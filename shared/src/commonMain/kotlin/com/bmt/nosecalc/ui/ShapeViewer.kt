package com.bmt.nosecalc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** 형상 뷰어에 그릴 윤곽 하나 */
@Immutable
data class ViewerLine(val contour: Contour, val color: Color, val width: Dp = 2.dp, val dashed: Boolean = false)

/** 형상 뷰어의 점(T 점·보정점 등). 라벨은 점 오른쪽 위(앞 라벨과 겹치면 다른 모서리로) */
@Immutable
data class ViewerMark(val at: Pt, val color: Color, val label: String? = null)

/** 직접 정한 보기: 맞춤의 몇 배(zoom)이고 어느 점(center, mm)이 화면 가운데에 오는가 */
@Immutable
data class UserView(val zoom: Float, val center: Pt) {
    /** 확대는 맞춤의 0.5~200배, 가운데는 그림 상자 b 에서 가로·세로 반 폭 밖까지 — 그림을 잃어버리지 않게 */
    fun clampedTo(b: Rect) = UserView(
        zoom.coerceIn(0.5f, 200f),
        Pt(
            center.z.coerceIn(b.left - b.width / 2.0, b.right + b.width / 2.0),
            center.x.coerceIn(b.top - b.height / 2.0, b.bottom + b.height / 2.0),
        ),
    )
}

/** 그림 상자가 b 로 바뀔 때: b 가 보이던 곳(visible, mm)에 걸치면 보기를 두고(상자 안으로 당겨), 아니면 null = 맞춤 */
internal fun keptView(u: UserView, visible: Rect, b: Rect?): UserView? = if (b != null && b.overlaps(visible)) u.clampedTo(b) else null

/**
 * 형상 뷰어의 보기. 카드의 −/+/맞춤 단추가 같이 쓰도록 밖에 둔다. userView = null 이면 맞춤.
 * 확대가 맞춤의 배수라 뷰어 크기가 바뀌어도(폰 회전 · PC 창) 같은 곳이 같은 비율로 보이고,
 * 치수를 고쳐 그림이 바뀌어도 보이던 곳에 새 그림이 걸치면 보기를 그대로 둔다
 */
@Stable
class ViewerState(view: UserView? = null) {
    var userView by mutableStateOf(view)
        private set

    /** 직접 정한 보기인가(맞춤 단추가 켜진 모양이 된다) */
    val isCustom: Boolean get() = userView != null

    /** 잠깐 뜨는 안내(한 손가락으로 끌었을 때 · Ctrl 없이 휠을 굴렸을 때) */
    internal var hint by mutableStateOf<ViewerHint?>(null)
        private set

    /** 지금 그리는 그림 상자(Z 가로 · X 세로). 단추 확대의 가운데와 옮길 한계가 된다 */
    private var bounds: Rect? = null

    /** 화면 가운데를 두고 f 배 */
    fun zoomBy(f: Float) {
        val b = bounds ?: return
        val u = userView ?: UserView(1f, Pt(b.center.x.toDouble(), b.center.y.toDouble()))
        userView = u.copy(zoom = u.zoom * f).clampedTo(b)
    }

    /** 다시 맞춤 */
    fun fit() {
        userView = null
    }

    /** 맞춤 곳(area)에 지금 보기를 놓은 변환 */
    internal fun xf(area: Rect) = ViewXf.of(userView, bounds, area)

    /** 손으로 옮긴 변환 to 를 맞춤의 배수 · 가운데 점으로 되돌려 둔다 */
    internal fun move(to: ViewXf, area: Rect) {
        val b = bounds ?: return
        userView = UserView(to.s / ViewXf.fit(b, area).s, to.toWorld(area.center)).clampedTo(b)
    }

    /** 그림 상자가 바뀌면 [keptView] 로 보기를 두거나 맞춤으로. size = 뷰어 크기(보이던 곳을 셈) */
    internal fun follow(b: Rect?, area: Rect, size: IntSize) {
        if (b == bounds) return
        val old = bounds
        bounds = b
        val u = userView ?: return
        userView = if (old == null) b?.let(u::clampedTo) else keptView(u, ViewXf.of(u, old, area).visible(size), b)
    }

    internal fun showHint(mouse: Boolean) {
        hint = ViewerHint(mouse)
    }

    internal fun clearHint(h: ViewerHint) {
        if (hint === h) hint = null
    }
}

/** 안내 한 번. 같은 안내라도 다시 띄우면 새로 1.5초 */
internal class ViewerHint(val mouse: Boolean)

@Composable
fun rememberViewerState(): ViewerState = remember { ViewerState() }

/**
 * calc 의 윤곽을 그리는 2D 뷰어. 그림 약속은 calc 와 같다 — Z 오른쪽 +, X(반경) 위 +.
 * 처음엔 모두 보이게 맞추고, 두 손가락 확대·이동 · PC 는 Ctrl + 휠 확대와 끌어 이동, 두 번 누르면 다시 맞춤.
 * 한 손가락 끌기와 Ctrl 없는 휠은 페이지 스크롤로 넘기고 안내만 띄운다(oneFingerPan 이면 한 손가락 · 휠도 뷰어가 쓴다).
 * 격자 간격은 1·2·5 단위로 확대에 따라 바뀌고 왼쪽 아래에 적는다. 중심선(X0)·Z0 은 보일 때만 긋는다.
 */
@Composable
fun ShapeViewer(
    lines: List<ViewerLine>,
    modifier: Modifier = Modifier,
    marks: List<ViewerMark> = emptyList(),
    state: ViewerState = rememberViewerState(),
    oneFingerPan: Boolean = false,
) {
    val c = AppTheme.colors
    val bounds = remember(lines, marks) { boundsOf(lines, marks) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    SideEffect { state.follow(bounds, density.fitArea(viewSize.width.toFloat(), viewSize.height.toFloat()), viewSize) }
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = AppTheme.sans, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.textMuted)
    val markStyle = label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(c.viewport)
                .onSizeChanged { viewSize = it }
                .pointerInput(state, oneFingerPan) { viewGestures(state, oneFingerPan) }
                .pointerInput(state) { detectTapGestures(onDoubleTap = { state.fit() }) },
        ) {
            val v = state.xf(fitArea(size.width, size.height))
            val step = grid(v, c.viewportGrid)
            axes(v, c.viewportAxis)
            for (l in lines) {
                drawPath(
                    pathOf(l.contour, v),
                    l.color,
                    style = Stroke(
                        l.width.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())) else null,
                    ),
                )
            }
            // 라벨은 점 오른쪽 위. 앞서 둔 라벨과 겹치면(T1 과 진입처럼 가까운 점) 오른쪽 아래 · 왼쪽 위 · 왼쪽 아래 순으로 옮긴다
            val placed = mutableListOf<Rect>()
            for (m in marks) {
                val p = v.toScreen(m.at)
                drawCircle(c.viewport, 4.dp.toPx(), p)
                drawCircle(m.color, 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                val text = m.label ?: continue
                val t = measurer.measure(text, markStyle.copy(color = m.color))
                val w = t.size.width.toFloat()
                val gap = 7.dp.toPx()
                val up = -19.dp.toPx()
                val down = 3.dp.toPx()
                val spots = listOf(Offset(gap, up), Offset(gap, down), Offset(-gap - w, up), Offset(-gap - w, down)).map { p + it }
                val at = spots.firstOrNull { o -> placed.none { it.overlaps(Rect(o, t.size.toSize())) } } ?: spots[0]
                placed += Rect(at, t.size.toSize())
                drawText(t, topLeft = at)
            }
            drawText(measurer, "격자 ${stepText(step)} mm", Offset(8.dp.toPx(), size.height - 20.dp.toPx()), label)
        }
        state.hint?.let { HintPill(state, it, Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) }
    }
}

/**
 * 확대·이동은 두 손가락 · 마우스 끌기 · (oneFingerPan 이면) 한 손가락일 때만 하고, 그때만 이벤트를 먹는다.
 * 한 손가락이 터치 슬롭을 넘으면 페이지 스크롤에 맡기고(손을 뗄 때까지) 안내를 띄운다. 휠은 Ctrl 을 누를 때만 확대
 */
private suspend fun PointerInputScope.viewGestures(state: ViewerState, oneFingerPan: Boolean) = awaitEachGesture {
    val area = fitArea(size.width.toFloat(), size.height.toFloat())
    val first = awaitPointerEvent()
    val ch = first.changes.first()
    if (first.type == PointerEventType.Scroll) {
        if (oneFingerPan || first.keyboardModifiers.isCtrlPressed) {
            state.move(state.xf(area).zoom(1.15f.pow(-ch.scrollDelta.y), ch.position), area)
            ch.consume()
        } else {
            state.showHint(mouse = true)
        }
        return@awaitEachGesture
    }
    // 두 번 누름(detectTapGestures)이 누름을 먼저 먹으므로 먹힌 것도 본다
    if (!ch.changedToDownIgnoreConsumed()) return@awaitEachGesture
    val mouse = ch.type == PointerType.Mouse
    val slop = viewConfiguration.touchSlop
    var pan = Offset.Zero
    var zoom = 1f
    var active = false
    while (true) {
        val e = awaitPointerEvent()
        val pressed = e.changes.count { it.pressed }
        if (pressed == 0) break
        val panChange = e.calculatePan()
        pan += panChange
        if (pressed < 2 && !mouse && !oneFingerPan && !active) {
            if (pan.getDistance() > slop) {
                state.showHint(mouse = false)
                break
            }
            continue
        }
        val zoomChange = e.calculateZoom()
        zoom *= zoomChange
        if (!active) active = pan.getDistance() > slop || abs(1 - zoom) * e.calculateCentroidSize(useCurrent = false) > slop
        if (active) state.move(state.xf(area).zoom(zoomChange, e.calculateCentroid()).pan(panChange), area)
        // 두 손가락은 슬롭 전에도 먹는다 — 페이지 스크롤이 먼저 가져가지 않게. 마우스는 슬롭 뒤에만(두 번 누름이 살게)
        if (active || pressed >= 2) e.changes.forEach { if (it.positionChanged()) it.consume() }
    }
}

/** 뷰어 위 가운데에 잠깐 뜨는 안내 알약. 1.5초 뒤 0.2초에 걸쳐 사라진다(프레임 시계라 스크린샷에서도 같다) */
@Composable
private fun HintPill(state: ViewerState, hint: ViewerHint, modifier: Modifier) {
    val c = AppTheme.colors
    val alpha = remember(hint) { Animatable(1f) }
    LaunchedEffect(hint) {
        alpha.animateTo(0f, tween(200, delayMillis = 1500))
        state.clearHint(hint)
    }
    val shape = RoundedCornerShape(50)
    Text(
        if (hint.mouse) "Ctrl + 휠로 확대 · 끌어서 이동" else "두 손가락으로 확대·이동",
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(shape)
            .background(c.card.copy(alpha = .92f))
            .border(1.dp, c.lineCard, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = c.textStrong,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** 맞춤에 쓰는 곳: 뷰어에서 왼쪽 36 · 위 36 · 오른쪽 52 · 아래 40dp 를 뺀다(가장자리 라벨 · 격자 글 자리) */
private fun Density.fitArea(w: Float, h: Float) = Rect(36.dp.toPx(), 36.dp.toPx(), w - 52.dp.toPx(), h - 40.dp.toPx())

/** 화면 좌표 = t + (z·s, −x·s). s 는 mm 당 px */
internal data class ViewXf(val s: Float, val t: Offset) {
    fun toScreen(p: Pt) = Offset(t.x + p.z.toFloat() * s, t.y - p.x.toFloat() * s)

    fun toWorld(o: Offset) = Pt(((o.x - t.x) / s).toDouble(), ((t.y - o.y) / s).toDouble())

    fun zoom(f: Float, focus: Offset) = ViewXf(s * f, focus - (focus - t) * f)

    fun pan(d: Offset) = copy(t = t + d)

    /** size 크기 뷰어에 보이는 곳(mm): left/right = Z, top/bottom = X */
    fun visible(size: IntSize): Rect {
        val a = toWorld(Offset.Zero)
        val b = toWorld(Offset(size.width.toFloat(), size.height.toFloat()))
        return Rect(a.z.toFloat(), b.x.toFloat(), b.z.toFloat(), a.x.toFloat())
    }

    companion object {
        /** 그림 상자 b 를 area(여백을 뺀 곳) 가운데에 꽉 차게 */
        fun fit(b: Rect?, area: Rect): ViewXf {
            if (b == null) return ViewXf(10f, area.center)
            // Rect 의 left/right = Z, top/bottom = X(반경)
            val s = min(max(area.width, 1f) / max(b.width, 1e-3f), max(area.height, 1f) / max(b.height, 1e-3f))
            return ViewXf(s, area.center - Offset(b.center.x * s, -b.center.y * s))
        }

        /** 맞춤 위에 직접 정한 보기 u 를 얹는다: 맞춤의 u.zoom 배, u.center 가 area 가운데 */
        fun of(u: UserView?, b: Rect?, area: Rect): ViewXf {
            val f = fit(b, area)
            if (u == null) return f
            val s = f.s * u.zoom
            return ViewXf(s, area.center - Offset(u.center.z.toFloat() * s, -u.center.x.toFloat() * s))
        }
    }
}

/** 모든 윤곽·점을 품는 상자(Z 가 가로, X 가 세로). 그릴 것이 없으면 null */
internal fun boundsOf(lines: List<ViewerLine>, marks: List<ViewerMark>): Rect? {
    val pts = lines.flatMap { l -> l.contour.segs.flatMap { seg -> if (seg is Arc) arcPoints(seg, 16) else listOf(seg.start, seg.end) } } +
        marks.map { it.at }
    if (pts.isEmpty()) return null
    return Rect(
        pts.minOf { it.z }.toFloat(), pts.minOf { it.x }.toFloat(),
        pts.maxOf { it.z }.toFloat(), pts.maxOf { it.x }.toFloat(),
    )
}

private fun arcPoints(a: Arc, n: Int): List<Pt> {
    val a0 = atan2(a.start.x - a.center.x, a.start.z - a.center.z)
    val sweep = a.sweepDeg * PI / 180 * (if (a.cw) -1 else 1)
    return (0..n).map { i ->
        val t = a0 + sweep * i / n
        Pt(a.center.z + a.radius * cos(t), a.center.x + a.radius * sin(t))
    }
}

private fun pathOf(c: Contour, v: ViewXf): Path = Path().apply {
    val first = c.segs.firstOrNull() ?: return@apply
    v.toScreen(first.start).let { moveTo(it.x, it.y) }
    for (seg in c.segs) {
        when (seg) {
            is Line -> v.toScreen(seg.end).let { lineTo(it.x, it.y) }
            is Arc -> {
                if (seg.sweepDeg == 0.0) {
                    v.toScreen(seg.end).let { lineTo(it.x, it.y) }
                    continue
                }
                // 화면은 y 가 아래로 커져 각도 부호가 뒤집힌다: 시작각 = −(Z 축에서 잰 각), G02(시계) = 양의 쓸기
                val a0 = atan2(seg.start.x - seg.center.x, seg.start.z - seg.center.z) * 180 / PI
                val cs = v.toScreen(seg.center)
                val r = seg.radius.toFloat() * v.s
                arcTo(Rect(cs, r), (-a0).toFloat(), (if (seg.cw) seg.sweepDeg else -seg.sweepDeg).toFloat(), false)
            }
        }
    }
}

/** 격자를 긋고 간격(mm)을 돌려준다 */
private fun DrawScope.grid(v: ViewXf, color: Color): Double {
    val raw = 30.dp.toPx() / v.s
    val p = 10.0.pow(floor(log10(raw.toDouble())))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * p }.first { it >= raw }
    val z0 = -v.t.x / v.s
    val z1 = (size.width - v.t.x) / v.s
    val x0 = (v.t.y - size.height) / v.s
    val x1 = v.t.y / v.s
    var z = floor(z0 / step) * step
    while (z <= z1) {
        val sx = v.t.x + z.toFloat() * v.s
        drawLine(color, Offset(sx, 0f), Offset(sx, size.height), 1f)
        z += step
    }
    var x = floor(x0 / step) * step
    while (x <= ceil(x1 / step) * step) {
        val sy = v.t.y - x.toFloat() * v.s
        drawLine(color, Offset(0f, sy), Offset(size.width, sy), 1f)
        x += step
    }
    return step
}

/** 중심선(X0, 1점 쇄선)과 Z0(점선) */
private fun DrawScope.axes(v: ViewXf, color: Color) {
    val w = 1.2.dp.toPx()
    val y = v.t.y
    if (y in 0f..size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 4.dp.toPx(), 3.dp.toPx(), 4.dp.toPx())))
    }
    val x = v.t.x
    if (x in 0f..size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    }
}

private fun stepText(step: Double): String {
    val r = (step * 1000).toLong()
    return if (r % 1000 == 0L) "${r / 1000}" else (step.toString().trimEnd('0'))
}
