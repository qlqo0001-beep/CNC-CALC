package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Intersect
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.wrap2Pi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 고정 사이클 펼치기(`docs/simulator-design.md` 1.6 · 12절) — 기하만. 블록 읽기 · 모달 · 시간 · 보정은 [GSim] 이 쥔다.
 * 점은 모두 실제 인선(가상 인선) 자리, x 는 반경. 출처 약칭: S = Siemens SINUMERIK ISO Turning 04/2007(FANUC 2줄 형식),
 * H = Haas ST/TL 워크북 2015(인쇄 쪽).
 */
internal object GCycles {
    /** 사이클이 만든 이동 하나. [cmd] = 그 이동의 명령 경로(보정 전 — null 이면 [seg] 와 같음) */
    data class Mov(val seg: Seg, val kind: MoveKind, val src: StepSrc, val pass: Int?, val model: Boolean, val cmd: Seg? = null)

    /** P~Q 윤곽 한 토막: 실제 인선 경로 · 명령 경로 · G00 블록인지 · 편집기 줄 · 끼운 모서리인지 */
    data class Piece(val tip: Seg, val cmd: Seg, val rapid: Boolean, val line: Int, val corner: Boolean = false)

    sealed interface Res
    data class Ok(val moves: List<Mov>) : Res

    /** [line] null = 사이클 블록 줄 */
    data class Bad(val code: IssueCode, val line: Int? = null, val args: List<String> = emptyList()) : Res

    /** 방향 · 부호를 볼 때 0 으로 치는 길이(입력 최소 단위의 반 — 보정 GComp.REVERSE 와 같다) */
    const val TOL = 5e-4

    private const val E = 1e-9

    /** 한 사이클이 만들 수 있는 층 · 쪼기 · 패스 수(잘못 친 작은 절입으로 끝없이 늘지 않게) */
    private const val MAX_STEPS = 50_000

    // ================================================================ G90 · G92 · G94 (S 4-91~4-100, H 127~132)

    /**
     * 단일형 한 번. [a] = 시작점 A(사이클 블록 실행 때 자리), [b] = 끝점 B(X · Z), [taper] = R(G90 · G92 는 X 반경, G94 는 Z):
     * 부호는 B 에서 A′(1 단계가 끝나는 점)를 보는 방향(S 4-93 · 4-96 · 4-100).
     * - G90: ① 급송 X → A′ = (A.z, B.x + R) ② 이송 → B ③ 이송 X → A.x ④ 급송 Z → A (S 그림 4-1 · 4-3)
     * - G94: ① 급송 Z → A′ = (B.z + R, A.x) ② 이송 → B ③ 이송 Z → A.z ④ 급송 X → A (S 그림 4-11 · 4-13)
     * - G92: ① 급송 X → A′ ② 나사 → B(끝 빼기 [chamfer] > 0 이면 끝에서 약 45° — S 그림 4-6) ③ 급송 X → A.x ④ 급송 Z → A
     */
    fun single(g: Int, a: Pt, b: Pt, taper: Double, chamfer: Double, pass: Int): List<Mov> {
        val o = Out(a)
        when (g) {
            90 -> {
                o.to(Pt(a.z, b.x + taper), MoveKind.RAPID, StepSrc.CYCLE_APPROACH, pass, false)
                o.to(b, MoveKind.FEED, StepSrc.CYCLE_CUT, pass, false)
                o.to(Pt(b.z, a.x), MoveKind.FEED, StepSrc.CYCLE_RETRACT, pass, false)
                o.to(a, MoveKind.RAPID, StepSrc.CYCLE_RETURN, pass, false)
            }
            94 -> {
                o.to(Pt(b.z + taper, a.x), MoveKind.RAPID, StepSrc.CYCLE_APPROACH, pass, false)
                o.to(b, MoveKind.FEED, StepSrc.CYCLE_CUT, pass, false)
                o.to(Pt(a.z, b.x), MoveKind.FEED, StepSrc.CYCLE_RETRACT, pass, false)
                o.to(a, MoveKind.RAPID, StepSrc.CYCLE_RETURN, pass, false)
            }
            else -> {
                val s = Pt(a.z, b.x + taper)
                o.to(s, MoveKind.RAPID, StepSrc.CYCLE_APPROACH, pass, false)
                threadCut(o, s, b, chamfer, sgn(a.x - b.x), a.x, pass, false)
                o.to(Pt(o.at.z, a.x), MoveKind.RAPID, StepSrc.CYCLE_RETRACT, pass, false)
                o.to(a, MoveKind.RAPID, StepSrc.CYCLE_RETURN, pass, false)
            }
        }
        return o.list
    }

    /**
     * 나사 한 줄 s → e. [chamfer] > 0 이면 끝 Z 앞 [chamfer] 에서 시작해 끝 Z 에서 [out] 쪽으로 [chamfer] 만큼 빠진 점까지 비스듬히
     * (S 4-95 그림 4-6 '약 45°', H 111 설정 95 · 96 — 크기 · 각은 기계마다라 **모형**). 빠지는 X 는 시작점 X([startX])를 넘지 않는다
     * (넘으면 뒤의 급송 X 가 공작물 쪽으로 되돌아온다 — 우리가 정함).
     */
    private fun threadCut(o: Out, s: Pt, e: Pt, chamfer: Double, out: Int, startX: Double, pass: Int, model: Boolean) {
        val lz = abs(e.z - s.z)
        if (chamfer <= E || out == 0 || lz <= E) {
            o.to(e, MoveKind.THREAD, StepSrc.CYCLE_CUT, pass, model)
            return
        }
        val c = min(chamfer, lz)
        val lift = min(c, max(0.0, out * (startX - e.x)))
        o.to(s + (e - s) * (1 - c / lz), MoveKind.THREAD, StepSrc.CYCLE_CUT, pass, model)
        o.to(Pt(e.z, e.x + out * lift), MoveKind.THREAD, StepSrc.CYCLE_CUT, pass, true)
    }

    // ================================================================ G71 · G72 (S 4-102~4-105, H 78~80 · 92~94)

    /**
     * 형식 I 황삭. [a] = 시작점 A, [pEnd] = P 블록 끝 A′(실제 인선), [contour] = A′→B 윤곽(실제 인선 — P~Q 를 보정 모형으로 미리 돌린 것),
     * [d] = 절입 Δd(반경), [e] = 후퇴량, [off] = 다듬질 여유 (Δw, Δu/2), [g72] = 단면 방향, [pRapid] = P 블록이 G00.
     *
     * G71 을 기준으로 적는다(G72 는 X ↔ Z): f = 층이 나아가는 축(G71 X), c = 깎는 축(G71 Z).
     * 1. A′→B 프로그램 윤곽(보정 전)이 두 축 모두 한 방향(S 4-103), f 는 A′ 에서 A 쪽으로 돌아와야 한다 → 아니면 G71_NOT_MONOTONE(그 블록 줄).
     * 2. 여유 부호(S 4-103 그림 4-16): f 여유는 윤곽이 A 로 가는 쪽, c 여유는 깎는 방향의 반대 → 아니면 G7X_SIGN.
     * 3. 윤곽′ = 윤곽 + off(평행 이동 — S 그림 4-15 의 u/2 · w). 층 L_k = f(A) + s·k·Δd 가 윤곽′ 의 A′ 높이 앞인 동안,
     *    **마지막 층 = A′′ 높이**(앞 층과 같지 않으면 — 우리가 정함).
     * 4. 층마다(H 80): c(A) 에서 L 로 절입(P 블록 모드 — H 80) → 이송으로 윤곽′ 이 L 을 A 쪽으로 넘는 첫 점까지 →
     *    45° 로 e 후퇴(f · c 모두 e — S 그림 4-15 '(F) 45°') → 급송으로 c(A). 윤곽′ 이 L 을 끝까지 넘지 않으면 B′ 의 c 까지.
     *    층 길이가 0 이면(윤곽′ 이 이미 c(A) 앞에서 L 을 넘음) 그 층과 더 깊은 층은 없다.
     * 5. 여유 패스(H 80 'moved along the tool path to clean up'): P 블록 모드로 A′′ → 윤곽′ 를 블록 모드대로(G00 블록은 급송) B′ → 급송 A(H 79).
     * 층 이동(1~4)과 5 의 진입은 **모형**, 윤곽′ 패스 · 복귀는 정확(남는 모양 = 윤곽′).
     */
    fun rough(a: Pt, pEnd: Pt, contour: List<Piece>, d: Double, e: Double, off: Pt, g72: Boolean, pRapid: Boolean): Res {
        val ax = Ax(g72)
        val fName = if (g72) "Z" else "X"
        val cName = if (g72) "X" else "Z"
        // 1. 단조 — 프로그램 윤곽(보정 전, 끼운 모서리 포함)으로 본다(S 4-103 'The contour defined between A' and B').
        //    보정한 실제 인선은 바깥 모서리 바로 뒤 오목 원호에서 노즈 반경 만큼 되돌아갈 수 있다(H 95 O00096 N13 → N14) — 층은 그 경로로 잡는다
        var fDir = 0
        var cDir = 0
        for (pc in contour) {
            val df = dir(pc.cmd, !g72) ?: return Bad(IssueCode.G71_NOT_MONOTONE, pc.line, listOf(fName))
            val dc = dir(pc.cmd, g72) ?: return Bad(IssueCode.G71_NOT_MONOTONE, pc.line, listOf(cName))
            if (df != 0) {
                if (fDir == 0) fDir = df else if (df != fDir) return Bad(IssueCode.G71_NOT_MONOTONE, pc.line, listOf(fName))
            }
            if (dc != 0) {
                if (cDir == 0) cDir = dc else if (dc != cDir) return Bad(IssueCode.G71_NOT_MONOTONE, pc.line, listOf(cName))
            }
        }
        if (cDir == 0) return Bad(IssueCode.G71_NOT_MONOTONE, contour.first().line, listOf(cName))
        val s = sgnTol(ax.f(pEnd) - ax.f(a))
        if (s != 0 && fDir == s) {
            val bad = contour.first { dir(it.cmd, !g72) == s }
            return Bad(IssueCode.G71_NOT_MONOTONE, bad.line, listOf(fName))
        }
        // 2. 여유 부호
        val af = ax.f(off)
        val ac = ax.c(off)
        val wantF = if (fDir != 0) fDir else -s
        if (wantF != 0 && af * wantF < -TOL) return Bad(IssueCode.G7X_SIGN, null, listOf(if (g72) "W" else "U"))
        if (ac * cDir > TOL) return Bad(IssueCode.G7X_SIGN, null, listOf(if (g72) "U" else "W"))

        // 3. 윤곽′ · 층
        val cont2 = contour.map { Piece(shift(it.tip, off), shift(it.cmd, off), it.rapid, it.line, it.corner) }
        val a2 = pEnd + off
        val sA = -s
        val o = Out(a)
        val pKind = if (pRapid) MoveKind.RAPID else MoveKind.FEED
        var pass = 0
        if (s != 0 && s * (ax.f(a2) - ax.f(a)) > E) {
            val levels = ArrayList<Double>()
            var k = 1
            while (true) {
                val lv = ax.f(a) + s * k * d
                if (s * (ax.f(a2) - lv) <= E) break
                levels += lv
                if (levels.size > MAX_STEPS) return Bad(IssueCode.EXEC_LIMIT)
                k++
            }
            if (levels.isEmpty() || abs(levels.last() - ax.f(a2)) > E) levels += ax.f(a2)
            // 4. 층
            val c0 = ax.c(a)
            for (lv in levels) {
                val ce = crossing(cont2, lv, sA, ax) ?: ax.c(cont2.last().tip.end)
                if (cDir * (ce - c0) <= E) break
                pass++
                o.to(ax.pt(lv, c0), pKind, StepSrc.CYCLE_APPROACH, pass, true)
                o.to(ax.pt(lv, ce), MoveKind.FEED, StepSrc.CYCLE_CUT, pass, true)
                o.to(ax.pt(lv + sA * e, ce - cDir * e), MoveKind.FEED, StepSrc.CYCLE_RETRACT, pass, true)
                o.to(ax.pt(lv + sA * e, c0), MoveKind.RAPID, StepSrc.CYCLE_RETURN, pass, true)
            }
        }
        // 5. 여유 패스 · 복귀
        pass++
        o.to(a2, pKind, StepSrc.CYCLE_APPROACH, pass, true)
        for (pc in cont2) o.seg(pc.tip, if (pc.rapid) MoveKind.RAPID else MoveKind.FEED, StepSrc.CYCLE_FINISH, pass, false, pc.cmd)
        o.to(a, MoveKind.RAPID, StepSrc.CYCLE_RETURN, null, false)
        return Ok(o.list)
    }

    /** 윤곽′ 에서 f 가 처음으로 [lv] 를 A 쪽([sA])으로 넘는 점의 c. 끝까지 넘지 않으면 null */
    private fun crossing(cont: List<Piece>, lv: Double, sA: Int, ax: Ax): Double? {
        for (pc in cont) {
            val sg = pc.tip
            val f0 = ax.f(sg.start)
            val f1 = ax.f(sg.end)
            if (sA * (f1 - lv) <= E) continue
            if (sA * (f0 - lv) >= -E) return ax.c(sg.start)
            return when (sg) {
                is Line -> ax.c(sg.start) + (ax.c(sg.end) - ax.c(sg.start)) * ((lv - f0) / (f1 - f0))
                is Arc -> arcAt(sg, lv, ax) ?: ax.c(sg.end)
            }
        }
        return null
    }

    /** 원호 위에서 f = lv 인 첫 점의 c(원호는 f 로 한 방향 — 단조 검사를 지남) */
    private fun arcAt(a: Arc, lv: Double, ax: Ax): Double? {
        val a0 = angleOf(a.start - a.center)
        val sw = a.sweepRad()
        val dir = if (a.cw) -1.0 else 1.0
        return Intersect.lineCircle(ax.pt(lv, 0.0), ax.pt(0.0, 1.0), a.center, a.radius)
            .map { p ->
                val u = wrap2Pi(dir * (angleOf(p - a.center) - a0))
                p to (if (u > 2 * PI - 1e-9) 0.0 else u)
            }
            .filter { it.second <= sw + 1e-9 }
            .minByOrNull { it.second }
            ?.let { ax.c(it.first) }
    }

    // ================================================================ G73 (S 4-106~4-107, H 98)

    /**
     * 형상 반복. [di] · [dk] = 첫 패스와 마지막 패스 사이(반경 X · Z — H 98 I · K, S U(Δi) · W(Δk)), [n] = 패스 수(≥ 2),
     * [off] = 다듬질 여유 (Δw, Δu/2). 패스 j(0 … n−1)의 평행 이동 off_j = (Δk, Δi)·(n−1−j)/(n−1) + off — 첫 패스 = 여유 + (Δk, Δi),
     * 마지막 = 여유(H 98 · S 그림 4-20). 움직임(H 98): P 블록 모드로 A′ + off_j → 윤곽 + off_j 를 블록 모드대로 Q 까지 →
     * 급송으로 A + off_(j+1)(H 98 'rapid departure … to the start position plus the offset for the second pass') → … → 마지막 패스 뒤 급송 A.
     * 첫 패스는 A 에서 바로 A′ + off_0 으로(H 98 'rapids or feeds to the first rough cut'). 진입 · 마지막 앞 패스는 **모형**(간격이 고르다고 봄).
     */
    fun pattern(a: Pt, pEnd: Pt, contour: List<Piece>, di: Double, dk: Double, n: Int, off: Pt, pRapid: Boolean): List<Mov> {
        fun offAt(j: Int): Pt {
            val k = (n - 1 - j).toDouble() / (n - 1)
            return Pt(dk * k + off.z, di * k + off.x)
        }
        val o = Out(a)
        val pKind = if (pRapid) MoveKind.RAPID else MoveKind.FEED
        for (j in 0 until n) {
            val oj = offAt(j)
            val last = j == n - 1
            o.to(pEnd + oj, pKind, StepSrc.CYCLE_APPROACH, j + 1, true)
            for (pc in contour) {
                o.seg(shift(pc.tip, oj), if (pc.rapid) MoveKind.RAPID else MoveKind.FEED, StepSrc.CYCLE_CUT, j + 1, !last, shift(pc.cmd, oj))
            }
            o.to(if (last) a else a + offAt(j + 1), MoveKind.RAPID, StepSrc.CYCLE_RETURN, j + 1, false)
        }
        return o.list
    }

    // ================================================================ G74 · G75 (S 4-112~4-113, H 102~108)

    /**
     * 쪼아 들어가기. G74(단면 홈 · 드릴) = Z 로 쪼고 X 로 옮김, G75(외경 · 내경 홈) = X 로 쪼고 Z 로 옮김.
     * [a] = 시작점, [end] = 끝(쪼는 축의 끝 깊이 · 옮기는 축의 끝 자리), [shift] = 옮김 간격(G74 Δi · G75 Δk, mm),
     * [depth] = 한 번 깊이(G74 Δk · G75 Δi, mm), [e] = 쫄 때마다 되돌아가는 양.
     * - 자리: 시작 자리부터 [shift] 간격, 끝 자리에 꼭 맞추고 **마지막 간격만 짧다**(H 102 · 106). [shift] 0 이면 시작 · 끝 두 자리(H 102 'a minimum of two').
     * - 한 자리: 이송 depth → 급송 e 되돌아감 → 이송 (e + 다음 깊이) … 끝 깊이(마지막 한 번은 짧다) → 급송 시작 높이(H 102 · 106 · S 그림 4-25).
     * - 자리 사이는 시작 높이에서 급송, 끝나면 급송 A. 바닥 도피 Δd 는 부르는 쪽이 막는다(G7X_RELIEF).
     */
    fun peck(g75: Boolean, a: Pt, end: Pt, shift: Double, depth: Double, e: Double): Res {
        fun pv(p: Pt) = if (g75) p.x else p.z
        fun qv(p: Pt) = if (g75) p.z else p.x
        fun mk(p: Double, q: Double) = if (g75) Pt(q, p) else Pt(p, q)
        val p0 = pv(a)
        val q0 = qv(a)
        val qE = qv(end)
        val cols = arrayListOf(q0)
        if (abs(qE - q0) > E) {
            val sd = sgn(qE - q0)
            if (shift > E) {
                var m = 1
                while (true) {
                    val qm = q0 + sd * m * shift
                    if (sd * (qE - qm) <= E) break
                    cols += qm
                    if (cols.size > MAX_STEPS) return Bad(IssueCode.EXEC_LIMIT)
                    m++
                }
            }
            cols += qE
        }
        val total = abs(pv(end) - p0)
        val pd = sgn(pv(end) - p0)
        val depths = ArrayList<Double>()
        if (depth <= E) depths += total
        else {
            var k = 1
            while (true) {
                val dk = k * depth
                if (dk >= total - E) {
                    depths += total
                    break
                }
                depths += dk
                if (depths.size * cols.size > MAX_STEPS) return Bad(IssueCode.EXEC_LIMIT)
                k++
            }
        }
        val o = Out(a)
        for ((ci, qc) in cols.withIndex()) {
            val pass = ci + 1
            o.to(mk(p0, qc), MoveKind.RAPID, StepSrc.CYCLE_APPROACH, pass, false)
            for ((k, dk) in depths.withIndex()) {
                o.to(mk(p0 + pd * dk, qc), MoveKind.FEED, StepSrc.CYCLE_CUT, pass, false)
                if (k < depths.lastIndex) o.to(mk(p0 + pd * (dk - e), qc), MoveKind.RAPID, StepSrc.CYCLE_RETRACT, pass, false)
            }
            o.to(mk(p0, qc), MoveKind.RAPID, StepSrc.CYCLE_RETURN, pass, false)
        }
        o.to(a, MoveKind.RAPID, StepSrc.CYCLE_RETURN, null, false)
        return Ok(o.list)
    }

    // ================================================================ G76 (S 4-114~4-117, H 109~111)

    /**
     * 나사 복합. [a] = 시작점, [b] = 나사 끝(골 — X · Z), [taper] = i(반경, 부호는 G92 R 과 같음), [k] = 나사 높이, [first] = 첫 절입 Δd,
     * [dmin] = 최소 절입 Δdmin, [fin] = 다듬질 여유 d, [m] = 다듬질 횟수, [chamfer] = 끝 빼기 길이(r·L/10), [angleDeg] = 공구 각 a.
     * - 깊이(산에서): d_n = max(Δd·√n, d_(n−1) + Δdmin)(S 4-115~4-116: n 번째 Δd√n, 한 번 깊이가 Δdmin 보다 작으면 Δdmin).
     *   d_n ≥ k − d 이면 그 층을 k − d 로 자르고(우리가 정함) 거친 절입 끝. 그 뒤 깊이 k 로 m 번(S 4-115).
     * - 패스마다: 급송으로 깎을 자리 → 나사 이송 끝까지(끝 빼기) → 급송 X 시작 높이 → 급송 Z 시작(S 그림 4-28).
     * - 한쪽 날 진입(S 4-116 주 2): 패스의 Z 시작을 (d_n − d_1)·tan(a/2) 만큼 나사 시작 쪽으로 옮긴다 — **모형**(모든 이동을 모형으로 표시).
     */
    fun thread(
        a: Pt, b: Pt, taper: Double, k: Double, first: Double, dmin: Double, fin: Double, m: Int, chamfer: Double, angleDeg: Double,
    ): Res {
        val out = sgn(a.x - b.x)
        if (out == 0) return Bad(IssueCode.BAD_VALUE, null, listOf("X"))
        val depths = ArrayList<Double>()
        val rough = k - fin
        if (rough > E) {
            var n = 1
            var prev = 0.0
            while (true) {
                val dn = max(first * sqrt(n.toDouble()), if (n == 1) 0.0 else prev + dmin)
                if (dn >= rough - E) {
                    depths += rough
                    break
                }
                depths += dn
                if (depths.size > MAX_STEPS) return Bad(IssueCode.EXEC_LIMIT)
                prev = dn
                n++
            }
        }
        repeat(m) { depths += k }
        val cz = sgn(b.z - a.z)
        val tanHalf = tan(angleDeg * PI / 360)
        val d1 = depths.first()
        val o = Out(a)
        for ((j, dn) in depths.withIndex()) {
            val pass = j + 1
            val lift = out * (k - dn)
            val s0 = Pt(a.z, b.x + taper + lift)
            val e0 = Pt(b.z, b.x + lift)
            val zs = a.z - cz * (dn - d1) * tanHalf
            val us = if (abs(e0.z - s0.z) > E) (zs - s0.z) / (e0.z - s0.z) else 0.0
            val st = s0 + (e0 - s0) * us
            o.to(st, MoveKind.RAPID, StepSrc.CYCLE_APPROACH, pass, true)
            threadCut(o, st, e0, chamfer, out, a.x, pass, true)
            o.to(Pt(o.at.z, a.x), MoveKind.RAPID, StepSrc.CYCLE_RETRACT, pass, true)
            o.to(a, MoveKind.RAPID, StepSrc.CYCLE_RETURN, pass, true)
        }
        return Ok(o.list)
    }

    // ================================================================ 공용

    /** 이어진 이동 목록 — 길이 0 은 빼고 넣는다 */
    private class Out(var at: Pt) {
        val list = ArrayList<Mov>()

        fun to(p: Pt, kind: MoveKind, src: StepSrc, pass: Int?, model: Boolean) {
            if ((p - at).length > E) list += Mov(Line(at, p), kind, src, pass, model)
            at = p
        }

        fun seg(s: Seg, kind: MoveKind, src: StepSrc, pass: Int?, model: Boolean, cmd: Seg?) {
            list += Mov(s, kind, src, pass, model, cmd)
            at = s.end
        }
    }

    /** G71 = (f X, c Z), G72 = (f Z, c X) */
    private class Ax(val g72: Boolean) {
        fun f(p: Pt) = if (g72) p.z else p.x
        fun c(p: Pt) = if (g72) p.x else p.z
        fun pt(f: Double, c: Double) = if (g72) Pt(f, c) else Pt(c, f)
    }

    /**
     * 토막의 한 축 방향: 1 · −1 · 0([TOL] 안). 원호가 그 축으로 끝점 범위를 [TOL] 넘게 벗어나면(되돌아감) null.
     * [x] = X 축(반경)을 보나.
     */
    internal fun dir(s: Seg, x: Boolean): Int? {
        fun v(p: Pt) = if (x) p.x else p.z
        val a = v(s.start)
        val b = v(s.end)
        if (s is Arc) {
            val lo = min(a, b) - TOL
            val hi = max(a, b) + TOL
            val sw = s.sweepRad()
            val a0 = angleOf(s.start - s.center)
            for (q in 0 until 4) {
                val ang = q * PI / 2
                val u = wrap2Pi((if (s.cw) -1 else 1) * (ang - a0))
                if (u < sw) {
                    val pv = v(s.center + Pt(cos(ang), sin(ang)) * s.radius)
                    if (pv < lo || pv > hi) return null
                }
            }
        }
        return sgnTol(b - a)
    }

    internal fun shift(s: Seg, v: Pt): Seg = when (s) {
        is Line -> Line(s.start + v, s.end + v)
        is Arc -> Arc(s.start + v, s.end + v, s.center + v, s.cw)
    }

    private fun sgn(v: Double): Int = if (v > E) 1 else if (v < -E) -1 else 0
    private fun sgnTol(v: Double): Int = if (v > TOL) 1 else if (v < -TOL) -1 else 0
}
