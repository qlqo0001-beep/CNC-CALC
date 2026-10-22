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

/** 직각삼각형 화면 상태 → 계산기 입력, 처음 빈칸, X·Z 배치, 짚을 칸, Saver */
class TriangleStateTest {
    private val rad25 = 25 * PI / 180
    private val example = mapOf("a" to "10", "angA" to "25")

    /** 처음엔 모두 빈칸 → 값 부족(안내), 다섯 칸 모두 모자란 칸이고 빨강으로 짚을 칸은 없다 */
    @Test
    fun startsEmpty() {
        val s = TriangleState()
        assertFalse(TriField.entries.any { s.given(it) })
        val w = RightTriangle.calc(s.input()).warnings.single()
        assertEquals(TriangleWarning.MISSING_INPUT, w)
        assertEquals(TriField.entries.toSet(), s.need(w))
        assertEquals(emptySet(), s.wrong(w))
    }

    /** 밑변 10 · A 25° → 높이 10·tan25°, 빗변 10/cos25°, Z = 밑변, X 직경 = 높이 × 2 */
    @Test
    fun base10Angle25() {
        val sol = RightTriangle.calc(TriangleState(example).input()).solution!!
        assertEquals(10 * tan(rad25), sol.b, 1e-9)
        assertEquals(10 / cos(rad25), sol.c, 1e-9)
        assertEquals(65.0, sol.angleB, 1e-9)
        assertEquals(10.0, sol.dz, 1e-9)
        assertEquals(20 * tan(rad25), sol.dxDia, 1e-9)
    }

    /** 반대 배치: Z = 높이, X 직경 = 밑변 × 2 */
    @Test
    fun swapped() {
        val s = TriangleState(example, baseIsZ = false)
        val sol = RightTriangle.calc(s.input()).solution!!
        assertEquals(10 * tan(rad25), sol.dz, 1e-9)
        assertEquals(20.0, sol.dxDia, 1e-9)
    }

    /** 각도 둘뿐 → 변 셋이 모자라고 빨강. 하나만 넣으면 넣지 않은 넷이 모자란 칸 */
    @Test
    fun clearAndAnglesOnly() {
        val s = TriangleState(example)
        s.clear()
        assertEquals(listOf(TriangleWarning.MISSING_INPUT), RightTriangle.calc(s.input()).warnings)
        assertFalse(TriField.entries.any { s.given(it) })
        s.set(TriField.ANGLE_A, "30")
        assertEquals(TriField.entries.toSet() - TriField.ANGLE_A, s.need(TriangleWarning.MISSING_INPUT))
        s.set(TriField.ANGLE_B, "60")
        val w = RightTriangle.calc(s.input()).warnings.single()
        assertEquals(TriangleWarning.ANGLES_ONLY, w)
        val sides = setOf(TriField.A_SIDE, TriField.B_SIDE, TriField.C_SIDE)
        assertEquals(sides, s.need(w))
        assertEquals(sides, s.wrong(w))
    }

    @Test
    fun stateSurvivesSaver() {
        val s = TriangleState(example, baseIsZ = false)
        s.set(TriField.A_SIDE, "")
        s.set(TriField.C_SIDE, "13")
        s.set(TriField.B_SIDE, "5")
        val back = with(TriangleState.Saver) { restore(SaverScope { true }.save(s)!!)!! }
        assertFalse(back.baseIsZ)
        assertEquals("", back.value(TriField.A_SIDE))
        assertEquals("5", back.value(TriField.B_SIDE))
        assertEquals("13", back.value(TriField.C_SIDE))
        assertEquals("25", back.value(TriField.ANGLE_A))
        // 셋(b · c · A)을 넣었는데 서로 안 맞으면 멈추고, 넣은 셋을 빨강으로 짚는다
        val w = RightTriangle.calc(back.input()).warnings.single()
        assertEquals(TriangleWarning.CONTRADICTION, w)
        assertEquals(setOf(TriField.B_SIDE, TriField.C_SIDE, TriField.ANGLE_A), back.wrong(w))
        assertEquals(emptySet(), back.need(w))
        back.set(TriField.ANGLE_A, "")
        assertEquals(12.0, RightTriangle.calc(back.input()).solution!!.a, 1e-9)
    }
}
