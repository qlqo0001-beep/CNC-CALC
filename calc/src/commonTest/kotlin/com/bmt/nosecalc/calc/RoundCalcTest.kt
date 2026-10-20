package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundCalcTest {
    private val leg = 5.0
    private val noseRs = listOf(0.0, 0.2, 0.4, 0.8, 1.2)

    private fun ok(r: RoundResult): RoundSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        return r.solution
    }

    /** 숫자와 함께 나온 경고까지 */
    private fun solved(r: RoundResult) = ok(r) to r.warnings

    private fun stops(w: RoundWarning, r: RoundResult) {
        assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다")
        assertEquals(listOf(w), r.warnings)
    }

    private fun near(e: Pt, a: Pt, msg: String, tol: Double = 1e-9) =
        assertTrue((e - a).length <= tol, "$msg: 기대 $e, 실제 $a")

    private fun nodes(c: Contour) = listOf(c.segs.first().start) + c.segs.map { it.end }

    private fun unit(v: Pt) = v * (1 / v.length)

    /** 진행 방향 단위 접선(계산기와 따로) */
    private fun tangent(s: Seg, p: Pt): Pt = when (s) {
        is Line -> unit(s.end - s.start)
        is Arc -> {
            val n = unit(p - s.center)
            val l = Pt(-n.x, n.z)
            if (s.cw) l * -1.0 else l
        }
    }

    // ---- 닫힌 식(계산기와 따로): 구 중심은 축 위 C = (zc, 0). 모서리 R c 의 중심은 직선에서 c, 구 중심에서 R ∓ c ----

    /** 수평선(높이 h)과 구 사이 모서리 R. 볼록이면 R 안쪽(R − c, 선 아래), 오목이면 바깥(R + c, 선 위). 반환: (선 접점, 구 접점) */
    private fun filletH(zc: Double, bigR: Double, h: Double, c: Double, convex: Boolean, rightOfCenter: Boolean): Pair<Pt, Pt> {
        val fx = if (convex) h - c else h + c
        val dist = if (convex) bigR - c else bigR + c
        val fz = zc + (if (rightOfCenter) 1 else -1) * sqrt(dist * dist - fx * fx)
        val f = Pt(fz, fx)
        val cen = Pt(zc, 0.0)
        return Pt(fz, h) to cen + (f - cen) * (bigR / dist)
    }

    /** 수직선(z = zf)과 구 사이 모서리 R. 모서리 원 중심의 z = zf + dz. 반환: (선 접점, 구 접점) */
    private fun filletV(zc: Double, bigR: Double, zf: Double, c: Double, convex: Boolean, dz: Double): Pair<Pt, Pt> {
        val dist = if (convex) bigR - c else bigR + c
        val f = Pt(zf + dz, sqrt(dist * dist - (zf + dz - zc) * (zf + dz - zc)))
        val cen = Pt(zc, 0.0)
        return Pt(zf, f.x) to cen + (f - cen) * (bigR / dist)
    }

    /** 기대 도면 노드와 원호(cw, R) — 가공 순서(오른쪽 → 왼쪽). 비울 수 있는 길이(L·L2)는 비워 둔 입력만 받는다 */
    private fun expected(i: RoundInput): Pair<List<Pt>, List<Pair<Boolean, Double>>> {
        val bigR = i.mainR!!
        val n = mutableListOf(Pt(0.0, 0.0))
        val arcs = mutableListOf<Pair<Boolean, Double>>()
        fun corner(c: Double, cw: Boolean, sharp: Pt, pts: () -> List<Pt>) {
            if (c > 0) {
                n += pts()
                arcs += cw to c
            } else n += sharp
        }
        when (i.type) {
            1 -> {
                val l = i.l1!!
                val c = i.cornerR1!!
                val zc = -l / 2
                val h = sqrt(bigR * bigR - l * l / 4)
                corner(c, false, Pt(0.0, h)) { filletV(zc, bigR, 0.0, c, true, -c).toList() }
                arcs += false to bigR
                corner(c, false, Pt(-l, h)) { filletV(zc, bigR, -l, c, true, c).toList().reversed() }
                n += Pt(-l, 0.0)
            }
            2 -> {
                val a = i.d1!! / 2
                val l1 = i.l1!!
                val c = i.cornerR1!!
                val s = sqrt(bigR * bigR - a * a)
                val zc = -l1 - s
                n += Pt(0.0, a)
                corner(c, true, Pt(-l1, a)) { filletH(zc, bigR, a, c, false, true).toList() }
                arcs += false to bigR
                corner(c, true, Pt(zc - s, a)) { filletH(zc, bigR, a, c, false, false).toList().reversed() }
                n += Pt(zc - s - leg, a)
            }
            3, 4 -> {
                val h = i.d1!! / 2
                val c = i.cornerR1!!
                val ball = i.type == 4
                val zc = -bigR
                val l = bigR + (if (ball) 1 else -1) * sqrt(bigR * bigR - h * h)
                arcs += false to bigR
                corner(c, ball, Pt(-l, h)) { filletH(zc, bigR, h, c, !ball, !ball).toList().reversed() }
                n += Pt(-l - leg, h)
            }
            5 -> {
                val a = i.d1!! / 2
                val b = i.d2!! / 2
                val l1 = i.l1!!
                val u = sqrt(bigR * bigR - a * a)
                val v = sqrt(bigR * bigR - b * b)
                val zc = -l1 - u
                n += Pt(0.0, a)
                corner(i.cornerR1!!, true, Pt(-l1, a)) { filletH(zc, bigR, a, i.cornerR1, false, true).toList() }
                arcs += false to bigR
                corner(i.cornerR2!!, false, Pt(zc + v, b)) { filletH(zc, bigR, b, i.cornerR2, true, true).toList().reversed() }
                n += Pt(zc + v - leg, b)
            }
            6 -> {
                val a = i.d1!! / 2
                val b = i.d2!! / 2
                val l1 = i.l1!!
                val u = sqrt(bigR * bigR - a * a)
                val zc = -l1 - u
                val zf = -l1 - i.l2!!
                val hf = sqrt(bigR * bigR - (zf - zc) * (zf - zc))
                n += Pt(0.0, a)
                corner(i.cornerR1!!, true, Pt(-l1, a)) { filletH(zc, bigR, a, i.cornerR1, false, true).toList() }
                arcs += false to bigR
                corner(i.cornerR2!!, true, Pt(zf, hf)) { filletV(zc, bigR, zf, i.cornerR2, false, i.cornerR2).toList().reversed() }
                n += Pt(zf, b)
                n += Pt(zf - leg, b)
            }
        }
        return n to arcs
    }

    /** 대표 치수. 비울 수 있는 길이는 비워 둠(계산기가 채운다) */
    private val cases = listOf(
        RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0),
        RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 0.0),
        RoundInput(type = 1, mainR = 10.0, l1 = 6.0, cornerR1 = 2.5),
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

    /**
     * TYPE 1~6: 도면 윤곽이 닫힌 식의 접점과 같고, 원호 방향·R 이 맞고, 원호가 낀 이음점마다 접선 방향이 이어진다.
     * 주 원호 중심은 축 위.
     */
    @Test
    fun drawingMatchesClosedForm() {
        for (i in cases) {
            val s = ok(RoundCalc.calc(i))
            val (en, ea) = expected(i)
            val got = nodes(s.drawing)
            assertEquals(en.size, got.size, "TYPE ${i.type} 노드 수 $i")
            for (k in en.indices) near(en[k], got[k], "TYPE ${i.type} 노드 $k $i")
            val arcs = s.drawing.segs.filterIsInstance<Arc>()
            assertEquals(ea.map { it.first }, arcs.map { it.cw }, "TYPE ${i.type} 원호 방향 $i")
            for ((k, a) in arcs.withIndex()) assertTrue(abs(a.radius - ea[k].second) <= 1e-9, "TYPE ${i.type} 원호 $k R ${a.radius}")
            val main = arcs.single { abs(it.radius - i.mainR!!) <= 1e-9 && abs(it.center.x) <= 1e-12 }
            assertTrue(main.sweepDeg < 180, "주 원호는 반원보다 작다")
            // 원호가 낀 이음점: 접선 연속
            for ((a, b) in s.drawing.segs.zipWithNext()) {
                if (a !is Arc && b !is Arc) continue
                if (i.cornerR1 == 0.0 && (i.cornerR2 ?: 0.0) == 0.0) continue
                val ta = tangent(a, a.end)
                val tb = tangent(b, b.start)
                assertTrue((ta - tb).length <= 1e-9, "TYPE ${i.type} 접선 끊김 $ta → $tb ($i)")
            }
            // 원호 표 = 도면의 원호
            assertEquals(arcs.size, s.arcs.size)
            for (ra in s.arcs) {
                val a = s.drawing.segs[ra.index] as Arc
                assertEquals(a.cw, ra.cw)
                assertEquals(a.radius, ra.r)
                assertEquals(a.center, ra.center)
            }
        }
    }

    /** 점에서 요소까지(유한) 거리 */
    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val t = if (d.length == 0.0) 0.0 else (((p - s.start) dot d) / (d dot d)).coerceIn(0.0, 1.0)
            (p - (s.start + d * t)).length
        }
        is Arc -> if (onArc(s, p)) abs((p - s.center).length - s.radius) else min((p - s.start).length, (p - s.end).length)
    }

    private fun onArc(s: Arc, p: Pt): Boolean {
        val a = angleOf(p - s.center) - angleOf(s.start - s.center)
        return wrap2Pi(if (s.cw) -a else a) <= s.sweepRad() + 1e-12
    }

    /** 요소를 품은 직선·원까지 거리 */
    private fun carrierDist(p: Pt, s: Seg): Double = when (s) {
        is Line -> abs(unit(s.end - s.start) cross (p - s.start))
        is Arc -> abs((p - s.center).length - s.radius)
    }

    private fun samples(s: Seg): List<Pt> = when (s) {
        is Line -> (0..20).map { s.start + (s.end - s.start) * (it / 20.0) }
        is Arc -> {
            val a0 = angleOf(s.start - s.center)
            val sw = s.sweepRad() * (if (s.cw) -1 else 1)
            (0..20).map {
                val t = a0 + sw * it / 20.0
                s.center + Pt(cos(t), sin(t)) * s.radius
            }
        }
    }

    /**
     * 보정(합성 R): 노즈 중심 경로가 도면 어디에도 r 보다 가까이 가지 않고(파고듦 없음), 요소마다 그 요소의 직선·원에서
     * 정확히 r 떨어지며, 원호의 보정 R 은 볼록 R + r · 오목 R − r. 인선 경로 = 중심 경로 + 팁 벡터.
     */
    @Test
    fun compensationAllTypes() {
        for (base in cases) for (r in noseRs) {
            val i = base.copy(noseR = r)
            val (s, w) = solved(RoundCalc.calc(i))
            val tag = "TYPE ${i.type} r=$r $i"
            assertEquals(Side.G42, s.side)
            assertEquals(3, s.tip)
            val v = NoseComp.tipVector(3, r)
            assertEquals(s.drawing.segs.size, s.center.segs.size)
            assertEquals(s.drawing.segs.size, s.program.segs.size)
            for (k in s.drawing.segs.indices) {
                val d = s.drawing.segs[k]
                val c = s.center.segs[k]
                val p = s.program.segs[k]
                near(c.start + v, p.start, "$tag 인선 = 중심 + 팁 $k")
                near(c.end + v, p.end, "$tag 인선 = 중심 + 팁 $k")
                val gone = (c.end - c.start).length <= 1e-9
                if (!gone) for (q in samples(c)) {
                    assertTrue(abs(carrierDist(q, d) - r) <= 1e-7, "$tag 요소 $k 에서 r 이 아님: ${carrierDist(q, d)}")
                    val m = s.drawing.segs.minOf { dist(q, it) }
                    assertTrue(m >= r - 1e-7, "$tag 파고듦: 요소 $k 의 점 $q 가 도면에서 $m")
                }
                if (d is Arc) {
                    // G42(공구가 진행 방향 오른쪽): 시계(G02) 원호는 공구가 원 안쪽 = 오목
                    val want = if (d.cw) d.radius - r else d.radius + r
                    val ra = s.arcs.single { it.index == k }
                    if (want <= 1e-9) {
                        assertTrue(gone, "$tag 오목 R ≤ r 인데 원호가 남음")
                        assertNull(ra.progR)
                        assertTrue(RoundWarning.CONCAVE_R_LE_NOSE in w, tag)
                    } else {
                        val pa = p as Arc
                        assertTrue(abs(pa.radius - want) <= 1e-9, "$tag 보정 R ${pa.radius} ≠ $want")
                        assertEquals(d.cw, pa.cw, "$tag 보정 원호 방향")
                        assertTrue(abs(ra.progR!! - want) <= 1e-9)
                    }
                }
            }
            // 노드 표 = 도면 노드와 보정 노드
            assertEquals(nodes(s.drawing), s.nodes.map { it.drawn }, tag)
            assertEquals(nodes(s.program), s.nodes.map { it.prog }, tag)
            if (s.drawing.segs.none { it is Arc && it.cw && it.radius <= r + 1e-9 }) {
                assertTrue(RoundWarning.CONCAVE_R_LE_NOSE !in w, tag)
            }
        }
    }

    /** 바이트 반대쪽 형상: 왼쪽 면(1), 공의 뒤쪽(2·4), 꼭대기를 넘는 돔(6 의 셋째) */
    @Test
    fun againstTool() {
        for ((k, i) in cases.withIndex()) {
            val (s, w) = solved(RoundCalc.calc(i))
            val want = i.type in listOf(1, 2, 4) || k == cases.lastIndex
            assertEquals(want, RoundWarning.AGAINST_TOOL in w, "TYPE ${i.type} $i")
        }
        // 가운데 팁(0)은 따지지 않는다
        val (s, w) = solved(RoundCalc.calc(cases[0].copy(tip = 0)))
        assertTrue(RoundWarning.AGAINST_TOOL !in w)
    }

    /**
     * 손 계산(TYPE 3): Ø20, SR12.5 → L = 12.5 − √(12.5² − 10²) = 5. 모서리 R1, 노즈 0.4, 팁 3.
     * 모서리 R 중심 (−12.5 + √(11.5² − 9²), 9) = (−5.34109, 9), 구 접점 = C + (F − C)·12.5/11.5 = (−4.71858, 9.78261).
     * 보정: 돔 끝 (0, 0) → 중심 (0.4, 0) → 인선 (0, −0.4) = X−0.8. 구 접점 → 중심 반지름 12.9 → 인선 X19.391 Z−4.870.
     * 모서리 R 끝 (−5.34109, 10) → 인선 (−5.74109, 10) = X20. 원통 끝 Z −10 − 0.4.
     */
    @Test
    fun handExampleType3() {
        val (s, w) = solved(RoundCalc.calc(RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4)))
        assertTrue(abs(s.filled!!.l1!! - 5.0) <= 1e-12)
        assertEquals(
            listOf("G00 X-0.8 Z1.", "G01 Z0.", "G03 X19.391 Z-4.87 R12.9", "G03 X20. Z-5.741 R1.4", "G01 Z-10.4"),
            s.nc,
        )
        assertEquals(listOf("19.565", "-4.719"), s.nodes[1].drawn.let { listOf(Fmt.fixed3(it.xDia), Fmt.fixed3(it.z)) })
        near(Pt(1.0, -0.4), s.entry, "진입점")
        assertTrue(w.isEmpty(), "${w}")
    }

    /** Z 여유가 노즈R 보다 작아도(0 포함) 진입점은 첫 보정점에서 노즈R 만큼 띄운다 — 테이퍼와 같다. 3점호도 */
    @Test
    fun entryStaysNoseRAway() {
        val i = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4)
        for (zc in listOf(0.0, 0.2)) {
            val s = ok(RoundCalc.calc(i.copy(zClear = zc)))
            near(Pt(0.4, -0.4), s.entry, "진입점 Z 여유 $zc")
            assertEquals("G00 X-0.8 Z0.4", s.nc.first())
        }
        near(Pt(1.0, -0.4), ok(RoundCalc.calc(i.copy(zClear = 1.0))).entry, "Z 여유가 크면 그대로")
        val cen = Pt(-10.0, 15.0)
        val p = listOf(30.0, 90.0, 150.0).map { onCircle(cen, 5.0, it) }
        val a = RoundCalc.arc3(Arc3Input(false, 0.4, null, p[0].xDia, p[0].z, p[1].xDia, p[1].z, p[2].xDia, p[2].z, zClear = 0.0))
        val s = ok(a)
        assertEquals(s.nodes[0].prog + Pt(0.4, 0.0), s.entry)
    }

    /** NC 를 따라가면 보정 경로의 끝점(찍힌 글)과 G02/G03·R 이 그대로다 */
    @Test
    fun ncFollowsProgram() {
        for (base in cases) for (r in noseRs) {
            val s = ok(RoundCalc.calc(base.copy(noseR = r)))
            val tag = "TYPE ${base.type} r=$r"
            assertTrue(s.nc[0].startsWith("G00"), tag)
            var x = ""
            var z = ""
            val ends = mutableListOf<Triple<String, Pair<String, String>, String?>>()
            for ((n, line) in s.nc.withIndex()) {
                val w = line.split(" ")
                var rr: String? = null
                for (t in w.drop(1)) when (t[0]) {
                    'X' -> x = t.substring(1)
                    'Z' -> z = t.substring(1)
                    'R' -> rr = t.substring(1)
                }
                if (n > 0) ends += Triple(w[0], x to z, rr) // 첫 줄 G00 은 위치(모달)만
            }
            // 첫 줄(G01) = 프로그램 시작점, 그다음은 움직이는 요소마다 한 줄 — 90° 넘는 원호는 같은 각으로 나눈 줄 수만큼
            // (R 지령은 각이 클수록 끝점 반올림이 중심 위치에서 커진다. TYPE 2 의 공, TYPE 4 의 꼭대기를 넘는 원호가 그렇다)
            val p0 = s.program.segs.first().start
            assertEquals(Fmt.nc(p0.xDia) to Fmt.nc(p0.z), ends[0].second, tag)
            val moving = s.program.segs.filter { (it.end - it.start).length > 1e-9 }
            var k = 1
            for (seg in moving) {
                val parts = if (seg is Arc) max(1, ceil(seg.sweepDeg / 90 - 1e-3).toInt()) else 1
                repeat(parts) {
                    val e = ends[k++]
                    when (seg) {
                        is Line -> assertEquals("G01", e.first, tag)
                        is Arc -> {
                            assertEquals(if (seg.cw) "G02" else "G03", e.first, tag)
                            assertEquals(Fmt.nc(seg.radius), e.third, tag)
                        }
                    }
                }
                assertEquals(Fmt.nc(seg.end.xDia) to Fmt.nc(seg.end.z), ends[k - 1].second, tag)
            }
            assertEquals(ends.size, k, "$tag ${s.nc}")
        }
    }

    /** 모서리 R 0 이면 이론 꼭짓점 그대로, 접해서 이어지는 곳(반구)은 모서리 R 을 넣어도 원호가 생기지 않는다 */
    @Test
    fun tangentJunctionNeedsNoFillet() {
        val s = ok(RoundCalc.calc(RoundInput(type = 3, mainR = 10.0, d1 = 20.0, cornerR1 = 2.0)))
        assertEquals(2, s.drawing.segs.size)
        near(Pt(-10.0, 10.0), s.drawing.segs[0].end, "반구 끝")
        assertTrue(abs(s.filled!!.l1!! - 10.0) <= 1e-12)
    }

    // ---- 비울 수 있는 값 하나(구 중심이 축 위라 하나는 나머지로 정해진다) ----

    private fun sameShape(a: RoundSolution, b: RoundSolution, tag: String) {
        val na = nodes(a.drawing)
        val nb = nodes(b.drawing)
        assertEquals(na.size, nb.size, tag)
        for (k in na.indices) near(na[k], nb[k], "$tag 노드 $k", 1e-9)
    }

    @Test
    fun fillOneBlankType2() {
        val ref = ok(RoundCalc.calc(RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 1.0)))
        val l2b = 2 * sqrt(100.0 - 16.0)
        assertTrue(abs(ref.filled!!.l2!! - l2b) <= 1e-12)
        assertEquals(L2Ref.B, ref.filled.l2Ref)
        val a = ok(RoundCalc.calc(RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 1.0, l2Ref = L2Ref.A)))
        assertTrue(abs(a.filled!!.l2!! - (l2b + 5.0)) <= 1e-12, "L2(A) = L1 + L2(B)")
        sameShape(ref, a, "L2(A)")
        val base = RoundInput(type = 2, l1 = 5.0, cornerR1 = 1.0)
        for (ref2 in L2Ref.entries) {
            val l2 = if (ref2 == L2Ref.A) l2b + 5.0 else l2b
            sameShape(ref, ok(RoundCalc.calc(base.copy(d1 = 8.0, l2 = l2, l2Ref = ref2))), "R 비움 $ref2")
            sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = 10.0, l2 = l2, l2Ref = ref2))), "D1 비움 $ref2")
            sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = 10.0, d1 = 8.0, l2 = l2 + 0.0004, l2Ref = ref2))), "모두 $ref2")
            stops(RoundWarning.CONTRADICTION, RoundCalc.calc(base.copy(mainR = 10.0, d1 = 8.0, l2 = l2 + 0.01, l2Ref = ref2)))
        }
        stops(RoundWarning.MISSING_INPUT, RoundCalc.calc(base.copy(mainR = 10.0)))
        stops(RoundWarning.MISSING_INPUT, RoundCalc.calc(RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0)))
        // L2(A) 가 L1 보다 짧으면 공이 없다
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(d1 = 8.0, l2 = 4.0, l2Ref = L2Ref.A)))
        // 공이 축보다 가늘다
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(mainR = 3.0, d1 = 8.0)))
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(mainR = 5.0, l2 = 12.0)))
    }

    @Test
    fun fillOneBlankType34() {
        for ((type, l) in listOf(3 to 12.5 - 7.5, 4 to 8.0 + sqrt(64.0 - 25.0))) {
            val (bigR, d) = if (type == 3) 12.5 to 20.0 else 8.0 to 10.0
            val base = RoundInput(type = type, cornerR1 = 1.0)
            val ref = ok(RoundCalc.calc(base.copy(mainR = bigR, d1 = d)))
            assertTrue(abs(ref.filled!!.l1!! - l) <= 1e-12, "TYPE $type L ${ref.filled.l1}")
            sameShape(ref, ok(RoundCalc.calc(base.copy(d1 = d, l1 = l))), "TYPE $type R 비움")
            sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = bigR, l1 = l))), "TYPE $type D 비움")
            sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = bigR, d1 = d, l1 = l - 0.0004))), "TYPE $type 모두")
            stops(RoundWarning.CONTRADICTION, RoundCalc.calc(base.copy(mainR = bigR, d1 = d, l1 = l + 0.01)))
            stops(RoundWarning.MISSING_INPUT, RoundCalc.calc(base.copy(mainR = bigR)))
            // R 이 반지름 D/2 보다 작으면 돔·공이 안 된다
            stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(mainR = d / 2 - 0.5, d1 = d)))
        }
        // 돔(3)은 L ≤ D/2, 공(4)은 L ≥ D/2
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 3, d1 = 20.0, l1 = 11.0, cornerR1 = 1.0)))
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 4, d1 = 20.0, l1 = 9.0, cornerR1 = 1.0)))
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 3, mainR = 10.0, l1 = 11.0, cornerR1 = 1.0)))
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 4, mainR = 10.0, l1 = 9.0, cornerR1 = 1.0)))
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 4, mainR = 10.0, l1 = 20.0, cornerR1 = 1.0)))
    }

    @Test
    fun fillOneBlankType5() {
        val base = RoundInput(type = 5, l1 = 6.0, cornerR1 = 1.0, cornerR2 = 2.0)
        val ref = ok(RoundCalc.calc(base.copy(mainR = 25.0, d2 = 40.0, d1 = 16.0)))
        val l2 = sqrt(625.0 - 64.0) - 15.0
        assertTrue(abs(ref.filled!!.l2!! - l2) <= 1e-12)
        sameShape(ref, ok(RoundCalc.calc(base.copy(d2 = 40.0, d1 = 16.0, l2 = l2))), "R 비움")
        sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = 25.0, d1 = 16.0, l2 = l2))), "D2 비움")
        sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = 25.0, d2 = 40.0, l2 = l2))), "D1 비움")
        sameShape(ref, ok(RoundCalc.calc(base.copy(mainR = 25.0, d2 = 40.0, d1 = 16.0, l2 = l2 + 0.0004))), "모두")
        stops(RoundWarning.CONTRADICTION, RoundCalc.calc(base.copy(mainR = 25.0, d2 = 40.0, d1 = 16.0, l2 = l2 + 0.01)))
        stops(RoundWarning.MISSING_INPUT, RoundCalc.calc(base.copy(mainR = 25.0, d2 = 40.0)))
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(mainR = 25.0, d2 = 16.0, d1 = 20.0)))
        // R < D2/2: 구가 큰 원통에 못 닿는다
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(mainR = 15.0, d2 = 40.0, d1 = 16.0)))
        // L2 가 √(b² − a²) 보다 길면 중심이 큰 원통 쪽을 넘어 볼록 전이부가 아니다
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(base.copy(d2 = 40.0, d1 = 16.0, l2 = 18.4)))
    }

    @Test
    fun shapeChecks() {
        // TYPE 1: L ≥ 2R 이면 면이 구를 자르지 못한다
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 1, mainR = 10.0, l1 = 20.0, cornerR1 = 1.0)))
        // TYPE 6: 돔이 플랜지보다 크다
        stops(
            RoundWarning.SHAPE_MISMATCH,
            RoundCalc.calc(RoundInput(type = 6, mainR = 20.0, d2 = 30.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 1.0, cornerR2 = 1.0)),
        )
        // TYPE 6: 플랜지 면이 구 밖
        stops(
            RoundWarning.SHAPE_MISMATCH,
            RoundCalc.calc(RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 40.0, cornerR1 = 1.0, cornerR2 = 1.0)),
        )
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(RoundInput(type = 6, mainR = 5.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 3.0, cornerR1 = 1.0, cornerR2 = 1.0)))
        // TYPE 6, 중심이 면 오른쪽(꼭대기 R15 가 돔 안): 면 높이 13.8 은 플랜지(14.5)보다 낮아도 꼭대기가 넘는다
        val over = RoundInput(type = 6, mainR = 15.0, d2 = 29.0, d1 = 10.0, l1 = 3.0, l2 = 20.0, cornerR1 = 1.0, cornerR2 = 1.0)
        stops(RoundWarning.SHAPE_MISMATCH, RoundCalc.calc(over))
        ok(RoundCalc.calc(over.copy(d2 = 31.0)))
        // 중심이 면 왼쪽: 구가 R20 이어도 돔에 든 것은 면 높이 17.8 까지라 플랜지 19 면 된다
        ok(RoundCalc.calc(RoundInput(type = 6, mainR = 20.0, d2 = 38.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 1.0, cornerR2 = 1.0)))
    }

    @Test
    fun cornerTooBig() {
        // 축(L1 0.5)을 모서리 R 2 가 다 먹는다
        stops(RoundWarning.CORNER_R_TOO_BIG, RoundCalc.calc(RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 0.5, cornerR1 = 2.0)))
        // 면이 모서리 R 보다 짧다
        stops(RoundWarning.CORNER_R_TOO_BIG, RoundCalc.calc(RoundInput(type = 1, mainR = 10.0, l1 = 6.0, cornerR1 = 9.0)))
        // 볼록 모서리 R ≥ 구 R
        stops(RoundWarning.CORNER_R_TOO_BIG, RoundCalc.calc(RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 13.0)))
    }

    @Test
    fun concaveCornerVersusNose() {
        // 오목 모서리 R = 노즈R: 보정 원호가 점이 된다(경고, 숫자는 낸다). R < r: 그 자리에 r 이 남는다
        for (c in listOf(0.4, 0.2)) {
            val (s, w) = solved(RoundCalc.calc(RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = c, noseR = 0.4)))
            assertTrue(RoundWarning.CONCAVE_R_LE_NOSE in w, "c=$c")
            val fillet = s.arcs.single { it.cw }
            assertNull(fillet.progR)
            assertTrue(s.nc.none { it.startsWith("G02") }, "${s.nc}")
        }
        val (s, w) = solved(RoundCalc.calc(RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 0.41, noseR = 0.4)))
        assertTrue(RoundWarning.CONCAVE_R_LE_NOSE !in w)
        assertTrue(abs(s.arcs.single { it.cw }.progR!! - 0.01) <= 1e-12)
    }

    /**
     * 노즈가 축과 플랜지 면 사이의 짧은 돔에 못 닿는다. 돔 보정원(R + r)이 축 보정선(x = a + r)과 만나는 z 는
     * −L1 − u + √((R + r)² − (a + r)²) = −4.42, 플랜지 면 보정선은 z = −L1 − L2 + r 라 L2 < 0.22 면 그 오른쪽.
     */
    @Test
    fun noseUnreachable() {
        val i = RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 0.1, cornerR1 = 0.0, cornerR2 = 0.0, noseR = 0.8)
        stops(RoundWarning.NOSE_UNREACHABLE, RoundCalc.calc(i))
        ok(RoundCalc.calc(i.copy(l2 = 0.3)))
    }

    /**
     * 한쪽 끝이 볼록인 짧은 요소도 못 깎으면 멈춘다(전에는 볼록 쪽 연장 교점 때문에 못 알아채고 숫자를 냈다).
     * - TYPE 2 (D1 29.634, L1 0.608, L2(A) 40.753, R 비움 → 24.95): 축과 공 사이 오목 모서리(공구 쪽 사이각 126.4°)에 접한
     *   r 1.983 원은 축 위 1.983/tan(63.2°) = 1.0 에서 닿는데 축은 0.608 이다. 다른 끝(끝 면 모서리)은 볼록.
     * - TYPE 6: 오목 CR2 0.606 < r 0.8 이 빠지고 남는 플랜지 면(0.141)에 노즈 원이 닿을 곳이 없다. 경고 CONCAVE_R_LE_NOSE
     *   하나로 숫자를 내면 면이 통째로 안 깎인 것을 모른다.
     */
    @Test
    fun shortElementNextToConvexCornerStops() {
        stops(
            RoundWarning.NOSE_UNREACHABLE,
            RoundCalc.calc(
                RoundInput(type = 2, cornerR1 = 0.0, d1 = 29.634, l1 = 0.608, l2 = 40.75319209070994, l2Ref = L2Ref.A, noseR = 1.983),
            ),
        )
        stops(
            RoundWarning.NOSE_UNREACHABLE,
            RoundCalc.calc(
                RoundInput(
                    type = 6, mainR = 18.554, cornerR1 = 1.75, cornerR2 = 0.606, d1 = 28.589, d2 = 37.236, l1 = 8.285, l2 = 7.365,
                    noseR = 0.8,
                ),
            ),
        )
    }

    /**
     * 3점 오목 홈 X40 Z0 → X35 Z−3 → X40 Z−6 (R3.05): 시작 접선이 재료로 가파르게 내려가 공구 쪽 법선이 −Z 쪽이다.
     * Z 로 물러난 진입(X39.344 높이로 들어옴)은 노즈 원이 시작점 옆을 0.33 mm 판다 → 시작 접선을 거꾸로 따라 Z 여유만큼.
     */
    @Test
    fun threePointArcEntryFollowsTangentWhenZWouldGouge() {
        val p1 = Pt.ofDia(0.0, 40.0)
        val s = ok(arc3(p1, Pt.ofDia(-3.0, 35.0), Pt.ofDia(-6.0, 40.0)))
        val back = Pt(0.55, 3.0) * (1 / 3.05) // 시계 원호의 시작 접선(중심 (−3, 20.55) 에서 시작점으로 (3, −0.55))의 반대
        near(s.nodes[0].prog + back, s.entry, "진입점")
        val c0 = s.center.segs.first().start
        for (k in 0..20) assertTrue((c0 + back * (k / 20.0) - p1).length >= 0.4 - 1e-9, "진입 중 노즈 원이 시작점을 판다")
    }

    @Test
    fun badInputs() {
        val good = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0)
        ok(RoundCalc.calc(good))
        stops(RoundWarning.MISSING_INPUT, RoundCalc.calc(good.copy(cornerR1 = null)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(cornerR1 = -1.0)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(mainR = 0.0)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(d1 = -20.0)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(mainR = Double.NaN)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(noseR = -0.4)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(tip = 10)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(legLen = 0.0)))
        stops(RoundWarning.BAD_INPUT, RoundCalc.calc(good.copy(zClear = -1.0)))
        assertFailsWith<IllegalArgumentException> { RoundCalc.calc(good.copy(type = 7)) }
    }

    // ---- 수동 모드: 세 점 원호 ----

    private fun onCircle(c: Pt, r: Double, deg: Double) = c + Pt(cos(deg * PI / 180), sin(deg * PI / 180)) * r

    private fun arc3(p1: Pt, p2: Pt, p3: Pt, inner: Boolean = false, noseR: Double = 0.4, tip: Int? = null) =
        RoundCalc.arc3(Arc3Input(inner, noseR, tip, p1.xDia, p1.z, p2.xDia, p2.z, p3.xDia, p3.z))

    /** 세 점을 지나는 원: 중심·반지름·방향, 가운데 점이 원호 위(시작 → 끝 사이) */
    @Test
    fun threePointArcPassesThroughPoints() {
        val cen = Pt(-10.0, 15.0)
        val tries = listOf(
            listOf(30.0, 80.0, 150.0), listOf(150.0, 80.0, 30.0), listOf(-30.0, -90.0, -150.0),
            listOf(10.0, 20.0, 170.0), listOf(-60.0, 90.0, 240.0), listOf(200.0, 90.0, -20.0),
        )
        for (t in tries) {
            val p = t.map { onCircle(cen, 5.0, it) }
            val s = ok(arc3(p[0], p[1], p[2]))
            val a = s.drawing.segs.single() as Arc
            near(cen, a.center, "중심 $t")
            assertTrue(abs(a.radius - 5.0) <= 1e-9)
            near(p[0], a.start, "시작 $t")
            near(p[2], a.end, "끝 $t")
            assertEquals(t[2] < t[0], a.cw, "방향 $t")
            assertTrue(onArc(a, p[1]), "가운데 점이 원호 밖 $t")
            assertTrue(abs((p[1] - a.center).length - a.radius) <= 1e-9)
            assertEquals(a.center, s.arcs.single().center)
            assertNull(s.filled)
        }
    }

    /** 외경: 볼록(위로 볼록) R + r, 오목 홈 R − r. 내경은 반대. 보정점 = 원 중심에서 R ± r + 팁 벡터 */
    @Test
    fun threePointArcCompensation() {
        val r = 0.4
        // 외경 볼록, 척 쪽(−Z)으로: G42, 팁 3
        val cen = Pt(-10.0, 15.0)
        val p = listOf(30.0, 90.0, 150.0).map { onCircle(cen, 5.0, it) }
        val s = ok(arc3(p[0], p[1], p[2]))
        assertEquals(Side.G42, s.side)
        assertEquals(3, s.tip)
        val pa = s.program.segs.single() as Arc
        assertTrue(abs(pa.radius - 5.4) <= 1e-9)
        val v = NoseComp.tipVector(3, r)
        near(cen + unit(p[0] - cen) * 5.4 + v, s.nodes[0].prog, "시작 보정")
        near(cen + unit(p[2] - cen) * 5.4 + v, s.nodes[1].prog, "끝 보정")
        assertEquals("G03", s.nc.last().substringBefore(" "))
        assertTrue(s.nc.last().endsWith(" R5.4"), s.nc.last())
        assertEquals(s.nodes[0].prog + Pt(1.0, 0.0), s.entry)
        // 외경 오목 홈
        val g = listOf(-30.0, -90.0, -150.0).map { onCircle(Pt(-10.0, 20.0), 5.0, it) }
        val sg = ok(arc3(g[0], g[1], g[2]))
        assertTrue(abs((sg.program.segs.single() as Arc).radius - 4.6) <= 1e-9)
        assertEquals("G02", sg.nc.last().substringBefore(" "))
        // 내경(G41, 팁 2): 같은 볼록 모양이 공구에게는 오목
        val si = ok(arc3(p[0], p[1], p[2], inner = true))
        assertEquals(Side.G41, si.side)
        assertEquals(2, si.tip)
        assertTrue(abs((si.program.segs.single() as Arc).radius - 4.6) <= 1e-9)
        near(cen + unit(p[0] - cen) * 4.6 + NoseComp.tipVector(2, r), si.nodes[0].prog, "내경 시작 보정")
        // 역방향(+Z 로): 외경 G41, 팁 4, 진입은 −Z 쪽
        val sr = ok(arc3(p[2], p[1], p[0]))
        assertEquals(Side.G41, sr.side)
        assertEquals(4, sr.tip)
        assertTrue(abs((sr.program.segs.single() as Arc).radius - 5.4) <= 1e-9)
        assertEquals(sr.nodes[0].prog + Pt(-1.0, 0.0), sr.entry)
        // 팁을 바꾸면 그 팁 벡터
        val st = ok(arc3(p[0], p[1], p[2], tip = 8))
        near(cen + unit(p[0] - cen) * 5.4 + NoseComp.tipVector(8, r), st.nodes[0].prog, "팁 8")
    }

    /**
     * 180° 넘는 원호는 R 의 뜻이 둘이라 나눠 써야 한다. 나눌 때는 같은 각으로 조각마다 90° 이하(300° → 75° 넷):
     * R 지령의 중심은 끝점 반올림을 1/cos(각/2) 배로 키워, 반으로만 나눈 150° 조각은 0.001 mm 를 넘을 수 있다
     */
    @Test
    fun threePointArcOver180SplitsNc() {
        val cen = Pt(-10.0, 15.0)
        val p = listOf(-60.0, 90.0, 240.0).map { onCircle(cen, 5.0, it) }
        val (s, w) = solved(arc3(p[0], p[1], p[2], noseR = 0.0, tip = 0))
        val arcs = s.nc.filter { it.startsWith("G03") }
        assertEquals(4, arcs.size, "${s.nc}")
        assertTrue(arcs.all { it.endsWith(" R5.") })
        val top = onCircle(cen, 5.0, 90.0)
        assertEquals("G03 X${Fmt.nc(top.xDia)} Z${Fmt.nc(top.z)} R5.", arcs[1])
        assertTrue(RoundWarning.AGAINST_TOOL !in w, "팁 0 은 안 본다")
        assertTrue(RoundWarning.AGAINST_TOOL in arc3(p[0], p[1], p[2]).warnings, "300° 원호는 팁 3 으로 다 못 깎는다")
        // 팁 1 로 오목 300° 원호(0° → 시계 → 60°): 양 끝 접점은 팁 사분면 안이지만 그 사이가 밖
        val q = listOf(0.0, -150.0, 60.0).map { onCircle(cen, 5.0, it) }
        assertTrue(RoundWarning.AGAINST_TOOL in arc3(q[0], q[1], q[2], tip = 1).warnings)
    }

    /**
     * 화면 예시 값: 단면에서 외경으로 도는 볼록 R10(X30 Z0 → X50 Z−10)을 CAD 점처럼 넷째 자리로 넣는다. 접점은 사분면 경계(45°)
     * 위라 바이트 반대쪽이 아니다 — 반올림으로 중심이 1e-5 흔들려 경고가 나던 것. 2° 넘게 단면 뒤로 돌면 경고.
     * 원호 각이 90° 를 조금(0.0003°) 넘어도 NC 는 R 한 줄이다
     */
    @Test
    fun threePointQuarterRoundTypedFromCad() {
        val (s, w) = solved(arc3(Pt.ofDia(0.0, 30.0), Pt.ofDia(-2.9289, 44.1421), Pt.ofDia(-10.0, 50.0)))
        assertTrue(RoundWarning.AGAINST_TOOL !in w, "$w")
        // 90.0003° 원호는 나누지 않고 한 줄
        assertTrue((s.drawing.segs.single() as Arc).sweepDeg > 90.0)
        assertEquals(listOf("G00 X29.2 Z1.", "G01 Z0.", "G03 X50. Z-10.4 R10.4"), s.nc)
        val cen = Pt(-10.0, 15.0)
        val past = arc3(onCircle(cen, 10.0, -2.0), onCircle(cen, 10.0, 45.0), onCircle(cen, 10.0, 90.0))
        assertTrue(RoundWarning.AGAINST_TOOL in past.warnings, "${past.warnings}")
    }

    @Test
    fun threePointArcBadInputs() {
        val a = Pt(0.0, 10.0)
        stops(RoundWarning.SHAPE_MISMATCH, arc3(a, Pt(-1.0, 11.0), Pt(-2.0, 12.0))) // 한 줄
        stops(RoundWarning.SHAPE_MISMATCH, arc3(a, a, Pt(-2.0, 12.0))) // 겹침
        stops(RoundWarning.SHAPE_MISMATCH, arc3(a, Pt(-1.0, 12.0), Pt(0.0, 14.0))) // 시작·끝 Z 같음: 방향 모름
        stops(RoundWarning.MISSING_INPUT, RoundCalc.arc3(Arc3Input(x1 = 20.0, z1 = 0.0, x2 = 22.0, z2 = -1.0, x3 = 24.0)))
        stops(RoundWarning.BAD_INPUT, arc3(Pt(0.0, -1.0), Pt(-1.0, 11.0), Pt(-3.0, 12.0)))
        stops(RoundWarning.BAD_INPUT, arc3(a, Pt(-1.0, 12.0), Pt(-3.0, 12.0), tip = 11))
        stops(RoundWarning.BAD_INPUT, arc3(a, Pt(-1.0, 12.0), Pt(-20_000.0, 12.0))) // 크기 한도(±MAX_MM)
        stops(RoundWarning.SHAPE_MISMATCH, arc3(a, Pt(-1.0, 11.0 + 1e-7), Pt(-2.0, 12.0))) // 거의 한 줄: 중심이 한도 밖
        // 오목 R < 노즈R: 공구가 원호를 못 따라간다
        val g = listOf(-30.0, -90.0, -150.0).map { onCircle(Pt(-10.0, 20.0), 0.3, it) }
        stops(RoundWarning.NOSE_UNREACHABLE, arc3(g[0], g[1], g[2]))
    }
}
