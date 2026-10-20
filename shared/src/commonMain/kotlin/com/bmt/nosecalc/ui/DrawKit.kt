package com.bmt.nosecalc.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 그림 한 벌의 선 규칙(ISO 128 꼴): 굵은 윤곽 대 가는 치수선 약 2:1, 채운 화살촉, 흐림 없는 테두리(halo) 글,
 * 1점 쇄선 중심선, 소재는 면 + 45° 빗금. 값은 배율 1 의 dp — 그림마다 artScale 의 ks(선)를 곱해 쓴다.
 */
internal object DrawSpec {
    val profile = 2.4.dp
    val dim = 1.0.dp
    val ext = 0.8.dp
    val leader = 0.8.dp

    /** 보조선: 점에서 띄우는 틈 · 치수선을 넘는 길이 */
    val extGap = 2.5.dp
    val extOver = 3.dp
    val arrowLen = 6.dp

    /** 화살촉 반폭 = 길이 × 0.3 (약 17°) */
    const val arrowHalf = 0.3f
    val halo = 3.dp
    val hatchSpacing = 6.dp
    val hatchStroke = 0.75.dp
}

/**
 * 그림 폭 w(px)가 기준 폭 base 의 몇 배인지 → (ks 선 배율 0.9~1.3, kt 글 배율 0.95~1.25).
 * 좁은 폰에서 글이 너무 작아지거나 PC 넓은 창에서 너무 커지지 않게 묶는다. 자리(기하)는 그림 폭 비율 그대로 쓴다
 */
internal fun Density.artScale(w: Float, base: Dp): Pair<Float, Float> {
    val k = w / base.toPx()
    return k.coerceIn(.9f, 1.3f) to k.coerceIn(.95f, 1.25f)
}

/** 중심선(1점 쇄선): 긴 9 · 틈 2.5 · 점 2 · 틈 2.5dp, 굵기 1dp (× ks) */
internal fun DrawScope.centerLine(a: Offset, b: Offset, color: Color, ks: Float) {
    val dash = floatArrayOf(9f, 2.5f, 2f, 2.5f).map { it.dp.toPx() * ks }.toFloatArray()
    drawLine(color, a, b, 1.dp.toPx() * ks, pathEffect = PathEffect.dashPathEffect(dash))
}

/** 끝이 tip 인 채운 화살촉. dir = 화살이 가리키는 쪽(길이는 상관없음), len = 촉 길이(px) */
internal fun DrawScope.arrowHead(tip: Offset, dir: Offset, color: Color, len: Float) {
    val d = dir.getDistance()
    if (d < 1e-3f) return
    val u = dir / d
    val n = Offset(-u.y, u.x) * (len * DrawSpec.arrowHalf)
    val base = tip - u * len
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + n.x, base.y + n.y)
            lineTo(base.x - n.x, base.y - n.y)
            close()
        },
        color,
    )
}

/**
 * 바탕색 테(halo, 둥근 이음 3dp × ks)를 먼저 긋고 글을 쓴다 — 선 위에 놓여도 또렷하다. 흐린 그림자는 쓰지 않는다.
 * 자리는 center 나 topLeft 하나로 준다. pill = 글 뒤에 halo 색 알약(양옆 4dp · 모서리 4dp)을 깐다(초점 표시). 그린 칸을 돌려준다
 */
internal fun DrawScope.haloText(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    halo: Color,
    ks: Float,
    center: Offset? = null,
    topLeft: Offset? = null,
    pill: Boolean = false,
): Rect {
    val layout = measurer.measure(text, style)
    val size = layout.size.toSize()
    val tl = topLeft ?: requireNotNull(center) { "center 나 topLeft 가 필요하다" }.let { it - Offset(size.width / 2, size.height / 2) }
    if (pill) {
        val bg = 4.dp.toPx()
        drawRoundRect(halo, tl - Offset(bg, 0f), Size(size.width + 2 * bg, size.height), CornerRadius(bg))
    }
    // 글자마다 색을 따로 준 부분("L2(B)" 의 B)도 테는 바탕색. 테 색 · 획은 그릴 때 넘긴다 —
    // drawText(measurer, …, style) 는 같은 글이면 먼저 잰 배치를 색째 다시 써서 테가 글자색으로 그려졌다
    val plain = AnnotatedString(text.text, text.spanStyles.map { AnnotatedString.Range(it.item.copy(color = Color.Unspecified), it.start, it.end) })
    drawText(measurer.measure(plain, style), halo, tl, drawStyle = Stroke(DrawSpec.halo.toPx() * ks, join = StrokeJoin.Round))
    drawText(layout, topLeft = tl)
    return Rect(tl, size)
}

/**
 * 치수 하나: a·b 에서 offset(px)만큼 띄운 치수선 + 보조선(점에서 틈을 두고, 치수선을 조금 넘게) + 양 끝 채운 화살 + 이름.
 * offset 의 + 쪽은 a→b 를 화면에서 시계 방향으로 90° 돌린 쪽(왼→오른 가로 치수면 아래, 아래→위 세로 치수면 오른쪽).
 * 이름은 치수선 가운데를 끊고 바로 놓는다(글은 늘 가로). 치수선이 이름 + 화살 둘 + 4dp 보다 짧으면 화살을 보조선 바깥에서
 * 안으로 겨누고(꼬리 9dp) 이름은 오른쪽 끝(세로 치수면 위쪽 끝) 너머에 둔다 — 그림 칸을 벗어나면 두 보조선 사이에(들어가면),
 * 아니면 칸 안(가장자리 2dp)으로 밀고, 밀려서 끝 화살을 덮으면 치수선의 + 쪽 옆으로 비킨다.
 * 이름은 늘 halo 를 두른다(pill 이면 halo 색 알약도 — haloText). 이름 칸을 돌려준다
 */
internal fun DrawScope.linearDim(
    a: Offset,
    b: Offset,
    offset: Float,
    label: AnnotatedString,
    color: Color,
    ks: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    halo: Color,
    pill: Boolean = false,
): Rect {
    val span = (b - a).getDistance()
    if (span < 1e-3f) return Rect.Zero
    val u = (b - a) / span
    val n = Offset(-u.y, u.x)
    val dimW = DrawSpec.dim.toPx() * ks
    val extW = DrawSpec.ext.toPx() * ks
    val arrow = DrawSpec.arrowLen.toPx() * ks
    val pad = 2.dp.toPx() * ks
    // 보조선(치수선이 점에 바로 붙으면 없음)
    val gap = DrawSpec.extGap.toPx() * ks
    if (abs(offset) > gap) {
        val s = if (offset > 0) 1f else -1f
        val over = DrawSpec.extOver.toPx() * ks
        for (p in listOf(a, b)) drawLine(color, p + n * (s * gap), p + n * (offset + s * over), extW)
    }
    val pa = a + n * offset
    val pb = b + n * offset
    val text = measurer.measure(label, style).size.toSize()
    // 치수선 방향으로 이름이 차지하는 길이
    val along = abs(u.x) * text.width + abs(u.y) * text.height
    val center = if (span >= along + 2 * arrow + 2 * pad) {
        val mid = (pa + pb) / 2f
        val half = along / 2 + pad
        drawLine(color, pa, mid - u * half, dimW)
        drawLine(color, mid + u * half, pb, dimW)
        arrowHead(pa, pa - pb, color, arrow)
        arrowHead(pb, pb - pa, color, arrow)
        mid
    } else {
        val tail = arrow + 9.dp.toPx() * ks
        val out = -u
        arrowHead(pa, u, color, arrow)
        arrowHead(pb, -u, color, arrow)
        // 이름은 오른쪽(세로에 가까우면 위쪽) 끝 너머. 그림 칸을 벗어나면 두 보조선 사이(들어가면 — 치수선은 바깥 꼬리만),
        // 아니면 칸 안으로 밀고, 밀려서 끝 화살을 덮으면 치수선 옆(+ 쪽)으로
        val bEnd = if (abs(u.x) >= abs(u.y)) u.x > 0 else u.y < 0
        val end = if (bEnd) pb else pa
        val dir = if (bEnd) u else out
        val bg = if (pill) 4.dp.toPx() else 0f
        val m = 2.dp.toPx() + bg
        fun inside(p: Offset) = Offset(
            p.x.coerceAtMost(size.width - m - text.width / 2).coerceAtLeast(m + text.width / 2),
            p.y.coerceAtMost(size.height - m - text.height / 2).coerceAtLeast(m + text.height / 2),
        )
        val far = end + dir * (tail + pad + along / 2)
        val p = inside(far)
        if (p != far && span >= along + 2 * (pad + bg)) {
            drawLine(color, pa + out * tail, pa, dimW)
            drawLine(color, pb, pb - out * tail, dimW)
            (pa + pb) / 2f
        } else {
            drawLine(color, pa + out * tail, pb - out * tail, dimW)
            // 끝에서 이름(알약) 가장자리까지
            val clear = (p.x - end.x) * dir.x + (p.y - end.y) * dir.y - along / 2 - bg
            if (clear >= arrow + pad) p else inside(p + n * (abs(n.x) * text.width / 2 + abs(n.y) * text.height / 2 + pad))
        }
    }
    return haloText(measurer, label, style, halo, ks, center = center, pill = pill)
}

/** 소재 단면: 면을 칠하고 그 안에만 45° 빗금(선 사이 6dp · 굵기 0.75dp, × ks) */
internal fun DrawScope.hatchFill(path: Path, fill: Color, hatch: Color, ks: Float) {
    drawPath(path, fill)
    val r = path.getBounds()
    // 가로로 잰 간격 = 수직 간격 × √2
    val step = DrawSpec.hatchSpacing.toPx() * ks * sqrt(2f)
    val w = DrawSpec.hatchStroke.toPx() * ks
    clipPath(path) {
        var x = r.left - r.height
        while (x < r.right) {
            drawLine(hatch, Offset(x, r.bottom), Offset(x + r.height, r.top), w)
            x += step
        }
    }
}
