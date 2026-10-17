package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * TYPE 그림(02·11). 꺾은선·T 점·빨간 기준 점선·각도 호·바이트. 개념도라 경사는 그림마다 정한 기울기 그대로다.
 * 외경을 기준으로 그리고, 내경은 위아래(재료가 위), 역방향(mirrored)은 좌우를 뒤집는다 — 바이트도 반대쪽으로 간다.
 * 글자는 뒤집지 않는다. 크기는 02 의 카드 폭(199dp)에 맞춘 비율이라 작은 칸에서도 같은 모양으로 줄어든다.
 */
@Composable
internal fun TypeArt(kind: TaperKind, type: Int, mirrored: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Canvas(modifier) {
        val pen = Pen(this, c, measurer, sans, kind.inner, mirrored)
        if (kind.groove) pen.groove(type) else pen.slope(art(kind, type), kind.double)
    }
}

/**
 * 꺾은선(카드 비율: x = 폭, y = 높이, 왼쪽부터), T1·T2(·T3) 의 점 번호, 라벨 가운데 자리(점에서, 폭 비율),
 * 기준 점선 길이(폭 비율, 02 는 TYPE 2 만 짧다)
 */
private class Art(val pts: List<Offset>, val t: List<Int>, val labels: List<Offset>, val ref: Float = .33f)

private fun o(x: Float, y: Float) = Offset(x, y)

/** 외경·정방향 그림. 홑테이퍼 1~5 는 02 의 점 자리를 잰 값 */
private fun art(kind: TaperKind, type: Int): Art = if (!kind.double) {
    when (type) {
        1 -> Art(listOf(o(.185f, .265f), o(.449f, .265f), o(.683f, .517f), o(.683f, .80f)), listOf(2, 1), listOf(o(.081f, .012f), o(0f, -.069f)))
        2 -> Art(listOf(o(.38f, .08f), o(.38f, .365f), o(.616f, .616f), o(.616f, .90f)), listOf(2, 1), listOf(o(.081f, -.02f), o(.081f, -.02f)), .255f)
        3 -> Art(listOf(o(.11f, .39f), o(.374f, .39f), o(.608f, .636f), o(.872f, .636f)), listOf(2, 1), listOf(o(.083f, -.069f), o(0f, -.069f)))
        4 -> Art(listOf(o(.21f, .175f), o(.21f, .477f), o(.48f, .765f), o(.76f, .765f)), listOf(2, 1), listOf(o(.081f, -.069f), o(.083f, -.02f)))
        else -> Art(listOf(o(.11f, .64f), o(.37f, .64f), o(.60f, .39f), o(.86f, .39f)), listOf(2, 1), listOf(o(.081f, -.069f), o(-.085f, -.069f)))
    }
} else {
    when (type) {
        1 -> Art(
            listOf(o(.08f, .28f), o(.27f, .28f), o(.43f, .43f), o(.58f, .68f), o(.84f, .68f)), listOf(3, 2, 1),
            listOf(o(.08f, -.069f), o(.075f, -.03f), o(0f, -.069f)), .22f,
        )
        2 -> Art(
            listOf(o(.06f, .66f), o(.22f, .66f), o(.44f, .36f), o(.66f, .66f), o(.88f, .66f)), listOf(3, 2, 1),
            listOf(o(.07f, -.069f), o(0f, -.072f), o(-.07f, -.069f)), .22f,
        )
        3 -> Art(
            listOf(o(.06f, .36f), o(.22f, .36f), o(.44f, .66f), o(.66f, .36f), o(.88f, .36f)), listOf(3, 2, 1),
            listOf(o(.07f, -.069f), o(0f, .075f), o(-.07f, -.069f)), .22f,
        )
        else -> Art(
            listOf(o(.05f, .78f), o(.20f, .78f), o(.44f, .60f), o(.60f, .36f), o(.84f, .36f)), listOf(3, 2, 1),
            listOf(o(.08f, -.069f), o(-.075f, -.04f), o(-.075f, -.06f)), .22f,
        )
    }
}

/** T 점 라벨 색: T1 주황, T2 청록, T3 남색 */
internal fun pointColor(c: AppColors, i: Int): Color = when (i) {
    0 -> c.drawT1
    1 -> c.drawT2
    else -> c.indigo
}

private class Pen(
    val d: DrawScope,
    val c: AppColors,
    val measurer: TextMeasurer,
    val sans: FontFamily,
    val flipY: Boolean,
    val flipX: Boolean,
) {
    val w = d.size.width
    val h = d.size.height
    /** 02 카드 폭 199dp 에 대한 배율 */
    val k = w / with(d) { 199.dp.toPx() }

    fun dp(v: Float) = with(d) { (v * k).dp.toPx() }

    /** 카드 비율 → 화면 점 */
    fun at(p: Offset) = Offset((if (flipX) 1 - p.x else p.x) * w, (if (flipY) 1 - p.y else p.y) * h)

    /** 점에서 라벨까지(폭 비율) → 화면 거리 */
    fun shift(v: Offset) = Offset((if (flipX) -v.x else v.x) * w, (if (flipY) -v.y else v.y) * w)

    fun slope(a: Art, double: Boolean) {
        val pts = a.pts.map(::at)
        val t = a.t.map { pts[it] }
        val left = Offset(if (flipX) 1f else -1f, 0f)
        val refLen = a.ref * w
        val arcR = (if (double) .10f else .135f) * w
        // 기준 점선은 꺾은선 밑에 깐다
        for (i in 0 until t.size - 1) {
            d.drawLine(
                c.drawRef, t[i], t[i] + left * refLen, dp(1.2f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp(3.4f), dp(2.3f))),
            )
        }
        d.drawPath(
            Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            },
            c.drawProfile,
            style = Stroke(dp(1.9f), join = StrokeJoin.Miter),
        )
        for (i in 0 until t.size - 1) {
            val label = if (double) "각${i + 1}" else "각도"
            angle(t[i], left, t[i + 1] - t[i], arcR, label, if (double) 9.5f else 10.5f)
        }
        t.forEach { p ->
            d.drawCircle(c.subtle, dp(3.2f), p)
            d.drawCircle(c.drawRef, dp(3.2f), p, style = Stroke(dp(1.2f)))
        }
        t.forEachIndexed { i, p ->
            text("T${i + 1}", p + shift(a.labels[i]), pointColor(c, i), 14.5f, FontWeight.Medium)
        }
        if (flipY) boringBar(barTop(a)) else outerTool()
    }

    /** 기준선 방향 ref 에서 경사 방향 s 까지의 호(양쪽 화살촉)와 각도 글 */
    private fun angle(p: Offset, ref: Offset, s: Offset, r: Float, label: String, size: Float) {
        val a0 = atan2(ref.y, ref.x)
        var sweep = atan2(s.y, s.x) - a0
        while (sweep > PI) sweep -= (2 * PI).toFloat()
        while (sweep < -PI) sweep += (2 * PI).toFloat()
        val head = dp(4.2f)
        val inset = head / r * 0.8f * if (sweep > 0) 1 else -1
        d.drawArc(
            c.drawRef,
            ((a0 + inset) * 180 / PI).toFloat(),
            ((sweep - 2 * inset) * 180 / PI).toFloat(),
            useCenter = false,
            topLeft = p - Offset(r, r),
            size = Size(2 * r, 2 * r),
            style = Stroke(dp(1.2f)),
        )
        val dir = if (sweep > 0) 1f else -1f
        arrowHead(p, r, a0, -dir, head)
        arrowHead(p, r, a0 + sweep, dir, head)
        // 글은 호의 가운데 방향, 호 바깥
        val mid = a0 + sweep / 2
        text(label, p + Offset(cos(mid), sin(mid)) * (r + .075f * w), c.drawAngle, size, FontWeight.Bold)
    }

    /** 원 위 각 a 의 점에 끝이 오는 화살촉. dir = 각이 커지는 쪽(+1)·작아지는 쪽(−1)을 가리킴 */
    private fun arrowHead(p: Offset, r: Float, a: Float, dir: Float, len: Float) {
        val tip = p + Offset(cos(a), sin(a)) * r
        val tangent = Offset(-sin(a), cos(a)) * dir
        val normal = Offset(cos(a), sin(a))
        val base = tip - tangent * len
        d.drawPath(
            Path().apply {
                moveTo(tip.x, tip.y)
                (base + normal * (len * 0.45f)).let { lineTo(it.x, it.y) }
                (base - normal * (len * 0.45f)).let { lineTo(it.x, it.y) }
                close()
            },
            c.drawRef,
        )
    }

    private fun text(s: String, center: Offset, color: Color, size: Float, weight: FontWeight) {
        val style = TextStyle(fontFamily = sans, fontSize = (size * k).sp, fontWeight = weight, color = color)
        val layout = measurer.measure(s, style)
        val topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
        // 흰 테두리(halo) + 옅은 그림자(02): 바탕색 획을 먼저 긋는다
        val shadow = Shadow(Color.Black.copy(alpha = if (c.dark) 0.5f else 0.22f), Offset(0f, dp(1f)), dp(3f))
        d.drawText(measurer, s, topLeft, style.copy(color = c.subtle, drawStyle = Stroke(dp(3.2f), join = StrokeJoin.Round), shadow = shadow))
        d.drawText(layout, topLeft = topLeft)
    }

    private fun poly(p: List<Offset>) = Path().apply {
        val q = p.map(::at)
        moveTo(q[0].x, q[0].y)
        q.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }

    private fun solid(path: Path, fill: Color) {
        d.drawPath(path, fill)
        d.drawPath(path, c.drawToolLine, style = Stroke(dp(0.9f), join = StrokeJoin.Round))
    }

    /** 외경 바이트(02): 오른쪽 위에 세로로 선 자루, 왼쪽 아래(역방향은 오른쪽 아래) 끝에 인서트 */
    private fun outerTool() {
        val shank = listOf(o(.851f, .054f), o(.915f, .054f), o(.915f, .245f), o(.879f, .298f), o(.851f, .272f))
        d.drawPath(poly(shank).apply { translate(Offset(dp(1.5f), dp(2f))) }, Color.Black.copy(alpha = if (c.dark) 0.25f else 0.07f))
        solid(poly(shank), c.drawToolBody)
        solid(poly(listOf(o(.839f, .272f), o(.876f, .266f), o(.879f, .298f), o(.842f, .303f))), c.drawInsert)
        d.drawCircle(c.drawToolLine, dp(1.3f), at(o(.859f, .285f)))
    }

    /** 내경 보링바(11): 오른쪽에서 왼쪽을 향해 누운 자루(카드 밖으로 잘림), 왼쪽 위 끝에 인서트. 꺾은선 아래(구멍 안)에 둔다 */
    private fun boringBar(top: Float) {
        // 자리는 11 의 화면 비율(아래로 +)로 적었다. at() 이 내경 그림을 위아래로 뒤집으므로 미리 뒤집어 둔다
        fun q(x: Float, y: Float) = o(x, 1 - (y - .672f + top))
        solid(poly(listOf(q(.80f, .672f), q(1.02f, .672f), q(1.02f, .756f), q(.85f, .756f), q(.80f, .712f))), c.drawToolBody)
        solid(poly(listOf(q(.799f, .662f), q(.829f, .670f), q(.832f, .712f), q(.801f, .708f))), c.drawInsert)
        d.drawCircle(c.drawToolLine, dp(1.3f), at(q(.815f, .688f)))
    }

    /** 보링바 윗변: 오른쪽(x ≥ 0.7) 꺾은선보다 아래, 0.62~0.70 */
    private fun barTop(a: Art): Float {
        val low = a.pts.filter { it.x >= .7f }.maxOfOrNull { 1 - it.y } ?: 0f
        return (low + .06f).coerceIn(.62f, .70f)
    }

    /** 홈: 외경 → 홈(벽·바닥) → 외경, 오른쪽 벽에서 들어가는 홈 바이트, 기준 인선 점, 폭 W */
    fun groove(type: Int) {
        val od = .36f
        val bottom = .70f
        val lw = .30f
        val rw = .70f
        d.drawPath(
            Path().apply {
                listOf(o(.05f, od), o(lw, od), o(lw, bottom), o(rw, bottom), o(rw, od), o(.95f, od)).map(::at).forEachIndexed { i, p ->
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            },
            c.drawProfile,
            style = Stroke(dp(1.9f), join = StrokeJoin.Miter),
        )
        // 홈 폭 W: 바닥 아래 치수선
        val y = .80f
        val a = at(o(lw, y))
        val b = at(o(rw, y))
        d.drawLine(c.drawRef, at(o(lw, bottom + .03f)), at(o(lw, y + .03f)), dp(0.9f))
        d.drawLine(c.drawRef, at(o(rw, bottom + .03f)), at(o(rw, y + .03f)), dp(0.9f))
        d.drawLine(c.drawRef, a, b, dp(1.1f))
        listOf(a to 1f, b to -1f).forEach { (p, s) ->
            val len = dp(4.2f)
            d.drawPath(
                Path().apply {
                    moveTo(p.x, p.y)
                    lineTo(p.x + s * len, p.y - len * 0.45f)
                    lineTo(p.x + s * len, p.y + len * 0.45f)
                    close()
                },
                c.drawRef,
            )
        }
        text("W", at(o((lw + rw) / 2, y + .065f)), c.drawAngle, 11f, FontWeight.Bold)
        // 첫 단: 날 오른쪽이 오른쪽 벽에 닿은 홈 바이트
        val bl = .54f
        solid(poly(listOf(o(bl, .03f), o(rw, .03f), o(rw, .50f), o(bl, .50f))), c.drawToolBody)
        solid(poly(listOf(o(bl, .50f), o(rw, .50f), o(rw, .585f), o(bl, .585f))), c.drawInsert)
        val ref = at(o(if (type == 1) bl else rw, .585f))
        d.drawCircle(c.subtle, dp(3.2f), ref)
        d.drawCircle(c.drawRef, dp(3.2f), ref, style = Stroke(dp(1.4f)))
        val dx = if (type == 1) -.095f else .095f
        text("기준", ref + Offset(dx * w, 0f), c.drawT1, 12f, FontWeight.Bold)
    }
}
