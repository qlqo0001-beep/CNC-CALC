package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 3D 메시(설계 6.2 · 16절): 부피(다각형으로 돌린 몸체의 손 계산) · 바깥쪽 법선(부호 있는 부피 > 0, 삼각형 감김 = 꼭짓점 법선 쪽) ·
 * 1/4 절개 부피 = 270° 몫 · 삼각형 한도 · 나사 골(θ = 0 단면이 2D 톱니와 같은 자리 · 골 반경 = 골 선).
 */
class RevolveMeshTest {
    private fun piece(bottom: List<Pt>, top: List<Pt>) = StockPiece(bottom + top.asReversed(), emptyList(), bottom, top)

    private fun rect(z0: Double, z1: Double, r0: Double, r1: Double) =
        piece(listOf(Pt(z0, r0), Pt(z1, r0)), listOf(Pt(z0, r1), Pt(z1, r1)))

    /** S 토막 다각형의 넓이 몫(원 대비) — 돌린 몸체 부피의 손 계산 */
    private fun polyArea(r: Double, segs: Int, sweep: Double) = segs * 0.5 * r * r * sin(sweep / segs)

    /** 삼각형마다 기하 법선(감김)이 꼭짓점 법선 쪽 — 바깥에서 보아 반시계 */
    private fun windingOutward(m: Mesh) {
        val p = m.pos
        val n = m.nrm
        for (k in 0 until m.triangles) {
            val a = 3 * m.tri[3 * k]
            val b = 3 * m.tri[3 * k + 1]
            val c = 3 * m.tri[3 * k + 2]
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val wx = p[c] - p[a]; val wy = p[c + 1] - p[a + 1]; val wz = p[c + 2] - p[a + 2]
            val gx = uy * wz - uz * wy; val gy = uz * wx - ux * wz; val gz = ux * wy - uy * wx
            val d = gx * (n[a] + n[b] + n[c]) + gy * (n[a + 1] + n[b + 1] + n[c + 1]) + gz * (n[a + 2] + n[b + 2] + n[c + 2])
            assertTrue(d > 0, "삼각형 $k 감김이 법선과 반대")
        }
        for (i in 0 until m.vertices) {
            val l = sqrt(n[3 * i] * n[3 * i] + n[3 * i + 1] * n[3 * i + 1] + n[3 * i + 2] * n[3 * i + 2])
            assertTrue(abs(l - 1) < 1e-4, "법선 $i 길이 $l")
        }
    }

    @Test
    fun solidBarVolume() {
        val prof = StockProfile(listOf(rect(-40.0, 0.0, 0.0, 25.0)), emptyList())
        val m = RevolveMesh.part(prof, Section.FULL, 96, 0.005, RevolveMesh.TRI_IDLE)
        val want = 40 * polyArea(25.0, 96, 2 * PI)
        assertEquals(want, RevolveMesh.volume(m), want * 1e-5, "통짜 봉 부피")
        windingOutward(m)
        // 축 위 띠는 돌리지 않는다: 원통(토막마다 2) + 양 끝 원판(축 쪽 삼각형은 넓이 0 — 토막마다 1)
        assertEquals(2 * 96 + 2 * 96, m.triangles)
        assertTrue(m.tag.all { it == Mesh.PART })
    }

    @Test
    fun quarterTubeVolume() {
        val prof = StockProfile(listOf(rect(-30.0, 2.0, 10.0, 30.0)), emptyList())
        val full = RevolveMesh.part(prof, Section.FULL, 96, 0.005, RevolveMesh.TRI_IDLE)
        val tube = 32 * (polyArea(30.0, 96, 2 * PI) - polyArea(10.0, 96, 2 * PI))
        assertEquals(tube, RevolveMesh.volume(full), tube * 1e-5, "관 부피")
        val q = RevolveMesh.part(prof, Section.QUARTER, 96, 0.005, RevolveMesh.TRI_IDLE)
        val want = 32 * (polyArea(30.0, 72, 1.5 * PI) - polyArea(10.0, 72, 1.5 * PI))
        assertEquals(want, RevolveMesh.volume(q), want * 1e-5, "1/4 절개 부피 = 270° 몫(단면으로 닫힘)")
        windingOutward(q)
        assertTrue(q.tag.any { it == Mesh.SECTION }, "단면 삼각형")
        // 뺀 쪽(θ ∈ (0°, 90°) — y > 0 이고 z > 0)에는 꼭짓점이 없다
        for (i in 0 until q.vertices) {
            val y = q.pos[3 * i + 1]
            val z = q.pos[3 * i + 2]
            assertTrue(!(y > 1e-3 && z > 1e-3), "뺀 쪽 꼭짓점 ($y, $z)")
        }
    }

    /** 홈 · 어깨가 있는 단면: 부피 = 사다리꼴 넓이마다 2π r̄ (다각형 몫) */
    @Test
    fun steppedProfileVolume() {
        // z 가 늘어나게(어깨는 거의 세로)
        val t = listOf(Pt(-50.0, 30.0), Pt(-20.0, 30.0), Pt(-19.999, 20.0), Pt(-12.0, 20.0), Pt(-10.0, 22.0), Pt(0.0, 22.0))
        val prof = StockProfile(listOf(piece(listOf(Pt(-50.0, 0.0), Pt(0.0, 0.0)), t)), emptyList())
        val m = RevolveMesh.part(prof, Section.FULL, 96, 0.001, RevolveMesh.TRI_IDLE)
        // 돌린 부피 = Σ ∫ π r(z)² dz × 다각형 몫
        var v = 0.0
        for (i in 0 until t.size - 1) {
            val a = t[i]
            val b = t[i + 1]
            val dz = b.z - a.z
            v += PI * dz * (a.x * a.x + a.x * b.x + b.x * b.x) / 3
        }
        v *= polyArea(1.0, 96, 2 * PI) / PI
        assertEquals(v, RevolveMesh.volume(m), v * 1e-4, "단 있는 몸체 부피")
        windingOutward(m)
    }

    /** 삼각형 한도: 꼭짓점 2만 개 톱니 단면도 한도 안으로 줄인다(tol 두 배씩) */
    @Test
    fun triangleBudget() {
        val n = 20_000
        val top = List(n) { Pt(-100.0 + 100.0 * it / (n - 1), 20.0 + if (it % 2 == 0) 0.0 else 0.3) }
        val prof = StockProfile(listOf(piece(listOf(Pt(-100.0, 0.0), Pt(0.0, 0.0)), top)), emptyList())
        for ((seg, tol, max) in listOf(Triple(RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG), Triple(RevolveMesh.SEG_IDLE, RevolveMesh.TOL_IDLE, RevolveMesh.TRI_IDLE))) {
            val m = RevolveMesh.part(prof, Section.QUARTER, seg, tol, max)
            assertTrue(m.triangles in 1..max, "삼각형 ${m.triangles} > $max")
        }
    }

    /**
     * 나사(S 4-95 G92 — 골 X62.64 · 리드 6 · 60°, 소재 Ø68 · 끝면 Z70 — 진입 급송 Z76.2 가 소재 밖): 겉면 가장 작은 반경 = 골 31.32,
     * θ = 0 단면(1/4 의 끝 면)의 골 자리 = 2D 톱니 가운데(골 선 위끝 Z76.2 에서 리드/2 · 리드마다 — Z67.2 · 61.2 …), 산은 소재 반경 34 그대로.
     */
    @Test
    fun threadGrooves() {
        val ex = SimExamples.all[5]
        val run = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        val spec = StockSpec(68.0, 70.0, 70.0, 0.0, 170.0)
        val s = StockSim(spec, run, ex.tools).also { it.cutTo(run.totalSec) }
        val prof = s.profile()
        assertEquals(1, prof.threads.size)
        val m = RevolveMesh.part(prof, Section.QUARTER, 96, 0.005, RevolveMesh.TRI_IDLE)
        windingOutward(m)
        var rMin = Double.POSITIVE_INFINITY
        var rMax = 0.0
        val roots = ArrayList<Double>()
        for (i in 0 until m.vertices) {
            val x = m.pos[3 * i].toDouble()
            val y = m.pos[3 * i + 1].toDouble()
            val z = m.pos[3 * i + 2].toDouble()
            val r = sqrt(y * y + z * z)
            // 겉면만(축 · 단면 안쪽 꼭짓점은 뺀다)
            if (x < 30 || x > 69 || r < 20) continue
            rMin = min(rMin, r)
            rMax = max(rMax, r)
            // θ = 0 단면(평면 z = 0, y > 0)의 골 꼭짓점
            if (abs(z) < 1e-4 && y > 0 && abs(y - 31.32) < 1e-3) roots += x
        }
        assertEquals(31.32, rMin, 1e-3, "골 반경")
        assertEquals(34.0, rMax, 1e-3, "산 반경")
        assertTrue(roots.size >= 6, "θ = 0 골 ${roots.size}")
        for (x in roots) {
            val k = (73.2 - x) / 6
            assertTrue(abs(k - kotlin.math.round(k)) < 1e-4, "θ = 0 골 Z$x 는 2D 톱니 가운데(Z73.2 − 6k)여야")
        }
        // 나사 홈이 있는 만큼 부피가 온통 Ø68 보다 작다
        val bar = RevolveMesh.part(StockProfile(prof.pieces, emptyList()), Section.QUARTER, 96, 0.005, RevolveMesh.TRI_IDLE)
        assertTrue(RevolveMesh.volume(m) < RevolveMesh.volume(bar) - 100, "홈 부피")
    }

    /** 척: 몸통 · 턱 셋 · 물린 소재 — 바깥쪽 감김, 1/4 이면 물린 소재만 잘림 */
    @Test
    fun chuck() {
        val spec = StockSpec(50.0, 40.0, 0.0, 20.0, 125.0)
        val full = RevolveMesh.chuck(spec, 96, Section.FULL)
        val q = RevolveMesh.chuck(spec, 96, Section.QUARTER)
        windingOutward(full)
        windingOutward(q)
        assertTrue(full.tag.count { it == Mesh.JAW } == 3 * 12, "턱 상자 셋")
        assertTrue(q.tag.any { it == Mesh.SECTION } && full.tag.none { it == Mesh.SECTION })
        assertTrue(RevolveMesh.volume(full) > 0 && RevolveMesh.volume(q) < RevolveMesh.volume(full))
        // 턱 앞면 = 척 면(Z−40)
        var xMax = Float.NEGATIVE_INFINITY
        for (i in 0 until full.vertices) xMax = max(xMax, full.pos[3 * i])
        assertEquals(-40f, xMax, 1e-4f)
    }

    /** 원호 단면은 매끈하게(띠 사이 법선 평균), 어깨는 모서리 — 둥근 홈 바닥의 법선이 이웃 띠와 이어진다 */
    @Test
    fun smoothArcCreaseShoulder() {
        val arc = List(41) { i ->
            val a = PI * i / 40
            Pt(-20.0 - 5 * cos(a), 25.0 - 5 * sin(a))
        }
        val top = listOf(Pt(-40.0, 25.0)) + arc.sortedBy { it.z } + listOf(Pt(0.0, 25.0))
        val prof = StockProfile(listOf(piece(listOf(Pt(-40.0, 0.0), Pt(0.0, 0.0)), top)), emptyList())
        val m = RevolveMesh.part(prof, Section.FULL, 96, 0.001, RevolveMesh.TRI_IDLE)
        // 홈 바닥(Z−20 · r 20) 근처 θ = 0 꼭짓점들의 법선이 거의 같은 방향끼리 이어지는지: 같은 자리 꼭짓점 법선의 차가 작다
        val byPos = HashMap<Long, MutableList<Int>>()
        for (i in 0 until m.vertices) {
            val x = m.pos[3 * i]
            val y = m.pos[3 * i + 1]
            val z = m.pos[3 * i + 2]
            if (abs(z) > 1e-4 || y < 0) continue
            byPos.getOrPut((kotlin.math.round(x * 1e4).toLong() shl 24) xor kotlin.math.round(y * 1e4).toLong()) { ArrayList() } += i
        }
        var shared = 0
        for ((_, list) in byPos) {
            if (list.size < 2) continue
            val x = m.pos[3 * list[0]]
            if (x < -24.9 || x > -15.1) continue
            shared++
            val a = list[0]
            for (b in list.drop(1)) {
                val d = m.nrm[3 * a] * m.nrm[3 * b] + m.nrm[3 * a + 1] * m.nrm[3 * b + 1] + m.nrm[3 * a + 2] * m.nrm[3 * b + 2]
                assertTrue(d > 0.9999f, "홈 안 같은 자리 법선이 갈림 (Z$x)")
            }
        }
        assertTrue(shared > 5, "홈 안 이음 $shared")
    }

    /**
     * 4단계 무작위 시험에서 찾음: 잘게 깎인 큰 소재(단순화로 줄지 않는 톱니 3만 개)는 나사 · tol 0.5 · 토막 16 까지 줄여도 한도를 넘어
     * 180만 개 메시가 나왔다 — 이제 tol 을 더 키워 한도 안(3D 만 거칠어짐).
     */
    @Test
    fun jaggedProfileStaysUnderLimit() {
        val n = 30_000
        val top = (0 until n).map { k -> Pt(-600.0 + 0.02 * k, if (k % 2 == 0) 20.0 else 22.0) }
        val prof = StockProfile(listOf(piece(listOf(Pt(-600.0, 0.0), Pt(top.last().z, 0.0)), top)), emptyList())
        val drag = RevolveMesh.part(prof, Section.QUARTER, RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG)
        assertTrue(drag.triangles in 1..RevolveMesh.TRI_DRAG, "끄는 중 ${drag.triangles}")
        val idle = RevolveMesh.part(prof, Section.FULL, RevolveMesh.SEG_IDLE, RevolveMesh.TOL_IDLE, RevolveMesh.TRI_IDLE)
        assertTrue(idle.triangles in 1..RevolveMesh.TRI_IDLE, "멈춤 ${idle.triangles}")
    }

    /**
     * 4단계 무작위 시험에서 찾음: 노즈 R 0.0001 인 둥근 인서트가 층마다 떠 있는 가는 고리를 남겨 조각이 수만 개 — tol 로는 줄지 않아
     * 넓은 조각부터 한도까지만 3D 로(작은 조각은 뺌). 큰 몸체는 늘 남는다.
     */
    @Test
    fun manySmallPiecesKeepBigBody() {
        val rings = (0 until 20_000).map { k -> rect(-60.0 + 0.003 * k, -60.0 + 0.003 * k + 0.002, 30.0, 30.01) }
        val body = rect(-60.0, 0.0, 0.0, 25.0)
        val prof = StockProfile(rings + body, emptyList())
        val m = RevolveMesh.part(prof, Section.QUARTER, RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG)
        assertTrue(m.triangles in 1..RevolveMesh.TRI_DRAG, "삼각형 ${m.triangles}")
        // 몸체(반경 25 원통 — 270°)가 남았다: 부피가 몸체 몫 이상
        val bodyVol = 60 * polyArea(25.0, 12, 1.5 * PI) // 토막이 16 까지 줄어도(270° 에 12)
        assertTrue(RevolveMesh.volume(m) >= bodyVol * 0.999, "부피 ${RevolveMesh.volume(m)} < 몸체 $bodyVol")
    }
}
