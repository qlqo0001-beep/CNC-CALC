package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.sim.Contact
import com.bmt.nosecalc.calc.sim.ContactKind
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StepSrc
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.StockProfile
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.sim.ToolShape
import com.bmt.nosecalc.ui.ViewXf
import com.bmt.nosecalc.ui.ViewerState
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.tan

/**
 * 한 실행의 그릴 경로(설계 7.6 ⑥⑦) — 모델 좌표(mm, Z 가로 · X 반경 세로)의 토막 목록(z0 x0 z1 x1 …, 원호는 2° 꺾은선)으로 실행마다 한 번 만든다.
 * 그릴 때 보이는 토막만 화면 좌표로 잘라 긋고 화면 픽셀로 같은 토막은 한 번만 긋는다([screenPath]) — 큰 프로그램(겹친 층 · 되짚는 급송)도 긋는 양이 화면 크기에 묶인다.
 * 급송(점선) · 황삭 층(사이클의 층 · 쪼기 · 나사 패스 — 회색) · 명령 경로(보정 전, 보정이 있을 때만 초록) · 보정된 인선 경로(보라, 원호 구간은 파랑) · 노즈 중심(점선).
 * 블록(G70 의 P~Q 포함) · 모서리 · G71/G72 여유 패스의 이송이 '깎는 경로', 나머지 사이클 이송은 '층'.
 */
internal class SimPaths(run: SimRun) {
    val rapid: FloatArray
    val layer: FloatArray
    val cmd: FloatArray
    val tip: FloatArray
    val arc: FloatArray
    val nose: FloatArray
    val hasRapid get() = rapid.isNotEmpty()
    val hasLayer get() = layer.isNotEmpty()
    val hasCmd get() = cmd.isNotEmpty()
    val hasArc get() = arc.isNotEmpty()

    /** 모형 이동(층 위치 · 진입을 우리가 정한 것)이 있나 — 범례 '모형' · 시간 '모형 포함' */
    val model: Boolean

    /** 기운 프로그램 직선(각도 확인 — 06 의 56.31°): 시작 · 끝(명령점) */
    val slopes: List<Pair<Pt, Pt>>

    /** 이송이 닿은 상자(left/right = Z, top/bottom = X 반경) — 맞춤 */
    val box: Rect?

    /** 토막 목록에 긋는다(원호는 2° 꺾은선) */
    private class Pen {
        private var a = FloatArray(64)
        private var n = 0

        private fun add(p: Pt, q: Pt) {
            if (n + 4 > a.size) a = a.copyOf(a.size * 2)
            a[n++] = p.z.toFloat()
            a[n++] = p.x.toFloat()
            a[n++] = q.z.toFloat()
            a[n++] = q.x.toFloat()
        }

        fun seg(s: Seg) {
            when (s) {
                is Line -> add(s.start, s.end)
                is Arc -> {
                    val k = max(4, ceil(s.sweepDeg / 2).toInt())
                    var a = s.start
                    for (j in 1..k) {
                        val q = SimRun.at(s, j.toDouble() / k)
                        add(a, q)
                        a = q
                    }
                }
            }
        }

        fun done() = a.copyOf(n)
    }

    init {
        val pr = Pen()
        val pl = Pen()
        val pc = Pen()
        val pt = Pen()
        val pa = Pen()
        val pn = Pen()
        var m = false
        val sl = LinkedHashSet<Pair<Pt, Pt>>()
        var zl = Double.POSITIVE_INFINITY
        var zr = Double.NEGATIVE_INFINITY
        var xb = Double.POSITIVE_INFINITY
        var xt = Double.NEGATIVE_INFINITY
        for (s in run.steps) {
            if (s !is MoveStep) continue
            if (s.model) m = true
            if (s.kind == MoveKind.RAPID) {
                pr.seg(s.tip)
                continue
            }
            for (p in StockAuto.extremes(s.tip)) {
                zl = min(zl, p.z)
                zr = max(zr, p.z)
                xb = min(xb, p.x)
                xt = max(xt, p.x)
            }
            val cut = s.src == StepSrc.BLOCK || s.src == StepSrc.CORNER || s.src == StepSrc.CYCLE_FINISH
            if (!cut || s.kind == MoveKind.THREAD) {
                pl.seg(s.tip)
                continue
            }
            if (s.tip is Arc) pa.seg(s.tip) else pt.seg(s.tip)
            pn.seg(s.nose)
            val c = s.cmd
            if (c != null && ((c.start - s.tip.start).length > 1e-6 || (c.end - s.tip.end).length > 1e-6)) {
                pc.seg(c)
            }
            val l = c ?: s.tip
            if (l is Line && s.src != StepSrc.CYCLE_FINISH) {
                val d = l.end - l.start
                if (abs(d.z) > 1e-6 && abs(d.x) > 1e-6) sl += l.start to l.end
            }
        }
        model = m
        rapid = pr.done()
        layer = pl.done()
        cmd = pc.done()
        tip = pt.done()
        arc = pa.done()
        nose = pn.done()
        slopes = sl.toList()
        box = if (zl <= zr) Rect(zl.toFloat(), xb.toFloat(), zr.toFloat(), xt.toFloat()) else null
    }
}

/** 고른 것: 고스트 번호(측정 모드면 그 블록 끝 명령점) */
internal data class Pick(val ghost: Int, val measure: Boolean)

/**
 * 2D 뷰(설계 7.6 · 7.7 — 06 · 10). 그리는 순서(아래 → 위): 바탕 · 격자 → 척 → 기장여유 띠 → 처음 소재(점선) · 남은 소재(면 + 테) · 나사 →
 * Z0 · 중심선 → 급송 · 층 · 노즈 중심 · 명령 경로 · 보정 경로 · 원호 → 고스트 · 측정점 · 각도 → 급송 깎음 ▲ · 척 ✕ → 지금 공구 → 고른 표시(고리 · 십자 · 좌표 알약) →
 * 알약(Z0 · 척 쪽 · 축척). 손짓: 한 손가락 이동 · 두 손가락 확대 · 누르기 = 고스트/측정점 고르기, PC 는 끌기 · 휠 확대.
 * 재생 시각은 그리기에서만 읽어(play.t) 프레임마다 다시 그려도 화면 전체를 다시 짜지 않는다.
 * [insets] = 겹쳐 놓인 단추 · 카드 자리(맞춤에서 뺄 곳: 왼 · 위 · 오른 · 아래, dp).
 */
@Composable
internal fun Sim2DView(
    run: SimRun,
    paths: SimPaths,
    spec: StockSpec?,
    profile: State<StockProfile?>,
    crest: (Double, Boolean) -> Double?,
    play: PlayState,
    pick: Pick?,
    measure: Boolean,
    angle: Boolean,
    flags: ViewFlags,
    tools: Map<Int, ToolOffset>,
    contacts: List<Contact>,
    view: ViewerState,
    insets: List<Dp>,
    onPick: (Pick?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val bounds = remember(paths, spec) { fitBox(paths, spec, run) }
    SideEffect { view.follow(bounds, density.fitArea(size, insets), size) }
    val measurer = rememberTextMeasurer()
    val mono = TextStyle(fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold)
    val sans = TextStyle(fontFamily = AppTheme.sans, fontWeight = FontWeight.Bold)
    // 겹친 고스트를 다시 누르면 다음 것 — 마지막 누른 후보 목록
    val cycle = remember { mutableStateOf<Pair<List<Int>, Int>?>(null) }
    // 손짓 블록은 view · insets 가 바뀔 때만 다시 시작한다 — 그 안에서 쓰는 값은 늘 지금 것으로
    val runNow by rememberUpdatedState(run)
    val measureNow by rememberUpdatedState(measure)
    val onPickNow by rememberUpdatedState(onPick)
    fun DrawScope.xf() = view.xf(fitArea(IntSize(this.size.width.toInt(), this.size.height.toInt()), insets))
    Box(
        modifier
            .fillMaxSize()
            .background(c.viewport)
            .onSizeChanged { size = it }
            .pointerInput(view, insets) {
                viewGestures(view, { fitArea(IntSize(this.size.width, this.size.height), insets) }) { at ->
                    val v = view.xf(fitArea(IntSize(this.size.width, this.size.height), insets))
                    val near = 24.dp.toPx()
                    val ghosts = runNow.ghosts
                    val cand = ghosts.indices.map { i ->
                        val g = ghosts[i]
                        val d = if (measureNow) (v.toScreen(g.cmd) - at).getDistance() else max(0f, (v.toScreen(g.nose) - at).getDistance() - (g.r * v.s).toFloat())
                        i to d
                    }.filter { it.second <= near }.sortedBy { it.second }.map { it.first }
                    if (cand.isEmpty()) {
                        cycle.value = null
                        onPickNow(null)
                    } else {
                        val prev = cycle.value
                        val k = if (prev != null && prev.first.toSet() == cand.toSet()) (prev.second + 1) % prev.first.size else 0
                        val list = prev?.first?.takeIf { it.toSet() == cand.toSet() } ?: cand
                        cycle.value = list to k
                        onPickNow(Pick(list[k], measureNow))
                    }
                }
            },
    ) {
        // 네 층(아래 → 위). 재생 프레임마다 다시 그리는 것은 ② 남은 소재와 ④ 지금 공구뿐 — ① · ③ 은 보기(확대 · 이동) · 설정이 바뀔 때만.
        // ③ 경로 층은 화면 밖 그림(Offscreen)으로 따로 둬 안드로이드에선 재생 중 다시 래스터하지 않는다(큰 프로그램 — 15.3-10)
        Canvas(Modifier.fillMaxSize()) {
            val v = xf()
            grid(v, c.viewportGrid, c.viewGridMajor)
            spec?.let { stockBase(it, v, c, p) }
        }
        if (spec != null) {
            Canvas(Modifier.fillMaxSize()) { stockRemain(spec, xf(), profile.value, crest, flags, c, p) }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val v = xf()
            axes(v, c.viewportAxis)
            if (flags.rapid && paths.hasRapid) dashedSegs(paths.rapid, v, c.viewRapid, 1.2.dp, 6.dp, 4.dp, arrows = true)
            if (flags.layers && paths.hasLayer) solidSegs(paths.layer, v, p.layer, 0.8.dp)
            if (flags.nose) dashedSegs(paths.nose, v, c.viewNose, 1.dp, 5.dp, 4.dp, arrows = false)
            if (paths.hasCmd) solidSegs(paths.cmd, v, c.simProgram, 1.5.dp)
            solidSegs(paths.tip, v, c.viewPath, 2.dp)
            if (paths.hasArc) solidSegs(paths.arc, v, c.simArc, 2.dp)
            // 고스트 · 측정점(7.7)
            if (measure) {
                for (g in run.ghosts) {
                    val q = v.toScreen(g.cmd)
                    if (!inView(q)) continue
                    val h = 3.5.dp.toPx()
                    drawRect(c.viewport, Offset(q.x - h, q.y - h), Size(2 * h, 2 * h))
                    drawRect(c.lineFocus, Offset(q.x - h, q.y - h), Size(2 * h, 2 * h), style = Stroke(1.2.dp.toPx()))
                }
            } else if (flags.ghost && run.ghosts.size <= MAX_GHOSTS) {
                for (g in run.ghosts) ghost(g.nose, g.tip, g.r, g.tipNo, v, p.ghost)
            }
            if (angle) for ((a, b) in paths.slopes) anglePill(a, b, v, measurer, sans, c)
            for (k in contacts) {
                val q = v.toScreen(k.at)
                if (k.kind == ContactKind.CHUCK) crossMark(q, c.error.label) else warnMark(q, c.warn.label, c.viewport)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val v = xf()
            currentTool(run, play, tools, v, c)
            // 알약(Z0 · 척 쪽 · 축척)을 먼저 — 고른 좌표 알약이 그 위에 오게(큰 글꼴에서 축척 막대가 좌표를 덮었다)
            pills(v, gridStep(v), spec, measurer, sans, c, insets)
            val sel = pick?.let { run.ghosts.getOrNull(it.ghost) }
            if (sel != null) {
                if (!measure) {
                    val q = v.toScreen(sel.nose)
                    drawCircle(c.teal, max((sel.r * v.s).toFloat(), 3.dp.toPx()) + 3.dp.toPx(), q, style = Stroke(2.dp.toPx()))
                }
                val at = if (pick.measure) sel.cmd else sel.tip
                focusCross(v.toScreen(at), at, measurer, mono, c, p)
            }
        }
    }
}

/** 맞출 상자: 이송이 닿은 곳 + 소재(척 면 ~ 끝면, 중심 ~ ØD/2). 없으면 인선 이동 상자 */
private fun fitBox(paths: SimPaths, spec: StockSpec?, run: SimRun): Rect? {
    var r = paths.box ?: run.extent?.let { (a, b) -> Rect(a.z.toFloat(), a.x.toFloat(), b.z.toFloat(), b.x.toFloat()) }
    if (spec != null) {
        val s = Rect((spec.faceZ - spec.length).toFloat(), (spec.boreDia / 2).toFloat(), spec.faceZ.toFloat(), (spec.dia / 2).toFloat())
        r = r?.let { Rect(min(it.left, s.left), min(it.top, s.top), max(it.right, s.right), max(it.bottom, s.bottom)) } ?: s
    }
    return r
}

/** 고스트가 이보다 많으면 원을 그리지 않는다(큰 프로그램 — 누르기는 된다) */
private const val MAX_GHOSTS = 4000

/** 맞춤에 쓰는 곳: 겹친 단추 · 카드 자리를 뺀 곳 */
private fun Density.fitArea(size: IntSize, insets: List<Dp>): Rect {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val l = insets[0].toPx()
    val t = insets[1].toPx()
    val r = w - insets[2].toPx()
    val b = h - insets[3].toPx()
    return if (r - l < 40 || b - t < 40) Rect(0f, 0f, max(w, 1f), max(h, 1f)) else Rect(l, t, r, b)
}

private fun DrawScope.inView(q: Offset) = q.x > -40 && q.y > -40 && q.x < size.width + 40 && q.y < size.height + 40

/** 화면 좌표로 모은 보이는 토막: Path · 보인 길이(px) · 길게 보이는 토막의 가운데와 방향(x y ux uy …, 급송 화살) */
private class ScreenPath(val path: Path, val length: Float, val heads: FloatArray)

/**
 * 보이는 토막만 화면 좌표 Path 로: 화면(+8px) 밖은 버리고 걸친 것은 Liang–Barsky 로 자르며, 화면 픽셀로 같은 토막(거꾸로 간 것 포함)은 한 번만.
 * 이어진 토막은 한 줄로 이어 긋는다(점선 위상 · 둥근 이음). [arrowMin] 보다 길게 보이는 토막은 [maxHeads] 개까지 가운데 · 방향을 모은다.
 */
private fun DrawScope.screenPath(segs: FloatArray, v: ViewXf, arrowMin: Float = Float.POSITIVE_INFINITY, maxHeads: Int = 0): ScreenPath {
    val x0 = -8f
    val y0 = -8f
    val x1 = size.width + 8f
    val y1 = size.height + 8f
    val path = Path()
    var total = 0f
    var lx = Float.NaN
    var ly = Float.NaN
    val heads = ArrayList<Float>()
    val seen = HashSet<Long>()
    var i = 0
    while (i < segs.size) {
        var ax = v.t.x + segs[i] * v.s
        var ay = v.t.y - segs[i + 1] * v.s
        var bx = v.t.x + segs[i + 2] * v.s
        var by = v.t.y - segs[i + 3] * v.s
        i += 4
        if ((ax < x0 && bx < x0) || (ax > x1 && bx > x1) || (ay < y0 && by < y0) || (ay > y1 && by > y1)) continue
        val dx = bx - ax
        val dy = by - ay
        var t0 = 0f
        var t1 = 1f
        // 왼 · 오른 · 위 · 아래 경계(그 축으로 움직이지 않으면 위 버림 검사가 밖을 걸렀다)
        if (dx != 0f) {
            val ra = (x0 - ax) / dx
            val rb = (x1 - ax) / dx
            t0 = max(t0, min(ra, rb))
            t1 = min(t1, max(ra, rb))
        }
        if (dy != 0f) {
            val ra = (y0 - ay) / dy
            val rb = (y1 - ay) / dy
            t0 = max(t0, min(ra, rb))
            t1 = min(t1, max(ra, rb))
        }
        if (t0 > t1) continue
        bx = ax + dx * t1
        by = ay + dy * t1
        ax += dx * t0
        ay += dy * t0
        val len = kotlin.math.hypot(bx - ax, by - ay)
        if (len < 0.01f || !seen.add(pixelKey(ax, ay, bx, by))) continue
        total += len
        if (ax != lx || ay != ly) path.moveTo(ax, ay)
        path.lineTo(bx, by)
        lx = bx
        ly = by
        if (len >= arrowMin && heads.size < 4 * maxHeads) {
            heads += (ax + bx) / 2
            heads += (ay + by) / 2
            heads += (bx - ax) / len
            heads += (by - ay) / len
        }
    }
    return ScreenPath(path, total, heads.toFloatArray())
}

/** 실선(굵기는 화면 dp) */
private fun DrawScope.solidSegs(segs: FloatArray, v: ViewXf, color: Color, width: Dp) {
    drawPath(screenPath(segs, v).path, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * 점선(급송 · 노즈 중심) — 점선 조각은 긋는 길이만큼 생기므로 화면 밖 · 겹친 토막까지 통째로 점선으로 만들면 한 장에 수 초가 걸렸다
 * (이동 10만 개 — TrajPerfTest). 그래도 보이는 길이가 [DASH_BUDGET] 화면 폭을 넘으면(급송이 화면을 덮음) 점선이 읽히지 않으므로
 * 옅은 머리카락 실선으로. 급송은 길게 보이는 토막 가운데에 빈 화살(7.6-6).
 */
private fun DrawScope.dashedSegs(segs: FloatArray, v: ViewXf, color: Color, width: Dp, on: Dp, off: Dp, arrows: Boolean) {
    val sp = screenPath(segs, v, 48.dp.toPx(), if (arrows) MAX_ARROWS + 1 else 0)
    if (sp.length > DASH_BUDGET * max(size.width, size.height)) {
        // 머리카락 선(1px) — 굵은 선 긋기(외곽 만들기)는 토막이 많으면 무겁다
        drawPath(sp.path, color.copy(alpha = color.alpha * .5f), style = Stroke(0f))
        return
    }
    val w = width.toPx()
    drawPath(sp.path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))))
    val heads = sp.heads
    if (heads.size > 4 * MAX_ARROWS) return
    // 빈 화살: 가운데에서 진행 방향으로, 두 날개 ±28°
    val h = 6.dp.toPx()
    val cs = kotlin.math.cos(28 * PI / 180).toFloat()
    val sn = kotlin.math.sin(28 * PI / 180).toFloat()
    for (k in heads.indices step 4) {
        val tip = Offset(heads[k] + heads[k + 2] * h / 2, heads[k + 1] + heads[k + 3] * h / 2)
        val ux = -heads[k + 2]
        val uy = -heads[k + 3]
        drawLine(color, tip, tip + Offset((ux * cs - uy * sn) * h, (ux * sn + uy * cs) * h), w * 1.2f, StrokeCap.Round)
        drawLine(color, tip, tip + Offset((ux * cs + uy * sn) * h, (-ux * sn + uy * cs) * h), w * 1.2f, StrokeCap.Round)
    }
}

/** 화면 픽셀로 반올림한 토막 열쇠(끝점 순서 무관) — 각 값 16 비트 */
private fun pixelKey(ax: Float, ay: Float, bx: Float, by: Float): Long {
    fun q(f: Float): Long = (kotlin.math.round(f).toLong() + 16).coerceIn(0, 0xFFFF)
    var a = (q(ax) shl 16) or q(ay)
    var b = (q(bx) shl 16) or q(by)
    if (a > b) a = b.also { b = a }
    return (a shl 32) or b
}

/** 점선으로 그을 보이는 길이 한도(화면 긴 변의 배수) — 넘으면 옅은 실선 */
private const val DASH_BUDGET = 60f

/** 급송 빈 화살 한도 — 넘으면(빽빽하면) 화살을 그리지 않는다 */
private const val MAX_ARROWS = 150

/** 격자 간격(mm): 화면 30dp 이상인 1 · 2 · 5 × 10ⁿ */
private fun DrawScope.gridStep(v: ViewXf): Double {
    val raw = 30.dp.toPx() / v.s
    val pw = 10.0.pow(floor(log10(raw.toDouble())))
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * pw }.first { it >= raw }
}

/** 격자(1 · 2 · 5 간격, 0 에서 다섯째 줄 굵게 — ShapeViewer 와 같은 꼴) */
private fun DrawScope.grid(v: ViewXf, color: Color, major: Color) {
    val step = gridStep(v)
    fun tone(at: Double) = if (round(at / step).toLong() % 5 == 0L) major else color
    val z0 = -v.t.x / v.s
    val z1 = (size.width - v.t.x) / v.s
    val x0 = (v.t.y - size.height) / v.s
    val x1 = v.t.y / v.s
    var z = floor(z0 / step) * step
    while (z <= z1) {
        val sx = v.t.x + z.toFloat() * v.s
        drawLine(tone(z), Offset(sx, 0f), Offset(sx, size.height), 1f)
        z += step
    }
    var x = floor(x0 / step) * step
    while (x <= ceil(x1 / step) * step) {
        val sy = v.t.y - x.toFloat() * v.s
        drawLine(tone(x), Offset(0f, sy), Offset(size.width, sy), 1f)
        x += step
    }
}

/** Z0 세로선 · 중심선 X0(1점 쇄선) */
private fun DrawScope.axes(v: ViewXf, color: Color) {
    val y = v.t.y
    if (y in 0f..size.height) centerLine(Offset(0f, y), Offset(size.width, y), color, 1.2f)
    val x = v.t.x
    if (x in 0f..size.width) centerLine(Offset(x, 0f), Offset(x, size.height), color, 1.2f)
}

private fun ViewXf.rect(z0: Double, x0: Double, z1: Double, x1: Double): Rect {
    val a = toScreen(Pt(min(z0, z1), max(x0, x1)))
    val b = toScreen(Pt(max(z0, z1), min(x0, x1)))
    return Rect(a, b)
}

/** 척 · 기장여유 띠 · 처음 소재(점선) — 보기만 따른다 */
private fun DrawScope.stockBase(spec: StockSpec, v: ViewXf, c: AppColors, p: SimPalette) {
    val chuckZ = spec.faceZ - spec.length
    val r = spec.dia / 2
    // 척: 척 면 왼쪽, 반경 척 지름/2 까지(척 닿음 검사와 같은 곳)
    val jaw = max(15.0, spec.dia * 0.4)
    val cr = v.rect(chuckZ - jaw, 0.0, chuckZ, spec.chuckDia / 2)
    val chuck = Path().apply { addRect(cr) }
    hatchFill(chuck, p.chuck.copy(alpha = .22f), p.chuck.copy(alpha = .45f), 1f)
    drawRect(p.chuck, cr.topLeft, cr.size, style = Stroke(1.dp.toPx()))
    // 기장여유 띠: Z0 ~ 끝면
    if (spec.faceZ > 1e-9) {
        val band = v.rect(0.0, 0.0, spec.faceZ, r)
        drawRect(c.drawRef.copy(alpha = .10f), band.topLeft, band.size)
        drawLine(c.drawRef, Offset(band.left, band.top), Offset(band.left, band.bottom), 1.dp.toPx())
        drawLine(
            c.drawRef, Offset(band.right, band.top), Offset(band.right, band.bottom), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
    // 처음 소재 외곽(점선)
    val init = v.rect(chuckZ, spec.boreDia / 2, spec.faceZ, r)
    drawRect(c.drawRef, init.topLeft, init.size, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
}

/** 남은 소재(면 + 테) · 나사 톱니 — 재생 프레임마다 */
private fun DrawScope.stockRemain(
    spec: StockSpec,
    v: ViewXf,
    profile: StockProfile?,
    crest: (Double, Boolean) -> Double?,
    flags: ViewFlags,
    c: AppColors,
    p: SimPalette,
) {
    val chuckZ = spec.faceZ - spec.length
    val prof = profile ?: return
    for (piece in prof.pieces) {
        if (flags.fill && piece.outline.size >= 3) {
            val path = Path()
            piece.outline.forEachIndexed { i, q ->
                val s = v.toScreen(q)
                if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
            }
            path.close()
            drawPath(path, p.part2d.copy(alpha = .55f))
        }
        for (e in piece.edges) {
            if (e.size < 2) continue
            val path = Path()
            e.forEachIndexed { i, q ->
                val s = v.toScreen(q)
                if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
            }
            drawPath(path, p.partEdge, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
        }
    }
    // 나사(설계 5.4): θ = 0 단면의 톱니 — 남은 소재 면을 바탕색으로 파고 테를 긋는다
    for (th in prof.threads) {
        val a = th.root.start
        val b = th.root.end
        val z0 = max(min(a.z, b.z), chuckZ)
        val z1 = min(max(a.z, b.z), spec.faceZ)
        if (z1 <= z0 || th.lead <= 0) continue
        val half = tan(th.angleDeg / 2 * PI / 180)
        // 골 선의 시작(큰 Z)에서 리드마다 — 보이는 [z0, z1] 안의 것만(나사 시작이 끝면 바깥이어도)
        val top0 = max(a.z, b.z)
        val k0 = max(0, floor((top0 - z1) / th.lead - 0.5).toInt())
        val k1 = min(k0 + 4000, ceil((top0 - z0) / th.lead).toInt())
        val tooth = Path()
        for (k in k0..k1) {
            val zc = top0 - th.lead * (k + 0.5)
            if (zc < z0 || zc > z1) continue
            val f = if (abs(b.z - a.z) < 1e-12) 0.0 else (zc - a.z) / (b.z - a.z)
            val root = a.x + (b.x - a.x) * f
            val top = crest(zc, th.outer) ?: continue
            val depth = if (th.outer) top - root else root - top
            if (depth <= 1e-6) continue
            val w = min(depth * half, th.lead / 2)
            val p0 = v.toScreen(Pt(zc - w, top))
            val p1 = v.toScreen(Pt(zc, root))
            val p2 = v.toScreen(Pt(zc + w, top))
            tooth.moveTo(p0.x, p0.y)
            tooth.lineTo(p1.x, p1.y)
            tooth.lineTo(p2.x, p2.y)
            tooth.close()
        }
        drawPath(tooth, c.viewport)
        drawPath(tooth, p.partEdge, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** 고스트 원(노즈, 반지름 r — 작으면 2.5dp) + 'ㄱ'(가상 인선에서 노즈로 접하는 두 선, 팁 1~4) */
private fun DrawScope.ghost(nose: Pt, tip: Pt, r: Double, tipNo: Int, v: ViewXf, color: Color) {
    val q = v.toScreen(nose)
    if (!inView(q)) return
    val rad = max((r * v.s).toFloat(), 2.5.dp.toPx())
    drawCircle(color.copy(alpha = .18f), rad, q)
    drawCircle(color, rad, q, style = Stroke(1.2.dp.toPx()))
    if (tipNo !in 1..4 || r <= 0) return
    val tv = NoseComp.tipVector(tipNo, r)
    val t = v.toScreen(tip)
    val a = v.toScreen(Pt(tip.z - tv.z, tip.x))
    val b = v.toScreen(Pt(tip.z, tip.x - tv.x))
    drawLine(color, t, a, 1.2.dp.toPx())
    drawLine(color, t, b, 1.2.dp.toPx())
}

/** 각도 알약: 기운 직선마다 Z 축에서 잰 각(06 '56.31°'). 화면에서 28dp 보다 짧으면 뺀다 */
private fun DrawScope.anglePill(a: Pt, b: Pt, v: ViewXf, measurer: TextMeasurer, sans: TextStyle, c: AppColors) {
    val sa = v.toScreen(a)
    val sb = v.toScreen(b)
    if ((sb - sa).getDistance() < 28.dp.toPx()) return
    val mid = (sa + sb) / 2f
    if (!inView(mid)) return
    val deg = atan2(abs(b.x - a.x), abs(b.z - a.z)) * 180 / PI
    val t = measurer.measure("${Fmt.fixed3(deg).dropLast(1)}°", sans.copy(fontSize = 12.sp, color = c.drawAngle))
    val pad = Offset(5.dp.toPx(), 2.dp.toPx())
    val tl = mid - Offset(t.size.width / 2f, t.size.height + 6.dp.toPx())
    drawRoundRect(c.card.copy(alpha = .92f), tl - pad, Size(t.size.width + 2 * pad.x, t.size.height + 2 * pad.y), CornerRadius(5.dp.toPx()))
    drawRoundRect(c.drawAngle, tl - pad, Size(t.size.width + 2 * pad.x, t.size.height + 2 * pad.y), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(t, topLeft = tl)
}

/** 척 닿음 ✕(빨강) */
private fun DrawScope.crossMark(q: Offset, color: Color) {
    val h = 6.dp.toPx()
    drawLine(color, q - Offset(h, h), q + Offset(h, h), 2.5.dp.toPx(), StrokeCap.Round)
    drawLine(color, q + Offset(-h, h), q + Offset(h, -h), 2.5.dp.toPx(), StrokeCap.Round)
}

/** 급송 깎음 ▲(노랑, 바탕 테) */
private fun DrawScope.warnMark(q: Offset, color: Color, bg: Color) {
    val h = 7.dp.toPx()
    val tri = Path().apply {
        moveTo(q.x, q.y - h)
        lineTo(q.x + h, q.y + h * .75f)
        lineTo(q.x - h, q.y + h * .75f)
        close()
    }
    drawPath(tri, bg, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
    drawPath(tri, color)
}

/** 지금 공구: 인서트 다각형(면 + 테) + 노즈 원 + 가상 인선 점 */
private fun DrawScope.currentTool(run: SimRun, play: PlayState, tools: Map<Int, ToolOffset>, v: ViewXf, c: AppColors) {
    val pose = run.poseAt(play.t)
    var i = pose.step.coerceAtMost(run.steps.size - 1)
    while (i >= 0 && run.steps[i] !is MoveStep) i--
    val m = run.steps.getOrNull(i) as? MoveStep
    val r = m?.r ?: 0.0
    val tipNo = m?.tipNo ?: 0
    val shape = ToolShape.polygon((pose.offset?.let { tools[it] } ?: ToolOffset()).copy(noseR = r, tip = tipNo), pose.tip)
    if (shape.size >= 3) {
        val path = Path()
        shape.forEachIndexed { k, q ->
            val s = v.toScreen(q)
            if (k == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
        }
        path.close()
        drawPath(path, c.drawToolBody.copy(alpha = .85f))
        drawPath(path, c.drawInsert, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
    }
    if (r > 0) drawCircle(c.viewNose, (r * v.s).toFloat(), v.toScreen(pose.nose), style = Stroke(1.2.dp.toPx()))
    val q = v.toScreen(pose.tip)
    drawCircle(c.viewport, 3.5.dp.toPx(), q)
    drawCircle(c.drawInsert, 2.5.dp.toPx(), q)
}

/** 측정 십자(초점색 + 옅은 테) + 좌표 알약(초점색 — 원본의 빨간 십자 · 주황 알약 대신, 7.10) */
private fun DrawScope.focusCross(q: Offset, at: Pt, measurer: TextMeasurer, mono: TextStyle, c: AppColors, p: SimPalette) {
    val h = 11.dp.toPx()
    drawCircle(c.lineFocus.copy(alpha = .18f), 9.dp.toPx(), q)
    drawLine(c.lineFocus, q - Offset(h, 0f), q + Offset(h, 0f), 2.dp.toPx())
    drawLine(c.lineFocus, q - Offset(0f, h), q + Offset(0f, h), 2.dp.toPx())
    drawCircle(c.lineFocus, 2.5.dp.toPx(), q)
    val t = measurer.measure("X${Fmt.fixed3(at.xDia)}  Z${Fmt.fixed3(at.z)}", mono.copy(fontSize = 13.sp, color = p.onFocusPill))
    val pad = Offset(7.dp.toPx(), 3.dp.toPx())
    val w = t.size.width + 2 * pad.x
    val hh = t.size.height + 2 * pad.y
    var x = q.x + 14.dp.toPx()
    var y = q.y - 14.dp.toPx() - hh
    if (x + w > size.width - 60.dp.toPx()) x = q.x - 14.dp.toPx() - w
    if (y < 4.dp.toPx()) y = q.y + 14.dp.toPx()
    x = x.coerceIn(4.dp.toPx(), max(4.dp.toPx(), size.width - w - 4.dp.toPx()))
    drawRoundRect(p.focusPill, Offset(x, y), Size(w, hh), CornerRadius(6.dp.toPx()))
    drawText(t, topLeft = Offset(x + pad.x, y + pad.y))
}

/** 알약: Z0(선 위 끝) · ◀ 척 쪽(척 면 위) · 축척 막대(왼쪽 아래, 겹친 기장여유 알약 위) */
private fun DrawScope.pills(v: ViewXf, step: Double, spec: StockSpec?, measurer: TextMeasurer, sans: TextStyle, c: AppColors, insets: List<Dp>) {
    val bg = c.card.copy(alpha = .9f)
    val corner = CornerRadius(6.dp.toPx())
    fun pill(text: String, at: Offset, color: Color = c.textMuted) {
        val t = measurer.measure(text, sans.copy(fontSize = 11.5.sp, color = color))
        val sz = Size(t.size.width + 12.dp.toPx(), t.size.height + 4.dp.toPx())
        val tl = Offset(at.x.coerceIn(4.dp.toPx(), max(4.dp.toPx(), size.width - sz.width - 4.dp.toPx())), at.y)
        drawRoundRect(bg, tl, sz, corner)
        drawText(t, topLeft = tl + Offset(6.dp.toPx(), 2.dp.toPx()))
    }
    // 왼쪽 위 알약 묶음(설정 · 공구 · 범례 — 약 170 × 156dp)을 피한다
    val pillsW = 176.dp.toPx()
    val pillsH = 158.dp.toPx()
    val top = insets[1].toPx() + 2.dp.toPx()
    if (v.t.x in 0f..size.width) pill("Z0 (소재면)", Offset(v.t.x - 10.dp.toPx(), if (v.t.x < pillsW) pillsH else top))
    if (spec != null) {
        // 척 면 바로 왼쪽, 중심선 바로 위(보이면) — 아니면 소재 위끝 위
        val cz = v.toScreen(Pt(spec.faceZ - spec.length, spec.dia / 2))
        val y = if (v.t.y in pillsH..size.height) v.t.y - 26.dp.toPx() else max(cz.y - 26.dp.toPx(), pillsH)
        if (cz.x in 0f..size.width) pill("◀ 척 쪽", Offset(cz.x - 64.dp.toPx(), y))
    }
    // 축척: 격자 한 칸 막대 + "2 mm · X 반경"
    val label = measurer.measure("${stepText(step)} mm · X 반경", sans.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Normal, color = c.textMuted))
    val bar = (step * v.s).toFloat()
    val pad = 8.dp.toPx()
    val boxH = label.size.height + 6.dp.toPx()
    val box = Rect(Offset(pad, size.height - insets[3].toPx() + 6.dp.toPx() - boxH - 46.dp.toPx()), Size(bar + label.size.width + 24.dp.toPx(), boxH))
    drawRoundRect(bg, box.topLeft, box.size, corner)
    val y = box.center.y
    val x0 = box.left + 8.dp.toPx()
    val bw = 2.dp.toPx()
    drawLine(c.textStrong, Offset(x0, y), Offset(x0 + bar, y), bw)
    for (x in listOf(x0, x0 + bar)) drawLine(c.textStrong, Offset(x, y - 3.dp.toPx()), Offset(x, y + 3.dp.toPx()), bw)
    drawText(label, topLeft = Offset(x0 + bar + 8.dp.toPx(), box.top + 3.dp.toPx()))
}

private fun stepText(step: Double): String {
    val r = (step * 1000).toLong()
    return if (r % 1000 == 0L) "${r / 1000}" else step.toString().trimEnd('0')
}

/**
 * 손짓: 한 손가락 · 마우스 끌기 = 이동, 두 손가락 = 확대 · 이동, 휠 = 확대(이 창은 페이지 스크롤이 없다), 움직이지 않고 뗀 한 손가락 = 누르기
 */
private suspend fun PointerInputScope.viewGestures(state: ViewerState, area: () -> Rect, onTap: (Offset) -> Unit) = awaitEachGesture {
    val first = awaitPointerEvent()
    val ch = first.changes.first()
    if (first.type == PointerEventType.Scroll) {
        state.move(state.xf(area()).zoom(1.15f.pow(-ch.scrollDelta.y), ch.position), area())
        ch.consume()
        return@awaitEachGesture
    }
    if (!ch.changedToDown()) return@awaitEachGesture
    val down = ch.position
    val slop = viewConfiguration.touchSlop
    var pan = Offset.Zero
    var zoom = 1f
    var active = false
    var multi = false
    while (true) {
        val e = awaitPointerEvent()
        val pressed = e.changes.count { it.pressed }
        if (pressed == 0) break
        if (pressed >= 2) multi = true
        val pc = e.calculatePan()
        pan += pc
        val zc = e.calculateZoom()
        zoom *= zc
        if (!active) active = pan.getDistance() > slop || abs(1 - zoom) * e.calculateCentroidSize(useCurrent = false) > slop
        if (active) {
            state.move(state.xf(area()).zoom(zc, e.calculateCentroid()).pan(pc), area())
            e.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
    if (!active && !multi) onTap(down)
}
