package com.bmt.nosecalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Radius
import com.bmt.nosecalc.ui.theme.Space
import kotlin.math.max

/** 범례 그림: 실선 · 점선 */
enum class LegendGlyph { Line, Dashed }

/** 범례 한 칸 */
@Immutable
data class LegendItem(val color: Color, val label: String, val glyph: LegendGlyph = LegendGlyph.Line)

/**
 * 형상 확인 카드: 머리(제목 + 부제) · [ShapeViewer] · 단추 줄(− + 맞춤) · 범례.
 * 그림 높이는 그림 상자 비율을 따른다 — 폭 × 세로/가로(0.45~1.1) + 76dp 를 220~400dp(넓은 PC 창은 520dp)로.
 * 납작한 테이퍼는 낮은 상자, 높은 형상은 높은 상자가 되어 빈 곳이 줄어든다
 */
@Composable
fun ShapeViewerCard(
    lines: List<ViewerLine>,
    marks: List<ViewerMark>,
    legend: List<LegendItem>,
    modifier: Modifier = Modifier,
    title: String = "형상 확인",
    subtitle: String? = null,
    state: ViewerState = rememberViewerState(),
) {
    val bounds = remember(lines, marks) { boundsOf(lines, marks) }
    AppCard(modifier.fillMaxWidth(), padding = 0.dp) {
        CardHeader(title, subtitle, Modifier.padding(start = Space.cardPad, end = Space.cardPad, top = 14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val aspect = (bounds?.let { it.height / max(it.width, 1e-3f) } ?: 0f).coerceIn(.45f, 1.1f)
            val h = (maxWidth * aspect + 76.dp).coerceIn(220.dp, if (maxWidth > 600.dp) 520.dp else 400.dp)
            ShapeViewer(lines, Modifier.fillMaxWidth().height(h), marks, state)
        }
        ViewerToolbar(state)
        ViewerLegend(legend)
    }
}

/** 단추 줄(52dp, 위 1dp 구분선): − · + 는 화면 가운데를 두고 1.5배씩, 맞춤은 직접 정한 보기일 때 청록으로 켜진다. 오른쪽은 비워 둔다 */
@Composable
private fun ViewerToolbar(state: ViewerState) {
    val c = AppTheme.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomButton(AppIcons.Minus, "축소") { state.zoomBy(1 / 1.5f) }
        ZoomButton(AppIcons.Plus, "확대") { state.zoomBy(1.5f) }
        FitButton(state.isCustom) { state.fit() }
    }
}

/** − · + 단추: 44 × 40dp(누르는 곳 48dp), 입력칸 바탕 + 1dp 테두리 */
@Composable
private fun ZoomButton(icon: ImageVector, name: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(Radius.control)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp, 40.dp)
            .clip(shape)
            .background(c.field)
            .border(1.dp, c.lineField, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, name, Modifier.size(20.dp), tint = c.textStrong)
    }
}

/** 맞춤 알약(40dp): 직접 정한 보기면 청록 옅은 바탕 + 청록 테두리 + 진청록 글 — 눌러서 돌아갈 수 있다는 표시 */
@Composable
private fun FitButton(on: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(50)
    val fg = if (on) c.tealDeep else c.textStrong
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(shape)
            .background(if (on) c.teal.copy(alpha = .12f) else c.field)
            .border(1.dp, if (on) c.teal else c.lineField, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Fit, null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text("맞춤", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 범례: 칸이 넘치면 다음 줄로 */
@Composable
private fun ViewerLegend(items: List<LegendItem>) {
    val c = AppTheme.colors
    FlowRow(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp, 8.dp)) {
                    drawLine(
                        l.color, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx(),
                        pathEffect = if (l.glyph == LegendGlyph.Dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(l.label, color = c.textBody, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
