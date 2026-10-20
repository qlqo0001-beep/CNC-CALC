package com.bmt.nosecalc.screens.triangle

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.TriangleResult
import com.bmt.nosecalc.ui.DrawSpec
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** 그림 높이 = 위(꼭짓점 B 칸) + 삼각형 높이 상한 + 아래(밑변 치수). 밑변 자리가 늘 같아 꼭짓점 B 만 움직인다 */
private val TOP = 64.dp
private val TRI_H = 170.dp
private val BOTTOM = 56.dp

/** 칸 안 여백(가로 · 세로) · 칸이 윤곽 · 보조선에서 띄우는 틈 */
private val PAD_X = 8.dp
private val PAD_Y = 5.dp
private val GAP = 6.dp

/**
 * 직각삼각형 그림: 왼쪽 아래 각 A, 오른쪽 아래 직각, 오른쪽 위 각 B. 높이는 290dp 로 고정(아래 칸이 들썩이지 않게).
 * 값은 두 줄 칸(이름 · 값)으로 적는다 — 넣은 값 = 보라 칸, 계산값 = 흰 칸, 모르면 ?. 밑변 · 높이 칸 이름에 Z · X 를 함께 적는다.
 * 변 a · b · c 는 치수선(DrawKit) 가운데에 칸을 얹어 칸 바탕이 선을 끊고, 각 A · B 칸은 꼭짓점 바깥 이등분선 위에 두고
 * 꼭짓점을 지나 호 가운데까지 지시선을 긋는다. 칸 자리는 칸 크기에서 정해 윤곽 · 보조선과 겹치지 않는다.
 * 모양은 각 A 를 따르되 22°~58° 로 눌러 그리고, 눌렀으면 왼쪽 위에 "그림 비율 아님" 과 함께 호를 점선으로.
 * 못 풀면 삼각형을 흐리게 하고 모자란 칸(need)을 점선 테로 짚는다: 값 부족 = 파랑 안내, 각도 둘뿐 = 빨강 "변 하나 필요".
 * 서로 안 맞으면 넣은 칸에 빨강 테.
 */
@Composable
internal fun TriangleFigure(s: TriangleState, result: TriangleResult, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val accent = c.features.getValue(Feature.TRIANGLE).menu
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    val mono = AppTheme.mono
    val sol = result.solution
    val w = result.warnings.firstOrNull()
    val need = s.need(w)
    val wrong = s.wrong(w)
    val real = sol?.angleA ?: 30.0
    val drawn = real.coerceIn(22.0, 58.0)
    val t = (drawn * PI / 180).toFloat()
    Canvas(modifier.fillMaxWidth().height(TOP + TRI_H + BOTTOM)) {
        val gap = GAP.toPx()
        val ink = if (sol != null) accent else c.textHint
        val line = if (sol != null) c.textBody else c.textHint

        // 값 칸 다섯을 먼저 잰다(자리는 칸 크기에서 정한다)
        fun chip(f: TriField): Chip {
            val given = s.given(f)
            val red = f in wrong
            val name = when {
                red && !given -> "변 하나 필요"
                f == TriField.A_SIDE -> "${f.label} · ${if (s.baseIsZ) "Z" else "X(반경)"}"
                f == TriField.B_SIDE -> "${f.label} · ${if (s.baseIsZ) "X(반경)" else "Z"}"
                else -> f.label
            }
            val deg = if (f.unit == "°") "°" else ""
            val (value, valueInk) = when {
                given -> s.value(f) + deg to accent
                sol != null -> Fmt.fixed3(f.of(sol)) + deg to c.valueComp
                red -> "?" to c.error.label
                f in need -> "?" to c.info.label
                else -> "?" to c.textHint
            }
            val nameInk = if (given) accent else if (red) c.error.label else c.textMuted
            return Chip(
                measurer.measure(name, TextStyle(fontFamily = sans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = nameInk)),
                measurer.measure(value, TextStyle(fontFamily = mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueInk)),
                if (given) accent.copy(alpha = if (c.dark) .22f else .12f).compositeOver(c.card) else c.card,
                when {
                    red -> c.error.label
                    f in need -> c.info.label
                    given -> accent
                    c.dark -> c.lineStrong
                    else -> c.lineField
                },
                (if (given || f in need || red) 1.6 else 1.2).dp.toPx(),
                dash = f in need,
                pad = Offset(PAD_X.toPx(), PAD_Y.toPx()),
            )
        }
        val chips = TriField.entries.associateWith(::chip)
        val sa = chips.getValue(TriField.A_SIDE).size
        val sb = chips.getValue(TriField.B_SIDE).size
        val sc = chips.getValue(TriField.C_SIDE).size
        val sA = chips.getValue(TriField.ANGLE_A).size
        val sB = chips.getValue(TriField.ANGLE_B).size

        // 각 칸: 꼭짓점 바깥 이등분선 eA · eB 위, 그 꼭짓점의 두 보조선에서 gap 만큼 떨어지게.
        // A = 밑변 보조선(아래로) · 빗변 보조선(왼쪽 위로) 사이, B = 높이 보조선(오른쪽으로) · 빗변 보조선 사이
        val ct = cos(t)
        val st = sin(t)
        val eA = Offset(-cos(t / 2), sin(t / 2))
        val dA = (max(sA.width / 2, sA.width / 2 * ct + sA.height / 2 * st) + gap) / cos(t / 2)
        val q = PI.toFloat() / 4 + t / 2
        val eB = Offset(cos(q), -sin(q))
        val dB = (max(sB.height / 2, sB.width / 2 * ct + sB.height / 2 * st) + gap) / sin(q)
        // 양옆 여백 = 왼쪽 각 A 칸, 오른쪽 높이 칸(높이 치수는 칸 폭의 반 + gap 바깥) 또는 각 B 칸. 밑변은 높이가 170dp 를 넘지 않는 데까지
        val left = dA * cos(t / 2) + sA.width / 2
        val right = max(sb.width + gap, dB * eB.x + sB.width / 2)
        val room = size.width - left - right
        val base = min(room, TRI_H.toPx() / tan(t))
        val a = Offset(left + (room - base) / 2, (TOP + TRI_H).toPx())
        val rc = Offset(a.x + base, a.y)
        val b = Offset(rc.x, rc.y - base * tan(t))

        val tri = Path().apply {
            moveTo(a.x, a.y)
            lineTo(rc.x, rc.y)
            lineTo(b.x, b.y)
            close()
        }
        drawPath(tri, ink.copy(alpha = if (sol != null) .07f else .06f))
        drawPath(tri, ink, style = Stroke((if (sol != null) DrawSpec.profile else 2.dp).toPx(), join = StrokeJoin.Round))
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
        // 각 A(밑변–빗변), 각 B(높이–빗변). 화면 각은 시계 방향이 +. 비율이 아니면 점선
        val deg = drawn.toFloat()
        val ra = 34.dp.toPx()
        val rb = 26.dp.toPx()
        val arc = Stroke(1.4.dp.toPx(), pathEffect = if (drawn != real) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null)
        drawArc(ink, -deg, deg, false, a - Offset(ra, ra), Size(2 * ra, 2 * ra), style = arc)
        drawArc(ink, 90f, 90f - deg, false, b - Offset(rb, rb), Size(2 * rb, 2 * rb), style = arc)
        val letter = TextStyle(fontFamily = sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sol != null) accent else c.textMuted)
        fun mark(text: String, at: Offset) {
            val l = measurer.measure(text, letter)
            drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
        }
        mark("A", a - eA * (ra + 10.dp.toPx()))
        mark("B", b - eB * (rb + 9.dp.toPx()))

        // 변: 치수선(이름은 칸이 대신하므로 비운다) 가운데에 칸. 칸이 윤곽에서 gap 만큼 떨어지게 띄우고, c 는 빗변 바깥(왼쪽 위)으로
        val none = AnnotatedString("")
        val dimStyle = TextStyle(fontSize = 11.sp)
        fun dim(f: TriField, p: Offset, r: Offset, off: Float) =
            drawChip(chips.getValue(f), linearDim(p, r, off, none, line, 1f, measurer, dimStyle, c.card).center)
        dim(TriField.A_SIDE, a, rc, sa.height / 2 + gap)
        dim(TriField.B_SIDE, rc, b, sb.width / 2 + gap)
        dim(TriField.C_SIDE, a, b, -(sc.width / 2 * st + sc.height / 2 * ct + gap))
        // 각: 칸 → 꼭짓점 → 호 가운데 지시선
        fun angle(f: TriField, v: Offset, e: Offset, d: Float, r: Float) {
            val box = drawChip(chips.getValue(f), v + e * d)
            drawLine(line, box.edgeToward(v), v - e * r, DrawSpec.leader.toPx())
        }
        angle(TriField.ANGLE_A, a, eA, dA, ra)
        angle(TriField.ANGLE_B, b, eB, dB, rb)

        if (drawn != real) {
            val note = measurer.measure(
                "그림 비율 아님 (실제 A ${Fmt.fixed3(real)}°)",
                TextStyle(fontFamily = sans, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = c.textMuted),
            )
            drawText(note, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()))
        }
    }
}

/** 값 칸 하나: 윗줄 이름(11sp) · 아랫줄 값(15sp 고정폭), 가운데 맞춤. dash = 점선 테(모자란 값) */
private class Chip(
    val name: TextLayoutResult,
    val value: TextLayoutResult,
    val fill: Color,
    val border: Color,
    val width: Float,
    val dash: Boolean,
    val pad: Offset,
) {
    val size = Size(max(name.size.width, value.size.width) + 2 * pad.x, name.size.height + value.size.height + 2 * pad.y)
}

/** 칸을 center 에 그린다(그림 칸 안으로 민다 — 칸이 더 크면 왼쪽 위에 맞춘다). 바탕은 불투명이라 밑의 치수선을 끊는다. 그린 칸을 돌려준다 */
private fun DrawScope.drawChip(chip: Chip, center: Offset): Rect {
    val sz = chip.size
    val tl = Offset(
        (center.x - sz.width / 2).coerceAtMost(size.width - sz.width).coerceAtLeast(0f),
        (center.y - sz.height / 2).coerceAtMost(size.height - sz.height).coerceAtLeast(0f),
    )
    val corner = CornerRadius(8.dp.toPx())
    val dash = if (chip.dash) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
    drawRoundRect(chip.fill, tl, sz, corner)
    drawRoundRect(chip.border, tl, sz, corner, style = Stroke(chip.width, pathEffect = dash))
    drawText(chip.name, topLeft = tl + Offset((sz.width - chip.name.size.width) / 2, chip.pad.y))
    drawText(chip.value, topLeft = tl + Offset((sz.width - chip.value.size.width) / 2, chip.pad.y + chip.name.size.height))
    return Rect(tl, sz)
}

/** 칸 가운데에서 p 쪽으로 가다 칸 테두리를 나서는 점 */
private fun Rect.edgeToward(p: Offset): Offset {
    val d = p - center
    return center + d * min(width / 2 / abs(d.x), height / 2 / abs(d.y))
}
