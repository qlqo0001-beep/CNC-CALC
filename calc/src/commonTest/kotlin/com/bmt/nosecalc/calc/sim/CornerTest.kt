package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.SimKit.PRE
import com.bmt.nosecalc.calc.sim.SimKit.moves
import com.bmt.nosecalc.calc.sim.SimKit.near
import com.bmt.nosecalc.calc.sim.SimKit.ok
import com.bmt.nosecalc.calc.sim.SimKit.stops
import com.bmt.nosecalc.calc.sim.SimKit.xz
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 모서리 `,C` · `,R`(`docs/simulator-design.md` 1.7 — S 2-31~2-32, HG) */
class CornerTest {
    private val head = PRE + listOf("G00 X20. Z1.", "G01 Z0. F0.1")

    /** 직선–직선 `,C2.`: 꼭짓점 (Z-10, X20) 에서 두 선을 따라 2 → (Z-8, X20) · (Z-10, X24) */
    @Test
    fun chamferLineLine() {
        val r = ok(head + listOf("G01 Z-10. ,C2.", "G01 X40.", "M30"))
        val f = moves(r).filter { it.kind == MoveKind.FEED }
        assertEquals(4, f.size)
        near(Pt.ofDia(-8.0, 20.0), f[1].tip.end)
        assertEquals(StepSrc.CORNER, f[2].src)
        near(Pt.ofDia(-10.0, 24.0), f[2].tip.end)
        near(Pt.ofDia(-10.0, 40.0), f[3].tip.end)
        assertEquals(6, f[2].line) // 모서리 토막은 ,C 를 쓴 블록 줄
        // 고스트: 면취 시작(측정점) · 끝 · 블록 끝
        assertEquals(listOf(Pt.ofDia(-8.0, 20.0), Pt.ofDia(-10.0, 24.0)), r.ghosts.filter { it.line == 6 }.map { it.cmd }.takeLast(2))
    }

    /** 직선–직선 `,R2.`: 오목 모서리 원호 중심 (Z-8, 반경 12), G02(시계) */
    @Test
    fun roundLineLine() {
        val r = ok(head + listOf("G01 Z-10. ,R2.", "G01 X40.", "M30"))
        val arc = moves(r).map { it.tip }.filterIsInstance<Arc>().single()
        near(Pt(-8.0, 12.0), arc.center)
        assertTrue(arc.cw)
        assertEquals(90.0, arc.sweepDeg, 1e-9)
        near(Pt(-8.0, 10.0), arc.start)
        near(Pt(-10.0, 12.0), arc.end)
    }

    /** `,R` 이웃이 원호 — 두 요소에 접해야(1e-9) */
    @Test
    fun roundLineArc() {
        // Z-5 까지 직선 → 볼록 원호 G03 R10 (끝 X40 Z-15). 꼭짓점에 ,R1.
        val r = ok(head + listOf("G01 Z-5. ,R1.", "G03 X40. Z-15. R10.", "G01 Z-25.", "M30"))
        val tips = moves(r).filter { it.kind == MoveKind.FEED }.map { it.tip }
        val fil = tips.filterIsInstance<Arc>().first { abs(it.radius - 1) < 1e-9 }
        val big = tips.filterIsInstance<Arc>().first { abs(it.radius - 1) > 1e-6 }
        // 직선 x = 10 에서 1 떨어진 중심, 큰 원과 접함
        assertEquals(1.0, abs(fil.center.x - 10.0), 1e-9)
        val d = (fil.center - big.center).length
        assertTrue(abs(d - (big.radius - 1)) < 1e-9 || abs(d - (big.radius + 1)) < 1e-9, "중심 거리 $d")
        near(fil.end, big.start)
    }

    @Test
    fun cornerErrors() {
        // ,C 의 이웃이 원호
        stops(head + listOf("G01 Z-5. ,C1.", "G03 X40. Z-15. R10."), IssueCode.CORNER_C_ARC, 6)
        // 너무 큼: 앞 요소가 5 인데 ,C5.
        stops(head + listOf("G01 Z-5. ,C5.", "G01 X40."), IssueCode.CORNER_TOO_BIG, 6)
        stops(head + listOf("G01 Z-5. ,R30.", "G01 X40."), IssueCode.CORNER_TOO_BIG, 6)
        // 꺾이지 않음(한 직선)
        stops(head + listOf("G01 Z-5. ,C1.", "G01 Z-10."), IssueCode.CORNER_NO_VERTEX, 6)
        // 다음이 이동 블록이 아님 · 글 끝 · G00 · G04
        stops(head + listOf("G01 Z-5. ,C1.", "M08", "G01 X40."), IssueCode.CORNER_NO_NEXT, 6)
        stops(head + listOf("G01 Z-5. ,C1.", "M30"), IssueCode.CORNER_NO_NEXT, 6)
        stops(head + listOf("G01 Z-5. ,C1."), IssueCode.CORNER_NO_NEXT, 6)
        stops(head + listOf("G01 Z-5. ,C1.", "G00 X40."), IssueCode.CORNER_NO_NEXT, 6)
        stops(head + listOf("G01 Z-5. ,C1.", "G04 X1.", "G01 X40."), IssueCode.CORNER_NO_NEXT, 6)
        stops(head + listOf("G01 Z-5. ,C0.", "G01 X40."), IssueCode.BAD_VALUE, 6)
        // 멈추면 모서리 블록은 그리지 않는다
        val r = stops(head + listOf("G01 Z-5. ,C1.", "M08"), IssueCode.CORNER_NO_NEXT)
        assertTrue(moves(r).none { it.line == 6 })
    }

    /** 원본 09.jpg 의 모서리 줄을 이어 쓴 것 — ,R → ,R → ,C → ,R → ,C 가 이어져도 된다(앞 모서리가 줄인 요소에 다음 모서리) */
    @Test
    fun chainedCorners09() {
        val r = ok(
            PRE + listOf(
                "G00 X16. Z1.", "G01 Z0. F0.15", "G01 X20. Z-2.", "G01 Z-8.", "G02 X34. Z-15. R7.",
                "G01 Z-22. ,R1.", "G01 X44. ,R1.", "G01 Z-30. ,C1.", "G01 X54. ,R1.", "G01 Z-43. ,C1.", "G01 X60.", "M30",
            ),
        )
        val f = moves(r).filter { it.kind == MoveKind.FEED }
        // 끊김 없이 이어진다
        for ((a, b) in f.zipWithNext()) near(a.tip.end, b.tip.start)
        assertEquals(5, f.count { it.src == StepSrc.CORNER })
        // 면취 시작 측정점(10.jpg): Z-29 X44
        assertEquals("X44.000 Z-29.000", xz(r.ghosts.filter { it.line == 11 }[0].cmd))
    }

    /**
     * 원본 10.jpg: 09 프로그램의 `G01 X44. ,R1.` → `G01 Z-30. ,C1.` → `G01 X54. ,R1.` 을 0.4R · 팁 3 · G42 로 —
     * 면취 시작 고스트의 보정 좌표 X44.000 Z-29.234(= Z −29 − r(1 − tan 22.5°)).
     */
    @Test
    fun original10() {
        val r = ok(
            PRE + listOf(
                "G00 X16. Z1.", "G42 G01 Z0. F0.15", "G01 X20. Z-2.", "G01 Z-8.", "G02 X34. Z-15. R7.",
                "G01 Z-22. ,R1.", "G01 X44. ,R1.", "G01 Z-30. ,C1.", "G01 X54. ,R1.", "G01 Z-43. ,C1.", "G01 X60.", "G40 G00 X62.", "M30",
            ),
        )
        val g = r.ghosts.filter { it.line == 11 }[0] // 면취 시작(줄인 Z-30 블록의 끝)
        assertEquals("X44.000 Z-29.000", xz(g.cmd))
        assertEquals("X44.000 Z-29.234", xz(g.tip))
        assertEquals(0.4, g.r)
        assertEquals(3, g.tipNo)
        // 보정 경로도 끊김 없이(노즈 중심)
        val mv = moves(r)
        for ((a, b) in mv.zipWithNext()) near(a.nose.end, b.nose.start, 1e-9)
        assertIs<Line>(mv.last().tip)
    }
}
