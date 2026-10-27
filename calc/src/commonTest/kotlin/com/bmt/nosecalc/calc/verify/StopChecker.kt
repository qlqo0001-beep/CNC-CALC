package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Bore
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderCut
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderPlan
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.Intersect
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Need
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RetractKind
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.StopJob
import com.bmt.nosecalc.calc.StopWarning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 안전 끝점 · 잘린 NC 검사기. 구현의 경로 계산(볼록 껍질 · 반올림 되돌리기 · 제어기 보정 모형)을 쓰지 않고 따로 짠다:
 * - 경로: 프로그램 요소를 이 파일에서 따로 따라가며 [step] mm 마다 자세를 찍는다(점마다 여유)
 * - NC: 이 파일의 글 읽기(G40 · 모달 · R 원호)로 다시 읽는다. 기계 보정 글은 이 파일의 제어기 모형으로 노즈 경로를 만든다 —
 *   블록마다 공구 쪽으로 r 민 보정선, 이웃 보정선은 교점(볼록은 늘인 교점, 거의 접하면 가운데)에서 잇고, G40 앞 블록 끝은 법선 보정,
 *   G40 블록은 그 자리에서 끝점(가상 인선)으로 곧게
 * 여유 식 자체는 HolderCheck 그대로다(그 식은 HolderVerifyTest 의 V2~V5 가 표본으로 따로 검증한다)
 */
internal class StopChecker(
    val sol: RoundSolution,
    val drawn: RoundSolution?,
    val r: Double,
    private val face: Boolean,
    bar: BoringBar,
    val safe: Double,
) {
    private val job = StopJob(sol, drawn, r, Bore(sol.drawing, face), bar, safe)
    private val tv = NoseComp.tipVector(sol.tip, r)
    private val cut = Need(safe, -HolderStop.CUT_TOL)
    /** 쓴 글: 바 · 공구대 · 최소 가공경 · 인서트 맞은편 ≥ 안전 여유 − 1e-4. 인서트 깎는 쪽(날)은 반올림만큼 벽에 들어갈 수 있어 보지 않는다 */
    private val text = Need(safe - TEXT, Double.NEGATIVE_INFINITY)

    /** 경로(가상 인선): 진입 이동 + 프로그램 요소 */
    private val path: List<Pair<Int, Seg>> = (listOf<Pair<Int, Seg>>(-1 to Line(sol.entry, sol.program.segs.first().start)) +
        sol.program.segs.withIndex().map { it.index to it.value }).filter { len(it.second) > 1e-9 }
    private val cum = DoubleArray(path.size + 1).also { c -> for (i in path.indices) c[i + 1] = c[i] + len(path[i].second) }

    /**
     * 노즈 자세의 여유 — 깎는 면 예외(구현의 [StopJob.clear] 와 같은 요소 규칙)는 그 자세가 놓인 경로 요소로: [k] 를 주지 않으면 노즈 중심이
     * 가장 가까운 노즈 경로 요소(진입 이동이면 −1 — 예외 없음)
     */
    fun sigma(nose: Pt, need: Need = cut, k: Int = elementNear(nose)): Double = job.clear(listOf(nose), k).slack(need)

    /** 노즈 중심에서 가장 가까운 노즈 경로 요소(경로 위 자세면 그 요소, 둘이 같으면 뒤 요소 — 경로 길이 순 마지막) */
    fun elementNear(nose: Pt): Int {
        var best = Double.POSITIVE_INFINITY
        var k = -1
        for ((e, s) in path) {
            val d = dist(s.moved(tv * -1.0), nose)
            if (d <= best + 1e-12) {
                best = min(best, d)
                k = e
            }
        }
        return k
    }

    /** 경로 길이 s 의 요소 */
    fun elementAt(s: Double): Int = path[(0 until path.size).firstOrNull { s <= cum[it + 1] } ?: (path.size - 1)].first

    /** 요소 k 비율 t 의 경로 길이(−2 = 0) */
    fun pos(k: Int, t: Double): Double {
        if (k == -2) return 0.0
        val i = path.indexOfFirst { it.first == k }
        if (i < 0) return cum[path.indexOfFirst { it.first > k }.let { if (it < 0) path.size else it }]
        return cum[i] + t * len(path[i].second)
    }

    /** 경로 길이 s 의 노즈 중심 */
    fun noseAt(s: Double): Pt {
        val i = (0 until path.size).firstOrNull { s <= cum[it + 1] } ?: (path.size - 1)
        return at(path[i].second, ((s - cum[i]) / len(path[i].second)).coerceIn(0.0, 1.0)) - tv
    }

    val total get() = cum[path.size]

    /** 검사. 틀린 것을 [fails] 에 넣는다 */
    fun check(plan: HolderPlan, step: Double, fails: MutableList<String>) {
        fun bad(m: String) {
            fails += m
        }
        // 깎는 면 사슬(앞 요소 면)은 계획의 형상 끝점 요소까지 — 끝내 깎지 않는 면은 예외가 아니다(10-28 검증). 아래 '0.001 mm 더 간 곳도 안전' 이
        // 그 끝점이 이 기준의 첫 위반인지 본다
        job.faceLimit = listOfNotNull(plan.end, plan.machineEnd).firstOrNull { it.element >= 0 }?.element ?: Int.MAX_VALUE
        checkMachineProgram(plan, step, ::bad)
        val tight = listOfNotNull(plan.direct, plan.machine).any { it.retractClear < safe - TEXT }
        if (tight != (StopWarning.RETRACT_TIGHT in plan.warnings)) bad("RETRACT_TIGHT ${plan.warnings} 인데 후퇴 여유 ${plan.direct?.retractClear} · ${plan.machine?.retractClear}")
        val end = plan.end
        val firstHit = plan.result.hits.minOfOrNull { pos(it.element, it.fromT) }
        if (end == null) {
            if (StopWarning.UNSAFE_AT_ENTRY in plan.warnings) {
                if (sigma(sol.entry - tv, k = -2) >= 0) bad("진입점이 안전한데 UNSAFE_AT_ENTRY")
                return
            }
            // 경로 전체가 안전해야
            val n = ceil(total / step).toInt()
            for (q in 0..n) if (sigma(noseAt(total * q / n), k = elementAt(total * q / n)) < -1e-9) return bad("끝점이 없는데 ${total * q / n} mm 에서 안전 기준이 모자람")
            if (firstHit != null) bad("닿는 곳이 있는데 끝점 없음")
            return
        }
        val sEnd = pos(end.element, end.t)
        // 1. 끝점까지 모든 자세가 안전, 끝점 여유 = 기준(면을 넘는 순간이면 그 바로 뒤에 기준을 넘는다), 0.001 mm 더 가면 모자람
        val n = max(1, ceil(sEnd / step).toInt())
        for (q in 0..n) {
            val s = sEnd * q / n
            if (sigma(noseAt(s), k = elementAt(s)) < -1e-9) return bad("끝점 앞 ${s} mm(끝 $sEnd)에서 안전 기준이 모자람 ${sigma(noseAt(s), k = elementAt(s))}")
        }
        if (sigma(sol.entry - tv, k = -2) < -1e-9) bad("진입점 자세가 안전하지 않은데 끝점이 있음")
        if ((noseAt(sEnd) - end.pose.nose).length > 1e-9) bad("끝점 자세 ${end.pose.nose} ≠ 경로 ${noseAt(sEnd)}")
        val thr = if (end.part == HolderPart.INSERT && !end.opposite) 0.0 else safe
        val here = job.clear(listOf(end.pose.nose), end.element).margin(end.part, end.opposite)
        if (abs(here - end.margin) > 1e-12) bad("끝점 여유 ${end.margin} ≠ 다시 잰 $here")
        if (abs(here - thr) > 1e-5) {
            val sn = min(total, sEnd + 2e-7)
            val next = job.clear(listOf(noseAt(sn)), elementAt(sn)).margin(end.part, end.opposite)
            if (next >= thr) bad("끝점 ${end.part}${if (end.opposite) "(맞은편)" else ""} 여유 $here ≠ 기준 $thr 이고 바로 뒤도 $next")
        }
        if (sEnd + 1e-3 <= total && sigma(noseAt(sEnd + 1e-3), k = elementAt(sEnd + 1e-3)) >= 0) bad("끝점에서 0.001 mm 더 간 곳도 안전 ${sigma(noseAt(sEnd + 1e-3), k = elementAt(sEnd + 1e-3))}")
        if (firstHit != null && firstHit < sEnd) bad("첫 닿음 $firstHit 이 끝점 $sEnd 보다 앞")
        // 끝점의 기계 보정 좌표: 그 블록을 거기서 끝내면 끝 블록 법선 보정으로 노즈가 끝점 자세여야 한다(못 끝내면 null — 노즈가 닿는 도면 점으로 대신하지 않는다)
        val cmd = end.at.machine
        if (end.element >= 0 && cmd != null && drawn != null) {
            val de = drawn.program.segs[end.element]
            val nrm = when (de) {
                is Line -> (de.end - de.start).let { Pt(-it.x, it.z) * ((if (sol.side == Side.G41) 1.0 else -1.0) / it.length) }
                is Arc -> (cmd - de.center).let { it * ((if ((sol.side == Side.G41) == de.cw) 1.0 else -1.0) / it.length) }
            }
            if ((cmd + nrm * r - end.pose.nose).length > 1e-6) bad("끝점 기계 좌표 $cmd 에서 블록을 끝내면 노즈 ${cmd + nrm * r} ≠ 끝점 자세 ${end.pose.nose}")
        }
        // 2. 잘린 NC(직접 보정 — 기계 보정은 [checkMachineProgram])
        if (end.element < 0) {
            if (plan.direct != null) bad("진입 이동에서 멈췄는데 직접 보정 잘린 NC 가 있음")
            if (StopWarning.NOTHING_CUT !in plan.warnings) bad("진입 이동에서 멈췄는데 NOTHING_CUT 없음")
            return
        }
        val d = plan.direct
        if (d == null) {
            if (StopWarning.NOTHING_CUT !in plan.warnings) bad("직접 보정 잘린 NC 가 없는데 NOTHING_CUT 없음")
        } else {
            checkDirect(d, sEnd, step, ::bad)
        }
    }

    /**
     * 기계 보정 프로그램: 이 파일의 제어기 모형으로 진입을 따로 본다 — G00 점(G40 상태)의 노즈 = 도면 G00 점 − 팁 벡터, 보정 시작 블록(G00 다음 줄)
     * 동안 노즈는 다음 블록 보정선의 시작으로 곧게. 띄운 진입(진입 이동이 두 줄 — G00 → lead 직각 · lead → 첫 점)이면 둘째 줄 동안 노즈는 그 줄의
     * 보정선을 따라간다. 진입이 안전하면 끝점은 형상(직접 보정과 같은 노즈 경로)의 첫 위반, 아니면 진입 위의 그 자리
     */
    private fun checkMachineProgram(plan: HolderPlan, step: Double, bad: (String) -> Unit) {
        val dr = drawn
        val me = plan.machineEnd
        if (dr == null) {
            if (me != null || plan.machine != null || !plan.machineEntry.isNullOrEmpty()) bad("도면 좌표 풀이가 없는데 기계 보정 끝점 · 잘린 NC · 진입 닿음이 있음")
            return
        }
        val n0 = dr.entry - tv
        val bl = readText(dr.nc)
        val entryLines = dr.ncRefs.indices.filter { dr.ncRefs[it].element == -1 }
        val firstShape = dr.program.segs.first { len(it) > 1e-9 }
        // 진입 블록: (줄, 명령 시작 · 끝, 노즈 시작 · 끝)
        class EB(val line: Int, val a: Pt, val b: Pt, val na: Pt, val nb: Pt) {
            fun nose(u: Double) = na + (nb - na) * u
            fun cmd(u: Double) = a + (b - a) * u
            val l = (nb - na).length
        }
        // 명령점은 풀이의 정확한 점(글은 0.001 로 반올림됐다 — 그 차이는 아래 잘린 NC 검사가 쓴 글로 본다)
        val start = dr.program.segs.first().start
        val lead = dr.lead
        if (entryLines.size > 1 && (lead == null || (bl[entryLines[0]].seg!!.end - lead).length > 1e-3)) bad("기계 보정 진입이 두 줄인데 lead $lead 가 첫 줄 끝이 아님")
        val blocks = if (entryLines.size > 1 && lead != null) {
            val along = offset(Line(lead, start))
            listOf(EB(entryLines[0], dr.entry, lead, n0, along.start), EB(entryLines[1], lead, start, along.start, along.end))
        } else {
            listOf(EB(entryLines.firstOrNull() ?: 1, dr.entry, start, n0, offset(firstShape).start))
        }
        // 진입 자세: 부분 여유 기준(진입이라 깎는 면 예외 없음) + 노즈 원이 소재에 들어가지 않음(중심이 소재 안 · 이 파일의 경계 거리).
        // 중심이 경계 위면 거리 쪽이 본다(노즈R 0 은 벽 위가 된다)
        val entryBore = Bore(sol.drawing, face)
        fun ok(c: Pt) = sigma(c, k = -1) >= -1e-9 && abs(c.x) <= entryBore.rho(c.z) + 1e-9 && noseGap(c) >= -1e-6
        val claimed = me != null && me.element < 0
        if (claimed != (StopWarning.MACHINE_ENTRY in plan.warnings)) bad("MACHINE_ENTRY ${plan.warnings} 인데 기계 보정 끝점 요소 ${me?.element}")
        if (me != null && me.element == -2) {
            if (ok(n0)) bad("기계 보정 G00 점 자세가 안전한데 끝점 −2")
            if ((me.pose.nose - n0).length > 1e-9 || (me.at.machine!! - dr.entry).length > 1e-9) bad("기계 보정 G00 끝점 자세 ${me.pose.nose} · ${me.at.machine} ≠ $n0 · ${dr.entry}")
        } else {
            if (!ok(n0)) bad("기계 보정 G00 점 자세가 안전하지 않은데 끝점 ${me?.element}")
            for (b in blocks) {
                val here = me != null && me.element == -1 && me.at.machineLine == b.line
                val uEnd = if (here) me!!.t else 1.0
                val n = max(1, ceil(b.l * uEnd / step).toInt())
                for (q in 0..n) {
                    val u = uEnd * q / n
                    if (!ok(b.nose(u))) return bad("기계 보정 진입 ${b.line + 1}줄 $u 에서 안전 기준이 모자람(끝점 ${me?.element} ${me?.t} ${me?.at?.machineLine})")
                }
                if (here) {
                    if ((me!!.pose.nose - b.nose(me.t)).length > 1e-9) bad("기계 보정 진입 끝점 노즈 ${me.pose.nose} ≠ ${b.nose(me.t)}")
                    if ((me.at.machine!! - b.cmd(me.t)).length > 1e-9) bad("기계 보정 진입 끝점 명령점 ${me.at.machine} ≠ ${b.cmd(me.t)}")
                    if (b.l > 0 && me.t + 1e-3 / b.l <= 1 && ok(b.nose(me.t + 1e-3 / b.l))) bad("기계 보정 진입 끝점에서 0.001 mm 더 간 곳도 안전")
                    break
                }
            }
            if (me != null && me.element == -1 && blocks.none { it.line == me.at.machineLine }) bad("기계 보정 진입 끝점 줄 ${me.at.machineLine} 이 진입 블록이 아님")
        }
        if (claimed) {
            if (plan.machine != null) bad("기계 보정 진입에서 멈췄는데 잘린 NC 가 있음")
            return
        }
        // 진입이 되면 형상의 첫 위반: 직접 보정 끝점이 형상 위면 같은 곳, 형상 전체가 안전하면 없음
        val end = plan.end
        if (end != null && end.element >= 0 && me != end) bad("기계 보정 끝점 ${me?.element} ${me?.t} ≠ 직접 보정 형상 끝점 ${end.element} ${end.t}")
        if (me == null) {
            val s0 = pos(0, 0.0)
            val nn = ceil((total - s0) / step).toInt()
            for (q in 0..nn) {
                val s = s0 + (total - s0) * q / max(1, nn)
                if (sigma(noseAt(s), k = elementAt(s)) < -1e-9) return bad("기계 보정 끝점이 없는데 형상 $s mm 에서 안전 기준이 모자람")
            }
            if (plan.machine != null) bad("기계 보정 끝점이 없는데 잘린 NC 가 있음")
            return
        }
        val sM = pos(me.element, me.t)
        if (end == null || end.element < 0) {
            // 직접 보정은 진입에서 멈췄다: 형상 끝점을 따로 — 형상 처음부터 끝점까지 안전, 0.001 mm 더 가면 모자람
            val s0 = pos(0, 0.0)
            val nn = max(1, ceil((sM - s0) / step).toInt())
            for (q in 0..nn) {
                val s = s0 + (sM - s0) * q / nn
                if (sigma(noseAt(s), k = elementAt(s)) < -1e-9) return bad("기계 보정 형상 끝점 앞 $s mm 에서 모자람")
            }
            if (sM + 1e-3 <= total && sigma(noseAt(sM + 1e-3), k = elementAt(sM + 1e-3)) >= 0) bad("기계 보정 형상 끝점에서 0.001 mm 더 간 곳도 안전")
        }
        val m = plan.machine ?: return
        checkMachine(m, sM, step, bad)
    }

    private fun checkDirect(c: HolderCut, sEnd: Double, step: Double, bad: (String) -> Unit) {
        if (c.nc.take(c.kept) != sol.nc.take(c.kept)) bad("직접 보정 앞 ${c.kept} 줄이 원래와 다르다: ${c.nc}")
        val read = readText(c.nc)
        // 마지막 블록 끝 = stop, 경로를 따라 안전 끝점을 넘지 않는다
        val stop = read[c.retractFrom - 1].seg!!.end
        if ((stop - c.stop).length > 1e-12) bad("직접 보정 stop ${c.stop} ≠ 글 $stop")
        val sStop = pos(c.element, projT(c.element, stop))
        if (sStop > sEnd + 1e-9) bad("직접 보정 마지막 블록 끝이 안전 끝점을 넘음: $sStop > $sEnd")
        if (sEnd - sStop > 0.05) bad("직접 보정이 안전 끝점보다 ${sEnd - sStop} mm 앞에서 멈춤")
        if (abs(sEnd - sStop - c.lack) > 1e-6) bad("직접 보정 덜 간 길이 ${c.lack} ≠ ${sEnd - sStop}")
        val motions = (1 until read.size).mapNotNull { i -> read[i].seg?.let { i to Seg2(it.moved(tv * -1.0)) } }
        sweepMotions(motions, c, step, bad, "직접 보정") { i -> i >= c.retractFrom }
    }

    private fun checkMachine(c: HolderCut, sEnd: Double, step: Double, bad: (String) -> Unit) {
        val dr = drawn!!
        if (c.nc.take(c.kept) != dr.nc.take(c.kept)) bad("기계 보정 앞 ${c.kept} 줄이 원래와 다르다: ${c.nc}")
        if (!c.nc[c.retractFrom].startsWith("G40")) bad("기계 보정 첫 후퇴 줄에 G40 없음: ${c.nc[c.retractFrom]}")
        if (c.nc.take(c.retractFrom).any { it.contains("G40") }) bad("기계 보정 깎는 줄에 G40")
        val read = readText(c.nc)
        // 제어기 모형: 첫 가공 요소 줄부터 G40 앞까지 보정
        val first = dr.ncRefs.indexOfFirst { it.element >= 0 }
        val blocks = (first until c.retractFrom).map { read[it].seg!! }
        // 오목 R ≤ r(보정 원호가 없어짐 — 제어기는 간섭 알람)인 블록을 이 모형으로 이을 수 없다: 그 앞뒤는 건너뛴다
        val lastFrom0 = max(0, c.kept - first - 1)
        val gone = blocks.indices.filter { (blocks[it] as? Arc)?.let { a -> (sol.side == Side.G41) != a.cw && (a.start - a.center).length <= r + 1e-9 } == true }
        if (gone.any { it >= lastFrom0 - 1 }) {
            skippedMachine++
            return
        }
        // 그런 블록이 없으면 첫 가공 블록부터 끝까지(앞 블록의 반올림도) 이 모형으로 본다
        val modelFrom = gone.maxOrNull()?.let { it + 2 } ?: 0
        val offs = blocks.map { offset(it) }
        val joints = mutableListOf(offs.first().start)
        for (i in 0 until offs.size - 1) joints += join(offs[i], offs[i + 1])
        joints += offs.last().end
        val noses = offs.indices.map { i ->
            val o = offs[i]
            when (o) {
                is Line -> Line(joints[i], joints[i + 1])
                is Arc -> Arc(joints[i], joints[i + 1], o.center, o.cw)
            }
        }
        val endNose = joints.last()
        if ((endNose - c.pose.nose).length > 1e-6) bad("기계 보정 블록 끝 노즈 $endNose ≠ 계산기 ${c.pose.nose}")
        val sStop = pos(c.element, projT(c.element, endNose + tv))
        if (sStop > sEnd + 1e-9) bad("기계 보정 마지막 블록 끝 노즈가 안전 끝점을 넘음: $sStop > $sEnd")
        if (abs(sEnd - sStop - c.lack) > 1e-6) bad("기계 보정 덜 간 길이 ${c.lack} ≠ ${sEnd - sStop}")
        // 깎는 동안: 다시 읽은 블록을 이 제어기 모형으로
        val motions = mutableListOf<Pair<Int, Seg2>>()
        for (i in modelFrom until noses.size) motions += (first + i) to Seg2(noses[i])
        var tip = endNose + tv
        for (i in c.retractFrom until read.size) {
            val s = read[i].seg ?: continue
            val m: Seg = if (read[i].g40) Line(tip, s.end) else s
            motions += i to Seg2(m.moved(tv * -1.0))
            tip = s.end
        }
        sweepMotions(motions, c, step, bad, "기계 보정") { i -> i >= c.retractFrom }
    }

    /**
     * 움직임을 [step] mm 마다: 깎는 동안 text 기준, 후퇴는 겹치지 않음(빼기 시작 블록의 인서트 깎는 쪽 · 거꾸로 후퇴는 날이 벽을 따라감).
     * X 빼기 · Z 후퇴는 노즈 원도: 도면(맞은편 포함)까지 거리 − r ≥ min(L, 간 길이/2) − 0.002, L = max(기준, 0.025) — 벽에 붙어 끌리지 않는다
     */
    private fun sweepMotions(ms: List<Pair<Int, Seg2>>, c: HolderCut, step: Double, bad: (String) -> Unit, name: String, isRetract: (Int) -> Boolean) {
        var low = Double.POSITIVE_INFINITY
        var travel = 0.0
        val level = if (c.retractClear >= safe - TEXT) safe else 0.0
        for ((i, m) in ms) {
            val retract = isRetract(i)
            val firstRetract = i == c.retractFrom
            val n = max(1, ceil(len(m.s) / min(step, 0.002)).toInt())
            for (q in 0..n) {
                val nose = at(m.s, q.toDouble() / n)
                if (retract && c.retract == RetractKind.LIFT) {
                    val gap = noseGap(nose)
                    val want = min(max(level, 0.025), (travel + len(m.s) * q / n) / 2) - 0.002
                    if (gap < want) return bad("$name 줄 $i 후퇴 노즈 $nose: 소재까지 $gap < $want (간 길이 ${travel + len(m.s) * q / n}) · ${c.nc}")
                }
                if (q % max(1, (step / 0.002).toInt()) != 0 && q != n) continue
                // 깎는 면 예외는 그 자세의 경로 요소(빼기 후퇴는 멈춘 요소 — 구현과 같다)
                val kk = if (retract && c.retract == RetractKind.LIFT) c.element else elementNear(nose)
                if (!retract) {
                    val sl = SHIFTS.maxOf { job.clear(listOf(nose + it), kk).slack(text) }
                    if (sl < -1e-9) return bad("$name 줄 $i: 깎는 자세 $nose 가 안전 여유 − 1e-4 아래 $sl · ${c.nc}")
                    continue
                }
                val exemptCut = c.retract == RetractKind.REVERSE || (firstRetract && q == 0) || (firstRetract && c.nc[i].contains("X") && !c.nc[i].startsWith("G00") && !c.nc[i].startsWith("G40 G00"))
                val need = if (c.retract == RetractKind.REVERSE) text else Need(-TEXT, if (exemptCut) Double.NEGATIVE_INFINITY else -TEXT)
                val sl = SHIFTS.maxOf { job.clear(listOf(nose + it), kk).slack(need) }
                if (sl < -1e-9) return bad("$name 줄 $i 후퇴 자세 $nose 가 겹침 $sl · ${c.nc}")
                low = min(low, SHIFTS.maxOf { job.clear(listOf(nose + it), kk).least(false) })
            }
            if (retract) travel += len(m.s)
        }
        if (low < c.retractClear - 1e-6) bad("$name 후퇴 여유 표본 $low < 계산기 ${c.retractClear}")
    }

    /** 소재 경계(이 파일 따로): 도면 + 입구 쪽(TYPE 은 입구 면 위로, 3점호는 시작 접선 거꾸로) · 깊은 끝(축 위가 아니면 끝 접선) 반직선 */
    private val wall: List<Seg> = run {
        val segs = sol.drawing.segs
        fun tan(s: Seg, end: Boolean): Pt = when (s) {
            is Line -> (s.end - s.start) * (1 / len(s))
            is Arc -> {
                val v = (if (end) s.end else s.start) - s.center
                Pt(-v.x, v.z) * ((if (s.cw) -1.0 else 1.0) / v.length)
            }
        }
        fun ray(p: Pt, d0: Pt): Seg? {
            val d = if (abs(d0.z) <= 2e-3) Pt(0.0, if (d0.x >= 0) 1.0 else -1.0) else d0
            val l = if (d.x < 0) min(1e3, p.x / -d.x) else 1e3
            return if (l <= 1e-9) null else Line(p, p + d * l)
        }
        val first = segs.first()
        val last = segs.last()
        val head = if (face) Line(first.start, first.start + Pt(0.0, 1e3)) else ray(first.start, tan(first, false) * -1.0)
        val tail = if (last.end.x > 1e-9) ray(last.end, tan(last, true)) else null
        listOfNotNull(head) + segs + listOfNotNull(tail)
    }

    /** 노즈 원과 도면(맞은편 벽 포함) 사이 거리 */
    private fun noseGap(c: Pt): Double {
        var d = Double.POSITIVE_INFINITY
        for (s in wall) for (p in listOf(c, Pt(c.z, -c.x))) {
            val q = when (s) {
                is Line -> {
                    val v = s.end - s.start
                    val t = (((p - s.start).let { it.z * v.z + it.x * v.x }) / (v.z * v.z + v.x * v.x)).coerceIn(0.0, 1.0)
                    (p - (s.start + v * t)).length
                }
                is Arc -> {
                    val sw = sweep(s)
                    var a = (if (s.cw) -1 else 1) * (ang(p - s.center) - ang(s.start - s.center))
                    a = ((a % (2 * PI)) + 2 * PI) % (2 * PI)
                    if (a <= sw) abs((p - s.center).length - (s.start - s.center).length) else min((p - s.start).length, (p - s.end).length)
                }
            }
            d = min(d, q)
        }
        return d - r
    }

    /** 공구 쪽으로 r 민 보정선(직선은 평행 이동, 원호는 볼록 R + r · 오목 R − r) */
    private fun offset(s: Seg): Seg = when (s) {
        is Line -> {
            val d = (s.end - s.start)
            val n = Pt(-d.x, d.z) * ((if (sol.side == Side.G41) 1.0 else -1.0) / d.length)
            Line(s.start + n * r, s.end + n * r)
        }
        is Arc -> {
            val out = (sol.side == Side.G41) == s.cw
            fun o(p: Pt) = s.center + (p - s.center) * ((s.radius + (if (out) r else -r)) / (p - s.center).length)
            Arc(o(s.start), o(s.end), s.center, s.cw)
        }
    }

    /** 이웃 보정선의 이음: 거의 이어지면 가운데, 아니면 두 선 · 원의 교점 가운데 두 끝의 가운데에 가까운 것 */
    private fun join(a: Seg, b: Seg): Pt {
        val mid = (a.end + b.start) * 0.5
        if ((a.end - b.start).length <= 1e-3) return mid
        val cand = when {
            a is Line && b is Line -> listOfNotNull(Intersect.lineLine(a.start, a.end - a.start, b.start, b.end - b.start))
            a is Line && b is Arc -> Intersect.lineCircle(a.start, a.end - a.start, b.center, (b.start - b.center).length)
            a is Arc && b is Line -> Intersect.lineCircle(b.start, b.end - b.start, a.center, (a.start - a.center).length)
            a is Arc && b is Arc -> Intersect.circleCircle(a.center, (a.start - a.center).length, b.center, (b.start - b.center).length)
            else -> emptyList()
        }
        return cand.minByOrNull { (it - mid).length } ?: mid
    }

    /** 경로 요소 k 위 점 p 의 비율(직선 투영, 원호 각) */
    private fun projT(k: Int, p: Pt): Double {
        val s = path.first { it.first == k }.second
        return when (s) {
            is Line -> ((p - s.start).let { it.z * (s.end - s.start).z + it.x * (s.end - s.start).x }) / (len(s) * len(s))
            is Arc -> {
                val sw = sweep(s)
                var a = (if (s.cw) -1 else 1) * (ang(p - s.center) - ang(s.start - s.center))
                a = ((a % (2 * PI)) + 2 * PI) % (2 * PI)
                (if (a > PI + sw / 2) a - 2 * PI else a) / sw
            }
        }
    }

    companion object {
        const val TEXT = 1e-4

        /**
         * 쓴 글 자세는 Z 를 반 칸(0.0005) 앞뒤로 옮긴 것 가운데 좋은 쪽: Z 반올림은 반경 여유로 재면 면에서 크게 뛴다(면에 붙어 깎는
         * 최소 가공경 구간 앞 끝이 0.0001 넘으면 막힌 바닥 너머 반경 0 을 본다). X 반올림은 그대로
         */
        val SHIFTS = listOf(Pt(0.0, 0.0), Pt(5e-4, 0.0), Pt(-5e-4, 0.0))

        /** 제어기 모형으로 볼 수 없어 건너뛴 기계 보정 수(오목 R ≤ r) */
        var skippedMachine = 0
    }
}

/** 움직임 하나(노즈 중심) */
internal class Seg2(val s: Seg)

/** 다시 읽은 줄: 움직임(첫 G00 은 null)과 G40 */
internal class ReadLine(val seg: Seg?, val g40: Boolean)

/** NC 글 읽기(구현과 따로): G00/G01 직선, G02/G03 끝점 R, 빠진 축은 앞 값, X 는 직경, G40 은 표시만 */
internal fun readText(lines: List<String>): List<ReadLine> {
    var z: Double? = null
    var x: Double? = null
    return lines.map { line ->
        val w = line.trim().split(" ").filter { it.isNotEmpty() }
        val g = w.filter { it.startsWith("G") }
        // G40 만 있는 줄(노즈R 0 의 보정 취소)은 움직임 없음
        val motion = g.lastOrNull { it != "G40" } ?: if (g == listOf("G40")) "G01" else error("움직임 G 가 없다: $line")
        fun v(c: Char) = w.firstOrNull { it[0] == c }?.substring(1)?.toDouble()
        val nz = v('Z') ?: z ?: error("Z 없음: $line")
        val nx = v('X')?.let { it / 2 } ?: x ?: error("X 없음: $line")
        val from = if (z != null && x != null) Pt(z!!, x!!) else null
        z = nz
        x = nx
        val to = Pt(nz, nx)
        val seg = when {
            from == null -> null
            motion == "G02" || motion == "G03" -> {
                val rr = v('R') ?: error("R 없음: $line")
                val ch = to - from
                val h = ch.length / 2
                val dd = sqrt(max(rr * rr - h * h, 0.0))
                val cw = motion == "G02"
                Arc(from, to, (from + to) * 0.5 + Pt(-ch.x, ch.z) * ((if (cw) -dd else dd) / ch.length), cw)
            }
            else -> Line(from, to)
        }
        ReadLine(seg, "G40" in g)
    }
}

private fun ang(v: Pt) = atan2(v.x, v.z)

private fun sweep(a: Arc): Double {
    if ((a.end - a.start).length < 1e-12) return 0.0
    val d = (if (a.cw) -1 else 1) * (ang(a.end - a.center) - ang(a.start - a.center))
    return ((d % (2 * PI)) + 2 * PI) % (2 * PI)
}

internal fun len(s: Seg): Double = when (s) {
    is Line -> (s.end - s.start).length
    is Arc -> sweep(s) * (s.start - s.center).length
}

/** 요소 위 비율 t 의 점(이 파일 따로) */
internal fun at(s: Seg, t: Double): Pt = when (s) {
    is Line -> s.start + (s.end - s.start) * t
    is Arc -> {
        val a = ang(s.start - s.center) + (if (s.cw) -1 else 1) * sweep(s) * t
        s.center + Pt(cos(a), sin(a)) * (s.start - s.center).length
    }
}

/** 점 p 에서 요소까지 거리(이 파일 따로) */
internal fun dist(s: Seg, p: Pt): Double = when (s) {
    is Line -> {
        val d = s.end - s.start
        val l2 = d.z * d.z + d.x * d.x
        val t = if (l2 == 0.0) 0.0 else ((((p - s.start).z * d.z) + (p - s.start).x * d.x) / l2).coerceIn(0.0, 1.0)
        (p - (s.start + d * t)).length
    }
    is Arc -> {
        val sw = sweep(s)
        var a = (if (s.cw) -1 else 1) * (ang(p - s.center) - ang(s.start - s.center))
        a = ((a % (2 * PI)) + 2 * PI) % (2 * PI)
        if (a <= sw) abs((p - s.center).length - (s.start - s.center).length) else min((p - s.start).length, (p - s.end).length)
    }
}

internal fun Seg.moved(v: Pt): Seg = when (this) {
    is Line -> Line(start + v, end + v)
    is Arc -> Arc(start + v, end + v, center + v, cw)
}

/** HolderCheck 의 닿음 경계: 처음 닿는 곳에서 닿고, 1e-6 mm 앞은 닿지 않는다 */
internal fun contactEdge(sol: RoundSolution, r: Double, face: Boolean, bar: BoringBar, k: Int, t: Double, part: HolderPart, reverse: Boolean): Pair<Double, Double> {
    val bore = Bore(sol.drawing, face)
    val tool = com.bmt.nosecalc.calc.BarShape(bar, r, reverse)
    val tv = NoseComp.tipVector(sol.tip, r)
    val s = if (k < 0) Line(sol.entry, sol.program.segs.first().start) else sol.program.segs[k]
    fun m(tt: Double) = HolderCheck.margins(bore, tool, at(s, tt) - tv)[part].m
    return m(t) to m(t - 1e-6 / len(s))
}
