package com.bmt.nosecalc.screens.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeResult
import com.bmt.nosecalc.calc.PipeSolution
import com.bmt.nosecalc.calc.PipeSpec
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.screens.oneDecimal
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NcFact
import com.bmt.nosecalc.screens.taper.TaperPage
import com.bmt.nosecalc.screens.taper.firstSentence
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.FieldChain
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.math.abs

/**
 * 관용 테이퍼 나사(PT · NPT) 화면 — 사용자 결정 2026-11-05: 바이트 G92 테이퍼로만 깎는다(다이스 · G76 없음), 숫나사 · 암나사 둘 다.
 * 외경/내경(PT 이름 R · Rc 가 따라감) → (파란 안내 · 빨간 멈춤) → '관용 테이퍼 나사 설정'(게이지 공차 상자 · 종류 · 사이즈 · 산수(읽기만) · Z 시작 · 회전수 ·
 * 끝 Z · 기준면 Z → '고급 설정' 접이: 게이지 면 옮김 · 정삭 횟수 · Δdmin · d · X 여유 + 게이지 표) → (노란 확인) → 'G92 절입 및 분석'(첫 절입 · 절입 횟수 · 결과 격자) →
 * 회차별 분석(시작 X = 끝 X + 2I) → 나사 단면(PT 55° 둥근 산 · 골, NPT 60° 평탄 — 테이퍼는 뺌) → NC(G92 테이퍼 하나 + I/R 고르기, 다듬질 블록 — G76 탭은 없다).
 * 계산은 [PipeThreadCalc], 칸 · 경고 글은 곧은 나사와 같은 [ThreadState] · [threadWarningText]. 틀(키패드 묶음 · 결과 띠)은 곧은 나사 화면과 같다
 */
@Composable
internal fun PipeThreadBody(s: ThreadState, chain: FieldChain, onPick: (Pick) -> Unit) {
    val kind = s.family.pipeKind ?: return
    val c = AppTheme.colors
    val input = s.pipeInput()
    val result = remember(input) { PipeThreadCalc.calc(input) }
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
    val dock = pipeDock(s, result, errors, c)
    // 키패드 결과 한 줄 = 테이퍼 낱말이 든 첫 G92 줄
    val preview = sol?.g92?.firstOrNull { it.startsWith("G92") } ?: dock?.oneLine()

    @Composable
    fun field(f: ThreadField, modifier: Modifier) = NumberField(
        f.label, s.value(f), { s.set(f, it) }, modifier,
        unit = f.unit, placeholder = s.placeholder(f), error = f in flagged,
        hint = fieldHint(f, s.inner, pipe = true), id = f.key, preview = preview,
    )

    FieldChainHost(chain) {
        TaperPage(dock, chain.sheetOpen) { anchor ->
            AppCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                Segmented(listOf("외경 가공 (OD)", "내경 가공 (ID)"), if (s.inner) 1 else 0, { s.switchInner(it == 1) }, Modifier.fillMaxWidth(), height = 54.dp)
            }
            if (waiting) WarningBanner(threadWarningText(G76Warning.MISSING_INPUT, s), level = WarnLevel.Info)
            if (errors.isNotEmpty()) {
                Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                    errors.forEach { WarningBanner(threadWarningText(it, s), level = WarnLevel.Error) }
                }
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("관용 테이퍼 나사 설정")) { Text("단위 mm · X 는 직경", style = AppType.caption, color = c.textMuted) }
                GaugeTolBox(s)
                Spacer(Modifier.height(Space.fieldGap))
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        ChoiceField("나사 종류", kindText(s.family), Modifier.weight(1.4f)) { onPick(Pick.KIND) }
                        // PT 는 이름 R · Rc 가 외경 · 내경을 따라간다 — 1/4 칸에 'Rc 1 1/2' 가 잘리지 않게 이름 줄에(값은 크기만)
                        val sizeLabel = when (kind) {
                            PipeKind.PT -> if (s.inner) "사이즈 (Rc)" else "사이즈 (R)"
                            PipeKind.NPT -> "사이즈"
                        }
                        ChoiceField(sizeLabel, s.pipe?.size ?: "고르기", Modifier.weight(1f), empty = s.pipe == null) { onPick(Pick.SIZE) }
                        // 산수는 크기에 딸려 있어 읽기만
                        ChoiceField(pitchLabel(s.family), s.pipe?.let { "${plain(it.tpi)} 산" } ?: "—", Modifier.weight(1f), empty = s.pipe == null, onClick = null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                        Marked(c.drawT1, 1f) { field(ThreadField.Z_START, it) }
                        field(ThreadField.RPM, Modifier.weight(1f))
                    }
                    pipeVcText(s)?.let { Text(keepWords(it), Modifier.padding(start = 2.dp), style = AppType.caption, color = c.textMuted) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                        Marked(c.drawT2, 1f) { field(ThreadField.Z_END, it) }
                        field(ThreadField.Z_REF, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                val shift = s.num(ThreadField.GAUGE_SHIFT)?.takeIf { ThreadField.GAUGE_SHIFT !in bad } ?: 0.0
                AdvancedBar(advShown, "옮김 ${plain(shift)}산", title = "고급 설정") { s.advOpen = !advShown }
                if (advShown) {
                    Spacer(Modifier.height(Space.fieldGap))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        listOf(
                            ThreadField.GAUGE_SHIFT to ThreadField.FINISH_COUNT,
                            ThreadField.MIN_CUT to ThreadField.ALLOW,
                        ).forEach { (a, b) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                field(a, Modifier.weight(1f))
                                field(b, Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                            field(ThreadField.X_CLEAR, Modifier.weight(1f))
                            Spacer(Modifier.weight(1f))
                        }
                        // 인서트: 이름 · 풀 프로파일 표시만(인선 R 칸이 없다 — 풀 프로파일 가정)
                        InsertField(s, Modifier.fillMaxWidth()) { onPick(Pick.INSERT) }
                    }
                    GaugeTableBox(s, sol)
                    ExpandableHint(
                        "게이지 면 옮김은 산 단위 · 게이지 공차 안. 빈칸은 기본값.",
                        "게이지 면 옮김 s 는 나사를 게이지 면에서 몇 산 옮겨 깎을지입니다 — + 는 조립 때 더 깊이 들어가는 쪽" +
                            "(${if (s.inner) "암나사가 지름 s·P/16 굵어짐" else "숫나사가 지름 s·P/16 가늘어짐"}), 게이지 공차(표의 ± 산)를 넘으면 멈춥니다. " +
                            "정삭 횟수 m · 최소 절입 Δdmin · 정삭 여유 d 는 곧은 나사 G92 와 같은 절입표 규칙입니다(n 회 깊이 = Δd × √n). " +
                            "시작 X 여유는 사이클 시작점을 끝 Z 의 ${if (s.inner) "안지름에서 안쪽으로" else "바깥지름에서 바깥으로"} 띄우는 반경 거리입니다 — " +
                            "나사 안에서 ${if (s.inner) "가장 가는" else "가장 굵은"} 곳이라 테이퍼 전체에서 공구가 닿지 않습니다. " +
                            "인서트는 산봉우리까지 함께 만드는 풀 프로파일을 가정합니다(골 X = 끝 Z 피치경 ∓ h).",
                        Modifier.padding(top = 4.dp),
                    )
                }
            }

            // 숫자와 함께 나온 확인 경고(나사 길이가 짧음 · 거친 절입 한 번)
            if (sol != null) result.warnings.forEach { WarningBanner(threadWarningText(it, s, pipe = sol), level = WarnLevel.Warn) }

            AppCard(if (sol != null) anchor.fillMaxWidth() else Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("G92 절입 및 분석"), keepWords("관용 테이퍼 1/16 · 깊이 · 절입은 반경, X 는 직경"))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                    field(ThreadField.FIRST_CUT, Modifier.weight(1f))
                    ResultCell(
                        "절입 횟수",
                        sol?.let { "${it.passes.size - it.finishCount} + ${it.finishCount}" } ?: "—",
                        Modifier.weight(1f).padding(bottom = 4.dp),
                        sub = if (sol != null) "거친 + 정삭" else "첫 절입을 넣으면",
                    )
                }
                if (sol != null) {
                    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pipeResultCells(s, sol).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                row.forEach { (label, value, sub) -> ResultCell(label, value, Modifier.weight(1f), sub = sub) }
                            }
                        }
                    }
                }
            }

            if (sol != null) {
                AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                    CardHeader("회차별 분석", keepWords("거친 ${sol.passes.size - sol.finishCount}회 + 정삭 ${sol.finishCount}회 · 깊이 · 절입은 반경, X 는 직경"))
                    PassTable(sol.passes)
                    Text(
                        keepWords(
                            "X 는 끝 Z 쪽의 날 끝 X 입니다 — 시작 Z 쪽 X = 끝 X + 2${s.taperWord} (2${s.taperWord} = ${Fmt.word(2 * sol.taper)}). 누적 깊이 = Δd × √n (한 회가 Δdmin 보다 작으면 Δdmin), 산 높이 − 정삭 여유에서 멈춘 뒤 정삭 ${sol.finishCount}회.",
                        ),
                        Modifier.padding(top = 8.dp),
                        style = AppType.caption,
                        color = c.textMuted,
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader("나사 단면", keepWords("${if (s.inner) "내경" else "외경"} · 끝 Z 의 축 단면 · 피치 기준 실제 비율"))
                ThreadSection(pipeSectionGeom(kind, s.inner, s.pipe, sol))
                Text(
                    keepWords(
                        (if (sol != null) "굵은 선 = 마지막 정삭 뒤 나사산. 오른쪽 홈의 가는 선 = 거친 절입 ${sol.passes.size - sol.finishCount}회의 날 끝 깊이. " else "값을 넣으면 치수와 회차별 절입 깊이가 그림에 적힙니다. ") +
                            when (kind) {
                                PipeKind.PT -> "55° · 산봉우리와 골은 둥글기 r(JIS B 0203)."
                                PipeKind.NPT -> "60° · 산봉우리와 골은 평탄(산 높이 0.8P)."
                            } + " 1/16 테이퍼는 그림에서 뺐습니다.",
                    ),
                    Modifier.padding(top = 6.dp),
                    style = AppType.caption,
                    color = c.textMuted,
                )
            }

            if (sol != null) {
                val w = s.taperWord
                val words = @Composable {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ThreadLabel("테이퍼 낱말", Modifier.weight(1f))
                            Segmented(
                                PipeThreadCalc.TAPER_WORDS.map { it.toString() }, PipeThreadCalc.TAPER_WORDS.indexOf(w).coerceAtLeast(0),
                                { s.taperWord = PipeThreadCalc.TAPER_WORDS[it] }, Modifier.width(150.dp), color = c.teal, height = 48.dp, fontSize = 15,
                            )
                        }
                        Text(
                            keepWords("처음 값은 I. FANUC 0i 의 기본은 R 이고, I 는 Series 10/11 형식일 때입니다 — 기계 설명서로 확인하세요."),
                            Modifier.padding(top = 4.dp),
                            style = AppType.caption,
                            color = c.textMuted,
                        )
                    }
                }
                NcCard(
                    sol.g92,
                    "관용 테이퍼 나사는 G92 로만 깎습니다(G76 은 쓰지 않음). G92 는 X 줄마다 나사를 한 번 깎고 시작점으로 돌아옵니다. X 줄은 회차별 분석표의 X(끝 Z 쪽) 그대로이고, " +
                        "첫 G92 줄의 $w 는 사이클 시작 Z 쪽 반경 − 끝 반경 = (시작 Z − 끝 Z)/32 입니다(${if (s.inner) "암나사 +" else "숫나사 −"}). " +
                        "$w 는 X · Z 처럼 모달이라 둘째 줄부터는 X 만 씁니다. " +
                        "G92 앞 M23 · 마지막 X 뒤 M24 는 나사 끝 빼기 켬 · 끔입니다 — 흔한 FANUC 계열 기계 M 정의라 기계마다 다를 수 있습니다. " +
                        "공구를 바꾼 뒤 Z20. 에서 시작 X 를 맞추고 Z 로 들어가며, 끝나면 Z 만 물러납니다(Z200.). " +
                        "G97 S 줄은 회전수를 넣었을 때만 씁니다. 주축 회전(M03/M04) · 공구 호출 · 냉각은 기계에 맞게 앞에 넣습니다.",
                    programName = "G92 테이퍼 나사 프로그램",
                    facts = listOf(
                        NcFact("회차 ${sol.passes.size}줄 = 분석표"),
                        NcFact("$w 는 첫 줄만 (모달)"),
                        NcFact("관용 테이퍼 나사는 G92 로만"),
                        NcFact("M03/M04 · 공구 호출 없음"),
                    ),
                    top = words,
                )
                NcCard(
                    sol.g92Finish,
                    "${if (s.inner) "구멍 입구" else "외경"}의 버를 없앤 뒤 같은 나사 공구로 마지막 X 를 두 번 더 지나는 블록입니다(스프링 패스). " +
                        "첫 G92 줄의 $w 는 위 프로그램과 같습니다. M23 · M24 는 위 프로그램과 같은 자리입니다(M24 줄에서 마지막 X 를 한 번 더 지남).",
                    programName = "다듬질 블록",
                    facts = listOf(NcFact("마지막 X${Fmt.word(sol.passes.last().xDia)} 두 번")),
                    title = "다듬질 블록",
                )
            }
            if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
        }
    }
}

/** 관용 테이퍼 사이즈 시트: 그 종류의 크기 격자(산수는 크기에 딸림) */
@Composable
internal fun PipeSizeSheet(s: ThreadState, kind: PipeKind, onDismiss: () -> Unit) {
    val list = PipeTable.of(kind)
    val sizes = list.map { it.size }
    val note = when (kind) {
        PipeKind.PT -> "JIS B 0203 의 16 크기 — 이름은 외경(숫나사) R · 내경(암나사) Rc 로 따라 바뀝니다. 산수는 크기에 딸려 있습니다."
        PipeKind.NPT -> "ANSI B2.1 (ASME B1.20.1) 의 1/16 ~ 4 — 산수는 크기에 딸려 있습니다."
    }
    ChoiceSheet("사이즈", kindText(s.family), note, onDismiss) {
        ChoiceGrid(sizes, sizes.indexOf(s.pipe?.size).takeIf { it >= 0 }) {
            s.pickPipe(list[it])
            onDismiss()
        }
    }
}

/** 결과 띠 · 키패드 한 줄: 풀리면 시작점 · 끝점(끝 골 X · 끝 Z)과 노란 경고 수, 멈췄으면 첫 경고의 첫 문장, 값이 모자라는 동안은 null */
private fun pipeDock(s: ThreadState, result: PipeResult, errors: List<G76Warning>, c: AppColors): DockStatus? {
    val sol = result.solution ?: return errors.firstOrNull()?.let { DockStatus.Error(firstSentence(threadWarningText(it, s))) }
    val zs = s.num(ThreadField.Z_START) ?: return null
    val ze = s.num(ThreadField.Z_END) ?: return null
    val rows = listOf(
        DockRow("시작", Fmt.fixed3(sol.startX), Fmt.fixed3(zs), c.drawT1, c.threadAuto()),
        DockRow("끝", Fmt.fixed3(sol.x), Fmt.fixed3(ze), c.drawT2, c.threadAuto()),
    )
    return if (result.warnings.isNotEmpty()) DockStatus.Warn(result.warnings.size, rows) else DockStatus.Ok(rows)
}

/** 회전수 칸 아래 한 줄: 게이지 면 바깥지름에서의 절삭 속도 Vc = π·D·n/1000 */
private fun pipeVcText(s: ThreadState): String? {
    val n = s.num(ThreadField.RPM)?.takeIf { ThreadField.RPM !in s.bad() } ?: return null
    val spec = s.pipe ?: return null
    val d = spec.gaugePitch + spec.h
    return "Vc ≈ ${oneDecimal(Cutting.vc(n, d))} m/min (게이지 면 바깥지름 Ø${Fmt.fixed3(d)} · ${plain(n)} rpm) — 공구 카탈로그의 나사 절삭 속도와 견주세요"
}

/** 결과 격자의 칸(이름 · 값 · 아래 글) — 화면과 골든이 같이 쓴다 */
internal fun pipeResultCells(s: ThreadState, sol: PipeSolution): List<Triple<String, String, String>> {
    val zs = s.num(ThreadField.Z_START) ?: 0.0
    val ze = s.num(ThreadField.Z_END) ?: 0.0
    val w = s.taperWord
    val hRule = when (s.family.pipeKind) {
        PipeKind.PT -> "0.640327P"
        PipeKind.NPT, null -> "0.8P"
    }
    return listOf(
        Triple(if (s.inner) "끝 골 X (바깥 쪽)" else "끝 골 X", "X${Fmt.fixed3(sol.x)}", "나사 끝 Z${Fmt.nc(ze)}"),
        Triple("테이퍼 $w", "$w${Fmt.word(sol.taper)}", "반경 · 길이 ÷ 32"),
        Triple("사이클 시작점", "X${Fmt.nc(sol.startX)} Z${Fmt.nc(zs)}", "G00 · 회마다 여기로"),
        Triple("나사산 높이 k", Fmt.fixed3(sol.height), "반경 = h ($hRule)"),
        Triple(if (s.inner) "나사 전 구멍 지름" else "나사 전 바깥지름", "Ø${Fmt.fixed3(sol.crestAtRef)} → ${Fmt.fixed3(sol.crestAtEnd)}", "기준면 → 끝 Z"),
        Triple("리드 F", leadText(sol.lead), s.pipe?.name(s.inner) ?: ""),
        Triple("나사 길이", Fmt.fixed3(sol.threadLen), "규격 최소 ${Fmt.fixed3(sol.needLen)}"),
        Triple("분당 이송", sol.feedPerMin?.let(::oneDecimal) ?: "—", if (sol.feedPerMin != null) "mm/min = 회전수 × 리드" else "회전수를 넣으면"),
    )
}

/**
 * 게이지 표 상자의 줄(이름 · 값) — 화면과 골든이 같이 쓴다. 크기를 고르면 다른 값이 없어도 보인다(게이지 면 Z 는 풀린 뒤, 그 전에는 '기준면 − a' 꼴).
 * PT 는 기준 길이 a · 접합 여유 f · 암나사 유효 나사부 l, NPT 는 L1 손 조임 · L2 유효 나사 · L3 렌치 조임. 출처는 [gaugeSource]
 */
internal fun gaugeRows(s: ThreadState, sol: PipeSolution?): List<Pair<String, String>> {
    val spec = s.pipe ?: return emptyList()
    val tol = if (s.inner) spec.tolTurnsInner else spec.tolTurns
    val shift = s.num(ThreadField.GAUGE_SHIFT)?.takeIf { ThreadField.GAUGE_SHIFT !in s.bad() } ?: 0.0
    val lengths = when (spec.kind) {
        PipeKind.PT -> listOf(
            "기준 길이 a" to Fmt.fixed3(spec.gaugeLen),
            "접합 여유 f" to Fmt.fixed3(spec.extLen - spec.gaugeLen),
            "암나사 유효 나사부 l" to Fmt.fixed3(spec.intLen),
        )
        PipeKind.NPT -> listOf(
            "L1 손 조임" to Fmt.fixed3(spec.gaugeLen),
            "L2 유효 나사" to Fmt.fixed3(spec.extLen),
            "L3 렌치 조임" to Fmt.fixed3(spec.intLen - spec.gaugeLen),
        )
    }
    val face = sol?.let { "Z${Fmt.nc(it.gaugeZ)}" } ?: if (s.inner) "기준면" else "기준면 − ${if (spec.kind == PipeKind.PT) "a" else "L1"}"
    val need = sol?.needLen ?: ((if (s.inner) spec.intLen else spec.extLen) + shift * spec.pitch)
    return listOf(
        (if (s.inner) "게이지 면 (입구 면)" else "게이지 면") to face,
        "게이지 피치경" to "Ø${Fmt.fixed3(spec.gaugePitch)}",
    ) + listOfNotNull(sol?.takeIf { abs(it.gaugePitch - spec.gaugePitch) > 1e-9 }?.let { "옮긴 피치경" to "Ø${Fmt.fixed3(it.gaugePitch)}" }) + listOf(
        "게이지 바깥지름" to "Ø${Fmt.fixed3(spec.gaugePitch + spec.h)}",
    ) + lengths + listOf(
        "게이지 공차 (축)" to "±${turnsText(tol)}산 = ±${Fmt.fixed3(tol * spec.pitch)}",
        "최소 나사 길이 ${needLenName(s)}" to Fmt.fixed3(need),
    )
}

/** 게이지 표의 출처 — PT JIS B 0203:1999 부표1(3 1/2 은 부속서1 부표1), NPT NBS H28 (1957) Part II 표 VII.2(= ANSI B2.1) */
internal fun gaugeSource(spec: PipeSpec): String = when (spec.kind) {
    PipeKind.PT -> if (spec.size == "3 1/2") "JIS B 0203:1999 부속서1 부표1" else "JIS B 0203:1999 부표1"
    PipeKind.NPT -> "NBS Handbook H28 (1957) Part II 표 VII.2 (= ANSI B2.1)"
}

/** 공차 급수 상자 자리: 게이지 공차(관용 테이퍼에는 급수가 없다) — 이름 · 규격, 오른쪽에 ± 산 */
@Composable
private fun GaugeTolBox(s: ThreadState) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val spec = s.pipe
    val letter = when (s.family.pipeKind) {
        PipeKind.PT -> if (s.inner) " · 기준경 위치 c" else " · 기준경 위치 b"
        PipeKind.NPT, null -> " · 손 조임 면"
    }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("게이지 공차", color = c.textBody, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(keepWords(standardText(s.family) + letter), color = c.textMuted, fontSize = 12.5.sp)
        }
        Text(
            spec?.let { "±${turnsText(if (s.inner) it.tolTurnsInner else it.tolTurns)} 산" } ?: "크기를 고르면",
            Modifier.padding(start = 8.dp),
            color = if (spec != null) c.textValue else c.textHint,
            fontFamily = AppTheme.sans,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 고급 설정 안: 고른 크기의 게이지 표(규격 값 · 출처) */
@Composable
private fun GaugeTableBox(s: ThreadState, sol: PipeSolution?) {
    val c = AppTheme.colors
    val spec = s.pipe ?: return
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.padding(top = Space.fieldGap).fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${spec.name(s.inner)} 게이지 표 (mm)", color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        // 이름은 어절에서 줄을 바꾸고, 값(숫자 위주)은 한 줄 — 좁은 폰 · 큰 글꼴에서 값이 갈라지지 않게
        gaugeRows(s, sol).forEach { (name, value) ->
            Row(verticalAlignment = Alignment.Top) {
                Text(keepWords(name), Modifier.weight(1f), color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text(value, Modifier.padding(start = 8.dp), style = AppType.numSmall, color = c.textValue, textAlign = TextAlign.End, maxLines = 1)
            }
        }
        Text(keepWords("출처: ${gaugeSource(spec)}"), style = AppType.caption, color = c.textMuted)
    }
}
