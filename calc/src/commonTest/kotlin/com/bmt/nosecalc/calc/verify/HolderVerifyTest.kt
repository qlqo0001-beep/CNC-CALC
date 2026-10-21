package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BarShape
import com.bmt.nosecalc.calc.Bore
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderSkip
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.Seg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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
 * 독립 검증 ③ — 보링바(홀더) 간섭.
 *
 * HolderCheck 은 단면의 위 · 아래 사슬과 꺾임점 · 원호 극값으로 여유의 정확한 최솟값을 구한다. 여기서는 그 식을 쓰지 않고
 * 이 파일에서 따로 만든 공구 · 구멍 모형 위에 점을 촘촘히 찍어 잰다(거리 · NC 해석은 V · El · parseNc 만 쓴다):
 * - 인서트: 꼭짓점이 원점이고 두 날이 0 · ε 방향인 마름모(T 는 삼각형)를 κr − 180° 돌리고 두 날에 접하는 노즈 원으로 둥글게.
 *   뒤보링(경로가 +Z)은 노즈 중심에서 Z 로 뒤집는다. 바 띠 밖으로 나온 곳과 띠 경계에서 자른 선이 인서트다
 * - 바: 가상 인선 x 에서 f 아래가 중심인 지름 D 의 띠. 앞은 돌린 주절삭날이 꼭짓점을 지나는 선(뒤보링은 인서트 끝에서 수직), 뒤는 공구대 면
 * - 구멍 반경 R(z): 도면 요소를 따로 읽고, 입구 면 · 반직선 · 막힌 바닥 규칙도 따로 짰다. 면(R 이 뛰는 곳) ±0.001 은 두 쪽 가운데
 *   소재가 적은 쪽 — NC 글은 0.001 로 반올림돼 면에 딱 붙은 자세가 1e-4 들어가 보이고, 그러면 반경 겹침이 면 높이만큼 튄다
 * - 경로: NC 글을 따라 0.05 mm 마다, 공구 윤곽 점은 0.005 mm 마다. 노즈 원 위 점은 뺀다(노즈가 벽에 닿는 게 정상).
 *   바 띠의 긴 위 · 아래 변은 구멍 쪽 0.005 mm 격자의 점(그 변의 진짜 점)과 반직선 양 끝으로 잰다
 *
 * | V1 | 표준 팁이면 둥근 인서트의 (가장 작은 z 또는 뒤보링은 가장 큰 z, 가장 큰 x) = NC 인선점 ±1e-6 — 팁 2 · 1 기준이 맞다 |
 * | V2 | 모든 자세 · 부분: 구현 여유 ≤ 표본 여유 + 1e-6 (표본 점은 구현 조각 안에 있으므로 정확한 최솟값이 더 클 수 없다) |
 * | V3 | 구현의 최솟점이 면에서 0.05 이상, \|ρ′\| ≤ 3 이면 표본 여유 ≤ 구현 여유 + 0.02. 최솟점이 노즈 원 위가 아닌 자세의 70 % 이상이 대상 |
 * | V4 | 깊이 > 0.05 인 구현 닿음이 있는 요소에는 표본 닿음도, 그 거꾸로도. 처음 닿는 곳은 같은 G40 요소 위 0.01 mm 간격으로 찾아 0.1 mm 안 |
 * | V5 | 바 자루 구간의 최솟점에서 단면 여유 = 원기둥 표면 표본(φ 1°, ρ = √((x_c + a cos φ)² + (a sin φ)²)) ±0.003 (요소마다 한 번) |
 * | V6 | 닿음마다 lineOf(첫 인선점)이 가리키는 NC 블록(parseNc)이 그 점을 1e-3 안에 지난다 |
 */
class HolderVerifyTest {
    private val stats = Stats()

    /** 설계 문서의 손 계산 A~F · 문턱 · 화면 프리셋 바와 무작위 내경 TYPE 2~6 300개(5 · 6 은 넷에 하나 관통) */
    @Test
    fun innerTypes() {
        val rep = Report("보링바 · 내경 TYPE")
        val started = TimeSource.Monotonic.markNow()
        val barA = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
        val dome = RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0)
        val step = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0)
        val fixed = listOf(
            dome to barA,
            dome.copy(d1 = 18.0) to barA,
            step to barA,
            step.copy(through = true) to barA,
            step.copy(mainR = 30.0, d2 = 50.0, d1 = 38.0) to barA,
            step.copy(mainR = 30.0, d2 = 50.0, d1 = 38.0) to barA.copy(kappaDeg = 90.0),
            dome to barA.copy(overhang = 12.0),
            dome to barA.copy(dMin = 20.0),
            RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0, legLen = 5.0) to BoringBar(8.0, 4.5, InsertShape.D, 7.75, 93.0),
            step.copy(through = true) to BoringBar(20.0, 20.01, InsertShape.C, 9.67, 95.0),
            step.copy(through = true) to BoringBar(20.0, 19.99, InsertShape.C, 9.67, 95.0),
            // 화면 스크린샷 · 골든의 프리셋 A16R-SCLCR 09(Ø16 · f 11 · C 9 · κr 95° · DMIN 20)
            dome to A16,
            step.copy(through = true) to A16,
        )
        for ((inp, bar) in fixed) runType(rep, inp.copy(inner = true, noseR = 0.4, zClear = 1.0), bar)
        val rnd = Random(2024)
        val g = GeometricVerifyTest()
        repeat(300) {
            val r = NOSE_RS[rnd.nextInt(NOSE_RS.size)]
            val inp = g.randomRound(rnd.nextInt(2, 7), rnd, inner = true).input.copy(noseR = r)
            runType(rep, inp, randomBar(rnd, r))
        }
        println("${rep.name}: ${started.elapsedNow()} · ${stats.line()}")
        stats.require(rep)
        rep.finish()
    }

    /** 3점호 내경: 정방향 150 · 뒤보링(역방향) 150 무작위, Z 로 되돌아가는 원호 30(검사 안 함이 맞나), 뒤보링 손 계산 · 입구 R(화면 프리셋 바도) */
    @Test
    fun arcs() {
        val rep = Report("보링바 · 3점호 내경")
        val started = TimeSource.Monotonic.markNow()
        val back = Arc3Input(inner = true, noseR = 0.4, x1 = 30.0, z1 = -20.0, x2 = 24.0, z2 = -19.0, x3 = 20.0, z3 = -15.0, zClear = 1.0)
        for (f in listOf(11.0, 11.5)) runArc(rep, back, BoringBar(12.0, f, InsertShape.C, 9.67, 95.0))
        // 입구 R5(Ø30 면 → Ø20 구멍), 시작 접선이 수직 — 입구 면을 아는 3점호
        val mouth = Arc3Input(inner = true, noseR = 0.4, x1 = 30.0, z1 = 0.0, x2 = 22.929, z2 = -1.464, x3 = 20.0, z3 = -5.0, zClear = 1.0)
        runArc(rep, mouth, BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0, overhang = 30.0))
        runArc(rep, mouth, A16)
        val rnd = Random(77)
        for (kind in listOf(Kind.FORWARD, Kind.REVERSE, Kind.LOOP)) repeat(if (kind == Kind.LOOP) 30 else 150) {
            val r = NOSE_RS[rnd.nextInt(NOSE_RS.size)]
            runArc(rep, randomArc(rnd, kind, r), randomBar(rnd, r))
        }
        println("${rep.name}: ${started.elapsedNow()} · ${stats.line()}")
        stats.require(rep)
        rep.finish()
    }

    private fun runType(rep: Report, inp: RoundInput, bar: BoringBar) = run(rep, "$inp", RoundCalc.calc(inp), true, bar, inp.noseR)

    private fun runArc(rep: Report, inp: Arc3Input, bar: BoringBar) = run(rep, "$inp", RoundCalc.arc3(inp), false, bar, inp.noseR)

    private fun run(rep: Report, what: String, calc: RoundResult, faceAtStart: Boolean, bar: BoringBar, r: Double) {
        rep.cases++
        val sol = calc.solution ?: return rep.stop("계산 멈춤 ${calc.warnings}")
        val id = "$what + $bar"
        val vb = VBore(sol.drawing.segs.map(::toEl), faceAtStart)
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, r, faceAtStart, bar)
        if (!vb.monotone) {
            if (res.skip != HolderSkip.NOT_MONOTONE) rep.bad(id, "건너뜀", "Z 로 되돌아가는 윤곽인데 ${res.skip}")
            return rep.stop("NOT_MONOTONE")
        }
        if (res.skip != null) return rep.bad(id, "건너뜀", "${res.skip}")
        rep.solved++
        res.barClear?.let { rep.worstClear = min(rep.worstClear, it) }
        val vt = VTool(bar, r, vb.reverse)
        val bore = Bore(sol.drawing, faceAtStart)
        val tool = BarShape(bar, r, vb.reverse)
        val tv = tipV(sol.tip, r)
        val nc = parseNc(sol.nc)
        val entry = nc.firstOrNull()?.a ?: V(sol.entry.z, sol.entry.x)
        // 요소마다 NC 블록(줄 0 = G00 진입점, 줄 i ≥ 1 = parseNc 의 i − 1 번)
        val paths = sol.ncRefs.indices.drop(1).groupBy({ sol.ncRefs[it].element }, { nc[it - 1] })
        val elements = listOf(-2) + (listOf(-1) + sol.program.segs.indices).filter { it in paths || res.hits.any { h -> h.element == it } }

        fun gaps(tip: V): DoubleArray = vGaps(vt, vb, tip - tv, bar)
        fun impl(tip: V) = HolderCheck.margins(bore, tool, Pt(tip.z - tv.z, tip.x - tv.x))

        // 구현과 같은 G40 경로의 요소(−1 = 진입 이동) — 처음 닿는 곳은 이 위에서 비교한다. NC 글은 0.001 로 반올림돼
        // 가파른 벽(|ρ′| ~ 35, 돔 바닥 근처)에서 여유가 0.02 쯤 달라져 스치듯 닿기 시작하는 곳이 0.2 mm 움직일 수 있다
        fun program(e: Int): El? = when {
            e == -1 -> Ln(V(sol.entry.z, sol.entry.x), V(sol.program.segs[0].start.z, sol.program.segs[0].start.x))
            e >= 0 -> toEl(sol.program.segs[e])
            else -> null
        }

        // V1: 가상 인선 기준
        if (sol.tip == (if (vb.reverse) 1 else 2)) {
            val (ez, ex) = vt.extreme()
            val c = entry - tv
            if (abs(c.z + ez - entry.z) > 1e-6 || abs(c.x + ex - entry.x) > 1e-6) {
                rep.bad(id, "V1", "둥근 인서트 끝 (${c.z + ez}, ${c.x + ex}) ≠ 인선 $entry")
            }
        }

        for (e in elements) {
            val segs = if (e == -2) emptyList() else paths[e].orEmpty()
            val len = segs.sumOf { it.length }
            val n = if (segs.isEmpty()) 0 else max(1, ceil(len / PATH_STEP).toInt())
            val ss = (0..n).map { if (n == 0) 0.0 else len * it / n }
            fun at(s: Double): V = if (segs.isEmpty()) entry else pointAt(segs, s)
            val vDepth = DoubleArray(PARTS.size) { Double.NEGATIVE_INFINITY }
            val vFirst = arrayOfNulls<Double>(PARTS.size)
            val vgs = mutableListOf<DoubleArray>()
            var cylinderDone = false
            if (e == -2 || segs.isNotEmpty()) {
                for (s in ss) {
                    val tip = at(s)
                    val vg = gaps(tip)
                    vgs += vg
                    val ig = impl(tip)
                    val c = tip - tv
                    for (p in PARTS) {
                        val mi = ig[p].m
                        val mv = vg[p.ordinal]
                        if (mi > mv + 1e-6) rep.bad(id, "V2", "요소 $e ${p.name} 인선 $tip: 구현 ${fmt(mi)} > 표본 ${fmt(mv)}")
                        if (mv < -TOUCH) {
                            vDepth[p.ordinal] = max(vDepth[p.ordinal], -mv)
                            if (vFirst[p.ordinal] == null) vFirst[p.ordinal] = s
                        }
                        if (!mi.isFinite()) continue
                        if (p == HolderPart.TURRET) {
                            if (abs(mi - mv) > 1e-9) rep.bad(id, "V3", "공구대 ${fmt(mi)} ≠ ${fmt(mv)}")
                            continue
                        }
                        val gz = ig[p].z
                        val gx = ig[p].x
                        // 노즈 원 위 최솟점(노즈가 벽에 닿는 정상 접촉)은 표본이 일부러 빼므로 비교 밖
                        if (p != HolderPart.MIN_BORE && hypot(gz - c.z, gx - c.x) <= r + 1e-3) continue
                        stats.poses++
                        if (vb.nearFace(gz) < 0.05 || abs(vb.slope(gz)) > 3) continue
                        stats.qualified++
                        if (mv > mi + 0.02) {
                            rep.bad(id, "V3", "요소 $e ${p.name} 인선 $tip: 표본 ${fmt(mv)} > 구현 ${fmt(mi)} + 0.02 (최솟점 Z${fmt(gz)} X${fmt(2 * gx)})")
                        }
                        // V5: 바 자루 구간의 최솟점은 원기둥 표면으로도 같다
                        if (p == HolderPart.BAR && gz > c.z + vt.shankFrom && !cylinderDone) {
                            cylinderDone = true
                            stats.cylinder++
                            val xc = c.x + vt.barMid
                            val m3 = vb.raw(gz) - (0 until 360).maxOf { f ->
                                val u = xc + vt.barHalf * cos(f * PI / 180)
                                val w = vt.barHalf * sin(f * PI / 180)
                                sqrt(u * u + w * w)
                            }
                            if (abs(m3 - mi) > 0.003) rep.bad(id, "V5", "Z${fmt(gz)} 원기둥 ${fmt(m3)} ≠ 단면 ${fmt(mi)}")
                        }
                    }
                }
            }
            // V4: 요소 · 부분마다 닿음이 서로 있다, 처음 닿는 위치
            for (p in PARTS) {
                val hit = res.hits.singleOrNull { it.element == e && it.part == p }
                if (vDepth[p.ordinal] > 0.05 && hit == null) {
                    rep.bad(id, "V4", "요소 $e ${p.name}: 표본은 ${fmt(vDepth[p.ordinal])} 겹치는데 구현은 닿음 없음 (처음 ${vFirst[p.ordinal]?.let(::fmt)} mm)")
                }
                if (hit == null) continue
                stats.hits++
                // V6: 경고에 쓰는 NC 줄이 첫 인선점을 지난다
                val line = sol.lineOf(e, hit.first.tip)
                val q = V(hit.first.tip.z, hit.first.tip.x)
                val off = when (line) {
                    !in sol.nc.indices -> INF
                    0 -> (q - entry).len
                    else -> nc[line - 1].dist(q)
                }
                if (off > 1e-3) rep.bad(id, "V6", "요소 $e ${p.name}: ${line + 1}번 줄 ${sol.nc.getOrNull(line)} 이 첫 인선점 $q 에서 ${fmt(off)}")
                if (hit.depth <= 0.05) continue
                val worst = V(hit.worst.tip.z, hit.worst.tip.x)
                if (vDepth[p.ordinal] < TOUCH && gaps(worst)[p.ordinal] >= -TOUCH) {
                    rep.bad(id, "V4", "요소 $e ${p.name}: 구현 깊이 ${fmt(hit.depth)} 인데 표본은 닿지 않음 (가장 깊은 인선 $worst)")
                    continue
                }
                val pe = program(e) ?: continue
                val si = hit.fromT * pe.length
                // NC 표본이 0.1 mm 넘게 먼저 깊이 닿았는데 구현도 그 G40 자세에서 닿는다면 구현 훑기가 놓친 것
                val early = vgs.indices.firstOrNull { ss[it] < si - 0.1 && -vgs[it][p.ordinal] > 0.05 && impl(pe.at(ss[it] / len))[p].m < -TOUCH }
                if (early != null) rep.bad(id, "V4", "요소 $e ${p.name}: ${fmt(ss[early])} mm 에서 먼저 닿음, 구현 처음 ${fmt(si)}")
                // 처음 닿는 곳: 같은 G40 요소 위 0.01 mm 간격(0.11 앞에서 이미 닿으면 그것으로 어긋남)
                val from = max(0.0, si - 0.11)
                val sv = (0..22).map { from + it * FINE_STEP }
                    .firstOrNull { it <= pe.length + 1e-9 && gaps(pe.at(min(1.0, it / pe.length)))[p.ordinal] < -TOUCH }
                if (sv == null || abs(sv - si) > 0.1) rep.bad(id, "V4", "요소 $e ${p.name}: 처음 닿는 곳 구현 ${fmt(si)} · 표본 ${sv?.let(::fmt)} mm")
            }
        }
    }

    // -------------------------------------------------------------------- 무작위 입력

    private enum class Kind { FORWARD, REVERSE, LOOP }

    /** GeometricVerifyTest.arc3 와 같은 꼴: 위 반원은 반시계로 가면 Z 가 준다(정방향). LOOP 는 0° · 180° 를 넘어 Z 로 되돌아간다 */
    private fun randomArc(rnd: Random, kind: Kind, r: Double): Arc3Input {
        val upper = rnd.nextBoolean()
        val bigR = r3(rnd.nextDouble(0.5, 30.0))
        val lo: Double
        val hi: Double
        if (kind == Kind.LOOP) {
            val turn = if (rnd.nextBoolean()) 0.0 else 180.0
            lo = turn - rnd.nextDouble(10.0, 60.0)
            hi = turn + rnd.nextDouble(10.0, 60.0)
        } else {
            val (lo0, hi0) = if (upper) 3.0 to 177.0 else 183.0 to 357.0
            var a: Double
            var b: Double
            do {
                a = rnd.nextDouble(lo0, hi0)
                b = rnd.nextDouble(lo0, hi0)
            } while (abs(a - b) < 10)
            lo = min(a, b)
            hi = max(a, b)
        }
        val ccw = if (upper) kind != Kind.REVERSE else kind == Kind.REVERSE
        val th = listOf(if (ccw) lo else hi, rnd.nextDouble(lo + 2, hi - 2), if (ccw) hi else lo).map { it * PI / 180 }
        val minSin = (0..90).minOf { sin((lo + (hi - lo) * it / 90) * PI / 180) }
        val c = V(r3(rnd.nextDouble(-20.0, 5.0)), r3(rnd.nextDouble(0.5 - bigR * minSin, 30.5 - bigR * minSin)))
        val p = th.map { V(r3(c.z + bigR * cos(it)), r3(2 * (c.x + bigR * sin(it))) / 2) }
        return Arc3Input(
            inner = true, noseR = r, x1 = 2 * p[0].x, z1 = p[0].z, x2 = 2 * p[1].x, z2 = p[1].z, x3 = 2 * p[2].x, z3 = p[2].z,
            zClear = r3(rnd.nextDouble(0.0, 3.0)),
        )
    }

    /** 설계 문서의 무작위 바: D 6~32, f fMin + 0.05 ~ D/2 + 6, 인서트 넷, κr 90 ~ min(117.5, 179 − ε), 날 edgeMin + 0.3 ~ 16, 돌출 · DMIN 반은 비움 */
    private fun randomBar(rnd: Random, r: Double): BoringBar {
        val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
        val dia = r3(rnd.nextDouble(6.0, 32.0))
        val kr = r3(rnd.nextDouble(90.0, min(117.5, 179 - shape.epsDeg)))
        val f = r3(rnd.nextDouble(HolderCheck.fMin(dia, kr, r) + 0.05, dia / 2 + 6))
        var b = BoringBar(dia, f, shape, r3(rnd.nextDouble(HolderCheck.edgeMin(shape, r) + 0.3, 16.0)), kr)
        if (rnd.nextBoolean()) b = b.copy(overhang = r3(HolderCheck.overhangMin(b, r) + rnd.nextDouble(0.5, 60.0)))
        if (rnd.nextBoolean()) b = b.copy(dMin = r3(rnd.nextDouble(dia, 2 * f + 4)))
        check(HolderCheck.problems(b, r).isEmpty()) { "무작위 바가 틀림: $b" }
        return b
    }
}

// ------------------------------------------------------------------------ 이 파일의 모형

private const val TOUCH = 0.005 // 닿음: 여유 < −0.005 mm (설계 문서의 정의)
private const val OUT_STEP = 0.005 // 공구 윤곽 표본(mm)
private const val PATH_STEP = 0.05 // 경로 표본(mm)
private const val FINE_STEP = 0.01 // 처음 닿는 곳 근처(mm)
private const val FAR = 1e5 // 반직선 · 바 뒤가 끝없이 갈 때(mm)
private const val NEAR = 1e-3 // 면 ± 이만큼은 두 쪽 가운데 소재가 적은 쪽 — NC 글의 0.001 반올림으로 면에 붙은 자세가 1e-4 들어가 보인다
private const val Z_BACK = 1e-3 // Z 로 이만큼까지 되돌아가는 것은 반올림(CAD 3점호)
private const val UPRIGHT = 2e-3 // 반직선 방향의 z 성분이 이보다 작으면 수직(입구 면)
private const val BLOCK = 128
private val INF = Double.POSITIVE_INFINITY
private val PARTS = HolderPart.entries
private val NOSE_RS = listOf(0.0, 0.2, 0.4, 0.8)

/** 화면 프리셋 A16R-SCLCR 09 (스크린샷 · 골든의 바): Ø16 · f 11 · C 9(품번 크기 숫자) · κr 95° · DMIN 20 */
private val A16 = BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, dMin = 20.0)

private fun r3(v: Double) = (v * 1000).roundToLong() / 1000.0

private fun fmt(v: Double) = ((v * 10000).roundToLong() / 10000.0).toString()

private fun toEl(s: Seg): El = when (s) {
    is Line -> Ln(V(s.start.z, s.start.x), V(s.end.z, s.end.x))
    is Arc -> Ac(V(s.start.z, s.start.x), V(s.end.z, s.end.x), V(s.center.z, s.center.x), s.cw)
}

/** Haas 표(설계 문서): 노즈 중심 → 가상 인선 */
private fun tipV(tip: Int, r: Double): V = when (tip) {
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

private fun pointAt(segs: List<El>, s: Double): V {
    var left = s
    for (e in segs) {
        if (left <= e.length) return e.at(if (e.length == 0.0) 0.0 else left / e.length)
        left -= e.length
    }
    return segs.last().b
}

/** z 로 정렬한 점들(노즈 중심 기준) */
private class Pts(pts: List<V>) {
    private val sorted = pts.sortedBy { it.z }
    val z = DoubleArray(sorted.size) { sorted[it].z }
    val x = DoubleArray(sorted.size) { sorted[it].x }
}

/**
 * 구멍 반경 R(z), 소재 = {|x| ≥ R(z)}. 늘인 윤곽 = 시작 반직선(입구 면을 모를 때 −시작 접선) + 도면 + 끝 반직선(끝이 축 위가 아니면 끝 접선).
 * 반직선은 축에서 멈추고, z 성분이 아주 작으면 수직(면)으로 본다. 가장 큰 z 너머는 공기, 깊은 끝이 축 위면 그 너머는 소재
 */
private class VBore(drawing: List<El>, faceAtStart: Boolean) {
    val reverse = drawing.last().b.z > drawing.first().a.z
    val monotone: Boolean
    val zLo: Double
    val zHi: Double
    private val solidBelow: Boolean

    /** 공구대가 닿는 입구 면 z(모르면 null) */
    val face: Double?
    // 수직이 아닌 요소, z 순: [z0, z1] 에서 직선은 x = p + q(z − o), 원호는 x = p + s√(q − (z − o)²) (o, p = 중심, q = R²)
    private val z0: DoubleArray
    private val z1: DoubleArray
    private val round: BooleanArray
    private val o: DoubleArray
    private val pp: DoubleArray
    private val q: DoubleArray
    private val sg: DoubleArray

    /** ρ 가 뛰는 꺾임점(면 · 입구 · 막힌 바닥) */
    private val faces: DoubleArray

    // 바 띠의 긴 변용 격자: 도면 z 범위 ±1 을 OUT_STEP 마다, BLOCK 칸마다 최솟값
    private val g0: Double
    private val gv: DoubleArray
    private val gb: DoubleArray

    init {
        val first = drawing.first()
        val last = drawing.last()
        val head = if (faceAtStart) null else ray(first.a, first.tangent(0.0) * -1.0)?.let { Ln(it, first.a) }
        val tail = if (last.b.x > 1e-9) ray(last.b, last.tangent(1.0))?.let { Ln(last.b, it) } else null
        val ext = listOfNotNull(head) + drawing + listOfNotNull(tail)
        val dir = if (reverse) 1.0 else -1.0
        monotone = ext.all { e ->
            val zs = (0..200).map { e.at(it / 200.0).z }
            (e.b.z - e.a.z) * dir >= -Z_BACK && zs.max() <= max(e.a.z, e.b.z) + Z_BACK && zs.min() >= min(e.a.z, e.b.z) - Z_BACK
        }
        val ends = listOf(ext.first().a, ext.last().b)
        zLo = ends.minOf { it.z }
        zHi = ends.maxOf { it.z }
        val deep = ends.minBy { it.z }
        val mouth = ends.maxBy { it.z }
        solidBelow = deep.x <= 1e-9
        val mouthRay = if (mouth === ext.first().a) head else tail
        face = when {
            faceAtStart -> first.a.z
            mouth.x <= 1e-9 || mouthRay == null || abs(mouthRay.a.z - mouthRay.b.z) <= 1e-12 -> zHi
            else -> null
        }
        val pieces = ext.filter { abs(it.b.z - it.a.z) > 1e-9 }.sortedBy { min(it.a.z, it.b.z) }
        z0 = DoubleArray(pieces.size) { min(pieces[it].a.z, pieces[it].b.z) }
        z1 = DoubleArray(pieces.size) { max(pieces[it].a.z, pieces[it].b.z) }
        round = BooleanArray(pieces.size) { pieces[it] is Ac }
        o = DoubleArray(pieces.size) { (pieces[it] as? Ac)?.c?.z ?: pieces[it].a.z }
        pp = DoubleArray(pieces.size) { (pieces[it] as? Ac)?.c?.x ?: pieces[it].a.x }
        q = DoubleArray(pieces.size) { k ->
            when (val e = pieces[k]) {
                is Ac -> e.r * e.r
                is Ln -> (e.b.x - e.a.x) / (e.b.z - e.a.z)
            }
        }
        sg = DoubleArray(pieces.size) { (pieces[it] as? Ac)?.let { a -> if (a.at(0.5).x >= a.c.x) 1.0 else -1.0 } ?: 0.0 }
        val breaks = (z0.toList() + z1.toList() + zLo + zHi).sorted().distinct()
        faces = breaks.filter { b ->
            val l = raw(b - 1e-6)
            val u = raw(b + 1e-6)
            !(l.isFinite() && u.isFinite()) || abs(l - u) > 1e-3
        }.toDoubleArray()
        val dz = drawing.flatMap { listOf(it.a.z, it.b.z) }
        g0 = dz.min() - 1
        val n = ceil((dz.max() + 1 - g0) / OUT_STEP).toInt()
        gv = DoubleArray(n + 1) { r(g0 + it * OUT_STEP) }
        gb = DoubleArray(n / BLOCK + 1) { b -> (b * BLOCK..min(n, b * BLOCK + BLOCK - 1)).minOf { gv[it] } }
    }

    private fun ray(p: V, d0: V): V? {
        val d = if (abs(d0.z) <= UPRIGHT) V(0.0, if (d0.x >= 0) 1.0 else -1.0) else d0
        if (d.x >= 0) return p + d * FAR
        val len = min(FAR, p.x / -d.x)
        if (len <= 1e-12) return null
        return if (len < FAR) V(p.z + d.z * len, 0.0) else p + d * len
    }

    private fun xOn(k: Int, z: Double): Double {
        val d = z - o[k]
        return if (round[k]) pp[k] + sg[k] * sqrt(max(0.0, q[k] - d * d)) else pp[k] + q[k] * d
    }

    /** R(z) 그대로(면 위에서는 아래 · 위 가운데 이분 탐색이 고른 쪽) */
    fun raw(z: Double): Double {
        if (z > zHi) return INF
        if (z < zLo) return if (solidBelow) 0.0 else INF
        var lo = 0
        var hi = z0.size - 1
        while (lo < hi) {
            val m = (lo + hi) / 2
            if (z1[m] < z) lo = m + 1 else hi = m
        }
        return if (z0[lo] <= z && z <= z1[lo]) xOn(lo, z) else INF
    }

    /** 면(ρ 가 뛰는 곳)까지 거리 */
    fun nearFace(z: Double): Double = faces.minOfOrNull { abs(it - z) } ?: INF

    /** R(z) — 면 ±[NEAR] 안이면 그 점과 두 쪽 가운데 가장 큰 것(소재가 적은 쪽) */
    fun r(z: Double): Double {
        val f = faces.minByOrNull { abs(it - z) } ?: return raw(z)
        return if (abs(f - z) < NEAR) maxOf(raw(z), raw(f - NEAR), raw(f + NEAR)) else raw(z)
    }

    fun slope(z: Double): Double {
        val a = raw(z - 1e-4)
        val b = raw(z + 1e-4)
        return if (a.isFinite() && b.isFinite()) (b - a) / 2e-4 else INF
    }

    /** 노즈 중심 c 에서 점들 가운데 가장 작은 R(z) − |x| (정렬한 z 를 따라 요소를 넘긴다) */
    fun minGap(p: Pts, c: V): Double {
        var m = INF
        var k = 0
        var bi = 0
        val zs = p.z
        val xs = p.x
        for (i in zs.indices) {
            val z = zs[i] + c.z
            if (z > zHi + NEAR) break
            while (bi < faces.size && faces[bi] < z - NEAR) bi++
            val rr = when {
                bi < faces.size && faces[bi] <= z + NEAR -> r(z)
                z < zLo -> if (solidBelow) 0.0 else INF
                else -> {
                    while (k < z0.size - 1 && z1[k] < z) k++
                    if (z0[k] <= z && z <= z1[k]) xOn(k, z) else INF
                }
            }
            val g = rr - abs(xs[i] + c.x)
            if (g < m) m = g
        }
        return m
    }

    /** x 가 [x] 로 일정한 변 z ∈ [za, zb] 의 가장 작은 R(z) − |x|: 격자 점 + 끝점 + 격자 · 반직선 끝(그 너머 R 은 직선이라 끝에서 최소) */
    fun lineMin(za: Double, zb: Double, x: Double): Double {
        if (zb < za) return INF
        var m = INF
        val ax = abs(x)
        for (z in doubleArrayOf(za, zb, g0, g0 + (gv.size - 1) * OUT_STEP, zLo + 2 * NEAR, zLo - 2 * NEAR, zHi - 2 * NEAR)) {
            if (z < za || z > zb) continue
            val rr = r(z)
            if (rr < INF) m = min(m, rr - ax)
        }
        val i0 = max(0, ceil((za - g0) / OUT_STEP).toInt())
        val i1 = min(gv.size - 1, floor((zb - g0) / OUT_STEP).toInt())
        var i = i0
        while (i <= i1) {
            if (i % BLOCK == 0 && i + BLOCK - 1 <= i1) {
                m = min(m, gb[i / BLOCK] - ax)
                i += BLOCK
            } else {
                m = min(m, gv[i] - ax)
                i++
            }
        }
        return m
    }
}

/** 이 파일의 보링바 단면(노즈 중심 기준). 표본 점은 인서트(띠 밖 + 자른 선)와 바 앞 변, 긴 위 · 아래 변은 [VBore.lineMin] 으로 */
private class VTool(bar: BoringBar, private val r: Double, private val reverse: Boolean) {
    private val shape: List<V>
    private val noseFrom: Double
    private val noseSweep: Double
    val insert: Pts
    val front: Pts
    val top: Double
    val bottom: Double
    val frontTop: V
    val frontBottom: V
    val refZ = if (reverse) r else -r

    /** 바 뒤(공구대 면) z, 없으면 FAR */
    val back: Double

    /** DMIN 구간 z(인선 ~ 인서트 끝) */
    val zoneLo: Double
    val zoneHi: Double

    /** 띠가 꽉 찬 자루 구간이 시작하는 z, 띠 가운데 x · 반지름 */
    val shankFrom: Double
    val barMid: Double
    val barHalf = bar.dia / 2

    init {
        val k = bar.kappaDeg * PI / 180
        val e = bar.shape.epsDeg * PI / 180
        val l = bar.edge
        val phi = k - PI
        fun rot(p: V) = V(p.z * cos(phi) - p.x * sin(phi), p.z * sin(phi) + p.x * cos(phi))
        val toNose = rot(V(cos(e / 2), sin(e / 2)) * (r / sin(e / 2)))
        fun place(p: V): V {
            val q = rot(p) - toNose
            return if (reverse) V(-q.z, q.x) else q
        }
        val t = r / tan(e / 2)
        val ta = place(V(t, 0.0))
        val tb = place(V(t * cos(e), t * sin(e)))
        val a = place(V(l, 0.0))
        val b = place(V(l * cos(e), l * sin(e)))
        val far = if (bar.shape == InsertShape.T) null else place(V(l + l * cos(e), l * sin(e)))
        shape = listOfNotNull(ta, a, far, b, tb)
        val corner = place(V(0.0, 0.0))
        noseFrom = angOf(tb)
        val sweep = ((angOf(ta) - noseFrom) % (2 * PI) + 2 * PI) % (2 * PI)
        // 노즈 원호는 꼭짓점 쪽(바깥)으로 돈다
        noseSweep = if (cos(noseFrom + sweep / 2 - angOf(corner)) > 0) sweep else sweep - 2 * PI
        barMid = r - bar.f
        top = barMid + barHalf
        bottom = barMid - barHalf
        val edges = shape.zipWithNext()
        val ins = mutableListOf<V>()
        for ((p, q) in edges) for (u in samples(p, q)) if (u.x > top || u.x < bottom) ins += u
        for (level in listOf(top, bottom)) {
            val zs = edges.mapNotNull { (p, q) ->
                if ((p.x - level) * (q.x - level) <= 0 && p.x != q.x) p.z + (q.z - p.z) * (level - p.x) / (q.x - p.x) else null
            }
            if (zs.size >= 2 && (level == top || shape.any { it.x < bottom })) ins += samples(V(zs.min(), level), V(zs.max(), level))
        }
        insert = Pts(ins)
        if (reverse) {
            val zf = shape.minOf { it.z }
            frontTop = V(zf, top)
            frontBottom = V(zf, bottom)
        } else {
            val d = rot(V(1.0, 0.0))
            frontTop = corner + d * ((top - corner.x) / d.x)
            frontBottom = corner + d * ((bottom - corner.x) / d.x)
        }
        front = Pts(samples(frontBottom, frontTop))
        back = bar.overhang?.let { refZ + it } ?: FAR
        zoneLo = if (reverse) shape.minOf { it.z } else refZ
        zoneHi = if (reverse) refZ else shape.maxOf { it.z }
        shankFrom = max(frontTop.z, frontBottom.z)
    }

    private fun samples(p: V, q: V): List<V> {
        val n = max(1, ceil((q - p).len / OUT_STEP).toInt())
        return (0..n).map { p + (q - p) * (it.toDouble() / n) }
    }

    /** 둥근 인서트(노즈 원호 + 날)의 가상 인선 쪽 끝: (정방향 가장 작은 z · 뒤보링 가장 큰 z, 가장 큰 x) */
    fun extreme(): Pair<Double, Double> {
        // 원호 위 각: 3600 등분과 그 안에 드는 0 · 90 · 180 · 270°
        fun onArc(a: Double) = (((a - noseFrom) * (if (noseSweep < 0) -1 else 1)) % (2 * PI) + 2 * PI) % (2 * PI) <= abs(noseSweep)
        val arc = (0..3600).map { noseFrom + noseSweep * it / 3600 } + listOf(0.0, PI / 2, PI, 1.5 * PI).filter(::onArc)
        val pts = shape + arc.map { V(r * cos(it), r * sin(it)) }
        return (if (reverse) pts.maxOf { it.z } else pts.minOf { it.z }) to pts.maxOf { it.x }
    }
}

private fun angOf(v: V) = kotlin.math.atan2(v.x, v.z)

/** 표본 여유(INSERT, BAR, TURRET, MIN_BORE 순) */
private fun vGaps(t: VTool, b: VBore, c: V, bar: BoringBar): DoubleArray {
    val ins = b.minGap(t.insert, c)
    val backZ = c.z + t.back
    val barEnd = min(backZ, b.zHi)
    var body = b.minGap(t.front, c)
    body = min(body, b.lineMin(c.z + t.frontTop.z, barEnd, c.x + t.top))
    body = min(body, b.lineMin(c.z + t.frontBottom.z, barEnd, c.x + t.bottom))
    if (backZ <= b.zHi) body = min(body, b.r(backZ) - max(abs(c.x + t.top), abs(c.x + t.bottom)))
    val turret = if (bar.overhang != null && b.face != null) c.z + t.refZ + bar.overhang - b.face else INF
    val minBore = bar.dMin?.let { b.lineMin(c.z + t.zoneLo, c.z + t.zoneHi, 0.0) - it / 2 } ?: INF
    return doubleArrayOf(ins, body, turret, minBore)
}

/** 여러 사례를 모은 셈 */
private class Stats {
    var poses = 0
    var qualified = 0
    var hits = 0
    var cylinder = 0

    fun line() = "노즈 밖 최솟점 자세 · 부분 $poses (V3 대상 $qualified), 닿음 $hits, 원기둥 대조 $cylinder"

    fun require(rep: Report) {
        if (qualified < 0.7 * poses) rep.bad("전체", "V3", "V3 대상이 ${qualified * 100 / max(1, poses)} % — 70 % 아래")
        if (hits == 0 || cylinder == 0) rep.bad("전체", "범위", "닿음 $hits · 원기둥 대조 $cylinder — 검사가 비었다")
    }
}
