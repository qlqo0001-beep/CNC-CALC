package com.bmt.nosecalc.screens.polar

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarInput
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarWarning
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.screens.minSec
import com.bmt.nosecalc.screens.previewLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 극좌표 화면 상태: 입력 만들기 · 빈칸 · 범위 밖 칸 짚기 · 저장 · 그림 = 계산 · G코드 목록의 흐린 좌표 · 시뮬레이션 시간과 진행 */
class PolarStateTest {
    /** 원본 12 의 값: 화면 칸 → 계산기 입력(빈칸은 null — 회전 0 · 여유 1 은 계산기가 채움) */
    @Test
    fun originalScreenInput() {
        val s = polarOriginal12()
        assertEquals(
            PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0),
            s.input(),
        )
        val r = PolarG12.calc(s.input())
        val sol = assertNotNull(r.solution)
        assertEquals(emptyList(), r.warnings)
        assertEquals("(R5 T0)", sol.ncG41G42.lines.first())
        assertEquals("(G40 DIRECT R5 T0)", sol.ncG40.lines.first())
    }

    /** 원본 07 의 줄이 기계 보정 프로그램에 그대로 — 같은 수를 회사 표기로(calc `originalHexagonLines` 와 같은 입력) */
    @Test
    fun original07Lines() {
        val sol = assertNotNull(PolarG12.calc(polarOriginal07().input()).solution)
        val lines = sol.ncG41G42.lines
        val want = listOf(
            "G1X50.807C-36.", "G2X36.95C-40.R8.", "G1X-36.95", "G2X-50.807C-36.R8.", "G1X-87.757C-4.", "G2C4.R8.",
            "G1X-50.807C36.", "G2X-36.95C40.R8.", "G1X36.95",
        )
        val at = lines.indexOf(want.first())
        assertTrue(at >= 0, "원본 07 첫 줄이 없다: $lines")
        assertEquals(want, lines.subList(at, at + want.size))
    }

    /** 처음은 모두 빈칸: 파란 안내의 '남은 것' 다섯, 범위 밖 없음, 외측 · 육각형 · X 직경 */
    @Test
    fun emptyWaits() {
        val s = PolarState()
        val r = PolarG12.calc(s.input())
        assertEquals(listOf(PolarWarning.MISSING_INPUT), r.warnings)
        assertEquals(listOf("외경", "W", "공구경", "깊이 Z", "이송 F"), s.missing())
        assertEquals(emptySet(), s.bad())
        assertEquals(PolarShape.HEXAGON, s.shape)
        assertTrue(s.xDiameter)
        assertFalse(s.inner)
        assertEquals("값을 넣으면 바로 계산합니다 — 남은 것: 외경 · W · 공구경 · 깊이 Z · 이송 F.", polarWarningText(PolarWarning.MISSING_INPUT, s))
        s.inner = true
        assertEquals("기초 구멍 지름", s.missing().first())
        assertEquals("기초 구멍 지름", s.label(PolarField.OD))
        // 가공 값 자리표시 = 빈칸일 때 계산기가 쓰는 값
        assertEquals("0", s.placeholder(PolarField.CORNER_R))
        assertEquals("0", s.placeholder(PolarField.ROTATION))
        assertEquals("기본 1", s.placeholder(PolarField.CLEAR))
        PolarState.REQUIRED.forEach { assertEquals("", s.placeholder(it), "도면 값 $it 은 자리표시 없음") }
    }

    /** 자리표시가 말하는 값 = 빈칸일 때 계산(코너 R 0 · 회전 0 · 여유 1): 비운 것과 그 값을 넣은 것의 NC 가 같다 */
    @Test
    fun placeholdersAreCalcDefaults() {
        val base = mapOf("od" to "80", "w" to "70", "tool" to "10", "depth" to "-5", "feed" to "100")
        val blank = PolarG12.calc(PolarState(values = base).input()).solution!!
        val filled = PolarG12.calc(PolarState(values = base + mapOf("rc" to "0", "rot" to "0", "clear" to "1")).input()).solution!!
        assertEquals(blank.ncG41G42.lines, filled.ncG41G42.lines)
        assertEquals(blank.ncG40.lines, filled.ncG40.lines)
    }

    /** 칸마다 범위 밖 값: 화면이 짚는 칸(bad)이 있으면 계산기가 BAD_INPUT, 없으면 BAD_INPUT 이 아니다 */
    @Test
    fun badFieldsMatchCalc() {
        val values = listOf("-20000", "-10001", "-10000", "-5", "-1", "0", "0.0004", "0.5", "1", "3", "5", "10", "60", "79.9", "99999", "100000", "100001", "10000", "10001")
        var checked = 0
        for (inner in listOf(false, true)) for (shape in PolarShape.entries) {
            if (inner && shape == PolarShape.TWO_FLAT) continue
            val base = if (inner) polarInnerSquare().also { it.shape = shape } else polarOriginal12().also { it.shape = shape }
            for (f in base.fields) for (v in values) {
                val s = PolarState(inner, shape, base.xDiameter, PolarField.entries.associate { it.key to base.value(it) } + (f.key to v))
                val r = PolarG12.calc(s.input())
                val calcBad = r.warnings == listOf(PolarWarning.BAD_INPUT)
                assertEquals(calcBad, s.bad().isNotEmpty(), "$inner $shape ${f.key}=$v: 화면 ${s.bad()} · 계산기 ${r.warnings}")
                if (calcBad) assertEquals(setOf(f), s.bad())
                checked++
            }
        }
        assertTrue(checked > 1500)
        // 2면폭은 코너 R 칸이 없고(감춤) 계산기도 무시한다 — 범위 밖 값이 남아 있어도 짚지 않는다
        val two = polarTwoFlat().also { it.set(PolarField.CORNER_R, "-3") }
        assertEquals(emptySet(), two.bad())
        assertNotNull(PolarG12.calc(two.input()).solution)
        // 외측에서 2면폭을 고른 뒤 내측으로 바꾸면: 계산기 BAD_INPUT, 화면은 도형 글
        val innerTwo = polarTwoFlat().also { it.inner = true }
        assertTrue(innerTwo.shapeBad)
        assertEquals(listOf(PolarWarning.BAD_INPUT), PolarG12.calc(innerTwo.input()).warnings)
        assertTrue(polarWarningText(PolarWarning.BAD_INPUT, innerTwo).contains("2면폭은 외측 가공만"))
    }

    /** 멈춘 경고가 짚는 칸 */
    @Test
    fun flaggedFields() {
        fun stop(s: PolarState) = PolarG12.calc(s.input()).warnings.single()
        val wge = polarWgeOd()
        assertEquals(PolarWarning.W_GE_OD, stop(wge))
        assertEquals(setOf(PolarField.OD, PolarField.WIDTH), wge.flagged(listOf(stop(wge))))
        val big = polarOriginal12().also { it.set(PolarField.CORNER_R, "35") }
        assertEquals(PolarWarning.CORNER_R_TOO_BIG, stop(big))
        assertEquals(setOf(PolarField.CORNER_R), big.flagged(listOf(stop(big))))
        val hole = polarInnerSquare().also { it.set(PolarField.OD, "41") }
        assertEquals(PolarWarning.HOLE_GT_W, stop(hole))
        assertEquals(setOf(PolarField.OD, PolarField.WIDTH), hole.flagged(listOf(stop(hole))))
        val tool = polarInnerSquare().also { it.set(PolarField.TOOL_DIA, "20") }
        assertEquals(PolarWarning.TOOL_TOO_BIG, stop(tool))
        assertEquals(setOf(PolarField.TOOL_DIA), tool.flagged(listOf(stop(tool))))
        val gt = polarInnerSquare().also { it.set(PolarField.OD, "10"); it.set(PolarField.TOOL_DIA, "9") }
        assertEquals(PolarWarning.TOOL_GT_HOLE, stop(gt))
        assertEquals(setOf(PolarField.TOOL_DIA, PolarField.OD), gt.flagged(listOf(stop(gt))))
        gt.set(PolarField.CLEAR, "2")
        assertEquals(setOf(PolarField.TOOL_DIA, PolarField.OD, PolarField.CLEAR), gt.flagged(listOf(stop(gt))))
        // 값 부족 중의 범위 밖 값
        val w = polarBadWhileWaiting()
        assertEquals(listOf(PolarWarning.MISSING_INPUT), PolarG12.calc(w.input()).warnings)
        assertEquals(setOf(PolarField.DEPTH), w.bad())
        assertEquals("범위 밖 값 — 깊이 Z 는 0 보다 작게 (면 Z0 아래, 예: −5).", polarWarningText(PolarWarning.BAD_INPUT, w))
    }

    /** 저장: 외측/내측 · 도형 · X 직경/반경 · 칸 글 그대로 */
    @Test
    fun saverRoundTrip() {
        val s = polarTriangle().also { it.inner = true; it.set(PolarField.CLEAR, "0.5") }
        val scope = SaverScope { true }
        val saved = with(PolarState.Saver) { scope.save(s) }!!
        val back = PolarState.Saver.restore(saved)!!
        assertEquals(s.inner, back.inner)
        assertEquals(s.shape, back.shape)
        assertEquals(s.xDiameter, back.xDiameter)
        PolarField.entries.forEach { assertEquals(s.value(it), back.value(it), it.key) }
        assertEquals(s.input(), back.input())
    }

    /**
     * 그림 = 계산: 값이 다 들어가기 전에 그리는 도형(같은 규칙으로 화면이 만든 것)이 풀린 뒤 계산기 단면과 같은 곳을 지나는지.
     * 둘의 둘레를 촘촘히 짚어 서로의 둘레에서 0.0001 mm 안인지 본다(시작점 · 방향은 달라도 된다)
     */
    @Test
    fun figureMatchesCalc() {
        var n = 0
        for (inner in listOf(false, true)) for (shape in PolarShape.entries) for (rc in listOf("", "2", "6")) for (rot in listOf("", "12.5", "-40")) {
            if (inner && shape == PolarShape.TWO_FLAT) continue
            val s = if (inner) PolarState(true, shape, values = mapOf("od" to "20", "w" to "40", "rc" to rc, "rot" to rot, "tool" to "6", "depth" to "-3", "feed" to "80"))
            else PolarState(false, shape, values = mapOf("od" to "60", "w" to "40", "rc" to rc, "rot" to rot, "tool" to "6", "depth" to "-3", "feed" to "80"))
            val sol = assertNotNull(PolarG12.calc(s.input()).solution, "$inner $shape $rc $rot")
            val drawn = polarGeom(s, null)
            assertFalse(drawn.sample)
            assertSameBoundary(sol.shape.segs, drawn.shape, "$inner $shape R$rc 회전$rot")
            // 풀린 그림은 계산기 단면 그대로
            assertEquals(sol.shape.segs, polarGeom(s, sol).shape)
            n++
        }
        assertEquals(99, n)
    }

    /** 외경 · W 가운데 빈 것은 예시 비율(원본 12 의 80 : 70)로 그린다 — 이름은 넣은 값만 */
    @Test
    fun sampleFigure() {
        val g = polarGeom(PolarState(), null)
        assertTrue(g.sample)
        assertEquals("W", g.wText)
        assertNull(g.odText)
        assertEquals(35.0, g.a, 1e-12)
        assertEquals(40.0, g.stockR, 1e-12)
        val w = polarGeom(PolarState(values = mapOf("w" to "35")), null)
        assertEquals("W 35", w.wText)
        assertEquals(20.0, w.stockR, 1e-12)
        // 맞지 않는 값(W ≥ 외경)도 넣은 그대로 — 멈춘 까닭이 그림에 보이게
        val bad = polarGeom(polarWgeOd(), null)
        assertEquals(35.0, bad.a, 1e-12)
        assertEquals(30.0, bad.stockR, 1e-12)
        assertEquals("Ø60", bad.odText)
    }

    /**
     * G코드 목록의 흐린 좌표: 흐린 낱말을 빼고 이으면 NC 글 그대로, 흐린 낱말은 극좌표 평면을 움직이는 줄에만, 그 값은 그 축이 앞에서 마지막으로 쓰인 낱말과 같다
     * (NC 글을 다시 읽어 따로 센다). 평면을 움직이는 줄은 흐린 것까지 X · C 를 다 가진다
     */
    @Test
    fun dimCoordinatesAreModalValues() {
        var dims = 0
        for (s in listOf(polarOriginal12(), polarOriginal07(), polarInnerSquare(), polarInnerSmallR(), polarTwoFlat(), polarTriangle())) {
            val sol = PolarG12.calc(s.input()).solution!!
            for (prog in listOf(sol.ncG41G42, sol.ncG40)) {
                var lastX: String? = null
                var lastC: String? = null
                for (b in prog.blocks) {
                    val toks = ncTokens(b)
                    assertEquals(b.text, toks.filterNot { it.dim }.joinToString("") { it.text })
                    if (toks.any { it.dim }) {
                        assertTrue(b.polar && b.path.isNotEmpty(), b.text)
                        toks.filter { it.dim }.forEach { t ->
                            if (t.text.startsWith("X")) assertEquals(lastX, t.text, b.text) else assertEquals(lastC, t.text, b.text)
                            dims++
                        }
                    }
                    if (b.polar && b.path.isNotEmpty()) {
                        val words = toks.map { it.text }
                        assertEquals(1, words.count { it.startsWith("X") }, b.text)
                        assertEquals(1, words.count { it.startsWith("C") }, b.text)
                        // 흐린 것까지 넣은 줄의 순서: G … X … C …
                        assertTrue(words.indexOfFirst { it.startsWith("X") } < words.indexOfFirst { it.startsWith("C") }, words.toString())
                    }
                    if (b.text.startsWith("G12.1") || b.text.startsWith("G13.1")) {
                        lastX = null
                        lastC = null
                    }
                    ncWords(b.text).forEach { w ->
                        if (w.startsWith("X")) lastX = w
                        if (w.startsWith("C")) lastC = w
                    }
                }
            }
        }
        assertTrue(dims > 20, "흐린 좌표 $dims 개")
        // 원본 07 의 흐린 값: `G01 X-36.95 C-40.`(C 흐림) · `G02 X-87.757 C4. R8.`(X 흐림)
        val p = PolarG12.calc(polarOriginal07().input()).solution!!.ncG41G42
        assertEquals(listOf(NcTok("G1"), NcTok("X-36.95"), NcTok("C-40.", dim = true)), ncTokens(p.blocks.first { it.text == "G1X-36.95" }))
        assertEquals(listOf(NcTok("G2"), NcTok("X-87.757", dim = true), NcTok("C4."), NcTok("R8.")), ncTokens(p.blocks.first { it.text == "G2C4.R8." }))
    }

    /** 재생 계획: 줄 시간 = 경로 길이 ÷ F(Z 이송은 Z 거리), 줄 끝 공구 = 그 줄 경로 끝, 줄 가운데 공구는 그 줄 경로 위 */
    @Test
    fun simPlanFollowsPath() {
        for (s in listOf(polarOriginal12(), polarInnerSquare(), polarTwoFlat())) {
            val sol = PolarG12.calc(s.input()).solution!!
            for (prog in listOf(sol.ncG41G42, sol.ncG40)) {
                val plan = simPlan(sol, prog, 100.0)
                val total = prog.blocks.flatMap { it.path }.sumOf(::segLength)
                // Z: G01 Z 여유 1 → −5 (6 mm) 한 번
                assertEquals((total + 6.0) / (100.0 / 60), plan.feedSeconds, 1e-9)
                assertEquals(prog.toolPath.segs, plan.traveled(prog.blocks.lastIndex, 1.0))
                assertNull(plan.toolAt(0, 0.5), "위치 잡기 전(주석 줄)에는 공구 없음")
                val lathe = prog.blocks.indexOfFirst { it.text.startsWith("G0X") }
                assertEquals(PolarG12.pt(sol.startX, 0.0), plan.toolAt(lathe, 1.0))
                prog.blocks.forEachIndexed { i, b ->
                    if (b.path.isEmpty()) return@forEachIndexed
                    assertEquals(b.path.last().end, plan.toolAt(i, 1.0))
                    for (f in listOf(0.13, 0.5, 0.91)) {
                        val p = plan.toolAt(i, f)!!
                        assertTrue(b.path.minOf { dist(p, it) } < 1e-9, "${b.text} $f: 경로 밖 $p")
                    }
                }
            }
        }
    }

    /** 재생: 프레임을 잘게 흘리면 줄 시간 합만큼 걸려 끝난다, 싱글 블록은 줄 끝마다 멈춘다, 리셋은 처음으로 */
    @Test
    fun simRunAdvances() {
        val sol = PolarG12.calc(polarOriginal12().input()).solution!!
        val plan = simPlan(sol, sol.ncG41G42, 100.0)
        val run = SimRun(plan, speedIdx = SPEEDS.indexOf(10))
        val want = plan.blocks.indices.sumOf { run.seconds(it) }
        run.start()
        var t = 0.0
        while (run.running) {
            run.advance(1.0 / 60)
            t += 1.0 / 60
        }
        assertTrue(run.done)
        assertEquals(want, t, 1.0 / 60 + 1e-9)
        // 배율: 이송 줄은 나눠지고 머무는 줄은 그대로
        plan.timing.forEachIndexed { i, (sec, scaled) -> assertEquals(if (scaled) sec / 10 else sec, run.seconds(i), 1e-12) }
        // 끝난 뒤 시작 = 처음부터
        run.start()
        assertEquals(0, run.block)
        run.reset()
        assertEquals(-1, run.block)
        assertFalse(run.running)
        // 싱글 블록: 한 줄 끝에서 멈추고(방금 끝난 줄이 지금 줄), 다시 시작하면 다음 줄
        val single = SimRun(plan, single = true)
        single.start()
        single.advance(1000.0)
        assertEquals(0, single.block)
        assertEquals(1.0, single.frac)
        assertFalse(single.running)
        single.start()
        assertEquals(1, single.block)
        single.advance(1000.0)
        assertEquals(1, single.block)
        assertFalse(single.running)
        // 멈춘 자리에서 계속
        val mid = SimRun(plan, block = 9, frac = 0.4)
        mid.start()
        assertEquals(9, mid.block)
        assertEquals(0.4, mid.frac, 1e-12)
    }

    /**
     * 견고성(InputRobustnessTest 와 같은 뜻): 키패드로 칠 수 있는 글(빈칸 · 0 · 음수 · 12자리 · 아주 작은 수)을 칸마다 흔들어 2만 가지 —
     * 화면이 쓰는 것(경고 글 · 그림 자료 · 결과 칸 · 키패드 한 줄 · 목록 낱말 · 재생 계획)이 예외 없이 나오고 그림 점이 모두 유한한지
     */
    @Test
    fun fuzzNoCrash() {
        val texts = listOf("", "0", "-0", "1", "-1", "0.001", "0.0004", "2.5", "-5", "10", "35", "40", "70", "80", "-30", "999999999999", "-999999999999", "10000", "100000", "0.5")
        val rnd = kotlin.random.Random(28)
        repeat(20_000) {
            val s = PolarState(
                inner = rnd.nextBoolean(),
                shape = PolarShape.entries[rnd.nextInt(PolarShape.entries.size)],
                xDiameter = rnd.nextBoolean(),
                values = PolarField.entries.associate { it.key to texts[rnd.nextInt(texts.size)] },
            )
            val r = PolarG12.calc(s.input())
            val sol = r.solution
            r.warnings.forEach { polarWarningText(it, s, sol) }
            if (s.bad().isNotEmpty() || s.shapeBad) polarWarningText(PolarWarning.BAD_INPUT, s)
            val g = polarGeom(s, sol)
            g.shape.forEach { seg -> listOf(seg.start, seg.end).forEach { assertTrue(it.z.isFinite() && it.x.isFinite(), "$s") } }
            assertTrue(g.stockR.isFinite() && g.a.isFinite())
            if (sol != null) {
                polarResultCells(s, sol, false)
                polarResultCells(s, sol, true)
                previewLine(s, sol)
                for (p in listOf(sol.ncG41G42, sol.ncG40)) {
                    p.blocks.forEach { ncTokens(it) }
                    val plan = simPlan(sol, p, s.num(PolarField.FEED)!!)
                    assertTrue(plan.feedSeconds.isFinite())
                    plan.toolAt(p.blocks.lastIndex / 2, .5)
                }
            }
        }
    }

    @Test
    fun timeText() {
        assertEquals("42초", minSec(42.4))
        assertEquals("1분 0초", minSec(59.6))
        assertEquals("3분 54초", minSec(234.0))
    }

    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            val t = if (l2 == 0.0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0)
            (p - (s.start + d * t)).length
        }
        is Arc -> {
            // 원호 위 각도 범위 안이면 반지름 차, 아니면 끝점까지
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            val ap = atan2(p.x - s.center.x, p.z - s.center.z)
            val sweep = s.sweepDeg * PI / 180
            var d = if (s.cw) a0 - ap else ap - a0
            d = ((d % (2 * PI)) + 2 * PI) % (2 * PI)
            if (d <= sweep + 1e-12) abs((p - s.center).length - s.radius) else minOf((p - s.start).length, (p - s.end).length)
        }
    }

    private fun samples(segs: List<Seg>): List<Pt> = segs.flatMap { s ->
        (0..20).map { k -> partial(s, k / 20.0).end }
    }

    private fun assertSameBoundary(a: List<Seg>, b: List<Seg>, what: String) {
        samples(a).forEach { p -> assertTrue(b.minOf { dist(p, it) } < 1e-4, "$what: 계산기 점 $p 가 그림 둘레 밖") }
        samples(b).forEach { p -> assertTrue(a.minOf { dist(p, it) } < 1e-4, "$what: 그림 점 $p 가 계산기 둘레 밖") }
        // 꼭 같은 바퀴인지(가운데를 도는 각이 2π)
        assertEquals(2 * PI, abs(turn(b)), 1e-6, what)
    }

    /** 원점에서 본 둘레의 감긴 각 */
    private fun turn(segs: List<Seg>): Double {
        val pts = samples(segs)
        return pts.zipWithNext { p, q ->
            val d = atan2(q.x, q.z) - atan2(p.x, p.z)
            when {
                d > PI -> d - 2 * PI
                d < -PI -> d + 2 * PI
                else -> d
            }
        }.sum()
    }
}
