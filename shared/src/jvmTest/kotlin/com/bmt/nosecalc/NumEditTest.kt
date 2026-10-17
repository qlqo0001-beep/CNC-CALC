package com.bmt.nosecalc

import com.bmt.nosecalc.ui.NumEdit
import kotlin.test.Test
import kotlin.test.assertEquals

class NumEditTest {
    private fun type(start: String, keys: String) = keys.fold(start) { t, k -> NumEdit.press(t, k) }

    @Test
    fun digitsAndPoint() {
        assertEquals("12.5", type("", "12.5"))
        assertEquals("0.4", type("", ".4"))
        assertEquals("5", type("0", "5"))
        assertEquals("0.05", type("", "0.05"))
        assertEquals("1.25", type("", "1.2.5"))
    }

    @Test
    fun signToggles() {
        assertEquals("-25", type("", "-25"))
        assertEquals("-25", type("25", "-"))
        assertEquals("25", type("-25", "-"))
        assertEquals("-0.5", type("", "-.5"))
        assertEquals("-3", type("-0", "3"))
    }

    @Test
    fun deleteAndClear() {
        assertEquals("12", type("123", "<"))
        assertEquals("", type("123", "C"))
        assertEquals("", type("", "<"))
    }

    @Test
    fun digitLimit() {
        assertEquals("123456789012", type("", "1234567890123"))
    }

    @Test
    fun finishTidiesText() {
        assertEquals("5", NumEdit.finish("5."))
        assertEquals("", NumEdit.finish("-"))
        assertEquals("", NumEdit.finish(""))
        assertEquals("0", NumEdit.finish("-0"))
        assertEquals("0", NumEdit.finish("-0."))
        assertEquals("-25.0", NumEdit.finish("-25.0"))
        assertEquals(-25.0, NumEdit.finish("-25.0").toDouble())
    }
}
