package com.bmt.nosecalc

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.screens.TaperScreen
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
        a.setDark(false)
        val b = AppState(KeyValueStore(file))
        assertEquals(false, b.darkSaved)
        assertFalse(b.isDark(systemDark = true))
    }

    @Test
    fun backPopsScreensThenStops() {
        val s = AppState(store())
        assertFalse(s.canGoBack)
        assertFalse(s.back())
        s.open(Feature.ROUND)
        assertEquals(Feature.ROUND, s.current)
        assertTrue(s.back())
        assertEquals(Feature.TAPER, s.current)
        s.open(Feature.THREAD)
        s.home()
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
}
