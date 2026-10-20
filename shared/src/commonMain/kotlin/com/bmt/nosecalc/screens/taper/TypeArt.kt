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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.DrawSpec
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * TYPE 그림(02·11). 소재 면(빗금)·회전축·꺾은선·T 점·기준 점선·각도 호·이송 화살(T1 → T2)·바이트.
 * 개념도라 경사는 그림마다 정한 기울기 그대로다.
 * 외경을 기준으로 그리고, 내경은 위아래(재료가 위), 역방향(mirrored)은 좌우를 뒤집는다 — 바이트도 반대쪽으로 간다.
 * 글자는 뒤집지 않는다. 자리는 카드 비율, 선 굵기·글 크기는 02 의 카드 폭(199dp) 기준 배율을 묶어 쓴다(artScale) —
 * 좁은 폰에서 글이 너무 작아지거나 PC 넓은 창에서 너무 커지지 않는다.
 */
@Composable
internal fun TypeArt(kind: TaperKind, type: Int, mirrored: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Canvas(modifier) {
        val (ks, kt) = artScale(size.width, 199.dp)
        val pen = Pen(this, c, measurer, sans, ks, kt, kind.inner, mirrored)
        if (kind.groove) pen.groove(type) else pen.slope(art(kind, type), kind.double)
    }
}

/**
 * 꺾은선(카드 비율: x = 폭, y = 높이, 왼쪽부터), T1·T2(·T3) 의 점 번호, 라벨 가운데 자리(점에서, 02 카드 폭 비율 — 글 배율을 따른다),
 * 기준 점선 길이(폭 비율, 02 는 TYPE 2 만 짧다)
 */
private class Art(val pts: List<Offset>, val t: List<Int>, val labels: List<Offset>, val ref: Float = .33f)

private fun o(x: Float, y: Float) = Offset(x, y)

/** 외경·정방향 그림. 홑테이퍼 1~5 는 02 의 점 자리를 잰 값. 라벨은 이송 화살 · 각도 글과 떨어지게 02 에서 조금 옮겼다 */
private fun art(kind: TaperKind, type: Int): Art = if (!kind.double) {
    when (type) {
        1 -> Art(listOf(o(.185f, .265f), o(.449f, .265f), o(.683f, .517f), o(.683f, .80f)), listOf(2, 1), listOf(o(.081f, .012f), o(0f, -.069f)))
        // 끝 다리는 회전축(.94)에 닿지 않게 .86 까지
        2 -> Art(listOf(o(.38f, .08f), o(.38f, .365f), o(.616f, .616f), o(.616f, .86f)), listOf(2, 1), listOf(o(.081f, -.02f), o(.081f, -.045f)), .255f)
        3 -> Art(listOf(o(.11f, .39f), o(.374f, .39f), o(.608f, .636f), o(.872f, .636f)), listOf(2, 1), listOf(o(.083f, -.069f), o(0f, -.069f)))
        4 -> Art(listOf(o(.21f, .175f), o(.21f, .477f), o(.48f, .765f), o(.76f, .765f)), listOf(2, 1), listOf(o(.081f, -.069f), o(.083f, -.045f)))
        else -> Art(listOf(o(.11f, .64f), o(.37f, .64f), o(.60f, .39f), o(.86f, .39f)), listOf(2, 1), listOf(o(.081f, -.069f), o(-.10f, -.06f)))
    }
} else {
    when (type) {
        1 -> Art(
            listOf(o(.08f, .28f), o(.27f, .28f), o(.43f, .43f), o(.58f, .68f), o(.84f, .68f)), listOf(3, 2, 1),
            listOf(o(.08f, -.069f), o(.05f, -.075f), o(0f, -.069f)), .22f,
        )
        2 -> Art(
            listOf(o(.06f, .66f), o(.22f, .66f), o(.44f, .36f), o(.66f, .66f), o(.88f, .66f)), listOf(3, 2, 1),
            listOf(o(.07f, -.069f), o(0f, -.072f), o(-.07f, -.069f)), .22f,
        )
        3 -> Art(
            listOf(o(.06f, .36f), o(.22f, .36f), o(.44f, .66f), o(.66f, .36f), o(.88f, .36f)), listOf(3, 2, 1),
            listOf(o(.04f, -.069f), o(0f, .075f), o(-.07f, -.069f)), .22f,
        )
        // 형상 4: 아래 두 경사를 T1 쪽으로 줄여 T3 가 칩과 떨어지게 하고(각도는 그대로), T2 글은 왼쪽 점선 위가 아닌
        // 아래(이송 화살 밑)에, T3 글은 각2 글과 떨어지게 다리 위 왼쪽에 둔다
        else -> Art(
            listOf(o(.05f, .70f), o(.28f, .70f), o(.47f, .55f), o(.60f, .36f), o(.84f, .36f)), listOf(3, 2, 1),
            listOf(o(.08f, -.069f), o(.04f, .08f), o(-.13f, -.06f)), .22f,
        )
    }
}

/** T 점 · 라벨 색(입력 줄 점 이름 표와 같다): T1 주황, T2 청록, T3 남색 */
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
    /** 02 카드 폭 199dp 기준 배율(artScale): ks 선 · 점 · 화살촉, kt 글 */
    val ks: Float,
    val kt: Float,
    val flipY: Boolean,
    val flipX: Boolean,
) {
    val w = d.size.width
    val h = d.size.height

    /** 라벨 거리 단위: 02 카드 폭을 글 배율로 — 라벨은 카드 크기가 아니라 글 크기를 따라 점 곁에 붙는다 */
    val unit = with(d) { 199.dp.toPx() } * kt

    fun dp(v: Float) = with(d) { (v * ks).dp.toPx() }

    val profile = Stroke(dp(DrawSpec.profile.value), join = StrokeJoin.Miter)

    /** 카드 비율 → 화면 점 */
    fun at(p: Offset) = Offset((if (flipX) 1 - p.x else p.x) * w, (if (flipY) 1 - p.y else p.y) * h)

    /** 점에서 라벨까지(02 카드 폭 비율) → 화면 거리 */
    fun shift(v: Offset) = Offset(if (flipX) -v.x else v.x, if (flipY) -v.y else v.y) * unit

    fun slope(a: Art, double: Boolean) {
        val pts = a.pts.map(::at)
        val t = a.t.map { pts[it] }
        val left = Offset(if (flipX) 1f else -1f, 0f)
        val refLen = a.ref * w
        val arcR = maxOf((if (double) .10f else .135f) * w, dp(22f))
        // 맨 밑에 소재 면(빗금)과 회전축(1점 쇄선, 외경·내경 모두 아래). 기준 점선도 꺾은선 밑에 깐다
        d.hatchFill(stock(a.pts), c.drawStock, c.drawHatch, ks)
        d.centerLine(Offset(0f, .94f * h), Offset(w, .94f * h), c.drawRef, ks)
        for (i in 0 until t.size - 1) {
            d.drawLine(
                c.drawRef, t[i], t[i] + left * refLen, dp(1.1f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp(4f), dp(3f))),
            )
        }
        d.drawPath(
            Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            },
            c.drawProfile,
            style = profile,
        )
        for (i in 0 until t.size - 1) {
            val label = if (double) "각${i + 1}" else "각도"
            angle(t[i], left, t[i + 1] - t[i], arcR, label, if (double) 11f else 11.5f)
        }
        feed(t[0], t[1], left)
        // 점: 바탕색 테 + 점 이름 표와 같은 색으로 채운 원
        t.forEachIndexed { i, p ->
            d.drawCircle(c.subtle, dp(5.6f), p)
            d.drawCircle(pointColor(c, i), dp(4f), p)
        }
        t.forEachIndexed { i, p ->
            text("T${i + 1}", p + shift(a.labels[i]), pointColor(c, i), 15f, FontWeight.SemiBold)
        }
        if (flipY) boringBar(barTop(a)) else outerTool()
    }

    /**
     * 소재 면. 외경 카드 비율로 만들고 at() 으로 뒤집는다(내경은 재료가 위, 역방향은 좌우).
     * 꺾은선 두 끝을 끝 선분 방향으로 늘인다 — 가로면 카드 밖까지, 아래(축 쪽)로 가면 닫는 줄, 위로 가면 여는 줄까지.
     * 닫는 줄: 외경은 회전축(.94), 내경은 뒤집혀 카드 위 끝. 여는 줄: 외경은 카드 위 끝, 내경은 뒤집혀 회전축(막힌 구멍 바닥).
     * 마지막 끝 → 오른쪽 닫는 모서리 → 왼쪽 닫는 모서리(→ 첫 끝이 위로 갔으면 왼쪽 여는 모서리)로 닫는다
     */
    private fun stock(p: List<Offset>): Path {
        val close = if (flipY) 1.02f else .94f
        val open = if (flipY) .06f else -.02f
        fun end(e: Offset, prev: Offset) = when {
            e.y == prev.y -> o(if (e.x < prev.x) -.02f else 1.02f, e.y)
            e.y > prev.y -> o(e.x, close)
            else -> o(e.x, open)
        }
        val first = end(p.first(), p[1])
        val last = end(p.last(), p[p.size - 2])
        return poly(
            buildList {
                add(first)
                addAll(p)
                add(last)
                if (last.y != close) add(o(1.02f, close))
                add(o(-.02f, close))
                if (first.y == open) add(o(-.02f, open))
            },
        )
    }

    /**
     * 이송 화살(T1 → T2): 선분 가운데 절반을 9dp 띄워 나란히, T2 끝에 채운 촉. 띄우는 쪽은 각도 호가 없는 쪽 —
     * 보통 공기 쪽이고, 호가 공기 쪽에 서는 그림(역테이퍼 · 2중 골 · 2중 형상 4)은 소재 쪽
     */
    private fun feed(a: Offset, b: Offset, left: Offset) {
        val u = (b - a) / (b - a).getDistance()
        var n = Offset(-u.y, u.x)
        if (n.x * left.x > 0) n = -n
        val off = n * dp(9f)
        val head = dp(5f)
        val tip = a + (b - a) * .75f + off
        d.drawLine(c.textMuted, a + (b - a) * .25f + off, tip - u * head, dp(1.6f), cap = StrokeCap.Round)
        d.arrowHead(tip, u, c.textMuted, head)
    }

    /** 기준선 방향 ref 에서 경사 방향 s 까지의 호와 각도 글(치수라 파랑). 호가 화살촉 3.5개 길이보다 짧으면 촉 없이 */
    private fun angle(p: Offset, ref: Offset, s: Offset, r: Float, label: String, size: Float) {
        val a0 = atan2(ref.y, ref.x)
        var sweep = atan2(s.y, s.x) - a0
        while (sweep > PI) sweep -= (2 * PI).toFloat()
        while (sweep < -PI) sweep += (2 * PI).toFloat()
        val head = dp(4.5f)
        val heads = r * abs(sweep) >= 3.5f * head
        val dir = if (sweep > 0) 1f else -1f
        // 촉이 있으면 호를 촉 안쪽까지만 긋는다(굵은 획이 촉 끝을 넘지 않게)
        val inset = if (heads) head / r * 0.8f * dir else 0f
        d.drawArc(
            c.drawAngle,
            ((a0 + inset) * 180 / PI).toFloat(),
            ((sweep - 2 * inset) * 180 / PI).toFloat(),
            useCenter = false,
            topLeft = p - Offset(r, r),
            size = Size(2 * r, 2 * r),
            style = Stroke(dp(1.3f)),
        )
        if (heads) {
            // 원 위 각 e 의 점에 끝이 오는 촉 — 호를 따라 바깥(각이 작아지는 쪽 · 커지는 쪽)을 가리킨다
            for ((e, sign) in listOf(a0 to -dir, a0 + sweep to dir)) {
                d.arrowHead(p + Offset(cos(e), sin(e)) * r, Offset(-sin(e), cos(e)) * sign, c.drawAngle, head)
            }
        }
        // 글은 호의 가운데 방향, 호 바깥
        val mid = a0 + sweep / 2
        text(label, p + Offset(cos(mid), sin(mid)) * (r + .075f * unit), c.drawAngle, size, FontWeight.Bold)
    }

    /** 바탕색 테(halo)를 두른 글. 크기는 sp × kt 이고 10.5sp 아래로는 줄이지 않는다 */
    private fun text(s: String, center: Offset, color: Color, size: Float, weight: FontWeight) {
        val style = TextStyle(fontFamily = sans, fontSize = (size * kt).coerceAtLeast(10.5f).sp, fontWeight = weight, color = color)
        d.haloText(measurer, AnnotatedString(s), style, c.subtle, ks, center = center)
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
            style = profile,
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
        text("W", at(o((lw + rw) / 2, y)) + Offset(0f, .06f * unit), c.drawAngle, 11f, FontWeight.Bold)
        // 첫 단: 날 오른쪽이 오른쪽 벽에 닿은 홈 바이트
        val bl = .54f
        solid(poly(listOf(o(bl, .03f), o(rw, .03f), o(rw, .50f), o(bl, .50f))), c.drawToolBody)
        solid(poly(listOf(o(bl, .50f), o(rw, .50f), o(rw, .585f), o(bl, .585f))), c.drawInsert)
        val ref = at(o(if (type == 1) bl else rw, .585f))
        d.drawCircle(c.subtle, dp(3.2f), ref)
        d.drawCircle(c.drawRef, dp(3.2f), ref, style = Stroke(dp(1.4f)))
        val dx = if (type == 1) -.095f else .095f
        text("기준", ref + Offset(dx * unit, 0f), c.drawT1, 12f, FontWeight.Bold)
    }
}
