package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.tan

/** 노즈R 보정 기본식. 각도는 Z 축에서 잰 테이퍼 각(도). */
object NoseR {
    /** Z 방향 보정량: r(1 − tan(θ/2)) */
    fun taperDeltaZ(r: Double, angleDeg: Double): Double = r * (1 - tan(rad(angleDeg) / 2))

    /** X 방향 보정량(직경값): 2r(1 − tan((90° − θ)/2)) */
    fun taperDeltaXDia(r: Double, angleDeg: Double): Double = 2 * r * (1 - tan(rad(90 - angleDeg) / 2))

    private fun rad(deg: Double) = deg * PI / 180
}
