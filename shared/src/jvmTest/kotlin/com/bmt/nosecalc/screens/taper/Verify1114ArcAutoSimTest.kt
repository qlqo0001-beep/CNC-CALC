package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
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
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(사용자 피드백 2026-11-14 "z값이 자동으로 안나타나" — 테이퍼 원호의 빈 좌표를 호가 앞뒤 요소에 접하는 자리로 채움)의 동작 쪽.
 * 구현자의 시험 · 계산기 안쪽 함수를 쓰지 않는다. 화면이 부르는 길(`SlopeForm` → `TaperSolve.solve`)로 풀고:
 * 1. 접하는 끝을 따로 유도한다 — 그 끝의 요소 법선 위 중심(P + nR)이 현의 소재 쪽이면 볼록 · 공구 쪽이면 오목. 고른 볼록 · 오목과 맞는 끝만,
 *    둘 다 되면 두 좌표를 다 넣은 점(고침 단계 R-VR1 — 검증 때는 빈 좌표의 점), 하나도 없으면 ARC_NO_TANGENT. 채운 값 · 호 중심을 그 끝의 식으로 따로 계산해 견준다
 * 2. 채운 값을 네 좌표로 넣은 것(값 그대로)과 경고 · 두 글이 꼭 같다
 * 3. 도면: 접한 끝에서 호와 그 요소가 꺾임 없이 이어지고(모서리 R 을 넣었어도 그 끝은 필렛 없음), 두 글의 그 이음도 꺾이지 않는다
 * 4. 직접 보정 글 · 기계 보정 글(형식 C · 가끔 A/B)을 GSim 으로: 멈춤 0 · 파고듦 ≤ 0.002 · 덜 깎임 ≤ 0.002 · 회사 흐름 · 회사 표기
 * 결과 요약은 build/verify1114arcauto/ 에 쓴다
 */
class Verify1114ArcAutoSimTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** 파고듦 · 덜 깎임 허용(mm) — 지시서 '파고듦 < 0.002' */
    private val tol = 2e-3

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

    /** T1(a) → T2(b) 호(화면 좌표 — 네 좌표가 다 찬 f): 소재 쪽은 외경이면 X−, 내경이면 X+. 볼록 = 중심이 소재 쪽 */
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
        val side = if ((left(d) dot m) < 0) "G41" else "G42"
        return Exp(a, b, c, !ccw, side, Arc(a, b, c, !ccw))
    }

    private fun smooth(p: Seg, q: Seg): Boolean {
        val t1 = tanAt(p, true)
        val t2 = tanAt(q, false)
        return abs(t1 cross t2) < 1e-6 && (t1 dot t2) > 0
    }

    /** 도면이 넣은(채운) 값의 형상인가 — Verify1110ArcSimTest 의 같은 검사 */
    private fun drawingError(f: SlopeForm, s: TaperSolution, e: Exp): String? {
        val d = s.drawing.segs
        val n = d.size
        val sa = s.arc ?: return "NOARC solution.arc 가 null"
        val k = sa.index
        val a = d.getOrNull(k) as? Arc ?: return "NOARC drawing[$k] 이 원호가 아님"
        val rr = f.arcR!!
        if (abs(a.radius - rr) > 1e-9 * max(1.0, rr)) return "RADIUS ${a.radius} ≠ $rr"
        if ((a.center - e.c).length > 1e-6 * max(1.0, rr)) return "CENTER ${a.center} ≠ 기대 ${e.c}"
        if (a.cw != e.cw) return "CW 도면 호 방향 cw=${a.cw} ≠ 기대 ${e.cw}"
        val want = if (f.arcConcave) rr - f.noseR else rr + f.noseR
        val pr = sa.progR
        // 오목 R = 노즈R 이면 합성 R 0 — 계산기는 null(원호 없음)로 둔다(CONCAVE_R_LE_NOSE)
        if (!(pr == null && want <= 1e-9) && (pr == null || abs(pr - want) > 1e-9 * max(1.0, rr))) return "PROGR 합성 R $pr ≠ $want"
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

    private fun concaveSharp(p: Seg, q: Seg, side: String): Boolean {
        val t1 = tanAt(p, true)
        val t2 = tanAt(q, false)
        if (abs(t1 cross t2) < 1e-3) return false
        val toolN = if (side == "G41") left(t1) else left(t1) * -1.0
        return (t2 dot toolN) > 0
    }

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
        var fillErr = 0.0
        var centerErr = 0.0
        var drawKink = 0.0
        var ncKinkM = 0.0
        var ncKinkD = 0.0
        var runs = 0
        var cases = 0

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
            appendLine("사례 $cases · 시뮬 실행 $runs")
            appendLine("통과한 것의 최대 — 기계 보정 파고듦 $gougeM 덜 깎임 $underM / 직접 보정 파고듦 $gougeD 덜 깎임 $underD")
            appendLine("채운 값 오차(따로 유도한 값과) 최대 $fillErr · 호 중심 오차 최대 $centerErr · 접한 끝 도면 꺾임(외적) 최대 $drawKink")
            appendLine("접한 끝 글 꺾임(rad) 최대 — 기계 보정 $ncKinkM · 직접 보정 $ncKinkD")
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

    private val blankName = listOf("X1", "Z1", "X2", "Z2")

    private fun desc(f: SlopeForm, blank: Int? = null) =
        "${f.kind.label} 형상 ${f.type}${if (f.reverse) " 역방향" else ""}${if (f.flip) " 뒤집기" else ""} · T1 X${f.x[0]} Z${f.z[0]} · T2 X${f.x[1]} Z${f.z[1]} · " +
            "R${f.arcR} ${if (f.arcConcave) "오목" else "볼록"} · 노즈R ${f.noseR} · 팁 ${f.tip} · 모서리 R ${f.corners} · 시작 ${f.start} · 끝 ${f.end} · Z 여유 ${f.zClear}" +
            (blank?.let { " · 빈 칸 ${blankName[it]}" } ?: "")

    // ================================================================ 접하는 끝(따로 유도)

    /**
     * 그림(거울 전 — 계산기 좌표, T1 = 오른쪽 점) 끝 하나에서 호가 그 요소에 접하면 볼록인가. 요소가 수평이면 중심 = P + (0, ±R)(다른 점 쪽),
     * 단면이면 P + (±R, 0). 그 중심이 현의 소재 쪽(외경 X− · 내경 X+)이면 볼록. 모양(사분면)만 보면 되므로 이름뿐인 점으로 잰다
     */
    private fun convexIfTangentAt(inner: Boolean, type: Int, t1: Boolean): Boolean {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        val a = Pt(0.0, 0.0)
        val b = Pt(-1.0, up * 1.0)
        val (p, q) = if (t1) a to b else b to a
        val vertical = if (t1) type <= 2 else type == 2 || type == 4
        val c = if (vertical) Pt(p.z + (if (q.z > p.z) 1.0 else -1.0), p.x) else Pt(p.z, p.x + (if (q.x > p.x) 1.0 else -1.0))
        val d = b - a
        var m = left(d)
        if ((m.x < 0) == inner) m = m * -1.0
        return ((c - (a + b) * 0.5) dot m) > 0
    }

    private fun verticalAt(type: Int, t1: Boolean) = if (t1) type <= 2 else type == 2 || type == 4

    /**
     * 그림 끝(T1 = 오른쪽) 가운데 접할 끝 — 없으면 null. 둘 다 되면 두 좌표를 다 넣은 그림 점(빈 좌표의 반대 점 — 고침 단계 2026-11-14 R-VR1 결정으로
     * 기대를 바꿈, 검증 때는 빈 좌표의 점이었다)
     */
    private fun tangentEnd(inner: Boolean, type: Int, convex: Boolean, blankAtFigT1: Boolean): Boolean? {
        val ok = listOf(true, false).filter { convexIfTangentAt(inner, type, it) == convex }
        return when (ok.size) {
            0 -> null
            1 -> ok.single()
            else -> !blankAtFigT1
        }
    }

    /** 접한 끝에서 넣은 쪽 차(given)로 빈 쪽 차를: 수평이면 Δz² + (R − Δr)² = R², 단면이면 Δr² + (R − Δz)² = R² */
    private fun otherDelta(vertical: Boolean, blankIsZ: Boolean, given: Double, r: Double): Double {
        val g = min(given, r)
        return when {
            !vertical && blankIsZ -> sqrt(max(0.0, r * r - (r - g) * (r - g)))
            !vertical -> r - sqrt(max(0.0, r * r - g * g))
            blankIsZ -> r - sqrt(max(0.0, r * r - g * g))
            else -> sqrt(max(0.0, r * r - (r - g) * (r - g)))
        }
    }

    /**
     * 그림(거울 전) Δz · Δr 로 세운 네 좌표 화면 칸. 오른쪽 점 Z −2(역방향은 거울), X 는 x0 에서 TYPE 방향으로.
     * legR · legL = 앞뒤 요소 길이(null = 비움) — Verify1110ArcSimTest.form 과 같은 꼴이고 R 은 그대로 넣는다
     */
    private fun form(
        inner: Boolean, type: Int, reverse: Boolean, flip: Boolean, convex: Boolean, noseR: Double, tip: Int?,
        dz: Double, dr: Double, arcR: Double, crR: Double, crL: Double, legR: Double?, legL: Double?, zClear: Double, x0: Double = 30.0,
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
            angle = null, zClear = zClear, corners = if (flip) listOf(crL, crR) else listOf(crR, crL),
            start = if (flip) endL else startR, end = if (flip) startR else endL, flip = flip, angleRef = AngleRef.X,
            arc = true, arcR = arcR, arcConcave = !convex,
        )
    }

    private fun blanked(f: SlopeForm, blank: Int): SlopeForm = when (blank) {
        0 -> f.copy(x = listOf(null, f.x[1]))
        1 -> f.copy(z = listOf(null, f.z[1]))
        2 -> f.copy(x = listOf(f.x[0], null))
        else -> f.copy(z = listOf(f.z[0], null))
    }

    // ================================================================ 글 읽기

    private class Cmd(val g: Int, val x: Double, val z: Double, val r: Double?)

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

    private fun ncSegs(cmds: List<Cmd>): List<Seg> {
        if (cmds.isEmpty()) return emptyList()
        var p = Pt(cmds[0].z, cmds[0].x / 2)
        val out = mutableListOf<Seg>()
        for (c in cmds.drop(1)) {
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

    /**
     * 글(보정 켠 동안 · 직접 보정은 전부)에서 반지름 rr · 중심 c 쪽의 호 줄과 접한 끝 쪽 이웃 줄 사이 꺾임(rad).
     * atStart = 접한 끝이 호의 가공 시작 끝. 호 줄이 없거나 이웃이 없으면 null
     */
    private fun ncKink(lines: List<String>, rr: Double, c: Pt, atStart: Boolean): Double? {
        val segs = ncSegs(modal(lines)).filter { length(it) > 1e-9 }
        val i = segs.indexOfFirst { it is Arc && abs(it.radius - rr) < 2e-3 && (it.center - c).length < 3e-3 * (1 + 2 * rr / max(1e-3, (it.end - it.start).length)) }
        if (i < 0) return null
        val a = segs[i]
        val nb = segs.getOrNull(if (atStart) i - 1 else i + 1) ?: return null
        val t1 = if (atStart) tanAt(nb, true) else tanAt(a, true)
        val t2 = if (atStart) tanAt(a, false) else tanAt(nb, false)
        return atan2(abs(t1 cross t2), t1 dot t2)
    }

    // ================================================================ 사례 하나

    /**
     * full = 네 좌표가 다 찬(채운 값 그대로) 화면 칸, blank = 비운 칸 번호(X1 · Z1 · X2 · Z2). 비운 것을 풀어 따로 유도한 값 · 네 좌표와 같은 글 ·
     * 접한 끝 이음 · 시뮬레이터를 본다
     */
    private fun checkCase(full: SlopeForm, blank: Int, t: Tally, forms: List<CompStartup>, given: Double, kGrid: Boolean = true) {
        t.cases++
        val f = blanked(full, blank)
        val res = try {
            TaperSolve.solve(f)
        } catch (e: Throwable) {
            t.fail("SOLVE_THROW") { "${desc(full, blank)}\n$e" }
            return
        }
        // 접할 끝(그림 · 거울 전): 칸 점 k 의 그림 자리 — 뒤집으면 칸 T1 이 그림 왼쪽(T2)
        val blankAtFigT1 = (blank / 2 == 0) != full.flip
        val end = tangentEnd(full.kind.inner, full.type, !full.arcConcave, blankAtFigT1)
        val s = res.solution
        if (end == null) {
            if (res.warnings != listOf(TaperWarning.ARC_NO_TANGENT)) t.fail("NO_TANGENT_EXPECTED") { "${desc(full, blank)}\n경고 ${res.warnings} · 풀림 ${s != null}" }
            else {
                t.note("ARC_NO_TANGENT(기대대로)")
                val text = taperWarningText(TaperWarning.ARC_NO_TANGENT, f)
                if (!("네 좌표" in text && (if (f.arcConcave) "오목" else "볼록") in text)) t.fail("NO_TANGENT_TEXT") { "${desc(full, blank)}\n$text" }
            }
            return
        }
        // 4 좌표로 넣은 것과 같은 결과(값 그대로)
        val again = if (s == null) TaperSolve.solve(full) else {
            val p = s.points.map { it.vertex }
            TaperSolve.solve(f.copy(x = listOf(p[0].xDia, p[1].xDia), z = listOf(p[0].z, p[1].z)))
        }
        if (s == null) {
            val w = res.warnings.joinToString("+")
            t.note("멈춤:$w")
            if (res.warnings.size != 1) t.fail("STOP_WARNINGS") { "${desc(full, blank)}\n${res.warnings}" }
            val never = setOf(TaperWarning.MISSING_INPUT, TaperWarning.BAD_INPUT, TaperWarning.SHAPE_MISMATCH, TaperWarning.BAD_ANGLE, TaperWarning.CONTRADICTION, TaperWarning.ARC_R_TOO_SMALL, TaperWarning.ARC_NO_TANGENT)
            if (kGrid && res.warnings.any { it in never }) t.fail("STOP_UNEXPECTED_$w") { desc(full, blank) }
            // 같은 점을 네 좌표로 넣어도 같은 멈춤
            if (again.warnings != res.warnings) t.fail("STOP_DIFFERS_FROM_FULL") { "${desc(full, blank)}\n빈 칸 ${res.warnings} · 네 좌표 ${again.warnings} 풀림 ${again.solution != null}" }
            // 접한 끝에만 모서리 R 이 있는데 CORNER_R_TOO_BIG 면 그 끝의 모서리 R 을 빼지 않은 것
            val tanField = if (end != full.flip) 0 else 1
            if (TaperWarning.CORNER_R_TOO_BIG in res.warnings && full.corners[1 - tanField] == 0.0) {
                t.fail("CORNER_AT_TANGENT_END_STOPS") { desc(full, blank) }
            }
            return
        }
        t.note("풀림")
        // 채운 값 = 따로 유도한 값
        val p = s.points.map { it.vertex }
        val scale = max(1.0, full.arcR!!)
        val ferr = listOf(abs(p[0].xDia - full.x[0]!!), abs(p[0].z - full.z[0]!!), abs(p[1].xDia - full.x[1]!!), abs(p[1].z - full.z[1]!!)).max()
        t.fillErr = max(t.fillErr, ferr / scale)
        if (ferr > 1e-9 * scale) t.fail("FILL_VALUE") { "${desc(full, blank)}\n풀린 T1 ${xz(p[0])} T2 ${xz(p[1])} · 오차 $ferr" }
        if (again.warnings != res.warnings || again.solution?.ncG41G42 != s.ncG41G42 || again.solution?.ncG40 != s.ncG40) {
            t.fail("DIFFERS_FROM_FULL") { "${desc(full, blank)}\n빈 칸 ${res.warnings}\n${s.ncG41G42.joinToString(" ")}\n네 좌표 ${again.warnings}\n${again.solution?.ncG41G42?.joinToString(" ")}" }
        }
        // 호 중심 = 접한 끝 P + 그 요소 법선(다른 점 쪽) × R (화면 좌표 — 거울이면 Z 부호만)
        val tanField = if (end != full.flip) 0 else 1
        val pv = p[tanField]
        val qv = p[1 - tanField]
        val vertical = verticalAt(full.type, end)
        val rr = full.arcR!!
        val cWant = if (vertical) Pt(pv.z + (if (qv.z > pv.z) rr else -rr), pv.x) else Pt(pv.z, pv.x + (if (qv.x > pv.x) rr else -rr))
        val sa = s.arc
        if (sa == null) {
            t.fail("NOARC") { desc(full, blank) }
            return
        }
        val cerr = (sa.center - cWant).length
        t.centerErr = max(t.centerErr, cerr / scale)
        if (cerr > 1e-7 * scale) t.fail("TANGENT_CENTER") { "${desc(full, blank)}\n중심 ${xz(sa.center)} ≠ 접하는 자리 ${xz(cWant)} (${if (vertical) "단면" else "수평"} 끝 ${xz(pv)})" }
        // 접한 끝: 모서리 R 없이(넣었어도) 호와 그 요소가 꺾임 없이 이어진다
        val d = s.drawing.segs
        val ai = sa.index
        val a = d[ai] as Arc
        val atStart = (a.start - pv).length < 1e-9
        val atEnd = (a.end - pv).length < 1e-9
        if (!atStart && !atEnd) {
            t.fail("TANGENT_END_TRIMMED" + (if (full.corners[tanField] > 0) "_모서리R" else "")) { "${desc(full, blank)}\n호 ${a} · 접한 끝 ${xz(pv)}" }
        } else {
            val nb = d.getOrNull(if (atStart) ai - 1 else ai + 1)
            if (nb !is Line) t.fail("TANGENT_NEIGHBOR_NOT_LEG") { "${desc(full, blank)}\n${d.joinToString("\n")}" } else {
                val t1 = if (atStart) tanAt(nb, true) else tanAt(a, true)
                val t2 = if (atStart) tanAt(a, false) else tanAt(nb, false)
                val k = abs(t1 cross t2)
                t.drawKink = max(t.drawKink, k)
                if (k > 1e-7 || (t1 dot t2) <= 0) t.fail("TANGENT_KINK_DRAWING") { "${desc(full, blank)}\n외적 $k 내적 ${t1 dot t2}" }
            }
            if (s.points[tanField].corner != null) t.fail("CORNER_AT_TANGENT_END") { "${desc(full, blank)}\n${s.points[tanField].corner}" }
            if (full.corners[tanField] > 0) t.note("접한 끝 모서리 R 뺌")
        }
        val e = expect(full.copy(x = listOf(p[0].xDia, p[1].xDia), z = listOf(p[0].z, p[1].z)))
        drawingError(full, s, e)?.let { msg ->
            // 오목 모서리 R · 호가 노즈R 이하라(CONCAVE_R_LE_NOSE 경고) 호의 노즈 길이 이웃 교점에 다 먹혀 합성 R 이 없는 것은 따로 세고 시뮬은 돌린다
            if (msg.startsWith("PROGR") && TaperWarning.CONCAVE_R_LE_NOSE in res.warnings) {
                t.fail("DRAW_PROGR(경고 있음 — 호의 노즈 길이 사라짐)") { "${desc(full, blank)}\n$msg" }
            } else if (msg.startsWith("PROGR") && TaperWarning.NOSE_GT_SLOPE in res.warnings) {
                // 호의 노즈 길이 0(넣은 반경 차 = 노즈R 인 오목 호가 단면에 접한 꼴 — 보정선이 보정 원 중심을 지나 교점이 접한 끝의 법선 자리).
                // 2026-11-14 검증에서 이 꼴의 두 글은 시뮬레이터에서 멈춤 · 파고듦 · 덜 깎임이 없었고, 경고가 없던 것을 고침 단계(F4)가 NOSE_GT_SLOPE 로 알린다 — 시뮬은 아래에서 같은 눈으로
                t.note("호의 노즈 길 0 — NOSE_GT_SLOPE 경고 있음(고침 단계 F4)")
            } else if (msg.startsWith("PROGR")) {
                t.fail("DRAW_PROGR_경고없이_노즈길0") { "${desc(full, blank)}\n경고 ${res.warnings}\n$msg\n${s.drawing.segs.joinToString("\n")}\n${s.ncG41G42.joinToString(" ")}\n${s.ncG40.joinToString(" ")}" }
            } else {
                t.fail("DRAW_" + msg.substringBefore(' ')) { "${desc(full, blank)}\n경고 ${res.warnings}\n$msg\n${s.drawing.segs.joinToString("\n")}" }
                return
            }
        }
        // 글의 그 이음(접한 끝의 요소를 깎을 때만 — 안 깎으면 이웃 줄이 진입 · 빼는 줄)
        val (fv, bv) = legsVertical(full.kind, full.type, full.flip)
        val legCut = if (tanField == 0) full.start != null else full.end != null
        if (legCut) {
            val ncM = s.ncG41G42.let { nc -> nc.subList(1, max(1, nc.indexOf("G40"))) }
            ncKink(ncM, rr, sa.center, atStart)?.let { k ->
                t.ncKinkM = max(t.ncKinkM, k)
                if (k > 0.01) t.fail("M_NC_KINK") { "${desc(full, blank)}\n꺾임 $k rad\n${s.ncG41G42.joinToString("\n")}" }
            } ?: t.note("기계 보정 글 이음 못 잼")
            val want = if (full.arcConcave) rr - full.noseR else rr + full.noseR
            if (want < 0.1) t.note("직접 보정 글 이음 안 잼(합성 R < 0.1 — 반올림)")
            else ncKink(s.ncG40, want, sa.center + tipVec(s.tip, full.noseR), atStart)?.let { k ->
                t.ncKinkD = max(t.ncKinkD, k)
                if (k > 0.01) t.fail("D_NC_KINK") { "${desc(full, blank)}\n꺾임 $k rad\n${s.ncG40.joinToString("\n")}" }
            } ?: t.note("직접 보정 글 이음 못 잼")
        }
        if (fv || bv) t.note("단면 요소 있음")
        if (full.tip == null) {
            val std = if (full.kind.inner) (if (full.reverse) 1 else 2) else (if (full.reverse) 4 else 3)
            if (s.tip != std) t.fail("TIP") { "${desc(full, blank)}\n표준 팁 ${s.tip} ≠ $std" }
        }
        val w = res.warnings.toSet()
        res.warnings.forEach { t.note("경고:$it") }
        val r = full.noseR
        val step = sqrt(2 * r * 1e-3)
        val near = s.drawing.segs.flatMapIndexed { i, sg -> sample(sg, i, step) }
        val surf = if (full.kind.inner) near + near.map { Sm(Pt(it.p.z, -it.p.x), it.seg, it.s, it.len) } else near
        val must = mustCut(s.drawing.segs, e.side, r, step)
        machine(full, blank, s, e, w, surf, must, t, forms)
        direct(full, blank, s, e, w, surf, must, t)
    }

    private fun machine(f: SlopeForm, blank: Int, s: TaperSolution, e: Exp, w: Set<TaperWarning>, surf: List<Sm>, must: List<Pt>, t: Tally, forms: List<CompStartup>) {
        val nc = s.ncG41G42
        val r = f.noseR
        val text = { "${desc(f, blank)}\n경고 $w\n${nc.joinToString("\n")}" }
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = nc.indexOfFirst { it.contains("F.3") }
        val i40 = nc.indexOf("G40")
        // 회사 흐름: G42/G41 혼자 줄 → G0X#Z20. → … → G1 … F.1 → … 빼는 줄 F.3 → G40 혼자 줄 → G0Z200.
        val frame = nc.size >= 6 && nc[0] in listOf("G41", "G42") && Regex("""G0X-?[\d.]+Z20\.""").matches(nc[1]) && iCut in 2 until iExit &&
            nc.count { it.contains("G40") } == 1 && i40 == nc.size - 2 && i40 > iExit && nc.last() == "G0Z200."
        if (!frame) {
            t.fail("M_FRAME") { text() }
            return
        }
        if (nc[2].startsWith("Z")) t.note("흐름 G0X#Z20. → Z#") else t.note("흐름 G0X#Z20. → ${nc[2].take(1)}… (바깥에서 X 맞춤 등)")
        nc.firstNotNullOfOrNull(::badWord)?.let { bad -> t.fail("M_FORMAT") { "낱말 $bad\n${text()}" } }
        if (nc[0] != e.side) t.fail("M_SIDE") { "기대 ${e.side}\n${text()}" }
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
                val simLimit = stop.code == IssueCode.COMP_ACUTE_OUTER && s.drawing.segs.zipWithNext().any { (p, q) ->
                    val t1 = tanAt(p, true)
                    val t2 = tanAt(q, false)
                    abs(t1 cross t2) > 1e-3 && !concaveSharp(p, q, e.side) && (t1 dot t2) < 2e-4
                }
                val warned = warnedStop || (stop.code == IssueCode.COMP_INTERFERENCE && warnedAir)
                val edge = boundaryAt(f, s, e.side)
                t.fail("M_STOP_${stop.code}" + (if (warned) "(경고 있음)" else if (simLimit) "(시뮬 한계 — 바깥 90° 꺾임)" else if (edge != null) "_오목호_볼록꺾임_경계" else "")) {
                    (edge?.let { "경계: $it\n" } ?: "") + "형식 $form · ${stop.line - pre.size}줄 '${nc.getOrNull(stop.line - pre.size - 1)}'\n${text()}"
                }
                return
            }
            val moves = run.steps.filterIsInstance<MoveStep>()
            val cut = moves.filter { it.line in cutLines && it.kind == MoveKind.FEED }
            val p = probe(moves, cut, surf, must, r)
            if (p.gouge > tol) {
                val m = p.gougeBy!!
                val opposite = f.kind.inner && (p.gougeAt?.x ?: 0.0) < 0
                val kind = if (m.line in cutLines && !opposite) "M_GOUGE_CUT" else "M_GOUGE_AIR" + (if (opposite) "_OPPOSITE" else "") + (if (warnedAir) "(경고 있음)" else "")
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
            val arcs = moves.filter { it.line in cutLines }.mapNotNull { it.cmd as? Arc }
            val main = arcs.filter { abs(it.radius - rr) < 2e-3 && (it.center - e.c).length < 3e-3 * (1 + 2 * rr / max(1e-3, (it.end - it.start).length)) }
            if (main.isEmpty()) {
                t.fail(
                    "M_NOARC" + when {
                        TaperWarning.SHORT_END_SKIPPED in w || TaperWarning.SHORT_END_PAST in w -> "(짧은 끝)"
                        TaperWarning.CONCAVE_R_LE_NOSE in w -> "(오목 R ≤ 노즈 경고 — 짧은 호 반올림)"
                        else -> ""
                    },
                ) { "형식 $form · 기대 중심 ${xz(e.c)} R$rr\n${text()}" }
            } else if (main.any { it.cw != e.cw }) {
                t.fail("M_ARCDIR") { "형식 $form · 기대 ${if (e.cw) "G2" else "G3"}\n${text()}" }
            }
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

    private fun direct(f: SlopeForm, blank: Int, s: TaperSolution, e: Exp, w: Set<TaperWarning>, surf: List<Sm>, must: List<Pt>, t: Tally) {
        val nc = s.ncG40
        val r = f.noseR
        val text = { "${desc(f, blank)}\n경고 $w\n${nc.joinToString("\n")}" }
        nc.firstNotNullOfOrNull(::badWord)?.let { bad -> t.fail("D_FORMAT") { "낱말 $bad\n${text()}" } }
        t.runs++
        val run = sim(nc, r, s.tip, CompStartup.C, feed = true)
        val stop = run.stop
        if (stop != null) {
            val overMax = stop.code == IssueCode.NUMBER_TOO_BIG && (if (f.arcConcave) f.arcR!! - r else f.arcR!! + r) > 10_000.0
            t.fail("D_STOP_${stop.code}" + (if (overMax) "(합성 R 이 MAX_MM 넘음 — 기존 · 네 좌표도 같음)" else "")) { "${stop.line - pre.size}줄\n${text()}" }
            return
        }
        val moves = run.steps.filterIsInstance<MoveStep>()
        val tv = tipVec(s.tip, r)
        if (moves.any { (it.tip.end - it.nose.end - tv).length > 1e-9 }) {
            t.fail("D_TIPVEC") { text() }
            return
        }
        val cut = moves.filter { it.kind == MoveKind.FEED }
        val firstMove = pre.size + nc.indexOfFirst { !it.startsWith("(") } + 1
        val p = probe(moves.filter { it.line > firstMove }, cut, surf, must, r)
        if (p.gouge > tol) {
            val m = p.gougeBy!!
            // 축 너머 경고(ENTRY_PAST_AXIS)가 있어도 파고듦은 실패로 센다(이름에만 붙인다)
            t.fail((if (m.kind == MoveKind.FEED) "D_GOUGE_FEED" else "D_GOUGE_RAPID") + (if (TaperWarning.ENTRY_PAST_AXIS in w) "_축너머경고있음" else "")) {
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
        val left = s.arc?.let { length(s.drawing.segs[it.index]) } ?: 0.0
        val trimmed = f.arcConcave && left < 2 * r
        if (main.isEmpty() && left >= 0.01 && !trimmed) t.fail("D_NOARC") { "기대 중심 ${xz(e.c + tv)} R$want\n${text()}" }
        else if (main.any { it.cw != e.cw }) t.fail("D_ARCDIR") { "기대 ${if (e.cw) "G2" else "G3"}\n${text()}" }
    }

    private fun finish(t: Tally, title: String, file: String) {
        val text = t.report(title)
        File("build/verify1114arcauto").mkdirs()
        File("build/verify1114arcauto/$file").writeText(text)
        println(text.lineSequence().take(80).joinToString("\n"))
        val hard = t.count.filterKeys { !it.contains("(") }
        assertTrue(hard.isEmpty(), "$title: 실패 갈래 $hard — build/verify1114arcauto/$file")
    }

    // ================================================================ 격자

    /** 그림(거울 전) Δz · Δr 바탕 — 빈 칸이 Z 면 Δr 이, X 면 Δz 가 넣은 차 */
    private val geos = listOf(5.0 to 5.0, 8.0 to 3.0, 3.0 to 8.0, 0.6 to 0.4)

    /**
     * 외경 · 내경 × 형상 1~5 × 볼록 · 오목 × 뒤집기 × 화면 역방향 × 빈 칸 넷 × 형상 치수 넷 × R(넣은 차의 1 · 1.01 · 1.5 · 3 · 10 배) × 두 벌.
     * 벌마다 노즈R 0.2/0.4/0.8/1.2 · Z 여유 0/1/5 · 팁(표준 · 가끔 1~8) · 모서리 R 0/0.5(작은 치수 0.1) 양 끝 · 시작 · 끝 비움/3 mm 를 씨앗 고정으로 고른다
     */
    @Test
    fun grid() {
        val t = Tally()
        val rnd = Random(20261114)
        var n = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (convex in listOf(true, false)) for (flip in listOf(false, true)) for (reverse in listOf(false, true))
            for (blank in 0..3) for ((gi, geo) in geos.withIndex()) for (k in listOf(1.0, 1.01, 1.5, 3.0, 10.0)) repeat(2) {
                val blankIsZ = blank % 2 == 1
                val given = if (blankIsZ) geo.second else geo.first
                val rr = k * given
                val blankAtFigT1 = (blank / 2 == 0) != flip
                val end = tangentEnd(inner, type, convex, blankAtFigT1)
                // 접할 끝이 없으면 바탕 치수 그대로(어차피 멈춤)
                val other = if (end == null) (if (blankIsZ) geo.first else geo.second) else otherDelta(verticalAt(type, end), blankIsZ, given, rr)
                val dz = if (blankIsZ) other else given
                val dr = if (blankIsZ) given else other
                val noseR = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
                val zc = listOf(0.0, 1.0, 5.0)[rnd.nextInt(3)]
                val tip = if (rnd.nextInt(7) == 0) 1 + rnd.nextInt(8) else null
                val c = if (gi == 3) 0.1 else 0.5
                val crR = if (rnd.nextBoolean()) c else 0.0
                val crL = if (rnd.nextBoolean()) c else 0.0
                val legR = if (rnd.nextBoolean()) 3.0 else null
                val legL = if (rnd.nextBoolean()) 3.0 else null
                val full = form(inner, type, reverse, flip, convex, noseR, tip, dz, dr, rr, crR, crL, legR, legL, zc)
                n++
                if (dz < 1e-6 || dr < 1e-6) t.note("푼 차가 아주 작음(${"%.1e".format(min(dz, dr))})")
                checkCase(full, blank, t, if (n % 4 == 0) listOf(CompStartup.C, CompStartup.AB) else listOf(CompStartup.C), given)
            }
        t.note("사례 수 = $n")
        finish(t, "격자(빈 좌표 하나 · 접하는 자리)", "grid.txt")
    }

    /**
     * 끝 꼴: 1/4 원(R = 넣은 차) · 받는 띠(넣은 차가 R 보다 0.001 까지 큼은 네 좌표처럼 받고 0.01 · 0.1 큼은 ARC_R_TOO_SMALL — 고침 단계 R-VR3) · 아주 큰 R(1000 · 9999 · 10000, 10000.001 은 BAD_INPUT) ·
     * 푼 지름이 0 이하 · ±MAX_MM 밖 — 던지지 않고, 멈추면 경고 하나 + 한글 글, 풀리면 위와 같은 눈으로
     */
    @Test
    fun edges() {
        val t = Tally()
        val bad = mutableListOf<String>()
        fun hangul(s: String) = s.any { it in '가'..'힣' }
        fun stopOk(f: SlopeForm, what: String, want: TaperWarning?) {
            val res = try {
                TaperSolve.solve(f)
            } catch (e: Throwable) {
                bad += "던짐 $what ${desc(f)} → $e"
                return
            }
            if (want != null && res.warnings != listOf(want)) bad += "$what: 기대 $want · 실제 ${res.warnings} 풀림 ${res.solution != null} — ${desc(f)}"
            if (res.solution == null) {
                if (res.warnings.size != 1) bad += "$what: 멈췄는데 경고 ${res.warnings}"
                for (w in res.warnings) {
                    val text = taperWarningText(w, f)
                    if (!hangul(text)) bad += "$what: 경고 글이 한글이 아님 $w '$text'"
                    taperWarningFields(w, f)
                }
            }
        }
        for (inner in listOf(false, true)) for (type in 1..5) for (convex in listOf(true, false)) for (flip in listOf(false, true)) for (reverse in listOf(false, true))
            for (blank in 0..3) {
                val blankIsZ = blank % 2 == 1
                val blankAtFigT1 = (blank / 2 == 0) != flip
                val end = tangentEnd(inner, type, convex, blankAtFigT1) ?: continue
                val vertical = verticalAt(type, end)
                // 1/4 원: R = 넣은 차 = 5 → 빈 차도 5
                for (noseR in listOf(0.4, 1.2)) {
                    val full = form(inner, type, reverse, flip, convex, noseR, null, 5.0, 5.0, 5.0, 0.5, 0.5, 3.0, 3.0, 1.0)
                    checkCase(full, blank, t, listOf(CompStartup.C), 5.0, kGrid = false)
                }
                // 받는 띠: 넣은 차 = R + ε. 고침 단계(R-VR3)부터 R 로 잘라 1/4 원으로 풀고 부푼 깊이(≈ ε² / 2R)를 네 좌표처럼 ARC_OVER_TOL(5e-6)로 본다 —
                // R5 면 ε 0.001 까지 받고(네 좌표와 같은 경고 · 글) 0.01 · 0.1 은 ARC_R_TOO_SMALL. 꺾임 · 중심 오차가 ε 쯤이라 1e-7 까지만 checkCase 의 접함 눈으로
                for (eps in listOf(5e-10, 2e-9, 1e-7, 1e-6, 1e-3, 1e-2, 1e-1)) {
                    val rr = 5.0
                    val g = rr + eps
                    val other = otherDelta(vertical, blankIsZ, g, rr)
                    val dz = if (blankIsZ) other else g
                    val dr = if (blankIsZ) g else other
                    val full = form(inner, type, reverse, flip, convex, 0.4, null, dz, dr, rr, 0.0, 0.0, 3.0, 3.0, 1.0)
                    val f = blanked(full, blank)
                    if (eps <= 1e-3) {
                        val res = TaperSolve.solve(f)
                        val four = TaperSolve.solve(full)
                        if (res.warnings != four.warnings || res.solution?.ncG41G42 != four.solution?.ncG41G42 || res.solution?.ncG40 != four.solution?.ncG40) {
                            bad += "받는 띠 ε $eps: 빈 칸 ${res.warnings} ≠ 네 좌표 ${four.warnings} — ${desc(full, blank)}"
                        }
                        if (res.solution == null && res.warnings != listOf(TaperWarning.NOSE_UNREACHABLE)) bad += "받는 띠 ε $eps 가 멈춤 ${res.warnings} — ${desc(full, blank)}"
                        if (res.solution != null && eps <= 1e-7) checkCase(full, blank, t, listOf(CompStartup.C), g, kGrid = false)
                    } else {
                        stopOk(f, "받는 띠 밖 ε $eps", TaperWarning.ARC_R_TOO_SMALL)
                        val text = taperWarningText(TaperWarning.ARC_R_TOO_SMALL, f)
                        val gapText = if (blankIsZ) "반경 차" else "Z 거리"
                        if (gapText !in text) bad += "R 과소 글에 '$gapText' 없음: $text"
                    }
                }
                // 아주 큰 R(넣은 차 0.05 · 0.5 · 5)
                for (rr in listOf(1000.0, 9999.0, 10000.0)) for (g in listOf(0.05, 0.5, 5.0)) {
                    val other = otherDelta(vertical, blankIsZ, g, rr)
                    val dz = if (blankIsZ) other else g
                    val dr = if (blankIsZ) g else other
                    val full = form(inner, type, reverse, flip, convex, 0.4, null, dz, dr, rr, 0.0, 0.0, 3.0, 3.0, 1.0)
                    val f = blanked(full, blank)
                    val res = try {
                        TaperSolve.solve(f)
                    } catch (e: Throwable) {
                        bad += "던짐 큰 R ${desc(full, blank)} → $e"
                        continue
                    }
                    if (res.solution != null) {
                        // 푼 차가 아주 작아 두 점이 0.001 안이면 그 칸이 넣은 값과 같게 보인다 — 따로 센다
                        if (other < 5e-4) t.note("큰 R: 푼 차 < 0.0005 (${if (blankIsZ) "Z" else "X"}) 풀림")
                        if (other > 1e-6 && dz < 900 && dr < 900) checkCase(full, blank, t, listOf(CompStartup.C), g, kGrid = false)
                    } else {
                        t.note("큰 R 멈춤 ${res.warnings} · 푼 차 ${"%.1e".format(other)}")
                        if (res.warnings.size != 1) bad += "큰 R 멈춤 경고 ${res.warnings}"
                        // 푼 차가 EPS(1e-9) 위면 풀려야 한다 — 멈추면 이유를 적는다
                        if (other > 1e-6 && res.warnings.any { it == TaperWarning.ARC_R_TOO_SMALL || it == TaperWarning.ARC_NO_TANGENT || it == TaperWarning.MISSING_INPUT }) {
                            bad += "큰 R 이 ${res.warnings} 로 멈춤(푼 차 $other) — ${desc(full, blank)}"
                        }
                    }
                }
                stopOk(blanked(form(inner, type, reverse, flip, convex, 0.4, null, 5.0, 5.0, 10000.001, 0.0, 0.0, null, null, 1.0), blank), "R 10000.001", TaperWarning.BAD_INPUT)
                // 푼 지름이 0 이하: 빈 칸이 X 이고 넣은 다른 X 가 작다(외경 형상 1~4 의 X1 · 내경 형상 5 의 X1 — up > 0 이면 T1 이 작은 쪽)
                if (!blankIsZ) {
                    val up = if (inner == (type == 5)) 1.0 else -1.0
                    val smallIsBlank = (up > 0) == blankAtFigT1
                    if (smallIsBlank) {
                        val other = otherDelta(vertical, false, 5.0, 5.0)
                        // 큰 쪽 지름을 2·other − 1 로 — 작은 쪽이 −1 (또 꼭 0)
                        for (big in listOf(2 * other - 1, 2 * other)) {
                            val full = form(inner, type, reverse, flip, convex, 0.4, null, 5.0, other, 5.0, 0.0, 0.0, null, null, 1.0, x0 = big - 2 * other)
                            stopOk(blanked(full, blank), "푼 지름 ${big - 2 * other}", TaperWarning.BAD_INPUT)
                        }
                    }
                }
                // ±MAX_MM 밖: 넣은 점을 9999 근처에 두고 빈 칸이 그 너머로
                run {
                    val other = otherDelta(vertical, blankIsZ, 5.0, 8.0)
                    val dz = if (blankIsZ) other else 5.0
                    val dr = if (blankIsZ) 5.0 else other
                    val f0 = form(inner, type, reverse, flip, convex, 0.4, null, dz, dr, 8.0, 0.0, 0.0, null, null, 1.0)
                    // Z 를 −9998 쪽으로 옮긴다(거울이면 +): 빈 칸이 Z 면 푼 Z 가 ±10000 을 넘을 수 있다
                    val sh = if (reverse) 9996.0 else -9996.0
                    val far = f0.copy(z = f0.z.map { it!! + sh })
                    stopOk(blanked(far, blank), "MAX_MM 둘레", null)
                }
            }
        File("build/verify1114arcauto").mkdirs()
        File("build/verify1114arcauto/edges-bad.txt").writeText(bad.joinToString("\n"))
        finish(t, "끝 꼴(1/4 원 · 받는 띠 · 큰 R)", "edges.txt")
        assertEquals(emptyList(), bad.take(20), "문제 ${bad.size} 건 — build/verify1114arcauto/edges-bad.txt")
    }

    /** 사용자 화면 그대로(내경 형상 3 · 뒤집기 · X1 6.3 · Z1 빈칸 · X2 7.5 · Z2 −10.1 · 오목 R3.75 · 시작 · 끝 빈칸)의 두 글을 시뮬레이터로 · 노즈R 넷 · Z 여유 셋 · 형식 C · A/B */
    @Test
    fun userCase() {
        val t = Tally()
        val bad = mutableListOf<String>()
        for (noseR in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0)) for (reverse in listOf(false, true)) for (legs in 0..3) {
            // 화면 역방향은 그림을 Z −15 둘레로 거울(화면 Z 는 늘 척 쪽이 −): z → −30 − z
            fun mz(z: Double) = if (reverse) -30.0 - z else z
            val dz = sqrt(0.6 * 6.9)
            val start = if (legs and 1 != 0) mz(-10.1 - dz - 3.0) else null
            val end = if (legs and 2 != 0) mz(-5.0) else null
            val full = SlopeForm(
                kind = TaperKind.INNER, type = 3, reverse = reverse, noseR = noseR, tip = null, x = listOf(6.3, 7.5), z = listOf(mz(-10.1 - dz), mz(-10.1)),
                angle = null, zClear = zc, corners = listOf(0.0, 0.0), start = start, end = end, flip = true, angleRef = AngleRef.X, arc = true, arcR = 3.75, arcConcave = true,
            )
            val res = TaperSolve.solve(blanked(full, 1))
            val s = res.solution
            if (s == null) {
                bad += "사용자 화면이 멈춤 ${res.warnings} — ${desc(full, 1)}"
                continue
            }
            if (abs(s.points[0].vertex.z - mz(-10.1 - dz)) > 1e-9) bad += "Z1 ${s.points[0].vertex.z} ≠ ${mz(-10.1 - dz)}"
            if (Fmt3(s.points[0].vertex.z) != (if (reverse) "-17.865" else "-12.135")) bad += "Z1 셋째 자리 ${Fmt3(s.points[0].vertex.z)}"
            checkCase(full, 1, t, listOf(CompStartup.C, CompStartup.AB), 0.6, kGrid = false)
        }
        File("build/verify1114arcauto").mkdirs()
        finish(t, "사용자 화면", "user.txt")
        assertEquals(emptyList(), bad)
    }

    private fun Fmt3(v: Double) = com.bmt.nosecalc.calc.Fmt.fixed3(v)

    /**
     * 오목 T1–T2 호가 볼록 꼭짓점(바깥 꺾임 β)에서 다음 요소와 만나고 노즈R 이 '못 받는 꼴' 경계(R = 2r / (1 + cos β) — calc-design.md '못 받는 꼴')에
     * 1e-4 안으로 붙었나. 붙었으면 설명, 아니면 null. 이 경계에서는 늘인 교점이 보정 원의 꼭대기(겹근)라 NC 0.001 반올림이 교점을 없앤다
     */
    private fun boundaryAt(f: SlopeForm, s: TaperSolution, side: String): String? {
        if (!f.arcConcave) return null
        val sa = s.arc ?: return null
        val d = s.drawing.segs
        val a = d[sa.index] as? Arc ?: return null
        val r = f.noseR
        for ((p, q) in listOfNotNull(d.getOrNull(sa.index - 1)?.let { it to a }, d.getOrNull(sa.index + 1)?.let { a to it })) {
            val t1 = tanAt(p, true)
            val t2 = tanAt(q, false)
            if (abs(t1 cross t2) < 1e-3 || concaveSharp(p, q, side)) continue
            val cosB = (t1 dot t2).coerceIn(-1.0, 1.0)
            val need = 2 * r / (1 + cosB)
            if (abs(need - a.radius) <= 1e-4 * max(1.0, a.radius)) {
                return "오목 R ${a.radius} · 바깥 꺾임 ${"%.3f".format(Math.toDegrees(Math.acos(cosB)))}° · 2r/(1+cosβ) = $need"
            }
        }
        return null
    }

    /**
     * 위 경계 둘레를 노즈R 로 훑는다(알림만 — build/verify1114arcauto/boundary.txt): 외경 형상 3 · X1 30 Z1 −2 · X2 30.8 Z2 빈칸 · 오목 R0.6(경계 노즈 0.4),
     * 같은 꼴 X2 31.6 · R0.8(1/4 원 · 경계 노즈 0.4), 내경 형상 3 · X1 30.8 Z1 −2 · X2 30 Z2 빈칸 · 오목 R1 · (경계 노즈 0.8). 시작 · 끝 비움 / 넣음, 형식 C · A/B
     */
    @Test
    fun boundaryProbe() {
        val out = StringBuilder()
        val shapes = listOf(
            Triple(false, listOf(30.0, 30.8), 0.6),
            Triple(false, listOf(30.0, 31.6), 0.8),
            Triple(true, listOf(30.8, 30.0), 1.0),
        )
        for ((inner, xs, rr) in shapes) for (noseR in listOf(0.3, 0.39, 0.399, 0.4, 0.401, 0.41, 0.45, 0.5, 0.55, 0.59, 0.79, 0.8, 0.81)) for (legs in 0..3) {
            if (noseR >= rr) continue
            val dr = abs(xs[1] - xs[0]) / 2
            val dz = sqrt(rr * rr - (rr - dr) * (rr - dr))
            val start = if (legs and 1 != 0) 1.0 else null
            val end = if (legs and 2 != 0) -2.0 - dz - 3.0 else null
            val full = SlopeForm(
                kind = if (inner) TaperKind.INNER else TaperKind.OUTER, type = 3, reverse = false, noseR = noseR, tip = null, x = xs, z = listOf(-2.0, -2.0 - dz),
                angle = null, zClear = 1.0, corners = listOf(0.0, 0.0), start = start, end = end, flip = false, angleRef = AngleRef.X, arc = true, arcR = rr, arcConcave = true,
            )
            val res = TaperSolve.solve(blanked(full, 3))
            val s = res.solution
            val line = StringBuilder("${if (inner) "내경" else "외경"} X${xs[0]}→${xs[1]} R$rr 노즈 $noseR 시작 $start 끝 $end: ")
            if (s == null) line.append("멈춤 ${res.warnings}") else {
                val e = expect(full.copy(z = listOf(s.points[0].vertex.z, s.points[1].vertex.z)))
                val stops = listOf(CompStartup.C, CompStartup.AB).map { form -> sim(s.ncG41G42, noseR, s.tip, form).stop?.let { "${it.code}@${it.line - pre.size}" } ?: "돎" }
                line.append("풀림 Z2 ${Fmt3(s.points[1].vertex.z)} 경고 ${res.warnings} · 경계 ${boundaryAt(full, s, e.side) ?: "아님"} · 시뮬(C · A/B) $stops · ${s.ncG41G42.joinToString(" ")}")
            }
            out.appendLine(line)
        }
        File("build/verify1114arcauto").mkdirs()
        File("build/verify1114arcauto/boundary.txt").writeText(out.toString())
        println(out)
    }
}
