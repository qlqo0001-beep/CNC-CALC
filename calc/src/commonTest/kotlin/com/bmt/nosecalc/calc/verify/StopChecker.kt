package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Bore
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderCut
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderPlan
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.Legacy
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
     * 기계 보정 프로그램(회사 꼴 — `G41`/`G42` 혼자 줄 / `G0X…Z20.` / 진입 G0 줄들 / `G1 … F.1` / 형상 / 빼는 줄 `F.3` / `G40` 혼자 줄 / `G0Z200.`)의 진입을
     * 이 파일의 제어기 모형으로 따로 본다. 보정 줄이 이동 없는 혼자 줄이라 다음 이동 블록(`G0X…Z20.`)이 시작 블록이다 — 형식 A 로 그 끝에서 노즈는 다음 블록
     * 처음의 공구 쪽 법선 위(형식 C 는 공구 교환 자리에서 옮겨 Z20. 자리의 노즈가 공구 교환 자리 방향에 따라 다르다 — 부품 앞 20 mm 공기라 형식 A 하나로 본다).
     * 그 뒤 진입 블록마다 노즈는 그 블록의 보정선(공구 쪽으로 r 민 선) 위, 꺾임은 두 보정선의 교점(첫 깎는 줄 끝은 첫 형상 요소의 보정선과). 진입 꺾임이 바깥으로
     * 90° 넘게 꺾이거나(제어기가 토막을 끼워 이 모형 밖) 노즈가 블록과 거꾸로 가면(간섭 검사 알람) 틀린 것으로 센다. 명령점은 풀이의 정확한 점(`approach` + 첫 점 —
     * 쓴 글과 0.001 반올림 안인지 따로 본다). 진입이 안전하면 끝점은 형상(직접 보정과 같은 노즈 경로)의 첫 위반, 아니면 진입 위의 그 자리
     */
    private fun checkMachineProgram(plan: HolderPlan, step: Double, bad: (String) -> Unit) {
        val dr = drawn
        val me = plan.machineEnd
        // 회사 꼴(진입 명령점이 있는) 풀이만 기계 보정 프로그램이다 — 노즈R 0 으로 푼 직접 보정 꼴은 없는 것과 같다
        if (dr == null || dr.approach.isEmpty()) {
            if (me != null || plan.machine != null || plan.machineEntry != null) bad("회사 꼴 기계 보정 풀이가 없는데 기계 보정 끝점 · 잘린 NC · 진입 닿음이 있음")
            return
        }
        val read = readText(dr.nc)
        if (dr.nc.first() != "${sol.side}") bad("기계 보정 첫 줄이 ${sol.side} 혼자 줄이 아님: ${dr.nc.first()}")
        val cmds = dr.approach + dr.program.segs.first().start
        // 진입 줄: Z20. 자리를 쓴 줄(1) 다음부터 첫 깎는 줄(이송 F 가 처음 나오는 줄)까지
        val firstCut = dr.nc.indexOfFirst { 'F' in it }
        val entryLines = (2..firstCut).filter { read[it].seg != null }
        if (entryLines.size != cmds.size - 1) return bad("기계 보정 진입 줄 ${entryLines.size} 개 ≠ 진입 명령점 ${cmds.size} 개 − 1: ${dr.nc}")
        val written = listOf(read[1].end) + entryLines.map { read[it].end }
        for ((i, p) in written.withIndex()) {
            if (p == null || abs(p.z - cmds[i].z) > 5e-4 + 1e-9 || abs(p.x - cmds[i].x) > 2.5e-4 + 1e-9) bad("기계 보정 진입 글 $p ≠ 명령점 ${cmds[i]}")
        }
        // 노즈: Z20. 자리는 첫 진입 블록 처음의 법선, 꺾임마다 두 보정선의 교점(noses[i] ↔ cmds[i])
        val blocks = cmds.zipWithNext { a, b -> Line(a, b) as Seg } + dr.program.segs.first { len(it) > 1e-9 }
        val offs = blocks.map(::offset)
        val noses = listOf(offs.first().start) + (0 until offs.size - 1).map { join(offs[it], offs[it + 1]) }
        for (i in 0 until blocks.size - 1) {
            val u1 = dirEnd(blocks[i])
            val u2 = dirStart(blocks[i + 1])
            if (crs(u1, u2) * sideSign < -1e-9 && (u1.z * u2.z + u1.x * u2.x) < -1e-9) bad("기계 보정 진입 ${entryLines[i] + 1}줄 끝에서 바깥으로 90° 넘게 꺾임 — 제어기가 토막을 끼운다: ${dr.nc}")
        }
        for (i in 0 until cmds.size - 1) {
            val d = cmds[i + 1] - cmds[i]
            if ((noses[i + 1] - noses[i]).let { it.z * d.z + it.x * d.x } <= 0) bad("기계 보정 진입 ${entryLines[i] + 1}줄의 노즈가 블록과 거꾸로 감(간섭 검사 알람): ${dr.nc}")
        }
        // 진입 블록: (줄, 명령 시작 · 끝, 노즈 시작 · 끝)
        class EB(val line: Int, val a: Pt, val b: Pt, val na: Pt, val nb: Pt) {
            fun nose(u: Double) = na + (nb - na) * u
            fun cmd(u: Double) = a + (b - a) * u
            val l = (nb - na).length
        }
        val eb = (0 until cmds.size - 1).map { EB(entryLines[it], cmds[it], cmds[it + 1], noses[it], noses[it + 1]) }
        val n0 = noses.first()
        // 진입 자세: 부분 여유 기준(진입이라 깎는 면 예외 없음) + 노즈 원이 소재에 들어가지 않음(중심이 소재 안 · 이 파일의 경계 거리).
        // 중심이 경계 위면 거리 쪽이 본다(노즈R 0 은 벽 위가 된다)
        val entryBore = Bore(sol.drawing, face)
        fun ok(c: Pt) = sigma(c, k = -1) >= -1e-9 && abs(c.x) <= entryBore.rho(c.z) + 1e-9 && noseGap(c) >= -1e-6
        val claimed = me != null && me.element < 0
        if (claimed != (StopWarning.MACHINE_ENTRY in plan.warnings)) bad("MACHINE_ENTRY ${plan.warnings} 인데 기계 보정 끝점 요소 ${me?.element}")
        if (me != null && me.element == -2) {
            if (ok(n0)) bad("기계 보정 Z20. 자리 자세가 안전한데 끝점 −2")
            if ((me.pose.nose - n0).length > 1e-9 || (me.at.machine!! - cmds[0]).length > 1e-9) bad("기계 보정 Z20. 자리 끝점 자세 ${me.pose.nose} · ${me.at.machine} ≠ $n0 · ${cmds[0]}")
            if (me.at.machineLine != 1) bad("기계 보정 Z20. 자리 끝점 줄 ${me.at.machineLine} ≠ 1(`G0X…Z20.`)")
        } else {
            if (!ok(n0)) bad("기계 보정 Z20. 자리 자세가 안전하지 않은데 끝점 ${me?.element}")
            for (b in eb) {
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
            if (me != null && me.element == -1 && eb.none { it.line == me.at.machineLine }) bad("기계 보정 진입 끝점 줄 ${me.at.machineLine} 이 진입 블록이 아님")
        }
        if (claimed) {
            if (plan.machine != null) bad("기계 보정 진입에서 멈췄는데 잘린 NC 가 있음")
            return
        }
        // 진입이 되면 형상의 첫 위반: 직접 보정 끝점이 형상 위면 같은 곳, 형상 전체가 안전하면 없음
        val end = plan.end
        if (end != null && end.element >= 0 && me != end) bad("기계 보정 끝점 ${me?.element} ${me?.t} ≠ 직접 보정 형상 끝점 ${end.element} ${end.t}")
        // 원래 끝(빼는 줄 → G40 → G0Z200.)에서 바가 닿는다는 주장(MACHINE_EXIT): 끝점은 경로 끝, 이 파일의 모형으로 원래 끝이 닿아야 한다.
        // 주장이 없고 형상이 끝까지 안전하면 원래 끝이 닿지 않아야 한다
        val exitClaim = StopWarning.MACHINE_EXIT in plan.warnings
        if (exitClaim && (me == null || me.element != path.last().first || me.t != 1.0)) bad("MACHINE_EXIT 인데 기계 보정 끝점 ${me?.element} ${me?.t} 이 경로 끝이 아님")
        if (me == null || exitClaim) {
            val ex = exitSlack(dr.nc, step)
            if (exitClaim && ex > 2e-3) bad("MACHINE_EXIT 인데 원래 끝의 표본 여유가 $ex(겹치지 않음) · ${dr.nc}")
            if (!exitClaim && ex < -1e-9) bad("원래 끝(빼는 줄 → G40 → G0Z200.)에서 바가 겹치는데($ex) MACHINE_EXIT 없음 · ${dr.nc}")
        }
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

    /**
     * 잘린 기계 보정 NC(회사 꼴): 앞 줄은 원래 글 그대로, 마지막 블록 → 빼는 줄(들 — 보정 켠 채, 첫 줄에 `F.3`) → `G40` 혼자 줄 → `G0Z200.`(Z 만).
     * 이 파일의 제어기 모형: `G0X…Z20.` 다음 줄부터 마지막 빼는 줄까지 블록마다 r 민 보정선을 교점(볼록은 늘인 교점)으로 잇고, Z20. 자리는 첫 블록 처음의 법선,
     * 마지막 빼는 줄 끝은 그 블록 끝의 법선(다음 줄 `G40` 은 이동이 없다). 빼는 줄은 공구 쪽으로 꺾여야 하고(안쪽 — 교점) 노즈가 블록과 같은 쪽으로 가야 한다.
     * 취소는 두 가지를 다 본다 — 형식 C: `G40` 줄에서 그 자리 → 마지막 빼는 줄 끝 명령점 − 팁 벡터로 곧게, 그 뒤 `G0Z200.` 은 +Z. 형식 A · B: `G40` 줄은 움직이지 않고
     * `G0Z200.` 동안 그 자리 → (Z200, 같은 X) − 팁 벡터로 곧게. Z 로 빠지는 움직임은 [zTop] 까지 잰다. 그리고 G40 자리 규칙을 따로: 팁 0~9 어느 것으로
     * 옮겨져도(어느 형식이든) 노즈 원이 소재(늘인 윤곽 · 맞은편)에 들어가지 않는다
     */
    private fun checkMachine(c: HolderCut, sEnd: Double, step: Double, bad: (String) -> Unit) {
        val dr = drawn!!
        if (c.nc.take(c.kept) != dr.nc.take(c.kept)) bad("기계 보정 앞 ${c.kept} 줄이 원래와 다르다: ${c.nc}")
        val iG40 = c.nc.size - 2
        if (c.nc.indices.filter { "G40" in c.nc[it] } != listOf(iG40) || c.nc[iG40] != "G40") return bad("기계 보정 G40 이 끝에서 둘째 혼자 줄이 아님: ${c.nc}")
        if (c.retract != RetractKind.LIFT) bad("기계 보정 후퇴가 ${c.retract} — 보정 켠 채 깎은 길을 되짚을 수 없다")
        if (c.retractFrom <= c.kept || c.retractFrom >= iG40) return bad("기계 보정 빼는 줄 자리 ${c.retractFrom}(앞 ${c.kept} 줄, G40 $iG40): ${c.nc}")
        if ('F' !in c.nc[c.retractFrom]) bad("기계 보정 첫 빼는 줄에 이송 F 가 없음: ${c.nc[c.retractFrom]}")
        val read = readText(c.nc)
        val zLine = read.last().seg as? Line
        if (zLine == null || abs(zLine.end.z - RETRACT_Z) > 1e-9 || abs(zLine.end.x - zLine.start.x) > 1e-12) bad("기계 보정 마지막 줄이 Z 만 Z200. 으로 가지 않음: ${c.nc.last()}")
        // 보정 블록: Z20. 자리를 쓴 줄(1) 다음부터 마지막 빼는 줄까지 — blocks[i] = 줄 i + 2
        val blocks = (2 until iG40).map { read[it].seg ?: return bad("기계 보정 ${it + 1}줄이 움직이지 않음: ${c.nc}") }
        val firstShape = dr.nc.indexOfFirst { 'F' in it } + 1
        // 오목 R ≤ r(보정 원호가 없어짐 — 제어기는 간섭 알람)인 블록을 이 모형으로 이을 수 없다: 그 앞뒤는 건너뛴다
        val gone = (firstShape until c.retractFrom).filter { (read[it].seg as? Arc)?.let { a -> (sol.side == Side.G41) != a.cw && (a.start - a.center).length <= r + 1e-9 } == true }
        if (gone.any { it >= c.kept - 2 }) {
            skippedMachine++
            return
        }
        // 그런 블록이 없으면 첫 형상 블록부터 끝까지(앞 블록의 반올림도) 이 모형으로 본다
        val modelFrom = gone.maxOrNull()?.let { it + 2 } ?: firstShape
        val offs = blocks.map { offset(it) }
        // joints[i] = 줄 i + 2 블록의 처음 노즈, joints[i + 1] = 그 끝
        val joints = mutableListOf(offs.first().start)
        for (i in 0 until offs.size - 1) joints += join(offs[i], offs[i + 1])
        joints += offs.last().end
        fun noseOf(line: Int): Seg {
            val i = line - 2
            return when (val o = offs[i]) {
                is Line -> Line(joints[i], joints[i + 1])
                is Arc -> Arc(joints[i], joints[i + 1], o.center, o.cw)
            }
        }
        val endNose = joints[c.retractFrom - 2]
        if ((endNose - c.pose.nose).length > 1e-6) bad("기계 보정 마지막 블록 끝 노즈 $endNose ≠ 계산기 ${c.pose.nose}")
        val sStop = pos(c.element, projT(c.element, endNose + tv))
        if (sStop > sEnd + 1e-9) bad("기계 보정 마지막 블록 끝 노즈가 안전 끝점을 넘음: $sStop > $sEnd")
        if (abs(sEnd - sStop - c.lack) > 1e-6) bad("기계 보정 덜 간 길이 ${c.lack} ≠ ${sEnd - sStop}")
        if (crs(dirEnd(blocks[c.retractFrom - 3]), dirStart(blocks[c.retractFrom - 2])) * sideSign <= 1e-9) bad("기계 보정 첫 빼는 줄이 공구 쪽으로 꺾이지 않음: ${c.nc[c.retractFrom]}")
        for (line in c.retractFrom until iG40) {
            val i = line - 2
            val u = dirStart(blocks[i])
            if ((joints[i + 1] - joints[i]).let { it.z * u.z + it.x * u.x } <= 0) bad("기계 보정 빼는 줄 ${line + 1}줄의 노즈가 블록과 거꾸로 감(간섭 검사 알람): ${c.nc}")
        }
        val motions = (modelFrom until iG40).map { it to Seg2(noseOf(it)) }
        val xe = read[iG40 - 1].end!!
        val nBefore = joints.last()
        fun up(a: Pt, b: Pt): Line = if (b.z <= zTop) Line(a, b) else Line(a, a + (b - a) * ((zTop - a.z) / (b.z - a.z)).coerceIn(0.0, 1.0))
        val far = Pt(RETRACT_Z, xe.x)
        sweepMotions(motions + listOf(iG40 to Seg2(Line(nBefore, xe - tv)), iG40 + 1 to Seg2(up(xe - tv, far - tv))), c, step, bad, "기계 보정(취소 형식 C)") { it >= c.retractFrom }
        sweepMotions(motions.filter { it.first >= c.retractFrom } + listOf(iG40 + 1 to Seg2(up(nBefore, far - tv))), c, step, bad, "기계 보정(취소 형식 A · B)") { it >= c.retractFrom }
        // G40 자리 규칙: 팁 번호가 틀려도(0~9) 옮겨지는 노즈 원이 소재에 들어가지 않는다
        for (t in 0..9) {
            val v = NoseComp.tipVector(t, r)
            for (m in listOf(Line(nBefore, xe - v), up(xe - v, far - v), up(nBefore, far - v))) {
                val n = max(1, ceil(len(m) / 0.01).toInt())
                for (q in 0..n) {
                    val p = at(m, q.toDouble() / n)
                    val g = noseGap(p)
                    if (g < -1e-3) return bad("기계 보정 G40 자리 규칙: 팁 $t 이면 노즈 $p 가 소재에 ${-g} 들어감 · ${c.nc}")
                }
            }
        }
    }

    /**
     * 기계 보정 원래 프로그램 끝(첫 빼는 줄 → … → `G40` → `G0Z200.`)의 겹침 여유 최솟값(이 파일의 제어기 모형 — [checkMachine] 과 같다): 노즈는 보정선 교점,
     * 마지막 빼는 줄 끝은 법선, 형식 C 의 G40 옮김 + Z · 형식 A · B 의 비스듬한 Z 를 [zTop] 까지. 겹침 기준(바 · 공구대 · 최소 가공경 · 인서트 맞은편 ≥ −1e-4,
     * 인서트 깎는 쪽은 첫 빼는 줄만 빼고 ≥ −1e-4, 첫 빼는 줄 뒤는 노즈 원도 이 파일의 경계 거리로 ≥ −1e-4), 깎는 면 예외는 마지막 요소. 0.002 mm 마다 재되 여유가 넉넉하면 그만큼 건너뛴다(직선 거리는 1-립시츠 —
     * 건너뛴 사이에 겹침이 숨지 않는다)
     */
    private fun exitSlack(nc: List<String>, step: Double): Double {
        val dr = drawn!!
        val read = readText(nc)
        val iG40 = nc.size - 2
        val firstExit = dr.ncRefs.indexOfFirst { it.element == dr.drawing.segs.size }
        if (nc[iG40] != "G40" || firstExit < 3 || firstExit >= iG40) return Double.POSITIVE_INFINITY
        val blocks = (2 until iG40).map { read[it].seg ?: return Double.POSITIVE_INFINITY }
        val offs = blocks.map { offset(it) }
        val joints = mutableListOf(offs.first().start)
        for (i in 0 until offs.size - 1) joints += join(offs[i], offs[i + 1])
        joints += offs.last().end
        val xe = read[iG40 - 1].end!!
        val nB = joints.last()
        fun up(a: Pt, b: Pt): Line = if (b.z <= zTop) Line(a, b) else Line(a, a + (b - a) * ((zTop - a.z) / (b.z - a.z)).coerceIn(0.0, 1.0))
        val far = Pt(RETRACT_Z, xe.x)
        val ms = (firstExit until iG40).map { Line(joints[it - 2], joints[it - 1]) to (it == firstExit) } +
            listOf(Line(nB, xe - tv) to false, up(xe - tv, far - tv) to false, up(nB, far - tv) to false)
        val k = dr.drawing.segs.size - 1
        var low = Double.POSITIVE_INFINITY
        for ((m, first) in ms) {
            val need = Need(-TEXT, if (first) Double.NEGATIVE_INFINITY else -TEXT)
            val l = len(m)
            var s = 0.0
            while (true) {
                val p = at(m, if (l > 0) s / l else 0.0)
                val sl = min(SHIFTS.maxOf { job.clear(listOf(p + it), k).slack(need) }, if (first) Double.POSITIVE_INFINITY else noseGap(p) + TEXT)
                low = min(low, sl)
                if (s >= l) break
                s = min(l, s + max(min(step, 0.002), sl))
            }
        }
        return low
    }

    /**
     * 움직임을 [step] mm 마다: 깎는 동안 text 기준, 후퇴는 겹치지 않음(빼기 시작 블록의 인서트 깎는 쪽 · 거꾸로 후퇴는 날이 벽을 따라감).
     * X 빼기 · Z 후퇴는 노즈 원도: 도면(맞은편 포함)까지 거리 − r ≥ min(L, 간 길이/2) − 0.002, L = max(기준, 0.025) — 벽에 붙어 끌리지 않는다(0.002 mm 마다).
     * 빼기 후퇴(LIFT)의 부분 여유는 기준까지 남은 양 · 보고한 후퇴 여유까지 남은 양이 넉넉하면 그만큼 건너뛴다 — 직선 거리는 자리에 대해 1-립시츠라 건너뛴 사이에서
     * 그 아래로 못 내려간다(겹치는 곳은 남은 양이 음수라 건너뛰지 않는다). 긴 Z 후퇴(`G0Z200.`)를 빨리 재려고
     */
    private fun sweepMotions(ms: List<Pair<Int, Seg2>>, c: HolderCut, step: Double, bad: (String) -> Unit, name: String, isRetract: (Int) -> Boolean) {
        var low = Double.POSITIVE_INFINITY
        var travel = 0.0
        val level = if (c.retractClear >= safe - TEXT) safe else 0.0
        val old = Legacy.nc(c.nc)
        val every = max(1, (step / 0.002).toInt())
        for ((i, m) in ms) {
            val retract = isRetract(i)
            val firstRetract = i == c.retractFrom
            val l = len(m.s)
            val n = max(1, ceil(l / min(step, 0.002)).toInt())
            var skipTo = -1.0
            for (q in 0..n) {
                val along = l * q / n
                val nose = at(m.s, q.toDouble() / n)
                if (retract && c.retract == RetractKind.LIFT) {
                    val gap = noseGap(nose)
                    val want = min(max(level, 0.025), (travel + along) / 2) - 0.002
                    if (gap < want) return bad("$name 줄 $i 후퇴 노즈 $nose: 소재까지 $gap < $want (간 길이 ${travel + along}) · ${c.nc}")
                }
                if (q % every != 0 && q != n) continue
                // 깎는 면 예외는 그 자세의 경로 요소(빼기 후퇴는 멈춘 요소 — 구현과 같다)
                val kk = if (retract && c.retract == RetractKind.LIFT) c.element else elementNear(nose)
                if (!retract) {
                    val sl = SHIFTS.maxOf { job.clear(listOf(nose + it), kk).slack(text) }
                    if (sl < -1e-9) return bad("$name 줄 $i: 깎는 자세 $nose 가 안전 여유 − 1e-4 아래 $sl · ${c.nc}")
                    continue
                }
                if (along < skipTo && q != n) continue
                val exemptCut = c.retract == RetractKind.REVERSE || (firstRetract && q == 0) || (firstRetract && old[i].contains("X") && !old[i].startsWith("G00") && !old[i].startsWith("G40 G00"))
                val need = if (c.retract == RetractKind.REVERSE) text else Need(-TEXT, if (exemptCut) Double.NEGATIVE_INFINITY else -TEXT)
                val sl = SHIFTS.maxOf { job.clear(listOf(nose + it), kk).slack(need) }
                if (sl < -1e-9) return bad("$name 줄 $i 후퇴 자세 $nose 가 겹침 $sl · ${c.nc}")
                val lw = SHIFTS.maxOf { job.clear(listOf(nose + it), kk).least(false) }
                low = min(low, lw)
                if (c.retract == RetractKind.LIFT) skipTo = along + min(sl, lw - (c.retractClear - 1e-6))
            }
            if (retract) travel += l
        }
        if (low < c.retractClear - 1e-6) bad("$name 후퇴 여유 표본 $low < 계산기 ${c.retractClear}")
    }

    /**
     * 소재 경계(이 파일 따로): 도면 + 입구 쪽(TYPE 은 입구 면 위로, 3점호는 시작 접선 거꾸로) · 깊은 끝(축 위가 아니면 끝 접선) 반직선 +
     * 입구 면(두 반직선 끝 가운데 Z 가 큰 쪽에서 위로 — 설계 문서의 소재 정의. 회사 꼴 진입은 Z20. 에서 내려와 그 면을 지난다)
     */
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
        val a = if (face || head == null) first.start else head.end
        val b = tail?.end ?: last.end
        val mouth = if (a.z >= b.z) a else b
        listOfNotNull(head) + segs + listOfNotNull(tail) + Line(mouth, mouth + Pt(0.0, 1e3))
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

    /** 이웃 보정선의 이음: 거의 이어지면 가운데, 아니면 두 선 · 원(늘인 것)의 교점 가운데 두 끝 가운데에 가까운 것(교점 식도 이 파일 따로) */
    private fun join(a: Seg, b: Seg): Pt {
        val mid = (a.end + b.start) * 0.5
        if ((a.end - b.start).length <= 1e-3) return mid
        val cand = when {
            a is Line && b is Line -> listOfNotNull(meetLines(a.start, a.end - a.start, b.start, b.end - b.start))
            a is Line && b is Arc -> meetLineCircle(a.start, a.end - a.start, b.center, (b.start - b.center).length)
            a is Arc && b is Line -> meetLineCircle(b.start, b.end - b.start, a.center, (a.start - a.center).length)
            a is Arc && b is Arc -> meetCircles(a.center, (a.start - a.center).length, b.center, (b.start - b.center).length)
            else -> emptyList()
        }
        return cand.minByOrNull { (it - mid).length } ?: mid
    }

    /** 공구 쪽 부호: G41(진행 왼쪽) = 1 — 진행 방향 u1 → u2 의 [crs] 가 이 부호와 같으면 공구 쪽(안쪽)으로 꺾인다 */
    private val sideSign = if (sol.side == Side.G41) 1.0 else -1.0

    /** Z 로 빠지는 움직임을 재는 끝: 도면 맨 위 + 25 mm + 안전 여유(공구 앞뒤 길이를 넉넉히) — 그 위는 입구 면 위 공기이거나 같은 구멍(반직선)이 이어진다 */
    private val zTop: Double = min(RETRACT_Z, sol.drawing.segs.maxOf { s -> (0..16).maxOf { at(s, it / 16.0).z } } + 25 + safe)

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

        /** 회사 꼴 물러남 Z(`G0Z200.`) */
        const val RETRACT_Z = 200.0

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

/** 다시 읽은 줄: 움직임(첫 좌표 줄 · 축 없는 줄은 null), G40, 그 줄 뒤 자리(좌표가 아직 없으면 null) */
internal class ReadLine(val seg: Seg?, val g40: Boolean, val end: Pt?)

/**
 * NC 글 읽기(구현과 따로): G00/G01 직선, G02/G03 끝점 R, 빠진 축 · 이동 G 는 앞 값(모달), X 는 직경, G40 은 표시만.
 * 축 없는 줄(회사 꼴 보정 혼자 줄 `G41` · `G42` · `G40`)은 움직이지 않는다. 앱 글(회사 표기)은 옛 꼴로 되돌려([Legacy]) 읽는다
 */
internal fun readText(lines: List<String>): List<ReadLine> {
    var z: Double? = null
    var x: Double? = null
    var motion: String? = null
    return Legacy.nc(lines).map { line ->
        val w = line.trim().split(" ").filter { it.isNotEmpty() }
        val g = w.filter { it.startsWith("G") }
        g.lastOrNull { it in MOTIONS }?.let { motion = it }
        fun v(c: Char) = w.firstOrNull { it[0] == c }?.substring(1)?.toDouble()
        val here = z?.let { zz -> x?.let { Pt(zz, it) } }
        if (v('X') == null && v('Z') == null) return@map ReadLine(null, "G40" in g, here)
        val mo = motion ?: error("움직임 G 가 없다: $line")
        val to = Pt(v('Z') ?: z ?: error("Z 없음: $line"), v('X')?.let { it / 2 } ?: x ?: error("X 없음: $line"))
        z = to.z
        x = to.x
        val seg = when {
            here == null -> null
            mo == "G02" || mo == "G03" -> {
                val rr = v('R') ?: error("R 없음: $line")
                val ch = to - here
                val h = ch.length / 2
                val dd = sqrt(max(rr * rr - h * h, 0.0))
                val cw = mo == "G02"
                Arc(here, to, (here + to) * 0.5 + Pt(-ch.x, ch.z) * ((if (cw) -dd else dd) / ch.length), cw)
            }
            else -> Line(here, to)
        }
        ReadLine(seg, "G40" in g, to)
    }
}

private val MOTIONS = setOf("G00", "G01", "G02", "G03")

/** 진행 방향 a → b 의 꺾임 부호(z·x 평면의 외적) */
private fun crs(a: Pt, b: Pt) = a.z * b.x - a.x * b.z

/** 요소 처음 · 끝의 진행 방향(단위) */
private fun dirStart(s: Seg): Pt = dirAt(s, s.start)

private fun dirEnd(s: Seg): Pt = dirAt(s, s.end)

private fun dirAt(s: Seg, p: Pt): Pt = when (s) {
    is Line -> (s.end - s.start) * (1 / len(s))
    is Arc -> (p - s.center).let { v -> Pt(-v.x, v.z) * ((if (s.cw) -1.0 else 1.0) / v.length) }
}

/** 두 직선(점 p · 방향 d)의 교점. 나란하면 null */
private fun meetLines(p: Pt, d: Pt, q: Pt, e: Pt): Pt? {
    val den = crs(d, e)
    if (abs(den) < 1e-12) return null
    return p + d * (crs(q - p, e) / den)
}

/** 직선(점 p · 방향 d)과 원(중심 c · 반지름 rr)의 교점 */
private fun meetLineCircle(p: Pt, d: Pt, c: Pt, rr: Double): List<Pt> {
    val u = d * (1 / d.length)
    val w = p - c
    val b = w.z * u.z + w.x * u.x
    val disc = b * b - (w.z * w.z + w.x * w.x - rr * rr)
    if (disc < -1e-12) return emptyList()
    val s = sqrt(max(0.0, disc))
    return listOf(p + u * (-b - s), p + u * (-b + s))
}

/** 두 원의 교점 */
private fun meetCircles(c1: Pt, r1: Double, c2: Pt, r2: Double): List<Pt> {
    val d = (c2 - c1).length
    if (d < 1e-12) return emptyList()
    val a = (d * d + r1 * r1 - r2 * r2) / (2 * d)
    val h2 = r1 * r1 - a * a
    if (h2 < -1e-12) return emptyList()
    val u = (c2 - c1) * (1 / d)
    val m = c1 + u * a
    val h = sqrt(max(0.0, h2))
    val n = Pt(-u.x, u.z)
    return listOf(m + n * h, m - n * h)
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
