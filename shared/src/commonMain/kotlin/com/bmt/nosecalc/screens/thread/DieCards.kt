package com.bmt.nosecalc.screens.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieHeadCatalog
import com.bmt.nosecalc.calc.DieResult
import com.bmt.nosecalc.calc.DieSolution
import com.bmt.nosecalc.calc.DieSpeed
import com.bmt.nosecalc.calc.DieWarning
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSpec
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
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.theme.AppColors
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToLong

/**
 * 다이스 헤드 나사 화면 — 사용자 결정 2026-11-05: 고정형 자동 열림(공작물이 돌고 머리는 서 있다), M · UN 숫나사만, 나사 화면 안의 가공 방식.
 * 외경/내경 + 가공 방식(맨 위 카드) → (파란 안내 · 빨간 멈춤) → '다이스 헤드 설정'(종류 · 사이즈 · 피치 · 가공 소재 · Z 시작 · 회전수(직접) · Z 끝 · 끝면 Z) →
 * '다이스 조건'(회사 값 — Vc(소재마다) + 카탈로그 줄 · 이송 비율 · 끝 멈춤 · 물러남 Z · 플로트 · 목 산수, 고치면 바로 [DieStore] 에 저장) → (노란 확인) →
 * '다이스 결과'(S · F · 분당 이송 · 시간 · 끝 당김 길이 · 완전 나사 끝 · 멈춤 동안 체이서 이동 · 가장 깊은 체이서 끝) → (파랑: 카탈로그는 이송 = 피치) →
 * '체이서 규격 (카탈로그)' → NC '다이스 헤드 프로그램'(깎는 구간만).
 * 계산은 [DieHeadCalc], 틀(키패드 묶음 · 결과 띠)은 곧은 나사 화면과 같다
 */
@Composable
internal fun DieThreadBody(s: ThreadState, die: DieStore, chain: FieldChain, onPick: (Pick) -> Unit) {
    val c = AppTheme.colors
    val input = s.dieInput(die)
    val result = remember(input) { DieHeadCalc.calc(input) }
    val sol = result.solution
    val waiting = DieWarning.MISSING_INPUT in result.warnings
    val bad = s.bad()
    val dieBad = dieBad(s, die)
    // 멈춘 까닭(빨강): 계산기의 멈춤 경고, 값이 모자라는 동안에도 범위 밖 칸은 바로
    val errors = when {
        sol != null -> emptyList()
        waiting -> if (bad.isNotEmpty() || dieBad.isNotEmpty()) listOf(DieWarning.BAD_INPUT) else emptyList()
        else -> result.warnings
    }
    val (flagged, dieFlagged) = if (sol != null) emptySet<ThreadField>() to emptySet() else dieFlagged(s, die, errors)
    val dock = dieDock(s, die, result, errors, c)
    // 키패드 결과 한 줄 = 깎는 G1 줄
    val preview = sol?.program?.firstOrNull { it.startsWith("G1") } ?: dock?.oneLine()

    @Composable
    fun field(f: ThreadField, modifier: Modifier) = NumberField(
        dieFieldLabel(f), s.value(f), { s.set(f, it) }, modifier,
        unit = f.unit, placeholder = s.placeholder(f), error = f in flagged,
        hint = dieFieldHint(f), id = f.key, preview = preview,
    )

    @Composable
    fun dieField(f: DieField, modifier: Modifier) = NumberField(
        f.label, die.value(f, s.material), { die.set(f, s.material, it) }, modifier,
        unit = f.unit, placeholder = diePlaceholder(f), error = f in dieFlagged,
        hint = dieConditionHint(f), id = "die.${f.key}", preview = preview,
    )

    FieldChainHost(chain) {
        TaperPage(dock, chain.sheetOpen) { anchor ->
            ThreadTopCard(s)
            if (waiting) WarningBanner(dieWarningText(DieWarning.MISSING_INPUT, s, die), level = WarnLevel.Info)
            if (errors.isNotEmpty()) {
                Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                    errors.forEach { WarningBanner(dieWarningText(it, s, die), level = WarnLevel.Error) }
                }
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("다이스 헤드 설정")) { Text("단위 mm · X0 (머리가 중심)", style = AppType.caption, color = c.textMuted) }
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        ChoiceField("나사 종류", kindText(s.family), Modifier.weight(1.4f)) { onPick(Pick.KIND) }
                        ChoiceField("사이즈", s.spec?.size ?: "고르기", Modifier.weight(1f), empty = s.spec == null) { onPick(Pick.SIZE) }
                        ChoiceField(pitchLabel(s.family), s.spec?.let(::pitchText) ?: "—", Modifier.weight(1f), empty = s.spec == null) {
                            onPick(if (s.spec == null) Pick.SIZE else Pick.PITCH)
                        }
                    }
                    ChoiceField("가공 소재", s.material?.let(::materialText) ?: "고르지 않음", Modifier.fillMaxWidth(), empty = s.material == null) {
                        onPick(Pick.MATERIAL)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                        Marked(c.drawT1, 1f) { field(ThreadField.Z_START, it) }
                        field(ThreadField.RPM, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                        Marked(c.drawT2, 1f) { field(ThreadField.Z_END, it) }
                        field(ThreadField.Z_REF, Modifier.weight(1f))
                    }
                }
                s.spec?.let { sp ->
                    Text(
                        keepWords("${specTitle(sp)} · 호칭 Ø${Fmt.fixed3(sp.d)} · 피치 ${Fmt.fixed3(sp.pitch)} — 다이스는 숫나사 · 정가공(Z−)만"),
                        Modifier.padding(top = 8.dp),
                        style = AppType.caption,
                        color = c.textMuted,
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("다이스 조건"), keepWords("회사 값 — 고치면 바로 저장 · 빈칸은 처음 값"))
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        dieField(DieField.VC, Modifier.weight(1f))
                        dieField(DieField.RATIO, Modifier.weight(1f))
                    }
                    // Vc 아래 카탈로그 줄(재질 글 · 범위가 잘리지 않게 한 줄 전체) + 출처 한 줄
                    val hit = s.shownDieSpeed
                    ChoiceField("카탈로그 절삭 속도 (Wagner)", hit?.speed?.let { "${speedShort(it)} m/min" } ?: "재질 고르기", Modifier.fillMaxWidth(), empty = hit == null) {
                        onPick(Pick.DIE_SPEED)
                    }
                    Text(keepWords(dieSpeedNote(s, sol)), Modifier.padding(start = 2.dp), style = AppType.caption, color = c.textMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        dieField(DieField.DWELL, Modifier.weight(1f))
                        dieField(DieField.RETRACT, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        dieField(DieField.FLOAT, Modifier.weight(1f))
                        dieField(DieField.THROAT, Modifier.weight(1f))
                    }
                }
                ExpandableHint(
                    "빈칸은 처음 값 · Vc 는 소재마다 저장.",
                    "다이스 조건은 회사에서 쓰는 값입니다 — 다이스 헤드 모델을 모르니 카탈로그 일반 값과 따로 넣고, 고치면 바로 저장되어 다음에 열어도 그대로입니다. " +
                        "절삭 속도 Vc 는 가공 소재마다 따로 저장되고 처음은 빈칸입니다(회전수 칸에 넣으면 Vc 대신 그 값). " +
                        "이송 비율 · 끝 멈춤 · 물러남 Z 의 빈칸은 처음 값 ${plain(DieHeadCalc.RATIO_DEFAULT)} · ${plain(DieHeadCalc.DWELL_DEFAULT)} 초 · " +
                        "Z${plain(DieHeadCalc.RETRACT_Z_DEFAULT)} 입니다(사용자가 정한 처음 값). 플로트(머리의 축 방향 여유)와 체이서 목 산수는 카탈로그에 값이 없어 " +
                        "빈칸이 처음이고, 넣으면 끝 당김 길이 · 끝 멈춤 동안 열림 · 완전 나사를 견줍니다.",
                    Modifier.padding(top = 4.dp),
                )
            }

            // 숫자와 함께 나온 확인 경고(비율 > 1 · 당김 > 플로트 · 완전 나사 없음 · 멈춤 동안 안 열림 · 시작이 끝면에 가까움)
            if (sol != null) result.warnings.forEach { WarningBanner(dieWarningText(it, s, die, sol), level = WarnLevel.Warn) }

            AppCard(if (sol != null) anchor.fillMaxWidth() else Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("다이스 결과"), keepWords("G97 회전수 일정 · F 는 회전당 이송"))
                if (sol == null) {
                    Text(keepWords("값을 넣으면 회전수 · 이송 · 시간 · 끝 당김 길이가 나옵니다."), style = AppType.caption, color = c.textMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        dieResultCells(s, die, sol).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                row.forEach { (label, value, sub) -> ResultCell(label, value, Modifier.weight(1f), sub = sub) }
                            }
                        }
                    }
                }
            }
            // 카탈로그와 다른 점 — 안내(경고 아님): 비율이 1 이 아닐 때
            if (sol?.note != null) WarningBanner(dieCatalogFeedText(), level = WarnLevel.Info)

            ChaserCard(s.spec)

            if (sol != null) {
                val retract = die.num(DieField.RETRACT, s.material) ?: DieHeadCalc.RETRACT_Z_DEFAULT
                NcCard(
                    sol.program,
                    dieNcExplain(retract),
                    programName = "다이스 헤드 프로그램",
                    facts = listOf(
                        NcFact("깎는 구간만 — T · M03 없음"),
                        NcFact("G99 회전당 이송 상태로"),
                        NcFact("F = 피치 × ${plain(input.ratio)}"),
                    ),
                )
            }
            if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
        }
    }
}

/** 다이스 헤드 화면의 칸 이름 — 기준면 Z 칸은 '끝면 Z'(공작물 끝면) */
internal fun dieFieldLabel(f: ThreadField): String = if (f == ThreadField.Z_REF) "끝면 Z" else f.label

/** 다이스 조건 칸 자리표시: 처음 값이 있는 칸은 '기본 …', Vc 는 '회사 값', 플로트 · 목 산수는 '모름' */
internal fun diePlaceholder(f: DieField): String = when (f) {
    DieField.VC -> "회사 값"
    DieField.RATIO -> "기본 ${plain(DieHeadCalc.RATIO_DEFAULT)}"
    DieField.DWELL -> "기본 ${plain(DieHeadCalc.DWELL_DEFAULT)}"
    DieField.RETRACT -> "기본 ${plain(DieHeadCalc.RETRACT_Z_DEFAULT)}"
    DieField.FLOAT, DieField.THROAT -> "모름"
}

/** 다이스 조건 칸 키패드 안내 줄 */
internal fun dieConditionHint(f: DieField): String = when (f) {
    DieField.VC -> "다이스 절삭 속도 (호칭 지름에서) — 회사 값, 가공 소재마다 저장 · 회전수 칸을 넣으면 쓰지 않음"
    DieField.RATIO -> "이송 F = 피치 × 비율 · 비우면 ${plain(DieHeadCalc.RATIO_DEFAULT)} — 카탈로그(Wagner · Landis)는 1 (F = 피치)"
    DieField.DWELL -> "G1 끝에서 멈추는 시간 (G4 U, 초) — 머리가 열리게 · 비우면 ${plain(DieHeadCalc.DWELL_DEFAULT)} · 0 이면 G4 줄 없음(안 열린 채 물러나 노란 경고)"
    DieField.RETRACT -> "끝나고 급송으로 물러나는 Z (G0 Z) · 비우면 ${plain(DieHeadCalc.RETRACT_Z_DEFAULT)}"
    DieField.FLOAT -> "머리의 축 방향 여유 · 열림 행정 (카탈로그에 값 없음) — 넣으면 당김이 넘을 때 · 멈춤 동안 못 열 때 노란 경고, 체이서 끝 = 끝 Z − 플로트"
    DieField.THROAT -> "체이서 목(모따기) 산수 — 넣으면 완전 나사 끝 Z = 끝 Z + 목 산수 × 피치"
}

/** 아직 안 넣은 값(파랑 안내의 '남은 것') — Vc 와 회전수는 하나만 있으면 된다 */
internal fun dieMissing(s: ThreadState, d: DieStore): List<String> = buildList {
    if (s.spec == null) add("사이즈")
    if (s.num(ThreadField.Z_START) == null) add("Z축 시작점")
    if (s.num(ThreadField.Z_END) == null) add("가공 목표점 (Z)")
    if (d.num(DieField.VC, s.material) == null && s.num(ThreadField.RPM) == null) add("절삭 속도 Vc (또는 회전수)")
}

/** Z 순서 멈춤의 까닭 — 계산기(DieHeadCalc.calc)와 같은 차례로 첫째 */
private enum class DieZProblem { ORDER, APPROACH, RETRACT, FACE_OUT, FACE_IN }

private fun dieZProblem(s: ThreadState, d: DieStore): DieZProblem? {
    val zs = s.num(ThreadField.Z_START) ?: return null
    val ze = s.num(ThreadField.Z_END) ?: return null
    val face = s.num(ThreadField.Z_REF) ?: 0.0
    val retract = d.num(DieField.RETRACT, s.material) ?: DieHeadCalc.RETRACT_Z_DEFAULT
    return when {
        zs <= ze -> DieZProblem.ORDER
        DieHeadCalc.APPROACH_Z_DEFAULT < zs -> DieZProblem.APPROACH
        retract < zs -> DieZProblem.RETRACT
        face > zs -> DieZProblem.FACE_OUT
        face <= ze -> DieZProblem.FACE_IN
        else -> null
    }
}

/** 멈춘 경고가 가리키는 칸(빨간 테) — 이 화면의 숫자칸 · 다이스 조건 칸 */
internal fun dieFlagged(s: ThreadState, d: DieStore, warnings: List<DieWarning>): Pair<Set<ThreadField>, Set<DieField>> {
    val fields = s.bad().toMutableSet()
    val dies = dieBad(s, d).toMutableSet()
    if (DieWarning.Z_ORDER in warnings) {
        when (dieZProblem(s, d)) {
            DieZProblem.ORDER -> fields += listOf(ThreadField.Z_START, ThreadField.Z_END)
            DieZProblem.APPROACH -> fields += ThreadField.Z_START
            DieZProblem.RETRACT -> {
                fields += ThreadField.Z_START
                dies += DieField.RETRACT
            }
            DieZProblem.FACE_OUT -> fields += listOf(ThreadField.Z_START, ThreadField.Z_REF)
            DieZProblem.FACE_IN -> fields += listOf(ThreadField.Z_END, ThreadField.Z_REF)
            null -> Unit
        }
    }
    return fields to dies
}

/**
 * 다이스 헤드 경고 글. 값 부족은 파랑, 멈춤은 빨강, 숫자와 함께 나온 것(비율 > 1 · 당김 > 플로트 · 완전 나사 없음)은 노랑 — 숫자 글은 sol 이 있을 때
 */
internal fun dieWarningText(w: DieWarning, s: ThreadState, d: DieStore, sol: DieSolution? = null): String = when (w) {
    DieWarning.MISSING_INPUT -> "값을 넣으면 바로 계산합니다 — 남은 것: ${dieMissing(s, d).joinToString(" · ")}."
    DieWarning.BAD_INPUT -> {
        val parts = s.bad().map { f ->
            when (f) {
                ThreadField.RPM -> "회전수 1 ~ 100000"
                else -> "${dieFieldLabel(f)} ±${MAX_MM.toInt()} mm 안"
            }
        } + dieBad(s, d).map { f ->
            when (f) {
                DieField.VC -> "절삭 속도 Vc 0 보다 크게"
                DieField.RATIO -> "이송 비율 0 보다 크게 (F = 피치 × 비율이 0.001 mm 이상)"
                DieField.DWELL -> "끝 멈춤 0 이상"
                DieField.RETRACT -> "물러남 Z ±${MAX_MM.toInt()} mm 안"
                DieField.FLOAT -> "플로트 0 이상"
                DieField.THROAT -> "체이서 목 산수 0 이상"
            }
        }
        if (parts.isNotEmpty()) {
            "넣은 값이 범위 밖입니다 — ${parts.joinToString(" · ")}."
        } else {
            "계산한 회전수 S 가 1 ~ 100000 밖입니다 — 절삭 속도 Vc(또는 회전수)와 사이즈를 확인하세요."
        }
    }
    DieWarning.Z_ORDER -> {
        val zs = s.num(ThreadField.Z_START)?.let(Fmt::nc) ?: ""
        val face = Fmt.nc(s.num(ThreadField.Z_REF) ?: 0.0)
        when (dieZProblem(s, d)) {
            DieZProblem.APPROACH ->
                "Z축 시작점(Z$zs)이 진입 Z${plain(DieHeadCalc.APPROACH_Z_DEFAULT)} 보다 큽니다 — Z${plain(DieHeadCalc.APPROACH_Z_DEFAULT)}. 에서 시작점으로 급송하는 글이라 " +
                    "시작점은 그 이하로 하세요."
            DieZProblem.RETRACT ->
                "물러남 Z 가 Z축 시작점(Z$zs)보다 작습니다 — 끝나고 물러나는 급송이 공작물 쪽으로 갑니다. 다이스 조건의 물러남 Z 를 키우세요."
            DieZProblem.FACE_OUT ->
                "Z축 시작점(Z$zs)이 공작물 끝면(Z$face) 안입니다 — 시작점으로 급송하면 공작물에 닿습니다. Z축 시작점을 끝면보다 크게 하세요 (예: 끝면 + 5)."
            DieZProblem.FACE_IN ->
                "공작물 끝면(Z$face)이 가공 목표점보다 작거나 같아 깎을 나사가 없습니다 — 끝면 Z 를 확인하세요 (비우면 0)."
            DieZProblem.ORDER, null ->
                "다이스 헤드는 정가공(Z−)만 — Z축 시작점이 가공 목표점보다 커야 합니다 (척 쪽으로 깎음, 예: 5 → −20)."
        }
    }
    DieWarning.RATIO_OVER_ONE ->
        "이송 비율이 1 보다 큽니다 — 기계 이송이 체이서 리드보다 빨라 머리를 밀어붙입니다. 당겨 여는 머리는 끝에서 안 열릴 수 있고, " +
            "물릴 때 이송이 너무 크면 두 줄 나사가 될 수 있습니다(Wagner 고장표)."
    DieWarning.FLOAT_EXCEEDED -> {
        val pull = sol?.let { "(${Fmt.fixed3(it.floatPull)} mm)" } ?: ""
        val fl = d.num(DieField.FLOAT, s.material)?.let { "(${Fmt.fixed3(it)} mm)" } ?: ""
        "끝 당김 길이${pull}가 플로트${fl}보다 깁니다 — G1 끝 전에 머리가 열려 나사가 덜 깎일 수 있습니다. 이송 비율을 1 에 가깝게 하거나 플로트를 확인하세요."
    }
    DieWarning.SHORT_THREAD -> {
        val end = sol?.fullThreadEndZ?.let { "(Z${Fmt.nc(it)})" } ?: ""
        val face = Fmt.nc(s.num(ThreadField.Z_REF) ?: 0.0)
        "완전 나사 끝${end}이 공작물 끝면(Z$face) 이상입니다 — 나사 길이가 체이서 목 길이(목 산수 × 피치) 이하라 완전 나사가 생기지 않습니다. " +
            "가공 목표점과 목 산수를 확인하세요."
    }
    DieWarning.DWELL_SHORT -> {
        val fl = d.num(DieField.FLOAT, s.material)
        val noG4 = sol != null && sol.program.none { it.startsWith("G4") }
        if (fl == null || noG4) {
            "끝 멈춤이 0 이라(G4 줄 없음) 머리가 열리기 전에 급송(G0 물러남)으로 닫힌 체이서를 끌어냅니다 — 나사 끝이 뜯기거나 머리가 상할 수 있습니다. " +
                "끝 멈춤을 넣으세요(처음 값 ${plain(DieHeadCalc.DWELL_DEFAULT)} 초)."
        } else {
            val pull = sol?.let { Fmt.fixed3(it.floatPull) } ?: ""
            val move = sol?.let { Fmt.fixed3(it.dwellTravel) } ?: ""
            "끝 당김 길이 $pull mm 에 끝 멈춤 동안 체이서 이동 $move mm 를 더해도 플로트 ${Fmt.fixed3(fl)} mm 에 못 미쳐, 머리가 열리기 전에 급송(G0 물러남)으로 " +
                "닫힌 체이서를 끌어낼 수 있습니다 — 끝 멈춤을 늘리거나 플로트를 확인하세요."
        }
    }
    DieWarning.START_NEAR -> {
        val zs = s.num(ThreadField.Z_START)?.let(Fmt::nc) ?: ""
        val face = Fmt.nc(s.num(ThreadField.Z_REF) ?: 0.0)
        val p = s.spec?.let { Fmt.fixed3(it.pitch) } ?: ""
        "Z축 시작점(Z$zs)이 공작물 끝면(Z$face)에서 피치($p)보다 가깝습니다 — 급송 끝이 끝면에 붙어 체이서 목이 급송으로 닿을 수 있습니다. " +
            "Z축 시작점을 끝면에서 더 띄우세요 (예: 끝면 + 5)."
    }
}

/**
 * 다이스 NC 카드 설명 — 체이서 끝은 G1 끝에서 당김만큼 들어가 있고 끝 멈춤 동안 머리가 열릴 때까지(플로트까지) 더 들어간다, 자동 열림 머리는 다음 공작물 전에 닫는다
 */
internal fun dieNcExplain(retract: Double): String =
    "다이스 헤드는 X0.(머리가 공작물 중심)에서 Z 로만 움직입니다. Z20. 에서 Z축 시작점으로 급송한 뒤 G1 로 가공 목표점까지 깎고, " +
        "끝 멈춤(G4 U — 초)으로 머리가 열리게 한 뒤 Z${Fmt.word(retract)} 로 물러납니다. F 는 회전당 이송(G99)이고 피치 × 이송 비율입니다. " +
        "체이서는 G1 끝에서 이미 끝 당김 길이만큼 더 들어가 있고, 끝 멈춤 동안에도 제 리드로 더 들어가다가 머리 앞쪽이 플로트(열림 행정)까지 끌려 나가야 열립니다 — " +
        "실제 나사 끝(체이서 끝)은 가장 많이 G1 끝보다 플로트만큼 척 쪽입니다(결과의 '체이서 끝'). 어깨 가까이 끝낼 때는 이 몫과 머리 앞면을 보고 가공 목표점을 잡으세요. " +
        "자동 열림 머리는 다음 공작물 전에 닫아 둡니다(닫기 레버 등 — 잊으면 다음 회는 열린 채 지나가 나사가 생기지 않음). " +
        "G97 S 는 계산한 회전수입니다. 공구 호출(T) · 주축 회전(M03) · 냉각은 기계에 맞게 앞에 넣습니다."

/** 비율이 1 이 아닐 때의 파랑 안내 한 줄(경고 아님 — 계산기 [DieHeadCalc.CATALOG_FEED_NOTE] 와 같은 뜻) */
internal fun dieCatalogFeedText(): String = "카탈로그(Wagner · Landis)는 이송 = 피치(비율 1)로 씁니다 — 처음 값 ${plain(DieHeadCalc.RATIO_DEFAULT)}\u00A0은 사용자가 정한 값입니다."

/** 결과 칸(이름 · 값 · 아래 글) — 화면과 골든이 같이 쓴다 */
internal fun dieResultCells(s: ThreadState, d: DieStore, sol: DieSolution): List<Triple<String, String, String>> {
    val spec = s.spec
    val i = s.dieInput(d)
    return listOf(
        Triple("회전수 S", "S${sol.rpm}", "Vc ${oneDecimal(sol.vcUsed)} m/min · Ø${spec?.let { plain(it.d) } ?: ""}"),
        Triple("이송 F", "F${Fmt.word(sol.feed)}", "피치 ${spec?.let { Fmt.fixed3(it.pitch) } ?: ""} × ${plain(i.ratio)}"),
        Triple("분당 이송", oneDecimal(sol.feedPerMin), "mm/min = S × F"),
        Triple("가공 시간 (초)", oneDecimal(sol.timeSec), "G1 + 끝 멈춤 ${plain(i.dwell)} 초"),
        Triple("끝 당김 길이", Fmt.fixed3(sol.floatPull), if (sol.floatPull < 0) "밀림 (비율 > 1)" else "끝면부터 (1 − 비율)/비율 배"),
        Triple(
            "완전 나사 끝",
            sol.fullThreadEndZ?.let { "Z${Fmt.nc(it)}" } ?: "—",
            if (sol.fullThreadEndZ == null) "목 산수를 넣으면" else "끝 Z + 목 ${plain(i.throatThreads ?: 0.0)}산 × 피치",
        ),
        Triple("멈춤 동안 체이서 이동", Fmt.fixed3(sol.dwellTravel), "S/60 × 멈춤 × 피치"),
        Triple(
            "체이서 끝 (가장 깊게)",
            sol.chaserEndZ?.let { "Z${Fmt.nc(it)}" } ?: "—",
            if (sol.chaserEndZ == null) "플로트를 넣으면" else "끝 Z − 플로트 ${plain(i.floatTravel ?: 0.0)}",
        ),
    )
}

/** 카탈로그 절삭 속도 범위 글(m/min): "15 ~ 50" · 최대가 없으면 "15 이상" */
internal fun speedRange(sp: DieSpeed): String {
    val lo = sp.vcMin
    val hi = sp.vcMax
    return when {
        lo != null && hi != null -> "${plain(lo)}\u00A0~\u00A0${plain(hi)}"
        lo != null -> "${plain(lo)} 이상"
        hi != null -> "${plain(hi)} 이하"
        else -> "—"
    }
}

/** 카탈로그 재질 이름(짧게) — 한글 풀이가 '원문 …' 이면 원문 재질 그대로, 괄호 풀이는 뺀다 */
private fun speedName(sp: DieSpeed): String = if (sp.materialKo.startsWith("원문")) sp.material else sp.materialKo.substringBefore(" (")

/** 칸 값(좁은 칸): "쾌삭강 15 ~ 50" */
internal fun speedShort(sp: DieSpeed): String = "${speedName(sp)} ${speedRange(sp)}"

/** 출처 글: 출처 이름(괄호 풀이 뺌, 회사 이름이 없으면 앞에) + 쪽 — "WAGNER Operating Manual ZR 26-I 29쪽 (14.1, Table 4)" */
internal fun dieSourceText(sourceId: String, page: String): String {
    val src = DieHeadCatalog.source(sourceId) ?: return "$sourceId $page"
    val title = src.title.substringBefore(" (")
    val maker = DieHeadCatalog.makers.firstOrNull { m -> m.speeds.any { it.sourceId == sourceId } || m.chasers.any { it.sourceId == sourceId } }?.label
    val named = if (maker == null || title.startsWith(maker, ignoreCase = true)) title else "$maker $title"
    val p = if ("쪽" in page) page else page.replaceFirst(Regex("^(\\d+)"), "$1쪽")
    return "$named $p"
}

/**
 * Vc 칸 아래 한 줄: 고른 카탈로그 줄의 출처(안 골랐으면 고르는 법) · Vc 가 어느 소재 열쇠에 저장되는지 · 원문 풀이(한글 이름의 '— …로 보임') ·
 * 사이즈가 있으면 그 줄 범위의 S(호칭 지름에서), 풀렸으면 지금 쓴 Vc 가 범위 밖인지
 */
internal fun dieSpeedNote(s: ThreadState, sol: DieSolution? = null): String {
    val at = "Vc 는 '${s.material?.let(::materialText) ?: "소재 고르지 않음"}' 에 저장"
    val hit = s.shownDieSpeed ?: return "$at. 카탈로그는 Wagner 설명서 두 권의 범위 — ISO 군 표시가 없어 재질 글로 고릅니다."
    val sp = hit.speed
    val gloss = sp.materialKo.substringAfter(" — ", "").removeSuffix(")").let { if (it.isEmpty()) "" else " ($it)" }
    val rpm = s.spec?.let { " 이 줄은 Vc ${speedRange(sp)} → ${speedRpm(sp, it.d)} (Ø${plain(it.d)})." } ?: ""
    val lo = sp.vcMin
    val hi = sp.vcMax
    val out = sol?.takeIf { (lo != null && it.vcUsed < lo - 1e-9) || (hi != null && it.vcUsed > hi + 1e-9) }
        ?.let { " 지금 Vc ${oneDecimal(it.vcUsed)}(S${it.rpm})는 이 범위 밖입니다." } ?: ""
    return "$at. 출처: ${dieSourceText(sp.sourceId, sp.page)} · 원문 '${sp.material}'$gloss.$rpm$out"
}

/** 카탈로그 범위의 회전수 글(호칭 지름 d — 계산기와 같은 반올림): "S199 ~ 497" · 최소만 있으면 "S299 이상" */
private fun speedRpm(sp: DieSpeed, d: Double): String {
    fun n(vc: Double) = Cutting.rpm(vc, d).roundToLong()
    val lo = sp.vcMin
    val hi = sp.vcMax
    return when {
        lo != null && hi != null -> "S${n(lo)}\u00A0~\u00A0${n(hi)}"
        lo != null -> "S${n(lo)} 이상"
        hi != null -> "S${n(hi)} 이하"
        else -> "—"
    }
}

/** 체이서 규격 표의 한 줄(제조사 · 체이서 크기 · 나사(꼴 · 산수/피치) · 목 · 한 벌 · 출처 id · 쪽) */
internal data class ChaserRow(
    val maker: String,
    val size: String,
    val thread: String,
    val throats: String,
    val set: String,
    val sourceId: String,
    val page: String,
)

/** 체이서 표 아래 출처 줄: 출처마다 한 줄(쪽은 모아서 — "Landis Standard Stock & Non Stock Chasers 1쪽 · 2쪽") */
internal fun chaserSources(rows: List<ChaserRow>): List<String> =
    rows.groupBy { it.sourceId }.map { (id, rs) -> dieSourceText(id, rs.map { it.page }.distinct().joinToString(" · ")) }

/** [spec] 에 맞는 카탈로그 체이서 줄([DieHeadCatalog.chasers] — 나사 꼴 · 피치로만 거름). 화면과 골든이 같이 쓴다 */
internal fun chaserRows(spec: ThreadSpec): List<ChaserRow> = DieHeadCatalog.chasers(spec).map { (m, ch) ->
    ChaserRow(
        maker = m.label,
        size = ch.chaserSize ?: "—",
        thread = when (spec.kind) {
            ThreadKind.UN -> "UN ${spec.tpi}산"
            ThreadKind.METRIC -> "미터 피치 ${plain(spec.pitch)}"
        },
        throats = ch.throats.joinToString(" · ").ifEmpty { "—" },
        set = (ch.setOf as? JsonPrimitive)?.content ?: "—",
        sourceId = ch.sourceId,
        page = ch.page,
    )
}

/** '체이서 규격 (카탈로그)' 카드: 그 피치가 맞는 체이서 줄. 체이서는 피치만 정하고 지름 범위는 머리의 홀더가 정한다(Landis) */
@Composable
private fun ChaserCard(spec: ThreadSpec?) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader(keepWords("체이서 규격 (카탈로그)"), keepWords(spec?.let { "${specTitle(it)} 의 피치가 맞는 줄 · 체이서는 피치만 정합니다" } ?: "사이즈를 고르면"))
        val rows = spec?.let(::chaserRows).orEmpty()
        if (spec != null && rows.isEmpty()) {
            Text(keepWords("카탈로그에 이 피치의 체이서 줄이 없습니다."), style = AppType.caption, color = c.textMuted)
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
            Row(verticalAlignment = Alignment.Top) {
                Text(keepWords("${r.maker} · ${r.size}"), Modifier.weight(1f), color = c.textBody, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(r.thread, Modifier.padding(start = 8.dp), color = c.textValue, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
            }
            // 목 이름('30° Spec. Short') · '한 벌 4개' · 쪽은 줄이 갈리지 않게(빈칸 대신 줄 안 바꿈 빈칸)
            Text(
                keepWords("목: ${r.throats.split(" · ").joinToString(" · ") { it.replace(' ', '\u00A0') }} · ${"한 벌 ${r.set}개".replace(' ', '\u00A0')} · ${r.page.replace(' ', '\u00A0')}"),
                Modifier.padding(top = 2.dp),
                color = c.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
        chaserSources(rows).forEach { Text(keepWords("출처: $it"), Modifier.padding(top = 8.dp), style = AppType.caption, color = c.textMuted) }
        Text(
            keepWords(
                "체이서는 피치만 정하고 지름 범위는 머리의 체이서 홀더가 정합니다(Landis). Wagner 체이서 표는 표기 예뿐이고 TACO 는 나사 꼴 · 피치가 없어 이 표에 없습니다.",
            ),
            Modifier.padding(top = 10.dp),
            style = AppType.caption,
            color = c.textMuted,
        )
    }
}

/** 결과 띠 · 키패드 한 줄: 풀리면 시작 · 끝(X0 · G1 Z)과 노란 경고 수, 멈췄으면 첫 경고의 첫 문장, 값이 모자라는 동안은 null */
private fun dieDock(s: ThreadState, d: DieStore, result: DieResult, errors: List<DieWarning>, c: AppColors): DockStatus? {
    if (result.solution == null) return errors.firstOrNull()?.let { DockStatus.Error(firstSentence(dieWarningText(it, s, d))) }
    val zs = s.num(ThreadField.Z_START) ?: return null
    val ze = s.num(ThreadField.Z_END) ?: return null
    val rows = listOf(
        DockRow("시작", Fmt.fixed3(0.0), Fmt.fixed3(zs), c.drawT1, c.threadAuto()),
        DockRow("끝", Fmt.fixed3(0.0), Fmt.fixed3(ze), c.drawT2, c.threadAuto()),
    )
    return if (result.warnings.isNotEmpty()) DockStatus.Warn(result.warnings.size, rows) else DockStatus.Ok(rows)
}

/** 다이스 카탈로그 절삭 속도 시트: 출처마다 묶은 재질 글 줄(사다리꼴 나사 줄은 뺌). 고르면 Vc 칸 옆에 보이기만 한다(회사 Vc 칸에 넣지 않음) */
@Composable
internal fun DieSpeedSheet(s: ThreadState, onDismiss: () -> Unit) {
    ChoiceSheet(
        "카탈로그 절삭 속도",
        "Wagner · m/min",
        "고정형 다이스 헤드의 공식 절삭 속도 표는 Wagner 설명서 두 권뿐입니다(Landis · TACO 는 공개 표 없음). 표에 ISO 군 표시가 없어 재질 글로 고릅니다. " +
            "고른 줄은 견주기만 하고 회사 Vc 칸에는 넣지 않습니다.",
        onDismiss,
    ) {
        ChoiceRow("고르지 않음", null, s.dieSpeed == null) {
            s.dieSpeed = null
            onDismiss()
        }
        DieHeadCatalog.speeds().groupBy { it.speed.sourceId to it.speed.page }.forEach { (src, hits) ->
            Spacer(Modifier.height(8.dp))
            ThreadLabel("출처: ${dieSourceText(src.first, src.second)}", maxLines = 2)
            hits.forEach { h ->
                val sp = h.speed
                val type = sp.threadType?.substringBefore(" (")?.let { " · $it" } ?: ""
                ChoiceRow("${sp.materialKo} · ${speedRange(sp)}", "원문 '${sp.material}'$type", s.dieSpeed == dieSpeedKey(h)) {
                    s.dieSpeed = dieSpeedKey(h)
                    onDismiss()
                }
            }
        }
    }
}
