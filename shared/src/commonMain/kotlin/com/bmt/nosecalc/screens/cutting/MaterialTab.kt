package com.bmt.nosecalc.screens.cutting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.IsoGroupInfo
import com.bmt.nosecalc.calc.MaterialRef
import com.bmt.nosecalc.calc.WorkMaterials
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space

/**
 * 재료분석 탭: ISO 피삭재 6군 카드(왼쪽 ISO 513 색 띠 + 군 글자 판) — 들어가는 재료 · 가공 특성 · 대표 재질(KS 굵게, JIS · ISO · 미국 대조) · 출처.
 * 자료는 calc 의 [WorkMaterials](절삭 속도 숫자는 없다 — 드릴은 드릴링 탭의 카탈로그 값).
 */
@Composable
internal fun ColumnScope.MaterialTab() {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("ISO 피삭재 군", "ISO 513 · 가공성으로 나눈 여섯 군")
        Text(
            keepWords(
                "색 띠는 ISO 513 의 군 식별색(인서트 상자 · 카탈로그 표와 같은 색)이고 경고 색이 아닙니다. " +
                    "대표 재질의 규격 대조는 KORLOY 선삭 카탈로그 2025-2026 대조표의 칸 그대로입니다. " +
                    "같은 재료라도 공구사마다 군을 다르게 두는 경우가 있으니 쓰는 공구 표의 재질 줄을 따르세요.",
            ),
            style = AppType.caption,
            color = c.textMuted,
        )
    }
    WorkMaterials.groups.forEach { GroupCard(it) }
}

/** 한 군의 카드: 왼쪽 6dp 색 띠 · 머리(글자 판 · 이름 · 'ISO P · 파랑') · 들어가는 재료 · 가공 특성 · 대표 재질 · 출처 */
@Composable
private fun GroupCard(g: IsoGroupInfo) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.lineCard, shape),
    ) {
        IsoBand(g.group)
        Column(Modifier.weight(1f).padding(Space.cardPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IsoBadge(g.group, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = AppType.cardTitle, color = c.textStrong)
                    Text("ISO ${g.group.name} · ISO 513 ${isoColorName(g.group)}", style = AppType.caption, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Labeled("들어가는 재료", g.covers)
            Spacer(Modifier.height(8.dp))
            Labeled("가공 특성", g.machining)
            Spacer(Modifier.height(12.dp))
            Text("대표 재질", color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Rule()
            g.examples.forEach { ExampleRow(it) }
            Spacer(Modifier.height(10.dp))
            val sources = g.examples.map { it.source }.distinct()
            Text(
                keepWords("출처 — 설명: ${g.source} · 대표 재질: ${sources.joinToString(" · ")}"),
                style = AppType.caption,
                color = c.textMuted,
            )
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    val c = AppTheme.colors
    Text(label, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text(keepWords(text), style = AppType.body, color = c.textBody)
}

/** 대표 재질 한 줄: 이름(작게) · KS 기호(고정폭 굵게, 없으면 이름) · 'JIS … · ISO … · 미국 …' · 메모 */
@Composable
private fun ExampleRow(m: MaterialRef) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val main = m.ks ?: m.us ?: m.name
        Row(verticalAlignment = Alignment.Bottom) {
            Text(keepWords(main), style = if (main == m.name) AppType.cardTitle else AppType.numLarge, color = c.textValue)
            if (m.ks != null) {
                Text(" KS", Modifier.padding(start = 2.dp, bottom = 2.dp), color = c.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            if (main != m.name) {
                Spacer(Modifier.width(10.dp))
                Text(keepWords(m.name), Modifier.weight(1f).padding(bottom = 2.dp), color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val cross = listOfNotNull(m.jis?.let { "JIS $it" }, m.iso?.let { "ISO $it" }, m.us?.takeIf { m.ks != null }?.let { "미국 $it" })
        if (cross.isNotEmpty()) Text(keepWords(cross.joinToString("  ·  ")), color = c.textBody, fontSize = 12.5.sp, lineHeight = 18.sp)
        m.note?.let { Text(keepWords(it), style = AppType.caption, color = c.textMuted) }
    }
    Rule()
}
