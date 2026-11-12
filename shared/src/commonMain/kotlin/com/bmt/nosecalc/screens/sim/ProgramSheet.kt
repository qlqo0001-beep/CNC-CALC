package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.Modal
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

/**
 * '목록' 시트(설계 7.3 — 우리가 정함): 저장한 프로그램(O 번호 · 첫 주석 · 줄 수 · 저장 시각, 최근 저장 먼저) → 예제.
 * 누르면 불러옴(고친 글이 있으면 먼저 묻는다), 지우기는 휴지통 단추나 길게 누르기 → 묻는 창.
 */
@Composable
internal fun ProgramSheet(sim: SimState, onDismiss: () -> Unit) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    val progs = remember(sim.listVersion) { sim.programs.list() }
    BottomSheet(onDismiss, maxHeightFraction = 0.85f) {
        SheetTitle("프로그램 목록", trailing = "${progs.size} / ${ProgramStore.MAX_PROGRAMS}") {
            Spacer(Modifier.width(8.dp))
            PillButton("새 프로그램", AppIcons.Plus, { sim.request(Pending.New) })
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionLabel("저장한 프로그램") }
            if (progs.isEmpty()) {
                item {
                    Text(
                        keepWords("저장한 프로그램이 없습니다. 편집기의 '저장' 은 첫 O 번호를 이름으로 저장합니다."),
                        Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        color = c.textMuted,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            items(progs, key = { it.number }) { info ->
                SavedRow(
                    info,
                    current = (sim.origin as? Origin.Saved)?.number == info.number,
                    onOpen = { sim.request(Pending.Load(info.number))?.let { app.toast(it) } },
                    onDelete = { sim.ask = SimAsk.Delete(info.number) },
                )
            }
            item { SectionLabel("예제 — 출처와 바꾼 곳은 이름 아래", Modifier.padding(top = 10.dp)) }
            itemsIndexed(exampleList) { i, e ->
                val number = e.text.substringBefore(' ')
                ListRow(
                    title = e.name,
                    sub = "$number · ${e.source}",
                    current = sim.origin == Origin.Example(i),
                    onClick = { sim.request(Pending.Example(i)) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 4.dp, top = 4.dp), color = AppTheme.colors.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
}

/** 저장한 프로그램 한 줄: O 번호(고정폭) · 첫 주석 · 줄 수와 저장 시각 · 휴지통 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRow(info: ProgramInfo, current: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val name = ProgramStore.oText(info.number)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(c.card)
            .border(if (current) 2.dp else 1.dp, if (current) c.teal else c.lineCard, shape)
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = AppType.numBody, color = c.textStrong)
                if (current) {
                    Spacer(Modifier.width(8.dp))
                    Text("지금 편집 중", color = c.tealDeep, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(info.comment ?: "주석 없음", color = if (info.comment != null) c.textBody else c.textHint, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${info.lines}줄" + (info.savedAt?.let { " · ${savedAtText(it)} 저장" } ?: ""),
                color = c.textMuted,
                fontSize = 12.sp,
            )
        }
        IconAction(SimIcons.Trash, "$name 지우기", onDelete)
    }
}

/** 48dp 아이콘 단추(테 없음) */
@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.minimumInteractiveComponentSize().size(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = AppTheme.colors.textMuted)
    }
}

/** 제목 · 부제 한 줄 단추(예제 · 더 보기 메뉴) */
@Composable
private fun ListRow(title: String, sub: String, current: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(c.card)
            .border(if (current) 2.dp else 1.dp, if (current) c.teal else c.lineCard, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.icon)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textStrong, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(keepWords(sub), color = c.textMuted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.textHint)
    }
}

/** ⋯ 시트: 새 프로그램 · 붙여넣기 · 전체 복사 · 예제 */
@Composable
internal fun MoreSheet(onNew: () -> Unit, onPaste: () -> Unit, onCopyAll: () -> Unit, onExamples: () -> Unit, onDismiss: () -> Unit) {
    BottomSheet(onDismiss) {
        SheetTitle("프로그램")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListRow("새 프로그램", "빈 편집기 — 저장하지 않은 글이 있으면 먼저 묻습니다", icon = AppIcons.Plus, onClick = onNew)
            ListRow("붙여넣기", "클립보드 글을 커서 자리에 (대문자로)", icon = SimIcons.Keyboard, onClick = onPaste)
            ListRow("전체 복사", "편집기 글 전체를 클립보드로", icon = AppIcons.Copy, onClick = onCopyAll)
            ListRow("예제 열기", "Siemens 설명서의 사이클 예 · 원본 화면 글 · 우리 예 11개", icon = SimIcons.Play, onClick = onExamples)
        }
    }
}

/** 문제 목록 시트: 줄 순, 누르면 그 줄로. 실행이 멈췄으면 머리 아래에 멈춘 줄 */
@Composable
internal fun IssueSheet(issues: List<SimIssue>, stop: SimIssue?, onPick: (SimIssue) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    BottomSheet(onDismiss, maxHeightFraction = 0.75f) {
        SheetTitle("문제 ${issues.size}", trailing = issueCounts(issues))
        if (stop != null) {
            Text(
                keepWords("실행은 ${lineLabel(stop.line)}에서 멈춥니다 — 그 뒤 블록은 실행 검사를 하지 않았습니다(글자 검사는 모두)."),
                Modifier.padding(bottom = 8.dp),
                color = c.textBody,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(issues) { i ->
                val rc = c.roleOf(i.level)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rc.bg)
                        .border(1.dp, rc.border, RoundedCornerShape(10.dp))
                        .clickable(enabled = i.line > 0) { onPick(i) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(iconOf(i.level), null, Modifier.padding(top = 2.dp).size(18.dp), tint = rc.label)
                    Spacer(Modifier.width(10.dp))
                    Text(lineLabel(i.line), Modifier.width(44.dp), color = rc.label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(keepWords(simIssueText(i)), Modifier.weight(1f), color = rc.value, fontSize = 13.5.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** 묻는 창들(저장 번호 · 덮어쓰기 · 저장 안 한 글 · 지우기). 돌려준 글은 토스트 */
@Composable
internal fun SimAskModal(sim: SimState) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    fun toast(s: String?) = s?.let { app.toast(it) }
    when (val a = sim.ask) {
        null -> {}
        SimAsk.Number -> {
            var value by remember { mutableStateOf("") }
            var bad by remember { mutableStateOf(false) }
            Modal("저장할 프로그램 번호", { sim.ask = null }) {
                Body("글에 O 번호가 없습니다. 번호를 넣으면 첫 줄에 O 번호를 넣고 그 이름으로 저장합니다.")
                FieldChainHost(rememberFieldChain()) {
                    NumberField(
                        "프로그램 번호 (O)", value, { value = it; bad = false },
                        Modifier.padding(top = 12.dp).fillMaxWidth(),
                        error = bad,
                        hint = "1 ~ 99999999 의 정수 (예 1001 → O1001)",
                        id = "o",
                    )
                }
                if (bad) Text("1 ~ 99999999 의 정수를 넣으세요", Modifier.padding(top = 6.dp), color = c.error.label, fontSize = 13.sp)
                Buttons {
                    PillButton("취소", null, { sim.ask = null })
                    PillButton("저장", AppIcons.Check, {
                        val n = value.takeIf { v -> v.all { it.isDigit() } }?.toIntOrNull()?.takeIf { it in 1..99_999_999 }
                        if (n == null) bad = true else toast(sim.saveAs(n))
                    })
                }
            }
        }
        is SimAsk.Overwrite -> Modal("덮어쓰기", { sim.ask = null }) {
            Body("${ProgramStore.oText(a.number)} 이 이미 저장되어 있습니다. 덮어쓰면 저장된 글은 되돌릴 수 없습니다.")
            Buttons {
                PillButton("취소", null, { sim.ask = null })
                PillButton("덮어쓰기", null, { toast(sim.save(a.number)) }, danger = true)
            }
        }
        is SimAsk.Unsaved -> Modal(if (sim.textModified && sim.editor.text.isNotBlank()) "저장하지 않은 글" else "저장하지 않은 설정", { sim.ask = null }) {
            Body(
                if (sim.textModified && sim.editor.text.isNotBlank()) {
                    "편집기 글이 저장되지 않았습니다. 버리면 지금 글은 되돌릴 수 없습니다. 남기려면 취소하고 '저장' 을 누르세요." +
                        if (sim.setupModified) " 고친 공구 · 소재 설정도 저장되지 않았습니다." else ""
                } else {
                    "공구 · 소재 설정을 고친 뒤 저장하지 않았습니다. 버리면 고친 설정은 이 프로그램에 남지 않습니다. 남기려면 취소하고 '저장' 을 누르세요."
                },
            )
            Buttons {
                PillButton("취소", null, { sim.ask = null })
                PillButton(if (a.then == Pending.New) "버리고 새로" else "버리고 열기", null, { toast(sim.perform(a.then)) }, danger = true)
            }
        }
        is SimAsk.Delete -> Modal("프로그램 지우기", { sim.ask = null }) {
            val comment = sim.programs.load(a.number)?.let { ProgramStore.firstComment(it) }
            Body("${ProgramStore.oText(a.number)}${comment?.let { " ($it)" } ?: ""} 을 지울까요? 되돌릴 수 없습니다.")
            Buttons {
                PillButton("취소", null, { sim.ask = null })
                PillButton("지우기", SimIcons.Trash, { toast(sim.delete(a.number)) }, danger = true)
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(keepWords(text), color = AppTheme.colors.textBody, fontSize = 14.5.sp, lineHeight = 22.sp)
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { content() }
}
