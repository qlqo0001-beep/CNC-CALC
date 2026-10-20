package com.bmt.nosecalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.ui.FieldChain
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.NumEdit
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.NumberPad
import com.bmt.nosecalc.ui.rememberFieldChain
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

    /** PC 키: Enter · Tab = 다음(마지막 칸 · 묶음 밖은 닫기), Shift+Tab = 앞 칸 */
    @OptIn(InternalComposeUiApi::class)
    @Test
    fun nextKeys() {
        assertEquals(NumEdit.NEXT, NumEdit.keyOf(KeyEvent(Key.Enter, KeyEventType.KeyDown)))
        assertEquals(NumEdit.NEXT, NumEdit.keyOf(KeyEvent(Key.NumPadEnter, KeyEventType.KeyDown)))
        assertEquals(NumEdit.NEXT, NumEdit.keyOf(KeyEvent(Key.Tab, KeyEventType.KeyDown)))
        assertEquals(NumEdit.PREV, NumEdit.keyOf(KeyEvent(Key.Tab, KeyEventType.KeyDown, isShiftPressed = true)))
    }

    /**
     * 현장 보고 "누르면 숫자가 지워지지 않음"(예전 빌드: 처음 − 가 부호만 바꿔 Z-5.0 에서 − 1 2 → 5.012 처럼 이어 붙었다).
     * 화면 키로: 연 뒤 · 다음 뒤 · ⌫/C 뒤 모두 처음 키가 새 값이고, 다음은 시트를 닫지 않는다. 마지막 칸 닫기 · 바깥 누름은 닫기만
     */
    @Test
    fun chainScreenKeys() = Chain().use { ch ->
        ch.tapField("z1")
        assertEquals("z1", ch.chain.active)
        ch.tapKey(0, 3) // −
        ch.tapKey(0, 2) // 1
        ch.tapKey(1, 2) // 2
        assertEquals("-12", ch.values["z1"])
        ch.tapKey(3, 3) // 다음
        assertEquals("ang", ch.chain.active)
        ch.tapKey(0, 1) // 4
        assertEquals("4", ch.values["ang"]) // "304" 가 아니다
        ch.tapKey(3, 1) // ⌫
        ch.tapKey(1, 1) // 5
        assertEquals("5", ch.values["ang"])
        ch.tapKey(3, 0) // C
        ch.tapKey(2, 1) // 6
        assertEquals("6", ch.values["ang"])
        ch.tapKey(3, 3) // 마지막 칸 → 닫기
        assertNull(ch.chain.active)
        ch.tapField("x1")
        ch.tapAt(200f, 20f) // 바깥(덮개) — 안 누르고 닫으면 그대로
        assertNull(ch.chain.active)
        assertEquals(mapOf("x1" to "40", "z1" to "-12", "ang" to "6"), ch.values.toMap())
    }

    /** PC 키로: Tab · Enter = 다음, Shift+Tab = 앞 칸, 돌아온 칸도 처음 키가 덮어쓴다. "-" 뿐이면 넘어갈 때 연 때 값으로 */
    @Test
    fun chainPcKeys() = Chain().use { ch ->
        ch.tapField("x1")
        ch.press(Key.Tab, shift = true) // 첫 칸에서 앞 칸은 없다
        assertEquals("x1", ch.chain.active)
        ch.press(Key.Minus)
        ch.press(Key.Tab)
        assertEquals("z1", ch.chain.active)
        assertEquals("40", ch.values["x1"])
        ch.press(Key.Seven)
        assertEquals("7", ch.values["z1"])
        ch.press(Key.Tab, shift = true)
        assertEquals("x1", ch.chain.active)
        ch.press(Key.Three)
        assertEquals("3", ch.values["x1"]) // "403" 이 아니다
        ch.press(Key.Enter)
        ch.press(Key.Enter)
        assertEquals("ang", ch.chain.active)
        ch.press(Key.Enter)
        assertNull(ch.chain.active)
        ch.tapField("z1")
        ch.press(Key.Nine)
        ch.press(Key.Escape)
        assertNull(ch.chain.active)
        assertEquals(mapOf("x1" to "3", "z1" to "9", "ang" to "30"), ch.values.toMap())
    }

    /** 다음 칸으로 막 넘어가 다시 그리기 전에 온 키는 앞 칸을 바꾸지 않는다 */
    @Test
    fun chainKeyRightAfterNext() = Chain().use { ch ->
        ch.tapField("x1")
        ch.press(Key.Enter, frame = false)
        ch.press(Key.Five)
        assertEquals("40", ch.values["x1"])
        assertEquals("z1", ch.chain.active)
    }

    /** 연 칸은 시트 위로 올라온다(아래에 결과 카드가 있는 화면처럼) */
    @Test
    fun chainBringsFieldAboveSheet() = Chain(top = 600).use { ch ->
        assertTrue(ch.bounds.getValue("z1").bottom > 400f) // 처음엔 시트가 덮을 자리
        ch.tapField("z1")
        assertTrue(ch.chain.sheetHeight > 0)
        assertTrue(ch.bounds.getValue("z1").bottom <= 800f - ch.chain.sheetHeight, "z1 ${ch.bounds["z1"]} 시트 ${ch.chain.sheetHeight}")
        ch.press(Key.Enter)
        assertEquals("ang", ch.chain.active)
        assertTrue(ch.bounds.getValue("ang").bottom <= 800f - ch.chain.sheetHeight, "ang ${ch.bounds["ang"]} 시트 ${ch.chain.sheetHeight}")
    }

    /**
     * 묶음 칸 셋(X1 · Z1 / 각도)을 400×800(밀도 1) 창에 그린다. top = 칸 위 빈자리. 칸 아래는 결과 카드 자리 200dp,
     * 키패드가 열리면 440dp 를 더 둔다. 화면 키 자리: 시트 아래 여백 16 · 키 58 · 간격 8 · 왼쪽 3열 270 + 오른쪽 열 90
     */
    private class Chain(top: Int = 0) : AutoCloseable {
        private val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        val values = mutableStateMapOf("x1" to "40", "z1" to "-5.0", "ang" to "30")
        val bounds = mutableMapOf<String, Rect>()
        lateinit var chain: FieldChain
        private var t = 0L
        private val scene = ImageComposeScene(400, 800, Density(1f)) {
            AppHost(state) {
                chain = rememberFieldChain()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(top.dp))
                    FieldChainHost(chain) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Field("x1", Modifier.weight(1f))
                                Field("z1", Modifier.weight(1f))
                            }
                            Field("ang", Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(200.dp))
                    if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
                }
            }
        }

        @Composable
        private fun Field(id: String, modifier: Modifier) = NumberField(
            id, values.getValue(id), { values[id] = it }, modifier.onGloballyPositioned { bounds[id] = it.boundsInRoot() }, id = id,
        )

        init {
            frames()
        }

        private fun frames(n: Int = 8) = repeat(n) {
            scene.render(t).close()
            t += 50_000_000L
        }

        fun tapAt(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
            frames(1)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
            frames()
        }

        /** 칸(아래 52dp 칸 가운데)을 누른다 */
        fun tapField(id: String) = bounds.getValue(id).let { tapAt(it.center.x, it.bottom - 26f) }

        /** 키패드 키: col 0~2 = 숫자 열, 3 = 오른쪽 열(C · ⌫ · 닫기 · 다음), row 0~3 = 위에서부터 */
        fun tapKey(col: Int, row: Int) {
            val x = if (col < 3) 16f + col * (254f / 3 + 8f) + 127f / 3 else 16f + 270f + 8f + 45f
            tapAt(x, 800f - 16f - (3 - row) * 66f - 29f)
        }

        @OptIn(InternalComposeUiApi::class)
        fun press(k: Key, shift: Boolean = false, frame: Boolean = true) {
            state.handleKey(KeyEvent(k, KeyEventType.KeyDown, isShiftPressed = shift))
            if (frame) frames()
        }

        override fun close() = scene.close()
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
