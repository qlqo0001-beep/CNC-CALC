package com.bmt.nosecalc.screens.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Pass
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.FieldChain
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.onFill

/**
 * 자동 계산 값의 주황(원본 08 의 '노란색 텍스트는 자동 연산 영역'): 나사 기능색(헤더 제목 주황)을 라이트에서는 글 색 쪽으로 25% 짙게 —
 * 흰 카드 위 #EA580C 는 3.6:1 이라 숫자 글 4.5:1 에 못 미친다(짙게 한 값 약 5.4:1). 다크는 그대로(#FB923C, 카드 위 약 7:1)
 */
internal fun AppColors.threadAuto(): Color {
    val orange = features.getValue(Feature.THREAD).title
    return if (dark) orange else lerp(orange, textStrong, .25f)
}

/** 칸 위 이름(NumberField 의 라벨 줄과 같은 모양) */
@Composable
internal fun ThreadLabel(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(keepWords(text), modifier, style = AppType.label, color = AppTheme.colors.fieldNormal.label, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * 고르는 칸(원본의 드롭다운): 위 이름(label = null 이면 없음 · 좁으면 띄어쓰기에서 두 줄) + 52dp 칸(값 굵게 · 오른쪽 ⌄).
 * 누르면 고르기 시트를 연다. 값이 칸보다 길면 0.75배까지 줄이고 그래도 넘치면 '…'(좁은 칸에서도 값이 보이게 여백 · ⌄ 는 작게).
 * empty = 아직 안 고름(값 대신 흐린 자리표시)
 */
@Composable
internal fun ChoiceField(label: String?, value: String, modifier: Modifier = Modifier, empty: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Column(modifier) {
        if (label != null) {
            ThreadLabel(label, maxLines = 2)
            Spacer(Modifier.height(6.dp))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(c.fieldNormal.bg)
                .border(1.5.dp, c.fieldNormal.border, shape)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
        // 아주 좁은 칸(360dp 폰 · 큰 글꼴의 1/4 칸)은 ⌄ 를 빼고 값에 자리를 준다
        val chevron = maxWidth >= 50.dp * LocalDensity.current.fontScale
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                value,
                Modifier.weight(1f),
                style = TextStyle(
                    color = if (empty) c.textHint else c.textValue,
                    fontFamily = AppTheme.sans,
                    fontWeight = if (empty) FontWeight.Medium else FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(12.sp, 16.sp, 0.5.sp),
            )
            if (chevron) Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(12.dp), tint = c.textMuted)
        }
        }
    }
}

/** 고르기 시트: 제목 · 보조 글 · 설명(note) 아래 고를 것(스크롤). 창 높이 85% 까지 */
@Composable
internal fun ChoiceSheet(title: String, trailing: String = "", note: String? = null, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    BottomSheet(onDismiss, maxHeightFraction = 0.85f) {
        SheetTitle(title, trailing)
        if (note != null) Text(keepWords(note), Modifier.padding(bottom = 8.dp), style = AppType.caption, color = c.textMuted)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
    }
}

/** 시트의 한 줄(56dp 이상): 이름(굵게) · 아래 설명, 고른 줄은 청록 체크 */
@Composable
internal fun ChoiceRow(text: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.teal.copy(alpha = if (c.dark) .14f else .08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(keepWords(sub), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        if (selected) Icon(AppIcons.Check, "고름", Modifier.padding(start = 8.dp).size(20.dp), tint = c.tealDeep)
    }
}

/** 짧은 값 여럿(사이즈 · 면취량 · 각도)을 칩 격자로: 한 줄에 columns 개 같은 폭, 칩 48dp, 고른 칩은 청록 채움 + 굵은 테 */
@Composable
internal fun ChoiceGrid(items: List<String>, selected: Int?, columns: Int = 4, onPick: (Int) -> Unit) {
    val c = AppTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val gap = 8.dp
        // px 반올림으로 한 줄 합이 폭을 넘어 줄이 하나 일찍 바뀌지 않게 1dp 덜
        val w = (maxWidth - gap * (columns - 1)) / columns - 1.dp
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            items.forEachIndexed { i, t ->
                val on = i == selected
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .width(w)
                        .height(48.dp)
                        .clip(shape)
                        .background(if (on) c.teal else c.field)
                        .border(if (on) 2.dp else 1.5.dp, if (on) c.tealDeep else c.lineField, shape)
                        .clickable { onPick(i) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        t,
                        style = TextStyle(color = if (on) c.onFill(c.teal) else c.textValue, fontFamily = AppTheme.sans, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(11.sp, 16.sp, 0.5.sp),
                    )
                }
            }
        }
    }
}

/** 색 띠가 붙은 숫자칸 자리: 칸 높이(52dp)의 4dp 띠(시작 · 끝 색 — 결과 띠의 '시작' · '끝' 과 같은 색) + 칸 */
@Composable
internal fun RowScope.Marked(color: Color, weight: Float, content: @Composable (Modifier) -> Unit) {
    Row(Modifier.weight(weight), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.width(4.dp).height(52.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        content(Modifier.weight(1f))
    }
}

/**
 * '고급 공차 및 인선 설정' 접이 막대(원본 08 ⑨): 56dp 줄 전체를 누른다. 이름 · 지금 목표 % 표 · ⌄(펴면 위로).
 * 원본은 남색 채움 — 이 앱은 남색 테 + 옅은 바탕(흰 글이 남색 위 4.5:1 에 못 미쳐서)
 */
@Composable
internal fun AdvancedBar(open: Boolean, badge: String, onToggle: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(c.indigo.copy(alpha = if (c.dark) .16f else .08f))
            .border(1.5.dp, c.indigo.copy(alpha = .55f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 좁은 폰 · 큰 글꼴(360dp · 130%)에서 '고급 공차 및 인…' 로 잘리던 것 — 어절에서 두 줄로(3단계 최종 점검)
        Text(
            keepWords("고급 공차 및 인선 설정"),
            Modifier.weight(1f),
            color = c.textStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            badge,
            Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(c.indigo.copy(alpha = .16f)).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.textStrong,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Icon(AppIcons.ChevronDown, if (open) "접기" else "펴기", Modifier.padding(start = 8.dp).size(20.dp).rotate(if (open) 180f else 0f), tint = c.textBody)
    }
}

/**
 * G76 한 줄(원본 08 ⑫): 회색 상자 안에 'G76' + 낱말. 테두리 칸 = 눌러서 고치는 값(키패드 · 빈칸이면 기본값을 흐리게, 넣어야 할 빈칸은 파랑 '?'),
 * 테 없는 주황 고정폭 = 자동 계산 값(누르면 무엇에서 나왔는지 토스트). 고치는 중인 칸은 초점색, 계산을 멈춘 칸은 빨간 테
 */
@Composable
internal fun G76Line(words: List<NcWord>, chain: FieldChain, flagged: Set<ThreadField>, onEdit: (ThreadField) -> Unit, onNote: (String) -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    FlowRow(
        Modifier.fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("G76", Modifier.padding(end = 2.dp), style = AppType.numBody, color = c.textMuted)
        words.forEach { w ->
            val f = w.field
            if (f == null) AutoWord(w) { w.note?.let(onNote) } else EditWord(w, chain.active == f.key, f in flagged) { onEdit(f) }
        }
    }
}

/** 주소 글자(작게 · 흐리게) + 값(고정폭 굵게) */
@Composable
private fun WordText(letter: String, value: String, color: Color) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (letter.isNotEmpty()) Text(letter, Modifier.padding(end = 2.dp), color = c.textMuted, fontFamily = AppTheme.mono, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontFamily = AppTheme.mono, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun AutoWord(w: NcWord, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) { WordText(w.letter, w.value, if (w.value == "—") c.textHint else c.threadAuto()) }
}

@Composable
private fun EditWord(w: NcWord, editing: Boolean, error: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(7.dp)
    val (bg, border) = when {
        error -> c.error.bg to c.error.label
        editing -> c.card to c.lineFocus
        w.missing -> c.info.bg to c.info.border
        else -> c.card to c.lineField
    }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(bg)
            .border(if (editing || error) 2.dp else 1.5.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color = when {
            error -> c.error.label
            w.missing -> c.info.label
            w.byDefault -> c.textMuted
            else -> c.textValue
        }
        WordText(w.letter, w.value, color)
    }
}

/** 이름 + 값 한 칸(결과 격자): 이름 12sp 흐리게 위, 값 고정폭 굵게(주황 = 자동 계산 값) 아래, 아래 작은 글(sub) */
@Composable
internal fun ResultCell(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, color: Color = AppTheme.colors.threadAuto()) {
    val c = AppTheme.colors
    Column(modifier) {
        Text(label, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(
            value,
            style = AppType.numLarge.copy(color = color),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(11.sp, AppType.numLarge.fontSize, 0.5.sp),
        )
        if (sub != null) Text(sub, color = c.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
    }
}

/** 화면에 보이는 분석표 줄 한도 — 나머지는 '… n줄 더'(NC 는 다 복사된다) */
internal const val MAX_PASS_ROWS = 60

/**
 * 회차별 분석표: 회 · 누적 깊이 · 그 회 절입 · 그 회 X · (정삭 표). 깊이 · 절입은 반경, X 는 직경. 정삭 줄은 옅은 바탕 + '정삭' 표(색만으로 가르지 않게)
 */
@Composable
internal fun PassTable(passes: List<G76Pass>) {
    val c = AppTheme.colors
    val weights = listOf(.55f, 1.15f, 1f, 1.3f, .8f)
    @Composable
    fun RowScope.cell(i: Int, text: String, color: Color, mono: Boolean = true, bold: Boolean = false) = Text(
        text,
        Modifier.weight(weights[i]),
        color = color,
        fontFamily = if (mono) AppTheme.mono else AppTheme.sans,
        fontSize = if (mono) 14.sp else 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        textAlign = if (i == 0 || i == 4) TextAlign.Center else TextAlign.End,
        maxLines = 1,
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("회", "누적 깊이", "절입", "X (직경)", "").forEachIndexed { i, t -> cell(i, t, c.textMuted, mono = false) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineStrong))
    passes.take(MAX_PASS_ROWS).forEach { p ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .background(if (p.finish) c.subtle else Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cell(0, "${p.n}", c.textBody)
            cell(1, Fmt.fixed3(p.depth), c.textValue)
            cell(2, Fmt.fixed3(p.cut), if (p.cut == 0.0) c.textHint else c.textValue)
            cell(3, Fmt.fixed3(p.xDia), c.threadAuto(), bold = true)
            Box(Modifier.weight(weights[4]), contentAlignment = Alignment.Center) {
                if (p.finish) {
                    Text(
                        "정삭",
                        Modifier.clip(RoundedCornerShape(4.dp)).background(c.teal.copy(alpha = .14f)).padding(horizontal = 5.dp),
                        color = c.tealDeep,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
    }
    if (passes.size > MAX_PASS_ROWS) {
        Text("… ${passes.size - MAX_PASS_ROWS}줄 더 (NC 에는 모두)", Modifier.padding(top = 6.dp), color = c.textMuted, fontSize = 12.5.sp)
    }
}
