package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 빼는 길 · 축 둘레 고침(2026-11-05 검증 문제)을 지은 도형의 손 계산과 시뮬레이터 `GSim` 으로 지킨다. 기대값은 앱 글이 아니라 도형에서 따로 구한 수
 * (식과 수를 주석에)이고, 거리는 이 파일에서 따로 잰다(앱의 `CompFrame` 을 부르지 않는다).
 * - 내경 막힌 끝의 첫 빼는 줄(벽을 따라 공구 쪽 c)은 축까지만 — 구멍 반지름 < c 이면 축 너머 맞은편 벽을 팠다
 * - 마지막 깎는 요소 · 빼는 줄끼리 바깥 90° 넘게 꺾지 않는다 — 역방향 3점호에서 비키는 줄 → 물러남 X 맞춤 줄이 127° 로 꺾여 시뮬레이터가 멈췄다
 * - 외경 노즈 중심이 축을 넘어 축 거울 도면을 파면 계산을 멈춘다(NOSE_UNREACHABLE — 두 탭 같은 노즈 길)
 * - 빼는 길을 규칙대로 못 짜면 EXIT_NOT_FOUND 이고, 헛경고가 아니다(시뮬레이터 노즈가 정말 도면에 닿는다)
 * - G42 진입 Z 줄이 노즈 중심을 축에 세우는 X(축 너머 r)까지는 ENTRY_PAST_AXIS 가 아니다
 * - 보정 중 두 축 진입 G0 은 급송 속도 비가 달라도(LRP=0 — 꺾이는 자리) 노즈가 도면에 닿지 않는다
 */
class VerifyExitHandTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99F.1")

    private fun sim(nc: List<String>, r: Double, tip: Int, form: CompStartup, rx: Double = 20_000.0, rz: Double = 24_000.0): SimRun =
        GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(rapidX = rx, rapidZ = rz, compStartup = form), mapOf(1 to ToolOffset(r, tip))))

    /** 글 줄 번호(0부터)의 시뮬레이터 줄 번호 */
    private fun line(i: Int) = pre.size + i + 1

    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    /** 원호 중심에서 본 p 의 방향이 원호 각 안인가 */
    private fun within(a: Arc, p: Pt): Boolean {
        val a0 = atan2(a.start.x - a.center.x, a.start.z - a.center.z)
        val ap = atan2(p.x - a.center.x, p.z - a.center.z)
        var d = if (a.cw) a0 - ap else ap - a0
        d = ((d % (2 * PI)) + 2 * PI) % (2 * PI)
        return d <= a.sweepDeg * PI / 180 + 1e-12
    }

    /** 점 ~ 요소 정확한 거리(직선은 수선 발, 원호는 각 안이면 |중심 거리 − R| 아니면 끝점) */
    private fun dist(s: Seg, p: Pt): Double = when (s) {
        is Line -> {
            val v = s.end - s.start
            val l2 = v dot v
            val k = if (l2 <= 0) 0.0 else (((p - s.start) dot v) / l2).coerceIn(0.0, 1.0)
            (p - (s.start + v * k)).length
        }
        is Arc -> {
            val c = (p - s.center).length
            if (c > 1e-12 && within(s, p)) abs(c - (s.start - s.center).length) else min((p - s.start).length, (p - s.end).length)
        }
    }

    /** 노즈 길의 점들(0.01 mm 마다) */
    private fun pts(s: Seg): List<Pt> = when (s) {
        is Line -> {
            val k = max(1, ((s.end - s.start).length / 0.01).toInt())
            (0..k).map { s.start + (s.end - s.start) * (it.toDouble() / k) }
        }
        is Arc -> {
            val c = s.center
            val rr = (s.start - c).length
            val a0 = atan2(s.start.x - c.x, s.start.z - c.z)
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * rr / 0.01).toInt())
            (0..n).map { c + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * rr }
        }
    }

    /** 줄 번호 [lines] 안 이동의 노즈 점 */
    private fun noses(run: SimRun, lines: IntRange): List<Pt> = run.steps.filterIsInstance<MoveStep>().filter { it.line in lines }.flatMap { pts(it.nose) }

    private fun text(nc: List<String>) = nc.joinToString(" / ")

    @Test
    fun innerShortEndExitStopsAtAxis() {
        // 내경 TYPE 2(아령 구멍): 입구 구멍 Ø3(반지름 a = 1.5) · L1 3, 주 R 2.5 의 공(이음점 사이 반 s = √(R² − a²) = 2 → 구 중심 Z−5, 공 끝 Z−7),
        // 모서리 R 0, 공 뒤 구멍 0.2(막힌 끝 Z−7.2 — 너머가 구멍 바닥), 0.4R · Z 여유 5(c = 5 — 구멍 반지름 1.5 보다 길다)
        val r = 0.4
        val res = RoundCalc.machine(RoundInput(type = 2, inner = true, mainR = 2.5, d1 = 3.0, l1 = 3.0, cornerR1 = 0.0, noseR = r, zClear = 5.0, legLen = 0.2))
        val sol = res.solution!!
        val nc = sol.nc
        // 공의 노즈 원: 중심 (−5, 0), 반지름 R − r = 2.1(공구가 구 안 — 오목). 공 뒤 구멍(0.2 < r)은 노즈가 벽 앞에서 닿을 길이 없어 뺀다 — 공 끝 (−7, 1.5) 에서
        // 벽을 따라 축 쪽(−X)으로 빼는 줄의 보정선 Z = −7 + r = −6.6 과 노즈 원의 교점 X = √(2.1² − 1.6²) = √1.85 = 1.360147. 그 각 atan2(1.360147, −1.6) = 139.63° 가
        // 공 노즈 길(36.87° ~ 143.13°) 위이고 끝까지 2.1·3.50° = 0.128 ≥ 0.05
        val hit = Pt(-6.6, sqrt(1.85))
        val a = atan2(hit.x, hit.z + 5) * 180 / PI
        val aEnd = atan2(1.5, -2.0) * 180 / PI
        assertTrue(a in atan2(1.5, 2.0) * 180 / PI..aEnd && 2.1 * (aEnd - a) * PI / 180 >= 0.05, "$a $aEnd")
        assertTrue(RoundWarning.SHORT_END_SKIPPED in res.warnings, "${res.warnings}\n${text(nc)}")
        assertTrue(RoundWarning.EXIT_NOT_FOUND !in res.warnings && RoundWarning.SHORT_END_PAST !in res.warnings, "${res.warnings}\n${text(nc)}")
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        assertTrue(nc[iExit - 1].endsWith("X3.Z-7.R2.5"), text(nc))
        // 첫 빼는 줄: 벽을 따라 c = 5 면 반지름 1.5 − 5 = −3.5(X−7.) — 노즈(Z−6.6, 반지름 −3.1 까지)가 축 너머 맞은편 공(거울 원 중심 (−5, 0) · R2.5 —
        // Z−6.6 에서 반지름 √(6.25 − 1.6²) = 1.921)을 판다. 축까지만 가서 X0.
        assertEquals(1.921, sqrt(6.25 - 1.6 * 1.6), 1e-3)
        assertEquals("G1X0.F.3", nc[iExit], text(nc))
        // G40 자리 규칙 반지름 (1 + √2)·0.4 + 0.02 = 0.985685: (−7, 0) 은 구멍 바닥 벽(Z−7.2)에서 0.2 뿐이라 벽에서 멀어지는 +Z 로 0.985685 − 0.2 → Z−6.214315.
        // 그 자리의 +Z 물러남 띠는 입구 구멍(반지름 1.5 > 0.985685) 안이라 X 맞춤이 없다
        val rc = (1 + sqrt(2.0)) * r + 0.02
        assertEquals("Z${Fmt.nc(-7.2 + rc)}", nc[iExit + 1], text(nc))
        assertEquals("Z-6.214", nc[iExit + 1])
        assertEquals("G40", nc[iExit + 2], text(nc))
        // 시뮬레이터: 형식 A·B / C × 팁 0~9 멈춤 없음, 빼는 줄부터 물러남까지 노즈가 도면 · 축 거울 도면에서 r 이상, 구멍 바닥 벽(Z−7.2)에서 r 이상(0.001 반올림까지)
        val mat = sol.drawing.segs + sol.drawing.segs.map(::mirror)
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            val run = sim(nc, r, tip, form)
            assertNull(run.stop, "형식 $form 팁 $tip: ${run.stop}\n${text(nc)}")
            for (p in noses(run, line(iExit)..line(nc.lastIndex))) {
                assertTrue(mat.minOf { dist(it, p) } >= r - 1e-3, "형식 $form 팁 $tip: 노즈 $p\n${text(nc)}")
                assertTrue(p.z >= -7.2 + r - 1e-3, "형식 $form 팁 $tip: 노즈 $p 가 벽 너머\n${text(nc)}")
            }
        }
    }

    @Test
    fun reverseArcExitDoesNotTurnAcuteOuter() {
        // 외경 3점호(역방향 — 끝 Z 가 시작보다 큼): 원 중심 (Z−5, 반경 8) · R4 위 −90° → 0° → 36.87°(반시계) — 시작 (Z−5, 4) = X8, 가운데 (Z−1, 8) = X16,
        // 끝 (−5 + 4·0.8, 8 + 4·0.6) = (Z−1.8, 10.4) = X20.8. 공구는 진행 왼쪽(G41) = 원 안(오목 홈), 0.4R, Z 여유 0.1 → c = max(0.1, 0.4) = 0.4
        val r = 0.4
        val res = RoundCalc.machine(Arc3Input(inner = false, noseR = r, x1 = 8.0, z1 = -5.0, x2 = 16.0, z2 = -1.0, x3 = 20.8, z3 = -1.8, zClear = 0.1))
        val sol = res.solution!!
        val nc = sol.nc
        val end = Pt(-1.8, 10.4)
        // 끝 접선(반시계) = 반지름 방향 (0.8, 0.6) 을 +90° 돌린 (−0.6, 0.8) — 위로 가며 −Z 로 37° 기운다. 공구 쪽(왼쪽) 법선 (−0.8, −0.6).
        // 전에는 끝 접선으로 c = 0.4 → G40 자리 규칙 (1 + √2)·0.4 + 0.02 = 0.985685 에 모자라 공구 쪽 법선으로 비키고, 그 점의 +Z 물러남 띠가 끝(반경 10.4) 밑을
        // 지나 +X 로 X 맞춤 — 비키는 줄 (−0.8, −0.6) → X 맞춤 (0, 1) 은 내적 −0.6(127°)이고 진행 왼쪽(공구 쪽) 반대로 도는 바깥 꺾임이라 제어기가 꼭짓점 토막을
        // 끼운다(시뮬레이터 COMP_ACUTE_OUTER). 이제 그런 안은 버리고 첫 빼는 줄을 끝 접선으로 0.5 씩 늘인다: 0.9 는 끝점에서 0.9 < 0.985685 라 또 비켜야 하고(같은 꺾임),
        // 1.4 면 G40 자리(끝점에서 1.4)와 물러남 띠(끝점보다 1.4·0.8 = 1.12 위 — 원호는 끝에서 끝난다)가 맞는다 → 끝 + 1.4·(−0.6, 0.8) = (−2.64, 11.52) = X23.04
        val u = Pt(-0.6, 0.8)
        val nL = Pt(-0.8, -0.6)
        val dot = nL dot Pt(0.0, 1.0)
        assertEquals(-0.6, dot, 1e-12)
        assertTrue((nL cross Pt(0.0, 1.0)) < 0) // G41 은 왼쪽으로 도는 꺾임(외적 > 0)이 안쪽 — 이것은 바깥
        val rc = (1 + sqrt(2.0)) * r + 0.02
        assertTrue(0.9 < rc && 1.4 >= rc && 1.4 * u.x >= rc)
        val q = end + u * 1.4
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        assertEquals("G1X${Fmt.nc(2 * q.x)}Z${Fmt.nc(q.z)}F.3", nc[iExit], text(nc))
        assertEquals("G1X23.04Z-2.64F.3", nc[iExit])
        assertEquals("G40", nc[iExit + 1], text(nc))
        assertTrue(RoundWarning.EXIT_NOT_FOUND !in res.warnings, "${res.warnings}")
        val mat = sol.drawing.segs + sol.drawing.segs.map(::mirror)
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            val run = sim(nc, r, tip, form)
            assertNull(run.stop, "형식 $form 팁 $tip: ${run.stop}\n${text(nc)}")
            for (p in noses(run, line(iExit)..line(nc.lastIndex))) assertTrue(mat.minOf { dist(it, p) } >= r - 1e-3, "형식 $form 팁 $tip: 노즈 $p\n${text(nc)}")
        }
    }

    @Test
    fun outerNoseCenterPastAxisStops() {
        // 외경 3점호(역방향 · 볼록): 원 중심 (Z−5, 반경 3.5) · R5 위 90° → 36.87° → −36.87°(시계) — 시작 (Z−5, 8.5) = X17, 가운데 (Z−1, 6.5) = X13,
        // 끝 (Z−1, 0.5) = X1(축에서 0.5). 1.0R: 노즈 중심 길은 같은 중심 반지름 6 — 끝에서 (−5 + 6·0.8, 3.5 − 6·0.6) = (Z−0.2, 반경 −0.1) 로 축을 넘는다.
        // 축 거울 도면(중심 (−5, −3.5) · R5)까지 그 노즈 중심 거리 √(4.8² + 3.4²) − 5 = √34.6 − 5 = 0.882 < r — 그 점 방향(35.3°)이 거울 호(−90° ~ 36.87°) 안이라
        // 부품 반대쪽을 1 − 0.882 = 0.118 판다(0.001 넘음). 같은 노즈 길이라 직접 보정 · 기계 보정 모두 멈춘다
        val input = Arc3Input(inner = false, noseR = 1.0, x1 = 17.0, z1 = -5.0, x2 = 13.0, z2 = -1.0, x3 = 1.0, z3 = -1.0, zClear = 1.0)
        assertEquals(0.118, 1 - (sqrt(34.6) - 5), 1e-3)
        assertTrue(atan2(3.4, 4.8) * 180 / PI in -90.0..36.87)
        for (res in listOf(RoundCalc.arc3(input), RoundCalc.machine(input))) {
            assertNull(res.solution, "${res.warnings}")
            assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), res.warnings)
        }
        // 노즈를 줄이면(0.4R — 노즈 중심 끝 반경 3.5 − 5.4·0.6 = 0.26 > 0) 축을 넘지 않아 푼다
        assertTrue(3.5 - 5.4 * 0.6 > 0)
        assertNotNull(RoundCalc.arc3(input.copy(noseR = 0.4)).solution)
        assertNotNull(RoundCalc.machine(input.copy(noseR = 0.4)).solution)
    }

    @Test
    fun narrowConeExitWarnsAndIsNotSilly() {
        // 내경 TYPE 3: X26 Z−1.5 에서 X0.6 Z−7.2 로 좁아지는 원뿔(끝 반지름 0.3), 끝 비움, 1.4R · 팁 0, Z 여유 0.5. G40 자리 규칙 반지름 (1 + √2)·1.4 + 0.02 = 3.400 —
        // 원뿔 끝 둘레는 그보다 좁고, 내경 빼는 줄은 축을 넘지 않는다. 빼는 길을 규칙대로 못 짜 EXIT_NOT_FOUND(전에는 경고 없이 G40 · 물러남이 원뿔 벽을 팠다)
        val r = 1.4
        val res = TaperCalc.calc(TaperInput(inner = true, type = 3, noseR = r, tip = 0, x1 = 26.0, z1 = -1.5, x2 = 0.6, z2 = -7.2, zClear = 0.5))
        val sol = res.solution!!
        val nc = sol.ncG41G42
        assertEquals(3.400, (1 + sqrt(2.0)) * r + 0.02, 1e-3)
        assertTrue(TaperWarning.EXIT_NOT_FOUND in res.warnings, "${res.warnings}\n${text(nc)}")
        // 헛경고가 아니다: 어떤 형식 · 팁에서 빼는 줄 · G40 · 물러남 노즈가 도면(또는 축 거울)에서 r 안으로 0.001 넘게 들어간다
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        val mat = sol.drawing.segs + sol.drawing.segs.map(::mirror)
        var deep = 0.0
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            val run = sim(nc, r, tip, form)
            if (run.stop != null) {
                deep = max(deep, r)
                continue
            }
            for (p in noses(run, line(iExit)..line(nc.lastIndex))) deep = max(deep, r - mat.minOf { dist(it, p) })
        }
        assertTrue(deep > 1e-3, "빼는 길 노즈가 도면에 닿지 않는데 경고\n${text(nc)}")
    }

    @Test
    fun g42EntryToAxisIsNotPastAxis() {
        // 내경 역방향 겹테이퍼(산 — 가운데 T2 반경 2.363 이 양 끝보다 낮다), 1.1R · Z 여유 0.5, T1 모서리 R2.051, 끝 Z−14.901. 진입 Z 줄을 축 쪽으로 찾아
        // 노즈 중심이 축에 서는 X 까지 간다 — 역방향 내경은 G42 라 Z 줄(−Z) 노즈가 진행 오른쪽 = +X 로 r 밀린다: 명령 X 가 −2r = X−2.2 보다 바깥이면 노즈 중심은 축 위.
        // 그 진입은 축 너머가 아니므로 ENTRY_PAST_AXIS('Z 여유를 줄이세요')를 띄우지 않는다(전에는 `G0X-1.874Z20.` — 노즈 중심 반경 0.163 — 에 그 경고가 떴다)
        val r = 1.1
        val res = TaperCalc.calcDouble(
            DoubleTaperInput(inner = true, reverse = true, noseR = r, x1 = 20.167, z1 = 0.042, x2 = 4.726, z2 = -3.337, x3 = 23.588, z3 = -7.756, zClear = 0.5, cornerR1 = 2.051, end = -14.901),
        )
        val sol = res.solution!!
        val nc = sol.ncG41G42
        assertEquals("G42", nc[0], text(nc))
        val x = Regex("G0X(-?[0-9.]+)Z").find(nc[1])!!.groupValues[1].toDouble()
        assertTrue(x >= -2 * r - 1e-9, text(nc))
        assertTrue(TaperWarning.ENTRY_PAST_AXIS !in res.warnings, "${res.warnings}\n${text(nc)}")
        // 시뮬레이터: 진입 G0 줄(Z20. 줄 뒤 ~ 첫 깎는 줄 앞)의 노즈 중심이 축을 넘지 않는다. 골 T2 → 경사는 도면에서 바깥으로 90° 넘게 꺾여(FANUC 은 꼭짓점 토막을
        // 끼워 돈다) 시뮬레이터가 그 형상 줄에서 멈춘다 — 시뮬레이터 한계(다른 검증 시험과 같은 가름)라 멈춤은 그 형상 줄만 받고, 그 앞 진입 이동을 본다
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            val run = sim(nc, r, tip, form)
            run.stop?.let { st ->
                assertTrue(st.code.toString() == "COMP_ACUTE_OUTER" && st.line > line(iCut) && st.line < line(iExit), "형식 $form 팁 $tip: $st\n${text(nc)}")
            }
            val entry = noses(run, line(2) until line(iCut))
            assertTrue(entry.isNotEmpty())
            for (p in entry) assertTrue(p.x >= -1e-3, "형식 $form 팁 $tip: 노즈 $p\n${text(nc)}")
        }
    }

    @Test
    fun twoAxisEntryG0ClearsAtAnyRapidRatio() {
        // 외경 3점호(앞으로 부푼 정방향) — 첫 깎는 줄이 비스듬해 공구 쪽에서 직각으로 들어가는 진입(P3 → 진입점)이 두 축 G0 이다. LRP=0 이면 X · Z 가 따로 가
        // 급송 속도 비에 따라 꺾이는 자리가 다르다(전에는 X5000 · Z40000 에서 꺾인 노즈가 축 거울 도면을 0.192 팠는데 경고가 없었다). 어느 속도 비에서도 진입 노즈가
        // 도면 · 축 거울 도면에서 r 이상이거나, 아니면 앱이 ENTRY_NOT_FOUND 로 알려야 한다. 알렸다면 헛경고가 아니고(치우친 속도 비에서 정말 닿음), 보통 속도 비
        // (20000/24000)에서는 닿지 않는다 — 곧장 내려가는 길(어느 속도 비에서도 판다)보다 꺾인 길만 닿는 직각 진입을 낸다
        val r = 1.4
        val res = RoundCalc.machine(Arc3Input(inner = false, noseR = r, tip = 4, x1 = 2.795, z1 = -5.949, x2 = 36.063, z2 = -4.478, x3 = 53.376, z3 = -18.758, zClear = 1.01))
        val sol = res.solution!!
        val nc = sol.nc
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val mat = sol.drawing.segs + sol.drawing.segs.map(::mirror)
        fun worst(rx: Double, rz: Double): Double {
            var w = 0.0
            for (form in listOf(CompStartup.AB, CompStartup.C)) {
                val run = sim(nc, r, sol.tip, form, rx, rz)
                assertNull(run.stop, "급송 X $rx · Z $rz 형식 $form: ${run.stop}\n${text(nc)}")
                for (p in noses(run, line(2) until line(iCut))) w = max(w, r - mat.minOf { dist(it, p) })
            }
            return w
        }
        val skewed = listOf(5_000.0 to 40_000.0, 40_000.0 to 5_000.0, 30_000.0 to 8_000.0, 6_000.0 to 30_000.0).maxOf { (rx, rz) -> worst(rx, rz) }
        assertTrue(worst(20_000.0, 24_000.0) <= 1e-3, "보통 속도 비에서 진입이 닿는다\n${text(nc)}")
        if (RoundWarning.ENTRY_NOT_FOUND in res.warnings) {
            assertTrue(skewed > 1e-3, "ENTRY_NOT_FOUND 인데 어느 속도 비에서도 닿지 않는다\n${text(nc)}")
            // 두 축 진입 G0(X · Z 가 함께 있는 G0 줄)이 있다 — 그 꺾인 길 때문에 알린다
            assertTrue((2 until iCut).any { nc[it].contains("X") && nc[it].contains("Z") }, text(nc))
        } else {
            assertTrue(skewed <= 1e-3, "치우친 속도 비에서 진입이 ${Fmt.nc(skewed)} 닿는데 경고가 없다\n${text(nc)}")
        }
    }
}
