package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.saveable.SaverScope
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
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.screens.SimulatorScreen
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 편집기의 쓰는 줄(설계 21 — 피드백 5)과 공구 저장(설계 20.2 — 피드백 3 · 4)이 함께 도는 경우(2026-11-12 통합 고침 S1, 설계 22).
 * 두 갈래가 같은 화면 파일을 따로 고쳐 합쳤다 — 쓰는 중에 저장 · 열기 · 예제 · 새 글 · 묻는 창 · 공구표 바꿈 · 궤적 창 · Saver 를 섞는다.
 * 모델은 화면 없이, 화면은 앱의 `SimulatorScreen()` 그대로(상태를 들여다볼 것만 같은 배선의 쥔 상태로). 글 · 공구 값은 새로 쓴 것.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SimEditorToolsTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-edtools", ".properties").apply { delete() })

    private val toolsA = mapOf(1 to ToolOffset(0.4, 3, kappaDeg = 95.0), 2 to ToolOffset(0.2, 2, barDia = 6.0))
    private val toolsB = mapOf(1 to ToolOffset(0.8, 3))

    /** 6줄이 오류(X 뒤 숫자 없음) */
    private val textA = "O1001 (A)\nT0101\nG98\nG0X40.Z2.\nG1Z-20.F5.\nX\nM30\n"
    private val textB = "O1002 (B)\nT0101\nG98\nG0X30.Z2.\nG1Z-10.F5.\nG0X40.\nM30\n"

    /** 5줄 끝(G1Z-20.F5. 뒤) */
    private val line5End = textA.indexOf("F5.") + 3

    /** 화면의 0.3초 효과가 하는 일: 검사 글로 돌고 그 번호의 결과가 올랐다고 알린다 */
    private fun SimState.rerun() {
        val gen = editor.checkGen
        run = runOf(editor.checkedText, tools)
        runKey = Triple(editor.checkedText, tools, runSetup())
        editor.ran(gen)
    }

    /** O1002(공구 B) · O1001(공구 A · 소재 Ø50) 을 저장하고 O1001 을 연 저장소. 커서는 O1001 의 5줄 끝 */
    private fun twoPrograms(): KeyValueStore {
        val kv = store()
        SimState(kv).apply {
            tools = toolsB
            editor.replaceAll(textB)
            assertEquals("O1002 저장했습니다 — 공구 · 소재 설정도 함께", requestSave())
            tools = toolsA
            setStock(StockField.DIA, "50")
            editor.replaceAll(textA, line5End)
            assertEquals("O1001 저장했습니다 — 공구 · 소재 설정도 함께", requestSave())
        }
        return kv
    }

    private fun draftSetup(kv: KeyValueStore) = ProgramSetup.decode(kv.get(ProgramStore.DRAFT_SETUP)!!)!!

    // ---- 모델

    /** 쓰는 중에 공구표를 바꿔도 쓰는 줄은 그대로(검사 글 · 번호도) — 저장은 줄을 끝내고 쓰던 글과 바꾼 공구를 한 쌍으로 */
    @Test
    fun toolsWhileWritingThenSave() {
        val kv = twoPrograms()
        val s = SimState(kv).apply { rerun() }
        assertEquals(Origin.Saved(1001), s.origin)
        assertFalse(s.modified)
        s.editor.insert("0")
        assertEquals(4, s.editor.writingLine)
        val gen = s.editor.checkGen
        s.tools = s.tools + (1 to ToolOffset(0.8, 3, kappaDeg = 95.0))
        s.setStock(StockField.DIA, "60")
        assertEquals(4, s.editor.writingLine)
        assertEquals(gen, s.editor.checkGen)
        assertEquals(textA, s.editor.checkedText)
        assertTrue(s.textModified && s.setupModified)
        assertEquals(s.setupNow(), draftSetup(kv))
        // 화면의 [저장] = 줄 끝 → 저장
        s.editor.finishLine()
        assertEquals("O1001 저장했습니다 — 공구 · 소재 설정도 함께", s.requestSave())
        assertNull(s.editor.writingLine)
        assertEquals(s.editor.text, s.editor.checkedText)
        assertFalse(s.modified)
        assertEquals(textA.replace("F5.\n", "F5.0\n"), s.programs.load(1001))
        assertEquals(s.setupNow().encode(), s.programs.setup(1001))
        // 다른 것을 열었다 다시 열면 쓰던 글 · 바꾼 공구 · 소재 그대로
        assertEquals("O1002 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다", s.request(Pending.Load(1002)))
        assertEquals(toolsB, s.tools)
        assertEquals("O1001 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다", s.request(Pending.Load(1001)))
        assertEquals(0.8, s.tools[1]?.noseR)
        assertEquals("60", s.stockText[StockField.DIA])
        assertEquals(textA.replace("F5.\n", "F5.0\n"), s.editor.text)
    }

    /** 쓰는 중에 [목록] → 다른 프로그램: 묻는 창 '취소' 는 글 · 설정 그대로, '버리고 열기' 는 새 글 · 새 설정 — 쓰는 줄 · 가림 · 고침 표시가 남지 않는다 */
    @Test
    fun askWhileWriting() {
        val kv = twoPrograms()
        val s = SimState(kv).apply { rerun() }
        s.editor.insert("X")
        s.tools = s.tools + (2 to ToolOffset(0.2, 2, barDia = 8.0))
        val mine = s.setupNow()
        // 화면의 [목록] 이 줄을 끝낸다
        s.editor.finishLine()
        assertTrue(s.editor.hides(4)) // 막 끝낸 줄 — 새 결과가 올 때까지
        assertNull(s.request(Pending.Load(1002)))
        assertTrue(s.ask is SimAsk.Unsaved)
        s.ask = null // 취소
        assertEquals(textA.replace("F5.\n", "F5.X\n"), s.editor.text)
        assertEquals(mine, s.setupNow())
        assertEquals(mine, draftSetup(kv))
        assertTrue(s.textModified && s.setupModified)
        s.rerun()
        assertTrue(s.run.issues.any { it.code == IssueCode.NO_NUMBER && it.line == 5 }, "${s.run.issues}")
        // 버리고 열기
        assertNull(s.request(Pending.Load(1002)))
        assertEquals("O1002 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다", s.perform((s.ask as SimAsk.Unsaved).then))
        assertEquals(textB, s.editor.text)
        assertEquals(textB, s.editor.checkedText)
        assertNull(s.editor.writingLine)
        assertFalse(s.editor.hides(4))
        assertEquals(toolsB, s.tools)
        assertEquals(emptyMap(), s.stockText)
        assertFalse(s.modified)
        assertEquals(ProgramSetup(toolsB, emptyMap(), emptyMap()).canonical(), draftSetup(kv).canonical())
        assertEquals(draftSetup(kv).canonical().encode(), kv.get(ProgramStore.DRAFT_BASE))
        s.rerun()
        assertNull(s.run.stop, "${s.run.stop}")
        assertTrue(shownIssues(s.editor, s.run).none { it.code == IssueCode.NO_NUMBER })
        assertEquals(1002, s.run.program.units.first().number)
    }

    /** 줄을 끝내지 않은 채 바로 바꾸는 길(예제 · 새 글)도 쓰는 줄을 남기지 않는다. 예제는 제 공구를 더하고, 새 글은 공구표 · 소재를 그대로 둔다 */
    @Test
    fun exampleAndNewWhileWriting() {
        val kv = twoPrograms()
        val s = SimState(kv).apply { rerun() }
        s.editor.insert("X")
        val ex = exampleList.indexOfFirst { it.tools.isNotEmpty() }
        assertNull(s.perform(Pending.Example(ex)))
        assertNull(s.editor.writingLine)
        assertEquals(s.editor.text, s.editor.checkedText)
        assertEquals(exampleList[ex].text, s.editor.text)
        assertEquals(toolsA + exampleList[ex].tools, s.tools)
        assertEquals(Origin.Example(ex), s.origin)
        assertFalse(s.modified)
        assertEquals("", kv.get(ProgramStore.DRAFT_BASE))
        s.editor.insert("X")
        assertNull(s.perform(Pending.New))
        assertNull(s.editor.writingLine)
        assertEquals("", s.editor.checkedText)
        assertEquals(toolsA + exampleList[ex].tools, s.tools)
        assertEquals("50", s.stockText[StockField.DIA])
        assertEquals(s.setupNow(), draftSetup(kv))
    }

    /** 쓰는 중에 프로세스가 죽었다 살면(Saver): 새 상태라 쓰는 줄 없이 글 전체를 검사하고, 공구 · 소재 · '● 고침' 은 저장소에서 그대로 */
    @Test
    fun restoreWhileWriting() {
        val kv = twoPrograms()
        val s = SimState(kv).apply { rerun() }
        s.editor.insert("X")
        s.tools = s.tools + (1 to ToolOffset(0.8, 3))
        val saver = SimState.saver(kv)
        val saved = with(saver) { SaverScope { true }.save(s) }
        val r = saver.restore(assertNotNull(saved))!!
        assertEquals(s.editor.text, r.editor.text)
        assertNull(r.editor.writingLine)
        assertEquals(r.editor.text, r.editor.checkedText)
        assertEquals(s.tools, r.tools)
        assertEquals("50", r.stockText[StockField.DIA])
        assertEquals(Origin.Saved(1001), r.origin)
        assertTrue(r.textModified && r.setupModified && r.modified)
        // 처음 실행이 글 전체 · 저장소의 공구표로
        assertTrue(r.run.issues.any { it.code == IssueCode.NO_NUMBER && it.line == 5 }, "${r.run.issues}")
        assertTrue(shownIssues(r.editor, r.run).any { it.code == IssueCode.NO_NUMBER && it.line == 5 })
    }

    // ---- 화면

    private inner class Stage(val kv: KeyValueStore, private val held: SimState?) {
        val app = AppState(kv).also { it.open(Feature.SIMULATOR) }
        private val ui = UiQueue()
        private val scene = ImageComposeScene(412, 915, Density(1f), coroutineContext = ui) {
            AppHost(app) {
                val sim = held
                if (sim == null) SimulatorScreen() else {
                    // SimulatorScreen 과 같은 배선(상태만 시험이 쥔다)
                    val traj = remember { TrajUi() }
                    SimulatorPage(
                        sim,
                        onTrajectory = {
                            sim.ensureRun()
                            sim.trajOpen = true
                        },
                        onTools = {
                            sim.ensureRun()
                            traj.sheet = TrajSheet.TOOLS
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

        /** 실제 시간 ms 만큼 프레임을 돌린다(0.3초 효과 · 백그라운드 실행은 실제 시간) */
        fun idle(ms: Long) {
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                Thread.sleep(20)
                frame()
            }
        }

        /** 길어야 15초(같은 PC 에서 다른 빌드가 같이 돈다) */
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

        /** 그 글(읽어 주는 글)을 가진 누를 수 있는 것을 누른다 — 시트 · 궤적 창 같은 위층은 한 프레임 늦게 뜨므로 나올 때까지 기다린다 */
        fun tap(label: String) {
            fun find() = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && n.words().any { it == label } }
            await("누를 것: $label") { find() != null }
            find()!!.config[SemanticsActions.OnClick].action?.invoke()
            frame()
        }

        fun close() = scene.close()
    }

    private fun stage(kv: KeyValueStore, held: SimState? = null, block: Stage.() -> Unit) {
        val s = Stage(kv, held)
        try {
            repeat(5) { s.frame() }
            s.block()
        } finally {
            s.close()
        }
    }

    /**
     * 쓰는 중에 ▶ 궤적: 줄을 끝내고 바로 돈 결과를 궤적 창이 쓴다. 줄을 끝내며 오른 검사 번호로 0.3초 뒤 편집기 효과가 같은 글을 한 번 더 돌리지만
     * 결과가 같은 값이라(`SimRun` 은 data class · 상태는 구조 같음) 궤적 창의 재생 · 소재가 새로 만들어지지 않는다 — 막 누른 재생이 이어진다
     */
    @Test
    fun trajectoryFromWritingKeepsPlaying() {
        val kv = store().also {
            it.put(ProgramStore.DRAFT, textB)
            it.put(ProgramStore.DRAFT_CURSOR, (textB.indexOf("F5.") + 3).toString())
        }
        stage(kv) {
            await("첫 실행") { shows("O1002") }
            tap("0")
            tap("궤적 확인")
            tap("재생")
            await("재생 중") { shows("멈춤") }
            idle(1500)
            assertTrue(shows("멈춤"), "궤적 창을 연 뒤 실행이 다시 들어와 재생이 멈췄다: ${texts()}")
        }
    }

    /** 같은 것을 쥔 상태로: ▶ 뒤 실행이 쓰던 줄이 든 글 전체 · 지금 공구표로 돈 것이고, 0.3초 뒤 다시 돌아도 궤적 창이 보는 실행 객체는 그대로 */
    @Test
    fun trajectoryRunIsNotReplaced() {
        val kv = store().also {
            it.put(ProgramStore.DRAFT, textB)
            it.put(ProgramStore.DRAFT_CURSOR, (textB.indexOf("F5.") + 3).toString())
        }
        val sim = SimState(kv)
        stage(kv, sim) {
            await("첫 실행") { sim.runKey != null }
            tap("0")
            assertEquals(4, sim.editor.writingLine)
            tap("궤적 확인")
            assertTrue(sim.trajOpen)
            assertNull(sim.editor.writingLine)
            val run = sim.run
            assertEquals(Triple(sim.editor.text, sim.tools, sim.runSetup()), sim.runKey)
            idle(1200)
            assertSame(run, sim.run, "같은 글 · 공구표인데 실행이 다시 들어왔다")
        }
    }

    /**
     * 쓰는 중에 공구표가 바뀌면 검사 글(쓰는 줄은 고치기 전 그대로) · 새 공구표로 다시 돌고 쓰는 줄은 여전히 숨는다.
     * ▶ 궤적은 줄을 끝내고 새 공구표로 돌아, 숨었던 오류가 궤적 창에 멈춤으로 뜬다(조용히 돌지 않는다)
     */
    @Test
    fun toolsChangeWhileWritingReruns() {
        val text = "O1003 (G42)\nT0101\nG97S1000M03\nG99\nG0X60.Z50.\nG42\nG0X40.Z20.\nZ1.\nG1Z-20.F.1\nX44.F.3\nG40\nG0Z50.\nM30\n"
        val kv = store().also {
            it.put(ProgramStore.DRAFT, text)
            it.put(ProgramStore.DRAFT_CURSOR, (text.indexOf("F.1") + 3).toString())
        }
        val sim = SimState(kv)
        stage(kv, sim) {
            await("공구 없음 멈춤") { sim.runKey != null && sim.run.stop?.code == IssueCode.TOOL_DATA_MISSING }
            tap("X")
            assertEquals(8, sim.editor.writingLine)
            sim.tools = mapOf(1 to ToolOffset(0.4, 3))
            frame()
            await("새 공구표로 다시 돎") { sim.runKey == Triple(sim.editor.checkedText, sim.tools, sim.runSetup()) }
            assertNull(sim.run.stop, "${sim.run.stop}")
            assertEquals(8, sim.editor.writingLine)
            assertFalse(shows("X 뒤에 숫자가 없습니다"), "쓰는 줄의 오류가 떴다: ${texts()}")
            // 바 지름 · 소재도 같은 길
            sim.tools = sim.tools + (2 to ToolOffset(0.2, 2, barDia = 6.0))
            sim.setStock(StockField.DIA, "70")
            frame()
            await("소재를 바꾼 뒤 다시 돎") { sim.runKey == Triple(sim.editor.checkedText, sim.tools, sim.runSetup()) }
            assertEquals(8, sim.editor.writingLine)
            tap("궤적 확인")
            assertNull(sim.editor.writingLine)
            assertEquals(Triple(sim.editor.text, sim.tools, sim.runSetup()), sim.runKey)
            assertEquals(IssueCode.NO_NUMBER, sim.run.stop?.code)
            await("궤적 창의 멈춤 띠") { shows("9줄에서 멈춤") }
        }
    }

    /**
     * 진짜 화면: 저장한 O1001 에서 쓰는 중에 [목록] → O1002 — 묻는 창 '취소' 는 그대로, '버리고 열기' 는 토스트와 함께 O1002 · 그 공구표로 바뀌고
     * 낡은 문제 띠 · O 번호 칩 · '● 고침' 이 남지 않는다
     */
    @Test
    fun openOtherProgramWhileWriting() {
        val kv = twoPrograms()
        stage(kv) {
            await("O1001 의 오류 띠") { shows("오류 1") && shows("O1001") }
            assertTrue(shows("✓ 저장됨"), "${texts()}")
            tap("0")
            assertTrue(shows("● 고침"), "${texts()}")
            tap("목록")
            tap("O1002")
            await("묻는 창") { shows("저장하지 않은 글") }
            tap("취소")
            await("묻는 창이 닫힘") { !shows("저장하지 않은 글") }
            assertTrue(shows("● 고침") && shows("O1001"), "${texts()}")
            assertEquals(toolsA, draftSetup(kv).tools)
            tap("목록")
            tap("O1002")
            tap("버리고 열기")
            assertEquals("O1002 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다", app.toast?.text)
            await("O1002 칩 · 띠 없음") { shows("O1002") && !shows("O1001") && !shows("오류") }
            assertTrue(shows("✓ 저장됨") && !shows("● 고침"), "${texts()}")
            assertEquals(toolsB, draftSetup(kv).tools)
            assertEquals(textB, kv.get(ProgramStore.DRAFT))
        }
    }
}
