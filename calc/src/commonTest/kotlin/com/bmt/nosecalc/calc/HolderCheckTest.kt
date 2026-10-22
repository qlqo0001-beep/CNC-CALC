package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 보링바 간섭(HolderCheck). 정방향 보링 A~F 는 설계 때 따로 짠 표본 검사(파이썬)와 손 식에서, 뒤보링은 손 식에서 온 값.
 * 좌표는 G40 프로그램(가상 인선) 점, 따로 적지 않으면 ±0.003 mm(X 는 직경)
 */
class HolderCheckTest {
    /** 바 A: Ø16, f 11, C 80°, 날 9.67, κr 95° */
    private val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
    private val r = 0.4

    /** 내경 TYPE 3: Ø20 입구 깊이 10, SR12.5, CR1 — 구멍 안 돔 바닥 */
    private val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0)

    /** 내경 TYPE 5: 입구 Ø40 깊이 5 → 구면 → Ø30 깊이 6 → 바닥 */
    private val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)

    /** 내경 TYPE 4: 입구 Ø10 깊이 5, SR8, CR1 — 입구보다 넓은 공 모양 소켓 */
    private val socket = RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, legLen = 5.0)

    private fun inner(i: RoundInput, noseR: Double = r): RoundSolution {
        val res = RoundCalc.calc(i.copy(inner = true, noseR = noseR, zClear = 1.0))
        assertNotNull(res.solution, "${res.warnings}")
        return res.solution
    }

    private fun check(s: RoundSolution, bar: BoringBar, face: Boolean = true) =
        HolderCheck.check(s.drawing, s.program, s.entry, s.tip, r, face, bar)

    private fun near(z: Double, xDia: Double, p: Pt, tag: String, tol: Double = 3e-3) =
        assertTrue(abs(p.z - z) <= tol && abs(p.xDia - xDia) <= tol, "$tag: 기대 Z$z X$xDia, 실제 Z${p.z} X${p.xDia}")

    private fun hits(res: HolderResult, part: HolderPart) = res.hits.filter { it.part == part }

    /** 프로그램 요소 위 비율 t 의 점(계산기와 따로) */
    private fun on(s: Seg, t: Double): Pt = when (s) {
        is Line -> s.start + (s.end - s.start) * t
        is Arc -> {
            val a0 = angleOf(s.start - s.center)
            val a = a0 + (if (s.cw) -1 else 1) * s.sweepRad() * t
            s.center + Pt(cos(a), sin(a)) * s.radius
        }
    }

    /** 가상 인선 [tip] 자세의 부분별 여유(검사 고리) */
    private fun gaps(s: RoundSolution, bar: BoringBar, tip: Pt, face: Boolean = true): Margins =
        HolderCheck.margins(Bore(s.drawing, face), BarShape(bar, r, false), tip - NoseComp.tipVector(s.tip, r))

    @Test
    fun barSectionCheckValues() {
        val t = BarShape(barA, r, false)
        // 꼭짓점 N = (−0.44003, 0.44003), 바 위(x_c + D/2 = −2.6)로 나온 인서트, 바 앞 끝(주절삭날 연장선)
        val want = listOf(Pt(-0.3985, -0.0349), Pt(-0.1741, -2.6), Pt(9.3854, -2.6), Pt(9.1932, -0.4028), Pt(0.0349, 0.3985))
        val ins = t.inserts.single().pts
        assertEquals(want.size, ins.size, "$ins")
        for (w in want) assertTrue(ins.any { (it - w).length <= 1e-4 }, "인서트 꼭짓점 $w 없음: $ins")
        val body = t.body.pts
        for (w in listOf(Pt(1.2258, -18.6), Pt(-0.1741, -2.6))) assertTrue(body.any { (it - w).length <= 1e-4 }, "바 앞 $w 없음: $body")
        assertEquals(10.436, t.insZ, 1e-3)
        assertEquals(Pt(-r, r), t.ref)
        assertEquals(10.436, HolderCheck.overhangMin(barA, r), 1e-3)
    }

    /** A — 돔 바닥: 바와 인서트가 맞은편 벽에 닿는다(구 원호 = 요소 2). 앞 구멍 · 모서리 R 에서는 닿지 않는다 */
    @Test
    fun caseA() {
        val s = inner(dome)
        val res = check(s, barA)
        assertNull(res.skip)
        assertEquals(listOf(HolderPart.BAR, HolderPart.INSERT), res.hits.map { it.part })
        assertTrue(res.hits.all { it.element == 2 && it.opposite }, "${res.hits}")
        val bar = res.hits[0]
        near(-11.409, 18.006, bar.first.tip, "바 처음")
        assertEquals(12.4355, bar.depth, 1e-3)
        near(-15.0, 0.8, bar.worst.tip, "바 가장 깊은 곳")
        near(-15.0, 0.8, bar.last.tip, "바 마지막")
        assertEquals(1.0, bar.toT, 1e-12)
        assertEquals(1.626, bar.behind, 0.01)
        val ins = res.hits[1]
        near(-14.998, 1.238, ins.first.tip, "인서트 처음")
        assertEquals(0.2341, ins.depth, 1e-3)
        near(-15.0, 0.8, ins.worst.tip, "인서트 가장 깊은 곳")
        // 여유 0 이 되는 곳(닿음 여유 없이): Z−11.404 X18.014
        val arc = s.program.segs[2]
        var lo = 0.0
        var hi = bar.fromT
        repeat(60) {
            val mid = (lo + hi) / 2
            if (gaps(s, barA, on(arc, mid)).bar.m < 0) hi = mid else lo = mid
        }
        near(-11.404, 18.014, on(arc, hi), "여유 0")
        // 바 여유: 요소 0 은 바 아래가 다 구멍에 들어온 뒤 2ρ − f − D/2 = 1(처음에는 아래 앞 끝이 입구 면 앞이라 위쪽 3),
        // 요소 0~1 가운데 가장 좁은 곳은 요소 1 끝 0.8696
        for (t in listOf(0.3, 0.7, 1.0)) assertEquals(1.0, gaps(s, barA, on(s.program.segs[0], t)).bar.m, 1e-9)
        assertTrue((0..50).all { gaps(s, barA, on(s.program.segs[0], it / 50.0)).bar.m >= 1.0 - 1e-9 })
        assertEquals(0.8696, gaps(s, barA, s.program.segs[1].end).bar.m, 1e-4)
        assertTrue((0..50).all { gaps(s, barA, on(s.program.segs[1], it / 50.0)).bar.m >= 0.8696 - 1e-4 })
        // 경고 줄: G40 프로그램은 인선 좌표로, 기계 보정(노즈R 0) 프로그램은 도면 점으로 — 둘 다 5번째 줄(0부터 4)
        assertEquals(4, s.lineOf(2, bar.first.tip))
        assertEquals(4, inner(dome, 0.0).lineOf(2, bar.first.drawn))
    }

    /** B — Ø18 구멍: 바가 처음부터 맞은편 벽에 1 mm 들어간다 */
    @Test
    fun caseB() {
        val res = check(inner(dome.copy(d1 = 18.0)), barA)
        val bars = hits(res, HolderPart.BAR)
        assertEquals(listOf(0, 1, 2), bars.map { it.element })
        assertTrue(bars.all { it.opposite })
        near(-1.539, 18.0, bars[0].first.tip, "바 처음")
        assertEquals(1.0, bars[0].depth, 1e-6)
        assertEquals(0.0, bars[1].fromT)
        assertEquals(1.1826, bars[1].depth, 1e-3)
        assertEquals(12.4355, bars[2].depth, 1e-3)
    }

    /** C — 막힌 바닥 면을 중심까지: 바 아래가 Ø30 맞은편 벽에. 처음 닿는 반경 = f + D/2 − D1/2 − 0.005 = 3.995 */
    @Test
    fun caseC() {
        val s = inner(step)
        val res = check(s, barA)
        val h = res.hits.single()
        assertEquals(3, h.element)
        assertEquals(HolderPart.BAR, h.part)
        assertTrue(h.opposite)
        near(-16.0, 7.99, h.first.tip, "처음")
        assertEquals(3.6, h.depth, 1e-6)
        near(-16.0, 0.8, h.worst.tip, "가장 깊은 곳")
        assertEquals(2.9698, gaps(s, barA, s.program.segs[1].start).bar.m, 1e-4)
        // 관통이면 바닥 면이 없어 닿지 않는다. 가장 좁은 곳은 요소 0 · 1 이음점
        val t = check(inner(step.copy(through = true)), barA)
        assertTrue(t.hits.isEmpty(), "${t.hits}")
        assertEquals(2.9698, t.barClear!!, 1e-4)
    }

    /** C38 — 바닥을 중심까지 깎으려면 구멍 Ø ≥ 2f + D: Ø38 이면 끝에서 D1/2 + r − f − D/2 = 0.4 남는다. κr 90° 바 앞이 바닥에 붙어도 닿음이 아니다 */
    @Test
    fun bottomFacingNeedsBore() {
        val s = inner(RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 38.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0))
        for (kr in listOf(95.0, 90.0)) {
            val res = check(s, barA.copy(kappaDeg = kr))
            assertTrue(res.hits.isEmpty(), "κr $kr ${res.hits}")
            assertEquals(0.4, res.barClear!!, 1e-6)
            assertEquals(3, res.barClearElement)
            near(s.program.segs[3].end.z, s.program.segs[3].end.xDia, res.barClearAt!!.tip, "가장 좁은 곳", 1e-9)
        }
        // κr 90°: 바닥 면을 깎는 동안 바 앞 끝이 바닥 면과 같은 z
        val flat = BarShape(barA.copy(kappaDeg = 90.0), r, false)
        assertEquals(-r, flat.body.pts.minOf { it.z }, 1e-12)
    }

    /** D — 돌출 길이 12: 인선이 Z−12 를 넘으면 공구대가 입구 면에 닿는다 */
    @Test
    fun caseD() {
        val res = check(inner(dome), barA.copy(overhang = 12.0))
        val t = hits(res, HolderPart.TURRET).single()
        assertEquals(2, t.element)
        near(-12.005, 16.739, t.first.tip, "공구대 처음")
        assertEquals(3.0, t.depth, 1e-9)
        near(-15.0, 0.8, t.worst.tip, "공구대 가장 깊은 곳")
        assertEquals(setOf(HolderPart.BAR, HolderPart.INSERT, HolderPart.TURRET), res.hits.map { it.part }.toSet())
    }

    /** E — DMIN 20: 헤드(인선 뒤 insZ)가 Ø20 보다 좁은 곳에 들어가면 */
    @Test
    fun caseE() {
        val res = check(inner(dome), barA.copy(dMin = 20.0))
        val m = hits(res, HolderPart.MIN_BORE)
        assertEquals(listOf(0, 1, 2), m.map { it.element })
        near(-9.759, 20.0, m[0].first.tip, "처음")
        assertEquals(0.0835, m[0].depth, 1e-3)
        assertEquals(0.0, m[1].fromT)
        assertEquals(0.3394, m[1].depth, 1e-3)
        assertEquals(10.0, m[2].depth, 1e-3)
    }

    /**
     * F — 공 모양 소켓(언더컷), 작은 바(Ø8, f 4.5, D 55° 날 7.75, κr 93°): 바 위로 나온 인서트는 깎는 쪽 벽(입구 쪽 면)에, 바는 입구 턱에.
     * 이 인서트는 맞은 꼭짓점이 바 띠 아래로 3 mm 나와, 그 조각도 맞은편 벽에 닿는다
     */
    @Test
    fun caseF() {
        val bar = BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0)
        val s = inner(socket)
        val res = check(s, bar)
        assertEquals(
            listOf(0 to HolderPart.INSERT, 1 to HolderPart.INSERT, 2 to HolderPart.INSERT, 2 to HolderPart.BAR),
            res.hits.map { it.element to it.part },
        )
        val (i0, i1, _, b) = res.hits
        assertTrue(i0.opposite && i1.opposite && i1.fromT == 0.0)
        // 바 아래 조각: 맞은 꼭짓점 N + l(e1 + e2) 에서 e2 를 거슬러 바 아래 x = r − f − D/2 까지가 아래 변(기울기 −tan κr′, κr′ = 32°).
        // 요소 0 끝에서 그 변이 입구 면(z 0)까지 들어와 Ø10 맞은편 벽을 넘은 만큼이 깊이
        val kr = 93 * PI / 180
        val kp = 32 * PI / 180
        val e1 = Pt(-cos(kr), -sin(kr))
        val e2 = Pt(cos(kp), -sin(kp))
        val far = (e1 + e2) * (7.75 - r / sin(27.5 * PI / 180) / (e1 + e2).length)
        val bottom = r - 4.5 - 4.0
        val zc = far.z - (bottom - far.x) / tan(kp)
        val nose0 = s.program.segs[0].end - NoseComp.tipVector(2, r)
        assertEquals(-(nose0.x + bottom - (-nose0.z - zc) * tan(kp)) - 5.0, i0.depth, 1e-9)
        assertEquals(s.program.segs[0].end, i0.worst.tip)
        // 바 위 조각(깎는 쪽 벽)은 설계 값 그대로: 요소 1 처음 (Z−5.769, X10.548), 가장 깊은 곳 0.0655 (Z−5.980, X10.933), 요소 2 는 처음부터 0.2559
        val bore = Bore(s.drawing, true)
        val up = BarShape(bar, r, false).inserts.single { p -> p.pts.any { it.x > 0 } }
        fun upper(seg: Seg, t: Double) = bore.gap(up, on(seg, t) - NoseComp.tipVector(2, r)).m
        val ts = (0..4000).map { it / 4000.0 }
        val corner = s.program.segs[1]
        near(-5.769, 10.548, on(corner, ts.first { upper(corner, it) < -HolderCheck.CONTACT_TOL }), "인서트 처음")
        val w = ts.minBy { upper(corner, it) }
        assertEquals(0.0655, -upper(corner, w), 2e-3)
        near(-5.980, 10.933, on(corner, w), "인서트 가장 깊은 곳")
        val ball = s.program.segs[2]
        assertTrue(upper(ball, 0.0) < -HolderCheck.CONTACT_TOL)
        assertEquals(0.2559, -ts.minOf { upper(ball, it) }, 1e-3)
        // 바: 처음 닿는 반경 = 입구 반경 + f − D/2 + 0.005 = 5.505
        near(-6.015, 11.010, b.first.tip, "바 처음")
        assertEquals(5.5303, b.depth, 1e-3)
        near(-19.245, 0.8, b.worst.tip, "바 가장 깊은 곳")
        assertEquals(4, s.lineOf(2, b.first.tip))
        assertEquals(5, s.lineOf(2, b.worst.tip))
        assertEquals(0.5, gaps(s, bar, on(s.program.segs[0], 0.5)).bar.m, 1e-9)
    }

    /** 문턱: Ø30 관통, Ø20 바에서 f 가 맞은편 벽 문턱(D1/2 + r − ... )을 0.01 넘으면 닿고 0.01 모자라면 그만큼 남는다 */
    @Test
    fun thresholdSignFlip() {
        val s = inner(step.copy(through = true))
        val over = check(s, BoringBar(20.0, 20.01, InsertShape.C, 9.67, 95.0))
        val h = over.hits.single()
        assertEquals(2 to HolderPart.BAR, h.element to h.part)
        assertEquals(0.01, h.depth, 1e-6)
        val under = check(s, BoringBar(20.0, 19.99, InsertShape.C, 9.67, 95.0))
        assertTrue(under.hits.isEmpty(), "${under.hits}")
        assertEquals(0.01, under.barClear!!, 1e-6)
    }

    /**
     * 3점호 입구 R5(Ø30 면 → Ø20 구멍, CAD 점 셋째 자리): 시작 접선이 반올림으로 조금 기울어도 입구 면은 Z0. 바(A16R-SCLCR 09)는 닿지 않고,
     * 가장 좁은 곳은 원호 끝에서 바 아래 앞 끝(아직 Ø20 앞, R 위): ρ(z) − 9, z = 끝 노즈 z + E_bot.z
     */
    @Test
    fun entranceRFromCadPoints() {
        val a3 = Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0, zClear = 1.0)
        val s = RoundCalc.arc3(a3).solution!!
        assertEquals(0.0, Bore(s.drawing, false).face!!, 1e-9)
        val bar = BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, overhang = 12.0, dMin = 20.0)
        val res = HolderCheck.check(s.drawing, s.program, s.entry, s.tip, r, false, bar)
        assertTrue(res.hits.isEmpty(), "${res.hits}")
        val k = 95 * PI / 180
        val e1 = Pt(-cos(k), -sin(k))
        val n = (e1 + Pt(cos(5 * PI / 180), -sin(5 * PI / 180))).unit() * (-r / sin(40 * PI / 180))
        val eBot = n + e1 * ((n.x - (r - 11.0 - 8.0)) / sin(k))
        val z = -5.0 + eBot.z
        // CAD 점 반올림으로 중심이 5e-4 쯤 흔들린다
        assertEquals(15 - sqrt(25 - (z + 5) * (z + 5)) - 9.0, res.barClear!!, 2e-3)
        assertEquals(0, res.barClearElement)
        near(-5.4, 20.0, res.barClearAt!!.tip, "가장 좁은 곳", 2e-3)
    }

    @Test
    fun notMonotoneSkips() {
        // 270° 원호(중심 (−3, 13), R 4.243, −45° 에서 반시계로 225°): Z 로 되돌아간다
        val c = Pt(-3.0, 13.0)
        fun at(deg: Double) = c + Pt(cos(deg * PI / 180), sin(deg * PI / 180)) * 4.243
        val loop = Contour(listOf(Arc(at(-45.0), at(225.0), c, cw = false)))
        val res = HolderCheck.check(loop, loop, at(-45.0) + Pt(1.0, 0.0), 2, 0.0, false, barA)
        assertEquals(HolderSkip.NOT_MONOTONE, res.skip)
        assertTrue(res.hits.isEmpty())
        assertNull(res.barClear)
    }

    @Test
    fun badInput() {
        val fm = HolderCheck.fMin(16.0, 95.0, r)
        assertEquals(8.0 + r * (1 - cos(95 * PI / 180)), fm, 1e-12)
        assertEquals(r / tan(40 * PI / 180), HolderCheck.edgeMin(InsertShape.C, r), 1e-12)
        val s = inner(dome)
        val bad = listOf(
            barA.copy(dia = 0.0) to BarField.DIA,
            barA.copy(dia = Double.NaN) to BarField.DIA,
            barA.copy(dia = 2 * MAX_MM) to BarField.DIA,
            barA.copy(f = fm - 1e-6) to BarField.F,
            barA.copy(f = Double.POSITIVE_INFINITY) to BarField.F,
            barA.copy(edge = HolderCheck.edgeMin(InsertShape.C, r)) to BarField.EDGE,
            barA.copy(kappaDeg = 89.9) to BarField.KAPPA,
            barA.copy(kappaDeg = 100.0) to BarField.KAPPA, // 180 − ε
            barA.copy(overhang = 0.0) to BarField.OVERHANG,
            barA.copy(overhang = 10.4) to BarField.OVERHANG, // 헤드(10.436)보다 짧다
            barA.copy(dMin = -1.0) to BarField.DMIN,
            barA.copy(dMin = Double.NaN) to BarField.DMIN,
        )
        for ((b, field) in bad) {
            assertEquals(setOf(field), HolderCheck.problems(b, r), "$b")
            val res = check(s, b)
            assertEquals(HolderSkip.BAD_INPUT, res.skip, "$b")
            assertTrue(res.hits.isEmpty())
        }
        // 경계: f = fMin 이면 바 윗면이 노즈 접점 T1 에 닿는 높이 — 받는다
        assertTrue(HolderCheck.problems(barA.copy(f = fm), r).isEmpty())
        assertTrue(HolderCheck.problems(barA.copy(kappaDeg = 90.0, overhang = 10.5, dMin = 20.0), r).isEmpty())
        assertEquals(HolderSkip.BAD_INPUT, HolderCheck.check(s.drawing, s.program, s.entry, s.tip, -0.1, true, barA).skip)
    }

    /** 겹침 그림(A 끝 자세): 꼭짓점은 소재 안이고 공구 조각 안, 가장 깊은 곳 = 바 깊이 */
    @Test
    fun overlapAtWorstPose() {
        val s = inner(dome)
        val nose = s.program.segs[2].end - NoseComp.tipVector(s.tip, r)
        val ov = HolderCheck.overlap(s.drawing, true, barA, r, nose)
        assertTrue(ov.isNotEmpty())
        val bore = Bore(s.drawing, true)
        val pieces = BarShape(barA, r, false).let { it.inserts + it.body }
        var deepest = Double.NEGATIVE_INFINITY
        for (c in ov) for (p in c.segs.map { it.start }) {
            val rho = minOf(bore.rho(p.z - 1e-9), bore.rho(p.z + 1e-9))
            assertTrue(abs(p.x) >= rho - 1e-6, "소재 밖 $p (ρ $rho)")
            assertTrue(pieces.any { inside(it.pts.map { q -> q + nose }, p) }, "공구 밖 $p")
            deepest = max(deepest, abs(p.x) - rho)
        }
        assertEquals(12.4355, deepest, 0.02)
        // 닿지 않는 자세(요소 0 가운데)는 겹침 없음
        assertTrue(HolderCheck.overlap(s.drawing, true, barA, r, on(s.program.segs[0], 0.5) - NoseComp.tipVector(2, r)).isEmpty())
    }

    /**
     * 뒤보링 겹침 그림(X50 Z−10 → R10 → X30 Z0, 바 A16): 입구 반직선이 +Z 로 끝없이 가 띠가 약 250 mm 로 넓어지면 R10 원호가
     * 현 하나로 그려져 원호와 현 사이(최대 2.6 mm)가 빠졌다. 그린 바처럼 maxZ(Z+3)에서 자르면 띠가 좁아 아래 변이 원호를 따른다
     */
    @Test
    fun backBoringOverlapFollowsArc() {
        val a3 = Arc3Input(inner = true, noseR = r, x1 = 50.0, z1 = -10.0, x2 = 35.8579, z2 = -7.0711, x3 = 30.0, z3 = 0.0, zClear = 1.0)
        val s = RoundCalc.arc3(a3).solution!!
        val bar = BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0)
        val res = HolderCheck.check(s.drawing, s.program, s.entry, s.tip, r, false, bar)
        val nose = res.hits.first { it.part == HolderPart.BAR }.first.nose
        val ov = HolderCheck.overlap(s.drawing, false, bar, r, nose, maxZ = 3.0)
        assertTrue(ov.isNotEmpty())
        assertTrue(ov.all { c -> c.segs.all { it.start.z <= 3.0 + 1e-9 } }, "Z+3 너머는 자른다")
        val body = BarShape(bar, r, true).body.pts.map { it + nose }
        // 원호(중심 Z0 · 반경 25, R10) 바로 위 0.05 mm(소재 안)이고 바 안인 점은 겹친 곳 안
        for (z in listOf(-8.0, -6.08, -4.0, -2.0)) {
            val p = Pt(z, 25 - sqrt(100 - z * z) + 0.05)
            assertTrue(inside(body, p), "바 안 $p")
            assertTrue(ov.any { inRing(it.segs.map { q -> q.start }, p) }, "겹친 곳 밖 $p")
        }
    }

    /** 바(공구대 면까지 자름)와 노즈 원호를 넣은 인서트 */
    @Test
    fun outlineShapes() {
        val nose = Pt(-5.0, 3.0)
        val (body, ins) = HolderCheck.outline(barA, r, nose, maxZ = 20.0)
        assertTrue(body.segs.all { it is Line })
        assertEquals(20.0, body.segs.maxOf { max(it.start.z, it.end.z) }, 1e-12)
        assertTrue((body.segs.last().end - body.segs.first().start).length <= 1e-12, "닫힘")
        val arc = ins.segs.single { it is Arc } as Arc
        assertEquals(nose, arc.center)
        assertEquals(r, arc.radius, 1e-12)
        // 노즈 원호는 가상 인선 쪽(위 · 왼쪽)을 지난다: 끝 X+ · Z− 극점이 원호 위
        assertEquals(nose.z - r, ins.segs.minOf { s -> if (s is Arc) s.center.z - s.radius else minOf(s.start.z, s.end.z) }, 1e-12)
        assertTrue((ins.segs.last().end - ins.segs.first().start).length <= 1e-12, "닫힘")
        // 뒤보링은 인서트를 Z 로 뒤집는다: 노즈 원호가 +Z 쪽
        val (_, back) = HolderCheck.outline(barA, r, nose, 20.0, reverse = true)
        val ba = back.segs.single { it is Arc } as Arc
        assertTrue(back.segs.filter { it is Line }.all { maxOf(it.start.z, it.end.z) <= nose.z + r + 1e-12 })
        assertTrue(abs(angleOf(mid(ba) - nose)) < PI / 2, "원호 가운데가 +Z 쪽")
    }

    /**
     * 뒤보링(3점호 역방향, 팁 1 · G42): 방 안 뒷면 → R5 모서리 → Ø20 구멍. 바는 +Z 로 Ø20 을 지나 나가야 하므로
     * 시작에서 바 윗면 = P1 반경 + r − f + D/2. f 11 이면 0.4 들어가 처음 4.195° 까지 닿고, f 11.5 면 0.1 남는다.
     * 인서트는 −Z 쪽(방 안 공기)으로 뻗어 닿지 않는다 — 정방향 인서트였다면 모서리를 판다
     */
    @Test
    fun backBoringBarMustFitBore() {
        val a3 = Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = -20.0, x2 = 24.0, z2 = -19.0, x3 = 20.0, z3 = -15.0, zClear = 1.0)
        val s = RoundCalc.arc3(a3).solution!!
        assertEquals(1, s.tip)
        assertEquals(Side.G42, s.side)
        assertEquals(listOf("G00 X30.8 Z-21.", "G01 Z-20.", "G03 X20. Z-14.6 R5.4"), s.nc)
        val bar = BoringBar(12.0, 11.0, InsertShape.C, 9.67, 95.0)
        val res = HolderCheck.check(s.drawing, s.program, s.entry, s.tip, r, false, bar)
        assertNull(res.skip)
        assertEquals(listOf(-2, -1, 0), res.hits.map { it.element })
        assertTrue(res.hits.all { it.part == HolderPart.BAR && !it.opposite && abs(it.depth - 0.4) <= 1e-9 }, "${res.hits}")
        val h = res.hits.last()
        assertEquals(0.0, h.fromT)
        // 바 윗면 = 노즈 중심 x − 4.6 이 10.005 가 되는 곳: 노즈 중심 원(반지름 5.4)에서 180° + asin(0.395 / 5.4)
        val th = asin(0.395 / 5.4)
        assertEquals(th / (PI / 2), h.toT, 1e-6)
        near(-15.0 - 5.4 * cos(th) + r, 2 * (14.605 + r), h.last.tip, "마지막")
        near(-20.0, 30.8, h.worst.tip, "가장 깊은 곳")
        val ok = HolderCheck.check(s.drawing, s.program, s.entry, s.tip, r, false, bar.copy(f = 11.5))
        assertTrue(ok.hits.isEmpty(), "${ok.hits}")
        assertEquals(0.1, ok.barClear!!, 1e-9)
        assertEquals(-2, ok.barClearElement)
        assertEquals(s.entry, ok.barClearAt!!.tip)
    }

    /**
     * 뒤보링 인서트의 끝날(−Z 로 κr′ = 5° 내려감)은 +Z 로 α 만큼 넓어지는 원뿔을 깎을 때 α > κr′ 이면 이미 깎은 면을 판다:
     * 깊이 = l · sin(α − κr′) / cos α (날 끝 꼭짓점, 인선에서 −l·cos κr′). 노즈R 0 · 팁 1 이라 프로그램 = 도면
     */
    @Test
    fun backBoringTrailingEdge() {
        val bar = BoringBar(12.0, 10.0, InsertShape.C, 9.67, 95.0)
        for (deg in listOf(15.0, 4.0)) {
            val al = deg * PI / 180
            val cone = Contour(listOf(Line(Pt(-20.0, 10.0), Pt(-10.0, 10.0 + 10 * tan(al)))))
            val entry = Pt(-20.0 - cos(al), 10.0 - sin(al))
            val res = HolderCheck.check(cone, cone, entry, 1, 0.0, false, bar)
            assertNull(res.skip)
            if (deg < 5) {
                assertTrue(res.hits.isEmpty(), "${res.hits}")
                continue
            }
            assertEquals(listOf(-2, -1, 0), res.hits.map { it.element }, "${res.hits}")
            for (h in res.hits) {
                assertEquals(HolderPart.INSERT, h.part)
                assertTrue(!h.opposite)
                assertEquals(9.67 * sin(10 * PI / 180) / cos(al), h.depth, 1e-9)
                assertEquals(-9.67 * cos(5 * PI / 180), h.behind, 1e-9)
            }
            assertEquals(0.0, res.hits[2].fromT)
            assertEquals(1.0, res.hits[2].toT)
        }
    }

    /**
     * 뒤보링 인서트 = 정방향 인서트를 노즈 중심에서 Z 로 뒤집은 것: Z 로 뒤집은 원뿔 구멍에서 뒤집은 자세의 인서트 여유가 같다
     * (바는 늘 +Z 로 나가 뒤집지 않는다)
     */
    @Test
    fun insertMirrorsForReverse() {
        val bar = BoringBar(12.0, 9.0, InsertShape.C, 11.0, 95.0)
        for (deg in listOf(3.0, 15.0)) {
            val rise = 20 * tan(deg * PI / 180)
            val fwd = Contour(listOf(Line(Pt(10.0, 10.0), Pt(-10.0, 10.0 + rise))))
            val rev = Contour(listOf(Line(Pt(-10.0, 10.0), Pt(10.0, 10.0 + rise))))
            val bf = Bore(fwd, false)
            val br = Bore(rev, false)
            val tf = BarShape(bar, r, false)
            val tr = BarShape(bar, r, true)
            // 정방향(G41)은 진행 방향 왼쪽 = 축 쪽에 노즈 중심
            val toTool = Pt(-rise, -20.0).unit()
            var worst = Double.POSITIVE_INFINITY
            for (k in 0..20) {
                val nose = on(fwd.segs[0], 0.3 + 0.02 * k) + toTool * r
                val a = HolderCheck.margins(bf, tf, nose).insert.m
                val b = HolderCheck.margins(br, tr, Pt(-nose.z, nose.x)).insert.m
                assertEquals(a, b, 1e-9, "α $deg° 자세 $k")
                worst = minOf(worst, a)
            }
            if (deg > 10) assertTrue(worst < -0.5, "가파른 원뿔은 끝날이 판다: $worst") else assertTrue(worst > -HolderCheck.CONTACT_TOL, "$worst")
        }
    }

    private fun mid(a: Arc): Pt = on(a, 0.5)

    /** 닫힌 꺾은선(볼록이 아니어도) 안 — 반직선 교차 수 */
    private fun inRing(ring: List<Pt>, p: Pt): Boolean {
        var odd = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            if ((a.x > p.x) != (b.x > p.x) && p.z < a.z + (p.x - a.x) * (b.z - a.z) / (b.x - a.x)) odd = !odd
        }
        return odd
    }

    /** 볼록 다각형(반시계) 안 또는 변 위(1e-6) */
    private fun inside(poly: List<Pt>, p: Pt): Boolean =
        poly.indices.all { i ->
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val d = b - a
            d.length <= 1e-12 || (d cross (p - a)) / d.length >= -1e-6
        }
}
