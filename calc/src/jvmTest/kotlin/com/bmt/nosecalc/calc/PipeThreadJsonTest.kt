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
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 앱의 관용 테이퍼 표([PipeTable])가 조사 원본(docs/research/pipe-threads.json — 출처 대조 끝남)과 맞는지. 앱의 모든 칸을 견준다.
 *
 * 단위 · 반올림 규칙:
 *  - PT 는 JSON 의 PT[].jis(JIS B 0203:1999 付表1, mm) 16줄 전부, 차례대로. 그대로 옮긴 칸(산수 · a · f · l · r · 강관)은 정확히(1e-9).
 *    게이지 면 피치경은 앱이 d − 0.640327P 로 내므로(ISO 7-1 Table 1 주: 바깥지름을 먼저 환산하고 h 를 뺐다) d 는 정확히,
 *    표의 d2 는 ±0.0005(표가 0.001 로 둥금), d1 = d − 2h 는 ±0.001(h 를 0.001 로 둥근 뒤 두 번 뺀 표 — JSON formulaChecksR2 와 같은 경계),
 *    h 는 표 3자리라 ±0.0005, P 는 표 4자리라 ±0.00005, r = 0.137278P 는 표 2자리라 ±0.005.
 *    게이지 공차(산)는 ISO 7-1 Table 1 의 10 · 14열(T1halfTurns · T2halfTurns — 규정값 "1" · "1 1/4" · "1 1/2")과 같고, 산 × P 를 0.01 로 둥근 값이
 *    JIS b · c 다(ISO 줄이 없는 3 1/2 은 JIS b · c 로만).
 *  - NPT 는 JSON 의 NPT[](H28 1957 Part II 표 VII.2, 인치) 앞 14줄(1/16 ~ 4) — 앱 칸 = 표 인치 × 25.4 (1e-9 mm). 산수는 그대로.
 *    표에 없는 칸은 표의 관계로 본다: 관 끝 피치경 E1 − L1/16 = E0(둘 다 5자리라 ±0.00001 인치), 0.8p = h(±0.000005 인치), p(±0.000005 인치)
 *    — 경계에 1e-9 mm 를 더한 것은 부동소수 몫이다.
 *  - 상수: PT 55° · h 0.640327P · r 0.137278P, NPT 60° · h 0.8p · 제품 허용차 ±1산.
 */
class PipeThreadJsonTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다(작업 폴더 ${System.getProperty("user.dir")})")
    }

    private val root = Json.parseToJsonElement(findUp("docs/research/pipe-threads.json").readText(Charsets.UTF_8)).jsonObject

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.num(k: String): Double? = (get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDouble()
    private fun JsonObject.obj(k: String) = getValue(k).jsonObject

    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a (차 ${a - e})")

    /** "1" · "1 1/4" · "1 1/2" → 산 */
    private fun turns(t: String): Double {
        val parts = t.trim().split(' ')
        val whole = parts[0].toDouble()
        if (parts.size == 1) return whole
        val (n, d) = parts[1].split('/').map { it.toDouble() }
        return whole + n / d
    }

    /** 0.01 로 반올림(5 는 올림) */
    private fun round2(v: Double) = floor(v * 100 + 0.5 + 1e-9) / 100

    @Test
    fun ptMatchesJis() {
        val rows = root.getValue("PT").jsonArray.map { it.jsonObject }
        assertEquals(rows.size, PipeTable.PT.size, "PT 줄 수")
        for ((row, s) in rows.zip(PipeTable.PT)) {
            val j = row.obj("jis")
            val what = s.name(false)
            assertEquals(row.str("size"), s.size)
            assertEquals(PipeKind.PT, s.kind)
            assertEquals(row.str("tpi").toDouble(), s.tpi, what)
            near(j.num("P")!!, s.pitch, 0.00005, "$what P")
            near(j.num("h")!!, s.h, 0.0005, "$what h")
            near(j.num("d")!!, s.gaugePitch + s.h, 1e-9, "$what 기준경 바깥지름 d")
            near(j.num("d2")!!, s.gaugePitch, 0.0005, "$what 유효지름 d2")
            near(j.num("d1")!!, s.gaugePitch - s.h, 0.001, "$what 골지름 d1")
            near(j.num("a")!!, s.gaugeLen, 1e-9, "$what 기준 길이 a")
            near(j.num("a")!! + j.num("f")!!, s.extLen, 1e-9, "$what 숫나사 최소 길이 a + f")
            near(j.num("l")!!, s.intLen, 1e-9, "$what 암나사 최소 길이 l")
            near(j.num("r")!!, s.rootR!!, 1e-9, "$what r")
            near(0.137278 * s.pitch, s.rootR!!, 0.005, "$what r = 0.137278P")
            val od = j.num("pipeOD")
            if (od == null) assertNull(s.pipeOd, what) else near(od, s.pipeOd!!, 1e-9, "$what 강관")
            // 게이지 공차: 산 × P 를 0.01 로 = JIS b · c, ISO 줄이 있으면 산 수 = ISO 10 · 14열
            near(j.num("b")!!, round2(s.tolTurns * s.pitch), 1e-9, "$what b = 산 × P")
            near(j.num("c")!!, round2(s.tolTurnsInner * s.pitch), 1e-9, "$what c = 산 × P")
            val iso = row["iso"] as? JsonObject
            if (iso != null) {
                assertEquals(turns(iso.str("T1halfTurns")), s.tolTurns, "$what ISO 10열")
                assertEquals(turns(iso.str("T2halfTurns")), s.tolTurnsInner, "$what ISO 14열")
                // 지름은 JIS 와 ISO 가 같다
                near(iso.num("d")!!, s.gaugePitch + s.h, 1e-9, "$what ISO d")
            } else {
                assertEquals("3 1/2", s.size, "ISO 줄이 없는 크기")
            }
        }
    }

    @Test
    fun nptMatchesH28() {
        val rows = root.getValue("NPT").jsonArray.map { it.jsonObject }
        assertEquals(24, rows.size, "표 VII.2 줄 수")
        assertEquals(14, PipeTable.NPT.size, "앱은 1/16 ~ 4")
        assertEquals("4", rows[13].str("size"))
        for ((row, s) in rows.zip(PipeTable.NPT)) {
            val what = s.label
            assertEquals(row.str("size"), s.size)
            assertEquals(PipeKind.NPT, s.kind)
            assertEquals(row.str("tpi").toDouble(), s.tpi, what)
            near(row.num("D")!! * 25.4, s.pipeOd!!, 1e-9, "$what D")
            near(row.num("E1")!! * 25.4, s.gaugePitch, 1e-9, "$what E1")
            near(row.num("L1")!! * 25.4, s.gaugeLen, 1e-9, "$what L1")
            near(row.num("L2")!! * 25.4, s.extLen, 1e-9, "$what L2")
            near((row.num("L1")!! + row.num("L3")!!) * 25.4, s.intLen, 1e-9, "$what L1 + L3")
            near(row.num("p")!! * 25.4, s.pitch, 0.000005 * 25.4 + 1e-9, "$what p")
            near(row.num("h")!! * 25.4, s.h, 0.000005 * 25.4 + 1e-9, "$what h = 0.8p")
            // E0 · E1 이 각각 5자리로 둥근 값이라 차는 0.00001 인치까지 — 4 in 줄이 꼭 그 경계다(JSON r2Review)
            near(row.num("E0")!! * 25.4, s.gaugePitch - s.gaugeLen / 16, 0.00001 * 25.4 + 1e-9, "$what E0 = E1 − L1/16")
            assertNull(s.rootR, what)
        }
    }

    @Test
    fun constantsMatch() {
        val c = root.obj("constants")
        val pt = c.obj("PT")
        assertEquals(pt.obj("angle").num("value")!!.toInt(), PipeTable.PT[0].angleDeg)
        assertEquals(0.640327, pt.obj("taperThreadProfile").num("h"))
        assertEquals(0.137278, pt.obj("taperThreadProfile").num("r"))
        for (s in PipeTable.PT) near(0.640327, s.h / s.pitch, 1e-12, "${s.label} h/P")
        val npt = c.obj("NPT")
        assertEquals(npt.obj("angle").num("value")!!.toInt(), PipeTable.NPT[0].angleDeg)
        assertEquals("true", npt.obj("h").str("isEightTenthsP"))
        for (s in PipeTable.NPT) near(0.8, s.h / s.pitch, 1e-12, "${s.label} h/p")
        assertEquals("±1산 (게이지)", npt.obj("productTolerance").str("value"))
        assertTrue(PipeTable.NPT.all { it.tolTurns == 1.0 && it.tolTurnsInner == 1.0 }, "NPT 게이지 ±1산")
    }
}
