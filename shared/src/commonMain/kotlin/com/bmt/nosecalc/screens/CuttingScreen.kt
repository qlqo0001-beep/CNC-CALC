package com.bmt.nosecalc.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.screens.cutting.BasicTab
import com.bmt.nosecalc.screens.cutting.CutBox
import com.bmt.nosecalc.screens.cutting.CutTab
import com.bmt.nosecalc.screens.cutting.CuttingState
import com.bmt.nosecalc.screens.cutting.DrillTab
import com.bmt.nosecalc.screens.cutting.MaterialTab
import com.bmt.nosecalc.screens.cutting.UnderlineTabs
import com.bmt.nosecalc.screens.taper.TaperPage
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.HelpSection
import com.bmt.nosecalc.ui.rememberFieldChain

/** 재료·절삭조건 */
@Composable
fun CuttingScreen() = CuttingScreen(CuttingState())

/**
 * 재료·절삭조건 화면(원본 03): 헤더 아래 밑줄 탭 셋(기본계산기 · 재료분석 · 드릴링) — 탭 줄은 고정, 아래 본문만 스크롤한다.
 * 숫자칸은 한 묶음(키패드 '다음'), 값이 바뀌면 바로 다시 계산한다. 탭을 바꾸면 키패드를 닫는다. 도움말은 지금 탭의 묶음부터 연다.
 * initial = 시험이 정한 처음 상태, editing = 처음부터 키패드를 열어 둘 칸(스크린샷 시험용)
 */
@Composable
internal fun CuttingScreen(initial: CuttingState, editing: CutBox? = null) {
    val s = rememberSaveable(saver = CuttingState.Saver) { initial }
    val chain = rememberFieldChain(editing?.key)
    val app = LocalAppState.current
    FeatureScaffold(Feature.CUTTING, CuttingHelp, helpStart = s.tab.ordinal + 1) {
        FieldChainHost(chain) {
            Column(Modifier.fillMaxSize()) {
                UnderlineTabs(CutTab.entries.map { it.label }, s.tab.ordinal, {
                    chain.close()
                    s.tab = CutTab.entries[it]
                })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // 탭마다 따로 부른다 — 스크롤은 탭을 바꾸면 맨 위에서
                    when (s.tab) {
                        CutTab.BASIC -> TaperPage(null, chain.sheetOpen) {
                            BasicTab(s)
                            if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
                        }
                        CutTab.MATERIAL -> TaperPage(null, false) { MaterialTab() }
                        CutTab.DRILL -> TaperPage(null, chain.sheetOpen) {
                            DrillTab(s) { app.toast(it) }
                            if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 헤더 도움말: 탭 셋 · 기본계산기 · 재료분석 · 드릴링 · 경고 (지금 탭의 묶음부터 열린다) */
internal val CuttingHelp = listOf(
    HelpSection(
        "탭 셋",
        listOf(
            "기본계산기 = 절삭 속도 ↔ 회전수, 이송, 가공 시간, 이론 거칠기, G96 회전수 제한. 재료분석 = ISO 피삭재 여섯 군과 대표 재질의 규격 대조. " +
                "드릴링 = 메이커 카탈로그의 드릴 절삭 조건표에서 직경별 Vc · 회전수 · 이송 · 분당 이송.",
            "칸은 빈칸에서 시작하고 값을 바꾸면 바로 다시 계산합니다(계산 단추 없음). 다른 기능에 다녀와도 넣은 값은 남습니다.",
        ),
    ),
    HelpSection(
        "기본계산기",
        listOf(
            "지름 D 와 절삭 속도 Vc 를 넣으면 회전수 n = 1000 × Vc / (π × D), 회전수를 넣으면 Vc = π × D × n / 1000 입니다. " +
                "Vc 와 n 중 나중에 넣은 쪽이 기준이 되고, 다른 칸은 청록 '자동' 값으로 바뀝니다.",
            "이송: 회전당 이송 f 를 넣거나, 드릴처럼 날이 여럿이면 날당 이송 fz 와 날 수 Z 를 넣습니다(f = fz × Z, 날 수를 비우면 2날). " +
                "분당 이송 vf = f × n, 가공 시간 = 길이 L / vf 입니다(회전수 일정 기준).",
            "이론 거칠기(선삭): Rmax = f² / (8 × 노즈 R) — KORLOY 카탈로그 식, Ra 는 같은 단면의 산술 평균(f² / (18√3 × r)). " +
                "실제 거칠기는 이론값보다 커서 카탈로그는 강 1.5~3배 · 주철 3~5배로 봅니다. f 가 노즈 R 의 두 배 이상이면 내지 않습니다.",
            "G50 S 최대 회전수를 넣으면 G96(주속 일정)에서 회전수가 막히기 시작하는 지름을 냅니다. 지름 D 가 그보다 작으면 노란 경고와 함께 " +
                "막힌 회전수로 낸 실제 주속 · 분당 이송을 보입니다. NC 줄은 만들지 않습니다(S · M03/M04 는 기계마다).",
            "빈칸인 줄은 결과를 비웁니다 — 0 으로 계산하지 않습니다. 식의 출처는 Sandvik Coromant · KORLOY · Haas 공식 자료와 FANUC 매뉴얼입니다.",
        ),
    ),
    HelpSection(
        "재료분석",
        listOf(
            "ISO 513 의 피삭재 여섯 군: P 강(파랑) · M 스테인리스강(노랑) · K 주철(빨강) · N 비철금속(초록) · S 내열합금 · 티타늄(갈색) · H 고경도강(회색). " +
                "색 띠는 인서트 상자 · 카탈로그 표와 같은 군 식별색이고 경고 색이 아닙니다.",
            "군마다 들어가는 재료와 가공 특성(Mitsubishi Materials 교육 자료 요약), 대표 재질의 KS · JIS · ISO · 미국 기호 대조(KORLOY 선삭 카탈로그 대조표)를 보입니다. " +
                "절삭 속도 숫자는 넣지 않았습니다 — 드릴은 드릴링 탭, 선삭은 인서트 카탈로그를 보세요.",
        ),
    ),
    HelpSection(
        "드릴링",
        listOf(
            "① 메이커 → ② 공구 종류 → ③ 피삭재(ISO 군을 누르고 재질 행을 고름) → ④ 직경 순서입니다. 처음엔 아무것도 골라져 있지 않습니다 — " +
                "파란 안내가 남은 것을 적습니다. 메이커를 바꾸면 공구 종류 · 행을, 공구 종류를 바꾸면 행을 다시 고릅니다.",
            "결과 표는 카탈로그 값 그대로입니다: Vc 최소 · 권장 · 최대(표에 권장이 없으면 비움 — 가운데 값을 만들지 않음), 회전수 = 1000 × Vc / (π × D), " +
                "이송 f = 그 직경이 든 표 구간의 값(보간 없음), 분당 이송 = f × n. Mitsubishi 처럼 직경마다 회전수를 주는 표는 그 회전수를 쓰고 Vc 는 환산 값입니다.",
            "카드 아래 출처 줄에 카탈로그 이름 · 판 · 쪽이 있습니다. 깊이별 이송 감소 · 외부 급유 감소 같은 각주는 계산에 넣지 않았습니다 — 공구 카드의 깊이 · 급유 글과 " +
                "'카탈로그 각주' 를 보고 줄이세요.",
            "직접 입력: 카탈로그 없이 직경 · Vc · f 를 넣어 회전수 · 분당 이송을 냅니다. 원본의 대구텍 · Gühring · OSG 는 확인한 자료가 없어 넣지 않았습니다.",
        ),
    ),
    HelpSection(
        "경고",
        listOf(
            "파란 띠는 안내(남은 값 · 이송 없이 회전수만), 노란 띠는 숫자는 나왔지만 확인할 것(카탈로그 주문 범위 밖 직경 · 이송 표의 직경 사이 · 표에 이송이나 Vc 칸이 없음 · " +
                "노즈 R 보다 큰 이송 · G96 회전수 막힘), 빨간 띠는 계산을 멈춘 것입니다(범위 밖 값 · 그 행의 직경 범위 밖).",
            "행의 직경 범위 밖이면 같은 재질의 다른 직경 행이 있을 때 그 행으로 가는 단추가 빨간 띠 아래에 있습니다.",
        ),
    ),
)
