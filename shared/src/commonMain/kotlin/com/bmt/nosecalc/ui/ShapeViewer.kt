package com.bmt.nosecalc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/** 윤곽의 진행 방향 화살: 없음 · 채운 화살(경로) · 빈 화살(급송) */
enum class Arrow { None, Filled, Hollow }

/**
 * 형상 뷰어에 그릴 윤곽 하나. dashed = 끊은 선(노즈 중심), dotted = 둥근 점 줄(급송),
 * arrows = 화면에서 40dp 넘는 토막마다 가운데에 진행 방향 화살(그런 토막이 없으면 끝에 하나), fill = 닫힌 윤곽의 면 색
 */
@Immutable
data class ViewerLine(
    val contour: Contour,
    val color: Color,
    val width: Dp = 2.dp,
    val dashed: Boolean = false,
    val dotted: Boolean = false,
    val arrows: Arrow = Arrow.None,
    val fill: Color? = null,
)

/** 점 모양. 라벨은 이 순서로 자리를 먼저 잡는다 — T 점(Ring) → 노드(Dot) → 진입·후퇴(Square) → 원호 중심(Cross) */
enum class MarkKind { Ring, Dot, Square, Cross }

/**
 * 형상 뷰어의 점(T 점·보정점 등). 라벨은 점 둘레 알약으로([labels]).
 * info 는 점을 눌러 좌표를 보는 기능(나중)에 쓸 줄들 — 지금은 그리지 않는다.
 * arc = 이 점이 중심인 원호(원호 중심 표시): 중심에서 원호 가운데까지 끊은 지시선(점 색 α .6, 1dp)을 긋고,
 * 화면에서 그 반지름이 14dp 보다 작으면 점 · 지시선 · 라벨을 모두 뺀다(작은 모서리 R 에 몰리지 않게)
 */
@Immutable
data class ViewerMark(
    val at: Pt,
    val color: Color,
    val label: String? = null,
    val kind: MarkKind = MarkKind.Ring,
    val info: List<String> = emptyList(),
    val arc: Arc? = null,
)

/**
 * 소재 단면. 뷰어가 가장 먼저 면 + 45° 빗금으로 칠한다: 외경은 profile 과 중심선 사이, 내경(inner)은 profile 위(뷰어 위 끝까지).
 * 화면 왼쪽 끝(척 쪽)은 extendLeft 면, 오른쪽 끝은 extendRight 면 뷰어 밖까지 늘린다.
 * mirror = 내경의 맞은편 벽도: 축(X0)에 뒤집은 단면을 뷰어 아래 끝까지(지름 전체 단면 — 보링바 간섭)
 */
@Immutable
data class ViewerStock(val profile: Contour, val inner: Boolean, val extendLeft: Boolean = true, val extendRight: Boolean = false, val mirror: Boolean = false)

/** 소재 위에 칠하는 면(깎을 부분 등). 윤곽은 닫힌 것으로 본다 */
@Immutable
data class ViewerFill(val contour: Contour, val color: Color)

/** 직접 정한 보기: 맞춤의 몇 배(zoom)이고 어느 점(center, mm)이 화면 가운데에 오는가 */
@Immutable
data class UserView(val zoom: Float, val center: Pt) {
    /** 확대는 맞춤의 0.5~200배, 가운데는 그림 상자 b 에서 가로·세로 반 폭 밖까지 — 그림을 잃어버리지 않게 */
    fun clampedTo(b: Rect) = UserView(
        zoom.coerceIn(0.5f, 200f),
        Pt(
            center.z.coerceIn(b.left - b.width / 2.0, b.right + b.width / 2.0),
            center.x.coerceIn(b.top - b.height / 2.0, b.bottom + b.height / 2.0),
        ),
    )
}

/** 그림 상자가 b 로 바뀔 때: b 가 보이던 곳(visible, mm)에 걸치면 보기를 두고(상자 안으로 당겨), 아니면 null = 맞춤 */
internal fun keptView(u: UserView, visible: Rect, b: Rect?): UserView? = if (b != null && b.overlaps(visible)) u.clampedTo(b) else null

/**
 * 형상 뷰어의 보기. 카드의 −/+/맞춤 단추가 같이 쓰도록 밖에 둔다. userView = null 이면 맞춤.
 * 확대가 맞춤의 배수라 뷰어 크기가 바뀌어도(폰 회전 · PC 창) 같은 곳이 같은 비율로 보이고,
 * 치수를 고쳐 그림이 바뀌어도 보이던 곳에 새 그림이 걸치면 보기를 그대로 둔다
 */
@Stable
class ViewerState(view: UserView? = null) {
    var userView by mutableStateOf(view)
        private set

    /** 직접 정한 보기인가(맞춤 단추가 켜진 모양이 된다) */
    val isCustom: Boolean get() = userView != null

    /** 잠깐 뜨는 안내(한 손가락으로 끌었을 때 · Ctrl 없이 휠을 굴렸을 때) */
    internal var hint by mutableStateOf<ViewerHint?>(null)
        private set

    /** 지금 그리는 그림 상자(Z 가로 · X 세로). 단추 확대의 가운데와 옮길 한계가 된다 */
    private var bounds: Rect? = null

    /** 화면 가운데를 두고 f 배 */
    fun zoomBy(f: Float) {
        val b = bounds ?: return
        val u = userView ?: UserView(1f, Pt(b.center.x.toDouble(), b.center.y.toDouble()))
        userView = u.copy(zoom = u.zoom * f).clampedTo(b)
    }

    /** 다시 맞춤 */
    fun fit() {
        userView = null
    }

    /** 맞춤 곳(area)에 지금 보기를 놓은 변환 */
    internal fun xf(area: Rect) = ViewXf.of(userView, bounds, area)

    /** 손으로 옮긴 변환 to 를 맞춤의 배수 · 가운데 점으로 되돌려 둔다 */
    internal fun move(to: ViewXf, area: Rect) {
        val b = bounds ?: return
        userView = UserView(to.s / ViewXf.fit(b, area).s, to.toWorld(area.center)).clampedTo(b)
    }

    /** 그림 상자가 바뀌면 [keptView] 로 보기를 두거나 맞춤으로. size = 뷰어 크기(보이던 곳을 셈) */
    internal fun follow(b: Rect?, area: Rect, size: IntSize) {
        if (b == bounds) return
        val old = bounds
        bounds = b
        val u = userView ?: return
        userView = if (old == null) b?.let(u::clampedTo) else keptView(u, ViewXf.of(u, old, area).visible(size), b)
    }

    internal fun showHint(mouse: Boolean) {
        hint = ViewerHint(mouse)
    }

    internal fun clearHint(h: ViewerHint) {
        if (hint === h) hint = null
    }
}

/** 안내 한 번. 같은 안내라도 다시 띄우면 새로 1.5초 */
internal class ViewerHint(val mouse: Boolean)

@Composable
fun rememberViewerState(): ViewerState = remember { ViewerState() }

/**
 * calc 의 윤곽을 그리는 2D 뷰어. 그림 약속은 calc 와 같다 — Z 오른쪽 +, X(반경) 위 +.
 * 처음엔 모두 보이게 맞추고, 두 손가락 확대·이동 · PC 는 Ctrl + 휠 확대와 끌어 이동, 두 번 누르면 다시 맞춤.
 * 한 손가락 끌기와 Ctrl 없는 휠은 페이지 스크롤로 넘기고 안내만 띄운다(oneFingerPan 이면 한 손가락 · 휠도 뷰어가 쓴다).
 * 그리는 순서: 격자(1·2·5 간격, 다섯째 줄 굵게) → 소재(stock) → 면(fills) → 중심선 X0 · Z0(1점 쇄선, 보일 때만) → 윤곽 → 원호 중심 지시선 → 점 →
 * 뷰어 알약([viewerPills]: 척 쪽 · Z0 · 중심 · 축척 막대) → 점 라벨([labels]).
 */
@Composable
fun ShapeViewer(
    lines: List<ViewerLine>,
    modifier: Modifier = Modifier,
    marks: List<ViewerMark> = emptyList(),
    state: ViewerState = rememberViewerState(),
    oneFingerPan: Boolean = false,
    stock: ViewerStock? = null,
    fills: List<ViewerFill> = emptyList(),
) {
    val c = AppTheme.colors
    val bounds = remember(lines, marks) { boundsOf(lines, marks) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    SideEffect { state.follow(bounds, density.fitArea(viewSize.width.toFloat(), viewSize.height.toFloat()), viewSize) }
    val measurer = rememberTextMeasurer()
    val sans = TextStyle(fontFamily = AppTheme.sans, fontWeight = FontWeight.Bold)
    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(c.viewport)
                .onSizeChanged { viewSize = it }
                .pointerInput(state, oneFingerPan) { viewGestures(state, oneFingerPan) }
                .pointerInput(state) { detectTapGestures(onDoubleTap = { state.fit() }) },
        ) {
            val v = state.xf(fitArea(size.width, size.height))
            val step = grid(v, c.viewportGrid, c.viewGridMajor)
            stock?.let { stockSection(it, v, c.drawStock, c.drawHatch) }
            for (f in fills) drawPath(pathOf(f.contour, v), f.color)
            axes(v, c.viewportAxis)
            for (l in lines) viewerLine(l, v, c.viewport)
            val shown = marks.filter { m -> m.arc == null || m.arc.radius * v.s >= 14.dp.toPx() }
            for (m in shown) m.arc?.let { arcLeader(v, m.at, it, m.color) }
            // 라벨 없는 점(보정점 등)을 먼저 — T 점 표시가 그 밑에 묻히지 않게
            for (m in shown.sortedBy { it.label != null }) mark(m.kind, v.toScreen(m.at), m.color, c.viewport)
            val pills = viewerPills(v, step, stock != null, measurer, sans, c, shown.map { v.toScreen(it.at) })
            // 점 라벨 글은 그림 글처럼 시스템 글꼴 1.1배까지(artFontCap) — 큰 글꼴에서 알약끼리 자리가 모자라 빠지지 않게
            // 진행 화살도 알약처럼 피한다 — 선은 알약 밑으로 지나도 읽히지만 방향을 알리는 촉은 가리면 못 읽는다(급송 후퇴 화살 등)
            val arrows = lines.flatMap { l -> arrowsAt(l, v).map { (mid) -> Rect(mid, 6.dp.toPx()) } }
            labels(shown, v, measurer, sans.copy(fontSize = (13f * artFontCap()).sp), inkOf(lines, v), pills + arrows, c.card.copy(alpha = .92f))
        }
        state.hint?.let { HintPill(state, it, Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) }
    }
}

/**
 * 뷰어 알약(카드색 α .9, 둥근 6dp)을 긋고 그 칸들을 돌려준다 — 라벨이 피한다. 왼쪽 위 "◀ 척 쪽"(소재가 있을 때),
 * Z0 선 위 끝 "Z0" · 중심선 왼쪽 "중심"(보일 때), 왼쪽 아래 축척(격자 한 칸 길이 막대 2dp, 양 끝 6dp 눈금 + "2 mm · X 반경").
 * marksAt = 점 표시의 화면 자리 — "중심" 이 덮지 않게 비킨다(내경은 깊은 끝 노드가 축 왼쪽에 선다)
 */
private fun DrawScope.viewerPills(
    v: ViewXf,
    step: Double,
    chuckSide: Boolean,
    measurer: TextMeasurer,
    sans: TextStyle,
    c: AppColors,
    marksAt: List<Offset>,
): List<Rect> {
    val bg = c.card.copy(alpha = .9f)
    val corner = CornerRadius(6.dp.toPx())
    val pad = 8.dp.toPx()
    val scale = measurer.measure(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textStrong)) { append("${stepText(step)} mm") }
            append(" · X 반경")
        },
        sans.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = c.textMuted),
    )
    val bar = (step * v.s).toFloat()
    val scaleBox = Rect(
        Offset(pad, size.height - pad - scale.size.height - 6.dp.toPx()),
        Size(bar + scale.size.width + 24.dp.toPx(), scale.size.height + 6.dp.toPx()),
    )
    drawRoundRect(bg, scaleBox.topLeft, scaleBox.size, corner)
    val y = scaleBox.center.y
    val x0 = scaleBox.left + 8.dp.toPx()
    val bw = 2.dp.toPx()
    drawLine(c.textStrong, Offset(x0, y), Offset(x0 + bar, y), bw)
    for (x in listOf(x0, x0 + bar)) drawLine(c.textStrong, Offset(x, y - 3.dp.toPx()), Offset(x, y + 3.dp.toPx()), bw)
    drawText(scale, topLeft = Offset(x0 + bar + 8.dp.toPx(), scaleBox.top + 3.dp.toPx()))

    val boxes = mutableListOf(scaleBox)
    fun pill(text: String, style: TextStyle, at: (Size) -> Offset): Rect {
        val t = measurer.measure(text, style)
        val sz = Size(t.size.width + 12.dp.toPx(), t.size.height + 4.dp.toPx())
        val r = Rect(at(sz), sz)
        drawRoundRect(bg, r.topLeft, r.size, corner)
        drawText(t, topLeft = r.topLeft + Offset(6.dp.toPx(), 2.dp.toPx()))
        return r.also { boxes += it }
    }
    val chuck = if (chuckSide) pill("◀ 척 쪽", sans.copy(fontSize = 12.sp, color = c.textMuted)) { Offset(pad, pad) } else null
    // 축 이름 글은 textMuted — 다크의 viewportAxis 는 알약 위 대비가 4.5:1 에 못 미친다(라이트는 같은 색)
    val axis = sans.copy(fontSize = 11.sp, color = c.textMuted)
    if (v.t.x in 0f..size.width) {
        pill("Z0", axis) { sz ->
            val top = chuck?.takeIf { it.right > v.t.x - sz.width / 2 }?.let { it.bottom + 4.dp.toPx() } ?: pad
            Offset((v.t.x - sz.width / 2).coerceIn(pad, max(pad, size.width - pad - sz.width)), top)
        }
    }
    if (v.t.y in 0f..size.height) {
        pill("중심", axis) { sz ->
            val mid = Rect(Offset(pad, v.t.y - sz.height / 2), sz)
            val right = Offset(size.width - pad - sz.width, v.t.y - sz.height / 2)
            fun covers(r: Rect) = marksAt.any { r.inflate(8.dp.toPx()).contains(it) }
            when {
                // 축척 · 척 쪽 알약과 겹치면 중심선 위로
                listOfNotNull(scaleBox, chuck).any { it.overlaps(mid) } -> Offset(pad, v.t.y - sz.height - 3.dp.toPx())
                // 점 표시(내경 깊은 끝 노드 등)를 덮으면 중심선 오른쪽 끝으로 — 위 칸은 그 점 · 돔에 또 걸린다
                covers(mid) && !covers(Rect(right, sz)) -> right
                else -> mid.topLeft
            }
        }
    }
    return boxes
}

/**
 * 확대·이동은 두 손가락 · 마우스 끌기 · (oneFingerPan 이면) 한 손가락일 때만 하고, 그때만 이벤트를 먹는다.
 * 한 손가락이 터치 슬롭을 넘으면 페이지 스크롤에 맡기고(손을 뗄 때까지) 안내를 띄운다. 휠은 Ctrl 을 누를 때만 확대
 */
private suspend fun PointerInputScope.viewGestures(state: ViewerState, oneFingerPan: Boolean) = awaitEachGesture {
    val area = fitArea(size.width.toFloat(), size.height.toFloat())
    val first = awaitPointerEvent()
    val ch = first.changes.first()
    if (first.type == PointerEventType.Scroll) {
        if (oneFingerPan || first.keyboardModifiers.isCtrlPressed) {
            state.move(state.xf(area).zoom(1.15f.pow(-ch.scrollDelta.y), ch.position), area)
            ch.consume()
        } else {
            state.showHint(mouse = true)
        }
        return@awaitEachGesture
    }
    // 두 번 누름(detectTapGestures)이 누름을 먼저 먹으므로 먹힌 것도 본다
    if (!ch.changedToDownIgnoreConsumed()) return@awaitEachGesture
    val mouse = ch.type == PointerType.Mouse
    val slop = viewConfiguration.touchSlop
    var pan = Offset.Zero
    var zoom = 1f
    var active = false
    while (true) {
        val e = awaitPointerEvent()
        val pressed = e.changes.count { it.pressed }
        if (pressed == 0) break
        val panChange = e.calculatePan()
        pan += panChange
        if (pressed < 2 && !mouse && !oneFingerPan && !active) {
            if (pan.getDistance() > slop) {
                state.showHint(mouse = false)
                break
            }
            continue
        }
        val zoomChange = e.calculateZoom()
        zoom *= zoomChange
        if (!active) active = pan.getDistance() > slop || abs(1 - zoom) * e.calculateCentroidSize(useCurrent = false) > slop
        if (active) state.move(state.xf(area).zoom(zoomChange, e.calculateCentroid()).pan(panChange), area)
        // 두 손가락은 슬롭 전에도 먹는다 — 페이지 스크롤이 먼저 가져가지 않게. 마우스는 슬롭 뒤에만(두 번 누름이 살게)
        if (active || pressed >= 2) e.changes.forEach { if (it.positionChanged()) it.consume() }
    }
}

/** 뷰어 위 가운데에 잠깐 뜨는 안내 알약. 1.5초 뒤 0.2초에 걸쳐 사라진다(프레임 시계라 스크린샷에서도 같다) */
@Composable
private fun HintPill(state: ViewerState, hint: ViewerHint, modifier: Modifier) {
    val c = AppTheme.colors
    val alpha = remember(hint) { Animatable(1f) }
    LaunchedEffect(hint) {
        alpha.animateTo(0f, tween(200, delayMillis = 1500))
        state.clearHint(hint)
    }
    val shape = RoundedCornerShape(50)
    Text(
        if (hint.mouse) "Ctrl + 휠로 확대 · 끌어서 이동" else "두 손가락으로 확대·이동",
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(shape)
            .background(c.card.copy(alpha = .92f))
            .border(1.dp, c.lineCard, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = c.textStrong,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** 맞춤에 쓰는 곳: 뷰어에서 왼쪽 36 · 위 36 · 오른쪽 52 · 아래 40dp 를 뺀다(가장자리 라벨 · 격자 글 자리) */
private fun Density.fitArea(w: Float, h: Float) = Rect(36.dp.toPx(), 36.dp.toPx(), w - 52.dp.toPx(), h - 40.dp.toPx())

/** 화면 좌표 = t + (z·s, −x·s). s 는 mm 당 px */
internal data class ViewXf(val s: Float, val t: Offset) {
    fun toScreen(p: Pt) = Offset(t.x + p.z.toFloat() * s, t.y - p.x.toFloat() * s)

    fun toWorld(o: Offset) = Pt(((o.x - t.x) / s).toDouble(), ((t.y - o.y) / s).toDouble())

    fun zoom(f: Float, focus: Offset) = ViewXf(s * f, focus - (focus - t) * f)

    fun pan(d: Offset) = copy(t = t + d)

    /** size 크기 뷰어에 보이는 곳(mm): left/right = Z, top/bottom = X */
    fun visible(size: IntSize): Rect {
        val a = toWorld(Offset.Zero)
        val b = toWorld(Offset(size.width.toFloat(), size.height.toFloat()))
        return Rect(a.z.toFloat(), b.x.toFloat(), b.z.toFloat(), a.x.toFloat())
    }

    companion object {
        /** 그림 상자 b 를 area(여백을 뺀 곳) 가운데에 꽉 차게 */
        fun fit(b: Rect?, area: Rect): ViewXf {
            if (b == null) return ViewXf(10f, area.center)
            // Rect 의 left/right = Z, top/bottom = X(반경)
            val s = min(max(area.width, 1f) / max(b.width, 1e-3f), max(area.height, 1f) / max(b.height, 1e-3f))
            return ViewXf(s, area.center - Offset(b.center.x * s, -b.center.y * s))
        }

        /** 맞춤 위에 직접 정한 보기 u 를 얹는다: 맞춤의 u.zoom 배, u.center 가 area 가운데 */
        fun of(u: UserView?, b: Rect?, area: Rect): ViewXf {
            val f = fit(b, area)
            if (u == null) return f
            val s = f.s * u.zoom
            return ViewXf(s, area.center - Offset(u.center.z.toFloat() * s, -u.center.x.toFloat() * s))
        }
    }
}

/** 모든 윤곽·점을 품는 상자(Z 가 가로, X 가 세로). 그릴 것이 없으면 null */
internal fun boundsOf(lines: List<ViewerLine>, marks: List<ViewerMark>): Rect? {
    val pts = lines.flatMap { l -> l.contour.segs.flatMap { seg -> if (seg is Arc) arcPoints(seg, 16) else listOf(seg.start, seg.end) } } +
        marks.map { it.at }
    if (pts.isEmpty()) return null
    return Rect(
        pts.minOf { it.z }.toFloat(), pts.minOf { it.x }.toFloat(),
        pts.maxOf { it.z }.toFloat(), pts.maxOf { it.x }.toFloat(),
    )
}

private fun arcPoints(a: Arc, n: Int): List<Pt> {
    val a0 = atan2(a.start.x - a.center.x, a.start.z - a.center.z)
    val sweep = a.sweepDeg * PI / 180 * (if (a.cw) -1 else 1)
    return (0..n).map { i ->
        val t = a0 + sweep * i / n
        Pt(a.center.z + a.radius * cos(t), a.center.x + a.radius * sin(t))
    }
}

private fun pathOf(c: Contour, v: ViewXf): Path = Path().apply {
    val first = c.segs.firstOrNull() ?: return@apply
    v.toScreen(first.start).let { moveTo(it.x, it.y) }
    for (seg in c.segs) {
        when (seg) {
            is Line -> v.toScreen(seg.end).let { lineTo(it.x, it.y) }
            is Arc -> {
                if (seg.sweepDeg == 0.0) {
                    v.toScreen(seg.end).let { lineTo(it.x, it.y) }
                    continue
                }
                // 화면은 y 가 아래로 커져 각도 부호가 뒤집힌다: 시작각 = −(Z 축에서 잰 각), G02(시계) = 양의 쓸기
                val a0 = atan2(seg.start.x - seg.center.x, seg.start.z - seg.center.z) * 180 / PI
                val cs = v.toScreen(seg.center)
                val r = seg.radius.toFloat() * v.s
                arcTo(Rect(cs, r), (-a0).toFloat(), (if (seg.cw) seg.sweepDeg else -seg.sweepDeg).toFloat(), false)
            }
        }
    }
}

/** 격자를 긋고 간격(mm)을 돌려준다. 0 에서 다섯째 줄마다 major 색 */
private fun DrawScope.grid(v: ViewXf, color: Color, major: Color): Double {
    val raw = 30.dp.toPx() / v.s
    val p = 10.0.pow(floor(log10(raw.toDouble())))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * p }.first { it >= raw }
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
    return step
}

/** 중심선(X0)과 Z0 — 둘 다 1점 쇄선(기준선, 1.2dp). 끊은 선 · 점선은 노즈 중심 · 급송이라 쓰지 않는다 */
private fun DrawScope.axes(v: ViewXf, color: Color) {
    val y = v.t.y
    if (y in 0f..size.height) centerLine(Offset(0f, y), Offset(size.width, y), color, 1.2f)
    val x = v.t.x
    if (x in 0f..size.width) centerLine(Offset(x, 0f), Offset(x, size.height), color, 1.2f)
}

/** 윤곽 하나: 닫혔으면 면(fill) → 선(실선 · 끊은 선 7/5dp · 둥근 점 5dp 간격) → 진행 화살 */
private fun DrawScope.viewerLine(l: ViewerLine, v: ViewXf, bg: Color) {
    val path = pathOf(l.contour, v)
    val segs = l.contour.segs
    if (l.fill != null && segs.isNotEmpty() && (segs.last().end - segs.first().start).length < 1e-6) drawPath(path, l.fill)
    val effect = when {
        l.dotted -> PathEffect.dashPathEffect(floatArrayOf(0.1.dp.toPx(), 5.dp.toPx()))
        l.dashed -> PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
        else -> null
    }
    drawPath(path, l.color, style = Stroke(l.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    val half = 5.dp.toPx()
    // 촉 길이 10dp · 밑변 7dp 이등변 삼각형. 빈 화살은 바탕색으로 속을 채우고 1.5dp 테두리
    for ((mid, d) in arrowsAt(l, v)) {
        val u = d / max(d.getDistance(), 1e-3f)
        val n = Offset(-u.y, u.x) * 3.5.dp.toPx()
        val tip = mid + u * half
        val base = mid - u * half
        val tri = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + n.x, base.y + n.y)
            lineTo(base.x - n.x, base.y - n.y)
            close()
        }
        if (l.arrows == Arrow.Filled) {
            drawPath(tri, l.color)
        } else {
            drawPath(tri, bg)
            drawPath(tri, l.color, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

/** 진행 화살 자리(화살 가운데, 화면 방향): 화면에서 40dp 넘는 토막의 가운데. 그런 토막이 없으면 촉이 끝점에 닿게 하나 */
private fun DrawScope.arrowsAt(l: ViewerLine, v: ViewXf): List<Pair<Offset, Offset>> {
    val segs = l.contour.segs
    if (l.arrows == Arrow.None || segs.isEmpty()) return emptyList()
    val half = 5.dp.toPx()
    return segs.mapNotNull { seg ->
        when (seg) {
            is Line -> {
                val a = v.toScreen(seg.start)
                val b = v.toScreen(seg.end)
                if ((b - a).getDistance() >= 40.dp.toPx()) (a + b) / 2f to (b - a) else null
            }
            is Arc -> if (seg.radius * seg.sweepDeg * PI / 180 * v.s >= 40.dp.toPx()) {
                val t = arcAngle(seg, .5)
                v.toScreen(seg.center + Pt(cos(t), sin(t)) * seg.radius) to tangent(seg, t)
            } else {
                null
            }
        }
    }.ifEmpty {
        // 짧은 토막뿐이면 끝 토막에 하나 — 촉이 끝점 표시(후퇴 □ 8dp 등)에 붙지 않게 끝에서 9dp 앞(□ 가장자리에서 약 4dp)에서 멈춘다.
        // 그만큼도 안 되면 화살 없이(끝 표시가 끝을 알린다)
        val last = segs.last()
        val d = if (last is Arc) tangent(last, arcAngle(last, 1.0)) else v.toScreen(last.end) - v.toScreen(last.start)
        val back = 9.dp.toPx()
        if ((v.toScreen(last.end) - v.toScreen(last.start)).getDistance() < 2 * half + back + 4.dp.toPx()) emptyList()
        else listOf(v.toScreen(last.end) - d / max(d.getDistance(), 1e-3f) * (half + back) to d)
    }
}

/** 원호 중심 at 에서 원호 a 의 가운데까지 끊은 지시선(1dp · 4/3dp, 색 α .6) */
private fun DrawScope.arcLeader(v: ViewXf, at: Pt, a: Arc, color: Color) {
    val t = arcAngle(a, .5)
    val mid = a.center + Pt(cos(t), sin(t)) * a.radius
    drawLine(color.copy(alpha = .6f), v.toScreen(at), v.toScreen(mid), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
}

/** 원호의 시작각 + 쓸기 × f 인 각(라디안, Z 축에서 X 쪽으로) */
private fun arcAngle(a: Arc, f: Double) = atan2(a.start.x - a.center.x, a.start.z - a.center.z) + a.sweepDeg * PI / 180 * f * (if (a.cw) -1 else 1)

/** 원호가 각 t 에서 나아가는 화면 방향: 반시계면 (−sin, cos), 시계면 그 반대. 화면은 y 가 아래라 y 를 뒤집는다 */
private fun tangent(a: Arc, t: Double): Offset {
    val k = if (a.cw) -1f else 1f
    return Offset(-sin(t).toFloat() * k, -cos(t).toFloat() * k)
}

/**
 * 점 하나. Ring = 반지름 5dp 빈 원(2.5dp) · Dot = 3dp 채운 점(바탕색 1dp 테) · Square = 8dp 빈 네모(1.8dp) · Cross = 팔 6dp 십자(1.5dp).
 * bg 는 빈 모양의 속과 테 색(뷰어는 뷰어 바탕, 범례는 카드)
 */
internal fun DrawScope.mark(kind: MarkKind, p: Offset, color: Color, bg: Color) {
    when (kind) {
        MarkKind.Ring -> {
            drawCircle(bg, 5.dp.toPx(), p)
            drawCircle(color, 5.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
        }
        MarkKind.Dot -> {
            drawCircle(bg, 4.dp.toPx(), p)
            drawCircle(color, 3.dp.toPx(), p)
        }
        MarkKind.Square -> {
            val r = Rect(p, 4.dp.toPx())
            drawRect(bg, r.topLeft, r.size)
            drawRect(color, r.topLeft, r.size, style = Stroke(1.8.dp.toPx()))
        }
        MarkKind.Cross -> {
            val a = 6.dp.toPx()
            val w = 1.5.dp.toPx()
            drawLine(color, p - Offset(a, 0f), p + Offset(a, 0f), w)
            drawLine(color, p - Offset(0f, a), p + Offset(0f, a), w)
        }
    }
}

/**
 * 소재 단면: profile(원호는 3° 마다 꺾은선)의 화면 왼쪽 끝을 x = −10px 까지(extendLeft), 오른쪽 끝을 뷰어 밖까지(extendRight) 늘리고
 * 외경은 중심선을 따라, 내경은 뷰어 위 밖(y = −10px)을 따라 닫아 면 + 45° 빗금(9dp 간격)으로 칠한다.
 * mirror 면 축에 뒤집은 단면을 뷰어 아래 밖(y = 높이 + 10px)까지 닫아 함께 칠한다
 */
private fun DrawScope.stockSection(st: ViewerStock, v: ViewXf, fill: Color, hatch: Color) {
    val pts = st.profile.segs.flatMap { seg ->
        if (seg is Arc) arcPoints(seg, max(1, ceil(seg.sweepDeg / 3).toInt())) else listOf(seg.start, seg.end)
    }.map(v::toScreen)
    if (pts.size < 2) return
    val p = if (pts.first().x <= pts.last().x) pts else pts.reversed()
    val out = 10f
    val left = if (st.extendLeft) Offset(min(p.first().x, -out), p.first().y) else p.first()
    val right = if (st.extendRight) Offset(max(p.last().x, size.width + out), p.last().y) else p.last()
    // 축 아래로 뒤집은 점(화면 y 를 중심선에서 뒤집는다)
    fun flip(q: Offset) = Offset(q.x, 2 * v.t.y - q.y)
    val section = Path().apply {
        // (닫는 높이, 뒤집었나): 윗 단면 · 맞은편 벽
        for ((edge, flipped) in listOfNotNull((if (st.inner) -out else v.t.y) to false, (size.height + out to true).takeIf { st.mirror })) {
            val ring = (listOf(left) + p + right).map { if (flipped) flip(it) else it }
            moveTo(ring.first().x, edge)
            for (q in ring) lineTo(q.x, q.y)
            lineTo(ring.last().x, edge)
            close()
        }
    }
    // 크게 확대하면 면이 화면보다 훨씬 커진다 — 뷰어 칸으로 잘라 빗금 수를 묶는다
    val view = Path().apply { addRect(Rect(-out, -out, size.width + out, size.height + out)) }
    hatchFill(Path().apply { op(section, view, PathOperation.Intersect) }, fill, hatch, 1.5f)
}

/** 라벨이 피할 선: 윤곽(원호는 16 토막)과 보이는 중심선 X0 · Z0 의 화면 선분 */
private fun DrawScope.inkOf(lines: List<ViewerLine>, v: ViewXf): List<Pair<Offset, Offset>> =
    lines.flatMap { l ->
        l.contour.segs.flatMap { seg -> if (seg is Arc) arcPoints(seg, 16) else listOf(seg.start, seg.end) }.map(v::toScreen).zipWithNext()
    } + listOfNotNull(
        (Offset(0f, v.t.y) to Offset(size.width, v.t.y)).takeIf { v.t.y in 0f..size.height },
        (Offset(v.t.x, 0f) to Offset(v.t.x, size.height)).takeIf { v.t.x in 0f..size.width },
    )

/**
 * 점 라벨 = 알약(둥근 5dp · 바탕 bg · 점 색 α .55 테두리 1dp · 13sp 굵은 점 색 글). 점에서 9dp 띄운 여덟 곳
 * (오른쪽 위 · 오른쪽 · 오른쪽 아래 · 아래 · 왼쪽 아래 · 왼쪽 · 왼쪽 위 · 위)에 점수를 매겨 가장 낮은 곳(같으면 앞 순서)에 둔다:
 * 뷰어 4dp 안쪽을 벗어나는 곳은 빼고, 겹친 것마다 앞서 둔 라벨 4 · 다른 점(10dp 칸) 3 · 뷰어 알약 · 진행 화살(avoid, 화살은 12dp 칸) 4,
 * 같은 꼴 점이 제 점만큼(2dp 안) 가까우면 3 · 다른 꼴 점이 더 가까우면 0.75(옆 점 라벨로 읽힌다), 지나는 선 토막마다 0.5(1 까지 — 선은 알약 밑으로
 * 지나도 읽힌다), 점에서 그 자리까지(지시선)가 지나는 앞 라벨마다 2.
 * 가까운 곳이 다 막히면 16dp 씩 더 띄운 바깥 세 겹(겹마다 1)까지 보고, 점에서 16dp 넘게 떨어지거나 같은 꼴 다른 점에 더 가까우면 점 색 α .6 의 1dp 지시선을 긋는다.
 * 모두 뷰어를 벗어나면 가장 나은 곳을 뷰어 안으로 당긴다. 가장 나은 곳도 앞서 둔 라벨과 겹치면 라벨은 빼고 점 표시만 남긴다.
 * [MarkKind] 순서(T 점 → 노드 → 진입·후퇴 → 원호 중심)로 자리를 잡는다. 화면 밖 점은 라벨도 없다
 */
private fun DrawScope.labels(
    marks: List<ViewerMark>,
    v: ViewXf,
    measurer: TextMeasurer,
    style: TextStyle,
    ink: List<Pair<Offset, Offset>>,
    avoid: List<Rect>,
    bg: Color,
) {
    val view = Rect(Offset.Zero, size)
    val inside = view.deflate(4.dp.toPx())
    val pts = marks.map { v.toScreen(it.at) }
    val dots = pts.map { Rect(it, 5.dp.toPx()) }
    val placed = mutableListOf<Rect>()
    for ((i, m) in marks.withIndex().sortedBy { it.value.kind }) {
        val text = m.label ?: continue
        val p = pts[i]
        if (!view.contains(p)) continue
        val t = measurer.measure(text, style.copy(color = m.color))
        val sz = Size(t.size.width + 10.dp.toPx(), t.size.height + 3.dp.toPx())
        val w = sz.width
        val h = sz.height
        // (자리, 겹): 9dp · 25 · 41 · 57dp 띄운 여덟 곳씩
        val spots = (0..3).flatMap { k ->
            val g = (9 + 16 * k).dp.toPx()
            listOf(
                Offset(p.x + g, p.y - g - h), Offset(p.x + g, p.y - h / 2), Offset(p.x + g, p.y + g), Offset(p.x - w / 2, p.y + g),
                Offset(p.x - g - w, p.y + g), Offset(p.x - g - w, p.y - h / 2), Offset(p.x - g - w, p.y - g - h), Offset(p.x - w / 2, p.y - g - h),
            ).map { Rect(it, sz) to k }
        }
        fun score(s: Pair<Rect, Int>): Double {
            val r = s.first
            val own = r.distanceTo(p)
            return 4.0 * placed.count { it.overlaps(r) } +
                3.0 * dots.withIndex().count { (j, d) -> j != i && d.overlaps(r) } +
                4.0 * avoid.count { it.overlaps(r) } +
                // 같은 꼴 점(노드 둘 등)이 제 점만큼(2dp 안) 가까우면 그 점 라벨로 읽힌다 — 점 겹침과 같은 3(가까운 두 노드에서 번호가 뒤바뀌어 보였다).
                // 다른 꼴(진입 □ 등)은 제 점보다 더 가까울 때만 0.75
                when {
                    pts.withIndex().any { (j, q) -> j != i && marks[j].kind == m.kind && r.distanceTo(q) < own + 2.dp.toPx() } -> 3.0
                    pts.withIndex().any { (j, q) -> j != i && r.distanceTo(q) < own - 1f } -> 0.75
                    else -> 0.0
                } +
                0.5 * min(2, ink.count { (a, b) -> r.crossedBy(a, b) }) +
                // 점에서 라벨까지(지시선 자리)가 앞서 둔 라벨을 지나면 — 지시선이 글 사이로 보인다
                2.0 * placed.count { !it.overlaps(r) && it.crossedBy(p, r.nearest(p)) } +
                s.second
        }
        val box = spots.filter { (r) -> inside.contains(r.topLeft) && inside.contains(r.bottomRight) }.minByOrNull(::score)?.first
            ?: spots.minBy(::score).first.let { r ->
                r.translate(
                    (inside.left - r.left).coerceAtLeast(0f) + (inside.right - r.right).coerceAtMost(0f),
                    (inside.top - r.top).coerceAtLeast(0f) + (inside.bottom - r.bottom).coerceAtMost(0f),
                )
            }
        // 가장 나은 곳도 둔 라벨과 겹치면 알약은 빼고 점 표시만(겹친 두 글은 다 못 읽는다 — 먼저 둔 것이 남는다)
        if (placed.any { it.overlaps(box) }) continue
        placed += box
        val near = box.nearest(p)
        val far = (near - p).getDistance()
        // 16dp 넘게 떨어졌거나, 제 점보다 같은 꼴 다른 점에 더 가까우면(그 점 라벨로 읽힌다) 지시선
        val misread = far > 8.dp.toPx() && pts.withIndex().any { (j, q) -> j != i && marks[j].kind == m.kind && box.distanceTo(q) < far - 1f }
        if (far > 16.dp.toPx() || misread) drawLine(m.color.copy(alpha = .6f), p + (near - p) * (6.dp.toPx() / far), near, 1.dp.toPx())
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(bg, box.topLeft, box.size, r)
        drawRoundRect(m.color.copy(alpha = .55f), box.topLeft, box.size, r, style = Stroke(1.dp.toPx()))
        drawText(t, topLeft = box.topLeft + Offset(5.dp.toPx(), 1.5.dp.toPx()))
    }
}

/** 칸에서 p 에 가장 가까운 곳 */
private fun Rect.nearest(p: Offset) = Offset(p.x.coerceIn(left, right), p.y.coerceIn(top, bottom))

private fun Rect.distanceTo(p: Offset) = (nearest(p) - p).getDistance()

/** 화면 선분 a–b 가 칸을 지나는가(Liang–Barsky 자르기) */
private fun Rect.crossedBy(a: Offset, b: Offset): Boolean {
    val d = b - a
    var t0 = 0f
    var t1 = 1f
    for ((p, q) in listOf(-d.x to a.x - left, d.x to right - a.x, -d.y to a.y - top, d.y to bottom - a.y)) {
        if (p == 0f) {
            if (q < 0f) return false
            continue
        }
        val r = q / p
        if (p < 0f) {
            if (r > t1) return false
            t0 = max(t0, r)
        } else {
            if (r < t0) return false
            t1 = min(t1, r)
        }
    }
    return true
}

private fun stepText(step: Double): String {
    val r = (step * 1000).toLong()
    return if (r % 1000 == 0L) "${r / 1000}" else (step.toString().trimEnd('0'))
}
