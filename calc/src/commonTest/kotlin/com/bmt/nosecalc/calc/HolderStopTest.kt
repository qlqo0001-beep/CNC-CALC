package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.verify.EuclidRef
import com.bmt.nosecalc.calc.verify.StopChecker
import com.bmt.nosecalc.calc.verify.V
import com.bmt.nosecalc.calc.verify.at
import com.bmt.nosecalc.calc.verify.contactEdge
import com.bmt.nosecalc.calc.verify.len
import com.bmt.nosecalc.calc.verify.readText
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 보링바 안전 끝점 · 잘린 NC(HolderStop). 설계 문서 손 계산 예 A~F · 뒤보링에서 안전 여유 0 과 0.5:
 * 끝점 여유 = 기준(±1e-5), 끝점까지 모든 자세 안전, 0.001 mm 더 가면 모자람, 잘린 NC 두 가지를 다시 읽어 굴린 자세 ≥ 기준 − 1e-4,
 * 후퇴 ≥ 0, 닿는 곳의 처음 좌표 = 기존 표(±0.003). 검사기([StopChecker])는 경로 · NC 읽기 · 제어기 보정 모형을 따로 짰다
 */
class HolderStopTest {
    private val r = 0.4
    private val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
    private val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0)
    private val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)
    private val socket = RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, legLen = 5.0)
    private val barF = BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0)

    /** 뒤보링 3점호(방 안 뒷면 → R5 → Ø20, 팁 1 · G42) */
    private val back = Arc3Input(inner = true, noseR = 0.4, x1 = 30.0, z1 = -20.0, x2 = 24.0, z2 = -19.0, x3 = 20.0, z3 = -15.0, zClear = 1.0)

    /** 뒤보링 바: f 12 면 시작에서 바 여유 0.6, DMIN 22 는 헤드(인선 뒤 −Z 쪽)가 Ø20 쪽으로 가며 경로 가운데에서 모자란다 */
    private val backBar = BoringBar(12.0, 12.0, InsertShape.C, 9.67, 95.0, dMin = 22.0)

    private class Case(val name: String, val sol: RoundSolution, val drawn: RoundSolution, val face: Boolean, val bar: BoringBar)

    private fun type(name: String, i: RoundInput, bar: BoringBar): Case {
        val inp = i.copy(inner = true, noseR = r, zClear = 1.0)
        return Case(name, RoundCalc.calc(inp).solution!!, RoundCalc.machine(inp).solution!!, true, bar)
    }

    private fun arc(name: String, a: Arc3Input, bar: BoringBar) = Case(name, RoundCalc.arc3(a).solution!!, RoundCalc.machine(a).solution!!, false, bar)

    private val cases by lazy {
        listOf(
            type("A", dome, barA),
            type("B", dome.copy(d1 = 18.0), barA),
            type("C", step, barA),
            type("C 관통", step.copy(through = true), barA),
            type("C38", RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 38.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0), barA),
            type("D", dome, barA.copy(overhang = 12.0)),
            type("E", dome, barA.copy(dMin = 20.0)),
            type("F", socket, barF),
            arc("뒤보링 DMIN", back, backBar),
            arc("뒤보링 f 11", back, BoringBar(12.0, 11.0, InsertShape.C, 9.67, 95.0)),
            arc("뒤보링 f 11.5", back, BoringBar(12.0, 11.5, InsertShape.C, 9.67, 95.0)),
            arc("입구 R", Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0, zClear = 1.0),
                BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, overhang = 12.0, dMin = 20.0)),
        )
    }

    private fun plan(c: Case, safe: Double) = HolderStop.plan(c.sol, c.drawn, r, c.face, c.bar, safe)

    private fun near(z: Double, xDia: Double, p: Pt?, tag: String, tol: Double = 3e-3) {
        assertNotNull(p, tag)
        assertTrue(abs(p.z - z) <= tol && abs(p.xDia - xDia) <= tol, "$tag: 기대 Z$z X$xDia, 실제 Z${p.z} X${p.xDia}")
    }

    /** 손 계산 예마다 안전 여유 0 · 0.5 에서 검사기(0.002 mm 표본) */
    @Test
    fun handCasesHoldProperties() {
        val fails = mutableListOf<String>()
        for (c in cases) for (safe in listOf(0.0, 0.5)) {
            val p = plan(c, safe)
            val f = mutableListOf<String>()
            StopChecker(c.sol, c.drawn, r, c.face, c.bar, safe).check(p, 0.002, f)
            fails += f.map { "${c.name} 안전 여유 $safe: $it" }
        }
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    /**
     * 끝점이 생기는지 · 경고 · 후퇴 방식(사례마다 기대). 기계 보정(회사 꼴)은 보정 켠 채 깎은 길을 되짚을 수 없어 후퇴가 늘 LIFT(공구 쪽으로 빼는 줄 → G40 →
     * G0Z200.)다 — 빼는 줄은 마지막 블록의 공구 쪽 법선이라 끝점에서 기준(안전 여유)에 꼭 맞던 맞은편 부분이 그만큼 가까워진다. 그래서 끝점이 맞은편 부분으로
     * 정해진 사례(A · B · D · F)의 안전 여유 0.5 는 RETRACT_TIGHT(직접 보정은 거꾸로 후퇴로 0.5 를 지킨다). 진입은 Z20. 에서 내려오는 보정 중 블록이라 직접 보정
     * 진입과 다르다(뒤보링은 Ø20 구멍을 지나 방 안 진입점까지 — 아래 각 줄)
     */
    @Test
    fun handCasesOutcome() {
        fun outcome(name: String, safe: Double): String {
            val p = plan(cases.single { it.name == name }, safe)
            val e = p.end ?: return "없음 ${p.warnings}"
            return "${e.element} ${e.part}${if (e.opposite) " 맞은편" else ""} ${p.direct?.retract} ${p.machine?.retract} ${p.warnings}"
        }
        // 여유 0: 구 위에서 노즈가 +Z 로 바로 떨어지고 맞은편 여유 ≥ 0 이라 Z 로만 뺀다. 0.5: 축 쪽으로 빼면 바 맞은편이 0.5 아래 → 직접 보정은 온 길을 거꾸로,
        // 기계 보정은 공구 쪽(구 중심 쪽 — 축 쪽 성분이 있다)으로 빼야 해 바 맞은편이 0.5 아래(바 아래 변 ∥ Ø20 벽이라 X 로 뺀 만큼 준다)
        assertEquals("2 BAR 맞은편 LIFT LIFT []", outcome("A", 0.0))
        assertEquals("2 BAR 맞은편 REVERSE LIFT [RETRACT_TIGHT]", outcome("A", 0.5))
        // 바가 처음부터 Ø18 맞은편 벽에 가까워 X 를 뺄 수 없고, Z 로만 빼면 노즈가 벽에 끌린다 — 기계 보정의 빼는 줄은 Ø18 벽의 법선(−X)이라 바 앞면이 맞은편 입구 모서리로 다가간다
        assertEquals("0 BAR 맞은편 REVERSE LIFT []", outcome("B", 0.0))
        assertEquals("0 BAR 맞은편 REVERSE LIFT [RETRACT_TIGHT]", outcome("B", 0.5))
        // 바닥 면에서는 노즈가 +Z 로 바로 떨어져 X 를 빼지 않고 Z 로만 뺀다(기계 보정의 빼는 줄도 바닥 면 법선 +Z — 바 맞은편 그대로)
        assertEquals("3 BAR 맞은편 LIFT LIFT []", outcome("C", 0.0))
        assertEquals("3 BAR 맞은편 LIFT LIFT []", outcome("C", 0.5))
        assertEquals("없음 []", outcome("C 관통", 0.0))
        assertEquals("없음 []", outcome("C 관통", 0.5))
        // C38 여유 0: 경로는 끝까지 되는데(가장 좁은 곳 = 축의 바 맞은편 D1/2 + r − f − D/2 = 0.4) 원래 기계 보정 끝은 바닥 면을 축 너머 X−.8 까지 쓰고 +Z 로 뺀 뒤
        // G40 — 형식 C · 팁 2(팁 벡터 (−r, +r))면 노즈가 반경 0 → −0.8 로 옮겨져 바가 맞은편으로 0.8 → 0.4 − 0.8 < 0 겹침. 빠지는 길만 바꾼다(MACHINE_EXIT)
        assertEquals("없음 [MACHINE_EXIT]", outcome("C38", 0.0))
        assertEquals("3 BAR 맞은편 LIFT LIFT []", outcome("C38", 0.5))
        assertEquals("2 BAR 맞은편 LIFT LIFT []", outcome("D", 0.0))
        assertEquals("2 BAR 맞은편 REVERSE LIFT [RETRACT_TIGHT]", outcome("D", 0.5))
        assertEquals("0 MIN_BORE LIFT LIFT []", outcome("E", 0.0))
        // Ø20 구멍에 DMIN 20 은 0.5 여유가 없어 입구 면에서 멈춘다 — 깎는 것이 없다. 기계 보정도 진입(X20 한 직선 — 첫 깎는 줄)에서 같은 노즈 자리(헤드가 입구 면에 닿음)에서 멈춘다
        assertEquals("-1 MIN_BORE null null [NOTHING_CUT, MACHINE_ENTRY]", outcome("E", 0.5))
        assertEquals(-1, plan(cases.single { it.name == "E" }, 0.5).machineEnd!!.element)
        assertEquals("0 INSERT 맞은편 REVERSE LIFT []", outcome("F", 0.0))
        assertEquals("0 INSERT 맞은편 REVERSE LIFT [RETRACT_TIGHT]", outcome("F", 0.5))
        // 뒤보링 DMIN 22: 직접 보정은 방 안 진입점에서 시작해 깎지만, 기계 보정은 Z20. 에서 Ø20 구멍(끝 접선 반직선 — 늘인 구멍)을 지나 들어와야 해
        // 헤드(Ø22)가 들어갈 수 없다 — Z20. 자리부터(늘인 구멍이 +Z 로 끝없이 이어지는 소재 모형)
        assertEquals("0 MIN_BORE LIFT null [MACHINE_ENTRY]", outcome("뒤보링 DMIN", 0.0))
        assertEquals("0 MIN_BORE LIFT null [MACHINE_ENTRY]", outcome("뒤보링 DMIN", 0.5))
        assertEquals(-2, plan(cases.single { it.name == "뒤보링 DMIN" }, 0.0).machineEnd!!.element)
        // 바가 Ø20 입구를 못 지난다 — 기계 보정도 제 진입(방 안으로 X 를 맞추는 줄)에서 바가 닿아 멈춘다
        assertEquals("없음 [UNSAFE_AT_ENTRY, MACHINE_ENTRY]", outcome("뒤보링 f 11", 0.0))
        // f 11.5: 직접 보정은 바 여유 0.1(진입점 반경 15.4 − 11.5 + 6 = 9.9 < 10) — 여유 0 이면 끝까지 안전, 0.5 면 진입점부터 모자람.
        // 기계 보정 진입점은 첫 점에서 시작 접선(−X)을 거꾸로 c = 1 → 반경 16 이라 바 윗면 16 − 11.5 + 6 = 10.5 가 Ø20 구멍 벽을 넘는다 — 진입에서 멈춤
        assertEquals("없음 [MACHINE_ENTRY]", outcome("뒤보링 f 11.5", 0.0))
        assertEquals("없음 [UNSAFE_AT_ENTRY, MACHINE_ENTRY]", outcome("뒤보링 f 11.5", 0.5))
        // DMIN 20 = 구멍 Ø20: CAD 점 반올림으로 끝 접선이 조금 기울어 늘인 구멍이 Ø20 보다 좁아지는 곳에서 멈춘다. 기계 보정 진입점은 첫 점 처음 접선(입구 면 쪽)
        // 위라 진입이 그 면을 따라 들어와 인서트가 면에 붙어 지나간다(노즈 원이 면에서 r) — 겹치지 않음 기준(1e-6)의 경계라 진입 Z 줄 끝에서 멈춘다(알려진 한계 —
        // 면을 따라 들어오는 회사 꼴 진입, 직접 보정은 Z1 에서 들어온다)
        assertEquals("0 MIN_BORE LIFT null [MACHINE_ENTRY]", outcome("입구 R", 0.0))
    }

    /**
     * A 돔 바닥: 안전 여유 0 의 끝점 = 바 맞은편 여유 0 이 되는 곳 Z−11.404 X18.014(설계 문서), 0.5 는 바와 소재 사이 직선 거리가 0.5 가 되는 곳을
     * 독립 참조([EuclidRef] — 구현의 거리 식 · 공구 단면을 쓰지 않음)로 요소 2 위에서 이분법(1e-6 mm). 10-28 직선 거리 기준: 이 자리의 가장 가까운 곳은
     * 바 아래 변과 맞은편 Ø20 구멍 벽(둘 다 Z 에 나란해 반경 여유 = 직선 거리)이라 끝점은 반경 기준 때와 같다(Z−10.875 X19.000).
     * 잘린 NC: 앞 4 줄 그대로 + 구 원호를 끝점에서 끊은 G03(같은 R) + 거꾸로 후퇴. 기계 보정은 도면 R12.5 를 끊고 G40 으로 인선 자리
     */
    @Test
    fun caseAStop() {
        val c = cases.single { it.name == "A" }
        val p0 = plan(c, 0.0)
        val e0 = p0.end!!
        near(-11.404, 18.014, e0.pose.tip, "여유 0 끝점")
        assertEquals(0.0, e0.margin, 1e-5)
        // 0.5: 바와 소재 직선 거리가 0.5 가 되는 곳을 요소 2(원호 — 깎는 면 예외 없음) 위에서 이분법(경로 · 거리 계산 따로)
        val p5 = plan(c, 0.5)
        val e5 = p5.end!!
        val seg = c.sol.program.segs[2]
        val ref = EuclidRef(barA, r, false)
        val (cutWall, oppWall) = EuclidRef.material(c.sol.drawing.segs, true)
        fun barM(t: Double): Double {
            val n = at(seg, t) - NoseComp.tipVector(2, r)
            return ref.gaps(V(n.z, n.x), cutWall, oppWall).let { min(it[2], it[3]) }
        }
        var lo = 0.0
        var hi = p5.result.hits.first().fromT
        repeat(80) {
            val mid = (lo + hi) / 2
            if (barM(mid) < 0.5) hi = mid else lo = mid
        }
        assertEquals(2, e5.element)
        assertEquals(lo * len(seg), e5.t * len(seg), 1e-6)
        assertEquals(0.5, e5.margin, 1e-5)
        near(-10.875, 19.0, e5.pose.tip, "여유 0.5 끝점")
        assertEquals(4, e5.at.directLine)
        // 기계 보정(회사 꼴): G41 / G0X20.Z20. / Z1. / G1Z0.F.1 / Z-9.659(요소 0) / G3 R1(요소 1) / 구(요소 2) — 0부터 6번째 줄
        assertEquals(6, e5.at.machineLine)
        // 기계 보정 명령점 + r·법선 = 노즈 중심(구 중심 (−2.5, 0) 에서 R12.5 → 노즈는 R12.1)
        val m = e5.at.machine!!
        val o = Pt(-2.5, 0.0)
        assertEquals(12.5, (m - o).length, 1e-9)
        assertTrue((o + (m - o) * (12.1 / 12.5) - e5.pose.nose).length < 1e-9)
        val d = p5.direct!!
        assertEquals(c.sol.nc.legacy.take(4), d.nc.legacy.take(4))
        assertEquals(listOf("G03 X19.001 Z-10.874 R12.1", "G02 X19.739 Z-10.432 R12.1", "G02 X20. Z-10.059 R0.6", "G01 Z-0.4", "G01 Z0.6"), d.nc.legacy.drop(4))
        // 잘린 기계 보정(회사 꼴): 앞 6 줄 그대로 + 구(R12.5)를 P′ 까지 + 공구 쪽으로 빼는 줄(F.3) … + G40 혼자 줄 + G0Z200.
        // P′ = 끝 법선 자리가 안전 끝점인 도면 점 m 을 같은 원 위로 진행 쪽(반시계 — G03)으로 asin(r/ρ), ρ = 노즈 보정 반지름 12.1 더 돈 점:
        // 그래야 빼는 줄(끝 법선 방향 — 직각, 안쪽 꺾임)과의 보정선 교점이 안전 끝점이다
        val mc = p5.machine!!
        assertEquals(c.drawn.nc.take(6), mc.nc.take(6))
        val th = atan2(m.x - o.x, m.z - o.z) + asin(r / 12.1)
        val pp = o + Pt(cos(th), sin(th)) * 12.5
        val w6 = ncWords(mc.nc[6])
        assertEquals("R12.5", w6.last(), mc.nc[6])
        near(pp.z, pp.xDia, Pt(w6.first { it[0] == 'Z' }.drop(1).toDouble(), w6.first { it[0] == 'X' }.drop(1).toDouble() / 2), "끝 블록 P′", 2e-3)
        assertTrue("F.3" in mc.nc[7], mc.nc[7])
        assertEquals(listOf("G40", "G0Z200."), mc.nc.takeLast(2))
        // 끝 블록 끝 노즈(빼는 줄과의 교점)는 안전 끝점을 넘지 않고(덜 간 길이 ≥ 0) 반올림 몇 칸 안
        assertTrue(mc.lack in 0.0..2e-3, "${mc.lack}")
        assertTrue((mc.pose.nose - e5.pose.nose).length < 2e-3, "${mc.pose.nose} · ${e5.pose.nose}")
        // 빼는 줄은 구 중심 쪽(공구 쪽 법선 — 축 쪽 성분 0.752)이라 끝점에서 0.5 이던 바 맞은편(아래 변 ∥ Ø20 벽)이 준다 — 되짚을 수 없어 RETRACT_TIGHT
        assertEquals(RetractKind.LIFT, mc.retract)
        assertTrue(mc.retractClear < 0.5 - HolderStop.TEXT_TOL && StopWarning.RETRACT_TIGHT in p5.warnings, "${mc.retractClear} ${p5.warnings}")
    }

    /** C 막힌 바닥: 바닥 면을 축 쪽으로 깎다가 바가 Ø30 맞은편 벽에서 안전 여유 — 인선 반경 = f + D/2 − D1/2 + 안전 여유 */
    @Test
    fun caseCStop() {
        val c = cases.single { it.name == "C" }
        for (safe in listOf(0.0, 0.5)) {
            val p = plan(c, safe)
            val e = p.end!!
            assertEquals(3, e.element)
            near(-16.0, 2 * (11.0 + 8.0 - 15.0 + safe), e.pose.tip, "끝점 $safe", 1e-6)
            // 0.001 로 쓴 X 가 끝점을 넘지 않게(축 쪽으로 가는 면이라 X 를 크게) 한 칸 뒤로
            val d = p.direct!!
            assertEquals("G01 X${Fmt.nc(2 * (4.0 + safe) + 0.002)}", d.nc.legacy[d.retractFrom - 1])
            assertEquals(listOf("G00 Z0.6"), d.nc.legacy.drop(d.retractFrom))
            val m = p.machine!!
            // 기계 보정(회사 꼴): 면 위 도면 점(노즈 Z−15.6 의 발 Z−16, 반경 3.6 + 안전 여유)을 바닥 면 진행(−X)으로 r 더 간 P′(반경 3.2 + 안전 여유) — 빼는 줄
            // (바닥 면의 공구 쪽 법선 +Z, 직각 안쪽 꺾임)과의 보정선 교점이 끝점. 직접 보정처럼 끝점을 넘지 않게 경로로 한 칸(반경 0.001 = 직경 0.002) 물림
            assertEquals("G01 X${Fmt.nc(2 * (3.2 + safe) + 0.002)}", m.nc.legacy[m.retractFrom - 1])
            val ex = m.nc.legacy[m.retractFrom]
            assertTrue(ex.startsWith("G01 Z") && "X" !in ex && "F0.3" in ex, ex)
            assertEquals(listOf("G40", "G00 Z200."), m.nc.legacy.takeLast(2))
        }
    }

    /**
     * E: 헤드(인선 뒤 insZ)가 모서리 R 시작(Z−9.659)에 닿기 직전이 끝점. 안전 여유 0.5 는 Ø20 = DMIN 이라 들어가기 전에 멈춘다 — 10-28 직선 거리 기준:
     * DMIN 구간(인선 z ~ 인선 z + insZ, 축 둘레 Ø20) 앞 위 모서리 (인선 z, 10) 과 입구 모서리 (Z0, 10) 의 거리 = 인선 z 라 진입 이동의 인선 Z0.5
     * (반경 기준에서는 구간 앞이 입구 면을 넘는 Z0 — 면 앞은 공기라 반경 여유가 끝없다가 뛰었다). 기계 보정(회사 꼴)은 `G0X20.Z20.` → `Z1.` → `G1Z0.F.1` 이
     * 모두 X20 한 직선이라 노즈는 보정선(반경 9.6) 위, 노즈 z = 명령점 z. 구간 앞(노즈 z − r)이 0.5 가 되는 명령점 Z0.9 — Z1. 아래라 첫 깎는 줄(0부터 3번째 줄)의
     * 비율 (1 − 0.9)/(1 − 0) = 0.1
     */
    @Test
    fun caseEStop() {
        val c = cases.single { it.name == "E" }
        val e0 = plan(c, 0.0).end!!
        near(-9.659, 20.0, e0.pose.tip, "끝점")
        assertEquals(HolderPart.MIN_BORE, e0.part)
        val p5 = plan(c, 0.5)
        val e5 = p5.end!!
        assertEquals(-1, e5.element)
        near(0.5, 20.0, e5.pose.tip, "진입 이동", 1e-6)
        assertEquals(0.5, e5.margin, 1e-6)
        assertNull(p5.direct)
        assertNull(p5.machine)
        val me = p5.machineEnd!!
        assertEquals(-1, me.element)
        near(0.9, 20.0, me.at.machine!!, "기계 보정 첫 깎는 줄", 1e-6)
        assertEquals(3, me.at.machineLine)
        assertEquals(0.1, me.t, 1e-6)
    }

    /**
     * 뒤보링 DMIN 22: 헤드 구간(인선 −Z 쪽 insZ, 축 둘레 Ø22)의 +Z 위 모서리 (인선 z, 11) 이 입구 쪽 R5(중심 (−15, 15) — 소재가 원 안인 볼록 모서리)에
     * 다가간다. 직선 거리 = √((z + 15)² + 4²) − 5 (모서리에서 중심 방향이 원호 안) — 안전 여유가 되는 인선 z = −15 − √((5 + 안전 여유)² − 16).
     * 0 이면 Z−18.000(반경 기준과 같다 — 닿는 순간은 같다), 0.5 면 Z−18.775 X23.950(반경 기준 Z−18.571 X23.481 보다 앞 — 모서리가 비스듬히 가깝다).
     * X 는 G40 경로 원호(중심 (−14.6, 15.4), R5.4) 위
     */
    @Test
    fun backBoringStop() {
        val c = cases.single { it.name == "뒤보링 DMIN" }
        for (safe in listOf(0.0, 0.5)) {
            val e = plan(c, safe).end!!
            val z = -15 - sqrt((5 + safe) * (5 + safe) - 16)
            val x = 15.4 - sqrt(5.4 * 5.4 - (z + 14.6) * (z + 14.6))
            near(z, 2 * x, e.pose.tip, "안전 여유 $safe", 1e-6)
            assertEquals(HolderPart.MIN_BORE, e.part)
            assertEquals(safe, e.margin, 1e-5)
        }
        near(-18.775, 23.950, plan(c, 0.5).end!!.pose.tip, "표")
    }

    /** 닿는 곳 좌표(기존 표 ±0.003)를 두 프로그램으로 · 줄 번호 · 처음 닿는 곳의 정밀도(1e-6 mm 앞은 닿지 않음) */
    @Test
    fun hitCoordinatesBothPrograms() {
        val a = plan(cases.single { it.name == "A" }, 0.5)
        val bar = a.hits.single { it.hit.part == HolderPart.BAR }
        near(-11.409, 18.006, bar.first.direct, "A 바 처음")
        assertEquals(4, bar.first.directLine)
        assertEquals(bar.hit.first.drawn, bar.first.machine)
        // 기계 보정(회사 꼴) 구 줄: G41 / G0X20.Z20. / Z1. / G1Z0.F.1 / 요소 0 / 요소 1 / 요소 2 → 0부터 6
        assertEquals(6, bar.first.machineLine)
        near(-14.998, 1.238, a.hits.single { it.hit.part == HolderPart.INSERT }.first.direct, "A 인서트 처음")
        near(-12.005, 16.739, plan(cases.single { it.name == "D" }, 0.0).hits.single { it.hit.part == HolderPart.TURRET }.first.direct, "D 공구대")
        near(-9.759, 20.0, plan(cases.single { it.name == "E" }, 0.0).hits.first().first.direct, "E 최소 가공경")
        val f = plan(cases.single { it.name == "F" }, 0.0)
        val fb = f.hits.single { it.hit.part == HolderPart.BAR }
        near(-6.015, 11.010, fb.first.direct, "F 바 처음")
        assertEquals(4, fb.first.directLine)
        assertEquals(5, fb.last.directLine)
        // 기계 보정 줄: 도면 좌표 풀이의 같은 요소 줄
        val drawnF = cases.single { it.name == "F" }.drawn
        assertEquals(drawnF.lineOf(2, fb.hit.first.drawn), fb.first.machineLine)
        // 처음 닿는 곳: 그 자세는 닿고(여유 < −0.005) 1e-6 mm 앞은 닿지 않는다
        for (c in cases.filter { it.name in setOf("A", "B", "C", "D", "E", "F") }) {
            for (h in plan(c, 0.0).result.hits.filter { it.fromT > 0 && it.element >= 0 }) {
                val (on, before) = contactEdge(c.sol, r, c.face, c.bar, h.element, h.fromT, h.part, false)
                assertTrue(on < -HolderCheck.CONTACT_TOL && before >= -HolderCheck.CONTACT_TOL, "${c.name} ${h.part} 요소 ${h.element}: $on · $before")
            }
        }
    }

    /**
     * 기계 보정 진입은 팁 번호와 상관없다(회사 꼴 — 보정 줄이 이동 없는 혼자 줄이고 진입 블록이 모두 보정 중이라 노즈는 보정선 위). 10-28 에는 노즈R 0 풀이의
     * G00 점에 선 노즈(= 점 − 팁 벡터)가 비표준 팁에서 소재에 박혀(TYPE 6 팁 4: 벽보다 0.8 위) MACHINE_ENTRY 였다 — 이제 그런 풀이(진입 명령점 없음)는 기계 보정
     * 프로그램이 아니라 계획이 기계 보정을 보지 않는다(끝점 · 잘린 NC · 진입 닿음 모두 null).
     * 손 계산(지은 도형 TYPE 6 입구 Ø12.756 · 0.8R · Z 여유 0.069): c = max(0.069, 0.8) = 0.8, 첫 점 (Z0, X12.756) 의 처음 접선이 −Z(입구 구멍)라
     * 진입점 = Z0.8. Z 줄은 Z20. 에서 그 X 로 곧장(노즈는 반경 6.378 − 0.8 = 5.578 — 구멍 안, 면 위라 도면에 닿지 않음) → `G41` / `G0X12.756Z20.` / `Z.8` / `G1Z0.F.1`.
     * 진입에서 멈추지 않고 검사기를 지난다
     */
    @Test
    fun machineEntryNonStandardTip() {
        val inp = RoundInput(type = 6, mainR = 4.326, cornerR1 = 0.0, cornerR2 = 0.0, d1 = 3.41, d2 = 12.756, l1 = 1.322, l2 = 1.585, noseR = 0.8, tip = 4, zClear = 0.069, legLen = 4.89, inner = true)
        val sol = RoundCalc.calc(inp).solution!!
        val old = RoundCalc.calc(inp.copy(noseR = 0.0)).solution!!
        val bar = BoringBar(7.251, 5.113, InsertShape.T, 4.285, 92.496)
        val p = HolderStop.plan(sol, old, 0.8, true, bar, 0.5)
        assertNull(p.machineEnd)
        assertNull(p.machine)
        assertNull(p.machineEntry)
        assertTrue(StopWarning.MACHINE_ENTRY !in p.warnings, "${p.warnings}")
        val drawn = RoundCalc.machine(inp).solution!!
        assertEquals(listOf("G41", "G0X12.756Z20.", "Z.8", "G1Z0.F.1"), drawn.nc.take(4))
        assertEquals(RoundCalc.machine(inp.copy(tip = null)).solution!!.nc, drawn.nc)
        val q = HolderStop.plan(sol, drawn, 0.8, true, bar, 0.5)
        assertTrue(StopWarning.MACHINE_ENTRY !in q.warnings, "${q.warnings} ${q.machineEnd}")
        val f2 = mutableListOf<String>()
        StopChecker(sol, drawn, 0.8, true, bar, 0.5).check(q, 0.01, f2)
        assertTrue(f2.isEmpty(), "$f2")
    }

    /**
     * 표준 팁 뒤보링 3점호(10-28 검증 사례): 노즈R 0 풀이의 G00 점(시작 접선 위 도면 점)에 선 노즈는 접선에서 |n · 팁 벡터| 만 떨어져 늘인 접선(소재)을 팠다.
     * 회사 꼴 진입은 첫 깎는 줄이 시작 접선과 한 직선이라 그 줄 동안 노즈는 접선의 보정선(공구 쪽으로 r) 위다 — 진입 블록 노즈를 이 시험에서 보정선 교점으로
     * 따로 구해(직선 블록 — 공구 쪽 법선 r 민 선의 교점) 접선 반직선(첫 점에서 거꾸로)까지 거리가 r 이상인지 본다. 진입에서 멈추지 않고 검사기를 지난다
     */
    @Test
    fun machineEntryReversedTangent() {
        val a = Arc3Input(inner = true, noseR = 0.4, x1 = 39.601, z1 = -35.029, x2 = 47.115, z2 = -29.633, x3 = 48.379, z3 = -28.385, zClear = 0.287)
        val sol = RoundCalc.arc3(a).solution!!
        val old = RoundCalc.arc3(a.copy(noseR = 0.0)).solution!!
        val bar = BoringBar(30.058, 18.958, InsertShape.V, 5.913, 98.556)
        val p = HolderStop.plan(sol, old, 0.4, false, bar, 0.0)
        assertNull(p.end, "직접 보정은 끝까지 안전")
        assertNull(p.machineEnd)
        assertNull(p.machineEntry)
        val drawn = RoundCalc.machine(a).solution!!
        val arc = drawn.drawing.segs.first() as Arc
        val t0 = (arc.start - arc.center).unit().left() * (if (arc.cw) -1.0 else 1.0)
        // 진입 명령점 → 진입 블록(직선)의 보정선(G42 = 진행 오른쪽으로 r), 이웃 보정선의 교점 = 노즈
        val cmds = drawn.approach + arc.start
        fun off(u: Pt) = u.left() * (if (drawn.side == Side.G41) 0.4 else -0.4)
        val noses = cmds.indices.map { i ->
            val uA = (cmds[maxOf(i, 1)] - cmds[maxOf(i, 1) - 1]).unit()
            val uB = if (i == cmds.lastIndex) t0 else (cmds[i + 1] - cmds[i]).unit()
            val a1 = cmds[i] + off(uA)
            val b1 = cmds[i] + off(uB)
            // 두 선(a1 + uA·s, b1 + uB·t)의 교점 — 나란하면 법선 자리
            val den = uA.z * uB.x - uA.x * uB.z
            if (abs(den) < 1e-12) b1 else a1 + uA * (((b1 - a1).z * uB.x - (b1 - a1).x * uB.z) / den)
        }
        // 첫 깎는 줄 끝(첫 점) 노즈 = 첫 점의 법선(시작 접선과 한 직선)
        assertTrue((noses.last() - (arc.start + off(t0))).length < 1e-9, "${noses.last()}")
        // 진입 노즈 길(마디 사이 직선)이 접선 반직선(첫 점에서 −t0 쪽)에서 r 이상
        fun toRay(c: Pt): Double {
            val s = ((c - arc.start) dot (t0 * -1.0)).coerceAtLeast(0.0)
            return (c - (arc.start + t0 * -s)).length
        }
        for (i in 0 until noses.size - 1) for (k in 0..100) {
            val c = noses[i] + (noses[i + 1] - noses[i]) * (k / 100.0)
            assertTrue(toRay(c) >= 0.4 - 1e-9, "진입 ${i + 1} 블록 노즈 $c ~ 시작 접선 ${toRay(c)}")
        }
        val q = HolderStop.plan(sol, drawn, 0.4, false, bar, 0.0)
        assertTrue(q.warnings.isEmpty() && q.machineEnd == null, "${q.warnings} ${q.machineEnd}")
        val f = mutableListOf<String>()
        StopChecker(sol, drawn, 0.4, false, bar, 0.0).check(q, 0.01, f)
        assertTrue(f.isEmpty(), "$f")
    }

    /**
     * 진입에서 닿는 곳의 기계 보정 좌표 = 기계 보정 프로그램 자기 진입(회사 꼴)에서 처음 닿는 명령점. 뒤보링 f 11(G42): Z20. 에서 Ø20 구멍 안으로 Z 줄(−Z)로 내려와
     * 방 안에서 X 를 바깥(+X)으로 맞추는 넷째 줄(0부터 3), 다시 +Z 로 진입점 — 그 넷째 줄에서 바가 Ø20 구멍 벽에 닿는다. 명령점은 글에서 읽고(검사기의 글 읽기) 노즈는 손으로:
     * G42 는 진행 오른쪽 — −Z 줄은 +X, +X 줄은 +Z, +Z 줄은 −X 쪽으로 r. 두 직각 꺾임의 교점이라 넷째 줄 노즈는 (z + r, x0 + r) → (z + r, x1 − r), 명령점은 (z, x0) → (z, x1)
     * 같은 비율. 바가 닿기 시작하는 비율을 따로 이분법(여유 식은 HolderCheck.margins)
     */
    @Test
    fun machineEntryHitCoordinates() {
        val c = cases.single { it.name == "뒤보링 f 11" }
        val read = readText(c.drawn.nc)
        val zIn = read[2].seg as Line
        val xOut = read[3].seg as Line
        val zBack = read[4].seg as Line
        assertTrue(zIn.end.z < zIn.start.z && zIn.end.x == zIn.start.x, "${c.drawn.nc}")
        assertTrue(xOut.end.x > xOut.start.x && xOut.end.z == xOut.start.z, "${c.drawn.nc}")
        assertTrue(zBack.end.z > zBack.start.z && zBack.end.x == zBack.start.x, "${c.drawn.nc}")
        val p = plan(c, 0.0)
        val tool = BarShape(c.bar, r, true)
        val bore = Bore(c.sol.drawing, false)
        val na = Pt(xOut.start.z + r, xOut.start.x + r)
        val nb = Pt(xOut.end.z + r, xOut.end.x - r)
        fun touch(u: Double) = HolderCheck.margins(bore, tool, na + (nb - na) * u).bar.m < -HolderCheck.CONTACT_TOL
        assertTrue(!touch(0.0), "넷째 줄 처음(구멍 안)은 바가 안 닿는다")
        var lo = 0.0
        var hi = 1.0
        repeat(60) {
            val mid = (lo + hi) / 2
            if (touch(mid)) hi = mid else lo = mid
        }
        val cmd = xOut.start + (xOut.end - xOut.start) * hi
        val h = p.machineEntry!!.first { it.hit.part == HolderPart.BAR }
        assertTrue((h.first.machine!! - cmd).length < 1e-6, "${h.first.machine} ≠ $cmd")
        assertEquals(3, h.first.machineLine)
        // Z20. 자리(−2)에서는 바가 안 닿아 기계 보정 자기 진입 목록에 −2 가 없고, 직접 보정 진입점(−2)의 기계 좌표도 없다
        assertTrue(p.machineEntry!!.none { it.hit.element == -2 }, "${p.machineEntry!!.map { it.hit.element to it.hit.part }}")
        assertNull(p.hits.single { it.hit.element == -2 && it.hit.part == HolderPart.BAR }.first.machine)
    }

    /**
     * 안전 여유는 직선 거리(사용자 결정 10-28). B(Ø18): 바 앞면(N 을 지나는 주절삭날 연장선)이 맞은편 입구 모서리 (Z0, −9) 에 다가간다 — 반경 여유
     * 0.5(예전 끝점 Z−1.495)에서는 직선 0.043 이었다. 이제 끝점은 그 직선 거리 = 0.5 인 곳: 노즈 x = 9 − r 위에서 모서리와 앞면 직선의 수선 길이가 0.5
     * (따로 이분법 — 그 발이 앞면 토막 안이라 직선 거리 = 토막 거리), 전보다 앞(Z−1.036). 그 자세에서 바와 소재 직선 거리(gap) = 여유 = 0.5.
     * C(안전 여유 0.5): 바 위 앞 끝 E(top) 과 바닥 면 Z−16 의 거리 r + E.z = 0.226 — 막힌 바닥 단면은 깎는 면이라 예외(겹치지 않음만)이고
     * 기준은 바 아래와 맞은편 Ø30 벽 0.5. 안전 여유 0 이면 바 아래가 맞은편 벽에 붙어 0
     */
    @Test
    fun straightGapAtEnd() {
        val k = 95.0 * kotlin.math.PI / 180
        val e1 = Pt(-kotlin.math.cos(k), -kotlin.math.sin(k))
        val eps = 80.0 * kotlin.math.PI / 180
        val e2 = Pt(kotlin.math.cos(kotlin.math.PI - k - eps), -kotlin.math.sin(kotlin.math.PI - k - eps))
        val nVirt = (e1 + e2).unit() * (-r / kotlin.math.sin(eps / 2))
        val corner = Pt(0.0, -9.0)
        // 노즈 (z, 8.6) 에서 앞면 직선과 모서리의 수선 길이(모서리가 아직 앞면 −Z 쪽이면 앞면이 다가간다)
        fun perp(z: Double) = abs(e1 cross (corner - (Pt(z, 8.6) + nVirt)))
        var lo = -1.13
        var hi = 1.0
        repeat(80) {
            val mid = (lo + hi) / 2
            if (perp(mid) < 0.5) lo = mid else hi = mid
        }
        val eb = plan(cases.single { it.name == "B" }, 0.5).end!!
        assertEquals(0, eb.element)
        assertEquals(HolderPart.BAR, eb.part)
        assertTrue(eb.opposite)
        near(hi - r, 18.0, eb.pose.tip, "B 끝점", 1e-6)
        near(-1.036, 18.0, eb.pose.tip, "B 끝점 표")
        assertTrue(eb.pose.tip.z > -1.495, "예전 반경 기준 끝점 Z−1.495 보다 앞")
        // 발이 앞면 토막(E(bottom) ~ E(top)) 안
        val foot = eb.pose.nose + nVirt + e1 * ((corner - (eb.pose.nose + nVirt)) dot e1)
        assertTrue(foot.x > eb.pose.nose.x + r - 11.0 - 8.0 && foot.x < eb.pose.nose.x + r - 11.0 + 8.0, "$foot")
        assertEquals(0.5, eb.margin, 1e-6)
        assertEquals(0.5, eb.gap, 1e-6)
        assertTrue((eb.gapAt - corner).length < 1e-9, "${eb.gapAt}")
        val top = r - 11.0 + 8.0
        val eTop = nVirt + e1 * ((nVirt.x - top) / kotlin.math.sin(k))
        val ec = plan(cases.single { it.name == "C" }, 0.5).end!!
        assertEquals(r + eTop.z, ec.gap, 1e-9)
        assertEquals(-16.0, ec.gapAt.z, 1e-9)
        assertEquals(0.5, ec.margin, 1e-6)
        assertEquals(0.0, plan(cases.single { it.name == "C" }, 0.0).end!!.gap, 1e-6)
    }

    /**
     * 깎는 면 사슬은 끝내 깎을 면까지(10-28 검증 — 예외로 다가간 면을 깎지 않고 그 앞에서 멈추는 경로가 그 면에 안전 여유보다 가까이 갔다).
     * C 에 돌출 길이 16.4: 공구대는 인선 Z ≥ 0.5 − 16.4 = −15.9(바닥 면 0.1 앞)까지 된다. 예전 넓은 사슬은 바닥 면(요소 3)을 구멍 벽(요소 2)에서도 예외로 봐
     * 공구대로 Z−15.9 에서 멈췄다 — 바 앞면이 깎지도 않을 바닥 면 0.326 앞까지. 이제 끝점이 요소 2 라 바닥 면은 예외가 아니고, 바 위 앞 끝 E(top) ~ 바닥 면 =
     * (r + E.z) + (인선 Z + 16)(구석 0.226 에서 Z 로 물러난 만큼 는다)이 0.5 인 인선 Z = −16 + 0.5 − (r + E.z) = −15.726 에서 바(깎는 쪽)로 멈춘다.
     * 돌출 16.2 면 공구대(Z−15.7)가 그보다 앞이라 예전과 같다. C(돌출 없음)는 바닥 면을 깎으니 그대로 바닥 면 위에서 멈춘다(위 [straightGapAtEnd])
     */
    @Test
    fun faceChainOnlyToCutFaces() {
        val k = 95.0 * kotlin.math.PI / 180
        val e1 = Pt(-kotlin.math.cos(k), -kotlin.math.sin(k))
        val eps = 80.0 * kotlin.math.PI / 180
        val e2 = Pt(kotlin.math.cos(kotlin.math.PI - k - eps), -kotlin.math.sin(kotlin.math.PI - k - eps))
        val nVirt = (e1 + e2).unit() * (-r / kotlin.math.sin(eps / 2))
        val eTop = nVirt + e1 * ((nVirt.x - (r - 11.0 + 8.0)) / kotlin.math.sin(k))
        val d0 = r + eTop.z
        assertEquals(0.226, d0, 1e-3)
        val c = cases.single { it.name == "C" }
        val fails = mutableListOf<String>()
        for ((ov, part, z) in listOf(Triple(16.4, HolderPart.BAR, -16 + 0.5 - d0), Triple(16.2, HolderPart.TURRET, -15.7))) {
            val bar = barA.copy(overhang = ov)
            val p = HolderStop.plan(c.sol, c.drawn, r, true, bar, 0.5)
            val e = p.end!!
            assertEquals(2, e.element, "돌출 $ov")
            assertEquals(part, e.part, "돌출 $ov")
            assertTrue(!e.opposite, "돌출 $ov")
            near(z, 30.0, e.pose.tip, "돌출 $ov 끝점", 1e-6)
            // 바와 소재(깎지 않는 바닥 면 포함) 직선 거리가 안전 여유 이상
            assertTrue(e.gap >= 0.5 - 1e-6, "돌출 $ov gap ${e.gap}")
            // 직접 보정은 거꾸로 후퇴로 안전 여유를 지킨다(10-28 과 같다). 기계 보정은 되짚을 수 없어 공구 쪽(구멍 벽 법선 −X — 깎지 않을 바닥 면과 나란히)으로 빼므로
            // 안전 여유로 못 빠지면 RETRACT_TIGHT 만 더해질 수 있다(경고는 그것뿐 · 후퇴 여유와 맞음 — 그 값이 표본보다 넉넉하지 않음은 검사기가 본다)
            assertTrue(p.warnings.all { it == StopWarning.RETRACT_TIGHT }, "돌출 $ov ${p.warnings}")
            assertTrue(p.direct!!.retractClear >= 0.5 - HolderStop.TEXT_TOL, "돌출 $ov ${p.direct!!.retractClear}")
            assertEquals(p.machine!!.retractClear < 0.5 - HolderStop.TEXT_TOL, StopWarning.RETRACT_TIGHT in p.warnings, "돌출 $ov 기계 보정 후퇴 여유 ${p.machine!!.retractClear}")
            StopChecker(c.sol, c.drawn, r, true, bar, 0.5).check(p, 0.002, fails)
        }
        // 끊은 점은 안전한 쪽으로 반올림(−15.72594 → Z−15.725)
        near(-15.725, 30.0, HolderStop.plan(c.sol, c.drawn, r, true, barA.copy(overhang = 16.4), 0.5).direct!!.stop, "직접 보정 끊은 점", 1e-9)
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    /**
     * 기계 보정은 거꾸로 후퇴하지 않는다(회사 꼴 — 보정 켠 채 깎은 길을 되짚으려면 보정 쪽을 바꿔야 하고 원호 뒤에서는 FANUC 이 길을 정하지 않았다).
     * 10-28 의 '거꾸로 후퇴의 G40 점 반올림' 성질은 대상이 없어졌다 — 같은 자리의 반올림 성질(끝 블록 교점이 안전 끝점을 넘지 않음)은 검사기
     * (StopChecker.checkMachine — 이 파일의 제어기 모형으로 교점을 따로)가 본다. 3점호 내경 · 안전 여유 1.27(그 사례): 직접 보정은 거꾸로 후퇴로 1.27 을 지키고,
     * 기계 보정은 공구 쪽으로 빼며 바 맞은편이 끝점의 1.27 보다 가까워져 RETRACT_TIGHT
     */
    @Test
    fun machineRetractNeverReverses() {
        val a = Arc3Input(inner = true, noseR = r, x1 = 59.744, z1 = 12.411, x2 = 57.336, z2 = 8.549, x3 = 56.799, z3 = 6.893, zClear = 2.202)
        val sol = RoundCalc.arc3(a).solution!!
        val drawn = RoundCalc.machine(a).solution!!
        val bar = BoringBar(20.394, 46.203, InsertShape.C, 5.916, 91.025)
        val p = HolderStop.plan(sol, drawn, r, false, bar, 1.27)
        assertEquals(RetractKind.REVERSE, p.direct!!.retract)
        assertTrue(p.direct!!.retractClear >= 1.27 - HolderStop.TEXT_TOL, "${p.direct!!.retractClear}")
        val m = p.machine!!
        assertEquals(RetractKind.LIFT, m.retract)
        assertTrue(m.lack >= 0)
        assertTrue(m.retractClear < 1.27 - HolderStop.TEXT_TOL && p.warnings == listOf(StopWarning.RETRACT_TIGHT), "${m.retractClear} ${p.warnings}")
        assertEquals(listOf("G40", "G0Z200."), m.nc.takeLast(2))
        val f = mutableListOf<String>()
        StopChecker(sol, drawn, r, false, bar, 1.27).check(p, 0.002, f)
        assertTrue(f.isEmpty(), f.joinToString("\n"))
    }

    @Test
    fun badSafeValue() {
        val c = cases.single { it.name == "A" }
        for (v in listOf(-0.1, Double.NaN, Double.POSITIVE_INFINITY, 2 * MAX_MM)) {
            assertEquals(HolderSkip.BAD_INPUT, plan(c, v).result.skip, "$v")
            assertTrue(!HolderStop.safeOk(v))
        }
        assertTrue(HolderStop.safeOk(0.0) && HolderStop.safeOk(0.5))
        // 기본값 0.5
        assertEquals(plan(c, 0.5).end, HolderStop.plan(c.sol, c.drawn, r, true, barA).end)
    }

    /** 원호를 끊어 쓰기: 끝까지면 [NcLines.arc] 와 같은 글, 가운데에서 끊으면 앞 조각은 그 글 그대로 */
    @Test
    fun arcUntilKeepsPieces() {
        val c = Pt(0.0, 0.0)
        fun p(deg: Double) = Pt(kotlin.math.cos(deg * kotlin.math.PI / 180), kotlin.math.sin(deg * kotlin.math.PI / 180)) * 10.0
        val a = Arc(p(10.0), p(200.0), c, cw = false)
        val full = NcLines().apply { move("G0", a.start); arc(a) }
        val until = NcLines().apply { move("G0", a.start); arcUntil(a, a.end) }
        assertEquals(full.lines, until.lines)
        assertEquals(full.refs, until.refs)
        // 190° → 3 조각(63.3°). 150° 에서 끊으면 앞 두 조각 그대로 + 150° 까지
        val cut = NcLines().apply { move("G0", a.start); arcUntil(a, p(150.0)) }
        assertEquals(full.lines.take(3), cut.lines.take(3))
        // 앞 줄도 G3 이라 이동 G 는 빠진다(모달)
        assertEquals("G3", ncWords(cut.lines[1]).first())
        assertEquals("X${Fmt.word(p(150.0).xDia)}Z${Fmt.word(p(150.0).z)}R10.", cut.lines[3])
        // 끝을 조금 넘어도(볼록 모서리에서 늘인 보정선) 마지막 조각이 늘어난다
        val over = NcLines().apply { move("G0", a.start); arcUntil(a, p(203.0)) }
        assertEquals(full.lines.take(3), over.lines.take(3))
        assertEquals(4, over.lines.size)
    }

    /** 글 다시 읽기(회사 표기): 모달 축 · 모달 이동 G(빠진 G 는 앞 줄 — 원호 다음 원호) · R 원호 중심 · G40 · 축 없는 줄. 옛 꼴(빈칸 · G00)도 같게 읽는다 */
    @Test
    fun readNcRoundTrip() {
        val nc = listOf("G0X20.Z1.", "G1Z-5.", "G3X30.Z-10.R5.", "X40.Z-15.R5.", "G40G1X38.", "Z-16.")
        val read = readNc(nc)
        assertNull(read[0])
        assertEquals(Line(Pt(1.0, 10.0), Pt(-5.0, 10.0)), read[1])
        val a = read[2] as Arc
        assertTrue((a.center - Pt(-10.0, 10.0)).length < 1e-12, "${a.center}")
        assertTrue(!a.cw)
        val b = read[3] as Arc
        assertTrue(!b.cw && abs(b.radius - 5.0) < 1e-9, "$b")
        assertEquals(Line(Pt(-15.0, 20.0), Pt(-15.0, 19.0)), read[4])
        assertEquals(Line(Pt(-15.0, 19.0), Pt(-16.0, 19.0)), read[5])
        assertEquals(read, readNc(nc.legacy))
        // 원호 뒤 축 없는 `G40`(노즈R 0 의 보정 취소 — 원호 모달이어도)은 움직이지 않는다
        assertEquals(Line(Pt(-10.0, 15.0), Pt(-10.0, 15.0)), readNc(listOf("G0X20.Z1.", "G1Z-5.", "G3X30.Z-10.R5.", "G40")).last())
    }
}
