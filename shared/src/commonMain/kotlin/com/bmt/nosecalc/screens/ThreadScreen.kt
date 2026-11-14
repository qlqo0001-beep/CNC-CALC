package com.bmt.nosecalc.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.CompanyRolls
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Result
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.RollHeadCalc
import com.bmt.nosecalc.calc.RollHeadCatalog
import com.bmt.nosecalc.calc.RollTipGroup
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ThreadTol
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.calc.WorkMaterials
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NcFact
import com.bmt.nosecalc.screens.taper.TaperPage
import com.bmt.nosecalc.screens.taper.firstSentence
import com.bmt.nosecalc.screens.thread.AdvancedBar
import com.bmt.nosecalc.screens.thread.COMPANY_20UN
import com.bmt.nosecalc.screens.thread.ChoiceField
import com.bmt.nosecalc.screens.thread.ChoiceGrid
import com.bmt.nosecalc.screens.thread.ChoiceRow
import com.bmt.nosecalc.screens.thread.ChoiceSheet
import com.bmt.nosecalc.screens.thread.DieSpeedSheet
import com.bmt.nosecalc.screens.thread.DieStore
import com.bmt.nosecalc.screens.thread.DieThreadBody
import com.bmt.nosecalc.screens.thread.G76Line
import com.bmt.nosecalc.screens.thread.InsertField
import com.bmt.nosecalc.screens.thread.Marked
import com.bmt.nosecalc.screens.thread.PassTable
import com.bmt.nosecalc.screens.thread.Pick
import com.bmt.nosecalc.screens.thread.PipeSizeSheet
import com.bmt.nosecalc.screens.thread.PipeThreadBody
import com.bmt.nosecalc.screens.thread.ROLL_TIP_GROUPS
import com.bmt.nosecalc.screens.thread.ResultCell
import com.bmt.nosecalc.screens.thread.RollHeadSheet
import com.bmt.nosecalc.screens.thread.RollStore
import com.bmt.nosecalc.screens.thread.RollThreadBody
import com.bmt.nosecalc.screens.thread.ThreadFamily
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadLabel
import com.bmt.nosecalc.screens.thread.ThreadNc
import com.bmt.nosecalc.screens.thread.ThreadSection
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.ThreadTopCard
import com.bmt.nosecalc.screens.thread.chamferText
import com.bmt.nosecalc.screens.thread.classNote
import com.bmt.nosecalc.screens.thread.classText
import com.bmt.nosecalc.screens.thread.fieldHint
import com.bmt.nosecalc.screens.thread.g76Words
import com.bmt.nosecalc.screens.thread.insertSource
import com.bmt.nosecalc.screens.thread.insertText
import com.bmt.nosecalc.screens.thread.kindNote
import com.bmt.nosecalc.screens.thread.kindText
import com.bmt.nosecalc.screens.thread.leadText
import com.bmt.nosecalc.screens.thread.materialText
import com.bmt.nosecalc.screens.thread.pitchLabel
import com.bmt.nosecalc.screens.thread.pitchRow
import com.bmt.nosecalc.screens.thread.pitchText
import com.bmt.nosecalc.screens.thread.plain
import com.bmt.nosecalc.screens.thread.rollHeadRange
import com.bmt.nosecalc.screens.thread.rollTipGroupTitle
import com.bmt.nosecalc.screens.thread.rollTipsFull
import com.bmt.nosecalc.screens.thread.sectionGeom
import com.bmt.nosecalc.screens.thread.specTitle
import com.bmt.nosecalc.screens.thread.standardText
import com.bmt.nosecalc.screens.thread.unSizes
import com.bmt.nosecalc.screens.thread.threadAuto
import com.bmt.nosecalc.screens.thread.threadWarningText
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.ExpandableHint
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
import kotlin.math.floor

/** 나사 G76 */
@Composable
fun ThreadScreen() = ThreadScreen(ThreadState())

/**
 * 나사 G76 화면(원본 08): 외경/내경 → (값 부족 안내 · 멈춤) → '스레드 및 소재 설정'(공차 급수 · 종류 · 사이즈 · 피치 · 소재 · 방향 · Z 시작 · 회전수 ·
 * 목표 Z · 각도 · 면취량 → '고급 공차 및 인선 설정' 접이) → (확인 경고) → 'G76 파라미터 입력 및 분석'(두 줄 · 첫 절입 · 결과) → 회차별 분석표 →
 * 나사 단면 → NC(탭 G92 다회 · G76 두 줄, 처음은 G92 — G92 면 다듬질 블록 카드가 아래에). 값이 바뀌면 바로 다시 푼다(계산 버튼 없음). 숫자칸은 한 묶음(키패드 하나 · PC Tab 순서)이고, G76 줄의 테두리 칸을 눌러도
 * 그 칸의 키패드가 열린다(고급 설정 칸이면 접이를 편다). 결과가 화면 밖이면 아래 결과 띠에 시작점 · 끝점(G76 X Z).
 * G76 은 공구 노즈R 보정(G41/G42)을 쓰지 않아 보정 방식 스위치가 없다.
 * 나사 종류가 관용 테이퍼(PT · NPT)면 같은 틀에 다른 카드들(`thread/PipeCards.kt` — G92 테이퍼만)이, 곧은 나사 숫나사에서 가공 방식을 절삭 다이스(다이스 헤드)로 고르면
 * `thread/DieCards.kt` 의 카드들이(다이스 조건 회사 값은 [DieStore] — 앱 저장소), 전조 다이스로 고르면 `thread/RollCards.kt` 의 카드들이 선다
 * (전조 조건 회사 값은 [RollStore] — 앱 저장소).
 * initial = 시험이 정한 처음 상태, editing = 처음부터 키패드를 열어 둘 칸, sheet = 처음부터 열어 둘 고르기 시트(스크린샷 시험용)
 */
@Composable
internal fun ThreadScreen(initial: ThreadState, editing: ThreadField? = null, sheet: Pick? = null) {
    val s = rememberSaveable(saver = ThreadState.Saver) { initial }
    val chain = rememberFieldChain(editing?.key)
    var pick by remember { mutableStateOf(sheet) }
    val app = LocalAppState.current
    // 다이스 조건(회사 값): 화면을 열 때 앱 저장소에서 읽고, 고치면 바로 쓴다
    val die = remember(app.store) { DieStore(app.store) }
    // 전조 조건(회사 값): 다이스 조건과 같이 화면을 열 때 읽고, 고치면 바로 쓴다
    val roll = remember(app.store) { RollStore(app.store) }
    val c = AppTheme.colors
    FeatureScaffold(Feature.THREAD, ThreadHelp) {
        if (s.isPipe) {
            PipeThreadBody(s, chain) { pick = it }
            return@FeatureScaffold
        }
        if (s.isDie) {
            DieThreadBody(s, die, chain) { pick = it }
            return@FeatureScaffold
        }
        if (s.isRoll) {
            RollThreadBody(s, roll, chain) { pick = it }
            return@FeatureScaffold
        }
        val input = s.input()
        val result = remember(input) { G76Calc.calc(input) }
        val sol = result.solution
        val waiting = G76Warning.MISSING_INPUT in result.warnings
        val bad = s.bad()
        // 멈춘 까닭(빨강): 계산기의 멈춤 경고, 값이 모자라는 동안에도 범위 밖 칸은 바로
        val errors = when {
            sol != null -> emptyList()
            waiting -> if (bad.isNotEmpty()) listOf(G76Warning.BAD_INPUT) else emptyList()
            else -> result.warnings
        }
        val flagged = if (sol != null) emptySet() else s.flagged(errors)
        val advShown = s.advOpen || flagged.any { it.advanced }
        val dock = threadDock(s, result, errors, c)
        val preview = if (sol != null) sol.nc[1] else dock?.oneLine()
        val words = g76Words(s, sol)
        fun edit(f: ThreadField) {
            if (f.advanced) s.advOpen = true
            chain.open(f.key)
        }

        @Composable
        fun field(f: ThreadField, modifier: Modifier) = NumberField(
            f.label, s.value(f), { s.set(f, it) }, modifier,
            unit = f.unit, placeholder = s.placeholder(f), error = f in flagged,
            hint = fieldHint(f, s.inner), id = f.key, preview = preview,
        )

        FieldChainHost(chain) {
            TaperPage(dock, chain.sheetOpen) { anchor ->
                ThreadTopCard(s)
                if (waiting) WarningBanner(threadWarningText(G76Warning.MISSING_INPUT, s), level = WarnLevel.Info)
                if (errors.isNotEmpty()) {
                    // 결과 띠의 '칸 보기' 가 가는 곳(멈추면 이 빨간 띠)
                    Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                        errors.forEach { WarningBanner(threadWarningText(it, s), level = WarnLevel.Error) }
                    }
                }

                // 스레드 및 소재 설정(원본 08 ③~⑨)
                AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                    CardHeader(keepWords("스레드 및 소재 설정")) { Text("단위 mm · X 는 직경", style = AppType.caption, color = c.textMuted) }
                    ClassBox(s) { pick = Pick.CLASS }
                    Spacer(Modifier.height(Space.fieldGap))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            ChoiceField("나사 종류", kindText(s.family), Modifier.weight(1.4f)) { pick = Pick.KIND }
                            ChoiceField("사이즈", s.spec?.size ?: "고르기", Modifier.weight(1f), empty = s.spec == null) { pick = Pick.SIZE }
                            ChoiceField(
                                pitchLabel(s.family), s.spec?.let(::pitchText) ?: "—", Modifier.weight(1f),
                                empty = s.spec == null,
                            ) { pick = if (s.spec == null) Pick.SIZE else Pick.PITCH }
                        }
                        // 가공 소재 · 방향 한 줄(원본 08 ⑥). 좁은 폰 · 큰 글꼴(글꼴 배율로 나눈 폭 260dp 미만)이면 '정가공(우)' 가 잘리지 않게 두 줄로
                        BoxWithConstraints {
                            val narrow = maxWidth / LocalDensity.current.fontScale < 260.dp
                            val material = @Composable { m: Modifier ->
                                ChoiceField("가공 소재", s.material?.let(::materialText) ?: "고르지 않음", m, empty = s.material == null) { pick = Pick.MATERIAL }
                            }
                            val direction = @Composable { m: Modifier ->
                                Column(m) {
                                    ThreadLabel("방향")
                                    Spacer(Modifier.height(6.dp))
                                    Segmented(
                                        listOf("정가공(우)", "역가공(좌)"), if (s.dir == ThreadDir.FORWARD) 0 else 1,
                                        { s.dir = if (it == 0) ThreadDir.FORWARD else ThreadDir.REVERSE },
                                        Modifier.fillMaxWidth(), height = 52.dp, fontSize = 14,
                                    )
                                }
                            }
                            if (narrow) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                    material(Modifier.fillMaxWidth())
                                    direction(Modifier.fillMaxWidth())
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                                    material(Modifier.weight(.85f))
                                    direction(Modifier.weight(1.15f))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                            Marked(c.drawT1, 1f) { field(ThreadField.Z_START, it) }
                            field(ThreadField.RPM, Modifier.weight(1f))
                        }
                        vcText(s)?.let { Text(keepWords(it), Modifier.padding(start = 2.dp), style = AppType.caption, color = c.textMuted) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                            Marked(c.drawT2, 2f) { field(ThreadField.Z_END, it) }
                            ChoiceField("나사산 각도", "${s.shownAngle}°", Modifier.weight(1f)) { pick = Pick.ANGLE }
                            ChoiceField("면취량", chamferText(s.shownChamfer), Modifier.weight(1f)) { pick = Pick.CHAMFER }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val target = s.num(ThreadField.TARGET)?.takeIf { ThreadField.TARGET !in bad } ?: G76Calc.TARGET_DEFAULT
                    AdvancedBar(advShown, "목표 ${plain(target)}%") { s.advOpen = !advShown }
                    if (advShown) {
                        Spacer(Modifier.height(Space.fieldGap))
                        Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            listOf(
                                ThreadField.TARGET to ThreadField.TIP_R,
                                ThreadField.FINISH_COUNT to ThreadField.MIN_CUT,
                                ThreadField.ALLOW to ThreadField.X_CLEAR,
                            ).forEachIndexed { i, (a, b) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                    field(a, Modifier.weight(1f))
                                    field(b, Modifier.weight(1f))
                                }
                                // 인선 R 바로 아래 인서트(UN 20산 자료 — 미터는 자료가 없어 숨김). 이름이 길어 한 줄 전체
                                if (i == 0 && s.hasInsertData) InsertField(s, Modifier.fillMaxWidth()) { pick = Pick.INSERT }
                            }
                        }
                        LimitsBox(s, sol)
                        ExpandableHint(
                            "공차 목표 0% = 최대 실체, 100% = 최소 실체. 빈칸은 기본값.",
                            "공차 목표는 피치경 · 산봉우리 지름(${if (s.inner) "암나사 안지름" else "숫나사 바깥지름"})의 공차역 안 어디를 겨눌지입니다 — " +
                                "0% 는 ${if (s.inner) "작은" else "큰"} 쪽(최대 실체), 100% 는 반대쪽(최소 실체), 비우면 원본 화면 값 80%. " +
                                "날 끝이 닳으면 치수가 최대 실체 쪽으로 움직이므로 큰 % 에서 시작하면 마모 여유가 큽니다. " +
                                "인선 R 은 인서트 날 끝 R(카탈로그 값)이고, 비우면 규격 기본 깊이와 맞는 R(${if (s.inner) "H/8" else "H/6"})로 계산합니다. " +
                                "정삭 횟수 m · 최소 절입 Δdmin · 정삭 여유 d 는 G76 첫 줄의 P(앞 두 자리) · Q · R 입니다. " +
                                "시작 X 여유는 사이클 시작점을 ${if (s.inner) "안지름 최소에서 안쪽으로" else "바깥지름 최대에서 바깥으로"} 띄우는 반경 거리입니다.",
                            Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // 숫자와 함께 나온 확인 경고 · 늘 보이는 안내(역가공)
                if (sol != null) result.warnings.forEach { WarningBanner(threadWarningText(it, s, sol), level = WarnLevel.Warn) }
                if (s.dir == ThreadDir.REVERSE) {
                    WarningBanner(
                        "역가공(Z+)은 Z축 시작점보다 큰 Z 로 깎습니다(왼나사). FANUC 매뉴얼의 G76 그림은 모두 Z− 방향이라 이 방향은 매뉴얼 그림으로 확인하지 못했습니다 — " +
                            "첫 가공 전에 기계에서 한 번(공회전 · 단일 블록) 확인하세요.",
                        level = WarnLevel.Info,
                        collapsedText = "역가공 (Z+) — 기계에서 한 번 확인",
                    )
                }

                // G76 파라미터 입력 및 분석(원본 08 ⑩~⑫ + 둘째 줄 · 결과)
                AppCard(if (sol != null) anchor.fillMaxWidth() else Modifier.fillMaxWidth(), padding = Space.cardPad) {
                    CardHeader(keepWords("G76 파라미터 입력 및 분석"), keepWords("FANUC 두 줄 · P · Q 는 μm (0.001 mm) 정수"))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.threadAuto(), fontWeight = FontWeight.Bold)) { append(keepWords("주황 글은 자동 계산 값이며, ")) }
                            withStyle(SpanStyle(color = c.textBody, textDecoration = TextDecoration.Underline)) { append(keepWords("테두리 칸을 누르면")) }
                            withStyle(SpanStyle(color = c.textMuted)) { append(keepWords(" 고칠 수 있습니다. 각 재질에 알맞은 절삭 조건을 확인하세요.")) }
                        },
                        Modifier.padding(bottom = 10.dp),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    words.forEachIndexed { i, line ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        G76Line(line, chain, flagged, ::edit) { app.toast(it) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                        field(ThreadField.FIRST_CUT, Modifier.weight(1f))
                        ResultCell(
                            "절입 횟수",
                            sol?.let { "${it.passes.size - it.finishCount} + ${it.finishCount}" } ?: "—",
                            Modifier.weight(1f).padding(bottom = 4.dp),
                            sub = if (sol != null) "거친 + 정삭" else "첫 절입을 넣으면",
                        )
                    }
                    if (sol != null) ResultGrid(s, sol)
                }
                if (sol?.limits?.byFormula == true) {
                    WarningBanner(
                        "이 급수 · 사이즈의 공차 일부는 규격 표 자료가 없어 식(ISO 965-1)으로 계산했습니다 — 규격 표와 몇 μm 다를 수 있습니다.",
                        level = WarnLevel.Info,
                        collapsedText = "공차 일부는 식으로 계산 — 표와 몇 μm 차이",
                    )
                }

                if (sol != null) {
                    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                        CardHeader("회차별 분석", keepWords("거친 ${sol.passes.size - sol.finishCount}회 + 정삭 ${sol.finishCount}회 · 깊이 · 절입은 반경, X 는 직경"))
                        PassTable(sol.passes)
                        Text(
                            keepWords(
                                "n 번째 거친 절입의 누적 깊이 = Δd × √n (한 회가 Δdmin 보다 작으면 Δdmin), 산 높이 − 정삭 여유에서 멈춘 뒤 정삭 ${sol.finishCount}회 " +
                                    "(첫 회가 정삭 여유를 깎음). 소재를 목표 산봉우리 지름으로 깎아 둔 것을 기준으로 잽니다.",
                            ),
                            Modifier.padding(top = 8.dp),
                            style = AppType.caption,
                            color = c.textMuted,
                        )
                    }
                }

                AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                    CardHeader("나사 단면", keepWords("${if (s.inner) "내경" else "외경"} · 축을 지나는 단면 · 피치 기준 실제 비율"))
                    ThreadSection(sectionGeom(s.inner, sol))
                    Text(
                        keepWords(
                            if (sol != null) {
                                "굵은 선 = 마지막 정삭 뒤 나사산(골은 인선 R 원호). 오른쪽 홈의 가는 선 = 거친 절입 ${sol.passes.size - sol.finishCount}회의 날 끝 깊이."
                            } else {
                                "값을 넣으면 치수와 회차별 절입 깊이가 그림에 적힙니다."
                            },
                        ),
                        Modifier.padding(top = 6.dp),
                        style = AppType.caption,
                        color = c.textMuted,
                    )
                }

                if (sol != null) {
                    // NC 탭: G92 다회(처음 — 회사 흐름) · G76 두 줄. G92 면 아래에 다듬질 블록 카드를 따로
                    val g92 = s.nc == ThreadNc.G92
                    val tabs = @Composable {
                        Segmented(
                            listOf("G92 다회", "G76 두 줄"), if (g92) 0 else 1, { s.nc = if (it == 0) ThreadNc.G92 else ThreadNc.G76 },
                            Modifier.fillMaxWidth(), color = c.teal, height = 48.dp, fontSize = 15,
                        )
                    }
                    if (g92) {
                        NcCard(
                            sol.g92,
                            "G92 는 X 줄마다 나사를 한 번 깎고 시작점으로 돌아옵니다. X 줄은 회차별 분석표의 X 그대로이고(마지막 정삭 X = G76 X), 절입은 위 G76 카드의 " +
                                "첫 절입 Δd · 최소 절입 Δdmin · 정삭 여유 d · 정삭 횟수 m 으로 정합니다. X 로 곧게 절입하므로 나사산 각도 a · 면취량 r 은 쓰지 않습니다. " +
                                "G92 앞 M23 · 마지막 X 뒤 M24 는 나사 끝 빼기(면취) 켬 · 끔입니다 — 흔한 FANUC 계열 기계 M 정의라 기계마다 다를 수 있고, 빼는 길이는 기계 설정입니다. " +
                                "M24 줄은 G92 모드 안이라 제어기가 그 줄에서 마지막 X 를 한 번 더 지난 뒤 끝 빼기를 끕니다(시뮬레이터도 그렇게 그립니다). " +
                                "공구를 바꾼 뒤 Z20. 에서 시작 X 를 맞추고 Z 로 들어가며, 끝나면 Z 만 물러납니다(Z200.). " +
                                "G97 S 줄은 회전수를 넣었을 때만 씁니다. 주축 회전(M03/M04) · 공구 호출 · 냉각은 기계에 맞게 앞에 넣습니다.",
                            programName = "G92 나사 프로그램",
                            facts = listOf(
                                NcFact("회차 ${sol.passes.size}줄 = 분석표"),
                                NcFact("M23 끝 빼기 켬 · M24 끔"),
                                NcFact("M03/M04 · 공구 호출 없음"),
                            ),
                            top = tabs,
                        )
                        NcCard(
                            sol.g92Finish,
                            "${if (s.inner) "구멍 입구" else "외경"}의 버를 없앤 뒤 같은 나사 공구로 마지막 X 를 두 번 더 지나는 블록입니다. " +
                                "공구가 휘어 덜 깎인 것을 마저 깎습니다(스프링 패스). 위 프로그램과 따로, 버를 없애는 공구 블록 뒤에 넣습니다. " +
                                "M23 · M24 는 위 프로그램과 같은 자리입니다(M24 줄에서 마지막 X 를 한 번 더 지남).",
                            programName = "다듬질 블록",
                            facts = listOf(NcFact("마지막 X${Fmt.word(sol.passes.last().xDia)} 두 번")),
                            title = "다듬질 블록",
                        )
                    } else {
                        NcCard(
                            sol.program,
                            "G97 S 줄은 회전수를 넣었을 때만 씁니다(나사는 회전수 일정). G00 은 사이클 시작점이고, G76 은 끝나면 이 점으로 돌아옵니다. " +
                                "P(k) · Q(Δd) · 첫 줄 Q(Δdmin) 는 소수점 없이 최소 지령 단위(0.001 mm = μm) 정수입니다 — 0.0001 mm 단위(IS-C) 기계는 이 값을 10배로 읽으니 " +
                                "그런 기계에서는 쓰지 마세요. 주축 회전(M03/M04) · 공구 호출 · 냉각은 기계에 맞게 앞에 넣습니다.",
                            programName = "G76 나사 프로그램",
                            facts = listOf(
                                NcFact("P · Q 는 μm 정수 (0.001 mm 기계)", WarnLevel.Warn),
                                NcFact("M03/M04 · 공구 호출 없음"),
                                NcFact("끝나면 시작점으로"),
                            ),
                            top = tabs,
                        )
                    }
                }
                if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
            }
        }
    }
    pick?.let { ThreadSheet(it, s) { pick = null } }
}

/** 결과 띠 · 키패드 한 줄: 풀리면 시작점 · 끝점(G76 X Z)과 노란 경고 수, 멈췄으면 첫 경고의 첫 문장, 값이 모자라는 동안은 null */
private fun threadDock(s: ThreadState, result: G76Result, errors: List<G76Warning>, c: AppColors): DockStatus? {
    val sol = result.solution ?: return errors.firstOrNull()?.let { DockStatus.Error(firstSentence(threadWarningText(it, s))) }
    val zs = s.num(ThreadField.Z_START) ?: return null
    val ze = s.num(ThreadField.Z_END) ?: return null
    val rows = listOf(
        DockRow("시작", Fmt.fixed3(sol.startX), Fmt.fixed3(zs), c.drawT1, c.threadAuto()),
        DockRow("끝", Fmt.fixed3(sol.x), Fmt.fixed3(ze), c.drawT2, c.threadAuto()),
    )
    return if (result.warnings.isNotEmpty()) DockStatus.Warn(result.warnings.size, rows) else DockStatus.Ok(rows)
}

/** 회전수 칸 아래 한 줄: 호칭 지름에서의 절삭 속도 Vc = π·D·n/1000 (Cutting.vc) */
private fun vcText(s: ThreadState): String? {
    val n = s.num(ThreadField.RPM)?.takeIf { ThreadField.RPM !in s.bad() } ?: return null
    val spec = s.spec ?: return null
    return "Vc ≈ ${oneDecimal(Cutting.vc(n, spec.d))} m/min (호칭 Ø${Fmt.fixed3(spec.d)} · ${plain(n)} rpm) — 공구 카탈로그의 나사 절삭 속도와 견주세요"
}

/** 소수 첫째 자리(5 는 올림) */
internal fun oneDecimal(v: Double): String {
    val q = floor(v * 10 + 0.5 + 1e-9).toLong()
    return "${q / 10}.${q % 10}"
}

/** 공차 급수 상자(원본 08 ④): 이름 + 오른쪽 드롭다운 */
@Composable
private fun ClassBox(s: ThreadState, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("공차 급수", color = c.textBody, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(standardText(s.family), color = c.textMuted, fontSize = 12.5.sp)
        }
        ChoiceField(null, s.shownClass?.let(::classText) ?: "—", Modifier.width(150.dp), onClick = onClick)
    }
}

/** 고급 설정 안: 고른 급수의 한계 치수(사이즈를 고르면 다른 값이 없어도 보인다)와 목표 값 */
@Composable
private fun LimitsBox(s: ThreadState, sol: G76Solution?) {
    val c = AppTheme.colors
    val spec = s.spec ?: return
    val lim = sol?.limits ?: ThreadTol.limits(spec, s.shownClass ?: return)
    val crest = if (s.inner) "안지름" else "바깥지름"
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.padding(top = Space.fieldGap).fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${specTitle(spec)} ${lim.cls.label} 한계 치수", color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        @Composable
        fun line(name: String, min: Double, max: Double, target: Double?) {
            Row(verticalAlignment = Alignment.Top) {
                Text(name, Modifier.width(64.dp), color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ø${Fmt.fixed3(min)} ~ ${Fmt.fixed3(max)}", style = AppType.numSmall, color = c.textValue, maxLines = 1)
                    if (target != null) Text("목표 ${Fmt.fixed3(target)}", style = AppType.numSmall, color = c.threadAuto(), maxLines = 1)
                }
            }
        }
        line("피치경", lim.pitchDia.min, lim.pitchDia.max, sol?.pitchDia)
        line(crest, lim.crest.min, lim.crest.max, sol?.crestDia)
        Text(
            keepWords(if (s.inner) "골(바깥지름) 최소 Ø${Fmt.fixed3(lim.rootLimit)}" else "골지름 최대 Ø${Fmt.fixed3(lim.rootLimit)} (평평한 골)"),
            color = c.textMuted,
            fontSize = 12.sp,
        )
    }
}

/** G76 카드 아래 결과(주황 = 자동 계산 값): 목표 피치경 · 산봉우리 지름 · G76 X · k · 시작점 · 리드 · 인선 R · 분당 이송 */
@Composable
private fun ResultGrid(s: ThreadState, sol: G76Solution) {
    val c = AppTheme.colors
    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        resultCells(s, sol).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                row.forEach { (label, value, sub) -> ResultCell(label, value, Modifier.weight(1f), sub = sub) }
            }
        }
    }
}

/** 결과 격자의 칸(이름 · 값 · 아래 글) — 화면과 골든이 같이 쓴다 */
internal fun resultCells(s: ThreadState, sol: G76Solution): List<Triple<String, String, String>> {
    val zs = s.num(ThreadField.Z_START) ?: 0.0
    return listOf(
        Triple("목표 피치경", "Ø${Fmt.fixed3(sol.pitchDia)}", "공차 목표 ${plain(sol.target)}%"),
        Triple(if (s.inner) "나사 전 구멍 지름" else "나사 전 바깥지름", "Ø${Fmt.fixed3(sol.crestDia)}", "목표 산봉우리"),
        Triple(if (s.inner) "G76 X (바깥 쪽 골)" else "G76 X (골)", "X${Fmt.fixed3(sol.x)}", "나사 끝 Z${Fmt.nc(s.num(ThreadField.Z_END) ?: 0.0)}"),
        Triple("나사산 높이 k", Fmt.fixed3(sol.height), "반경 · P${floor(sol.height * 1000 + 0.5).toLong()}"),
        Triple("사이클 시작점", "X${Fmt.nc(sol.startX)} Z${Fmt.nc(zs)}", "G00 · 끝나면 여기로"),
        Triple("리드 F", leadText(sol.lead), s.spec?.label ?: ""),
        Triple("인선 R", Fmt.fixed3(sol.tipR), if (s.value(ThreadField.TIP_R).isEmpty()) "기본 (${if (s.inner) "H/8" else "H/6"})" else "넣은 값"),
        Triple("분당 이송", sol.feedPerMin?.let(::oneDecimal) ?: "—", if (sol.feedPerMin != null) "mm/min = 회전수 × 리드" else "회전수를 넣으면"),
    )
}

/** 고르기 시트들(원본의 드롭다운). 고르면 닫힌다 */
@Composable
private fun ThreadSheet(pick: Pick, s: ThreadState, onDismiss: () -> Unit) {
    when (pick) {
        Pick.CLASS -> ChoiceSheet(
            "공차 급수",
            if (s.inner) "암나사" else "숫나사",
            "${standardText(s.family, long = true)} 의 공차로 피치경 · ${if (s.inner) "안지름" else "바깥지름"} 한계를 냅니다.",
            onDismiss,
        ) {
            s.classChoices.forEach { cl ->
                ChoiceRow(classText(cl), classNote(cl), cl == s.shownClass) {
                    s.pickClass(cl)
                    onDismiss()
                }
            }
        }
        Pick.KIND -> ChoiceSheet("나사 종류", onDismiss = onDismiss) {
            ThreadFamily.entries.forEach { f ->
                ChoiceRow(kindText(f), kindNote(f), s.family == f) {
                    s.switchFamily(f)
                    onDismiss()
                }
            }
        }
        Pick.SIZE -> when (s.family) {
            ThreadFamily.METRIC -> {
                val sizes = ThreadTable.sizes(ThreadKind.METRIC)
                ChoiceSheet("사이즈", kindText(s.family), "고르면 보통 나사 피치가 먼저 들어갑니다 — 가는 나사는 피치 칸에서.", onDismiss) {
                    ChoiceGrid(sizes, sizes.indexOf(s.spec?.size).takeIf { it >= 0 }) {
                        s.pickSize(sizes[it])
                        onDismiss()
                    }
                }
            }
            ThreadFamily.UN -> ChoiceSheet(
                "사이즈",
                kindText(s.family),
                (if (s.isRoll) "전조: 맨 위 '회사 롤 규격' 을 누르면 그 크기의 20산 줄이 바로 들어갑니다. " else "") +
                    "계열을 고르고 크기를 고르면 그 계열의 산수가 들어갑니다 — 같은 크기의 다른 산수는 산수 칸에서. 20UN 과 겹치는 줄은 '3/4-20 UNEF (20UN)' 처럼 본래 계열 이름입니다.",
                onDismiss,
            ) { UnSizePicker(s, onDismiss) }
            ThreadFamily.PT -> PipeSizeSheet(s, PipeKind.PT, onDismiss)
            ThreadFamily.NPT -> PipeSizeSheet(s, PipeKind.NPT, onDismiss)
        }
        Pick.PITCH -> {
            val spec = s.spec
            ChoiceSheet(pitchLabel(s.family), spec?.size ?: "", onDismiss = onDismiss) {
                s.kind?.let { k -> spec?.let { ThreadTable.pitches(k, it.size) } }.orEmpty().forEach { p ->
                    val (text, sub) = pitchRow(p)
                    ChoiceRow(text, sub, p == spec) {
                        s.pickSpec(p)
                        onDismiss()
                    }
                }
            }
        }
        Pick.MATERIAL -> ChoiceSheet(
            "가공 소재",
            "ISO 피삭재 군",
            if (s.isDie) {
                "다이스 헤드: 다이스 조건의 절삭 속도 Vc(회사 값)를 소재마다 따로 저장하는 열쇠입니다 — 소재를 바꾸면 그 소재의 Vc 가 들어옵니다."
            } else if (s.isRoll) {
                "전조: 회사 회전수는 스테인리스(M) 기준입니다. 다른 소재를 고르면 Fette 추천이 없어 회전수를 넣어야 합니다. 고르지 않으면 스테인리스로 봅니다."
            } else {
                "기록용 — 출처를 확인한 나사 절삭 속도 자료가 없어 회전수를 정하지 않습니다. 회전수는 인서트 카탈로그의 나사 절삭 조건으로 넣으세요."
            },
            onDismiss,
        ) {
            ChoiceRow("고르지 않음", null, s.material == null) {
                s.material = null
                onDismiss()
            }
            WorkMaterials.groups.forEach { g ->
                val examples = g.examples.mapNotNull { it.ks }.take(3)
                ChoiceRow(materialText(g.group), g.covers + if (examples.isEmpty()) "" else " 예: ${examples.joinToString(" · ")}", s.material == g.group) {
                    s.material = g.group
                    onDismiss()
                }
            }
        }
        Pick.ANGLE -> ChoiceSheet(
            "나사산 각도 (날 끝 각도 a)",
            "G76 첫 줄 P 끝 두 자리",
            "FANUC 는 80 · 60 · 55 · 30 · 29 · 0 만 받습니다(매뉴얼 4.2.7). 나사 모양은 규격(60°) 그대로이고 이 값은 절입하는 방향만 바꿉니다. 미터 · UN 나사는 보통 60.",
            onDismiss,
        ) {
            ChoiceGrid(G76Calc.ANGLES.map { "$it°" }, G76Calc.ANGLES.indexOf(s.shownAngle), columns = 3) {
                s.angle = G76Calc.ANGLES[it]
                onDismiss()
            }
        }
        Pick.CHAMFER -> {
            val values = (0..20).toList()
            ChoiceSheet(
                "면취량 r",
                "G76 첫 줄 P 가운데 두 자리",
                "나사 끝의 빼기(면취) 길이 — 리드의 0.1 배 단위(FANUC 00 ~ 99 = 0.0 ~ 9.9 리드). 1.0P = 10.",
                onDismiss,
            ) {
                ChoiceGrid(values.map(::chamferText), values.indexOf(s.shownChamfer).takeIf { it >= 0 }, columns = 5) {
                    s.chamfer = values[it]
                    onDismiss()
                }
            }
        }
        Pick.INSERT -> ChoiceSheet(
            "인서트",
            "${if (s.inner) "내경" else "외경"} · ${s.insertChoices.firstOrNull()?.let { "${it.form.name} ${plain(it.tpi)}산" } ?: ""}",
            (if (s.isPipe) {
                "풀 프로파일 레이다운 인서트(IC 3/8\") — 이름만 남깁니다(관용 테이퍼는 풀 프로파일을 가정해 계산하고 인선 R 칸이 없습니다). PT 는 BSPT(같은 55° 꼴) 인서트입니다."
            } else {
                "풀 프로파일 레이다운 인서트(IC 3/8\"). 카탈로그에 날 끝 R 이 있으면 인선 R 칸에 넣고, 없으면 이름만 남깁니다(인선 R 빈칸 = 규격 기본 R)."
            }) + (if (s.inner) " 내경은 인서트를 받는 가장 작은 홀더의 최소 구멍을 함께 적습니다." else ""),
            onDismiss,
        ) {
            ChoiceRow("고르지 않음", null, s.shownInsert == null) {
                s.pickInsert(null)
                onDismiss()
            }
            val bore = s.insertBore
            s.insertChoices.forEach { i ->
                val r = i.tipR?.let { "날 끝 R ${Fmt.fixed3(it)} (${plain(i.tipRPerPitch!!)}P)" } ?: "날 끝 R 카탈로그에 없음"
                // 내경: 가장 작은 홀더의 최소 구멍 — 나사 구멍이 더 작으면 그 말도
                val mb = i.minBore?.let { m ->
                    " · 최소 구멍 Ø${Fmt.fixed3(m)}" + (bore?.takeIf { it < m - 1e-9 }?.let { b -> " (구멍 Ø${Fmt.fixed3(b)} 에 안 들어감)" } ?: "")
                } ?: ""
                ChoiceRow(insertText(i), "왼손 ${i.lhDesignation} · $r$mb · 출처 ${insertSource(i)}", i == s.shownInsert) {
                    s.pickInsert(i)
                    onDismiss()
                }
            }
        }
        Pick.DIE_SPEED -> DieSpeedSheet(s, onDismiss)
        Pick.ROLL_HEAD -> RollHeadSheet(s, onDismiss)
    }
}

/**
 * UN 사이즈 시트 안: 위 계열 묶음 고르기(UNC · UNF · UNEF · 20UN), 아래 그 묶음의 크기 격자. 20UN 은 자주 쓰는 다섯(사용자 결정 2026-11-05)을
 * 따로 위에 두고 나머지를 지름 순으로. 고른 칩은 지금 규격이 그 묶음 줄일 때만.
 * 전조 다이스면 맨 위에 '회사 롤 규격' 일곱([CompanyRolls.specs] — 누르면 그 20산 줄, 묶음도 20UN — 결정 D15). 바이트 · 절삭 다이스의 시트는 그대로
 */
@Composable
private fun UnSizePicker(s: ThreadState, onDismiss: () -> Unit) {
    if (s.isRoll) {
        val rolls = CompanyRolls.specs
        ThreadLabel("회사 롤 규격 (20산)")
        ChoiceGrid(rolls.map { it.size }, rolls.indexOf(s.spec).takeIf { it >= 0 }) {
            s.pickCompanyRoll(rolls[it])
            onDismiss()
        }
        Spacer(Modifier.height(10.dp))
    }
    val g = s.unGroup
    val chosen = s.spec?.takeIf { it in ThreadTable.group(g) }?.size
    Segmented(UnGroup.entries.map { it.label }, g.ordinal, { s.unGroup = UnGroup.entries[it] }, Modifier.fillMaxWidth(), height = 48.dp, fontSize = 15)
    Spacer(Modifier.height(10.dp))
    @Composable
    fun grid(sizes: List<String>) = ChoiceGrid(sizes, sizes.indexOf(chosen).takeIf { it >= 0 }) {
        s.pickSize(sizes[it])
        onDismiss()
    }
    when (g) {
        UnGroup.UNC, UnGroup.UNF, UnGroup.UNEF -> grid(unSizes(g))
        UnGroup.UN20 -> {
            val all = unSizes(g)
            ThreadLabel("자주 쓰는 크기")
            grid(all.take(COMPANY_20UN.size))
            Spacer(Modifier.height(6.dp))
            ThreadLabel("그 밖의 20UN 크기 (지름 순)")
            grid(all.drop(COMPANY_20UN.size))
        }
    }
}

/**
 * 헤더 도움말 버튼의 사용법: 나사 고르기 · 값 넣기 · 고급 설정 · G92 · G76 프로그램과 분석 · 관용 테이퍼 나사 · 전조 다이스 · 다이스 헤드 · 경고.
 * 전조 다이스 묶음의 머리 · 롤 표 · 요령은 회사 롤 표 · Fette 카탈로그 자료에서 만든다(처음 열 때 — 카탈로그를 읽으므로 lazy)
 */
internal val ThreadHelp: List<HelpSection> by lazy { listOf(
    HelpSection(
        "나사 고르기",
        listOf(
            "맨 위에서 외경 가공(숫나사, OD) · 내경 가공(암나사, ID)을 고릅니다. 나사 종류(미터 M · 유니파이 UN · 관용 테이퍼 PT · 미국 관용 테이퍼 NPT) → 사이즈 → " +
                "피치(UN 은 산수 TPI)를 고르면 규격 기본 치수와 공차로 목표 지름을 정합니다. 미터는 사이즈를 고르면 보통 나사가 먼저 들어가고 가는 나사는 피치 칸에서 바꿉니다. " +
                "UN 은 사이즈 시트 위에서 계열(UNC · UNF · UNEF · 20UN)을 고른 뒤 크기를 고릅니다 — 20UN 은 자주 쓰는 1/4 · 3/8 · 1/2 · 3/4 · 1 이 위에 있고, " +
                "UNC · UNF · UNEF 와 겹치는 줄은 '3/4-20 UNEF (20UN)' 처럼 본래 계열 이름입니다.",
            "관용 테이퍼(PT · NPT)는 G92 테이퍼로만 깎습니다 — 아래 '관용 테이퍼 나사 (PT · NPT)'. 미터 · UN 숫나사는 맨 위 가공 방식 " +
                "[바이트 | 전조 다이스 | 절삭 다이스] 에서 '전조 다이스' · '절삭 다이스' 를 고를 수 있습니다 — 아래 '전조 다이스' · '다이스 헤드'.",
            "공차 급수는 미터 숫나사 4h(정밀) · 6g(보통, 기본) · 6h · 8g(거침), 암나사 6H(기본) · 6G, UN 은 1A · 2A(기본) · 3A, 1B · 2B(기본) · 3B 입니다. " +
                "미터는 ISO 965 표 값(표가 없는 칸은 식 — 그때는 파란 안내), UN 은 ASME B1.1 의 식입니다.",
            "가공 소재는 바이트에서는 기록용입니다. 출처를 확인한 나사 절삭 속도 자료가 없어 회전수를 정해 주지 않습니다 — 인서트 카탈로그의 나사 절삭 조건으로 회전수를 넣으세요. " +
                "절삭 다이스에서는 회사 Vc 를 소재마다 저장하는 열쇠이고, 전조 다이스에서는 회전수의 근거입니다(회사 S · Fette 추천은 스테인리스만 — 비우면 스테인리스).",
        ),
    ),
    HelpSection(
        "값 넣기",
        listOf(
            "칸은 빈칸에서 시작합니다. 사이즈 · Z축 시작점 · 가공 목표점 (Z) · 첫 절입 Δd 를 넣으면 바로 계산합니다(파란 안내가 남은 것을 적습니다). " +
                "자리표시 '기본 …' 이 있는 칸은 비워 두면 그 값으로 계산합니다.",
            "Z축 시작점(주황 띠)은 사이클 시작 Z 로, 나사보다 앞(공기 중)에 둡니다 — 처음 몇 mm 는 서보 지연으로 리드가 맞지 않습니다. " +
                "가공 목표점 (Z)(청록 띠)은 나사 끝 Z 입니다. 두 색은 아래 결과 띠의 '시작' · '끝' 과 같습니다.",
            "방향: 정가공(우)은 시작 Z 가 목표보다 크고 척 쪽(Z−)으로 깎습니다 — 보통 바이트 · 정회전이면 오른나사. 역가공(좌)은 Z+ 로 깎습니다(왼나사). " +
                "F 는 늘 부호 없는 리드이고 방향은 Z 순서가 정합니다.",
            "회전수를 넣으면 프로그램 첫 줄에 G97 S 를 쓰고, 분당 이송(회전수 × 리드)과 호칭 지름의 Vc 를 보입니다. 비우면 S 줄 없이 씁니다.",
            "나사산 각도는 G76 의 공구 날 끝 각도 a(80 · 60 · 55 · 30 · 29 · 0), 면취량은 나사 끝 빼기 길이 r(0.1 리드 단위, 1.0P = 10)입니다.",
            "첫 절입 Δd 는 정해진 기본값이 없어 꼭 넣습니다. 회차별 분석표의 횟수를 보고 고칩니다(n 회 깊이 = Δd × √n).",
        ),
    ),
    HelpSection(
        "고급 공차 및 인선 설정",
        listOf(
            "공차 목표 %: 공차역 안 어디를 겨눌지 — 0% = 최대 실체(숫나사는 큰 쪽, 암나사는 작은 쪽), 100% = 최소 실체. 비우면 80%(원본 화면 값). " +
                "피치경과 산봉우리 지름(숫나사 바깥지름 · 암나사 안지름)에 같은 비율을 씁니다. 날이 닳으면 최대 실체 쪽으로 움직이므로 큰 % 가 마모 여유가 큽니다.",
            "인선 R: 인서트 날 끝 R(카탈로그 값). 비우면 규격 기본 깊이와 맞는 R — 숫나사 H/6(k = 0.6134P), 암나사 H/8(k = 0.5413P). R 이 크면 골이 덜 파여 노란 경고가 뜹니다.",
            "인서트: 내외 · 종류 · 산수에 맞는 카탈로그 인서트(VARDEX · Carmex 의 IC 3/8\" 풀 프로파일 — UN 20산 · PT(BSPT) · NPT, 예: 3ER20UN · 16 ER 20 UN)를 고릅니다. " +
                "카탈로그에 날 끝 R 이 있으면 인선 R 칸에 넣고, 없으면(UN 줄은 모두 없음) 이름만 남기고 인선 R 빈칸은 규격 기본 R 로 계산합니다 — 인서트에 적힌 R 이 있으면 넣으세요. " +
                "풀 프로파일은 산봉우리도 만들어 소재가 목표보다 크면 첫 회가 깊어집니다. 내경은 인서트를 받는 가장 작은 홀더의 최소 구멍(VARDEX 0.67 in · Carmex 16 mm)보다 " +
                "구멍이 작거나, 시작 X 여유가 바 뒤 틈((구멍 − 최소 구멍)/2)보다 크면 노란 안내가 뜹니다. 미터는 자료가 없어 칸이 없습니다.",
            "정삭 횟수 m(기본 2) · 최소 절입 Δdmin(기본 0.035) · 정삭 여유 d(기본 0.02)는 G76 첫 줄의 P 앞 두 자리 · Q · R 입니다. " +
                "시작 X 여유(기본 1, 반경)는 사이클 시작점을 지름에서 띄우는 거리입니다.",
            "사이즈를 고르면 아래에 그 급수의 한계 치수(피치경 · 산봉우리 지름 · 골 한계)와 목표 값이 보입니다.",
        ),
    ),
    HelpSection(
        "G92 · G76 프로그램과 분석",
        listOf(
            "NC 카드는 탭으로 G92 다회(처음) · G76 두 줄을 고릅니다. G92 는 분석표의 X 를 회마다 한 줄씩 쓰고(G92X…Z…F… 뒤는 X 만), 아래 다듬질 블록(마지막 X 두 번)은 " +
                "버를 없앤 뒤 따로 넣습니다. G92 는 X 로 곧게 절입해 나사산 각도 · 면취량을 쓰지 않습니다. G92 앞 M23 · 마지막 X 뒤 M24 는 나사 끝 빼기 켬 · 끔입니다" +
                "(흔한 FANUC 계열 기계 M 정의 — 기계마다 다를 수 있음).",
            "G76 은 FANUC 두 줄 형식입니다: G76 P(m)(r)(a) Q(Δdmin) R(d) / G76 X(골) Z(나사 끝) P(k) Q(Δd) F(리드). 곧은 나사라 둘째 줄의 R(i)는 쓰지 않습니다.",
            "테두리 칸은 눌러서 고칩니다(키패드). 테 없는 주황 값은 자동 계산 값이고, 누르면 어디서 나온 값인지 알려 줍니다. 넣어야 할 빈칸은 파란 '?' 입니다.",
            "P(k) · Q(Δd) · Q(Δdmin) 는 소수점 없이 μm(0.001 mm) 정수입니다. 0.0001 mm 단위(IS-C) 기계는 10배로 읽으니 그런 기계에는 쓰지 마세요.",
            "X 는 목표 피치경에서 날 단면(60° + 인선 R)의 폭이 피치선에서 P/2 가 되는 깊이로 정한 골 지름이고, k 는 목표 산봉우리 지름에서 X 까지(반경)입니다. " +
                "소재는 '나사 전 바깥지름(구멍 지름)' 으로 깎아 둔다고 봅니다 — 더 크면 첫 회가 Δd 보다 깊습니다.",
            "회차별 분석표는 회마다 누적 깊이 · 그 회 절입 · 날 끝 X 를, 나사 단면은 마지막 나사산과 거친 절입 깊이(가는 선)를 보입니다. NC 카드의 복사 단추로 프로그램을 복사합니다.",
        ),
    ),
    HelpSection(
        "관용 테이퍼 나사 (PT · NPT)",
        listOf(
            "PT(숫나사 R · 암나사 Rc — JIS B 0203)와 NPT(ANSI B2.1 — 지금 이름 ASME B1.20.1, 값은 NBS H28 1957 판 표)는 바이트 G92 테이퍼로만 깎습니다(G76 · 다이스 헤드 없음). 숫나사 · 암나사 둘 다 됩니다. " +
                "산수는 크기에 딸려 있고, 급수 대신 게이지 공차(± 산)가 보입니다.",
            "기준면 Z 는 공작물 끝면(숫나사 관 끝 · 암나사 입구, 빈칸 0)입니다. 여기서 규격 게이지 면을 잡고 끝 Z 의 골 X 와 테이퍼 I(시작 Z ~ 끝 Z 길이의 1/32 — " +
                "숫나사 −, 암나사 +)를 냅니다. 회차별 분석표의 X 는 끝 Z 쪽이고, 시작 Z 쪽 X = 끝 X + 2I 입니다. " +
                "Z축 시작점은 기준면보다 커야 하고(같으면 멈춤 — 시작점에서 X 로 급송 절입해 날이 끝 모서리를 묾), 기준면에서 2 × 피치 안이면 노란 경고입니다" +
                "(나사 시작의 가속 구간에 끝면 쪽 산이 들어감 — 회전수가 높으면 더 띄움).",
            "테이퍼 낱말은 처음 I 이고 NC 카드에서 R 로 바꿀 수 있습니다 — FANUC 0i 의 기본은 R 이고, I 는 Series 10/11 형식일 때입니다. " +
                "기계가 그 낱말을 받지 않으면 알람이 나거나 테이퍼 없이 곧게 깎이니, 첫 공작물은 싱글 블록 · 그래픽으로 테이퍼를 확인하세요. " +
                "I(R)는 첫 G92 줄에만 쓰고 둘째 줄부터는 X 만 씁니다(모달).",
            "고급 설정의 게이지 면 옮김(산)은 게이지 공차 안에서 나사를 옮겨 깎습니다(+ 는 조립 때 더 깊이). 풀 프로파일 인서트를 가정합니다(골 X = 끝 Z 피치경 ∓ h) — " +
                "인서트 칸에서 BSPT(PT) · NPT 인서트 이름을 고를 수 있습니다. 기준면 ~ 끝 Z 가 규격 최소 나사 길이(PT a + f · l, NPT L2 · L1 + L3)보다 짧으면 노란 경고가 뜹니다 — " +
                "길이는 끝 Z 까지라, G92 앞 M23(나사 끝 빼기)을 쓰면 끝의 빼기 길이(기계 설정 — FANUC 매개변수 No.5130)만큼 완전 나사가 짧으니 그만큼 더 깊게 잡습니다.",
            "소재는 나사 전 테이퍼(숫나사 바깥지름 · 암나사 안지름 — 결과의 '나사 전 …' 칸)를 앞 공정에서 깎아 둔다고 봅니다(절입표의 깊이가 그 테이퍼에서). " +
                "곧은 관 · 봉재 · 구멍에 바로 깎으면 숫나사는 기준면(작은) 쪽, 암나사는 입구 쪽 첫 회가 그 차만큼 깊어 인서트가 깨질 수 있습니다 — 결과 아래에 그 깊이가 나옵니다. " +
                "암나사의 시작 X 여유는 날 끝 기준이라, 날이 안쪽으로 빠지는 만큼 바 뒤가 맞은편 벽으로 다가갑니다(인서트를 고르면 바 뒤 틈과 견줌).",
        ),
    ),
    rollHelp(),
    HelpSection(
        "다이스 헤드",
        listOf(
            "미터 · UN 숫나사는 맨 위 가공 방식에서 '절삭 다이스'(절삭(체이서) 다이스 — 고정형 자동 열림 다이스 헤드, 공작물이 돌고 머리는 서 있음)를 고를 수 있습니다. " +
                "내경이나 PT · NPT 로 바꾸면 바이트로 돌아갑니다.",
            "프로그램은 깎는 구간만입니다: G97 S… · G0X0.Z20. · Z(시작) · G1Z(끝)F… · G4U(끝 멈춤) · G0Z(물러남). F 는 회전당 이송(G99)이고, 공구 호출 · M03 은 기계에 맞게 앞에 넣습니다.",
            "다이스 조건(절삭 속도 Vc · 이송 비율 · 끝 멈춤 · 물러남 Z · 플로트 · 체이서 목 산수)은 회사 값입니다 — 고치면 바로 저장되고 다음에 열어도 그대로입니다. " +
                "Vc 는 가공 소재마다 따로 저장되고 처음은 빈칸입니다. 회전수 칸에 넣으면 Vc 대신 그 회전수를 씁니다.",
            "처음 값은 이송 비율 0.96(F = 피치 × 0.96) · 끝 멈춤 1 초 · 물러남 Z150 입니다(사용자가 정한 처음 값). 카탈로그(Wagner · Landis)는 이송을 피치와 같게(비율 1) 씁니다 — " +
                "비율이 1 이 아니면 파란 안내가 한 줄 뜹니다.",
            "끝 당김 길이 = (1 − 비율)/비율 × (끝면 Z − 끝 Z): 비율 < 1 이면 체이서가 제 리드로 나아가 머리 앞쪽이 회전마다 (1 − 비율) × 피치씩 끌려 나간 길이로" +
                "(회전 수 = 길이 ÷ (비율 × 피치)), 머리의 플로트가 받아야 합니다(플로트를 넣으면 넘을 때 노란 경고). " +
                "체이서는 끝 멈춤 동안에도 더 들어가다가(멈춤 동안 이동 = S/60 × 멈춤 × 피치) 머리 앞쪽이 플로트(열림 행정)까지 끌려 나가야 열립니다 — " +
                "실제 나사 끝은 가장 많이 G1 끝보다 플로트만큼 척 쪽입니다(결과의 '체이서 끝' — 어깨 가까이 끝낼 때 확인). " +
                "끝 멈춤이 0 이거나 멈춤 동안 플로트에 못 미치면 닫힌 체이서를 급송으로 끌어내니 노란 경고가 뜹니다. 자동 열림 머리는 다음 공작물 전에 닫아 둡니다. " +
                "완전 나사 끝 = 끝 Z + 목 산수 × 피치(목 산수를 넣었을 때).",
            "카탈로그 Vc 는 Wagner 설명서 두 권의 범위(재질 글 — ISO 군 표시 없음)이고 견주기만 합니다 — 고른 줄 범위의 회전수 S 와, 지금 Vc 가 범위 밖인지를 칸 아래에 적습니다. " +
                "체이서 규격 표는 그 피치가 맞는 Landis 체이서 줄입니다" +
                "(체이서는 피치만 정하고 지름 범위는 홀더가 정함). 다이스 헤드 모델을 모르니 카탈로그는 일반 값으로 보고 회사 값을 넣으세요.",
        ),
    ),
    HelpSection(
        "경고",
        listOf(
            "파란 띠는 안내(값을 넣으면 계산 · 식으로 낸 공차 · 역가공 확인), 노란 띠는 숫자는 나왔지만 확인할 것(골이 규격 한계를 넘음 · 거친 절입 한 번), " +
                "빨간 띠는 계산을 멈춘 것입니다(범위 밖 값 · Z 순서와 방향이 반대 · 인선 R 이 너무 큼 · 정삭 여유 ≥ 산 높이 · 첫 절입 < 최소 절입 · 절입 1000회 넘음 · 내경 시작 X ≤ 0). " +
                "빨간 띠가 가리키는 칸은 빨간 테두리로 보입니다.",
            "다이스 헤드: 빨강 = 범위 밖 값 · Z 순서(시작 ≤ 끝 · 시작이 끝면 안 · 진입 Z20 이나 물러남 Z 보다 큰 시작 · 끝면 ≤ 끝), " +
                "노랑 = 이송 비율 > 1 · 끝 당김 길이 > 플로트 · 완전 나사 없음(목 길이 ≥ 나사 길이) · 끝 멈춤 동안 머리가 안 열림 · 시작이 끝면에서 피치 안, 파랑 = 카탈로그는 이송 = 피치.",
            "관용 테이퍼: 빨강 = 시작 Z ≤ 기준면(그 밖은 곧은 나사와 같음), 노랑 = 나사 길이가 규격 최소보다 짧음 · 시작 Z 가 기준면에서 2 × 피치 안 · 거친 절입 한 번. " +
                "내경 인서트를 고르면 구멍이 홀더 최소 구멍보다 작거나 시작 X 여유가 바 뒤 틈보다 클 때 노란 안내가 뜹니다.",
        ),
    ),
) }

/**
 * 도움말 '전조 다이스' 묶음: 고르기 · 머리 · 롤 표(회사 짝 · 카탈로그 지름 범위 · 열림 행정) · 전조 전 지름 읽는 법(NC 에 안 들어감 — 앞 공정 정삭 X) ·
 * 회전수 · 이송 사슬 · STS304 / STS316 · 결과와 경고 · 요령 전문(출처마다)
 */
private fun rollHelp(): HelpSection {
    val pairs = CompanyRolls.ALL.joinToString(", ") { r ->
        "${r.size}: " + r.heads.mapIndexed { i, h -> "${h.model} · ${h.roll}${if (i == 0 && r.heads.size > 1) "(처음)" else ""}" }.joinToString(" / ")
    }
    val heads = RollHeadCatalog.evoHeads
    val ranges = heads.joinToString(" · ") { "${it.key} ${rollHeadRange(it)}" }
    val opening = heads.groupBy { it.openingMovementMm }.entries.filter { it.key != null }
        .joinToString(", ") { (a, hs) -> "${hs.joinToString(" · ") { it.key }} ${plain(a!!)} mm" }
    val s316 = RollHeadCatalog.speed("1.4401")
    val s304 = RollHeadCatalog.speed("1.4301")
    fun span(lo: Double?, hi: Double?) = "${lo?.let(::plain) ?: "?"}–${hi?.let(::plain) ?: "?"}"
    val tips = (ROLL_TIP_GROUPS + RollTipGroup.OTHER).map { g ->
        "${rollTipGroupTitle(g)} — " + rollTipsFull(g).joinToString(" / ")
    }
    return HelpSection(
        "전조 다이스",
        listOf(
            "미터 · UN 숫나사는 맨 위 가공 방식에서 '전조 다이스'(LMT Fette 축방향 전조 머리 EVOline — 머리는 터렛에 서 있고 공작물이 돎)를 고를 수 있습니다. " +
                "사이즈를 아직 안 골랐으면 UN · 20UN 묶음으로 바뀌고(회사 롤은 모두 UN 20산), UN 사이즈 시트 맨 위의 '회사 롤 규격' 을 누르면 그 20산 줄이 바로 들어갑니다. " +
                "꼭 넣을 값은 사이즈 · 가공 목표점 둘입니다 — Z축 시작점 빈칸은 ${plain(RollHeadCalc.Z_START_DEFAULT)}, 끝면 Z 빈칸은 0. 내경이나 PT · NPT 로 바꾸면 바이트로 돌아갑니다.",
            "머리 · 롤: 회사 롤 짝이 먼저입니다 — $pairs. 그다음 지름 범위(카탈로그)에 드는 다른 EVOline 머리 — $ranges. " +
                "고를 것이 하나뿐이면 칸은 읽기만 합니다. 회사 롤이 없는 머리는 맞는 롤을 따로 확인하세요. 열림 행정 a(설명서 Tab. 8): $opening.",
            "'전조 전 지름 (앞 공정 정삭 X)' 카드의 큰 값은 이 프로그램(NC)에 들어가지 않습니다 — 앞 공정(바깥 정삭)의 X 에 쓰는 값입니다. " +
                "회사 값(회사 프로그램 원문에서 확인한 값만 — 소재 종류 자동단조 · 자동핵사마다) → Fette 추천 차례로 쓰고, 회전수도 이 지름으로 계산합니다. " +
                "Fette 추천 = 기본 급수(UN 2A · 미터 6g) 유효지름 범위 − ${plain(RollHeadCalc.BLANK_OFFSET)} mm(설명서 dV ≈ d2 − 0.03), 한 값은 그 가운데입니다. " +
                "전조 전 지름을 바꾸면 바깥지름은 그 3~5배로 바뀌고(설명서), 정한 지름은 ±0.015 mm 안으로 지킵니다(카탈로그). 셋째 칸은 전조 뒤 바깥지름의 규격 범위입니다.",
            "회전수: 넣은 값 → 회사 S(가공 소재가 스테인리스 M 이거나 비었고 회사 표 규격일 때, 소재 종류마다) → Fette 추천(고른 강종의 재질 표 속도 아래 끝, " +
                "n = 1000 × v / (π × 전조 전 지름)). 다른 소재는 Fette 표 줄을 가를 수 없어 회전수를 넣어야 합니다. " +
                "이송 비율(전조 조건 — 회사 값, 고치면 저장): 넣은 값 → 20산이면 회사 F${plain(CompanyRolls.FEED_20TPI)}(비율 ${plain(CompanyRolls.FEED_20TPI)}/1.27 ≈ 0.961) → " +
                "Fette ${plain(RollHeadCalc.FETTE_RATIO)}(피치보다 5 % 작게). 끝 멈춤 빈칸 ${plain(RollHeadCalc.DWELL_DEFAULT)} 초 · 물러남 Z 빈칸 ${plain(RollHeadCalc.RETRACT_Z_DEFAULT)}. " +
                "자리표시가 쓰일 값을 숫자로 보입니다('추천 …' · '회사 S…').",
            "STS316 · STS304: Fette 재질 표(F1–F3 설명서 Tab. 6)가 1.4401(STS316 에 해당) ${span(s316?.vMinMpm, s316?.vMaxMpm)}, " +
                "1.4301(STS304 에 해당) ${span(s304?.vMinMpm, s304?.vMaxMpm)} m/min 으로 갈라 적습니다(316 · 304 대응은 조사자 주 — 원문에 AISI 표기 없음). " +
                "가공 소재가 스테인리스이거나 비었을 때만 그 아래에서 고릅니다(처음 STS316).",
            "결과: 실제 전조 끝 = 끝 Z − 열림 행정 a(이송을 멈추면 머리가 a 만큼 더 나가며 열림), 전조 길이 = (끝면 Z − 끝 Z) + a. " +
                "회전수 · 비율을 처음 값과 다르게 넣으면 그 방향의 요령이 결과 위에 파랗게 뜹니다(출처 있는 글이 없으면 안 뜸). " +
                "빨강 = 범위 밖 값 · 맞는 머리 없음(고른 머리의 지름 범위 밖) · Z 순서(절삭 다이스와 같음), " +
                "노랑 = 이송 비율 > 1 · 넣은 회전수가 Fette 범위 밖 · 회사 짝이 카탈로그 지름 범위 밖 · 산수(피치)가 롤 목록 밖 · 전조 길이가 선택표 최대보다 김 · 끝 멈춤 0. " +
                "열린 머리는 다음 공작물 전에 닫습니다.",
        ) + tips.mapIndexed { i, t -> if (i == 0) "요령(LMT Fette 설명서 · 카탈로그 — 글마다 출처, 출처 없는 글은 넣지 않음). $t" else t },
    )
}
