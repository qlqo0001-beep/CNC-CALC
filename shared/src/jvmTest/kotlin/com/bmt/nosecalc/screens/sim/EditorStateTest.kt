package com.bmt.nosecalc.screens.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 편집기 글 모델 · CNC 키 · SHIFT(설계 7.1 · 7.2 · 9.4 EditorTest) */
class EditorStateTest {
    private fun EditorState.type(vararg keys: String) = keys.forEach { insert(it) }

    @Test
    fun insertIsUppercaseAndLineEndsAreLf() {
        val ed = EditorState()
        ed.type("g", "01 x", "44.")
        assertEquals("G01 X44.", ed.text)
        ed.insert("\r\nz-2.\rF0.1")
        assertEquals("G01 X44.\nZ-2.\nF0.1", ed.text)
        assertEquals(3, ed.lineCount)
        assertEquals(ed.text.length, ed.cursor)
        assertEquals("Z-2.", ed.lineText(1))
        // 줄마다 LF 뒤 CR 둘(`\n\r\r`)을 붙인 글도 줄 끝 하나 — 빈 줄이 생기지 않는다
        assertEquals("G00 X10.\nZ2.\nM30\n", EditorState.clean("g00 x10.\n\r\rz2.\n\r\rm30\n\r\r"))
        // 다른 줄끝: CRLF · CR 하나는 줄 끝 하나, 원래 빈 줄(LF 여럿)은 그대로, 한 글 안에 섞여도 같다
        assertEquals("G00 X10.\nZ2.\n", EditorState.clean("G00 X10.\r\nZ2.\r\n"))
        assertEquals("G00 X10.\nZ2.\n", EditorState.clean("G00 X10.\rZ2.\r"))
        assertEquals("O0001\n\n\nG00 X10.\n", EditorState.clean("O0001\n\n\nG00 X10.\n"))
        assertEquals("O0001\nG00 X10.\nZ2.\n\nM30\n", EditorState.clean("O0001\n\r\rG00 X10.\r\nZ2.\n\n\r\rM30\r"))
    }

    @Test
    fun backspaceEnterAndSelection() {
        val ed = EditorState("G00 X10.", cursor = 8)
        ed.backspace()
        assertEquals("G00 X10", ed.text)
        ed.newline()
        ed.insert("M30")
        assertEquals("G00 X10\nM30", ed.text)
        assertEquals(1, ed.cursorLine)
        // 고른 글은 넣는 글로 바뀌고 ⌫ 는 고른 것을 지운다
        ed.select(4 until 7)
        assertEquals("X10", ed.selectedText())
        ed.insert("Z5.")
        assertEquals("G00 Z5.\nM30", ed.text)
        ed.select(0 until 4)
        ed.backspace()
        assertEquals("Z5.\nM30", ed.text)
        assertNull(ed.selection)
        // 처음에서 ⌫ 는 아무것도 하지 않고 되돌리기 걸음도 없다
        ed.moveTo(0)
        val undoBefore = ed.canUndo
        ed.backspace()
        assertEquals("Z5.\nM30", ed.text)
        assertEquals(undoBefore, ed.canUndo)
    }

    @Test
    fun undoRedoKeeps200Steps() {
        val ed = EditorState()
        repeat(250) { ed.insert("1") }
        var n = 0
        while (ed.canUndo) {
            ed.undo()
            n++
        }
        assertEquals(EditorState.UNDO_MAX, n)
        assertEquals(50, ed.text.length)
        ed.redo()
        assertEquals(51, ed.text.length)
        // 새로 고치면 다시하기는 사라진다
        ed.insert("X")
        assertFalse(ed.canRedo)
        // 불러오기는 쌓임을 비운다
        ed.replaceAll("O1001")
        assertFalse(ed.canUndo)
        assertEquals(0, ed.cursor)
    }

    @Test
    fun cursorMoves() {
        val ed = EditorState("G00 X56. Z5.\nG01\nZ-30.", cursor = 10)
        ed.vertical(1)
        assertEquals(ed.lineEnd(1), ed.cursor) // 짧은 줄이면 그 끝
        ed.vertical(1)
        assertEquals(ed.lineStart(2) + 3, ed.cursor)
        ed.home()
        assertEquals(ed.lineStart(2), ed.cursor)
        ed.end(extend = true)
        assertEquals("Z-30.", ed.selectedText())
        ed.left()
        assertEquals(ed.lineStart(2), ed.cursor) // 고른 것이 있으면 왼쪽 끝으로
        ed.vertical(-5)
        assertEquals(0, ed.cursor)
        ed.selectWordAt(6)
        assertEquals("X56.", ed.selectedText())
        ed.selectAll()
        assertEquals(ed.text, ed.selectedText())
    }

    @Test
    fun findWrapsAndIgnoresCase() {
        val ed = EditorState("G01 Z-30. ,C1.\nG01 X54. ,R1.\nG01 Z-30.")
        assertEquals(4 until 8, ed.find("z-30", 0))
        val second = ed.find("Z-30", 5)!!
        assertEquals("Z-30", ed.text.substring(second.first, second.last + 1))
        assertTrue(second.first > 20)
        assertEquals(4 until 8, ed.find("Z-30", second.last + 1)) // 끝에서 처음으로 돈다
        assertEquals(second, ed.find("Z-30", 4, forward = false)) // 뒤로 — 처음 앞이면 끝에서
        assertNull(ed.find("G02", 0))
        assertNull(ed.find("", 0))
    }

    @Test
    fun keypadLayoutMatches09() {
        assertEquals(4, CncRows.size)
        assertTrue(CncRows.all { it.size == 7 })
        assertEquals(listOf("O", "N", "G", "7", "8", "9", "⌫"), CncRows[0].map { it.main })
        assertEquals(listOf("F", "T", "R", "-", "0", ".", "⏎"), CncRows[3].map { it.main })
        // SHIFT 글자: 09 의 P · C · Q 그대로, A 는 빼고 I · K · L · ( · ) · / 를 더함
        val shifted = CncRows.flatten().mapNotNull { k -> k.shift?.let { k.main to it } }.toMap()
        assertEquals(mapOf("O" to "(", "N" to ")", "G" to "/", "X" to "I", "Z" to "K", "S" to "L", "W" to "P", "T" to "C", "R" to "Q"), shifted)
        assertNull(CncRows[3][0].shift) // F 의 A 는 뺐다
    }

    @Test
    fun shiftOnceThenOff() {
        val x = CncRows[1][0]
        val digit = CncRows[1][3]
        val back = CncRows[0][6]
        assertEquals(CncKey.Text("X") to ShiftState.OFF, pressKey(x, ShiftState.OFF))
        assertEquals(CncKey.Text("I") to ShiftState.OFF, pressKey(x, ShiftState.ONCE))
        assertEquals(CncKey.Text("I") to ShiftState.LOCK, pressKey(x, ShiftState.LOCK))
        // SHIFT 글자가 없는 키도 한 글자로 센다 · ⌫ 는 SHIFT 를 그대로 둔다
        assertEquals(CncKey.Text("4") to ShiftState.OFF, pressKey(digit, ShiftState.ONCE))
        assertEquals(CncKey.Back to ShiftState.ONCE, pressKey(back, ShiftState.ONCE))
        assertEquals(CncKey.Text(" ") to ShiftState.OFF, pressKey(CncRows[2][6], ShiftState.OFF))
        assertEquals(CncKey.Enter to ShiftState.OFF, pressKey(CncRows[3][6], ShiftState.OFF))
        // 단추: 누름 꺼짐 → 한 글자 → 꺼짐, 길게 → 고정, 고정에서 누름 → 꺼짐
        assertEquals(ShiftState.ONCE, nextShift(ShiftState.OFF, long = false))
        assertEquals(ShiftState.OFF, nextShift(ShiftState.ONCE, long = false))
        assertEquals(ShiftState.LOCK, nextShift(ShiftState.ONCE, long = true))
        assertEquals(ShiftState.OFF, nextShift(ShiftState.LOCK, long = false))
    }
}
