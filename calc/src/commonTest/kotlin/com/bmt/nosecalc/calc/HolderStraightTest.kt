package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.verify.GeometricVerifyTest
import com.bmt.nosecalc.calc.verify.StopChecker
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 10-28 사용자 결정 두 가지:
 * 1. 안전 여유는 직선(유클리드) 거리 — 볼록 조각과 소재 경계 사이 거리([clearance])의 손 계산 값, 깎는 면 예외([Bore.faceFlags])의 범위.
 * 2. 라운드 기계 보정 프로그램의 G00 을 팁에 맞게 띄운다([RoundCalc.machine]) — 표준 팁은 그대로, 비표준 팁은 손 계산, 무작위 비표준 팁에서
 *    기계 보정 진입에서 멈추는(MACHINE_ENTRY) 비율이 줄었는지
 */
class HolderStraightTest {
    private val r = 0.4
    private val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
    private val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, inner = true, noseR = 0.4)
    private val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0, inner = true, noseR = 0.4)

    /** 정사각형 (0,0)~(1,1) 과 직선 · 원호 토막 사이 거리 — 손 계산 */
    @Test
    fun clearanceExact() {
        val sq = listOf(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 1.0))
        // Z3 수직선: 2
        val wall = listOf(Wall(Line(Pt(3.0, -5.0), Pt(3.0, 5.0)), false))
        assertEquals(2.0, clearance(sq, wall, false, null).m, 1e-12)
        // 맞은편 쪽만 보면 토막이 없다
        assertEquals(Double.POSITIVE_INFINITY, clearance(sq, wall, true, null).m)
        // 중심 (5, 0.5) R1 의 왼쪽 반원(위 (5, 1.5) 에서 반시계로 아래 (5, −0.5)): 가장 가까운 점 (4, 0.5), 거리 3 — 변 안쪽 점(중심에서 변에 내린 발 쪽)
        val left = Wall(Arc(Pt(5.0, 1.5), Pt(5.0, -0.5), Pt(5.0, 0.5), cw = false), false)
        clearance(sq, listOf(left), false, null).let {
            assertEquals(3.0, it.m, 1e-12)
            assertEquals(4.0, it.z, 1e-12)
            assertEquals(0.5, it.x, 1e-12)
        }
        // 오른쪽 위 1/4 원(위 (5, 1.5) 에서 시계로 (6, 0.5)): 발 쪽 점이 원호 밖이라 원호 끝 (5, 1.5) ~ 모서리 (1, 1): √(16 + 0.25)
        val quarter = Wall(Arc(Pt(5.0, 1.5), Pt(6.0, 0.5), Pt(5.0, 0.5), cw = true), false)
        assertEquals(sqrt(16.25), clearance(sq, listOf(quarter), false, null).m, 1e-12)
        // 비스듬한 선분이 다각형 꼭짓점 (1, 1) 에 가장 가깝다: 선 z + x = 4 위 (1.5, 2.5)~(3, 1) → 거리 |1 + 1 − 4| / √2
        val slant = Wall(Line(Pt(1.5, 2.5), Pt(3.0, 1.0)), false)
        assertEquals(2 / sqrt(2.0), clearance(sq, listOf(slant), false, null).m, 1e-12)
        // 만나면 0
        val cross = Wall(Line(Pt(0.5, -1.0), Pt(0.5, 2.0)), false)
        assertEquals(0.0, clearance(sq, listOf(cross), false, null).m, 1e-12)
        // 원호가 변을 지나도 0: 중심 (0.5, 0.5) R0.6 오른쪽 반원이 변 Z1 을 지난다. R1.5 면 정사각형이 통째로 원 안이라 1.5 − √0.5
        val through = Wall(Arc(Pt(0.5, 1.1), Pt(0.5, -0.1), Pt(0.5, 0.5), cw = true), false)
        val around = Wall(Arc(Pt(0.5, 2.0), Pt(0.5, -1.0), Pt(0.5, 0.5), cw = true), false)
        assertEquals(1.5 - sqrt(0.5), clearance(sq, listOf(around), false, null).m, 1e-12)
        assertEquals(0.0, clearance(sq, listOf(through), false, null).m, 1e-12)
        // 깃발 토막은 뺀다
        assertEquals(3.0, clearance(sq, listOf(cross, left), false, booleanArrayOf(true, false)).m, 1e-12)
    }

    /**
     * 깎는 면(예외) 범위: 예 C(내경 5 막힘, 바 A) — 도면 요소 0 입구 Ø40, 1 SR25, 2 Ø30 구멍, 3 바닥 면(Z−16, 축 쪽으로).
     * 바닥 면은 주절삭날 쪽(노즈가 닿는 방향 (−1, 0) 이 주절삭날 접점 방향 n1 = (−sin 95°, cos 95°) 쪽 — u·n1 = 0.996 > u·n2 = −0.087)이라
     * 요소 3 에서, 그리고 요소 2 → 3 이 공구 쪽으로 꺾여(오목) 요소 2 에서도 깃발. 그 직선 위 소재 경계 = 바닥 면과 축 너머 거울 반쪽(둘).
     * 구멍 벽(요소 0 · 2)은 끝날 쪽이라 깃발 없음, 원호(1)는 예외 없음. 예 A(돔 바닥)는 직선 면이 없다
     */
    @Test
    fun faceFlagsBottomFace() {
        val sol = RoundCalc.calc(step).solution!!
        val bore = Bore(sol.drawing, true)
        val tool = BarShape(barA, r, false)
        assertEquals(-sin(95 * PI / 180), tool.n1.z, 1e-12)
        assertEquals(cos(95 * PI / 180), tool.n1.x, 1e-12)
        for (k in listOf(2, 3)) {
            val f = bore.faceFlags(sol.drawing, Side.G41, tool, k)!!
            val on = bore.walls.indices.filter { f[it] }.map { bore.walls[it] }
            assertEquals(2, on.size, "요소 $k")
            assertTrue(on.all { w -> w.seg is Line && abs(w.seg.start.z + 16) < 1e-9 && abs(w.seg.end.z + 16) < 1e-9 }, "요소 $k ${on.map { it.seg }}")
            assertEquals(setOf(false, true), on.map { it.opposite }.toSet())
        }
        for (k in listOf(-2, -1, 0, 1)) assertNull(bore.faceFlags(sol.drawing, Side.G41, tool, k), "요소 $k")
        val a = RoundCalc.calc(dome).solution!!
        val ba = Bore(a.drawing, true)
        for (k in -1 until a.drawing.segs.size) assertNull(ba.faceFlags(a.drawing, Side.G41, tool, k), "A 요소 $k")
        // 뒤보링은 인서트가 Z 로 뒤집혀 주절삭날 접점이 +Z 쪽
        assertTrue(BarShape(barA, r, true).n1.z > 0)
    }

    /**
     * 예외가 없으면 막힌 바닥을 못 깎는다(사용자 결정의 까닭): C 에서 요소 2 끝(바닥 면 모서리, 노즈 (Z−15.6, 14.6))에 선 자세의 바와 소재 직선 거리는
     * 바닥 면까지 0.226(안전 여유 0.5 보다 짧다)인데, 깎는 면을 뺀 여유는 바 윗면(14.6 − 2.6 = 12)과 Ø30 벽(15) 사이 3 이고 안전 끝점은 바닥 면(요소 3) 위에 있다
     */
    @Test
    fun exceptionLetsBottomFaceBeCut() {
        val sol = RoundCalc.calc(step).solution!!
        val bore = Bore(sol.drawing, true)
        val tool = BarShape(barA, r, false)
        val corner = sol.program.segs[2].end - NoseComp.tipVector(2, r)
        val plain = clearOf(bore, tool, listOf(corner), null)
        val face = clearOf(bore, tool, listOf(corner), bore.faceFlags(sol.drawing, Side.G41, tool, 2))
        assertEquals(0.226, plain.barCut.m, 1e-3)
        assertEquals(3.0, min(face.barCut.m, face.barOpp.m), 1e-9)
        val e = HolderStop.plan(sol, RoundCalc.machine(step).solution, r, true, barA, 0.5).end!!
        assertEquals(3, e.element)
    }

    /**
     * 기계 보정 G00 — 표준 팁이 제 쪽 요소를 따라가면 lift = 0: Z 여유 ≥ r 이면 노즈R 0 풀이와 같은 글(내경 · 외경 TYPE, 3점호 Z 진입).
     * Z 여유 < r 이면 G00 이 r 만큼(직접 보정과 같은 c = max(Z 여유, r)) — 보정 시작 이동이 r 보다 짧지 않게(예전에는 Z 여유 0 이면 시작 이동이 없었다)
     */
    @Test
    fun machineStandardTipUnchanged() {
        val outer3 = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4)
        val inputs = listOf(step, step.copy(through = true), dome, outer3, RoundInput(type = 1, mainR = 10.0, cornerR1 = 1.0, l1 = 14.8, noseR = 0.8))
        for (i in inputs) {
            val m = RoundCalc.machine(i).solution!!
            assertEquals(RoundCalc.calc(i.copy(noseR = 0.0)).solution!!.nc, m.nc, "$i")
            assertNull(m.lead)
        }
        // 무작위 TYPE(표준 팁, Z 여유 ≥ r)
        val rnd = Random(28)
        val g = GeometricVerifyTest()
        var n = 0
        repeat(400) {
            val inner = rnd.nextBoolean()
            val i = g.randomRound(rnd.nextInt(if (inner) 2 else 1, 7), rnd, inner = inner).input.copy(tip = null)
            if (i.zClear < i.noseR) return@repeat
            val m = RoundCalc.machine(i).solution ?: return@repeat
            n++
            assertEquals(RoundCalc.calc(i.copy(noseR = 0.0)).solution!!.nc, m.nc, "$i")
        }
        assertTrue(n > 150, "$n")
        // Z 여유 0 < r: 외경 TYPE 3 의 G00 = (Z r, X0) 다음 G01 Z0 — 노즈R 0 풀이는 G00 이 첫 점 Z0 이라 보정 시작 이동이 없었다
        assertEquals(listOf("G00 X0. Z0.4", "G01 Z0."), RoundCalc.machine(outer3.copy(zClear = 0.0)).solution!!.nc.take(2))
        assertEquals("G00 X0. Z0.", RoundCalc.calc(outer3.copy(zClear = 0.0, noseR = 0.0)).solution!!.nc.first())
    }

    /**
     * 비표준 팁의 띄운 G00 손 계산. 외경 TYPE 3(돔 끝이 축 위 (0, 0), 시작 접선 +X, G42 공구 쪽 n = (1, 0)) · 팁 4 tv = (0.4, −0.4):
     * lift = r + n·tv = 0.8, 진입은 Z(시작 선에 직각)라 G00 = (Z c + lift, X0) = Z1.8 — 노즈 (1.4, 0.4) 가 끝 면에서 c + r = 1.4.
     * 3점호 외경 Z 진입도 같은 식
     */
    @Test
    fun machineLiftHandCalc() {
        val outer3 = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4, tip = 4)
        val m = RoundCalc.machine(outer3).solution!!
        assertEquals("G00 X0. Z1.8", m.nc.first())
        assertEquals(RoundCalc.calc(outer3.copy(noseR = 0.0)).solution!!.nc.drop(1), m.nc.drop(1))
        assertNull(m.lead)
        val nose0 = m.entry - NoseComp.tipVector(4, 0.4)
        assertEquals(Pt(1.4, 0.4), nose0)
        // 표준 팁 3 이면 그대로 Z1
        assertEquals("G00 X0. Z1.", RoundCalc.machine(outer3.copy(tip = null)).solution!!.nc.first())
        // 팁 0(중심): lift = r → Z1.4
        assertEquals("G00 X0. Z1.4", RoundCalc.machine(outer3.copy(tip = 0)).solution!!.nc.first())
    }

    /**
     * 직접 보정이 멈추면 기계 보정도 멈춘다(10-28 검증): 3점호 외경 X20.998 Z−13.421 / X20.776 Z−15.234 / X21.604 Z−15.393 은 오목 R1.046 홈이라
     * 1.2R 노즈가 닿지 못한다(`NOSE_UNREACHABLE`). 예전 기계 보정은 노즈R 0 도면 좌표로 풀어 글을 냈고 시작 블록 노즈가 맞은편 벽을 0.28 mm 팠다.
     * 내경 TYPE 3 R0.5 · Ø0.6 · 0.4R(노즈 중심이 축을 넘음)도 같다
     */
    @Test
    fun machineStopsWhenDirectStops() {
        val a = Arc3Input(inner = false, noseR = 1.2, x1 = 20.998, z1 = -13.421, x2 = 20.776, z2 = -15.234, x3 = 21.604, z3 = -15.393, zClear = 0.508)
        assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), RoundCalc.arc3(a).warnings)
        assertNull(RoundCalc.arc3(a.copy(noseR = 0.0)).warnings.firstOrNull { it == RoundWarning.NOSE_UNREACHABLE })
        RoundCalc.machine(a).let {
            assertNull(it.solution)
            assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), it.warnings)
        }
        val tiny = RoundInput(type = 3, mainR = 0.5, d1 = 0.6, cornerR1 = 0.0, legLen = 2.0, inner = true, noseR = 0.4)
        assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), RoundCalc.calc(tiny).warnings)
        RoundCalc.machine(tiny).let {
            assertNull(it.solution)
            assertEquals(listOf(RoundWarning.NOSE_UNREACHABLE), it.warnings)
        }
    }

    /**
     * 무작위 비표준 팁(내경 TYPE 2~6 · 3점호 정방향 · 뒤보링 — 팁 0~9 가운데 표준이 아닌 것, Z 여유 0~1.5)에서 기계 보정 프로그램이 제 진입(G00 점 ·
     * 보정 시작 블록)에서 멈추는(MACHINE_ENTRY) 사례 수: 노즈R 0 풀이(예전) 대 [RoundCalc.machine](띄운 G00). 바는 구멍에 맞춰 가늘게(지름 = 입구 반경의
     * 0.6 배, 바 윗면이 노즈 접점보다 1 mm 아래) 하고, 직접 보정이 제 진입을 지나는 사례만 센다 — 둘 다 진입에서 막히는 것은 바 탓이지 G00 자리 탓이 아니다.
     * 띄운 프로그램은 검사기([StopChecker])로도 본다
     */
    @Test
    fun machineEntryRatioDrops() {
        val rnd = Random(1028)
        val g = GeometricVerifyTest()
        var cases = 0
        var oldStop = 0
        var newStop = 0
        val fails = mutableListOf<String>()
        fun bar(rho: Double, rr: Double): BoringBar {
            val dia = max(1.0, 0.6 * rho)
            return BoringBar(dia, HolderCheck.fMin(dia, 95.0, rr) + 1.0, InsertShape.C, HolderCheck.edgeMin(InsertShape.C, rr) + 0.5, 95.0)
        }
        fun run(name: String, sol: RoundSolution, old: RoundSolution, new: RoundSolution, face: Boolean, rr: Double, b: BoringBar) {
            for (safe in listOf(0.0, 0.5)) {
                val po = HolderStop.plan(sol, old, rr, face, b, safe)
                if (po.result.skip != null) return
                val pn = HolderStop.plan(sol, new, rr, face, b, safe)
                // 직접 보정이 제 진입에서 막히면(바 탓) 세지 않는다
                if (StopWarning.UNSAFE_AT_ENTRY in po.warnings || StopWarning.NOTHING_CUT in po.warnings) continue
                cases++
                if (StopWarning.MACHINE_ENTRY in po.warnings) oldStop++
                if (StopWarning.MACHINE_ENTRY in pn.warnings) newStop++
                val f = mutableListOf<String>()
                StopChecker(sol, new, rr, face, b, safe).check(pn, 0.01, f)
                fails += f.map { "$name 안전 여유 $safe: $it" }
            }
        }
        repeat(160) {
            val rr = listOf(0.2, 0.4, 0.8)[rnd.nextInt(3)]
            val i0 = g.randomRound(rnd.nextInt(2, 7), rnd, inner = true).input
            val tip = (0..9).filter { it != 2 }[rnd.nextInt(9)]
            val i = i0.copy(noseR = rr, tip = tip, zClear = (rnd.nextDouble(0.0, 1.5) * 1000).toLong() / 1000.0)
            val sol = RoundCalc.calc(i).solution ?: return@repeat
            run("$i", sol, RoundCalc.calc(i.copy(noseR = 0.0)).solution!!, RoundCalc.machine(i).solution!!, true, rr, bar(sol.drawing.segs.first().start.x, rr))
        }
        repeat(160) { n ->
            val rr = listOf(0.2, 0.4, 0.8)[rnd.nextInt(3)]
            val reverse = n % 2 == 1
            val bigR = rnd.nextDouble(2.0, 20.0)
            val a0 = rnd.nextDouble(20.0, 160.0)
            val a1 = a0 + (if (reverse) 1 else -1) * rnd.nextDouble(10.0, 60.0) * (if (a0 > 90) 1 else 1)
            val c = Pt(rnd.nextDouble(-20.0, -5.0), rnd.nextDouble(0.5, 10.0))
            fun p(deg: Double) = c + Pt(cos(deg * PI / 180), sin(deg * PI / 180)) * bigR
            // 위 반원: 반시계(각 증가)면 Z 가 준다 = 정방향
            val (s, e) = if (reverse) p(max(a0, a1)) to p(min(a0, a1)) else p(min(a0, a1)) to p(max(a0, a1))
            val m = p((a0 + a1) / 2)
            val std = if (reverse) 1 else 2
            val tip = (0..9).filter { it != std }[rnd.nextInt(9)]
            fun r3(v: Double) = (v * 1000).toLong() / 1000.0
            val a = Arc3Input(
                inner = true, noseR = rr, tip = tip, x1 = r3(2 * s.x), z1 = r3(s.z), x2 = r3(2 * m.x), z2 = r3(m.z), x3 = r3(2 * e.x), z3 = r3(e.z),
                zClear = r3(rnd.nextDouble(0.0, 1.5)),
            )
            val sol = RoundCalc.arc3(a).solution ?: return@repeat
            val rho = min(sol.drawing.segs.first().start.x, sol.drawing.segs.last().end.x)
            run("$a", sol, RoundCalc.arc3(a.copy(noseR = 0.0)).solution!!, RoundCalc.machine(a).solution!!, false, rr, bar(rho, rr))
        }
        println("[MACHINE_ENTRY] 비표준 팁 무작위 $cases 사례(안전 여유 0 · 0.5, 직접 보정은 진입을 지남): 노즈R 0 풀이 $oldStop (${oldStop * 100 / max(1, cases)} %) → 띄운 G00 $newStop (${newStop * 100 / max(1, cases)} %)")
        assertTrue(cases > 200, "$cases")
        assertTrue(fails.isEmpty(), fails.take(10).joinToString("\n"))
        assertTrue(newStop * 4 <= oldStop, "띄운 G00 이 진입에서 멈추는 사례 $newStop 가 예전 $oldStop 의 1/4 을 넘는다")
    }
}
