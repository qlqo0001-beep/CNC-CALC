package com.bmt.nosecalc.screens.polar

import com.bmt.nosecalc.calc.PolarShape

/*
 * 스크린샷 · 골든이 같이 쓰는 극좌표 화면 상태. 화면은 빈칸에서 시작하므로 원본 12 · 07 의 값과 calc-design.md 의 손 계산 값을 넣는다.
 * 공구경 10 · 깊이 −5 · F100 은 원본에 안 보이는(잘린) 칸이라 calc 테스트(PolarG12Test)와 같은 예시 값이다 — 앱의 기본값이 아니다.
 */

private val TOOL = mapOf("tool" to "10", "depth" to "-5", "feed" to "100")

/** 원본 12: 외측 육각형 · 외경 80 · 맞변 70 · 코너 R 3 · 회전 0(빈칸) */
internal fun polarOriginal12() = PolarState(values = mapOf("od" to "80", "w" to "70", "rc" to "3") + TOOL)

/** 원본 07 의 프로그램(맞변 80 · R8 — calc `originalHexagonLines` 의 외경 100) */
internal fun polarOriginal07() = PolarState(values = mapOf("od" to "100", "w" to "80", "rc" to "8") + TOOL)

/** calc 손 계산 내측 사각형: W40 · R6 · 기초 구멍 Ø20 — 살이 두꺼워 노란 경고(STOCK_LEFT) */
internal fun polarInnerSquare() = PolarState(inner = true, shape = PolarShape.SQUARE, values = mapOf("od" to "20", "w" to "40", "rc" to "6") + TOOL)

/** 내측 육각형 · 코너 R 3 < 공구 반경 5 — 노란 경고 둘(INNER_CORNER_LT_TOOL · STOCK_LEFT) */
internal fun polarInnerSmallR() = PolarState(inner = true, values = mapOf("od" to "30", "w" to "50", "rc" to "3") + TOOL)

/** calc 손 계산 2면폭: 외경 48 · W30 */
internal fun polarTwoFlat() = PolarState(shape = PolarShape.TWO_FLAT, values = mapOf("od" to "48", "w" to "30") + TOOL)

/** 홀수 각: 외측 삼각형 · 내접원 지름 40 · 외경 90 · R4 · 회전 15° · X 반경 지령 */
internal fun polarTriangle() = PolarState(shape = PolarShape.TRIANGLE, xDiameter = false, values = mapOf("od" to "90", "w" to "40", "rc" to "4", "rot" to "15") + TOOL)

/** W ≥ 외경 — 빨간 멈춤 · 두 칸 빨간 테 */
internal fun polarWgeOd() = PolarState(values = mapOf("od" to "60", "w" to "70", "rc" to "3") + TOOL)

/** 값이 모자라는 동안 범위 밖 값(깊이 +5) — 파란 안내와 빨간 띠가 같이 */
internal fun polarBadWhileWaiting() = PolarState(values = mapOf("od" to "80", "depth" to "5"))
