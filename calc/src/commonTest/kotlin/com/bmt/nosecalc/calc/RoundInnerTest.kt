package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 내경 라운드 R: 외경 도면을 Z 로 뒤집은 구멍(팁 2 · G41), NC 줄 ↔ 요소(ncRefs · lineOf) */
class RoundInnerTest {
    private val noseRs = listOf(0.0, 0.2, 0.4, 0.8)

    private fun ok(r: RoundResult): RoundSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        return r.solution
    }

    private fun solved(r: RoundResult) = ok(r) to r.warnings

    private fun near(e: Pt, a: Pt, msg: String, tol: Double = 1e-9) =
        assertTrue((e - a).length <= tol, "$msg: 기대 $e, 실제 $a")

    /** RoundCalcTest 의 대표 치수 가운데 TYPE 2~6 */
    private val cases = listOf(
        RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 1.0),
        RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 0.0),
        RoundInput(type = 2, mainR = 6.0, d1 = 10.0, l1 = 3.0, cornerR1 = 1.5),
        RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0),
        RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 0.0),
        RoundInput(type = 3, mainR = 30.0, d1 = 20.0, cornerR1 = 3.0),
        RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0),
        RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 0.0),
        RoundInput(type = 4, mainR = 5.0, d1 = 4.0, cornerR1 = 1.5),
        RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 16.0, l1 = 6.0, cornerR1 = 1.0, cornerR2 = 2.0),
        RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 16.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0),
        RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 10.0, l1 = 4.0, cornerR1 = 1.2, cornerR2 = 1.5),
        RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 1.2, cornerR2 = 1.5),
        RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 0.0, cornerR2 = 0.0),
        // 중심이 플랜지 면 오른쪽: 돔이 꼭대기를 넘어 내려가 면에 닿는다
        RoundInput(type = 6, mainR = 15.0, d2 = 50.0, d1 = 10.0, l1 = 3.0, l2 = 20.0, cornerR1 = 1.2, cornerR2 = 1.2),
    )

    /** 원호 가운데 점(계산기와 따로) */
    private fun mid(a: Arc): Pt {
        val a0 = angleOf(a.start - a.center)
        val t = a0 + (if (a.cw) -1 else 1) * a.sweepRad() / 2
        return a.center + Pt(cos(t), sin(t)) * a.radius
    }

    /** 이 시험의 거울: 외경 도면의 끝 깊이 K 만큼 옮겨 Z 를 뒤집는다(외경 끝 → 구멍 입구 Z0). 요소는 거꾸로 */
    private fun mirrorOf(outer: List<Seg>): List<Pair<Pt, Pt>> {
        val k = -outer.last().end.z
        fun m(p: Pt) = Pt(-p.z - k, p.x)
        // 끝점 둘과 원호는 가운데 점 · 중심까지 — 방향(cw)을 따로 셈하지 않고 모양으로 맞춘다
        return outer.asReversed().flatMap { s ->
            listOf(m(s.end) to m(s.start)) + listOfNotNull((s as? Arc)?.let { m(mid(it)) to m(it.center) })
        }
    }

    private fun shape(segs: List<Seg>): List<Pair<Pt, Pt>> =
        segs.flatMap { s -> listOf(s.start to s.end) + listOfNotNull((s as? Arc)?.let { mid(it) to it.center }) }

    private fun samePairs(e: List<Pair<Pt, Pt>>, a: List<Pair<Pt, Pt>>, tag: String) {
        assertEquals(e.size, a.size, tag)
        for (k in e.indices) {
            near(e[k].first, a[k].first, "$tag $k", 1e-9)
            near(e[k].second, a[k].second, "$tag $k", 1e-9)
        }
    }

    /** 내경 3~6 = 외경 도면의 거울(입구 Z0), 2 = 외경에서 끝 면만 뺀 것. 채운 값은 같고 G41 · 팁 2 */
    @Test
    fun innerDrawingIsMirror() {
        for (base in cases) for (r in noseRs) {
            val tag = "TYPE ${base.type} r=$r $base"
            val o = ok(RoundCalc.calc(base.copy(noseR = r)))
            val i = ok(RoundCalc.calc(base.copy(noseR = r, inner = true)))
            val want = if (base.type == 2) shape(o.drawing.segs.drop(1)) else mirrorOf(o.drawing.segs)
            samePairs(want, shape(i.drawing.segs), tag)
            assertEquals(o.filled!!.copy(inner = true), i.filled, tag)
            assertEquals(Side.G41, i.side, tag)
            assertEquals(2, i.tip, tag)
        }
    }

    /**
     * 손 계산(내경 TYPE 3): Ø20 구멍, 입구 깊이 10, SR12.5, CR1 → L = 5, K = 15. 외경 모서리 R 중심 (−5.341089, 9) → (−9.658911, 9),
     * 구 접점 (−4.718576, 9.782609) → (−10.281424, 9.782609), 구 중심 (−12.5, 0) → (−2.5, 0). 내경에서 CR 은 오목(R − r = 0.6),
     * 구는 공구가 원 안쪽(R − r = 12.1). 진입: 첫 보정점 (−0.4, 10) 에서 Z+ 1.
     */
    @Test
    fun innerHandExampleType3() {
        val inp = RoundInput(type = 3, inner = true, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4, zClear = 1.0, legLen = 10.0)
        val (s, w) = solved(RoundCalc.calc(inp))
        assertTrue(abs(s.filled!!.l1!! - 5.0) <= 1e-12)
        val d = s.drawing.segs
        assertEquals(3, d.size)
        near(Pt(0.0, 10.0), d[0].start, "입구")
        near(Pt(-9.658911, 10.0), d[0].end, "구멍 끝", 1e-6)
        near(Pt(-9.658911, 9.0), (d[1] as Arc).center, "CR 중심", 1e-6)
        near(Pt(-10.281424, 9.782609), d[1].end, "구 접점", 1e-6)
        near(Pt(-2.5, 0.0), (d[2] as Arc).center, "구 중심")
        near(Pt(-15.0, 0.0), d[2].end, "바닥")
        assertEquals(listOf("G00 X20. Z0.6", "G01 Z-0.4", "G01 Z-10.059", "G03 X19.739 Z-10.432 R0.6", "G03 X0.8 Z-15. R12.1"), s.nc)
        assertEquals(
            listOf("G00 X20. Z1.", "G01 Z0.", "G01 Z-9.659", "G03 X19.565 Z-10.281 R1.", "G03 X0. Z-15. R12.5"),
            ok(RoundCalc.calc(inp.copy(noseR = 0.0))).nc,
        )
        assertTrue(w.isEmpty(), "$w")
        assertEquals(listOf(NcRef(-2, 0, 1), NcRef(-1, 0, 1), NcRef(0, 0, 1), NcRef(1, 0, 1), NcRef(2, 0, 1)), s.ncRefs)
        assertEquals(listOf("G00 X20. Z0.", "G01 Z-0.4"), ok(RoundCalc.calc(inp.copy(zClear = 0.0))).nc.take(2))
    }

    /**
     * 내경 TYPE 5 막힘: 입구 Ø40 깊이 5 → 볼록 구면(공구에게는 오목 R25) → Ø30 → 바닥 면을 중심까지.
     * 관통이면 바닥 면이 없고 Ø30 끝까지 노즈가 간다(인선 Z−16.4, 외경 끝 원통과 같다)
     */
    @Test
    fun innerType5Blind() {
        val inp = RoundInput(
            type = 5, inner = true, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, noseR = 0.4, legLen = 5.0,
        )
        val (s, w) = solved(RoundCalc.calc(inp))
        assertTrue(abs(s.filled!!.l2!! - 5.0) <= 1e-12)
        val nc = listOf("G00 X40. Z0.6", "G01 Z-0.4", "G01 Z-5.266", "G03 X30. Z-10.199 R24.6", "G01 Z-16.", "G01 X0.8")
        assertEquals(nc, s.nc)
        assertEquals(listOf("G00 X40. Z1.", "G01 Z0.", "G01 Z-5.", "G03 X30. Z-10. R25.", "G01 Z-16.", "G01 X0."), ok(RoundCalc.calc(inp.copy(noseR = 0.0))).nc)
        assertTrue(w.isEmpty(), "$w")
        val t = ok(RoundCalc.calc(inp.copy(through = true)))
        assertEquals(3, t.drawing.segs.size)
        val last = t.drawing.segs.last()
        assertTrue(last is Line)
        near(Pt(-16.0, 15.0), last.end, "관통 끝")
        assertTrue(abs(last.start.x - 15.0) <= 1e-12)
        assertEquals(nc.dropLast(2) + "G01 Z-16.4", t.nc)
    }

    /**
     * 내경 TYPE 4(공 모양 소켓 — 언더컷): 입구 Ø10 깊이 5, SR8, CR1(내경에서는 볼록). 공은 138° 라 NC 두 줄.
     * 공의 입구 쪽 절반은 바이트 반대쪽이다.
     */
    @Test
    fun innerType4Socket() {
        val inp = RoundInput(type = 4, inner = true, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, noseR = 0.4, legLen = 5.0)
        val (s, w) = solved(RoundCalc.calc(inp))
        assertEquals(
            listOf("G00 X10. Z0.6", "G01 Z-0.4", "G01 Z-4.937", "G02 X10.933 Z-5.98 R1.4", "G03 X14.999 Z-14.357 R7.6", "G03 X0.8 Z-19.245 R7.6"),
            s.nc,
        )
        assertEquals(
            listOf("G01 Z-4.537", "G02 X10.667 Z-5.282 R1.", "G03 X14.947 Z-14.1 R8.", "G03 X0. Z-19.245 R8."),
            ok(RoundCalc.calc(inp.copy(noseR = 0.0))).nc.drop(2),
        )
        assertTrue(RoundWarning.AGAINST_TOOL in w, "$w")
        assertEquals(listOf(NcRef(2, 0, 2), NcRef(2, 1, 2)), s.ncRefs.filter { it.element == 2 })
        val ball = s.program.segs[2] as Arc
        fun at(f: Double): Pt {
            val a0 = angleOf(ball.start - ball.center)
            val t = a0 + (if (ball.cw) -1 else 1) * ball.sweepRad() * f
            return ball.center + Pt(cos(t), sin(t)) * ball.radius
        }
        assertEquals(4, s.lineOf(2, at(0.25)))
        assertEquals(5, s.lineOf(2, at(0.75)))
        assertEquals(4, s.lineOf(2, ball.start))
        assertEquals(5, s.lineOf(2, ball.end))
    }

    /** 같은 원호가 외경에서 볼록이면 내경에서는 오목(또는 거꾸로): 보정 R − R 의 부호가 바뀐다 */
    @Test
    fun convexityFlips() {
        for (base in cases) for (r in noseRs) {
            val o = ok(RoundCalc.calc(base.copy(noseR = r)))
            val i = ok(RoundCalc.calc(base.copy(noseR = r, inner = true)))
            val n = o.drawing.segs.size
            for (ia in i.arcs) {
                val oa = o.arcs.single { it.index == if (base.type == 2) ia.index + 1 else n - 1 - ia.index }
                assertTrue(abs(ia.r - oa.r) <= 1e-12)
                if (ia.progR == null || oa.progR == null) continue
                assertTrue(abs((ia.progR - ia.r) + (oa.progR - oa.r)) <= 1e-9, "TYPE ${base.type} r=$r 원호 ${ia.index}: ${ia.progR} / ${oa.progR}")
            }
        }
    }

    /**
     * 바이트 반대쪽(AGAINST_TOOL)은 내경(팁 2)과 외경(팁 3)이 같다: 거울 Mz 로 접점 방향 n → (−nz, nx) 와 (−nz, −nx),
     * 팁 벡터와의 내적이 둘 다 (nz + nx)/√2. 내경 3 · 5 는 없음, 2 · 4 는 늘, 6 은 꼭대기를 넘는 돔만. 관통도 같다
     */
    @Test
    fun againstToolSameAsOuter() {
        for ((k, base) in cases.withIndex()) {
            val outer = RoundWarning.AGAINST_TOOL in RoundCalc.calc(base).warnings
            val inner = RoundWarning.AGAINST_TOOL in RoundCalc.calc(base.copy(inner = true)).warnings
            assertEquals(outer, inner, "TYPE ${base.type} $base")
            assertEquals(base.type == 2 || base.type == 4 || k == cases.lastIndex, inner, "TYPE ${base.type} $base")
            if (base.type >= 5) {
                assertEquals(outer, RoundWarning.AGAINST_TOOL in RoundCalc.calc(base.copy(inner = true, through = true)).warnings, "관통 $base")
            }
        }
    }

    /** 내경 3 의 CR 은 오목: R = r 이면 경고하고 숫자는 낸다. 내경 4 의 CR 은 볼록: 외경에서 나던 경고가 없다 */
    @Test
    fun concaveCrVsNose() {
        val (s, w) = solved(RoundCalc.calc(RoundInput(type = 3, inner = true, mainR = 12.5, d1 = 20.0, cornerR1 = 0.4, noseR = 0.4)))
        assertTrue(RoundWarning.CONCAVE_R_LE_NOSE in w, "$w")
        assertNull(s.arcs.single { abs(it.r - 0.4) <= 1e-12 }.progR)
        val ball = RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 0.3, noseR = 0.4)
        assertTrue(RoundWarning.CONCAVE_R_LE_NOSE !in solved(RoundCalc.calc(ball.copy(inner = true))).second)
        assertTrue(RoundWarning.CONCAVE_R_LE_NOSE in solved(RoundCalc.calc(ball)).second)
    }

    @Test
    fun innerGuards() {
        assertFailsWith<IllegalArgumentException> { RoundCalc.calc(RoundInput(type = 1, inner = true, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0)) }
        assertFailsWith<IllegalArgumentException> { RoundCalc.calc(cases[9].copy(through = true)) }
        assertFailsWith<IllegalArgumentException> { RoundCalc.calc(cases[3].copy(inner = true, through = true)) }
        // 구멍 지름 < 2r: 노즈 중심이 축을 넘어 맞은편 벽을 판다. Ø0.6 에 R0.4, 입구 Ø2 소켓에 R1.2(보정 자체는 풀리는 모양)
        for (i in listOf(
            RoundInput(type = 3, inner = true, mainR = 0.5, d1 = 0.6, cornerR1 = 0.0, noseR = 0.4, legLen = 2.0),
            RoundInput(type = 4, inner = true, mainR = 10.0, d1 = 2.0, cornerR1 = 0.5, noseR = 1.2),
        )) {
            val res = RoundCalc.calc(i)
            assertNull(res.solution, "$i")
            assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), res.warnings)
        }
        ok(RoundCalc.calc(RoundInput(type = 3, inner = true, mainR = 0.5, d1 = 0.6, cornerR1 = 0.0, noseR = 0.3, legLen = 2.0)))
        ok(RoundCalc.calc(RoundInput(type = 4, inner = true, mainR = 10.0, d1 = 2.0, cornerR1 = 0.5, noseR = 0.8)))
        // 모서리 R 은 외경 도면에 끼운 뒤 뒤집으므로 안 들어가는 것도 외경과 같다
        val big = RoundCalc.calc(RoundInput(type = 3, inner = true, mainR = 12.5, d1 = 20.0, cornerR1 = 13.0))
        assertEquals(listOf(RoundWarning.CORNER_R_TOO_BIG), big.warnings)
        // 팁을 정하면 그 팁, 바이트 반대쪽도 그 팁으로 본다(팁 3 은 구멍 벽이 사분면 밖)
        val (t3, w3) = solved(RoundCalc.calc(cases[3].copy(inner = true, tip = 3)))
        assertEquals(3, t3.tip)
        assertTrue(RoundWarning.AGAINST_TOOL in w3, "$w3")
    }

    /** NC 줄 끝점(찍힌 글을 따로 읽어) */
    private fun ncEnds(nc: List<String>): List<Pt> {
        var x = 0.0
        var z = 0.0
        return nc.map { line ->
            for (w in line.split(" ").drop(1)) when (w[0]) {
                'X' -> x = w.substring(1).toDouble() / 2
                'Z' -> z = w.substring(1).toDouble()
            }
            Pt(z, x)
        }
    }

    /** 점에서 요소까지 거리(계산기와 따로) */
    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val t = if (d.length == 0.0) 0.0 else (((p - s.start) dot d) / (d dot d)).coerceIn(0.0, 1.0)
            (p - (s.start + d * t)).length
        }
        is Arc -> {
            val a = angleOf(p - s.center) - angleOf(s.start - s.center)
            if (wrap2Pi(if (s.cw) -a else a) <= s.sweepRad() + 1e-9) abs((p - s.center).length - s.radius)
            else min((p - s.start).length, (p - s.end).length)
        }
    }

    /**
     * ncRefs: 줄마다 하나, 요소 번호는 줄지 않고, 줄 끝점은 그 요소 위(−2 = 진입점, −1 = 시작점). 노즈R 0 · Z 여유 0 이면 G01 시작 줄이 없다.
     * lineOf(k, 요소 끝) = 그 요소의 마지막 줄, 줄이 없는 요소는 그 앞 줄
     */
    @Test
    fun ncRefsConsistent() {
        val outer = listOf(
            RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0),
            RoundInput(type = 1, mainR = 10.0, l1 = 6.0, cornerR1 = 2.5),
        ) + cases
        val inputs = outer + cases.map { it.copy(inner = true) } + cases.filter { it.type >= 5 }.map { it.copy(inner = true, through = true) }
        val cen = Pt(-10.0, 15.0)
        fun on(deg: Double) = cen + Pt(cos(deg * PI / 180), sin(deg * PI / 180)) * 5.0
        val arcs = listOf(listOf(-60.0, 90.0, 240.0), listOf(30.0, 90.0, 150.0)).flatMap { t ->
            val p = t.map(::on)
            listOf(false, true).map { Arc3Input(it, 0.4, null, p[0].xDia, p[0].z, p[1].xDia, p[1].z, p[2].xDia, p[2].z) }
        }
        // (설명, 결과, 노즈R 0 · Z 여유 0)
        val sols = listOf(0.0, 0.4).flatMap { r ->
            listOf(0.0, 1.0).flatMap { zc ->
                inputs.map { Triple("$it r=$r Z여유=$zc", RoundCalc.calc(it.copy(noseR = r, zClear = zc)), r == 0.0 && zc == 0.0) } +
                    arcs.map { Triple("$it r=$r Z여유=$zc", RoundCalc.arc3(it.copy(noseR = r, zClear = zc)), r == 0.0 && zc == 0.0) }
            }
        }
        for ((tag, res, noStart) in sols) {
            val s = res.solution ?: continue
            assertEquals(s.nc.size, s.ncRefs.size, tag)
            assertTrue(s.ncRefs.zipWithNext().all { (a, b) -> a.element < b.element || a.element == b.element && a.piece < b.piece }, "$tag ${s.ncRefs}")
            for ((i, p) in ncEnds(s.nc).withIndex()) {
                val e = s.ncRefs[i].element
                val g = when (e) {
                    -2 -> (p - s.entry).length
                    -1 -> (p - s.program.segs[0].start).length
                    else -> dist(p, s.program.segs[e])
                }
                assertTrue(g <= 1e-3, "$tag 줄 $i ${s.nc[i]} 이 요소 $e 에서 $g")
            }
            if (noStart) assertTrue(s.ncRefs.none { it.element == -1 }, tag)
            for ((k, seg) in s.program.segs.withIndex()) {
                val line = s.lineOf(k, seg.end)
                val want = s.ncRefs.indexOfLast { it.element <= k }
                assertEquals(want, line, "$tag 요소 $k")
            }
        }
        assertTrue(sols.any { (_, r, _) -> r.solution?.ncRefs?.any { it.pieces > 1 } == true }, "나눈 원호가 하나는 있어야")
    }
}
