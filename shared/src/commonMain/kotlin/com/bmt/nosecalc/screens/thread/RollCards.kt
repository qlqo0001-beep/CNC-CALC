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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.CompanyRolls
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.RollBlankBasis
import com.bmt.nosecalc.calc.RollHeadCalc
import com.bmt.nosecalc.calc.RollHeadCatalog
import com.bmt.nosecalc.calc.RollHeadType
import com.bmt.nosecalc.calc.RollRatioBasis
import com.bmt.nosecalc.calc.RollRef
import com.bmt.nosecalc.calc.RollResult
import com.bmt.nosecalc.calc.RollRpmBasis
import com.bmt.nosecalc.calc.RollSolution
import com.bmt.nosecalc.calc.RollSpeed
import com.bmt.nosecalc.calc.RollSteel
import com.bmt.nosecalc.calc.RollStock
import com.bmt.nosecalc.calc.RollTip
import com.bmt.nosecalc.calc.RollTipGroup
import com.bmt.nosecalc.calc.RollWarning
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSpec
import com.bmt.nosecalc.calc.ThreadTol
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

/**
 * 전조 다이스 나사 화면 — 사용자 결정 2026-11-11: 회사가 쓰는 LMT Fette 축방향 전조 머리(머리는 터렛에 서 있고 공작물이 돈다), M · UN 숫나사만, 나사 화면 안의 가공 방식.
 * 결과를 위로(결정 D17): 외경/내경 + 가공 방식(맨 위 카드) → (파란 안내 · 빨간 멈춤) → '전조 다이스 설정'(종류 · 사이즈 · 피치 · 가공 소재 · 강종 · 머리 · 롤 ·
 * 소재 종류 · Z 시작(빈칸 5) · 회전수 · Z 끝 · 끝면 Z) → '전조 전 지름 (앞 공정 정삭 X)'(NC 에 안 들어감 — 회사 값 · 추천 값 · 전조 뒤 바깥지름) →
 * (노란 확인 · 고친 방향의 요령 안내) → '전조 결과' → NC '전조 다이스 프로그램'(전조 구간만) → '전조 조건'(회사 값 — 비율 · 끝 멈춤 · 물러남 Z, 고치면 바로
 * [RollStore] 에 저장) → '회전수 · 이송 조정 요령'(Fette 자료 — 글마다 출처).
 * 계산은 [RollHeadCalc], 틀(키패드 묶음 · 결과 띠)은 다이스 헤드 화면([DieThreadBody])과 같다
 */
@Composable
internal fun RollThreadBody(s: ThreadState, roll: RollStore, chain: FieldChain, onPick: (Pick) -> Unit) {
    val c = AppTheme.colors
    val input = s.rollInput(roll)
    val result = remember(input) { RollHeadCalc.calc(input) }
    val sol = result.solution
    val waiting = RollWarning.MISSING_INPUT in result.warnings
    val bad = s.bad()
    val rollBad = rollBad(s, roll)
    // 멈춘 까닭(빨강): 계산기의 멈춤 경고, 값이 모자라는 동안에도 범위 밖 칸은 바로
    val errors = when {
        sol != null -> emptyList()
        waiting -> if (bad.isNotEmpty() || rollBad.isNotEmpty()) listOf(RollWarning.BAD_INPUT) else emptyList()
        else -> result.warnings
    }
    val (flagged, rollFlagged) = if (sol != null) emptySet<ThreadField>() to emptySet() else rollFlagged(s, roll, errors)
    val dock = rollDock(s, roll, result, errors, c)
    // 키패드 결과 한 줄 = 전조 G1 줄
    val preview = sol?.program?.firstOrNull { it.startsWith("G1") } ?: dock?.oneLine()
    val defaults = s.rollDefaults

    @Composable
    fun field(f: ThreadField, modifier: Modifier) = NumberField(
        dieFieldLabel(f), s.value(f), { s.set(f, it) }, modifier,
        unit = f.unit, placeholder = s.placeholder(f), error = f in flagged,
        hint = rollFieldHint(f), id = f.key, preview = preview,
    )

    @Composable
    fun rollField(f: RollField, modifier: Modifier) = NumberField(
        f.label, roll.value(f), { roll.set(f, it) }, modifier,
        unit = f.unit, placeholder = rollPlaceholder(s, f), error = f in rollFlagged,
        hint = rollConditionHint(f), id = "roll.${f.key}", preview = preview,
    )

    FieldChainHost(chain) {
        TaperPage(dock, chain.sheetOpen) { anchor ->
            ThreadTopCard(s)
            if (waiting) WarningBanner(rollWarningText(RollWarning.MISSING_INPUT, s, roll), level = WarnLevel.Info)
            if (errors.isNotEmpty()) {
                Column(anchor, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                    errors.forEach { WarningBanner(screenText(rollWarningText(it, s, roll)), level = WarnLevel.Error) }
                }
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                // 부제는 제목 아래 — 오른쪽에 두면 360dp · 130% 에서 제목이 '전조 / 다이스 / 설정' 세 줄로 갈렸다
                CardHeader(keepWords("전조 다이스 설정"), keepWords("단위 mm · X0 (머리가 중심)"))
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        ChoiceField("나사 종류", kindText(s.family), Modifier.weight(1.4f)) { onPick(Pick.KIND) }
                        ChoiceField("사이즈", s.spec?.size ?: "고르기", Modifier.weight(1f), empty = s.spec == null) { onPick(Pick.SIZE) }
                        ChoiceField(pitchLabel(s.family), s.spec?.let(::pitchText) ?: "—", Modifier.weight(1f), empty = s.spec == null) {
                            onPick(if (s.spec == null) Pick.SIZE else Pick.PITCH)
                        }
                    }
                    // 가공 소재(재질) — 비우면 스테인리스로 본다(회사 S · Fette 추천은 스테인리스만). 스테인리스면 그 아래 강종(Fette 재질 표의 두 줄).
                    // 빈칸 글은 뜻을 가르는 '고르지 않음' 을 앞에 — 좁은 폰 · 큰 글꼴에서 뒤가 잘려도 스테인리스를 고른 것으로 읽히지 않게(검토 a3)
                    ChoiceField("가공 소재", s.material?.let(::materialText) ?: ROLL_MATERIAL_EMPTY, Modifier.fillMaxWidth(), empty = s.material == null) {
                        onPick(Pick.MATERIAL)
                    }
                    if (s.rollStainless) {
                        Column {
                            ThreadLabel("스테인리스 강종 (Fette 재질 표)")
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                RollSteel.entries.map { it.label }, s.rollSteel.ordinal, { s.rollSteel = RollSteel.entries[it] },
                                Modifier.fillMaxWidth(), height = 48.dp, fontSize = 15,
                            )
                        }
                    }
                    // 머리 · 롤: 고를 것이 둘 이상일 때만 시트를 연다(하나뿐이면 읽기만)
                    val heads = s.rollHeadChoices
                    ChoiceField(
                        "머리 · 롤", rollHeadText(s), Modifier.fillMaxWidth(), empty = s.spec == null || defaults.head == null,
                        onClick = if (heads.size > 1) ({ onPick(Pick.ROLL_HEAD) }) else null,
                    )
                    // 소재 종류(회사 프로그램이 둘로 나뉨 — 가공 소재와 헷갈리지 않게 이름을 따로, U-6)
                    Column {
                        ThreadLabel("소재 종류")
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            RollStock.entries.map(::rollStockText), s.rollStock.ordinal, { s.rollStock = RollStock.entries[it] },
                            Modifier.fillMaxWidth(), height = 48.dp, fontSize = 15,
                        )
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
                        keepWords("${specTitle(sp)} · 호칭 Ø${Fmt.fixed3(sp.d)} · 피치 ${Fmt.fixed3(sp.pitch)} — 전조는 숫나사 · 정가공(Z−)만"),
                        Modifier.padding(top = 8.dp),
                        style = AppType.caption,
                        color = c.textMuted,
                    )
                }
            }

            BlankCard(s)

            // 숫자와 함께 나온 확인 경고, 그다음 사람이 고친 회전수 · 비율 방향의 요령(파랑 — U-4)
            if (sol != null) {
                result.warnings.forEach { WarningBanner(screenText(rollWarningText(it, s, roll, sol)), level = WarnLevel.Warn) }
                rollDirectionText(sol).forEach { WarningBanner(screenText(it), level = WarnLevel.Info) }
            }

            AppCard(if (sol != null) anchor.fillMaxWidth() else Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("전조 결과"), keepWords("G97 회전수 일정 · F 는 회전당 이송"))
                if (sol == null) {
                    Text(keepWords("값을 넣으면 회전수 · 이송 · 시간 · 실제 전조 끝이 나옵니다."), style = AppType.caption, color = c.textMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rollResultCells(s, roll, sol).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                                row.forEach { (label, value, sub) -> ResultCell(label, value, Modifier.weight(1f), sub = keepWords(screenText(sub))) }
                            }
                        }
                    }
                }
            }

            if (sol != null) {
                val retract = roll.num(RollField.RETRACT) ?: RollHeadCalc.RETRACT_Z_DEFAULT
                NcCard(
                    sol.program,
                    rollNcExplain(retract, sol.openingMovement),
                    programName = "전조 다이스 프로그램",
                    facts = listOf(
                        NcFact("전조 구간만 — T · M03 없음"),
                        NcFact("G99 회전당 이송 상태로"),
                        NcFact(rollFeedFact(sol)),
                    ),
                )
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("전조 조건"), keepWords("회사 값 — 고치면 바로 저장 · 빈칸은 처음 값"))
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        rollField(RollField.RATIO, Modifier.weight(1f))
                        rollField(RollField.DWELL, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        rollField(RollField.RETRACT, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
                Text(keepWords(screenText(rollReferenceText(s))), Modifier.padding(start = 2.dp, top = 8.dp), style = AppType.caption, color = c.textMuted)
            }

            AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
                CardHeader(keepWords("회전수 · 이송 조정 요령"), keepWords("LMT Fette 설명서 · 카탈로그 — 글마다 출처"))
                ExpandableHint(
                    "회전수 · 이송 비율을 올리거나 내리면 생기는 일과 대책, 처음 맞추는 차례.",
                    ROLL_TIP_GROUPS.joinToString("\n\n") { g -> rollTipGroupTitle(g) + "\n" + rollTipsText(g).joinToString("\n") { "· $it" } },
                )
            }
            if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
        }
    }
}

/** '전조 전 지름 (앞 공정 정삭 X)' 카드: 쓴 값을 큰 글로(NC 에 안 들어감 — 앞 공정 정삭 X), 아래 회사 값 · 추천 값 · 전조 뒤 바깥지름 칸(U-2) */
@Composable
private fun BlankCard(s: ThreadState) {
    val c = AppTheme.colors
    val blank = s.rollDefaults.blank
    val cells = rollBlankCells(s)
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader(
            keepWords("전조 전 지름 (앞 공정 정삭 X)"),
            blank?.let { keepWords(if (it.usedBasis == RollBlankBasis.COMPANY) "회사 값 — 회전수도 이 지름으로" else "추천 값 — 회전수도 이 지름으로") },
        )
        if (blank == null || cells.isEmpty()) {
            Text(keepWords("사이즈를 고르면 앞 공정 정삭 X 에 쓸 전조 전 지름이 나옵니다."), style = AppType.caption, color = c.textMuted)
            return@AppCard
        }
        BasicText(
            "X${Fmt.fixed3(blank.used)}",
            style = AppType.numHero.copy(color = c.threadAuto(), fontSize = 28.sp),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(16.sp, 28.sp, 0.5.sp),
        )
        Text(keepWords("이 프로그램에는 안 들어갑니다 — 앞 공정 정삭 X 에 쓰는 값"), Modifier.padding(top = 2.dp), style = AppType.caption, color = c.textMuted)
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cells.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    row.forEach { (label, value, sub) -> ResultCell(label, value, Modifier.weight(1f), sub = keepWords(screenText(sub))) }
                }
            }
        }
    }
}

/**
 * 화면에 쓸 때만: 범위 'Ø18.050 ~ 18.162' 와 '× 0.95' · '× 피치' 를 줄 안 바꿈 빈칸으로 묶는다(412dp 에서 '~ / 18.162', 360dp 130% 에서 '(× / 0.95)' 로 갈렸다).
 * 골든 · 시험이 보는 글 함수는 그대로 둔다. 결과 칸 아래 글은 [keepWords] 도 — ResultCell 은 낱말을 지키지 않아 '아 / 래 끝' 으로 갈렸다
 */
private fun screenText(t: String): String = t.replace(" ~ ", "\u00A0~\u00A0").replace("× ", "×\u00A0")

/** 전조 화면 가공 소재 칸의 빈칸 글(비우면 스테인리스로 본다 — 결정 D6) */
internal const val ROLL_MATERIAL_EMPTY = "고르지 않음 (스테인리스로 봄)"

/** 소재 종류 글(회사 현장 말): 자동단조 · 자동핵사 */
internal fun rollStockText(st: RollStock): String = when (st) {
    RollStock.FORGED -> "자동단조"
    RollStock.HEX -> "자동핵사"
}

/** 요령 묶음의 화면 차례(접힌 글) — RPM · 비율 올림 · 내림, 전조 전 지름 큼 · 작음, 처음 맞추는 차례. 도움말은 '그 밖' 도 */
internal val ROLL_TIP_GROUPS = listOf(
    RollTipGroup.RPM_UP, RollTipGroup.RPM_DOWN, RollTipGroup.RATIO_UP, RollTipGroup.RATIO_DOWN,
    RollTipGroup.BLANK_BIG, RollTipGroup.BLANK_SMALL, RollTipGroup.SETUP,
)

/** 요령 묶음 이름 */
internal fun rollTipGroupTitle(g: RollTipGroup): String = when (g) {
    RollTipGroup.RPM_UP -> "회전수를 올리면"
    RollTipGroup.RPM_DOWN -> "회전수를 내리면"
    RollTipGroup.RATIO_UP -> "이송 비율을 올리면"
    RollTipGroup.RATIO_DOWN -> "이송 비율을 내리면"
    RollTipGroup.BLANK_BIG -> "전조 전 지름이 크면"
    RollTipGroup.BLANK_SMALL -> "전조 전 지름이 작으면"
    RollTipGroup.SETUP -> "처음 맞출 때 (차례)"
    RollTipGroup.OTHER -> "그 밖"
}

/** 출처가 있는 요령만(출처 없는 글은 넣지 않는다) */
private fun sourcedTips(g: RollTipGroup): List<RollTip> = RollHeadCatalog.tips(g).filter { it.refs.isNotEmpty() }

/** 글의 첫 문장(원문을 바꾸지 않고 짧게 — ". " 앞까지, 끝 마침표 뺌) */
private fun firstKo(text: String): String = text.substringBefore(". ").removeSuffix(".")

/** 요령 한 줄의 출처 표(같은 출처 · 쪽은 한 번) */
private fun tipSources(t: RollTip): String = t.refs.map(::rollSourceText).distinct().joinToString(" · ")

/**
 * 요령 묶음의 글(접힌 글 — 짧게): "증상 → 대책 첫 문장 (출처 …)", 처음 맞추는 차례는 앞에 "1. ". 출처 있는 글이 없는 묶음(RATIO_DOWN)은 "Fette 자료에 없음"
 */
internal fun rollTipsText(g: RollTipGroup): List<String> {
    val tips = sourcedTips(g)
    if (tips.isEmpty()) return listOf("Fette 자료에 없음")
    return tips.map { t -> "${t.order?.let { "$it. " } ?: ""}${t.symptomKo} → ${firstKo(t.remedyKo)} (${tipSources(t)})" }
}

/** 요령 묶음의 글(도움말 — 전문): "증상 → 대책 전문 (출처 …)" */
internal fun rollTipsFull(g: RollTipGroup): List<String> {
    val tips = sourcedTips(g)
    if (tips.isEmpty()) return listOf("Fette 자료에 없음")
    return tips.map { t -> "${t.order?.let { "$it. " } ?: ""}${t.symptomKo} → ${t.remedyKo.removeSuffix(".")} (${tipSources(t)})" }
}

/**
 * 출처 표(짧게): 자료 이름 + 인쇄 쪽 — "F1–F3 설명서 인쇄 83" · "카탈로그 인쇄 242". PDF 쪽 · 독어판 쪽(";" 뒤)은 뺀다
 */
internal fun rollSourceText(ref: RollRef): String {
    val name = when (ref.sourceId) {
        "FET_OI_F1F3_EVO" -> "F1–F3 설명서"
        "FET_CAT_ROLL" -> "카탈로그"
        "FET_EVO_BROCHURE" -> "EVOline 브로셔"
        "FET_OI_FRN" -> "F RN 설명서"
        "FET_OI_F01K01" -> "F01/K01 설명서"
        "DIST_TECHMAR_ROLLS" -> "대리점 롤 자료"
        else -> RollHeadCatalog.source(ref.sourceId)?.title?.substringBefore(" (") ?: ref.sourceId
    }
    val page = ref.page.substringBefore(";").replace(PDF_PAGE, "").trim()
    return "$name $page"
}

private val PDF_PAGE = Regex("\\s*\\(PDF[^)]*\\)")

/**
 * 고친 방향의 요령 안내(U-4 — 파랑): 사람이 넣은 회전수 · 비율이 기준(넣지 않았으면 쓰였을 값)과 다르면 그 묶음의 첫 요령을
 * "회전수를 기준 527 보다 올렸습니다(620) — 올리면: 증상 → 대책 (출처)" 꼴로. 그 묶음에 출처 있는 글이 없으면(RATIO_DOWN) 띄우지 않는다.
 * 계산기의 방향(허용차 1e-6)이 있어도 화면에 보이는 두 값(0.001 자리)이 같거나, 비율이면 찍힐 F 가 기준 F 와 같으면 띄우지 않는다
 * (검토 B1 — 자리표시 '기본 0.961' 을 그대로 0.961 로 넣으면 기준 0.960630 보다 크지만 F1.22 그대로라 '0.961 보다 올렸습니다(0.961)' 가 되었다)
 */
internal fun rollDirectionText(sol: RollSolution): List<String> = listOfNotNull(
    sol.rpmDirection?.let { directionLine(it, "회전수를", sol.baseRpm?.toString() ?: "", sol.rpm.toString()) },
    sol.ratioDirection?.takeIf { feedChanged(sol) }?.let { directionLine(it, "이송 비율을", plain(sol.baseRatio), plain(sol.ratio)) },
)

/** 넣은 비율로 찍힐 F 가 기준 비율의 F 와 다른가(NC 글 0.001 자리) — 넣은 비율이면 F = 비율 × 피치라 피치 = F / 비율 */
private fun feedChanged(sol: RollSolution): Boolean = Fmt.word(sol.feed) != Fmt.word(sol.baseRatio * sol.feed / sol.ratio)

private fun directionLine(g: RollTipGroup, what: String, base: String, now: String): String? {
    if (base == now) return null
    val tip = sourcedTips(g).firstOrNull() ?: return null
    val up = g == RollTipGroup.RPM_UP || g == RollTipGroup.RATIO_UP
    return "$what 기준 $base 보다 ${if (up) "올렸습니다" else "내렸습니다"}($now) — ${if (up) "올리면" else "내리면"}: " +
        "${tip.symptomKo} → ${firstKo(tip.remedyKo)} (${rollSourceText(tip.refs.first())})"
}

/** 숫자 칸 키패드 안내 줄 — 전조 다이스 화면 */
internal fun rollFieldHint(f: ThreadField): String = when (f) {
    ThreadField.Z_START -> "전조 G1 시작 Z — 공작물 끝면 밖 공기 중 · 비우면 ${plain(RollHeadCalc.Z_START_DEFAULT)} (진입 Z${plain(RollHeadCalc.APPROACH_Z_DEFAULT)} 이하)"
    ThreadField.RPM -> "주축 회전수 직접 — 비우면 회사 S → Fette 추천(스테인리스만). 다른 소재는 넣으세요"
    ThreadField.Z_END -> "전조 G1 끝 Z — 여기서 이송을 멈추면 머리가 열림 행정 a 만큼 더 나가며 열립니다(실제 끝 = 끝 Z − a). 척 쪽은 −"
    ThreadField.Z_REF -> "공작물 끝면 Z — 전조 길이를 여기부터 셈 · 비우면 0"
    else -> fieldHint(f, inner = false)
}

/** 전조 조건 칸 자리표시: 비율은 쓰일 처음 값(20산 '기본 0.961 (F1.22)' · 그 밖 '기본 0.95'), 멈춤 · 물러남 '기본 …' */
internal fun rollPlaceholder(s: ThreadState, f: RollField): String = when (f) {
    RollField.RATIO -> s.rollDefaults.let { d ->
        if (d.ratioBasis == RollRatioBasis.COMPANY) "기본 ${plain(d.ratio)} (F${plain(CompanyRolls.FEED_20TPI)})" else "기본 ${plain(d.ratio)}"
    }
    RollField.DWELL -> "기본 ${plain(RollHeadCalc.DWELL_DEFAULT)}"
    RollField.RETRACT -> "기본 ${plain(RollHeadCalc.RETRACT_Z_DEFAULT)}"
}

/** 전조 조건 칸 키패드 안내 줄 */
internal fun rollConditionHint(f: RollField): String = when (f) {
    RollField.RATIO ->
        "이송 F = 피치 × 비율 · 비우면 20산은 회사 F${plain(CompanyRolls.FEED_20TPI)}, 그 밖은 Fette ${plain(RollHeadCalc.FETTE_RATIO)} (피치보다 5 % 작게)"
    RollField.DWELL ->
        "G1 끝에서 멈추는 시간 (G4 U, 초) — 머리가 열릴 만큼 · 비우면 ${plain(RollHeadCalc.DWELL_DEFAULT)} · 0 이면 G4 줄 없음(노란 경고)"
    RollField.RETRACT -> "끝나고 급송으로 물러나는 Z (G0 Z) · 비우면 ${plain(RollHeadCalc.RETRACT_Z_DEFAULT)}"
}

/** 아직 안 넣은 값(파랑 안내의 '남은 것') — 꼭 넣을 값은 사이즈 · 끝 Z 둘(시작 Z 빈칸 = 5, F4), 회전수 사슬이 비면 회전수도 */
internal fun rollMissing(s: ThreadState): List<String> = buildList {
    if (s.spec == null) add("사이즈")
    if (s.num(ThreadField.Z_END) == null) add("가공 목표점 (Z)")
    if (s.num(ThreadField.RPM) == null && s.rollRpmMissing) add("회전수 (이 소재는 추천 값이 없음)")
}

/** 쓴 시작 Z — 빈칸이면 계산기와 같은 5(F4) */
internal fun rollZStart(s: ThreadState): Double = s.num(ThreadField.Z_START) ?: RollHeadCalc.Z_START_DEFAULT

/** Z 순서 멈춤의 까닭 — 계산기(RollHeadCalc.calc)와 같은 차례로 첫째 */
private enum class RollZProblem { ORDER, APPROACH, RETRACT, FACE_OUT, FACE_IN }

private fun rollZProblem(s: ThreadState, r: RollStore): RollZProblem? {
    val zs = rollZStart(s)
    val ze = s.num(ThreadField.Z_END) ?: return null
    val face = s.num(ThreadField.Z_REF) ?: 0.0
    val retract = r.num(RollField.RETRACT) ?: RollHeadCalc.RETRACT_Z_DEFAULT
    return when {
        zs <= ze -> RollZProblem.ORDER
        RollHeadCalc.APPROACH_Z_DEFAULT < zs -> RollZProblem.APPROACH
        retract < zs -> RollZProblem.RETRACT
        face > zs -> RollZProblem.FACE_OUT
        face <= ze -> RollZProblem.FACE_IN
        else -> null
    }
}

/** 멈춘 경고가 가리키는 칸(빨간 테) — 이 화면의 숫자칸 · 전조 조건 칸. 시작 Z 가 빈칸이어도(5 로 씀) 그 칸을 짚는다 */
internal fun rollFlagged(s: ThreadState, r: RollStore, warnings: List<RollWarning>): Pair<Set<ThreadField>, Set<RollField>> {
    val fields = s.bad().toMutableSet()
    val rolls = rollBad(s, r).toMutableSet()
    if (RollWarning.Z_ORDER in warnings) {
        when (rollZProblem(s, r)) {
            RollZProblem.ORDER -> fields += listOf(ThreadField.Z_START, ThreadField.Z_END)
            RollZProblem.APPROACH -> fields += ThreadField.Z_START
            RollZProblem.RETRACT -> {
                fields += ThreadField.Z_START
                rolls += RollField.RETRACT
            }
            RollZProblem.FACE_OUT -> fields += listOf(ThreadField.Z_START, ThreadField.Z_REF)
            RollZProblem.FACE_IN -> fields += listOf(ThreadField.Z_END, ThreadField.Z_REF)
            null -> Unit
        }
    }
    return fields to rolls
}

/** 머리의 카탈로그 지름 범위 글: "Ø8–16" */
internal fun rollHeadRange(h: RollHeadType): String = "Ø${h.threadDiaMinMm?.let(::plain) ?: "?"}–${h.threadDiaMaxMm?.let(::plain) ?: "?"}"

/** Fette 속도 범위 글(m/min): "30–50" */
private fun speedSpan(r: RollSpeed): String = "${r.vMinMpm?.let(::plain) ?: "?"}–${r.vMaxMpm?.let(::plain) ?: "?"}"

/**
 * 전조 다이스 경고 글. 값 부족은 파랑, 멈춤은 빨강, 숫자와 함께 나온 것은 노랑 — 숫자 글은 sol 이 있을 때.
 * Z 순서 글의 "Z축 시작점(Z…)" 은 쓴 시작 Z(빈칸 5 — F4)
 */
internal fun rollWarningText(w: RollWarning, s: ThreadState, r: RollStore, sol: RollSolution? = null): String = when (w) {
    RollWarning.MISSING_INPUT -> "값을 넣으면 바로 계산합니다 — 남은 것: ${rollMissing(s).joinToString(" · ")}."
    RollWarning.BAD_INPUT -> {
        val parts = s.bad().map { f ->
            when (f) {
                ThreadField.RPM -> "회전수 1 ~ 100000"
                else -> "${dieFieldLabel(f)} ±${MAX_MM.toInt()} mm 안"
            }
        } + rollBad(s, r).map { f ->
            when (f) {
                RollField.RATIO -> "이송 비율 0 보다 크게 (F = 피치 × 비율이 0.001 mm 이상)"
                RollField.DWELL -> "끝 멈춤 0 이상"
                RollField.RETRACT -> "물러남 Z ±${MAX_MM.toInt()} mm 안"
            }
        }
        if (parts.isNotEmpty()) {
            "넣은 값이 범위 밖입니다 — ${parts.joinToString(" · ")}."
        } else {
            "계산한 회전수 S 가 1 ~ 100000 밖이거나 이송 F 가 0.001 mm 보다 작습니다 — 회전수와 이송 비율을 확인하세요."
        }
    }
    RollWarning.OUT_OF_HEAD_RANGE -> {
        val spec = s.spec
        val d = spec?.let { "(Ø${plain(it.d)})" } ?: ""
        val picked = s.rollHead?.let(RollHeadCatalog::head)
        if (picked != null && spec != null && !RollHeadCalc.inRange(picked, spec)) {
            "이 규격${d}은 고른 머리 ${picked.key} 의 지름 범위 ${rollHeadRange(picked)}(카탈로그) 밖입니다 — 머리 · 롤을 바꾸세요."
        } else {
            val heads = RollHeadCatalog.evoHeads
            val lo = heads.mapNotNull { it.threadDiaMinMm }.minOrNull()
            val hi = heads.mapNotNull { it.threadDiaMaxMm }.maxOrNull()
            "이 규격${d}에 맞는 EVOline 머리가 없습니다 — ${heads.joinToString(" · ") { it.key }} 의 지름 범위는 Ø${lo?.let(::plain) ?: "?"} ~ ${hi?.let(::plain) ?: "?"} 입니다(카탈로그). " +
                "바이트나 절삭 다이스로 깎으세요."
        }
    }
    RollWarning.Z_ORDER -> {
        val zs = Fmt.nc(rollZStart(s))
        val face = Fmt.nc(s.num(ThreadField.Z_REF) ?: 0.0)
        val approach = plain(RollHeadCalc.APPROACH_Z_DEFAULT)
        when (rollZProblem(s, r)) {
            RollZProblem.APPROACH ->
                "Z축 시작점(Z$zs)이 진입 Z$approach 보다 큽니다 — Z$approach. 에서 시작점으로 급송하는 글이라 시작점은 그 이하로 하세요."
            RollZProblem.RETRACT ->
                "물러남 Z 가 Z축 시작점(Z$zs)보다 작습니다 — 끝나고 물러나는 급송이 공작물 쪽으로 갑니다. 전조 조건의 물러남 Z 를 키우세요."
            RollZProblem.FACE_OUT ->
                "Z축 시작점(Z$zs)이 공작물 끝면(Z$face) 안입니다 — 시작점으로 급송하면 머리가 공작물에 닿습니다. Z축 시작점을 끝면보다 크게 하세요 (예: 끝면 + 5)."
            RollZProblem.FACE_IN ->
                "공작물 끝면(Z$face)이 가공 목표점보다 작거나 같아 전조할 나사가 없습니다 — 끝면 Z 를 확인하세요 (비우면 0)."
            RollZProblem.ORDER, null ->
                "전조 다이스는 정가공(Z−)만 — Z축 시작점(Z$zs)이 가공 목표점보다 커야 합니다 (척 쪽으로 전조, 예: 5 → −20)."
        }
    }
    RollWarning.RATIO_OVER_ONE -> {
        val ratio = sol?.ratio ?: s.rollInput(r).ratio
        val feedRef = RollHeadCatalog.data.feed.refs.firstOrNull()?.let { " (${rollSourceText(it)})" } ?: ""
        "이송 비율${ratio?.let { " ${plain(it)}" } ?: ""} 이 1 보다 큽니다 — 기계 이송이 피치보다 빨라, 공작물 1회전에 1피치씩 스스로 당겨 들어가는 머리를 기계가 앞서 밉니다. " +
            "Fette 는 이송을 피치보다 5 % 작게(× ${plain(RollHeadCalc.FETTE_RATIO)}) 줍니다$feedRef."
    }
    RollWarning.RPM_OUT_OF_RANGE -> {
        val range = sol?.rpmRange?.let { (lo, hi) -> "S$lo ~ $hi" } ?: ""
        val row = sol?.speedRow?.let { "${s.rollSteel.label}(${it.materialNo ?: ""}) ${speedSpan(it)} m/min" } ?: ""
        val dv = sol?.blank?.used?.let { " · 전조 전 지름 Ø${Fmt.fixed3(it)}" } ?: ""
        "넣은 회전수${sol?.let { " S${it.rpm}" } ?: ""} 이 Fette 재질 표 범위($row → $range$dv) 밖입니다 — 나사면이 깨끗하지 않거나 두 줄이 겹친(이중 리드) 모양이 될 수 있습니다. " +
            "범위 안으로 맞추세요."
    }
    RollWarning.HEAD_RANGE_COMPANY -> {
        val h = sol?.head
        val pair = sol?.companyHead?.let { "${it.model} · ${it.roll}" } ?: ""
        val d = s.spec?.let { "(Ø${plain(it.d)})" } ?: ""
        "회사 롤 짝 $pair 인데 이 규격${d}이 카탈로그 지름 범위 ${h?.let(::rollHeadRange) ?: ""} 밖입니다 — 회사 표대로 계산했습니다. 머리 · 롤을 확인하세요."
    }
    RollWarning.ROLL_NOT_LISTED -> {
        val h = sol?.head
        val spec = s.spec
        val (what, range) = when (spec?.kind) {
            ThreadKind.UN -> "${spec.tpi}산" to "${h?.tpiMin?.let(::plain) ?: "?"}–${h?.tpiMax?.let(::plain) ?: "?"}산"
            ThreadKind.METRIC -> "피치 ${plain(spec.pitch)}" to "피치 ${h?.pitchMinMm?.let(::plain) ?: "?"}–${h?.pitchMaxMm?.let(::plain) ?: "?"}"
            null -> "" to ""
        }
        "$what 이 ${h?.key ?: ""} 카탈로그 롤 목록($range — 목록의 최소 · 최대, 공식 범위 칸이 아님) 밖입니다 — 맞는 롤이 있는지 따로 확인하세요."
    }
    RollWarning.ROLL_LENGTH_OVER -> {
        val len = sol?.rolledLength?.let(Fmt::fixed3) ?: ""
        val a = sol?.openingMovement?.let(::plain) ?: ""
        val max = sol?.maxRollLength?.let(::plain) ?: ""
        "전조 길이 $len mm((끝면 Z − 끝 Z) + 열림 행정 a $a)가 ${sol?.head?.key ?: ""} 의 최대 전조 길이 $max mm(카탈로그 선택표)보다 깁니다 — " +
            "가공 목표점이나 머리를 확인하세요."
    }
    RollWarning.DWELL_ZERO -> {
        val ref = RollHeadCatalog.data.endDwell.refs.firstOrNull()?.let { "(${rollSourceText(it)})" } ?: ""
        "끝 멈춤이 0 이라(G4 줄 없음) 머리가 열리기 전에 급송(G0 물러남)으로 물러납니다 — Fette 는 머리가 열릴 만큼 충분히 머물라고 합니다$ref. " +
            "끝 멈춤을 넣으세요(처음 값 ${plain(RollHeadCalc.DWELL_DEFAULT)} 초)."
    }
}

/** NC 카드의 이송 사실 한 줄: 20산 회사 값은 F 그대로, 그 밖은 피치 × 비율 */
internal fun rollFeedFact(sol: RollSolution): String =
    if (sol.ratioBasis == RollRatioBasis.COMPANY) "F${Fmt.word(sol.feed)} = 회사 20산" else "F = 피치 × ${plain(sol.ratio)}"

/**
 * 전조 NC 카드 설명 — 이송을 멈추면 머리가 열림 행정 a 만큼 더 나가며 열린다(실제 전조 끝 = 끝 Z − a), 다음 공작물 전에 닫기, G97 S 는 근거 값,
 * 전조 전 지름은 이 글에 없다(앞 공정 정삭 X)
 */
internal fun rollNcExplain(retract: Double, a: Double?): String =
    "전조 머리는 X0.(머리가 공작물 중심)에서 Z 로만 움직입니다. Z20. 에서 Z축 시작점으로 급송한 뒤 G1 로 가공 목표점까지 들어갑니다 — " +
        "롤이 기울어져 있어 머리가 공작물 1회전에 1피치씩 스스로 당겨 들어가고, 기계 이송(F — 회전당 이송 G99)은 피치보다 조금 작게 따라갑니다. " +
        "끝에서 이송을 멈추면(끝 멈춤 G4 U — 초, 열릴 만큼 충분히) 머리가 열림 행정 a${a?.let { " ${plain(it)} mm" } ?: ""} 만큼 더 나가며 저절로 열리고, " +
        "Z${Fmt.word(retract)} 로 물러납니다 — 실제 전조 끝은 가공 목표점보다 a 만큼 척 쪽입니다(결과의 '실제 전조 끝'). " +
        "열린 머리는 다음 공작물 전에 닫아 둡니다(닫힘 캠 · 끝 스톱 등 — 잊으면 다음 회는 열린 채 지나가 나사가 생기지 않음). " +
        "G97 S 는 근거 값(넣은 값 · 회사 값 · Fette 추천)입니다. 전조 전 지름은 이 글에 들어가지 않습니다 — 앞 공정 정삭 X 로 깎아 둡니다. " +
        "공구 호출(T) · 주축 회전(M03) · 냉각은 기계에 맞게 앞에 넣습니다."

/** 회전수 칸 아래 근거 글(U-5): 넣은 값 · 회사 값 (소재 종류 · 개수) · Fette 추천 (강종 · 속도 아래 끝). 스테인리스가 아니면 회사 S 를 왜 안 썼는지 */
private fun rpmBasisText(s: ThreadState, sol: RollSolution): String = when (sol.rpmBasis) {
    RollRpmBasis.INPUT -> when {
        s.rollStainless -> "넣은 값"
        CompanyRolls.of(s.spec)?.stock(s.rollStock)?.rpm != null -> "넣은 값 · 회사 S 는 스테인리스 기준 (소재 ${s.material?.name ?: ""})"
        else -> "넣은 값 · 이 소재는 Fette 추천 없음"
    }
    RollRpmBasis.COMPANY -> {
        val n = CompanyRolls.of(s.spec)?.stock(s.rollStock)?.rpmCount
        "회사 값 (${rollStockText(s.rollStock)}${n?.let { " · ${it}개" } ?: ""})"
    }
    RollRpmBasis.FETTE -> "Fette 추천 · ${s.rollSteel.label} ${sol.speedRow?.vMinMpm?.let(::plain) ?: ""} m/min 아래 끝"
}

/** 결과 칸(이름 · 값 · 아래 글) — 화면과 골든이 같이 쓴다 */
internal fun rollResultCells(s: ThreadState, r: RollStore, sol: RollSolution): List<Triple<String, String, String>> {
    val i = s.rollInput(r)
    val head = sol.head
    val pair = sol.companyHead
    return listOf(
        Triple("회전수 S", "S${sol.rpm}", rpmBasisText(s, sol)),
        Triple("전조 속도 v", oneDecimal(sol.vUsed), sol.speedRow?.let { "범위 ${speedSpan(it)} m/min" } ?: "m/min = π × dV × S / 1000"),
        Triple(
            "이송 F",
            "F${Fmt.word(sol.feed)}",
            when (sol.ratioBasis) {
                RollRatioBasis.COMPANY -> "비율 ${plain(sol.ratio)} · 회사 20산"
                RollRatioBasis.FETTE -> "비율 ${plain(sol.ratio)} · Fette"
                RollRatioBasis.INPUT -> "넣은 비율 ${plain(sol.ratio)}"
            },
        ),
        Triple("분당 이송", oneDecimal(sol.feedPerMin), "mm/min = S × F"),
        Triple("가공 시간 (초)", oneDecimal(sol.timeSec), "G1 + 끝 멈춤 ${plain(i.dwell)} 초"),
        Triple(
            "머리 · 롤",
            pair?.let { "${head.key} · ${it.roll}" } ?: "${head.key} · 회사 롤 없음",
            "${if (pair != null) "회사 롤 · " else ""}카탈로그 ${rollHeadRange(head)}",
        ),
        Triple(
            "실제 전조 끝",
            sol.rollEndZ?.let { "Z${Fmt.nc(it)}" } ?: "—",
            sol.openingMovement?.let { "끝 Z − 열림 행정 a ${plain(it)}" } ?: "열림 행정 a 모름",
        ),
        Triple(
            "전조 길이",
            sol.rolledLength?.let(Fmt::fixed3) ?: "—",
            "끝면부터 + a" + (sol.maxRollLength?.let { " · 최대 ${plain(it)}" } ?: ""),
        ),
    )
}

/**
 * 전조 전 지름 칸(이름 · 값 · 아래 글) — 회사 값(YM 원문에서 확인한 값만 · 개수 / 회사 표 규격인데 아직 없으면 '확인 전 — 없음' / 회사 표 밖) │
 * 추천 값(Fette d2 − 0.03 · 범위) │ 전조 뒤 바깥지름(기본 급수 2A · 6g 범위). 사이즈가 없으면 비었다 — 화면과 골든이 같이 쓴다
 */
internal fun rollBlankCells(s: ThreadState): List<Triple<String, String, String>> {
    val spec = s.spec ?: return emptyList()
    val b = s.rollDefaults.blank ?: return emptyList()
    val cls = ThreadClass.default(spec.kind, false)
    val crest = ThreadTol.limits(spec, cls).crest
    val companySub = when {
        b.company != null -> "YM" + (b.companyCount?.let { " · ${it}개" } ?: "")
        CompanyRolls.of(spec) != null -> "확인 전 — 없음"
        else -> "회사 롤 없음"
    }
    return listOf(
        Triple("회사 값 (${rollStockText(s.rollStock)})", b.company?.let { "Ø${Fmt.fixed3(it)}" } ?: "—", companySub),
        Triple("추천 값", "Ø${Fmt.fixed3(b.rec)}", "Fette d2 − ${plain(RollHeadCalc.BLANK_OFFSET)} · Ø${Fmt.fixed3(b.recMin)} ~ ${Fmt.fixed3(b.recMax)}"),
        Triple("전조 뒤 바깥지름", "Ø${Fmt.fixed3(crest.min)} ~ ${Fmt.fixed3(crest.max)}", "${cls.label} 바깥지름 범위"),
    )
}

/** '전조 조건' 카드 아래 참고 한 줄: Fette 이송 비율 · 고른 강종의 속도 범위 · 출처(회사 값과 섞지 않음) */
internal fun rollReferenceText(s: ThreadState): String {
    val feedRef = RollHeadCatalog.data.feed.refs.firstOrNull()?.let { " — ${rollSourceText(it)}" } ?: ""
    val row = if (s.rollStainless) RollHeadCatalog.speed(s.rollSteel.materialNo) else null
    val speed = row?.let { r ->
        " · ${s.rollSteel.label}(${r.materialNo ?: ""}) 속도 ${speedSpan(r)} m/min${r.refs.firstOrNull()?.let { " — ${rollSourceText(it)}" } ?: ""}"
    } ?: " · 이 소재는 Fette 속도 줄을 쓰지 않음"
    return "참고 (Fette — 회사 값과 따로): 이송 ${plain(RollHeadCalc.FETTE_RATIO)} × 피치$feedRef$speed."
}

/** 머리 · 롤 칸 글: 쓰는 머리(고른 것 · 처음 값) "F34 · AT08" · "F23 · 회사 롤 없음", 사이즈 전 '사이즈를 고르면', 맞는 머리가 없으면 '맞는 머리 없음' */
internal fun rollHeadText(s: ThreadState): String {
    if (s.spec == null) return "사이즈를 고르면"
    val d = s.rollDefaults
    val h = d.head ?: return "맞는 머리 없음"
    return d.companyHead?.let { "${h.key} · ${it.roll}" } ?: "${h.key} · 회사 롤 없음"
}

/** 머리 · 롤 시트 줄(글 · 아래 글): 회사 짝 "F34 · AT08" + "회사 롤 · 처음 값" / "회사 롤", 그 밖 "F2" + "회사 롤 없음 · Ø8–16"(처음 값이면 뒤에 표) */
internal fun rollHeadRow(spec: ThreadSpec, key: String): Pair<String, String> {
    val company = CompanyRolls.of(spec)
    val pair = company?.heads?.firstOrNull { it.model == key }
    if (pair != null) return "$key · ${pair.roll}" to (if (pair == company.defaultHead) "회사 롤 · 처음 값" else "회사 롤")
    val range = RollHeadCatalog.head(key)?.let(::rollHeadRange) ?: ""
    val first = RollHeadCalc.pickHead(spec, null)?.key == key
    return key to "회사 롤 없음 · $range${if (first) " · 처음 값" else ""}"
}

/** 결과 띠 · 키패드 한 줄: 풀리면 시작(쓴 시작 Z — 빈칸 5) · 끝(X0 · G1 Z)과 노란 경고 수, 멈췄으면 첫 경고의 첫 문장, 값이 모자라는 동안은 null */
private fun rollDock(s: ThreadState, r: RollStore, result: RollResult, errors: List<RollWarning>, c: AppColors): DockStatus? {
    val sol = result.solution ?: return errors.firstOrNull()?.let { DockStatus.Error(firstSentence(rollWarningText(it, s, r))) }
    val ze = s.num(ThreadField.Z_END) ?: return null
    val rows = listOf(
        DockRow("시작", Fmt.fixed3(0.0), Fmt.fixed3(sol.zStart), c.drawT1, c.threadAuto()),
        DockRow("끝", Fmt.fixed3(0.0), Fmt.fixed3(ze), c.drawT2, c.threadAuto()),
    )
    return if (result.warnings.isNotEmpty()) DockStatus.Warn(result.warnings.size, rows) else DockStatus.Ok(rows)
}

/**
 * 머리 · 롤 시트(U-7): 회사 짝(처음 값이 먼저) 다음 지름 범위에 드는 다른 EVOline 머리. 고르면 그 머리(규격의 처음 값을 고르면 null — 처음 값을 따름)
 */
@Composable
internal fun RollHeadSheet(s: ThreadState, onDismiss: () -> Unit) {
    val spec = s.spec
    val shown = s.rollDefaults.head?.key
    ChoiceSheet(
        "머리 · 롤",
        spec?.let(::specTitle) ?: "",
        "회사 롤 짝이 먼저 — 그다음 지름 범위(카탈로그)에 드는 다른 LMT Fette EVOline 머리입니다. 회사 롤이 없는 머리는 맞는 롤을 따로 확인하세요.",
        onDismiss,
    ) {
        if (spec == null) return@ChoiceSheet
        val first = RollHeadCalc.pickHead(spec, null)?.key
        s.rollHeadChoices.forEach { key ->
            val (text, sub) = rollHeadRow(spec, key)
            ChoiceRow(text, sub, key == shown) {
                s.rollHead = if (key == first) null else key
                onDismiss()
            }
        }
    }
}
