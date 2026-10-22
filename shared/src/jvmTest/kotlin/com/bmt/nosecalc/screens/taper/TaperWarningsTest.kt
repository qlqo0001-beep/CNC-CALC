package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveWarning
import com.bmt.nosecalc.calc.TaperWarning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 멈춘 경고가 짚는 칸과 글: 화면이 빨간 테를 두를 칸, Z 부호 글, 값이 모자란 안내 */
class TaperWarningsTest {
    private fun form(
        kind: TaperKind, type: Int, x: List<Double?>, z: List<Double?>, reverse: Boolean = false, angle: Double? = null,
        r: Double = 0.4, corners: List<Double> = List(x.size) { 0.0 }, zClear: Double = 1.0,
    ) = SlopeForm(kind, type, reverse, r, null, x, z, angle, zClear, corners)

    private fun stop(f: SlopeForm): TaperWarning = TaperSolve.solve(f).warnings.single()

    @Test
    fun contradictionFlagsFiveValues() {
        // taper-b-error: 두 점은 45° 인데 각도 30
        val f = form(TaperKind.OUTER, 3, listOf(40.0, 50.0), listOf(0.0, -5.0), angle = 30.0)
        assertEquals(TaperWarning.CONTRADICTION, stop(f))
        assertEquals(setOf("x1", "z1", "x2", "z2", "ang"), taperWarningFields(TaperWarning.CONTRADICTION, f))
    }

    /**
     * 다섯 값이 어긋나면 비울 칸을 단추로 권한다: 비어 있다가 마지막에 값이 들어온 칸 · 각도 가운데 지금 찬 것(둘까지).
     * 종류 · 형상 · 방향마다 따로 기억한다. 단추가 그 칸을 비우면 남은 넷으로 바로 풀린다
     */
    @Test
    fun contradictionOffersBlanks() {
        // taper-b-error: 비어 있던 각도에 30 을 넣었다
        val s = TaperState(type = 3, open = true).example().apply { setPoint("ang", "30") }
        assertEquals("ang", s.lastAuto)
        assertEquals(listOf("ang"), s.contradictionFixes())
        // Z2 를 비워 각도로 풀다가 Z2 를 다시 넣으면 Z2 · 각도
        s.setPoint("z2", "")
        s.setPoint("z2", "-8")
        assertEquals(listOf("z2", "ang"), s.contradictionFixes())
        // 각도가 비어 있으면 그 단추는 없다
        s.setPoint("ang", "")
        assertEquals(listOf("z2"), s.contradictionFixes())
        // 모서리 R · 시작점은 다섯 값이 아니다
        s.setPoint("r1", "1")
        assertEquals("z2", s.lastAuto)
        // 방향을 바꾸면 따로
        s.reverse = true
        assertEquals(null, s.lastAuto)
        assertEquals(emptyList(), s.contradictionFixes())
        // 각도 비우고 계산: 남은 넷(X 40 · 50, Z 0 · −5)으로 풀린다
        assertTrue(TaperSolve.solve(form(TaperKind.OUTER, 3, listOf(40.0, 50.0), listOf(0.0, -5.0), angle = null)).solution != null)
    }

    @Test
    fun zSignFlagsOnlyZ() {
        // taper-b-zsign: 정방향인데 Z2 가 + 쪽
        val f = form(TaperKind.OUTER, 1, listOf(40.0, 50.0), listOf(0.0, 5.0))
        assertEquals(TaperWarning.SHAPE_MISMATCH, stop(f))
        assertTrue(f.zBackward)
        assertEquals(setOf("z1", "z2"), taperWarningFields(TaperWarning.SHAPE_MISMATCH, f))
        assertTrue(taperWarningText(TaperWarning.SHAPE_MISMATCH, f).startsWith("Z 부호 · 순서를 확인하세요. 정방향은"))
        // 역방향은 T1 이 척 쪽이라 Z 가 커져야 한다
        val r = form(TaperKind.OUTER, 1, listOf(40.0, 50.0), listOf(-30.0, -35.0), reverse = true)
        assertEquals(TaperWarning.SHAPE_MISMATCH, stop(r))
        assertTrue(taperWarningText(TaperWarning.SHAPE_MISMATCH, r).startsWith("Z 부호 · 순서를 확인하세요. 역방향은"))
        // Z 는 맞고 X 가 형상과 반대면 예전 글, 점 칸 모두
        val x = form(TaperKind.OUTER, 1, listOf(50.0, 40.0), listOf(0.0, -5.0))
        assertEquals(TaperWarning.SHAPE_MISMATCH, stop(x))
        assertFalse(x.zBackward)
        assertEquals(setOf("x1", "x2", "z1", "z2"), taperWarningFields(TaperWarning.SHAPE_MISMATCH, x))
    }

    @Test
    fun badInputFlagsTheValue() {
        val f = form(TaperKind.OUTER, 1, listOf(-40.0, 50.0), listOf(0.0, -5.0), corners = listOf(-1.0, 0.0))
        assertEquals(TaperWarning.BAD_INPUT, stop(f))
        assertEquals(setOf("x1", "r1"), taperWarningFields(TaperWarning.BAD_INPUT, f))
        assertEquals(setOf("noseR"), taperWarningFields(TaperWarning.BAD_INPUT, form(TaperKind.OUTER, 1, listOf(40.0, 50.0), listOf(0.0, -5.0), r = Double.NaN)))
    }

    @Test
    fun cornerAndNose() {
        val f = form(TaperKind.OUTER, 1, listOf(40.0, 50.0), listOf(0.0, -5.0), corners = listOf(0.0, 30.0))
        assertEquals(TaperWarning.CORNER_R_TOO_BIG, stop(f))
        assertEquals(setOf("r2"), taperWarningFields(TaperWarning.CORNER_R_TOO_BIG, f))
        assertEquals(setOf("noseR"), taperWarningFields(TaperWarning.NOSE_UNREACHABLE, f))
        // 숫자와 함께 나오는 노랑 경고는 칸을 칠하지 않는다
        assertEquals(emptySet(), taperWarningFields(TaperWarning.NOSE_GT_SLOPE, f))
    }

    @Test
    fun missingInputIsGuidance() {
        val f = form(TaperKind.OUTER, 1, listOf(40.0, null), listOf(0.0, -5.0))
        assertEquals(TaperWarning.MISSING_INPUT, stop(f))
        assertEquals(emptySet(), taperWarningFields(TaperWarning.MISSING_INPUT, f))
        assertEquals("값을 넣으면 바로 계산합니다 — X1 · Z1 · X2 · Z2 · 각도 가운데 넷.", taperWarningText(TaperWarning.MISSING_INPUT, f))
        // X 를 넣고 Z2 를 아직 0 으로 두면 안내(두 점이 Z 0 에 겹침)
        assertTrue(form(TaperKind.OUTER, 1, listOf(40.0, 50.0), listOf(0.0, 0.0)).waiting)
        assertFalse(form(TaperKind.OUTER, 1, listOf(40.0, 50.0), listOf(0.0, -5.0)).waiting)
    }

    @Test
    fun grooveFields() {
        val i = GrooveInput(-10.0, 8.0, 40.0, 30.0, 9.0)
        assertEquals(listOf(GrooveWarning.TOOL_WIDER_THAN_GROOVE), GrooveCalc.calc(i).warnings)
        assertEquals(setOf("insert", "width"), grooveWarningFields(GrooveWarning.TOOL_WIDER_THAN_GROOVE, i))
        val bad = GrooveInput(-10.0, 8.0, 40.0, -1.0, 3.0)
        assertEquals(listOf(GrooveWarning.BAD_INPUT), GrooveCalc.calc(bad).warnings)
        assertEquals(setOf("bottom"), grooveWarningFields(GrooveWarning.BAD_INPUT, bad))
        assertTrue(GrooveInput(0.0, 0.0, 40.0, 30.0, 3.0).waiting)
    }
}
