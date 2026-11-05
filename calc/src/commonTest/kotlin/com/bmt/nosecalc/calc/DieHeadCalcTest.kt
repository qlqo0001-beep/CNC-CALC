package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 다이스 헤드 글(DieHeadCalc). 기대값은 손 계산(식과 수를 주석에). 치수 · 절삭 속도 · 회전수는 새로 지은 값이다(처음 값 0.96 · 1초 · Z150 ·
 * Z20 은 사용자 결정 2026-11-05).
 */
class DieHeadCalcTest {
    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
    private fun m(size: String, pitch: Double) = ThreadTable.METRIC.single { it.size == size && it.pitch == pitch }

    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    private fun ok(r: DieResult, warnings: List<DieWarning> = emptyList()): DieSolution {
        assertEquals(warnings, r.warnings)
        return assertNotNull(r.solution)
    }

    private fun stops(w: DieWarning, i: DieInput) {
        val r = DieHeadCalc.calc(i)
        assertNull(r.solution, "$w: $i")
        assertEquals(listOf(w), r.warnings, "$i")
    }

    /** 지은 치수: 1/2-20 UNF · Vc 12 · Z4 → −22, 공정 값은 처음 값 */
    private val base = DieInput(spec = un("1/2", 20), vc = 12.0, zStart = 4.0, zEnd = -22.0)

    /**
     * 1/2-20 UNF(d 12.7 · P 1.27), Vc 12 m/min: n = 1000 × 12 / (π × 12.7) = 300.765 → S301, 쓴 Vc = π × 12.7 × 301 / 1000 = 12.009366.
     * F = 0.96 × 1.27 = 1.2192 → F1.219. 길이 4 − (−22) = 26, 분당 이송 1.2192 × 301 = 366.9792,
     * 시간 = 60 × 26 / 366.9792 + 1 = 4.250922 + 1 = 5.250922 초, 당김 (1 − 0.96) × 26 = 1.04. 비율 ≠ 1 → 카탈로그 안내.
     */
    @Test
    fun unfHandNumbers() {
        assertEquals(ThreadSeries.UNF, base.spec!!.series)
        val s = ok(DieHeadCalc.calc(base))
        assertEquals(301L, s.rpm)
        near(12.009366236877701, s.vcUsed, 1e-9, "쓴 Vc")
        near(1.2192, s.feed, 1e-12, "이송")
        near(366.9792, s.feedPerMin, 1e-9, "분당 이송")
        near(5.2509221231067045, s.timeSec, 1e-9, "시간")
        near(1.04, s.floatPull, 1e-12, "당김")
        assertNull(s.fullThreadEndZ)
        assertEquals(listOf("G97S301", "G0X0.Z20.", "Z4.", "G1Z-22.F1.219", "G4U1.", "G0Z150."), s.program)
        assertEquals(DieHeadCalc.CATALOG_FEED_NOTE, s.note)
        assertEquals("카탈로그(Wagner · Landis)는 이송을 피치와 같게(리드와 맞게) 쓴다", s.note)
    }

    /**
     * M10×1.5, 회전수 직접 320, 비율 0.98 · 멈춤 0.5 · 물러남 Z120 · 진입 Z15 · Z3 → −17 · 목 2.5산 · 플로트 1:
     * 쓴 Vc = π × 10 × 320 / 1000 = 10.053096, F = 0.98 × 1.5 = 1.47, 분당 470.4, 시간 = 60 × 20 / 470.4 + 0.5 = 2.551020 + 0.5 = 3.051020,
     * 당김 0.02 × 20 = 0.4 ≤ 1(경고 없음), 완전 나사 끝 = −17 + 2.5 × 1.5 = −13.25.
     */
    @Test
    fun metricDirectRpmAndOptions() {
        val i = DieInput(
            spec = m("M10", 1.5), rpm = 320.0, zStart = 3.0, zEnd = -17.0, ratio = 0.98, dwell = 0.5, retractZ = 120.0, approachZ = 15.0,
            floatTravel = 1.0, throatThreads = 2.5,
        )
        val s = ok(DieHeadCalc.calc(i))
        assertEquals(320L, s.rpm)
        near(10.053096491487338, s.vcUsed, 1e-9, "쓴 Vc")
        near(1.47, s.feed, 1e-12, "이송")
        near(470.4, s.feedPerMin, 1e-9, "분당 이송")
        near(3.0510204081632653, s.timeSec, 1e-9, "시간")
        near(0.4, s.floatPull, 1e-12, "당김")
        near(-13.25, s.fullThreadEndZ!!, 1e-12, "완전 나사 끝")
        assertEquals(listOf("G97S320", "G0X0.Z15.", "Z3.", "G1Z-17.F1.47", "G4U.5", "G0Z120."), s.program)
        // 회전수와 Vc 를 둘 다 넣으면 회전수(Vc 99 는 n = 99000 / (π × 10) = 3151 이 될 값)
        assertEquals(320L, ok(DieHeadCalc.calc(i.copy(vc = 99.0))).rpm)
        // 회전수 직접도 반올림: 320.4 → 320, 320.5 → 321
        assertEquals(320L, ok(DieHeadCalc.calc(i.copy(rpm = 320.4))).rpm)
        assertEquals(321L, ok(DieHeadCalc.calc(i.copy(rpm = 320.5))).rpm)
    }

    /**
     * 비율 1(카탈로그대로): 3/4-16 UNF(P 1.5875), 250 rpm, Z5 → −25: F1.588(0.001 로 — 5 는 올림), 당김 0, 안내 없음,
     * 시간 = 60 × 30 / (1.5875 × 250) + 1 = 4.535433 + 1 = 5.535433. 1/4-28 UNF: 0.96 × 25.4/28 = 0.870857 → F.871(앞 0 뺌)
     */
    @Test
    fun ratioOneAndFeedDigits() {
        val s = ok(DieHeadCalc.calc(DieInput(spec = un("3/4", 16), rpm = 250.0, zStart = 5.0, zEnd = -25.0, ratio = 1.0)))
        assertEquals(ThreadSeries.UNF, un("3/4", 16).series)
        near(1.5875, s.feed, 1e-12, "이송")
        assertEquals(0.0, s.floatPull)
        assertNull(s.note)
        near(5.535433070866142, s.timeSec, 1e-9, "시간")
        assertEquals("G1Z-25.F1.588", s.program[3])
        val f28 = ok(DieHeadCalc.calc(DieInput(spec = un("1/4", 28), rpm = 700.0, zStart = 3.0, zEnd = -9.0)))
        assertEquals("G1Z-9.F.871", f28.program[3])
    }

    /** 멈춤 0 이면 G4 줄 없음, 시간은 깎는 시간만(60 × 26 / 366.9792 = 4.250922). 0.0004 초도 찍으면 0 이라 없음 */
    @Test
    fun zeroDwellDropsG4() {
        val s = ok(DieHeadCalc.calc(base.copy(dwell = 0.0)))
        assertEquals(listOf("G97S301", "G0X0.Z20.", "Z4.", "G1Z-22.F1.219", "G0Z150."), s.program)
        near(4.2509221231067045, s.timeSec, 1e-9, "시간")
        assertEquals(5, ok(DieHeadCalc.calc(base.copy(dwell = 0.0004))).program.size)
        assertEquals("G4U.001", ok(DieHeadCalc.calc(base.copy(dwell = 0.001))).program[4])
    }

    /** 숫자와 함께 내는 경고: 비율 > 1 · 당김 > 플로트 · 완전 나사 없음 — 각각 경계(같으면 경고 없음) */
    @Test
    fun warningsWithNumbers() {
        // 비율 1.02: 당김 = (1 − 1.02) × 26 = −0.52(밀림), 안내도 함께
        val over = ok(DieHeadCalc.calc(base.copy(ratio = 1.02)), listOf(DieWarning.RATIO_OVER_ONE))
        near(-0.52, over.floatPull, 1e-12, "밀림")
        assertEquals(DieHeadCalc.CATALOG_FEED_NOTE, over.note)
        assertEquals("G1Z-22.F1.295", over.program[3]) // 1.02 × 1.27 = 1.2954
        // 플로트 0.5 < 당김 1.04 → 경고, 플로트 = 1.04 → 없음
        ok(DieHeadCalc.calc(base.copy(floatTravel = 0.5)), listOf(DieWarning.FLOAT_EXCEEDED))
        ok(DieHeadCalc.calc(base.copy(floatTravel = 1.04)))
        // 비율 > 1 이면 밀림이라 플로트 경고는 없다
        ok(DieHeadCalc.calc(base.copy(ratio = 1.02, floatTravel = 0.1)), listOf(DieWarning.RATIO_OVER_ONE))
        // 목 3산 × 1.27 = 3.81: Z2 → −1.5 면 완전 나사 끝 −1.5 + 3.81 = 2.31 ≥ 2 → 경고, Z2 → −1.81 이면 끝 = 2.0(같음) → 경고, −1.82 면 1.99 → 없음
        val short = base.copy(zStart = 2.0, zEnd = -1.5, throatThreads = 3.0)
        near(2.31, ok(DieHeadCalc.calc(short), listOf(DieWarning.SHORT_THREAD)).fullThreadEndZ!!, 1e-12, "완전 나사 끝")
        ok(DieHeadCalc.calc(short.copy(zEnd = -1.81)), listOf(DieWarning.SHORT_THREAD))
        ok(DieHeadCalc.calc(short.copy(zEnd = -1.82)))
        // 둘씩 함께(비율 > 1 이면 밀림이라 플로트 경고와는 함께 나지 않는다)
        ok(
            DieHeadCalc.calc(short.copy(ratio = 1.1, floatTravel = 0.0)),
            listOf(DieWarning.RATIO_OVER_ONE, DieWarning.SHORT_THREAD),
        )
        ok(DieHeadCalc.calc(short.copy(floatTravel = 0.0)), listOf(DieWarning.FLOAT_EXCEEDED, DieWarning.SHORT_THREAD))
    }

    /**
     * 끝면 Z(체이서가 물리기 시작하는 곳)를 넣으면 당김 · 완전 나사 검사를 거기부터: base(Z4 → −22)에 끝면 0 →
     * 당김 = (1 − 0.96) × (0 − (−22)) = 0.88(시작 Z 부터면 1.04), 시간 · 글은 그대로(G1 은 시작 Z 부터).
     * 플로트 0.9 ≥ 0.88 → 경고 없음(시작 Z 부터면 1.04 > 0.9 로 경고), 0.87 → 경고.
     * 목 3산 × 1.27 = 3.81: 끝 −3.8 이면 완전 나사 끝 = 0.01 ≥ 끝면 0 → 경고(시작 Z 4 부터면 없음), 끝 −3.82 면 −0.01 → 없음.
     */
    @Test
    fun faceZ() {
        val face = base.copy(zFace = 0.0)
        val s = ok(DieHeadCalc.calc(face))
        near(0.88, s.floatPull, 1e-12, "당김")
        near(5.2509221231067045, s.timeSec, 1e-9, "시간")
        assertEquals(listOf("G97S301", "G0X0.Z20.", "Z4.", "G1Z-22.F1.219", "G4U1.", "G0Z150."), s.program)
        ok(DieHeadCalc.calc(face.copy(floatTravel = 0.9)))
        ok(DieHeadCalc.calc(base.copy(floatTravel = 0.9)), listOf(DieWarning.FLOAT_EXCEEDED))
        ok(DieHeadCalc.calc(face.copy(floatTravel = 0.87)), listOf(DieWarning.FLOAT_EXCEEDED))
        val short = face.copy(zEnd = -3.8, throatThreads = 3.0)
        near(0.01, ok(DieHeadCalc.calc(short), listOf(DieWarning.SHORT_THREAD)).fullThreadEndZ!!, 1e-12, "완전 나사 끝")
        ok(DieHeadCalc.calc(short.copy(zFace = null)))
        ok(DieHeadCalc.calc(short.copy(zEnd = -3.82)))
        // 끝면 = 시작 Z 는 된다(시작 Z 부터와 같은 값), 시작 Z 보다 밖이면 시작점으로 급송할 때 닿고, 끝 Z 이하면 깎을 나사가 없다
        near(1.04, ok(DieHeadCalc.calc(base.copy(zFace = 4.0))).floatPull, 1e-12, "끝면 = 시작 Z")
        stops(DieWarning.Z_ORDER, base.copy(zFace = 4.5))
        stops(DieWarning.Z_ORDER, base.copy(zFace = -22.0))
        ok(DieHeadCalc.calc(base.copy(zFace = -21.9)))
        stops(DieWarning.BAD_INPUT, base.copy(zFace = 20_000.0))
        stops(DieWarning.BAD_INPUT, base.copy(zFace = Double.NaN))
    }

    @Test
    fun stopsOnMissingInput() {
        stops(DieWarning.MISSING_INPUT, base.copy(spec = null))
        stops(DieWarning.MISSING_INPUT, base.copy(zStart = null))
        stops(DieWarning.MISSING_INPUT, base.copy(zEnd = null))
        stops(DieWarning.MISSING_INPUT, base.copy(vc = null))
        // 회전수만 있어도 된다
        assertEquals(400L, ok(DieHeadCalc.calc(base.copy(vc = null, rpm = 400.0))).rpm)
    }

    @Test
    fun stopsOnBadInput() {
        for (i in listOf(
            base.copy(ratio = 0.0), base.copy(ratio = -0.5), base.copy(ratio = Double.NaN),
            base.copy(dwell = -1.0), base.copy(dwell = Double.POSITIVE_INFINITY), base.copy(dwell = 20_000.0),
            base.copy(vc = 0.0), base.copy(vc = -12.0), base.copy(vc = Cutting.MAX_SPEED * 2),
            base.copy(rpm = 0.9), base.copy(rpm = 200_000.0),
            base.copy(floatTravel = -0.1), base.copy(throatThreads = -1.0),
            base.copy(zEnd = -20_000.0), base.copy(retractZ = Double.NaN), base.copy(approachZ = 20_000.0),
            // Vc 0.001 → n 0.025 → S0, 비율 0.0003 → F 0.000381 → F0.
            base.copy(vc = 0.001), base.copy(ratio = 0.0003),
            // Vc 10 000 m/min 을 1/2 에 → n = 10 000 000 / (π × 12.7) = 250 638 > 100 000
            base.copy(vc = 10_000.0),
        )) stops(DieWarning.BAD_INPUT, i)
    }

    @Test
    fun stopsOnZOrder() {
        stops(DieWarning.Z_ORDER, base.copy(zStart = -22.0))
        stops(DieWarning.Z_ORDER, base.copy(zStart = -30.0))
        stops(DieWarning.Z_ORDER, base.copy(approachZ = 3.0)) // 진입 Z3 < 시작 Z4
        stops(DieWarning.Z_ORDER, base.copy(retractZ = 3.9))
        // 진입 · 물러남이 시작 Z 와 같으면 된다
        val same = ok(DieHeadCalc.calc(base.copy(approachZ = 4.0, retractZ = 4.0)))
        assertEquals(listOf("G97S301", "G0X0.Z4.", "Z4.", "G1Z-22.F1.219", "G4U1.", "G0Z4."), same.program)
    }

    @Test
    fun defaults() {
        assertEquals(0.96, DieHeadCalc.RATIO_DEFAULT)
        assertEquals(1.0, DieHeadCalc.DWELL_DEFAULT)
        assertEquals(150.0, DieHeadCalc.RETRACT_Z_DEFAULT)
        assertEquals(20.0, DieHeadCalc.APPROACH_Z_DEFAULT)
        val i = DieInput()
        assertEquals(DieHeadCalc.RATIO_DEFAULT, i.ratio)
        assertEquals(DieHeadCalc.DWELL_DEFAULT, i.dwell)
        assertEquals(DieHeadCalc.RETRACT_Z_DEFAULT, i.retractZ)
        assertEquals(DieHeadCalc.APPROACH_Z_DEFAULT, i.approachZ)
    }
}
