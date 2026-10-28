package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 나사 규격 자료 · 공차역. 미터 공차는 ISO 965-1 표 값(없는 칸만 식), UN 은 ASME B1.1 의 식. 공개된 한계 치수 표와 맞춰 본다.
 */
class ThreadSpecTest {
    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a (차 ${a - e})")

    private fun m(d: Double, p: Double) = ThreadTable.METRIC.single { it.d == d && it.pitch == p }
    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }

    /** 기본 형상: H = 0.866025P, d2 = d − 0.649519P, d1 = d − 1.082532P (M10×1.5 → 9.026 · 8.376, ISO 724 표) */
    @Test
    fun basicProfile() {
        val l = ThreadTol.limits(m(10.0, 1.5), ThreadClass.M6H)
        near(1.299038, l.h, 1e-6, "H")
        near(9.025721, l.basicPitch, 1e-6, "d2")
        near(8.376202, l.basicMinor, 1e-6, "d1")
        near(0.649519 * 1.5, 10 - l.basicPitch, 1e-6, "0.649519P")
        near(1.082532 * 1.5, 10 - l.basicMinor, 1e-6, "1.082532P")
    }

    /**
     * 미터 6g · 6H — amesweb.info "Metric Thread Chart"(ISO 965-2 · ASME B1.13M 의 한계 치수 표) 와 맞춰 본다.
     * 열: 지름, 피치, 6g 바깥지름 최대 · 최소, 피치경 최대 · 최소 / 6H 안지름 최소 · 최대, 피치경 최소 · 최대.
     * 공차가 표 값이라 표의 0.001 반올림 안(±0.0006)으로 맞는다. (식으로만 낼 때는 피치경 9 μm · 바깥지름 최소 24 μm(M36 Td 식 452 ↔
     * 표 475) · 안지름 최대 14 μm 까지 달랐다)
     */
    @Test
    fun metricMatchesPublishedTable() {
        val ext = """
            1.6 0.35 1.581 1.496 1.354 1.291
            2 0.4 1.981 1.886 1.721 1.654
            2.5 0.45 2.480 2.380 2.188 2.117
            3 0.5 2.980 2.874 2.655 2.580
            3.5 0.6 3.479 3.354 3.089 3.004
            4 0.7 3.978 3.838 3.523 3.433
            5 0.8 4.976 4.826 4.456 4.361
            6 1 5.974 5.794 5.324 5.212
            8 1.25 7.972 7.760 7.160 7.042
            8 1 7.974 7.794 7.324 7.212
            10 1.5 9.968 9.732 8.994 8.862
            10 1.25 9.972 9.760 9.160 9.042
            12 1.75 11.966 11.701 10.829 10.679
            12 1.5 11.968 11.732 10.994 10.854
            12 1.25 11.972 11.760 11.160 11.028
            14 2 13.962 13.682 12.663 12.503
            14 1.5 13.968 13.732 12.994 12.854
            16 2 15.962 15.682 14.663 14.503
            16 1.5 15.968 15.732 14.994 14.854
            18 1.5 17.968 17.732 16.994 16.854
            20 2.5 19.958 19.623 18.334 18.164
            20 1.5 19.968 19.732 18.994 18.854
            22 2.5 21.958 21.623 20.334 20.164
            22 1.5 21.968 21.732 20.994 20.854
            24 3 23.952 23.577 22.003 21.803
            24 2 23.962 23.682 22.663 22.493
            27 3 26.952 26.577 25.003 24.803
            27 2 26.962 26.682 25.663 25.493
            30 3.5 29.947 29.522 27.674 27.462
            30 2 29.962 29.682 28.663 28.493
            33 2 32.962 32.682 31.663 31.493
            36 4 35.940 35.465 33.342 33.118
            42 4.5 41.937 41.437 39.014 38.778
            48 5 47.929 47.399 44.681 44.431
            56 5.5 55.925 55.365 52.353 52.088
        """.trimIndent()
        val inn = """
            1.6 0.35 1.221 1.321 1.373 1.458
            2 0.4 1.567 1.679 1.740 1.830
            2.5 0.45 2.013 2.138 2.208 2.303
            3 0.5 2.459 2.599 2.675 2.775
            3.5 0.6 2.850 3.010 3.110 3.222
            4 0.7 3.242 3.422 3.545 3.663
            5 0.8 4.134 4.334 4.480 4.605
            6 1 4.917 5.153 5.350 5.500
            8 1.25 6.647 6.912 7.188 7.348
            8 1 6.917 7.153 7.350 7.500
            10 1.5 8.376 8.676 9.026 9.206
            10 1.25 8.647 8.912 9.188 9.348
            12 1.75 10.106 10.441 10.863 11.063
            12 1.5 10.376 10.676 11.026 11.216
            12 1.25 10.647 10.912 11.188 11.368
            14 2 11.835 12.210 12.701 12.913
            14 1.5 12.376 12.676 13.026 13.216
            16 2 13.835 14.210 14.701 14.913
            16 1.5 14.376 14.676 15.026 15.216
            18 1.5 16.376 16.676 17.026 17.216
            20 2.5 17.294 17.744 18.376 18.600
            20 1.5 18.376 18.676 19.026 19.216
            22 2.5 19.294 19.744 20.376 20.600
            22 1.5 20.376 20.676 21.026 21.216
            24 3 20.752 21.252 22.051 22.316
            24 2 21.835 22.210 22.701 22.925
            27 3 23.752 24.252 25.051 25.316
            27 2 24.835 25.210 25.701 25.925
            30 3.5 26.211 26.771 27.727 28.007
            30 2 27.835 28.210 28.701 28.925
            33 2 30.835 31.210 31.701 31.925
            36 4 31.670 32.270 33.402 33.702
            42 4.5 37.129 37.799 39.077 39.392
            48 5 42.587 43.297 44.752 45.087
            56 5.5 50.046 50.796 52.428 52.783
            64 6 57.505 58.305 60.103 60.478
        """.trimIndent()
        for (row in ext.lines()) {
            val v = row.trim().split(" ").map { it.toDouble() }
            val l = ThreadTol.limits(m(v[0], v[1]), ThreadClass.M6g)
            val what = "M${v[0]}×${v[1]} 6g"
            near(v[2], l.crest.max, 0.0006, "$what 바깥지름 최대")
            near(v[3], l.crest.min, 0.0006, "$what 바깥지름 최소")
            near(v[4], l.pitchDia.max, 0.0006, "$what 피치경 최대")
            near(v[5], l.pitchDia.min, 0.0006, "$what 피치경 최소")
            assertTrue(!l.byFormula, "$what 표 값")
        }
        for (row in inn.lines()) {
            val v = row.trim().split(" ").map { it.toDouble() }
            val l = ThreadTol.limits(m(v[0], v[1]), ThreadClass.M6H)
            val what = "M${v[0]}×${v[1]} 6H"
            near(v[2], l.crest.min, 0.0006, "$what 안지름 최소")
            near(v[3], l.crest.max, 0.0006, "$what 안지름 최대")
            near(v[4], l.pitchDia.min, 0.0006, "$what 피치경 최소")
            near(v[5], l.pitchDia.max, 0.0006, "$what 피치경 최대")
            near(v[0], l.rootLimit, 1e-12, "$what 바깥지름 최소")
            assertTrue(!l.byFormula, "$what 표 값")
        }
    }

    /**
     * 표가 없는 칸의 식 — RoyMech 의 반올림 안 한 계산 표(같은 식)와 1 μm 안: M10×1.5 Td2(6) 130 · TD2(6) 172 · Td(6) 233 · TD1(6) 305 μm,
     * M3×0.5 Td2 78 · Td 109 · TD1 135, M48×5 Td2 260 · Td 525 · TD1 710. (이 세 사이즈의 실제 한계는 표 값을 쓴다)
     */
    @Test
    fun metricFormulaMatchesRoyMechCalculatedValues() {
        fun check(dMean: Double, p: Double, td2: Double, tD2: Double, td: Double, tD1: Double) {
            near(td2, Iso965.td2Six(p, dMean), 1.0, "P$p Td2")
            near(tD2, 1.32 * Iso965.td2Six(p, dMean), 1.0, "P$p TD2")
            near(td, Iso965.td6(p), 1.0, "P$p Td")
            near(tD1, Iso965.tD1Six(p), 1.0, "P$p TD1")
        }
        check(sqrt(5.6 * 11.2), 1.5, 130.0, 172.0, 233.0, 305.0)
        check(sqrt(2.8 * 5.6), 0.5, 78.0, 103.0, 109.0, 135.0)
        check(sqrt(45.0 * 90.0), 5.0, 260.0, 343.0, 525.0, 710.0)
    }

    /**
     * 급수 사이 관계: 6g · 6h 는 같은 공차(Td · Td2), 6G · 6H 도 같은 공차(TD1 · TD2). g 의 es = −(G 의 EI) = ISO 965-1 표 1 값
     * (P1.5 32 · P2 38 · P4 60 · P5 71 · P6 80 μm — 식 15 + 11P 와 다른 칸), h · H = 0.
     * 4h · 8g 는 표 자료가 없어 식의 배수(4 = 0.63 ×, 8 = 1.6 × 식 Td · Td2(6) — FED-STD-H28/21B 예 3 의 Td2(4) = 0.63 × 0.09 P^0.4 d^0.1).
     * 표가 없어 식을 쓴 곳만 byFormula: 4h · 8g, M1~M1.4 의 6H · 6G, M8×0.75 의 6g · 6h · 6H · 6G.
     */
    @Test
    fun metricClassRelations() {
        val fundamental = mapOf(1.5 to 32.0, 2.0 to 38.0, 4.0 to 60.0, 5.0 to 71.0, 6.0 to 80.0, 0.6 to 21.0, 0.75 to 22.0)
        val ranges = listOf(0.99, 1.4, 2.8, 5.6, 11.2, 22.4, 45.0, 90.0).zipWithNext()
        for (s in ThreadTable.METRIC) {
            val g6 = ThreadTol.limits(s, ThreadClass.M6g)
            val h6 = ThreadTol.limits(s, ThreadClass.M6h)
            val h4 = ThreadTol.limits(s, ThreadClass.M4h)
            val g8 = ThreadTol.limits(s, ThreadClass.M8g)
            val bigH = ThreadTol.limits(s, ThreadClass.M6H)
            val bigG = ThreadTol.limits(s, ThreadClass.M6G)
            near(0.0, h6.deviation, 0.0, "${s.label} h es")
            near(0.0, bigH.deviation, 0.0, "${s.label} H EI")
            near(-g6.deviation, bigG.deviation, 1e-12, "${s.label} G EI = −g es")
            near(g6.deviation, g8.deviation, 1e-12, "${s.label} 8g es")
            fundamental[s.pitch]?.let { near(-it / 1000, g6.deviation, 1e-12, "${s.label} g es (표 1)") }
            near(g6.pitchDia.width, h6.pitchDia.width, 1e-12, "${s.label} 6h Td2")
            near(g6.crest.width, h6.crest.width, 1e-12, "${s.label} 6h Td")
            near(bigH.pitchDia.width, bigG.pitchDia.width, 1e-12, "${s.label} 6G TD2")
            near(bigH.crest.width, bigG.crest.width, 1e-12, "${s.label} 6G TD1")
            // ISO 965-1 지름 구간(초과~이하)의 양 끝 기하평균
            val dMean = ranges.first { (lo, hi) -> s.d > lo && s.d <= hi }.let { (lo, hi) -> sqrt(lo * hi) }
            near(0.63 * Iso965.td2Six(s.pitch, dMean) / 1000, h4.pitchDia.width, 1e-12, "${s.label} 4h Td2")
            near(0.63 * Iso965.td6(s.pitch) / 1000, h4.crest.width, 1e-12, "${s.label} 4h Td")
            near(1.6 * Iso965.td2Six(s.pitch, dMean) / 1000, g8.pitchDia.width, 1e-12, "${s.label} 8g Td2")
            near(1.6 * Iso965.td6(s.pitch) / 1000, g8.crest.width, 1e-12, "${s.label} 8g Td")
            assertTrue(h4.byFormula && g8.byFormula, "${s.label} 4h · 8g 는 식")
            val small = s.d <= 1.4
            val m8f = s.d == 8.0 && s.pitch == 0.75
            assertEquals(m8f, g6.byFormula, "${s.label} 6g byFormula")
            assertEquals(m8f, h6.byFormula, "${s.label} 6h byFormula")
            assertEquals(small || m8f, bigH.byFormula, "${s.label} 6H byFormula")
            assertEquals(small || m8f, bigG.byFormula, "${s.label} 6G byFormula")
        }
        // UN 은 늘 식(ASME B1.1 이 식으로 정함) — 표 대체가 아니라 byFormula 는 false
        assertTrue(ThreadTable.UN.none { s -> ThreadClass.entries.filter { it.kind == ThreadKind.UN }.any { ThreadTol.limits(s, it).byFormula } })
    }

    /**
     * UN 2A · 2B — amesweb.info "UNC Thread Chart"(ASME B1.1 표), 3A · 3B — saolamfg(NBS H28 표 2.21 인용).
     * 표는 0.0001 인치(안지름은 2B 가 0.001 인치)로 반올림해 있다.
     * 열: 인치 지름, 산수, 2A 바깥 최대 · 최소, 피치경 최대 · 최소 / 2B 피치경 최대 · 최소, 안지름 최대 · 최소.
     */
    @Test
    fun unifiedMatchesPublishedTable() {
        val names = mapOf(0.073 to "#1", 0.112 to "#4", 0.138 to "#6", 0.164 to "#8", 0.190 to "#10", 0.25 to "1/4",
            0.3125 to "5/16", 0.375 to "3/8", 0.5 to "1/2", 0.75 to "3/4", 1.0 to "1", 1.5 to "1-1/2")
        val ext = """
            0.073 64 0.0724 0.0686 0.0623 0.0603
            0.112 40 0.1112 0.1061 0.0950 0.0925
            0.138 32 0.1372 0.1312 0.1169 0.1141
            0.164 32 0.1631 0.1571 0.1428 0.1399
            0.190 24 0.1890 0.1818 0.1619 0.1586
            0.25 20 0.2489 0.2408 0.2164 0.2127
            0.3125 18 0.3113 0.3026 0.2752 0.2712
            0.375 16 0.3737 0.3643 0.3331 0.3287
            0.5 13 0.4985 0.4876 0.4485 0.4435
            0.75 10 0.7482 0.7353 0.6832 0.6773
            1.0 8 0.9980 0.9830 0.9168 0.9100
            1.5 6 1.4976 1.4794 1.3893 1.3812
        """.trimIndent()
        // 1-1/2-6 2B 피치경 최대는 그 표가 1.4075(공차 0.0158 — 2A 의 1.3 배 0.0105 와 안 맞는 오기)라 뺐다
        val inn = """
            0.073 64 0.0655 0.0629 0.0623 0.0561
            0.112 40 0.0991 0.0958 0.0939 0.0849
            0.138 32 0.1214 0.1177 0.1140 0.1040
            0.164 32 0.1475 0.1437 0.1390 0.1300
            0.190 24 0.1672 0.1629 0.1560 0.1450
            0.25 20 0.2224 0.2175 0.2070 0.1960
            0.3125 18 0.2817 0.2764 0.2650 0.2520
            0.375 16 0.3401 0.3344 0.3210 0.3070
            0.5 13 0.4565 0.4500 0.4340 0.4170
            0.75 10 0.6927 0.6850 0.6630 0.6420
            1.0 8 0.9276 0.9188 0.8900 0.8650
        """.trimIndent()
        val inch = 0.00015 * 25.4 // 0.0001 반올림 + 끝자리
        val minorInch = 0.0006 * 25.4 // 2B 안지름은 0.001 인치로 반올림(최소를 먼저 반올림하고 공차를 더함)
        for (row in ext.lines()) {
            val v = row.trim().split(" ").map { it.toDouble() }
            val s = un(names.getValue(v[0]), v[1].toInt())
            val l = ThreadTol.limits(s, ThreadClass.UN2A)
            val what = "${s.label} 2A"
            near(v[2] * 25.4, l.crest.max, inch, "$what 바깥지름 최대")
            near(v[3] * 25.4, l.crest.min, inch, "$what 바깥지름 최소")
            near(v[4] * 25.4, l.pitchDia.max, inch, "$what 피치경 최대")
            near(v[5] * 25.4, l.pitchDia.min, inch, "$what 피치경 최소")
        }
        for (row in inn.lines()) {
            val v = row.trim().split(" ").map { it.toDouble() }
            val s = un(names.getValue(v[0]), v[1].toInt())
            val l = ThreadTol.limits(s, ThreadClass.UN2B)
            val what = "${s.label} 2B"
            near(v[2] * 25.4, l.pitchDia.max, inch, "$what 피치경 최대")
            near(v[3] * 25.4, l.pitchDia.min, inch, "$what 피치경 최소")
            near(v[4] * 25.4, l.crest.max, minorInch, "$what 안지름 최대")
            near(v[5] * 25.4, l.crest.min, minorInch, "$what 안지름 최소")
            near(s.d, l.rootLimit, 1e-12, "$what 바깥지름 최소")
        }
        // 3A · 3B (saolamfg, H28 표 2.21): 1/4-20 3A 피치경 0.2175/0.2147 · 3B 피치경 최대 0.2211 · 안지름 최대 0.2067,
        // 1/2-13 3A 0.4500/0.4463 · 3B 0.4548 · 0.4284. 3A 는 허용차 0
        for ((s, row) in listOf(un("1/4", 20) to listOf(0.2175, 0.2147, 0.2211, 0.2067), un("1/2", 13) to listOf(0.4500, 0.4463, 0.4548, 0.4284))) {
            val a3 = ThreadTol.limits(s, ThreadClass.UN3A)
            val b3 = ThreadTol.limits(s, ThreadClass.UN3B)
            assertEquals(0.0, a3.deviation, s.label)
            near(row[0] * 25.4, a3.pitchDia.max, inch, "${s.label} 3A 피치경 최대")
            near(row[1] * 25.4, a3.pitchDia.min, inch, "${s.label} 3A 피치경 최소")
            near(row[2] * 25.4, b3.pitchDia.max, inch, "${s.label} 3B 피치경 최대")
            near(row[3] * 25.4, b3.crest.max, inch, "${s.label} 3B 안지름 최대")
        }
        // 허용차: 1/4-20 2A 0.0011 · 1/2-13 2A 0.0015 인치(saolamfg)
        near(-0.0011 * 25.4, ThreadTol.limits(un("1/4", 20), ThreadClass.UN2A).deviation, 0.00006 * 25.4, "1/4-20 허용차")
        near(-0.0015 * 25.4, ThreadTol.limits(un("1/2", 13), ThreadClass.UN2A).deviation, 0.00006 * 25.4, "1/2-13 허용차")
    }

    /**
     * H28 표 2.20 의 상 · 하한이 걸리는 곳: #0-80 1B/2B 는 식(0.00694)이 0.394p(0.00493)를 넘어 0.394p,
     * 1-8 3B 는 식(0.01425)이 12 산 이하 하한 0.120p(0.015)보다 작아 0.015 → 안지름 0.8647 ~ 0.8797(ASME B1.1 1-8 UNC-3B 0.8797),
     * 1/2-13 3B 는 식 그대로(0.011659 > 0.23p − 1.5p²), 1/4 이상 2B 는 0.25p − 0.4p².
     */
    @Test
    fun unifiedMinorToleranceClamps() {
        fun tol(cls: ThreadClass, dIn: Double, tpi: Int) = ThreadTol.unMinorTol(cls, dIn, 1.0 / tpi)
        near(0.394 / 80, tol(ThreadClass.UN2B, 0.060, 80), 1e-12, "#0-80 2B")
        near(0.394 / 80, tol(ThreadClass.UN1B, 0.060, 80), 1e-12, "#0-80 1B")
        near(0.120 / 8, tol(ThreadClass.UN3B, 1.0, 8), 1e-12, "1-8 3B 하한")
        near(0.8797, (ThreadTol.limits(un("1", 8), ThreadClass.UN3B).crest.max) / 25.4, 0.00005, "1-8 3B 안지름 최대")
        val p = 1.0 / 13
        near(0.05 * cbrt(p * p) + 0.03 * p / 0.5 - 0.002, tol(ThreadClass.UN3B, 0.5, 13), 1e-12, "1/2-13 3B")
        near(0.25 * p - 0.4 * p * p, tol(ThreadClass.UN2B, 0.5, 13), 1e-12, "1/2-13 2B")
        // #6-32 2B: 0.25 미만 식(0.009754)이 하한 0.25p − 0.4p²(0.007422)보다 커서 식 그대로 → 안지름 최대 0.1139(표 0.114)
        near(0.1140 * 25.4, ThreadTol.limits(un("#6", 32), ThreadClass.UN2B).crest.max, 0.0002 * 25.4, "#6-32 2B")
    }

    /** UN 급수 배수(H28 6.1 의 C): 1A 1.5 · 3A 0.75 · 1B 1.95 · 2B 1.30 · 3B 0.975, 허용차 0.3(1A · 2A), 바깥지름 1A 0.090 · 2A 3A 0.060 */
    @Test
    fun unifiedClassFactors() {
        for (s in ThreadTable.UN) {
            val l = ThreadClass.entries.filter { it.kind == ThreadKind.UN }.associateWith { ThreadTol.limits(s, it) }
            val base = l.getValue(ThreadClass.UN2A).pitchDia.width
            for ((c, f) in listOf(ThreadClass.UN1A to 1.5, ThreadClass.UN3A to 0.75, ThreadClass.UN1B to 1.95, ThreadClass.UN2B to 1.30, ThreadClass.UN3B to 0.975)) {
                near(f * base, l.getValue(c).pitchDia.width, 1e-12, "${s.label} ${c.label}")
            }
            near(-0.3 * base, l.getValue(ThreadClass.UN1A).deviation, 1e-12, "${s.label} 1A 허용차")
            near(-0.3 * base, l.getValue(ThreadClass.UN2A).deviation, 1e-12, "${s.label} 2A 허용차")
            near(1.5 * l.getValue(ThreadClass.UN2A).crest.width, l.getValue(ThreadClass.UN1A).crest.width, 1e-12, "${s.label} 1A Td")
            near(l.getValue(ThreadClass.UN2A).crest.width, l.getValue(ThreadClass.UN3A).crest.width, 1e-12, "${s.label} 3A Td")
            near(s.d, l.getValue(ThreadClass.UN3A).crest.max, 1e-12, "${s.label} 3A 바깥 최대 = 기본")
        }
    }

    /** 모든 규격 · 급수의 놓임: 숫나사는 기본에서 es 만큼 내려 아래로, 암나사는 EI 만큼 올려 위로 — 폭은 양수 */
    @Test
    fun dispositionForEverySpec() {
        for (s in ThreadTable.METRIC + ThreadTable.UN) {
            for (c in ThreadClass.entries.filter { it.kind == s.kind }) {
                val l = ThreadTol.limits(s, c)
                val what = "${s.label} ${c.label}"
                assertTrue(l.pitchDia.width > 0 && l.crest.width > 0, what)
                if (c.internal) {
                    assertTrue(l.deviation >= 0, what)
                    near(l.basicPitch + l.deviation, l.pitchDia.min, 1e-12, what)
                    near(l.basicMinor + l.deviation, l.crest.min, 1e-12, what)
                    assertTrue(l.crest.max < l.pitchDia.min, "$what 안지름 < 피치경")
                } else {
                    assertTrue(l.deviation <= 0, what)
                    near(l.basicPitch + l.deviation, l.pitchDia.max, 1e-12, what)
                    near(s.d + l.deviation, l.crest.max, 1e-12, what)
                    near(l.basicMinor + l.deviation, l.rootLimit, 1e-12, what)
                    assertTrue(l.crest.min > l.pitchDia.max, "$what 바깥지름 > 피치경")
                }
            }
        }
    }

    /** 목록: ISO 262 보통 34 사이즈 M1~M64 + 가는 나사, UNC 23 · UNF 24(#0 은 UNF 만), 번호 사이즈 지름 0.060 + 0.013N */
    @Test
    fun tableContents() {
        val coarse = ThreadTable.METRIC.filter { it.series == ThreadSeries.M_COARSE }
        assertEquals(34, coarse.size)
        assertEquals("M1", coarse.first().size)
        assertEquals(6.0, coarse.last().pitch)
        assertEquals(listOf(1.5, 1.25, 1.0), ThreadTable.pitches(ThreadKind.METRIC, "M10").map { it.pitch })
        assertEquals("M10×1.25", ThreadTable.pitches(ThreadKind.METRIC, "M10")[1].label)
        assertEquals(23, ThreadTable.UN.count { it.series == ThreadSeries.UNC })
        assertEquals(24, ThreadTable.UN.count { it.series == ThreadSeries.UNF })
        assertEquals(listOf(80), ThreadTable.pitches(ThreadKind.UN, "#0").map { it.tpi })
        assertEquals(listOf(20, 28), ThreadTable.pitches(ThreadKind.UN, "1/4").map { it.tpi })
        assertEquals("1/4-20 UNC", un("1/4", 20).label)
        for (n in listOf(0, 1, 2, 3, 4, 5, 6, 8, 10, 12)) near((0.060 + 0.013 * n) * 25.4, ThreadTable.pitches(ThreadKind.UN, "#$n").first().d, 1e-9, "#$n")
        near(25.4 / 20, un("1/4", 20).pitch, 1e-12, "1/4-20 피치")
        assertEquals(ThreadTable.sizes(ThreadKind.UN).size, 24)
        assertNull(ThreadTable.pitches(ThreadKind.METRIC, "M9").firstOrNull())
        // 급수 고르기 · 기본
        assertEquals(listOf("4h", "6g", "6h", "8g"), ThreadClass.choices(ThreadKind.METRIC, false).map { it.label })
        assertEquals(listOf("6H", "6G"), ThreadClass.choices(ThreadKind.METRIC, true).map { it.label })
        assertEquals(ThreadClass.UN2A, ThreadClass.default(ThreadKind.UN, false))
        assertEquals(ThreadClass.M6H, ThreadClass.default(ThreadKind.METRIC, true))
        assertFailsWith<IllegalArgumentException> { ThreadTol.limits(m(10.0, 1.5), ThreadClass.UN2A) }
    }
}
