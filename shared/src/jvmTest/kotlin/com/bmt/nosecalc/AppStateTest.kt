package com.bmt.nosecalc

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.ui.BottomSheet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })

    @Test
    fun themeIsSavedAndReadBack() {
        val file = File.createTempFile("bmt-kv", ".properties").apply { delete() }
        val a = AppState(KeyValueStore(file))
        assertNull(a.darkSaved)
        assertTrue(a.isDark(systemDark = true))
        a.setTheme(false)
        val b = AppState(KeyValueStore(file))
        assertEquals(false, b.darkSaved)
        assertFalse(b.isDark(systemDark = true))
        // 메뉴의 '자동': 다시 시스템 설정을 따르고, 다음에 켜도 그대로
        b.setTheme(null)
        val c = AppState(KeyValueStore(file))
        assertNull(c.darkSaved)
        assertTrue(c.isDark(systemDark = true))
        assertFalse(c.isDark(systemDark = false))
    }

    @Test
    fun backPopsScreensThenStops() {
        val s = AppState(store())
        assertFalse(s.canGoBack)
        assertFalse(s.back())
        s.open(Feature.ROUND)
        assertEquals(Feature.ROUND, s.current)
        assertTrue(s.hasBackStep)
        assertTrue(s.back())
        assertEquals(Feature.TAPER, s.current)
        assertFalse(s.hasBackStep)
    }

    /** 이미 쌓인 화면을 다시 열면 그 위를 걷어 낸다 — 같은 화면이 두 번 쌓이지 않는다 */
    @Test
    fun openNeverDuplicates() {
        val s = AppState(store())
        s.open(Feature.ROUND)
        s.open(Feature.TRIANGLE)
        s.open(Feature.ROUND)
        assertEquals(listOf(Feature.TAPER, Feature.ROUND), s.stack)
        s.open(Feature.TRIANGLE)
        s.open(Feature.TAPER)
        assertEquals(listOf(Feature.TAPER), s.stack)
    }

    /** 뒤로는 위에 뜬 것부터: 시트 → 메뉴 → 화면 */
    @Test
    fun backClosesTopmostFirst() {
        val s = AppState(store())
        val sheetOpen = mutableStateOf(true)
        s.menuOpen = true
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            AppHost(s) {
                TaperScreen()
                if (sheetOpen.value) BottomSheet(onDismiss = { sheetOpen.value = false }) {}
            }
        }
        try {
            scene.render(0)
            scene.render(1_000_000_000)
            assertTrue(s.canGoBack)
            // 메뉴 · 시트는 제 닫기가 있어 헤더 ← 를 띄우지 않는다
            assertFalse(s.hasBackStep)
            assertTrue(s.back())
            assertFalse(sheetOpen.value)
            scene.render(2_000_000_000)
            assertTrue(s.back())
            assertFalse(s.menuOpen)
            scene.render(3_000_000_000)
            assertFalse(s.canGoBack)
            assertFalse(s.back())
        } finally {
            scene.close()
        }
    }

    /** 메뉴 카드: 준비 중은 토스트만(화면 그대로), 지금 화면은 메뉴만 닫고, 다른 계산은 그리로. 화면 테마 '자동' = 시스템 따라 */
    @Test
    fun menuCards() {
        val s = AppState(store())
        s.setTheme(true)
        s.open(Feature.ROUND)
        s.menuOpen = true
        val scene = ImageComposeScene(400, 1600, Density(1f)) { App(s) }
        var t = 0L
        fun frames() = repeat(5) {
            scene.render(t).close()
            t += 100_000_000L
        }
        try {
            frames()
            scene.tap("G코드 시뮬레이터")
            assertEquals("G코드 시뮬레이터: 준비 중인 기능입니다", s.toast?.text)
            assertEquals(listOf(Feature.TAPER, Feature.ROUND), s.stack)
            assertTrue(s.menuOpen)
            scene.tap("자동")
            assertNull(s.darkSaved)
            frames()
            scene.tap("라운드 R 계산")
            assertFalse(s.menuOpen)
            assertEquals(listOf(Feature.TAPER, Feature.ROUND), s.stack)
            s.menuOpen = true
            frames()
            scene.tap("직각삼각형 계산")
            assertEquals(listOf(Feature.TAPER, Feature.ROUND, Feature.TRIANGLE), s.stack)
            assertFalse(s.menuOpen)
        } finally {
            scene.close()
        }
    }

    /**
     * 보정 방식은 앱 전체 하나: 처음은 기계 보정. 테이퍼 결과의 '직접 보정' 을 누르면 라운드도 직접 보정이고
     * (NC 첫 줄 '(G40 DIRECT R0.4 T3)'), 다음에 켜도 그대로
     */
    @Test
    fun compModeIsSharedAndRemembered() {
        val file = File.createTempFile("bmt-kv", ".properties").apply { delete() }
        val s = AppState(KeyValueStore(file))
        assertEquals(CompMode.MACHINE, s.compMode)
        val round = mutableStateOf(false)
        val scene = ImageComposeScene(400, 900, Density(1f)) {
            AppHost(s) {
                if (round.value) RoundScreen(roundExample(1, open = true)) else TaperScreen(TaperState(type = 1, open = true).example())
            }
        }
        try {
            scene.render(0)
            assertFalse("(G40 DIRECT R0.4 T3)" in scene.texts())
            scene.tap("직접 보정")
            assertEquals(CompMode.DIRECT, s.compMode)
            scene.render(100_000_000)
            assertTrue("(G40 DIRECT R0.4 T3)" in scene.texts())
            round.value = true
            scene.render(200_000_000)
            assertTrue("(G40 DIRECT R0.4 T3)" in scene.texts())
        } finally {
            scene.close()
        }
        assertEquals(CompMode.DIRECT, AppState(KeyValueStore(file)).compMode)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.texts(): List<String> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        return semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    /** 글이 text 인 누를 수 있는 것 하나를 누른다 */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.tap(text: String) {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        val node = semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.single { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null &&
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }
        node.config[SemanticsActions.OnClick].action!!.invoke()
    }
}
