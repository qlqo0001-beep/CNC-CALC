package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-14) — 사용자 답 "관통이면 넘어가도 됌" 반영(라운드 내경 TYPE 5 · 6 관통 구멍은 시작 · 끝 좌표 너머로).
 * 구현 쪽 시험(RoundThroughTest 등)을 쓰지 않고 여기서 따로 잰다: 시뮬레이터(형식 C)로 글을 돌려 노즈 길을 얻고, 소재(도면 + 축 거울 + 관통 구멍 연장 반직선 +
 * 입구 끝면)에서 노즈까지 거리로 파고듦 · 덜 깎임 · 급송 틈을 잰다. 옛 계산기는 출발 커밋 9387628 의 calc-jvm.jar 를 따로 읽는다.
 */
internal object V1113T {
    private val dir = File("C:/Users/qlqo1/AppData/Local/Temp/claude/D------/d07d09c9-0e23-46d6-8d20-7d841b2772ce/scratchpad/cnc-feedback/ans-round")

    fun begin(name: String) {
        if (dir.isDirectory) runCatching { File(dir, "v-$name.txt").writeText("", Charsets.UTF_8) }
    }

    fun log(name: String, line: String) {
        if (dir.isDirectory) runCatching { File(dir, "v-$name.txt").appendText(line + System.lineSeparator(), Charsets.UTF_8) }
    }

    fun f(v: Double) = "%.4f".format(v)

    fun r3(v: Double) = round(v * 1000) / 1000

    val pre = listOf("T0101", "G97S1000M03", "G99")

    fun sim(nc: List<String>, r: Double, tip: Int): SimRun =
        GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = CompStartup.C), mapOf(1 to ToolOffset(r, tip))))

    /** 직접 보정 글: 주석 줄을 빼고 F 가 없으면 첫 G1 에 F.1 */
    fun direct(nc: List<String>): List<String> {
        val l = nc.filter { !it.startsWith("(") }
        if (l.any { 'F' in it }) return l
        val g1 = l.indexOfFirst { it.startsWith("G1") }
        return l.mapIndexed { k, t -> if (k == g1) t + "F.1" else t }
    }

    // ---------------------------------------------------------------- 거리(여기서 따로)

    private fun angleIn(s: Arc, p: Pt): Boolean {
        val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
        val a = atan2(p.x - s.center.x, p.z - s.center.z)
        var d = if (s.cw) a0 - a else a - a0
        while (d < 0) d += 2 * PI
        while (d >= 2 * PI) d -= 2 * PI
        return d <= s.sweepDeg * PI / 180 + 1e-12
    }

    fun dSeg(s: Seg, p: Pt): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 < 1e-18) (p - s.start).length
            else (p - (s.start + d * (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0))).length
        }
        is Arc -> if (angleIn(s, p)) abs((p - s.center).length - (s.start - s.center).length) else min((p - s.start).length, (p - s.end).length)
    }

    fun dense(s: Seg, step: Double): List<Pt> = when (s) {
        is Line -> {
            val n = max(2, ((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val rr = (s.start - s.center).length
            val n = max(2, (abs(sw) * rr / step).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * rr }
        }
    }

    fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    private fun shifted(s: Seg, v: Pt): Seg = when (s) {
        is Line -> Line(s.start + v, s.end + v)
        is Arc -> Arc(s.start + v, s.end + v, s.center + v, s.cw)
    }

    /** 내경 TYPE 의 관통 구멍 벽(D1 직선) — 뒤집었으면 첫 요소, 아니면 마지막 */
    fun holeWall(s: RoundSolution): Line? {
        if (s.filled?.through != true) return null
        return (if (s.flip) s.drawing.segs.first() else s.drawing.segs.last()) as? Line
    }

    /** 소재: 도면 + (관통) 구멍 끝에서 Z− 로 같은 지름의 반직선 + (내경 TYPE) 입구 끝면(입구 점에서 X+) — 모두 축 거울까지 */
    fun stock(s: RoundSolution, inner: Boolean): List<Seg> {
        val segs = s.drawing.segs
        val extra = mutableListOf<Seg>()
        holeWall(s)?.let { h ->
            val end = if (h.start.z < h.end.z) h.start else h.end
            extra += Line(end, end + Pt(-300.0, 0.0))
        }
        if (inner && s.filled != null) {
            val mouth = if (s.flip) segs.last().end else segs.first().start
            extra += Line(mouth, mouth + Pt(0.0, 300.0))
        }
        val all = segs + extra
        return all + all.map(::mirror)
    }

    class Meas(
        val stop: String?,
        val stopCode: String?,
        /** 가장 깊은 파고듦(노즈 원이 소재 안으로) — 모든 이동 */
        val gouge: Double,
        val gougeAt: String,
        /** 관통 구멍 벽의 가장 큰 덜 깎임 */
        val holeUnder: Double,
        /** 도면 요소마다 가장 큰 덜 깎임 */
        val under: List<Double>,
        /** 첫 이송 앞 급송 노즈의 가장 작은 틈(소재까지 − r) */
        val rapidGap: Double?,
        val nc: List<String>,
    )

    fun measure(s: RoundSolution, nc0: List<String>, r: Double, machine: Boolean, inner: Boolean): Meas {
        val nc = if (machine) nc0 else direct(nc0)
        val run = sim(nc, r, s.tip)
        run.stop?.let { return Meas("${it.code} ${it.line - pre.size}줄", it.code.name, 0.0, "", 0.0, emptyList(), null, nc) }
        val tv = NoseComp.tipVector(s.tip, r)
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size && it.line <= pre.size + nc.size }
        val noses = moves.map { if (machine) it.nose else shifted(it.tip, tv * -1.0) }
        val st = stock(s, inner)
        fun clear(p: Pt) = st.minOf { dSeg(it, p) } - r
        var gouge = 0.0
        var at = ""
        noses.forEachIndexed { k, n ->
            // 첫 이동은 기계 원점에서 온다 — 끝점만
            val pts = if (k == 0) listOf(n.end) else dense(n, if (moves[k].kind == MoveKind.FEED) 0.02 else 0.05)
            for (p in pts) {
                val g = -clear(p)
                if (g > gouge) {
                    gouge = g
                    at = "${nc.getOrNull(moves[k].line - pre.size - 1)} 노즈 Z${f(p.z)} X${f(2 * p.x)}"
                }
            }
        }
        val feeds = noses.filterIndexed { k, _ -> moves[k].kind == MoveKind.FEED }
        fun underOf(seg: Seg) = if (feeds.isEmpty()) Double.POSITIVE_INFINITY else dense(seg, 0.02).maxOf { p -> feeds.minOf { dSeg(it, p) } - r }
        val under = s.drawing.segs.map(::underOf)
        val holeUnder = holeWall(s)?.let(::underOf) ?: 0.0
        val firstFeed = moves.indexOfFirst { it.kind == MoveKind.FEED }
        val rapidPts = (0 until max(0, firstFeed)).filter { moves[it].kind == MoveKind.RAPID }
            .flatMap { k -> if (k == 0) listOf(noses[k].end) else dense(noses[k], 0.02) }
        return Meas(null, null, gouge, at, holeUnder, under, rapidPts.minOfOrNull(::clear), nc)
    }

    /** 내경 도면에서 가장 좁은 반지름(급송이 지날 자리의 어림) */
    fun minRadius(s: RoundSolution): Double = s.drawing.segs.flatMap { dense(it, 0.05) }.minOf { it.x }

    /**
     * 회사 흐름(기계 보정): 보정 혼자 줄 → G0X…Z20. → 한 축 G0 → 첫 깎는 줄 G1 … F.1 → … → 빼는 줄 F.3 → … → G40 혼자 줄 → G0Z200.,
     * G 는 바뀔 때만(회사 표기 Fmt). 틀린 것들을 돌려준다
     */
    fun flowIssues(nc: List<String>, side: String): List<String> {
        val out = mutableListOf<String>()
        if (nc.getOrNull(0) != side) out += "첫 줄 ${nc.getOrNull(0)} ≠ $side"
        if (nc.getOrNull(1)?.let { Regex("G0X-?[0-9]*\\.[0-9]*Z20\\.").matches(it) } != true) out += "둘째 줄 ${nc.getOrNull(1)}"
        val cut = nc.indexOfFirst { it.endsWith("F.1") }
        if (cut < 3 || !nc[cut].startsWith("G1")) out += "첫 깎는 줄 $cut ${nc.getOrNull(cut)}"
        else for (k in 2 until cut) if (nc[k].contains("G") || (nc[k].contains("X") && nc[k].contains("Z"))) out += "진입 G0 이 한 축이 아님: ${nc[k]}"
        if (nc.count { it.endsWith("F.1") } != 1) out += "F.1 줄 ${nc.count { it.endsWith("F.1") }}개"
        if (nc.count { "F.3" in it } != 1) out += "F.3 줄 ${nc.count { "F.3" in it }}개"
        val f3 = nc.indexOfFirst { "F.3" in it }
        val g40 = nc.indexOf("G40")
        if (nc.takeLast(2) != listOf("G40", "G0Z200.")) out += "끝 두 줄 ${nc.takeLast(2)}"
        if (f3 < 0 || g40 < 0 || f3 > g40) out += "F.3($f3) 이 G40($g40) 뒤"
        var modal: String? = null
        for (l in nc) {
            val g = Regex("^G([0-3])(?=[XZRF]|$)").find(l)?.groupValues?.get(1)
            val moves = l.contains("X") || l.contains("Z")
            if (g != null) {
                if (g == modal) out += "G 반복: $l"
                modal = g
            } else if (moves && !l.startsWith("G") && modal == null) out += "G 없는 첫 이동: $l"
        }
        return out
    }

    // ---------------------------------------------------------------- 옛 계산기(9387628)

    val oldJar: File = File(
        System.getProperty("bmt.oldJar9387628") ?: System.getenv("BMT_OLD_JAR_9387628") ?: File(dir, "old-9387628-calc-jvm.jar").path,
    )

    fun needOld() = assumeTrue(oldJar.isFile, "옛 jar 가 없어 건너뜀: $oldJar")

    private class ChildFirst(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            if (name.startsWith("com.bmt.nosecalc.")) findLoadedClass(name) ?: findClass(name) else super.loadClass(name, resolve)
        }
    }

    private val loader: ClassLoader by lazy { ChildFirst(arrayOf(oldJar.toURI().toURL()), V1113T::class.java.classLoader) }

    fun toOld(v: Any?): Any? {
        if (v == null) return null
        val cls = v.javaClass
        if (v is Enum<*>) {
            val ec = if (cls.isEnum) cls else cls.superclass
            if (!ec.name.startsWith("com.bmt.nosecalc.")) return v
            return loader.loadClass(ec.name).enumConstants.first { (it as Enum<*>).name == v.name }
        }
        if (v is List<*>) return v.map { toOld(it) }
        if (!cls.name.startsWith("com.bmt.nosecalc.")) return v
        val n = cls.methods.count { Regex("component[0-9]+").matches(it.name) && it.parameterCount == 0 }
        val vals = (1..n).map { toOld(cls.getMethod("component$it").invoke(v)) }
        val ctor = loader.loadClass(cls.name).declaredConstructors.single {
            it.parameterCount == n && it.parameterTypes.none { t -> t.name.endsWith("DefaultConstructorMarker") }
        }
        return ctor.newInstance(*vals.toTypedArray())
    }

    fun oldInvoke(obj: String, method: String, vararg args: Any?): Any? {
        val cls = loader.loadClass("com.bmt.nosecalc.calc.$obj")
        val m = cls.methods.single { m ->
            m.name == method && m.parameterCount == args.size &&
                m.parameterTypes.zip(args).all { (t, a) -> a == null || t.isPrimitive || t.isInstance(a) }
        }
        return m.invoke(cls.getField("INSTANCE").get(null), *args)
    }

    fun get(o: Any?, name: String): Any? = o?.javaClass?.getMethod(name)?.invoke(o)

    class Old(val nc: List<String>?, val warnings: List<String>, val sol: Any?)

    @Suppress("UNCHECKED_CAST")
    fun oldRound(i: Any, machine: Boolean): Old {
        val res = oldInvoke("RoundCalc", if (machine) "machine" else if (i is Arc3Input) "arc3" else "calc", toOld(i))
        val s = get(res, "getSolution")
        return Old(get(s, "getNc") as List<String>?, (get(res, "getWarnings") as List<*>).map { (it as Enum<*>).name }, s)
    }

    /** 옛 보링바 검사(같은 입력 · 같은 바) — 글로 */
    fun oldPlan(i: RoundInput, bar: BoringBar, safe: Double): String? {
        val sol = oldRound(i, false).sol ?: return null
        val drawn = oldRound(i, true).sol
        return oldInvoke("HolderStop", "plan", sol, drawn, i.noseR, true, toOld(bar), safe).toString()
    }

    /** 무작위 형상(기존 생성기 — GeometricVerifyTest.randomRound) */
    private val gen by lazy { GeometricVerifyTest() }

    fun randomShape(rnd: Random, type: Int, inner: Boolean): RoundInput = gen.randomRound(type, rnd, inner).input
}

/** A. 관통(TYPE 5 · 6) × 뒤집기 · 기본 × 기계 · 직접 × 노즈R × Z 여유 × 팁 × 치수 격자 — 시뮬레이터로 잰다 */
class Verify1113ThroughSimTest {
    internal class Tally {
        var runs = 0
        var noSol = 0
        val fails = mutableListOf<String>()
        /** 찾은 것 1: 바꾸지 않은 방향 기계 보정 관통 끝의 구멍 벽 덜 깎임(되돌림) — 따로 [Verify1113ThroughFindingTest] */
        val silentFallback = mutableListOf<String>()
        /** 찾은 것 2: 뒤집은 기계 보정 진입 급송 틈 < min(c/4, 자리) — 따로 [Verify1113ThroughFindingTest] */
        val machineGap = mutableListOf<String>()
        val soft = mutableListOf<String>()
        var worstHole = 0.0
        var worstGouge = 0.0
        val known = mutableMapOf<String, Int>()
        fun know(k: String) { known[k] = (known[k] ?: 0) + 1 }
    }

    /** 한 글을 재고 틀린 것을 [t] 에 적는다. 같은 입력의 옛 글(9387628)은 문제가 보일 때만 견준다(옛 글도 같은 문제면 '전부터') */
    internal fun check(name: String, t: Tally, i: RoundInput, machine: Boolean) {
        val res = if (machine) RoundCalc.machine(i) else RoundCalc.calc(i)
        val s = res.solution
        val tag = (if (machine) "기계 " else "직접 ") + (if (i.flip) "뒤집기 " else "기본 ") + "$i ${res.warnings}"
        val haveOld = V1113T.oldJar.isFile
        val old by lazy { if (haveOld) V1113T.oldRound(i, machine) else null }
        if (s == null) {
            t.noSol++
            // 뒤집은 관통만 못 푸는가(시작 좌표 너머에서 들어오니 시작 쪽이 짧아도 풀려야 한다 — 화면의 NOSE_UNREACHABLE 뒤집기 글은 '시작 좌표를 넘지 않고' 라고 쓴다)
            val basic = if (i.flip) (if (machine) RoundCalc.machine(i.copy(flip = false)) else RoundCalc.calc(i.copy(flip = false))) else null
            if (old?.nc != null) t.fails += "$tag: 풀이 없음 — 옛 글은 있었다 ${old!!.warnings}"
            else if (basic?.solution != null) t.fails += "$tag: 뒤집기만 풀이 없음(기본 방향은 풀림 ${basic.warnings})"
            else t.know("풀이 없음 ${res.warnings}")
            return
        }
        t.runs++
        val m = V1113T.measure(s, s.nc, i.noseR, machine, true)
        V1113T.log(name, "$tag 멈춤 ${m.stop} 파고듦 ${V1113T.f(m.gouge)} 구멍벽 ${V1113T.f(m.holeUnder)} 급송틈 ${m.rapidGap?.let(V1113T::f)}")
        // 옛 글의 도면 · 팁은 새 것과 같다(형상 계산은 안 바뀜) — 새 풀이로 소재를 만든다
        val oldM by lazy { old?.nc?.let { V1113T.measure(s, it, i.noseR, machine, true) } }
        val shapeAlarm = RoundWarning.CONCAVE_R_LE_NOSE in res.warnings
        fun oldInfo(): String {
            val om = oldM
            return "\n  옛 ${old?.warnings} ${old?.nc} (멈춤 ${om?.stop} · 구멍 벽 ${om?.holeUnder?.let(V1113T::f)} · 급송 틈 ${om?.rapidGap?.let(V1113T::f)}" +
                " · 같은 글 ${old?.nc == s.nc})"
        }
        if (m.stop != null) {
            val om = oldM
            // 기계 보정 풀이는 오목 R ≤ r 경고를 안 단다 — 화면은 같은 입력의 직접 보정 경고(CONCAVE_R_LE_NOSE)를 같이 보인다
            val directAlarm = machine && RoundWarning.CONCAVE_R_LE_NOSE in RoundCalc.calc(i).warnings
            if (shapeAlarm) t.know("형상 알람 멈춤 ${m.stopCode}")
            else if (om != null && om.stopCode == m.stopCode) t.know("전부터 같은 멈춤 ${m.stopCode}")
            else if (directAlarm && m.stopCode == "COMP_ARC_SMALL") {
                t.know("직접 보정 오목 R 경고 있는 멈춤 ${m.stopCode}")
                t.soft += "$tag: 멈춤 ${m.stop} — 직접 보정 CONCAVE_R_LE_NOSE\n  ${m.nc}${oldInfo()}"
            } else t.fails += "$tag: 멈춤 ${m.stop}\n  ${m.nc}${oldInfo()}"
            return
        }
        t.worstGouge = max(t.worstGouge, m.gouge)
        if (m.gouge > 2e-3) {
            val og = oldM?.let { if (it.stop == null) it.gouge else null }
            if (og != null && og >= m.gouge - 1e-3) t.know("전부터 파고듦")
            else t.fails += "$tag: 파고듦 ${V1113T.f(m.gouge)} @ ${m.gougeAt}\n  ${m.nc}${oldInfo()}"
        }
        // 되돌림(막힌 끝 — 구석이 남음)은 THROUGH_END_BLOCKED 로 알린다(고침 2026-11-14 — 전에는 EXIT_NOT_FOUND 만, 그것도 되돌림이 못 찾을 때만)
        val fallback = machine && !i.flip && RoundWarning.THROUGH_END_BLOCKED in res.warnings
        if (fallback) {
            t.know("좁은 관통 되돌림(THROUGH_END_BLOCKED)")
            t.soft += "$tag: 되돌림 — 구멍 벽 덜 깎임 ${V1113T.f(m.holeUnder)}\n  ${m.nc}"
        } else {
            t.worstHole = max(t.worstHole, m.holeUnder)
            if (m.holeUnder > 1e-3) (if (machine && !i.flip) t.silentFallback else t.fails) += "$tag: 구멍 벽 덜 깎임 ${V1113T.f(m.holeUnder)}\n  ${m.nc}${oldInfo()}"
        }
        // 다른 요소는 옛 글보다 덜 깎이지 않아야 한다(구멍 벽 말고)
        val om = oldM
        if (om != null && om.stop == null && om.under.size == m.under.size) {
            val hw = V1113T.holeWall(s)
            val hole = if (hw == null) -1 else s.drawing.segs.indexOf<Seg>(hw)
            for (k in m.under.indices) if (k != hole && m.under[k] > om.under[k] + 1e-3 && m.under[k] > 1e-3) {
                t.fails += "$tag: 요소 $k 덜 깎임 ${V1113T.f(m.under[k])} > 옛 ${V1113T.f(om.under[k])}\n  ${m.nc}\n  옛 ${om.nc}"
            }
        }
        if (i.flip) {
            val c = max(i.zClear, i.noseR)
            val room = V1113T.minRadius(s) - i.noseR
            val gap = m.rapidGap
            val hard = min(c / 4, room)
            val want = min(c, room)
            if (gap == null || gap < hard - 2e-3) (if (machine) t.machineGap else t.fails) += "$tag: 급송 틈 ${gap?.let(V1113T::f)} < c/4·자리 ${V1113T.f(hard)} (c ${V1113T.f(c)})\n  ${m.nc}${oldInfo()}"
            else if (gap < want - 2e-3) t.soft += "$tag: 급송 틈 ${V1113T.f(gap)} < c·자리 ${V1113T.f(want)} (c ${V1113T.f(c)})"
        }
        if (machine) V1113T.flowIssues(s.nc, if (i.flip) "G42" else "G41").takeIf { it.isNotEmpty() }?.let { t.fails += "$tag: 회사 흐름 $it\n  ${s.nc}" }
    }

    internal fun report(name: String, t: Tally) {
        val head = "$name: 글 ${t.runs} · 풀이 없음 ${t.noSol} · 실패 ${t.fails.size} · 찾은 것1(되돌림) ${t.silentFallback.size} · 찾은 것2(기계 급송 틈) ${t.machineGap.size} · " +
            "참고 ${t.soft.size} · 가장 큰 구멍 벽 덜 깎임 ${V1113T.f(t.worstHole)} · 가장 큰 파고듦 ${V1113T.f(t.worstGouge)} · 아는 것 ${t.known}"
        println(head)
        V1113T.log(name, head)
        t.fails.forEach { V1113T.log(name, "실패 $it") }
        t.silentFallback.forEach { V1113T.log(name, "찾은것1 $it") }
        t.machineGap.forEach { V1113T.log(name, "찾은것2 $it") }
        t.soft.forEach { V1113T.log(name, "참고 $it") }
    }

    /** 무작위 관통 형상(TYPE 5 · 6) × 뒤집기 · 기본 × 기계 · 직접 × 노즈R 0.2 · 0.4 · 0.8 · 1.2 × Z 여유 0 · 1 · 5, 팁 표준 */
    @Test
    fun a1GridStandardTip() {
        val name = "through-a1"
        V1113T.begin(name)
        val t = Tally()
        for (shape in a1Shapes()) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0)) for (flip in listOf(true, false)) for (machine in listOf(true, false)) {
            check(name, t, shape.copy(noseR = r, zClear = zc, flip = flip), machine)
        }
        report(name, t)
        assertTrue(t.runs > 1500, "글 ${t.runs}")
        assertEquals(emptyList(), t.fails.take(6), t.fails.take(6).joinToString("\n\n"))
    }

    /** a1 의 형상: 무작위 관통 TYPE 5 · 6 각 36 + 고정 셋(예제 D · 형상 6 · 작은 구멍) */
    internal fun a1Shapes(): List<RoundInput> {
        val rnd = Random(1113_01)
        return (0 until 36).flatMap { listOf(5, 6).map { ty -> V1113T.randomShape(rnd, ty, true).copy(through = true, tip = null) } } +
            listOf(
                RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, inner = true, through = true),
                RoundInput(type = 6, mainR = 12.0, d2 = 40.0, d1 = 12.0, l1 = 4.0, l2 = 8.0, cornerR1 = 1.0, cornerR2 = 0.5, legLen = 3.0, inner = true, through = true),
                RoundInput(type = 5, mainR = 8.0, d2 = 15.0, d1 = 6.0, l1 = 2.0, cornerR1 = 0.3, cornerR2 = 0.3, legLen = 2.0, inner = true, through = true),
            )
    }

    /** 팁 0~9 × 관통 형상 × 뒤집기 · 기본 × 기계 · 직접 × 노즈R 0.4 · 1.2 × Z 여유 0 · 2 */
    @Test
    fun a2Tips() {
        val name = "through-a2"
        V1113T.begin(name)
        val t = Tally()
        val rnd = Random(1113_02)
        val shapes = (0 until 10).flatMap { listOf(5, 6).map { ty -> V1113T.randomShape(rnd, ty, true).copy(through = true) } }
        for (shape in shapes) for (tip in 0..9) for (r in listOf(0.4, 1.2)) for (zc in listOf(0.0, 2.0)) for (flip in listOf(true, false)) for (machine in listOf(true, false)) {
            check(name, t, shape.copy(noseR = r, zClear = zc, flip = flip, tip = tip), machine)
        }
        report(name, t)
        assertTrue(t.runs > 1000, "글 ${t.runs}")
        assertEquals(emptyList(), t.fails.take(6), t.fails.take(6).joinToString("\n\n"))
    }
}

/** B. 관통이 아닌 것이 9387628 과 NC · 경고가 같은가(격자), 관통은 무엇이 바뀌었나, 보링바 검사(HolderStop 변경)가 관통 아닌 내경에서 같은가 */
class Verify1113ThroughRegressionTest {
    private fun same(new: List<String>?, newW: List<RoundWarning>, old: V1113T.Old) = new == old.nc && newW.map { it.name } == old.warnings

    /** 관통 아닌 TYPE(외경 1~6 · 내경 2~6 막힘) × 뒤집기 · 기본 × 직접 · 기계 — 무작위 치수 · 노즈R · Z 여유 · 팁 */
    @Test
    fun b1NonThroughSameAsOld() {
        V1113T.needOld()
        val name = "through-b1"
        V1113T.begin(name)
        val rnd = Random(1113_11)
        var n = 0
        val diffs = mutableListOf<String>()
        repeat(1500) {
            val inner = rnd.nextBoolean()
            val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
            val base = V1113T.randomShape(rnd, type, inner).copy(through = false)
            for (flip in listOf(false, true)) for (machine in listOf(false, true)) {
                val i = base.copy(flip = flip)
                val res = if (machine) RoundCalc.machine(i) else RoundCalc.calc(i)
                val old = V1113T.oldRound(i, machine)
                n++
                if (!same(res.solution?.nc, res.warnings, old)) diffs += "${if (machine) "기계" else "직접"} $i\n  새 ${res.warnings} ${res.solution?.nc}\n  옛 ${old.warnings} ${old.nc}"
            }
        }
        V1113T.log(name, "글 $n · 다름 ${diffs.size}")
        diffs.forEach { V1113T.log(name, it) }
        println("$name: 글 $n · 다름 ${diffs.size}")
        assertEquals(emptyList(), diffs.take(5), diffs.take(5).joinToString("\n\n"))
    }

    /** 3점호(외경 · 내경, 정방향 · 뒤집기) — RoundCalc.arc 길은 through 를 안 쓴다 */
    @Test
    fun b2Arc3SameAsOld() {
        V1113T.needOld()
        val name = "through-b2"
        V1113T.begin(name)
        val rnd = Random(1113_12)
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        var n = 0
        val diffs = mutableListOf<String>()
        repeat(400) {
            val inner = rnd.nextBoolean()
            val rr = u(2.0, 30.0)
            val c = Pt(u(-20.0, 0.0), u(if (inner) rr + 4 else -rr * 0.5, if (inner) rr + 30 else rr + 20))
            val a0 = u(0.0, 2 * PI)
            val sw = u(0.3, 2.6) * (if (rnd.nextBoolean()) 1 else -1)
            val pts = listOf(0.0, 0.5, 1.0).map { c + Pt(cos(a0 + sw * it), sin(a0 + sw * it)) * rr }
            if (pts.any { it.x < 0.5 } || abs(pts[2].z - pts[0].z) < 0.5) return@repeat
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val tip = if (rnd.nextInt(4) == 0) rnd.nextInt(10) else null
            for (flip in listOf(false, true)) for (machine in listOf(false, true)) {
                val i = Arc3Input(
                    inner, r, tip, V1113T.r3(2 * pts[0].x), V1113T.r3(pts[0].z), V1113T.r3(2 * pts[1].x), V1113T.r3(pts[1].z),
                    V1113T.r3(2 * pts[2].x), V1113T.r3(pts[2].z), listOf(0.0, 0.5, 1.0, 2.0)[rnd.nextInt(4)], flip,
                )
                val res = if (machine) RoundCalc.machine(i) else RoundCalc.arc3(i)
                val old = V1113T.oldRound(i, machine)
                n++
                if (!same(res.solution?.nc, res.warnings, old)) diffs += "${if (machine) "기계" else "직접"} $i\n  새 ${res.warnings} ${res.solution?.nc}\n  옛 ${old.warnings} ${old.nc}"
            }
        }
        V1113T.log(name, "글 $n · 다름 ${diffs.size}")
        diffs.forEach { V1113T.log(name, it) }
        println("$name: 글 $n · 다름 ${diffs.size}")
        assertTrue(n > 800, "글 $n")
        assertEquals(emptyList(), diffs.take(5), diffs.take(5).joinToString("\n\n"))
    }

    /**
     * 관통 TYPE 5 · 6: 바뀐 글을 갈래(직접 · 기계 × 기본 · 뒤집기)로 센다. 직접 보정 기본 방향은 바뀌지 않아야 한다(구현 보고 — 이미 다 깎아 그대로 둠).
     * 경고가 바뀐 것도 적는다
     */
    @Test
    fun b3ThroughChanges() {
        V1113T.needOld()
        val name = "through-b3"
        V1113T.begin(name)
        val rnd = Random(1113_13)
        val changed = mutableMapOf<String, Int>()
        val total = mutableMapOf<String, Int>()
        val bad = mutableListOf<String>()
        val warnChanged = mutableListOf<String>()
        repeat(500) {
            val base = V1113T.randomShape(rnd, 5 + rnd.nextInt(2), true).copy(through = true)
            for (flip in listOf(false, true)) for (machine in listOf(false, true)) {
                val i = base.copy(flip = flip)
                val res = if (machine) RoundCalc.machine(i) else RoundCalc.calc(i)
                val old = V1113T.oldRound(i, machine)
                val k = (if (machine) "기계 " else "직접 ") + (if (flip) "뒤집기" else "기본")
                total[k] = (total[k] ?: 0) + 1
                if (!same(res.solution?.nc, res.warnings, old)) {
                    changed[k] = (changed[k] ?: 0) + 1
                    val line = "$k $i\n  새 ${res.warnings} ${res.solution?.nc}\n  옛 ${old.warnings} ${old.nc}"
                    if (res.warnings.map { it.name } != old.warnings) warnChanged += line
                    if (!machine && !flip) bad += line
                    V1113T.log(name, line)
                }
            }
        }
        val head = "$name: 갈래별 바뀜 $changed / 전체 $total · 경고 바뀜 ${warnChanged.size} · 직접 기본 바뀜 ${bad.size}"
        println(head)
        V1113T.log(name, head)
        warnChanged.forEach { V1113T.log(name, "경고 바뀜 $it") }
        assertEquals(emptyList(), bad.take(5), bad.take(5).joinToString("\n\n"))
    }

    /** 보링바 검사(HolderStop.plan — cornerWith 를 고쳤다): 관통 아닌 내경 TYPE 2~6 기본 방향은 옛 결과와 같아야 한다. 관통은 바뀐 수만 적는다 */
    @Test
    fun b4HolderSameAsOld() {
        V1113T.needOld()
        val name = "through-b4"
        V1113T.begin(name)
        val rnd = Random(1113_14)
        var n = 0
        val diffs = mutableListOf<String>()
        var throughChanged = 0
        var throughN = 0
        repeat(260) {
            val type = 2 + rnd.nextInt(5)
            val through = type >= 5 && rnd.nextInt(3) == 0
            val i = V1113T.randomShape(rnd, type, true).copy(through = through, flip = false)
            val sol = RoundCalc.calc(i).solution ?: return@repeat
            val drawn = RoundCalc.machine(i).solution
            val narrow = V1113T.minRadius(sol)
            val bars = listOf(
                BoringBar(V1113T.r3(max(2.0, 1.2 * narrow)), V1113T.r3(max(1.5, 0.95 * narrow)), InsertShape.C, 9.67, 95.0),
                BoringBar(V1113T.r3(max(2.0, 0.8 * narrow)), V1113T.r3(max(1.2, 0.7 * narrow)), InsertShape.D, 7.75, 93.0),
            )
            for (bar in bars) {
                val new = HolderStop.plan(sol, drawn, i.noseR, true, bar, 0.5).toString()
                val old = V1113T.oldPlan(i, bar, 0.5)
                if (through) {
                    throughN++
                    if (new != old) throughChanged++
                    continue
                }
                n++
                if (new != old) diffs += "$i $bar\n  새 $new\n  옛 $old"
            }
        }
        val head = "$name: 관통 아닌 검사 $n · 다름 ${diffs.size} · 관통 검사 $throughN 중 바뀜 $throughChanged"
        println(head)
        V1113T.log(name, head)
        diffs.forEach { V1113T.log(name, it) }
        assertTrue(n > 200, "검사 $n")
        assertEquals(emptyList(), diffs.take(3), diffs.take(3).joinToString("\n\n"))
    }
}

/**
 * 찾은 것(검증 2026-11-14)의 재현 — 고칠 때까지 실패한다.
 * 1. 바꾸지 않은 방향의 관통 끝(기계 보정): 노즈에 비해 좁은 구멍(D1 ≲ 5r)은 끝 좌표 너머로 빼는 길을 못 찾아(`RoundCalc.throughFrame` 의 EXIT_NOT_FOUND)
 *    전 규칙(막힌 끝)으로 되돌아가는데, 되돌린 글이 빼는 길을 찾으면 시도에서 난 EXIT_NOT_FOUND 를 버려 경고 없이 구멍 벽 끝에 (√2 − 1)·r 구석이 남는다
 *    (짧은 D1 은 SHORT_END_SKIPPED 로 그 직선을 통째로 뺀다). 화면은 그래도 "너머로 지나가 구멍 벽을 끝까지 깎은 뒤" 라고 쓴다(Verify1113ThroughScreenTest).
 *    기대: 구멍 벽 덜 깎임 ≤ 0.001 이거나, 못 하면 경고가 남는다(고침 뒤 THROUGH_END_BLOCKED — check 는 그 경고가 있는 되돌림을 참고로만 둔다).
 * 2. 뒤집은 관통 기계 보정 진입: 구멍 반지름 − r(노즈 중심을 축에 둘 때의 틈) 이 c/4 보다 작으면 급송 노즈를 제 벽에서 c/4 자리(노즈 중심이 축 너머)나
 *    전 규칙(틈 0.05)에 두어 맞은편 벽까지의 틈이 min(c/4, 자리)보다 작다. 직접 보정은 노즈 중심을 축까지만 옮겨(e.x ≥ 0) 틈 = 자리를 지킨다.
 */
class Verify1113ThroughFindingTest {
    private val sim = Verify1113ThroughSimTest()

    @Test
    fun f1BasicMachineThroughEndCutOrWarned() {
        val name = "through-f1"
        V1113T.begin(name)
        val t = Verify1113ThroughSimTest.Tally()
        val fixed = listOf(
            RoundInput(type = 5, mainR = 5.532, cornerR1 = 0.977, cornerR2 = 0.0, d1 = 4.97, d2 = 8.667, l1 = 1.994, noseR = 1.2, zClear = 2.0, legLen = 1.05, inner = true, through = true),
            RoundInput(
                type = 6, mainR = 5.036, cornerR1 = 1.74, cornerR2 = 1.344, d1 = 5.287, d2 = 26.062, l1 = 2.006, l2 = 6.018, noseR = 1.2, zClear = 1.0, legLen = 2.297,
                inner = true, through = true,
            ),
        )
        for (i in fixed) sim.check(name, t, i, machine = true)
        for (shape in sim.a1Shapes()) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0)) sim.check(name, t, shape.copy(noseR = r, zClear = zc), machine = true)
        sim.report(name, t)
        assertEquals(emptyList(), t.silentFallback.take(3), "${t.silentFallback.size} 글\n" + t.silentFallback.take(3).joinToString("\n\n"))
    }

    @Test
    fun f2FlippedMachineRapidGap() {
        val name = "through-f2"
        V1113T.begin(name)
        val t = Verify1113ThroughSimTest.Tally()
        val fixed = listOf(
            RoundInput(
                type = 5, mainR = 10.44, cornerR1 = 0.0, cornerR2 = 1.222, d1 = 4.332, d2 = 8.356, l1 = 1.898, noseR = 1.2, zClear = 5.0, legLen = 3.827,
                inner = true, through = true, flip = true,
            ),
            RoundInput(
                type = 6, mainR = 3.413, cornerR1 = 1.918, cornerR2 = 1.867, d1 = 1.921, d2 = 17.752, l1 = 1.406, l2 = 5.617, noseR = 0.4, zClear = 5.0, legLen = 2.179,
                inner = true, through = true, flip = true,
            ),
        )
        for (i in fixed) sim.check(name, t, i, machine = true)
        for (shape in sim.a1Shapes()) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0)) {
            sim.check(name, t, shape.copy(noseR = r, zClear = zc, flip = true), machine = true)
        }
        sim.report(name, t)
        assertEquals(emptyList(), t.machineGap.take(3), "${t.machineGap.size} 글\n" + t.machineGap.take(3).joinToString("\n\n"))
    }
}
