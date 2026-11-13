package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-05) — 짧은 막힌 끝 · 시작과 그 이웃(모서리 R · 주 원호 · 겹테이퍼 골), 진입 길 못 찾음(2026-11-04 고침)을 시뮬레이터 `GSim` 으로 깨뜨려 본다.
 * `VerifyCompanyFlowTest` · `CompanyFrameVerifyTest` 와 다른 생성기: 씨앗 515151 · 616161 · 717171 · 818181, 짧은 막힌 끝 · 시작(길이 0.05 ~ 2r)을 일부러 많이,
 * 이웃은 볼록 · 오목 모서리 R(r 보다 조금 큼 1.01 ~ 1.41r · 1.5 ~ 2.5r · 2 ~ 6 · 아주 큼 8 ~ 30) · 주 원호(라운드 모서리 R 0 — r 보다 조금 큰 공도) ·
 * 겹테이퍼 골과 산, 뒤집기 · 계산기 역방향 · 화면 역방향, 노즈R 0.1 ~ 1.6(0.1 칸), Z 여유 0 ~ 5, 3점호(역방향 · 앞으로 부푼 것 포함).
 * 기하(점 · 선분 · 원호 정확한 거리, 닫은 도면 안팎)는 이 파일에서 따로 짰다 — 앱의 `CompFrame` · `NoseComp` 를 부르지 않는다.
 * 형식 A · B / C × 팁 0 ~ 9 와 급송 속도 비 둘(LRP=0 — 꺾이는 자리를 바꿈)로 돌려 본다:
 * 멈춤 · 모든 이동(진입 G0 · G40 옮김 · G0Z200. 포함)의 도면(축 거울 포함) 파먹음 · 노즈 중심이 닫은 도면(그린 구간의 소재) 속 · 노즈가 막힌 끝 · 시작 벽 너머 ·
 * 깎는 줄 노즈의 형식 · 팁 차이 · 경고가 덮지 않는 문제(벽 너머는 SHORT_END_PAST, 진입 G0 · 첫 깎는 줄은 ENTRY_NOT_FOUND, 빼는 줄 · G40 · 물러남은 EXIT_NOT_FOUND
 * (2026-11-05 더함 — 빼는 길을 규칙대로 못 짜면 앱이 알린다) 만 덮는다 — 나머지는 경고 없이 파는 글) ·
 * 진입 · 짧은 끝 경고의 헛경고. 짧은 끝 · 시작을 빼 벽까지 남는 노즈 거리(덜 깎음)는 출력만 한다(사용자 결정 대기).
 * 단언에서 빼는 꼬리표는 도면 알람(CONCAVE_R_LE_NOSE · NOSE_GT_SLOPE · 노즈 길이 사라진 요소 — 제어기가 도면 탓으로 멈춤)과 좁은 구멍(노즈에 비해 좁은 내경)뿐이고 이 둘도 센다.
 */
class VerifyShortNeighborTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99F.1")

    /** NC 0.001 반올림까지 받는 허용(mm) */
    private val tol = 1e-3

    /** 막힌 끝 · 시작 벽은 (그 좌표, 너머 방향 단위) — (q − 좌표)·방향 > 0 이 벽 속 */
    private class Case(
        val group: String, val tag: String, val warns: Set<String>, val nc: List<String>, val r: Double, val tip: Int,
        val drawing: List<Seg>, val inner: Boolean, val front: Double, val endWall: Pair<Pt, Pt>?, val startWall: Pair<Pt, Pt>?, val label: String,
    )

    private class Tally(val name: String) {
        var cases = 0
        var runs = 0
        var simLimit = 0
        val labels = linkedMapOf<String, Int>()
        val kinds = linkedMapOf<String, Int>()
        val first = linkedMapOf<String, String>()

        /** 경고가 덮지 않는 문제가 있는 사례(도면 알람 · 좁은 구멍 빼고) */
        var silent = 0
        var formDiff = 0
        var entryWarnBad = 0
        var entryWarnFalse = 0

        /** 경고가 떴는데 닫은 도면을 가릴 수 없어(닫는 선이 도면을 가로지름) 진입이 부품 속을 지나는지 판정하지 못한 것 */
        var entryWarnUndecided = 0
        var regionBad = 0
        var entrySilent = 0
        var shortWarnPast = 0
        var shortWarnFalse = 0

        /** EXIT_NOT_FOUND 가 떴는데 빼는 줄 · G40 · 물러남에 문제가 없는 사례(G40 자리 규칙은 팁 1~9 를 모두 덮는 넉넉한 규칙이라 출력만) · 덮은 사례 */
        var exitWarnFalse = 0
        var exitWarnBad = 0
        val uncut = linkedMapOf<String, MutableList<Double>>()
        val uncutWorst = linkedMapOf<String, Pair<Double, String>>()

        fun add(key: String, msg: String) {
            kinds[key] = (kinds[key] ?: 0) + 1
            if (key !in first) first[key] = msg
        }

        fun summary(): String =
            "$name: 사례 $cases · 경고가 덮지 않는 문제 $silent · 형식 · 팁 차이 $formDiff · 진입 헛경고 $entryWarnFalse · 진입 문제인데 경고 없음 $entrySilent"

        fun report(): String = buildString {
            appendLine("== $name: 사례 $cases · 실행 $runs · 꼬리표 $labels · 형상 줄 바깥 꺾임 멈춤(시뮬레이터 한계) $simLimit")
            appendLine("   경고가 덮지 않는 문제(도면 알람 · 좁은 구멍 빼고) $silent 사례 · 깎는 노즈 형식 · 팁 차이 $formDiff 사례")
            appendLine("   진입: ENTRY_NOT_FOUND 경고+진짜 문제 $entryWarnBad · 경고+문제 없음(헛경고) $entryWarnFalse · 경고+판정 못 함 $entryWarnUndecided · 경고 없이 진입 문제(도면 알람 빼고) $entrySilent")
            appendLine("   닫은 도면을 가릴 수 없어 속 검사를 못 한 사례 $regionBad")
            appendLine("   SHORT_END_PAST: 경고+벽 넘음 $shortWarnPast · 경고+안 넘음 $shortWarnFalse")
            appendLine("   EXIT_NOT_FOUND: 경고+빼는 길 문제 $exitWarnBad · 경고+문제 없음 $exitWarnFalse")
            for ((k, v) in uncut) appendLine("   벽에 못 닿음(덜 깎음) $k: ${v.size}개 · 최대 ${Fmt.nc(v.maxOrNull() ?: 0.0)} · 평균 ${Fmt.nc(v.average())}")
            for ((k, n) in kinds) appendLine("   $k × $n")
            for ((k, m) in first) appendLine("-- 첫 예 $k\n$m")
            for ((k, m) in uncutWorst) appendLine("-- 덜 깎음 최대 $k (${Fmt.nc(m.first)})\n${m.second}")
        }
    }

    // ================================================================ 따로 짠 기하

    private fun ang(v: Pt) = atan2(v.x, v.z)

    private fun mod2(a: Double): Double {
        val t = a % (2 * PI)
        return if (t < 0) t + 2 * PI else t
    }

    private fun rad(a: Arc) = (a.start - a.center).length

    private fun sweepOf(a: Arc): Double {
        if ((a.end - a.start).length < 1e-9) return 0.0
        val a0 = ang(a.start - a.center)
        val a1 = ang(a.end - a.center)
        return mod2(if (a.cw) a0 - a1 else a1 - a0)
    }

    /** 원호 중심에서 본 p 의 방향이 원호 안인가 */
    private fun onArc(a: Arc, p: Pt): Boolean {
        val a0 = ang(a.start - a.center)
        val ap = ang(p - a.center)
        return mod2(if (a.cw) a0 - ap else ap - a0) <= sweepOf(a) + 1e-12
    }

    private fun lenOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> rad(s) * sweepOf(s)
    }

    private fun unitOf(v: Pt) = v * (1 / v.length)

    private fun cross2(a: Pt, b: Pt) = a.z * b.x - a.x * b.z

    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    private fun dPt(s: Seg, p: Pt): Double = when (s) {
        is Line -> {
            val v = s.end - s.start
            val l2 = v dot v
            if (l2 < 1e-24) (p - s.start).length
            else (p - (s.start + v * (((p - s.start) dot v) / l2).coerceIn(0.0, 1.0))).length
        }
        is Arc -> {
            val d = (p - s.center).length
            if (d > 1e-12 && onArc(s, p)) abs(d - rad(s)) else min((p - s.start).length, (p - s.end).length)
        }
    }

    private fun hit(a: Line, b: Line): Boolean {
        val d1 = a.end - a.start
        val d2 = b.end - b.start
        val den = cross2(d1, d2)
        if (abs(den) < 1e-15) return false
        val w = b.start - a.start
        val t = cross2(w, d2) / den
        val u = cross2(w, d1) / den
        return t in 0.0..1.0 && u in 0.0..1.0
    }

    /** 선분 ~ 선분 · 원호 정확한 거리: 끝점끼리 · 만남(0) · 원호 위 수선 발 */
    private fun dLine(l: Line, s: Seg): Double {
        var best = minOf(dPt(s, l.start), dPt(s, l.end), dPt(l, s.start), dPt(l, s.end))
        when (s) {
            is Line -> if (hit(l, s)) return 0.0
            is Arc -> {
                val v = l.end - l.start
                val len = v.length
                if (len > 1e-12) {
                    val u = v * (1 / len)
                    val k = (s.center - l.start) dot u
                    val foot = l.start + u * k
                    val h = (s.center - foot).length
                    val rr = rad(s)
                    if (h <= rr) {
                        val w = sqrt(rr * rr - h * h)
                        for (t in listOf(k - w, k + w)) if (t in 0.0..len && onArc(s, l.start + u * t)) return 0.0
                    }
                    if (k in 0.0..len && h > 1e-12) {
                        val q = s.center + (foot - s.center) * (rr / h)
                        if (onArc(s, q)) best = min(best, abs(h - rr))
                    }
                }
            }
        }
        return best
    }

    /** 요소 위의 점들 — 직선은 [step] 마다, 원호는 활꼴 높이가 2e-4 mm 를 넘지 않게(그리고 [step] 마다) */
    private fun along(s: Seg, step: Double): List<Pt> = when (s) {
        is Line -> {
            val n = max(1, ceil((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val rr = rad(s)
            val sw = sweepOf(s) * (if (s.cw) -1 else 1)
            val a0 = ang(s.start - s.center)
            val dTheta = min(step / max(rr, 1e-9), 0.04)
            val n = max(2, ceil(abs(sw) / dTheta).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * rr }
        }
    }

    /** 노즈 길 ~ 소재 요소들 가장 짧은 거리(원호 ~ 원호만 0.01 mm 표본) */
    private fun gap(n: Seg, mats: List<Seg>): Double = mats.minOfOrNull { m ->
        when (n) {
            is Line -> dLine(n, m)
            is Arc -> if (m is Line) dLine(m, n) else min(along(n, 0.01).minOf { dPt(m, it) }, min(dPt(n, m.start), dPt(n, m.end)))
        }
    } ?: Double.POSITIVE_INFINITY

    /** 노즈 길 위 (q − p)·u 의 가장 큰 값 */
    private fun far(n: Seg, p: Pt, u: Pt): Double {
        var best = max((n.start - p) dot u, (n.end - p) dot u)
        if (n is Arc && onArc(n, n.center + u * rad(n))) best = max(best, ((n.center - p) dot u) + rad(n))
        return best
    }

    /**
     * 닫은 도면(그린 구간의 소재): 외경은 두 끝에서 축으로, 내경은 두 끝에서 바깥(도면 위 1000 mm — 벽은 바깥으로 이어진다)으로 닫는다(끝 너머는 모른다 — 닫는 법은 [closeAt]).
     * [bound] = 닫는 선(내경은 위 선도)과 그 축 거울 — 도면과 함께 소재 경계. 닫는 선이 도면을 가로지르면(Z 로 되돌아오는 윤곽) 안팎을 가릴 수 없어 [ok] = false
     */
    private class Region(val poly: List<Pt>, val bound: List<Line>, val ok: Boolean, val why: String)

    /** 요소 처음 · 끝의 진행 방향(원호는 접선 — 반시계면 반지름 방향을 +90°) */
    private fun dirAt(s: Seg, atEnd: Boolean): Pt = when (s) {
        is Line -> unitOf(s.end - s.start)
        is Arc -> unitOf((if (atEnd) s.end else s.start) - s.center).let { n -> if (s.cw) Pt(n.x, -n.z) else Pt(-n.x, n.z) }
    }

    /**
     * 끝을 닫는 점: 끝 접선이 그린 구간 쪽(Z 로 되돌아옴 — 언더컷)으로 꺾여 닫는 쪽(외경 축 · 내경 바깥)으로 X 성분 0.2(11.5°) 넘게 가면 그 접선을 따라
     * (사용자 결정 2026-10-29 ③ — 빈 끝은 끝 좌표 너머로 곧게 빼니 그 길은 공기로 본다), 아니면 곧장 X 로. [back] = 끝에서 그린 구간 쪽 Z 부호
     */
    private fun closeAt(p: Pt, u: Pt, target: Double, back: Double): Pt {
        val toward = if (target > p.x) 1.0 else -1.0
        return if (u.x * toward > 0.2 && u.z * back > 1e-9) p + u * ((target - p.x) / u.x) else Pt(p.z, target)
    }

    private fun region(d0: List<Seg>, inner: Boolean): Region {
        val tx = if (inner) d0.flatMap { along(it, 0.1) }.maxOf { it.x } + 1000.0 else 0.0
        // 끝 · 처음의 단면이 닫는 선 위에 놓이면(끝 단면이 닫는 쪽에서 멀어지거나 처음 단면이 닫는 쪽으로 감) 그 단면은 닫는 선의 일부 — 떼고 그 안쪽 점에서 닫는다
        fun onCloser(s: Seg, towardTarget: Boolean): Boolean {
            if (s !is Line || abs(s.end.z - s.start.z) > 1e-9) return false
            val down = s.end.x < s.start.x
            return if (inner) down != towardTarget else down == towardTarget
        }
        var i0 = 0
        var i1 = d0.size
        while (i1 - i0 > 1 && onCloser(d0[i1 - 1], false)) i1--
        while (i1 - i0 > 1 && onCloser(d0[i0], true)) i0++
        val d = d0.subList(i0, i1)
        val pts = mutableListOf<Pt>()
        for (s in d) pts += (if (s is Line) listOf(s.start, s.end) else along(s, 0.1)).dropLast(1)
        pts += d.last().end
        val a = d.last().end
        val b = d.first().start
        val ca = if (i1 < d0.size) Pt(a.z, tx) else closeAt(a, dirAt(d.last(), true), tx, if (b.z > a.z) 1.0 else -1.0)
        val cb = if (i0 > 0) Pt(b.z, tx) else closeAt(b, dirAt(d.first(), false) * -1.0, tx, if (a.z > b.z) 1.0 else -1.0)
        val closers = listOf(Line(a, ca), Line(cb, b)).filter { (it.end - it.start).length > 1e-9 }
        // 닫는 선 양 끝 1e-3 을 떼고 도면과 만나는지(거리 0) — 끝에서 접하는 원호(돔 끝 · 단면에 접한 모서리 R)는 만남이 아니다
        val shrunk = closers.filter { (it.end - it.start).length > 3e-3 }.map { l ->
            val u = unitOf(l.end - l.start)
            Line(l.start + u * 1e-3, l.end - u * 1e-3)
        }
        val cross = shrunk.firstNotNullOfOrNull { l -> d.indexOfFirst { dLine(l, it) == 0.0 }.takeIf { it >= 0 }?.let { "닫는 선 ${xz(l.start)} → ${xz(l.end)} 이 ${it + i0}번 요소에 닿음" } }
        val why = cross ?: if (shrunk.size == 2 && hit(shrunk[0], shrunk[1])) "두 닫는 선이 엇갈림" else ""
        val edges = closers + (if (inner) listOf(Line(ca, cb)) else emptyList())
        return Region(pts + ca + cb, edges + edges.map { mirror(it) as Line }, why.isEmpty(), why)
    }

    /** 점이 닫은 도면(또는 그 축 거울) 속인가 — 짝홀 */
    private fun inside(poly: List<Pt>, p: Pt): Boolean {
        val q = Pt(p.z, abs(p.x))
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.x > q.x) != (b.x > q.x) && q.z < a.z + (q.x - a.x) * (b.z - a.z) / (b.x - a.x)) c = !c
            j = i
        }
        return c
    }

    /**
     * 노즈 길 중심이 닫은 도면 속으로 들어간 깊이(속이 아니면 0)와, 가장 깊은 곳에서 가장 가까운 경계가 닫는 선인지(그린 도면이 아니라 끝 너머를 어림한 선).
     * 소재 경계(도면 · 축 거울 [mats] — 그 거리 [g] — 와 닫는 선)에 닿지 않는 길은 통째 속이거나 밖이라 한 점으로 가리고, 속이면(또는 닫는 선에 닿으면)
     * 0.05 mm 마다 점으로 깊이를 잰다
     */
    private fun insideDepth(n: Seg, reg: Region, mats: List<Seg>, g: Double): Pair<Double, Boolean> {
        val gb = min(g, gap(n, reg.bound))
        if (gb > 1e-6 && !inside(reg.poly, n.start)) return 0.0 to false
        var deep = 0.0
        var viaCloser = false
        for (q in along(n, 0.05)) if (inside(reg.poly, q)) {
            val dd = mats.minOf { dPt(it, q) }
            val db = reg.bound.minOf { dPt(it, q) }
            if (min(dd, db) > deep) {
                deep = min(dd, db)
                viaCloser = db < dd
            }
        }
        return deep to viaCloser
    }

    private fun same(a: Seg, b: Seg): Boolean =
        a::class == b::class && (a.start - b.start).length <= 1e-6 && (a.end - b.end).length <= 1e-6 &&
            (a !is Arc || (a.center - (b as Arc).center).length <= 1e-6)

    private fun xz(p: Pt) = "X${Fmt.nc(2 * p.x)} Z${Fmt.nc(p.z)}"

    private fun r3(v: Double) = Fmt.nc(v).toDouble()

    private fun sim(nc: List<String>, r: Double, tip: Int, form: CompStartup, front: Double, rx: Double = 20_000.0, rz: Double = 24_000.0): SimRun =
        GSim.run(
            SimInput(
                (pre + nc + "M30").joinToString("\n"),
                MachineSetup(rapidX = rx, rapidZ = rz, home = Pt(front * 100.0, 100.0), compStartup = form),
                mapOf(1 to ToolOffset(r, tip)),
            ),
        )

    /** 글 줄 번호(0부터)의 시뮬레이터 줄 번호 */
    private fun line(k: Int) = pre.size + k + 1

    // ================================================================ 사례 하나 보기

    private fun check(c: Case, t: Tally) {
        t.cases++
        t.labels[c.label] = (t.labels[c.label] ?: 0) + 1
        val f = c.nc
        val bad = linkedMapOf<String, String>()
        fun fail(kind: String, msg: String) {
            if (kind !in bad) bad[kind] = msg
        }
        val iCut = f.indexOfFirst { it.endsWith("F.1") }
        val iExit = f.indexOfFirst { it.contains("F.3") }
        val iG40 = f.indexOf("G40")
        if (f.firstOrNull() !in listOf("G41", "G42") || iCut < 2 || iExit <= iCut || iG40 < iExit || iG40 != f.size - 2 ||
            !f.last().startsWith("G0Z") || !f[1].startsWith("G0X") || !f[1].contains("Z")
        ) {
            fail("STRUCT", "회사 틀이 아님")
            close(c, t, bad, emptyList(), false)
            return
        }
        fun role(line: Int): String {
            val k = line - pre.size - 1
            return when {
                k < 0 -> "앞"
                k == 0 -> "보정 혼자 줄"
                k == 1 -> "Z20 줄"
                k < iCut -> "진입 G0"
                k == iCut -> "첫 깎는 줄"
                k < iExit -> "형상"
                k < iG40 -> "빼는 줄"
                k == iG40 -> "G40"
                else -> "물러남"
            }
        }
        val mats = c.drawing + c.drawing.map(::mirror)
        val reg = region(c.drawing, c.inner)
        if (!reg.ok) {
            t.regionBad++
            val shape = c.drawing.joinToString(" ") { s -> "${xz(s.start)}→${xz(s.end)}${if (s is Arc) "R" + Fmt.nc(rad(s)) else ""}" }
            t.add("닫은 도면 판정 못 함|${c.group}", "[${c.group} · ${c.label}] ${c.tag}\n  ${reg.why}\n  도면 $shape")
        }
        val cutLines = line(iCut)..line(iExit - 1)

        // 공구 교환 자리 · Z20. 줄(원점에서 오는 길) 뒤의 이동 하나
        fun look(m: MoveStep, where: String, pfx: String = "") {
            if (m.line <= line(1)) return
            val rl = role(m.line)
            val text = "${m.line - pre.size}줄 '${f.getOrNull(m.line - pre.size - 1) ?: "?"}' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)}"
            val g = gap(m.nose, mats)
            if (g < c.r - tol) {
                fail("${pfx}GOUGE@$rl", "$where: $text 이 도면을 ${Fmt.nc(c.r - g)} 팜")
            } else if (reg.ok) {
                val (depth, viaCloser) = insideDepth(m.nose, reg, mats, g)
                // 끝 너머를 어림한 닫는 선을 r 안쪽으로만 넘은 것(그림 5 mm 너머 단면 · 끝 접선 너머 — 모르는 곳)은 출력만
                val soft = viaCloser && depth <= c.r + tol
                if (depth > tol) fail("${if (soft) "SOFT_" else ""}${pfx}INSIDE${if (soft) "_BEYOND" else ""}@$rl", "$where: $text 의 중심이 닫은 도면(부품) 속 — 경계에서 ${Fmt.nc(depth)}")
            }
            for ((wall, name) in listOf(c.endWall to "END_PAST", c.startWall to "START_PAST")) {
                val (p, u) = wall ?: continue
                val past = far(m.nose, p, u)
                if (past > -c.r + tol) fail("$pfx$name@$rl", "$where: $text 이 막힌 ${if (name == "END_PAST") "끝" else "시작"} ${xz(p)} 벽 너머로 ${Fmt.nc(past + c.r)}")
            }
        }

        var ref: List<Seg>? = null
        var reachEnd = Double.NEGATIVE_INFINITY
        var reachStart = Double.NEGATIVE_INFINITY
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            t.runs++
            val run = sim(f, c.r, tip, form, c.front)
            val where = "형식 $form · 팁 $tip"
            val st = run.stop
            if (st != null) {
                // 형상 줄의 바깥 꺾임 90° 넘음은 도면 모양 — FANUC 은 토막을 끼워 도는데 시뮬레이터가 그리지 않는다(시뮬레이터 한계 — 세기만)
                if (st.code.toString() == "COMP_ACUTE_OUTER" && role(st.line) == "형상") {
                    t.simLimit++
                    t.add("시뮬레이터 한계|${c.group}", "[${c.group} · ${c.label}] ${c.tag}\n  $where: ${st.line - pre.size}줄\n  G41/G42: ${f.joinToString(" / ")}")
                    return
                }
                fail("STOP_${st.code}@${role(st.line)}", "$where: ${st.line - pre.size}줄 '${f.getOrNull(st.line - pre.size - 1)}'")
                continue
            }
            val moves = run.steps.filterIsInstance<MoveStep>()
            val cut = moves.filter { it.line in cutLines && it.kind == MoveKind.FEED }.map { it.nose }
            val r0 = ref
            if (r0 == null) {
                ref = cut
            } else if (r0.size != cut.size || r0.indices.any { !same(r0[it], cut[it]) }) {
                val k = r0.indices.firstOrNull { it >= cut.size || !same(r0[it], cut[it]) } ?: cut.size
                fail("FORM_TIP_DIFF", "$where: 깎는 노즈가 첫 실행과 다름(걸음 $k: ${r0.getOrNull(k)} / ${cut.getOrNull(k)})")
            }
            for (m in moves) look(m, where)
            c.endWall?.let { (p, u) -> for (n in cut) reachEnd = max(reachEnd, far(n, p, u)) }
            c.startWall?.let { (p, u) -> for (n in cut) reachStart = max(reachStart, far(n, p, u)) }
        }
        // 급송 속도 비를 바꿔도(LRP=0 — 꺾이는 자리가 바뀜) 꺾인 G00 이 공기
        for ((rx, rz) in listOf(40_000.0 to 6_000.0, 5_000.0 to 40_000.0)) for (form in listOf(CompStartup.AB, CompStartup.C)) {
            val run = sim(f, c.r, c.tip, form, c.front, rx, rz)
            val where = "급송 X $rx · Z $rz 형식 $form 팁 ${c.tip}"
            val st = run.stop
            if (st != null) {
                if (!(st.code.toString() == "COMP_ACUTE_OUTER" && role(st.line) == "형상")) fail("DOGLEG_STOP_${st.code}@${role(st.line)}", where)
                continue
            }
            for (m in run.steps.filterIsInstance<MoveStep>()) if (m.kind == MoveKind.RAPID) look(m, where, "DOGLEG_")
        }
        val uncut = listOfNotNull(
            c.endWall?.takeIf { reachEnd.isFinite() }?.let { "${c.label} · 끝" to (-c.r - reachEnd) },
            c.startWall?.takeIf { reachStart.isFinite() }?.let { "${c.label} · 시작" to (-c.r - reachStart) },
        )
        close(c, t, bad, uncut, reg.ok)
    }

    private fun close(c: Case, t: Tally, bad: Map<String, String>, uncut: List<Pair<String, Double>>, regionOk: Boolean) {
        fun msg(m: String) = "[${c.group} · ${c.label}] ${c.tag}\n  경고 ${c.warns.sorted()}\n  $m\n  G41/G42: ${c.nc.joinToString(" / ")}"
        for ((k, m) in bad) t.add("${c.label}|${c.group}:$k", msg("$k: $m"))
        val hard = bad.keys.filter { !it.startsWith("SOFT_") }
        fun roleOf(k: String) = k.substringAfter('@', "")
        fun entryRole(k: String) = roleOf(k) == "진입 G0" || roleOf(k) == "첫 깎는 줄"
        fun exitRole(k: String) = roleOf(k) == "빼는 줄" || roleOf(k) == "G40" || roleOf(k) == "물러남"
        fun covered(k: String) = (("END_PAST" in k || "START_PAST" in k) && "SHORT_END_PAST" in c.warns) || (entryRole(k) && "ENTRY_NOT_FOUND" in c.warns) ||
            (exitRole(k) && "EXIT_NOT_FOUND" in c.warns)
        val exempt = c.label == "도면 알람" || c.label == "좁은 구멍"
        val open = hard.filter { !covered(it) }
        if (open.isNotEmpty() && !exempt) {
            t.silent++
            for (k in open) t.add("경고 없음 · ${c.label}|${c.group}:$k", msg("$k: ${bad[k]}"))
        }
        if ("FORM_TIP_DIFF" in bad && c.label != "도면 알람") t.formDiff++
        val entryBad = hard.any { entryRole(it) }
        val ew = "ENTRY_NOT_FOUND" in c.warns
        when {
            ew && entryBad -> {
                t.entryWarnBad++
                t.add("진입 경고+진짜 문제|${c.group}", msg(hard.filter { entryRole(it) }.joinToString("\n  ") { "$it: ${bad[it]}" }))
            }
            ew && (!regionOk || bad.keys.any { it.startsWith("SOFT_") && "INSIDE_BEYOND" in it && entryRole(it) }) -> {
                t.entryWarnUndecided++
                t.add("진입 경고 · 판정 못 함|${c.group}", msg("ENTRY_NOT_FOUND — 닫은 도면을 가릴 수 없거나 진입이 끝 너머 어림한 닫는 선만 넘어 부품 속인지 모름"))
            }
            ew -> {
                t.entryWarnFalse++
                t.add("진입 헛경고|${c.group}", msg("ENTRY_NOT_FOUND 인데 진입 G0 · 첫 깎는 줄에 문제 없음${if (hard.isEmpty()) "" else " (다른 문제 $hard)"}"))
            }
            hard.any { entryRole(it) && !covered(it) } && c.label != "도면 알람" -> t.entrySilent++
        }
        if ("EXIT_NOT_FOUND" in c.warns) {
            if (hard.any { exitRole(it) }) {
                t.exitWarnBad++
            } else {
                t.exitWarnFalse++
                t.add("EXIT_NOT_FOUND 인데 빼는 길 문제 없음|${c.group}", msg("EXIT_NOT_FOUND 인데 빼는 줄 · G40 · 물러남에 문제 없음${if (hard.isEmpty()) "" else " (다른 문제 $hard)"}"))
            }
        }
        val past = hard.any { "END_PAST" in it || "START_PAST" in it }
        val sw = "SHORT_END_PAST" in c.warns
        if (sw && past) t.shortWarnPast++
        if (sw && !past) {
            t.shortWarnFalse++
            t.add("SHORT_END_PAST 헛경고|${c.group}", msg("SHORT_END_PAST 인데 노즈가 벽을 넘지 않음${if (hard.isEmpty()) "" else " (다른 문제 $hard)"}"))
        }
        for ((k, v) in uncut) {
            if (v <= tol) continue
            t.uncut.getOrPut(k) { mutableListOf() } += v
            val worst = t.uncutWorst[k]
            if (worst == null || v > worst.first) t.uncutWorst[k] = v to msg("벽까지 노즈가 ${Fmt.nc(v)} 못 감(그만큼 덜 깎임)")
        }
    }

    private fun finish(t: Tally, min: Int) {
        println(t.report())
        assertTrue(t.cases >= min, "사례가 너무 적다 ${t.cases}\n" + t.summary())
        assertTrue(t.silent == 0 && t.formDiff == 0 && t.entryWarnFalse == 0, t.summary() + "\n(전문은 시험 출력)")
    }

    // ================================================================ 꼬리표 · 생성기(다른 씨앗 · 범위 · 모양)

    private fun narrow(d: List<Seg>, r: Double): Boolean {
        val low = d.flatMap { along(it, 0.1) }.filter { it.x > 1e-6 }.minOfOrNull { it.x } ?: 0.0
        return low < (1 + sqrt(2.0)) * r + 0.02
    }

    private fun labelOf(warns: Set<String>, gone: Boolean, d: List<Seg>, inner: Boolean, r: Double, g41: Boolean, endWall: Pair<Pt, Pt>?, startWall: Pair<Pt, Pt>?): String {
        if ("CONCAVE_R_LE_NOSE" in warns || "NOSE_GT_SLOPE" in warns || gone) return "도면 알람"
        if (inner && narrow(d, r)) return "좁은 구멍"
        val sE = endWall != null && d.size > 1 && d.last() is Line && lenOf(d.last()) < 2 * r + 0.1
        val sS = startWall != null && d.size > 1 && d.first() is Line && lenOf(d.first()) < 2 * r + 0.1
        if (!sE && !sS) return "보통"
        val nb = if (sE) d[d.size - 2] else d[1]
        val what = when (nb) {
            is Line -> "직선"
            is Arc -> {
                // 공구가 원 밖이면 볼록: G41(공구 왼쪽)은 시계 원호, G42 는 반시계
                val convex = g41 == nb.cw
                val k = rad(nb) / r
                (if (convex) "볼록 R" else "오목 R") + when {
                    k < 1.45 -> "≈r"
                    k < 2.6 -> " 1.5~2.5r"
                    rad(nb) < 7.5 -> " 중간"
                    else -> " 큼"
                }
            }
        }
        return "짧은 ${if (sE && sS) "끝·시작" else if (sE) "끝" else "시작"}+$what"
    }

    private fun zClear(rnd: Random, r: Double): Double = when (rnd.nextInt(5)) {
        0 -> 0.0
        1 -> r3(rnd.nextDouble() * r)
        2 -> r3(r + rnd.nextDouble() * (5.0 - r))
        3 -> 5.0
        else -> listOf(0.5, 1.0, 2.0, 3.0)[rnd.nextInt(4)]
    }

    /** 모서리 R: 없음 · r 보다 조금 큼(1.01 ~ 1.41r) · 1.5 ~ 2.5r(오목이면 R < 2r 이면 빼는 줄 보정선이 노즈 원에 못 닿는 경계) · 2 ~ 6 · 아주 큼(8 ~ 30) */
    private fun cornerR(rnd: Random, r: Double): Double = when (rnd.nextInt(8)) {
        0, 1 -> 0.0
        2, 3 -> r3(r * (1.01 + 0.4 * rnd.nextDouble()))
        4 -> r3(r * (1.5 + rnd.nextDouble()))
        5 -> r3(2.0 + 4.0 * rnd.nextDouble())
        else -> r3(8.0 + 22.0 * rnd.nextDouble())
    }

    private fun noseOf(rnd: Random) = r3(0.1 * (1 + rnd.nextInt(16)))

    private fun taperCase(group: String, tag: String, res: TaperResult, inner: Boolean, mirror: Boolean, r: Double): Case? {
        val s = res.solution ?: return null
        val d = s.drawing.segs
        val front = if (mirror) -1.0 else 1.0
        val first = d.first()
        val last = d.last()
        // 넣은 끝 · 시작 좌표 너머가 소재인가: 수평은 척 쪽(화면 역방향이면 계산기 +Z), 단면은 재료 쪽(외경 아래 — X0 은 빼고, 내경 위)
        var endWall: Pair<Pt, Pt>? = null
        if (s.toEnd && last is Line) {
            val u = unitOf(last.end - last.start)
            val blocked = if (abs(u.x) > abs(u.z)) (if (inner) u.x > 0 else u.x < 0 && last.end.x > 1e-6) else u.z * front < 0
            if (blocked) endWall = last.end to u
        }
        var startWall: Pair<Pt, Pt>? = null
        if (s.fromStart && first is Line) {
            val u = unitOf(first.end - first.start)
            val blocked = if (abs(u.x) > abs(u.z)) (if (inner) u.x < 0 else u.x > 0 && first.start.x > 1e-6) else u.z * front > 0
            if (blocked) startWall = first.start to u * -1.0
        }
        val warns = res.warnings.map { it.name }.toSet()
        val gone = d.indices.any { k -> k < s.center.segs.size && lenOf(s.center.segs[k]) < 1e-6 && lenOf(d[k]) > 1e-6 }
        val label = labelOf(warns, gone, d, inner, r, s.ncG41G42.firstOrNull() == "G41", endWall, startWall)
        return Case(group, tag, warns, s.ncG41G42, r, s.tip, d, inner, front, endWall, startWall, label)
    }

    private fun taperCases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val type = 1 + rnd.nextInt(5)
            val reverse = rnd.nextInt(4) == 0
            val flip = rnd.nextBoolean()
            val mirror = !reverse && rnd.nextInt(5) == 0
            val r = noseOf(rnd)
            val zc = zClear(rnd, r)
            val tip: Int? = if (rnd.nextInt(3) == 0) null else rnd.nextInt(10)
            val up = if (inner == (type == 5)) 1.0 else -1.0
            val deg = when (rnd.nextInt(6)) {
                0 -> u(0.5, 4.0)
                1 -> u(80.0, 89.5)
                else -> u(4.0, 80.0)
            }
            val dz = r3(if (rnd.nextInt(6) == 0) u(0.1, 2 * r + 0.1) else u(0.8, 14.0))
            val x1 = r3(if (inner) u(8.0, 140.0) else u(3.0, 160.0))
            val z1 = r3(u(-5.0, 5.0))
            val x2 = r3(x1 + up * 2 * dz * tan(deg * PI / 180))
            if (x2 < 0.5 || x2 > 600.0) return@repeat
            val z2 = r3(z1 - dz)
            // 짧은 끝 · 시작 · 둘 다 — 짧은 요소는 0.05 ~ 2r
            val which = rnd.nextInt(3)
            fun shortLeg() = r3(u(0.05, 2 * r))
            fun otherLeg() = if (rnd.nextBoolean()) r3(u(1.0, 10.0)) else null
            val sLeg = if (which != 0) shortLeg() else otherLeg()
            val eLeg = if (which != 1) shortLeg() else otherLeg()
            val frontFace = type <= 2
            val backFace = type == 2 || type == 4
            val start = sLeg?.let { if (frontFace) max(0.0, r3(x1 - up * 2 * it)) else r3(z1 + it) }
            val end = eLeg?.let { if (backFace) max(0.0, r3(x2 + up * 2 * it)) else r3(z2 - it) }
            var inp = TaperInput(inner, type, reverse, r, tip, x1, z1, x2, z2, null, zc, cornerR(rnd, r), cornerR(rnd, r), start = start, end = end, approach = !mirror)
            if (flip) inp = inp.copy(x1 = inp.x2, z1 = inp.z2, x2 = inp.x1, z2 = inp.z1, cornerR1 = inp.cornerR2, cornerR2 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
            taperCase("테이퍼", inp.toString(), TaperCalc.calc(inp), inner, mirror, r)?.let { out += it }
        }
        return out
    }

    /** 겹테이퍼: 골(외경은 가운데가 아래, 내경은 위 — 공구 쪽에서 오목) 둘에 산 하나. 앞뒤 수평 요소가 짧은 막힌 끝 · 시작 */
    private fun doubleCases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val reverse = rnd.nextInt(4) == 0
            val flip = rnd.nextBoolean()
            val mirror = !reverse && rnd.nextInt(5) == 0
            val r = noseOf(rnd)
            val zc = zClear(rnd, r)
            val tip: Int? = if (rnd.nextInt(3) == 0) null else rnd.nextInt(10)
            val valley = rnd.nextInt(3) != 0
            val s = if (valley == inner) 1.0 else -1.0
            val x1 = r3(if (inner) u(14.0, 120.0) else u(8.0, 140.0))
            val z1 = r3(u(-4.0, 4.0))
            val dz1 = r3(if (rnd.nextInt(5) == 0) u(0.15, 2 * r + 0.2) else u(0.6, 8.0))
            val dz2 = r3(if (rnd.nextInt(5) == 0) u(0.15, 2 * r + 0.2) else u(0.6, 8.0))
            val x2 = r3(x1 + s * 2 * dz1 * tan(u(4.0, 80.0) * PI / 180))
            val x3 = r3(x2 - s * 2 * dz2 * tan(u(4.0, 80.0) * PI / 180))
            if (x2 < 1.0 || x3 < 1.0 || x2 > 600.0 || x3 > 600.0) return@repeat
            val z2 = r3(z1 - dz1)
            val z3 = r3(z2 - dz2)
            val which = rnd.nextInt(3)
            fun shortLeg() = r3(u(0.05, 2 * r))
            fun otherLeg() = if (rnd.nextBoolean()) r3(u(1.0, 8.0)) else null
            val sLeg = if (which != 0) shortLeg() else otherLeg()
            val eLeg = if (which != 1) shortLeg() else otherLeg()
            var inp = DoubleTaperInput(
                inner, reverse, r, tip, x1, z1, x2, z2, x3, z3, zc, cornerR(rnd, r), cornerR(rnd, r), cornerR(rnd, r),
                start = sLeg?.let { r3(z1 + it) }, end = eLeg?.let { r3(z3 - it) }, approach = !mirror,
            )
            if (flip) inp = inp.copy(x1 = inp.x3, z1 = inp.z3, x3 = inp.x1, z3 = inp.z1, cornerR1 = inp.cornerR3, cornerR3 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
            taperCase(if (valley) "겹테이퍼 골" else "겹테이퍼 산", inp.toString(), TaperCalc.calcDouble(inp), inner, mirror, r)?.let { out += it }
        }
        return out
    }

    private fun roundCase(group: String, tag: String, m: RoundResult, d: RoundResult, inner: Boolean, r: Double, extra: String? = null, through: Boolean = false): Case? {
        val s = m.solution ?: return null
        val ds = d.solution ?: return null
        val dr = s.drawing.segs
        val last = dr.last()
        var endWall: Pair<Pt, Pt>? = null
        // 관통 구멍의 끝 너머는 같은 지름의 구멍 — 막힌 끝이 아니다(사용자 답 2026-11-13). 너머로 뺄 자리가 없으면 계산기가 전 규칙(막힌 끝)으로 짠다 —
        // 어느 쪽인지는 NC 로 가른다(CompanyFrameVerifyTest.kt 의 `passesThroughEnd` — 검증 2026-11-14)
        if (last is Line && !(through && passesThroughEnd(s.nc, last.end.z))) {
            val u = unitOf(last.end - last.start)
            val blocked = if (abs(u.x) > abs(u.z)) (if (inner) u.x > 0 else u.x < 0 && last.end.x > 1e-6) else u.z < 0
            if (blocked) endWall = last.end to u
        }
        val warns = (m.warnings + d.warnings).map { it.name }.toSet()
        val gone = dr.indices.any { k -> k < ds.center.segs.size && lenOf(ds.center.segs[k]) < 1e-6 && lenOf(dr[k]) > 1e-6 }
        var label = labelOf(warns, gone, dr, inner, r, s.nc.firstOrNull() == "G41", endWall, null)
        if (extra != null && label == "보통") label = extra
        return Case(group, tag, warns, s.nc, r, s.tip, dr, inner, 1.0, endWall, null, label)
    }

    private fun roundCases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val r = noseOf(rnd)
            val zc = zClear(rnd, r)
            val tip: Int? = if (rnd.nextInt(3) == 0) null else rnd.nextInt(10)
            // 끝 직선(외경 끝 원통 · 내경 2 공 뒤 구멍)은 넷에 셋이 짧게(0.05 ~ 2r)
            val leg = r3(if (rnd.nextInt(4) != 0) u(0.05, 2 * r) else u(1.0, 10.0))
            // 주 R 은 다섯에 하나가 r 보다 조금 큰 공(1.05 ~ 3r)
            val bigR = r3(if (rnd.nextInt(5) == 0) r * u(1.05, 3.0) else u(1.5, 40.0))
            fun cr() = cornerR(rnd, r)
            val base = RoundInput(type = type, noseR = r, tip = tip, zClear = zc, legLen = leg, inner = inner)
            val inp = when (type) {
                1 -> base.copy(mainR = bigR, l1 = r3(u(0.1, 1.95) * bigR), cornerR1 = cr())
                2 -> base.copy(mainR = bigR, d1 = r3(u(0.15, 1.95) * bigR), l1 = r3(u(0.3, 12.0)), cornerR1 = cr(), l2Ref = if (rnd.nextBoolean()) L2Ref.A else L2Ref.B)
                3 -> base.copy(mainR = bigR, l1 = r3(u(0.1, 1.0) * bigR), cornerR1 = cr())
                4 -> base.copy(mainR = bigR, l1 = r3(u(1.0, 1.95) * bigR), cornerR1 = cr())
                5 -> {
                    val d2 = r3(u(0.6, 1.95) * bigR)
                    base.copy(mainR = bigR, d2 = d2, d1 = r3(u(0.15, 0.95) * d2), l1 = r3(u(0.5, 10.0)), cornerR1 = cr(), cornerR2 = cr(), through = inner && rnd.nextInt(3) == 0)
                }
                else -> {
                    val a = u(0.15, 0.9) * bigR
                    val w = sqrt(bigR * bigR - a * a)
                    val l2 = r3(w - u(-0.85, 0.85) * min(w, bigR))
                    val dz = l2 - w
                    val top = if (dz >= 0) bigR else sqrt(max(0.0, bigR * bigR - dz * dz))
                    base.copy(
                        mainR = bigR, d1 = r3(2 * a), d2 = r3(2 * (top + u(0.3, 8.0))), l1 = r3(u(0.5, 10.0)), l2 = l2, cornerR1 = cr(), cornerR2 = cr(),
                        through = inner && rnd.nextInt(3) == 0,
                    )
                }
            }
            roundCase("라운드", inp.toString(), RoundCalc.machine(inp), RoundCalc.calc(inp), inner, r, through = inp.through)?.let { out += it }
        }
        return out
    }

    /** 3점호(정 · 역방향 · 앞으로 부푼 것) — 진입 길 못 찾음을 주로 본다 */
    private fun arc3Cases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val r = noseOf(rnd)
            val rr = u(0.8, 45.0)
            val c = Pt(u(-30.0, 8.0), if (inner) u(0.25 * rr + 2.5, rr + 35.0) else u(-0.85 * rr, rr + 30.0))
            val a0 = u(0.0, 2 * PI)
            val sw = u(0.1, 3.0) * (if (rnd.nextBoolean()) 1 else -1)
            val pts = listOf(0.0, 0.5, 1.0).map { c + Pt(cos(a0 + sw * it), sin(a0 + sw * it)) * rr }
            val sampled = (0..48).map { c + Pt(cos(a0 + sw * it / 48), sin(a0 + sw * it / 48)) * rr }
            if (sampled.any { it.x < 0.3 } || abs(pts[2].z - pts[0].z) < 0.15) return@repeat
            val reverse = pts[2].z > pts[0].z
            val bulge = if (reverse) sampled.any { it.z < pts[0].z - 1e-6 } else sampled.any { it.z > pts[0].z + 1e-6 }
            val zc = zClear(rnd, r)
            val tip: Int? = if (rnd.nextInt(3) == 0) null else rnd.nextInt(10)
            val inp = Arc3Input(inner, r, tip, r3(2 * pts[0].x), r3(pts[0].z), r3(2 * pts[1].x), r3(pts[1].z), r3(2 * pts[2].x), r3(pts[2].z), zc)
            val extra = when {
                reverse && bulge -> "3점호 역방향+부풂"
                reverse -> "3점호 역방향"
                bulge -> "3점호 부풂"
                else -> null
            }
            roundCase("3점호", inp.toString(), RoundCalc.machine(inp), RoundCalc.arc3(inp), inner, r, extra)?.let { out += it }
        }
        return out
    }

    // ================================================================ 시험

    @Test
    fun taperShortNeighbors() {
        val t = Tally("테이퍼 짧은 막힌 끝 · 시작 + 이웃 (씨앗 515151)")
        for (c in taperCases(515151, 6000)) check(c, t)
        finish(t, 1000)
    }

    @Test
    fun doubleTaperValleys() {
        val t = Tally("겹테이퍼 골 · 산 + 짧은 막힌 끝 · 시작 (씨앗 616161)")
        for (c in doubleCases(616161, 6000)) check(c, t)
        finish(t, 600)
    }

    @Test
    fun roundShortLegs() {
        val t = Tally("라운드 짧은 끝 직선 + 모서리 R · 주 원호 (씨앗 717171)")
        for (c in roundCases(717171, 3000)) check(c, t)
        finish(t, 300)
    }

    @Test
    fun arc3Entries() {
        val t = Tally("3점호 진입 · 빼기 (씨앗 818181)")
        for (c in arc3Cases(818181, 2000)) check(c, t)
        finish(t, 300)
    }
}
