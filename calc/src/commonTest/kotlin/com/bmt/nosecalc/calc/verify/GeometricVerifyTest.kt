package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 독립 검증 ① — 무작위 기하 검사.
 *
 * 계산기가 낸 프로그램 경로(가상 인선)를 촘촘히 따라가며 노즈 원 중심 = 인선점 − 팁 벡터 를 놓고
 * (a) 노즈 원이 재료로 파고들지 않는지(여유 ≥ −0.0005 mm),
 * (b) 도면 요소마다 원이 닿는 구간이 있고, 반지름 r 인 원이 닿을 수 있는 점은 모두 깎였는지(남김 ≤ 0.0005 mm) 본다.
 * "닿을 수 있다" = 그 점에서 공구 쪽으로 접한 반지름 r 원이 재료와 겹치지 않는다 — 구현과 상관없는 정의다.
 *
 * 거리·안팎·NC 해석은 모두 이 파일 안에서 따로 짰다(구현의 Pt 연산·Intersect·NcLines 를 쓰지 않는다).
 * 재료 영역 = 도면 윤곽 + 양 끝을 접선 방향으로 늘인 반직선 + 큰 원으로 닫은 고리. 외경은 아래, 내경은 위가 재료.
 * 팁 벡터는 설계 문서의 Haas 표를 다시 옮겨 쓰고, 표준 팁은 "바이트가 깎는 모서리" 로 따로 정한다.
 */
class GeometricVerifyTest {

    @Test
    fun tipTableIsHaasTable() {
        for (t in 0..9) {
            val a = NoseComp.tipVector(t, 0.4)
            assertEquals(tipVec(t, 0.4), V(a.z, a.x), "팁 $t")
        }
    }

    /** 이 파일의 NC 해석기 자체 점검: Haas 워크북 43쪽 `X1.5 Z0` → `G03 X2. Z-0.25 R0.25` 는 중심 (Z−0.25, X1.5) 인 볼록 모서리 */
    @Test
    fun ncParserSelfCheck() {
        val a = parseNc(listOf("G00 X1.5 Z0.", "G03 X2. Z-0.25 R0.25")).single() as Ac
        assertEquals(0.0, (a.c - V(-0.25, 0.75)).len, 1e-12)
        assertEquals(90.0, a.sweep * 180 / PI, 1e-9)
    }

    @Test
    fun taper() {
        val rep = Report("테이퍼")
        // 대표값: 외경/내경 × TYPE 1~5 × 정/역 × 노즈R 4가지 × 각 5가지, X40 Z0 에서 Z 로 5 만큼
        for (inner in BOOLS) for (type in 1..5) for (rev in BOOLS) for (r in TaperCalc.NOSE_R_CHOICES) {
            for (deg in listOf(15.0, 30.0, 45.0, 60.0, 75.0)) {
                val dx = 5 * tan(deg * PI / 180)
                val t2 = V(-5.0, 20 + (if (rises(inner, type)) dx else -dx))
                runTaper(rep, inner, type, rev, r, null, V(0.0, 20.0), t2, blank = 4, zClear = 1.0, leg = null)
            }
        }
        // 모서리 R: 볼록·오목, R < r · R = r · R > r
        for (inner in BOOLS) for (type in 1..5) for (rev in BOOLS) for (r in listOf(0.4, 1.2)) {
            for (deg in listOf(30.0, 60.0)) for (cr in listOf(listOf(0.3, 1.5), listOf(1.5, 0.4), listOf(1.2, 1.2))) {
                val dx = 6 * tan(deg * PI / 180)
                val t2 = V(-6.0, 20 + (if (rises(inner, type)) dx else -dx))
                runTaper(rep, inner, type, rev, r, null, V(0.0, 20.0), t2, blank = 4, zClear = 1.0, leg = 6.0, cr = cr)
            }
        }
        // 설계 문서의 손 계산 예 두 개
        runTaper(rep, false, 1, false, 0.4, null, V(0.0, 20.0), V(-5.0, 25.0), blank = 4, zClear = 1.0, leg = null)
        runTaper(rep, true, 1, false, 0.4, null, V(0.0, 20.0), V(-2.0, 17.0), blank = 4, zClear = 1.0, leg = null)
        // C0.1 면취(경사 0.141)를 0.4R 로: 노즈R > 경사지만 양 끝이 볼록이라 닿는다(통과해야 정상)
        runTaper(rep, false, 1, false, 0.4, null, V(0.0, 19.9), V(-0.1, 20.0), blank = 4, zClear = 1.0, leg = null)
        val rnd = Random(20261017)
        repeat(700) {
            val inner = rnd.nextBoolean()
            val type = rnd.nextInt(1, 6)
            var t1: V
            var t2: V
            while (true) {
                val x1 = r3(rnd.nextDouble(4.0, 80.0))
                val z1 = r3(rnd.nextDouble(-20.0, 5.0))
                val dz = r3(if (rnd.nextInt(4) == 0) rnd.nextDouble(0.1, 1.5) else rnd.nextDouble(0.5, 12.0))
                val dxd = r3(if (rnd.nextInt(4) == 0) rnd.nextDouble(0.1, 2.0) else rnd.nextDouble(0.3, 30.0))
                val x2 = r3(x1 + (if (rises(inner, type)) dxd else -dxd))
                val deg = atan2(dxd / 2, dz) * 180 / PI
                t1 = V(z1, x1 / 2)
                t2 = V(r3(z1 - dz), x2 / 2)
                if (x2 >= 0.2 && deg in 2.0..88.0) break
            }
            runTaper(
                rep, inner, type, rnd.nextBoolean(), noseR(rnd), randomTip(rnd), t1, t2,
                blank = rnd.nextInt(-1, 5), zClear = r3(rnd.nextDouble(0.05, 3.0)),
                leg = if (rnd.nextInt(4) == 0) null else r3(rnd.nextDouble(1.0, 8.0)), cr = listOf(cornerR(rnd), cornerR(rnd)),
            )
        }
        rep.finish()
    }

    @Test
    fun doubleTaper() {
        val rep = Report("겹테이퍼")
        // 대표값: 산·골·오르막·내리막 4모양 × 외경/내경 × 정/역 × 노즈R 4가지
        val shapes = listOf(
            listOf(V(0.0, 15.0), V(-5.0, 20.0), V(-10.0, 15.0)),
            listOf(V(0.0, 20.0), V(-5.0, 15.0), V(-10.0, 20.0)),
            listOf(V(0.0, 15.0), V(-4.0, 18.0), V(-10.0, 25.0)),
            listOf(V(0.0, 25.0), V(-6.0, 22.0), V(-10.0, 15.0)),
        )
        for (s in shapes) for (inner in BOOLS) for (rev in BOOLS) for (r in TaperCalc.NOSE_R_CHOICES) {
            runDouble(rep, inner, rev, r, null, s, 1.0, null)
            runDouble(rep, inner, rev, r, null, s, 1.0, 6.0, listOf(1.0, 2.0, 0.5))
        }
        // 경사 1.22 > 노즈R 1.2 인데 T2 골(사이각 74°)에 붙은 원이 T1 을 넘어간다
        runDouble(rep, false, false, 1.2, null, listOf(V(0.0, 25.0), V(-1.0, 24.3), V(-3.0, 30.0)), 1.0, null)
        val rnd = Random(77)
        repeat(400) {
            val pts = mutableListOf(V(r3(rnd.nextDouble(-10.0, 5.0)), r3(rnd.nextDouble(4.0, 80.0)) / 2))
            while (pts.size < 3) {
                val p = pts.last()
                val dz = r3(if (rnd.nextInt(4) == 0) rnd.nextDouble(0.1, 1.5) else rnd.nextDouble(0.5, 10.0))
                val dxd = r3(if (rnd.nextInt(4) == 0) rnd.nextDouble(0.1, 2.0) else rnd.nextDouble(0.3, 20.0))
                val xd = r3(2 * p.x + (if (rnd.nextBoolean()) dxd else -dxd))
                val deg = atan2(dxd / 2, dz) * 180 / PI
                if (xd >= 0.2 && deg in 3.0..87.0) pts += V(r3(p.z - dz), xd / 2)
            }
            runDouble(
                rep, rnd.nextBoolean(), rnd.nextBoolean(), noseR(rnd), randomTip(rnd), pts,
                r3(rnd.nextDouble(0.05, 3.0)), if (rnd.nextInt(4) == 0) null else r3(rnd.nextDouble(1.0, 8.0)),
                listOf(cornerR(rnd), cornerR(rnd), cornerR(rnd)),
            )
        }
        rep.finish()
    }

    @Test
    fun round() {
        val rep = Report("라운드R")
        // 설계 문서의 손 계산 예(TYPE 3, Ø20, SR12.5, CR1, 0.4R)와 TYPE 마다 하나씩
        val fixed = listOf(
            buildRound(3, 12.5, d1 = 20.0, c1 = 1.0, blank = "l1"),
            buildRound(1, 10.0, l1 = 12.0, c1 = 1.0),
            buildRound(2, 8.0, d1 = 10.0, l1 = 5.0, c1 = 0.5, blank = "l2"),
            buildRound(2, 8.0, d1 = 10.0, l1 = 5.0, c1 = 0.5, ref = L2Ref.A),
            buildRound(4, 10.0, d1 = 12.0, c1 = 0.5, blank = "l1"),
            buildRound(5, 15.0, d1 = 10.0, d2 = 24.0, l1 = 4.0, c1 = 0.5, c2 = 1.0, blank = "l2"),
            buildRound(6, 10.0, d1 = 8.0, d2 = 30.0, l1 = 3.0, l2 = 7.165, c1 = 0.5, c2 = 0.5),
            // 주 원호가 180° 가까운 공(목 Ø2)과 아령(축 Ø3)
            buildRound(4, 10.0, d1 = 2.0, c1 = 0.5),
            buildRound(2, 19.0, d1 = 3.0, l1 = 5.0, c1 = 0.3),
        )
        for (c in fixed) for (r in listOf(0.2, 0.4, 0.8, 1.2)) runRound(rep, c.withNose(r))
        val rnd = Random(4242)
        repeat(600) { runRound(rep, randomRound(rnd.nextInt(1, 7), rnd)) }
        rep.finish()
    }

    /** 내경 라운드: 외경 도면을 이 파일에서 따로 뒤집은 구멍, 재료는 위. 막힘 · 관통 */
    @Test
    fun roundInner() {
        val rep = Report("내경 라운드R")
        // 설계 문서의 내경 예(TYPE 3 Ø20 입구 깊이 10, TYPE 5 Ø40 → Ø30 막힘 · 관통)와 외경 대표값을 뒤집은 것
        val fixed = listOf(
            buildRound(3, 12.5, d1 = 20.0, c1 = 1.0, blank = "l1", leg = 10.0, inner = true),
            buildRound(5, 25.0, d1 = 30.0, d2 = 40.0, l1 = 6.0, blank = "l2", inner = true),
            buildRound(5, 25.0, d1 = 30.0, d2 = 40.0, l1 = 6.0, blank = "l2", inner = true, through = true),
            buildRound(2, 8.0, d1 = 10.0, l1 = 5.0, c1 = 0.5, blank = "l2", inner = true),
            buildRound(2, 8.0, d1 = 10.0, l1 = 5.0, c1 = 0.5, ref = L2Ref.A, inner = true),
            buildRound(4, 10.0, d1 = 12.0, c1 = 0.5, blank = "l1", inner = true),
            buildRound(5, 15.0, d1 = 10.0, d2 = 24.0, l1 = 4.0, c1 = 0.5, c2 = 1.0, blank = "l2", inner = true),
            buildRound(6, 10.0, d1 = 8.0, d2 = 30.0, l1 = 3.0, l2 = 7.165, c1 = 0.5, c2 = 0.5, inner = true),
            buildRound(6, 10.0, d1 = 8.0, d2 = 30.0, l1 = 3.0, l2 = 7.165, c1 = 0.5, c2 = 0.5, inner = true, through = true),
            buildRound(4, 10.0, d1 = 2.0, c1 = 0.5, inner = true),
            buildRound(2, 19.0, d1 = 3.0, l1 = 5.0, c1 = 0.3, inner = true),
        )
        for (c in fixed) for (r in listOf(0.2, 0.4, 0.8, 1.2)) runRound(rep, c.withNose(r))
        val rnd = Random(4343)
        repeat(600) { runRound(rep, randomRound(rnd.nextInt(2, 7), rnd, inner = true)) }
        rep.finish()
    }

    @Test
    fun arc3() {
        val rep = Report("3점호")
        runArc3(rep, false, 0.4, null, V(0.0, 10.0), V(-2.0, 12.0), V(-6.0, 13.0), 1.0)
        runArc3(rep, true, 0.4, null, V(0.0, 10.0), V(-3.0, 8.0), V(-8.0, 7.0), 1.0)
        // 외경 R 홈 X40 Z0 → X35 Z-3 → X40 Z-6 (오목, 시작 접선이 아래로 가파름)
        runArc3(rep, false, 0.4, null, V(0.0, 20.0), V(-3.0, 17.5), V(-6.0, 20.0), 1.0)
        val rnd = Random(333)
        repeat(400) {
            val upper = rnd.nextBoolean()
            val bigR = r3(rnd.nextDouble(0.3, 30.0))
            val (lo0, hi0) = if (upper) 3.0 to 177.0 else 183.0 to 357.0
            var a: Double
            var b: Double
            do {
                a = rnd.nextDouble(lo0, hi0)
                b = rnd.nextDouble(lo0, hi0)
            } while (abs(a - b) < 10)
            val lo = min(a, b)
            val hi = max(a, b)
            val forward = rnd.nextBoolean()
            // 위쪽 반원은 반시계(각이 커짐)로 가면 Z 가 준다 = 정방향. 아래쪽 반원은 반대
            val ccw = if (upper) forward else !forward
            val th = listOf(if (ccw) lo else hi, rnd.nextDouble(lo + 2, hi - 2), if (ccw) hi else lo).map { it * PI / 180 }
            val minSin = if (!upper && 270.0 in lo..hi) -1.0 else min(sin(lo * PI / 180), sin(hi * PI / 180))
            val c = V(r3(rnd.nextDouble(-20.0, 5.0)), r3(rnd.nextDouble(0.5 - bigR * minSin, 30.5 - bigR * minSin)))
            val p = th.map { val q = polar(c, bigR, it); V(r3(q.z), r3(2 * q.x) / 2) }
            runArc3(rep, rnd.nextBoolean(), noseR(rnd), randomTip(rnd), p[0], p[1], p[2], r3(rnd.nextDouble(0.0, 3.0)))
        }
        rep.finish()
    }

    @Test
    fun groove() {
        val rep = Report("홈")
        for (ref in GrooveRef.entries) for (rg in listOf(0.2, 0.4)) {
            runGroove(rep, GrooveInput(zStart = -10.0, width = 8.0, od = 40.0, bottom = 30.0, insert = 3.0, ref = ref), rg)
            runGroove(rep, GrooveInput(zStart = -10.0, width = 3.0, od = 40.0, bottom = 30.0, insert = 3.0, ref = ref), rg)
        }
        val rnd = Random(55)
        repeat(250) {
            val width = r3(rnd.nextDouble(1.0, 15.0))
            val insert = if (rnd.nextInt(10) == 0) width else r3(rnd.nextDouble(0.5, width))
            val od = r3(rnd.nextDouble(10.0, 100.0))
            val bottom = if (rnd.nextInt(20) == 0) 0.0 else r3(rnd.nextDouble(max(0.0, od - 30), od - 0.5))
            val inp = GrooveInput(
                zStart = r3(rnd.nextDouble(-30.0, 0.0)), width = width, od = od, bottom = bottom, insert = insert,
                overlap = r3(rnd.nextDouble(0.0, 0.7 * insert)),
                ref = if (rnd.nextBoolean()) GrooveRef.LEFT else GrooveRef.RIGHT,
                xClear = r3(rnd.nextDouble(0.0, 3.0)),
            )
            // 모서리 R 이 홈 깊이보다 크면 벽이 남지 않아 검사할 것이 없다
            runGroove(rep, inp, r3(rnd.nextDouble(0.05, minOf(0.8, insert / 2, (od - bottom) / 2 - 0.05))))
        }
        rep.finish()
    }

    @Test
    fun noseCompRandom() {
        val rep = Report("NoseComp 무작위 윤곽")
        // 대표값: 면취, 노즈보다 좁은 V 홈 바닥, R < r 인 오목 원호
        val chamfer = polyline(V(0.0, 15.0), V(0.0, 20.0), V(-5.0, 25.0), V(-10.0, 25.0))
        val vee = polyline(V(0.0, 20.0), V(-3.0, 20.0), V(-5.0, 17.0), V(-5.3, 17.0), V(-7.3, 20.0), V(-10.0, 20.0))
        val pocket = listOf(
            Ln(V(0.0, 20.0), V(-3.0, 20.0)), Ln(V(-3.0, 20.0), V(-3.0, 17.3)),
            Ac(V(-3.0, 17.3), V(-3.3, 17.0), V(-3.3, 17.3), cw = true),
            Ln(V(-3.3, 17.0), V(-6.0, 17.0)), Ln(V(-6.0, 17.0), V(-9.0, 17.0)),
        )
        // 단면을 올라가 날카로운 모서리에서 바로 오목 원호(R4.03)로 내려가는 모양
        val edge = listOf(
            Ln(V(0.0, 10.0), V(-3.0, 10.0)), Ln(V(-3.0, 10.0), V(-3.0, 13.0)),
            Ac(V(-3.0, 13.0), V(-6.0, 11.0), V(-6.5, 15.0), cw = true), Ln(V(-6.0, 11.0), V(-10.0, 11.0)),
        )
        for (p in listOf(chamfer, vee, pocket, edge)) for (inner in BOOLS) for (rev in BOOLS) for (r in listOf(0.2, 0.4, 0.8)) {
            runNoseComp(rep, p, inner, rev, r, physicalTip(inner, rev))
        }
        val rnd = Random(9001)
        repeat(500) {
            runNoseComp(rep, randomProfile(rnd), rnd.nextBoolean(), rnd.nextBoolean(), r3(rnd.nextDouble(0.1, 1.6)), rnd.nextInt(0, 10))
        }
        rep.finish()
    }

    // ---------------------------------------------------------------- 계산기마다

    private fun runTaper(
        rep: Report, inner: Boolean, type: Int, reverse: Boolean, r: Double, tip: Int?, t1: V, t2: V,
        blank: Int, zClear: Double, leg: Double?, cr: List<Double> = listOf(0.0, 0.0),
    ) {
        val deg = atan2(abs(t2.x - t1.x), t1.z - t2.z) * 180 / PI
        val v = arrayOf<Double?>(2 * t1.x, t1.z, 2 * t2.x, t2.z, deg)
        if (blank in 0..4) v[blank] = null
        // TYPE 표(설계 문서): TYPE 1·2 는 T1 에 단면, TYPE 2·4 는 T2 에 단면, 나머지는 수평. 단면은 경사의 오르내림을 잇고 X0 에서 멈춘다.
        // 시작·끝점 칸은 단면이면 X(직경), 수평이면 Z. leg = null 이면 비워 둔다(5 mm, 단면은 반경 5)
        val up = if (t2.x > t1.x) 1.0 else -1.0
        val len = leg ?: 5.0
        val frontV = type <= 2
        val backV = type == 2 || type == 4
        val front = if (frontV) V(t1.z, max(t1.x - up * len, 0.0)) else V(t1.z + len, t1.x)
        val back = if (backV) V(t2.z, max(t2.x + up * len, 0.0)) else V(t2.z - len, t2.x)
        val inp = TaperInput(
            inner = inner, type = type, reverse = reverse, noseR = r, tip = tip,
            x1 = v[0], z1 = v[1], x2 = v[2], z2 = v[3], angleDeg = v[4], zClear = zClear, cornerR1 = cr[0], cornerR2 = cr[1],
            start = leg?.let { if (frontV) 2 * front.x else front.z }, end = leg?.let { if (backV) 2 * back.x else back.z },
        )
        val res = TaperCalc.calc(inp)
        checkTaper(rep, "$inp → 경고 ${res.warnings}", res, inner, reverse, r, tip, listOf(t1, t2), listOf(front, t1, t2, back), cr)
    }

    private fun runDouble(
        rep: Report, inner: Boolean, reverse: Boolean, r: Double, tip: Int?, t: List<V>, zClear: Double, leg: Double?,
        cr: List<Double> = listOf(0.0, 0.0, 0.0),
    ) {
        val len = leg ?: 5.0
        val inp = DoubleTaperInput(
            inner = inner, reverse = reverse, noseR = r, tip = tip,
            x1 = 2 * t[0].x, z1 = t[0].z, x2 = 2 * t[1].x, z2 = t[1].z, x3 = 2 * t[2].x, z3 = t[2].z,
            zClear = zClear, cornerR1 = cr[0], cornerR2 = cr[1], cornerR3 = cr[2],
            start = leg?.let { t[0].z + it }, end = leg?.let { t[2].z - it },
        )
        val pts = listOf(V(t[0].z + len, t[0].x)) + t + V(t[2].z - len, t[2].x)
        val res = TaperCalc.calcDouble(inp)
        checkTaper(rep, "$inp → 경고 ${res.warnings}", res, inner, reverse, r, tip, t, pts, cr)
    }

    /** pts = 그림 오른쪽부터 [시작점, T1, T2, (T3,) 끝점], cr = T 점마다 모서리 R */
    private fun checkTaper(
        rep: Report, id: String, res: TaperResult, inner: Boolean, reverse: Boolean, r: Double, tipIn: Int?,
        drawn: List<V>, pts: List<V>, cr: List<Double>,
    ) {
        rep.cases++
        val drawing = filletPolyline(if (reverse) pts.reversed() else pts, if (reverse) cr.reversed() else cr)
        val sol = res.solution
        if (drawing == null) {
            rep.stop(res.warnings.toString())
            if (res.warnings != listOf(TaperWarning.CORNER_R_TOO_BIG)) rep.bad(id, "모서리R", "모서리 R 이 안 들어가는데 ${res.warnings}")
            return
        }
        val reg = regionOf(drawing, materialBelow = !inner)
        val leftMat = leftIsMaterial(reg, drawing)
        val sign = if (leftMat) -1.0 else 1.0
        if (sol == null) {
            rep.stop(res.warnings.toString())
            when (res.warnings) {
                listOf(TaperWarning.NOSE_UNREACHABLE) -> if (drawing.none { e -> !anyReachable(e, r, reg, sign) }) {
                    rep.bad(id, "지나친멈춤", "NOSE_UNREACHABLE 인데 도면의 모든 요소에 노즈 원이 닿을 수 있다")
                }
                // 인선을 시작·끝좌표에 두려면 공구가 되돌아가야 하는 경우: 보정점은 요소를 따라 꼭짓점에서 많아야 r + |팁 벡터|
                // 밀리므로 앞뒤 요소의 직선 부분이 그보다 짧을 때만 있을 수 있다
                listOf(TaperWarning.BAD_LEG) -> if (min(drawing.first().length, drawing.last().length) > (1 + sqrt(2.0)) * r + 1e-9) {
                    rep.bad(id, "지나친멈춤", "BAD_LEG 인데 앞뒤 요소가 노즈R 에 비해 짧지 않다")
                }
                else -> rep.bad(id, "예상밖멈춤", "${res.warnings}")
            }
            return
        }
        rep.solved++
        for ((k, p) in sol.points.withIndex()) {
            if ((vOf(p.vertex) - drawn[k]).len > 1e-6) rep.bad(id, "도면점", "T${k + 1} 꼭짓점 ${vOf(p.vertex)} ≠ 입력 ${drawn[k]}")
        }
        sameDrawing(rep, id, sol.drawing, drawing)
        val expTip = tipIn ?: physicalTip(inner, reverse)
        if (sol.tip != expTip) rep.bad(id, "팁", "팁 ${sol.tip}, 바이트가 깎는 모서리로 본 팁 $expTip")
        val expSide = if (leftMat) Side.G42 else Side.G41
        if (sol.side != expSide) rep.bad(id, "보정쪽", "${sol.side}, 재료 반대쪽은 $expSide")
        // 모서리 R 결과: 접점 = 도면 원호의 처음·끝(가공 순서), 보정 R = 볼록 R + r · 오목 R − r, 오목 R ≤ r 이면 없음
        val arcs = drawing.filterIsInstance<Ac>()
        val corners = sol.points.mapNotNull { p -> p.corner?.let { p to it } }
        if (corners.size != arcs.size) rep.bad(id, "모서리R", "모서리 결과 ${corners.size}개 ≠ 도면 원호 ${arcs.size}개")
        for ((p, c) in corners) {
            val a = arcs.firstOrNull { (it.c - vOf(c.center)).len < 1e-6 }
            if (a == null || abs(a.r - c.r) > 1e-9 || a.cw != c.cw || p.nodes.size != 2 ||
                (vOf(p.nodes[0].drawn) - a.a).len > 1e-6 || (vOf(p.nodes[1].drawn) - a.b).len > 1e-6
            ) {
                rep.bad(id, "모서리R", "모서리 결과 $c ${p.nodes} 가 도면 원호와 다르다")
                continue
            }
            val want = if (concave(a, sign)) a.r - r else a.r + r
            val got = c.progR
            if (if (want <= 1e-9) got != null else got == null || abs(got - want) > 1e-9) rep.bad(id, "모서리R", "보정 R $got, R ± r 은 $want")
        }
        val small = drawing.indices.filter { k -> concave(drawing[k], sign) && (drawing[k] as Ac).r <= r + 1e-9 }.toSet()
        val warned = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings
        if (warned != small.isNotEmpty()) rep.bad(id, "오목경고", "CONCAVE_R_LE_NOSE=$warned, R ≤ r 인 오목 원호 $small")
        // 직접 보정 NC 는 진입 · 형상 · 후퇴. 시작·끝은 인선을 그 좌표에 둬서 노즈 접점이 바깥 끝에서 r 까지 안쪽일 수 있다.
        // 시작점을 비우면 시작 요소는 깎지 않는다(진입점에서 첫 꺾임점′ 으로). 끝점을 비우면 끝 요소도 깎지 않는다(마지막 꺾임점′ 에서 후퇴)
        val skip = (if (sol.fromStart) emptySet() else setOf(0)) + (if (sol.toEnd) emptySet() else setOf(drawing.lastIndex))
        checkPath(
            rep, id, drawing, reg, leftMat, sol.program.segs.map(::el), sol.center.segs.map(::el), sol.tip, r,
            sol.ncG40, ncSkip = skip, looseApproach = false, legTrim = r, exitMove = true,
        ) { it in small && warned }
        checkEnds(rep, id, sol, drawing, sign, r)
        checkG4x(rep, id, sol.ncG41G42, drawing, reg, expSide, sol.tip, r, sol.tip == physicalTip(inner, reverse), sol.fromStart, sol.toEnd)
    }

    /**
     * 직접 보정 NC 의 시작·끝(진입 G00 다음 첫 형상 점, 후퇴 앞 점)이 인선을 시작·끝점 좌표에 둔 점인지:
     * 앞뒤 요소를 공구 쪽으로 r 밀고 팁 벡터를 더한 선 위에서 시작·끝점에 내린 발. 시작점을 비웠으면 끝만 본다.
     * 끝점을 비웠으면 끝 요소의 보정선 위, 마지막 꺾임점 곁(끝 요소에 노즈가 닿기 시작하는 자리)에서 멈춰야 한다 —
     * 경사가 끝까지 깎였는지는 [checkPath] 가 본다.
     */
    private fun checkEnds(rep: Report, id: String, sol: TaperSolution, drawing: List<El>, sign: Double, r: Double) {
        val tv = tipVec(sol.tip, r)
        fun foot(e: El, p: V): V {
            val u = e.tangent(0.0)
            val off = e.leftNormal(0.0) * (sign * r) + tv
            return p + off - u * (off dot u)
        }
        val s0 = foot(drawing.first(), drawing.first().a)
        val e0 = foot(drawing.last(), drawing.last().b)
        val pts = ncPoints(sol.ncG40)
        // 시작 요소가 수평이면 진입점·시작점′·다음 점이 한 직선이라 시작점′ 을 따로 쓰지 않는다 — 그때는 진입선 위에 있으면 된다
        val a = pts[0]
        val b = pts[1]
        val onEntry = abs((b - a).unit() cross (s0 - a)) <= NC_TOL && ((s0 - a) dot (b - s0)) >= -NC_TOL
        if (sol.fromStart && (b - s0).len > NC_TOL && !onEntry) rep.bad(id, "시작점", "NC 첫 형상 점 $b, 인선을 시작점에 둔 점은 $s0  NC=${sol.ncG40}")
        val end = pts[pts.size - 2]
        if (sol.toEnd) {
            if ((end - e0).len > NC_TOL) rep.bad(id, "끝점", "NC 끝 점 $end, 인선을 끝점에 둔 점은 $e0  NC=${sol.ncG40}")
        } else {
            val l = drawing.last()
            val u = l.tangent(0.0)
            val off = l.leftNormal(0.0) * (sign * r) + tv
            // 보정점은 요소를 따라 꼭짓점에서 많아야 r + |팁 벡터| 밀린다
            val along = (end - (l.a + off)) dot u
            if (abs(u cross (end - (l.a + off))) > NC_TOL || along > (1 + sqrt(2.0)) * r + NC_TOL) {
                rep.bad(id, "끝점", "끝점을 비웠는데 NC 끝 점 $end 가 끝 요소 보정선의 처음이 아니다  NC=${sol.ncG40}")
            }
        }
    }

    /**
     * G41/G42 프로그램: 첫 줄 주석 (R… T…) = 노즈R·팁, G00 진입 → G4x G01 시작점(길이 ≥ r) → 형상(도면 좌표) → G40 G00 후퇴(길이 ≥ r).
     * 형상 줄은 도면 위에 있고 도면의 꼭짓점을 모두 지난다(시작점을 비웠으면 시작 요소는 빼고). 첫 형상 줄만은 시작 요소의 연장선 위여도 된다
     * (공구 쪽으로 띄운 G00 점에서 그 선에 직각으로 들어간 뒤 선을 따라가는 줄). 표준 팁이면 진입점·후퇴점(G40 상태의 인선)에서 노즈가 재료 밖이다.
     * 끝점을 비웠으면(toEnd = false) 끝 요소는 프로그램에 없다.
     */
    private fun checkG4x(
        rep: Report, id: String, nc: List<String>, drawing: List<El>, reg: Region, side: Side, tip: Int, r: Double, standard: Boolean,
        fromStart: Boolean, toEnd: Boolean,
    ) {
        val m = Regex("""\(R([\d.]+) T(\d)\)""").matchEntire(nc.firstOrNull() ?: "")
        if (m == null || abs(m.groupValues[1].toDouble() - r) > 0.0005 || m.groupValues[2].toInt() != tip) {
            rep.bad(id, "G41/G42", "첫 줄 주석 ${nc.firstOrNull()} ≠ 노즈R $r 팁 $tip")
        }
        val body = nc.drop(1)
        if (body.size < 4 || !body[1].startsWith("${side.name} G01 ") || !body.last().startsWith("G40 G00 ")) {
            rep.bad(id, "G41/G42", "보정 시작·취소 줄이 약속과 다르다: $nc")
            return
        }
        val els = parseNc(body.map { it.replace(Regex("^G4[012] "), "") })
        if (els.first().length < r - 1e-9) rep.bad(id, "G41/G42", "보정 시작 이동 ${fmt(els.first().length)} < 노즈R $r")
        if (els.last().length < r - 1e-9) rep.bad(id, "G41/G42", "보정 취소 이동 ${fmt(els.last().length)} < 노즈R $r")
        val shape = els.drop(1).dropLast(1)
        val d0 = drawing.first()
        for ((i, e) in shape.withIndex()) {
            val n = max(20, ceil(e.length / STEP).toInt())
            val dev = (0..n).maxOf { k -> drawing.minOf { it.dist(e.at(k.toDouble() / n)) } }
            // 시작 요소의 무한 연장선까지의 거리
            val onLead = i == 0 && e is Ln && d0 is Ln && (0..n).maxOf { k -> abs((d0.b - d0.a).unit() cross (e.at(k.toDouble() / n) - d0.a)) } <= NC_TOL
            if (dev > NC_TOL && !onLead) rep.bad(id, "G41/G42", "형상 줄이 도면에서 ${fmt(dev)} mm 벗어남  NC=$nc")
        }
        val ncPts = listOf(shape.first().a) + shape.map { it.b }
        // 시작·끝점을 비웠으면 그 요소는 프로그램에 없다
        val cut = drawing.drop(if (fromStart) 0 else 1).dropLast(if (toEnd) 0 else 1)
        for (q in (if (fromStart) listOf(drawing.first().a) else emptyList()) + cut.map { it.b }) {
            if (ncPts.none { (it - q).len <= NC_TOL }) rep.bad(id, "G41/G42", "도면 꼭짓점 $q 가 NC 에 없다  NC=$nc")
        }
        if (standard) {
            val c = els.first().a - tipVec(tip, r)
            if (reg.inside(c) || reg.dist(c) < r - NC_TOL) rep.bad(id, "G41/G42", "진입점에서 노즈가 재료에 닿는다  NC=$nc")
            val d = els.last().b - tipVec(tip, r)
            if (reg.inside(d) || reg.dist(d) < r - NC_TOL) rep.bad(id, "G41/G42", "후퇴점에서 노즈가 재료에 닿는다  NC=$nc")
        }
    }

    internal class RoundCase(
        val input: RoundInput, val outline: List<El>, val bigR: Double, val zc: Double,
        val expect: Map<String, Double>, val corners: List<Double>,
    ) {
        fun withNose(r: Double) = RoundCase(input.copy(noseR = r), outline, bigR, zc, expect, corners)
    }

    /**
     * 설계 문서 TYPE 표의 식으로 이론 윤곽(모서리 R 전)과 채워질 값을 만든다. 정해지는 값(TYPE 2 의 L2, 3·4 의 L, 5 의 L2)은
     * 여기서 계산하고, [blank] 이름의 칸을 비워 넣는다.
     */
    internal fun buildRound(
        type: Int, bigR: Double, d1: Double = 0.0, d2: Double = 0.0, l1: Double = 0.0, l2: Double = 0.0,
        c1: Double = 0.0, c2: Double = 0.0, ref: L2Ref = L2Ref.B, blank: String? = null,
        r: Double = 0.4, tip: Int? = null, zClear: Double = 1.0, leg: Double = 5.0, inner: Boolean = false, through: Boolean = false,
    ): RoundCase {
        val o = V(0.0, 0.0)
        val outline: List<El>
        val zc: Double
        val exp: Map<String, Double>
        when (type) {
            1 -> {
                val h = sqrt(bigR * bigR - l1 * l1 / 4)
                zc = -l1 / 2
                outline = listOf(Ln(o, V(0.0, h)), Ac(V(0.0, h), V(-l1, h), V(zc, 0.0), false), Ln(V(-l1, h), V(-l1, 0.0)))
                exp = mapOf("mainR" to bigR, "l1" to l1)
            }
            2 -> {
                val a = d1 / 2
                val s = sqrt(bigR * bigR - a * a)
                zc = -l1 - s
                val zl = zc - s
                outline = listOf(
                    Ln(o, V(0.0, a)), Ln(V(0.0, a), V(-l1, a)),
                    Ac(V(-l1, a), V(zl, a), V(zc, 0.0), false), Ln(V(zl, a), V(zl - leg, a)),
                )
                exp = mapOf("mainR" to bigR, "d1" to d1, "l1" to l1, "l2" to 2 * s + (if (ref == L2Ref.A) l1 else 0.0))
            }
            3, 4 -> {
                val h = d1 / 2
                val len = bigR + (if (type == 4) 1 else -1) * sqrt(max(bigR * bigR - h * h, 0.0))
                zc = -bigR
                outline = listOf(Ac(o, V(-len, h), V(zc, 0.0), false), Ln(V(-len, h), V(-len - leg, h)))
                exp = mapOf("mainR" to bigR, "d1" to d1, "l1" to len)
            }
            5 -> {
                val a = d1 / 2
                val b = d2 / 2
                val u = sqrt(bigR * bigR - a * a)
                val l2v = u - sqrt(max(bigR * bigR - b * b, 0.0))
                zc = -l1 - u
                val z2 = -l1 - l2v
                outline = listOf(
                    Ln(o, V(0.0, a)), Ln(V(0.0, a), V(-l1, a)),
                    Ac(V(-l1, a), V(z2, b), V(zc, 0.0), false), Ln(V(z2, b), V(z2 - leg, b)),
                )
                exp = mapOf("mainR" to bigR, "d1" to d1, "d2" to d2, "l1" to l1, "l2" to l2v)
            }
            else -> {
                val a = d1 / 2
                val b = d2 / 2
                zc = -l1 - sqrt(bigR * bigR - a * a)
                val zf = -l1 - l2
                val dz = zf - zc
                val hf = sqrt(bigR * bigR - dz * dz)
                outline = listOf(
                    Ln(o, V(0.0, a)), Ln(V(0.0, a), V(-l1, a)), Ac(V(-l1, a), V(zf, hf), V(zc, 0.0), false),
                    Ln(V(zf, hf), V(zf, b)), Ln(V(zf, b), V(zf - leg, b)),
                )
                exp = mapOf("mainR" to bigR, "d1" to d1, "d2" to d2, "l1" to l1, "l2" to l2)
            }
        }
        // 내경: 2 는 끝 면만 뺀다. 3~6 은 외경 끝(깊이 K)이 구멍 입구 Z0 이 되게 Z 를 뒤집고(뒤집으면 시계 ↔ 반시계) 거꾸로 간다.
        // 관통은 바닥 면을 뺀다
        val depth = -outline.last().b.z
        fun m(p: V) = V(-p.z - depth, p.x)
        val shape = when {
            !inner -> outline
            type == 2 -> outline.drop(1)
            else -> outline.reversed().map { e ->
                when (val q = e.reversed()) {
                    is Ln -> Ln(m(q.a), m(q.b))
                    is Ac -> Ac(m(q.a), m(q.b), m(q.c), !q.cw)
                }
            }.let { if (through) it.dropLast(1) else it }
        }
        fun pick(k: String) = if (k == blank) null else exp[k]
        val inp = RoundInput(
            type = type, mainR = pick("mainR"), cornerR1 = c1, cornerR2 = if (type >= 5) c2 else null,
            d1 = pick("d1"), d2 = pick("d2"), l1 = pick("l1"), l2 = pick("l2"), l2Ref = ref,
            noseR = r, tip = tip, zClear = zClear, legLen = leg, inner = inner, through = through,
        )
        val center = if (inner && type != 2) -zc - depth else zc
        return RoundCase(inp, shape, bigR, center, exp, if (type >= 5) listOf(c1, c2) else listOf(c1))
    }

    internal fun randomRound(type: Int, rnd: Random, inner: Boolean = false): RoundCase {
        fun cr() = if (rnd.nextInt(10) < 3) 0.0 else r3(rnd.nextDouble(0.05, 2.5))
        fun rr(a: Double, b: Double) = r3(rnd.nextDouble(a, b))
        val r = noseR(rnd)
        val tip = if (rnd.nextInt(5) == 0) rnd.nextInt(0, 10) else null
        // 내경 5 · 6 은 넷에 하나 관통
        val through = inner && type >= 5 && rnd.nextInt(4) == 0
        val zClear = rr(0.0, 3.0)
        val leg = rr(1.0, 8.0)
        fun blankOf(vararg k: String) = if (rnd.nextInt(k.size + 1) == 0) null else k[rnd.nextInt(k.size)]
        return when (type) {
            1 -> {
                val bigR = rr(2.0, 40.0)
                buildRound(1, bigR, l1 = rr(0.3 * bigR, 1.9 * bigR), c1 = cr(), r = r, tip = tip, zClear = zClear, leg = leg)
            }
            2 -> {
                val d1 = rr(2.0, 40.0)
                buildRound(
                    2, rr(d1 / 2 + 0.3, d1 / 2 + 20), d1 = d1, l1 = rr(0.5, 12.0), c1 = cr(),
                    ref = if (rnd.nextBoolean()) L2Ref.A else L2Ref.B, blank = blankOf("mainR", "d1", "l2"),
                    r = r, tip = tip, zClear = zClear, leg = leg, inner = inner,
                )
            }
            3 -> {
                val bigR = rr(2.0, 40.0)
                val half = rnd.nextInt(10) == 0 // 반구: 원통과 접해서 이어진다
                val d1 = if (half) 2 * bigR else rr(0.6 * bigR, 2 * bigR)
                buildRound(
                    3, bigR, d1 = d1, c1 = cr(), blank = if (half) null else blankOf("mainR", "d1", "l1"), r = r, tip = tip, zClear = zClear, leg = leg,
                    inner = inner,
                )
            }
            4 -> {
                val bigR = rr(2.0, 40.0)
                buildRound(
                    4, bigR, d1 = rr(0.6 * bigR, 1.9 * bigR), c1 = cr(), blank = blankOf("mainR", "d1", "l1"), r = r, tip = tip, zClear = zClear, leg = leg,
                    inner = inner,
                )
            }
            5 -> {
                val bigR = rr(3.0, 40.0)
                val tangent = rnd.nextInt(10) == 0 // R = D2/2: 큰 원통과 접해서 이어진다
                val d2 = if (tangent) 2 * bigR else rr(0.8 * bigR, 1.96 * bigR)
                val d1 = rr(0.3 * bigR, d2 - 0.6)
                buildRound(
                    5, bigR, d1 = d1, d2 = d2, l1 = rr(1.0, 10.0), c1 = cr(), c2 = cr(),
                    blank = if (tangent) null else blankOf("mainR", "d2", "d1", "l2"), r = r, tip = tip, zClear = zClear, leg = leg,
                    inner = inner, through = through,
                )
            }
            else -> {
                var c: RoundCase? = null
                while (c == null) {
                    val bigR = rr(3.0, 30.0)
                    val d1 = rr(0.4 * bigR, 1.6 * bigR)
                    val l1 = rr(1.0, 10.0)
                    val u = sqrt(bigR * bigR - d1 * d1 / 4)
                    val l2 = r3(u - rnd.nextDouble(-0.9 * bigR, 0.9 * u))
                    val dz = u - l2 // 플랜지 면 − 구 중심
                    if (l2 < 0.1 || abs(dz) >= bigR - 0.01) continue
                    val top = if (dz <= 0) bigR else sqrt(bigR * bigR - dz * dz)
                    val d2 = r3(2 * (top + rnd.nextDouble(0.3, 10.0)))
                    c = buildRound(
                        6, bigR, d1 = d1, d2 = d2, l1 = l1, l2 = l2, c1 = cr(), c2 = cr(), r = r, tip = tip, zClear = zClear, leg = leg,
                        inner = inner, through = through,
                    )
                }
                c
            }
        }
    }

    private fun runRound(rep: Report, rc: RoundCase) {
        rep.cases++
        val inp = rc.input
        val res: RoundResult = RoundCalc.calc(inp)
        val id = "$inp → 경고 ${res.warnings}"
        val sol = res.solution
        if (sol == null) {
            rep.stop(res.warnings.toString())
            val w = res.warnings.singleOrNull()
            if (w != RoundWarning.CORNER_R_TOO_BIG && w != RoundWarning.NOSE_UNREACHABLE) rep.bad(id, "예상밖멈춤", "${res.warnings}")
            return
        }
        rep.solved++
        val f = sol.filled!!
        val got = mapOf("mainR" to f.mainR, "d1" to f.d1, "d2" to f.d2, "l1" to f.l1, "l2" to f.l2)
        for ((k, e) in rc.expect) {
            val g = got[k]
            if (g == null || abs(g - e) > 1e-6) rep.bad(id, "채운값", "$k = $g, 식으로는 $e")
        }
        val drawing = sol.drawing.segs.map(::el)
        // 도면 윤곽 점검: 꼭짓점은 이론 윤곽 위, 모서리 원호는 입력 R 이고 이웃과 접한다, 주 원호는 축 위 중심
        if ((drawing.first().a - rc.outline.first().a).len > 1e-9) rep.bad(id, "도면", "시작 ${drawing.first().a} ≠ ${rc.outline.first().a}")
        if ((drawing.last().b - rc.outline.last().b).len > 1e-6) rep.bad(id, "도면", "끝 ${drawing.last().b} ≠ ${rc.outline.last().b}")
        for ((k, e) in drawing.withIndex()) {
            for (p in listOf(e.a, e.b)) {
                val d = rc.outline.minOf { it.dist(p) }
                if (d > 1e-6) rep.bad(id, "도면", "${k}번 요소의 끝점 $p 가 이론 윤곽에서 ${fmt(d)} 떨어졌다")
            }
            if (e !is Ac) continue
            if (abs((e.b - e.c).len - e.r) > 1e-9) rep.bad(id, "도면", "${k}번 원호 끝점이 원 위에 없다")
            val main = abs(e.r - rc.bigR) <= 1e-6 && (e.c - V(rc.zc, 0.0)).len <= 1e-6
            if (main) continue
            if (rc.corners.none { abs(it - e.r) <= 1e-9 }) rep.bad(id, "도면", "${k}번 원호 R ${e.r} 가 모서리 R ${rc.corners} 도 주 R 도 아니다")
            val prev = drawing.getOrNull(k - 1)
            val next = drawing.getOrNull(k + 1)
            if (prev != null && (prev.tangent(1.0) - e.tangent(0.0)).len > 1e-6) rep.bad(id, "도면", "${k}번 모서리 원호가 앞 요소와 접하지 않는다")
            if (next != null && (e.tangent(1.0) - next.tangent(0.0)).len > 1e-6) rep.bad(id, "도면", "${k}번 모서리 원호가 뒤 요소와 접하지 않는다")
        }
        val reg = regionOf(drawing, materialBelow = !inp.inner)
        val leftMat = leftIsMaterial(reg, drawing)
        val sign = if (leftMat) -1.0 else 1.0
        val expTip = inp.tip ?: physicalTip(inp.inner, false)
        if (sol.tip != expTip) rep.bad(id, "팁", "팁 ${sol.tip}, 입력 ${inp.tip} · 바이트가 깎는 모서리로 본 팁 $expTip")
        val expSide = if (leftMat) Side.G42 else Side.G41
        if (sol.side != expSide) rep.bad(id, "보정쪽", "${sol.side}, 재료 반대쪽은 $expSide")
        val r = inp.noseR
        val small = drawing.indices.filter { k -> concave(drawing[k], sign) && (drawing[k] as Ac).r <= r + 1e-9 }.toSet()
        val warned = RoundWarning.CONCAVE_R_LE_NOSE in res.warnings
        if (warned != small.isNotEmpty()) rep.bad(id, "오목경고", "CONCAVE_R_LE_NOSE=$warned, R ≤ r 인 오목 원호 $small")
        if (inp.inner) {
            // 구멍은 축 대칭: 노즈 원이 축 너머 맞은편 벽(도면을 X 로 뒤집은 것, 재료는 아래)도 파지 않는다
            val far = drawing.map { e ->
                when (e) {
                    is Ln -> Ln(V(e.a.z, -e.a.x), V(e.b.z, -e.b.x))
                    is Ac -> Ac(V(e.a.z, -e.a.x), V(e.b.z, -e.b.x), V(e.c.z, -e.c.x), !e.cw)
                }
            }
            val (cl, at) = clearance(sol.program.segs.map(::el), tipVec(sol.tip, r), r, regionOf(far, materialBelow = true))
            if (cl < -TOL) rep.bad(id, "맞은편벽", "노즈 원이 맞은편 벽으로 ${fmt(-cl)} mm 들어감, 노즈 중심 $at")
        }
        checkPath(
            rep, id, drawing, reg, leftMat, sol.program.segs.map(::el), sol.center.segs.map(::el), sol.tip, r,
            sol.nc, ncSkip = emptySet(), looseApproach = false,
        ) { it in small && warned }
    }

    private fun runArc3(rep: Report, inner: Boolean, r: Double, tip: Int?, p1: V, p2: V, p3: V, zClear: Double) {
        rep.cases++
        val inp = Arc3Input(inner = inner, noseR = r, tip = tip, x1 = 2 * p1.x, z1 = p1.z, x2 = 2 * p2.x, z2 = p2.z, x3 = 2 * p3.x, z3 = p3.z, zClear = zClear)
        val res = RoundCalc.arc3(inp)
        val id = "$inp → 경고 ${res.warnings}"
        // 세 점을 지나는 원(외심), 방향은 세 점의 돌림(오른쪽으로 돌면 시계 = G02)
        val arc = Ac(p1, p3, circum(p1, p2, p3), cw = ((p2 - p1) cross (p3 - p2)) < 0)
        val reverse = p3.z > p1.z
        val drawing = listOf<El>(arc)
        val reg = regionOf(drawing, materialBelow = !inner)
        val leftMat = leftIsMaterial(reg, drawing)
        val sign = if (leftMat) -1.0 else 1.0
        val sol = res.solution
        val tooSmall = concave(arc, sign) && arc.r < r - 1e-9
        // 내경: 노즈 중심(원호를 공구 쪽으로 r 민 것)이 축 아래로 가면 노즈가 맞은편 벽을 판다 — 멈춰야 한다. 경계 ±1e-7 은 둘 다 된다
        val off = Ac(arc.a + arc.leftNormal(0.0) * (sign * r), arc.b + arc.leftNormal(1.0) * (sign * r), arc.c, arc.cw)
        val bottom = V(off.c.z, off.c.x - off.r)
        val lowest = min(min(off.a.x, off.b.x), if (off.dist(bottom) < 1e-9) bottom.x else Double.POSITIVE_INFINITY)
        val crosses = inner && !tooSmall && lowest < -1e-7
        if (sol == null) {
            rep.stop(res.warnings.toString())
            val axis = inner && !tooSmall && lowest < 1e-7
            if (!((tooSmall || axis) && res.warnings == listOf(RoundWarning.NOSE_UNREACHABLE))) {
                rep.bad(id, "예상밖멈춤", "${res.warnings}, 오목 R<r=$tooSmall (R ${arc.r}), 노즈 중심 가장 낮은 X ${fmt(2 * lowest)}")
            }
            return
        }
        rep.solved++
        if (tooSmall) rep.bad(id, "멈춤없음", "오목 R ${arc.r} < 노즈R $r 인데 숫자를 냈다")
        if (crosses) rep.bad(id, "멈춤없음", "내경인데 노즈 중심이 축 아래(X${fmt(2 * lowest)})로 간다")
        val d = sol.drawing.segs.singleOrNull()?.let(::el) as? Ac
        if (d == null || (d.c - arc.c).len > 1e-6 || d.cw != arc.cw || (d.a - p1).len > 1e-9 || (d.b - p3).len > 1e-9) {
            rep.bad(id, "도면", "도면 원호 ${sol.drawing.segs} ≠ 세 점의 원호 중심 ${arc.c} cw=${arc.cw}")
        }
        val expTip = tip ?: physicalTip(inner, reverse)
        if (sol.tip != expTip) rep.bad(id, "팁", "팁 ${sol.tip}, 바이트가 깎는 모서리로 본 팁 $expTip")
        val expSide = if (leftMat) Side.G42 else Side.G41
        if (sol.side != expSide) rep.bad(id, "보정쪽", "${sol.side}, 재료 반대쪽은 $expSide")
        checkPath(
            rep, id, drawing, reg, leftMat, sol.program.segs.map(::el), sol.center.segs.map(::el), sol.tip, r,
            sol.nc, ncSkip = emptySet(), looseApproach = true,
        ) { false }
    }

    private fun runNoseComp(rep: Report, prof: List<El>, inner: Boolean, reverse: Boolean, r: Double, tip: Int) {
        rep.cases++
        val els = if (reverse) prof.reversed().map { it.reversed() } else prof
        val id = "NoseComp ${if (inner) "내경" else "외경"} ${if (reverse) "역" else "정"} r=$r 팁 $tip 윤곽 ${els.joinToString(" ") { desc(it) }}"
        val reg = regionOf(els, materialBelow = !inner)
        val leftMat = leftIsMaterial(reg, els)
        val sign = if (leftMat) -1.0 else 1.0
        val side = if (leftMat) Side.G42 else Side.G41
        if (Side.of(inner, reverse) != side) rep.bad(id, "보정쪽", "Side.of = ${Side.of(inner, reverse)}, 재료 반대쪽은 $side")
        val contour = Contour(els.map(::toSeg))
        val cp = try {
            NoseComp.tipPath(contour, r, side, tip)
        } catch (e: IllegalArgumentException) {
            rep.stop("IllegalArgumentException")
            // 이웃한 두 요소의 볼록 모서리에서 공구 쪽 보정선이 만나지 않으면 공구는 꼭짓점을 도는 원호로 넘어가야 한다.
            // NoseComp 는 요소마다 한 자리(볼록 모서리는 날카롭게)로만 내는 약속이라 그 원호를 넣을 자리가 없어
            // 틀린 숫자 대신 던진다(설계 문서 "일반 해법"). 이것은 지나친 멈춤으로 치지 않는다.
            if (els.none { !anyReachable(it, r, reg, sign) } && !convexGap(els, r, sign)) {
                rep.bad(id, "지나친멈춤", "이을 수 없다며 던졌는데 모든 요소에 노즈 원이 닿을 수 있다: ${e.message}")
            }
            return
        }
        rep.solved++
        if (cp.warnings.isNotEmpty()) rep.stop("경고와 함께 숫자")
        val flagged = cp.warnings.map { it.index }.toSet()
        val center = NoseComp.centerPath(contour, r, side).path.segs.map(::el)
        checkPath(rep, "$id → 경고 ${cp.warnings}", els, reg, leftMat, cp.path.segs.map(::el), center, tip, r, null, emptySet(), false) { it in flagged }
    }

    private fun runGroove(rep: Report, inp: GrooveInput, rg: Double) {
        rep.cases++
        val id = "$inp, 인서트 모서리 R $rg"
        val res = GrooveCalc.calc(inp)
        val sol = res.solution
        if (sol == null) {
            rep.stop(res.warnings.toString())
            rep.bad(id, "예상밖멈춤", "${res.warnings}")
            return
        }
        rep.solved++
        val z0 = inp.zStart!!
        val w = inp.width!!
        val top = inp.od!! / 2
        val bot = inp.bottom!! / 2
        val t = inp.insert!!
        val drawing = polyline(V(z0, top), V(z0, bot), V(z0 - w, bot), V(z0 - w, top))
        sameVertices(rep, id, sol.drawing, drawing)
        // 재료 = 외경 아래 전부에서 홈을 판 것
        val ext = 20.0
        val poly = listOf(
            V(z0 + ext, top), V(z0, top), V(z0, bot), V(z0 - w, bot), V(z0 - w, top),
            V(z0 - w - ext, top), V(z0 - w - ext, -top - 5), V(z0 + ext, -top - 5),
        )
        val reg = Region(poly.indices.map { Ln(poly[it], poly[(it + 1) % poly.size]) })
        val leftMat = leftIsMaterial(reg, drawing)
        val sign = if (leftMat) -1.0 else 1.0
        // 기준 인선 P 에서 날의 두 노즈 원 중심 = 모서리 인선점 − 팁 벡터 (왼쪽 모서리 팁 3, 오른쪽 모서리 팁 4)
        val leftTip = if (inp.ref == GrooveRef.LEFT) V(0.0, 0.0) else V(-t, 0.0)
        if (inp.ref.tip != (if (inp.ref == GrooveRef.LEFT) 3 else 4)) rep.bad(id, "팁", "GrooveRef.${inp.ref}.tip = ${inp.ref.tip}")
        val cl = leftTip - tipVec(3, rg)
        val cr = leftTip + V(t, 0.0) - tipVec(4, rg)
        val up = V(0.0, top + 20)
        // 날의 "속심": 두 중심 사이 선분 + 두 중심에서 위로 올라가는 옆면. 날 = 속심에서 rg 안쪽
        val core = listOf(cl to cr, cl to cl + up, cr to cr + up)
        val moves = try {
            parseNc(sol.nc).map { it as Ln }
        } catch (e: Exception) {
            rep.bad(id, "NC", e.message ?: "$e")
            return
        }
        var worst = Double.POSITIVE_INFINITY
        var worstAt = V(0.0, 0.0)
        for (m in moves) for ((ca, cb) in core) {
            val v = sweptDist(m.a + ca, m.a + cb, m.b - m.a, reg, poly) - rg
            if (v < worst) {
                worst = v
                worstAt = m.a
            }
        }
        rep.worstClear = min(rep.worstClear, worst)
        if (worst < -TOL) rep.bad(id, "파고듦", "날이 재료로 ${fmt(-worst)} mm 들어감 — 기준 인선 $worstAt 에서 시작하는 이동")
        for ((i, e) in drawing.withIndex()) {
            val n = max(100, ceil(e.length / 0.02).toInt())
            var reach = 0
            var maxH = Double.NEGATIVE_INFINITY
            var at: V? = null
            for (j in 0 until n) {
                val tt = (j + 0.5) / n
                if (!reachable(e, tt, rg, reg, sign)) continue
                reach++
                val q = e.at(tt)
                var best = Double.POSITIVE_INFINITY
                for (m in moves) for ((ca, cb) in core) best = sweptGap(q, m.a + ca, m.a + cb, m.b - m.a, best)
                val h = best - rg
                if (h > maxH) {
                    maxH = h
                    at = q
                }
            }
            if (reach == 0) {
                rep.bad(id, "못닿음", "홈 ${i}번 요소에 모서리 R $rg 원이 닿을 수 없다(시험 입력 문제)")
                continue
            }
            rep.worstUncut = max(rep.worstUncut, maxH)
            if (maxH > TOL) rep.bad(id, "덜깎음", "홈 ${i}번 요소 $at 에서 ${fmt(maxH)} mm 남음")
        }
    }

    // ---------------------------------------------------------------- 공통 경로 검사

    /**
     * program = 가상 인선 경로(도면 요소와 같은 번호), center = 구현이 낸 노즈 중심 경로(있으면 program − 팁 벡터 와 맞춰 본다).
     * nc 가 있으면 글을 따로 읽어 같은 검사를 한다(0.001 반올림이라 [NC_TOL]). ncSkip = NC 가 일부러 깎지 않는 요소
     * (테이퍼의 앞 요소: 진입점이 첫 꺾임점에서 zClear 바깥). looseApproach = 3점호처럼 진입 이동 옆의 재료를 모르는 경우.
     * mayBeUncut(i) = 닿을 수 없어도 되는 요소(경고가 난 것).
     */
    private fun checkPath(
        rep: Report, id: String, drawing: List<El>, reg: Region, leftMat: Boolean,
        program: List<El>, center: List<El>?, tip: Int, r: Double,
        nc: List<String>?, ncSkip: Set<Int>, looseApproach: Boolean,
        legTrim: Double = 0.0, exitMove: Boolean = false,
        mayBeUncut: (Int) -> Boolean,
    ) {
        val tv = tipVec(tip, r)
        val centers = program.map { it.shift(tv * -1.0) }
        if (center != null) {
            val off = center.indices.firstOrNull { k ->
                k >= centers.size || (center[k].a - centers[k].a).len > 1e-9 || (center[k].b - centers[k].b).len > 1e-9
            }
            if (center.size != centers.size || off != null) rep.bad(id, "중심경로", "center.segs[$off] ≠ program − 팁 $tip 벡터")
        }
        for ((k, e) in program.withIndex()) {
            if (e is Ac && abs((e.b - e.c).len - e.r) > 1e-7) rep.bad(id, "원호", "program.segs[$k] 의 끝점이 원 위에 없다")
        }
        if (program.size != drawing.size) {
            rep.bad(id, "대응", "프로그램 요소 ${program.size}개 ≠ 도면 ${drawing.size}개")
            return
        }
        val (cl, at) = clearance(program, tv, r, reg)
        rep.worstClear = min(rep.worstClear, cl)
        if (cl < -TOL) rep.bad(id, "파고듦", "프로그램 경로에서 노즈 원이 재료로 ${fmt(-cl)} mm 들어감, 노즈 중심 $at")
        val sign = if (leftMat) -1.0 else 1.0
        // 시작·끝 요소의 바깥 끝 legTrim 안쪽은 남김을 세지 않는다
        fun trimmed(i: Int, e: El): (V) -> Boolean = when {
            legTrim <= 0 -> { _ -> false }
            i == 0 -> { q -> (q - e.a).len < legTrim + 1e-6 }
            i == drawing.lastIndex -> { q -> (q - e.b).len < legTrim + 1e-6 }
            else -> { _ -> false }
        }
        for ((i, e) in drawing.withIndex()) {
            val cv = cover(e, centers, r, reg, sign, trimmed(i, e))
            if (cv.minGap < -TOL) rep.bad(id, "파고듦", "도면 ${i}번 요소의 점 ${cv.gapAt} 에 노즈 원이 ${fmt(-cv.minGap)} mm 들어감")
            // 닿는 곳이 표본 간격(0.005 mm 쯤)보다 좁은 요소는 표본으로 못 찾는다(0.0015 mm 만 닿는 경사를 "못닿음"으로 잘못 쳤다).
            // 그때는 구현 경로가 닿는 점들의 접점을 이 파일의 정의(reachable)로 다시 본다 — 하나라도 맞으면 닿는 요소다
            if (cv.reachable == 0 && (mayBeUncut(i) || !witnessed(e, centers[i], r, reg, sign))) {
                if (!mayBeUncut(i)) rep.bad(id, "못닿음", "도면 ${i}번 요소(${desc(e)})는 노즈R $r 원이 어디도 닿을 수 없는데 경고 없이 숫자를 냈다")
                continue
            }
            if (cv.reachable > 0) {
                rep.worstUncut = max(rep.worstUncut, cv.maxUncut)
                if (cv.minContact > TOL) {
                    rep.bad(id, "접촉없음", "도면 ${i}번 요소(${desc(e)})에 노즈 원이 한 번도 닿지 않는다(가장 가까울 때 ${fmt(cv.minContact)} mm)")
                } else if (cv.maxUncut > TOL) {
                    rep.bad(id, "덜깎음", "도면 ${i}번 요소 ${cv.uncutAt} 에서 ${fmt(cv.maxUncut)} mm 남음(닿을 수 있는 점)")
                }
            }
            if (!mayBeUncut(i)) {
                val g = touchGap(centers[i], e, r)
                if (g > TOL) rep.bad(id, "대응", "program.segs[$i] 의 노즈 원이 도면 ${i}번 요소에 닿지 않는다(${fmt(g)} mm)")
            }
        }
        if (nc == null) return
        val ncEls = try {
            parseNc(nc)
        } catch (e: Exception) {
            rep.bad(id, "NC", e.message ?: "$e")
            return
        }
        // NC 글이 계산 경로(program)에서 얼마나 벗어나나. 첫 이동(진입)·후퇴는 program 에 없어 뺀다
        for (e in ncEls.drop(1).dropLast(if (exitMove) 1 else 0)) {
            val n = max(20, ceil(e.length / STEP).toInt())
            val dev = (0..n).maxOf { k -> program.minOf { it.dist(e.at(k.toDouble() / n)) } }
            if (dev > NC_TOL) {
                val what = if (e is Ac) "원호 R${fmt(e.r)} ${fmt(e.sweep * 180 / PI)}°" else "직선"
                rep.bad(id, "NC오차", "NC $what 가 계산 경로에서 ${fmt(dev)} mm 벗어남  NC=$nc")
            }
        }
        val body = if (looseApproach) ncEls.drop(1) else ncEls
        val (ncl, nat) = clearance(body, tv, r, reg)
        if (ncl < -NC_TOL) rep.bad(id, "NC파고듦", "NC 글대로 가면 노즈 원이 재료로 ${fmt(-ncl)} mm 들어감, 노즈 중심 $nat  NC=$nc")
        if (looseApproach && ncEls.isNotEmpty()) {
            val ap = ncEls[0]
            val n = max(20, ceil(ap.length / STEP).toInt())
            val g = (0..n).minOf { k -> drawing.minOf { it.dist(ap.at(k.toDouble() / n) - tv) } } - r
            if (g < -NC_TOL) rep.bad(id, "NC파고듦", "진입 이동에서 노즈 원이 도면 원호로 ${fmt(-g)} mm 들어감  NC=$nc")
        }
        val ncCenters = ncEls.map { it.shift(tv * -1.0) }
        for ((i, e) in drawing.withIndex()) {
            if (i in ncSkip || mayBeUncut(i)) continue
            val cv = cover(e, ncCenters, r, reg, sign, trimmed(i, e))
            if (cv.minGap < -NC_TOL) rep.bad(id, "NC파고듦", "NC 글대로 가면 도면 ${i}번 요소 ${cv.gapAt} 에 ${fmt(-cv.minGap)} mm 들어감")
            if (cv.reachable > 0 && cv.maxUncut > NC_TOL) rep.bad(id, "NC덜깎음", "NC 글대로 가면 도면 ${i}번 요소 ${cv.uncutAt} 에서 ${fmt(cv.maxUncut)} mm 남음  NC=$nc")
        }
    }

    /** 도면 요소가 하나씩 같은가(종류·끝점, 원호는 중심·방향) */
    private fun sameDrawing(rep: Report, id: String, c: Contour, mine: List<El>) {
        val got = c.segs.map(::el)
        val same = got.size == mine.size && got.zip(mine).all { (g, e) ->
            (g.a - e.a).len <= 1e-6 && (g.b - e.b).len <= 1e-6 &&
                (g is Ln && e is Ln || g is Ac && e is Ac && (g.c - e.c).len <= 1e-6 && g.cw == e.cw)
        }
        if (!same) rep.bad(id, "도면", "도면 ${got.map { desc(it) }} ≠ 설계 표대로 ${mine.map { desc(it) }}")
    }

    private fun sameVertices(rep: Report, id: String, c: Contour, mine: List<El>) {
        val got = listOf(vOf(c.segs.first().start)) + c.segs.map { vOf(it.end) }
        val exp = listOf(mine.first().a) + mine.map { it.b }
        if (got.size != exp.size || got.zip(exp).any { (g, e) -> (g - e).len > 1e-6 }) {
            rep.bad(id, "도면", "도면 꼭짓점 $got ≠ 설계 표대로 $exp")
        }
    }
}

// -------------------------------------------------------------------- 기준값

private const val TOL = 0.0005 // 과제 기준(mm)

/** NC 글은 0.001 로 반올림 — X 반경 0.00025, Z 0.0005 가 더해질 수 있다 */
private const val NC_TOL = 0.001

/** 경로 따라가기 간격(mm). 노즈 원 r ≥ 0.1 에서 놓치는 양 ≈ (STEP/2)²/(2r) < 0.00004 */
private const val STEP = 0.005

/** 재료 영역을 닫는 큰 원 */
private const val RB = 1000.0

private val BOOLS = listOf(false, true)

private fun r3(v: Double) = (v * 1000).roundToLong() / 1000.0

private fun fmt(v: Double) = ((v * 10000).roundToLong() / 10000.0).toString()

private fun rises(inner: Boolean, type: Int) = (!inner && type != 5) || (inner && type == 5)

private fun noseR(rnd: Random) =
    if (rnd.nextInt(3) == 0) r3(rnd.nextDouble(0.1, 2.0)) else TaperCalc.NOSE_R_CHOICES.random(rnd)

private fun randomTip(rnd: Random): Int? = if (rnd.nextInt(5) == 0) rnd.nextInt(0, 10) else null

/** 모서리 R: 반은 0(날카로움), 반은 0.05~3 */
private fun cornerR(rnd: Random) = if (rnd.nextBoolean()) 0.0 else r3(rnd.nextDouble(0.05, 3.0))

/**
 * 꺾은선(가공 순서)의 안쪽 꼭짓점 k(1 ~ n−2)마다 모서리 R cr[k−1] 을 끼운 요소들. 계산기의 Corners 와 따로 짰다.
 * 접선 길이 = R·tan(꺾인 각/2), 원 중심은 꺾이는 쪽으로 첫 접점에서 R. 어느 직선에서 양 끝 접선 길이의 합이
 * 그 직선 길이 이상이면(R 이 안 들어가면) null. 일직선으로 이어지는 꼭짓점의 R 은 무시한다.
 */
private fun filletPolyline(p: List<V>, cr: List<Double>): List<El>? {
    val n = p.size
    val t = DoubleArray(n)
    for (k in 1 until n - 1) {
        val d1 = (p[k] - p[k - 1]).unit()
        val d2 = (p[k + 1] - p[k]).unit()
        if (cr[k - 1] > 0) t[k] = cr[k - 1] * tan(atan2(abs(d1 cross d2), d1 dot d2) / 2)
    }
    for (k in 0 until n - 1) if (t[k] + t[k + 1] >= (p[k + 1] - p[k]).len - 1e-9) return null
    val out = mutableListOf<El>()
    var cur = p[0]
    for (k in 1 until n - 1) {
        if (t[k] == 0.0) {
            out += Ln(cur, p[k])
            cur = p[k]
            continue
        }
        val d1 = (p[k] - p[k - 1]).unit()
        val d2 = (p[k + 1] - p[k]).unit()
        val left = (d1 cross d2) > 0
        val a = p[k] - d1 * t[k]
        val b = p[k] + d2 * t[k]
        out += Ln(cur, a)
        out += Ac(a, b, a + d1.ccw() * (if (left) cr[k - 1] else -cr[k - 1]), cw = !left)
        cur = b
    }
    out += Ln(cur, p[n - 1])
    return out
}

/** NC 줄마다 그 뒤의 위치(주석은 건너뜀). X 는 직경 → 반경 */
private fun ncPoints(nc: List<String>): List<V> {
    var z = 0.0
    var x = 0.0
    return nc.filter { !it.startsWith("(") }.map { line ->
        for (w in line.split(" ")) when (w[0]) {
            'X' -> x = w.substring(1).toDouble() / 2
            'Z' -> z = w.substring(1).toDouble()
        }
        V(z, x)
    }
}

/** 설계 문서의 팁 표(Haas 워크북 69~70쪽): 노즈 중심 → 가상 인선 (Δz, Δx 반경) */
private fun tipVec(tip: Int, r: Double): V = when (tip) {
    1 -> V(r, r)
    2 -> V(-r, r)
    3 -> V(-r, -r)
    4 -> V(r, -r)
    5 -> V(r, 0.0)
    6 -> V(0.0, r)
    7 -> V(-r, 0.0)
    8 -> V(0.0, -r)
    else -> V(0.0, 0.0)
}

/** 바이트가 깎는 모서리: 정방향은 척 쪽(Z−)·역방향은 Z+, 외경은 재료가 아래(X−)·내경은 위(X+) */
private fun physicalTip(inner: Boolean, reverse: Boolean): Int =
    (1..4).first { tipVec(it, 1.0) == V(if (reverse) 1.0 else -1.0, if (inner) 1.0 else -1.0) }

// -------------------------------------------------------------------- 기하(구현과 따로)

/** 점·벡터. x 는 반경 */
internal data class V(val z: Double, val x: Double) {
    operator fun plus(o: V) = V(z + o.z, x + o.x)
    operator fun minus(o: V) = V(z - o.z, x - o.x)
    operator fun times(k: Double) = V(z * k, x * k)
    infix fun dot(o: V) = z * o.z + x * o.x
    infix fun cross(o: V) = z * o.x - x * o.z
    val len: Double get() = sqrt(z * z + x * x)
    fun unit() = this * (1 / len)

    /** 반시계 90° */
    fun ccw() = V(-x, z)
    override fun toString() = "(Z${fmt(z)} X${fmt(2 * x)})"
}

private fun vOf(p: Pt) = V(p.z, p.x)

private fun wrap(a: Double): Double {
    val m = a % (2 * PI)
    return if (m < 0) m + 2 * PI else m
}

private fun angOf(v: V) = atan2(v.x, v.z)

private fun polar(c: V, r: Double, a: Double) = V(c.z + r * cos(a), c.x + r * sin(a))

internal sealed interface El {
    val a: V
    val b: V
    val length: Double
    fun at(t: Double): V
    fun dist(p: V): Double

    /** 진행 방향 단위 접선 */
    fun tangent(t: Double): V
    fun leftNormal(t: Double): V = tangent(t).ccw()

    /** p 에서 가장 가까운 점의 매개변수 t ∈ [0, 1] */
    fun foot(p: V): Double

    /** p 에서 보아 a → b 로 갈 때 돈 각(감은 수 계산용) */
    fun wind(p: V): Double
    fun shift(d: V): El
    fun reversed(): El
}

internal class Ln(override val a: V, override val b: V) : El {
    private val d = b - a
    override val length = d.len
    override fun at(t: Double) = if (t >= 1) b else a + d * t
    override fun dist(p: V): Double {
        val l2 = d dot d
        if (l2 == 0.0) return (p - a).len
        val t = (((p - a) dot d) / l2).coerceIn(0.0, 1.0)
        return (p - (a + d * t)).len
    }
    override fun tangent(t: Double) = d.unit()
    override fun foot(p: V): Double {
        val l2 = d dot d
        return if (l2 == 0.0) 0.0 else (((p - a) dot d) / l2).coerceIn(0.0, 1.0)
    }
    override fun wind(p: V): Double {
        val u = a - p
        val w = b - p
        return atan2(u cross w, u dot w)
    }
    override fun shift(d: V): El = Ln(a + d, b + d)
    override fun reversed(): El = Ln(b, a)
}

/** cw = 이 그림(Z 오른쪽, X 위)에서 시계 */
internal class Ac(override val a: V, override val b: V, val c: V, val cw: Boolean) : El {
    val r = (a - c).len
    private val s = if (cw) -1.0 else 1.0
    private val a0 = angOf(a - c)
    val sweep = if ((b - a).len < 1e-12) 0.0 else wrap(s * (angOf(b - c) - a0))
    override val length = r * sweep
    private fun angAt(t: Double) = a0 + s * sweep * t
    override fun at(t: Double) = when {
        t <= 0 -> a
        t >= 1 -> b
        else -> polar(c, r, angAt(t))
    }
    private fun covers(p: V) = wrap(s * (angOf(p - c) - a0)) <= sweep + 1e-12
    override fun dist(p: V): Double {
        val q = (p - c).len
        if (q < 1e-12) return r
        return if (covers(p)) abs(q - r) else min((p - a).len, (p - b).len)
    }
    override fun tangent(t: Double) = V(cos(angAt(t)), sin(angAt(t))).ccw() * s
    override fun foot(p: V): Double {
        if (sweep == 0.0) return 0.0
        val u = wrap(s * (angOf(p - c) - a0))
        return if (u <= sweep) u / sweep else if ((p - a).len <= (p - b).len) 0.0 else 1.0
    }
    override fun wind(p: V): Double {
        val u = a - p
        val w = b - p
        var t = atan2(u cross w, u dot w)
        // 현과 원호 사이(활꼴) 안의 점이면 원호는 현과 한 바퀴 다르게 돈다
        if (sweep > 0 && (p - c).len < r) {
            val ch = b - a
            val m = polar(c, r, angAt(0.5))
            if ((ch cross (p - a)) * (ch cross (m - a)) > 0) t += 2 * PI * s
        }
        return t
    }
    override fun shift(d: V): El = Ac(a + d, b + d, c + d, cw)
    override fun reversed(): El = Ac(b, a, c, !cw)
}

private fun el(s: Seg): El = when (s) {
    is Line -> Ln(vOf(s.start), vOf(s.end))
    is Arc -> Ac(vOf(s.start), vOf(s.end), vOf(s.center), s.cw)
}

private fun toSeg(e: El): Seg = when (e) {
    is Ln -> Line(Pt(e.a.z, e.a.x), Pt(e.b.z, e.b.x))
    is Ac -> Arc(Pt(e.a.z, e.a.x), Pt(e.b.z, e.b.x), Pt(e.c.z, e.c.x), e.cw)
}

private fun polyline(vararg p: V): List<El> = p.toList().zipWithNext { a, b -> Ln(a, b) }

private fun desc(e: El) = when (e) {
    is Ln -> "직선${e.a}→${e.b}"
    is Ac -> "원호${e.a}→${e.b} 중심${e.c} R${fmt(e.r)}${if (e.cw) " G02" else " G03"}"
}

private fun circum(p1: V, p2: V, p3: V): V {
    val (ax, ay) = p1.z to p1.x
    val (bx, by) = p2.z to p2.x
    val (cx, cy) = p3.z to p3.x
    val d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
    val a2 = ax * ax + ay * ay
    val b2 = bx * bx + by * by
    val c2 = cx * cx + cy * cy
    return V((a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d, (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d)
}

/** 닫힌 고리로 둘러싼 재료 */
internal class Region(val loop: List<El>) {
    fun inside(p: V) = abs(loop.sumOf { it.wind(p) }) > PI
    fun dist(p: V) = loop.minOf { it.dist(p) }
    fun sd(p: V): Double {
        val d = dist(p)
        return if (inside(p)) -d else d
    }
}

/**
 * 열린 도면 윤곽으로 재료 영역을 만든다: 양 끝을 접선 방향으로 큰 원까지 늘이고 큰 원호로 닫는다.
 * 닫는 방향은 외경이면 아주 아래 점, 내경이면 아주 위 점이 안에 들도록 고른다(Z 가 한쪽으로만 가는 선반 윤곽).
 */
internal fun regionOf(drawing: List<El>, materialBelow: Boolean): Region {
    val pts = drawing.flatMap { listOf(it.a, it.b) }
    val o = V(pts.sumOf { it.z } / pts.size, pts.sumOf { it.x } / pts.size)
    fun far(p: V, u: V): V {
        val d = p - o
        val k = d dot u
        return p + u * (-k + sqrt(k * k - (d dot d) + RB * RB))
    }
    val s0 = drawing.first().a
    val e0 = drawing.last().b
    val sFar = far(s0, drawing.first().tangent(0.0) * -1.0)
    val eFar = far(e0, drawing.last().tangent(1.0))
    val known = o + V(0.0, if (materialBelow) -RB / 2 else RB / 2)
    val other = o + V(0.0, if (materialBelow) RB / 2 else -RB / 2)
    for (cw in BOOLS) {
        val reg = Region(listOf<El>(Ln(sFar, s0)) + drawing + Ln(e0, eFar) + Ac(eFar, sFar, o, cw))
        if (reg.inside(known) && !reg.inside(other)) return reg
    }
    error("재료 영역을 닫지 못했다: ${drawing.map { desc(it) }}")
}

/** 진행 방향 왼쪽이 재료인가(가장 긴 요소 가운데에서 본다) */
private fun leftIsMaterial(reg: Region, drawing: List<El>): Boolean {
    val e = drawing.maxByOrNull { it.length }!!
    val m = e.at(0.5)
    val n = e.leftNormal(0.5)
    val l = reg.inside(m + n * 1e-4)
    check(l != reg.inside(m - n * 1e-4)) { "재료 쪽을 못 정했다" }
    return l
}

/** 오목 원호 = 공구가 원 안쪽 */
private fun concave(e: El, toolSign: Double): Boolean =
    e is Ac && ((e.leftNormal(0.5) * toolSign) dot (e.c - e.at(0.5))) > 0

/** 도면 점 e(t) 에서 공구 쪽으로 접한 반지름 r 원이 재료와 겹치지 않는가 */
private fun reachable(e: El, t: Double, r: Double, reg: Region, toolSign: Double): Boolean {
    val c = e.at(t) + e.leftNormal(t) * (toolSign * r)
    return reg.dist(c) >= r - 1e-7 && !reg.inside(c)
}

/**
 * 요소 위에서 볼 매개변수: 칸 가운데들과 두 끝점. 끝점도 본다 — 볼록 모서리 옆 0.0002 mm 만 닿는 요소를
 * 칸 가운데만 보다가 "못닿음"으로 잘못 친 적이 있다(NoseComp 무작위, 내경 역 r=1.493 의 4번 단면).
 */
private fun samplesOf(e: El): List<Double> {
    val n = min(20_000, max(200, ceil(e.length / 0.01).toInt()))
    return listOf(0.0) + (0 until n).map { (it + 0.5) / n } + 1.0
}

private fun anyReachable(e: El, r: Double, reg: Region, toolSign: Double): Boolean =
    samplesOf(e).any { reachable(e, it, r, reg, toolSign) }

/**
 * 이웃한 두 요소가 볼록하게(공구 반대쪽으로) 꺾이는데 공구 쪽으로 r 민 두 곡선(직선·원을 끝없이 늘인 것)이 만나지 않는 곳이
 * 있나. 그런 모서리는 꼭짓점을 도는 원호 없이는 이을 수 없다.
 */
private fun convexGap(els: List<El>, r: Double, toolSign: Double): Boolean = els.zipWithNext().any { (p, q) ->
    val turn = p.tangent(1.0) cross q.tangent(0.0)
    turn * toolSign < -1e-12 && !offsetsMeet(p, q, r, toolSign)
}

private fun offsetsMeet(p: El, q: El, r: Double, toolSign: Double): Boolean {
    fun shifted(e: El) = e.a + e.leftNormal(0.0) * (toolSign * r)
    fun radius(e: Ac) = (shifted(e) - e.c).len
    return when {
        p is Ln && q is Ln -> abs(p.tangent(0.0) cross q.tangent(0.0)) > 1e-12
        p is Ac && q is Ac -> {
            val d = (p.c - q.c).len
            d <= radius(p) + radius(q) + 1e-9 && d >= abs(radius(p) - radius(q)) - 1e-9
        }
        else -> {
            val l = (if (p is Ln) p else q) as Ln
            val a = (if (p is Ac) p else q) as Ac
            abs((a.c - shifted(l)) cross l.tangent(0.0)) <= radius(a) + 1e-9
        }
    }
}

/** 경로를 따라가며 노즈 원(중심 = 경로점 − tv)의 재료 여유가 가장 작은 곳 */
private fun clearance(path: List<El>, tv: V, r: Double, reg: Region): Pair<Double, V> {
    var worst = Double.POSITIVE_INFINITY
    var at = V(0.0, 0.0)
    for (e in path) {
        val n = min(50_000, max(1, ceil(e.length / STEP).toInt()))
        for (k in 0..n) {
            val c = e.at(k.toDouble() / n) - tv
            val d = reg.dist(c)
            // 재료 안쪽인지는 경계 가까이에서 늘, 멀리서는 가끔 본다(경계를 넘지 않고 안으로 들어갈 수는 없다)
            val inside = (d < r + 0.05 || k % 25 == 0) && reg.inside(c)
            val v = (if (inside) -d else d) - r
            if (v < worst) {
                worst = v
                at = c
            }
        }
    }
    return worst to at
}

private class Cover {
    var reachable = 0
    var maxUncut = Double.NEGATIVE_INFINITY
    var uncutAt: V? = null
    var minContact = Double.POSITIVE_INFINITY
    var minGap = Double.POSITIVE_INFINITY
    var gapAt: V? = null
}

/** 도면 요소의 점마다: 노즈 중심 경로까지 거리 − r (음수 = 파고듦, 양수 = 남김). 닿을 수 있는 점 가운데 skip 이 아닌 점만 남김을 센다 */
private fun cover(e: El, centers: List<El>, r: Double, reg: Region, toolSign: Double, skip: (V) -> Boolean = { false }): Cover {
    val cv = Cover()
    for (t in samplesOf(e)) {
        val q = e.at(t)
        val h = centers.minOf { it.dist(q) } - r
        if (h < cv.minGap) {
            cv.minGap = h
            cv.gapAt = q
        }
        if (!reachable(e, t, r, reg, toolSign)) continue
        cv.reachable++
        cv.minContact = min(cv.minContact, abs(h))
        if (skip(q)) continue
        if (h > cv.maxUncut) {
            cv.maxUncut = h
            cv.uncutAt = q
        }
    }
    return cv
}

/** 중심 경로 요소 c 위 점들에서 도면 요소 e 로 내린 발(접점) 가운데 반지름 r 원이 재료와 겹치지 않는 것이 있나 */
private fun witnessed(e: El, c: El, r: Double, reg: Region, toolSign: Double): Boolean {
    val n = max(20, ceil(c.length / STEP).toInt())
    return (0..n).any { k ->
        val q = c.at(k.toDouble() / n)
        abs(e.dist(q) - r) <= 1e-6 && reachable(e, e.foot(q), r, reg, toolSign)
    }
}

/** 중심 경로 요소 c 를 따라갈 때 노즈 원과 도면 요소 e 사이가 가장 0 에 가까운 값 */
private fun touchGap(c: El, e: El, r: Double): Double {
    val n = max(20, ceil(c.length / STEP).toInt())
    return (0..n).minOf { abs(e.dist(c.at(it.toDouble() / n)) - r) }
}

private fun segSeg(p: Ln, q: Ln): Double {
    val u = p.b - p.a
    val w = q.b - q.a
    val d1 = w cross (p.a - q.a)
    val d2 = w cross (p.b - q.a)
    val d3 = u cross (q.a - p.a)
    val d4 = u cross (q.b - p.a)
    if (d1 * d2 < 0 && d3 * d4 < 0) return 0.0
    return minOf(p.dist(q.a), p.dist(q.b), q.dist(p.a), q.dist(p.b))
}

/** 선분 ab 를 tr 만큼 밀어 쓴 평행사변형과 재료 경계 사이 거리. 평행사변형 꼭짓점이 재료 안이면 음수 */
private fun sweptDist(a: V, b: V, tr: V, reg: Region, poly: List<V>): Double {
    val cs = listOf(a, b, b + tr, a + tr)
    val edges = (0..3).map { Ln(cs[it], cs[(it + 1) % 4]) }
    var d = edges.minOf { e -> reg.loop.minOf { segSeg(e, it as Ln) } }
    if (poly.any { p -> abs(edges.sumOf { it.wind(p) }) > PI }) d = 0.0
    return min(d, cs.minOf { reg.sd(it) })
}

/** 점 q 와 선분 ab 를 tr 만큼 밀어 쓴 평행사변형 사이 거리(안이면 0). best 보다 멀면 best */
private fun sweptGap(q: V, a: V, b: V, tr: V, best: Double): Double {
    val cs = arrayOf(a, b, b + tr, a + tr)
    val gz = max(max(cs.minOf { it.z } - q.z, q.z - cs.maxOf { it.z }), 0.0)
    val gx = max(max(cs.minOf { it.x } - q.x, q.x - cs.maxOf { it.x }), 0.0)
    if (hypot(gz, gx) >= best) return best
    val edges = (0..3).map { Ln(cs[it], cs[(it + 1) % 4]) }
    if (abs(edges.sumOf { it.wind(q) }) > PI) return 0.0
    return min(best, edges.minOf { it.dist(q) })
}

// -------------------------------------------------------------------- NC 글 읽기(NcLines 와 따로)

private val NC_LINE = Regex("""(G0[0-3])((?: [XZR]-?\d+\.\d*)+)""")

/** G00/G01 직선, G02/G03 끝점 R 원호. 빠진 축은 앞 줄 값(모달). X 는 직경 → 반경 */
internal fun parseNc(lines: List<String>): List<El> {
    var z: Double? = null
    var x: Double? = null
    val out = mutableListOf<El>()
    for (line in lines.filter { !it.startsWith("(") }) {
        val m = NC_LINE.matchEntire(line) ?: error("NC 줄 모양이 다르다: '$line'")
        val g = m.groupValues[1]
        val w = m.groupValues[2].trim().split(" ").associate { it[0] to it.substring(1).toDouble() }
        val nz = w['Z'] ?: z ?: error("첫 줄에 Z 가 없다: '$line'")
        val nx = w['X']?.let { it / 2 } ?: x ?: error("첫 줄에 X 가 없다: '$line'")
        val pz = z
        val px = x
        if (pz != null && px != null) {
            val p0 = V(pz, px)
            val p1 = V(nz, nx)
            out += when (g) {
                "G00", "G01" -> Ln(p0, p1)
                else -> arcR(p0, p1, w['R'] ?: error("원호에 R 이 없다: '$line'"), cw = g == "G02")
            }
        } else {
            require(g == "G00") { "첫 줄은 G00 이어야: '$line'" }
        }
        z = nz
        x = nx
    }
    return out
}

/** R 지령(180° 이하) 원호. G02 = 이 그림에서 시계 → 중심은 현 진행 방향 오른쪽 */
internal fun arcR(a: V, b: V, rr: Double, cw: Boolean): Ac {
    val ch = b - a
    val h = ch.len / 2
    val d = sqrt(max(rr * rr - h * h, 0.0))
    return Ac(a, b, (a + b) * 0.5 + ch.unit().ccw() * (if (cw) -d else d), cw)
}

// -------------------------------------------------------------------- 무작위 선반 윤곽(외경 정방향 모양)

/** 오른쪽 → 왼쪽, Z 는 늘 줄거나 그대로(단면). 처음·끝은 수평. 짧은 요소·볼록/오목 원호를 섞는다 */
private fun randomProfile(rnd: Random): List<El> {
    while (true) {
        val els = mutableListOf<El>()
        var p = V(0.0, r3(rnd.nextDouble(5.0, 30.0)))
        fun lineTo(q: V) {
            els += Ln(p, q)
            p = q
        }
        lineTo(V(p.z - r3(rnd.nextDouble(2.0, 5.0)), p.x))
        var lastVertical = false
        repeat(rnd.nextInt(1, 7)) {
            val short = rnd.nextInt(4) == 0
            fun len() = r3(if (short) rnd.nextDouble(0.05, 0.6) else rnd.nextDouble(0.3, 6.0))
            val kind = if (lastVertical) rnd.nextInt(0, 2) * 2 else rnd.nextInt(3)
            val sgn = if (rnd.nextBoolean()) 1.0 else -1.0
            var q = when (kind) {
                0 -> V(p.z - len(), p.x)
                1 -> V(p.z, p.x + sgn * len())
                else -> {
                    val dz = len()
                    V(p.z - dz, p.x + sgn * dz * tan(rnd.nextDouble(5.0, 85.0) * PI / 180))
                }
            }
            if (q.x < 1.0 || q.x > 60.0) q = V(q.z, 2 * p.x - q.x)
            if (q.x < 1.0 || q.x > 60.0) return@repeat
            val ch = q - p
            if (kind != 1 && abs(ch.x) <= abs(ch.z) && rnd.nextInt(10) < 4) {
                els += bulge(p, q, (if (rnd.nextBoolean()) 1 else -1) * rnd.nextDouble(0.05, 0.2))
                p = q
            } else {
                lineTo(q)
            }
            lastVertical = kind == 1
        }
        lineTo(V(p.z - r3(rnd.nextDouble(2.0, 5.0)), p.x))
        if (els.all { e -> (0..20).all { e.at(it / 20.0).x >= 0.5 } }) return els
    }
}

/** p → q 현에서 왼쪽으로 ratio × 현 길이만큼 부푼 원호(음수면 오른쪽) */
private fun bulge(p: V, q: V, ratio: Double): Ac {
    val ch = q - p
    val l = ch.len
    val sag = ratio * l
    val bigR = (l * l / 4 + sag * sag) / (2 * abs(sag))
    val n = ch.unit().ccw()
    val top = (p + q) * 0.5 + n * sag
    return Ac(p, q, top - n * (if (sag > 0) bigR else -bigR), cw = sag > 0)
}

// -------------------------------------------------------------------- 결과 모으기

internal class Report(val name: String) {
    private val fails = linkedMapOf<String, MutableList<String>>()
    private val failedCases = mutableSetOf<String>()
    var cases = 0
    var solved = 0
    private val stops = linkedMapOf<String, Int>()
    var worstClear = Double.POSITIVE_INFINITY
    var worstUncut = 0.0

    fun bad(id: String, tag: String, msg: String) {
        fails.getOrPut(tag) { mutableListOf() } += "[$tag] $msg\n      입력: $id"
        failedCases += id
    }

    fun stop(w: String) {
        stops[w] = (stops[w] ?: 0) + 1
    }

    fun finish() {
        val head = "$name: 사례 $cases, 계산됨 $solved, 멈춤·경고 $stops, 최소 여유 ${fmt(worstClear)} mm, 최대 남김 ${fmt(worstUncut)} mm"
        println(head)
        if (fails.isEmpty()) return
        val count = fails.mapValues { it.value.size }
        println("실패 ${count.values.sum()}건(사례 ${failedCases.size}개) $count")
        for (list in fails.values) list.forEach { println(it) }
        val text = "$head\n실패 ${count.values.sum()}건(사례 ${failedCases.size}개) $count\n" +
            fails.values.joinToString("\n") { it.take(4).joinToString("\n") }
        kotlin.test.fail(text)
    }
}
