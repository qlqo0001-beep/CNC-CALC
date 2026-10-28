package com.bmt.nosecalc.calc

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * 나사 규격 자료 — 기본 치수는 식, 공차는 **규격 표 값**(없는 칸만 식)으로 낸다.
 *
 * 기본 형상(ISO 68-1 · ASME B1.1 같은 60° 형상): H = (√3/2)P = 0.866025P, 기본 피치경 d2 = d − 2·(3/8)H = d − 0.649519P,
 * 기본 골지름(안지름) d1 = d − 2·(5/8)H = d − 1.082532P.
 *   출처: Wikipedia "ISO metric screw thread" · "Unified Thread Standard" 의 기본 형상 식(두 규격 같은 식),
 *   NBS Handbook H28 (1969) Part I 표 2.1 의 0.649519p · 1.082532p.
 *
 * 미터(ISO 965-1) 공차 — μm, P · d 는 mm. 규격의 공차는 표 값이다(식 값을 둥근 수로 정한 것 — 반올림 규칙이 한결같지 않아
 * 식으로 다시 낼 수 없다: Td(6) P4 는 식 452 인데 표 475). 그래서 표 값을 쓰고([Iso965]), 표 자료가 없는 칸만 식을 쓴다 —
 * FED-STD-H28/21B(1984, 미국 연방 표준) 5.1.3 의 절차(공차는 ANSI/ASME B1.13M 표 8 · 10 · 11 · 12 · 13 에서 읽고, 표에 없는
 * 조합만 식 "Td2(4) = 0.63 × 0.09 P^0.4 d^0.1" 로 계산, 한계 치수는 0.001 로 반올림 — 예 1 M27×1.5 의 Td 0.236 · TD2 0.200 ·
 * TD1 0.300, 예 3 M29×1.25 의 Td 0.212). 기초 편차 표: ISO 965-1:1998 표 1. 등급 6 공차: ISO 965-2:1973 표 1~4 와
 * ASME B1.13M 한계 치수 표의 줄에서 읽은 값([Iso965] 주석). 식(표가 없는 칸 · 4h · 8g):
 *   기초 편차  g: es = −(15 + 11P),  h: es = 0,  G: EI = +(15 + 11P),  H: EI = 0
 *   바깥지름 Td(6) = 180·P^(2/3) − 3.15/√P,  등급 4 = 0.63×, 8 = 1.6×
 *   피치경  Td2(6) = 90·P^0.4·d^0.1 (d = 호칭 지름이 든 구간 경계의 기하평균),  등급 4 = 0.63×, 8 = 1.6×
 *   암나사 피치경 TD2(6) = 1.32·Td2(6)
 *   암나사 안지름 TD1(6) = 433P − 190P^1.22 (P 0.2~0.8), 230·P^0.7 (P 1 이상)
 *   출처: RoyMech "ISO Metric Screw Thread Tolerances — ISO 965"(https://www.roymech.co.uk/Useful_Tables/Screws/iso-metric-thread-tolerances/)
 *   — 이 쪽의 Td 식 글은 "− 3.15 P" 로 깨져 있으나 같은 쪽의 계산 표(M3 109, M10 233 μm)가 −3.15/√P 와 맞는다.
 *   등급 4 · 8 은 표 자료를 못 구해 식 그대로다(4h · 8g — [ThreadLimits.byFormula]).
 *   e · f 위치(작은 피치 예외를 확인 못 함)와 4H · 5H · 7H(암나사 피치경 등급 배수를 확인 못 함)는 넣지 않았다.
 *
 * UN(ASME B1.1) 공차 — 인치, p = 1/산수:
 *   피치경 공차 = C·(0.0015·∛D + 0.0015·√Le + 0.015·∛p²), C: 1A 1.5 · 2A 1.0 · 3A 0.75 · 1B 1.95 · 2B 1.30 · 3B 0.975,
 *   Le(물림 길이) = D (UNC · UNF 표의 가정).  허용차(allowance, es) = 0.3 × (C = 1 의 값), 1A · 2A 만(3A · 암나사는 0).
 *   바깥지름 공차 1A 0.090·∛p², 2A · 3A 0.060·∛p².  암나사 안지름 공차는 표 2.20(크기 · 산수에 따른 식과 상·하한).
 *   출처: NBS Handbook H28 (1969) Part I(미국 정부 간행물) 5절 · 6.1 · 6.2 · 표 2.20 · 7절,
 *   https://nvlpubs.nist.gov/nistpubs/Legacy/hb/nbshandbook28-1969p1.pdf  ·  ASME B1.1 목차 표 15 의 같은 식
 *   ("PD Tolerance = 0.0015∛D + 0.0015√LE + 0.015∛P²"). 번호 사이즈 지름 = 0.060 + 0.013·N 인치(Wikipedia UTS).
 *   공개 표(amesweb.info UNC 2A/2B, saolamfg 3A/3B 예)와 0.0001 인치 반올림 안으로 맞춰 본다.
 */

/** 나사 종류 */
enum class ThreadKind {
    /** 미터 나사 M (ISO 261/262 · 공차 ISO 965-1) */
    METRIC,

    /** 유니파이 UN (ASME B1.1 — UNC · UNF) */
    UN,
}

/** 계열 */
enum class ThreadSeries(val kind: ThreadKind) {
    /** 미터 보통 나사 */
    M_COARSE(ThreadKind.METRIC),

    /** 미터 가는 나사 */
    M_FINE(ThreadKind.METRIC),
    UNC(ThreadKind.UN),
    UNF(ThreadKind.UN),
}

/**
 * 규격 나사 하나(한 줄 나사). [d] · [pitch] 는 mm. UN 은 [tpi](산수)가 있고 피치 = 25.4 / 산수, 지름 = 인치 × 25.4.
 * [size] 는 화면에 쓰는 호칭: "M10" · "1/4" · "#10".
 */
data class ThreadSpec(
    val series: ThreadSeries,
    val size: String,
    val d: Double,
    val pitch: Double,
    val tpi: Int? = null,
) {
    val kind: ThreadKind get() = series.kind

    /** "M10×1.5" · "1/4-20 UNC" */
    val label: String
        get() = if (kind == ThreadKind.METRIC) "$size×${Fmt.nc(pitch).trimEnd('.')}" else "$size-$tpi ${series.name}"
}

/** 규격 사이즈 목록(드롭다운 자료). 사이즈 → 피치만 적었다 — 한계 치수는 [ThreadTol] 이 식으로 낸다 */
object ThreadTable {
    /**
     * ISO 262 (나사 · 볼트 · 너트용 고른 사이즈) 의 보통 나사 M1~M64 와 가는 나사 M8~M64.
     * 출처: Wikipedia "ISO metric screw thread" 의 "ISO 262 selected sizes" 표(2026-10 확인).
     */
    val METRIC: List<ThreadSpec> = buildList {
        val coarse = listOf(
            1.0 to 0.25, 1.2 to 0.25, 1.4 to 0.3, 1.6 to 0.35, 1.8 to 0.35, 2.0 to 0.4, 2.5 to 0.45, 3.0 to 0.5,
            3.5 to 0.6, 4.0 to 0.7, 5.0 to 0.8, 6.0 to 1.0, 7.0 to 1.0, 8.0 to 1.25, 10.0 to 1.5, 12.0 to 1.75,
            14.0 to 2.0, 16.0 to 2.0, 18.0 to 2.5, 20.0 to 2.5, 22.0 to 2.5, 24.0 to 3.0, 27.0 to 3.0, 30.0 to 3.5,
            33.0 to 3.5, 36.0 to 4.0, 39.0 to 4.0, 42.0 to 4.5, 45.0 to 4.5, 48.0 to 5.0, 52.0 to 5.0, 56.0 to 5.5,
            60.0 to 5.5, 64.0 to 6.0,
        )
        val fine = mapOf(
            8.0 to listOf(1.0, 0.75), 10.0 to listOf(1.25, 1.0), 12.0 to listOf(1.5, 1.25), 14.0 to listOf(1.5),
            16.0 to listOf(1.5), 18.0 to listOf(2.0, 1.5), 20.0 to listOf(2.0, 1.5), 22.0 to listOf(2.0, 1.5),
            24.0 to listOf(2.0), 27.0 to listOf(2.0), 30.0 to listOf(2.0), 33.0 to listOf(2.0), 36.0 to listOf(3.0),
            39.0 to listOf(3.0), 42.0 to listOf(3.0), 45.0 to listOf(3.0), 48.0 to listOf(3.0), 52.0 to listOf(4.0),
            56.0 to listOf(4.0), 60.0 to listOf(4.0), 64.0 to listOf(4.0),
        )
        for ((d, p) in coarse) {
            val name = "M" + Fmt.nc(d).trimEnd('.')
            add(ThreadSpec(ThreadSeries.M_COARSE, name, d, p))
            fine[d]?.forEach { add(ThreadSpec(ThreadSeries.M_FINE, name, d, it)) }
        }
    }

    /**
     * UNC · UNF #0~1-1/2. 번호 사이즈 지름 = 0.060 + 0.013·N 인치, 나머지는 분수 그대로.
     * 산수 출처: Wikipedia "Unified Thread Standard" 의 UNC/UNF 표(2026-10 확인). #0 은 UNC 가 없다.
     */
    val UN: List<ThreadSpec> = buildList {
        // 이름, 인치 지름, UNC 산수(없으면 null), UNF 산수
        val rows = listOf(
            Triple("#0", 0.060, null to 80), Triple("#1", 0.073, 64 to 72), Triple("#2", 0.086, 56 to 64),
            Triple("#3", 0.099, 48 to 56), Triple("#4", 0.112, 40 to 48), Triple("#5", 0.125, 40 to 44),
            Triple("#6", 0.138, 32 to 40), Triple("#8", 0.164, 32 to 36), Triple("#10", 0.190, 24 to 32),
            Triple("#12", 0.216, 24 to 28), Triple("1/4", 0.25, 20 to 28), Triple("5/16", 0.3125, 18 to 24),
            Triple("3/8", 0.375, 16 to 24), Triple("7/16", 0.4375, 14 to 20), Triple("1/2", 0.5, 13 to 20),
            Triple("9/16", 0.5625, 12 to 18), Triple("5/8", 0.625, 11 to 18), Triple("3/4", 0.75, 10 to 16),
            Triple("7/8", 0.875, 9 to 14), Triple("1", 1.0, 8 to 12), Triple("1-1/8", 1.125, 7 to 12),
            Triple("1-1/4", 1.25, 7 to 12), Triple("1-3/8", 1.375, 6 to 12), Triple("1-1/2", 1.5, 6 to 12),
        )
        for ((name, inch, tpis) in rows) {
            val (c, f) = tpis
            if (c != null) add(ThreadSpec(ThreadSeries.UNC, name, inch * MM_PER_INCH, MM_PER_INCH / c, c))
            add(ThreadSpec(ThreadSeries.UNF, name, inch * MM_PER_INCH, MM_PER_INCH / f, f))
        }
    }

    fun of(kind: ThreadKind): List<ThreadSpec> = if (kind == ThreadKind.METRIC) METRIC else UN

    /** 사이즈 이름(작은 것부터, 겹치지 않게) */
    fun sizes(kind: ThreadKind): List<String> = of(kind).map { it.size }.distinct()

    /** 한 사이즈의 피치들(보통 · UNC 먼저) */
    fun pitches(kind: ThreadKind, size: String): List<ThreadSpec> = of(kind).filter { it.size == size }
}

internal const val MM_PER_INCH = 25.4

/** 공차 급수. [internal] = 암나사. 화면 글은 [label] */
enum class ThreadClass(val kind: ThreadKind, val internal: Boolean, val label: String) {
    M4h(ThreadKind.METRIC, false, "4h"),
    M6g(ThreadKind.METRIC, false, "6g"),
    M6h(ThreadKind.METRIC, false, "6h"),
    M8g(ThreadKind.METRIC, false, "8g"),
    M6H(ThreadKind.METRIC, true, "6H"),
    M6G(ThreadKind.METRIC, true, "6G"),
    UN1A(ThreadKind.UN, false, "1A"),
    UN2A(ThreadKind.UN, false, "2A"),
    UN3A(ThreadKind.UN, false, "3A"),
    UN1B(ThreadKind.UN, true, "1B"),
    UN2B(ThreadKind.UN, true, "2B"),
    UN3B(ThreadKind.UN, true, "3B"),
    ;

    companion object {
        fun choices(kind: ThreadKind, internal: Boolean): List<ThreadClass> = entries.filter { it.kind == kind && it.internal == internal }

        /**
         * 기본 급수: 미터 6g/6H (FED-STD-H28/21B 5.1.1 "Standard M profile screw threads shall be tolerance class 6g for
         * external threads and 6H for internal threads"), UN 2A/2B (H28 4.2 "the most commonly used", 원본 08.jpg 도 2A)
         */
        fun default(kind: ThreadKind, internal: Boolean): ThreadClass = when (kind) {
            ThreadKind.METRIC -> if (internal) M6H else M6g
            ThreadKind.UN -> if (internal) UN2B else UN2A
        }
    }
}

/** 지름 범위(mm) */
data class Span(val min: Double, val max: Double) {
    val width: Double get() = max - min
}

/**
 * 한 규격 · 급수의 한계 치수(mm, 지름값). 숫나사는 바깥지름 · 피치경, 암나사는 안지름 · 피치경 · 바깥지름 최소.
 * 숫나사의 골지름 최소 · 암나사의 바깥지름 최대는 규격이 정하지 않는다(공구 날 끝이 정함).
 */
data class ThreadLimits(
    val spec: ThreadSpec,
    val cls: ThreadClass,
    /** 기본 삼각형 높이 H = 0.866025P */
    val h: Double,
    /** 기본 피치경 d2 = D2 */
    val basicPitch: Double,
    /** 기본 안지름(골지름) d1 = D1 */
    val basicMinor: Double,
    /** 숫나사 es(≤ 0, UN 은 −허용차) · 암나사 EI(≥ 0) */
    val deviation: Double,
    val pitchDia: Span,
    /** 산봉우리 지름: 숫나사 = 바깥지름 d, 암나사 = 안지름 D1(나사 내기 전 구멍) */
    val crest: Span,
    /**
     * 골 쪽 한계: 숫나사 = 골지름 최대(평평한 골 d1 + es — 상대 암나사 산봉우리와 안 닿는 끝, ASME B1.1 의 UN 최대 골지름 ·
     * 미터 표의 "Max. minor diameter (flat form)"), 암나사 = 바깥지름 최소 D + EI
     */
    val rootLimit: Double,
    /**
     * true = 공차 · 기초 편차 가운데 하나 이상을 규격 표 자료가 없어 식으로 냈다(규격 표와 몇 μm 다를 수 있다).
     * 미터 4h · 8g, M1~M1.4 의 6H · 6G 안지름 · 피치경, M8×0.75 의 피치경. UN 은 늘 false(ASME B1.1 이 식으로 정한다)
     */
    val byFormula: Boolean = false,
)

/**
 * ISO 965-1 미터 공차 — 표 값(μm)과 식. 표 값은 아래 출처의 줄에서 읽었다(공차 = 한계 치수의 차, 표의 한계 치수는 0.001 반올림이라
 * 정수 μm 공차는 그대로 나온다). 지름 구간 번호: 1 = 0.99~1.4, 2 = ~2.8, 3 = ~5.6, 4 = ~11.2, 5 = ~22.4, 6 = ~45, 7 = ~90 (초과~이하).
 *  - ISO 965-2:1973 표 1(6H 보통) · 2(6g 보통, M1~M1.4 는 6h) · 3(6H 가는) · 4(6g 가는) — M1~M39, iTeh 미리보기
 *    https://cdn.standards.iteh.ai/samples/2866/71172a0b1ab24e9b83a6e723b5fd79e9/ISO-965-2-1973.pdf
 *  - ASME B1.13M(Engineers Edge "External/Internal Metric Thread" 표 https://www.engineersedge.com/hardware/metric-external-thread-sizes2.htm
 *    · -sizes3 · metric-internal-thread-sizes2 · -sizes3) — M42 이상과 ISO 965-2 에 없는 가는 나사(M20×2 · M48×3 · M52×4 …).
 *    그 표의 M52×5 줄(Td2 236 · TD2 315)은 같은 지름 구간 M48×5(250 · 335)와 다르고 P4 칸(236 · 315)과 같아 오기로 보고 M48×5 를 썼다.
 *  - Optimas "ISO Metric Screw Thread Tolerances" 6g 표 — M3.5 피치경 최소(ISO 965-2 미리보기에서 안 읽힘).
 */
internal object Iso965 {
    /** 표 1 "Fundamental deviations" — G 의 EI = +값, g 의 es = −값 (ISO 965-1:1998 표 1, iTeh 미리보기 8쪽) */
    val FUNDAMENTAL_G: Map<Double, Int> = mapOf(
        0.2 to 17, 0.25 to 18, 0.3 to 18, 0.35 to 19, 0.4 to 19, 0.45 to 20, 0.5 to 20, 0.6 to 21, 0.7 to 22, 0.75 to 22, 0.8 to 24,
        1.0 to 26, 1.25 to 28, 1.5 to 32, 1.75 to 34, 2.0 to 38, 2.5 to 42, 3.0 to 48, 3.5 to 53, 4.0 to 60, 4.5 to 63, 5.0 to 71,
        5.5 to 75, 6.0 to 80, 8.0 to 100,
    )

    /** 숫나사 바깥지름 Td(6) — 피치별. P0.25 · 0.3 은 M1 · M1.4 의 6h 줄, P0.75 는 M4.5, P4.5 이상은 ASME 표 */
    val TD_6: Map<Double, Int> = mapOf(
        0.25 to 67, 0.3 to 75, 0.35 to 85, 0.4 to 95, 0.45 to 100, 0.5 to 106, 0.6 to 125, 0.7 to 140, 0.75 to 140, 0.8 to 150,
        1.0 to 180, 1.25 to 212, 1.5 to 236, 1.75 to 265, 2.0 to 280, 2.5 to 335, 3.0 to 375, 3.5 to 425, 4.0 to 475, 4.5 to 500,
        5.0 to 530, 5.5 to 560, 6.0 to 600,
    )

    /** 암나사 안지름 TD1(6) — 피치별. P0.25 · 0.3 은 ISO 965-2 가 5H 만 적어 없다(식) */
    val TD1_6: Map<Double, Int> = mapOf(
        0.35 to 100, 0.4 to 112, 0.45 to 125, 0.5 to 140, 0.6 to 160, 0.7 to 180, 0.75 to 190, 0.8 to 200, 1.0 to 236, 1.25 to 265,
        1.5 to 300, 1.75 to 335, 2.0 to 375, 2.5 to 450, 3.0 to 500, 3.5 to 560, 4.0 to 600, 4.5 to 670, 5.0 to 710, 5.5 to 750,
        6.0 to 800,
    )

    /** 숫나사 피치경 Td2(6) — (지름 구간 번호, 피치). 구간 4 의 P0.75(M8×0.75)는 자료가 없어 식 */
    val TD2_6: Map<Pair<Int, Double>, Int> = mapOf(
        (1 to 0.25) to 53, (1 to 0.3) to 56,
        (2 to 0.35) to 63, (2 to 0.4) to 67, (2 to 0.45) to 71,
        (3 to 0.5) to 75, (3 to 0.6) to 85, (3 to 0.7) to 90, (3 to 0.8) to 95,
        (4 to 1.0) to 112, (4 to 1.25) to 118, (4 to 1.5) to 132,
        (5 to 1.25) to 132, (5 to 1.5) to 140, (5 to 1.75) to 150, (5 to 2.0) to 160, (5 to 2.5) to 170,
        (6 to 2.0) to 170, (6 to 3.0) to 200, (6 to 3.5) to 212, (6 to 4.0) to 224, (6 to 4.5) to 236,
        (7 to 3.0) to 212, (7 to 4.0) to 236, (7 to 5.0) to 250, (7 to 5.5) to 265, (7 to 6.0) to 280,
    )

    /** 암나사 피치경 TD2(6) — (지름 구간 번호, 피치). 구간 1(M1~M1.4, ISO 965-2 는 5H)과 구간 4 의 P0.75 는 식 */
    val TD2_6_INTERNAL: Map<Pair<Int, Double>, Int> = mapOf(
        (2 to 0.35) to 85, (2 to 0.4) to 90, (2 to 0.45) to 95,
        (3 to 0.5) to 100, (3 to 0.6) to 112, (3 to 0.7) to 118, (3 to 0.8) to 125,
        (4 to 1.0) to 150, (4 to 1.25) to 160, (4 to 1.5) to 180,
        (5 to 1.25) to 180, (5 to 1.5) to 190, (5 to 1.75) to 200, (5 to 2.0) to 212, (5 to 2.5) to 224,
        (6 to 2.0) to 224, (6 to 3.0) to 265, (6 to 3.5) to 280, (6 to 4.0) to 300, (6 to 4.5) to 315,
        (7 to 3.0) to 280, (7 to 4.0) to 315, (7 to 5.0) to 335, (7 to 5.5) to 355, (7 to 6.0) to 375,
    )

    /* 식(RoyMech 의 ISO 965 식) — 표에 없는 칸 · 등급 4 · 8 */
    fun fundamental(p: Double): Double = 15 + 11 * p
    fun td6(p: Double): Double = 180 * p.pow(2.0 / 3) - 3.15 / sqrt(p)
    fun td2Six(p: Double, dMean: Double): Double = 90 * p.pow(0.4) * dMean.pow(0.1)
    fun tD1Six(p: Double): Double = if (p < 1.0) 433 * p - 190 * p.pow(1.22) else 230 * p.pow(0.7) // 0.8 과 1 사이엔 규격 피치가 없다
}

/** 한계 치수 계산(식 · 표는 파일 머리 주석과 [Iso965]) */
object ThreadTol {
    /** ISO 965-1 의 호칭 지름 구간(mm). Td2 의 d 는 든 구간 경계의 기하평균. 이 앱은 M1~M64 라 90 까지만 */
    private val ISO_RANGES = listOf(0.99, 1.4, 2.8, 5.6, 11.2, 22.4, 45.0, 90.0)

    fun limits(spec: ThreadSpec, cls: ThreadClass): ThreadLimits {
        require(cls.kind == spec.kind) { "규격과 급수의 나사 종류가 다르다: ${spec.label} ${cls.label}" }
        return if (spec.kind == ThreadKind.METRIC) metric(spec, cls) else unified(spec, cls)
    }

    private fun basics(spec: ThreadSpec) = Triple(
        sqrt(3.0) / 2 * spec.pitch,
        spec.d - 3 * sqrt(3.0) / 8 * spec.pitch, // 2·(3/8)H = 0.649519P
        spec.d - 5 * sqrt(3.0) / 8 * spec.pitch, // 2·(5/8)H = 1.082532P
    )

    private fun metric(spec: ThreadSpec, cls: ThreadClass): ThreadLimits {
        val p = spec.pitch
        val (h, d2, d1) = basics(spec)
        val i = ISO_RANGES.indices.firstOrNull { it > 0 && spec.d > ISO_RANGES[it - 1] && spec.d <= ISO_RANGES[it] }
        require(i != null) { "ISO 965-1 지름 구간 밖: ${spec.d}" }
        val dMean = sqrt(ISO_RANGES[i - 1] * ISO_RANGES[i])
        // 표 값(μm) — 없으면 null 이라 식을 쓴다
        val gTable = Iso965.FUNDAMENTAL_G[p]
        val shift = (gTable?.toDouble() ?: Iso965.fundamental(p)) / 1000 // g · G 의 기초 편차 크기
        val shifted = cls == ThreadClass.M6g || cls == ThreadClass.M8g || cls == ThreadClass.M6G
        val devByFormula = shifted && gTable == null
        return when (cls) {
            ThreadClass.M4h, ThreadClass.M6g, ThreadClass.M6h, ThreadClass.M8g -> {
                val es = if (shifted) -shift else 0.0
                val tdTable = Iso965.TD_6[p]
                val td2Table = Iso965.TD2_6[i to p]
                // 등급 6 은 표(없으면 식), 등급 4 · 8 은 식의 배수(표 자료가 없다)
                val (td, td2, byFormula) = when (cls) {
                    ThreadClass.M4h -> Triple(0.63 * Iso965.td6(p), 0.63 * Iso965.td2Six(p, dMean), true)
                    ThreadClass.M8g -> Triple(1.6 * Iso965.td6(p), 1.6 * Iso965.td2Six(p, dMean), true)
                    else -> Triple(
                        tdTable?.toDouble() ?: Iso965.td6(p),
                        td2Table?.toDouble() ?: Iso965.td2Six(p, dMean),
                        tdTable == null || td2Table == null,
                    )
                }
                ThreadLimits(
                    spec, cls, h, d2, d1, es,
                    pitchDia = Span(d2 + es - td2 / 1000, d2 + es),
                    crest = Span(spec.d + es - td / 1000, spec.d + es),
                    rootLimit = d1 + es,
                    byFormula = byFormula || devByFormula,
                )
            }
            ThreadClass.M6H, ThreadClass.M6G -> {
                val ei = if (shifted) shift else 0.0
                val tD1Table = Iso965.TD1_6[p]
                val tD2Table = Iso965.TD2_6_INTERNAL[i to p]
                val tD1 = (tD1Table?.toDouble() ?: Iso965.tD1Six(p)) / 1000
                val tD2 = (tD2Table?.toDouble() ?: 1.32 * Iso965.td2Six(p, dMean)) / 1000
                ThreadLimits(
                    spec, cls, h, d2, d1, ei,
                    pitchDia = Span(d2 + ei, d2 + ei + tD2),
                    crest = Span(d1 + ei, d1 + ei + tD1),
                    rootLimit = spec.d + ei,
                    byFormula = tD1Table == null || tD2Table == null || devByFormula,
                )
            }
            else -> throw IllegalArgumentException("미터 급수가 아니다: $cls")
        }
    }

    private fun unified(spec: ThreadSpec, cls: ThreadClass): ThreadLimits {
        // 식은 인치로(H28 그대로) 풀고 끝에 mm 로
        val dIn = spec.d / MM_PER_INCH
        val p = spec.pitch / MM_PER_INCH
        val (h, d2, d1) = basics(spec)
        val base = (0.0015 * cbrt(dIn) + 0.0015 * sqrt(dIn) + 0.015 * cbrt(p * p)) * MM_PER_INCH // Le = D
        return when (cls) {
            ThreadClass.UN1A, ThreadClass.UN2A, ThreadClass.UN3A -> {
                val es = if (cls == ThreadClass.UN3A) 0.0 else -0.3 * base
                val c = when (cls) {
                    ThreadClass.UN1A -> 1.5
                    ThreadClass.UN2A -> 1.0
                    else -> 0.75
                }
                val td = (if (cls == ThreadClass.UN1A) 0.090 else 0.060) * cbrt(p * p) * MM_PER_INCH
                ThreadLimits(
                    spec, cls, h, d2, d1, es,
                    pitchDia = Span(d2 + es - c * base, d2 + es),
                    crest = Span(spec.d + es - td, spec.d + es),
                    rootLimit = d1 + es,
                )
            }
            ThreadClass.UN1B, ThreadClass.UN2B, ThreadClass.UN3B -> {
                val c = when (cls) {
                    ThreadClass.UN1B -> 1.95
                    ThreadClass.UN2B -> 1.30
                    else -> 0.975
                }
                ThreadLimits(
                    spec, cls, h, d2, d1, 0.0,
                    pitchDia = Span(d2, d2 + c * base),
                    crest = Span(d1, d1 + unMinorTol(cls, dIn, p) * MM_PER_INCH),
                    rootLimit = spec.d,
                )
            }
            else -> throw IllegalArgumentException("UN 급수가 아니다: $cls")
        }
    }

    /** 암나사 안지름 공차(인치) — H28 Part I 표 2.20 그대로 */
    internal fun unMinorTol(cls: ThreadClass, dIn: Double, p: Double): Double {
        val tpi = 1 / p
        val small = 0.05 * cbrt(p * p) + 0.03 * p / dIn - 0.002
        val coarse = 0.25 * p - 0.4 * p * p
        return if (cls == ThreadClass.UN3B) {
            // 3B(모든 크기): 위 식, 0.394p 를 넘지 않고, 80~13 산은 0.23p − 1.5p², 12 산 이하는 0.120p 보다 작지 않게
            val floor = if (tpi >= 13 - 1e-9) 0.23 * p - 1.5 * p * p else 0.120 * p
            small.coerceAtLeast(floor).coerceAtMost(0.394 * p)
        } else when {
            // 1B · 2B, 0.25 인치 미만: 위 식, 0.394p 를 넘지 않고 0.25p − 0.4p² 보다 작지 않게
            dIn < 0.25 -> small.coerceAtLeast(coarse).coerceAtMost(0.394 * p)
            // 0.25 인치 이상: 0.25p − 0.4p² (4 산보다 거친 나사는 0.15p — 이 앱 목록엔 없음)
            tpi < 4 - 1e-9 -> 0.15 * p
            else -> coarse
        }
    }
}
