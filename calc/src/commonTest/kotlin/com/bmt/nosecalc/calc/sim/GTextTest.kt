package com.bmt.nosecalc.calc.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 글 읽기(`docs/simulator-design.md` 1.1 · 1.2). 출처 S = Siemens ISO Turning 04/2007, H = Haas ST/TL 워크북 2015(인쇄 쪽) */
class GTextTest {
    private fun parse(vararg lines: String, decimal: DecimalMode = DecimalMode.STANDARD) = GText.parse(lines.joinToString("\n"), decimal)

    private fun codes(p: ParsedProgram) = p.issues.map { it.code }

    private fun only(p: ParsedProgram, code: IssueCode, line: Int = 1) {
        assertEquals(listOf(code), codes(p), "${p.issues}")
        assertEquals(line, p.issues[0].line)
    }

    @Test
    fun wordsValuesAndCols() {
        val p = parse("G01 X44. Z-30. F0.2")
        assertTrue(p.issues.isEmpty(), "${p.issues}")
        val b = p.units[0].blocks.single()
        assertEquals(listOf("G", "X", "Z", "F"), b.words.map { it.key })
        assertEquals(listOf(1.0, 44.0, -30.0, 0.2), b.words.map { it.value })
        assertEquals(listOf(0..2, 4..7, 9..13, 15..18), b.words.map { it.cols })
        assertEquals("X44.", b.words[1].text)
        assertTrue(b.words[1].point)
        assertEquals(1, b.line)
    }

    @Test
    fun lowercaseNoSpacesAndCommaWords() {
        // H 53 의 `x2.218` 같은 소문자, 낱말 사이 빈칸 없음
        val b = parse("g01x2.218z-1.,r1.").units[0].blocks.single()
        assertEquals(listOf("G", "X", "Z", ",R"), b.words.map { it.key })
        assertEquals(listOf(1.0, 2.218, -1.0, 1.0), b.words.map { it.value })
        assertEquals(13..16, b.words[3].cols)
    }

    @Test
    fun commentsSemicolonPercentSkip() {
        val p = parse("%", "N5 G00 X10. (APPROACH) Z5.;G01 Z-5. F0.1 ; ", "/G00 X20.", "%")
        assertTrue(p.issues.isEmpty(), "${p.issues}")
        val bs = p.units[0].blocks
        assertEquals(3, bs.size)
        assertEquals(5, bs[0].n)
        assertEquals("APPROACH", bs[0].comment)
        assertEquals(listOf("G", "X", "Z"), bs[0].words.map { it.key })
        assertEquals(2, bs[0].line)
        assertEquals(2, bs[1].line)
        assertTrue(bs[0].cols.last < bs[1].cols.first)
        assertEquals("G01 Z-5. F0.1", bs[1].text)
        assertTrue(bs[2].skip)
        assertFalse(bs[0].skip)
        assertEquals(3, bs[2].line)
        // `;` 앞에 아무것도 없으면 EOB 만인 블록(FF 4.1.6 — G90/G92/G94 모드에서 사이클을 다시 돈다). 줄 끝 `;` · 빈 줄은 블록을 더 만들지 않는다
        val e = parse("G00 X1.;", "", ";", "G01 Z1. F0.1;;")
        assertTrue(e.issues.isEmpty(), "${e.issues}")
        val eb = e.units[0].blocks
        assertEquals(listOf(1, 3, 4, 4), eb.map { it.line })
        assertEquals(listOf(false, true, false, true), eb.map { it.words.isEmpty() })
        assertEquals(0..0, eb[1].cols)
        assertEquals(13..13, eb[3].cols)
    }

    @Test
    fun commentErrors() {
        only(parse("G00 X1. (OPEN"), IssueCode.COMMENT_OPEN)
        only(parse("G00 X1. (A (B)"), IssueCode.COMMENT_NESTED)
        // 주석 안의 ; 와 한글은 괜찮다
        assertTrue(parse("G00 X1. (가공; 시작)").issues.isEmpty())
    }

    @Test
    fun programUnits() {
        val p = parse("O1001 (MAIN)", "G00 X10.", "M98 P2000", "M30", "O2000", "G01 Z1.", "M99")
        assertTrue(p.issues.isEmpty(), "${p.issues}")
        assertEquals(listOf(1001, 2000), p.units.map { it.number })
        assertEquals(listOf(1, 5), p.units.map { it.firstLine })
        assertEquals(1, p.units[1].blocks[0].unit)
        assertEquals(6, p.lineOf(1, 0))
        // 첫 O 앞 글 = 번호 없는 주 프로그램
        val q = parse("G00 X10.", "M30", "O0021", "M99")
        assertEquals(listOf(null, 21), q.units.map { it.number })
        // 같은 번호 둘
        assertTrue(IssueCode.DUP_PROGRAM in codes(parse("O1", "M30", "O1", "M99")))
    }

    /** S 1-13 표 1-2(IS-B): `X1000` 표준 = 1 mm, 계산기식 = 1000 mm */
    @Test
    fun decimalModes() {
        val std = parse("G00 X1000 Z44")
        val w = std.units[0].blocks[0].words
        assertEquals(1.0, w[1].value, 1e-12)
        assertEquals(0.044, w[2].value, 1e-12)
        assertEquals(listOf(IssueCode.NO_POINT_COORD, IssueCode.NO_POINT_COORD), codes(std))
        assertEquals(listOf("1."), std.issues[0].args)
        assertEquals("X1000", std.issues[0].word)
        val calc = parse("G00 X1000 Z44", decimal = DecimalMode.CALCULATOR)
        assertTrue(calc.issues.isEmpty())
        assertEquals(1000.0, calc.units[0].blocks[0].words[1].value)
        // 정수 전용 주소는 두 설정 모두 그대로
        val i = parse("G04 P1500", "M98 P1000 L2", "T0101", "S1000")
        assertTrue(i.issues.isEmpty(), "${i.issues}")
        assertEquals(1500.0, i.units[0].blocks[0].w("P")!!.value)
        // G73 첫 줄 R 은 횟수(S 4-106 — ×0.001 하지 않음) — 둘째 줄 R 은 없음. 소수점도 받는다(FF 4.2.3 주: 실행기가 반올림)
        val g73 = parse("G73 U1. W1. R3")
        assertTrue(g73.issues.isEmpty(), "${g73.issues}")
        assertEquals(3.0, g73.units[0].blocks[0].w("R")!!.value)
        val g73p = parse("G73 U1. W1. R3.0")
        assertTrue(g73p.issues.isEmpty(), "${g73p.issues}")
        assertEquals(3.0, g73p.units[0].blocks[0].w("R")!!.value)
        only(parse("G73 U1. W1. R-3."), IssueCode.BAD_VALUE)
        // 다른 블록 R 은 좌표
        assertEquals(listOf(IssueCode.NO_POINT_COORD), codes(parse("G02 X10. Z-5. R5")))
    }

    @Test
    fun integerOnlyAddresses() {
        only(parse("G04 P1.5"), IssueCode.DECIMAL_NOT_ALLOWED)
        only(parse("M98 P1000."), IssueCode.DECIMAL_NOT_ALLOWED)
        only(parse("T01.01"), IssueCode.DECIMAL_NOT_ALLOWED)
        only(parse("N1.5 G00 X1."), IssueCode.DECIMAL_NOT_ALLOWED)
        only(parse("G12.1"), IssueCode.UNSUPPORTED_G)
        only(parse("S-100"), IssueCode.BAD_VALUE)
        only(parse("N-5"), IssueCode.BAD_VALUE)
        only(parse("F-0.2"), IssueCode.BAD_VALUE)
    }

    @Test
    fun syntaxErrors() {
        only(parse("G01 X10. @"), IssueCode.BAD_CHAR)
        only(parse("G01 X10. 35"), IssueCode.BAD_CHAR)
        only(parse("G01 X10.,"), IssueCode.BAD_CHAR)
        only(parse("G01 X"), IssueCode.NO_NUMBER)
        only(parse("G01 X1234567890."), IssueCode.NUMBER_TOO_LONG)
        only(parse("N123456 G00 X1."), IssueCode.NUMBER_TOO_LONG)
        only(parse("G00 X20001."), IssueCode.NUMBER_TOO_BIG)
        only(parse("G00 X1. X2."), IssueCode.DUP_ADDRESS)
        only(parse("G00 G01 X1."), IssueCode.DUP_GROUP)
        only(parse("M03 M08"), IssueCode.TWO_M)
        only(parse("G00 X10. U2."), IssueCode.ABS_INC_BOTH)
        only(parse("G01 X10. ,C1. ,R1."), IssueCode.CORNER_BOTH)
        only(parse("G00 X1. /Z2."), IssueCode.MID_BLOCK_SKIP)
        // 여러 줄 — 줄 번호가 맞아야
        only(parse("G00 X1.", "G01 Z-1. F0.1", "G01 Y5."), IssueCode.UNKNOWN_ADDRESS, 3)
    }

    @Test
    fun gCodes() {
        only(parse("G20"), IssueCode.INCH)
        for (g in listOf("G52", "G53", "G55", "G59", "G154")) only(parse("$g X0."), IssueCode.WORK_OFFSET)
        for (g in listOf("G32", "G17", "G33", "G81", "G10", "G65")) only(parse("$g X0."), IssueCode.UNSUPPORTED_G)
        assertTrue(parse("G00 G18 G21 G40 G54 G80 G97 G99 X1.").issues.isEmpty())
    }

    @Test
    fun tFormat() {
        for (t in listOf("T0101", "T101", "T1212", "T0", "T00", "T0100")) assertTrue(parse(t).issues.isEmpty(), t)
        only(parse("T1"), IssueCode.T_FORMAT)
        only(parse("T12"), IssueCode.T_FORMAT)
        only(parse("T12345"), IssueCode.T_FORMAT)
    }

    /** Haas 1줄 형식(H 77~111 · 36~38)은 HAAS_FORMAT, 그 밖 D · A 는 UNKNOWN_ADDRESS */
    @Test
    fun haasFormat() {
        only(parse("G71 P10 Q20 U0.01 W0.005 D0.1 F0.01"), IssueCode.HAAS_FORMAT)
        only(parse("G73 P1 Q2 U0.01 W0.005 I0.3 K0.15 D4"), IssueCode.HAAS_FORMAT)
        val g76 = parse("G76 X0.674 Z-1.25 K0.038 D0.012 F0.0625 A60")
        assertEquals(listOf(IssueCode.HAAS_FORMAT, IssueCode.HAAS_FORMAT), codes(g76))
        only(parse("G01 X2. A30."), IssueCode.HAAS_FORMAT)
        // 모달 G01 뒤의 A 도
        assertEquals(IssueCode.HAAS_FORMAT, parse("G01 Z-1. F0.1", "X2. A30.").issues.single().code)
        only(parse("G00 A1."), IssueCode.UNKNOWN_ADDRESS)
        only(parse("G00 X1. D1"), IssueCode.UNKNOWN_ADDRESS)
    }

    /** 원본 09.jpg 편집기 글 — 문제 없이 읽혀야 하고 `,R` · `,C` 낱말 위치가 맞아야 */
    @Test
    fun original09() {
        val text = listOf(
            "O1001", "N11 T0101 M08", "G50 S2000", "G96 S200 M03", "G00 X56. Z5.", "G71 U1.5 R0.5",
            "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X16.", "G01 Z0. F0.15", "G01 X20. Z-2.", "G01 Z-8.",
            "G02 X34. Z-15. R7.", "G01 Z-22. ,R1.", "G01 X44. ,R1.", "G01 Z-30. ,C1.", "G01 X54. ,R1.", "G01 Z-43. ,C1.",
        )
        val p = GText.parse(text.joinToString("\n"))
        assertTrue(p.issues.isEmpty(), "${p.issues}")
        assertEquals(1001, p.units.single().number)
        val b = p.units[0].blocks.first { it.line == 13 }
        assertEquals(listOf("G", "Z", ",R"), b.words.map { it.key })
        assertEquals(10..13, b.words[2].cols)
        assertEquals(11, p.units[0].blocks.first { it.line == 2 }.n)
    }

    @Test
    fun tokensForSyntaxColor() {
        assertEquals(
            listOf(0..2 to "N", 4..8 to "T", 10..12 to "M", 14..16 to "(", 17..17 to ";"),
            GText.tokens("N11 T0101 M08 (X);"),
        )
        assertEquals(listOf(0..0 to "/", 1..3 to "G", 5..8 to ",C", 10..10 to "!"), GText.tokens("/G01 ,C1. @"))
        assertEquals(listOf(0..0 to "%"), GText.tokens("%"))
        assertEquals(listOf(0..0 to "!"), GText.tokens("X"))
    }

    @Test
    fun parseNeverThrows() {
        val junk = listOf("", ";;;", "(", ")", "((", "G", ",", ",C", "--", "1.2.3", "X--1", "%%", "O", "N", "/", "/ /", "\t", "가나다", "G01X.Z-.F")
        for (j in junk) GText.parse(j)
        assertNull(GText.parse("").units.single().number)
    }

    /** 4단계 무작위 시험에서 찾음: 10자리 넘는 G(Int 를 넘음)가 예외 없이 NUMBER_TOO_LONG — 실행도 그 줄에서 멈춘다 */
    @Test
    fun hugeGNumberIsTooLongNotCrash() {
        only(parse("G9999999999 X1."), IssueCode.NUMBER_TOO_LONG)
        val r = GSim.run(SimInput(listOf("T0101", "G9999999999", "M30").joinToString("\n")))
        assertEquals(IssueCode.NUMBER_TOO_LONG, r.stop?.code)
        assertEquals(2, r.stop?.line)
    }
}
