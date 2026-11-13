package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.verify.GeometricVerifyTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 라운드 가공 방향 뒤집기([RoundInput.flip] · [Arc3Input.flip]) — 사용자 요청 2026-11-10(피드백 2: "G41 말고 G42 계산법도").
 * 예제 A~F 의 기대 글은 앱 코드와 따로 기하에서 손 계산한 값이다(설계 단계의 py 스크립트 — docs/calc-design.md '라운드 가공 방향 뒤집기').
 * 진입 줄만은 2026-11-11 급송 여유 고침 뒤 규칙으로 다시 손 계산했다 — 기계 보정의 막힌 시작: 급송 X = D ± 2(2r + c) · Z = 시작 Z + r + c 뒤 `G1 Z(시작) F.1` · `X(D)`,
 * 축에서 시작: 급송 Z = 첫 깎는 점 + 2r + c. 직접 보정의 막힌 시작: 급송 끝 = 첫 보정점에서 X ± 2c · Z + c 뒤 비스듬한 `G1` 한 줄.
 * 기계 보정 프로그램을 시뮬레이터로 돌려 보는 검증은 `verify/CompanyFrameVerifyTest.randomFlippedRoundsFollowCompanyFrame`
 */
class RoundFlipTest {
    /** 예제 A: 외경 형상 3 — D20 · 주 R12.5 · 모서리 R1 · 끝 직선 5 */
    private val a = RoundInput(type = 3, mainR = 12.5, cornerR1 = 1.0, d1 = 20.0, noseR = 0.4, zClear = 1.0, legLen = 5.0, flip = true)

    /** 예제 B: 내경 형상 3 — 같은 치수, 입구 구멍 깊이 10 */
    private val b = a.copy(legLen = 10.0, inner = true)

    /** 예제 C: 외경 형상 5 — 주 R18 · D2 30 · D1 10 · L1 5 · 모서리 R 1 · 1 · 끝 직선 5 (L2 자동 7.342) */
    private val c = RoundInput(type = 5, mainR = 18.0, cornerR1 = 1.0, cornerR2 = 1.0, d1 = 10.0, d2 = 30.0, l1 = 5.0, noseR = 0.4, zClear = 1.0, legLen = 5.0, flip = true)

    /** 예제 D: 내경 형상 5 관통 — 주 R25 · D2 40 · D1 30 · L1 6 · 입구 구멍 깊이 5. D2 = 같은 치수 막힘 */
    private val d = RoundInput(
        type = 5, mainR = 25.0, cornerR1 = 0.0, cornerR2 = 0.0, d1 = 30.0, d2 = 40.0, l1 = 6.0, noseR = 0.4, zClear = 1.0, legLen = 5.0,
        inner = true, through = true, flip = true,
    )

    /** 예제 F: 외경 형상 2(아령) — 주 R10 · D1 10 · L1 5 · 모서리 R1 · 끝 직선 5. 공 뒤 축에서 시작해 접근이 공을 넘는다 */
    private val f = RoundInput(type = 2, mainR = 10.0, cornerR1 = 1.0, d1 = 10.0, l1 = 5.0, noseR = 0.4, zClear = 1.0, legLen = 5.0, flip = true)

    private fun direct(i: RoundInput) = assertNotNull(RoundCalc.calc(i).solution, "$i")
    private fun machine(i: RoundInput) = assertNotNull(RoundCalc.machine(i).solution, "$i")

    @Test
    fun outerDomeFlippedIsG41FromChuckSide() {
        val s = direct(a)
        assertEquals(Side.G41, s.side)
        assertEquals(3, s.tip)
        assertTrue(s.flip)
        // 척 쪽 끝 직선의 끝(Z-10)에서 인선이 시작 좌표에 서고(노즈가 넘지 않음), 앞 끝면 바깥(Z1)에서 X 를 맞춘 뒤 Z 로 들어가 벽 앞 c(Z-9)에서 멈춘다 —
        // 거기서 비스듬한 이송 한 줄로 닿는다
        assertEquals(listOf("G0X22.Z1.", "Z-9.", "G1X20.Z-10.", "Z-5.741", "G2X19.391Z-4.87R1.4", "X-.8Z0.R12.9"), s.nc)
        // 기계 보정: 급송은 노즈 밑이 깎을 면(Ø20) 위 c · 벽(Z-10) 앞 c 인 자리(X23.6 = 20 + 2(2r + c), Z-8.6 = -10 + r + c)까지, 이송으로 벽 쪽 → 벽을 따라 Ø20 으로
        assertEquals(
            listOf("G41", "G0X23.6Z20.", "Z-8.6", "G1Z-10.F.1", "X20.", "Z-5.341", "G2X19.565Z-4.719R1.", "X0.Z0.R12.5", "G1X-.82F.3", "Z.986", "G40", "G0Z200."),
            machine(a).nc,
        )
    }

    @Test
    fun innerDomeFlippedIsG42FromBottom() {
        val s = direct(b)
        assertEquals(Side.G42, s.side)
        assertEquals(2, s.tip)
        // 돔 바닥(축)에서 시작 — 축을 따라 들어가 Z 로 c 를 이송으로 내려간다
        assertEquals(listOf("G0X.8Z1.", "Z-14.", "G1Z-15.", "G2X19.739Z-10.432R12.1", "X20.Z-10.059R.6", "G1Z-.4"), s.nc)
        // 기계 보정: 시작 접선 위 축 너머 r + 0.01 로 들어가 접선 한 줄(X0.) 뒤 원호, 끝은 입구 끝면 너머로 곧게 c. 급송은 그 자리 위 2r + c(Z-13.2)에서 멈춘다
        assertEquals(
            listOf("G42", "G0X-.82Z20.", "Z-13.2", "G1Z-15.F.1", "X0.", "G2X19.565Z-10.281R12.5", "X20.Z-9.659R1.", "G1Z0.", "Z1.F.3", "G40", "G0Z200."),
            machine(b).nc,
        )
    }

    @Test
    fun outerType5Flipped() {
        assertEquals(
            listOf("G0X32.Z1.", "Z-16.342", "G1X30.Z-17.342", "Z-13.048", "G2X29.506Z-12.254R1.4", "X10.821Z-5.233R18.4", "G3X10.Z-4.664R.6", "G1Z0.", "X-.8"),
            direct(c).nc,
        )
        assertEquals(
            listOf(
                "G41", "G0X33.6Z20.", "Z-15.942", "G1Z-17.342F.1", "X30.", "Z-12.648", "G2X29.647Z-12.081R1.", "X11.368Z-5.213R18.", "G3X10.Z-4.264R1.", "G1Z0.", "X-.8",
                "Z1.F.3", "G40", "G0Z200.",
            ),
            machine(c).nc,
        )
    }

    @Test
    fun innerType5FlippedThroughAndBlind() {
        // 관통: D1 구멍의 끝(Z-16) 너머는 같은 지름의 구멍(사용자 답 2026-11-13 — 벽이 아님). 직접 보정은 첫 보정점이 기본 방향의 끝 보정점(인선 Z-16.4 —
        // 노즈가 Z-16 에 접함)이고, 급송은 그 노즈에서 너머 c · 축 쪽 c(노즈 중심 X27.2 Z-17 → 인선 X28. Z-17.4)에서 멈춰 비스듬한 이송 한 줄로 닿는다
        assertEquals(listOf("G0X28.Z1.", "Z-17.4", "G1X30.Z-16.4", "Z-10.199", "G2X40.Z-5.266R24.6", "G1Z-.4"), direct(d).nc)
        // 기계 보정: 급송은 시작 좌표 너머 r + c(Z-17.4) · 축 쪽 2r + c(X26.4)까지 → 이송으로 구멍 벽 선(X30.) — 노즈가 너머 c(Z-17)에서 구멍 연장선에 닿음 →
        // 벽을 따라 시작 좌표(Z-16.)로 → 형상
        assertEquals(listOf("G42", "G0X26.4Z20.", "Z-17.4", "G1X30.F.1", "Z-16.", "Z-10.", "G2X40.Z-5.R25.", "G1Z0.", "Z1.F.3", "G40", "G0Z200."), machine(d).nc)
        // 기본 방향(입구 → 관통 끝)의 기계 보정 끝도 막힌 끝이 아니다 — 끝 좌표 너머로 c(Z-17.) 지나간 뒤 G40 자리(둘레 (1 + √2)r + 0.02 = 0.986 에 소재
        // 없음)까지 축 쪽으로(X30 − 2 × 0.986 = X28.029)
        assertEquals(listOf("G1Z-16.", "Z-17.F.3", "X28.029", "G40", "G0Z200."), machine(d.copy(flip = false)).nc.takeLast(5))
        // 막힘: 바닥 면의 축에서 — 면을 축 너머 r(X-.8)부터 쓰고 그 자리로 Z 이송
        val blind = d.copy(through = false)
        assertEquals(listOf("G0X.8Z1.", "Z-15.", "G1Z-16.", "X30.", "Z-10.199", "G2X40.Z-5.266R24.6", "G1Z-.4"), direct(blind).nc)
        assertEquals(
            listOf("G42", "G0X-.8Z20.", "Z-14.2", "G1Z-16.F.1", "X30.", "Z-10.", "G2X40.Z-5.R25.", "G1Z0.", "Z1.F.3", "G40", "G0Z200."),
            machine(blind).nc,
        )
    }

    @Test
    fun dumbbellFlippedApproachGoesOverTheBall() {
        // 직접 보정: 공(Ø20)보다 바깥 X22 에서 Z 를 맞춘 뒤 X 로 진입점에
        assertEquals(
            listOf(
                "G0X22.Z1.", "Z-26.321", "X12.", "G1X10.Z-27.321", "Z-23.28", "G3X10.545Z-22.777R.6", "G2X20.Z-14.06R10.4", "X10.545Z-5.344R10.4", "G3X10.Z-4.841R.6",
                "G1Z0.", "X-.8",
            ),
            direct(f).nc,
        )
        // 기계 보정도 공을 넘어 온다(손 계산의 단순 규칙은 X12 로 곧장 — 공을 지나므로 틀렸다) — 노즈 밑이 공(Ø20) 위 c 인 X23.6 에서 Z 를 맞추고 X 로 내려온다
        assertEquals(listOf("G41", "G0X23.6Z20.", "Z-25.921", "X13.6", "G1Z-27.321F.1", "X10.", "Z-22.88"), machine(f).nc.take(7))
        assertEquals(listOf("G1Z0.", "X-.8", "Z1.F.3", "G40", "G0Z200."), machine(f).nc.takeLast(5))
    }

    /** 무작위 입력(형상 1~6 외 · 내경 · 관통 · 팁): 뒤집으면 보정 쪽만 반대이고 노드는 같은 점의 역순 — 막힌 시작의 첫 보정점만 노즈가 시작 좌표에 닿는 자리 */
    @Test
    fun flippedNodesAreTheSamePointsReversed() {
        val rnd = Random(20261110)
        val g = GeometricVerifyTest()
        var solved = 0
        var walls = 0
        var short = 0
        repeat(600) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val inp = g.randomRound(type, rnd, inner).input
            val base = RoundCalc.calc(inp)
            val flip = RoundCalc.calc(inp.copy(flip = true))
            val bs = base.solution
            val fs = flip.solution
            if (bs == null) {
                assertNull(fs, "$inp")
                assertEquals(base.warnings, flip.warnings, "$inp")
                return@repeat
            }
            if (fs == null) {
                // 뒤집어서만 멈추는 것은 짧은 막힌 시작뿐(끝 직선의 노즈 길이 모자람)
                assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), flip.warnings, "$inp")
                val last = bs.drawing.segs.last()
                assertTrue(last is Line && last.end.z < last.start.z, "$inp")
                assertNull(RoundCalc.machine(inp.copy(flip = true)).solution, "$inp")
                short++
                return@repeat
            }
            solved++
            assertEquals(if (bs.side == Side.G41) Side.G42 else Side.G41, fs.side, "$inp")
            assertEquals(bs.tip, fs.tip, "$inp")
            assertEquals(bs.filled, fs.filled?.copy(flip = false), "$inp")
            assertEquals(bs.nodes.size, fs.nodes.size, "$inp")
            val tv = NoseComp.tipVector(fs.tip, inp.noseR)
            val first = fs.drawing.segs.first()
            val t0 = CompFrame.startDir(first)
            // 관통 구멍에서 시작하면 그 너머는 구멍이라 막힌 시작이 아니다(사용자 답 2026-11-13 — RoundThroughTest)
            val wall = first is Line && abs(t0.z) > abs(t0.x) && t0.z > 0 && !inp.through
            if (wall) walls++
            for ((k, n) in fs.nodes.withIndex()) {
                val m = bs.nodes[bs.nodes.lastIndex - k]
                assertTrue((n.drawn - m.drawn).length < 1e-9, "$inp 노드 $k 도면점")
                if (k == 0 && wall) {
                    val nose = n.prog - tv - first.start
                    assertEquals(inp.noseR, nose dot t0, 1e-9, "$inp 막힌 시작: 노즈가 시작 좌표의 법선에 닿는다")
                    assertEquals(inp.noseR, nose dot CompFrame.normal(t0, fs.side), 1e-9, "$inp 막힌 시작: 노즈가 첫 직선에 닿는다")
                } else {
                    assertTrue((n.prog - m.prog).length < 1e-9, "$inp 노드 $k 보정점 ${n.prog} / ${m.prog}")
                }
            }
            for ((k, arc) in fs.arcs.withIndex()) {
                val m = bs.arcs[bs.arcs.lastIndex - k]
                assertEquals(m.r, arc.r, 1e-9, "$inp")
                assertEquals(m.progR ?: -1.0, arc.progR ?: -1.0, 1e-9, "$inp")
            }
            assertEquals(bs.arcs.map { !it.cw }.reversed(), fs.arcs.map { it.cw }, "$inp")
            // 기계 보정: 반대 보정 G 혼자 줄로 시작해 G40 혼자 줄 · G0Z200. 으로 끝난다
            val nc = machine(inp.copy(flip = true)).nc
            assertEquals(fs.side.toString(), nc.first(), "$inp")
            assertEquals(listOf("G40", "G0Z200."), nc.takeLast(2), "$inp")
        }
        println("뒤집기 불변식: 풀림 $solved (막힌 시작 $walls) · 짧은 막힌 시작으로 멈춤 $short")
        assertTrue(solved > 300 && walls > 150, "풀림 $solved 막힌 시작 $walls")
    }

    /** 끝 직선이 노즈R 쯤보다 짧으면 벽 앞에서 닿을 길이 없다 — 뒤집은 쪽만 멈춘다(직접 · 기계 둘 다) */
    @Test
    fun shortBlockedStartStops() {
        // 모서리 R 없이 끝 직선 0.3: 노즈(0.4)가 벽에 닿은 자리에서 그 직선의 노즈 길이 0.032 뿐이다
        val i = a.copy(cornerR1 = 0.0, legLen = 0.3)
        assertNotNull(RoundCalc.calc(i.copy(flip = false)).solution)
        assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), RoundCalc.calc(i).warnings)
        assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), RoundCalc.machine(i).warnings)
    }

    /** 뒤집으면 외경 · 구멍을 팁의 Z 와 반대로(Z+) 깎는다 — 끝날 쪽 깎기 경고(테이퍼 뒤집기와 같은 기준) */
    @Test
    fun flippedWarnsBackTravel() {
        assertEquals(emptyList(), RoundCalc.calc(a.copy(flip = false)).warnings)
        assertEquals(listOf(RoundWarning.AGAINST_TOOL), RoundCalc.calc(a).warnings)
        assertEquals(listOf(RoundWarning.AGAINST_TOOL), RoundCalc.calc(b).warnings)
    }

    /**
     * 3점호: 같은 바이트(넣은 순서의 표준 팁)로 끝점부터 — 보정 쪽 · 원호 방향이 반대, 보정점은 같은 점의 역순. 진입은 끝점(P3) 너머로 가지 않고 공구 쪽에서
     * (점을 거꾸로 넣은 역방향 3점호는 전처럼 접선으로 — 역방향 바이트는 그쪽에서 온다)
     */
    @Test
    fun arc3Flipped() {
        // 외경 볼록 1/4 원: X10 Z0 → X20 Z-5 (중심 X10 Z-5, R5)
        val i = Arc3Input(noseR = 0.4, x1 = 10.0, z1 = 0.0, x2 = 17.0711, z2 = -1.4645, x3 = 20.0, z3 = -5.0, zClear = 1.0)
        val base = assertNotNull(RoundCalc.arc3(i).solution)
        val flip = assertNotNull(RoundCalc.arc3(i.copy(flip = true)).solution)
        assertEquals(Side.G42, base.side)
        assertEquals(Side.G41, flip.side)
        assertEquals(3, flip.tip)
        assertTrue(flip.flip)
        assertEquals(listOf(false), base.arcs.map { it.cw })
        assertEquals(listOf(true), flip.arcs.map { it.cw })
        for (k in 0..1) {
            assertTrue((flip.nodes[k].drawn - base.nodes[1 - k].drawn).length < 1e-9)
            assertTrue((flip.nodes[k].prog - base.nodes[1 - k].prog).length < 1e-9)
        }
        // 직접 보정: 급송은 P3(X20 Z-5)의 법선 앞 c · 공구 쪽 c 에서 멈추고(Z-4 = 첫 보정점 Z-5.4 + r + c) 비스듬한 이송 한 줄로 첫 보정점에
        assertEquals(listOf("G0X22.Z1.", "Z-4.", "G1X20.Z-5.4", "G2X9.2Z0.R5.4"), flip.nc)
        // 기계 보정: 급송은 같은 자리(X23.6 Z-3.6)까지, 이송으로 P3 너머 r + 0.05 자리 위 → 접선 연장선으로 → 접선 한 줄로 P3 에(노즈가 P3 의 법선에서 원호를 탄다)
        val m = assertNotNull(RoundCalc.machine(i.copy(flip = true)).solution)
        assertEquals(listOf("G41", "G0X23.6Z20.", "Z-3.6", "G1Z-5.45F.1", "X20.", "Z-5.", "G2X10.Z0.R5.", "G1X8.F.3", "Z.986", "G40", "G0Z200."), m.nc)
        // 점을 거꾸로 넣은 역방향 3점호(팁 4)는 그대로 — 끝점 너머 접선으로
        val swapped = i.copy(x1 = i.x3, z1 = i.z3, x3 = i.x1, z3 = i.z1)
        assertEquals(listOf("G41", "G0X22.4Z20.", "Z-6.", "X20.", "G1Z-5.F.1", "G2X10.Z0.R5."), RoundCalc.machine(swapped).solution?.nc?.take(6))
        // 내경도: 넣은 순서(정방향 · 팁 2 · G41)를 뒤집으면 팁 2 그대로 G42
        val inner = RoundCalc.arc3(i.copy(inner = true, x1 = 20.0, x2 = 12.9289, x3 = 10.0, flip = true)).solution
        assertEquals(Side.G42 to 2, inner?.let { it.side to it.tip })
    }

    /** 뒤집은 풀이는 보링바 간섭 검사를 하지 않는다(말없이 뒤보링으로 보지 않게) */
    @Test
    fun holderPlanSkipsFlipped() {
        val bar = BoringBar(dia = 12.0, f = 9.0, shape = InsertShape.entries.first(), edge = 10.0, kappaDeg = 93.0)
        val plan = HolderStop.plan(direct(b), machine(b), 0.4, true, bar)
        assertEquals(HolderSkip.FLIPPED, plan.result.skip)
        assertNull(plan.direct)
        assertNull(plan.machine)
    }

    // ---------------------------------------------------------------- 직접 보정(G40) 글을 시뮬레이터로

    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    /** 요소 위 점(0.05 mm 마다) */
    private fun dense(s: Seg): List<Pt> = when (s) {
        is Line -> {
            val n = max(2, ((s.end - s.start).length / 0.05).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * s.radius / 0.05).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * s.radius }
        }
    }

    private fun shifted(s: Seg, v: Pt): Seg = when (s) {
        is Line -> Line(s.start + v, s.end + v)
        is Arc -> Arc(s.start + v, s.end + v, s.center + v, s.cw)
    }

    /**
     * 뒤집은 직접 보정 글을 시뮬레이터로 돌려(보정 없음 — 노즈 중심 = 인선 − 팁 벡터) 본다: 멈춤 0, 급송 · 이송 어느 이동도 노즈가 도면(내경은 축 거울 포함)을
     * 파지 않는다(0.001 반올림까지), 막힌 시작은 어느 이동도 노즈가 시작 좌표 너머로 가지 않는다, 깎는 줄이 기본 방향 풀이만큼 도면에 닿는다(덜 깎임 0 —
     * 막힌 시작 벽에서 r 안은 빼고: 노즈가 벽을 넘지 않아 구석 R 이 남는 자리)
     */
    @Test
    fun flippedDirectProgramCutsTheDrawing() {
        val rnd = Random(20261111)
        val g = GeometricVerifyTest()
        val tol = 2e-3
        var runs = 0
        var walls = 0
        var gouge = 0.0
        var under = 0.0
        val fails = mutableListOf<String>()
        repeat(500) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val inp = g.randomRound(type, rnd, inner).input
            val base = RoundCalc.calc(inp).solution ?: return@repeat
            val res = RoundCalc.calc(inp.copy(flip = true))
            val s = res.solution ?: return@repeat
            // 오목 R ≤ 노즈R 은 도면대로 안 나오는 꼴(경고) — 보지 않는다
            if (RoundWarning.CONCAVE_R_LE_NOSE in res.warnings) return@repeat
            val r = inp.noseR
            val tv = NoseComp.tipVector(s.tip, r)
            // 직접 보정 글에는 이송 F 가 없다 — 첫 이송 줄에 붙여 돌린다
            val g1 = s.nc.indexOfFirst { it.startsWith("G1") }
            val text = (listOf("T0101", "G97S1000M03", "G99") + s.nc.mapIndexed { k, l -> if (k == g1) l + "F.1" else l } + "M30").joinToString("\n")
            val run = GSim.run(SimInput(text, MachineSetup(), mapOf(1 to ToolOffset(r, s.tip))))
            if (run.stop != null) {
                fails += "$inp: STOP ${run.stop?.code}\n${s.nc.joinToString("\n")}"
                return@repeat
            }
            runs++
            // 첫 G0(공구 교환 자리 → 앞 끝면 바깥)은 글 밖 자리에서 오는 이동이라 빼고 본다 — 그 줄이 닿는 자리(끝점)는 다음 이동의 시작으로 본다
            val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > 4 }
            val noses = moves.map { shifted(it.tip, tv * -1.0) }
            val mat = if (inner) s.drawing.segs + s.drawing.segs.map(::mirror) else s.drawing.segs
            for ((k, n) in noses.withIndex()) {
                val d = dense(n).minOf { p -> mat.minOf { distTo(it, p) } }
                gouge = max(gouge, r - d)
                if (d < r - tol) fails += "$inp: GOUGE ${moves[k].kind} ${moves[k].line - 3}줄 이 도면을 팜(${r - d})\n${s.nc.joinToString("\n")}"
            }
            val first = s.drawing.segs.first()
            val t0 = CompFrame.startDir(first)
            // 관통 구멍에서 시작하면 그 너머는 구멍이라 막힌 시작이 아니다(사용자 답 2026-11-13 — RoundThroughTest)
            val wall = first is Line && abs(t0.z) > abs(t0.x) && t0.z > 0 && !inp.through
            if (wall) {
                walls++
                val past = noses.flatMap(::dense).minOf { (it - first.start) dot t0 }
                if (past < r - tol) fails += "$inp: START_PAST 노즈가 시작 좌표 너머로 감(${r - past})\n${s.nc.joinToString("\n")}"
            }
            val cut = noses.filterIndexed { k, _ -> moves[k].kind == MoveKind.FEED }
            for (seg in s.drawing.segs) for (p in dense(seg)) {
                if (wall && ((p - first.start) dot t0) < r + tol) continue
                val lack = cut.minOf { distTo(it, p) } - base.center.segs.minOf { distTo(it, p) }
                under = max(under, lack)
                if (lack > tol) {
                    fails += "$inp: UNDERCUT X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)} 에서 기본 방향보다 덜 깎음($lack)\n${s.nc.joinToString("\n")}"
                    return@repeat
                }
            }
        }
        println("뒤집은 직접 보정: $runs 건(막힌 시작 $walls) · 가장 큰 파고듦 $gouge · 가장 큰 덜 깎임 $under · 실패 ${fails.size}")
        fails.take(5).forEach(::println)
        assertTrue(runs > 250 && walls > 120, "runs $runs walls $walls")
        assertEquals(0, fails.size, fails.take(5).joinToString("\n\n"))
    }

    // ---------------------------------------------------------------- 급송 여유(검증 2026-11-11 — V1 · V2 · V3)

    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /**
     * 글을 시뮬레이터로 돌린 진입 급송(첫 이송 앞 — 보정 혼자 줄 · 공구 교환 자리에서 오는 첫 G0 은 빼고)의 노즈와 소재(도면 + 축 거울 + [wall] = 시작 좌표의 벽 평면,
     * 관통 구멍에서 시작한 글은 시작 좌표 너머로 이어지는 구멍 벽) 사이 가장 작은 틈. [machine] = 기계 보정 글(형식 C 로 돌린 노즈), 아니면 직접 보정 글(노즈 = 인선 − 팁 벡터)
     */
    private fun rapidGap(s: RoundSolution, r: Double, machine: Boolean, wall: Boolean): Double {
        val g1 = s.nc.indexOfFirst { it.startsWith("G1") }
        val nc = if (machine) s.nc else s.nc.mapIndexed { k, l -> if (k == g1) l + "F.1" else l }
        val run = GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = CompStartup.C), mapOf(1 to ToolOffset(r, s.tip))))
        assertNull(run.stop, "${run.stop?.code}\n${s.nc.joinToString("\n")}")
        val tv = NoseComp.tipVector(s.tip, r)
        val skip = pre.size + (if (machine) 2 else 1)
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > skip }
        val rapids = moves.takeWhile { it.kind == MoveKind.RAPID }.map { if (machine) it.nose else shifted(it.tip, tv * -1.0) }
        assertTrue(rapids.isNotEmpty(), s.nc.joinToString("\n"))
        val first = s.drawing.segs.first()
        val t0 = CompFrame.startDir(first)
        // 관통 구멍에서 시작한 글은 시작 좌표 너머로 이어지는 구멍 벽도 소재로 잰다
        val hole = if (s.flip && s.filled?.through == true) listOf<Seg>(Line(first.start, first.start - t0 * 300.0)) else emptyList()
        val mat = (s.drawing.segs + hole).let { it + it.map(::mirror) }
        return rapids.flatMap(::dense).minOf { p -> min(mat.minOf { distTo(it, p) }, if (wall) (p - first.start) dot t0 else Double.MAX_VALUE) } - r
    }

    /** 검증 V1 의 재현 입력들(외경 3 · 내경 5 관통 · 막힘) */
    private val v1 = listOf(
        RoundInput(type = 3, mainR = 25.0, cornerR1 = 0.0, d1 = 30.0, noseR = 0.8, zClear = 1.0, legLen = 5.0, flip = true) to true,
        a to true,
        d.copy(noseR = 0.8) to false,
        d to false,
        RoundInput(type = 5, mainR = 20.0, cornerR1 = 0.0, cornerR2 = 0.0, d1 = 16.0, d2 = 30.0, l1 = 8.0, noseR = 0.8, zClear = 1.0, legLen = 5.0, inner = true, flip = true) to false,
        d.copy(through = false) to false,
        b to false,
    )

    /**
     * 뒤집은 기계 보정 글의 진입 급송은 깎을 면 · 시작 좌표의 벽에서 Z 여유(c = max(Z 여유, 노즈R))만큼 떨어져 지난다(검증 2026-11-11 V1 — 전에는 G41/G42 가 걸린 채
     * 가는 Z 급송의 노즈가 명령선에서 2r 밀려 틈이 c − 2r · 모자라면 0.05 였고, 급송 끝이 벽 평면에 틈 0 으로 닿았다)
     */
    @Test
    fun flippedMachineRapidKeepsZClear() {
        for ((inp, wall) in v1) for (zc in listOf(inp.zClear, 0.0)) {
            val i = inp.copy(zClear = zc)
            val s = machine(i)
            val c = max(zc, i.noseR)
            val gap = rapidGap(s, i.noseR, machine = true, wall = wall)
            assertTrue(gap >= c - 2e-3, "$i: 급송 틈 $gap < $c\n${s.nc.joinToString("\n")}")
        }
    }

    /** 뒤집은 직접 보정 글도 같다 — 접근 급송의 걸림 판정 · 급송 끝에 여유 c(검증 2026-11-11 V3: 공 꼭대기 위 0.0275 로 지나던 값 포함) */
    @Test
    fun flippedDirectRapidKeepsZClear() {
        val ball = RoundInput(type = 4, mainR = 5.887, cornerR1 = 1.251, d1 = 11.029, noseR = 0.4, zClear = 0.0, legLen = 9.377, flip = true)
        for ((inp, wall) in v1 + (ball to true)) for (zc in listOf(inp.zClear, 0.0)) {
            val i = inp.copy(zClear = zc)
            val s = direct(i)
            val c = max(zc, i.noseR)
            val gap = rapidGap(s, i.noseR, machine = false, wall = wall)
            assertTrue(gap >= c - 2e-3, "$i: 급송 틈 $gap < $c\n${s.nc.joinToString("\n")}")
        }
    }

    /**
     * 뒤집은 3점호(끝점 P3 에서 Z+ 로)의 급송은 P3 너머(척 · 구멍 안쪽)로 가지 않는다 — P3 의 법선 앞 c · 공구 쪽 c 에서 멈추고 이송으로 닿는다. 어느 이동도 노즈
     * 중심이 P3 의 법선을 0.05(기계 보정의 접선 줄 [CompFrame.MIN_MOVE]) 넘게 넘지 않는다(검증 2026-11-11 V2 — 전에는 P3 너머 접선 연장선 위로 급송이
     * 틈 0 으로 내려앉았다). 앞이 원호에 막히는 오목 R 은 급송이 원호에서 c/2 는 떨어진다
     */
    @Test
    fun flippedArc3EntersSquareAtP3() {
        // 외경 볼록 1/4 원 X30 Z0 → X50 Z-10 (R10) — P3 뒤로 Ø50 이 접해 이어지는 꼴
        val outer = Arc3Input(noseR = 0.4, x1 = 30.0, z1 = 0.0, x2 = 44.1421, z2 = -2.9289, x3 = 50.0, z3 = -10.0, zClear = 1.0, flip = true)
        // 내경 오목 1/4 원 X30 Z0 → X20 Z-5 (R5) — 구멍 벽에서 바닥 면으로 도는 R, P3 에서 접선이 X 방향
        val inner = Arc3Input(inner = true, noseR = 0.4, x1 = 30.0, z1 = 0.0, x2 = 27.0711, z2 = -3.5355, x3 = 20.0, z3 = -5.0, zClear = 1.0, flip = true)
        // 내경 볼록 1/4 원(입구 모서리 R) X30 Z0 → X20 Z-5 — P3 뒤로 Ø20 구멍이 접해 이어지는 꼴
        val mouth = inner.copy(x2 = 22.9289, z2 = -1.4645)
        for ((i, convex) in listOf(outer to true, inner to false, mouth to true)) for (m in listOf(false, true)) {
            val res = if (m) RoundCalc.machine(i) else RoundCalc.arc3(i)
            val s = assertNotNull(res.solution, "$i")
            assertTrue(RoundWarning.ENTRY_NOT_FOUND !in res.warnings, "$i")
            val r = i.noseR
            val text = "$i 기계 $m" + "\n" + s.nc.joinToString("\n")
            if (convex) assertTrue(rapidGap(s, r, machine = m, wall = true) >= 1.0 - 2e-3, "급송 틈(원호 · P3 의 법선) " + text)
            else assertTrue(rapidGap(s, r, machine = m, wall = false) >= 0.5 - 2e-3, "급송 틈(원호) " + text)
            val g1 = s.nc.indexOfFirst { it.startsWith("G1") }
            val nc = if (m) s.nc else s.nc.mapIndexed { k, l -> if (k == g1) l + "F.1" else l }
            val run = GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = CompStartup.C), mapOf(1 to ToolOffset(r, s.tip))))
            val tv = NoseComp.tipVector(s.tip, r)
            val first = s.drawing.segs.first()
            val t0 = CompFrame.startDir(first)
            val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size + (if (m) 2 else 1) }
            // 빼는 줄 뒤(G40 · G0Z200.)는 부품 앞이라 빼고 본다
            val upTo = moves.indexOfLast { it.kind == MoveKind.FEED }
            val past = moves.take(upTo + 1).map { if (m) it.nose else shifted(it.tip, tv * -1.0) }.flatMap(::dense).minOf { (it - first.start) dot t0 }
            assertTrue(past >= -CompFrame.MIN_MOVE - 2e-3, "노즈 중심이 P3 의 법선을 넘음(${-past}) " + text)
            // 원호는 끝까지 깎는다 — 첫 보정점은 기본 방향 풀이의 끝 보정점
            val base = assertNotNull((if (m) RoundCalc.machine(i.copy(flip = false)) else RoundCalc.arc3(i.copy(flip = false))).solution)
            assertTrue((s.nodes.first().prog - base.nodes.last().prog).length < 1e-9, text)
        }
    }

    /**
     * 무작위 입력(형상 1~6 외 · 내경 · 관통) + 짧은 끝 직선: 뒤집은 글(기계 · 직접)의 진입 급송 틈(도면 · 축 거울 · 막힌 시작의 벽까지)을 센다.
     * 급송 여유 c 를 지키는 것이 대부분이고, 이웃 요소에 걸리는 짧은 끝 직선 · 좁은 구멍에서만 c/2 · c/4 로 물러난다 — 어느 것도 c/4 밑으로는 안 간다
     * (노즈가 겨우 들어가는 좁은 구멍은 구멍 반지름 − 노즈R 이 한계 — 축에서 시작하는 원호는 거기서 [RoundCalc.AXIS_PAST] 더)
     */
    @Test
    fun flippedEntryRapidSweep() {
        val rnd = Random(20261112)
        val g = GeometricVerifyTest()
        val count = mutableMapOf<String, Int>()
        val worst = mutableMapOf<String, Pair<Double, String>>()
        var least = Double.MAX_VALUE
        var runs = 0
        var full = 0
        val fails = mutableListOf<String>()
        repeat(700) { n ->
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            var inp = g.randomRound(type, rnd, inner).input.copy(flip = true)
            // 셋에 하나는 짧은 끝 직선(노즈R 의 1.5 ~ 4 배)
            if (n % 3 == 0) inp = inp.copy(legLen = inp.noseR * (1.5 + 2.5 * rnd.nextDouble()))
            val res = RoundCalc.machine(inp)
            val m = res.solution ?: return@repeat
            if (RoundWarning.CONCAVE_R_LE_NOSE in res.warnings || RoundWarning.ENTRY_NOT_FOUND in res.warnings) return@repeat
            val d = direct(inp)
            val c = max(inp.zClear, inp.noseR)
            val first = m.drawing.segs.first()
            val t0 = CompFrame.startDir(first)
            // 관통 구멍에서 시작하면 그 너머는 구멍이라 막힌 시작이 아니다(사용자 답 2026-11-13 — RoundThroughTest)
            val wall = first is Line && abs(t0.z) > abs(t0.x) && t0.z > 0 && !inp.through
            for ((tab, s) in listOf("기계" to m, "직접" to d)) {
                val run = runCatching { rapidGap(s, inp.noseR, machine = tab == "기계", wall = wall) }.getOrNull()
                if (run == null) {
                    // 시뮬레이터가 멈춘 글(기본 방향도 멈추는 꼴 — 짧은 요소의 간섭 알람)은 세기만 한다
                    count["$tab 알람"] = (count["$tab 알람"] ?: 0) + 1
                    continue
                }
                least = min(least, run)
                runs++
                if (run >= c - 2e-3) full++
                val room = if (inner) s.drawing.segs.flatMap { listOf(it.start.x, it.end.x) }.filter { it > 1e-6 }.min() - inp.noseR - RoundCalc.AXIS_PAST else Double.MAX_VALUE
                if (run < min(c / 4, room) - 2e-3) fails += "$tab $inp: 급송 틈 $run (c $c · 구멍 여유 $room)" + "\n" + s.nc.take(7).joinToString(" / ")
                val key = "$tab " + (if (inner) "내경" else "외경") + " " + when {
                    run >= c - 2e-3 -> "c 이상"
                    run >= c / 2 - 2e-3 -> "c/2 이상"
                    run >= c / 4 - 2e-3 -> "c/4 이상"
                    else -> "c/4 미만"
                }
                count[key] = (count[key] ?: 0) + 1
                val w = worst[key]
                if (w == null || run / c < w.first) worst[key] = (run / c) to "$inp 틈 $run (c $c)" + "\n" + s.nc.take(7).joinToString(" / ")
            }
        }
        println("뒤집은 진입 급송 틈: " + count.toSortedMap())
        for ((k, v) in worst.toSortedMap()) if (!k.endsWith("c 이상")) println("  $k 가장 작은 비 ${v.first}: ${v.second}")
        println("  가장 작은 틈 $least · 잰 글 $runs · c 이상 $full")
        assertTrue(runs > 900 && full > runs * 0.8, "잰 글 $runs · c 이상 $full")
        assertEquals(0, fails.size, fails.take(5).joinToString("\n\n"))
    }
}
