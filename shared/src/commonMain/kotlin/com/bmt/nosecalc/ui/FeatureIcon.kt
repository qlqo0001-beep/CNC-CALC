package com.bmt.nosecalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 기능 아이콘. 원본은 애플식 컬러 이모지(01.jpg)라 폰마다 다르게 보여서, 같은 뜻의 그림을 한 가지 선 스타일로 직접 그린다.
 * 24칸 격자 · 선 2칸 · 둥근 끝, 선 = 기능색, 면 = 기능색 옅게(라이트 .14 · 다크 .24). 그라데이션 · 기울임 없음, 받침 같은 보조 선만 중립색.
 * 테이퍼 윤곽(테이퍼) · 속도 게이지(재료) · 축 위 돔과 R 화살표(라운드) · 모니터(시뮬레이터) · 직각삼각형 · 육각형과 반지름(극좌표) · 가로 볼트(나사)
 */
@Composable
fun FeatureIcon(feature: Feature, modifier: Modifier = Modifier, color: Color = AppTheme.colors.features.getValue(feature).menu) {
    val c = AppTheme.colors
    val tint = color.copy(alpha = if (c.dark) .24f else .14f)
    Canvas(modifier) {
        val u = size.minDimension / 24f
        when (feature) {
            Feature.TAPER -> taper(u, color, tint)
            Feature.CUTTING -> gauge(u, color, tint)
            Feature.ROUND -> dome(u, color, tint)
            Feature.SIMULATOR -> monitor(u, color, tint, c.icon)
            Feature.TRIANGLE -> rightTriangle(u, color, tint)
            Feature.POLAR -> hexagon(u, color, tint)
            Feature.THREAD -> bolt(u, color, tint)
        }
    }
}

private fun o(u: Float, x: Float, y: Float) = Offset(x * u, y * u)

/** 각도(°, 화면 좌표라 음수가 위쪽)와 길이로 가리키는 점 */
private fun Offset.toward(deg: Float, r: Float): Offset {
    val a = deg * PI / 180
    return this + Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
}

/** 꺾은선(x, y 번갈아). close 면 닫는다 */
private fun poly(u: Float, vararg xy: Float, close: Boolean = false) = Path().apply {
    moveTo(xy[0] * u, xy[1] * u)
    for (i in 2 until xy.size step 2) lineTo(xy[i] * u, xy[i + 1] * u)
    if (close) close()
}

private fun stroke(u: Float, w: Float = 2f) = Stroke(w * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** 원호(중심 · 반지름 칸) — 면이면 부채꼴 */
private fun DrawScope.arc(color: Color, center: Offset, r: Float, start: Float, sweep: Float, style: Stroke? = null) {
    val tl = center - Offset(r, r)
    if (style == null) drawArc(color, start, sweep, true, tl, Size(2 * r, 2 * r))
    else drawArc(color, start, sweep, false, tl, Size(2 * r, 2 * r), style = style)
}

/** 선반 축(1점 쇄선, 기능색 반투명) */
private fun DrawScope.axis(u: Float, c: Color) = drawLine(
    c.copy(alpha = 0.5f), o(u, 2.5f, 21f), o(u, 21.5f, 21f), u,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 1.5f * u, 1f * u, 1.5f * u)),
)

/** 테이퍼: 윤곽 아래 소재 면 + 축 1점 쇄선 + 꺾이는 두 점 */
private fun DrawScope.taper(u: Float, c: Color, tint: Color) {
    drawPath(poly(u, 2.5f, 7f, 9.5f, 7f, 15f, 16f, 21.5f, 16f, 21.5f, 20f, 2.5f, 20f, close = true), tint)
    axis(u, c)
    drawPath(poly(u, 2.5f, 7f, 9.5f, 7f, 15f, 16f, 21.5f, 16f), c, style = stroke(u))
    for (p in listOf(o(u, 9.5f, 7f), o(u, 15f, 16f))) drawCircle(c, 1.8f * u, p)
}

/** 재료·절삭조건: 속도 게이지(반원 · 눈금 5 · 바늘) */
private fun DrawScope.gauge(u: Float, c: Color, tint: Color) {
    val ctr = o(u, 12f, 17f)
    arc(tint, ctr, 9f * u, 180f, 180f)
    arc(c, ctr, 9f * u, 180f, 180f, stroke(u))
    for (a in listOf(180f, 225f, 270f, 315f, 360f)) {
        drawLine(c, ctr.toward(a, 6.3f * u), ctr.toward(a, 7.8f * u), 1.4f * u, StrokeCap.Round)
    }
    drawLine(c, o(u, 3f, 17f), o(u, 21f, 17f), 1.4f * u, StrokeCap.Round)
    drawLine(c, ctr, ctr.toward(-55f, 7f * u), 2.2f * u, StrokeCap.Round)
    drawCircle(c, 1.8f * u, ctr)
}

/**
 * 라운드: 축 위 소재 끝의 돔(반 단면이라 1/4 원호) + 중심에서 호까지 R 화살표.
 * 받침 위 반원 돔은 24dp 에서 재료의 속도 게이지와 구별이 안 돼 테이퍼와 같은 윤곽 · 축 그림으로 그린다
 */
private fun DrawScope.dome(u: Float, c: Color, tint: Color) {
    val ctr = o(u, 10f, 20f)
    val r = 11.5f * u
    fun profile() = Path().apply {
        moveTo(2.5f * u, 8.5f * u)
        lineTo(10f * u, 8.5f * u)
        arcTo(Rect(ctr, r), -90f, 90f, false)
    }
    drawPath(profile().apply { lineTo(2.5f * u, 20f * u); close() }, tint)
    axis(u, c)
    drawPath(profile(), c, style = stroke(u))
    val tip = ctr.toward(-45f, r)
    drawLine(c, ctr, ctr.toward(-45f, r - 2.4f * u), 1.4f * u)
    arrowHead(tip, tip - ctr, c, 2.4f * u)
    drawCircle(c, 1.1f * u, ctr)
}

/** 시뮬레이터: 모니터(테·받침은 중립색) 안의 공구 경로와 끝점 */
private fun DrawScope.monitor(u: Float, c: Color, tint: Color, neutral: Color) {
    val screen = o(u, 2.5f, 3.5f)
    val box = Size(19f * u, 13f * u)
    drawRoundRect(tint, screen, box, CornerRadius(1.6f * u))
    drawRoundRect(neutral, screen, box, CornerRadius(1.6f * u), style = stroke(u))
    drawPath(poly(u, 5.5f, 13f, 9f, 13f, 12.5f, 8f, 18.5f, 8f), c, style = stroke(u))
    drawCircle(c, 1.6f * u, o(u, 18.5f, 8f))
    drawLine(neutral, o(u, 12f, 17.5f), o(u, 12f, 20.5f), 2f * u)
    drawLine(neutral, o(u, 7.5f, 20.5f), o(u, 16.5f, 20.5f), 2f * u, StrokeCap.Round)
}

/** 직각삼각형: 직각 표시 네모 + 밑변 끝의 각도 호 */
private fun DrawScope.rightTriangle(u: Float, c: Color, tint: Color) {
    val tri = poly(u, 3.5f, 20.5f, 20.5f, 20.5f, 3.5f, 4.5f, close = true)
    drawPath(tri, tint)
    drawPath(tri, c, style = stroke(u))
    drawPath(poly(u, 3.5f, 17.3f, 6.7f, 17.3f, 6.7f, 20.5f), c, style = stroke(u, 1.3f))
    arc(c, o(u, 20.5f, 20.5f), 5.5f * u, 180f, 43f, stroke(u, 1.3f))
}

/** 턴밀 극좌표: 육각형(다각가공) + 기준 반직선(점선) · 반지름 · 각도 호 — 톱니(설정)로 읽히지 않게 */
private fun DrawScope.hexagon(u: Float, c: Color, tint: Color) {
    val ctr = o(u, 12f, 12f)
    val hex = Path().apply {
        for (k in 0 until 6) {
            val p = ctr.toward(-90f + 60f * k, 9.5f * u)
            if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(hex, tint)
    drawPath(hex, c, style = stroke(u))
    drawLine(c, ctr, o(u, 21f, 12f), u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * u, 1.5f * u)))
    drawLine(c, ctr, ctr.toward(-30f, 9.5f * u), 1.5f * u, StrokeCap.Round)
    arc(c, ctr, 4.5f * u, -30f, 30f, stroke(u, 1.2f))
    drawCircle(c, 1.4f * u, ctr)
}

/** 나사: 선반 축을 따라 누운 볼트 — 머리 + 위아래 나사산(1.5칸마다 산 · 골) + 오른쪽 끝 45° 모따기 */
private fun DrawScope.bolt(u: Float, c: Color, tint: Color) {
    val shank = poly(
        u,
        7.5f, 10f, 8.5f, 10f, 10f, 8f, 11.5f, 10f, 13f, 8f, 14.5f, 10f, 16f, 8f, 17.5f, 10f, 19f, 8f,
        21f, 10f, 21f, 14f,
        19f, 16f, 17.5f, 14f, 16f, 16f, 14.5f, 14f, 13f, 16f, 11.5f, 14f, 10f, 16f, 8.5f, 14f, 7.5f, 14f,
        close = true,
    )
    drawPath(shank, tint)
    drawPath(shank, c, style = stroke(u, 1.6f))
    drawRoundRect(c, o(u, 2.5f, 6.5f), Size(5f * u, 11f * u), CornerRadius(u))
}
