package com.bmt.nosecalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

/**
 * calc 의 윤곽을 그리는 2D 뷰어. 그림 약속은 calc 와 같다 — Z 오른쪽 +, X(반경) 위 +.
 * 처음엔 모두 보이게 맞추고, 두 손가락 확대·끌어 옮기기·PC 마우스 휠 확대, 두 번 누르면 다시 맞춤.
 * 격자 간격은 1·2·5 단위로 확대에 따라 바뀌고 왼쪽 아래에 적는다. 중심선(X0)·Z0 은 보일 때만 긋는다.
 */
@Composable
fun ShapeViewer(lines: List<ViewerLine>, modifier: Modifier = Modifier, marks: List<ViewerMark> = emptyList()) {
    val c = AppTheme.colors
    val bounds = remember(lines, marks) { boundsOf(lines, marks) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // null = 자동 맞춤. 손대면 그때부터 직접 정한 보기
    var view by remember(bounds) { mutableStateOf<ViewXf?>(null) }
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = AppTheme.sans, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.textMuted)
    val markStyle = label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    fun now() = view ?: ViewXf.fit(bounds, size)
    Canvas(
        modifier
            .clipToBounds()
            .background(c.viewport)
            .onSizeChanged { size = it }
            .pointerInput(bounds) {
                detectTransformGestures { centroid, pan, zoom, _ -> view = now().zoom(zoom, centroid).pan(pan) }
            }
            .pointerInput(bounds) { detectTapGestures(onDoubleTap = { view = null }) }
            .pointerInput(bounds) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type != PointerEventType.Scroll) continue
                        val ch = e.changes.first()
                        view = now().zoom(1.15f.pow(-ch.scrollDelta.y), ch.position)
                        e.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val v = now()
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
}

/** 화면 좌표 = t + (z·s, −x·s). s 는 mm 당 px */
internal data class ViewXf(val s: Float, val t: Offset) {
    fun toScreen(p: Pt) = Offset(t.x + p.z.toFloat() * s, t.y - p.x.toFloat() * s)

    fun zoom(f: Float, focus: Offset): ViewXf {
        val k = (s * f).coerceIn(1e-3f, 1e5f) / s
        return ViewXf(s * k, focus - (focus - t) * k)
    }

    fun pan(d: Offset) = copy(t = t + d)

    companion object {
        fun fit(b: Rect?, size: IntSize): ViewXf {
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            if (b == null) return ViewXf(10f, Offset(w / 2, h / 2))
            // Rect 의 left/right = Z, top/bottom = X(반경)
            val s = min(w * 0.84f / max(b.width, 1e-3f), h * 0.84f / max(b.height, 1e-3f))
            return ViewXf(s, Offset(w / 2 - b.center.x * s, h / 2 + b.center.y * s))
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
