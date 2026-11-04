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
 * 2. 라운드 기계 보정 프로그램의 진입(10-28 에는 G00 띄우기, 계획 단계 4 부터 회사 꼴 — 보정 중 블록이라 팁과 상관없다): 회사 꼴 손 계산,
 *    무작위 비표준 팁에서 기계 보정 진입 판정(MACHINE_ENTRY · 그 끝점)이 표준 팁과 같은지
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
     * 기계 보정 프로그램 = 회사 꼴(계획 단계 4) 손 계산(지은 도형, 0.4R · Z 여유 1). 외경 TYPE 3(Ø20 · SR12.5 · CR1): 돔 끝이 축 위(처음 접선 +X)라 진입점은 축 너머
     * X −2c = X−2 · Z0(첫 깎는 줄이 축 너머 같은 면을 타고 올라옴 — 노즈 접점이 첫 점), 끝 Z-10 은 척 쪽 막힌 끝이라 공구 쪽 직각 X22(노즈가 교점에서 멈춤) 뒤 벽 너머에서
     * +Z 로 (1 + √2)·0.4 + 0.02 = 0.986(Z-9.014). 내경 TYPE 3 바닥 돔: 끝 원호가 축에서 끝나 끝 접선으로 축 너머 r + 0.01(X−0.82) 뒤 +Z.
     * 형상 줄은 노즈R 0 풀이와 같고, 글은 팁 번호와 상관없다(보정 줄이 이동 없는 혼자 줄)
     */
    @Test
    fun machineCompanyFrame() {
        val outer3 = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4)
        assertEquals(
            listOf("G42", "G0X-2.Z20.", "Z0.", "G1X0.F.1", "G3X19.565Z-4.719R12.5", "X20.Z-5.341R1.", "G1Z-10.", "X22.F.3", "Z-9.014", "G40", "G0Z200."),
            RoundCalc.machine(outer3).solution!!.nc,
        )
        assertEquals(
            listOf("G41", "G0X20.Z20.", "Z1.", "G1Z0.F.1", "Z-9.659", "G3X19.565Z-10.281R1.", "X0.Z-15.R12.5", "G1X-.82F.3", "Z-14.007", "G40", "G0Z200."),
            RoundCalc.machine(dome).solution!!.nc,
        )
        for (i in listOf(step, step.copy(through = true), dome, outer3, RoundInput(type = 1, mainR = 10.0, cornerR1 = 1.0, l1 = 14.8, noseR = 0.8))) {
            val m = RoundCalc.machine(i).solution!!
            for (tip in 0..9) assertEquals(m.nc, RoundCalc.machine(i.copy(tip = tip)).solution!!.nc, "$i 팁 $tip")
            // 도면(형상 줄의 좌표)은 노즈R 0 풀이와 같다
            assertEquals(RoundCalc.calc(i.copy(noseR = 0.0)).solution!!.drawing, m.drawing, "$i")
        }
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
     * 무작위 비표준 팁(내경 TYPE 2~6 · 3점호 정방향 · 뒤보링 — 팁 0~9 가운데 표준이 아닌 것, Z 여유 0~1.5)에서 기계 보정 진입 판정이 표준 팁(팁을 비운 풀이)과 같다:
     * MACHINE_ENTRY 가 있고 없음, 있으면 그 끝점(요소 · 비율 · 명령점). 회사 꼴 진입은 보정 혼자 줄 뒤 보정 중 블록이라 노즈가 보정선 위 — 팁과 상관없다.
     * (10-28 에는 노즈R 0 풀이의 G00 노즈가 비표준 팁에서 소재에 박혀 이 사례들의 80 % 가 진입에서 멈췄고 띄운 G00 으로 0 % 가 됐다 — 그 G00 이 없어져 비교 대상이
     * 바뀌었다. 노즈R 0 풀이는 이제 기계 보정 프로그램이 아니라 계획이 보지 않는다 — [HolderStopTest.machineEntryNonStandardTip].)
     * 바는 구멍에 맞춰 가늘게(지름 = 입구 반경의 0.6 배, 바 윗면이 노즈 접점보다 1 mm 아래). 비표준 팁 풀이는 검사기([StopChecker])로도 본다
     */
    @Test
    fun machineEntryTipIndependent() {
        val rnd = Random(1028)
        val g = GeometricVerifyTest()
        var cases = 0
        var newStop = 0
        val fails = mutableListOf<String>()
        fun bar(rho: Double, rr: Double): BoringBar {
            val dia = max(1.0, 0.6 * rho)
            return BoringBar(dia, HolderCheck.fMin(dia, 95.0, rr) + 1.0, InsertShape.C, HolderCheck.edgeMin(InsertShape.C, rr) + 0.5, 95.0)
        }
        fun run(name: String, sol: RoundSolution, solStd: RoundSolution, new: RoundSolution, newStd: RoundSolution, face: Boolean, rr: Double, b: BoringBar) {
            for (safe in listOf(0.0, 0.5)) {
                val pn = HolderStop.plan(sol, new, rr, face, b, safe)
                if (pn.result.skip != null) return
                val ps = HolderStop.plan(solStd, newStd, rr, face, b, safe)
                cases++
                val stop = StopWarning.MACHINE_ENTRY in pn.warnings
                if (stop) newStop++
                val en = pn.machineEnd
                val es = ps.machineEnd
                val same = stop == (StopWarning.MACHINE_ENTRY in ps.warnings) && (!stop || (en != null && es != null && en.element == es.element &&
                    abs(en.t - es.t) < 1e-9 && (en.at.machine!! - es.at.machine!!).length < 1e-9))
                if (!same) fails += "$name 안전 여유 $safe: 비표준 팁 ${pn.warnings} ${en?.element} ${en?.t} ${en?.at?.machine} · 표준 팁 ${ps.warnings} ${es?.element} ${es?.t} ${es?.at?.machine}"
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
            val std = i.copy(tip = null)
            run("$i", sol, RoundCalc.calc(std).solution!!, RoundCalc.machine(i).solution!!, RoundCalc.machine(std).solution!!, true, rr, bar(sol.drawing.segs.first().start.x, rr))
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
            val aStd = a.copy(tip = null)
            run("$a", sol, RoundCalc.arc3(aStd).solution!!, RoundCalc.machine(a).solution!!, RoundCalc.machine(aStd).solution!!, false, rr, bar(rho, rr))
        }
        println("[MACHINE_ENTRY] 비표준 팁 무작위 $cases 사례(안전 여유 0 · 0.5): 회사 꼴 진입에서 멈춤 $newStop (${newStop * 100 / max(1, cases)} %) — 표준 팁과 같은 판정")
        assertTrue(cases > 200, "$cases")
        assertTrue(fails.isEmpty(), fails.take(10).joinToString("\n"))
    }
}
