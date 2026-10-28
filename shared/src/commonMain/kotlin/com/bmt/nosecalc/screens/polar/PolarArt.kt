package com.bmt.nosecalc.screens.polar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarSolution
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 극좌표 평면 그림(원본 12 · 07): X 세로(위 +) · C 가로(왼쪽 +) 점선 축, 외경(내측은 기초 구멍) 점선 원, 도형 면 + 윤곽, W 치수.
 * 점은 계산기와 같은 Pt(z = −C, x = X 반경)(PolarG12.pt) — Z 오른쪽 · X 위로 그리면 원본 그림(C(+) 왼쪽)이고 Arc.cw = 화면 시계다.
 */

/**
 * 그림 자료. shape = 도형 윤곽(도면, 코너 R 그대로), stockR = 외경(내측은 기초 구멍) 반경, a = 중심에서 변까지(W/2),
 * normal0 = 첫 변의 바깥 법선(W 치수 방향), sample = 외경 · W 가운데 빠진 값을 예시 비율(원본 12 의 80 : 70)로 채워 그린 것.
 * wText = W 치수 이름('W 70' · 값이 없으면 'W'), odText = 원 이름('Ø80' · 없으면 null)
 */
internal class PolarGeom(
    val shape: List<Seg>,
    val stockR: Double,
    val inner: Boolean,
    val a: Double,
    val normal0: Pt,
    val odd: Boolean,
    val sample: Boolean,
    val wText: String,
    val odText: String?,
)

/** 받을 수 있는 크기의 양수인지(그림용) */
private fun ok(v: Double?): Double? = v?.takeIf { it.isFinite() && it > 0 && it <= MAX_MM }

/**
 * 화면 상태 → 그림 자료. 풀렸으면 계산기 단면(sol.shape) 그대로, 아니면 넣은 값으로 같은 규칙의 도형을 만들고(빠진 외경 · W 는 예시 비율),
 * 값이 서로 맞지 않아도(W ≥ 외경 등) 넣은 그대로 그린다 — 멈춘 까닭이 그림에 보이게
 */
internal fun polarGeom(s: PolarState, sol: PolarSolution?): PolarGeom {
    val wIn = ok(s.num(PolarField.WIDTH))
    val odIn = ok(s.num(PolarField.OD))
    val rot = (s.num(PolarField.ROTATION)?.takeIf { it.isFinite() && abs(it) <= MAX_MM } ?: 0.0) * PI / 180
    val w: Double
    val od: Double
    when {
        wIn != null && odIn != null -> { w = wIn; od = odIn }
        wIn != null -> { w = wIn; od = if (s.inner) w * SAMPLE_HOLE else w * SAMPLE_OD / SAMPLE_W }
        odIn != null -> { od = odIn; w = if (s.inner) od / SAMPLE_HOLE else od * SAMPLE_W / SAMPLE_OD }
        else -> { w = SAMPLE_W; od = if (s.inner) w * SAMPLE_HOLE else SAMPLE_OD }
    }
    val a = w / 2
    val rs = od / 2
    val rc = if (s.usesCornerR) s.num(PolarField.CORNER_R)?.takeIf { it.isFinite() && it >= 0 && it < a } ?: 0.0 else 0.0
    val shape = sol?.shape?.segs ?: if (s.shape == PolarShape.TWO_FLAT) twoFlatShape(a, rs, rot) else roundedPolygon(s.shape.sides, a, rc, rot)
    return PolarGeom(
        shape = shape,
        stockR = sol?.stockR ?: rs,
        inner = s.inner,
        a = a,
        normal0 = dirXC(PI / 2 + rot),
        odd = s.shape.sides % 2 == 1,
        sample = wIn == null || odIn == null,
        wText = if (wIn != null) "W ${plainNum(wIn)}" else "W",
        odText = odIn?.let { "Ø${plainNum(it)}" },
    )
}

/** 예시 비율(원본 12 의 외경 80 · 맞변 70), 내측 기초 구멍은 W 의 반 */
private const val SAMPLE_OD = 80.0
private const val SAMPLE_W = 70.0
private const val SAMPLE_HOLE = 0.5

/** X 에서 C(+) 쪽으로 잰 각 φ 의 단위 벡터(계산기와 같은 Pt(z = −C, x = X)) */
internal fun dirXC(phi: Double) = Pt(-sin(phi), cos(phi))

/**
 * 정다각형(변 n, 중심에서 변까지 a, 코너 R rc, 회전 rot — 계산기와 같은 규칙: 변 k 의 바깥 법선 = 90° + rot + k·360°/n)을 반시계로 한 바퀴.
 * 모서리 원 중심은 법선 k 와 k+1 사이 방향 (a − rc)/cos(π/n)
 */
internal fun roundedPolygon(n: Int, a: Double, rc: Double, rot: Double): List<Seg> {
    val half = PI / n
    fun normal(k: Int) = dirXC(PI / 2 + rot + 2 * PI * k / n)
    fun center(k: Int) = dirXC(PI / 2 + rot + 2 * PI * k / n + half) * ((a - rc) / cos(half))
    val out = mutableListOf<Seg>()
    var cur = center(n - 1) + normal(0) * rc
    for (k in 0 until n) {
        val cc = center(k)
        val t1 = cc + normal(k) * rc
        out += Line(cur, t1)
        cur = if (rc > 0) {
            val t2 = cc + normal(k + 1) * rc
            out += Arc(t1, t2, cc, cw = false)
            t2
        } else {
            t1
        }
    }
    return out
}

/** 2면폭: 법선 90° + rot 와 그 반대인 두 평면 + 소재 원호 둘(반시계). 평면이 소재에 닿지 않으면(a ≥ rs) 원만 */
internal fun twoFlatShape(a: Double, rs: Double, rot: Double): List<Seg> {
    if (a >= rs) return listOf(Arc(Pt(0.0, rs), Pt(0.0, -rs), Pt(0.0, 0.0), cw = false), Arc(Pt(0.0, -rs), Pt(0.0, rs), Pt(0.0, 0.0), cw = false))
    val te = sqrt(rs * rs - a * a)
    val out = mutableListOf<Seg>()
    for (j in 0..1) {
        val nj = dirXC(PI / 2 + rot + PI * j)
        val d = Pt(-nj.x, nj.z) // 진행 방향(법선을 반시계로 90°) — 반시계 둘레에서 바깥 법선은 진행의 오른쪽
        val p = nj * a - d * te
        val q = nj * a + d * te
        out += Line(p, q)
        val nNext = dirXC(PI / 2 + rot + PI * (j + 1))
        val dNext = Pt(-nNext.x, nNext.z)
        out += Arc(q, nNext * a - dNext * te, Pt(0.0, 0.0), cw = false)
    }
    return out
}

/** 그림 좌표: 모델 점 → 화면(가운데 o, 1 mm = k px). Z 오른쪽 · X 위 */
internal class PolarXf(val o: Offset, val k: Float) {
    fun of(p: Pt) = Offset(o.x + (p.z * k).toFloat(), o.y - (p.x * k).toFloat())
}

/** 윤곽을 화면 Path 로(원호는 Compose 의 시계 + 각) */
internal fun segPath(segs: List<Seg>, xf: PolarXf, close: Boolean = false): Path {
    val path = Path()
    segs.forEachIndexed { i, s ->
        val st = xf.of(s.start)
        if (i == 0) path.moveTo(st.x, st.y)
        when (s) {
            is Line -> xf.of(s.end).let { path.lineTo(it.x, it.y) }
            is Arc -> {
                val c = xf.of(s.center)
                val r = (s.radius * xf.k).toFloat()
                val start = atan2(st.y - c.y, st.x - c.x) * 180 / PI.toFloat()
                val sweep = s.sweepDeg.toFloat()
                if (sweep == 0f) {
                    xf.of(s.end).let { path.lineTo(it.x, it.y) }
                } else {
                    path.arcTo(Rect(c, r), start, if (s.cw) sweep else -sweep, false)
                }
            }
        }
    }
    if (close) path.close()
    return path
}

/** 윤곽이 닿는 가장 먼 거리(중심에서, mm) — 원호는 중심 거리 + 반지름으로 넉넉히 */
internal fun reach(segs: List<Seg>): Double = segs.maxOfOrNull { s ->
    when (s) {
        is Line -> max(s.start.length, s.end.length)
        is Arc -> min(s.center.length + s.radius, max(s.start.length, s.end.length) + s.radius)
    }
} ?: 0.0

/** 점선 축 둘(X 세로 · C 가로)과 끝 이름 X(+) · X(−) · C(+) · C(−) — 원본 12 · 07 */
internal fun DrawScope.polarAxes(xf: PolarXf, half: Float, measurer: TextMeasurer, c: AppColors, ks: Float, kt: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx() * ks, 4.dp.toPx() * ks))
    val w = 1.dp.toPx() * ks
    val o = xf.o
    drawLine(c.viewportAxis, Offset(o.x, o.y - half), Offset(o.x, o.y + half), w, pathEffect = dash)
    drawLine(c.viewportAxis, Offset(o.x - half, o.y), Offset(o.x + half, o.y), w, pathEffect = dash)
    val style = TextStyle(color = c.textMuted, fontSize = (11 * kt).sp, fontWeight = FontWeight.SemiBold)
    fun label(text: String, at: Offset, ax: Float, ay: Float) {
        val l = measurer.measure(text, style)
        drawText(l, topLeft = Offset(at.x - l.size.width * ax, at.y - l.size.height * ay))
    }
    val g = 3.dp.toPx()
    label("X(+)", Offset(o.x + g, o.y - half), 0f, 0f)
    label("X(−)", Offset(o.x + g, o.y + half), 0f, 1f)
    label("C(+)", Offset(o.x - half, o.y - g), 0f, 1f)
    label("C(−)", Offset(o.x + half, o.y - g), 1f, 1f)
}

/**
 * 도형 · 외경(구멍) 원 · 면을 그린다. 외측 = 도형 면(청록 옅게) + 윤곽(청록) + 외경 점선 원(호박색),
 * 내측 = 소재 덩어리(면 + 빗금)에서 다각형 구멍을 뺀 단면 + 윤곽 + 기초 구멍 점선 원. focusOd = 키패드가 고치는 칸이 외경이면 원을 초점색으로
 */
internal fun DrawScope.polarShape(g: PolarGeom, xf: PolarXf, c: AppColors, ks: Float, focusOd: Boolean = false) {
    val outline = segPath(g.shape, xf, close = true)
    val r = (g.stockR * xf.k).toFloat()
    val circleColor = if (focusOd) c.lineFocus else c.amber
    val dashed = Stroke(2.dp.toPx() * ks, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx() * ks, 5.dp.toPx() * ks)))
    if (g.inner) {
        val block = Path().apply {
            addOval(Rect(xf.o, (max(reach(g.shape), g.stockR) * 1.22 * xf.k).toFloat()))
            addPath(outline)
            fillType = PathFillType.EvenOdd
        }
        hatchFill(block, c.drawStock, c.drawHatch, ks)
        drawPath(outline, c.teal.copy(alpha = if (c.dark) .10f else .06f))
    } else {
        drawPath(outline, c.teal.copy(alpha = if (c.dark) .18f else .13f))
    }
    drawCircle(circleColor, r, xf.o, style = dashed)
    drawPath(outline, c.teal, style = Stroke(2.4.dp.toPx() * ks, join = StrokeJoin.Round))
}

/**
 * W 치수: 첫 변의 법선을 따라 중심을 지나는 치수선(양 끝 채운 화살) + 가운데 이름. 홀수 각(3 · 5)은 맞은편이 변이 아니라 내접원(가는 점선)까지.
 * 외경 · 구멍 이름은 원의 오른쪽 위 바깥
 */
internal fun DrawScope.polarDims(g: PolarGeom, xf: PolarXf, measurer: TextMeasurer, c: AppColors, ks: Float, kt: Float, focusW: Boolean = false) {
    val a = xf.of(g.normal0 * g.a)
    val b = xf.of(g.normal0 * -g.a)
    if (g.odd) {
        drawCircle(c.drawAngle.copy(alpha = .55f), (g.a * xf.k).toFloat(), xf.o, style = Stroke(1.dp.toPx() * ks, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
    }
    val color = if (focusW) c.lineFocus else c.drawAngle
    linearDim(
        a, b, 0f, AnnotatedString(g.wText), color, ks, measurer,
        TextStyle(color = if (focusW) c.lineFocus else c.textStrong, fontSize = (14 * kt).sp, fontWeight = FontWeight.Bold),
        halo = c.card, pill = true, centerIfFits = true,
    )
    val od = g.odText ?: return
    val style = TextStyle(color = c.viewLabelT1, fontSize = (12 * kt).sp, fontWeight = FontWeight.Bold)
    val l = measurer.measure(od, style)
    val r = (g.stockR * xf.k).toFloat()
    val d = 0.7071f
    // 원의 오른쪽 위 바깥(내측 구멍도 바깥 — 구멍 안은 W 이름 자리라 겹쳤다)
    drawText(l, topLeft = Offset(xf.o.x + r * d + 3.dp.toPx(), xf.o.y - r * d - l.size.height))
}

/** 그림 상자에 맞춘 좌표: 그릴 것의 반경 reachMm 이 상자 반폭 − margin 에 든다 */
internal fun DrawScope.fitXf(reachMm: Double, margin: Float): PolarXf {
    val half = min(size.width, size.height) / 2 - margin
    return PolarXf(Offset(size.width / 2, size.height / 2), (half / max(reachMm, 1e-6)).toFloat())
}

/**
 * 입력 화면의 형상 뷰어(원본 12): 축 · 외경(구멍) 원 · 도형 · W 치수. 값을 바꾸면 바로 다시 그린다. focus = 키패드가 고치는 칸(외경 · W 를 초점색으로)
 */
@Composable
internal fun PolarFigure(g: PolarGeom, modifier: Modifier = Modifier, focus: PolarField? = null) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val (ks, kt) = artScale(size.width, 340.dp)
        val margin = 26.dp.toPx() * kt
        val shapeReach = reach(g.shape)
        val all = max(shapeReach, g.stockR) * (if (g.inner) 1.22 else 1.0)
        val xf = fitXf(all, margin)
        val half = min(size.width, size.height) / 2 - 4.dp.toPx()
        if (g.inner) {
            polarShape(g, xf, c, ks, focusOd = focus == PolarField.OD)
            polarAxes(xf, half, measurer, c, ks, kt)
        } else {
            polarAxes(xf, half, measurer, c, ks, kt)
            polarShape(g, xf, c, ks, focusOd = focus == PolarField.OD)
        }
        polarDims(g, xf, measurer, c, ks, kt, focusW = focus == PolarField.WIDTH)
    }
}
