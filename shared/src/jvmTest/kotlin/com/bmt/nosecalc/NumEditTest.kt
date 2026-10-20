package com.bmt.nosecalc

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.ui.NumEdit
import com.bmt.nosecalc.ui.NumberPad
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumEditTest {
    /** 글 고치기만(연 뒤 덮어쓰기 없이) */
    private fun edit(start: String, keys: String) = keys.fold(start) { t, k -> NumEdit.press(t, k) }

    @Test
    fun digitsAndPoint() {
        assertEquals("12.5", edit("", "12.5"))
        assertEquals("0.4", edit("", ".4"))
        assertEquals("5", edit("0", "5"))
        assertEquals("0.05", edit("", "0.05"))
        assertEquals("1.25", edit("", "1.2.5"))
    }

    @Test
    fun signToggles() {
        assertEquals("-25", edit("", "-25"))
        assertEquals("-25", edit("25", "-"))
        assertEquals("25", edit("-25", "-"))
        assertEquals("-0.5", edit("", "-.5"))
        assertEquals("-3", edit("-0", "3"))
    }

    @Test
    fun deleteAndClear() {
        assertEquals("12", edit("123", "<"))
        assertEquals("", edit("123", "C"))
        assertEquals("", edit("", "<"))
    }

    @Test
    fun digitLimit() {
        assertEquals("123456789012", edit("", "1234567890123"))
    }

    /** 키패드를 연 뒤: 처음 숫자 · . · − 는 원래 값을 덮어쓰고, ⌫ 는 원래 값을 고친다. 그 뒤 − 는 부호 바꾸기 */
    @Test
    fun firstKeyOverwrites() {
        assertEquals("-12", NumEdit.type("-5", "-12")) // 예전엔 − 가 부호만 바꿔 512 가 됐다
        assertEquals("0.5", NumEdit.type("25", ".5"))
        assertEquals("7", NumEdit.type("-5", "7"))
        assertEquals("-12", NumEdit.type("", "12-"))
        assertEquals("-35", NumEdit.type("40", "35-"))
        assertEquals("", NumEdit.type("3", "<"))
        assertEquals("-2", NumEdit.type("-25", "<"))
        assertEquals("", NumEdit.type("-5", "C"))
        assertEquals("-5", NumEdit.type("-5", ""))
    }

    @Test
    fun finishTidiesText() {
        assertEquals("5", NumEdit.finish("5."))
        assertEquals("", NumEdit.finish("-"))
        assertEquals("", NumEdit.finish(""))
        assertEquals("0", NumEdit.finish("-0"))
        assertEquals("0", NumEdit.finish("-0."))
        assertEquals("-25.0", NumEdit.finish("-25.0"))
        assertEquals(-25.0, NumEdit.finish("-25.0").toDouble())
    }

    /** 누르는 동안 칸에 넣는 값: 다듬은 글. "-" 뿐이면 넣지 않는다(칸은 앞 값) */
    @Test
    fun liveValue() {
        assertNull(NumEdit.live("-"))
        assertEquals("5", NumEdit.live("5."))
        assertEquals("0", NumEdit.live("-0."))
        assertEquals("", NumEdit.live(""))
        assertEquals("-12", NumEdit.live("-12"))
    }

    /** 누를 때마다 바로 칸에 들어가고, 닫으면(Enter) 그대로 남는다 */
    @Test
    fun padAppliesLive() = Pad("-5").use { pad ->
        pad.press(Key.Minus)
        assertEquals("-5", pad.value) // "-" 만으론 바꾸지 않는다
        pad.press(Key.One)
        assertEquals("-1", pad.value)
        pad.press(Key.Two)
        assertEquals("-12", pad.value)
        pad.press(Key.Enter)
        assertFalse(pad.open.value)
        assertEquals("-12", pad.value)
    }

    /** 아무것도 안 누르고 닫으면 값은 그대로(바꾸지도 않는다), 닫을 때 "-" 뿐이면 연 때 값으로 */
    @Test
    fun padRestoresOnClose() {
        Pad("-5").use { pad ->
            pad.press(Key.Escape)
            assertFalse(pad.open.value)
            assertEquals(0, pad.changes)
        }
        Pad("-5").use { pad ->
            pad.press(Key.Seven)
            pad.press(Key.Backspace)
            assertEquals("", pad.value)
            pad.press(Key.Minus)
            assertEquals("", pad.value)
            pad.press(Key.Escape)
            assertFalse(pad.open.value)
            assertEquals("-5", pad.value)
        }
        Pad("40").use { pad ->
            pad.press(Key.Five)
            pad.press(Key.Period)
            assertEquals("5", pad.value) // "5." 은 다듬어 넣는다
            pad.press(Key.Delete)
            assertEquals("", pad.value) // C = 빈칸
            pad.press(Key.Enter)
            assertEquals("", pad.value)
            assertTrue(pad.changes > 0)
        }
    }

    /** 키패드만 띄우고 PC 키로 누른다. value = 칸 값(키패드가 바로 바꾼다) */
    private class Pad(initial: String) : AutoCloseable {
        private val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        var value = initial
        var changes = 0
        val open = mutableStateOf(true)
        private var t = 0L
        private val scene = ImageComposeScene(400, 800, Density(1f)) {
            AppHost(state) {
                if (open.value) NumberPad("Z2", "mm", value, onChange = { value = it; changes++ }, onClose = { open.value = false })
            }
        }

        init {
            frame()
        }

        private fun frame() {
            scene.render(t).close()
            t += 100_000_000L
        }

        @OptIn(InternalComposeUiApi::class)
        fun press(k: Key) {
            state.handleKey(KeyEvent(k, KeyEventType.KeyDown))
            frame()
        }

        override fun close() = scene.close()
    }
}
