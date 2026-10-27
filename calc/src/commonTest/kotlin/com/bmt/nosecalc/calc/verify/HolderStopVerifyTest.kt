package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * 독립 검증 ④ — 보링바 안전 끝점 · 잘린 NC. 무작위 내경 TYPE 2~6 200 · 3점호 정방향 150 · 뒤보링 150 × 무작위 바, 안전 여유 0 과 0.5 에서
 * [StopChecker](경로 · NC 읽기 · 제어기 보정 모형을 따로 짠 검사기)로 0.01 mm 마다:
 * 끝점까지 안전 · 끝점 여유 = 기준(또는 면을 넘는 순간) · 0.001 mm 더 가면 모자람 · 첫 닿음보다 앞 · 잘린 NC 두 가지를 다시 읽어 굴린 자세가
 * 안전 여유 − 1e-4 아래로 안 내려감 · 후퇴가 겹치지 않음 · 앞 줄이 원래 NC 그대로 · G40 은 첫 후퇴 줄
 */
class HolderStopVerifyTest {
    private val rs = listOf(0.0, 0.2, 0.4, 0.8)

    @Test
    fun randomTypes() {
        val rep = Report("안전 끝점 · 내경 TYPE")
        val started = TimeSource.Monotonic.markNow()
        val rnd = Random(4711)
        val g = GeometricVerifyTest()
        repeat(200) {
            val r = rs[rnd.nextInt(rs.size)]
            val inp = g.randomRound(rnd.nextInt(2, 7), rnd, inner = true).input.copy(noseR = r)
            run(rep, "$inp", RoundCalc.calc(inp), RoundCalc.machine(inp), true, randomBar(rnd, r), r)
        }
        println("${rep.name}: ${started.elapsedNow()} · 제어기 모형으로 못 본 기계 보정 ${StopChecker.skippedMachine}")
        rep.finish()
    }

    @Test
    fun randomArcs() {
        val rep = Report("안전 끝점 · 3점호 내경")
        val started = TimeSource.Monotonic.markNow()
        val rnd = Random(911)
        for (reverse in listOf(false, true)) repeat(150) {
            val r = rs[rnd.nextInt(rs.size)]
            val a = randomArc(rnd, reverse, r)
            run(rep, "$a", RoundCalc.arc3(a), RoundCalc.machine(a), false, randomBar(rnd, r), r)
        }
        println("${rep.name}: ${started.elapsedNow()}")
        rep.finish()
    }

    private val outcomes = mutableMapOf<String, Int>()

    private fun run(rep: Report, what: String, calc: RoundResult, drawnCalc: RoundResult, face: Boolean, bar: BoringBar, r: Double) {
        rep.cases++
        val sol = calc.solution ?: return rep.stop("계산 멈춤 ${calc.warnings}")
        rep.solved++
        for (safe in listOf(0.0, 0.5)) {
            val plan = HolderStop.plan(sol, drawnCalc.solution, r, face, bar, safe)
            if (plan.result.skip != null) {
                rep.stop("${plan.result.skip}")
                continue
            }
            val d = plan.direct
            val key = when {
                plan.end == null -> "끝점 없음 ${plan.warnings}"
                d == null -> "잘린 NC 없음 ${plan.warnings}"
                else -> "${d.retract}/${plan.machine?.retract} ${plan.warnings}"
            }
            outcomes[key] = (outcomes[key] ?: 0) + 1
            val fails = mutableListOf<String>()
            StopChecker(sol, drawnCalc.solution, r, face, bar, safe).check(plan, STEP, fails)
            for (f in fails) rep.bad("$what + $bar · 안전 여유 $safe", "끝점", f)
        }
        if (rep.cases % 40 == 0) println("  ${rep.cases}: $outcomes")
    }

    /** HolderVerifyTest 와 같은 꼴(정방향 · 뒤보링만): 위 반원은 반시계로 가면 Z 가 준다 */
    private fun randomArc(rnd: Random, reverse: Boolean, r: Double): Arc3Input {
        val upper = rnd.nextBoolean()
        val bigR = r3(rnd.nextDouble(0.5, 30.0))
        val (lo0, hi0) = if (upper) 3.0 to 177.0 else 183.0 to 357.0
        var a: Double
        var b: Double
        do {
            a = rnd.nextDouble(lo0, hi0)
            b = rnd.nextDouble(lo0, hi0)
        } while (abs(a - b) < 10)
        val lo = min(a, b)
        val hi = max(a, b)
        val ccw = if (upper) !reverse else reverse
        val th = listOf(if (ccw) lo else hi, rnd.nextDouble(lo + 2, hi - 2), if (ccw) hi else lo).map { it * PI / 180 }
        val minSin = (0..90).minOf { sin((lo + (hi - lo) * it / 90) * PI / 180) }
        val cz = r3(rnd.nextDouble(-20.0, 5.0))
        val cx = r3(rnd.nextDouble(0.5 - bigR * minSin, 30.5 - bigR * minSin))
        val p = th.map { r3(cz + bigR * cos(it)) to r3(2 * (cx + bigR * sin(it))) }
        return Arc3Input(
            inner = true, noseR = r, x1 = p[0].second, z1 = p[0].first, x2 = p[1].second, z2 = p[1].first, x3 = p[2].second, z3 = p[2].first,
            zClear = r3(rnd.nextDouble(0.0, 3.0)),
        )
    }

    /** HolderVerifyTest 와 같은 무작위 바: D 6~32, f fMin + 0.05 ~ D/2 + 6, 인서트 넷, κr 90 ~ min(117.5, 179 − ε), 돌출 · DMIN 반은 비움 */
    private fun randomBar(rnd: Random, r: Double): BoringBar {
        val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
        val dia = r3(rnd.nextDouble(6.0, 32.0))
        val kr = r3(rnd.nextDouble(90.0, min(117.5, 179 - shape.epsDeg)))
        val f = r3(rnd.nextDouble(HolderCheck.fMin(dia, kr, r) + 0.05, dia / 2 + 6))
        var b = BoringBar(dia, f, shape, r3(rnd.nextDouble(HolderCheck.edgeMin(shape, r) + 0.3, 16.0)), kr)
        if (rnd.nextBoolean()) b = b.copy(overhang = r3(HolderCheck.overhangMin(b, r) + rnd.nextDouble(0.5, 60.0)))
        if (rnd.nextBoolean()) b = b.copy(dMin = r3(rnd.nextDouble(dia, 2 * f + 4)))
        return b
    }

    private fun r3(v: Double) = (v * 1000).roundToLong() / 1000.0

    private companion object {
        const val STEP = 0.01
    }
}
