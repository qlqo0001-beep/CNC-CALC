package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CuttingTest {
    private fun near(e: Double, a: Double?, what: String, tol: Double = 1e-9) {
        assertNotNull(a, "$what 이 비었다")
        assertTrue(abs(e - a) <= tol * maxOf(1.0, abs(e)), "$what: 기대 $e, 실제 $a")
    }

    /** 손 계산(따로 파이썬으로): Ø50 · Vc150 → n 954.9297, f0.2 → vf 190.9859, L120 → 0.62832 분, r0.8 → Rz 6.25 · Ra 1.60375 */
    @Test
    fun handNumbers() {
        val r = Cutting.calc(CutInput(d = 50.0, vc = 150.0, f = 0.2, length = 120.0, noseR = 0.8))
        near(150.0, r.vc, "Vc")
        near(954.929658551372, r.n, "n")
        near(0.2, r.f, "f")
        near(190.9859317102744, r.vf, "vf")
        near(0.6283185307179586, r.timeMin, "분")
        near(37.69911184307752, r.timeSec, "초")
        near(6.25, r.rz, "Rz")
        near(1.6037507477489608, r.ra, "Ra")
        assertNull(r.g96)
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.bad.isEmpty())
    }

    /** n 을 넣으면 Vc 를 구한다(Ø40 · 1200 rpm → 150.796) */
    @Test
    fun rpmToVc() {
        val r = Cutting.calc(CutInput(d = 40.0, speedFrom = SpeedFrom.N, n = 1200.0, vc = 999.0))
        near(150.79644737231007, r.vc, "Vc")
        near(1200.0, r.n, "n")
        for (d in listOf(0.5, 3.0, 12.7, 250.0)) for (vc in listOf(10.0, 95.5, 400.0)) {
            near(vc, Cutting.vc(Cutting.rpm(vc, d), d), "왕복 Vc→n→Vc Ø$d")
        }
    }

    /** 드릴 fz · 날 수: Ø10 · Vc100 · fz0.05 · 날 수 빈칸(2) → f 0.1 · vf 318.31. 3날이면 0.15 */
    @Test
    fun drillFeedPerTooth() {
        val r = Cutting.calc(CutInput(d = 10.0, vc = 100.0, feedFrom = FeedFrom.FZ, fz = 0.05, f = 9.0))
        near(3183.098861837907, r.n, "n")
        near(0.1, r.f, "f")
        assertEquals(2, r.z)
        near(318.3098861837907, r.vf, "vf")
        val r3 = Cutting.calc(CutInput(d = 10.0, vc = 100.0, feedFrom = FeedFrom.FZ, fz = 0.05, z = 3))
        near(0.15, r3.f, "f 3날")
        assertEquals(3, r3.z)
    }

    /** 빈칸 줄은 결과도 비어 있다 — 0 으로 계산하지 않는다 */
    @Test
    fun blanksStayBlank() {
        val empty = Cutting.calc(CutInput())
        assertNull(empty.vc); assertNull(empty.n); assertNull(empty.f); assertNull(empty.vf); assertNull(empty.timeMin)
        assertNull(empty.rz); assertNull(empty.ra); assertNull(empty.g96); assertNull(empty.z)
        assertTrue(empty.warnings.isEmpty())
        // 지름 없이 Vc 만: n 이 없어 vf · 시간도 없다. 거칠기는 f · r 만으로
        val r = Cutting.calc(CutInput(vc = 120.0, f = 0.1, noseR = 0.4, length = 50.0))
        near(120.0, r.vc, "Vc")
        assertNull(r.n); assertNull(r.vf); assertNull(r.timeMin)
        near(0.1 * 0.1 / 3.2 * 1000, r.rz, "Rz")
    }

    /**
     * Ra 식 = 포물선 단면 z = x²/(2r) 의 Ra 정의(평균선에서의 |벗어남| 평균)를 직접 적분한 값.
     * 같은 단면의 최대 높이는 f²/(8r) 이고, 실제 원호 단면과는 f ≪ r 에서 같아진다.
     */
    @Test
    fun raMatchesDefinitionOnTheProfile() {
        fun profile(g: (Double) -> Double, h: Double, steps: Int = 200_000): Pair<Double, Double> {
            val ys = DoubleArray(steps) { g(-h + 2 * h * (it + 0.5) / steps) }
            val mean = ys.average()
            return ys.sumOf { abs(it - mean) } / steps * 1000 to (ys.max() - ys.min()) * 1000
        }
        for ((f, r) in listOf(0.05 to 0.2, 0.1 to 0.4, 0.2 to 0.8, 0.3 to 0.4, 0.25 to 1.2, 0.5 to 1.6)) {
            val (raP, rzP) = profile({ x -> x * x / (2 * r) }, f / 2)
            near(raP, Cutting.ra(f, r), "포물선 Ra f$f r$r", 1e-6)
            near(rzP, Cutting.rz(f, r), "포물선 Rz f$f r$r", 1e-4)
            // 실제 원호 단면: f ≤ r/2 이면 Ra · Rz 모두 2% 안
            if (f <= r / 2) {
                val (raC, rzC) = profile({ x -> r - sqrt(r * r - x * x) }, f / 2)
                assertTrue(abs(raC - Cutting.ra(f, r)) <= 0.02 * raC, "원호 Ra f$f r$r: $raC vs ${Cutting.ra(f, r)}")
                assertTrue(abs(rzC - Cutting.rz(f, r)) <= 0.02 * rzC, "원호 Rz f$f r$r: $rzC vs ${Cutting.rz(f, r)}")
            }
        }
        // 흔한 f²/(32r) 보다 2.6% 크다
        near(32 / (18 * sqrt(3.0)), Cutting.ra(0.2, 0.8) / (0.2 * 0.2 / (32 * 0.8) * 1000), "Ra 비")
    }

    @Test
    fun feedTooBigForNose() {
        val r = Cutting.calc(CutInput(f = 0.8, noseR = 0.4))
        assertNull(r.rz); assertNull(r.ra)
        assertEquals(listOf(CutWarning.FEED_TOO_BIG_FOR_NOSE), r.warnings)
        assertNotNull(Cutting.calc(CutInput(f = 0.79, noseR = 0.4)).rz)
    }

    /** G96 Vc200 · G50 S2000: Ø31.831 아래로 막힘. Ø20 → 3183 이 2000 으로, 주속 125.66 · Ø40 → 1591.5 그대로 */
    @Test
    fun g96Clamp() {
        val small = Cutting.calc(CutInput(d = 20.0, vc = 200.0, smax = 2000.0))
        val g = assertNotNull(small.g96)
        near(31.83098861837907, g.dLimit, "막히는 지름")
        near(3183.098861837907, g.nCommand, "G96 회전수")
        near(2000.0, g.n, "실제 회전수")
        assertTrue(g.clamped)
        near(125.66370614359172, g.vcActual, "실제 주속")
        assertEquals(listOf(CutWarning.RPM_CLAMPED), small.warnings)
        // n 칸은 G96 이 내려는 값(막히기 전) 그대로 — 막힌 값은 g96 에만
        near(3183.098861837907, small.n, "n")

        val big = assertNotNull(Cutting.calc(CutInput(d = 40.0, vc = 200.0, smax = 2000.0)).g96)
        assertFalse(big.clamped)
        near(1591.5494309189535, big.n, "Ø40 회전수")
        near(200.0, big.vcActual, "Ø40 주속")
        // 지름 없이: 막히는 지름만
        val noD = assertNotNull(Cutting.calc(CutInput(vc = 200.0, smax = 2000.0)).g96)
        near(31.83098861837907, noD.dLimit, "막히는 지름")
        assertNull(noD.n); assertNull(noD.vcActual); assertFalse(noD.clamped)
        // 막히는 지름 그 자리: 막히지 않음(같음)
        val edge = Cutting.g96(200.0, 2000.0, 1000 * 200.0 / (PI * 2000.0))
        assertFalse(edge.clamped)
    }

    @Test
    fun badInput() {
        for ((input, field) in listOf(
            CutInput(d = 0.0, vc = 100.0) to CutField.D,
            CutInput(d = -5.0, vc = 100.0) to CutField.D,
            CutInput(d = MAX_MM + 1, vc = 100.0) to CutField.D,
            CutInput(d = 10.0, vc = Double.NaN) to CutField.VC,
            CutInput(d = 10.0, speedFrom = SpeedFrom.N, n = Double.POSITIVE_INFINITY) to CutField.N,
            CutInput(d = 10.0, vc = Cutting.MAX_SPEED * 2) to CutField.VC,
            CutInput(f = -0.1) to CutField.F,
            CutInput(feedFrom = FeedFrom.FZ, fz = 0.0) to CutField.FZ,
            CutInput(feedFrom = FeedFrom.FZ, fz = 0.05, z = 0) to CutField.Z,
            CutInput(feedFrom = FeedFrom.FZ, fz = 0.05, z = Cutting.MAX_TEETH + 1) to CutField.Z,
            CutInput(length = 0.0) to CutField.LENGTH,
            CutInput(noseR = 0.0, f = 0.1) to CutField.NOSE_R,
            CutInput(vc = 100.0, smax = 0.0) to CutField.SMAX,
        )) {
            val r = Cutting.calc(input)
            assertEquals(setOf(field), r.bad, "$input")
            assertEquals(CutWarning.BAD_INPUT, r.warnings.first(), "$input")
        }
        // 잘못된 칸에 기대는 결과는 비고, 다른 줄은 계산된다
        val r = Cutting.calc(CutInput(d = -1.0, vc = 100.0, f = 0.2, noseR = 0.8))
        assertNull(r.n); assertNull(r.vf)
        near(100.0, r.vc, "Vc")
        assertNotNull(r.rz)
        // 쓰지 않는 쪽 칸은 검사하지 않는다(speedFrom = VC 인데 n 이 이상)
        assertTrue(Cutting.calc(CutInput(d = 10.0, vc = 100.0, n = -1.0)).bad.isEmpty())
    }
}
