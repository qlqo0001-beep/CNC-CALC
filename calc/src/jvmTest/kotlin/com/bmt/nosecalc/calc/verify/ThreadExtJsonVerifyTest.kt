package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.DieHeadCatalog
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.InsertForm
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.PipeInput
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadInserts
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ThreadTol
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * V1 독립 검증 — 규격 자료(docs/research 의 JSON 넷)를 직접 읽어 따로 짠 모형으로 기대값을 내고 앱과 맞댄다(2026-11-06).
 *  1. unified-extra.json limits 98줄 = H28 반올림 모형(nominalDGap · 비운 칸 제외), 앱 ThreadTol 과의 차(인치), 20UN · UNEF 목록.
 *  2. pipe-threads.json 으로 피치선 직선 모형(게이지 점 + 테이퍼 1/16, 게이지 옮김 = 게이지 점의 축 방향 이동)을 짜
 *     무작위 사례(크기 × 숫/암 × 옮김 × Z)에서 끝 골 X · I · 시작 X · 경고를 맞대고, G92 글을 GSim 에 넣어 THREAD 양 끝 반경을 본다.
 *  3. die-heads.json 의 절삭 속도 · 체이서 표본 = DieHeadCatalog.
 *  4. thread-inserts.json 의 줄 = ThreadInserts.match.
 */
class ThreadExtJsonVerifyTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다")
    }

    private fun load(name: String) = Json.parseToJsonElement(findUp("docs/research/$name").readText(Charsets.UTF_8)).jsonObject

    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
    private fun JsonObject.num(k: String): Double? = get(k).num()
    private fun JsonObject.str(k: String): String? = (get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonObject.obj(k: String): JsonObject = getValue(k).jsonObject
    private fun JsonObject.arr(k: String): JsonArray = getValue(k).jsonArray

    private val inch = 25.4

    // ───────────── 1. UNEF · 20UN ─────────────

    @Test
    fun unifiedExtraLimits() {
        val root = load("unified-extra.json")
        val dIn = mutableMapOf<Pair<String, Int>, Double>()
        for (o in root.obj("series").arr("UNEF").map { it.jsonObject }) dIn[o.str("size")!! to o.num("tpi")!!.toInt()] = o.num("dIn")!!
        for (o in root.obj("series").arr("UN20").map { it.jsonObject }) dIn[o.str("size")!! to 20] = o.num("dIn")!!
        val modelMiss = mutableListOf<String>()
        val appMiss = mutableListOf<String>()
        var max4 = 0.0
        var max4At = ""
        var max3 = 0.0
        var over4 = 0
        var cells4 = 0
        val overRows = mutableSetOf<String>()
        for (row in root.arr("limits").map { it.jsonObject }) {
            val des = row.str("designation")!!
            val cls = row.str("class")!!
            val (head, series) = des.split(" ")
            val size = head.substringBeforeLast("-")
            val tpi = head.substringAfterLast("-").toInt()
            val d = dIn[size to tpi] ?: fail("$des 지름")
            val formulaOk = row.obj("formulaCheck").getValue("match").jsonPrimitive.boolean
            val m = H28Model.cells(d, tpi, series, cls, rounded = true)
            val model = mapOf(
                "majorMax" to m.majorMax, "majorMin" to m.majorMin, "pitchMax" to m.pitchMax, "pitchMin" to m.pitchMin,
                "minorMax" to m.minorMax, "minorMin" to m.minorMin, "allowance" to m.allowance, "pitchTol" to m.pitchTol,
            )
            for ((k, mv) in model) {
                val jv = row.num(k) ?: continue
                val lim = if (formulaOk) 1e-9 else 0.00021
                if (mv == null || abs(mv - jv) > lim) modelMiss += "$des-$cls $k: 표 $jv · 모형 $mv (formulaCheck $formulaOk)"
            }
            // 앱
            val spec = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
            assertEquals(series, spec.series.name, des)
            val c = ThreadClass.valueOf("UN$cls")
            val lim = ThreadTol.limits(spec, c)
            val app = if (c.internal) {
                mapOf(
                    "majorMin" to lim.rootLimit, "pitchMax" to lim.pitchDia.max, "pitchMin" to lim.pitchDia.min,
                    "minorMin" to lim.crest.min, "minorMax" to lim.crest.max, "pitchTol" to lim.pitchDia.width,
                )
            } else {
                mapOf(
                    "majorMax" to lim.crest.max, "majorMin" to lim.crest.min, "pitchMax" to lim.pitchDia.max, "pitchMin" to lim.pitchDia.min,
                    "allowance" to -lim.deviation, "pitchTol" to lim.pitchDia.width,
                )
            }
            for ((k, av) in app) {
                val jv = row.num(k) ?: continue
                val diff = abs(av / inch - jv)
                val threeDigit = k == "minorMin" || (k == "minorMax" && cls != "3B")
                if (threeDigit) {
                    max3 = max(max3, diff)
                    if (diff > 0.0006) appMiss += "$des-$cls $k: 표 $jv · 앱 ${av / inch}"
                } else {
                    cells4++
                    if (diff > max4) {
                        max4 = diff
                        max4At = "$des-$cls $k"
                    }
                    if (diff > 0.00005 + 1e-9) {
                        over4++
                        overRows += "$des-$cls"
                    }
                    if (diff > 0.00015) appMiss += "$des-$cls $k: 표 $jv · 앱 ${av / inch}"
                }
            }
        }
        println(
            "[V1 UN] 앱 − 표(인치): 4자리 칸 최대 ${"%.6f".format(max4)} ($max4At), 0.00005 넘는 칸 $over4/$cells4 (${overRows.size}줄), " +
                "3자리 칸 최대 ${"%.6f".format(max3)}",
        )
        assertTrue(modelMiss.isEmpty(), "독립 모형이 표를 재현하지 못한 칸 ${modelMiss.size}:\n" + modelMiss.joinToString("\n"))
        assertTrue(appMiss.isEmpty(), "앱 − 표가 허용(4자리 ±0.00015 · 3자리 ±0.0006 인치) 밖 ${appMiss.size}:\n" + appMiss.joinToString("\n"))
    }

    @Test
    fun unifiedExtraSeries() {
        val root = load("unified-extra.json")
        val unef = root.obj("series").arr("UNEF").map { it.jsonObject }
        val g = ThreadTable.group(UnGroup.UNEF)
        assertEquals<List<Pair<String?, Int?>>>(unef.map { it.str("size") to it.num("tpi")!!.toInt() }, g.map { it.size to it.tpi })
        for ((o, s) in unef.zip(g)) assertTrue(abs(o.num("dIn")!! * inch - s.d) < 1e-9, s.label)
        val un20 = root.obj("series").arr("UN20").map { it.jsonObject }
        assertEquals(37, un20.size)
        val g20 = ThreadTable.group(UnGroup.UN20)
        assertEquals(un20.map { it.str("size") }, g20.map { it.size })
        for ((o, s) in un20.zip(g20)) {
            assertTrue(abs(o.num("dIn")!! * inch - s.d) < 1e-9, s.label)
            assertEquals(o.str("coincidesWith") ?: "UN", s.series.name, "${s.size} 계열(designationRule)")
            assertEquals(20, s.tpi)
        }
        // designationRule.bySize 와 같은지
        val by = root.obj("designationRule").obj("bySize")
        for ((k, v) in by) {
            if (!k.contains("-20")) continue
            val size = k.substringBefore("-20")
            assertEquals(v.jsonPrimitive.content, g20.single { it.size == size }.series.name, k)
        }
        // classes: UNEF · UN20 = 2A 3A 2B 3B
        for (key in listOf("UNEF", "UN20")) {
            val want = root.obj("classes").arr(key).map { it.jsonPrimitive.content }.toSet()
            val series = if (key == "UNEF") com.bmt.nosecalc.calc.ThreadSeries.UNEF else com.bmt.nosecalc.calc.ThreadSeries.UN
            assertEquals(want, (ThreadClass.choices(series, false) + ThreadClass.choices(series, true)).map { it.label }.toSet(), key)
        }
    }

    // ───────────── 2. 관용 테이퍼 ─────────────

    /** 규격 행(따로 읽음, mm). gaugePitch = 게이지 면 피치경, gaugeLen = 숫나사 작은 끝 → 게이지 면 */
    private data class PRow(
        val kind: PipeKind, val size: String, val tpi: Double, val h: Double, val gaugePitch: Double, val gaugeLen: Double,
        val tolExt: Double, val tolInt: Double, val needExt: Double, val needInt: Double,
    ) {
        val p get() = 25.4 / tpi
    }

    private fun turns(t: String): Double = t.split(" ").sumOf { if (it.contains("/")) it.substringBefore("/").toDouble() / it.substringAfter("/").toDouble() else it.toDouble() }

    private val pipeRows: List<PRow> by lazy {
        val root = load("pipe-threads.json")
        val c = root.obj("constants")
        val hPt = c.obj("PT").obj("taperThreadProfile").num("h")!!
        assertEquals("1:16", c.obj("PT").obj("taper").str("value"))
        assertEquals("1 in 16", c.obj("NPT").obj("taper").str("value"))
        assertTrue(c.obj("NPT").obj("h").str("formula")!!.contains("0.800p"))
        val pt = root.arr("PT").map { it.jsonObject }.map { o ->
            val j = o.obj("jis")
            val tpi = o.num("tpi")!!
            val p = 25.4 / tpi
            val d = j.num("d")!!
            val iso = o["iso"] as? JsonObject
            // ISO 의 산 수(규정값), 없으면(3 1/2) JIS b · c / P 를 1/4 산으로
            val tE = iso?.str("T1halfTurns")?.let(::turns) ?: (floor(j.num("b")!! / p * 4 + 0.5) / 4)
            val tI = iso?.str("T2halfTurns")?.let(::turns) ?: (floor(j.num("c")!! / p * 4 + 0.5) / 4)
            // 기준면 유효지름: ISO Table 1 주 — 바깥지름에서 h 를 뺀다. 표 d2(3자리)와 0.0005 안인지도 본다
            val eg = d - hPt * p
            assertTrue(abs(eg - j.num("d2")!!) <= 0.0005 + 1e-9, "PT ${o.str("size")} d2")
            PRow(PipeKind.PT, o.str("size")!!, tpi, hPt * p, eg, j.num("a")!!, tE, tI, j.num("a")!! + j.num("f")!!, j.num("l")!!)
        }
        val appNpt = PipeTable.NPT.map { it.size }.toSet()
        val npt = root.arr("NPT").map { it.jsonObject }.filter { it.str("size") in appNpt }.map { o ->
            val tpi = o.num("tpi")!!
            val p = 25.4 / tpi
            val e0 = o.num("E0")!!
            val e1 = o.num("E1")!!
            val l1 = o.num("L1")!!
            val l2 = o.num("L2")!!
            // 두 점 확인: 표의 E0(작은 끝) · E1(L1) · E2(L2) 가 테이퍼 1/16 직선 위(표 5자리 ±0.00001 인치)
            assertTrue(abs((e1 - e0) - l1 / 16) <= 0.00001 + 1e-9, "NPT ${o.str("size")} E1 − E0")
            assertTrue(abs((o.num("E2")!! - e0) - l2 / 16) <= 0.00001 + 1e-9, "NPT ${o.str("size")} E2 − E0")
            PRow(PipeKind.NPT, o.str("size")!!, tpi, 0.8 * p, e1 * inch, l1 * inch, 1.0, 1.0, l2 * inch, (l1 + o.num("L3")!!) * inch)
        }
        assertEquals(16, pt.size)
        assertEquals(14, npt.size)
        pt + npt
    }

    private fun r3(v: Double) = floor(abs(v) * 1000 + 0.5 + 1e-6) / 1000 * (if (v < 0) -1 else 1)
    private fun r4(v: Double) = floor(abs(v) * 10000 + 0.5 + 1e-6) / 10000

    /** 독립 절입표(FANUC G76 규칙 — calc-design G76 절): 누적 Δd·√n, 증분 < Δdmin 이면 Δdmin, k − d 에서 자름, 정삭 m 회(깊이 k) */
    private fun schedule(k: Double, dd: Double, ddMin: Double, d: Double, m: Int): List<Double> {
        val out = mutableListOf<Double>()
        val cap = k - d
        var cum = 0.0
        var n = 1
        while (true) {
            var next = dd * sqrt(n.toDouble())
            if (next - cum < ddMin) next = cum + ddMin
            if (next >= cap - 1e-9) {
                out += cap
                break
            }
            out += next
            cum = next
            n++
        }
        repeat(m) { out += k }
        return out
    }

    private class PCase(
        val row: PRow, val inner: Boolean, val s: Double, val zRef: Double, val zStart: Double, val zEnd: Double,
        val dd: Double, val ddMin: Double, val allow: Double, val m: Int, val clear: Double, val rpm: Double?,
    )

    /** 피치선 직선 모형: 게이지 점(옮김 s 산 = 축으로 s·P 이동)을 지나는 1/16 테이퍼 */
    private fun pitchAt(c: PCase, z: Double): Double {
        val r = c.row
        return if (c.inner) {
            val zg = c.zRef - c.s * r.p // 플러그가 s 산 더 들어감 → 게이지 점이 안쪽으로
            r.gaugePitch - (zg - z) / 16
        } else {
            val zg = c.zRef - r.gaugeLen - c.s * r.p // 링 게이지에 s 산 더 들어감 → 게이지 점이 척 쪽으로
            r.gaugePitch + (zg - z) / 16
        }
    }

    private fun rootAt(c: PCase, z: Double) = if (c.inner) pitchAt(c, z) + c.row.h else pitchAt(c, z) - c.row.h
    private fun crestAt(c: PCase, z: Double) = if (c.inner) pitchAt(c, z) - c.row.h else pitchAt(c, z) + c.row.h

    private fun cases(): List<PCase> {
        val rnd = Random(20261106)
        val out = mutableListOf<PCase>()
        while (out.size < 240) {
            val row = pipeRows[rnd.nextInt(pipeRows.size)]
            val inner = rnd.nextBoolean()
            val tol = if (inner) row.tolInt else row.tolExt
            val s = when (rnd.nextInt(5)) {
                0, 1 -> 0.0
                2 -> tol
                3 -> -tol
                else -> floor(rnd.nextDouble(-tol, tol) * 100) / 100
            }
            val zRef = r3(rnd.nextDouble(-6.0, 4.0))
            val zStart = r3(zRef + rnd.nextDouble(0.0, 8.0))
            val need = (if (inner) row.needInt else row.needExt) + s * row.p
            val len = r3(rnd.nextDouble(0.6 * need, need + 10.0))
            val zEnd = r3(zRef - len)
            val dd = r3(rnd.nextDouble(0.12, 0.6))
            val ddMin = r3(rnd.nextDouble(0.02, 0.06))
            val allow = r3(rnd.nextDouble(0.005, 0.05))
            val m = rnd.nextInt(1, 4)
            var clear = r3(rnd.nextDouble(0.3, 2.0))
            val c0 = PCase(row, inner, s, zRef, zStart, zEnd, dd, ddMin, allow, m, clear, if (rnd.nextBoolean()) 500.0 else null)
            if (inner && crestAt(c0, zEnd) - 2 * clear < 1.0) clear = r3((crestAt(c0, zEnd) - 1.0) / 4)
            if (clear <= 0.0) continue
            out += PCase(row, inner, s, zRef, zStart, zEnd, dd, ddMin, allow, m, clear, c0.rpm)
        }
        // 크기마다 숫 · 암 한 번씩은 꼭(경계 옮김)
        for (row in pipeRows) for (inner in listOf(false, true)) {
            val tol = if (inner) row.tolInt else row.tolExt
            val need = (if (inner) row.needInt else row.needExt) + tol * row.p
            val c0 = PCase(row, inner, tol, 0.0, 5.0, r3(-need - 3), 0.3, 0.035, 0.02, 2, 0.5, null)
            out += if (inner && crestAt(c0, c0.zEnd) - 1.0 < 1.0) PCase(row, inner, tol, 0.0, 5.0, c0.zEnd, 0.3, 0.035, 0.02, 2, 0.2, null) else c0
        }
        return out
    }

    private fun input(c: PCase, word: Char = 'I') = PipeInput(
        inner = c.inner, spec = PipeTable.of(c.row.kind).single { it.size == c.row.size }, zRef = c.zRef, zStart = c.zStart, zEnd = c.zEnd,
        rpm = c.rpm, gaugeShift = c.s, firstCut = c.dd, minCut = c.ddMin, finishAllow = c.allow, finishCount = c.m, xClear = c.clear, taperWord = word,
    )

    private fun tag(c: PCase) =
        "${c.row.kind} ${c.row.size} ${if (c.inner) "암" else "숫"} s=${c.s} zRef=${c.zRef} Z${c.zStart}→${c.zEnd} Δd${c.dd} Δdmin${c.ddMin} d${c.allow} m${c.m} 여유${c.clear}"

    @Test
    fun pipeRandomAgainstLineModelAndSimulator() {
        val bad = mutableListOf<String>()
        val tools = mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD))
        var maxEnd = 0.0
        var maxStart = 0.0
        var maxI = 0.0
        var nSim = 0
        val all = cases()
        for ((idx, c) in all.withIndex()) {
            val t = tag(c)
            val r = PipeThreadCalc.calc(input(c))
            val sol = r.solution
            // 시작 Z ≤ 기준면은 멈춤(V3 고침 2026-11-06 — 시작 Z 에서 X 로 급송 절입하므로 기준면 위도 끝 모서리를 묾)
            if (c.zStart <= c.zRef + 1e-9) {
                if (sol != null || r.warnings != listOf(G76Warning.START_INSIDE)) bad += "$t: 시작 Z ≤ 기준면인데 ${r.warnings}"
                continue
            }
            if (sol == null) {
                bad += "$t: 풀이 없음 ${r.warnings}"
                continue
            }
            val h = c.row.h
            val k = r3(h)
            val xEnd = rootAt(c, c.zEnd)
            val iIndep = (rootAt(c, c.zStart) - xEnd) / 2
            val startX = if (c.inner) crestAt(c, c.zEnd) - 2 * c.clear else crestAt(c, c.zEnd) + 2 * c.clear
            val len = c.zRef - c.zEnd
            val need = (if (c.inner) c.row.needInt else c.row.needExt) + c.s * c.row.p
            val sched = schedule(k, c.dd, c.ddMin, c.allow, c.m)
            fun near(what: String, e: Double, a: Double, tol: Double) {
                if (abs(e - a) > tol) bad += "$t: $what 독립 $e · 앱 $a (차 ${a - e})"
            }
            near("끝 골 X", xEnd, sol.x, 1e-6)
            near("끝 Z 피치경", pitchAt(c, c.zEnd), sol.pitchAtEnd, 1e-6)
            near("기준면 산봉우리 지름", crestAt(c, c.zRef), sol.crestAtRef, 1e-6)
            near("끝 Z 산봉우리 지름", crestAt(c, c.zEnd), sol.crestAtEnd, 1e-6)
            near("게이지 면 Z(옮김 전)", if (c.inner) c.zRef else c.zRef - c.row.gaugeLen, sol.gaugeZ, 1e-9)
            near("k", h, sol.height, 0.0005 + 1e-12)
            near("I", iIndep, sol.taper, 0.0005 + 1e-9)
            near("시작 X", startX, sol.startX, 0.0005 + 1e-9)
            near("나사 길이", len, sol.threadLen, 1e-9)
            near("최소 길이", need, sol.needLen, 1e-6)
            near("리드", 25.4 / c.row.tpi, sol.lead, 1e-4)
            maxI = max(maxI, abs(iIndep - sol.taper))
            if (if (c.inner) sol.taper <= 0 else sol.taper >= 0) bad += "$t: I 부호 ${sol.taper}"
            // 경고(숫자와 함께): SHORT_THREAD ⇔ 길이 < 최소, ONE_ROUGH_PASS ⇔ Δd ≥ k − d,
            // START_NEAR ⇔ 시작 Z − 기준면 < 2 × 피치(25.4/산수 — V3 고침 2026-11-06: 급송 절입 · 나사 시작 가속 구간이 끝면에 걸림)
            val wantW = buildSet {
                if (len < need - 1e-9) add(G76Warning.SHORT_THREAD)
                if (c.dd >= k - c.allow - 1e-9) add(G76Warning.ONE_ROUGH_PASS)
                if (c.zStart - c.zRef < 2 * 25.4 / c.row.tpi - 1e-9) add(G76Warning.START_NEAR)
            }
            if (wantW != r.warnings.toSet()) bad += "$t: 경고 독립 $wantW · 앱 ${r.warnings}"
            if (sched.size != sol.passes.size) bad += "$t: 회차 수 독립 ${sched.size} · 앱 ${sol.passes.size}"
            // 글 꼴: 첫 G92 줄 X Z I F, 둘째부터 X 만, M23 … M24
            val g = sol.g92
            val i92 = g.indexOfFirst { it.startsWith("G92") }
            val m92 = Regex("""G92X(-?[0-9.]+)Z(-?[0-9.]+)I(-?[0-9.]+)F([0-9.]+)""").matchEntire(g[i92])
            if (m92 == null) {
                bad += "$t: 첫 G92 줄 꼴 ${g[i92]}"
                continue
            }
            val fText = m92.groupValues[4]
            if (abs(fText.toDouble() - r4(25.4 / c.row.tpi)) > 1e-9) bad += "$t: F $fText · 넷째 자리 ${r4(25.4 / c.row.tpi)}"
            if (c.row.tpi == 11.5 && fText != "2.2087") bad += "$t: 11.5산 F $fText"
            if (abs(m92.groupValues[2].toDouble() - c.zEnd) > 1e-9) bad += "$t: G92 Z ${m92.groupValues[2]}"
            if (g[i92 - 1] != "M23" || g.last() != "G0Z200." || g[g.size - 2] != "M24") bad += "$t: M23 · M24 · G0Z200. 자리 $g"
            if (g.subList(i92 + 1, g.size - 2).any { !Regex("""X-?[0-9.]+""").matches(it) }) bad += "$t: 둘째 줄부터 X 만 아님 $g"
            // GSim (모두 — 240 + 60)
            val run = GSim.run(SimInput((listOf("T0101", "G97S500M03") + g + "M30").joinToString("\n"), MachineSetup(), tools))
            nSim++
            if (run.stop != null || run.issues.isNotEmpty()) {
                bad += "$t: GSim 멈춤 ${run.stop?.code} · 문제 ${run.issues.map { it.code }}"
                continue
            }
            val cuts = run.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.THREAD }.groupBy { it.line to it.pass }.values.map { it.single() }
            if (cuts.size != sched.size + 1) {
                bad += "$t: GSim 깎는 회 ${cuts.size} · 독립 ${sched.size} + 1(M24)"
                continue
            }
            for ((j, mv) in cuts.withIndex()) {
                val cum = sched[minOf(j, sched.size - 1)]
                val off = (k - cum) * (if (c.inner) -1 else 1) // 반경 — 숫나사는 바깥으로, 암나사는 축 쪽으로 덜 깎음
                val eEnd = xEnd / 2 + off
                val eStart = rootAt(c, c.zStart) / 2 + off
                val tip = mv.tip as Line
                val dEnd = abs(tip.end.x - eEnd)
                val dStart = abs(tip.start.x - eStart)
                maxEnd = max(maxEnd, dEnd)
                maxStart = max(maxStart, dStart)
                if (dEnd > 0.0005 + 1e-9) bad += "$t 회 ${j + 1}: 끝 반경 GSim ${tip.end.x} · 독립 $eEnd (차 ${tip.end.x - eEnd})"
                // 시작 반경 = 끝 반경 + I — I 지령값 반올림(≤ 0.0005)만큼 더 허용
                if (dStart > 0.0005 + abs(sol.taper - iIndep) + 1e-9) bad += "$t 회 ${j + 1}: 시작 반경 GSim ${tip.start.x} · 독립 $eStart (차 ${tip.start.x - eStart})"
                if (abs(tip.end.z - c.zEnd) > 1e-9 || abs(tip.start.z - c.zStart) > 1e-9) bad += "$t 회 ${j + 1}: Z ${tip.start.z} → ${tip.end.z}"
                if (abs((mv.feed ?: 0.0) - fText.toDouble()) > 1e-9) bad += "$t 회 ${j + 1}: 리드 ${mv.feed}"
            }
            // 다섯에 하나: R 꼴 글의 이동 = I 꼴
            if (idx % 5 == 0) {
                val sr = PipeThreadCalc.calc(input(c, 'R')).solution!!
                val rr = GSim.run(SimInput((listOf("T0101", "G97S500M03") + sr.g92 + "M30").joinToString("\n"), MachineSetup(), tools))
                val a = run.steps.filterIsInstance<MoveStep>().map { it.kind to it.tip }
                val b = rr.steps.filterIsInstance<MoveStep>().map { it.kind to it.tip }
                if (a != b || rr.stop != null) bad += "$t: R 꼴 이동이 I 꼴과 다름"
                if (sr.g92.count { it.contains("R") } != 1) bad += "$t: R 낱말 줄 수"
            }
        }
        println("[V1 관용] 사례 ${all.size} · GSim $nSim · 끝 반경 최대차 ${"%.6f".format(maxEnd)} · 시작 반경 최대차 ${"%.6f".format(maxStart)} · I 최대차 ${"%.6f".format(maxI)}")
        assertTrue(all.size >= 200)
        assertTrue(bad.isEmpty(), "독립 모형과 다른 곳 ${bad.size}:\n" + bad.take(60).joinToString("\n"))
    }

    /** 게이지 옮김이 공차(PT 숫 ±1 · 1½산 · 암 ±1¼ · 1½산, NPT ±1산) 밖이면 멈춤(BAD_INPUT), 안(경계 포함)이면 풀림 */
    @Test
    fun pipeGaugeShiftTolerance() {
        val bad = mutableListOf<String>()
        for (row in pipeRows) for (inner in listOf(false, true)) {
            val tol = if (inner) row.tolInt else row.tolExt
            for ((s, ok) in listOf(tol to true, -tol to true, tol + 0.01 to false, -tol - 0.01 to false)) {
                val c = PCase(row, inner, s, 0.0, 5.0, r3(-(if (inner) row.needInt else row.needExt) - 5), 0.3, 0.035, 0.02, 2, 0.2, null)
                val r = PipeThreadCalc.calc(input(c))
                if (ok && r.solution == null) bad += "${tag(c)}: 공차 안인데 멈춤 ${r.warnings}"
                if (!ok && (r.solution != null || G76Warning.BAD_INPUT !in r.warnings)) bad += "${tag(c)}: 공차 밖인데 ${r.warnings}"
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    // ───────────── 3. 다이스 카탈로그 표본 ─────────────

    @Test
    fun dieCatalogSamples() {
        val root = load("die-heads.json")
        val makers = root.arr("makers").map { it.jsonObject }
        assertEquals(makers.map { it.str("key") }, DieHeadCatalog.makers.map { it.key })
        for ((mj, ma) in makers.zip(DieHeadCatalog.makers)) {
            val sj = mj.arr("speeds").map { it.jsonObject }
            assertEquals(sj.size, ma.speeds.size, "${ma.key} 절삭 속도 줄 수")
            for ((o, s) in sj.zip(ma.speeds)) {
                val w = "${ma.key} ${o.str("material")}"
                assertEquals(o.str("material"), s.material, w)
                assertEquals(o.num("vcMin"), s.vcMin, w)
                assertEquals(o.num("vcRec"), s.vcRec, w)
                assertEquals(o.num("vcMax"), s.vcMax, w)
                assertEquals(o.str("sourceId"), s.sourceId, w)
                assertEquals(o.str("page"), s.page, w)
                assertEquals(o.str("threadType"), s.threadType, w)
            }
            val cj = mj.arr("chasers").map { it.jsonObject }
            assertEquals(cj.size, ma.chasers.size, "${ma.key} 체이서 줄 수")
            for ((o, c) in cj.zip(ma.chasers)) {
                assertEquals(o.str("designation"), c.designation)
                assertEquals(o.str("threadForm"), c.threadForm)
                assertEquals((o["tpi"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(), c.tpis)
                assertEquals((o["pitch"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(), c.pitches)
                assertEquals(o.str("sourceId"), c.sourceId)
                assertEquals(o.str("page"), c.page)
            }
        }
        // speeds(): 사다리꼴(Trapez) 줄만 빠짐
        val nonTrapez = makers.sumOf { m -> m.arr("speeds").count { it.jsonObject.str("threadType")?.startsWith("Trapez") != true } }
        assertEquals(nonTrapez, DieHeadCatalog.speeds().size)
        assertEquals(18, nonTrapez)
        // chasers(): 20산 UN → 나사 꼴 "UN" 이고 tpi 배열에 "20" 인 줄, M12×1.75 → "mm" 줄에 1.75
        fun want(form: (String) -> Boolean, hit: (JsonObject) -> Boolean) =
            makers.flatMap { m -> m.arr("chasers").map { it.jsonObject }.filter { o -> o.str("threadForm")?.let(form) == true && hit(o) }.map { m.str("key") to it.str("designation") } }
        val w20 = want({ it == "UN" }) { o -> (o["tpi"] as? JsonArray)?.any { it.jsonPrimitive.content == "20" } == true }
        for (s in ThreadTable.group(UnGroup.UN20).filter { it.size in listOf("1/4", "3/8", "1/2", "3/4", "1") }) {
            assertEquals(w20, DieHeadCatalog.chasers(s).map { it.maker.key to it.chaser.designation }, s.label)
        }
        val m12 = ThreadTable.METRIC.single { it.size == "M12" && abs(it.pitch - 1.75) < 1e-9 }
        val w175 = want({ it.startsWith("mm") }) { o -> (o["pitch"] as? JsonArray)?.any { abs(it.jsonPrimitive.content.toDouble() - 1.75) < 1e-9 } == true }
        assertEquals(w175, DieHeadCatalog.chasers(m12).map { it.maker.key to it.chaser.designation })
        assertTrue(w20.isNotEmpty() && w175.isNotEmpty())
    }

    // ───────────── 4. 인서트 ─────────────

    @Test
    fun insertsMatch() {
        val root = load("thread-inserts.json")
        val rows = root.arr("inserts").map { it.jsonObject }
        assertEquals(rows.size, ThreadInserts.ALL.size)
        for ((o, a) in rows.zip(ThreadInserts.ALL)) {
            val w = o.str("designation")!!
            assertEquals(o.str("maker"), a.maker, w)
            assertEquals(w, a.designation)
            assertEquals(o.str("lhDesignation"), a.lhDesignation, w)
            assertEquals(o.getValue("internal").jsonPrimitive.boolean, a.internal, w)
            assertEquals(o.str("form"), a.form.name, w)
            assertEquals(o.num("tpi"), a.tpi, w)
            assertEquals(o.str("ic"), a.ic, w)
            assertEquals(o.getValue("fullProfile").jsonPrimitive.boolean, a.fullProfile, w)
            assertEquals(o.str("sourceId"), a.sourceId, w)
            assertEquals(o.str("page"), a.page, w)
            // 날 끝 R: 원문 글자 "R 0.137P" 가 있는 줄만(JSON tipR)
            val tipText = o.str("tipR")
            if (tipText == null) assertEquals(null, a.tipRPerPitch, "$w R 없음") else assertTrue(tipText.contains("0.137") && a.tipRPerPitch == 0.137, "$w R $tipText")
        }
        val tpis = rows.map { it.num("tpi")!! }.distinct() + listOf(16.0, 12.5, 10.0)
        for (inner in listOf(false, true)) for (form in InsertForm.entries) for (tpi in tpis) {
            val want = rows.filter { it.getValue("internal").jsonPrimitive.boolean == inner && it.str("form") == form.name && it.num("tpi") == tpi }
                .map { it.str("maker") to it.str("designation") }
            val got = ThreadInserts.match(inner, form, tpi).map { it.maker to it.designation }
            assertEquals(want, got, "match($inner, $form, $tpi)")
        }
        // 사용자 결정(2026-11-05) 팁 3ER20UN · 짝 3IR20UN
        assertEquals("3ER20UN", ThreadInserts.match(false, InsertForm.UN, 20.0).first().designation)
        assertEquals("3IR20UN", ThreadInserts.match(true, InsertForm.UN, 20.0).first().designation)
    }
}
