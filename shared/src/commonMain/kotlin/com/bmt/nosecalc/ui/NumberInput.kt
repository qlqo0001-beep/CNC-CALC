package com.bmt.nosecalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import kotlin.math.roundToInt

/**
 * 숫자칸(위 라벨 줄 + 52dp 칸, 값 가운데 굵게). 누르면 [NumberPad] 가 뜬다 — 시스템 키보드는 쓰지 않는다.
 * value 는 쓴 그대로의 글("5.0", "-25", "" = 빈칸). 숫자는 value.toDoubleOrNull() 로 꺼낸다.
 * 키패드에서 누를 때마다 값이 바로 onValueChange 로 들어간다(결과·그림·NC 가 따라 바뀐다).
 * 값은 단위와 겹치지 않게 길면 줄인다([ValueAndUnit]). 칸 모양은 색 + 표 + 굵기로 가른다(색만으로 가르지 않는다):
 * - autoValue = 빈칸일 때 계산된 값: 청록 옅은 바탕·테두리 + valueComp 글 + 라벨 줄 "자동" 표(자리가 모자라도 남긴다, [FieldLabel]).
 *   "" 이면 풀지 못한 것 → 칸에 "—", 표는 "자동 · 계산 안 됨"
 * - error = 이 칸 값이 문제: 빨강 2dp 테두리 · 옅은 빨강 바탕 · 라벨 앞 멈춤 아이콘
 * - tag = 라벨 뒤 회색 표("Ø직경" · "반경")
 * - placeholder = 빈칸 자리표시("기본 0" 처럼, 흐리게)
 * hint · blankable · figure · preview 는 키패드로 넘긴다(안내 줄 · "비우기 · 자동 계산" 단추 · 작은 그림 · 결과 한 줄).
 * 키패드는 [FieldChainHost] 가 띄운다 — 칸은 묶음에 id 로 들어가("다음"), 열리면 시트 위로 올라온다.
 * 묶음 밖의 칸은 모양만 보인다(부품 견본).
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "",
    placeholder: String = "",
    autoValue: String? = null,
    error: Boolean = false,
    tag: String? = null,
    hint: String? = null,
    blankable: Boolean = false,
    id: String = label,
    figure: (@Composable (Modifier) -> Unit)? = null,
    preview: String? = null,
) {
    val c = AppTheme.colors
    val rc = c.fieldNormal
    val chain = LocalFieldChain.current
    val editing = chain?.active == id
    val bring = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(6.dp)
    val unsolved = value.isEmpty() && autoValue == ""
    val computed = value.isEmpty() && !autoValue.isNullOrEmpty()
    // 칸 글 · 색 · 굵기: 넣은 값 > 못 푼 자동 > 자동값 > 자리표시
    val (shown, color, weight) = when {
        value.isNotEmpty() -> Triple(value, rc.value, FontWeight.Bold)
        unsolved -> Triple("—", c.textHint, FontWeight.Medium)
        autoValue != null -> Triple(autoValue, c.valueComp, FontWeight.SemiBold)
        else -> Triple(placeholder, c.textHint, FontWeight.Medium)
    }
    Column(if (chain != null) modifier.bringIntoViewRequester(bring).onSizeChanged { size = it } else modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (error) Icon(AppIcons.Stop, null, Modifier.padding(end = 4.dp).size(16.dp), tint = c.error.label)
            FieldLabel(
                label, if (error) c.error.label else rc.label, tag,
                when {
                    unsolved -> listOf("자동 · 계산 안 됨", "계산 안 됨")
                    computed -> listOf("자동")
                    else -> emptyList()
                },
                if (unsolved) c.error.bg else c.teal.copy(alpha = 0.14f),
                if (unsolved) c.error.label else c.tealDeep,
            )
        }
        Spacer(Modifier.height(6.dp))
        ValueAndUnit(
            shown,
            TextStyle(color = color, fontFamily = AppTheme.sans, fontWeight = weight),
            unit,
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(
                    when {
                        error -> c.error.bg
                        computed -> c.teal.copy(alpha = if (c.dark) .10f else .06f)
                        else -> rc.bg
                    },
                )
                .border(
                    if (editing || error) 2.dp else 1.5.dp,
                    when {
                        editing -> c.lineFocus
                        error -> c.error.label
                        computed -> c.teal.copy(alpha = .45f)
                        else -> rc.border
                    },
                    shape,
                )
                .then(
                    if (chain == null) Modifier
                    else Modifier.onGloballyPositioned {
                        val p = it.positionInRoot()
                        chain.place(id, (p.y / with(density) { 8.dp.toPx() }).roundToInt(), p.x)
                    },
                )
                .clickable(enabled = chain != null) { chain?.open(id) }
                .padding(horizontal = 12.dp),
        )
    }
    if (chain != null) {
        SideEffect { chain.fields[id] = ChainField(label, unit, value, onValueChange, hint, autoValue, blankable, figure, preview) }
        DisposableEffect(chain, id) { onDispose { chain.remove(id) } }
        if (editing) {
            // 칸 아래로 시트 높이 + 8dp 를 더한 곳까지 보이게 — 칸이 시트 위로 올라온다
            val sheet = chain.sheetHeight
            LaunchedEffect(sheet) {
                if (sheet > 0) bring.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + sheet + with(density) { 8.dp.toPx() }))
            }
        }
    }
}

/**
 * 칸 안의 값 + 오른쪽 단위. 값은 칸 가운데에 두고, 길면 단위 앞까지만 왼쪽으로 민 뒤 글자를 줄인다([ValueFit]) —
 * 큰 글꼴 · 좁은 폰에서도 숫자가 단위와 겹치거나 잘리지 않게
 */
@Composable
private fun ValueAndUnit(text: String, style: TextStyle, unit: String, modifier: Modifier) {
    val c = AppTheme.colors
    Layout(
        content = {
            BasicText(text, style = style, overflow = TextOverflow.Ellipsis, maxLines = 1, autoSize = ValueFit)
            if (unit.isNotEmpty()) Text(unit, color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        },
        modifier,
    ) { ms, cs ->
        val u = ms.getOrNull(1)?.measure(Constraints())
        val room = (cs.maxWidth - (u?.let { it.width + 8.dp.roundToPx() } ?: 0)).coerceAtLeast(0)
        val v = ms[0].measure(Constraints(maxWidth = room))
        layout(cs.maxWidth, cs.maxHeight) {
            v.place(minOf((cs.maxWidth - v.width) / 2, room - v.width), (cs.maxHeight - v.height) / 2)
            u?.place(cs.maxWidth - u.width, (cs.maxHeight - u.height) / 2)
        }
    }
}

/**
 * 숫자 글자 크기: max 에서 0.5sp 씩 줄여 한 줄에 다 들어가는 크기(min 까지). 그래도 넘치면 "…" 로 줄인다 —
 * 잘린 숫자가 멀쩡한 값처럼 보이지 않게. (StepBased 는 PC 에서 "…" 를 넘침으로 보지 않아 줄이지 않는다)
 */
private data class FitDown(val max: Float, val min: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max
        while (size > min && performLayout(constraints, text, size.sp).multiParagraph.intrinsics.maxIntrinsicWidth > constraints.maxWidth) size -= 0.5f
        return size.sp
    }
}

/** 칸 값: 20sp → 11sp */
private val ValueFit = FitDown(20f, 11f)

/**
 * 라벨 줄: 라벨 + tag 표("Ø직경") + 상태 표(state = 긴 글부터: "자동" · "자동 · 계산 안 됨" → "계산 안 됨").
 * 라벨이 먼저 자리를 잡고 tag 표는 남은 자리에(어느 칸인지가 먼저). 상태 표는 넣은 값과 계산 값을 가르는 표라 색만 남지 않게
 * 자리가 모자라면 tag 표를 먼저 빼고, 그래도 모자라면 짧은 글로, 그래도 모자라면 라벨을 "…" 로 줄여서라도 남긴다
 * (라벨은 앞 [LABEL_KEEP] 만큼은 남긴다). 표는 다 들어가지 않으면 그리지 않는다(반쯤 잘린 표 대신)
 */
@Composable
private fun FieldLabel(label: String, color: Color, tag: String?, state: List<String>, stateBg: Color, stateFg: Color) {
    val c = AppTheme.colors
    Layout({
        Text(label, style = AppType.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (tag != null) FieldChip(tag, c.subtle, c.textBody, c.lineField)
        state.forEach { FieldChip(it, stateBg, stateFg) }
    }) { ms, cs ->
        val w = cs.maxWidth
        val lw = ms[0].maxIntrinsicWidth(Constraints.Infinity)
        val t = if (tag != null) ms[1].measure(Constraints()) else null
        val ss = ms.drop(if (tag != null) 2 else 1).map { it.measure(Constraints()) }
        val s = ss.firstOrNull { lw + it.width <= w } ?: ss.lastOrNull()?.takeIf { minOf(lw, LABEL_KEEP.roundToPx()) + it.width <= w }
        val sw = s?.width ?: 0
        val tg = t?.takeIf { lw + sw + it.width <= w }
        val l = ms[0].measure(if (cs.hasBoundedWidth) Constraints(maxWidth = (w - sw - (tg?.width ?: 0)).coerceAtLeast(0)) else Constraints())
        val h = maxOf(l.height, tg?.height ?: 0, s?.height ?: 0)
        layout(l.width + (tg?.width ?: 0) + sw, h) {
            var x = 0
            listOfNotNull(l, tg, s).forEach {
                it.place(x, ((h - it.height) / 2f).roundToInt()) // Row 의 CenterVertically 와 같게
                x += it.width
            }
        }
    }
}

/** 상태 표가 라벨을 줄여도 라벨은 이만큼은 남긴다("끝 Z …" 처럼 어느 칸인지 알 만큼) */
private val LABEL_KEEP = 48.dp

/** 라벨 줄 작은 표(11.5sp 굵게, 모서리 4dp, 앞 6dp 띄움): "자동" · "Ø직경" */
@Composable
private fun FieldChip(text: String, bg: Color, fg: Color, border: Color = Color.Transparent) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text,
        Modifier.padding(start = 6.dp).clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = 5.dp),
        color = fg,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/**
 * 숫자 키패드(바텀시트). 열면 지금 값이 "통째 선택"된 채 보이고, 처음 누른 숫자 · . · − 가 그 값을 덮어쓴다(⌫ 는 원래 값을 고친다).
 * 확인 단추는 없다 — 누를 때마다 값이 바로 onChange 로 들어간다. "-" 만 남은 동안은 칸에 앞 값을 둔다.
 * 닫기(닫기 키 · 바깥 누름 · 뒤로 · Esc · 손잡이 끌어내림)는 넣은 값을 그대로 두되, 그때 "-" 뿐이면 연 때 값으로 되돌린다.
 * 위 "취소" 는 이 칸을 연 때 값으로 되돌리고 닫는다. 바뀌는 결과가 보이게 뒤 덮개는 옅다.
 * hint = 안내 줄, autoValue = 빈칸일 때 계산값("자동 -7.385"), blankable = "비우기 · 자동 계산" 단추(빈칸으로 하고 닫는다).
 * preview = 제목 위 결과 한 줄(누를 때마다 바뀐다), figure = 제목 아래 작은 그림(시트 자리가 넉넉할 때만).
 * chain = 칸 묶음: 제목 오른쪽 "n / m", 마지막 칸이 아니면 닫기 아래 "다음"(닫을 때처럼 다듬고 다음 칸을 연다 — 시트는 그대로).
 * PC 는 실제 키보드도 받는다(숫자 · . · − · Backspace · Delete = C · Enter · Tab = 다음(마지막 칸 · 묶음 밖은 닫기) ·
 * Shift+Tab = 앞 칸 · Esc = 닫기).
 */
@Composable
internal fun NumberPad(
    label: String,
    unit: String,
    initial: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
    hint: String? = null,
    autoValue: String? = null,
    blankable: Boolean = false,
    figure: (@Composable (Modifier) -> Unit)? = null,
    preview: String? = null,
    chain: FieldChain? = null,
) {
    val c = AppTheme.colors
    // 값이 바로 들어가 initial 도 따라 바뀌므로 연 때 값을 잡아 둔다. 묶음에서 다른 칸으로 가면 그 칸 값으로 새로 잡는다
    val id = chain?.active
    val start = remember(id) { initial }
    var keys by remember(id) { mutableStateOf("") }
    val text = NumEdit.type(start, keys)
    val fresh = keys.isEmpty() && start.isNotEmpty()
    val next = id?.let { chain.neighbour(it, 1) }
    val prev = id?.let { chain.neighbour(it, -1) }
    // 닫거나 다른 칸으로 갈 때: "-" 뿐이면 연 때 값으로
    fun settle() {
        if (keys.isNotEmpty() && NumEdit.live(NumEdit.type(start, keys)) == null) onChange(start)
    }
    fun close() {
        settle()
        onClose()
    }
    fun cancel() {
        if (keys.isNotEmpty()) onChange(start)
        onClose()
    }
    fun go(to: String) {
        settle()
        chain?.open(to)
    }
    fun press(k: Char) {
        // 다른 칸으로 막 넘어가 아직 다시 그리기 전에 온 키는 앞 칸에 넣지 않는다
        if (chain != null && chain.active != id) return
        when (k) {
            NumEdit.NEXT -> if (next != null) go(next) else close()
            NumEdit.PREV -> if (prev != null) go(prev)
            else -> {
                keys += k
                NumEdit.live(NumEdit.type(start, keys))?.let(onChange)
            }
        }
    }
    BottomSheet(
        ::close,
        onKey = { e -> NumEdit.keyOf(e)?.let { press(it); true } ?: false },
        scrimAlpha = 0.25f,
        onHeight = chain?.let { ch -> { ch.sheetHeight = it } },
    ) {
        BoxWithConstraints {
            val room = maxHeight
            Column {
                // 큰 글꼴 · 좁은 폰에서도 끝 점 좌표가 "…" 로 잘리지 않게 numSmall(12.5sp) 에서 9sp 까지 줄인다
                if (preview != null) {
                    BasicText(
                        preview, Modifier.padding(bottom = 2.dp), style = AppType.numSmall.copy(color = c.valueComp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FitDown(12.5f, 9f),
                    )
                }
                val order = chain?.order.orEmpty()
                SheetTitle(label, if (id in order) "${order.indexOf(id) + 1} / ${order.size}" else "") {
                    Box(
                        Modifier.size(48.dp, 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = ::cancel),
                        contentAlignment = Alignment.Center,
                    ) { Text("취소", color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                if (figure != null && room >= FIGURE_MIN_H) {
                    // 둥근 바탕은 그림 칸에(가운데 둔 비율 그림이면 그 폭만) — 넓은 바탕 가운데서 소재 면이 칸 안에서 끊겨 떠 보였다
                    Box(
                        Modifier
                            .padding(bottom = 10.dp)
                            .fillMaxWidth()
                            .height(if (room >= FIGURE_BIG_H) 170.dp else 150.dp),
                        contentAlignment = Alignment.Center,
                    ) { figure(Modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(c.subtle)) }
                }
                PadDisplay(text, unit, fresh, autoValue)
                Row(Modifier.padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // 좁은 폰 · 큰 글꼴에서 '고 / 치기' 처럼 어절 안에서 줄이 바뀌지 않게(keepWords)
                        if (hint != null) Text(keepWords(hint), color = c.textBody, fontSize = 13.sp, lineHeight = 18.sp)
                        Text(if (fresh) keepWords("숫자 · − · . 를 누르면 새 값 · ⌫ 로 고치기") else "", color = c.textMuted, fontSize = 12.sp)
                    }
                    if (blankable) {
                        val pill = RoundedCornerShape(50)
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .minimumInteractiveComponentSize()
                                .height(40.dp)
                                .clip(pill)
                                .border(1.5.dp, c.lineField, pill)
                                .clickable {
                                    onChange("")
                                    onClose()
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("비우기 · 자동 계산", color = c.textBody, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
                val gap = 8.dp
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
                        listOf("789", "456", "123", "-0.").forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { k ->
                                    PadKey(Modifier.weight(1f), onClick = { press(k) }, strong = k.isDigit()) {
                                        Text(if (k == '-') "−" else "$k", color = c.textStrong, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    // C 는 맨 위(닫기 · 다음에서 떨어뜨려 잘못 눌러 지우지 않게). 값은 이미 들어가 있어 닫기는 닫기만 한다
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                        PadKey(Modifier.fillMaxWidth(), onClick = { press('C') }) {
                            Text("C", color = c.red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        PadKey(Modifier.fillMaxWidth(), onClick = { press('<') }) {
                            Icon(AppIcons.Backspace, "지우기", Modifier.size(26.dp), tint = c.textBody)
                        }
                        val accent = if (c.dark) c.appsFill else c.teal
                        if (next != null) {
                            PadKey(Modifier.fillMaxWidth(), onClick = ::close, outline = c.teal) {
                                Text("닫기", color = c.tealDeep, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            PadKey(Modifier.fillMaxWidth(), onClick = { press(NumEdit.NEXT) }, fill = accent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("다음", color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Icon(AppIcons.ChevronRight, null, Modifier.padding(start = 4.dp).size(16.dp), tint = c.onAccent)
                                }
                            }
                        } else {
                            PadKey(Modifier.fillMaxWidth(), onClick = { press(NumEdit.NEXT) }, height = KEY_H * 2 + gap, fill = accent) {
                                Text("닫기", color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 키패드 위 값 칸(64dp): 지금 글(연 때는 "통째 선택" 바탕), 빈칸이면 계산값 "자동 …"(없으면 "빈칸") + 단위 */
@Composable
private fun PadDisplay(text: String, unit: String, fresh: Boolean, autoValue: String?) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(shape).background(c.field).border(2.dp, c.lineFocus, shape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val auto = text.isEmpty() && !autoValue.isNullOrEmpty()
            Text(
                if (auto) "자동 $autoValue" else text.ifEmpty { "빈칸" },
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (fresh) c.lineFocus.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(horizontal = 4.dp),
                color = if (text.isNotEmpty()) c.textValue else if (auto) c.valueComp else c.textHint,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(unit, color = c.textMuted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * 키패드 그림(150dp) · 큰 그림(170dp)을 넣는 시트 안 자리(창 높이 − 시스템 막대 · 손잡이 · 아래 여백).
 * 그림을 넣어도 시트 위에 머리 · 고치는 칸(약 180dp)이 보일 만큼일 때만 — 412×915 폰 · PC 창은 큰 그림, 360×800 폰은 그림 없이
 */
private val FIGURE_MIN_H = 780.dp
private val FIGURE_BIG_H = 820.dp

private val KEY_H = 58.dp

@Composable
private fun PadKey(
    modifier: Modifier,
    onClick: () -> Unit,
    strong: Boolean = false,
    fill: Color? = null,
    outline: Color? = null,
    height: Dp = KEY_H,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(fill ?: if (strong) c.subtle else c.field)
            .border(if (outline != null) 1.5.dp else 1.dp, outline ?: if (fill != null) Color.Transparent else c.lineCard, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
}

/** 키패드 글 고치기(화면과 떼어 시험한다). 키: '0'~'9' '.' '-'(부호 바꾸기) '<'(지우기) 'C'(비우기) */
internal object NumEdit {
    /** 다음 칸(마지막 칸 · 묶음 밖은 닫기) · 앞 칸 */
    const val NEXT = '='
    const val PREV = '^'
    private const val MAX_DIGITS = 12

    /** 연 뒤 처음 누를 때 원래 값을 덮어쓰는 키(숫자 · . · −). − 를 먼저 누르면 새 음수: Z-5 에서 − 1 2 → -12 */
    private fun replaces(k: Char) = k.isDigit() || k == '.' || k == '-'

    /** 키패드를 열고(값 initial) keys 를 차례로 누른 글. 처음 키가 숫자 · . · − 면 원래 값을 덮어쓴다 */
    fun type(initial: String, keys: String): String =
        keys.foldIndexed(initial) { i, t, k -> press(if (i == 0 && replaces(k)) "" else t, k) }

    fun press(text: String, k: Char): String = when {
        k.isDigit() -> when {
            text.count { it.isDigit() } >= MAX_DIGITS -> text
            text == "0" -> "$k"
            text == "-0" -> "-$k"
            else -> text + k
        }
        k == '.' -> when {
            '.' in text -> text
            text.isEmpty() || text == "-" -> text + "0."
            else -> "$text."
        }
        k == '-' -> if (text.startsWith("-")) text.drop(1) else "-$text"
        k == '<' -> text.dropLast(1)
        k == 'C' -> ""
        else -> text
    }

    /** 칸에 넣을 글로 다듬기: "5." → "5", "-" → "", "-0" → "0" */
    fun finish(text: String): String {
        val t = text.removeSuffix(".")
        if (t.isEmpty() || t == "-") return ""
        return if (t.toDouble() == 0.0) t.removePrefix("-") else t
    }

    /** 누르는 동안 칸에 바로 넣을 값(다듬은 글, "" = 빈칸). "-" 뿐이면 숫자가 아니라 null — 칸은 앞 값을 둔다 */
    fun live(text: String): String? = if (text == "-") null else finish(text)

    fun keyOf(e: KeyEvent): Char? {
        if (e.isCtrlPressed || e.isAltPressed) return null
        digitKeys.indexOf(e.key).takeIf { it >= 0 && !e.isShiftPressed }?.let { return '0' + it }
        padKeys.indexOf(e.key).takeIf { it >= 0 }?.let { return '0' + it }
        return when (e.key) {
            Key.Period, Key.NumPadDot, Key.Comma -> '.'
            Key.Minus, Key.NumPadSubtract -> '-'
            Key.Backspace -> '<'
            Key.Delete -> 'C'
            Key.Enter, Key.NumPadEnter -> NEXT
            Key.Tab -> if (e.isShiftPressed) PREV else NEXT
            else -> null
        }
    }

    private val digitKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    private val padKeys = listOf(
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
        Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
    )
}
