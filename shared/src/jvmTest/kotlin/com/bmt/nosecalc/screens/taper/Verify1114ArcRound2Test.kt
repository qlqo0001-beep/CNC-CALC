package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.GText
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 재확인2-동작(2026-11-14 — 고침2 `git diff 3c6d3ed` 를 다시 깨 본다). 고친 사람의 시험 · 계산기 안쪽 함수를 쓰지 않고 화면이 부르는 길
 * (SlopeForm → TaperSolve.solve)과 시뮬레이터(GSim)로 잰다.
 * - RC-2: 내경 바닥 단면 시작 X0(뒤집기) · 끝 X0 × 원호 오목 · 볼록 · 직선 × 화면 정 · 역방향 × 바닥 Ø2~12 × 노즈 0.2~1.6 × Z 여유 0 · 1 · 5 × 팁 표준 · 비표준.
 *   '옛 글' 은 시작을 X0.000001 로 넣어 얻는다(고친 갈래는 시작 X ≤ 1e-9 만 — 그 밖은 HEAD 와 같은 길, 글자는 X0.).
 *   고침3(재확인2 B2-V2-1)부터는 노즈 중심이 축 너머로 가는 시작(팁 1 · 2 · 6 의 X < 2r)도 고친 갈래라 그 팁의 '옛 글' 은 새 글과 같다(비교가 제 자신과 됨 —
 *   팁 3 · 4 · 5 · 7 · 8 · 0 만 옛 길).
 * - RC-1: 0.001 반올림 띠에서 직접 보정 글의 T1–T2 호 줄 유무 ⇔ NOSE_GT_SLOPE 없음/있음 ⇔ 보정 R 있음/없음.
 * - B-F3: R 9999 · 10000 원호 글이 시뮬레이터에서 돎, 좌표 낱말 10000 넘음은 NUMBER_TOO_BIG, 사이클 R 이 커도 시뮬레이터가 끝남.
 * 요약은 build/verify1114arcround2/ 에 쓴다
 */
class Verify1114ArcRound2Test {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** 파고듦 · 덜 깎임 허용(mm) */
    private val tol = 2e-3

    private fun outFile(name: String) = File("build/verify1114arcround2").also { it.mkdirs() }.resolve(name)

    // ================================================================ 기하(제품 코드의 internal 도우미를 쓰지 않는다)

    private fun ang(v: Pt) = atan2(v.x, v.z)
    private fun wrap(a: Double): Double {
        var x = a % (2 * PI)
        if (x < 0) x += 2 * PI
        return x
    }

    private fun sweep(a: Arc): Double {
        if ((a.end - a.start).length < 1e-12) return 0.0
        val d = ang(a.end - a.center) - ang(a.start - a.center)
        return wrap(if (a.cw) -d else d)
    }

    private fun onArc(a: Arc, p: Pt): Boolean {
        val d = ang(p - a.center) - ang(a.start - a.center)
        val t = wrap(if (a.cw) -d else d)
        return t <= sweep(a) + 1e-12 || t > 2 * PI - 1e-9
    }

    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 < 1e-24) (p - s.start).length else (p - (s.start + d * (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0))).length
        }
        is Arc -> if (onArc(s, p)) abs((p - s.center).length - s.radius) else min((p - s.start).length, (p - s.end).length)
    }

    private fun samples(s: Seg, step: Double): List<Pt> = when (s) {
        is Line -> {
            val n = max(1, ceil((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val sw = sweep(s)
            val n = max(1, ceil(sw * s.radius / step).toInt())
            val a0 = ang(s.start - s.center)
            (0..n).map {
                val a = a0 + (if (s.cw) -1 else 1) * sw * it / n
                s.center + Pt(cos(a), sin(a)) * s.radius
            }
        }
    }

    private fun mirror(p: Pt) = Pt(p.z, -p.x)

    private fun xz(p: Pt) = "X%.4f Z%.4f".format(p.xDia, p.z)

    // ================================================================ 시뮬레이터 · 파고듦 · 덜 깎임

    private fun sim(nc: List<String>, r: Double, tip: Int, form: CompStartup = CompStartup.C, feed: Boolean = false): SimRun {
        val head = if (feed) pre.dropLast(1) + "G99F.1" else pre
        return GSim.run(SimInput((head + nc + "M30").joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, tip))))
    }

    private class Box(s: Seg, r: Double) {
        val z0: Double
        val z1: Double
        val x0: Double
        val x1: Double

        init {
            if (s is Arc) {
                z0 = s.center.z - s.radius - r - 0.01
                z1 = s.center.z + s.radius + r + 0.01
                x0 = s.center.x - s.radius - r - 0.01
                x1 = s.center.x + s.radius + r + 0.01
            } else {
                z0 = min(s.start.z, s.end.z) - r - 0.01
                z1 = max(s.start.z, s.end.z) + r + 0.01
                x0 = min(s.start.x, s.end.x) - r - 0.01
                x1 = max(s.start.x, s.end.x) + r + 0.01
            }
        }

        fun has(p: Pt) = p.z in z0..z1 && p.x in x0..x1
    }

    /** depth = 가장 깊이 판 깊이(+ 면 팜 · − 면 틈), far = 그 자리가 축 거울(내경 맞은편 벽) */
    private class Dig(val depth: Double, val by: MoveStep?, val at: Pt?, val far: Boolean)

    /** [moves] 의 노즈(반지름 r)가 도면(내경이면 축 거울 = 맞은편 구멍 벽도)을 가장 깊이 판 곳 */
    private fun dig(moves: List<MoveStep>, segs: List<Seg>, r: Double, inner: Boolean): Dig {
        val step = min(0.05, sqrt(2 * r * 1e-3))
        val near = segs.flatMap { samples(it, step) }
        val far = if (inner) near.map(::mirror) else emptyList()
        var best = Dig(Double.NEGATIVE_INFINITY, null, null, false)
        for (m in moves) {
            val box = Box(m.nose, r)
            for ((list, isFar) in listOf(near to false, far to true)) for (q in list) {
                if (!box.has(q)) continue
                val d = r - dist(q, m.nose)
                if (d > best.depth) best = Dig(d, m, q, isFar)
            }
        }
        return best
    }

    /** 도면 점 q 의 덜 깎임(+ 면 노즈가 그 점까지 오지 않음): 이송 줄 노즈 경로까지 거리 − r. 축 너머 노즈는 거울 점을 깎는다(회전체) */
    private fun uncut(q: Pt, feeds: List<MoveStep>, r: Double, viaMirror: Boolean = true): Double {
        val m = mirror(q)
        return feeds.minOfOrNull { (if (viaMirror) min(dist(q, it.nose), dist(m, it.nose)) else dist(q, it.nose)) - r } ?: Double.POSITIVE_INFINITY
    }

    private fun moves(run: SimRun) = run.steps.filterIsInstance<MoveStep>()

    /** 글의 첫 이동 줄(공구 교환 자리에서 오는 G00) 번호(프로그램 줄) */
    private fun firstLine(nc: List<String>) = pre.size + nc.indexOfFirst { !it.startsWith("(") } + 1

    // ================================================================ 집계

    private class Tally {
        val count = sortedMapOf<String, Int>()
        val first = linkedMapOf<String, MutableList<String>>()
        val stat = sortedMapOf<String, Int>()
        val max = sortedMapOf<String, Double>()

        fun fail(kind: String, text: () -> String) {
            count[kind] = (count[kind] ?: 0) + 1
            val l = first.getOrPut(kind) { mutableListOf() }
            if (l.size < 4) l += text()
        }

        fun note(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }

        fun keepMax(k: String, v: Double) {
            if (v > (max[k] ?: Double.NEGATIVE_INFINITY)) max[k] = v
        }

        fun report(title: String) = buildString {
            appendLine("==== $title")
            appendLine("-- 최대")
            max.forEach { (k, v) -> appendLine("$k = $v") }
            appendLine("-- 통계")
            stat.forEach { (k, v) -> appendLine("$k = $v") }
            appendLine("-- 실패 갈래")
            count.forEach { (k, v) -> appendLine("$k = $v") }
            first.forEach { (k, l) ->
                appendLine("---- $k")
                l.forEach { appendLine(it); appendLine() }
            }
        }
    }

    private fun finish(t: Tally, title: String, file: String) {
        val text = t.report(title)
        outFile(file).writeText(text)
        println(text.lineSequence().take(80).joinToString("\n"))
        // '(기존' 은 이번 diff 밖(옛 글도 같음) · '(못넣음' 은 노즈가 원래 바닥에 못 들어가는 꼴 — 알리기만 한다
        val hard = t.count.filterKeys { "(기존" !in it && "(못넣음" !in it }
        assertTrue(hard.isEmpty(), "$title: 실패 갈래 $hard — build/verify1114arcround2/$file")
    }

    private fun desc(f: SlopeForm) =
        "${f.kind.label} 형상 ${f.type}${if (f.reverse) " 역방향" else ""}${if (f.flip) " 뒤집기" else ""} · X${f.x} Z${f.z} · " +
            (if (f.arc) "R${f.arcR} ${if (f.arcConcave) "오목" else "볼록"}" else "직선") +
            " · 노즈R ${f.noseR} · 팁 ${f.tip} · 모서리 R ${f.corners} · 시작 ${f.start} · 끝 ${f.end} · Z 여유 ${f.zClear}"

    // ================================================================ RC-2 — 내경 바닥 단면 X0

    /**
     * 내경 바닥 단면이 X0 에 닿는 화면 칸. [bottomStart] = 뒤집어 바닥을 중심부터(시작 X0), 아니면 정방향으로 바닥을 중심까지(끝 X0).
     * 바닥 쪽 T 점 = Ø[d1] Z−6, 다른 T 점 = Ø(d1 + 2dr) Z(−6 + dz). shape 0 = 오목 · 1 = 볼록 · 2 = 직선. 화면 역방향은 z → −30 − z(구멍이 −Z 로 열림).
     * [otherLeg] = 바닥 아닌 쪽 요소(형상 2 는 입구 쪽 단면 X, 형상 4 는 큰 내경 Z)를 넣나
     */
    private fun bottomForm(
        type: Int, bottomStart: Boolean, reverse: Boolean, d1: Double, dr: Double, dz: Double, shape: Int, factor: Double,
        noseR: Double, tip: Int?, zc: Double, otherLeg: Boolean, cr: Double, axis: Double = 0.0,
    ): SlopeForm? {
        val zB = -6.0
        val zT = zB + dz
        val xB = d1
        val xT = d1 + 2 * dr
        fun mz(z: Double) = if (reverse) -30.0 - z else z
        val arc = shape < 2
        val rr = if (arc) (TaperCalc.arcMinR(xB, zB, xT, zT) ?: return null).let { round(it * factor * 1000) / 1000 + 0.001 } else null
        val other: Double? = if (!otherLeg) null else if (type == 2) xT + 4.0 else mz(zT + 3.0)
        return SlopeForm(
            kind = TaperKind.INNER, type = type, reverse = reverse, noseR = noseR, tip = tip,
            x = if (bottomStart) listOf(xB, xT) else listOf(xT, xB),
            z = if (bottomStart) listOf(mz(zB), mz(zT)) else listOf(mz(zT), mz(zB)),
            angle = null, zClear = zc, corners = listOf(cr, cr),
            start = if (bottomStart) axis else other, end = if (bottomStart) other else axis,
            flip = bottomStart, angleRef = AngleRef.X, arc = arc, arcR = rr, arcConcave = shape == 0,
        )
    }

    /** 노즈(반지름 r)가 축 위에서 바닥 단면([bottom] 번 요소)에 얹힌 자리부터 구멍 입구 쪽으로 축을 따라 올라오는 동안 다른 도면 요소에 닿지 않나(축 위 점은 거울과 거리가 같다) */
    private fun fits(segs: List<Seg>, bottom: Int, r: Double): Boolean {
        val zBot = segs[bottom].start.z
        val zs = segs.flatMap { listOf(it.start.z, it.end.z) }
        val sign = if (zs.max() > zBot + 1e-9) 1.0 else -1.0
        val span = zs.maxOf { abs(it - zBot) } + r
        val others = segs.filterIndexed { i, _ -> i != bottom }
        var t = 0.0
        while (t <= span) {
            val c = Pt(zBot + sign * (r + t), 0.0)
            if (others.any { dist(c, it) < r - 1e-4 }) return false
            t += 0.005
        }
        return true
    }

    private class Prog(val stop: String?, val dig: Dig?, val feeds: List<MoveStep>, val all: List<MoveStep>)

    private fun runProg(nc: List<String>, r: Double, tip: Int, direct: Boolean, segs: List<Seg>): Prog {
        val run = sim(nc, r, tip, feed = direct)
        val stop = run.stop
        val first = firstLine(nc)
        val all = moves(run).filter { it.line > first }
        if (stop != null) return Prog("${stop.code}@${stop.line - pre.size}", null, emptyList(), all)
        return Prog(null, dig(all, segs, r, true), all.filter { it.kind == MoveKind.FEED }, all)
    }

    private fun digText(p: Prog, nc: List<String>): String {
        val d = p.dig ?: return "멈춤 ${p.stop}"
        val m = d.by ?: return "${d.depth}"
        return "${m.line - pre.size}줄 '${nc.getOrNull(m.line - pre.size - 1)}' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 가 ${xz(d.at!!)}(${if (d.far) "맞은편" else "도면"}) 를 ${"%.4f".format(d.depth)} 팜"
    }

    /** 한 사례: 직접 보정 · 기계 보정 글을 GSim 으로 — 멈춤 · 파고듦 · 바닥 중심 깎임 · (시작 X0 이면) 옛 글보다 덜 깎이지 않음 */
    private fun rc2Case(f: SlopeForm, bottomStart: Boolean, t: Tally, log: StringBuilder?) {
        val res: TaperResult = try {
            TaperSolve.solve(f)
        } catch (e: Throwable) {
            t.fail("SOLVE_THROW") { "${desc(f)}\n$e" }
            return
        }
        val r = f.noseR
        // 도면(노즈와 상관없음) — 멈춘 사례의 '넣을 수 있나' 판정용
        val shapeSol = res.solution ?: TaperSolve.solve(f.copy(noseR = 0.05, tip = null, zClear = 1.0)).solution
        if (shapeSol == null) {
            t.note("형상 못 풂 ${res.warnings.joinToString("+")}")
            return
        }
        val segs0 = shapeSol.drawing.segs
        val bottom = if (bottomStart) 0 else segs0.lastIndex
        val fit = fits(segs0, bottom, r)
        val soft = if (fit) "" else "(못넣음)"
        val side = if (bottomStart) "시작X0" else "끝X0"
        val old = if (bottomStart) TaperSolve.solve(f.copy(start = 1e-6)) else null
        val oldSol = old?.solution
        val oldDirect = oldSol?.let { runProg(it.ncG40, r, it.tip, true, it.drawing.segs) }
        val s = res.solution
        if (s == null) {
            t.note("$side 멈춤${if (fit) "(넣을 수 있는 꼴)" else "(못 넣는 꼴)"} ${res.warnings.joinToString("+")}")
            if (bottomStart && oldSol != null && oldDirect != null) {
                val oldOk = oldDirect.stop == null && (oldDirect.dig?.depth ?: 1.0) <= tol
                if (oldOk) {
                    t.fail("RC2_NEW_STOP_옛글은안전$soft") { "${desc(f)}\n새: 멈춤 ${res.warnings}\n옛 글(시작 X0.000001): ${oldSol.ncG40.joinToString(" ")}" }
                } else {
                    t.note("$side 멈춤 — 옛 글은 ${if (oldDirect.stop != null) "멈춤" else "팜 ${"%.3f".format(oldDirect.dig!!.depth)}"}")
                }
            }
            // 넣을 수 있다고 본 꼴의 멈춤은 예를 남긴다(시작 X0 은 옛 글도 멈춤/팜일 때만 여기 — 옛 글이 안전하면 위 실패)
            if (fit) t.fail("RC2_STOP_FITS_${side}_${res.warnings.joinToString("+")}(기존 — 알림)") {
                "${desc(f)}\n옛 글: ${old?.warnings} ${oldSol?.ncG40?.joinToString(" ")} · ${oldDirect?.let { digText(it, oldSol!!.ncG40) }}"
            }
            return
        }
        t.note("$side 풂${if (fit) "" else "(못 넣는 꼴)"}")
        val tag = { "${desc(f)}\n경고 ${res.warnings}\nG40: ${s.ncG40.joinToString(" ")}\nG41/G42: ${s.ncG41G42.joinToString(" ")}" }
        val segs = s.drawing.segs
        val qB = segs[bottom].let { if (bottomStart) it.start else it.end } // 바닥 중심(X0)
        // ---- 직접 보정
        val d = runProg(s.ncG40, r, s.tip, true, segs)
        if (d.stop != null) t.fail("RC2_D_STOP_${d.stop.substringBefore('@')}_$side$soft") { "${d.stop}\n${tag()}" }
        else {
            val dd = d.dig!!
            if (fit) t.keepMax("직접 보정 파고듦 $side(넣을 수 있는 꼴)", dd.depth) else t.keepMax("직접 보정 파고듦 $side(못 넣는 꼴)", dd.depth)
            if (dd.depth > tol) {
                val oldD = oldDirect?.dig?.depth
                val why = if (oldD != null && oldD >= dd.depth - tol) "(기존 — 옛 글도 ${"%.3f".format(oldD)} 팜)" else if (!bottomStart) "(기존 — 끝 X0 갈래는 이번 diff 밖)" else ""
                t.fail("RC2_D_GOUGE_${if (dd.far) "맞은편" else "도면"}_$side$soft$why") { "${digText(d, s.ncG40)}\n옛 글: ${oldSol?.ncG40?.joinToString(" ")} · ${oldDirect?.let { digText(it, oldSol!!.ncG40) }}\n${tag()}" }
            }
            // 바닥 중심까지 깎임
            val uc = uncut(qB, d.feeds, r)
            if (fit) t.keepMax("직접 보정 바닥 중심 덜 깎임 $side(넣을 수 있는 꼴)", uc)
            if (fit && uc > tol) t.fail("RC2_D_CENTER_UNCUT_$side${if (!bottomStart) "(기존 — 끝 X0 갈래는 이번 diff 밖)" else ""}") { "바닥 중심 ${xz(qB)} 덜 깎임 ${"%.4f".format(uc)}\n${tag()}" }
            // 바닥 단면은 노즈 중심이 지나간 X(축 ~ 꺾임의 노즈 중심 X)까지 다 깎인다
            val cB = s.center.segs[bottom]
            val xc = max(cB.start.x, cB.end.x)
            var bw = Double.NEGATIVE_INFINITY
            var bAt: Pt? = null
            for (q in samples(segs[bottom], 0.01)) if (q.x <= xc - 1e-6) {
                val u = uncut(q, d.feeds, r)
                if (u > bw) {
                    bw = u
                    bAt = q
                }
            }
            if (bAt != null) {
                t.keepMax("직접 보정 바닥 단면(노즈 중심 X 안) 덜 깎임 $side", bw)
                if (bw > tol) t.fail("RC2_D_BOTTOM_UNCUT_$side$soft") { "${xz(bAt)} 덜 깎임 ${"%.4f".format(bw)} (노즈 중심 X ${"%.4f".format(xc * 2)})\n${tag()}" }
            }
            // 시작 X0: 노즈 중심이 축에서 시작(고친 뜻) · 옛 글보다 덜 깎이지 않음
            if (bottomStart) {
                val c0 = s.center.segs.first().start
                if (abs(c0.x) > 1e-6) t.fail("RC2_D_NOSE_NOT_ON_AXIS") { "노즈 중심 시작 ${xz(c0)}\n${tag()}" }
                oldDirect?.dig?.let {
                    t.note(if (it.depth > tol) "시작X0 옛 글(X0.000001) 팜${if (fit) "" else "(못 넣는 꼴)"}" else "시작X0 옛 글 안 팜")
                    t.keepMax("시작X0 옛 글 파고듦${if (fit) "" else "(못 넣는 꼴)"}", it.depth)
                }
                // 옛 글이 파지 않았을 때만 견준다 — 옛 글이 맞은편을 팠으면 오목 꺾임 둘레를 판 것으로 더 깎았을 뿐이다(새 글은 그 자리에 r 이 남는 것이 맞다)
                if (oldDirect != null && oldDirect.stop == null && oldDirect.dig!!.depth <= tol) {
                    val step = min(0.02, sqrt(2 * r * 1e-3))
                    var worst = 0.0
                    var at: Pt? = null
                    // 축 너머 노즈가 거울 점을 깎는 것은 바닥 단면(축을 가로지르는 같은 면)만 — 그 밖은 옛 글이 맞은편 벽을 판 것이라 깎은 것으로 치지 않는다
                    for ((i, sg) in segs.withIndex()) for (q in samples(sg, step)) {
                        // 옛 글이 깎은(덜 깎임 ≤ tol) 점만 — 둘 다 안 깎는 점(비운 요소 · 끝 너머)은 견주지 않는다
                        val uo = uncut(q, oldDirect.feeds, r, i == bottom)
                        if (uo > tol) continue
                        val w = uncut(q, d.feeds, r, i == bottom) - max(0.0, uo)
                        if (w > worst) {
                            worst = w
                            at = q
                        }
                    }
                    t.keepMax("시작X0 덜 깎임이 옛 글보다 나빠진 양", worst)
                    if (worst > tol) t.fail("RC2_D_UNCUT_WORSE_THAN_OLD$soft") { "${xz(at!!)} 에서 ${"%.4f".format(worst)} 더 남음\n옛 글: ${oldSol!!.ncG40.joinToString(" ")}\n${tag()}" }
                }
            }
        }
        // ---- 기계 보정(형식 C — 회사 기계). 이번 diff 는 G41/G42 를 바꾸지 않았다
        val m = runProg(s.ncG41G42, r, s.tip, false, segs)
        val honest = TaperWarning.ENTRY_NOT_FOUND in res.warnings || TaperWarning.EXIT_NOT_FOUND in res.warnings
        val mSoft = soft + (if (honest) "(기존 — 진입 · 빼는 길 못 찾음 경고)" else "")
        // 오목 R ≤ 노즈(모서리 R · T1–T2 호)는 G41/G42 가 간섭 알람을 낼 수 있다고 이미 알린다(CONCAVE_R_LE_NOSE)
        val rWarned = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings
        if (m.stop != null) t.fail("RC2_M_STOP_${m.stop.substringBefore('@')}_$side$mSoft${if (rWarned && !honest) "(기존 — 오목 R ≤ 노즈 경고)" else ""}") { "${m.stop}\n${tag()}" }
        else {
            val md = m.dig!!
            t.keepMax("기계 보정 파고듦 $side${if (fit) "" else "(못 넣는 꼴)"}${if (honest) "(경고 있음)" else ""}", md.depth)
            if (md.depth > tol) t.fail("RC2_M_GOUGE_${if (md.far) "맞은편" else "도면"}_$side${if (f.reverse) "_역방향" else ""}$mSoft") { "${digText(m, s.ncG41G42)}\n${tag()}" }
            val uc = uncut(qB, m.feeds, r)
            if (fit && !honest && uc > tol) t.fail("RC2_M_CENTER_UNCUT_$side") { "바닥 중심 덜 깎임 ${"%.4f".format(uc)}\n${tag()}" }
        }
        // ---- 경고 글(시작 X0 의 ENTRY_PAST_AXIS 는 바닥 글)
        if (bottomStart && TaperWarning.ENTRY_PAST_AXIS in res.warnings) {
            val text = taperWarningText(TaperWarning.ENTRY_PAST_AXIS, f)
            if (!text.startsWith("바닥 단면을 중심(X0)부터")) t.fail("RC2_TEXT_바닥글아님") { "$text\n${tag()}" }
            // 글이 말하는 표준 팁 인선 X(2r)가 글의 진입 X 와 같나
            if (f.tip == null) {
                val x0 = s.ncG40.firstNotNullOfOrNull { l -> ncWords(l).firstOrNull { it.startsWith("X") } }
                val said = "인선 X" + Fmt.nc(2 * r)
                if (said !in text) t.fail("RC2_TEXT_표준팁X없음") { "$text\n${tag()}" }
                if (x0 != "X" + Fmt.word(2 * r)) t.note("알림: 표준 팁 첫 X 글 $x0 ≠ 2r(접근 줄이 다른 X)")
            }
        }
        log?.appendLine("${desc(f)} · ${res.warnings}\n  G40 ${s.ncG40.joinToString(" ")} · 파고듦 ${d.dig?.depth} 멈춤 ${d.stop}\n  G41 ${s.ncG41G42.joinToString(" ")} · 파고듦 ${m.dig?.depth} 멈춤 ${m.stop}")
    }

    /** RC-2 재현 그대로(내경 형상 2 · 뒤집기 · X1 4 Z1 −4 → X2 5.2 Z2 −2 · R3.634 오목 / 직선 · 노즈 1.6(직선 1.2 도) · Z 여유 5 · 시작 X0 · 팁 표준) */
    @Test
    fun rc2Repro() {
        val t = Tally()
        val log = StringBuilder()
        for (arc in listOf(true, false)) for (noseR in listOf(1.2, 1.6)) for (reverse in listOf(false, true)) {
            fun mz(z: Double) = if (reverse) -30.0 - z else z
            val f = SlopeForm(
                kind = TaperKind.INNER, type = 2, reverse = reverse, noseR = noseR, tip = null, x = listOf(4.0, 5.2), z = listOf(mz(-4.0), mz(-2.0)),
                angle = null, zClear = 5.0, corners = listOf(0.0, 0.0), start = 0.0, end = null, flip = true, angleRef = AngleRef.X,
                arc = arc, arcR = if (arc) 3.634 else null, arcConcave = true,
            )
            val s = TaperSolve.solve(f).solution
            if (s != null && s.ncG40.take(2) == listOf("G0X0.Z${if (reverse) "-31." else "1."}", "G1Z${if (reverse) "-26." else "-4."}")) t.fail("RC2_REPRO_OLD_NC") { desc(f) }
            rc2Case(f, true, t, log)
        }
        outFile("rc2-repro.txt").writeText(log.toString())
        println(log)
        finish(t, "RC-2 재현", "rc2-repro-tally.txt")
    }

    /**
     * RC-2 둘레: 바닥 시작 X0(뒤집기) · 끝 X0(정방향) × 형상 2 · 4 × 오목 · 볼록 · 직선 × 화면 정 · 역방향 × 바닥 Ø 2 · 3 · 4 · 6 · 12 × 반경 차 0.6 · 2 ×
     * 노즈 0.2 · 0.4 · 0.8 · 1.2 · 1.6 × Z 여유 0 · 1 · 5 × 팁 표준 · 3 · 5 · 8 — 다 돌린다. 그 위에 팁 0 · 1 · 4 · 6 · 7, 다른 요소 넣음, 모서리 R 0.3,
     * 깊이 0.6 · R 배수 1 · 3 을 씨앗 고정으로 섞는다
     */
    @Test
    fun rc2BottomGrid() {
        val t = Tally()
        val rnd = Random(1114_22)
        var n = 0
        for (bottomStart in listOf(true, false)) for (type in listOf(2, 4)) for (shape in 0..2) for (reverse in listOf(false, true))
            for (d1 in listOf(2.0, 3.0, 4.0, 6.0, 12.0)) for (dr in listOf(0.6, 2.0)) for (noseR in listOf(0.2, 0.4, 0.8, 1.2, 1.6))
                for (zc in listOf(0.0, 1.0, 5.0)) for (tip in listOf(null, 3, 5, 8)) {
                    val f = bottomForm(type, bottomStart, reverse, d1, dr, 2.0, shape, 1.5, noseR, tip, zc, false, 0.0) ?: continue
                    n++
                    rc2Case(f, bottomStart, t, null)
                }
        // 섞음
        repeat(3000) {
            val bottomStart = rnd.nextInt(3) != 0
            val f = bottomForm(
                type = if (rnd.nextBoolean()) 2 else 4, bottomStart = bottomStart, reverse = rnd.nextBoolean(),
                d1 = listOf(2.0, 2.5, 3.0, 4.0, 5.0, 8.0, 12.0)[rnd.nextInt(7)], dr = listOf(0.3, 0.6, 1.0, 2.0, 4.0)[rnd.nextInt(5)],
                dz = listOf(0.6, 2.0, 5.0)[rnd.nextInt(3)], shape = rnd.nextInt(3), factor = listOf(1.0, 1.5, 3.0)[rnd.nextInt(3)],
                noseR = listOf(0.2, 0.4, 0.8, 1.2, 1.6)[rnd.nextInt(5)], tip = listOf(null, 0, 1, 2, 3, 4, 5, 6, 7, 8)[rnd.nextInt(10)],
                zc = listOf(0.0, 1.0, 5.0)[rnd.nextInt(3)], otherLeg = rnd.nextBoolean(), cr = if (rnd.nextInt(4) == 0) 0.3 else 0.0,
            ) ?: return@repeat
            n++
            rc2Case(f, bottomStart, t, null)
        }
        t.note("사례 수 = $n")
        finish(t, "RC-2 내경 바닥 X0 격자", "rc2-grid.txt")
    }

    /**
     * 시작 X 가 축에 아주 가깝지만 0 이 아닌 꼴(0.0005 · 0.001 · 0.002 직경 — 화면에 칠 수 있는 0.001 둘레)과 0 은 글이 거의 같아야 한다(고친 갈래는 X ≤ 1e-9 만).
     * 바닥 Ø4 → Ø5.2 · 노즈 1.6 · 표준 팁: X0.001 시작이 옛 글처럼 노즈 중심을 축 너머로 보내 맞은편을 파나.
     * 고침3(재확인2 B2-V2-1)부터 실패 시험: 인선을 넣은 X 에 두면 노즈 중심이 축 너머인 값도 X0 처럼 노즈 중심을 축에 세운다 — 파고듦이 있으면 실패
     */
    @Test
    fun rc2NearZeroStart() {
        val t = Tally()
        val log = StringBuilder()
        for (atStart in listOf(true, false)) for (arc in listOf(true, false)) for (noseR in listOf(0.8, 1.2, 1.6)) for (st in listOf(0.0, 0.0005, 0.001, 0.002, 0.01)) {
            // 끝 쪽은 정방향(뒤집지 않음)으로 같은 구멍의 바닥을 중심까지
            val f = SlopeForm(
                kind = TaperKind.INNER, type = 2, reverse = false, noseR = noseR, tip = null,
                x = if (atStart) listOf(4.0, 5.2) else listOf(5.2, 4.0), z = if (atStart) listOf(-4.0, -2.0) else listOf(-2.0, -4.0),
                angle = null, zClear = 5.0, corners = listOf(0.0, 0.0), start = if (atStart) st else null, end = if (atStart) null else st, flip = atStart,
                angleRef = AngleRef.X, arc = arc, arcR = if (arc) 3.634 else null, arcConcave = true,
            )
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s == null) {
                t.fail("RC2_NEAR_ZERO_STOP") { "${desc(f)} ${res.warnings}" }
                continue
            }
            val d = runProg(s.ncG40, noseR, s.tip, true, s.drawing.segs)
            log.appendLine("${if (atStart) "시작" else "끝"} ${if (arc) "원호" else "직선"} 노즈 $noseR X$st · ${s.ncG40.joinToString(" ")} · 파고듦 ${d.dig?.depth} ${if (d.dig?.far == true) "맞은편" else "도면"} · ${res.warnings}")
            if (st == 0.001 && TaperWarning.ENTRY_PAST_AXIS in res.warnings) log.appendLine("  ENTRY_PAST_AXIS 글: " + taperWarningText(TaperWarning.ENTRY_PAST_AXIS, f))
            if ((d.dig?.depth ?: 0.0) > tol) t.fail("RC2_NEAR_ZERO_${if (atStart) "START" else "END"}_GOUGE(X ${st})") { "${digText(d, s.ncG40)}\n${desc(f)}\n${s.ncG40.joinToString(" ")}" }
        }
        outFile("rc2-near-zero.txt").writeText(log.toString())
        println(log)
        finish(t, "RC-2 시작 X 가 0 둘레", "rc2-near-zero-tally.txt")
    }

    /**
     * 격자에서 나온 끝 X0 의 멈춤: 축으로 끝나는 단면(내경 바닥 끝 · 외경 앞면 끝)에서 팁 벡터 X 가 축 쪽인 팁(내경 3 · 4 · 8, 외경 표준 3 · 8)은
     * 마지막 꺾임의 노즈 중심 X 가 r 보다 작으면 BAD_LEG 로 멈추나 — 같은 꼴의 다른 팁 · 뒤집은 시작 X0 은 풀리나.
     * 내경 형상 2 · X1 3.2 Z1 −4 → X2 2 Z2 −6(오목 R5.451 · 직선) · 노즈 0.8 · Z 여유 0 · 끝 X0, 외경 형상 1 뒤집기 · X1 3 Z1 −1 → X2 1(·1.6) Z2 0 · 노즈 0.8 · Z 여유 1 · 끝 X0.
     * 고침3(재확인2 B2-V2-2)부터 실패 시험: BAD_LEG 를 노즈 중심을 축에 세운 끝점′ 으로 봐 모두 풀리고, 직접 보정 글이 GSim 에서 멈추거나 파지 않는다
     */
    @Test
    fun axisEndStopsByTip() {
        val log = StringBuilder()
        val t = Tally()
        fun one(tag: String, f: SlopeForm) {
            val res = TaperSolve.solve(f)
            val s = res.solution
            val dig = s?.let { runProg(it.ncG40, f.noseR, it.tip, true, it.drawing.segs) }
            log.appendLine("$tag · 팁 ${f.tip} · ${res.warnings} · ${s?.ncG40?.joinToString(" ") ?: "멈춤"}${dig?.let { " · 파고듦 ${it.dig?.depth} 멈춤 ${it.stop}" } ?: ""}")
            if (s == null) t.fail("AXIS_END_STOP_${res.warnings.joinToString("+")}") { "$tag · ${desc(f)}" }
            else if (dig!!.stop != null) t.fail("AXIS_END_D_STOP") { "$tag · ${desc(f)} ${dig.stop}" }
            else if (dig.dig!!.depth > tol) t.fail("AXIS_END_D_GOUGE") { "$tag · ${digText(dig, s.ncG40)}" }
        }
        for (arc in listOf(true, false)) for (tip in listOf(null, 3, 4, 8, 5)) {
            val base = SlopeForm(
                kind = TaperKind.INNER, type = 2, reverse = false, noseR = 0.8, tip = tip, x = listOf(3.2, 2.0), z = listOf(-4.0, -6.0),
                angle = null, zClear = 0.0, corners = listOf(0.0, 0.0), start = null, end = 0.0, flip = false, angleRef = AngleRef.X,
                arc = arc, arcR = if (arc) 5.451 else null, arcConcave = true,
            )
            one("내경 끝 X0 ${if (arc) "오목 R5.451" else "직선"}", base)
            one("  같은 구멍 뒤집어 시작 X0", base.copy(x = base.x.reversed(), z = base.z.reversed(), start = 0.0, end = null, flip = true))
        }
        for (x2 in listOf(1.0, 1.4, 1.6)) for (tip in listOf(null, 2, 8)) {
            val f = SlopeForm(
                kind = TaperKind.OUTER, type = 1, reverse = false, noseR = 0.8, tip = tip, x = listOf(3.0, x2), z = listOf(-1.0, 0.0),
                angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = null, end = 0.0, flip = true, angleRef = AngleRef.X,
            )
            one("외경 형상 1 뒤집기 앞면 끝 X0 · X2 $x2", f)
            one("  화면 역방향", f.copy(reverse = true, z = f.z.map { it?.let { v -> -30.0 - v } }))
        }
        outFile("axis-end-stops.txt").writeText(log.toString())
        println(log)
        finish(t, "축으로 끝나는 단면 · 팁", "axis-end-stops-tally.txt")
    }

    // ================================================================ RC-1 — 0.001 반올림 띠

    /** 재확인 RC-1 꼴: 외경 형상 4 · X1 30 Z1 −2 → X2 (30 + 2r) Z2 · 오목 R · 시작 빈칸 · 끝 X(30 + 2r + 6) · Z 여유 5. 뒤집기 · 화면 역방향 */
    private fun rc1Form(rr: Double, r: Double, z2: Double, flip: Boolean, reverse: Boolean, tip: Int?, inner: Boolean = false): SlopeForm {
        fun mz(z: Double) = if (reverse) -30.0 - z else z
        // 내경 형상 4 는 큰 내경 X(30 + 2r) → 안쪽 단면 쪽 X30, 단면은 축 쪽으로
        val x1 = if (inner) 30.0 + 2 * r else 30.0
        val x2 = if (inner) 30.0 else 30.0 + 2 * r
        val endX = if (inner) x2 - 6.0 else x2 + 6.0
        return SlopeForm(
            kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = 4, reverse = reverse, noseR = r, tip = tip,
            x = if (flip) listOf(x2, x1) else listOf(x1, x2), z = if (flip) listOf(mz(z2), mz(-2.0)) else listOf(mz(-2.0), mz(z2)),
            angle = null, zClear = 5.0, corners = listOf(0.0, 0.0), start = if (flip) endX else null, end = if (flip) null else endX, flip = flip,
            angleRef = AngleRef.X, arc = true, arcR = rr, arcConcave = true,
        )
    }

    @Test
    fun rc1Band() {
        val t = Tally()
        val log = StringBuilder()
        val geos = listOf(1.2 to 0.4, 2.0 to 0.4, 1.2 to 0.8, 0.9 to 0.2, 3.0 to 1.2, 1.2 to 0.4004, 5.0 to 0.8)
        for (inner in listOf(false, true)) for ((rr, r) in geos) for (flip in listOf(false, true)) for (reverse in listOf(false, true)) for (tip in listOf(null, 8)) {
            val exact = -2.0 - (rr - sqrt(rr * rr - r * r))
            var kept = 0
            var gone = 0
            var lastLine: Boolean? = null
            var flips = 0
            for (k in -750..750) {
                val z2 = exact + k * 2e-6
                val f = rc1Form(rr, r, z2, flip, reverse, tip, inner)
                val res = TaperSolve.solve(f)
                val s = res.solution
                if (s == null) {
                    t.note("멈춤 ${res.warnings.joinToString("+")}")
                    continue
                }
                val arcLine = s.ncG40.any { l -> !l.startsWith("(") && ncWords(l).any { it == "G2" || it == "G3" } }
                val warned = TaperWarning.NOSE_GT_SLOPE in res.warnings
                val progR = s.arc?.progR
                if (arcLine) kept++ else gone++
                if (lastLine != null && lastLine != arcLine) flips++
                lastLine = arcLine
                val text = { "R$rr 노즈 $r · Z2 ${"%.6f".format(z2)}(정확 ${"%.6f".format(exact)}) · ${desc(f)}\n호 줄 $arcLine · 경고 ${res.warnings} · 보정 R $progR\nG40: ${s.ncG40.joinToString(" ")}" }
                if (arcLine && warned) t.fail("RC1_헛경고(호 줄 있는데 NOSE_GT_SLOPE)", text)
                if (!arcLine && !warned) t.fail("RC1_놓침(호 줄 없는데 경고 없음)", text)
                if (arcLine != (progR != null)) t.fail("RC1_보정R_불일치", text)
                // 남은 짧은 호 줄은 시뮬레이터에서 돈다(직접 보정)
                if (arcLine && abs(k) < 400 && k % 7 == 0) {
                    val run = sim(s.ncG40, r, s.tip, feed = true)
                    if (run.stop != null) t.fail("RC1_짧은호_시뮬멈춤_${run.stop!!.code}", text)
                }
            }
            t.note("띠 ${if (inner) "내경" else "외경"} R$rr 노즈 $r: 남음 $kept · 빠짐 $gone")
            if (kept == 0 || gone == 0) t.note("알림: 띠에서 호 줄이 한쪽뿐(${if (inner) "내경" else "외경"} R$rr 노즈 $r)")
            if (flips > 2) t.note("알림: 호 줄 있음/없음이 띠 안에서 ${flips}번 바뀜")
            log.appendLine("${if (inner) "내경" else "외경"} R$rr 노즈 $r 뒤집기 $flip 역방향 $reverse 팁 $tip: 남음 $kept · 빠짐 $gone · 바뀜 $flips")
        }
        outFile("rc1-band.txt").writeText(log.toString())
        println(log)
        finish(t, "RC-1 0.001 반올림 띠", "rc1-band-tally.txt")
    }

    /** 재확인 보고의 재현 그대로(Z2 −2.069 · 노즈 0.4 · 오목 R1.2 · 직접 보정): 호 줄 없음 → 경고 · 보정 R 없음 */
    @Test
    fun rc1Repro() {
        val f = rc1Form(1.2, 0.4, -2.069, false, false, null)
        val res = TaperSolve.solve(f)
        val s = res.solution!!
        println("RC-1 재현: ${res.warnings} · 보정 R ${s.arc?.progR} · ${s.ncG40}")
        assertTrue(s.ncG40.none { l -> ncWords(l).any { it == "G2" || it == "G3" } }, "${s.ncG40}")
        assertTrue(TaperWarning.NOSE_GT_SLOPE in res.warnings && s.arc!!.progR == null, "${res.warnings} ${s.arc}")
    }

    // ================================================================ B-F3 — 시뮬레이터 R 낱말 한도

    @Test
    fun bf3BigArcRuns() {
        val t = Tally()
        val log = StringBuilder()
        for (inner in listOf(false, true)) for (convex in listOf(true, false)) for (rr in listOf(9999.0, 9999.999, 10000.0)) for (r in listOf(0.4, 1.2))
            for (flip in listOf(false, true)) for (reverse in listOf(false, true)) {
                fun mz(z: Double) = if (reverse) -30.0 - z else z
                val xs = if (inner) listOf(31.0, 30.0) else listOf(30.0, 31.0)
                val zs = listOf(-2.0, -6.0)
                val f = SlopeForm(
                    kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = 3, reverse = reverse, noseR = r, tip = null,
                    x = if (flip) xs.reversed() else xs, z = (if (flip) zs.reversed() else zs).map(::mz),
                    angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = if (flip) mz(-10.0) else null, end = if (flip) null else mz(-10.0),
                    flip = flip, angleRef = AngleRef.X, arc = true, arcR = rr, arcConcave = !convex,
                )
                val res = TaperSolve.solve(f)
                val s = res.solution
                if (s == null) {
                    t.fail("BF3_SOLVE_STOP") { "${desc(f)} ${res.warnings}" }
                    continue
                }
                val want = "R" + Fmt.word(if (convex) rr + r else rr - r)
                if (s.ncG40.none { l -> ncWords(l).any { it == want } }) t.fail("BF3_NO_PROG_R_WORD") { "$want ${desc(f)}\n${s.ncG40}" }
                val d = runProg(s.ncG40, r, s.tip, true, s.drawing.segs)
                val m = runProg(s.ncG41G42, r, s.tip, false, s.drawing.segs)
                log.appendLine("${desc(f)} · ${res.warnings}\n  G40 ${s.ncG40.joinToString(" ")} · 멈춤 ${d.stop} 파고듦 ${d.dig?.depth}\n  G41 ${s.ncG41G42.joinToString(" ")} · 멈춤 ${m.stop} 파고듦 ${m.dig?.depth}")
                if (d.stop != null) t.fail("BF3_D_STOP_${d.stop.substringBefore('@')}") { "${d.stop} ${desc(f)}\n${s.ncG40}" }
                if (m.stop != null) t.fail("BF3_M_STOP_${m.stop.substringBefore('@')}") { "${m.stop} ${desc(f)}\n${s.ncG41G42}" }
                d.dig?.let { t.keepMax("직접 보정 파고듦", it.depth) }
                m.dig?.let { t.keepMax("기계 보정 파고듦", it.depth) }
            }
        outFile("bf3-arc.txt").writeText(log.toString())
        finish(t, "B-F3 R 9999 · 10000 원호 글", "bf3-arc-tally.txt")
    }

    @Test
    fun bf3WordLimits() {
        fun codes(vararg l: String) = GText.parse(l.joinToString("\n")).issues.map { it.code }
        val bad = mutableListOf<String>()
        // 좌표 · I · K · ,C · ,R 은 10000 넘으면 그대로 NUMBER_TOO_BIG
        for (w in listOf("G1X10000.001", "G1Z-10000.001", "G1U10000.001", "G1W-10000.001", "G2X10.Z-1.I10000.001", "G2X10.Z-1.K-10000.001",
            "G1X10.,C10000.001", "G1X10.,R10000.001", "G0X10001.", "G1Z10000.002F.1")) {
            if (IssueCode.NUMBER_TOO_BIG !in codes(w)) bad += "$w → ${codes(w)}"
        }
        // 10000 안은 그대로 된다
        for (w in listOf("G1X10000.Z-10000.F.1", "G2X10.Z-1.I10000.", "G1X10.,C10000.")) {
            if (IssueCode.NUMBER_TOO_BIG in codes(w)) bad += "$w → ${codes(w)}"
        }
        // R 은 ±99999.999 까지
        for (w in listOf("G2X10.Z-1.R10000.4", "G2X10.Z-1.R99999.999", "G3X10.Z-1.R-99999.999", "G90X10.Z-1.R10000.5F.1")) {
            if (IssueCode.NUMBER_TOO_BIG in codes(w)) bad += "$w → ${codes(w)}"
        }
        for (w in listOf("G2X10.Z-1.R100000.", "G2X10.Z-1.R-100000.")) {
            if (IssueCode.NUMBER_TOO_BIG !in codes(w)) bad += "$w → ${codes(w)}"
        }
        println("B-F3 낱말 한도: 어긋남 ${bad.size}\n${bad.joinToString("\n")}")
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    /** 사이클의 R(G90 테이퍼 · G71/G72 물러남 · G74 물러남 · G76 다듬질 여유 · 나사 테이퍼)이 10000 을 넘어도 시뮬레이터가 끝난다(멈춰도 된다 — 던지거나 오래 돌지 않음) */
    @Test
    fun bf3CycleRFinishes() {
        val progs = listOf(
            "G90 테이퍼 R50000" to listOf("G0X40.Z2.", "G90X38.Z-20.R-50000.F.2", "G0X200.Z100."),
            "G90 테이퍼 R99999.999" to listOf("G0X40.Z2.", "G90X38.Z-20.R99999.999F.2", "G0X200.Z100."),
            "G92 나사 R50000" to listOf("G0X40.Z2.", "G92X38.Z-20.R-50000.F1.5", "G0X200.Z100."),
            "G94 단면 R50000" to listOf("G0X42.Z2.", "G94X20.Z-1.R-50000.F.2", "G0X200.Z100."),
            "G71 물러남 R50000" to listOf("G0X42.Z2.", "G71U1.R50000.", "G71P10Q20U.4W.1F.2", "N10G0X20.", "G1Z-20.", "N20X42.", "G0X200.Z100."),
            "G72 물러남 R50000" to listOf("G0X42.Z2.", "G72W1.R50000.", "G72P10Q20U.4W.1F.2", "N10G0Z-10.", "G1X20.", "N20Z2.", "G0X200.Z100."),
            "G74 물러남 R50000" to listOf("G0X0.Z2.", "G74R50000.", "G74Z-20.Q2000F.1", "G0X200.Z100."),
            "G75 물러남 R50000" to listOf("G0X42.Z-10.", "G75R50000.", "G75X30.P1000F.1", "G0X200.Z100."),
            "G76 다듬질 여유 R50000" to listOf("G0X42.Z5.", "G76P010060Q100R50000.", "G76X38.16Z-20.P920Q300F1.5", "G0X200.Z100."),
            "G76 나사 테이퍼 R-50000" to listOf("G0X42.Z5.", "G76P010060Q100R.05", "G76X38.16Z-20.R-50000.P920Q300F1.5", "G0X200.Z100."),
        )
        val bad = mutableListOf<String>()
        for ((name, body) in progs) {
            val text = (listOf("T0101", "G97S500M03", "G99") + body + "M30").joinToString("\n")
            var out: String? = null
            var err: Throwable? = null
            val started = System.nanoTime()
            val th = thread(isDaemon = true) {
                try {
                    val run = GSim.run(SimInput(text, MachineSetup(), mapOf(1 to ToolOffset(0.4, 3))))
                    out = "멈춤 ${run.stop?.code} · 걸음 ${run.steps.size} · 문제 ${run.issues.map { it.code }.distinct()}"
                } catch (e: Throwable) {
                    err = e
                }
            }
            th.join(30_000)
            val sec = (System.nanoTime() - started) / 1e9
            when {
                th.isAlive -> bad += "$name: 30초 넘게 돎"
                err != null -> bad += "$name: 던짐 $err"
                else -> println("B-F3 사이클 $name: $out · ${"%.2f".format(sec)}초")
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }
}
