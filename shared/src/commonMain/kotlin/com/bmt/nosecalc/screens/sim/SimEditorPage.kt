package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.ParsedProgram
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.CircleIconButton
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 시뮬레이터 화면 A — 편집기(설계 7.1 · 09): 프로그램 줄 → (찾기 줄) → 편집기 → 문제 띠 → CNC 키패드(닫으면 아래 단추 줄).
 * 줄을 끝내고 0.3초 뒤 백그라운드에서 다시 돌려(GSim) 문제 표를 바꾸고(쓰는 중인 줄은 검사하지 않는다 — 설계 20), 0.5초 쉬면 임시 글을 남긴다.
 * onTrajectory = 궤적 창 열기(▶ 궤적 · 궤적 확인 단추). onTools = 궤적 창을 공구 설정 시트로 열기(문제 띠의 '공구 설정').
 */
@Composable
internal fun SimulatorPage(sim: SimState, onTools: () -> Unit, onTrajectory: (SimRun) -> Unit) {
    val app = LocalAppState.current
    @Suppress("DEPRECATION")
    val clip = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    val ed = sim.editor
    fun refocus() {
        runCatching { focus.requestFocus() }
    }

    // 설정(기계 · 소재 원점 · 읽기)이 바뀌어도 다시 — 궤적 창의 설정 창이 바꾼다
    val setup = sim.runSetup()
    // 검사 글로 돈다 — 쓰는 동안은 검사 글(번호)이 그대로라 다시 돌지 않는다(설계 20)
    LaunchedEffect(sim, ed.checkGen, sim.tools, setup) {
        delay(300)
        val gen = ed.checkGen
        val text = ed.checkedText
        val tools = sim.tools
        sim.run = withContext(Dispatchers.Default) { sim.runOf(text, tools, setup) }
        sim.runKey = Triple(text, tools, setup)
        ed.ran(gen)
    }
    LaunchedEffect(sim, ed.text, ed.cursor) {
        delay(500)
        sim.saveDraft()
    }
    DisposableEffect(sim) {
        onDispose {
            ed.finishLine()
            sim.saveDraft()
        }
    }

    val run = sim.run
    // 빈 글의 'M30 없이 끝남' 같은 안내와 쓰는 줄의 문제는 띄우지 않는다
    val issues = shownIssues(ed, run)
    val stop = run.stop?.takeIf { !hidden(ed, run, it) }
    FeatureScaffold(Feature.SIMULATOR, SimHelp, helpStart = if (sim.trajOpen) SIM_HELP_TRAJ else 0) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val keyH = if (maxHeight < 700.dp) 46.dp else 58.dp
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.gutter).padding(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProgramBar(
                        sim, run,
                        onSave = { ed.finishLine(); sim.requestSave()?.let { app.toast(it) } },
                        onList = { ed.finishLine(); sim.sheet = SimSheet.PROGRAMS },
                        onMore = { ed.finishLine(); sim.sheet = SimSheet.MORE },
                    )
                    if (sim.findOpen) {
                        FindBar(sim, onClose = { sim.findOpen = false; refocus() })
                    }
                    GEditor(
                        ed, issues.filter { it.line > 0 }.groupBy { it.line }, stop?.line, focus,
                        // 궤적 창이 덮고 있으면 PC 키를 편집기에 넣지 않는다(창이 받지 않은 글자 키가 숨은 글에 들어가지 않게)
                        onKey = { e -> !sim.trajOpen && pcKey(e, sim, clip) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        placeholder = "키패드로 G코드를 넣으세요. 저장한 프로그램과 예제는 '목록' 에서 엽니다.",
                    )
                    IssueStrip(issues, onTools = { ed.finishLine(); onTools() }) { ed.finishLine(); sim.sheet = SimSheet.ISSUES }
                }
                if (sim.keypadOpen) {
                    CncKeypad(
                        shift = sim.shift,
                        canUndo = ed.canUndo,
                        canRedo = ed.canRedo,
                        findOn = sim.findOpen,
                        onKey = { sim.press(it); refocus() },
                        onUndo = { ed.undo(); refocus() },
                        onRedo = { ed.redo(); refocus() },
                        onShift = { long -> sim.shift = nextShift(sim.shift, long); refocus() },
                        onFind = {
                            if (sim.findOpen) sim.findOpen = false else {
                                ed.finishLine()
                                sim.openFind()
                            }
                            refocus()
                        },
                        onRun = { ed.finishLine(); onTrajectory(sim.run) },
                        onClose = { ed.finishLine(); sim.keypadOpen = false },
                        keyHeight = keyH,
                    )
                } else {
                    BottomBar(onKeypad = { sim.keypadOpen = true; refocus() }, onRun = { ed.finishLine(); onTrajectory(sim.run) })
                }
            }
        }
    }
    OnBack(enabled = sim.keypadOpen, layer = true) { ed.finishLine(); sim.keypadOpen = false }
    OnBack(enabled = sim.findOpen, layer = true) { sim.findOpen = false }

    when (sim.sheet) {
        SimSheet.PROGRAMS -> ProgramSheet(sim) { sim.sheet = null }
        SimSheet.MORE -> MoreSheet(
            onNew = { sim.sheet = null; sim.request(Pending.New) },
            onPaste = {
                sim.sheet = null
                clip.getText()?.text?.takeIf { it.isNotEmpty() }?.let { ed.paste(it) } ?: app.toast("클립보드에 글이 없습니다")
            },
            onCopyAll = {
                sim.sheet = null
                clip.setText(AnnotatedString(ed.text))
                app.toast("전체 복사 — ${ed.lineCount}줄")
            },
            onExamples = { sim.sheet = SimSheet.PROGRAMS },
            onDismiss = { sim.sheet = null },
        )
        SimSheet.ISSUES -> IssueSheet(issues, stop, onPick = { sim.goTo(it); refocus() }) { sim.sheet = null }
        null -> {}
    }
    SimAskModal(sim)
}

/** 편집기에 보일 문제(설계 20): 빈 검사 글이면 없음, 가린 것([hidden])은 뺀다. 화면과 시험이 같이 쓴다 */
internal fun shownIssues(ed: EditorState, run: SimRun): List<SimIssue> =
    if (ed.checkedText.isBlank()) emptyList() else run.issues.filterNot { hidden(ed, run, it) }

/** 지금 가린 문제: 쓰는 줄 · 막 끝낸 줄의 것, 그 줄이 글의 마지막 줄이면 글이 덜 끝나서 생긴 문제([endOfText])도(설계 21.6) */
internal fun hidden(ed: EditorState, run: SimRun, i: SimIssue): Boolean = ed.hides(i.line - 1) || ed.atTextEnd && endOfText(run.program, i)

/** 실행이 글 끝에 닿아서 붙는 문제(설계 21.6): 끝(M30 · M02 · M99) 없음 · 부프로그램 M99 없음 · 끄지 않은 보정 · 다음 이동 없는 모서리 */
internal val TextEndCodes = setOf(IssueCode.NO_END, IssueCode.SUB_NO_M99, IssueCode.COMP_NOT_CANCELLED, IssueCode.CORNER_NO_NEXT)

/** 프로그램을 끝내는 M — 이 블록에 붙은 [TextEndCodes] 는 글 끝이 아니라 이 끝에서 생겼다 */
private val EndM = setOf(2.0, 30.0, 99.0)

/**
 * 검사 글이 덜 끝나서 생긴 문제인가(설계 21.6). 같은 코드라도 이미 끝낸 다른 줄 때문에 생긴 것은 아니다:
 * [TextEndCodes] 는 줄 없는 것이거나 마지막 O 번호의 낱말 있는 마지막 블록(M02 · M30 · M99 블록이 아님)에 붙은 것,
 * 아직 안 쓴 P · Q 의 N 블록(CYCLE_PQ_NOT_FOUND — 같은 O 번호 안에서만 찾는다)은 그 G7x 가 마지막 O 번호에 있을 때,
 * 아직 안 쓴 M98 의 부프로그램(SUB_SKIPPED — 부프로그램은 글 끝에 쓴다)은 언제나.
 */
internal fun endOfText(prog: ParsedProgram, i: SimIssue): Boolean {
    val unit = prog.units.lastOrNull() ?: return false
    return when (i.code) {
        in TextEndCodes -> {
            val end = unit.blocks.lastOrNull { it.words.isNotEmpty() }
            i.line == 0 || i.line >= (end?.line ?: unit.firstLine) && end?.words.orEmpty().none { it.key == "M" && it.value in EndM }
        }
        IssueCode.CYCLE_PQ_NOT_FOUND -> i.line >= unit.firstLine
        IssueCode.SUB_SKIPPED -> true
        else -> false
    }
}

/**
 * 프로그램 줄(48dp — 우리가 정함): O 번호 칩 · 첫 주석과 상태(저장됨 · 예제 · 고침) · [저장] · [목록] · [⋯].
 * 상태는 색만으로 가르지 않고 글로 적는다(고침은 노랑이 아님 — 경고가 아니라서).
 */
@Composable
private fun ProgramBar(sim: SimState, run: SimRun, onSave: () -> Unit, onList: () -> Unit, onMore: () -> Unit) {
    val c = AppTheme.colors
    val number = run.program.units.firstOrNull()?.number
    val comment = ProgramStore.firstComment(sim.editor.text.take(2000))
    val status = when {
        sim.modified && sim.editor.text.isNotEmpty() -> "● 고침"
        sim.origin is Origin.Saved -> "✓ 저장됨"
        sim.origin is Origin.Example -> "예제"
        else -> "새 프로그램"
    }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(8.dp)
        Text(
            if (number != null) ProgramStore.oText(number) else "번호 없음",
            Modifier.clip(shape).background(c.card).border(1.dp, c.lineCard, shape).padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (number != null) c.textStrong else c.textMuted,
            fontFamily = if (number != null) AppTheme.mono else AppTheme.sans,
            fontWeight = FontWeight.Bold,
            fontSize = if (number != null) 15.sp else 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (comment != null) {
                Text(comment, color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                status,
                color = if (sim.origin is Origin.Saved && !sim.modified) c.tealDeep else c.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PillButton("저장", null, onSave)
        PillButton("목록", null, onList)
        CircleIconButton(SimIcons.More, "더 보기", onMore, size = 40.dp)
    }
}

/** 찾기 줄(설계 7.2 🔍): 찾는 글(키패드로 친다) · 없음 · 앞 · 다음 · 닫기 */
@Composable
private fun FindBar(sim: SimState, onClose: () -> Unit) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).background(c.card).border(1.5.dp, c.teal, shape).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SimIcons.Search, null, Modifier.size(18.dp), tint = c.teal)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (sim.query.isEmpty()) {
                Box(Modifier.width(2.dp).height(20.dp).background(p.caret))
                Text("  찾을 글 — 키패드로", color = c.textHint, fontSize = 14.sp, maxLines = 1)
            } else {
                Text(sim.query, color = c.textValue, fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(start = 1.dp).width(2.dp).height(20.dp).background(p.caret))
            }
        }
        if (sim.findMiss) Text("없음", Modifier.padding(horizontal = 6.dp), color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SmallIcon(SimIcons.ChevronUp, "앞에서 찾기") { sim.findNext(false) }
        SmallIcon(AppIcons.ChevronDown, "다음 찾기") { sim.findNext(true) }
        SmallIcon(AppIcons.Close, "찾기 닫기", onClose)
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.minimumInteractiveComponentSize().size(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = AppTheme.colors.icon)
    }
}

/**
 * 문제 띠(설계 7.1 ③): 가장 무거운 문제의 색 · 아이콘 + '오류 2 · 확인 1 — 12줄 …(첫 문제)' + '목록'. 누르면 문제 목록 시트.
 * 문제가 없으면 그리지 않는다. 공구 값이 없어 멈추면(TOOL_DATA_MISSING) '공구 설정' 단추 — 공구표는 궤적 창에만 있어 편집기에서 길을 못 찾았다(10-28).
 */
@Composable
private fun IssueStrip(issues: List<SimIssue>, onTools: () -> Unit, onOpen: () -> Unit) {
    if (issues.isEmpty()) return
    val c = AppTheme.colors
    val worst = issues.minOf { it.level.ordinal }.let { IssueLevel.entries[it] }
    val first = issues.first { it.level == worst }
    val rc = c.roleOf(worst)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(rc.bg)
            .drawWithContent {
                drawContent()
                drawRect(rc.label, size = Size(4.dp.toPx(), size.height))
            }
            .border(1.dp, rc.border, shape)
            .clickable(onClickLabel = "문제 목록", onClick = onOpen)
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconOf(worst), null, Modifier.size(18.dp), tint = rc.label)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(issueCounts(issues), color = rc.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                keepWords("${lineLabel(first.line)}: ${simIssueText(first)}"),
                color = rc.value,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (issues.any { it.code == IssueCode.TOOL_DATA_MISSING }) {
            Spacer(Modifier.width(6.dp))
            PillButton("공구 설정", SimIcons.Wrench, onTools)
        }
        Text("목록", Modifier.padding(start = 6.dp), color = rc.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(16.dp).rotate(-90f), tint = rc.label)
    }
}

/** 키패드를 닫았을 때 아래 단추 줄: [⌨ 키패드] · [▶ 궤적 확인](초록 채움, 더 넓게) */
@Composable
private fun BottomBar(onKeypad: () -> Unit, onRun: () -> Unit) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Space.gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f).height(52.dp).clip(shape).background(c.card).border(1.5.dp, c.lineField, shape).clickable(onClick = onKeypad),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SimIcons.Keyboard, null, Modifier.size(22.dp), tint = c.icon)
            Spacer(Modifier.width(8.dp))
            Text("키패드", color = c.textBody, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier.weight(1.5f).height(52.dp).clip(shape).background(p.run).clickable(onClick = onRun),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SimIcons.Play, null, Modifier.size(18.dp), tint = p.onRun)
            Spacer(Modifier.width(8.dp))
            Text("궤적 확인", color = p.onRun, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * PC 키보드(설계 7.1): 글자 · 숫자 · 기호는 그대로(대문자로), 화살표 · Home · End(+Shift 고르기), ⌫ · Delete · Enter,
 * Ctrl+Z / Y(Ctrl+Shift+Z) · Ctrl+A / C / X / V · Ctrl+F. 찾기 줄이 열려 있으면 글은 찾는 글로, Enter = 다음(Shift = 앞).
 * Esc 는 앱이 먼저 받아 찾기 · 키패드를 닫는다(AppState.handleKey).
 */
@Suppress("DEPRECATION")
internal fun pcKey(e: KeyEvent, sim: SimState, clip: ClipboardManager): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ed = sim.editor
    val sh = e.isShiftPressed
    if (e.isCtrlPressed || e.isMetaPressed) {
        when (e.key) {
            Key.Z -> if (sh) ed.redo() else ed.undo()
            Key.Y -> ed.redo()
            Key.A -> ed.selectAll()
            Key.C -> ed.selectedText()?.let { clip.setText(AnnotatedString(it)) }
            Key.X -> ed.selectedText()?.let {
                clip.setText(AnnotatedString(it))
                ed.backspace()
            }
            Key.V -> clip.getText()?.text?.let { if (sim.findOpen) sim.editQuery(sim.query + EditorState.clean(it).replace("\n", "")) else ed.paste(it) }
            Key.F -> {
                ed.finishLine()
                sim.openFind()
            }
            else -> return false
        }
        return true
    }
    if (sim.findOpen) {
        when (e.key) {
            Key.Enter, Key.NumPadEnter -> sim.findNext(!sh)
            Key.Backspace -> sim.key(CncKey.Back)
            else -> {
                val ch = e.utf16CodePoint
                if (ch in 32..126) sim.key(CncKey.Text(ch.toChar().toString())) else return false
            }
        }
        return true
    }
    when (e.key) {
        Key.DirectionLeft -> ed.left(sh)
        Key.DirectionRight -> ed.right(sh)
        Key.DirectionUp -> ed.vertical(-1, sh)
        Key.DirectionDown -> ed.vertical(1, sh)
        Key.MoveHome -> ed.home(sh)
        Key.MoveEnd -> ed.end(sh)
        Key.Backspace -> ed.backspace()
        Key.Delete -> ed.deleteForward()
        Key.Enter, Key.NumPadEnter -> ed.newline()
        else -> {
            val ch = e.utf16CodePoint
            if (ch in 32..126) ed.insert(ch.toChar().toString()) else return false
        }
    }
    return true
}
