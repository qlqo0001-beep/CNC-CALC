package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.ui.DrawSpec
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 그림 치수 이름 옆 값의 종류: 넣은 값 · 비운 칸을 계산이 채운 값(자동) · 비어서 계산이 멈춘 값 */
internal enum class DimKind { Given, Auto, Missing }

/** 그림 치수 이름 옆에 쓰는 값. text = 칸에 보이는 그대로(자동은 Fmt.fixed3). Missing 은 '?' 로 쓴다 */
internal class DimShown(val text: String, val kind: DimKind = DimKind.Given)

/**
 * TYPE 그림(04 의 자리): 측면 개념도 + 치수선 + 치수 이름(입력 칸 이름과 같다).
 * 윤곽은 계산기가 견본 치수로 만든 도면 윤곽(반경)을 축 아래로 접어 그린다 — 그림이 계산의 해석과 같다.
 * 견본 치수는 04 그림의 비율을 잰 값이다. 자리는 04 의 카드 몸통(193 × 189dp)에 대한 비율이라 작은 칸에서도 같은 모양이고,
 * 선 · 글 크기는 DrawKit 배율로 묶는다(작은 칸에서도 읽히고 넓은 창에서 커지지 않게).
 * showDatum = 입력 화면 그림: Z0 ▲ 와 치수가 닿는 이론 꼭짓점 ○ 를 더 그린다. 고르기 카드는 false.
 * 아래 넷은 고르기 카드에서 비워 둔다(이름만):
 * - l2Ref: 형상 2 에서 고른 L2 기준. 그 L2 는 이름을 굵게, 다른 L2 는 흐리게(선 α .25 · 이름 textHint)
 * - labels: 치수 key(mainR · cr1 · cr2 · d1 · d2 · l1 · l2 · leg) → 이름 옆에 쓸 값. 있으면 끝 직선 길이(leg) 치수도 그린다
 * - contradiction: 서로 맞지 않는 치수 key — 이름 · 값을 빨강 + 점선 밑줄
 * - focus: 키패드 그림. 그 key 의 치수 하나만 초점색 · 굵은 선 · 알약 이름으로 그리고 다른 치수는 뺀다
 */
@Composable
internal fun RoundTypeArt(
    type: Int,
    modifier: Modifier = Modifier.fillMaxSize(),
    showDatum: Boolean = false,
    l2Ref: L2Ref? = null,
    labels: Map<String, DimShown>? = null,
    contradiction: Set<String> = emptySet(),
    focus: String? = null,
) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    val shape = remember(type) { RoundCalc.calc(sampleInput(type)).solution?.drawing }
    Canvas(modifier) {
        if (shape != null) ArtPen(this, c, measurer, sans, showDatum, l2Ref, labels, contradiction, focus).draw(type, shape)
    }
}

/** 그림용 견본 치수(04 의 비율). 노즈R 0 — 윤곽만 쓴다 */
internal fun sampleInput(type: Int): RoundInput = when (type) {
    1 -> RoundInput(1, mainR = 10.0, cornerR1 = 1.0, l1 = 14.8, noseR = 0.0)
    2 -> RoundInput(2, mainR = 8.0, cornerR1 = 0.8, d1 = 5.5, l1 = 4.5, l2Ref = L2Ref.B, noseR = 0.0, legLen = 4.5)
    3 -> RoundInput(3, mainR = 11.5, cornerR1 = 1.0, d1 = 20.0, noseR = 0.0, legLen = 11.8)
    4 -> RoundInput(4, mainR = 5.0, cornerR1 = 0.6, d1 = 6.0, noseR = 0.0, legLen = 10.0)
    5 -> RoundInput(5, cornerR1 = 0.6, cornerR2 = 0.8, d1 = 4.3, d2 = 20.0, l1 = 4.5, l2 = 6.45, noseR = 0.0, legLen = 6.8)
    else -> RoundInput(6, mainR = 7.0, cornerR1 = 0.4, cornerR2 = 0.5, d1 = 3.0, d2 = 20.0, l1 = 2.9, l2 = 4.35, noseR = 0.0, legLen = 4.6)
}

private class ArtPen(
    val d: DrawScope,
    val c: AppColors,
    val measurer: TextMeasurer,
    val sans: FontFamily,
    val showDatum: Boolean,
    val l2Ref: L2Ref?,
    val labels: Map<String, DimShown>?,
    val contradiction: Set<String>,
    val focus: String?,
) {
    val w = d.size.width
    val h = d.size.height

    /** 04 카드 몸통 폭 193dp 에 대한 배율 — 자리(기하)에만 쓴다 */
    val k = w / with(d) { 193.dp.toPx() }

    /** DrawKit 의 묶은 배율: first = 선(ks), second = 글 */
    private val scale = with(d) { artScale(w, 193.dp) }
    val ks = scale.first
    val ink = c.textValue

    /** 치수선 · 보조선 · 지시선 */
    val line = c.textBody
    val style = TextStyle(fontFamily = sans, fontSize = (11.5f * scale.second).sp, fontWeight = FontWeight.Medium, color = ink, letterSpacing = 0.sp)

    /** 초점 치수의 이름: 굵게 13sp 이상, 초점색 α .16 을 카드에 섞은 알약 위 */
    val focusStyle = style.copy(fontSize = maxOf(13f, 11.5f * scale.second).sp, fontWeight = FontWeight.Bold)
    val pill = c.lineFocus.copy(alpha = .16f).compositeOver(c.card)

    /** 자리 길이(dp × k) */
    fun dp(v: Float) = with(d) { (v * k).dp.toPx() }

    /** 선 굵기 · 화살 · 표시 크기(dp × ks) */
    fun px(v: Float) = with(d) { (v * ks).dp.toPx() }

    fun px(v: Dp) = px(v.value)

    // 그림 자리: 윤곽의 Z 범위를 폭 비율 x0..x1 에, 축을 높이 비율 cy 에 둔다
    var x0 = 0f
    var s = 1f
    var zMin = 0.0
    var cy = 0f

    /** 윤곽의 가장 높은 반경(부품 맨 위) */
    var peak = 0.0

    fun at(z: Double, x: Double) = Offset(x0 * w + ((z - zMin) * s).toFloat(), cy * h - (x * s).toFloat())

    fun draw(type: Int, shape: Contour) {
        val upper = Probe.points(shape)
        zMin = upper.minOf { it.z }
        peak = upper.maxOf { it.x }
        val (left, right, axis) = when (type) {
            1 -> Triple(.282f, .707f, .4725f)
            2 -> Triple(.188f, .738f, .47f)
            3 -> Triple(.232f, .728f, .51f)
            4 -> Triple(.161f, .780f, .477f)
            5 -> Triple(.200f, .650f, .535f)
            else -> Triple(.215f, .630f, .478f)
        }
        x0 = left
        cy = axis
        s = ((right - left) * w / (0.0 - zMin)).toFloat()
        body(shape, upper)
        if (showDatum) datum()
        // 치수는 견본 치수의 이론 꼭짓점(모서리 R 을 넣기 전)에 단다 — 길이 L·L1·L2 가 그 점 사이라서
        val i = sampleInput(type)
        fun onSphere(zc: Double, r: Double, rad: Double) = at(zc + r * cos(rad), r * sin(rad))
        when (type) {
            1 -> {
                val r = i.mainR!!
                val l = i.l1!!
                val face = sqrt(r * r - l * l / 4)
                leader("mainR", "주 R", .582f, .124f, onSphere(-l / 2, r, 1.48))
                leader("cr1", "모서리 R", .159f, .233f, at(-l, face))
                leader("cr1", "모서리 R", .818f, .235f, at(0.0, face))
                hDim("l1", "L", 0.0, -face, -l, -face, .857f)
                vertex(0.0, face, Pt(0.0, -1.0), along(-l / 2, 0.0, face, left = true))
                vertex(-l, face, Pt(0.0, -1.0), along(-l / 2, -l, face, left = false))
            }
            2 -> {
                val r = i.mainR!!
                val a = i.d1!! / 2
                val l1 = i.l1!!
                val half = sqrt(r * r - a * a)
                val zc = -l1 - half
                // 오른쪽 모서리 R 은 위로 — D1 이 짧아 그 이름이 위쪽 끝 너머에 선다. 왼쪽 이음점도 같은 모서리 R(칸 하나).
                // 입력 화면 그림은 그 위가 끝 직선 길이 치수 자리라 아래쪽 이음점을 가리킨다
                leader("mainR", "주 R", .53f, .15f, onSphere(zc, r, 1.31))
                leader("cr1", "모서리 R", .80f, .14f, at(-l1, a))
                if (labels != null && focus == null) leader("cr1", "모서리 R", .13f, .61f, at(zc - half, -a))
                else leader("cr1", "모서리 R", .19f, .30f, at(zc - half, a))
                vDim("d1", "D1", .826f, 0.0, a)
                hDim("l1", "L1", 0.0, -a, -l1, -a, .655f)
                hDim("l2", "L2 (B)", -l1, -a, zc - half, -a, .745f, L2Ref.B)
                hDim("l2", "L2 (A)", 0.0, -a, zc - half, -a, .815f, L2Ref.A)
                leg(zc - half, a)
                vertex(-l1, a, Pt(1.0, 0.0), along(zc, -l1, a, left = true))
                vertex(zc - half, a, Pt(-1.0, 0.0), along(zc, zc - half, a, left = false))
            }
            3, 4 -> {
                val r = i.mainR!!
                val hh = i.d1!! / 2
                val joint = -(r + (if (type == 4) 1 else -1) * sqrt(r * r - hh * hh))
                vDim("d1", "D", if (type == 3) .163f else .10f, zMin, hh)
                if (type == 3) {
                    leader("cr1", "모서리 R", .718f, .163f, at(joint, hh))
                    leader("mainR", "주 R", .83f, .317f, onSphere(-r, r, 0.87))
                    hDim("l1", "L", joint, -hh, 0.0, 0.0, .85f)
                } else {
                    leader("cr1", "모서리 R", .31f, .278f, at(joint, hh))
                    leader("mainR", "주 R", .85f, .336f, onSphere(-r, r, 0.70))
                    hDim("l1", "L", joint, -hh, 0.0, 0.0, .711f)
                }
                leg(joint, hh)
                vertex(joint, hh, Pt(-1.0, 0.0), along(-r, joint, hh, left = false))
            }
            5 -> {
                val a = i.d1!! / 2
                val b = i.d2!! / 2
                val l1 = i.l1!!
                val l2 = i.l2!!
                val u = ((b * b - a * a) / l2 + l2) / 2
                val r = sqrt(u * u + a * a)
                vDim("d2", "D2", .146f, zMin, b)
                // D1 은 짧아 이름이 위쪽 끝 너머에 서므로 오른쪽으로 비켜 두고, 모서리 R(1) 은 그 왼쪽
                vDim("d1", "D1", .9f, 0.0, a)
                leader("cr2", "모서리 R(2)", .623f, .133f, at(-l1 - l2, b))
                leader("mainR", "주 R", .697f, .221f, onSphere(-l1 - u, r, PI / 4))
                leader("cr1", "모서리 R(1)", .675f, .345f, at(-l1, a))
                hDim("l1", "L1", 0.0, -a, -l1, -a, .711f)
                hDim("l2", "L2", -l1, -a, -l1 - l2, -b, .839f)
                leg(-l1 - l2, b)
                vertex(-l1, a, Pt(1.0, 0.0), along(-l1 - u, -l1, a, left = true))
                vertex(-l1 - l2, b, Pt(-1.0, 0.0), along(-l1 - u, -l1 - l2, b, left = false))
            }
            else -> {
                val r = i.mainR!!
                val a = i.d1!! / 2
                val b = i.d2!! / 2
                val l1 = i.l1!!
                val zc = -l1 - sqrt(r * r - a * a)
                val zf = -l1 - i.l2!!
                val hf = sqrt(r * r - (zf - zc) * (zf - zc))
                vDim("d2", "D2", .165f, zMin, b)
                vDim("d1", "D1", .9f, 0.0, a)
                leader("cr2", "모서리 R(2)", .628f, .146f, at(zf, hf))
                leader("mainR", "주 R", .646f, .223f, onSphere(zc, r, PI / 4))
                leader("cr1", "모서리 R(1)", .68f, .33f, at(-l1, a))
                hDim("l1", "L1", 0.0, -a, -l1, -a, .679f)
                hDim("l2", "L2", -l1, -a, zf, -hf, .829f)
                leg(zf, b)
                vertex(-l1, a, Pt(1.0, 0.0), along(zc, -l1, a, left = true))
                vertex(zf, hf, Pt(0.0, 1.0), along(zc, zf, hf, left = false))
            }
        }
    }

    /** 윤곽 점(z, x)을 이은 꺾은선 */
    private fun poly(pts: List<Pt>) = Path().apply {
        pts.forEachIndexed { i, p -> at(p.z, p.x).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
    }

    /**
     * 몸통: 윤곽 + 축 아래로 접은 윤곽을 옅은 금속 그라데이션으로 칠하고, 요소 경계마다 옅은 세로 모서리선 · 옅은 둘레선.
     * 깎는 윤곽(축 위 반쪽)은 가장 굵은 선으로 다시 긋는다
     */
    private fun body(shape: Contour, upper: List<Pt>) {
        val path = poly(upper + upper.asReversed().map { Pt(it.z, -it.x) }).apply { close() }
        val y0 = at(0.0, peak).y
        val y1 = at(0.0, -peak).y
        val metal = c.drawMetal
        d.drawPath(path, Brush.verticalGradient(0f to metal[0], .28f to metal[1], .5f to metal[2], .78f to metal[3], 1f to metal[4], startY = y0, endY = y1))
        // 모서리선: 요소가 꺾이는 곳(접해서 이어지는 곳은 빼지 않는다 — 04 의 렌더도 이음매가 보인다)
        val edge = c.drawMetalEdge
        for (seg in shape.segs.dropLast(1)) {
            val p = seg.end
            if (p.x > 1e-6) d.drawLine(edge.copy(alpha = .35f), at(p.z, p.x), at(p.z, -p.x), px(0.8f))
        }
        d.drawPath(path, edge.copy(alpha = .6f), style = Stroke(px(1.1f), join = StrokeJoin.Round))
        // 중심선(1점 쇄선): 양끝을 10dp 넘긴다
        val over = Offset(px(10f), 0f)
        d.centerLine(at(zMin, 0.0) - over, at(0.0, 0.0) + over, c.drawRef.copy(alpha = .85f), ks)
        d.drawPath(poly(upper), c.drawProfile, style = Stroke(px(2f), join = StrokeJoin.Round))
    }

    /** Z0(부품 오른쪽 끝면): 축 바로 아래에서 위를 가리키는 채운 ▲ */
    private fun datum() {
        val side = px(6f)
        val apex = at(0.0, 0.0) + Offset(0f, with(d) { 1.5.dp.toPx() })
        val base = apex.y + side * sqrt(3f) / 2
        d.drawPath(
            Path().apply {
                moveTo(apex.x, apex.y)
                lineTo(apex.x + side / 2, base)
                lineTo(apex.x - side / 2, base)
                close()
            },
            c.drawT1,
        )
    }

    /** 구(중심 (zc, 0)) 위 점 (z, x) 에서 원호를 따라가는 방향(z, x). 원호가 그 점에서 왼쪽(−Z)으로 가면 left */
    private fun along(zc: Double, z: Double, x: Double, left: Boolean) = if (left) Pt(-x, z - zc) else Pt(x, zc - z)

    /**
     * 이론 꼭짓점 (z, ±x) 두 곳(입력 화면 그림만): 이웃 두 면을 꼭짓점까지 잇는 7dp 점선 토막 + 속 빈 ○.
     * face · arc = 위쪽 꼭짓점에서 두 면으로 가는 방향(z, x) — 아래쪽은 x 를 뒤집는다. 치수 · 지시선 위에 그린다
     */
    private fun vertex(z: Double, x: Double, face: Pt, arc: Pt) {
        if (!showDatum) return
        val dash = PathEffect.dashPathEffect(floatArrayOf(px(2f), px(1.5f)))
        for (sign in listOf(1.0, -1.0)) {
            val p = at(z, sign * x)
            for (dir in listOf(face, arc)) {
                val v = Offset(dir.z.toFloat(), -(sign * dir.x).toFloat())
                d.drawLine(ink.copy(alpha = .5f), p, p + v / v.getDistance() * dp(7f), px(0.8f), pathEffect = dash)
            }
            d.drawCircle(c.card, px(2.2f), p)
            d.drawCircle(ink.copy(alpha = .8f), px(2.2f), p, style = Stroke(px(0.8f)))
        }
    }

    /** 치수 하나를 그리는 모양: 이름 글 · 글 스타일 · 선 색 · 선 배율(ks 에 곱함) · 이름 테 색(초점이면 알약 색) · 초점 · 모순 */
    private class Look(
        val text: AnnotatedString,
        val style: TextStyle,
        val line: Color,
        val weight: Float,
        val halo: Color,
        val focused: Boolean,
        val wrong: Boolean,
    )

    /**
     * key 치수의 모양. 초점 모드에서 다른 치수면 null(그리지 않는다).
     * 값(labels)은 이름 뒤에 두 칸 띄고 굵게: 넣은 값 textValue · 자동 valueComp · 빈칸 '?' 빨강, 지름은 값 앞에 Ø.
     * ref = 형상 2 의 L2 기준(A · B), stack = 이름과 값을 두 줄로(세로 치수 — 옆 자리가 좁다).
     * 끝 직선 길이(leg)는 도면 치수가 아니라 옅은 색(textMuted)
     */
    private fun look(key: String, name: String, ref: L2Ref? = null, stack: Boolean = false): Look? {
        val other = ref != null && l2Ref != null && ref != l2Ref
        if (focus != null && (key != focus || other)) return null
        val focused = focus != null
        val wrong = key in contradiction && !other
        val muted = key == "leg" && !focused
        val text = buildAnnotatedString {
            val weight = when {
                focused || (ref != null && ref == l2Ref) -> FontWeight.Bold
                labels != null -> FontWeight.Normal
                else -> FontWeight.Medium
            }
            val color = when {
                other -> c.textHint
                wrong -> c.error.label
                muted -> c.textMuted
                else -> ink
            }
            withStyle(SpanStyle(color = color, fontWeight = weight)) { append(name) }
            val v = labels?.get(key)
            if (v != null && !other) {
                append(if (stack) "\n" else "  ")
                val vc = when {
                    wrong || v.kind == DimKind.Missing -> c.error.label
                    v.kind == DimKind.Auto -> c.valueComp
                    else -> c.textValue
                }
                withStyle(SpanStyle(color = vc, fontWeight = FontWeight.Bold)) {
                    append(if (v.kind == DimKind.Missing) "?" else if (key.startsWith("d")) "Ø${v.text}" else v.text)
                }
            }
        }
        val base = if (focused) focusStyle else style
        return Look(
            text,
            if (stack) base.copy(textAlign = TextAlign.Center, lineHeight = 1.15.em) else base,
            when {
                focused -> c.lineFocus
                other -> line.copy(alpha = .25f)
                muted -> c.textMuted
                else -> line
            },
            if (focused) 1.4f else 1f,
            if (focused) pill else c.card,
            focused,
            wrong,
        )
    }

    /** 이름 글 왼쪽 x 를 그림 칸 안(양옆 2dp, 알약이면 6dp)으로 민다 */
    private fun inside(x: Float, width: Int, look: Look): Float {
        val pad = with(d) { (if (look.focused) 6 else 2).dp.toPx() }
        return x.coerceAtMost(w - width - pad).coerceAtLeast(pad)
    }

    /** 모순 표시: 이름 칸 아래 1dp 점선(3/2dp, 이름과 같은 빨강 — 색만으로 알리지 않게) */
    private fun underline(box: Rect) {
        val y = box.bottom - px(1.5f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(px(3f), px(2f)))
        d.drawLine(c.error.label, Offset(box.left, y), Offset(box.right, y), px(1f), pathEffect = dash)
    }

    /**
     * 지시선 이름: halo 이름 + 밑줄 + 밑줄 끝에서 대상까지 꺾인 선 + 채운 화살(04). 이름 가운데 = 폭·높이 비율(lx, ly).
     * 밑줄은 대상에 가까운 쪽 끝에서 내려간다 — 대상이 이름 바로 아래면 그 자리에서 곧게(모순이면 밑줄이 빨간 점선).
     * 이름은 그림 칸 안으로 민다
     */
    private fun leader(key: String, name: String, lx: Float, ly: Float, target: Offset) {
        val look = look(key, name) ?: return
        val size = measurer.measure(look.text, look.style).size
        val tl = Offset(inside(lx * w - size.width / 2f, size.width, look), ly * h - size.height / 2f)
        val box = d.haloText(measurer, look.text, look.style, look.halo, ks, topLeft = tl, pill = look.focused)
        val width = px(DrawSpec.leader) * look.weight
        val y = tl.y + size.height - px(1.5f)
        val a = Offset(tl.x - px(1f), y)
        val b = Offset(tl.x + size.width + px(1f), y)
        if (look.wrong) underline(box) else d.drawLine(look.line, a, b, width)
        val from = Offset(target.x.coerceIn(a.x, b.x), y)
        d.drawLine(look.line, from, target, width)
        d.arrowHead(target, target - from, look.line, px(DrawSpec.arrowLen) * look.weight)
    }

    /** 점 p 에서 높이 yy 의 치수선까지 세로 보조선: 점에서 틈을 두고 치수선을 조금 넘게(치수선이 위든 아래든) */
    private fun ext(p: Offset, yy: Float, color: Color, width: Float, dash: PathEffect? = null) {
        val side = if (yy > p.y) 1f else -1f
        d.drawLine(color, Offset(p.x, p.y + side * px(DrawSpec.extGap)), Offset(p.x, yy + side * px(DrawSpec.extOver)), width, pathEffect = dash)
    }

    /**
     * 가로 치수: (za, xa)·(zb, xb) 에서 내린 보조선 + 높이 비율 y 의 치수선(DrawKit: 짧으면 바깥 화살 · 이름은 오른쪽 끝 너머).
     * x 는 그림의 축 아래쪽 점(반경 음수)이라 보조선이 아래로 내려온다. 두 점 높이가 달라 보조선은 여기서 긋는다
     */
    private fun hDim(key: String, name: String, za: Double, xa: Double, zb: Double, xb: Double, y: Float, ref: L2Ref? = null) {
        val look = look(key, name, ref) ?: return
        val yy = y * h
        val (l, r) = listOf(at(za, xa), at(zb, xb)).sortedBy { it.x }
        for (p in listOf(l, r)) ext(p, yy, look.line, px(DrawSpec.ext) * look.weight)
        val box = d.linearDim(Offset(l.x, yy), Offset(r.x, yy), 0f, look.text, look.line, ks * look.weight, measurer, look.style, look.halo, look.focused)
        if (look.wrong) underline(box)
    }

    /** 세로 치수(지름): z 의 윤곽 끝(±r)에서 가로 보조선 + 폭 비율 x 의 치수선(짧으면 바깥 화살 · 이름은 위쪽 끝 너머). 값은 이름 아랫줄 */
    private fun vDim(key: String, name: String, x: Float, z: Double, r: Double) {
        val look = look(key, name, stack = true) ?: return
        val top = at(z, r)
        val box = d.linearDim(at(z, -r), top, x * w - top.x, look.text, look.line, ks * look.weight, measurer, look.style, look.halo, look.focused)
        if (look.wrong) underline(box)
    }

    /**
     * 끝 직선 길이(마지막 가로 요소 = RoundInput.legLen, 입력 · 키패드 그림만): 그 요소의 이론 시작 z(반경 x)부터 윤곽 왼쪽 끝까지,
     * 부품 맨 위에서 14dp 올린 옅은 점선 치수(4/3dp · 0.8dp, 보조선은 위로). 이름은 치수선 위 가운데 —
     * 위가 모자라면(플랜지가 가장 높은 형상 6) 치수선을 칸 안까지 내리고 이름은 오른쪽 끝 옆
     */
    private fun leg(z: Double, x: Double) {
        if (labels == null && focus == null) return
        val look = look("leg", "끝 직선 길이") ?: return
        val size = measurer.measure(look.text, look.style).size
        val pad = with(d) { 2.dp.toPx() }
        val above = at(0.0, peak).y - dp(14f) - px(1f) - size.height >= pad
        val yy = (at(0.0, peak).y - dp(14f)).coerceAtLeast(size.height / 2f + pad)
        val l = at(zMin, x)
        val r = at(z, x)
        val width = px(if (look.focused) 1.4f else .8f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(px(4f), px(3f)))
        for (p in listOf(l, r)) ext(p, yy, look.line, width, dash)
        d.drawLine(look.line, Offset(l.x, yy), Offset(r.x, yy), width, pathEffect = dash)
        val arrow = px(DrawSpec.arrowLen) * look.weight
        d.arrowHead(Offset(l.x, yy), Offset(-1f, 0f), look.line, arrow)
        d.arrowHead(Offset(r.x, yy), Offset(1f, 0f), look.line, arrow)
        val tl = if (above) Offset(inside((l.x + r.x - size.width) / 2, size.width, look), yy - px(1f) - size.height)
        else Offset(inside(r.x + px(4f), size.width, look), yy - size.height / 2f)
        d.haloText(measurer, look.text, look.style, look.halo, ks, topLeft = tl, pill = look.focused)
    }
}

/** 윤곽을 꺾은선으로: 요소 끝점과 원호를 2° 안팎으로 나눈 점 */
private object Probe {
    fun points(c: Contour): List<Pt> = buildList {
        add(c.segs.first().start)
        for (s in c.segs) {
            if (s is Arc) {
                val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
                val sweep = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
                val n = maxOf(4, (abs(sweep) / (PI / 90)).toInt())
                for (i in 1..n) {
                    val t = a0 + sweep * i / n
                    add(Pt(s.center.z + s.radius * cos(t), s.center.x + s.radius * sin(t)))
                }
            } else {
                add(s.end)
            }
        }
    }
}
