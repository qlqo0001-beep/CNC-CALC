package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * TYPE 그림(04 의 자리): 측면 개념도 + 치수선 + 치수 이름(입력 칸 이름과 같다).
 * 윤곽은 계산기가 견본 치수로 만든 도면 윤곽(반경)을 축 아래로 접어 그린다 — 그림이 계산의 해석과 같다.
 * 견본 치수는 04 그림의 비율을 잰 값이다. 자리는 04 의 카드 몸통(193 × 189dp)에 대한 비율이라 작은 칸에서도 같은 모양이고,
 * 선 · 글 크기는 DrawKit 배율로 묶는다(작은 칸에서도 읽히고 넓은 창에서 커지지 않게).
 * showDatum = 입력 화면 그림: Z0 ▲ 와 치수가 닿는 이론 꼭짓점 ○ 를 더 그린다. 고르기 카드는 false.
 */
@Composable
internal fun RoundTypeArt(type: Int, modifier: Modifier = Modifier.fillMaxSize(), showDatum: Boolean = false) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    val shape = remember(type) { RoundCalc.calc(sampleInput(type)).solution?.drawing }
    Canvas(modifier) {
        if (shape != null) ArtPen(this, c, measurer, sans, showDatum).draw(type, shape)
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

private class ArtPen(val d: DrawScope, val c: AppColors, val measurer: TextMeasurer, val sans: FontFamily, val showDatum: Boolean) {
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

    fun at(z: Double, x: Double) = Offset(x0 * w + ((z - zMin) * s).toFloat(), cy * h - (x * s).toFloat())

    fun draw(type: Int, shape: Contour) {
        zMin = Probe.points(shape).minOf { it.z }
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
        body(shape)
        if (showDatum) datum()
        // 치수는 견본 치수의 이론 꼭짓점(모서리 R 을 넣기 전)에 단다 — 길이 L·L1·L2 가 그 점 사이라서
        val i = sampleInput(type)
        fun onSphere(zc: Double, r: Double, rad: Double) = at(zc + r * cos(rad), r * sin(rad))
        when (type) {
            1 -> {
                val r = i.mainR!!
                val l = i.l1!!
                val face = sqrt(r * r - l * l / 4)
                leader("주 R", .582f, .124f, onSphere(-l / 2, r, 1.48))
                leader("모서리 R", .159f, .233f, at(-l, face))
                leader("모서리 R", .818f, .235f, at(0.0, face))
                hDim(0.0, -face, -l, -face, .857f, plain("L"))
                vertex(0.0, face, Pt(0.0, -1.0), along(-l / 2, 0.0, face, left = true))
                vertex(-l, face, Pt(0.0, -1.0), along(-l / 2, -l, face, left = false))
            }
            2 -> {
                val r = i.mainR!!
                val a = i.d1!! / 2
                val l1 = i.l1!!
                val half = sqrt(r * r - a * a)
                val zc = -l1 - half
                // 모서리 R(1) 은 위로 — D1 이 짧아 그 이름이 위쪽 끝 너머에 선다
                leader("주 R", .50f, .15f, onSphere(zc, r, 1.31), color = c.green)
                leader("모서리 R(1)", .75f, .14f, at(-l1, a))
                vDim(.826f, 0.0, a, "D1")
                hDim(0.0, -a, -l1, -a, .665f, plain("L1"))
                hDim(-l1, -a, zc - half, -a, .724f, letter("L2(", "B", c.blue))
                hDim(0.0, -a, zc - half, -a, .793f, letter("L2(", "A", c.red))
                vertex(-l1, a, Pt(1.0, 0.0), along(zc, -l1, a, left = true))
                vertex(zc - half, a, Pt(-1.0, 0.0), along(zc, zc - half, a, left = false))
            }
            3, 4 -> {
                val r = i.mainR!!
                val hh = i.d1!! / 2
                val joint = -(r + (if (type == 4) 1 else -1) * sqrt(r * r - hh * hh))
                vDim(if (type == 3) .163f else .115f, zMin, hh, "D")
                if (type == 3) {
                    leader("모서리 R", .718f, .163f, at(joint, hh))
                    leader("주 R", .83f, .317f, onSphere(-r, r, 0.87))
                    hDim(joint, -hh, 0.0, 0.0, .85f, plain("L"))
                } else {
                    leader("모서리 R", .377f, .278f, at(joint, hh))
                    leader("주 R", .85f, .336f, onSphere(-r, r, 0.70))
                    hDim(joint, -hh, 0.0, 0.0, .711f, plain("L"))
                }
                vertex(joint, hh, Pt(-1.0, 0.0), along(-r, joint, hh, left = false))
            }
            5 -> {
                val a = i.d1!! / 2
                val b = i.d2!! / 2
                val l1 = i.l1!!
                val l2 = i.l2!!
                val u = ((b * b - a * a) / l2 + l2) / 2
                val r = sqrt(u * u + a * a)
                vDim(.146f, zMin, b, "D2")
                // D1 은 짧아 이름이 위쪽 끝 너머에 서므로 오른쪽으로 비켜 두고, 모서리 R(1) 은 그 왼쪽
                vDim(.9f, 0.0, a, "D1")
                leader("모서리 R(2)", .623f, .133f, at(-l1 - l2, b))
                leader("주 R", .697f, .221f, onSphere(-l1 - u, r, PI / 4))
                leader("모서리 R(1)", .675f, .345f, at(-l1, a))
                hDim(0.0, -a, -l1, -a, .711f, plain("L1"))
                hDim(-l1, -a, -l1 - l2, -b, .839f, plain("L2"))
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
                vDim(.165f, zMin, b, "D2")
                vDim(.9f, 0.0, a, "D1")
                leader("모서리 R(2)", .628f, .146f, at(zf, hf))
                leader("주 R", .646f, .223f, onSphere(zc, r, PI / 4))
                leader("모서리 R(1)", .68f, .33f, at(-l1, a))
                hDim(0.0, -a, -l1, -a, .679f, plain("L1"))
                hDim(-l1, -a, zf, -hf, .829f, plain("L2"))
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
    private fun body(shape: Contour) {
        val upper = Probe.points(shape)
        val path = poly(upper + upper.asReversed().map { Pt(it.z, -it.x) }).apply { close() }
        val top = upper.maxOf { it.x }
        val y0 = at(0.0, top).y
        val y1 = at(0.0, -top).y
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

    private fun plain(s: String) = AnnotatedString(s)

    /** "L2(B)" 처럼 한 글자만 색을 바꾼 이름(04: B 파랑, A 빨강) */
    private fun letter(head: String, mark: String, color: Color) = buildAnnotatedString {
        append(head)
        withStyle(SpanStyle(color = color)) { append(mark) }
        append(")")
    }

    /**
     * 지시선 이름: halo 이름 + 밑줄 + 밑줄 끝에서 대상까지 꺾인 선 + 채운 화살(04). 이름 가운데 = 폭·높이 비율(lx, ly).
     * 밑줄은 대상에 가까운 쪽 끝에서 내려간다. 이름은 그림 칸 안(양옆 2dp)으로 민다
     */
    private fun leader(name: String, lx: Float, ly: Float, target: Offset, color: Color = ink) {
        val size = measurer.measure(name, style).size
        val pad = with(d) { 2.dp.toPx() }
        val tl = Offset((lx * w - size.width / 2f).coerceAtMost(w - size.width - pad).coerceAtLeast(pad), ly * h - size.height / 2f)
        d.haloText(measurer, plain(name), style.copy(color = color), c.card, ks, topLeft = tl)
        val y = tl.y + size.height - px(1.5f)
        val a = Offset(tl.x - px(1f), y)
        val b = Offset(tl.x + size.width + px(1f), y)
        d.drawLine(line, a, b, px(DrawSpec.leader))
        val from = if (abs(target.x - a.x) < abs(target.x - b.x)) a else b
        d.drawLine(line, from, target, px(DrawSpec.leader))
        d.arrowHead(target, target - from, line, px(DrawSpec.arrowLen))
    }

    /**
     * 가로 치수: (za, xa)·(zb, xb) 에서 내린 보조선 + 높이 비율 y 의 치수선(DrawKit: 짧으면 바깥 화살 · 이름은 오른쪽 끝 너머).
     * x 는 그림의 축 아래쪽 점(반경 음수)이라 보조선이 아래로 내려온다. 두 점 높이가 달라 보조선은 여기서 긋는다
     */
    private fun hDim(za: Double, xa: Double, zb: Double, xb: Double, y: Float, label: AnnotatedString) {
        val yy = y * h
        val (l, r) = listOf(at(za, xa), at(zb, xb)).sortedBy { it.x }
        for (p in listOf(l, r)) d.drawLine(line, Offset(p.x, p.y + px(DrawSpec.extGap)), Offset(p.x, yy + px(DrawSpec.extOver)), px(DrawSpec.ext))
        d.linearDim(Offset(l.x, yy), Offset(r.x, yy), 0f, label, line, ks, measurer, style, c.card)
    }

    /** 세로 치수(지름): z 의 윤곽 끝(±r)에서 가로 보조선 + 폭 비율 x 의 치수선(짧으면 바깥 화살 · 이름은 위쪽 끝 너머) */
    private fun vDim(x: Float, z: Double, r: Double, name: String) {
        val top = at(z, r)
        d.linearDim(at(z, -r), top, x * w - top.x, plain(name), line, ks, measurer, style, c.card)
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
