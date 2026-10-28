package com.bmt.nosecalc.screens.cutting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DrillCatalog
import com.bmt.nosecalc.calc.DrillResult
import com.bmt.nosecalc.calc.DrillSeries
import com.bmt.nosecalc.calc.DrillWarning
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.screens.thread.ChoiceGrid
import com.bmt.nosecalc.screens.thread.ChoiceRow
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space

/**
 * 드릴링 탭(원본 03): '드릴링 가공 조건 계산' 카드(카탈로그 참고값 안내 → 고른 공구 카드(형식 그림 · 시리즈 · 직경 범위 · 깊이 · 급유) →
 * ① 메이커 → ② 공구 종류) → ③ 피삭재(ISO 군 → 재질 행) → ④ 직경 · 절삭 조건(칸 · 경고 · 최소 · 권장 · 최대 표 · 출처).
 * 직접 입력은 ① 다음 바로 직경 · Vc · f. 값은 calc 의 [DrillCatalog] 가 낸 그대로(보간 · 권장값 만들기 없음).
 */
@Composable
internal fun ColumnScope.DrillTab(s: CuttingState, onToast: (String) -> Unit) {
    val r = s.drill()
    val series = s.drillSeries
    val missing = s.drillMissing()
    if (missing.isNotEmpty()) {
        WarningBanner(keepWords("고르고 넣으면 바로 계산합니다 — 남은 것: ${missing.joinToString(" · ")}"), level = WarnLevel.Info)
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("드릴링 가공 조건 계산", "카탈로그 절삭 조건표 → Vc · n · f · vf")
        NoticeBox(
            "카탈로그 참고값",
            "각 메이커 공식 카탈로그의 절삭 조건표 값(아래 출처 · 쪽)으로 계산합니다. 실제 가공 조건(기계 강성 · 홀더 · 고정 상태 · 재질 편차)에 " +
                "따라 다르므로 시험 가공 후 맞추세요.",
        )
        Spacer(Modifier.height(12.dp))
        ToolCard(s, series)
        Spacer(Modifier.height(18.dp))
        StepTitle("① 메이커 선택")
        val keys = DrillCatalog.makers.map { it.key } + MANUAL
        val labels = DrillCatalog.makers.map { it.label } + "직접 입력"
        ChoiceGrid(labels, keys.indexOf(s.maker).takeIf { it >= 0 }, columns = 3) { s.pickMaker(keys[it]) }
        val maker = s.drillMaker
        if (maker != null) {
            Spacer(Modifier.height(14.dp))
            StepTitle("② 공구 종류", trailing = "${maker.label} ${maker.series.size}종")
            SeriesGrid(maker.series, s.series) { s.pickSeries(it) }
        }
    }

    if (series != null) MaterialCard(s, series, onToast)
    if (s.manual || s.condition != null) ResultCard(s, r)
}

/** 고른 공구 카드(원본 03 ⑤ 의 제품 카드 자리): 왼쪽 형식 그림, 오른쪽 형식 · 시리즈 · 직경 범위 · 깊이 · 급유 */
@Composable
private fun ToolCard(s: CuttingState, series: DrillSeries?) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).border(1.dp, c.lineCard, shape),
    ) {
        Box(Modifier.width(104.dp).fillMaxHeight().background(c.subtle), contentAlignment = Alignment.Center) {
            DrillArt(series?.type, Modifier.size(width = 100.dp, height = 72.dp), dim = series == null)
        }
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (series == null) {
                Text(if (s.manual) "직접 입력" else "공구를 고르세요", color = c.cutAccent(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    keepWords(
                        if (s.manual) "카탈로그 없이 넣은 절삭 속도 Vc · 이송 f 로 회전수 · 분당 이송을 계산합니다."
                        else "메이커와 공구 종류를 고르면 형식 · 직경 범위 · 깊이 · 급유가 여기 보입니다.",
                    ),
                    style = AppType.caption,
                    color = c.textMuted,
                )
            } else {
                Text(drillTypeText(series.type), color = c.cutAccent(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(keepWords(series.series), color = c.textStrong, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                Spec("직경", "${rangeText(series.diameterMin, series.diameterMax)} mm (카탈로그 주문 범위)")
                Spec("깊이", series.depth)
                Spec("급유", coolantText(series.coolant))
            }
        }
    }
}

@Composable
private fun Spec(name: String, text: String) {
    val c = AppTheme.colors
    Row {
        Text(name, Modifier.width(34.dp), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
        Text(keepWords(text), color = c.textBody, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** ② 공구 종류: 같은 폭 카드(넓으면 두 줄 — 원본 03 ⑦, 좁은 폰 · 큰 글꼴이면 한 줄) */
@Composable
private fun SeriesGrid(list: List<DrillSeries>, selected: Int?, onPick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth / LocalDensity.current.fontScale >= 320.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.indices.chunked(cols).forEach { line ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { i ->
                        val d = list[i]
                        val (title, rest) = seriesTitle(d.series)
                        PickCard(
                            title,
                            listOfNotNull(rest, "${shortType(d.type)} · ${rangeText(d.diameterMin, d.diameterMax)}").joinToString("\n"),
                            i == selected,
                            { onPick(i) },
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun shortType(type: String): String = when (type) {
    "solid-carbide" -> "솔리드 초경"
    "exchangeable-head" -> "헤드 교환식"
    "indexable" -> "인서트"
    else -> type
}

/** ③ 피삭재: ISO 군 여섯 칸(이 시리즈 표에 없는 군은 흐리게) → 그 군의 재질 행 */
@Composable
private fun MaterialCard(s: CuttingState, series: DrillSeries, onToast: (String) -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("③ 피삭재", "ISO 군 → 카탈로그 표의 재질 행")
        val enabled = series.conditions.map { it.iso }.toSet()
        IsoChips(s.iso, enabled, { s.pickIso(it) }) { g -> onToast("이 공구 표에는 ${g.name} 군(${isoTitle(g)}) 행이 없습니다") }
        Spacer(Modifier.height(10.dp))
        val g = s.iso
        if (g == null || g !in enabled) {
            Text(keepWords("군을 고르면 이 공구 표의 재질 행이 보입니다."), style = AppType.caption, color = c.textMuted)
            return@AppCard
        }
        val rows = series.conditions.withIndex().filter { it.value.iso == g }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IsoBadge(g, 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(keepWords("${isoTitle(g)} — ${rows.size}행"), color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { (i, cond) ->
            Rule()
            ChoiceRow(keepWords(materialKo(cond)), rowSub(series, cond), i == s.row) { s.pickRow(i) }
        }
    }
}

/** ④ 직경 · 절삭 조건(직접 입력이면 ②): 칸 → 경고 → 표 → 출처 · 각주 */
@Composable
private fun ResultCard(s: CuttingState, r: DrillResult?) {
    val c = AppTheme.colors
    val series = s.drillSeries
    val cond = s.condition
    val bad = s.drillBad(r)
    val preview = drillPreview(r) ?: r?.let { res ->
        res.warnings.firstOrNull { it.stops && !it.missing }?.let { "계산 멈춤 — ${firstSentence(drillWarningText(it, s, res))}" }
    }

    @Composable
    fun field(b: CutBox, modifier: Modifier, placeholder: String = "", hint: String? = null) = NumberField(
        b.label, s.value(b), { s.set(b, it) }, modifier,
        unit = b.unit, placeholder = placeholder, error = b in bad, hint = hint, id = b.key, preview = preview,
    )

    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader(if (s.manual) "② 직경 · 절삭 조건" else "④ 직경 · 절삭 조건", "n = 1000 × Vc / (π × D) · vf = f × n")
        if (s.manual) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                    field(CutBox.DRILL_D, Modifier.weight(1f))
                    field(CutBox.DRILL_VC, Modifier.weight(1f), hint = "공구 카탈로그의 절삭 속도")
                }
                field(CutBox.DRILL_F, Modifier.fillMaxWidth(), hint = "공구 카탈로그의 회전당 이송(비우면 회전수만)")
            }
        } else if (series != null && cond != null) {
            val range = rangeText(DrillCatalog.rowRange(series, cond))
            field(
                CutBox.DRILL_D, Modifier.fillMaxWidth(), placeholder = range,
                hint = "이 행 $range mm · 카탈로그 주문 범위 ${rangeText(series.diameterMin, series.diameterMax)} mm",
            )
        }
        if (r == null) return@AppCard

        // 경고: 멈춤(빨강, 다른 직경 행으로 가는 단추) · 숫자와 함께(노랑) · 안내(파랑)
        r.warnings.filter { !it.missing }.forEach { w ->
            Spacer(Modifier.height(10.dp))
            val level = when {
                w.stops -> WarnLevel.Error
                w == DrillWarning.MISSING_FEED -> WarnLevel.Info
                else -> WarnLevel.Warn
            }
            val actions = if (w == DrillWarning.OUTSIDE_ROW && series != null) {
                r.alternatives.map { i -> "${rangeText(DrillCatalog.rowRange(series, series.conditions[i]))} 행으로" to { s.pickRow(i) } }
            } else emptyList()
            WarningBanner(drillWarningText(w, s, r), level = level, actions = actions)
        }
        if (r.warnings.any { it.stops }) return@AppCard

        Spacer(Modifier.height(14.dp))
        DrillTable(drillLines(r))
        Spacer(Modifier.height(8.dp))
        val notes = buildList {
            r.band?.let { add("이송은 표의 ${bandText(it)} 값 그대로입니다(보간 없음).") }
            if (r.nFromTable) add("이 표는 직경마다 회전수를 줍니다 — Vc 는 그 회전수에서 환산한 값입니다.")
            if (!s.manual && r.vc != null && r.vc?.rec == null && !r.nFromTable) add("이 표는 권장값 없이 범위만 줍니다(가운데 값을 만들지 않음).")
            if (!s.manual) add("깊이별 이송 감소 · 급유 각주는 계산에 넣지 않았습니다 — 위 공구 카드의 깊이 · 급유 글을 보고 줄이세요.")
        }
        notes.forEach { Text(keepWords(it), style = AppType.caption, color = c.textMuted) }
        if (cond != null) {
            Spacer(Modifier.height(10.dp))
            Rule()
            Spacer(Modifier.height(8.dp))
            Text(
                keepWords("카탈로그 행: ${cond.isoGroup} · ${materialEn(cond)}" + (cond.hardness?.let { " · $it" } ?: "")),
                color = c.textBody,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        sourceText(r)?.let {
            Text(keepWords("출처: $it"), Modifier.padding(top = 4.dp), color = c.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        s.drillMaker?.let { m ->
            ExpandableHint("${m.label} 카탈로그 각주 · 확인 기록", m.catalog.notes, Modifier.padding(top = 4.dp))
        }
    }
}

/** 값 부족 경고(멈춤이지만 빨강이 아니라 위 파랑 안내로 보인다) */
private val DrillWarning.missing: Boolean get() = this == DrillWarning.MISSING_DIAMETER || this == DrillWarning.MISSING_VC

/** 결과 띠 · 키패드 한 줄의 멈춤 글: 경고 글의 첫 문장(' — ' 앞) */
private fun firstSentence(t: String): String = t.substringBefore(" — ")

/** 빨간 테를 두를 칸(계산기의 BAD_INPUT 과 같은 규칙: 숫자가 아님 · 0 이하 · 한도 밖) */
internal fun CuttingState.drillBad(r: DrillResult?): Set<CutBox> {
    if (r == null || DrillWarning.BAD_INPUT !in r.warnings) return emptySet()
    val boxes = if (manual) listOf(CutBox.DRILL_D, CutBox.DRILL_VC, CutBox.DRILL_F) else listOf(CutBox.DRILL_D)
    return boxes.filter { b ->
        val v = num(b) ?: return@filter false
        val max = if (b == CutBox.DRILL_VC) Cutting.MAX_SPEED else MAX_MM
        !(v.isFinite() && v > 0 && v <= max)
    }.toSet()
}
