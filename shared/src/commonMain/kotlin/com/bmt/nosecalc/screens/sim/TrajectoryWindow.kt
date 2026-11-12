package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.Contact
import com.bmt.nosecalc.calc.sim.ContactKind
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.SimPose
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockProfile
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.CircleIconButton
import com.bmt.nosecalc.ui.CoordPair
import com.bmt.nosecalc.ui.Overlay
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.ViewerState
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.onFill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** 궤적 창 위에 여는 시트 */
internal enum class TrajSheet { SETTINGS, TOOLS, LEGEND }

/**
 * 궤적 창의 화면 상태(설계 8.6 `PickState` 자리): 고른 고스트 · 측정 · 각도 · 시트 · 코드 목록 · 2D 보기 · 3D(켬 · 카메라).
 * [lastT] = 마지막 재생 시각 — 설정을 바꿔 실행이 바뀌어도 그 자리에서 이어 본다.
 */
@Stable
internal class TrajUi(var lastT: Double = 0.0) {
    var pick by mutableStateOf<Pick?>(null)
    var measure by mutableStateOf(false)
    var angle by mutableStateOf(false)
    var sheet by mutableStateOf<TrajSheet?>(null)
    var listOpen by mutableStateOf(false)
    val viewer = ViewerState()

    /** 위 띠의 2D/3D — 3D 면 남은 소재 몸체(설계 6), 2D 와 같은 재생 · 카드 */
    var mode3d by mutableStateOf(false)
    val cam = Cam3D()
}

/** 코드 띠 접힌 높이(앞 · 지금 · 다음 줄 + 손잡이) · 낮은 뷰포트(지금 줄만) */
private val STRIP_H = 100.dp
private val STRIP_COMPACT = 50.dp

/**
 * 궤적 창 화면 B(설계 7.4 · 05 · 06 · 10): 화면 전체를 덮는 위층(뒤로 키 · Esc 로 닫힘).
 * 위 띠 → (멈춤 · 닿음 띠) → 뷰포트(2D 뷰 + 설정 알약 · S/F 카드 · 오른쪽 단추 · 기장여유 알약 · 코드 띠) → 좌표 카드 → 가공 시간 · 속도 → 단추 줄.
 * 재생은 Compose 프레임 시계(withFrameNanos)로 — 폰 · PC 같은 빠르기. 소재는 실행이 바뀔 때 백그라운드에서 끝까지 한 번 깎아(닿음을 미리 알고)
 * 재생 시각을 따라 이어 깎는다(StockSim — 앞으로는 이어서, 뒤로는 저장점에서).
 */
@Composable
internal fun TrajectoryWindow(sim: SimState, ui: TrajUi, onClose: () -> Unit, onEditor: (Int) -> Unit) {
    val app = LocalAppState.current
    val run = sim.run
    val play = remember(run) { PlayState(run).also { it.seek(ui.lastT) } }
    val paths = remember(run) { SimPaths(run) }
    val stockText = sim.stockText
    val stockRes = remember(run, stockText) { stockOf(stockText, run) }
    val spec = stockRes.first
    val tools = sim.tools
    var stock by remember(run, spec, tools) { mutableStateOf<StockSim?>(null) }
    var contacts by remember(run, spec, tools) { mutableStateOf(emptyList<Contact>()) }
    // 단면은 재생 프레임마다 바뀐다 — 상태를 그대로 넘겨 2D 뷰의 그리기에서만 읽는다(창 전체를 다시 짜지 않게)
    val profile = remember(stock) { mutableStateOf<StockProfile?>(null) }

    LaunchedEffect(play) { snapshotFlow { play.t }.collect { ui.lastT = it } }
    // 실행이 바뀌면(설정 · 공구표) 없는 고스트를 고른 채 두지 않는다
    LaunchedEffect(run) { ui.pick = ui.pick?.takeIf { it.ghost < run.ghosts.size } }
    // 소재: 끝까지 한 번(닿음 · 척 멈춤을 미리) → 재생 시각을 따라
    LaunchedEffect(run, spec, tools) {
        play.setChuck(null)
        if (spec == null) return@LaunchedEffect
        val s = withContext(Dispatchers.Default) { StockSim(spec, run, tools).also { it.cutTo(run.totalSec) } }
        contacts = s.contacts
        play.setChuck(s.contacts.firstOrNull { it.kind == ContactKind.CHUCK }?.t)
        stock = s
    }
    LaunchedEffect(stock) {
        val s = stock ?: return@LaunchedEffect
        val tol = max(0.002, s.spec.length / 15_000)
        snapshotFlow { play.t }.collect { t ->
            s.cutTo(t)
            profile.value = s.profile(tol)
        }
    }
    // 재생: 프레임마다 실제 흐른 시간 × 속도
    LaunchedEffect(play, play.playing) {
        if (!play.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (play.playing) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9).coerceIn(0.0, 0.1)
                last = now
                play.advance(dt, sim.speed, sim.singleBlock, sim.optStop)?.let { ev -> eventText(ev, run, play)?.let(app::toast) }
            }
        }
    }

    Overlay(onClose, onKey = { e -> trajKey(e, play, ui) }) {
        val c = AppTheme.colors
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val short = maxHeight < 760.dp
            Column(Modifier.fillMaxSize().background(c.page).pointerInput(Unit) { detectTapGestures { } }) {
                TopBar(ui, onClose)
                StatusBand(run, contacts, spec, stockText.values.all { it.isBlank() }, onEditor, onTools = { ui.sheet = TrajSheet.TOOLS })
                Viewport(sim, ui, run, play, paths, spec, profile, stock, contacts, Modifier.weight(1f).fillMaxWidth())
                // 아래 카드 · 단추는 넓은 PC 창에서 가운데 720dp 로(뷰포트만 창 폭 그대로)
                Box(Modifier.fillMaxWidth().background(c.page).windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = if (short) 6.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (short) 6.dp else 8.dp),
                    ) {
                        CoordCard(run, play, ui, short)
                        TimeSpeedRow(sim, play, paths.model, short)
                        ButtonRow(sim, play, short)
                    }
                }
            }
        }
    }
    when (ui.sheet) {
        TrajSheet.SETTINGS -> SimSettingsSheet(sim, run, stockRes.second) { ui.sheet = null }
        TrajSheet.TOOLS -> ToolSettingsSheet(sim, run) { ui.sheet = null }
        TrajSheet.LEGEND -> LegendSheet(paths, sim.view) { ui.sheet = null }
        null -> {}
    }
}

/** 재생이 스스로 멈춘 까닭의 토스트(싱글 블록은 조용히) */
private fun eventText(ev: PlayEvent, run: SimRun, play: PlayState): String? = when (ev) {
    is PlayEvent.Stop ->
        if (ev.optional) "M01 — ${lineLabel(ev.line)}에서 멈춤(선택 정지 켬) · 재생을 누르면 이어서"
        else "M00 — ${lineLabel(ev.line)}에서 멈춤 · 재생을 누르면 이어서"
    PlayEvent.BlockEnd -> null
    PlayEvent.End -> run.stop?.let { "${lineLabel(it.line)}에서 멈춤 — 위 빨간 띠를 보세요" } ?: "끝 — 가공 시간 ${timeText(run.totalSec)}"
    PlayEvent.Chuck -> "척에 닿음 — ${lineLabel(run.poseAt(play.t).line)}에서 재생을 멈춥니다"
}

/** PC 키: 빈칸 재생/멈춤 · ← |◀ · → ▶| · Home 리셋 · + − 0 확대 · 축소 · 맞춤(3D 는 카메라) */
private fun trajKey(e: KeyEvent, play: PlayState, ui: TrajUi): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val view = ui.viewer
    val cam = ui.cam
    when (e.key) {
        Key.Spacebar -> play.toggle()
        Key.DirectionLeft -> play.blockBack()
        Key.DirectionRight -> play.blockForward()
        Key.MoveHome -> play.reset()
        Key.Plus, Key.Equals, Key.NumPadAdd -> if (ui.mode3d) cam.zoomBy(1.5f) else view.zoomBy(1.5f)
        Key.Minus, Key.NumPadSubtract -> if (ui.mode3d) cam.zoomBy(1 / 1.5f) else view.zoomBy(1 / 1.5f)
        Key.Zero, Key.NumPad0 -> if (ui.mode3d) cam.fit() else view.fit()
        else -> return false
    }
    return true
}

/** ① 위 띠(흰 카드 52dp): 모니터 + 제목 · 2D/3D(2D 고르면 파랑 · 3D 초록 — 05 · 06) · 도움말 · 닫기(중립 — 7.10) */
@Composable
private fun TopBar(ui: TrajUi, onClose: () -> Unit) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(c.card).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SimIcons.Monitor, null, Modifier.size(22.dp), tint = c.blue)
            Spacer(Modifier.width(8.dp))
            BasicText(
                keepWords("가공 궤적 시뮬레이션 확인"),
                Modifier.weight(1f),
                style = TextStyle(color = c.textStrong, fontFamily = AppTheme.sans, fontWeight = FontWeight.Bold, lineHeight = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(13.sp, 16.sp, 0.5.sp),
            )
            Spacer(Modifier.width(6.dp))
            Segmented(
                listOf("2D", "3D"), if (ui.mode3d) 1 else 0, { ui.mode3d = it == 1 }, Modifier.width(104.dp),
                color = if (ui.mode3d) c.green else c.blue, height = 40.dp, fontSize = 15,
            )
            CircleIconButton(AppIcons.Help, "도움말", { app.helpOpen = true }, outlined = true, size = 40.dp)
            CircleIconButton(AppIcons.Close, "닫기", onClose, size = 40.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
    }
}

/**
 * ⑩ 멈춤 · 닿음 띠(뷰포트 위, 한두 줄): 실행이 멈춘 줄(빨강 + [편집기로] — 공구 값이 없으면 [공구 설정]) · 척 닿음(빨강) ·
 * 형식 C 의 G40 옮김이 앞 블록 면 쪽으로 들어가며 깎음(노랑 — 팁 번호 확인) · 안쪽 공구의 몸통(바 지름)이 소재에 닿음(노랑 — 바 지름 확인 + [공구 설정], 설계 20절) ·
 * 급송 깎음(노랑). 노랑 셋은 소재가 자동이면 그 말을 붙인다. 없으면 그리지 않는다.
 */
@Composable
private fun StatusBand(run: SimRun, contacts: List<Contact>, spec: StockSpec?, autoStock: Boolean, onEditor: (Int) -> Unit, onTools: () -> Unit) {
    val stop = run.stop
    val chuck = contacts.firstOrNull { it.kind == ContactKind.CHUCK }
    val rapid = contacts.filter { it.kind == ContactKind.RAPID_CUT }
    val cancel = contacts.filter { it.kind == ContactKind.CANCEL_CUT }
    val body = contacts.filter { it.kind == ContactKind.BODY }
    val auto = if (autoStock && spec != null) " — 소재가 자동 값입니다" else ""
    var toTools = stop?.code == IssueCode.TOOL_DATA_MISSING
    val (level, text, line) = when {
        stop != null -> Triple(IssueLevel.STOP, "${lineLabel(stop.line)}에서 멈춤 — ${simIssueText(stop)}", stop.line)
        chuck != null -> Triple(IssueLevel.STOP, "${lineLabel(chuck.line)}: 공구가 척에 닿습니다 — 재생이 그 자리에서 멈춥니다 (${xzText(chuck.at)})", chuck.line)
        cancel.isNotEmpty() -> {
            val k = cancel.first()
            val more = if (cancel.size > 1) " 외 ${cancel.size - 1}곳" else ""
            Triple(IssueLevel.WARN, "${lineLabel(k.line)} G40 줄의 노즈 옮김(형식 C)이 소재를 깎습니다 (${xzText(k.at)})$more — 공구 설정의 팁 번호를 확인하세요$auto", k.line)
        }
        body.isNotEmpty() -> {
            val k = body.first()
            val more = if (body.size > 1) " 외 ${body.size - 1}곳" else ""
            toTools = true
            Triple(IssueLevel.WARN, "${lineLabel(k.line)}: 공구 몸통(바)이 소재에 닿습니다 (${xzText(k.at)})$more — 바 지름을 확인하세요$auto", k.line)
        }
        rapid.isNotEmpty() -> {
            val k = rapid.first()
            val more = if (rapid.size > 1) " 외 ${rapid.size - 1}곳" else ""
            Triple(IssueLevel.WARN, "${lineLabel(k.line)} G00 이 소재를 지납니다 (${xzText(k.at)})$more$auto", k.line)
        }
        else -> return
    }
    val c = AppTheme.colors
    val rc = c.roleOf(level)
    Row(
        Modifier
            .fillMaxWidth()
            .background(rc.bg)
            .drawWithContent {
                drawContent()
                drawRect(rc.label, size = Size(4.dp.toPx(), size.height))
            }
            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconOf(level), null, Modifier.size(18.dp), tint = rc.label)
        Spacer(Modifier.width(8.dp))
        Text(keepWords(text), Modifier.weight(1f), color = rc.value, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (toTools) PillButton("공구 설정", SimIcons.Wrench, onTools)
        else if (line > 0) PillButton("편집기로", null, { onEditor(line) })
    }
}

internal fun xzText(p: Pt) = "X${Fmt.fixed3(p.xDia)} Z${Fmt.fixed3(p.z)}"

/**
 * 뷰포트: 2D 뷰 또는 3D 보기 + 겹친 것들(설계 7.4 ②~⑥). 3D(05): 왼쪽 위 설정 · 공구 알약 + 안내 칩(처음 손짓까지), S/F 카드 아래 [회전] [자유],
 * 오른쪽 [1/4] · |◀ · ▶| · ⤢(처음 시점) — 2D 의 범례 · 각도 · 측정은 없다(공구 · 경로는 3D 에 그리지 않음 — 05).
 */
@Composable
private fun Viewport(
    sim: SimState,
    ui: TrajUi,
    run: SimRun,
    play: PlayState,
    paths: SimPaths,
    spec: StockSpec?,
    profile: State<StockProfile?>,
    stock: StockSim?,
    contacts: List<Contact>,
    modifier: Modifier,
) {
    val app = LocalAppState.current
    val cam = ui.cam
    val d3 = ui.mode3d
    BoxWithConstraints(modifier.clipToBounds()) {
        // 좁은 폰 · 큰 글꼴은 왼쪽 위 알약을 짧게('설정' · '공구') — S/F 카드에 덮이지 않게
        val narrow = maxWidth < 400.dp || LocalDensity.current.fontScale > 1.15f
        val btn = if (maxHeight < 420.dp) 40.dp else 48.dp
        // 낮은 뷰포트(창 높이 640dp 꼴): 알약은 아이콘만 한 줄, 코드 띠는 지금 줄만, 기장여유 알약은 뺀다(설정에 있음) — 겹쳐 놓인 것끼리 부딪쳐서
        val compact = maxHeight < 380.dp
        val stripH = if (compact) STRIP_COMPACT else STRIP_H
        val insets = if (compact) listOf(16.dp, 60.dp, 64.dp, stripH + 8.dp) else listOf(16.dp, 108.dp, 64.dp, stripH + 50.dp)
        if (d3) {
            // 3D 는 05 처럼 부품이 겹친 알약 · 카드 뒤까지 차게 — 위는 뷰포트의 1/5, 아래는 코드 띠만 비운다
            Sim3DView(spec, profile, cam, playing = { play.playing }, insets = if (compact) insets else listOf(16.dp, maxHeight * 0.2f, 64.dp, stripH + 16.dp))
        } else {
            Sim2DView(
                run, paths, spec, profile,
                crest = { z, outer -> if (outer) stock?.outerAt(z) else stock?.innerAt(z)?.takeIf { it > 0 } },
                play = play,
                pick = ui.pick,
                measure = ui.measure,
                angle = ui.angle,
                flags = sim.view,
                tools = sim.tools,
                contacts = contacts,
                view = ui.viewer,
                insets = insets,
                onPick = { ui.pick = it },
            )
        }
        val fit = { if (d3) cam.fit() else ui.viewer.fit() }
        if (compact) {
            Row(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                SideButton(SimIcons.Sliders, "시뮬레이션 설정", false, 44.dp) { ui.sheet = TrajSheet.SETTINGS }
                SideButton(SimIcons.Wrench, "공구 설정", false, 44.dp) { ui.sheet = TrajSheet.TOOLS }
                if (!d3) SideButton(SimIcons.Legend, "범례", false, 44.dp) { ui.sheet = TrajSheet.LEGEND }
                // 오른쪽 세로 단추가 S/F 카드에 닿지 않게 맞춤은 이 줄로
                SideButton(AppIcons.Fit, "맞춤", false, 44.dp, onClick = fit)
            }
        } else {
            Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ViewPill(SimIcons.Sliders, if (narrow) "설정" else "시뮬레이션 설정") { ui.sheet = TrajSheet.SETTINGS }
                ViewPill(SimIcons.Wrench, if (narrow) "공구" else "공구 설정") { ui.sheet = TrajSheet.TOOLS }
                if (!d3) ViewPill(SimIcons.Legend, "범례") { ui.sheet = TrajSheet.LEGEND }
            }
        }
        // 3D 안내 칩(05 는 왼쪽 위 알약 아래 — 좁은 폰 · 큰 글꼴에서 회전 · 자유 줄과 부딪쳐 왼쪽 아래 기장여유 알약 위로)
        if (d3 && !compact && !cam.touched && spec != null) {
            GuideChip(
                if (cam.quarter) "단면 절개 · 두 손가락 확대/이동" else "두 손가락 확대/이동",
                Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 64.dp, bottom = stripH + 52.dp),
            )
        }
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SfCard(run, play, Modifier)
            if (d3 && !compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkToggle(SimIcons.Spin, "회전", cam.spinning) { cam.spinning = !cam.spinning }
                    DarkToggle(SimIcons.Orbit, "자유", cam.free) { cam.free = !cam.free }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (d3) {
                SideButton(SimIcons.Quarter, "1/4 단면 절개", cam.quarter, btn, text = "1/4") { cam.quarter = !cam.quarter }
                if (compact) {
                    SideButton(SimIcons.Spin, "회전", cam.spinning, btn) { cam.spinning = !cam.spinning }
                    SideButton(SimIcons.Orbit, "자유", cam.free, btn) { cam.free = !cam.free }
                }
            } else {
                SideButton(SimIcons.Angle, "각도 확인", ui.angle, btn) { ui.angle = !ui.angle }
            }
            SideButton(SimIcons.SkipBack, "블록 처음으로", false, btn) { play.blockBack() }
            SideButton(SimIcons.SkipFwd, "블록 끝까지", false, btn) { play.blockForward() }
            if (!compact) {
                SideButton(AppIcons.Fit, if (d3) "처음 시점" else "맞춤", false, btn, onClick = fit)
                if (!d3) Box(Modifier.padding(vertical = 3.dp).width(btn - 8.dp).height(1.dp).background(AppTheme.colors.lineStrong).align(Alignment.CenterHorizontally))
            }
            if (!d3) {
                SideButton(SimIcons.Ruler, "측정", ui.measure, btn) {
                    ui.measure = !ui.measure
                    ui.pick = null
                    if (ui.measure) {
                        app.toast("경로의 시작점 · 끝점 가까이를 누르면 아래 측정점 카드에 좌표가 나옵니다 · 프로그램에 쓴 좌표입니다(📏 다시 누르면 끔)")
                    }
                }
            }
        }
        if (spec != null && !compact) {
            FacePill(spec, Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = stripH + 8.dp)) { ui.sheet = TrajSheet.SETTINGS }
        }
        // 소재를 끝까지 한 번 깎는 동안(큰 프로그램은 몇 초 — 15.7) 남은 소재가 없는 까닭
        if (spec != null && stock == null) BusyChip("소재를 깎는 중 — 큰 프로그램은 잠시 걸립니다", Modifier.align(Alignment.Center))
        // 3D 는 소재가 있어야 그릴 것이 있다
        if (d3 && spec == null) NoteChip("3D 로 그릴 소재가 없습니다 — 이송 이동이 있어야 하고, 소재 칸이 맞아야 합니다", Modifier.align(Alignment.Center))
        CodeStrip(sim, run, play, ui, Modifier.align(Alignment.BottomStart).padding(end = 64.dp).fillMaxWidth(), maxHeight / 2, compact)
    }
}

/** 3D 안내 칩(05 — 어두운 바탕 · 초록 테): 처음 손짓까지 */
@Composable
private fun GuideChip(text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        modifier.clip(shape).background(Sim3DColors.chipBg).border(1.dp, Sim3DColors.chipBorder, shape).padding(horizontal = 12.dp, vertical = 7.dp),
        color = Sim3DColors.chipText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** 3D 어두운 뷰포트 위 켬/끔 단추 '회전' · '자유'(05 — 어두운 바탕 · 밝은 테). 켜진 것 = 청록 */
@Composable
private fun DarkToggle(icon: ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val fg = if (on) c.onFill(c.teal) else Sim3DColors.darkBtnText
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(shape)
            .background(if (on) c.teal else Sim3DColors.darkBtnBg)
            .border(1.dp, if (on) c.teal else Sim3DColors.darkBtnBorder, shape)
            .clickable(onClickLabel = text, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** ② 뷰포트 왼쪽 위 알약(반투명 흰 — 05 · 06): 보이는 40dp · 누르는 48dp */
@Composable
private fun ViewPill(icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(shape)
            .background(c.card.copy(alpha = .92f))
            .border(1.dp, c.lineCard, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.icon)
        Spacer(Modifier.width(7.dp))
        Text(text, color = c.textStrong, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * ③ S/F 카드: 왼쪽 'G97 · S 900rpm'(G96 이면 'G96 · 200 m/min') + 큰 'S 900'(지금 회전수) │ 오른쪽 'mm/rev'(G98 'mm/min' · 급송 '급송' · 나사 '리드') + 큰 'F 0.08'.
 * 재생 시각은 여기서만 읽는다(이 카드만 다시 그림).
 */
@Composable
private fun SfCard(run: SimRun, play: PlayState, modifier: Modifier) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val pose = run.poseAt(play.t)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(c.card.copy(alpha = .95f)).border(1.dp, c.lineCard, shape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(sText(pose), color = c.textMuted, fontSize = 11.5.sp, maxLines = 1)
            Text("S ${pose.rpm.roundToInt()}", color = p.valueSf, fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        }
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(36.dp).background(c.lineCard))
        Column(horizontalAlignment = Alignment.Start) {
            Text(fUnit(pose), color = c.textMuted, fontSize = 11.5.sp, maxLines = 1)
            Text("F ${pose.feed?.let(::feedText) ?: "—"}", color = p.valueSf, fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        }
    }
}

internal fun sText(pose: SimPose): String {
    val s = pose.sCmd
    return when {
        pose.css -> "G96 · ${s?.let(::numText) ?: "—"} m/min"
        else -> "G97 · S ${s?.let(::numText) ?: "—"}rpm"
    }
}

internal fun fUnit(pose: SimPose): String = when {
    pose.kind == MoveKind.RAPID -> "급송"
    pose.kind == MoveKind.THREAD -> "리드 mm"
    pose.perRev -> "mm/rev"
    else -> "mm/min"
}

/** ④ 오른쪽 세로 단추(보이는 40dp · 누르는 48dp). 켜진 것 = 청록 채움(원본 주황 · 하늘 대신 — 7.10). [text] = 아이콘 아래 작은 글(05 의 1/4) */
@Composable
private fun SideButton(icon: ImageVector, label: String, on: Boolean, size: Dp, text: String? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val fg = if (on) c.onFill(c.teal) else c.textStrong
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size - 8.dp)
                .clip(shape)
                .background(if (on) c.teal else c.card.copy(alpha = .95f))
                .border(1.dp, if (on) c.teal else c.lineCard, shape)
                .clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (text == null) {
                Icon(icon, label, Modifier.size(20.dp), tint = fg)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, label, Modifier.size(16.dp), tint = fg)
                    Text(text, color = fg, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** 오래 걸리는 일 알림 칩(0.4 초 넘게 걸릴 때만 — 보통 프로그램에서 깜빡이지 않게) */
@Composable
private fun BusyChip(text: String, modifier: Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        show = true
    }
    if (!show) return
    val c = AppTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        keepWords(text),
        modifier.padding(horizontal = 24.dp).clip(shape).background(c.card.copy(alpha = .95f)).border(1.dp, c.lineStrong, shape).padding(horizontal = 14.dp, vertical = 6.dp),
        color = c.textBody, fontSize = 13.sp, textAlign = TextAlign.Center,
    )
}

/** 알림 칩(바로 — 늦추지 않음) */
@Composable
private fun NoteChip(text: String, modifier: Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Text(
        keepWords(text),
        modifier.padding(horizontal = 24.dp).clip(shape).background(c.card.copy(alpha = .95f)).border(1.dp, c.lineStrong, shape).padding(horizontal = 14.dp, vertical = 8.dp),
        color = c.textBody, fontSize = 13.sp, textAlign = TextAlign.Center,
    )
}

/** ⑤ 기장여유 알약(중립 — 원본 노랑 대신, 7.10). 누르면 설정의 소재 칸 */
@Composable
private fun FacePill(spec: StockSpec, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .clip(shape)
            .background(c.card.copy(alpha = .92f))
            .border(1.dp, c.lineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SimIcons.Ruler, null, Modifier.size(16.dp), tint = c.icon)
        Spacer(Modifier.width(6.dp))
        val f = spec.faceZ
        Text("기장여유 ${if (f >= 0) "+" else ""}${numText(f)} mm", color = c.textStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * ⑥ 코드 띠: 손잡이 + 앞 · **지금** · 다음 줄(지금 줄은 옅은 바탕 + 왼쪽 3dp 막대 — 오류로 멈춘 줄이면 빨간 막대 + ⛔).
 * 손잡이를 누르거나 끌어 올리면 전체 목록(뷰포트 절반까지) — 줄을 누르면 그 블록 처음으로.
 */
@Composable
private fun CodeStrip(sim: SimState, run: SimRun, play: PlayState, ui: TrajUi, modifier: Modifier, maxList: Dp, compact: Boolean) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val lines = remember(sim.editor.text) { sim.editor.text.split('\n') }
    val pose = run.poseAt(play.t)
    val stopLine = run.stop?.line?.takeIf { play.atEnd }
    val cur = (stopLine ?: pose.line).coerceIn(1, max(1, lines.size))
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    Column(modifier.clip(shape).background(c.card.copy(alpha = .97f)).border(1.dp, c.lineCard, shape)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clickable(onClickLabel = "코드 목록") { ui.listOpen = !ui.listOpen }
                .pointerInput(Unit) {
                    detectDragGestures { _, d ->
                        if (d.y < -8) ui.listOpen = true
                        if (d.y > 8) ui.listOpen = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
        }
        fun go(line: Int) {
            if (!play.goLine(line)) app.toast("${lineLabel(line)}은 실행되지 않은 줄입니다")
        }
        if (ui.listOpen) {
            val state = rememberLazyListState((cur - 3).coerceAtLeast(0))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxList), state = state) {
                itemsIndexed(lines) { i, l ->
                    CodeLine(i + 1, l, i + 1 == cur, (i + 1) == stopLine, p, numbered = true) { go(i + 1) }
                }
            }
        } else {
            for (n in if (compact) listOf(cur) else listOf(cur - 1, cur, cur + 1)) {
                val l = lines.getOrNull(n - 1)
                if (l == null) Spacer(Modifier.height(if (n == cur) 30.dp else 26.dp))
                else CodeLine(n, l, n == cur, n == stopLine, p, numbered = false) { go(n) }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CodeLine(n: Int, text: String, current: Boolean, stopped: Boolean, p: SimPalette, numbered: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val bar = if (stopped) c.error.label else p.hlBar
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (current) 30.dp else 26.dp)
            .background(if (current) p.hlLine else Color.Transparent)
            .drawWithContent {
                drawContent()
                if (current) drawRect(bar, size = Size(3.dp.toPx(), size.height))
            }
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stopped) {
            Icon(AppIcons.Stop, null, Modifier.size(14.dp), tint = c.error.label)
            Spacer(Modifier.width(4.dp))
        }
        if (numbered) {
            Text(n.toString(), Modifier.width(30.dp), color = c.textMuted, fontFamily = AppTheme.mono, fontSize = 12.sp, maxLines = 1)
        }
        Text(
            syntaxLine(text, p),
            color = c.textStrong,
            fontFamily = AppTheme.mono,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (current) 17.sp else 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * ⑦ 좌표 카드(설계 7.5): 위치 (절대) — 지금 실제 인선 / 보정 좌표 — 고른 고스트의 실제 인선(노즈R 보정이 걸린 자리 = G40 으로 쓸 좌표) /
 * 측정점 — 고른 블록 끝의 명령점(프로그램에 쓴 좌표). 고르면 안내와 칩. 낮은 창은 안내를 한 줄로 접는다.
 */
@Composable
private fun CoordCard(run: SimRun, play: PlayState, ui: TrajUi, short: Boolean) {
    val c = AppTheme.colors
    // 3D 에는 고스트가 없다 — 고른 것은 2D 로 돌아가면 다시 보인다
    val sel = ui.pick.takeIf { !ui.mode3d }
    val g = sel?.let { run.ghosts.getOrNull(it.ghost) }
    val meas = sel?.measure == true
    AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        val (head, at, color) = when {
            g != null && meas -> Triple("측정점", g.cmd, c.valueComp)
            g != null -> Triple("보정 좌표", g.tip, c.valueComp)
            else -> Triple("위치 (절대)", run.poseAt(play.t).tip, c.valuePos)
        }
        Text(head, color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        CoordPair(Fmt.fixed3(at.xDia), Fmt.fixed3(at.z), color, Modifier.padding(top = 2.dp), size = 24.sp)
        if (g == null) return@AppCard
        val lines = if (meas) {
            listOf("※ 프로그램에 쓴 좌표 — G41/G42 프로그램이면 도면 치수와 같고, 직접 보정(G40) 좌표면 다를 수 있습니다")
        } else {
            listOf(
                "고스트 원 = 블록 끝의 노즈 · ㄱ 은 가상 인선",
                "위 좌표는 노즈R 보정이 걸린 실제 인선 자리 (G40 으로 쓸 좌표)",
                "겹친 원은 다시 누르면 다음 원",
            )
        }
        var open by remember { mutableStateOf(false) }
        if (short && !open) {
            Row(Modifier.padding(top = 4.dp).clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (meas) "측정점 안내" else "고스트 원 안내", color = c.tealDeep, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Icon(AppIcons.ChevronDown, null, Modifier.size(14.dp), tint = c.tealDeep)
            }
        } else {
            Column(Modifier.padding(top = 4.dp)) {
                lines.forEach { Text(keepWords(it), color = c.textBody, fontSize = 12.5.sp, lineHeight = 18.sp) }
            }
        }
        Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.clip(shape).background(c.teal.copy(alpha = .12f)).border(1.dp, c.teal, shape).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SimIcons.Ruler, null, Modifier.size(14.dp), tint = c.tealDeep)
                Spacer(Modifier.width(5.dp))
                Text(
                    "${if (meas) "측정점" else "보정 좌표"} 선택됨 · ${lineLabel(g.line)}",
                    color = c.tealDeep, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** ⑧ 가공 시간(지금 · 전체 · 모형 포함) │ 속도 미끄럼 ×1 ~ ×200(로그) + 값 칩 */
@Composable
private fun TimeSpeedRow(sim: SimState, play: PlayState, model: Boolean, short: Boolean) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    // 높이는 글에 맞춘다(큰 글꼴에서 시간 숫자가 잘리지 않게) — 낮은 창은 두 줄
    Row(Modifier.fillMaxWidth().heightIn(min = if (short) 52.dp else 60.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppCard(Modifier.weight(0.37f).fillMaxHeight(), padding = 8.dp) {
            val total = "전체 ${timeText(play.run.totalSec)}"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("가공 시간", Modifier.weight(1f), color = c.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (short) Text(total, color = c.textMuted, fontFamily = AppTheme.mono, fontSize = 10.5.sp, maxLines = 1)
            }
            Text(timeText(play.t), color = p.valueTime, fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold, fontSize = if (short) 19.sp else 22.sp, maxLines = 1)
            if (!short) {
                // 큰 글꼴 · 좁은 폰은 두 줄로('모형 포함' 이 잘리지 않게)
                Text(keepWords(total + if (model) " · 모형 포함" else ""), color = c.textMuted, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        AppCard(Modifier.weight(0.63f).fillMaxHeight(), padding = 8.dp) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(SimIcons.Gauge, null, Modifier.size(18.dp), tint = c.icon)
                Spacer(Modifier.width(4.dp))
                Text("속도", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                SpeedSlider(sim.speed, { sim.speed = it }, Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp))
                val shape = RoundedCornerShape(8.dp)
                Text(
                    speedText(sim.speed),
                    Modifier.clip(shape).border(1.dp, c.lineField, shape).padding(horizontal = 8.dp, vertical = 4.dp),
                    color = c.textValue, fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                )
            }
        }
    }
}

/** 로그 미끄럼(×1 ~ ×200): 눈금 1 · 2 · 5 · 10 · 20 · 50 · 100 · 200, 누르거나 끌어 바꾼다 */
@Composable
private fun SpeedSlider(speed: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o -> onChange(PlayState.speedAt(posAt(o.x, size.width.toFloat()))) }
            }
            .pointerInput(Unit) {
                detectDragGestures { ch, _ -> onChange(PlayState.speedAt(posAt(ch.position.x, size.width.toFloat()))) }
            },
    ) {
        val pad = 10.dp.toPx()
        val y = size.height / 2
        val w = size.width - 2 * pad
        val pos = PlayState.posOf(speed)
        drawRoundRect(c.lineField, Offset(pad, y - 2.dp.toPx()), Size(w, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawRoundRect(c.textStrong, Offset(pad, y - 2.dp.toPx()), Size(w * pos, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        for (t in PlayState.TICKS) {
            val x = pad + w * PlayState.posOf(t)
            drawLine(c.lineStrong, Offset(x, y + 6.dp.toPx()), Offset(x, y + 10.dp.toPx()), 1.5.dp.toPx())
        }
        val tx = pad + w * pos
        drawCircle(p.run, 10.dp.toPx(), Offset(tx, y))
        drawCircle(c.card, 10.dp.toPx(), Offset(tx, y), style = Stroke(2.dp.toPx()))
    }
}

private fun androidx.compose.ui.unit.Density.posAt(x: Float, width: Float): Float {
    val pad = 10.dp.toPx()
    return ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
}

/** ⑨ 단추 줄(폭 1 : 1 : 1.5 : 1): 싱글 블록 · M01(켜지면 청록) · 재생/멈춤/처음부터(초록) · 리셋(중립 테두리 — 7.10) */
@Composable
private fun ButtonRow(sim: SimState, play: PlayState, short: Boolean) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val h = if (short) 48.dp else 52.dp
    Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleButton(SimIcons.Lines, "싱글 블록", sim.singleBlock, Modifier.weight(1f)) { sim.singleBlock = !sim.singleBlock }
        ToggleButton(SimIcons.Hand, "M01", sim.optStop, Modifier.weight(1f)) { sim.optStop = !sim.optStop }
        val (icon, text) = when {
            play.playing -> SimIcons.Pause to "멈춤"
            play.atEnd -> SimIcons.Replay to "처음부터"
            else -> SimIcons.Play to "재생"
        }
        BarButton(icon, text, p.run, p.onRun, null, Modifier.weight(1.5f)) { play.toggle() }
        BarButton(SimIcons.Reset, "리셋", c.card, c.textStrong, c.lineStrong, Modifier.weight(1f), stacked = true) { play.reset() }
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    BarButton(icon, text, if (on) c.teal else c.field, if (on) c.onFill(c.teal) else c.textBody, if (on) c.teal else c.lineField, modifier, stacked = true, onClick = onClick)
}

/**
 * 단추 줄의 단추. stacked = 아이콘 위 · 글 아래(폭 1 칸 단추 — '싱글 블록' 이 360dp · 큰 글꼴에서도 들게), 아니면 아이콘 옆 글(재생).
 */
@Composable
private fun BarButton(
    icon: ImageVector,
    text: String,
    bg: Color,
    fg: Color,
    border: Color?,
    modifier: Modifier,
    stacked: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val box = modifier
        .fillMaxHeight()
        .clip(shape)
        .background(bg)
        .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
        .clickable(onClick = onClick)
        .padding(horizontal = 4.dp)
    val label = @Composable { size: Float ->
        BasicText(
            text,
            style = TextStyle(color = fg, fontFamily = AppTheme.sans, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
            // Ellipsis 면 줄인 글자 크기를 고르지 않고 '…' 로 끝난다(넘침으로 보지 않음) — Clip 으로 넘침을 알려 줄이게
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(9.sp, size.sp, 0.5.sp),
        )
    }
    if (stacked) {
        Column(box, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = fg)
            label(13f)
        }
    } else {
        Row(box, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
            label(16f)
        }
    }
}

/** 범례 시트(설계 7.6 끝 — 뷰포트 자리를 아끼려고 알약으로 연다) */
@Composable
private fun LegendSheet(paths: SimPaths, flags: ViewFlags, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    BottomSheet(onDismiss) {
        SheetTitle("범례")
        val items = buildList {
            if (paths.hasCmd) add(Triple(c.simProgram, "명령 경로 (프로그램에 쓴 길 — 보정 전)", Glyph.LINE))
            add(Triple(c.viewPath, if (paths.hasCmd) "보정된 인선 경로 (실제 가상 인선)" else "인선 경로", Glyph.LINE))
            add(Triple(c.simArc, "원호 구간", Glyph.LINE))
            add(Triple(c.viewRapid, "급송 · 후퇴 (G00)", Glyph.DASH))
            add(Triple(p.layer, "황삭 층 · 사이클 이동" + if (paths.model) " (층 위치는 모형)" else "", Glyph.THIN))
            if (flags.nose) add(Triple(c.viewNose, "노즈 중심 경로", Glyph.DASH))
            add(Triple(p.part2d, "남은 소재", Glyph.FILL))
            add(Triple(c.drawRef, "처음 소재 · 기장여유 띠", Glyph.DASH))
            add(Triple(p.ghost, "고스트 원 (블록 끝의 노즈 · ㄱ = 가상 인선)", Glyph.CIRCLE))
            add(Triple(c.drawInsert, "지금 공구 (인서트)", Glyph.FILL))
            add(Triple(c.warn.label, "▲ 급송이 소재를 깎음", Glyph.DOT))
            add(Triple(c.error.label, "✕ 공구가 척에 닿음", Glyph.DOT))
        }
        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((color, label, g) in items) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(28.dp, 14.dp)) {
                        val y = size.height / 2
                        when (g) {
                            Glyph.LINE -> drawLine(color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                            Glyph.THIN -> drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            Glyph.DASH -> drawLine(color, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
                            Glyph.FILL -> drawRoundRect(color.copy(alpha = .6f), cornerRadius = CornerRadius(3.dp.toPx()))
                            Glyph.CIRCLE -> {
                                drawCircle(color.copy(alpha = .18f), 6.dp.toPx())
                                drawCircle(color, 6.dp.toPx(), style = Stroke(1.2.dp.toPx()))
                            }
                            Glyph.DOT -> drawCircle(color, 5.dp.toPx())
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(keepWords(label), color = c.textBody, fontSize = 14.sp)
                }
            }
        }
    }
}

private enum class Glyph { LINE, THIN, DASH, FILL, CIRCLE, DOT }
