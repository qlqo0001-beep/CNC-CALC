package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.sim.MoveKind.FEED
import com.bmt.nosecalc.calc.sim.MoveKind.RAPID
import com.bmt.nosecalc.calc.sim.MoveKind.THREAD
import com.bmt.nosecalc.calc.sim.SimKit.PRE
import com.bmt.nosecalc.calc.sim.SimKit.has
import com.bmt.nosecalc.calc.sim.SimKit.moves
import com.bmt.nosecalc.calc.sim.SimKit.near
import com.bmt.nosecalc.calc.sim.SimKit.ok
import com.bmt.nosecalc.calc.sim.SimKit.stops
import com.bmt.nosecalc.calc.sim.SimKit.xz
import com.bmt.nosecalc.calc.sim.StepSrc.CYCLE_APPROACH
import com.bmt.nosecalc.calc.sim.StepSrc.CYCLE_CUT
import com.bmt.nosecalc.calc.sim.StepSrc.CYCLE_FINISH
import com.bmt.nosecalc.calc.sim.StepSrc.CYCLE_RETRACT
import com.bmt.nosecalc.calc.sim.StepSrc.CYCLE_RETURN
import com.bmt.nosecalc.calc.wrap2Pi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 고정 사이클(`docs/simulator-design.md` 1.6 · 12절). 출처: S = Siemens SINUMERIK ISO Turning 04/2007(FANUC 2줄 형식 · 체계 A),
 * H = Haas ST/TL 워크북 2015(인쇄 쪽 — inch 예제는 숫자를 그대로 mm 로 읽어 모양만 맞춘다).
 * 출처 글을 바꾼 곳은 그 자리에 적었다: 체계 B 의 G90(절대)는 뺌, Haas 1줄 사이클(D · I · K)은 FANUC 2줄로, Haas 의 `G28` · `G53 …` 줄은 `G28 U0. W0.` 로.
 */
class CycleTest {
    private fun at(r: SimRun, line: Int) = moves(r).filter { it.line == line }
    private fun ends(ms: List<MoveStep>) = ms.map { xz(it.tip.end) }
    private fun src(r: SimRun, s: StepSrc, line: Int) = moves(r).filter { it.src == s && it.line == line }

    private fun sameSeg(want: Seg, got: Seg, tol: Double = 1e-9, msg: String = "") {
        near(want.start, got.start, tol, "$msg 시작")
        near(want.end, got.end, tol, "$msg 끝")
        assertEquals(want is Arc, got is Arc, "$msg 종류")
        if (want is Arc && got is Arc) {
            near(want.center, got.center, tol, "$msg 중심")
            assertEquals(want.cw, got.cw)
        }
    }

    /** 점에서 토막까지 거리 */
    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            val u = if (l2 == 0.0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0)
            (s.start + d * u - p).length
        }
        is Arc -> {
            val dir = if (s.cw) -1.0 else 1.0
            val u = wrap2Pi(dir * (angleOf(p - s.center) - angleOf(s.start - s.center)))
            if (u <= s.sweepRad() + 1e-9) abs((p - s.center).length - s.radius)
            else minOf((p - s.start).length, (p - s.end).length)
        }
    }

    private fun onPath(p: Pt, path: List<Seg>, tol: Double = 1e-9) = path.any { dist(p, it) <= tol }

    // ================================================================ G90 · G92 · G94

    /** S 4-92 그림 4-2: G90 직선 세 번 — 네 이동(급송 X · 이송 · 이송 X · 급송 Z) × 3, X 만 바꿔 모달 반복(S 4-91) */
    @Test
    fun siemens92G90() {
        val r = ok(PRE + listOf("N10 G00 X94. Z62.", "N11 G90 X80. W-42. F0.3", "N12 X70.", "N13 X60.", "N14 G00", "M30"))
        assertEquals(listOf("X80.000 Z62.000", "X80.000 Z20.000", "X94.000 Z20.000", "X94.000 Z62.000"), ends(at(r, 5)))
        assertEquals(listOf("X70.000 Z62.000", "X70.000 Z20.000", "X94.000 Z20.000", "X94.000 Z62.000"), ends(at(r, 6)))
        assertEquals(listOf("X60.000 Z62.000", "X60.000 Z20.000", "X94.000 Z20.000", "X94.000 Z62.000"), ends(at(r, 7)))
        val p1 = at(r, 5)
        assertEquals(listOf(RAPID, FEED, FEED, RAPID), p1.map { it.kind })
        assertEquals(listOf(CYCLE_APPROACH, CYCLE_CUT, CYCLE_RETRACT, CYCLE_RETURN), p1.map { it.src })
        assertEquals(listOf(1, 2, 3), listOf(5, 6, 7).map { at(r, it).first().pass })
        assertTrue(moves(r).filter { it.line in 5..7 }.none { it.model })
        assertTrue(p1.filter { it.kind == FEED }.all { it.feed == 0.3 })
        // 사이클 이동에는 고스트가 없다(설계 2.3)
        assertTrue(r.ghosts.none { it.line in 5..7 })
    }

    /** S 4-93 그림 4-4: G90 테이퍼 R−10.5(B 에서 A′ 를 본 방향) — R 은 모달 */
    @Test
    fun siemens93G90Taper() {
        val r = ok(PRE + listOf("N20 G00 X87. Z72.", "N21 G90 X85. W-42. R-10.5 F0.25", "N22 X80.", "N23 X75.", "N24 X70.", "N25 G00", "M30"))
        assertEquals(listOf("X64.000 Z72.000", "X85.000 Z30.000", "X87.000 Z30.000", "X87.000 Z72.000"), ends(at(r, 5)))
        assertEquals(listOf("X49.000 Z72.000", "X70.000 Z30.000", "X87.000 Z30.000", "X87.000 Z72.000"), ends(at(r, 8)))
    }

    /** S 4-99 그림 4-12 G94 직선, S 4-100 그림 4-14 G94 테이퍼 R−5.29(A′ = B.z + R) — Z 만 바꿔 모달 반복 */
    @Test
    fun siemens99And100G94() {
        val r = ok(PRE + listOf("N60 G00 X65. Z42.", "N61 G94 X20. Z38. F0.35", "N62 Z34.", "N63 Z30.", "N64 G00", "M30"))
        assertEquals(listOf("X65.000 Z38.000", "X20.000 Z38.000", "X20.000 Z42.000", "X65.000 Z42.000"), ends(at(r, 5)))
        assertEquals(listOf("X65.000 Z30.000", "X20.000 Z30.000", "X20.000 Z42.000", "X65.000 Z42.000"), ends(at(r, 7)))
        assertEquals(listOf(RAPID, FEED, FEED, RAPID), at(r, 5).map { it.kind })
        // S 원문 `N70N G00 …` 의 N 하나는 오자
        val t = ok(PRE + listOf("N70 G00 X74. Z32.", "N71 G94 X20. Z30. R-5.29 F0.3", "N72 Z25.", "N73 Z20.", "N74 G00", "M30"))
        assertEquals(listOf("X74.000 Z24.710", "X20.000 Z30.000", "X20.000 Z32.000", "X74.000 Z32.000"), ends(at(t, 5)))
        assertEquals(listOf("X74.000 Z14.710", "X20.000 Z20.000", "X20.000 Z32.000", "X74.000 Z32.000"), ends(at(t, 7)))
    }

    /** S 4-95 그림 4-7(체계 B G78 → A G92, 끝 빼기 M 은 뺌): 나사 네 번, 나사 시간 = 길이 / (리드 × 회전수) */
    @Test
    fun siemens95G92() {
        val r = ok(PRE + listOf("N30 G00 X80. Z76.2", "N31 G92 X66.4 Z25.4 F6.", "N32 X65.", "N33 X63.8", "N34 X62.64", "N35 G00 X100. Z100.", "M30"))
        val p1 = at(r, 5)
        assertEquals(listOf("X66.400 Z76.200", "X66.400 Z25.400", "X80.000 Z25.400", "X80.000 Z76.200"), ends(p1))
        assertEquals(listOf(RAPID, THREAD, RAPID, RAPID), p1.map { it.kind })
        assertEquals(6.0, p1[1].feed)
        assertEquals(50.8 / (6.0 * 1000) * 60, p1[1].t1 - p1[1].t0, 1e-12)
        assertEquals(listOf("X62.640 Z25.400"), at(r, 8).filter { it.kind == THREAD }.map { xz(it.tip.end) })
        assertEquals(4, at(r, 8).first().pass)
    }

    /** S 4-97 그림 4-9: G92 테이퍼를 I 로(I = R) — I 도 모달 */
    @Test
    fun siemens97G92TaperI() {
        val r = ok(PRE + listOf("N50 G00 X80. Z80.8", "N51 G92 X70. W-50.8 I-1.5 F2.", "N52 X68.8", "N53 X67.8", "N54 G00 X100. Z100.", "M30"))
        assertEquals(listOf("X67.000 Z80.800", "X70.000 Z30.000", "X80.000 Z30.000", "X80.000 Z80.800"), ends(at(r, 5)))
        assertEquals(listOf("X64.800 Z80.800", "X67.800 Z30.000", "X80.000 Z30.000", "X80.000 Z80.800"), ends(at(r, 7)))
    }

    /** G92 끝 빼기(설정 1 리드): 끝 Z 앞 6 mm 에서 45° 로 — 모형. 빠지는 X 는 시작점 X 를 넘지 않는다 */
    @Test
    fun g92Chamfer() {
        val mc = MachineSetup(g92ChamferLeads = 1.0)
        val r = ok(PRE + listOf("G00 X80. Z76.2", "G92 X66.4 Z25.4 F6.", "G00 X100.", "M30"), machine = mc)
        val p = at(r, 5)
        assertEquals(listOf("X66.400 Z76.200", "X66.400 Z31.400", "X78.400 Z25.400", "X80.000 Z25.400", "X80.000 Z76.200"), ends(p))
        assertEquals(listOf(false, false, true, false, false), p.map { it.model })
        val big = ok(PRE + listOf("G00 X70. Z76.2", "G92 X66.4 Z25.4 F6.", "G00 X100.", "M30"), machine = mc)
        assertEquals(listOf("X66.400 Z76.200", "X66.400 Z31.400", "X70.000 Z25.400", "X70.000 Z76.200"), ends(at(big, 5)))
    }

    private val haas131 = listOf(
        "G28 U0. W0.", "T101", "G50 S3000", "G97 S480 M03", "G54 G00 X1.85 Z1. M08", "G96 S390", "Z0.1",
        "G90 G42 X1.65 Z-0.6495 I-0.375 F0.006", "X1.55", "X1.45", "X1.35", "X1.25", "G00 G40 X3.1 Z1. M09", "M05", "G28 U0. W0.", "M30",
    )

    /** H 128 O00131: G90 에 G42 → COMP_IN_CYCLE(H 73 — Haas 고유 보정 경로가 출처에 없음). G42 를 빼면 네 이동 × 5, I 를 R 로 */
    @Test
    fun haas128G90() {
        stops(haas131, IssueCode.COMP_IN_CYCLE, 8, tools = mapOf(1 to ToolOffset(0.031, 3)))
        val r = ok(haas131.map { it.replace("G90 G42", "G90") })
        assertEquals(20, moves(r).count { it.line in 8..12 })
        val p1 = at(r, 8)
        near(Pt.ofDia(0.1, 0.9), p1[0].tip.end)
        near(Pt.ofDia(-0.6495, 1.65), p1[1].tip.end)
        near(Pt.ofDia(-0.6495, 1.85), p1[2].tip.end)
        near(Pt.ofDia(0.1, 1.85), p1[3].tip.end)
        near(Pt.ofDia(0.1, 0.5), at(r, 12)[0].tip.end)
    }

    /**
     * H 130 O00133: G92 12 번(M23 은 S 4-93 이 사이클 블록의 M 을 오류로 적어 앞 줄로 옮김). M23 = 나사 끝 빼기 켬(H 111 — 설계 18.8)이라 '기계 M' 알림이 없고,
     * 설정 끝 빼기가 처음 0 이라 패스는 곧다(모르는 M 의 알림은 GBasicTest.mCodes · singleCycleRepeatsOnBlockWithoutMove 의 M41 · M71 이 본다)
     */
    @Test
    fun haas130G92() {
        val xs = listOf(".98", ".96", ".94", ".935", ".93", ".925", ".9225", ".92", ".9175", ".9155", ".915", ".9148")
        val text = listOf("G28 U0. W0.", "T404", "G97 S825 M03", "G54 G00 X1.1 Z1. M08", "Z0.25 M23", "G92 X.98 Z-1.05 F0.08333") +
            xs.drop(1).map { "X$it" } + listOf("G00 X1.1 Z1. M09", "M05", "G28 U0. W0.", "M30")
        val r = ok(text)
        val th = moves(r).filter { it.kind == THREAD }
        assertEquals(12, th.size)
        for ((m, x) in th.zip(xs)) {
            near(Pt.ofDia(0.25, x.toDouble()), m.tip.start, 1e-12, "X$x")
            near(Pt.ofDia(-1.05, x.toDouble()), m.tip.end, 1e-12, "X$x")
        }
        assertEquals((1..12).toList(), th.map { it.pass })
        assertTrue(!has(r, IssueCode.MACHINE_M))
    }

    /** H 132 O00135: G94 G41 → COMP_IN_CYCLE. G41 을 빼면 K 테이퍼(K = R) 여섯 번 */
    @Test
    fun haas132G94() {
        val text = listOf(
            "G28 U0. W0.", "T101", "G50 S3000", "G97 S480 M03", "G54 G00 X3.1 Z1. M08", "G96 S390", "Z.1",
            "G94 G41 X1.0 Z-0.3 K-0.5774 F0.01", "Z-0.4", "Z-0.5", "Z-0.6", "Z-0.69", "Z-0.7", "G40 G00 X3.1 Z1. M09", "M05", "G28 U0. W0.", "M30",
        )
        stops(text, IssueCode.COMP_IN_CYCLE, 8)
        val r = ok(text.map { it.replace("G94 G41", "G94") })
        assertEquals(24, moves(r).count { it.line in 8..13 })
        val p1 = at(r, 8)
        near(Pt.ofDia(-0.8774, 3.1), p1[0].tip.end)
        near(Pt.ofDia(-0.3, 1.0), p1[1].tip.end)
        near(Pt.ofDia(0.1, 1.0), p1[2].tip.end)
        near(Pt.ofDia(0.1, 3.1), p1[3].tip.end)
        near(Pt.ofDia(-0.7 - 0.5774, 3.1), at(r, 13)[0].tip.end)
    }

    @Test
    fun singleCycleErrors() {
        val head = PRE + listOf("G00 X87. Z72.", "G90 X85. W-42. R-10.5 F0.25")
        // S 4-93: 사이클 모드 블록에 축과 T · M 이 같이 = 오류, 모드를 끝낸 G00 블록에서는 된다
        stops(head + listOf("X80.", "X75. T0505 M05"), IssueCode.CYCLE_BLOCK_TM, 7)
        ok(head + listOf("X80.", "G00 X75. T0505 M05", "M30"))
        // 축 없이 M 만: S 4-93 은 보통 블록이라 하나 FANUC 은 사이클을 한 번 더 돌며 M 을 '함께'(FF 4.1.6 · F0 13.1 주 2(3)) —
        // 절삭유 · 기계 M 은 사이클 뒤 M, 순서가 결과를 바꾸는 주축 M 은 멈춤(singleCycleRepeatsOnBlockWithoutMove)
        assertEquals(2, moves(ok(head + listOf("M08", "X80.", "G00 X100.", "M30"))).filter { it.line == 6 }.mapNotNull { it.pass }.distinct().single())
        stops(head + listOf("M05", "X80.", "G00 X100.", "M30"), IssueCode.CYCLE_REPEAT_MST, 6)
        stops(head + "G80", IssueCode.G80_IN_CYCLE, 6)
        stops(head + "G28 U0.", IssueCode.CYCLE_ONESHOT, 6)
        stops(head + "G30 U0.", IssueCode.CYCLE_ONESHOT, 6)
        stops(PRE + listOf("G00 X87. Z72.", "G90 X85. F0.25"), IssueCode.WORD_MISSING, 5)
        stops(PRE + listOf("G00 X87. Z72.", "G90 X85. Z30. R1. I1. F0.25"), IssueCode.WORD_NOT_USED, 5)
        stops(PRE + listOf("G00 X87. Z72.", "G90 X85. Z30. ,C1. F0.25"), IssueCode.WORD_NOT_USED, 5)
        // 나사 리드 F 는 소수점(S 1-14), 주축은 G98 이어도 돌아야
        stops(PRE + listOf("G00 X80. Z76.2", "G92 X66.4 Z25.4 F6"), IssueCode.NO_POINT_FEED, 5)
        stops(listOf("T0101", "G97 S500", "G98", "G00 X80. Z76.2", "G92 X66.4 Z25.4 F6."), IssueCode.SPINDLE_STOPPED, 5)
        assertTrue(has(ok(listOf("T0101", "G50 S1500", "G96 S100 M03", "G00 X80. Z76.2", "G92 X66.4 Z25.4 F6.", "G00 X100.", "M30")), IssueCode.THREAD_IN_CSS))
        // G92 의 F 는 리드 — 모드를 떠나면 이송으로 남기지 않는다
        val g92 = PRE + listOf("G01 X80. Z76.2 F0.2", "G92 X66.4 Z25.4 F6.", "G00 X70. Z80.")
        stops(g92 + "G01 Z70.", IssueCode.FEED_MISSING, 7)
        ok(g92 + listOf("G01 Z70. F0.2", "M30"))
        // G90 의 F 는 이송 — 모드 뒤에도 남는다
        ok(head + listOf("G01 X87. Z80.", "M30"))
    }

    // ================================================================ G71 · G72 · G73 · G70

    private val siemens109 = listOf(
        "N010 G00 X200.0 Z220.0", "N011 X142.0 Z171.0", "N012 G71 U4.0 R1.0", "N013 G71 P014 Q020 U4.0 W2.0 F0.3 S550",
        "N014 G00 X40.0 F0.15 S700", "N015 G01 Z140.0", "N016 X60.0 Z110.0", "N017 Z90.0", "N018 X100.0 Z80.0", "N019 Z60.0",
        "N020 X140.0 Z40.0", "N021 G70 P014 Q020", "N022 G00 X200. Z220.", "M30",
    )

    /**
     * S 4-109 그림 4-22(N010 의 체계 B G90 은 뺌, N022 의 소수점 없는 `X200 Z220` 은 `X200. Z220.` 으로):
     * 층 반경 67 · 63 … 23(12층) + 마지막 층 22(= 20 + u/2), 층 끝 = 윤곽′ 교점(손 계산), 45° 후퇴 e, 여유 패스 = 윤곽 + (W2, 반경 2),
     * G70 = 윤곽(P~Q 의 F0.15 · S700 — S 4-108 주 1).
     */
    @Test
    fun siemens109G71G70() {
        val r = ok(PRE + siemens109)
        val cuts = src(r, CYCLE_CUT, 7)
        val wantX = listOf(134.0, 126.0, 118.0, 110.0, 102.0, 94.0, 86.0, 78.0, 70.0, 62.0, 54.0, 46.0, 44.0)
        val wantZ = listOf(47.0, 51.0, 55.0, 59.0, 82.5, 84.5, 86.5, 88.5, 90.5, 115.0, 127.0, 139.0, 142.0)
        assertEquals(13, cuts.size)
        for ((i, c) in cuts.withIndex()) {
            near(Pt.ofDia(171.0, wantX[i]), c.tip.start, 1e-9, "층 ${i + 1} 시작")
            near(Pt.ofDia(wantZ[i], wantX[i]), c.tip.end, 1e-9, "층 ${i + 1} 끝")
            assertEquals(i + 1, c.pass)
            assertTrue(c.model)
            assertEquals(0.3, c.feed)
            assertEquals(550.0, c.rpm0)
        }
        // 45° 로 e = 1 후퇴(반경 +1 · Z +1)
        val rets = src(r, CYCLE_RETRACT, 7)
        assertEquals(13, rets.size)
        for ((c, t) in cuts.zip(rets)) near(c.tip.end + Pt(1.0, 1.0), t.tip.end)
        // 층 절입은 P 블록(G00)대로 급송(H 80)
        assertTrue(src(r, CYCLE_APPROACH, 7).all { it.kind == RAPID })
        // 여유 패스 = 윤곽 + (W2, U4/2)
        val fin = src(r, CYCLE_FINISH, 7)
        assertEquals(
            listOf("X44.000 Z142.000", "X64.000 Z112.000", "X64.000 Z92.000", "X104.000 Z82.000", "X104.000 Z62.000", "X144.000 Z42.000"),
            ends(fin),
        )
        assertEquals("X44.000 Z173.000", xz(fin.first().tip.start))
        assertTrue(fin.none { it.model })
        assertEquals("X142.000 Z171.000", xz(at(r, 7).last().tip.end))
        // G71 바로 뒤가 P 블록이라 Q 다음(N021)으로 — P~Q 는 G70 에서 한 번만
        val pq = moves(r).filter { it.line in 8..14 }
        assertEquals(
            listOf("X40.000 Z171.000", "X40.000 Z140.000", "X60.000 Z110.000", "X60.000 Z90.000", "X100.000 Z80.000", "X100.000 Z60.000", "X140.000 Z40.000"),
            ends(pq),
        )
        assertTrue(pq.drop(1).all { it.feed == 0.15 && it.rpm0 == 700.0 })
        assertEquals("X142.000 Z171.000", xz(at(r, 15).last().tip.end))
        assertTrue(at(r, 15).all { it.kind == RAPID && it.src == CYCLE_RETURN })
        // 사이클 뒤 F · S 는 부르기 전 값(설계 1.6)
        val st = r.steps.filterIsInstance<StateStep>().last()
        assertEquals(1000.0, st.sCmd)
        assertNull(st.feed)
        // 고스트는 G70 의 P~Q 블록 끝마다, G71 층에는 없다
        assertEquals((8..14).toList(), r.ghosts.filter { it.line in 7..15 }.map { it.line })
    }

    private val siemens110 = listOf(
        "N010 G00 X220.0 Z190.0", "N011 G00 X162.0 Z132.0", "N012 G72 W7.0 R1.0", "N013 G72 P014 Q019 U4.0 W2.0 F0.3",
        "N014 G00 Z59.5 F0.15 S200", "N015 G01 X120.0 Z70.0", "N016 Z80.0", "N017 X80.0 Z90.0", "N018 Z110.0", "N019 X36.0 Z132.0",
        "N020 G70 P014 Q019", "N021 G00 X220.0 Z190.0", "M30",
    )

    /**
     * S 4-110 그림 4-23 G72(N010 의 G90 은 뺌): Z 층 125 · 118 … 69, 층 끝 X 손 계산. Z62 · Z61.5 층은 윤곽′ 이 시작점 X162 바깥(X164 · X166)이라
     * 깎을 것이 없어 빠진다. N021 은 원문에 G 가 없다 — 사이클 뒤라 CYCLE_MOTION_MISSING(설계 1.6), G00 을 넣으면 된다.
     */
    @Test
    fun siemens110G72() {
        val r = ok(PRE + siemens110)
        val cuts = src(r, CYCLE_CUT, 7)
        val wantZ = listOf(125.0, 118.0, 111.0, 104.0, 97.0, 90.0, 83.0, 76.0, 69.0)
        val wantX = listOf(58.0, 72.0, 84.0, 84.0, 84.0, 92.0, 120.0, 124.0, 136.0)
        assertEquals(9, cuts.size)
        for ((i, c) in cuts.withIndex()) {
            near(Pt.ofDia(wantZ[i], 162.0), c.tip.start, 1e-9, "층 ${i + 1} 시작")
            near(Pt.ofDia(wantZ[i], wantX[i]), c.tip.end, 1e-9, "층 ${i + 1} 끝")
        }
        val rets = src(r, CYCLE_RETRACT, 7)
        for ((c, t) in cuts.zip(rets)) near(c.tip.end + Pt(1.0, 1.0), t.tip.end)
        val fin = src(r, CYCLE_FINISH, 7)
        assertEquals("X166.000 Z61.500", xz(fin.first().tip.start))
        assertEquals("X40.000 Z134.000", xz(fin.last().tip.end))
        assertEquals("X162.000 Z132.000", xz(at(r, 7).last().tip.end))
        stops(PRE + siemens110.map { if (it.startsWith("N021")) "N021 X220.0 Z190.0" else it }, IssueCode.CYCLE_MOTION_MISSING, 15)
    }

    private val siemens111 = listOf(
        "N010 G00 X260.0 Z220.0", "N011 G00 X220.0 Z160.0", "N012 G73 U14.0 W14.0 R3", "N013 G73 P014 Q020 U4.0 W2.0 F0.3 S0180",
        "N014 G00 X80.0 Z120.0", "N015 G01 Z100.0 F0.15", "N017 X120 Z90.0", "N018 Z70", "N019 G02 X160.0 Z50.0 R20.0",
        "N020 G01 X180.0 Z40.0 F0.25", "N021 G70 P014 Q020", "N022 G00 X260.0 Z220.0", "M30",
    )

    /**
     * S 4-111 그림 4-24 G73(원문에 소수점 없는 `X120` · `Z70` 이 섞여 계산기식 설정으로): 패스마다 (Z, X 반경) 평행 이동 = (16, 16) · (9, 9) · (2, 2),
     * 패스 사이 급송 A + 다음 옮김(H 98), 끝은 A. F · S 는 G73 줄의 것(P~Q 의 F 는 무시).
     */
    @Test
    fun siemens111G73() {
        val r = ok(PRE + siemens111, machine = MachineSetup(decimal = DecimalMode.CALCULATOR))
        val cuts = src(r, CYCLE_CUT, 7)
        assertEquals(15, cuts.size)
        val base = listOf(Pt.ofDia(100.0, 80.0), Pt.ofDia(90.0, 120.0), Pt.ofDia(70.0, 120.0), Pt.ofDia(50.0, 160.0), Pt.ofDia(40.0, 180.0))
        val offs = listOf(16.0, 9.0, 2.0)
        val a = Pt.ofDia(160.0, 220.0)
        for (j in 0..2) {
            val o = Pt(offs[j], offs[j])
            val pj = cuts.filter { it.pass == j + 1 }
            assertEquals(5, pj.size)
            near(Pt.ofDia(120.0, 80.0) + o, pj.first().tip.start, 1e-9, "패스 ${j + 1} 시작")
            for ((m, b) in pj.zip(base)) near(b + o, m.tip.end, 1e-9, "패스 ${j + 1}")
            assertTrue(pj[3].tip is Arc)
            assertEquals(j < 2, pj.first().model)
            val back = moves(r).last { it.line == 7 && it.src == CYCLE_RETURN && it.pass == j + 1 }
            near(if (j < 2) a + Pt(offs[j + 1], offs[j + 1]) else a, back.tip.end, 1e-9, "패스 ${j + 1} 뒤")
        }
        assertTrue(cuts.filter { it.kind == FEED }.all { it.feed == 0.3 && it.rpm0 == 180.0 })
    }

    /**
     * S 4-116 그림 4-30 G76(1.6.6 손 계산): 깊이 1.8 · 2.546 · 3.118 · 3.48(= k − d 로 자름) · 3.68(다듬질 1번),
     * X 64.400 · 62.909 · 61.765 · 61.040 · 60.640. 끝 빼기 r = 10(1.0 리드 = 6 mm), 공구 각 60° 한쪽 날 진입(모형).
     */
    @Test
    fun siemens116G76() {
        val head = PRE + listOf("G00 X70. Z105.", "G76 P011060 Q100 R200", "G76 X60640 Z25000 P3680 Q1800 F6.0")
        val r = ok(head + listOf("G00 X100.", "M30"))
        val byPass = moves(r).filter { it.line == 6 && it.kind == THREAD }.groupBy { it.pass!! }
        assertEquals(5, byPass.size)
        val depth = listOf(1.8, 1.8 * sqrt(2.0), 1.8 * sqrt(3.0), 3.48, 3.68)
        val wantX = listOf(64.4, 62.909, 61.765, 61.04, 60.64)
        for (j in 0 until 5) {
            val main = byPass[j + 1]!!.first()
            assertEquals(68.0 - 2 * depth[j], main.tip.end.xDia, 1e-9)
            assertEquals(wantX[j], main.tip.end.xDia, 0.0005)
            assertEquals(31.0, main.tip.end.z, 1e-9)
            assertEquals(105.0 + (depth[j] - 1.8) * tan(PI / 6), main.tip.start.z, 1e-9)
            assertEquals("X70.000 Z25.000", xz(byPass[j + 1]!![1].tip.end))
        }
        assertTrue(at(r, 6).all { it.model })
        assertEquals(6.0, byPass[1]!!.first().feed)
        assertEquals("X70.000 Z105.000", xz(at(r, 6).last().tip.end))
        // 리드 F 는 사이클 뒤 이송으로 남지 않는다(앞의 F0.2 도 — FANUC 은 F 가 하나라 리드가 이송이 됨, 출처 없어 비움)
        stops(
            PRE + listOf("G00 X72. Z106.", "G01 X70. Z105. F0.2", "G76 P011060 Q100 R200", "G76 X60640 Z25000 P3680 Q1800 F6.0", "G01 Z110."),
            IssueCode.FEED_MISSING, 8,
        )
    }

    /** H 111 O00113 3/4-16: K.0383 → P38 · D.0122 → Q12(µm 정수로 가까이), Δdmin Q10 — 깊이 .012 · .022 · .032 · .038 · .038 */
    @Test
    fun haas111G76() {
        val r = ok(
            listOf(
                "G28 U0. W0.", "T606", "G97 S720 M03", "G54 G00 X0.85 Z1. M08", "Z0.2 M23", "G76 P010060 Q10 R0",
                "G76 X0.674 Z-1.25 P38 Q12 F0.0625", "M09", "G28 U0. W0.", "M30",
            ),
        )
        val th = moves(r).filter { it.line == 7 && it.kind == THREAD }
        val want = listOf(0.726, 0.706, 0.686, 0.674, 0.674)
        assertEquals(want.size, th.size)
        for ((m, x) in th.zip(want)) {
            assertEquals(x, m.tip.end.xDia, 1e-9)
            assertEquals(-1.25, m.tip.end.z, 1e-12)
        }
    }

    /** H 103~105 O00105 · O00106 · O00107 G74(K → Q µm, I → P µm, 되돌아감 e = 첫 줄 R — Haas 설정 22 자리에 0.05) */
    @Test
    fun haas103to105G74() {
        val head = listOf("G28 U0. W0.", "T404", "G97 S1150 M03")
        val one = ok(head + listOf("G54 G00 X1.5 Z0.1 M08", "G74 R0.05", "G74 Z-0.375 Q100 F0.005", "M09", "G28 U0. W0.", "M30"))
        val m = at(one, 6)
        val wantZ = listOf(0.0, 0.05, -0.1, -0.05, -0.2, -0.15, -0.3, -0.25, -0.375, 0.1)
        assertEquals(wantZ.size, m.size)
        for ((s, z) in m.zip(wantZ)) assertEquals(z, s.tip.end.z, 1e-12)
        assertTrue(m.all { abs(it.tip.end.xDia - 1.5) < 1e-12 })
        assertEquals(listOf(FEED, RAPID, FEED, RAPID, FEED, RAPID, FEED, RAPID, FEED, RAPID), m.map { it.kind })
        // 여러 자리: X1.75 → 1.35 → 1.25(마지막 간격만 짧다 — H 102), 자리마다 열 번 쪼기
        val multi = ok(head + listOf("G54 G00 X1.75 Z0.1 M08", "G74 R0.05", "G74 X1.25 Z-.375 P200 Q50 F.005", "M09", "G28 U0. W0.", "M30"))
        val cuts = src(multi, CYCLE_CUT, 6)
        assertEquals(30, cuts.size)
        val cols = cuts.groupBy { it.pass!! }
        assertEquals(listOf(1.75, 1.35, 1.25), cols.values.map { round(it.first().tip.end.xDia * 1000) / 1000.0 })
        assertTrue(cols.values.all { abs(it.last().tip.end.z + 0.375) < 1e-12 })
        assertEquals("X1.750 Z0.100", xz(at(multi, 6).last().tip.end))
        // 드릴: Z-.525 를 K.1 로 — 일곱 번(.1 … .6, .625)
        val drill = ok(head + listOf("G54 G00 X0. Z0.1 M08", "G74 R0.05", "G74 Z-.525 Q100 F0.006", "G00 Z1.0 M09", "G28 U0. W0.", "M30"))
        assertEquals(7, src(drill, CYCLE_CUT, 6).size)
        stops(head + listOf("G00 X1.5 Z0.1", "G74 Z-0.375 Q100 F0.005"), IssueCode.CYCLE_FIRST_LINE, 5)
        stops(head + listOf("G00 X1.5 Z0.1", "G74 R0.05", "G74 X1.0 P100 F0.005"), IssueCode.WORD_MISSING, 6)
    }

    /** H 107 · 108 O00109 · O00110 G75(I → P µm, K → Q µm): 바닥 도피 R ≠ 0 은 지원 안 함 */
    @Test
    fun haas107and108G75() {
        val head = listOf("G28 U0. W0.", "T505", "G97 S960 M03", "G54 G00 X2.1 Z0.1 M08", "Z-0.75", "G75 R0.05")
        val one = ok(head + listOf("G75 X1.75 P50 F0.005", "M09", "M30"))
        val m = at(one, 7)
        val wantX = listOf(2.0, 2.1, 1.9, 2.0, 1.8, 1.9, 1.75, 2.1)
        assertEquals(wantX.size, m.size)
        for ((s, x) in m.zip(wantX)) assertEquals(x, s.tip.end.xDia, 1e-12)
        assertTrue(m.all { abs(it.tip.end.z + 0.75) < 1e-12 })
        val multi = ok(head + listOf("G75 X1.75 Z-1.5 P50 Q200 F0.005", "M09", "M30"))
        val cuts = src(multi, CYCLE_CUT, 7)
        assertEquals(20, cuts.size)
        assertEquals(listOf(-0.75, -0.95, -1.15, -1.35, -1.5), cuts.groupBy { it.pass!! }.values.map { round(it.first().tip.end.z * 1000) / 1000.0 })
        assertEquals("X2.100 Z-0.750", xz(at(multi, 7).last().tip.end))
        stops(head + "G75 X1.75 Z-1.5 P50 Q200 R0.1 F0.005", IssueCode.G7X_RELIEF, 7)
    }

    private val haas84 = listOf(
        "G28 U0. W0.", "T101", "G50 S3000", "G97 S450 M03", "G54 G00 X3.1 Z0.1 M08", "G96 S370", "Z0.", "G01 X-0.062 F0.01",
        "G00 X3.1 Z0.1", "G71 U0.125 R0.05", "G71 P10 Q20 U0.01 W0.005 F0.012", "N10 G42 G00 X0.325", "G01 Z0. F0.01", "X0.425",
        "X.625 Z-0.1 F0.005", "Z-0.375", "X0.75", "G03 X1. Z-0.5 R0.125", "G01 Z-1.", "G02 X1.25 Z-1.125 R0.125", "G01 X1.825",
        "X2.125 Z-1.275", "Z-2.5", "X3.", "N20 G40 G00 X3.1", "G70 P10 Q20", "G97 S450 M09", "G28 U0. W0.", "M30",
    )

    /**
     * H 83 O00084(TNR .032 · 팁 3, Haas 1줄 `G71 … D0.125` → `G71 U0.125 R0.05`, e 는 Haas 설정 73 이라 0.05 로 정함):
     * 황삭 윤곽 = P~Q 를 G42 보정 모형으로 돌린 실제 인선 경로(설계 3.4) — 여유 패스 − (W, U/2) = G70 이 깎은 보정 경로,
     * 층 끝은 윤곽′ 위, 45° 후퇴 e. P 블록 G42 시작은 안쪽 모서리 교점(형식 A)이라 A′ = (Z0.1 − 2r, X0.325).
     */
    @Test
    fun haas83G71G70WithTnc() {
        val r = ok(haas84, mapOf(1 to ToolOffset(0.032, 3)))
        val off = Pt(0.005, 0.005)
        val fin = src(r, CYCLE_FINISH, 11)
        val g70 = moves(r).filter { it.line in 13..25 }
        assertEquals(g70.size, fin.size)
        for ((a, b) in fin.zip(g70)) {
            sameSeg(GCycles.shift(a.tip, off * -1.0), b.tip, 1e-9, "줄 ${b.line}")
            assertEquals(b.kind, a.kind)
        }
        near(Pt.ofDia(0.1 - 2 * 0.032, 0.325) + off, fin.first().tip.start)
        val radii = g70.map { it.tip }.filterIsInstance<Arc>().map { it.radius }
        assertEquals(2, radii.size)
        assertEquals(0.125 + 0.032, radii[0], 1e-9)
        assertEquals(0.125 - 0.032, radii[1], 1e-9)
        val cont2 = fin.map { it.tip }
        val cuts = src(r, CYCLE_CUT, 11)
        val want = (1..11).map { 3.1 - 0.25 * it } + 0.335
        assertEquals(want.size, cuts.size)
        for ((c, x) in cuts.zip(want)) {
            assertEquals(x, c.tip.end.xDia, 1e-9)
            assertEquals(0.1, c.tip.start.z, 1e-12)
            assertTrue(onPath(c.tip.end, cont2), "층 끝 ${xz(c.tip.end)} 이 윤곽′ 위가 아님")
        }
        for ((c, t) in cuts.zip(src(r, CYCLE_RETRACT, 11))) near(c.tip.end + Pt(0.05, 0.05), t.tip.end)
        assertTrue(cuts.all { it.feed == 0.012 })
        assertEquals(setOf(1), r.compOffsets)
        // G71 줄은 보정 중이 아님 — 그 앞에서 G42 를 걸면 COMP_IN_CYCLE(H 77)
        stops(haas84.map { if (it == "G00 X3.1 Z0.1") "G42 G00 X3.1 Z0.1" else it }, IssueCode.COMP_IN_CYCLE, 11, tools = mapOf(1 to ToolOffset(0.032, 3)))
    }

    /** H 87 O00088 내경 G71(U−, G41 · TNR .032 · 팁 2): 층이 X+ 로, 후퇴는 X− · Z+ 45°, 여유 패스 − 여유 = G70 */
    @Test
    fun haas87IdG71() {
        val text = listOf(
            "G28 U0. W0.", "T404", "G50 S3000", "G97 S1780 M03", "G54 G00 X0.9 Z0.1 M08", "G96 S420",
            "G71 U0.12 R0.05", "G71 P8 Q18 U-0.01 W0.005 F0.012", "N8 G41 G00 X2.83", "N9 G01 Z0. F0.02", "N10 X2.73 F0.005",
            "N11 G02 X2.63 Z-.05 R0.05", "N12 G01 Z-.725", "N13 G03 X2.43 Z-.825 R.1", "N14 G01 X2.25", "N15 G02 X1.25 Z-1.325 R0.5",
            "N16 G01 Z-3.25", "N17 G03 X.75 Z-3.5 R0.25", "N18 G01 G40 X0.7", "N19 G70 P8 Q18", "G97 S1780 M09", "G28 U0. W0.", "M30",
        )
        val r = ok(text, mapOf(4 to ToolOffset(0.032, 2)))
        val cuts = src(r, CYCLE_CUT, 8)
        val want = (1..7).map { 0.9 + 0.24 * it } + 2.82
        assertEquals(want.size, cuts.size)
        val fin = src(r, CYCLE_FINISH, 8)
        val cont2 = fin.map { it.tip }
        for ((c, x) in cuts.zip(want)) {
            assertEquals(x, c.tip.end.xDia, 1e-9)
            assertTrue(c.tip.end.z < c.tip.start.z)
            assertTrue(onPath(c.tip.end, cont2), "층 끝 ${xz(c.tip.end)}")
        }
        for ((c, t) in cuts.zip(src(r, CYCLE_RETRACT, 8))) near(c.tip.end + Pt(0.05, -0.05), t.tip.end)
        val g70 = moves(r).filter { it.line in 10..19 }
        assertEquals(g70.size, fin.size)
        for ((a, b) in fin.zip(g70)) sameSeg(GCycles.shift(a.tip, Pt(-0.005, 0.005)), b.tip, 1e-9, "줄 ${b.line}")
    }

    /** H 89 O00090 형식 II(P 블록에 X · Z) — 지원 안 함. 원문 `Z0-.25` 는 `Z-.25` 의 오자 */
    @Test
    fun haas89TypeII() {
        val text = listOf(
            "G28 U0. W0.", "T101", "G50 S3000", "G97 S746 M03", "G54 G00 X2.1 Z0.1", "G96 S380", "G71 U0.05 R0.05",
            "G71 P108 Q113 U0.02 W0.005 F0.01", "N108 G00 X1.75 Z0.1", "N109 G01 Z-.25 F0.006", "N110 X1. Z-1.", "N111 X1.5 Z-1.5",
            "N112 Z-2.25", "N113 G01 X2.1", "M30",
        )
        stops(text, IssueCode.G71_TYPE2, 9)
    }

    /** H 95 O00096 G72 + G41(TNR .031 · 팁 3, 한 공구로 G70 까지): 층은 Z 로, 깎기는 X 로만, 층 끝은 윤곽′ 위, 여유 패스 − 여유 = G70 */
    @Test
    fun haas95G72WithTnc() {
        val text = listOf(
            "G28 U0. W0.", "T101", "G50 S3000", "G97 S450 M03", "G54 G00 X3.1 Z0.1 M08", "G96 S370",
            "G72 W0.06 R0.05", "G72 P9 Q18 U0.01 W0.01 F0.012", "N9 G41 G00 Z-1.6", "N10 G01 X2. F0.008", "N11 X1.4 Z-0.9",
            "N12 X1.", "N13 Z-0.6", "N14 G03 X0.8 Z-0.5 R0.1", "N15 G01 Z-0.1", "N16 X0.6 Z0.", "N17 X-0.062", "N18 G40 G00 Z0.1",
            "G70 P9 Q18", "G97 S450 M09", "G28 U0. W0.", "M30",
        )
        val r = ok(text, mapOf(1 to ToolOffset(0.031, 3)))
        val cuts = src(r, CYCLE_CUT, 8)
        val fin = src(r, CYCLE_FINISH, 8)
        val cont2 = fin.map { it.tip }
        assertTrue(cuts.size >= 20)
        for ((i, c) in cuts.withIndex()) {
            assertEquals(c.tip.start.z, c.tip.end.z, 1e-12)
            assertTrue(c.tip.end.x < c.tip.start.x)
            assertEquals(1.55, c.tip.start.x, 1e-12)
            if (i < cuts.lastIndex) assertEquals(0.1 - 0.06 * (i + 1), c.tip.end.z, 1e-9)
            assertTrue(onPath(c.tip.end, cont2), "층 끝 ${xz(c.tip.end)}")
        }
        val g70 = moves(r).filter { it.line in 10..18 }
        assertEquals(g70.size, fin.size)
        for ((a, b) in fin.zip(g70)) sameSeg(GCycles.shift(a.tip, Pt(-0.01, -0.005)), b.tip, 1e-9, "줄 ${b.line}")
    }

    /**
     * H 99 O00101 G73(`G73 … I0.3 K0.15 D4` → `G73 U0.3 W0.15 R4`) + G42(TNR .0312 · 팁 3): 패스 네 개가 같은 윤곽을 (K, I)/3 간격으로
     * 평행 이동, 마지막 = 여유, 마지막 패스 − 여유 = G70 보정 경로. F 는 G73 줄의 것(P~Q 의 F 는 무시 — H 98).
     */
    @Test
    fun haas99G73() {
        val text = listOf(
            "G28 U0. W0.", "T101", "G50 S3000", "G97 S450 M03", "G54 G00 X3.1 Z.1 M08", "G96 S370",
            "G73 U0.3 W0.15 R4", "G73 P108 Q121 U.01 W.005 F.012", "N108 G42 G00 X0.325", "N109 G01 Z0. F0.01", "N110 X0.425",
            "N111 X.625 Z-0.1 F0.005", "N112 Z-0.375", "N113 X0.75", "N114 G03 X1. Z-0.5 R.125", "N115 G01 Z-1. F0.003",
            "N116 G02 X1.25 Z-1.125 R.125", "N117 G01 X1.825", "N118 X2.125 Z-1.275", "N119 Z-2.5 F0.008", "N120 X3.",
            "N121 G40 G00 X3.1", "N122 G70 P108 Q121", "N123 G97 S450 M09", "N125 M30",
        )
        val r = ok(text, mapOf(1 to ToolOffset(0.0312, 3)))
        val cuts = src(r, CYCLE_CUT, 8)
        val byPass = cuts.groupBy { it.pass!! }
        assertEquals(4, byPass.size)
        val last = byPass[4]!!
        for (j in 1..4) {
            val k = (4 - j) / 3.0
            val pj = byPass[j]!!
            assertEquals(last.size, pj.size)
            for ((a, b) in pj.zip(last)) sameSeg(GCycles.shift(b.tip, Pt(0.15 * k, 0.3 * k)), a.tip, 1e-9, "패스 $j")
        }
        val g70 = moves(r).filter { it.line in 10..22 }
        assertEquals(g70.size, last.size)
        for ((a, b) in last.zip(g70)) sameSeg(GCycles.shift(a.tip, Pt(-0.005, -0.005)), b.tip, 1e-9, "줄 ${b.line}")
        assertTrue(cuts.filter { it.kind == FEED }.all { it.feed == 0.012 })
        assertEquals("X3.100 Z0.100", xz(at(r, 8).last().tip.end))
    }

    /** 원본 09 의 G71 + `,R` · `,C` (GTextTest.original09 글 + Q 블록 · G70): 끼운 모서리도 윤곽′ 에 들어가고 G70 과 같다 */
    @Test
    fun original09CornersInPQ() {
        val text = listOf(
            "O1001", "N11 T0101 M08", "G50 S2000", "G96 S200 M03", "G00 X56. Z5.", "G71 U1.5 R0.5",
            "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X16.", "G01 Z0. F0.15", "G01 X20. Z-2.", "G01 Z-8.",
            "G02 X34. Z-15. R7.", "G01 Z-22. ,R1.", "G01 X44. ,R1.", "G01 Z-30. ,C1.", "G01 X54. ,R1.", "G01 Z-43. ,C1.",
            "N20 G01 X58.", "G70 P10 Q20", "G00 X100. Z50.", "M30",
        )
        val r = ok(text)
        val fin = src(r, CYCLE_FINISH, 7)
        val g70 = moves(r).filter { it.line in 9..18 }
        assertEquals(g70.size, fin.size)
        for ((a, b) in fin.zip(g70)) sameSeg(GCycles.shift(a.tip, Pt(-0.1, -0.2)), b.tip, 1e-9, "줄 ${b.line}")
        assertEquals(4, fin.count { it.tip is Arc }) // G02 R7 + `,R1.` 셋
        assertEquals(5, g70.count { it.src == StepSrc.CORNER })
        val cont2 = fin.map { it.tip }
        for (c in src(r, CYCLE_CUT, 7)) assertTrue(onPath(c.tip.end, cont2), "층 끝 ${xz(c.tip.end)}")
        // 층 깊이 1.5(반경) — 첫 층 X53
        assertEquals(53.0, src(r, CYCLE_CUT, 7).first().tip.end.xDia, 1e-9)
    }

    private val od = PRE + listOf(
        "G00 X52. Z2.", "G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X20.", "G01 Z-10. F0.1", "X40. Z-20.", "N20 X52.",
        "G70 P10 Q20", "G00 X100. Z50.", "M30",
    )

    private fun odWith(line: Int, text: String) = od.mapIndexed { i, s -> if (i + 1 == line) text else s }

    @Test
    fun roughCycleErrors() {
        ok(od)
        stops(od.filterIndexed { i, _ -> i + 1 != 5 }, IssueCode.CYCLE_FIRST_LINE, 5)
        stops(odWith(6, "G71 P11 Q20 U0.4 W0.1 F0.25"), IssueCode.CYCLE_PQ_NOT_FOUND, 6)
        stops(odWith(9, "N10 X40. Z-20."), IssueCode.CYCLE_PQ_DUP, 6)
        stops(odWith(6, "G71 P20 Q10 U0.4 W0.1 F0.25"), IssueCode.CYCLE_PQ_ORDER, 6)
        stops(odWith(6, "G71 P10 Q10 U0.4 W0.1 F0.25"), IssueCode.CYCLE_PQ_ORDER, 6)
        // P 블록(S 4-103): G00/G01 + X 만
        stops(odWith(7, "N10 G00 Z0."), IssueCode.G7X_P_BLOCK, 7)
        stops(odWith(7, "N10 X20."), IssueCode.G7X_P_BLOCK, 7)
        stops(odWith(7, "N10 G00 X20. Z2."), IssueCode.G71_TYPE2, 7)
        // 단조(S 4-103): X 가 되돌아감 · Z 가 되돌아감 · 시작점 쪽으로 돌아오지 않음 — 그 블록 줄
        val notX = stops(odWith(9, "X40. Z-20.") .let { it.subList(0, 9) + "X35. Z-25." + it.subList(9, it.size) }, IssueCode.G71_NOT_MONOTONE, 10)
        assertEquals(listOf("X"), notX.stop!!.args)
        val notZ = stops(odWith(9, "X40. Z-5."), IssueCode.G71_NOT_MONOTONE, 9)
        assertEquals(listOf("Z"), notZ.stop!!.args)
        stops(odWith(10, "N20 X10."), IssueCode.G71_NOT_MONOTONE, 10)
        // 여유 부호(S 4-103 그림 4-16): 외경 −Z 깎기는 U+ · W+
        assertEquals(listOf("U"), stops(odWith(6, "G71 P10 Q20 U-0.4 W0.1 F0.25"), IssueCode.G7X_SIGN, 6).stop!!.args)
        assertEquals(listOf("W"), stops(odWith(6, "G71 P10 Q20 U0.4 W-0.1 F0.25"), IssueCode.G7X_SIGN, 6).stop!!.args)
        // P~Q 안 금지(S 4-117 주 2 · 3) · G71~G73 은 G96~G99 도(우리가 정함)
        stops(odWith(8, "G28 U0."), IssueCode.CYCLE_PQ_FORBIDDEN, 8)
        stops(odWith(8, "G90 X30. Z-10."), IssueCode.CYCLE_PQ_FORBIDDEN, 8)
        stops(odWith(8, "G21 G01 Z-10."), IssueCode.CYCLE_PQ_FORBIDDEN, 8)
        stops(odWith(8, "G96 G01 Z-10. S200"), IssueCode.CYCLE_PQ_FORBIDDEN, 8)
        stops(odWith(8, "G01 Z-10. F0.1 M30"), IssueCode.CYCLE_PQ_FORBIDDEN, 8)
        // P~Q 의 M 은 G71 황삭 동안 무시하고 G70 에서 실행(FF 4.2.1 주 3: M 은 F · S · T 와 같이)
        val m08 = ok(odWith(8, "G01 Z-10. F0.1 M08"))
        val on = m08.steps.indexOfFirst { it is StateStep && it.coolant }
        assertEquals(8, m08.steps[on].line)
        assertTrue(on > m08.steps.indexOfLast { it is MoveStep && it.line == 6 }, "M08 이 G71 황삭 뒤(G70 에서)")
        stops(odWith(8, "M98 P1000"), IssueCode.M98_IN_PQ, 8)
        // G70 은 P~Q 를 보통 블록처럼 — M08 은 된다
        ok(PRE + listOf("G00 X52. Z2.", "G70 P10 Q20", "N10 G00 X20.", "G01 Z-10. F0.1 M08", "N20 X52.", "M30"))
        // 마지막 이동을 ,C ,R 로 끝내면 오류(S 4-117 주 4)
        stops(odWith(10, "N20 X52. ,C1."), IssueCode.CORNER_NO_NEXT, 10)
        // P~Q 가 보정을 끄지 않음
        stops(odWith(7, "N10 G42 G00 X20."), IssueCode.CYCLE_COMP_OPEN, 10)
        stops(PRE + listOf("G00 X52. Z2.", "G70 P10 Q20", "N10 G42 G00 X20.", "G01 Z-10. F0.1", "N20 X52.", "M30"), IssueCode.CYCLE_COMP_OPEN, 8)
        // 보정 중에 부름(H 77) · 사이클 줄의 T(S 2-24) · M · 쓰지 않는 주소
        stops(odWith(4, "G42 G00 X52. Z2."), IssueCode.COMP_IN_CYCLE, 6)
        stops(odWith(6, "G71 P10 Q20 U0.4 W0.1 F0.25 T0101"), IssueCode.T_WITH_FEED, 6)
        stops(odWith(11, "G70 P10 Q20 M08"), IssueCode.CYCLE_BLOCK_TM, 11)
        stops(odWith(11, "G70 P10 Q20 F0.1"), IssueCode.WORD_NOT_USED, 11)
        stops(odWith(5, "G71 U2. R0.5 F0.2"), IssueCode.WORD_NOT_USED, 5)
        stops(odWith(5, "G71 U-2. R0.5"), IssueCode.BAD_VALUE, 5)
        // 사이클 뒤 01 그룹 모달은 비운다(설계 1.6)
        stops(odWith(12, "X100. Z50."), IssueCode.CYCLE_MOTION_MISSING, 12)
        // G73 패스 수 < 2
        stops(PRE + listOf("G00 X52. Z2.", "G73 U2. W2. R1"), IssueCode.G73_COUNT, 5)
    }

    /** 첫 줄 값은 모달(S 4-102 'This value is modal') — 두 번째 G71 은 첫 줄 없이 같은 Δd · e */
    @Test
    fun firstLineIsModal() {
        val r = ok(
            od.dropLast(1) + listOf(
                "G00 X62. Z2.", "G71 P30 Q40 U0.4 W0.1 F0.25", "N30 G00 X30.", "G01 Z-10. F0.1", "N40 X62.", "G00 X100. Z50.", "M30",
            ),
        )
        val cuts = src(r, CYCLE_CUT, 14)
        assertEquals(58.0, cuts.first().tip.end.xDia, 1e-9)
        assertTrue(cuts.size >= 7)
    }

    /** 사이클 동안은 G71 줄의 F · S(P~Q 의 F · S 는 무시 — S 4-103), 사이클 뒤는 부르기 전 값(설계 1.6) */
    @Test
    fun feedAndSpeedAroundCycle() {
        val r = ok(
            PRE + listOf(
                "G00 X52. Z2.", "G01 Z1. F0.2", "G00 X52. Z2.", "G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25 S600",
                "N10 G00 X20.", "G01 Z-10. F0.1 S900", "N20 X52.", "G01 X60. Z-10.", "M30",
            ),
        )
        assertTrue(src(r, CYCLE_CUT, 8).all { it.feed == 0.25 && it.rpm0 == 600.0 })
        val after = at(r, 12).single()
        assertEquals(0.2, after.feed)
        assertEquals(1000.0, after.rpm0)
    }

    /**
     * 다음 블록 — G70: G70 블록 다음(FF 4.2.4 '- Operations' · F0 13.2.4 주 2 · H 82 'returns to the block following the G70 call') —
     * P~Q 가 바로 뒤에 있으면 그 블록들을 보통 블록으로 한 번 더 실행한다. G71~G73: Q 블록 다음(FF 4.2.1 '- Operations'),
     * P~Q 가 사이클 블록 앞이면 되돌아와 되풀이하므로 CYCLE_PQ_BEFORE.
     */
    @Test
    fun nextBlockAfterCycle() {
        val r = ok(PRE + listOf("G00 X52. Z2.", "G70 P10 Q20", "N10 G00 X20.", "G01 Z-10. F0.1", "N20 X52.", "G00 X100.", "M30"))
        assertEquals(2, at(r, 7).size) // G70 이 한 번 · 이어서 보통 블록으로 한 번
        assertEquals(1, at(r, 9).size)
        val r2 = ok(PRE + listOf("G00 X52. Z2.", "N10 G00 X20.", "G01 Z-10. F0.1", "N20 X52.", "G00 X52. Z2.", "G70 P10 Q20", "G00 X100.", "M30"))
        assertEquals(2, at(r2, 6).size)
        // G71: P~Q 가 바로 뒤가 아니어도 Q 다음 — 7 · 8줄은 건너뜀
        val g71 = PRE + listOf("G00 X62. Z2.", "G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25")
        val r3 = ok(g71 + listOf("G00 X100. Z50.", "M30", "N10 G00 X40.", "G01 Z-20.", "N20 X62.", "G00 X150. Z80.", "M30"))
        assertEquals(listOf(12), moves(r3).filter { it.line > 6 }.map { it.line }.distinct())
        // G71: P~Q 가 앞 — FANUC 은 Q 다음으로 되돌아가 되풀이 → 멈춤
        stops(
            PRE + listOf("G00 X62. Z2.", "N10 G00 X40.", "G01 Z-20. F0.2", "N20 X62.", "G00 X62. Z2.", "G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25", "M30"),
            IssueCode.CYCLE_PQ_BEFORE, 10,
        )
    }

    /**
     * FF 4.1.6 '- Block in which no move command is specified' · F0 13.1 주 2(1): G90/G92/G94 모드에서 이동 없는 블록(EOB 만 · 주석만 · N 만 ·
     * F 만 · R 만)도 같은 사이클을 한 번 더 — F · R 은 새 값으로. 모달 값이 없으면(모드에 막 들어온 `G90 F0.2`) 사이클 없음(FF 4.1.6).
     * 축 없는 S · T · 주축 M(M03~M05) · 끝(M02 · M30) · M98/M99 는 사이클과 '함께'(F0 주 2(3)) 라 순서가 결과를 바꿔 멈춤 — 그 밖의 M(기계 M ·
     * M00 · M01 · M08 · M09)은 사이클을 한 번 더 돈 뒤 M(설계 13.1 · 18). 모드를 잇는 G18 은 모달 값을 지워(FF 4.1.6) 멈춤.
     */
    @Test
    fun singleCycleRepeatsOnBlockWithoutMove() {
        val head = PRE + listOf("G00 X60. Z2.", "G90 X50. Z-20. F0.2")
        for (x in listOf(";", "(MEMO)", "N100", "/", "  ;  ")) {
            val r = ok(head + listOf(x, "G00 X100.", "M30"))
            val m6 = at(r, 6)
            assertEquals(4, m6.size, "'$x'")
            assertEquals(2, m6[0].pass, "'$x'")
            near(Pt.ofDia(-20.0, 50.0), m6[1].tip.end, msg = "'$x'")
        }
        // F 만: 새 이송으로
        assertEquals(0.15, at(ok(head + listOf("F0.15", "G00 X100.", "M30")), 6)[1].feed)
        // R 만: 새 테이퍼로 — 급송 X 가 50 + 2R
        near(Pt.ofDia(2.0, 48.0), at(ok(head + listOf("R-1.", "G00 X100.", "M30")), 6)[0].tip.end)
        // 모달 값 없이 모드에 들어온 줄은 사이클 없음 — 뒤 주석 줄도
        val e = ok(PRE + listOf("G00 X60. Z2.", "G90 F0.2", "(MEMO)", "X50. Z-20.", "G00 X100.", "M30"))
        assertTrue(at(e, 5).isEmpty() && at(e, 6).isEmpty())
        assertEquals(4, at(e, 7).size)
        // 한 줄에 `;` 둘 = 블록 둘 — 두 번째(EOB 만)도 사이클
        assertEquals(8, at(ok(PRE + listOf("G00 X60. Z2.", "G90 X50. Z-20. F0.2;;", "G00 X100.", "M30")), 5).size)
        for (x in listOf("M03", "M05", "M30", "M98 P10", "S800", "T0101", "G97 S800", "G99")) stops(head + listOf(x, "G00 X100.", "M30"), IssueCode.CYCLE_REPEAT_MST, 6)
        // 기계 M · M01 · M08 · M09: 같은 사이클(마지막 X)을 한 번 더 돈 뒤 M 을 실행
        for (x in listOf("M71", "M01", "M08")) {
            val r = ok(head + listOf(x, "G00 X100.", "M30"))
            val m6 = at(r, 6)
            assertEquals(4, m6.size, x)
            assertEquals(2, m6[0].pass, x)
            near(Pt.ofDia(-20.0, 50.0), m6[1].tip.end, msg = x)
            val after = r.steps.subList(r.steps.indexOf(m6.last()) + 1, r.steps.size).filter { it.line == 6 }
            when (x) {
                "M71" -> assertTrue(r.issues.any { it.code == IssueCode.MACHINE_M && it.line == 6 }, x)
                "M01" -> assertTrue(after.single() is StopStep, x)
                else -> assertTrue((after.single() as StateStep).coolant, x)
            }
        }
        // M08 · M09 이어서: 줄마다 사이클을 한 번 더(pass 2 · 3) 돈 뒤 절삭유 켬 · 끔
        val cool = ok(head + listOf("M08", "M09", "G00 X100.", "M30"))
        assertEquals(listOf(2, 3), listOf(6, 7).map { at(cool, it)[0].pass })
        assertEquals(listOf(true, false), cool.steps.filterIsInstance<StateStep>().filter { it.line in 6..7 }.map { it.coolant })
        // 나사: G92 패스들 뒤 기계 M 만 쓴 줄 → 마지막 패스를 한 번 더(같은 X) 돈 뒤 M, G00 으로 모드를 끝냄
        val th = ok(PRE + listOf("G00 X10. Z5.", "M71", "G92 X5.76 Z-25. F1.27", "X5.59", "M72", "G00 Z200.", "M30"))
        assertEquals(listOf(1, 2, 3), moves(th).filter { it.kind == THREAD }.mapNotNull { it.pass })
        assertEquals(listOf(5.76, 5.59, 5.59), moves(th).filter { it.kind == THREAD }.map { round(it.tip.start.xDia * 1000) / 1000 })
        stops(head + listOf("G18", "G00 X100.", "M30"), IssueCode.CYCLE_ONESHOT, 6)
        stops(head + listOf("G18 X48.", "G00 X100.", "M30"), IssueCode.CYCLE_ONESHOT, 6)
        ok(PRE + listOf("G00 X60. Z2.", "G18 G90 X50. Z-20. F0.2", "G00 X100.", "M30"))
    }

    /** FF 4.2.4 '- Return to the cycle start point' 주: G70 의 시작점 복귀는 매개변수 LRP(급송 직선)와 상관없이 늘 축마다 따로 */
    @Test
    fun g70ReturnIsAlwaysDogleg() {
        val text = PRE + listOf("F0.2", "G00 X62. Z2.", "G70 P10 Q20", "G00 X100. Z10.", "M30", "N10 G00 X40.", "G01 Z-20.", "N20 X70. Z-25.")
        for (rp in RapidPath.values()) {
            val r = ok(text, machine = MachineSetup(rapidPath = rp))
            val back = src(r, CYCLE_RETURN, 6)
            assertEquals(2, back.size, "$rp")
            // Z 27 mm(24 m/min) · X 반경 4 mm(20 m/min): X 가 먼저 끝나 Z −25 + 24·4/20 = −20.2 에서 꺾임
            near(Pt(-20.2, 31.0), back[0].tip.end)
            near(Pt(2.0, 31.0), back[1].tip.end)
            // 사이클 밖 급송은 설정대로
            assertEquals(if (rp == RapidPath.LINEAR) 1 else 2, at(r, 7).size, "$rp")
        }
    }

    /** FF 4.2.3 주 · 그림 4.2.3(b) O0001 `G73U6.0W6.0R3.0`: d 는 소수점을 받아 반올림한 정수(2.6 → 3) */
    @Test
    fun g73CountAcceptsDecimal() {
        for (rw in listOf("R3.0", "R3", "R2.6")) {
            val r = ok(PRE + listOf("G00 X100. Z0.", "G73 U6. W6. $rw", "G73 P10 Q30 U6. W3. F1.", "N10 G00 X60. W0.", "G01 Z-10.", "N30 X100.", "M30"))
            assertEquals(listOf(1, 2, 3), at(r, 6).mapNotNull { it.pass }.distinct(), rw)
        }
        stops(PRE + listOf("G00 X100. Z0.", "G73 U6. W6. R1.4"), IssueCode.G73_COUNT, 5)
    }
}
