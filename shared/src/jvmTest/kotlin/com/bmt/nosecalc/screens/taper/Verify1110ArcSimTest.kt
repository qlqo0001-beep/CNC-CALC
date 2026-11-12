package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(피드백 2026-11-10 의 7 — 테이퍼 T1–T2 원호). 구현자의 시험 · 계산기 안쪽 함수를 쓰지 않고, 화면이 부르는 길(`SlopeForm` → `TaperSolve.solve`)의
 * 결과를 넣은 값에서 따로 세운 기대 형상과 견준다.
 * A. 도면: T1–T2 호가 두 점을 지나고 반지름 R · 중심이 소재 쪽(볼록) / 공구 쪽(오목), 모서리 R 이 양쪽에 접함, 앞뒤 요소가 넣은 시작 · 끝 좌표
 * B. 직접 보정 글(G40)을 시뮬레이터로 돌린 노즈 중심이 도면에서 r(파고듦 · 덜 깎임 1e-3), 합성 R · G02/G03
 * C. 기계 보정 글(G41/G42 회사 흐름)을 시뮬레이터(형식 C · A/B)로 돌려 멈춤 0 · 파고듦 0 · 덜 깎임 0 · 보정 쪽 · 막힌 끝/시작 너머로 안 감 · 회사 표기
 * 결과 요약은 build/verify1110arc/sim-report.txt 에 쓴다
 */
class Verify1110ArcSimTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** NC 0.001 반올림까지 받는 허용(mm) */
    private val tol = 1e-3

    // ================================================================ 기하(제품 코드의 internal 도우미를 쓰지 않는다)

    private fun unit(v: Pt) = v * (1 / v.length)
    private fun left(v: Pt) = Pt(-v.x, v.z)
    private fun ang(v: Pt) = atan2(v.x, v.z)
    private fun wrap(a: Double): Double {
        var x = a % (2 * PI)
        if (x < 0) x += 2 * PI
        return x
    }

    /** 호가 진행 방향으로 돈 각(rad) */
    private fun sweep(a: Arc): Double {
        if ((a.end - a.start).length < 1e-12) return 0.0
        val d = ang(a.end - a.center) - ang(a.start - a.center)
        return wrap(if (a.cw) -d else d)
    }

    /** 점 p 의 호 위 각 위치(시작에서 진행 방향으로, rad) */
    private fun param(a: Arc, p: Pt): Double {
        val d = ang(p - a.center) - ang(a.start - a.center)
        val t = wrap(if (a.cw) -d else d)
        return if (t > 2 * PI - 1e-7) 0.0 else t
    }

    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 < 1e-24) (p - s.start).length else (p - (s.start + d * (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0))).length
        }
        is Arc -> if (param(s, p) <= sweep(s) + 1e-12) abs((p - s.center).length - s.radius) else min((p - s.start).length, (p - s.end).length)
    }

    private fun length(s: Seg) = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> sweep(s) * s.radius
    }

    private fun pointAt(s: Seg, t: Double): Pt = when (s) {
        is Line -> s.start + (s.end - s.start) * t
        is Arc -> {
            val a = ang(s.start - s.center) + (if (s.cw) -1 else 1) * sweep(s) * t
            s.center + Pt(cos(a), sin(a)) * s.radius
        }
    }

    /** 요소 끝(end = true) 또는 처음의 진행 방향 */
    private fun tanAt(s: Seg, end: Boolean): Pt = when (s) {
        is Line -> unit(s.end - s.start)
        is Arc -> unit((if (end) s.end else s.start) - s.center).let { if (s.cw) left(it) * -1.0 else left(it) }
    }

    private class Sm(val p: Pt, val seg: Int, val s: Double, val len: Double)

    private fun sample(s: Seg, k: Int, step: Double): List<Sm> {
        val len = length(s)
        val n = max(1, ceil(len / step).toInt())
        return (0..n).map { Sm(pointAt(s, it.toDouble() / n), k, len * it / n, len) }
    }

    /** 팁 번호 → 노즈 중심에서 가상 인선으로 가는 벡터(FANUC 표) */
    private fun tipVec(tip: Int, r: Double): Pt = when (tip) {
        1 -> Pt(r, r)
        2 -> Pt(-r, r)
        3 -> Pt(-r, -r)
        4 -> Pt(r, -r)
        5 -> Pt(r, 0.0)
        6 -> Pt(0.0, r)
        7 -> Pt(-r, 0.0)
        8 -> Pt(0.0, -r)
        else -> Pt(0.0, 0.0)
    }

    // ================================================================ 기대 형상(넣은 값에서 따로)

    private class Exp(val a: Pt, val b: Pt, val c: Pt, val cw: Boolean, val side: String, val full: Arc)

    /** T1(a) → T2(b) 호: 소재 쪽은 외경이면 축 쪽(X−), 내경이면 X+. 볼록 = 중심이 소재 쪽. 화면 길은 늘 T1 부터 깎는다 */
    private fun expect(f: SlopeForm): Exp {
        val a = Pt(f.z[0]!!, f.x[0]!! / 2)
        val b = Pt(f.z[1]!!, f.x[1]!! / 2)
        val d = b - a
        val rr = f.arcR!!
        val h = sqrt(max(0.0, rr * rr - (d dot d) / 4))
        var m = unit(left(d))
        if ((m.x < 0) == f.kind.inner) m = m * -1.0
        val c = (a + b) * 0.5 + m * (if (f.arcConcave) -h else h)
        val ccw = ((a - c) cross (b - c)) > 0
        // 공구 쪽 = 소재 반대(−m). 진행 a → b 의 왼쪽이 공구 쪽이면 G41
        val side = if ((left(d) dot m) < 0) "G41" else "G42"
        return Exp(a, b, c, !ccw, side, Arc(a, b, c, !ccw))
    }

    private fun smooth(p: Seg, q: Seg): Boolean {
        val t1 = tanAt(p, true)
        val t2 = tanAt(q, false)
        return abs(t1 cross t2) < 1e-6 && (t1 dot t2) > 0
    }

    /** 도면(해의 drawing)이 넣은 값의 형상인가. 문제면 "갈래 설명" */
    private fun drawingError(f: SlopeForm, s: TaperSolution, e: Exp): String? {
        val d = s.drawing.segs
        val n = d.size
        val sa = s.arc ?: return "NOARC solution.arc 가 null"
        val k = sa.index
        val a = d.getOrNull(k) as? Arc ?: return "NOARC drawing[$k] 이 원호가 아님"
        val rr = f.arcR!!
        if (abs(a.radius - rr) > 1e-9) return "RADIUS ${a.radius} ≠ $rr"
        if ((a.center - e.c).length > 1e-6) return "CENTER ${a.center} ≠ 기대 ${e.c}"
        if (a.cw != e.cw) return "CW 도면 호 방향 cw=${a.cw} ≠ 기대 ${e.cw}"
        if (sa.cw != e.cw || abs(sa.r - rr) > 1e-9 || (sa.center - e.c).length > 1e-6) return "SLOPEARC $sa"
        val want = if (f.arcConcave) rr - f.noseR else rr + f.noseR
        val pr = sa.progR
        if (pr == null || abs(pr - want) > 1e-9) return "PROGR 합성 R $pr ≠ $want"
        if (k !in 1..2 || (n - 1 - k) !in 1..2) return "SHAPE 요소 $n 개 · 호 $k 번"
        val sw = sweep(e.full)
        val p0 = param(e.full, a.start)
        val p1 = param(e.full, a.end)
        if (!(p0 <= p1 + 1e-9 && p1 <= sw + 1e-7)) return "SPAN 줄인 호가 T1–T2 호 밖($p0 · $p1 / $sw)"
        val first = d.first() as? Line ?: return "LEG 첫 요소가 직선이 아님"
        val last = d.last() as? Line ?: return "LEG 끝 요소가 직선이 아님"
        val (fv, bv) = legsVertical(f.kind, f.type, f.flip)
        fun leg(l: Line, vertical: Boolean, at: Pt, value: Double?, outer: Pt, name: String): String? {
            if (vertical && (abs(l.start.z - at.z) > 1e-9 || abs(l.end.z - at.z) > 1e-9)) return "LEG $name 단면이 T 점의 Z 에 없음 $l"
            if (!vertical && (abs(l.start.x - at.x) > 1e-9 || abs(l.end.x - at.x) > 1e-9)) return "LEG $name 수평이 T 점의 X 에 없음 $l"
            if (value != null && abs((if (vertical) outer.xDia else outer.z) - value) > 1e-9) return "LEG $name 바깥 끝 $outer ≠ 넣은 $value"
            return null
        }
        leg(first, fv, e.a, f.start, first.start, "시작")?.let { return it }
        leg(last, bv, e.b, f.end, last.end, "끝")?.let { return it }
        val cr1 = f.corners[0]
        val cr2 = f.corners[1]
        // 모서리 R 이 빠져도 되는 이음: 접해 이어지거나, 받는 띠(가장 작은 R 바로 아래)에서 호가 아주 조금 넘어 거꾸로 꺾여 끼웠을 필렛이 꼭짓점에서
        // ρ(1/cos(θ/2) − 1) ≤ 1e-5 mm 안인 것(고침 단계 V7-3 · V7-5 — 그 끝의 모서리 R 은 끼우지 않는다). 전에는 꺾임 1e-3 rad 만 봤다
        fun missing(cr: Double, t1: Pt, t2: Pt): Boolean {
            val th = atan2(abs(t1 cross t2), t1 dot t2)
            return cr > 0 && abs(t1 cross t2) > 1e-3 && cr * (1 / cos(th / 2) - 1) > 1e-5
        }
        if (k == 1) {
            if ((a.start - e.a).length > 1e-9) return "T1 호 시작 ${a.start} ≠ T1 ${e.a}"
            if (missing(cr1, tanAt(first, true), tanAt(a, false))) return "FILLET1 모서리 R $cr1 이 빠짐"
        } else {
            val fl = d[1] as? Arc ?: return "FILLET1 1번이 원호가 아님"
            if (cr1 <= 0) return "FILLET1 넣지 않은 모서리 R"
            if (abs(fl.radius - cr1) > 1e-9) return "FILLET1 R ${fl.radius} ≠ $cr1"
            if (!smooth(first, fl) || !smooth(fl, a)) return "FILLET1 접하지 않음"
            if (((e.a - first.end) dot tanAt(first, true)) < -1e-9 || sweep(fl) >= PI) return "FILLET1 자리가 틀림"
        }
        if (n - 1 - k == 1) {
            if ((a.end - e.b).length > 1e-9) return "T2 호 끝 ${a.end} ≠ T2 ${e.b}"
            if (missing(cr2, tanAt(a, true), tanAt(last, false))) return "FILLET2 모서리 R $cr2 이 빠짐"
        } else {
            val fl = d[n - 2] as? Arc ?: return "FILLET2 원호가 아님"
            if (cr2 <= 0) return "FILLET2 넣지 않은 모서리 R"
            if (abs(fl.radius - cr2) > 1e-9) return "FILLET2 R ${fl.radius} ≠ $cr2"
            if (!smooth(a, fl) || !smooth(fl, last)) return "FILLET2 접하지 않음"
            if (((last.start - e.b) dot tanAt(last, false)) < -1e-9 || sweep(fl) >= PI) return "FILLET2 자리가 틀림"
        }
        return null
    }

    /** 이음이 접하지 않고 오목(공구 쪽으로 꺾임)인가 — 그 꼭짓점 둘레는 노즈가 못 닿아 r 이 남는다 */
    private fun concaveSharp(p: Seg, q: Seg, side: String): Boolean {
        val t1 = tanAt(p, true)
        val t2 = tanAt(q, false)
        if (abs(t1 cross t2) < 1e-3) return false
        val toolN = if (side == "G41") left(t1) else left(t1) * -1.0
        return (t2 dot toolN) > 0
    }

    /** 꼭 깎여야 하는 도면 점: 앞뒤 요소를 뺀 가운데 요소(모서리 R · T1–T2 호). 날카로운 오목 꼭짓점에서 1.1r + 0.02 안은 뺀다 */
    private fun mustCut(d: List<Seg>, side: String, r: Double, step: Double): List<Pt> {
        val out = mutableListOf<Pt>()
        for (i in 1 until d.size - 1) {
            val len = length(d[i])
            val m0 = if (concaveSharp(d[i - 1], d[i], side)) 1.1 * r + 0.02 else 0.0
            val m1 = if (concaveSharp(d[i], d[i + 1], side)) 1.1 * r + 0.02 else 0.0
            for (q in sample(d[i], i, step)) if (q.s >= m0 && q.s <= len - m1) out += q.p
        }
        return out
    }

    // ================================================================ 시뮬레이터

    /** [feed] = 직접 보정 글은 이송 F 가 없어(화면 글 그대로) 머리 줄에 F 를 넣어 돌린다 */
    private fun sim(nc: List<String>, r: Double, tip: Int, form: CompStartup, feed: Boolean = false): SimRun {
        val head = if (feed) pre.dropLast(1) + "G99F.1" else pre
        return GSim.run(SimInput((head + nc + "M30").joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, tip))))
    }

    private class Box(m: MoveStep, r: Double) {
        val z0: Double
        val z1: Double
        val x0: Double
        val x1: Double

        init {
            val s = m.nose
            if (s is Arc) {
                val rr = s.radius
                z0 = s.center.z - rr - r - 0.01
                z1 = s.center.z + rr + r + 0.01
                x0 = s.center.x - rr - r - 0.01
                x1 = s.center.x + rr + r + 0.01
            } else {
                z0 = min(s.start.z, s.end.z) - r - 0.01
                z1 = max(s.start.z, s.end.z) + r + 0.01
                x0 = min(s.start.x, s.end.x) - r - 0.01
                x1 = max(s.start.x, s.end.x) + r + 0.01
            }
        }

        fun has(p: Pt) = p.z in z0..z1 && p.x in x0..x1
    }

    /** (가장 깊은 파고듦, 그 이동) · 가장 큰 덜 깎임 */
    private class Probe(val gouge: Double, val gougeBy: MoveStep?, val under: Double, val underAt: Pt?, val gougeAt: Pt? = null)

    private fun probe(moves: List<MoveStep>, cut: List<MoveStep>, surf: List<Sm>, must: List<Pt>, r: Double): Probe {
        val boxes = moves.map { Box(it, r) }
        var g = Double.NEGATIVE_INFINITY
        var by: MoveStep? = null
        var gAt: Pt? = null
        for (q in surf) for (i in moves.indices) {
            if (!boxes[i].has(q.p)) continue
            val dd = r - dist(q.p, moves[i].nose)
            if (dd > g) {
                g = dd
                by = moves[i]
                gAt = q.p
            }
        }
        var u = Double.NEGATIVE_INFINITY
        var at: Pt? = null
        for (q in must) {
            val dd = (cut.minOfOrNull { dist(q, it.nose) } ?: Double.POSITIVE_INFINITY) - r
            if (dd > u) {
                u = dd
                at = q
            }
        }
        return Probe(g, by, u, at, gAt)
    }

    private val numOk = Regex("""-?(\d+\.|[1-9]\d*\.\d{0,2}[1-9]|\.\d{0,2}[1-9])""")

    /** 회사 표기가 아닌 낱말(없으면 null): 좌표 · R · F 는 소수점, 뒤 0 없음, 앞 0 없음, −0 없음 */
    private fun badWord(line: String): String? {
        if (line.startsWith("(")) return null
        if (line.contains(' ')) return "빈칸"
        for (w in ncWords(line)) {
            val v = w.drop(1)
            when (w[0]) {
                'X', 'Z', 'R', 'F' -> if (!numOk.matches(v) || v == "-0.") return w
                'G', 'M', 'T' -> if (v.toIntOrNull() == null) return w
                else -> return w
            }
        }
        return null
    }

    private fun xz(p: Pt) = "X%.4f Z%.4f".format(p.xDia, p.z)

    private class Tally {
        val count = sortedMapOf<String, Int>()
        val first = linkedMapOf<String, MutableList<String>>()
        val stat = sortedMapOf<String, Int>()
        var gougeM = Double.NEGATIVE_INFINITY
        var underM = Double.NEGATIVE_INFINITY
        var gougeD = Double.NEGATIVE_INFINITY
        var underD = Double.NEGATIVE_INFINITY
        var runs = 0

        fun fail(kind: String, text: () -> String) {
            count[kind] = (count[kind] ?: 0) + 1
            val l = first.getOrPut(kind) { mutableListOf() }
            if (l.size < 4) l += text()
        }

        fun note(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }

        fun report(title: String) = buildString {
            appendLine("==== $title")
            appendLine("시뮬 실행 $runs · 통과한 것의 최대 — 기계 보정 파고듦 $gougeM 덜 깎임 $underM / 직접 보정 파고듦 $gougeD 덜 깎임 $underD")
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

    private fun desc(f: SlopeForm) =
        "${f.kind.label} 형상 ${f.type}${if (f.reverse) " 역방향" else ""}${if (f.flip) " 뒤집기" else ""} · T1 X${f.x[0]} Z${f.z[0]} · T2 X${f.x[1]} Z${f.z[1]} · " +
            "R${f.arcR} ${if (f.arcConcave) "오목" else "볼록"} · 노즈R ${f.noseR} · 팁 ${f.tip} · 모서리 R ${f.corners} · 시작 ${f.start} · 끝 ${f.end} · Z 여유 ${f.zClear}"

    /** 넣은 앞뒤 요소가 0.5 mm 보다 짧은가(격자의 0.3 mm) */
    private fun shortLeg(f: SlopeForm): Boolean {
        val (fv, bv) = legsVertical(f.kind, f.type, f.flip)
        fun len(v: Double?, vertical: Boolean, i: Int) = v?.let { if (vertical) abs(it - f.x[i]!!) / 2 else abs(it - f.z[i]!!) } ?: 9.0
        return len(f.start, fv, 0) < 0.5 || len(f.end, bv, 1) < 0.5
    }

    /** 사례 하나: 풀고 도면 · 두 글을 본다 */
    private fun checkCase(f: SlopeForm, t: Tally, forms: List<CompStartup>, big: Boolean, allow: Set<TaperWarning> = emptySet()) {
        val res = try {
            TaperSolve.solve(f)
        } catch (e: Throwable) {
            t.fail("SOLVE_THROW") { "${desc(f)}\n$e" }
            return
        }
        val s = res.solution
        if (s == null) {
            val w = res.warnings.joinToString("+")
            t.note("멈춤:$w")
            val chord = hypot(f.z[1]!! - f.z[0]!!, (f.x[1]!! - f.x[0]!!) / 2)
            t.note(
                "멈춤 분포:$w · 현 ${if (chord < 1) "0.72" else "5 이상"} · 노즈R ${f.noseR} · ${if (f.arcConcave) "오목" else "볼록"} · R ${if (f.arcR!! < 2 * chord) "작음" else "큼"} · " +
                    "모서리 R ${if (f.corners.any { it > 0 }) "있음" else "없음"} · 짧은 앞뒤 ${if (shortLeg(f)) "있음" else "없음"}",
            )
            if (res.warnings.size != 1) t.fail("STOP_WARNINGS") { "${desc(f)}\n${res.warnings}" }
            val never = setOf(
                TaperWarning.MISSING_INPUT, TaperWarning.BAD_INPUT, TaperWarning.SHAPE_MISMATCH, TaperWarning.BAD_ANGLE, TaperWarning.CONTRADICTION,
                TaperWarning.ARC_R_TOO_SMALL,
            )
            if (res.warnings.any { it in never && it !in allow }) t.fail("STOP_UNEXPECTED_$w") { desc(f) }
            // 넉넉한 형상(현 5 mm 이상 · 노즈R 0.4 이하 · 짧은 앞뒤 요소 없음 · 모서리 R 없음)이 멈추면 따로 적어 본다
            if (big) t.fail("STOP_ROOMY_$w") { desc(f) }
            return
        }
        t.note("풀림")
        val e = expect(f)
        run {
            drawingError(f, s, e)?.let { msg ->
                t.fail("DRAW_" + msg.substringBefore(' ')) { "${desc(f)}\n$msg\n${s.drawing.segs.joinToString("\n")}" }
                return
            }
        }
        if (f.tip == null) {
            val std = if (f.kind.inner) (if (f.reverse) 1 else 2) else (if (f.reverse) 4 else 3)
            if (s.tip != std) t.fail("TIP") { "${desc(f)}\n표준 팁 ${s.tip} ≠ $std" }
        }
        val w = res.warnings.toSet()
        res.warnings.forEach { t.note("경고:$it") }
        val r = f.noseR
        val step = sqrt(2 * r * tol)
        val near = s.drawing.segs.flatMapIndexed { i, sg -> sample(sg, i, step) }
        // 내경은 축 건너편 벽(거울)도 파이는지 본다
        val surf = if (f.kind.inner) near + near.map { Sm(Pt(it.p.z, -it.p.x), it.seg, it.s, it.len) } else near
        val must = mustCut(s.drawing.segs, e.side, r, step)
        machine(f, s, e, w, surf, must, t, forms)
        direct(f, s, e, w, surf, must, t)
    }

    private fun machine(f: SlopeForm, s: TaperSolution, e: Exp, w: Set<TaperWarning>, surf: List<Sm>, must: List<Pt>, t: Tally, forms: List<CompStartup>) {
        val nc = s.ncG41G42
        val r = f.noseR
        val text = { "${desc(f)}\n경고 $w\n${nc.joinToString("\n")}" }
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = nc.indexOfFirst { it.contains("F.3") }
        val i40 = nc.indexOf("G40")
        val frame = nc.size >= 6 && nc[0] in listOf("G41", "G42") && nc[1].startsWith("G0X") && nc[1].contains("Z") && iCut in 2 until iExit &&
            nc.count { it.contains("G40") } == 1 && i40 == nc.size - 2 && i40 > iExit && nc.last() == "G0Z200."
        if (!frame) {
            t.fail("M_FRAME") { text() }
            return
        }
        nc.firstNotNullOfOrNull(::badWord)?.let { bad -> t.fail("M_FORMAT") { "낱말 $bad\n${text()}" } }
        if (nc[0] != e.side) t.fail("M_SIDE") { "기대 ${e.side}\n${text()}" }
        // G40 혼자 줄 바로 앞 이동은 직선이어야 한다(원호 뒤 축 없는 G40 금지)
        val lastG = nc.subList(0, i40).flatMap { ncWords(it) }.lastOrNull { it in listOf("G0", "G1", "G2", "G3") }
        if (lastG == "G2" || lastG == "G3") t.fail("M_G40_AFTER_ARC") { text() }
        val cutLines = (pre.size + iCut + 1)..(pre.size + iExit)
        val warnedStop = TaperWarning.CONCAVE_R_LE_NOSE in w || TaperWarning.NOSE_GT_SLOPE in w
        val warnedAir = TaperWarning.ENTRY_NOT_FOUND in w || TaperWarning.EXIT_NOT_FOUND in w
        val skipUnder = warnedStop || TaperWarning.SHORT_END_SKIPPED in w
        val rr = f.arcR!!
        for (form in forms) {
            t.runs++
            val run = sim(nc, r, s.tip, form)
            val stop = run.stop
            if (stop != null) {
                // 시뮬레이터는 바깥으로 90° 넘게 꺾이는 이음을 그리지 않는다(COMP_ACUTE_OUTER — 실제 제어기는 토막을 끼워 돈다). 도면에 꼭 90°(또는 그 위)로
                // 꺾이는 볼록 이음이 있을 때의 그 멈춤은 시뮬 한계로 따로 센다
                val simLimit = stop.code == IssueCode.COMP_ACUTE_OUTER && s.drawing.segs.zipWithNext().any { (p, q) ->
                    val t1 = tanAt(p, true)
                    val t2 = tanAt(q, false)
                    abs(t1 cross t2) > 1e-3 && !concaveSharp(p, q, e.side) && (t1 dot t2) < 2e-4
                }
                // 간섭 멈춤에 진입 · 빼는 길 못 찾음 경고(손으로 고치거나 직접 보정을 쓰라는 글)가 함께 나왔으면 앱이 알린 것으로 센다
                val warned = warnedStop || (stop.code == IssueCode.COMP_INTERFERENCE && warnedAir)
                // 바깥 꺾임 멈춤은 글의 꺾임을 직접 재서 되돌아감 · 바깥으로 돌 때 파고듦이 있으면 경고가 있어도 따로 센다
                val reversed = stop.code == IssueCode.COMP_ACUTE_OUTER &&
                    acuteJunctions(nc, e.side, s.drawing.segs, r).any { it.reversal || it.gouge > tol }
                t.fail(
                    "M_STOP_${stop.code}" + (if (reversed) "(되돌아감 · 파고듦)" else "") +
                        (if (warned) "(경고 있음)" else if (simLimit && !reversed) "(시뮬 한계 — 바깥 90° 꺾임)" else ""),
                ) {
                    "형식 $form · ${stop.line - pre.size}줄 '${nc.getOrNull(stop.line - pre.size - 1)}'\n${text()}"
                }
                return
            }
            val moves = run.steps.filterIsInstance<MoveStep>()
            val cut = moves.filter { it.line in cutLines && it.kind == MoveKind.FEED }
            val p = probe(moves, cut, surf, must, r)
            if (p.gouge > tol) {
                val m = p.gougeBy!!
                val pts = s.drawing.segs.flatMap { listOf(it.start, it.end) }
                val narrow = f.kind.inner && (pts.filter { it.x > 1e-6 }.minOfOrNull { it.x } ?: 0.0) < (1 + sqrt(2.0)) * r + 0.02
                val opposite = f.kind.inner && (p.gougeAt?.x ?: 0.0) < 0
                val kind = if (m.line in cutLines && !opposite) "M_GOUGE_CUT"
                else "M_GOUGE_AIR" + (if (opposite) "_OPPOSITE" else "") + (if (warnedAir) "(경고 있음)" else if (opposite && narrow) "(좁은 구멍)" else "")
                t.fail(kind) {
                    "형식 $form · ${m.line - pre.size}줄 '${nc.getOrNull(m.line - pre.size - 1)}' (${m.src} ${m.kind}) 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 이 도면을 ${p.gouge} 팜\n${text()}"
                }
                return
            }
            t.gougeM = max(t.gougeM, p.gouge)
            if (!skipUnder) {
                if (p.under > tol) {
                    t.fail("M_UNDER") { "형식 $form · 도면 ${p.underAt?.let(::xz)} 에 ${p.under} 남음\n${text()}" }
                    return
                }
                if (must.isNotEmpty()) t.underM = max(t.underM, p.under)
            }
            // T1–T2 호 명령: 중심 · R · 방향
            val arcs = moves.filter { it.line in cutLines }.mapNotNull { it.cmd as? Arc }
            val main = arcs.filter { abs(it.radius - rr) < 2e-3 && (it.center - e.c).length < 3e-3 * (1 + 2 * rr / max(1e-3, (it.end - it.start).length)) }
            if (main.isEmpty()) {
                t.fail("M_NOARC" + (if (TaperWarning.SHORT_END_SKIPPED in w || TaperWarning.SHORT_END_PAST in w) "(짧은 끝)" else "")) { "형식 $form · 기대 중심 ${xz(e.c)} R$rr\n${text()}" }
            } else if (main.any { it.cw != e.cw }) {
                t.fail("M_ARCDIR") { "형식 $form · 기대 ${if (e.cw) "G2" else "G3"}\n${text()}" }
            }
            // 막힌 시작 · 끝(넣은 좌표 너머가 척 쪽 Z− 이거나 소재 쪽 X): 노즈가 그 좌표의 법선을 넘지 않는다
            val d = s.drawing.segs
            val after = moves.filter { it.line > pre.size + 2 }.flatMap { m ->
                val sg = m.nose
                if (sg is Arc) (0..48).map { pointAt(sg, it / 48.0) } else listOf(sg.start, sg.end)
            }
            fun wall(name: String, given: Boolean, at: Pt, beyond: Pt) {
                if (!given) return
                val blocked = if (abs(beyond.z) > abs(beyond.x)) beyond.z < 0 else (beyond.x < 0) != f.kind.inner && !(!f.kind.inner && at.x <= 1e-9)
                if (!blocked) return
                val past = after.maxOf { (it - at) dot beyond } + r
                if (past > tol) {
                    t.fail("M_WALL_$name" + (if (TaperWarning.SHORT_END_PAST in w) "(경고 있음)" else "")) { "형식 $form · 노즈가 넣은 좌표 ${xz(at)} 너머로 $past\n${text()}" }
                }
            }
            wall("START", f.start != null, d.first().start, tanAt(d.first(), false) * -1.0)
            wall("END", f.end != null, d.last().end, tanAt(d.last(), true))
        }
    }

    private fun direct(f: SlopeForm, s: TaperSolution, e: Exp, w: Set<TaperWarning>, surf: List<Sm>, must: List<Pt>, t: Tally) {
        val nc = s.ncG40
        val r = f.noseR
        val text = { "${desc(f)}\n경고 $w\n${nc.joinToString("\n")}" }
        nc.firstNotNullOfOrNull(::badWord)?.let { bad -> t.fail("D_FORMAT") { "낱말 $bad\n${text()}" } }
        t.runs++
        val run = sim(nc, r, s.tip, CompStartup.C, feed = true)
        val stop = run.stop
        if (stop != null) {
            t.fail("D_STOP_${stop.code}") { "${stop.line - pre.size}줄\n${text()}" }
            return
        }
        val moves = run.steps.filterIsInstance<MoveStep>()
        val tv = tipVec(s.tip, r)
        if (moves.any { (it.tip.end - it.nose.end - tv).length > 1e-9 }) {
            t.fail("D_TIPVEC") { text() }
            return
        }
        val cut = moves.filter { it.kind == MoveKind.FEED }
        // 글의 첫 이동 줄(진입 G0 — 뒤집기는 접근 줄의 첫 줄)은 시뮬레이터가 원점에서 오는 길로 그린다. 글이 정하는 길이 아니라 뺀다
        val firstMove = pre.size + nc.indexOfFirst { !it.startsWith("(") } + 1
        val p = probe(moves.filter { it.line > firstMove }, cut, surf, must, r)
        if (p.gouge > tol) {
            val m = p.gougeBy!!
            // 내경 직접 보정 끝의 축 너머 후퇴(마지막 G0 — 노즈가 맞은편 = 같은 회전면을 팜)는 T1–T2 직선에도 있는 기존 규칙이라(V7-2, 고침 단계에서 미룸) 따로 센다
            val pastAxis = f.kind.inner && m.kind != MoveKind.FEED && m.line == pre.size + nc.size && m.nose.end.x < 0
            t.fail(if (m.kind == MoveKind.FEED) "D_GOUGE_FEED" else "D_GOUGE_RAPID" + (if (pastAxis) "(축 너머 후퇴 — 기존 · 미룸)" else "")) {
                "${m.line - pre.size}줄 '${nc.getOrNull(m.line - pre.size - 1)}' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 이 도면을 ${p.gouge} 팜\n${text()}"
            }
            return
        }
        t.gougeD = max(t.gougeD, p.gouge)
        if (!(TaperWarning.CONCAVE_R_LE_NOSE in w || TaperWarning.NOSE_GT_SLOPE in w)) {
            if (p.under > tol) {
                t.fail("D_UNDER") { "도면 ${p.underAt?.let(::xz)} 에 ${p.under} 남음\n${text()}" }
                return
            }
            if (must.isNotEmpty()) t.underD = max(t.underD, p.under)
        }
        val rr = f.arcR!!
        val want = if (f.arcConcave) rr - r else rr + r
        val arcs = moves.mapNotNull { it.cmd as? Arc }
        val main = arcs.filter { abs(it.radius - want) < 2e-3 && (it.center - (e.c + tv)).length < 3e-3 * (1 + 2 * want / max(1e-3, (it.end - it.start).length)) }
        // 모서리 R 이 호를 거의 다 먹어 남은 호가 NC 자릿수 아래면(0.01 mm 미만) 글에 호 줄이 없어도 된다(파고듦 · 덜 깎임은 위에서 봤다)
        val left = s.arc?.let { length(s.drawing.segs[it.index]) } ?: 0.0
        // 짧은(2r 미만) 오목 호는 이웃 오목 꼭짓점의 교점에 노즈 길이 다 잘릴 수 있다(남은 노즈 길이 NC 자릿수 아래 — 글에 호 줄이 없다). 모양은 위 덜 깎임 검사가 본다
        // (고침 단계 V7-6: 외경 2 역 · R0.864 오목 · 모서리 R 2.0 — 남은 호 0.191 의 노즈 길 0.0003, 덜 깎임 0.0235 는 T2 오목 꼭짓점에 늘 남는 살)
        val trimmed = f.arcConcave && left < 2 * r
        if (main.isEmpty() && left >= 0.01 && !trimmed) t.fail("D_NOARC") { "기대 중심 ${xz(e.c + tv)} R$want\n${text()}" }
        else if (main.any { it.cw != e.cw }) t.fail("D_ARCDIR") { "기대 ${if (e.cw) "G2" else "G3"}\n${text()}" }
    }

    // ================================================================ 격자

    /**
     * 풀이 그림(거울 전) 좌표로 형상을 세워 화면 칸으로 옮긴다: 오른쪽 점 · 왼쪽 점, 그 모서리 R · 앞뒤 요소 길이(null = 비움). arcK = 가장 작은 R 의 몇 배
     * (0.001 로 올림 — 1 이면 화면이 '최소 R' 로 알려 주는 값)
     */
    private fun form(
        inner: Boolean, type: Int, reverse: Boolean, flip: Boolean, convex: Boolean, noseR: Double, tip: Int?,
        dz: Double, dx: Double, arcK: Double, crR: Double, crL: Double, legR: Double?, legL: Double?, zClear: Double = 1.0, x0: Double = 30.0,
    ): SlopeForm {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        val zR = if (reverse) dz + 2.0 else -2.0
        val zL = zR - dz
        val xR = if (up > 0) x0 else x0 + 2 * dx
        val xL = xR + up * 2 * dx
        val sg = if (reverse) -1.0 else 1.0
        val startR = legR?.let { if (type <= 2) max(0.0, xR - up * 2 * it) else sg * (zR + it) }
        val endL = legL?.let { if (type == 2 || type == 4) max(0.0, xL + up * 2 * it) else sg * (zL - it) }
        val th = atan2(dx, dz)
        val minR = hypot(dz, dx) / (2 * sin(min(th, PI / 2 - th)))
        val arcR = ceil(minR * arcK * 1000 - 1e-6) / 1000
        return SlopeForm(
            kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = type, reverse = reverse, noseR = noseR, tip = tip,
            x = if (flip) listOf(xL, xR) else listOf(xR, xL), z = if (flip) listOf(sg * zL, sg * zR) else listOf(sg * zR, sg * zL),
            angle = null, zClear = zClear, corners = if (flip) listOf(crL, crR) else listOf(crR, crL),
            start = if (flip) endL else startR, end = if (flip) startR else endL, flip = flip, angleRef = AngleRef.X,
            arc = true, arcR = arcR, arcConcave = !convex,
        )
    }

    private val geos = listOf(5.0 to 5.0, 8.0 to 3.0, 3.0 to 8.0, 0.6 to 0.4)
    private val legs = listOf(null, 3.0, 0.3)

    private fun finish(t: Tally, title: String, file: String) {
        val text = t.report(title)
        File("build/verify1110arc").mkdirs()
        File("build/verify1110arc/$file").writeText(text)
        println(text.lineSequence().take(60).joinToString("\n"))
        val hard = t.count.filterKeys { !it.contains("(") || it.contains("되돌아감") }
        assertTrue(hard.isEmpty(), "$title: 실패 갈래 $hard — build/verify1110arc/$file")
    }

    /** 외경 · 내경 × 형상 1~5 × 역방향 × 뒤집기 × 볼록 · 오목 × 노즈R × 모서리 R × 시작 · 끝 × 형상 치수 × R 배수, 표준 팁 */
    @Test
    fun grid() {
        val t = Tally()
        var n = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (convex in listOf(true, false)) for (noseR in listOf(0.2, 0.4, 0.8, 1.2)) for ((gi, geo) in geos.withIndex())
                for (k in listOf(1.0, 1.4, 6.0)) for (cr in 0..3) for (lr in legs) for (ll in legs) {
                    val c = if (gi == 3) 0.1 else 0.5
                    val f = form(inner, type, reverse, flip, convex, noseR, null, geo.first, geo.second, k, if (cr and 1 != 0) c else 0.0, if (cr and 2 != 0) c else 0.0, lr, ll)
                    n++
                    val big = gi < 3 && noseR <= 0.4 && lr != 0.3 && ll != 0.3 && cr == 0
                    checkCase(f, t, if (n % 4 == 0) listOf(CompStartup.C, CompStartup.AB) else listOf(CompStartup.C), big)
                }
        t.note("사례 수 = $n")
        finish(t, "격자(표준 팁)", "sim-report.txt")
    }

    /** 팁 1~9 를 손으로 고른 것 · Z 여유 0.3 / 3 — 격자를 듬성듬성 */
    @Test
    fun gridTips() {
        val t = Tally()
        var n = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (convex in listOf(true, false)) for (noseR in listOf(0.4, 0.8)) for ((gi, geo) in geos.withIndex()) for (k in listOf(1.0, 2.5))
                for (cr in listOf(0, 3)) for (lr in listOf(null, 3.0)) for (ll in listOf(null, 3.0)) {
                    n++
                    val tip = 1 + n % 9
                    val zc = if (n % 2 == 0) 0.3 else 3.0
                    val c = if (gi == 3) 0.1 else 0.5
                    val f = form(inner, type, reverse, flip, convex, noseR, tip, geo.first, geo.second, k, if (cr != 0) c else 0.0, if (cr != 0) c else 0.0, lr, ll, zc)
                    checkCase(f, t, listOf(CompStartup.C), big = false)
                }
        t.note("사례 수 = $n")
        finish(t, "격자(팁 1~9 · Z 여유)", "sim-report-tips.txt")
    }

    /**
     * 가장 작은 R 바로 아래(0.001 ~ 0.007 작음): 계산기가 반올림 허용으로 받는 띠(호가 T1 · T2 너머로 5e-6 mm 까지 부풂 — 끝 접선이 앞뒤 요소를 1e-3 rad 쯤 거꾸로 넘음).
     * 받았으면 같은 기준으로 맞아야 하고, 안 받았으면 R 과소로 멈춰야 한다
     */
    @Test
    fun gridJustBelowMin() {
        val t = Tally()
        var n = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (convex in listOf(true, false)) for (noseR in listOf(0.2, 0.4, 0.8)) for (geo in geos.take(3)) for (below in listOf(0.001, 0.002, 0.003, 0.005, 0.007))
                for (cr in listOf(0.0, 0.5)) for (lr in listOf(null, 3.0)) for (ll in listOf(null, 3.0)) {
                    val f0 = form(inner, type, reverse, flip, convex, noseR, null, geo.first, geo.second, 1.0, cr, cr, lr, ll)
                    val f = f0.copy(arcR = Math.round((f0.arcR!! - below) * 1000) / 1000.0)
                    n++
                    checkCase(f, t, listOf(CompStartup.C), big = false, allow = setOf(TaperWarning.ARC_R_TOO_SMALL))
                }
        t.note("사례 수 = $n")
        finish(t, "가장 작은 R 바로 아래", "sim-report-below.txt")
    }

    private fun r3(v: Double) = Math.round(v * 1000) / 1000.0

    /**
     * 씨앗 고정 무작위: 지름 1~100(가는 핀 · 좁은 구멍 · 단면이 축까지), 현 0.2~15, R = 최소 R ~ 300 배, 노즈R 0.1~1.6, 모서리 R, 앞뒤 요소 0.05~12 mm · 비움,
     * Z 여유 0~5, 팁 표준 · 1~9
     */
    @Test
    fun random() {
        val rnd = kotlin.random.Random(20261112)
        val t = Tally()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        fun <T> pick(l: List<T>): T = l[rnd.nextInt(l.size)]
        repeat(40_000) { n ->
            val inner = rnd.nextBoolean()
            val noseR = pick(listOf(0.1, 0.2, 0.4, 0.4, 0.8, 0.8, 1.2, 1.6))
            val k = when (rnd.nextInt(5)) {
                0 -> 1.0
                1 -> u(1.0, 1.2)
                2 -> u(1.2, 3.0)
                3 -> u(3.0, 30.0)
                else -> u(30.0, 300.0)
            }
            fun cr() = if (rnd.nextBoolean()) 0.0 else pick(listOf(0.1, 0.2, 0.4, 0.5, 1.0, 2.0, 3.0))
            fun leg() = when (rnd.nextInt(3)) {
                0 -> null
                1 -> r3(u(0.05, 1.0))
                else -> r3(u(1.0, 12.0))
            }
            val f = form(
                inner, 1 + rnd.nextInt(5), rnd.nextInt(3) == 0, rnd.nextBoolean(), rnd.nextBoolean(), noseR, if (rnd.nextInt(5) < 3) null else 1 + rnd.nextInt(9),
                r3(u(0.2, 15.0)), r3(u(0.2, 15.0)), k, cr(), cr(), leg(), leg(), pick(listOf(0.0, 0.3, 1.0, 1.0, 2.0, 5.0)), r3(u(if (inner) 8.0 else 1.0, 100.0)),
            )
            // R 이 치수 한계(10 000 mm)를 넘으면 BAD_INPUT 으로 멈추는 것이 맞다
            val allow = if (f.arcR!! > 10_000.0) setOf(TaperWarning.BAD_INPUT) else emptySet()
            checkCase(f, t, if (n % 3 == 0) listOf(CompStartup.C, CompStartup.AB) else listOf(CompStartup.C), big = false, allow = allow)
        }
        finish(t, "무작위", "sim-report-random.txt")
    }

    // ================================================================ 글의 바깥 90° 넘는 꺾임(CompFrame 규칙: 그런 줄은 짜지 않는다)

    private class Jn(val line: Int, val turnDeg: Double, val reversal: Boolean, val gouge: Double, val gougeAt: Pt?, val at: Pt)

    /** 모달 이동 줄 → 기하 요소(G2/G3 은 R 의 작은 호) */
    private fun ncSegs(cmds: List<Cmd>, start: Pt): List<Seg> {
        var p = start
        val out = mutableListOf<Seg>()
        for (c in cmds) {
            val q = Pt(c.z, c.x / 2)
            val rr = c.r
            if (c.g <= 1 || rr == null) out += Line(p, q) else {
                val cw = c.g == 2
                val d = q - p
                val h = sqrt(max(0.0, rr * rr - (d dot d) / 4))
                out += Arc(p, q, (p + q) * 0.5 + unit(left(d)) * (if (cw) -h else h), cw)
            }
            p = q
        }
        return out
    }

    private fun tanAtT(s: Seg, t: Double): Pt = when (s) {
        is Line -> unit(s.end - s.start)
        is Arc -> unit(pointAt(s, t) - s.center).let { if (s.cw) left(it) * -1.0 else left(it) }
    }

    /** 도면에서 공구 쪽이면 r − 거리, 소재 쪽이면 r + 거리(가장 가까운 점의 공구 쪽 법선 — 꼭짓점은 두 법선의 합) */
    private fun depth(p: Pt, d: List<Seg>, side: String, r: Double): Double {
        fun nrm(t: Pt) = if (side == "G41") left(t) else left(t) * -1.0
        val c = d.map { s ->
            val (q, tq) = when (s) {
                is Line -> {
                    val v = s.end - s.start
                    val k = ((p - s.start) dot v) / (v dot v)
                    val kk = k.coerceIn(0.0, 1.0)
                    s.start + v * kk to tanAtT(s, kk)
                }
                is Arc -> if (param(s, p) <= sweep(s) + 1e-12) (s.center + unit(p - s.center) * s.radius).let { it to unit(it - s.center).let { u -> if (s.cw) left(u) * -1.0 else left(u) } }
                else if ((p - s.start).length < (p - s.end).length) s.start to tanAt(s, false) else s.end to tanAt(s, true)
            }
            Triple((p - q).length, q, nrm(tq))
        }
        val best = c.minOf { it.first }
        val near = c.filter { it.first <= best + 1e-9 }
        val n = near.fold(Pt(0.0, 0.0)) { acc, it -> acc + it.third }
        val q = near.first().second
        if (best < 1e-12) return r
        return if (((p - q) dot n) >= 0) r - best else r + best
    }

    /**
     * 보정 중 이음(시작 · 취소 블록 빼고) 가운데 바깥으로 90° 넘게 꺾이거나 제자리로 되돌아가는 것. 그때 제어기가 바깥 꺾임으로 돈다고 보고
     * (A 의 보정선 → 법선 끝 e + nA·r → 늘인 토막 → e + nB·r → B 의 보정선) 노즈가 도면을 파는 깊이
     */
    private fun acuteJunctions(nc: List<String>, side: String, d: List<Seg>, r: Double): List<Jn> {
        val i40 = nc.indexOf("G40")
        val cmds = modal(nc.subList(1, i40))
        if (cmds.size < 3) return emptyList()
        val segs = ncSegs(cmds.drop(1), Pt(cmds[0].z, cmds[0].x / 2))
        fun nrm(t: Pt) = if (side == "G41") left(t) else left(t) * -1.0
        fun off(s: Seg) = (0..40).map { pointAt(s, it / 40.0) + nrm(tanAtT(s, it / 40.0)) * r }
        val out = mutableListOf<Jn>()
        for (i in 0 until segs.size - 1) {
            val a = segs[i]
            val b = segs[i + 1]
            if (length(a) < 1e-9 || length(b) < 1e-9) continue
            val ta = tanAt(a, true)
            val tb = tanAt(b, false)
            val na = nrm(ta)
            val nb = nrm(tb)
            val dt = ta dot tb
            val reversal = abs(ta cross tb) < 1e-6 && dt < 0
            if (!(reversal || ((tb dot na) < 0 && dt < -2e-4))) continue
            val e = a.end
            val corner = listOf(e + na * r, e + na * r + ta * r, e + nb * r - tb * r, e + nb * r)
            // 꼭짓점 둘레(3r 안)의 보정선만 — 그 너머는 다음 이음에서 제어기가 따로 정한다
            val pts = (off(a) + (0 until corner.size - 1).flatMap { k -> (0..20).map { corner[k] + (corner[k + 1] - corner[k]) * (it / 20.0) } } + off(b))
                .filter { (it - e).length <= 3 * r + 1e-9 }
            val worst = pts.maxBy { depth(it, d, side, r) }
            out += Jn(i + 3, Math.toDegrees(Math.acos(dt.coerceIn(-1.0, 1.0))), reversal, depth(worst, d, side, r), worst, e)
        }
        return out
    }

    /**
     * 가장 작은 R 둘레(+0.001 ~ −0.007)에서 기계 보정 글에 바깥 90° 넘는 꺾임이 생기나 — 시뮬레이터는 COMP_ACUTE_OUTER 로 멈춰 깊이를 못 잰다.
     * 결과는 build/verify1110arc/acute-report.txt
     */
    @Test
    fun acuteOuterProbe() {
        val cnt = sortedMapOf<String, Int>()
        val first = linkedMapOf<String, MutableList<String>>()
        var n = 0
        var solved = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (convex in listOf(true, false)) for (noseR in listOf(0.2, 0.4, 0.8, 1.2)) for (geo in geos.take(3)) for (below in listOf(-0.001, 0.0, 0.001, 0.003, 0.007))
                for (cr in listOf(0.0, 0.5)) for (lr in legs) for (ll in legs) {
                    val f0 = form(inner, type, reverse, flip, convex, noseR, null, geo.first, geo.second, 1.0, cr, cr, lr, ll)
                    val f = f0.copy(arcR = Math.round((f0.arcR!! - below) * 1000) / 1000.0)
                    n++
                    val res = try {
                        TaperSolve.solve(f)
                    } catch (e: Throwable) {
                        cnt.merge("SOLVE_THROW", 1, Int::plus)
                        continue
                    }
                    val s = res.solution ?: continue
                    solved++
                    for (j in acuteJunctions(s.ncG41G42, s.side.name, s.drawing.segs, noseR)) {
                        val bel = if (below < 0) "R 최소+0.001" else if (below == 0.0) "R = 최소" else "R 최소−$below"
                        val key = "${if (j.reversal) "되돌아감" else "바깥 %.1f°".format(j.turnDeg)} · 파고듦 ${if (j.gouge > tol) "있음" else "없음"} · " +
                            "$bel · 경고 ${res.warnings.map { it.name }.sorted()}"
                        cnt.merge(key, 1, Int::plus)
                        val l = first.getOrPut(key) { mutableListOf() }
                        if (l.size < 3) l += "${j.line}줄 '${s.ncG41G42.getOrNull(j.line - 1)}' 꼭짓점 ${xz(j.at)} · 깊이 ${"%.4f".format(j.gouge)} 자리 ${j.gougeAt?.let(::xz)}\n" +
                            "${desc(f)}\n${s.ncG41G42.joinToString("\n")}"
                        else if (j.reversal) l += "깊이 ${"%.4f".format(j.gouge)} · ${desc(f)}"
                    }
                }
        val text = buildString {
            appendLine("==== 바깥 90° 넘는 꺾임(기계 보정 글) — 사례 $n · 풀림 $solved")
            cnt.forEach { (k, v) -> appendLine("$k = $v") }
            first.forEach { (k, l) ->
                appendLine("---- $k")
                l.forEach { appendLine(it); appendLine() }
            }
        }
        File("build/verify1110arc").mkdirs()
        File("build/verify1110arc/acute-report.txt").writeText(text)
        println(text.lineSequence().take(80).joinToString("\n"))
        // 꼭 90° 를 소수 잡음만큼 넘는 바깥 꺾임(파고듦 0)은 시뮬레이터 한계로 적어만 두고, 되돌아감 · 파고듦만 실패로
        val bad = cnt.filterKeys { it.startsWith("되돌아감") || it.contains("파고듦 있음") }
        assertTrue(bad.isEmpty(), "되돌아감 · 파고듦 ${bad.values.sum()} 건 — build/verify1110arc/acute-report.txt")
    }

    /**
     * 짧은 시작을 뺀(SHORT_END_SKIPPED) 직각 진입이 가장 작은 R(또는 그 0.001 올림)의 볼록 호와 꼭 반대로 이어지는 꼴(되돌아감) — 재현 입력 둘.
     * 기계 보정 글이 시뮬레이터에서 멈추지 않고, 글에 되돌아감 · 바깥으로 돌 때 파고듦이 없어야 한다
     */
    @Test
    fun shortStartReversal() {
        val cases = listOf(
            SlopeForm(
                kind = TaperKind.OUTER, type = 3, reverse = true, noseR = 0.2, tip = null, x = listOf(30.0, 40.0), z = listOf(-7.0, -2.0), angle = null,
                zClear = 1.0, corners = listOf(0.0, 0.0), start = -7.3, end = null, flip = false, angleRef = AngleRef.X, arc = true, arcR = 5.0, arcConcave = false,
            ),
            SlopeForm(
                kind = TaperKind.INNER, type = 5, reverse = false, noseR = 0.2, tip = 4, x = listOf(72.096, 51.916), z = listOf(-7.086, -2.0), angle = null,
                zClear = 2.0, corners = listOf(0.0, 1.0), start = -7.359, end = -1.128, flip = true, angleRef = AngleRef.X, arc = true, arcR = 12.552, arcConcave = false,
            ),
        )
        val bad = mutableListOf<String>()
        for (f in cases) {
            val res = TaperSolve.solve(f)
            val s = res.solution ?: continue // 멈추면(경고로 알림) 그것도 받는다
            val stop = sim(s.ncG41G42, f.noseR, s.tip, CompStartup.C).stop
            val js = acuteJunctions(s.ncG41G42, s.side.name, s.drawing.segs, f.noseR).filter { it.reversal || it.gouge > tol }
            if (stop != null || js.isNotEmpty()) {
                bad += "${desc(f)}\n경고 ${res.warnings} · 시뮬 멈춤 ${stop?.code} · 되돌아감 ${js.map { "${it.line}줄 깊이 ${"%.4f".format(it.gouge)}" }}\n${s.ncG41G42.joinToString(" ")}"
            }
        }
        assertEquals(emptyList(), bad)
    }

    /**
     * 무작위에서 나온 직접 보정 축 너머 후퇴(`G0X-10.` — 노즈가 맞은편 = 같은 회전면의 호를 팜)가 T1–T2 직선에도 있나(기존 문제인가).
     * 결과는 build/verify1110arc/followups.txt
     */
    @Test
    fun followUps() {
        val out = StringBuilder()
        val base = SlopeForm(
            kind = TaperKind.INNER, type = 2, reverse = false, noseR = 0.8, tip = null, x = listOf(20.324, 9.23), z = listOf(-2.0, -2.504),
            angle = null, zClear = 5.0, corners = listOf(0.0, 0.4), start = 29.082, end = 0.0, flip = false, angleRef = AngleRef.X,
            arc = true, arcR = 50.771, arcConcave = false,
        )
        for (f in listOf(base, base.copy(arc = false), base.copy(arc = false, corners = listOf(0.0, 0.0)), base.copy(arc = false, zClear = 1.0))) {
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s == null) {
                out.appendLine("원호 ${f.arc}: 멈춤 ${res.warnings}")
                continue
            }
            val r = f.noseR
            val near = s.drawing.segs.flatMapIndexed { i, sg -> sample(sg, i, sqrt(2 * r * tol)) }
            val surf = near + near.map { Sm(Pt(it.p.z, -it.p.x), it.seg, it.s, it.len) }
            val run = sim(s.ncG40, r, s.tip, CompStartup.C, feed = true)
            val moves = run.steps.filterIsInstance<MoveStep>()
            val firstMove = pre.size + s.ncG40.indexOfFirst { !it.startsWith("(") } + 1
            val p = probe(moves.filter { it.line > firstMove }, moves.filter { it.kind == MoveKind.FEED }, surf, emptyList(), r)
            out.appendLine(
                "원호 ${f.arc} · 모서리 R ${f.corners} · Z 여유 ${f.zClear} · 경고 ${res.warnings} · 멈춤 ${run.stop?.code} · 파고듦 ${p.gouge} · " +
                    "줄 '${p.gougeBy?.let { s.ncG40.getOrNull(it.line - pre.size - 1) }}' 노즈 ${p.gougeBy?.let { xz(it.nose.start) + " → " + xz(it.nose.end) }}\n" +
                    s.ncG40.joinToString(" "),
            )
        }
        // 무작위의 D_NOARC: 모서리 R 2.0 이 호 대부분을 먹은 꼴 — 직접 보정 글에 호 줄이 없다. 남은 호 위 덜 깎임을 오목 꼭짓점 제외 없이 잰다
        run {
            val f = SlopeForm(
                kind = TaperKind.OUTER, type = 2, reverse = true, noseR = 0.4, tip = 5, x = listOf(69.422, 70.208), z = listOf(-2.724, -2.0), angle = null,
                zClear = 0.0, corners = listOf(2.0, 0.0), start = null, end = null, flip = false, angleRef = AngleRef.X, arc = true, arcR = 0.864, arcConcave = true,
            )
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s == null) out.appendLine("D_NOARC 꼴: 멈춤 ${res.warnings}") else {
                val r = f.noseR
                val a = s.arc?.let { s.drawing.segs[it.index] }
                val must = a?.let { sg -> sample(sg, 0, 0.002).map { it.p } } ?: emptyList()
                val surf = s.drawing.segs.flatMapIndexed { i, sg -> sample(sg, i, sqrt(2 * r * tol)) }
                val mv = sim(s.ncG40, r, s.tip, CompStartup.C, feed = true).steps.filterIsInstance<MoveStep>()
                val p = probe(mv.drop(1), mv.filter { it.kind == MoveKind.FEED }, surf, must, r)
                out.appendLine(
                    "D_NOARC 꼴: 경고 ${res.warnings} · 남은 호 길이 ${a?.let { length(it) }} · 직접 보정 덜 깎임(호 위) ${p.under} 자리 ${p.underAt?.let(::xz)} · 파고듦 ${p.gouge}\n" +
                        "도면 ${s.drawing.segs.joinToString(" | ")}\nG40: ${s.ncG40.joinToString(" ")}\nG41/42: ${s.ncG41G42.joinToString(" ")}",
                )
            }
        }
        // 격자의 CORNER_R_TOO_BIG(넉넉한 앞뒤 요소) 가 호가 앞뒤 요소에 접하는 끝에 모서리 R 을 넣은 것뿐인가
        val stops = sortedMapOf<String, Int>()
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (convex in listOf(true, false)) for (geo in geos.take(3)) for (k in listOf(1.0, 1.4, 6.0)) for (cr in 1..3) for (lr in listOf(null, 3.0)) for (ll in listOf(null, 3.0)) {
                val f = form(inner, type, reverse, flip, convex, 0.4, null, geo.first, geo.second, k, if (cr and 1 != 0) 0.5 else 0.0, if (cr and 2 != 0) 0.5 else 0.0, lr, ll)
                val res = TaperSolve.solve(f)
                if (TaperWarning.CORNER_R_TOO_BIG !in res.warnings) continue
                val e = expect(f)
                val (fv, bv) = legsVertical(f.kind, f.type, f.flip)
                fun touches(t: Pt, vertical: Boolean) = if (vertical) abs(t.z) < 2e-3 else abs(t.x) < 2e-3
                val t1 = touches(tanAt(e.full, false), fv) && f.corners[0] > 0
                val t2 = touches(tanAt(e.full, true), bv) && f.corners[1] > 0
                stops.merge("k $k · ${if (t1 || t2) "접하는 끝에 모서리 R" else "접하지 않는 끝"}", 1, Int::plus)
            }
        out.appendLine("CORNER_R_TOO_BIG 분포(노즈R 0.4 · 모서리 R 0.5 · 앞뒤 비움/3): $stops")
        File("build/verify1110arc").mkdirs()
        File("build/verify1110arc/followups.txt").writeText(out.toString())
        println(out)
    }

    // ================================================================ 못 그리는 입력

    private fun hangul(s: String) = s.any { it in '가'..'힣' }

    /** 못 그리는 값 · 치는 중간 값: 던지지 않고, 멈추면 경고 하나 + 한글 글. 가장 작은 R 의 경계 */
    @Test
    fun badInputs() {
        val bad = mutableListOf<String>()
        val nan = Double.NaN
        val base = form(false, 1, false, false, true, 0.4, null, 5.0, 5.0, 2.0, 0.0, 0.0, null, null)
        val rs = listOf(null, 0.0, -1.0, 1e-9, 1e-4, 0.001, 3.5, 3.535, 3.536, 4.9, 4.99, 4.999, 4.9999, 5.0, 5.001, 1e4, 10000.001, 1e9, 1e300, nan, Double.POSITIVE_INFINITY)
        var count = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true)) for (convex in listOf(true, false))
            for (rr in rs) for (variant in 0..7) for (cr in listOf(0.0, 0.5, 50.0)) {
                var f = form(inner, type, reverse, flip, convex, 0.4, null, 5.0, 5.0, 2.0, cr, cr, null, null).copy(arcR = rr)
                f = when (variant) {
                    1 -> f.copy(x = listOf(f.x[0], f.x[0]))
                    2 -> f.copy(z = listOf(f.z[0], f.z[0]))
                    3 -> f.copy(x = listOf(f.x[0], f.x[0]), z = listOf(f.z[0], f.z[0]))
                    4 -> f.copy(x = listOf(null, f.x[1]))
                    5 -> f.copy(z = listOf(f.z[0], null), angle = 45.0)
                    6 -> f.copy(x = listOf(0.0, f.x[1]))
                    7 -> f.copy(x = listOf(f.x[1], f.x[0]))
                    else -> f
                }
                count++
                val res = try {
                    TaperSolve.solve(f)
                } catch (e: Throwable) {
                    bad += "던짐 ${desc(f)} → $e"
                    continue
                }
                if (res.solution == null) {
                    if (res.warnings.size != 1) bad += "멈췄는데 경고 ${res.warnings} — ${desc(f)}"
                    for (w in res.warnings) {
                        val text = try {
                            taperWarningText(w, f)
                        } catch (e: Throwable) {
                            bad += "경고 글 던짐 $w ${desc(f)} → $e"
                            continue
                        }
                        if (text.isBlank() || !hangul(text)) bad += "경고 글이 비었거나 한글이 아님 $w '$text'"
                        try {
                            taperWarningFields(w, f)
                        } catch (e: Throwable) {
                            bad += "경고 칸 던짐 $w ${desc(f)} → $e"
                        }
                    }
                } else if (variant == 0 && (rr == null || !(rr >= 4.993) || rr > 1e4)) {
                    bad += "풀리면 안 되는 R 이 풀림 ${desc(f)}"
                } else if (variant in 1..7) {
                    bad += "풀리면 안 되는 점이 풀림(variant $variant) ${desc(f)}"
                }
                // 최소 R 안내 값도 던지지 않는다
                try {
                    f.arcMinR
                    f.waiting
                } catch (e: Throwable) {
                    bad += "arcMinR 던짐 ${desc(f)} → $e"
                }
            }
        // 경계: 5×5 의 가장 작은 R 은 5. 0.01 아래(4.99)는 멈추고 글에 5.000 을 적는다, 5 와 그 위는 풀린다(4.993~5 는 반올림 허용으로 받는다 — gridJustBelowMin)
        val below = TaperSolve.solve(base.copy(arcR = 4.99))
        if (below.warnings != listOf(TaperWarning.ARC_R_TOO_SMALL)) bad += "R4.99 가 ARC_R_TOO_SMALL 이 아님: ${below.warnings}"
        val msg = taperWarningText(TaperWarning.ARC_R_TOO_SMALL, base.copy(arcR = 4.99))
        if (!msg.contains("5.000")) bad += "R 과소 글에 최소 R 5.000 이 없음: $msg"
        if (TaperSolve.solve(base.copy(arcR = 5.0)).solution == null) bad += "R5(최소 R)가 안 풀림"
        if (base.arcMinR != 5.0) bad += "arcMinR ${base.arcMinR} ≠ 5"
        // 현의 절반(3.536) 바로 아래 · 위 모두 R 과소
        for (v in listOf(3.5, 3.535, 3.536, 4.0)) {
            val w = TaperSolve.solve(base.copy(arcR = v)).warnings
            if (w != listOf(TaperWarning.ARC_R_TOO_SMALL)) bad += "R$v 가 ARC_R_TOO_SMALL 이 아님: $w"
        }
        // 오목 R ≤ 노즈R: R 0.9 현 0.72 오목에 노즈R 1.2 → 멈춤(틀린 숫자 금지)
        val small = form(false, 3, false, false, false, 1.2, null, 0.6, 0.4, 1.4, 0.0, 0.0, null, null)
        val sr = TaperSolve.solve(small)
        if (sr.solution != null && small.arcR!! < small.noseR) bad += "오목 R ${small.arcR} < 노즈R 1.2 인데 풀림"
        // 원호를 끄면 R · 볼록 값은 결과에 영향이 없다(직선 그대로)
        for (inner in listOf(false, true)) for (type in 1..5) for (flip in listOf(false, true)) for (reverse in listOf(false, true)) {
            val line = form(inner, type, reverse, flip, true, 0.4, null, 5.0, 5.0, 2.0, 0.5, 0.0, 3.0, null).copy(arc = false)
            val a = TaperSolve.solve(line)
            val b = TaperSolve.solve(line.copy(arcR = 3.0, arcConcave = true))
            val c = TaperSolve.solve(line.copy(arcR = null))
            if (a != b || a != c) bad += "원호를 껐는데 R 값이 결과를 바꿈 ${desc(line)}"
            if (a.solution?.arc != null) bad += "직선인데 solution.arc 가 있음"
        }
        // 계산기 직접: 숫자가 아닌 좌표 · 팁
        for (v in listOf(nan, Double.POSITIVE_INFINITY, 1e9, -1e9)) for (slot in 0..4) {
            val i = TaperInput(arc = true, arcR = if (slot == 4) v else 10.0, x1 = if (slot == 0) v else 40.0, z1 = if (slot == 1) v else 0.0, x2 = if (slot == 2) v else 50.0, z2 = if (slot == 3) v else -5.0)
            try {
                val res = TaperCalc.calc(i)
                if (res.solution != null) bad += "숫자가 아닌 값이 풀림 $i"
            } catch (e: Throwable) {
                bad += "계산기 던짐 $i → $e"
            }
        }
        println("badInputs: $count 사례 · 문제 ${bad.size}")
        File("build/verify1110arc").mkdirs()
        File("build/verify1110arc/bad-report.txt").writeText("사례 $count\n" + bad.joinToString("\n"))
        assertEquals(emptyList(), bad.take(20), "문제 ${bad.size} 건 — build/verify1110arc/bad-report.txt")
    }

    // ================================================================ 설계B 의 손 계산 예제

    private class Cmd(val g: Int, val x: Double, val z: Double, val r: Double?) {
        override fun toString() = "G$g X$x Z$z" + (r?.let { " R$it" } ?: "")
    }

    /** 글을 모달로 풀어 이동 줄마다 (G, X, Z, R) */
    private fun modal(lines: List<String>): List<Cmd> {
        var g = 0
        var x = Double.NaN
        var z = Double.NaN
        val out = mutableListOf<Cmd>()
        for (line in lines) {
            if (line.startsWith("(")) continue
            var r: Double? = null
            var moved = false
            for (w in ncWords(line)) when (w[0]) {
                'G' -> w.drop(1).toIntOrNull()?.let { if (it in 0..3) g = it }
                'X' -> { x = w.drop(1).toDouble(); moved = true }
                'Z' -> { z = w.drop(1).toDouble(); moved = true }
                'R' -> r = w.drop(1).toDouble()
            }
            if (moved) out += Cmd(g, x, z, r)
        }
        return out
    }

    private fun sameCmd(a: Cmd, b: Cmd) = a.g == b.g && abs(a.x - b.x) <= 1.5e-3 && abs(a.z - b.z) <= 1.5e-3 &&
        ((a.r == null) == (b.r == null)) && (a.r == null || abs(a.r!! - b.r!!) <= 1.5e-3)

    private fun containsRun(all: List<Cmd>, want: List<Cmd>): Boolean =
        (0..all.size - want.size).any { i -> want.indices.all { sameCmd(all[i + it], want[it]) } }

    private class Ex(
        val no: Int, val inner: Boolean, val type: Int, val flip: Boolean, val x1: Double, val z1: Double, val x2: Double, val z2: Double, val arcR: Double,
        val convex: Boolean, val noseR: Double, val cr1: Double, val cr2: Double, val start: Double?, val end: Double?,
        val side: String, val tip: Int, val arcG: Int, val progR: Double, val cx: Double, val cz: Double, val direct: String, val machine: String,
    )

    /** 설계B(기하에서 따로 계산 — 임시 폴더 arc_table.md)의 예제 12개. 값은 그 표 그대로 */
    private val examples = listOf(
        Ex(1, false, 1, false, 40.0, 0.0, 50.0, -5.0, 10.0, true, 0.4, 0.0, 0.0, null, null, "G42", 3, 3, 10.4, 31.771, -9.114, "G1X39.37Z0. G3X50.Z-5.315R10.4", "G1X40.Z0. G3X50.Z-5.R10."),
        Ex(2, false, 3, false, 30.0, -10.0, 50.0, -20.0, 12.0, false, 0.4, 0.0, 0.0, null, null, "G42", 3, 2, 11.6, 53.711, -8.144, "G1X30.Z-10.369 G2X50.Z-20.053R11.6", "G1X30.Z-10. G2X50.Z-20.R12."),
        Ex(3, true, 1, false, 40.0, 0.0, 34.0, -3.0, 6.0, true, 0.4, 0.0, 0.0, null, null, "G41", 2, 2, 6.4, 44.937, -5.469, "G1X40.631Z0. G2X34.Z-3.315R6.4", "G1X40.Z0. G2X34.Z-3.R6."),
        Ex(4, false, 2, false, 20.0, 0.0, 60.0, -3.0, 80.0, true, 0.8, 0.0, 0.0, null, null, "G42", 3, 3, 80.8, 16.456, -79.980, "G1X18.418Z0. G3X58.621Z-3.R80.8", "G1X20.Z0. G3X60.Z-3.R80."),
        Ex(
            5, false, 1, false, 40.0, 0.0, 50.0, -5.0, 10.0, true, 0.4, 1.0, 2.0, 30.0, -12.0, "G42", 3, 3, 10.4, 31.771, -9.114,
            "G1X30.Z0. X38.758 G3X39.969Z-.138R1.4 X49.469Z-4.758R10.4 X50.Z-5.856R2.4 G1Z-12.", "G1X30.Z0. X39.558 G3X40.423Z-.098R1. X49.557Z-4.541R10. X50.Z-5.456R2. G1Z-12.",
        ),
        Ex(
            6, false, 3, true, 50.0, -20.0, 30.0, -10.0, 12.0, false, 0.4, 1.0, 1.5, null, null, "G41", 3, 3, 11.6, 53.711, -8.144,
            "G1X50.Z-21.227 G2X47.815Z-19.861R1.4 G3X30.03Z-10.461R11.6 X30.Z-10.279R1.1", "G1X50.Z-20.827 G2X48.439Z-19.851R1. G3X30.041Z-10.127R12. X30.Z-9.879R1.5",
        ),
        Ex(
            7, true, 3, true, 30.0, -20.0, 50.0, -10.0, 12.0, false, 0.4, 0.5, 0.0, null, null, "G42", 2, 2, 11.6, 26.289, -8.144,
            "G1X30.Z-20.82 G3X31.461Z-19.937R.9 G2X50.Z-10.369R11.6", "G1X30.Z-20.42 G3X30.812Z-19.929R.5 G2X50.Z-10.R12.",
        ),
        Ex(8, false, 3, false, 30.0, -10.0, 50.0, -20.0, 10.0, false, 0.4, 1.0, 0.0, null, null, "G42", 3, 2, 9.6, 50.0, -10.0, "G1X30.Z-10.4 G2X35.909Z-17.328R9.6 X50.Z-19.992R9.6", "G1X30.Z-10. G2X50.Z-20.R10."),
        Ex(
            9, true, 1, false, 40.0, 0.0, 34.0, -3.0, 6.0, true, 0.8, 0.5, 0.5, null, null, "G41", 2, 2, 6.8, 44.937, -5.469,
            "G1X41.82Z0. G2X40.705Z-.126R1.3 X34.251Z-3.353R6.8 X34.Z-3.91R1.3", "G1X40.22Z0. G2X39.791Z-.048R.5 X34.097Z-2.896R6. X34.Z-3.11R.5",
        ),
        Ex(10, false, 5, true, 40.0, -18.0, 50.0, -10.0, 15.0, true, 0.4, 0.0, 0.0, null, null, "G41", 3, 2, 15.4, 20.851, -6.453, "G1X40.Z-18.586 G2X50.Z-10.448R15.4", "G1X40.Z-18. G2X50.Z-10.R15."),
        Ex(11, true, 3, false, 50.0, -10.0, 30.0, -20.0, 12.0, false, 0.4, 0.0, 0.0, null, null, "G41", 2, 3, 11.6, 26.289, -8.144, "G1X50.Z-10.369 G3X30.Z-20.053R11.6", "G1X50.Z-10. G3X30.Z-20.R12."),
        Ex(
            12, false, 4, false, 30.0, -10.0, 44.0, -18.0, 9.0, false, 0.4, 0.0, 1.0, null, 60.0, "G42", 3, 2, 8.6, 47.932, -9.217,
            "G1X30.Z-10.382 G2X43.15Z-17.984R8.6 X43.428Z-18.R.6 G1X60.", "G1X30.Z-10. G2X43.765Z-17.973R9. X44.228Z-18.R1. G1X60.",
        ),
    )

    @Test
    fun designBExamples() {
        val bad = mutableListOf<String>()
        val t = Tally()
        for (x in examples) {
            val f = SlopeForm(
                kind = if (x.inner) TaperKind.INNER else TaperKind.OUTER, type = x.type, reverse = false, noseR = x.noseR, tip = null,
                x = listOf(x.x1, x.x2), z = listOf(x.z1, x.z2), angle = null, zClear = 1.0, corners = listOf(x.cr1, x.cr2), start = x.start, end = x.end,
                flip = x.flip, angleRef = AngleRef.X, arc = true, arcR = x.arcR, arcConcave = !x.convex,
            )
            val res = TaperSolve.solve(f)
            val s = res.solution
            if (s == null) {
                bad += "예제 ${x.no}: 안 풀림 ${res.warnings}"
                continue
            }
            val a = s.arc
            if (s.side.name != x.side) bad += "예제 ${x.no}: 보정 쪽 ${s.side} ≠ ${x.side}"
            if (s.tip != x.tip) bad += "예제 ${x.no}: 팁 ${s.tip} ≠ ${x.tip}"
            if (a == null) bad += "예제 ${x.no}: arc null" else {
                if ((if (a.cw) 2 else 3) != x.arcG) bad += "예제 ${x.no}: 호 방향 G${if (a.cw) 2 else 3} ≠ G${x.arcG}"
                if (a.progR == null || abs(a.progR!! - x.progR) > 1e-6) bad += "예제 ${x.no}: 합성 R ${a.progR} ≠ ${x.progR}"
                if (abs(a.center.xDia - x.cx) > 1.5e-3 || abs(a.center.z - x.cz) > 1.5e-3) bad += "예제 ${x.no}: 중심 ${xz(a.center)} ≠ X${x.cx} Z${x.cz}"
            }
            val wantD = modal(x.direct.split(" "))
            val wantM = modal(x.machine.split(" "))
            if (!containsRun(modal(s.ncG40), wantD)) bad += "예제 ${x.no}: 직접 보정 형상 줄이 다름\n  기대 ${x.direct}\n  실제 ${s.ncG40.joinToString(" ")}"
            if (!containsRun(modal(s.ncG41G42), wantM)) bad += "예제 ${x.no}: 기계 보정 형상 줄이 다름\n  기대 ${x.machine}\n  실제 ${s.ncG41G42.joinToString(" ")}"
            // 예제도 같은 눈으로(도면 · 시뮬)
            checkCase(f, t, listOf(CompStartup.C, CompStartup.AB), big = false)
        }
        File("build/verify1110arc").mkdirs()
        File("build/verify1110arc/examples-report.txt").writeText(bad.joinToString("\n") + "\n" + t.report("설계B 예제 12개"))
        println(bad.joinToString("\n"))
        assertEquals(emptyList(), bad, "설계B 예제와 다름")
        assertTrue(t.count.isEmpty(), "예제 시뮬 실패 ${t.count}")
    }
}
