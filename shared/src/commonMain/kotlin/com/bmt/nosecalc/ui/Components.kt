package com.bmt.nosecalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.RoleColors
import com.bmt.nosecalc.ui.theme.onFill

/** 흰 카드(1.3): 1dp 연한 테두리 + 아주 옅은 그림자, 모서리 12dp */
@Composable
fun AppCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.lineCard, shape)
            .padding(padding),
        content = content,
    )
}

/** TYPE 칩 자리: 테이퍼(02)는 왼쪽 아래, 라운드(04)·11 은 왼쪽 위. End 는 그림을 좌우로 뒤집을 때(테이퍼 역방향) */
enum class ChipPlace(val align: Alignment) {
    BottomStart(Alignment.BottomStart),
    TopStart(Alignment.TopStart),
    BottomEnd(Alignment.BottomEnd),
    TopEnd(Alignment.TopEnd),
}

/**
 * TYPE 그림 카드 2열(02·04·11). type 은 1부터. 개수가 홀수면 마지막 줄 오른쪽은 빈칸.
 * drawing 이 카드 안 전체를 채운다(칩은 그 위에 얹힌다).
 */
@Composable
fun TypeGrid(
    count: Int,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chip: ChipPlace = ChipPlace.BottomStart,
    aspectRatio: Float = 1.08f,
    chipColor: ((Int) -> Color)? = null,
    /** TYPE 칩 글자 크기(sp). 테이퍼(02)는 작은 칩 11.5 */
    chipTextSize: Float = 14f,
    drawing: @Composable BoxScope.(type: Int) -> Unit,
) {
    val c = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..count).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { type ->
                    val on = type == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                            .typeFrame(on, c.subtle) { onSelect(type) },
                    ) {
                        drawing(type)
                        TypeChip(
                            type, chipColor?.invoke(type) ?: c.tealDeep, chipTextSize,
                            Modifier
                                .align(chip.align)
                                .padding(14.dp),
                        )
                        // 칩이 오른쪽 위에 있으면(내경 역방향) 체크는 왼쪽 위
                        if (on) SelectedCheck(if (chip == ChipPlace.TopEnd) Alignment.TopStart else Alignment.TopEnd)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypeChip(type: Int, color: Color, size: Float, modifier: Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Text(
        "형상 $type",
        modifier.clip(shape).background(c.field).border(1.5.dp, c.lineField, shape).padding(horizontal = 9.dp, vertical = 2.dp),
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em,
    )
}

/**
 * 형상 카드(고르기 화면): 36dp 머리 띠(칩 + 이름, 길면 …) + 1dp 구분선 + 몸통(그림). 모서리 12dp.
 * 고르면 2dp 청록 테두리 + 옅은 청록 바탕 + 오른쪽 위 체크 원 — 색만으로 가르지 않는다
 */
@Composable
fun TypeCard(
    chip: String,
    caption: String?,
    chipColors: RoleColors,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    body: @Composable BoxScope.() -> Unit,
) {
    val c = AppTheme.colors
    Box(modifier.aspectRatio(aspectRatio).typeFrame(selected, c.card, onClick)) {
        Column(Modifier.fillMaxSize()) {
            // 오른쪽 36dp 는 체크 원 자리(고르기 전후로 글이 밀리지 않게 늘 비운다)
            Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 8.dp, end = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                HeadChip(chip, chipColors)
                if (caption != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        caption,
                        color = c.textBody,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
            Box(Modifier.fillMaxWidth().weight(1f), content = body)
        }
        if (selected) SelectedCheck(Alignment.TopEnd)
    }
}

/** 형상 카드 틀(TypeCard · TypeGrid): 보통 1dp 연한 테두리 + 옅은 그림자(라이트만), 고르면 2dp 청록 테두리 + 청록 옅은 바탕 */
@Composable
private fun Modifier.typeFrame(selected: Boolean, bg: Color, onClick: () -> Unit): Modifier {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    return shadow(if (c.dark) 0.dp else 1.dp, shape)
        .clip(shape)
        .background(bg)
        .background(if (selected) c.teal.copy(alpha = if (c.dark) .10f else .06f) else Color.Transparent)
        .border(if (selected) 2.dp else 1.dp, if (selected) c.teal else c.lineCard, shape)
        .clickable(onClick = onClick)
}

/** 고른 카드 모서리의 청록 체크 원(20dp, 8dp 안쪽) */
@Composable
private fun BoxScope.SelectedCheck(align: Alignment) {
    val c = AppTheme.colors
    Box(
        Modifier.align(align).padding(8.dp).size(20.dp).clip(CircleShape).background(c.teal),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Check, null, Modifier.size(14.dp), tint = c.onFill(c.teal))
    }
}

/** 형상 카드 머리 띠의 칩: 옅은 바탕 + 1dp 테두리 + 굵은 글(라운드 홀수 하늘 · 짝수 민트) */
@Composable
private fun HeadChip(text: String, rc: RoleColors) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.height(21.dp).clip(shape).background(rc.bg).border(1.dp, rc.border, shape).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = rc.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em, maxLines = 1)
    }
}

/** 카드 머리: 제목(cardTitle) + 아랫줄 부제(caption, 2dp 띄움) + 오른쪽 trailing. 아래로 12dp 띄운다 */
@Composable
fun CardHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.cardTitle, color = c.textStrong)
            if (subtitle != null) Text(subtitle, Modifier.padding(top = 2.dp), style = AppType.caption, color = c.textMuted)
        }
        trailing?.invoke()
    }
}

/**
 * 좌표 한 쌍 X | Z: 두 칸 사이 1dp 구분선, 칸마다 축 글자(작게) + 오른쪽 맞춤 고정폭 굵은 값, 아래에 바뀐 양 "Δ −0.469"(있을 때).
 * 축 글자는 제 값 바로 옆에 붙는다. 값이 칸보다 길면(좁은 폰 · 큰 글꼴) 글자를 줄여 한 줄에 둔다 — 숫자가 잘리지 않게
 */
@Composable
fun CoordPair(
    x: String,
    z: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 24.sp,
    dx: String? = null,
    dz: String? = null,
) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CoordCell("X", x, dx, color, size, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(1.dp).height(28.dp).background(c.lineCard))
        Spacer(Modifier.width(12.dp))
        CoordCell("Z", z, dz, color, size, Modifier.weight(1f))
    }
}

@Composable
private fun CoordCell(axis: String, value: String, delta: String?, color: Color, size: TextUnit, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier) {
        // 축 글자 + 값을 한 덩어리로 칸 오른쪽에 붙인다(소수점이 줄마다 맞고, 축 글자가 제 값 옆에 온다)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(axis, Modifier.alignByBaseline(), color = c.textMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            BasicText(
                value,
                Modifier.weight(1f, fill = false).alignByBaseline(),
                style = AppType.numHero.copy(color = color),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(12.sp, size, 0.5.sp),
            )
        }
        if (delta != null) {
            Text("Δ $delta", Modifier.fillMaxWidth(), style = AppType.numSmall, color = c.textMuted, textAlign = TextAlign.End)
        }
    }
}

/** 이름 + 값 한 벌: 이름 12sp(흐림) 위, 고정폭 굵은 값 아래 */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.textValue,
    size: TextUnit = 17.sp,
) {
    val c = AppTheme.colors
    Column(modifier) {
        Text(label, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = color, fontFamily = AppTheme.mono, fontSize = size, fontWeight = FontWeight.Bold)
    }
}

/** 한 줄 안내 + '자세히' 단추(48dp 높이): 누르면 긴 설명을 편다. 처음엔 접혀 있고, 다른 화면에 다녀와도 그대로 */
@Composable
fun ExpandableHint(short: String, long: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(short, Modifier.weight(1f), color = c.textBody, fontSize = 13.sp, lineHeight = 19.sp)
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { open = !open }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (open) "접기" else "자세히", color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(2.dp))
                Icon(AppIcons.ChevronDown, null, Modifier.size(14.dp).rotate(if (open) 180f else 0f), tint = c.tealDeep)
            }
        }
        if (open) Text(long, style = AppType.caption, color = c.textMuted)
    }
}

/** 채움 세그먼트(나사 OD/ID·방향, 극좌표 외측/내측, 시뮬 2D/3D): 트랙 안에서 고른 칸만 색으로 채운다 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.blue,
    track: Color = AppTheme.colors.segTrack,
    height: Dp = 48.dp,
    fontSize: Int = 16,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(11.dp)
    Row(modifier.height(height).clip(shape).background(track).border(1.dp, c.lineCard, shape).padding(4.dp)) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) color else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(s, color = if (on) c.onFill(color) else c.textMuted, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * 테두리 버튼 묶음(테이퍼 가공 종류, 02): 비선택 = 투명 + 파랑 테두리, 선택 = 밝은 바탕 + 그림자 + 진청록 글자.
 * rows 는 줄마다의 이름, 번호는 줄을 이어 센다(02: 1줄 3칸 + 2줄 2칸 → 0~4).
 */
@Composable
fun OutlineTabs(rows: List<List<String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.subtle.copy(alpha = if (c.dark) 0.35f else 0.25f)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var index = 0
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { name ->
                    val i = index++
                    val on = i == selected
                    val shape = RoundedCornerShape(9.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .shadow(if (on && !c.dark) 3.dp else 0.dp, shape)
                            .clip(shape)
                            .background(if (on) (if (c.dark) c.field else c.subtle) else Color.Transparent)
                            .border(1.5.dp, if (on) (if (c.dark) c.teal.copy(alpha = 0.6f) else c.lineCard) else c.lineTabIdle, shape)
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name,
                            color = if (on) c.tealDeep else c.textBody,
                            // 02 와 글자 폭을 맞춘 값(원본 라틴 글자가 좁다)
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            letterSpacing = (-0.03).em,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 결과 카드(06·10): 왼쪽 위 대문자 작은 라벨, 아래 고정폭 큰 숫자. 축 글자와 값 사이를 넓게.
 * 예: ResultCard("COMPENSATED POINT (보정좌표)", listOf("X" to "34.000", "Z" to "-2.186"))
 */
@Composable
fun ResultCard(
    label: String,
    values: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.valueComp,
) {
    val c = AppTheme.colors
    AppCard(modifier, padding = 12.dp) {
        Text(label, color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            values.forEach { (axis, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(axis, color = color, fontFamily = AppTheme.mono, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text(v, color = color, fontFamily = AppTheme.mono, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em)
                }
            }
        }
    }
}

enum class WarnLevel { Warn, Error, Info }

/**
 * 경고 띠: 왼쪽 4dp 색 띠 + 모양이 다른 아이콘 + 머리말 + 글 — 색만으로 가르지 않는다(흑백에서도 아이콘·머리말로 갈림).
 * Warn = 노랑 삼각형(숫자는 나왔지만 확인할 것), Error = 빨강 팔각형(계산 멈춤), Info = 파랑 원(안내, 머리말 없음).
 * collapsedText 가 있으면 한 줄("… ⌄", 44dp)로 접혀 시작하고 누르면 편다. actions = 글 아래 알약 단추(이름 to 할 일)
 */
@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    level: WarnLevel = WarnLevel.Warn,
    collapsedText: String? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val c = AppTheme.colors
    val (rc, icon, headline) = when (level) {
        WarnLevel.Warn -> Triple(c.warn, AppIcons.Alert, "확인 필요 — 결과는 나왔습니다")
        WarnLevel.Error -> Triple(c.error, AppIcons.Stop, "계산 멈춤 — 결과·NC 없음")
        WarnLevel.Info -> Triple(c.info, AppIcons.Info, null)
    }
    var open by rememberSaveable { mutableStateOf(false) }
    val folded = collapsedText != null && !open
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rc.bg)
            .drawWithContent {
                drawContent()
                drawRect(rc.label, size = Size(4.dp.toPx(), size.height))
            }
            .border(1.dp, rc.border, shape)
            .then(if (collapsedText != null) Modifier.clickable { open = !open } else Modifier)
            .heightIn(min = 44.dp)
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = if (folded) Alignment.CenterVertically else Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = rc.label)
        Spacer(Modifier.width(10.dp))
        if (folded) {
            Text(
                collapsedText,
                Modifier.weight(1f, fill = false),
                color = rc.value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 4.dp).size(16.dp), tint = rc.label)
        } else {
            Column(Modifier.weight(1f)) {
                if (headline != null) {
                    Text(headline, Modifier.padding(bottom = 2.dp), color = rc.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                }
                Text(text, color = rc.value, fontSize = 13.5.sp, lineHeight = 19.sp)
                if (actions.isNotEmpty()) {
                    FlowRow(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        actions.forEach { (name, run) -> PillButton(name, null, run) }
                    }
                }
                if (collapsedText != null) {
                    Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("접기", color = rc.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(14.dp).rotate(180f), tint = rc.label)
                    }
                }
            }
        }
    }
}
