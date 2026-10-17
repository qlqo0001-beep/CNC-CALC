package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NoseCompTest {
    private val angles = (5..85 step 5).map { it.toDouble() }
    private val radii = listOf(0.2, 0.4, 0.8, 1.2, 1.6)

    private fun rad(deg: Double) = deg * PI / 180

    /** Haas 표(tool tip code 0~9)를 그대로 옮긴 기대값. 벡터 = 노즈 중심 → 가상 인선 */
    @Test
    fun tipVectors() {
        val r = 0.4
        val expected = mapOf(
            0 to Pt(0.0, 0.0),
            1 to Pt(r, r),   // X+ Z+
            2 to Pt(-r, r),  // X+ Z-
            3 to Pt(-r, -r), // X- Z-
            4 to Pt(r, -r),  // X- Z+
            5 to Pt(r, 0.0), // Z+
            6 to Pt(0.0, r), // X+
            7 to Pt(-r, 0.0), // Z-
            8 to Pt(0.0, -r), // X-
            9 to Pt(0.0, 0.0),
        )
        for ((tip, v) in expected) assertEquals(v, NoseComp.tipVector(tip, r), "T$tip")
        assertFailsWith<IllegalArgumentException> { NoseComp.tipVector(10, r) }
        assertFailsWith<IllegalArgumentException> { NoseComp.tipVector(-1, r) }
    }

    /** Haas: 외경을 단면에서 척 쪽으로 깎으면 G42, 내경은 G41. 역방향은 진행이 뒤집혀 반대 */
    @Test
    fun standardSideAndTip() {
        assertEquals(Side.G42, Side.of(inner = false, reverse = false))
        assertEquals(Side.G41, Side.of(inner = false, reverse = true))
        assertEquals(Side.G41, Side.of(inner = true, reverse = false))
        assertEquals(Side.G42, Side.of(inner = true, reverse = true))
        assertEquals(3, NoseComp.standardTip(inner = false, reverse = false))
        assertEquals(4, NoseComp.standardTip(inner = false, reverse = true))
        assertEquals(2, NoseComp.standardTip(inner = true, reverse = false))
        assertEquals(1, NoseComp.standardTip(inner = true, reverse = true))
    }

    @Test
    fun zeroRadiusKeepsContour() {
        val c = Contour(
            listOf(
                Line(Pt(5.0, 10.0), Pt(0.0, 10.0)),
                Arc(Pt(0.0, 10.0), Pt(-5.0, 15.0), Pt(-5.0, 10.0), cw = false),
                Line(Pt(-5.0, 15.0), Pt(-8.0, 18.0)),
            ),
        )
        val p = NoseComp.tipPath(c, 0.0, Side.G42, 3)
        assertTrue(p.warnings.isEmpty())
        for ((a, b) in c.segs.zip(p.path.segs)) {
            assertPt(a.start, b.start)
            assertPt(a.end, b.end)
        }
    }

    /**
     * 가공 경우 4가지. sz = 진행 방향(−1 = 척 쪽), sx = 테이퍼가 진행하며 X 가 느는(+1)/주는(−1) 쪽.
     * zSign·xSign = 닫힌 식 보정량을 어느 쪽으로 더하나 (손으로 정한 값).
     */
    private data class Case(
        val name: String, val tip: Int, val side: Side,
        val sz: Double, val sx: Double, val zSign: Double, val xSign: Double,
    )

    private val cases = listOf(
        Case("외경 정방향", 3, Side.G42, sz = -1.0, sx = 1.0, zSign = -1.0, xSign = -1.0),
        Case("내경 정방향", 2, Side.G41, sz = -1.0, sx = -1.0, zSign = -1.0, xSign = 1.0),
        Case("외경 역방향", 4, Side.G41, sz = 1.0, sx = 1.0, zSign = 1.0, xSign = -1.0),
        Case("내경 역방향", 1, Side.G42, sz = 1.0, sx = -1.0, zSign = 1.0, xSign = 1.0),
    )

    /** 수평 → 테이퍼 → 수평 (테이퍼 TYPE 3 꼴): 꺾인 두 점이 Z 로만 ΔZ = r(1 − tan(θ/2)) 밀린다 */
    @Test
    fun taperBetweenFlatsMatchesClosedForm() {
        val a = 50.0
        val len = 2.0
        for (k in cases) for (deg in angles) for (r in radii) {
            val h = len * tan(rad(deg))
            val t1 = Pt(0.0, a)
            val t2 = Pt(k.sz * len, a + k.sx * h)
            val c = Contour.polyline(Pt(-k.sz * 5, a), t1, t2, Pt(k.sz * (len + 5), t2.x))
            val p = NoseComp.tipPath(c, r, k.side, k.tip)
            val dz = NoseR.taperDeltaZ(r, deg)
            val tag = "${k.name} ${deg}° r$r"
            assertTrue(p.warnings.isEmpty(), tag)
            assertPt(t1 + Pt(k.zSign * dz, 0.0), p.path.segs[0].end, msg = "$tag T1")
            assertPt(t2 + Pt(k.zSign * dz, 0.0), p.path.segs[1].end, msg = "$tag T2")
            // 앞뒤 수평 요소는 X 가 도면 그대로
            assertEquals(a, p.path.segs[0].start.x, 1e-9, tag)
            assertEquals(t2.x, p.path.segs[2].end.x, 1e-9, tag)
        }
    }

    /** 수직 → 테이퍼 → 수직 (테이퍼 TYPE 2 꼴): 꺾인 두 점이 X 로만 ΔX = r(1 − tan((90° − θ)/2)) 밀린다 */
    @Test
    fun taperBetweenFacesMatchesClosedForm() {
        val a = 50.0
        val len = 2.0
        for (k in cases) for (deg in angles) for (r in radii) {
            val h = len * tan(rad(deg))
            val t1 = Pt(0.0, a)
            val t2 = Pt(k.sz * len, a + k.sx * h)
            val c = Contour.polyline(Pt(0.0, a - k.sx * 5), t1, t2, Pt(t2.z, t2.x + k.sx * 5))
            val p = NoseComp.tipPath(c, r, k.side, k.tip)
            val dx = NoseR.taperDeltaXDia(r, deg) / 2
            val tag = "${k.name} ${deg}° r$r"
            assertTrue(p.warnings.isEmpty(), tag)
            assertPt(t1 + Pt(0.0, k.xSign * dx), p.path.segs[0].end, msg = "$tag T1")
            assertPt(t2 + Pt(0.0, k.xSign * dx), p.path.segs[1].end, msg = "$tag T2")
            // 앞뒤 단면 요소는 Z 가 도면 그대로
            assertEquals(0.0, p.path.segs[0].start.z, 1e-9, tag)
            assertEquals(t2.z, p.path.segs[2].end.z, 1e-9, tag)
        }
    }

    /**
     * 외경에서 척 쪽으로 갈수록 작아지는 테이퍼(TYPE 5 꼴). 닫힌 식이 다르다: ΔZ = r(1 + tan(θ/2)).
     * 손 유도: 공구 쪽 법선 n = (−sinθ, cosθ), 인선 이동 u = r·n + (−r, −r),
     * 같은 X 에서 Z 이동 = u.z − u.x·cotθ = −r(1/sinθ + 1 − cotθ) = −r(1 + tan(θ/2)).
     */
    @Test
    fun descendingTaperOuter() {
        val b = 50.0
        val len = 2.0
        for (deg in angles) for (r in radii) {
            val h = len * tan(rad(deg))
            val t1 = Pt(0.0, b)
            val t2 = Pt(-len, b - h)
            val c = Contour.polyline(Pt(5.0, b), t1, t2, Pt(-len - 5, t2.x))
            val p = NoseComp.tipPath(c, r, Side.G42, 3)
            val dz = r * (1 + tan(rad(deg) / 2))
            assertPt(t1 + Pt(-dz, 0.0), p.path.segs[0].end, msg = "${deg}° r$r T1")
            assertPt(t2 + Pt(-dz, 0.0), p.path.segs[1].end, msg = "${deg}° r$r T2")
        }
    }

    /**
     * 원본 앱 스토어 사진 06.jpg: `G00 X40. Z0.` → `G01 X34. Z-2.` → `G01 Z-30.` 의
     * 보정좌표가 X 34.000 · Z -2.186 으로 찍혀 있다. 사진에 노즈R·팁이 안 보이지만
     * 내경 입구 면취(재료가 위)·노즈 0.4·T2·G41 로 풀면 같은 값이 나온다.
     */
    @Test
    fun innerChamferFromStoreScreenshot06() {
        val c = Contour.polyline(Pt.ofDia(0.0, 40.0), Pt.ofDia(-2.0, 34.0), Pt.ofDia(-30.0, 34.0))
        val p = NoseComp.tipPath(c, 0.4, Side.G41, 2).path.segs[0].end
        assertEquals("34.000", Fmt.fixed3(p.xDia))
        assertEquals("-2.186", Fmt.fixed3(p.z))
    }

    /**
     * Haas 워크북 43~44쪽 원호 예제(인치)를 노즈 0.031 · 외경 정방향(G42) · T3 로 보정.
     * 볼록 R0.25(G03) → R + r, 오목 R1.5(G02) → R − r. 가상 인선 원호의 끝점은 도면의 직선 위에 그대로 남는다.
     */
    @Test
    fun convexAndConcaveArcs() {
        val r = 0.031
        val c = Contour(
            listOf(
                Line(Pt(0.0, 0.70), Pt(0.0, 0.75)), // N7 X1.5 (단면)
                Arc(Pt(0.0, 0.75), Pt(-0.25, 1.0), Pt(-0.25, 0.75), cw = false), // N8 G03 X2. Z-0.25 R0.25
                Line(Pt(-0.25, 1.0), Pt(-1.0, 1.0)), // N9 G01 Z-1.
                Arc(Pt(-1.0, 1.0), Pt(-2.5, 2.5), Pt(-1.0, 2.5), cw = true), // N10 G02 X5. Z-2.5 R1.5
                Line(Pt(-2.5, 2.5), Pt(-2.5, 3.0)), // N11 G01 X6.
            ),
        )
        val center = NoseComp.centerPath(c, r, Side.G42)
        assertTrue(center.warnings.isEmpty())
        val convex = center.path.segs[1] as Arc
        val concave = center.path.segs[3] as Arc
        assertEquals(0.25 + r, convex.radius, 1e-12)
        assertPt(Pt(-0.25, 0.75), convex.center)
        assertEquals(false, convex.cw)
        assertEquals(1.5 - r, concave.radius, 1e-12)
        assertPt(Pt(-1.0, 2.5), concave.center)
        assertEquals(true, concave.cw)

        val tip = NoseComp.tipPath(c, r, Side.G42, 3).path.segs
        val a1 = tip[1] as Arc
        assertPt(Pt(0.0, 0.75 - r), a1.start)
        assertPt(Pt(-0.25 - r, 1.0), a1.end)
        assertPt(Pt(-0.25 - r, 0.75 - r), a1.center)
        val a2 = tip[3] as Arc
        assertPt(Pt(-1.0 - r, 1.0), a2.start)
        assertPt(Pt(-2.5, 2.5 - r), a2.end)
        assertPt(Pt(-1.0 - r, 2.5 - r), a2.center)
    }

    /** 어깨 안쪽 R0.3 에 노즈 0.4 — 공구가 못 들어간다. 경고 + 원호 자리는 길이 0, 가상 인선은 날카로운 모서리로 */
    @Test
    fun concaveArcSmallerThanNoseWarns() {
        val c = Contour(
            listOf(
                Line(Pt(10.0, 10.0), Pt(0.3, 10.0)),
                Arc(Pt(0.3, 10.0), Pt(0.0, 10.3), Pt(0.3, 10.3), cw = true),
                Line(Pt(0.0, 10.3), Pt(0.0, 20.0)),
            ),
        )
        val p = NoseComp.tipPath(c, 0.4, Side.G42, 3)
        val w = p.warnings.single() as CompWarning.ArcTooSmall
        assertEquals(1, w.index)
        assertEquals(0.3, w.arcR, 1e-9)
        assertEquals(0.4, w.noseR)
        assertEquals(3, p.path.segs.size)
        assertPt(Pt(0.0, 10.0), p.path.segs[0].end)
        assertTrue(p.path.segs[1] is Line)
        assertPt(Pt(0.0, 10.0), p.path.segs[1].start)
        assertPt(Pt(0.0, 10.0), p.path.segs[1].end)
        assertPt(Pt(0.0, 10.0), p.path.segs[2].start)
    }

    /** R = r 이면 공구가 꼭 맞게 들어간다 — 경고 없이 중심 경로의 원호가 한 점(원호 중심)이 된다 */
    @Test
    fun concaveArcEqualToNoseIsExact() {
        val c = Contour(
            listOf(
                Line(Pt(10.0, 10.0), Pt(0.4, 10.0)),
                Arc(Pt(0.4, 10.0), Pt(0.0, 10.4), Pt(0.4, 10.4), cw = true),
                Line(Pt(0.0, 10.4), Pt(0.0, 20.0)),
            ),
        )
        val center = NoseComp.centerPath(c, 0.4, Side.G42)
        assertTrue(center.warnings.isEmpty())
        val s = center.path.segs[1]
        assertTrue(s is Line)
        assertPt(Pt(0.4, 10.4), s.start)
        assertPt(Pt(0.4, 10.4), s.end)
    }

    /** 수평선이 볼록 원호에 꺾여 붙는 오목 모서리: 교점 = x = 10 + r 와 반지름 R + r 원의 교점 (손 계산) */
    @Test
    fun lineMeetsArcAtCorner() {
        val o = Pt(-5.0, 7.0)
        val bigR = sqrt(34.0)
        val c = Contour(
            listOf(
                Line(Pt(5.0, 10.0), Pt(0.0, 10.0)),
                Arc(Pt(0.0, 10.0), Pt(-5.0, 7.0 + bigR), o, cw = false),
            ),
        )
        val r = 0.4
        val center = NoseComp.centerPath(c, r, Side.G42).path.segs
        val rr = bigR + r
        val expected = Pt(-5.0 + sqrt(rr * rr - (3 + r) * (3 + r)), 10 + r)
        assertPt(expected, center[0].end)
        assertPt(expected, center[1].start)
        assertPt(Pt(-5.0, 7.0 + rr), center[1].end)
    }

    /** 바닥이 짧은 V 홈: 노즈 0.8 이 바닥에 못 닿는다 → 바닥은 경고와 함께 사라지고 두 벽의 오프셋 교점으로 잇는다 */
    @Test
    fun narrowVGrooveBottomUnreachable() {
        val c = Contour.polyline(
            Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(-2.0, 8.0), Pt(-2.2, 8.0), Pt(-4.2, 10.0), Pt(-9.0, 10.0),
        )
        val r = 0.8
        val p = NoseComp.centerPath(c, r, Side.G42)
        assertEquals(listOf<CompWarning>(CompWarning.Unreachable(2)), p.warnings)
        // 벽 x − z = 10 에서 r 떨어진, 홈 가운데 z = −2.1 위의 점
        val j = Pt(-2.1, 10 - 2.1 + r * sqrt(2.0))
        val s = p.path.segs
        assertEquals(5, s.size)
        assertPt(j, s[1].end)
        assertPt(j, s[2].start)
        assertPt(j, s[2].end)
        assertPt(j, s[3].start)
    }

    /** 폭 0.8 홈에 노즈 0.4: 바닥 중심 경로가 한 점으로 줄어든다 — 경고 없음 */
    @Test
    fun grooveExactlyTwiceNose() {
        val c = Contour.polyline(
            Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(0.0, 8.0), Pt(-0.8, 8.0), Pt(-0.8, 10.0), Pt(-5.0, 10.0),
        )
        val p = NoseComp.centerPath(c, 0.4, Side.G42)
        assertTrue(p.warnings.isEmpty())
        assertPt(Pt(-0.4, 8.4), p.path.segs[2].start)
        assertPt(Pt(-0.4, 8.4), p.path.segs[2].end)
    }

    /** 바닥이 반지름 = 노즈R 인 반원 홈: 벽 오프셋이 한 직선으로 겹치고 바닥 원호는 한 점 */
    @Test
    fun fullRadiusGroove() {
        val c = Contour(
            listOf(
                Line(Pt(5.0, 10.0), Pt(0.0, 10.0)),
                Line(Pt(0.0, 10.0), Pt(0.0, 8.4)),
                Arc(Pt(0.0, 8.4), Pt(-0.8, 8.4), Pt(-0.4, 8.4), cw = true),
                Line(Pt(-0.8, 8.4), Pt(-0.8, 10.0)),
                Line(Pt(-0.8, 10.0), Pt(-5.0, 10.0)),
            ),
        )
        val p = NoseComp.centerPath(c, 0.4, Side.G42)
        assertTrue(p.warnings.isEmpty())
        val s = p.path.segs
        assertPt(Pt(-0.4, 10.4), s[1].start)
        assertPt(Pt(-0.4, 8.4), s[1].end)
        assertPt(Pt(-0.4, 8.4), s[2].end)
        assertPt(Pt(-0.4, 10.4), s[3].end)
    }

    /**
     * 홈 폭 0.5 < 2r: 공구가 홈에 못 들어간다. 두 벽·바닥은 어느 점에 접한 원도 맞은편 벽과 겹치니 못닿음 경고,
     * 홈 양옆 윗면은 한 직선이라 그대로 이어 공구는 홈 위를 지나간다.
     * (전에는 두 벽의 보정선이 평행해 잇지 못하고 던졌다. 계산기는 어느 쪽이든 NOSE_UNREACHABLE 로 멈춘다)
     */
    @Test
    fun grooveNarrowerThanToolIsUnreachable() {
        val c = Contour.polyline(
            Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(0.0, 8.0), Pt(-0.5, 8.0), Pt(-0.5, 10.0), Pt(-5.0, 10.0),
        )
        val p = NoseComp.centerPath(c, 0.4, Side.G42)
        assertEquals((1..3).map { CompWarning.Unreachable(it) }, p.warnings)
        val s = p.path.segs
        val mid = Pt(-0.25, 10.4)
        assertPt(Pt(5.0, 10.4), s[0].start)
        for (k in 0..4) {
            if (k > 0) assertPt(mid, s[k].start)
            if (k < 4) assertPt(mid, s[k].end)
        }
        assertPt(Pt(-5.0, 10.4), s[4].end)
    }

    /** 제자리로 되돌아가는 윤곽: 꺾인 곳의 두 보정선이 평행하게 떨어져 이을 수 없다 — 숫자를 내지 않는다 */
    @Test
    fun contourTurningBackFails() {
        val c = Contour.polyline(Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(3.0, 10.0))
        assertFailsWith<IllegalArgumentException> { NoseComp.centerPath(c, 0.4, Side.G42) }
    }

    /**
     * 단면을 올라가 날카로운 모서리에서 바로 오목 원호(R4.03)로 내려가는 모양. 그 모서리는 볼록이라 공구는 꼭짓점을 돌아
     * 넘어가야 하는데, 단면 보정선(z = −3 + r)과 줄인 원(R − r, 중심 z −6.5)이 만나지 않는다. 요소마다 한 자리이고 볼록 모서리는
     * 날카롭게 내는 이 꼴로는 그 원호를 넣을 데가 없어 던진다.
     * (전에는 닿을 수 있는 원호를 못닿음으로 빼고 단면과 마지막 직선을 바로 이어 모서리를 1 mm 가까이 파고들었다)
     */
    @Test
    fun convexCornerIntoConcaveArcFails() {
        val c = Contour(
            listOf(
                Line(Pt(0.0, 10.0), Pt(-3.0, 10.0)),
                Line(Pt(-3.0, 10.0), Pt(-3.0, 13.0)),
                Arc(Pt(-3.0, 13.0), Pt(-6.0, 11.0), Pt(-6.5, 15.0), cw = true),
                Line(Pt(-6.0, 11.0), Pt(-10.0, 11.0)),
            ),
        )
        for (r in listOf(0.4, 0.8)) assertFailsWith<IllegalArgumentException>("r$r") { NoseComp.centerPath(c, r, Side.G42) }
    }

    /**
     * 볼록 모서리 T1 과 오목 골 T2(공구 쪽 사이각 74.3°) 사이의 짧은 경사(1.221)에 r 1.2. 두 경사에 함께 접한 원은 첫 경사 위
     * T2 에서 1.2/tan(37.15°) = 1.58 떨어진 곳에 닿아 경사 길이를 넘으니 첫 경사는 어디서도 깎이지 않는다.
     * (전에는 볼록 쪽 연장 교점이 꼭짓점 바깥에 생겨 잘린 경사가 뒤집히지 않아 못 알아챘다)
     * 앞 직선은 T1 까지 다 깎이고, 공구는 T1 을 돌아(빠진 경사 자리의 원호) 둘째 경사에 닿는다.
     */
    @Test
    fun shortSlopeBetweenConvexAndConcaveIsUnreachable() {
        val r = 1.2
        val t1 = Pt(0.0, 25.0)
        val t2 = Pt(-1.0, 24.3)
        val t3 = Pt(-3.0, 30.0)
        val c = Contour.polyline(Pt(5.0, 25.0), t1, t2, t3, Pt(-8.0, 30.0))
        val p = NoseComp.centerPath(c, r, Side.G42)
        assertEquals(listOf<CompWarning>(CompWarning.Unreachable(1)), p.warnings)
        val s = p.path.segs
        assertPt(Pt(0.0, 25.0 + r), s[0].end)
        val link = s[1] as Arc
        assertPt(t1, link.center)
        assertEquals(false, link.cw)
        // 원호 끝 = T1 에서 r, 둘째 경사 직선에서 r 인 점(T1 을 감싼 원이 둘째 경사 보정선과 만나는 곳)
        val q = link.end
        assertEquals(r, (q - t1).length, 1e-9)
        assertEquals(r, distToLine(q, Line(t2, t3)), 1e-9)
        assertPt(q, s[2].start)
    }

    /**
     * 높은 단면(1번)을 내려가 폭이 위에서 0.2 뿐인 틈(2번 벽이 기울어 올라감)을 지나 바닥(3번)으로. r 0.5 는 틈에 못 들어가니
     * 2번만 못닿음이고, 1번은 바닥 보정선 x = 16.5 까지 깎여 두 보정선의 교점 (−0.5, 16.5) 에서 바닥과 만난다.
     * (전에는 2번의 끝없는 보정선이 1번 위쪽에서 1번을 잘라 1번부터 빼고 줄줄이 3번까지 뺐다)
     */
    @Test
    fun onlyTheNarrowElementIsDropped() {
        val c = Contour.polyline(Pt(5.0, 20.0), Pt(0.0, 20.0), Pt(0.0, 14.0), Pt(-0.2, 16.0), Pt(-5.0, 16.0))
        val p = NoseComp.centerPath(c, 0.5, Side.G42)
        assertEquals(listOf<CompWarning>(CompWarning.Unreachable(2)), p.warnings)
        val s = p.path.segs
        val j = Pt(-0.5, 16.5)
        assertPt(Pt(-0.5, 20.5), s[1].start)
        assertPt(j, s[1].end)
        assertPt(j, s[2].start)
        assertPt(j, s[2].end)
        assertPt(j, s[3].start)
        assertPt(Pt(-5.0, 16.5), s[3].end)
    }

    /** 섞인 윤곽(꺾인 선-원, 원-원 이음 포함)에서 중심 경로의 모든 요소가 원래 요소와 정확히 r 떨어졌는지 */
    @Test
    fun centerPathStaysAtNoseRadius() {
        val r = 0.5
        val o1 = Pt(-4.0, 10.0)
        val r1 = 5.0
        val p1 = Pt(-4.0 + r1 * kotlin.math.cos(rad(60.0)), 10 + r1 * kotlin.math.sin(rad(60.0)))
        val o2 = Pt(-9.0, 18.0)
        val r2 = (p1 - o2).length
        val p2 = Pt(-9.0, 18.0 - r2)
        val c = Contour(
            listOf(
                Line(Pt(5.0, 12.0), Pt(1.0, 10.0)), // 볼록 원호에 꺾여 붙음
                Arc(Pt(1.0, 10.0), p1, o1, cw = false),
                Arc(p1, p2, o2, cw = true), // 원-원 이음(접하지 않음)
                Line(p2, Pt(-15.0, p2.x)),
            ),
        )
        val p = NoseComp.centerPath(c, r, Side.G42)
        assertTrue(p.warnings.isEmpty())
        val s = p.path.segs
        for ((orig, off) in c.segs.zip(s)) {
            when (orig) {
                is Line -> for (q in listOf(off.start, off.end)) assertEquals(r, distToLine(q, orig), 1e-9)
                is Arc -> {
                    off as Arc
                    assertPt(orig.center, off.center)
                    val want = if (orig.cw) orig.radius - r else orig.radius + r
                    assertEquals(want, off.radius, 1e-9)
                    assertEquals(want, (off.end - off.center).length, 1e-9)
                }
            }
        }
        for ((a, b) in s.zipWithNext()) assertPt(a.end, b.start)
    }

    /**
     * 교점 계산과 따로, 무식하게 확인: 중심 경로를 잘게 따라가며 노즈 원이 도면 윤곽을 파고들지 않는지(거리 ≥ r),
     * 그리고 깎아야 할 요소마다 한 번은 닿는지(거리 = r).
     */
    @Test
    fun bruteForceNoGougeAndContact() {
        val shapes = listOf(
            Triple("면취 외경", Contour.polyline(Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(-3.0, 13.0), Pt(-9.0, 13.0)), Side.G42),
            Triple("면취 내경", Contour.polyline(Pt(5.0, 20.0), Pt(0.0, 20.0), Pt(-3.0, 17.0), Pt(-9.0, 17.0)), Side.G41),
            Triple("V 홈", Contour.polyline(Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(-2.0, 8.0), Pt(-2.2, 8.0), Pt(-4.2, 10.0), Pt(-9.0, 10.0)), Side.G42),
            Triple(
                "볼록·오목 R",
                Contour(
                    listOf(
                        Line(Pt(5.0, 10.0), Pt(0.0, 10.0)),
                        Arc(Pt(0.0, 10.0), Pt(-2.0, 12.0), Pt(-2.0, 10.0), cw = false),
                        Arc(Pt(-2.0, 12.0), Pt(-5.0, 15.0), Pt(-2.0, 15.0), cw = true),
                        Line(Pt(-5.0, 15.0), Pt(-9.0, 15.0)),
                    ),
                ),
                Side.G42,
            ),
            Triple(
                "어깨 R0.3",
                Contour(
                    listOf(
                        Line(Pt(10.0, 10.0), Pt(0.3, 10.0)),
                        Arc(Pt(0.3, 10.0), Pt(0.0, 10.3), Pt(0.3, 10.3), cw = true),
                        Line(Pt(0.0, 10.3), Pt(0.0, 20.0)),
                    ),
                ),
                Side.G42,
            ),
        )
        for ((name, c, side) in shapes) for (r in listOf(0.2, 0.8)) {
            val p = NoseComp.centerPath(c, r, side)
            val samples = p.path.segs.flatMap { samplePoints(it) }
            for (q in samples) {
                val d = c.segs.minOf { distToSeg(q, it) }
                assertTrue(d >= r - 1e-9, "$name r$r: $q 에서 윤곽까지 $d < r (파고듦)")
            }
            val skipped = p.warnings.map { it.index }.toSet()
            for ((i, seg) in c.segs.withIndex()) {
                if (i in skipped) continue
                val closest = samples.minOf { distToSeg(it, seg) }
                assertEquals(r, closest, 1e-9, "$name r$r: ${i}번 요소에 안 닿음")
            }
        }
    }

    private fun samplePoints(s: Seg): List<Pt> = when (s) {
        is Line -> (0..100).map { s.start + (s.end - s.start) * (it / 100.0) }
        is Arc -> {
            val a0 = angleOf(s.start - s.center)
            val sw = s.sweepRad() * if (s.cw) -1 else 1
            (0..100).map {
                val a = a0 + sw * it / 100.0
                s.center + Pt(kotlin.math.cos(a), kotlin.math.sin(a)) * s.radius
            }
        }
    }

    private fun distToSeg(q: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val t = ((q - s.start) dot d) / (d dot d)
            (q - (s.start + d * t.coerceIn(0.0, 1.0))).length
        }
        is Arc -> {
            val a = angleOf(q - s.center) - angleOf(s.start - s.center)
            val along = wrap2Pi(if (s.cw) -a else a)
            if (along <= s.sweepRad()) abs((q - s.center).length - s.radius)
            else minOf((q - s.start).length, (q - s.end).length)
        }
    }

    private fun distToLine(q: Pt, l: Line): Double {
        val d = l.end - l.start
        return abs(d cross (q - l.start)) / d.length
    }
}
