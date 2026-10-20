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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.screens.taper.FieldLabel
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NoseRPicker
import com.bmt.nosecalc.screens.taper.PointBadge
import com.bmt.nosecalc.screens.taper.TipField
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme

/**
 * TYPE 1~6 입력·결과: 요약 → (계산이 멈추면 그 까닭) → 모드 → 도면 치수(큰 그림 + 칸)·공구 → 경고 → 노드 표 → 형상 → NC.
 * 값이 바뀌면 바로 다시 푼다. 접점 모드의 NC 는 같은 윤곽을 노즈R 0 으로 푼 것(= 도면 좌표 프로그램)이다.
 */
@Composable
internal fun RoundTypeScreen(s: RoundState, type: Int) {
    val c = AppTheme.colors
    val input = s.input(type)
    val result = remember(input) { RoundCalc.calc(input) }
    // 노즈R 0 으로 푼 NC = 도면 좌표 프로그램
    val drawn = remember(input) { RoundCalc.calc(input.copy(noseR = 0.0)).solution?.nc }
    val sol = result.solution
    val blanks = blankable(type)
    val warnings = shownWarnings(result)
    val flagged = warnings.flatMap { roundWarningFields(it, type) }.toSet()
    val missing = RoundWarning.MISSING_INPUT in warnings
    // 아직 안 넣은 칸(빈칸 · 0). 비울 수 있는 칸이면 계산이 채운 값을 칸 · 그림에 보인다
    val empty = dims(type).map { it.key }.filter { input.dim(it) == null }.toSet()
    fun auto(key: String) = if (key in blanks && key in empty) sol?.filled?.dim(key) else null

    Page {
        RoundSummary(type, "외경 · 정방향 가공 (Z−) · ${toolText(s, input.tip)}") { s.open = false }
        if (sol == null) Banners(warnings, type, solved = false, empty) { s.setDim(type, it, "") }
        ModeCard(s.comp) { s.comp = it }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            CardHeader("도면 치수")
            ArtFrame(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(193f / 189f)) {
                RoundTypeArt(
                    type,
                    showDatum = true,
                    l2Ref = s.l2Ref,
                    // 넣은 값 그대로(모서리 R 빈칸은 0), 비운 칸은 계산이 채운 값. 값이 모자라는 동안 안 넣은 칸은 이름만, 계산이 멈췄으면 '?'
                    labels = dims(type).mapNotNull { f ->
                        val a = auto(f.key)
                        when {
                            a != null -> DimShown(Fmt.fixed3(a), DimKind.Auto)
                            f.key !in empty -> DimShown(s.dim(type, f.key).ifEmpty { "0" })
                            missing -> null
                            else -> DimShown("", DimKind.Missing)
                        }?.let { f.key to it }
                    }.toMap() + ("leg" to DimShown(s.shared("leg").ifEmpty { "5.0" })),
                    contradiction = flagged,
                )
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
            Hint(
                buildString {
                    val dia = dims(type).filter { it.label.startsWith("D") }
                    // 조사는 마지막 이름의 읽는 소리로: D1(디일) → 은, D(디) · D2(디이) → 는. 붙임 공백(\u00A0)으로 '·' 가 줄 앞에 오지 않게
                    if (dia.isNotEmpty()) append("${dia.joinToString("\u00A0· ") { it.label }} ${if (dia.last().label.last() in "013678") "은" else "는"} 지름(직경)입니다. ")
                    append("길이는 모서리 R 을 넣기 전 이론 꼭짓점 사이입니다. 모서리 R 이 없으면 모서리 R 칸은 0 그대로 둡니다(비워도 0).")
                    if (blanks.isNotEmpty()) {
                        val names = dims(type).associate { it.key to it.label }
                        append(" ${blanks.joinToString("\u00A0· ") { names.getValue(it).replace(' ', '\u00A0') }} 가운데 하나를 비우거나 0 으로 두면 나머지로 계산합니다(주 R 은 중심이 축 위인 구).")
                    }
                },
            )
            Divider()
            ToolInputs(s, standardTip = RoundState.STANDARD_TIP, tip = input.tip, onTip = { s.tip = it }, leg = type != 1)
        }

        Results(sol, warnings, type, s.comp, input.noseR, drawn)
    }
}

/** 수동(CAD 3점호): 요약 → (계산이 멈추면 그 까닭) → 모드 → 세 점(외경/내경 · 그림 · 칸) · 공구 → 원호 → 경고 → 노드 표 → 형상 → NC */
@Composable
internal fun ManualScreen(s: RoundState) {
    val c = AppTheme.colors
    val input = s.arc3()
    val result = remember(input) { RoundCalc.arc3(input) }
    val drawn = remember(input) { RoundCalc.arc3(input.copy(noseR = 0.0)).solution?.nc }
    val sol = result.solution
    val warnings = shownWarnings(result, input)
    val standard = s.manualStandardTip()
    val tip = s.manualTip ?: standard
    val reverse = s.manualReverse()

    Page {
        val dir = when {
            sol == null -> "방향은 점으로 정해짐"
            reverse -> "역방향 가공 (Z+)"
            else -> "정방향 가공 (Z−)"
        }
        RoundSummary(MANUAL, "${if (s.inner) "내경" else "외경"} · $dir · ${toolText(s, tip)}") { s.open = false }
        if (sol == null) Banners(warnings, MANUAL, solved = false)
        ModeCard(s.comp) { s.comp = it }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            CardHeader("세 점")
            Segmented(listOf("외경", "내경"), if (s.inner) 1 else 0, { s.inner = it == 1 }, Modifier.fillMaxWidth(), color = c.teal, height = 44.dp)
            Spacer(Modifier.height(12.dp))
            ArtFrame(Modifier.fillMaxWidth().height(200.dp)) { ManualArt(withText = true, inner = s.inner, reverse = reverse) }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1 to "시작", 2 to "가운데", 3 to "끝").forEach { (i, name) ->
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PointBadge("P$i", pointColor(i), Modifier.size(38.dp, 52.dp))
                        NumberField("X$i $name (직경)", s.point("x$i"), { s.setPoint("x$i", it) }, Modifier.weight(1f), unit = "mm")
                        NumberField("Z$i", s.point("z$i"), { s.setPoint("z$i", it) }, Modifier.weight(1f), unit = "mm")
                    }
                }
            }
            Hint("CAD 에서 원호 위 세 점을 가공 순서(시작 → 가운데 → 끝)로 찍어 넣습니다. 가운데 점은 원호 위 아무 곳이나 됩니다. 끝 Z 가 시작보다 크면 역방향(역바이트)입니다.")
            Divider()
            ToolInputs(s, standardTip = standard, tip = tip, onTip = { s.manualTip = it }, leg = false)
        }

        if (sol != null) ArcCard(sol)
        Results(sol, warnings, MANUAL, s.comp, input.noseR, drawn)
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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
 * 경고 띠. 값 부족은 파란 안내(넣을 칸 이름), 계산이 멈춘 것은 빨강 — 모순이면 비울 수 있는 칸마다 '… 비우기' 단추.
 * 숫자와 함께 나온 것은 노랑. 다만 형상 1 · 2 · 4 의 바이트 반대쪽 면(이 형상들은 늘 뜬다)은 파란 한 줄로 접어 둔다
 */
@Composable
private fun Banners(warnings: List<RoundWarning>, type: Int, solved: Boolean, empty: Set<String> = emptySet(), onBlank: (String) -> Unit = {}) {
    val names = dims(type).associate { it.key to it.label }
    warnings.forEach { w ->
        val text = roundWarningText(w, type, empty)
        when {
            w == RoundWarning.MISSING_INPUT -> WarningBanner(text, level = WarnLevel.Info)
            !solved -> WarningBanner(
                text,
                level = WarnLevel.Error,
                actions = if (w == RoundWarning.CONTRADICTION) blankable(type).map { key -> "${names.getValue(key)} 비우기" to { onBlank(key) } } else emptyList(),
            )
            w == RoundWarning.AGAINST_TOOL && type in listOf(1, 2, 4) ->
                WarningBanner(text, level = WarnLevel.Info, collapsedText = "바이트 반대쪽 면 있음 (척 쪽 면) — 눌러서 자세히")
            else -> WarningBanner(text, level = WarnLevel.Warn)
        }
    }
}

/** 노즈R · Z 여유 · 끝 직선 길이(TYPE 2~6) · 팁 번호 — TYPE 끼리 함께 쓴다 */
@Composable
private fun ToolInputs(s: RoundState, standardTip: Int, tip: Int, onTip: (Int?) -> Unit, leg: Boolean) {
    NoseRPicker(s.shared("noseR")) { s.setShared("noseR", it) }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField("Z 여유 (진입)", s.shared("zClear"), { s.setShared("zClear", it) }, Modifier.weight(1f), unit = "mm", placeholder = "0")
        if (leg) {
            NumberField(
                "끝 직선 길이", s.shared("leg"), { s.setShared("leg", it) }, Modifier.weight(1f),
                unit = "mm", placeholder = "5.0", hint = "마지막 직선 부분 길이 (그림의 점선 치수) · 비우면 5.0",
            )
        } else {
            TipField(tip, standardTip, onTip, Modifier.weight(1f))
        }
    }
    if (leg) {
        Spacer(Modifier.height(12.dp))
        TipField(tip, standardTip, onTip)
    }
}

/** 숫자와 함께 나온 경고 → 노드 표 → 형상 → NC(모드에 따라 보정 / 도면 좌표). 계산이 멈췄으면 없다(까닭은 요약 아래) */
@Composable
private fun Results(sol: RoundSolution?, warnings: List<RoundWarning>, type: Int, comp: Boolean, noseR: Double, drawnNc: List<String>?) {
    if (sol == null) return
    Banners(warnings, type, solved = true)
    NodeTable(sol, comp)
    RoundViewer(sol, comp, noseR)
    val legNote = if (type in 2..6) " 마지막 줄은 끝 직선의 끝(끝 직선 길이)까지입니다." else ""
    if (comp) {
        NcCard(
            sol.nc,
            "가상 인선 팁 ${sol.tip}번 기준 프로그램 좌표입니다 — 기계의 노즈R 보정은 쓰지 않습니다(G40). 원호 R 은 합성 R(볼록 R + r · 오목 R − r)이고, " +
                "90° 넘는 원호는 나눠 썼습니다. 이송 F 는 넣지 않았습니다.$legNote",
        )
    } else if (drawnNc != null) {
        NcCard(
            drawnNc,
            "도면(접점) 좌표 프로그램입니다. 기계의 노즈R 보정(${sol.side} · 공구 보정 R${Fmt.nc(noseR.takeIf { it.isFinite() } ?: 0.0)} · T${sol.tip})과 함께 쓰고, " +
                "보정 시작 · 취소(${sol.side}/G40) 블록은 기계에 맞게 넣으세요. 이 좌표를 보정 없이 깎으면 노즈R 만큼 형상이 틀립니다. 이송 F 는 넣지 않았습니다.$legNote",
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
