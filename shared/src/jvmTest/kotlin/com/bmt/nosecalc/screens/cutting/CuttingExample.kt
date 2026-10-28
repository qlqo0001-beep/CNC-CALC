package com.bmt.nosecalc.screens.cutting

import com.bmt.nosecalc.calc.IsoGroup

/*
 * 스크린샷 · 골든이 같이 쓰는 재료·절삭조건 화면 상태. 화면은 빈칸 · 안 고름에서 시작하므로 calc-design.md 의 손 계산 값과 카탈로그 행을 넣는다.
 */

private fun basic(vararg v: Pair<CutBox, String>) = CuttingState().apply { v.forEach { (b, t) -> set(b, t) } }

/** 손 계산(CuttingTest.handNumbers): Ø50 · Vc150 → n 954.930, f0.2 → vf 190.986, L120 → 0.628 분, r0.8 → Rmax 6.250 · Ra 1.604 */
internal fun cutHand() = basic(CutBox.D to "50", CutBox.VC to "150", CutBox.F to "0.2", CutBox.LENGTH to "120", CutBox.NOSE_R to "0.8")

/** 회전수를 넣고 Vc 를 구함 · 날당 이송 0.05 × 3날 */
internal fun cutFromN() = basic(CutBox.D to "50", CutBox.N to "1000", CutBox.FZ to "0.05", CutBox.Z to "3", CutBox.LENGTH to "30")

/** G96 막힘: Vc200 · S2000 → 막히는 지름 31.831, Ø20 → 3183.1 이 2000 으로(주속 125.66) */
internal fun cutG96() = basic(CutBox.D to "20", CutBox.VC to "200", CutBox.F to "0.15", CutBox.LENGTH to "40", CutBox.SMAX to "2000")

/** 잘못 넣은 칸: 지름 0 — 그 칸에 기대는 회전수 · 분당 이송 · 시간만 비고 거칠기는 나온다 */
internal fun cutBad() = basic(CutBox.D to "0", CutBox.VC to "150", CutBox.F to "0.3", CutBox.LENGTH to "50", CutBox.NOSE_R to "0.8")

/** 이송이 노즈 R 의 두 배 이상 */
internal fun cutFeedTooBig() = basic(CutBox.D to "40", CutBox.VC to "120", CutBox.F to "1.8", CutBox.NOSE_R to "0.8")

private fun drill(maker: String, series: Int, row: Int?, iso: IsoGroup? = null, vararg v: Pair<CutBox, String>) = CuttingState(
    tab = CutTab.DRILL, maker = maker, series = series, row = row, iso = iso,
    values = v.associate { (b, t) -> b.key to t },
).apply { row?.let { pickRow(it) } }

/** 드릴링 처음(아무것도 안 고름) */
internal fun drillEmpty() = CuttingState(tab = CutTab.DRILL)

/** ISCAR SOLIDDRILL · P 군만 고름(행 목록) */
internal fun drillIscarRows() = drill("iscar", 0, null, IsoGroup.P)

/** 손 계산: Sandvik 860-GM P1.1(125 HB) Ø10 → Vc 120 · 145 · 170, n 3819.7 · 4615.5 · 5411.3, f 0.20 · 0.25 · 0.30, vf 763.9 · 1153.9 · 1623.4 */
internal fun drillSandvik() = drill("sandvik", 0, 0, null, CutBox.DRILL_D to "10")

/** 손 계산: Mitsubishi MVE 연강 Ø6.3 → 표 4000 rpm, Vc 환산 79.17, vf 520 · 800 · 1040 */
internal fun drillMitsubishi() = drill("mitsubishi", 0, 0, null, CutBox.DRILL_D to "6.3")

/** 행 범위 밖: Sandvik 870 저합금강 경화 [Ø10-20.99] 에 Ø25 → 멈춤 + 같은 재질 [Ø21-33] 행(5) 안내 */
internal fun drillOutsideRow() = drill("sandvik", 1, 4, null, CutBox.DRILL_D to "25")

/** 이송 표 구간 사이: 860-GM 은 Ø4 · Ø6 열뿐 — Ø5 는 f · vf 없음 */
internal fun drillBetween() = drill("sandvik", 0, 0, null, CutBox.DRILL_D to "5")

/** 시리즈 주문 범위(Ø3~16) 밖이지만 표에 Ø20 열이 있는 행 */
internal fun drillOutsideSeries() = drill("sandvik", 0, 2, null, CutBox.DRILL_D to "20")

/** 표에 이송이 없는 행(Mitsubishi TAFL — 각주만) */
internal fun drillNoFeed() = drill("mitsubishi", 11, 0, null, CutBox.DRILL_D to "20")

/** 표의 Vc 칸이 빈 행(ISCAR SOLIDDRILL No.4) */
internal fun drillNoVc() = drill("iscar", 0, 3, null, CutBox.DRILL_D to "8")

/** 직접 입력: Ø10 · Vc100 · f0.2 → n 3183.1 · vf 636.6 */
internal fun drillManual() = CuttingState(
    tab = CutTab.DRILL, maker = MANUAL,
    values = mapOf(CutBox.DRILL_D.key to "10", CutBox.DRILL_VC.key to "100", CutBox.DRILL_F.key to "0.2"),
)

/** 직접 입력 · 이송 빈칸(회전수만) · 잘못된 Vc */
internal fun drillManualBad() = CuttingState(
    tab = CutTab.DRILL, maker = MANUAL,
    values = mapOf(CutBox.DRILL_D.key to "10", CutBox.DRILL_VC.key to "0"),
)
