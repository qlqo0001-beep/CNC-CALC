package com.bmt.nosecalc.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FmtTest {
    @Test
    fun ncNumbers() {
        assertEquals("34.", Fmt.nc(34.0))
        assertEquals("-2.186", Fmt.nc(-2.186))
        assertEquals("-2.", Fmt.nc(-2.0))
        assertEquals("0.", Fmt.nc(0.0))
        assertEquals("0.5", Fmt.nc(0.5))
        assertEquals("100.1", Fmt.nc(100.1))
        assertEquals("1.235", Fmt.nc(1.23456))
        assertEquals("2.186", Fmt.nc(2.1855)) // 반올림(5 는 올림)
        assertEquals("-2.186", Fmt.nc(-2.1855))
        assertEquals("0.", Fmt.nc(-0.0004)) // "-0." 이 아니다
        assertEquals("1.", Fmt.nc(0.9999))
    }

    /** NC 글의 숫자(회사 표기): 앞 0 을 뺀다. 정수 뒤 점 · 0 은 그대로 */
    @Test
    fun ncWordNumbers() {
        assertEquals("34.", Fmt.word(34.0))
        assertEquals(".5", Fmt.word(0.5))
        assertEquals("-.234", Fmt.word(-0.234))
        assertEquals("-2.186", Fmt.word(-2.1855))
        assertEquals("0.", Fmt.word(0.0))
        assertEquals("0.", Fmt.word(-0.0004))
        assertEquals(".001", Fmt.word(0.0005))
        assertEquals(".635", Fmt.shortLead("0.635"))
        assertEquals("-.25", Fmt.shortLead("-0.25"))
        assertEquals("12.5", Fmt.shortLead("12.5"))
        assertEquals("0.", Fmt.shortLead("0."))
    }

    /** 낱말 쪼개기: 빈칸이 있든 없든 같다. 주석은 통째, 쉼표 모서리 · G12.1 은 한 낱말 */
    @Test
    fun ncWordSplit() {
        assertEquals(listOf("G42", "G1", "X40.", "Z.4"), ncWords("G42G1X40.Z.4"))
        assertEquals(listOf("G42", "G01", "X40.", "Z0.4"), ncWords("G42 G01 X40. Z0.4"))
        assertEquals(listOf("N10", "G0", "X-.5", "Z1."), ncWords("N10G0X-.5Z1."))
        assertEquals(listOf("G1", "Z-30.", ",C1."), ncWords("G1Z-30.,C1."))
        assertEquals(listOf("G12.1"), ncWords("G12.1"))
        assertEquals(listOf("(R0.4 T3)"), ncWords("(R0.4 T3)"))
    }

    /** NC 줄 모으기: 같은 축 값 · 같은 이동 G 는 빼고, 보정 G 는 앞에 붙이며, 움직임 없는 줄(과 그 G)은 버린다 */
    @Test
    fun ncLinesModal() {
        val nc = NcLines().apply {
            move("G0", Pt.ofDia(1.0, 38.0))
            move("G0", Pt.ofDia(1.0, 38.0))
            move("G1", Pt.ofDia(0.0, 40.0), "G42")
            move("G1", Pt.ofDia(-5.0, 50.0))
            move("G1", Pt.ofDia(-6.0, 52.0), "G40")
            move("G0", Pt.ofDia(-6.0, 52.0))
            move("G0", Pt.ofDia(1.0, 52.0))
        }
        assertEquals(listOf("G0X38.Z1.", "G42G1X40.Z0.", "X50.Z-5.", "G40X52.Z-6.", "G0Z1."), nc.lines)
    }

    @Test
    fun fixed3() {
        assertEquals("34.000", Fmt.fixed3(34.0))
        assertEquals("-2.186", Fmt.fixed3(-2.185927))
        assertEquals("0.469", Fmt.fixed3(0.4686))
        assertEquals("0.000", Fmt.fixed3(-0.0001))
        assertEquals("0.010", Fmt.fixed3(0.01))
    }

    @Test
    fun rejectsNonFinite() {
        assertFailsWith<IllegalArgumentException> { Fmt.nc(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Fmt.fixed3(Double.POSITIVE_INFINITY) }
    }
}
