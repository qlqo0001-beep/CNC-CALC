package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Pt
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 안쪽 공구의 바 지름(2026-11-10 피드백 4 — "X6. 으로 가공하면 소재를 X6. 이상으로 다 밀어버림", 설계 20절): 인서트 등이 축을 넘어가도 반대쪽 소재를 깎지 않는다,
 * 바 지름(빈칸 = 자동)으로 몸통을 그리고 구멍보다 큰 몸통은 깎는 대신 '몸통 닿음' 으로 알린다.
 * 닿은 넓이의 기대 값은 따로 짠 손 계산(파이썬 — 래스터 · 볼록 껍질을 옮겨 짠 것)의 수다.
 */
class ToolBarTest {
    /** 내경 X6. — 소재 Ø30 · 돌출 30 · 끝면 Z0 · 구멍 Ø5, T0202 바이트 R0.2 · 팁 2 · C(κr 빈칸 95°) */
    private val bore6 = listOf("O0006", "T0202", "G97S1500M03", "G99", "G0X6.Z2.", "G1Z-10.F.05", "X5.", "G0Z50.", "M30")
    private val stock = StockSpec(30.0, 30.0, 0.0, 5.0, 100.0)
    private val boring = ToolOffset(0.2, 2)

    private fun cut(lines: List<String>, tool: ToolOffset, spec: StockSpec = stock): StockSim {
        val n = lines.first { it.startsWith("T") }.takeLast(2).toInt()
        val tools = mapOf(n to tool)
        val run = SimKit.ok(lines, tools)
        return StockSim(spec, run, tools).also { it.cutTo(run.totalSec) }
    }

    private fun area(s: StockSim): Double = (0 until s.columns).sumOf { i -> s.intervals(i).toList().chunked(2).sumOf { (a, b) -> b - a } * s.dz }

    private fun same(a: StockSim, b: StockSim, what: String) {
        assertEquals(a.columns, b.columns)
        for (i in 0 until a.columns) assertContentEquals(a.intervals(i), b.intervals(i), "$what 열 $i (z ${a.zOf(i)})")
    }

    /** 몸통 닿음(줄 → 넓이) */
    private fun body(s: StockSim): Map<Int, Double> = s.contacts.filter { it.kind == ContactKind.BODY }.associate { it.line to it.area }

    /** 구멍 벽: Z−9.8(노즈 중심) ~ 0 은 반경 3.000, 그 아래 Z−10 까지는 노즈 원(중심 Z−9.8 · X5.6, R0.2), Z−10 아래는 Ø5 그대로 */
    private fun assertBore6(s: StockSim, what: String) {
        var checked = 0
        for (i in 0 until s.columns) {
            val z = s.zOf(i)
            val iv = s.intervals(i)
            assertEquals(2, iv.size, "$what z $z: ${iv.toList()}")
            assertEquals(15.0, iv[1], "$what z $z 바깥은 깎지 않음")
            when {
                z > -9.8 -> {
                    assertTrue(abs(iv[0] - 3.0) <= 1e-9, "$what z $z 구멍 반경 ${iv[0]} (기대 3)")
                    checked++
                }
                z > -10 + 1e-6 -> {
                    val d = sqrt((z + 9.8) * (z + 9.8) + (iv[0] - 2.8) * (iv[0] - 2.8))
                    assertTrue(abs(d - 0.2) <= ToolShape.SAG + 1e-9, "$what z $z 노즈 원: ${iv[0]}")
                }
                else -> assertEquals(2.5, iv[0], "$what z $z 는 깎지 않음")
            }
        }
        assertEquals(980, checked, "$what 확인한 열")
    }

    /** 피드백 그대로: 공구 설정을 건드리지 않고(바 지름 빈칸) X6. 내경을 깎으면 구멍이 Ø6 이어야 한다 — 620edc6 은 Ø18 ~ 20 으로 밀었다 */
    @Test
    fun boreX6KeepsStockBeyondX6() {
        val s = cut(bore6, boring)
        assertBore6(s, "자동")
        assertTrue(s.contacts.isEmpty(), "닿음 없음: ${s.contacts}")
        // 손 계산: 깎은 넓이 = 처음 넓이 − 남은 넓이 = 4.99 mm²(620edc6 은 68.07)
        assertEquals(4.99, 30.0 * 12.5 - area(s), 0.01)
    }

    /** 구멍(Ø5 → 깎은 Ø6)에 들어가는 바 지름은 닿지 않고, 넘는 바 지름은 깎지 않고 '몸통 닿음' — 어느 값도 소재를 X6 너머로 밀지 않는다 */
    @Test
    fun barDiameterDecidesBodyContact() {
        val auto = cut(bore6, boring)
        for (w in listOf(0.05, 1.0, 4.0, 5.0, 5.5)) {
            val s = cut(bore6, boring.copy(barDia = w))
            same(auto, s, "바 $w")
            assertTrue(s.contacts.isEmpty(), "바 $w 닿음 없음: ${s.contacts}")
        }
        // 바 6 = 깎은 구멍과 같음 → Z 로 들어갈 때는 벽에 닿기만(닿음 아님), X5. 로 빼면 등이 반대쪽 벽(반경 3 → 3.5)에 닿는다
        val s6 = cut(bore6, boring.copy(barDia = 6.0))
        assertBore6(s6, "바 6")
        assertEquals(setOf(7, 8), body(s6).keys)
        assertEquals(4.758, body(s6).getValue(7), 0.01)
        assertEquals(4.758, body(s6).getValue(8), 0.01)
        // 바 8 · 16(인서트보다 큼 — 바 띠): 들어가는 줄부터 닿는다
        for ((w, areas) in listOf(8.0 to listOf(18.812, 23.569, 23.569), 16.0 to listOf(90.559, 95.316, 95.316))) {
            val s = cut(bore6, boring.copy(barDia = w))
            assertBore6(s, "바 $w")
            assertEquals(setOf(6, 7, 8), body(s).keys, "바 $w")
            for ((k, line) in listOf(6, 7, 8).withIndex()) assertEquals(areas[k], body(s).getValue(line), 0.01, "바 $w ${line}줄")
            assertTrue(s.contacts.all { it.kind == ContactKind.BODY }, "바 $w: ${s.contacts}")
            // 처음 닿는 자리: 몸통에서 반대쪽 벽(반경 3)을 넘는 곳(날끝에서 6 mm 아래 — 주절삭날을 따라 날끝보다 0.507 뒤)이 끝면(Z0)에 들어설 때
            val first = s.contacts.first()
            assertEquals(6, first.line)
            assertEquals(3.0, first.at.x, 1e-9)
            assertTrue(first.at.z < -0.507 && first.at.z > -0.56, "바 $w 처음 닿는 Z ${first.at.z}")
        }
    }

    /** 자동 값 = 인서트 깊이와 '그 공구가 지나는 가장 작은 지름' 가운데 작은 쪽(1 mm 이상). 안쪽 공구가 아니면 바 지름을 쓰지 않는다 */
    @Test
    fun autoBar() {
        val run = SimKit.ok(bore6, mapOf(2 to boring))
        assertEquals(mapOf(2 to 5.0), ToolShape.barFit(run))
        assertEquals(5.0, ToolShape.barOf(boring, 5.0))
        // 프로그램에 없는 번호(fit 없음) = 인서트 깊이. C · R0.2 · κr 95° 의 깊이 12.9802(손 계산)
        assertEquals(12.9802, ToolShape.barOf(boring)!!, 1e-4)
        assertEquals(12.9802, ToolShape.barOf(boring, 40.0)!!, 1e-4)
        assertEquals(ToolShape.BAR_MIN, ToolShape.barOf(boring, 0.0))
        assertEquals(8.0, ToolShape.barOf(boring.copy(barDia = 8.0), 5.0))
        // 깊이: 홈 = max(12, 3w), 나사 = 8 + 노즈 R
        assertEquals(12.0, ToolShape.barOf(ToolOffset(0.1, 2, ToolKind.GROOVE, width = 2.0))!!, 1e-9)
        assertEquals(15.0, ToolShape.barOf(ToolOffset(0.2, 1, ToolKind.GROOVE, width = 5.0))!!, 1e-9)
        assertEquals(8.1, ToolShape.barOf(ToolOffset(0.1, 6, ToolKind.THREAD))!!, 1e-9)
        // 안쪽 공구가 아니거나 인서트 모양이 없으면 null
        for (t in listOf(
            ToolOffset(0.4, 3), ToolOffset(0.4, 4), ToolOffset(0.4, 0), ToolOffset(0.4, null), ToolOffset(0.4, 8),
            ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0), ToolOffset(0.2, 2, ToolKind.GROOVE), ToolOffset(0.0, 0, ToolKind.THREAD),
            ToolOffset(0.0, 3, ToolKind.THREAD), ToolOffset(2.0, 2, ToolKind.ROUND), ToolOffset(kind = ToolKind.DIE, tip = 2),
            ToolOffset(0.4, 2, kappaDeg = 150.0),
        )) {
            assertNull(ToolShape.barOf(t.copy(barDia = 8.0), 5.0), "$t")
            assertEquals(ToolShape.outline(t), ToolShape.body(t.copy(barDia = 8.0), 5.0), "$t 몸통 = 인서트")
        }
        // 틀린 값은 자동으로 돈다
        for (bad in listOf(0.0, -3.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 500.001, 1e9)) {
            assertTrue(!ToolShape.barOk(bad), "$bad")
            assertEquals(5.0, ToolShape.barOf(boring.copy(barDia = bad), 5.0), "$bad")
        }
        assertTrue(ToolShape.barOk(0.001) && ToolShape.barOk(500.0))
    }

    private fun convexCcw(p: List<Pt>): Boolean {
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            val c = p[(i + 2) % p.size]
            if ((b.z - a.z) * (c.x - b.x) - (b.x - a.x) * (c.z - b.z) < -1e-12) return false
        }
        return ToolShape.area(p) > 0
    }

    /** 몸통 단면: 바 지름 0.05 ~ 500 에서 볼록 · 반시계 · 날끝 x = 0 · 깊이 = 바 지름, 인서트보다 작으면 인서트 안 · 크면 인서트를 품는다 */
    @Test
    fun bodyShapes() {
        val tools = listOf(
            ToolOffset(0.4, 2), ToolOffset(0.2, 2), ToolOffset(0.0, 2), ToolOffset(0.4, 1),
            ToolOffset(0.4, 2, insert = InsertShape.D), ToolOffset(0.4, 2, insert = InsertShape.V), ToolOffset(0.4, 2, insert = InsertShape.T),
            ToolOffset(0.4, 2, insert = InsertShape.T, kappaDeg = 90.0), ToolOffset(0.4, 1, insert = InsertShape.T, kappaDeg = 75.0),
            ToolOffset(0.1, 2, ToolKind.GROOVE, width = 2.0), ToolOffset(0.2, 1, ToolKind.GROOVE, width = 5.0), ToolOffset(1.0, 2, ToolKind.GROOVE, width = 2.0),
            ToolOffset(0.0, 2, ToolKind.THREAD), ToolOffset(0.1, 6, ToolKind.THREAD), ToolOffset(0.0, 1, ToolKind.THREAD, threadDeg = 55.0),
        )
        for (t in tools) {
            val insert = ToolShape.outline(t)
            val (i0, i1) = ToolShape.box(insert)
            val h = -i0.x
            assertEquals(0.0, i1.x, 1e-12, "$t 날끝 x")
            assertEquals(h, ToolShape.barOf(t)!!, 1e-12, "$t 깊이")
            assertEquals(insert, ToolShape.body(t), "$t 바 지름 없음 = 인서트")
            for (w in listOf(0.05, 0.5, 1.0, 3.0, 5.0, 8.0, h, 12.0, 16.0, 25.0, 40.0, 500.0)) {
                val b = ToolShape.body(t.copy(barDia = w))
                assertTrue(b.size >= 3, "$t 바 $w 점 ${b.size}")
                assertTrue(convexCcw(b), "$t 바 $w 볼록 · 반시계: $b")
                val (b0, b1) = ToolShape.box(b)
                assertEquals(0.0, b1.x, 1e-9, "$t 바 $w 날끝 x")
                assertEquals(-w, b0.x, 1e-9, "$t 바 $w 깊이")
                // z 범위는 인서트 그대로(바 띠는 인서트의 z 범위 안)
                assertTrue(b0.z >= i0.z - 1e-9 && b1.z <= i1.z + 1e-9, "$t 바 $w z ${b0.z} ~ ${b1.z} (인서트 ${i0.z} ~ ${i1.z})")
                if (w <= h) assertTrue(ToolShape.area(b) <= ToolShape.area(insert) + 1e-9, "$t 바 $w 넓이")
                else assertTrue(insert.all { p -> b.indices.all { k -> cross(b[k], b[(k + 1) % b.size], p) >= -1e-9 } }, "$t 바 $w 가 인서트를 품는다")
            }
        }
        // 손 계산(C · R0.2 · 팁 2): 바 4 의 넓이 41.4723 · z 0 ~ 12.1945, 바 16 의 넓이 183.4274 · z 0 ~ 12.9802
        val b4 = ToolShape.body(boring.copy(barDia = 4.0))
        assertEquals(41.4723, ToolShape.area(b4), 1e-3)
        assertEquals(12.1945, ToolShape.box(b4).second.z, 1e-3)
        val b16 = ToolShape.body(boring.copy(barDia = 16.0))
        assertEquals(183.4274, ToolShape.area(b16), 1e-3)
        assertEquals(12.9802, ToolShape.box(b16).second.z, 1e-3)
        // 바 띠의 앞은 주절삭날(95° — 5° 뒤로 누움) 연장선: x = −16 에서 z 1.3816
        assertTrue(b16.any { abs(it.x + 16.0) < 1e-9 && abs(it.z - 1.3816) < 1e-3 }, "$b16")
    }

    private fun cross(a: Pt, b: Pt, p: Pt) = (b.z - a.z) * (p.x - a.x) - (b.x - a.x) * (p.z - a.z)

    /** 내경 홈(폭 2 · 팁 2, 구멍 Ø8 에서 X10. 홈): 홈 밖의 구멍은 Ø8 그대로 — 620edc6 은 인서트 높이 12 가 축을 넘어 Ø17 로 밀었다. 바 10 은 닿음 */
    @Test
    fun innerGroove() {
        val prog = listOf("T0404", "G97S800M03", "G99", "G0X7.Z2.", "Z-10.", "G1X10.F.05", "G0X7.", "Z50.", "M30")
        val spec = StockSpec(30.0, 30.0, 0.0, 8.0, 100.0)
        val tool = ToolOffset(0.1, 2, ToolKind.GROOVE, width = 2.0)
        val s = cut(prog, tool, spec)
        for (i in 0 until s.columns) {
            val z = s.zOf(i)
            val iv = s.intervals(i)
            // 홈 바닥(노즈 R0.1 모서리 사이) 반경 5, 홈 밖 4
            if (z > -9.9 && z < -8.1) assertTrue(abs(iv[0] - 5.0) <= 1e-9, "홈 z $z: ${iv[0]}")
            if (z < -10 - 1e-6 || z > -8 + 1e-6) assertEquals(4.0, iv[0], "홈 밖 z $z")
            assertEquals(15.0, iv.last())
        }
        assertTrue(s.contacts.isEmpty(), "${s.contacts}")
        assertEquals(mapOf(4 to 7.0), ToolShape.barFit(s.run))
        val s10 = cut(prog, tool.copy(barDia = 10.0), spec)
        same(s, s10, "바 10")
        assertEquals(setOf(5, 6, 7, 8), body(s10).keys)
        assertEquals(25.0, body(s10).getValue(5), 0.01)
        assertEquals(3.004, body(s10).getValue(6), 0.01)
        assertEquals(23.004, body(s10).getValue(8), 0.01)
    }

    /** 내경 나사 공구의 급송(구멍 Ø5 에 X4.5 로): 인서트 높이 8 이 축을 넘어도 깎지 않는다(620edc6 은 Ø11.5 로 밀고 '급송 깎음'). 바 6 은 닿음 */
    @Test
    fun innerThreadToolRapid() {
        val prog = listOf("T0505", "G97S500M03", "G99", "G0X4.5Z5.", "Z-12.", "Z5.", "M30")
        val tool = ToolOffset(0.0, 2, ToolKind.THREAD)
        val s = cut(prog, tool)
        for (i in 0 until s.columns) assertContentEquals(doubleArrayOf(2.5, 15.0), s.intervals(i), "z ${s.zOf(i)}")
        assertTrue(s.contacts.isEmpty(), "${s.contacts}")
        val s6 = cut(prog, tool.copy(barDia = 6.0))
        same(s, s6, "바 6")
        assertEquals(setOf(5, 6), body(s6).keys)
        assertEquals(18.879, body(s6).getValue(5), 0.01)
        assertEquals(18.879, body(s6).getValue(6), 0.01)
    }

    /**
     * 인서트보다 굵은 바(16)의 띠: 구멍 Ø20 을 X40. 으로 넓히고 바닥을 X20. 까지 훑어도 닿지 않는다(띠의 앞이 주절삭날 연장선이라 바닥의 5° 원뿔을 건드리지 않는다).
     * 구멍이 Ø10 이면 X40. 으로 한 번에 넓힐 때 인서트(깊이 12.96 — 반경 7.04 위만 깎는다)가 못 깎는 살(반경 5 ~)에 바 띠가 닿는다 — 깎지 않고 남긴다.
     */
    @Test
    fun barWiderThanInsert() {
        val prog = listOf("T0202", "G97S800M03", "G99", "G0X40.Z2.", "G1Z-10.F.1", "X20.", "G0Z5.", "M30")
        val tool = ToolOffset(0.4, 2, barDia = 16.0)
        val ok = cut(prog, tool, StockSpec(50.0, 30.0, 0.0, 20.0, 125.0))
        assertTrue(ok.contacts.none { it.kind == ContactKind.BODY }, "${ok.contacts}")
        same(cut(prog, tool.copy(barDia = null), StockSpec(50.0, 30.0, 0.0, 20.0, 125.0)), ok, "바 16")
        assertEquals(20.0, ok.innerAt(-5.0)!!, 1e-9)

        val hit = cut(prog - "X20.", tool, StockSpec(50.0, 30.0, 0.0, 10.0, 125.0))
        assertEquals(setOf(5, 6), body(hit).keys, "${hit.contacts}")
        val iv = hit.intervals(((-5.0 - hit.chuckZ) / hit.dz).toInt())
        assertEquals(4, iv.size, "${iv.toList()}")
        assertEquals(5.0, iv[0])
        assertTrue(iv[1] > 20 - 12.9602 && iv[1] < 8.0, "남은 살 위 끝 ${iv[1]}")
        assertEquals(20.0, iv[2], 1e-9)
        assertEquals(25.0, iv[3])
    }

    /** 바깥 공구 · 둥근 공구는 바 지름을 무시한다 — 단면도 깎은 결과도 그대로(축 아래는 예전처럼 접어 깎는다) */
    @Test
    fun outerToolsIgnoreBar() {
        val prog = listOf("T0101", "G97S800M03", "G99", "G0X20.Z2.", "G1Z-10.F.1", "X-1.", "G0Z5.", "M30")
        val spec = StockSpec(30.0, 30.0, 0.0, 0.0, 100.0)
        for (t in listOf(ToolOffset(0.4, 3), ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0), ToolOffset(2.0, 0, ToolKind.ROUND), ToolOffset(0.4, 4))) {
            val a = cut(prog, t, spec)
            val b = cut(prog, t.copy(barDia = 3.0), spec)
            same(a, b, "$t")
            assertEquals(a.contacts, b.contacts, "$t")
            assertTrue(a.contacts.none { it.kind == ContactKind.BODY })
        }
        // 축 아래에서만 도는 둥근 공구(중심 X−10): 예전 규칙 그대로 |x| 로 접어 반경 3 ~ 7 을 깎는다
        val round = cut(listOf("T0101", "G97S800M03", "G99", "G0X-10.Z2.", "G1Z-10.F.1", "G0Z5.", "M30"), ToolOffset(2.0, 0, ToolKind.ROUND), spec)
        assertContentEquals(doubleArrayOf(0.0, 3.0, 7.0, 15.0), round.intervals(((-5.0 - round.chuckZ) / round.dz).toInt()))
    }

    /**
     * 안쪽 공구(팁 2)를 축 아래(음수 X)에서 바깥 깎기에 쓰면 공구 쪽은 날끝이 있는 x ≤ 0 이다 — 같은 글을 양수 X 로 뒤집고 팁 3(X 거울)으로 깎은 것과 같고,
     * 몸통 닿음도 없다(검증 V1: 공구 쪽을 x ≥ 0 으로 못박아 하나도 안 깎고 헛 '몸통 닿음' 을 냈다)
     */
    @Test
    fun negativeXInnerToolCutsOnItsSide() {
        val spec = StockSpec(30.0, 30.0, 0.0, 0.0, 100.0)
        fun prog(s: String) = listOf("O1114", "T0202", "G97S1000M03", "G99", "G0X${s}24.Z2.", "G1Z-10.F.2", "X${s}32.", "G0Z50.", "M30")
        val neg = cut(prog("-"), ToolOffset(0.4, 2), spec)
        val pos = cut(prog(""), ToolOffset(0.4, 3), spec)
        assertContentEquals(doubleArrayOf(0.0, 12.0), neg.intervals(((-5.0 - neg.chuckZ) / neg.dz).toInt()), "Z−5 는 Ø24 까지 깎임")
        assertTrue(neg.contacts.none { it.kind == ContactKind.BODY }, "몸통 닿음 없음: ${neg.contacts}")
        same(pos, neg, "팁 3 · 양수 X 와 같음")
        // 굵은 바(인서트보다 깊음)도 몸통은 날끝 바깥(반경 12 위) 쪽 — 이미 깎는 범위 밖 소재(반경 15 까지)에 닿는 만큼만 알린다
        val thick = cut(prog("-"), ToolOffset(0.4, 2, barDia = 20.0), spec)
        same(neg, thick, "굵은 바도 깎은 결과는 같음")
    }

    /** 틀린 바 지름(0 · 음수 · 숫자 아님 · 아주 큰 값)을 넣어도 던지지 않고 자동과 같게 돈다. 받는 범위의 양 끝도 돈다 */
    @Test
    fun badBarValuesRunAsAuto() {
        val auto = cut(bore6, boring)
        for (bad in listOf(0.0, -3.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e9, 1e300)) {
            val s = cut(bore6, boring.copy(barDia = bad))
            same(auto, s, "바 $bad")
            assertEquals(auto.contacts, s.contacts, "바 $bad")
        }
        for (edge in listOf(0.001, 1e-9, 500.0)) {
            val s = cut(bore6, boring.copy(barDia = edge))
            same(auto, s, "바 $edge")
            assertBore6(s, "바 $edge")
        }
        assertEquals(setOf(6, 7, 8), body(cut(bore6, boring.copy(barDia = 500.0))).keys)
    }

    /** 되감아도 몸통 닿음 · 소재가 같다(저장점에서 다시 깎음) */
    @Test
    fun rewindKeepsBodyContacts() {
        val tools = mapOf(2 to boring.copy(barDia = 8.0))
        val run = SimKit.ok(bore6, tools)
        val s = StockSim(stock, run, tools)
        s.cutTo(run.totalSec)
        val contacts = s.contacts
        val fresh = cut(bore6, boring.copy(barDia = 8.0))
        s.cutTo(run.totalSec * 0.4)
        s.cutTo(run.totalSec)
        same(fresh, s, "되감기")
        assertEquals(contacts, s.contacts)
    }

    /**
     * 예제 16개: 몸통 닿음이 없다(안쪽 공구 예제 ⑩ · ⑬ 의 자동 바 지름은 구멍에 맞는다).
     * 바 지름을 인서트 깊이로 넣어도(몸통 = 인서트) 없다 — 그때의 몸통 닿음 넓이가 곧 620edc6 이 축 너머를 접어 더 깎던 넓이라, 예제의 깎은 결과가 620edc6 과
     * 같다는 뜻이다(이동마다 [StockSim.BODY_AREA] 안). 인서트 등이 축을 넘는 안쪽 공구 예제(자동 값 < 인서트 깊이)가 있어 빈 확인이 아니다.
     */
    @Test
    fun examplesHaveNoBodyContact() {
        var crossing = 0
        for (e in SimExamples.all) {
            val run = SimKit.ok(e.text.lines(), e.tools)
            val spec = StockAuto.of(run) ?: continue
            val s = StockSim(spec, run, e.tools).also { it.cutTo(run.totalSec) }
            assertTrue(s.contacts.none { it.kind == ContactKind.BODY }, "${e.name}: ${s.contacts}")
            val fits = ToolShape.barFit(run)
            val full = e.tools.mapValues { (n, t) ->
                val h = ToolShape.barOf(t.copy(barDia = null)) ?: return@mapValues t
                if ((fits[n] ?: h) < h) crossing++
                t.copy(barDia = h)
            }
            val old = StockSim(spec, run, full).also { it.cutTo(run.totalSec) }
            same(s, old, e.name)
            assertEquals(s.contacts, old.contacts, "${e.name} 바 지름 = 인서트 깊이")
        }
        assertTrue(crossing > 0, "인서트 등이 축을 넘는 안쪽 공구 예제가 있어야 한다")
    }
}
