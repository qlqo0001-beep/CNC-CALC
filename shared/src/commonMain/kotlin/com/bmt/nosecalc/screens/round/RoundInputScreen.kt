package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.screens.taper.FieldLabel
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NcFact
import com.bmt.nosecalc.screens.taper.NoseRPicker
import com.bmt.nosecalc.screens.taper.PointBadge
import com.bmt.nosecalc.screens.taper.TipField
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.ResultDock
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * TYPE 1~6 입력·결과: 요약 → (계산이 멈추면 그 까닭) → 모드 → 도면 치수(큰 그림 + 칸)·공구 → 경고 → 노드 표 → 형상 → NC.
 * 값이 바뀌면 바로 다시 푼다. 접점 모드의 NC 는 같은 윤곽을 노즈R 0 으로 푼 것(= 도면 좌표 프로그램)이다.
 * 치수 · 공구 칸은 한 묶음이라 키패드의 "다음" 으로 이어 넣고, 키패드에는 그 치수 하나만 짚은 그림과 결과 한 줄이 뜬다
 */
@Composable
internal fun RoundTypeScreen(s: RoundState, type: Int) {
    val c = AppTheme.colors
    val input = s.input(type)
    val result = remember(input) { RoundCalc.calc(input) }
    // 노즈R 0 으로 푼 것 = 도면 좌표 프로그램(접점 모드의 NC · 뷰어 경로)
    val drawn = remember(input) { RoundCalc.calc(input.copy(noseR = 0.0)).solution }
    val sol = result.solution
    val blanks = blankable(type)
    val warnings = shownWarnings(result)
    val flagged = warnings.flatMap { roundWarningFields(it, type) }.toSet()
    val missing = RoundWarning.MISSING_INPUT in warnings
    // 아직 안 넣은 칸(빈칸 · 0). 비울 수 있는 칸이면 계산이 채운 값을 칸 · 그림에 보인다
    val empty = dims(type).map { it.key }.filter { input.dim(it) == null }.toSet()
    fun auto(key: String) = if (key in blanks && key in empty) sol?.filled?.dim(key) else null
    // 넣은 값 그대로(모서리 R 빈칸은 0), 비운 칸은 계산이 채운 값. 값이 모자라는 동안 안 넣은 칸은 이름만, 계산이 멈췄으면 '?'
    val labels = dims(type).mapNotNull { f ->
        val a = auto(f.key)
        when {
            a != null -> DimShown(Fmt.fixed3(a), DimKind.Auto)
            f.key !in empty -> DimShown(s.dim(type, f.key).ifEmpty { "0" })
            missing -> null
            else -> DimShown("", DimKind.Missing)
        }?.let { f.key to it }
    }.toMap() + ("leg" to DimShown(s.shared("leg").ifEmpty { "5.0" }))
    val dock = roundDock(sol, warnings, type, s.comp, empty, c.textStrong)
    val preview = dock?.oneLine()
    val chain = rememberFieldChain(s.editing)
    // 키패드 그림: 같은 그림에서 그 치수 하나만 초점색으로
    fun figure(key: String): @Composable (Modifier) -> Unit = { m ->
        RoundTypeArt(type, m.aspectRatio(193f / 189f), showDatum = true, l2Ref = s.l2Ref, labels = labels, contradiction = flagged, focus = key)
    }

    Page(dock, chain.sheetOpen) { anchor ->
        RoundSummary(type, "외경 · 정방향 가공 (Z−) · ${toolText(s, input.tip)}") { s.open = false }
        if (sol == null) Banners(warnings, type, solved = false, empty, anchor) { s.setDim(type, it, "") }
        ModeCard(s.comp) { s.comp = it }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            CardHeader("도면 치수")
            ArtFrame(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(193f / 189f)) {
                RoundTypeArt(type, showDatum = true, l2Ref = s.l2Ref, labels = labels, contradiction = flagged)
            }
            Text(
                // ▲ 는 그림의 Z0 표와 같은 색
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.drawT1)) { append("▲") }
                    append(" Z0 = 오른쪽 끝면 · X 는 지름 · 바이트는 Z− (왼쪽) 으로")
                },
                Modifier.fillMaxWidth().padding(top = 6.dp),
                color = c.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            FieldChainHost(chain) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    dims(type).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { f ->
                                // 비울 수 있는 칸의 0 · 빈칸은 계산이 채운 값을 "자동" 으로(못 풀면 "—"). 값이 모자라는 동안은 쓴 그대로
                                val asAuto = f.key in blanks && f.key in empty && !missing
                                NumberField(
                                    if (type == 2 && f.key == "l2") "L2 · ${s.l2Ref.name} 기준" else f.label,
                                    if (asAuto) "" else s.dim(type, f.key),
                                    { s.setDim(type, f.key, it) },
                                    Modifier.weight(1f),
                                    unit = "mm",
                                    placeholder = "0",
                                    autoValue = if (asAuto) auto(f.key)?.let { Fmt.fixed3(it) } ?: "" else null,
                                    error = f.key in flagged,
                                    tag = if (f.key.startsWith("d")) "Ø직경" else null,
                                    hint = dimHint(f.key),
                                    blankable = f.key in blanks,
                                    id = f.key,
                                    figure = figure(f.key),
                                    preview = preview,
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (type == 2) {
                        Column {
                            FieldLabel("L2 기준")
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                listOf("A  왼쪽 이음점 ~ 오른쪽 끝", "B  두 이음점 사이"), if (s.l2Ref == L2Ref.A) 0 else 1,
                                { s.l2Ref = if (it == 0) L2Ref.A else L2Ref.B }, Modifier.fillMaxWidth(), color = c.teal, height = 42.dp, fontSize = 14,
                            )
                        }
                    }
                }
                val dia = dims(type).filter { it.key.startsWith("d") }
                ExpandableHint(
                    "${if (dia.isEmpty()) "길이는" else "길이 · 지름은"} ○ 표시(모서리 R 을 넣기 전 이론 꼭짓점) 사이입니다.",
                    buildString {
                        // 조사는 마지막 이름의 읽는 소리로: D1(디일) → 은, D(디) · D2(디이) → 는. 붙임 공백(\u00A0)으로 '·' 가 줄 앞에 오지 않게
                        if (dia.isNotEmpty()) append("${dia.joinToString("\u00A0· ") { it.label }} ${if (dia.last().label.last() in "013678") "은" else "는"} 지름(직경)입니다. ")
                        append("모서리 R 이 없으면 모서리 R 칸은 0 그대로 둡니다(비워도 0).")
                        if (blanks.isNotEmpty()) {
                            val names = dims(type).associate { it.key to it.label }
                            append(" ${blanks.joinToString("\u00A0· ") { names.getValue(it).replace(' ', '\u00A0') }} 가운데 하나를 비우거나 0 으로 두면 나머지로 계산합니다(주 R 은 중심이 축 위인 구).")
                            append(" 키패드의 '비우기 · 자동 계산' 으로 비웁니다.")
                        }
                    },
                    Modifier.padding(top = 8.dp),
                )
                Divider()
                ToolInputs(s, RoundState.STANDARD_TIP, input.tip, { s.tip = it }, leg = if (type != 1) figure("leg") else null, preview = preview)
            }
        }

        Results(sol, warnings, type, s.comp, input.noseR, drawn, anchor, inner = false)
        if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
    }
}

/** 치수 칸 키패드의 안내 줄: 지름 · 길이만 */
private fun dimHint(key: String): String? = when {
    key.startsWith("d") -> "직경값 — 도면 Ø 그대로"
    key.startsWith("l") -> "길이 (+ 값) · 모서리 R 을 넣기 전 이론 꼭짓점 사이"
    else -> null
}

/** 수동(CAD 3점호): 요약 → (계산이 멈추면 그 까닭) → 모드 → 세 점(외경/내경 · 그림 · 칸) · 공구 → 원호 → 경고 → 노드 표 → 형상 → NC */
@Composable
internal fun ManualScreen(s: RoundState) {
    val c = AppTheme.colors
    val input = s.arc3()
    val result = remember(input) { RoundCalc.arc3(input) }
    val drawn = remember(input) { RoundCalc.arc3(input.copy(noseR = 0.0)).solution }
    val sol = result.solution
    val warnings = shownWarnings(result, input)
    val standard = s.manualStandardTip()
    val tip = s.manualTip ?: standard
    val reverse = s.manualReverse()
    val dock = roundDock(sol, warnings, MANUAL, s.comp, name = c.textStrong)
    val preview = dock?.oneLine()
    val chain = rememberFieldChain(s.editing)

    Page(dock, chain.sheetOpen) { anchor ->
        val dir = when {
            sol == null -> "방향은 점으로 정해짐"
            reverse -> "역방향 가공 (Z+)"
            else -> "정방향 가공 (Z−)"
        }
        RoundSummary(MANUAL, "${if (s.inner) "내경" else "외경"} · $dir · ${toolText(s, tip)}") { s.open = false }
        if (sol == null) Banners(warnings, MANUAL, solved = false, modifier = anchor)
        ModeCard(s.comp) { s.comp = it }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            CardHeader("세 점")
            Segmented(listOf("외경", "내경"), if (s.inner) 1 else 0, { s.inner = it == 1 }, Modifier.fillMaxWidth(), color = c.teal, height = 44.dp)
            Spacer(Modifier.height(12.dp))
            ArtFrame(Modifier.fillMaxWidth().height(200.dp)) { ManualArt(withText = true, inner = s.inner, reverse = reverse) }
            Spacer(Modifier.height(14.dp))
            FieldChainHost(chain) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(1 to "시작", 2 to "가운데", 3 to "끝").forEach { (i, name) ->
                        // 키패드 그림: 그 점만 초점색으로
                        val figure: @Composable (Modifier) -> Unit = { m -> ManualArt(withText = false, inner = s.inner, reverse = reverse, focus = i, modifier = m.fillMaxWidth()) }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PointBadge("P$i", pointColor(i), Modifier.size(38.dp, 52.dp))
                            NumberField(
                                "X$i $name (직경)", s.point("x$i"), { s.setPoint("x$i", it) }, Modifier.weight(1f),
                                unit = "mm", id = "x$i", figure = figure, preview = preview,
                            )
                            NumberField("Z$i", s.point("z$i"), { s.setPoint("z$i", it) }, Modifier.weight(1f), unit = "mm", id = "z$i", figure = figure, preview = preview)
                        }
                    }
                }
                Hint("CAD 에서 원호 위 세 점을 가공 순서(시작 → 가운데 → 끝)로 찍어 넣습니다. 가운데 점은 원호 위 아무 곳이나 됩니다. 끝 Z 가 시작보다 크면 역방향(역바이트)입니다.")
                Divider()
                ToolInputs(s, standard, tip, { s.manualTip = it }, leg = null, preview = preview)
            }
        }

        if (sol != null) ArcCard(sol)
        Results(sol, warnings, MANUAL, s.comp, input.noseR, drawn, anchor, inner = s.inner)
        if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
    }
}

/**
 * 화면 틀: 세로 스크롤(양옆 Space.gutter · 카드 사이 Space.cardGap · 아래 80dp — 끝 카드가 결과 띠에 가리지 않게) + 아래 결과 띠.
 * 띠는 anchor 를 단 카드(풀리면 노드 표, 멈췄으면 위의 빨간 띠)가 화면 밖일 때만 뜨고(키패드가 열려 있으면 안 뜬다), 띠 단추는 그 카드로 스크롤한다
 */
@Composable
private fun Page(dock: DockStatus?, sheetOpen: Boolean, content: @Composable ColumnScope.(anchor: Modifier) -> Unit) {
    val scope = rememberCoroutineScope()
    val bring = remember { BringIntoViewRequester() }
    // 스크롤 칸(화면에 보이는 곳)과 anchor 카드의 자리(뿌리 좌표, 스크롤로 화면 밖이어도 잘리지 않은 칸)
    var view by remember { mutableStateOf(Rect.Zero) }
    var target by remember { mutableStateOf(Rect.Zero) }
    val anchor = remember { Modifier.bringIntoViewRequester(bring).onGloballyPositioned { target = Rect(it.positionInRoot(), it.size.toSize()) } }
    // 스크롤할 때마다 자리가 바뀌지만 띠는 보임 · 숨김이 바뀔 때만 다시 그린다
    val away by remember { derivedStateOf { !target.isEmpty && !target.overlaps(view) } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { view = it.boundsInRoot() }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(top = Space.cardGap, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(Space.cardGap),
        ) { content(anchor) }
        if (dock != null) {
            ResultDock(dock, visible = !sheetOpen && away) { scope.launch { bring.bringIntoView() } }
        }
    }
}

/**
 * 결과 띠 · 키패드 결과 한 줄: 풀리면 지금 모드의 노드 좌표(합성 R = 보정 = 프로그램 좌표, 접점 = 도면 좌표 · name 색 이름)와
 * 노란 경고 수, 멈췄으면 첫 경고의 첫 문장. 값이 모자라는 동안(파란 안내)은 null — 띠도 한 줄도 없다
 */
internal fun roundDock(
    sol: RoundSolution?,
    warnings: List<RoundWarning>,
    type: Int,
    comp: Boolean,
    empty: Set<String> = emptySet(),
    name: Color,
): DockStatus? {
    if (sol == null) {
        val w = warnings.firstOrNull()?.takeIf { it != RoundWarning.MISSING_INPUT } ?: return null
        val text = roundWarningText(w, type, empty)
        val cut = text.indexOf(". ")
        return DockStatus.Error(if (cut < 0) text else text.substring(0, cut + 1))
    }
    val rows = sol.nodes.mapIndexed { i, n ->
        val p = if (comp) n.prog else n.drawn
        DockRow("P${nodeNo(sol, i)}", Fmt.fixed3(p.xDia), Fmt.fixed3(p.z), name)
    }
    val warn = warnings.count { warnLevel(it, type, solved = true) == WarnLevel.Warn }
    return if (warn > 0) DockStatus.Warn(warn, rows) else DockStatus.Ok(rows)
}

/** 요약 끝 공구 글: 노즈 R(쓴 그대로) · 팁 번호. 붙임 공백 · 낱말 이음(\u2060)으로 "팁 3번" 이 쪼개져 줄바꿈되지 않게 */
private fun toolText(s: RoundState, tip: Int) = "노즈\u00A0R${s.shared("noseR").ifEmpty { "—" }} · 팁\u00A0${tip}\u2060번"

/** 카드 안 그림 칸: 가운데, 모서리 10dp, 옅은 바탕 + 1dp 테두리 */
@Composable
private fun ColumnScope.ArtFrame(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.align(Alignment.CenterHorizontally).then(modifier).clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape),
        content = content,
    )
}

/**
 * 경고 띠 색: 값 부족은 파란 안내, 계산이 멈춘 것은 빨강, 숫자와 함께 나온 것은 노랑.
 * 다만 형상 1 · 2 · 4 의 바이트 반대쪽 면(이 형상들은 늘 뜬다)은 파란 한 줄로 접어 둔다
 */
private fun warnLevel(w: RoundWarning, type: Int, solved: Boolean): WarnLevel = when {
    w == RoundWarning.MISSING_INPUT -> WarnLevel.Info
    !solved -> WarnLevel.Error
    w == RoundWarning.AGAINST_TOOL && type in listOf(1, 2, 4) -> WarnLevel.Info
    else -> WarnLevel.Warn
}

/** 경고 띠([warnLevel] 색). 값 부족은 넣을 칸 이름을 들고, 모순으로 멈췄으면 비울 수 있는 칸마다 '… 비우기' 단추 */
@Composable
private fun Banners(
    warnings: List<RoundWarning>,
    type: Int,
    solved: Boolean,
    empty: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    onBlank: (String) -> Unit = {},
) {
    if (warnings.isEmpty()) return
    val names = dims(type).associate { it.key to it.label }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
        warnings.forEach { w ->
            val level = warnLevel(w, type, solved)
            WarningBanner(
                roundWarningText(w, type, empty),
                level = level,
                collapsedText = if (w == RoundWarning.AGAINST_TOOL && level == WarnLevel.Info) "바이트 반대쪽 면 있음 (척 쪽 면) — 눌러서 자세히" else null,
                actions = if (w == RoundWarning.CONTRADICTION && !solved) blankable(type).map { key -> "${names.getValue(key)} 비우기" to { onBlank(key) } } else emptyList(),
            )
        }
    }
}

/**
 * 노즈R · Z 여유 · 끝 직선 길이(TYPE 2~6) · 팁 번호 — TYPE 끼리 함께 쓴다.
 * leg = 끝 직선 길이 칸의 키패드 그림(null 이면 그 칸이 없다 — 형상 1 · 3점호), preview = 키패드 결과 한 줄
 */
@Composable
private fun ToolInputs(s: RoundState, standardTip: Int, tip: Int, onTip: (Int?) -> Unit, leg: (@Composable (Modifier) -> Unit)?, preview: String?) {
    NoseRPicker(s.shared("noseR")) { s.setShared("noseR", it) }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            "Z 여유 (진입)", s.shared("zClear"), { s.setShared("zClear", it) }, Modifier.weight(1f),
            unit = "mm", placeholder = "0", id = "zClear", preview = preview,
        )
        if (leg != null) {
            NumberField(
                "끝 직선 길이", s.shared("leg"), { s.setShared("leg", it) }, Modifier.weight(1f),
                unit = "mm", placeholder = "5.0", hint = "마지막 직선 부분 길이 (그림의 점선 치수) · 비우면 5.0", id = "leg", figure = leg, preview = preview,
            )
        } else {
            TipField(tip, standardTip, onTip, Modifier.weight(1f))
        }
    }
    if (leg != null) {
        Spacer(Modifier.height(12.dp))
        TipField(tip, standardTip, onTip)
    }
}

/**
 * 숫자와 함께 나온 경고 → 노드 표(anchor — 결과 띠가 가리킨다) → 형상 → NC(모드에 따라 보정 / 도면 좌표). 계산이 멈췄으면 없다(까닭은 요약 아래).
 * drawn = 노즈R 0 으로 푼 것(도면 좌표 프로그램), inner = 소재가 구멍 쪽(3점호 내경)
 */
@Composable
private fun Results(
    sol: RoundSolution?,
    warnings: List<RoundWarning>,
    type: Int,
    comp: Boolean,
    noseR: Double,
    drawn: RoundSolution?,
    anchor: Modifier,
    inner: Boolean,
) {
    if (sol == null) return
    Banners(warnings, type, solved = true)
    NodeTable(sol, comp, anchor)
    RoundViewer(sol, comp, noseR, drawn, inner)
    val legNote = if (type in 2..6) " 마지막 줄은 끝 직선의 끝(끝 직선 길이)까지입니다." else ""
    val r = Fmt.nc(noseR).removeSuffix(".")
    if (comp) {
        NcCard(
            sol.nc,
            "가상 인선 팁 ${sol.tip}번 기준 프로그램 좌표입니다 — 기계의 노즈R 보정은 쓰지 않습니다(G40). 원호 R 은 합성 R(볼록 R + r · 오목 R − r)이고, " +
                "90° 넘는 원호는 나눠 썼습니다. 이송 F 는 넣지 않았습니다.$legNote",
            programName = "직접 보정(G40) 프로그램",
            facts = listOf(NcFact("G41/G42 걸지 말 것 (이중 보정)", WarnLevel.Error), NcFact("원호 = 합성 R"), NcFact("이송 F 없음")),
        )
    } else if (drawn != null) {
        NcCard(
            drawn.nc,
            "도면(접점) 좌표 프로그램입니다. 기계의 노즈R 보정(${sol.side} · 공구 보정 R$r · T${sol.tip})과 함께 쓰고, " +
                "보정 시작 · 취소(${sol.side}/G40) 블록은 기계에 맞게 넣으세요. 이 좌표를 보정 없이 깎으면 노즈R 만큼 형상이 틀립니다. 이송 F 는 넣지 않았습니다.$legNote",
            programName = "도면 좌표 프로그램 (G41/G42 용)",
            facts = listOf(
                NcFact("보정 시작 · 취소 블록은 직접 넣기", WarnLevel.Warn),
                NcFact("공구 보정: R $r · T ${sol.tip}", WarnLevel.Warn),
                NcFact("이송 F 없음"),
            ),
        )
    }
}

/** 점 배지 색(그림의 P1 · P2 · P3 와 같다). 끝점도 보통 요소라 빨강이 아닌 남색 */
@Composable
private fun pointColor(i: Int) = when (i) {
    1 -> AppTheme.colors.drawT1
    2 -> AppTheme.colors.drawT2
    else -> AppTheme.colors.indigo
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(top = 12.dp), color = AppTheme.colors.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp)
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.lineCard))
}
