package com.bmt.nosecalc.screens.sim

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쓰는 줄(설계 20 — 2026-11-10 피드백 5): 커서가 있고 들어온 뒤 고친 줄은 떠날 때까지 검사하지 않는다.
 * [EditorState.writingLine] · [EditorState.checkedText](검사 글) · [EditorState.checkGen](검사 글 번호) · [EditorState.hides].
 */
class EditorWritingTest {
    private fun EditorState.type(vararg keys: String) = keys.forEach { insert(it) }

    /** 쓰는 중: 줄 [line], 검사 글 [checked], 번호 [gen] */
    private fun EditorState.assertWriting(line: Int, checked: String, gen: Int) {
        assertEquals(line, writingLine)
        assertEquals(checked, checkedText)
        assertEquals(gen, checkGen)
        assertTrue(hides(line))
    }

    /** 줄을 끝냄: 쓰는 줄 없음 · 검사 글 = 글 · 번호 [gen] */
    private fun EditorState.assertSettled(gen: Int) {
        assertNull(writingLine)
        assertEquals(text, checkedText)
        assertEquals(gen, checkGen)
    }

    @Test
    fun typingMakesWritingLineUntilEnter() {
        val ed = EditorState()
        ed.insert("X")
        ed.assertWriting(0, "", 0)
        ed.type("3", "8", ".")
        ed.assertWriting(0, "", 0)
        ed.newline()
        ed.assertSettled(1)
        assertEquals("X38.\n", ed.checkedText)
        // 막 끝낸 줄: 그 글의 결과가 화면에 오를 때까지 가린다
        assertTrue(ed.hides(0))
        ed.ran(1)
        assertFalse(ed.hides(0))
        // 소문자도 같은 규칙(대문자로 들어간다)
        ed.insert("x")
        ed.assertWriting(1, "X38.\n", 1)
        assertEquals("X38.\nX", ed.text)
    }

    @Test
    fun movingInsideTheLineKeepsWritingAndLeavingEnds() {
        val before = "G0X1.\nG1Z2."
        val ed = EditorState(before, cursor = 5)
        ed.insert("F")
        ed.assertWriting(0, before, 0)
        ed.left()
        ed.right()
        ed.home()
        ed.end()
        ed.moveTo(2)
        ed.moveTo(4, extend = true)
        ed.selectWordAt(1)
        ed.assertWriting(0, before, 0)
        ed.vertical(1)
        ed.assertSettled(1)
        assertTrue(ed.hides(0))
        // 다른 줄 누르기 · 낱말 고르기 · 찾기(select)도 줄 끝
        for (leave in listOf<EditorState.() -> Unit>({ moveTo(0) }, { selectWordAt(1) }, { select(0 until 2) }, { moveTo(0, extend = true) })) {
            val e = EditorState(before, cursor = before.length)
            e.insert("F")
            e.assertWriting(1, before, 0)
            e.leave()
            e.assertSettled(1)
        }
    }

    /** 첫 줄의 ↑ · 끝 줄의 ↓ 는 갈 줄이 없어도 줄 끝(한 줄짜리 글에서 검사할 수 있게) */
    @Test
    fun verticalAtTheEdgeEndsTheLine() {
        val up = EditorState()
        up.insert("X")
        up.vertical(-1)
        up.assertSettled(1)
        val down = EditorState("G0X1.\n", cursor = 6)
        down.insert("X")
        down.assertWriting(1, "G0X1.\n", 0)
        down.vertical(1)
        down.assertSettled(1)
        assertEquals("G0X1.\nX", down.checkedText)
    }

    @Test
    fun joiningAndMultiLineEditsEndTheLine() {
        // 줄 처음의 ⌫ = 앞 줄과 합침
        val join = EditorState("G0\nX1.", cursor = 5)
        join.insert("5")
        join.assertWriting(1, "G0\nX1.", 0)
        join.moveTo(3)
        join.assertWriting(1, "G0\nX1.", 0)
        join.backspace()
        join.assertSettled(1)
        assertEquals("G0X15.", join.text)
        // 줄 끝의 Delete = 다음 줄과 합침
        val del = EditorState("G0\nX1.", cursor = 2)
        del.insert("1")
        del.deleteForward()
        del.assertSettled(1)
        assertEquals("G01X1.", del.text)
        // 여러 줄을 고르고 지움
        val cut = EditorState("G0\nX1.\nM30", cursor = 0)
        cut.select(1 until 5)
        cut.backspace()
        cut.assertSettled(1)
        // 여러 줄을 고른 채 글자를 침: 지운 데까지만 검사하고(줄 합침 = 줄 끝) 친 글자의 줄은 쓰는 줄
        val swap = EditorState("G0\nX1.\nM30", cursor = 0)
        swap.select(1 until 5)
        swap.insert("X")
        swap.assertWriting(0, "G.\nM30", 1)
        assertEquals("GX.\nM30", swap.text)
        // 그렇게 친 것을 되돌리면(줄이 다시 나뉨) 줄 끝, 다시하면 다시 쓰는 줄
        swap.undo()
        swap.assertSettled(2)
        assertEquals("G0\nX1.\nM30", swap.text)
        swap.redo()
        swap.assertWriting(0, "G.\nM30", 3)
        // 쓰던 줄이 있는 채 다른 여러 줄을 고르고 침(고르는 순간 쓰던 줄은 끝난다)
        val both = EditorState("A\nB\nC\nD", cursor = 1)
        both.insert("1")
        both.select(3 until 6)
        both.assertSettled(1)
        both.insert(";")
        both.assertSettled(3)
        assertEquals("A1\n;\nD", both.text)
        // 한 줄 안에서 고른 글을 바꾸면 쓰는 줄
        val one = EditorState("G0\nX1.\nM30", cursor = 0)
        one.select(3 until 5)
        one.insert("Z")
        one.assertWriting(1, "G0\nX1.\nM30", 0)
        assertEquals("G0\nZ.\nM30", one.text)
    }

    @Test
    fun undoRedo() {
        val ed = EditorState("G0X1.\nM30", cursor = 5)
        ed.type("F", "2")
        ed.undo()
        ed.assertWriting(0, "G0X1.\nM30", 0)
        ed.redo()
        ed.assertWriting(0, "G0X1.\nM30", 0)
        assertEquals("G0X1.F2\nM30", ed.text)
        // 줄바꿈을 되돌리면 줄 끝
        ed.newline()
        ed.assertSettled(1)
        ed.insert("Z")
        ed.assertWriting(1, "G0X1.F2\n\nM30", 1)
        ed.undo()
        ed.undo()
        ed.assertSettled(2)
        assertEquals("G0X1.F2\nM30", ed.text)

        // 다시하기가 커서 없는 다른 줄을 고침: 그 줄은 쓰는 줄이 되지 않는다
        val other = EditorState("A\nB", cursor = 3)
        other.insert("X")
        other.moveTo(0)
        other.assertSettled(1)
        other.undo()
        other.assertWriting(1, "A\nBX", 1)
        other.moveTo(0)
        other.assertSettled(2)
        other.redo()
        other.assertSettled(3)
        assertEquals("A\nBX", other.text)

        // 쓰는 줄이 있는데 되돌리기가 다른 줄을 바꿈: 쓰던 줄은 끝난다(지키는 것 나 — 쓰는 줄 말고는 검사 글과 같다)
        val two = EditorState("A\nB", cursor = 1)
        two.insert("1")
        two.moveTo(4)
        two.insert("2")
        two.assertWriting(1, "A1\nB", 1)
        two.undo()
        two.assertWriting(1, "A1\nB", 1)
        two.undo()
        assertEquals("A\nB", two.text)
        assertEquals(0, two.writingLine)
        assertEquals("A1\nB", two.checkedText)
    }

    @Test
    fun replaceAllAndPasteCheckAtOnce() {
        val ed = EditorState()
        ed.insert("X")
        ed.replaceAll("G0X1.\nM30")
        ed.assertSettled(1)
        assertFalse(ed.hides(0))
        // 붙여넣기는 한 글자라도 바로 검사
        ed.moveTo(ed.text.length)
        ed.paste("X")
        ed.assertSettled(2)
        ed.paste("G0\nG1")
        ed.assertSettled(3)
        // 쓰던 줄에 붙여도 끝난다
        ed.insert("Z")
        ed.assertWriting(2, ed.checkedText, 3)
        ed.paste("1.")
        ed.assertSettled(4)
    }

    /** EOB(;)는 줄 끝 — 같은 줄에 이어 쓰면 다시 쓰는 줄 */
    @Test
    fun eobEndsTheLine() {
        val ed = EditorState()
        ed.type("X", "1", ".")
        ed.insert(";")
        ed.assertSettled(1)
        assertTrue(ed.hides(0))
        ed.insert("G")
        ed.assertWriting(0, "X1.;", 1)
    }

    @Test
    fun selectAll() {
        // 한 줄짜리 글: 고른 범위가 줄 안이라 그대로 쓰는 중
        val one = EditorState()
        one.insert("X")
        one.selectAll()
        one.assertWriting(0, "", 0)
        // 여러 줄: 마지막 줄을 쓰던 중이어도(커서는 글 끝 = 그 줄) 끝난다
        val many = EditorState("G0X1.\n", cursor = 6)
        many.insert("X")
        many.selectAll()
        many.assertSettled(1)
    }

    @Test
    fun finishLine() {
        val ed = EditorState("G0X1.\nM30", cursor = 5)
        // 쓰는 줄이 없으면 아무것도 바뀌지 않는다
        ed.finishLine()
        ed.assertSettled(0)
        ed.insert("F")
        ed.finishLine()
        ed.assertSettled(1)
        assertTrue(ed.hides(0))
        ed.ran(1)
        // 쳤다가 도로 지움: 떠날 때까지 쓰는 줄, 떠나면 검사 글이 그대로라 번호도 가림도 그대로
        ed.insert("2")
        ed.backspace()
        ed.assertWriting(0, "G0X1.F\nM30", 1)
        ed.finishLine()
        ed.assertSettled(1)
        assertFalse(ed.hides(0))
    }

    @Test
    fun ranClearsOnlyForTheCurrentCheck() {
        val ed = EditorState("A\nB\nC", cursor = 1)
        ed.insert("1")
        ed.vertical(1)
        ed.insert("2")
        ed.vertical(1)
        ed.assertSettled(2)
        assertTrue(ed.hides(0) && ed.hides(1))
        assertFalse(ed.hides(2))
        // 낡은 번호(첫 줄만 끝낸 글의 결과)는 가림을 풀지 않는다
        ed.ran(1)
        assertTrue(ed.hides(0) && ed.hides(1))
        ed.ran(2)
        assertFalse(ed.hides(0) || ed.hides(1))
    }

    /** 첫 줄 · 빈 줄 · 줄 가운데 · 마지막 줄(줄바꿈 없이 끝) 어디서든 고친 줄이 쓰는 줄 */
    @Test
    fun anyLineCanBeTheWritingLine() {
        val text = "G0X1.\n\nG1Z2.\nM30"
        for ((pos, line) in listOf(0 to 0, 6 to 1, 9 to 2, text.length to 3)) {
            val ed = EditorState(text, cursor = pos)
            ed.insert("X")
            ed.assertWriting(line, text, 0)
            ed.backspace()
            ed.assertWriting(line, text, 0)
        }
        // 지워서 고쳐도 쓰는 줄
        val del = EditorState(text, cursor = 9)
        del.deleteForward()
        del.assertWriting(2, text, 0)
    }

    /** 글이 비면(다 지움 · 빈칸만 남음) 쓰는 줄도 없다 — 빈 편집기에 고치기 전 글의 결과(안내 · O 번호)가 남지 않게 바로 검사한다 */
    @Test
    fun blankTextHasNoWritingLine() {
        val ed = EditorState("O1234", cursor = 5)
        repeat(4) { ed.backspace() }
        ed.assertWriting(0, "O1234", 0)
        ed.backspace()
        ed.assertSettled(1)
        assertEquals("", ed.checkedText)
        // 빈칸만 남아도 같다
        val sp = EditorState("O1234", cursor = 0)
        sp.selectAll()
        sp.insert(" ")
        sp.assertSettled(1)
        // 빈 편집기에서 쳤다 지우면 검사 글이 그대로라 번호도 그대로
        val x = EditorState()
        x.insert("X")
        x.assertWriting(0, "", 0)
        x.backspace()
        x.assertSettled(0)
    }

    /**
     * 지키는 것(설계 20): 가 — 쓰는 줄이 없으면 검사 글 = 글. 나 — 쓰는 줄이 있으면 줄 수가 같고 그 줄 말고는 모두 같으며 커서가 그 줄에 있다.
     * 검사 글이 바뀌면 번호가 오른다(화면이 번호로 다시 돈다). 무작위로 섞어 걸음마다 본다
     */
    @Test
    fun invariantsHoldUnderRandomEdits() {
        val rnd = Random(1110)
        val ed = EditorState()
        val pieces = listOf("X", "1", ".", " ", "G", "-", ";", "\n", "M30", "G0X1.\nZ2.", "(", ")", ",", "x")
        fun pos() = rnd.nextInt(ed.text.length + 1)
        repeat(6000) { step ->
            val checkedBefore = ed.checkedText
            val genBefore = ed.checkGen
            val op = rnd.nextInt(16)
            when (op) {
                0, 1, 2, 3 -> ed.insert(pieces[rnd.nextInt(pieces.size)])
                4, 5 -> ed.backspace()
                6 -> ed.deleteForward()
                7 -> ed.moveTo(pos(), extend = rnd.nextBoolean())
                8 -> pos().let { a -> ed.select(a until a + rnd.nextInt(6)) }
                9 -> if (rnd.nextInt(4) == 0) ed.selectAll() else ed.selectWordAt(pos())
                10 -> ed.vertical(if (rnd.nextBoolean()) 1 else -1, extend = rnd.nextInt(4) == 0)
                11 -> if (rnd.nextBoolean()) ed.left(rnd.nextBoolean()) else ed.right(rnd.nextBoolean())
                12 -> ed.undo()
                13 -> ed.redo()
                14 -> if (ed.text.length > 120 || rnd.nextInt(8) == 0) ed.replaceAll(if (rnd.nextBoolean()) "" else "G0X1.\nM30", pos().coerceAtMost(3))
                else -> if (rnd.nextBoolean()) ed.paste(pieces[rnd.nextInt(pieces.size)]) else ed.finishLine()
            }
            val at = "걸음 $step(일 $op) 글 ${ed.text.replace("\n", "⏎")} / 검사 글 ${ed.checkedText.replace("\n", "⏎")} / 쓰는 줄 ${ed.writingLine}"
            val w = ed.writingLine
            if (w == null) {
                assertEquals(ed.text, ed.checkedText, at)
            } else {
                val a = ed.text.split('\n')
                val b = ed.checkedText.split('\n')
                assertEquals(a.size, b.size, at)
                for (i in a.indices) if (i != w) assertEquals(a[i], b[i], at)
                assertEquals(w, ed.cursorLine, at)
                ed.anchor?.let { assertEquals(w, ed.lineOf(it), at) }
            }
            if (ed.checkedText != checkedBefore) assertTrue(ed.checkGen > genBefore, at)
            assertTrue(ed.checkGen >= genBefore, at)
            if (op == 15) assertEquals(ed.text, ed.checkedText, at)
            // 지금 검사 글의 결과가 오르면 가리는 것은 쓰는 줄뿐
            if (step % 7 == 0) {
                ed.ran(ed.checkGen)
                for (i in 0 until ed.lineCount) assertEquals(i == ed.writingLine, ed.hides(i), at)
            }
        }
    }
}
