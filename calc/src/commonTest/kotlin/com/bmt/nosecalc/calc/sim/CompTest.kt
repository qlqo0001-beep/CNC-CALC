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
        stops(listOf("T0101", "G97 S1000 M03", "G00 X40. Z0.", "G42 G01 X40. Z0. F0.1"), IssueCode.COMP_START_ZERO, 4)
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
        // 이동 없는 G40 뒤의 이동은 경로를 모른다 → 멈춤. 뒤에 이동이 없으면 괜찮다
        stops(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "G00 X60."), IssueCode.COMP_CANCEL_NO_MOVE, 7)
        val tail = ok(pre + listOf("G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40", "M30"))
        assertEquals("X40.000 Z-10.400", xz(tipAt(tail, 5))) // 끝 법선 자리까지
        // 공구 설정 빈칸
        stops(pre + listOf("G42 G01 X40. Z0. F0.1"), IssueCode.TOOL_DATA_MISSING, 4, tools = emptyMap())
        stops(pre + listOf("G42 G01 X40. Z0. F0.1"), IssueCode.TOOL_DATA_MISSING, 4, tools = mapOf(1 to ToolOffset(0.4, null)))
    }

    @Test
    fun compWarnings() {
        // S 3-78: T 보정과 G41/G42 가 둘 다 있어야 보정 — T□□00 이면 보정 없이 가고 경고
        val noOff = ok(listOf("T0100", "G97 S1000 M03", "G00 X44. Z1.", "G42 G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X44.", "M30"))
        assertTrue(has(noOff, IssueCode.COMP_NO_OFFSET))
        assertEquals("X40.000 Z-10.000", xz(tipAt(noOff, 5)))
        // G41/G42 를 먼저 걸고 T 는 나중(S 3-78 그림 3-16) — T 뒤 첫 이동이 시작 블록
        val late = ok(listOf("G97 S1000 M03", "G00 X36. Z1.", "G42", "T0101", "G01 X40. Z0. F0.1", "G01 Z-10.", "G40 G00 X44.", "M30"))
        assertTrue(!has(late, IssueCode.COMP_NO_OFFSET))
        assertEquals("X40.000 Z-0.400", xz(tipAt(late, 5)))
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
     * 같은 규칙의 독립 구현(RefG4x = 앱 VerifyFlipTest.simG4x(typeB = false))과 무작위 2 000 프로그램에서 노즈 경로가 같아야(1e-8).
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
            val cancel = "G40 " + put("G01", c)
            val prog = listOf("G00 X${f(entry.xDia)} Z${f(entry.z)}", start) + body + cancel
            val sim = SimKit.run(listOf("T0101", "G97 S1000 M03", "G99 F0.2") + prog + "M30", mapOf(1 to ToolOffset(r, tip)))
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
}
