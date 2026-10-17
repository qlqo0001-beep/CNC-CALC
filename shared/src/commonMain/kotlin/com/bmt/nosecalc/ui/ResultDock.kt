package com.bmt.nosecalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

/** 결과 띠 한 줄: 점 이름(색) + 보정 X · Z(이미 Fmt 로 쓴 글) */
data class DockRow(val name: String, val x: String, val z: String, val color: Color)

/** 결과 띠의 상태: 풀림 · 풀렸지만 확인할 것 count 건 · 계산 멈춤(경고 첫 문장) */
sealed interface DockStatus {
    data class Ok(val rows: List<DockRow>) : DockStatus
    data class Warn(val count: Int, val rows: List<DockRow>) : DockStatus
    data class Error(val text: String) : DockStatus
}

/** 키패드 미리보기 한 줄: "T1′ X39.531 Z0.000 · T2′ X50.000 Z-5.234" (점이 둘을 넘으면 처음 · 끝 · 외 n점) */
fun DockStatus.oneLine(): String = when (this) {
    is DockStatus.Ok -> rowsLine(rows)
    is DockStatus.Warn -> "${rowsLine(rows)} · 확인 필요 ${count}건"
    is DockStatus.Error -> "계산 멈춤 — $text"
}

private fun rowsLine(rows: List<DockRow>): String =
    shown(rows).joinToString(" · ") { "${it.name} X${it.x} Z${it.z}" } + if (rows.size > 2) " · 외 ${rows.size - 2}점" else ""

/** 띠에 보이는 줄: 점이 둘을 넘으면 처음과 끝만 */
private fun shown(rows: List<DockRow>) = if (rows.size > 2) listOf(rows.first(), rows.last()) else rows

/**
 * 화면 아래에 붙는 결과 띠(PC 넓은 창은 가운데 640dp 까지). 결과 카드가 화면 밖에 있을 때 visible 로 띄운다.
 * 오른쪽 48dp 단추(onJump)가 결과 카드로(멈춤이면 고칠 칸으로) 데려간다. 띠만 150ms 로 오르내리고 값은 깜빡이지 않는다
 */
@Composable
fun BoxScope.ResultDock(status: DockStatus, visible: Boolean, onJump: () -> Unit) {
    val c = AppTheme.colors
    val error = status is DockStatus.Error
    AnimatedVisibility(
        visible,
        Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth(),
        enter = slideInVertically(tween(150)) { it } + fadeIn(tween(150)),
        exit = slideOutVertically(tween(150)) { it } + fadeOut(tween(150)),
    ) {
        Row(
            Modifier
                .shadow(if (c.dark) 0.dp else 8.dp, RectangleShape)
                .background(if (error) c.error.bg else c.card)
                .drawBehind {
                    drawRect(c.lineCard, size = Size(size.width, 1.dp.toPx()))
                    if (status is DockStatus.Warn) drawRect(c.warn.label, size = Size(4.dp.toPx(), size.height))
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                is DockStatus.Ok -> DockRows(status.rows, Modifier.weight(1f))
                is DockStatus.Warn -> Column(Modifier.weight(1f)) {
                    Text("확인 필요 ${status.count}건", color = c.warn.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    DockRows(status.rows, Modifier)
                }
                is DockStatus.Error -> {
                    Icon(AppIcons.Stop, null, Modifier.size(20.dp), tint = c.error.label)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "계산 멈춤 — ${status.text}",
                        Modifier.weight(1f),
                        color = c.error.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            JumpButton(error, onJump)
        }
    }
}

@Composable
private fun DockRows(rows: List<DockRow>, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier) {
        shown(rows).forEach { r ->
            Row {
                Text(r.name, Modifier.alignByBaseline(), color = r.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                // 좁은 폰 · 큰 글꼴에서는 줄여서 한 줄에(숫자가 잘리지 않게)
                BasicText(
                    "X ${r.x}  Z ${r.z}",
                    Modifier.weight(1f).alignByBaseline(),
                    style = AppType.numLarge.copy(color = c.valueComp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(11.sp, 18.sp, 0.5.sp),
                )
            }
        }
        if (rows.size > 2) Text("외 ${rows.size - 2}점", color = c.textMuted, fontSize = 12.sp)
    }
}

/** 결과로(⌄ 결과) · 멈춤이면 고칠 칸으로(⌃ 칸 보기). 48dp 이상 */
@Composable
private fun JumpButton(error: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val fg = if (error) c.error.value else c.textBody
    Column(
        Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (error) c.card else c.subtle)
            .border(1.dp, if (error) c.error.border else c.lineCard, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.ChevronDown, null, Modifier.size(20.dp).rotate(if (error) 180f else 0f), tint = fg)
        Text(if (error) "칸 보기" else "결과", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
