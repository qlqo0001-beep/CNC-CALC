package com.bmt.nosecalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Radius
import com.bmt.nosecalc.ui.theme.Space
import kotlin.math.max

/** 범례 그림: 선(실선 · 끊은 선 · 점선) · 점([MarkKind] 넷과 같은 모양) · 노즈 원 · 면 */
enum class LegendGlyph { Line, Dashed, Dotted, Ring, Dot, Square, Cross, Circle, Fill }

/** 범례 한 칸 */
@Immutable
data class LegendItem(val color: Color, val label: String, val glyph: LegendGlyph = LegendGlyph.Line)

/**
 * 형상 확인 카드: 머리(제목 + 부제 + 크게 보기) · [ShapeViewer] · 단추 줄(− + 맞춤) · footer(그림을 고르는 줄 등) · 범례.
 * 그림 높이는 그림 상자 비율을 따른다 — 폭 × 세로/가로(0.45~1.1) + 76dp 를 220~400dp(넓은 PC 창은 520dp)로.
 * 납작한 테이퍼는 낮은 상자, 높은 형상은 높은 상자가 되어 빈 곳이 줄어든다. 크게 보기도 같은 보기(state)를 쓴다
 */
@Composable
fun ShapeViewerCard(
    lines: List<ViewerLine>,
    marks: List<ViewerMark>,
    legend: List<LegendItem>,
    modifier: Modifier = Modifier,
    title: String = "형상 확인",
    subtitle: String? = null,
    stock: ViewerStock? = null,
    fills: List<ViewerFill> = emptyList(),
    state: ViewerState = rememberViewerState(),
    footer: (@Composable () -> Unit)? = null,
) {
    val bounds = remember(lines, marks) { boundsOf(lines, marks) }
    var full by remember { mutableStateOf(false) }
    AppCard(modifier.fillMaxWidth(), padding = 0.dp) {
        CardHeader(title, subtitle, Modifier.padding(start = Space.cardPad, end = 10.dp, top = 14.dp)) {
            ViewerButton(AppIcons.Expand, "크게 보기") { full = true }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val aspect = (bounds?.let { it.height / max(it.width, 1e-3f) } ?: 0f).coerceIn(.45f, 1.1f)
            val h = (maxWidth * aspect + 76.dp).coerceIn(220.dp, if (maxWidth > 600.dp) 520.dp else 400.dp)
            ShapeViewer(lines, Modifier.fillMaxWidth().height(h), marks, state, stock = stock, fills = fills)
        }
        ViewerToolbar(state)
        footer?.invoke()
        ViewerLegend(legend, Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp))
    }
    if (full) ViewerFullScreen(lines, marks, legend, title, subtitle, stock, fills, state) { full = false }
}

/**
 * 크게 보기: 화면 전체(c.page)에 머리(제목 · 부제 · 닫기) + 범례, 뷰어(한 손가락 이동 · Ctrl 없는 휠 확대), 아래 단추 줄(내비게이션 바 위).
 * 보기(state)는 카드와 같이 써서 확대가 양쪽으로 이어진다. 키: + / − 확대·축소, 0 맞춤, Esc · 뒤로 키는 닫기
 */
@Composable
internal fun ViewerFullScreen(
    lines: List<ViewerLine>,
    marks: List<ViewerMark>,
    legend: List<LegendItem>,
    title: String,
    subtitle: String?,
    stock: ViewerStock?,
    fills: List<ViewerFill>,
    state: ViewerState,
    onClose: () -> Unit,
) {
    Overlay(onClose, onKey = { e -> viewerKey(e, state) }) {
        val c = AppTheme.colors
        Column(Modifier.fillMaxSize().background(c.page).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.fillMaxWidth().background(c.card).windowInsetsPadding(WindowInsets.statusBars)) {
                CardHeader(title, subtitle, Modifier.padding(start = Space.cardPad, end = 12.dp, top = 10.dp)) {
                    PillButton("닫기", AppIcons.Close, onClose)
                }
                ViewerLegend(legend, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
            ShapeViewer(lines, Modifier.fillMaxWidth().weight(1f), marks, state, oneFingerPan = true, stock = stock, fills = fills)
            Column(Modifier.fillMaxWidth().background(c.card).windowInsetsPadding(WindowInsets.navigationBars)) { ViewerToolbar(state) }
        }
    }
}

/** 크게 보기의 PC 키: + (= · 숫자판 +) 확대, − 축소, 0 맞춤 */
private fun viewerKey(e: KeyEvent, state: ViewerState): Boolean {
    when (e.key) {
        Key.Plus, Key.Equals, Key.NumPadAdd -> state.zoomBy(1.5f)
        Key.Minus, Key.NumPadSubtract -> state.zoomBy(1 / 1.5f)
        Key.Zero, Key.NumPad0 -> state.fit()
        else -> return false
    }
    return true
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
        ViewerButton(AppIcons.Minus, "축소") { state.zoomBy(1 / 1.5f) }
        ViewerButton(AppIcons.Plus, "확대") { state.zoomBy(1.5f) }
        FitButton(state.isCustom) { state.fit() }
    }
}

/** − · + · 크게 보기 단추: 44 × 40dp(누르는 곳 48dp), 입력칸 바탕 + 1dp 테두리 */
@Composable
private fun ViewerButton(icon: ImageVector, name: String, onClick: () -> Unit) {
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

/** 범례: 18 × 12dp 그림 + 13sp 이름. 칸이 넘치면 다음 줄로, 한 칸이 줄보다 길면 이름이 줄을 바꾼다(잘리지 않게) */
@Composable
private fun ViewerLegend(items: List<LegendItem>, modifier: Modifier) {
    val c = AppTheme.colors
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp, 12.dp)) { legendGlyph(l, c.card) }
                Spacer(Modifier.width(6.dp))
                Text(l.label, color = c.textBody, fontSize = 13.sp)
            }
        }
    }
}

/** 범례 그림 하나. 점 넷은 뷰어의 [mark] 그대로, 노즈 원은 옅게 채운 원, 면은 칠한 네모 + 같은 색(불투명) 테두리 */
private fun DrawScope.legendGlyph(l: LegendItem, bg: Color) {
    val y = center.y
    val w = 2.dp.toPx()
    fun line(effect: PathEffect?, cap: StrokeCap = StrokeCap.Butt) =
        drawLine(l.color, Offset(0f, y), Offset(size.width, y), w, cap, effect)
    when (l.glyph) {
        LegendGlyph.Line -> line(null)
        LegendGlyph.Dashed -> line(PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        LegendGlyph.Dotted -> line(PathEffect.dashPathEffect(floatArrayOf(0.1.dp.toPx(), 4.dp.toPx())), StrokeCap.Round)
        LegendGlyph.Ring -> mark(MarkKind.Ring, center, l.color, bg)
        LegendGlyph.Dot -> mark(MarkKind.Dot, center, l.color, bg)
        LegendGlyph.Square -> mark(MarkKind.Square, center, l.color, bg)
        LegendGlyph.Cross -> mark(MarkKind.Cross, center, l.color, bg)
        LegendGlyph.Circle -> {
            drawCircle(l.color.copy(alpha = .10f), 5.5.dp.toPx())
            drawCircle(l.color, 5.5.dp.toPx(), style = Stroke(1.2.dp.toPx()))
        }
        LegendGlyph.Fill -> {
            val tl = Offset(1.dp.toPx(), 1.dp.toPx())
            val sz = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
            val r = CornerRadius(2.dp.toPx())
            drawRoundRect(l.color, tl, sz, r)
            drawRoundRect(l.color.copy(alpha = 1f), tl, sz, r, style = Stroke(1.dp.toPx()))
        }
    }
}
