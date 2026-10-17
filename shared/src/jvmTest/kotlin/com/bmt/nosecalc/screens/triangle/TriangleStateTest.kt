package com.bmt.nosecalc.screens.triangle

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.TriangleWarning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 직각삼각형 화면 상태 → 계산기 입력, 처음 값, X·Z 배치, Saver */
class TriangleStateTest {
    private val rad25 = 25 * PI / 180

    /** 처음 값 밑변 10 · A 25° → 높이 10·tan25°, 빗변 10/cos25°, Z = 밑변, X 직경 = 높이 × 2 */
    @Test
    fun defaults() {
        val sol = RightTriangle.calc(TriangleState().input()).solution!!
        assertEquals(10 * tan(rad25), sol.b, 1e-9)
        assertEquals(10 / cos(rad25), sol.c, 1e-9)
        assertEquals(65.0, sol.angleB, 1e-9)
        assertEquals(10.0, sol.dz, 1e-9)
        assertEquals(20 * tan(rad25), sol.dxDia, 1e-9)
        assertEquals(2, TriField.entries.count { TriangleState().given(it) })
    }

    /** 반대 배치: Z = 높이, X 직경 = 밑변 × 2 */
    @Test
    fun swapped() {
        val s = TriangleState(baseIsZ = false)
        val sol = RightTriangle.calc(s.input()).solution!!
        assertEquals(10 * tan(rad25), sol.dz, 1e-9)
        assertEquals(20.0, sol.dxDia, 1e-9)
    }

    @Test
    fun clearAndAnglesOnly() {
        val s = TriangleState()
        s.clear()
        assertEquals(listOf(TriangleWarning.MISSING_INPUT), RightTriangle.calc(s.input()).warnings)
        assertFalse(TriField.entries.any { s.given(it) })
        s.set(TriField.ANGLE_A, "30")
        s.set(TriField.ANGLE_B, "60")
        assertEquals(listOf(TriangleWarning.ANGLES_ONLY), RightTriangle.calc(s.input()).warnings)
    }

    @Test
    fun stateSurvivesSaver() {
        val s = TriangleState(baseIsZ = false)
        s.set(TriField.A_SIDE, "")
        s.set(TriField.C_SIDE, "13")
        s.set(TriField.B_SIDE, "5")
        val back = with(TriangleState.Saver) { restore(SaverScope { true }.save(s)!!)!! }
        assertFalse(back.baseIsZ)
        assertEquals("", back.value(TriField.A_SIDE))
        assertEquals("5", back.value(TriField.B_SIDE))
        assertEquals("13", back.value(TriField.C_SIDE))
        assertEquals("25", back.value(TriField.ANGLE_A))
        // 셋(b · c · A)을 넣었는데 서로 안 맞으면 멈춘다
        assertEquals(listOf(TriangleWarning.CONTRADICTION), RightTriangle.calc(back.input()).warnings)
        back.set(TriField.ANGLE_A, "")
        assertEquals(12.0, RightTriangle.calc(back.input()).solution!!.a, 1e-9)
    }
}
