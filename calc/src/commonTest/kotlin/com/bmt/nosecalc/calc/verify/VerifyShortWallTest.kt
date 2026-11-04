package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
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
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 짧은 막힌 끝 · 시작의 이웃이 모서리 R 일 때와 진입 길을 못 찾을 때(2026-11-04 고침)를 손 계산(지은 도형)과 시뮬레이터 `GSim` 으로 지킨다.
 * 기대값은 앱 글이 아니라 도형에서 따로 구한 원 · 교점(식과 수를 주석에)이다.
 * - 라운드 외경 돔 + 모서리 R + 짧은 끝 직선: G41/G42 는 끝 직선을 빼고 모서리 R 끝에서 공구 쪽 직각으로 뺀다 — 꺾임 노즈 = 빼는 줄 보정선과 노즈 원의 교점,
 *   노즈는 끝 좌표(벽)를 넘지 않는다
 * - 테이퍼 뒤집기 짧은 막힌 시작 + 모서리 R: 시작 요소를 빼고 R 처음에 공구 쪽 직각으로 들어간다 — 꺾임 노즈 = 진입 줄 보정선과 노즈 원의 교점
 * - 뺄 수 없는 모양(노즈보다 좁은 틈 너머 가파른 경사): 그 경사의 노즈 길이 모두 시작 벽 너머라 어느 길도 넘는다 — SHORT_END_PAST 경고
 * - 앞으로 부푼 역방향 3점호: 시작 접선을 거꾸로 늘인 진입점이 부품 속이라 진입 길이 없다 — ENTRY_NOT_FOUND(전에는 경고 없이 부품 속으로 들어갔다)
 * - 진입 길을 못 찾는 입력을 무작위로 찾아 세고, 경고(ENTRY_NOT_FOUND)가 뜬 글은 정말 진입 노즈가 도면에 닿거나 부품 속을 지나는지(헛경고가 아닌지),
 *   경고가 없는 글은 진입 노즈가 도면에 닿거나 부품 속을 지나지 않는지(경고 없이 파는 글이 없는지 — 도면 알람 꼴은 빼고, 2026-11-05 더함) 본다
 */
class VerifyShortWallTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99F.1")

    private fun sim(nc: List<String>, r: Double, tip: Int, form: CompStartup): SimRun =
        GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, tip))))

    /** NC 줄 번호(0부터, 글 안)의 시뮬레이터 줄 번호 */
    private fun line(i: Int) = pre.size + i + 1

    /** 노즈 길의 점들(직선은 끝점, 원호는 0.01 mm 마다) */
    private fun pts(s: Seg): List<Pt> = when (s) {
        is Line -> listOf(s.start, s.end)
        is Arc -> {
            val c = s.center
            val rr = (s.start - c).length
            val a0 = atan2(s.start.x - c.x, s.start.z - c.z)
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * rr / 0.01).toInt())
            (0..n).map { c + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * rr }
        }
    }

    /** 첫 Z20. 점 뒤(공구 교환 자리 뺀) 모든 이동의 노즈 점 */
    private fun noses(run: SimRun): List<Pt> = run.steps.filterIsInstance<MoveStep>().filter { it.line > line(1) }.flatMap { pts(it.nose) }

    @Test
    fun roundShortBlockedEndLeavesFromCornerR() {
        // 외경 TYPE 3(돔): 주 R10 · L4 → 반지름 h = √(L(2R − L)) = √(4·16) = 8(Ø16), 모서리 R1, 끝 직선 1(이론 꼭짓점 Z−4 → 끝 Z−5 — 막힌 끝, 척 쪽), 0.8R, Z 여유 1
        val r = 0.8
        val res = RoundCalc.machine(RoundInput(type = 3, mainR = 10.0, l1 = 4.0, cornerR1 = 1.0, noseR = r, zClear = 1.0, legLen = 1.0))
        val nc = res.solution!!.nc
        // 모서리 R 중심: 원통 X8 에서 1 아래(X7), 돔 중심 (−10, 0) 에서 9 → Z = −10 + √(81 − 49) = −10 + √32 = −4.343146
        val zc = -10 + sqrt(32.0)
        assertEquals(-4.343146, zc, 1e-6)
        // 남는 끝 직선 Z−4.343146 ~ −5 = 0.656854 < r — 노즈가 그 위에서 벽(Z−5 의 법선, 노즈 중심 Z−4.2)까지 가려면 R 끝 법선 자리(Z−4.343)에서 +Z 로 되돌아간다(간섭 알람 꼴)
        assertTrue(-5.0 + r - zc > 0)
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        // 끝 직선을 빼고 R 끝(X16. Z−4.343 — 돔 G3 에 이어 모달)에서 공구 쪽(+X)으로 뺀다
        assertTrue(nc[iExit - 2].startsWith("G3"), nc.joinToString(" / "))
        assertEquals("X16.Z-4.343R1.", nc[iExit - 1], nc.joinToString(" / "))
        assertFalse(nc.any { it.contains("Z-5.") }, nc.joinToString(" / "))
        assertTrue(RoundWarning.SHORT_END_PAST !in res.warnings && RoundWarning.ENTRY_NOT_FOUND !in res.warnings, "${res.warnings}")
        // 뺀 끝 직선(Z−4.343 ~ −5)은 깎이지 않는다 — 화면에 알린다(2026-11-05)
        assertTrue(RoundWarning.SHORT_END_SKIPPED in res.warnings, "${res.warnings}")
        // 꺾임 노즈: 빼는 줄 보정선 Z = zc + r = −3.543146 과 노즈 원(중심 (zc, 7), 반지름 1 + r = 1.8)의 교점 X = 7 + √(1.8² − 0.8²) = 7 + √2.6 = 8.612452
        val want = Pt(zc + r, 7 + sqrt(2.6))
        // 그 교점이 R 위: R 처음 각 atan2(7/9, 5.656854/9) = 51.06°, 교점 각 atan2(1.612452, 0.8) = 63.61° (끝 90°) — 노즈가 R 위로 1.8·12.55° = 0.394 간다(≥ 0.05)
        val aStart = atan2(7.0 / 9, sqrt(32.0) / 9) * 180 / PI
        val aHit = atan2(sqrt(2.6), r) * 180 / PI
        assertTrue(aHit in aStart..90.0 && 1.8 * (aHit - aStart) * PI / 180 >= 0.05, "$aStart $aHit")
        // 첫 빼는 줄(+X)은 c = 1 로 X18. 이면 그 뒤 G40 자리로 비키는 줄(+Z — R 꼭대기에서 1 은 G40 자리 규칙 (1 + √2)·0.8 + 0.02 = 1.951 보다 가깝다)과의
        // 안쪽 꺾임 노즈가 X(9 − 0.8) = 8.2 로 교점(8.612452)보다 낮아 거꾸로 간다 → 노즈 이동 0.05 가 되게 늘인다: X 반경 = 8.612452 + 0.05 + 0.8 = 9.462452 → X18.925
        assertEquals("G1X${Fmt.nc(2 * (want.x + 0.05 + r))}F.3", nc[iExit], nc.joinToString(" / "))
        assertEquals("G1X18.925F.3", nc[iExit])
        assertTrue(nc[iExit + 1].startsWith("Z"), nc.joinToString(" / "))
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            val run = sim(nc, r, tip, form)
            assertNull(run.stop, "형식 $form 팁 $tip: ${run.stop}\n${nc.joinToString(" / ")}")
            val arc = run.steps.filterIsInstance<MoveStep>().last { it.line == line(iExit - 1) }.nose
            assertEquals(want.z, arc.end.z, 2e-3, "형식 $form 팁 $tip R 끝 노즈")
            assertEquals(want.x, arc.end.x, 2e-3, "형식 $form 팁 $tip R 끝 노즈")
            // 어느 이동에서도 노즈 중심이 벽 Z−5 에서 r 안(Z < −4.2)으로 가지 않는다
            val worst = noses(run).minOf { it.z }
            assertTrue(worst >= -5.0 + r - 1e-3, "형식 $form 팁 $tip: 노즈 Z $worst")
        }
    }

    @Test
    fun taperShortBlockedStartEntersOnCornerR() {
        // 외경 TYPE 3 뒤집기(큰 외경 쪽 — 척 쪽에서 +Z 로): 큰 외경 X40 의 T1 Z−10, 45° 테이퍼로 T2 X30 Z−5, T1 모서리 R2, 시작 Z−11.2(막힌 시작 — 너머가 척 쪽),
        // 0.8R · 역바이트 팁 4, Z 여유 1
        val r = 0.8
        val res = TaperCalc.calc(TaperInput(inner = false, type = 3, noseR = r, tip = 4, x1 = 40.0, z1 = -10.0, x2 = 30.0, z2 = -5.0, zClear = 1.0, cornerR1 = 2.0, start = -11.2, flip = true))
        val nc = res.solution!!.ncG41G42
        // 모서리 R(볼록, 45° 꺾임): 접점까지 2·tan 22.5° = 0.828427 → R 처음 Z−10.828427(X40), 중심 (−10.828427, 18), 끝 = 중심 + 2·(√½, √½) = (−9.414214, 19.414214)
        val zs = -10 - 2 * tan(PI / 8)
        // 시작 요소 Z−11.2 ~ −10.828 = 0.372 < r: 노즈가 시작 벽(노즈 중심 Z ≥ −11.2 + 0.8 = −10.4)에서 R 처음 법선(Z−10.828)으로 가면 −Z 로 되돌아간다
        assertTrue(zs < -11.2 + r)
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        // 시작 요소를 빼고 R 처음(X40. Z−10.828)에 공구 쪽(+X)에서 직각으로 — Z20. 에서 Z 로 곧장 내려와 G1 X40.
        assertEquals("Z-10.828", nc[iCut - 1], nc.joinToString(" / "))
        assertEquals("G1X40.F.1", nc[iCut], nc.joinToString(" / "))
        assertEquals("G2X38.828Z-9.414R2.", nc[iCut + 1], nc.joinToString(" / "))
        assertFalse(nc.any { it.contains("Z-11.2") }, nc.joinToString(" / "))
        assertTrue(TaperWarning.SHORT_END_PAST !in res.warnings && TaperWarning.ENTRY_NOT_FOUND !in res.warnings, "${res.warnings}")
        // 뺀 시작 요소(Z−11.2 ~ −10.828)는 깎이지 않는다 — 화면에 알린다(2026-11-05)
        assertTrue(TaperWarning.SHORT_END_SKIPPED in res.warnings, "${res.warnings}")
        // 꺾임 노즈: 진입 줄(−X) 보정선 Z = zs + r = −10.028427 과 노즈 원(중심 (zs, 18), 반지름 2 + r = 2.8)의 교점 X = 18 + √(2.8² − 0.8²) = 18 + √7.2 = 20.683282
        val want = Pt(zs + r, 18 + sqrt(7.2))
        // 교점 각 atan2(2.683282, 0.8) = 73.40° — R 은 90° 에서 45° 로(시계) 돌아 노즈가 R 위로 2.8·28.40° = 1.388 간다
        val aHit = atan2(sqrt(7.2), r) * 180 / PI
        assertTrue(aHit in 45.0..90.0 && 2.8 * (aHit - 45) * PI / 180 >= 0.05)
        // 진입점: c = 1 이면 X42. 인데 Z 줄(−Z) → 첫 깎는 줄(−X) 안쪽 꺾임 노즈가 X(21 − 0.8) = 20.2 로 교점(20.683282)보다 낮아 거꾸로 간다 →
        // 첫 깎는 줄 노즈 이동 0.05 가 되게 물린다: X 반경 = 20.683282 + 0.05 + 0.8 = 21.533282 → X43.067. 그 Z 줄의 노즈(X20.733282 에서 Z−10.028427 까지)는
        // R 중심에서 √(0.8² + 2.733282²) = 2.848 — R 보정 원 2.8 밖이라 R 을 파지 않는다
        assertEquals("G0X${Fmt.nc(2 * (want.x + 0.05 + r))}Z20.", nc[iCut - 2], nc.joinToString(" / "))
        assertEquals("G0X43.067Z20.", nc[iCut - 2])
        assertTrue(sqrt(0.8 * 0.8 + (want.x + 0.05 - 18) * (want.x + 0.05 - 18)) > 2.8)
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            val run = sim(nc, r, tip, form)
            assertNull(run.stop, "형식 $form 팁 $tip: ${run.stop}\n${nc.joinToString(" / ")}")
            val g1 = run.steps.filterIsInstance<MoveStep>().last { it.line == line(iCut) }.nose
            assertEquals(want.z, g1.end.z, 2e-3, "형식 $form 팁 $tip 첫 깎는 줄 끝 노즈")
            assertEquals(want.x, g1.end.x, 2e-3, "형식 $form 팁 $tip 첫 깎는 줄 끝 노즈")
            // 노즈 중심이 시작 벽 Z−11.2 에서 r 안(Z < −10.4)으로 가지 않는다
            val worst = noses(run).minOf { it.z }
            assertTrue(worst >= -11.2 + r - 1e-3, "형식 $form 팁 $tip: 노즈 Z $worst\n${nc.joinToString(" / ")}")
        }
    }

    @Test
    fun narrowSlotBehindShortStartWarns() {
        // 외경 TYPE 5 뒤집기: T1 X40 Z−10 → 가파른 경사(ΔZ 0.2 · Δ반경 4) → T2 X48 Z−9.8, 시작 Z−11.2(막힌 시작, 요소 1.2), 0.8R · 팁 4, Z 여유 1
        val r = 0.8
        val res = TaperCalc.calc(TaperInput(inner = false, type = 5, noseR = r, tip = 4, x1 = 40.0, z1 = -10.0, x2 = 48.0, z2 = -9.8, zClear = 1.0, start = -11.2, flip = true))
        val s = res.solution!!
        assertTrue(TaperWarning.SHORT_END_PAST in res.warnings, "${res.warnings}\n${s.ncG41G42.joinToString(" / ")}")
        // 손 계산: 경사 u = (0.2, 4)/4.004997, G41 노즈는 그 왼쪽(−Z 쪽) r. T1 은 오목 꺾임 — 노즈 = 시작 요소 보정선 X20.8 과 경사 보정선의 교점,
        // Z = −10 − r·tan(α/2), tan(α/2) = (1 − cos α)/sin α = (1 − 0.049938)/0.998752 = 0.951249 → Z−10.760999.
        // T2 는 볼록(바깥 90° 이하) — 끝 요소 보정선 X24.8 과의 늘인 교점 Z = −10 − 0.998752·0.8 + 0.049938·t, t = (24.8 − 20 − 0.049938·0.8)/0.998752 → Z−10.560999.
        val len = sqrt(0.2 * 0.2 + 4.0 * 4.0)
        val u = Pt(0.2 / len, 4.0 / len)
        val n = Pt(-u.x, u.z)
        val zLow = -10 - r * (1 - u.z) / u.x
        val t = (24.8 - (20 + n.x * r)) / u.x
        val zHigh = -10 + n.z * r + u.z * t
        assertEquals(-10.760999, zLow, 1e-5)
        assertEquals(-10.560999, zHigh, 1e-5)
        // 시작 벽을 넘지 않으려면 노즈 중심 Z ≥ −11.2 + 0.8 = −10.4 — 경사를 깎는 노즈(Z−10.761 ~ −10.561)는 모두 그 너머(0.161 ~ 0.361):
        // 노즈(Ø1.6)가 시작 벽과 경사 사이 틈(Z−11.2 ~ −10, 1.2)에 들어가 경사를 깎을 길이 없다. 시작 요소를 빼고 들어가려 해도 진입 줄 보정선 Z = −10 + r = −9.2 가
        // 경사 노즈 길과 만나지 않는다 — 그래서 곧게 들어가고 경고한다(조용히 넘지 않는다)
        assertTrue(zHigh < -11.2 + r && zHigh < -10 + r)
        val nc = s.ncG41G42
        val run = sim(nc, r, 4, CompStartup.C)
        assertNull(run.stop, "${run.stop}\n${nc.joinToString(" / ")}")
        // 경고가 헛것이 아니다: 노즈가 정말 시작 벽 한계를 넘는다
        assertTrue(noses(run).minOf { it.z } < -11.2 + r - 0.1, nc.joinToString(" / "))
    }

    // ================================================================ 진입 길 못 찾음(ENTRY_NOT_FOUND)

    private fun r3(v: Double) = Fmt.nc(v).toDouble()

    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    /** 점 ~ 요소 거리(원호는 0.01 mm 표본) */
    private fun dist(s: Seg, p: Pt): Double = when (s) {
        is Line -> {
            val v = s.end - s.start
            val l2 = v dot v
            val k = if (l2 <= 0) 0.0 else (((p - s.start) dot v) / l2).coerceIn(0.0, 1.0)
            (p - (s.start + v * k)).length
        }
        is Arc -> pts(s).minOf { (it - p).length }
    }

    /** 노즈 길 ~ 도면 가장 짧은 거리(노즈 길 0.01 mm 표본) */
    private fun gap(nose: Seg, mat: List<Seg>): Double {
        val ps = when (nose) {
            is Line -> {
                val k = max(1, ((nose.end - nose.start).length / 0.01).toInt())
                (0..k).map { nose.start + (nose.end - nose.start) * (it.toDouble() / k) }
            }
            is Arc -> pts(nose)
        }
        return ps.minOf { p -> mat.minOf { dist(it, p) } }
    }

    /** 닫은 도면(외경은 두 끝에서 축으로, 내경은 바깥으로 곧게 닫음) 속인가 — 세로 · 가로 반직선 교차 수(축 너머 점은 거울로) */
    private fun inside(drawing: List<Seg>, inner: Boolean, p: Pt): Boolean {
        val poly = drawing.flatMap { s -> if (s is Line) listOf(s.start) else pts(s).dropLast(1) } + drawing.last().end
        val close = if (inner) 1e4 else 0.0
        val ring = poly + Pt(drawing.last().end.z, close) + Pt(drawing.first().start.z, close)
        val q = Pt(p.z, abs(p.x))
        var c = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.x > q.x) != (b.x > q.x) && q.z < a.z + (q.x - a.x) * (b.z - a.z) / (b.x - a.x)) c = !c
            j = i
        }
        return c
    }

    /**
     * 경고(ENTRY_NOT_FOUND)가 뜬 글의 진입이 정말 도면에 닿거나 부품 속을 지나는지(형식 C · 팁 [tip] 으로 돌린 노즈) — 그 까닭, 아니면 null(헛경고).
     * ① 첫 깎는 점의 노즈 자체가 도면을 판다(어떤 길로 와도 판다 — 첫 깎는 요소가 오목 R < r 모서리 등) ② 진입 G0 · 첫 깎는 줄(끝 0.01 mm 빼고)의 노즈가
     * 도면(축 거울 포함)에서 r 안으로 0.001 넘게 ③ 진입 노즈 중심이 닫은 도면 속(그린 선에 안 닿고 부품 속을 지남)
     */
    private fun entryReason(nc: List<String>, drawing: List<Seg>, inner: Boolean, r: Double, tip: Int): String? {
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val run = sim(nc, r, tip, CompStartup.C)
        val mat = drawing + drawing.map(::mirror)
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > line(1) && it.line <= line(iCut) }
        val end = moves.lastOrNull()?.nose?.end ?: return "진입 이동 없음(${run.stop})"
        val atEnd = r - mat.minOf { dist(it, end) }
        if (atEnd > 1e-3) return "① 첫 깎는 점 노즈가 도면을 ${Fmt.nc(atEnd)} 판다"
        val noses = moves.map { m ->
            val n = m.nose
            if (m.line == line(iCut) && n is Line && (n.end - n.start).length > 0.02) Line(n.start, n.end - (n.end - n.start) * (0.01 / (n.end - n.start).length)) else n
        }
        val deep = noses.maxOf { r - gap(it, mat) }
        if (deep > 1e-3) return "② 진입 노즈가 도면을 ${Fmt.nc(deep)} 판다"
        // 노즈 길을 0.1 mm 마다(직선 끝점만 보면 부품을 가로지르는 Z 줄을 놓친다)
        fun dense(n: Seg): List<Pt> = if (n is Line) (0..max(1, ((n.end - n.start).length / 0.1).toInt())).let { k -> (0..k.last).map { n.start + (n.end - n.start) * (it.toDouble() / k.last) } } else pts(n)
        if (noses.any { n -> dense(n).any { inside(drawing, inner, it) } }) return "③ 진입 노즈 중심이 부품 속을 지난다"
        return null
    }

    @Test
    fun bulgingReverseArcEntryWarns() {
        // 외경 3점호(역방향 · 앞으로 부풂): 원 중심 (Z−5, 반경 5) · R4 위 240° → 90° → 330°(시계) — 시작 (Z−7, 반경 1.536 = 5 − 4·sin 60°), 꼭대기 (Z−5, 9),
        // 끝 (Z−5 + 4·cos 30° = −1.536, 반경 3). 시작에서 왼쪽(−Z)으로 부풀었다가 넘어와 끝은 시작보다 +Z. 0.8R, Z 여유 1
        val r = 0.8
        val res = RoundCalc.machine(Arc3Input(inner = false, noseR = r, x1 = 3.072, z1 = -7.0, x2 = 18.0, z2 = -5.0, x3 = 6.0, z3 = -1.536, zClear = 1.0))
        val sol = res.solution!!
        val nc = sol.nc
        // 세 점의 원(외접원 — 따로 셈)
        val p1 = Pt(-7.0, 1.536)
        val p2 = Pt(-5.0, 9.0)
        val p3 = Pt(-1.536, 3.0)
        val d = 2 * (p1.z * (p2.x - p3.x) + p2.z * (p3.x - p1.x) + p3.z * (p1.x - p2.x))
        val q1 = p1.z * p1.z + p1.x * p1.x
        val q2 = p2.z * p2.z + p2.x * p2.x
        val q3 = p3.z * p3.z + p3.x * p3.x
        val c = Pt((q1 * (p2.x - p3.x) + q2 * (p3.x - p1.x) + q3 * (p1.x - p2.x)) / d, (q1 * (p3.z - p2.z) + q2 * (p1.z - p3.z) + q3 * (p2.z - p1.z)) / d)
        val rr = (p1 - c).length
        assertEquals(-5.0, c.z, 1e-3)
        assertEquals(5.0, c.x, 1e-3)
        assertEquals(4.0, rr, 1e-3)
        // 진입점 = 시작에서 시작 접선을 거꾸로 c = max(1, 0.8) = 1(calc-design 라운드 '기계 보정 프로그램'). 시계 접선 = 반지름 방향 (cos θ, sin θ) 을 −90° 돌린
        // (sin θ, −cos θ) — 240° 에서 (−0.866, 0.5): 왼쪽 위로 출발하므로 진입점은 오른쪽 아래 (Z−6.134, 반경 1.036)
        val radial = (p1 - c) * (1 / rr)
        val t0 = Pt(radial.x, -radial.z)
        val e0 = p1 - t0
        assertEquals(-6.134, e0.z, 2e-3)
        assertEquals(1.036, e0.x, 2e-3)
        // 그 진입점이 부품 속: 외경 3점호의 소재 = 호와 두 끝에서 축으로 내린 선 사이(calc-design · FrameFill). Z−6.134 는 두 끝 Z(−7 · −1.536) 사이이고,
        // 그 세로선이 호와 만나는 곳은 한 번 — 위쪽 X = 5 + √(16 − (−6.134 + 5)²) = 8.836 — 그 아래 축까지가 부품이라 반경 1.036 은 속이다.
        // 그래서 어떤 길로 와도 진입점에서 첫 깎는 줄 노즈(진입점의 공구 쪽 r)가 부품 속이다 — 진입 길을 못 찾는다
        val upper = c.x + sqrt(rr * rr - (e0.z - c.z) * (e0.z - c.z))
        assertTrue(e0.z > p1.z && e0.z < p3.z && e0.x > 0 && e0.x < upper, "$e0 / 위 $upper")
        assertEquals(8.836, upper, 2e-3)
        assertTrue(RoundWarning.ENTRY_NOT_FOUND in res.warnings, "${res.warnings}\n${nc.joinToString(" / ")}")
        // 글은 전과 같은 곧은 Z 줄이고(경고만 더함) — 첫 깎는 줄은 진입점에서 시작점으로
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        assertEquals("G1X3.072Z-7.F.1", nc[iCut], nc.joinToString(" / "))
        assertEquals("Z${Fmt.nc(e0.z)}", nc[iCut - 1], nc.joinToString(" / "))
        // 경고가 헛것이 아니다: 시뮬레이터 노즈가 부품 속을 지난다
        assertTrue(entryReason(nc, sol.drawing.segs, false, r, sol.tip) != null, nc.joinToString(" / "))
    }

    @Test
    fun entryNotFoundIsCountedAndHonest() {
        val rnd = Random(20261104)
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        fun nose() = listOf(0.2, 0.4, 0.8, 1.2, 1.6)[rnd.nextInt(5)]
        var cases = 0
        val warned = mutableListOf<String>()
        val silly = mutableListOf<String>()
        val silent = mutableListOf<String>()
        val reasons = linkedMapOf<String, Int>()
        // [alarm] = 도면 알람 꼴(오목 R ≤ r · 경사보다 큰 노즈 — 첫 깎는 점 노즈부터 도면 탓으로 판다)
        fun see(tag: String, nc: List<String>?, drawing: List<Seg>, inner: Boolean, r: Double, tip: Int, warn: Boolean, alarm: Boolean) {
            if (nc == null) return
            cases++
            if (!warn) {
                // 경고 없는 글의 진입은 도면에 닿지도 부품 속을 지나지도 않는다
                if (!alarm) entryReason(nc, drawing, inner, r, tip)?.let { silent += "$tag\n  경고 없이 $it\n  ${nc.joinToString(" / ")}" }
                return
            }
            val why = entryReason(nc, drawing, inner, r, tip)
            val k = why?.take(1) ?: "헛경고"
            reasons[k] = (reasons[k] ?: 0) + 1
            val text = "$tag\n  ${why ?: "헛경고"}\n  ${nc.joinToString(" / ")}"
            warned += text
            if (why == null) silly += text
        }
        repeat(1500) {
            val inner = rnd.nextBoolean()
            val r = nose()
            val zc = listOf(0.0, 0.3, 1.0, 2.0)[rnd.nextInt(4)]
            val tip: Int? = if (rnd.nextInt(3) == 0) rnd.nextInt(10) else null
            fun cr() = if (rnd.nextInt(3) != 0) 0.0 else r3(listOf(0.2, r, 1.0, 2.5)[rnd.nextInt(4)])
            when (rnd.nextInt(4)) {
                0, 1 -> {
                    val type = 1 + rnd.nextInt(5)
                    val up = if (inner == (type == 5)) 1.0 else -1.0
                    val x1 = r3(u(if (inner) 6.0 else 4.0, 100.0))
                    val z1 = r3(u(-6.0, 4.0))
                    val dz = r3(u(0.2, 10.0))
                    val x2 = r3(x1 + up * 2 * dz * tan(u(3.0, 87.0) * PI / 180))
                    if (x2 < 0.5 || x2 > 300) return@repeat
                    val z2 = r3(z1 - dz)
                    val face0 = type <= 2
                    val face1 = type == 2 || type == 4
                    val leg = { r3(if (rnd.nextInt(3) == 0) u(0.05, 2.5 * r) else u(1.0, 10.0)) }
                    val start = if (rnd.nextBoolean()) null else if (face0) max(0.0, r3(x1 - up * 2 * leg())) else r3(z1 + leg())
                    val end = if (rnd.nextBoolean()) null else if (face1) max(0.0, r3(x2 + up * 2 * leg())) else r3(z2 - leg())
                    var inp = TaperInput(inner, type, rnd.nextInt(4) == 0, r, tip, x1, z1, x2, z2, null, zc, cr(), cr(), start = start, end = end)
                    if (rnd.nextBoolean()) inp = inp.copy(x1 = inp.x2, z1 = inp.z2, x2 = inp.x1, z2 = inp.z1, cornerR1 = inp.cornerR2, cornerR2 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
                    val res: TaperResult = TaperCalc.calc(inp)
                    res.solution?.let { see("$inp → ${res.warnings}", it.ncG41G42, it.drawing.segs, inner, r, it.tip, TaperWarning.ENTRY_NOT_FOUND in res.warnings, alarm(res)) }
                }
                2 -> {
                    val x1 = r3(u(6.0, 90.0))
                    val z1 = r3(u(-5.0, 5.0))
                    val dz1 = r3(u(0.3, 8.0))
                    val dz2 = r3(u(0.3, 8.0))
                    val x2 = r3(x1 + (if (rnd.nextBoolean()) 1 else -1) * 2 * dz1 * tan(u(5.0, 80.0) * PI / 180))
                    val x3 = r3(x2 + (if (rnd.nextBoolean()) 1 else -1) * 2 * dz2 * tan(u(5.0, 80.0) * PI / 180))
                    if (x2 < 1 || x3 < 1 || x2 > 300 || x3 > 300) return@repeat
                    val inp = DoubleTaperInput(
                        inner, rnd.nextInt(4) == 0, r, tip, x1, z1, x2, r3(z1 - dz1), x3, r3(z1 - dz1 - dz2), zc, cr(), cr(), cr(),
                        start = if (rnd.nextBoolean()) r3(z1 + u(0.05, 6.0)) else null, end = if (rnd.nextBoolean()) r3(z1 - dz1 - dz2 - u(0.05, 6.0)) else null,
                        flip = rnd.nextBoolean(),
                    )
                    val res = TaperCalc.calcDouble(inp)
                    res.solution?.let { see("$inp → ${res.warnings}", it.ncG41G42, it.drawing.segs, inner, r, it.tip, TaperWarning.ENTRY_NOT_FOUND in res.warnings, alarm(res)) }
                }
                else -> {
                    // 3점호(정 · 역방향, 앞으로 부푼 것도)
                    val rr = u(1.5, 35.0)
                    val c = Pt(u(-20.0, 5.0), if (inner) u(0.3 * rr + 2, rr + 25) else u(-0.7 * rr, rr + 20))
                    val a0 = u(0.0, 2 * PI)
                    val sw = u(0.2, 2.7) * (if (rnd.nextBoolean()) 1 else -1)
                    val p = listOf(0.0, 0.5, 1.0).map { c + Pt(cos(a0 + sw * it), sin(a0 + sw * it)) * rr }
                    if ((0..30).any { (c + Pt(cos(a0 + sw * it / 30), sin(a0 + sw * it / 30)) * rr).x < 0.3 } || abs(p[2].z - p[0].z) < 0.3) return@repeat
                    val inp = Arc3Input(inner, r, tip, r3(p[0].xDia), r3(p[0].z), r3(p[1].xDia), r3(p[1].z), r3(p[2].xDia), r3(p[2].z), zc)
                    val res: RoundResult = RoundCalc.machine(inp)
                    res.solution?.let {
                        see("$inp → ${res.warnings}", it.nc, it.drawing.segs, inner, r, it.tip, RoundWarning.ENTRY_NOT_FOUND in res.warnings, RoundWarning.CONCAVE_R_LE_NOSE in res.warnings)
                    }
                }
            }
        }
        println("== 진입 길 못 찾음: 사례 $cases · 경고 ${warned.size} · 까닭 $reasons · 경고 없이 진입이 파는 글 ${silent.size}\n" + (warned + silent).take(60).joinToString("\n"))
        assertTrue(cases > 800, "사례 $cases")
        assertEquals(0, silly.size, silly.joinToString("\n"))
        assertEquals(0, silent.size, silent.joinToString("\n"))
    }

    private fun alarm(res: TaperResult) = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings
}
