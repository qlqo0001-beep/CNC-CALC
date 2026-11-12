package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.calc.sim.ContactKind
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * 독립 검증(2026-10-29, 시뮬레이터 관점) — simulator-design.md 13.7 고친 판의 테이퍼 G41/G42 · 직접 보정(G40) 글을
 * 씨앗 고정 무작위 수천 개(홑 TYPE 1~5 · 겹테이퍼, 외경/내경, 계산기 정/역, 뒤집기, 화면 역방향(거울 — approach = false),
 * 팁 표준 · 1~9, 노즈 R 0.2~1.6, 모서리 R(R < r · R = r 포함), 시작 · 끝 좌표 넣음/비움(외경 X0 포함), Z 여유 0~5, 각도 기준 X/Z)로 만들어
 * - calc.sim(GSim — 형식 A, 급송 직선 · 축마다)과
 * - 이 파일의 따로 짠 제어기 모형([model] — 형식 A · B, 구현의 GComp · RefG4x · simG4x 를 쓰지 않음, 급송 직선)으로 돌린다.
 * 구현은 고치지 않는다 — 숫자를 출력하고, 지금 성립하는 것만 단언한다.
 *
 * 보는 것(노즈 중심 경로 — 기계 원점에서 오는 첫 이동은 뺀다. 첫 G00 앞에 '부품 앞 안전점에서 그 X 로 Z 만 와서' 줄을 넣는다):
 * 1. 남김: 깎는 도면(시작 · 끝 요소는 넣었을 때만) 위 점마다 노즈가 남긴 살 max(0, 노즈 경로까지 거리 − r) — G4x − G40 (끝 꼭짓점 · 첫 점 · 어디든)
 * 2. 파고듦: 노즈가 도면(깎지 않는 요소 포함) + 양 끝 접선 연장선에 r 보다 가까이 간 깊이 — 블록 종류별
 * 3. 넣은 좌표 너머가 막힌 쪽(척 · 구멍 바닥 · 보스 — calc-design '넣은 시작·끝점 너머로 넘지 않기')으로 G00 점 · 노즈가 넘는가
 * 4. 끝 노즈 자리: (G4x 끝 노즈 − G40 끝 노즈)·u 가 문서 식인가(회사 꼴 — 막힌 끝 팁 벡터·u − r, 외경 단면 X0 끝 min(0, 팁 벡터·u), 그 밖 팁 벡터·u)
 * 5. 소재 깎기(StockSim, 노즈 원 공구)의 마지막 단면이 두 글에서 같은가 · 급송 깎음
 * 6. 형식 A ↔ B 차이, 따로 짠 모형 ↔ GSim 차이, 시뮬레이터 멈춤
 * 소재 깎기는 봉(내경은 관) 소재라 황삭 안 된 덩어리를 두 글의 다른 진입 · 후퇴 길이 다르게 지나는 차이도 나온다 — 그래서 단면 비교는 깎는 도면에서
 * r + 0.05 안이고 깎는 요소가 가장 가까운 점만, 그 차이도 까닭(시작 첫 점 · 깎지 않는 요소 타기 · 그 밖)으로 나눠 출력한다.
 * 결과(2026-10-29, 씨앗 20261029, 풀린 사례 4 036): 위 단언은 통과. 출력으로만 남긴 문제는 맨 아래 단언 위 주석.
 * 2026-11-04: G41/G42 글이 회사 꼴(계획 단계 4 — 보정 혼자 줄 · Z20. 진입 · F.1 · F.3 빼는 줄 · 이동 없는 G40 · G0Z200.)이 되어 글 읽기와 모형이 처음
 * 보정 혼자 줄을 받는다: 그 앞 공구 자리는 시뮬레이터 처음 값 원점(MachineSetup().home)이고 다음 이동(G0X…Z20.)이 시작 블록 — 그 줄은 두 쪽 모두 뺀다.
 */
class VerifyG4xSimTest {
    private val pre = listOf("T0101", "G97 S1000 M03", "G99 F0.2")
    private val tol = 1e-3

    // ================================================================ 기하(따로 짬)

    private fun lft(v: Pt) = Pt(-v.x, v.z)
    private fun unitOf(v: Pt) = v * (1.0 / v.length)
    private fun angOf(v: Pt) = atan2(v.x, v.z)
    private fun wrap2(a: Double): Double {
        val t = 2 * PI
        var x = a % t
        if (x < 0) x += t
        return x
    }

    private fun wrapPi(a: Double): Double {
        var x = wrap2(a)
        if (x > PI) x -= 2 * PI
        return x
    }

    private fun radOf(a: Arc) = (a.start - a.center).length
    private fun sweepOf(a: Arc): Double {
        if ((a.end - a.start).length < 1e-12) return 0.0
        val d = angOf(a.end - a.center) - angOf(a.start - a.center)
        return wrap2(if (a.cw) -d else d)
    }

    private fun lenOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> radOf(s) * sweepOf(s)
    }

    private fun at(s: Seg, f: Double): Pt = when (s) {
        is Line -> s.start + (s.end - s.start) * f
        is Arc -> {
            val a = angOf(s.start - s.center) + (if (s.cw) -1.0 else 1.0) * sweepOf(s) * f
            s.center + Pt(cos(a), sin(a)) * radOf(s)
        }
    }

    private fun pts(s: Seg, step: Double): List<Pt> {
        val n = max(1, ceil(lenOf(s) / step).toInt())
        return (0..n).map { at(s, it.toDouble() / n) }
    }

    private fun tanStart(s: Seg): Pt = when (s) {
        is Line -> unitOf(s.end - s.start)
        is Arc -> lft(unitOf(s.start - s.center)) * (if (s.cw) -1.0 else 1.0)
    }

    private fun tanEnd(s: Seg): Pt = when (s) {
        is Line -> unitOf(s.end - s.start)
        is Arc -> lft(unitOf(s.end - s.center)) * (if (s.cw) -1.0 else 1.0)
    }

    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            val t = if (l2 <= 0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0)
            (p - (s.start + d * t)).length
        }
        is Arc -> {
            val v = p - s.center
            val rel = wrap2((if (s.cw) -1.0 else 1.0) * (angOf(v) - angOf(s.start - s.center)))
            if (v.length > 1e-12 && rel <= sweepOf(s) + 1e-12) abs(v.length - radOf(s)) else min((p - s.start).length, (p - s.end).length)
        }
    }

    /** 두 요소 사이 가장 가까운 거리(직선끼리는 정확, 원호가 끼면 원호를 0.002 mm 로 나눔) */
    private fun segDist(a: Seg, b: Seg): Double {
        if (a is Line && b is Line) {
            val d1 = a.end - a.start
            val d2 = b.end - b.start
            val den = d1 cross d2
            if (abs(den) > 1e-15) {
                val w = b.start - a.start
                val t = (w cross d2) / den
                val u = (w cross d1) / den
                if (t in 0.0..1.0 && u in 0.0..1.0) return 0.0
            }
            return minOf(dist(a.start, b), dist(a.end, b), dist(b.start, a), dist(b.end, a))
        }
        return if (a is Arc) pts(a, 0.002).minOf { dist(it, b) } else pts(b, 0.002).minOf { dist(it, a) }
    }

    private fun arcCenter(a: Pt, b: Pt, rr: Double, cw: Boolean): Pt {
        val d = b - a
        val half = d.length / 2
        val h = sqrt(max(0.0, rr * rr - half * half))
        val m = (a + b) * 0.5
        val nl = lft(unitOf(d))
        return if (cw) m - nl * h else m + nl * h
    }

    private fun llMeet(p1: Pt, d1: Pt, p2: Pt, d2: Pt): Pt? {
        val den = d1 cross d2
        if (abs(den) < 1e-12) return null
        return p1 + d1 * (((p2 - p1) cross d2) / den)
    }

    private fun lcMeet(p: Pt, d: Pt, c: Pt, rr: Double): List<Pt> {
        val u = unitOf(d)
        val w = p - c
        val b = w dot u
        val disc = b * b - ((w dot w) - rr * rr)
        if (disc < -1e-9) return emptyList()
        val s = sqrt(max(0.0, disc))
        return listOf(p + u * (-b - s), p + u * (-b + s))
    }

    private fun ccMeet(c1: Pt, r1: Double, c2: Pt, r2: Double): List<Pt> {
        val d = (c2 - c1).length
        if (d < 1e-12) return emptyList()
        val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
        val h2 = r1 * r1 - a * a
        if (h2 < -1e-9) return emptyList()
        val u = unitOf(c2 - c1)
        val m = c1 + u * a
        val h = sqrt(max(0.0, h2))
        return listOf(m + lft(u) * h, m - lft(u) * h)
    }

    private fun f4(v: Double) = if (v.isNaN()) "NaN" else ((v * 10000).roundToLong() / 10000.0).toString()

    // ================================================================ NC 글 읽기 · 따로 짠 제어기 모형(형식 A · B)

    /** 노즈 중심 이동 하나. [blk] = 글 목록의 줄 번호(0부터), [rapid] = G00 */
    private class NMove(val seg: Seg, val blk: Int, val rapid: Boolean)

    private class Blk(val idx: Int, val g: Int, val comp: Int?, val p: Pt, val rr: Double?)

    /**
     * G0~3 · G40~42 · X(직경) · Z · R · F(버림)만(빈칸이 있든 없든 — `ncWords`). 빠진 축 · 이동 G 는 앞 값(모달). 주석 줄은 건너뜀.
     * 좌표가 아직 없는 처음 보정 혼자 줄(회사 꼴 `G42`)은 다음 이동 줄의 보정 G 로 붙이고, 그 앞에 기계 원점(시뮬레이터 처음 공구 자리)을 idx −1 블록으로 둔다
     */
    private fun parse(nc: List<String>): List<Blk> {
        var z: Double? = null
        var x: Double? = null
        var g = 0
        var pending: Int? = null
        val out = mutableListOf<Blk>()
        for ((i, raw) in nc.withIndex()) {
            val text = raw.trim()
            if (text.isEmpty() || text.startsWith("(")) continue
            var comp: Int? = null
            var rr: Double? = null
            for (tok in ncWords(text)) {
                val v = tok.substring(1)
                when (tok[0]) {
                    'G' -> when (val n = v.toDouble().toInt()) {
                        0, 1, 2, 3 -> g = n
                        40, 41, 42 -> comp = n
                        else -> error("모르는 G$n: $raw")
                    }
                    'X' -> x = v.toDouble() / 2
                    'Z' -> z = v.toDouble()
                    'R' -> rr = v.toDouble()
                    'F' -> {}
                    else -> error("모르는 낱말 $tok: $raw")
                }
            }
            val zz = z
            val xx = x
            if (zz == null || xx == null) {
                pending = comp ?: error("좌표가 아직 없음: $raw")
                continue
            }
            if (pending != null) {
                out += Blk(-1, 0, null, MachineSetup().home, null)
                comp = pending
                pending = null
            }
            out += Blk(i, g, comp, Pt(zz, xx), rr)
        }
        return out
    }

    private class Mod(val moves: List<NMove>, val outerSC: Int, val bad: String?)

    private class Ent(val s: Pt, val e: Pt, val c: Pt?, val cw: Boolean, val b: Blk)

    /**
     * 제어기 노즈R 보정 모형(FANUC FF 5.4 의 그림을 보고 따로 짬). 노즈 중심 = 인선 − 팁 벡터(G40), 보정선 = 도면을 공구 쪽으로 r.
     * 이음점: 접해서 이어짐 → 끝 법선, 안쪽 → 두 보정선의 교점(시작 · 취소 블록은 직선 보정선), 바깥 ≤ 90° → 교점,
     * 시작 · 취소의 바깥: 형식 A = 다음(시작)/앞(취소) 블록 법선, 형식 B = 교점(> 90° 면 네 점 — e+na·r, +ta·r, e+nb·r−tb·r, e+nb·r).
     * 이동 없는 G40: 앞 블록 끝 법선(형식 A · B 같음, FF 그림 5.4.4(e)), 다음 직선 이동이 그 자리에서 끝점 − 팁 벡터로. 급송은 직선.
     * [Mod.outerSC] = 시작 · 취소가 바깥 모서리였던 수(형식 A · B 가 다를 수 있는 자리)
     */
    private fun model(nc: List<String>, tv: Pt, r: Double, typeB: Boolean): Mod {
        val bs = parse(nc)
        val out = mutableListOf<NMove>()
        fun g40(k: Int) {
            val a = bs[k - 1].p
            val b = bs[k].p
            if ((b - a).length < 1e-12) return
            val arc = bs[k].g == 2 || bs[k].g == 3
            val seg: Seg = if (arc) Arc(a - tv, b - tv, arcCenter(a, b, bs[k].rr!!, bs[k].g == 2) - tv, bs[k].g == 2) else Line(a - tv, b - tv)
            out += NMove(seg, bs[k].idx, bs[k].g == 0)
        }
        val k0 = bs.indexOfFirst { it.comp == 41 || it.comp == 42 }
        if (k0 < 0) {
            for (k in 1 until bs.size) g40(k)
            return Mod(out, 0, null)
        }
        if (k0 == 0) return Mod(out, 0, "보정 시작이 첫 블록")
        for (k in 1 until k0) g40(k)
        val kc = (k0 + 1 until bs.size).firstOrNull { bs[it].comp == 40 } ?: return Mod(out, 0, "G40 없음")
        val side = if (bs[k0].comp == 41) 1.0 else -1.0
        val alone = (bs[kc].p - bs[kc - 1].p).length < 1e-9
        val ents = mutableListOf<Ent>()
        for (k in k0..(if (alone) kc - 1 else kc)) {
            val a = bs[k - 1].p
            val e = bs[k].p
            if ((e - a).length < 1e-9) return Mod(out, 0, "보정 중 움직임 없는 블록 '${nc[bs[k].idx]}'")
            val arc = bs[k].g == 2 || bs[k].g == 3
            if (arc && (k == k0 || (!alone && k == kc))) return Mod(out, 0, "원호에서 시작 · 취소")
            ents += Ent(a, e, if (arc) arcCenter(a, e, bs[k].rr!!, bs[k].g == 2) else null, bs[k].g == 2, bs[k])
        }
        fun n(t: Pt) = lft(t) * side
        fun tS(en: Ent): Pt = en.c?.let { lft(unitOf(en.s - it)) * (if (en.cw) -1.0 else 1.0) } ?: unitOf(en.e - en.s)
        fun tE(en: Ent): Pt = en.c?.let { lft(unitOf(en.e - it)) * (if (en.cw) -1.0 else 1.0) } ?: unitOf(en.e - en.s)
        fun rho(en: Ent): Double {
            val c = en.c!!
            val rr = (en.s - c).length
            return if ((n(tS(en)) dot (c - en.s)) > 0) rr - r else rr + r
        }
        val last = if (alone) ents.lastIndex else ents.lastIndex - 1 // 마지막 보정 블록
        for (i in 1..last) if (ents[i].c != null && rho(ents[i]) < -1e-9) return Mod(out, 0, "오목 원호 R < r")
        var outer = 0
        fun meet(i: Int, e: Pt, na: Pt, nb: Pt): Pt? {
            val a = ents[i]
            val b = ents[i + 1]
            val guess = e + (na + nb) * (r / 2)
            val la = a.c == null || i == 0
            val lb = b.c == null || (!alone && i + 1 == ents.lastIndex)
            val cands: List<Pt> = when {
                la && lb -> listOfNotNull(llMeet(e + na * r, tE(a), e + nb * r, tS(b)))
                la -> lcMeet(e + na * r, tE(a), b.c!!, rho(b))
                lb -> lcMeet(e + nb * r, tS(b), a.c!!, rho(a))
                else -> ccMeet(a.c!!, rho(a), b.c!!, rho(b))
            }
            return cands.minByOrNull { (it - guess).length }
        }
        val smooth = min(1e-3, 5e-4 / max(r, 1e-9))
        fun junction(i: Int): List<Pt>? {
            val a = ents[i]
            val b = ents[i + 1]
            val e = a.e
            val ta = tE(a)
            val tb = tS(b)
            val na = n(ta)
            val nb = n(tb)
            val cr = ta cross tb
            val dt = ta dot tb
            if (abs(cr) < smooth && dt > 0) return listOf(e + na * r)
            val startup = i == 0
            val cancel = !alone && i + 1 == ents.lastIndex
            if (cr * side > 0) return meet(i, e, na, nb)?.let { listOf(it) }
            if (startup || cancel) outer++
            if (!typeB && startup) return listOf(e + nb * r)
            if (!typeB && cancel) return listOf(e + na * r)
            if (dt >= 0) return meet(i, e, na, nb)?.let { listOf(it) }
            return if (startup || cancel) listOf(e + na * r, e + na * r + ta * r, e + nb * r - tb * r, e + nb * r) else null
        }
        var cur = ents[0].s - tv
        for (i in 0..last) {
            val en = ents[i]
            val js = if (i < ents.lastIndex) junction(i) ?: return Mod(out, outer, "이음 $i: 간섭 · 예각 바깥 '${nc[en.b.idx]}'") else listOf(en.e + n(tE(en)) * r)
            val j = js.first()
            val c = en.c
            val seg: Seg = if (i == 0 || c == null) {
                if (i > 0 && ((j - cur) dot tE(en)) < -5e-4) return Mod(out, outer, "간섭(직선) '${nc[en.b.idx]}'")
                Line(cur, j)
            } else {
                val sw = wrapPi((if (en.cw) -1.0 else 1.0) * (angOf(j - c) - angOf(cur - c)))
                if (sw * rho(en) < -5e-4) return Mod(out, outer, "간섭(원호) '${nc[en.b.idx]}'")
                if (sw <= 1e-9 || rho(en) <= 1e-9) Line(cur, j) else Arc(cur, j, c, en.cw)
            }
            out += NMove(seg, en.b.idx, en.b.g == 0)
            for (q in js.zipWithNext()) out += NMove(Line(q.first, q.second), en.b.idx, false)
            cur = js.last()
        }
        var k = kc
        if (!alone) {
            out += NMove(Line(cur, bs[kc].p - tv), bs[kc].idx, bs[kc].g == 0)
        } else {
            var j = kc + 1
            while (j < bs.size && (bs[j].p - bs[j - 1].p).length < 1e-12) j++
            if (j < bs.size) {
                if (bs[j].comp == 41 || bs[j].comp == 42 || bs[j].g == 2 || bs[j].g == 3) return Mod(out, outer, "이동 없는 G40 뒤 원호 · 다시 보정")
                out += NMove(Line(cur, bs[j].p - tv), bs[j].idx, bs[j].g == 0)
                k = j
            }
        }
        for (q in k + 1 until bs.size) g40(q)
        return Mod(out, outer, null)
    }

    // ================================================================ GSim

    private fun simRun(prog: List<String>, r: Double, tip: Int, rapid: RapidPath): SimRun =
        GSim.run(SimInput((pre + prog).joinToString("\n"), MachineSetup(rapidPath = rapid, compStartup = CompStartup.AB), mapOf(1 to ToolOffset(r, tip))))

    /** 기계 원점에서 오는 이동([skip] 줄)은 뺀다 */
    private fun movesOf(run: SimRun, skip: Int): List<NMove> = run.steps.filterIsInstance<MoveStep>()
        .filter { it.line > pre.size && it.line - pre.size - 1 != skip }
        .map { NMove(it.nose, it.line - pre.size - 1, it.kind == MoveKind.RAPID) }

    // ================================================================ 사례

    private class Case(val tag: String, val r: Double, val res: TaperResult, val inner: Boolean, val dbl: Boolean, val type: Int, val reverse: Boolean,
                       val flip: Boolean, val mirror: Boolean, val stdTip: Boolean, val angleRefX: Boolean?)

    /**
     * 씨앗 고정 무작위 입력. 화면이 넣는 그대로: 점 · 시작 · 끝 · 모서리 R 은 0.001 로 반올림, 다섯 값 가운데 하나는 비움(각도를 넣으면 X 기준이면 90° − 각도 칸).
     * 뒤집기는 칸의 뜻대로(칸 T1 = 그림 왼쪽 점 …). approach = false 는 화면 역방향(그림을 Z 로 거울한 풀이 — 계산기 +Z 가 실제 척 쪽)이라 계산기 정방향에서만.
     */
    private fun cases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        fun <T> pick(l: List<T>): T = l[rnd.nextInt(l.size)]
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        fun r3(v: Double) = Fmt.nc(v).toDouble()
        repeat(count) {
            val dbl = rnd.nextInt(5) == 0
            val inner = rnd.nextBoolean()
            val reverse = rnd.nextInt(3) == 0
            val flip = rnd.nextBoolean()
            val mirror = !reverse && rnd.nextInt(4) == 0
            val r = pick(listOf(0.2, 0.4, 0.4, 0.5, 0.8, 0.8, 1.0, 1.2, 1.6))
            val tip: Int? = if (rnd.nextInt(5) < 2) null else 1 + rnd.nextInt(9)
            val zc = pick(listOf(0.0, 0.3, 0.5, 1.0, 1.0, 2.0, 5.0))
            fun cr() = if (rnd.nextInt(3) != 0) 0.0 else pick(listOf(0.1, 0.2, r, 0.5, 1.0, 1.5, 2.0, 3.0))
            fun legLen() = r3(if (rnd.nextInt(3) == 0) u(0.05, 1.0) else u(1.0, 15.0))
            val x1 = r3(u(if (inner) 12.0 else 8.0, 120.0))
            val z1 = r3(u(-5.0, 5.0))
            if (!dbl) {
                val type = 1 + rnd.nextInt(5)
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val deg = u(3.0, 87.0)
                val dz = r3(u(0.3, 12.0))
                val x2 = r3(x1 + up * 2 * dz * tan(deg * PI / 180))
                if (x2 < 1.0 || x2 > 400.0) return@repeat
                val z2 = r3(z1 - dz)
                val angTrue = atan2(abs(x2 - x1) / 2, z1 - z2) * 180 / PI
                val refX = rnd.nextBoolean()
                val angIn = if (refX) 90.0 - r3(90.0 - angTrue) else r3(angTrue)
                val blank = rnd.nextInt(6) // 0~3 = 점 값 하나 비움(각도 넣음), 4 · 5 = 각도 비움
                val frontFace = type <= 2
                val backFace = type == 2 || type == 4
                val start = if (rnd.nextBoolean()) null else if (frontFace) {
                    if (!inner && rnd.nextInt(5) == 0) 0.0 else max(0.0, r3(x1 - up * 2 * legLen()))
                } else r3(z1 + legLen())
                val end = if (rnd.nextBoolean()) null else if (backFace) {
                    if (inner && rnd.nextInt(5) == 0) 0.0 else max(0.0, r3(x2 + up * 2 * legLen()))
                } else r3(z2 - legLen())
                var inp = TaperInput(
                    inner, type, reverse, r, tip,
                    x1.takeIf { blank != 0 }, z1.takeIf { blank != 1 }, x2.takeIf { blank != 2 }, z2.takeIf { blank != 3 },
                    angIn.takeIf { blank < 4 }, zc, cornerR1 = cr(), cornerR2 = cr(), start = start, end = end, approach = !mirror,
                )
                if (flip) inp = inp.copy(
                    x1 = inp.x2, z1 = inp.z2, x2 = inp.x1, z2 = inp.z1, cornerR1 = inp.cornerR2, cornerR2 = inp.cornerR1,
                    start = inp.end, end = inp.start, flip = true,
                )
                out += Case(inp.toString(), r, TaperCalc.calc(inp), inner, false, type, reverse, flip, mirror, tip == null, if (blank < 4) refX else null)
            } else {
                val dz1 = r3(u(0.5, 10.0))
                val dz2 = r3(u(0.5, 10.0))
                val s1 = if (rnd.nextBoolean()) 1.0 else -1.0
                val s2 = if (rnd.nextBoolean()) 1.0 else -1.0
                val x2 = r3(x1 + s1 * 2 * dz1 * tan(u(5.0, 80.0) * PI / 180))
                val x3 = r3(x2 + s2 * 2 * dz2 * tan(u(5.0, 80.0) * PI / 180))
                if (x2 < 1.0 || x3 < 1.0 || x2 > 400 || x3 > 400) return@repeat
                val z2 = r3(z1 - dz1)
                val z3 = r3(z2 - dz2)
                var inp = DoubleTaperInput(
                    inner, reverse, r, tip, x1, z1, x2, z2, x3, z3, zc, cr(), cr(), cr(),
                    start = if (rnd.nextBoolean()) r3(z1 + legLen()) else null, end = if (rnd.nextBoolean()) r3(z3 - legLen()) else null,
                    approach = !mirror,
                )
                if (flip) inp = inp.copy(
                    x1 = inp.x3, z1 = inp.z3, x3 = inp.x1, z3 = inp.z1, cornerR1 = inp.cornerR3, cornerR3 = inp.cornerR1,
                    start = inp.end, end = inp.start, flip = true,
                )
                out += Case(inp.toString(), r, TaperCalc.calcDouble(inp), inner, true, 0, reverse, flip, mirror, tip == null, null)
            }
        }
        return out
    }

    /** 사례의 도면 · 재료 */
    private class Geo(
        val bnd: List<Seg>, val cut: List<Seg>, val qs: List<Pt>, val f: Pt, val uF: Pt, val l: Pt, val uB: Pt, val nB: Pt,
        /** 넣은 시작 · 끝 좌표 너머가 막힌 쪽(척 · 구멍 바닥 · 보스) */
        val startWall: Boolean, val endWall: Boolean,
        val zMin: Double, val zMax: Double, val xMin: Double, val xMax: Double, val mirror: Boolean, val inner: Boolean,
    )

    /** 끝 요소의 끝점 · 단위 접선 · 공구 쪽 법선(막힌 끝이 짧은지 가리는 데만) */
    private class EndOf(val l: Pt, val uB: Pt, val nB: Pt)

    private fun g0Of(s: TaperSolution): EndOf {
        val last = s.drawing.segs.last()
        val uB = tanEnd(last)
        return EndOf(last.end, uB, lft(uB) * (if (s.side.name == "G41") 1.0 else -1.0))
    }

    private fun geoOf(s: TaperSolution, c: Case): Geo {
        val d = s.drawing.segs
        var cut = d
        if (!s.fromStart) cut = cut.drop(1)
        if (!s.toEnd) cut = cut.dropLast(1)
        cut = cut.filter { lenOf(it) > 1e-9 }
        val u0 = tanStart(d.first())
        val un = tanEnd(d.last())
        val uF = tanStart(cut.first())
        val uB = tanEnd(cut.last())
        val side = if (s.side.name == "G41") 1.0 else -1.0
        fun horiz(u: Pt) = abs(u.z) > abs(u.x)
        val l = cut.last().end
        val f = cut.first().start
        // 끝: 수평이면 척 쪽(거울이면 계산기 +Z)으로 깎아 끝남, 단면이면 재료 쪽(외경 X−, 내경 X+)으로 깎아 끝남(외경 X0 은 뺌).
        // 시작: 수평이면 척 쪽에서 앞으로 깎기 시작, 단면이면 바깥으로 깎기 시작(그 앞이 안쪽 같은 면 · 보스, 외경 X0 은 뺌) — 회사 꼴 G41/G42 의 막힌 시작과 같은
        // 모양 규칙(calc-design '진입점과 NC' — 팁과 상관없다. 2026-11-04 전에는 '팁의 Z 가 진행 쪽이면 비어 있다' 고 보았다 — 직접 보정의 연장선 진입 규칙)
        val endWall = s.toEnd && if (horiz(uB)) (if (c.mirror) uB.z > 0 else uB.z < 0) else ((uB.x < 0) != c.inner && !(!c.inner && l.x <= 1e-9))
        val startWall = s.fromStart && if (horiz(uF)) (if (c.mirror) uF.z < 0 else uF.z > 0) else ((uF.x > 0) != c.inner && !(!c.inner && f.x <= 1e-9))
        // 재료(파고듦을 잴 경계) = 도면 + 양 끝 접선 연장선. 다만 빈 끝(끝점을 넣었고 막히지 않음 — 외경 X0 끝 빼고)의 너머는 공기다:
        // 회사 꼴은 그쪽으로 곧게 빼고 G40 을 두므로(사용자 결정 ③) 그 연장선은 재료로 보지 않는다
        val outerAxis = s.toEnd && !horiz(uB) && !c.inner && uB.x < 0 && l.x <= 1e-9
        val freeEnd = s.toEnd && !endWall && !outerAxis
        val bnd = listOf(Line(d.first().start - u0 * 1000.0, d.first().start)) + d + (if (freeEnd) emptyList() else listOf(Line(d.last().end, d.last().end + un * 1000.0)))
        val all = d.flatMap { listOf(it.start, it.end) }
        return Geo(
            bnd, cut, cut.flatMap { pts(it, 0.02) }, f, uF, l, uB, lft(uB) * side, startWall, endWall,
            all.minOf { it.z }, all.maxOf { it.z }, all.minOf { it.x }, all.maxOf { it.x }, c.mirror, c.inner,
        )
    }

    /**
     * 첫 G00 점으로 오는 길(글 밖 — 앞 공구 자리에 달림)을 하나 가정한다: 부품 앞(거울이면 계산기 −Z) 5 mm 바깥에서 그 X 로 맞춘 뒤 Z 로만.
     * 그 이동(G40 상태 노즈)이 도면에 가장 깊이 파고든 깊이(r − 거리)
     */
    private fun approachDepth(g: Geo, first: Pt, tv: Pt, r: Double): Double {
        val z = if (g.mirror) min(g.zMin, first.z) - 5 else max(g.zMax, first.z) + 5
        val seg = Line(Pt(z, first.x) - tv, first - tv)
        return r - g.bnd.minOf { segDist(seg, it) }
    }

    // ================================================================ 모음

    private class Hit(val v: Double, val cat: String, val key: String, val ex: String)

    /** 검사 하나의 모음. [key] = 경로(화면 정방향 · 화면 역방향(거울) · 계산기 역방향 — 화면에 없음) · 뒤집기 · 팁 */
    private inner class Agg(val name: String) {
        var n = 0
        var max = 0.0
        val hits = mutableListOf<Hit>()
        val nByKey = mutableMapOf<String, Int>()
        fun see(v: Double, cat: String, key: String, ex: () -> String) {
            n++
            nByKey[key] = (nByKey[key] ?: 0) + 1
            if (v > max) max = v
            if (v > tol) hits += Hit(v, cat, key, ex())
        }
        fun screenHits() = hits.count { !it.key.startsWith("계산기역") }
        fun screenMax() = hits.filter { !it.key.startsWith("계산기역") }.maxOfOrNull { it.v } ?: 0.0
        fun line() = "| $name | $n | ${hits.size} | ${screenHits()} | ${f4(max)} | ${f4(screenMax())} |"
        fun detail(top: Int = 8): List<String> =
            listOf("    갈래: " + hits.groupBy { it.key }.entries.sortedByDescending { it.value.size }.joinToString(" · ") { (k, v) -> "$k ${v.size}/${nByKey[k]}" }) +
                hits.groupBy { it.cat }.entries.sortedByDescending { it.value.size }.take(top).map { (k, v) ->
                    val w = v.maxBy { it.v }
                    "    ${v.size}개 · $k · ${f4(v.minOf { it.v })}~${f4(w.v)} — ${w.ex}"
                }
    }

    private class Count(val name: String) {
        val m = mutableMapOf<String, Int>()
        val ex = mutableMapOf<String, String>()
        fun add(k: String, e: String) {
            m[k] = (m[k] ?: 0) + 1
            if (k !in ex) ex[k] = e
        }
    }

    // ================================================================ 시험

    @Test
    fun randomTapersBothProgramsOnSimulator() {
        val clock = TimeSource.Monotonic.markNow()
        val all = cases(20261029, 5200)
        val solved = all.filter { it.res.solution != null }
        val calcStops = all.filter { it.res.solution == null }.groupingBy { it.res.warnings.first().name }.eachCount()

        val vars = listOf("A·직선", "A·꺾임", "B·직선")
        fun aggs(prefix: String) = vars.associateWith { Agg("$prefix ($it)") }
        // 넣은 좌표 너머가 막힌 끝 · 시작(척 · 구멍 바닥 · 보스)은 따로 — G41/G42 는 노즈 끝이 그 좌표에서 멈추고(교점), 직접 보정은 팁에 따라 너머로 가며 깎는다
        // (2026-10-29 판정 · 고침: 막힌 끝의 이동 없는 G40 은 노즈가 끝좌표 너머로 갔다)
        val endLeft = aggs("끝 꼭짓점 남김 G4x − G40 (막힌 끝 빼고)")
        val startLeft = aggs("시작 첫 도면점 남김 G4x − G40 (막힌 시작 빼고)")
        val endLeftWall = aggs("끝 꼭짓점 남김 G4x − G40 — 막힌 끝")
        val startLeftWall = aggs("시작 첫 도면점 남김 G4x − G40 — 막힌 시작")
        val anyLeft = aggs("깎는 도면 어디든 남김 G4x − G40")
        val gouge4 = aggs("G41/G42 글 파고듦")
        val gouge0 = listOf("직선", "꺾임").associateWith { Agg("G40 글 파고듦 ($it)") }
        val endOver4 = aggs("막힌 끝 좌표 너머 노즈 G4x")
        // 막힌 끝에서 교점(직각 취소)으로 멈춘 글 — 끝 요소가 짧아 이동 없는 G40(직접 보정과 같은 끝 법선)으로 둔 글은 빼고(2026-10-29 고침)
        val endOverStop = aggs("막힌 끝 좌표 너머 노즈 G4x — 벽에서 멈춘 글")
        val endOverD = aggs("막힌 끝 좌표 너머 노즈 G4x − G40")
        // 끝 요소가 노즈R 쯤보다 짧은 막힌 끝은 계산기가 곧게 뺀다(알려진 한계 — 끝좌표 너머로 감, calc-design '진입점과 NC') — 따로 출력만
        val shortEndOver = Agg("막힌 끝이 짧아 곧게 뺀 글의 끝좌표 너머 노즈 G4x − G40 (알려진 한계)")
        val mirrorApproach = Agg("화면 역방향 진입 G0 이 계산기 −Z 쪽 요소의 연장선을 지남 G4x (남은 일)")
        val shortEndGouge = Agg("막힌 끝이 짧아 곧게 뺀 글의 빼는 줄 · G40 뒤 노즈가 벽 쪽 연장선을 지남 G4x (알려진 한계)")
        val narrowGouge = Agg("노즈에 비해 좁은 구멍의 G41/G42 글 파고듦 (알려진 한계)")
        val startOver4 = aggs("막힌 시작 좌표 너머 노즈 G4x")
        val startOverD = aggs("막힌 시작 좌표 너머 노즈 G4x − G40")
        val endOver0 = Agg("막힌 끝 좌표 너머 노즈 G40")
        val startOver0 = Agg("막힌 시작 좌표 너머 노즈 G40")
        val g00Beyond4 = Agg("G41/G42 글 G00 점이 막힌 좌표 너머")
        val g00Beyond0 = Agg("G40 글 G00 점이 막힌 좌표 너머")
        val g00BeyondD = Agg("G00 점 막힌 좌표 너머 G4x − G40")
        val approach4 = Agg("첫 G00 에 앞에서 Z 로 올 때 파고듦 G4x")
        val approach0 = Agg("첫 G00 에 앞에서 Z 로 올 때 파고듦 G40")
        val approachD = Agg("첫 G00 에 앞에서 Z 로 올 때 파고듦 G4x − G40")
        val ride4 = Agg("깎지 않는 요소(비운 시작 · 끝 요소와 그 연장)를 이송 노즈가 타고 간 길이 G4x (mm)")
        val ride0 = Agg("깎지 않는 요소를 이송 노즈가 타고 간 길이 G40 (mm)")
        val stockLin = Agg("StockSim 단면 차이 G4x ↔ G40 (직선)")
        val stockDog = Agg("StockSim 단면 차이 G4x ↔ G40 (꺾임)")
        val abDiff = Agg("형식 A ↔ B 노즈 경로 차이(따로 짠 모형)")
        val modelVsSim = Agg("따로 짠 모형 A ↔ GSim A·직선 노즈 경로 차이")
        val rapidCut = Count("급송 깎음(StockSim RAPID_CUT) — G4x 에만 있는 것")
        val stockWhy = Count("StockSim 단면 차이의 까닭")
        val stopsNoWarn = Count("G41/G42 글이 멈춤 — 계산기 경고(AGAINST_TOOL · CONCAVE_R_LE_NOSE · NOSE_GT_SLOPE) 없음")
        val startLeftKey = Agg("시작 첫 도면점 남김 G4x − G40 — 화면 경로 · 표준 팁 · AGAINST_TOOL 없음")
        val endOverKey = Agg("막힌 끝 좌표 너머 노즈 G4x — 화면 경로 · AGAINST_TOOL 없음")
        val endOverKey0 = Agg("막힌 끝 좌표 너머 노즈 G40 — 화면 경로 · AGAINST_TOOL 없음")
        val rapidRide4 = Agg("G41/G42 글 G00 이 깎는 도면을 노즈로 타고 간 길이(mm, 꺾임)")
        val rapidRide0 = Agg("G40 글 G00 이 깎는 도면을 노즈로 타고 간 길이(mm, 꺾임)")
        val stockOtherLin = Agg("StockSim 단면 차이 — 시작 첫 점 · 깎지 않는 요소로 설명 안 되는 것 (직선)")
        val stockOtherDog = Agg("StockSim 단면 차이 — 시작 첫 점 · 깎지 않는 요소로 설명 안 되는 것 (꺾임)")
        val stops = Count("멈춤(사례마다 한 번 — 급송 직선 기준)")
        // 문서 식(calc-design '끝 블록', 2026-10-29 판정 고침): 막힌 끝(교점으로 멈춘 글) u·(팁 벡터·u − r), 바깥으로 이어 빼는 단면 u·(팁 벡터·u), 그 밖 u·min(0, 팁 벡터·u)
        val endTable = Count("끝 노즈 (G4x − G40)·u 와 문서 식(막힌 끝 · 바깥 단면 · 그 밖)")
        var outerSC = 0
        var typeAInfo = 0
        var aloneCount = 0
        var errors = 0
        val errEx = mutableListOf<String>()

        for (c in solved) {
            val s = c.res.solution!!
            val r = c.r
            val tip = s.tip
            val tv = NoseComp.tipVector(tip, r)
            val w = c.res.warnings
            val wText = w.joinToString(",").ifEmpty { "경고 없음" }
            if (s.drawing.segs.let { d -> (if (s.fromStart) d else d.drop(1)).let { if (s.toEnd) it else it.dropLast(1) } }.none { lenOf(it) > 1e-9 }) {
                stops.add("깎는 요소 없음", c.tag)
                continue
            }
            val path = when {
                c.reverse -> "계산기역(화면 없음)"
                c.mirror -> "화면역(거울)"
                else -> "화면정"
            }
            val key = "$path${if (c.flip) "·뒤집기" else ""}·${if (c.stdTip) "표준팁" else "다른팁"}"
            try {
                val g = geoOf(s, c)
                fun cls(v: Double) = when {
                    abs(v - r) < 1e-9 -> "+r"
                    abs(v) < 1e-9 -> "0"
                    abs(v + r) < 1e-9 -> "−r"
                    else -> f4(v)
                }
                fun horiz(u: Pt) = abs(u.z) > abs(u.x)
                val cat = (if (c.dbl) "겹테이퍼" else "형상${c.type}") + (if (c.inner) " 내경" else " 외경") + " $path" + (if (c.flip) " 뒤집기" else "") +
                    " 팁$tip${if (c.stdTip) "(표준)" else ""}" +
                    " 시작${if (s.fromStart) "(${if (horiz(g.uF)) "수평" else "단면"}, 팁·u ${cls(tv dot g.uF)})" else "비움"}" +
                    " 끝${if (s.toEnd) "(${if (horiz(g.uB)) "수평" else "단면"}, 팁·u ${cls(tv dot g.uB)})" else "비움"} [$wText]"
                val nc4 = s.ncG41G42
                val nc0 = s.ncG40
                val ex4 = { "${c.tag} | ${nc4.drop(1).joinToString(" / ")}" }
                val ex0 = { "${c.tag} | ${nc0.joinToString(" / ")}" }
                val b4 = parse(nc4)
                val b0 = parse(nc0)
                // 기계 원점에서 오는 이동(회사 꼴은 첫 G0X…Z20. — 보정 시작 블록)은 뺀다
                val home4 = b4.first { it.idx >= 0 }.idx
                val home0 = b0[0].idx
                val iG40 = nc4.indexOfFirst { ncWords(it).contains("G40") }
                val iCut = nc4.indexOfFirst { it.endsWith("F.1") }
                val iExit = nc4.indexOfFirst { "F.3" in it }
                if (nc4[iG40].trim() == "G40") aloneCount++
                // 끝 요소가 노즈R 쯤보다 짧은 막힌 끝은 계산기가 곧게 뺀다(알려진 한계 — 끝좌표 너머로 감). 벽에서 멈춘 글은 그 밖의 막힌 끝
                val shortEnd = s.toEnd && (((g0Of(s).l + g0Of(s).nB * r - g0Of(s).uB * r) - s.center.segs.last().start) dot g0Of(s).uB) < 0.05
                val narrow = c.inner && s.drawing.segs.flatMap { listOf(it.start, it.end) }.filter { it.x > 1e-6 }.minOf { it.x } < (1 + sqrt(2.0)) * r + 0.02

                val run4 = mapOf("A·직선" to simRun(nc4, r, tip, RapidPath.LINEAR), "A·꺾임" to simRun(nc4, r, tip, RapidPath.DOGLEG))
                val run0 = mapOf("직선" to simRun(nc0, r, tip, RapidPath.LINEAR), "꺾임" to simRun(nc0, r, tip, RapidPath.DOGLEG))
                val modA = model(nc4, tv, r, false).let { m -> Mod(m.moves.filter { it.blk != home4 }, m.outerSC, m.bad) }
                val modB = model(nc4, tv, r, true).let { m -> Mod(m.moves.filter { it.blk != home4 }, m.outerSC, m.bad) }
                outerSC += modA.outerSC
                typeAInfo += run4.getValue("A·직선").issues.count { it.code == IssueCode.COMP_TYPE_A }
                run4.getValue("A·직선").stop?.let { stops.add("G41/G42 ${it.code} · $path · ${if (c.stdTip) "표준팁" else "다른팁"} · [$wText]", ex4()) }
                if (w.none { it == TaperWarning.AGAINST_TOOL || it == TaperWarning.CONCAVE_R_LE_NOSE || it == TaperWarning.NOSE_GT_SLOPE }) run4.getValue("A·직선").stop?.let { stopsNoWarn.add("G41/G42 ${it.code} · $path · ${if (c.stdTip) "표준팁" else "팁$tip"}", ex4()) }
                run0.getValue("직선").stop?.let { stops.add("G40 ${it.code} · $path · ${if (c.stdTip) "표준팁" else "다른팁"} · [$wText]", ex0()) }
                if (run4.getValue("A·직선").stop == null) modA.bad?.let { stops.add("모형 A 만: $it", ex4()) }

                val mv4 = mutableMapOf<String, List<NMove>>()
                for ((k, run) in run4) if (run.stop == null) mv4[k] = movesOf(run, home4)
                if (modB.bad == null) mv4["B·직선"] = modB.moves
                val mv0 = mutableMapOf<String, List<NMove>>()
                for ((k, run) in run0) if (run.stop == null) mv0[k] = movesOf(run, home0)

                if (modA.bad == null) mv4["A·직선"]?.let { modelVsSim.see(hausdorff(it, modA.moves), cat, key, ex4) }
                // 형식 A · B 는 시작 블록(공구 교환 자리 → Z20.)과 그 다음 진입 줄들(공기 — 시작 꺾임의 노즈가 다르다)에서만 다를 수 있다 — 첫 깎는 줄부터 견준다
                if (modA.bad == null && modB.bad == null) abDiff.see(hausdorff(modA.moves.filter { it.blk >= iCut }, modB.moves.filter { it.blk >= iCut }), cat, key, ex4)

                // 남김은 이송(G01~G03)으로만 — 급송이 도면 곁을 지나 깎은 것은 셈에 넣지 않는다(급송 깎음은 파고듦 · StockSim 으로 본다)
                fun leftAt(q: Pt, mv: List<NMove>) = max(0.0, (mv.filter { !it.rapid }.ifEmpty { mv }).minOf { dist(q, it.seg) } - r)
                fun depths(mv: List<NMove>) = mv.map { m -> m to (r - g.bnd.minOf { segDist(m.seg, it) }) }
                fun reach(mv: List<NMove>, wv: Pt) = mv.maxOf { m ->
                    when (val sg = m.seg) {
                        is Line -> max(sg.start dot wv, sg.end dot wv)
                        is Arc -> pts(sg, 0.01).maxOf { it dot wv }
                    }
                }
                val k0 = b4.indexOfFirst { it.comp == 41 || it.comp == 42 }
                val i0 = b4[k0].idx
                // 회사 꼴: 보정 혼자 줄 뒤 G0X…Z20. 이 시작 블록, 그 뒤 G0 들이 진입, 첫 깎는 줄(F.1) ~ 형상, 첫 빼는 줄(F.3) ~ G40 앞이 빼는 줄
                fun phase4(blk: Int): String = when {
                    blk < i0 -> "G00 접근 · 진입"
                    blk == i0 -> "시작 블록 '${nc4[blk]}'"
                    blk < iCut -> "진입 G0"
                    blk < iExit -> "형상 블록"
                    blk < iG40 -> "빼는 줄"
                    blk == iG40 -> "취소 블록 '${nc4[blk]}'"
                    else -> "G40 뒤 이동"
                }

                for (v in vars) {
                    val m4 = mv4[v] ?: continue
                    val m0 = mv0[if (v == "A·꺾임") "꺾임" else "직선"] ?: continue
                    (if (g.endWall) endLeftWall else endLeft).getValue(v).see(leftAt(g.l, m4) - leftAt(g.l, m0), cat, key, ex4)
                    (if (g.startWall) startLeftWall else startLeft).getValue(v).see(leftAt(g.f, m4) - leftAt(g.f, m0), cat, key, ex4)
                    if (v == "A·직선" && !c.reverse && c.stdTip && TaperWarning.AGAINST_TOOL !in w) startLeftKey.see(leftAt(g.f, m4) - leftAt(g.f, m0), cat, key, ex4)
                    var worst = 0.0
                    var wq = g.l
                    for (q in g.qs) {
                        val d = leftAt(q, m4) - leftAt(q, m0)
                        if (d > worst) {
                            worst = d
                            wq = q
                        }
                    }
                    anyLeft.getValue(v).see(worst, cat, key) { "Z${f4(wq.z)} X${f4(wq.xDia)} · ${ex4()}" }
                    val deep = depths(m4).maxByOrNull { it.second }
                    // 화면 역방향(거울)은 실제 부품 앞이 계산기 −Z 라 진입 G0 이 계산기 −Z 쪽 요소(실제 맨 앞 — 단면이면 높이를 모른다, 그림 5 mm)를 넘어온다.
                    // 계산기는 그 그림 위 2r + 0.4 로 넘는다 — 이 시험의 재료(양 끝 접선 연장선)를 지나는 것은 따로 세고 출력한다(남은 일 — 실제 단면이 더 높으면 부딪힌다).
                    // 끝 요소가 노즈R 쯤보다 짧은 막힌 끝의 곧은 빼기(알려진 한계)가 그 연장선(벽 쪽)을 지나는 것도 따로
                    if (deep != null && deep.second > tol && c.mirror && deep.first.blk < iCut) {
                        if (v == "A·직선") mirrorApproach.see(deep.second, cat, key, ex4)
                    } else if (deep != null && deep.second > tol && shortEnd && g.endWall && deep.first.blk >= iExit) {
                        if (v == "A·직선") shortEndGouge.see(deep.second, cat, key, ex4)
                    } else if (deep != null && deep.second > tol && narrow) {
                        // 노즈에 비해 좁은 구멍(가장 좁은 반지름 < G40 자리 규칙 반지름)은 G40 을 둘 곳이 없다(알려진 한계 — 계산기는 축 위에 둔다)
                        if (v == "A·직선") narrowGouge.see(deep.second, cat, key, ex4)
                    } else if (deep != null) gouge4.getValue(v).see(deep.second, "${phase4(deep.first.blk)}${if (deep.first.rapid) " G00" else ""} · $cat", key, ex4)
                    if (g.endWall) {
                        val o4 = reach(m4, g.uB) + r - (g.l dot g.uB)
                        val o0 = reach(m0, g.uB) + r - (g.l dot g.uB)
                        endOver4.getValue(v).see(o4, cat, key, ex4)
                        if (!shortEnd) endOverStop.getValue(v).see(o4, cat, key, ex4)
                        if (v == "A·직선" && !c.reverse && TaperWarning.AGAINST_TOOL !in w) {
                            endOverKey.see(o4, cat, key, ex4)
                            endOverKey0.see(o0, cat, key, ex0)
                        }
                        if (!shortEnd) endOverD.getValue(v).see(o4 - o0, cat, key, ex4) else if (v == "A·직선") shortEndOver.see(o4 - o0, cat, key, ex4)
                        if (v == "A·직선") endOver0.see(o0, cat, key, ex0)
                    }
                    if (g.startWall) {
                        val wv = g.uF * -1.0
                        val o4 = reach(m4, wv) + r - (g.f dot wv)
                        val o0 = reach(m0, wv) + r - (g.f dot wv)
                        startOver4.getValue(v).see(o4, cat, key, ex4)
                        startOverD.getValue(v).see(o4 - o0, cat, key, ex4)
                        if (v == "A·직선") startOver0.see(o0, cat, key, ex0)
                    }
                }
                for ((k, m0) in mv0) {
                    val deep = depths(m0).maxByOrNull { it.second }
                    if (deep != null) gouge0.getValue(k).see(deep.second, "${if (deep.first.rapid) "G00" else "G01"} · $cat", key, ex0)
                }

                // 깎지 않는 요소(그림 · 재료만인 비운 요소 · 양 끝 연장선)를 이송 노즈가 r 거리로 타고 간 길이 — 거기 소재가 남아 있으면 깎는다
                val uncut = g.bnd.filter { it !in g.cut }
                fun ride(mv: List<NMove>): Double {
                    var len = 0.0
                    for (m in mv) {
                        if (m.rapid) continue
                        val ps = pts(m.seg, 0.01)
                        for (q in 1 until ps.size) {
                            val p = (ps[q - 1] + ps[q]) * 0.5
                            if (uncut.minOf { dist(p, it) } <= r + 1e-3 && g.cut.minOf { dist(p, it) } > r + 1e-3) len += (ps[q] - ps[q - 1]).length
                        }
                    }
                    return len
                }
                val rd4 = mv4["A·직선"]?.let { ride(it) } ?: 0.0
                val rd0 = mv0["직선"]?.let { ride(it) } ?: 0.0
                if (mv4["A·직선"] != null) ride4.see(rd4, cat, key, ex4)
                if (mv0["직선"] != null) ride0.see(rd0, cat, key, ex0)
                val slA = run { val m4 = mv4["A·직선"]; val m0 = mv0["직선"]; if (m4 == null || m0 == null) 0.0 else leftAt(g.f, m4) - leftAt(g.f, m0) }
                // 급송(G00)이 깎는 도면을 노즈 r 거리로 타고 간 길이 — 다듬질 면을 급송으로 깎는 것(소재 가정과 상관없음)
                fun rapidRide(mv: List<NMove>): Double {
                    var len = 0.0
                    for (m in mv) {
                        if (!m.rapid) continue
                        val ps = pts(m.seg, 0.01)
                        for (q in 1 until ps.size) if (g.cut.minOf { dist((ps[q - 1] + ps[q]) * 0.5, it) } <= r + 1e-3) len += (ps[q] - ps[q - 1]).length
                    }
                    return len
                }
                mv4["A·꺾임"]?.let { rapidRide4.see(rapidRide(it), cat, key, ex4) }
                mv0["꺾임"]?.let { rapidRide0.see(rapidRide(it), cat, key, ex0) }
                // G00 점이 넣은 좌표 너머(막힌 쪽)
                fun beyond(bs: List<Blk>): Double {
                    val g00 = bs.filter { it.g == 0 && it.comp == null && it.idx >= 0 }
                    if (g00.isEmpty()) return 0.0
                    return max(
                        if (g.endWall) g00.maxOf { (it.p - g.l) dot g.uB } else 0.0,
                        if (g.startWall) g00.maxOf { (it.p - g.f) dot (g.uF * -1.0) } else 0.0,
                    )
                }
                if (g.endWall || g.startWall) {
                    val y4 = beyond(b4)
                    val y0 = beyond(b0)
                    g00Beyond4.see(y4, cat, key, ex4)
                    g00Beyond0.see(y0, cat, key, ex0)
                    g00BeyondD.see(y4 - y0, cat, key, ex4)
                }
                // 첫 G00 점으로 오는 길(가정 — 부품 앞에서 Z 로)
                val a4 = approachDepth(g, b4.first { it.idx >= 0 }.p, tv, r)
                val a0 = approachDepth(g, b0[0].p, tv, r)
                approach4.see(a4, cat, key, ex4)
                approach0.see(a0, cat, key, ex0)
                approachD.see(a4 - max(0.0, a0), cat, key, ex4)

                // 끝 노즈 자리(끝점 넣음): (G4x 끝 노즈 − G40 끝 노즈)·u 와 문서 식 min(0, 팁 벡터·u)
                val m4a = mv4["A·직선"]
                val m0a = mv0["직선"]
                if (s.toEnd && m4a != null && m0a != null) {
                    // 회사 꼴: 마지막 형상 블록은 첫 빼는 줄(F.3) 앞. 막힌 끝(짧지 않음)은 공구 쪽 직각 빼기의 교점(노즈 끝이 끝좌표에서 멈춤) → 팁 벡터·u − r,
                    // 단면 X0 끝(외경 · 내경 바닥 — 내경은 2026-11-12 통합 고침부터 직접 보정이 축 너머로 이어 가지 않고 Z 로 빠져 노즈 중심을 축까지)은 X −2r 까지 쓰고
                    // 직각(노즈 중심이 축 — 직접 보정도 노즈 중심을 축까지) → 외경 min(0, 팁 벡터·u) · 내경 0(어느 팁이든 노즈 중심이 축 — 검증 F1 2026-11-13),
                    // 그 밖(빈 끝 · 짧은 막힌 끝 — 곧게 또는 끝 법선)은 팁 벡터·u
                    val end4 = m4a.last { it.blk < iExit }.seg.end
                    val end0 = m0a.last { !it.rapid }.seg.end
                    val tvu = tv dot g.uB
                    val got = (end4 - end0) dot g.uB
                    val outerAxis = !horiz(g.uB) && g.uB.x < 0 && g.l.x <= 1e-9
                    // 짧은 막힌 끝 요소를 계산기가 뺐으면(마지막 형상 줄 끝이 끝좌표가 아님 — 앞 요소가 직선일 때, calc-design '진입점과 NC' 2026-11-04) 노즈는 그 요소에
                    // 닿지 않고 앞 요소 끝 둘레에서 멈춘다 — 문서 식 대신 노즈가 벽(끝좌표의 법선)에서 r 이상 앞인지 본다
                    val skipped = shortEnd && g.endWall && (parse(nc4).last { it.idx < iExit }.p - g.l).length > 1e-3
                    val want = if (g.endWall && !shortEnd) tvu - r else if (outerAxis) (if (c.inner) 0.0 else min(0.0, tvu)) else tvu
                    val onLine = abs(((end4 - g.l) dot g.nB) - r) <= tol
                    val ahead = (end4 - g.l) dot g.uB
                    val ok = if (skipped) ahead <= -r + tol else abs(got - want) <= tol && onLine
                    endTable.add(
                        "끝 ${if (horiz(g.uB)) "수평" else "단면"}${if (skipped) "(짧은 막힘 · 뺌)" else if (g.endWall && !shortEnd) "(막힘)" else if (outerAxis) "(X0)" else ""} 팁·u ${cls(tvu)} · " +
                            (if (ok) "문서 식대로" else if (skipped) "문서 식과 다름(노즈가 벽에서 ${f4(-ahead)} — r 보다 가까움)" else "문서 식과 다름(G4x − G40 = ${f4(got)}, 식 ${f4(want)}, 보정선 위 $onLine)") + " · $path",
                        ex4(),
                    )
                }

                // 소재 깎기
                for ((k, ra) in mapOf("직선" to run4.getValue("A·직선"), "꺾임" to run4.getValue("A·꺾임"))) {
                    val rb = run0.getValue(k)
                    if (ra.stop != null || rb.stop != null) continue
                    val sa = stockOf(ra, g, r, tip)
                    val sb = stockOf(rb, g, r, tip)
                    val (dd, zAt) = profDiff(sa, sb, g, r + 0.05)
                    (if (k == "직선") stockLin else stockDog).see(dd, cat, key) { "Z${f4(zAt)} · ${ex4()}" }
                    if (dd > tol) {
                        val why = when {
                            slA > tol -> "시작 첫 도면점 남김(위 표)"
                            abs(rd4 - rd0) > 0.01 -> "깎지 않는 요소를 타고 간 길이가 다름"
                            else -> "그 밖"
                        }
                        stockWhy.add("$k · $why · $key", "Z${f4(zAt)} 차이 ${f4(dd)} · ${ex4()}")
                        if (why == "그 밖") (if (k == "직선") stockOtherLin else stockOtherDog).see(dd, cat, key) { "Z${f4(zAt)} · ${ex4()}" }
                    }
                    val c4 = sa.contacts.filter { it.kind == ContactKind.RAPID_CUT }
                    val c0 = sb.contacts.filter { it.kind == ContactKind.RAPID_CUT }
                    if (c4.size > c0.size) {
                        val where4 = c4.joinToString { "'${nc4.getOrElse(it.line - pre.size - 1) { "?" }}' ${f4(it.area)}mm²" }
                        rapidCut.add("$k · G4x ${c4.size} · G40 ${c0.size} · $key", "$where4 | ${ex4()}")
                    }
                }
            } catch (e: Throwable) {
                errors++
                if (errEx.size < 5) errEx += "${e::class.simpleName}: ${e.message} | ${c.tag}"
            }
        }

        val sec = clock.elapsedNow().inWholeMilliseconds / 1000.0
        println("=== VerifyG4xSimTest — 씨앗 20261029 · 만든 입력 ${all.size} · 풀린 사례 ${solved.size} · ${sec}초 ===")
        println("계산기가 멈춘 입력: $calcStops")
        println("각도 넣은 사례: X 기준 ${solved.count { it.angleRefX == true }} · Z 기준 ${solved.count { it.angleRefX == false }}")
        println(
            "갈래: 겹 ${solved.count { it.dbl }} · 내경 ${solved.count { it.inner }} · 계산기역(화면 없음) ${solved.count { it.reverse }} · 화면역(거울) ${solved.count { it.mirror }}" +
                " · 뒤집기 ${solved.count { it.flip }} · 표준 팁 ${solved.count { it.stdTip }} · 이동 없는 G40 $aloneCount",
        )
        println("예외 $errors ${errEx.joinToString("\n  ", prefix = "\n  ")}")
        println("| 검사 | 사례 | 0.001 넘음 | 그 가운데 화면 경로 | 최대(mm) | 화면 경로 최대 |")
        println("|---|---|---|---|---|---|")
        val tables = vars.flatMap { listOf(endLeft.getValue(it), startLeft.getValue(it), endLeftWall.getValue(it), startLeftWall.getValue(it), anyLeft.getValue(it), gouge4.getValue(it)) } + gouge0.values +
            vars.flatMap { listOf(endOver4.getValue(it), endOverStop.getValue(it), endOverD.getValue(it), startOver4.getValue(it), startOverD.getValue(it)) } +
            listOf(endOver0, startOver0, g00Beyond4, g00Beyond0, g00BeyondD, approach4, approach0, approachD, ride4, ride0, stockLin, stockDog, stockOtherLin, stockOtherDog, abDiff, modelVsSim, startLeftKey, endOverKey, endOverKey0, rapidRide4, rapidRide0, shortEndOver, mirrorApproach, shortEndGouge, narrowGouge)
        for (a in tables) println(a.line())
        println("시작 · 취소가 바깥 모서리(형식 A · B 가 다를 자리, 따로 짠 모형): $outerSC · GSim COMP_TYPE_A INFO: $typeAInfo")
        for (a in tables) if (a.hits.isNotEmpty() && !a.name.endsWith("(A·꺾임)")) {
            println("-- ${a.name}: ${a.hits.size}")
            for (l in a.detail()) println(l)
        }
        for (cnt in listOf(stops, stopsNoWarn, endTable, rapidCut, stockWhy)) {
            println("-- ${cnt.name}: ${cnt.m.values.sum()}")
            for ((k, v) in cnt.m.entries.sortedByDescending { it.value }.take(30)) println("    ${v}개 · $k — ${cnt.ex[k]}")
        }

        // 지금 성립하는 것만 단언한다(2026-10-29 검증 보고 → 판정 · 고침 뒤 — simulator-design.md 13.9). 출력만 하는 것: 막힌 끝 · 시작의 남김
        // (G41/G42 는 벽에서 멈추고 직접 보정은 팁에 따라 너머를 깎음), 경고 없는 멈춤(대부분 팁 5~9 의 짧은 요소 · 겹테이퍼 예각 — 고치기 전에도 있던 것),
        // 화면 역방향 뒤집기의 첫 G00 앞쪽 접근, 끝 노즈 표
        assertTrue(solved.size > 3000, "풀린 사례 ${solved.size}")
        assertTrue(errors == 0, "예외 $errors: ${errEx.joinToString("\n")}")
        // 따로 짠 제어기 모형(형식 A)이 GSim 과 같은 노즈 경로 — 이동 없는 G40 그림(FF 그림 5.4.4(e)) 포함
        assertTrue(modelVsSim.n > 3000 && modelVsSim.hits.isEmpty(), modelVsSim.line() + "\n" + modelVsSim.detail().joinToString("\n"))
        // 계산기 글의 시작 · 취소가 바깥 모서리가 아니라 형식 A · B 가 같다
        assertTrue(abDiff.n > 3000 && abDiff.hits.isEmpty(), abDiff.line() + "\n" + abDiff.detail().joinToString("\n"))
        for (v in vars) {
            // 13.7 고침: G41/G42 글이 도면을 파고들지 않음(급송 직선 · 축마다, 형식 A · B), 끝 꼭짓점에 직접 보정보다 살이 더 남지 않음
            val gg = gouge4.getValue(v)
            val el = endLeft.getValue(v)
            assertTrue(gg.n > 3000 && gg.hits.isEmpty(), gg.line() + "\n" + gg.detail().joinToString("\n"))
            assertTrue(el.n > 2000 && el.hits.isEmpty(), el.line() + "\n" + el.detail().joinToString("\n"))
            // 판정 고침(13.9): 너머가 빈 시작의 첫 도면점에 직접 보정보다 살이 더 남지 않음, 막힌 끝에서 G41/G42 노즈가 끝좌표 너머로 가지 않고(교점으로 멈춘 글)
            // 직접 보정보다 더 가지 않음
            val sl = startLeft.getValue(v)
            val eo = endOverStop.getValue(v)
            val ed = endOverD.getValue(v)
            assertTrue(sl.n > 2000 && sl.hits.isEmpty(), sl.line() + "\n" + sl.detail().joinToString("\n"))
            assertTrue(eo.n > 300 && eo.hits.isEmpty(), eo.line() + "\n" + eo.detail().joinToString("\n"))
            // (계산기 역방향 — 화면에 없음 — 의 볼록 띄운 시작 G00 이 경사에서 c + lift 떨어져 끝좌표 너머에 놓이는 겹치는 모양 하나는 빼고 — 13.9 남은 것)
            assertTrue(ed.hits.none { !it.key.startsWith("계산기역") }, ed.line() + "\n" + ed.detail().joinToString("\n"))
        }
        for (a in gouge0.values) assertTrue(a.hits.isEmpty(), a.line() + "\n" + a.detail().joinToString("\n"))
        // 끝 노즈 자리가 문서 식(막힌 끝 · 바깥 단면 · 그 밖 — 13.9)대로이고 끝 법선 · 교점 위
        val off = endTable.m.filterKeys { "문서 식과 다름" in it }
        assertTrue(off.isEmpty(), off.entries.joinToString("\n") { "${it.value}개 ${it.key} — ${endTable.ex[it.key]}" })
    }

    /** 진단(시험 아님 — 직접 부른다): 사례 하나의 두 글 · GSim 노즈 경로 · 끝 · 첫 점 남김 */
    fun diag() {
        val list = listOf(
            // 손 예: 뒤집기 외경 형상 2(칸 T1 = 그림 왼쪽 X50 Z-5, T2 = X40 Z0), 시작 = 뒤 단면 위 X60, 끝 = 앞 단면 X30, 0.8R 표준 팁
            TaperInput(false, 2, false, 0.8, null, 50.0, -5.0, 40.0, 0.0, null, 1.0, start = 60.0, end = 30.0, flip = true),
            // 뒤집기 내경 형상 4, 시작 단면이 노즈 R 보다 짧음(0.357 < 0.4) — 표준 팁 · 경고 없음
            TaperInput(true, 4, false, 0.4, null, 10.417, null, 46.301, -4.505, 77.517, 0.3, start = 9.703, end = null, flip = true),
            // 경고 없이 COMP_INTERFERENCE(팁 9, 비운 시작 단면의 띄운 진입 + 모서리 R0.2)
            TaperInput(false, 1, false, 1.0, 9, 115.959, -1.751, 299.33, null, 83.599, 0.0, cornerR1 = 0.2, start = null, end = -26.408),
            // 막힌 끝 너머(팁 6, 정방향 외경 형상 3, 끝 Z-7.877 척 쪽)
            TaperInput(false, 3, false, 1.0, 6, 40.66, 4.331, 42.058, 2.033, null, 2.0, start = null, end = -7.877),
        )
        for (inp in list) {
            val res = TaperCalc.calc(inp)
            val s = res.solution ?: run { println("멈춤 ${res.warnings}"); null } ?: continue
            val r = inp.noseR
            val tv = NoseComp.tipVector(s.tip, r)
            println("=== $inp\n경고 ${res.warnings} 팁 ${s.tip} tv $tv side ${s.side} fromStart ${s.fromStart} toEnd ${s.toEnd}")
            println("G4x: ${s.ncG41G42.joinToString(" / ")}")
            println("G40: ${s.ncG40.joinToString(" / ")}")
            println("도면: " + s.drawing.segs.joinToString(" | ") { "${f4(it.start.z)},${f4(it.start.xDia)}→${f4(it.end.z)},${f4(it.end.xDia)}" })
            var cut = s.drawing.segs
            if (!s.fromStart) cut = cut.drop(1)
            if (!s.toEnd) cut = cut.dropLast(1)
            val f = cut.first().start
            val l = cut.last().end
            for ((name, nc) in listOf("G4x" to s.ncG41G42, "G40" to s.ncG40)) {
                val run = simRun(nc, r, s.tip, RapidPath.LINEAR)
                val mv = movesOf(run, parse(nc)[0].idx)
                if (mv.isNotEmpty()) {
                    println(
                        "  $name 첫 도면점 Z${f4(f.z)} X${f4(f.xDia)} 남김 ${f4(max(0.0, mv.minOf { dist(f, it.seg) } - r))} · " +
                            "끝 꼭짓점 Z${f4(l.z)} X${f4(l.xDia)} 남김 ${f4(max(0.0, mv.minOf { dist(l, it.seg) } - r))}",
                    )
                }
                println("  $name 멈춤 ${run.stop} 문제 ${run.issues.map { it.code }}")
                for (m in movesOf(run, -1)) println("    [${m.blk}] ${nc[m.blk]} : 노즈 ${f4(m.seg.start.z)},${f4(m.seg.start.x)} → ${f4(m.seg.end.z)},${f4(m.seg.end.x)}${if (m.rapid) " G00" else ""}")
            }
            val cs = Case(inp.toString(), r, res, inp.inner, false, inp.type, inp.reverse, inp.flip, !inp.approach, inp.tip == null, null)
            val g = geoOf(s, cs)
            val ra = simRun(s.ncG41G42, r, s.tip, RapidPath.LINEAR)
            val rb = simRun(s.ncG40, r, s.tip, RapidPath.LINEAR)
            if (ra.stop == null && rb.stop == null) {
                val sa = stockOf(ra, g, r, s.tip)
                val sb = stockOf(rb, g, r, s.tip)
                val (dd, zAt) = profDiff(sa, sb, g, r + 0.05)
                println("  StockSim 차이 ${f4(dd)} @Z${f4(zAt)} · 급송 깎음 G4x ${sa.contacts.filter { it.kind == ContactKind.RAPID_CUT }.map { it.line }} G40 ${sb.contacts.filter { it.kind == ContactKind.RAPID_CUT }.map { it.line }}")
                for (dz in listOf(-0.02, -0.01, 0.0, 0.01, 0.02)) {
                    val z = zAt + dz
                    val col = ((z - sa.chuckZ) / sa.dz).toInt().coerceIn(0, sa.columns - 1)
                    println("    Z${f4(z)}: 구간 G4x ${sa.intervals(col).toList().map { f4(it) }} · G40 ${sb.intervals(col).toList().map { f4(it) }}")
                }
            }
        }
    }

    private fun hausdorff(a: List<NMove>, b: List<NMove>): Double {
        if (a.isEmpty() || b.isEmpty()) return if (a.isEmpty() && b.isEmpty()) 0.0 else Double.MAX_VALUE
        fun one(p: List<NMove>, q: List<NMove>) = p.flatMap { pts(it.seg, 0.05) }.maxOf { x -> q.minOf { dist(x, it.seg) } }
        return max(one(a, b), one(b, a))
    }

    /** 노즈 원 공구(ToolKind.ROUND)로 깎은 소재. 원점에서 오는 이동은 뺀다. 소재 = 도면을 감싼 봉(내경은 관) — 척 검사는 끔 */
    private fun stockOf(run: SimRun, g: Geo, r: Double, tip: Int): StockSim {
        val home = firstLineOf(run)
        val steps = run.steps.filter { !(it is MoveStep && it.line - pre.size - 1 == home) }
        val faceZ = if (g.mirror) g.zMax + 20 else g.zMax
        val length = if (g.mirror) g.zMax + 20 - g.zMin else g.zMax - g.zMin + 20
        val spec = if (g.inner) StockSpec(2 * g.xMax + 20, length, faceZ, max(0.0, 2 * g.xMin), 0.0) else StockSpec(2 * g.xMax, length, faceZ, 0.0, 0.0)
        val st = StockSim(spec, run.copy(steps = steps), mapOf(1 to ToolOffset(r, tip, ToolKind.ROUND)))
        st.cutTo(run.totalSec + 1)
        return st
    }

    /** 원점에서 오는 이동의 글 줄(0부터) — 안전점 줄 */
    private fun firstLineOf(run: SimRun): Int = run.steps.filterIsInstance<MoveStep>().first { it.line > pre.size }.line - pre.size - 1

    /**
     * 두 소재의 마지막 단면(외경은 바깥 반경, 내경은 안 반경)을 열마다 꺾은선으로 보고 서로의 거리 가운데 가장 큰 것과 그 Z.
     * 다듬은 면만 본다 — 단면 점이 깎는 도면에서 [band] 안인 열(깎지 않는 요소 위 · 진입 · 후퇴 길의 덩어리 소재는 소재 가정에 달려 빼다)
     */
    private fun profDiff(a: StockSim, b: StockSim, g: Geo, band: Double): Pair<Double, Double> {
        val zs = (0 until a.columns).map { a.zOf(it) }.filter { it >= g.zMin - 2 && it <= g.zMax + 2 }
        fun v(s: StockSim, z: Double) = if (g.inner) (s.innerAt(z) ?: (g.xMax + 10)) else (s.outerAt(z) ?: 0.0)
        val pa = zs.map { Pt(it, v(a, it)) }
        val pb = zs.map { Pt(it, v(b, it)) }
        val uncut = g.bnd.filter { it !in g.cut }
        fun near(p: Pt): Boolean {
            val dc = g.cut.minOf { dist(p, it) }
            return dc <= band && dc <= uncut.minOf { dist(p, it) } + 1e-9
        }
        fun one(p: List<Pt>, q: List<Pt>): Pair<Double, Double> {
            var worst = 0.0
            var wz = 0.0
            for (i in p.indices) {
                if (abs(p[i].x - q[i].x) <= 1e-4 || !(near(p[i]) || near(q[i]))) continue
                var best = Double.MAX_VALUE
                for (j in max(0, i - 60) until min(q.size - 1, i + 60)) best = min(best, dist(p[i], Line(q[j], q[j + 1])))
                if (best > worst) {
                    worst = best
                    wz = p[i].z
                }
            }
            return worst to wz
        }
        if (pa.size < 2) return 0.0 to 0.0
        val x = one(pa, pb)
        val y = one(pb, pa)
        return if (x.first >= y.first) x else y
    }
}
