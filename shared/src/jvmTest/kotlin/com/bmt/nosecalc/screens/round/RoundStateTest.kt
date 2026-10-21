package com.bmt.nosecalc.screens.round

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.oneLine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 라운드 화면 상태 → 계산기 입력, 처음 값 · 예시 값, 경고 보이기, 그림 견본, Saver */
class RoundStateTest {
    private val numbersOnly = setOf(RoundWarning.CONCAVE_R_LE_NOSE, RoundWarning.AGAINST_TOOL)

    @Test
    fun examplesSolveEveryType() {
        val s = roundExample()
        for (t in 1..6) {
            val r = RoundCalc.calc(s.input(t))
            assertNotNull(r.solution, "형상 $t: ${r.warnings}")
            assertTrue(numbersOnly.containsAll(r.warnings), "형상 $t: ${r.warnings}")
        }
        val m = RoundCalc.arc3(s.arc3())
        assertNotNull(m.solution, "수동: ${m.warnings}")
    }

    /** 처음 값은 모두 0 — 형상마다 값 부족(파란 안내), 3점호는 세 점이 겹쳐 모양 불일치 대신 값 부족 */
    @Test
    fun freshStartIsMissingInput() {
        val s = RoundState()
        for (t in 1..6) {
            for (d in dims(t)) assertEquals("0", s.dim(t, d.key))
            assertEquals(listOf(RoundWarning.MISSING_INPUT), shownWarnings(RoundCalc.calc(s.input(t))), "형상 $t")
        }
        val input = s.arc3()
        assertEquals(listOf(RoundWarning.SHAPE_MISMATCH), RoundCalc.arc3(input).warnings)
        assertEquals(listOf(RoundWarning.MISSING_INPUT), shownWarnings(RoundCalc.arc3(input), input))
        assertEquals("0", s.shared("zClear"))
        assertEquals(0.0, s.input(3).zClear)
        assertEquals("0.4", s.shared("noseR"))
    }

    /** 값 부족 안내는 넣을 칸 이름을 든다: 꼭 넣을 칸 + 비울 수 있는 묶음에서 더 넣을 개수 */
    @Test
    fun missingTextNamesFields() {
        val s = RoundState()
        val empty = dims(2).map { it.key }.filter { s.input(2).dim(it) == null }.toSet()
        assertEquals(setOf("mainR", "d1", "l1", "l2"), empty)
        assertEquals("값을 넣으면 바로 계산합니다. 넣을 값: L1, 주 R · D1 · L2 가운데 둘.", roundWarningText(RoundWarning.MISSING_INPUT, 2, empty))
        assertEquals("값을 넣으면 바로 계산합니다. 넣을 값: 주 R · L.", roundWarningText(RoundWarning.MISSING_INPUT, 1, setOf("mainR", "l1")))
    }

    /** 길이 · 지름 · 주 R 의 0 은 빈칸과 같다(비울 수 있는 칸이면 자동). 모서리 R 빈칸은 0 */
    @Test
    fun zeroIsBlankAndBlankCornerIsZero() {
        val s = roundExample()
        s.setDim(3, "l1", "0")
        assertNull(s.input(3).l1)
        assertEquals(5.0, RoundCalc.calc(s.input(3)).solution!!.filled!!.l1!!, 1e-9)
        val blank = roundExample().apply { setDim(5, "cr1", "") }.input(5)
        assertEquals(0.0, blank.cornerR1)
        val r = RoundCalc.calc(blank)
        assertNotNull(r.solution)
        assertEquals(RoundCalc.calc(roundExample().apply { setDim(5, "cr1", "0") }.input(5)), r)
    }

    /** 빨간 칸: 모순은 비울 수 있는 묶음, 모서리 R 이 너무 크면 모서리 R 칸. 값 부족은 칸을 붉히지 않는다 */
    @Test
    fun warningFields() {
        assertEquals(setOf("mainR", "d1", "l2"), roundWarningFields(RoundWarning.CONTRADICTION, 2))
        assertEquals(setOf("cr1", "cr2"), roundWarningFields(RoundWarning.CORNER_R_TOO_BIG, 6))
        assertEquals(setOf("cr1"), roundWarningFields(RoundWarning.CORNER_R_TOO_BIG, 3))
        assertEquals(emptySet(), roundWarningFields(RoundWarning.MISSING_INPUT, 2))
        val r = RoundCalc.calc(roundExample().apply { setDim(2, "l2", "30") }.input(2))
        assertEquals(listOf(RoundWarning.CONTRADICTION), shownWarnings(r))
    }

    /** calc-design.md 의 손 계산(Ø20, SR12.5, CR1, 0.4R, 팁 3)이 예시 값의 형상 3 그대로 나온다. 비운 L 은 5 로 채워진다 */
    @Test
    fun type3ExampleIsHandCalc() {
        val sol = RoundCalc.calc(roundExample().input(3)).solution!!
        assertEquals(listOf("G00 X-0.8 Z1.", "G01 Z0.", "G03 X19.391 Z-4.87 R12.9", "G03 X20. Z-5.741 R1.4", "G01 Z-10.4"), sol.nc)
        assertEquals(5.0, sol.filled!!.l1!!, 1e-9)
    }

    /** 수동 예시 값: 어깨의 볼록 R10(X30 Z0 → X50 Z−10), G03, 90°. 노즈 0.4 팁 3 → 첫 점 X29.2 Z0, 끝 X50 Z−10.4, R10.4 */
    @Test
    fun manualExample() {
        val s = roundExample()
        val sol = RoundCalc.arc3(s.arc3()).solution!!
        val a = sol.arcs.single()
        assertEquals(10.0, a.r, 1e-3)
        assertEquals(10.4, a.progR!!, 1e-3)
        assertTrue(!a.cw)
        assertEquals(90.0, (sol.drawing.segs[a.index] as Arc).sweepDeg, 1e-3)
        assertEquals(29.2, sol.nodes.first().prog.xDia, 1e-3)
        assertEquals(-10.4, sol.nodes.last().prog.z, 1e-3)
        assertEquals(3, s.manualStandardTip())
        assertTrue(!s.manualReverse())
    }

    /** 그 형상의 칸만 계산기로 간다(다른 형상에서 넣은 값이 섞이지 않게). 노즈R 빈칸은 BAD_INPUT */
    @Test
    fun inputTakesOnlyThatTypesFields() {
        val s = roundExample()
        s.setDim(5, "d2", "30")
        val i = s.input(3)
        assertNull(i.d2)
        assertNull(i.cornerR2)
        assertNull(i.l2)
        assertEquals(20.0, i.d1)
        s.setShared("noseR", "")
        assertEquals(listOf(RoundWarning.BAD_INPUT), RoundCalc.calc(s.input(3)).warnings)
    }

    /** 결과 띠 · 키패드 한 줄: 지금 모드의 노드 좌표(처음 · 끝 · 외 n점), 멈추면 경고 첫 문장, 값이 모자라면 없음 */
    @Test
    fun dock() {
        val r = RoundCalc.calc(roundExample().input(1))
        val comp = roundDock(r.solution, shownWarnings(r), 1, comp = true, name = Color.Unspecified)
        // 형상 1 의 바이트 반대쪽 면은 파란 안내라 '확인 필요' 로 세지 않는다
        assertTrue(comp is DockStatus.Ok)
        assertEquals("P1 X-0.800 Z0.000 · P6 X-0.800 Z-12.800 · 외 4점", comp.oneLine())
        assertEquals("P1 X0.000 Z0.000 · P6 X0.000 Z-12.000 · 외 4점", roundDock(r.solution, shownWarnings(r), 1, comp = false, name = Color.Unspecified)!!.oneLine())
        val bad = RoundCalc.calc(roundExample().apply { setDim(2, "l2", "30") }.input(2))
        assertEquals(DockStatus.Error("치수가 서로 맞지 않습니다(0.001 mm 넘게)."), roundDock(bad.solution, shownWarnings(bad), 2, comp = true, name = Color.Unspecified))
        val fresh = RoundCalc.calc(RoundState().input(3))
        assertNull(roundDock(fresh.solution, shownWarnings(fresh), 3, comp = true, name = Color.Unspecified))
    }

    /** 그림은 견본 치수로 만든 윤곽을 그린다 — 풀리지 않으면 카드가 빈다 */
    @Test
    fun artSamplesSolve() {
        for (t in 1..6) assertNotNull(RoundCalc.calc(sampleInput(t)).solution, "형상 $t 그림 견본")
    }

    @Test
    fun stateSurvivesSaver() {
        val s = RoundState(type = 2, open = true)
        s.setDim(2, "l2", "12")
        s.l2Ref = L2Ref.A
        s.tip = 2
        s.inner = true
        s.setPoint("x1", "41.5")
        val back = with(RoundState.Saver) { restore(SaverScope { true }.save(s)!!)!! }
        assertEquals(2, back.type)
        assertTrue(back.open)
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
