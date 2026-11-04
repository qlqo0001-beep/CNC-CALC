package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderCut
import com.bmt.nosecalc.calc.HolderPlan
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Legacy
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RetractKind
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.SafeEnd
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.StopWarning
import com.bmt.nosecalc.calc.legacy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * 10-28 독립 검증 — 직선 거리 안전 여유 · 깎는 면 예외 · 라운드 기계 보정 G00. 구현(Clearance · Holder · HolderStop 의 식, readNc)과
 * 앞 검증 파일(EuclidRef · StopChecker · SafeStopVerifyTest)의 모형을 쓰지 않고 이 파일에서 따로 짠다. 무작위 TYPE 입력 만들기만
 * GeometricVerifyTest.randomRound 를 빌린다(입력일 뿐).
 *
 * 표본 모형:
 * - 공구: 꼭짓점에서 이송 방향(−Z)과 κr 인 주절삭날, 그 날에서 ε 돈 끝날로 마름모(T 는 삼각형) 인서트를 세우고 두 날에 접하는 노즈 원(둥근 끝)까지
 *   넣는다. 바 = 띠 [r − f − D/2, r − f + D/2] × [앞, 공구대 면], 앞은 주절삭날 연장선(뒤보링은 인서트 끝에서 수직). 최소 가공경 = 축 둘레 지름 DMIN,
 *   인선 z 에서 인서트 끝까지. 공구대 = 인선 z + 돌출 길이 − 입구 면 z.
 * - 소재: 도면 + 반직선 · 입구 면(설계 문서 '소재' 정의)과 축 너머 거울을 0.01 mm 마다 찍은 점. 점 ↔ 공구 조각의 정확한 거리(볼록 다각형 · 노즈 원호)의
 *   최솟값 = 직선 거리(표본 간격 탓 과대 ≤ 0.005²/(2d)). 공구 꼭짓점이 소재 안이면(따로 짠 ρ(z)) 겹침.
 * - NC: 이 파일의 글 읽기(모달 · R 원호 중심 다시 구함). 직접 보정은 인선 − 팁 벡터. 기계 보정(회사 꼴 — 보정 혼자 줄 / `G0X…Z20.` / 진입 / 형상 / 빼는 줄 /
 *   `G40` 혼자 줄 / `G0Z200.`)은 Z20. 자리 노즈 = 첫 블록 처음의 법선(시작 형식 A), 보정 블록은 도면을 r 민 보정선을 교점으로 잇고, 마지막 빼는 줄 끝은 법선,
 *   취소는 형식 C(`G40` 줄에서 그 자리 → 끝점 − 팁 벡터) · 형식 A · B(`G0Z200.` 동안 비스듬히) 둘 다.
 * - 깎는 면 예외: 넓은 정의(설계 문서 — 블록 요소 k 와 볼록 이음 전까지 앞 요소들의 주절삭날 쪽 직선) · 좁은 정의(사용자 문구 — 지금 노즈가 닿은
 *   주절삭날 쪽 직선과 그 연장)를 따로 짜서, 예외로 넘어간 자세를 둘로 나눠 센다.
 *
 * | ① | 잘린 NC 두 가지를 0.01 mm 마다(멀리 떨어진 곳은 거리의 1-립시츠 성질로 건너뜀 — 건너뛴 길이 < 남은 여유라 놓치지 않음): (예외 밖) ≥ 안전 여유 − 2e-3,
 *       어디서든 겹침 ≤ 1e-3(쓴 글 반올림), 후퇴 겹침 없음 · 보고한 후퇴 여유 이하로 안 내려감 |
 * | ② | 예외로 넘어간 자세: 그 면이 지금 노즈가 닿은 면인가, 앞 요소인가(사이에 볼록 원호가 있나), 공구의 어느 부분인가(바 앞면 · 그 밖) |
 * | ③ | 끝점 + 0.01 mm 에서 표본 기준 아래인 비율 ≥ 80 % |
 * | ④ | 라운드 기계 보정 진입(회사 꼴): 진입 노즈가 재료 밖(늘인 윤곽에서 r — 내경은 맞은편도), 진입 꺾임 바깥 90° 이하 · 노즈가 거꾸로 가지 않음, 첫 깎는 줄 노즈 이동 ≥ 0.05, 형상 시작 노즈 = 직접 보정, 글이 팁과 상관없음, 형상 줄 = 노즈R 0 풀이 |
 */
class EuclidMarginVerifyTest {
    private val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
    private val a16 = BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, dMin = 20.0)

    @Test
    fun handCases() {
        val t0 = TimeSource.Monotonic.markNow()
        val log = EmLog("손 계산 예")
        val r = 0.4
        val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0)
        val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)
        val c38 = RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 38.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)
        val socket = RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, legLen = 5.0)
        val back = Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = -20.0, x2 = 24.0, z2 = -19.0, x3 = 20.0, z3 = -15.0, zClear = 1.0)
        val mouth = Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0, zClear = 1.0)
        fun type(name: String, i: RoundInput, bar: BoringBar, safes: List<Double> = listOf(0.0, 0.2, 0.5, 1.0)) {
            val inp = i.copy(inner = true, noseR = r, zClear = 1.0)
            for (safe in safes) emRun(log, "$name · 안전 여유 $safe", RoundCalc.calc(inp), RoundCalc.machine(inp), true, bar, r, safe, verbose = true)
        }
        fun arc(name: String, a: Arc3Input, bar: BoringBar, safes: List<Double> = listOf(0.0, 0.2, 0.5, 1.0)) {
            for (safe in safes) emRun(log, "$name · 안전 여유 $safe", RoundCalc.arc3(a), RoundCalc.machine(a), false, bar, a.noseR, safe, verbose = true)
        }
        type("A 돔 바닥 · 바 A", dome, barA)
        type("B Ø18 · 바 A", dome.copy(d1 = 18.0), barA)
        type("C 막힌 단차 · 바 A", step, barA)
        type("C 관통 · 바 A", step.copy(through = true), barA)
        type("C38 · 바 A", c38, barA)
        type("C38 · 바 A κr 90", c38, barA.copy(kappaDeg = 90.0))
        type("D 돌출 12", dome, barA.copy(overhang = 12.0))
        type("E DMIN 20", dome, barA.copy(dMin = 20.0))
        type("F 소켓 · Ø8", socket, BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0))
        arc("뒤보링 DMIN 22 · f 12", back, BoringBar(12.0, 12.0, InsertShape.C, 9.67, 95.0, dMin = 22.0))
        arc("뒤보링 f 11", back, BoringBar(12.0, 11.0, InsertShape.C, 9.67, 95.0))
        arc("뒤보링 f 11.5", back, BoringBar(12.0, 11.5, InsertShape.C, 9.67, 95.0))
        arc("입구 R · 돌출 12 · DMIN 20", mouth, BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, overhang = 12.0, dMin = 20.0))
        // 화면 예(스크린샷 · 골든과 맞춰 볼 값): 돔 + A16 프리셋, 관통 단차 + A16, 입구 R + A16, 돔 팁 0 · 4(띄운 G00)
        type("화면 b3 돔 + A16", dome, a16, listOf(0.5))
        type("화면 b5 관통 + A16", step.copy(through = true), a16, listOf(0.5))
        arc("화면 입구 R + A16", mouth, a16, listOf(0.5))
        type("돔 팁 0 · 바 A", dome.copy(tip = 0), barA, listOf(0.0, 0.5))
        type("돔 팁 4 · 바 A", dome.copy(tip = 4), barA, listOf(0.0, 0.5))
        type("C 팁 0 · 바 A", step.copy(tip = 0), barA, listOf(0.0, 0.5))
        log.finish(t0)
        assertEquals(0, log.badCount, "손 계산 예 문제 ${log.badCount}건 — 출력 참고")
    }

    @Test
    fun randomTypes() {
        val t0 = TimeSource.Monotonic.markNow()
        val log = EmLog("무작위 내경 TYPE 2~6")
        val rnd = Random(28102026)
        val g = GeometricVerifyTest()
        var tries = 0
        while (log.followed < 110 && tries < 3000) {
            tries++
            val r = RS[rnd.nextInt(RS.size)]
            val inp = g.randomRound(rnd.nextInt(2, 7), rnd, inner = true).input.copy(noseR = r)
            val calc = RoundCalc.calc(inp)
            val sol = calc.solution ?: run { log.count("건너뜀: 계산 멈춤 ${calc.warnings}"); null } ?: continue
            val bar = emBar(rnd, r, sol.drawing.segs.first().start.x)
            val safe2 = if (rnd.nextBoolean()) 0.5 else r3(rnd.nextDouble(0.0, 1.5))
            emRun(log, "TYPE ${inp.type}${if (inp.through) " 관통" else ""} $inp", calc, RoundCalc.machine(inp), true, bar, r, safe2)
        }
        log.finish(t0)
        assertEquals(0, log.badCount, "무작위 TYPE 문제 ${log.badCount}건 — 출력 참고")
    }

    @Test
    fun randomArcs() {
        val t0 = TimeSource.Monotonic.markNow()
        val log = EmLog("무작위 3점호 정방향 내경")
        arcs(log, Random(10281), reverse = false)
        log.finish(t0)
        assertEquals(0, log.badCount, "무작위 3점호 문제 ${log.badCount}건 — 출력 참고")
    }

    @Test
    fun randomBackBoring() {
        val t0 = TimeSource.Monotonic.markNow()
        val log = EmLog("무작위 뒤보링(3점호 역방향 내경)")
        arcs(log, Random(10282), reverse = true)
        log.finish(t0)
        assertEquals(0, log.badCount, "무작위 뒤보링 문제 ${log.badCount}건 — 출력 참고")
    }

    private fun arcs(log: EmLog, rnd: Random, reverse: Boolean) {
        var tries = 0
        while (log.followed < 110 && tries < 3000) {
            tries++
            val r = RS[rnd.nextInt(RS.size)]
            val a = emArc(rnd, reverse, r, inner = true).let { if (rnd.nextInt(5) == 0) it.copy(tip = rnd.nextInt(0, 10)) else it }
            val calc = RoundCalc.arc3(a)
            val sol = calc.solution ?: run { log.count("건너뜀: 계산 멈춤 ${calc.warnings}"); null } ?: continue
            val segs = sol.drawing.segs
            val rho = if (reverse) segs.last().end.x else segs.first().start.x
            val bar = if (rnd.nextInt(4) == 0) emBar(rnd, r, rho) else emArcBar(rnd, r, sol)
            val safe = if (rnd.nextBoolean()) 0.5 else r3(rnd.nextDouble(0.0, 1.5))
            emRun(log, "${if (reverse) "뒤보링" else "3점호"} $a", calc, RoundCalc.machine(a), false, bar, r, safe)
        }
    }

    /** ④ 라운드 기계 보정 G00 — 외경 · 내경 TYPE, 3점호 외경 · 내경 · 정 · 역방향, 표준 · 비표준 팁 */
    @Test
    fun machineG00() {
        val t0 = TimeSource.Monotonic.markNow()
        val log = EmLog("라운드 기계 보정 G00")
        val rnd = Random(20261028)
        val g = GeometricVerifyTest()
        val rs = listOf(0.2, 0.4, 0.8, 1.2)
        repeat(700) {
            val inner = rnd.nextBoolean()
            val type = if (inner) rnd.nextInt(2, 7) else rnd.nextInt(1, 7)
            val r = rs[rnd.nextInt(rs.size)]
            val tip = if (rnd.nextBoolean()) null else rnd.nextInt(0, 10)
            val zc = when (rnd.nextInt(4)) {
                0 -> 0.0
                1 -> r3(rnd.nextDouble(0.0, r))
                else -> r3(rnd.nextDouble(0.0, 3.0))
            }
            val inp = g.randomRound(type, rnd, inner).input.copy(noseR = r, tip = tip, zClear = zc)
            emG00(log, "${if (inner) "내경" else "외경"} TYPE $type $inp", RoundCalc.machine(inp), RoundCalc.machine(inp.copy(tip = null)), RoundCalc.calc(inp), RoundCalc.calc(inp.copy(noseR = 0.0)), r, inner)
        }
        repeat(600) {
            val inner = rnd.nextBoolean()
            val reverse = rnd.nextBoolean()
            val r = rs[rnd.nextInt(rs.size)]
            val tip = if (rnd.nextBoolean()) null else rnd.nextInt(0, 10)
            val zc = when (rnd.nextInt(4)) {
                0 -> 0.0
                1 -> r3(rnd.nextDouble(0.0, r))
                else -> r3(rnd.nextDouble(0.0, 3.0))
            }
            val a = emArc(rnd, reverse, r, inner).copy(tip = tip, zClear = zc)
            emG00(log, "3점호 ${if (inner) "내경" else "외경"}${if (reverse) " 역방향" else ""} $a", RoundCalc.machine(a), RoundCalc.machine(a.copy(tip = null)), RoundCalc.arc3(a), RoundCalc.arc3(a.copy(noseR = 0.0)), r, inner, reverse)
        }
        // 손 계산(지은 도형): 외경 TYPE 3 Ø20 · SR12.5 · CR1, 0.4R · Z 여유 1. 돔 꼭대기(Z0, X0)의 처음 접선이 +X 라 진입점 = 첫 점 − 접선 · c, c = max(Z 여유 1, r 0.4) = 1
        // → 반경 −1(직경 X−2.) · Z0. Z 줄(X−2. 에서 Z20. → Z0.)의 노즈는 반경 −1 + 0.4 = −0.6(G42 — −Z 로 갈 때 공구 쪽 +X), 부품 앞(Z ≥ 0)이라 도면에 닿지 않아
        // 곧장 내려온다. 첫 깎는 줄 `G1X0.F.1` 이 축 너머 같은 면을 타고 첫 점으로(노즈 이동 1 ≥ 0.05). 보정 줄이 이동 없는 혼자 줄이라 팁 4 · 0 · 3 모두 같은 글
        val o3 = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4, zClear = 1.0)
        val want = listOf("G42", "G0X-2.Z20.", "Z0.", "G1X0.F.1")
        for (tip in listOf(4, 0, 3)) {
            val m = RoundCalc.machine(o3.copy(tip = tip)).solution!!
            log.ratio("손 계산 외경 TYPE 3 진입", m.nc.take(4) == want)
            if (m.nc.take(4) != want) log.bad("④ 손 계산", "외경 TYPE 3 팁 $tip: ${m.nc.take(4)} ≠ $want")
            emG00(log, "손 계산 외경 TYPE 3 팁 $tip", RoundCalc.machine(o3.copy(tip = tip)), RoundCalc.machine(o3), RoundCalc.calc(o3.copy(tip = tip)), RoundCalc.calc(o3.copy(tip = tip, noseR = 0.0)), 0.4, false)
        }
        log.finish(t0)
        assertEquals(0, log.badCount, "기계 보정 G00 문제 ${log.badCount}건 — 출력 참고")
    }

    private companion object {
        val RS = listOf(0.0, 0.2, 0.4, 0.8)
    }
}

// ====================================================================== 상수

private const val H = 0.01 // 소재 경계 표본 간격(mm)
private const val STEP = 0.01 // 경로 표본(mm)
private const val TOL = 2e-3 // 표본 기준 여유(mm)
private const val PEN = 1e-3 // 이보다 깊으면 겹침(쓴 글 반올림 0.0007 을 받는다)
private const val RAY = 1e5 // 반직선 길이(설계 정의)
private const val NEAR = 80.0 // 반직선 · 입구 면은 도면에서 이만큼까지만 찍는다
private const val CELL = 0.5 // 점 격자(mm)
private const val HEAD = -10
private const val TAIL = -11
private const val MOUTH = -12
private const val ENTRY = 0
private const val SHAPE = 1
private const val BACK = 2
private val INF = Double.POSITIVE_INFINITY
private val SLOT = listOf("바 깎는 쪽", "바 맞은편", "인서트 맞은편", "최소 가공경")

// ====================================================================== 기하

private class Q(val z: Double, val x: Double) {
    operator fun plus(o: Q) = Q(z + o.z, x + o.x)
    operator fun minus(o: Q) = Q(z - o.z, x - o.x)
    operator fun times(k: Double) = Q(z * k, x * k)
    infix fun dot(o: Q) = z * o.z + x * o.x
    infix fun cross(o: Q) = z * o.x - x * o.z
    val len: Double get() = sqrt(z * z + x * x)
    fun unit(): Q = len.let { Q(z / it, x / it) }
    fun ccw() = Q(-x, z)
    override fun toString() = "X${f3(2 * x)} Z${f3(z)}"
}

private sealed class EmEl {
    abstract val a: Q
    abstract val b: Q
    abstract val length: Double
    abstract fun at(t: Double): Q
    abstract fun dist(p: Q): Double
    abstract fun tan(t: Double): Q
    abstract fun shift(v: Q): EmEl
    abstract fun mirror(): EmEl
}

private class EmLn(override val a: Q, override val b: Q) : EmEl() {
    override val length = (b - a).len
    override fun at(t: Double) = Q(a.z + (b.z - a.z) * t, a.x + (b.x - a.x) * t)
    override fun dist(p: Q) = emSeg(a.z, a.x, b.z, b.x, p.z, p.x)
    override fun tan(t: Double) = (b - a).unit()
    override fun shift(v: Q) = EmLn(a + v, b + v)
    override fun mirror() = EmLn(Q(a.z, -a.x), Q(b.z, -b.x))
}

/** cw = G02(이 그림에서 시계) */
private class EmAc(override val a: Q, override val b: Q, val c: Q, val cw: Boolean) : EmEl() {
    val rad = (a - c).len
    private val a0 = atan2(a.x - c.x, a.z - c.z)
    val sweep: Double = emWrap(if (cw) a0 - atan2(b.x - c.x, b.z - c.z) else atan2(b.x - c.x, b.z - c.z) - a0)
    override val length = rad * sweep
    override fun at(t: Double): Q {
        val g = a0 + (if (cw) -sweep else sweep) * t
        return Q(c.z + rad * cos(g), c.x + rad * sin(g))
    }

    fun covers(p: Q): Boolean {
        val g = atan2(p.x - c.x, p.z - c.z)
        return emWrap(if (cw) a0 - g else g - a0) <= sweep + 1e-12
    }

    override fun dist(p: Q): Double = if ((p - c).len > 1e-12 && covers(p)) abs((p - c).len - rad) else min((p - a).len, (p - b).len)
    override fun tan(t: Double): Q {
        val u = (at(t) - c).unit()
        return if (cw) Q(u.x, -u.z) else u.ccw()
    }

    override fun shift(v: Q) = EmAc(a + v, b + v, c + v, cw)
    override fun mirror() = EmAc(Q(a.z, -a.x), Q(b.z, -b.x), Q(c.z, -c.x), !cw)
}

private fun emWrap(g: Double): Double {
    var v = g % (2 * PI)
    if (v < 0) v += 2 * PI
    return v
}

private fun emSeg(az: Double, ax: Double, bz: Double, bx: Double, pz: Double, px: Double): Double {
    val dz = bz - az
    val dx = bx - ax
    val l2 = dz * dz + dx * dx
    var t = if (l2 <= 0) 0.0 else ((pz - az) * dz + (px - ax) * dx) / l2
    if (t < 0) t = 0.0 else if (t > 1) t = 1.0
    val ez = pz - (az + dz * t)
    val ex = px - (ax + dx * t)
    return sqrt(ez * ez + ex * ex)
}

/** 볼록 다각형(반시계 n 개)과 점: 안이면 −(경계까지), 밖이면 경계까지 */
private fun emPoly(zs: DoubleArray, xs: DoubleArray, n: Int, pz: Double, px: Double): Double {
    var inside = true
    var d = INF
    for (i in 0 until n) {
        val j = if (i + 1 == n) 0 else i + 1
        if ((zs[j] - zs[i]) * (px - xs[i]) - (xs[j] - xs[i]) * (pz - zs[i]) < 0) inside = false
        val dd = emSeg(zs[i], xs[i], zs[j], xs[j], pz, px)
        if (dd < d) d = dd
    }
    return if (inside) -d else d
}

/** 볼록 다각형 n 개에서 [edge] 변까지 거리 */
private fun emEdge(zs: DoubleArray, xs: DoubleArray, n: Int, edge: Int, pz: Double, px: Double): Double {
    val j = if (edge + 1 == n) 0 else edge + 1
    return emSeg(zs[edge], xs[edge], zs[j], xs[j], pz, px)
}

private fun q(p: Pt) = Q(p.z, p.x)

private fun emEl(s: Seg): EmEl = when (s) {
    is Line -> EmLn(q(s.start), q(s.end))
    is Arc -> EmAc(q(s.start), q(s.end), q(s.center), s.cw)
}

/** 팁 번호 → 노즈 중심에서 가상 인선(설계 문서 표, Haas 워크북 69~70쪽) */
private fun emTip(tip: Int, r: Double): Q = when (tip) {
    1 -> Q(r, r)
    2 -> Q(-r, r)
    3 -> Q(-r, -r)
    4 -> Q(r, -r)
    5 -> Q(r, 0.0)
    6 -> Q(0.0, r)
    7 -> Q(-r, 0.0)
    8 -> Q(0.0, -r)
    else -> Q(0.0, 0.0)
}

/** 요소의 공구 쪽 법선(단위): 직선은 G41 이면 진행 왼쪽, 원호는 공구가 중심 쪽이면 안쪽 */
private fun emNormal(e: EmEl, p: Q, left: Boolean): Q = when (e) {
    is EmLn -> (e.b - e.a).unit().ccw().let { if (left) it else it * -1.0 }
    is EmAc -> (p - e.c).unit().let { if (left != e.cw) it * -1.0 else it }
}

/** 도면 요소를 공구 쪽으로 rr 민 보정선(원호는 같은 중심). 공구가 중심 쪽인 원호의 R − rr ≤ 0 이면 null(제어기 알람) */
private fun emOffset(e: EmEl, rr: Double, left: Boolean): EmEl? = when (e) {
    is EmLn -> emNormal(e, e.a, left).let { EmLn(e.a + it * rr, e.b + it * rr) }
    is EmAc -> {
        val inward = left != e.cw
        if (inward && e.rad - rr <= 1e-9) null else EmAc(e.a + emNormal(e, e.a, left) * rr, e.b + emNormal(e, e.b, left) * rr, e.c, e.cw)
    }
}

// ====================================================================== NC 읽기

private class EmBlk(val text: String, val g40: Boolean, val end: Q, val el: EmEl?)

/**
 * NC 글 → 블록. X 직경 → 반경, 빠진 축은 앞 값, G02/G03 은 끝점 · R 로 중심(180° 이하 쪽). 첫 좌표 줄은 시작점(움직임 없음), 축 없는 줄도 움직임 없음.
 * 앱 글(회사 표기)은 옛 꼴로 되돌려([Legacy]) 읽는다
 */
private fun emParse(lines: List<String>): List<EmBlk> {
    var z: Double? = null
    var x: Double? = null
    var g = "G00"
    val out = mutableListOf<EmBlk>()
    for (line in Legacy.nc(lines)) {
        var nz = z
        var nx = x
        var rr: Double? = null
        var g40 = false
        var axis = false
        for (w in line.trim().split(' ')) {
            if (w.isEmpty()) continue
            when (w[0]) {
                'G' -> when (w) {
                    "G00", "G01", "G02", "G03" -> g = w
                    "G40" -> g40 = true
                }
                'X' -> nx = w.substring(1).toDouble() / 2
                'Z' -> nz = w.substring(1).toDouble()
                'R' -> rr = w.substring(1).toDouble()
            }
            if (w[0] == 'X' || w[0] == 'Z') axis = true
        }
        // 축 없는 줄(회사 꼴 보정 혼자 줄 `G41` · `G42` · `G40`)은 움직이지 않는다 — 자리는 앞 줄 그대로(첫 줄이면 아직 없어 NaN)
        if (!axis) {
            out += EmBlk(line, g40, Q(z ?: Double.NaN, x ?: Double.NaN), null)
            continue
        }
        val to = Q(nz!!, nx!!)
        val from = if (z != null && x != null) Q(z, x) else null
        val el: EmEl? = when {
            from == null -> null
            (g == "G02" || g == "G03") && rr != null -> {
                val ch = to - from
                val h = sqrt(max(rr * rr - ch.len * ch.len / 4, 0.0))
                val side = if (g == "G02") Q(ch.x, -ch.z).unit() else ch.ccw().unit()
                EmAc(from, to, (from + to) * 0.5 + side * h, g == "G02")
            }
            else -> EmLn(from, to)
        }
        out += EmBlk(line, g40, to, el)
        z = nz
        x = nx
    }
    return out
}

// ====================================================================== 공구

private class EmTool(val bar: BoringBar, val r: Double, val reverse: Boolean) {
    /** 인서트(노즈 중심 기준, 반시계 다각형 — 닫는 변이 노즈 현 T2–T1) */
    val iz: DoubleArray
    val ix: DoubleArray
    val t1: Q
    val t2: Q

    /** 노즈 중심에서 주절삭날 · 끝날 접점 방향 */
    val n1: Q
    val n2: Q
    private val wedge: Double
    val refZ = if (reverse) r else -r
    val top = r - bar.f + bar.dia / 2
    val bottom = r - bar.f - bar.dia / 2
    val frontBottom: Q
    val frontTop: Q
    val insZ: Double

    /** 노즈 원호 표본(노즈 중심 기준) */
    val arcPts: List<Q>

    init {
        val kr = bar.kappaDeg * PI / 180
        val eps = bar.shape.epsDeg * PI / 180
        fun rot(v: Q, g: Double) = Q(v.z * cos(g) - v.x * sin(g), v.z * sin(g) + v.x * cos(g))
        val f1 = rot(Q(-1.0, 0.0), kr) // 주절삭날: 꼭짓점에서 이송 방향(−Z)과 κr
        val f2 = rot(f1, eps) // 끝날: 주절삭날에서 ε
        val corner = (f1 + f2).unit() * (-r / sin(eps / 2))
        val tl = r / tan(eps / 2)
        val l = bar.edge
        val fwd = listOf(corner + f1 * tl, corner + f1 * l) +
            (if (bar.shape == InsertShape.T) emptyList() else listOf(corner + (f1 + f2) * l)) +
            listOf(corner + f2 * l, corner + f2 * tl)
        fun outward(e: Q, other: Q): Q = e.ccw().let { if ((it dot other) < 0) it else it * -1.0 }
        val m1 = outward(f1, f2)
        val m2 = outward(f2, f1)
        insZ = fwd.maxOf { it.z } + r
        fun mir(p: Q) = if (reverse) Q(-p.z, p.x) else p
        val pts = fwd.map(::mir)
        var area = 0.0
        for (i in pts.indices) area += pts[i] cross pts[(i + 1) % pts.size]
        val ccw = if (area >= 0) pts else pts.reversed()
        iz = DoubleArray(ccw.size) { ccw[it].z }
        ix = DoubleArray(ccw.size) { ccw[it].x }
        t1 = mir(corner + f1 * tl)
        t2 = mir(corner + f2 * tl)
        n1 = mir(m1)
        n2 = mir(m2)
        wedge = if ((n1 cross n2) < 0) -1.0 else 1.0
        if (reverse) {
            val zf = pts.minOf { it.z }
            frontBottom = Q(zf, bottom)
            frontTop = Q(zf, top)
        } else {
            frontBottom = corner + f1 * ((bottom - corner.x) / f1.x)
            frontTop = corner + f1 * ((top - corner.x) / f1.x)
        }
        val g1 = atan2(n1.x, n1.z)
        val span = PI - eps
        arcPts = if (r <= 0) emptyList() else (0..8).map { k -> (g1 + wedge * span * k / 8).let { Q(r * cos(it), r * sin(it)) } }
    }

    private fun inWedge(pz: Double, px: Double) = (n1.z * px - n1.x * pz) * wedge >= 0 && (pz * n2.x - px * n2.z) * wedge >= 0

    /** 인서트 안(둥근 끝 포함)인가 — 노즈 중심 기준 점 */
    fun insInside(pz: Double, px: Double): Boolean {
        var inPoly = true
        val n = iz.size
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            if ((iz[j] - iz[i]) * (px - ix[i]) - (ix[j] - ix[i]) * (pz - iz[i]) < 0) {
                inPoly = false
                break
            }
        }
        if (inPoly) return true
        if (r <= 0) return false
        // 노즈 현(T2 → T1) 바깥(꼭짓점 쪽) 원 조각
        return pz * pz + px * px < r * r && ((t1.z - t2.z) * (px - t2.x) - (t1.x - t2.x) * (pz - t2.z)) * capSide < 0
    }

    /** 현의 꼭짓점 쪽 부호: 다각형 안쪽(꼭짓점 반대)이 양이 되게 */
    private val capSide: Double = run {
        val inner = Q(iz.average(), ix.average())
        if (((t1.z - t2.z) * (inner.x - t2.x) - (t1.x - t2.x) * (inner.z - t2.z)) > 0) 1.0 else -1.0
    }

    /** 인서트(둥근 끝)까지 거리, 안이면 음수 — 노즈 중심 기준 점. 경계 = 날 사슬(현 빼고) + 노즈 원호 */
    fun insDist(pz: Double, px: Double): Double {
        var d = INF
        val n = iz.size
        for (i in 0 until n - 1) d = min(d, emSeg(iz[i], ix[i], iz[i + 1], ix[i + 1], pz, px))
        if (r > 0) {
            val l = sqrt(pz * pz + px * px)
            val da = if (l > 1e-12 && inWedge(pz, px)) abs(l - r) else min(Q(pz - t1.z, px - t1.x).len, Q(pz - t2.z, px - t2.x).len)
            d = min(d, da)
        }
        return if (insInside(pz, px)) -d else d
    }

    /** 노즈 [c] 의 바(반시계 4 꼭짓점: 앞 아래, 뒤 아래, 뒤 위, 앞 위 — 앞면 = 3번 변). 뒤는 [clipZ] 에서 자른다 */
    fun barPoly(c: Q, clipZ: Double, zs: DoubleArray, xs: DoubleArray) {
        // 자르는 곳은 바 앞(비스듬한 앞면의 끝)보다 뒤 — 안 그러면 다각형이 뒤집힌다
        val back = min(c.z + refZ + (bar.overhang ?: RAY), max(clipZ, c.z + max(frontBottom.z, frontTop.z) + 1))
        zs[0] = c.z + frontBottom.z; xs[0] = c.x + frontBottom.x
        zs[1] = back; xs[1] = c.x + bottom
        zs[2] = back; xs[2] = c.x + top
        zs[3] = c.z + frontTop.z; xs[3] = c.x + frontTop.x
    }

    /** 노즈 [c] 의 최소 가공경 구간(축 둘레 지름 DMIN, 인선 z 에서 인서트 끝까지 — 뒤보링은 −Z 쪽) */
    fun zonePoly(c: Q, zs: DoubleArray, xs: DoubleArray): Boolean {
        val d = bar.dMin ?: return false
        val tipZ = c.z + refZ
        val lo = if (reverse) tipZ - insZ else tipZ
        val hi = if (reverse) tipZ else tipZ + insZ
        zs[0] = lo; xs[0] = -d / 2
        zs[1] = hi; xs[1] = -d / 2
        zs[2] = hi; xs[2] = d / 2
        zs[3] = lo; xs[3] = d / 2
        return true
    }
}

// ====================================================================== 소재

private class EmWall(val el: EmEl, val tag: Int, val mirrored: Boolean) {
    override fun toString() = (if (mirrored) "맞은편 " else "") + when (tag) {
        HEAD -> "입구 쪽 반직선"
        TAIL -> "깊은 쪽 반직선"
        MOUTH -> "입구 면"
        else -> "도면 요소 $tag"
    }
}

/** 소재 경계(설계 문서 '소재' 정의를 따로 짬)와 그 표본 점 · 격자, 안팎(ρ) */
private class EmMat(drawing: List<EmEl>, faceAtStart: Boolean) {
    val reverse = drawing.last().b.z > drawing.first().a.z
    val ext: List<EmEl>
    val walls = mutableListOf<EmWall>()
    val zTop: Double
    val zDeep: Double
    val closed: Boolean
    val faceZ: Double?
    val sz: DoubleArray
    val sx: DoubleArray
    val sw: IntArray
    val mirroredWall: BooleanArray
    val zMax: Double
    val gz0: Double
    val gx0: Double
    val gnz: Int
    val gnx: Int
    val cells: Array<IntArray>

    init {
        val first = drawing.first()
        val last = drawing.last()
        fun ray(p: Q, d0: Q): Q? {
            val d = if (abs(d0.z) <= 2e-3) Q(0.0, if (d0.x >= 0) 1.0 else -1.0) else d0
            if (d.x >= 0) return p + d * RAY
            val len = min(RAY, p.x / -d.x)
            return when {
                len <= 1e-12 -> null
                len < RAY -> Q(p.z + d.z * len, 0.0)
                else -> p + d * len
            }
        }
        val head = if (faceAtStart) null else ray(first.a, first.tan(0.0) * -1.0)?.let { EmLn(it, first.a) }
        val tail = if (last.b.x > 1e-9) ray(last.b, last.tan(1.0))?.let { EmLn(last.b, it) } else null
        ext = listOfNotNull(head) + drawing + listOfNotNull(tail)
        val tags = listOfNotNull(head?.let { HEAD }) + drawing.indices + listOfNotNull(tail?.let { TAIL })
        val a = ext.first().a
        val b = ext.last().b
        val mouthA = a.z >= b.z
        val mouth = if (mouthA) a else b
        val deep = if (mouthA) b else a
        zTop = mouth.z
        zDeep = deep.z
        closed = deep.x <= 1e-9
        val mouthRay = if (mouthA) head else tail
        faceZ = if (faceAtStart || mouth.x <= 1e-9 || mouthRay == null || mouthRay.a.z == mouthRay.b.z) zTop else null
        val up = EmLn(mouth, Q(mouth.z, mouth.x + RAY))
        val chain = ext.zip(tags) + (up to MOUTH)
        for ((e, t) in chain) walls += EmWall(e, t, false)
        for ((e, t) in chain) walls += EmWall(e.mirror(), t, true)
        mirroredWall = BooleanArray(walls.size) { walls[it].mirrored }
        // 표본: 도면은 전부, 반직선 · 입구 면은 도면 끝에서 NEAR 까지
        val ends = listOf(first.a, last.b)
        val pz = ArrayList<Double>()
        val px = ArrayList<Double>()
        val pw = ArrayList<Int>()
        for ((w, wall) in walls.withIndex()) {
            val e = wall.el
            val part: EmEl? = when (wall.tag) {
                HEAD -> EmLn(e.b, e.b + (e.a - e.b).unit() * min(e.length, NEAR))
                TAIL -> EmLn(e.a, e.a + (e.b - e.a).unit() * min(e.length, NEAR))
                MOUTH -> {
                    val m = if (wall.mirrored) Q(e.a.z, -e.a.x) else e.a
                    if (ends.minOf { (it - m).len } > NEAR) null else EmLn(e.a, e.a + (e.b - e.a).unit() * NEAR)
                }
                else -> e
            }
            if (part == null) continue
            val n = max(1, ceil(part.length / H).toInt())
            for (i in 0..n) {
                val p = part.at(i.toDouble() / n)
                pz += p.z
                px += p.x
                pw += w
            }
        }
        sz = pz.toDoubleArray()
        sx = px.toDoubleArray()
        sw = pw.toIntArray()
        zMax = sz.max()
        gz0 = sz.min() - 1
        gx0 = sx.min() - 1
        gnz = ceil((zMax + 1 - gz0) / CELL).toInt() + 1
        gnx = ceil((sx.max() + 1 - gx0) / CELL).toInt() + 1
        val count = IntArray(gnz * gnx)
        val idx = IntArray(sz.size) { (floor((sz[it] - gz0) / CELL).toInt()) * gnx + floor((sx[it] - gx0) / CELL).toInt() }
        for (c in idx) count[c]++
        val lists = Array(gnz * gnx) { IntArray(count[it]) }
        val fill = IntArray(gnz * gnx)
        for (i in idx.indices) lists[idx[i]][fill[idx[i]]++] = i
        cells = lists
    }

    /** 상자 안 격자의 점마다 */
    inline fun each(z0: Double, z1: Double, x0: Double, x1: Double, f: (Int) -> Unit) {
        val i0 = max(0, floor((z0 - gz0) / CELL).toInt())
        val i1 = min(gnz - 1, floor((z1 - gz0) / CELL).toInt())
        val j0 = max(0, floor((x0 - gx0) / CELL).toInt())
        val j1 = min(gnx - 1, floor((x1 - gx0) / CELL).toInt())
        for (i in i0..i1) for (j in j0..j1) for (p in cells[i * gnx + j]) f(p)
    }

    /** ρ(z): 소재 = {|x| ≥ ρ}. 입구 앞은 공기(∞), 깊은 끝 너머는 막혔으면 0. 면(수직)에서는 넓은 쪽 */
    fun rho(z: Double): Double {
        if (z > zTop) return INF
        if (z < zDeep) return if (closed) 0.0 else INF
        var best = -1.0
        for (e in ext) {
            val za = min(e.a.z, e.b.z)
            val zb = max(e.a.z, e.b.z)
            if (zb - za <= 1e-9 || z < za || z > zb) continue
            val x = when (e) {
                is EmLn -> e.a.x + (e.b.x - e.a.x) * (z - e.a.z) / (e.b.z - e.a.z)
                is EmAc -> e.c.x + (if (e.at(0.5).x >= e.c.x) 1 else -1) * sqrt(max(0.0, e.rad * e.rad - (z - e.c.z) * (z - e.c.z)))
            }
            if (x > best) best = x
        }
        return if (best < 0) INF else best
    }

    /** 점이 소재 안이면 경계까지 거리(겹친 깊이), 아니면 0 */
    fun depth(p: Q): Double {
        val rr = max(rho(p.z), max(rho(p.z - 1e-7), rho(p.z + 1e-7)))
        if (abs(p.x) <= rr + 1e-9) return 0.0
        var d = INF
        for (w in walls) d = min(d, w.el.dist(p))
        return d
    }
}

// ====================================================================== 한 자세의 표본 여유

/** 슬롯: 0 바 깎는 쪽, 1 바 맞은편, 2 인서트 맞은편, 3 최소 가공경 — all 은 예외 없음, broad · strict 는 깎는 면 예외(넓은 · 좁은 정의)를 뺀 것 */
private class EmEv {
    val all = DoubleArray(4) { INF }
    val broad = DoubleArray(4) { INF }
    val strict = DoubleArray(4) { INF }
    val broadPt = IntArray(4) { -1 }
    val allPt = IntArray(4) { -1 }
    var turret = INF
    var pen = 0.0
    var penWhat = ""
    var exD = INF
    var exSlot = -1
    var exPt = -1
    var exFront = false
    fun broadMin() = min(broad.min(), turret)
    fun allMin() = min(all.min(), turret)
    fun strictMin() = min(strict.min(), turret)
    fun worstBroad(): Int = (0..3).minBy { broad[it] }
}

private class EmMv(val line: Int, val path: EmEl, val kind: Int, val elem: Int, val text: String)

private class EmCase(
    val name: String,
    val sol: RoundSolution,
    val drawn: RoundSolution?,
    val r: Double,
    face: Boolean,
    val bar: BoringBar,
    val safe: Double,
    val plan: HolderPlan,
    val log: EmLog,
    val verbose: Boolean,
) {
    val drawing = sol.drawing.segs.map(::emEl)
    val prog = sol.program.segs.map(::emEl)
    val mat = EmMat(drawing, face)
    val tool = EmTool(bar, r, mat.reverse)
    val tv = emTip(sol.tip, r)
    val left = sol.side == Side.G41
    private var margin = safe + 0.3
    private val clipZ get() = mat.zMax + margin + 1
    private val bz = DoubleArray(4)
    private val bx = DoubleArray(4)
    private val zz = DoubleArray(4)
    private val zx = DoubleArray(4)

    // ------------------------------------------------------------------ 깎는 면(예외)

    /** 도면 요소 j 가 주절삭날 쪽 직선(노즈가 닿는 방향이 끝날 접점보다 주절삭날 접점 쪽)인가 */
    private fun facing(j: Int): Boolean {
        val e = drawing[j] as? EmLn ?: return false
        if (e.length <= 1e-9) return false
        val u = emNormal(e, e.a, left) * -1.0
        return (u dot tool.n1) > (u dot tool.n2)
    }

    private fun convexJoin(j: Int): Boolean {
        val a = drawing[j]
        val b = drawing[j + 1]
        if (a.length <= 1e-9 || b.length <= 1e-9) return false
        return (a.tan(1.0) cross b.tan(0.0)) * (if (left) 1.0 else -1.0) < -1e-9
    }

    /** 넓은 정의(설계 문서): 블록 요소 k 와 볼록 이음 전까지 앞 요소들의 주절삭날 쪽 직선 */
    fun broadFaces(k: Int): List<Int> {
        if (k < 0 || k >= drawing.size) return emptyList()
        val out = mutableListOf<Int>()
        if (facing(k)) out += k
        var j = k
        while (j + 1 < drawing.size) {
            if (convexJoin(j)) break
            if (facing(j + 1)) out += j + 1
            j++
        }
        return out
    }

    /** 좁은 정의(사용자 문구): 지금 노즈가 닿은(노즈 중심에서 r 안) 주절삭날 쪽 직선 */
    fun strictFaces(c: Q): List<Int> = drawing.indices.filter { facing(it) && drawing[it].dist(c) <= r + TOL }

    private val flagCache = HashMap<String, BooleanArray?>()

    /** 직선들 위에 놓인 소재 경계 토막(이웃 · 거울 포함)에 깃발 */
    fun flags(faces: List<Int>): BooleanArray? {
        if (faces.isEmpty()) return null
        return flagCache.getOrPut(faces.joinToString(",")) {
            val lines = faces.map { drawing[it] as EmLn }
            fun on(l: EmLn, p: Q) = abs((p - l.a) cross (l.b - l.a).unit()) <= 1e-6
            BooleanArray(mat.walls.size) { w -> (mat.walls[w].el as? EmLn)?.let { e -> lines.any { on(it, e.a) && on(it, e.b) } } == true }
        }
    }

    // ------------------------------------------------------------------ 표본 여유

    /** 바 · 최소 가공경 · 인서트 맞은편 · 공구대와 꼭짓점 겹침(0.01 mm 마다는 아님 — 립시츠 건너뜀) */
    fun full(c: Q, exB: BooleanArray?, exS: BooleanArray?): EmEv {
        val e = EmEv()
        val m = margin
        fun put(slot: Int, d: Double, p: Int, part: String) {
            val w = mat.sw[p]
            if (d < e.all[slot]) {
                e.all[slot] = d
                e.allPt[slot] = p
            }
            if (exB != null && exB[w]) {
                if (d < e.exD) {
                    e.exD = d
                    e.exSlot = slot
                    e.exPt = p
                }
            } else if (d < e.broad[slot]) {
                e.broad[slot] = d
                e.broadPt[slot] = p
            }
            if ((exS == null || !exS[w]) && d < e.strict[slot]) e.strict[slot] = d
            if (-d > PEN && -d > e.pen) {
                e.pen = -d
                e.penWhat = "$part ↔ ${mat.walls[w]} ${Q(mat.sz[p], mat.sx[p])}"
            }
        }
        // 바
        tool.barPoly(c, clipZ, bz, bx)
        mat.each(bz.min() - m, bz.max() + m, bx.min() - m, bx.max() + m) { p ->
            put(if (mat.mirroredWall[mat.sw[p]]) 1 else 0, emPoly(bz, bx, 4, mat.sz[p], mat.sx[p]), p, "바")
        }
        // 최소 가공경(축 둘레 대칭 — 깎는 쪽 사슬만)
        if (tool.zonePoly(c, zz, zx)) {
            mat.each(zz.min() - m, zz.max() + m, zx.min() - m, zx.max() + m) { p ->
                if (!mat.mirroredWall[mat.sw[p]]) put(3, emPoly(zz, zx, 4, mat.sz[p], mat.sx[p]), p, "최소 가공경")
            }
        }
        // 인서트 맞은편
        val iz0 = tool.iz.min() + c.z - r
        val iz1 = tool.iz.max() + c.z + r
        val ix0 = tool.ix.min() + c.x - r
        val ix1 = tool.ix.max() + c.x + r
        mat.each(iz0 - m, iz1 + m, ix0 - m, ix1 + m) { p ->
            if (mat.mirroredWall[mat.sw[p]]) put(2, tool.insDist(mat.sz[p] - c.z, mat.sx[p] - c.x), p, "인서트")
        }
        // 공구대
        val o = bar.overhang
        val f = mat.faceZ
        if (o != null && f != null) e.turret = c.z + tool.refZ + o - f
        // 꼭짓점이 소재 안
        for (i in 0 until 4) vertex(e, Q(bz[i], bx[i]), "바 꼭짓점 $i")
        if (bar.dMin != null) for (i in 0 until 4) vertex(e, Q(zz[i], zx[i]), "최소 가공경 꼭짓점 $i")
        // 예외로 넘어간 가장 가까운 점이 바 앞면(3번 변)인가
        if (e.exSlot in 0..1) {
            val pz = mat.sz[e.exPt]
            val px = mat.sx[e.exPt]
            val all = abs(emPoly(bz, bx, 4, pz, px))
            e.exFront = emEdge(bz, bx, 4, 3, pz, px) <= all + 1e-9
        }
        return e
    }

    private fun vertex(e: EmEv, v: Q, what: String) {
        val d = mat.depth(v)
        if (d > PEN && d > e.pen) {
            e.pen = d
            e.penWhat = "$what $v 가 소재 안"
        }
    }

    /** 인서트가 깎는 쪽 소재에 들어간 깊이(0.01 mm 마다): 소재 점이 인서트 안 · 인서트 꼭짓점 · 노즈 원호 점이 소재 안 */
    fun insCut(c: Q): Pair<Double, String> {
        var pen = 0.0
        var what = ""
        val iz0 = tool.iz.min() + c.z - r - 0.01
        val iz1 = tool.iz.max() + c.z + r + 0.01
        val ix0 = tool.ix.min() + c.x - r - 0.01
        val ix1 = tool.ix.max() + c.x + r + 0.01
        mat.each(iz0, iz1, ix0, ix1) { p ->
            if (!mat.mirroredWall[mat.sw[p]] && tool.insInside(mat.sz[p] - c.z, mat.sx[p] - c.x)) {
                val d = -tool.insDist(mat.sz[p] - c.z, mat.sx[p] - c.x)
                if (d > pen) {
                    pen = d
                    what = "인서트 ↔ ${mat.walls[mat.sw[p]]} ${Q(mat.sz[p], mat.sx[p])}"
                }
            }
        }
        val vs = (0 until tool.iz.size).map { Q(tool.iz[it], tool.ix[it]) } + tool.arcPts
        for (v in vs) {
            val d = mat.depth(v + c)
            if (d > pen) {
                pen = d
                what = "인서트 꼭짓점 · 노즈 ${v + c} 가 소재 안"
            }
        }
        return pen to what
    }

    // ------------------------------------------------------------------ 따라가기

    private var exceptionPoses = 0

    /** 움직임들을 따라가며 ① · ② */
    private var followedAny = false

    fun follow(pname: String, moves: List<EmMv>, cut: HolderCut?) {
        followedAny = true
        var worstShape = INF
        var backMin = INF
        var backWhere = ""
        var insPenMax = 0.0
        var firstBad = true
        var firstIns = true
        var firstPen = true
        for (mv in moves) {
            val exB = if (mv.kind == ENTRY) null else flags(broadFaces(mv.elem))
            val len = mv.path.length
            val n = max(1, ceil(len / STEP).toInt())
            var nextFull = 0.0
            for (i in 0..n) {
                val s = len * i / n
                val c = mv.path.at(i.toDouble() / n)
                val (ip, iw) = insCut(c)
                if (ip > insPenMax) insPenMax = ip
                if (ip > PEN && firstIns) {
                    firstIns = false
                    log.bad("① $pname 인서트 깎는 쪽 겹침 > 0.001", "$name · ${mv.line + 1}번 줄 '${mv.text}' 노즈 $c: ${f4(ip)} ($iw)")
                }
                if (s + 1e-12 < nextFull && i < n) continue
                val e = full(c, exB, if (mv.kind == ENTRY) null else flags(strictFaces(c)))
                if (e.pen > PEN && firstPen) {
                    firstPen = false
                    log.bad("① $pname ${if (mv.kind == BACK) "후퇴" else "형상"} 겹침(예외 면 포함)", "$name · ${mv.line + 1}번 줄 '${mv.text}' 노즈 $c: ${f4(e.pen)} (${e.penWhat})")
                }
                if (mv.kind == BACK) {
                    if (e.broadMin() < backMin) {
                        backMin = e.broadMin()
                        val slot = e.worstBroad()
                        val pt = e.broadPt[slot]
                        backWhere = "${mv.line + 1}번 줄 '${mv.text}' 노즈 $c · " + (if (e.turret <= e.broad[slot]) "공구대 ${f4(e.turret)}" else "${SLOT[slot]} ↔ ${if (pt >= 0) "${mat.walls[mat.sw[pt]]} ${Q(mat.sz[pt], mat.sx[pt])}" else "-"} (요소 ${mv.elem})")
                    }
                } else {
                    val sB = e.broadMin() - safe
                    worstShape = min(worstShape, sB)
                    if (sB < -TOL && firstBad) {
                        firstBad = false
                        val slot = e.worstBroad()
                        val pt = e.broadPt[slot]
                        val at = if (pt >= 0) "${mat.walls[mat.sw[pt]]} ${Q(mat.sz[pt], mat.sx[pt])}" else "공구대"
                        log.bad("① $pname 여유 < 안전 여유(예외 밖)", "$name · ${mv.line + 1}번 줄 '${mv.text}' 노즈 $c: ${f4(sB)} (${if (e.turret - safe <= sB + 1e-12) "공구대" else SLOT[slot]} ↔ $at)")
                    }
                    if (e.allMin() - safe < -TOL && sB >= -TOL) classify(pname, mv, c, e)
                }
                nextFull = s + max(STEP, min(e.allMin(), margin) - safe)
            }
        }
        log.note("① $pname 형상 표본 여유 − 안전 여유(최소)", worstShape)
        log.note("① $pname 인서트 깎는 쪽 겹침 깊이(최대)", insPenMax)
        if (cut != null) {
            log.note("① $pname 후퇴 표본 여유(최소)", backMin)
            log.note("① $pname 후퇴: 표본 − 보고한 후퇴 여유", backMin - cut.retractClear)
            if (backMin < cut.retractClear - TOL) log.bad("① $pname 후퇴 여유를 구현이 크게 봄", "$name: 표본 ${f4(backMin)} < 보고 ${f4(cut.retractClear)} · ${cut.nc.drop(cut.retractFrom)} · 표본 최소 자리 $backWhere · 끝점 ${plan.end?.element} ${plan.end?.part}")
            if (StopWarning.RETRACT_TIGHT in plan.warnings) log.info("① $pname RETRACT_TIGHT 사례", "$name: 표본 후퇴 최소 ${f4(backMin)} · 보고 ${f4(cut.retractClear)} · ${cut.retract} · ${cut.nc.drop(cut.retractFrom)} · 자리 $backWhere")
            if (StopWarning.RETRACT_TIGHT !in plan.warnings && backMin < safe - TOL) log.bad("① $pname 후퇴 여유 < 안전 여유인데 RETRACT_TIGHT 없음", "$name: 표본 ${f4(backMin)}")
            log.count("후퇴 ${cut.retract}")
        }
        if (verbose) println("    [$pname] 형상 표본 여유 − 안전 여유 최소 ${f4(worstShape)} · 인서트 날 겹침 최대 ${f4(insPenMax)}${if (cut != null) " · 후퇴 표본 최소 ${f4(backMin)} (보고 ${f4(cut.retractClear)})" else ""}")
    }

    /** ② 예외로 넘어간 자세 나누기 */
    private fun classify(pname: String, mv: EmMv, c: Q, e: EmEv) {
        exceptionPoses++
        val w = mat.walls[mat.sw[e.exPt]]
        val j = w.tag
        val touched = j >= 0 && drawing[j].dist(c) <= r + TOL
        val strictOk = e.strictMin() - safe >= -TOL
        val part = when (e.exSlot) {
            0, 1 -> if (e.exFront) "바 앞면" else "바 앞면 밖(위 · 아래 변)"
            2 -> "인서트(맞은편 쪽)"
            else -> "최소 가공경 구간"
        }
        val convexBetween = j > mv.elem && (mv.elem until j).any { k ->
            val d = drawing.getOrNull(k)
            d is EmAc && (left == d.cw) // 공구가 중심 반대쪽 = 볼록 원호
        }
        val rel = when {
            j < 0 -> "도면 밖 토막($w)"
            touched -> "지금 노즈가 닿은 면"
            // 그 면을 깎기 전에 끝점에서 멈췄나(예외로 다가갔지만 그 면은 깎지 않음)
            else -> "앞 요소 면(+${j - mv.elem})${if (convexBetween) " · 사이에 볼록 원호" else ""}${if ((plan.end?.element ?: Int.MAX_VALUE) < j) " · 그 면 전에 멈춤" else ""}"
        }
        val cat = "② 예외: $part · ${if (w.mirrored) "맞은편 반쪽" else "깎는 쪽"} · $rel · 좁은 정의로는 ${if (strictOk) "통과" else "멈춤"}"
        log.count(cat)
        log.note("$cat — 예외 면까지(mm)", e.exD)
        if (!touched && j >= 0) log.note("$cat — 노즈 원 ~ 그 면(mm)", drawing[j].dist(c) - r)
        log.info(cat, "$name · $pname ${mv.line + 1}번 줄 '${mv.text}' 노즈 $c: 예외 면 ${f4(e.exD)} mm ($w ${Q(mat.sz[e.exPt], mat.sx[e.exPt])}), 예외 밖 여유 − 기준 ${f4(e.broadMin() - safe)}, 좁은 정의 ${f4(e.strictMin() - safe)}")
        if (j < 0) log.bad("② 예외가 도면 밖 토막에", "$name · $pname ${mv.line + 1}번 줄 노즈 $c: $w")
    }

    // ------------------------------------------------------------------ 두 프로그램의 움직임

    /** 경로 위 인선 블록의 요소(되짚는 후퇴): G40 프로그램 경로 요소 위에 놓인 것, 없으면 −1(진입 이동) */
    private fun matchProg(el: EmEl): Int {
        var best = -1
        var bd = 2e-3
        for ((j, s) in prog.withIndex()) {
            if (s.length <= 1e-9) continue
            val d = maxOf(s.dist(el.at(0.0)), s.dist(el.at(0.5)), s.dist(el.at(1.0)))
            if (d < bd) {
                bd = d
                best = j
            }
        }
        return best
    }

    private fun label(i: Int, cut: HolderCut?, refs: List<Int>, tipEl: EmEl, g40: Boolean): Int = when {
        cut == null -> refs.getOrElse(i) { -1 }
        i < cut.kept -> refs[i]
        i < cut.retractFrom -> cut.element
        cut.retract == RetractKind.LIFT || g40 -> cut.element
        else -> matchProg(tipEl)
    }

    fun directMoves(nc: List<String>, cut: HolderCut?): List<EmMv> {
        val bl = emParse(nc)
        val refs = sol.ncRefs.map { it.element }
        val n0 = bl[0].end - tv
        val out = mutableListOf(EmMv(0, EmLn(n0, n0), ENTRY, -2, nc[0]))
        for (i in 1 until bl.size) {
            val el = bl[i].el ?: continue
            val back = cut != null && i >= cut.retractFrom
            val k = label(i, cut, refs, el, false)
            out += EmMv(i, el.shift(tv * -1.0), if (back) BACK else if (k < 0) ENTRY else SHAPE, k, nc[i])
        }
        return out
    }

    /** Z 로 빠지는 길을 재는 끝: 도면 맨 위 + 25 mm + 안전 여유 — 그 위는 입구 면 위 공기이거나 같은 구멍(반직선)이 이어진다 */
    private val zTop = min(200.0, drawing.maxOf { e -> (0..16).maxOf { e.at(it / 16.0).z } } + 25 + safe)

    /**
     * 기계 보정 제어기 모형(회사 꼴): 첫 줄 보정 혼자 줄 · 둘째 줄 `G0X…Z20.` 은 시작점, 셋째 줄부터 `G40` 혼자 줄 앞까지 모두 보정 블록 — 도면을 r 민 보정선을
     * 교점으로 잇고, Z20. 자리 노즈는 첫 블록 처음의 법선(시작 형식 A), 마지막 블록 끝은 법선(다음 `G40` 은 이동이 없다). 첫 깎는 줄까지는 진입, 빼는 줄부터 후퇴.
     * 취소는 둘 다 후퇴로 본다 — 형식 C(`G40` 줄에서 그 자리 → 끝점 − 팁 벡터, 그 뒤 `G0Z200.` 은 +Z) · 형식 A · B(`G0Z200.` 동안 그 자리 → (Z200, 같은 X) −
     * 팁 벡터로 곧게), Z 는 [zTop] 까지. 회사 꼴이 아니거나 오목 R ≤ r 블록 · 모형이 노즈 경로와 0.01 넘게 다르면 null
     */
    fun machineMoves(nc: List<String>, cut: HolderCut?): List<EmMv>? {
        val d = drawn ?: return null
        if (d.approach.isEmpty()) {
            log.count("기계 보정 모형 밖: 회사 꼴 아님")
            return null
        }
        val bl = emParse(nc)
        val refs = d.ncRefs.map { it.element }
        val g40 = bl.indexOfFirst { it.g40 }
        if (g40 < 4 || g40 != bl.size - 2 || bl[g40].el != null) {
            log.bad("① 기계 보정 틀", "$name: G40 이 끝에서 둘째 혼자 줄이 아님 · $nc")
            return null
        }
        val offs = (2 until g40).map { i ->
            val el = bl[i].el ?: run {
                log.bad("① 기계 보정 틀", "$name: ${i + 1}번 줄이 움직이지 않음 · $nc")
                return null
            }
            emOffset(el, r, left) ?: run {
                log.count("기계 보정 모형 밖: 오목 R ≤ r")
                return null
            }
        }
        val firstShape = nc.indexOfFirst { 'F' in it } + 1
        val retractFrom = cut?.retractFrom ?: refs.indexOfFirst { it >= d.drawing.segs.size }
        val kBack = cut?.element ?: (d.drawing.segs.size - 1)
        val n0 = offs[0].a
        val out = mutableListOf(EmMv(1, EmLn(n0, n0), ENTRY, -2, nc[1]))
        var from = n0
        var dev = 0.0
        for (i in offs.indices) {
            val line = i + 2
            val to = if (i == offs.size - 1) offs[i].b else emJoin(offs[i], offs[i + 1])
            val o = offs[i]
            var path: EmEl = if (o is EmAc) EmAc(from, to, o.c, o.cw) else EmLn(from, to)
            val orig = bl[line].el
            if (path is EmAc && orig is EmAc && path.sweep > orig.sweep + PI / 2) {
                log.count("기계 보정 모형: 짧은 원호 먹힘 → 곧은 선")
                path = EmLn(from, to)
            }
            val kind = if (line < firstShape) ENTRY else if (line >= retractFrom) BACK else SHAPE
            val k = when (kind) {
                ENTRY -> -1
                BACK -> kBack
                else -> label(line, cut, refs, orig!!, false)
            }
            // 앞 블록(원래 글)의 제어기 경로는 G40 노즈 경로와 같아야 — 모형 확인(형상 블록 사이 이음만: 진입 · 빼는 줄 쪽 꺾임은 경로 밖)
            if (kind == SHAPE && line + 1 < retractFrom && (cut == null || line < cut.kept)) dev = max(dev, prog.minOf { it.dist(to + tv) })
            out += EmMv(line, path, kind, k, nc[line])
            from = to
        }
        log.note("기계 보정 모형 이음 ~ G40 노즈 경로(앞 블록, 최대)", dev)
        if (dev > 0.01) {
            log.count("기계 보정 모형 밖: 이음이 노즈 경로와 ${if (dev > 0.1) "0.1" else "0.01"} 넘게 다름")
            log.info("기계 보정 모형 밖", "$name: 이음 차이 ${f4(dev)} · $nc")
            return null
        }
        val xe = bl[g40 - 1].end
        val far = Q(200.0, xe.x)
        fun up(a: Q, b: Q): EmEl = if (b.z <= zTop) EmLn(a, b) else EmLn(a, a + (b - a) * ((zTop - a.z) / (b.z - a.z)).coerceIn(0.0, 1.0))
        out += EmMv(g40, EmLn(from, xe - tv), BACK, kBack, "G40(형식 C — 그 자리에서 옮김)")
        out += EmMv(g40 + 1, up(xe - tv, far - tv), BACK, kBack, "G0Z200.(형식 C)")
        out += EmMv(g40 + 1, up(from, far - tv), BACK, kBack, "G0Z200.(형식 A · B — 남은 벡터를 지우며)")
        return out
    }

    // ------------------------------------------------------------------ 한 사례

    fun verify() {
        val w = plan.warnings
        log.count("경고 ${w.map { it.name }.sorted()}")
        if (verbose) {
            val e = plan.end
            println("  ● $name — 경고 $w")
            if (e != null) println("    직접 보정 끝점: 요소 ${e.element} t ${f4(e.t)} 인선 ${q(e.pose.tip)} · ${e.part}${if (e.opposite) " 맞은편" else ""} 여유 ${f4(e.margin)} · gap ${f4(e.gap)} @ ${q(e.gapAt)} · 기계 ${e.at.machine?.let { q(it) }} (${e.at.machineLine}줄)")
            plan.machineEnd?.let { println("    기계 보정 끝점: 요소 ${it.element} t ${f4(it.t)} 명령 ${it.at.machine?.let { p -> q(p) }} · ${it.part}${if (it.opposite) " 맞은편" else ""} 여유 ${f4(it.margin)}") }
            plan.direct?.let { println("    잘린 직접 보정: ${it.nc.drop(max(0, it.kept - 1))}  (${it.retract}, 후퇴 여유 ${f4(it.retractClear)})") }
            plan.machine?.let { println("    잘린 기계 보정: ${it.nc.drop(max(0, it.kept - 1))}  (${it.retract}, 후퇴 여유 ${f4(it.retractClear)})") }
            if (plan.end == null && w.isEmpty()) println("    끝까지 안전 · barClear ${plan.result.barClear?.let(::f4)} @ ${plan.result.barClearAt?.tip?.let(::q)}")
        }
        // 직접 보정
        when {
            StopWarning.UNSAFE_AT_ENTRY in w -> {
                val c = q(sol.entry) - tv
                val e = full(c, null, null)
                val s = min(e.allMin() - safe, -insCut(c).first)
                log.note("UNSAFE_AT_ENTRY: 표본 진입 자세 여유 − 기준", s)
                if (s > TOL) log.bad("① UNSAFE_AT_ENTRY 주장", "$name: 표본으로는 진입 자세 여유가 기준보다 ${f4(s)} 넉넉")
            }
            plan.end == null -> {
                follow("원래 직접 보정", directMoves(sol.nc, null), null)
                if (w.isEmpty()) barClearCheck()
            }
            plan.direct != null -> {
                val cut = plan.direct
                if (cut.nc.take(cut.kept) != sol.nc.take(cut.kept)) log.bad("① 직접 보정 앞 줄", "$name: 앞 ${cut.kept} 줄이 원래 NC 와 다름")
                if (cut.nc.drop(cut.retractFrom).any { "G40" in it } || cut.nc.take(cut.retractFrom).any { "G40" in it }) log.bad("① 직접 보정 G40", "$name: ${cut.nc}")
                follow("직접 보정", directMoves(cut.nc, cut), cut)
            }
            StopWarning.NOTHING_CUT in w -> log.count("직접 보정 NOTHING_CUT")
            else -> log.bad("① 잘린 직접 보정 없음", "$name: 끝점 ${plan.end} 인데 잘린 NC 없음 $w")
        }
        // 기계 보정
        if (drawn != null) {
            when {
                StopWarning.MACHINE_ENTRY in w -> {
                    if (plan.machine != null) log.bad("① MACHINE_ENTRY 인데 잘린 기계 보정", name)
                    machineEntryClaim()
                }
                plan.machineEnd == null -> machineMoves(drawn.nc, null)?.let { follow("원래 기계 보정", it, null) }
                plan.machine != null -> {
                    val cut = plan.machine
                    if (cut.nc.take(cut.kept) != drawn.nc.take(cut.kept)) log.bad("① 기계 보정 앞 줄", "$name: 앞 ${cut.kept} 줄이 원래 NC 와 다름")
                    val g40 = cut.nc.indices.filter { "G40" in cut.nc[it] }
                    if (g40 != listOf(cut.nc.size - 2) || cut.nc[cut.nc.size - 2] != "G40") log.bad("① 기계 보정 G40", "$name: G40 줄 $g40 이 끝에서 둘째 혼자 줄이 아님 · ${cut.nc}")
                    machineMoves(cut.nc, cut)?.let { follow("기계 보정", it, cut) }
                }
                else -> log.count("기계 보정 잘린 NC 없음 ${w.map { it.name }}")
            }
        }
        // ③
        plan.end?.takeIf { it.element >= 0 }?.let(::tooEarly)
        if (followedAny) log.followed++
        if (exceptionPoses > 0) log.count("예외를 쓴 사례")
    }

    /**
     * 끝까지 안전한 경로의 '가장 좁은 곳'(HolderResult.barClear — 화면 글): G40 노즈 경로(진입점 자세 · 진입 이동 · 요소)를 따라 바의 표본 직선 거리
     * (넓은 정의 예외 뺌)의 최솟값을 따로 — 0.01 mm 마다, 지금 최솟값보다 먼 곳은 립시츠로 건너뜀
     */
    private fun barClearCheck() {
        val bc = plan.result.barClear ?: return
        val keep = margin
        margin = max(margin, bc + 0.3)
        val moves = directMoves(sol.nc, null)
        var best = INF
        for (mv in moves) {
            val exB = if (mv.kind == ENTRY) null else flags(broadFaces(mv.elem))
            val len = mv.path.length
            val n = max(1, ceil(len / STEP).toInt())
            var next = 0.0
            for (i in 0..n) {
                val s = len * i / n
                if (s + 1e-12 < next && i < n) continue
                val e = full(mv.path.at(i.toDouble() / n), exB, null)
                val d = min(e.broad[0], e.broad[1])
                if (d < best) best = d
                next = s + max(STEP, min(d, margin) - best)
            }
        }
        val box = margin
        margin = keep
        if (!best.isFinite() || best > box) return log.count("가장 좁은 곳: 표본 상자 밖")
        log.note("가장 좁은 곳: 표본 − 구현 barClear(mm)", best - bc)
        if (abs(best - bc) > 2e-3) log.bad("① 가장 좁은 곳(barClear) 차이", "$name: 표본 ${f4(best)} · 구현 ${f4(bc)}")
        if (verbose) println("    가장 좁은 곳: 표본 ${f4(best)} · 구현 ${f4(bc)}")
    }

    /** 기계 보정 진입에서 멈춘다는 주장: 이 파일의 모형으로 G00 점 · 진입 블록을 0.01 mm 마다 — 표본 최소 여유가 기준보다 넉넉하면 틀림 */
    private fun machineEntryClaim() {
        val d = drawn!!
        val moves = machineMoves(d.nc, null) ?: return
        var worst = INF
        for (mv in moves.filter { it.kind == ENTRY }) {
            val n = max(1, ceil(mv.path.length / STEP).toInt())
            for (i in 0..n) {
                val c = mv.path.at(i.toDouble() / n)
                worst = min(worst, min(full(c, null, null).allMin() - safe, -insCut(c).first))
            }
        }
        log.note("MACHINE_ENTRY: 표본 진입 최소 여유 − 기준", worst)
        if (worst > TOL) log.bad("① MACHINE_ENTRY 주장", "$name: 표본으로는 기계 보정 진입이 기준보다 ${f4(worst)} 넉넉 · ${d.nc.take(3)}")
    }

    /** ③ 끝점 + 0.01 mm 는 표본 기준 아래인가(정확한 G40 노즈 경로를 따라) */
    private fun tooEarly(end: SafeEnd) {
        val segs = prog.withIndex().filter { it.value.length > 1e-9 }
        val cum = DoubleArray(segs.size + 1).also { a -> for (i in segs.indices) a[i + 1] = a[i] + segs[i].value.length }
        val i0 = segs.indexOfFirst { it.index == end.element }
        if (i0 < 0) return
        val sEnd = cum[i0] + end.t * segs[i0].value.length
        fun at(s: Double): Pair<Q, Int> {
            val i = (segs.indices).firstOrNull { s <= cum[it + 1] + 1e-12 } ?: (segs.size - 1)
            val sg = segs[i]
            return (sg.value.at(((s - cum[i]) / sg.value.length).coerceIn(0.0, 1.0)) - tv) to sg.index
        }
        val (c0, k0) = at(sEnd)
        if ((c0 - q(end.pose.nose)).len > 1e-6) log.info("③ 끝점 자리 차이", "$name: 경로 위 $c0 ≠ 끝점 노즈 ${q(end.pose.nose)}")
        val e0 = full(c0, flags(broadFaces(k0)), flags(strictFaces(c0)))
        val s0 = e0.broadMin() - safe
        log.note("③ 끝점 표본 여유 − 기준", s0)
        if (verbose) {
            val slot = e0.worstBroad()
            val pt = e0.broadPt[slot]
            println("    표본 끝점: 여유 − 기준 ${f4(s0)} (${SLOT[slot]} ↔ ${if (pt >= 0) "${mat.walls[mat.sw[pt]]} ${Q(mat.sz[pt], mat.sx[pt])}" else "-"}) · 예외 면까지 ${f4(e0.exD)} · 예외 없이 ${f4(e0.allMin() - safe)}")
        }
        if (sEnd + STEP > cum.last()) {
            log.count("③ 끝점이 경로 끝 0.01 mm 안")
            return
        }
        val (c1, k1) = at(sEnd + STEP)
        val e1 = full(c1, flags(broadFaces(k1)), flags(strictFaces(c1)))
        val s1 = e1.broadMin() - safe
        val ins1 = insCut(c1).first
        val below = s1 < 0 || ins1 > 1e-7
        log.ratio("③ 끝점 + 0.01 mm 가 표본 기준 아래", below)
        if (!below) log.info("③ 끝점 + 0.01 mm 도 표본 기준 이상", "$name: 끝점 요소 ${end.element} ${end.part}${if (end.opposite) " 맞은편" else ""} 여유 ${f4(end.margin)} · 표본 끝점 ${f4(s0)} · +0.01 ${f4(s1)} · 인서트 겹침 ${f4(ins1)}")
        if (verbose) println("    표본 끝점 + 0.01: 여유 − 기준 ${f4(s1)} · 인서트 겹침 ${f4(ins1)}")
    }
}

/** 두 보정선의 이음: 끝이 붙어 있으면 그 점(반올림 2e-3 까지는 가운데), 아니면 늘인 교점 가운데 두 끝 가운데에 가까운 것 */
private fun emJoin(a: EmEl, b: EmEl): Q {
    val mid = (a.b + b.a) * 0.5
    if ((a.b - b.a).len <= 2e-3) return mid
    val cs = mutableListOf<Q>()
    fun lineLine(p: Q, d: Q, p2: Q, d2: Q) {
        val den = d cross d2
        if (abs(den) < 1e-12) return
        cs += p + d * (((p2 - p) cross d2) / den)
    }
    fun lineCircle(p: Q, d: Q, c: Q, rr: Double) {
        val u = d.unit()
        val w = p - c
        val bq = w dot u
        val disc = bq * bq - ((w dot w) - rr * rr)
        if (disc < 0) return
        val s = sqrt(disc)
        cs += p + u * (-bq - s)
        cs += p + u * (-bq + s)
    }
    fun circleCircle(c1: Q, r1: Double, c2: Q, r2: Double) {
        val dd = (c2 - c1).len
        if (dd < 1e-12 || dd > r1 + r2 || dd < abs(r1 - r2)) return
        val aa = (r1 * r1 - r2 * r2 + dd * dd) / (2 * dd)
        val h = sqrt(max(0.0, r1 * r1 - aa * aa))
        val u = (c2 - c1) * (1 / dd)
        val m = c1 + u * aa
        cs += m + u.ccw() * h
        cs += m - u.ccw() * h
    }
    when {
        a is EmLn && b is EmLn -> lineLine(a.a, a.b - a.a, b.a, b.b - b.a)
        a is EmLn && b is EmAc -> lineCircle(a.a, a.b - a.a, b.c, b.rad)
        a is EmAc && b is EmLn -> lineCircle(b.a, b.b - b.a, a.c, a.rad)
        a is EmAc && b is EmAc -> circleCircle(a.c, a.rad, b.c, b.rad)
    }
    return cs.minByOrNull { (it - mid).len } ?: mid
}

// ====================================================================== 사례 돌리기 · 무작위 입력

private fun emRun(log: EmLog, name: String, calc: RoundResult, drawnCalc: RoundResult, face: Boolean, bar: BoringBar, r: Double, safe: Double, verbose: Boolean = false) {
    val sol = calc.solution ?: return log.count("건너뜀: 계산 멈춤 ${calc.warnings}")
    if (HolderCheck.problems(bar, r).isNotEmpty()) return log.count("건너뜀: 바 값 틀림")
    val plan = HolderStop.plan(sol, drawnCalc.solution, r, face, bar, safe)
    if (plan.result.skip != null) return log.count("건너뜀: ${plan.result.skip}")
    log.cases++
    EmCase("$name + $bar · 안전 여유 $safe", sol, drawnCalc.solution, r, face, bar, safe, plan, log, verbose).verify()
}

/** 무작위 바: 셋에 둘은 입구 반경 [rho] 에 맞춘 것(경로 가운데에서 끝점이 생기게), 하나는 넓은 분포 */
private fun emBar(rnd: Random, r: Double, rho0: Double): BoringBar {
    val rho = max(1.0, rho0)
    val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
    val eps = shape.epsDeg
    val fitted = rnd.nextInt(3) != 0
    val kr = r3(rnd.nextDouble(90.0, min(if (fitted) 110.0 else 117.5, 179.0 - eps)))
    val dia = r3(if (fitted) max(1.0, rnd.nextDouble(0.3, 0.9) * 2 * rho) else rnd.nextDouble(6.0, 32.0))
    val fMin = dia / 2 + r * (1 - cos(kr * PI / 180))
    val f = r3(if (fitted) rnd.nextDouble(fMin + 0.02, max(fMin + 0.07, 2 * rho - dia / 2 - rnd.nextDouble(0.0, 1.0))) else rnd.nextDouble(fMin + 0.05, dia / 2 + 6))
    val eMin = r / tan(eps * PI / 360)
    val edge = r3(rnd.nextDouble(eMin + 0.3, max(eMin + 0.6, min(16.0, 1.5 * rho))))
    var b = BoringBar(dia, f, shape, edge, kr)
    if (rnd.nextBoolean() && HolderCheck.problems(b, r).isEmpty()) b = b.copy(overhang = r3(HolderCheck.overhangMin(b, r) + rnd.nextDouble(0.5, 40.0)))
    if (rnd.nextBoolean()) b = b.copy(dMin = r3(rnd.nextDouble(b.dia, 2 * b.f + 4)))
    return b
}

/**
 * 3점호용 바: 경로가 지나는 가장 좁은 반경 ρmin(도면 표본) 안에 진입점 자세가 들어가게 — D < ρmin, f 는 바가 그 반경 안에 드는 범위
 * (진입점 인선 반경 tipX 에서 tipX + D/2 − ρmin < f < tipX − D/2 + ρmin). 경로 가운데에서 끝점이 생기게
 */
private fun emArcBar(rnd: Random, r: Double, sol: RoundSolution): BoringBar {
    val d = emEl(sol.drawing.segs.first())
    val rhoMin = max(0.5, (0..60).minOf { d.at(it / 60.0).x })
    val tipX = sol.entry.x
    val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
    val kr = r3(rnd.nextDouble(90.0, min(110.0, 179.0 - shape.epsDeg)))
    val dia = r3(max(0.5, rnd.nextDouble(0.3, 0.95) * rhoMin))
    val fMin = dia / 2 + r * (1 - cos(kr * PI / 180))
    val lo = max(fMin + 0.02, tipX + dia / 2 - rhoMin + 0.05)
    val hi = tipX - dia / 2 + rhoMin - 0.05
    if (lo >= hi) return emBar(rnd, r, rhoMin)
    val eMin = r / tan(shape.epsDeg * PI / 360)
    val edge = r3(rnd.nextDouble(eMin + 0.3, max(eMin + 0.6, min(16.0, 1.5 * rhoMin))))
    var b = BoringBar(dia, r3(rnd.nextDouble(lo, hi)), shape, edge, kr)
    if (rnd.nextInt(3) == 0 && HolderCheck.problems(b, r).isEmpty()) b = b.copy(overhang = r3(HolderCheck.overhangMin(b, r) + rnd.nextDouble(0.5, 40.0)))
    if (rnd.nextInt(3) == 0) b = b.copy(dMin = r3(rnd.nextDouble(b.dia, max(b.dia + 0.01, 2 * rhoMin))))
    return b
}

/** 무작위 3점호: 위 반원(3°~177°)이나 아래 반원 위 원호, x ≥ 0.5. 정방향 = Z 가 준다 */
private fun emArc(rnd: Random, reverse: Boolean, r: Double, inner: Boolean): Arc3Input {
    val upper = rnd.nextBoolean()
    val bigR = r3(rnd.nextDouble(0.5, 30.0))
    val (lo0, hi0) = if (upper) 3.0 to 177.0 else 183.0 to 357.0
    var a: Double
    var b: Double
    do {
        a = rnd.nextDouble(lo0, hi0)
        b = rnd.nextDouble(lo0, hi0)
    } while (abs(a - b) < 10)
    val lo = min(a, b)
    val hi = max(a, b)
    val ccw = if (upper) !reverse else reverse
    val th = listOf(if (ccw) lo else hi, rnd.nextDouble(lo + 2, hi - 2), if (ccw) hi else lo).map { it * PI / 180 }
    val minSin = (0..90).minOf { sin((lo + (hi - lo) * it / 90) * PI / 180) }
    val cz = r3(rnd.nextDouble(-20.0, 5.0))
    val cx = r3(rnd.nextDouble(0.5 - bigR * minSin, 30.5 - bigR * minSin))
    val p = th.map { r3(cz + bigR * cos(it)) to r3(2 * (cx + bigR * sin(it))) }
    return Arc3Input(
        inner = inner, noseR = r, x1 = p[0].second, z1 = p[0].first, x2 = p[1].second, z2 = p[1].first, x3 = p[2].second, z3 = p[2].first,
        zClear = r3(rnd.nextDouble(0.0, 3.0)),
    )
}

// ====================================================================== ④ 라운드 기계 보정 G00

/**
 * ④ 라운드 기계 보정 진입(회사 꼴 — 계획 단계 4): `G41`/`G42` 혼자 줄 / `G0X…Z20.` / (X 맞춤) / 진입점 / `G1 첫 점 F.1`. 이 파일의 제어기 모형
 * (Z20. 자리 노즈 = 첫 블록 처음의 법선, 꺾임 = 이웃 보정선의 교점 — 첫 깎는 줄 끝은 첫 형상 요소의 보정선과)으로: 진입 노즈가 늘인 윤곽에서 r 이상
 * (내경은 맞은편 벽도), 진입 꺾임이 바깥 90° 이하 · 노즈가 블록과 거꾸로 가지 않음, 첫 깎는 줄 노즈 이동 ≥ 0.05, 첫 형상 블록 처음 노즈 = 직접 보정 첫 노즈,
 * 글이 팁 번호와 상관없음([std] = 팁을 비운 풀이와 같은 글), 형상 줄이 노즈R 0 풀이와 같음(끝 요소 직선은 축 너머로 늘일 수 있어 뺀다)
 */
private fun emG00(log: EmLog, name: String, m: RoundResult, std: RoundResult, d: RoundResult, z0: RoundResult, r: Double, inner: Boolean, reverseArc: Boolean = false) {
    val ms = m.solution ?: return log.count("기계 보정 풀이 없음 ${m.warnings}${if (d.solution != null) " (직접 보정은 풀림)" else ""}")
    val ds = d.solution
    log.cases++
    // 직접 보정이 멈추는 입력(오목 R < r · 노즈가 축을 넘음)은 기계 보정 글만 나온다 — 그 사례의 진입 문제는 정보로.
    // 역방향 3점호의 진입은 회사 꼴 틀이 아직 다루지 않는 꼴(앞 단계 남은 일 — 앞으로 부푼 원호 · +Z 진입)이라 진입 꺾임 · 노즈 거꾸로 · 첫 깎는 줄 이동은 따로 센다
    fun bad(cat: String, msg: String) = if (ds != null) log.bad(cat, msg) else log.info("$cat (직접 보정 ${d.warnings} — 기계 보정만)", msg)
    fun badEntry(cat: String, msg: String) = if (reverseArc) log.info("$cat — 역방향 3점호(알려진 한계)", msg) else bad(cat, msg)
    val left = ms.side == Side.G41
    val drawing = ms.drawing.segs.map(::emEl)
    val first = drawing.first()
    val last = drawing.last()
    // 공기 이동을 잴 소재(라운드 기계 보정 정의 — 계획 단계 4): 도면 + 직선이거나 축에서 시작 · 끝나는 끝 요소의 접선 반직선(3점호 원호의 접선은 늘이지 않는다 —
    // 그 너머 윤곽을 모른다), 내경은 축 거울(맞은편 벽). 진입은 Z20. 공기에서 내려오므로 이 경계에서 r 이상 떨어져 지나면 소재에 들지 않는다
    val mat0 = drawing + listOfNotNull(
        if (first is EmLn || first.a.x <= 1e-9) EmLn(first.a - first.tan(0.0) * 1000.0, first.a) else null,
        if (last is EmLn || last.b.x <= 1e-9) EmLn(last.b, last.b + last.tan(1.0) * 1000.0) else null,
    )
    val mat = if (inner) mat0 + mat0.map { it.mirror() } else mat0
    val bl = emParse(ms.nc)
    if (ms.nc.first() != "${ms.side}") log.bad("④ 첫 줄이 보정 혼자 줄이 아님", "$name: ${ms.nc.take(3)}")
    val cmds = ms.approach.map(::q) + q(ms.program.segs.first().start)
    val firstCut = ms.nc.indexOfFirst { 'F' in it }
    val lines = (2..firstCut).filter { bl[it].el != null }
    if (lines.size != cmds.size - 1) return log.bad("④ 진입 줄 수", "$name: 진입 줄 ${lines.size} · 명령점 ${cmds.size} · ${ms.nc.take(6)}")
    for ((i, ln) in (listOf(1) + lines).withIndex()) if ((bl[ln].end - cmds[i]).len > 8e-4) log.bad("④ 진입 줄 글", "$name: ${ms.nc[ln]} ≠ ${cmds[i]}")
    val els = cmds.zipWithNext { a, b -> EmLn(a, b) as EmEl } + first
    val offs = els.map { emOffset(it, r, left) ?: return log.count("④ 첫 요소가 오목 R ≤ r(모형 밖)") }
    val noses = listOf(offs.first().a) + (0 until offs.size - 1).map { emJoin(offs[it], offs[it + 1]) }
    val s = if (left) 1.0 else -1.0
    for (i in 0 until els.size - 1) {
        val u1 = els[i].tan(1.0)
        val u2 = els[i + 1].tan(0.0)
        if (s * (u1 cross u2) < -1e-9 && (u1 dot u2) < -1e-9) badEntry("④ 진입 꺾임이 바깥 90° 넘음", "$name: ${i + 2}번째 진입 줄 끝 · ${ms.nc.take(6)}")
    }
    for (i in 0 until cmds.size - 1) if (((noses[i + 1] - noses[i]) dot (cmds[i + 1] - cmds[i])) <= 0) badEntry("④ 진입 노즈가 블록과 거꾸로", "$name: ${ms.nc[lines[i]]} · ${ms.nc.take(6)}")
    val g1 = (noses.last() - noses[noses.size - 2]) dot (cmds.last() - cmds[cmds.size - 2]).unit()
    log.note("④ 첫 깎는 줄 노즈 이동(최소)", g1)
    if (g1 < 0.05 - 1e-6) badEntry("④ 첫 깎는 줄 노즈 이동 < 0.05", "$name: ${f4(g1)} · ${ms.nc.take(6)}")
    // 진입 노즈 길 ~ 소재 경계 − r
    var worst = INF
    for (i in 0 until cmds.size - 1) {
        val leg = EmLn(noses[i], noses[i + 1])
        val n = max(1, ceil(leg.length / 0.01).toInt())
        for (k in 0..n) worst = min(worst, mat.minOf { it.dist(leg.at(k.toDouble() / n)) } - r)
    }
    log.note("④ 진입 노즈 ~ 소재 경계 − r(최소)", worst)
    if (worst < -1e-6) {
        // 노즈에 비해 좁은 구멍(가장 좁은 반경 < (1 + √2)·r + 0.02)은 회사 꼴 틀이 맞출 곳이 없는 알려진 한계 — 정보로
        val narrow = inner && drawing.minOf { e -> (0..16).minOf { e.at(it / 16.0).x } } < (1 + sqrt(2.0)) * r + 0.02
        if (narrow) log.info("④ 진입 노즈가 소재에 — 노즈에 비해 좁은 구멍(알려진 한계)", "$name: ${f4(worst)} · ${ms.nc.take(6)}")
        else badEntry("④ 진입 노즈가 소재에", "$name: ${f4(worst)} · ${ms.nc.take(6)}")
    }
    // 첫 형상 블록 처음 노즈 = 직접 보정 첫 노즈
    if (ds != null) {
        val dn = q(ds.program.segs.first().start) - emTip(ds.tip, r)
        log.note("④ 형상 시작 노즈 − 직접 보정 첫 노즈(최대)", (noses.last() - dn).len)
        if ((noses.last() - dn).len > 1e-6) log.bad("④ 형상 시작 노즈 ≠ 직접 보정", "$name: 기계 ${noses.last()} · 직접 $dn · ${ms.nc.take(6)}")
    } else {
        log.count("④ 직접 보정 풀이 없음 ${d.warnings}")
    }
    // 글이 팁 번호와 상관없다(보정 줄이 이동 없는 혼자 줄)
    val stdNc = std.solution?.nc
    log.ratio("④ 글이 팁 번호와 상관없음(팁을 비운 풀이와 같은 글)", stdNc == ms.nc)
    if (stdNc != ms.nc) log.bad("④ 글이 팁 번호에 따라 바뀜", "$name: ${ms.nc} / ${stdNc}")
    // 형상 줄이 노즈R 0 풀이와 같다 — 옛 꼴(줄마다 G)로 견준다. 끝 요소가 직선이면 축 너머로 늘일 수 있어 뺀다.
    // 짧은 막힌 끝 직선을 빼며 그 앞 원호(모서리 R · 주 원호)도 함께 뺄 수 있다(calc-design 라운드 '기계 보정 프로그램' 2026-11-04) — 뺀 것은 끝 직선 앞 원호뿐이어야 한다
    val zs = z0.solution ?: return log.count("④ 노즈R 0 풀이 없음")
    val kept = (ms.ncRefs.filter { it.element in drawing.indices }.maxOfOrNull { it.element } ?: -1) + 1
    if (last is EmLn && kept < drawing.size - 1) {
        log.count("④ 짧은 막힌 끝과 함께 원호를 뺌")
        if (ms.drawing.segs.subList(kept, drawing.size - 1).any { it !is com.bmt.nosecalc.calc.Arc }) log.bad("④ 짧은 막힌 끝과 함께 직선을 뺌", "$name: ${ms.nc}")
    }
    val upTo = min(if (last is EmLn) drawing.size - 1 else drawing.size, kept)
    fun shape(sol: RoundSolution) = sol.nc.legacy.filterIndexed { i, _ -> sol.ncRefs[i].element in 0 until upTo }
    val mShape = shape(ms)
    val zShape = shape(zs)
    if (mShape != zShape) log.bad("④ 형상 줄이 노즈R 0 풀이와 다름", "$name: $mShape / $zShape")
}

// ====================================================================== 기록

private class EmLog(val title: String) {
    var cases = 0

    /** 깎는 경로(잘린 NC 또는 끝까지 안전한 원래 NC)를 하나라도 따라간 사례 */
    var followed = 0
    private val counts = linkedMapOf<String, Int>()
    private val bads = linkedMapOf<String, MutableList<String>>()
    private val badN = linkedMapOf<String, Int>()
    private val infos = linkedMapOf<String, MutableList<String>>()
    private val infoN = linkedMapOf<String, Int>()
    private val notes = linkedMapOf<String, DoubleArray>()
    private val ratios = linkedMapOf<String, IntArray>()
    val badCount get() = badN.values.sum()

    fun count(k: String, n: Int = 1) {
        counts[k] = (counts[k] ?: 0) + n
    }

    fun bad(cat: String, msg: String) {
        badN[cat] = (badN[cat] ?: 0) + 1
        bads.getOrPut(cat) { mutableListOf() }.let { if (it.size < 4) it += msg }
    }

    fun info(cat: String, msg: String) {
        infoN[cat] = (infoN[cat] ?: 0) + 1
        infos.getOrPut(cat) { mutableListOf() }.let { if (it.size < 3) it += msg }
    }

    fun note(k: String, v: Double) {
        if (!v.isFinite()) return
        val a = notes.getOrPut(k) { doubleArrayOf(INF, -INF, 0.0, 0.0) }
        a[0] = min(a[0], v)
        a[1] = max(a[1], v)
        a[2] += v
        a[3] += 1.0
    }


    fun ratio(k: String, ok: Boolean) {
        val a = ratios.getOrPut(k) { IntArray(2) }
        if (ok) a[0]++
        a[1]++
    }

    fun finish(started: TimeSource.Monotonic.ValueTimeMark) {
        val sb = StringBuilder()
        sb.appendLine("==== EuclidMarginVerifyTest: $title — 사례 $cases (깎는 경로를 따라간 사례 $followed), ${started.elapsedNow()} ====")
        for ((k, v) in counts) sb.appendLine("  [수] $k: $v")
        for ((k, a) in ratios) sb.appendLine("  [비율] $k: ${a[0]}/${a[1]} = ${if (a[1] == 0) "-" else f3(100.0 * a[0] / a[1])} %")
        for ((k, a) in notes) sb.appendLine("  [값] $k: 최소 ${f4(a[0])} · 최대 ${f4(a[1])} · 평균 ${f4(a[2] / a[3])} (${a[3].toInt()})")
        for ((k, l) in infos) {
            sb.appendLine("  [정보] $k (${infoN[k]})")
            for (m in l) sb.appendLine("      - $m")
        }
        for ((k, l) in bads) {
            sb.appendLine("  [문제] $k (${badN[k]})")
            for (m in l) sb.appendLine("      - $m")
        }
        sb.appendLine("  문제 합계 $badCount")
        println(sb)
    }
}

private fun f3(v: Double): String = if (v.isFinite()) ((v * 1000).roundToLong() / 1000.0).toString() else v.toString()
private fun f4(v: Double): String = if (v.isFinite()) ((v * 10000).roundToLong() / 10000.0).toString() else v.toString()
private fun r3(v: Double) = (v * 1000).roundToLong() / 1000.0
