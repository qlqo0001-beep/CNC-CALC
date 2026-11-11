@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.awtKey
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.numPadKey
import com.bmt.nosecalc.screens.SimulatorScreen
import com.bmt.nosecalc.ui.NumEdit
import com.bmt.nosecalc.ui.NumberPad
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.awt.event.KeyEvent as AwtKey

/**
 * 독립 검증(2026-11-10 피드백 5 · 6) — 진짜 화면으로. 구현자의 시험과 따로 쓴 검사다.
 * real = 앱이 쓰는 `SimulatorScreen()` 그대로(편집기 + 궤적 창, 상태는 화면이 만든다 — 보이는 글만 본다),
 * 아니면 같은 배선을 시험이 가진 [SimState] 로(상태를 들여다보려고). PC 키는 창이 하는 차례 그대로: 앱(`handleKey`)이 안 받으면 초점 있는 곳으로.
 */
class Verify1110EditorScreenTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-v1110s", ".properties").apply { delete() })

    private inner class Stage(draft: String, cursor: Int, private val real: Boolean) {
        private val kv = store().also {
            it.put(ProgramStore.DRAFT, draft)
            it.put(ProgramStore.DRAFT_CURSOR, cursor.toString())
        }
        val app = AppState(kv).also { it.open(Feature.SIMULATOR) }

        /** real 이 아닐 때 화면이 쓰는 상태 */
        val sim = SimState(kv)
        private val ui = UiQueue()
        private val scene = ImageComposeScene(412, 915, Density(1f), coroutineContext = ui) {
            AppHost(app) {
                if (real) SimulatorScreen() else {
                    val traj = remember { TrajUi() }
                    SimulatorPage(
                        sim,
                        onTools = {
                            sim.ensureRun()
                            traj.sheet = TrajSheet.TOOLS
                            sim.trajOpen = true
                        },
                        onTrajectory = {
                            sim.ensureRun()
                            sim.trajOpen = true
                        },
                    )
                    if (sim.trajOpen) {
                        TrajectoryWindow(
                            sim, traj,
                            onClose = {
                                sim.trajOpen = false
                                traj.sheet = null
                            },
                            onEditor = { line ->
                                sim.trajOpen = false
                                traj.sheet = null
                                sim.goToLine(line)
                            },
                        )
                    }
                }
            }
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

        fun await(what: String, cond: () -> Boolean) {
            val until = System.currentTimeMillis() + 15_000
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

        fun texts(): List<String> = nodes().flatMap { it.words() }

        fun shows(part: String) = texts().any { part in it }

        /** 그 글(읽어 주는 글)을 가진 누를 수 있는 것을 누른다 */
        fun tap(label: String) {
            val node = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && n.words().any { it == label } }
            requireNotNull(node) { "누를 것이 없다: $label — 화면 글 ${texts()}" }.config[SemanticsActions.OnClick].action?.invoke()
            frame()
        }

        /** 그 글이 적힌 곳의 가운데를 마우스(또는 손가락)로 누른다 */
        fun click(text: String, type: PointerType = PointerType.Mouse) {
            val node = requireNotNull(nodes().firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) == null && n.words().any { it == text } }) { "그 글이 없다: $text — 화면 글 ${texts()}" }
            val at = node.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, at, type = type)
            frame()
            scene.sendPointerEvent(PointerEventType.Release, at, type = type)
            frame()
            frame()
        }

        /** PC 창의 키: 앱이 먼저(Esc · 숫자 키패드), 안 받으면 초점 있는 곳(편집기) */
        fun pc(e: KeyEvent) {
            if (!app.handleKey(e)) scene.sendKeyEvent(e)
            frame()
        }

        fun close() = scene.close()
    }

    private fun stage(draft: String, cursor: Int = draft.length, real: Boolean = false, block: Stage.() -> Unit) {
        val s = Stage(draft, cursor, real)
        try {
            repeat(5) { s.frame() }
            s.block()
        } finally {
            s.close()
        }
    }

    // ---------------------------------------------------------------- 5. 앱의 화면 그대로

    /** 피드백 원문: 빈 편집기에서 X 만 침 → 아무 오류 글도 없다 → ⏎ → 뜬다 */
    @Test
    fun realScreenXAloneThenEnter() = stage("", real = true) {
        tap("X")
        idle(1000)
        assertFalse(shows("오류"), "쓰는 중인데 오류 글: ${texts()}")
        assertFalse(shows("숫자가 없습니다"), "쓰는 중인데 오류 글: ${texts()}")
        tap("새 줄")
        await("문제 띠") { shows("오류 1") }
        assertTrue(shows("숫자가 없습니다"), "${texts()}")
    }

    /** 끝 줄(줄바꿈 없이 끝)에 쓰다 만 글이 있는 채 ▶ 궤적: 궤적 창이 그 줄에서 멈췄다고 알린다 — 조용히 돌지 않는다. 편집기로 돌아오면 오류가 보인다 */
    @Test
    fun realScreenUnfinishedLastLineThenTrajectory() = stage("O0001\nG0X40.Z2.\n", real = true) {
        idle(700)
        tap("G")
        tap("1")
        tap("Z")
        idle(800)
        assertFalse(shows("숫자가 없습니다"), "쓰는 중인데 오류 글: ${texts()}")
        assertFalse(shows("오류 1"), "${texts()}")
        tap("궤적 확인")
        await("궤적 창의 멈춤 띠") { shows("3줄에서 멈춤") }
        assertTrue(shows("숫자가 없습니다"), "${texts()}")
        tap("편집기로")
        await("편집기의 문제 띠") { shows("오류 1") }
        assertTrue(shows("숫자가 없습니다"), "${texts()}")
    }

    /**
     * 블록 없는 한 줄(`O1234`)을 ⌫ 로 다 지운 빈 편집기: 문제 띠가 없어야 한다.
     * [2026-11-11 검증에서 찾아 고침] '안내 1 — 프로그램: M30 / M02 없이 끝납니다' 띠가 빈 편집기에 남았다(설계 20.5)
     */
    @Test
    fun realScreenClearedEditorShowsNoStrip() = stage("O1234", real = true) {
        await("첫 실행의 안내") { shows("안내 1") }
        repeat(5) { tap("지우기") }
        assertTrue(shows("키패드로 G코드를 넣으세요"), "편집기가 비지 않았다: ${texts()}")
        idle(700)
        assertFalse(shows("안내 1"), "빈 편집기에 문제 띠가 남았다: ${texts()}")
    }

    // ---------------------------------------------------------------- 5. 같은 배선 + 상태 들여다보기

    /** PC: 마우스로 줄을 고르고 키보드로 침(초점 길 그대로) → 쓰는 동안 그대로 → 다른 줄을 마우스 · 손가락으로 누르면 그 줄을 읽는다. 숫자 키패드 키도 편집기에 들어간다 */
    @Test
    fun pcKeysMouseAndTouch() = stage(SimShotsTest.ERRORS + "\n") {
        await("첫 실행") { sim.runKey != null && shows("오류 2 · 확인 1") }
        click("M30")
        assertEquals(7, sim.editor.cursorLine, "마우스로 8줄을 눌렀다")
        pc(awtKey(AwtKey.VK_DOWN))
        assertEquals(8, sim.editor.cursorLine, "↓ 키가 편집기에 닿지 않았다(초점)")
        val run = sim.run
        pc(awtKey(AwtKey.VK_X, 'x'))
        assertEquals("X", sim.editor.lineText(8))
        assertEquals(8, sim.editor.writingLine)
        pc(numPadKey(AwtKey.VK_SUBTRACT, '-'))
        pc(numPadKey(AwtKey.VK_NUMPAD4, '4'))
        pc(numPadKey(AwtKey.VK_DECIMAL, '.'))
        assertEquals("X-4.", sim.editor.lineText(8), "숫자 키패드 − 4 .")
        repeat(3) { pc(awtKey(AwtKey.VK_BACK_SPACE, '\b')) }
        assertEquals("X", sim.editor.lineText(8))
        idle(800)
        assertTrue(shows("오류 2 · 확인 1"), "${texts()}")
        assertFalse(shows("숫자가 없습니다"), "${texts()}")
        assertSame(run, sim.run, "쓰는 동안 다시 돌았다")
        click("T0101")
        assertEquals(1, sim.editor.cursorLine)
        assertNull(sim.editor.writingLine, "다른 줄을 마우스로 눌렀는데 쓰는 줄이 남았다")
        await("문제 띠 '오류 3 · 확인 1'") { shows("오류 3 · 확인 1") }
        // 그 줄로 돌아가 고쳐 쓰는 중 → 손가락으로 다른 줄
        click("X")
        assertEquals(8, sim.editor.cursorLine)
        pc(awtKey(AwtKey.VK_END))
        pc(awtKey(AwtKey.VK_4, '4'))
        assertEquals("X4", sim.editor.lineText(8))
        assertEquals(8, sim.editor.writingLine)
        frame()
        assertTrue(shows("오류 2 · 확인 1"), "고치기 시작하면 그 줄 오류는 숨는다: ${texts()}")
        click("G99", PointerType.Touch)
        assertEquals(3, sim.editor.cursorLine, "손가락으로 4줄을 눌렀다")
        assertNull(sim.editor.writingLine)
        await("새 결과") { sim.run.issues.none { it.code == IssueCode.NO_NUMBER } && !sim.editor.hides(8) }
        frame()
        assertEquals(shownIssues(sim.editor, sim.run), sim.runOf(sim.editor.text, sim.tools).issues)
    }

    /** PC Esc: 키패드를 닫으며 쓰던 줄을 읽는다 */
    @Test
    fun escClosesKeypadAndChecksTheLine() = stage("") {
        tap("X")
        assertEquals(0, sim.editor.writingLine)
        idle(500)
        assertFalse(shows("오류"), "${texts()}")
        pc(awtKey(AwtKey.VK_ESCAPE, '\u001b'))
        assertFalse(sim.keypadOpen)
        assertNull(sim.editor.writingLine)
        await("문제 띠") { shows("오류 1") }
    }

    /**
     * 쓰던 줄이 있는 채 ▶ 궤적: 궤적 창은 글 전체의 결과로 열리고, 그 뒤 편집기 화면의 0.3초 효과가 실행 결과를 다른 것으로 바꿔 치우지 않는다
     * (바뀌면 궤적 창의 재생 상태 · 소재가 연 직후 다시 만들어진다)
     */
    @Test
    fun trajectoryRunStaysAfterOpening() = stage("O0001\nT0101\nG97 S1000 M03\nG99\nG00 X40. Z2.\nG01 Z-10. F0.2\n") {
        await("첫 실행") { sim.runKey != null }
        idle(400)
        tap("M")
        tap("3")
        tap("0")
        assertEquals(6, sim.editor.writingLine)
        assertTrue(sim.run.issues.any { it.code == IssueCode.NO_END }, "쓰는 동안의 결과는 M30 을 치기 전 글의 것: ${sim.run.issues}")
        tap("궤적 확인")
        assertTrue(sim.trajOpen)
        assertNull(sim.editor.writingLine)
        val opened = sim.run
        assertTrue(opened.issues.none { it.code == IssueCode.NO_END }, "궤적 창이 쓰던 줄(M30)을 뺀 결과로 열렸다: ${opened.issues}")
        idle(1500)
        assertSame(opened, sim.run, "궤적 창을 연 뒤 실행 결과가 새 것으로 바뀌었다")
    }

    // ---------------------------------------------------------------- 6. 숫자 칸

    /** 숫자 칸 키패드: 창이 넘기는 차례 그대로(`AppState.handleKey`) 숫자 키패드의 숫자 · . · Enter. 소수점은 한 번만 */
    @Test
    fun numberPadTakesNumPadKeys() {
        val app = AppState(store())
        var value by mutableStateOf("12.5")
        var open by mutableStateOf(true)
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            AppHost(app) {
                if (open) NumberPad("X", "mm", value, onChange = { value = it }, onClose = { open = false })
            }
        }
        var t = 0L
        fun frame() {
            scene.render(t).close()
            t += 100_000_000L
        }
        fun press(e: KeyEvent) {
            assertTrue(app.handleKey(e), "창이 넘긴 키를 숫자 칸이 받지 않았다: ${e.key}")
            frame()
        }
        try {
            frame()
            press(numPadKey(AwtKey.VK_NUMPAD3, '3'))
            press(numPadKey(AwtKey.VK_DECIMAL, '.'))
            press(numPadKey(AwtKey.VK_NUMPAD7, '7'))
            press(numPadKey(AwtKey.VK_DECIMAL, '.'))
            press(numPadKey(AwtKey.VK_NUMPAD5, '5'))
            assertEquals("3.75", value)
            press(numPadKey(AwtKey.VK_ENTER, '\n'))
            assertFalse(open)
            assertEquals("3.75", value)
        } finally {
            scene.close()
        }
        // 안드로이드에 붙인 키보드는 이름 그대로의 키로 온다
        assertEquals('.', NumEdit.keyOf(KeyEvent(Key.NumPadDot, KeyEventType.KeyDown)))
        assertEquals('.', NumEdit.keyOf(KeyEvent(Key.NumPadComma, KeyEventType.KeyDown)))
        assertEquals('-', NumEdit.keyOf(KeyEvent(Key.NumPadSubtract, KeyEventType.KeyDown)))
        assertEquals(NumEdit.DONE, NumEdit.keyOf(KeyEvent(Key.NumPadEnter, KeyEventType.KeyDown)))
    }
}
