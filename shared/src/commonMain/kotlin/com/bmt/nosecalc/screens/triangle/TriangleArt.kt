package com.bmt.nosecalc.screens.triangle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.TriangleSolution
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * 직각삼각형 그림: 왼쪽 아래 각 A, 오른쪽 아래 직각, 오른쪽 위 각 B. 값은 제자리 칸에 적는다
 * (넣은 값 = 보라 칸, 계산값 = 흰 칸, 모르면 ?). 모양은 각 A 를 따르되 22°~58° 로 눌러 그린다(너무 납작하거나 가파르면 칸이 겹쳐서).
 * 왼쪽 위 화살 두 개 = 밑변·높이가 Z · X 가운데 어느 쪽인지.
 */
@Composable
internal fun TriangleFigure(s: TriangleState, sol: TriangleSolution?, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val accent = c.features.getValue(Feature.TRIANGLE).menu
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    val mono = AppTheme.mono
    val t = (sol?.angleA ?: 30.0).coerceIn(22.0, 58.0) * PI / 180
    // 둘레 여백: 왼쪽 = 각 A 칸, 오른쪽 = 높이·각 B 칸, 위 = 축 화살, 아래 = 밑변 칸. 그림 높이는 삼각형에 맞춘다
    val left = 92.dp
    val right = 104.dp
    val top = 36.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val baseDp = min((maxWidth - left - right).value.toDouble(), 170 / tan(t))
        val heightDp = (baseDp * tan(t)).toFloat()
        Canvas(Modifier.fillMaxWidth().height(top + heightDp.dp + 40.dp)) {
            val base = baseDp.toFloat().dp.toPx()
            val height = heightDp.dp.toPx()
            val w = size.width - left.toPx() - right.toPx()
            val a = Offset(left.toPx() + (w - base) / 2, top.toPx() + height)
            val rc = Offset(a.x + base, a.y)
            val b = Offset(rc.x, rc.y - height)

            val tri = Path().apply {
                moveTo(a.x, a.y)
                lineTo(rc.x, rc.y)
                lineTo(b.x, b.y)
                close()
            }
            drawPath(tri, accent.copy(alpha = .07f))
            drawPath(tri, accent, style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))
            // 직각 표시
            val m = 13.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(rc.x - m, rc.y)
                    lineTo(rc.x - m, rc.y - m)
                    lineTo(rc.x, rc.y - m)
                },
                c.textMuted, style = Stroke(1.2.dp.toPx()),
            )
            // 각 A(밑변–빗변), 각 B(높이–빗변). 화면 각은 시계 방향이 +
            val deg = (t * 180 / PI).toFloat()
            val ra = 34.dp.toPx()
            val rb = 26.dp.toPx()
            drawArc(accent, -deg, deg, false, a - Offset(ra, ra), Size(2 * ra, 2 * ra), style = Stroke(1.4.dp.toPx()))
            drawArc(accent, 90f, 90f - deg, false, b - Offset(rb, rb), Size(2 * rb, 2 * rb), style = Stroke(1.4.dp.toPx()))
            val letter = TextStyle(fontFamily = sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            fun mark(text: String, at: Offset) {
                val l = measurer.measure(text, letter)
                drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            mark("A", a + Offset(cos(t / 2).toFloat(), -sin(t / 2).toFloat()) * (ra + 10.dp.toPx()))
            val half = (PI / 2 + (PI / 2 - t) / 2)
            mark("B", b + Offset(cos(half).toFloat(), sin(half).toFloat()) * (rb + 9.dp.toPx()))

            // 값 칸
            val gap = 8.dp.toPx()
            fun chip(f: TriField, place: (Size) -> Offset): Rect {
                val given = s.given(f)
                val v = when {
                    given -> s.value(f)
                    sol != null -> Fmt.fixed3(f.of(sol))
                    else -> "?"
                } + if (f.unit == "°" && (given || sol != null)) "°" else ""
                val text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = sans, fontSize = 12.5.sp, color = if (given) accent else c.textMuted)) { append(f.sym) }
                    append("  ")
                    withStyle(SpanStyle(fontFamily = mono, fontSize = 14.sp, color = if (given) accent else if (sol != null) c.valueComp else c.textHint)) { append(v) }
                }
                val l = measurer.measure(text, TextStyle(fontWeight = FontWeight.Bold))
                val pad = Offset(8.dp.toPx(), 4.dp.toPx())
                val sz = Size(l.size.width + 2 * pad.x, l.size.height + 2 * pad.y)
                val p = place(sz)
                val tl = Offset(p.x.coerceIn(0f, size.width - sz.width), p.y.coerceIn(0f, size.height - sz.height))
                val corner = CornerRadius(7.dp.toPx())
                drawRoundRect(if (given) accent.copy(alpha = if (c.dark) .22f else .12f) else c.card, tl, sz, corner)
                drawRoundRect(if (given) accent else c.lineField, tl, sz, corner, style = Stroke((if (given) 1.6 else 1.2).dp.toPx()))
                drawText(l, topLeft = tl + pad)
                return Rect(tl, sz)
            }
            chip(TriField.A_SIDE) { Offset((a.x + rc.x) / 2 - it.width / 2, a.y + gap) }
            val bChip = chip(TriField.ANGLE_B) { Offset(b.x + gap, b.y - 4.dp.toPx()) }
            chip(TriField.B_SIDE) { Offset(rc.x + gap, maxOf((b.y + rc.y) / 2 - it.height / 2, bChip.bottom + 6.dp.toPx())) }
            chip(TriField.ANGLE_A) { Offset(a.x - gap - it.width, a.y - it.height - 2.dp.toPx()) }
            // 빗변 칸: 가운데에서 바깥(왼쪽 위) 법선으로, 칸 모서리가 선에 닿지 않을 만큼
            val n = Offset(-sin(t).toFloat(), -cos(t).toFloat())
            chip(TriField.C_SIDE) {
                val d = it.width / 2 * abs(n.x) + it.height / 2 * abs(n.y) + 6.dp.toPx()
                (a + b) / 2f + n * d - Offset(it.width / 2, it.height / 2)
            }

            axes(s.baseIsZ, c.textMuted, measurer, sans)
        }
    }
}

/** 왼쪽 위: 오른쪽 화살 = 밑변 방향, 위 화살 = 높이 방향, 끝에 축 이름 */
private fun DrawScope.axes(baseIsZ: Boolean, ink: Color, measurer: TextMeasurer, sans: FontFamily) {
    val o = Offset(14.dp.toPx(), 46.dp.toPx())
    val len = 28.dp.toPx()
    val head = 5.dp.toPx()
    val stroke = 1.4.dp.toPx()
    val style = TextStyle(fontFamily = sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink)
    fun arrow(dir: Offset, name: String) {
        val tip = o + dir * len
        drawLine(ink, o, tip, stroke)
        val n = Offset(-dir.y, dir.x)
        drawPath(
            Path().apply {
                moveTo(tip.x + dir.x * 1.5f, tip.y + dir.y * 1.5f)
                (tip - dir * head + n * (head * .45f)).let { lineTo(it.x, it.y) }
                (tip - dir * head - n * (head * .45f)).let { lineTo(it.x, it.y) }
                close()
            },
            ink,
        )
        val l = measurer.measure(name, style)
        val at = if (dir.x > 0) tip + Offset(5.dp.toPx() + l.size.width / 2f, 0f) else tip - Offset(0f, 2.dp.toPx() + l.size.height / 2f)
        drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
    }
    arrow(Offset(1f, 0f), if (baseIsZ) "Z" else "X")
    arrow(Offset(0f, -1f), if (baseIsZ) "X" else "Z")
}
