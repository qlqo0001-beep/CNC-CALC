@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.numPadKey
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.awt.event.KeyEvent as AwtKey

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

    /**
     * 숫자 키패드(PC 창이 받는 그대로 — [numPadKey]): 숫자 · . · − · + · / 는 눌린 글자로 들어가고 Enter 는 줄바꿈.
     * 찾기 줄이 열려 있으면 찾는 글로, Enter 는 다음 찾기
     */
    @Test
    fun numPadKeys() {
        fun pad(code: Int, ch: Char) = assertTrue(pcKey(numPadKey(code, ch), sim, clip), "$ch")
        fun digits(s: String) = s.forEach { pad(AwtKey.VK_NUMPAD0 + (it - '0'), it) }
        pad(AwtKey.VK_DIVIDE, '/')
        type("g1x")
        pad(AwtKey.VK_SUBTRACT, '-')
        digits("12")
        pad(AwtKey.VK_DECIMAL, '.')
        digits("5")
        type("w")
        pad(AwtKey.VK_ADD, '+')
        digits("03")
        pad(AwtKey.VK_ENTER, '\n')
        type("z")
        digits("4")
        pad(AwtKey.VK_DECIMAL, '.')
        assertEquals("/G1X-12.5W+03\nZ4.", sim.editor.text)
        press(Key.F, ctrl = true)
        digits("12")
        pad(AwtKey.VK_DECIMAL, '.')
        assertEquals("12.", sim.query)
        assertEquals("12.", sim.editor.selectedText())
        pad(AwtKey.VK_ENTER, '\n') // 다음 찾기 — 하나뿐이라 그 자리
        assertEquals("12.", sim.editor.selectedText())
        assertEquals("/G1X-12.5W+03\nZ4.", sim.editor.text)
    }

    /**
     * 쓰는 줄(설계 20 — 쓰는 중인 줄에는 오류를 띄우지 않는다): 글자 키가 쓰는 줄을 만들고, Enter(숫자 키패드 Enter) · ↑ ↓ · 줄 밖으로 나가는 ← → ·
     * Shift+↓ · Ctrl+V · Ctrl+F · ; · 여러 줄 Ctrl+A 가 끝낸다. 같은 줄 안의 ← → Home End · Ctrl+Z · 한 줄 안 Ctrl+X 는 그대로
     */
    @Test
    fun writingLine() {
        val ed = sim.editor
        fun writing() = ed.writingLine != null
        type("x")
        assertTrue(writing())
        assertEquals(emptyList(), shownIssues(ed, sim.run))
        press(Key.Enter)
        assertFalse(writing())
        assertEquals("X\n", ed.checkedText)
        type("z")
        assertTrue(pcKey(numPadKey(AwtKey.VK_ENTER, '\n'), sim, clip))
        assertFalse(writing())
        assertEquals("X\nZ\n", ed.checkedText)
        // 같은 줄 안에서 옮기는 것은 그대로
        type("g1")
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        press(Key.MoveHome)
        press(Key.MoveEnd)
        assertEquals(2, ed.writingLine)
        assertEquals("X\nZ\n", ed.checkedText)
        // 줄 처음의 ← · 줄 끝의 →
        press(Key.MoveHome)
        press(Key.DirectionLeft)
        assertFalse(writing())
        type("5")
        assertEquals(1, ed.writingLine)
        press(Key.DirectionRight)
        assertFalse(writing())
        // ↓ ↑: 갈 줄이 없어도(끝 줄의 ↓)
        type("f")
        assertEquals(2, ed.writingLine)
        press(Key.DirectionDown)
        assertFalse(writing())
        type("2")
        press(Key.DirectionUp)
        assertFalse(writing())
        type("3")
        assertEquals(1, ed.writingLine)
        press(Key.DirectionDown, shift = true)
        assertFalse(writing())
        press(Key.DirectionRight)
        assertEquals("X\nZ53\nFG12", ed.text)
        assertEquals(ed.text, ed.checkedText)
        // Ctrl+V 는 한 글자라도 바로 검사, Ctrl+Z(한 줄 안)는 그대로 쓰는 중
        clip.held = AnnotatedString("7")
        type("w")
        press(Key.V, ctrl = true)
        assertFalse(writing())
        type("u1")
        press(Key.Z, ctrl = true)
        assertTrue(writing())
        // Ctrl+F: 줄을 끝내고 찾기 줄을 연다 — 열린 동안 글자는 찾는 글로 가서 쓰는 줄이 생기지 않는다
        press(Key.F, ctrl = true)
        assertFalse(writing())
        assertTrue(sim.findOpen)
        type("z")
        assertEquals("Z", sim.query)
        assertFalse(writing())
        sim.findOpen = false
        press(Key.DirectionRight)
        // ; (EOB)
        type("m")
        assertTrue(writing())
        type(";")
        assertFalse(writing())
        // Ctrl+A: 고른 범위가 쓰는 줄 밖으로 나가면 끝
        type("s")
        press(Key.A, ctrl = true)
        assertFalse(writing())
        assertEquals(ed.text, ed.checkedText)
        // 한 줄 안에서 고른 것을 Ctrl+X: 그대로 쓰는 중
        press(Key.DirectionRight)
        type("t1")
        press(Key.DirectionLeft, shift = true)
        press(Key.X, ctrl = true)
        assertEquals("1", clip.held?.text)
        assertTrue(writing())
    }
}
