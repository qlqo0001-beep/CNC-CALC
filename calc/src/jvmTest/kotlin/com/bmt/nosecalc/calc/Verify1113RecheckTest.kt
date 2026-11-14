package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimExamples
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StepSrc
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.verify.GeometricVerifyTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 최종 재확인 2026-11-13 — 통합 고침(테이퍼 F1~F4 · 시뮬레이터 V12-1)을 고친 사람과 따로 다시 깨 본다. [V1112] 의 도우미(시뮬레이터 형식 C · 급송 dog-leg ·
 * 거리 · 옛 jar)를 쓰고, 여기서 더하는 것: 덜 깎임(도면 점에서 이송 노즈 길까지 − r), 그림에 없는 부품 앞면(회전체라 거울도), 옛 시뮬레이터와 견주기.
 * 보고 줄은 표준 출력과 재확인 작업 폴더의 v13-<이름>.txt(있을 때)에.
 */
internal object V1113 {
    private val dir = File("C:/Users/qlqo1/AppData/Local/Temp/claude/D------/d07d09c9-0e23-46d6-8d20-7d841b2772ce/scratchpad/cnc-feedback/final-recheck")

    fun begin(name: String) {
        runCatching { File(dir, "v13-$name.txt").delete() }
    }

    fun log(name: String, line: String) {
        println(line)
        if (dir.isDirectory) runCatching { File(dir, "v13-$name.txt").appendText(line + System.lineSeparator(), Charsets.UTF_8) }
    }

    fun f(v: Double): String = String.format(Locale.ROOT, "%.4f", v)

    fun moves(run: SimRun): List<MoveStep> = run.steps.filterIsInstance<MoveStep>().filter { it.line > V1112.pre.size }

    fun lineOf(nc: List<String>, m: MoveStep): String? = nc.getOrNull(m.line - V1112.pre.size - 1)

    private fun deepest(ms: List<MoveStep>, nc: List<String>, r: Double, mat: List<Seg>): Pair<Double, String> {
        var worst = Double.NEGATIVE_INFINITY
        var at = ""
        if (mat.isEmpty()) return worst to at
        for (m in ms) for (p in V1112.samples(m.nose)) {
            val d = r - V1112.minDist(mat, p)
            if (d > worst) {
                worst = d
                at = "${m.kind} '${lineOf(nc, m)}' 노즈 중심 X${f(2 * p.x)} Z${f(p.z)}"
            }
        }
        return worst to at
    }

    /** 첫 이송 앞(진입) 이동의 노즈가 [mat] 을 파는 가장 큰 깊이(+ 면 팜, − 면 틈)와 그 자리. 이송이 없으면 null */
    fun entryDepth(run: SimRun, nc: List<String>, r: Double, mat: List<Seg>): Pair<Double, String>? {
        val ms = moves(run)
        val ff = ms.indexOfFirst { it.kind == MoveKind.FEED }
        if (ff < 0) return null
        return deepest(ms.take(ff), nc, r, mat)
    }

    /** 모든 이동의 노즈가 [mat] 을 파는 가장 큰 깊이와 그 자리 */
    fun depth(run: SimRun, nc: List<String>, r: Double, mat: List<Seg>): Pair<Double, String> = deepest(moves(run), nc, r, mat)

    /** 그림에 없는 부품 앞면: [pts] 가운데 가장 앞(+Z) 점에서 내경은 바깥으로, 외경은 축으로(축 거울도) */
    fun frontFace(pts: List<Pt>, inner: Boolean): List<Seg> {
        val fz = pts.maxOf { it.z }
        val at = pts.filter { abs(it.z - fz) < 1e-6 }
        val face: Seg? = if (inner) at.maxByOrNull { it.x }!!.let { Line(it, Pt(fz, it.x + 1000.0)) }
        else at.minByOrNull { it.x }!!.takeIf { it.x > 1e-6 }?.let { Line(it, Pt(fz, 0.0)) }
        return listOfNotNull(face).flatMap { listOf(it, V1112.mirror(it)) }
    }

    fun ends(segs: List<Seg>): List<Pt> = segs.flatMap { listOf(it.start, it.end) }

    fun feeds(run: SimRun): List<Seg> = moves(run).filter { it.kind == MoveKind.FEED }.map { it.nose }

    /** 점 [p] 에 남은 살(이송 노즈 길까지 − r, + 면 남음) */
    fun leftAt(run: SimRun, r: Double, p: Pt): Double = feeds(run).minOfOrNull { V1112.dist(it, p) }?.minus(r) ?: Double.NaN

    /**
     * 덜 깎임: 도면 [segs] 의 [k0]..[k1] 요소 위 점(0.05 마다)에서 이송 노즈 길까지 − r 의 가장 큰 값(+ 면 살이 남음). 접하지 않고 꺾인 이음 둘레
     * (오목이면 노즈가 못 들어가는 몫 r·tan(꺾임/2))와 깎는 구간 양 끝 둘레 r 은 뺀다
     */
    fun under(run: SimRun, r: Double, segs: List<Seg>, k0: Int, k1: Int): Pair<Double, String> {
        val fs = feeds(run)
        if (fs.isEmpty() || k1 < k0) return Double.NaN to ""
        val skip = mutableListOf(segs[k0].start to r + 0.02, segs[k1].end to r + 0.02)
        for (k in k0 until k1) {
            val turn = acos((CompFrame.endDir(segs[k]) dot CompFrame.startDir(segs[k + 1])).coerceIn(-1.0, 1.0))
            if (turn > 0.01) skip += segs[k + 1].start to r * max(1.0, tan(min(turn, 3.0) / 2)) + 0.02
        }
        var worst = 0.0
        var at = ""
        for (k in k0..k1) for (p in V1112.samples(segs[k], 0.05)) {
            if (skip.any { (p - it.first).length < it.second }) continue
            val d = fs.minOf { V1112.dist(it, p) } - r
            if (d > worst) {
                worst = d
                at = "도면 X${f(2 * p.x)} Z${f(p.z)}(요소 $k)"
            }
        }
        return worst to at
    }

    /** 홑테이퍼(T1 = Z0 · T2 = Z−len, 칸 순서) — [V1112.single] 과 같은 식에 치수 · 호 R 을 넣는다. [flip] 이면 화면처럼 칸을 맞바꿔 뒤집는다 */
    fun taper(
        inner: Boolean, type: Int, r: Double, zc: Double, tip: Int?, lead: Double?, tail: Double?, cr: Double,
        x1: Double, x2: Double, len: Double, arcR: Double?, convex: Boolean, flip: Boolean, reverse: Boolean = false, approach: Boolean = true,
        cr2: Double = cr,
    ): TaperInput {
        val up = if (x2 > x1) 1.0 else -1.0
        val start = lead?.let { if (type <= 2) x1 - up * 2 * it else it }
        val end = tail?.let { if (type == 2 || type == 4) x2 + up * 2 * it else -len - it }
        val base = TaperInput(
            inner, type, reverse, r, tip, x1, 0.0, x2, -len, null, zc, cr, cr2, start = start, end = end,
            arc = arcR != null, arcR = arcR, arcConvex = convex, approach = approach,
        )
        return if (!flip) base
        else base.copy(x1 = base.x2, z1 = base.z2, x2 = base.x1, z2 = base.z1, cornerR1 = base.cornerR2, cornerR2 = base.cornerR1, start = base.end, end = base.start, flip = true)
    }

    /** 깎는 요소 범위(도면 순서) — 시작 · 끝 요소를 깎지 않으면 뺀다 */
    fun cutRange(s: TaperSolution): IntRange = (if (s.fromStart) 0 else 1)..(s.drawing.segs.lastIndex - if (s.toEnd) 0 else 1)

    /**
     * 뒤집은 테이퍼 직접 보정을 잴 소재(1회차 뒤 고침 — 그림만 요소는 길이가 그림용이라 뺀다): 깎는 요소 + 축 거울, 앞면 둘 — [faceCalc] = 계산기의 앞
     * (앞 요소를 넣었으면 그 끝, 비웠으면 T 점 — `TaperCalc` 의 zFront 와 같은 뜻), [faceCut] = 깎는 점 가운데 가장 앞(T 점 모서리 R 이 비운 앞 요소 쪽으로
     * 나온 몫까지), 막힌 시작 벽
     */
    class DirectMat(val cut: List<Seg>, val faceCalc: List<Seg>, val faceCut: List<Seg>, val walls: List<Seg>)

    fun directMat(s: TaperSolution, inner: Boolean, w: List<TaperWarning>): DirectMat {
        val segs = s.drawing.segs
        val rg = cutRange(s)
        val cut = segs.subList(rg.first, rg.last + 1)
        val pts = ends(cut)
        val zCalc = if (s.toEnd) pts.maxOf { it.z } else s.points.maxOf { it.vertex.z }
        val calcPts = pts.filter { it.z <= zCalc + 1e-6 }.ifEmpty { pts }
        val walls = if (TaperWarning.SHORT_END_PAST in w) emptyList() else V1112.walls(segs, inner, s.fromStart, false)
        return DirectMat(cut + cut.map(V1112::mirror), frontFace(calcPts, inner), frontFace(pts, inner), walls)
    }

    /** 뒤집은 직접 보정의 진입(첫 급송들과 첫 깎는 점으로 가는 첫 이송 줄)이 가장 깊이 파는 곳: (깊이, 어디, 자리) */
    fun directEntry(run: SimRun, nc: List<String>, r: Double, m: DirectMat): Triple<Double, String, String>? {
        val ms = moves(run)
        val ff = ms.indexOfFirst { it.kind == MoveKind.FEED }
        if (ff < 0) return null
        fun entryDepth(run: SimRun, nc: List<String>, r: Double, mat: List<Seg>) = deepest(ms.take(ff + 1), nc, r, mat)
        val draw = entryDepth(run, nc, r, m.cut)
        val fc = entryDepth(run, nc, r, m.faceCalc)
        val fx = entryDepth(run, nc, r, m.faceCut)
        val wall = entryDepth(run, nc, r, m.walls)
        val main = listOf(Triple(draw.first, "도면", draw.second), Triple(fc.first, "앞면", fc.second), Triple(wall.first, "막힌 시작 벽", wall.second)).maxBy { it.first }
        return if (main.first <= 2e-3 && fx.first > 2e-3) Triple(fx.first, "앞면(T 점 모서리 R 이 비운 앞 요소 쪽으로 나온 몫)", fx.second) else main
    }

    // ---------------------------------------------------------------- 최종 확인 뒤 고침(2026-11-13)이 옛 글(b305c71)에서 일부러 바꾼 것 — 회귀 시험이 쓴다

    /** 그림(도면 · 깎는 범위)만 — 노즈 0 · 표준 팁으로 푼다(그림은 노즈 · 팁과 상관없다) */
    private fun drawingOf(c: V1112.Case): TaperSolution? =
        (c.single?.let { TaperCalc.calc(it.copy(noseR = 0.0, tip = null, zClear = max(it.zClear, 1.0))) }
            ?: c.double!!.let { TaperCalc.calcDouble(it.copy(noseR = 0.0, tip = null, zClear = max(it.zClear, 1.0))) }).solution

    /**
     * 바뀐 직접 보정(G40) 글이 옛 글보다 나쁜가: 새 글만 멈춤 · 소재(깎는 요소 + 축 거울 + 막힌 시작 · 끝 벽 — 그림만 요소는 길이가 그림용이라 뺀다, [directMat] 과 같다)를
     * 옛 글보다 더 팜(옛 글이 이미 선을 지나 r 쯤 팠으면 이 잣대로는 더 나빠질 수 없어 보지 않는다) · 깎는 구간 덜 깎임이 늚. 나쁘면 그 까닭, 아니면 null.
     * 고침은 막힌 시작 · 끝과 연장선 진입 · 뒤집은 접근을 노즈 기준으로, 내경에서 축 쪽으로 가는 진입 · 접근 · 후퇴는 노즈 중심이 축에서 멈추게 했다
     * (2026-11-14 검증 뒤 고침 F1 — T1–T2 호의 처음 접선을 거꾸로 늘인 진입도).
     * 내경은 글의 첫 줄(공구 교환 자리 home — 구멍 밖 X100 — 에서 진입점 · 접근점으로 오는 G00)을 재지 않는다: 그 줄 끝의 X 급송이 진입 Z 에서 그림의 부품 몸을
     * 가로질러(실제 공구는 구멍을 따라 들어온다), 진입점이 그림 끝 너머로 멀리 간 글만 피한 것으로 잰다 — F1 의 축에서 자른 진입(노즈 중심이 축 위)이 그렇게
     * '파고듦 0.0003 → 0.797' 로 나왔다(옛 진입은 계산기 Z1.392 · 노즈 중심 반경 −4.12 — 그림이 끝난 너머의 맞은편 Ø8.403 벽 쪽)
     */
    fun g40Worse(s: TaperSolution, inner: Boolean, r: Double, old: List<String>): String? {
        val segs = s.drawing.segs
        val rg = cutRange(s)
        if (rg.isEmpty()) return null
        val cut = segs.subList(rg.first, rg.last + 1)
        val mat = cut + cut.map(V1112::mirror) + V1112.walls(segs, inner, s.fromStart, s.toEnd)
        val nNew = V1112.direct(s.ncG40)
        val nOld = V1112.direct(old)
        val a = V1112.sim(nNew, r, s.tip)
        val b = V1112.sim(nOld, r, s.tip)
        if (a.stop != null) return if (b.stop == null) "새 글만 멈춤 ${a.stop}" else null
        if (b.stop != null) return null
        val from = V1112.pre.size + if (inner) 1 else 0
        val gNew = deepest(moves(a).filter { it.line > from }, nNew, r, mat).first
        val gOld = deepest(moves(b).filter { it.line > from }, nOld, r, mat).first
        if (gNew > max(gOld, 0.0) + 2e-3 && gOld < r - 0.02) return "파고듦 늘어남 ${f(gOld)} → ${f(gNew)}"
        val uNew = under(a, r, segs, rg.first, rg.last).first
        val uOld = under(b, r, segs, rg.first, rg.last).first
        if (uNew > max(uOld, 0.0) + 2e-3) return "덜 깎임 늘어남 ${f(uOld)} → ${f(uNew)}"
        return null
    }

    /**
     * 새로 BAD_LEG 로 멈춘 것이 고침 탓인가 — 노즈 기준 막힌 시작 · 끝에서 노즈가 짧은 요소에 벽 앞에서 닿을 길이 없음: 옛 직접 보정 글의 이송 노즈가 넣은 막힌
     * 시작 · 끝 좌표 너머로 갔어야 한다(calc-design '넣은 시작·끝점 너머로 넘지 않기'). 막힘은 계산기 판정 그대로 — 수평은 척 · 구멍 바닥 쪽(거울이면 계산기 +Z),
     * 단면은 재료 쪽(보스 — 외경 X0 은 빼고)
     */
    fun newBadLegOk(c: V1112.Case, res: TaperResult, o: V1112.Old): Boolean {
        if (res.solution != null || res.warnings != listOf(TaperWarning.BAD_LEG) || o.nc40 == null) return false
        val d = drawingOf(c) ?: return false
        val segs = d.drawing.segs
        val mirror = !(c.single?.approach ?: c.double!!.approach)
        val planes = mutableListOf<Pair<Pt, Pt>>() // (좌표 점, 너머 방향)
        fun add(p: Pt, beyond: Pt) {
            val blocked = if (abs(beyond.z) > abs(beyond.x)) (if (mirror) beyond.z > 0 else beyond.z < 0)
            else (beyond.x < 0) != c.inner && !(!c.inner && p.x <= 1e-9)
            if (blocked) planes += p to beyond
        }
        if (d.fromStart) add(segs.first().start, CompFrame.startDir(segs.first()) * -1.0)
        if (d.toEnd) add(segs.last().end, CompFrame.endDir(segs.last()))
        if (planes.isEmpty()) return false
        val tip = c.single?.tip ?: c.double?.tip ?: NoseComp.standardTip(c.inner, c.single?.reverse ?: c.double!!.reverse)
        val n = V1112.direct(o.nc40)
        val run = V1112.sim(n, c.r, tip)
        if (run.stop != null) return false
        val over = feeds(run).flatMap { V1112.samples(it) }.maxOfOrNull { q -> planes.maxOf { (p, b) -> ((q - p) dot b) + c.r } } ?: return false
        return over > 2e-3
    }

    /**
     * 뒤집기 밖 회귀: 옛 글과의 차이가 고침 탓만인가. G41/G42 는 같아야 하고, 직접 보정(G40)은 [g40Worse] 가 아니면 되고(내경 바닥 X0 끝은 통합 고침 몫이라 [axisOk]
     * 면 그대로 받음), 경고는 G40 이 바뀌었을 때 ENTRY_PAST_AXIS 만 달라질 수 있다(그 경고는 직접 보정 점도 본다). 반환 = (받지 못한 차이, 고침으로 G40 이 바뀜)
     */
    fun vsOld(c: V1112.Case, res: TaperResult, o: V1112.Old, axisOk: (TaperSolution) -> Boolean = { false }): Pair<List<String>, Boolean> {
        val s = res.solution
        if ((s == null) != (o.nc4x == null)) {
            return (if (newBadLegOk(c, res, o)) emptyList() else listOf("풀림 다름: 새 ${res.warnings} 옛 ${o.warnings}")) to (s == null)
        }
        if (s == null) return emptyList<String>() to false
        val out = mutableListOf<String>()
        if (s.ncG41G42 != o.nc4x) out += "G41/G42 바뀜\n 옛 ${o.nc4x}\n 새 ${s.ncG41G42}"
        val g40 = s.ncG40 != o.nc40 && !axisOk(s)
        if (g40) g40Worse(s, c.inner, c.r, o.nc40!!)?.let { out += "G40 $it\n 옛 ${o.nc40}\n 새 ${s.ncG40}" }
        val wNew = res.warnings.map { it.name }
        val wOld = o.warnings
        if (wNew != wOld && !(s.ncG40 != o.nc40 && wNew.filter { it != "ENTRY_PAST_AXIS" } == wOld.filter { it != "ENTRY_PAST_AXIS" })) out += "경고: 옛 $wOld 새 $wNew"
        return out to g40
    }

    /**
     * 라운드 직접 보정이 고침(F5 — 뒤집은 접근 줄을 노즈 기준으로)으로만 달라졌나: 첫 G1 부터의 길(G · X · Z · R)이 같고 그 앞 급송(접근 · 진입 — 접근 줄이 생겨
     * 급송 여유 후보가 바뀌면 진입점도 바뀐다)만 다르며, 새 급송의 노즈가 소재(도면 + 축 거울 + 그림에 없는 부품 앞면)를 옛 글보다 더 파지 않는다
     */
    fun roundEntryOnly(s: RoundSolution?, old: List<String>?, r: Double, inner: Boolean): Boolean {
        val new = s?.nc ?: return false
        if (old == null) return false
        val a = new.indexOfFirst { it.startsWith("G1") }
        val b = old.indexOfFirst { it.startsWith("G1") }
        if (a < 1 || b < 1) return false
        if (V1112.parse(new).drop(a).map { it.spelled() } != V1112.parse(old).drop(b).map { it.spelled() }) return false
        val segs = s.drawing.segs
        val mat = segs + segs.map(V1112::mirror) + frontFace(ends(segs), inner)
        val nNew = V1112.direct(new)
        val nOld = V1112.direct(old)
        val runNew = V1112.sim(nNew, r, s.tip)
        if (runNew.stop != null) return false
        val dNew = entryDepth(runNew, nNew, r, mat)?.first ?: return false
        val runOld = V1112.sim(nOld, r, s.tip)
        val dOld = if (runOld.stop != null) Double.POSITIVE_INFINITY else entryDepth(runOld, nOld, r, mat)?.first ?: Double.POSITIVE_INFINITY
        return dNew <= 2e-3 || dNew <= max(dOld, 0.0) + 2e-3
    }

    /**
     * 라운드 관통 구멍(내경 5 · 6 through) 글이 사용자 답 2026-11-13("관통이면 넘어가도 됌" — 뒤집은 시작은 시작 좌표 너머에서 들어오고 기본 방향 기계 보정의
     * 끝은 끝 좌표 너머로 지나감)으로 달라진 것인가: 새 글을 시뮬레이터로 돌려 어느 이동의 노즈도 소재(도면 + 축 거울 + 관통 구멍 연장선 + 부품 앞면)를
     * 파지 않거나(0.002) 옛 글보다 더 파지 않는다(좁은 구멍 — 옛 글도 판 것). 새 글이 멈추면 옛 글도 같은 까닭으로 멈춰야 한다(형상 알람 — 오목 R ≤ r ·
     * 형상 안의 바깥 90° 넘는 꺾임은 방향 · 관통과 상관없이 멈춘다). 구멍 벽을 끝까지 깎는지 · 급송 틈은 `RoundThroughTest` 가 고정한다
     */
    fun roundThroughOk(s: RoundSolution?, old: List<String>?, machine: Boolean, r: Double): Boolean {
        val (dNew, dOld) = throughDepths(s, old, machine, r) ?: return false
        return dNew <= 2e-3 || dNew <= max(dOld, 0.0) + 2e-3
    }

    /**
     * 관통 글의 경고가 바뀌어도 되는 것만 바뀌었나(검증 2026-11-14 — 전에는 관통 글이면 경고를 견주지 않았다): 없어진 것은 관통 끝 · 시작 탓인 것만
     * (짧은 관통 시작의 NOSE_UNREACHABLE · 막힌 끝의 SHORT_END_* · 빼는 · 진입 길 못 찾음), 새로 난 것은 THROUGH_END_BLOCKED(너머로 못 빼 막힌 끝 — 옛 글과 같은 끝)와,
     * 옛 글이 없을 때(풀리게 된 것) 풀이에 붙는 경고(AGAINST_TOOL · CONCAVE_R_LE_NOSE)뿐
     */
    fun roundThroughWarningsOk(newW: List<String>, oldW: List<String>, oldSolved: Boolean): Boolean {
        val added = newW.filter { it !in oldW && it != "THROUGH_END_BLOCKED" }
        val removed = oldW.filter { it !in newW }
        val mayAdd = if (oldSolved) emptySet() else setOf("AGAINST_TOOL", "CONCAVE_R_LE_NOSE")
        val mayGo = setOf("NOSE_UNREACHABLE", "SHORT_END_SKIPPED", "SHORT_END_PAST", "EXIT_NOT_FOUND", "ENTRY_NOT_FOUND")
        return added.all { it in mayAdd } && removed.all { it in mayGo }
    }

    /**
     * [roundThroughOk] 의 잰 값: (새 글 파고듦, 옛 글 파고듦) — 소재는 도면 + 축 거울 + 관통 구멍 연장선 + 부품 앞면. 새 글이 멈추면 옛 글도 같은 까닭으로 멈출 때만
     * (0, 0)(형상 알람), 아니면 null. 옛 글이 없으면(짧은 관통 구멍 쪽 시작을 막힌 시작으로 봐 NOSE_UNREACHABLE — 이제는 벽이 없어 풀린다) 오목 R ≤ r 형상 알람
     * (COMP_ARC_SMALL — 직접 보정이 CONCAVE_R_LE_NOSE 로 알리는 꼴, 방향 · 관통과 상관없음 — 도면에 오목 R ≤ r 이 있을 때만)만 멈춤으로 받는다. 옛 글이 없거나 멈추면 옛 값은 +∞
     */
    private fun throughDepths(s: RoundSolution?, old: List<String>?, machine: Boolean, r: Double): Pair<Double, Double>? {
        val new = s?.nc ?: return null
        val segs = s.drawing.segs
        val hole = (if (s.flip) segs.first() else segs.last()) as? Line ?: return null
        val end = if (hole.start.z < hole.end.z) hole.start else hole.end
        val ext = Line(end, end + Pt(-300.0, 0.0))
        val mat = segs + segs.map(V1112::mirror) + listOf(ext, V1112.mirror(ext)) + frontFace(ends(segs), true)
        fun text(nc: List<String>) = if (machine) nc else V1112.direct(nc)
        val nNew = text(new)
        val runNew = V1112.sim(nNew, r, s.tip)
        val nOld = old?.let(::text)
        val runOld = nOld?.let { V1112.sim(it, r, s.tip) }
        // 오목 R ≤ r(계산기 finish 의 CONCAVE_R_LE_NOSE 기준 — G41 이면 반시계, G42 면 시계 원호)
        val concaveLeNose = segs.any { it is Arc && (s.side == Side.G41) != it.cw && it.radius - r <= 1e-9 }
        runNew.stop?.let { stop -> return if (runOld?.stop?.code == stop.code || (old == null && stop.code == IssueCode.COMP_ARC_SMALL && concaveLeNose)) 0.0 to 0.0 else null }
        val dOld = if (nOld == null || runOld == null || runOld.stop != null) Double.POSITIVE_INFINITY else depth(runOld, nOld, r, mat).first
        return depth(runNew, nNew, r, mat).first to dOld
    }

    fun shapeAlarm(w: List<TaperWarning>) = TaperWarning.CONCAVE_R_LE_NOSE in w || TaperWarning.NOSE_GT_SLOPE in w

    fun shortEnd(w: List<TaperWarning>) = TaperWarning.SHORT_END_SKIPPED in w || TaperWarning.SHORT_END_PAST in w

    // ---------------------------------------------------------------- 옛 계산기 · 시뮬레이터(b305c71 jar) — 중첩 data class · enum · 목록 · 맵을 옛 클래스로 옮겨 부른다

    private class ChildFirst(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            if (name.startsWith("com.bmt.nosecalc.")) findLoadedClass(name) ?: findClass(name) else super.loadClass(name, resolve)
        }
    }

    private val loader: ClassLoader by lazy { ChildFirst(arrayOf(V1112.oldJar.toURI().toURL()), V1113::class.java.classLoader) }

    fun toOld(v: Any?): Any? {
        if (v == null) return null
        val cls = v.javaClass
        if (v is Enum<*>) {
            val ec = if (cls.isEnum) cls else cls.superclass
            if (!ec.name.startsWith("com.bmt.nosecalc.")) return v
            return loader.loadClass(ec.name).enumConstants.first { (it as Enum<*>).name == v.name }
        }
        if (v is Map<*, *>) return v.entries.associate { toOld(it.key) to toOld(it.value) }
        if (v is List<*>) return v.map { toOld(it) }
        if (!cls.name.startsWith("com.bmt.nosecalc.")) return v
        val n = cls.methods.count { Regex("component[0-9]+").matches(it.name) && it.parameterCount == 0 }
        val vals = (1..n).map { toOld(cls.getMethod("component$it").invoke(v)) }
        val ctor = loader.loadClass(cls.name).declaredConstructors.single {
            it.parameterCount == n && it.parameterTypes.none { t -> t.name.endsWith("DefaultConstructorMarker") }
        }
        return ctor.newInstance(*vals.toTypedArray())
    }

    fun oldCall(obj: String, method: String, input: Any): Any {
        val o = toOld(input)!!
        val cls = loader.loadClass(obj)
        return cls.getMethod(method, o.javaClass).invoke(cls.getField("INSTANCE").get(null), o)
    }

    fun get(o: Any?, name: String): Any? = o?.javaClass?.getMethod(name)?.invoke(o)

    @Suppress("UNCHECKED_CAST")
    fun oldRoundResult(o: Any): Pair<List<String>?, List<String>> =
        (get(get(o, "getSolution"), "getNc") as List<String>?) to (get(o, "getWarnings") as List<*>).map { (it as Enum<*>).name }
}

/** 고침 단계에서 '고침' 으로 적힌 것의 원래 재현 입력(값 그대로) */
class Verify1113FixReproTest {
    /** F1: 내경 바닥 X0 끝 직접 보정 — 노즈 중심이 축에 서고(맞은편을 파지 않음) 바닥 가운데 살이 남지 않음. 같은 꼴의 노즈 · 원호 · 팁도 */
    @Test
    fun f1() {
        val name = "f1"
        V1113.begin(name)
        val base = TaperInput(inner = true, type = 2, noseR = 0.8, x1 = 6.0, z1 = 0.0, x2 = 2.0, z2 = -6.0, zClear = 0.0, start = 12.0, end = 0.0)
        val arcR = ceil(TaperCalc.arcMinR(6.0, 0.0, 2.0, -6.0)!! * 2 * 1000) / 1000
        val fails = mutableListOf<String>()
        val inputs = listOf(base, base.copy(noseR = 1.2), base.copy(arc = true, arcR = arcR), base.copy(noseR = 1.2, arc = true, arcR = arcR)) +
            listOf(0, 1, 3, 4, 5, 6, 7, 8, 9).map { base.copy(tip = it) }
        for (i in inputs) {
            val res = TaperCalc.calc(i)
            val s = res.solution
            if (s == null) {
                V1113.log(name, "안 풀림 $i ${res.warnings}")
                continue
            }
            val segs = s.drawing.segs
            val mat = segs + segs.map(V1112::mirror)
            for (m in listOf(false, true)) {
                if (m && i.tip != null) continue
                val nc = if (m) s.ncG41G42 else V1112.direct(s.ncG40)
                val run = V1112.sim(nc, i.noseR, s.tip)
                val tag = "${if (m) "기계" else "직접"} $i ${res.warnings}"
                if (run.stop != null) {
                    fails += "$tag 멈춤 ${run.stop}\n $nc"
                    continue
                }
                val (g, at) = V1113.depth(run, nc, i.noseR, mat)
                val left = V1113.leftAt(run, i.noseR, segs.last().end)
                V1113.log(name, "$tag: 파고듦 ${V1113.f(g)} $at · 바닥 가운데 살 ${V1113.f(left)}\n  $nc")
                if (g > 2e-3) fails += "$tag 파고듦 ${V1113.f(g)} $at\n $nc"
                if (left > 2e-3) fails += "$tag 바닥 가운데 살 ${V1113.f(left)}\n $nc"
            }
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /** F2: 뒤집은 내경 작은 구멍 ①② — 급송 틈 c/4 이상 · ENTRY_NOT_FOUND 없음 · 진입 이송 · 모든 이동 파고듦 0 */
    @Test
    fun f2() {
        val name = "f2"
        V1113.begin(name)
        val i1 = TaperInput(inner = true, type = 2, noseR = 0.2, x1 = 6.0, z1 = -8.0, x2 = 10.0, z2 = 0.0, zClear = 5.0, cornerR1 = 0.5, cornerR2 = 0.5, start = 4.0, flip = true)
        val i2 = i1.copy(noseR = 0.4, start = null)
        val fails = mutableListOf<String>()
        for (i in listOf(i1, i2, i1.copy(type = 4, start = 2.0), i2.copy(arc = true, arcR = ceil(TaperCalc.arcMinR(6.0, -8.0, 10.0, 0.0)!! * 2 * 1000) / 1000))) {
            val res = TaperCalc.calc(i)
            val s = res.solution
            if (s == null) {
                fails += "안 풀림 $i ${res.warnings}"
                continue
            }
            val segs = s.drawing.segs
            val walls = if (TaperWarning.SHORT_END_PAST in res.warnings) emptyList() else V1112.walls(segs, true, s.fromStart, false)
            val mat = segs + segs.map(V1112::mirror)
            val nc = s.ncG41G42
            val run = V1112.sim(nc, i.noseR, s.tip)
            val c = max(i.zClear, i.noseR)
            if (run.stop != null) {
                fails += "$i 멈춤 ${run.stop}\n $nc"
                continue
            }
            val gap = V1112.entryGap(run, i.noseR, mat + walls)!!
            val (g, at) = V1113.depth(run, nc, i.noseR, mat)
            V1113.log(name, "$i ${res.warnings}: 급송 틈 ${V1113.f(gap)} (c $c) · 파고듦 ${V1113.f(g)} $at\n  $nc")
            if (gap < c / 4 - 2e-3) fails += "$i 급송 틈 ${V1113.f(gap)} < c/4\n $nc"
            if (TaperWarning.ENTRY_NOT_FOUND in res.warnings) fails += "$i ENTRY_NOT_FOUND 남음"
            if (g > 2e-3) fails += "$i 파고듦 ${V1113.f(g)} $at\n $nc"
            V1112.companyFlow(nc)?.let { fails += "$i 회사 흐름 $it" }
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /** F3: 외경 형상 5 뒤집기 · 시작 Z−6 — 이웃 위 높이로 급송 틈 c(1) 이상 */
    @Test
    fun f3() {
        val name = "f3"
        V1113.begin(name)
        val i = TaperInput(type = 5, noseR = 0.2, x1 = 40.0, z1 = -5.0, x2 = 50.0, z2 = 0.0, zClear = 1.0, start = -6.0, flip = true)
        val res = TaperCalc.calc(i)
        val s = res.solution!!
        val segs = s.drawing.segs
        val walls = V1112.walls(segs, false, s.fromStart, false)
        val mat = segs + segs.map(V1112::mirror)
        val nc = s.ncG41G42
        val run = V1112.sim(nc, 0.2, s.tip)
        assertEquals(null, run.stop, "$nc")
        val gap = V1112.entryGap(run, 0.2, mat + walls)!!
        val (g, at) = V1113.depth(run, nc, 0.2, mat)
        val iCut = V1112.firstCutLine(nc, segs, s.fromStart)
        var feedIn = Double.MAX_VALUE
        val ms = V1113.moves(run)
        for (m in ms.drop(ms.indexOfFirst { it.kind == MoveKind.FEED }).filter { it.line - V1112.pre.size - 1 <= iCut }) {
            for (p in V1112.samples(m.nose)) feedIn = min(feedIn, V1112.minDist(mat + walls, p) - 0.2)
        }
        V1113.log(name, "$i ${res.warnings}: 급송 틈 ${V1113.f(gap)} · 진입 이송 틈 ${V1113.f(feedIn)} · 파고듦 ${V1113.f(g)} $at\n  $nc")
        assertTrue(gap >= 1.0 - 2e-3, "급송 틈 $gap\n$nc")
        assertTrue(feedIn >= -2e-3, "진입 이송 틈 $feedIn\n$nc")
        assertTrue(g <= 2e-3, "파고듦 $g $at")
        assertEquals(null, V1112.companyFlow(nc))
    }

    /** V12-1: V7-4 꼴(최소 R5 · 받는 띠 R4.999)이 노즈 0.2~1.2 · 형식 C/AB 에서 돌고, 90° 를 그보다 넘는 것(90.3° r 1.2 · 90.7°)은 여전히 멈춘다 */
    @Test
    fun v121() {
        val name = "v121"
        V1113.begin(name)
        val head = listOf("T0101", "G97S1000M03", "G99")
        fun prog(vararg l: String) = (head + l.toList() + "M30").joinToString("\n")
        val below = prog("G42", "G0X30.Z20.", "Z-1.", "G1Z-2.F.1", "G2X40.Z-7.R4.999", "G1Z-10.", "X42.F.3", "G40", "G0Z200.")
        val exact = prog("G42", "G0X24.Z20.", "Z-1.", "G1Z-2.F.1", "X30.", "G2X40.Z-7.R5.", "G1X42.F.3", "G40", "G0Z200.")
        val fails = mutableListOf<String>()
        // 노즈 1.2 는 이 손 글의 짧은 줄(Z−1 → Z−2 · X30 · 빼는 X42 — 반경 1 ~ 3)이 노즈보다 짧아 COMP_INTERFERENCE 로 멈춘다(90° 규칙과 상관없는 시험 글 탓 — 첫 회차에서 봄)
        for (r in listOf(0.2, 0.4, 0.8)) for (form in listOf(CompStartup.C, CompStartup.AB)) for ((n, t) in listOf("R4.999" to below, "R5" to exact)) {
            val run = GSim.run(SimInput(t, MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, 3))))
            V1113.log(name, "$n r $r $form: 멈춤 ${run.stop}")
            if (run.stop != null) fails += "$n r $r $form 멈춤 ${run.stop}"
        }
        // 직선끼리 90.3° · 90.7° 바깥 꺾임(A: X50 을 Z 로 −, B: 공구 반대쪽 아래로 꺾여 앞으로 조금) — r 1.2 면 넘은 몫 1.2·0.0052 = 0.006 > 0.001,
        // 90.7° 는 sin 0.0122 > 0.01
        for ((deg, r) in listOf(90.3 to 1.2, 90.7 to 0.2)) {
            val dz = 5 * tan(Math.toRadians(deg - 90))
            val t = prog("G42", "G0X50.Z20.", "Z1.", "G1Z0.F.1", "Z-5.", "X40.Z${String.format(Locale.ROOT, "%.3f", -5 + dz)}", "X38.F.3", "G40", "G0Z200.")
            val run = GSim.run(SimInput(t, MachineSetup(), mapOf(1 to ToolOffset(r, 3))))
            V1113.log(name, "직선 $deg° r $r: 멈춤 ${run.stop}")
            if (run.stop?.code != IssueCode.COMP_ACUTE_OUTER) fails += "직선 $deg° r $r 가 멈추지 않음 ${run.stop}\n$t"
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n"))
    }
}

/**
 * F1 이 뒤집지 않은 기본 방향의 내경 바닥 X0 끝 직접 보정도 바꿨다 — 형상 2 · 4 × 직선 · 원호 × 노즈 × 바닥 Ø(노즈가 못 들어가는 작은 것까지) × 팁 0~9 에서
 * 맞은편 파고듦 · 바닥 가운데 살 · 덜 깎임 · 끝 노즈 중심이 축인가(화면 글) · 표준 팁 인선 끝 X = 2r(화면 글) · 끝 뒤 바닥 쪽 급송
 */
class Verify1113InnerAxisEndTest {
    private val tips: List<Int?> = listOf(null, 0, 1, 3, 4, 5, 6, 7, 8, 9)

    private fun backRapid(run: SimRun, nc: List<String>): String? {
        val ms = V1113.moves(run)
        val ff = ms.indexOfFirst { it.kind == MoveKind.FEED }
        if (ff < 0) return null
        return ms.drop(ff).firstOrNull {
            it.kind == MoveKind.RAPID && it.src != StepSrc.SHIFT_START && it.src != StepSrc.SHIFT_CANCEL && it.tip.end.z < it.tip.start.z - 1e-6
        }?.let { V1113.lineOf(nc, it) }
    }

    /** 직접 보정: 첫 이송 뒤 첫 급송 바로 앞 이송의 노즈 끝 */
    private fun endNose(run: SimRun): Pt? {
        val ms = V1113.moves(run)
        val ff = ms.indexOfFirst { it.kind == MoveKind.FEED }
        if (ff < 0) return null
        val rr = (ff until ms.size).firstOrNull { ms[it].kind == MoveKind.RAPID } ?: ms.size
        return ms[rr - 1].nose.end
    }

    @Test
    fun defaultDirectionGrid() {
        val name = "innerAxisEnd"
        V1113.begin(name)
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }
        val bad = sortedMapOf<String, Pair<Int, String>>()
        fun fail(k: String, ex: String) {
            bad[k] = ((bad[k]?.first ?: 0) + 1) to (bad[k]?.second ?: ex)
        }
        var worstGouge = Double.NEGATIVE_INFINITY
        var worstLeft = Double.NEGATIVE_INFINITY
        var worstUnder = 0.0
        var runs = 0
        for (type in listOf(2, 4)) for (kind in 0..2) for (r in listOf(0.2, 0.8, 1.2)) for (zc in listOf(0.0, 5.0)) for (bottom in listOf(0.5, 1.0, 2.0, 6.0))
            for (open in listOf(1.0, 14.0)) for (depth in listOf(0.5, 6.0)) for (cr in listOf(0.0, 0.5)) for (tip in tips) {
                val x1 = bottom + open
                val arcR = if (kind > 0) TaperCalc.arcMinR(x1, 0.0, bottom, -depth)?.let { ceil(it * 2 * 1000) / 1000 } else null
                val start = if (type == 2) x1 + 6.0 else 3.0
                val i = TaperInput(true, type, false, r, tip, x1, 0.0, bottom, -depth, null, zc, cr, cr, start = start, end = 0.0, arc = kind > 0, arcR = arcR, arcConvex = kind == 1)
                val res = TaperCalc.calc(i)
                val s = res.solution
                if (s == null) {
                    bump("안 풀림 ${res.warnings.sorted()} 바닥 Ø$bottom 노즈 $r")
                    continue
                }
                if (!V1112.axisEnd(s, true)) {
                    bump("X0 끝 아님")
                    continue
                }
                val segs = s.drawing.segs
                val mat = segs + segs.map(V1112::mirror)
                val alarm = V1113.shapeAlarm(res.warnings)
                for (m in listOf(false, true)) {
                    if (m && tip != null) continue
                    val kt = if (m) "기계" else "직접"
                    val nc = if (m) s.ncG41G42 else V1112.direct(s.ncG40)
                    val run = V1112.sim(nc, r, s.tip)
                    val tag = "$i ${res.warnings}\n   $nc"
                    val stop = run.stop
                    if (stop != null) {
                        bump("$kt 멈춤" + if (alarm) "(형상 경고 꼴)" else "")
                        if (!alarm && !(m && bottom / 2 < CompFrame.g40Radius(r))) fail("$kt 멈춤 ${stop.code}", "$stop · $tag")
                        continue
                    }
                    runs++
                    bump("$kt 돌림")
                    // 첫 이송 뒤(깎기 · 바닥 끝 · 후퇴 — F1 몫)와 진입(첫 급송 — 기본 방향 직접 보정 진입, 1회차에서 팁 1 · 4 · 5 가 앞면을 지남:
                    // Verify1113DirectTipTest 가 재현)을 나눠 잰다. 형상 경고 · EXIT_NOT_FOUND 가 뜬 기계 보정 파고듦은 앱이 알리니 세기만
                    val ms = V1113.moves(run)
                    val ff = ms.indexOfFirst { it.kind == MoveKind.FEED }
                    var g = Double.NEGATIVE_INFINITY
                    var gAt = ""
                    var ge = Double.NEGATIVE_INFINITY
                    for ((k, mv) in ms.withIndex()) for (p in V1112.samples(mv.nose)) {
                        val d = r - V1112.minDist(mat, p)
                        if (k < ff) ge = max(ge, d)
                        else if (d > g) {
                            g = d
                            gAt = "${mv.kind} '${V1113.lineOf(nc, mv)}' 노즈 중심 X${V1113.f(2 * p.x)} Z${V1113.f(p.z)}"
                        }
                    }
                    worstGouge = max(worstGouge, g)
                    if (ge > 2e-3) bump("$kt 진입 첫 급송 파고듦 팁 ${tip ?: "표준"} — DirectTip 몫")
                    if (g > 2e-3) {
                        if (alarm || TaperWarning.EXIT_NOT_FOUND in res.warnings) bump("$kt 파고듦 · 앱 경고 있음 ${res.warnings.filter { it != TaperWarning.ENTRY_PAST_AXIS }}")
                        else fail("$kt 파고듦 팁 ${tip ?: "표준"} 노즈 $r 바닥 Ø$bottom", "${V1113.f(g)} $gAt · $tag")
                    }
                    val left = V1113.leftAt(run, r, segs.last().end)
                    worstLeft = max(worstLeft, left)
                    if (left > 2e-3) fail("$kt 바닥 가운데 살 팁 ${tip ?: "표준"}", "${V1113.f(left)} · $tag")
                    if (!alarm && !V1113.shortEnd(res.warnings)) {
                        val rg = V1113.cutRange(s)
                        val (u, uAt) = V1113.under(run, r, segs, rg.first, rg.last)
                        if (!u.isNaN()) worstUnder = max(worstUnder, u)
                        if (u > 2e-3) fail("$kt 덜 깎임 팁 ${tip ?: "표준"}", "${V1113.f(u)} $uAt · $tag")
                    }
                    backRapid(run, nc)?.let { fail("$kt 끝 뒤 바닥 쪽(−Z) 급송", "'$it' · $tag") }
                    if (!m) {
                        val en = endNose(run)
                        if (en == null || abs(en.x) > 1e-3) fail("직접 끝 노즈 중심이 축에 안 섬 팁 ${tip ?: "표준"}", "노즈 끝 $en · $tag")
                        if (tip == null) {
                            val pl = V1112.parse(nc)
                            val g1 = nc.indexOfFirst { it.startsWith("G1") }
                            val k = (g1 until nc.size).firstOrNull { pl[it].g == 0 }
                            val endX = if (k == null) Double.NaN else 2 * pl[k - 1].p.x
                            if (!(abs(endX - 2 * r) <= 1e-3)) fail("직접 표준 팁 인선 끝 X ≠ 2r(화면 글)", "X ${V1113.f(endX)} · $tag")
                        }
                    }
                }
            }
        V1113.log(name, "기본 방향 내경 바닥 X0 끝: 돌린 글 $runs · 가장 깊은 파고듦 ${V1113.f(worstGouge)} · 가장 큰 바닥 가운데 살 ${V1113.f(worstLeft)} · 가장 큰 덜 깎임 ${V1113.f(worstUnder)}")
        for ((k, v) in stat) V1113.log(name, "  $k: $v")
        V1113.log(name, "문제 꼴 ${bad.size}:")
        for ((k, v) in bad) V1113.log(name, "- ${v.first}건 $k\n   예: ${v.second}")
        assertTrue(runs > 1000, "$stat")
        assertEquals(0, bad.size, bad.entries.take(5).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second}" })
    }
}

/** F2 를 넓혀: 뒤집은 내경 작은 구멍(바닥 Ø3 · 4 · 6 × 입구 +2 · +8 × 길이 3 · 8)의 기계 보정 진입 — ENTRY_NOT_FOUND 가 정직한가 */
class Verify1113FlipSmallHoleTest {
    @Test
    fun flippedSmallHoleGrid() {
        val name = "flipSmallHole"
        V1113.begin(name)
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }
        val bad = sortedMapOf<String, Pair<Int, Pair<Double, String>>>()
        fun fail(k: String, v: Double, ex: String) {
            val p = bad[k]
            bad[k] = ((p?.first ?: 0) + 1) to (if (p == null || v < p.second.first) v to ex else p.second)
        }
        var runs = 0
        for (type in 1..5) for (kind in 0..1) for (bottom in listOf(3.0, 4.0, 6.0)) for (open in listOf(2.0, 8.0)) for (len in listOf(3.0, 8.0))
            for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 3.0, 10.0)) for (tail in listOf(null, 1.0, 2.5)) for (cr in listOf(0.0, 0.5)) {
                val x1 = if (type == 5) bottom else bottom + open
                val x2 = if (type == 5) bottom + open else bottom
                if (type == 2 || type == 4) if (tail != null && x2 - 2 * tail <= 0.0) continue
                val arcR = if (kind > 0) TaperCalc.arcMinR(x1, 0.0, x2, -len)?.let { ceil(it * 2 * 1000) / 1000 } else null
                val i = V1113.taper(true, type, r, zc, null, null, tail, cr, x1, x2, len, arcR, true, flip = true)
                val res = TaperCalc.calc(i)
                val s = res.solution ?: continue
                val segs = s.drawing.segs
                val walls = if (TaperWarning.SHORT_END_PAST in res.warnings) emptyList() else V1112.walls(segs, true, s.fromStart, false)
                val mat = segs + segs.map(V1112::mirror)
                val nc = s.ncG41G42
                val run = V1112.sim(nc, r, s.tip)
                val nf = TaperWarning.ENTRY_NOT_FOUND in res.warnings
                val c = max(zc, r)
                val shape = "형상 $type${if (kind > 0) " 원호" else ""} ${res.warnings.sorted()}"
                val ex = "$i\n   $nc"
                val stop = run.stop
                if (stop != null) {
                    val iCut = V1112.firstCutLine(nc, segs, s.fromStart)
                    val at = stop.line - V1112.pre.size - 1
                    if (V1113.shapeAlarm(res.warnings)) bump("형상 경고 꼴 멈춤")
                    else if (iCut >= 0 && at <= iCut) fail("진입에서 멈춤 ${stop.code} | $shape", 0.0, ex)
                    else fail("형상 · 빼기에서 멈춤 ${stop.code} | $shape", 0.0, ex)
                    continue
                }
                runs++
                val gap = V1112.entryGap(run, r, mat + walls)!!
                when {
                    gap < -2e-3 -> if (nf) bump("급송 파고듦 · ENTRY_NOT_FOUND 알림") else fail("급송 파고듦 · 알림 없음 | $shape", gap, ex)
                    gap < 2e-3 -> bump("급송 닿음(틈 0) · " + if (nf) "알림" else "알림 없음(알려진 절충)")
                    // 설계: 자리가 없으면 (c, 0) (c/2, 0) (c/4, 0) — 급송 끝 틈은 g 이상, 가는 길은 닿지만 않으면(loose 단계) — 그래서 길 틈 0 ~ c/4 는 세기만
                    gap < c / 4 - 2e-3 -> bump("c/4 미만(틈 > 0) · " + if (nf) "알림" else "알림 없음(loose 단계 — 설계)")
                    gap >= c - 2e-3 -> bump("c 이상")
                    gap >= c / 2 - 2e-3 -> bump("c/2 이상")
                    else -> bump("c/4 이상")
                }
                if (nf && gap >= c / 4 - 2e-3) bump("ENTRY_NOT_FOUND 인데 틈 c/4 이상(헛경고?)")
                val iCut = V1112.firstCutLine(nc, segs, s.fromStart)
                val ms = V1113.moves(run)
                var feedIn = Double.MAX_VALUE
                for (m in ms.drop(ms.indexOfFirst { it.kind == MoveKind.FEED }).filter { it.line - V1112.pre.size - 1 <= iCut }) {
                    for (p in V1112.samples(m.nose)) feedIn = min(feedIn, V1112.minDist(mat + walls, p) - r)
                }
                // 파고듦은 ENTRY_NOT_FOUND · EXIT_NOT_FOUND 가 뜨면(앱이 알림) 세기만 — 알림 없이 파는 것만 실패(F2 의 ENTRY_NOT_FOUND 기준)
                val warned = nf || TaperWarning.EXIT_NOT_FOUND in res.warnings
                if (feedIn < -2e-3) if (nf) bump("진입 이송 파고듦 · ENTRY_NOT_FOUND 알림") else fail("진입 이송이 도면 · 벽을 팜 · 알림 없음 | $shape", feedIn, ex)
                val (g, at) = V1113.depth(run, nc, r, mat)
                if (g > 2e-3) if (warned) bump("파고듦 · 알림 있음") else fail("파고듦 · 알림 없음 ${at.substringBefore(" ")} | $shape", -g, "$at · $ex")
                // 회사 흐름(급송 한 축씩): 통합 고침의 뒤집은 진입 후보가 낸 두 축 G0 은 실패(고침 2026-11-13 — 후보에서 받지 않음). 옛 b305c71 글과 글자 같은 것은
                // 후보를 다 못 맞춰 떨어진 전 규칙 길(`CompFrame.entry` 셋째 길 — 비스듬한 첫 깎는 줄에 직각으로 오는 두 축 G0, 두 꺾인 길이 소재에서 r 이상인지
                // 보고 낸다 — 검증 2026-11-05 의 설계)이라 세기만 한다(남은 것 — 고침 보고 unresolved)
                V1112.companyFlow(nc)?.let {
                    val same = V1112.oldJar.isFile && V1112.oldTaper(V1112.Case("", true, r, c, single = i)).nc4x == nc
                    if (same) bump("회사 흐름 ${it.substringBefore(" ")} — 옛 b305c71 글과 같음(전 규칙 셋째 길)") else fail("회사 흐름 ${it.substringBefore(" ")}", 0.0, "$it · $ex")
                }
            }
        V1113.log(name, "뒤집은 내경 작은 구멍 기계 보정: 돌린 글 $runs")
        for ((k, v) in stat) V1113.log(name, "  $k: $v")
        V1113.log(name, "문제 꼴 ${bad.size}:")
        for ((k, v) in bad) V1113.log(name, "- ${v.first}건 $k · 가장 나쁜 ${V1113.f(v.second.first)}\n   ${v.second.second}")
        assertTrue(runs > 1000, "$stat")
        assertEquals(0, bad.size, bad.entries.take(4).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second.second}" })
    }
}

/**
 * F5(미룸)를 따진다: 라운드 뒤집은 직접 보정의 첫 급송이 도면 · 그림에 없는 부품 앞면 · 막힌 시작 벽을 파는가 — 재현 입력과 무작위 형상 × 팁 표준 · 0~9.
 * 견줌으로 뒤집은 기계 보정 · 기본 방향 직접 보정도 같이 잰다
 */
class Verify1113RoundFlipEntryTest {
    private class D(val draw: Pair<Double, String>, val face: Pair<Double, String>, val wall: Pair<Double, String>, val hole: Boolean = false) {
        val worst get() = listOf(draw, face, wall).maxBy { it.first }
        val where get() = when (worst) {
            face -> "앞면"
            wall -> if (hole) "관통 구멍 연장선" else "막힌 시작 벽"
            else -> "도면"
        }
    }

    /**
     * [through] = 관통 구멍: 뒤집은 시작 좌표 너머는 같은 지름의 구멍이라(사용자 답 2026-11-13 — 시작 좌표 너머에서 들어온다) 막힌 시작 벽 대신 그 구멍 벽
     * (시작 직선을 거꾸로 늘인 선과 축 거울)을 잰다
     */
    private fun measure(segs: List<Seg>, inner: Boolean, flip: Boolean, nc: List<String>, run: SimRun, r: Double, through: Boolean = false): D? {
        val t0 = CompFrame.startDir(segs.first())
        val upStart = flip && segs.first() is Line && abs(t0.z) > abs(t0.x) && t0.z > 0
        val none = Double.NEGATIVE_INFINITY to ""
        val draw = V1113.entryDepth(run, nc, r, segs + segs.map(V1112::mirror)) ?: return null
        val face = V1113.entryDepth(run, nc, r, V1113.frontFace(V1113.ends(segs), inner)) ?: none
        val hole = upStart && through
        val ext = Line(segs.first().start, segs.first().start - t0 * 1000.0)
        val wall = when {
            hole -> V1113.entryDepth(run, nc, r, listOf(ext, V1112.mirror(ext))) ?: none
            upStart -> V1113.entryDepth(run, nc, r, V1112.walls(segs, inner, true, false)) ?: none
            else -> none
        }
        return D(draw, face, wall, hole)
    }

    @Test
    fun f5Repro() {
        val name = "f5"
        V1113.begin(name)
        val i = RoundInput(type = 3, mainR = 3.911, cornerR1 = 1.143, l1 = 0.887664094084152, noseR = 0.4, tip = 4, zClear = 1.703, legLen = 1.539, inner = true, flip = true)
        val fails = mutableListOf<String>()
        // Z 여유 2.2 면 팁 0 · 8(팁 벡터 z 0)의 진입 노즈 중심이 앞면 앞 0.176(< r) 이라 접근 줄 없이 들어온다(1회차 뒤 더함 — 손셈 −2.027 + 2.203)
        val j = i.copy(zClear = 2.203)
        for (inp in listOf(i, i.copy(tip = 0), i.copy(tip = 8), i.copy(tip = null), i.copy(tip = 1), i.copy(tip = 5), j.copy(tip = 0), j.copy(tip = 8), j.copy(tip = null))) {
            val res = RoundCalc.calc(inp)
            val s = res.solution ?: continue
            val nc = V1112.direct(s.nc)
            val run = V1112.sim(nc, inp.noseR, s.tip)
            val d = measure(s.drawing.segs, true, true, nc, run, inp.noseR)!!
            V1113.log(name, "$inp ${res.warnings}: 첫 급송 파고듦 도면 ${V1113.f(d.draw.first)} ${d.draw.second} · 앞면 ${V1113.f(d.face.first)} ${d.face.second}\n  $nc")
            if (d.worst.first > 2e-3) fails += "팁 ${inp.tip}: ${d.where} ${V1113.f(d.worst.first)} ${d.worst.second} ${res.warnings}\n $nc"
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    @Test
    fun entryGrid() {
        val name = "roundFlipEntry"
        V1113.begin(name)
        val rnd = Random(20261113)
        val g = GeometricVerifyTest()
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }
        val bad = sortedMapOf<String, Pair<Int, Pair<Double, String>>>()
        val modes = listOf("뒤집기 직접", "뒤집기 기계", "기본 직접")
        repeat(300) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val base = g.randomRound(type, rnd, inner).input
            for (tip in listOf<Int?>(null) + (0..9).toList()) for (mode in 0..2) {
                val inp = base.copy(tip = tip, flip = mode < 2)
                val res = if (mode == 1) RoundCalc.machine(inp) else RoundCalc.calc(inp)
                val s = res.solution ?: continue
                val segs = s.drawing.segs
                val r = inp.noseR
                val nc = if (mode == 1) s.nc else V1112.direct(s.nc)
                val run = V1112.sim(nc, r, s.tip)
                if (run.stop != null) {
                    bump("${modes[mode]} 멈춤")
                    continue
                }
                val d = measure(segs, inner, inp.flip, nc, run, r, inp.through) ?: continue
                bump("${modes[mode]} 돌림")
                val t0 = CompFrame.startDir(segs.first())
                val branch = if (!inp.flip) "기본" else if (segs.first() is Line && abs(t0.z) > abs(t0.x) && t0.z > 0) (if (inp.through) "관통에서 시작" else "막힌 시작") else if (segs.first().start.x <= 1e-6) "축에서 시작" else "그 밖"
                val w = d.worst
                if (w.first > 2e-3) {
                    bump("${modes[mode]} 파고듦 팁 ${tip ?: "표준"}")
                    val key = "${modes[mode]} | ${if (inner) "내경" else "외경"} 형상 $type | 팁 ${tip ?: "표준"} | $branch | ${d.where} | ${if (RoundWarning.AGAINST_TOOL in res.warnings) "AGAINST_TOOL" else "경고 없음"}"
                    val p = bad[key]
                    val ex = "${V1113.f(w.first)} ${w.second} · $inp ${res.warnings}\n   ${nc.take(5)}"
                    bad[key] = ((p?.first ?: 0) + 1) to (if (p == null || w.first > p.second.first) w.first to ex else p.second)
                }
            }
        }
        V1113.log(name, "라운드 첫 급송(진입) 파고듦 — 무작위 300 형상 × 팁 11:")
        for ((k, v) in stat) V1113.log(name, "  $k: $v")
        V1113.log(name, "문제 꼴 ${bad.size}:")
        for ((k, v) in bad) V1113.log(name, "- ${v.first}건 $k · 가장 깊음 ${V1113.f(v.second.first)}\n   ${v.second.second}")
        assertEquals(0, bad.size, bad.entries.take(4).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second.second}" })
    }
}

/** 뒤집은 테이퍼 직접 보정(G40)의 첫 급송 — F5 와 같은 꼴(앞면 · 막힌 시작 벽 · 도면)이 테이퍼에도 있나. 짧은 모양(길이 1.5) · 작은 구멍 × 팁 표준 · 0 · 역바이트 */
class Verify1113FlipTaperDirectTest {
    @Test
    fun flippedDirectEntryGrid() {
        val name = "flipTaperDirect"
        V1113.begin(name)
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }
        val bad = sortedMapOf<String, Pair<Int, Pair<Double, String>>>()
        var runs = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (kind in 0..2) for (geom in listOf(1, 2)) for (r in listOf(0.2, 0.8, 1.2)) for (zc in listOf(0.0, 5.0))
            for (lead in listOf(null, 3.0)) for (tail in listOf(null, 3.0)) for (cr in listOf(0.0, 0.5)) for (tip in listOf(null, 0, if (inner) 1 else 4)) {
                val i = V1112.single(inner, type, kind, r, zc, tip, lead, tail, cr, geom, flip = true)
                val res = TaperCalc.calc(i)
                val s = res.solution ?: continue
                val segs = s.drawing.segs
                val rg = V1113.cutRange(s).takeIf { !it.isEmpty() } ?: continue
                val nc = V1112.direct(s.ncG40)
                val run = V1112.sim(nc, r, s.tip)
                if (run.stop != null) {
                    bump("멈춤")
                    continue
                }
                runs++
                val dm = V1113.directMat(s, inner, res.warnings)
                val w = V1113.directEntry(run, nc, r, dm) ?: continue
                val all = V1113.depth(run, nc, r, dm.cut)
                bump("돌림 팁 ${tip ?: "표준"}")
                if (w.first > 2e-3) {
                    bump("진입 파고듦 팁 ${tip ?: "표준"}")
                    val key = "${if (inner) "내경" else "외경"} 형상 $type | 팁 ${tip ?: "표준"} | ${w.second} | ${res.warnings.filter { it == TaperWarning.AGAINST_TOOL || it == TaperWarning.ENTRY_PAST_AXIS }}"
                    val p = bad[key]
                    val ex = "${V1113.f(w.first)} ${w.third} · $i ${res.warnings}\n   ${nc.take(5)}"
                    bad[key] = ((p?.first ?: 0) + 1) to (if (p == null || w.first > p.second.first) w.first to ex else p.second)
                } else if (all.first > 2e-3) {
                    val key = "${if (inner) "내경" else "외경"} 형상 $type | 팁 ${tip ?: "표준"} | 진입 뒤 도면 파고듦 ${all.second.substringBefore(" ")}"
                    val p = bad[key]
                    val ex = "${V1113.f(all.first)} ${all.second} · $i ${res.warnings}\n   $nc"
                    bad[key] = ((p?.first ?: 0) + 1) to (if (p == null || all.first > p.second.first) all.first to ex else p.second)
                }
            }
        V1113.log(name, "뒤집은 테이퍼 직접 보정 첫 급송: 돌린 글 $runs")
        for ((k, v) in stat) V1113.log(name, "  $k: $v")
        V1113.log(name, "문제 꼴 ${bad.size}:")
        for ((k, v) in bad) V1113.log(name, "- ${v.first}건 $k · 가장 깊음 ${V1113.f(v.second.first)}\n   ${v.second.second}")
        assertTrue(runs > 1000, "$stat")
        assertEquals(0, bad.size, bad.entries.take(4).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second.second}" })
    }
}

/**
 * 합친 뒤의 맞물림: 테이퍼 원호(⑦) × 뒤집기 × 새 진입 × 시뮬레이터 90° 규칙. 호 R = 가장 작은 R(꼭 90° 바깥 꺾임) · 그보다 0.001 작은 R(받는 띠, 계산기가 받으면) ·
 * 1.5 배. 기계 보정은 알람 없이 돌고(받는 띠 끝 · 큰 노즈의 COMP_ACUTE_OUTER 는 알려진 남은 것 — 따로 셈) 파고듦 · 덜 깎임 0 · 진입 급송 틈 c/4 이상,
 * 직접 보정은 파고듦 · 덜 깎임 0 · 첫 급송이 앞면 · 벽 · 도면을 파지 않음
 */
class Verify1113FlipArcTaperTest {
    @Test
    fun flippedArcGrid() {
        val name = "flipArcTaper"
        V1113.begin(name)
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }
        val bad = sortedMapOf<String, Pair<Int, String>>()
        fun fail(k: String, ex: String) {
            bad[k] = ((bad[k]?.first ?: 0) + 1) to (bad[k]?.second ?: ex)
        }
        var runs = 0
        var worstUnder = 0.0
        val rkName = listOf("가장 작은 R", "R − 0.001", "1.5 배")
        for (inner in listOf(false, true)) for (type in 1..5) for (convex in listOf(true, false)) for (geom in listOf(0, 2)) for (rk in 0..2)
            for (r in listOf(0.2, 0.8, 1.2)) for (zc in listOf(0.0, 5.0)) for (lead in listOf(null, 3.0)) for (tail in listOf(null, 3.0)) for (cr in listOf(0.0, 0.5)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x1 = if (geom == 0) (if (inner) (if (type == 5) 30.0 else 40.0) else (if (type == 5) 50.0 else 40.0)) else (if (inner) (if (type == 5) 6.0 else 10.0) else (if (type == 5) 10.0 else 6.0))
                val dx = if (geom == 0) 10.0 else 4.0
                val len = if (geom == 0) 5.0 else 8.0
                val x2 = x1 + up * dx
                val minR = TaperCalc.arcMinR(x1, 0.0, x2, -len)!!
                val arcR = when (rk) {
                    0 -> ceil(minR * 1000) / 1000
                    1 -> (ceil(minR * 1000) - 1) / 1000
                    else -> ceil(1.5 * minR * 1000) / 1000
                }
                val i = V1113.taper(inner, type, r, zc, null, lead, tail, cr, x1, x2, len, arcR, convex, flip = true)
                val res = TaperCalc.calc(i)
                val s = res.solution
                if (s == null) {
                    bump("안 풀림 ${rkName[rk]} ${res.warnings.sorted()}")
                    continue
                }
                val segs = s.drawing.segs
                val mat = segs + segs.map(V1112::mirror)
                val alarm = V1113.shapeAlarm(res.warnings)
                val walls = if (TaperWarning.SHORT_END_PAST in res.warnings) emptyList() else V1112.walls(segs, inner, s.fromStart, false)
                val rg = V1113.cutRange(s).takeIf { !it.isEmpty() } ?: continue
                val c = max(zc, r)
                for (m in listOf(true, false)) {
                    val kt = if (m) "기계" else "직접"
                    val nc = if (m) s.ncG41G42 else V1112.direct(s.ncG40)
                    val run = V1112.sim(nc, r, s.tip)
                    val tag = "$i ${res.warnings}\n   $nc"
                    val shape = "${if (inner) "내경" else "외경"} 형상 $type ${if (convex) "볼록" else "오목"} ${rkName[rk]}"
                    val stop = run.stop
                    if (stop != null) {
                        if (alarm) bump("$kt 멈춤(형상 경고 꼴)")
                        else if (m && stop.code == IssueCode.COMP_ACUTE_OUTER && arcR < minR) bump("기계 COMP_ACUTE_OUTER — 받는 띠(R < 가장 작은 R) 노즈 $r")
                        else fail("$kt 멈춤 ${stop.code} | $shape", "$stop · $tag")
                        continue
                    }
                    runs++
                    bump("$kt 돌림")
                    // 직접 보정: 소재 = 깎는 요소(그림만 요소는 뺌 — 1회차 뒤 고침), 진입(첫 급송 · 첫 이송 줄)은 따로 세고(뒤집은 직접 진입은
                    // Verify1113FlipTaperDirectTest 가 잰다) 깎는 몫(첫 이송 뒤)만 여기서 실패로
                    val dm = V1113.directMat(s, inner, res.warnings)
                    val ms = V1113.moves(run)
                    val ff = ms.indexOfFirst { it.kind == MoveKind.FEED }
                    var g = Double.NEGATIVE_INFINITY
                    var gAt = ""
                    for (mv in (if (m) ms else ms.drop(ff + 1))) for (p in V1112.samples(mv.nose)) {
                        val d = r - V1112.minDist(if (m) mat else dm.cut, p)
                        if (d > g) {
                            g = d
                            gAt = "${mv.kind} '${V1113.lineOf(nc, mv)}'"
                        }
                    }
                    if (g > 2e-3) fail("$kt 파고듦 | $shape | ${gAt.substringBefore(" ")}", "${V1113.f(g)} $gAt · $tag")
                    if (!alarm && !V1113.shortEnd(res.warnings)) {
                        val (u, uAt) = V1113.under(run, r, segs, rg.first, rg.last)
                        if (!u.isNaN()) worstUnder = max(worstUnder, u)
                        if (u > 2e-3) fail("$kt 덜 깎임 | $shape", "${V1113.f(u)} $uAt · $tag")
                    }
                    if (m) {
                        val gap = V1112.entryGap(run, r, mat + walls)!!
                        if (gap < c / 4 - 2e-3 && TaperWarning.ENTRY_NOT_FOUND !in res.warnings) fail("기계 진입 급송 틈 < c/4 | $shape", "${V1113.f(gap)} · $tag")
                        V1112.companyFlow(nc)?.let { fail("기계 회사 흐름 $it", tag) }
                    } else {
                        val e = V1113.directEntry(run, nc, r, dm)
                        var first = Double.NEGATIVE_INFINITY
                        if (ff >= 0) for (p in V1112.samples(ms[ff].nose)) first = max(first, r - V1112.minDist(dm.cut + dm.faceCalc + dm.walls, p))
                        if (e != null && e.first > 2e-3) bump("직접 첫 급송 파고듦(${e.second}) — FlipTaperDirect 몫")
                        else if (first > 2e-3) bump("직접 첫 이송 줄 파고듦 — FlipTaperDirect 몫")
                    }
                }
            }
        V1113.log(name, "뒤집은 원호 테이퍼: 돌린 글 $runs · 가장 큰 덜 깎임 ${V1113.f(worstUnder)}")
        for ((k, v) in stat) V1113.log(name, "  $k: $v")
        V1113.log(name, "문제 꼴 ${bad.size}:")
        for ((k, v) in bad) V1113.log(name, "- ${v.first}건 $k\n   예: ${v.second}")
        assertTrue(runs > 1000, "$stat")
        assertEquals(0, bad.size, bad.entries.take(5).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second}" })
    }
}

/**
 * V12-1 규칙(넘은 몫만 0.001 로 재고 원호 곡률 몫은 안 봄)을 직선 · 볼록 · 오목 원호 이음으로 깨 본다: 90° ~ 90.6° 바깥 꺾임을 받으면 노즈 길이 두 요소를
 * 파지 않고 끊기지 않으며, 받은 넘은 각이 OUTER_SIN(0.573°) 안이고 직선끼리는 0.001/r rad 안인가. 이음 노즈가 꼭짓점에서 얼마나 멀리 서는지(× r)도 적는다
 */
class Verify1113SimCornerTest {
    private fun rot(v: Pt, a: Double) = Pt(v.z * cos(a) - v.x * sin(a), v.z * sin(a) + v.x * cos(a))
    private fun left(v: Pt) = Pt(-v.x, v.z)
    private fun right(v: Pt) = Pt(v.x, -v.z)

    /** e 에서 끝나는(끝 접선 t) 요소: 0 직선 4 mm, 1 · 2 = 중심이 왼쪽 · 오른쪽인 원호(반지름 rad, 40°) */
    private fun endingAt(e: Pt, t: Pt, k: Int, rad: Double): Seg = when (k) {
        0 -> Line(e - t * 4.0, e)
        else -> {
            val m = if (k == 1) left(t) else right(t)
            val c = e + m * rad
            val ae = kotlin.math.atan2(e.x - c.x, e.z - c.z)
            val a0 = if (k == 1) ae - Math.toRadians(40.0) else ae + Math.toRadians(40.0)
            Arc(c + Pt(cos(a0), sin(a0)) * rad, e, c, cw = k != 1)
        }
    }

    /** e 에서 시작하는(처음 접선 t) 요소 */
    private fun startingAt(e: Pt, t: Pt, k: Int, rad: Double): Seg = when (k) {
        0 -> Line(e, e + t * 4.0)
        else -> {
            val m = if (k == 1) left(t) else right(t)
            val c = e + m * rad
            val a0 = kotlin.math.atan2(e.x - c.x, e.z - c.z)
            val a1 = if (k == 1) a0 + Math.toRadians(40.0) else a0 - Math.toRadians(40.0)
            Arc(e, c + Pt(cos(a1), sin(a1)) * rad, c, cw = k != 1)
        }
    }

    private fun words(s: Seg): List<String> = NcLines().apply { if (s is Arc) arc(s) else move("G1", s.end) }.lines

    @Test
    fun junctionsJustOverNinety() {
        val name = "simCorner"
        V1113.begin(name)
        val rnd = Random(1113)
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) {
            stat[k] = (stat[k] ?: 0) + 1
        }
        val bad = mutableListOf<String>()
        var farthest = 0.0
        var farAt = ""
        val maxAccepted = sortedMapOf<String, Double>()
        repeat(4000) {
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val g42 = rnd.nextBoolean()
            val ta = Pt(1.0, 0.0).let { rot(it, rnd.nextDouble(0.0, 2 * PI)) }
            val delta = listOf(0.0, 0.003, 0.01, 0.03, 0.06, 0.1, 0.2, 0.3, 0.5, 0.6)[rnd.nextInt(10)]
            val tb = rot(ta, Math.toRadians(90 + delta) * (if (g42) 1 else -1))
            val e = Pt(0.0, 60.0)
            val ka = rnd.nextInt(3)
            val kb = rnd.nextInt(3)
            fun rad() = listOf(r * 1.05, r * 2.1, r * 3.0, 5.0, 20.0)[rnd.nextInt(5)]
            val a = endingAt(e, ta, ka, rad())
            val b = startingAt(e, tb, kb, rad())
            val st = a.start - CompFrame.startDir(a) * 3.0
            val en = b.end + CompFrame.endDir(b) * 3.0
            fun w(p: Pt) = "X" + Fmt.nc(2 * p.x) + "Z" + Fmt.nc(p.z)
            val la = words(a)
            val lb = words(b)
            val side = if (g42) "G42" else "G41"
            val body = listOf("G0${w(st)}", "${side}G1${w(a.start)}F.1") + la + lb + listOf("G40G1${w(en)}")
            val text = (V1112.pre + body + "M30").joinToString("\n")
            val run = GSim.run(SimInput(text, MachineSetup(), mapOf(1 to ToolOffset(r, 3))))
            val lineB = V1112.pre.size + 2 + la.size + 1
            val kinds = "${listOf("직선", "원호(왼 중심)", "원호(오른 중심)")[ka]} → ${listOf("직선", "원호(왼 중심)", "원호(오른 중심)")[kb]}"
            val stop = run.stop
            if (stop != null) {
                bump(if (stop.code == IssueCode.COMP_ACUTE_OUTER && stop.line == lineB) "멈춤 COMP_ACUTE_OUTER δ $delta" else "멈춤 ${stop.code}")
                return@repeat
            }
            bump("받음 δ $delta")
            val key = "$kinds r $r"
            if (delta > 0.0) maxAccepted[key] = max(maxAccepted[key] ?: 0.0, delta)
            if (delta > 0.573) bad += "넘은 각 $delta° 를 받음 — OUTER_SIN 밖 $kinds r $r\n$text"
            if (ka == 0 && kb == 0 && Math.toRadians(delta) > 1.5e-3 / r) bad += "직선끼리 넘은 각 $delta° 를 받음 — 0.001/r rad 밖 r $r\n$text"
            val ms = V1113.moves(run)
            for ((p, q) in ms.zipWithNext()) if ((p.nose.end - q.nose.start).length > 1e-6) bad += "노즈 길 끊김 ${p.line} → ${q.line} $kinds r $r\n$text"
            // A · B 줄의 노즈만(시작 · 취소 블록의 옮김은 시험 그림 밖)
            val part = ms.filter { it.line in (V1112.pre.size + 3)..(V1112.pre.size + 2 + la.size + lb.size) }
            var dmin = Double.MAX_VALUE
            for (m in part) for (p in V1112.samples(m.nose, 0.01)) dmin = min(dmin, min(V1112.dist(a, p), V1112.dist(b, p)) - r)
            if (dmin < -2e-3) bad += "노즈가 도면을 팜 ${V1113.f(-dmin)} δ $delta $kinds r $r\n$text"
            val nose = run.ghosts.lastOrNull { it.line == lineB - 1 }?.nose
            if (nose != null) {
                val far = (nose - e).length / r
                if (far > farthest) {
                    farthest = far
                    farAt = "δ $delta $kinds r $r 이음 노즈 $nose"
                }
            }
        }
        V1113.log(name, "이음 4000: $stat")
        V1113.log(name, "이음 노즈가 꼭짓점에서 가장 먼 것 ${V1113.f(farthest)} × r — $farAt")
        for ((k, v) in maxAccepted) V1113.log(name, "  받은 가장 큰 넘은 각 $k: $v°")
        bad.take(20).forEach { V1113.log(name, "! $it") }
        assertEquals(emptyList(), bad, bad.take(5).joinToString("\n\n"))
    }
}

/** 회귀(b305c71 jar 와 견줌 — 골든 말고 무작위 격자): 테이퍼 뒤집기 밖 · 라운드(기본 · 뒤집기 · 3점호) · 시뮬레이터 예제 */
class Verify1113RegressionTest {
    private fun need() = assumeTrue(V1112.oldJar.isFile, "옛 jar 가 없어 건너뜀: ${V1112.oldJar}")

    private fun r3(v: Double) = Math.round(v * 1000) / 1000.0

    @Test
    fun simExamplesSameAsOld() {
        need()
        val name = "regSimExamples"
        V1113.begin(name)
        val diffs = mutableListOf<String>()
        for (ex in SimExamples.all) for (form in listOf(CompStartup.C, CompStartup.AB)) {
            val input = SimInput(ex.text, MachineSetup(compStartup = form), ex.tools)
            val run = GSim.run(input)
            val o = V1113.oldCall("com.bmt.nosecalc.calc.sim.GSim", "run", input)
            val same = run.steps.toString() == V1113.get(o, "getSteps").toString() && run.stop.toString() == V1113.get(o, "getStop").toString() &&
                run.issues.toString() == V1113.get(o, "getIssues").toString() && run.totalSec == V1113.get(o, "getTotalSec")
            V1113.log(name, "${ex.name} 형식 $form: 걸음 ${run.steps.size} · 멈춤 ${run.stop} · 옛것과 같음 $same")
            if (!same) diffs += "${ex.name} $form"
        }
        V1113.log(name, "예제 ${SimExamples.all.size} × 형식 2 · 다름 ${diffs.size}")
        assertEquals(emptyList(), diffs)
    }

    @Test
    fun roundSameAsOld() {
        need()
        val name = "regRound"
        V1113.begin(name)
        val rnd = Random(1311)
        val g = GeometricVerifyTest()
        val diffs = mutableListOf<String>()
        var n = 0
        // 최종 확인 뒤 고침(F5 2026-11-13): 뒤집은 직접 보정은 첫 G1 앞 급송만 달라질 수 있다(첫 G1 부터 같고 급송이 더 파지 않음 — V1113.roundEntryOnly)
        var approachFixed = 0
        // 사용자 답 2026-11-13: 관통 구멍은 끝 · 시작 좌표 너머로 간다 — 뒤집은 글(직접 · 기계)과 기본 방향 기계 보정 글이 바뀐다(V1113.roundThroughOk)
        var throughChanged = 0
        fun same(
            flip: Boolean, m: Boolean, sol: RoundSolution?, o: List<String>?, w: Boolean, r: Double, inner: Boolean, through: Boolean = false, throughW: Boolean = false,
        ): Boolean {
            if (sol?.nc == o && w) return true
            if (flip && !m && w && V1113.roundEntryOnly(sol, o, r, inner)) {
                approachFixed++
                return true
            }
            if (through && (flip || m) && throughW && V1113.roundThroughOk(sol, o, m, r)) {
                throughChanged++
                return true
            }
            return false
        }
        repeat(1500) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val b = g.randomRound(type, rnd, inner).input
            val inp0 = if (rnd.nextBoolean()) b.copy(tip = rnd.nextInt(10)) else b
            for (inp in listOf(inp0, inp0.copy(flip = true))) for (m in listOf(false, true)) {
                val res = if (m) RoundCalc.machine(inp) else RoundCalc.calc(inp)
                val (o, w) = V1112.oldRound(inp, m)
                n++
                val throughW = V1113.roundThroughWarningsOk(res.warnings.map { it.name }, w, o != null)
                if (!same(inp.flip, m, res.solution, o, res.warnings.map { it.name } == w, inp.noseR, inp.inner, inp.through, throughW)) diffs += "$inp ${if (m) "기계" else "직접"}\n 옛 $o $w\n 새 ${res.solution?.nc} ${res.warnings}"
            }
        }
        var n3 = 0
        repeat(800) {
            val inner = rnd.nextBoolean()
            val x1 = r3(rnd.nextDouble(6.0, 60.0))
            val len = r3(rnd.nextDouble(1.0, 15.0))
            val x3 = r3(max(1.0, x1 + rnd.nextDouble(-10.0, 10.0)))
            val p1 = Pt(0.0, x1 / 2)
            val p3 = Pt(-len, x3 / 2)
            val d = p3 - p1
            val h = rnd.nextDouble(-0.45, 0.45) * d.length
            val p2 = (p1 + p3) * 0.5 + Pt(-d.x, d.z) * (h / d.length)
            if (p2.x <= 0.3 || abs(h) < 0.01) return@repeat
            val tip = if (rnd.nextInt(3) == 0) rnd.nextInt(10) else null
            val nose = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val zc = r3(rnd.nextDouble(0.0, 3.0))
            for (flip in listOf(false, true)) for (m in listOf(false, true)) {
                val a = Arc3Input(inner = inner, noseR = nose, tip = tip, x1 = x1, z1 = 0.0, x2 = r3(2 * p2.x), z2 = r3(p2.z), x3 = x3, z3 = -len, zClear = zc, flip = flip)
                val res = if (m) RoundCalc.machine(a) else RoundCalc.arc3(a)
                val (o, w) = V1113.oldRoundResult(V1113.oldCall("com.bmt.nosecalc.calc.RoundCalc", if (m) "machine" else "arc3", a))
                n3++
                if (!same(flip, m, res.solution, o, res.warnings.map { it.name } == w, nose, inner)) diffs += "$a ${if (m) "기계" else "직접"}\n 옛 $o $w\n 새 ${res.solution?.nc} ${res.warnings}"
            }
        }
        V1113.log(name, "라운드 회귀: TYPE 글 $n · 3점호 글 $n3 · 뒤집은 직접 보정 접근만 바뀜(고침 F5) $approachFixed · 관통 구멍 너머로(사용자 답 11-13) $throughChanged · 다름 ${diffs.size}")
        diffs.take(10).forEach { V1113.log(name, it) }
        assertEquals(0, diffs.size, diffs.take(4).joinToString("\n\n"))
    }

    /**
     * 무작위 치수 · 팁 0~9 · 모서리 R · 호 · 시작 · 끝(내경 바닥 X0 포함). 뒤집기 밖 다섯 꼴은 G41/G42 가 같고, G40 은 내경 바닥 X0 끝(통합 고침)과 최종 확인 뒤 고침
     * (2026-11-13 — 노즈 기준 막힌 시작 · 끝 · 진입, 내경 축 쪽 노즈 중심 멈춤: 시뮬레이터로 옛 글보다 나쁘지 않은 것만 — V1113.vsOld)만 다를 수 있다
     */
    @Test
    fun taperSameAsOldOutsideFlip() {
        need()
        val name = "regTaper"
        V1113.begin(name)
        val rnd = Random(1313)
        val combos = listOf(Triple(false, false, true), Triple(false, false, false), Triple(true, false, false), Triple(false, true, true), Triple(true, true, true))
        val diffs = mutableListOf<String>()
        var n = 0
        var solved = 0
        var axisChanged = 0
        var fixChanged = 0
        val crs = listOf(0.0, 0.0, 0.2, 0.5, 1.0)
        repeat(2500) {
            val inner = rnd.nextBoolean()
            val type = 1 + rnd.nextInt(5)
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val tip = if (rnd.nextInt(3) == 0) rnd.nextInt(10) else null
            val up = if (inner == (type == 5)) 1.0 else -1.0
            val dx = r3(rnd.nextDouble(0.5, 20.0))
            val len = r3(rnd.nextDouble(0.5, 15.0))
            val x1 = r3(if (up > 0) rnd.nextDouble(2.0, 60.0) else rnd.nextDouble(dx + 1.0, dx + 60.0))
            val x2 = r3(x1 + up * dx)
            val kind = rnd.nextInt(3)
            val arcR = if (kind > 0) TaperCalc.arcMinR(x1, 0.0, x2, -len)?.let { ceil(it * listOf(1.0, 1.3, 3.0)[rnd.nextInt(3)] * 1000) / 1000 } else null
            val zc = listOf(0.0, 0.5, 1.0, 3.0, 5.0)[rnd.nextInt(5)]
            val lead = listOf(null, 0.5, 1.0, 3.0)[rnd.nextInt(4)]
            val tail = listOf(null, 0.5, 1.0, 3.0)[rnd.nextInt(4)]
            val axisBottom = inner && (type == 2 || type == 4) && rnd.nextInt(3) == 0
            for ((flip, reverse, approach) in combos) {
                var i = V1113.taper(inner, type, r, zc, tip, lead, tail, crs[rnd.nextInt(5)], x1, x2, len, arcR, kind == 1, false, reverse, approach, crs[rnd.nextInt(5)])
                if (axisBottom) i = i.copy(end = 0.0)
                if (flip) i = i.copy(x1 = i.x2, z1 = i.z2, x2 = i.x1, z2 = i.z1, cornerR1 = i.cornerR2, cornerR2 = i.cornerR1, start = i.end, end = i.start, flip = true)
                val c = V1112.Case(i.toString(), inner, r, max(zc, r), single = i)
                val res = c.calc()
                val o = V1112.oldTaper(c)
                n++
                val s = res.solution
                if (s != null) solved++
                if (s != null && s.ncG40 != o.nc40 && V1112.axisEnd(s, inner)) axisChanged++
                // 최종 확인 뒤 고침(2026-11-13): 직접 보정 · 그 탓의 ENTRY_PAST_AXIS · 새 BAD_LEG 는 시뮬레이터로 나빠지지 않은 것만(V1113.vsOld)
                val (bad, fixed) = V1113.vsOld(c, res, o) { V1112.axisEnd(it, inner) }
                if (fixed) fixChanged++
                bad.forEach { diffs += "$it · $i" }
            }
        }
        var nd = 0
        repeat(600) {
            val inner = rnd.nextBoolean()
            val type = 1 + rnd.nextInt(4)
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val zc = listOf(0.0, 1.0, 5.0)[rnd.nextInt(3)]
            val lead = listOf(null, 1.0, 3.0)[rnd.nextInt(3)]
            val tail = listOf(null, 3.0)[rnd.nextInt(2)]
            val len = r3(rnd.nextDouble(1.0, 10.0))
            val cr = crs[rnd.nextInt(5)]
            for ((flip, reverse, approach) in combos) {
                val i = V1112.double(inner, type, r, zc, lead, tail, cr, len, flip, reverse, approach)
                val c = V1112.Case(i.toString(), inner, r, max(zc, r), double = i)
                val res = c.calc()
                val o = V1112.oldTaper(c)
                nd++
                val (bad, fixed) = V1113.vsOld(c, res, o)
                if (fixed) fixChanged++
                bad.forEach { diffs += "2중 $it · $i" }
            }
        }
        V1113.log(name, "테이퍼 회귀(뒤집기 밖 다섯 꼴): 홑 $n(풀림 $solved, 내경 바닥 X0 끝 G40 바뀜 $axisChanged) · 2중 $nd · 고침(2026-11-13)으로 G40 바뀜 $fixChanged · 다름 ${diffs.size}")
        diffs.take(10).forEach { V1113.log(name, it) }
        assertEquals(0, diffs.size, diffs.take(4).joinToString("\n\n"))
    }
}

/**
 * 재확인 1회차에서 새로 찾은 것(b305c71 과 글이 같은 옛 동작) — 직접 보정(G40) 글이 팁 벡터의 Z 성분을 안 보는 자리. 표준 팁(외경 3 · 내경 2 — 팁 벡터 z −r)이면
 * 노즈가 넣은 면 · 앞면에 닿기만 하는데, 팁 벡터 z 가 0(팁 0 · 6 · 8 · 9)이면 노즈가 r, +r(팁 1 · 4 · 5)이면 그보다 더 넘어간다. 또 뒤집은 작은 구멍의 접근 X 가
 * 축 너머로 가 맞은편 벽을 판다. 재현 입력마다 시뮬레이터(형식 C · 급송 dog-leg)로 잰 깊이(그 면에서 노즈가 들어간 만큼 — 노즈 중심이 면을 지나면 r 로 셈)
 */
class Verify1113DirectTipTest {
    private fun check(name: String, i: TaperInput, mat: (TaperSolution) -> List<Seg>, entryOnly: Boolean, fails: MutableList<String>) {
        val res = TaperCalc.calc(i)
        val s = res.solution
        if (s == null) {
            fails += "안 풀림 $i ${res.warnings}"
            return
        }
        val nc = V1112.direct(s.ncG40)
        val run = V1112.sim(nc, i.noseR, s.tip)
        if (run.stop != null) {
            fails += "멈춤 ${run.stop} $i"
            return
        }
        val d = if (entryOnly) V1113.entryDepth(run, nc, i.noseR, mat(s))!! else V1113.depth(run, nc, i.noseR, mat(s))
        V1113.log(name, "팁 ${i.tip ?: "표준"} ${if (i.flip) "뒤집기" else "기본"} ${res.warnings}: 깊이 ${V1113.f(d.first)} ${d.second}\n  $i\n  $nc")
        if (d.first > 2e-3) fails += "팁 ${i.tip ?: "표준"} ${if (i.flip) "뒤집기" else "기본"}: ${V1113.f(d.first)} ${d.second} ${res.warnings}\n $nc"
    }

    /** 외경 형상 3(T1 X40 Z0 → T2 X50 Z−1.5)의 척 쪽 막힌 끝 Z−4.5(기본) · 같은 형상 뒤집기의 막힌 시작 Z−4.5 — 노즈 1.2, 넣은 Z 너머(벽)를 파나 */
    @Test
    fun taperBlockedStartEnd() {
        val name = "directTipBlocked"
        V1113.begin(name)
        val fails = mutableListOf<String>()
        val a = TaperInput(false, 3, false, 1.2, null, 40.0, 0.0, 50.0, -1.5, null, 0.0, 0.0, 0.0, null, -4.5)
        val b = a.copy(x1 = 50.0, z1 = -1.5, x2 = 40.0, z2 = 0.0, start = -4.5, end = null, flip = true)
        for (tip in listOf(null, 0, 8, 4, 5)) {
            check(name, a.copy(tip = tip), { V1112.walls(it.drawing.segs, false, false, true) }, false, fails)
            check(name, b.copy(tip = tip), { V1112.walls(it.drawing.segs, false, it.fromStart, false) }, true, fails)
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /** 기본 방향 내경 형상 4(입구 Ø7 · Ø20 → 바닥 Ø6, 시작 Z3 — 부품 앞면 Z3) · Z 여유 0: 진입 첫 급송이 앞면을 지나나 */
    @Test
    fun taperDefaultEntryFrontFace() {
        val name = "directTipEntry"
        V1113.begin(name)
        val fails = mutableListOf<String>()
        val small = TaperInput(inner = true, type = 4, noseR = 0.2, x1 = 7.0, z1 = 0.0, x2 = 6.0, z2 = -0.5, zClear = 0.0, start = 3.0, end = 0.0)
        val big = TaperInput(inner = true, type = 4, noseR = 1.2, x1 = 20.0, z1 = 0.0, x2 = 6.0, z2 = -6.0, zClear = 0.0, start = 3.0, end = 0.0)
        for (base in listOf(small, big)) for (tip in listOf(null, 0, 1, 5)) {
            check(name, base.copy(tip = tip), { s -> V1113.frontFace(V1113.ends(s.drawing.segs), true) + s.drawing.segs + s.drawing.segs.map(V1112::mirror) }, true, fails)
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 내경 작은 구멍(입구 Ø10 → 안 Ø6, 깊이 8 — 끝 비움) · Z 여유 5 · 노즈 0.8 의 직접 보정 끝 후퇴: 기본 방향 형상 1 · 3 은 `X6.Z-8.702` 뒤 `G0X-4.`(공구 쪽 X 로 c),
     * 뒤집기 형상 5 는 입구에서 `G0X-4.` — 노즈 중심이 축 너머 2.8 이라 맞은편 Ø6 벽을 파나(2026-11-12 통합 고침 T2 는 바닥 단면 X0 끝만 고쳤다).
     * 앱은 ENTRY_PAST_AXIS('Z 여유를 줄이세요')를 띄운다
     */
    @Test
    fun taperInnerExitPastAxis() {
        val name = "directTipExit"
        V1113.begin(name)
        val fails = mutableListOf<String>()
        val t1 = TaperInput(inner = true, type = 1, noseR = 0.8, x1 = 10.0, z1 = 0.0, x2 = 6.0, z2 = -8.0, zClear = 5.0)
        val t5f = TaperInput(inner = true, type = 5, noseR = 0.8, x1 = 10.0, z1 = -8.0, x2 = 6.0, z2 = 0.0, zClear = 5.0, flip = true)
        for (inp in listOf(t1, t1.copy(type = 3), t1.copy(zClear = 2.0), t5f)) {
            check(name, inp, { s -> V1113.cutRange(s).let { rg -> s.drawing.segs.subList(rg.first, rg.last + 1) }.let { c -> c + c.map(V1112::mirror) } }, false, fails)
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /** 뒤집은 내경 작은 구멍(바닥 Ø6 · 시작 Z−11 → 입구 Ø10, 깊이 8) · Z 여유 5 · 노즈 0.8: 직접 보정 접근 X(= 구멍 가장 좁은 X − c)가 축 너머로 가 맞은편 벽을 파나 */
    @Test
    fun taperFlippedSmallHoleApproach() {
        val name = "directTipSmallHole"
        V1113.begin(name)
        val fails = mutableListOf<String>()
        val i = TaperInput(inner = true, type = 1, noseR = 0.8, x1 = 6.0, z1 = -8.0, x2 = 10.0, z2 = 0.0, zClear = 5.0, start = -11.0, flip = true)
        for (inp in listOf(i, i.copy(zClear = 1.0), i.copy(noseR = 0.4, zClear = 3.0))) {
            check(name, inp, { s -> s.drawing.segs + s.drawing.segs.map(V1112::mirror) }, false, fails)
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }
}
