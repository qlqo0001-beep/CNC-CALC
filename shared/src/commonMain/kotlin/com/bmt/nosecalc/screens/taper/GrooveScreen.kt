package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.GrooveResult
import com.bmt.nosecalc.calc.GrooveSolution
import com.bmt.nosecalc.calc.GrooveWarning
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.Arrow
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.LabeledValue
import com.bmt.nosecalc.ui.LegendGlyph
import com.bmt.nosecalc.ui.LegendItem
import com.bmt.nosecalc.ui.MarkKind
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.ShapeViewerCard
import com.bmt.nosecalc.ui.ViewerFill
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.ViewerStock
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.math.max

/**
 * B 화면 — Outer Groove: 그림 요약 → (침범 경고 · 값을 넣으라는 안내) → 기준 인선(TYPE 1 왼쪽 팁 3번 / TYPE 2 오른쪽 팁 4번) · 치수 →
 * 다단 진입 표 → 형상 → NC.
 * 벽·바닥이 X·Z 축에 평행해 노즈R 보정은 0 이다(calc-design.md 홈 계산). 치수 칸은 한 묶음(키패드 "다음"),
 * 다단 진입 표가 화면 밖이면 아래 결과 띠에 첫 단 · 끝 단 좌표가 남는다
 */
@Composable
internal fun GrooveScreen(s: TaperState) {
    val c = AppTheme.colors
    val type = s.type ?: return
    val ref = if (type == 1) GrooveRef.LEFT else GrooveRef.RIGHT
    fun v(name: String) = s.shared(name)
    val input = GrooveInput(
        zStart = v("zStart").toDoubleOrNull(),
        width = v("width").toDoubleOrNull(),
        od = v("od").toDoubleOrNull(),
        bottom = v("bottom").toDoubleOrNull(),
        insert = v("insert").toDoubleOrNull(),
        overlap = v("overlap").toDoubleOrNull() ?: 0.5,
        ref = ref,
        xClear = v("xClear").toDoubleOrNull() ?: 0.0,
    )
    val result = remember(input) { GrooveCalc.calc(input) }
    val sol = result.solution
    // 폭 · 외경이 아직 0 이면 빨간 멈춤 대신 파란 안내. 멈춘 경고는 그 칸들을 빨간 테로 짚는다
    val waiting = sol == null && (GrooveWarning.MISSING_INPUT in result.warnings || input.waiting)
    val flagged = if (waiting) emptySet() else result.warnings.flatMap { grooveWarningFields(it, input) }.toSet()
    val dock = grooveDock(result, waiting, c)
    val preview = dock?.oneLine()
    val chain = rememberFieldChain(s.editing)
    // 키패드 그림: 요약 칸 그림에서 그 치수만 짚는다(겹침 · X 여유는 그림에 없다)
    fun figure(key: String): (@Composable (Modifier) -> Unit)? = if (key == "overlap" || key == "xClear") null else { m ->
        TypeArt(TaperKind.GROOVE, type, false, m.aspectRatio(199f / 184f), compact = true, focus = key)
    }
    @Composable
    fun Field(name: String, label: String, modifier: Modifier, placeholder: String = "", tag: String? = null, hint: String? = null) {
        NumberField(
            label, s.shared(name), { s.setShared(name, it) }, modifier,
            unit = "mm", placeholder = placeholder, error = name in flagged, tag = tag, hint = hint,
            id = name, figure = figure(name), preview = preview,
        )
    }

    TaperPage(dock, chain.sheetOpen) { anchor ->
        SummaryCard(TaperKind.GROOVE, type, false, "기준 인선 팁 ${ref.tip}번 · 노즈R 보정 0") { s.open = false }
        // 멈춘 까닭은 입력 카드 위에(폰 첫 화면 안에)
        if (waiting) {
            WarningBanner(grooveWarningText(GrooveWarning.MISSING_INPUT), level = WarnLevel.Info)
        } else if (result.warnings.isNotEmpty()) {
            Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                result.warnings.forEach { WarningBanner(grooveWarningText(it), level = WarnLevel.Error) }
            }
        }

        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            FieldLabel("기준 인선 (프로그램 기준 모서리)")
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("왼쪽 인선 (팁 3번)", "오른쪽 인선 (팁 4번)"), type - 1, { s.type = it + 1 }, color = c.teal, height = 46.dp, fontSize = 15)
            Spacer(Modifier.height(14.dp))
            // 후퇴 X(직경) = 외경 + 2 × X 여유(반경) — X 여유 칸 키패드에 지금 값으로 보인다
            val retract = input.od?.takeIf { it > 0 }?.let { " · 지금 후퇴 Ø" + Fmt.nc(it + 2 * input.xClear).removeSuffix(".") }.orEmpty()
            FieldChainHost(chain) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Field("zStart", "시작 Z (오른쪽 벽)", Modifier.weight(1f))
                        Field("width", "홈 폭 W", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Field("od", "외경 ØD", Modifier.weight(1f), tag = "Ø직경")
                        Field("bottom", "바닥 Ød", Modifier.weight(1f), tag = "Ø직경")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Field("insert", "인서트 폭 T", Modifier.weight(1f))
                        Field("overlap", "단 겹침", Modifier.weight(1f), placeholder = "0.5")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Field("xClear", "X 여유", Modifier.weight(1f), placeholder = "기본 0", tag = "반경", hint = "반경값 — 한쪽 거리 · 0 이면 외경까지$retract")
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            ExpandableHint(
                "피치 = 인서트 폭 T − 겹침. 마지막 단은 왼쪽 벽에 맞춥니다.",
                "피치 = T − 겹침. 첫 단은 날 오른쪽을 오른쪽 벽에, 마지막 단은 날 왼쪽을 왼쪽 벽에 정확히 맞춥니다. " +
                    "단마다 외경 + 2×X 여유로 후퇴합니다(X 여유 0 이면 외경까지).",
                Modifier.padding(top = 4.dp),
            )
        }

        if (sol != null) {
            val t = input.insert!!
            StepsCard(sol, ref, anchor)
            GrooveViewer(sol, input, t)
            NcCard(
                sol.nc,
                "단마다 G00 후퇴 → G01 바닥 → G00 후퇴, 끝에 벽·바닥 정삭. 기준 인선 좌표 그대로(G40) 깎습니다 — 벽·바닥이 X·Z 축에 평행해 " +
                    "노즈R 보정량이 0 이고, 반대쪽 벽은 다른 날 모서리가 깎아 공구 보정 하나(G41/G42)로는 맡을 수 없어 G41/G42 프로그램은 따로 없습니다. " +
                    "이송 F 는 넣지 않았습니다.",
                programName = "홈 가공 프로그램",
                facts = listOf(NcFact("기준 인선 팁 ${ref.tip}번 · 노즈R 보정 0"), NcFact("이송 F 없음")),
            )
        }
        if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
    }
}

/** 결과 띠 · 키패드 결과 한 줄: 풀리면 첫 단 · 끝 단의 진입 Z · 목표 X(기준 인선), 멈췄으면 첫 경고의 첫 문장. 값이 모자라는 동안은 null */
private fun grooveDock(result: GrooveResult, waiting: Boolean, c: AppColors): DockStatus? {
    val sol = result.solution ?: return if (waiting) null else result.warnings.firstOrNull()?.let { DockStatus.Error(firstSentence(grooveWarningText(it))) }
    val ends = listOf(0, sol.steps.lastIndex).distinct()
    return DockStatus.Ok(ends.map { i -> sol.steps[i].let { DockRow("${i + 1}단", Fmt.fixed3(it.xDia), Fmt.fixed3(it.z), c.textStrong) } })
}

/** 다단 진입 표: 단 수·피치·후퇴 X, 단마다 진입 Z·목표 X, 정삭 경로 */
@Composable
private fun StepsCard(sol: GrooveSolution, ref: GrooveRef, modifier: Modifier) {
    val c = AppTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }
    val n = sol.steps.size
    val side = if (ref == GrooveRef.LEFT) "왼쪽" else "오른쪽"
    val head = AppType.caption.copy(fontWeight = FontWeight.SemiBold)
    AppCard(modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("다단 진입", "$side 인선 팁 ${ref.tip}번 기준 좌표")
        Row {
            val size = AppType.numLarge.fontSize
            LabeledValue("단 수", "$n", Modifier.weight(0.8f), color = c.valueComp, size = size)
            LabeledValue("피치", Fmt.fixed3(sol.pitch), Modifier.weight(1f), color = c.valueComp, size = size)
            LabeledValue("후퇴 X (직경)", Fmt.fixed3(sol.retractXDia), Modifier.weight(1.1f), color = c.valueComp, size = size)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().background(c.field).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("단", Modifier.width(34.dp), style = head, color = c.textMuted)
            Text("진입 Z", Modifier.weight(1f), style = head, color = c.textMuted, textAlign = TextAlign.End)
            Text("목표 X", Modifier.weight(1f), style = head, color = c.textMuted, textAlign = TextAlign.End)
            Text("", Modifier.width(78.dp))
        }
        val rows = if (all || n <= SHOWN) sol.steps.indices.toList() else (0 until SHOWN - 1) + (n - 1)
        rows.forEachIndexed { k, i ->
            if (k > 0 && rows[k - 1] != i - 1) {
                Text(
                    "… ${i - rows[k - 1] - 1}단 더 보기",
                    Modifier.fillMaxWidth().clickable { all = true }.padding(vertical = 6.dp),
                    style = AppType.label, color = c.teal, textAlign = TextAlign.Center,
                )
            }
            val st = sol.steps[i]
            val note = when {
                n == 1 -> "한 번에"
                i == 0 -> "오른쪽 벽"
                i == n - 1 -> "왼쪽 벽 맞춤"
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(34.dp), style = AppType.label, color = c.textBody)
                Mono(Fmt.fixed3(st.z), Modifier.weight(1f))
                Mono(Fmt.fixed3(st.xDia), Modifier.weight(1f))
                Text(note, Modifier.width(78.dp), style = head, color = c.tealDeep, textAlign = TextAlign.End, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
        }
        Spacer(Modifier.height(12.dp))
        SmallLabel("벽·바닥 정삭 (기준 인선)")
        val f = listOf(sol.finish.segs.first().start) + sol.finish.segs.map { it.end }
        val names = listOf("오른쪽 벽 위", "바닥", "왼쪽 벽", "위로")
        f.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(names[i], Modifier.width(92.dp), style = AppType.body, color = c.textBody)
                Text("X${Fmt.fixed3(p.xDia)}  Z${Fmt.fixed3(p.z)}", style = AppType.numBody, color = c.textValue)
            }
        }
        Text(
            "벽·바닥이 X·Z 축에 평행해 노즈R 보정은 0 입니다. 바닥 두 모서리에는 인서트 노즈R 만큼 R 이 남습니다. " +
                "인서트 $side 모서리에 공구 길이를 맞추고 팁 번호는 ${ref.tip}번으로 둡니다.",
            Modifier.padding(top = 8.dp),
            style = AppType.caption,
            color = c.textMuted,
        )
    }
}

private const val SHOWN = 12

@Composable
private fun Mono(text: String, modifier: Modifier) {
    Text(text, modifier, style = AppType.numBody, color = AppTheme.colors.textValue, textAlign = TextAlign.End)
}

/**
 * 외경 + 홈 도면선 · 소재(깎을 부분은 옅은 노랑 면) · 첫 단의 날(폭 T, 후퇴 X 위 2 mm 까지, 기준 인선에 '기준') · 다른 단 자리(옅은 네모) ·
 * 정삭 경로(기준 인선, 채운 화살). 단 이름은 후퇴 X 위에 — 맞춤 보기에서 단 사이가 22dp 넘으면 단마다 이름과 진입(빈 화살,
 * 첫 · 끝 단은 정삭 경로와 겹쳐 뺀다), 아니면 처음 · 끝 이름만
 */
@Composable
private fun GrooveViewer(sol: GrooveSolution, i: GrooveInput, t: Double) {
    val c = AppTheme.colors
    val z0 = i.zStart!!
    val w = i.width!!
    val od = i.od!!
    val d = i.bottom!!
    val ext = max(2.0, w * 0.4)
    val off = if (i.ref == GrooveRef.LEFT) 0.0 else t
    val steps = sol.steps
    val up = sol.retractXDia / 2
    // 날 자리(반경 x0 ~ x1) — st.z 는 기준 인선 Z 라 날 왼쪽은 st.z − off
    fun blade(z: Double, x0: Double, x1: Double) = (z - off).let { l -> Contour.polyline(Pt(l, x0), Pt(l + t, x0), Pt(l + t, x1), Pt(l, x1), Pt(l, x0)) }
    val profile = Contour.polyline(
        Pt.ofDia(z0 + ext, od), Pt.ofDia(z0, od), Pt.ofDia(z0, d),
        Pt.ofDia(z0 - w, d), Pt.ofDia(z0 - w, od), Pt.ofDia(z0 - w - ext, od),
    )
    val first = steps.first()
    // 맞춤 보기의 배율(dp/mm) 어림: 폰 뷰어에서 그림이 약 300dp 에 들어간다
    val dpPerMm = 300 / max(w + 2 * ext, up + 2 - d / 2)
    val roomy = sol.pitch * dpPerMm >= 22
    val lines = buildList {
        add(ViewerLine(profile, c.drawProfile, 3.5.dp))
        if (steps.size <= 40) {
            steps.drop(1).forEach { add(ViewerLine(blade(it.z, it.xDia / 2, up), c.drawT2.copy(alpha = .45f), 1.dp)) }
        }
        add(ViewerLine(blade(first.z, first.xDia / 2, up + 2), c.drawToolLine, 1.2.dp, fill = c.drawInsert.copy(alpha = if (c.dark) .7f else .85f)))
        if (roomy) {
            steps.drop(1).dropLast(1).forEach { add(ViewerLine(Contour.polyline(Pt(it.z, up), Pt.ofDia(it.z, it.xDia)), c.viewPath, 1.2.dp, arrows = Arrow.Hollow)) }
        }
        add(ViewerLine(sol.finish, c.viewPath, 1.8.dp, arrows = Arrow.Filled))
    }
    val named = if (roomy) steps.indices.toList() else listOf(0, steps.lastIndex).distinct()
    val marks = buildList {
        add(ViewerMark(Pt.ofDia(first.z, first.xDia), c.viewLabelT1, "기준", MarkKind.Dot))
        named.forEach { k -> add(ViewerMark(Pt(steps[k].z, up), c.viewLabelT2, "${k + 1}단", MarkKind.Dot)) }
    }
    ShapeViewerCard(
        lines, marks,
        listOf(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.drawInsert.copy(alpha = .85f), "날 (폭 T)", LegendGlyph.Fill),
            // 테만 있는 네모: 면은 투명, 테는 같은 색(면 그림은 테를 불투명으로 긋는다)
            LegendItem(c.drawT2.copy(alpha = 0f), "단 위치", LegendGlyph.Fill),
            LegendItem(c.viewPath, "프로그램 경로 (기준 인선)"),
            LegendItem(c.amber.copy(alpha = .16f), "깎을 부분", LegendGlyph.Fill),
            LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
        ),
        subtitle = "${if (i.ref == GrooveRef.LEFT) "왼쪽" else "오른쪽"} 인선 팁 ${i.ref.tip}번 기준",
        stock = ViewerStock(profile, inner = false, extendRight = true),
        fills = listOf(ViewerFill(Contour.polyline(Pt.ofDia(z0 - w, d), Pt.ofDia(z0, d), Pt.ofDia(z0, od), Pt.ofDia(z0 - w, od), Pt.ofDia(z0 - w, d)), c.amber.copy(alpha = .16f))),
    )
}
