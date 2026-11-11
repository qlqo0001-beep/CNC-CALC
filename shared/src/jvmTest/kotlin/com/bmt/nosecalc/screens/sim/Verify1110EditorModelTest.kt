@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.awtKey
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.numPadKey
import com.bmt.nosecalc.ui.NumEdit
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.awt.event.KeyEvent as AwtKey

/**
 * 독립 검증(2026-11-10 피드백 5 · 6) — 화면 없이. 구현자의 시험과 따로 쓴 검사다.
 * 화면이 하는 일은 [quiesce] 로 흉내 낸다: 검사 글 번호가 바뀌었을 때만 검사 글로 다시 돈다(SimEditorPage 의 0.3초 효과 그대로).
 * 글은 모두 새로 쓴 것.
 */
@Suppress("DEPRECATION")
class Verify1110EditorModelTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-v1110", ".properties").apply { delete() })

    /** 화면 한 벌: 상태 + 화면의 효과가 마지막으로 돈 검사 글 번호 */
    private inner class Screen(text: String = "", cursor: Int = text.length) {
        val sim = SimState(store())
        val ed get() = sim.editor
        private var ranGen = -1

        init {
            ed.replaceAll(text, cursor)
            quiesce()
        }

        /** 0.3초가 지남: 검사 글 번호가 바뀌었으면 검사 글로 돌고 그 번호의 결과가 올랐다고 알린다. 돌았으면 true */
        fun quiesce(): Boolean {
            val gen = ed.checkGen
            if (gen == ranGen) return false
            val text = ed.checkedText
            sim.run = sim.runOf(text, sim.tools)
            sim.runKey = Triple(text, sim.tools, sim.runSetup())
            ed.ran(gen)
            ranGen = gen
            return true
        }

        fun shown(): List<SimIssue> = shownIssues(ed, sim.run)

        fun codes(line: Int): List<IssueCode> = shown().filter { it.line == line }.map { it.code }

        /** 글 전체를 바로 돌린 문제(빈 글이면 화면은 아무것도 안 보인다) */
        fun full(text: String = ed.text): List<SimIssue> = if (text.isBlank()) emptyList() else sim.runOf(text, sim.tools).issues

        fun key(main: String) = sim.press(CncRows.flatten().first { it.main == main })

        fun keys(s: String) = s.forEach { key(it.toString()) }
    }

    private class Clip : ClipboardManager {
        var held: AnnotatedString? = null
        override fun getText(): AnnotatedString? = held
        override fun setText(annotatedString: AnnotatedString) {
            held = annotatedString
        }
    }

    private fun Screen.pc(k: Key, ch: Char? = null, ctrl: Boolean = false, shift: Boolean = false, clip: Clip = Clip()): Boolean =
        pcKey(KeyEvent(k, KeyEventType.KeyDown, codePoint = ch?.code ?: 0, isCtrlPressed = ctrl, isShiftPressed = shift), sim, clip)

    private fun Screen.pcType(s: String) = s.forEach { assertTrue(pc(Key.A, it), "글자 키 $it") }

    // ---------------------------------------------------------------- 5. 지키는 것(무작위)

    /**
     * 무작위 조작 뒤에도: ① 쓰는 줄이 없으면 검사 글 = 편집기 글 ② 쓰는 줄이 있으면 커서 · 고른 범위가 그 줄 안이고 검사 글은 그 줄만 다르다
     * ③ 검사 글이 바뀌면 번호가 오른다(화면이 다시 돈다) ④ 0.3초 뒤 화면의 결과는 검사 글의 결과이고, 쓰는 줄 말고는 가려진 줄이 없다
     * ⑤ 쓰는 줄이 없을 때 0.3초 뒤 보이는 문제 = 글 전체의 문제(숨은 오류가 남지 않는다) ⑥ 궤적 보기(ensureRun) 뒤 결과 = 글 전체의 결과
     */
    @Test
    fun randomEditingKeepsCheckedTextHonest() {
        val words = listOf("X", "Z", "G", "0", "1", "4", ".", "-", " ", ";", "(", ")", "M", "3", "F", "T", ",", "/", "N", "O", "U", "W", "R")
        val pastes = listOf("X", "G1X\nZ", "Z2.", "\n", "M30", "(A\nB)")
        val seeds = 1..36
        for (seed in seeds) {
            val r = Random(seed)
            val s = Screen(if (seed % 3 == 0) "" else "O0001\nG0X40.Z2.\nG1Z-10.F0.2\nX\nM30")
            val ed = s.ed
            repeat(260) { step ->
                val beforeChecked = ed.checkedText
                val beforeGen = ed.checkGen
                var afterRun = false
                var afterEnsure = false
                val op = r.nextInt(100)
                when (op) {
                    in 0..33 -> ed.insert(words[r.nextInt(words.size)])
                    in 34..41 -> ed.newline()
                    in 42..53 -> ed.backspace()
                    in 54..57 -> ed.deleteForward()
                    in 58..63 -> ed.moveTo(r.nextInt(ed.text.length + 1), extend = r.nextInt(4) == 0)
                    in 64..67 -> ed.vertical(if (r.nextBoolean()) 1 else -1, extend = r.nextInt(5) == 0)
                    in 68..70 -> ed.left(r.nextInt(4) == 0)
                    in 71..73 -> ed.right(r.nextInt(4) == 0)
                    74 -> ed.home(r.nextInt(4) == 0)
                    75 -> ed.end(r.nextInt(4) == 0)
                    in 76..81 -> ed.undo()
                    in 82..85 -> ed.redo()
                    86 -> ed.selectAll()
                    87 -> ed.selectWordAt(r.nextInt(ed.text.length + 1))
                    in 88..89 -> ed.paste(pastes[r.nextInt(pastes.size)])
                    90 -> ed.finishLine()
                    91 -> {
                        s.sim.ensureRun()
                        afterEnsure = true
                    }
                    92 -> if (r.nextInt(6) == 0) ed.replaceAll("G0X1.\nG1Z-2.F0.1\nM30", r.nextInt(10)) else ed.insert("X")
                    93 -> s.key("⏎")
                    94 -> s.key("⌫")
                    95 -> s.key("X")
                    else -> afterRun = s.quiesce() || true
                }
                val at = "씨앗 $seed 걸음 $step 조작 $op 글 ${ed.text.replace("\n", "⏎")} 검사 글 ${ed.checkedText.replace("\n", "⏎")} 쓰는 줄 ${ed.writingLine}"
                val w = ed.writingLine
                if (w == null) {
                    assertEquals(ed.text, ed.checkedText, "쓰는 줄이 없는데 검사 글이 다르다 — $at")
                } else {
                    assertEquals(w, ed.cursorLine, "쓰는 줄에 커서가 없다 — $at")
                    ed.anchor?.let { assertEquals(w, ed.lineOf(it), "고른 범위가 쓰는 줄 밖 — $at") }
                    val a = ed.text.split('\n')
                    val b = ed.checkedText.split('\n')
                    assertEquals(a.size, b.size, "줄 수가 다르다 — $at")
                    for (i in a.indices) if (i != w) assertEquals(a[i], b[i], "쓰는 줄이 아닌 ${i + 1}줄이 다르다 — $at")
                }
                if (ed.checkedText != beforeChecked) assertTrue(ed.checkGen > beforeGen, "검사 글이 바뀌었는데 번호가 그대로 — $at")
                if (afterEnsure) {
                    assertNull(ed.writingLine, at)
                    val want = s.sim.runOf(ed.text, s.sim.tools)
                    assertEquals(want.issues, s.sim.run.issues, "궤적 보기 뒤 결과가 글 전체의 결과가 아니다 — $at")
                    assertEquals(want.stop, s.sim.run.stop, at)
                    assertEquals(want.steps.size, s.sim.run.steps.size, at)
                    assertEquals(s.full(), s.shown(), "궤적 보기 뒤 숨은 문제가 있다 — $at")
                }
                if (afterRun) {
                    for (l in 0 until ed.lineCount) if (l != ed.writingLine) assertFalse(ed.hides(l), "0.3초 뒤에도 ${l + 1}줄이 가려져 있다 — $at")
                    val whole = s.full(ed.checkedText)
                    assertEquals(whole.filterNot { it.line - 1 == ed.writingLine }, s.shown(), "보이는 문제가 검사 글의 문제와 다르다 — $at")
                    if (ed.writingLine == null) assertEquals(s.full(), s.shown(), "쓰는 줄이 없는데 숨은 문제가 있다 — $at")
                }
            }
            // 끝: 줄을 끝내고 0.3초 — 글 전체의 문제가 다 보인다
            ed.finishLine()
            s.quiesce()
            assertEquals(s.full(), s.shown(), "씨앗 $seed 끝")
        }
    }

    // ---------------------------------------------------------------- 5. 장면

    /** "X" 만 침: 어느 줄이든(한 줄짜리 · 첫 줄 · 가운데 빈 줄 · 줄바꿈 없는 끝 줄) 쓰는 동안 그 줄 문제가 없고, ⏎ · 위 · 아래 · 다른 줄 누르기로 끝내면 보인다 */
    @Test
    fun xAloneOnAnyLineThenLeave() {
        data class Case(val text: String, val cursor: Int, val line: Int)
        val cases = listOf(
            Case("", 0, 1),
            Case("\nG0X40.Z2.\nM30", 0, 1),
            Case("G0X40.Z2.\n\nM30", 10, 2),
            Case("G0X40.Z2.\n", 10, 2),
        )
        val leaves: List<Pair<String, (Screen) -> Unit>> = listOf(
            "⏎" to { s -> s.key("⏎") },
            "위" to { s -> s.ed.vertical(-1) },
            "아래" to { s -> s.ed.vertical(1) },
            "PC ;" to { s -> s.pcType(";") },
            "PC Enter" to { s -> s.pc(Key.Enter) },
            "PC 숫자 키패드 Enter" to { s -> assertTrue(pcKey(numPadKey(AwtKey.VK_ENTER, '\n'), s.sim, Clip())) },
        )
        for (c in cases) for ((name, leave) in leaves) {
            val s = Screen(c.text, c.cursor)
            val before = s.shown()
            val run = s.sim.run
            s.key("X")
            assertEquals(c.line - 1, s.ed.writingLine, "$c")
            assertEquals(before.filterNot { it.line == c.line }, s.shown(), "쓰는 동안 다른 줄의 문제가 바뀌었다 — $c")
            assertFalse(s.quiesce(), "쓰는 동안 다시 돌았다 — $c")
            assertTrue(run === s.sim.run)
            assertEquals(emptyList(), s.codes(c.line), "$c")
            leave(s)
            assertNull(s.ed.writingLine, "$name 로 끝나지 않았다 — $c")
            assertTrue(s.quiesce(), "$name 뒤 다시 돌지 않았다 — $c")
            assertTrue(IssueCode.NO_NUMBER in s.codes(c.line), "$name 뒤 ${c.line}줄 문제 ${s.shown()} — $c")
            assertEquals(s.full(), s.shown(), "$name — $c")
        }
    }

    /** 다른 줄의 문제(확인 + 오류)는 쓰는 동안 그대로, 쓰는 줄 것만 숨는다. 실행이 멈춘 줄도 그대로 */
    @Test
    fun otherLinesKeepIssues() {
        val text = "G00 X44 Z2.\nG01 X40. X41. F0.2\n\nM30"
        val s = Screen(text, text.indexOf("\n\n") + 1)
        val before = s.shown()
        assertTrue(IssueCode.NO_POINT_COORD in s.codes(1), "${s.shown()}")
        assertTrue(IssueCode.DUP_ADDRESS in s.codes(2), "${s.shown()}")
        val stop = s.sim.run.stop
        s.keys("G1X")
        assertEquals(2, s.ed.writingLine)
        assertFalse(s.quiesce())
        assertEquals(before, s.shown())
        assertEquals(stop, s.sim.run.stop)
        // 문제가 있는 줄을 고치기 시작하면 그 줄 것만 숨는다
        s.ed.moveTo(s.ed.lineEnd(1))
        s.quiesce()
        assertTrue(IssueCode.DUP_ADDRESS in s.codes(2), "${s.shown()}")
        s.key("⌫")
        assertEquals(emptyList(), s.codes(2))
        assertTrue(IssueCode.NO_POINT_COORD in s.codes(1), "${s.shown()}")
    }

    /** 줄 가운데를 고칠 때: 고치는 동안 숨고, 도로 돌려놓고 떠나면 다시 돌지 않으며, 고쳐 놓고 떠나면 0.3초 뒤 새 문제가 보인다 */
    @Test
    fun editInTheMiddleOfALine() {
        val text = "G01 X40. Z-10. F0.2\nM30"
        val s = Screen(text, text.indexOf(" Z"))
        val base = s.codes(1)
        assertFalse(IssueCode.NO_POINT_COORD in base)
        s.key("⌫") // X40. → X40
        assertEquals("G01 X40 Z-10. F0.2", s.ed.lineText(0))
        assertFalse(s.quiesce())
        assertEquals(emptyList(), s.codes(1))
        s.key(".")
        s.ed.vertical(1)
        assertFalse(s.quiesce(), "도로 돌려놓고 떠났는데 다시 돌았다")
        assertEquals(base, s.codes(1))
        s.ed.moveTo(text.indexOf(" Z"))
        s.key("⌫")
        s.ed.vertical(1)
        assertTrue(s.quiesce())
        assertTrue(IssueCode.NO_POINT_COORD in s.codes(1), "${s.shown()}")
        // 고치면 사라진다
        s.ed.moveTo(text.indexOf(" Z") - 1)
        s.key(".")
        assertEquals(emptyList(), s.codes(1), "고치기 시작했는데 옛 문제가 남았다")
        s.ed.vertical(1)
        assertEquals(emptyList(), s.codes(1), "떠난 직후 옛 문제가 되살아났다")
        s.quiesce()
        assertEquals(base, s.codes(1))
        assertEquals(s.full(), s.shown())
    }

    /** 여러 줄을 골라 지움 · 다 지움 */
    @Test
    fun multiLineSelectionDelete() {
        val s = Screen("G0X40.Z2.\nX\nG1Z\nM30", 0)
        assertTrue(IssueCode.NO_NUMBER in s.codes(2), "${s.shown()}")
        s.ed.select(s.ed.lineStart(1) until s.ed.lineStart(3))
        s.key("⌫")
        assertEquals("G0X40.Z2.\nM30", s.ed.text)
        assertNull(s.ed.writingLine)
        assertTrue(s.quiesce())
        assertEquals(s.full(), s.shown())
        assertTrue(s.shown().none { it.code == IssueCode.NO_NUMBER })
        // 끝 줄을 쓰다가 전부 골라 지움
        s.ed.moveTo(s.ed.text.length)
        s.key("X")
        assertEquals(1, s.ed.writingLine)
        s.ed.selectAll()
        assertNull(s.ed.writingLine)
        s.key("⌫")
        assertEquals("", s.ed.text)
        s.quiesce()
        assertEquals(emptyList(), s.shown())
    }

    /**
     * 한 줄짜리 글을 다 지우면(빈 편집기) 옛 결과의 문제가 하나도 보이지 않는다.
     * [2026-11-11 검증에서 찾아 고침] 블록이 없는 한 줄(`O1234` · 주석만)은 'M30 / M02 없이 끝납니다' 안내가 줄 0 으로 붙는데,
     * 다 지워도 검사 글이 그대로라 빈 편집기에 그 안내가 남았다 — 이제 글이 비면 줄을 끝낸다(설계 20.5)
     */
    @Test
    fun clearingAOneLineProgramShowsNothing() {
        val bad = ArrayList<String>()
        for (text in listOf("G0X40.Z2.", "X", "O1234", "T0101", "(TEST)", "M30")) {
            val s = Screen(text)
            s.ed.selectAll()
            s.key("⌫")
            assertEquals("", s.ed.text)
            if (s.shown().isNotEmpty()) bad += "'$text' 를 골라 지운 직후: ${s.shown()}"
            s.quiesce()
            if (s.shown().isNotEmpty()) bad += "'$text' 를 골라 지우고 0.3초 뒤: ${s.shown()}"
            // 글자를 하나씩 지워도
            val t = Screen(text)
            repeat(text.length) { t.key("⌫") }
            assertEquals("", t.ed.text)
            if (t.shown().isNotEmpty()) bad += "'$text' 를 한 글자씩 다 지운 직후: ${t.shown()}"
        }
        assertEquals(emptyList(), bad, "빈 편집기에 문제가 보인다")
    }

    /** 빈 줄 · 주석 줄 · O 번호 줄 */
    @Test
    fun blankCommentAndONumberLines() {
        // 주석: 여는 괄호만 친 동안 숨고, 닫으면 문제가 없다
        val c = Screen("G0X40.Z2.\n")
        c.sim.shift = ShiftState.ONCE
        c.key("O") // (
        assertEquals("G0X40.Z2.\n(", c.ed.text)
        assertEquals(emptyList(), c.codes(2))
        c.ed.vertical(-1)
        c.quiesce()
        assertTrue(IssueCode.COMMENT_OPEN in c.codes(2), "${c.shown()}")
        c.ed.moveTo(c.ed.text.length)
        c.keys("T1")
        c.sim.shift = ShiftState.ONCE
        c.key("N") // )
        assertEquals("(T1)", c.ed.lineText(1))
        assertEquals(emptyList(), c.codes(2))
        c.key("⏎")
        c.quiesce()
        assertEquals(emptyList(), c.codes(2))
        assertEquals(c.full(), c.shown())
        // 빈 줄만 더함: 쓰는 줄이 생기지 않고 문제도 생기지 않는다
        val before = c.shown()
        c.key("⏎")
        c.key("⏎")
        assertNull(c.ed.writingLine)
        c.quiesce()
        assertEquals(before.map { it.code }, c.shown().map { it.code })
        // O 번호 줄: 쓰는 동안 아무것도 없고, 끝내면 글 전체의 문제 그대로
        val o = Screen()
        o.keys("O12")
        assertEquals(emptyList(), o.shown())
        assertNull(o.sim.run.program.units.firstOrNull()?.number)
        o.key("⏎")
        o.quiesce()
        assertEquals(12, o.sim.run.program.units.first().number)
        assertEquals(o.full(), o.shown())
    }

    /** 끝 줄(줄바꿈 없이 끝)에 쓰다 만 글 + 궤적 보기 · 저장: 숨은 오류가 든 프로그램이 조용히 돌지 않는다 */
    @Test
    fun unfinishedLastLineRunAndSave() {
        val s = Screen("O0007\nG0X40.Z2.\n")
        s.keys("G1Z")
        assertEquals(2, s.ed.writingLine)
        assertEquals(emptyList(), s.codes(3))
        assertNull(s.sim.run.stop, "쓰는 동안의 결과는 고치기 전 글의 것")
        s.sim.ensureRun()
        val stop = assertNotNull(s.sim.run.stop, "궤적 보기: 쓰다 만 줄에서 멈춰야 한다")
        assertEquals(3, stop.line)
        assertEquals(IssueCode.NO_NUMBER, stop.code)
        assertTrue(IssueCode.NO_NUMBER in s.codes(3))
        assertEquals(s.sim.runOf(s.ed.text, s.sim.tools).steps.size, s.sim.run.steps.size)
        // 화면의 [저장] 차례(줄 끝 → 저장): 저장한 글 = 편집기 글, 0.3초 뒤 문제가 보인다
        val t = Screen("O0008\nG0X40.Z2.\n")
        t.keys("G1Z")
        t.ed.finishLine()
        assertEquals("O0008 저장했습니다", t.sim.requestSave())
        assertEquals("O0008\nG0X40.Z2.\nG1Z", t.sim.programs.load(8))
        assertTrue(t.quiesce())
        assertTrue(IssueCode.NO_NUMBER in t.codes(3))
        // 0.3초가 지나기 전에 궤적 보기를 눌러도
        val q = Screen("G0X40.Z2.\n")
        q.keys("G1Z")
        q.key("⏎")
        q.sim.ensureRun()
        assertEquals(2, q.sim.run.stop?.line)
        assertTrue(IssueCode.NO_NUMBER in q.codes(2))
    }

    /** 붙여넣기 · 예제 · 프로그램 불러오기 · 새 프로그램 직후 */
    @Test
    fun pasteAndLoadAreCheckedAtOnce() {
        val s = Screen("G0X40.Z2.\n")
        s.ed.paste("G1Z")
        assertNull(s.ed.writingLine)
        assertTrue(s.quiesce())
        assertTrue(IssueCode.NO_NUMBER in s.codes(2))
        // PC Ctrl+V
        val p = Screen("G0X40.Z2.\n")
        val clip = Clip().apply { held = AnnotatedString("x") }
        assertTrue(p.pc(Key.V, ctrl = true, clip = clip))
        assertEquals("G0X40.Z2.\nX", p.ed.text)
        assertNull(p.ed.writingLine)
        p.quiesce()
        assertTrue(IssueCode.NO_NUMBER in p.codes(2))
        // 쓰던 중에 예제 · 저장한 프로그램 · 새 프로그램
        p.key("1")
        assertEquals(1, p.ed.writingLine)
        p.sim.perform(Pending.Example(0))
        assertNull(p.ed.writingLine)
        p.quiesce()
        assertEquals(p.full(), p.shown())
        p.sim.programs.save(31, "O0031\nG0X40.Z2.\nG1Z\nM30")
        p.key("X")
        p.sim.perform(Pending.Load(31))
        assertNull(p.ed.writingLine)
        p.quiesce()
        assertTrue(IssueCode.NO_NUMBER in p.codes(3))
        p.ed.moveTo(p.ed.text.length)
        p.key("X")
        p.sim.perform(Pending.New)
        assertEquals("", p.ed.text)
        assertNull(p.ed.writingLine)
        p.quiesce()
        assertEquals(emptyList(), p.shown())
    }

    /** 폰 CNC 키패드의 ⌫: 줄 안에서는 쓰는 중, 줄 처음에서 앞 줄과 합치면 끝. PC 의 ; 뒤에 이어 쓰면 다시 숨는다. 되돌리기 */
    @Test
    fun backspaceEobAndUndo() {
        val s = Screen("G0X40.Z2.\n")
        s.keys("X1")
        s.key("⌫")
        s.key("⌫")
        assertEquals(1, s.ed.writingLine)
        s.key("⌫") // 앞 줄과 합침
        assertEquals("G0X40.Z2.", s.ed.text)
        assertNull(s.ed.writingLine)
        assertTrue(s.quiesce())
        assertEquals(s.full(), s.shown())
        // ; 뒤에 이어 씀
        val e = Screen("G0X40.Z2.\n")
        e.pcType("x;")
        assertNull(e.ed.writingLine)
        e.quiesce()
        assertTrue(e.codes(2).isNotEmpty(), "${e.shown()}")
        e.pcType("z")
        assertEquals(1, e.ed.writingLine)
        assertEquals(emptyList(), e.codes(2))
        // 되돌리기: 다른 줄을 끝낸 뒤 되돌리면(그 줄을 고침) 그 줄이 쓰는 줄이 되고, 떠나면 글 전체를 본다
        val u = Screen("G0X40.Z2.\n")
        u.key("X")
        u.ed.vertical(-1)
        u.quiesce()
        assertTrue(IssueCode.NO_NUMBER in u.codes(2), "${u.shown()}")
        u.ed.undo()
        assertEquals("G0X40.Z2.\n", u.ed.text)
        u.ed.vertical(-1)
        u.quiesce()
        assertEquals(u.full(), u.shown())
        assertTrue(u.shown().none { it.code == IssueCode.NO_NUMBER })
        u.ed.redo()
        u.ed.moveTo(0)
        u.quiesce()
        assertTrue(IssueCode.NO_NUMBER in u.codes(2), "${u.shown()}")
    }

    /** PC 키보드: 글자 · 방향키 · Ctrl+F · Ctrl+A · Ctrl+X · Home/End */
    @Test
    fun pcKeyboard() {
        val s = Screen("G0X40.Z2.\n\nM30", 10)
        s.pcType("g1z")
        assertEquals("G1Z", s.ed.lineText(1))
        assertEquals(1, s.ed.writingLine)
        assertEquals(emptyList(), s.codes(2))
        s.pc(Key.MoveHome)
        s.pc(Key.MoveEnd)
        s.pc(Key.DirectionLeft)
        s.pc(Key.DirectionRight)
        assertEquals(1, s.ed.writingLine, "같은 줄 안에서 움직임")
        s.pc(Key.DirectionDown)
        assertNull(s.ed.writingLine)
        s.quiesce()
        assertTrue(IssueCode.NO_NUMBER in s.codes(2))
        // 끝 줄에서 쓰다 Ctrl+F
        s.pc(Key.MoveEnd)
        s.pcType("x")
        assertEquals(2, s.ed.writingLine)
        s.pc(Key.F, ctrl = true)
        assertNull(s.ed.writingLine)
        s.sim.findOpen = false
        // 한 줄 안에서 고른 글을 잘라냄(Ctrl+X) = 쓰는 중, 여러 줄 Ctrl+A = 끝
        s.pc(Key.DirectionLeft, shift = true)
        val clip = Clip()
        s.pc(Key.X, ctrl = true, clip = clip)
        assertEquals("X", clip.held?.text)
        assertEquals(2, s.ed.writingLine)
        s.pcType("y")
        s.pc(Key.A, ctrl = true)
        assertNull(s.ed.writingLine)
        s.quiesce()
        assertEquals(s.full(), s.shown())
    }

    /** 찾기 줄이 열려 있을 때 친 글자는 찾는 글로 가고 편집기 글 · 쓰는 줄을 만들지 않는다 */
    @Test
    fun findBarTakesKeys() {
        val s = Screen("G0X40.Z2.\nG1Z-5.F0.1\nM30", 0)
        s.key("X")
        assertEquals(0, s.ed.writingLine)
        s.ed.finishLine()
        s.sim.openFind()
        s.keys("Z")
        assertEquals("Z", s.sim.query)
        assertNull(s.ed.writingLine)
        assertEquals("XG0X40.Z2.\nG1Z-5.F0.1\nM30", s.ed.text)
        s.quiesce()
        assertEquals(s.full(), s.shown())
    }

    // ---------------------------------------------------------------- 살펴보기(판정 없이 적는다 — 보고서에 옮김)

    /** 같은 글을 두 번 돌린 결과가 값으로 같은가(같으면 Compose 상태가 안 바뀌어 궤적 창이 다시 만들어지지 않는다) · 여러 줄을 고른 채 글자를 치면 */
    @Test
    fun probes() {
        val s = Screen(SimShotsTest.ERRORS)
        val a = s.sim.runOf(s.ed.text, s.sim.tools)
        val b = s.sim.runOf(s.ed.text, s.sim.tools)
        println("PROBE 같은 글 두 번(오류 보기): 같음=${a == b} 걸음=${a.steps.size}")
        for ((i, ex) in exampleList.withIndex()) {
            val x = s.sim.runOf(ex.text, ex.tools)
            val y = s.sim.runOf(ex.text, ex.tools)
            println("PROBE 같은 글 두 번(예제 $i): 같음=${x == y} 걸음=${x.steps.size} program 같음=${x.program == y.program} steps 같음=${x.steps == y.steps} ghosts 같음=${x.ghosts == y.ghosts}")
        }
        // 끝 줄에 M30 을 치는 동안 'M30 / M02 없이 끝납니다' 안내(앞 줄에 붙은 것)가 남는가
        val e = Screen("O0001\nT0101\nG97 S1000 M03\nG99\nG00 X40. Z2.\nG01 Z-10. F0.2\n")
        val had = e.shown().filter { it.code == IssueCode.NO_END }
        e.keys("M30")
        e.quiesce()
        println("PROBE 끝 줄에 M30 을 침: 쓰는 줄=${e.ed.writingLine} 치기 전 안내=$had 치는 중 보이는 안내=${e.shown().filter { it.code == IssueCode.NO_END }}")
        e.key("⏎")
        e.quiesce()
        println("PROBE M30 뒤 ⏎: 보이는 안내=${e.shown().filter { it.code == IssueCode.NO_END }}")
        val m = Screen("G0X40.Z2.\nG1Z-5.F0.1\nG1X42.\nM30", 0)
        m.ed.select(m.ed.lineStart(1) until m.ed.lineEnd(2))
        m.key("X")
        m.quiesce()
        println("PROBE 여러 줄을 고르고 X: 글=${m.ed.text.replace("\n", "⏎")} 쓰는 줄=${m.ed.writingLine} 2줄 문제=${m.codes(2)}")
    }

    // ---------------------------------------------------------------- 6. 숫자 키패드

    /** 숫자 칸: 숫자 키패드의 키(창이 받는 그대로)가 글자 줄의 같은 키와 똑같은 키로 읽힌다. 소수점은 한 번만, 빈칸에서 . 부터 치면 0. */
    @Test
    fun numPadEqualsMainRowInNumberField() {
        val pairs = listOf(
            numPadKey(AwtKey.VK_DECIMAL, '.') to awtKey(AwtKey.VK_PERIOD, '.'),
            numPadKey(AwtKey.VK_SUBTRACT, '-') to awtKey(AwtKey.VK_MINUS, '-'),
            numPadKey(AwtKey.VK_ENTER, '\n') to awtKey(AwtKey.VK_ENTER, '\n'),
        ) + (0..9).map { numPadKey(AwtKey.VK_NUMPAD0 + it, '0' + it) to awtKey(AwtKey.VK_0 + it, '0' + it) }
        for ((pad, main) in pairs) {
            assertNotNull(NumEdit.keyOf(pad), "숫자 키패드 키를 못 읽음: ${pad.key}")
            assertEquals(NumEdit.keyOf(main), NumEdit.keyOf(pad), "${pad.key}")
        }
        fun typed(initial: String, es: List<KeyEvent>): String = NumEdit.type(initial, es.map { assertNotNull(NumEdit.keyOf(it)) }.joinToString(""))
        val dot = numPadKey(AwtKey.VK_DECIMAL, '.')
        fun d(n: Int) = numPadKey(AwtKey.VK_NUMPAD0 + n, '0' + n)
        assertEquals("0.", typed("", listOf(dot)))
        assertEquals("0.5", typed("", listOf(dot, d(5))))
        assertEquals("0.5", typed("", listOf(dot, dot, d(5))), "소수점 두 번")
        assertEquals("1.25", typed("", listOf(d(1), dot, d(2), dot, d(5))), "이미 소수점이 있는 값")
        assertEquals("0.", typed("12.5", listOf(dot)), "값이 있는 칸을 열고 . 부터: 덮어쓴다")
        assertEquals("-0.5", typed("", listOf(numPadKey(AwtKey.VK_SUBTRACT, '-'), dot, d(5))))
        // 숫자 줄 . 과 똑같은 결과
        val mainDot = awtKey(AwtKey.VK_PERIOD, '.')
        for (init in listOf("", "3", "3.1", "-", "-0")) assertEquals(typed(init, listOf(d(7), mainDot, d(1), mainDot)), typed(init, listOf(d(7), dot, d(1), dot)), "처음 값 '$init'")
        // NumLock 이 꺼진 . 자리(Delete)는 소수점으로 읽지 않는다
        assertNull(NumEdit.keyOf(numPadKey(AwtKey.VK_DELETE, '\u007f')))
    }

    /** 시뮬레이터 편집기: 숫자 키패드의 숫자 · . · − · + · / 가 글자 그대로 들어가고(쓰는 줄), Enter 는 줄을 끝낸다 */
    @Test
    fun numPadInEditor() {
        val s = Screen()
        val clip = Clip()
        fun pad(code: Int, ch: Char) = assertTrue(pcKey(numPadKey(code, ch), s.sim, clip), "$ch")
        s.pcType("x")
        pad(AwtKey.VK_SUBTRACT, '-')
        pad(AwtKey.VK_NUMPAD1, '1')
        pad(AwtKey.VK_DECIMAL, '.')
        pad(AwtKey.VK_NUMPAD5, '5')
        pad(AwtKey.VK_DECIMAL, '.')
        assertEquals("X-1.5.", s.ed.text)
        assertEquals(0, s.ed.writingLine)
        assertEquals(emptyList(), s.shown())
        pad(AwtKey.VK_ENTER, '\n')
        assertNull(s.ed.writingLine)
        s.quiesce()
        assertEquals(s.full(), s.shown())
        pad(AwtKey.VK_DIVIDE, '/')
        pad(AwtKey.VK_ADD, '+')
        assertEquals("X-1.5.\n/+", s.ed.text)
    }
}
