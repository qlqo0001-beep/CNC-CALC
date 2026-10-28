package com.bmt.nosecalc.screens.cutting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.onFill

/** 재료 화면의 강조색(헤더 제목색 — 라이트 #0369A1 카드 위 5.9:1, 다크 #38BDF8) */
internal fun AppColors.cutAccent(): Color = features.getValue(Feature.CUTTING).title

/**
 * ISO 513:2012 표 5 의 피삭재 군 식별색(P 파랑 · M 노랑 · K 빨강 · N 초록 · S 갈색 · H 회색) — 인서트 상자 · 카탈로그 표와 같은 색이라 그대로 쓴다.
 * 경고 색(노랑 · 빨강)과 같은 계열이지만 늘 군 글자(P M K N S H)와 함께 띠 · 글자 판으로만 쓰고 바탕 · 경고 자리에는 쓰지 않는다.
 * 다크는 같은 색상의 밝은 쪽(글자 판 위 글은 [onFill] 이 고른다)
 */
internal fun AppColors.iso(g: IsoGroup): Color = if (dark) {
    when (g) {
        IsoGroup.P -> Color(0xFF60A5FA)
        IsoGroup.M -> Color(0xFFFACC15)
        IsoGroup.K -> Color(0xFFF87171)
        IsoGroup.N -> Color(0xFF4ADE80)
        IsoGroup.S -> Color(0xFFD19A6A)
        IsoGroup.H -> Color(0xFF9CA3AF)
    }
} else {
    when (g) {
        IsoGroup.P -> Color(0xFF1D4ED8)
        IsoGroup.M -> Color(0xFFEAB308)
        IsoGroup.K -> Color(0xFFDC2626)
        IsoGroup.N -> Color(0xFF15803D)
        IsoGroup.S -> Color(0xFF92400E)
        IsoGroup.H -> Color(0xFF6B7280)
    }
}

/** ISO 513 색 이름(도움말 · 재료분석 머리) */
internal fun isoColorName(g: IsoGroup): String = when (g) {
    IsoGroup.P -> "파랑"
    IsoGroup.M -> "노랑"
    IsoGroup.K -> "빨강"
    IsoGroup.N -> "초록"
    IsoGroup.S -> "갈색"
    IsoGroup.H -> "회색"
}

/**
 * 밑줄 탭(원본 03 ②): 같은 폭 칸 · 52dp. 고른 칸 = 옅은 강조 바탕 + 3dp 밑줄 + 강조색 굵은 글, 아닌 칸 = 흐린 글(색만으로 가르지 않게 밑줄 · 바탕).
 * 글이 칸을 넘으면(좁은 폰 · 큰 글꼴) 0.8배까지 줄인다
 */
@Composable
internal fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val accent = c.cutAccent()
    Row(
        modifier
            .fillMaxWidth()
            .background(c.card)
            .drawBehind { drawRect(c.lineCard, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
    ) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(if (on) accent.copy(alpha = if (c.dark) .14f else .07f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .drawBehind { if (on) drawRect(accent, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    t,
                    style = TextStyle(
                        color = if (on) accent else c.textMuted,
                        fontFamily = AppTheme.sans,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(13.sp, 16.sp, 0.5.sp),
                )
            }
        }
    }
}

/** 군 글자 판: ISO 513 색 채움 + 굵은 글자(P · M …) */
@Composable
internal fun IsoBadge(g: IsoGroup, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val fill = c.iso(g)
    Box(modifier.size(size).clip(RoundedCornerShape(8.dp)).background(fill), contentAlignment = Alignment.Center) {
        Text(g.name, color = c.onFill(fill), fontSize = (size.value * .5f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * 피삭재 군 고르기(드릴링 ③): 여섯 칸 한 줄, 칸마다 위 4dp ISO 513 색 띠 + 군 글자. 고른 칸은 그 색 채움 + 강조 테.
 * enabled 밖(그 시리즈 표에 행이 없는 군)은 흐리게 — 누르면 onDisabled(까닭 안내)
 */
@Composable
internal fun IsoChips(selected: IsoGroup?, enabled: Set<IsoGroup>, onPick: (IsoGroup) -> Unit, onDisabled: (IsoGroup) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IsoGroup.entries.forEach { g ->
            val on = g == selected
            val ok = g in enabled
            val fill = c.iso(g)
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(shape)
                    .background(if (on) fill else c.field)
                    .drawBehind { if (!on) drawRect(fill.copy(alpha = if (ok) 1f else .35f), size = Size(size.width, 4.dp.toPx())) }
                    .border(if (on) 2.dp else 1.dp, if (on) c.textStrong else c.lineField, shape)
                    .clickable { if (ok) onPick(g) else onDisabled(g) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    g.name,
                    color = when {
                        on -> c.onFill(fill)
                        ok -> c.textStrong
                        else -> c.textHint
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/**
 * 고르는 카드(드릴링 ② 공구 종류): 제목(굵게, 3줄까지) + 아래 글(흐리게). 고르면 2dp 청록 테 + 옅은 청록 바탕 + 오른쪽 위 체크
 */
@Composable
internal fun PickCard(title: String, sub: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(c.card)
            .background(if (selected) c.teal.copy(alpha = if (c.dark) .12f else .07f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.teal else c.lineField, shape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 30.dp, top = 10.dp, bottom = 10.dp)) {
            Text(keepWords(title), color = if (selected) c.tealDeep else c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(keepWords(sub), Modifier.padding(top = 3.dp), color = c.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp).clip(CircleShape).background(c.teal),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Check, "고름", Modifier.size(12.dp), tint = c.onFill(c.teal)) }
        }
    }
}

/** 안내 상자(드릴링 첫머리 '카탈로그 참고값' — 원본 03 ④): 파랑 안내 색 + 아이콘 + 굵은 머리말 + 글 */
@Composable
internal fun NoticeBox(head: String, text: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.info.bg).border(1.dp, c.info.border, shape).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(AppIcons.Info, null, Modifier.size(20.dp), tint = c.info.label)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(head, color = c.info.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(keepWords(text), color = c.info.value, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

/** 결과 한 칸: 이름(12sp 흐림) · 값(고정폭 굵게, 계산값 파랑, 넘치면 줄임) + 단위 · 아래 글. 값이 없으면 '—' */
@Composable
internal fun ValueCell(label: String, value: String?, unit: String, modifier: Modifier = Modifier, sub: String? = null) {
    val c = AppTheme.colors
    Column(modifier) {
        Text(keepWords(label), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                value ?: "—",
                Modifier.weight(1f, fill = false),
                style = AppType.numLarge.copy(color = if (value == null) c.textHint else c.valueComp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(11.sp, AppType.numLarge.fontSize, 0.5.sp),
            )
            if (value != null && unit.isNotEmpty()) Text(" $unit", color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (sub != null) Text(keepWords(sub), color = c.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
    }
}

/**
 * 드릴 결과 표(최소 · 권장 · 최대): 머리 줄 + 줄마다 이름 · 단위(왼쪽) · 세 칸(오른쪽 맞춤 고정폭). 권장 칸은 굵고 계산값 파랑,
 * 최소 · 최대는 본문 색. 없는 칸 '—'
 */
@Composable
internal fun DrillTable(lines: List<DrillLine>) {
    val c = AppTheme.colors
    val w = listOf(1.35f, 1f, 1f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.weight(w[0]))
            listOf("최소", "권장", "최대").forEachIndexed { i, t ->
                Text(t, Modifier.weight(w[i + 1]), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineStrong))
        lines.forEach { l ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(w[0])) {
                    Text(keepWords(l.name), color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
                    Text(l.unit, color = c.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
                listOf(l.min, l.rec, l.max).forEachIndexed { i, v ->
                    BasicText(
                        v ?: "—",
                        Modifier.weight(w[i + 1]).padding(start = 4.dp),
                        style = (if (i == 1) AppType.numLarge else AppType.numBody).copy(
                            color = when {
                                v == null -> c.textHint
                                i == 1 -> c.valueComp
                                else -> c.textValue
                            },
                            textAlign = TextAlign.End,
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(10.sp, if (i == 1) AppType.numLarge.fontSize else AppType.numBody.fontSize, 0.5.sp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
        }
    }
}

/**
 * 드릴 모양(원본 03 의 제품 사진 자리): 형식마다 옆에서 본 모양, 날 끝이 왼쪽. 실제 비율 · 치수가 아닌 형식 그림이다.
 *  - 솔리드 초경: 생크 + 나선 홈 몸통 + 140° 날 끝
 *  - 헤드 교환식: 같은 몸통 + 날 끝에 따로 끼운 헤드(이음선, 인서트 색) + 굵은 생크 턱
 *  - 인서트(인덱서블): 곧은 홈 몸통 + 평평한 날 끝에 인서트 둘(중심 · 바깥) + 굵은 생크 턱
 * dim = 아직 안 고름(흐리게)
 */
@Composable
internal fun DrillArt(type: String?, modifier: Modifier = Modifier, dim: Boolean = false) {
    val c = AppTheme.colors
    Canvas(modifier) {
        val a = if (dim) .4f else 1f
        val line = c.drawToolLine.copy(alpha = a)
        val body = c.drawToolBody.copy(alpha = a)
        val insert = c.drawInsert.copy(alpha = a)
        val w = size.width
        val h = size.height
        val cy = h / 2
        val x0 = w * .06f
        val x1 = w * .94f
        val len = x1 - x0
        val d = minOf(h * .36f, len * .22f)
        val stroke = 1.6.dp.toPx()
        val big = type == "exchangeable-head" || type == "indexable"
        val shankD = if (big) d * 1.5f else d
        val xs = x0 + len * .60f
        // 생크(+ 턱)
        val shank = Path().apply {
            val r = shankD / 2
            moveTo(xs, cy - r)
            lineTo(x1 - r * .35f, cy - r)
            lineTo(x1, cy - r * .65f)
            lineTo(x1, cy + r * .65f)
            lineTo(x1 - r * .35f, cy + r)
            lineTo(xs, cy + r)
            close()
        }
        drawPath(shank, body)
        drawPath(shank, line, style = Stroke(stroke, join = StrokeJoin.Round))
        // 몸통 + 날 끝
        val r = d / 2
        val tipLen = if (type == "indexable") r * .12f else r / 2.75f // 140° 날 끝: (d/2)/tan70° ≈ 0.36 · (d/2)
        val bodyPath = Path().apply {
            moveTo(xs, cy - r)
            lineTo(x0 + tipLen, cy - r)
            lineTo(x0, cy)
            lineTo(x0 + tipLen, cy + r)
            lineTo(xs, cy + r)
            close()
        }
        drawPath(bodyPath, body)
        // 홈: 나선(사선) 또는 곧은 홈
        clipPath(bodyPath) {
            if (type == "indexable") {
                drawLine(line.copy(alpha = .55f * a), Offset(x0, cy - r * .15f), Offset(xs, cy - r * .15f), stroke * .8f)
                drawLine(line.copy(alpha = .55f * a), Offset(x0, cy + r * .45f), Offset(xs, cy + r * .45f), stroke * .8f)
            } else {
                val pitch = d * 1.1f
                var x = x0 - d
                while (x < xs + d) {
                    drawLine(line.copy(alpha = .55f * a), Offset(x, cy + r), Offset(x + d * .9f, cy - r), stroke * .8f)
                    x += pitch
                }
            }
        }
        drawPath(bodyPath, line, style = Stroke(stroke, join = StrokeJoin.Round))
        when (type) {
            "exchangeable-head" -> {
                // 헤드: 날 끝에서 몸통 지름의 0.9배 길이, 이음선
                val xh = x0 + d * .9f
                val head = Path().apply {
                    moveTo(xh, cy - r)
                    lineTo(x0 + tipLen, cy - r)
                    lineTo(x0, cy)
                    lineTo(x0 + tipLen, cy + r)
                    lineTo(xh, cy + r)
                    close()
                }
                drawPath(head, insert.copy(alpha = .55f * a))
                drawPath(head, line, style = Stroke(stroke, join = StrokeJoin.Round))
            }
            "indexable" -> {
                // 인서트 둘: 바깥(위) · 중심(아래) — 끝에서 조금 나온 네모
                val s = r * .62f
                listOf(cy - r + s * .55f, cy + r * .05f).forEach { y ->
                    val p = Path().apply {
                        moveTo(x0 - s * .12f, y - s / 2)
                        lineTo(x0 + s * .9f, y - s / 2)
                        lineTo(x0 + s * .9f, y + s / 2)
                        lineTo(x0 - s * .12f, y + s / 2)
                        close()
                    }
                    drawPath(p, insert)
                    drawPath(p, line, style = Stroke(stroke * .8f, join = StrokeJoin.Round))
                }
            }
        }
        centerLine(Offset(x0 - 4.dp.toPx(), cy), Offset(x1 + 4.dp.toPx(), cy), c.drawRef.copy(alpha = a), 1f)
    }
}

/** 카드 안 작은 제목(① 메이커 선택 같은) */
@Composable
internal fun StepTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(keepWords(text), Modifier.weight(1f), color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        if (trailing != null) Text(keepWords(trailing), Modifier.padding(start = 8.dp), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 그림 · 표를 가르는 가는 선 */
@Composable
internal fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.lineCard))
}

/** 세로 색 띠(재료분석 카드 왼쪽) */
@Composable
internal fun IsoBand(g: IsoGroup, modifier: Modifier = Modifier) {
    Box(modifier.width(6.dp).fillMaxHeight().background(AppTheme.colors.iso(g)))
}
