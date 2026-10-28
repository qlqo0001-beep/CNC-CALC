package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.sim.SimKit.has
import com.bmt.nosecalc.calc.sim.SimKit.moves
import com.bmt.nosecalc.calc.sim.SimKit.near
import com.bmt.nosecalc.calc.sim.SimKit.ok
import com.bmt.nosecalc.calc.sim.SimKit.stops
import com.bmt.nosecalc.calc.sim.SimKit.xz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 부프로그램 M98 / M99(`docs/simulator-design.md` 1.8 — S 4-131~4-132) */
class SubTest {
    /** 부프로그램 O1000: W-1 만큼 이송 — 몇 번 돌았는지는 Z 로 센다 */
    private val sub = listOf("O1000", "G01 W-1. F0.1", "M99")
    private val head = listOf("O1", "G97 S1000 M03", "G00 X40. Z0.")

    private fun zEnd(r: SimRun) = moves(r).last().tip.end.z

    @Test
    fun callForms() {
        assertEquals(-1.0, zEnd(ok(head + listOf("M98 P1000", "M30") + sub)), 1e-12)
        // S 4-131: P 앞 4자리 = 반복 — P31000 = 3번, P1000 L2 = 2번, P30077 L2 = L 이 이김
        assertEquals(-3.0, zEnd(ok(head + listOf("M98 P31000", "M30") + sub)), 1e-12)
        assertEquals(-2.0, zEnd(ok(head + listOf("M98 P1000 L2", "M30") + sub)), 1e-12)
        assertEquals(-2.0, zEnd(ok(head + listOf("M98 P31000 L2", "M30") + sub)), 1e-12)
        // 4자리로 채워 찾음: P21 = O0021
        assertEquals(-1.0, zEnd(ok(head + listOf("M98 P21", "M30", "O0021", "G01 W-1. F0.1", "M99"))), 1e-12)
        // 부른 블록 다음으로 돌아온다
        val r = ok(head + listOf("M98 P1000", "G00 X60.", "M30") + sub)
        assertEquals("X60.000 Z-1.000", xz(moves(r).last().tip.end))
        // 부프로그램 줄의 단위 번호
        assertEquals(1, moves(r).first { it.line == 8 }.unit)
    }

    @Test
    fun nesting() {
        // 주 → 1 → 2 → 3 → 4 (4단) 는 되고 5단은 멈춤(S 4-131)
        fun chain(depth: Int): List<String> {
            val out = mutableListOf<String>()
            for (d in 1..depth) {
                out += "O${100 + d}"
                out += "G01 W-1. F0.1"
                if (d < depth) out += "M98 P${100 + d + 1}"
                out += "M99"
            }
            return out
        }
        assertEquals(-4.0, zEnd(ok(head + listOf("M98 P101", "M30") + chain(4))), 1e-12)
        stops(head + listOf("M98 P101", "M30") + chain(5), IssueCode.SUB_NEST)
    }

    /** M99 P n: 부른 쪽 N n 으로 — 앞으로 찾고 없으면 처음부터(S 4-132) */
    @Test
    fun returnToN() {
        val fwd = ok(head + listOf("M98 P1000", "G00 X50.", "N20 G00 X70.", "M30", "O1000", "G01 W-1. F0.1", "M99 P20"))
        assertTrue(moves(fwd).none { abs(it.tip.end.xDia - 50) < 1e-9 }) // X50 줄은 건너뜀
        assertEquals("X70.000 Z-1.000", xz(moves(fwd).last().tip.end))
        // 뒤에 없으면 처음부터 찾음 → 고리 → 실행 한도
        stops(listOf("O1", "G97 S1000 M03", "N10 G00 X40. Z0.", "M98 P1000", "M30", "O1000", "G01 W-1. F0.1", "M99 P10"), IssueCode.EXEC_LIMIT)
        stops(head + listOf("M98 P1000", "M30", "O1000", "M99 P77"), IssueCode.SUB_NOT_FOUND, 7)
    }

    @Test
    fun mainM99AndErrors() {
        val r = ok(head + listOf("G01 W-1. F0.1", "M99"))
        assertTrue(has(r, IssueCode.M99_MAIN))
        assertEquals(99, (r.steps.last() as EndStep).m)
        stops(head + listOf("M98 P2000", "M30") + sub, IssueCode.SUB_NOT_FOUND, 4)
        stops(head + listOf("M98", "M30"), IssueCode.WORD_MISSING, 4)
        stops(head + listOf("M98 P1000", "M30", "O1000", "G01 W-1. F0.1"), IssueCode.SUB_NO_M99, 7)
        stops(head + listOf("M98 P1000", "M30") + sub + sub, IssueCode.DUP_PROGRAM, 4)
        stops(head + listOf("M98 P1000 L0", "M30") + sub, IssueCode.BAD_VALUE, 4)
    }

    /** 보정 중 M98/M99 는 받는다(S 3-77 주 2) — 블록을 이어 붙인 것처럼, M98 · M99 블록은 움직임 없는 블록으로 세지 않는다 */
    @Test
    fun compAcrossSub() {
        val r = ok(
            listOf("O1", "T0101", "G97 S1000 M03", "G00 X36. Z1.", "G42 G01 X40. Z0. F0.1", "M98 P2000", "G40 G00 X60.", "M30", "O2000", "G01 Z-10.", "G01 X50.", "M99"),
        )
        for ((a, b) in moves(r).zipWithNext()) near(a.nose.end, b.nose.start)
        // 부프로그램의 Z-10 블록 끝: 다음(X50 올라감)과 안쪽 모서리 교점 → 인선이 꼭짓점 X40 Z-10
        assertEquals("X40.000 Z-10.000", xz(r.ghosts.last { it.line == 10 }.tip))
        assertEquals(setOf(1), r.compOffsets)
    }

    private fun abs(v: Double) = kotlin.math.abs(v)
}
