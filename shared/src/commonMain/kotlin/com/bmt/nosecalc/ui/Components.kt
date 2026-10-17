package com.bmt.nosecalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.theme.AppTheme
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
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                            .clip(shape)
                            .background(c.subtle)
                            .border(if (on) 3.dp else 2.dp, if (on) c.teal else c.lineStrong, shape)
                            .clickable { onSelect(type) },
                    ) {
                        drawing(type)
                        TypeChip(
                            type, chipColor?.invoke(type) ?: c.tealDeep, chipTextSize,
                            Modifier
                                .align(chip.align)
                                .padding(14.dp),
                        )
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

enum class WarnLevel { Warn, Error }

/** 경고 띠. Warn = 노랑(숫자는 나왔지만 확인할 것), Error = 빨강(계산 멈춤) */
@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier, level: WarnLevel = WarnLevel.Warn) {
    val c = AppTheme.colors
    val rc = if (level == WarnLevel.Warn) c.warn else c.error
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(rc.bg).border(1.5.dp, rc.border, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Alert, null, Modifier.size(20.dp), tint = rc.label)
        Spacer(Modifier.width(10.dp))
        Text(text, color = rc.value, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    }
}

/** 아직 안 만든 기능 화면의 자리표시 */
@Composable
fun ComingSoon(feature: Feature, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FeatureIcon(feature, Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text("준비 중", color = c.textStrong, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "${feature.menuSub} 화면은 다음 단계에서 채웁니다.",
            color = c.textMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
