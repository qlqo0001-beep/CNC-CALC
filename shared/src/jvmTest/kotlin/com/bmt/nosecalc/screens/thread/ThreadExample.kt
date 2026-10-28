package com.bmt.nosecalc.screens.thread

import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable

/*
 * 스크린샷 · 골든이 같이 쓰는 나사 화면 상태. 화면은 빈칸에서 시작하므로 원본 08 의 값과 calc-design.md 의 손 계산 값을 넣는다.
 */

private fun un(size: String, tpi: Int) = ThreadTable.pitches(ThreadKind.UN, size).first { it.tpi == tpi }
private fun metric(size: String, pitch: Double) = ThreadTable.pitches(ThreadKind.METRIC, size).first { it.pitch == pitch }

/**
 * 원본 08 화면: 외경 1/4-20 UNC 2A · 스테인리스 · 정가공(우) · Z축 시작점 5.0 · 1821 rpm · 가공 목표점 −25.0 · 60° · 1.0P.
 * 원본은 둘째 줄이 잘려 첫 절입이 안 보인다 — calc-design.md 손 계산(`originalScreenHandNumbers`)의 0.2 를 넣는다
 */
internal fun threadOriginal(dir: ThreadDir = ThreadDir.FORWARD, values: Map<String, String> = emptyMap()) = ThreadState(
    kind = ThreadKind.UN,
    spec = un("1/4", 20),
    material = IsoGroup.M,
    dir = dir,
    values = mapOf("zStart" to "5.0", "rpm" to "1821", "zEnd" to "-25.0", "firstCut" to "0.2") + values,
)

/** 내경 M10×1.5 6H · 공차 목표 50% (calc-design.md `metricHandNumbers` 의 X10.09 · P782) · 고급 설정 펼침 */
internal fun threadMetricInner() = ThreadState(
    inner = true,
    spec = metric("M10", 1.5),
    values = mapOf("zStart" to "3", "zEnd" to "-20", "firstCut" to "0.15", "target" to "50"),
    advOpen = true,
)

/** 외경 M10×1.5 4h — 공차가 식(표 자료 없음)이라 파란 안내 */
internal fun threadFormula() = ThreadState(
    spec = metric("M10", 1.5),
    cls = ThreadClass.M4h,
    values = mapOf("zStart" to "3", "zEnd" to "-20", "firstCut" to "0.15", "rpm" to "1200"),
)

/** 원본에 공차 목표 0% · 인선 R 0.35(> H/4 — 골이 규격 한계를 넘음)와 첫 절입 0.8(거친 절입 한 번): 노란 경고 둘 */
internal fun threadWarn() = threadOriginal(values = mapOf("target" to "0", "tipR" to "0.35", "firstCut" to "0.8")).apply { advOpen = true }

/** 정가공인데 시작 Z 가 목표보다 작다 — 빨간 멈춤 · 두 Z 칸 빨간 테 */
internal fun threadZOrder() = threadOriginal(values = mapOf("zStart" to "-30"))

/** 역가공(좌): Z −30 → 0, 파란 '기계에서 한 번 확인' */
internal fun threadReverse() = threadOriginal(ThreadDir.REVERSE, mapOf("zStart" to "-30", "zEnd" to "0"))

/** 값이 모자라는 동안 범위 밖 값(공차 목표 120 %) — 파란 안내와 빨간 띠가 같이 */
internal fun threadBadWhileWaiting() = ThreadState(values = mapOf("target" to "120"), advOpen = true)
