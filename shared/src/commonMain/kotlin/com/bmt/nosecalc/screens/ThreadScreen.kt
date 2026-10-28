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
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Result
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ThreadTol
import com.bmt.nosecalc.calc.WorkMaterials
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NcFact
import com.bmt.nosecalc.screens.taper.TaperPage
import com.bmt.nosecalc.screens.taper.firstSentence
import com.bmt.nosecalc.screens.thread.AdvancedBar
import com.bmt.nosecalc.screens.thread.ChoiceField
import com.bmt.nosecalc.screens.thread.ChoiceGrid
import com.bmt.nosecalc.screens.thread.ChoiceRow
import com.bmt.nosecalc.screens.thread.ChoiceSheet
import com.bmt.nosecalc.screens.thread.G76Line
import com.bmt.nosecalc.screens.thread.Marked
import com.bmt.nosecalc.screens.thread.PassTable
import com.bmt.nosecalc.screens.thread.Pick
import com.bmt.nosecalc.screens.thread.ResultCell
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadLabel
import com.bmt.nosecalc.screens.thread.ThreadSection
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.chamferText
import com.bmt.nosecalc.screens.thread.classNote
import com.bmt.nosecalc.screens.thread.classText
import com.bmt.nosecalc.screens.thread.fieldHint
import com.bmt.nosecalc.screens.thread.g76Words
import com.bmt.nosecalc.screens.thread.kindText
import com.bmt.nosecalc.screens.thread.leadText
import com.bmt.nosecalc.screens.thread.materialText
import com.bmt.nosecalc.screens.thread.pitchRow
import com.bmt.nosecalc.screens.thread.pitchText
import com.bmt.nosecalc.screens.thread.plain
import com.bmt.nosecalc.screens.thread.sectionGeom
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
 * 나사 단면 → NC. 값이 바뀌면 바로 다시 푼다(계산 버튼 없음). 숫자칸은 한 묶음이라 키패드 '다음' 으로 이어 넣고, G76 줄의 테두리 칸을 눌러도
 * 그 칸의 키패드가 열린다(고급 설정 칸이면 접이를 편다). 결과가 화면 밖이면 아래 결과 띠에 시작점 · 끝점(G76 X Z).
 * G76 은 공구 노즈R 보정(G41/G42)을 쓰지 않아 보정 방식 스위치가 없다.
 * initial = 시험이 정한 처음 상태, editing = 처음부터 키패드를 열어 둘 칸, sheet = 처음부터 열어 둘 고르기 시트(스크린샷 시험용)
 */
@Composable
internal fun ThreadScreen(initial: ThreadState, editing: ThreadField? = null, sheet: Pick? = null) {
    val s = rememberSaveable(saver = ThreadState.Saver) { initial }
    val chain = rememberFieldChain(editing?.key)
    var pick by remember { mutableStateOf(sheet) }
    val app = LocalAppState.current
    val c = AppTheme.colors
    FeatureScaffold(Feature.THREAD, ThreadHelp) {
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
                AppCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                    Segmented(listOf("외경 가공 (OD)", "내경 가공 (ID)"), if (s.inner) 1 else 0, { s.switchInner(it == 1) }, Modifier.fillMaxWidth(), height = 54.dp)
                }
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
                            ChoiceField("나사 종류", kindText(s.kind), Modifier.weight(1.4f)) { pick = Pick.KIND }
                            ChoiceField("사이즈", s.spec?.size ?: "고르기", Modifier.weight(1f), empty = s.spec == null) { pick = Pick.SIZE }
                            ChoiceField(
                                if (s.kind == ThreadKind.UN) "산수 (TPI)" else "피치", s.spec?.let(::pitchText) ?: "—", Modifier.weight(1f),
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
                            ).forEach { (a, b) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                    field(a, Modifier.weight(1f))
                                    field(b, Modifier.weight(1f))
                                }
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
                    )
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
            Text(if (s.kind == ThreadKind.UN) "ASME B1.1" else "ISO 965", color = c.textMuted, fontSize = 12.5.sp)
        }
        ChoiceField(null, classText(s.shownClass), Modifier.width(150.dp), onClick = onClick)
    }
}

/** 고급 설정 안: 고른 급수의 한계 치수(사이즈를 고르면 다른 값이 없어도 보인다)와 목표 값 */
@Composable
private fun LimitsBox(s: ThreadState, sol: G76Solution?) {
    val c = AppTheme.colors
    val spec = s.spec ?: return
    val lim = sol?.limits ?: ThreadTol.limits(spec, s.shownClass)
    val crest = if (s.inner) "안지름" else "바깥지름"
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.padding(top = Space.fieldGap).fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${spec.label} ${lim.cls.label} 한계 치수", color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            "${if (s.kind == ThreadKind.UN) "ASME B1.1" else "ISO 965-1 · 965-2"} 의 공차로 피치경 · ${if (s.inner) "안지름" else "바깥지름"} 한계를 냅니다.",
            onDismiss,
        ) {
            ThreadClass.choices(s.kind, s.inner).forEach { cl ->
                ChoiceRow(classText(cl), classNote(cl), cl == s.shownClass) {
                    s.pickClass(cl)
                    onDismiss()
                }
            }
        }
        Pick.KIND -> ChoiceSheet("나사 종류", onDismiss = onDismiss) {
            ChoiceRow(kindText(ThreadKind.METRIC), "ISO 262 보통 · 가는 나사 M1 ~ M64 · 공차 ISO 965", s.kind == ThreadKind.METRIC) {
                s.switchKind(ThreadKind.METRIC)
                onDismiss()
            }
            ChoiceRow(kindText(ThreadKind.UN), "UNC · UNF #0 ~ 1-1/2 · 공차 ASME B1.1 (치수는 mm 로 바꿔 씀)", s.kind == ThreadKind.UN) {
                s.switchKind(ThreadKind.UN)
                onDismiss()
            }
        }
        Pick.SIZE -> {
            val sizes = ThreadTable.sizes(s.kind)
            ChoiceSheet("사이즈", kindText(s.kind), "고르면 ${if (s.kind == ThreadKind.UN) "UNC" else "보통 나사"} 피치가 먼저 들어갑니다 — 가는 나사는 피치 칸에서.", onDismiss) {
                ChoiceGrid(sizes, sizes.indexOf(s.spec?.size).takeIf { it >= 0 }) {
                    s.pickSize(sizes[it])
                    onDismiss()
                }
            }
        }
        Pick.PITCH -> {
            val spec = s.spec
            ChoiceSheet(if (s.kind == ThreadKind.UN) "산수 (TPI)" else "피치", spec?.size ?: "", onDismiss = onDismiss) {
                spec?.let { ThreadTable.pitches(s.kind, it.size) }.orEmpty().forEach { p ->
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
            "기록용 — 출처를 확인한 나사 절삭 속도 자료가 없어 회전수를 정하지 않습니다. 회전수는 인서트 카탈로그의 나사 절삭 조건으로 넣으세요.",
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
    }
}

/** 헤더 도움말 버튼의 사용법: 나사 고르기 · 값 넣기 · 고급 설정 · G76 두 줄과 분석 · 경고 */
internal val ThreadHelp = listOf(
    HelpSection(
        "나사 고르기",
        listOf(
            "맨 위에서 외경 가공(숫나사, OD) · 내경 가공(암나사, ID)을 고릅니다. 나사 종류(미터 M · 유니파이 UN) → 사이즈 → 피치(UN 은 산수 TPI)를 고르면 " +
                "규격 기본 치수와 공차로 목표 지름을 정합니다. 사이즈를 고르면 보통 나사(UNC)가 먼저 들어가고, 가는 나사(UNF)는 피치 칸에서 바꿉니다.",
            "공차 급수는 미터 숫나사 4h(정밀) · 6g(보통, 기본) · 6h · 8g(거침), 암나사 6H(기본) · 6G, UN 은 1A · 2A(기본) · 3A, 1B · 2B(기본) · 3B 입니다. " +
                "미터는 ISO 965 표 값(표가 없는 칸은 식 — 그때는 파란 안내), UN 은 ASME B1.1 의 식입니다.",
            "가공 소재는 기록용입니다. 출처를 확인한 나사 절삭 속도 자료가 없어 회전수를 정해 주지 않습니다 — 인서트 카탈로그의 나사 절삭 조건으로 회전수를 넣으세요.",
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
            "정삭 횟수 m(기본 2) · 최소 절입 Δdmin(기본 0.035) · 정삭 여유 d(기본 0.02)는 G76 첫 줄의 P 앞 두 자리 · Q · R 입니다. " +
                "시작 X 여유(기본 1, 반경)는 사이클 시작점을 지름에서 띄우는 거리입니다.",
            "사이즈를 고르면 아래에 그 급수의 한계 치수(피치경 · 산봉우리 지름 · 골 한계)와 목표 값이 보입니다.",
        ),
    ),
    HelpSection(
        "G76 두 줄과 분석",
        listOf(
            "FANUC 두 줄 형식입니다: G76 P(m)(r)(a) Q(Δdmin) R(d) / G76 X(골) Z(나사 끝) P(k) Q(Δd) F(리드). 곧은 나사라 둘째 줄의 R(i)는 쓰지 않습니다.",
            "테두리 칸은 눌러서 고칩니다(키패드). 테 없는 주황 값은 자동 계산 값이고, 누르면 어디서 나온 값인지 알려 줍니다. 넣어야 할 빈칸은 파란 '?' 입니다.",
            "P(k) · Q(Δd) · Q(Δdmin) 는 소수점 없이 μm(0.001 mm) 정수입니다. 0.0001 mm 단위(IS-C) 기계는 10배로 읽으니 그런 기계에는 쓰지 마세요.",
            "X 는 목표 피치경에서 날 단면(60° + 인선 R)의 폭이 피치선에서 P/2 가 되는 깊이로 정한 골 지름이고, k 는 목표 산봉우리 지름에서 X 까지(반경)입니다. " +
                "소재는 '나사 전 바깥지름(구멍 지름)' 으로 깎아 둔다고 봅니다 — 더 크면 첫 회가 Δd 보다 깊습니다.",
            "회차별 분석표는 회마다 누적 깊이 · 그 회 절입 · 날 끝 X 를, 나사 단면은 마지막 나사산과 거친 절입 깊이(가는 선)를 보입니다. NC 카드의 복사 단추로 프로그램을 복사합니다.",
        ),
    ),
    HelpSection(
        "경고",
        listOf(
            "파란 띠는 안내(값을 넣으면 계산 · 식으로 낸 공차 · 역가공 확인), 노란 띠는 숫자는 나왔지만 확인할 것(골이 규격 한계를 넘음 · 거친 절입 한 번), " +
                "빨간 띠는 계산을 멈춘 것입니다(범위 밖 값 · Z 순서와 방향이 반대 · 인선 R 이 너무 큼 · 정삭 여유 ≥ 산 높이 · 첫 절입 < 최소 절입 · 절입 1000회 넘음 · 내경 시작 X ≤ 0). " +
                "빨간 띠가 가리키는 칸은 빨간 테두리로 보입니다.",
        ),
    ),
)
