package com.bmt.nosecalc.calc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 앱의 UNEF · 20UN 자료가 조사 원본(docs/research/unified-extra.json — NBS Handbook H28 (1969) Part I, 출처 대조 끝남)과 맞는지.
 *  1. series.UNEF 25줄 · series.UN20 37줄 = [ThreadTable.group] 의 줄(차례 · 이름 · 인치 지름 · 산수 · 겹치는 계열).
 *  2. engagement(계열별 물림 길이) · classes(계열별 급수) = [ThreadTol.engagement] · [ThreadClass.choices].
 *  3. limits 98줄의 모든 값 칸 = [ThreadTol.limits] (인치 → mm 는 × 25.4). 값이 있는데 견주지 않은 칸이 있으면 실패한다.
 *
 * 허용차 근거(기존 ThreadSpecTest.unifiedMatchesPublishedTable 과 같은 원칙 — 표의 반올림 자리 + 끝자리):
 *  - 0.0001 인치 자리 칸(바깥지름 · 피치경 · 피치경 공차 · 3B 안지름 최대 · 숫나사 골 칸): ±0.00015 인치. H28 표는 공차식의 증분을 6자리,
 *    공차 · 허용차를 4자리로 반올림해 더했고(JSON tableVsFormula.procedure), 앱은 반올림 없이 더한다 — 그래서 98줄 가운데 UNC · UNF 를 포함한
 *    약 절반이 0.0001 다르다(tableVsFormula.appNote). nominalDGap 9 표기(표 공차가 호칭 D 식보다 0.0001 다름 — MH31 · ASME 쪽은 식과 같음)도
 *    이 안이다. 이 JSON 으로 잰 최대 차는 0.000136 인치(#12-32 UNEF-2A 피치경 최소).
 *  - 허용차 칸: ±0.00006 인치(표는 허용차를 4자리로 반올림 — unifiedMatchesPublishedTable 의 1/4-20 · 1/2-13 허용차와 같음).
 *  - 0.001 인치 자리 칸(암나사 안지름 최소 전부 · 1B · 2B 안지름 최대): ±0.0006 인치(표는 최소를 3자리로 먼저 반올림하고 공차를 더함 — 같은 원칙).
 *  - 암나사 바깥지름 최소 = 호칭 지름: 정확히(1e-9 mm).
 * 인쇄 오류로 보아 비운 두 칸(removed — 5/8-24 UNEF-3B 피치경 최소 · 1-1/2-18 UNEF-2B 안지름 최대)은 null 이라 견주지 않는다.
 * MH31 칸(limits[].mh31)은 보조 출처라 견주지 않는다(값으로 쓰지 않음 — JSON rules).
 * LE 를 반대로 넣으면 이 검사가 실패하는지(3/4-20 UNEF-2A 피치경 최소 0.7118 ↔ D 식 0.7114)는 ThreadSpecTest.engagementBySeries 가 본다.
 */
class UnifiedExtraJsonTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다(작업 폴더 ${System.getProperty("user.dir")})")
    }

    private val root = Json.parseToJsonElement(findUp("docs/research/unified-extra.json").readText(Charsets.UTF_8)).jsonObject

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.num(k: String): Double? = (get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDouble()

    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a (차 ${a - e})")

    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }

    @Test
    fun seriesRowsMatchTable() {
        val unef = root.getValue("series").jsonObject.getValue("UNEF").jsonArray.map { it.jsonObject }
        val mine = ThreadTable.group(UnGroup.UNEF)
        assertEquals(unef.size, mine.size, "UNEF 줄 수")
        for ((o, s) in unef.zip(mine)) {
            assertEquals(o.str("size"), s.size)
            assertEquals(o.str("tpi").toInt(), s.tpi, s.label)
            near(o.num("dIn")!! * 25.4, s.d, 1e-9, "${s.label} 지름")
            near(25.4 / s.tpi!!, s.pitch, 1e-12, "${s.label} 피치")
            assertEquals(ThreadSeries.UNEF, s.series)
        }
        val un20 = root.getValue("series").jsonObject.getValue("UN20").jsonArray.map { it.jsonObject }
        val mine20 = ThreadTable.group(UnGroup.UN20)
        assertEquals(un20.size, mine20.size, "20UN 줄 수")
        for ((o, s) in un20.zip(mine20)) {
            assertEquals(o.str("size"), s.size)
            assertEquals(20, s.tpi, s.label)
            near(o.num("dIn")!! * 25.4, s.d, 1e-9, "${s.label} 지름")
            // 겹치는 계열(UNC · UNF · UNEF) — 없으면 일정 피치 UN
            val with = (o.getValue("coincidesWith") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            assertEquals(with ?: "UN", s.series.name, "${s.size}-20 의 계열")
        }
        // designationRule.bySize 의 "크기-20" 열쇠 8개
        for ((k, v) in root.getValue("designationRule").jsonObject.getValue("bySize").jsonObject) {
            if (!k.endsWith("-20")) continue
            assertEquals(v.jsonPrimitive.content, un(k.removeSuffix("-20"), 20).series.name, k)
        }
    }

    @Test
    fun engagementAndClassesMatch() {
        val e = root.getValue("engagement").jsonObject
        // 계열마다 대표 줄 하나: LE "D" = 호칭 지름, "9P" = 9 피치(인치)
        for ((key, s) in listOf("UNC" to un("1/4", 20), "UNF" to un("1/2", 20), "UNEF" to un("3/4", 20), "UN20" to un("3/8", 20))) {
            val want = when (val rule = e.str(key)) {
                "D" -> s.d / 25.4
                "9P" -> 9 * s.pitch / 25.4
                else -> fail("모르는 물림 길이 규칙 $key: $rule")
            }
            near(want, ThreadTol.engagement(s), 1e-12, "$key LE")
        }
        val classes = root.getValue("classes").jsonObject
        for ((key, series) in listOf("UNEF" to ThreadSeries.UNEF, "UN20" to ThreadSeries.UN)) {
            val want = classes.getValue(key).jsonArray.map { it.jsonPrimitive.content }
            assertEquals(want, (ThreadClass.choices(series, false) + ThreadClass.choices(series, true)).map { it.label }, "$key 급수")
        }
    }

    @Test
    fun limitsMatchThreadTol() {
        val inch4 = 0.00015 * 25.4
        val allowTol = 0.00006 * 25.4
        val inch3 = 0.0006 * 25.4
        val meta = setOf("designation", "h28Label", "class", "sourceId", "page", "formulaCheck", "mh31")
        var cells = 0
        for (row in root.getValue("limits").jsonArray.map { it.jsonObject }) {
            val designation = row.str("designation") // "1-1/2-18 UNEF"
            val (sizeTpi, seriesName) = designation.split(' ')
            val spec = un(sizeTpi.substringBeforeLast('-'), sizeTpi.substringAfterLast('-').toInt())
            assertEquals(seriesName, spec.series.name, designation)
            val cls = ThreadClass.entries.single { it.kind == ThreadKind.UN && it.label == row.str("class") }
            val l = ThreadTol.limits(spec, cls)
            val what = "$designation-${cls.label}"
            val seen = mutableSetOf<String>()
            fun cmp(k: String, app: Double, tol: Double) {
                seen += k
                val v = row.num(k) ?: return
                near(v * 25.4, app, tol, "$what $k")
                cells++
            }
            cmp("pitchMin", l.pitchDia.min, inch4)
            cmp("pitchMax", l.pitchDia.max, inch4)
            cmp("pitchTol", l.pitchDia.width, inch4)
            if (!cls.internal) {
                cmp("majorMin", l.crest.min, inch4)
                cmp("majorMax", l.crest.max, inch4)
                cmp("allowance", -l.deviation, allowTol)
                // 숫나사 골 칸 = Ks − 허용차, Ks = D − 1.226869p(표 2.10 · 2.16 의 설계 형상 숫나사 안지름 — 앱 칸 없음): 앱 허용차로 맞춰 본다
                cmp("minorMax", spec.d - 1.226869 * spec.pitch + l.deviation, inch4)
                // 열간압연 소재 바깥지름 최소(1/4-20 UNC-2A 각주 c) = 최대 − 0.090∛p² — 앱 칸 없음, 같은 식인 1A 바깥지름 최소로
                if (row.num("majorMinHotRolled") != null) cmp("majorMinHotRolled", ThreadTol.limits(spec, ThreadClass.UN1A).crest.min, inch4)
            } else {
                cmp("majorMin", l.rootLimit, 1e-9)
                cmp("minorMin", l.crest.min, inch3)
                cmp("minorMax", l.crest.max, if (cls == ThreadClass.UN3B) inch4 else inch3)
            }
            // 값이 있는데 견주지 않은 칸이 없어야 한다
            for ((k, v) in row) {
                if (k in meta || k in seen) continue
                assertTrue(v is JsonNull, "$what: 견주지 않은 값 칸 $k = $v")
            }
        }
        // 견준 칸 수 = JSON 에서 센 값 칸 636 (출처 대조의 '값 638칸' 에서 인쇄 오류로 비운 2칸을 뺀 수)
        assertEquals(636, cells, "견준 칸 수")
    }
}
