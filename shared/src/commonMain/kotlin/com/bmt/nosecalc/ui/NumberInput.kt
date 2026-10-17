package com.bmt.nosecalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.RoleColors

/** 숫자칸 역할색(1.1 역할색 입력칸): 시작점 = 노랑, 목표점 = 빨강, 치수 = 연노랑 */
enum class FieldRole { Normal, Start, End, Dim }

/**
 * 숫자칸(08 모양: 위 라벨 + 칸, 값 가운데 굵은 산세리프). 누르면 [NumberPad] 가 뜬다 — 시스템 키보드는 쓰지 않는다.
 * value 는 쓴 그대로의 글("5.0", "-25", "" = 빈칸). 숫자는 value.toDoubleOrNull() 로 꺼낸다.
 * autoValue = 빈칸일 때 계산된 값(흐리게 칸에, "자동" 표는 라벨 줄 오른쪽에). 값 글은 단위 자리를 비워 두고 길면 줄여 겹치지 않는다.
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
) {
    val c = AppTheme.colors
    val rc = roleColors(role)
    var editing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val auto = value.isEmpty() && autoValue != null
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, Modifier.weight(1f, fill = false), color = rc.label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (auto) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "자동",
                    Modifier.clip(RoundedCornerShape(4.dp)).background(c.teal.copy(alpha = 0.14f)).padding(horizontal = 5.dp),
                    color = c.tealDeep,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(rc.bg)
                .border(if (editing) 2.dp else 1.5.dp, if (editing) c.lineFocus else rc.border, shape)
                .clickable { editing = true }
                .padding(horizontal = 12.dp),
        ) {
            // 단위 자리만큼 양쪽을 비워 가운데에 둔다. 칸보다 길면 글자를 줄인다
            BasicText(
                value.ifEmpty { autoValue ?: placeholder },
                Modifier.align(Alignment.Center).padding(horizontal = if (unit.isEmpty()) 0.dp else 22.dp),
                style = TextStyle(
                    color = if (value.isEmpty()) c.textHint else rc.value,
                    fontFamily = AppTheme.sans,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(11.sp, 18.sp, 0.5.sp),
            )
            if (unit.isNotEmpty()) {
                Text(unit, Modifier.align(Alignment.CenterEnd), color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (editing) {
        NumberPad(
            label, unit, value,
            onDone = {
                onValueChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
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
 * 숫자 키패드(바텀시트). 열 때 값이 "통째 선택"된 상태라 처음 누른 숫자·점이 새 값이 되고,
 * ⌫·− 는 원래 값을 고친다. PC 는 실제 키보드도 받는다(숫자·.·−·Backspace·Delete=C·Enter=확인·Esc=닫기).
 */
@Composable
internal fun NumberPad(label: String, unit: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    var text by remember { mutableStateOf(initial) }
    var fresh by remember { mutableStateOf(initial.isNotEmpty()) }
    fun press(k: Char) {
        if (k == NumEdit.OK) {
            onDone(NumEdit.finish(text))
            return
        }
        text = NumEdit.press(if (fresh && NumEdit.replaces(k)) "" else text, k)
        fresh = false
    }
    BottomSheet(onDismiss, onKey = { e -> NumEdit.keyOf(e)?.let { press(it); true } ?: false }) {
        SheetTitle(label, if (unit.isEmpty()) "" else "단위 $unit")
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(shape).background(c.field).border(2.dp, c.lineFocus, shape).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text.ifEmpty { "빈칸" },
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (fresh) c.lineFocus.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(horizontal = 4.dp),
                    color = if (text.isEmpty()) c.textHint else c.textValue,
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
        Text(
            if (fresh) "숫자를 누르면 새 값이 됩니다 · ⌫ 로 고치기" else "",
            Modifier.padding(top = 6.dp, bottom = 10.dp),
            color = c.textMuted,
            fontSize = 12.sp,
        )
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                PadKey(Modifier.fillMaxWidth(), onClick = { press('<') }) {
                    Icon(AppIcons.Backspace, "지우기", Modifier.size(26.dp), tint = c.textBody)
                }
                PadKey(Modifier.fillMaxWidth(), onClick = { press('C') }) {
                    Text("C", color = c.red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                PadKey(Modifier.fillMaxWidth(), onClick = { press(NumEdit.OK) }, height = KEY_H * 2 + gap, fill = if (c.dark) c.appsFill else c.teal) {
                    Text("확인", color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    const val OK = '='
    private const val MAX_DIGITS = 12

    /** 열린 뒤 처음 누를 때 원래 값을 갈아 끼우는 키 */
    fun replaces(k: Char) = k.isDigit() || k == '.'

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

    /** 확인 때 다듬기: "5." → "5", "-" → "", "-0" → "0" */
    fun finish(text: String): String {
        val t = text.removeSuffix(".")
        if (t.isEmpty() || t == "-") return ""
        return if (t.toDouble() == 0.0) t.removePrefix("-") else t
    }

    fun keyOf(e: KeyEvent): Char? {
        if (e.isCtrlPressed || e.isAltPressed) return null
        digitKeys.indexOf(e.key).takeIf { it >= 0 && !e.isShiftPressed }?.let { return '0' + it }
        padKeys.indexOf(e.key).takeIf { it >= 0 }?.let { return '0' + it }
        return when (e.key) {
            Key.Period, Key.NumPadDot, Key.Comma -> '.'
            Key.Minus, Key.NumPadSubtract -> '-'
            Key.Backspace -> '<'
            Key.Delete -> 'C'
            Key.Enter, Key.NumPadEnter -> OK
            else -> null
        }
    }

    private val digitKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    private val padKeys = listOf(
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
        Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
    )
}
