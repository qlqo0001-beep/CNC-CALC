package com.bmt.nosecalc.screens.thread

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeSolution
import com.bmt.nosecalc.calc.PipeSpec
import com.bmt.nosecalc.ui.DrawSpec
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 나사 단면 그림의 자료(mm, 반경). k = 산봉우리에서 골(날 끝)까지, crestToPitch = 산봉우리에서 피치선까지,
 * depths = 거친 절입마다 산봉우리에서 잰 누적 깊이(가는 선). 이름(name)과 값(value)은 따로 — 값은 고정폭으로 쓴다(값이 없으면 "")
 */
internal data class SectionGeom(
    val p: Double,
    val k: Double,
    val crestToPitch: Double,
    val tipR: Double,
    val depths: List<Double>,
    val inner: Boolean,
    val crest: Pair<String, String>,
    val pitch: Pair<String, String>,
    val root: Pair<String, String>,
    val kText: String,
    val pText: String,
    val rText: String,
    /** 관용 테이퍼의 단면 모양 — null = 곧은 나사(60° 날 · 끝 R 홈, 산봉우리 평면) */
    val profile: PipeProfile? = null,
)

/**
 * 관용 테이퍼 나사의 축 단면(풀 프로파일 인서트가 산봉우리까지 함께 만든다고 본다 — calc-design.md '관용 테이퍼 나사'): 나사산 각, 산봉우리 · 골 둥글기
 * round(PT r — JIS B 0203 의 0.137278P), null 이면 평탄(NPT — 산 높이 h = 0.8P 를 산봉우리 · 골에 반씩 깎은 평탄, 너비 = 2(P/4 − h/2 · tan 30°) ≈ 0.038P).
 * 1/16 테이퍼는 그리지 않는다(두 피치에서 지름 차 P/8 — 끝 Z 의 지름으로 그림)
 */
internal data class PipeProfile(val angleDeg: Int, val round: Double?)

/**
 * 관용 테이퍼 단면의 윤곽(z, 산봉우리에서 잰 깊이) — 0 ~ 2P, 산 가운데가 z = 0 · P · 2P, 홈 가운데가 P/2 · 3P/2. 옆면은 피치선(깊이 h/2)에서
 * 산 가운데로부터 P/4(대칭 형상), PT 원호는 옆면에 접하는 반지름 round(중심은 산 · 홈 가운데 줄 위)
 */
internal fun pipeOutline(pr: PipeProfile, p: Double, h: Double): List<Pair<Double, Double>> {
    val a = pr.angleDeg * PI / 360
    val tanA = tan(a)
    // 산 가운데에서 옆면이 산봉우리 높이(깊이 0)에 닿는 곳
    val c0 = p / 4 - h / 2 * tanA
    val r = pr.round
    if (r == null) {
        val c1 = c0 + h * tanA
        return listOf(
            0.0 to 0.0, c0 to 0.0, c1 to h, p - c1 to h, p - c0 to 0.0,
            p + c0 to 0.0, p + c1 to h, 2 * p - c1 to h, 2 * p - c0 to 0.0, 2 * p to 0.0,
        )
    }
    // 산봉우리 원호 중심 깊이: 옆면까지 거리 = r. 골 원호는 피치선 점 (P/4, h/2) 둘레로 점대칭
    val dc = (r - c0 * cos(a)) / sin(a)
    val span = PI / 2 - a
    val n = 12
    fun crest(zt: Double, from: Double, to: Double) = (0..n).map { i ->
        val t = from + (to - from) * i / n
        (zt + r * sin(t)) to (dc - r * cos(t))
    }
    fun root(zg: Double) = (0..n).map { i ->
        val t = -span + 2 * span * i / n
        (zg + r * sin(t)) to (h - dc + r * cos(t))
    }
    return crest(0.0, 0.0, span) + root(p / 2) + crest(p, -span, span) + root(1.5 * p) + crest(2 * p, -span, 0.0)
}

/** 관용 테이퍼 홈의 반폭 — 산봉우리에서 깊이 d(거친 절입 깊이 가는 선). 옆면 · PT 골 원호 · NPT 골 평탄 */
internal fun pipeGrooveHalf(pr: PipeProfile, p: Double, h: Double, d: Double): Double {
    val a = pr.angleDeg * PI / 360
    val tanA = tan(a)
    val c0 = p / 4 - h / 2 * tanA
    val flank = p / 2 - c0 - d * tanA
    val r = pr.round ?: return max(flank, p / 2 - c0 - h * tanA)
    val center = h - (r - c0 * cos(a)) / sin(a)
    return if (d <= center + r * sin(a)) flank else sqrt(max(0.0, r * r - (d - center) * (d - center)))
}

/**
 * 관용 테이퍼 화면의 단면 자료. 풀렸으면 끝 Z 의 지름(바깥지름 · 피치경 · 골 X) · 리드 · k(지령값) · 거친 절입 깊이, 아직이면 피치 1 의 기본 형상에 이름만.
 * 둥글기는 PT 표의 r(크기를 고르기 전에는 0.137278P), NPT 는 평탄 너비를 적는다
 */
internal fun pipeSectionGeom(kind: PipeKind, inner: Boolean, spec: PipeSpec?, sol: PipeSolution?): SectionGeom {
    val crestName = if (inner) "안지름 Ø" else "바깥지름 Ø"
    val p = sol?.lead ?: spec?.pitch ?: 1.0
    val h = sol?.height ?: spec?.h ?: (if (kind == PipeKind.PT) 0.640327 else 0.8)
    val profile = when (kind) {
        PipeKind.PT -> PipeProfile(55, spec?.rootR ?: (0.137278 * p))
        PipeKind.NPT -> PipeProfile(60, null)
    }
    val round = profile.round
    val roundText = if (round != null) "r" to Fmt.fixed3(round) else "평탄" to Fmt.fixed3(2 * (p / 4 - h / 2 * tan(PI / 6)))
    if (sol == null) {
        return SectionGeom(
            p, h, h / 2, round ?: 0.0, emptyList(), inner,
            crestName to "", "피치경 Ø" to "", "골 X" to "", "k", "P", roundText.first, profile,
        )
    }
    return SectionGeom(
        p = p,
        k = h,
        crestToPitch = kotlin.math.abs(sol.crestAtEnd - sol.pitchAtEnd) / 2,
        tipR = round ?: 0.0,
        depths = sol.passes.filter { !it.finish }.map { it.depth },
        inner = inner,
        crest = crestName to Fmt.fixed3(sol.crestAtEnd),
        pitch = "피치경 Ø" to Fmt.fixed3(sol.pitchAtEnd),
        root = "골 X" to Fmt.fixed3(sol.x),
        kText = "k ${Fmt.fixed3(sol.height)}",
        pText = "P ${leadText(sol.lead)}",
        rText = "${roundText.first} ${roundText.second}",
        profile = profile,
    )
}

/**
 * 화면의 단면 자료. 풀렸으면 계산값(피치 = 리드, k = 지령값, 인선 R = 쓴 값) 그대로 — 그림이 계산과 같다.
 * 아직이면 피치 1 의 최대 실체 기본 형상(숫나사 k 0.6134P · 피치선 0.375H, 암나사 k 0.5413P · 0.25H, 인선 R H/6 · H/8)에 값 없이 이름만
 */
internal fun sectionGeom(inner: Boolean, sol: G76Solution?): SectionGeom {
    val crestName = if (inner) "안지름 Ø" else "바깥지름 Ø"
    if (sol == null) {
        val h = sqrt(3.0) / 2
        val r = if (inner) h / 8 else h / 6
        val toPitch = if (inner) h / 4 else 3 * h / 8
        return SectionGeom(
            1.0, toPitch + h / 2 - r, toPitch, r, emptyList(), inner,
            crestName to "", "피치경 Ø" to "", "골 X" to "", "k", "P", "인선 R",
        )
    }
    return SectionGeom(
        p = sol.lead,
        k = sol.height,
        crestToPitch = kotlin.math.abs(sol.crestDia - sol.pitchDia) / 2,
        tipR = sol.tipR,
        depths = sol.passes.filter { !it.finish }.map { it.depth },
        inner = inner,
        crest = crestName to Fmt.fixed3(sol.crestDia),
        pitch = "피치경 Ø" to Fmt.fixed3(sol.pitchDia),
        root = "골 X" to Fmt.fixed3(sol.x),
        kText = "k ${Fmt.fixed3(sol.height)}",
        pText = "P ${leadText(sol.lead)}",
        rText = "인선 R${Fmt.fixed3(sol.tipR)}",
    )
}

private val COS30 = sqrt(3.0) / 2
private val TAN30 = 1 / sqrt(3.0)

/** 60° V 날(끝 R) 단면의 반폭 — 날 끝에서 높이 v. 끝 원호는 높이 R/2 까지, 그 위는 옆날(30°) */
internal fun toolHalfWidth(r: Double, v: Double): Double =
    if (v <= r / 2) sqrt(max(0.0, r * r - (r - v) * (r - v))) else r * COS30 + (v - r / 2) * TAN30

/**
 * 나사 단면(축을 지나는 단면, 피치 기준 실제 비율): 두 피치의 나사산 — 산봉우리 평면 · 60° 옆면 · 골은 인선 R 원호(날 끝이 마지막에 지난 자리).
 * 외경은 소재(빗금)가 아래(축 쪽), 내경은 위(구멍 벽 쪽)다. 치수: 왼쪽 산 높이 k, 산봉우리 쪽 피치 P, 오른쪽에 산봉우리 지름 · 피치경(점선) · 골 X 이름.
 * 오른쪽 홈의 가는 선은 거친 절입마다 날 끝이 닿는 깊이(√n 간격), 왼쪽 홈 아래 인선 R
 */
@Composable
internal fun ThreadSection(g: SectionGeom, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    val mono = AppTheme.mono
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        val labelW = (w * 0.37f).coerceIn(116.dp, 180.dp)
        val left = 72.dp
        val profW = (w - left - labelW).coerceAtLeast(120.dp)
        val perMm = profW / (2 * g.p).toFloat()
        val band = maxOf(perMm * (0.3 * g.p).toFloat(), 24.dp)
        val edge = 36.dp
        val height = edge + perMm * g.k.toFloat() + band + 4.dp
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val (ks, kt) = artScale(size.width, 356.dp)
            val x0 = left.toPx()
            val sp = perMm.toPx()
            val down = if (g.inner) -1f else 1f
            val crestY = if (g.inner) size.height - edge.toPx() else edge.toPx()
            fun x(z: Double) = x0 + (z * sp).toFloat()
            fun y(d: Double) = crestY + down * (d * sp).toFloat()
            val p = g.p
            val k = g.k
            val r = g.tipR
            val style = TextStyle(fontFamily = sans, fontSize = 12.sp * kt, fontWeight = FontWeight.SemiBold, color = c.drawAngle)
            fun label(name: String, value: String, bold: Boolean = false) = buildAnnotatedString {
                append(name)
                if (value.isNotEmpty()) withStyle(SpanStyle(fontFamily = mono, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)) { append(value) }
            }

            // 윤곽: 산봉우리 → 홈 둘(옆날 · 끝 원호 · 옆날) → 산봉우리. 원호는 날 끝에서 높이 k 까지만(아주 얕은 홈).
            // 관용 테이퍼는 산봉우리까지 대칭 형상(pipeOutline — PT 둥근 산 · 골, NPT 평탄)
            val pr = g.profile
            val pts = if (pr != null) {
                pipeOutline(pr, p, k).map { (z, d) -> Offset(x(z), y(d)) }
            } else {
                val phiMax = if (k >= r / 2) PI / 3 else acos((1 - k / r).coerceIn(-1.0, 1.0))
                val hk = toolHalfWidth(r, k)
                val line = mutableListOf(Offset(x(0.0), y(0.0)))
                for (zc in listOf(0.5 * p, 1.5 * p)) {
                    line += Offset(x(zc - hk), y(0.0))
                    val n = 16
                    for (i in 0..n) {
                        val phi = -phiMax + 2 * phiMax * i / n
                        val u = r * sin(phi)
                        val v = r - r * cos(phi)
                        line += Offset(x(zc + u), y(k - v))
                    }
                    line += Offset(x(zc + hk), y(0.0))
                }
                line += Offset(x(2 * p), y(0.0))
                line
            }
            val profile = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val baseY = y(0.0) + down * (k * sp).toFloat() + down * band.toPx()
            val stock = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(x(2 * p), baseY)
                lineTo(x(0.0), baseY)
                close()
            }
            hatchFill(stock, c.drawStock, c.drawHatch, ks)

            // 오른쪽 홈: 거친 절입마다 날 끝 깊이(가는 선)
            val zc2 = 1.5 * p
            g.depths.forEach { d ->
                val hv = if (pr != null) pipeGrooveHalf(pr, p, k, d) else toolHalfWidth(r, k - d)
                drawLine(c.drawAngle, Offset(x(zc2 - hv), y(d)), Offset(x(zc2 + hv), y(d)), DrawSpec.dim.toPx() * ks * .9f)
            }

            // 기준선: 피치선(점선, 그림 폭 전체) · 골 높이(가는 선) · 산봉우리 높이(오른쪽 끝에서 이름까지)
            val labelX = x(2 * p) + 10.dp.toPx() * ks
            val ext = DrawSpec.ext.toPx() * ks
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() * ks, 3.dp.toPx() * ks))
            drawLine(c.drawRef, Offset(x(0.0) - 4.dp.toPx(), y(g.crestToPitch)), Offset(labelX - 3.dp.toPx(), y(g.crestToPitch)), ext, pathEffect = dash)
            drawLine(c.drawRef, Offset(x(0.0), y(k)), Offset(labelX - 3.dp.toPx(), y(k)), ext)
            drawLine(c.drawRef, Offset(x(2 * p), y(0.0)), Offset(labelX - 3.dp.toPx(), y(0.0)), ext)

            drawPath(profile, c.drawProfile, style = Stroke(DrawSpec.profile.toPx() * ks, join = StrokeJoin.Round))

            // 치수: 산 높이 k(왼쪽), 피치 P(산봉우리 쪽 바깥)
            // 치수선은 왼쪽 34dp 자리. linearDim 의 + 쪽은 a → b 를 시계로 돌린 쪽 — 외경(아래로)은 왼쪽, 내경(위로)은 오른쪽
            val kDist = x0 - 34.dp.toPx()
            linearDim(
                Offset(x(0.0), y(0.0)), Offset(x(0.0), y(k)), if (g.inner) -kDist else kDist,
                AnnotatedString(g.kText), c.drawAngle, ks, measurer, style, c.card,
            )
            linearDim(
                Offset(x(0.5 * p), y(0.0)), Offset(x(1.5 * p), y(0.0)), (if (g.inner) 1f else -1f) * 20.dp.toPx() * ks,
                AnnotatedString(g.pText), c.drawAngle, ks, measurer, style, c.card,
            )
            // 인선 R: 왼쪽 홈 날 끝 바로 너머(소재 안, 바탕색 테)
            haloText(measurer, AnnotatedString(g.rText), style, c.card, ks, center = Offset(x(0.5 * p), y(k) + down * 14.dp.toPx() * ks))

            // 오른쪽 이름 셋: 줄 높이에 두되 서로 겹치면 벌린다(위 → 아래 차례)
            val items = listOf(
                y(0.0) to label(g.crest.first, g.crest.second),
                y(g.crestToPitch) to label(g.pitch.first, g.pitch.second),
                y(k) to label(g.root.first, g.root.second, bold = true),
            ).sortedBy { it.first }
            val sizes = items.map { measurer.measure(it.second, style).size }
            val gap = 2.dp.toPx()
            val ys = items.map { it.first }.toMutableList()
            for (i in 1 until ys.size) {
                val need = (sizes[i - 1].height + sizes[i].height) / 2f + gap
                if (ys[i] - ys[i - 1] < need) ys[i] = ys[i - 1] + need
            }
            // 아래 끝을 넘으면 위로 되민다
            val over = ys.last() + sizes.last().height / 2f - (size.height - 1.dp.toPx())
            if (over > 0) for (i in ys.indices) ys[i] -= over
            items.forEachIndexed { i, (_, text) ->
                haloText(measurer, text, style, c.card, ks, topLeft = Offset(labelX, ys[i] - sizes[i].height / 2f))
            }
        }
    }
}
