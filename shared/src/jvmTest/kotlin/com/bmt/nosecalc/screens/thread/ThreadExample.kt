package com.bmt.nosecalc.screens.thread

import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.UnGroup

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
    family = ThreadFamily.UN,
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

/*
 * 나사 확장(2026-11-05)의 상태 — 치수는 모두 새로 지은 값이다.
 */

private fun pipe(kind: PipeKind, size: String) = PipeTable.of(kind).single { it.size == size }

/** UNEF 묶음: 내경 9/16-24 UNEF 2B · Z 4 → −18 · Δd 0.12 · 870 rpm */
internal fun threadUnef() = ThreadState(
    inner = true,
    family = ThreadFamily.UN,
    spec = un("9/16", 24),
    unGroup = UnGroup.UNEF,
    values = mapOf("zStart" to "4", "zEnd" to "-18", "firstCut" to "0.12", "rpm" to "870"),
)

/** 20UN 묶음에서 3/4 → 3/4-20 UNEF (20UN), 외경 2A · Z 7 → −23.5 · Δd 0.22 · 640 rpm */
internal fun thread20Un() = ThreadState(
    family = ThreadFamily.UN,
    unGroup = UnGroup.UN20,
    values = mapOf("zStart" to "7", "zEnd" to "-23.5", "firstCut" to "0.22", "rpm" to "640"),
).apply { pickSize("3/4") }

/** PT 숫나사 R 3/4 · 기준면 빈칸(0) · Z 5 → −15 · Δd 0.35 · 480 rpm (I 꼴) */
internal fun threadPtExt() = ThreadState(
    family = ThreadFamily.PT,
    pipe = pipe(PipeKind.PT, "3/4"),
    values = mapOf("zStart" to "5", "zEnd" to "-15", "firstCut" to "0.35", "rpm" to "480"),
)

/** PT 암나사 Rc 1 · 기준면 Z−2 · Z 3 → −21 · 게이지 면 옮김 +0.5산 · Δd 0.3 · 410 rpm · 고급 설정 펼침 */
internal fun threadPtInt() = ThreadState(
    inner = true,
    family = ThreadFamily.PT,
    pipe = pipe(PipeKind.PT, "1"),
    values = mapOf("zRef" to "-2", "zStart" to "3", "zEnd" to "-21", "gaugeShift" to "0.5", "firstCut" to "0.3", "rpm" to "410"),
    advOpen = true,
)

/** NPT 숫나사 3/4-14 · Z 4 → −12 (나사 길이 12 < L2 — 노란 '나사 길이 짧음') · Δd 0.3 · 520 rpm */
internal fun threadNptExt() = ThreadState(
    family = ThreadFamily.NPT,
    pipe = pipe(PipeKind.NPT, "3/4"),
    values = mapOf("zStart" to "4", "zEnd" to "-12", "firstCut" to "0.3", "rpm" to "520"),
)

/** NPT 암나사 1-11.5 · 기준면 0 · Z 4 → −28 · Δd 0.28 · 테이퍼 낱말 R */
internal fun threadNptInt() = ThreadState(
    inner = true,
    family = ThreadFamily.NPT,
    pipe = pipe(PipeKind.NPT, "1"),
    values = mapOf("zRef" to "0", "zStart" to "4", "zEnd" to "-28", "firstCut" to "0.28"),
    taperWord = 'R',
)

/*
 * 다이스 헤드(2026-11-06)의 상태 — 치수 · 절삭 속도는 새로 지은 값이다. 다이스 조건(회사 값)은 상태가 아니라 저장소라 [threadDieStore] 로 따로 넣는다.
 */

/** 다이스 헤드: 외경 M16×2 · 소재 P · Z 5 → −24 · 끝면 빈칸(0) · 카탈로그 줄 Wagner ZR 26-I '구조용강 500 이하' */
internal fun threadDie() = ThreadState(
    spec = metric("M16", 2.0),
    material = IsoGroup.P,
    values = mapOf("zStart" to "5", "zEnd" to "-24"),
    method = ThreadMethod.DIE,
    dieSpeed = "WAG_ZR26I|Construction steel up to 500 N/m²",
)

/** [threadDie] 의 다이스 조건(저장소 열쇠 → 글): P 군 Vc 8 · 플로트 1.5 · 목 2.5산, 비율 · 멈춤 · 물러남은 빈칸(처음 값) */
internal fun threadDieStore(): Map<String, String> = mapOf(
    DieStore.key(DieField.VC, IsoGroup.P) to "8",
    DieStore.key(DieField.FLOAT, null) to "1.5",
    DieStore.key(DieField.THROAT, null) to "2.5",
)

/** 다이스 헤드 처음(사이즈 · 값 없음) — 파란 안내 */
internal fun threadDieEmpty() = ThreadState(method = ThreadMethod.DIE)
