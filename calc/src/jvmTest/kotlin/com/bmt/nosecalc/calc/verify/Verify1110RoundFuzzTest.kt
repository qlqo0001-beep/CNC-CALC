package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderSkip
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-10 피드백 1 · 2): 계산기는 화면이 그리는 중에 부른다 — 예외 하나가 곧 앱이 꺼지는 것이다(`guarded` 는 멈춤 경고만 받는다).
 * 형상 1~6 × 외·내경(관통) × 가공 방향 × 치수(보통 · 모서리 R · 아주 작은 것 · 비운 칸) × 노즈R(빈칸 = NaN · 0 · 아주 작은 값 · 큰 값 · 음수 · ∞) ×
 * Z 여유 × 끝 직선 × 팁으로 직접 · 기계 보정 계산과(내경) 보링바 검사를 불러 예외 0 인지, 숫자가 아닌 좌표가 NC 에 없는지 본다
 */
class Verify1110RoundFuzzTest {
    private val noses = listOf(Double.NaN, 0.0, 1e-9, 0.001, 0.05, 0.4, 0.8, 2.0, 5.0, 30.0, 400.0, 1e12, -0.4, Double.POSITIVE_INFINITY)
    private val zClears = listOf(0.0, 0.001, 1.0, 1e7)
    private val legs = listOf(1e-9, 0.049, 0.4, 5.0, 1e5)
    private val bar = BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0, null, null)

    private fun dims(type: Int): List<RoundInput> {
        val full = when (type) {
            1 -> RoundInput(1, mainR = 20.0, l1 = 20.0, cornerR1 = 0.0)
            2 -> RoundInput(2, mainR = 10.0, d1 = 12.0, l1 = 10.0, l2 = 16.0, cornerR1 = 0.0)
            3 -> RoundInput(3, mainR = 10.0, d1 = 16.0, l1 = 4.0, cornerR1 = 0.0)
            4 -> RoundInput(4, mainR = 10.0, d1 = 12.0, l1 = 18.0, cornerR1 = 0.0)
            // 사용자가 말한 상태: 주 R · D2 · D1 · L1 을 넣고 L2 는 자동
            5 -> RoundInput(5, mainR = 30.0, d2 = 50.0, d1 = 30.0, l1 = 10.0, l2 = null, cornerR1 = 0.0, cornerR2 = 0.0)
            else -> RoundInput(6, mainR = 10.0, d2 = 30.0, d1 = 12.0, l1 = 5.0, l2 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0)
        }
        fun scale(i: RoundInput, k: Double) = i.copy(mainR = i.mainR?.times(k), d1 = i.d1?.times(k), d2 = i.d2?.times(k), l1 = i.l1?.times(k), l2 = i.l2?.times(k))
        val out = mutableListOf(full, full.copy(cornerR1 = 1.0, cornerR2 = if (type >= 5) 0.5 else null), scale(full, 0.02), scale(full, 0.2).copy(cornerR1 = 0.1))
        // 비울 수 있는 칸을 하나씩 비움 / 두 개 비움(값 부족)
        when (type) {
            2 -> out += listOf(full.copy(mainR = null), full.copy(d1 = null), full.copy(l2 = null), full.copy(mainR = null, d1 = null))
            3, 4 -> out += listOf(full.copy(mainR = null), full.copy(d1 = null), full.copy(l1 = null), full.copy(mainR = null, l1 = null))
            5 -> out += listOf(full.copy(mainR = null, l2 = 9.398), full.copy(d2 = null, l2 = 9.398), full.copy(d1 = null, l2 = 9.398), full.copy(l2 = 9.398), full.copy(l2 = 3.0))
        }
        return out
    }

    private fun bad(r: RoundResult): String? {
        val s = r.solution ?: return null
        val pts = s.nodes.flatMap { listOf(it.drawn, it.prog) } + s.entry + s.approach
        if (pts.any { !it.z.isFinite() || !it.x.isFinite() }) return "숫자가 아닌 좌표"
        if (s.nc.any { it.contains("NaN") || it.contains("Infinity") || it.contains("E") }) return "NC 에 숫자가 아닌 글: ${s.nc}"
        if (s.nc.isEmpty()) return "NC 가 비었다"
        return null
    }

    @Test
    fun typeInputsNeverThrow() {
        val fails = mutableListOf<String>()
        var calls = 0
        var solved = 0
        var flippedSkips = 0
        var k = 0
        for (inner in listOf(false, true)) for (type in (if (inner) 2..6 else 1..6)) for (through in (if (inner && type >= 5) listOf(false, true) else listOf(false))) {
            for (flip in listOf(false, true)) for (d in dims(type)) for (r in noses) for (zc in zClears) for (leg in legs) {
                // 팁은 돌려 가며(표준 · 0~9)
                val tip = if (k % 3 == 0) null else (k / 3) % 10
                k++
                val i = d.copy(noseR = r, zClear = zc, legLen = leg, inner = inner, through = through, flip = flip, tip = tip)
                try {
                    calls++
                    val c = RoundCalc.calc(i)
                    val m = RoundCalc.machine(i)
                    bad(c)?.let { fails += "직접 $it — $i" }
                    bad(m)?.let { fails += "기계 $it — $i" }
                    if ((c.solution == null) != (m.solution == null)) fails += "직접 · 기계 한쪽만 풀림 — $i"
                    val sol = c.solution
                    if (sol != null) {
                        solved++
                        if (sol.flip != flip) fails += "풀이의 flip 표시가 입력과 다름 — $i"
                        if (inner) {
                            for (s in listOf(sol, m.solution!!)) {
                                val plan = HolderStop.plan(s, m.solution, r, true, bar)
                                if (flip) {
                                    if (plan.result.skip != HolderSkip.FLIPPED) fails += "뒤집은 내경인데 보링바 검사가 FLIPPED 가 아님(${plan.result.skip}) — $i" else flippedSkips++
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    fails += "예외 ${e::class.simpleName}: ${e.message} @ ${e.stackTrace.take(4).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" }} — $i"
                }
            }
        }
        val text = "TYPE 훑기: 부른 수 $calls · 풀린 수 $solved · 뒤집은 내경 보링바 건너뜀 $flippedSkips · 실패 ${fails.size}\n" + fails.take(40).joinToString("\n")
        File("build/verify1110").mkdirs()
        File("build/verify1110/fuzz-type.txt").writeText(text)
        println(text)
        assertTrue(calls > 40_000 && solved > 3_000, text)
        assertEquals(0, fails.size, text)
    }

    @Test
    fun arc3InputsNeverThrow() {
        val fails = mutableListOf<String>()
        var calls = 0
        var solved = 0
        // 볼록 · 오목 · 정 · 역방향 · 축에 닿는 것 · 거의 한 줄 · 겹친 점 · 빈칸
        val pts = listOf(
            listOf(30.0, 0.0, 44.142, -2.929, 50.0, -10.0),
            listOf(50.0, -10.0, 44.142, -2.929, 30.0, 0.0),
            listOf(30.0, 0.0, 22.929, -1.464, 20.0, -5.0),
            listOf(0.0, 0.0, 14.142, -2.929, 20.0, -10.0),
            listOf(20.0, -10.0, 14.142, -2.929, 0.0, 0.0),
            listOf(30.0, 0.0, 30.001, -5.0, 30.0, -10.0),
            listOf(30.0, 0.0, 30.0, -5.0, 30.0, -10.0),
            listOf(30.0, 0.0, 30.0, 0.0, 50.0, -10.0),
            listOf(1.0, 0.0, 0.2, -0.1, 0.0, -0.3),
            listOf(0.4, 0.0, 1.2, -0.2, 2.0, -0.2),
        )
        for (p in pts) for (inner in listOf(false, true)) for (flip in listOf(false, true)) for (r in noses) for (zc in zClears) for (tip in listOf(null, 0, 1, 2, 3, 4, 8)) {
            for (blank in listOf(-1, 0, 5)) {
                val v = p.mapIndexed { n, x -> if (n == blank) null else x }
                val i = Arc3Input(inner, r, tip, v[0], v[1], v[2], v[3], v[4], v[5], zc, flip)
                try {
                    calls++
                    val c = RoundCalc.arc3(i)
                    val m = RoundCalc.machine(i)
                    bad(c)?.let { fails += "직접 $it — $i" }
                    bad(m)?.let { fails += "기계 $it — $i" }
                    if ((c.solution == null) != (m.solution == null)) fails += "직접 · 기계 한쪽만 풀림 — $i"
                    c.solution?.let { sol ->
                        solved++
                        if (inner) for (s in listOf(sol, m.solution!!)) {
                            val plan = HolderStop.plan(s, m.solution, r, false, bar)
                            if (flip && plan.result.skip != HolderSkip.FLIPPED) fails += "뒤집은 내경 3점호인데 보링바 검사가 FLIPPED 가 아님(${plan.result.skip}) — $i"
                        }
                    }
                } catch (e: Throwable) {
                    fails += "예외 ${e::class.simpleName}: ${e.message} @ ${e.stackTrace.take(4).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" }} — $i"
                }
            }
        }
        val text = "3점호 훑기: 부른 수 $calls · 풀린 수 $solved · 실패 ${fails.size}\n" + fails.take(40).joinToString("\n")
        File("build/verify1110").mkdirs()
        File("build/verify1110/fuzz-arc3.txt").writeText(text)
        println(text)
        assertTrue(calls > 20_000 && solved > 1_000, text)
        assertEquals(0, fails.size, text)
    }
}
