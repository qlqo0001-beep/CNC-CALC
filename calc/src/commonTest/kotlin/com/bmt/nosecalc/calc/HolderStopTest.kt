package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.verify.StopChecker
import com.bmt.nosecalc.calc.verify.at
import com.bmt.nosecalc.calc.verify.contactEdge
import com.bmt.nosecalc.calc.verify.len
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 보링바 안전 끝점 · 잘린 NC(HolderStop). 설계 문서 손 계산 예 A~F · 뒤보링에서 안전 여유 0 과 0.5:
 * 끝점 여유 = 기준(±1e-5), 끝점까지 모든 자세 안전, 0.001 mm 더 가면 모자람, 잘린 NC 두 가지를 다시 읽어 굴린 자세 ≥ 기준 − 1e-4,
 * 후퇴 ≥ 0, 닿는 곳의 처음 좌표 = 기존 표(±0.003). 검사기([StopChecker])는 경로 · NC 읽기 · 제어기 보정 모형을 따로 짰다
 */
class HolderStopTest {
    private val r = 0.4
    private val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
    private val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0)
    private val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)
    private val socket = RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, legLen = 5.0)
    private val barF = BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0)

    /** 뒤보링 3점호(방 안 뒷면 → R5 → Ø20, 팁 1 · G42) */
    private val back = Arc3Input(inner = true, noseR = 0.4, x1 = 30.0, z1 = -20.0, x2 = 24.0, z2 = -19.0, x3 = 20.0, z3 = -15.0, zClear = 1.0)

    /** 뒤보링 바: f 12 면 시작에서 바 여유 0.6, DMIN 22 는 헤드(인선 뒤 −Z 쪽)가 Ø20 쪽으로 가며 경로 가운데에서 모자란다 */
    private val backBar = BoringBar(12.0, 12.0, InsertShape.C, 9.67, 95.0, dMin = 22.0)

    private class Case(val name: String, val sol: RoundSolution, val drawn: RoundSolution, val face: Boolean, val bar: BoringBar)

    private fun type(name: String, i: RoundInput, bar: BoringBar): Case {
        val inp = i.copy(inner = true, noseR = r, zClear = 1.0)
        return Case(name, RoundCalc.calc(inp).solution!!, RoundCalc.calc(inp.copy(noseR = 0.0)).solution!!, true, bar)
    }

    private fun arc(name: String, a: Arc3Input, bar: BoringBar) = Case(name, RoundCalc.arc3(a).solution!!, RoundCalc.arc3(a.copy(noseR = 0.0)).solution!!, false, bar)

    private val cases by lazy {
        listOf(
            type("A", dome, barA),
            type("B", dome.copy(d1 = 18.0), barA),
            type("C", step, barA),
            type("C 관통", step.copy(through = true), barA),
            type("C38", RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 38.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0), barA),
            type("D", dome, barA.copy(overhang = 12.0)),
            type("E", dome, barA.copy(dMin = 20.0)),
            type("F", socket, barF),
            arc("뒤보링 DMIN", back, backBar),
            arc("뒤보링 f 11", back, BoringBar(12.0, 11.0, InsertShape.C, 9.67, 95.0)),
            arc("뒤보링 f 11.5", back, BoringBar(12.0, 11.5, InsertShape.C, 9.67, 95.0)),
            arc("입구 R", Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0, zClear = 1.0),
                BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, overhang = 12.0, dMin = 20.0)),
        )
    }

    private fun plan(c: Case, safe: Double) = HolderStop.plan(c.sol, c.drawn, r, c.face, c.bar, safe)

    private fun near(z: Double, xDia: Double, p: Pt?, tag: String, tol: Double = 3e-3) {
        assertNotNull(p, tag)
        assertTrue(abs(p.z - z) <= tol && abs(p.xDia - xDia) <= tol, "$tag: 기대 Z$z X$xDia, 실제 Z${p.z} X${p.xDia}")
    }

    /** 손 계산 예마다 안전 여유 0 · 0.5 에서 검사기(0.002 mm 표본) */
    @Test
    fun handCasesHoldProperties() {
        val fails = mutableListOf<String>()
        for (c in cases) for (safe in listOf(0.0, 0.5)) {
            val p = plan(c, safe)
            val f = mutableListOf<String>()
            StopChecker(c.sol, c.drawn, r, c.face, c.bar, safe).check(p, 0.002, f)
            fails += f.map { "${c.name} 안전 여유 $safe: $it" }
        }
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    /** 끝점이 생기는지 · 경고 · 후퇴 방식(사례마다 기대) */
    @Test
    fun handCasesOutcome() {
        fun outcome(name: String, safe: Double): String {
            val p = plan(cases.single { it.name == name }, safe)
            val e = p.end ?: return "없음 ${p.warnings}"
            return "${e.element} ${e.part}${if (e.opposite) " 맞은편" else ""} ${p.direct?.retract} ${p.machine?.retract} ${p.warnings}"
        }
        // 여유 0: 구 위에서 노즈가 +Z 로 바로 떨어지고 맞은편 여유 ≥ 0 이라 Z 로만 뺀다. 0.5: 축 쪽으로 빼면 바 맞은편이 0.5 아래 → 온 길을 거꾸로
        assertEquals("2 BAR 맞은편 LIFT LIFT []", outcome("A", 0.0))
        assertEquals("2 BAR 맞은편 REVERSE REVERSE []", outcome("A", 0.5))
        // 바가 처음부터 Ø18 맞은편 벽에 가까워 X 를 뺄 수 없고, Z 로만 빼면 노즈가 벽에 끌린다
        assertEquals("0 BAR 맞은편 REVERSE REVERSE []", outcome("B", 0.0))
        assertEquals("0 BAR 맞은편 REVERSE REVERSE []", outcome("B", 0.5))
        // 바닥 면에서는 노즈가 +Z 로 바로 떨어져 X 를 빼지 않고 Z 로만 뺀다
        assertEquals("3 BAR 맞은편 LIFT LIFT []", outcome("C", 0.0))
        assertEquals("3 BAR 맞은편 LIFT LIFT []", outcome("C", 0.5))
        assertEquals("없음 []", outcome("C 관통", 0.0))
        assertEquals("없음 []", outcome("C 관통", 0.5))
        assertEquals("없음 []", outcome("C38", 0.0))
        assertEquals("3 BAR 맞은편 LIFT LIFT []", outcome("C38", 0.5))
        assertEquals("2 BAR 맞은편 LIFT LIFT []", outcome("D", 0.0))
        assertEquals("2 BAR 맞은편 REVERSE REVERSE []", outcome("D", 0.5))
        assertEquals("0 MIN_BORE LIFT LIFT []", outcome("E", 0.0))
        // Ø20 구멍에 DMIN 20 은 0.5 여유가 없어 입구 면에서 멈춘다 — 깎는 것이 없다. 기계 보정도 보정 시작 블록에서 같은 노즈 자리(헤드가 입구 면에 닿음)에서 멈춘다
        assertEquals("-1 MIN_BORE null null [NOTHING_CUT, MACHINE_ENTRY]", outcome("E", 0.5))
        assertEquals(-1, plan(cases.single { it.name == "E" }, 0.5).machineEnd!!.element)
        assertEquals("0 INSERT 맞은편 REVERSE REVERSE []", outcome("F", 0.0))
        assertEquals("0 INSERT 맞은편 REVERSE REVERSE []", outcome("F", 0.5))
        assertEquals("0 MIN_BORE LIFT LIFT []", outcome("뒤보링 DMIN", 0.0))
        assertEquals("0 MIN_BORE LIFT LIFT []", outcome("뒤보링 DMIN", 0.5))
        // 바가 Ø20 입구를 못 지난다 — 기계 보정도 제 진입(보정 시작 블록)에서 바가 닿아 멈춘다
        assertEquals("없음 [UNSAFE_AT_ENTRY, MACHINE_ENTRY]", outcome("뒤보링 f 11", 0.0))
        // f 11.5: 바 여유 0.1 — 여유 0 이면 끝까지 안전, 0.5 면 진입점부터 모자람
        assertEquals("없음 []", outcome("뒤보링 f 11.5", 0.0))
        assertEquals("없음 [UNSAFE_AT_ENTRY, MACHINE_ENTRY]", outcome("뒤보링 f 11.5", 0.5))
        // DMIN 20 = 구멍 Ø20: CAD 점 반올림으로 끝 접선이 조금 기울어 늘인 구멍이 Ø20 보다 좁아지는 곳에서 멈춘다
        assertEquals("0 MIN_BORE LIFT LIFT []", outcome("입구 R", 0.0))
    }

    /**
     * A 돔 바닥: 안전 여유 0 의 끝점 = 바 맞은편 여유 0 이 되는 곳 Z−11.404 X18.014(설계 문서), 0.5 는 따로 이분법으로 찾은 곳(1e-6 mm).
     * 잘린 NC: 앞 4 줄 그대로 + 구 원호를 끝점에서 끊은 G03(같은 R) + 거꾸로 후퇴. 기계 보정은 도면 R12.5 를 끊고 G40 으로 인선 자리
     */
    @Test
    fun caseAStop() {
        val c = cases.single { it.name == "A" }
        val p0 = plan(c, 0.0)
        val e0 = p0.end!!
        near(-11.404, 18.014, e0.pose.tip, "여유 0 끝점")
        assertEquals(0.0, e0.margin, 1e-5)
        // 0.5: 바 맞은편 여유가 0.5 가 되는 곳을 요소 2 위에서 이분법(경로 계산 따로)
        val p5 = plan(c, 0.5)
        val e5 = p5.end!!
        val seg = c.sol.program.segs[2]
        val bore = Bore(c.sol.drawing, true)
        val tool = BarShape(barA, r, false)
        fun barM(t: Double) = HolderCheck.margins(bore, tool, at(seg, t) - NoseComp.tipVector(2, r)).bar.m
        var lo = 0.0
        var hi = p5.result.hits.first().fromT
        repeat(80) {
            val mid = (lo + hi) / 2
            if (barM(mid) < 0.5) hi = mid else lo = mid
        }
        assertEquals(2, e5.element)
        assertEquals(lo * len(seg), e5.t * len(seg), 1e-6)
        assertEquals(0.5, e5.margin, 1e-5)
        near(-10.875, 19.0, e5.pose.tip, "여유 0.5 끝점")
        assertEquals(4, e5.at.directLine)
        assertEquals(4, e5.at.machineLine)
        // 기계 보정 명령점 + r·법선 = 노즈 중심(구 중심 (−2.5, 0) 에서 R12.5 → 노즈는 R12.1)
        val m = e5.at.machine!!
        val o = Pt(-2.5, 0.0)
        assertEquals(12.5, (m - o).length, 1e-9)
        assertTrue((o + (m - o) * (12.1 / 12.5) - e5.pose.nose).length < 1e-9)
        val d = p5.direct!!
        assertEquals(c.sol.nc.take(4), d.nc.take(4))
        assertEquals(listOf("G03 X19.001 Z-10.874 R12.1", "G02 X19.739 Z-10.432 R12.1", "G02 X20. Z-10.059 R0.6", "G01 Z-0.4", "G01 Z0.6"), d.nc.drop(4))
        val mc = p5.machine!!
        assertEquals(c.drawn.nc.take(4), mc.nc.take(4))
        assertEquals("G03 X18.803 Z-10.738 R12.5", mc.nc[4])
        assertTrue(mc.nc[5].startsWith("G40 G01 "), mc.nc[5])
    }

    /** C 막힌 바닥: 바닥 면을 축 쪽으로 깎다가 바가 Ø30 맞은편 벽에서 안전 여유 — 인선 반경 = f + D/2 − D1/2 + 안전 여유 */
    @Test
    fun caseCStop() {
        val c = cases.single { it.name == "C" }
        for (safe in listOf(0.0, 0.5)) {
            val p = plan(c, safe)
            val e = p.end!!
            assertEquals(3, e.element)
            near(-16.0, 2 * (11.0 + 8.0 - 15.0 + safe), e.pose.tip, "끝점 $safe", 1e-6)
            // 0.001 로 쓴 X 가 끝점을 넘지 않게(축 쪽으로 가는 면이라 X 를 크게) 한 칸 뒤로
            val d = p.direct!!
            assertEquals("G01 X${Fmt.nc(2 * (4.0 + safe) + 0.002)}", d.nc[d.retractFrom - 1])
            assertEquals(listOf("G00 Z0.6"), d.nc.drop(d.retractFrom))
            val m = p.machine!!
            // 기계 보정: 면 위 도면 점(노즈 Z−15.6 의 발 Z−16) — 블록 끝 법선(+Z)으로 r 보정되면 노즈가 끝점
            assertEquals("G01 X${Fmt.nc(2 * (3.6 + safe) + 0.002)}", m.nc[m.retractFrom - 1])
            assertEquals("G40 G00 X${Fmt.nc(2 * (4.0 + safe) + 0.002)} Z1.", m.nc[m.retractFrom])
        }
    }

    /** E: 헤드(인선 뒤 insZ)가 모서리 R 시작(Z−9.659)에 닿기 직전이 끝점. 안전 여유 0.5 는 Ø20 = DMIN 이라 입구 면에서 */
    @Test
    fun caseEStop() {
        val c = cases.single { it.name == "E" }
        val e0 = plan(c, 0.0).end!!
        near(-9.659, 20.0, e0.pose.tip, "끝점")
        assertEquals(HolderPart.MIN_BORE, e0.part)
        val p5 = plan(c, 0.5)
        val e5 = p5.end!!
        assertEquals(-1, e5.element)
        near(0.0, 20.0, e5.pose.tip, "입구 면", 1e-6)
        assertNull(p5.direct)
        assertNull(p5.machine)
    }

    /** 뒤보링: 헤드(인선 −Z 쪽 insZ)의 가장 좁은 곳 − DMIN/2 = 안전 여유가 되는 곳을 따로 이분법 */
    @Test
    fun backBoringStop() {
        val c = cases.single { it.name == "뒤보링 DMIN" }
        val seg = c.sol.program.segs[0]
        val bore = Bore(c.sol.drawing, false)
        val tool = BarShape(backBar, r, true)
        fun minM(t: Double) = HolderCheck.margins(bore, tool, at(seg, t) - NoseComp.tipVector(1, r)).minBore.m
        for (safe in listOf(0.0, 0.5)) {
            val e = plan(c, safe).end!!
            var lo = 0.0
            var hi = 1.0
            repeat(80) {
                val mid = (lo + hi) / 2
                if (minM(mid) < safe) hi = mid else lo = mid
            }
            assertEquals(lo * len(seg), e.t * len(seg), 1e-6, "안전 여유 $safe")
            assertEquals(safe, e.margin, 1e-5)
        }
    }

    /** 닿는 곳 좌표(기존 표 ±0.003)를 두 프로그램으로 · 줄 번호 · 처음 닿는 곳의 정밀도(1e-6 mm 앞은 닿지 않음) */
    @Test
    fun hitCoordinatesBothPrograms() {
        val a = plan(cases.single { it.name == "A" }, 0.5)
        val bar = a.hits.single { it.hit.part == HolderPart.BAR }
        near(-11.409, 18.006, bar.first.direct, "A 바 처음")
        assertEquals(4, bar.first.directLine)
        assertEquals(bar.hit.first.drawn, bar.first.machine)
        assertEquals(4, bar.first.machineLine)
        near(-14.998, 1.238, a.hits.single { it.hit.part == HolderPart.INSERT }.first.direct, "A 인서트 처음")
        near(-12.005, 16.739, plan(cases.single { it.name == "D" }, 0.0).hits.single { it.hit.part == HolderPart.TURRET }.first.direct, "D 공구대")
        near(-9.759, 20.0, plan(cases.single { it.name == "E" }, 0.0).hits.first().first.direct, "E 최소 가공경")
        val f = plan(cases.single { it.name == "F" }, 0.0)
        val fb = f.hits.single { it.hit.part == HolderPart.BAR }
        near(-6.015, 11.010, fb.first.direct, "F 바 처음")
        assertEquals(4, fb.first.directLine)
        assertEquals(5, fb.last.directLine)
        // 기계 보정 줄: 도면 좌표 풀이의 같은 요소 줄
        val drawnF = cases.single { it.name == "F" }.drawn
        assertEquals(drawnF.lineOf(2, fb.hit.first.drawn), fb.first.machineLine)
        // 처음 닿는 곳: 그 자세는 닿고(여유 < −0.005) 1e-6 mm 앞은 닿지 않는다
        for (c in cases.filter { it.name in setOf("A", "B", "C", "D", "E", "F") }) {
            for (h in plan(c, 0.0).result.hits.filter { it.fromT > 0 && it.element >= 0 }) {
                val (on, before) = contactEdge(c.sol, r, c.face, c.bar, h.element, h.fromT, h.part, false)
                assertTrue(on < -HolderCheck.CONTACT_TOL && before >= -HolderCheck.CONTACT_TOL, "${c.name} ${h.part} 요소 ${h.element}: $on · $before")
            }
        }
    }

    /**
     * 기계 보정 진입(10-28 검증): 비표준 팁이면 도면 G00 점에 선 노즈(= 점 − 팁 벡터)가 이미 소재에 박힌다 — 기계 보정 프로그램은 그 자리부터 멈추고
     * (끝점 −2, MACHINE_ENTRY, 잘린 NC 없음) 직접 보정은 따로 본다. 손 계산: TYPE 6 입구 Ø12.756 · 팁 4 (+r, −r) · Z 여유 0.069 → 노즈 (Z−0.731, 반경 7.178),
     * 입구 벽 반경 6.378 보다 0.8 위
     */
    @Test
    fun machineEntryNonStandardTip() {
        val inp = RoundInput(type = 6, mainR = 4.326, cornerR1 = 0.0, cornerR2 = 0.0, d1 = 3.41, d2 = 12.756, l1 = 1.322, l2 = 1.585, noseR = 0.8, tip = 4, zClear = 0.069, legLen = 4.89, inner = true)
        val sol = RoundCalc.calc(inp).solution!!
        val drawn = RoundCalc.calc(inp.copy(noseR = 0.0)).solution!!
        val bar = BoringBar(7.251, 5.113, InsertShape.T, 4.285, 92.496)
        val p = HolderStop.plan(sol, drawn, 0.8, true, bar, 0.5)
        val me = p.machineEnd!!
        assertEquals(-2, me.element)
        assertTrue(StopWarning.MACHINE_ENTRY in p.warnings, "${p.warnings}")
        assertNull(p.machine)
        near(0.069, 12.756, me.at.machine, "기계 보정 G00 점")
        assertEquals(0, me.at.machineLine)
        assertTrue((me.pose.nose - Pt(0.069 - 0.8, 12.756 / 2 + 0.8)).length < 1e-9, "${me.pose.nose}")
        // 그 자세에서 인서트가 입구 벽에 박힌다 — 기계 보정 자기 진입의 간섭 목록(−2)
        val g00 = p.machineEntry!!.single { it.hit.element == -2 && it.hit.part == HolderPart.INSERT }
        assertTrue(g00.hit.depth > 0.5, "${g00.hit.depth}")
        near(0.069, 12.756, g00.first.machine, "간섭 목록 G00")
        val fails = mutableListOf<String>()
        StopChecker(sol, drawn, 0.8, true, bar, 0.5).check(p, 0.01, fails)
        assertTrue(fails.isEmpty(), "$fails")
    }

    /**
     * 표준 팁 뒤보링 3점호: 진입은 시작 접선을 거꾸로 따라간다. 직접 보정은 노즈 원이 그 접선에 접한 채 들어와 끝까지 안전한데, 기계 보정 G00 점(접선 위의
     * 도면 점)에 선 노즈는 팁 1 이라 접선에서 공구 쪽으로 |n · 팁 벡터| 만 떨어져 늘인 접선(소재)을 판다 — 따로 잰 노즈 중심 ~ 접선 거리 < r
     */
    @Test
    fun machineEntryReversedTangent() {
        val a = Arc3Input(inner = true, noseR = 0.4, x1 = 39.601, z1 = -35.029, x2 = 47.115, z2 = -29.633, x3 = 48.379, z3 = -28.385, zClear = 0.287)
        val sol = RoundCalc.arc3(a).solution!!
        val drawn = RoundCalc.arc3(a.copy(noseR = 0.0)).solution!!
        val bar = BoringBar(30.058, 18.958, InsertShape.V, 5.913, 98.556)
        val p = HolderStop.plan(sol, drawn, 0.4, false, bar, 0.0)
        assertNull(p.end, "직접 보정은 끝까지 안전")
        val arc = drawn.drawing.segs.first() as Arc
        val t0 = (arc.start - arc.center).unit().left() * (if (arc.cw) -1.0 else 1.0)
        val nose = drawn.entry - NoseComp.tipVector(sol.tip, 0.4)
        val off = abs((nose - arc.start) cross t0)
        assertTrue(off < 0.4 - 0.3, "노즈 중심 ~ 시작 접선 $off")
        assertEquals(-2, p.machineEnd!!.element)
        assertEquals(listOf(StopWarning.MACHINE_ENTRY), p.warnings)
        assertNull(p.machine)
    }

    /**
     * 진입 이동에서 닿는 곳의 기계 보정 좌표 = 기계 보정 프로그램 자기 경로(G00 점 노즈 → 첫 보정선 시작으로 곧게, 명령점과 같은 비율)에서 처음 닿는 명령점.
     * 뒤보링 f 11: 직접 보정 비율을 옮긴 예전 값 Z−21.000 이 아니라 Z−20.9875(검증 단계 표본) — 여기서는 따로 이분법
     */
    @Test
    fun machineEntryHitCoordinates() {
        val c = cases.single { it.name == "뒤보링 f 11" }
        val p = plan(c, 0.0)
        val h = p.hits.single { it.hit.element == -1 && it.hit.part == HolderPart.BAR }
        val tool = BarShape(c.bar, r, true)
        val bore = Bore(c.sol.drawing, false)
        val tv = NoseComp.tipVector(c.sol.tip, r)
        val a = c.drawn.entry - tv
        val first = c.drawn.drawing.segs.first() as Arc
        val n = if ((c.sol.side == Side.G41) == first.cw) 1.0 else -1.0
        val b = first.start + (first.start - first.center).unit() * (r * n)
        fun touch(u: Double) = HolderCheck.margins(bore, tool, a + (b - a) * u).bar.m < -HolderCheck.CONTACT_TOL
        assertTrue(!touch(0.0), "기계 보정 G00 점 자세는 바가 안 닿는다")
        var lo = 0.0
        var hi = 1.0
        repeat(60) {
            val mid = (lo + hi) / 2
            if (touch(mid)) hi = mid else lo = mid
        }
        val cmd = c.drawn.entry + (c.drawn.program.segs.first().start - c.drawn.entry) * hi
        assertTrue((h.first.machine!! - cmd).length < 1e-6, "${h.first.machine} ≠ $cmd")
        near(-20.9875, 30.0, h.first.machine, "뒤보링 f 11 진입 이동 바", 2e-3)
        assertEquals(1, h.first.machineLine)
        // 기계 보정 G00 점에서는 바가 안 닿아 직접 보정 진입점(−2)의 기계 좌표는 없다 · 기계 보정 자기 진입 목록에도 −2 가 없다
        assertNull(p.hits.single { it.hit.element == -2 && it.hit.part == HolderPart.BAR }.first.machine)
        assertTrue(p.machineEntry!!.none { it.hit.element == -2 }, "${p.machineEntry!!.map { it.hit.element to it.hit.part }}")
        assertTrue((p.machineEntry!!.single { it.hit.part == HolderPart.BAR }.first.machine!! - cmd).length < 1e-6)
    }

    /**
     * 바와 소재 직선 거리(반경 여유와 다름 — 10-28 검증 예 B · C). B: 끝점 자세에서 바 앞면(N 을 지나는 주절삭날 연장선)과 맞은편 입구 모서리 (Z0, −9)의
     * 수선 길이(반경 여유 0.5 인데 0.04 대). C(안전 여유 0.5): 바 위 앞 끝 E(top) 과 바닥 면 Z−16 의 거리 r + E.z = 0.226(바닥 단면을 깎는 내내 —
     * 바 아래는 맞은편 Ø30 벽에서 0.5). 안전 여유 0 이면 바 아래가 맞은편 벽에 붙어 0
     */
    @Test
    fun straightGapAtEnd() {
        val k = 95.0 * kotlin.math.PI / 180
        val e1 = Pt(-kotlin.math.cos(k), -kotlin.math.sin(k))
        val eps = 80.0 * kotlin.math.PI / 180
        val e2 = Pt(kotlin.math.cos(kotlin.math.PI - k - eps), -kotlin.math.sin(kotlin.math.PI - k - eps))
        val nVirt = (e1 + e2).unit() * (-r / kotlin.math.sin(eps / 2))
        val eb = plan(cases.single { it.name == "B" }, 0.5).end!!
        val corner = Pt(0.0, -9.0)
        val v = corner - (eb.pose.nose + nVirt)
        val expect = abs(e1 cross v)
        assertEquals(expect, eb.gap, 1e-9)
        assertTrue((eb.gapAt - corner).length < 1e-9, "${eb.gapAt}")
        assertTrue(eb.gap < 0.05 && abs(eb.margin - 0.5) < 1e-5, "직선 ${eb.gap} · 반경 ${eb.margin}")
        val top = r - 11.0 + 8.0
        val eTop = nVirt + e1 * ((nVirt.x - top) / kotlin.math.sin(k))
        val ec = plan(cases.single { it.name == "C" }, 0.5).end!!
        assertEquals(r + eTop.z, ec.gap, 1e-9)
        assertEquals(-16.0, ec.gapAt.z, 1e-9)
        assertEquals(0.0, plan(cases.single { it.name == "C" }, 0.0).end!!.gap, 1e-6)
    }

    @Test
    fun badSafeValue() {
        val c = cases.single { it.name == "A" }
        for (v in listOf(-0.1, Double.NaN, Double.POSITIVE_INFINITY, 2 * MAX_MM)) {
            assertEquals(HolderSkip.BAD_INPUT, plan(c, v).result.skip, "$v")
            assertTrue(!HolderStop.safeOk(v))
        }
        assertTrue(HolderStop.safeOk(0.0) && HolderStop.safeOk(0.5))
        // 기본값 0.5
        assertEquals(plan(c, 0.5).end, HolderStop.plan(c.sol, c.drawn, r, true, barA).end)
    }

    /** 원호를 끊어 쓰기: 끝까지면 [NcLines.arc] 와 같은 글, 가운데에서 끊으면 앞 조각은 그 글 그대로 */
    @Test
    fun arcUntilKeepsPieces() {
        val c = Pt(0.0, 0.0)
        fun p(deg: Double) = Pt(kotlin.math.cos(deg * kotlin.math.PI / 180), kotlin.math.sin(deg * kotlin.math.PI / 180)) * 10.0
        val a = Arc(p(10.0), p(200.0), c, cw = false)
        val full = NcLines().apply { move("G00", a.start); arc(a) }
        val until = NcLines().apply { move("G00", a.start); arcUntil(a, a.end) }
        assertEquals(full.lines, until.lines)
        assertEquals(full.refs, until.refs)
        // 190° → 3 조각(63.3°). 150° 에서 끊으면 앞 두 조각 그대로 + 150° 까지
        val cut = NcLines().apply { move("G00", a.start); arcUntil(a, p(150.0)) }
        assertEquals(full.lines.take(3), cut.lines.take(3))
        assertEquals("G03 X${Fmt.nc(p(150.0).xDia)} Z${Fmt.nc(p(150.0).z)} R10.", cut.lines[3])
        // 끝을 조금 넘어도(볼록 모서리에서 늘인 보정선) 마지막 조각이 늘어난다
        val over = NcLines().apply { move("G00", a.start); arcUntil(a, p(203.0)) }
        assertEquals(full.lines.take(3), over.lines.take(3))
        assertEquals(4, over.lines.size)
    }

    /** 글 다시 읽기: 모달 축 · R 원호 중심 · G40 */
    @Test
    fun readNcRoundTrip() {
        val read = readNc(listOf("G00 X20. Z1.", "G01 Z-5.", "G03 X30. Z-10. R5.", "G40 G01 X28."))
        assertNull(read[0])
        assertEquals(Line(Pt(1.0, 10.0), Pt(-5.0, 10.0)), read[1])
        val a = read[2] as Arc
        assertTrue((a.center - Pt(-10.0, 10.0)).length < 1e-12, "${a.center}")
        assertTrue(!a.cw)
        assertEquals(Line(Pt(-10.0, 15.0), Pt(-10.0, 14.0)), read[3])
    }
}
