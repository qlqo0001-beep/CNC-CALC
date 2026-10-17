package com.bmt.nosecalc.calc

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

fun assertPt(expected: Pt, actual: Pt, tol: Double = 1e-9, msg: String = "") {
    assertTrue(
        (expected - actual).length <= tol,
        "$msg 기대 $expected, 실제 $actual (차이 ${(expected - actual).length})",
    )
}

private fun assertPts(expected: List<Pt>, actual: List<Pt>) {
    assertEquals(expected.size, actual.size, "교점 개수: $actual")
    for (e in expected) assertTrue(actual.any { (it - e).length <= 1e-9 }, "$e 가 $actual 에 없다")
}

class GeomTest {
    @Test
    fun lineLine() {
        assertPt(Pt(1.0, 1.0), Intersect.lineLine(Pt(0.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 2.0), Pt(1.0, -1.0))!!)
        // 방향 벡터 길이와 무관
        assertPt(Pt(1.0, 1.0), Intersect.lineLine(Pt(0.0, 0.0), Pt(5.0, 5.0), Pt(0.0, 2.0), Pt(0.1, -0.1))!!)
        assertNull(Intersect.lineLine(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(0.0, 1.0), Pt(-2.0, 0.0)))
    }

    @Test
    fun lineCircle() {
        // x = 3 인 직선과 중심 (0,0) 반지름 5 → z = ±4
        assertPts(listOf(Pt(-4.0, 3.0), Pt(4.0, 3.0)), Intersect.lineCircle(Pt(-10.0, 3.0), Pt(1.0, 0.0), Pt(0.0, 0.0), 5.0))
        assertPts(listOf(Pt(0.0, 5.0)), Intersect.lineCircle(Pt(-10.0, 5.0), Pt(2.0, 0.0), Pt(0.0, 0.0), 5.0))
        assertPts(emptyList(), Intersect.lineCircle(Pt(-10.0, 6.0), Pt(1.0, 0.0), Pt(0.0, 0.0), 5.0))
        // 비스듬한 직선: z = x 와 중심 (0,0) 반지름 √2 → (±1, ±1)
        assertPts(listOf(Pt(1.0, 1.0), Pt(-1.0, -1.0)), Intersect.lineCircle(Pt(3.0, 3.0), Pt(-1.0, -1.0), Pt(0.0, 0.0), sqrt(2.0)))
    }

    @Test
    fun circleCircle() {
        assertPts(listOf(Pt(4.0, 3.0), Pt(4.0, -3.0)), Intersect.circleCircle(Pt(0.0, 0.0), 5.0, Pt(8.0, 0.0), 5.0))
        assertPts(listOf(Pt(2.0, 0.0)), Intersect.circleCircle(Pt(0.0, 0.0), 2.0, Pt(5.0, 0.0), 3.0)) // 밖에서 접함
        assertPts(listOf(Pt(5.0, 0.0)), Intersect.circleCircle(Pt(0.0, 0.0), 5.0, Pt(3.0, 0.0), 2.0)) // 안에서 접함
        assertPts(emptyList(), Intersect.circleCircle(Pt(0.0, 0.0), 1.0, Pt(5.0, 0.0), 1.0)) // 떨어짐
        assertPts(emptyList(), Intersect.circleCircle(Pt(0.0, 0.0), 5.0, Pt(1.0, 0.0), 1.0)) // 안에 들어감
        assertPts(emptyList(), Intersect.circleCircle(Pt(1.0, 1.0), 5.0, Pt(1.0, 1.0), 5.0)) // 같은 중심
    }

    @Test
    fun diameterConversion() {
        assertEquals(Pt(-2.0, 17.0), Pt.ofDia(-2.0, 34.0))
        assertEquals(34.0, Pt(0.0, 17.0).xDia)
    }

    /**
     * Haas ST/TL 프로그래밍 워크북 43~44쪽 원호 예제(인치).
     * N7 X1.5 → N8 G03 X2. Z-0.25 R0.25 (끝단 볼록 R) → N9 G01 Z-1. → N10 G02 X5. Z-2.5 I1.5 K0. (안쪽 R)
     * 두 원호 모두 1/4 원이다. G03 = 반시계(cw=false), G02 = 시계(cw=true) 로 두면 90° 가 나와야 한다.
     */
    @Test
    fun arcDirectionMatchesHaasExample() {
        val n8 = Arc(Pt(0.0, 0.75), Pt(-0.25, 1.0), Pt(-0.25, 0.75), cw = false)
        val n10 = Arc(Pt(-1.0, 1.0), Pt(-2.5, 2.5), Pt(-1.0, 2.5), cw = true)
        assertEquals(90.0, n8.sweepDeg, 1e-9)
        assertEquals(90.0, n10.sweepDeg, 1e-9)
        assertEquals(270.0, n8.copy(cw = true).sweepDeg, 1e-9)
        assertEquals(0.25, n8.radius, 1e-12)
        // 반원
        assertEquals(180.0, Arc(Pt(1.0, 0.0), Pt(-1.0, 0.0), Pt(0.0, 0.0), cw = false).sweepDeg, 1e-9)
        assertEquals(180.0, Arc(Pt(1.0, 0.0), Pt(-1.0, 0.0), Pt(0.0, 0.0), cw = true).sweepDeg, 1e-9)
    }

    @Test
    fun contourMustBeContinuous() {
        assertFailsWith<IllegalArgumentException> {
            Contour(listOf(Line(Pt(0.0, 0.0), Pt(1.0, 0.0)), Line(Pt(1.1, 0.0), Pt(2.0, 0.0))))
        }
        assertEquals(2, Contour.polyline(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 1.0)).segs.size)
    }
}
