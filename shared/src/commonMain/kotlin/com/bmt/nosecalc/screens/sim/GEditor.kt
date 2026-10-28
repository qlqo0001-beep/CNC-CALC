package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.sim.GText
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.RoleColors
import kotlinx.coroutines.flow.first
import kotlin.math.max

/** 문제 무게 → 역할색(빨강 멈춤 · 노랑 확인 · 파랑 안내) */
internal fun AppColors.roleOf(level: IssueLevel): RoleColors = when (level) {
    IssueLevel.STOP -> error
    IssueLevel.WARN -> warn
    IssueLevel.INFO -> info
}

/** 문제 무게 → 아이콘(색만으로 가르지 않게 모양도 다름) */
internal fun iconOf(level: IssueLevel) = when (level) {
    IssueLevel.STOP -> AppIcons.Stop
    IssueLevel.WARN -> AppIcons.Alert
    IssueLevel.INFO -> AppIcons.Info
}

/** 한 줄의 구문 색(같은 파서 [GText.tokens] — 색과 실행이 어긋나지 않게). ,C · ,R 의 쉼표는 O 색, 글자는 원호 색 */
internal fun syntaxLine(line: String, p: SimPalette): AnnotatedString = buildAnnotatedString {
    append(line)
    for ((cols, kind) in GText.tokens(line)) {
        val a = cols.first
        val b = minOf(cols.last + 1, line.length)
        if (a >= b) continue
        val color = SimColors.token(p, kind) ?: continue
        if (kind == ",C" || kind == ",R") {
            addStyle(SpanStyle(color = p.o), a, a + 1)
            if (a + 1 < b) addStyle(SpanStyle(color = color), a + 1, b)
        } else addStyle(SpanStyle(color = color), a, b)
    }
}

/**
 * G코드 편집기(설계 7.1 · 09 ②): 줄 번호 칸(문제 표 ⛔ · ▲ · ●) + 구문 색 글(JetBrains Mono 18sp · 줄 높이 27, 좁은 폰 · 큰 글꼴 16sp) +
 * 지금 줄 옅은 바탕과 왼쪽 막대, 문제 낱말 물결 밑줄, 커서 줄과 멈춘 줄 아래 문제 글. 긴 줄은 접어 보인다(줄 번호는 첫 줄에만).
 * 손짓: 누름 = 커서, 길게 누름 = 낱말 고름 → 끌면 범위, 한 손가락 끌기 = 스크롤. PC 마우스는 끌면 고름.
 * 시스템 키보드는 띄우지 않는다(CNC 키패드) — PC 키는 [onKey](초점은 [focus]).
 */
@Composable
internal fun GEditor(
    ed: EditorState,
    issues: Map<Int, List<SimIssue>>,
    stopLine: Int?,
    focus: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.lineFocus, shape)
            .focusRequester(focus)
            .onPreviewKeyEvent(onKey)
            .focusable(),
    ) {
        val small = maxWidth < 380.dp || density.fontScale >= 1.2f
        val style = TextStyle(
            fontFamily = AppTheme.mono,
            fontSize = if (small) 16.sp else 18.sp,
            lineHeight = if (small) 24.sp else 27.sp,
            color = c.textBody,
        )
        val rowH = with(density) { style.lineHeight.toDp() }
        val numStyle = TextStyle(fontFamily = AppTheme.mono, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        val measurer = rememberTextMeasurer()
        val lineCount = ed.lineCount
        val digits = max(2, lineCount.toString().length)
        val numW = with(density) { measurer.measure("0".repeat(digits), numStyle).size.width.toDp() }
        // 막대 3 · 표 14 · 사이 3 · 번호 · 오른쪽 8
        val gutter = 6.dp + 14.dp + 3.dp + numW + 8.dp
        val textLeft = with(density) { gutter.toPx() }
        val list = rememberLazyListState()
        val layouts = remember(ed) { HashMap<Int, TextLayoutResult>() }

        fun hit(pos: Offset): Int? {
            val items = list.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) return null
            val item = items.firstOrNull { pos.y < it.offset + it.size } ?: items.last()
            if (item.index >= ed.lineCount) return null
            val lay = layouts[item.index] ?: return null
            val y = (pos.y - item.offset).coerceIn(0f, lay.size.height - 1f)
            val off = lay.getOffsetForPosition(Offset(pos.x - textLeft, y))
            return ed.lineStart(item.index) + off.coerceAtMost(ed.lineText(item.index).length)
        }

        val cursor = ed.cursor
        val cursorLine = ed.cursorLine
        val sel = ed.selection
        val starts = ed.lineStarts()
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp)
                .pointerInput(ed, textLeft) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        runCatching { focus.requestFocus() }
                        if (down.type == PointerType.Mouse) {
                            // 마우스: 누른 자리 커서, 끌면 고름(스크롤은 휠)
                            hit(down.position)?.let { ed.moveTo(it) }
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                if (ch.positionChanged()) {
                                    hit(ch.position)?.let { ed.moveTo(it, extend = true) }
                                    ch.consume()
                                }
                            }
                            return@awaitEachGesture
                        }
                        // 손가락: 떼면 커서 · 움직이면 스크롤(목록에 맡김) · 오래 누르면 낱말 고름 → 끌어 늘림
                        var result = 0 // 0 = 길게 누름, 1 = 누름, 2 = 스크롤 · 두 손가락
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch == null || ev.changes.size > 1) {
                                    result = 2
                                    break
                                }
                                if (!ch.pressed) {
                                    result = 1
                                    break
                                }
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    result = 2
                                    break
                                }
                            }
                        }
                        when (result) {
                            1 -> hit(down.position)?.let { ed.moveTo(it) }
                            0 -> {
                                hit(down.position)?.let { ed.selectWordAt(it) }
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    ch.consume()
                                    if (!ch.pressed) break
                                    hit(ch.position)?.let { ed.moveTo(it, extend = true) }
                                }
                            }
                        }
                    }
                },
            state = list,
        ) {
            items(lineCount) { i ->
                val start = starts[i]
                val end = if (i + 1 < starts.size) starts[i + 1] - 1 else ed.text.length
                val text = ed.text.substring(start, end)
                val lineIssues = issues[i + 1].orEmpty()
                val current = i == cursorLine
                val localSel = sel?.let { s ->
                    val a = maxOf(s.first, start) - start
                    val b = minOf(s.last + 1, end) - start
                    if (a < b) a until b else null
                }
                EditorLine(
                    number = i + 1,
                    text = text,
                    style = style,
                    numStyle = numStyle,
                    gutter = gutter,
                    rowH = rowH,
                    current = current,
                    caret = if (current && sel == null) cursor - start else null,
                    selection = localSel,
                    issues = lineIssues,
                    showNotes = current || i + 1 == stopLine,
                    p = p,
                    onLayout = { layouts[i] = it },
                )
            }
        }
        if (ed.text.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                keepWords(placeholder),
                Modifier.padding(start = gutter + 14.dp, top = 6.dp + rowH * 1.4f, end = 16.dp),
                color = c.textMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
        Scrollbar(list.layoutInfo.totalItemsCount, list.firstVisibleItemIndex, list.layoutInfo.visibleItemsInfo.size, Modifier.align(Alignment.CenterEnd))

        // 키패드 · 붙여넣기로 커서가 화면 밖으로 가면 따라간다
        LaunchedEffect(cursorLine, lineCount) {
            // 처음 그릴 때는 줄 자리가 잡힌 뒤에
            snapshotFlow { list.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
            val info = list.layoutInfo
            val vis = info.visibleItemsInfo
            val item = vis.firstOrNull { it.index == cursorLine }
            if (item != null && item.offset >= 0 && item.offset + item.size <= info.viewportEndOffset) return@LaunchedEffect
            if (cursorLine <= list.firstVisibleItemIndex) list.scrollToItem(cursorLine)
            else list.scrollToItem((cursorLine - (vis.size - 3)).coerceAtLeast(0))
        }
    }
}

/** 오른쪽 스크롤 막대(09) — 줄이 넘칠 때만 */
@Composable
private fun Scrollbar(total: Int, first: Int, visible: Int, modifier: Modifier) {
    if (total <= 0 || visible >= total) return
    val color = AppTheme.colors.lineStrong
    Canvas(modifier.fillMaxHeight().width(10.dp).padding(vertical = 8.dp, horizontal = 3.dp)) {
        val h = size.height
        val thumb = (h * visible / total).coerceAtLeast(24.dp.toPx()).coerceAtMost(h)
        val top = ((h - thumb) * first / (total - visible).coerceAtLeast(1)).coerceIn(0f, h - thumb)
        drawRoundRect(color.copy(alpha = .75f), Offset(0f, top), Size(size.width, thumb), CornerRadius(size.width / 2))
    }
}

/** 편집기 한 줄: 줄 번호 칸 + 글(커서 · 고른 글 · 물결 밑줄) + (보일 때) 문제 글 */
@Composable
private fun EditorLine(
    number: Int,
    text: String,
    style: TextStyle,
    numStyle: TextStyle,
    gutter: Dp,
    rowH: Dp,
    current: Boolean,
    caret: Int?,
    selection: IntRange?,
    issues: List<SimIssue>,
    showNotes: Boolean,
    p: SimPalette,
    onLayout: (TextLayoutResult) -> Unit,
) {
    val c = AppTheme.colors
    val annotated = remember(text, p) { syntaxLine(text, p) }
    var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
    val worst = issues.minByOrNull { it.level.ordinal }?.level
    Column(Modifier.fillMaxWidth().background(if (current) p.hlLine else Color.Transparent)) {
        Row {
            Box(Modifier.width(gutter).height(rowH)) {
                if (current) Box(Modifier.width(3.dp).fillMaxHeight().background(p.hlBar))
                Row(
                    Modifier.fillMaxSize().padding(start = 6.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (worst != null) Icon(iconOf(worst), null, Modifier.size(14.dp), tint = c.roleOf(worst).label)
                    Spacer(Modifier.width(3.dp))
                    BasicText(
                        number.toString(),
                        Modifier.weight(1f),
                        style = numStyle.copy(
                            color = if (current) c.textStrong else c.textMuted,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.End,
                        ),
                    )
                }
            }
            BasicText(
                annotated,
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .drawBehind {
                        val l = lay ?: return@drawBehind
                        if (selection != null && selection.last < l.layoutInput.text.length) {
                            drawPath(l.getPathForRange(selection.first, selection.last + 1), p.caret.copy(alpha = .24f))
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val l = lay ?: return@drawWithContent
                        val n = l.layoutInput.text.length
                        for (i in issues) {
                            val cols = i.cols ?: continue
                            if (i.level == IssueLevel.INFO || cols.first >= n) continue
                            wavy(l, cols.first, minOf(cols.last + 1, n), c.roleOf(i.level).label)
                        }
                        if (caret != null && caret <= n) {
                            val r = l.getCursorRect(caret)
                            val inset = 3.dp.toPx()
                            drawRect(p.caret, Offset(r.left - 1.dp.toPx(), r.top + inset), Size(2.5.dp.toPx(), r.height - 2 * inset))
                        }
                    },
                style = style,
                onTextLayout = {
                    lay = it
                    onLayout(it)
                },
            )
        }
        if (showNotes) {
            for (i in issues) IssueNote(i, Modifier.padding(start = gutter, end = 12.dp, bottom = 6.dp))
        }
    }
}

/** 줄 아래 문제 글 한 줄: 왼쪽 색 띠 + 모양 아이콘 + 글(색만으로 가르지 않게) */
@Composable
private fun IssueNote(i: SimIssue, modifier: Modifier) {
    val c = AppTheme.colors
    val rc = c.roleOf(i.level)
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rc.bg)
            .drawWithContent {
                drawContent()
                drawRect(rc.label, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 9.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(iconOf(i.level), null, Modifier.padding(top = 1.dp).size(14.dp), tint = rc.label)
        Spacer(Modifier.width(6.dp))
        Text(keepWords(simIssueText(i)), color = rc.value, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

/** 문제 낱말 아래 물결 밑줄(줄이 접히면 줄마다) */
private fun DrawScope.wavy(l: TextLayoutResult, a: Int, b: Int, color: Color) {
    if (a >= b) return
    val first = l.getLineForOffset(a)
    val last = l.getLineForOffset(b - 1)
    val amp = 1.6.dp.toPx()
    val step = 3.dp.toPx()
    for (row in first..last) {
        val x0 = if (row == first) l.getBoundingBox(a).left else l.getLineLeft(row)
        val x1 = if (row == last) l.getBoundingBox(b - 1).right else l.getLineRight(row)
        if (x1 - x0 < 1f) continue
        val y = l.getLineBaseline(row) + 3.5.dp.toPx()
        val path = Path().apply {
            moveTo(x0, y)
            var x = x0
            var up = true
            while (x < x1) {
                val nx = minOf(x + step, x1)
                lineTo(nx, if (up) y - amp else y + amp)
                x = nx
                up = !up
            }
        }
        drawPath(path, color, style = Stroke(1.4.dp.toPx()))
    }
}
