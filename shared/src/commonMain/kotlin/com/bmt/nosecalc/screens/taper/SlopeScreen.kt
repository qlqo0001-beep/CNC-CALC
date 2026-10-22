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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.CompMode
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.TaperPoint
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.Arrow
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.CoordPair
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.LabeledValue
import com.bmt.nosecalc.ui.LegendGlyph
import com.bmt.nosecalc.ui.LegendItem
import com.bmt.nosecalc.ui.MarkKind
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.ShapeViewerCard
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.ViewerStock
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space

/**
 * B 화면 — 테이퍼(외경·내경 · 2중 테이퍼): 그림 요약 → (멈춘 까닭 · 값을 넣으라는 안내) → 입력(도면 치수 · 공구) → 보정 방식 스위치 → 경고 → 결과 카드 → 형상 → NC.
 * 결과 카드 · 형상 · NC 는 앱 전체 보정 방식(기계 보정 G41/G42 · 직접 보정 G40)을 따른다.
 * 값이 바뀌면 바로 다시 푼다(계산 버튼 없음). 입력 칸은 한 묶음이라 키패드의 "다음" 으로 이어 넣고, 키패드에는 그 칸만 짚은 그림과 결과 한 줄이 뜬다.
 * 결과 카드가 화면 밖이면 아래 결과 띠에 T 점 좌표(멈췄으면 그 까닭)가 남는다
 */
@Composable
internal fun SlopeScreen(s: TaperState) {
    val c = AppTheme.colors
    val kind = s.kind
    val type = s.type ?: return
    val n = kind.points
    val xs = (1..n).map { s.point("x$it") }
    val zs = (1..n).map { s.point("z$it") }
    val rs = (1..n).map { s.point("r$it") }
    val ang = s.point("ang")
    val start = s.point("start")
    val end = s.point("end")
    val noseR = s.shared("noseR")
    val zClear = s.shared("zClear")
    val standard = NoseComp.standardTip(kind.inner, s.reverse)
    val form = SlopeForm(
        kind, type, s.reverse,
        noseR.toDoubleOrNull() ?: Double.NaN,
        s.tip,
        xs.map { it.toDoubleOrNull() },
        zs.map { it.toDoubleOrNull() },
        ang.toDoubleOrNull(),
        zClear.toDoubleOrNull() ?: 0.0,
        rs.map { it.toDoubleOrNull() ?: 0.0 },
        start.toDoubleOrNull(),
        end.toDoubleOrNull(),
    )
    val result = remember(form) { TaperSolve.solve(form) }
    val sol = result.solution
    val tip = s.tip ?: standard
    val (frontV, backV) = legsVertical(kind, type)
    // 치수를 아직 다 넣지 않았으면(0 · 빈칸) 빨간 멈춤 대신 파란 안내. 멈춘 경고는 그 칸들을 빨간 테로 짚는다
    val waiting = sol == null && (TaperWarning.MISSING_INPUT in result.warnings || form.waiting)
    val flagged = if (sol == null && !waiting) result.warnings.flatMap { taperWarningFields(it, form) }.toSet() else emptySet()
    fun auto(text: String, v: Double?) = if (waiting) null else autoValue(text, v)
    val r = if (form.noseR.isFinite()) Fmt.nc(form.noseR).removeSuffix(".") else "—"
    val direct = LocalAppState.current.compMode == CompMode.DIRECT
    val dock = slopeDock(result, form, waiting, direct, c)
    val preview = dock?.oneLine()
    val chain = rememberFieldChain(s.editing)
    // 키패드 그림: 요약 칸 그림에서 그 칸의 점 · 치수만 짚는다
    fun figure(key: String): @Composable (Modifier) -> Unit = { m ->
        TypeArt(kind, type, s.reverse, m.aspectRatio(199f / 184f), compact = true, focus = key)
    }

    TaperPage(dock, chain.sheetOpen) { anchor ->
        val dir = if (s.reverse) "역방향 가공 (Z+)" else "정방향 가공 (Z−)"
        // 좁은 폰에서 '노즈 R' · '팁 3번' 이 쪼개지지 않게(라운드 toolText 와 같은 붙임 공백 · 낱말 이음)
        SummaryCard(kind, type, s.reverse, "$dir · 노즈\u00A0R$r · 팁\u00A0${tip}\u2060번") { s.open = false }
        // 멈춘 까닭은 입력 카드 위에(폰 첫 화면 안에). 다섯 값이 어긋나면 비울 칸을 단추로 권한다
        if (waiting) {
            WarningBanner(taperWarningText(TaperWarning.MISSING_INPUT, form), level = WarnLevel.Info)
        } else if (sol == null) {
            Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                result.warnings.forEach { w ->
                    val fixes = if (w == TaperWarning.CONTRADICTION) s.contradictionFixes() else emptyList()
                    WarningBanner(
                        taperWarningText(w, form),
                        level = WarnLevel.Error,
                        actions = fixes.map { k -> "${if (k == "ang") "각도" else k.uppercase()} 비우고 계산" to { s.setPoint(k, "") } },
                    )
                }
            }
        }

        // 입력 카드: '도면 치수'(점마다 X · Z · 모서리 R → 각도 → 시작 · 끝, 단위는 머리에 한 번) → '공구'(노즈 R → Z 여유 · 팁 번호)
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            FieldChainHost(chain) {
                CardHeader("도면 치수") { Text("단위 mm · X 는 직경", style = AppType.caption, color = c.textMuted) }
                // 단일 테이퍼는 다섯 값 가운데 하나를 비워 계산한다(키패드 '비우기 · 자동 계산')
                val blankable = !kind.double
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    for (i in 0 until n) {
                        val p = sol?.points?.get(i)?.vertex
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PointBadge("T${i + 1}", pointColor(c, i), Modifier.size(38.dp, 52.dp))
                            NumberField(
                                "X${i + 1}", xs[i], { s.setPoint("x${i + 1}", it) }, Modifier.weight(1f),
                                autoValue = auto(xs[i], p?.xDia), error = "x${i + 1}" in flagged,
                                tag = "Ø직경", hint = "직경값 — 도면 Ø 그대로 (Ø40 → 40)", blankable = blankable,
                                id = "x${i + 1}", figure = figure("x${i + 1}"), preview = preview,
                            )
                            NumberField(
                                "Z${i + 1}", zs[i], { s.setPoint("z${i + 1}", it) }, Modifier.weight(1f),
                                autoValue = auto(zs[i], p?.z), error = "z${i + 1}" in flagged,
                                hint = "Z0 = 부품 오른쪽 끝면 · 척 쪽은 − (예: −5)", blankable = blankable,
                                id = "z${i + 1}", figure = figure("z${i + 1}"), preview = preview,
                            )
                            // 점 이름은 줄 앞 표에 있으니 키패드 안내 줄에 적는다
                            NumberField(
                                "모서리 R", rs[i], { s.setPoint("r${i + 1}", it) }, Modifier.weight(.8f),
                                placeholder = "기본 0", error = "r${i + 1}" in flagged, hint = "T${i + 1} 모서리 R · 비우면 0 (날카로운 꼭짓점)",
                                id = "r${i + 1}", figure = figure("r${i + 1}"), preview = preview,
                            )
                        }
                    }
                    if (!kind.double) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            NumberField(
                                "각도 (Z축 기준)", ang, { s.setPoint("ang", it) }, Modifier.weight(1f),
                                unit = "°", autoValue = auto(ang, sol?.anglesDeg?.first()), error = "ang" in flagged,
                                hint = "Z축(가로선)에서 잰 한쪽 각 · 도면이 꼭지각이면 ÷2 · X축 기준이면 90 − 값", blankable = true,
                                id = "ang", figure = figure("ang"), preview = preview,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    // 시작점·끝점: 그 요소가 단면이면 X(직경), 수평이면 Z. 넣었을 때만 그 요소를 깎는다.
                    // 끝점을 비우면 마지막 T 점(모서리 R 이면 그 끝)에서 끝나 그 좌표를 자동 값으로, 시작점을 비우면 "안 깎음"
                    val last = sol?.cutEnd
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        // 외경 단면은 중심 쪽에서 바깥으로 깎아 시작이 안쪽 Ø(내경은 바깥 Ø). 역방향은 시작(앞 끝)이 척 쪽, 끝이 척 반대쪽
                        NumberField(
                            when {
                                frontV -> if (kind.inner) "시작 X (단면 바깥 Ø)" else "시작 X (단면 안쪽 Ø)"
                                s.reverse -> "시작 Z (앞 끝 · 척 쪽)"
                                else -> "시작 Z (앞 끝)"
                            },
                            start, { s.setPoint("start", it) }, Modifier.weight(1f),
                            placeholder = "안 깎음", error = "start" in flagged,
                            hint = "T1 앞 요소가 시작하는 좌표 · 비우면 그 요소는 안 깎고 연장선에서 진입",
                            id = "start", figure = figure("start"), preview = preview,
                        )
                        NumberField(
                            when {
                                backV -> "끝 X (단면 끝 Ø)"
                                s.reverse -> "끝 Z (뒤 끝 · 척 반대쪽)"
                                else -> "끝 Z (뒤 끝 · 척 쪽)"
                            },
                            end, { s.setPoint("end", it) }, Modifier.weight(1f),
                            autoValue = auto(end, last?.let { if (backV) it.xDia else it.z }), error = "end" in flagged,
                            hint = (if (s.reverse) "T$n 뒤 요소의 실제 끝 좌표" else "척·턱 쪽 실제 끝 좌표") + " · 비우면 T$n 에서 끝남 (자동 값)",
                            id = "end", figure = figure("end"), preview = preview,
                        )
                    }
                }
                ExpandableHint(
                    if (kind.double) "세 점의 X · Z 를 모두 넣습니다. 각도는 결과로 나옵니다."
                    else "빈칸 하나는 나머지 넷으로 자동 계산합니다 (보통 각도를 비움).",
                    (
                        if (kind.double) ""
                        else "X1·Z1·X2·Z2·각도 가운데 하나를 비우면(키패드의 '비우기 · 자동 계산') 나머지 넷으로 계산합니다. 보통 각도를 비워 둡니다. "
                        ) +
                        "모서리 R 은 비우면 0(날카로운 꼭짓점). 시작점·끝점은 앞뒤 요소의 바깥 끝 좌표(단면이면 X, 수평이면 Z)로, 넣었을 때만 그 요소를 깎습니다 — " +
                        "끝점을 비우면 마지막 T 점에서 끝나고, 넣으면 인선이 그 좌표까지 갑니다. 시작점을 비우면 첫 T 점에서 시작합니다.",
                    Modifier.padding(top = 4.dp),
                )
                GroupDivider()
                CardHeader("공구")
                NoseRPicker(noseR, error = "noseR" in flagged) { s.setShared("noseR", it) }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        NumberField(
                            "Z 여유 (진입)", zClear, { s.setShared("zClear", it) }, Modifier.weight(1f), unit = "mm", placeholder = "기본 0",
                            error = "zClear" in flagged, hint = "진입점까지 Z 거리 (+ 값) · 노즈 R 보다 작으면 노즈 R 만큼", id = "zClear", preview = preview,
                        )
                        TipField(tip, standard, { s.tip = it }, Modifier.weight(1f))
                    }
                    // Z 여유가 노즈 R 보다 작으면 진입은 노즈 R 만큼 띄운다(G41/G42 시작 이동 ≥ 노즈 R) — 왜 G00 이 그대로인지 알린다
                    if (sol != null && form.zClear < form.noseR) {
                        WarningBanner("Z 여유가 노즈 R 보다 작아 진입을 노즈 R($r) 만큼 띄웠습니다", level = WarnLevel.Info)
                    }
                }
            }
        }

        if (sol != null) {
            CompModeSwitch(sol.side)
            // 결과 띠가 가리키는 곳: 경고 · 결과 카드
            Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                result.warnings.forEach {
                    if (it == TaperWarning.AGAINST_TOOL) {
                        WarningBanner(taperWarningText(it, form), level = WarnLevel.Info, collapsedText = "바이트 반대쪽 면 있음 (역테이퍼 꼴) — 눌러서 자세히")
                    } else {
                        WarningBanner(taperWarningText(it, form), level = WarnLevel.Warn)
                    }
                }
                if (direct) CompCard(sol) else MachineCard(sol, form.noseR)
            }
            SlopeViewer(sol, form.noseR, r, kind.inner, s.reverse, direct)
            NcCard(
                if (direct) directNc(sol.ncG40, form.noseR, sol.tip) else sol.ncG41G42,
                if (direct) {
                    "앱이 계산한 가상 인선 ${sol.tip}번 좌표입니다 — 기계의 노즈R 보정 없이(G40) 그대로 깎습니다. 이 좌표에 G41/G42 를 또 걸면 " +
                        "노즈R 만큼 두 번 밀립니다(첫 줄 주석이 그 노즈 R · 팁을 적어 둡니다 — 기계는 주석을 건너뜁니다). " +
                        "모서리 원호는 보정 R(볼록 R + r · 오목 R − r), 인선은 끝좌표에 멈춥니다. 이송 F 는 넣지 않았습니다."
                } else {
                    "도면 좌표에 ${sol.side} ${eulReul("${sol.side}")} 걸어 기계가 노즈R 을 보정합니다. " +
                        "기계 공구 보정 화면에 R$r · T${sol.tip} ${eulReul("${sol.tip}")} 넣으세요(첫 줄 주석). " +
                        "보정 시작·취소는 직선 이동에서 합니다. 마지막 줄 끝에서는 노즈 접점이 끝좌표에 와서 인선이 직접 보정보다 더 갈 수 있습니다 " +
                        "(끝 요소가 외경·내경처럼 수평이면 Z 로 노즈R 만큼) — 바로 뒤에 어깨·턱이 있으면 위 스위치에서 직접 보정을 고르세요. 이송 F 는 넣지 않았습니다."
                },
                programName = if (direct) "직접 보정(G40) 프로그램" else "기계 보정(G41/G42) 프로그램",
                facts = if (direct) {
                    listOf(NcFact("G41/G42 걸지 말 것 (이중 보정)", WarnLevel.Error), NcFact("공구 보정 R 안 씀"), NcFact("이송 F 없음"))
                } else {
                    listOf(NcFact("공구 보정 화면: R $r · T ${sol.tip} 입력", WarnLevel.Warn), NcFact("끝점에서 노즈R 만큼 더 갈 수 있음"), NcFact("이송 F 없음"))
                },
            )
        }
        if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
    }
}

/**
 * 결과 띠 · 키패드 결과 한 줄: 풀리면 T 점마다 첫 점(직접 보정 = 보정점 T1′ …, 기계 보정 = 도면 점 T1 …)과 노란 경고 수,
 * 멈췄으면 첫 경고의 첫 문장. 값이 모자라는 동안(파란 안내)은 null — 띠도 한 줄도 없다
 */
private fun slopeDock(result: TaperResult, form: SlopeForm, waiting: Boolean, direct: Boolean, c: AppColors): DockStatus? {
    val sol = result.solution ?: return if (waiting) null else result.warnings.firstOrNull()?.let { DockStatus.Error(firstSentence(taperWarningText(it, form))) }
    val rows = sol.points.mapIndexed { i, p ->
        val n = p.nodes.first()
        if (direct) {
            DockRow("T${i + 1}′", Fmt.fixed3(n.prog.xDia), Fmt.fixed3(n.prog.z), pointColor(c, i))
        } else {
            DockRow("T${i + 1}", Fmt.fixed3(n.drawn.xDia), Fmt.fixed3(n.drawn.z), pointColor(c, i), c.textValue)
        }
    }
    // 바이트 반대쪽 면(파란 접힌 띠)은 세지 않는다
    val warn = result.warnings.count { it != TaperWarning.AGAINST_TOOL }
    return if (warn > 0) DockStatus.Warn(warn, rows) else DockStatus.Ok(rows)
}

/** 끝 숫자를 읽은 소리로 목적격 조사(영 · 일 · 삼 · 육 · 칠 · 팔 = 받침 → 을, 이 · 사 · 오 · 구 → 를). G41 을 · G42 를 · T3 을 · T2 를 */
private fun eulReul(s: String): String = if (s.last() in "013678") "을" else "를"

/** 빈칸이면 계산된 값(칸에 청록 글로, "자동" 표는 라벨 줄에). 못 풀면 "" */
private fun autoValue(text: String, v: Double?): String? = if (text.isNotEmpty()) null else v?.let { Fmt.fixed3(it) } ?: ""

/**
 * 직접 보정 결과 — 보정 좌표(가상 인선 = 직접 보정 프로그램 좌표): 점마다 X | Z 좌표 쌍 + "ΔX(직경) · ΔZ" 줄.
 * 모서리 R 이 있는 점은 R 시작·끝 접점 둘(도면 좌표를 아래에)과 원호 방향·도면 R·보정 R. 아래 각도·G00 진입점
 */
@Composable
private fun CompCard(sol: TaperSolution) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("보정 좌표", "직접 보정(G40) 프로그램 좌표 · 팁 ${sol.tip}번") {
            Text("단위 mm · X 직경", style = AppType.caption, color = c.textMuted)
        }
        sol.points.forEachIndexed { i, p ->
            Rule()
            Row(verticalAlignment = if (p.corner == null) Alignment.CenterVertically else Alignment.Top) {
                PointBadge("T${i + 1}′", pointColor(c, i), Modifier.size(44.dp, 40.dp))
                Spacer(Modifier.width(12.dp))
                PointRows(p, Modifier.weight(1f), drawn = false, c.valueComp)
            }
        }
        Rule()
        Angles(sol, entry = true)
    }
}

/**
 * 기계 보정 결과(G41/G42 프로그램): 공구 보정 화면에 넣을 R · T(크게) → 점마다 도면 좌표(모서리 R 은 접점 둘 · 원호 방향 · 도면 R) → 각도.
 * 진입점은 G41/G42 프로그램(NC 의 첫 G00)에만 있어 따로 쓰지 않는다. 보정 좌표는 '직접 보정 좌표도 보기' 를 눌러야 흐리게 나온다 —
 * 그 좌표를 G41/G42 프로그램에 넣으면 노즈R 이 두 번 들어가서
 */
@Composable
private fun MachineCard(sol: TaperSolution, noseR: Double) {
    val c = AppTheme.colors
    var more by rememberSaveable { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("기계 보정", "공구 보정 화면에 넣을 값 · 도면 좌표") {
            Text("단위 mm · X 직경", style = AppType.caption, color = c.textMuted)
        }
        // R · T 는 기계 공구 보정 화면의 칸 이름 그대로(이름은 한글 글꼴로 작게, 값은 고정폭)
        Text(
            buildAnnotatedString {
                listOf("R" to Fmt.fixed3(noseR), "T" to "${sol.tip}").forEachIndexed { i, (name, v) ->
                    withStyle(SpanStyle(fontFamily = AppTheme.sans, fontSize = 15.sp, color = c.textMuted)) { append(if (i > 0) "   ·   $name " else "$name ") }
                    append(v)
                }
            },
            style = AppType.numHero,
            color = c.textValue,
        )
        // 'R 칸' 이 줄 끝에서 쪼개지지 않게 붙임 공백(\u00A0) · 낱말 이음(\u2060)
        Text("노즈 R · 팁 번호 — 기계 공구 보정 화면의 R\u00A0칸 · T\u00A0칸\u2060에 넣습니다", Modifier.padding(top = 2.dp), style = AppType.caption, color = c.textMuted)
        sol.points.forEachIndexed { i, p ->
            Rule()
            Row(verticalAlignment = if (p.corner == null) Alignment.CenterVertically else Alignment.Top) {
                PointBadge("T${i + 1}", pointColor(c, i), Modifier.size(44.dp, 40.dp))
                Spacer(Modifier.width(12.dp))
                PointRows(p, Modifier.weight(1f), drawn = true, c.textValue, AppType.numLarge.fontSize)
            }
        }
        Rule()
        Angles(sol, entry = false)
        Rule()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable { more = !more },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (more) "직접 보정 좌표 접기" else "직접 보정 좌표도 보기", Modifier.weight(1f), color = c.tealDeep, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Icon(AppIcons.ChevronDown, null, Modifier.size(16.dp).rotate(if (more) 180f else 0f), tint = c.tealDeep)
        }
        if (more) {
            Text("직접 보정(G40)용 보정 좌표 — G41/G42 프로그램에 넣으면 노즈R 이 두 번 들어갑니다.", style = AppType.caption, color = c.textMuted)
            sol.points.forEachIndexed { i, p ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = if (p.corner == null) Alignment.CenterVertically else Alignment.Top) {
                    PointBadge("T${i + 1}′", c.textMuted, Modifier.size(44.dp, 40.dp))
                    Spacer(Modifier.width(12.dp))
                    PointRows(p, Modifier.weight(1f), drawn = false, c.textMuted, AppType.numBody.fontSize)
                }
            }
        }
    }
}

/** 각도(2중 테이퍼는 경사 둘 — 그림의 각1 = T1–T2 · 각2 = T2–T3)와 entry 면 G00 진입점(단일은 각도 옆, 2중은 아랫줄) */
@Composable
private fun Angles(sol: TaperSolution, entry: Boolean) {
    val size = AppType.numLarge.fontSize
    val at = "X${Fmt.fixed3(sol.entry.xDia)}  Z${Fmt.fixed3(sol.entry.z)}"
    val angles = sol.anglesDeg
    Row {
        angles.forEachIndexed { i, a ->
            LabeledValue(if (angles.size == 1) "각도 (Z축 기준)" else "각${i + 1} (Z축 기준)", "${Fmt.fixed3(a)}°", Modifier.weight(1f), size = size)
        }
        if (entry && angles.size == 1) LabeledValue("G00 진입점", at, Modifier.weight(1.5f), size = size)
    }
    if (entry && angles.size > 1) LabeledValue("G00 진입점", at, Modifier.padding(top = 10.dp), size = size)
}

@Composable
private fun Rule() {
    Box(Modifier.padding(vertical = 9.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.lineCard))
}

/**
 * 점 하나의 좌표 줄(color · size 글로). drawn = 도면 좌표(기계 보정 프로그램 좌표)만, 아니면 보정 좌표 + 아래 "ΔX(직경) · ΔZ" 줄
 * (모서리 R 이면 그 접점의 도면 좌표). 모서리 R 이 있으면 R 시작·끝 접점 둘과 원호 방향 · 도면 R(보정 좌표면 → 보정 R)
 */
@Composable
private fun PointRows(p: TaperPoint, modifier: Modifier, drawn: Boolean, color: Color, size: TextUnit = AppType.numHero.fontSize) {
    val c = AppTheme.colors
    val corner = p.corner
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        p.nodes.forEachIndexed { k, node ->
            if (k > 0) Spacer(Modifier.height(6.dp))
            if (corner != null) SmallLabel(if (k == 0) "R 시작 (접점)" else "R 끝 (접점)")
            val q = if (drawn) node.drawn else node.prog
            CoordPair(Fmt.fixed3(q.xDia), Fmt.fixed3(q.z), color, size = size)
            when {
                drawn -> {}
                corner == null -> SubLine("ΔX(직경)" to signed(node.dXDia), "ΔZ" to signed(node.dZ))
                else -> SubLine("도면" to "X${Fmt.fixed3(node.drawn.xDia)}  Z${Fmt.fixed3(node.drawn.z)}")
            }
        }
        if (corner != null) {
            // 어절 안에서 줄이 바뀌지 않게(keepWords) — '(R ≤ 노즈R)' 는 붙임 공백으로 한 덩이
            Text(
                keepWords(
                    "${if (corner.cw) "G02 시계" else "G03 반시계"} · 도면 R${Fmt.fixed3(corner.r)}" +
                        if (drawn) "" else " → 보정 R" + (corner.progR?.let { Fmt.fixed3(it) } ?: " 없음 (R\u00A0≤\u00A0노즈R)"),
                ),
                Modifier.padding(top = 2.dp),
                style = AppType.label,
                color = c.simArc,
            )
        }
    }
}

/**
 * 좌표 쌍 아래 작은 줄: 이름(sans) + 값(고정폭) 쌍을 " · " 로 잇는다.
 * 한글 이름까지 고정폭으로 쓰면 줄 가운데서 대체 글꼴로 빠지므로 글꼴을 나눈다
 */
@Composable
private fun SubLine(vararg parts: Pair<String, String>) {
    val sans = AppTheme.sans
    Text(
        buildAnnotatedString {
            parts.forEachIndexed { i, (name, v) ->
                if (i > 0) append("  ·  ")
                withStyle(SpanStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold)) { append(name) }
                append(" $v")
            }
        },
        style = AppType.numSmall,
        color = AppTheme.colors.textMuted,
    )
}

@Composable
internal fun SmallLabel(text: String) {
    Text(text, color = AppTheme.colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * 도면(굵게) 위에 직접 보정(G40) 경로(진입점에서 G01 포함, 채운 화살) · 노즈 중심(끊은 선) · 보정점마다 노즈 원과 가상 인선 꺾쇠 ·
 * G00 후퇴(점선, 빈 화살). 점: T 점(○) · 보정점(●) · 진입 · 후퇴(□) · 모서리 R 중심(+ 도면 R → 보정 R).
 * 소재는 빗금(내경은 구멍 위) — 척 쪽(왼쪽)으로 이어지고, 역방향은 끝 요소가 Z0 쪽(오른쪽)으로도 이어진다.
 * 시작·끝점을 비웠으면 프로그램이 그 요소를 깎지 않으므로(진입점에서 첫 꺾임점′ 으로, 마지막 꺾임점′ 에서 후퇴) 그 토막은 그리지 않는다.
 * direct = false(기계 보정)면 프로그램은 도면 좌표 그대로라 도면의 깎는 토막을 경로로 그리고(모서리 R 은 도면 R),
 * 노즈 원 · 보정점 · 직접 보정의 진입 · 후퇴는 그리지 않는다. rText = 노즈 R 을 쓴 글(범례)
 */
@Composable
private fun SlopeViewer(sol: TaperSolution, r: Double, rText: String, inner: Boolean, reverse: Boolean, direct: Boolean) {
    val c = AppTheme.colors
    val skip = if (sol.fromStart) 0 else 1
    val tail = if (sol.toEnd) 0 else 1
    val program = sol.program.segs.drop(skip).dropLast(tail)
    val tv = NoseComp.tipVector(sol.tip, r)
    val nose = direct && r > 0
    val lines = buildList {
        add(ViewerLine(sol.drawing, c.drawProfile, 3.5.dp))
        if (nose) {
            for (p in sol.points) for (node in p.nodes) {
                val o = node.prog - tv
                add(ViewerLine(circle(o, r), c.viewNose, 1.2.dp, fill = c.viewNose.copy(alpha = .10f)))
                // 가상 인선 꺾쇠: 노즈 원에 Z · X 방향으로 접하는 두 선이 가상 인선(보정점)에서 만난다(팁 1~4)
                if (tv.z != 0.0 && tv.x != 0.0) add(ViewerLine(Contour.polyline(o + Pt(tv.z, 0.0), node.prog, o + Pt(0.0, tv.x)), c.viewNose.copy(alpha = .8f), 1.2.dp))
            }
            add(ViewerLine(Contour(sol.center.segs.drop(skip).dropLast(tail)), c.viewNose, 1.4.dp, dashed = true))
        }
        if (direct) {
            add(ViewerLine(Contour.polyline(program.last().end, sol.exit), c.viewRapid, 2.dp, dotted = true, arrows = Arrow.Hollow))
            // 진입점에서 첫 점까지는 G01 이송
            add(ViewerLine(Contour(listOf(Line(sol.entry, program.first().start)) + program), c.viewPath, 1.8.dp, arrows = Arrow.Filled))
        } else {
            add(ViewerLine(Contour(sol.drawing.segs.drop(skip).dropLast(tail)), c.viewPath, 1.8.dp, arrows = Arrow.Filled))
        }
    }
    val marks = buildList {
        sol.points.forEachIndexed { i, p ->
            // T 점은 넣은 꼭짓점(모서리 R 이 있으면 이론 꼭짓점) 그대로
            add(ViewerMark(p.vertex, viewLabelColor(c, i), "T${i + 1}"))
            if (direct) p.nodes.forEach { add(ViewerMark(it.prog, c.viewPath, kind = MarkKind.Dot)) }
            p.corner?.let { k ->
                val g = if (k.cw) "G02" else "G03"
                val text = "R${Fmt.fixed3(k.r)} " + if (!direct) g else k.progR?.let { "→ ${Fmt.fixed3(it)} $g" } ?: "$g · 보정 R 없음"
                add(ViewerMark(k.center, c.simArc, text, MarkKind.Cross, arc = Arc(p.nodes.first().drawn, p.nodes.last().drawn, k.center, k.cw)))
            }
        }
        if (direct) {
            add(ViewerMark(sol.entry, c.viewRapid, "진입", MarkKind.Square))
            add(ViewerMark(sol.exit, c.viewRapid, "후퇴", MarkKind.Square))
        }
    }
    ShapeViewerCard(
        lines, marks,
        listOfNotNull(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.viewNose, "노즈 중심", LegendGlyph.Dashed).takeIf { nose },
            LegendItem(c.viewNose, "노즈 R$rText · 가상 인선 ${sol.tip}번", LegendGlyph.Circle).takeIf { nose },
            LegendItem(c.viewPath, if (direct) "직접 보정(G40) 경로" else "기계 보정(${sol.side}) 경로 · 도면 좌표"),
            LegendItem(c.viewRapid, "급송 G00", LegendGlyph.Dotted).takeIf { direct },
            LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
        ),
        subtitle = if (direct) "직접 보정(G40) 경로 · 팁 ${sol.tip}번" else "기계 보정(${sol.side}) · 도면 좌표",
        stock = ViewerStock(sol.drawing, inner, extendRight = reverse),
    )
}

/** 뷰어의 T 점 · 라벨 색(뷰어 바탕 대비가 나은 쪽): T1 주황, T2 청록, T3 남색 */
private fun viewLabelColor(c: AppColors, i: Int): Color = when (i) {
    0 -> c.viewLabelT1
    1 -> c.viewLabelT2
    else -> c.indigo
}

/** 노즈 원(반원 둘) */
internal fun circle(o: Pt, r: Double): Contour {
    val a = o + Pt(r, 0.0)
    val b = o - Pt(r, 0.0)
    return Contour(listOf(Arc(a, b, o, cw = false), Arc(b, a, o, cw = false)))
}
