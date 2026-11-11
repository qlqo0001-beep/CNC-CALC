package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
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
 * 독립 검증(2026-11-10 피드백 2 — 라운드 가공 방향 바꾸기). 구현의 틀 코드(CompFrame · NoseComp 의 길 만들기)를 쓰지 않고 여기서 따로 만든 기하로 본다:
 * - 도면 = 같은 입력의 기본 방향 풀이의 도면(전부터 있던 코드). 뒤집은 풀이의 도면이 같은 점 집합인지 먼저 본다
 * - 직접 보정 NC 는 글을 직접 읽어(모달 G · X · Z · R) 인선 길을 만들고 팁 벡터(여기 표)로 노즈 중심 길을 낸다
 * - 기계 보정 NC 는 시뮬레이터(GSim)로 돌려 노즈 중심 길을 받는다(형식 C · AB)
 * - 소재 = 도면 아래(외경) · 도면 밖(내경)을 채운 것. 급송 · 이송의 노즈 원이 소재에 얼마나 들어가나 / 도면 위 점마다 이송 노즈가 닿았나
 * 결과 글은 calc/build/verify1110/ 에 쓴다
 */
class Verify1110RoundFlipTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** NC 0.001 반올림(좌표 · R)까지 받는 허용 */
    private val tol = 2e-3

    private val outDir = File("build/verify1110").also { it.mkdirs() }

    // ------------------------------------------------------------ 기하(여기서 따로)

    /** 노즈 중심 → 가상 인선(FANUC 팁 번호: 2 6 1 / 7 0 5 / 3 8 4, X 위 · Z 오른쪽) */
    private fun tv(tip: Int): Pt = when (tip) {
        1 -> Pt(1.0, 1.0)
        2 -> Pt(-1.0, 1.0)
        3 -> Pt(-1.0, -1.0)
        4 -> Pt(1.0, -1.0)
        5 -> Pt(1.0, 0.0)
        6 -> Pt(0.0, 1.0)
        7 -> Pt(-1.0, 0.0)
        8 -> Pt(0.0, -1.0)
        else -> Pt(0.0, 0.0)
    }

    private fun ang(v: Pt) = atan2(v.x, v.z)
    private fun w2(a: Double): Double {
        var m = a % (2 * PI)
        if (m < 0) m += 2 * PI
        return m
    }

    private fun sweep(a: Arc): Double {
        if ((a.end - a.start).length < 1e-12) return 0.0
        val d = ang(a.end - a.center) - ang(a.start - a.center)
        return w2(if (a.cw) -d else d)
    }

    private fun onArc(a: Arc, p: Pt): Boolean {
        val d = ang(p - a.center) - ang(a.start - a.center)
        return w2(if (a.cw) -d else d) <= sweep(a) + 1e-9
    }

    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 < 1e-24) (p - s.start).length else (p - (s.start + d * ((((p - s.start) dot d) / l2).coerceIn(0.0, 1.0)))).length
        }
        is Arc -> if (onArc(s, p)) abs((p - s.center).length - (s.start - s.center).length) else min((p - s.start).length, (p - s.end).length)
    }

    private fun dist(p: Pt, segs: List<Seg>): Double = segs.minOf { dist(p, it) }

    private fun sample(s: Seg, step: Double): List<Pt> = when (s) {
        is Line -> {
            val n = max(1, ceil((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val rr = (s.start - s.center).length
            val sw = sweep(s)
            val n = max(1, ceil(sw * rr / step).toInt())
            val a0 = ang(s.start - s.center)
            val dir = if (s.cw) -1.0 else 1.0
            (0..n).map { k -> (a0 + dir * sw * k / n).let { a -> s.center + Pt(cos(a), sin(a)) * rr } }
        }
    }

    private fun mirror(p: Pt) = Pt(p.z, -p.x)
    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(mirror(s.start), mirror(s.end))
        is Arc -> Arc(mirror(s.start), mirror(s.end), mirror(s.center), !s.cw)
    }

    private fun left(u: Pt) = Pt(-u.x, u.z)

    /** 요소 가운데 점과 그 자리의 진행 방향 */
    private fun midDir(s: Seg): Pair<Pt, Pt> = when (s) {
        is Line -> ((s.start + s.end) * 0.5) to (s.end - s.start).let { it * (1 / it.length) }
        is Arc -> {
            val rr = (s.start - s.center).length
            val dir = if (s.cw) -1.0 else 1.0
            val a = ang(s.start - s.center) + dir * sweep(s) / 2
            val rad = Pt(cos(a), sin(a))
            (s.center + rad * rr) to (left(rad) * dir)
        }
    }

    private fun length(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> sweep(s) * (s.start - s.center).length
    }

    /** 소재: 기본 방향 도면(외경 = 앞 끝 → 척 쪽, 내경 = 입구 → 바닥)으로 만든다 */
    private inner class Solid(val segs: List<Seg>, val inner: Boolean) {
        val zFront = segs.maxOf { max(it.start.z, it.end.z) }
        val zBack = segs.minOf { min(it.start.z, it.end.z) }
        private val last = segs.last()

        /** 척 · 구멍 바닥 쪽이 누운 끝 직선으로 끝나나(그 너머는 벽으로 본다) */
        val legEnd = last is Line && abs(last.end.x - last.start.x) < 1e-9 && last.end.z < last.start.z - 1e-9 && last.end.x > 1e-9
        val backX = last.end.x
        val frontX = segs.first().start.x
        val wallZ: Double? = if (legEnd) zBack else null
        val boundary: List<Seg>

        init {
            val b = segs.toMutableList()
            if (legEnd) b += Line(last.end, Pt(last.end.z - 300.0, last.end.x))
            if (inner) b += Line(Pt(zFront, frontX), Pt(zFront, frontX + 500.0))
            boundary = b + b.map { mirror(it) }
        }

        /** Z = z 에서 도면의 X 들 */
        fun profile(z: Double): List<Double> {
            val out = mutableListOf<Double>()
            for (s in segs) when (s) {
                is Line -> {
                    val lo = min(s.start.z, s.end.z)
                    val hi = max(s.start.z, s.end.z)
                    if (z < lo - 1e-12 || z > hi + 1e-12) continue
                    if (hi - lo < 1e-12) {
                        out += s.start.x
                        out += s.end.x
                    } else out += s.start.x + (s.end.x - s.start.x) * ((z - s.start.z) / (s.end.z - s.start.z))
                }
                is Arc -> {
                    val rr = (s.start - s.center).length
                    val dz = z - s.center.z
                    if (abs(dz) > rr) continue
                    val h = sqrt(max(0.0, rr * rr - dz * dz))
                    for (x in listOf(s.center.x + h, s.center.x - h)) if (onArc(s, Pt(z, x))) out += x
                }
            }
            return out
        }

        fun inMaterial(q: Pt): Boolean {
            val ax = abs(q.x)
            if (q.z > zFront) return false
            return if (!inner) {
                if (q.z < zBack) legEnd && ax < backX else profile(q.z).maxOrNull()?.let { ax < it } ?: false
            } else {
                if (q.z < zBack) (if (legEnd) ax > backX else true) else profile(q.z).minOrNull()?.let { ax > it } ?: true
            }
        }

        /** 점에서 소재까지(소재 안이면 음수) */
        fun clearance(q: Pt): Double = dist(q, boundary).let { if (inMaterial(q)) -it else it }
    }

    // ------------------------------------------------------------ NC 읽기(직접 보정 글)

    private class Mv(val idx: Int, val text: String, val rapid: Boolean, val seg: Seg, val axes: Int)

    private val wordRe = Regex("([A-Z])(-?[0-9]*\\.?[0-9]*)")

    private fun arcOf(s: Pt, e: Pt, r: Double, cw: Boolean): Arc {
        val d = e - s
        val l = d.length
        val h = sqrt(max(0.0, r * r - l * l / 4))
        val n = left(d) * (1 / l)
        val m = (s + e) * 0.5
        return Arc(s, e, if (cw) m - n * h else m + n * h, cw)
    }

    /** 글 → 명령점 길. 첫 위치 줄은 길이 0 인 급송(끝 점만). 읽지 못한 것은 [errors] 에 */
    private fun parse(nc: List<String>, errors: MutableList<String>): List<Mv> {
        var g = -1
        var z = Double.NaN
        var x = Double.NaN
        val out = mutableListOf<Mv>()
        for ((idx, raw) in nc.withIndex()) {
            val line = raw.trim()
            if (line.startsWith("(") || line.isEmpty()) continue
            var nz = z
            var nx = x
            var rr: Double? = null
            var axes = 0
            var consumed = 0
            for (m in wordRe.findAll(line)) {
                consumed += m.value.length
                val v = m.groupValues[2]
                when (m.groupValues[1]) {
                    "G" -> when (val k = v.toIntOrNull()) {
                        0, 1, 2, 3 -> g = k
                        40, 41, 42 -> {}
                        else -> errors += "모르는 G: $line"
                    }
                    "X" -> {
                        nx = v.toDouble() / 2
                        axes++
                    }
                    "Z" -> {
                        nz = v.toDouble()
                        axes++
                    }
                    "R" -> rr = v.toDouble()
                    "F" -> {}
                    else -> errors += "모르는 주소: $line"
                }
            }
            if (consumed != line.length) errors += "읽지 못한 글자: $line"
            if (axes == 0) continue
            if (nz.isNaN() || nx.isNaN()) {
                errors += "첫 이동에 X · Z 가 다 없음: $line"
                continue
            }
            val to = Pt(nz, nx)
            if (z.isNaN() || x.isNaN()) {
                out += Mv(idx, line, true, Line(to, to), axes)
            } else {
                val from = Pt(z, x)
                when (g) {
                    0, 1 -> out += Mv(idx, line, g == 0, Line(from, to), axes)
                    2, 3 -> {
                        if (rr == null) errors += "원호에 R 없음: $line" else out += Mv(idx, line, false, arcOf(from, to, rr, g == 2), axes)
                    }
                    else -> errors += "G 없이 이동: $line"
                }
            }
            z = nz
            x = nx
        }
        return out
    }

    private fun shift(s: Seg, d: Pt): Seg = when (s) {
        is Line -> Line(s.start + d, s.end + d)
        is Arc -> Arc(s.start + d, s.end + d, s.center + d, s.cw)
    }

    // ------------------------------------------------------------ 집계

    private class Tally(val name: String) {
        val counts = sortedMapOf<String, Int>()
        val firsts = linkedMapOf<String, MutableList<String>>()
        val worst = sortedMapOf<String, Double>()
        val worstAt = hashMapOf<String, String>()
        fun hit(kind: String, detail: () -> String) {
            counts[kind] = (counts[kind] ?: 0) + 1
            val l = firsts.getOrPut(kind) { mutableListOf() }
            if (l.size < 4) l += detail()
        }

        fun count(kind: String) {
            counts[kind] = (counts[kind] ?: 0) + 1
        }

        fun maxOf(kind: String, v: Double, at: () -> String) {
            if (v > (worst[kind] ?: Double.NEGATIVE_INFINITY)) {
                worst[kind] = v
                worstAt[kind] = at()
            }
        }

        fun minOf(kind: String, v: Double, at: () -> String) = maxOf(kind, -v, at)

        fun report(): String = buildString {
            appendLine("==== $name")
            appendLine("수: $counts")
            for ((k, v) in worst) appendLine("가장 큰 값 $k = $v  @ ${worstAt[k]}")
            for ((k, l) in firsts) {
                appendLine("-- $k (${counts[k]}건) 첫 예")
                l.forEach { appendLine(it.prependIndent("   ")) }
            }
        }
    }

    /** 실패로 세는 갈래(이름이 F_ 로 시작) */
    private fun fails(t: Tally) = t.counts.filterKeys { it.startsWith("F_") }

    // ------------------------------------------------------------ 한 사례 보기

    private fun sameSet(a: List<Seg>, b: List<Seg>): Double {
        var d = 0.0
        for (s in a) for (p in sample(s, 0.25)) d = max(d, dist(p, b))
        for (s in b) for (p in sample(s, 0.25)) d = max(d, dist(p, a))
        return d
    }

    private fun tag(i: RoundInput) =
        "T${i.type}${if (i.inner) " 내경" else " 외경"}${if (i.through) " 관통" else ""} R${i.mainR} cr1 ${i.cornerR1} cr2 ${i.cornerR2} d1 ${i.d1} d2 ${i.d2} l1 ${i.l1} l2 ${i.l2} " +
            "노즈 ${i.noseR} 팁 ${i.tip} Z여유 ${i.zClear} 끝직선 ${i.legLen}"

    /** 도면 위 점마다 이송 노즈가 닿았나(가장 가까운 노즈 중심 길 − r). 0.05 mm 마다 */
    private fun lacks(drawing: List<Seg>, feedNose: List<Seg>, r: Double): List<Pair<Pt, Double>> =
        drawing.flatMap { s -> sample(s, 0.05) }.map { p -> p to (feedNose.minOf { dist(p, it) } - r) }

    private class Nose(val feed: List<Seg>, val rapid: List<Pair<Seg, String>>, val other: List<Seg>)

    /** 급송 · 이송 노즈가 소재에 들어간 깊이 / 벽 너머 / 급송 틈 */
    private fun checkNose(t: Tally, kind: String, what: String, solid: Solid, n: Nose, r: Double, nc: List<String>, warned: Boolean): Double? {
        val w = if (warned) "W_" else "F_"
        var feedPen = 0.0
        for (s in n.feed + n.other) for (q in sample(s, 0.02)) feedPen = max(feedPen, r - solid.clearance(q))
        t.maxOf("$kind 이송 파고듦", feedPen) { what }
        if (feedPen > tol) t.hit("${w}${kind}_이송이_소재를_팜") { "$what\n파고듦 $feedPen\n${nc.joinToString("\n")}" }
        var rapidClear = Double.MAX_VALUE
        var rapidAt = ""
        for ((s, text) in n.rapid) for (q in sample(s, 0.02)) {
            // 부품 앞 3 mm 넘게 떨어진 곳은 재지 않는다(빠르게 — G0Z200. 같은 긴 줄)
            val c = if (q.z - r > solid.zFront + 3) 3.0 else solid.clearance(q) - r
            if (c < rapidClear) {
                rapidClear = c
                rapidAt = "$text 의 노즈 중심 X${2 * q.x} Z${q.z}"
            }
        }
        if (n.rapid.isNotEmpty()) {
            t.minOf("$kind 급송 틈(가장 작은 — 음수면 파고듦)", rapidClear) { "$what · $rapidAt" }
            when {
                rapidClear < -tol -> t.hit("${w}${kind}_급송이_소재를_지남") { "$what\n틈 $rapidClear · $rapidAt\n${nc.joinToString("\n")}" }
                rapidClear < 0.05 -> t.hit("I_${kind}_급송_틈_0.05_미만") { "$what\n틈 $rapidClear · $rapidAt\n${nc.joinToString("\n")}" }
                rapidClear < 0.25 -> t.hit("I_${kind}_급송_틈_0.25_미만") { "$what\n틈 $rapidClear · $rapidAt\n${nc.joinToString("\n")}" }
                else -> t.count("I_${kind}_급송_틈_0.25_이상")
            }
        }
        solid.wallZ?.let { wz ->
            var past = Double.NEGATIVE_INFINITY
            for (s in n.feed + n.other + n.rapid.map { it.first }) for (q in sample(s, 0.05)) past = max(past, wz + r - q.z)
            t.maxOf("$kind 벽 너머(양수면 넘음)", past) { what }
            if (past > tol) t.hit("${w}${kind}_시작_끝_좌표_너머로_감") { "$what\n너머 $past\n${nc.joinToString("\n")}" }
        }
        return if (n.rapid.isEmpty()) null else rapidClear
    }

    /** 급송 틈 표: Z 여유 · 노즈R 마다 가장 작은 틈 */
    private fun gapTable(t: Tally, kind: String, i: RoundInput, clear: Double?, what: String) {
        if (clear == null) return
        val start = if (i.inner) (if (i.through || i.type == 2) "막힌 시작" else "축에서 시작") else (if (i.type == 1) "축에서 시작" else "막힌 시작")
        t.minOf("틈표 [$kind] Z여유 ${i.zClear} 노즈 ${i.noseR} ${if (kind.startsWith("뒤집기")) start else ""}", clear) { what }
    }

    /** 시뮬레이터로 기계 보정 글을 돌린 노즈 길(멈추면 null 과 멈춘 까닭) */
    private fun simNose(nc: List<String>, r: Double, tip: Int, form: CompStartup): Pair<Nose?, String?> {
        val run = GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, tip))))
        run.stop?.let { return null to "${it.code} ${it.line - pre.size}줄 '${nc.getOrNull(it.line - pre.size - 1)}'" }
        // 보정 혼자 줄 · 첫 G0X…Z20.(공구 교환 자리에서 오는 이동)은 부품에서 멀어 뺀다
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size + 2 }
        val feed = moves.filter { it.kind == MoveKind.FEED }.map { it.nose }
        val rapid = moves.filter { it.kind == MoveKind.RAPID }.map { it.nose to (nc.getOrNull(it.line - pre.size - 1) ?: "?") + "(${it.src})" }
        val other = moves.filter { it.kind != MoveKind.FEED && it.kind != MoveKind.RAPID }.map { it.nose }
        return Nose(feed, rapid, other) to null
    }

    /** 회사 흐름 꼴인가. 틀린 점을 돌려준다(없으면 null) */
    private fun frameError(nc: List<String>, z20: Boolean = true): String? {
        if (nc.size < 6) return "줄 수 ${nc.size}"
        if (nc[0] != "G41" && nc[0] != "G42") return "첫 줄이 보정 혼자 줄이 아님: ${nc[0]}"
        if (!Regex(if (z20) "G0X-?[0-9]*\\.[0-9]*Z20\\." else "G0X-?[0-9]*\\.[0-9]*Z-?[0-9]*\\.[0-9]*").matches(nc[1])) return "둘째 줄이 G0X…Z20. 이 아님: ${nc[1]}"
        val iCut = nc.indexOfFirst { it.endsWith("F.1") }
        val iExit = nc.indexOfFirst { it.endsWith("F.3") }
        if (iCut < 2 || !nc[iCut].startsWith("G1")) return "첫 깎는 줄(G1 … F.1) 없음"
        if (nc.count { it.endsWith("F.1") } != 1 || nc.count { it.endsWith("F.3") } != 1) return "F.1 · F.3 줄 수"
        if (iExit < iCut) return "빼는 줄(F.3) 없음"
        for (k in 2 until iCut) if (nc[k].contains("G")) return "진입 줄에 G: ${nc[k]}"
        // 진입 G0(첫 줄 말고)은 한 축씩 — 두 축 G0 은 급송 속도 비에 따라 꺾인다(LRP=0)
        if (z20) for (k in 2 until iCut) if (nc[k].contains("X") && nc[k].contains("Z")) return "두 축 진입 G0: ${nc[k]}"
        if (nc[nc.size - 2] != "G40") return "G40 혼자 줄이 끝에서 둘째가 아님: ${nc[nc.size - 2]}"
        if (nc.last() != "G0Z200.") return "마지막 줄이 G0Z200. 이 아님: ${nc.last()}"
        if (nc.count { it.contains("G40") || it.contains("G41") || it.contains("G42") } != 2) return "보정 G 수"
        // G40 앞 마지막 이동은 직선 이송(G1)이어야 한다 — 원호 모드에서 G40 금지
        var g = -1
        for (k in 0 until nc.size - 2) Regex("G([0-9]+)").findAll(nc[k]).forEach { m -> m.groupValues[1].toInt().let { if (it in 0..3) g = it } }
        if (g != 1) return "G40 앞 모드가 G$g"
        return null
    }

    /** 도면(가공 순서)의 진행 방향으로 볼 때 공구(빈 쪽)가 왼쪽이면 G41 */
    private fun expectedSide(travel: List<Seg>, solid: Solid): String? {
        val votes = mutableSetOf<String>()
        for (s in travel) {
            if (length(s) < 0.2) continue
            val (m, u) = midDir(s)
            val l = solid.inMaterial(m + left(u) * 1e-3)
            val rgt = solid.inMaterial(m - left(u) * 1e-3)
            if (l == rgt) continue
            votes += if (!l) "G41" else "G42"
        }
        return votes.singleOrNull()
    }

    private val stopWarnings = setOf(
        RoundWarning.MISSING_INPUT, RoundWarning.BAD_INPUT, RoundWarning.CONTRADICTION, RoundWarning.SHAPE_MISMATCH, RoundWarning.CORNER_R_TOO_BIG, RoundWarning.NOSE_UNREACHABLE,
    )

    private fun safe(t: Tally, what: String, f: () -> RoundResult): RoundResult? = try {
        f()
    } catch (e: Throwable) {
        t.hit("F_예외") { "$what\n${e.stackTraceToString().lines().take(8).joinToString("\n")}" }
        null
    }

    private fun checkCase(t: Tally, base: RoundInput, simTips: List<Int>) {
        val what = tag(base)
        val inp = base.copy(flip = true)
        val bd = safe(t, "기본 직접 $what") { RoundCalc.calc(base) } ?: return
        val bm = safe(t, "기본 기계 $what") { RoundCalc.machine(base) } ?: return
        val fd = safe(t, "뒤집기 직접 $what") { RoundCalc.calc(inp) } ?: return
        val fm = safe(t, "뒤집기 기계 $what") { RoundCalc.machine(inp) } ?: return
        val bs = bd.solution
        if (bs == null) {
            t.count("기본 방향이 안 풀림(${bd.warnings.firstOrNull()})")
            if (fd.solution != null) t.hit("F_기본은_안_풀리는데_뒤집기는_풀림") { what }
            return
        }
        t.count("사례")
        val r = base.noseR
        val solid = Solid(bs.drawing.segs, base.inner)
        val fs = fd.solution
        if (fs == null) {
            val w = fd.warnings.firstOrNull()
            t.hit("뒤집기만 안 풀림($w)") { what }
            if (w != RoundWarning.NOSE_UNREACHABLE) t.hit("F_뒤집기가_다른_까닭으로_멈춤") { "$what → ${fd.warnings}" }
            if (fm.solution != null) t.hit("F_직접은_멈췄는데_기계는_글이_나옴") { what }
            return
        }
        if (fd.warnings.any { it in stopWarnings }) t.hit("F_멈춤_경고인데_풀이가_있음") { "$what ${fd.warnings}" }
        // 1. 뒤집은 도면 = 같은 점 집합, 순서는 거꾸로
        val setGap = sameSet(fs.drawing.segs, bs.drawing.segs)
        t.maxOf("도면 점 집합 차", setGap) { what }
        if (setGap > 1e-9) t.hit("F_뒤집은_도면이_다름") { "$what 차 $setGap" }
        if ((fs.drawing.segs.first().start - bs.drawing.segs.last().end).length > 1e-9 || (fs.drawing.segs.last().end - bs.drawing.segs.first().start).length > 1e-9) {
            t.hit("F_뒤집은_도면_순서") { what }
        }
        if (!fs.flip) t.hit("F_풀이_flip_표시_없음") { what }
        // 2. 보정 쪽: 진행 방향 기준(왼쪽 = G41)
        val want = expectedSide(fs.drawing.segs, solid)
        if (want == null) t.count("보정 쪽을 기하로 못 정함") else if (fs.side.name != want) t.hit("F_보정_쪽이_진행_방향과_안_맞음") { "$what 풀이 ${fs.side} 기하 $want" }
        if (fs.side == bs.side) t.hit("F_뒤집어도_보정_쪽이_같음") { what }
        if (fs.tip != bs.tip) t.hit("F_뒤집으면_팁이_바뀜") { "$what ${bs.tip} → ${fs.tip}" }
        // 3. 직접 보정: 노드의 노즈 원이 도면에 닿나
        val tipV = tv(fs.tip) * r
        val drawingBoth = bs.drawing.segs + bs.drawing.segs.map { mirror(it) }
        for ((k, n) in fs.nodes.withIndex()) {
            val c = n.prog - tipV
            val d = dist(c, drawingBoth)
            // 날카로운 볼록 꼭짓점의 노드는 두 보정선의 교점이라 r 보다 멀다(정상) — r 보다 가까우면(도면 안쪽) 틀린 것
            t.maxOf("직접 노드: r − (노즈 중심~도면)", r - d) { "$what P${k + 1}" }
            if (abs(d - r) <= 1e-3) t.count("노드: 노즈 원이 도면에 닿음") else t.count("노드: 교점(도면에서 r 보다 멂)")
            if (d < r - 1e-3) {
                if (RoundWarning.CONCAVE_R_LE_NOSE in fd.warnings) t.count("노드가 도면 안쪽(오목 R ≤ r 경고 있음)") else t.hit("F_직접_노드가_도면_안쪽") { "$what P${k + 1} 도면 ${n.drawn} 보정 ${n.prog} 거리 $d" }
            }
        }
        // 4. 직접 보정 NC 를 글로 읽어 노즈 길
        val errors = mutableListOf<String>()
        val mv = parse(fs.nc, errors)
        if (errors.isNotEmpty()) t.hit("F_직접_NC_못_읽음") { "$what $errors\n${fs.nc.joinToString("\n")}" }
        if (fs.nc.any { it.contains("G41") || it.contains("G42") }) t.hit("F_직접_NC_에_G41_G42") { what }
        val dFeed = mv.filter { !it.rapid }.map { shift(it.seg, tipV * -1.0) }
        val dRapid = mv.filter { it.rapid }.map { shift(it.seg, tipV * -1.0) to it.text }
        val twoAxis = mv.drop(1).filter { it.rapid && it.axes == 2 }
        if (twoAxis.isNotEmpty()) t.hit("I_직접_두_축_G0(첫 줄 말고)") { "$what ${twoAxis.map { it.text }}\n${fs.nc.joinToString("\n")}" }
        gapTable(t, "뒤집기 직접", base, checkNose(t, "뒤집기 직접", what, solid, Nose(dFeed, dRapid, emptyList()), r, fs.nc, false), what)
        // 첫 G0 의 끝(공구 교환 자리에서 두 축으로 온다)은 부품 앞 끝면 바깥이어야 한다 — 아니면 오는 길이 부품을 지날 수 있다
        mv.firstOrNull()?.let { m0 ->
            val q = m0.seg.end - tipV
            val outside = if (base.inner) q.z - r >= solid.zFront - tol else (q.z - r >= solid.zFront - tol || abs(q.x) - r >= solid.segs.maxOf { max(it.start.x, it.end.x) } - tol)
            if (!outside) t.hit("F_직접_첫_G0_끝이_부품_안쪽") { "$what ${m0.text} 노즈 중심 X${2 * q.x} Z${q.z}\n${fs.nc.joinToString("\n")}" }
        }
        // 덜 깎임: 기본 방향 직접 보정과 견줌(막힌 시작 구석은 r(√2−1) 까지 설계대로)
        val bErr = mutableListOf<String>()
        val bFeed = parse(bs.nc, bErr).filter { !it.rapid }.map { shift(it.seg, tv(bs.tip) * r * -1.0) }
        val s0 = fs.drawing.segs.first().start
        val wallStart = solid.legEnd
        val bl = lacks(bs.drawing.segs, bFeed, r)
        val fl = lacks(bs.drawing.segs, dFeed, r)
        for (k in fl.indices) {
            val (p, lack) = fl[k]
            val allowed = max(tol, bl[k].second + tol)
            val corner = wallStart && (p - s0).length <= r + 0.02
            if (corner) t.maxOf("뒤집기 직접 덜 깎임(막힌 시작 구석)/r", lack / r) { "$what X${2 * p.x} Z${p.z}" }
            else t.maxOf("뒤집기 직접 덜 깎임(구석 밖, 기본보다)", lack - max(0.0, bl[k].second)) { "$what X${2 * p.x} Z${p.z}" }
            if (lack > (if (corner) r * (sqrt(2.0) - 1) + tol else allowed)) {
                t.hit("F_뒤집기_직접_덜_깎임") { "$what X${2 * p.x} Z${p.z} 남음 $lack (기본 ${bl[k].second})\n${fs.nc.joinToString("\n")}" }
                break
            }
        }
        // 5. 기계 보정
        val ms = fm.solution
        if (ms == null) {
            t.hit("F_직접은_풀리는데_기계는_멈춤") { "$what ${fm.warnings}" }
            return
        }
        val nc = ms.nc
        frameError(nc)?.let { e -> t.hit("F_회사_흐름_꼴이_아님") { "$what: $e\n${nc.joinToString("\n")}" } }
        if (want != null && nc[0] != want) t.hit("F_기계_글의_보정_G_가_진행_방향과_안_맞음") { "$what 글 ${nc[0]} 기하 $want" }
        val bnc = bm.solution?.nc
        val warned = fm.warnings.any { it == RoundWarning.ENTRY_NOT_FOUND || it == RoundWarning.EXIT_NOT_FOUND || it == RoundWarning.SHORT_END_PAST }
        if (warned) t.count("기계 글에 진입 · 빼기 경고(${fm.warnings.filter { it.name.endsWith("FOUND") || it.name.startsWith("SHORT") }})")
        val shapeAlarm = RoundWarning.CONCAVE_R_LE_NOSE in fd.warnings
        for (form in listOf(CompStartup.C, CompStartup.AB)) for (tip in simTips.ifEmpty { listOf(fs.tip) }.distinct()) {
            val whatRun = "$what · 형식 $form · 공구 팁 $tip"
            val (nose, stop) = simNose(nc, r, tip, form)
            if (nose == null) {
                val baseStop = bnc?.let { simNose(it, r, tip, form).second }
                when {
                    baseStop != null -> t.count("알람: 기본 방향도 멈춤")
                    shapeAlarm -> t.count("알람: 오목 R ≤ r 경고 있음")
                    warned -> t.hit("W_알람(경고 있음)") { "$whatRun: $stop\n${nc.joinToString("\n")}" }
                    else -> t.hit("F_뒤집기만_알람") { "$whatRun: $stop\n${nc.joinToString("\n")}" }
                }
                continue
            }
            t.count("기계 시뮬 실행")
            gapTable(t, "뒤집기 기계", base, checkNose(t, "뒤집기 기계", whatRun, solid, nose, r, nc, warned || shapeAlarm), whatRun)
            // 덜 깎임: 같은 조건의 기본 방향 기계 보정과 견줌
            val bNose = bnc?.let { simNose(it, r, tip, form).first }
            val ml = lacks(bs.drawing.segs, nose.feed, r)
            val bml = bNose?.let { lacks(bs.drawing.segs, it.feed, r) }
            for (k in ml.indices) {
                val (p, lack) = ml[k]
                val corner = wallStart && (p - s0).length <= r + 0.02
                val baseLack = bml?.get(k)?.second ?: 0.0
                if (corner) t.maxOf("뒤집기 기계 덜 깎임(막힌 시작 구석)/r", lack / r) { "$whatRun X${2 * p.x} Z${p.z}" }
                else t.maxOf("뒤집기 기계 덜 깎임(구석 밖, 기본보다)", lack - max(0.0, baseLack)) { "$whatRun X${2 * p.x} Z${p.z}" }
                if (lack > (if (corner) r * (sqrt(2.0) - 1) + tol else max(tol, baseLack + tol))) {
                    t.hit(if (shapeAlarm || warned) "W_뒤집기_기계_덜_깎임(경고 있음)" else "F_뒤집기_기계_덜_깎임") {
                        "$whatRun X${2 * p.x} Z${p.z} 남음 $lack (기본 $baseLack)\n${nc.joinToString("\n")}"
                    }
                    break
                }
            }
            // 견줄 값: 기본 방향의 급송 틈
            bNose?.let { b ->
                var c = Double.MAX_VALUE
                for ((s, _) in b.rapid) for (q in sample(s, 0.05)) c = min(c, if (q.z - r > solid.zFront + 3) 3.0 else solid.clearance(q) - r)
                if (b.rapid.isNotEmpty()) gapTable(t, "기본 기계", base, c, whatRun)
            }
        }
    }

    // ------------------------------------------------------------ 입력 만들기

    private fun r3(v: Double) = Math.round(v * 1000) / 1000.0

    private fun dimsOf(type: Int): List<RoundInput> = when (type) {
        1 -> listOf(RoundInput(1, mainR = 20.0, l1 = 20.0), RoundInput(1, mainR = 15.0, l1 = 24.0), RoundInput(1, mainR = 50.0, l1 = 30.0))
        2 -> listOf(
            RoundInput(2, mainR = 10.0, d1 = 12.0, l1 = 10.0), RoundInput(2, mainR = 15.0, d1 = 20.0, l1 = 5.0), RoundInput(2, mainR = 6.0, d1 = 8.0, l1 = 15.0),
            RoundInput(2, mainR = 10.0, d1 = 10.0, l1 = 5.0),
        )
        3 -> listOf(RoundInput(3, mainR = 10.0, d1 = 16.0), RoundInput(3, mainR = 10.0, d1 = 20.0), RoundInput(3, mainR = 25.0, d1 = 30.0), RoundInput(3, mainR = 12.5, d1 = 20.0))
        4 -> listOf(RoundInput(4, mainR = 10.0, d1 = 12.0), RoundInput(4, mainR = 10.0, d1 = 16.0), RoundInput(4, mainR = 8.0, d1 = 6.0))
        5 -> listOf(
            RoundInput(5, mainR = 20.0, d2 = 30.0, d1 = 16.0, l1 = 8.0), RoundInput(5, mainR = 15.0, d2 = 28.0, d1 = 10.0, l1 = 3.0),
            RoundInput(5, mainR = 40.0, d2 = 50.0, d1 = 30.0, l1 = 10.0), RoundInput(5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0),
        )
        else -> listOf(
            RoundInput(6, mainR = 10.0, d2 = 30.0, d1 = 12.0, l1 = 5.0, l2 = 6.0), RoundInput(6, mainR = 10.0, d2 = 30.0, d1 = 12.0, l1 = 5.0, l2 = 12.0),
            RoundInput(6, mainR = 20.0, d2 = 50.0, d1 = 20.0, l1 = 4.0, l2 = 10.0),
        )
    }

    private fun write(name: String, text: String) {
        File(outDir, name).writeText(text)
        println(text)
    }

    // ------------------------------------------------------------ 시험

    /** 여기 팁 표가 앱의 표와 같은가(다르면 아래 검사 전체가 뜻이 없다) */
    @Test
    fun tipTableAgrees() {
        for (tip in 0..9) assertEquals(tv(tip), NoseComp.tipVector(tip, 1.0), "팁 $tip")
    }

    /** 격자: 형상 1~6 × 외·내경(관통) × 치수 × 노즈R × 모서리 R × Z 여유 × 끝 직선 */
    @Test
    fun gridFlippedTypes() {
        val t = Tally("격자 — 뒤집은 TYPE")
        for (inner in listOf(false, true)) for (type in (if (inner) 2..6 else 1..6)) for (through in (if (inner && type >= 5) listOf(false, true) else listOf(false))) {
            for (d in dimsOf(type)) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (cr in listOf(0.0, 0.5, 2.0)) for (zc in listOf(0.0, 1.0)) for (leg in listOf(0.3, 1.0, 5.0)) {
                val i = d.copy(cornerR1 = cr, cornerR2 = if (type >= 5) cr else null, noseR = r, zClear = zc, legLen = leg, inner = inner, through = through)
                checkCase(t, i, emptyList())
            }
        }
        write("grid.txt", t.report())
        assertTrue((t.counts["사례"] ?: 0) > 1500, "사례 수 ${t.counts["사례"]}")
        assertTrue(fails(t).isEmpty(), "실패 갈래 ${fails(t)} — calc/build/verify1110/grid.txt")
    }

    /** 격자(작게) × 넣은 팁 번호 × 시뮬 공구 팁 1~9 */
    @Test
    fun gridTips() {
        val t = Tally("격자 — 팁 번호")
        for (inner in listOf(false, true)) for (type in (if (inner) 2..6 else 1..6)) for (through in (if (inner && type >= 5) listOf(false, true) else listOf(false))) {
            val d = dimsOf(type).first()
            for (r in listOf(0.4, 0.8)) for (cr in listOf(0.0, 0.5)) for (tip in listOf(null, 0, if (inner) 1 else 4, if (inner) 6 else 8)) {
                val i = d.copy(cornerR1 = cr, cornerR2 = if (type >= 5) cr else null, noseR = r, zClear = 1.0, legLen = 5.0, inner = inner, through = through, tip = tip)
                checkCase(t, i, if (tip == null) (1..9).toList() else emptyList())
            }
        }
        write("tips.txt", t.report())
        assertTrue((t.counts["사례"] ?: 0) > 150, "사례 수 ${t.counts["사례"]}")
        assertTrue(fails(t).isEmpty(), "실패 갈래 ${fails(t)} — calc/build/verify1110/tips.txt")
    }

    /** 무작위(씨앗 고정, 0.001 로 반올림한 치수) */
    @Test
    fun randomFlippedTypes() {
        val t = Tally("무작위 — 뒤집은 TYPE")
        val rnd = Random(1110_2026)
        fun u(a: Double, b: Double) = r3(a + (b - a) * rnd.nextDouble())
        repeat(1400) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val r = listOf(0.2, 0.4, 0.4, 0.8, 0.8, 1.2)[rnd.nextInt(6)]
            fun cr() = if (rnd.nextInt(3) == 0) 0.0 else u(0.1, 3.0)
            val through = inner && type >= 5 && rnd.nextBoolean()
            val zc = listOf(0.0, 0.5, 1.0, 1.0, 2.0)[rnd.nextInt(5)]
            val leg = if (rnd.nextInt(4) == 0) u(0.1, 1.5) else u(1.5, 12.0)
            val i = when (type) {
                1 -> u(8.0, 60.0).let { bigR -> RoundInput(1, mainR = bigR, l1 = r3(bigR * (0.3 + 1.5 * rnd.nextDouble())), cornerR1 = cr()) }
                2 -> u(6.0, 40.0).let { d1 -> RoundInput(2, mainR = r3(d1 / 2 * (1.1 + 1.9 * rnd.nextDouble())), d1 = d1, l1 = u(1.0, 20.0), cornerR1 = cr()) }
                3 -> u(5.0, 40.0).let { bigR -> RoundInput(3, mainR = bigR, d1 = r3(2 * bigR * (0.3 + 0.7 * rnd.nextDouble())), cornerR1 = cr()) }
                4 -> u(5.0, 30.0).let { bigR -> RoundInput(4, mainR = bigR, d1 = r3(2 * bigR * (0.3 + 0.68 * rnd.nextDouble())), cornerR1 = cr()) }
                5 -> u(10.0, 50.0).let { bigR ->
                    val d2 = r3(2 * bigR * (0.5 + 0.48 * rnd.nextDouble()))
                    RoundInput(5, mainR = bigR, d2 = d2, d1 = r3(d2 * (0.3 + 0.6 * rnd.nextDouble())), l1 = u(1.0, 15.0), cornerR1 = cr(), cornerR2 = cr())
                }
                else -> u(6.0, 30.0).let { bigR ->
                    val d1 = r3(2 * bigR * (0.3 + 0.6 * rnd.nextDouble()))
                    RoundInput(6, mainR = bigR, d2 = r3(2 * bigR * (1.1 + 1.4 * rnd.nextDouble())), d1 = d1, l1 = u(1.0, 15.0), l2 = r3(bigR * (0.2 + 1.3 * rnd.nextDouble())), cornerR1 = cr(), cornerR2 = cr())
                }
            }.copy(noseR = r, zClear = zc, legLen = leg, inner = inner, through = through)
            checkCase(t, i, emptyList())
        }
        write("random.txt", t.report())
        assertTrue((t.counts["사례"] ?: 0) > 500, "사례 수 ${t.counts["사례"]}")
        assertTrue(fails(t).isEmpty(), "실패 갈래 ${fails(t)} — calc/build/verify1110/random.txt")
    }

    /**
     * 3점호 뒤집기: 원 위 세 점. 뒤집은 글의 깎는 노즈 길이 같은 입력의 기본 방향과 같은 자리(거꾸로)인가, 보정 G 가 반대인가, 도면(원호)에 r 로 닿나.
     * 소재는 모른다(원호 하나) — 급송은 원호와 그 축 거울에서 r 이상 떨어졌는지만 본다
     */
    @Test
    fun flippedArc3() {
        val t = Tally("3점호 뒤집기")
        val rnd = Random(2026_1111)
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        repeat(900) {
            val inner = rnd.nextBoolean()
            val rr = u(2.0, 30.0)
            val c = Pt(u(-20.0, 0.0), u(if (inner) rr + 4 else -rr * 0.5, if (inner) rr + 30 else rr + 20))
            val a0 = u(0.0, 2 * PI)
            val sw = u(0.3, 2.6) * (if (rnd.nextBoolean()) 1 else -1)
            val p = listOf(0.0, 0.5, 1.0).map { c + Pt(cos(a0 + sw * it), sin(a0 + sw * it)) * rr }
            if (p.any { it.x < 0.5 } || abs(p[2].z - p[0].z) < 0.5) return@repeat
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val base = Arc3Input(inner, r, null, r3(2 * p[0].x), r3(p[0].z), r3(2 * p[1].x), r3(p[1].z), r3(2 * p[2].x), r3(p[2].z), listOf(0.0, 0.5, 1.0, 2.0)[rnd.nextInt(4)])
            val what = base.toString()
            val bd = safe(t, what) { RoundCalc.arc3(base) } ?: return@repeat
            val fd = safe(t, what) { RoundCalc.arc3(base.copy(flip = true)) } ?: return@repeat
            val bm = safe(t, what) { RoundCalc.machine(base) } ?: return@repeat
            val fm = safe(t, what) { RoundCalc.machine(base.copy(flip = true)) } ?: return@repeat
            val bs = bd.solution
            val fs = fd.solution
            if (bs == null || fs == null) {
                // 고침 단계(2026-11-11 — V2): 뒤집은 3점호는 진입 급송 길(부품 앞 → P3 쪽)을 재서 원호에 걸리거나 여유가 없으면 멈춘다(NOSE_UNREACHABLE) —
                // 전에는 재지 않고 두 축 G0 한 줄로 갔다. 뒤집기만 그 경고로 멈추는 것은 실패로 치지 않는다(기계 보정도 같이 멈춰야 한다)
                if (bs != null && fd.warnings == listOf(RoundWarning.NOSE_UNREACHABLE)) {
                    t.hit("뒤집기만 안 풀림(NOSE_UNREACHABLE — 진입 급송 길 없음)") { what }
                    if (fm.solution != null) t.hit("F_직접은_멈췄는데_기계는_글이_나옴") { what }
                } else if ((bs == null) != (fs == null)) t.hit("F_한쪽만_풀림") { "$what 기본 ${bd.warnings} 뒤집기 ${fd.warnings}" } else t.count("둘 다 안 풀림")
                return@repeat
            }
            t.count("사례")
            val arc = bs.drawing.segs
            if (sameSet(fs.drawing.segs, arc) > 1e-9) t.hit("F_뒤집은_도면이_다름") { what }
            if (fs.side == bs.side) t.hit("F_뒤집어도_보정_쪽이_같음") { what }
            if (fs.tip != bs.tip) t.hit("F_뒤집으면_팁이_바뀜") { what }
            // 직접 보정: 글을 읽어 노즈 길 — 도면 원호에서 r, 기본 방향과 같은 자리
            val err = mutableListOf<String>()
            val fmv = parse(fs.nc, err)
            val bmv = parse(bs.nc, err)
            if (err.isNotEmpty()) t.hit("F_직접_NC_못_읽음") { "$what $err" }
            val fFeed = fmv.filter { !it.rapid }.map { shift(it.seg, tv(fs.tip) * r * -1.0) }
            val bFeed = bmv.filter { !it.rapid }.map { shift(it.seg, tv(bs.tip) * r * -1.0) }
            val both = arc + arc.map { mirror(it) }
            var pen = 0.0
            for (s in fFeed) for (q in sample(s, 0.02)) pen = max(pen, r - dist(q, if (inner) both else arc))
            t.maxOf("직접 이송 파고듦", pen) { what }
            if (pen > tol && RoundWarning.CONCAVE_R_LE_NOSE !in fd.warnings) t.hit("F_직접_이송이_원호를_팜") { "$what $pen\n${fs.nc.joinToString("\n")}" }
            val fl = lacks(arc, fFeed, r)
            val bl = lacks(arc, bFeed, r)
            for (k in fl.indices) if (fl[k].second > max(tol, bl[k].second + tol)) {
                t.hit("F_직접_덜_깎임") { "$what ${fl[k]} 기본 ${bl[k].second}\n${fs.nc.joinToString("\n")}" }
                break
            }
            var rc = Double.MAX_VALUE
            for (m in fmv.drop(1).filter { it.rapid }) for (q in sample(shift(m.seg, tv(fs.tip) * r * -1.0), 0.02)) rc = min(rc, dist(q, both) - r)
            if (rc < -tol) t.hit("F_직접_급송이_원호를_지남") { "$what $rc\n${fs.nc.joinToString("\n")}" }
            // 기계 보정
            val ms = fm.solution
            val bms = bm.solution
            if (ms == null || bms == null) {
                t.hit("F_기계_보정이_안_풀림") { "$what ${fm.warnings} ${bm.warnings}" }
                return@repeat
            }
            frameError(ms.nc, z20 = false)?.let { e -> t.hit("F_회사_흐름_꼴이_아님") { "$what: $e\n${ms.nc.joinToString("\n")}" } }
            if (ms.nc[0] == bms.nc[0]) t.hit("F_기계_글의_보정_G_가_안_바뀜") { what }
            if (ms.nc[0] != fs.side.name) t.hit("F_기계_글의_보정_G_가_풀이와_다름") { what }
            val warned = fm.warnings.any { it == RoundWarning.ENTRY_NOT_FOUND || it == RoundWarning.EXIT_NOT_FOUND }
            val (nose, stop) = simNose(ms.nc, r, fs.tip, CompStartup.C)
            val (bNose, _) = simNose(bms.nc, r, bs.tip, CompStartup.C)
            if (nose == null) {
                when {
                    bNose == null -> t.count("알람: 기본 방향도 멈춤")
                    RoundWarning.CONCAVE_R_LE_NOSE in fd.warnings -> t.count("알람: 오목 R ≤ r 경고 있음")
                    warned -> t.hit("W_알람(경고 있음)") { "$what: $stop\n${ms.nc.joinToString("\n")}" }
                    else -> t.hit("F_뒤집기만_알람") { "$what: $stop\n${ms.nc.joinToString("\n")}" }
                }
                return@repeat
            }
            t.count("기계 시뮬 실행")
            var mpen = 0.0
            var mAt = ""
            for (s in nose.feed) for (q in sample(s, 0.02)) (r - dist(q, if (inner) both else arc)).let { if (it > mpen) { mpen = it; mAt = "X${2 * q.x} Z${q.z}" } }
            t.maxOf("기계 이송 파고듦", mpen) { what }
            if (mpen > tol && RoundWarning.CONCAVE_R_LE_NOSE !in fd.warnings) {
                t.hit(if (warned) "W_기계_이송이_원호를_팜(경고 있음)" else "F_기계_이송이_원호를_팜") { "$what $mpen @ $mAt\n${ms.nc.joinToString("\n")}" }
            }
            var mrc = Double.MAX_VALUE
            for ((s, _) in nose.rapid) for (q in sample(s, 0.02)) mrc = min(mrc, dist(q, both) - r)
            if (nose.rapid.isNotEmpty()) t.minOf("기계 급송 틈(원호 · 거울까지)", mrc) { what }
            if (mrc < -tol) t.hit(if (warned) "W_기계_급송이_원호를_지남(경고 있음)" else "F_기계_급송이_원호를_지남") { "$what $mrc\n${ms.nc.joinToString("\n")}" }
            if (bNose != null) {
                val ml = lacks(arc, nose.feed, r)
                val bml = lacks(arc, bNose.feed, r)
                for (k in ml.indices) if (ml[k].second > max(tol, bml[k].second + tol)) {
                    t.hit("F_기계_덜_깎임") { "$what ${ml[k]} 기본 ${bml[k].second}\n${ms.nc.joinToString("\n")}" }
                    break
                }
            }
        }
        write("arc3.txt", t.report())
        assertTrue((t.counts["사례"] ?: 0) > 300, "사례 수 ${t.counts["사례"]}")
        assertTrue(fails(t).isEmpty(), "실패 갈래 ${fails(t)} — calc/build/verify1110/arc3.txt")
    }

    // ------------------------------------------------------------ 손 계산 예제(설계 단계의 g42_examples.py — 앱 코드를 안 쓰고 기하에서 따로 낸 값)
    // 진입 줄은 고침 단계(2026-11-11 — 이 검증의 V1 · V2 · V3 로 진입 규칙이 바뀜)에서 새 규칙으로 다시 손 계산해 바꿨다: 기계 보정 막힌 시작은 급송
    // X = D ± 2(2r + c) · Z = 시작 Z + r + c 뒤 `G1 Z(시작) F.1` · `X(D)`, 축에서 시작은 급송 Z = 첫 깎는 점 + 2r + c, 직접 보정 막힌 시작은 급송 끝 = 첫 보정점에서
    // X ± 2c · Z + c 뒤 비스듬한 G1 한 줄, 3점호는 P3 의 법선 앞 c 에서 급송을 멈춤. 형상 줄 · 빼는 줄은 처음 손 계산 그대로다

    private class Example(val name: String, val direct: List<String>, val machine: List<String>, val calc: () -> RoundResult, val mach: () -> RoundResult, val base: RoundInput?)

    private fun l(s: String) = s.split(" / ")

    private val examples: List<Example> = run {
        val a = RoundInput(3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 5.0, noseR = 0.4, zClear = 1.0)
        val b = a.copy(inner = true, legLen = 10.0)
        val c = RoundInput(5, mainR = 18.0, d2 = 30.0, d1 = 10.0, l1 = 5.0, cornerR1 = 1.0, cornerR2 = 1.0, legLen = 5.0, noseR = 0.4, zClear = 1.0)
        val d = RoundInput(5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, noseR = 0.4, zClear = 1.0, inner = true, through = true)
        val d2 = d.copy(through = false)
        val f = RoundInput(2, mainR = 10.0, d1 = 10.0, l1 = 5.0, cornerR1 = 1.0, legLen = 5.0, noseR = 0.4, zClear = 1.0)
        val e = Arc3Input(false, 0.4, null, 30.0, 0.0, 44.142, -2.929, 50.0, -10.0, 1.0, flip = true)
        fun ex(name: String, i: RoundInput, direct: String, machine: String) =
            Example(name, l(direct), l(machine), { RoundCalc.calc(i.copy(flip = true)) }, { RoundCalc.machine(i.copy(flip = true)) }, i)
        listOf(
            ex("A 외경 3", a, "G0X22.Z1. / Z-9. / G1X20.Z-10. / Z-5.741 / G2X19.391Z-4.87R1.4 / X-.8Z0.R12.9",
                "G41 / G0X23.6Z20. / Z-8.6 / G1Z-10.F.1 / X20. / Z-5.341 / G2X19.565Z-4.719R1. / X0.Z0.R12.5 / G1X-.82F.3 / Z.986 / G40 / G0Z200."),
            ex("B 내경 3", b, "G0X.8Z1. / Z-14. / G1Z-15. / G2X19.739Z-10.432R12.1 / X20.Z-10.059R.6 / G1Z-.4",
                "G42 / G0X-.82Z20. / Z-13.2 / G1Z-15.F.1 / X0. / G2X19.565Z-10.281R12.5 / X20.Z-9.659R1. / G1Z0. / Z1.F.3 / G40 / G0Z200."),
            ex("C 외경 5", c, "G0X32.Z1. / Z-16.342 / G1X30.Z-17.342 / Z-13.048 / G2X29.506Z-12.254R1.4 / X10.821Z-5.233R18.4 / G3X10.Z-4.664R.6 / G1Z0. / X-.8",
                "G41 / G0X33.6Z20. / Z-15.942 / G1Z-17.342F.1 / X30. / Z-12.648 / G2X29.647Z-12.081R1. / X11.368Z-5.213R18. / G3X10.Z-4.264R1. / G1Z0. / X-.8 / Z1.F.3 / G40 / G0Z200."),
            ex("D 내경 5 관통", d, "G0X28.Z1. / Z-15. / G1X30.Z-16. / Z-10.199 / G2X40.Z-5.266R24.6 / G1Z-.4",
                "G42 / G0X26.4Z20. / Z-14.6 / G1Z-16.F.1 / X30. / Z-10. / G2X40.Z-5.R25. / G1Z0. / Z1.F.3 / G40 / G0Z200."),
            ex("D2 내경 5 막힘", d2, "G0X.8Z1. / Z-15. / G1Z-16. / X30. / Z-10.199 / G2X40.Z-5.266R24.6 / G1Z-.4",
                "G42 / G0X-.8Z20. / Z-14.2 / G1Z-16.F.1 / X30. / Z-10. / G2X40.Z-5.R25. / G1Z0. / Z1.F.3 / G40 / G0Z200."),
            ex("F 외경 2", f, "G0X22.Z1. / Z-26.321 / X12. / G1X10.Z-27.321 / Z-23.28 / G3X10.545Z-22.777R.6 / G2X20.Z-14.06R10.4 / X10.545Z-5.344R10.4 / G3X10.Z-4.841R.6 / G1Z0. / X-.8",
                "G41 / G0X23.6Z20. / Z-25.921 / X13.6 / G1Z-27.321F.1 / X10. / Z-22.88 / G3X10.909Z-22.042R1. / G2X20.Z-13.66R10. / X10.909Z-5.279R10. / G3X10.Z-4.441R1. / G1Z0. / X-.8 / Z1.F.3 / G40 / G0Z200."),
            Example("E 3점호", l("G0X52.Z1. / Z-9. / G1X50.Z-10.4 / G2X29.2Z0.R10.4"), l("G41 / G0X53.6Z20. / Z-8.6 / G1Z-10.45F.1 / X50. / Z-10. / G2X30.Z0.R10. / G1X28.F.3 / Z.986 / G40 / G0Z200."),
                { RoundCalc.arc3(e) }, { RoundCalc.machine(e) }, null),
        )
    }

    /** 손 계산 예제와 앱 값 대조. 다른 것은 어느 쪽이 맞는지 시뮬레이터 + 소재로 가린다(급송이 소재를 지나는 쪽이 틀린 것) */
    @Test
    fun designExamples() {
        val out = StringBuilder()
        val wrong = mutableListOf<String>()
        for (ex in examples) {
            val direct = ex.calc().solution?.nc?.filter { !it.startsWith("(") }
            val machine = ex.mach().solution?.nc
            out.appendLine("== ${ex.name}")
            out.appendLine("직접: ${if (direct == ex.direct) "같음" else "다름 — 손 ${ex.direct} / 앱 $direct"}")
            out.appendLine("기계: ${if (machine == ex.machine) "같음" else "다름 — 손 ${ex.machine} / 앱 $machine"}")
            if (direct != ex.direct) wrong += "${ex.name} 직접 보정이 손 계산과 다름"
            if (machine != ex.machine && ex.base != null && machine != null) {
                // 어느 쪽이 맞나: 두 글을 같은 소재에서 돌려 급송 틈을 견준다
                val solid = Solid(RoundCalc.calc(ex.base).solution!!.drawing.segs, ex.base.inner)
                fun gap(nc: List<String>): String {
                    val (nose, stop) = simNose(nc, ex.base.noseR, NoseComp.standardTip(ex.base.inner, false), CompStartup.C)
                    if (nose == null) return "알람 $stop"
                    var c = Double.MAX_VALUE
                    var at = ""
                    for ((s, text) in nose.rapid) for (q in sample(s, 0.02)) (solid.clearance(q) - ex.base.noseR).let { if (it < c) { c = it; at = text } }
                    var pen = 0.0
                    for (s in nose.feed) for (q in sample(s, 0.02)) pen = max(pen, ex.base.noseR - solid.clearance(q))
                    return "급송 틈 $c ($at) · 이송 파고듦 $pen"
                }
                val hand = gap(ex.machine)
                val app = gap(machine)
                out.appendLine("  손 계산 글: $hand")
                out.appendLine("  앱 글: $app")
                if (!app.startsWith("급송 틈") || Regex("급송 틈 (-?[0-9.E-]+)").find(app)!!.groupValues[1].toDouble() < -tol) wrong += "${ex.name} 앱의 기계 보정 글이 소재를 지남: $app"
            } else if (machine != ex.machine) wrong += "${ex.name} 기계 보정이 손 계산과 다름"
        }
        write("examples.txt", out.toString())
        assertTrue(wrong.isEmpty(), wrong.joinToString(" | ") + " — " + out)
    }

    /** 보고용: 몇 사례의 기계 보정 글과 급송 줄마다의 틈(뒤집기 · 기본) */
    @Test
    fun dumpSamples() {
        val cases = listOf(
            "외경 3 (골든 b3) 노즈 0.4 Z여유 1" to RoundInput(3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 5.0, noseR = 0.4, zClear = 1.0),
            "외경 3 노즈 0.8 Z여유 1" to RoundInput(3, mainR = 25.0, d1 = 30.0, cornerR1 = 0.0, legLen = 5.0, noseR = 0.8, zClear = 1.0),
            "외경 3 노즈 0.4 Z여유 0(빈칸)" to RoundInput(3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 5.0, noseR = 0.4, zClear = 0.0),
            "내경 5 막힘 노즈 0.8 Z여유 1" to RoundInput(5, mainR = 20.0, d2 = 30.0, d1 = 16.0, l1 = 8.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, noseR = 0.8, zClear = 1.0, inner = true),
            "내경 5 막힘 노즈 0.4 Z여유 1" to RoundInput(5, mainR = 20.0, d2 = 30.0, d1 = 16.0, l1 = 8.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, noseR = 0.4, zClear = 1.0, inner = true),
            "내경 5 관통 노즈 0.4 Z여유 1" to RoundInput(5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, noseR = 0.4, zClear = 1.0, inner = true, through = true),
            "내경 5 관통 노즈 0.8 Z여유 1" to RoundInput(5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, noseR = 0.8, zClear = 1.0, inner = true, through = true),
            "내경 3 노즈 0.4 Z여유 1" to RoundInput(3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0, noseR = 0.4, zClear = 1.0, inner = true),
            "외경 2 아령 노즈 0.4 Z여유 1" to RoundInput(2, mainR = 10.0, d1 = 10.0, l1 = 5.0, cornerR1 = 1.0, legLen = 5.0, noseR = 0.4, zClear = 1.0),
            "외경 1 술통 노즈 0.4 Z여유 1" to RoundInput(1, mainR = 20.0, l1 = 20.0, cornerR1 = 0.0, noseR = 0.4, zClear = 1.0),
            "외경 6 노즈 0.4 Z여유 1" to RoundInput(6, mainR = 10.0, d2 = 30.0, d1 = 12.0, l1 = 5.0, l2 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, noseR = 0.4, zClear = 1.0),
        )
        val out = StringBuilder()
        for ((name, base) in cases) {
            val solid = Solid(RoundCalc.calc(base).solution!!.drawing.segs, base.inner)
            for (flip in listOf(true, false)) {
                val i = base.copy(flip = flip)
                val m = RoundCalc.machine(i)
                val d = RoundCalc.calc(i)
                out.appendLine("== $name · ${if (flip) "뒤집기" else "기본"} · 경고 ${m.warnings}")
                val nc = m.solution?.nc
                if (nc == null) {
                    out.appendLine("  (안 풀림)")
                    continue
                }
                val tip = m.solution!!.tip
                val (nose, stop) = simNose(nc, base.noseR, tip, CompStartup.C)
                out.appendLine("  기계: " + nc.joinToString(" / "))
                if (nose == null) out.appendLine("  알람 $stop") else {
                    for ((s, text) in nose.rapid) {
                        var c = Double.MAX_VALUE
                        var at = s.start
                        for (q in sample(s, 0.02)) (if (q.z - base.noseR > solid.zFront + 3) 3.0 else solid.clearance(q) - base.noseR).let { if (it < c) { c = it; at = q } }
                        out.appendLine("    급송 $text: 노즈 중심 X${r3(2 * s.start.x)} Z${r3(s.start.z)} → X${r3(2 * s.end.x)} Z${r3(s.end.z)} · 소재까지 가장 가까운 틈 ${r3(c)} (노즈 중심 X${r3(2 * at.x)} Z${r3(at.z)})")
                    }
                }
                out.appendLine("  직접: " + d.solution!!.nc.joinToString(" / "))
                val err = mutableListOf<String>()
                val tipV = tv(d.solution!!.tip) * base.noseR
                for (mv in parse(d.solution!!.nc, err).drop(1).filter { it.rapid }) {
                    var c = Double.MAX_VALUE
                    for (q in sample(shift(mv.seg, tipV * -1.0), 0.02)) c = min(c, if (q.z - base.noseR > solid.zFront + 3) 3.0 else solid.clearance(q) - base.noseR)
                    out.appendLine("    급송 ${mv.text}: 소재까지 가장 가까운 틈 ${r3(c)}")
                }
            }
        }
        write("samples.txt", out.toString())
    }
}
