package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T1–T2 사이 원호([TaperInput.arc], 피드백 2026-11-10 "테이퍼 계산에서 면취에 r값"). 직선 대신 반지름 R 의 호로 잇고, 모서리 R · 직접 보정 · G41/G42 회사 틀 ·
 * T1 ⇄ T2 · 시작 · 끝점이 직선 때처럼 따라온다. 수치 기준은 기하에서 따로 계산한 예제(설계B — [handNumbers])이고, 나머지는 성질로 본다:
 * 호가 T1 · T2 를 지나고 반지름 R, 모서리 R 이 양쪽에 접함, 노즈 원이 윤곽에 접함(파고듦 없음), G41/G42 글을 시뮬레이터로 돌린 깎인 윤곽 = 도면
 */
class TaperArcTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    private fun ok(r: TaperResult, tag: String = ""): TaperSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings} $tag")
        return r.solution
    }

    private fun stops(w: TaperWarning, r: TaperResult, tag: String = "") {
        assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다 $tag")
        assertEquals(listOf(w), r.warnings, tag)
    }

    /** 형상(TYPE)에 맞는 두 점. T1 = 그림 오른쪽 */
    private fun base(inner: Boolean, type: Int, dz: Double, dxDia: Double): TaperInput {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        return TaperInput(inner, type, x1 = 40.0, z1 = -10.0, x2 = 40.0 + up * dxDia, z2 = -10.0 - dz)
    }

    /** 같은 형상을 반대 끝부터 — 칸의 점 · 모서리 R · 시작 · 끝을 맞바꿔 넣는다(R · 볼록은 형상의 것이라 그대로) */
    private fun TaperInput.flipped() =
        copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start, flip = !flip)

    private fun ceil3(v: Double) = ceil(v * 1000 - 1e-6) / 1000

    private fun minR(i: TaperInput) = TaperCalc.arcMinR(i.x1!!, i.z1!!, i.x2!!, i.z2!!)!!

    /** R = 가장 작은 R 의 k 배(0.001 로 올림) */
    private fun TaperInput.withArc(k: Double, convex: Boolean) = copy(arc = true, arcR = ceil3(minR(this) * k), arcConvex = convex)

    /** 앞뒤 요소를 6 mm 깎게 시작 · 끝 좌표를 넣는다(단면은 X, 수평은 Z) */
    private fun TaperInput.withLegs(): TaperInput {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        return copy(
            start = if (type <= 2) x1!! - up * 12 else z1!! + 6,
            end = if (type == 2 || type == 4) x2!! + up * 12 else z2!! - 6,
        )
    }

    private val chords = listOf(5.0 to 10.0, 3.0 to 20.0, 8.0 to 6.0)

    /** 외 · 내경 × 형상 1~5 × 현 셋 × R(가장 작은 R 의 1 · 1.4 · 4 배) × 볼록 · 오목 */
    private fun shapes(): List<TaperInput> = buildList {
        for (inner in listOf(false, true)) for (type in 1..5) for ((dz, dx) in chords) for (k in listOf(1.0, 1.4, 4.0)) for (convex in listOf(true, false)) {
            add(base(inner, type, dz, dx).withArc(k, convex))
        }
    }

    private fun startDir(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> (s.start - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
    }

    private fun endDir(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> (s.end - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
    }

    private fun samples(s: Seg, step: Double = 0.05): List<Pt> = when (s) {
        is Line -> {
            val n = max(2, ((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val a0 = angleOf(s.start - s.center)
            val sw = s.sweepRad() * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * s.radius / step).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * s.radius }
        }
    }

    private fun lengthOf(s: Seg) = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> s.radius * s.sweepRad()
    }

    // ------------------------------------------------------------ 직선은 그대로

    @Test
    fun straightIgnoresArcFields() {
        var n = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (flip in listOf(false, true)) for (legs in listOf(false, true)) {
            val b = base(inner, type, 5.0, 10.0).copy(cornerR1 = 0.5).let { if (legs) it.withLegs() else it }.let { if (flip) it.flipped() else it }
            val plain = TaperCalc.calc(b)
            assertNotNull(plain.solution)
            assertNull(plain.solution.arc)
            for (r in listOf(null, 0.0, -1.0, 3.0, 50.0, Double.NaN)) for (cv in listOf(true, false)) {
                assertEquals(plain, TaperCalc.calc(b.copy(arcR = r, arcConvex = cv)), "$b arcR $r")
                n++
            }
        }
        assertTrue(n >= 400)
    }

    // ------------------------------------------------------------ 호의 기하

    /** 모서리 R 이 없으면 호가 정확히 T1 · T2 를 지나고 반지름이 R, 작은 호, 볼록이면 살이 남는 쪽(외경은 지름 큰 쪽 · 내경은 축 쪽)으로 부푼다 */
    @Test
    fun arcPassesThroughPoints() {
        var n = 0
        for (s0 in shapes()) for (flip in listOf(false, true)) for (reverse in listOf(false, true)) {
            val inp = (if (flip) s0.flipped() else s0).copy(reverse = reverse)
            val tag = inp.toString()
            val s = ok(TaperCalc.calc(inp), tag)
            val info = assertNotNull(s.arc, tag)
            val a = s.drawing.segs[info.index] as Arc
            val t1 = Pt.ofDia(inp.z1!!, inp.x1!!)
            val t2 = Pt.ofDia(inp.z2!!, inp.x2!!)
            // 정방향은 T1 → T2 로 깎고 계산기 역방향은 마지막 점부터
            val (from, to) = if (reverse) t2 to t1 else t1 to t2
            assertTrue((a.start - from).length < 1e-9 && (a.end - to).length < 1e-9, "끝점 $tag: ${a.start} ${a.end}")
            assertEquals(inp.arcR!!, a.radius, 1e-9, tag)
            assertEquals(inp.arcR!!, (a.end - a.center).length, 1e-9, tag)
            assertEquals(inp.arcR!!, info.r, 1e-9, tag)
            assertTrue(a.sweepDeg < 90.0 + 1e-3, "호 각 ${a.sweepDeg} $tag")
            // 볼록 = 호 가운데가 현보다 공기 쪽(외경 +X, 내경 −X)
            val mid = samples(a).let { it[it.size / 2] }
            val bulge = (mid - (t1 + t2) * 0.5).x * (if (inp.inner) -1 else 1)
            assertTrue(if (inp.arcConvex) bulge > 1e-6 else bulge < -1e-6, "부푼 쪽 $bulge $tag")
            // 방향 글자가 실제로 도는 쪽과 같다(작은 호)
            assertEquals(((a.start - a.center) cross (a.end - a.center)) < 0, a.cw, tag)
            assertEquals(a.cw, info.cw, tag)
            assertTrue((a.center - info.center).length < 1e-12, tag)
            // 보정 R = 볼록 R + r · 오목 R − r
            assertEquals(inp.arcR!! + (if (inp.arcConvex) 0.4 else -0.4), info.progR!!, 1e-9, tag)
            // 호는 두 점의 X · Z 범위 안(부풀지 않는다 — 0.001 올림만큼의 여유)
            for (p in samples(a)) {
                assertTrue(p.x in minOf(t1.x, t2.x) - 1e-6..maxOf(t1.x, t2.x) + 1e-6 && p.z in minOf(t1.z, t2.z) - 1e-6..maxOf(t1.z, t2.z) + 1e-6, "범위 $p $tag")
            }
            n++
        }
        assertTrue(n == 2 * 5 * 3 * 3 * 2 * 4, "$n")
    }

    /** G02/G03: 보정 쪽이 G42 면 볼록 G03 · 오목 G02, G41 이면 반대. 외경 정방향 G42 · 뒤집기 G41, 내경은 반대. 글의 G2/G3 도 같다 */
    @Test
    fun arcDirection() {
        fun g(inner: Boolean, flip: Boolean, convex: Boolean): Pair<Side, String> {
            val b = base(inner, 1, 5.0, 10.0).copy(arc = true, arcR = 10.0, arcConvex = convex)
            val s = ok(TaperCalc.calc(if (flip) b.flipped() else b))
            val word = if (s.arc!!.cw) "G2" else "G3"
            for ((nc, rr) in listOf(s.ncG40 to s.arc!!.progR!!, s.ncG41G42 to 10.0)) {
                val arcLine = nc.single { it.endsWith("R" + Fmt.word(rr)) }
                // 모달: 그 줄이나 앞줄에서 마지막으로 쓴 이동 G
                val gs = nc.subList(0, nc.indexOf(arcLine) + 1).flatMap(::ncWords).last { it in listOf("G0", "G1", "G2", "G3") }
                assertEquals(word, gs, "$inner $flip $convex $nc")
            }
            return s.side to (if (s.arc!!.cw) "G02" else "G03")
        }
        assertEquals(Side.G42 to "G03", g(inner = false, flip = false, convex = true))
        assertEquals(Side.G42 to "G02", g(inner = false, flip = false, convex = false))
        assertEquals(Side.G41 to "G02", g(inner = false, flip = true, convex = true))
        assertEquals(Side.G41 to "G03", g(inner = false, flip = true, convex = false))
        assertEquals(Side.G41 to "G02", g(inner = true, flip = false, convex = true))
        assertEquals(Side.G41 to "G03", g(inner = true, flip = false, convex = false))
        assertEquals(Side.G42 to "G03", g(inner = true, flip = true, convex = true))
        assertEquals(Side.G42 to "G02", g(inner = true, flip = true, convex = false))
    }

    /** 모서리 R: 필렛이 T1–T2 호와 이웃 직선에 접하고(접점의 접선이 같다), 호는 같은 원 위에서 줄기만 한다 */
    @Test
    fun cornerFilletsTouchArcAndLine() {
        var fillets = 0
        var skipped = 0
        for (s0 in shapes()) for ((r1, r2) in listOf(0.5 to 0.0, 0.0 to 0.5, 0.5 to 0.8)) for (flip in listOf(false, true)) {
            val sharp = ok(TaperCalc.calc(if (flip) s0.flipped() else s0))
            val inp = s0.copy(cornerR1 = r1, cornerR2 = r2).let { if (flip) it.flipped() else it }
            val tag = inp.toString()
            val res = TaperCalc.calc(inp)
            val s = res.solution
            if (s == null) {
                // 안 들어가는 R(호가 짧다) · 오목 필렛 뒤 노즈가 못 닿는 꼴만
                assertTrue(res.warnings.single() in listOf(TaperWarning.CORNER_R_TOO_BIG, TaperWarning.NOSE_UNREACHABLE), "${res.warnings} $tag")
                skipped++
                continue
            }
            val d = s.drawing.segs
            val whole = sharp.drawing.segs[sharp.arc!!.index] as Arc
            val a = d[s.arc!!.index] as Arc
            assertTrue((a.center - whole.center).length < 1e-9 && abs(a.radius - whole.radius) < 1e-9 && a.cw == whole.cw, "호가 바뀜 $tag")
            for ((k, p) in s.points.withIndex()) {
                val c = p.corner ?: continue
                val want = if (k == 0) inp.cornerR1 else inp.cornerR2
                val i = d.indexOfFirst { it is Arc && (it.center - c.center).length < 1e-12 && it !== a }
                val f = d[i] as Arc
                assertEquals(want, f.radius, 1e-9, tag)
                assertEquals(want, c.r, 1e-9, tag)
                assertTrue((endDir(d[i - 1]) - startDir(f)).length < 1e-9, "앞 접선 $tag")
                assertTrue((endDir(f) - startDir(d[i + 1])).length < 1e-9, "뒤 접선 $tag")
                assertTrue(lengthOf(d[i - 1]) > 1e-9 && lengthOf(d[i + 1]) > 1e-9, "이웃을 다 먹음 $tag")
                // 이웃 가운데 하나는 T1–T2 호
                assertTrue(d[i - 1] === a || d[i + 1] === a, tag)
                fillets++
            }
            for ((x, y) in d.zipWithNext()) assertTrue((x.end - y.start).length < 1e-9, "끊김 $tag")
        }
        assertTrue(fillets > 600, "필렛 $fillets 건너뜀 $skipped")
    }

    // ------------------------------------------------------------ 직접 보정

    /**
     * 직접 보정: 노즈 중심 길의 깎는 요소마다 그 요소를 늘인 선 · 원에서 꼭 r(접한다), 어디서도 도면에서 r 보다 가깝지 않다(파고듦 없음),
     * 보정 호는 도면 호와 같은 중심 · R ± r, 프로그램 점 = 노즈 중심 + 팁 벡터. 노즈R 0.2 ~ 1.2 × 모서리 R × 시작 · 끝 × 뒤집기
     */
    @Test
    fun noseTouchesContour() {
        var solved = 0
        var stopped = 0
        for (s0 in shapes()) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for ((r1, r2) in listOf(0.0 to 0.0, 0.6 to 0.0, 0.3 to 1.0)) for (legs in listOf(false, true)) {
            for (flip in listOf(false, true)) {
                val inp = s0.copy(noseR = r, cornerR1 = r1, cornerR2 = r2).let { if (legs) it.withLegs() else it }.let { if (flip) it.flipped() else it }
                val tag = inp.toString()
                val res = TaperCalc.calc(inp)
                val s = res.solution
                if (s == null) {
                    assertTrue(
                        res.warnings.single() in listOf(TaperWarning.CORNER_R_TOO_BIG, TaperWarning.NOSE_UNREACHABLE, TaperWarning.BAD_LEG),
                        "${res.warnings} $tag",
                    )
                    stopped++
                    continue
                }
                solved++
                val d = s.drawing.segs
                val tv = NoseComp.tipVector(s.tip, r)
                val from = if (s.fromStart) 0 else 1
                val to = if (s.toEnd) d.size else d.size - 1
                for (k in from until to) {
                    val c = s.center.segs[k]
                    val pr = s.program.segs[k]
                    assertTrue((pr.start - c.start - tv).length < 1e-9 && (pr.end - c.end - tv).length < 1e-9, "팁 벡터 $k $tag")
                    val el = d[k]
                    for (p in samples(c, 0.1)) {
                        val off = when (el) {
                            is Line -> abs((el.end - el.start).unit() cross (p - el.start))
                            is Arc -> abs((p - el.center).length - el.radius)
                        }
                        // 오목 R ≤ r 로 사라진 모서리 R 자리는 한 점(r 이 남는다 — 경고가 알린다)
                        if (lengthOf(c) > 1e-9) assertEquals(r, off, 1e-6, "접함 $k $p $tag")
                        val near = d.minOf { distTo(it, p) }
                        assertTrue(near > r - 1e-6, "파고듦 ${r - near} 요소 $k $p $tag")
                    }
                }
                // 진입 G0 점에서 첫 점까지의 G1 도 도면을 파지 않는다(시작을 비우면 호의 접선으로 들어온다)
                for (p in samples(Line(s.entry - tv, s.center.segs[from].start), 0.1)) {
                    val near = d.minOf { distTo(it, p) }
                    assertTrue(near > r - 1e-6, "진입 줄 파고듦 ${r - near} $p $tag")
                }
                val a = s.arc!!
                val ca = s.center.segs[a.index] as Arc
                assertTrue((ca.center - a.center).length < 1e-9, "보정 호 중심 $tag")
                assertEquals(a.r + (if (inp.arcConvex) r else -r), ca.radius, 1e-9, tag)
                assertEquals(ca.radius, a.progR!!, 1e-9, tag)
                assertEquals((s.drawing.segs[a.index] as Arc).cw, ca.cw, tag)
            }
        }
        assertTrue(solved > 3000, "푼 것 $solved 멈춘 것 $stopped")
    }

    /** T1 ⇄ T2: 같은 호(같은 원) · 같은 보정점, 순서와 G02/G03 · 보정 쪽만 반대 */
    @Test
    fun flipKeepsArcPoints() {
        var n = 0
        for (s0 in shapes()) for ((r1, r2) in listOf(0.0 to 0.0, 0.4 to 0.7)) {
            val inp = s0.copy(cornerR1 = r1, cornerR2 = r2)
            val u = TaperCalc.calc(inp).solution ?: continue
            val f = ok(TaperCalc.calc(inp.flipped()), inp.toString())
            assertTrue(u.side != f.side)
            assertEquals(u.tip, f.tip)
            val ua = u.arc!!
            val fa = f.arc!!
            assertEquals(!ua.cw, fa.cw, inp.toString())
            assertTrue((ua.center - fa.center).length < 1e-9 && abs(ua.r - fa.r) < 1e-9 && abs(ua.progR!! - fa.progR!!) < 1e-9, inp.toString())
            // 거의 접해서 이어지는 이음(가장 작은 R 의 0.001 올림)은 두 보정선 끝 사이 틈(1e-5 mm 쯤)을 가는 쪽 끝에 맞춰 잇는다 — 그만큼은 다를 수 있다
            val up = u.program.segs[ua.index]
            val fp = f.program.segs[fa.index]
            assertTrue((up.start - fp.end).length < 1e-4 && (up.end - fp.start).length < 1e-4, "보정점 $inp: $up / $fp")
            // 칸 순서의 T 점은 서로 거꾸로
            for ((a, b) in u.points.zip(f.points.reversed())) {
                assertTrue((a.vertex - b.vertex).length < 1e-9)
                for ((x, y) in a.nodes.zip(b.nodes.reversed())) assertTrue((x.prog - y.prog).length < 1e-4 && (x.drawn - y.drawn).length < 1e-9, "T 점 $inp")
            }
            n++
        }
        assertTrue(n > 300, "$n")
    }

    // ------------------------------------------------------------ 못 그리는 입력

    @Test
    fun arcStops() {
        val b = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, arc = true, arcR = 10.0)
        ok(TaperCalc.calc(b))
        // 빈칸: R, 좌표 하나(호는 네 좌표를 모두 — 각도로 채우지 않는다)
        stops(TaperWarning.MISSING_INPUT, TaperCalc.calc(b.copy(arcR = null)))
        stops(TaperWarning.MISSING_INPUT, TaperCalc.calc(b.copy(x2 = null, angleDeg = 45.0)))
        stops(TaperWarning.MISSING_INPUT, TaperCalc.calc(b.copy(z1 = null)))
        // 각도 칸은 읽지 않는다(점과 안 맞아도 · 범위 밖이어도)
        assertEquals(TaperCalc.calc(b), TaperCalc.calc(b.copy(angleDeg = 12.0)))
        assertEquals(TaperCalc.calc(b), TaperCalc.calc(b.copy(angleDeg = 95.0)))
        // R 이 숫자가 아니거나 0 이하 · 한도 밖
        for (r in listOf(0.0, -3.0, Double.NaN, Double.POSITIVE_INFINITY, 1e12)) stops(TaperWarning.BAD_INPUT, TaperCalc.calc(b.copy(arcR = r)), "R $r")
        // 현(7.071)의 절반보다 작다 · 그 바로 아래 · 가장 작은 R(5) 바로 아래
        for (r in listOf(1.0, 3.535, 3.6, 4.9, 4.99)) stops(TaperWarning.ARC_R_TOO_SMALL, TaperCalc.calc(b.copy(arcR = r)), "R $r")
        for (cv in listOf(true, false)) ok(TaperCalc.calc(b.copy(arcR = 5.0, arcConvex = cv)), "R5 $cv")
        // 같은 점 · 수평 · 수직 · 오르내림이 형상과 반대
        stops(TaperWarning.BAD_ANGLE, TaperCalc.calc(b.copy(x2 = 40.0, z2 = 0.0)))
        stops(TaperWarning.BAD_ANGLE, TaperCalc.calc(b.copy(x2 = 40.0)))
        stops(TaperWarning.BAD_ANGLE, TaperCalc.calc(b.copy(z2 = 0.0)))
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(b.copy(x2 = 30.0)))
        // 오목 호 R < 노즈R: 주 요소가 사라진다 — 멈춤. R = 노즈R 은 꼭 맞아 숫자와 노랑
        val small = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 41.0, z2 = -0.5, arc = true, arcR = 0.6, arcConvex = false, noseR = 0.8)
        stops(TaperWarning.NOSE_UNREACHABLE, TaperCalc.calc(small))
        // 모서리 R 이 호에 안 들어간다
        stops(TaperWarning.CORNER_R_TOO_BIG, TaperCalc.calc(b.copy(cornerR1 = 30.0)))
        stops(TaperWarning.CORNER_R_TOO_BIG, TaperCalc.calc(b.copy(cornerR1 = 20.0, cornerR2 = 20.0)))
        ok(TaperCalc.calc(b.copy(cornerR1 = 1.0, cornerR2 = 2.0)))
        // 아주 큰 R(거의 직선) · 큰 좌표에서도 꺼지지 않는다
        for (r in listOf(500.0, 5000.0, 10000.0)) for (cv in listOf(true, false)) {
            val res = TaperCalc.calc(b.copy(arcR = r, arcConvex = cv))
            assertTrue(res.solution != null || res.warnings.size == 1, "R $r")
            res.solution?.let { s -> assertTrue((s.ncG40 + s.ncG41G42).none { "NaN" in it || "Infinity" in it }) }
        }
    }

    @Test
    fun arcMinRMatches() {
        assertEquals(5.0, TaperCalc.arcMinR(40.0, 0.0, 50.0, -5.0)!!, 1e-9)
        assertEquals(26.0, TaperCalc.arcMinR(40.0, 0.0, 44.0, -10.0)!!, 1e-9)
        assertEquals(7.25, TaperCalc.arcMinR(40.0, 0.0, 50.0, -2.0)!!, 1e-9)
        // 점 순서 · X 오르내림과 상관없다
        assertEquals(7.25, TaperCalc.arcMinR(50.0, -2.0, 40.0, 0.0)!!, 1e-9)
        assertEquals(7.25, TaperCalc.arcMinR(40.0, 0.0, 30.0, -2.0)!!, 1e-9)
        assertNull(TaperCalc.arcMinR(40.0, 0.0, 40.0, -5.0))
        assertNull(TaperCalc.arcMinR(40.0, 0.0, 50.0, 0.0))
        assertNull(TaperCalc.arcMinR(40.0, 0.0, 40.0, 0.0))
        assertNull(TaperCalc.arcMinR(Double.NaN, 0.0, 40.0, -3.0))
        for (s0 in shapes().filter { it.arcR == ceil3(minR(it)) }) {
            val m = minR(s0)
            ok(TaperCalc.calc(s0.copy(arcR = m)), "$s0")
            stops(TaperWarning.ARC_R_TOO_SMALL, TaperCalc.calc(s0.copy(arcR = m * 0.995)), "$s0")
        }
        // CAD 값을 0.001 로 반올림해 접하던 호가 아주 조금 넘는 것은 받는다(X50 이 50.01 — 넘은 깊이 2.5e-6)
        ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.01, z2 = -5.0, arc = true, arcR = 5.0)))
        ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.01, z2 = -5.0, arc = true, arcR = 5.0, arcConvex = false)))
    }

    // ------------------------------------------------------------ G41/G42 글을 시뮬레이터로

    /** 글의 X · Z 를 모달로 읽어 줄마다 끝 명령점(반경) */
    private fun ncPoints(nc: List<String>): List<Pt> {
        var z = 0.0
        var x = 0.0
        return nc.map { line ->
            for (w in ncWords(line)) when (w[0]) {
                'X' -> w.drop(1).toDoubleOrNull()?.let { x = it / 2 }
                'Z' -> w.drop(1).toDoubleOrNull()?.let { z = it }
            }
            Pt(z, x)
        }
    }

    /** G41/G42 글을 돌려 첫 깎는 줄(F.1) ~ 첫 빼는 줄(F.3)의 노즈 길. 멈추면 실패 */
    private fun simCut(s: TaperSolution, r: Double, tip: Int, form: CompStartup, tag: String): List<Seg> {
        val nc = s.ncG41G42
        val run = GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, tip))))
        assertNull(run.stop, "시뮬레이터 멈춤 ${run.stop} 형식 $form 팁 $tip $tag\n${nc.joinToString("\n")}")
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = nc.indexOfFirst { it.contains("F.3") }
        assertTrue(iCut > 0 && iExit > iCut, "회사 꼴이 아님 $nc")
        val lines = (pre.size + iCut + 1)..(pre.size + iExit + 1)
        return run.steps.filterIsInstance<MoveStep>().filter { it.line in lines && it.kind == MoveKind.FEED }.map { it.nose }
    }

    /**
     * G41/G42 글(도면 좌표)을 시뮬레이터(형식 C · A/B, 팁 넷)로 돌린 노즈 길:
     * - 멈춤(간섭 알람) 0
     * - 파고듦 0: 깎는 줄 · 첫 빼는 줄의 노즈가 도면에서 r 보다 가깝지 않다(0.001 반올림까지)
     * - 덜 깎임 0: T 점 사이 요소(T1–T2 호 · 모서리 R) 위의 점마다 남는 살이 직접 보정(노즈 중심 길)보다 많지 않고, 호의 가운데 1/3 은 남는 살이 없다
     * 바깥으로 꼭 90° 꺾이는 꼴(가장 작은 R 의 오목 호가 볼록 꼭짓점에 직각으로 닿음)은 시뮬레이터가 소수 잡음으로 '90° 넘음' 으로 멈출 수 있어
     * 가장 작은 R 은 여기서 빼고 [com.bmt.nosecalc.calc.verify.CompanyFrameVerifyTest] 가 따로 센다
     */
    @Test
    fun machineProgramCutsTheDrawing() {
        var n = 0
        var worstLeft = 0.0
        var worstGouge = 0.0
        for (s0 in shapes().filter { it.arcR != ceil3(minR(it)) }) for ((r1, r2) in listOf(0.0 to 0.0, 0.5 to 0.9)) for (legs in listOf(false, true)) {
            for (flip in listOf(false, true)) for (r in listOf(0.4, 0.8)) {
                val inp = s0.copy(noseR = r, cornerR1 = r1, cornerR2 = r2).let { if (legs) it.withLegs() else it }.let { if (flip) it.flipped() else it }
                val tag = inp.toString()
                val res = TaperCalc.calc(inp)
                val s = res.solution ?: continue
                // 제어기가 도면 자체에서 알람을 낼 꼴 · 짧은 막힌 끝을 뺀 꼴은 따로(회사 틀 검증)
                if (res.warnings.any { it in listOf(TaperWarning.CONCAVE_R_LE_NOSE, TaperWarning.NOSE_GT_SLOPE, TaperWarning.SHORT_END_PAST, TaperWarning.SHORT_END_SKIPPED) }) continue
                val d = s.drawing.segs
                val from = if (s.fromStart) 0 else 1
                val to = if (s.toEnd) d.size else d.size - 1
                val direct = s.center.segs.subList(from, to)
                val arc = d[s.arc!!.index] as Arc
                val arcMid = samples(arc).let { it.subList(it.size / 3, it.size - it.size / 3) }
                for (form in listOf(CompStartup.C, CompStartup.AB)) for (tip in listOf(s.tip, 8)) {
                    val cut = simCut(s, r, tip, form, tag)
                    for (c in cut) for (p in samples(c, 0.1)) {
                        val g = r - d.minOf { distTo(it, p) }
                        worstGouge = max(worstGouge, g)
                        assertTrue(g < 2e-3, "파고듦 $g $p 형식 $form 팁 $tip $tag\n${s.ncG41G42.joinToString("\n")}")
                    }
                    for (k in 1 until d.size - 1) for (q in samples(d[k], 0.1)) {
                        val left = cut.minOf { distTo(it, q) } - r
                        val leftDirect = max(0.0, direct.minOf { distTo(it, q) } - r)
                        worstLeft = max(worstLeft, left - leftDirect)
                        assertTrue(left < leftDirect + 2e-3, "덜 깎임 $left (직접 보정 $leftDirect) 요소 $k $q 형식 $form 팁 $tip $tag\n${s.ncG41G42.joinToString("\n")}")
                    }
                    for (q in arcMid) assertTrue(cut.minOf { distTo(it, q) } - r < 2e-3, "호 가운데 덜 깎임 $q $tag")
                    n++
                }
            }
        }
        println("machineProgramCutsTheDrawing: $n 번, 덜 깎임 최대 $worstLeft 파고듦 최대 $worstGouge")
        assertTrue(n > 3000, "$n")
    }

    /** 짧은 막힌 끝 · 시작(척 쪽 · 구멍 바닥 쪽 요소가 노즈R 보다 짧다)에서 G41/G42 글이 T1–T2 호를 빼지 않는다 */
    @Test
    fun shortEndNeverDropsArc() {
        var n = 0
        var short = 0
        // 끝 요소 길이를 잘게 훑는다 — 너무 짧으면 BAD_LEG, 그 바로 위 0.05 mm 쯤이 '짧은 막힌 끝'
        for (inner in listOf(false, true)) for (convex in listOf(true, false)) for (r in listOf(0.4, 0.8, 1.2)) for (leg in (5..65).map { it * 0.02 }) {
            for (rc in listOf(0.0, 0.3)) for (flip in listOf(false, true)) {
                // 형상 1(단면 → 호 → 수평): 수평 끝이 척 · 구멍 바닥 쪽. 뒤집으면 그 짧은 요소가 시작
                val b = base(inner, 1, 5.0, 10.0).copy(noseR = r, arc = true, arcR = 9.0, arcConvex = convex, end = -15.0 - leg, cornerR2 = rc)
                val inp = if (flip) b.flipped() else b
                val res = TaperCalc.calc(inp)
                // 끝 요소가 노즈보다 한참 짧으면 계산기가 BAD_LEG 로 멈춘다(직선 테이퍼와 같다)
                val s = res.solution ?: continue
                assertTrue(s.ncG41G42.count { "R9." in it } == 1, "호 줄이 없다 $inp\n${s.ncG41G42}")
                assertTrue(s.ncG40.count { "R" in it } >= 1, "$inp")
                n++
                if (res.warnings.any { it == TaperWarning.SHORT_END_PAST || it == TaperWarning.SHORT_END_SKIPPED }) short++
            }
        }
        assertTrue(n > 20 && short > 5, "푼 것 $n 짧은 막힌 끝 $short")
    }

    /**
     * 노즈R 보다 짧은 호를 접선으로 들어가(시작 비움 · 볼록 꺾임) 짧은 막힌 끝에서 뺄 때: 호의 노즈 길은 T1 의 법선에서 시작한다(직접 보정의 늘인 교점이 아니라).
     * 그 앞 늘인 토막 위의 교점을 '뺄 자리' 로 보면 호 블록이 거꾸로 가 간섭 알람이었다(무작위 검증 2026-11-12)
     */
    @Test
    fun shortArcBeforeShortWallHasNoAlarm() {
        val inp = TaperInput(
            inner = true, type = 1, reverse = true, noseR = 1.0, x1 = 74.988, z1 = -3.959, x2 = 75.127, z2 = -3.404, start = -4.845, flip = true,
            arc = true, arcR = 3.037, arcConvex = false,
        )
        val res = TaperCalc.calc(inp)
        val s = ok(res)
        assertTrue(TaperWarning.SHORT_END_PAST in res.warnings, "${res.warnings}")
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 1..9) simCut(s, 1.0, tip, form, "$inp")
    }

    /**
     * 짧은 막힌 시작 · 끝을 빼고 벽을 따라 직각으로 들어가거나 빼는 줄이 가장 작은 R(또는 NC 반올림 띠)의 호 접선과 꼭 반대(되돌아감)거나 바깥으로 꺾이면
     * 제어기가 바깥 꺾임으로 돌아 호를 판다 — 그때는 빼지 않고 곧게(SHORT_END_PAST). 검증 2026-11-12 재현 둘(① 화면 역방향 = Z 거울 · approach = false,
     * ② 반올림으로 179.9993° 바깥) + 같은 꼴(외경 · 내경 형상 3 · 4 역방향, 짧은 시작 0.3, R = 가장 작은 R ~ +0.004) — 뒤집어 짧은 요소를 끝으로도
     */
    @Test
    fun shortStartNeverTurnsBack() {
        val cases = mutableListOf(
            TaperInput(false, 3, noseR = 0.2, x1 = 30.0, z1 = 7.0, x2 = 40.0, z2 = 2.0, start = 7.3, approach = false, arc = true, arcR = 5.0),
            TaperInput(
                true, 5, noseR = 0.2, tip = 4, x1 = 72.096, z1 = -7.086, x2 = 51.916, z2 = -2.0, zClear = 2.0, cornerR2 = 1.0, start = -7.359, end = -1.128,
                flip = true, arc = true, arcR = 12.552,
            ),
        )
        for (inner in listOf(false, true)) for (type in 3..4) for (r in listOf(0.2, 0.4)) for (up in listOf(0.0, 0.001, 0.002, 0.004)) for (cr2 in listOf(0.0, 0.5)) {
            val b = TaperInput(
                inner, type, noseR = r, x1 = if (inner) 40.0 else 30.0, z1 = 7.0, x2 = if (inner) 30.0 else 40.0, z2 = 2.0, start = 7.3, approach = false,
                arc = true, cornerR2 = cr2,
            )
            cases += b.copy(arcR = ceil3(minR(b)) + up)
        }
        // Z 거울(approach = false)로 푼 글은 화면처럼 되돌려(Z 부호 · G2↔G3 · G41↔G42) 돌린다 — 노즈 길만 보므로 팁은 9(노즈 중심)
        val swap = mapOf("G2" to "G3", "G3" to "G2", "G41" to "G42", "G42" to "G41")
        fun unmirror(line: String) = ncWords(line).joinToString("") { w ->
            when {
                w in swap -> swap.getValue(w)
                !w.startsWith("Z") -> w
                w.startsWith("Z-") -> "Z" + w.substring(2)
                else -> "Z-" + w.substring(1)
            }
        }
        var short = 0
        for (inp in cases + cases.map { it.flipped() }) {
            val res = TaperCalc.calc(inp)
            val s0 = res.solution ?: continue
            val tag = "$inp ${res.warnings}"
            if (inp.arcR == 5.0 && inp.type == 3 && !inp.inner && inp.noseR == 0.2 && inp.cornerR2 == 0.0 && !inp.flip) {
                assertTrue(TaperWarning.SHORT_END_SKIPPED !in res.warnings && TaperWarning.SHORT_END_PAST in res.warnings, tag)
            }
            if (res.warnings.none { it == TaperWarning.SHORT_END_SKIPPED || it == TaperWarning.SHORT_END_PAST }) continue
            short++
            val s = if (inp.approach) s0 else s0.copy(ncG41G42 = s0.ncG41G42.map(::unmirror))
            for (form in listOf(CompStartup.C, CompStartup.AB)) simCut(s, inp.noseR, if (inp.approach) s.tip else 9, form, tag)
        }
        assertTrue(short > 20, "$short")
    }

    /**
     * T1–T2 호가 그 끝에서 앞뒤 요소에 접하면(가장 작은 R — 45° 현이면 두 끝 다) 꺾인 모서리가 없어 그 끝의 모서리 R 은 끼우지 않는다. 받는 띠(가장 작은 R 보다
     * 조금 작은 R)에서 호가 아주 조금 넘어 거꾸로 꺾여도 같다. 전에는 CORNER_R_TOO_BIG('모서리 R 을 줄이세요' — 어떤 R 도 안 됨)이거나 거꾸로 꺾인
     * 0.001 mm 필렛 줄(`G2X30.001R.5`)이 끼어 노즈가 못 도는 오목 R 이 됐다(검증 2026-11-12)
     */
    @Test
    fun cornerRAtTangentEndIsSkipped() {
        val b = TaperInput(false, 1, noseR = 0.4, x1 = 30.0, z1 = -2.0, x2 = 40.0, z2 = -7.0, arc = true, arcR = 5.0)
        val both = b.copy(cornerR1 = 0.5, cornerR2 = 0.5)
        for (inp in listOf(b.copy(cornerR1 = 0.5), b.copy(cornerR2 = 0.5), both, both.copy(arcR = 5.001), both.copy(arcR = 4.993, noseR = 0.8), both.flipped())) {
            val tag = "$inp"
            val res = TaperCalc.calc(inp)
            val s = ok(res, tag)
            assertEquals(3, s.drawing.segs.size, "필렛이 끼었다 $tag ${s.drawing.segs}")
            assertTrue(s.points.all { it.corner == null }, tag)
            val a = s.drawing.segs[s.arc!!.index] as Arc
            assertTrue((a.start - Pt.ofDia(-2.0, 30.0)).length + (a.end - Pt.ofDia(-7.0, 40.0)).length < 1e-9 || inp.flip, tag)
            assertTrue(TaperWarning.CONCAVE_R_LE_NOSE !in res.warnings, "${res.warnings} $tag")
            // 원호 줄은 T1–T2 호뿐(90° 를 넘는 호는 `NcLines` 가 두 줄로 — 받는 띠의 R4.993 은 90.16°)
            val radii = (s.ncG40 + s.ncG41G42).mapNotNull { l -> ncWords(l).firstOrNull { it.startsWith("R") } }.toSet()
            assertEquals(2, radii.size, "$tag\n${s.ncG40}\n${s.ncG41G42}")
            for (form in listOf(CompStartup.C, CompStartup.AB)) simCut(s, inp.noseR, s.tip, form, tag)
        }
        // 넉넉한 형상 × 가장 작은 R(+0 ~ 0.002) × 모서리 R 0.5 한쪽 · 양쪽: 접하는 끝 때문에 멈추지 않는다
        for (s0 in shapes().filter { it.arcR == ceil3(minR(it)) }) for (up in listOf(0.0, 0.001, 0.002)) for ((r1, r2) in listOf(0.5 to 0.0, 0.0 to 0.5, 0.5 to 0.5)) {
            val inp = s0.copy(arcR = s0.arcR!! + up, cornerR1 = r1, cornerR2 = r2)
            val res = TaperCalc.calc(inp)
            assertTrue(TaperWarning.CORNER_R_TOO_BIG !in res.warnings, "$inp")
        }
    }

    // ------------------------------------------------------------ 손 계산(설계B — 기하에서 따로 계산한 예제)

    private class Ex(
        val id: Int, val input: TaperInput, val side: Side, val tip: Int, val g: String, val cx: Double, val cz: Double, val progR: Double,
        /** T 점(가공 순서)마다 꼭짓점 하나 또는 모서리 R 시작 · 끝: [도면 X, 도면 Z, 보정 X, 보정 Z] */
        val pts: List<List<Double>>, val g40: List<String>, val g4x: List<String>,
        /** 직접 보정 진입 · G41/G42 진입점 · 직접 보정 후퇴 · G41/G42 첫 빼는 줄 끝 [X, Z] — 계산한 것만 */
        val directEntry: Pair<Double, Double>? = null, val machineEntry: Pair<Double, Double>? = null,
        val directExit: Pair<Double, Double>? = null, val machineExit: Pair<Double, Double>? = null,
    )

    /** 그림 오른쪽 점(xR, zR) · 왼쪽 점(xL, zL), rhoR · rhoL = 그 점의 모서리 R, front · back = 그림 오른쪽 · 왼쪽 요소의 끝 좌표 */
    private fun exIn(
        inner: Boolean, type: Int, xR: Double, zR: Double, xL: Double, zL: Double, arcR: Double, convex: Boolean, r: Double,
        rhoR: Double = 0.0, rhoL: Double = 0.0, front: Double? = null, back: Double? = null, flip: Boolean = false,
    ): TaperInput {
        val i = TaperInput(
            inner, type, noseR = r, x1 = xR, z1 = zR, x2 = xL, z2 = zL, cornerR1 = rhoR, cornerR2 = rhoL, start = front, end = back,
            arc = true, arcR = arcR, arcConvex = convex,
        )
        return if (flip) i.flipped() else i
    }

    private val examples = listOf(
        // 1 외경 볼록 · 형상 1
        Ex(
            1, exIn(false, 1, 40.0, 0.0, 50.0, -5.0, 10.0, true, 0.4), Side.G42, 3, "G03", 31.771, -9.114, 10.4,
            listOf(listOf(40.0, 0.0, 39.37, 0.0), listOf(50.0, -5.0, 50.0, -5.315)),
            listOf("G1X39.37Z0.", "G3X50.Z-5.315R10.4"), listOf("G1X40.Z0.", "G3X50.Z-5.R10."),
            37.37 to 0.441, 38.0 to 0.451, 52.0 to -5.315, 50.903 to -6.0,
        ),
        // 2 외경 오목 · 형상 3
        Ex(
            2, exIn(false, 3, 30.0, -10.0, 50.0, -20.0, 12.0, false, 0.4), Side.G42, 3, "G02", 53.711, -8.144, 11.6,
            listOf(listOf(30.0, -10.0, 30.0, -10.369), listOf(50.0, -20.0, 50.0, -20.053)),
            listOf("G1X30.Z-10.369", "G2X50.Z-20.053R11.6"), listOf("G1X30.Z-10.", "G2X50.Z-20.R12."),
            30.0 to -9.369, 30.0 to -9.0, 52.0 to -20.053, 52.0 to -20.157,
        ),
        // 3 내경 볼록 · 형상 1
        Ex(
            3, exIn(true, 1, 40.0, 0.0, 34.0, -3.0, 6.0, true, 0.4), Side.G41, 2, "G02", 44.937, -5.469, 6.4,
            listOf(listOf(40.0, 0.0, 40.631, 0.0), listOf(34.0, -3.0, 34.0, -3.315)),
            listOf("G1X40.631Z0.", "G2X34.Z-3.315R6.4"), listOf("G1X40.Z0.", "G2X34.Z-3.R6."),
            42.631 to 0.435, 42.0 to 0.451, 32.0 to -3.315, 33.097 to -4.0,
        ),
        // 4 단면 쪽(가파른 현) 볼록 · 외경 형상 2 · 노즈 0.8
        Ex(
            4, exIn(false, 2, 20.0, 0.0, 60.0, -3.0, 80.0, true, 0.8), Side.G42, 3, "G03", 16.456, -79.98, 80.8,
            listOf(listOf(20.0, 0.0, 18.418, 0.0), listOf(60.0, -3.0, 58.621, -3.0)),
            listOf("G1X18.418Z0.", "G3X58.621Z-3.R80.8"), listOf("G1X20.Z0.", "G3X60.Z-3.R80."),
            16.418 to 0.022, 18.0 to 0.022, 58.621 to -2.0, 62.0 to -3.0,
        ),
        // 5 외경 볼록 + 모서리 R 1 · 2 + 시작 X30 · 끝 Z-12
        Ex(
            5, exIn(false, 1, 40.0, 0.0, 50.0, -5.0, 10.0, true, 0.4, rhoR = 1.0, rhoL = 2.0, front = 30.0, back = -12.0), Side.G42, 3, "G03", 31.771, -9.114, 10.4,
            listOf(
                listOf(39.558, 0.0, 38.758, 0.0, 40.423, -0.098, 39.969, -0.138),
                listOf(49.557, -4.541, 49.469, -4.758, 50.0, -5.456, 50.0, -5.856),
            ),
            listOf("G1X30.Z0.", "X38.758", "G3X39.969Z-.138R1.4", "X49.469Z-4.758R10.4", "X50.Z-5.856R2.4", "G1Z-12."),
            listOf("G1X30.Z0.", "X39.558", "G3X40.423Z-.098R1.", "X49.557Z-4.541R10.", "X50.Z-5.456R2.", "G1Z-12."),
        ),
        // 6 외경 오목 뒤집기 + 모서리 R
        Ex(
            6, exIn(false, 3, 30.0, -10.0, 50.0, -20.0, 12.0, false, 0.4, rhoR = 1.5, rhoL = 1.0, flip = true), Side.G41, 3, "G03", 53.711, -8.144, 11.6,
            listOf(
                listOf(50.0, -20.827, 50.0, -21.227, 48.439, -19.851, 47.815, -19.861),
                listOf(30.041, -10.127, 30.03, -10.461, 30.0, -9.879, 30.0, -10.279),
            ),
            listOf("G1X50.Z-21.227", "G2X47.815Z-19.861R1.4", "G3X30.03Z-10.461R11.6", "X30.Z-10.279R1.1"),
            listOf("G1X50.Z-20.827", "G2X48.439Z-19.851R1.", "G3X30.041Z-10.127R12.", "X30.Z-9.879R1.5"),
            50.0 to -22.227, 50.0 to -21.827, 32.0 to -10.279, 30.0 to -8.879,
        ),
        // 7 내경 오목 뒤집기 + 모서리 R(T1 만)
        Ex(
            7, exIn(true, 3, 50.0, -10.0, 30.0, -20.0, 12.0, false, 0.4, rhoL = 0.5, flip = true), Side.G42, 2, "G02", 26.289, -8.144, 11.6,
            listOf(listOf(30.0, -20.42, 30.0, -20.82, 30.812, -19.929, 31.461, -19.937), listOf(50.0, -10.0, 50.0, -10.369)),
            listOf("G1X30.Z-20.82", "G3X31.461Z-19.937R.9", "G2X50.Z-10.369R11.6"),
            listOf("G1X30.Z-20.42", "G3X30.812Z-19.929R.5", "G2X50.Z-10.R12."),
            30.0 to -21.82, 30.0 to -21.42, 48.0 to -10.369, 50.0 to -9.0,
        ),
        // 8 외경 오목 · 가장 작은 R(T1 에서 앞 요소에 접함 — 그 모서리 R 은 무시, T2 는 바깥 90°)
        Ex(
            8, exIn(false, 3, 30.0, -10.0, 50.0, -20.0, 10.0, false, 0.4, rhoR = 1.0), Side.G42, 3, "G02", 50.0, -10.0, 9.6,
            listOf(listOf(30.0, -10.0, 30.0, -10.4), listOf(50.0, -20.0, 50.0, -19.992)),
            listOf("G1X30.Z-10.4", "G2X35.909Z-17.328R9.6", "X50.Z-19.992R9.6"), listOf("G1X30.Z-10.", "G2X50.Z-20.R10."),
            30.0 to -9.4, 30.0 to -9.0, 52.0 to -19.992, 52.0 to -20.0,
        ),
        // 9 내경 볼록 + 모서리 R · 노즈 0.8
        Ex(
            9, exIn(true, 1, 40.0, 0.0, 34.0, -3.0, 6.0, true, 0.8, rhoR = 0.5, rhoL = 0.5), Side.G41, 2, "G02", 44.937, -5.469, 6.8,
            listOf(
                listOf(40.22, 0.0, 41.82, 0.0, 39.791, -0.048, 40.705, -0.126),
                listOf(34.097, -2.896, 34.251, -3.353, 34.0, -3.11, 34.0, -3.91),
            ),
            listOf("G1X41.82Z0.", "G2X40.705Z-.126R1.3", "X34.251Z-3.353R6.8", "X34.Z-3.91R1.3"),
            listOf("G1X40.22Z0.", "G2X39.791Z-.048R.5", "X34.097Z-2.896R6.", "X34.Z-3.11R.5"),
            41.82 to 1.0, 42.22 to 0.0, 32.0 to -3.91, 34.0 to -4.11,
        ),
        // 10 외경 역테이퍼(형상 5) 볼록 뒤집기
        Ex(
            10, exIn(false, 5, 50.0, -10.0, 40.0, -18.0, 15.0, true, 0.4, flip = true), Side.G41, 3, "G02", 20.851, -6.453, 15.4,
            listOf(listOf(40.0, -18.0, 40.0, -18.586), listOf(50.0, -10.0, 50.0, -10.448)),
            listOf("G1X40.Z-18.586", "G2X50.Z-10.448R15.4"), listOf("G1X40.Z-18.", "G2X50.Z-10.R15."),
            40.0 to -19.586, 40.0 to -19.0, 52.0 to -10.448, 50.487 to -9.0,
        ),
        // 11 내경 오목 · 형상 3
        Ex(
            11, exIn(true, 3, 50.0, -10.0, 30.0, -20.0, 12.0, false, 0.4), Side.G41, 2, "G03", 26.289, -8.144, 11.6,
            listOf(listOf(50.0, -10.0, 50.0, -10.369), listOf(30.0, -20.0, 30.0, -20.053)),
            listOf("G1X50.Z-10.369", "G3X30.Z-20.053R11.6"), listOf("G1X50.Z-10.", "G3X30.Z-20.R12."),
            50.0 to -9.369, 50.0 to -9.0, 28.0 to -20.053, 28.0 to -20.157,
        ),
        // 12 외경 오목 · 형상 4 + T2 모서리 R + 끝 X60
        Ex(
            12, exIn(false, 4, 30.0, -10.0, 44.0, -18.0, 9.0, false, 0.4, rhoL = 1.0, back = 60.0), Side.G42, 3, "G02", 47.932, -9.217, 8.6,
            listOf(listOf(30.0, -10.0, 30.0, -10.382), listOf(43.765, -17.973, 43.15, -17.984, 44.228, -18.0, 43.428, -18.0)),
            listOf("G1X30.Z-10.382", "G2X43.15Z-17.984R8.6", "X43.428Z-18.R.6", "G1X60."),
            listOf("G1X30.Z-10.", "G2X43.765Z-17.973R9.", "X44.228Z-18.R1.", "G1X60."),
            30.0 to -9.382, 30.0 to -9.0,
        ),
    )

    /** 글의 이동 줄을 모달을 풀어 "G1 X30. Z-10.369 R…" 꼴로(이송 F 는 뺀다) */
    private fun spelled(nc: List<String>): List<String> {
        var g = ""
        var x = ""
        var z = ""
        return nc.map { line ->
            var rr = ""
            for (w in ncWords(line)) when {
                w in listOf("G0", "G1", "G2", "G3") -> g = w
                w[0] == 'X' -> x = w
                w[0] == 'Z' -> z = w
                w[0] == 'R' -> rr = " $w"
            }
            "$g $x $z$rr"
        }
    }

    /** 가장 작은 R 을 0.001 로 올려 넣으면 호가 앞뒤 요소에 거의 접해서(1e-5 rad 쯤 꺾여) 이어진다 — 그 꼴도 풀린다(화면이 그 값을 '최소 R' 로 알려 준다) */
    @Test
    fun nearTangentJoinSolves() {
        var n = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (dz in listOf(1.0, 3.0, 4.7, 8.0, 12.3)) for (dx in listOf(1.5, 6.0, 9.4, 20.0)) {
            for (convex in listOf(true, false)) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (up in listOf(0.0, 0.001, 0.002, 0.01)) {
                val b = base(inner, type, dz, dx)
                val inp = b.copy(noseR = r, arc = true, arcR = ceil3(minR(b)) + up, arcConvex = convex)
                val res = TaperCalc.calc(inp)
                // 오목 호가 볼록 꼭짓점에 닿는 꼴은 R 이 노즈R 의 두 배쯤보다 작으면 노즈가 못 돈다(알려진 한계)
                if (res.solution == null && !convex && inp.arcR!! < 2.5 * r) continue
                ok(res, "$inp")
                n++
            }
        }
        assertTrue(n > 5000, "$n")
    }

    /** 셋째 자리로 적은 손 계산과 같은가(반올림 반 자리) */
    private fun same3(want: Double, got: Double) = abs(want - got) <= 0.00051

    /**
     * 설계B 의 수치 예제 12개(앱 수식을 보지 않고 기하에서 계산 — 임시 스크립트 arc_examples.py, 값은 그 출력 그대로 셋째 자리):
     * 보정 쪽 · 팁 · G02/G03 · 호 중심 · 보정 R · T 점(모서리 R 접점)의 도면 · 직접 보정 좌표 · 두 프로그램의 형상 줄, 그리고 진입 · 후퇴 점
     */
    @Test
    fun handNumbers() {
        for (e in examples) {
            val tag = "예제 ${e.id}"
            val res = TaperCalc.calc(e.input)
            val s = ok(res, tag)
            assertEquals(e.side, s.side, tag)
            assertEquals(e.tip, s.tip, tag)
            val a = assertNotNull(s.arc, tag)
            assertEquals(e.g, if (a.cw) "G02" else "G03", tag)
            assertTrue(same3(e.cx, a.center.xDia) && same3(e.cz, a.center.z), "$tag 중심 X${a.center.xDia} Z${a.center.z}")
            assertEquals(e.input.arcR!!, a.r, 1e-9, tag)
            assertEquals(e.progR, a.progR!!, 1e-9, tag)
            assertEquals(e.pts.size, s.points.size, tag)
            for ((want, p) in e.pts.zip(s.points)) {
                assertEquals(want.size / 4, p.nodes.size, "$tag 점 수 ${p.nodes}")
                for ((k, n) in p.nodes.withIndex()) {
                    val w = want.subList(4 * k, 4 * k + 4)
                    assertTrue(
                        same3(w[0], n.drawn.xDia) && same3(w[1], n.drawn.z) && same3(w[2], n.prog.xDia) && same3(w[3], n.prog.z),
                        "$tag 기대 $w 실제 도면 X${n.drawn.xDia} Z${n.drawn.z} 보정 X${n.prog.xDia} Z${n.prog.z}",
                    )
                }
            }
            // 직접 보정 형상 줄 = 첫 G1 부터 마지막 G0(후퇴) 앞까지. 앞줄과 같은 축 값 · G 를 빼는 표기는 풀어서(모달) 견준다
            val i40 = s.ncG40.indexOfFirst { it.startsWith("G1") }
            assertEquals(spelled(e.g40), spelled(s.ncG40).subList(i40, s.ncG40.size - 1), "$tag 직접 보정 ${s.ncG40}")
            // G41/G42 형상 줄 = 첫 깎는 줄(F.1)부터 첫 빼는 줄(F.3) 앞까지
            val nc = s.ncG41G42
            val iCut = nc.indexOfFirst { it.endsWith("F.1") }
            val iExit = nc.indexOfFirst { it.contains("F.3") }
            assertEquals(spelled(e.g4x), spelled(nc).subList(iCut, iExit), "$tag G41/G42 $nc")
            assertEquals(e.side.name, nc.first(), tag)
            val at = ncPoints(nc)
            fun near(want: Pair<Double, Double>, got: Pt, what: String) =
                assertTrue(same3(want.first, got.xDia) && same3(want.second, got.z), "$tag $what 기대 $want 실제 X${got.xDia} Z${got.z}\n${s.ncG40}\n$nc")
            e.directEntry?.let { near(it, s.entry, "직접 보정 진입") }
            e.directExit?.let { near(it, s.exit, "직접 보정 후퇴") }
            e.machineEntry?.let { near(it, at[iCut - 1], "G41/G42 진입점") }
            e.machineExit?.let { near(it, at[iExit], "G41/G42 첫 빼는 줄 끝") }
        }
    }

    /** 접선이 단면에 거의 나란한 호(가파른 현 + 큰 R)에서 비운 시작의 진입점이 유한하고 축을 넘지 않는다(Z 로 c 를 늘이면 X 가 발산한다) */
    @Test
    fun steepTangentEntryStaysNear() {
        for (inner in listOf(false, true)) for (cv in listOf(true, false)) {
            val up = if (inner) -1.0 else 1.0
            val inp = TaperInput(inner, 1, x1 = 30.0, z1 = 0.0, x2 = 30.0 + up * 16, z2 = -0.4, arc = true, arcR = 400.0, arcConvex = cv, zClear = 2.0)
            val res = TaperCalc.calc(inp)
            val s = ok(res, "$inp")
            assertTrue((s.entry - s.program.segs[0].end).length < 2.0 * sqrt(2.0) + 1e-9, "직접 보정 진입 ${s.entry} $inp")
            val at = ncPoints(s.ncG41G42)
            val iCut = s.ncG41G42.indexOfFirst { it.endsWith("F.1") }
            assertTrue((at[iCut - 1] - at[iCut]).length < 2.0 * sqrt(2.0) + 0.5, "G41/G42 진입점 ${at[iCut - 1]} $inp")
            assertTrue(TaperWarning.ENTRY_PAST_AXIS !in res.warnings, "$inp")
            assertTrue((s.ncG40 + s.ncG41G42).none { "NaN" in it || "Infinity" in it })
        }
    }
}
