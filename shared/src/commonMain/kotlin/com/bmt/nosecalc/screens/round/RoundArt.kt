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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
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
 * 견본 치수는 04 그림의 비율을 잰 값이다. 자리·글자는 04 의 카드 몸통(193 × 189dp)에 대한 비율이라 작은 칸에서도 같은 모양이다.
 */
@Composable
internal fun RoundTypeArt(type: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    val shape = remember(type) { RoundCalc.calc(sampleInput(type)).solution?.drawing }
    Canvas(modifier) {
        if (shape != null) ArtPen(this, c, measurer, sans).draw(type, shape)
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

private class ArtPen(val d: DrawScope, val c: AppColors, val measurer: TextMeasurer, val sans: FontFamily) {
    val w = d.size.width
    val h = d.size.height

    /** 04 카드 몸통 폭 193dp 에 대한 배율 */
    val k = w / with(d) { 193.dp.toPx() }
    val ink = c.textValue

    fun dp(v: Float) = with(d) { (v * k).dp.toPx() }

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
            }
            2 -> {
                val r = i.mainR!!
                val a = i.d1!! / 2
                val l1 = i.l1!!
                val half = sqrt(r * r - a * a)
                val zc = -l1 - half
                leader("주 R", .609f, .199f, onSphere(zc, r, 1.31), ink = c.green)
                leader("모서리 R(1)", .824f, .306f, at(-l1, a))
                vDim(.826f, 0.0, a, "D1")
                hDim(0.0, -a, -l1, -a, .665f, plain("L1"))
                hDim(-l1, -a, zc - half, -a, .724f, letter("L2(", "B", c.blue))
                hDim(0.0, -a, zc - half, -a, .793f, letter("L2(", "A", c.red))
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
            }
            5 -> {
                val a = i.d1!! / 2
                val b = i.d2!! / 2
                val l1 = i.l1!!
                val l2 = i.l2!!
                val u = ((b * b - a * a) / l2 + l2) / 2
                val r = sqrt(u * u + a * a)
                vDim(.146f, zMin, b, "D2")
                vDim(.734f, 0.0, a, "D1")
                leader("모서리 R(2)", .623f, .133f, at(-l1 - l2, b))
                leader("주 R", .697f, .221f, onSphere(-l1 - u, r, PI / 4))
                leader("모서리 R(1)", .79f, .353f, at(-l1, a))
                hDim(0.0, -a, -l1, -a, .711f, plain("L1"))
                hDim(-l1, -a, -l1 - l2, -b, .839f, plain("L2"))
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
                vDim(.718f, 0.0, a, "D1")
                leader("모서리 R(2)", .628f, .146f, at(zf, hf))
                leader("주 R", .646f, .223f, onSphere(zc, r, PI / 4))
                leader("모서리 R(1)", .77f, .319f, at(-l1, a))
                hDim(0.0, -a, -l1, -a, .679f, plain("L1"))
                hDim(-l1, -a, zf, -hf, .829f, plain("L2"))
            }
        }
    }

    /** 몸통: 윤곽 + 축 아래로 접은 윤곽을 금속 그라데이션으로 칠하고, 요소 경계마다 옅은 세로 모서리선 */
    private fun body(shape: Contour) {
        val upper = Probe.points(shape)
        val end = upper.last()
        val path = Path().apply {
            upper.forEachIndexed { i, p -> at(p.z, p.x).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            at(end.z, -end.x).let { lineTo(it.x, it.y) }
            upper.asReversed().forEach { p -> at(p.z, -p.x).let { lineTo(it.x, it.y) } }
            close()
        }
        val top = upper.maxOf { it.x }
        val y0 = at(0.0, top).y
        val y1 = at(0.0, -top).y
        val metal = c.drawMetal
        d.drawPath(path, Brush.verticalGradient(0f to metal[0], .28f to metal[1], .5f to metal[2], .78f to metal[3], 1f to metal[4], startY = y0, endY = y1))
        // 모서리선: 요소가 꺾이는 곳(접해서 이어지는 곳은 빼지 않는다 — 04 의 렌더도 이음매가 보인다)
        for (seg in shape.segs.dropLast(1)) {
            val p = seg.end
            if (p.x > 1e-6) d.drawLine(c.drawMetalEdge, at(p.z, p.x), at(p.z, -p.x), dp(0.7f))
        }
        d.drawPath(path, c.drawMetalEdge, style = Stroke(dp(1.1f), join = StrokeJoin.Round))
        // 중심선(1점 쇄선)
        val l = at(zMin, 0.0) - Offset(dp(6f), 0f)
        val r = at(0.0, 0.0) + Offset(dp(6f), 0f)
        d.drawLine(
            c.drawRef.copy(alpha = .55f), l, r, dp(0.6f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp(7f), dp(2f), dp(1.5f), dp(2f))),
        )
    }

    private fun style(color: Color = ink) = TextStyle(fontFamily = sans, fontSize = (10.5f * k).sp, color = color, letterSpacing = 0.sp)

    private fun plain(s: String) = AnnotatedString(s)

    /** "L2(B)" 처럼 한 글자만 색을 바꾼 이름(04: B 파랑, A 빨강) */
    private fun letter(head: String, mark: String, color: Color) = buildAnnotatedString {
        append(head)
        withStyle(SpanStyle(color = color)) { append(mark) }
        append(")")
    }

    /**
     * 지시선 이름: 이름 밑줄 + 밑줄 끝에서 대상까지 꺾인 선(04). 이름 가운데 = 폭·높이 비율(lx, ly).
     * 밑줄은 대상에 가까운 쪽 끝에서 내려간다.
     */
    private fun leader(name: String, lx: Float, ly: Float, target: Offset, ink: Color = this.ink) {
        val layout = measurer.measure(name, style(ink))
        val tl = Offset(lx * w - layout.size.width / 2f, ly * h - layout.size.height / 2f)
        d.drawText(layout, topLeft = tl)
        val y = tl.y + layout.size.height - dp(1.5f)
        val a = Offset(tl.x - dp(1f), y)
        val b = Offset(tl.x + layout.size.width + dp(1f), y)
        d.drawLine(this.ink, a, b, dp(0.7f))
        val from = if (abs(target.x - a.x) < abs(target.x - b.x)) a else b
        d.drawLine(this.ink, from, target, dp(0.7f))
        arrow(target, target - from)
    }

    /** 끝이 tip 인 화살촉. dir = 화살 방향(tip 쪽으로) */
    private fun arrow(tip: Offset, dir: Offset) {
        val len = dir.getDistance()
        if (len < 1e-3f) return
        val u = dir / len
        val n = Offset(-u.y, u.x)
        val head = dp(4.5f)
        val base = tip - u * head
        d.drawPath(
            Path().apply {
                moveTo(tip.x, tip.y)
                (base + n * (head * .32f)).let { lineTo(it.x, it.y) }
                (base - n * (head * .32f)).let { lineTo(it.x, it.y) }
                close()
            },
            ink,
        )
    }

    /**
     * 가로 치수: (za, xa)·(zb, xb) 에서 내린 보조선 + 높이 비율 y 의 치수선(양쪽 화살) + 가운데 위 이름.
     * x 는 그림의 축 아래쪽 점(반경 음수)이라 보조선이 아래로 내려온다.
     */
    private fun hDim(za: Double, xa: Double, zb: Double, xb: Double, y: Float, label: AnnotatedString) {
        val pa = at(za, xa)
        val pb = at(zb, xb)
        val yy = y * h
        val gap = dp(2.5f)
        val over = dp(3.5f)
        d.drawLine(ink, Offset(pa.x, pa.y + gap), Offset(pa.x, yy + over), dp(0.6f))
        d.drawLine(ink, Offset(pb.x, pb.y + gap), Offset(pb.x, yy + over), dp(0.6f))
        val l = Offset(minOf(pa.x, pb.x), yy)
        val r = Offset(maxOf(pa.x, pb.x), yy)
        d.drawLine(ink, l, r, dp(0.7f))
        arrow(l, l - r)
        arrow(r, r - l)
        val layout = measurer.measure(label, style())
        d.drawText(layout, topLeft = Offset((l.x + r.x) / 2 - layout.size.width / 2f, yy - layout.size.height + dp(1.5f)))
    }

    /** 세로 치수(지름): z 의 윤곽 끝(±r)에서 가로 보조선 + 폭 비율 x 의 치수선 + 왼쪽에 90° 돌린 이름 */
    private fun vDim(x: Float, z: Double, r: Double, name: String) {
        val xx = x * w
        val top = at(z, r)
        val bot = at(z, -r)
        val gap = dp(2.5f)
        val over = dp(3.5f)
        val dir = if (xx > top.x) 1f else -1f
        d.drawLine(ink, Offset(top.x + dir * gap, top.y), Offset(xx + dir * over, top.y), dp(0.6f))
        d.drawLine(ink, Offset(bot.x + dir * gap, bot.y), Offset(xx + dir * over, bot.y), dp(0.6f))
        val t = Offset(xx, top.y)
        val b = Offset(xx, bot.y)
        d.drawLine(ink, t, b, dp(0.7f))
        arrow(t, t - b)
        arrow(b, b - t)
        val layout = measurer.measure(name, style())
        val center = Offset(xx - dp(1.5f) - layout.size.height / 2f, (t.y + b.y) / 2)
        d.rotate(-90f, center) {
            drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
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
