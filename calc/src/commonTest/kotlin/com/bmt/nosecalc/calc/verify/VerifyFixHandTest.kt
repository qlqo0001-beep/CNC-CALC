package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperWarning
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-04 `VerifyCompanyFlowTest`)이 찾은 회사 꼴 G41/G42 잘못의 고침을 손 계산으로 지킨다(지은 도형 — 검증 보고의 입력).
 * 기대값은 앱 글이 아니라 세 점으로 따로 구한 원 · 접선 · 띠 규칙(설계 calc-design '진입점과 NC' · 라운드 '기계 보정 프로그램')에서 낸다.
 * - 외경 3점호 끝 접선이 축 쪽으로 꺾임: `G0Z200.` 이 그린 호 밑(부품 속)을 지나지 않는다 — 물러남 X ≥ 2·(호 꼭대기 + G40 자리 규칙 반지름)
 * - 축 가까이에서 끝나는 외경 3점호: 빼는 줄 · G40 자리가 축 너머 맞은편(축 거울)으로 가지 않는다
 * - 테이퍼 비운 시작 · 볼록 첫 꺾임의 가파른 경사: 진입점은 경사를 큰 축으로 c 늘인 점(X 가 축 너머로 가지 않음)
 * - 짧은 막힌 끝: G41/G42 는 그 요소를 빼고 벽을 따라 빼서 노즈가 끝 좌표 너머(보스 · 벽)로 가지 않는다
 */
class VerifyFixHandTest {
    /** G40 자리 규칙 반지름 (1 + √2)·r + 0.02 */
    private fun rc(r: Double) = (1 + sqrt(2.0)) * r + 0.02

    /** 직경 X 세 점(Z, X)의 원 중심 · 반지름(반경 좌표) */
    private fun circle(p1: Pt, p2: Pt, p3: Pt): Pair<Pt, Double> {
        val a = p1.z * (p2.x - p3.x) - p1.x * (p2.z - p3.z) + p2.z * p3.x - p3.z * p2.x
        val b = (p1.z * p1.z + p1.x * p1.x) * (p3.x - p2.x) + (p2.z * p2.z + p2.x * p2.x) * (p1.x - p3.x) + (p3.z * p3.z + p3.x * p3.x) * (p2.x - p1.x)
        val c = (p1.z * p1.z + p1.x * p1.x) * (p2.z - p3.z) + (p2.z * p2.z + p2.x * p2.x) * (p3.z - p1.z) + (p3.z * p3.z + p3.x * p3.x) * (p1.z - p2.z)
        val o = Pt(-b / (2 * a), -c / (2 * a))
        return o to sqrt((p1.z - o.z) * (p1.z - o.z) + (p1.x - o.x) * (p1.x - o.x))
    }

    /** NC 줄마다 그 줄 끝의 명령점(반경, X · Z 모달) — 축 낱말이 없는 줄은 null */
    private fun positions(nc: List<String>): List<Pt?> {
        var z = 0.0
        var x = 0.0
        return nc.map { line ->
            val zm = Regex("Z(-?[0-9.]+)").find(line)?.groupValues?.get(1)?.toDouble()
            val xm = Regex("X(-?[0-9.]+)").find(line)?.groupValues?.get(1)?.toDouble()
            if (zm != null) z = zm
            if (xm != null) x = xm / 2
            if (zm == null && xm == null) null else Pt(z, x)
        }
    }

    /** 첫 빼는 줄(F.3)부터 G40 앞까지의 명령점 */
    private fun exitPoints(nc: List<String>): List<Pt> {
        val pos = positions(nc)
        val i = nc.indexOfFirst { it.endsWith("F.3") }
        return (i until nc.indexOf("G40")).mapNotNull { pos[it] }
    }

    @Test
    fun outerArcTurningToAxisRetractsOverTheArc() {
        // 외경 3점호 X27.42 Z-0.567 / X28.04 Z-8.193 / X20.005 Z-14.682, 0.8R, Z 여유 2(검증 ①)
        val (o, rr) = circle(Pt(-0.567, 13.71), Pt(-8.193, 14.02), Pt(-14.682, 10.0025))
        // 꼭대기(중심 Z −4.886 이 호의 Z 구간 −14.682 ~ −0.567 안 — 호 위)
        val top = o.x + rr
        assertEquals(14.448, top, 1e-3)
        val s = RoundCalc.machine(Arc3Input(inner = false, noseR = 0.8, tip = 8, x1 = 27.42, z1 = -0.567, x2 = 28.04, z2 = -8.193, x3 = 20.005, z3 = -14.682, zClear = 2.0)).solution!!
        val nc = s.nc
        // 첫 빼는 줄 = 끝 접선으로 c = 2(빈 끝 — 사용자 결정 ③): 끝 반지름 방향을 왼쪽으로 돌린 것(G3 = 반시계)
        val p = Pt(-14.682, 10.0025)
        val radial = Pt((p.z - o.z) / rr, (p.x - o.x) / rr)
        val u = Pt(-radial.x, radial.z)
        val exits = exitPoints(nc)
        val want = Pt(p.z + u.z * 2.0, p.x + u.x * 2.0)
        assertEquals(want.z, exits.first().z, 1e-3, nc.joinToString(" / "))
        assertEquals(want.x * 2, exits.first().x * 2, 1.5e-3, nc.joinToString(" / "))
        // 물러남: G40 앞 마지막 점에서 +Z 로 가는 띠가 호에서 rc 이상 — X ≥ 2·(14.448 + 1.951) = X32.797
        val last = exits.last()
        assertTrue(last.x >= top + rc(0.8) - 1e-3, "물러남 X${last.x * 2} < ${2 * (top + rc(0.8))}\n${nc.joinToString(" / ")}")
        assertEquals("G0Z200.", nc.last())
    }

    @Test
    fun outerArcReturningPlusZKeepsG40OffTheTangent() {
        // 외경 3점호 X56.205 Z-14.035 / X58.876 Z-19.227 / X49.639 Z-21.948, 1.3R, Z 여유 1.113(검증 ② — 끝 접선이 +Z 로 되돌아옴)
        val (o, rr) = circle(Pt(-14.035, 28.1025), Pt(-19.227, 29.438), Pt(-21.948, 24.8195))
        val p = Pt(-21.948, 24.8195)
        val radial = Pt((p.z - o.z) / rr, (p.x - o.x) / rr)
        val u = Pt(-radial.x, radial.z) // 반시계
        assertTrue(u.z > 0 && u.x < -0.2, "끝 접선이 +Z 로 되돌아오며 축 쪽으로 가파르다 — 그 접선이 닫은 도면의 경계")
        val s = RoundCalc.machine(Arc3Input(inner = false, noseR = 1.3, tip = 0, x1 = 56.205, z1 = -14.035, x2 = 58.876, z2 = -19.227, x3 = 49.639, z3 = -21.948, zClear = 1.113)).solution!!
        val g40 = exitPoints(s.nc).last()
        // G40 자리(형식 C 에서 노즈가 옮겨지는 곳)는 그린 구간 소재에서 rc 이상 — 전에는 끝 접선 안쪽 소재로 1.1 들어갔다
        // 그린 구간 소재의 경계 = 끝에서 접선을 따라 축까지 내린 선분
        val a = Pt(p.z + u.z * (p.x / -u.x), 0.0)
        val ab = Pt(a.z - p.z, a.x - p.x)
        val t = (((g40.z - p.z) * ab.z + (g40.x - p.x) * ab.x) / (ab.z * ab.z + ab.x * ab.x)).coerceIn(0.0, 1.0)
        val gap = sqrt((g40.z - p.z - ab.z * t).let { it * it } + (g40.x - p.x - ab.x * t).let { it * it })
        assertTrue(gap >= rc(1.3) - 2e-3, "G40 자리 ~ 닫는 선 $gap\n${s.nc.joinToString(" / ")}")
        // 물러남 X 는 호 꼭대기(중심 위 — 호 위) + rc 위
        assertTrue(g40.x >= o.x + rr + rc(1.3) - 1e-3, "물러남 X${g40.x * 2}")
    }

    @Test
    fun outerArcNearAxisStaysOffTheMirror() {
        // 외경 3점호 X101.198 Z3.96 / X75.835 Z-30.406 / X3.153 Z-35.008, 1.2R(검증 ③ — 끝 접선이 축을 넘어 맞은편으로)
        val (o, rr) = circle(Pt(3.96, 50.599), Pt(-30.406, 37.9175), Pt(-35.008, 1.5765))
        val s = RoundCalc.machine(Arc3Input(inner = false, noseR = 1.2, x1 = 101.198, z1 = 3.96, x2 = 75.835, z2 = -30.406, x3 = 3.153, z3 = -35.008, zClear = 0.535)).solution!!
        // 빼는 점은 축 너머로 가더라도 맞은편 호(축 거울)의 Z 구간 밑 — 거울 원 안 — 으로 가지 않는다(전에는 `G1X-3.422Z-33.541` 이 맞은편 호를 1.1 팠다).
        // 원 전체로 재므로 엄격한 쪽이다
        val exits = exitPoints(s.nc)
        for (q in exits.filter { it.x < 0 }) {
            val dMirror = sqrt((q.z - o.z) * (q.z - o.z) + (q.x + o.x) * (q.x + o.x))
            assertTrue(dMirror >= rr + 1.2 - 2e-3, "빼는 점 $q 가 맞은편 호에서 r 안 — ${s.nc.joinToString(" / ")}")
        }
        // G40 자리는 두 호(제 것 · 거울)에서 rc 이상
        val g40 = exits.last()
        fun arcGap(c: Pt): Double = kotlin.math.abs(sqrt((g40.z - c.z) * (g40.z - c.z) + (g40.x - c.x) * (g40.x - c.x)) - rr)
        assertTrue(arcGap(o) >= rc(1.2) - 2e-3 && arcGap(Pt(o.z, -o.x)) >= rc(1.2) - 2e-3, "G40 $g40\n${s.nc.joinToString(" / ")}")
        // 물러남 X ≥ 2·(호 꼭대기 + rc) — 시작 각 atan2(34.64, 6.74) ≈ 79° 에서 반시계라 꼭대기(90°)를 지난다
        assertTrue(atan2(50.599 - o.x, 3.96 - o.z) < kotlin.math.PI / 2)
        assertTrue(g40.x >= o.x + rr + rc(1.2) - 1e-3, "물러남 X${g40.x * 2}")
    }

    @Test
    fun steepConvexStartEntersAlongTheMajorAxis() {
        // 내경 TYPE 5 X75.321 Z5.018 → X167.178 Z4.442, 1.4R, Z 여유 2, 시작 · 끝 비움(검증 — 89.6° 경사)
        val res = TaperCalc.calc(TaperInput(inner = true, type = 5, noseR = 1.4, tip = 2, x1 = 75.321, z1 = 5.018, x2 = 167.178, z2 = 4.442, zClear = 2.0))
        val s = res.solution!!
        // 진입점 = T1 + (T1 − T2)·c / max(|ΔZ|, |Δ반경|): Δ = (0.576, −45.9285) → 반경으로 2 → X(75.321 − 4) = X71.321, Z 5.018 + 0.576·2/45.9285 = 5.043
        val c = max(2.0, 1.4)
        val dz = 5.018 - 4.442
        val dx = (75.321 - 167.178) / 2
        val k = c / max(kotlin.math.abs(dz), kotlin.math.abs(dx))
        val e = Pt(5.018 + dz * k, 75.321 / 2 + dx * k)
        assertEquals("G0X71.321Z20.", s.ncG41G42[1])
        assertEquals(e.z, s.ncG41G42[2].removePrefix("Z").toDouble(), 1e-3)
        assertEquals(2 * e.x, 71.321, 1e-3)
        // G41/G42 의 어느 점도 축 너머로 가지 않는다(전에는 G0X-243.627Z20.). 직접 보정 진입점은 그대로 Z 로 c 라 축 너머 — ENTRY_PAST_AXIS 는 남는다(사용자 결정 ① 범위)
        assertTrue(positions(s.ncG41G42).filterNotNull().all { it.x >= 0 }, s.ncG41G42.joinToString(" / "))
        assertTrue(TaperWarning.ENTRY_PAST_AXIS in res.warnings && s.entry.x < 0)
    }

    @Test
    fun shortBlockedEndStaysOffTheWall() {
        // 뒤집기 외경 형상 1: X241.291 Z-10.995 → X138.145 Z-2.532, 끝 단면 X137.253(반경 0.446 — 0.8R 보다 짧음), 시작 Z-12.051, Z 여유 0.607, 팁 2
        val r = 0.8
        val s = TaperCalc.calc(
            TaperInput(inner = false, type = 1, noseR = r, tip = 2, x1 = 241.291, z1 = -10.995, x2 = 138.145, z2 = -2.532, zClear = 0.607, start = -12.051, end = 137.253, flip = true),
        ).solution!!
        val nc = s.ncG41G42
        // 끝 단면 줄(X137.253)을 쓰지 않고 앞 요소(경사) 끝에서 끝 단면의 공구 쪽(+Z)으로 뺀다
        assertFalse(nc.any { it.startsWith("X137.253") }, nc.joinToString(" / "))
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        assertEquals("X138.145Z-2.532", nc[iExit - 1])
        assertTrue(nc[iExit].startsWith("Z"), "첫 빼는 줄은 Z 로만(끝 단면의 공구 쪽 법선): ${nc[iExit]}")
        // 빼는 점은 경사 끝(반경 69.0725)보다 벽(보스 옆면 X137.253 — 반경 68.6265) 쪽으로 가지 않는다 — 빼는 줄의 공구 쪽은 +X(G41 · +Z 진행의 왼쪽)라
        // 노즈 중심은 그 점 + r 이상, 벽에서 0.446 + 0.8 ≥ r
        val wall = 137.253 / 2
        for (q in exitPoints(nc)) assertTrue(q.x >= 138.145 / 2 - 1e-3 && q.x + r >= wall + r, "빼는 점 $q 가 벽 쪽으로 간다 — ${nc.joinToString(" / ")}")
        // 첫 빼는 줄: 경사 → +Z 안쪽 꺾임(경사 방향 (0.162, −0.987), +Z 와 80.7°) 노즈는 경사 보정선과 빼는 줄 보정선(반경 69.0725 + 0.8)의 교점,
        // 그 줄 → +X(G40 자리로 비킴) 꺾임 노즈는 명령점 − r(Z) — 그 줄의 노즈 이동이 0.05 가 되게 c = 0.8 에 0.73 더(Z ≈ −1.003)
        val z1 = nc[iExit].removePrefix("Z").removeSuffix("F.3").toDouble()
        val slope = Pt(-2.532 - -10.995, (138.145 - 241.291) / 2).let { Pt(it.z / sqrt(it.z * it.z + it.x * it.x), it.x / sqrt(it.z * it.z + it.x * it.x)) }
        val nS = Pt(-slope.x, slope.z) // G41 왼쪽
        // 경사 보정선 위 점 + t·경사 가 반경 69.0725 + r 인 t
        val base = Pt(-2.532 + nS.z * r, 138.145 / 2 + nS.x * r)
        val t = (138.145 / 2 + r - base.x) / slope.x
        val noseStart = base.z + slope.z * t
        val noseEnd = z1 - r
        assertEquals(0.05, noseEnd - noseStart, 2e-3, "첫 빼는 줄 노즈 이동")
    }
}
