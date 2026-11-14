package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 재검(2026-11-14 — 테이퍼 원호 빈 좌표 자동 값의 검증 뒤 고침을 다시 확인). 고침 단계가 '고침' 으로 판정한 finding(B-F1 · B-F2 · B-F4 · R-VR1 · R-VR3 · R-VR6)을
 * 원래 재현 입력으로 다시 깨 보고, 고친 diff(`git diff 615bcad`)가 둘레에서 새로 깨뜨린 것을 찾는다. 고친 사람의 시험 · 계산기 안쪽 함수를 쓰지 않는다 —
 * 화면이 부르는 길(SlopeForm → TaperSolve.solve)과 시뮬레이터(GSim)로 잰다. 요약은 build/verify1114arcrecheck/ 에 쓴다
 */
class Verify1114ArcRecheckTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** 파고듦 허용(mm) */
    private val tol = 2e-3

    private fun outFile(name: String) = File("build/verify1114arcrecheck").also { it.mkdirs() }.resolve(name)

    // ================================================================ 기하(제품 코드의 internal 도우미를 쓰지 않는다)

    private fun unit(v: Pt) = v * (1 / v.length)
    private fun left(v: Pt) = Pt(-v.x, v.z)
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

    private fun tanAt(s: Seg, end: Boolean): Pt = when (s) {
        is Line -> unit(s.end - s.start)
        is Arc -> unit((if (end) s.end else s.start) - s.center).let { if (s.cw) left(it) * -1.0 else left(it) }
    }

    private fun xz(p: Pt) = "X%.4f Z%.4f".format(p.xDia, p.z)

    // ================================================================ 시뮬레이터 · 파고듦

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

    private fun moves(run: SimRun) = run.steps.filterIsInstance<MoveStep>()

    /** 글의 첫 이동 줄(공구 교환 자리에서 오는 G00) 번호(프로그램 줄) */
    private fun firstLine(nc: List<String>) = pre.size + nc.indexOfFirst { !it.startsWith("(") } + 1

    // ================================================================ 화면 칸

    private fun desc(f: SlopeForm) =
        "${f.kind.label} 형상 ${f.type}${if (f.reverse) " 역방향" else ""}${if (f.flip) " 뒤집기" else ""} · X${f.x} Z${f.z} · R${f.arcR} ${if (f.arcConcave) "오목" else "볼록"} · " +
            "노즈R ${f.noseR} · 팁 ${f.tip} · 모서리 R ${f.corners} · 시작 ${f.start} · 끝 ${f.end} · Z 여유 ${f.zClear}"

    /**
     * 그림(거울 전) Δz · Δr 의 네 좌표 화면 칸(Verify1114ArcAutoSimTest.form 과 같은 꼴). 오른쪽 점 Z −2(역방향은 거울), 작은 지름 x0,
     * legR · legL = 앞뒤 요소 길이(null = 비움)
     */
    private fun form4(
        inner: Boolean, type: Int, reverse: Boolean, flip: Boolean, convex: Boolean, noseR: Double, tip: Int?,
        dz: Double, dr: Double, arcR: Double, cr: Double, legR: Double?, legL: Double?, zClear: Double, x0: Double,
    ): SlopeForm {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        val zR = if (reverse) dz + 2.0 else -2.0
        val zL = zR - dz
        val xR = if (up > 0) x0 else x0 + 2 * dr
        val xL = xR + up * 2 * dr
        val sg = if (reverse) -1.0 else 1.0
        val startR = legR?.let { if (type <= 2) max(0.0, xR - up * 2 * it) else sg * (zR + it) }
        val endL = legL?.let { if (type == 2 || type == 4) max(0.0, xL + up * 2 * it) else sg * (zL - it) }
        return SlopeForm(
            kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = type, reverse = reverse, noseR = noseR, tip = tip,
            x = if (flip) listOf(xL, xR) else listOf(xR, xL), z = if (flip) listOf(sg * zL, sg * zR) else listOf(sg * zR, sg * zL),
            angle = null, zClear = zClear, corners = listOf(cr, cr), start = if (flip) endL else startR, end = if (flip) startR else endL, flip = flip,
            angleRef = AngleRef.X, arc = true, arcR = arcR, arcConcave = !convex,
        )
    }

    private fun blanked(f: SlopeForm, blank: Int?): SlopeForm = when (blank) {
        null -> f
        0 -> f.copy(x = listOf(null, f.x[1]))
        1 -> f.copy(z = listOf(null, f.z[1]))
        2 -> f.copy(x = listOf(f.x[0], null))
        else -> f.copy(z = listOf(f.z[0], null))
    }

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
        println(text.lineSequence().take(60).joinToString("\n"))
        // '(기존' 이 붙은 갈래는 고친 갈래 밖(고치기 전부터 · 넣을 수 없는 꼴)이라 알리기만 한다
        val hard = t.count.filterKeys { "(기존" !in it }
        assertTrue(hard.isEmpty(), "$title: 실패 갈래 $hard — build/verify1114arcrecheck/$file")
    }

    // ================================================================ 직접 보정 · 기계 보정 · F4 눈(격자 공용)

    /** 직접 보정 글: 멈춤 0 · 첫 줄(공구 교환 자리에서 오는 G00) 뒤 노즈가 도면 · 맞은편 벽을 파지 않음. 첫 줄 파고듦 · 급송 틈은 알림만 */
    private fun checkDirect(f: SlopeForm, res: TaperResult, s: TaperSolution, t: Tally, tag: String) {
        val nc = s.ncG40
        val r = f.noseR
        val run = sim(nc, r, s.tip, feed = true)
        val text = { "$tag ${desc(f)}\n경고 ${res.warnings}\n${nc.joinToString("\n")}" }
        val stop = run.stop
        if (stop != null) {
            t.fail("D_STOP_${stop.code}") { "${stop.line - pre.size}줄\n${text()}" }
            return
        }
        val first = firstLine(nc)
        val all = moves(run)
        val segs = s.drawing.segs
        val inner = f.kind.inner
        val d = dig(all.filter { it.line > first }, segs, r, inner)
        // 내경 T 점 반경이 노즈R 보다 작으면 공구가 구멍에 못 들어간다(넣을 수 없는 꼴 — 계산기는 ENTRY · EXIT_NOT_FOUND 등으로 알린다)
        val tooSmall = inner && s.points.minOf { it.vertex.x } < r + 0.1
        if (!tooSmall) t.keepMax("직접 보정 파고듦(첫 줄 뒤 · 구멍 ≥ 노즈)", d.depth)
        // 내경 바닥 단면을 중심부터 · 까지(X0, 또는 인선을 거기 두면 노즈 중심이 축 너머인 X — 팁 벡터 X 가 바깥인 표준 · 1 · 2 · 6 의 X < 2r): 노즈 중심을 축에 세우는
        // 고친 갈래(재확인 RC-2 · 재확인2 B2-V2-1)
        fun atAxis(x: Double?) = x == 0.0 || (x != null && x < 2 * r && (f.tip ?: 2) in listOf(1, 2, 6))
        val (fv, bv) = legsVertical(f.kind, f.type, f.flip)
        val axisStart = inner && fv && atAxis(f.start)
        if (d.depth > tol) {
            val m = d.by!!
            val past = TaperWarning.ENTRY_PAST_AXIS in res.warnings
            // 고친 진입(F1)은 비운 시작의 갈래뿐 — 시작을 넣은 글의 진입 · 끝 쪽은 이번 diff 가 바꾸지 않았다. 다만 바닥 단면을 중심부터 깎는 시작은 고친 갈래라
            // 실패로 센다(재확인2 R2-R2V-3 — 전에는 그 꼴도 알림이라 회귀를 못 잡았다)
            val soft = if (tooSmall) "(기존 — 구멍 반경 < 노즈R + 0.1)" else if (f.start != null && !axisStart) "(기존 — 시작 넣음, 고친 갈래 밖)" else ""
            t.fail("D_GOUGE_${m.kind}_${if (d.far) "맞은편벽" else "도면"}" + (if (f.start == null) "_비운시작" else "") + (if (past) "_축너머경고" else "") + soft) {
                "${m.line - pre.size}줄 '${nc.getOrNull(m.line - pre.size - 1)}' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 가 ${xz(d.at!!)} 를 ${d.depth} 팜\n${text()}"
            }
        }
        val d0 = dig(all.filter { it.line == first }, segs, r, inner)
        if (d0.depth > tol) t.note("알림: 첫 줄(공구 교환 자리 X200 Z100 → 진입) 직선 급송이 도면을 팜(${if (d0.far) "맞은편" else "도면"})")
        // 급송(첫 줄 뒤)이 소재에 가장 가까이 간 틈 — 진입을 축에서 자르면 진입점이 깎는 점에 가까워질 수 있다(알림)
        val rap = all.filter { it.line > first && it.kind == MoveKind.RAPID }
        if (rap.isNotEmpty()) t.keepMax("직접 보정 급송 파고듦(− = 가장 좁은 틈)", dig(rap, segs, r, inner).depth)
        // ENTRY_PAST_AXIS 글(내경 · 바닥 단면을 중심까지 깎는 끝 밖): 고침 F1 의 '맞은편 구멍 벽' 말. 바닥 단면을 중심부터 깎는 시작은 바닥 글(재확인 RC-2 고침 —
        // 직접 보정 노즈 중심이 축에서 시작, X0 이 아니면 그 X 를 밝힌 글 — 재확인2 B2-V2-1)
        if (TaperWarning.ENTRY_PAST_AXIS in res.warnings && inner && !(bv && atAxis(f.end))) {
            val pastText = taperWarningText(TaperWarning.ENTRY_PAST_AXIS, f)
            if (axisStart) {
                if (!pastText.startsWith(if (f.start == 0.0) "바닥 단면을 중심(X0)부터" else "바닥 단면 시작(X")) t.fail("TEXT_ENTRY_PAST_AXIS_바닥시작") { text() }
            } else if ("맞은편 구멍 벽" !in pastText) t.fail("TEXT_ENTRY_PAST_AXIS_내경") { text() }
        }
    }

    /** G41/G42 글의 T1–T2 호 줄(0 부터 — 글 순서). 모서리 R 이 없을 때만 */
    private fun arcLine(nc: List<String>, rr: Double): Int? {
        val w = "R" + Fmt.word(rr)
        return nc.indexOfFirst { l -> !l.startsWith("(") && ncWords(l).any { it == w } }.takeIf { it >= 0 }
    }

    /**
     * 고침 F2 와 같은 뜻을 따로 세운 것: 글에 쓰는 0.001 값(끝점 · R — 제어기가 R 로 다시 구한 중심)의 T1–T2 호와 깎는 이웃 직선 사이 이음에서
     * 보정선 교점이 있나. 교점이 없는 이음 이름들(빈 목록 = 다 있음). 도면 · 보정 쪽은 글(화면 좌표)의 것
     */
    private fun f2Missing(d: List<Seg>, ai: Int, g41: Boolean, r: Double, cutPrev: Boolean, cutNext: Boolean): List<String> {
        fun q(v: Double) = Fmt.nc(v).toDouble()
        fun nc(p: Pt) = Pt(q(p.z), q(p.xDia) / 2)
        val a = d[ai] as Arc
        val s = nc(a.start)
        val e = nc(a.end)
        val rr = q(a.radius)
        val half = (e - s).length / 2
        val h = sqrt(max(0.0, rr * rr - half * half))
        val c = (s + e) * 0.5 + unit(left(e - s)) * (if (a.cw) -h else h)
        fun nrm(u: Pt) = if (g41) left(u) else left(u) * -1.0
        fun arcTan(p: Pt) = unit(p - c).let { if (a.cw) left(it) * -1.0 else left(it) }
        val out = mutableListOf<String>()
        for ((k, cut) in listOf(ai - 1 to cutPrev, ai + 1 to cutNext)) {
            val l = d.getOrNull(k) as? Line ?: continue
            if (!cut) continue
            val ul = unit(nc(l.end) - nc(l.start))
            val at = if (k < ai) s else e
            val ua = arcTan(at)
            val uA = if (k < ai) ul else ua
            val uB = if (k < ai) ua else ul
            if (abs(uA cross uB) < min(1e-3, 5e-4 / r) && (uA dot uB) > 0) continue
            val rho = if ((nrm(ua) dot (c - at)) > 0) rr - r else rr + r
            if (rho <= 1e-9) continue
            val p0 = at + nrm(ul) * r
            // Intersect.lineCircle 과 같은 허용(EPS 1e-9)
            val dd = abs((c - p0) cross ul)
            if (dd > rho + 1e-9) out += (if (k < ai) "앞 직선 → 호" else "호 → 뒤 직선") + "(거리−ρ ${dd - rho} · ρ $rho · 중심 ${xz(c)} · s ${xz(s)} · e ${xz(e)} · cw ${a.cw} · G41 $g41 · ul $ul · ua $ua)"
        }
        return out
    }

    private val junctionStops = setOf(IssueCode.COMP_ACUTE_OUTER, IssueCode.COMP_INTERFERENCE)

    /** 기계 보정 글을 GSim 으로(형식 [forms]): T1–T2 호 둘레 이음에서 멈추면 F2 를 놓친 것(오목 R ≤ 노즈 · 노즈 길 0 경고가 있으면 따로) */
    private fun checkMachine(f: SlopeForm, res: TaperResult, s: TaperSolution, t: Tally, tag: String, forms: List<CompStartup>) {
        val nc = s.ncG41G42
        val r = f.noseR
        val sa = s.arc ?: return
        val g41 = nc.first() == "G41"
        // 내 F2 다시 세움이 풀린 글에서 '교점 없음' 이면 계산기가 멈췄어야 한다(F2 를 따로 세운 것이 맞나)
        val cutPrev = f.start != null
        val cutNext = f.end != null
        val short = TaperWarning.SHORT_END_SKIPPED in res.warnings || TaperWarning.SHORT_END_PAST in res.warnings
        val miss = if (f.corners.all { it == 0.0 } && !short) f2Missing(s.drawing.segs, sa.index, g41, r, cutPrev, cutNext) else emptyList()
        if (miss.isNotEmpty()) t.fail("F2_REPLICA_SAYS_STOP_BUT_SOLVED") { "$tag $miss ${desc(f)}\n${nc.joinToString(" ")}" }
        val al = arcLine(nc, sa.r)
        for (form in forms) {
            val run = sim(nc, r, s.tip, form)
            val stop = run.stop ?: continue
            val li = stop.line - pre.size - 1
            val atArc = al != null && li in al..al + 1 && stop.code in junctionStops
            val warned = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings
            if (atArc && !warned) t.fail("M_STOP_호이음_${stop.code}(F2 놓침?)") { "$tag 형식 $form · ${li + 1}줄 '${nc.getOrNull(li)}' · ${desc(f)}\n경고 ${res.warnings}\n${nc.joinToString("\n")}" }
            else t.note("기계 보정 멈춤 ${stop.code}" + (if (atArc) " 호 이음" else "") + (if (warned) "(경고 있음)" else ""))
            return
        }
        t.note("기계 보정 돎")
    }

    /** 고침 F4: NOSE_GT_SLOPE 가 노즈 > 두 점 거리 아닌데 나면 호의 노즈 길이 없어야(progR null) 하고, 오목 R > 노즈인데 노즈 길이 없으면 경고가 있어야 한다 */
    private fun checkF4(f: SlopeForm, res: TaperResult, s: TaperSolution, t: Tally, tag: String) {
        val sa = s.arc ?: return
        val r = f.noseR
        val chord = (s.points[1].vertex - s.points[0].vertex).length
        val warned = TaperWarning.NOSE_GT_SLOPE in res.warnings
        if (warned && r <= chord && sa.progR != null) t.fail("F4_SPURIOUS_NOSE_GT_SLOPE") { "$tag ${desc(f)} · 두 점 거리 $chord · progR ${sa.progR}\n${res.warnings}" }
        if (f.arcConcave && sa.r - r > 1e-9 && sa.progR == null) {
            if (!warned) t.fail("F4_MISSING_NOSE_GT_SLOPE") { "$tag ${desc(f)}\n${res.warnings}" } else t.note("F4: 오목 호 노즈 길 0 → NOSE_GT_SLOPE")
        }
        if (warned && sa.progR == null) {
            // 원호 글은 '거의(또는 전혀)' 를 말해야 한다
            if ("전혀" !in taperWarningText(TaperWarning.NOSE_GT_SLOPE, f)) t.fail("F4_TEXT") { taperWarningText(TaperWarning.NOSE_GT_SLOPE, f) }
        }
    }

    // ================================================================ B-F1

    /** 사용자 화면 그대로(내경 형상 3 · 뒤집기 · X1 6.3 · Z1 빈칸 · X2 7.5 · Z2 −10.1 · 오목 R3.75). 화면 역방향은 z → −30 − z(Z2 −19.9) */
    private fun userForm(noseR: Double, zc: Double, reverse: Boolean, start: Double?, end: Double?, tip: Int? = null): SlopeForm {
        fun mz(z: Double) = if (reverse) -30.0 - z else z
        return SlopeForm(
            kind = TaperKind.INNER, type = 3, reverse = reverse, noseR = noseR, tip = tip, x = listOf(6.3, 7.5), z = listOf(null, mz(-10.1)),
            angle = null, zClear = zc, corners = listOf(0.0, 0.0), start = start?.let(::mz), end = end?.let(::mz), flip = true, angleRef = AngleRef.X,
            arc = true, arcR = 3.75, arcConcave = true,
        )
    }

    /**
     * F1 재현 그대로: 노즈R 1.2 · Z 여유 5 · 시작 빈칸 · 끝 빈칸(또는 −5) · 팁 표준 · 거울 둘. 고치기 전 글 `G0X-2.127Z-5.1` `Z-17.943` 이 맞은편 Ø6.3 벽을 0.3135 팠다.
     * 둘레: 노즈R 0.4~3 · Z 여유 0~20 · 시작 넣음 · 비표준 팁. 직접 보정 글의 첫 줄 뒤 노즈가 도면 · 맞은편 벽을 파지 않는다(멈춤 0)
     */
    @Test
    fun f1UserRepro() {
        val t = Tally()
        val log = StringBuilder()
        for (reverse in listOf(false, true)) for (end in listOf(null, -5.0)) {
            val f = userForm(1.2, 5.0, reverse, null, end)
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s == null) {
                t.fail("F1_REPRO_STOPS") { "${desc(f)} ${res.warnings}" }
                continue
            }
            val z1 = Fmt.fixed3(s.points[0].vertex.z)
            if (z1 != (if (reverse) "-17.865" else "-12.135")) t.fail("F1_REPRO_Z1") { "$z1 ${desc(f)}" }
            log.appendLine("${desc(f)} · 경고 ${res.warnings}\n  G40: ${s.ncG40.joinToString(" ")}\n  G41/G42: ${s.ncG41G42.joinToString(" ")}")
            if (s.ncG40.any { it.startsWith("G0X-2.127") || it == "Z-17.943" }) t.fail("F1_REPRO_OLD_NC") { s.ncG40.joinToString(" ") }
            val run = sim(s.ncG40, 1.2, s.tip, feed = true)
            if (run.stop != null) {
                t.fail("F1_REPRO_SIM_STOP") { "${run.stop} ${desc(f)}" }
                continue
            }
            val d = dig(moves(run).filter { it.line > firstLine(s.ncG40) }, s.drawing.segs, 1.2, true)
            log.appendLine("  가장 깊은 곳 ${d.depth} (${if (d.far) "맞은편" else "도면"}) · 줄 ${d.by?.let { it.line - pre.size }}")
            t.keepMax("재현 파고듦", d.depth)
            if (d.depth > tol) t.fail("F1_REPRO_GOUGE") { "${d.depth} ${if (d.far) "맞은편" else "도면"} ${desc(f)}\n${s.ncG40.joinToString(" ")}" }
            checkF4(f, res, s, t, "재현")
        }
        // 둘레: 노즈R · Z 여유 · 시작 · 끝 · 팁
        for (reverse in listOf(false, true)) for (noseR in listOf(0.4, 0.8, 1.2, 1.6, 2.0, 2.4, 3.0)) for (zc in listOf(0.0, 0.5, 1.0, 2.0, 5.0, 8.0, 10.0, 20.0))
            for (legs in 0..3) for (tip in listOf(null, 1, 3, 4, 8)) {
                if (tip != null && (legs != 0 || zc !in listOf(1.0, 5.0))) continue
                val start = if (legs and 1 != 0) -10.1 - sqrt(0.6 * 6.9) - 3.0 else null
                val end = if (legs and 2 != 0) -5.0 else null
                val f = userForm(noseR, zc, reverse, start, end, tip)
                val res = TaperSolve.solve(f)
                val s = res.solution
                if (s == null) {
                    t.note("둘레 멈춤 ${res.warnings}")
                    continue
                }
                t.note("둘레 풀림")
                checkDirect(f, res, s, t, "둘레")
                checkF4(f, res, s, t, "둘레")
            }
        outFile("f1-user-nc.txt").writeText(log.toString())
        println(log)
        finish(t, "F1 사용자 화면 재현 · 둘레", "f1-user.txt")
    }

    /**
     * F1 의 둘레(작은 구멍): 고침은 내경에서 비운 시작의 T1–T2 호 접선 진입을 축에서 자른다 — 그 갈래가 서는 작은 구멍(Ø2~10)에서 외경도 섞어
     * 형상 1~5 × 볼록 · 오목 × 뒤집기 × 거울 × 빈 칸(없음 · 넷) × 노즈R × Z 여유 × 시작 · 끝을 씨앗 고정으로 골라 직접 보정 · 기계 보정 글을 GSim 으로
     */
    @Test
    fun f1SmallHoleGrid() {
        val t = Tally()
        val rnd = Random(1114_2)
        val geos = listOf(2.0 to 0.6, 1.0 to 1.0, 3.0 to 0.4, 0.6 to 1.5, 4.0 to 2.0)
        var n = 0
        for (inner in listOf(true, false)) for (type in 1..5) for (convex in listOf(true, false)) for (flip in listOf(false, true)) for (reverse in listOf(false, true))
            for (blank in listOf(null, 0, 1, 2, 3)) for ((dz, dr) in geos) {
                if (!inner && rnd.nextInt(3) != 0) continue
                val x0 = listOf(2.0, 4.0, 6.3, 10.0)[rnd.nextInt(4)]
                val noseR = listOf(0.4, 0.8, 1.2, 1.6)[rnd.nextInt(4)]
                val zc = listOf(0.0, 1.0, 5.0, 10.0)[rnd.nextInt(4)]
                val tip = if (rnd.nextInt(6) == 0) 1 + rnd.nextInt(8) else null
                val legR = if (rnd.nextInt(10) < 3) 3.0 else null
                val legL = if (rnd.nextBoolean()) 3.0 else null
                val cr = if (rnd.nextInt(6) == 0) 0.3 else 0.0
                val minR = TaperCalc.arcMinR(x0, 0.0, x0 + 2 * dr, -dz) ?: continue
                val rr = round(minR * listOf(1.0, 1.5, 3.0)[rnd.nextInt(3)] * 1000) / 1000 + 0.001
                val f = blanked(form4(inner, type, reverse, flip, convex, noseR, tip, dz, dr, rr, cr, legR, legL, zc, x0), blank)
                n++
                val res = try {
                    TaperSolve.solve(f)
                } catch (e: Throwable) {
                    t.fail("SOLVE_THROW") { "${desc(f)}\n$e" }
                    continue
                }
                val s = res.solution
                if (s == null) {
                    t.note("멈춤 ${res.warnings.joinToString("+")}")
                    continue
                }
                t.note("풀림")
                checkDirect(f, res, s, t, "작은 구멍")
                checkMachine(f, res, s, t, "작은 구멍", listOf(CompStartup.C))
                checkF4(f, res, s, t, "작은 구멍")
            }
        t.note("사례 수 = $n")
        finish(t, "F1 둘레 — 작은 구멍 격자", "f1-grid.txt")
    }

    /**
     * 재확인 RC-2(고침 2026-11-14 — 처음에는 알림만): 뒤집어 바닥 단면을 중심(시작 X0)부터 깎으면 직접 보정이 인선을 X0 에 두어 노즈 중심이 축 너머 r 로
     * 내려가 맞은편 벽을 팠다(원호 노즈 1.6 에서 0.630 · 직선 노즈 1.2 에서 0.089 · 1.6 에서 0.757). 끝 쪽 X0 처럼 노즈 중심을 축에 세워 시작한다 —
     * 원호 · 직선(같은 두 점) × 화면 정방향 · 역방향 × 노즈R 0.4~1.6: 멈춤 0 · 첫 줄 뒤 노즈가 도면 · 맞은편 벽을 파지 않음.
     * 내경 형상 2 · 뒤집기 · X1 4 · Z1 −4 · X2 5.2 · Z2 −2 · 오목 R3.634 · 시작 X0 · 끝 빈칸 · Z 여유 5
     */
    @Test
    fun bottomStartAtAxisFarWall() {
        val t = Tally()
        val log = StringBuilder()
        for (arc in listOf(true, false)) for (reverse in listOf(false, true)) for (noseR in listOf(0.4, 0.8, 1.2, 1.6)) {
            val f = SlopeForm(
                kind = TaperKind.INNER, type = 2, reverse = reverse, noseR = noseR, tip = null, x = listOf(4.0, 5.2),
                z = if (reverse) listOf(-26.0, -28.0) else listOf(-4.0, -2.0),
                angle = null, zClear = 5.0, corners = listOf(0.0, 0.0), start = 0.0, end = null, flip = true, angleRef = AngleRef.X,
                arc = arc, arcR = if (arc) 3.634 else null, arcConcave = true,
            )
            val tag = "${if (arc) "원호" else "직선"}${if (reverse) " 역방향" else ""} 노즈 $noseR"
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s == null) {
                log.appendLine("$tag 멈춤 ${res.warnings}")
                t.fail("STOP") { "$tag ${res.warnings}" }
                continue
            }
            val run = sim(s.ncG40, noseR, s.tip, feed = true)
            val d = if (run.stop == null) dig(moves(run).filter { it.line > firstLine(s.ncG40) }, s.drawing.segs, noseR, true) else null
            log.appendLine("$tag · 경고 ${res.warnings} · 멈춤 ${run.stop?.code} · 가장 깊음 ${d?.depth} ${if (d?.far == true) "맞은편" else "도면"} · ${s.ncG40.joinToString(" ")}")
            if (run.stop != null) t.fail("D_STOP_${run.stop!!.code}") { tag }
            if (d != null && d.depth > tol) t.fail("D_GOUGE_${if (d.far) "맞은편벽" else "도면"}_바닥중심시작") { "$tag ${d.depth}\n${s.ncG40.joinToString(" ")}" }
            // 기계 보정(이번 고침은 직접 보정만 — 확인만): 진입 길을 못 찾았다고 알린 글(ENTRY · EXIT_NOT_FOUND)의 파고듦은 알려진 한계로 따로 센다
            val mr = sim(s.ncG41G42, noseR, s.tip)
            val md = if (mr.stop == null) dig(moves(mr).filter { it.line > firstLine(s.ncG41G42) }, s.drawing.segs, noseR, true) else null
            val honest = TaperWarning.ENTRY_NOT_FOUND in res.warnings || TaperWarning.EXIT_NOT_FOUND in res.warnings
            log.appendLine("  기계 보정 · 멈춤 ${mr.stop?.code} · 가장 깊음 ${md?.depth} ${if (md?.far == true) "맞은편" else "도면"} · ${s.ncG41G42.joinToString(" ")}")
            if (mr.stop != null) t.fail("M_STOP_${mr.stop!!.code}" + if (honest) "(기존 — 진입 · 빼는 길 못 찾음 경고 있음)" else "") { tag }
            if (md != null && md.depth > tol) t.fail("M_GOUGE_${if (md.far) "맞은편벽" else "도면"}" + if (honest) "(기존 — 진입 · 빼는 길 못 찾음 경고 있음)" else "") { "$tag ${md.depth}" }
        }
        outFile("bottom-start.txt").writeText(log.toString())
        println(log)
        finish(t, "뒤집은 바닥 단면 중심 시작", "bottom-start-tally.txt")
    }

    // ================================================================ B-F2

    private fun f2Base(noseR: Double, start: Double?) = SlopeForm(
        kind = TaperKind.OUTER, type = 3, reverse = false, noseR = noseR, tip = null, x = listOf(30.0, 30.8), z = listOf(-2.0, null),
        angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = start, end = -5.566, flip = false, angleRef = AngleRef.X,
        arc = true, arcR = 0.6, arcConcave = true,
    )

    /** F2 재현 그대로: 외경 형상 3 · X1 30 Z1 −2 · X2 30.8 Z2 빈칸 · 오목 R0.6 · 노즈 0.4 · Z 여유 1 · 끝 Z −5.566 → 멈춤(네 좌표도). 노즈 0.399 는 풀리고 GSim C · A/B 가 돈다 */
    @Test
    fun f2Repro() {
        val bad = mutableListOf<String>()
        for (start in listOf(null, 1.0)) {
            val res = TaperSolve.solve(f2Base(0.4, start))
            if (res.solution != null || res.warnings != listOf(TaperWarning.NOSE_UNREACHABLE)) bad += "빈칸 시작 $start: ${res.warnings} 풀림 ${res.solution != null} ${res.solution?.ncG41G42}"
            val four = TaperSolve.solve(f2Base(0.4, start).copy(z = listOf(-2.0, -2.566)))
            if (four.solution != null || four.warnings != listOf(TaperWarning.NOSE_UNREACHABLE)) bad += "네 좌표 시작 $start: ${four.warnings}"
            if ("noseR" !in taperWarningFields(TaperWarning.NOSE_UNREACHABLE, f2Base(0.4, start))) bad += "NOSE_UNREACHABLE 이 노즈R 칸을 짚지 않음"
            val ok = TaperSolve.solve(f2Base(0.399, start))
            val s = ok.solution
            if (s == null) {
                bad += "노즈 0.399 시작 $start 멈춤 ${ok.warnings}"
                continue
            }
            for (form in listOf(CompStartup.C, CompStartup.AB)) sim(s.ncG41G42, 0.399, s.tip, form).stop?.let { bad += "노즈 0.399 시작 $start 형식 $form 멈춤 $it ${s.ncG41G42}" }
            sim(s.ncG40, 0.399, s.tip, feed = true).stop?.let { bad += "노즈 0.399 직접 보정 멈춤 $it" }
            // 노즈 0.4 로 0.399 의 기계 보정 글을 돌리면 그 호 뒤 줄에서 멈춘다(계산기 멈춤이 맞음)
            val run = sim(s.ncG41G42, 0.4, s.tip)
            val al = arcLine(s.ncG41G42, 0.6)
            if (run.stop == null || al == null || run.stop!!.line - pre.size - 1 != al + 1) bad += "노즈 0.4 로 돌린 0.399 글: ${run.stop} (호 줄 $al)"
        }
        assertEquals(emptyList(), bad)
    }

    private fun Double.r3() = round(this * 1000) / 1000

    /**
     * F2 의 헛멈춤 · 놓침 훑기: 둥근 값(R · 넣은 차 · 노즈)과 '못 받는 꼴' 경계 노즈(이음마다 R(1 + cos β)/2 를 0.001 로 반올림 ± 0.001)에서
     * - 계산기가 NOSE_UNREACHABLE 로 멈추면: 바로 작은 노즈로 푼 같은 도면의 기계 보정 글을 원래 노즈로 GSim — 내 F2 다시 세움이 '교점 없음' 인데 GSim 이 돌면 헛멈춤
     * - 풀리면: 기계 보정 글이 T1–T2 호 이음에서 멈추지 않는다(놓침)
     */
    @Test
    fun f2BoundaryScan() {
        val t = Tally()
        val rnd = Random(1114_3)
        val rs = listOf(0.4, 0.5, 0.6, 0.8, 1.0, 1.2, 1.5, 2.0, 3.0)
        val gs = listOf(0.2, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.5)
        var n = 0
        fun one(f: SlopeForm, tag: String) {
            n++
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s != null) {
                t.note("풀림")
                checkMachine(f, res, s, t, tag, if (n % 3 == 0) listOf(CompStartup.C, CompStartup.AB) else listOf(CompStartup.C))
                checkF4(f, res, s, t, tag)
                return
            }
            if (res.warnings != listOf(TaperWarning.NOSE_UNREACHABLE)) {
                t.note("멈춤 ${res.warnings.joinToString("+")}")
                return
            }
            val r = f.noseR
            val ref = listOf(r - 1e-4, r - 1e-3, r * 0.99, r * 0.95, r * 0.9, r * 0.8, r * 0.6).filter { it > 0 }
                .firstNotNullOfOrNull { rp -> TaperSolve.solve(f.copy(noseR = rp)).solution }
            if (ref == null) {
                t.note("NOSE_UNREACHABLE — 작은 노즈로도 안 풀려 못 가림")
                return
            }
            val sa = ref.arc!!
            val g41 = ref.ncG41G42.first() == "G41"
            val miss = f2Missing(ref.drawing.segs, sa.index, g41, r, f.start != null, f.end != null)
            val run = sim(ref.ncG41G42, r, ref.tip)
            val al = arcLine(ref.ncG41G42, sa.r)
            val stop = run.stop
            val atArc = stop != null && al != null && (stop.line - pre.size - 1) in al..al + 1 && stop.code in junctionStops
            when {
                miss.isNotEmpty() && atArc -> t.note("F2 멈춤 — 시뮬도 그 이음에서 멈춤")
                miss.isNotEmpty() && stop == null -> t.fail("F2_FALSE_STOP(시뮬은 돎)") { "$tag $miss ${desc(f)}\n작은 노즈 글 ${ref.ncG41G42.joinToString(" ")}" }
                // 다른 데서 멈춤: 호 줄보다 앞(작은 노즈의 진입 줄 등 — 그 이음까지 못 감, 못 가림)인지 뒤(이음을 지나감 — 헛멈춤)인지
                miss.isNotEmpty() -> {
                    val li = stop!!.line - pre.size - 1
                    if (al != null && li > al + 1) t.fail("F2_FALSE_STOP(시뮬이 호 이음을 지나 ${stop.code})") { "$tag $miss ${desc(f)}\n${li + 1}줄 · 작은 노즈 글 ${ref.ncG41G42.joinToString(" ")}" }
                    else t.note("F2 멈춤 — 시뮬은 호 이음 앞(${if (al == null) "호 줄 못 찾음" else "${li - al}줄"})에서 ${stop.code} — 못 가림")
                }
                stop == null -> t.note("F2 아닌 NOSE_UNREACHABLE(고치기 전부터) — 작은 노즈 글은 원래 노즈로도 돎")
                else -> t.note("F2 아닌 NOSE_UNREACHABLE(고치기 전부터) — 시뮬도 멈춤 ${stop.code}")
            }
            if (miss.isNotEmpty()) t.note("F2 멈춤 $tag")
        }
        for (inner in listOf(false, true)) for (type in 1..5) for (flip in listOf(false, true)) for (mode in 0..2) for (rr in rs) for (g in gs) {
            if (g > rr + 1e-9) continue
            val reverse = rnd.nextBoolean()
            val concave = rnd.nextInt(4) != 0
            val x0 = 30.0
            // mode 0: Z 빈칸(넣은 반경 차 g), 1: X 빈칸(넣은 Z 거리 g), 2: 네 좌표(Δz = g · Δr = 둥근 값, R 은 최소 R 이상일 때만).
            // 빈 쪽 차는 자리 값 R(푼 차 ≤ R) — 앞뒤 요소가 늘 3 mm 이상 남게(짧은 막힌 끝이면 G41/G42 가 그 요소를 빼 이음이 달라진다)
            val (dz, dr) = when (mode) {
                0 -> rr to g
                1 -> g to rr
                else -> g to listOf(0.2, 0.3, 0.4, 0.5)[rnd.nextInt(4)]
            }
            if (mode == 2 && (TaperCalc.arcMinR(x0, 0.0, x0 + 2 * dr, -dz) ?: 1e9) > rr + 1e-9) continue
            val blank = when (mode) {
                0 -> if (rnd.nextBoolean()) 1 else 3
                1 -> if (rnd.nextBoolean()) 0 else 2
                else -> null
            }
            val base = blanked(form4(inner, type, reverse, flip, !concave, 0.2, null, dz, dr, rr, 0.0, 3.0, 3.0, 1.0, x0), blank)
            for (noseR in listOf(0.2, 0.4, 0.8, 1.2)) if (noseR < rr) one(base.copy(noseR = noseR), "둥근 값")
            // 경계 노즈: 작은 노즈로 푼 도면의 호–직선 이음마다
            val s0 = TaperSolve.solve(base.copy(noseR = 0.05)).solution ?: continue
            val sa = s0.arc ?: continue
            val d = s0.drawing.segs
            val a = d[sa.index] as Arc
            val stars = listOfNotNull(d.getOrNull(sa.index - 1)?.let { tanAt(it, true) to tanAt(a, false) }, d.getOrNull(sa.index + 1)?.let { tanAt(a, true) to tanAt(it, false) })
                .filter { (u, v) -> abs(u cross v) > 1e-6 }.map { (u, v) -> a.radius * (1 + (u dot v).coerceIn(-1.0, 1.0)) / 2 }
            for (st in stars) for (dd in listOf(-0.001, 0.0, 0.001)) {
                val nr = (st.r3() + dd).r3()
                if (nr > 0.05 && nr < rr) one(base.copy(noseR = nr), "경계 노즈")
            }
        }
        t.note("사례 수 = $n")
        finish(t, "F2 둥근 값 · 경계 노즈 훑기", "f2-scan.txt")
    }

    // ================================================================ B-F4

    /**
     * F4 재현: 외경 형상 4 · X1 30 Z1 −2 · X2 30.8 · 오목 R1.2 · 노즈 0.4 · Z 여유 5 · 시작 빈칸 · 끝 X36.8.
     * 원래 입력(Z2 빈칸)은 R-VR1 결정(두 끝 형상은 다 넣은 점에서 접함)으로 이제 T1(Ø30)에 접한 다른 호(Z2 −2.894)가 된다 — 그 꼴은 노즈 길이 남아 경고가 없어야 한다.
     * F4 꼴(T2 단면에 접함 — 넣은 반경 차 = 노즈R)은 네 좌표(Z2 = −2 − (1.2 − √1.28), 0.001 로 적은 −2.069)와 T1 쪽을 비운 입력(Z1 빈칸 · Z2 −2.069 → T2 에서 접함)으로:
     * NOSE_GT_SLOPE · 보정 R 없음 · 직접 보정 글에 호 줄 없음 · 두 글 GSim 멈춤 0. 노즈 0.3 은 호가 남고 경고 없음
     */
    @Test
    fun f4Repro() {
        val bad = mutableListOf<String>()
        val log = StringBuilder()
        val f = SlopeForm(
            kind = TaperKind.OUTER, type = 4, reverse = false, noseR = 0.4, tip = null, x = listOf(30.0, 30.8), z = listOf(-2.0, null),
            angle = null, zClear = 5.0, corners = listOf(0.0, 0.0), start = null, end = 36.8, flip = false, angleRef = AngleRef.X,
            arc = true, arcR = 1.2, arcConcave = true,
        )
        // 원래 입력 그대로: 이제 T1 에서 접함
        val orig = TaperSolve.solve(f)
        val so = orig.solution
        log.appendLine("원래 입력(Z2 빈칸): 경고 ${orig.warnings} · Z2 ${so?.points?.get(1)?.vertex?.z} · tangentAt ${so?.arc?.tangentAt} · progR ${so?.arc?.progR}")
        if (so == null || so.arc?.tangentAt != 1 || Fmt.fixed3(so.points[1].vertex.z) != "-2.894" || so.arc?.progR == null || TaperWarning.NOSE_GT_SLOPE in orig.warnings) {
            bad += "원래 입력: ${orig.warnings} ${so?.points?.map { it.vertex }} tangentAt ${so?.arc?.tangentAt} progR ${so?.arc?.progR}"
        }
        val exact = -2.0 - (1.2 - sqrt(1.28))
        val shadow = listOf(
            "네 좌표 Z2 정확" to f.copy(z = listOf(-2.0, exact)),
            "네 좌표 Z2 −2.069" to f.copy(z = listOf(-2.0, -2.069)),
            "Z1 빈칸 · Z2 −2.069(T2 에서 접함)" to f.copy(z = listOf(null, -2.069)),
        )
        for ((name, g) in shadow) {
            val res = TaperSolve.solve(g)
            val s = res.solution
            log.appendLine("$name: 경고 ${res.warnings} · progR ${s?.arc?.progR} · tangentAt ${s?.arc?.tangentAt} · G40 ${s?.ncG40}")
            if (s == null) {
                bad += "$name 멈춤 ${res.warnings}"
                continue
            }
            // 노즈 길이 없으면 경고(F4), 남으면 없음 — −2.069 처럼 0.001 로 적은 값은 접하지 않아 길이 조금 남을 수 있다
            val gone = s.arc?.progR == null
            if (gone != (TaperWarning.NOSE_GT_SLOPE in res.warnings)) bad += "$name: 노즈 길 0 = $gone 인데 경고 ${res.warnings}"
            if (gone && s.ncG40.any { l -> ncWords(l).any { it == "G2" || it == "G3" } }) bad += "$name: 직접 보정 글에 호 줄 ${s.ncG40}"
            if (gone && "전혀" !in taperWarningText(TaperWarning.NOSE_GT_SLOPE, g)) bad += "$name 글 ${taperWarningText(TaperWarning.NOSE_GT_SLOPE, g)}"
            sim(s.ncG40, 0.4, s.tip, feed = true).stop?.let { bad += "$name 직접 보정 멈춤 $it" }
            sim(s.ncG41G42, 0.4, s.tip).stop?.let { bad += "$name 기계 보정 멈춤 $it" }
            if (name == "네 좌표 Z2 정확" && !gone) bad += "F4 꼴인데 노즈 길이 남음 progR ${s.arc?.progR}"
            val small = TaperSolve.solve(g.copy(noseR = 0.3))
            if (small.solution == null || TaperWarning.NOSE_GT_SLOPE in small.warnings || small.solution?.arc?.progR == null) bad += "$name 노즈 0.3: ${small.warnings} progR ${small.solution?.arc?.progR}"
        }
        outFile("f4.txt").writeText(log.toString())
        println(log)
        assertEquals(emptyList(), bad)
    }

    /**
     * F4 고침의 띠: 고침은 호의 노즈 길이 꼭 없어질 때(progR = null)만 알린다. 같은 꼴을 0.001 로 적은 네 좌표(Z2 −2.069 — 고치기 전 자동 값 그대로)나 그 둘레는
     * 직접 보정 글이 F4 꼴과 같이 호 줄 없이 나오는데(남은 노즈 길이 0.001 아래라 글에서 빠짐) 경고가 없고 결과 줄은 '보정 R 0.800' 이다.
     * 외경 형상 4 · X1 30 Z1 −2 · X2 30.8 · 오목 R1.2 · 노즈 0.4 · 끝 X36.8, Z2 를 정확한 접함 둘레 ±0.002 로
     */
    @Test
    fun f4RoundedShadowNoWarning() {
        val bad = mutableListOf<String>()
        val log = StringBuilder()
        val exact = -2.0 - (1.2 - sqrt(1.28))
        val zs = (-20..20).map { exact + it * 1e-4 } + listOf(-2.069, -2.068, -2.07)
        for (z2 in zs) {
            val g = SlopeForm(
                kind = TaperKind.OUTER, type = 4, reverse = false, noseR = 0.4, tip = null, x = listOf(30.0, 30.8), z = listOf(-2.0, z2),
                angle = null, zClear = 5.0, corners = listOf(0.0, 0.0), start = null, end = 36.8, flip = false, angleRef = AngleRef.X,
                arc = true, arcR = 1.2, arcConcave = true,
            )
            val res = TaperSolve.solve(g)
            val s = res.solution
            if (s == null) {
                log.appendLine("Z2 %.5f 멈춤 ${res.warnings}".format(z2))
                continue
            }
            val arcInNc = s.ncG40.any { l -> ncWords(l).any { it == "G2" || it == "G3" } }
            val warned = TaperWarning.NOSE_GT_SLOPE in res.warnings
            log.appendLine("Z2 %.5f 경고 ${res.warnings} · progR ${s.arc?.progR} · 직접 보정 글 호 줄 $arcInNc · ${s.ncG40.joinToString(" ")}".format(z2))
            if (!arcInNc && !warned) bad += "Z2 %.5f: 직접 보정 글에 호 줄이 없는데(남은 노즈 길 < 0.001) 경고 없음 · progR ${s.arc?.progR}".format(z2)
        }
        outFile("f4-band.txt").writeText(log.toString())
        println(log)
        assertEquals(emptyList(), bad.take(10), "${bad.size} 건 — build/verify1114arcrecheck/f4-band.txt")
    }

    // ================================================================ R-VR1

    /**
     * 그림 끝(T1 = 오른쪽) 하나에서 호가 그 요소에 접하면 볼록인가(Verify1114ArcAutoSimTest.convexIfTangentAt 과 같은 유도를 따로 씀):
     * 요소가 수평이면 중심 = P + (0, ±R)(다른 점 쪽), 단면이면 P + (±R, 0). 그 중심이 현의 소재 쪽(외경 X− · 내경 X+)이면 볼록
     */
    private fun convexIfTangentAt(inner: Boolean, type: Int, t1: Boolean): Boolean {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        val a = Pt(0.0, 0.0)
        val b = Pt(-1.0, up)
        val p = if (t1) a else b
        val q = if (t1) b else a
        val vertical = if (t1) type <= 2 else type == 2 || type == 4
        val c = if (vertical) Pt(p.z + (if (q.z > p.z) 1.0 else -1.0), p.x) else Pt(p.z, p.x + (if (q.x > p.x) 1.0 else -1.0))
        var m = left(b - a)
        if ((m.x < 0) == inner) m = m * -1.0
        return ((c - (a + b) * 0.5) dot m) > 0
    }

    /**
     * VR1 재현 셋(finding 의 숫자) + 뒤집기 · 거울, 사용자 화면의 tangentAt, 그리고 모든 형상 × 볼록 · 오목 × 뒤집기 × 거울 × 빈 칸 넷에서
     * 접한 점(두 끝 다 되면 두 좌표를 다 넣은 점 — 고침 결정)의 칸 번호 = tangentAt 이고 호 중심 = 그 점 + 그 요소 법선 × R
     */
    @Test
    fun vr1TwoEnds() {
        val bad = mutableListOf<String>()
        fun f(inner: Boolean, type: Int, convex: Boolean, x: List<Double?>, z: List<Double?>, rr: Double, flip: Boolean = false, reverse: Boolean = false): SlopeForm {
            fun mz(v: Double?) = if (reverse) v?.let { -30.0 - it } else v
            val xs = if (flip) x.reversed() else x
            val zs = (if (flip) z.reversed() else z).map(::mz)
            return SlopeForm(
                kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = type, reverse = reverse, noseR = 0.4, tip = null, x = xs, z = zs,
                angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = null, end = null, flip = flip, angleRef = AngleRef.X,
                arc = true, arcR = rr, arcConcave = !convex,
            )
        }
        // (형상, 볼록, x, z, R, 기대 칸 · 좌표 이름 · 값(거울 전), 기대 tangentAt(뒤집기 전))
        data class Rep(val inner: Boolean, val type: Int, val convex: Boolean, val x: List<Double?>, val z: List<Double?>, val rr: Double, val blankIdx: Int, val isZ: Boolean, val want: Double, val at: Int)
        val reps = listOf(
            Rep(true, 4, false, listOf(7.5, 6.3), listOf(-10.1, null), 3.75, 1, true, -12.135, 1),
            Rep(false, 1, true, listOf(null, 50.0), listOf(0.0, -8.0), 10.0, 0, false, 42.0, 2),
            Rep(false, 4, false, listOf(30.0, null), listOf(-3.0, -6.0), 5.0, 1, false, 32.0, 1),
        )
        for (rp in reps) for (flip in listOf(false, true)) for (reverse in listOf(false, true)) {
            val form = f(rp.inner, rp.type, rp.convex, rp.x, rp.z, rp.rr, flip, reverse)
            val res = TaperSolve.solve(form)
            val s = res.solution
            if (s == null) {
                bad += "재현 멈춤 ${res.warnings} ${desc(form)}"
                continue
            }
            val idx = if (flip) 1 - rp.blankIdx else rp.blankIdx
            val p = s.points[idx].vertex
            val got = if (rp.isZ) p.z else p.xDia
            val want = if (rp.isZ && reverse) -30.0 - rp.want else rp.want
            if (Fmt.fixed3(got) != Fmt.fixed3(want)) bad += "재현 값 ${Fmt.fixed3(got)} ≠ ${Fmt.fixed3(want)} — ${desc(form)}"
            val at = if (flip) 3 - rp.at else rp.at
            if (s.arc?.tangentAt != at) bad += "재현 tangentAt ${s.arc?.tangentAt} ≠ $at — ${desc(form)}"
        }
        // 사용자 화면: 접점 Ø7.5 = 칸 T2
        for (reverse in listOf(false, true)) {
            val s = TaperSolve.solve(userForm(0.4, 1.0, reverse, null, null)).solution
            if (s?.arc?.tangentAt != 2) bad += "사용자 화면 tangentAt ${s?.arc?.tangentAt} (거울 $reverse)"
        }
        // 네 좌표면 null
        val four = TaperSolve.solve(f(true, 4, false, listOf(7.5, 6.3), listOf(-10.1, -12.135), 3.75)).solution
        if (four?.arc?.tangentAt != null) bad += "네 좌표인데 tangentAt ${four?.arc?.tangentAt}"
        // 격자
        var checked = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (convex in listOf(true, false)) for (flip in listOf(false, true)) for (reverse in listOf(false, true))
            for (blank in 0..3) for ((dz, dr) in listOf(5.0 to 5.0, 8.0 to 3.0, 3.0 to 8.0)) {
                val blankIsZ = blank % 2 == 1
                val given = if (blankIsZ) dr else dz
                val rr = given * 1.4
                val form = blanked(form4(inner, type, reverse, flip, convex, 0.4, null, dz, dr, rr, 0.0, null, null, 1.0, 30.0), blank)
                val ok = listOf(true, false).filter { convexIfTangentAt(inner, type, it) == convex }
                val blankAtFigT1 = (blank / 2 == 0) != flip
                val res = TaperSolve.solve(form)
                val s = res.solution
                if (ok.isEmpty()) {
                    if (res.warnings != listOf(TaperWarning.ARC_NO_TANGENT)) bad += "접할 끝 없음인데 ${res.warnings} — ${desc(form)}"
                    continue
                }
                val endT1 = if (ok.size == 1) ok.single() else !blankAtFigT1
                if (s == null) {
                    if (res.warnings.any { it == TaperWarning.ARC_NO_TANGENT || it == TaperWarning.ARC_R_TOO_SMALL || it == TaperWarning.MISSING_INPUT }) bad += "격자 멈춤 ${res.warnings} — ${desc(form)}"
                    continue
                }
                checked++
                // 칸 번호(뒤집으면 그림 T1 이 칸 T2)
                val field = if (endT1 != flip) 0 else 1
                if (s.arc?.tangentAt != field + 1) bad += "tangentAt ${s.arc?.tangentAt} ≠ ${field + 1} (두 끝 ${ok.size == 2}) — ${desc(form)}"
                val pv = s.points[field].vertex
                val qv = s.points[1 - field].vertex
                val vertical = if (endT1) type <= 2 else type == 2 || type == 4
                val cWant = if (vertical) Pt(pv.z + (if (qv.z > pv.z) rr else -rr), pv.x) else Pt(pv.z, pv.x + (if (qv.x > pv.x) rr else -rr))
                val cerr = (s.arc!!.center - cWant).length
                if (cerr > 1e-7 * rr) bad += "중심 ${xz(s.arc!!.center)} ≠ 접하는 자리 ${xz(cWant)} — ${desc(form)}"
                // 두 끝 다 되는 형상은 접한 점이 두 좌표를 다 넣은 칸
                if (ok.size == 2 && form.x[field] == null || ok.size == 2 && form.z[field] == null) bad += "두 끝 형상인데 빈 좌표 칸에서 접함 — ${desc(form)}"
            }
        println("VR1 격자 풀림 확인 $checked")
        outFile("vr1.txt").writeText("격자 풀림 확인 $checked\n" + bad.joinToString("\n"))
        assertEquals(emptyList(), bad.take(20))
    }

    // ================================================================ R-VR3

    /**
     * VR3 재현 그대로: 내경 형상 3 오목 X1 7.501 · Z1 −10.1 · X2 6.3 · Z2 빈칸 · R0.6 → 풀림 Z2 −10.700, 네 좌표 Z2 −10.7 과 같은 글 · 경고.
     * X1 7.5002 도. 띠 둘레(넘은 몫 0 · 1e-9 · 1e-6 · 1e-4 · 5e-4 · 1e-3 · 띠의 0.5 · 0.99 · 1.01 배): 빈칸 = 네 좌표(Z2 = Z1 − R) 의 경고 · 글, 띠 넘으면 ARC_R_TOO_SMALL
     */
    @Test
    fun vr3Band() {
        val bad = mutableListOf<String>()
        fun f(x1: Double, z2: Double?, rr: Double = 0.6) = SlopeForm(
            kind = TaperKind.INNER, type = 3, reverse = false, noseR = 0.2, tip = null, x = listOf(x1, 6.3), z = listOf(-10.1, z2),
            angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = null, end = null, flip = false, angleRef = AngleRef.X,
            arc = true, arcR = rr, arcConcave = true,
        )
        for (x1 in listOf(7.501, 7.5002)) {
            val res = TaperSolve.solve(f(x1, null))
            val s = res.solution
            if (s == null) {
                bad += "X1 $x1 멈춤 ${res.warnings}"
                continue
            }
            if (Fmt.fixed3(s.points[1].vertex.z) != "-10.700") bad += "X1 $x1 Z2 ${Fmt.fixed3(s.points[1].vertex.z)}"
            val four = TaperSolve.solve(f(x1, -10.7))
            if (four.warnings != res.warnings || four.solution?.ncG40 != s.ncG40 || four.solution?.ncG41G42 != s.ncG41G42) bad += "X1 $x1 빈칸 ≠ 네 좌표: ${res.warnings} / ${four.warnings}"
            if (TaperSolve.solve(f(x1, -10.701)).solution == null) bad += "X1 $x1 네 좌표 Z2 −10.701 멈춤"
        }
        for (rr in listOf(0.6, 3.75, 10.0)) {
            val band = sqrt(8 * 5e-6 * rr)
            for (over in listOf(0.0, 1e-9, 1e-6, 1e-4, 5e-4, 1e-3, band * 0.5, band * 0.99, band * 1.01, band * 3)) {
                val x1 = 6.3 + 2 * (rr + over)
                val blank = TaperSolve.solve(f(x1, null, rr))
                val four = TaperSolve.solve(f(x1, -10.1 - rr, rr))
                if (over > band) {
                    if (blank.warnings != listOf(TaperWarning.ARC_R_TOO_SMALL)) bad += "R$rr 넘은 몫 $over(띠 $band 밖): ${blank.warnings}"
                } else if (blank.warnings != four.warnings || blank.solution?.ncG40 != four.solution?.ncG40) {
                    bad += "R$rr 넘은 몫 $over: 빈칸 ${blank.warnings} ≠ 네 좌표 ${four.warnings}"
                }
                if (blank.solution == null) {
                    val text = taperWarningText(blank.warnings.first(), f(x1, null, rr))
                    if (blank.warnings.first() == TaperWarning.ARC_R_TOO_SMALL && "반경 차" !in text) bad += "R 과소 글에 반경 차 없음: $text"
                }
            }
        }
        assertEquals(emptyList(), bad)
    }

    // ================================================================ R-VR6

    /** VR6 재현 그대로: 범위 밖 오타(X2 30000)는 BAD_INPUT + 그 칸, 형상 1 오목도. 푼 지름 ≤ 0 은 BAD_INPUT + 빈 좌표 설명. Z 범위 밖도 그 칸 */
    @Test
    fun vr6Range() {
        val bad = mutableListOf<String>()
        fun f(inner: Boolean, type: Int, convex: Boolean, x: List<Double?>, z: List<Double?>, rr: Double) = SlopeForm(
            kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = type, reverse = false, noseR = 0.4, tip = null, x = x, z = z,
            angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = null, end = null, flip = false, angleRef = AngleRef.X,
            arc = true, arcR = rr, arcConcave = !convex,
        )
        val cases = listOf(
            Triple(f(false, 3, true, listOf(40.0, 30000.0), listOf(0.0, null), 10.0), setOf("x2"), false),
            Triple(f(false, 1, false, listOf(40.0, 30000.0), listOf(0.0, null), 10.0), setOf("x2"), false),
            Triple(f(false, 3, true, listOf(40.0, 50.0), listOf(-20000.0, null), 10.0), setOf("z1"), false),
            Triple(f(true, 2, true, listOf(6.0, null), listOf(0.0, -3.4), 3.5), emptySet(), true),
        )
        for ((form, fields, explain) in cases) {
            val res = TaperSolve.solve(form)
            if (res.solution != null || res.warnings != listOf(TaperWarning.BAD_INPUT)) bad += "${desc(form)} → ${res.warnings}"
            val got = taperWarningFields(TaperWarning.BAD_INPUT, form)
            if (got != fields) bad += "짚는 칸 $got ≠ $fields — ${desc(form)}"
            val text = taperWarningText(TaperWarning.BAD_INPUT, form)
            if (explain && "빈 좌표" !in text) bad += "푼 지름 설명 없음: $text"
        }
        // 직선(각도로 채움)은 그대로: X2 30000 → BAD_INPUT
        val line = f(false, 3, true, listOf(40.0, 30000.0), listOf(0.0, null), 10.0).copy(arc = false, arcR = null, angle = 30.0)
        if (TaperSolve.solve(line).warnings != listOf(TaperWarning.BAD_INPUT)) bad += "직선 X2 30000: ${TaperSolve.solve(line).warnings}"
        assertEquals(emptyList(), bad)
    }
}
