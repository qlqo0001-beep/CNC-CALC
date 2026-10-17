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
