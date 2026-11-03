package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.ContactKind
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimExamples
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StateStep
import com.bmt.nosecalc.calc.sim.StepSrc
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-10-29) — 보정 시작 · 취소 형식 C(No.5003#1 SUV = 1)를 FANUC 설명서(FF B-64604EN-1/01 표 5.4.1(a) 173쪽 ·
 * '이동 없는 시작' 179~180쪽 · 그림 5.4.4(e)(f) 204~205쪽 · 부록 No.5003 373쪽)와 손 계산으로 본다. 구현 코드(GComp)는 쓰지 않고
 * 기대 노즈 자리는 따로 푼 값(소수 9자리)을 글로 적었다. 글 · 도형은 이 시험을 위해 지은 것이다(가상 좌표).
 *
 * 형식 C 규칙(손 계산의 바탕):
 * - G40 상태 노즈 = P − t(t = 노즈 중심 → 가상 인선, 팁 번호 표는 [t]).
 * - 이동 없는 G41/G42 줄: 그 줄에서 노즈 P − t → P + r·n₁(n₁ = 다음 이동 블록 처음의 공구 쪽 법선 — 'shifts … vertical to the block with tool
 *   movement subsequent to the start-up block'). 그 줄의 모달(G00/G01)로.
 * - 보정 중 블록 끝: 다음 블록과 안쪽이면 교점, 바깥(≤ 90°)이면 늘인 교점, 접하면 끝 법선. 이동 없는 G40 앞 블록 끝 = 끝 법선 E + r·nₑ.
 * - 이동 없는 G40 줄: 노즈 E + r·nₑ → E − t('shifts … vertical to the block preceding the cancel block').
 * - 형식 A · B: 두 줄 다 움직이지 않는다. 다음 이동이 시작 블록(안쪽 = 교점, 바깥 = 다음 블록 처음 법선), G40 뒤 첫 이동이 남은 벡터를 지운다.
 */
class VerifyTypeCHandTest {
    private val r = 0.4
    private val pre = listOf("T0101", "G97S1000M03", "G99")
    private val typeC = MachineSetup(compStartup = CompStartup.C)
    private val typeAB = MachineSetup(compStartup = CompStartup.AB)
    private val tol = 1e-7

    /** FANUC 가상 인선 번호(FF 그림 5.2.2(a) — X 위 · Z 오른쪽): 노즈 중심 → 가상 인선. Pt(z, x 반경) */
    private fun t(tip: Int): Pt = when (tip) {
        1 -> Pt(r, r)
        2 -> Pt(-r, r)
        3 -> Pt(-r, -r)
        4 -> Pt(r, -r)
        5 -> Pt(r, 0.0)
        6 -> Pt(0.0, r)
        7 -> Pt(-r, 0.0)
        8 -> Pt(0.0, -r)
        else -> Pt(0.0, 0.0)
    }

    private fun d(z: Double, xDia: Double) = Pt.ofDia(z, xDia)

    private fun s(p: Pt) = "X${p.xDia} Z${p.z}"

    private fun near(want: Pt, got: Pt, msg: String, tl: Double = tol) =
        assertTrue((want - got).length <= tl, "$msg: 기대 ${s(want)}, 실제 ${s(got)} (차이 ${(want - got).length})")

    private fun run(body: List<String>, tip: Int, m: MachineSetup, tools: Map<Int, ToolOffset>? = null): SimRun =
        GSim.run(SimInput((pre + body).joinToString("\n"), m, tools ?: mapOf(1 to ToolOffset(r, tip))))

    private fun moves(run: SimRun) = run.steps.filterIsInstance<MoveStep>()

    private fun ghostNose(run: SimRun, line: Int): Pt = run.ghosts.last { it.line == line }.nose

    // ---------------------------------------------------------------- 거리(공기 검사)

    private fun ang(p: Pt) = atan2(p.x, p.z)

    private fun wrap2Pi(a: Double): Double {
        var v = a % (2 * PI)
        if (v < 0) v += 2 * PI
        return v
    }

    private fun pointSeg(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val v = s.end - s.start
            val l2 = v dot v
            if (l2 < 1e-18) (p - s.start).length else {
                val k = (((p - s.start) dot v) / l2).coerceIn(0.0, 1.0)
                (p - (s.start + v * k)).length
            }
        }
        is Arc -> {
            val dir = if (s.cw) -1.0 else 1.0
            val sweep = wrap2Pi(dir * (ang(s.end - s.center) - ang(s.start - s.center)))
            val u = wrap2Pi(dir * (ang(p - s.center) - ang(s.start - s.center)))
            if (u <= sweep) abs((p - s.center).length - s.radius) else min((p - s.start).length, (p - s.end).length)
        }
    }

    /** 노즈 중심 경로(직선 · 원호)와 부품 면 사이 가장 가까운 거리 — 2 001 점 */
    private fun minDist(nose: Seg, profile: List<Seg>): Double {
        var best = Double.POSITIVE_INFINITY
        for (i in 0..2000) {
            val k = i / 2000.0
            val p = when (nose) {
                is Line -> nose.start + (nose.end - nose.start) * k
                is Arc -> {
                    val dir = if (nose.cw) -1.0 else 1.0
                    val sw = wrap2Pi(dir * (ang(nose.end - nose.center) - ang(nose.start - nose.center)))
                    val a = ang(nose.start - nose.center) + dir * sw * k
                    nose.center + Pt(kotlin.math.cos(a), kotlin.math.sin(a)) * nose.radius
                }
            }
            for (sg in profile) best = min(best, pointSeg(p, sg))
        }
        return best
    }

    // ---------------------------------------------------------------- 회사 꼴 흐름 넷

    /**
     * 회사 꼴 흐름 하나: 4번 줄 `G0X100.Z200.`(앞 블록 끝 자리) → 혼자 줄 G41/G42([g4x]) → `G0X#Z20.` → `Z#` → `G1 …` → 빼는 줄 → 혼자 줄 G40([g40]) →
     * `G0Z…`([zLine]). [ends] = 형식 C 블록 끝 노즈(팁과 무관 — 손 계산), [abFirst] = 형식 A · B 에서 시작 블록이 되는 첫 G0 줄(6줄)의 끝,
     * [cuts] = 이송 줄(형식 · 팁과 무관해야), [e] · [ne] = 마지막 지령점 · 끝 법선, [profile] = 부품 면(공기 검사),
     * [depth] = 팁 1~9 의 G40 옮김이 마지막 줄의 선을 넘는 깊이 max(0, r + t·nₑ)(손 계산).
     */
    private class Flow(
        val name: String,
        val body: List<String>,
        val shiftEnd: Pt,
        val ends: Map<Int, Pt>,
        val abFirst: Pt,
        val abOuterStart: Boolean,
        val cuts: IntRange,
        val e: Pt,
        val ne: Pt,
        val zLine: Int,
        val zEnd: Double,
        val profile: List<Seg>,
        val depth: List<Double>,
        val arcs: Map<Int, Pair<Pt, Double>>,
    ) {
        val g4x = 5
        val g40 = zLine - 1
        val p0 = Pt.ofDia(200.0, 100.0)
    }

    private val sq = 1 / sqrt(2.0)

    private val flows = listOf(
        // 외경 G42 Z−(앞 → 척): 끝면 X36→X40 · 테이퍼 X50 Z-5 · Ø50 · R.5(오목) · 어깨 X56 으로 빼기
        Flow(
            "외경 G42 Z-",
            listOf("G0X100.Z200.", "G42", "G0X36.Z20.", "Z2.", "G1Z0.F.1", "X40.", "X50.Z-5.", "Z-9.5F.15", "G2X51.Z-10.R.5", "G1X56.F.3", "G40", "G0Z200.", "M30"),
            shiftEnd = d(199.929986666, 100.787650006),
            ends = mapOf(
                6 to d(19.964721024, 36.8), 7 to d(2.0, 36.8), 8 to d(0.4, 36.8), 9 to d(0.4, 40.331370850),
                10 to d(-4.834314575, 50.8), 11 to d(-9.5, 50.8), 12 to d(-9.6, 51.0), 13 to d(-9.6, 56.0),
            ),
            abFirst = d(19.964721024, 36.8), abOuterStart = false, // 안쪽 시작 — 세 형식 같은 교점
            cuts = 8..13, e = d(-10.0, 56.0), ne = Pt(1.0, 0.0), zLine = 15, zEnd = 200.0,
            profile = listOf(
                Line(d(0.0, 36.0), d(0.0, 40.0)), Line(d(0.0, 40.0), d(-5.0, 50.0)), Line(d(-5.0, 50.0), d(-9.5, 50.0)),
                Arc(d(-9.5, 50.0), d(-10.0, 51.0), d(-9.5, 51.0), true), Line(d(-10.0, 51.0), d(-10.0, 56.0)),
            ),
            depth = listOf(0.8, 0.0, 0.0, 0.8, 0.8, 0.4, 0.0, 0.4, 0.4),
            arcs = mapOf(12 to (d(-9.5, 51.0) to 0.1)),
        ),
        // 외경 G41 Z+(같은 바이트로 뒤 → 앞): 어깨 X60→X51 · R.5 · Ø50 · 테이퍼 · 끝면을 X0 까지 깎고 바로 G40
        Flow(
            "외경 G41 Z+",
            listOf("G0X100.Z200.", "G41", "G0X60.Z20.", "Z-10.", "G1X51.F.1", "G3X50.Z-9.5R.5", "G1Z-5.", "X40.Z0.", "X0.", "G40", "G0Z200.", "M30"),
            shiftEnd = d(200.044172610, 99.204893012),
            ends = mapOf(
                6 to d(20.022154055, 59.2), 7 to d(-9.6, 59.2), 8 to d(-9.6, 51.0), 9 to d(-9.5, 50.8),
                10 to d(-4.834314575, 50.8), 11 to d(0.4, 40.331370850), 12 to d(0.4, 0.0),
            ),
            // 바깥 시작(6.34°): 형식 C 는 보정 중 블록이라 늘인 교점, 형식 A 는 시작 블록이라 다음 블록 처음 법선 — 공기에서 0.022 다름
            abFirst = d(20.0, 59.2), abOuterStart = true,
            cuts = 8..12, e = d(0.0, 0.0), ne = Pt(1.0, 0.0), zLine = 14, zEnd = 200.0,
            profile = listOf(
                Line(d(-10.0, 58.0), d(-10.0, 51.0)), Arc(d(-10.0, 51.0), d(-9.5, 50.0), d(-9.5, 51.0), false),
                Line(d(-9.5, 50.0), d(-5.0, 50.0)), Line(d(-5.0, 50.0), d(0.0, 40.0)), Line(d(0.0, 40.0), d(0.0, 0.0)),
            ),
            depth = listOf(0.8, 0.0, 0.0, 0.8, 0.8, 0.4, 0.0, 0.4, 0.4),
            arcs = mapOf(9 to (d(-9.5, 51.0) to 0.1)),
        ),
        // 내경 G41 Z−(보링바): 들머리 모따기 X26→X22 · Ø22 · 바닥 R1(오목) · 바닥 X20→X12 · 구멍 쪽으로 비스듬히 빼기
        Flow(
            "내경 G41 Z-",
            listOf("G0X100.Z200.", "G41", "G0X26.Z20.", "Z2.", "G1Z0.F.1", "X22.Z-2.", "Z-14.", "G3X20.Z-15.R1.", "G1X12.", "X8.Z-14.F.3", "G40", "G0Z150.", "M30"),
            shiftEnd = d(200.080538329, 99.216383825),
            ends = mapOf(
                6 to d(20.040685783, 25.2), 7 to d(2.0, 25.2), 8 to d(0.165685425, 25.2), 9 to d(-1.834314575, 21.2),
                10 to d(-14.0, 21.2), 11 to d(-14.6, 20.0), 12 to d(-14.6, 12.188854382), 13 to d(-13.642229124, 8.357770876),
            ),
            abFirst = d(20.0, 25.2), abOuterStart = true,
            cuts = 8..13, e = d(-14.0, 8.0), ne = Pt(2 / sqrt(5.0), 1 / sqrt(5.0)), zLine = 15, zEnd = 150.0,
            profile = listOf(
                Line(d(0.0, 26.0), d(-2.0, 22.0)), Line(d(-2.0, 22.0), d(-14.0, 22.0)),
                Arc(d(-14.0, 22.0), d(-15.0, 20.0), d(-14.0, 20.0), false), Line(d(-15.0, 20.0), d(-15.0, 12.0)),
            ),
            depth = listOf(0.936656315, 0.221114562, 0.0, 0.578885438, 0.757770876, 0.578885438, 0.042229124, 0.221114562, 0.4),
            arcs = mapOf(11 to (d(-14.0, 20.0) to 0.6)),
        ),
        // 내경 G42 Z+(구멍 안에서 앞으로): 바닥 X14→X20 · R1(오목) · Ø22 · 모따기 X26 Z0 · 앞으로 비스듬히 빼기
        Flow(
            "내경 G42 Z+",
            listOf("G0X100.Z200.", "G42", "G0X14.Z20.", "Z-10.", "G1Z-15.F.1", "X20.", "G2X22.Z-14.R1.", "G1Z-2.", "X26.Z0.", "X24.Z1.F.3", "G40", "G0Z150.", "M30"),
            shiftEnd = d(199.907059608, 100.778105606),
            ends = mapOf(
                6 to d(19.952885084, 14.8), 7 to d(-10.0, 14.8), 8 to d(-14.6, 14.8), 9 to d(-14.6, 20.0),
                10 to d(-14.0, 21.2), 11 to d(-1.834314575, 21.2), 12 to d(0.0, 24.868629150), 13 to d(0.717157288, 23.434314575),
            ),
            abFirst = d(19.952885084, 14.8), abOuterStart = false,
            cuts = 8..13, e = d(1.0, 24.0), ne = Pt(-sq, -sq), zLine = 15, zEnd = 150.0,
            profile = listOf(
                Line(d(-15.0, 14.0), d(-15.0, 20.0)), Arc(d(-15.0, 20.0), d(-14.0, 22.0), d(-14.0, 20.0), true),
                Line(d(-14.0, 22.0), d(-2.0, 22.0)), Line(d(-2.0, 22.0), d(0.0, 26.0)),
            ),
            depth = listOf(0.0, 0.4, 0.965685425, 0.4, 0.117157288, 0.117157288, 0.682842712, 0.682842712, 0.4),
            arcs = mapOf(10 to (d(-14.0, 20.0) to 0.6)),
        ),
        // 외경 G42 Z− 볼록 모서리 R1(회사 원호의 대부분 — 모서리 R): 끝면 X36→X38 · G3 R1 · Ø40 · 어깨 X46 으로 빼기. 노즈 원호 = R + r = 1.4
        Flow(
            "외경 G42 Z- 볼록 R1",
            listOf("G0X100.Z200.", "G42", "G0X36.Z20.", "Z2.", "G1Z0.F.1", "X38.", "G3X40.Z-1.R1.", "G1Z-8.", "X46.F.3", "G40", "G0Z200.", "M30"),
            shiftEnd = d(199.929986666, 100.787650006),
            ends = mapOf(
                6 to d(19.964721024, 36.8), 7 to d(2.0, 36.8), 8 to d(0.4, 36.8), 9 to d(0.4, 38.0),
                10 to d(-1.0, 40.8), 11 to d(-7.6, 40.8), 12 to d(-7.6, 46.0),
            ),
            abFirst = d(19.964721024, 36.8), abOuterStart = false,
            cuts = 8..12, e = d(-8.0, 46.0), ne = Pt(1.0, 0.0), zLine = 14, zEnd = 200.0,
            profile = listOf(
                Line(d(0.0, 36.0), d(0.0, 38.0)), Arc(d(0.0, 38.0), d(-1.0, 40.0), d(-1.0, 38.0), false),
                Line(d(-1.0, 40.0), d(-8.0, 40.0)), Line(d(-8.0, 40.0), d(-8.0, 46.0)),
            ),
            depth = listOf(0.8, 0.0, 0.0, 0.8, 0.8, 0.4, 0.0, 0.4, 0.4),
            arcs = mapOf(10 to (d(-1.0, 38.0) to 1.4)),
        ),
    )

    /**
     * 1. 회사 꼴 흐름(방향 넷 + 볼록 모서리 R) × 팁 1~9 × 형식 C · A·B: 블록 끝 노즈가 손 계산과 같고(깎는 줄은 팁 · 형식과 무관), 형식 C 의 G41/G42 · G40 줄 옮김이
     * P − t → P + r·n₁ · E + r·nₑ → E − t, 그 뒤 G0Z 는 Z 만. G40 옮김이 마지막 줄 선을 넘는 깊이 = max(0, r + t·nₑ).
     * 급송(꺾인 G00 포함) · G41/G42 옮김은 부품 면에서 r 이상(공기), G40 옮김은 깊이 0 인 팁에서만 공기.
     */
    @Test
    fun companyFlowFourDirectionsAllTips() {
        for (f in flows) {
            val base = moves(run(f.body, 3, typeC)).filter { it.line in f.cuts }
            for (tip in 1..9) {
                val tag = "${f.name} 팁 $tip"
                val tv = t(tip)
                // ---- 형식 C
                val c = run(f.body, tip, typeC)
                assertNull(c.stop, "$tag C 멈춤 ${c.stop}")
                for ((line, p) in f.ends) near(p, ghostNose(c, line), "$tag C 줄 $line")
                val sh = moves(c).filter { it.line == f.g4x }
                assertTrue(sh.isNotEmpty() && sh.all { it.src == StepSrc.SHIFT_START && it.kind == MoveKind.RAPID }, "$tag G4x 옮김 $sh")
                near(f.p0 - tv, sh.first().nose.start, "$tag G4x 옮김 처음 = P − t")
                near(f.shiftEnd, sh.last().nose.end, "$tag G4x 옮김 끝 = P + r·n₁")
                // 팁 벡터가 −r·nₑ 이면(외경 끝 법선 +Z 에 팁 7) 옮김 길이 0 — 걸음 없이 고스트만
                val cancel = moves(c).filter { it.line == f.g40 }
                val zero = ((f.e - tv) - (f.e + f.ne * r)).length < 1e-12
                assertEquals(if (zero) 0 else 1, cancel.size, "$tag G40 옮김")
                cancel.singleOrNull()?.let {
                    assertEquals(StepSrc.SHIFT_CANCEL, it.src)
                    assertEquals(MoveKind.FEED, it.kind, "$tag G40 줄 모달 G01")
                    near(f.e + f.ne * r, it.nose.start, "$tag G40 옮김 처음 = E + r·nₑ")
                }
                near(f.e - tv, ghostNose(c, f.g40), "$tag G40 옮김 끝 = E − t")
                val dep = max(0.0, r - ((ghostNose(c, f.g40) - f.e) dot f.ne))
                assertEquals(f.depth[tip - 1], dep, 1e-8, "$tag G40 옮김 깊이")
                val zm = moves(c).filter { it.line == f.zLine }
                assertTrue(zm.all { abs(it.nose.start.x - it.nose.end.x) < 1e-12 }, "$tag G0Z 는 Z 만")
                near(Pt(f.zEnd, f.e.x) - tv, zm.last().nose.end, "$tag G0Z 끝")
                for ((a, b) in moves(c).zipWithNext()) near(a.nose.end, b.nose.start, "$tag 이음 ${a.line}→${b.line}", 1e-9)
                val ca = c.issues.filter { it.code == IssueCode.COMP_CANCEL_ALONE }
                assertEquals(listOf(f.g40), ca.map { it.line }, tag)
                assertEquals(listOf("C"), ca.single().args)
                assertTrue(c.issues.none { it.code == IssueCode.COMP_TYPE_A }, "$tag C 에는 이동 있는 시작 · 취소가 없다")
                // 깎는 줄: 팁 3 과 같다
                val cut = moves(c).filter { it.line in f.cuts }
                assertEquals(base.size, cut.size, tag)
                for ((p, q) in base.zip(cut)) {
                    near(p.nose.start, q.nose.start, "$tag 줄 ${q.line} 처음(팁 무관)", 1e-9)
                    near(p.nose.end, q.nose.end, "$tag 줄 ${q.line} 끝(팁 무관)", 1e-9)
                }
                // 보정 중 원호 R: 노즈 원호 = 같은 중심 · R − r(오목)
                for ((line, cr) in f.arcs) {
                    val a = moves(c).single { it.line == line }.nose
                    assertTrue(a is Arc, "$tag 줄 $line 원호")
                    near(cr.first, a.center, "$tag 줄 $line 중심", 1e-9)
                    assertEquals(cr.second, a.radius, 1e-9, "$tag 줄 $line 노즈 원호 반지름")
                }
                // 공기: 급송 · G4x 옮김(그리고 깊이 0 이면 G40 옮김 · G0Z)
                val deep = f.depth[tip - 1] > 1e-9
                for (m in moves(c)) {
                    val air = m.kind == MoveKind.RAPID && !(deep && m.line == f.zLine) || m.src == StepSrc.SHIFT_CANCEL && !deep
                    if (air) assertTrue(minDist(m.nose, f.profile) >= r - 1e-6, "$tag C 줄 ${m.line} ${m.src} 공기 ${minDist(m.nose, f.profile)}")
                }
                // ---- 형식 A · B
                val ab = run(f.body, tip, typeAB)
                assertNull(ab.stop, "$tag AB 멈춤 ${ab.stop}")
                assertTrue(moves(ab).none { it.line == f.g4x || it.line == f.g40 }, "$tag AB 는 G4x · G40 줄에서 안 움직임")
                near(f.p0 - tv, moves(ab).first { it.line == 6 }.nose.start, "$tag AB 시작 블록 처음 = P − t")
                near(f.abFirst, ghostNose(ab, 6), "$tag AB 시작 블록 끝")
                for ((line, p) in f.ends) if (line > 6) near(p, ghostNose(ab, line), "$tag AB 줄 $line")
                assertEquals(f.abOuterStart, ab.issues.any { it.code == IssueCode.COMP_TYPE_A && it.line == 6 }, "$tag AB 바깥 시작 알림")
                val zab = moves(ab).filter { it.line == f.zLine }
                near(f.e + f.ne * r, zab.first().nose.start, "$tag AB G0Z 처음 = 끝 법선")
                near(Pt(f.zEnd, f.e.x) - tv, zab.last().nose.end, "$tag AB G0Z 끝")
                assertEquals(emptyList(), ab.issues.single { it.code == IssueCode.COMP_CANCEL_ALONE }.args)
                val cutAb = moves(ab).filter { it.line in f.cuts }
                assertEquals(cut.size, cutAb.size, tag)
                for ((p, q) in cut.zip(cutAb)) {
                    near(p.nose.start, q.nose.start, "$tag 줄 ${q.line} 처음 C = AB", 1e-9)
                    near(p.nose.end, q.nose.end, "$tag 줄 ${q.line} 끝 C = AB", 1e-9)
                }
                for (m in moves(ab).filter { it.kind == MoveKind.RAPID }) {
                    assertTrue(minDist(m.nose, f.profile) >= r - 1e-6, "$tag AB 줄 ${m.line} 공기 ${minDist(m.nose, f.profile)}")
                }
            }
        }
    }

    /**
     * 끝면을 X0 까지 깎고 바로 G40(외경 G41 Z+): 형식 C 의 G40 옮김이 끝면을 파는 팁(r + t·nₑ > 0 — 1 · 4 · 5 · 6 · 8 · 9)에서만
     * 소재 모형이 CANCEL_CUT(노랑)을 내고, 2 · 3 · 7 과 형식 A · B 는 닿음이 없다. 둥근 공구(노즈 원만)로 — 바이트 몸통 영향을 뺀다.
     */
    @Test
    fun cancelShiftDigsFaceOnlyForWrongTips() {
        val f = flows[1]
        val bar = StockSpec(58.0, 40.0, 0.0, 0.0, 145.0)
        for (tip in 1..9) {
            val tools = mapOf(1 to ToolOffset(r, tip, ToolKind.ROUND))
            for (m in listOf(typeC, typeAB)) {
                val run = run(f.body, tip, m, tools)
                assertNull(run.stop)
                val contacts = StockSim(bar, run, tools).also { it.cutTo(run.totalSec) }.contacts
                val got = contacts.map { it.kind to it.line }
                println("끝면 G40 팁 $tip ${m.compStartup}: $got ${contacts.map { it.area }}")
                if (m == typeC && f.depth[tip - 1] > 0) {
                    // 판 자리에서 G0Z 로 나오며 판 홈 가장자리를 조금 더 깎을 수 있다(그 급송도 노랑 — 판 것의 뒤끝)
                    assertEquals(ContactKind.CANCEL_CUT to f.g40, got.first(), "팁 $tip C")
                    assertTrue(got.drop(1).all { it == ContactKind.RAPID_CUT to f.zLine }, "팁 $tip C $got")
                } else {
                    assertEquals(emptyList(), got, "팁 $tip ${m.compStartup}")
                }
            }
        }
    }

    // ---------------------------------------------------------------- 이동 없는 줄 둘 · G40 바로 G41

    private val g40g41 = listOf(
        "G0X100.Z200.", "G42", "G0X46.Z20.", "Z2.", "G1Z0.F.1", "X50.", "Z-10.", "X56.F.3", // 4~11
        "G40", "G41", "X50.F.1", "Z0.", "X46.", "G40", "G0Z200.", "M30", // 12~19
    )

    /**
     * 2. 'G40 바로 G41'(이동 없는 줄 둘) — 형식 C: G40 줄 E + r·nₑ → E − t, G41 줄 E − t → P + r·n₁(G01 모달이라 이송). 깎는 줄은 팁과 무관.
     * 손 계산(형식 C · 0.4R): 1차 X46.8 Z19.970166878 / Z2. / Z0.4 · X50.8 Z0.4(바깥 90° — 늘인 교점) · X50.8 Z-9.6 · X56 Z-9.6,
     * G41 옮김 끝 X56 Z-9.6, 2차 X50.8 Z-9.6 · X50.8 Z0.4 · X46 Z0.4. 형식 A · B 는 설명서에 경로가 없어 멈춤(COMP_CANCEL_NO_MOVE).
     */
    @Test
    fun g40ThenG41AndIdleBlocks() {
        val ends = mapOf(
            6 to d(19.970166878, 46.8), 7 to d(2.0, 46.8), 8 to d(0.4, 46.8), 9 to d(0.4, 50.8), 10 to d(-9.6, 50.8), 11 to d(-9.6, 56.0),
            13 to d(-9.6, 56.0), 14 to d(-9.6, 50.8), 15 to d(0.4, 50.8), 16 to d(0.4, 46.0),
        )
        val e1 = d(-10.0, 56.0)
        val e2 = d(0.0, 46.0)
        val base = moves(run(g40g41, 3, typeC)).filter { it.line in 14..16 }
        for (tip in 1..9) {
            val tv = t(tip)
            val c = run(g40g41, tip, typeC)
            assertNull(c.stop, "팁 $tip ${c.stop}")
            for ((line, p) in ends) near(p, ghostNose(c, line), "팁 $tip 줄 $line")
            // 팁 7(t = −r·nₑ, nₑ = +Z)은 세 옮김 모두 길이 0 — 걸음 없이 고스트만
            val zero = tip == 7
            val g40 = moves(c).filter { it.line == 12 }
            assertEquals(if (zero) 0 else 1, g40.size, "팁 $tip G40")
            g40.singleOrNull()?.let { near(d(-9.6, 56.0), it.nose.start, "팁 $tip G40 처음") }
            near(e1 - tv, ghostNose(c, 12), "팁 $tip G40 끝 = E − t")
            val g41 = moves(c).filter { it.line == 13 }
            assertEquals(zero, g41.isEmpty(), "팁 $tip G41")
            assertTrue(g41.all { it.src == StepSrc.SHIFT_START && it.kind == MoveKind.FEED }, "팁 $tip G41 옮김은 G01 모달")
            g41.firstOrNull()?.let { near(e1 - tv, it.nose.start, "팁 $tip G41 처음 = P − t") }
            val g40b = moves(c).filter { it.line == 17 }
            assertEquals(if (zero) 0 else 1, g40b.size, "팁 $tip 둘째 G40")
            g40b.singleOrNull()?.let { near(d(0.4, 46.0), it.nose.start, "팁 $tip 둘째 G40 처음") }
            near(e2 - tv, ghostNose(c, 17), "팁 $tip 둘째 G40 끝")
            for ((a, b) in moves(c).zipWithNext()) near(a.nose.end, b.nose.start, "팁 $tip 이음 ${a.line}", 1e-9)
            for ((p, q) in base.zip(moves(c).filter { it.line in 14..16 })) {
                near(p.nose.start, q.nose.start, "팁 $tip 2차 줄 ${q.line} 처음", 1e-9)
                near(p.nose.end, q.nose.end, "팁 $tip 2차 줄 ${q.line} 끝", 1e-9)
            }
            val ab = run(g40g41, tip, typeAB)
            assertEquals(IssueCode.COMP_CANCEL_NO_MOVE, ab.stop?.code, "팁 $tip AB")
            assertEquals(14, ab.stop?.line)
        }
        // 사이에 M 줄 하나(취소 상태라 셈하지 않음) — 같은 노즈
        val withM = g40g41.toMutableList().apply { add(9, "M09") } // G40 / M09 / G41
        val cm = run(withM, 3, typeC)
        assertNull(cm.stop, "${cm.stop}")
        near(d(-9.6, 56.0), ghostNose(cm, 14), "G40 · M09 · G41 옮김 끝")
        assertEquals(IssueCode.COMP_CANCEL_NO_MOVE, run(withM, 3, typeAB).stop?.code)
        // 이동 없는 G41 뒤 M 줄 하나: 옮김 이동이 M 걸음보다 먼저(블록 순서)
        val oneM = g40g41.toMutableList().apply { add(10, "M08") } // G41 / M08 / X50.
        val c1 = run(oneM, 3, typeC)
        assertNull(c1.stop, "${c1.stop}")
        val iShift = c1.steps.indexOfFirst { it is MoveStep && it.line == 13 }
        val iM = c1.steps.indexOfFirst { it is StateStep && it.line == 14 }
        assertTrue(iShift in 0 until iM, "옮김 $iShift · M08 $iM")
        near(d(-9.6, 50.8), ghostNose(c1, 15), "G41 · M08 · X50.")
        // M 줄 둘: 다음 이동 블록을 읽지 못함 — 멈춤(설계 3.2 · FF 5.4.3 '(N − 2) ≥ M' 의 N = 3 가정)
        val twoM = g40g41.toMutableList().apply { addAll(10, listOf("M08", "M09")) }
        assertEquals(IssueCode.COMP_IDLE_BLOCKS to 15, run(twoM, 3, typeC).stop?.let { it.code to it.line })
        // 빼는 줄 뒤 M 줄 하나 그다음 G40: 옮김은 빼는 줄(X56.) 끝 법선에서
        val mBefore = g40g41.toMutableList().apply { add(8, "M09") } // X56.F.3 / M09 / G40
        val c2 = run(mBefore, 3, typeC)
        assertNull(c2.stop, "${c2.stop}")
        val sh = moves(c2).single { it.line == 13 }
        near(d(-9.6, 56.0), sh.nose.start, "M09 뒤 G40 처음")
        near(d(-9.6, 56.8), sh.nose.end, "M09 뒤 G40 끝")
    }

    // ---------------------------------------------------------------- 회사 꼴 예제 셋

    private fun example(o: String) = SimExamples.all.single { it.text.startsWith(o) }

    /** 보정 중 이송 블록의 노즈가 지령 경로에서 r: 직선은 양 끝이 지령선에서 r(공구 쪽), 원호는 같은 중심 · R ∓ r */
    private fun assertOffsetR(run: SimRun, lines: IntRange, rr: Double, arcTol: Double) {
        for (m in moves(run).filter { it.line in lines && it.kind == MoveKind.FEED && it.src == StepSrc.BLOCK }) {
            when (val cmd = m.cmd!!) {
                is Line -> {
                    val u = (cmd.end - cmd.start).let { it * (1 / it.length) }
                    for (p in listOf(m.nose.start, m.nose.end)) assertEquals(rr, abs(u cross (p - cmd.start)), 1e-9, "줄 ${m.line} 노즈 ${s(p)}")
                }
                is Arc -> {
                    val a = m.nose as Arc
                    near(cmd.center, a.center, "줄 ${m.line} 중심", 1e-9)
                    for (p in listOf(a.start, a.end)) assertEquals(rr, abs((p - cmd.center).length - cmd.radius), arcTol, "줄 ${m.line} 원호 노즈 ${s(p)}")
                }
            }
        }
    }

    /**
     * 3. 예제 ① O9020(외경 T01 0.4R 팁 3 · 형식 C) — 줄마다 블록 끝 노즈(손 계산):
     * 6 G0X58.Z20. → X58.8 Z20.4, 7 Z.1 → Z0.5, 8 G1X-2. → X-1.2(끝면 보정 없이), 9 G0Z5. → Z5.4,
     * 10 G42 → X-2. Z5.4(다음 +X 블록의 G42 법선 +Z: P − t → P + r·n₁), 11 X36. → X36.8 Z5.4(바깥 90° 늘인 교점), 12 Z3. → X36.8 Z3.,
     * 13 G1Z0. → X36.8 Z0.4(안쪽), 14 X40. → X40.331371 Z0.4, 15 X50.Z-5. → X50.8 Z-4.834315, 16 Z-9.5 → X50.8 Z-9.5(접함),
     * 17 G2 R.5 → X51. Z-9.6(노즈 원호 R0.1), 18 X60.F.3 → X60. Z-9.6, 19 G40 → X60.8 Z-9.6(+X 0.4 — 공기, 소재 Ø58 위),
     * 20 G41 → X60. Z-9.6(다음 −X 블록의 G41 법선 +Z), 21 X51. → X51. Z-9.6, 22 G3 R.5 → X50.8 Z-9.5, 23 Z-5. → X50.8 Z-4.834315,
     * 24 X40.Z0. → X40.331371 Z0.4, 25 X0. → X0. Z0.4(끝 법선 — 노즈 −Z 끝 Z0 이 X0 까지), 26 G40 → X0.8 Z0.4(끝면 따라 +X), 27 G0Z200. → X0.8 Z200.4.
     */
    @Test
    fun example1Hand() {
        val e = example("O9020")
        val run = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
        assertNull(run.stop)
        val want = mapOf(
            6 to d(20.4, 58.8), 7 to d(0.5, 58.8), 8 to d(0.5, -1.2), 9 to d(5.4, -1.2), 10 to d(5.4, -2.0), 11 to d(5.4, 36.8),
            12 to d(3.0, 36.8), 13 to d(0.4, 36.8), 14 to d(0.4, 40.331370850), 15 to d(-4.834314575, 50.8), 16 to d(-9.5, 50.8),
            17 to d(-9.6, 51.0), 18 to d(-9.6, 60.0), 19 to d(-9.6, 60.8), 20 to d(-9.6, 60.0), 21 to d(-9.6, 51.0), 22 to d(-9.5, 50.8),
            23 to d(-4.834314575, 50.8), 24 to d(0.4, 40.331370850), 25 to d(0.4, 0.0), 26 to d(0.4, 0.8), 27 to d(200.4, 0.8),
        )
        for ((line, p) in want) near(p, ghostNose(run, line), "① 줄 $line")
        assertEquals(StepSrc.SHIFT_START, moves(run).first { it.line == 10 }.src)
        assertEquals(StepSrc.SHIFT_CANCEL, moves(run).single { it.line == 19 }.src)
        assertEquals(StepSrc.SHIFT_START, moves(run).single { it.line == 20 }.src)
        assertEquals(StepSrc.SHIFT_CANCEL, moves(run).single { it.line == 26 }.src)
        // 첫 깎기(14 끝면) 노즈 처음 X36.8 Z0.4 → 접점 X36 Z0, 끝(25) 접점 Z0 · X0
        near(d(0.4, 36.8), moves(run).single { it.line == 14 }.nose.start, "① 첫 깎기 처음")
        assertOffsetR(run, 13..25, r, 1e-9)
        for (l in listOf(17, 22)) assertEquals(0.1, (moves(run).single { it.line == l }.nose as Arc).radius, 1e-9, "① 줄 $l R.5 − 0.4")
        // G0Z200. 은 Z 만
        assertTrue(moves(run).filter { it.line == 27 }.all { abs(it.nose.start.x - it.nose.end.x) < 1e-12 })
    }

    /**
     * 예제 ② O9021(내경 T02 0.4R 팁 2 · G41 · 형식 C · 기계 원점 X200 Z100) — 글 좌표(소수 셋째 자리 반올림)를 그대로 푼 손 계산:
     * R1 중심 Z-9.658349 X18.000000(지름), SR12.5 중심 Z-2.500830 X-0.002213 — 반올림으로 두 원호 이음이 0.0003 rad, 직선 → R1 이음이 0.00065 rad
     * 틀어져 있어 '접함'(1e-3 rad 안)으로 본다. 6 G41 → X199.468509 Z100.298964(다음 블록 G0X20.Z20. 의 G41 법선), 7 → X19.2 Z20.179626(바깥 48° 늘인 교점),
     * 8 Z1. → X19.2 Z1., 9 → X19.2 Z-9.659, 10 G3 R1. → X18.939 Z-10.031940(R1 끝 법선), 11 SR12.5 → X14.552688 Z-12.167717(빼는 줄과 안쪽 교점 —
     * 꺾임 5.91° 라 접점은 X15 Z-12.5 보다 0.021 앞), 12 빼는 줄 `G1X12.Z-13.4` → X11.588403 Z-13.057003(끝 법선), 13 G40 → X11.2 Z-13.0(E − t: 빼는 줄에서
     * 멀어짐 0.149), 14 G0Z150. → X11.2 Z150.4. (10-29 검증 뒤 빼는 줄 끝을 Z-13.5 → Z-13.4 로 고쳐 SR12.5 구면 연장 안 — 공기 — 에 두었다:
     * 11 · 12 · 13 줄 값은 그 글로 다시 푼 것)
     */
    @Test
    fun example2Hand() {
        val e = example("O9021")
        val run = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
        assertNull(run.stop)
        val want = mapOf(
            6 to d(100.298963727, 199.468508929), 7 to d(20.179626426, 19.2), 8 to d(1.0, 19.2), 9 to d(-9.659, 19.2),
            10 to d(-10.031939660, 18.939000169), 11 to d(-12.167717301, 14.552688490), 12 to d(-13.057002830, 11.588403396),
            13 to d(-13.0, 11.2), 14 to d(150.4, 11.2),
        )
        for ((line, p) in want) near(p, ghostNose(run, line), "② 줄 $line", 1e-6)
        near(d(100.4, 199.2), moves(run).first { it.line == 6 }.nose.start, "② G41 옮김 처음 = 원점 − t")
        near(d(1.0, 19.2), moves(run).single { it.line == 9 }.nose.start, "② 첫 깎기 처음")
        // 원호 노즈: R1 → 0.6, SR12.5 → 12.1. 이음 틀어짐(1.2e-4 mm)만큼 허용
        assertOffsetR(run, 9..12, r, 2e-4)
        assertTrue(moves(run).filter { it.line == 14 }.all { abs(it.nose.start.x - it.nose.end.x) < 1e-12 })
        // 빼는 줄 지령선은 SR12.5 구면을 넘지 않는다(돔이 중심까지 이어지는 도면이어도 공기 — 거리는 볼록이라 양 끝만 보면 된다)
        val sph = moves(run).single { it.line == 11 }.cmd as Arc
        val ret = moves(run).single { it.line == 12 }.cmd as Line
        for (p in listOf(ret.start, ret.end)) assertTrue((p - sph.center).length <= sph.radius + 1e-9, "② 빼는 줄 ${s(p)} 구면 밖 ${(p - sph.center).length - sph.radius}")
    }

    /**
     * 예제 ③ O9022(나사 T03 보정 없음 — 노즈 = 지령점): G92 사이클 시작점 X10 Z5 에서 패스 X5.76 · 5.56 · 5.4 · 5.26 · 5.14 · 5.03 · 4.93 · 4.84 · 4.76,
     * 다른 블록에서 X4.688 두 번(골 = 골든 thread 의 G76 X4.688), 모두 Z5 → Z-25 · 리드 1.27.
     */
    @Test
    fun example3Hand() {
        val e = example("O9022")
        val run = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
        assertNull(run.stop)
        val th = moves(run).filter { it.kind == MoveKind.THREAD }
        val xs = listOf(5.76, 5.56, 5.4, 5.26, 5.14, 5.03, 4.93, 4.84, 4.76, 4.688, 4.688)
        assertEquals(xs.size, th.size)
        for ((m, x) in th.zip(xs)) {
            near(d(5.0, x), m.tip.start, "③ 패스 X$x 처음")
            near(d(-25.0, x), m.tip.end, "③ 패스 X$x 끝")
            near(m.tip.start, m.nose.start, "③ 보정 없음", 1e-12)
            assertEquals(1.27, m.feed!!, 1e-12)
        }
        // 사이클 걸음은 X10 Z5 에서 시작해 X10 Z5 로 돌아온다
        for (l in moves(run).filter { it.src == StepSrc.CYCLE_RETURN }) near(d(5.0, 10.0), l.tip.end, "③ 돌아옴")
    }

    /** 세 예제를 자동 소재로 깎으면 닿음(급송 깎음 · 척 · G40 옮김 깎음)이 없다 — 구현 보고의 주장을 다시 본다 */
    @Test
    fun examplesNoContactOnAutoStock() {
        for (o in listOf("O9020", "O9021", "O9022")) {
            val e = example(o)
            val run = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
            val spec = assertNotNull(StockAuto.of(run), o)
            val contacts = StockSim(spec, run, e.tools).also { it.cutTo(run.totalSec) }.contacts
            println("$o 자동 소재 Ø${spec.dia} 구멍 Ø${spec.boreDia} 끝면 Z${spec.faceZ} — 닿음 ${contacts.map { it.kind to it.line }}")
            assertTrue(contacts.isEmpty(), "$o $contacts")
        }
    }
}
