package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StepSrc
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.verify.GeometricVerifyTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증 2026-11-13 — 통합 고침(테이퍼 진입 급송 · 내경 축 너머 후퇴)을 구현과 따로 잰다. 계산기의 도우미(FrameMaterial · CompFrame)는 쓰지 않고
 * 거리 · 소재 · 막힌 끝을 여기서 새로 정한다. 글은 시뮬레이터(형식 C · 급송 dog-leg)로 돌린다. c = max(Z 여유, 노즈R).
 */
internal object V1112 {
    val pre = listOf("T0101", "G97S1000M03", "G99")

    /** 보고 줄: 표준 출력(시험 XML — 콘솔 인코딩으로 한글이 깨질 수 있다)과, 검증 작업 폴더가 있으면 거기 UTF-8 파일([name].txt)에도 */
    fun log(name: String, line: String) {
        println(line)
        val dir = File("C:/Users/qlqo1/AppData/Local/Temp/claude/D------/d07d09c9-0e23-46d6-8d20-7d841b2772ce/scratchpad/cnc-feedback/integ-taper")
        if (dir.isDirectory) runCatching { File(dir, "v-$name.txt").appendText(line + System.lineSeparator(), Charsets.UTF_8) }
    }

    fun sim(nc: List<String>, r: Double, tip: Int, mirror: Boolean = false): SimRun = GSim.run(
        SimInput(
            (pre + nc + "M30").joinToString("\n"),
            MachineSetup(compStartup = CompStartup.C, home = Pt(if (mirror) -100.0 else 100.0, 100.0)),
            mapOf(1 to ToolOffset(r, tip)),
        ),
    )

    /** 직접 보정 글: 주석 줄을 빼고 F 가 없으면 첫 G1 에 F.1 */
    fun direct(nc: List<String>): List<String> {
        val l = nc.filter { !it.startsWith("(") }
        if (l.any { 'F' in it }) return l
        val g1 = l.indexOfFirst { it.startsWith("G1") || it.startsWith("G01") }
        return l.mapIndexed { k, t -> if (k == g1) t + "F.1" else t }
    }

    fun dLine(a: Pt, b: Pt, p: Pt): Double {
        val d = b - a
        val l2 = d dot d
        if (l2 < 1e-18) return (p - a).length
        val t = (((p - a) dot d) / l2).coerceIn(0.0, 1.0)
        return (p - (a + d * t)).length
    }

    fun dArc(s: Arc, p: Pt): Double {
        val rr = (s.start - s.center).length
        val sw = s.sweepDeg * PI / 180
        val ends = min((p - s.start).length, (p - s.end).length)
        if (sw < 1e-12) return ends
        val v = p - s.center
        if (v.length < 1e-12) return rr
        val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
        val ap = atan2(v.x, v.z)
        var rel = if (s.cw) a0 - ap else ap - a0
        rel = ((rel % (2 * PI)) + 2 * PI) % (2 * PI)
        return if (rel <= sw + 1e-12) abs(v.length - rr) else ends
    }

    fun dist(s: Seg, p: Pt): Double = when (s) {
        is Line -> dLine(s.start, s.end, p)
        is Arc -> dArc(s, p)
    }

    fun samples(s: Seg, step: Double = 0.02): List<Pt> = when (s) {
        is Line -> {
            val n = max(1, ceil((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val rr = (s.start - s.center).length
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            val n = max(1, ceil(abs(sw) * rr / step).toInt())
            (0..n).map { val a = a0 + sw * it / n; Pt(s.center.z + rr * cos(a), s.center.x + rr * sin(a)) }
        }
    }

    fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    fun minDist(mat: List<Seg>, p: Pt) = mat.minOf { dist(it, p) }

    fun unit(v: Pt) = v * (1 / v.length)

    /**
     * 막힌 끝의 벽(여기서 따로 정함): 가공 순서 [segs] 의 첫 요소를 시작 좌표부터 깎을 때([fromStart]), 실제 척 쪽(계산기 −Z)에서 +Z 로 깎기 시작하는
     * 수평이면 시작 좌표에 선 벽(외경은 위로, 내경은 축까지)과 그 너머 같은 면, 재료 쪽으로 깎기 시작하는 단면(외경 위로 · 내경 아래로 — 외경 X0 은 빼고)이면
     * 그 단면을 너머로 늘인 것. 끝도 같은 식(깎아 끝나는 쪽).
     */
    fun walls(segs: List<Seg>, inner: Boolean, fromStart: Boolean, toEnd: Boolean): List<Seg> {
        val out = mutableListOf<Seg>()
        fun at(p: Pt, beyond: Pt) {
            // beyond = 그 끝에서 너머로 가는 방향
            if (abs(beyond.z) > abs(beyond.x)) {
                if (beyond.z < 0) {
                    out += Line(p, p + beyond * 1000.0)
                    out += if (inner) Line(p, Pt(p.z, 0.0)) else Line(p, Pt(p.z, p.x + 1000.0))
                }
            } else {
                val toMat = if (inner) beyond.x > 0 else beyond.x < 0
                if (toMat && !(!inner && p.x <= 1e-9)) out += Line(p, if (inner) p + beyond * 1000.0 else Pt(p.z, 0.0))
            }
        }
        if (fromStart) {
            val f = segs.first()
            val u = unit(f.end - f.start)
            at(f.start, u * -1.0)
        }
        if (toEnd) {
            val l = segs.last()
            val u = unit(l.end - l.start)
            at(l.end, u)
        }
        return out
    }

    /** 글의 줄마다 (모달 G, 끝 점 — 반경, 줄 낱말). 주석 줄은 앞 점 그대로 */
    class NcLine(val g: Int, val p: Pt, val r: String?, val f: String?, val text: String) {
        fun spelled() = "G$g X${Fmt.nc(2 * p.x)} Z${Fmt.nc(p.z)}" + (r?.let { " R$it" } ?: "")
    }

    fun parse(nc: List<String>): List<NcLine> {
        var g = 0
        var x = 0.0
        var z = 0.0
        val word = Regex("([A-Z])([-+]?[0-9.]+)")
        return nc.map { raw ->
            val t = raw.replace(Regex("\\(.*?\\)"), "")
            var r: String? = null
            var f: String? = null
            for (m in word.findAll(t)) {
                val (k, v) = m.destructured
                when (k) {
                    "G" -> v.toDouble().toInt().let { if (it in 0..3) g = it }
                    "X" -> x = v.toDouble() / 2
                    "Z" -> z = v.toDouble()
                    "R" -> r = v
                    "F" -> f = v
                }
            }
            NcLine(g, Pt(z, x), r, f, raw)
        }
    }

    /** G41/G42 글에서 첫 깎는 점(도면 꼭짓점)에 닿는 줄 — F.1 줄부터 찾는다 */
    fun firstCutLine(nc: List<String>, drawing: List<Seg>, fromStart: Boolean): Int {
        val ls = parse(nc)
        val f1 = nc.indexOfFirst { it.endsWith("F.1") }
        // 비운 시작은 첫 요소(그림만)를 깎지 않는다 — 진입 이송 점 P 가 그 요소 끝(꼭짓점)에 우연히 오는 것을 첫 깎는 점으로 보지 않게
        val starts = drawing.drop(if (fromStart) 0 else 1).map { it.start }
        return (f1 until nc.size).firstOrNull { k -> starts.any { (ls[k].p - it).length < 1e-3 } } ?: -1
    }

    /** 회사 흐름: G41/G42 혼자 → G0X…Z20. → 급송 한 축씩 → G1(F.1) … → … F.3 → G40 혼자 → G0Z200. 어긋나면 그 까닭 */
    fun companyFlow(nc: List<String>): String? {
        if (nc.size < 6) return "줄 수"
        if (nc[0] != "G41" && nc[0] != "G42") return "첫 줄 ${nc[0]}"
        if (!Regex("^G0X-?[0-9.]+Z20\\.$").matches(nc[1])) return "둘째 줄 ${nc[1]}"
        val f1 = nc.indexOfFirst { it.endsWith("F.1") }
        if (f1 < 2 || !nc[f1].startsWith("G1")) return "F.1 줄 $f1"
        for (k in 2 until f1) {
            val t = nc[k]
            if (t.startsWith("G1") || t.startsWith("G2") || t.startsWith("G3")) return "급송 사이 이송 $t"
            if (t.contains('X') && t.contains('Z')) return "급송이 두 축을 함께 $t"
        }
        val f3 = nc.indexOfFirst { it.contains("F.3") }
        if (f3 <= f1) return "F.3 줄"
        val g40 = nc.indexOf("G40")
        if (g40 < f3 || nc.getOrNull(g40 + 1) != "G0Z200." || g40 + 2 != nc.size) return "끝 ${nc.drop(f3)}"
        return null
    }

    // ---------------------------------------------------------------- 격자

    class Case(val tag: String, val inner: Boolean, val r: Double, val c: Double, val single: TaperInput? = null, val double: DoubleTaperInput? = null) {
        fun calc(): TaperResult = single?.let(TaperCalc::calc) ?: TaperCalc.calcDouble(double!!)
    }

    /** 홑테이퍼 그림 순서(T1 = 오른쪽 Z0 · T2 = 왼쪽 Z−L) — [flip] 이면 화면처럼 칸을 맞바꿔 뒤집는다 */
    fun single(
        inner: Boolean, type: Int, kind: Int, r: Double, zc: Double, tip: Int?, lead: Double?, tail: Double?, cr: Double, geom: Int, flip: Boolean,
        reverse: Boolean = false, approach: Boolean = true,
    ): TaperInput {
        val up = if (inner == (type == 5)) 1.0 else -1.0
        val (x0, dx, len) = when (geom) {
            0 -> Triple(if (inner) (if (type == 5) 30.0 else 40.0) else (if (type == 5) 50.0 else 40.0), 10.0, 5.0)
            1 -> Triple(if (inner) (if (type == 5) 30.0 else 40.0) else (if (type == 5) 50.0 else 40.0), 10.0, 1.5)
            else -> Triple(if (inner) (if (type == 5) 6.0 else 10.0) else (if (type == 5) 10.0 else 6.0), 4.0, 8.0)
        }
        val x1 = x0
        val x2 = x1 + up * dx
        val arcR = if (kind > 0) TaperCalc.arcMinR(x1, 0.0, x2, -len)?.let { ceil(it * 2 * 1000) / 1000 } else null
        val start = lead?.let { if (type <= 2) x1 - up * 2 * it else it }
        val end = tail?.let { if (type == 2 || type == 4) x2 + up * 2 * it else -len - it }
        val base = TaperInput(
            inner, type, reverse, r, tip, x1, 0.0, x2, -len, null, zc, cr, cr, start = start, end = end,
            arc = kind > 0, arcR = arcR, arcConvex = kind == 1, approach = approach,
        )
        return if (!flip) base
        else base.copy(x1 = base.x2, z1 = base.z2, x2 = base.x1, z2 = base.z1, cornerR1 = base.cornerR2, cornerR2 = base.cornerR1, start = base.end, end = base.start, flip = true)
    }

    fun double(inner: Boolean, type: Int, r: Double, zc: Double, lead: Double?, tail: Double?, cr: Double, len: Double, flip: Boolean, reverse: Boolean = false, approach: Boolean = true): DoubleTaperInput {
        val xs = when (type) {
            1 -> listOf(40.0, 46.0, 52.0)
            2 -> listOf(40.0, 50.0, 40.0)
            3 -> listOf(50.0, 40.0, 50.0)
            else -> listOf(52.0, 46.0, 40.0)
        }.let { if (inner) it.map { x -> 92.0 - x } else it }
        val base = DoubleTaperInput(inner, reverse, r, null, xs[0], 0.0, xs[1], -len, xs[2], -2 * len, zc, cr, cr, cr, start = lead, end = tail?.let { -2 * len - it }, approach = approach)
        return if (!flip) base else base.copy(x1 = base.x3, z1 = base.z3, x3 = base.x1, z3 = base.z1, cornerR1 = base.cornerR3, cornerR3 = base.cornerR1, start = base.end, end = base.start, flip = true)
    }

    /** 격자 전체(뒤집기 · 역방향 · 거울은 부르는 쪽이 정한다) */
    fun grid(flip: Boolean, reverse: Boolean = false, approach: Boolean = true): List<Case> {
        val out = mutableListOf<Case>()
        for (inner in listOf(false, true)) for (type in 1..5) for (kind in 0..2) for (geom in 0..2) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0))
            for (lead in listOf(null, 1.0, 3.0)) for (tail in listOf(null, 1.0, 3.0)) for (cr in listOf(0.0, 0.5)) {
                val i = single(inner, type, kind, r, zc, null, lead, tail, cr, geom, flip, reverse, approach)
                out += Case(i.toString(), inner, r, max(zc, r), single = i)
            }
        // 팁(표준 밖): 0 · 8 · 2(외경) / 3(내경) — 노즈 0.4 · 1.2, Z 여유 0 · 5, 첫 모양
        for (inner in listOf(false, true)) for (tip in listOf(0, 8, if (inner) 3 else 2)) for (type in 1..5) for (kind in 0..2) for (r in listOf(0.4, 1.2)) for (zc in listOf(0.0, 5.0))
            for (lead in listOf(null, 1.0, 3.0)) for (tail in listOf(null, 1.0, 3.0)) for (cr in listOf(0.0, 0.5)) {
                val i = single(inner, type, kind, r, zc, tip, lead, tail, cr, 0, flip, reverse, approach)
                out += Case(i.toString(), inner, r, max(zc, r), single = i)
            }
        for (inner in listOf(false, true)) for (type in 1..4) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0))
            for (lead in listOf(null, 1.0, 3.0)) for (tail in listOf(null, 3.0)) for (cr in listOf(0.0, 0.5)) for (len in listOf(5.0, 2.5)) {
                val i = double(inner, type, r, zc, lead, tail, cr, len, flip, reverse, approach)
                out += Case(i.toString(), inner, r, max(zc, r), double = i)
            }
        return out
    }

    /** 진입 급송(첫 이송 앞 모든 이동 — 형식 C 의 보정 시작 옮김 포함)의 노즈와 [mat] 사이 가장 작은 틈. 첫 이송 전에 멈추면 null */
    fun entryGap(run: SimRun, r: Double, mat: List<Seg>): Double? {
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size }
        val firstFeed = moves.indexOfFirst { it.kind == MoveKind.FEED }
        if (firstFeed < 0) return null
        var gap = Double.MAX_VALUE
        for (m in moves.take(firstFeed)) for (p in samples(m.nose)) gap = min(gap, minDist(mat, p) - r)
        return gap
    }

    /** 모든 이동의 노즈가 [mat] 을 파는 가장 큰 깊이와 그 줄 */
    fun gouge(run: SimRun, nc: List<String>, r: Double, mat: List<Seg>): Pair<Double, String> {
        var worst = 0.0
        var at = ""
        for (m in run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size }) {
            val d = samples(m.nose).maxOf { r - minDist(mat, it) }
            if (d > worst) {
                worst = d
                at = "${m.kind} ${m.line - pre.size}줄 '${nc.getOrNull(m.line - pre.size - 1)}'"
            }
        }
        return worst to at
    }

    /** 내경 바닥 단면이 축(X0)까지인가(깎아 끝나는 쪽) — 여기서 따로 정한다 */
    fun axisEnd(s: TaperSolution, inner: Boolean): Boolean {
        val l = s.drawing.segs.last()
        val u = l.end - l.start
        return inner && s.toEnd && l is Line && abs(u.z) < 1e-9 && u.x < 0 && l.end.x <= 1e-9
    }

    // ---------------------------------------------------------------- 옛 계산기(b305c71) — 그 커밋 빌드의 calc-jvm.jar 를 따로 읽는다

    val oldJar: File = File(
        System.getProperty("bmt.oldJar") ?: System.getenv("BMT_OLD_JAR")
            ?: "C:/Users/qlqo1/AppData/Local/Temp/claude/D------/d07d09c9-0e23-46d6-8d20-7d841b2772ce/scratchpad/cnc-feedback/integ-taper/b305c71-calc-jvm.jar",
    )

    private class ChildFirst(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            if (name.startsWith("com.bmt.nosecalc.")) findLoadedClass(name) ?: findClass(name) else super.loadClass(name, resolve)
        }
    }

    private val loader: ClassLoader by lazy { ChildFirst(arrayOf(oldJar.toURI().toURL()), V1112::class.java.classLoader) }

    class Old(val nc40: List<String>?, val nc4x: List<String>?, val warnings: List<String>)

    private fun convert(v: Any): Any {
        val cls = loader.loadClass(v.javaClass.name)
        val n = v.javaClass.methods.count { Regex("component[0-9]+").matches(it.name) && it.parameterCount == 0 }
        val vals = (1..n).map { v.javaClass.getMethod("component$it").invoke(v) }
        val ctor = cls.declaredConstructors.single { it.parameterCount == n && it.parameterTypes.none { t -> t.name.endsWith("DefaultConstructorMarker") } }
        val args = vals.mapIndexed { k, x ->
            if (x is Enum<*>) ctor.parameterTypes[k].enumConstants!!.first { (it as Enum<*>).name == x.name } else x
        }
        return ctor.newInstance(*args.toTypedArray())
    }

    private fun call(obj: String, method: String, input: Any): Any {
        val o = convert(input)
        val cls = loader.loadClass("com.bmt.nosecalc.calc.$obj")
        return cls.getMethod(method, o.javaClass).invoke(cls.getField("INSTANCE").get(null), o)
    }

    private fun get(o: Any, name: String): Any? = o.javaClass.getMethod(name).invoke(o)

    @Suppress("UNCHECKED_CAST")
    fun oldTaper(c: Case): Old {
        val res = if (c.single != null) call("TaperCalc", "calc", c.single) else call("TaperCalc", "calcDouble", c.double!!)
        val s = get(res, "getSolution")
        val w = (get(res, "getWarnings") as List<Any>).map { (it as Enum<*>).name }
        return Old(s?.let { get(it, "getNcG40") as List<String> }, s?.let { get(it, "getNcG41G42") as List<String> }, w)
    }

    @Suppress("UNCHECKED_CAST")
    fun oldRound(i: RoundInput, machine: Boolean): Pair<List<String>?, List<String>> {
        val res = call("RoundCalc", if (machine) "machine" else "calc", i)
        val s = get(res, "getSolution")
        return (s?.let { get(it, "getNc") as List<String> }) to (get(res, "getWarnings") as List<Any>).map { (it as Enum<*>).name }
    }
}

/**
 * 회귀: 옛 계산기(b305c71)와 글을 견준다. 바뀌어도 되는 것 = 뒤집은(화면 정방향) G41/G42 의 진입 줄 · 내경 바닥 X0 끝의 직접 보정, 그리고 최종 확인 뒤 고침
 * (2026-11-13)의 직접 보정 — 노즈 기준 막힌 시작 · 끝 · 진입 · 뒤집은 접근, 내경 축 쪽 노즈 중심 멈춤(시뮬레이터로 옛 글보다 나쁘지 않은 것만 — `V1113.vsOld`)
 */
class Verify1112TaperRegressionTest {
    private fun need() = assumeTrue(V1112.oldJar.isFile, "옛 jar 가 없어 건너뜀: ${V1112.oldJar}")

    /** 옛 jar 가 정말 b305c71 인가 — 그 커밋의 골든 taper-b-flip-outer3 진입(`G0X52.Z20.` `Z-20.` `G1X50.F.1`)이 나와야 한다 */
    @Test
    fun oldJarIsB305() {
        need()
        val i = TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, cornerR1 = 0.5, start = -20.0, flip = true)
        val o = V1112.oldTaper(V1112.Case("outer3", false, 0.4, 1.0, single = i))
        assertEquals(listOf("G41", "G0X52.Z20.", "Z-20.", "G1X50.F.1"), o.nc4x!!.take(4), "${o.nc4x}")
        val n = TaperCalc.calc(i).solution!!.ncG41G42
        assertEquals(listOf("G41", "G0X53.6Z20.", "Z-18.6", "G1Z-20.F.1", "X50."), n.take(5), "$n")
    }

    /**
     * 뒤집지 않은 것 · 화면 역방향(거울, 뒤집기 포함) · 계산기 역방향(뒤집기 포함): G41/G42 는 글자 하나 다르지 않고, 직접 보정은 내경 바닥 X0 끝과
     * 최종 확인 뒤 고침(2026-11-13 — 시뮬레이터로 옛 글보다 나쁘지 않은 것, 그 탓의 ENTRY_PAST_AXIS · 새 BAD_LEG 포함)만
     */
    @Test
    fun unchangedOutsideFlip() {
        need()
        val diffs = mutableListOf<String>()
        val axisDiffs = mutableListOf<String>()
        var n = 0
        var solved = 0
        var axis = 0
        var fixed = 0
        for ((flip, reverse, approach) in listOf(Triple(false, false, true), Triple(false, false, false), Triple(true, false, false), Triple(false, true, true), Triple(true, true, true))) {
            for (c in V1112.grid(flip, reverse, approach)) {
                n++
                val res = c.calc()
                val o = V1112.oldTaper(c)
                val s = res.solution
                if (s != null) solved++
                val ax = s != null && V1112.axisEnd(s, c.inner)
                if (ax) axis++
                if (ax && s.ncG40 != o.nc40) axisDiffs += "G40 ${c.tag}\n 옛 ${o.nc40}\n 새 ${s.ncG40}"
                val (bad, fx) = V1113.vsOld(c, res, o) { V1112.axisEnd(it, c.inner) }
                if (fx) fixed++
                bad.forEach { diffs += "$it · ${c.tag}" }
            }
        }
        V1112.log(
            "unchangedOutsideFlip",
            "회귀(뒤집기 밖): 사례 $n · 풀림 $solved · 내경 바닥 X0 끝 $axis · 그 직접 보정 바뀜 ${axisDiffs.size} · 고침(2026-11-13)으로 G40 바뀜 $fixed · 다른 것 ${diffs.size}",
        )
        axisDiffs.take(3).forEach { V1112.log("unchangedOutsideFlip", it) }
        diffs.take(20).forEach { V1112.log("unchangedOutsideFlip", it) }
        assertEquals(0, diffs.size, diffs.take(5).joinToString("\n\n"))
    }

    /**
     * 뒤집은 것(화면 정방향 — 고침 대상): 직접 보정은 그대로, G41/G42 는 첫 깎는 점 뒤가 그대로(모달을 풀어 견줌)이고 회사 흐름을 지킨다.
     * 시뮬레이터로 둘 다 돌려 새로 난 알람 · 파고듦 · 덜 깎임이 없는지도 본다
     */
    @Test
    fun flipOnlyEntryChanged() {
        need()
        // 문제 꼴(종류 · 내외경 · 형상 · 경고)마다 수와 예 하나
        val bad = sortedMapOf<String, Pair<Int, String>>()
        val notes = sortedMapOf<String, MutableList<String>>()
        var fails = 0
        var n = 0
        var changed = 0
        var axis = 0
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) { stat[k] = (stat[k] ?: 0) + 1 }
        for (c in V1112.grid(flip = true)) {
            val res = c.calc()
            val o = V1112.oldTaper(c)
            val s = res.solution
            val shape = (if (c.inner) "내경 " else "외경 ") + (if (c.single != null) "형상 ${c.single.type}" else "2중")
            fun fail(kind: String, ex: String) {
                fails++
                val key = "$kind | $shape | 옛 ${o.warnings} 새 ${res.warnings}"
                bad[key] = ((bad[key]?.first ?: 0) + 1) to (bad[key]?.second ?: "${c.tag}\n   옛 ${o.nc4x}\n   새 ${s?.ncG41G42}$ex")
            }
            if ((s == null) != (o.nc4x == null)) {
                // 최종 확인 뒤 고침(2026-11-13): 노즈 기준 막힌 시작 · 끝에서 노즈가 짧은 요소에 못 닿아 새로 BAD_LEG(옛 글이 막힌 벽을 판 것만)
                if (V1113.newBadLegOk(c, res, o)) bump("새 BAD_LEG(옛 글은 막힌 벽을 팜)") else fail("풀림 다름", "")
                continue
            }
            if (s == null) continue
            n++
            val ax = V1112.axisEnd(s, c.inner)
            if (ax) axis++
            // 직접 보정은 최종 확인 뒤 고침(2026-11-13)으로 바뀔 수 있다 — 시뮬레이터로 옛 글보다 나쁘지 않은 것만
            if (s.ncG40 != o.nc40 && !ax) {
                val worse = V1113.g40Worse(s, c.inner, c.r, o.nc40!!)
                if (worse != null) fail("G40 나빠짐 $worse", "\n   옛 G40 ${o.nc40}\n   새 G40 ${s.ncG40}") else bump("G40 바뀜(고침 2026-11-13 — 나빠지지 않음)")
            }
            val wOld = o.warnings.toSet() - "ENTRY_NOT_FOUND"
            val wNew = res.warnings.map { it.name }.toSet() - "ENTRY_NOT_FOUND"
            if (wOld != wNew) notes.getOrPut("경고 바뀜 | $shape | 옛 ${o.warnings} 새 ${res.warnings}") { mutableListOf() }.add("${c.tag} / 옛 ${o.nc4x} / 새 ${s.ncG41G42}")
            if ("ENTRY_NOT_FOUND" in o.warnings && TaperWarning.ENTRY_NOT_FOUND !in res.warnings) bump("ENTRY_NOT_FOUND 없어짐")
            if ("ENTRY_NOT_FOUND" !in o.warnings && TaperWarning.ENTRY_NOT_FOUND in res.warnings) fail("ENTRY_NOT_FOUND 새로 남", "")
            val nNew = s.ncG41G42
            val nOld = o.nc4x!!
            if (nNew == nOld) continue
            changed++
            V1112.companyFlow(nNew)?.let { fail("회사 흐름 $it", "") }
            val segs = s.drawing.segs
            val iOld = nOld.indexOfFirst { it.endsWith("F.1") }
            val pOld = V1112.parse(nOld)
            val pNew = V1112.parse(nNew)
            // 첫 깎는 점 = 옛 글의 F.1 줄 끝(옛 꼴은 그 줄이 첫 깎는 점에서 끝난다)
            val startD = pOld[iOld].p
            val iNew = (nNew.indexOfFirst { it.endsWith("F.1") } until nNew.size).firstOrNull { (pNew[it].p - startD).length < 1e-4 }
            if (iNew == null) {
                fail("첫 깎는 점이 새 글에 없음", "")
                continue
            }
            // 진입 G1 줄 수: F.1 줄 ~ 첫 깎는 점 줄 = 1 또는 2
            val leads = iNew - nNew.indexOfFirst { it.endsWith("F.1") } + 1
            bump("진입 이송 줄 $leads")
            if (leads > 2) fail("진입 이송 줄 $leads", "")
            val tailOld = pOld.drop(iOld + 1).map { it.spelled() + (it.f?.let { f -> " F$f" } ?: "") }
            val tailNew = pNew.drop(iNew + 1).map { it.spelled() + (it.f?.let { f -> " F$f" } ?: "") }
            if (tailOld != tailNew || pNew[iNew].g != 1) fail("첫 깎는 점 뒤가 다름", "")
            // 시뮬레이터: 알람 · 파고듦 · 덜 깎임(옛 글보다 나빠졌나)
            val tip = s.tip
            val mat = segs + segs.map(V1112::mirror)
            val k0 = segs.indexOfFirst { (it.start - startD).length < 1e-3 }
            fun look(nc: List<String>): Triple<String?, Double, Double> {
                val run = V1112.sim(nc, c.r, tip)
                val stop = run.stop?.let { "${it.code} ${it.line - V1112.pre.size}줄" }
                val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > V1112.pre.size }
                var gouge = 0.0
                for (m in moves) for (p in V1112.samples(m.nose)) gouge = max(gouge, c.r - V1112.minDist(mat, p))
                // 덜 깎임: 첫 깎는 점(옛 F.1 끝)의 요소 ~ F.3 줄 앞 점의 요소까지 도면 점(0.05 마다)에서 이송 노즈 길까지 − r
                val feeds = moves.filter { it.kind == MoveKind.FEED }.map { it.nose }
                val iEnd = nc.indexOfFirst { it.contains("F.3") } - 1
                var under = 0.0
                if (k0 >= 0 && iEnd > 0 && feeds.isNotEmpty()) {
                    val to = V1112.parse(nc)[iEnd].p
                    val k1 = segs.indexOfLast { V1112.dist(it, to) < 1e-3 }
                    if (k1 >= k0) for (k in k0..k1) for (p in V1112.samples(segs[k], 0.05)) under = max(under, feeds.minOf { V1112.dist(it, p) } - c.r)
                }
                return Triple(stop, gouge, under)
            }
            val (s0, g0, u0) = look(nOld)
            val (s1, g1, u1) = look(nNew)
            if (s1 != null && s0 == null) fail("알람 새로 남 $s1", "")
            if (s1 == null && s0 != null) bump("알람 없어짐")
            if (s1 != null && s0 != null) bump("옛 글도 새 글도 알람")
            if (s1 == null && g1 > max(g0, 0.0) + 2e-3) fail("파고듦 늘어남", " (${"%.4f".format(g0)} → ${"%.4f".format(g1)})")
            if (s1 == null && s0 == null && u1 > u0 + 2e-3) fail("덜 깎임 늘어남", " (${"%.4f".format(u0)} → ${"%.4f".format(u1)})")
            if (s1 == null && g1 > 2e-3 && g1 <= max(g0, 0.0) + 2e-3) bump("새 글 파고듦(옛 글도 그만큼)")
            if (s1 == null && s0 == null && g0 > g1 + 2e-3) bump("파고듦 줄어듦")
        }
        V1112.log("flipOnlyEntryChanged", "뒤집기 회귀: 풀린 글 $n · G41/G42 바뀜 $changed · 내경 바닥 X0 $axis · 문제 $fails")
        for ((k, v) in stat) V1112.log("flipOnlyEntryChanged", "  $k: $v")
        for ((k, v) in bad) V1112.log("flipOnlyEntryChanged", "- ${v.first}건 $k\n   ${v.second}")
        for ((k, v) in notes) V1112.log("flipOnlyEntryChanged", "(참고) ${v.size}건 $k — 예: ${v.first()}")
        assertEquals(0, fails, bad.entries.take(4).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second}" })
    }

    /**
     * 이 검사가 옛 버그를 잡는가(대조): 옛 글로 재면 T1 골든 outer3(노즈 0.4 · Z 여유 1 — c 1)의 급송 틈이 0.2 쯤 · 벽에 0 이고,
     * T2 V7-2 재현 입력의 직접 보정 끝이 맞은편을 0.11~0.16 판다(검증 보고 값). 새 글은 c 이상 · 0
     */
    @Test
    fun controlsDetectOldBugs() {
        need()
        val i = TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, cornerR1 = 0.5, start = -20.0, flip = true)
        val s = TaperCalc.calc(i).solution!!
        val segs = s.drawing.segs
        val mat = segs + segs.map(V1112::mirror)
        val walls = V1112.walls(segs, false, s.fromStart, false)
        assertEquals(2, walls.size, "막힌 시작 벽 $walls")
        val old = V1112.oldTaper(V1112.Case("outer3", false, 0.4, 1.0, single = i)).nc4x!!
        val gOld = V1112.entryGap(V1112.sim(old, 0.4, s.tip), 0.4, mat)!!
        val wOld = V1112.entryGap(V1112.sim(old, 0.4, s.tip), 0.4, walls)!!
        val gNew = V1112.entryGap(V1112.sim(s.ncG41G42, 0.4, s.tip), 0.4, mat + walls)!!
        V1112.log("controlsDetectOldBugs", "대조 T1 outer3: 옛 급송 틈(도면) $gOld · 옛 벽 틈 $wOld · 새 급송 틈 $gNew")
        assertEquals(0.2, gOld, 0.01)
        assertEquals(0.0, wOld, 0.01)
        assertTrue(gNew >= 1.0 - 2e-3, "새 급송 틈 $gNew")
        val v72 = TaperInput(
            inner = true, type = 2, noseR = 0.8, x1 = 20.324, z1 = -2.0, x2 = 9.23, z2 = -2.504, zClear = 5.0, cornerR2 = 0.4,
            start = 29.082, end = 0.0, arc = true, arcR = 50.771, arcConvex = true,
        )
        for (arc in listOf(true, false)) {
            val vi = v72.copy(arc = arc)
            val vs = TaperCalc.calc(vi).solution!!
            val vm = vs.drawing.segs + vs.drawing.segs.map(V1112::mirror)
            val o40 = V1112.direct(V1112.oldTaper(V1112.Case("v72", true, 0.8, 5.0, single = vi)).nc40!!)
            val n40 = V1112.direct(vs.ncG40)
            val go = V1112.gouge(V1112.sim(o40, 0.8, vs.tip), o40, 0.8, vm)
            val gn = V1112.gouge(V1112.sim(n40, 0.8, vs.tip), n40, 0.8, vm)
            V1112.log("controlsDetectOldBugs", "대조 T2 V7-2 원호 $arc: 옛 직접 보정 파고듦 $go · 새 $gn / 옛 $o40 / 새 $n40")
            assertTrue(go.first in 0.09..0.2, "옛 파고듦 $go")
            assertTrue(gn.first <= 2e-3, "새 파고듦 $gn")
        }
    }

    /**
     * 라운드(공용 CompFrame 을 같이 씀): 무작위 외경 · 내경 · 뒤집기 · 직접 · 기계 보정 글이 옛것과 글자 하나 다르지 않다 — 뒤집은 직접 보정의 접근 급송만
     * 최종 확인 뒤 고침(F5 2026-11-13 — 노즈 기준 접근 줄)으로 달라질 수 있다(첫 G1 부터 같고 급송이 더 파지 않음 — `V1113.roundEntryOnly`)
     */
    @Test
    fun roundUnchanged() {
        need()
        val rnd = Random(20261113)
        val g = GeometricVerifyTest()
        val diffs = mutableListOf<String>()
        var n = 0
        repeat(1500) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val base = g.randomRound(type, rnd, inner).input
            for (inp in listOf(base, base.copy(flip = true))) for (m in listOf(false, true)) {
                val res = if (m) RoundCalc.machine(inp) else RoundCalc.calc(inp)
                val (o, w) = V1112.oldRound(inp, m)
                n++
                val wSame = res.warnings.map { it.name } == w
                if (inp.flip && !m && wSame && res.solution?.nc != o && V1113.roundEntryOnly(res.solution, o, inp.noseR, inp.inner)) continue
                if (res.solution?.nc != o || !wSame) diffs += "$inp ${if (m) "기계" else "직접"}\n 옛 $o $w\n 새 ${res.solution?.nc} ${res.warnings}"
            }
        }
        V1112.log("roundUnchanged", "라운드 회귀: 글 $n · 다름 ${diffs.size}")
        assertEquals(0, diffs.size, diffs.take(4).joinToString("\n\n"))
    }
}

/** T1: 뒤집은(화면 정방향) 기계 보정 글을 시뮬레이터로 — 진입 급송 노즈 틈 · 모든 이동의 파고듦 · 알람 · 회사 흐름 */
class Verify1112TaperFlipSimTest {
    /** 글 하나의 잰 값. [stop] = 멈춤(코드 · 줄 · 진입에서인가), [gap] = 진입 급송 노즈 틈, [feedIn] = 첫 깎는 점까지 진입 이송의 노즈 틈, [gouge] = 모든 이동 파고듦 */
    private class M(val stop: String?, val stopInEntry: Boolean, val gap: Double, val feedIn: Double, val gouge: Double, val gougeAt: String)

    private fun measure(nc: List<String>, c: V1112.Case, s: TaperSolution, walls: List<Seg>): M {
        val segs = s.drawing.segs
        val run = V1112.sim(nc, c.r, s.tip)
        val iCut = V1112.firstCutLine(nc, segs, s.fromStart)
        run.stop?.let {
            val at = it.line - V1112.pre.size - 1
            return M("${it.code} ${at + 1}줄 '${nc.getOrNull(at)}'", iCut >= 0 && at <= iCut, Double.NaN, Double.NaN, Double.NaN, "")
        }
        val mat = segs + segs.map(V1112::mirror)
        val withWalls = mat + walls
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > V1112.pre.size }
        val firstFeed = moves.indexOfFirst { it.kind == MoveKind.FEED }
        val gap = V1112.entryGap(run, c.r, withWalls)!!
        // 첫 깎는 점까지의 진입 이송(새 G1 줄): 도면 · 벽을 파지 않는다
        var feedIn = Double.MAX_VALUE
        for (m in moves.drop(firstFeed).filter { it.line - V1112.pre.size - 1 <= iCut }) for (p in V1112.samples(m.nose)) feedIn = min(feedIn, V1112.minDist(withWalls, p) - c.r)
        val (gouge, at) = V1112.gouge(run, nc, c.r, mat)
        return M(null, false, gap, feedIn, gouge, at)
    }

    @Test
    fun flippedMachineEntryGrid() {
        val cat = sortedMapOf<String, Int>()
        fun bump(k: String) { cat[k] = (cat[k] ?: 0) + 1 }
        // 문제 꼴(종류 · 내외경 · 형상 · 경고 · 옛 글도 그런가)마다 수와 가장 나쁜 예
        val bad = sortedMapOf<String, Pair<Int, Pair<Double, String>>>()
        val mid = sortedMapOf<String, Pair<Int, Pair<Double, String>>>()
        val oldOk = V1112.oldJar.isFile
        var fails = 0
        var runs = 0
        var least = Double.MAX_VALUE
        var leastAt = ""
        var leastRatio = Double.MAX_VALUE
        var leastRatioAt = ""
        for (c in V1112.grid(flip = true)) {
            val res = c.calc()
            val s = res.solution ?: continue
            val segs = s.drawing.segs
            val shapeAlarm = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings
            val nc = s.ncG41G42
            // 짧은 막힌 시작을 뺄 자리가 없어 한 직선으로 들어오는 꼴(앱 경고 SHORT_END_PAST — 노즈가 시작 좌표 너머로 간다고 알림)은 벽을 빼고 잰다
            val past = TaperWarning.SHORT_END_PAST in res.warnings
            val walls = if (past) emptyList() else V1112.walls(segs, c.inner, s.fromStart, false)
            val m = measure(nc, c, s, walls)
            val old by lazy { if (oldOk) V1112.oldTaper(c).nc4x?.let { measure(it, c, s, walls) to it } else null }
            val shape = (if (c.inner) "내경 " else "외경 ") + (if (c.single != null) "형상 ${c.single.type}" + (if (c.single.arc) (if (c.single.arcConvex) " 볼록" else " 오목") else "") else "2중 ${c.double!!.let { "${it.x1}/${it.x2}/${it.x3}" }}")
            fun fail(kind: String, value: Double, oldText: String) {
                fails++
                val key = "$kind | $shape | ${res.warnings.sorted()} | $oldText"
                val prev = bad[key]
                val ex = "$value · ${c.tag}\n   새 $nc" + (old?.let { "\n   옛 ${it.second}" } ?: "")
                bad[key] = ((prev?.first ?: 0) + 1) to (if (prev == null || value < prev.second.first) value to ex else prev.second)
            }
            V1112.companyFlow(nc)?.let { fail("회사 흐름 $it", 0.0, "") }
            if (m.stop != null) {
                if (shapeAlarm) bump("형상 알람 꼴(앱 경고) — 멈춤") else {
                    bump(if (m.stopInEntry) "진입에서 멈춤" else "형상 · 빼기에서 멈춤")
                    val o = old?.first
                    // 형상 · 빼기에서 멈추는데 옛 글도 같으면(진입과 상관없는 기존 꼴 — 앱 경고 AGAINST_TOOL 인 2중 테이퍼 등) 세기만 한다
                    if (!m.stopInEntry && o != null && o.stop != null) bump("형상 · 빼기에서 멈춤 — 옛 글도 멈춤 ${res.warnings}")
                    else fail(if (m.stopInEntry) "진입에서 멈춤" else "형상 · 빼기에서 멈춤", 0.0, if (o == null) "옛 없음" else if (o.stop != null) "옛 글도 멈춤" else "새로 멈춤")
                }
                continue
            }
            runs++
            if (past) bump("SHORT_END_PAST(벽 빼고 잼)")
            val gap = m.gap
            if (gap < least) { least = gap; leastAt = "${c.tag} ${nc.take(6)}" }
            if (gap / c.c < leastRatio) { leastRatio = gap / c.c; leastRatioAt = "틈 ${"%.3f".format(gap)} c ${c.c} ${c.tag} ${nc.take(6)}" }
            bump(
                (if (c.inner) "내경 " else "외경 ") + when {
                    gap < -2e-3 -> "급송 파고듦"
                    gap < 2e-3 -> "급송 닿음(틈 0)"
                    gap >= c.c - 2e-3 -> "c 이상"
                    gap >= c.c / 2 - 2e-3 -> "c/2 이상"
                    gap >= c.c / 4 - 2e-3 -> "c/4 이상"
                    else -> "c/4 미만"
                },
            )
            if (gap < c.c - 2e-3 && gap >= c.c / 4 - 2e-3) {
                val k = "${if (gap >= c.c / 2 - 2e-3) "c/2 이상" else "c/4 이상"} | $shape | Z 여유 ${c.c.takeIf { it > c.r } ?: "≤노즈"}"
                val prev = mid[k]
                if (prev == null || gap / c.c < prev.second.first) mid[k] = ((prev?.first ?: 0) + 1) to ((gap / c.c) to "${c.tag} / 새 $nc" + (old?.let { " / 옛 ${it.second}" } ?: ""))
                else mid[k] = (prev.first + 1) to prev.second
            }
            fun oldTag(v: (M) -> Double): String = old?.first?.let { o -> if (o.stop != null) "옛 글 멈춤" else "옛 ${"%.3f".format(v(o))}" } ?: "옛 없음"
            if (gap < c.c / 4 - 2e-3) fail(if (shapeAlarm) "급송 틈 < c/4(형상 알람 꼴)" else "급송 틈 < c/4", gap, oldTag { it.gap })
            if (m.feedIn < -2e-3) fail("진입 이송이 도면 · 벽을 팜", m.feedIn, oldTag { it.feedIn })
            if (m.gouge > 2e-3) fail("파고듦 ${m.gougeAt.substringBefore(" ")}", -m.gouge, oldTag { -it.gouge })
        }
        V1112.log("flippedMachineEntryGrid", "뒤집은 기계 보정 격자: 돌린 글 $runs · $cat")
        V1112.log("flippedMachineEntryGrid", "  가장 작은 급송 틈 ${"%.4f".format(least)}: $leastAt")
        V1112.log("flippedMachineEntryGrid", "  가장 작은 틈/c ${"%.3f".format(leastRatio)}: $leastRatioAt")
        V1112.log("flippedMachineEntryGrid", "문제 꼴 ${bad.size} · 문제 $fails (값 = 틈 · 파고듦은 −깊이, 가장 나쁜 예):")
        for ((k, v) in bad) V1112.log("flippedMachineEntryGrid", "- ${v.first}건 $k\n   ${v.second.second}")
        for ((k, v) in mid) V1112.log("flippedMachineEntryGrid", "(참고 — c 를 못 지킨 꼴) ${v.first}건 $k · 가장 작은 틈/c ${"%.3f".format(v.second.first)}: ${v.second.second}")
        assertTrue(runs > 5000, "돌린 글 $runs")
        assertEquals(0, fails, bad.entries.take(4).joinToString("\n\n") { "${it.value.first}건 ${it.key}\n${it.value.second.second}" })
    }
}


/** T2: 내경 바닥 단면 X0 끝(테이퍼) · 라운드 내경(막힌 바닥 · 돔 · 원뿔 바닥)의 끝 후퇴 — 맞은편 파고듦 · 구멍 바닥 쪽 G0 */
class Verify1112TaperAxisEndTest {
    /**
     * 글 하나를 돌려 (멈춤, 가장 깊은 파고듦과 그 줄, 첫 이송 뒤 −Z(구멍 바닥 · 척 쪽)로 가는 급송 줄). 소재 = 도면 + 축 거울
     */
    private fun look(nc: List<String>, r: Double, tip: Int, segs: List<Seg>): Triple<String?, Pair<Double, String>, String?> {
        val run = V1112.sim(nc, r, tip)
        run.stop?.let { return Triple("${it.code} ${it.line - V1112.pre.size}줄 '${nc.getOrNull(it.line - V1112.pre.size - 1)}'", 0.0 to "", null) }
        val mat = segs + segs.map(V1112::mirror)
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > V1112.pre.size }
        var worst = 0.0
        var at = ""
        for (m in moves) {
            val d = V1112.samples(m.nose).maxOf { r - V1112.minDist(mat, it) }
            if (d > worst) {
                worst = d
                at = "${m.kind} '${nc.getOrNull(m.line - V1112.pre.size - 1)}'"
            }
        }
        val firstFeed = moves.indexOfFirst { it.kind == MoveKind.FEED }
        val back = moves.drop(firstFeed).firstOrNull {
            it.kind == MoveKind.RAPID && it.src != StepSrc.SHIFT_START && it.src != StepSrc.SHIFT_CANCEL && it.tip.end.z < it.tip.start.z - 1e-6
        }?.let { "'${nc.getOrNull(it.line - V1112.pre.size - 1)}'" }
        return Triple(null, worst to at, back)
    }

    /** 검증 V7-2 재현 입력(그대로)과 같은 점 직선 — 직접 · 기계 보정 끝이 맞은편을 파지 않는다 */
    @Test
    fun v72Repro() {
        val base = TaperInput(
            inner = true, type = 2, noseR = 0.8, x1 = 20.324, z1 = -2.0, x2 = 9.23, z2 = -2.504, zClear = 5.0, cornerR2 = 0.4,
            start = 29.082, end = 0.0, arc = true, arcR = 50.771, arcConvex = true,
        )
        val fails = mutableListOf<String>()
        for (i in listOf(base, base.copy(arc = false), base.copy(arc = false, cornerR2 = 0.0), base.copy(zClear = 1.0), base.copy(tip = 8), base.copy(tip = 3))) {
            val res = TaperCalc.calc(i)
            val s = res.solution
            if (s == null) {
                fails += "안 풀림 $i ${res.warnings}"
                continue
            }
            for (m in listOf(false, true)) {
                if (m && TaperWarning.CONCAVE_R_LE_NOSE in res.warnings) continue
                val nc = if (m) s.ncG41G42 else V1112.direct(s.ncG40)
                val (stop, g, back) = look(nc, i.noseR, s.tip, s.drawing.segs)
                if (stop != null || g.first > 2e-3 || back != null) fails += "$i ${if (m) "기계" else "직접"}: 멈춤 $stop · 파고듦 ${"%.4f".format(g.first)} ${g.second} · 바닥 쪽 G0 $back\n $nc"
            }
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 격자: 내경 형상 2 · 4(바닥 단면 X0 끝) × 직선 · 원호 볼록 · 오목 × 노즈 0.2~1.2 × Z 여유 0 · 1 · 5 × 바닥 Ø 2 · 6 · 12 · 20 × 입구 Ø 바닥 + 4 · 14 ×
     * 깊이 2 · 6 × 모서리 R 0 · 0.5 × 팁 표준 · 0 · 8 · 3 (뒤집지 않음 — 바닥이 깎아 끝나는 쪽). 직접 · 기계 보정 모두
     */
    @Test
    fun innerAxisEndGrid() {
        val fails = mutableListOf<String>()
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) { stat[k] = (stat[k] ?: 0) + 1 }
        var worst = 0.0
        val worstBy = sortedMapOf<String, Pair<Double, String>>()
        for (type in listOf(2, 4)) for (kind in 0..2) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0))
            for (bottom in listOf(2.0, 6.0, 12.0, 20.0)) for (open in listOf(4.0, 14.0)) for (depth in listOf(2.0, 6.0)) for (cr in listOf(0.0, 0.5))
                for (tip in listOf(null, 0, 8, 3)) {
                    val x1 = bottom + open
                    val arcR = if (kind > 0) TaperCalc.arcMinR(x1, 0.0, bottom, -depth)?.let { ceil(it * 2 * 1000) / 1000 } else null
                    val start = if (type == 2) x1 + 6.0 else 3.0
                    val i = TaperInput(true, type, false, r, tip, x1, 0.0, bottom, -depth, null, zc, cr, cr, start = start, end = 0.0, arc = kind > 0, arcR = arcR, arcConvex = kind == 1)
                    val res = TaperCalc.calc(i)
                    val s = res.solution ?: continue
                    if (!V1112.axisEnd(s, true)) { bump("X0 끝 아님"); continue }
                    val alarmShape = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings
                    for (m in listOf(false, true)) {
                        val nc = if (m) s.ncG41G42 else V1112.direct(s.ncG40)
                        val (stop, g, back) = look(nc, r, s.tip, s.drawing.segs)
                        val kindTag = if (m) "기계" else "직접"
                        if (stop != null) {
                            bump("$kindTag 멈춤" + if (alarmShape) "(앱 경고 꼴)" else "")
                            if (!alarmShape && !(m && bottom / 2 < CompFrame.g40Radius(r))) fails += "$i $kindTag 멈춤 $stop ${res.warnings}\n $nc"
                            continue
                        }
                        bump("$kindTag 돌림")
                        worst = max(worst, g.first)
                        if (g.first > 2e-3) {
                            // 줄 종류(급송 · 이송과 그 낱말 앞) · 팁 · 노즈 · 바닥 Ø 로 나눠 세고, 옛 jar 가 있으면 같은 입력의 옛 글 파고듦도(옛 글이 멈추면 −1)
                            val line = g.second.substringBefore(" ") + " " + g.second.substringAfter("'").take(3)
                            val old = if (V1112.oldJar.isFile) {
                                val o = V1112.oldTaper(V1112.Case("", true, r, max(zc, r), single = i))
                                val onc = if (m) o.nc4x!! else V1112.direct(o.nc40!!)
                                val orun = V1112.sim(onc, r, s.tip)
                                if (orun.stop != null) -1.0 else V1112.gouge(orun, onc, r, s.drawing.segs + s.drawing.segs.map(V1112::mirror)).first
                            } else Double.NaN
                            val key = "$kindTag 파고듦 $line 팁 ${tip ?: "표준"} 노즈 $r 바닥 Ø$bottom"
                            bump(key)
                            val w = worstBy[key]
                            if (w == null || g.first > w.first) worstBy[key] = g.first to "옛 ${"%.4f".format(old)} · $i ${res.warnings} / $nc"
                            fails += "$i $kindTag: ${g.second} 이 ${"%.4f".format(g.first)} 팜(옛 글 ${"%.4f".format(old)}) ${res.warnings}\n $nc"
                        }
                        if (back != null) {
                            bump("$kindTag 바닥 쪽 G0")
                            if (fails.size < 60) fails += "$i $kindTag: 첫 이송 뒤 바닥 쪽 G0 $back\n $nc"
                        }
                    }
                }
        V1112.log("innerAxisEndGrid", "내경 바닥 X0 끝 격자: $stat · 가장 깊은 파고듦 ${"%.4f".format(worst)}")
        for ((k, v) in worstBy) V1112.log("innerAxisEndGrid", "  $k 가장 깊음 ${"%.4f".format(v.first)}: ${v.second}")
        fails.filter { "팜(옛" !in it }.take(20).forEach { V1112.log("innerAxisEndGrid", it) }
        assertTrue((stat["직접 돌림"] ?: 0) > 1000, "$stat")
        assertEquals(emptyList(), fails, fails.take(6).joinToString("\n\n"))
    }

    /** 라운드 내경(구멍 바닥이 축까지 — 막힌 바닥 · 돔 · 원뿔): 뒤집지 않은 것 · 뒤집은 것의 직접 · 기계 보정 글 */
    @Test
    fun roundInnerAxisEnd() {
        val rnd = Random(1113)
        val g = GeometricVerifyTest()
        val fails = mutableListOf<String>()
        val stat = sortedMapOf<String, Int>()
        fun bump(k: String) { stat[k] = (stat[k] ?: 0) + 1 }
        repeat(1500) {
            val type = 2 + rnd.nextInt(5)
            val base = g.randomRound(type, rnd, true).input
            for (inp in listOf(base, base.copy(flip = true))) for (m in listOf(false, true)) {
                val res = if (m) RoundCalc.machine(inp) else RoundCalc.calc(inp)
                val s = res.solution ?: continue
                val segs = s.drawing.segs
                val axisSide = segs.last().end.x <= 1e-9 || segs.first().start.x <= 1e-9
                if (!axisSide) continue
                if (RoundWarning.CONCAVE_R_LE_NOSE in res.warnings) continue
                val nc = if (m) s.nc else V1112.direct(s.nc)
                val tag = "${if (inp.flip) "뒤집기 " else ""}${if (m) "기계" else "직접"}"
                val (stop, gg, back) = look(nc, inp.noseR, s.tip, segs)
                if (stop != null) { bump("$tag 멈춤"); continue }
                bump("$tag 돌림")
                // 기계 보정은 노즈에 비해 좁은 구멍(G40 옮김의 알려진 한계)을 따로 센다
                val minR = segs.flatMap { listOf(it.start.x, it.end.x) }.filter { it > 1e-6 }.minOrNull() ?: 0.0
                val narrow = m && minR < CompFrame.g40Radius(inp.noseR)
                // 뒤집은 직접 보정의 진입 급송 파고듦(이 고침 밖 — 라운드 직접 보정의 뒤집은 진입, 팁이 바이트와 반대면 앱 경고 AGAINST_TOOL)은 따로 센다 — 여기서 보는 것은 축 쪽 끝의 후퇴
                val entryOnly = inp.flip && !m && gg.second.startsWith("RAPID") && nc.indexOfFirst { it.startsWith("G0") }.let { k -> k >= 0 && gg.second.endsWith("'${nc[k]}'") }
                if (gg.first > 2e-3) {
                    bump("$tag 파고듦" + if (narrow) "(좁은 구멍)" else if (entryOnly) "(뒤집은 직접 보정 진입 급송 — 고침 밖 ${res.warnings})" else "")
                    if (!narrow && !entryOnly && fails.size < 30) fails += "$inp $tag: ${gg.second} 이 ${"%.4f".format(gg.first)} 팜 ${res.warnings}\n $nc"
                    if (entryOnly) V1112.log("roundInnerAxisEnd", "(고침 밖) $inp $tag: ${gg.second} 이 ${"%.4f".format(gg.first)} 팜 ${res.warnings} / $nc")
                }
                if (back != null && !inp.flip) {
                    bump("$tag 바닥 쪽 G0")
                    if (fails.size < 30) fails += "$inp $tag: 첫 이송 뒤 바닥 쪽 G0 $back\n $nc"
                }
            }
        }
        V1112.log("roundInnerAxisEnd", "라운드 내경 축 끝: $stat")
        fails.take(10).forEach { V1112.log("roundInnerAxisEnd", it) }
        assertTrue((stat["직접 돌림"] ?: 0) > 100, "$stat")
        assertEquals(emptyList(), fails, fails.take(5).joinToString("\n\n"))
    }
}
