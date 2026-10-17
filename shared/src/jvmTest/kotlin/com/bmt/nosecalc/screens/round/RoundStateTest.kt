package com.bmt.nosecalc.screens.round

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Arc
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 라운드 화면 상태 → 계산기 입력, 처음 값, 그림 견본, Saver */
class RoundStateTest {
    private val numbersOnly = setOf(RoundWarning.CONCAVE_R_LE_NOSE, RoundWarning.AGAINST_TOOL)

    @Test
    fun defaultsSolveEveryType() {
        val s = RoundState()
        for (t in 1..6) {
            val r = RoundCalc.calc(s.input(t))
            assertNotNull(r.solution, "형상 $t: ${r.warnings}")
            assertTrue(numbersOnly.containsAll(r.warnings), "형상 $t: ${r.warnings}")
        }
        val m = RoundCalc.arc3(s.arc3())
        assertNotNull(m.solution, "수동: ${m.warnings}")
    }

    /** calc-design.md 의 손 계산(Ø20, SR12.5, CR1, 0.4R, 팁 3)이 처음 값의 형상 3 그대로 나온다. 비운 L 은 5 로 채워진다 */
    @Test
    fun type3DefaultIsHandCalc() {
        val sol = RoundCalc.calc(RoundState().input(3)).solution!!
        assertEquals(listOf("G00 X-0.8 Z1.", "G01 Z0.", "G03 X19.391 Z-4.87 R12.9", "G03 X20. Z-5.741 R1.4", "G01 Z-10.4"), sol.nc)
        assertEquals(5.0, sol.filled!!.l1!!, 1e-9)
    }

    /** 수동 처음 값: 어깨의 볼록 R10(X30 Z0 → X50 Z−10), G03, 90°. 노즈 0.4 팁 3 → 첫 점 X29.2 Z0, 끝 X50 Z−10.4, R10.4 */
    @Test
    fun manualDefault() {
        val sol = RoundCalc.arc3(RoundState().arc3()).solution!!
        val a = sol.arcs.single()
        assertEquals(10.0, a.r, 1e-3)
        assertEquals(10.4, a.progR!!, 1e-3)
        assertTrue(!a.cw)
        assertEquals(90.0, (sol.drawing.segs[a.index] as Arc).sweepDeg, 1e-3)
        assertEquals(29.2, sol.nodes.first().prog.xDia, 1e-3)
        assertEquals(-10.4, sol.nodes.last().prog.z, 1e-3)
        assertEquals(3, RoundState().manualStandardTip())
    }

    /** 그 형상의 칸만 계산기로 간다(다른 형상에서 넣은 값이 섞이지 않게). 노즈R 빈칸은 BAD_INPUT */
    @Test
    fun inputTakesOnlyThatTypesFields() {
        val s = RoundState()
        s.setDim(5, "d2", "30")
        val i = s.input(3)
        assertNull(i.d2)
        assertNull(i.cornerR2)
        assertNull(i.l2)
        assertEquals(20.0, i.d1)
        s.setShared("noseR", "")
        assertEquals(listOf(RoundWarning.BAD_INPUT), RoundCalc.calc(s.input(3)).warnings)
    }

    /** 그림은 견본 치수로 만든 윤곽을 그린다 — 풀리지 않으면 카드가 빈다 */
    @Test
    fun artSamplesSolve() {
        for (t in 1..6) assertNotNull(RoundCalc.calc(sampleInput(t)).solution, "형상 $t 그림 견본")
    }

    @Test
    fun stateSurvivesSaver() {
        val s = RoundState(type = 2, open = true, comp = false)
        s.setDim(2, "l2", "12")
        s.l2Ref = L2Ref.A
        s.tip = 2
        s.inner = true
        s.setPoint("x1", "41.5")
        val back = with(RoundState.Saver) { restore(SaverScope { true }.save(s)!!)!! }
        assertEquals(2, back.type)
        assertTrue(back.open && !back.comp)
        assertEquals("12", back.dim(2, "l2"))
        assertEquals(L2Ref.A, back.l2Ref)
        assertEquals(2, back.tip)
        assertTrue(back.inner)
        assertEquals("41.5", back.point("x1"))
        back.tip = null
        assertNull(back.tip)
        assertTrue(abs(back.input(2).noseR - 0.4) < 1e-12)
    }
}
