package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.bmt.nosecalc.ui.DrawSpec
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.artFontCap
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * TYPE 그림(02·11). 소재 면(빗금)·회전축·꺾은선·T 점·기준 점선·각도 호·이송 화살(T1 → T2)·시작/끝 표·바이트.
 * 개념도라 경사는 그림마다 정한 기울기 그대로다.
 * 외경을 기준으로 그리고, 내경은 위아래(재료가 위), 역방향(mirrored)은 좌우를 뒤집는다 — 바이트도 반대쪽으로 간다
 * (내경 뒤보링 바는 인서트만 뒤집고 자루는 +Z 구멍 입구 쪽으로 — [boringBar]).
 * 글자는 뒤집지 않는다. 자리는 카드 비율, 선 굵기·글 크기는 02 의 카드 폭(199dp) 기준 배율을 묶어 쓴다(artScale) —
 * 좁은 폰에서 글이 너무 작아지거나 PC 넓은 창에서 너무 커지지 않는다.
 * compact = B 화면 요약 칸(작은 그림): 글 12sp 고정, 각도 글 · 빗금 · 시작/끝 표 · 홈 치수 없이.
 * focus = 입력 중인 칸 이름(x1 · z2 · ang · r1 · start · end, 홈은 od · bottom · width · insert · zStart) — 그 점 · 치수를 짚는다.
 */
@Composable
internal fun TypeArt(
    kind: TaperKind,
    type: Int,
    mirrored: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    compact: Boolean = false,
    focus: String? = null,
) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Canvas(modifier) {
        val (ks, kt) = artScale(size.width, 199.dp)
        val pen = Pen(this, c, measurer, sans, ks, kt, kind.inner, mirrored, compact, focus)
        if (kind.groove) pen.groove(type) else pen.slope(art(kind, type), kind.double)
    }
}

/**
 * 꺾은선(카드 비율: x = 폭, y = 높이, 왼쪽부터), T1·T2(·T3) 의 점 번호, 라벨 가운데 자리(점에서, 02 카드 폭 비율 — 글 배율을 따른다),
 * 기준 점선 길이(폭 비율, 02 는 TYPE 2 만 짧다), '시작' · '끝' 글 자리(그 끝에서, 라벨과 같은 단위. 보통은 끝 위)
 */
private class Art(
    val pts: List<Offset>,
    val t: List<Int>,
    val labels: List<Offset>,
    val ref: Float = .33f,
    val ends: List<Offset> = listOf(o(0f, -.06f), o(0f, -.06f)),
)

private fun o(x: Float, y: Float) = Offset(x, y)

/**
 * 외경·정방향 그림. 홑테이퍼 1~5 는 02 의 점 자리를 잰 값. 라벨은 이송 화살 · 각도 글과 떨어지게 02 에서 조금 옮겼다.
 * '시작' · '끝' 은 공기 쪽에 두되, 바이트 · 보링바 · 회전축에 걸리는 끝(외경 5 · 2중 3 · 2중 4 의 시작 등)은 옆이나 소재 쪽
 */
private fun art(kind: TaperKind, type: Int): Art = if (!kind.double) {
    when (type) {
        1 -> Art(
            listOf(o(.185f, .265f), o(.449f, .265f), o(.683f, .517f), o(.683f, .80f)), listOf(2, 1), listOf(o(.081f, .012f), o(0f, -.069f)),
            ends = listOf(o(.08f, 0f), o(0f, -.06f)),
        )
        // 끝 다리는 회전축(.94)에 닿지 않게 .86 까지
        2 -> Art(
            listOf(o(.38f, .08f), o(.38f, .365f), o(.616f, .616f), o(.616f, .86f)), listOf(2, 1), listOf(o(.081f, -.02f), o(.081f, -.045f)), .255f,
            ends = listOf(o(.08f, 0f), o(.055f, .03f)),
        )
        3 -> Art(listOf(o(.11f, .39f), o(.374f, .39f), o(.608f, .636f), o(.872f, .636f)), listOf(2, 1), listOf(o(.083f, -.069f), o(0f, -.069f)))
        4 -> Art(
            listOf(o(.21f, .175f), o(.21f, .477f), o(.48f, .765f), o(.76f, .765f)), listOf(2, 1), listOf(o(.081f, -.069f), o(.083f, -.045f)),
            ends = listOf(o(0f, -.06f), o(.055f, 0f)),
        )
        else -> Art(
            listOf(o(.11f, .64f), o(.37f, .64f), o(.60f, .39f), o(.86f, .39f)), listOf(2, 1), listOf(o(.081f, -.069f), o(-.10f, -.06f)),
            ends = listOf(o(0f, .06f), o(0f, -.06f)),
        )
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
            ends = listOf(o(0f, -.06f), o(0f, .06f)),
        )
        3 -> Art(
            listOf(o(.06f, .36f), o(.22f, .36f), o(.44f, .66f), o(.66f, .36f), o(.88f, .36f)), listOf(3, 2, 1),
            listOf(o(.04f, -.069f), o(0f, .075f), o(-.07f, -.069f)), .22f,
            ends = listOf(o(0f, .06f), o(0f, .06f)),
        )
        // 형상 4: 아래 두 경사를 T1 쪽으로 줄여 T3 가 칩과 떨어지게 하고(각도는 그대로), T2 글은 왼쪽 점선 위가 아닌
        // 아래(이송 화살 밑)에, T3 글은 각2 글과 떨어지게 다리 위 왼쪽에 둔다
        else -> Art(
            listOf(o(.05f, .70f), o(.28f, .70f), o(.47f, .55f), o(.60f, .36f), o(.84f, .36f)), listOf(3, 2, 1),
            listOf(o(.08f, -.069f), o(.04f, .08f), o(-.13f, -.06f)), .22f,
            ends = listOf(o(0f, .06f), o(0f, .05f)),
        )
    }
}

/** T 점 · 라벨 색(입력 줄 점 이름 표와 같다): T1 주황, T2 청록, T3 남색 */
internal fun pointColor(c: AppColors, i: Int): Color = when (i) {
    0 -> c.drawT1
    1 -> c.drawT2
    else -> c.indigo
}

/** 인서트 날끝 쪽 두 변(외경 · 정방향, 화면 dp): 13dp 씩 −5° · −85° — 사이 80° 마름모 */
private val insertA = Offset(13f * cos(5 * PI / 180).toFloat(), -13f * sin(5 * PI / 180).toFloat())
private val insertB = Offset(-insertA.y, -insertA.x)

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
    val compact: Boolean,
    val focus: String?,
) {
    val w = d.size.width
    val h = d.size.height

    /**
     * 라벨 거리 단위: 02 카드 폭을 글 배율로 — 라벨은 카드 크기가 아니라 글 크기를 따라 점 곁에 붙는다. 요약 칸은 T 글 12sp(15sp × .8) 에 맞춘다.
     * 글꼴 배율 묶음(artFontCap)은 빼고 폭만 따른다 — 큰 글꼴에서 글은 1.1배까지인데 거리만 줄면 라벨이 점에 붙는다
     */
    val unit = with(d) { 199.dp.toPx() * if (compact) .8f else kt / artFontCap() }

    fun dp(v: Float) = with(d) { (v * ks).dp.toPx() }

    /** 배율 없이 고정 dp(요약 칸의 윤곽 · 점) */
    fun fixed(v: Float) = with(d) { v.dp.toPx() }

    val profile = Stroke(if (compact) fixed(2f) else dp(DrawSpec.profile.value), join = StrokeJoin.Miter)

    /** 그린 글 · 바이트 칸 — 입력 중인 칸 이름이 피해 갈 자리 */
    val taken = mutableListOf<Rect>()

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
        fillStock(stock(a.pts))
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
        // 내경 역방향(뒤보링)이고 +Z 쪽(화면 오른쪽 = 꺾은선 왼쪽 끝)이 가로로 열려 있으면 바는 구멍을 지나 +Z 로 나간다.
        // 자루가 그림을 가로지르므로 점 · 이름보다 먼저 그린다(이름이 바 위에 보이게)
        val back = flipY && flipX && a.pts[0].y == a.pts[1].y
        if (back) boringBar(barTop(a, back = true), back = true)
        for (i in 0 until t.size - 1) {
            val label = if (double) "각${i + 1}" else "각도"
            angle(t[i], left, t[i + 1] - t[i], arcR, label, if (double) 11f else 11.5f, hot = focus == "ang")
        }
        feed(t[0], t[1], left)
        // 시작 · 끝: T1 · 마지막 T 와 이웃한 자유 끝(배열 순서가 아니라 이웃으로 — 어느 쪽이 첫 점이어도 맞다)
        val startLast = a.t.first() == a.pts.lastIndex - 1
        val ends = if (startLast) listOf(pts.last(), pts.first()) else listOf(pts.first(), pts.last())
        // 입력 중인 끝의 테는 '시작' · '끝' 글보다 먼저(테가 글 위에 그려져 '시' 를 덮었다)
        val hotEnd = when (focus) {
            "start" -> 0
            "end" -> 1
            else -> null
        }
        if (hotEnd != null) ring(ends[hotEnd])
        if (!compact) {
            ends.forEachIndexed { i, p ->
                d.drawCircle(c.subtle, dp(3.6f), p)
                d.drawCircle(c.textMuted, dp(2.4f), p)
                text(if (i == 0) "시작" else "끝", p + shift(a.ends[i]) + clearRing(shift(a.ends[i]), i == hotEnd), c.textMuted, 10.5f, FontWeight.Medium)
            }
        }
        // 입력 중인 점 칸(x1 · z2 · r3): 그 T 점에 테, T 글 바깥 곁에 칸 이름(X1 · Z2 · R3)
        val hot = focus?.takeIf { it.length == 2 && it[0] in "xzr" }?.let { it[1].digitToIntOrNull()?.minus(1) }?.takeIf { it in t.indices }
        if (hot != null) ring(t[hot])
        // 점: 바탕색 테 + 점 이름 표와 같은 색으로 채운 원
        val dot = if (compact) fixed(3.5f) else dp(4f)
        t.forEachIndexed { i, p ->
            d.drawCircle(c.subtle, dot + dp(1.6f), p)
            d.drawCircle(pointColor(c, i), dot, p)
        }
        val boxes = t.mapIndexed { i, p -> text("T${i + 1}", p + shift(a.labels[i]) + clearRing(shift(a.labels[i]), i == hot), pointColor(c, i), 15f, FontWeight.SemiBold) }
        if (!flipY) outerTool() else if (!back) boringBar(barTop(a, back = false), back = false)
        if (hot != null) {
            taken += Rect(t[hot], dp(9f))
            beside(boxes[hot], shift(a.labels[hot]), focus.uppercase())
        }
    }

    /** 소재 면: 보통은 빗금, 요약 칸은 면만 */
    private fun fillStock(path: Path) {
        if (compact) d.drawPath(path, c.drawStock) else d.hatchFill(path, c.drawStock, c.drawHatch, ks)
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

    /**
     * 기준선 방향 ref 에서 경사 방향 s 까지의 호와 각도 글(치수라 파랑). 호가 화살촉 3.5개 길이보다 짧으면 촉 없이.
     * 요약 칸은 글 없이 호만. hot(각도 칸 입력 중)이면 부채꼴을 옅게 칠하고 호 · 촉 · 글을 lineFocus 로 굵게
     */
    private fun angle(p: Offset, ref: Offset, s: Offset, r: Float, label: String, size: Float, hot: Boolean) {
        val color = if (hot) c.lineFocus else c.drawAngle
        val a0 = atan2(ref.y, ref.x)
        var sweep = atan2(s.y, s.x) - a0
        while (sweep > PI) sweep -= (2 * PI).toFloat()
        while (sweep < -PI) sweep += (2 * PI).toFloat()
        if (hot) {
            d.drawArc(
                c.lineFocus.copy(alpha = .18f), (a0 * 180 / PI).toFloat(), (sweep * 180 / PI).toFloat(),
                useCenter = true, topLeft = p - Offset(r, r), size = Size(2 * r, 2 * r),
            )
        }
        val head = dp(4.5f)
        val heads = r * abs(sweep) >= 3.5f * head
        val dir = if (sweep > 0) 1f else -1f
        // 촉이 있으면 호를 촉 안쪽까지만 긋는다(굵은 획이 촉 끝을 넘지 않게)
        val inset = if (heads) head / r * 0.8f * dir else 0f
        d.drawArc(
            color,
            ((a0 + inset) * 180 / PI).toFloat(),
            ((sweep - 2 * inset) * 180 / PI).toFloat(),
            useCenter = false,
            topLeft = p - Offset(r, r),
            size = Size(2 * r, 2 * r),
            style = Stroke(dp(if (hot) 2.6f else 1.3f)),
        )
        if (heads) {
            // 원 위 각 e 의 점에 끝이 오는 촉 — 호를 따라 바깥(각이 작아지는 쪽 · 커지는 쪽)을 가리킨다
            for ((e, sign) in listOf(a0 to -dir, a0 + sweep to dir)) {
                d.arrowHead(p + Offset(cos(e), sin(e)) * r, Offset(-sin(e), cos(e)) * sign, color, head)
            }
        }
        // 글은 호의 가운데 방향, 호 바깥
        val mid = a0 + sweep / 2
        if (!compact) text(label, p + Offset(cos(mid), sin(mid)) * (r + .075f * unit), color, size, FontWeight.Bold)
    }

    /** 입력 중인 점 · 끝: lineFocus 테(r 8dp, 2.4dp)와 옅은 속 */
    private fun ring(p: Offset) {
        d.drawCircle(c.lineFocus.copy(alpha = .18f), dp(8f), p)
        d.drawCircle(c.lineFocus, dp(8f), p, style = Stroke(dp(2.4f)))
    }

    /**
     * 테 둘린 점(on)의 글은 점 → 글 방향(v)으로 6dp 더 민다 — 테(8dp + 굵기 반)가 글자 사이로 비치거나('Tr2') 글 밑을 지났다.
     * 글 칸 거리는 글 크기를 따르지만 테는 고정 크기라 큰 글꼴 · 요약 칸에서 더 붙었다
     */
    private fun clearRing(v: Offset, on: Boolean): Offset {
        val len = v.getDistance()
        return if (!on || len < 1e-3f) Offset.Zero else v / len * dp(6f)
    }

    /** 입력 중인 치수 글: lineFocus 알약(옅은 속 + 테) — 글 칸 r 을 조금 넓혀 그 위에 씌운다 */
    private fun pill(r: Rect) {
        val box = r.inflate(dp(2f))
        val corner = CornerRadius(box.height / 2)
        d.drawRoundRect(c.lineFocus.copy(alpha = .18f), box.topLeft, box.size, corner)
        d.drawRoundRect(c.lineFocus, box.topLeft, box.size, corner, style = Stroke(dp(1.6f)))
    }

    /** 글 크기(sp): 보통은 base × kt 이고 10.5sp 아래로는 줄이지 않는다. 요약 칸은 12sp 고정 */
    private fun style(color: Color, base: Float, weight: FontWeight) =
        TextStyle(fontFamily = sans, fontSize = (if (compact) 12f else (base * kt).coerceAtLeast(10.5f)).sp, fontWeight = weight, color = color)

    /** 바탕색 테(halo)를 두른 글. 그린 칸을 돌려준다 */
    private fun text(s: String, center: Offset, color: Color, size: Float, weight: FontWeight): Rect =
        d.haloText(measurer, AnnotatedString(s), style(color, size, weight), c.subtle, ks, center = center).also { taken += it }

    /**
     * 입력 중인 칸 이름(12sp, lineFocus)을 T 글 칸 box 곁에 붙인다(v = 점 → T 글). 점에서 먼 옆("T1 X1") → 먼 위아래 →
     * 반대 옆 → 반대 위아래 가운데 카드 안이고 다른 글 · 바이트와 겹치지 않는 첫 자리
     */
    private fun beside(box: Rect, v: Offset, s: String) {
        val st = style(c.lineFocus, 12f, FontWeight.Bold)
        val half = measurer.measure(s, st).size.toSize().let { Offset(it.width / 2, it.height / 2) }
        val gap = dp(2f)
        val side = { far: Boolean -> Offset(if ((v.x < 0) == far) box.left - gap - half.x else box.right + gap + half.x, box.center.y) }
        val updown = { far: Boolean -> Offset(box.center.x, if ((v.y < 0) == far) box.top - gap - half.y else box.bottom + gap + half.y) }
        val tries = listOf(side(true), updown(true), side(false), updown(false))
        val fits = { p: Offset ->
            val r = Rect(p - half, p + half)
            r.left >= 0 && r.top >= 0 && r.right <= w && r.bottom <= h && taken.none { it.overlaps(r) }
        }
        text(s, tries.firstOrNull(fits) ?: tries.first(), c.lineFocus, 12f, FontWeight.Bold)
    }

    private fun poly(p: List<Offset>) = Path().apply {
        val q = p.map(::at)
        moveTo(q[0].x, q[0].y)
        q.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }

    /** 닫힌 꺾은선(화면 점). round 의 꼭짓점은 양옆 변을 따라 r 만큼 떼어 둥글게 잇는다 */
    private fun rounded(p: List<Offset>, round: Set<Int> = emptySet(), r: Float = 0f) = Path().apply {
        p.forEachIndexed { i, v ->
            if (i in round) {
                val a = v + (p[(i + p.size - 1) % p.size] - v).let { it / it.getDistance() } * r
                val b = v + (p[(i + 1) % p.size] - v).let { it / it.getDistance() } * r
                if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
                quadraticTo(v.x, v.y, b.x, b.y)
            } else if (i == 0) {
                moveTo(v.x, v.y)
            } else {
                lineTo(v.x, v.y)
            }
        }
        close()
    }

    private fun solid(path: Path, fill: Color) {
        d.drawPath(path, fill)
        d.drawPath(path, c.drawToolLine, style = Stroke(dp(1f), join = StrokeJoin.Round))
    }

    /**
     * 바이트 · 보링바 한 벌(카드 끝에서 잘린다). 자리는 날끝 n 에서 dp(× ks)로 잰 외경 · 정방향 꼴(오른쪽 +, 아래 +)이고
     * 내경은 위아래, 역방향은 좌우를 뒤집는다. 자루 holder 위에 인서트(80° 마름모, 날끝 1.5dp 둥글게, 가운데 나사 구멍)
     */
    private fun tool(n: Offset, holder: List<Offset>) {
        fun p(v: Offset) = n + Offset(if (flipX) -dp(v.x) else dp(v.x), if (flipY) -dp(v.y) else dp(v.y))
        val body = holder.map(::p)
        val insert = listOf(Offset.Zero, insertA, insertA + insertB, insertB).map(::p)
        d.clipRect {
            solid(rounded(body), c.drawToolBody)
            solid(rounded(insert, setOf(0), dp(1.5f)), c.drawInsert)
            d.drawCircle(c.drawToolLine, dp(1.8f), p((insertA + insertB) / 2f))
        }
        (body + insert).let { q -> taken += Rect(q.minOf { it.x }, q.minOf { it.y }, q.maxOf { it.x }, q.maxOf { it.y }) }
    }

    /** 외경 바이트: 날끝이 카드 (.80, .30). 폭 16dp 자루가 카드 위 끝 너머까지 서고, 인서트 밑은 45° 로 깎였다 */
    private fun outerTool() {
        val n = at(o(.80f, .30f))
        val top = -n.y / dp(1f) - 2f
        val a = insertA
        tool(n, listOf(o(6f, top), o(22f, top), o(22f, a.y - (22f - a.x)), a, o(6f, -3f)))
    }

    /**
     * 내경 보링바(11): 날끝에서 오른쪽으로 누운 두께 15dp 자루(카드 밖으로 잘림), 날끝 밑은 45° 로 깎였다. 꺾은선 아래(구멍 안)에 둔다.
     * back = 뒤보링(역방향, +Z 쪽이 열린 구멍): 인서트만 뒤집혀(날이 +Z) 날끝 왼쪽에 서고, 자루는 그대로 화면 오른쪽(+Z, 구멍 입구)
     * 카드 끝까지 — 척 쪽에서 들어오지 않게. 헤드는 인서트 밑(15dp, 아래 모서리 45°). +Z 쪽이 막힌 역방향(홑 2 · 4)은 통째로 뒤집는다
     */
    private fun boringBar(top: Float, back: Boolean) {
        // top = 자루 윗변(화면 높이 비율). 날끝은 그보다 6dp 위
        val n = at(o(.80f, 1 - top)) - Offset(0f, dp(6f))
        if (back) {
            // tool() 이 x 를 뒤집으므로 음수 x 가 화면 오른쪽
            val edge = (w - n.x) / dp(1f) + 2f
            tool(n, listOf(o(15f, -6f), o(-edge, -6f), o(-edge, -21f), o(9f, -21f), o(15f, -15f)))
            return
        }
        val edge = (if (flipX) n.x else w - n.x) / dp(1f) + 2f
        val b = insertB
        tool(n, listOf(o(6f, -6f), o(edge, -6f), o(edge, -21f), o(b.x + 21f + b.y, -21f), b))
    }

    /**
     * 보링바 윗변: 자루가 지나는 꺾은선보다 아래 — 정방향은 오른쪽(x ≥ 0.7), 0.62~0.70.
     * 뒤보링은 날끝(x 0.8)에서 꺾은선 왼쪽 끝(뒤집혀 화면 오른쪽)까지 지나므로 x ≤ 0.8 모두, 0.62~0.80
     */
    private fun barTop(a: Art, back: Boolean): Float {
        val low = a.pts.filter { if (back) it.x <= .8f else it.x >= .7f }.maxOfOrNull { 1 - it.y } ?: 0f
        return (low + .06f).coerceIn(.62f, if (back) .80f else .70f)
    }

    /**
     * 홈: 소재(외경 아래, 홈은 비움) · 회전축 · 외경 → 홈(벽·바닥) → 외경, 오른쪽 벽에서 들어가는 홈 바이트,
     * 치수 표(입력 칸과 같은 이름: ØD · Ød · W · 인서트 폭 T · 시작 Z), 기준 인선(채운 점 + 글)과 다른 모서리(빈 테).
     * 요약 칸은 치수 표를 빼고 입력 중인 것만 쓴다
     */
    fun groove(type: Int) {
        val od = .36f
        val bottom = .62f
        val lw = .30f
        val rw = .70f
        // 첫 단: 날 오른쪽이 오른쪽 벽에 닿은 홈 바이트(날 아래 끝 tip)
        val bl = .54f
        val tip = bottom - .115f
        fillStock(poly(listOf(o(-.02f, od), o(lw, od), o(lw, bottom), o(rw, bottom), o(rw, od), o(1.02f, od), o(1.02f, .94f), o(-.02f, .94f))))
        d.centerLine(Offset(0f, .94f * h), Offset(w, .94f * h), c.drawRef, ks)
        d.drawPath(
            Path().apply {
                listOf(o(.05f, od), o(lw, od), o(lw, bottom), o(rw, bottom), o(rw, od), o(.95f, od)).map(::at).forEachIndexed { i, p ->
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            },
            c.drawProfile,
            style = profile,
        )
        solid(poly(listOf(o(bl, .03f), o(rw, .03f), o(rw, tip - .085f), o(bl, tip - .085f))), c.drawToolBody)
        solid(rounded(listOf(o(bl, tip - .085f), o(rw, tip - .085f), o(rw, tip), o(bl, tip)).map(::at), setOf(2, 3), dp(2f)), c.drawInsert)

        // 치수 표(치수색). 입력 중인 것은 lineFocus 로 쓰고 글에 알약을 씌운다
        fun color(key: String) = if (focus == key) c.lineFocus else c.drawAngle
        fun shown(key: String) = !compact || focus == key
        fun mark(key: String, box: Rect) {
            if (focus == key) pill(box)
        }
        fun tag(key: String, s: String, center: Offset) {
            if (shown(key)) mark(key, text(s, center, color(key), 10.5f, FontWeight.Bold))
        }
        // 요약 칸은 촉을 줄여 좁은 T 치수도 글이 치수선 안에 든다
        fun dim(key: String, s: String, a: Offset, b: Offset, offset: Float) {
            if (!shown(key)) return
            val st = style(color(key), 10.5f, FontWeight.Bold)
            mark(key, d.linearDim(a, b, offset, AnnotatedString(s), color(key), if (compact) ks * .75f else ks, measurer, st, c.subtle))
        }
        tag("od", "ØD", at(o(.17f, od)) + shift(o(0f, -.05f)))
        tag("bottom", "Ød", at(o((lw + rw) / 2, bottom + .065f)))
        dim("width", "W", at(o(lw, bottom)), at(o(rw, bottom)), .165f * h)
        // 요약 칸(키패드 그림)은 날끝과 홈 바닥 사이가 알약보다 낮아 알약이 두 윤곽에 걸린다 — 홈 바닥 10dp 아래로
        dim("insert", "T", at(o(bl, tip)), at(o(rw, tip)), if (compact) (bottom - tip) * h + dp(10f) else (bottom - tip) / 2 * h)
        // 시작 Z: 오른쪽 벽 위 모서리에서 오른쪽 위로 짧은 지시선, 그 끝에 글(카드 안으로)
        if (shown("zStart")) {
            val corner = at(o(rw, od))
            val end = corner + Offset(dp(7f), -dp(7f))
            d.drawLine(color("zStart"), corner, end, dp(DrawSpec.leader.value))
            val size = measurer.measure("시작 Z", style(color("zStart"), 10.5f, FontWeight.Bold)).size
            val half = size.width / 2f
            // 알약(2dp 넓힘 + 테)이 카드 테두리와 ØD 윤곽에 닿지 않게: 오른쪽 끝에서 9dp, 아래는 윤곽에서 5dp 위
            tag(
                "zStart", "시작 Z",
                Offset((end.x + dp(2f) + half).coerceAtMost(w - half - dp(9f)), minOf(end.y - dp(2f), corner.y - size.height / 2f - dp(5f))),
            )
        }

        // 기준 인선: 바탕색 테 + 채운 점 + 글(두 줄). 다른 모서리는 빈 테만
        val ref = at(o(if (type == 1) bl else rw, tip))
        val other = at(o(if (type == 1) rw else bl, tip))
        d.drawCircle(c.lineStrong, dp(3.5f), other, style = Stroke(dp(1.2f)))
        d.drawCircle(c.subtle, dp(6f), ref)
        d.drawCircle(c.drawT1, dp(4.5f), ref)
        // 글은 점 바깥 옆(홈 안쪽 · 벽 너머), 점 테에서 2dp 띄운다
        val label = if (compact) "기준" else "기준\n인선"
        val dx = measurer.measure(label, style(c.drawT1, 12.5f, FontWeight.Bold)).size.width / 2f + dp(8f)
        text(label, ref + Offset(if (type == 1) -dx else dx, 0f), c.drawT1, 12.5f, FontWeight.Bold)
    }
}
