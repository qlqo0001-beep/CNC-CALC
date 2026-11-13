package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-13 사용자 답 "그 줄을 다 입력할 때까지 에러 표기하지 않음" — 설계 21.6) — 화면 없이. 구현자의 시험과 따로 쓴 검사다.
 * 화면의 0.3초 효과가 하는 일([rerun])을 손으로 부르고 화면과 같은 [shownIssues] 로 보일 문제를 본다. '마지막 줄' 은 편집기 글에서 따로 센다. 글은 새로 쓴 것.
 */
class Verify1113EditorModelTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-v1113m", ".properties").apply { delete() })

    /** 화면의 0.3초 효과 그대로: 검사 글로 돌고 그 번호의 결과가 올랐다고 알린다 */
    private fun SimState.rerun() {
        val gen = editor.checkGen
        run = runOf(editor.checkedText, tools)
        runKey = Triple(editor.checkedText, tools, runSetup())
        editor.ran(gen)
    }

    private fun SimState.shown(): List<SimIssue> = shownIssues(editor, run)

    private fun sim(text: String, cursor: Int = text.length, tools: Map<Int, ToolOffset>? = null) = SimState(store(), tools).apply {
        editor.replaceAll(text, cursor)
        rerun()
    }

    private fun key(main: String) = CncRows.flatten().first { it.main == main }

    private fun SimState.type(s: String) = s.forEach { ch -> press(key(if (ch == ' ') "SP" else ch.toString())) }

    /** 따로 센 '마지막 줄': 그 줄(0부터) 뒤 줄이 모두 빈칸뿐 */
    private fun EditorState.lastLine(line: Int) = text.split('\n').drop(line + 1).all { it.isBlank() }

    /** 글 끝 문제 한 가지(구현자의 END_CASES 와 다른 글): 앞 글(끝은 빈 줄 — 커서가 거기), 끝내는 글, 끝내도 남는 글 */
    private class End(val code: IssueCode, val text: String, val done: String, val wrong: String, val tools: Map<Int, ToolOffset>? = null)

    private val ends = listOf(
        End(IssueCode.NO_END, HEAD + "G00 X50. Z3.\nG01 Z-5. F0.15\nX52.\n", "M02", "M0"),
        End(IssueCode.COMP_NOT_CANCELLED, HEAD + "G42\nG00 X30. Z20.\nZ1.\nG01 Z-6. F0.15\nX36. F.3\n", "G40", "Z5.", mapOf(1 to ToolOffset(0.4, 3))),
        End(IssueCode.CORNER_NO_NEXT, HEAD + "G00 X30. Z2.\nG01 Z0. F0.15\nX34.,R1.\n", "Z-6.", "M30"),
        End(IssueCode.CYCLE_PQ_NOT_FOUND, HEAD + "G00 X45. Z2.\nG71 U1.5 R0.5\nG71 P30 Q40 U0.3 W0.1 F0.25\nN30 G00 X28.\nG01 Z-12.\n", "N40 X45.", "N4 X45."),
        End(IssueCode.SUB_NO_M99, HEAD + "G00 X40. Z2.\nM98 P5\nM30\nO0005\nG01 Z-4. F0.2\n", "M99", "M9"),
    )

    /** 다른 글로 다섯 가지: 끝 줄을 치는 동안 · ⏎ 직후 없음, 바르게 끝내면 새 결과에도 없음, 틀리게 끝내면 새 결과 뒤 보임(멈춤 오류는 멈춘 줄도) */
    @Test
    fun fiveCodesWithOtherTexts() {
        for (c in ends) {
            fun SimState.has() = shown().any { it.code == c.code }
            val s = sim(c.text, tools = c.tools)
            assertTrue(s.has(), "치기 전(${c.code}): ${s.run.issues}")
            s.type(c.done)
            assertFalse(s.has(), "'${c.done}' 를 쓰는 중(${c.code}): ${s.shown()}")
            s.press(key("⏎"))
            assertFalse(s.has(), "⏎ 직후(${c.code}): ${s.shown()}")
            s.rerun()
            assertTrue(s.run.issues.none { it.code == c.code }, "'${c.done}' 로 끝났다(${c.code}): ${s.run.issues}")
            assertEquals(s.run.issues, s.shown(), "새 결과 뒤에는 가린 것이 없다(${c.code})")

            val w = sim(c.text, tools = c.tools)
            w.type(c.wrong)
            assertFalse(w.has(), "'${c.wrong}' 를 쓰는 중(${c.code}): ${w.shown()}")
            w.press(key("⏎"))
            w.rerun()
            assertTrue(w.has(), "'${c.wrong}' 로는 안 끝난다(${c.code}): ${w.run.issues}")
            assertEquals(w.run.issues, w.shown(), "새 결과 뒤에는 가린 것이 없다(${c.code})")
            if (c.code.level == IssueLevel.STOP) assertEquals(c.code, w.run.stop?.code, "멈춘 줄(${c.code})")
        }
    }

    /** ▶ 궤적 · 공구 설정이 부르는 [SimState.ensureRun]: 끝 줄을 쓰다 만 채(틀린 글) 불러도 곧바로(0.3초 없이) 모두 보이고 멈춘 줄도 가리지 않는다 */
    @Test
    fun ensureRunRevealsAtOnce() {
        for (c in ends) {
            val s = sim(c.text, tools = c.tools)
            s.type(c.wrong)
            assertFalse(s.shown().any { it.code == c.code }, "쓰는 중(${c.code})")
            s.ensureRun()
            assertEquals(null, s.editor.writingLine)
            assertTrue(s.shown().any { it.code == c.code }, "궤적 창을 열 때 숨은 채(${c.code}): ${s.shown()}")
            assertEquals(s.run.issues, s.shown(), "${c.code}")
            s.run.stop?.let { assertFalse(hidden(s.editor, s.run, it), "멈춘 줄이 숨은 채(${c.code}) — $it") }
        }
    }

    /** 끝 줄에 'M3' ⏎ 뒤 새 결과 전에 가운데 줄로 가서 고치기 시작: 새 결과가 오면 가운데 줄을 쓰는 중이어도 글 끝 안내가 보인다 */
    @Test
    fun settledLastLineThenMiddleEditBeforeTheResult() {
        val s = sim(ends[0].text)
        s.type("M3")
        s.press(key("⏎"))
        s.editor.moveTo(s.editor.lineEnd(2))
        s.type(" ")
        assertEquals(2, s.editor.writingLine)
        s.rerun()
        val noEnd = s.shown().filter { it.code == IssueCode.NO_END }
        assertEquals(1, noEnd.size, "가운데 줄을 쓰는 중, 새 결과 뒤 — ${s.shown()}")
        assertEquals(8, noEnd.single().line, "M3 줄(8줄)")
    }

    /**
     * 이미 끝낸 다른 줄 때문에 붙은 같은 코드의 문제는 끝 줄을 쓰는 동안에도 보여야 한다(사용자 답은 "그 줄" — 21.1 다른 줄의 문제는 그대로):
     * 가운데 `,C` 다음이 G00(다 쓴 줄) · 가운데 부프로그램에 M99 없음(뒤에 다른 부프로그램) · M30 이 이미 있는데 G40 을 빠뜨림.
     * 쓰는 줄로는 고칠 수 없다. [2026-11-13 검증에서 찾음] 코드만으로 가려 셋 다 숨었다 — 고침: 글 끝에서 생긴 것만 가린다(설계 21.6)
     */
    @Test
    fun otherLinesTextEndCodesStayWhileWritingTheLastLine() {
        data class Other(val what: String, val code: IssueCode, val line: Int, val text: String, val typed: String, val tools: Map<Int, ToolOffset>? = null)
        val cases = listOf(
            Other("가운데 ,C 다음 G00", IssueCode.CORNER_NO_NEXT, 7, HEAD + "G00 X36. Z2.\nG01 Z0. F0.2\nX40.,C1.\nG00 X50.\nZ5.\n", "M"),
            Other("가운데 부프로그램 M99 없음", IssueCode.SUB_NO_M99, 0, HEAD + "G00 X40. Z2.\nM98 P2\nM30\nO0002\nG01 Z-10. F0.2\nO0003\nG01 X50.\nM99\n", "G"),
            Other("M30 뒤에 덧붙임 — G40 빠뜨림", IssueCode.COMP_NOT_CANCELLED, 0, HEAD + "G42\nG00 X40. Z20.\nZ2.\nG01 Z-10. F0.2\nX46. F.3\nM30\n", "O", mapOf(1 to ToolOffset(0.8, 3))),
        )
        val hiddenOnes = cases.mapNotNull { c ->
            val s = sim(c.text, tools = c.tools)
            val before = s.shown().filter { it.code == c.code }
            assertTrue(before.isNotEmpty() && (c.line == 0 || before.any { it.line == c.line }), "${c.what}: 치기 전 ${s.run.issues}")
            s.type(c.typed)
            assertTrue(s.editor.writingLine != null && s.editor.lastLine(s.editor.writingLine!!))
            val now = s.shown().filter { it.code == c.code }
            if (now == before) null else "${c.what}(${c.code} ${before.map { it.line }}줄) — 끝 줄에 '${c.typed}' 하나로 숨음"
        }
        assertEquals(emptyList(), hiddenOnes)
    }

    /**
     * 아직 안 쓴 부프로그램: `M98 P3` · `M30` 뒤 끝 줄에 `O0003` 을 치는 동안 6줄의 '부프로그램이 없어 건너뜀'(SUB_SKIPPED — 확인)도 글이 덜 끝나서 붙는 문제다
     * (아직 안 쓴 N 블록의 CYCLE_PQ_NOT_FOUND 와 같은 꼴 — 그쪽은 가린다). [2026-11-13 검증에서 찾음] ⏎ 까지 남았다 — 고침: 글 끝 문제에 넣음(설계 21.6)
     */
    @Test
    fun subNotWrittenYetWaitsForTheLastLine() {
        val s = sim(HEAD + "G00 X40. Z2.\nM98 P3\nM30\n")
        assertTrue(s.shown().any { it.code == IssueCode.SUB_SKIPPED && it.line == 6 }, "${s.run.issues}")
        s.type("O0003")
        assertEquals(8, s.editor.writingLine?.plus(1))
        assertFalse(s.shown().any { it.code == IssueCode.SUB_SKIPPED }, "끝 줄에 O0003 을 쓰는 중 — 6줄 확인이 남았다: ${s.shown()}")
    }

    /**
     * 막 친 대로 아무렇게나(글자 · 숫자 · ⏎ · ⌫ · 위아래 · 커서 옮김 · 0.3초 · 줄 끝내기 · 궤적) 해도:
     * 줄을 끝내고 새 결과가 오면 가린 것이 없고, [SimState.ensureRun] 뒤에는 곧바로 없다.
     * 새 결과 뒤 줄을 쓰기 시작한 동안(막 끝낸 줄이 없을 때)은 쓰는 줄의 것을 모두 가리고, 그 밖에 가린 것은 (쓰는 줄이 마지막 줄일 때) 글 끝 여섯 코드뿐이다.
     * 같은 코드 가운데 무엇을 가리나(글 끝에서 생긴 것만 — 설계 21.6)는 위 사례 시험들이 고정한다. 마지막 줄은 따로 센다
     */
    @Test
    fun randomEditsNeverLeaveAnythingHidden() {
        val rnd = Random(1113)
        val keys = listOf("G", "0", "1", "X", "Z", "M", "3", "4", "-", ".", "SP", ",", "F", "N", "9")
        val textEnd = setOf(IssueCode.NO_END, IssueCode.SUB_NO_M99, IssueCode.COMP_NOT_CANCELLED, IssueCode.CORNER_NO_NEXT, IssueCode.CYCLE_PQ_NOT_FOUND, IssueCode.SUB_SKIPPED)
        var checkedWriting = 0
        repeat(40) { round ->
            val c = ends[round % ends.size]
            val s = sim(c.text, cursor = rnd.nextInt(c.text.length + 1), tools = c.tools)
            var clean = true // 새 결과 뒤 줄 끝(번호 오름)이 없었다
            repeat(60) { step ->
                val gen = s.editor.checkGen
                val act = rnd.nextInt(100)
                val what = when {
                    act < 50 -> keys[rnd.nextInt(keys.size)].also { s.press(key(it)) }
                    act < 58 -> "⏎".also { s.press(key("⏎")) }
                    act < 66 -> "⌫".also { s.press(key("⌫")) }
                    act < 72 -> "위아래".also { s.editor.vertical(if (rnd.nextBoolean()) 1 else -1) }
                    act < 78 -> "옮김".also { s.editor.moveTo(rnd.nextInt(s.editor.text.length + 1)) }
                    act < 92 -> "0.3초".also { s.rerun() }
                    act < 97 -> "줄 끝".also { s.editor.finishLine(); s.rerun() }
                    else -> "궤적".also { s.ensureRun() }
                }
                val where = "회 $round · 걸음 $step '$what' — 글 ${s.editor.text.replace("\n", "⏎")} · 쓰는 줄 ${s.editor.writingLine}"
                if (what == "0.3초" || what == "줄 끝" || what == "궤적") clean = true else if (s.editor.checkGen != gen) clean = false
                val blank = s.editor.checkedText.isBlank()
                if ((what == "줄 끝" || what == "궤적") && !blank) assertEquals(s.run.issues, s.shown(), "줄을 끝내고 새 결과 — $where")
                if (what == "0.3초" && s.editor.writingLine == null && !blank) assertEquals(s.run.issues, s.shown(), "새 결과 · 쓰는 줄 없음 — $where")
                val wl = s.editor.writingLine
                if (clean && wl != null && !blank) {
                    checkedWriting++
                    val last = s.editor.lastLine(wl)
                    val hid = s.run.issues.filter { it !in s.shown() }
                    assertTrue(s.run.issues.filter { it.line - 1 == wl }.all { it in hid }, "쓰는 줄의 것이 보인다 — $where")
                    assertEquals(emptyList(), hid.filterNot { it.line - 1 == wl || last && it.code in textEnd }, "쓰는 중(마지막 줄 $last) 그 밖의 것이 숨었다 — $where")
                }
            }
        }
        assertTrue(checkedWriting > 200, "쓰는 중 상태를 충분히 보지 못했다: $checkedWriting")
    }

    private companion object {
        const val HEAD = "O0001\nT0101\nG97 S1000 M03\nG99\n"
    }
}
