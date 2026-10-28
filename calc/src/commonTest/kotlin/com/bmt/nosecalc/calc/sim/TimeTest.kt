package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.sim.SimKit.moves
import com.bmt.nosecalc.calc.sim.SimKit.near
import com.bmt.nosecalc.calc.sim.SimKit.ok
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 사이클 타임(`docs/simulator-design.md` 4절) — 손 계산과 따로 짠 수치 적분 */
class TimeTest {
    /** 4.2 손 계산: (X200, Z100) → (X50, Z2), X 20 · Z 24 m/min → tX 0.225 · tZ 0.245 초, 꺾이는 점 (X50, Z10) */
    @Test
    fun rapid() {
        val r = ok(listOf("G00 X50. Z2.", "M30"))
        val mv = moves(r)
        assertEquals(2, mv.size)
        assertEquals(0.245, r.totalSec, 1e-12)
        near(Pt.ofDia(10.0, 50.0), mv[0].tip.end)
        assertEquals(0.225, mv[0].t1 - mv[0].t0, 1e-12)
        assertEquals(0.020, mv[1].t1 - mv[1].t0, 1e-12)
        // 직선 설정: 한 토막, 시간 같음
        val lin = ok(listOf("G00 X50. Z2.", "M30"), machine = MachineSetup(rapidPath = RapidPath.LINEAR))
        assertEquals(1, moves(lin).size)
        assertEquals(0.245, lin.totalSec, 1e-12)
        // 한 축만
        assertEquals(98.0 / 24000 * 60, ok(listOf("G00 Z2.", "M30")).totalSec, 1e-12)
    }

    /** 4.3 손 계산: G96 단면 X50 → X0, Vc 200, F0.2, G50 S2000 → 1.752 + 2.387 = 4.139 초 */
    @Test
    fun cssFace() {
        val r = ok(listOf("G50 S2000", "G96 S200 M03", "G00 X50. Z0.", "G01 X0. F0.2", "M30"))
        val cut = moves(r).last()
        val sec = cut.t1 - cut.t0
        val xc = 200_000 / (2 * PI * 2000)
        val expect = (PI / 200_000) * (625 - xc * xc) / 0.2 * 60 + xc / (0.2 * 2000) * 60
        assertEquals(expect, sec, 1e-12)
        assertEquals("4.139", "%.3f".fmt(sec))
        assertEquals(2000.0, cut.rpm1)
    }

    /** 자르지 않는 경우 X50 → X20(G50 S4000 — x_c 7.96 < 10): 2.474 초 */
    @Test
    fun cssNoClamp() {
        val r = ok(listOf("G50 S4000", "G96 S200 M03", "G00 X50. Z0.", "G01 X20. F0.2", "M30"))
        val cut = moves(r).last()
        assertEquals((PI / 200_000) * (625 - 100) / 0.2 * 60, cut.t1 - cut.t0, 1e-12)
        assertEquals("2.474", "%.3f".fmt(cut.t1 - cut.t0))
    }

    /** G04 X1.5 · P1500 · `X1500`(표준 ×0.001) 모두 1.5 초(S 3-72) */
    @Test
    fun dwell() {
        for (d in listOf("G04 X1.5", "G04 P1500", "G04 X1500", "G04 U1.5")) {
            val r = ok(listOf(d, "M30"))
            assertEquals(1.5, r.totalSec, 1e-12, d)
            assertEquals(1.5, r.steps.filterIsInstance<DwellStep>().single().sec, 1e-12)
        }
    }

    /** 공구 번호가 바뀔 때만 교환 시간(처음 T 는 0 — 그 전 공구를 모른다) */
    @Test
    fun tool() {
        val r = ok(listOf("T0101", "T0202", "T0202", "T0203", "M30"))
        assertEquals(1.0, r.totalSec, 1e-12)
        assertEquals(listOf(0.0, 1.0, 0.0, 0.0), r.steps.filterIsInstance<ToolStep>().map { it.t1 - it.t0 })
        assertEquals(2.5, ok(listOf("T0101", "T0202", "M30"), machine = MachineSetup(toolChangeSec = 2.5)).totalSec, 1e-12)
    }

    /** G98 = L/F 분, G99 + G97 = L/(F·n) */
    @Test
    fun feedModes() {
        val a = ok(listOf("G98", "G00 X40. Z0.", "G01 Z-30. F150", "M30"))
        assertEquals(30.0 / 150 * 60, moves(a).last().let { it.t1 - it.t0 }, 1e-12)
        val b = ok(listOf("G97 S800 M03", "G00 X40. Z0.", "G01 Z-30. F0.25", "M30"))
        assertEquals(30.0 / (0.25 * 800) * 60, moves(b).last().let { it.t1 - it.t0 }, 1e-12)
        // 원호 길이 = R × 각
        val c = ok(listOf("G97 S800 M03", "G00 X40. Z0.", "G02 X60. Z-10. R10. F0.25", "M30"))
        assertEquals(10 * PI / 2 / (0.25 * 800) * 60, moves(c).last().let { it.t1 - it.t0 }, 1e-12)
    }

    /**
     * S 3-82 그림 3-19 예제(체계 A 로 G92 S → G50 S): 이송 블록마다 시간 = 따로 짠 수치 적분(토막 1e-4 mm, 가운데 점)과 1e-6 상대.
     */
    @Test
    fun siemens382() {
        val r = ok(listOf("G50 S1500", "G96 S150 M03", "G00 X40. Z5.", "G01 Z0 F0.15", "X80. Z-30.", "W-10.", "G2 X120. W-20. R20.", "G01 U10.", "G97 S500", "M30"),
            machine = MachineSetup(decimal = DecimalMode.STANDARD))
        val feeds = moves(r).filter { it.kind == MoveKind.FEED }
        assertEquals(5, feeds.size)
        for (mv in feeds) {
            val want = brute(mv.tip, 0.15, 150.0, 1500.0)
            assertEquals(want, mv.t1 - mv.t0, want * 1e-6, "${mv.line}")
        }
        // N8 G01 Z0: X40 에서 회전수 = 150000/(π·40) = 1193.7 (1500 아래)
        assertEquals(150_000 / (PI * 40), feeds[0].rpm0, 1e-9)
        // 급송(G00 X40. Z5.)은 끝점 X 로 회전수(S 3-82)
        val rapid = moves(r).first { it.line == 3 }
        assertEquals(150_000 / (PI * 40), rapid.rpm1, 1e-9)
    }

    /** 무작위 직선 · 원호(축 넘음 · 자름 경계 포함)에서 닫힌 식 = 수치 적분 */
    @Test
    fun randomAgainstNumeric() {
        val rnd = Random(7)
        repeat(60) { k ->
            val vc = rnd.nextDouble(50.0, 300.0)
            val nMax = rnd.nextDouble(500.0, 5000.0)
            val f = rnd.nextDouble(0.05, 0.4)
            val a = Pt(rnd.nextDouble(-40.0, 0.0), rnd.nextDouble(-15.0, 40.0))
            val b = Pt(rnd.nextDouble(-40.0, 0.0), rnd.nextDouble(-15.0, 40.0))
            val seg: Seg = if (k % 2 == 0) Line(a, b) else {
                val c = Pt(rnd.nextDouble(-30.0, -10.0), rnd.nextDouble(-5.0, 10.0))
                val rr = rnd.nextDouble(3.0, 25.0)
                val a0 = rnd.nextDouble(0.0, 2 * PI)
                val a1 = a0 + rnd.nextDouble(0.3, 5.5) * (if (rnd.nextBoolean()) 1 else -1)
                Arc(c + Pt(cos(a0), sin(a0)) * rr, c + Pt(cos(a1), sin(a1)) * rr, c, a1 < a0)
            }
            val got = GTime.feedSec(seg, f, true, vc, null, nMax)
            val want = brute(seg, f, vc, nMax)
            assertTrue(abs(got - want) <= want * 1e-6, "$k $seg: $got vs $want")
        }
    }

    /** 따로 짠 적분: 토막 ≤ 1e-4 mm 가운데 점 */
    private fun brute(s: Seg, f: Double, vc: Double, nMax: Double): Double {
        val len = GTime.length(s)
        val n = max(1, (len / 1e-4).toInt())
        var sum = 0.0
        for (i in 0 until n) {
            val p = point(s, (i + 0.5) / n)
            val rpm = if (abs(p.x) < 1e-12) nMax else min(1000 * vc / (2 * PI * abs(p.x)), nMax)
            sum += len / n / rpm
        }
        return sum / f * 60
    }

    private fun point(s: Seg, u: Double): Pt = when (s) {
        is Line -> s.start + (s.end - s.start) * u
        is Arc -> {
            val a = angleOf(s.start - s.center) + (if (s.cw) -1 else 1) * s.sweepRad() * u
            s.center + Pt(cos(a), sin(a)) * s.radius
        }
    }

    private fun String.fmt(v: Double): String {
        val m = kotlin.math.round(v * 1000).toLong()
        return "${m / 1000}.${(m % 1000).toString().padStart(3, '0')}"
    }
}
