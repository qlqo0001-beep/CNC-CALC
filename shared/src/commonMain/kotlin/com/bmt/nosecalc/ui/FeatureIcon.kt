package com.bmt.nosecalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 기능 아이콘. 원본은 애플식 컬러 이모지(01.jpg)라 폰마다 다르게 보여서, 같은 뜻의 그림을 기능색 1~2색으로 직접 그린다.
 * 막대그래프(재료) · 구(라운드) · 모니터(시뮬레이터) · 삼각자(직각삼각형) · 톱니(극좌표) · 볼트(나사) · 테이퍼 윤곽(테이퍼)
 */
@Composable
fun FeatureIcon(feature: Feature, modifier: Modifier = Modifier, color: Color = AppTheme.colors.features.getValue(feature).menu) {
    val neutral = AppTheme.colors.icon
    Canvas(modifier) {
        val u = size.minDimension / 24f
        when (feature) {
            Feature.TAPER -> taper(u, color)
            Feature.CUTTING -> bars(u, color, neutral)
            Feature.ROUND -> sphere(u, color)
            Feature.SIMULATOR -> monitor(u, color, neutral)
            Feature.TRIANGLE -> setSquare(u, color)
            Feature.POLAR -> gear(u, color)
            Feature.THREAD -> bolt(u, color)
        }
    }
}

private fun o(u: Float, x: Float, y: Float) = Offset(x * u, y * u)

private fun DrawScope.taper(u: Float, c: Color) {
    val p = Path().apply {
        moveTo(2.5f * u, 7f * u); lineTo(9.5f * u, 7f * u); lineTo(15f * u, 16f * u); lineTo(21.5f * u, 16f * u)
    }
    drawPath(p, c, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    for (pt in listOf(o(u, 9.5f, 7f), o(u, 15f, 16f))) {
        drawCircle(lerp(c, Color.White, 0.75f), 2.2f * u, pt)
        drawCircle(c, 2.2f * u, pt, style = Stroke(1.5f * u))
    }
}

private fun DrawScope.bars(u: Float, c: Color, neutral: Color) {
    val axis = Path().apply { moveTo(3f * u, 3f * u); lineTo(3f * u, 21f * u); lineTo(21.5f * u, 21f * u) }
    drawPath(axis, neutral.copy(alpha = 0.5f), style = Stroke(1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val r = CornerRadius(0.9f * u)
    drawRoundRect(c, o(u, 5.5f, 11.5f), Size(3.8f * u, 8.3f * u), r)
    drawRoundRect(c.copy(alpha = 0.5f), o(u, 10.7f, 14.5f), Size(3.8f * u, 5.3f * u), r)
    drawRoundRect(c, o(u, 15.9f, 5f), Size(3.8f * u, 14.8f * u), r)
}

private fun DrawScope.sphere(u: Float, c: Color) {
    val center = o(u, 12f, 12f)
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(c, Color.White, 0.6f), c, lerp(c, Color.Black, 0.35f)),
            center = o(u, 8.5f, 8f), radius = 15f * u,
        ),
        9.5f * u, center,
    )
    drawCircle(Color.White.copy(alpha = 0.55f), 1.8f * u, o(u, 8.6f, 8.2f))
}

private fun DrawScope.monitor(u: Float, c: Color, neutral: Color) {
    val screen = Rect(2.5f * u, 3.5f * u, 21.5f * u, 16.5f * u)
    drawRoundRect(
        Brush.verticalGradient(listOf(c.copy(alpha = 0.12f), c.copy(alpha = 0.35f)), screen.top, screen.bottom),
        screen.topLeft, screen.size, CornerRadius(1.6f * u),
    )
    drawRoundRect(neutral, screen.topLeft, screen.size, CornerRadius(1.6f * u), style = Stroke(1.6f * u))
    val path = Path().apply { moveTo(5.5f * u, 13f * u); lineTo(9f * u, 13f * u); lineTo(12.5f * u, 8f * u); lineTo(18.5f * u, 8f * u) }
    drawPath(path, c, style = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawRect(neutral, o(u, 10.9f, 16.5f), Size(2.2f * u, 3f * u))
    drawRoundRect(neutral, o(u, 6.5f, 19.2f), Size(11f * u, 1.9f * u), CornerRadius(0.95f * u))
}

private fun DrawScope.setSquare(u: Float, c: Color) {
    val outer = Path().apply { moveTo(3.5f * u, 3f * u); lineTo(3.5f * u, 21f * u); lineTo(21.5f * u, 21f * u); close() }
    drawPath(outer, c.copy(alpha = 0.16f))
    drawPath(outer, c, style = Stroke(1.6f * u, join = StrokeJoin.Round))
    val inner = Path().apply { moveTo(7f * u, 10.5f * u); lineTo(7f * u, 17.5f * u); lineTo(14f * u, 17.5f * u); close() }
    drawPath(inner, c, style = Stroke(1.3f * u, join = StrokeJoin.Round))
    // 빗변의 눈금(안쪽으로)
    val k = 1 / sqrt(2f)
    for (i in 1..6) {
        val t = 0.14f + i * 0.11f
        val p = Offset((3.5f + 18f * t) * u, (3f + 18f * t) * u)
        val len = (if (i % 2 == 0) 2.4f else 1.5f) * u
        drawLine(c, p, p + Offset(-k * len, k * len), 1.1f * u)
    }
}

private fun DrawScope.gear(u: Float, c: Color) {
    val center = o(u, 12f, 12f)
    val gear = Path().apply {
        fillType = PathFillType.EvenOdd
        val rOut = 10.6f * u
        val rIn = 8.1f * u
        val step = PI / 4
        val pts = mutableListOf<Offset>()
        for (i in 0 until 8) {
            val a = i * step
            fun at(da: Double, r: Float) = center + Offset((r * cos(a + da)).toFloat(), (r * sin(a + da)).toFloat())
            pts += at(-step / 2, rIn)
            pts += at(-0.24, rIn)
            pts += at(-0.15, rOut)
            pts += at(0.15, rOut)
            pts += at(0.24, rIn)
        }
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
        addOval(Rect(center, 3.3f * u))
    }
    drawPath(gear, c)
    drawCircle(Color.White.copy(alpha = 0.45f), 5.5f * u, center, style = Stroke(1.2f * u))
}

private fun DrawScope.bolt(u: Float, c: Color) {
    rotate(45f, o(u, 12f, 12f)) {
        drawRect(c.copy(alpha = 0.6f), o(u, 6f, 10.2f), Size(16.5f * u, 3.6f * u))
        var x = 7.5f
        while (x < 22f) {
            drawLine(c, o(u, x, 10.2f), o(u, x + 0.9f, 13.8f), 0.8f * u)
            x += 1.5f
        }
        drawRoundRect(c, o(u, 1.5f, 7.6f), Size(5f * u, 8.8f * u), CornerRadius(0.8f * u))
        drawRoundRect(c, o(u, 12.2f, 7.8f), Size(4.4f * u, 8.4f * u), CornerRadius(0.7f * u))
        drawLine(Color.White.copy(alpha = 0.45f), o(u, 1.5f, 12f), o(u, 6.5f, 12f), 0.8f * u)
        drawLine(Color.White.copy(alpha = 0.45f), o(u, 12.2f, 12f), o(u, 16.6f, 12f), 0.8f * u)
    }
}
