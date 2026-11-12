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
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 공구 설정의 '바 지름' 칸과 '몸통 닿음' 띠(2026-11-10 피드백 4 "X6. 으로 가공하면 소재를 X6. 이상으로 다 밀어버림 — 공구 지름 설정 가능하게", 설계 20절):
 * 안쪽 공구 카드에만 칸이 있고 빈칸 = 자동 값, 틀린 글(0 · 음수 · 숫자 아님 · 아주 큰 값)은 꺼지지 않고 안내 줄 + 자동으로 돈다,
 * 구멍보다 굵은 바는 노란 띠, 2D 의 공구 그림이 바 지름을 따른다. 깎인 소재의 숫자는 calc `ToolBarTest`.
 */
class SimToolBarTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-simbar", ".properties").apply { delete() })

    private companion object {
        /** 내경 X6.(calc ToolBarTest 와 같은 글) — 소재 Ø30 · 돌출 30 · 끝면 Z0 · 구멍 Ø5 */
        const val BORE6 = "O0006\nT0202\nG97S1500M03\nG99\nG0X6.Z2.\nG1Z-10.F.05\nX5.\nG0Z50.\nM30"
        const val BAD = "바 지름은 0 보다 크고 500 mm 이하로 넣으세요 — 지금은 자동 값으로 그립니다"
        const val LABEL = "바 지름 Ø"
    }

    /** X6. 내경 글 + 공구 2번 [tool]. 재생은 Z−10 으로 들어가는 중간([at])에 멈춰 구멍 둘레를 크게 */
    private fun scene(name: String, tool: ToolOffset, at: Double = 0.5) = TrajScene(name, {
        editor.replaceAll(BORE6)
        tools = mapOf(2 to tool)
        stockText = mapOf(StockField.DIA to "30", StockField.LENGTH to "30", StockField.FACE to "0", StockField.BORE to "5")
    }) { run ->
        lastT = run.totalSec * at
        viewer.focus(Pt(-4.0, 3.0), 1.5f)
    }

    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { walk(it.rootSemanticsNode) }

    /** 노드의 글(낱말 붙임 표 U+2060 은 뺀다) */
    private fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace("⁠", "") }

    private fun ImageComposeScene.texts(): List<String> = nodes().flatMap { it.texts() }

    private fun ImageComposeScene.tap(match: (List<String>) -> Boolean) =
        nodes().first { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && match(n.texts()) }.config[SemanticsActions.OnClick].action!!.invoke()

    /**
     * 칸: 빈칸이면 자동 5(가장 작은 X5.) → 키패드로 0 · 08 · − · −3 · . · 999 · 500 · 6.5 · 빈칸. 틀린 글은 공구표에 안 들어가고(빈칸 = 자동) 안내 줄이 뜬다.
     * 부호 · 소수점만 친 글(− · .)은 키패드가 칸에 넘기지 않아 빈칸 그대로다(안내 줄은 보지 않는다)
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun barFieldAutoBadAndTyped() {
        val state = AppState(store()).apply { open(Feature.SIMULATOR) }
        val sim = TrajScenes.sim(state.store, scene("bar", ToolOffset(0.2, 2)))
        val scene = ImageComposeScene(824, 2400, Density(2f)) { AppHost(state) { ToolSettingsSheet(sim, sim.run) {} } }
        try {
            var t = 0L
            fun frames() = repeat(6) {
                scene.render(t).close()
                t += 100_000_000L
            }
            frames()
            assertTrue(LABEL in scene.texts(), "${scene.texts()}")
            assertFalse(BAD in scene.texts())
            // 자동 값 5 가 보이는 칸을 눌러 키패드를 연다
            scene.tap { "5" in it && "mm" in it }
            frames()
            // 누를 키 · 공구표에 들어갈 값 · 안내 줄(null = 보지 않음)
            val steps = listOf(
                Triple(listOf("0"), null, true), Triple(listOf("8"), 8.0, false), Triple(listOf("C", "−"), null, null), Triple(listOf("3"), null, true),
                Triple(listOf("C", "."), null, null), Triple(listOf("C", "9", "9", "9"), null, true), Triple(listOf("C", "5", "0", "0"), 500.0, false),
                Triple(listOf("C", "6", ".", "5"), 6.5, false),
            )
            for ((n, step) in steps.withIndex()) {
                val (keys, value, bad) = step
                for (k in keys) {
                    scene.tap { it == listOf(k) }
                    frames()
                }
                assertEquals(value, sim.tools[2]?.barDia, "$keys")
                if (bad != null) assertEquals(bad, BAD in scene.texts(), "$keys 안내 줄: ${scene.texts()}")
                if (n == 0) {
                    // 틀린 값 0 의 모습(빨간 칸 + 안내 줄): 키패드를 닫아도 칸의 글 · 안내 줄은 남는다 — 찍고 다시 연다
                    scene.tap { it == listOf("확인") }
                    frames()
                    assertTrue(BAD in scene.texts())
                    assertEquals(null, sim.tools[2]?.barDia)
                    val img = scene.render(t)
                    File(Shots.dir, "sim-tools-bar-bad-light.png").apply { parentFile.mkdirs() }.writeBytes(requireNotNull(img.encodeToData()).bytes)
                    img.close()
                    scene.tap { "0" in it && "mm" in it }
                    frames()
                }
            }
            // 비우면 자동 — 안내 줄 없음
            scene.tap { it == listOf("C") }
            frames()
            assertEquals(null, sim.tools[2]?.barDia)
            assertFalse(BAD in scene.texts())
            assertEquals(ToolOffset(0.2, 2), sim.tools[2])
        } finally {
            scene.close()
        }
        // 칸에 올 수 있는 글마다: 빈칸은 자동이라 틀린 것이 아니다
        for (text in listOf("0", "0.", "-3", "-", ".", "abc", "1e9", "500.001", "NaN", "Infinity")) assertTrue(barBad(text), text)
        for (text in listOf(null, "", " ", "0.05", "8", "12.5", "500")) assertFalse(barBad(text), "$text")
    }

    /** 칸은 안쪽 공구(바이트 · 홈 팁 1 · 2, 나사 팁 1 · 2 · 6)에만 있다 */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun barFieldOnlyOnInnerTools() {
        val cases = listOf(
            ToolOffset(0.2, 2) to true, ToolOffset(0.2, 1) to true, ToolOffset(0.2, 3) to false, ToolOffset(0.2, 4) to false, ToolOffset(0.2, null) to false,
            ToolOffset(0.1, 2, ToolKind.GROOVE, width = 2.0) to true, ToolOffset(0.1, 3, ToolKind.GROOVE, width = 2.0) to false,
            ToolOffset(0.0, 6, ToolKind.THREAD) to true, ToolOffset(0.0, 0, ToolKind.THREAD) to false,
            ToolOffset(2.0, 2, ToolKind.ROUND) to false, ToolOffset(kind = ToolKind.DIE, tip = 2) to false,
        )
        for ((tool, shown) in cases) {
            val state = AppState(store()).apply { open(Feature.SIMULATOR) }
            val sim = TrajScenes.sim(state.store, scene("bar", tool))
            val scene = ImageComposeScene(412, 1400, Density(1f)) { AppHost(state) { ToolSettingsSheet(sim, sim.run) {} } }
            try {
                var t = 0L
                repeat(6) {
                    scene.render(t).close()
                    t += 100_000_000L
                }
                assertEquals(shown, LABEL in scene.texts(), "$tool")
            } finally {
                scene.close()
            }
        }
    }

    /** 닿음 띠의 글(소재를 다 깎을 때까지 기다린다 — 백그라운드). 없으면 null */
    private fun band(tool: ToolOffset): String? = bandScene(tool).first

    /** [band] 와 그때 화면의 글 전부 */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun bandScene(tool: ToolOffset): Pair<String?, List<String>> {
        val state = AppState(store()).apply { open(Feature.SIMULATOR) }
        val s = scene("bar", tool)
        val sim = TrajScenes.sim(state.store, s)
        val ui = TrajScenes.ui(sim, s)
        val scene = ImageComposeScene(412, 915, Density(1f)) {
            AppHost(state) {
                SimulatorPage(sim, onTools = {}) {}
                TrajectoryWindow(sim, ui, onClose = {}, onEditor = {})
            }
        }
        try {
            var t = 0L
            var found: String? = null
            repeat(60) {
                scene.render(t).close()
                t += 100_000_000L
                Thread.sleep(25)
                found = scene.texts().firstOrNull { "닿습니다" in it || "지납니다" in it || "깎습니다" in it } ?: found
            }
            return found to scene.texts()
        } finally {
            scene.close()
        }
    }

    /** 구멍(깎은 Ø6)보다 굵은 바 8 은 노란 띠로 알리고, 자동 · 맞는 바 4 는 띠가 없다 */
    @Test
    fun bodyContactBand() {
        val text = band(ToolOffset(0.2, 2, barDia = 8.0))
        assertTrue(text != null && text.startsWith("6줄: 공구 몸통(바)이 소재에 닿습니다 (X6.000 Z-0.5"), "$text")
        assertTrue(text!!.endsWith(") 외 2곳 — 바 지름을 확인하세요"), text)
        assertEquals(null, band(ToolOffset(0.2, 2)))
        assertEquals(null, band(ToolOffset(0.2, 2, barDia = 4.0)))
    }

    /** 몸통 닿음 띠의 단추는 고칠 곳인 [공구 설정] 이다(검증 V4 — 예전엔 [편집기로] 였다) */
    @Test
    fun bodyContactBandOpensTools() {
        fun count(texts: List<String>, s: String) = texts.count { it == s }
        val (text, hit) = bandScene(ToolOffset(0.2, 2, barDia = 8.0))
        val (none, plain) = bandScene(ToolOffset(0.2, 2, barDia = 4.0))
        assertTrue(text != null && none == null, "$text / $none")
        assertEquals(count(plain, "공구 설정") + 1, count(hit, "공구 설정"), "띠에 [공구 설정]")
        assertEquals(count(plain, "편집기로"), count(hit, "편집기로"), "띠에 [편집기로] 없음")
    }

    /**
     * 2D 의 공구 그림이 바 지름을 따른다: 바 1 과 4 는 깎인 소재 · 띠가 같아(둘 다 닿지 않음) 그림이 다르면 공구 몸통이 다른 것이고,
     * 자동(5)과 직접 넣은 5 는 그림이 같다
     */
    @Test
    fun drawnBodyFollowsBar() {
        fun png(name: String, tool: ToolOffset): ByteArray {
            val s = scene(name, tool)
            return Shots.take(name, dark = false, setup = { it.open(Feature.SIMULATOR) }) { state ->
                val sim = remember { TrajScenes.sim(state.store, s) }
                val ui = remember { TrajScenes.ui(sim, s) }
                AppHost(state) {
                    SimulatorPage(sim, onTools = {}) {}
                    TrajectoryWindow(sim, ui, onClose = {}, onEditor = {})
                }
            }.readBytes()
        }
        val auto = png("sim-2d-bar-auto", ToolOffset(0.2, 2))
        val five = png("sim-2d-bar-5", ToolOffset(0.2, 2, barDia = 5.0))
        val one = png("sim-2d-bar-1", ToolOffset(0.2, 2, barDia = 1.0))
        val four = png("sim-2d-bar-4", ToolOffset(0.2, 2, barDia = 4.0))
        assertContentEquals(auto, five, "자동 5 와 직접 넣은 5 의 그림")
        assertFalse(one.contentEquals(four), "바 1 과 4 의 그림이 같다 — 공구 몸통이 바 지름을 따르지 않는다")
        assertFalse(four.contentEquals(five), "바 4 와 5 의 그림이 같다")
    }

    private fun shot(s: TrajScene, name: String, widthDp: Int = 412, heightDp: Int = 915, fontScale: Float = 1f, sheet: TrajSheet? = null) =
        Shots.both(name, heightDp = heightDp, widthDp = widthDp, fontScale = fontScale, setup = { it.open(Feature.SIMULATOR) }) { state ->
            val sim = remember { TrajScenes.sim(state.store, s) }
            val ui = remember { TrajScenes.ui(sim, s).apply { this.sheet = sheet } }
            AppHost(state) {
                SimulatorPage(sim, onTools = {}) {}
                TrajectoryWindow(sim, ui, onClose = {}, onEditor = {})
            }
        }

    /**
     * 스크린샷: 공구 설정 카드의 바 지름 칸(자동 5 · 좁은 폰 큰 글꼴 · 보링바 홀더 C08K 를 골라 8), 2D(자동 — Ø6 구멍과 가는 몸통 · 바 8 — 노란 띠와 ▲ · 끝까지)
     */
    @Test
    fun shots() {
        val auto = scene("sim-2d-bar", ToolOffset(0.2, 2))
        shot(auto, "sim-tools-bar", heightDp = 1100, sheet = TrajSheet.TOOLS)
        shot(auto, "sim-tools-bar-fs130-w360", widthDp = 360, heightDp = 1300, fontScale = 1.3f, sheet = TrajSheet.TOOLS)
        val held = TrajScene("sim-tools-bar-holder", {
            auto.prep(this)
            pickHolder(2, COMPANY_HOLDERS.first { it.code == "C08K-SCLCR06" })
        }, auto.ui)
        shot(held, "sim-tools-bar-holder", heightDp = 1100, sheet = TrajSheet.TOOLS)
        shot(auto, "sim-2d-bar")
        shot(scene("sim-2d-bar-hit", ToolOffset(0.2, 2, barDia = 8.0)), "sim-2d-bar-hit")
        shot(scene("sim-2d-bar-hit-end", ToolOffset(0.2, 2, barDia = 8.0), at = 1.0), "sim-2d-bar-hit-end")
    }
}
