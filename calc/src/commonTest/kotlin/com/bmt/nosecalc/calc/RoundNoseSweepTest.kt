package com.bmt.nosecalc.calc

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 노즈R 칸을 고치는 동안 지나가는 값(빈칸 · 0 · "0." · 큰 값 · 음수 …)에서 라운드 계산이 예외를 내지 않는가.
 * 화면은 값이 바뀔 때마다 그리는 중에 [RoundCalc.calc] · [RoundCalc.machine](내경은 [HolderStop.plan] 도)을 부른다 — 예외가 나면 앱이 꺼진다.
 *
 * 2026-11-10 피드백 1: 형상 5 에 주 R · D2 · D1 · L1 을 넣어 L2 가 자동 계산된 상태에서 노즈R 을 고치려 하면 앱이 꺼졌다.
 * 키패드의 첫 키(0 · . · ⌫)가 노즈R 을 0 으로 만들고, Z 여유가 빈칸(0)이라 진입 거리 max(Z 여유, 노즈R) = 0 →
 * 기계 보정 프로그램(`machineFrame`)의 진입점 = 첫 점, 첫 깎는 줄 길이 0 → 방향 NaN → NC 글(`Fmt`)이 IllegalArgumentException.
 * 풀이가 있는(값을 다 넣은) 모든 형상 · 3점호에서 같았다
 */
class RoundNoseSweepTest {
    /** 화면처럼: 칸 글 → 숫자(숫자가 아니면 NaN) */
    private fun nose(t: String) = t.toDoubleOrNull() ?: Double.NaN

    /** 키패드로 0.4 → 0.8 을 치는 동안 칸에 들어가는 글(`NumEdit.live`): 0 → 0. 은 0 → 0.8. ⌫ 로 고치면 0.4 → 0. 은 0 */
    private val typing = listOf("0.4", "0", "0", "0.8", "0", "", "0.4")

    /** 사용자가 말한 상태: 형상 5 에 주 R · D2 · D1 · L1(L2 는 비워 자동 계산), Z 여유 빈칸(0) */
    private val reported = RoundInput(type = 5, mainR = 30.0, cornerR1 = 0.0, cornerR2 = 0.0, d2 = 50.0, d1 = 30.0, l1 = 10.0, zClear = 0.0)

    @Test
    fun reportedStateSurvivesNoseEditing() {
        for (inner in listOf(false, true)) for (t in typing) {
            val i = reported.copy(inner = inner, noseR = nose(t))
            val direct = RoundCalc.calc(i)
            val machine = RoundCalc.machine(i)
            if (nose(t) > 0) {
                // L2 = √(30² − 15²) − √(30² − 25²) = 9.398
                assertEquals(9.398, assertNotNull(direct.solution, "$i").filled!!.l2!!, 1e-3)
                assertNotNull(machine.solution, "$i")
            } else {
                // 노즈R 0(· 숫자가 아님) + Z 여유 0: 진입 거리가 없다 — 테이퍼와 같이 입력 오류로 멈춘다
                assertEquals(listOf(RoundWarning.BAD_INPUT), direct.warnings, "$i")
                assertNull(direct.solution)
                assertEquals(listOf(RoundWarning.BAD_INPUT), machine.warnings, "$i")
                assertNull(machine.solution)
            }
        }
    }

    /** 형상마다 풀리는 치수 하나(비울 수 있는 칸은 비움) */
    private val shapes = listOf(
        RoundInput(type = 1, mainR = 10.0, cornerR1 = 1.0, l1 = 12.0),
        RoundInput(type = 2, mainR = 10.0, cornerR1 = 1.0, d1 = 10.0, l1 = 5.0),
        RoundInput(type = 3, mainR = 12.5, cornerR1 = 1.0, d1 = 20.0),
        RoundInput(type = 4, mainR = 10.0, cornerR1 = 1.0, d1 = 12.0),
        RoundInput(type = 5, mainR = 18.0, cornerR1 = 1.0, cornerR2 = 1.0, d2 = 30.0, d1 = 10.0, l1 = 5.0),
        RoundInput(type = 6, mainR = 12.0, cornerR1 = 1.0, cornerR2 = 1.0, d2 = 40.0, d1 = 8.0, l1 = 5.0, l2 = 8.0),
    )

    private val arcs = listOf(
        Arc3Input(x1 = 30.0, z1 = 0.0, x2 = 44.1421, z2 = -2.9289, x3 = 50.0, z3 = -10.0),
        Arc3Input(inner = true, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0),
        Arc3Input(inner = true, x1 = 50.0, z1 = -10.0, x2 = 35.8579, z2 = -7.0711, x3 = 30.0, z3 = 0.0),
        Arc3Input(x1 = 50.0, z1 = -10.0, x2 = 44.1421, z2 = -2.9289, x3 = 30.0, z3 = 0.0),
    )

    /** 진입 거리 = max(Z 여유, 노즈R). 둘 다 0 이면 진입 이동이 없다 → BAD_INPUT(직접 · 기계 보정 모두). 하나라도 0 보다 크면 푼다 */
    @Test
    fun zeroNoseNeedsZClear() {
        for (base in shapes) for (inner in listOf(false, true)) {
            if (inner && base.type == 1) continue
            val i = base.copy(inner = inner, noseR = 0.0, zClear = 0.0)
            for (r in listOf(RoundCalc.calc(i), RoundCalc.machine(i))) {
                assertNull(r.solution, "$i")
                assertEquals(listOf(RoundWarning.BAD_INPUT), r.warnings, "$i")
            }
            for (ok in listOf(i.copy(zClear = 1.0), i.copy(noseR = 0.4), i.copy(noseR = 0.001))) {
                assertNotNull(RoundCalc.calc(ok).solution, "$ok")
                assertNotNull(RoundCalc.machine(ok).solution, "$ok")
            }
        }
        for (base in arcs) {
            val a = base.copy(noseR = 0.0, zClear = 0.0)
            for (r in listOf(RoundCalc.arc3(a), RoundCalc.machine(a))) {
                assertNull(r.solution, "$a")
                assertEquals(listOf(RoundWarning.BAD_INPUT), r.warnings, "$a")
            }
            for (ok in listOf(a.copy(zClear = 1.0), a.copy(noseR = 0.4))) {
                assertNotNull(RoundCalc.arc3(ok).solution, "$ok")
                assertNotNull(RoundCalc.machine(ok).solution, "$ok")
            }
        }
    }

    /** 노즈R 칸에 칠 수 있는 글 */
    private val noseTexts = listOf(
        "", "0", ".", "0.", "-", "-0", "0.0", "0.00000000001", "0.001", "0.01", "0.05", "0.1", "0.2", "0.39", "0.4", "0.41", "0.8", "1", "1.", "1.2", "1.6", "2.4",
        "3.2", "5", "8", "12.5", "20", "30", "50", "100", "400", "9999", "10000", "10000.001", "999999999999", "-0.4", "-1",
    )

    /** 예외를 꼴(종류 + 앱 코드의 스택 머리)마다 하나씩 모은다 */
    private class Seen {
        val first = LinkedHashMap<String, String>()
        fun run(what: () -> String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                val at = t.stackTraceToString().lineSequence().drop(1).map { it.trim() }.filter { "com.bmt.nosecalc" in it }.take(8).joinToString(" | ")
                first.getOrPut("${t::class.simpleName}: ${(t.message ?: "").take(80)} @ $at") { what() }
            }
        }

        fun check() = assertTrue(first.isEmpty(), "예외 ${first.size} 가지:\n" + first.entries.joinToString("\n") { "${it.key}\n    ${it.value}" })
    }

    /** 화면이 찍는 숫자(유한하지 않으면 `Fmt` 가 예외) */
    private fun show(r: RoundResult) {
        val s = r.solution ?: return
        s.nodes.forEach { Fmt.fixed3(it.drawn.xDia); Fmt.fixed3(it.drawn.z); Fmt.fixed3(it.prog.xDia); Fmt.fixed3(it.prog.z) }
        s.arcs.forEach { a -> Fmt.fixed3(a.r); a.progR?.let { Fmt.fixed3(it) } }
        Fmt.fixed3(s.entry.xDia)
        Fmt.fixed3(s.entry.z)
        s.approach.forEach { Fmt.fixed3(it.xDia); Fmt.fixed3(it.z) }
        s.filled?.let { f -> listOfNotNull(f.mainR, f.d1, f.d2, f.l1, f.l2).forEach { Fmt.fixed3(it) } }
    }

    /**
     * 형상 1~6 × 외·내경 × 비운 칸 · 관통 · 팁 · Z 여유 · 끝 직선 × 노즈R 글 — 예외 0(몇 초). 치수는 풀리는 것이 많게 구의 식으로 맞춰 만든다
     */
    @Test
    fun typesNeverThrow() {
        val rnd = Random(20261110)
        val seen = Seen()
        var solved = 0
        fun pick(vararg v: Double) = v[rnd.nextInt(v.size)]
        fun mm(v: Double) = (v * 1000).toInt() / 1000.0
        for (type in 1..6) for (inner in listOf(false, true)) {
            if (inner && type == 1) continue
            repeat(24) { k ->
                val bigR = if (k % 3 == 0) pick(5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 50.0, 100.0) else rnd.nextInt(1000, 80_000) / 1000.0
                val d2 = mm(rnd.nextDouble(0.2, 2.0) * bigR)
                val d1 = mm(rnd.nextDouble(0.1, 0.95) * d2)
                val l1 = if (k % 2 == 0) pick(1.0, 2.0, 5.0, 10.0, 20.0, 0.5, 0.1) else rnd.nextInt(100, 40_000) / 1000.0
                val l2 = rnd.nextInt(100, 40_000) / 1000.0
                val c1 = pick(0.0, 0.0, 0.2, 0.5, 1.0, 2.0, 5.0)
                val c2 = pick(0.0, 0.0, 0.2, 0.5, 1.0, 2.0, 5.0)
                // 비운 칸: 0 = 주 R, 1 = D1, 2 = D2(형상 5), 그 밖 = L(자동 계산 — 사용자가 말한 상태)
                val blank = rnd.nextInt(5)
                val base = when (type) {
                    1 -> RoundInput(1, mainR = bigR, cornerR1 = c1, l1 = minOf(l1, 1.9 * bigR))
                    2 -> RoundInput(2, mainR = bigR.takeIf { blank != 0 }, cornerR1 = c1, d1 = d1.takeIf { blank != 1 }, l1 = l1, l2 = l2.takeIf { blank == 0 || blank == 1 })
                    3, 4 -> RoundInput(type, mainR = bigR.takeIf { blank != 0 }, cornerR1 = c1, d1 = d1.takeIf { blank != 1 }, l1 = l1.takeIf { blank == 0 || blank == 1 })
                    5 -> RoundInput(
                        5, mainR = bigR.takeIf { blank != 0 }, cornerR1 = c1, cornerR2 = c2, d1 = d1.takeIf { blank != 1 }, d2 = d2.takeIf { blank != 2 }, l1 = l1,
                        l2 = l2.takeIf { blank in 0..2 },
                    )
                    else -> RoundInput(6, mainR = bigR, cornerR1 = c1, cornerR2 = c2, d1 = d1, d2 = d2, l1 = l1, l2 = l2)
                }.copy(
                    inner = inner, through = inner && type >= 5 && rnd.nextBoolean(), legLen = pick(5.0, 5.0, 0.5, 1.0, 10.0, 20.0),
                    // Z 여유는 화면 처음 값(빈칸 = 0)이 대부분
                    zClear = pick(0.0, 0.0, 0.0, 1.0, 2.0), tip = if (rnd.nextInt(4) == 0) rnd.nextInt(10) else null,
                )
                // 가공 방향을 바꾼 것도(2026-11-10 피드백 2 — 뒤집은 시작 규칙 · 접근 줄)
                for (t in noseTexts) for (flip in listOf(false, true)) {
                    val i = base.copy(noseR = nose(t), flip = flip)
                    seen.run({ "$i" }) {
                        val r = RoundCalc.calc(i)
                        if (r.solution != null) solved++
                        show(r)
                        show(RoundCalc.machine(i))
                    }
                }
            }
        }
        seen.check()
        assertTrue(solved > 1000, "풀린 사례가 너무 적다: $solved")
    }

    /** 3점호(외·내경, 정 · 역방향) × 노즈R 글 — 예외 0 */
    @Test
    fun arc3NeverThrows() {
        val rnd = Random(20261111)
        val seen = Seen()
        var solved = 0
        val pts = arcs + List(40) {
            fun x() = rnd.nextInt(0, 60_000) / 1000.0
            fun z() = rnd.nextInt(-40_000, 5_000) / 1000.0
            Arc3Input(inner = rnd.nextBoolean(), x1 = x(), z1 = z(), x2 = x(), z2 = z(), x3 = x(), z3 = z())
        }
        for (base in pts) for (zc in listOf(0.0, 1.0)) for (t in noseTexts) for (flip in listOf(false, true)) {
            val a = base.copy(noseR = nose(t), zClear = zc, flip = flip)
            seen.run({ "$a" }) {
                val r = RoundCalc.arc3(a)
                if (r.solution != null) solved++
                show(r)
                show(RoundCalc.machine(a))
            }
        }
        seen.check()
        assertTrue(solved > 300, "풀린 사례가 너무 적다: $solved")
    }

    /** 내경 + 보링바 칸을 채운 화면: 노즈R 글마다 간섭 검사 · 안전 끝점([HolderStop.plan] — 화면의 `holderOf`)까지 예외 0 */
    @Test
    fun boringBarNeverThrows() {
        val seen = Seen()
        val bars = listOf(
            BoringBar(dia = 16.0, f = 11.0, shape = InsertShape.C, edge = 9.67, kappaDeg = 95.0, overhang = null, dMin = 20.0),
            BoringBar(dia = 8.0, f = 4.5, shape = InsertShape.D, edge = 7.75, kappaDeg = 93.0, overhang = 30.0, dMin = null),
        )
        val texts = listOf("", "0", "0.001", "0.4", "1.2", "20", "999999999999", "-0.4")
        var planned = 0
        fun plan(sol: RoundSolution?, drawn: RoundSolution?, r: Double, faceAtStart: Boolean, bar: BoringBar) {
            if (sol == null) return
            val p = HolderStop.plan(sol, drawn, r, faceAtStart, bar)
            planned++
            p.end?.let { Fmt.fixed3(it.pose.tip.xDia); Fmt.fixed3(it.pose.tip.z) }
        }
        val inner = shapes.filter { it.type != 1 }.map { it.copy(inner = true, legLen = 10.0) } +
            // 큰 구멍(보링바가 들어가는 크기): 사용자가 말한 상태의 내경 · 막힘 / 관통
            listOf(reported.copy(inner = true), reported.copy(inner = true, through = true))
        for (base in inner) for (zc in listOf(0.0, 1.0)) for (t in texts) for (bar in bars) {
            val i = base.copy(noseR = nose(t), zClear = zc)
            seen.run({ "$i $bar" }) { plan(RoundCalc.calc(i).solution, RoundCalc.machine(i).solution, i.noseR, true, bar) }
        }
        for (base in arcs.filter { it.inner }) for (zc in listOf(0.0, 1.0)) for (t in texts) {
            val a = base.copy(noseR = nose(t), zClear = zc)
            seen.run({ "$a" }) { plan(RoundCalc.arc3(a).solution, RoundCalc.machine(a).solution, a.noseR, false, bars[0]) }
        }
        seen.check()
        assertTrue(planned > 50, "검사한 사례가 너무 적다: $planned")
    }
}
