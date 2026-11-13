package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쓰는 줄의 오류 숨김(설계 20 — 2026-11-10 피드백 5)을 화면 없이: [SimState] 의 글을 고치고, 화면이 0.3초 뒤 하는 일([rerun])을 손으로 부른 뒤
 * 화면과 같은 [shownIssues] 로 보일 문제를 본다. 글은 모두 새로 쓴 것.
 */
class SimWritingTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-writing", ".properties").apply { delete() })

    /** 화면의 0.3초 효과가 하는 일 그대로: 검사 글로 돌고, 그 번호의 결과가 올랐다고 알린다 */
    private fun SimState.rerun() {
        val gen = editor.checkGen
        run = runOf(editor.checkedText, tools)
        runKey = Triple(editor.checkedText, tools, runSetup())
        editor.ran(gen)
    }

    private fun SimState.shown(): List<SimIssue> = shownIssues(editor, run)

    /** 그 줄(1부터)에 보이는 문제 코드 */
    private fun SimState.codes(line: Int): List<IssueCode> = shown().filter { it.line == line }.map { it.code }

    /** 글을 불러와 한 번 돈 상태(커서는 글 끝) */
    private fun sim(text: String = "", cursor: Int = text.length, tools: Map<Int, ToolOffset>? = null) = SimState(store(), tools).apply {
        editor.replaceAll(text, cursor)
        rerun()
    }

    private fun key(main: String) = CncRows.flatten().first { it.main == main }

    /** 피드백 원문: "X" 만 쳐도 오류가 뜬다 → 줄을 끝내야(⏎ · 다른 줄로) 뜬다 */
    @Test
    fun xAloneIsHiddenUntilTheLineEnds() {
        val empty = sim()
        empty.editor.insert("X")
        assertEquals(emptyList(), empty.shown())
        empty.rerun()
        assertEquals(emptyList(), empty.shown())
        empty.editor.newline()
        empty.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), empty.codes(1))

        val s = sim("G0X1.\n")
        s.editor.insert("X")
        assertEquals(emptyList(), s.codes(2))
        s.rerun()
        assertEquals(emptyList(), s.codes(2))
        s.editor.vertical(-1)
        s.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(2))
    }

    @Test
    fun otherLinesKeepTheirIssuesWhileWriting() {
        val s = sim("G00 X44 Z2.\n")
        val before = s.shown()
        assertTrue(IssueCode.NO_POINT_COORD in s.codes(1), "$before")
        assertTrue(IssueCode.NO_END in s.codes(1), "$before")
        s.editor.insert("X")
        // 글 끝 줄을 쓰는 중이라 글 끝 안내(NO_END)만 빠진다(설계 21.6)
        assertEquals(before.filter { it.code != IssueCode.NO_END }, s.shown())
        assertEquals(emptyList(), s.codes(2))
    }

    /** 쓰는 동안은 다시 돌지 않는다 — 반쯤 쓴 줄에서 실행이 멈춰 뒤 줄의 결과 · 문제가 사라지는 일이 없다 */
    @Test
    fun writingNeitherRerunsNorSpreads() {
        val text = "T0101\nG97 S1000 M03\nG99\nG00 X40. Z2.\n\nG01 Z-10. F0.2\nM71\nM30"
        val s = sim(text, cursor = text.indexOf("\n\n") + 1)
        val before = s.run
        assertNull(before.stop, "${before.stop}")
        assertTrue(IssueCode.MACHINE_M in s.codes(7), "${s.shown()}")
        val gen = s.editor.checkGen
        s.editor.insert("G")
        assertEquals(4, s.editor.writingLine)
        assertEquals(gen, s.editor.checkGen, "번호가 그대로여야 화면이 다시 돌지 않는다")
        s.rerun()
        assertEquals(before.stop, s.run.stop)
        assertEquals(before.steps.size, s.run.steps.size)
        assertEquals(before.issues, s.shown())
        // 견줌: 편집기 글 전체로 돌면(고치기 전 동작) 5줄에서 멈추고 뒤 줄의 실행 문제가 사라진다
        val whole = s.runOf(s.editor.text, s.tools)
        assertEquals(5, whole.stop?.line)
        assertTrue(whole.steps.size < before.steps.size)
        assertTrue(whole.issues.none { it.code == IssueCode.MACHINE_M })
        // 반쯤 쓴 값이 뒤 줄에 가짜 문제를 만들지도 않는다(X3 = 0.003 mm 로 실제 이동)
        s.editor.backspace()
        s.editor.insert("X3")
        assertEquals(before.issues, s.shown())
    }

    /** 이미 문제가 보이던 줄: 커서만 가면 그대로, 고치기 시작하면 그 줄 것만 숨기고, 떠난 뒤 새 결과가 올 때까지 옛 문제가 되살아나지 않는다 */
    @Test
    fun editingALineWithIssues() {
        val s = sim(SimShotsTest.ERRORS, cursor = 0)
        val line6 = s.editor.lineStart(5)
        assertEquals("G01 X40. X41. F0.2", s.editor.lineText(5))
        s.editor.moveTo(line6 + 13)
        assertEquals(listOf(IssueCode.DUP_ADDRESS), s.codes(6))
        s.editor.backspace()
        assertEquals(emptyList(), s.codes(6))
        assertEquals(listOf(IssueCode.NO_POINT_COORD), s.codes(5))
        assertEquals(listOf(IssueCode.UNKNOWN_ADDRESS), s.codes(7))
        repeat(4) { s.editor.backspace() }
        assertEquals("G01 X40. F0.2", s.editor.lineText(5))
        s.editor.vertical(-1)
        assertNull(s.editor.writingLine)
        assertEquals(emptyList(), s.codes(6), "새 결과가 오기 전: 옛 오류가 되살아나면 안 된다")
        s.rerun()
        assertEquals(emptyList(), s.codes(6))
        assertEquals(7, s.run.stop?.line)

        // 고치지 않고 떠나거나, 고쳤다가 원래대로 돌려놓고 떠나면 옛 문제가 곧바로 그대로 보인다(다시 돌지 않아도)
        val t = sim(SimShotsTest.ERRORS, cursor = 0)
        t.editor.moveTo(line6 + 13)
        t.editor.vertical(1)
        assertEquals(listOf(IssueCode.DUP_ADDRESS), t.codes(6))
        t.editor.moveTo(line6 + 13)
        t.editor.insert("1")
        assertEquals(emptyList(), t.codes(6))
        t.editor.backspace()
        assertEquals(emptyList(), t.codes(6))
        val gen = t.editor.checkGen
        t.editor.vertical(-1)
        assertEquals(gen, t.editor.checkGen)
        assertEquals(listOf(IssueCode.DUP_ADDRESS), t.codes(6))
    }

    /** 글의 마지막 줄(줄바꿈 없이 끝)에 쓰다 만 글 + 궤적 보기: 틀린 프로그램이 조용히 돌지 않는다 */
    @Test
    fun runChecksTheUnfinishedLastLine() {
        val s = sim("G0X1.\n")
        s.editor.insert("X")
        assertEquals(emptyList(), s.codes(2))
        s.ensureRun()
        assertNull(s.editor.writingLine)
        assertEquals(IssueCode.NO_NUMBER, s.run.stop?.code)
        assertEquals(2, s.run.stop?.line)
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(2))
        // 한 줄짜리 프로그램도
        val one = sim()
        one.editor.insert("X")
        one.ensureRun()
        assertEquals(IssueCode.NO_NUMBER, one.run.stop?.code)
        assertEquals(listOf(IssueCode.NO_NUMBER), one.codes(1))
        // 끝낸 직후(새 결과가 오기 전)에 눌러도: ensureRun 이 바로 돌고 가림을 푼다
        val quick = sim("G0X1.\n")
        quick.editor.insert("X")
        quick.editor.finishLine()
        assertEquals(emptyList(), quick.codes(2))
        quick.ensureRun()
        assertEquals(listOf(IssueCode.NO_NUMBER), quick.codes(2))
    }

    /** 저장(화면의 [저장] 과 같은 차례: 줄을 끝내고 저장) — 저장한 글은 편집기 글 그대로이고 문제가 드러난다 */
    @Test
    fun saveEndsTheLine() {
        val s = sim("O1234\nG0X1.\n")
        s.editor.insert("X")
        s.editor.finishLine()
        assertEquals("O1234 저장했습니다", s.requestSave())
        assertEquals("O1234\nG0X1.\nX", s.programs.load(1234))
        s.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(3))
        // 번호를 물어 넣는 저장: 첫 줄에 O 번호 줄이 들어가며 쓰던 줄이 끝난다
        val n = sim("G0X1.\n")
        n.editor.insert("X")
        n.editor.finishLine()
        assertNull(n.requestSave())
        assertEquals(SimAsk.Number, n.ask)
        n.editor.insert("1")
        n.saveAs(77)
        assertNull(n.editor.writingLine)
        assertEquals(n.editor.text, n.editor.checkedText)
        assertEquals("O0077\nG0X1.\nX1", n.programs.load(77))
    }

    /** 붙여넣기 · 예제 · 프로그램 불러오기는 바로 검사한다(쓰는 줄이 생기지 않는다) */
    @Test
    fun pasteAndLoadCheckAtOnce() {
        val s = sim("G0X1.\n")
        s.editor.paste("X")
        assertNull(s.editor.writingLine)
        s.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(2))
        // 쓰던 중에 불러와도
        s.editor.insert("3")
        assertEquals(1, s.editor.writingLine)
        s.programs.save(88, "O0088\nG00 X44 Z2.\nX\nM30")
        s.perform(Pending.Load(88))
        assertNull(s.editor.writingLine)
        assertEquals(s.editor.text, s.editor.checkedText)
        s.rerun()
        assertEquals(s.run.issues, s.shown())
        assertEquals(listOf(IssueCode.NO_POINT_COORD), s.codes(2))
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(3))
        s.editor.moveTo(s.editor.text.length)
        s.editor.insert("X")
        s.perform(Pending.Example(0))
        assertNull(s.editor.writingLine)
        assertEquals(EditorState.clean(exampleList[0].text), s.editor.checkedText)
        s.rerun()
        assertEquals(s.run.issues, s.shown())
    }

    /** 쓰다 만 글 여러 가지: 쓰는 동안은 그 줄에 아무것도 없고, 끝내면 보인다. '/' 만은 끝내도 문제가 아니다(블록 스킵) */
    @Test
    fun unfinishedWordsOfEveryKind() {
        val want = mapOf(
            "X" to IssueCode.NO_NUMBER, "(" to IssueCode.COMMENT_OPEN, "X3" to IssueCode.NO_POINT_COORD, "T01" to IssueCode.T_FORMAT,
            "G7" to IssueCode.UNSUPPORTED_G, "," to IssueCode.BAD_CHAR, "X-" to IssueCode.NO_NUMBER, "N" to IssueCode.NO_NUMBER,
            "O" to null, ",C" to null, "G" to IssueCode.NO_NUMBER, "M" to IssueCode.NO_NUMBER,
        )
        for ((word, code) in want) {
            val s = sim("G0X1.\n")
            for (ch in word) {
                s.editor.insert(ch.toString())
                assertEquals(emptyList(), s.codes(2), "'$word' 를 쓰는 중")
                assertEquals(1, s.editor.writingLine)
            }
            s.editor.newline()
            s.rerun()
            assertTrue(s.codes(2).isNotEmpty(), "'$word' 를 끝냄: ${s.shown()}")
            if (code != null) assertTrue(code in s.codes(2), "'$word' 를 끝냄: ${s.codes(2)}")
        }
        val skip = sim("G0X1.\n")
        skip.editor.insert("/")
        skip.editor.newline()
        skip.rerun()
        assertEquals(emptyList(), skip.codes(2))
    }

    /** 줄 없는 문제(line 0)는 쓰는 중에도 보이고(가운데 줄 — 글 끝 줄이면 글 끝 문제는 가린다, 설계 21.6), 검사 글이 빈 글이면 아무것도 보이지 않는다 */
    @Test
    fun lineLessIssuesAndBlankText() {
        val s = sim("G0X1.\n\nM30", cursor = 6)
        s.editor.insert("X")
        s.run = s.run.copy(issues = s.run.issues + SimIssue(IssueCode.NO_END, 0))
        assertTrue(s.shown().any { it.line == 0 })
        val empty = sim()
        empty.editor.insert("X")
        empty.run = empty.run.copy(issues = listOf(SimIssue(IssueCode.NO_END, 0)))
        assertEquals(emptyList(), empty.shown())
        // 한 줄짜리 프로그램을 끝낸 뒤(오류 보임) 다 지움 — 쓰는 줄이라 보일 문제가 없다
        val one = sim()
        one.editor.insert("X")
        one.editor.finishLine()
        one.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), one.codes(1))
        one.editor.backspace()
        assertEquals("", one.editor.text)
        assertEquals(emptyList(), one.shown())
    }

    /** 화면을 떠났다 오거나 프로세스가 죽었다 살면(Saver) 새 상태 — 쓰는 줄 없이 글 전체를 검사한다 */
    @Test
    fun restoreChecksEverything() {
        val kv = store()
        val s = SimState(kv).apply {
            editor.replaceAll("G0X1.\n", 6)
            rerun()
        }
        s.editor.insert("X")
        assertEquals(emptyList(), s.codes(2))
        val saver = SimState.saver(kv)
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(s) }
        val r = saver.restore(assertNotNull(saved))!!
        assertEquals("G0X1.\nX", r.editor.text)
        assertNull(r.editor.writingLine)
        assertEquals(listOf(IssueCode.NO_NUMBER), r.codes(2))
    }

    /** 폰 CNC 키패드: 글자 · SHIFT 글자 키는 쓰는 줄을 만들고 ⏎ 가 끝낸다. ⌫ 는 줄 안에서는 그대로, 줄 처음에서(앞 줄과 합침) 끝낸다 */
    @Test
    fun cncKeypad() {
        val s = sim()
        s.press(key("X"))
        assertEquals(0, s.editor.writingLine)
        assertEquals(emptyList(), s.shown())
        s.press(key("⏎"))
        assertNull(s.editor.writingLine)
        s.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(1))
        s.shift = ShiftState.ONCE
        s.press(key("X"))
        assertEquals("X\nI", s.editor.text)
        assertEquals(1, s.editor.writingLine)
        assertEquals(emptyList(), s.codes(2))
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(1))
        s.press(key("⌫"))
        assertEquals(1, s.editor.writingLine)
        s.press(key("⌫"))
        assertEquals("X", s.editor.text)
        assertNull(s.editor.writingLine)
        // ↶ ↷: 한 줄 안에서는 그대로 쓰는 중
        s.press(key("1"))
        s.editor.undo()
        assertEquals(0, s.editor.writingLine)
        s.editor.redo()
        assertEquals(0, s.editor.writingLine)
        assertEquals("X1", s.editor.text)
        // 찾기 줄이 열려 있으면 키는 찾는 글로 간다 — 편집기 글도 쓰는 줄도 그대로
        s.editor.finishLine()
        s.openFind()
        s.press(key("Z"))
        assertEquals("Z", s.query)
        assertEquals("X1", s.editor.text)
        assertNull(s.editor.writingLine)
        s.rerun()
        assertTrue(IssueCode.NO_POINT_COORD in s.codes(1), "${s.shown()}")
    }

    /** 블록 없는 한 줄(O 번호 · 주석만)을 다 지운 빈 편집기: 고치기 전 글의 'M30 없이 끝남' 안내(줄 0)도 O 번호도 남지 않는다 */
    @Test
    fun clearedEditorShowsNothing() {
        for (text in listOf("O1234", "(TEST)")) {
            val s = sim(text)
            assertEquals(listOf(IssueCode.NO_END), s.shown().map { it.code }, text)
            repeat(text.length) { s.editor.backspace() }
            assertEquals("", s.editor.text)
            assertEquals(emptyList(), s.shown(), text)
            s.rerun()
            assertEquals(emptyList(), s.shown(), text)
            assertNull(s.run.program.units.firstOrNull()?.number, text)
        }
        // 빈칸만 남겨도 같다
        val sp = sim("O1234")
        sp.editor.selectAll()
        sp.editor.insert(" ")
        assertEquals(emptyList(), sp.shown())
    }

    /** 여러 줄을 고른 채 글자를 침: 지운 데까지만 바로 검사하고, 친 글자의 줄은 쓰는 줄이라 떠날 때까지 문제가 없다 */
    @Test
    fun typingOverSeveralLinesIsWriting() {
        val s = sim("G0X40.Z2.\nG1Z-5.F0.1\nG1X42.\nM30", cursor = 0)
        s.editor.select(s.editor.lineStart(1) until s.editor.lineEnd(2))
        s.press(key("X"))
        assertEquals("G0X40.Z2.\nX\nM30", s.editor.text)
        assertEquals(1, s.editor.writingLine)
        assertEquals("G0X40.Z2.\n\nM30", s.editor.checkedText)
        s.rerun()
        assertEquals(emptyList(), s.codes(2))
        s.editor.vertical(1)
        s.rerun()
        assertEquals(listOf(IssueCode.NO_NUMBER), s.codes(2))
    }

    /**
     * 사용자 답(2026-11-13) "그 줄을 다 입력할 때까지 에러 표기하지 않음"(설계 21.6): 글 끝 줄을 쓰는 동안은 글이 덜 끝나서 붙는 문제도 띄우지 않는다.
     * 끝낼 글을 한 글자씩 치는 동안 · ⏎ 직후(새 결과 전) · 새 결과 뒤 모두 없다. 끝내도 남는 글을 치고 ⏎ 하면 새 결과 뒤 보인다
     */
    @Test
    fun endOfTextIssuesWaitForTheLastLine() {
        for (c in END_CASES) {
            fun SimState.has() = c.code in shown().map { it.code }
            val s = sim(c.text, tools = c.tools)
            assertTrue(s.has(), "치기 전에는 보인다(${c.code}): ${s.shown()}")
            for (ch in c.done) {
                s.press(key(if (ch == ' ') "SP" else ch.toString()))
                assertFalse(s.has(), "'${c.done}' 를 쓰는 중(${c.code}): ${s.shown()}")
                s.rerun()
                assertFalse(s.has(), "'${c.done}' 를 쓰는 중 0.3초 뒤(${c.code}): ${s.shown()}")
            }
            s.press(key("⏎"))
            assertNull(s.editor.writingLine)
            assertFalse(s.has(), "⏎ 직후 — 옛 결과의 ${c.code} 가 되살아났다: ${s.shown()}")
            s.rerun()
            assertTrue(s.run.issues.none { it.code == c.code }, "'${c.done}' 로 끝났다: ${s.run.issues}")

            val w = sim(c.text, tools = c.tools)
            for (ch in c.wrong) w.press(key(if (ch == ' ') "SP" else ch.toString()))
            assertFalse(w.has(), "'${c.wrong}' 를 쓰는 중(${c.code}): ${w.shown()}")
            w.press(key("⏎"))
            assertFalse(w.has(), "⏎ 직후(새 결과 전 — ${c.code}): ${w.shown()}")
            w.rerun()
            assertTrue(w.has(), "'${c.wrong}' 로는 끝나지 않는다(${c.code}): ${w.shown()}")
        }
    }

    /** 가운데 줄을 고칠 때는 글 끝 문제가 그 줄 때문이 아니라 그대로 보인다 */
    @Test
    fun endOfTextIssuesStayWhileEditingAMiddleLine() {
        for (c in END_CASES) {
            val s = sim(c.text, tools = c.tools)
            s.editor.moveTo(s.editor.lineEnd(2))
            s.editor.backspace()
            assertEquals(2, s.editor.writingLine)
            assertTrue(c.code in s.shown().map { it.code }, "${c.code}: ${s.shown()}")
            s.editor.insert("3")
            assertTrue(c.code in s.shown().map { it.code }, "${c.code}: ${s.shown()}")
        }
    }

    /**
     * 같은 코드라도 글 끝에서 생긴 것이 아니면 글 끝 줄을 쓰는 동안에도 보인다(설계 21.6 — 쓰는 줄로는 고칠 수 없다):
     * 주 프로그램에 M30 이 없어 끝 부프로그램의 M99 줄에 붙은 NO_END(M99 뒤에 쓰는 중) · 뒤에 다른 O 번호가 있는 주 프로그램의 G70 Q 번호 틀림
     */
    @Test
    fun endOfTextCodesFromFinishedPlacesStay() {
        val cases = listOf(
            Triple(IssueCode.NO_END, 9, HEAD + "G00 X40. Z2.\nM98 P2\nO0002\nG01 Z-10. F0.2\nM99\n"),
            Triple(IssueCode.CYCLE_PQ_NOT_FOUND, 11, HEAD + "G00 X42. Z2.\nG71 U1. R0.5\nG71 P10 Q20 U0.2 W0.1 F0.2\nN10 G00 X30.\nG01 Z-10.\nN20 X42.\nG70 P10 Q30\nM30\nO0002\nG01 X50.\n"),
        )
        for ((code, line, text) in cases) {
            val s = sim(text)
            assertTrue(code in s.codes(line), "$code: ${s.run.issues}")
            s.press(key("G"))
            assertEquals(text.count { it == '\n' }, s.editor.writingLine, "$code: 글 끝 줄을 쓰는 중")
            assertTrue(code in s.codes(line), "$code 가 끝 줄에 G 하나로 숨었다: ${s.shown()}")
        }
    }

    /** 글 끝 뒤에 빈 줄 · 빈칸만 있으면 쓰는 줄을 마지막 줄로 본다. 뒤에 글(주석 하나라도)이 있으면 마지막 줄이 아니다 */
    @Test
    fun onlyBlankLinesAfterIsTheLastLine() {
        val c = END_CASES[0]
        val blank = sim(c.text + "\n  \n\n", cursor = c.text.length)
        assertTrue(IssueCode.NO_END in blank.shown().map { it.code }, "${blank.shown()}")
        blank.press(key("M"))
        assertEquals(6, blank.editor.writingLine)
        assertFalse(IssueCode.NO_END in blank.shown().map { it.code }, "${blank.shown()}")
        val note = sim(c.text + "\n(END)", cursor = c.text.length)
        note.press(key("M"))
        assertEquals(6, note.editor.writingLine)
        assertTrue(IssueCode.NO_END in note.shown().map { it.code }, "${note.shown()}")
    }

    /** 글이 덜 끝나서 붙는 문제 한 가지: 앞 글(끝은 빈 줄 — 커서가 거기), 끝내는 글, 끝내도 남는 글, 그 문제 */
    class EndCase(val text: String, val done: String, val wrong: String, val code: IssueCode, val tools: Map<Int, ToolOffset>? = null)

    companion object {
        private const val HEAD = "O0001\nT0101\nG97 S1000 M03\nG99\n"

        /** 글 끝 문제마다 하나(설계 21.6 — 화면 시험도 쓴다). 글은 새로 쓴 것 */
        val END_CASES = listOf(
            EndCase(HEAD + "G00 X40. Z2.\nG01 Z-10. F0.2\n", "M30", "M3", IssueCode.NO_END),
            EndCase(HEAD + "G42\nG00 X40. Z20.\nZ2.\nG01 Z-10. F0.2\nX46. F.3\n", "G40", "X48.", IssueCode.COMP_NOT_CANCELLED, mapOf(1 to ToolOffset(0.8, 3))),
            EndCase(HEAD + "G00 X36. Z2.\nG01 Z0. F0.2\nX40.,C1.\n", "Z-10.", "M30", IssueCode.CORNER_NO_NEXT),
            EndCase(HEAD + "G00 X42. Z2.\nG71 U1. R0.5\nG71 P10 Q20 U0.2 W0.1 F0.2\nN10 G00 X30.\nG01 Z-10.\n", "N20 X42.", "N2 X42.", IssueCode.CYCLE_PQ_NOT_FOUND),
            EndCase(HEAD + "G00 X40. Z2.\nM98 P2\nM30\nO0002\nG01 Z-10. F0.2\n", "M99", "M9", IssueCode.SUB_NO_M99),
            EndCase(HEAD + "G00 X40. Z2.\nM98 P3\nM30\n", "O0003", "O0004", IssueCode.SUB_SKIPPED),
        )
    }
}
