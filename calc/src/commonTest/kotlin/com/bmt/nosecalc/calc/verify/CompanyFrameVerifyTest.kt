package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.distTo
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.calc.segGap
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
 * 회사 꼴 G41/G42 글(계획 단계 4)의 성공 기준을 시뮬레이터 `GSim` 으로 본다(독립 검증 — 계산기의 틀 코드 `CompFrame` 은 쓰지 않는다).
 * 무작위 사례(테이퍼 G41/G42: 바깥 · 안 · 뒤집기 · 겹테이퍼 · 계산기 역방향 · 화면 역방향(거울) · 끝 막힘 · 빔 · 비움, 라운드 TYPE 1~6 바깥 · 안 · 관통 ·
 * 3점호, 보링바 잘린 글은 `HolderStopVerifyTest`) × 보정 시작 · 취소 형식 A · B / C × 팁 1~9 마다:
 * ① 멈춤 0(간섭 0)
 * ② 깎는 줄(첫 깎는 줄 `F.1` ~ 첫 빼는 줄 `F.3` 앞)의 노즈가 형식 · 팁과 상관없이 같다(1e-6)
 * ③ 막힌 끝은 노즈가 벽(끝좌표의 법선)에 닿고 어느 이동에서도 넘지 않는다 · 빈 끝은 노즈 접점이 끝좌표 너머까지 · 축에서 끝나면 노즈 중심이 축에 ·
 *    막힌 시작은 노즈가 시작 좌표 너머로 가지 않는다
 * ④ 깎는 줄 밖(진입 G0 · 보정 혼자 줄의 옮김 · 빼는 줄 · G40 줄의 옮김 · G0Z200.)의 노즈가 도면(내경은 축 거울 포함)을 파지 않는다(0.001 반올림까지)
 * 도면 자체에서 제어기가 알람을 낼 것(오목 R < r · 노즈보다 짧아 보정에서 빠지는 요소 — 앱 경고가 알림)과 시뮬레이터가 그리지 않는 바깥 꺾임 90° 넘음
 * (`COMP_ACUTE_OUTER`)은 형상 줄에서 멈출 때만 따로 센다
 */
class CompanyFrameVerifyTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** NC 0.001 반올림까지 받는 허용(mm) */
    private val tol = 1e-3

    /**
     * 끝의 종류. [SHORT_BLOCKED] = 막힌 끝 요소가 노즈R 쯤보다 짧아 계산기가 그 요소를 빼고 앞 요소 끝에서 벽을 따라 뺀다(2026-11-04 — 앞 요소에 자리가 없으면
     * (작은 오목 모서리 R · 짧은 경사) 전처럼 곧게 빼서 [OTHER] — 글의 마지막 형상 점이 끝좌표인지로 가린다. 전에는 늘 곧게 빼 끝좌표 너머로 가서
     * 이 시험은 그것을 FREE · OTHER 로 두었다): 노즈가 벽(끝좌표의 법선)을 넘지 않는지만 본다(그 요소를 빼므로 벽에 닿지는 않는다)
     */
    private enum class End { BLOCKED, SHORT_BLOCKED, FREE, AXIS, OTHER }

    private class Frame(
        val tag: String, val nc: List<String>, val r: Double, val drawing: List<Seg>, val inner: Boolean,
        val end: End, val endP: Pt, val endU: Pt, val startWall: Pair<Pt, Pt>?, val shapeAlarm: Boolean, val mirrored: Boolean = false,
    )

    private class Tally(val name: String) {
        var cases = 0
        var runs = 0
        var skipped = 0
        var simLimit = 0
        var narrow = 0
        val fails = mutableListOf<String>()
        /** 실패 갈래(첫 낱말)마다 수와 첫 예 */
        val kinds = linkedMapOf<String, Int>()
        val first = linkedMapOf<String, String>()
        fun fail(s: String) {
            val k = s.substringAfter(": ").substringBefore(" ")
            kinds[k] = (kinds[k] ?: 0) + 1
            if (k !in first) first[k] = s
            if (fails.size < 40) fails += s
        }
        fun report() = "$name: cases $cases runs $runs alarmSkip $skipped simLimit $simLimit narrow $narrow fails ${kinds.values.sum()} $kinds"
    }

    private fun run(f: Frame, tip: Int, form: CompStartup): SimRun =
        GSim.run(SimInput((pre + f.nc + "M30").joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(f.r, tip))))

    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    /** 노즈 길(직선은 정확히, 원호는 0.02 mm 마다)과 도면 사이 가장 짧은 거리 */
    private fun gap(nose: Seg, mat: List<Seg>): Double = when (nose) {
        is Line -> mat.minOf { segGap(nose, it).first }
        is Arc -> samples(nose).minOf { p -> mat.minOf { distTo(it, p) } }
    }

    private fun samples(s: Seg): List<Pt> = when (s) {
        is Line -> listOf(s.start, s.end)
        is Arc -> {
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            val rr = (s.start - s.center).length
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * rr / 0.02).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * rr }
        }
    }

    private fun same(a: Seg, b: Seg): Boolean =
        a::class == b::class && (a.start - b.start).length <= 1e-6 && (a.end - b.end).length <= 1e-6 &&
            (a !is Arc || (a.center - (b as Arc).center).length <= 1e-6)

    private fun xz(p: Pt) = "X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)}"

    /** 사례 하나를 형식 둘 × 팁 1~9 로 돌려 ①~④ 를 본다 */
    private fun check(f: Frame, t: Tally) {
        t.cases++
        // 노즈에 비해 좁은 구멍(가장 좁은 반지름 < G40 자리 규칙 반지름 (1 + √2)·r + 0.02)은 G40 줄의 옮김이 팁에 따라 맞은편 벽에 닿을 수 있어 따로 센다
        // (계산기는 축 위에 G40 을 둔다 — 알려진 한계). 화면 역방향의 내경에서 그림 5 mm 단면이 축까지 닿아 구멍을 막은 그림도 같이 뺀다
        if (f.inner) {
            val pts = f.drawing.flatMap { listOf(it.start, it.end) }
            val minR = pts.filter { it.x > 1e-6 }.minOfOrNull { it.x } ?: 0.0
            if (minR < (1 + kotlin.math.sqrt(2.0)) * f.r + 0.02 || (f.mirrored && pts.any { it.x <= 1e-9 })) {
                t.narrow++
                return
            }
        }
        val iCut = f.nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = f.nc.indexOfFirst { it.contains("F.3") }
        if (iCut < 0 || iExit < 0 || f.nc.first() !in listOf("G41", "G42") || !f.nc.contains("G40")) {
            t.fail("${f.tag}: NOT_FRAME 회사 꼴이 아님\n${f.nc.joinToString("\n")}")
            return
        }
        val cutLines = (pre.size + iCut + 1)..(pre.size + iExit)
        val shapeLines = (pre.size + iCut + 2)..(pre.size + iExit)
        val mat = if (f.inner) f.drawing + f.drawing.map(::mirror) else f.drawing
        var ref: List<Seg>? = null
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 1..9) {
            t.runs++
            val run = run(f, tip, form)
            val stop = run.stop
            val tag = "${f.tag} · 형식 $form · 팁 $tip"
            if (stop != null) {
                // 도면이 제어기 알람 꼴(오목 R ≤ r 등)이면 그 형상 줄과 바로 뒤 첫 빼는 줄의 멈춤은 그 탓
                if ((stop.line in shapeLines || stop.line == pre.size + iExit + 1) && f.shapeAlarm) {
                    t.skipped++
                    return
                }
                if (stop.line in shapeLines && stop.code == IssueCode.COMP_ACUTE_OUTER) {
                    t.simLimit++
                    return
                }
                t.fail("$tag: STOP ${stop.code} ${stop.line - pre.size}줄 '${f.nc.getOrNull(stop.line - pre.size - 1)}'\n${f.nc.joinToString("\n")}")
                return
            }
            val moves = run.steps.filterIsInstance<MoveStep>()
            val cut = moves.filter { it.line in cutLines && it.kind == MoveKind.FEED }.map { it.nose }
            // ② 깎는 줄 노즈가 형식 · 팁과 상관없다
            val r0 = ref
            if (r0 == null) {
                ref = cut
            } else if (r0.size != cut.size || r0.indices.any { !same(r0[it], cut[it]) }) {
                val k = r0.indices.firstOrNull { it >= cut.size || !same(r0[it], cut[it]) } ?: cut.size
                t.fail("$tag: DIFF 깎는 줄 노즈가 형식 AB 팁 1 과 다름(걸음 $k: ${r0.getOrNull(k)} / ${cut.getOrNull(k)})")
                return
            }
            // ③ 끝 · 시작 — 공구 교환 자리(보정 혼자 줄 · 첫 G0X…Z20.)의 이동은 부품에서 멀어 빼고 본다
            val firstG0 = pre.size + 2
            val all = moves.filter { it.line > firstG0 }.flatMap { samples(it.nose) }
            when (f.end) {
                End.BLOCKED -> {
                    val past = all.maxOf { (it - f.endP) dot f.endU }
                    if (past > -f.r + tol) t.fail("$tag: BLOCKED_PAST 막힌 끝 너머로 노즈가 감(${past + f.r})")
                    val stopAt = (cut.last().end - f.endP) dot f.endU
                    if (stopAt < -f.r - tol) t.fail("$tag: BLOCKED_GAP 막힌 끝 벽에 안 닿음(${stopAt + f.r})")
                }
                End.SHORT_BLOCKED -> {
                    val past = all.maxOf { (it - f.endP) dot f.endU }
                    if (past > -f.r + tol) t.fail("$tag: BLOCKED_PAST 짧은 막힌 끝 너머로 노즈가 감(${past + f.r})")
                }
                End.FREE -> {
                    val exitNoses = moves.filter { it.line == pre.size + iExit + 1 }.flatMap { samples(it.nose) }
                    val beyond = exitNoses.maxOfOrNull { (it - f.endP) dot f.endU } ?: Double.NEGATIVE_INFINITY
                    if (beyond < -1e-6) t.fail("$tag: FREE_SHORT 빈 끝에서 노즈 접점이 끝좌표에 못 감($beyond)")
                }
                End.AXIS -> {
                    val low = moves.filter { it.line in cutLines || it.line == pre.size + iExit + 1 }.flatMap { samples(it.nose) }.minOf { abs(it.x) }
                    if (low > tol) t.fail("$tag: AXIS_SHORT 축에서 끝나는데 노즈 중심이 축에 안 닿음($low)")
                }
                End.OTHER -> {}
            }
            f.startWall?.let { (s, back) ->
                val past = all.maxOf { (it - s) dot back }
                if (past > -f.r + tol) t.fail("$tag: START_PAST 막힌 시작 너머로 노즈가 감(${past + f.r})")
            }
            // ④ 깎는 줄 밖 이동이 도면을 파지 않는다(깎는 줄도 같이 — 보정이 맞으면 r)
            for (m in moves) {
                val g = gap(m.nose, mat)
                if (g < f.r - tol) {
                    val where = if (m.line in cutLines) "깎는 줄" else "공기 이동"
                    t.fail("$tag: GOUGE_${if (m.line in cutLines) "CUT" else "AIR"} $where ${m.line - pre.size}줄 '${f.nc.getOrNull(m.line - pre.size - 1) ?: m.src}' (${m.src}) 이 도면을 팜(${f.r - g}) ${xz(m.nose.start)} → ${xz(m.nose.end)}\n${f.nc.joinToString("\n")}")
                    return
                }
            }
        }
    }

    // ================================================================ 테이퍼

    private fun r3(v: Double) = Fmt.nc(v).toDouble()

    private fun taperFrame(tag: String, res: TaperResult, inner: Boolean, mirror: Boolean, r: Double): Frame? {
        val s = res.solution ?: return null
        val d = s.drawing.segs
        val first = d.first()
        val last = d.last()
        val uF = (first.end - first.start).let { it * (1 / it.length) }
        val uB = (last.end - last.start).let { it * (1 / it.length) }
        var end = End.OTHER
        if (s.toEnd) {
            val face = abs(uB.x) > abs(uB.z)
            val inward = face && (uB.x < 0) != inner
            val toAxis = inward && !inner && last.end.x <= 1e-9
            // 끝 요소가 노즈R 쯤보다 짧아 직각으로 빼면 거꾸로 가는 막힌 끝은 계산기가 그 요소를 빼고 앞 요소 끝에서 벽을 따라 뺀다 — 벽을 넘지 않음만 본다
            val nB = if (s.side == com.bmt.nosecalc.calc.Side.G41) Pt(-uB.x, uB.z) else Pt(uB.x, -uB.z)
            val shortEnd = (((last.end + nB * r - uB * r) - s.center.segs.last().start) dot uB) < 0.05
            val wallEnd = if (face) inward && !toAxis else if (mirror) uB.z > 0 else uB.z < 0
            end = when {
                toAxis -> End.AXIS
                wallEnd && !shortEnd -> End.BLOCKED
                wallEnd && !reaches(s.ncG41G42, last.end) -> End.SHORT_BLOCKED
                wallEnd -> End.FREE
                else -> End.FREE
            }
        }
        var wall: Pair<Pt, Pt>? = null
        if (s.fromStart) {
            val horizontal = abs(uF.z) > abs(uF.x)
            val nF = if (s.side == com.bmt.nosecalc.calc.Side.G41) Pt(-uF.x, uF.z) else Pt(uF.x, -uF.z)
            val shortStart = ((s.center.segs[0].end - (first.start + nF * r + uF * r)) dot uF) < 0.05
            val blocked = if (horizontal) (if (mirror) uF.z < 0 else uF.z > 0) else (uF.x > 0) != inner && !(!inner && first.start.x <= 1e-9)
            // 짧은 막힌 시작도 계산기가 그 요소를 빼고 다음 요소 처음으로 직각 진입하면(첫 깎는 줄 끝이 시작 좌표가 아님) 시작 좌표 너머로 가지 않음을 본다.
            // 다음 요소에 자리가 없어 전처럼 한 직선으로 들어오면(알려진 한계) 보지 않는다
            val skipped = (cutEnd(s.ncG41G42) - first.start).length > 1e-3
            if (blocked && (!shortStart || skipped)) wall = first.start to uF * -1.0
        }
        // 제어기 알람이 날 도면: 오목 R < r(앱 경고), 깎는 요소가 보정에서 빠짐(노즈보다 짧음)
        val from = if (s.fromStart) 0 else 1
        val to = if (s.toEnd) d.size else d.size - 1
        val gone = (from until to).any { k -> lengthOf(s.center.segs[k]) < 1e-6 && lengthOf(d[k]) > 1e-6 }
        val alarm = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings || gone
        if (!mirror) return Frame(tag, s.ncG41G42, r, d, inner, end, last.end, uB, wall, alarm)
        // 화면 역방향(거울 풀이)은 화면이 하듯 Z 를 되돌려 실제 쪽에서 돌린다 — 공구 교환 자리(Z+)에서 부품 앞으로
        fun fz(p: Pt) = Pt(-p.z, p.x)
        val swap = mapOf("G2" to "G3", "G3" to "G2", "G41" to "G42", "G42" to "G41")
        val nc = s.ncG41G42.map { line ->
            ncWords(line).joinToString("") { w ->
                when {
                    w in swap -> swap.getValue(w)
                    !w.startsWith("Z") -> w
                    w.startsWith("Z-") -> "Z" + w.substring(2)
                    w.drop(1).all { it == '0' || it == '.' } -> w
                    else -> "Z-" + w.substring(1)
                }
            }
        }
        val dm = d.map { if (it is Arc) Arc(fz(it.start), fz(it.end), fz(it.center), !it.cw) else Line(fz(it.start), fz(it.end)) }
        return Frame(tag, nc, r, dm, inner, end, fz(last.end), fz(uB), wall?.let { fz(it.first) to fz(it.second) }, alarm, mirrored = true)
    }

    /** 글의 X · Z 를 모달로 읽어 줄마다 끝 명령점(반경) */
    private fun ncPoints(nc: List<String>): List<Pt> {
        var z = 0.0
        var x = 0.0
        return nc.map { line ->
            for (w in ncWords(line)) when (w[0]) {
                'X' -> w.drop(1).toDoubleOrNull()?.let { x = it / 2 }
                'Z' -> w.drop(1).toDoubleOrNull()?.let { z = it }
            }
            Pt(z, x)
        }
    }

    /** 첫 빼는 줄 앞(마지막 형상 줄)의 끝이 [p] 인가 — 짧은 막힌 끝 요소를 뺐는지 가린다 */
    private fun reaches(nc: List<String>, p: Pt): Boolean {
        val i = nc.indexOfFirst { it.endsWith("F.3") }
        return i > 0 && (ncPoints(nc)[i - 1] - p).length <= 1e-3
    }

    /** 첫 깎는 줄(F.1)의 끝 */
    private fun cutEnd(nc: List<String>): Pt = ncPoints(nc)[nc.indexOfFirst { it.endsWith("F.1") }]

    private fun lengthOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> (s.start - s.center).length * s.sweepDeg * PI / 180
    }

    /** 씨앗 고정 무작위 테이퍼(VerifyG4xSimTest 의 입력과 같은 꼴 — 화면이 넣는 그대로 0.001 반올림) */
    private fun taperCases(seed: Int, count: Int): List<Frame> {
        val rnd = Random(seed)
        val out = mutableListOf<Frame>()
        fun <T> pick(l: List<T>): T = l[rnd.nextInt(l.size)]
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
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
                val frontFace = type <= 2
                val backFace = type == 2 || type == 4
                val start = if (rnd.nextBoolean()) null else if (frontFace) {
                    if (!inner && rnd.nextInt(5) == 0) 0.0 else max(0.0, r3(x1 - up * 2 * legLen()))
                } else r3(z1 + legLen())
                val end = if (rnd.nextBoolean()) null else if (backFace) {
                    if (inner && rnd.nextInt(5) == 0) 0.0 else max(0.0, r3(x2 + up * 2 * legLen()))
                } else r3(z2 - legLen())
                var inp = TaperInput(inner, type, reverse, r, tip, x1, z1, x2, z2, null, zc, cr(), cr(), start = start, end = end, approach = !mirror)
                if (flip) inp = inp.copy(x1 = inp.x2, z1 = inp.z2, x2 = inp.x1, z2 = inp.z1, cornerR1 = inp.cornerR2, cornerR2 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
                taperFrame(inp.toString(), TaperCalc.calc(inp), inner, mirror, r)?.let { out += it }
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
                if (flip) inp = inp.copy(x1 = inp.x3, z1 = inp.z3, x3 = inp.x1, z3 = inp.z1, cornerR1 = inp.cornerR3, cornerR3 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
                taperFrame(inp.toString(), TaperCalc.calcDouble(inp), inner, mirror, r)?.let { out += it }
            }
        }
        return out
    }

    /**
     * 씨앗 고정 무작위 T1–T2 원호 테이퍼(피드백 2026-11-10 — `TaperInput.arc`): [taperCases] 의 홑테이퍼와 같은 꼴(바깥 · 안 · 뒤집기 · 계산기 역방향 · 거울 ·
     * 모서리 R · 시작 · 끝)에 호를 켠 것. R 은 가장 작은 R(0.001 올림 — 앞뒤 요소에 거의 접함) · 그 1~1.5 배 · 1.5~4 배 · 4~40 배, 볼록 · 오목
     */
    private fun taperArcCases(seed: Int, count: Int): List<Frame> {
        val rnd = Random(seed)
        val out = mutableListOf<Frame>()
        fun <T> pick(l: List<T>): T = l[rnd.nextInt(l.size)]
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(count) {
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
            val type = 1 + rnd.nextInt(5)
            val up = if (inner == (type == 5)) 1.0 else -1.0
            val deg = u(5.0, 85.0)
            val dz = r3(u(0.3, 12.0))
            val x2 = r3(x1 + up * 2 * dz * tan(deg * PI / 180))
            if (x2 < 1.0 || x2 > 400.0) return@repeat
            val z2 = r3(z1 - dz)
            val minR = TaperCalc.arcMinR(x1, z1, x2, z2) ?: return@repeat
            val k = when (rnd.nextInt(4)) {
                0 -> 1.0
                1 -> u(1.0, 1.5)
                2 -> u(1.5, 4.0)
                else -> u(4.0, 40.0)
            }
            val arcR = kotlin.math.ceil(minR * k * 1000 - 1e-6) / 1000
            val convex = rnd.nextBoolean()
            val frontFace = type <= 2
            val backFace = type == 2 || type == 4
            val start = if (rnd.nextBoolean()) null else if (frontFace) {
                if (!inner && rnd.nextInt(5) == 0) 0.0 else max(0.0, r3(x1 - up * 2 * legLen()))
            } else r3(z1 + legLen())
            val end = if (rnd.nextBoolean()) null else if (backFace) {
                if (inner && rnd.nextInt(5) == 0) 0.0 else max(0.0, r3(x2 + up * 2 * legLen()))
            } else r3(z2 - legLen())
            var inp = TaperInput(
                inner, type, reverse, r, tip, x1, z1, x2, z2, null, zc, cr(), cr(), start = start, end = end, approach = !mirror,
                arc = true, arcR = arcR, arcConvex = convex,
            )
            if (flip) inp = inp.copy(x1 = inp.x2, z1 = inp.z2, x2 = inp.x1, z2 = inp.z1, cornerR1 = inp.cornerR2, cornerR2 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
            taperFrame(inp.toString(), TaperCalc.calc(inp), inner, mirror, r)?.let { out += it }
        }
        return out
    }

    // ================================================================ 라운드

    private fun roundFrame(tag: String, machine: com.bmt.nosecalc.calc.RoundResult, direct: com.bmt.nosecalc.calc.RoundResult, inner: Boolean, r: Double): Frame? {
        val s = machine.solution ?: return null
        val dsol = direct.solution ?: return null
        val d = s.drawing.segs
        val last = d.last()
        val uB = when (last) {
            is Line -> (last.end - last.start).let { it * (1 / it.length) }
            is Arc -> (last.end - last.center).let { Pt(-it.x, it.z) * ((if (last.cw) -1.0 else 1.0) / it.length) }
        }
        // 끝 직선이 노즈R 쯤보다 짧아 직각으로 빼면 거꾸로 가는 막힌 끝은 계산기가 그 요소를 빼고 벽을 따라 뺀다 — 벽을 넘지 않음만 본다
        val nB = if (s.side == com.bmt.nosecalc.calc.Side.G41) Pt(-uB.x, uB.z) else Pt(uB.x, -uB.z)
        val shortEnd = (((last.end + nB * r - uB * r) - dsol.center.segs.last().start) dot uB) < 0.05
        val end = when {
            last is Line && abs(uB.z) > abs(uB.x) && uB.z < 0 && !shortEnd -> End.BLOCKED
            last is Line && abs(uB.z) > abs(uB.x) && uB.z < 0 && !reaches(s.nc, last.end) -> End.SHORT_BLOCKED
            last is Line && abs(uB.z) > abs(uB.x) && uB.z < 0 -> End.OTHER
            last.end.x <= 1e-9 -> End.AXIS
            else -> End.FREE
        }
        val gone = dsol.center.segs.indices.any { k -> lengthOf(dsol.center.segs[k]) < 1e-6 && lengthOf(d[k]) > 1e-6 }
        val alarm = RoundWarning.CONCAVE_R_LE_NOSE in direct.warnings || gone
        return Frame(tag, s.nc, r, d, inner, end, last.end, uB, null, alarm)
    }

    private fun roundCases(seed: Int, count: Int): List<Frame> {
        val rnd = Random(seed)
        val g = GeometricVerifyTest()
        val out = mutableListOf<Frame>()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val inp = g.randomRound(type, rnd, inner).input
            roundFrame(inp.toString(), RoundCalc.machine(inp), RoundCalc.calc(inp), inner, inp.noseR)?.let { out += it }
        }
        return out
    }

    /**
     * 뒤집은 라운드(가공 방향 뒤집기 — 2026-11-10 피드백 2)와 같은 입력의 기본 방향. 척 · 구멍 바닥 쪽 끝 직선에서 Z+ 로 시작하면 막힌 시작 —
     * 노즈가 시작 좌표의 법선(벽)을 넘지 않는지 본다
     */
    private fun flippedRoundCases(seed: Int, count: Int): List<Pair<Frame, Frame>> {
        val rnd = Random(seed)
        val g = GeometricVerifyTest()
        val out = mutableListOf<Pair<Frame, Frame>>()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val base = g.randomRound(type, rnd, inner).input
            val inp = base.copy(flip = true)
            val f = roundFrame(inp.toString(), RoundCalc.machine(inp), RoundCalc.calc(inp), inner, inp.noseR) ?: return@repeat
            val b = roundFrame(base.toString(), RoundCalc.machine(base), RoundCalc.calc(base), inner, base.noseR) ?: return@repeat
            val first = f.drawing.first()
            val u = (first.end - first.start).let { it * (1 / it.length) }
            val wall = if (first is Line && abs(u.z) > abs(u.x) && u.z > 0) first.start to u * -1.0 else null
            out += Frame(f.tag, f.nc, f.r, f.drawing, f.inner, f.end, f.endP, f.endU, wall, f.shapeAlarm) to b
        }
        return out
    }

    /** 깎는 줄(첫 깎는 줄 ~ 첫 빼는 줄)의 노즈 길 — 형식 C · 팁 3(② 가 형식 · 팁과 상관없음을 본다). 멈추면 null */
    private fun cutNoses(f: Frame): List<Seg>? {
        val run = run(f, 3, CompStartup.C)
        if (run.stop != null) return null
        val iCut = f.nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = f.nc.indexOfFirst { it.contains("F.3") }
        val lines = (pre.size + iCut + 1)..(pre.size + iExit + 1)
        return run.steps.filterIsInstance<MoveStep>().filter { it.line in lines && it.kind == MoveKind.FEED }.map { it.nose }
    }

    /** 요소 위 점(0.05 mm 마다) */
    private fun dense(s: Seg): List<Pt> = when (s) {
        is Line -> max(2, ((s.end - s.start).length / 0.05).toInt()).let { n -> (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) } }
        is Arc -> samples(s)
    }

    /** 3점호: 원 위 세 점(볼록 · 오목, 바깥 · 안, 정 · 역방향) */
    private fun arc3Cases(seed: Int, count: Int): List<Frame> {
        val rnd = Random(seed)
        val out = mutableListOf<Frame>()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(count) {
            val inner = rnd.nextBoolean()
            val rr = u(2.0, 30.0)
            val c = Pt(u(-20.0, 0.0), u(if (inner) rr + 4 else -rr * 0.5, if (inner) rr + 30 else rr + 20))
            val a0 = u(0.0, 2 * PI)
            val sw = u(0.3, 2.6) * (if (rnd.nextBoolean()) 1 else -1)
            val pts = listOf(0.0, 0.5, 1.0).map { c + Pt(cos(a0 + sw * it), sin(a0 + sw * it)) * rr }
            if (pts.any { it.x < 0.5 } || abs(pts[2].z - pts[0].z) < 0.5) return@repeat
            // 정방향(척 쪽으로)이고 원호가 시작점보다 앞(+Z)으로 나오지 않는 것만 — 도면 윤곽 한 조각인 CAD 원호의 보통 꼴.
            // 역방향 · 앞으로 부푼 원호는 공구가 그 원호 너머(척 쪽)에서 되돌아 들어가야 하는 꼴이라 이 틀 시험에서 뺀다(알려진 한계)
            val sampled = (0..20).map { c + Pt(cos(a0 + sw * it / 20), sin(a0 + sw * it / 20)) * rr }
            if (pts[2].z > pts[0].z || sampled.any { it.z > pts[0].z + 1e-9 }) return@repeat
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val inp = Arc3Input(
                inner, r, null, r3(pts[0].xDia), r3(pts[0].z), r3(pts[1].xDia), r3(pts[1].z), r3(pts[2].xDia), r3(pts[2].z), listOf(0.0, 0.5, 1.0, 2.0)[rnd.nextInt(4)],
            )
            roundFrame(inp.toString(), RoundCalc.machine(inp), RoundCalc.arc3(inp), inner, r)?.let { out += it }
        }
        return out
    }

    // ================================================================ 시험

    @Test
    fun randomTapersFollowCompanyFrame() {
        val t = Tally("테이퍼 G41/G42")
        for (f in taperCases(20261104, 1500)) check(f, t)
        println(t.report())
        t.fails.take(5).forEach(::println)
        assertTrue(t.cases > 1000, t.report())
        assertEquals(0, t.fails.size, t.report() + "\n" + t.fails.take(5).joinToString("\n\n"))
    }

    /** T1–T2 를 원호로 낸 테이퍼(피드백 2026-11-10)도 같은 기준 ①~④ 로 */
    @Test
    fun randomArcTapersFollowCompanyFrame() {
        val t = Tally("테이퍼 T1–T2 원호 G41/G42")
        for (f in taperArcCases(20261110, 2500)) check(f, t)
        println(t.report())
        t.fails.take(5).forEach(::println)
        assertTrue(t.cases > 1200, t.report())
        assertEquals(0, t.fails.size, t.report() + "\n" + t.fails.take(5).joinToString("\n\n"))
    }

    @Test
    fun randomRoundsFollowCompanyFrame() {
        val t = Tally("라운드 기계 보정")
        for (f in roundCases(20261105, 900) + arc3Cases(20261106, 1500)) check(f, t)
        println(t.report())
        t.fails.take(5).forEach(::println)
        assertTrue(t.cases > 900, t.report())
        assertEquals(0, t.fails.size, t.report() + "\n" + t.fails.take(5).joinToString("\n\n"))
    }

    /**
     * 뒤집은 라운드 TYPE 1~6(바깥 · 안 · 관통)의 기계 보정 글: ①~④ 에 더해 ⑤ 깎는 줄이 같은 입력의 기본 방향 글만큼 도면에 닿는다(덜 깎임 0 —
     * 도면 위 점마다 노즈 길까지의 거리가 기본 방향보다 멀지 않다. 두 방향 모두 척 쪽 벽 앞에서 노즈가 벽의 법선에 서므로 구석도 같다)
     */
    @Test
    fun randomFlippedRoundsFollowCompanyFrame() {
        val t = Tally("라운드 뒤집기 기계 보정")
        var compared = 0
        var worst = 0.0
        var walls = 0
        for ((f, base) in flippedRoundCases(20261110, 700)) {
            val before = t.kinds.values.sum()
            val narrow = t.narrow
            check(f, t)
            if (f.startWall != null) walls++
            if (t.kinds.values.sum() != before || t.narrow != narrow || f.shapeAlarm) continue
            val a = cutNoses(f) ?: continue
            val b = cutNoses(base) ?: continue
            var lack = 0.0
            var at = f.drawing.first().start
            for (s in f.drawing) for (p in dense(s)) {
                val d = a.minOf { distTo(it, p) } - b.minOf { distTo(it, p) }
                if (d > lack) {
                    lack = d
                    at = p
                }
            }
            compared++
            worst = max(worst, lack)
            if (lack > tol) t.fail("${f.tag}: UNDERCUT ${xz(at)} 에서 기본 방향보다 덜 깎음($lack)\n${f.nc.joinToString("\n")}\n-- 기본 방향\n${base.nc.joinToString("\n")}")
        }
        println(t.report() + " · 막힌 시작 $walls · 덜 깎임 견줌 $compared 건(가장 큰 차 $worst)")
        t.fails.take(5).forEach(::println)
        assertTrue(t.cases > 450 && walls > 200 && compared > 250, t.report() + " walls $walls compared $compared")
        assertEquals(0, t.fails.size, t.report() + "\n" + t.fails.take(5).joinToString("\n\n"))
    }
}
