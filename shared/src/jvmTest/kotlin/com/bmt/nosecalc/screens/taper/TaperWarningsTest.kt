package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.calc.Fmt
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
    ) = SlopeForm(kind, type, reverse, r, null, x, z, angle, zClear, corners, angleRef = AngleRef.Z) // 각도 숫자는 Z축 기준(taper-b-error 의 30°)

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

    /**
     * 뒤집으면(T1 = 반대 끝 점) Z 가 가는 쪽도 반대: 정방향 뒤집기는 T1 이 척 쪽이라 Z 가 커지고, 역방향 뒤집기는 작아진다.
     * 거꾸로 넣으면 Z 칸만 빨간 테와 그 방향의 예. 바이트 반대 글은 뒤집은 진행도 말한다
     */
    @Test
    fun zSignFollowsFlip() {
        val ok = form(TaperKind.OUTER, 1, listOf(50.0, 40.0), listOf(-5.0, 0.0)).copy(flip = true)
        assertTrue(TaperSolve.solve(ok).solution != null)
        assertFalse(ok.zBackward)
        val bad = form(TaperKind.OUTER, 1, listOf(50.0, 40.0), listOf(0.0, -5.0)).copy(flip = true)
        assertEquals(TaperWarning.SHAPE_MISMATCH, stop(bad))
        assertTrue(bad.zBackward)
        assertEquals(setOf("z1", "z2"), taperWarningFields(TaperWarning.SHAPE_MISMATCH, bad))
        assertTrue(taperWarningText(TaperWarning.SHAPE_MISMATCH, bad).contains("T1 이 척 쪽이라 Z 가 커집니다"))
        val rev = form(TaperKind.OUTER, 1, listOf(50.0, 40.0), listOf(-30.0, -25.0), reverse = true).copy(flip = true)
        assertEquals(TaperWarning.SHAPE_MISMATCH, stop(rev))
        assertTrue(rev.zBackward)
        assertTrue(taperWarningText(TaperWarning.SHAPE_MISMATCH, rev).contains("T1 이 척 반대쪽이라 Z 가 작아집니다"))
        // 뒤집은 외경 형상 3 의 시작점(외경 Z-20)부터 깎으면 외경을 Z+ 로 — 바이트 반대(진행), 글은 T1 ⇄ T2 도 말한다
        val back = form(TaperKind.OUTER, 3, listOf(50.0, 40.0), listOf(-5.0, 0.0)).copy(flip = true, start = -20.0)
        assertTrue(TaperWarning.AGAINST_TOOL in TaperSolve.solve(back).warnings)
        assertTrue(taperWarningText(TaperWarning.AGAINST_TOOL, back).contains("T1 ⇄ T2"))
        assertFalse(taperWarningText(TaperWarning.AGAINST_TOOL, back.copy(flip = false)).contains("⇄"))
    }

    /**
     * 뒤집은 외경 형상 1 로 단면을 중심(끝 X0)까지 깎으면 노즈가 중심에 닿게 인선이 일부러 중심 너머 X −2r 까지 가서 ENTRY_PAST_AXIS —
     * 글은 Z 여유를 줄이라고 하지 않고 그 X 와 Z 로 빠진다는 것을 말한다(검증 2026-10-27). 끝 X 를 넣은 것이 아니면 지금 글
     */
    @Test
    fun pastAxisTextForFaceToCenter() {
        val face = form(TaperKind.OUTER, 1, listOf(50.0, 40.0), listOf(-5.0, 0.0)).copy(flip = true, end = 0.0)
        assertTrue(TaperWarning.ENTRY_PAST_AXIS in TaperSolve.solve(face).warnings)
        val t = taperWarningText(TaperWarning.ENTRY_PAST_AXIS, face)
        assertTrue(t.startsWith("단면을 중심(X0)까지 깎아") && "Z 여유를 줄이세요" !in t && "X−0.8" in t && "Z 로 빠집니다" in t, t)
        // 그 글대로: 인선 X-0.8 까지 깎고 Z 로 후퇴(두 프로그램 모두). 기계 보정(회사 꼴)은 단면을 X −2r = −0.8 까지 쓰고 공구 쪽(+Z)으로
        // c = max(Z 여유 1, r) = 1 빼는 줄 Z1.(F.3) → G40 → G0Z200. — 단면 보정선(Z0.4)과 빼는 줄 보정선(반경 −0.4 + 0.4)의 교점이라 노즈 중심이 중심선에 선다
        val s = TaperSolve.solve(face).solution!!
        assertEquals(listOf("X-.8", "G0Z1."), s.ncG40.takeLast(2))
        assertEquals(listOf("X-.8", "Z1.F.3", "G40", "G0Z200."), s.ncG41G42.takeLast(4))
        assertTrue(taperWarningText(TaperWarning.ENTRY_PAST_AXIS, face.copy(end = 30.0)).endsWith("Z 여유를 줄이세요."))
        // 끝 X30 은 공구 쪽(+Z)으로 빠져 중심을 넘지 않는다
        assertTrue(TaperWarning.ENTRY_PAST_AXIS !in TaperSolve.solve(face.copy(end = 30.0)).warnings)
    }

    /**
     * 내경 형상 4(큰 내경 → 테이퍼 → 안쪽 단면)의 바닥 단면을 중심(끝 X0)까지 — 두 프로그램이 다르게 중심을 넘어 글이 둘을 나눠 말한다(검증 2026-11-04).
     * 기계 보정: 바닥 단면(Z−5)을 X −2r = −0.8 까지 쓰고 공구 쪽(+Z)으로 c = max(Z 여유 1, r 0.4) = 1 → Z−4.(F.3). G40 자리 규칙 0.986 — 가장 가까운 소재는
     * 바닥 단면(과 축 너머 같은 면)에서 1.0. 직접 보정: 끝 단면을 이어서 c 더(calc '진입점과 NC' 후퇴 — 단면이면 그대로 이어서) — 끝점′ 에서 같은 Z 로 X 를 2c 줄인 G0
     */
    @Test
    fun pastAxisTextForInnerBottomToCenter() {
        val bottom = form(TaperKind.INNER, 4, listOf(40.0, 30.0), listOf(0.0, -5.0)).copy(end = 0.0)
        val res = TaperSolve.solve(bottom)
        assertTrue(TaperWarning.ENTRY_PAST_AXIS in res.warnings)
        val t = taperWarningText(TaperWarning.ENTRY_PAST_AXIS, bottom)
        assertTrue(t.startsWith("바닥 단면을 중심(X0)까지 깎아") && "Z 여유를 줄이세요" !in t, t)
        assertTrue("기계 보정(G41/G42)은 X−0.8 까지 간 뒤 Z 로 빠지고" in t && "직접 보정(G40)은 같은 높이로 중심 너머까지" in t, t)
        val s = res.solution!!
        assertEquals(listOf("X-.8", "Z-4.F.3", "G40", "G0Z200."), s.ncG41G42.takeLast(4))
        val endX = s.ncG40[s.ncG40.size - 2].substringAfter("X").toDouble()
        assertEquals("G0X${Fmt.word(endX - 2 * 1.0)}", s.ncG40.last())
        assertTrue(endX - 2 * 1.0 < 0)
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
