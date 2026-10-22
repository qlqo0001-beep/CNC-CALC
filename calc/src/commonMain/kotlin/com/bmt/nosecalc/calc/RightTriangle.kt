package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 직각삼각형 경고. 모두 계산을 멈춘다([TriangleResult.solution] = null) */
enum class TriangleWarning {
    /** 값이 둘보다 적다 */
    MISSING_INPUT,

    /** 각도 둘만 넣었다 — 크기를 정할 수 없다 */
    ANGLES_ONLY,

    /** 길이 ≤ 0, 숫자가 아님, 변(넣은 값 · 계산값)이 [MAX_MM] 를 넘음 */
    BAD_INPUT,

    /** 각이 0° 이하·90° 이상 */
    BAD_ANGLE,

    /** 빗변이 밑변·높이보다 길지 않다 */
    HYPOTENUSE_TOO_SHORT,

    /** 셋 이상 넣었는데 서로 안 맞다(길이 0.001 mm, 각 0.001° 넘게) */
    CONTRADICTION,
}

/** 다섯 값 가운데 아는 둘(각도 둘만은 안 됨). 셋 이상 넣으면 맞는지 본다 */
data class TriangleInput(
    /** 밑변 */
    val a: Double? = null,
    /** 높이 */
    val b: Double? = null,
    /** 빗변 */
    val c: Double? = null,
    /** 밑변과 빗변 사이 각(°) */
    val angleA: Double? = null,
    /** 높이와 빗변 사이 각(°) = 90° − A */
    val angleB: Double? = null,
    /** true = 밑변이 Z, 높이가 X(반경). false = 높이가 Z, 밑변이 X(반경) */
    val baseIsZ: Boolean = true,
)

data class TriangleSolution(
    val a: Double,
    val b: Double,
    val c: Double,
    val angleA: Double,
    val angleB: Double,
    /** 좌표 연동: Z 거리 */
    val dz: Double,
    /** 좌표 연동: X 직경값(반경 × 2) */
    val dxDia: Double,
)

data class TriangleResult(val solution: TriangleSolution?, val warnings: List<TriangleWarning>)

object RightTriangle {
    private const val LEN_TOL = 1e-3
    private const val ANGLE_TOL = 1e-3

    fun calc(i: TriangleInput): TriangleResult {
        val sides = listOf(i.a, i.b, i.c)
        val angles = listOf(i.angleA, i.angleB)
        val given = (sides + angles).filterNotNull()
        if (given.any { !it.isFinite() } || sides.any { it != null && it <= 0 } || tooBig(i.a, i.b, i.c)) return stop(TriangleWarning.BAD_INPUT)
        if (angles.any { it != null && (it <= 0 || it >= 90) }) return stop(TriangleWarning.BAD_ANGLE)
        if (given.size < 2) return stop(TriangleWarning.MISSING_INPUT)
        if (sides.all { it == null }) return stop(TriangleWarning.ANGLES_ONLY)
        val c = i.c
        if (c != null && ((i.a ?: 0.0) >= c || (i.b ?: 0.0) >= c)) return stop(TriangleWarning.HYPOTENUSE_TOO_SHORT)

        // 변 둘이 있으면 변으로, 아니면 변 하나와 각으로 푼다. 넣은 나머지 값은 아래에서 맞춰 본다
        val (a, b) = when {
            i.a != null && i.b != null -> i.a to i.b
            i.a != null && c != null -> i.a to sqrt(c * c - i.a * i.a)
            i.b != null && c != null -> sqrt(c * c - i.b * i.b) to i.b
            else -> {
                val t = (i.angleA ?: (90 - i.angleB!!)) * PI / 180
                when {
                    i.a != null -> i.a to i.a * tan(t)
                    i.b != null -> i.b / tan(t) to i.b
                    else -> c!! * cos(t) to c * sin(t)
                }
            }
        }
        val hyp = hypot(a, b)
        // 각이 0° · 90° 에 아주 가까우면 계산된 변이 한도를 넘는다 — 큰 수가 Fmt 에서 잘려 틀린 값으로 보이지 않게 멈춘다.
        // 넣은 빗변은 위에서 봤다(다시 구한 hyp 는 부동소수 오차만큼 넘을 수 있어 한도 그대로인 빗변을 막지 않게)
        if (tooBig(a, b) || (c == null && tooBig(hyp))) return stop(TriangleWarning.BAD_INPUT)
        val degA = atan2(b, a) * 180 / PI
        val degB = 90 - degA
        val off = listOf(i.a to a, i.b to b, c to hyp).any { (g, v) -> g != null && abs(g - v) > LEN_TOL } ||
            listOf(i.angleA to degA, i.angleB to degB).any { (g, v) -> g != null && abs(g - v) > ANGLE_TOL }
        if (off) return stop(TriangleWarning.CONTRADICTION)
        val (z, x) = if (i.baseIsZ) a to b else b to a
        return TriangleResult(TriangleSolution(a, b, hyp, degA, degB, z, 2 * x), emptyList())
    }

    private fun stop(w: TriangleWarning) = TriangleResult(null, listOf(w))
}
