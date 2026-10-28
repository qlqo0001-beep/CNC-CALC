package com.bmt.nosecalc.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.CompMode
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarResult
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarSolution
import com.bmt.nosecalc.calc.PolarWarning
import com.bmt.nosecalc.screens.polar.PolarField
import com.bmt.nosecalc.screens.polar.PolarFigure
import com.bmt.nosecalc.screens.polar.PolarSimModal
import com.bmt.nosecalc.screens.polar.PolarState
import com.bmt.nosecalc.screens.polar.ShapeOrder
import com.bmt.nosecalc.screens.polar.SimRun
import com.bmt.nosecalc.screens.polar.SimStart
import com.bmt.nosecalc.screens.polar.plainNum
import com.bmt.nosecalc.screens.polar.polarGeom
import com.bmt.nosecalc.screens.polar.polarResultCells
import com.bmt.nosecalc.screens.polar.polarWarningText
import com.bmt.nosecalc.screens.polar.shapeName
import com.bmt.nosecalc.screens.polar.shapeNote
import com.bmt.nosecalc.screens.polar.simPlan
import com.bmt.nosecalc.screens.taper.GroupDivider
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NcFact
import com.bmt.nosecalc.screens.taper.TaperPage
import com.bmt.nosecalc.screens.taper.firstSentence
import com.bmt.nosecalc.screens.thread.ChoiceField
import com.bmt.nosecalc.screens.thread.ChoiceRow
import com.bmt.nosecalc.screens.thread.ChoiceSheet
import com.bmt.nosecalc.screens.thread.ResultCell
import com.bmt.nosecalc.screens.thread.ThreadLabel
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.HelpSection
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import com.bmt.nosecalc.ui.theme.onFill
import kotlin.math.floor

/** 턴밀 극좌표(G12.1) 다각가공 */
@Composable
fun PolarScreen() = PolarScreen(PolarState())

/**
 * 턴밀 극좌표 다각가공 화면(원본 12 · 07): 외측/내측 → (값 부족 안내 · 멈춤) → 형상 뷰어 → '작업 도형 선택'(도형 · 치수 입력 · 가공 옵션) →
 * (확인 경고) → '프로그램 생성'(결과 칸 · 시뮬레이션 단추) → 안내 → NC(복사). 값이 바뀌면 바로 다시 푼다(계산 버튼 없음).
 * NC · 결과 · 시뮬레이션은 앱 전체 보정 방식(기계 보정 G41 · 직접 보정 G40 — 테이퍼 · 라운드와 같은 [com.bmt.nosecalc.AppState.compMode])을 따른다.
 * initial = 시험이 정한 처음 상태, editing = 처음부터 키패드를 열어 둘 칸, sheet = 도형 시트를 열어 둠, sim = 시뮬레이션을 그 자리에 멈춰 열어 둠(스크린샷 시험용)
 */
@Composable
internal fun PolarScreen(initial: PolarState, editing: PolarField? = null, sheet: Boolean = false, sim: SimStart? = null) {
    val s = rememberSaveable(saver = PolarState.Saver) { initial }
    val chain = rememberFieldChain(editing?.key)
    var shapeSheet by remember { mutableStateOf(sheet) }
    var simOpen by remember { mutableStateOf(sim) }
    val app = LocalAppState.current
    val c = AppTheme.colors
    val direct = app.compMode == CompMode.DIRECT
    FeatureScaffold(Feature.POLAR, PolarHelp) {
        val input = s.input()
        val result = remember(input) { PolarG12.calc(input) }
        val sol = result.solution
        val waiting = PolarWarning.MISSING_INPUT in result.warnings
        val bad = s.bad()
        // 멈춘 까닭(빨강): 계산기의 멈춤 경고, 값이 모자라는 동안에도 범위 밖 칸 · 내측 2면폭은 바로
        val errors = when {
            sol != null -> emptyList()
            waiting -> if (bad.isNotEmpty() || s.shapeBad) listOf(PolarWarning.BAD_INPUT) else emptyList()
            else -> result.warnings
        }
        val flagged = if (sol != null) emptySet() else s.flagged(errors)
        val geom = polarGeom(s, sol)
        val dock = polarDock(s, result, errors, c)
        val preview = sol?.let { previewLine(s, it) } ?: dock?.oneLine()
        val program = sol?.let { if (direct) it.ncG40 else it.ncG41G42 }

        @Composable
        fun field(f: PolarField, modifier: Modifier) = NumberField(
            s.label(f), s.value(f), { s.set(f, it) }, modifier,
            unit = f.unit, placeholder = s.placeholder(f), error = f in flagged,
            hint = s.hint(f), id = f.key, preview = preview,
            figure = if (f in FIGURE_FIELDS) ({ m -> PolarFigure(geom, m.fillMaxWidth(), focus = f) }) else null,
        )

        FieldChainHost(chain) {
            TaperPage(dock, chain.sheetOpen) { anchor ->
                // ② 가공 위치(원본 12)
                AppCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                    Segmented(listOf("외측 가공", "내측 가공"), if (s.inner) 1 else 0, { s.inner = it == 1 }, Modifier.fillMaxWidth(), color = c.teal, height = 54.dp)
                }
                if (waiting) WarningBanner(polarWarningText(PolarWarning.MISSING_INPUT, s), level = WarnLevel.Info)
                if (errors.isNotEmpty()) {
                    // 결과 띠의 '칸 보기' 가 가는 곳(멈추면 이 빨간 띠)
                    Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                        errors.forEach { WarningBanner(polarWarningText(it, s), level = WarnLevel.Error) }
                    }
                }

                // ③ 형상 뷰어(원본 12): 축 · 외경(구멍) 원 · 도형 · W — 값을 바꾸면 바로 다시 그린다
                AppCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                    PolarFigure(geom, Modifier.fillMaxWidth().height(270.dp))
                    Text(
                        keepWords(
                            if (geom.sample) "예시 비율 — 외경 · W 를 넣으면 실제 비율로 그립니다"
                            else "실제 비율 · X 세로 · C 가로 (C(+) 왼쪽) · ${if (s.inner) "점선 = 기초 구멍" else "점선 = 소재 외경"}",
                        ),
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        style = AppType.caption,
                        color = c.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                // ④~⑦ 작업 도형 선택 · 치수 입력 · 가공 옵션(원본 12 — 한 카드)
                AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                    CardHeader("작업 도형 선택")
                    ChoiceField(null, shapeName(s.shape), Modifier.fillMaxWidth()) { shapeSheet = true }
                    GroupDivider()
                    CardHeader("치수 입력") { Text("단위 mm · 지름은 직경", style = AppType.caption, color = c.textMuted) }
                    Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            field(PolarField.OD, Modifier.weight(1f))
                            field(PolarField.WIDTH, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            if (s.usesCornerR) field(PolarField.CORNER_R, Modifier.weight(1f))
                            field(PolarField.ROTATION, Modifier.weight(1f))
                            if (!s.usesCornerR) Spacer(Modifier.weight(1f))
                        }
                    }
                    GroupDivider()
                    CardHeader("가공 옵션", keepWords("FANUC 기반 · 엔드밀 옆날로 한 바퀴"))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            field(PolarField.TOOL_DIA, Modifier.weight(1f))
                            field(PolarField.CLEAR, Modifier.weight(1f))
                        }
                        val toolR = s.num(PolarField.TOOL_DIA)?.takeIf { it > 0 && it.isFinite() }?.let { "R${plainNum(it / 2)}" } ?: "R(공구 반경)"
                        LabeledChoice(
                            "보정 방식 (경보정)",
                            listOf("기계 보정 G41", "직접 보정 G40"),
                            if (direct) 1 else 0,
                            { app.compMode = if (it == 1) CompMode.DIRECT else CompMode.MACHINE },
                            if (direct) "앱이 계산한 공구 중심 좌표 그대로 깎습니다 (기계 보정 없음)"
                            else "공구 보정 화면에 $toolR · T0 을 넣고 도면 좌표로 깎습니다",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            field(PolarField.DEPTH, Modifier.weight(1f))
                            field(PolarField.FEED, Modifier.weight(1f))
                        }
                        LabeledChoice(
                            "G12.1 안 X 지령",
                            listOf("직경값", "반경값"),
                            if (s.xDiameter) 0 else 1,
                            { s.xDiameter = it == 0 },
                            "기계 매개변수마다 다릅니다 (FANUC 매뉴얼끼리도 다름). 첫 가공은 싱글 블록으로 시작점 · 첫 변을 확인하세요.",
                        )
                    }
                }

                // 숫자와 함께 나온 확인 경고
                if (sol != null) result.warnings.forEach { WarningBanner(polarWarningText(it, s, sol), level = WarnLevel.Warn) }

                if (sol != null && program != null) {
                    val feed = s.num(PolarField.FEED) ?: 0.0
                    val plan = remember(program, feed) { simPlan(sol, program, feed) }
                    // ⑧ 프로그램 생성: 결과 칸 + 시뮬레이션(원본 07 모달)
                    AppCard(anchor.fillMaxWidth(), padding = Space.cardPad) {
                        CardHeader(
                            "프로그램 생성",
                            keepWords("${shapeName(s.shape)} · ${if (s.inner) "내측" else "외측"} · ${if (direct) "직접 보정 G40" else "기계 보정 G41"} · 한 바퀴"),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            polarResultCells(s, sol, direct).chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                    row.forEach { (label, value, sub) -> ResultCell(label, value, Modifier.weight(1f), sub = keepWords(sub), color = c.valueComp) }
                                }
                            }
                        }
                        Text(
                            keepWords("이송 시간 ≈ ${minSec(plan.feedSeconds)} (F${plainNum(feed)} mm/min · Z 이송 포함, 급송 · 공구 교환 빼고)"),
                            Modifier.padding(top = 10.dp),
                            style = AppType.caption,
                            color = c.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        val fill = c.appsFill
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(fill)
                                .clickable { simOpen = SimStart(-1, 0.0) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayMark(c.onFill(fill))
                            Spacer(Modifier.width(8.dp))
                            Text(keepWords("시뮬레이션 — 공구 경로 재생"), color = c.onFill(fill), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    WarningBanner(
                        "프로그램에는 C축 모드(Cs 윤곽 제어) · 회전 공구 회전(M 코드 · 회전수) · 공구 번호 T 가 없습니다 — 기계마다 달라 프로그램 앞에 붙여 씁니다. " +
                            "G12.1 안의 X 를 직경으로 읽는지 반경으로 읽는지도 기계 매개변수에 따릅니다(지금: ${if (s.xDiameter) "직경값" else "반경값"}). " +
                            "극좌표 보간 기능이 켜져 있어야 하고, 첫 가공은 공회전 · 싱글 블록으로 시작점과 첫 변을 확인하세요.",
                        level = WarnLevel.Info,
                        collapsedText = "기계마다 다른 것 — M 코드 · X 직경/반경",
                    )
                    NcCard(
                        program.lines,
                        "G12.1 · G13.1 은 혼자 한 줄이고 G40 상태에서 바꿉니다(FANUC). " +
                            "G12.1 앞 G00 X… C0. 은 선반 좌표(X 직경, C 회전각 0°)이고, G12.1 안의 C 는 각도가 아니라 극좌표 평면의 mm 입니다 — X 가 음수인 줄은 중심 너머 점입니다. " +
                            "F 는 평면의 접선 속도(mm/min, G98)이고, 모드 안에서는 G00 을 쓸 수 없어 이동은 모두 G01 · G02 · G03 입니다. " +
                            if (direct) "직접 보정 프로그램은 공구 중심 좌표라 G41/G42 를 또 걸면 안 됩니다(첫 줄 주석)."
                            else "기계 보정 프로그램은 도면 좌표 + G41 — 공구 보정 화면에 R · T0 을 넣습니다(첫 줄 주석).",
                        programName = if (direct) "직접 보정 프로그램 (G40)" else "기계 보정 프로그램 (G41)",
                        facts = listOfNotNull(
                            NcFact("G98 · F mm/min"),
                            NcFact(if (s.xDiameter) "G12.1 안 X = 직경" else "G12.1 안 X = 반경", WarnLevel.Warn),
                            NcFact("M 코드 · T 없음"),
                            if (direct) null else NcFact("공구 보정 R${plainNum(sol.toolR)} · T0"),
                        ),
                    )

                    simOpen?.let { start ->
                        val run = remember(start, plan) { SimRun(plan, start.block, start.frac, start.single, start.speedIdx) }
                        PolarSimModal(sol, program, geom, direct, s.xDiameter, run) { simOpen = null }
                    }
                }
                if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
            }
        }
    }
    if (shapeSheet) ShapeSheet(s) { shapeSheet = false }
}

/** 키패드 그림을 보일 칸(도형 치수) */
private val FIGURE_FIELDS = setOf(PolarField.OD, PolarField.WIDTH, PolarField.CORNER_R, PolarField.ROTATION)

/** 결과 띠 · 키패드 한 줄: 풀리면 시작점(G00 X · 깊이 Z)과 노란 경고 수, 멈췄으면 첫 경고의 첫 문장, 값이 모자라는 동안은 null */
private fun polarDock(s: PolarState, result: PolarResult, errors: List<PolarWarning>, c: AppColors): DockStatus? {
    val sol = result.solution ?: return errors.firstOrNull()?.let { DockStatus.Error(firstSentence(polarWarningText(it, s))) }
    val depth = s.num(PolarField.DEPTH) ?: return null
    val rows = listOf(DockRow("시작", Fmt.fixed3(2 * sol.startX), Fmt.fixed3(depth), c.drawT1, c.valueComp))
    return if (result.warnings.isNotEmpty()) DockStatus.Warn(result.warnings.size, rows) else DockStatus.Ok(rows)
}

/** 키패드 결과 한 줄(풀렸을 때): 꼭짓점 지름 · 평면 길이 · 시작점 */
internal fun previewLine(s: PolarState, sol: PolarSolution): String =
    (if (s.shape == PolarShape.TWO_FLAT) "" else "꼭짓점 Ø${Fmt.fixed3(sol.cornerDia)} · ") +
        "평면 ${Fmt.fixed3(sol.flatLength)} · 시작 X${Fmt.nc(2 * sol.startX)} C0."

/** 초 → '3분 54초' · '42초' */
internal fun minSec(sec: Double): String {
    val t = floor(sec + 0.5).toLong()
    return if (t >= 60) "${t / 60}분 ${t % 60}초" else "${t}초"
}

/** 이름 + 두 칸 고르기(52dp) + 아래 설명 한 줄 */
@Composable
private fun LabeledChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, note: String) {
    val c = AppTheme.colors
    Column {
        ThreadLabel(label)
        Spacer(Modifier.height(6.dp))
        Segmented(options, selected, onSelect, Modifier.fillMaxWidth(), color = c.teal, height = 52.dp, fontSize = 15)
        Text(keepWords(note), Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), style = AppType.caption, color = c.textMuted)
    }
}

/** 단추 안 재생 표(채운 삼각형) */
@Composable
private fun PlayMark(tint: Color) = Canvas(Modifier.size(16.dp)) {
    drawPath(Path().apply { moveTo(size.width * .18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .18f, size.height); close() }, tint)
}

/** 작업 도형 고르기 시트(원본 12 의 드롭다운). 내측에서는 2면폭을 고를 수 없다 */
@Composable
private fun ShapeSheet(s: PolarState, onDismiss: () -> Unit) {
    val app = LocalAppState.current
    ChoiceSheet("작업 도형 선택", if (s.inner) "내측" else "외측", "W 의 뜻이 도형마다 다릅니다 — 짝수 각은 맞변 폭, 홀수 각은 내접원 지름.", onDismiss) {
        ShapeOrder.forEach { p ->
            val blocked = s.inner && p == PolarShape.TWO_FLAT
            ChoiceRow(shapeName(p), if (blocked) "외측만 — 내측에서는 고를 수 없습니다" else shapeNote(p), p == s.shape) {
                if (blocked) {
                    app.toast("2면폭은 외측 가공만 됩니다")
                } else {
                    s.shape = p
                    onDismiss()
                }
            }
        }
    }
}

/** 헤더 도움말: 도형 · 값 넣기 · 가공 옵션과 보정 · 프로그램과 시뮬레이션 · 경고 */
internal val PolarHelp = listOf(
    HelpSection(
        "도형 고르기",
        listOf(
            "맨 위에서 외측 가공(소재 바깥을 다각형으로) · 내측 가공(뚫어 둔 구멍 안을 다각형 구멍으로)을 고릅니다. 도형은 정3 · 4 · 5 · 6 · 8각형과 2면폭(외측만)입니다.",
            "W 는 짝수 각(4 · 6 · 8각 · 2면폭)이면 마주 보는 변 사이(맞변 폭 · 스패너 치수), 홀수 각(3 · 5각)이면 중심에서 변까지의 두 배(내접원 지름)입니다.",
            "회전 0° 는 한 변이 C 축에 직각입니다(육각형이면 꼭짓점이 X 축 위 — 원본 화면과 같음). + 는 X 에서 C(+) 쪽, 그림에서 반시계입니다.",
            "형상 뷰어는 X 를 세로(위 +), C 를 가로(왼쪽 +)로 그립니다. 점선 원은 외측이면 소재 외경, 내측이면 기초 구멍입니다. 외경 · W 를 넣기 전에는 예시 비율로 그립니다.",
        ),
    ),
    HelpSection(
        "값 넣기",
        listOf(
            "칸은 빈칸에서 시작합니다. 외경(내측은 기초 구멍 지름) · W · 공구경 · 깊이 Z · 이송 F 를 넣으면 바로 계산합니다(파란 안내가 남은 것을 적습니다). " +
                "이송 F 와 공구경은 정해진 기본값이 없어 꼭 넣습니다 — 공구 카탈로그 값으로 넣으세요.",
            "코너 R · 회전은 비우면 0, 여유는 비우면 1 mm 로 계산합니다(자리표시가 그 값을 알립니다).",
            "깊이 Z 는 면을 Z0 으로 본 깎는 바닥이라 음수입니다(예: −5). 여유는 시작점이 소재(내측은 구멍 벽)에서 떨어지는 거리이자 Z 접근 높이(면 위)입니다.",
            "범위 밖 값은 넣자마자 빨간 띠와 칸의 빨간 테로 짚습니다.",
        ),
    ),
    HelpSection(
        "가공 옵션과 보정",
        listOf(
            "보정 방식(경보정)은 테이퍼 · 라운드와 같은 앱 전체 스위치입니다. 기계 보정 G41 = 도면 좌표에 G41 을 걸어 제어기가 공구 반경을 보정합니다 — 공구 보정 화면에 " +
                "R = 공구 반경, T = 0(가상 인선 0 = 공구 중심)을 넣습니다. 직접 보정 G40 = 앱이 계산한 공구 중심 좌표 그대로입니다(G41/G42 를 또 걸지 않습니다).",
            "외측은 시계, 내측은 반시계로 돌아 공구가 늘 진행 방향 왼쪽에 있습니다(G41). 하향 · 상향 절삭은 C 축 방향과 회전 공구 방향이 기계마다 달라 정하지 않았습니다.",
            "G12.1 안 X 지령: FANUC 은 X 를 직경으로 읽는 기계와 반경으로 읽는 기계가 있습니다(매뉴얼끼리도 다름). 기계에 맞게 고르고, 첫 가공은 싱글 블록으로 시작점과 첫 변을 확인하세요. " +
                "R 은 어느 쪽이든 실제 mm 입니다.",
        ),
    ),
    HelpSection(
        "프로그램과 시뮬레이션",
        listOf(
            "프로그램: G98(분당 이송) → G00 X… C0.(시작점, G12.1 앞 선반 좌표) → Z 여유 → G01 Z 깊이 → G12.1 → 원점 둘레로 진입 변까지 → 보정 시작 → " +
                "변 가운데로 들어가는 진입 원호 → 한 바퀴 → 후퇴 원호 → 보정 취소 → G13.1 → Z 여유. C축 모드 · 회전 공구 M 코드 · 회전수 · T 번호는 기계마다 달라 앞에 붙여 씁니다.",
            "G12.1 안의 C 는 각도가 아니라 극좌표 평면의 mm 이고, X 가 음수인 줄은 중심 너머 점입니다(제어기가 실제 X 와 C 각도로 바꿉니다).",
            "시뮬레이션은 줄마다 공구 중심이 지나는 길을 실제 이송 F 로 그립니다(기계 보정은 제어기 보정을 흉내 낸 길). 속도는 x1 = 실제 이송, 평면을 움직이지 않는 줄은 잠깐 머뭅니다. " +
                "싱글 블록을 켜면 줄 끝마다 멈추고 '계속' 으로 다음 줄입니다. 좌표 줄은 지금 줄의 줄 끝 지령값입니다.",
            "G코드 목록의 노란 띠가 지금 줄, 흐린 좌표는 앞 줄과 같아 프로그램에서 뺀 값(복사에는 없음), 초록은 주석입니다.",
        ),
    ),
    HelpSection(
        "경고",
        listOf(
            "파란 띠는 안내(값을 넣으면 계산 · 기계마다 다른 것), 노란 띠는 숫자는 나왔지만 확인할 것(살이 공구경보다 두꺼움 · 내측 코너 R 이 공구 반경보다 작음 · 꼭짓점이 소재 밖), " +
                "빨간 띠는 계산을 멈춘 것입니다(범위 밖 값 · W ≥ 외경 · 구멍 > W · 코너 R ≥ W/2 · 내측 공구경 ≥ W/2 · 공구가 구멍에 안 들어감). 빨간 띠가 가리키는 칸은 빨간 테두리로 보입니다.",
        ),
    ),
)
