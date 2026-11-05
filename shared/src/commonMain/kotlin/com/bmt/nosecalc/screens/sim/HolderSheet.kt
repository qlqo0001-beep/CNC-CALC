package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

/**
 * 공구 카드의 홀더 칸: '홀더 (선택)'(고친 뒤엔 옆에 '수정됨') + 칸(품번 또는 '고르지 않음', 오른쪽 '고르기 ⌄') + 아랫줄 묶음 · 제조사와 까닭.
 * 누르면 [HolderSheet]. 칸에는 품번만 둔다 — 360dp · 큰 글꼴에서도 품번(S12M-STFCR09 / 11 …)이 줄지 않게, 제조사로 나뉜 줄도 어느 것인지 보이게
 */
@Composable
internal fun HolderBox(h: CompanyHolder?, edited: Boolean, onPick: (CompanyHolder?) -> Unit) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("홀더 (선택)", color = c.fieldNormal.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (edited) {
                Text(
                    "수정됨",
                    Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(c.amber.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = c.amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(c.fieldNormal.bg)
                .border(1.5.dp, c.fieldNormal.border, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                h?.code ?: "고르지 않음",
                Modifier.weight(1f),
                color = if (h != null) c.textValue else c.textHint,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text("고르기", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(14.dp), tint = c.textMuted)
        }
        if (h != null) {
            // 묶음 · 값을 읽은 제조사(제조사로 나뉜 줄은 그 제조사 하나)
            val makers = if (h.makers.isEmpty()) "제조사 확인 필요" else h.makers.joinToString(" · ")
            Text(keepWords("${h.group.label} · $makers"), Modifier.padding(top = 4.dp), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        h?.note?.let { Text(keepWords(it), Modifier.padding(top = 2.dp), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp) }
    }
    if (open) {
        HolderSheet(h, onPick = {
            onPick(it)
            open = false
        }) { open = false }
    }
}

/**
 * 홀더 고르기 시트(회사 사용): 고르지 않음 → 외경 바이트 · 나사 · 보링바 · 소경 보링 묶음. 줄 = 품번(제조사로 나뉜 줄은 그 제조사) +
 * 채우는 값(종류 · 인서트 · κr) · 제조사 + 까닭 한 줄. 고른 줄은 청록 체크
 */
@Composable
internal fun HolderSheet(current: CompanyHolder?, onPick: (CompanyHolder?) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    BottomSheet(onDismiss, maxHeightFraction = 0.85f) {
        SheetTitle("홀더 고르기", "회사 사용")
        Text(
            keepWords(
                "고르면 종류 · 인서트 모양 · 절입각 κr 을 제조사 카탈로그 값으로 채웁니다(고칠 수 있음). " +
                    "노즈 R(인서트 코너) · 팁 번호(기계 터렛 방향) · 나사 각(인서트)은 홀더가 정하지 않아 그대로 둡니다.",
            ),
            Modifier.padding(bottom = 8.dp),
            style = AppType.caption,
            color = c.textMuted,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            HolderRow("고르지 않음", null, "칸에 넣은 값 그대로", null, current == null) { onPick(null) }
            for (g in HolderGroup.entries) {
                Text(g.label, Modifier.padding(top = 14.dp, bottom = 4.dp), color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val list = COMPANY_HOLDERS.filter { it.group == g }
                for (h in list) {
                    val split = list.count { it.code == h.code } > 1
                    // 같은 품번이 제조사로 나뉜 줄은 품번 옆에 제조사(값 줄에서는 뺀다)
                    HolderRow(h.code, if (split) h.makers.joinToString(" · ") else null, holderSub(h, makers = !split), h.note, h.id == current?.id) { onPick(h) }
                }
            }
        }
    }
}

/** 시트 줄의 값: 채우는 것(종류 · 인서트 · κr)과 제조사(없으면 '제조사 확인 필요') */
private fun holderSub(h: CompanyHolder, makers: Boolean): String = buildList {
    add(toolKindLabel(h.kind))
    if (h.kind == ToolKind.TURN) {
        add(h.insert?.let { "${it.name} ${it.epsDeg.toInt()}°" } ?: "인서트 직접")
        add(h.kappa?.let { "κr ${numText(it)}°" } ?: "κr 직접")
    }
    if (makers) add(if (h.makers.isEmpty()) "제조사 확인 필요" else h.makers.joinToString(" · "))
}.joinToString(" · ")

/** 시트 한 줄(52dp 이상): 품번(굵게) + 제조사 표(tag) · 아랫줄 값 · 까닭, 고른 줄은 오른쪽 청록 체크 */
@Composable
private fun HolderRow(name: String, tag: String?, sub: String, note: String?, on: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f, fill = false), color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tag != null) Text(tag, Modifier.padding(start = 6.dp), color = c.textMuted, fontSize = 13.sp, maxLines = 1)
            }
            Text(keepWords(sub), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
            if (note != null) Text(keepWords(note), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        if (on) Icon(AppIcons.Check, "고름", Modifier.size(20.dp), tint = c.teal)
    }
}
