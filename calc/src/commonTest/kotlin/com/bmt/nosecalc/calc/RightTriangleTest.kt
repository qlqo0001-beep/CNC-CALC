package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RightTriangleTest {
    private fun ok(r: TriangleResult): TriangleSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        return r.solution
    }

    private fun stops(w: TriangleWarning, r: TriangleResult) {
        assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다")
        assertEquals(listOf(w), r.warnings)
    }

    private fun near(e: Double, a: Double, what: String) = assertTrue(abs(e - a) <= 1e-9, "$what: 기대 $e, 실제 $a")

    /** 각 A 와 빗변으로 만든 정답(계산기와 따로 sin·cos 로) */
    private fun truth(c: Double, aDeg: Double): Map<String, Double> {
        val t = aDeg * PI / 180
        return mapOf("a" to c * cos(t), "b" to c * sin(t), "c" to c, "A" to aDeg, "B" to 90 - aDeg)
    }

    private fun input(k: Map<String, Double?>, baseIsZ: Boolean = true) =
        TriangleInput(k["a"], k["b"], k["c"], k["A"], k["B"], baseIsZ)

    /** 다섯 값 가운데 둘(각도 둘만 빼고) → 나머지 전부. 여러 크기·각도에서 */
    @Test
    fun everyPair() {
        val names = listOf("a", "b", "c", "A", "B")
        for (c in listOf(0.5, 50.0, 1234.5)) {
            for (deg in listOf(1.0, 17.5, 30.0, 45.0, 53.130102354, 60.0, 89.0)) {
                val t = truth(c, deg)
                for (p in names.indices) for (q in p + 1 until names.size) {
                    val given = mapOf(names[p] to t[names[p]], names[q] to t[names[q]])
                    val r = RightTriangle.calc(input(given))
                    if (names[p] == "A" && names[q] == "B") {
                        stops(TriangleWarning.ANGLES_ONLY, r)
                        continue
                    }
                    val s = ok(r)
                    val tag = "${names[p]},${names[q]} c=$c A=$deg"
                    // 길이는 크기에 비례해 오차가 커지므로 상대 오차로
                    assertTrue(abs(s.a - t["a"]!!) <= 1e-12 * c + 1e-12, "$tag a ${s.a}")
                    assertTrue(abs(s.b - t["b"]!!) <= 1e-12 * c + 1e-12, "$tag b ${s.b}")
                    assertTrue(abs(s.c - c) <= 1e-12 * c + 1e-12, "$tag c ${s.c}")
                    assertTrue(abs(s.angleA - deg) <= 1e-9, "$tag A ${s.angleA}")
                    assertTrue(abs(s.angleB - (90 - deg)) <= 1e-9, "$tag B ${s.angleB}")
                }
            }
        }
    }

    /** 손 계산: 3-4-5 */
    @Test
    fun threeFourFive() {
        val s = ok(RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0)))
        near(50.0, s.c, "c")
        near(53.13010235415598, s.angleA, "A")
        near(36.86989764584402, s.angleB, "B")
        val t = ok(RightTriangle.calc(TriangleInput(c = 10.0, angleB = 60.0)))
        near(5.0, t.b, "b = c cos B")
        near(8.660254037844386, t.a, "a = c sin B")
    }

    /** 밑변 = Z, 높이 = X 반경 → X 는 직경(2 × 높이). 반대 배치면 높이 = Z, 밑변 = X 반경 */
    @Test
    fun coordinates() {
        val s = ok(RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0)))
        near(30.0, s.dz, "Z")
        near(80.0, s.dxDia, "X 직경")
        val t = ok(RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0, baseIsZ = false)))
        near(40.0, t.dz, "Z(반대)")
        near(60.0, t.dxDia, "X 직경(반대)")
    }

    /** 셋 이상 넣으면 맞는지 본다(길이 0.001 mm, 각 0.001° 까지 봐준다) */
    @Test
    fun overSpecified() {
        ok(RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0, c = 50.0, angleA = 53.13, angleB = 36.87)))
        ok(RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0, c = 50.0004)))
        ok(RightTriangle.calc(TriangleInput(c = 10.0, angleA = 30.0, angleB = 60.0005)))
        stops(TriangleWarning.CONTRADICTION, RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0, c = 50.01)))
        stops(TriangleWarning.CONTRADICTION, RightTriangle.calc(TriangleInput(a = 30.0, b = 40.0, angleA = 53.2)))
        stops(TriangleWarning.CONTRADICTION, RightTriangle.calc(TriangleInput(c = 10.0, angleA = 30.0, angleB = 61.0)))
        stops(TriangleWarning.CONTRADICTION, RightTriangle.calc(TriangleInput(a = 30.0, angleA = 45.0, angleB = 50.0)))
    }

    @Test
    fun badInputs() {
        stops(TriangleWarning.MISSING_INPUT, RightTriangle.calc(TriangleInput()))
        stops(TriangleWarning.MISSING_INPUT, RightTriangle.calc(TriangleInput(a = 3.0)))
        stops(TriangleWarning.ANGLES_ONLY, RightTriangle.calc(TriangleInput(angleA = 30.0, angleB = 60.0)))
        stops(TriangleWarning.HYPOTENUSE_TOO_SHORT, RightTriangle.calc(TriangleInput(a = 5.0, c = 5.0)))
        stops(TriangleWarning.HYPOTENUSE_TOO_SHORT, RightTriangle.calc(TriangleInput(b = 6.0, c = 5.0)))
        stops(TriangleWarning.HYPOTENUSE_TOO_SHORT, RightTriangle.calc(TriangleInput(a = 3.0, b = 4.0, c = 4.0)))
        stops(TriangleWarning.BAD_ANGLE, RightTriangle.calc(TriangleInput(a = 3.0, angleA = 0.0)))
        stops(TriangleWarning.BAD_ANGLE, RightTriangle.calc(TriangleInput(a = 3.0, angleA = 90.0)))
        stops(TriangleWarning.BAD_ANGLE, RightTriangle.calc(TriangleInput(c = 3.0, angleB = 95.0)))
        stops(TriangleWarning.BAD_ANGLE, RightTriangle.calc(TriangleInput(c = 3.0, angleB = -5.0)))
        stops(TriangleWarning.BAD_INPUT, RightTriangle.calc(TriangleInput(a = 0.0, b = 4.0)))
        stops(TriangleWarning.BAD_INPUT, RightTriangle.calc(TriangleInput(a = -3.0, b = 4.0)))
        stops(TriangleWarning.BAD_INPUT, RightTriangle.calc(TriangleInput(a = Double.NaN, b = 4.0)))
        stops(TriangleWarning.BAD_INPUT, RightTriangle.calc(TriangleInput(a = 3.0, b = Double.POSITIVE_INFINITY)))
    }
}
