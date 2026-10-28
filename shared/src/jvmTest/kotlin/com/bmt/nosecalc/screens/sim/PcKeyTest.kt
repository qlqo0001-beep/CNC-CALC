@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.bmt.nosecalc.KeyValueStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PC 키보드(설계 7.1): 글자는 대문자로, 화살표 · Shift 고르기, Ctrl+Z/Y/A/C/X/V/F, 찾기 줄이 열리면 찾는 글로 */
@Suppress("DEPRECATION")
class PcKeyTest {
    private class Clip : ClipboardManager {
        var held: AnnotatedString? = null
        override fun getText(): AnnotatedString? = held
        override fun setText(annotatedString: AnnotatedString) {
            held = annotatedString
        }
    }

    private val clip = Clip()
    private val sim = SimState(KeyValueStore(File.createTempFile("bmt-pc", ".properties").apply { delete() }))

    private fun press(k: Key, ch: Char? = null, ctrl: Boolean = false, shift: Boolean = false): Boolean =
        pcKey(KeyEvent(k, KeyEventType.KeyDown, codePoint = ch?.code ?: 0, isCtrlPressed = ctrl, isShiftPressed = shift), sim, clip)

    private fun type(s: String) = s.forEach { ch ->
        val k = when (ch) {
            ' ' -> Key.Spacebar
            '.' -> Key.Period
            else -> Key.A // 글자는 코드 포인트로 넣는다
        }
        assertTrue(press(k, ch))
    }

    @Test
    fun typingArrowsAndShortcuts() {
        type("g01 x40.")
        assertEquals("G01 X40.", sim.editor.text)
        assertTrue(press(Key.Enter))
        type("m30")
        assertEquals("G01 X40.\nM30", sim.editor.text)
        press(Key.DirectionUp)
        press(Key.MoveHome)
        press(Key.MoveEnd, shift = true)
        assertEquals("G01 X40.", sim.editor.selectedText())
        press(Key.C, ctrl = true)
        assertEquals("G01 X40.", clip.held?.text)
        press(Key.X, ctrl = true)
        assertEquals("\nM30", sim.editor.text)
        press(Key.V, ctrl = true)
        press(Key.V, ctrl = true)
        assertEquals("G01 X40.G01 X40.\nM30", sim.editor.text)
        press(Key.Z, ctrl = true)
        assertEquals("G01 X40.\nM30", sim.editor.text)
        press(Key.Z, ctrl = true, shift = true)
        assertEquals("G01 X40.G01 X40.\nM30", sim.editor.text)
        press(Key.A, ctrl = true)
        press(Key.Delete)
        assertEquals("", sim.editor.text)
        // 모르는 키는 넘긴다(Esc 는 앱이 먼저 받는다)
        assertFalse(press(Key.F5))
        assertFalse(pcKey(KeyEvent(Key.A, KeyEventType.KeyUp, codePoint = 'a'.code), sim, clip))
    }

    @Test
    fun findTakesTypingWhenOpen() {
        type("g01 z-30.")
        sim.editor.moveTo(0)
        press(Key.F, ctrl = true)
        assertTrue(sim.findOpen)
        type("z-3")
        assertEquals("Z-3", sim.query)
        assertEquals("Z-3", sim.editor.selectedText())
        press(Key.Backspace)
        assertEquals("Z-", sim.query)
        assertEquals("G01 Z-30.", sim.editor.text)
    }
}
