package com.bmt.nosecalc.screens.polar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.PolarBlock
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarProgram
import com.bmt.nosecalc.calc.PolarSolution
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.Modal
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.onFill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 시뮬레이션(원본 07 모달): 프로그램 줄을 차례로 재생한다. 공구 중심은 계산기가 준 줄별 경로(PolarBlock.path — G41 프로그램은 제어기 보정을 흉내 낸 경로,
 * 직접 보정은 쓴 그대로)를 따라 실제 이송 F(mm/min, 평면 접선 속도)로 움직이고, 속도 배율로 빠르게 본다. 시간은 Compose 프레임 시각(withFrameNanos)이라
 * 폰과 PC 가 같은 빠르기다. 평면을 움직이지 않는 줄(주석 · G98 · G00 · G12.1 · G13.1)은 배율과 상관없이 [DWELL] 만큼 머문다 —
 * 급송 속도는 기계마다 달라 지어내지 않는다(그 줄은 시간 뜻이 없다).
 */

/** 속도 배율(원본 07 의 SPEED 슬라이더 — x5 가 약 30% 자리) */
internal val SPEEDS = listOf(1, 2, 5, 10, 20, 50, 100)
internal const val SPEED_DEFAULT = 3

/** 평면 이동이 없는 줄에 머무는 시간(초) — 지금 줄 띠가 줄마다 보이게 */
internal const val DWELL = 0.25

/** 선분 · 원호 길이(mm) */
internal fun segLength(s: Seg): Double = when (s) {
    is Line -> (s.end - s.start).length
    is Arc -> s.radius * s.sweepDeg * PI / 180
}

/** s 의 처음 t(0~1, 길이 비율)만 */
internal fun partial(s: Seg, t: Double): Seg = when (s) {
    is Line -> Line(s.start, s.start + (s.end - s.start) * t)
    is Arc -> {
        val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
        val a = a0 + (if (s.cw) -1 else 1) * s.sweepDeg * PI / 180 * t
        Arc(s.start, s.center + Pt(cos(a), sin(a)) * s.radius, s.center, s.cw)
    }
}

/**
 * 재생 계획(상태 없음): 줄마다 걸리는 시간(배율 1, 초)과 그 줄이 배율을 따르는지, 지나온 경로 · 공구 자리.
 * feed = 이송 F(mm/min). 줄의 길이는 평면 경로 길이, Z 만 움직이는 G01 은 Z 거리
 */
internal class SimPlan(val blocks: List<PolarBlock>, feed: Double, val start: Pt) {
    private val perSec = feed / 60

    /** 줄마다 (초, 배율을 따름) */
    val timing: List<Pair<Double, Boolean>> = blocks.mapIndexed { i, b ->
        val len = b.path.sumOf(::segLength)
        val prevZ = blocks.getOrNull(i - 1)?.z
        val dz = if (b.path.isEmpty() && "G1" in ncWords(b.text) && b.z != null && prevZ != null) abs(b.z!! - prevZ) else 0.0
        when {
            len > 1e-9 -> len / perSec to true
            dz > 1e-9 -> dz / perSec to true
            else -> DWELL to false
        }
    }

    /** 이송 줄의 시간 합(배율 1, 초) — 실제 가공 시간 어림(급송 빼고) */
    val feedSeconds: Double = timing.filter { it.second }.sumOf { it.first }

    /** block 줄의 frac(0~1)까지 지나온 공구 중심 경로 */
    fun traveled(block: Int, frac: Double): List<Seg> {
        if (block < 0) return emptyList()
        val done = blocks.take(block).flatMap { it.path }
        val cur = blocks[block].path
        if (cur.isEmpty() || frac <= 0) return done
        if (frac >= 1) return done + cur
        var want = cur.sumOf(::segLength) * frac
        val out = done.toMutableList()
        for (s in cur) {
            val l = segLength(s)
            if (want >= l) {
                out += s
                want -= l
            } else {
                if (want > 0 && l > 0) out += partial(s, want / l)
                break
            }
        }
        return out
    }

    /** 공구 중심 자리. 처음 위치 잡는 줄(G00 X… C0.) 전에는 없음 */
    fun toolAt(block: Int, frac: Double): Pt? {
        if (block < 0) return null
        val path = traveled(block, frac)
        if (path.isNotEmpty()) return path.last().end
        return if (blocks.take(block + 1).any { it.x != null }) start else null
    }

    /** 지금 Z(줄 안에서 앞 줄 Z 와 이 줄 Z 사이를 고르게) */
    fun zAt(block: Int, frac: Double): Double? {
        val b = blocks.getOrNull(block) ?: return null
        val z = b.z ?: return null
        val prev = blocks.getOrNull(block - 1)?.z ?: return z
        return prev + (z - prev) * frac.coerceIn(0.0, 1.0)
    }
}

/**
 * 재생 상태: block = 지금 줄(−1 = 시작 전), frac = 그 줄을 얼마나 지났는지(0~1). 싱글 블록이면 줄 끝마다 멈춘다(다시 '시작' 을 누르면 다음 줄 —
 * 기계의 싱글 블록처럼 방금 끝난 줄이 지금 줄로 남는다)
 */
@Stable
internal class SimRun(val plan: SimPlan, block: Int = -1, frac: Double = 0.0, single: Boolean = false, speedIdx: Int = SPEED_DEFAULT) {
    var block by mutableIntStateOf(block)
        private set
    var frac by mutableDoubleStateOf(frac)
        private set
    var running by mutableStateOf(false)
        private set
    var single by mutableStateOf(single)
    var speedIdx by mutableIntStateOf(speedIdx)

    val speed: Int get() = SPEEDS[speedIdx]
    val done: Boolean get() = block == plan.blocks.lastIndex && frac >= 1.0

    fun start() {
        if (done) {
            block = -1
            frac = 0.0
        }
        if (block < 0 || frac >= 1.0) {
            block += 1
            frac = 0.0
        }
        running = true
    }

    fun stop() {
        running = false
    }

    fun reset() {
        running = false
        block = -1
        frac = 0.0
    }

    /** 줄 i 가 지금 배율로 걸리는 시간(초) */
    fun seconds(i: Int): Double {
        val (t, scaled) = plan.timing[i]
        return max(if (scaled) t / speed else t, 1e-3)
    }

    /** dt 초만큼 진행 */
    fun advance(dt: Double) {
        var left = dt
        while (left > 0 && running) {
            if (block < 0) {
                block = 0
                frac = 0.0
            }
            val d = seconds(block)
            val remain = (1 - frac) * d
            if (left < remain) {
                frac += left / d
                return
            }
            left -= remain
            frac = 1.0
            if (block == plan.blocks.lastIndex || single) {
                running = false
                return
            }
            block += 1
            frac = 0.0
        }
    }
}

/**
 * 시뮬레이션 모달(원본 07): 그래프(축 · 외경 · 도형 · 지나온 공구 중심 경로 · 공구 원) → 좌표 줄(X · C · Z — 지금 줄의 줄 끝 지령값) →
 * 시작 · 리셋 · 싱글 블록 · 속도 → G코드 목록(지금 줄 노란 띠 · 앞 줄과 같아 뺀 좌표는 흐리게 · 주석 초록 · 저절로 따라 내려감)
 */
@Composable
internal fun PolarSimModal(sol: PolarSolution, program: PolarProgram, g: PolarGeom, direct: Boolean, xDiameter: Boolean, run: SimRun, onDismiss: () -> Unit) {
    // 프레임 시각으로 진행 — 폰 · PC 같은 빠르기. 한 프레임이 길어도(창을 옮기는 동안 등) 0.25초 넘게 건너뛰지 않는다
    LaunchedEffect(run, run.running) {
        if (!run.running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (run.running) {
            val now = withFrameNanos { it }
            run.advance(min((now - last) / 1e9, 0.25))
            last = now
        }
    }
    Modal(keepWords(if (direct) "시뮬레이션 · 직접 보정 G40" else "시뮬레이션 · 기계 보정 G41"), onDismiss) {
        // 그래프 높이는 모달 높이에 맞춘다(짧은 PC 창에서도 G코드 목록이 몇 줄은 보이게)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val graphH = (maxHeight * .34f).coerceIn(150.dp, 250.dp)
            Column(Modifier.fillMaxSize()) {
                SimGraph(sol, g, run, Modifier.fillMaxWidth().height(graphH))
                SimLegend(sol)
                Spacer(Modifier.height(8.dp))
                SimCoords(run, xDiameter)
                Spacer(Modifier.height(10.dp))
                SimControls(run)
                Spacer(Modifier.height(10.dp))
                CodeList(program.blocks, run.block, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** 그래프: 축 · 외경 원 · 도형 · 지나온 공구 중심 경로(보라) · 공구 원(파랑, Z 가 면 위 공기 중이면 점선 테), 오른쪽 위 '지금 줄 / 전체 줄' */
@Composable
private fun SimGraph(sol: PolarSolution, g: PolarGeom, run: SimRun, modifier: Modifier) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape).background(c.viewport).border(1.dp, c.lineCard, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val (ks, kt) = artScale(size.width, 340.dp)
            val all = run.plan.blocks.flatMap { it.path }
            val reachAll = maxOf(max(reach(sol.shape.segs), sol.stockR) * (if (g.inner) 1.22 else 1.0), reach(all) + sol.toolR)
            val xf = fitXf(reachAll, 20.dp.toPx() * kt)
            val half = min(size.width, size.height) / 2 - 4.dp.toPx()
            if (g.inner) {
                polarShape(g, xf, c, ks)
                polarAxes(xf, half, measurer, c, ks, kt)
            } else {
                polarAxes(xf, half, measurer, c, ks, kt)
                polarShape(g, xf, c, ks)
            }
            val path = run.plan.traveled(run.block, run.frac)
            if (path.isNotEmpty()) {
                drawPath(segPath(path, xf), c.viewPath, style = Stroke(2.5.dp.toPx() * ks, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val tool = run.plan.toolAt(run.block, run.frac) ?: return@Canvas
            val p = xf.of(tool)
            val r = max((sol.toolR * xf.k).toFloat(), 3.dp.toPx())
            val inAir = (run.plan.zAt(run.block, run.frac) ?: 1.0) >= 0
            if (inAir) {
                drawCircle(c.viewNose, r, p, style = Stroke(1.6.dp.toPx() * ks, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            } else {
                drawCircle(c.viewNose.copy(alpha = if (c.dark) .30f else .22f), r, p)
                drawCircle(c.viewNose, r, p, style = Stroke(1.6.dp.toPx() * ks))
            }
            drawCircle(c.viewNose, 2.5.dp.toPx() * ks, p)
        }
        Text(
            "${if (run.block < 0) 0 else run.block + 1} / ${run.plan.blocks.size}줄",
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/** 그래프 아래 범례: 지나온 공구 중심 경로 · 공구 원 */
@Composable
private fun SimLegend(sol: PolarSolution) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 10.dp)) { drawLine(c.viewPath, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(), StrokeCap.Round) }
        Spacer(Modifier.width(4.dp))
        Text("공구 중심 경로", color = c.textMuted, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.size(12.dp)) { drawCircle(c.viewNose, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx())) }
        Spacer(Modifier.width(4.dp))
        Text("공구 Ø${plainNum(2 * sol.toolR)}", color = c.textMuted, fontSize = 12.sp, maxLines = 1)
    }
}

/** 좌표 줄 세 칸(원본 07 의 X (DIA) · C (ANG) · Z (DEP)): 지금 줄의 줄 끝 지령값. C 는 G12.1 안이면 평면의 mm, 앞이면 회전각 */
@Composable
private fun SimCoords(run: SimRun, xDiameter: Boolean) {
    val c = AppTheme.colors
    val b = run.plan.blocks.getOrNull(run.block)
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().height(58.dp).clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape)) {
        @Composable
        fun RowScope.cell(label: String, value: Double?) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(label, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(value?.let(Fmt::nc) ?: "—", style = AppType.numLarge, color = c.valueComp, maxLines = 1)
            }
        }

        @Composable
        fun divider() = Box(Modifier.padding(vertical = 10.dp).width(1.dp).fillMaxHeight().background(c.lineCard))
        val polar = b?.polar == true
        cell(if (polar && !xDiameter) "X (반경)" else "X (직경)", b?.x)
        divider()
        cell(if (polar) "C (mm)" else "C (°)", b?.c)
        divider()
        cell("Z (깊이)", b?.z)
    }
}

/** 시작/정지 · 리셋 · 싱글 블록(줄마다 멈춤) / 속도 배율 슬라이더 */
@Composable
private fun SimControls(run: SimRun) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val fill = c.appsFill
        SimButton(
            when {
                run.running -> "정지"
                run.done -> "다시"
                run.block >= 0 -> "계속"
                else -> "시작"
            },
            Modifier.weight(1f), fill, c.onFill(fill), null,
            glyph = { tint -> if (run.running) PauseGlyph(tint) else PlayGlyph(tint) },
        ) { if (run.running) run.stop() else run.start() }
        SimButton("리셋", Modifier.weight(1f), c.subtle, c.textBody, c.lineField, glyph = { ResetGlyph(it) }) { run.reset() }
        val on = run.single
        SimButton(
            "싱글 블록", Modifier.weight(1.4f),
            if (on) c.teal.copy(alpha = if (c.dark) .16f else .08f) else c.subtle, if (on) c.tealDeep else c.textBody,
            if (on) c.teal else c.lineField,
            glyph = { tint -> CheckGlyph(on, tint) },
        ) { run.single = !run.single }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("속도", color = c.textBody, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("x${run.speed}", Modifier.width(44.dp), style = AppType.numBody, color = c.tealDeep, maxLines = 1)
        SpeedSlider(run.speedIdx, SPEEDS.size, { run.speedIdx = it }, Modifier.weight(1f))
    }
}

@Composable
private fun SimButton(
    text: String,
    modifier: Modifier,
    bg: Color,
    fg: Color,
    border: Color?,
    glyph: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.heightIn(min = 48.dp).clip(shape).background(bg).then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph(fg)
        Spacer(Modifier.width(6.dp))
        // 좁은 폰 · 큰 글꼴에서도 '싱글 블록' 이 잘리지 않게 줄인다 — 한 줄로 재야(softWrap 끔) 넘침을 알아채고 줄인다(말줄임은 넘침으로 안 셈)
        BasicText(
            text,
            style = TextStyle(color = fg, fontFamily = AppTheme.sans, fontWeight = FontWeight.Bold),
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(10.sp, 15.sp, 0.5.sp),
        )
    }
}

@Composable
private fun PlayGlyph(tint: Color) = Canvas(Modifier.size(16.dp)) {
    drawPath(Path().apply { moveTo(size.width * .18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .18f, size.height); close() }, tint)
}

@Composable
private fun PauseGlyph(tint: Color) = Canvas(Modifier.size(16.dp)) {
    val w = size.width * .3f
    drawRoundRect(tint, Offset(size.width * .1f, 0f), Size(w, size.height), CornerRadius(1.5.dp.toPx()))
    drawRoundRect(tint, Offset(size.width * .6f, 0f), Size(w, size.height), CornerRadius(1.5.dp.toPx()))
}

@Composable
private fun ResetGlyph(tint: Color) = Canvas(Modifier.size(18.dp)) {
    // 반시계로 도는 화살(⟲): 오른쪽 위에서 시작해 280° 돌아 오른쪽 아래에서 끝, 끝에 채운 촉
    val r = size.minDimension / 2 - 2.5.dp.toPx()
    val o = Offset(size.width / 2, size.height / 2)
    drawArc(tint, -50f, -280f, false, o - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    val a = 30.0 * PI / 180
    val tip = o + Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
    arrowHead(tip + Offset((sin(a) * 2.dp.toPx()).toFloat(), (-cos(a) * 2.dp.toPx()).toFloat()), Offset(sin(a).toFloat(), -cos(a).toFloat()), tint, 6.dp.toPx())
}

@Composable
private fun CheckGlyph(on: Boolean, tint: Color) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(20.dp).clip(shape).background(if (on) c.teal else c.card).border(1.5.dp, if (on) c.teal else c.lineStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = c.onFill(c.teal))
    }
}

/** 칸 count 개의 슬라이더(48dp 누르는 자리): 누르거나 끌면 가장 가까운 칸으로 */
@Composable
private fun SpeedSlider(index: Int, count: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    Canvas(
        modifier.height(48.dp).pointerInput(count) {
            fun pick(x: Float) {
                val pad = 12.dp.toPx()
                val t = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                onChange((t * (count - 1)).roundToInt())
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                pick(down.position.x)
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    pick(ch.position.x)
                    ch.consume()
                }
            }
        },
    ) {
        val pad = 12.dp.toPx()
        val y = size.height / 2
        val w = size.width - 2 * pad
        val tx = pad + w * index / (count - 1).coerceAtLeast(1)
        val th = 6.dp.toPx()
        drawRoundRect(c.segTrack, Offset(pad, y - th / 2), Size(w, th), CornerRadius(th / 2))
        drawRoundRect(c.teal, Offset(pad, y - th / 2), Size(tx - pad, th), CornerRadius(th / 2))
        for (i in 0 until count) {
            val x = pad + w * i / (count - 1).coerceAtLeast(1)
            drawCircle(if (i <= index) c.card else c.lineStrong, 1.5.dp.toPx(), Offset(x, y))
        }
        drawCircle(c.card, 12.dp.toPx(), Offset(tx, y))
        drawCircle(c.teal, 9.dp.toPx(), Offset(tx, y))
    }
}

/**
 * G코드 목록(원본 07 의 민트 바탕 칸): 지금 줄 노란 띠, 흐린 좌표, 초록 주석. 낱말은 NC 글처럼 붙여 쓴다(흐린 좌표도 제자리에 붙여).
 * 지금 줄이 바뀌면 그 두 줄 위까지 저절로 내린다(리셋이면 맨 위로)
 */
@Composable
private fun CodeList(blocks: List<PolarBlock>, current: Int, modifier: Modifier) {
    val c = AppTheme.colors
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        state.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    val shape = RoundedCornerShape(10.dp)
    LazyColumn(
        modifier.heightIn(min = 120.dp).clip(shape).background(c.chipMint.border.copy(alpha = if (c.dark) .10f else .16f)).border(1.dp, c.lineCard, shape),
        state = state,
        contentPadding = PaddingValues(6.dp),
    ) {
        itemsIndexed(blocks) { i, b ->
            val on = i == current
            val toks = ncTokens(b)
            Text(
                buildAnnotatedString {
                    toks.forEach { t ->
                        val style = when {
                            t.text.startsWith("(") -> SpanStyle(color = c.chipMint.label, fontWeight = FontWeight.Bold)
                            t.dim -> SpanStyle(color = if (on) c.textBody else c.textMuted, fontWeight = FontWeight.Normal)
                            else -> SpanStyle(color = c.textValue, fontWeight = FontWeight.Bold)
                        }
                        withStyle(style) { append(t.text) }
                    }
                },
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) currentBand(c) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = AppType.numBody,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * 지금 줄 노란 띠(원본 07). 라이트 = warn.border #FCD34D, 다크 = 짙은 호박 #5C430D — 다크의 warn.border(#8A6516)는 밝아서 그 위 흐린 좌표가 2:1 아래였다.
 * 대비(WCAG): 띠 위 굵은 글 textValue 라이트 12.4 · 다크 8.5, 흐린 좌표(textBody) 5.2 · 5.1. 띠 밖 흐린 좌표(textMuted)는 목록 바탕 위 4.7 · 5.4
 */
private fun currentBand(c: AppColors): Color = if (c.dark) Color(0xFF5C430D) else c.warn.border

/** 시뮬레이션 시작 상태(스크린샷 시험이 재생 중간을 고정해 찍는다) */
internal data class SimStart(val block: Int, val frac: Double, val single: Boolean = false, val speedIdx: Int = SPEED_DEFAULT)

/** 프로그램 → 재생 계획(시작점 = G12.1 앞 위치 잡는 점) */
internal fun simPlan(sol: PolarSolution, program: PolarProgram, feed: Double) = SimPlan(program.blocks, feed, PolarG12.pt(sol.startX, 0.0))
