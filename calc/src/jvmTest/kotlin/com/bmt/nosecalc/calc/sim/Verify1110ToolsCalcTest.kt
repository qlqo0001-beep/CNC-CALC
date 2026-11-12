package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Pt
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.bmt.nosecalc.calc.sim.v1110old.StockSim as OldStockSim

/**
 * 2026-11-10 공구 갈래 독립 검증(피드백 4 — 바 지름). 구현자의 시험과 따로 짠 검사: 예제 16개를 620edc6 의 StockSim · ToolShape 사본(v1110old)과
 * 열마다 대조, 손 계산 윤곽(내경 바이트 · 내경 홈), 극값, 종류마다.
 */
class Verify1110ToolsCalcTest {
    private fun cut(lines: List<String>, tools: Map<Int, ToolOffset>, spec: StockSpec): StockSim {
        val run = SimKit.ok(lines, tools)
        return StockSim(spec, run, tools).also { it.cutTo(run.totalSec) }
    }

    private fun body(s: StockSim): Map<Int, Double> = s.contacts.filter { it.kind == ContactKind.BODY }.associate { it.line to it.area }

    /** 예제 16개: 소재 칸 빈칸(자동 소재 — 앱과 같은 길)으로 새 코드와 620edc6 코드의 깎인 열 · 닿음이 같아야 한다(반 시각 · 끝 시각 둘 다) */
    @Test
    fun examplesSameAs620edc6() {
        val diffs = ArrayList<String>()
        var compared = 0
        for (e in SimExamples.all) {
            val run = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
            val spec = StockAuto.of(run) ?: continue
            compared++
            for (frac in listOf(0.5, 1.0)) {
                val a = StockSim(spec, run, e.tools).also { it.cutTo(run.totalSec * frac) }
                val b = OldStockSim(spec, run, e.tools).also { it.cutTo(run.totalSec * frac) }
                assertEquals(b.columns, a.columns)
                var bad = 0
                for (i in 0 until a.columns) if (!a.intervals(i).contentEquals(b.intervals(i))) bad++
                if (bad > 0) diffs += "${e.name} @$frac: 다른 열 $bad / ${a.columns}"
                val ca = a.contacts.map { listOf(it.kind.name, it.step, it.line, it.t, it.at, it.area) }
                val cb = b.contacts.map { listOf(it.kind.name, it.step, it.line, it.t, it.at, it.area) }
                if (ca != cb) diffs += "${e.name} @$frac: 닿음 새 $ca / 옛 $cb"
            }
        }
        assertEquals(16, SimExamples.all.size)
        assertTrue(compared >= 15, "대조한 예제 $compared")
        assertTrue(diffs.isEmpty(), diffs.joinToString("\n"))
    }

    // ---- 내경 바이트: Ø8 구멍을 X10. 으로(팁 2 · R0.4 · D 인서트 · κr 93 빈칸) — 손 계산

    private val boreLines = listOf("O1110", "T0303", "G97S1200M03", "G99", "G0X10.Z3.", "G1Z-15.F.08", "X8.6", "G0Z60.", "M30")
    private val boreSpec = StockSpec(40.0, 40.0, 0.0, 8.0, 120.0)
    private val bar = ToolOffset(0.4, 2, ToolKind.TURN, InsertShape.D)

    /**
     * 손 계산: 날끝(가상 인선) X10 → 노즈 꼭대기 반경 5. Z−14.6(노즈 중심) ~ 0 은 반경 5, Z−15 ~ −14.6 은 노즈 원(중심 Z−14.6 · 반경 4.6, R0.4),
     * Z−15 아래는 Ø8(반경 4) 그대로, 바깥 20 그대로
     */
    private fun assertBore10(s: StockSim, what: String) {
        var n5 = 0
        for (i in 0 until s.columns) {
            val z = s.zOf(i)
            val iv = s.intervals(i)
            assertEquals(2, iv.size, "$what z $z ${iv.toList()}")
            assertEquals(20.0, iv[1], "$what z $z 바깥")
            when {
                z > -14.6 -> {
                    assertTrue(abs(iv[0] - 5.0) <= 1e-9, "$what z $z 반경 ${iv[0]} (기대 5)")
                    n5++
                }
                z > -15 + 1e-6 -> {
                    val d = sqrt((z + 14.6) * (z + 14.6) + (iv[0] - 4.6) * (iv[0] - 4.6))
                    assertTrue(abs(d - 0.4) <= ToolShape.SAG + 1e-9, "$what z $z 노즈 원 ${iv[0]}")
                }
                else -> assertEquals(4.0, iv[0], "$what z $z 깎지 않음")
            }
        }
        assertTrue(n5 > 100, "$what 반경 5 열 $n5")
    }

    @Test
    fun boreX10AutoAndFittingBars() {
        for (w in listOf(null, 0.01, 2.0, 7.9, 8.0, 9.2)) {
            val s = cut(boreLines, mapOf(3 to bar.copy(barDia = w)), boreSpec)
            assertBore10(s, "바 $w")
            assertTrue(s.contacts.isEmpty(), "바 $w 닿음 없음: ${s.contacts}")
        }
    }

    /** 구멍(깎은 Ø10)보다 굵은 바: 깎인 윤곽은 그대로(말없이 깎지 않음) + 몸통 닿음. 뺀 뒤(X8.6) 반대쪽 벽까지 = 5 + 4.3 → 9.3 넘으면 7 · 8줄 */
    @Test
    fun boreX10ThickBarWarnsNotCuts() {
        for (w in listOf(10.5, 12.0, 16.0, 25.0, 500.0)) {
            val s = cut(boreLines, mapOf(3 to bar.copy(barDia = w)), boreSpec)
            assertBore10(s, "바 $w")
            assertTrue(s.contacts.all { it.kind == ContactKind.BODY }, "바 $w ${s.contacts}")
            assertTrue(body(s).keys.containsAll(setOf(7, 8)), "바 $w 닿은 줄 ${body(s).keys}")
            assertTrue(body(s).values.all { it > StockSim.BODY_AREA }, "바 $w ${body(s)}")
        }
        // 들어가는 줄(6줄)에서도 닿는 것은 바 > 구멍 Ø8 의 반대쪽(5 + 4 = 9 … 깎는 링 반경 5 까지는 깎이므로 바 > 10)
        val s12 = cut(boreLines, mapOf(3 to bar.copy(barDia = 12.0)), boreSpec)
        assertTrue(6 in body(s12).keys, "바 12 는 6줄부터: ${body(s12).keys}")
    }

    /** 틀린 바 지름 값(0 · 음수 · NaN · ∞ · 500 초과)은 던지지 않고 자동과 같다 */
    @Test
    fun badBarValuesAreAuto() {
        val auto = cut(boreLines, mapOf(3 to bar), boreSpec)
        for (w in listOf(0.0, -1.0, -1e300, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 500.0001, 1e300, Double.MIN_VALUE)) {
            val s = cut(boreLines, mapOf(3 to bar.copy(barDia = w)), boreSpec)
            if (ToolShape.barOk(w)) continue
            for (i in 0 until s.columns) assertTrue(s.intervals(i).contentEquals(auto.intervals(i)), "바 $w 열 $i")
            assertEquals(auto.contacts, s.contacts, "바 $w")
            assertEquals(ToolShape.body(bar), ToolShape.body(bar.copy(barDia = w)), "바 $w 몸통")
        }
        // 아주 작은 값(받는 범위 안): 그림 · 닿음이 던지지 않는다
        val tiny = cut(boreLines, mapOf(3 to bar.copy(barDia = Double.MIN_VALUE)), boreSpec)
        assertBore10(tiny, "바 MIN_VALUE")
    }

    /** 바깥 공구(팁 3) · 둥근 · 다이스에 바 지름을 넣어도 결과는 그대로 */
    @Test
    fun outerToolsIgnoreBar() {
        val lines = listOf("O1111", "T0101", "G97S1000M03", "G99", "G0X26.Z2.", "G1Z-12.F.2", "X32.", "G0Z50.", "M30")
        val spec = StockSpec(30.0, 30.0, 0.0, 0.0, 100.0)
        for (t in listOf(ToolOffset(0.8, 3), ToolOffset(0.4, 4), ToolOffset(3.0, 3, ToolKind.ROUND), ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0))) {
            val a = cut(lines, mapOf(1 to t), spec)
            val b = cut(lines, mapOf(1 to t.copy(barDia = 4.0)), spec)
            for (i in 0 until a.columns) assertTrue(a.intervals(i).contentEquals(b.intervals(i)), "$t 열 $i")
            assertEquals(a.contacts, b.contacts, "$t")
        }
    }

    // ---- 내경 홈: Ø8 구멍, 홈 폭 2 · 팁 2 · R0, X7. 로 들어가 Z−6. 에서 X12. 까지

    private val grooveLines = listOf("O1112", "T0404", "G97S800M03", "G99", "G0X7.Z2.", "Z-6.", "G1X12.F.05", "G0X7.", "Z50.", "M30")
    private val grooveSpec = StockSpec(30.0, 30.0, 0.0, 8.0, 100.0)
    private val groove = ToolOffset(0.0, 2, ToolKind.GROOVE, width = 2.0)

    /** 손 계산: 팁 2 홈 = 날끝 (Z−6, X12) 에서 +Z 로 폭 2 → Z −6 ~ −4 는 반경 6, 그 밖은 반경 4(구멍) 그대로 */
    private fun assertGroove(s: StockSim, what: String) {
        for (i in 0 until s.columns) {
            val z = s.zOf(i)
            val iv = s.intervals(i)
            assertEquals(2, iv.size, "$what z $z ${iv.toList()}")
            assertEquals(15.0, iv[1], "$what z $z 바깥")
            val want = if (z > -6 && z < -4) 6.0 else 4.0
            assertTrue(abs(iv[0] - want) <= 1e-9, "$what z $z 반경 ${iv[0]} (기대 $want)")
        }
    }

    @Test
    fun innerGrooveCutsOnlyTheGroove() {
        for (w in listOf(null, 3.0, 7.0)) {
            val s = cut(grooveLines, mapOf(4 to groove.copy(barDia = w)), grooveSpec)
            assertGroove(s, "바 $w")
            assertTrue(s.contacts.isEmpty(), "바 $w 닿음 없음 ${s.contacts}")
        }
        // 바 9 > 구멍 Ø8: X7. 로 들어갈 때(6줄 Z−6.) 반대쪽 벽에 닿음 — 깎지 않음
        val s9 = cut(grooveLines, mapOf(4 to groove.copy(barDia = 9.0)), grooveSpec)
        assertGroove(s9, "바 9")
        assertTrue(6 in body(s9).keys, "바 9 닿은 줄 ${body(s9).keys}")
        assertTrue(s9.contacts.all { it.kind == ContactKind.BODY }, "${s9.contacts}")
        // 620edc6 은 인서트 등(깊이 12)을 접어 구멍 전체를 밀었다(급송 깎음) — 고친 것 확인
        val run = SimKit.ok(grooveLines, mapOf(4 to groove))
        val old = OldStockSim(grooveSpec, run, mapOf(4 to groove)).also { it.cutTo(run.totalSec) }
        assertTrue((0 until old.columns).any { old.intervals(it).firstOrNull()?.let { r -> r > 6.0 + 1e-6 } == true }, "옛 코드는 구멍을 밀었다")
    }

    /** 내경 나사(G92, 팁 6 · 60°): 던지지 않고, 나사 패스는 몸통 닿음을 보지 않는다. 구멍 Ø8.5 에 바 12 → 들어가는 급송에서 닿음 */
    @Test
    fun innerThreadBar() {
        val lines = listOf(
            "O1113", "T0505", "G97S600M03", "G99", "G0X8.Z5.", "G92X9.2Z-12.F1.25", "X9.6", "X9.9", "X10.", "G0Z50.", "M30",
        )
        val spec = StockSpec(30.0, 30.0, 0.0, 8.5, 100.0)
        val th = ToolOffset(0.1, 6, ToolKind.THREAD, threadDeg = 60.0)
        for (w in listOf(null, 1.0, 7.0, 12.0, 400.0)) {
            val s = cut(lines, mapOf(5 to th.copy(barDia = w)), spec)
            assertTrue(s.contacts.none { it.kind != ContactKind.BODY && it.kind != ContactKind.RAPID_CUT }, "바 $w ${s.contacts}")
            if (w == null || w <= 8.0) assertTrue(body(s).isEmpty(), "바 $w ${body(s)}")
        }
    }

    /** 팁 1 몸통 = 팁 2 몸통의 Z 거울(바 띠 포함), 몸통의 가장 낮은 x = −바 지름 */
    @Test
    fun bodyShapes() {
        for (ins in InsertShape.entries) for (w in listOf(0.5, 3.0, 9.0, 20.0)) {
            val t2 = ToolOffset(0.4, 2, ToolKind.TURN, ins, barDia = w)
            val t1 = t2.copy(tip = 1)
            val b2 = ToolShape.body(t2)
            val b1 = ToolShape.body(t1)
            assertEquals(-w, b2.minOf { it.x }, 1e-9, "$ins 바 $w")
            assertEquals(-w, b1.minOf { it.x }, 1e-9, "$ins 바 $w 팁 1")
            val m = b2.map { Pt(-it.z, it.x) }
            for (p in b1) assertTrue(m.any { abs(it.z - p.z) < 1e-9 && abs(it.x - p.x) < 1e-9 }, "$ins 바 $w 팁 1 점 $p 이 팁 2 거울에 없음")
            // 몸통은 날끝(가상 인선) 위로 나가지 않는다
            assertTrue(b2.all { it.x <= 1e-9 } && b1.all { it.x <= 1e-9 }, "$ins 바 $w")
        }
        for (tip in listOf(1, 2, 6)) for (w in listOf(1.0, 30.0)) {
            val b = ToolShape.body(ToolOffset(0.1, tip, ToolKind.THREAD, barDia = w))
            assertEquals(-w, b.minOf { it.x }, 1e-9, "나사 팁 $tip 바 $w")
        }
    }

    /**
     * 음수 X 로 바깥을 깎는 글(반대편 공구 — 몸통이 축에서 먼 −x 쪽이라 팁 2): 620edc6 은 접어서 Ø24 로 깎았다.
     * 새 코드는 안쪽 공구로 보고 x ≥ 0 만 깎아 아무것도 깎지 않고 몸통 닿음을 낸다 — 이 검사는 그 차이를 드러낸다(실패 = 회귀).
     */
    @Test
    fun negativeXOuterTurnWithTip2() {
        val lines = listOf("O1114", "T0202", "G97S1000M03", "G99", "G0X-24.Z2.", "G1Z-10.F.2", "X-32.", "G0Z50.", "M30")
        val spec = StockSpec(30.0, 30.0, 0.0, 0.0, 100.0)
        val tools = mapOf(2 to ToolOffset(0.4, 2))
        val run = SimKit.ok(lines, tools)
        val old = OldStockSim(spec, run, tools).also { it.cutTo(run.totalSec) }
        val now = StockSim(spec, run, tools).also { it.cutTo(run.totalSec) }
        val z = -5.0
        val i = (0 until old.columns).minBy { abs(old.zOf(it) - z) }
        assertEquals(12.0, old.intervals(i).last(), 1e-6, "옛 코드: Z−5 에서 반경 12")
        assertEquals(old.intervals(i).last(), now.intervals(i).last(), 1e-6, "새 코드 Z−5 반경 (옛 12) — 닿음 ${now.contacts}")
    }
}
