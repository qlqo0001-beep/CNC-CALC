package com.bmt.nosecalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.RoleColors

/** 숫자칸 역할색(1.1 역할색 입력칸): 시작점 = 노랑, 목표점 = 빨강, 치수 = 연노랑 */
enum class FieldRole { Normal, Start, End, Dim }

/**
 * 숫자칸(위 라벨 줄 + 52dp 칸, 값 가운데 굵게). 누르면 [NumberPad] 가 뜬다 — 시스템 키보드는 쓰지 않는다.
 * value 는 쓴 그대로의 글("5.0", "-25", "" = 빈칸). 숫자는 value.toDoubleOrNull() 로 꺼낸다.
 * 키패드에서 누를 때마다 값이 바로 onValueChange 로 들어간다(결과·그림·NC 가 따라 바뀐다).
 * 값은 단위와 겹치지 않게 길면 줄인다([ValueAndUnit]). 칸 모양은 색 + 표 + 굵기로 가른다(색만으로 가르지 않는다):
 * - autoValue = 빈칸일 때 계산된 값: 청록 옅은 바탕·테두리 + valueComp 글 + 라벨 줄 "자동" 표.
 *   "" 이면 풀지 못한 것 → 칸에 "—", 표는 "자동 · 계산 안 됨"
 * - error = 이 칸 값이 문제: 빨강 2dp 테두리 · 옅은 빨강 바탕 · 라벨 앞 멈춤 아이콘
 * - example = 아직 바꾸지 않은 예시 값: 흐린 글 + "예시" 표. tag = 라벨 뒤 회색 표("Ø직경" · "반경")
 * - placeholder = 빈칸 자리표시("기본 0" 처럼, 흐리게)
 * hint · blankable 은 키패드로 넘긴다(안내 줄 · "비우기 · 자동 계산" 단추).
 * id · figure · preview 는 칸 묶음(다음 칸) · 키패드 그림 · 결과 한 줄 자리 — 아직 그리지 않는다.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "",
    role: FieldRole = FieldRole.Normal,
    placeholder: String = "",
    autoValue: String? = null,
    error: Boolean = false,
    example: Boolean = false,
    tag: String? = null,
    hint: String? = null,
    blankable: Boolean = false,
    id: String = label,
    figure: (@Composable (Modifier) -> Unit)? = null,
    preview: String? = null,
) {
    val c = AppTheme.colors
    val rc = roleColors(role)
    var editing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val unsolved = value.isEmpty() && autoValue == ""
    val computed = value.isEmpty() && !autoValue.isNullOrEmpty()
    // 칸 글 · 색 · 굵기: 넣은 값 > 못 푼 자동 > 자동값 > 자리표시
    val (shown, color, weight) = when {
        value.isNotEmpty() -> Triple(value, if (example) c.textMuted else rc.value, if (example) FontWeight.SemiBold else FontWeight.Bold)
        unsolved -> Triple("—", c.textHint, FontWeight.Medium)
        autoValue != null -> Triple(autoValue, c.valueComp, FontWeight.SemiBold)
        else -> Triple(placeholder, c.textHint, FontWeight.Medium)
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (error) Icon(AppIcons.Stop, null, Modifier.padding(end = 4.dp).size(16.dp), tint = c.error.label)
            // 라벨이 먼저 자리를 잡고 표는 남은 자리에(어느 칸인지가 먼저)
            Text(label, style = AppType.label, color = if (error) c.error.label else rc.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tag != null) FieldChip(tag, c.subtle, c.textBody, c.lineField)
            if (unsolved) FieldChip("자동 · 계산 안 됨", c.error.bg, c.error.label, short = "계산 안 됨")
            if (computed) FieldChip("자동", c.teal.copy(alpha = 0.14f), c.tealDeep)
            if (example) FieldChip("예시", c.warn.bg, c.warn.label, c.warn.border)
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
                .clickable { editing = true }
                .padding(horizontal = 12.dp),
        )
    }
    if (editing) {
        NumberPad(label, unit, value, onValueChange, onClose = { editing = false }, hint = hint, autoValue = autoValue, blankable = blankable)
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
 * 칸 값 글자 크기: 20sp 에서 0.5sp 씩 줄여 한 줄에 다 들어가는 크기(11sp 까지). 그래도 넘치면 "…" 로 줄인다 —
 * 잘린 숫자가 멀쩡한 값처럼 보이지 않게. (StepBased 는 PC 에서 "…" 를 넘침으로 보지 않아 줄이지 않는다)
 */
private data object ValueFit : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = 20f
        while (size > 11f && performLayout(constraints, text, size.sp).multiParagraph.intrinsics.maxIntrinsicWidth > constraints.maxWidth) size -= 0.5f
        return size.sp
    }
}

/**
 * 라벨 줄 작은 표(11.5sp 굵게, 모서리 4dp): "자동" · "예시" · "Ø직경". 라벨 다음 남은 자리에 text 가 다 들어가지 않으면 short 로,
 * 그것도 넘치면 그리지 않는다(반쯤 잘린 표 대신)
 */
@Composable
private fun FieldChip(text: String, bg: Color, fg: Color, border: Color = Color.Transparent, short: String? = null) {
    val shape = RoundedCornerShape(4.dp)
    Layout({
        listOfNotNull(text, short).forEach {
            Text(
                it,
                Modifier.padding(start = 6.dp).clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = 5.dp),
                color = fg,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }) { ms, cs ->
        val p = ms.firstNotNullOfOrNull { m -> m.measure(Constraints()).takeIf { it.width <= cs.maxWidth } }
        layout(p?.width ?: 0, p?.height ?: 0) { p?.place(0, 0) }
    }
}

@Composable
private fun roleColors(role: FieldRole): RoleColors {
    val c = AppTheme.colors
    return when (role) {
        FieldRole.Normal -> c.fieldNormal
        FieldRole.Start -> c.fieldStart
        FieldRole.End -> c.fieldEnd
        FieldRole.Dim -> c.fieldDim
    }
}

/**
 * 숫자 키패드(바텀시트). 열면 지금 값이 "통째 선택"된 채 보이고, 처음 누른 숫자 · . · − 가 그 값을 덮어쓴다(⌫ 는 원래 값을 고친다).
 * 확인 단추는 없다 — 누를 때마다 값이 바로 onChange 로 들어간다. "-" 만 남은 동안은 칸에 앞 값을 둔다.
 * 닫기(닫기 키 · 바깥 누름 · 뒤로 · Esc · 손잡이 끌어내림)는 넣은 값을 그대로 두되, 그때 "-" 뿐이면 연 때 값으로 되돌린다.
 * 위 "취소" 는 연 때 값으로 되돌리고 닫는다. 바뀌는 결과가 보이게 뒤 덮개는 옅다.
 * hint = 안내 줄, autoValue = 빈칸일 때 계산값("자동 -7.385"), blankable = "비우기 · 자동 계산" 단추(빈칸으로 하고 닫는다).
 * PC 는 실제 키보드도 받는다(숫자 · . · − · Backspace · Delete = C · Enter = 닫기 · Esc = 닫기).
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
) {
    val c = AppTheme.colors
    // 값이 바로 들어가 initial 도 따라 바뀌므로 연 때 값을 잡아 둔다
    val start = remember { initial }
    var keys by remember { mutableStateOf("") }
    val text = NumEdit.type(start, keys)
    val fresh = keys.isEmpty() && start.isNotEmpty()
    fun close() {
        if (keys.isNotEmpty() && NumEdit.live(NumEdit.type(start, keys)) == null) onChange(start)
        onClose()
    }
    fun cancel() {
        if (keys.isNotEmpty()) onChange(start)
        onClose()
    }
    fun press(k: Char) {
        if (k == NumEdit.CLOSE) return close()
        keys += k
        NumEdit.live(NumEdit.type(start, keys))?.let(onChange)
    }
    BottomSheet(::close, onKey = { e -> NumEdit.keyOf(e)?.let { press(it); true } ?: false }, scrimAlpha = 0.25f) {
        SheetTitle(label) {
            Box(
                Modifier.size(48.dp, 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = ::cancel),
                contentAlignment = Alignment.Center,
            ) { Text("취소", color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(shape).background(c.field).border(2.dp, c.lineFocus, shape).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                // 빈칸이면 지금 계산값을 "자동 …" 으로(없으면 "빈칸")
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
        Row(Modifier.padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (hint != null) Text(hint, color = c.textBody, fontSize = 13.sp, lineHeight = 18.sp)
                Text(if (fresh) "숫자 · − · . 를 누르면 새 값 · ⌫ 로 고치기" else "", color = c.textMuted, fontSize = 12.sp)
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
            // C 는 맨 위(닫기에서 떨어뜨려 잘못 눌러 지우지 않게). 값은 이미 들어가 있어 닫기는 닫기만 한다
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                PadKey(Modifier.fillMaxWidth(), onClick = { press('C') }) {
                    Text("C", color = c.red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                PadKey(Modifier.fillMaxWidth(), onClick = { press('<') }) {
                    Icon(AppIcons.Backspace, "지우기", Modifier.size(26.dp), tint = c.textBody)
                }
                PadKey(Modifier.fillMaxWidth(), onClick = { press(NumEdit.CLOSE) }, height = KEY_H * 2 + gap, fill = if (c.dark) c.appsFill else c.teal) {
                    Text("닫기", color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val KEY_H = 58.dp

@Composable
private fun PadKey(
    modifier: Modifier,
    onClick: () -> Unit,
    strong: Boolean = false,
    fill: Color? = null,
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
            .border(1.dp, if (fill != null) Color.Transparent else c.lineCard, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
}

/** 키패드 글 고치기(화면과 떼어 시험한다). 키: '0'~'9' '.' '-'(부호 바꾸기) '<'(지우기) 'C'(비우기) */
internal object NumEdit {
    const val CLOSE = '='
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
            Key.Enter, Key.NumPadEnter -> CLOSE
            else -> null
        }
    }

    private val digitKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    private val padKeys = listOf(
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
        Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
    )
}
