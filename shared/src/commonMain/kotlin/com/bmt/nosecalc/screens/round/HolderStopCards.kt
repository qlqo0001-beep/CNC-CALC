package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.screens.taper.NcFact
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.CoordPair
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.MarkKind
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.mark
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import com.bmt.nosecalc.ui.theme.onFill
import kotlinx.coroutines.delay

/** 좌표 기준 한 줄(작은 글): 지금 고른 NC 의 좌표가 무엇인가 */
internal fun coordBasis(comp: Boolean) = if (comp) "직접 보정 기준 · 가상 인선 좌표 (G40 프로그램)" else "기계 보정 기준 · 도면 명령 좌표 (G41/\u2060G42 프로그램)"

/** 안전 끝점 색(깃발 · 끊은 줄): 보링바 단면과 같은 초록 — 간섭 빨강과 갈린다 */
@Composable
internal fun stopColor() = AppTheme.colors.drawHolder

/** 후퇴 색(뷰어 점선 · NC 줄): 안내 파랑 */
@Composable
internal fun retractColor() = AppTheme.colors.info.label

/**
 * 간섭 목록 카드(노란 띠 대신): 머리 '보링바 간섭 — n곳'(노랑 경고 아이콘) + 좌표 기준 → 간섭마다 한 줄 —
 * 부분 · 벽과 전체 NC 줄, 닿기 시작 · 끝 X(직경) · Z(지금 NC 기준), 깊이. 줄을 누르면 [onPick](뷰어가 그 자리로, NC 가 그 줄을 짚는다).
 * picked = 지금 짚은 줄(초점색 띠 · 옅은 바탕). 'NC n줄' 은 간섭 줄과 같은 빨강
 */
@Composable
internal fun HolderHitsCard(rows: List<HitRow>, comp: Boolean, picked: HitRow?, onPick: (HitRow) -> Unit) {
    val c = AppTheme.colors
    val value = if (comp) c.valueComp else c.textValue
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Alert, null, Modifier.size(20.dp), tint = c.warn.label)
            Spacer(Modifier.width(8.dp))
            Text("보링바 간섭 — ${rows.size}곳", style = AppType.cardTitle, color = c.textStrong)
        }
        Text(keepWords("${coordBasis(comp)} · 줄은 전체 NC"), Modifier.padding(top = 2.dp, bottom = 8.dp), style = AppType.caption, color = c.textMuted)
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
            val on = row === picked
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.lineFocus.copy(alpha = .12f) else Color.Transparent)
                    .drawBehind { if (on) drawRect(c.lineFocus, Offset.Zero, Size(3.dp.toPx(), size.height)) }
                    .clickable { onPick(row) }
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.part, Modifier.weight(1f), color = c.textStrong, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "NC ${row.lines}",
                        Modifier.clip(RoundedCornerShape(6.dp)).background(c.error.bg).border(1.dp, c.error.border, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                        color = c.error.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                listOf("시작" to row.from, "끝" to row.to).forEach { (name, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.width(34.dp), color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("X${Fmt.fixed3(p.xDia)}", Modifier.weight(1f), style = AppType.numBody, color = value, textAlign = TextAlign.End, maxLines = 1)
                        Text("Z${Fmt.fixed3(p.z)}", Modifier.weight(1f), style = AppType.numBody, color = value, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
                Text(keepWords(row.detail), Modifier.padding(top = 3.dp), color = c.textBody, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
        Text(
            keepWords("줄을 누르면 형상 확인이 그 자리로 가고, NC 는 전체(확인용)로 바뀌어 그 줄을 짚습니다."),
            Modifier.padding(top = 8.dp),
            style = AppType.caption,
            color = c.textMuted,
        )
    }
}

/**
 * 가공 가능 끝점 카드: 끊고 빠지면 초록 깃발 + '여기까지 깎고 빠집니다' + 좌표(크게, 지금 NC 기준) + 끊은 줄 · (기계 보정이 덜 깎으면 그 양) ·
 * 가장 가까운 부분 · 여유(직선 거리) · (깎는 면 예외가 쓰였으면 작은 글 한 줄) · 후퇴 방식. 끝까지 안전하면 초록 체크 + 가장 좁은 곳, 깎을 수 없으면
 * (진입점부터 · 깎기 전에 멈춤 · 기계 보정 잘린 NC 없음) 노랑 경고. 후퇴 여유가 안전 여유보다 좁으면 노랑 한 줄
 */
@Composable
internal fun SafeEndCard(v: StopView, comp: Boolean, safe: Double) {
    val c = AppTheme.colors
    val flag = stopColor()
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        CardHeader("가공 가능 끝점", keepWords("안전 여유 ${plain(safe)} mm · ${coordBasis(comp)}"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (v.kind) {
                StopKind.CUT -> Canvas(Modifier.size(20.dp)) { mark(MarkKind.Flag, Offset(size.width * .3f, size.height * .92f), flag, c.card) }
                StopKind.CLEAR -> Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = flag)
                else -> Icon(AppIcons.Alert, null, Modifier.size(20.dp), tint = c.warn.label)
            }
            Spacer(Modifier.width(8.dp))
            Text(keepWords(v.head), color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        val at = v.at
        if (v.kind == StopKind.CUT && at != null) {
            Spacer(Modifier.height(8.dp))
            CoordPair(Fmt.fixed3(at.xDia), Fmt.fixed3(at.z), if (comp) c.valueComp else c.textValue)
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOfNotNull(v.where, v.lack, v.near).forEach { Text(keepWords(it), color = c.textBody, fontSize = 13.sp, lineHeight = 19.sp) }
            // 깎는 면 예외(바 앞면이 지금 깎는 면에 안전 여유보다 가까이 붙음)는 가장 가까운 곳 바로 아래 작은 글
            v.face?.let { Text(keepWords(it), style = AppType.caption, color = c.textMuted) }
            listOfNotNull(v.retract, v.note).forEach { Text(keepWords(it), color = c.textBody, fontSize = 13.sp, lineHeight = 19.sp) }
        }
        // 노랑 한 줄: 후퇴 여유가 안전 여유보다 좁음
        v.tight?.let {
            Row(Modifier.padding(top = 8.dp)) {
                Icon(AppIcons.Alert, null, Modifier.padding(top = 2.dp).size(16.dp), tint = c.warn.label)
                Spacer(Modifier.width(6.dp))
                Text(keepWords(it), color = c.warn.label, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (v.kind == StopKind.CUT) {
            Text(
                keepWords("나머지는 다른 공구로 깎습니다. 아래 NC 는 이 점까지 깎고(초록 줄) 빠지는(파란 줄) 잘린 프로그램입니다."),
                Modifier.padding(top = 8.dp),
                style = AppType.caption,
                color = c.textMuted,
            )
        }
    }
}

/** NC 줄 표시: 간섭이 걸린 줄(빨강) · 안전 끝에서 끊은 마지막 블록(초록) · 후퇴(파랑) */
internal enum class NcTone { HIT, END, RETRACT }

@Composable
private fun toneColor(t: NcTone) = when (t) {
    NcTone.HIT -> AppTheme.colors.error.label
    NcTone.END -> stopColor()
    NcTone.RETRACT -> retractColor()
}

/**
 * 내경 라운드의 NC 카드(테이퍼 화면의 NcCard 와 같은 꼴 — 그 파일은 다른 작업이 쓰고 있어 여기 따로 둔다): 머리(제목 · 복사 아이콘) →
 * top(잘린 / 전체 스위치) → 꼭 알 것(facts) → 줄 번호 + 고정폭 줄(tones 의 줄은 번호 왼쪽 3dp 띠 + 굵은 색 번호, 끊은 줄 · 후퇴 줄은 옅은 바탕도,
 * focus 줄은 초점색 바탕) → 줄 색 이름표 → 큰 복사 단추(보이는 글 그대로 — danger 면 노랑 테 '간섭 포함') → 설명(첫 문장 + 자세히)
 */
@Composable
internal fun HolderNcCard(
    lines: List<String>,
    note: String,
    programName: String,
    facts: List<NcFact>,
    tones: Map<Int, NcTone> = emptyMap(),
    focus: Int? = null,
    danger: Boolean = false,
    top: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    val state = LocalAppState.current
    @Suppress("DEPRECATION") // LocalClipboard 는 ClipEntry 를 플랫폼마다 따로 만들어야 한다. 글 복사는 이것으로 충분하다
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val copy = {
        clipboard.setText(AnnotatedString(lines.joinToString("\n")))
        state.toast("$programName ${lines.size}줄을 복사했습니다")
        copied = true
    }
    val shown = lines.take(MAX_NC_SHOWN)
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("NC 프로그램") {
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, c.lineCard, CircleShape)
                    .clickable(onClick = copy),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Copy, "복사", Modifier.size(20.dp), tint = c.textBody)
            }
        }
        if (top != null) {
            top()
            Spacer(Modifier.height(10.dp))
        }
        if (facts.isNotEmpty()) {
            FlowRow(
                Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { facts.forEach { FactChip(it) } }
        }
        val shape = RoundedCornerShape(8.dp)
        val tint = mapOf(NcTone.END to stopColor().copy(alpha = .12f), NcTone.RETRACT to retractColor().copy(alpha = .08f))
        val focusTint = c.lineFocus.copy(alpha = .22f)
        Column(Modifier.fillMaxWidth().clip(shape).background(c.field).border(1.dp, c.lineField, shape).padding(vertical = 10.dp)) {
            Row(
                // 줄 바탕(끊은 줄 · 후퇴 · 짚은 줄)은 번호와 글을 함께 — 줄 높이가 NC_LINE 로 같다
                Modifier.drawBehind {
                    val h = NC_LINE.toPx()
                    shown.indices.forEach { i ->
                        val bg = if (i == focus) focusTint else tones[i]?.let(tint::get)
                        if (bg != null) drawRect(bg, Offset(0f, i * h), Size(size.width, h))
                    }
                },
            ) {
                Column(Modifier.padding(start = 4.dp).width(24.dp)) {
                    shown.indices.forEach {
                        val tone = tones[it]
                        val col = tone?.let { t -> toneColor(t) }
                        Text(
                            "${it + 1}",
                            Modifier.fillMaxWidth().drawBehind {
                                if (col != null) drawRect(col, Offset(-4.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
                            },
                            style = AppType.numSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = if (tone != null || it == focus) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = NC_LINE,
                                lineHeightStyle = NcLineStyle,
                            ),
                            color = col ?: if (it == focus) c.textStrong else c.textHint,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(end = 12.dp)) { shown.forEach { NcLine(it) } }
                }
            }
            if (lines.size > shown.size) {
                Text("… ${lines.size - shown.size}줄 더 (복사하면 모두)", Modifier.padding(start = 38.dp, top = 4.dp), color = c.textMuted, fontSize = 12.5.sp)
            }
        }
        val legend = listOf(NcTone.HIT to "간섭", NcTone.END to "안전 끝 (끊은 블록)", NcTone.RETRACT to "후퇴").filter { (t) -> t in tones.values }
        if (legend.isNotEmpty() || focus != null) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                legend.forEach { (t, name) -> ToneKey(toneColor(t), name) }
                if (focus != null) ToneKey(c.lineFocus, "목록에서 고른 줄")
            }
        }
        Spacer(Modifier.height(10.dp))
        val bg = when {
            copied -> c.green
            danger -> c.warn.bg
            else -> c.teal
        }
        val fg = if (danger && !copied) c.warn.label else c.onFill(bg)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                .then(if (danger && !copied) Modifier.border(1.5.dp, c.warn.border, RoundedCornerShape(10.dp)) else Modifier)
                .clickable(onClick = copy)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (copied) AppIcons.Check else AppIcons.Copy, null, Modifier.size(20.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
            Text(
                if (copied) "복사됨" else keepWords("복사 · $programName ${lines.size}줄"),
                color = fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note.isNotEmpty()) {
            // 첫 문장만 보이고 나머지는 '자세히'
            val cut = note.indexOf(". ")
            if (cut < 0) {
                Text(note, Modifier.padding(top = 8.dp), style = AppType.caption, color = c.textMuted)
            } else {
                ExpandableHint(note.substring(0, cut + 1), note.substring(cut + 2), Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * 잘린 NC / 전체 스위치(NC 카드 머리 아래). 전체 칸 이름은 360dp 에서 반 칸에 들게 '전체 (확인용)' — 고르면 빨간 칩 '간섭 포함 — 확인용' 이
 * 바로 아래 붙는다('전체 (확인용 · 간섭 포함)' 은 360dp 에서 '…' 로 잘렸다)
 */
@Composable
internal fun CutSwitch(full: Boolean, onFull: (Boolean) -> Unit) {
    Segmented(
        listOf("안전 끝까지 (잘린 NC)", "전체 (확인용)"), if (full) 1 else 0, { onFull(it == 1) },
        Modifier.fillMaxWidth(), color = AppTheme.colors.teal, height = 44.dp, fontSize = 13,
    )
}

/** 줄 색 이름표: 3 × 12dp 띠 + 12.5sp 이름 */
@Composable
private fun ToneKey(color: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 12.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(name, color = AppTheme.colors.textBody, fontSize = 12.5.sp)
    }
}

private const val MAX_NC_SHOWN = 80

/** NC 줄 높이(줄 번호 · 줄 바탕과 같게) */
private val NC_LINE = 22.sp
private val NcLineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** 꼭 알 것 칩(28dp): 보통은 회색, 경고 · 멈춤 · 안내는 띠와 같은 색과 아이콘 */
@Composable
private fun FactChip(f: NcFact) {
    val c = AppTheme.colors
    val (rc, icon) = when (f.tone) {
        WarnLevel.Warn -> c.warn to AppIcons.Alert
        WarnLevel.Error -> c.error to AppIcons.Stop
        WarnLevel.Info -> c.info to AppIcons.Info
        null -> null to null
    }
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.height(28.dp).clip(shape).background(rc?.bg ?: c.subtle).border(1.dp, rc?.border ?: c.lineCard, shape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rc != null && icon != null) Icon(icon, null, Modifier.padding(end = 4.dp).size(14.dp), tint = rc.label)
        Text(f.text, color = rc?.label ?: c.textBody, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** G 는 파랑, X·Z 는 초록(09 편집기의 색 갈래), 주석 줄 "(…)" 은 흐리게. 접지 않는다 */
@Composable
private fun NcLine(line: String) {
    val c = AppTheme.colors
    Text(
        if (line.startsWith("(")) {
            AnnotatedString(line, SpanStyle(color = c.textMuted))
        } else {
            buildAnnotatedString {
                line.split(' ').forEachIndexed { i, w ->
                    if (i > 0) append(' ')
                    val color = when (w.firstOrNull()) {
                        'G' -> c.blue
                        'X', 'Z' -> c.valuePos
                        else -> c.textValue
                    }
                    withStyle(SpanStyle(color = color)) { append(w) }
                }
            }
        },
        style = AppType.numBody.copy(lineHeight = NC_LINE, lineHeightStyle = NcLineStyle),
        softWrap = false,
        maxLines = 1,
    )
}
