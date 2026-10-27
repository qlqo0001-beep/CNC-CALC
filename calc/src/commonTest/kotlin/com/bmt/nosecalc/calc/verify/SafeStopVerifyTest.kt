package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderCut
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderPlan
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.StopWarning
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
import kotlin.time.TimeSource

/**
 * 독립 검증 ⑤ — 보링바 안전 끝점 · 잘린 NC(HolderStop)를 구현 식 없이 본다(10-27 검증 단계).
 *
 * 표본 모형(이 파일에서 따로 짬 — HolderCheck · Bore · BarShape · StopJob · readNc 를 쓰지 않는다):
 * - 인서트: 꼭짓점 0 · 날 0°/ε 마름모(T 는 삼각형)를 κr − 180° 돌리고, 두 날에 접하는 노즈 원호(0.25° 꺾은선)까지 포함한 다각형.
 *   바 띠(x_c = r − f, 지름 D) 밖으로 나온 곳만 인서트, 띠는 바. 바 앞 = 주절삭날 연장선(뒤보링은 인서트 끝에서 수직), 뒤 = 공구대 면
 * - 소재 ρ(z): 도면 + 입구 면 · 반직선 · 막힌 바닥을 따로 만들고 0.003 mm 격자 + 꺾임점에서 잰다(면 ±0.001 은 소재가 적은 쪽)
 * - 여유 = 반경 방향(설계 정의) — 깎는 쪽 ρ − x, 맞은편 ρ + x, 공구대 Z, 최소 가공경 = 헤드 구간의 가장 좁은 ρ − DMIN/2
 * - NC: 이 파일의 글 읽기. 직접 보정은 인선 − 팁 벡터, 기계 보정은 블록마다 도면을 공구 쪽으로 r 민 보정선을 교점으로 잇고
 *   시작 블록(G41/G42 를 사람이 넣는 둘째 줄) 끝 = 다음 블록 시작 법선, G40 앞 블록 끝 = 그 블록 끝 법선, G40 블록은 그 자리에서 끝점(인선)으로 곧게
 *
 * | S1 | 잘린 NC 두 가지를 0.01 mm 마다: 형상(G00 점 · 진입 · 깎기) 여유 ≥ 안전 여유 − 1e-3(인서트 깎는 쪽은 ≥ −1e-3), 후퇴 ≥ −1e-3 |
 * | S2 | 정확한 경로에서 끝점 + 0.01 mm 는 표본 기준 아래(80 % 이상) · 표본의 첫 위반 곳과 끝점 차이 |
 * | S3 | 간섭 목록: 처음 닿는 곳(여유 < −0.005)을 표본으로 찾아 0.01 mm 안, 줄이 그 좌표를 지남(두 프로그램), 기계 좌표가 같은 노즈 자세 |
 * | S5 | 마지막 형상 점(쓴 글)이 경로를 따라 안전 끝점을 넘지 않음(두 프로그램) |
 */
class SafeStopVerifyTest {
    private val log = SLog()

    @Test
    fun handCases() {
        val started = TimeSource.Monotonic.markNow()
        val r = 0.4
        val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
        val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0)
        val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)
        val socket = RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, legLen = 5.0)
        val back = Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = -20.0, x2 = 24.0, z2 = -19.0, x3 = 20.0, z3 = -15.0, zClear = 1.0)
        val mouth = Arc3Input(inner = true, noseR = r, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0, zClear = 1.0)
        fun type(name: String, i: RoundInput, bar: BoringBar) {
            val inp = i.copy(inner = true, noseR = r, zClear = 1.0)
            for (safe in listOf(0.0, 0.2, 0.5, 1.0)) runCase(name, RoundCalc.calc(inp), RoundCalc.calc(inp.copy(noseR = 0.0)), true, bar, r, safe, dump = name == "A" && safe == 0.5)
        }
        fun arc(name: String, a: Arc3Input, bar: BoringBar) {
            for (safe in listOf(0.0, 0.2, 0.5, 1.0)) runCase(name, RoundCalc.arc3(a), RoundCalc.arc3(a.copy(noseR = 0.0)), false, bar, a.noseR, safe)
        }
        type("A", dome, barA)
        type("B", dome.copy(d1 = 18.0), barA)
        type("C", step, barA)
        type("C 관통", step.copy(through = true), barA)
        type("C38", RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 38.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0), barA)
        type("D", dome, barA.copy(overhang = 12.0))
        type("E", dome, barA.copy(dMin = 20.0))
        type("F", socket, BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0))
        arc("뒤보링 DMIN", back, BoringBar(12.0, 12.0, InsertShape.C, 9.67, 95.0, dMin = 22.0))
        arc("뒤보링 f 11", back, BoringBar(12.0, 11.0, InsertShape.C, 9.67, 95.0))
        arc("뒤보링 f 11.5", back, BoringBar(12.0, 11.5, InsertShape.C, 9.67, 95.0))
        arc("입구 R", mouth, BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, overhang = 12.0, dMin = 20.0))
        // 화면 프리셋 바(A16R-SCLCR 09 · DMIN 20)와 진입 이동에서 닿는 바(간섭 목록의 진입 좌표)
        type("A + A16", dome, BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, dMin = 20.0))
        type("A 큰 바", dome, BoringBar(19.0, 10.0, InsertShape.C, 9.67, 95.0))
        type("C 큰 바", step, BoringBar(28.0, 15.0, InsertShape.C, 9.67, 95.0, dMin = 33.0))
        log.finish("손 계산 예", started)
    }

    @Test
    fun randomTypes() {
        val started = TimeSource.Monotonic.markNow()
        val rnd = Random(20261027)
        val g = GeometricVerifyTest()
        repeat(150) { n ->
            val r = RS[rnd.nextInt(RS.size)]
            val inp = g.randomRound(rnd.nextInt(2, 7), rnd, inner = true).input.copy(noseR = r)
            val calc = RoundCalc.calc(inp)
            val sol = calc.solution
            if (sol == null) {
                log.skip("계산 멈춤 ${calc.warnings}")
                return@repeat
            }
            val bar = if (n % 2 == 0) wideBar(rnd, r) else fittedBar(rnd, r, sol)
            val safe2 = if (n % 2 == 0) 0.0 else sr3(rnd.nextDouble(0.05, 1.5))
            for (safe in listOf(0.5, safe2)) runCase("TYPE ${inp.type} $inp", calc, RoundCalc.calc(inp.copy(noseR = 0.0)), true, bar, r, safe)
        }
        log.finish("무작위 내경 TYPE", started)
    }

    @Test
    fun randomArcs() {
        val started = TimeSource.Monotonic.markNow()
        val rnd = Random(1027)
        for (reverse in listOf(false, true)) repeat(if (reverse) 170 else 150) { n ->
            val r = RS[rnd.nextInt(RS.size)]
            val a = randomArc(rnd, reverse, r)
            val calc = RoundCalc.arc3(a)
            val sol = calc.solution
            if (sol == null) {
                log.skip("계산 멈춤 ${calc.warnings}")
                return@repeat
            }
            val bar = if (n % 3 == 0) wideBar(rnd, r) else fittedBar(rnd, r, sol)
            val safe2 = if (n % 2 == 0) 0.0 else sr3(rnd.nextDouble(0.05, 1.5))
            for (safe in listOf(0.5, safe2)) runCase("${if (reverse) "뒤보링" else "3점호"} $a", calc, RoundCalc.arc3(a.copy(noseR = 0.0)), false, bar, r, safe)
        }
        log.finish("무작위 3점호 · 뒤보링", started)
    }

    // ------------------------------------------------------------------ 한 사례

    private fun runCase(name: String, calc: RoundResult, drawnCalc: RoundResult, face: Boolean, bar: BoringBar, r: Double, safe: Double, dump: Boolean = false) {
        val sol = calc.solution ?: return log.skip("계산 멈춤 ${calc.warnings}")
        val drawn = drawnCalc.solution
        if (HolderCheck.problems(bar, r).isNotEmpty()) return log.skip("바 값 틀림")
        val plan = HolderStop.plan(sol, drawn, r, face, bar, safe)
        if (plan.result.skip != null) return log.skip("${plan.result.skip}")
        val id = "$name + $bar · 안전 여유 $safe"
        log.cases++
        val c = SCase(sol, drawn, r, face, bar, safe, plan, id, log)
        c.verify()
        if (dump) c.dump()
    }

    // ------------------------------------------------------------------ 무작위 입력

    /** HolderVerifyTest 와 같은 넓은 분포: D 6~32, f fMin + 0.05 ~ D/2 + 6 */
    private fun wideBar(rnd: Random, r: Double): BoringBar {
        val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
        val dia = sr3(rnd.nextDouble(6.0, 32.0))
        val kr = sr3(rnd.nextDouble(90.0, min(117.5, 179 - shape.epsDeg)))
        val f = sr3(rnd.nextDouble(fMinS(dia, kr, r) + 0.05, dia / 2 + 6))
        return extras(rnd, BoringBar(dia, f, shape, sr3(rnd.nextDouble(r / tan(shape.epsDeg * PI / 360) + 0.3, 16.0)), kr), r)
    }

    /** 구멍에 맞춘 바: 입구 반경 ρ 에서 D = 0.3~0.9 × 2ρ, f 는 바가 입구에 들어가는 쪽 — 경로 가운데에서 끝점이 생기게 */
    private fun fittedBar(rnd: Random, r: Double, sol: RoundSolution): BoringBar {
        val segs = sol.drawing.segs
        val reverse = segs.last().end.z > segs.first().start.z
        val rho = max(1.0, if (reverse) segs.last().end.x else segs.first().start.x)
        val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
        val dia = sr3(max(1.0, rnd.nextDouble(0.3, 0.9) * 2 * rho))
        val kr = sr3(rnd.nextDouble(90.0, min(110.0, 179 - shape.epsDeg)))
        val lo = fMinS(dia, kr, r) + 0.02
        val hi = max(lo + 0.05, 2 * rho - dia / 2 - rnd.nextDouble(0.0, 1.0))
        val edge = sr3(rnd.nextDouble(r / tan(shape.epsDeg * PI / 360) + 0.3, max(r / tan(shape.epsDeg * PI / 360) + 0.6, min(16.0, 1.5 * rho))))
        return extras(rnd, BoringBar(dia, sr3(rnd.nextDouble(lo, hi)), shape, edge, kr), r)
    }

    private fun extras(rnd: Random, b0: BoringBar, r: Double): BoringBar {
        var b = b0
        if (rnd.nextBoolean() && HolderCheck.problems(b, r).isEmpty()) b = b.copy(overhang = sr3(HolderCheck.overhangMin(b, r) + rnd.nextDouble(0.5, 40.0)))
        if (rnd.nextBoolean()) b = b.copy(dMin = sr3(rnd.nextDouble(b.dia, 2 * b.f + 4)))
        return b
    }

    private fun fMinS(dia: Double, kr: Double, r: Double) = dia / 2 + r * (1 - cos(kr * PI / 180))

    /** 위 반원은 반시계로 가면 Z 가 준다(정방향) */
    private fun randomArc(rnd: Random, reverse: Boolean, r: Double): Arc3Input {
        val upper = rnd.nextBoolean()
        val bigR = sr3(rnd.nextDouble(0.5, 30.0))
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
        val cz = sr3(rnd.nextDouble(-20.0, 5.0))
        val cx = sr3(rnd.nextDouble(0.5 - bigR * minSin, 30.5 - bigR * minSin))
        val p = th.map { sr3(cz + bigR * cos(it)) to sr3(2 * (cx + bigR * sin(it))) }
        return Arc3Input(
            inner = true, noseR = r, x1 = p[0].second, z1 = p[0].first, x2 = p[1].second, z2 = p[1].first, x3 = p[2].second, z3 = p[2].first,
            zClear = sr3(rnd.nextDouble(0.0, 3.0)),
        )
    }

    private companion object {
        val RS = listOf(0.0, 0.2, 0.4, 0.8)
    }
}

// ====================================================================== 한 사례 검사

private const val GH = 0.003 // ρ 격자(mm)
private const val NEAR_FACE = 1e-3 // 면 ± 이만큼은 소재가 적은 쪽(쓴 글 0.001 반올림)
private const val PSTEP = 0.01 // 경로 표본(mm)
private const val TOL = 1e-3 // 표본 기준 여유(mm)
private const val TOUCH = 0.005 // 닿음 = 여유 < −0.005 (설계 정의)
private val INFS = Double.POSITIVE_INFINITY

private class SCase(
    val sol: RoundSolution,
    val drawn: RoundSolution?,
    val r: Double,
    face: Boolean,
    val bar: BoringBar,
    val safe: Double,
    val plan: HolderPlan,
    val id: String,
    val log: SLog,
) {
    val mat = SMat(sol.drawing.segs.map(::elOf), face)
    val tool = STool(bar, r, mat.reverse)
    val tv = tipVec(sol.tip, r)

    /** G40 경로(가상 인선): 진입 이동(−1) + 요소(길이 0 은 뺀다) */
    val path: List<Pair<Int, El>> = (listOf(-1 to (Ln(vOfS(sol.entry), vOfS(sol.program.segs.first().start)) as El)) +
        sol.program.segs.withIndex().map { it.index to elOf(it.value) }).filter { it.second.length > 1e-9 }
    val cum = DoubleArray(path.size + 1).also { c -> for (i in path.indices) c[i + 1] = c[i] + path[i].second.length }
    val total = cum[path.size]

    fun gaps(nose: V): SG = sgOf(mat, tool, nose)

    fun pos(k: Int, t: Double): Double {
        val i = path.indexOfFirst { it.first == k }
        if (i >= 0) return cum[i] + t * path[i].second.length
        val j = path.indexOfFirst { it.first > k }
        return if (j < 0) total else cum[j]
    }

    fun tipAt(s: Double): V {
        val i = (path.indices).firstOrNull { s <= cum[it + 1] } ?: (path.size - 1)
        val e = path[i].second
        return e.at(((s - cum[i]) / e.length).coerceIn(0.0, 1.0))
    }

    fun noseAt(s: Double) = tipAt(s) - tv

    /** 점 p(인선)의 경로 길이: 요소 k(앞뒤 하나씩도) 위 투영 가운데 가장 가까운 것 */
    fun project(k: Int, p: V): Double? {
        var best: Pair<Double, Double>? = null
        for ((i, pe) in path.withIndex()) {
            if (abs(pe.first - k) > 1) continue
            val e = pe.second
            val t = paramS(e, p)
            val q = if (t in 0.0..1.0) e.at(t) else extend(e, t)
            val d = (q - p).len
            if (best == null || d < best.first) best = d to cum[i] + t * e.length
        }
        return best?.takeIf { it.first < 2e-3 }?.second
    }

    fun verify() {
        val end = plan.end
        val sEnd = end?.let { pos(it.element, it.t) }
        // -------- S1: 잘린 NC(또는 끝까지 안전하면 원래 NC)
        val d = plan.direct
        val m = plan.machine
        // 기계 보정 프로그램이 제 진입(G00 점 · 보정 시작 블록)에서 멈춘다고 하면(10-28 계산기 수정) 그 주장을 이 파일의 모형으로 확인 — 그 밖에는 아래 그대로
        val machineEntry = StopWarning.MACHINE_ENTRY in plan.warnings
        if (machineEntry) machineEntryClaim()
        if (StopWarning.UNSAFE_AT_ENTRY in plan.warnings) {
            log.count("UNSAFE_AT_ENTRY")
            val s0 = gaps(vOfS(sol.entry) - tv).slack(safe)
            if (s0 > TOL) log.bad(id, "S1 진입", "UNSAFE_AT_ENTRY 인데 표본 진입 자세 여유가 기준보다 ${sf(s0)} 넉넉")
            if (m != null && drawn != null) {
                checkCut(m, drawn.nc, "기계 보정")
                followMachine(m.nc, m, "기계 보정")
            }
        } else if (end == null) {
            log.count("끝까지 안전")
            followDirect(sol.nc, Int.MAX_VALUE, "원래 직접 보정")
            if (drawn != null && !machineEntry) followMachine(drawn.nc, null, "원래 기계 보정")
        } else {
            log.count(if (d != null) "끊음 ${d.retract}/${m?.retract} ${plan.warnings}" else "잘린 NC 없음 ${plan.warnings}")
            if (d != null) {
                checkCut(d, sol.nc, "직접 보정")
                followDirect(d.nc, d.retractFrom, "직접 보정")
                // S5
                val last = readS(d.nc)[d.retractFrom - 1].end
                val sw = project(d.element, last)
                if (sw == null) {
                    log.bad(id, "S5", "직접 보정 마지막 점 $last 가 경로 위가 아님")
                } else {
                    log.note("직접 보정 끝 − 안전 끝점(mm)", sw - sEnd!!)
                    if (sw > sEnd + 1e-6) log.bad(id, "S5", "직접 보정 마지막 점 $last 가 안전 끝점을 ${sf(sw - sEnd)} mm 넘음 · ${d.nc}")
                }
            } else if (end.element >= 0 && StopWarning.NOTHING_CUT !in plan.warnings) {
                log.bad(id, "S1", "끝점이 요소 ${end.element} 인데 직접 보정 잘린 NC 가 없다 ${plan.warnings}")
            }
            if (m != null && drawn != null) {
                checkCut(m, drawn.nc, "기계 보정")
                followMachine(m.nc, m, "기계 보정")
            } else if (d != null && drawn != null && !machineEntry) {
                log.count("기계 보정 잘린 NC 없음(직접 보정은 있음)")
            }
            // -------- S2: 너무 일찍 멈추지 않나
            if (end.element >= 0) tooEarly(sEnd!!)
        }
        // -------- S3: 간섭 목록
        checkHits()
    }

    /** 앞 줄이 원래 글 그대로 · stop = 마지막 형상 줄 글 · G40 은 기계 보정 첫 후퇴 줄에만 */
    private fun checkCut(c: HolderCut, orig: List<String>, name: String) {
        if (c.nc.take(c.kept) != orig.take(c.kept)) log.bad(id, "S1 글", "$name 앞 ${c.kept} 줄이 원래와 다르다: ${c.nc}")
        val read = readS(c.nc)
        if ((read[c.retractFrom - 1].end - vOfS(c.stop)).len > 1e-9) log.bad(id, "S1 글", "$name stop ${c.stop} ≠ 마지막 형상 줄 ${c.nc[c.retractFrom - 1]}")
        val g40 = c.nc.indices.filter { "G40" in c.nc[it] }
        if (name == "기계 보정" && g40 != listOf(c.retractFrom)) log.bad(id, "S1 글", "기계 보정 G40 줄 $g40 ≠ 첫 후퇴 ${c.retractFrom}: ${c.nc}")
        if (name == "직접 보정" && g40.isNotEmpty()) log.bad(id, "S1 글", "직접 보정에 G40 줄: ${c.nc}")
    }

    // ------------------------------------------------------------------ 직접 보정: 인선 = 글 그대로

    private fun followDirect(nc: List<String>, retractFrom: Int, name: String) {
        val bl = readS(nc)
        val moves = mutableListOf<Mv>()
        moves += Mv(0, Ln(bl[0].end - tv, bl[0].end - tv), false)
        for (i in 1 until bl.size) bl[i].el?.let { moves += Mv(i, it.shift(V(0.0, 0.0) - tv), i >= retractFrom, bl[i].rapid) }
        sweep(moves, nc, name)
    }

    // ------------------------------------------------------------------ 기계 보정: 제어기 모형

    private fun followMachine(nc: List<String>, cut: HolderCut?, name: String) {
        val bl = readS(nc)
        val g40 = bl.indexOfFirst { it.g40 }.let { if (it < 0) bl.size else it }
        val d = drawn!!
        // 진입 이동이 없으면(노즈R 0 · Z 여유 0) 시작 블록이 첫 형상 블록이라 이 모형 밖
        if ((vOfS(d.entry) - vOfS(d.program.segs.first().start)).len < 1e-9) return log.count("기계 보정 진입 이동 없음(모형 밖)")
        if (g40 < 3) return log.bad(id, "S1 기계", "$name 보정 블록이 없다: $nc")
        val left = sol.side == Side.G41
        val blocks = (2 until g40).map { bl[it].el!! }
        if (blocks.any { it is Ac && !outward(it, left) && it.r - r <= 1e-9 }) return log.count("기계 보정 오목 R ≤ r(제어기 알람 — 모형 밖)")
        val offs = blocks.map { offsetS(it, left, r) }
        val joints = mutableListOf<V>()
        // 시작 블록 끝: 다음 블록 시작의 법선(형식 A). 교점식(안쪽 모서리)과 다르면 센다
        val start = offs.first().a
        joints += start
        val su = bl[1].el as Ln
        val suOff = offsetS(su, left, r)
        val alt = joinS(suOff, offs.first())
        if (alt != null && (alt - start).len > 2e-3) log.count("기계 보정 시작 모서리가 접하지 않음(형식 A 가정)")
        val solFirst = vOfS(sol.program.segs.first().start) - tv
        if ((start - solFirst).len > 2e-3) log.bad(id, "S1 기계", "$name 시작 블록 끝 노즈 $start ≠ G40 프로그램 첫 노즈 $solFirst")
        for (i in 0 until offs.size - 1) {
            val j = joinS(offs[i], offs[i + 1])
            if (j == null) {
                log.count("기계 보정 이음 교점 없음")
                joints += (offs[i].b + offs[i + 1].a) * 0.5
            } else {
                joints += j
            }
        }
        joints += offs.last().b
        val moves = mutableListOf<Mv>()
        val n0 = bl[0].end - tv
        moves += Mv(0, Ln(n0, n0), false, tag = "G00 점(G40 상태)")
        moves += Mv(1, Ln(n0, start), false, tag = "G41 시작 블록")
        for (i in offs.indices) {
            val o = offs[i]
            val a = joints[i]
            val b = joints[i + 1]
            val e: El = if (o is Ac) {
                val ac = Ac(a, b, o.c, o.cw)
                // 이음이 원호를 거꾸로 넘어가면(짧은 블록이 다 먹힘) 한 바퀴 가까이 돈다 — 곧은 선으로
                if (ac.sweep > (blocks[i] as Ac).sweep + PI / 2) {
                    log.count("기계 보정 짧은 원호 먹힘")
                    Ln(a, b)
                } else {
                    ac
                }
            } else {
                Ln(a, b)
            }
            moves += Mv(2 + i, e, false)
        }
        val jEnd = joints.last()
        if (g40 < bl.size) {
            val p40 = bl[g40].end
            moves += Mv(g40, Ln(jEnd, p40 - tv), true, bl[g40].rapid, "G40 취소 이동")
            for (i in g40 + 1 until bl.size) bl[i].el?.let { moves += Mv(i, it.shift(V(0.0, 0.0) - tv), true, bl[i].rapid) }
        }
        if (cut != null) {
            // S5: 마지막 블록 끝 노즈(법선 보정)가 안전 끝점을 넘지 않는다 — 기계 보정 프로그램의 끝점(진입이 다르면 직접 보정과 따로)
            val me = (plan.machineEnd ?: plan.end)!!
            val sEnd = pos(me.element, me.t)
            if ((jEnd - vOfS(cut.pose.nose)).len > 1e-6) log.bad(id, "S5", "기계 보정 끝 노즈 $jEnd ≠ 계산기 ${cut.pose.nose}")
            val sw = project(cut.element, jEnd + tv)
            if (sw == null) {
                log.bad(id, "S5", "기계 보정 끝 노즈 $jEnd 가 G40 노즈 경로 위가 아님 · $nc")
            } else {
                log.note("기계 보정 끝 − 안전 끝점(mm)", sw - sEnd)
                if (sw > sEnd + 1e-6) log.bad(id, "S5", "기계 보정 끝 노즈가 안전 끝점을 ${sf(sw - sEnd)} mm 넘음 · $nc")
            }
        }
        sweep(moves, nc, name)
    }

    /**
     * 기계 보정 진입에서 멈춘다는 주장(MACHINE_ENTRY): 이 파일의 모형(원래 도면 NC 를 읽어 G00 점 노즈 = 글의 G00 점 − 팁 벡터, 시작 블록은 그 노즈에서
     * 첫 블록 보정선 시작으로 곧게)으로 G00 점 · 시작 블록을 0.01 mm 마다 재어, 표본 여유 − 기준의 최솟값이 TOL 안이어야 한다(넉넉한데 멈추면 틀림).
     * 잘린 기계 보정 NC 는 없어야 한다
     */
    private fun machineEntryClaim() {
        val dr = drawn ?: return log.bad(id, "S1 기계 진입 주장", "도면 좌표 풀이가 없는데 MACHINE_ENTRY")
        log.count("기계 보정 진입에서 멈춤(MACHINE_ENTRY) ${plan.machineEnd?.element}")
        if (plan.machine != null) log.bad(id, "S1 기계 진입 주장", "MACHINE_ENTRY 인데 기계 보정 잘린 NC 가 있다")
        val bl = readS(dr.nc)
        val n0 = bl[0].end - tv
        // 진입 이동이 있으면 2번 줄이 시작 블록이고 3번 줄이 첫 형상 블록, 없으면(노즈R 0 · Z 여유 0) 2번 줄이 첫 형상 블록
        val noStart = (vOfS(dr.entry) - vOfS(dr.program.segs.first().start)).len < 1e-9
        val firstBlock = bl.getOrNull(if (noStart) 1 else 2)?.el
        val start = firstBlock?.let { offsetS(it, sol.side == Side.G41, r).a } ?: n0
        val mv = Ln(n0, start)
        var worst = gaps(n0).slack(safe)
        val n = max(1, ceil(mv.length / PSTEP).toInt())
        for (q in 0..n) worst = min(worst, gaps(mv.at(q.toDouble() / n)).slack(safe))
        log.note("기계 보정 진입 표본 여유 − 기준 최솟값(MACHINE_ENTRY)", worst)
        if (worst > TOL) log.bad(id, "S1 기계 진입 주장", "MACHINE_ENTRY 인데 표본으로 G00 점 · 시작 블록이 기준보다 ${sf(worst)} 넉넉 · ${dr.nc.take(3)}")
    }

    /** 움직임 하나: 노즈 중심 [e], 후퇴인가, G00 인가 */
    class Mv(val line: Int, val e: El, val retract: Boolean, val rapid: Boolean = false, val tag: String = "")

    private fun sweep(moves: List<Mv>, nc: List<String>, name: String) {
        var worstShape = INFS
        var worstBack = INFS
        var badShape = false
        var badBack = false
        var rapidDrag = 0.0
        for (mv in moves) {
            val n = max(1, ceil(mv.e.length / PSTEP).toInt())
            var dragFrom = -1.0
            for (q in 0..n) {
                val c = mv.e.at(q.toDouble() / n)
                val g = gaps(c)
                if (!mv.retract) {
                    val s = g.slack(safe)
                    if (s < worstShape) worstShape = s
                    if (s < -TOL && !badShape) {
                        badShape = true
                        val tag = if (mv.tag.isNotEmpty()) " (${mv.tag})" else ""
                        val msg = "$name ${mv.line + 1}번 줄$tag 노즈 $c: 표본 여유가 기준보다 ${sf(s)} (${g.worst(safe)}) · $nc"
                        val cat = if (mv.tag.isNotEmpty()) "S1 형상 · ${mv.tag}" else "S1 형상"
                        // 인서트 깎는 쪽(날이 깎는 벽)만 0.005 안으로 모자라면 쓴 글 반올림 — 원래 NC 도 같다(구현 결정, 문서). 정보로 센다
                        if (s == g.insCut && s >= -0.005) log.info(id, "$cat · 인서트 날 반올림 ≤ 0.005", msg)
                        else if (zHalf(c) { it.slack(safe) } >= -TOL) log.info(id, "$cat · Z 반 칸(0.0005) 안", msg)
                        else log.bad(id, cat, msg)
                    }
                } else {
                    val s = g.clear()
                    if (s < worstBack) worstBack = s
                    if (s < -TOL && !badBack) {
                        badBack = true
                        val msg = "$name ${mv.line + 1}번 줄 노즈 $c: 겹침 ${sf(s)} (${g.worstClear()}) · $nc"
                        if (s == g.insCut && s >= -0.005) log.info(id, "S1 후퇴 · 인서트 날 반올림 ≤ 0.005", msg)
                        else if (zHalf(c) { it.clear() } >= -TOL) log.info(id, "S1 후퇴 · Z 반 칸(0.0005) 안", msg)
                        else log.bad(id, "S1 후퇴", msg)
                    }
                    // G00 이 깎은 벽을 스치나(인서트 깎는 쪽 여유 < 0.005 가 0.05 mm 넘게 이어짐)
                    if (mv.rapid) {
                        val along = mv.e.length * q / n
                        if (g.insCut < 0.005) {
                            if (dragFrom < 0) dragFrom = along
                            rapidDrag = max(rapidDrag, along - dragFrom)
                        } else {
                            dragFrom = -1.0
                        }
                    }
                }
            }
        }
        log.note("$name 형상 표본 여유 − 기준 최솟값", worstShape)
        if (worstBack.isFinite()) log.note("$name 후퇴 표본 여유 최솟값", worstBack)
        if (rapidDrag > 0.05) log.info(id, "G00 스침", "$name G00 후퇴가 깎은 벽을 ${sf(rapidDrag)} mm 스침(인서트 깎는 쪽 여유 < 0.005) · $nc")
    }

    /** 쓴 글 Z 반올림 반 칸: Z ±0.0005 옮긴 자세 가운데 좋은 쪽(가파른 바 앞 · 면에서 반경 여유가 크게 뛴다 — 실제 거리는 0.0005 이하) */
    private fun zHalf(c: V, f: (SG) -> Double): Double = listOf(-5e-4, 5e-4).maxOf { f(gaps(V(c.z + it, c.x))) }

    // ------------------------------------------------------------------ S2

    private fun tooEarly(sEnd: Double) {
        val here = gaps(noseAt(sEnd)).slack(safe)
        log.note("끝점 표본 여유 − 기준", here)
        if (sEnd + PSTEP <= total) {
            val next = gaps(noseAt(sEnd + PSTEP)).slack(safe)
            log.ratio("끝점 + 0.01 mm 가 표본 기준 아래", next < 0)
            if (next >= 0) log.info(id, "S2 일찍", "끝점 + 0.01 mm 도 표본 여유가 기준 이상(+${sf(next)}), 끝점에서 ${sf(here)}")
        }
        // 표본으로 처음 기준이 깨지는 곳(0.01 mm 간격 → 이분법)
        var prev = 0.0
        var s = 0.0
        var found: Double? = null
        while (s <= total) {
            if (gaps(noseAt(s)).slack(safe) < -1e-4) {
                var a = prev
                var b = s
                repeat(40) {
                    val mid = (a + b) / 2
                    if (gaps(noseAt(mid)).slack(safe) < -1e-4) b = mid else a = mid
                }
                found = if (s == 0.0) 0.0 else a
                break
            }
            prev = s
            s += PSTEP
        }
        // 끝점 뒤 남은 경로에서 표본 인서트 깎는 쪽 최솟값(구현 기준 −1e-6 과 표본 기준 −1e-4 사이 미세 겹침인지)
        var restIns = INFS
        var restSlackNoIns = INFS
        run {
            var q = sEnd
            while (q <= total) {
                val g = gaps(noseAt(q))
                restIns = min(restIns, g.insCut)
                restSlackNoIns = min(restSlackNoIns, minOf(g.barCut, g.barOpp, g.insOpp, g.turret, g.minBore) - safe)
                q += PSTEP / 2
            }
        }
        val f = found ?: return log.info(
            id, "S2",
            "표본으로는 경로 끝까지 기준 이상인데 끝점이 ${sf(sEnd)} mm / 전체 ${sf(total)} mm · 남은 경로 표본 인서트 깎는 쪽 최소 ${restIns} · 나머지 부분 여유 − 기준 최소 ${sf(restSlackNoIns)}" +
                " — 구현: 요소 ${plan.end?.element} ${plan.end?.part} 맞은편 ${plan.end?.opposite} " +
                "여유 ${plan.end?.margin?.let(::sf)} · 표본(끝점): ${gaps(noseAt(sEnd)).let { "바 ${sf(it.barCut)}/${sf(it.barOpp)} 인서트 ${sf(it.insCut)}/${sf(it.insOpp)} 공구대 ${sf(it.turret)} DMIN ${sf(it.minBore)}" }}" +
                " · 표본(+0.01): ${gaps(noseAt(min(total, sEnd + PSTEP))).let { "바 ${sf(it.barCut)}/${sf(it.barOpp)} 인서트 ${sf(it.insCut)}/${sf(it.insOpp)} 공구대 ${sf(it.turret)} DMIN ${sf(it.minBore)}" }}",
        )
        log.note("표본 첫 위반 − 끝점(mm)", f - sEnd)
        if (f < sEnd - PSTEP) log.bad(id, "S2 늦음", "표본은 ${sf(f)} mm 에서 기준이 깨지는데 끝점은 ${sf(sEnd)} mm (${gaps(noseAt(f + 1e-4)).worst(safe)})")
    }

    // ------------------------------------------------------------------ S3

    private fun checkHits() {
        val parsedSol = readS(sol.nc)
        val parsedDrawn = drawn?.let { readS(it.nc) }
        for (h in plan.hits) {
            val hit = h.hit
            val k = hit.element
            log.count("간섭 목록 ${if (k < 0) "진입($k)" else "요소"}")
            // 줄: 그 블록이 좌표를 지난다
            fun passes(bl: List<SB>, line: Int, p: V): Double = when {
                line !in bl.indices -> INFS
                line == 0 -> (bl[0].end - p).len
                else -> bl[line].el?.dist(p) ?: (bl[line].end - p).len
            }
            val pd = vOfS(h.first.direct)
            val offD = passes(parsedSol, h.first.directLine, pd)
            if (offD > 2e-3) log.bad(id, "S3 줄", "직접 보정 ${h.first.directLine + 1}줄 ${sol.nc.getOrNull(h.first.directLine)} 이 처음 좌표 $pd 에서 ${sf(offD)}")
            val pm = h.first.machine?.let(::vOfS)
            val ml = h.first.machineLine
            if (parsedDrawn != null && pm != null && ml != null) {
                val offM = passes(parsedDrawn, ml, pm)
                if (offM > 2e-3) log.bad(id, "S3 줄", "기계 보정 ${ml + 1}줄 ${drawn!!.nc.getOrNull(ml)} 이 처음 좌표 $pm 에서 ${sf(offM)}")
            }
            if (k == -2) {
                // 기계 보정 프로그램의 G00 점 자세(도면 진입점의 인선)도 그 부분이 닿나
                if (drawn != null) {
                    val mg = gaps(vOfS(drawn.entry) - tv).part(hit.part)
                    log.count(if (mg < -TOUCH) "진입점(-2) 기계 G00 자세도 닿음" else "진입점(-2) 기계 G00 자세는 안 닿음")
                }
                continue
            }
            // 처음 닿는 곳(표본): 같은 G40 요소 위 0.01 mm 간격 → 이분법
            val pi = path.indexOfFirst { it.first == k }
            if (pi < 0) continue
            val e = path[pi].second
            fun m(t: Double) = gaps(e.at(t) - tv).part(hit.part)
            val n = max(1, ceil(e.length / PSTEP).toInt())
            fun first(level: Double): Double? {
                if (m(0.0) < level) return 0.0
                for (q in 1..n) {
                    if (m(q.toDouble() / n) < level) {
                        var a = (q - 1).toDouble() / n
                        var b = q.toDouble() / n
                        repeat(40) {
                            val mid = (a + b) / 2
                            if (m(mid) < level) b = mid else a = mid
                        }
                        return b
                    }
                }
                return null
            }
            val tv5 = first(-TOUCH)
            val implS = hit.fromT * e.length
            if (tv5 == null) {
                if (hit.depth > 0.02) log.bad(id, "S3 위치", "요소 $k ${hit.part}: 구현은 깊이 ${sf(hit.depth)} 닿는데 표본은 안 닿음")
                continue
            }
            val sv = tv5 * e.length
            log.note("간섭 처음 표본 − 구현(mm)", sv - implS)
            // 최소 가공경 구간 끝이 면(바닥 · 단) 위에 있으면 표본은 면 ±0.001 을 소재 없는 쪽으로 봐서 늦게 닿는다(표본 한계) — 정보로
            val faceEdge = hit.part == HolderPart.MIN_BORE && m(hit.fromT) > 0 && sv > implS && sv - implS < 0.1
            if (abs(sv - implS) > 0.01) {
                // 표본 오차인지: 구현 자리에서 표본 여유
                val msg = "요소 $k ${hit.part}: 처음 닿는 곳 구현 ${sf(implS)} · 표본 ${sf(sv)} mm (구현 자리 표본 여유 ${sf(m(hit.fromT))})"
                if (faceEdge) log.info(id, "S3 위치 · 면 옆 표본 한계", msg) else log.bad(id, "S3 위치", msg)
            }
            val t0 = first(0.0)
            if (t0 != null) log.note("닿기 시작(여유 0) → 목록 좌표(−0.005) 거리(mm)", implS - t0 * e.length)
            // 직접 보정 좌표 = 그 자리 인선
            val myTip = e.at(tv5)
            if ((myTip - pd).len > 0.01) {
                val msg = "요소 $k ${hit.part} 직접 보정 처음 $pd ≠ 표본 $myTip"
                if (faceEdge) log.info(id, "S3 좌표 · 면 옆 표본 한계", msg) else log.bad(id, "S3 좌표", msg)
            }
            // 기계 보정 좌표 = 같은 노즈 자세가 되는 도면 명령점
            if (pm == null || drawn == null) continue
            val nose = vOfS(hit.first.nose)
            if (k >= 0) {
                val de = elOf(drawn.program.segs[k])
                val foot = footS(de, nose)
                log.note("기계 좌표 − 노즈 발(mm)", (foot.first - pm).len)
                if ((foot.first - pm).len > 0.01) log.bad(id, "S3 기계", "요소 $k ${hit.part}: 기계 좌표 $pm ≠ 노즈의 도면 발 ${foot.first}")
                if (!foot.second) {
                    val back = foot.first + nrmS(de, foot.first, sol.side == Side.G41) * r
                    if ((back - nose).len > 0.01) log.bad(id, "S3 기계", "요소 $k ${hit.part}: 기계 좌표 $pm 에서 블록을 끝내면 노즈 $back ≠ 닿는 자세 $nose")
                }
            } else {
                // 진입 이동(−1): 도면 좌표 프로그램의 시작 블록(G41/G42) 위에서 노즈가 이 자세가 되는 곳
                val d0 = vOfS(drawn.entry)
                val d1 = vOfS(drawn.program.segs.first().start)
                val startNose = offsetS(elOf(drawn.program.segs.first()), sol.side == Side.G41, r).a
                val a = d0 - tv
                val dir = startNose - a
                val tt = (((nose - a) dot dir) / (dir dot dir)).coerceIn(0.0, 1.0)
                val onLine = (a + dir * tt - nose).len
                val mine = d0 + (d1 - d0) * tt
                log.note("진입 이동 기계 좌표 차이(mm)", (mine - pm).len)
                // 기계 보정 프로그램 자기 경로(G00 점 → 시작 블록에서 노즈가 보정 자리로 곧게)에서 그 부분이 처음 닿는 명령점(표본)
                fun mm(u: Double) = gaps(a + dir * u).part(hit.part)
                val nn = max(1, ceil(dir.len / PSTEP).toInt())
                var u5: Double? = if (mm(0.0) < -TOUCH) 0.0 else null
                if (u5 == null) {
                    for (q in 1..nn) {
                        if (mm(q.toDouble() / nn) < -TOUCH) {
                            var lo = (q - 1).toDouble() / nn
                            var hi = q.toDouble() / nn
                            repeat(40) {
                                val mid = (lo + hi) / 2
                                if (mm(mid) < -TOUCH) hi = mid else lo = mid
                            }
                            u5 = hi
                            break
                        }
                    }
                }
                val own = u5?.let { d0 + (d1 - d0) * it }
                if (own != null) log.note("진입 이동 기계 좌표 − 기계 프로그램 자기 첫 닿음(mm)", (own - pm).len)
                if (own == null || (own - pm).len > 0.01) {
                    log.bad(
                        id, "S3 기계 진입",
                        "진입 이동 ${hit.part}: 목록의 기계 좌표 $pm (직접 보정 비율 ${sf(hit.fromT)}) · 기계 보정 프로그램 자기 경로에서 처음 닿는 명령점 " +
                            (own?.let { "$it (차이 ${sf((it - pm).len)} mm)" } ?: "없음(시작 블록 동안 안 닿음)") +
                            " · 직접 보정 첫 닿음 노즈 $nose 는 기계 노즈 경로에서 ${sf(onLine)} 떨어짐(가장 가까운 명령점 $mine)",
                    )
                }
            }
        }
        // 안전 끝점의 기계 좌표: 블록을 거기서 끝내면 노즈가 끝 자세(요소 ≥ 0)
        val end = plan.end ?: return
        val am = end.at.machine ?: return
        if (end.element >= 0 && drawn != null) {
            val de = elOf(drawn.program.segs[end.element])
            val back = am.let(::vOfS) + nrmS(de, vOfS(am), sol.side == Side.G41) * r
            val dn = (back - vOfS(end.pose.nose)).len
            log.note("끝점 기계 좌표 → 노즈 차이(mm)", dn)
        }
        // 실제 거리(정보): 끝 자세에서 바와 소재 사이 가장 짧은 거리(반경이 아닌 유클리드)
        val cut = plan.direct ?: return
        val ed = euclidBar(vOfS(cut.pose.nose))
        log.note("끝 자세 바 실제 거리 − 안전 여유(mm)", ed - safe)
        if (safe > 0) {
            log.count("실제 거리 본 끊은 사례(안전 여유 > 0)")
            if (ed < safe - 0.05) log.count("  그 가운데 바 실제 거리 < 안전 여유 − 0.05")
            if (ed < safe / 2) log.count("  그 가운데 바 실제 거리 < 안전 여유 / 2")
        }
        if (ed < safe - 0.05) log.info(id, "실제 거리", "직접 보정 끝 자세에서 바와 소재의 실제 거리 ${sf(ed)} mm < 안전 여유 $safe (반경 여유 ${sf(gaps(vOfS(cut.pose.nose)).let { min(it.barCut, it.barOpp) })})")
    }

    /** 바 띠(앞 변 · 위 · 아래 변)와 소재 경계(늘인 윤곽 · 축 너머 거울 · 입구 면) 사이 유클리드 거리(겹치면 음수) */
    fun euclidBar(c: V): Double {
        val wall = mat.boundary()
        var best = INFS
        val zEnd = min(c.z + tool.zBack, mat.zHi + 0.5)
        fun look(p: V) {
            var d = INFS
            for (w in wall) d = min(d, w.dist(p))
            val inside = abs(p.x) >= mat.raw(p.z)
            val v = if (inside) -d else d
            if (v < best) best = v
        }
        for (x in listOf(tool.top, tool.bottom)) {
            val z0 = c.z + if (x == tool.top) tool.zFt else tool.zFb
            val n = max(1, ceil((zEnd - z0) / 0.01).toInt())
            for (q in 0..n) look(V(z0 + (zEnd - z0) * q / n, c.x + x))
        }
        val n = 200
        for (q in 0..n) {
            val x = tool.bottom + (tool.top - tool.bottom) * q / n
            look(V(c.z + tool.frontZ(x), c.x + x))
        }
        return best
    }

    fun dump() {
        println("[SSV] ---- $id")
        plan.direct?.let { println("[SSV] 직접 보정 잘린 NC: ${it.nc} · stop ${it.stop} · ${it.retract} · 후퇴 여유 ${it.retractClear}") }
        plan.machine?.let { println("[SSV] 기계 보정 잘린 NC: ${it.nc} · stop ${it.stop} · ${it.retract} · 후퇴 여유 ${it.retractClear}") }
        plan.end?.let { e ->
            val g = gaps(vOfS(e.pose.nose))
            println("[SSV] 끝점 요소 ${e.element} t ${e.t} 인선 ${e.pose.tip} ${e.part} 맞은편 ${e.opposite} 여유 ${e.margin} · 표본: 바 ${sf(g.barCut)}/${sf(g.barOpp)} 인서트 ${sf(g.insCut)}/${sf(g.insOpp)}")
            println("[SSV] 끝 자세 바 실제 거리 ${sf(euclidBar(vOfS(e.pose.nose)))}")
        }
        for (h in plan.hits) println("[SSV] 간섭 ${h.hit.element} ${h.hit.part}: 직접 ${h.first.direct} ${h.first.directLine} · 기계 ${h.first.machine} ${h.first.machineLine}")
    }
}

// ====================================================================== 표본 모형

/** 한 자세의 반경 여유(mm) */
private class SG(val barCut: Double, val barOpp: Double, val insCut: Double, val insOpp: Double, val turret: Double, val minBore: Double) {
    /** 안전 기준까지 남은 양: 인서트 깎는 쪽은 겹치지 않음, 나머지는 안전 여유 */
    fun slack(safe: Double) = minOf(barCut - safe, barOpp - safe, insOpp - safe, turret - safe, minBore - safe, insCut)
    fun clear() = minOf(barCut, barOpp, insCut, insOpp, turret, minBore)
    fun part(p: HolderPart) = when (p) {
        HolderPart.INSERT -> min(insCut, insOpp)
        HolderPart.BAR -> min(barCut, barOpp)
        HolderPart.TURRET -> turret
        HolderPart.MIN_BORE -> minBore
    }

    private fun all() = listOf("바 깎는 쪽" to barCut, "바 맞은편" to barOpp, "인서트 깎는 쪽" to insCut, "인서트 맞은편" to insOpp, "공구대" to turret, "최소 가공경" to minBore)
    fun worst(safe: Double) = all().minBy { (n, v) -> if (n == "인서트 깎는 쪽") v else v - safe }.let { "${it.first} ${sf(it.second)}" }
    fun worstClear() = all().minBy { it.second }.let { "${it.first} ${sf(it.second)}" }
}

private fun sgOf(m: SMat, t: STool, c: V): SG {
    val insCut = min(m.chain(t.upCut, c, true), m.chain(t.lowCut, c, true))
    val insOpp = min(m.chain(t.upOpp, c, false), m.chain(t.lowOpp, c, false))
    val barCut = m.rangeMin(c.z + t.zFt, c.z + t.zBack) - (t.top + c.x)
    val barOpp = min(m.chain(t.slant, c, false), m.rangeMin(c.z + t.zFb, c.z + t.zBack) + (t.bottom + c.x))
    val ovh = t.bar.overhang
    val face = m.face
    val turret = if (ovh != null && face != null) c.z + t.ref.z + ovh - face else INFS
    val minBore = t.bar.dMin?.let { m.rangeMin(c.z + t.zoneLo, c.z + t.zoneHi) - it / 2 } ?: INFS
    return SG(barCut, barOpp, insCut, insOpp, turret, minBore)
}

/** x = m·z + b, z ∈ [za, zb] (노즈 중심 기준) */
private class Lin(val za: Double, val zb: Double, val m: Double, val b: Double)

/**
 * 보링바(노즈 중심 기준). 인서트 = 꼭짓점 0 · 날 0°/ε 마름모를 κr − 180° 돌리고 노즈 중심을 원점에(뒤보링은 Z 로 뒤집음),
 * 노즈 원호 포함. 띠 위 · 아래로 나온 조각의 위 · 아래 사슬. 바 = 띠 [bottom, top], 앞은 주절삭날 연장선(뒤보링은 인서트 끝 수직)
 */
private class STool(val bar: BoringBar, val r: Double, val reverse: Boolean) {
    val ref = V(if (reverse) r else -r, r)
    val top = r - bar.f + bar.dia / 2
    val bottom = r - bar.f - bar.dia / 2
    val outline: List<V>
    val upCut: List<Lin>
    val upOpp: List<Lin>
    val lowCut: List<Lin>
    val lowOpp: List<Lin>
    val zFt: Double
    val zFb: Double
    val slant: List<Lin>
    val zBack: Double
    val zoneLo: Double
    val zoneHi: Double
    private val corner: V
    private val cot: Double

    init {
        val k = bar.kappaDeg * PI / 180
        val e = bar.shape.epsDeg * PI / 180
        val l = bar.edge
        val phi = k - PI
        fun rot(p: V) = V(p.z * cos(phi) - p.x * sin(phi), p.z * sin(phi) + p.x * cos(phi))
        val cn = rot(V(cos(e / 2), sin(e / 2)) * (r / sin(e / 2)))
        fun place(p: V): V {
            val q = rot(p) - cn
            return if (reverse) V(-q.z, q.x) else q
        }
        val t = r / tan(e / 2)
        val t1 = place(V(t, 0.0))
        val t2 = place(V(t * cos(e), t * sin(e)))
        val a = place(V(l, 0.0))
        val b = place(V(l * cos(e), l * sin(e)))
        val far = if (bar.shape == InsertShape.T) null else place(V(l + l * cos(e), l * sin(e)))
        corner = place(V(0.0, 0.0))
        val arc = mutableListOf<V>()
        if (r > 0) {
            val a2 = atan2(t2.x, t2.z)
            val a1 = atan2(t1.x, t1.z)
            var d = a1 - a2
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            check(cos(a2 + d / 2 - atan2(corner.x, corner.z)) > 0.999) { "노즈 원호가 꼭짓점 쪽이 아니다" }
            val n = max(2, ceil(abs(d) / (0.25 * PI / 180)).toInt())
            for (j in 1 until n) arc += V(r * cos(a2 + d * j / n), r * sin(a2 + d * j / n))
        }
        outline = listOfNotNull(t1, a, far, b, t2) + arc
        chainsOf(clipPoly(outline) { it.x - top }).let {
            upCut = it.first
            upOpp = it.second
        }
        chainsOf(clipPoly(outline) { bottom - it.x }).let {
            lowCut = it.first
            lowOpp = it.second
        }
        if (reverse) {
            val zf = outline.minOf { it.z }
            zFt = zf
            zFb = zf
            cot = 0.0
            slant = emptyList()
        } else {
            val e1 = rot(V(1.0, 0.0))
            cot = e1.z / e1.x
            zFt = corner.z + (top - corner.x) * cot
            zFb = corner.z + (bottom - corner.x) * cot
            slant = if (zFb - zFt > 1e-12) {
                val m = (bottom - top) / (zFb - zFt)
                listOf(Lin(zFt, zFb, m, top - m * zFt))
            } else {
                emptyList()
            }
        }
        zBack = ref.z + (bar.overhang ?: 1e5)
        zoneLo = if (reverse) outline.minOf { it.z } else ref.z
        zoneHi = if (reverse) ref.z else outline.maxOf { it.z }
    }

    /** 바 앞 변의 z (x 높이에서) */
    fun frontZ(x: Double) = if (reverse) zFt else corner.z + (x - corner.x) * cot
}

/** 볼록 다각형을 {f ≥ 0} 으로 자르기 */
private fun clipPoly(poly: List<V>, f: (V) -> Double): List<V> {
    val out = mutableListOf<V>()
    for (i in poly.indices) {
        val p = poly[i]
        val q = poly[(i + 1) % poly.size]
        val a = f(p)
        val b = f(q)
        if (a >= 0) out += p
        if ((a >= 0) != (b >= 0)) out += p + (q - p) * (a / (a - b))
    }
    return out
}

/** 볼록 다각형의 (위 사슬, 아래 사슬) */
private fun chainsOf(poly: List<V>): Pair<List<Lin>, List<Lin>> {
    if (poly.size < 3) return emptyList<Lin>() to emptyList()
    val zs = mutableListOf<Double>()
    for (z in poly.map { it.z }.sorted()) if (zs.isEmpty() || z - zs.last() > 1e-12) zs += z
    val edges = poly.indices.map { poly[it] to poly[(it + 1) % poly.size] }.filter { abs(it.second.z - it.first.z) > 1e-12 }
    val up = mutableListOf<Lin>()
    val lo = mutableListOf<Lin>()
    for (i in 0 until zs.size - 1) {
        val mid = (zs[i] + zs[i + 1]) / 2
        var hi = Double.NEGATIVE_INFINITY
        var low = INFS
        var hm = 0.0
        var hb = 0.0
        var lm = 0.0
        var lb = 0.0
        for ((p, q) in edges) {
            if (mid < min(p.z, q.z) || mid > max(p.z, q.z)) continue
            val m = (q.x - p.x) / (q.z - p.z)
            val b = p.x - m * p.z
            val x = m * mid + b
            if (x > hi) {
                hi = x
                hm = m
                hb = b
            }
            if (x < low) {
                low = x
                lm = m
                lb = b
            }
        }
        if (hi.isFinite()) up += Lin(zs[i], zs[i + 1], hm, hb)
        if (low.isFinite()) lo += Lin(zs[i], zs[i + 1], lm, lb)
    }
    return up to lo
}

/**
 * 구멍 소재 {|x| ≥ ρ(z)}. 늘인 윤곽 = (입구 면을 모르면 −시작 접선 반직선) + 도면 + (끝이 축 위가 아니면 끝 접선 반직선),
 * 반직선은 축에서 멈추고 거의 수직이면 수직. 가장 큰 z 너머는 공기, 깊은 끝이 축 위면 그 너머는 소재
 */
private class SMat(drawing: List<El>, faceAtStart: Boolean) {
    val reverse = drawing.last().b.z > drawing.first().a.z
    val ext: List<El>
    val zLo: Double
    val zHi: Double
    val closed: Boolean
    val face: Double?
    private val head: Ln?
    private val pz0: DoubleArray
    private val pz1: DoubleArray
    private val pe: List<El>
    private val ps: DoubleArray
    val breaks: DoubleArray
    private val isFace: BooleanArray
    private val faces: DoubleArray
    private val g0: Double
    private val gLo: Double
    private val gHi: Double
    private val gn: Int
    private val sp: Array<DoubleArray>

    init {
        val first = drawing.first()
        val last = drawing.last()
        fun ray(p: V, d0: V): V? {
            val d = if (abs(d0.z) <= 2e-3) V(0.0, if (d0.x >= 0) 1.0 else -1.0) else d0
            if (d.x >= 0) return p + d * 1e5
            val len = min(1e5, p.x / -d.x)
            if (len <= 1e-9) return null
            return if (len < 1e5) V(p.z + d.z * len, 0.0) else p + d * len
        }
        head = if (faceAtStart) null else ray(first.a, first.tangent(0.0) * -1.0)?.let { Ln(it, first.a) }
        val tail = if (last.b.x > 1e-9) ray(last.b, last.tangent(1.0))?.let { Ln(last.b, it) } else null
        ext = listOfNotNull(head) + drawing + listOfNotNull(tail)
        val a = ext.first().a
        val b = ext.last().b
        val mouthIsA = a.z >= b.z
        zHi = if (mouthIsA) a.z else b.z
        zLo = if (mouthIsA) b.z else a.z
        closed = (if (mouthIsA) b.x else a.x) <= 1e-9
        val mouthX = if (mouthIsA) a.x else b.x
        val mouthRay = if (mouthIsA) head else tail
        face = if (faceAtStart || mouthX <= 1e-9 || mouthRay == null || abs(mouthRay.a.z - mouthRay.b.z) <= 1e-12) zHi else null
        val pieces = ext.filter { abs(it.b.z - it.a.z) > 1e-9 }.sortedBy { min(it.a.z, it.b.z) }
        pe = pieces
        pz0 = DoubleArray(pieces.size) { min(pieces[it].a.z, pieces[it].b.z) }
        pz1 = DoubleArray(pieces.size) { max(pieces[it].a.z, pieces[it].b.z) }
        ps = DoubleArray(pieces.size) { (pieces[it] as? Ac)?.let { c -> if (c.at(0.5).x >= c.c.x) 1.0 else -1.0 } ?: 0.0 }
        breaks = (pz0.toList() + pz1.toList() + zLo + zHi).sorted().distinct().toDoubleArray()
        isFace = BooleanArray(breaks.size) { i ->
            val l = raw(breaks[i] - 1e-7)
            val u = raw(breaks[i] + 1e-7)
            !(l.isFinite() && u.isFinite()) || abs(l - u) > 1e-4
        }
        faces = breaks.filterIndexed { i, _ -> isFace[i] }.toDoubleArray()
        val dz = drawing.flatMap { listOf(it.a.z, it.b.z) }
        gLo = max(zLo, dz.min() - 40)
        gHi = min(zHi, dz.max() + 40)
        g0 = gLo
        gn = max(0, ceil((gHi - gLo) / GH).toInt())
        val g = DoubleArray(gn + 1) { rho(g0 + it * GH) }
        var levels = 1
        while ((1 shl levels) <= gn + 1) levels++
        sp = Array(levels) { DoubleArray(0) }
        sp[0] = g
        for (j in 1 until levels) {
            val prev = sp[j - 1]
            val w = 1 shl (j - 1)
            sp[j] = DoubleArray(gn + 1) { i -> if (i + w <= gn) min(prev[i], prev[i + w]) else prev[i] }
        }
    }

    private fun xOn(j: Int, z: Double): Double = when (val e = pe[j]) {
        is Ln -> e.a.x + (e.b.x - e.a.x) * (z - e.a.z) / (e.b.z - e.a.z)
        is Ac -> e.c.x + ps[j] * sqrt(max(0.0, e.r * e.r - (z - e.c.z) * (z - e.c.z)))
    }

    /** ρ(z) 그대로 — 면 위(두 토막이 만나는 z)는 소재가 많은 쪽 */
    fun raw(z: Double): Double {
        if (z > zHi) return INFS
        if (z < zLo) return if (closed) 0.0 else INFS
        var lo = 0
        var hi = pz0.size - 1
        while (lo < hi) {
            val m = (lo + hi) / 2
            if (pz1[m] < z) lo = m + 1 else hi = m
        }
        var best = INFS
        for (j in max(0, lo - 1)..min(pz0.size - 1, lo + 2)) if (pz0[j] <= z && z <= pz1[j]) best = min(best, xOn(j, z))
        return best
    }

    /** 면 ±[NEAR_FACE] 안이면 소재가 적은 쪽 */
    fun rho(z: Double): Double {
        for (f in faces) if (abs(z - f) < NEAR_FACE) return maxOf(raw(z), raw(f - NEAR_FACE), raw(f + NEAR_FACE))
        return raw(z)
    }

    private fun gridMin(i0: Int, i1: Int): Double {
        if (i0 > i1) return INFS
        var j = 0
        while ((1 shl (j + 1)) <= i1 - i0 + 1) j++
        return min(sp[j][i0], sp[j][i1 - (1 shl j) + 1])
    }

    /** z ∈ [a, b] 에서 가장 작은 ρ: 격자 · 양 끝 · 안의 꺾임점(끝에서 0.001 안의 면은 빼고) */
    fun rangeMin(a: Double, b: Double): Double {
        if (b < a) return INFS
        var m = min(rho(a), rho(b))
        if (closed && a < zLo - NEAR_FACE) m = 0.0
        for ((i, z) in breaks.withIndex()) {
            if (z <= a || z >= b) continue
            if (isFace[i] && (z - a < NEAR_FACE || b - z < NEAR_FACE)) continue
            m = min(m, raw(z))
        }
        for (z in doubleArrayOf(gLo, gHi)) if (z > a && z < b) m = min(m, rho(z))
        val i0 = max(0, ceil((max(a, gLo) - g0) / GH).toInt())
        val i1 = min(gn, floor((min(b, gHi) - g0) / GH).toInt())
        return min(m, gridMin(i0, i1))
    }

    /** 사슬(노즈 [c] 에 놓은)의 가장 작은 ρ − x(upper) 또는 ρ + x */
    fun chain(ch: List<Lin>, c: V, upper: Boolean): Double {
        if (ch.isEmpty()) return INFS
        val zA = c.z + ch.first().za
        val zB = c.z + ch.last().zb
        var best = INFS
        for (p in ch) {
            val za = c.z + p.za
            val zb = c.z + p.zb
            fun v(z: Double, rr: Double) {
                val x = p.m * (z - c.z) + p.b + c.x
                val g = if (upper) rr - x else rr + x
                if (g < best) best = g
            }
            v(za, rho(za))
            v(zb, rho(zb))
            for ((i, z) in breaks.withIndex()) {
                if (z <= za || z >= zb) continue
                if (isFace[i] && (z - zA < NEAR_FACE || zB - z < NEAR_FACE)) continue
                v(z, raw(z))
                if (isFace[i]) {
                    for (s in doubleArrayOf(z - NEAR_FACE - 1e-6, z + NEAR_FACE + 1e-6)) if (s > za && s < zb) v(s, raw(s))
                }
            }
            if (closed && za < zLo - NEAR_FACE) {
                v(za, 0.0)
                v(min(zb, zLo - NEAR_FACE), 0.0)
            }
            for (z in doubleArrayOf(gLo, gHi)) if (z > za && z < zb) v(z, rho(z))
            val i0 = max(0, ceil((max(za, gLo) - g0) / GH).toInt())
            val i1 = min(gn, floor((min(zb, gHi) - g0) / GH).toInt())
            val g = sp[0]
            for (i in i0..i1) v(g0 + i * GH, g[i])
        }
        return best
    }

    /** 소재 경계(유클리드 거리용): 늘인 윤곽, 입구 면(위로), 축 너머 거울 */
    fun boundary(): List<El> {
        val a = ext.first().a
        val b = ext.last().b
        val mouth = if (a.z >= b.z) a else b
        val up = listOf(Ln(mouth, V(mouth.z, mouth.x + 1e3)))
        val all = ext + up
        return all + all.map { mirror(it) }
    }

    private fun mirror(e: El): El = when (e) {
        is Ln -> Ln(V(e.a.z, -e.a.x), V(e.b.z, -e.b.x))
        is Ac -> Ac(V(e.a.z, -e.a.x), V(e.b.z, -e.b.x), V(e.c.z, -e.c.x), !e.cw)
    }
}

// ====================================================================== NC 글 · 보정

/** 다시 읽은 줄: 끝점, 움직임(첫 G00 · G40 만 있는 줄은 null), G40 · G00 */
private class SB(val end: V, val el: El?, val g40: Boolean, val rapid: Boolean)

private fun readS(lines: List<String>): List<SB> {
    var z: Double? = null
    var x: Double? = null
    val out = mutableListOf<SB>()
    for (line in lines) {
        val w = line.trim().split(" ").filter { it.isNotEmpty() }
        val gs = w.filter { it.startsWith("G") }
        val motion = gs.lastOrNull { it == "G00" || it == "G01" || it == "G02" || it == "G03" }
        require(motion != null || gs == listOf("G40")) { "움직임 G 가 없는 줄: $line" }
        fun num(ch: Char) = w.firstOrNull { it[0] == ch }?.substring(1)?.toDouble()
        val nz = num('Z') ?: z ?: error("Z 없음: $line")
        val nx = num('X')?.let { it / 2 } ?: x ?: error("X 없음: $line")
        val from = if (z != null && x != null) V(z, x) else null
        val to = V(nz, nx)
        val el: El? = when {
            from == null || motion == null -> null
            motion == "G02" || motion == "G03" -> arcR(from, to, num('R') ?: error("R 없음: $line"), motion == "G02")
            else -> Ln(from, to)
        }
        out += SB(to, el, "G40" in gs, motion == "G00")
        z = nz
        x = nx
    }
    return out
}

/** 공구 쪽 단위 법선: G41 = 진행 왼쪽. 원호는 볼록(공구가 원 밖)이면 바깥 */
private fun outward(a: Ac, left: Boolean) = left == a.cw

private fun nrmS(e: El, p: V, left: Boolean): V = when (e) {
    is Ln -> e.tangent(0.0).ccw() * (if (left) 1.0 else -1.0)
    is Ac -> (p - e.c).unit() * (if (outward(e, left)) 1.0 else -1.0)
}

/** 공구 쪽으로 [r] 민 보정선: 직선은 평행 이동, 원호는 같은 중심 · 볼록 R + r · 오목 R − r */
private fun offsetS(e: El, left: Boolean, r: Double): El = when (e) {
    is Ln -> {
        val n = nrmS(e, e.a, left)
        Ln(e.a + n * r, e.b + n * r)
    }
    is Ac -> Ac(e.a + nrmS(e, e.a, left) * r, e.b + nrmS(e, e.b, left) * r, e.c, e.cw)
}

/** 이웃 보정선의 이음: 거의 이어지면 가운데, 아니면 두 선 · 원(늘인 것)의 교점 가운데 두 끝 가운데에 가까운 것 */
private fun joinS(a: El, b: El): V? {
    val mid = (a.b + b.a) * 0.5
    if ((a.b - b.a).len <= 2e-3) return mid
    val cand = mutableListOf<V>()
    fun lineOf(e: Ln) = e.a to (e.b - e.a)
    when {
        a is Ln && b is Ln -> {
            val (p, d) = lineOf(a)
            val (q, e) = lineOf(b)
            val den = d cross e
            if (abs(den) > 1e-12) cand += p + d * (((q - p) cross e) / den)
        }
        a is Ln && b is Ac -> cand += lineCircle(a, b.c, b.r)
        a is Ac && b is Ln -> cand += lineCircle(b, a.c, a.r)
        a is Ac && b is Ac -> {
            val d = (b.c - a.c).len
            if (d > 1e-12) {
                val x = (d * d + a.r * a.r - b.r * b.r) / (2 * d)
                val h2 = a.r * a.r - x * x
                if (h2 >= -1e-12) {
                    val u = (b.c - a.c).unit()
                    val base = a.c + u * x
                    val h = sqrt(max(0.0, h2))
                    cand += base + u.ccw() * h
                    cand += base - u.ccw() * h
                }
            }
        }
    }
    return cand.minByOrNull { (it - mid).len }
}

private fun lineCircle(l: Ln, c: V, rr: Double): List<V> {
    val d = (l.b - l.a).unit()
    val f = l.a - c
    val bq = f dot d
    val disc = bq * bq - ((f dot f) - rr * rr)
    if (disc < -1e-12) return emptyList()
    val s = sqrt(max(0.0, disc))
    return listOf(l.a + d * (-bq - s), l.a + d * (-bq + s))
}

/** 요소 위 노즈 c 의 도면 발(가장 가까운 점)과 끝에 묶였는가 */
private fun footS(e: El, c: V): Pair<V, Boolean> = when (e) {
    is Ln -> {
        val d = e.b - e.a
        val t = ((c - e.a) dot d) / (d dot d)
        e.at(t.coerceIn(0.0, 1.0)) to (t < 0 || t > 1)
    }
    is Ac -> {
        val t = paramS(e, c)
        if (t in 0.0..1.0) (e.c + (c - e.c).unit() * e.r) to false else (if ((c - e.a).len <= (c - e.b).len) e.a else e.b) to true
    }
}

/** 요소 위(또는 가까운) 점의 비율 — 늘인 곳은 0 아래 · 1 위 */
private fun paramS(e: El, p: V): Double = when (e) {
    is Ln -> ((p - e.a) dot (e.b - e.a)) / ((e.b - e.a) dot (e.b - e.a))
    is Ac -> {
        val s = if (e.cw) -1.0 else 1.0
        var a = s * (atan2((p - e.c).x, (p - e.c).z) - atan2((e.a - e.c).x, (e.a - e.c).z))
        a %= 2 * PI
        if (a < 0) a += 2 * PI
        if (a > PI + e.sweep / 2) a -= 2 * PI
        if (e.sweep <= 0) 0.0 else a / e.sweep
    }
}

/** 비율 t(0~1 밖)의 늘인 점 */
private fun extend(e: El, t: Double): V = when (e) {
    is Ln -> e.a + (e.b - e.a) * t
    is Ac -> {
        val s = if (e.cw) -1.0 else 1.0
        val a = atan2((e.a - e.c).x, (e.a - e.c).z) + s * e.sweep * t
        V(e.c.z + e.r * cos(a), e.c.x + e.r * sin(a))
    }
}

/** Haas 표: 노즈 중심 → 가상 인선 */
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

private fun vOfS(p: com.bmt.nosecalc.calc.Pt) = V(p.z, p.x)

private fun elOf(s: Seg): El = when (s) {
    is Line -> Ln(vOfS(s.start), vOfS(s.end))
    is Arc -> Ac(vOfS(s.start), vOfS(s.end), vOfS(s.center), s.cw)
}

private fun sr3(v: Double) = (v * 1000).roundToLong() / 1000.0

private fun sf(v: Double) = if (v.isFinite()) ((v * 10000).roundToLong() / 10000.0).toString() else "$v"

// ====================================================================== 모으기

private class SLog {
    var cases = 0
    private val bads = linkedMapOf<String, MutableList<String>>()
    private val infos = linkedMapOf<String, MutableList<String>>()
    private val counts = linkedMapOf<String, Int>()
    private val notes = linkedMapOf<String, DoubleArray>() // min, max, sum, n
    private val ratios = linkedMapOf<String, IntArray>()

    fun bad(id: String, tag: String, msg: String) {
        bads.getOrPut(tag) { mutableListOf() } += "[$tag] $msg\n      입력: $id"
    }

    fun info(id: String, tag: String, msg: String) {
        infos.getOrPut(tag) { mutableListOf() } += "[$tag] $msg\n      입력: $id"
    }

    fun skip(why: String) = count("건너뜀: $why")

    fun count(k: String) {
        counts[k] = (counts[k] ?: 0) + 1
    }

    fun note(k: String, v: Double) {
        if (!v.isFinite()) return
        val a = notes.getOrPut(k) { doubleArrayOf(INFS, Double.NEGATIVE_INFINITY, 0.0, 0.0) }
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

    fun finish(name: String, started: TimeSource.Monotonic.ValueTimeMark) {
        println("[SSV] ===== $name: 사례 $cases, ${started.elapsedNow()}")
        for ((k, v) in counts) println("[SSV] 셈 $k: $v")
        for ((k, a) in notes) println("[SSV] 값 $k: 최소 ${sf(a[0])} · 최대 ${sf(a[1])} · 평균 ${sf(a[2] / a[3])} (${a[3].toInt()})")
        for ((k, a) in ratios) println("[SSV] 비율 $k: ${a[0]}/${a[1]} = ${if (a[1] > 0) a[0] * 100 / a[1] else 0} %")
        for ((k, v) in infos) {
            println("[SSV] 정보 $k ${v.size}건")
            v.take(6).forEach { println("[SSV]   $it") }
        }
        for ((k, a) in ratios) if (a[1] > 0 && a[0] < 0.8 * a[1]) bad("전체", "S2 비율", "$k ${a[0]}/${a[1]} < 80 %")
        if (bads.isEmpty()) return
        val total = bads.values.sumOf { it.size }
        println("[SSV] 실패 $total 건 ${bads.mapValues { it.value.size }}")
        for (v in bads.values) v.take(30).forEach { println("[SSV]   $it") }
        kotlin.test.fail("$name: 실패 $total 건 ${bads.mapValues { it.value.size }}\n" + bads.values.joinToString("\n") { it.take(3).joinToString("\n") })
    }
}
