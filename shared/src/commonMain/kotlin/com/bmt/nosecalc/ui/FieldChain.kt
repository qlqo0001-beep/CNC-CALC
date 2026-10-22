package com.bmt.nosecalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 숫자칸 묶음: 키패드를 닫지 않고 "다음" 으로 다음 칸에 간다(값은 누를 때마다 이미 들어가 있다 · 저절로 넘어가지 않는다).
 * [FieldChainHost] 안의 [NumberField] 가 스스로 들어오고, 순서는 화면 자리(위 → 아래, 같은 줄은 왼 → 오른)다.
 * active = 키패드가 열린 칸 id(null = 닫힘). sheetOpen 이면 화면 끝에 440dp 빈자리를 두어 아래 칸도 시트 위로 올라오게 한다.
 */
@Stable
class FieldChain internal constructor(initialActive: String?) {
    var active: String? by mutableStateOf(initialActive)
        private set
    val sheetOpen: Boolean get() = active != null

    /** 칸 id → 키패드에 넘길 것. 칸이 다시 그려질 때마다 새로 넣는다(값 · 미리보기가 키패드에 바로 따라온다) */
    internal val fields = mutableStateMapOf<String, ChainField>()

    /** 칸 순서. 자리를 옮겨 순서가 달라질 때만 바꾼다 — 스크롤할 때마다 키패드를 다시 그리지 않게 */
    internal var order by mutableStateOf(emptyList<String>())
        private set
    private val places = mutableMapOf<String, Pair<Int, Float>>()

    /** 열린 시트 높이(px, 아래 시스템 막대 빼고) — 고치는 칸을 이만큼 위로 올린다 */
    internal var sheetHeight by mutableIntStateOf(0)

    fun open(id: String) {
        active = id
    }

    fun close() {
        active = null
    }

    /** 칸 자리: row = 8dp 단위로 반올림한 y(같은 줄 칸은 같다), x = 왼쪽 끝 */
    internal fun place(id: String, row: Int, x: Float) {
        if (places.put(id, row to x) != (row to x)) sort()
    }

    /** 칸이 화면에서 빠지면 묶음에서도 뺀다. 열려 있던 칸이면 키패드를 닫는다 */
    internal fun remove(id: String) {
        fields.remove(id)
        places.remove(id)
        sort()
        if (active == id) active = null
    }

    /** id 의 다음(d = 1) · 앞(d = -1) 칸. 없으면 null */
    internal fun neighbour(id: String, d: Int): String? = order.indexOf(id).takeIf { it >= 0 }?.let { order.getOrNull(it + d) }

    private fun sort() {
        val o = places.entries.sortedWith(compareBy({ it.value.first }, { it.value.second })).map { it.key }
        if (o != order) order = o
    }
}

/** 키패드에 넘길 칸 하나([NumberField] 의 인자 그대로) */
internal class ChainField(
    val label: String,
    val unit: String,
    val value: String,
    val onValueChange: (String) -> Unit,
    val hint: String?,
    val autoValue: String?,
    val blankable: Boolean,
    val figure: (@Composable (Modifier) -> Unit)?,
    val preview: String?,
)

internal val LocalFieldChain = staticCompositionLocalOf<FieldChain?> { null }

/** initialActive = 처음부터 열어 둘 칸 id(스크린샷 시험용) */
@Composable
fun rememberFieldChain(initialActive: String? = null): FieldChain = remember { FieldChain(initialActive) }

/** 안의 [NumberField] 를 묶고, 열린 칸 하나의 키패드를 그린다. 다음 · 앞 칸으로 가도 시트는 그대로 두고 내용만 바꾼다 */
@Composable
fun FieldChainHost(chain: FieldChain, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFieldChain provides chain, content = content)
    val f = chain.active?.let { chain.fields[it] } ?: return
    NumberPad(
        f.label, f.unit, f.value, f.onValueChange, onClose = chain::close,
        hint = f.hint, autoValue = f.autoValue, blankable = f.blankable, figure = f.figure, preview = f.preview, chain = chain,
    )
}
