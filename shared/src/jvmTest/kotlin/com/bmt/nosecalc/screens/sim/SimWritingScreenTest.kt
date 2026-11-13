package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.Shots
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.screens.SimulatorScreen
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 쓰는 줄의 오류 숨김(설계 20 — 2026-11-10 피드백 5)을 진짜 편집기 화면으로: 키패드 키 · 단추를 의미 노드로 누르고
 * 화면 글(문제 띠 · 줄 아래 글)을 본다. 0.3초 뒤 다시 도는 효과는 실제 시간이라 프레임을 돌리며 기다린다.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SimWritingScreenTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-writing", ".properties").apply { delete() })

    /** 편집기 화면 한 장면. draft 가 있으면 앱의 `SimulatorScreen()` 그대로(상태는 화면이 그 초안으로 만든다 — [sim] 은 쓰지 않고 보이는 글만 본다) */
    private inner class Stage(val sim: SimState, draft: String? = null) {
        val app = AppState(
            store().also { kv ->
                draft?.let {
                    kv.put(ProgramStore.DRAFT, it)
                    kv.put(ProgramStore.DRAFT_CURSOR, it.length.toString())
                }
            },
        ).also { it.open(Feature.SIMULATOR) }
        private val ui = UiQueue()
        private val d = Shots.DENSITY
        private val scene = ImageComposeScene((412 * d).roundToInt(), (915 * d).roundToInt(), Density(d), coroutineContext = ui) {
            AppHost(app) { if (draft != null) SimulatorScreen() else SimulatorPage(sim, onTools = { sim.ensureRun() }) { sim.ensureRun() } }
        }
        private var clock = 0L

        fun frame() {
            clock += 16_000_000L
            ui.drain()
            scene.render(clock).close()
        }

        /** 실제 시간 ms 만큼 프레임을 돌린다 */
        fun idle(ms: Long) {
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                Thread.sleep(20)
                frame()
            }
        }

        /** 조건이 참이 될 때까지(길어야 10초 — 같은 PC 에서 다른 빌드가 같이 돈다) */
        fun await(what: String, cond: () -> Boolean) {
            val until = System.currentTimeMillis() + 10_000
            while (!cond()) {
                assertTrue(System.currentTimeMillis() < until, "기다려도 안 됨: $what — 화면 글 ${texts()}")
                Thread.sleep(20)
                frame()
            }
        }

        private fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }

        private fun SemanticsNode.words(): List<String> =
            (config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
                .map { it.replace("⁠", "") }

        /** 화면에 보이는 글 전부(낱말 이음은 뺀다) */
        fun texts(): List<String> = nodes().flatMap { it.words() }

        fun shows(part: String) = texts().any { part in it }

        /** 그 글(또는 읽어 주는 글)을 가진 누를 수 있는 것을 누른다 */
        fun tap(label: String) {
            val node = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && n.words().any { it == label } }
            requireNotNull(node) { "누를 것이 없다: $label — 화면 글 ${texts()}" }.config[SemanticsActions.OnClick].action?.invoke()
            frame()
        }

        fun close() = scene.close()
    }

    private fun stage(sim: SimState = SimState(store()), draft: String? = null, block: Stage.() -> Unit) {
        val s = Stage(sim, draft)
        try {
            repeat(5) { s.frame() }
            s.block()
        } finally {
            s.close()
        }
    }

    /** 피드백 원문 그대로: "X" 만 쳐도 오류가 뜨면 안 된다 — 줄을 끝내야(⏎) 뜬다 */
    @Test
    fun xAloneShowsNothingUntilEnter() = stage {
        tap("X")
        idle(900)
        assertFalse(shows("오류"), "쓰는 중인데 오류 글이 떴다: ${texts()}")
        assertFalse(shows("숫자가 없습니다"), "쓰는 중인데 오류 글이 떴다: ${texts()}")
        tap("새 줄")
        await("문제 띠 '오류 1'") { shows("오류 1") }
        assertTrue(shows("1줄: X 뒤에 숫자가 없습니다"), "${texts()}")
    }

    /** 오류 보기 글 끝에 빈 줄을 하나 더한 것(커서는 그 빈 줄) — 5줄 확인 · 6줄 · 7줄 오류 */
    private fun errorsSim() = SimState(store()).apply {
        val text = SimShotsTest.ERRORS + "\n"
        editor.replaceAll(text, text.length)
    }

    /** 다른 줄의 문제는 쓰는 동안에도 그대로이고, 다시 돌지 않는다(뒤 줄 결과가 깜빡이지 않는다). 커서를 다른 줄로 옮기면 그 줄을 읽는다 */
    @Test
    fun otherIssuesStayAndNothingRerunsWhileWriting() = stage(errorsSim()) {
        await("첫 실행") { sim.runKey != null && shows("오류 2 · 확인 1") }
        val run = sim.run
        tap("X")
        idle(900)
        assertTrue(shows("오류 2 · 확인 1"), "${texts()}")
        assertFalse(shows("숫자가 없습니다"), "${texts()}")
        assertSame(run, sim.run, "쓰는 동안 다시 돌았다")
        sim.editor.moveTo(0)
        frame()
        await("문제 띠 '오류 3 · 확인 1'") { shows("오류 3 · 확인 1") }
        assertEquals(9, sim.run.issues.first { it.code == IssueCode.NO_NUMBER }.line)
    }

    /** 고친 줄을 떠난 직후 새 결과가 오기 전에도 그 줄의 옛 오류가 화면에 되살아나지 않는다 */
    @Test
    fun oldIssueDoesNotFlashBackAfterLeaving() = stage(errorsSim()) {
        await("첫 실행") { sim.runKey != null && shows("오류 2 · 확인 1") }
        // 6줄 `G01 X40. X41. F0.2` 의 ` X41.` 을 지운다
        sim.editor.moveTo(sim.editor.lineStart(5) + 13)
        frame()
        assertTrue(shows("X 를 한 블록에 두 번 썼습니다"), "${texts()}")
        tap("지우기")
        assertFalse(shows("두 번 썼습니다"), "고치기 시작했는데 그 줄 오류가 남았다: ${texts()}")
        assertTrue(shows("오류 1 · 확인 1"), "${texts()}")
        repeat(4) { tap("지우기") }
        assertEquals("G01 X40. F0.2", sim.editor.lineText(5))
        sim.editor.vertical(-1)
        repeat(3) {
            frame()
            assertFalse(shows("두 번 썼습니다"), "떠난 직후 옛 오류가 되살아났다: ${texts()}")
        }
        await("새 결과") { sim.run.stop?.line == 7 }
        frame()
        assertFalse(shows("두 번 썼습니다"), "${texts()}")
        assertTrue(shows("오류 1 · 확인 1"), "${texts()}")
        assertFalse(sim.editor.hides(5))
    }

    /** 쓰는 줄을 끝내는 단추들: ▶ 궤적 · 저장 · 키패드 닫기(✕ · 뒤로) · 문제 띠 · 목록 · ⋯ · 찾기. ▶ 궤적은 숨은 오류가 든 글 전체로 바로 돈다 */
    @Test
    fun buttonsEndTheLine() = stage(errorsSim()) {
        await("첫 실행") { sim.runKey != null && shows("오류 2 · 확인 1") }
        val x = CncRows.flatten().first { it.main == "X" }
        val back = CncRows.flatten().first { it.main == "⌫" }
        fun reset() {
            sim.sheet = null
            sim.ask = null
            sim.findOpen = false
            sim.keypadOpen = true
        }
        fun write() {
            reset()
            sim.press(x)
            frame()
            assertEquals(8, sim.editor.writingLine)
        }
        write()
        tap("궤적 확인")
        assertNull(sim.editor.writingLine)
        assertTrue(sim.run.issues.any { it.code == IssueCode.NO_NUMBER && it.line == 9 }, "숨은 오류가 든 글이 조용히 돌았다: ${sim.run.issues}")
        assertTrue(shows("오류 3 · 확인 1"), "${texts()}")
        sim.press(back)
        for (button in listOf("저장", "키패드 닫기", "오류 2 · 확인 1", "목록", "더 보기", "찾기")) {
            write()
            tap(button)
            assertNull(sim.editor.writingLine, button)
            assertEquals(sim.editor.text, sim.editor.checkedText, button)
            await("$button 뒤 문제 띠") { shows("오류 3 · 확인 1") }
            reset()
            sim.press(back)
            sim.editor.finishLine()
            frame()
            await("$button 뒤 지움") { shows("오류 2 · 확인 1") }
        }
        // 뒤로 키로 키패드를 닫아도
        write()
        assertTrue(app.back())
        assertFalse(sim.keypadOpen)
        assertNull(sim.editor.writingLine)
        // 키패드를 닫은 화면의 큰 [궤적 확인]
        sim.editor.finishLine()
        sim.press(x)
        assertEquals(8, sim.editor.writingLine)
        frame()
        tap("궤적 확인")
        assertNull(sim.editor.writingLine)
    }

    /**
     * 사용자 답(2026-11-13) "그 줄을 다 입력할 때까지 에러 표기하지 않음"(설계 21.6) — 앱의 화면 그대로: 글 끝 줄에 `M30` 을 한 글자씩 치는 동안
     * 'M30 / M02 없이 글이 끝납니다' 띠가 없고 ⏎ 뒤에도 없다. `M3` 까지 치고 ⏎ 하면 새 결과(0.3초) 뒤 다시 보인다
     */
    @Test
    fun noEndWaitsForTheLastLineOnTheRealScreen() {
        val text = SimWritingTest.END_CASES[0].text
        val msg = "M30 / M02 없이 글이 끝납니다"
        stage(draft = text) {
            await("첫 실행 — 안내 띠") { shows("6줄: $msg") }
            for (k in listOf("M", "3", "0")) {
                tap(k)
                idle(400)
                assertFalse(shows(msg), "'$k' 뒤 — 쓰는 중인데 글 끝 안내가 남았다: ${texts()}")
            }
            tap("새 줄")
            repeat(3) {
                frame()
                assertFalse(shows(msg), "⏎ 직후 옛 결과의 안내가 되살아났다: ${texts()}")
            }
            idle(900)
            assertFalse(shows(msg), "M30 으로 끝났는데 안내가 떴다: ${texts()}")
        }
        stage(draft = text) {
            await("첫 실행 — 안내 띠") { shows("6줄: $msg") }
            tap("M")
            tap("3")
            idle(400)
            assertFalse(shows(msg), "${texts()}")
            tap("새 줄")
            repeat(3) {
                frame()
                assertFalse(shows(msg), "⏎ 직후(새 결과 전) — ${texts()}")
            }
            await("새 결과 뒤 7줄 안내") { shows("7줄: $msg") }
        }
    }

    /**
     * 글 끝 문제마다(설계 21.6): 목록 시트를 띄운 채(PC 에서 시트가 떠 있어도 키는 편집기로 간다) 끝낼 글을 치는 동안 문제 띠 · 목록(멈춘 줄 머리도) ·
     * 줄 표(편집기가 받는 목록)에 그 문제가 없고, ⏎ 와 새 결과 뒤에도 없다. 끝내도 남는 글이면 새 결과 뒤 보인다
     */
    @Test
    fun endOfTextIssuesWaitOnTheScreen() {
        for (c in SimWritingTest.END_CASES) {
            val stops = c.code.level == IssueLevel.STOP
            fun opened() = SimState(store(), c.tools).apply { editor.replaceAll(c.text, c.text.length) }
            fun Stage.typeIn(word: String) = word.forEach { ch -> sim.press(CncRows.flatten().first { it.main == (if (ch == ' ') "SP" else ch.toString()) }) }
            fun Stage.marked() = shownIssues(sim.editor, sim.run).any { it.code == c.code }
            // 목록 시트 제목('문제 n') — 띠 · 줄 아래 글에도 같은 글이 있어서, 시트가 떠 있는지 따로 본다
            fun Stage.sheetOpen() = texts().any { it.startsWith("문제 ") }
            fun Stage.open(): String {
                await("첫 실행(${c.code})") { sim.runKey != null && sim.run.issues.any { it.code == c.code } }
                sim.sheet = SimSheet.ISSUES
                await("목록 시트(${c.code})") { sheetOpen() }
                val msg = simIssueText(sim.run.issues.first { it.code == c.code })
                await("목록의 ${c.code}") { shows(msg) }
                // 멈춤 오류면 목록 머리('실행은 n줄에서 멈춥니다')가 먼저 보여야 쓰는 중에 사라지는 것을 볼 수 있다
                if (stops) assertTrue(shows("에서 멈춥니다"), "치기 전 목록 머리(${c.code}): ${texts()}")
                return msg
            }
            stage(opened()) {
                val msg = open()
                for (ch in c.done) {
                    typeIn(ch.toString())
                    idle(350)
                    assertTrue(sheetOpen(), "목록 시트가 닫혔다(${c.code})")
                    assertFalse(shows(msg), "'${c.done}' 를 쓰는 중(${c.code}): ${texts()}")
                    if (stops) assertFalse(shows("에서 멈춥니다"), "'${c.done}' 를 쓰는 중 — 목록 머리(${c.code}): ${texts()}")
                    assertFalse(marked(), "줄 표(${c.code})")
                }
                sim.press(CncRows.flatten().first { it.main == "⏎" })
                frame()
                assertFalse(shows(msg), "⏎ 직후(${c.code}): ${texts()}")
                await("새 결과(${c.code})") { (sim.runKey as? Triple<*, *, *>)?.first == sim.editor.text }
                frame()
                assertFalse(shows(msg), "'${c.done}' 로 끝났는데 떴다(${c.code}): ${texts()}")
                assertFalse(marked(), "줄 표(${c.code})")
            }
            stage(opened()) {
                val msg = open()
                typeIn(c.wrong)
                idle(350)
                assertFalse(shows(msg), "'${c.wrong}' 를 쓰는 중(${c.code}): ${texts()}")
                sim.press(CncRows.flatten().first { it.main == "⏎" })
                frame()
                assertFalse(shows(msg), "⏎ 직후(새 결과 전 — ${c.code}): ${texts()}")
                await("'${c.wrong}' 뒤 다시 보임(${c.code})") { shows(msg) }
                if (stops) assertTrue(shows("에서 멈춥니다"), "'${c.wrong}' 뒤 목록 머리(${c.code}): ${texts()}")
                assertTrue(marked(), "줄 표(${c.code})")
            }
        }
    }
}
