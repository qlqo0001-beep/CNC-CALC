package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.remember
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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 공구 값이 없어 멈추면 편집기 문제 띠에 '공구 설정' 단추가 있다(사용자 10-28 "공구 설정 어디서 해?" — 공구표가 궤적 창에만 있었다).
 * 누르면 onTools(화면은 궤적 창을 공구 설정 시트로 연다 — SimulatorScreen).
 */
class SimToolShortcutTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-simtool", ".properties").apply { delete() })

    private companion object {
        /** G42 를 쓰는데 공구표에 보정 12 가 없다 → 6줄에서 TOOL_DATA_MISSING */
        const val NO_TOOL = "O0001\nT1212\nG97 S1000 M03\nG99\nG00 X44. Z2.\nG42 G01 X40. Z0. F0.2\nG01 Z-20.\nG40 G00 X44.\nM30"
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun issueStripOpensTools() {
        val state = AppState(store()).apply { open(Feature.SIMULATOR) }
        val sim = SimState(state.store).apply {
            editor.replaceAll(NO_TOOL)
            run = runOf(editor.text, tools)
        }
        assertEquals(IssueCode.TOOL_DATA_MISSING, sim.run.stop?.code)
        var opened = 0
        val scene = ImageComposeScene(412, 915, Density(1f)) {
            AppHost(state) { SimulatorPage(sim, onTools = { opened++ }) {} }
        }
        try {
            var t = 0L
            repeat(8) {
                scene.render(t).close()
                t += 100_000_000L
            }
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            val node = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.single { n ->
                n.config.getOrNull(SemanticsActions.OnClick) != null &&
                    n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "공구 설정" }
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, opened)
        } finally {
            scene.close()
        }
    }

    /**
     * 팁 번호가 빈칸(—)인 공구 카드에서 '고르기' → 팁 시트가 열리고(예전엔 -1 을 넘겨 그림의 tipVector 가 던져 앱이 꺼졌다 — 사용자 10-28
     * "팁번호 설정시 팅기는 문제"), 번호를 고르면 공구표에 들어간다. 1~8 · 0 모두.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun tipPickFromBlank() {
        for (pick in listOf("1", "2", "3", "4", "5", "6", "7", "8", "0 · 노즈 중심")) {
            val state = AppState(store()).apply { open(Feature.SIMULATOR) }
            val sim = SimState(state.store).apply {
                editor.replaceAll(NO_TOOL)
                run = runOf(editor.text, tools)
            }
            val scene = ImageComposeScene(412, 915, Density(1f)) {
                AppHost(state) { ToolSettingsSheet(sim, sim.run) {} }
            }
            try {
                var t = 0L
                fun frames() = repeat(6) {
                    scene.render(t).close()
                    t += 100_000_000L
                }
                fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
                // 글을 모두 가진 누르는 노드(홀더 칸도 '고르기' 로 끝나 팁 칸은 '—' 와 함께 찾는다)
                fun tap(vararg text: String) = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.first { n ->
                    n.config.getOrNull(SemanticsActions.OnClick) != null &&
                        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }.containsAll(text.toList())
                }.config[SemanticsActions.OnClick].action!!.invoke()
                frames()
                tap("—", "고르기")
                frames()
                tap(pick)
                frames()
                assertEquals(pick.substringBefore(' ').toInt(), sim.tools[12]?.tip, pick)
            } finally {
                scene.close()
            }
        }
    }

    /** 스크린샷 sim-editor-tool-missing(412dp) · -w360(360dp · 글꼴 130%) — 문제 띠에 '공구 설정' 이 '목록' 과 함께 들어가는지 */
    @Test
    fun shots() {
        for ((name, w, fs) in listOf(Triple("sim-editor-tool-missing", 412, 1f), Triple("sim-editor-tool-missing-w360", 360, 1.3f))) {
            Shots.both(name, heightDp = 915, widthDp = w, fontScale = fs, setup = { it.open(Feature.SIMULATOR) }) { state ->
                val sim = remember {
                    SimState(state.store).apply {
                        editor.replaceAll(NO_TOOL)
                        run = runOf(editor.text, tools)
                    }
                }
                AppHost(state) { SimulatorPage(sim, onTools = {}) {} }
            }
        }
    }
}
