package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.SimKit.PRE
import com.bmt.nosecalc.calc.sim.SimKit.has
import com.bmt.nosecalc.calc.sim.SimKit.moves
import com.bmt.nosecalc.calc.sim.SimKit.near
import com.bmt.nosecalc.calc.sim.SimKit.ok
import com.bmt.nosecalc.calc.sim.SimKit.stops
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 모달 · 기본 이동 · T · M · 오류(`docs/simulator-design.md` 1.3 · 1.4 · 1.9). 출처 H = Haas 워크북(인쇄 쪽), S = Siemens ISO Turning */
class GBasicTest {
    /** H 32: A → B 를 네 가지로(절대 · 증분 · 섞음). A = X4. Z0.1 (U-1.8 W-3.1 로 B = X2.2 Z-3.) */
    @Test
    fun haas32RapidForms() {
        for (form in listOf("G00 X2.2 Z-3.0", "G00 U-1.8 W-3.1", "G00 X2.2 W-3.1", "G00 U-1.8 Z-3.0")) {
            val r = ok(listOf("G00 X4. Z0.1", form, "M30"))
            near(Pt.ofDia(-3.0, 2.2), moves(r).last().tip.end, 1e-9, form)
            assertTrue(moves(r).all { it.kind == MoveKind.RAPID })
        }
    }

    /**
     * H 33: G01 절대 · 섞음 두 열(같은 점). 증분 열의 둘째 줄 `W-1.0` 은 Z-1. → Z-3. 이라 W-2.0 이어야 한다(워크북 오자) — 고친 값으로.
     * 워크북 글 `X0. 9106` 의 빈칸 · `W-1.1.` 의 점 둘도 고쳤다.
     */
    @Test
    fun haas33LinearForms() {
        val cols = listOf(
            listOf("G00 X0.9106 Z0.1", "G01 X1.5 Z-1. F0.006", "Z-3", "X2."),
            listOf("G00 X0.9106 Z0.1", "G01 U.5894 W-1.1 F0.006", "W-2.0", "U0.5"),
            listOf("G00 X0.9106 Z0.1", "G01 X1.5 W-1.1 F0.006", "Z-3", "U0.5"),
        )
        for (c in cols) {
            val r = SimKit.run(PRE + c + "M30", machine = MachineSetup(decimal = DecimalMode.CALCULATOR))
            assertNull(r.stop, "${r.issues}")
            val ends = moves(r).filter { it.kind == MoveKind.FEED }.map { it.tip.end }
            assertEquals(3, ends.size)
            near(Pt.ofDia(-1.0, 1.5), ends[0], 1e-9, "$c")
            near(Pt.ofDia(-3.0, 1.5), ends[1], 1e-9, "$c")
            near(Pt.ofDia(-3.0, 2.0), ends[2], 1e-9, "$c")
        }
        // 표준 설정이면 `Z-3` 은 0.003 mm 로 읽고 경고
        val std = SimKit.run(PRE + cols[0] + "M30")
        assertTrue(std.issues.any { it.code == IssueCode.NO_POINT_COORD && it.word == "Z-3" })
        near(Pt.ofDia(-0.003, 1.5), moves(std).filter { it.kind == MoveKind.FEED }[1].tip.end)
    }

    /** H 44: `G03 X2. Z-0.25 R0.25` = `I0. K-0.25`(X1.5 Z0 에서), `G02 X5. Z-2.5 R1.5` = `I1.5 K0.`(X2. Z-1. 에서) — I 는 반경값 */
    @Test
    fun haas44ArcRAndIK() {
        val head = PRE + listOf("G00 X1.5 Z0.1", "G01 Z0. F0.1")
        val withR = ok(head + listOf("G03 X2. Z-0.25 R0.25", "G01 Z-1.", "G02 X5. Z-2.5 R1.5", "M30"))
        val withIK = ok(head + listOf("G03 X2. Z-0.25 I0. K-0.25", "G01 Z-1.", "G02 X5. Z-2.5 I1.5 K0.", "M30"))
        val a = moves(withR).map { it.tip }.filterIsInstance<Arc>()
        val b = moves(withIK).map { it.tip }.filterIsInstance<Arc>()
        assertEquals(2, a.size)
        for ((x, y) in a.zip(b)) {
            near(x.center, y.center)
            assertEquals(x.cw, y.cw)
            assertEquals(90.0, x.sweepDeg, 1e-9)
        }
        assertEquals(false, a[0].cw) // G03 = 반시계
        near(Pt(-0.25, 0.75), a[0].center)
        near(Pt(-1.0, 2.5), a[1].center) // X2. Z-1. + (K0, I1.5)
        assertEquals(1.5, a[1].radius, 1e-12)
    }

    /** S 2-30 · H 41: R > 0 = 180° 이하, R < 0 = 넘음. 같은 두 점 · 같은 G02 에서 중심이 현의 반대쪽 */
    @Test
    fun arcRSign() {
        val head = PRE + listOf("G00 X20. Z0.", "G01 F0.1")
        val minor = moves(ok(head + listOf("G02 X20. Z-10. R6.", "M30"))).last().tip as Arc
        val major = moves(ok(head + listOf("G02 X20. Z-10. R-6.", "M30"))).last().tip as Arc
        assertTrue(minor.sweepDeg < 180 && major.sweepDeg > 180, "${minor.sweepDeg} ${major.sweepDeg}")
        assertEquals(minor.center.z, major.center.z, 1e-9)
        assertTrue((minor.center.x - 10) * (major.center.x - 10) < 0)
        // G02(시계) + R>0: 진행(−Z) 오른쪽 = +X 쪽에 중심
        assertTrue(minor.center.x > 10)
        // 반원(현/2 = R): 끝점이 0.001 안이면 받는다
        ok(head + listOf("G02 X20. Z-12.0015 R6.", "M30"))
        stops(head + listOf("G02 X20. Z-12.004 R6.", "M30"), IssueCode.ARC_R_SHORT, 6)
    }

    @Test
    fun arcErrors() {
        val head = PRE + listOf("G00 X20. Z0.", "G01 F0.1")
        stops(head + "G02 X30. Z-5.", IssueCode.ARC_NO_CENTER, 6)
        stops(head + "G02 X30. Z-5. R5. I5.", IssueCode.ARC_R_AND_IK, 6)
        stops(head + "G02 X30. Z-5. I3. K0.", IssueCode.ARC_END_OFF, 6)
        stops(head + "G02 I5.", IssueCode.FULL_CIRCLE, 6)
        stops(head + "G02 X20. Z0. I5.", IssueCode.FULL_CIRCLE, 6)
        // I · K 끝점이 원에서 0.002 안이면 그 중심 그대로
        ok(head + listOf("G02 X30. Z-5.001 I0. K-5.", "M30"))
    }

    @Test
    fun modalFeedSpindleAndMotion() {
        val r = ok(PRE + listOf("G00 X40. Z2.", "G01 Z0. F0.2", "X50.", "Z-10.", "G00 X60.", "Z5.", "M30"))
        val mv = moves(r)
        // 원점(X200 Z100) → X40 Z2 급송은 꺾여 두 토막(4.2)
        assertEquals(listOf(MoveKind.RAPID, MoveKind.RAPID, MoveKind.FEED, MoveKind.FEED, MoveKind.FEED, MoveKind.RAPID, MoveKind.RAPID), mv.map { it.kind })
        assertTrue(mv.filter { it.kind == MoveKind.FEED }.all { it.feed == 0.2 && it.perRev && it.rpm0 == 1000.0 })
        assertIs<EndStep>(r.steps.last())
        assertEquals(30, (r.steps.last() as EndStep).m)
    }

    /** H 133: M 은 블록 끝에 — `G01 … M03` 은 주축이 선 채 이송 → 멈춤 */
    @Test
    fun mAtBlockEnd() {
        stops(listOf("T0101", "G97 S1000", "G00 X40. Z2.", "G01 Z0. F0.2 M03"), IssueCode.SPINDLE_STOPPED, 4)
        // 같은 일을 G98(분당)로 하면 주축과 상관없다
        ok(listOf("T0101", "G98", "G00 X40. Z2.", "G01 Z0. F200 M03", "M30"))
        // 급송은 주축이 서 있어도 된다
        ok(listOf("G00 X40. Z2. M03", "M30"))
    }

    @Test
    fun feedSpindleErrors() {
        stops(listOf("G97 S1000 M03", "G00 X40. Z2.", "G01 Z0."), IssueCode.FEED_MISSING, 3)
        stops(listOf("M03", "G00 X40. Z2.", "G01 Z0. F0.2"), IssueCode.S_MISSING, 3)
        // G96 → G97 를 S 없이 바꾸면 S 단위가 바뀌어 다시 써야 한다
        stops(listOf("G50 S2000", "G96 S200 M03", "G00 X40. Z2.", "G01 Z0. F0.2", "G97", "G01 X50."), IssueCode.S_MISSING, 6)
        // G98 ↔ G99 를 F 없이 바꾸면 F 를 다시
        stops(listOf("G97 S1000 M03", "G00 X40. Z2.", "G01 Z0. F0.2", "G98", "G01 X50."), IssueCode.FEED_MISSING, 5)
        // S 1-14: G99 의 소수점 없는 F 는 기계마다 달라 받지 않는다. G98 은 mm/min 정수
        stops(listOf("G97 S1000 M03", "G00 X40. Z2.", "G01 Z0. F2"), IssueCode.NO_POINT_FEED, 3)
        ok(listOf("G98", "G00 X40. Z2.", "G01 Z0. F200", "M30"))
        stops(listOf("G01 X10. F0.", "M30"), IssueCode.BAD_VALUE, 1)
    }

    /** H 49: G50 S 는 G96 계산값과 G97 rpm 을 모두 자른다. G96 에 G50 이 없으면 경고 */
    @Test
    fun spindleClamp() {
        val r = ok(listOf("G50 S2000", "G97 S3000 M03", "G00 X40. Z2.", "G01 Z0. F0.2", "M30"))
        assertTrue(has(r, IssueCode.RPM_OVER_LIMIT))
        assertEquals(2000.0, moves(r).last().rpm0)
        val noClamp = ok(listOf("G96 S200 M03", "G00 X40. Z2.", "G01 Z0. F0.2", "M30"))
        assertTrue(has(noClamp, IssueCode.NO_CLAMP_G96))
        val clamped = ok(listOf("G50 S2000", "G96 S200 M03", "G00 X40. Z2.", "G01 Z0. F0.2", "M30"))
        assertTrue(!has(clamped, IssueCode.NO_CLAMP_G96))
        // n = 1000·200/(π·40) = 1591.5 rpm
        assertEquals(200_000 / (PI * 40), moves(clamped).last().rpm0, 1e-9)
        assertEquals(200.0, moves(clamped).last().css)
        stops(listOf("G50 X100. Z50."), IssueCode.G50_COORD, 1)
        stops(listOf("G50"), IssueCode.WORD_MISSING, 1)
        stops(listOf("G50 S2000 M03"), IssueCode.WORD_NOT_USED, 1)
    }

    /** G28(S 2-47 · H 48): 쓴 축만, 중간점 거쳐 원점(설정). U0 · W0 = 중간점 없이 */
    @Test
    fun g28() {
        val home = MachineSetup().home
        val a = ok(listOf("G00 X40. Z2.", "G28 U0. W0.", "M30"))
        val ma = moves(a)
        assertTrue(ma.filter { it.line == 2 }.let { g -> g.isNotEmpty() && g.all { it.src == StepSrc.G28_HOME } })
        near(home, ma.last().tip.end)
        val b = ok(listOf("G00 X40. Z2.", "G28 U0.", "M30"))
        near(Pt(2.0, home.x), moves(b).last().tip.end)
        val c = ok(listOf("G00 X40. Z2.", "G28 X60. Z10.", "M30"))
        val via = moves(c).filter { it.src == StepSrc.G28_VIA }
        near(Pt.ofDia(10.0, 60.0), via.last().tip.end)
        near(home, moves(c).last().tip.end)
        // 다음 이동은 원점에서
        val d = ok(listOf("G28 U0. W0.", "G00 W-10.", "M30"))
        near(Pt(home.z - 10, home.x), moves(d).last().tip.end)
        stops(listOf("G28"), IssueCode.WORD_MISSING, 1)
        stops(listOf("G28 U0. R1."), IssueCode.WORD_NOT_USED, 1)
    }

    /** T(H 65 · S 3-78): 뒤 2자리 = 보정 번호. T 와 이송이 한 블록이면 멈춤(S 2-24) */
    @Test
    fun tools() {
        val r = ok(listOf("T0101", "T202", "G00 X40. Z2. T0303", "T0300", "M30"))
        val ts = r.steps.filterIsInstance<ToolStep>()
        assertEquals(listOf(1, 2, 3, 3), ts.map { it.toTool })
        assertEquals(listOf(1, 2, 3, 0), ts.map { it.offset })
        assertEquals(listOf(1, 2, 3), r.offsetsUsed)
        stops(listOf("G97 S1000 M03", "G01 X10. F0.2 T0101"), IssueCode.T_WITH_FEED, 2)
        // 모달 G01 이어도 축이 있으면 이송
        stops(listOf("G97 S1000 M03", "G01 X10. F0.2", "Z-5. T0101"), IssueCode.T_WITH_FEED, 3)
    }

    @Test
    fun mCodes() {
        val r = ok(listOf("M08", "M00", "M01", "M41", "M09", "M02", "G00 X1."))
        val st = r.steps
        assertEquals(listOf(false, true), st.filterIsInstance<StopStep>().map { it.optional })
        assertTrue(has(r, IssueCode.MACHINE_M))
        assertEquals(2, (st.last() as EndStep).m)
        assertTrue(st.filterIsInstance<StateStep>().first().coolant)
        assertTrue(moves(r).isEmpty()) // M02 뒤는 실행하지 않는다
        val noEnd = ok(listOf("G00 X1."))
        assertTrue(has(noEnd, IssueCode.NO_END))
    }

    @Test
    fun wordsNotUsed() {
        stops(listOf("G00 X10. R1."), IssueCode.WORD_NOT_USED, 1)
        stops(listOf("G00 X10. P100"), IssueCode.WORD_NOT_USED, 1)
        stops(listOf("G00 X10. Q1"), IssueCode.WORD_NOT_USED, 1)
        stops(listOf("L2"), IssueCode.WORD_NOT_USED, 1)
        stops(listOf("G00 X10. ,C1."), IssueCode.CORNER_ON_RAPID, 1)
        // FANUC · S 3-79 의 G01 I/K/R 직각 모서리 · Haas 모서리는 받지 않는다
        stops(PRE + "G01 X10. K-1. F0.1", IssueCode.G01_CORNER_IKR, 4)
        stops(PRE + "G01 W-20. R3. F0.1", IssueCode.G01_CORNER_IKR, 4)
        stops(PRE + listOf("G01 Z-1. F0.1", "X2. I0.1"), IssueCode.G01_CORNER_IKR, 5)
    }

    /** G04(S 3-72): 다른 명령과 한 블록 금지 */
    @Test
    fun dwellBlock() {
        stops(listOf("G04 X1. M03"), IssueCode.G04_MIXED, 1)
        stops(listOf("G04 X1. P1000"), IssueCode.G04_MIXED, 1)
        stops(listOf("G01 G04 X1."), IssueCode.G04_MIXED, 1)
        stops(listOf("G04"), IssueCode.WORD_MISSING, 1)
        val r = ok(listOf("G04 U0.5", "M30"))
        assertEquals(0.5, r.totalSec, 1e-12)
    }

    /** 글의 STOP 문제는 그 블록에서 실행을 멈추고 앞까지는 그린다(1.5) */
    @Test
    fun parseStopHaltsAtLine() {
        val r = stops(listOf("G00 X40. Z2.", "G00 X50.", "G00 X1. Y2.", "G00 X60."), IssueCode.UNKNOWN_ADDRESS, 3)
        assertEquals(3, moves(r).size) // 꺾인 급송 2 + 1
    }

    @Test
    fun blockSkip() {
        val text = listOf("G00 X40. Z2.", "/G00 X80.", "M30")
        assertEquals(3, moves(ok(text)).size)
        assertEquals(2, moves(ok(text, machine = MachineSetup(blockSkip = true))).size)
    }

    @Test
    fun extentAndPose() {
        val r = ok(PRE + listOf("G00 X20. Z0.", "G01 F0.1", "G02 X20. Z-10. R-6.", "M30"), machine = MachineSetup(home = Pt(5.0, 10.0)))
        val (lo, hi) = r.extent!!
        val arc = moves(r).last().tip as Arc
        // 180° 넘는 시계 원호(중심이 현 아래)는 아래로 볼록 — 상자는 원의 바닥 · 왼쪽 끝까지
        assertEquals(arc.center.x - 6, lo.x, 1e-9)
        assertEquals(arc.center.z - 6, lo.z, 1e-9)
        assertEquals(Pt(5.0, 10.0), hi)
        // 재생: 원호 가운데 시각 → 원 위
        val mv = moves(r).last()
        val p = r.poseAt((mv.t0 + mv.t1) / 2)
        assertEquals(6.0, (p.tip - arc.center).length, 1e-9)
        assertEquals(r.steps.indexOf(mv), p.step)
        val start = r.poseAt(-1.0)
        assertTrue(start.step == 0)
        val end = r.poseAt(r.totalSec + 5)
        near(arc.end, end.tip)
        // 블록 시작 · 끝 시각
        val first = r.steps.indexOfFirst { it is MoveStep }
        assertEquals(r.steps[first].t0, r.blockStart(first))
        assertTrue(r.blockEnd(first) >= r.steps[first].t1)
        assertTrue(moves(r).first().tip is Line)
    }
}
