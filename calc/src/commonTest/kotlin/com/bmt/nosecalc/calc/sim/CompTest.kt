package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.SimKit.has
import com.bmt.nosecalc.calc.sim.SimKit.moves
import com.bmt.nosecalc.calc.sim.SimKit.near
import com.bmt.nosecalc.calc.sim.SimKit.ok
import com.bmt.nosecalc.calc.sim.SimKit.stops
import com.bmt.nosecalc.calc.sim.SimKit.xz
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 노즈R 보정 모형(`docs/simulator-design.md` 3절 — 형식 A, `calc-design.md` 와 같은 규칙).
 * 출처: H = Haas ST/TL 워크북 2015(인쇄 쪽), S = Siemens ISO Turning 04/2007, 원본 06 · 10.jpg.
 */
class CompTest {
    private fun tool(r: Double, tip: Int) = mapOf(1 to ToolOffset(r, tip))

    /** 형식 A · B 기계(No.5003#1 SUV = 0) — 이동 없는 시작 · 취소 블록에서 움직이지 않음 */
    private val ab = MachineSetup(compStartup = CompStartup.AB)

    /** 블록 끝 고스트의 실제 인선(보정 좌표) */
    private fun tipAt(r: SimRun, line: Int): Pt = r.ghosts.last { it.line == line }.tip

    private fun assertXZ(xDia: Double, z: Double, p: Pt, tol: Double, msg: String) {
        assertTrue(abs(p.xDia - xDia) <= tol && abs(p.z - z) <= tol, "$msg 기대 X$xDia Z$z, 실제 ${xz(p)} (${p.xDia}, ${p.z})")
    }

    /** 원본 06.jpg: `G00 X40. Z0.` → `G01 X34. Z-2.`, 내경 0.4R · 팁 2 · G41 — 보정 좌표 X34.000 Z-2.186(진입 · 취소를 더함) */
    @Test
    fun original06() {
        val r = ok(
            listOf("T0101", "G97 S600 M03", "G99", "G00 X40. Z1.", "G41 G01 Z0. F0.08", "G01 X34. Z-2.", "G01 Z-30.", "G40 G00 X32.", "M30"),
            tool(0.4, 2),
        )
        assertEquals("X34.000 Z-2.186", xz(tipAt(r, 6)))
        // 안쪽 모서리 취소 블록 앞 = 두 보정선의 교점 → 인선이 도면 꼭짓점에
        assertEquals("X34.000 Z-30.000", xz(tipAt(r, 7)))
        assertEquals(setOf(1), r.compOffsets)
        // 노즈 중심 경로는 끊기지 않는다
        for ((a, b) in moves(r).zipWithNext()) near(a.nose.end, b.nose.start)
    }

    /**
     * H 47 의 R 프로그램 도면(빈칸 R 은 O00052 에서 거꾸로 구한 0.12 · 0.24 · 0.36)에 G42 · TNR .031 · 팁 3 을 걸면
     * 실제 인선 = H 53 O00052 R 열(N205~N212). N209 의 X1.667 은 I/K 열(N109)이 X1.668 — 워크북 반올림 차이라 0.0011 허용.
     */
    @Test
    fun haas47and53() {
        val r = ok(
            listOf(
                "T0101", "G97 S1450 M03", "G99", "G00 X0.91 Z0.1", "G42 G01 Z0. F0.01", "X1.01", "G03 X1.25 Z-0.12 R0.12",
                "G01 Z-0.89", "G02 X1.73 Z-1.13 R0.24", "G01 X2.28", "G03 X3. Z-1.49 R0.36", "G01 Z-2.375", "G40 G00 X3.1", "M30",
            ),
            tool(0.031, 3),
        )
        val want = listOf(
            5 to (0.91 to 0.0), 6 to (0.948 to 0.0), 7 to (1.25 to -0.151), 8 to (1.25 to -0.921),
            9 to (1.667 to -1.13), 10 to (2.218 to -1.13), 11 to (3.0 to -1.521), 12 to (3.0 to -2.375),
        )
        for ((line, xzw) in want) assertXZ(xzw.first, xzw.second, tipAt(r, line), 0.0011, "줄 $line")
        // 보정 원호 R ± r = O00052 의 R0.151 · R0.209 · R0.391
        val radii = moves(r).map { it.tip }.filterIsInstance<Arc>().map { it.radius }
        assertEquals(3, radii.size)
        for ((got, w) in radii.zip(listOf(0.151, 0.209, 0.391))) assertEquals(w, got, 1e-9)
    }

    /** H 56 O00055(수동 보정 .03125 TNR) — 같은 도면을 G42 로 넣으면 N8~N14 점과 0.0001 안 */
    @Test
    fun haas56() {
        val r = ok(
            listOf(
                "T0101", "G97 S1450 M03", "G99", "G00 X0.6 Z0.1", "G42 G01 Z0. F0.006", "X0.75", "X1.25 Z-0.25", "Z-1.",
                "X1.75 Z-1.25", "X2.4", "X3. Z-1.55", "Z-2.375", "G40 G00 X3.1", "M30",
            ),
            tool(0.03125, 3),
        )
        val want = listOf(
            6 to (0.7134 to 0.0), 7 to (1.250 to -0.2683), 8 to (1.25 to -1.0183), 9 to (1.7134 to -1.250),
            10 to (2.3634 to -1.25), 11 to (3.0 to -1.5683), 12 to (3.0 to -2.375),
        )
        for ((line, xzw) in want) assertXZ(xzw.first, xzw.second, tipAt(r, line), 0.0001, "줄 $line")
    }

    /** 시작 · 취소의 자세(형식 A): 안쪽 = 두 보정선의 교점, 바깥 = 시작은 다음 블록 시작의 법선, 취소는 앞 블록 끝의 법선 */
    @Test
    fun startCancelPosture() {
        // 안쪽 시작: X30 에서 −Z 로 들어와 단면을 올라감 → 인선이 꼭짓점 X30 Z0 에(calc-design '시작점 X30' 예)
        val inner = ok(listOf("T0101", "G97 S1000 M03", "G00 X30. Z1.", "G42 G01 Z0. F0.1", "G01 X40.", "G01 X50. Z-5.", "G40 G00 X52. Z-6.", "M30"))
        assertEquals("X30.000 Z0.000", xz(tipAt(inner, 4)))
        assertEquals("X39.531 Z0.000", xz(tipAt(inner, 5))) // 직접 보정 글의 `G01 X39.531`
        assertTrue(!has(inner, IssueCode.COMP_TYPE_A))

        // 바깥 시작: 아래에서 비스듬히 올라와 −Z 로 → 노즈 = 꼭짓점 + 다음 블록 법선 × r → 인선 X40 Z-0.4
        val outer = ok(listOf("T0101", "G97 S1000 M03", "G00 X36. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X36. Z-11.", "M30"))
        assertEquals("X40.000 Z-0.400", xz(tipAt(outer, 4)))
        // 바깥 취소: 앞 블록 끝 법선 → 인선 X40 Z-10.4, 그 뒤 취소 블록 끝점으로 곧게(G40)
        assertEquals("X40.000 Z-10.400", xz(tipAt(outer, 5)))
        assertEquals("X36.000 Z-11.000", xz(tipAt(outer, 6)))
        assertEquals(listOf(4, 6), outer.issues.filter { it.code == IssueCode.COMP_TYPE_A }.map { it.line })
        // 시작 블록 노즈 = G40 노즈(점 − 팁 벡터)에서 곧게
        val st = moves(outer).first { it.line == 4 }
        near(Pt.ofDia(1.0, 36.0) - NoseComp.tipVector(3, 0.4), st.nose.start)
        assertTrue(st.nose is Line)
    }

    @Test
    fun compErrors() {
        val pre = listOf("T0101", "G97 S1000 M03", "G00 X44. Z1.")
        stops(pre + listOf("G42 G02 X40. Z-1. R2. F0.1"), IssueCode.COMP_START_ARC, 4)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G02 X44. Z-12. R2."), IssueCode.COMP_CANCEL_ARC, 6)
        stops(listOf("T0101", "G97 S1000 M03", "G00 X40. Z0.", "G42 G01 X40. Z0. F0.1"), IssueCode.COMP_START_ZERO, 4, machine = ab)
        // 바깥 꺾임 > 90°: −Z 로 가다 +Z · −X 로 되돌아감
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G01 X30. Z-5.", "G40 G00 X20."), IssueCode.COMP_ACUTE_OUTER, 6)
        // 노즈(0.8 폭)보다 좁은 0.5 홈 바닥 → 보정 토막이 거꾸로
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G01 X38.", "G01 Z-10.5", "G01 X40.", "G01 Z-20.", "G40 G00 X44."), IssueCode.COMP_INTERFERENCE, 7)
        // 오목 원호 R0.3 < r 0.4 (H 61)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G02 X40.6 Z-10.3 R0.3", "G01 X50."), IssueCode.COMP_ARC_SMALL, 6)
        // 움직임 없는 블록 둘(H 58) — 하나는 된다
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "M08", "M09", "G01 X50."), IssueCode.COMP_IDLE_BLOCKS, 7)
        ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "M08", "G01 X50.", "G40 G00 X52.", "M30"))
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G41 G01 Z-10."), IssueCode.COMP_SWITCH, 5)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G01 X50. I1."), IssueCode.COMP_G40_IK, 6)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "T0202"), IssueCode.COMP_TOOL_CHANGE, 5)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G28 U0."), IssueCode.COMP_AT_G28, 5)
        stops(listOf("T0101", "G42", "G28 U0."), IssueCode.COMP_AT_G28, 3)
        // 형식 A · B: 이동 없는 G40 뒤 원호 · 모서리 · 곧바로 다시 보정은 경로를 모른다 → 멈춤. 뒤에 이동이 없으면 괜찮다
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G02 X50. Z-15. R5."), IssueCode.COMP_CANCEL_NO_MOVE, 7, machine = ab)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G01 X50. ,C1.", "G01 Z-20."), IssueCode.COMP_CANCEL_NO_MOVE, 7, machine = ab)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G42 G01 X50."), IssueCode.COMP_CANCEL_NO_MOVE, 7, machine = ab)
        val tail = ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "M30"), machine = ab)
        assertEquals("X40.000 Z-10.400", xz(tipAt(tail, 5))) // 끝 법선 자리까지
        // 형식 C: G40 블록에서 지령점으로 옮겨 뒤는 보통 G40 상태 — 원호 · 모서리 · 다시 보정도 된다
        ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G02 X50. Z-15. R5.", "M30"))
        ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G01 X50. ,C1.", "G01 Z-20.", "M30"))
        ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G42 G01 X50.", "G40 G01 X52.", "M30"))
        // 공구 설정 빈칸
        stops(pre + listOf("G42 G01 X40. Z0. F0.1"), IssueCode.TOOL_DATA_MISSING, 4, tools = emptyMap())
        stops(pre + listOf("G42 G01 X40. Z0. F0.1"), IssueCode.TOOL_DATA_MISSING, 4, tools = mapOf(1 to ToolOffset(0.4, null)))
    }

    /**
     * 이동 없는 G40(FF 5.4.4 그림 5.4.4(e) 204쪽 형식 A·B · F0 14.3.4 228쪽): 앞 블록 끝에 법선 벡터 — 노즈 접점이 끝점, 인선 X40 Z-10.4 —
     * 취소 블록에서는 움직이지 않고, 다음 이동이 그 자리에서 끝점(G40 상태: 노즈 = 점 − 팁 벡터)으로 곧게(G00 이면 급송 설정대로). INFO 하나.
     * 기계 설정 형식 A · B(형식 C 는 [typeCCancelAndStart])
     */
    @Test
    fun cancelWithoutMove() {
        val pre = listOf("T0101", "G97 S1000 M03", "G00 X44. Z1.")
        for (rapid in listOf(RapidPath.LINEAR, RapidPath.DOGLEG)) {
            val r = ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G00 X60.", "G00 Z5.", "M30"), machine = ab.copy(rapidPath = rapid))
            assertEquals("X40.000 Z-10.400", xz(tipAt(r, 5)))
            assertEquals(listOf(6), r.issues.filter { it.code == IssueCode.COMP_CANCEL_ALONE }.map { it.line })
            val m7 = moves(r).filter { it.line == 7 }
            near(Pt.ofDia(-10.0, 40.8), m7.first().nose.start)
            near(Pt.ofDia(-9.6, 60.8), m7.last().nose.end)
            assertEquals(if (rapid == RapidPath.LINEAR) 1 else 2, m7.size)
            // 그 뒤는 보통 G40 상태
            val m8 = moves(r).single { it.line == 8 }
            near(Pt.ofDia(5.4, 60.8), m8.nose.end)
        }
        // T□□00(보정 번호 0)으로 끈 것도 같다 — 그 뒤 팁 벡터 0
        val t0 = ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "T0100", "G01 X60.", "M30"), machine = ab)
        near(Pt.ofDia(-10.0, 40.8), moves(t0).first { it.line == 7 }.nose.start)
        near(Pt.ofDia(-10.0, 60.0), moves(t0).last { it.line == 7 }.nose.end)
    }

    @Test
    fun compWarnings() {
        // S 3-78: T 보정과 G41/G42 가 둘 다 있어야 보정 — T□□00 이면 보정 없이 가고 경고
        val noOff = ok(listOf("T0100", "G97 S1000 M03", "G00 X44. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X44.", "M30"))
        assertTrue(has(noOff, IssueCode.COMP_NO_OFFSET))
        assertEquals("X40.000 Z-10.000", xz(tipAt(noOff, 5)))
        // G41/G42 를 먼저 걸고 T 는 나중(S 3-78 그림 3-16) — 형식 A · B 는 T 뒤 첫 이동이 시작 블록
        val lateText = listOf("G97 S1000 M03", "G00 X36. Z1.", "G42", "T0101", "G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X44.", "M30")
        val late = ok(lateText, machine = ab)
        assertTrue(!has(late, IssueCode.COMP_NO_OFFSET))
        assertEquals("X40.000 Z-0.400", xz(tipAt(late, 5)))
        // 형식 C: 보정이 걸리는 T 블록(이동 없음)이 시작 — 그 블록에서 옮기고, 다음 이동은 보정 중 블록(끝 = 다음 블록과 늘인 교점)
        val lateC = ok(lateText)
        assertEquals(StepSrc.SHIFT_START, moves(lateC).first { it.line == 4 }.src)
        near(Pt.ofDia(1.0, 36.0) - NoseComp.tipVector(3, 0.4), moves(lateC).first { it.line == 4 }.nose.start)
        for ((a, b) in moves(lateC).filter { it.line >= 4 }.zipWithNext()) near(a.nose.end, b.nose.start)
        // G40 없이 끝
        val open = ok(listOf("T0101", "G97 S1000 M03", "G00 X36. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "M30"))
        assertTrue(has(open, IssueCode.COMP_NOT_CANCELLED))
        assertEquals("X40.000 Z-10.400", xz(tipAt(open, 5)))
        // R 0 이면 보정해도 경로가 같다(H 67)
        val zero = ok(listOf("T0101", "G97 S1000 M03", "G00 X36. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X44.", "M30"), tool(0.0, 3))
        assertEquals("X40.000 Z0.000", xz(tipAt(zero, 4)))
        assertEquals("X40.000 Z-10.000", xz(tipAt(zero, 5)))
    }

    /** M00 · M08 같은 걸음은 늦게 내보내는 보정 이동 뒤에 와야(시간 순) */
    @Test
    fun deferredStepsKeepOrder() {
        val r = ok(listOf("T0101", "G97 S1000 M03", "G00 X36. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "M00", "G01 X50.", "G40 G00 X52.", "M30"))
        val i5 = r.steps.indexOfLast { it is MoveStep && it.line == 5 }
        val iStop = r.steps.indexOfFirst { it is StopStep }
        val i7 = r.steps.indexOfFirst { it is MoveStep && it.line == 7 }
        assertTrue(i5 < iStop && iStop < i7, "$i5 $iStop $i7")
        for ((a, b) in r.steps.zipWithNext()) assertEquals(a.t1, b.t0, 1e-12)
    }

    /**
     * S 3-79 그림 3-17 예제(G42 · 팁 3). N1 `G92 X140. Z20.`(체계 B 좌표계 설정)은 원점 설정으로, N8 · N9 의 직각 모서리
     * `W-20. R3.` · `X50. K-3.` 은 같은 뜻의 `,R3.` · `,C3.` 으로 바꿨다. N13 `G04 U0`(움직임 없는 블록 하나), N17 `T0 G40`(이동 없는 취소)이 있다.
     */
    @Test
    fun siemens379() {
        val r = ok(
            listOf(
                "N2 G00 S1700 M03 T0202", "N3 G00 G42 X0 Z5.", "N4 G01 Z0 F0.2", "N5 X20.", "N6 Z-20.", "N7 X30. W-15. S1100",
                "N8 G1 W-20. ,R3.", "N9 G1 X50. ,C3. S700", "N10 G01 Z-70.", "N11 G02 X90. Z-90. R20. S360", "N12 G01 X110. S300",
                "N13 G04 U0", "N14 Z-110.", "N15 X120.", "N16 G00 X140. Z30.", "N17 T0 G40", "M30",
            ),
            mapOf(2 to ToolOffset(0.4, 3)),
            MachineSetup(home = Pt.ofDia(20.0, 140.0)),
        )
        val mv = moves(r)
        for ((a, b) in mv.zipWithNext()) near(a.nose.end, b.nose.start)
        // N5 끝(단면 → 외경 90° 바깥 꺾임): 늘인 교점 → 인선이 도면 꼭짓점 X20 Z0
        assertEquals("X20.000 Z0.000", xz(tipAt(r, 4)))
        // N9 면취 시작 측정점
        assertEquals("X44.000 Z-55.000", xz(r.ghosts.first { it.line == 8 }.cmd))
        assertEquals(setOf(2), r.compOffsets)
        // 시작 블록 N3 는 급송이라 꺾인 경로
        assertTrue(mv.count { it.line == 2 } == 2)
    }

    /**
     * 같은 규칙의 독립 구현(RefG4x = 앱 VerifyFlipTest.simG4x(typeB = false) — 이동 없는 G40 은 형식 A · B)과 무작위 2 000 프로그램에서 노즈 경로가 같아야(1e-8).
     * 시뮬레이터가 멈춘 사례(바깥 꺾임 > 90° · 간섭 · R < r)는 비교에서 빼고 개수만 본다.
     */
    @Test
    fun randomAgainstReference() {
        val rnd = Random(20261028)
        var compared = 0
        var stopped = 0
        repeat(2000) { k ->
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val tip = rnd.nextInt(10)
            val side = if (rnd.nextBoolean()) 41 else 42
            val body = mutableListOf<String>()
            var p = Pt(rnd.nextDouble(-5.0, 5.0), rnd.nextDouble(10.0, 40.0))
            fun f(v: Double) = Fmt.nc(v)
            fun put(g: String, q: Pt, rr: Double? = null): String = "$g X${f(q.xDia)} Z${f(q.z)}" + (rr?.let { " R${f(it)}" } ?: "")
            // 진입 · 시작
            val entry = p
            var dir = rnd.nextDouble(0.0, 2 * PI)
            p = Pt(f(p.z + cos(dir) * rnd.nextDouble(2.0, 6.0)).toDouble(), f(p.x + sin(dir) * rnd.nextDouble(2.0, 6.0)).toDouble() / 1.0)
            val start = "G$side " + put("G01", p)
            val n = 2 + rnd.nextInt(5)
            for (i in 0 until n) {
                dir += rnd.nextDouble(-1.4, 1.4)
                val len = rnd.nextDouble(1.0, 8.0)
                val q = Pt(f(p.z + cos(dir) * len).toDouble(), f((p.x + sin(dir) * len) * 2).toDouble() / 2)
                if ((q - p).length < 0.5) continue
                if (rnd.nextInt(3) == 0) {
                    val rr = f((q - p).length / 2 * rnd.nextDouble(1.05, 3.0)).toDouble()
                    body += put(if (rnd.nextBoolean()) "G02" else "G03", q, rr)
                } else body += put("G01", q)
                p = q
            }
            dir += rnd.nextDouble(-2.5, 2.5)
            val c = Pt(f(p.z + cos(dir) * 3).toDouble(), f((p.x + sin(dir) * 3) * 2).toDouble() / 2)
            // 셋에 하나는 이동 없는 G40 + 다음 이동(FF 5.4.4 그림 5.4.4(e))
            val cancel = if (rnd.nextInt(3) == 0) listOf("G40", put("G01", c)) else listOf("G40 " + put("G01", c))
            val prog = listOf("G00 X${f(entry.xDia)} Z${f(entry.z)}", start) + body + cancel
            val sim = SimKit.run(listOf("T0101", "G97 S1000 M03", "G99 F0.2") + prog + "M30", mapOf(1 to ToolOffset(r, tip)), ab)
            val ref = RefG4x.run(RefG4x.parse(prog), NoseComp.tipVector(tip, r).let { RefG4x.P(it.z, it.x) }, r)
            if (sim.stop != null) {
                stopped++
                return@repeat
            }
            assertTrue(ref != null, "$k 기준이 R < r 인데 시뮬레이터는 통과: $prog")
            val simEnds = moves(sim).filter { it.line >= 5 }.map { it.nose.end }
            val refEnds = ref.filter { (it.b - it.a).len > 1e-12 }.map { Pt(it.b.z, it.b.x) }
            assertEquals(refEnds.size, simEnds.size, "$k 토막 수 $prog\n$simEnds\n$refEnds")
            for ((a, b) in refEnds.zip(simEnds)) near(a, b, 1e-8, "$k $prog")
            // 원호 토막은 원호로
            val simArcs = moves(sim).filter { it.line >= 5 }.count { it.nose is Arc }
            val refArcs = ref.filter { (it.b - it.a).len > 1e-12 }.count { it is RefG4x.Ac && (it.a - it.c).len > 1e-9 }
            assertEquals(refArcs, simArcs, "$k 원호 수 $prog")
            compared++
        }
        println("randomAgainstReference compared=$compared stopped=$stopped")
        assertTrue(compared >= 600, "비교 $compared · 멈춤 $stopped")
    }

    /** 고스트 노즈를 0.001 글로 — 줄마다 마지막 */
    private fun noseAt(r: SimRun, line: Int): String = xz(r.ghosts.last { it.line == line }.nose)

    /**
     * 형식 C(No.5003#1 SUV = 1 — 기계 설정 처음 값): 이동 없는 G40 블록에서 노즈가 앞 블록 끝 법선 E + r·nₑ → 지령점 − 팁 벡터(FF 그림 5.4.4(f) 205쪽),
     * 이동 없는 G41/G42 블록에서 P − t → P + r·n₁(n₁ = 다음 이동 블록의 공구 쪽 법선 — FF 180쪽). 'G40 바로 G41' 은 그 둘을 차례로(형식 A · B 는 설명서에
     * 경로가 없어 멈춤). 손 계산(0.4R · 팁 3, 팁 벡터 t = Z −0.4 · X −0.4 반경)은 줄 옆 주석의 노즈(직경 X).
     */
    @Test
    fun typeCCancelAndStart() {
        val text = listOf(
            "T0101", "G97 S1000 M03", "G99", "G00 X44. Z2.",
            "G42", // 5: X44.8 Z2.4 → X44.8 Z2.(다음 −Z 블록의 G42 법선 +X)
            "G01 Z-10. F0.1", // 6: 다음(+X)과 안쪽 교점 X44.8 Z-9.6
            "X50.", // 7: 끝 법선 X50. Z-9.6
            "G40", // 8: → X50.8 Z-9.6(지령점 − 팁 벡터)
            "G41", // 9: X50.8 Z-9.6 → X49.2 Z-10.(다음 −Z 블록의 G41 법선 −X)
            "Z-12.", // 10: 다음(−X)과 안쪽 교점 X49.2 Z-11.6
            "X44.", // 11: 끝 법선 X44. Z-11.6
            "G40", // 12: → X44.8 Z-11.6
            "G00 Z5.", // 13: G40 상태 X44.8 Z5.4
            "M30",
        )
        val r = ok(text)
        val want = listOf(
            5 to "X44.800 Z2.000", 6 to "X44.800 Z-9.600", 7 to "X50.000 Z-9.600", 8 to "X50.800 Z-9.600", 9 to "X49.200 Z-10.000",
            10 to "X49.200 Z-11.600", 11 to "X44.000 Z-11.600", 12 to "X44.800 Z-11.600", 13 to "X44.800 Z5.400",
        )
        for ((line, w) in want) assertEquals(w, noseAt(r, line), "줄 $line")
        // 옮김 이동은 그 줄의 모달(G01 → 이송), 명령 경로는 그 자리 한 점
        val s8 = moves(r).single { it.line == 8 }
        assertEquals(StepSrc.SHIFT_CANCEL, s8.src)
        assertEquals(MoveKind.FEED, s8.kind)
        assertEquals("X50.000 Z-9.600", xz(s8.nose.start))
        val s9 = moves(r).single { it.line == 9 }
        assertEquals(StepSrc.SHIFT_START, s9.src)
        assertEquals("X50.800 Z-9.600", xz(s9.nose.start))
        near(s9.cmd!!.start, s9.cmd!!.end)
        assertEquals(MoveKind.RAPID, moves(r).single { it.line == 5 }.kind)
        for ((a, b) in moves(r).zipWithNext()) near(a.nose.end, b.nose.start)
        val alone = r.issues.filter { it.code == IssueCode.COMP_CANCEL_ALONE }
        assertEquals(listOf(8, 12), alone.map { it.line })
        assertTrue(alone.all { it.args == listOf("C") })
        assertEquals(setOf(1), r.compOffsets)
        // 형식 A · B 는 'G40 바로 G41' 의 경로가 설명서에 없어 멈춘다
        stops(text, IssueCode.COMP_CANCEL_NO_MOVE, 10, machine = ab)
    }

    /**
     * 형식 C 의 이동 없는 시작은 다음 '이동 있는' 블록의 법선으로 — 사이 제자리 이동(U0 꼴, FF 5.4.3 'A block without tool movement')은 이동 없는 블록이라
     * 건너뛴다(FF 5.4.2 시작 조건 (4) — 형식 A · B 는 제자리 시작 블록에서 멈춤). 사이 M 걸음은 옮김 이동 뒤, 이동 없는 블록이 둘이면 멈춤(설계 3.2).
     */
    @Test
    fun typeCStartReadsAhead() {
        val pre = listOf("T0101", "G97 S1000 M03", "G99", "G00 X30. Z2.")
        val text = pre + listOf("G42", "X30.", "G01 Z-5. F0.1", "X40.", "G40", "G00 Z5.", "M30")
        val r = ok(text)
        assertEquals("X30.800 Z2.000", xz(moves(r).single { it.line == 5 }.nose.end))
        assertTrue(moves(r).none { it.line == 6 })
        stops(text, IssueCode.COMP_START_ZERO, 6, machine = ab)
        val m = ok(pre + listOf("G42", "M08", "G01 Z-5. F0.1", "X40.", "G40", "G00 Z5.", "M30"))
        val iShift = m.steps.indexOfFirst { it is MoveStep && it.line == 5 }
        val iState = m.steps.indexOfFirst { it is StateStep && it.line == 6 }
        assertTrue(iShift in 0 until iState, "$iShift $iState")
        for ((a, b) in m.steps.zipWithNext()) assertEquals(a.t1, b.t0, 1e-12)
        stops(pre + listOf("G42", "M08", "M09", "G01 Z-5. F0.1"), IssueCode.COMP_IDLE_BLOCKS, 7)
    }

    @Test
    fun typeCErrors() {
        val pre = listOf("T0101", "G97 S1000 M03", "G99", "G00 X44. Z2.")
        // G02/G03 모드의 이동 없는 시작 · 취소(FF 5.4.2 시작 조건 (3) — 알람 PS0034)
        stops(pre + listOf("G02 X44. Z0. R1. F0.1", "G42"), IssueCode.COMP_START_ARC, 6)
        stops(pre + listOf("G42 G01 Z0. F0.1", "G02 X48. Z-2. R2.", "G40"), IssueCode.COMP_CANCEL_ARC, 7)
        // 이동 없는 시작 뒤 이동 블록 없이 끔 — 옮길 방향이 없다
        val z = stops(pre + listOf("G42", "G40"), IssueCode.COMP_START_ZERO, 6)
        assertEquals(listOf("5"), z.stop!!.args)
        stops(pre + listOf("G42"), IssueCode.TOOL_DATA_MISSING, 5, tools = emptyMap())
        // 보정 중 공구 바꿈 · G28 은 이동 없는 시작 블록 뒤에도 멈춤
        stops(pre + listOf("G42", "T0202"), IssueCode.COMP_TOOL_CHANGE, 6)
        stops(pre + listOf("G42", "G28 U0."), IssueCode.COMP_AT_G28, 6)
        // 이동 있는 시작 · 취소는 형식 C 기계도 SUP 를 따른다 — 시뮬레이터는 형식 A(바깥이면 알림, args C)
        val outer = ok(listOf("T0101", "G97 S1000 M03", "G00 X36. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X36. Z-11.", "M30"))
        assertEquals("X40.000 Z-0.400", xz(tipAt(outer, 4)))
        assertEquals("X40.000 Z-10.400", xz(tipAt(outer, 5)))
        val ta = outer.issues.filter { it.code == IssueCode.COMP_TYPE_A }
        assertEquals(listOf(4, 6), ta.map { it.line })
        assertTrue(ta.all { it.args == listOf("C") })
        // T□□00 으로 이동 없이 끔: 보정 번호 0 의 지령점(팁 벡터 0)으로 옮김
        val t0 = ok(listOf("T0101", "G97 S1000 M03", "G00 X44. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "T0100", "G01 X60.", "M30"))
        val s6 = moves(t0).single { it.line == 6 }
        near(Pt.ofDia(-10.0, 40.8), s6.nose.start)
        near(Pt.ofDia(-10.0, 40.0), s6.nose.end)
        near(Pt.ofDia(-10.0, 60.0), moves(t0).last { it.line == 7 }.nose.end)
    }

    /**
     * 회사 꼴 손 계산(형식 C · 0.4R · 팁 3 — 앱 골든 taper-b 의 도형 X40 Z0 / X50 Z-5 · 끝 X50 Z-10 을 끝면부터 한 보정 구간으로 지은 글:
     * 혼자 G42 → G0 X Z20. → Z 로 다가감 → 깎기 → 공기로 빼는 줄 → 혼자 G40 → G0 Z):
     * 테이퍼 시작 노즈 X40.331 Z0.4 · 끝 X50.8 Z-4.834(= 직접 보정 글 X39.531 Z0. · X50. Z-5.234 의 노즈), 어깨 앞 X50.8 Z-9.6(안쪽 교점),
     * 빼는 줄 끝 X56. Z-9.6(끝 법선), G40 줄 X56.8 Z-9.6(지령점 − 팁 벡터, 공기). 깎는 줄의 노즈는 형식 A · B 와 같다 — 다른 곳은 G42 · G40 줄 둘레(공기)뿐.
     */
    @Test
    fun typeCCompanyStyleTaper() {
        val text = listOf(
            "T0101", "G97 S1000 M03", "G99",
            "G42", "G0X36.Z20.", "Z2.", "G1Z0.F.1", "X40.", "X50.Z-5.", "Z-10.F.15", "X56.F.3", "G40", "G0Z200.", "M30",
        )
        val c = ok(text)
        assertEquals("X36.800 Z0.400", noseAt(c, 7))
        assertEquals("X40.331 Z0.400", noseAt(c, 8))
        assertEquals("X50.800 Z-4.834", noseAt(c, 9))
        assertEquals("X50.800 Z-9.600", noseAt(c, 10))
        assertEquals("X56.000 Z-9.600", noseAt(c, 11))
        assertEquals("X56.800 Z-9.600", noseAt(c, 12))
        assertEquals("X56.800 Z200.400", noseAt(c, 13))
        // 형식 A · B 로 돌려도 깎는 줄(7~11)의 노즈 경로는 같다
        val a = ok(text, machine = ab)
        fun cut(r: SimRun) = moves(r).filter { it.line in 7..11 }
        assertEquals(cut(a).size, cut(c).size)
        for ((ma, mc) in cut(a).zip(cut(c))) {
            near(ma.nose.start, mc.nose.start, 1e-9, "줄 ${ma.line} 처음")
            near(ma.nose.end, mc.nose.end, 1e-9, "줄 ${ma.line} 끝")
        }
        // 다른 곳: 형식 A · B 는 G40 줄에서 움직이지 않는다
        assertTrue(moves(a).none { it.line == 12 })
    }

    /**
     * 보정 중 G41 ↔ G42 바꿈(FF 5.4.3 189~191쪽 — 형식과 무관): 바꾸기 앞 블록 A 의 보정선(앞 쪽)과 다음 이동 블록 B 의 보정선(바뀐 쪽)의 교점,
     * 교점이 없으면(평행) B 처음 법선. 시작 블록 바로 다음 블록에서 바꿈 · 바꾼 바로 다음 이동 있는 취소 · 원호 끝에서 보정선이 안 만남은 멈춤.
     * 손 계산(0.4R · 팁 3, 노즈 직경 X).
     */
    @Test
    fun switchSideInOffsetMode() {
        val pre = listOf("T0101", "G97 S1000 M03", "G00 X44. Z1.")
        // 교점: A = `Z-10.`(−Z · G42 → 노즈 X40.8) · B = `X50.`(+X · G41 → 노즈 Z-10.4) → X40.8 Z-10.4. B → C(−Z · G41) 는 안쪽 교점 X49.2 Z-10.4
        val a = ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G41", "G01 X50.", "G01 Z-20.", "G40 G00 X60.", "M30"))
        assertEquals("X40.800 Z-10.400", noseAt(a, 5))
        assertEquals("X49.200 Z-10.400", noseAt(a, 7))
        for ((x, y) in moves(a).zipWithNext()) near(x.nose.end, y.nose.start)
        // 교점 없음(A · B 한 직선): A 의 노즈가 B 처음 법선 X39.2 Z-10 으로 곧게, B 는 G41 보정선 위
        val b = ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G41", "G01 Z-20.", "G40 G00 X30.", "M30"))
        assertEquals("X39.200 Z-10.000", noseAt(b, 5))
        assertEquals("X39.200 Z-19.600", noseAt(b, 7))
        // 형식 C 이동 없는 시작 뒤 한 블록 간 다음이면 된다(시작 블록 · 그 다음 블록만 막힘)
        ok(pre + listOf("G42", "G01 Z-5. F0.1", "G41", "G01 X50.", "G40", "G00 Z5.", "M30"))
        stops(pre + listOf("G42", "G41", "G01 Z-5. F0.1"), IssueCode.COMP_SWITCH, 5)
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G41", "G01 Z-10."), IssueCode.COMP_SWITCH, 5, machine = ab)
        // 바꾼 바로 다음이 이동 있는 취소
        assertEquals(listOf("G40"), stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G41", "G40 G01 X50."), IssueCode.COMP_SWITCH, 7).stop!!.args)
        // 원호 끝(오목 — 보정선 R5 − 0.4)에서 바꾼 쪽 보정선(Z-10.4)이 만나지 않음
        val arc = stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-5.", "G02 X50. Z-10. R5.", "G41", "G01 X60.", "G40 G00 X70."), IssueCode.COMP_SWITCH, 8)
        assertEquals(listOf("6"), arc.stop!!.args)
    }
}
