package com.bmt.nosecalc

import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.screens.round.COMPANY_PRESETS
import com.bmt.nosecalc.screens.round.HolderPreset
import com.bmt.nosecalc.screens.sim.COMPANY_HOLDERS
import com.bmt.nosecalc.screens.sim.HolderGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 앱에 넣은 회사 사용 홀더가 조사 원본(docs/research/company-holders.json — 출처를 다시 열어 칸마다 대조한 것)과 **값 하나 다르지 않은지**
 * (드릴 카탈로그의 DrillCatalogJsonTest 처럼).
 *  1. 라운드 보링바 프리셋의 회사 줄(바 값이 있는 20 줄)이 presetCandidates.rows 와 제조사 · 품번 · D · f · DMIN · κr · 모양 · 날 길이 · URL 까지 같다.
 *  2. 그 줄마다 holders 의 제조사 항목(designation = 품번)의 DCON · WF · DMIN · KAPR · 인서트 모양 · 날 길이와 같고, 출처 글 · URL 에 그 쪽(page)이 있다.
 *     단종 표시 제조사는 프리셋에 없다.
 *  3. 바 값이 없는 회사 줄(슬리브 · 계열 이름)은 holders 에서도 바 값이 없다고(missing) 적힌 것이고, URL 은 그 출처다.
 *  4. holders 의 보링바 · 소경 항목은 모두 프리셋에 있다(빠진 것 없음).
 *  5. 시뮬레이터 '홀더 고르기' 줄은 holders 의 17 품번 전부이고, 줄마다 종류 · 묶음 · 인서트 모양 · κr · 제조사가 holders 와 같다
 *     (κr 은 그 홀더에 simulator 칸이 있으면 그 값 — DCKNL 의 KAPR 75° 는 정면 이송 기준이라 빈칸).
 *     바 지름은 보링바 = DCON, 슬리브 = DCB(끼우는 바), 그 밖(외경 · 나사 · 계열 이름)은 없음.
 */
class CompanyHoldersJsonTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다(작업 폴더 ${System.getProperty("user.dir")})")
    }

    private val root = Json.parseToJsonElement(findUp("docs/research/company-holders.json").readText(Charsets.UTF_8)).jsonObject
    private val holders = root["holders"]!!.jsonArray.map { it.jsonObject }
    private val sources = root["sources"]!!.jsonArray.map { it.jsonObject }.associateBy { it.str("id")!! }

    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.num(k: String): Double? = this[k]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.obj(k: String): JsonObject = this[k]!!.jsonObject
    private fun JsonObject.makers(): List<JsonObject> = this["makers"]!!.jsonArray.map { it.jsonObject }

    /** 제조사 항목: 홀더 · 그 제조사 */
    private val entries: List<Pair<JsonObject, JsonObject>> = holders.flatMap { h -> h.makers().map { h to it } }

    /** 앱의 제조사 이름(한국야금 · 샌드빅 · 미쓰비시 …)은 원본 이름의 첫 낱말 */
    private fun short(maker: String) = maker.substringBefore(' ')
    private fun obsolete(e: JsonObject) = "단종" in e.str("maker")!!

    private fun row(p: HolderPreset) = listOf(p.maker, p.code, p.dia, p.f, p.dMin, p.kappa, p.shape?.name, p.edge, p.url)

    @Test
    fun presetsEqualCandidates() {
        val rows = root.obj("presetCandidates")["rows"]!!.jsonArray.map { it.jsonObject }.map { r ->
            listOf(r.str("maker"), r.str("code"), r.num("dia"), r.num("f"), r.num("dMin"), r.num("kappa"), r.str("shape"), r.num("edge"), r.str("url"))
        }
        val mine = COMPANY_PRESETS.filter { it.bar != null }.map(::row)
        assertEquals(20, rows.size)
        assertEquals(rows.size, mine.size)
        assertEquals(rows.map { it.toString() }.sorted(), mine.map { it.toString() }.sorted())
    }

    @Test
    fun presetsMatchHolderValues() {
        for (p in COMPANY_PRESETS.filter { it.bar != null }) {
            val (h, e) = entries.singleOrNull { (_, e) -> e.str("designation") == p.code } ?: fail("${p.code}: holders 에 그 designation 이 없다")
            assertTrue(h.str("kind")!!.startsWith("보링바"), p.code)
            assertEquals(short(e.str("maker")!!), p.maker, p.code)
            assertTrue(!obsolete(e), "${p.code}: 단종 표시 제조사")
            val v = e.obj("values")
            assertEquals(listOf(v.num("DCON"), v.num("WF"), v.num("DMIN"), v.num("KAPR"), v.str("insertShape"), v.num("edge")), row(p).subList(2, 8), p.code)
            val page = e.str("page")!!.substringBefore(' ')
            assertTrue(page in p.source!! || page in p.url, "${p.code}: 쪽 $page 가 출처 '${p.source}' · ${p.url} 에 없다")
            assertTrue(p.company)
        }
    }

    @Test
    fun blankPresetsHaveNoBarValues() {
        val blank = COMPANY_PRESETS.filter { it.bar == null }
        assertEquals(listOf("SH0516-100", "SH0616-100", "PICCO"), blank.map { it.code })
        for (p in blank) {
            val h = holders.single { it.str("code") == p.code }
            val e = h.makers().single()
            assertEquals(short(e.str("maker")!!), p.maker)
            assertTrue(listOf(p.dia, p.f, p.dMin, p.kappa, p.shape, p.edge).all { it == null }, p.code)
            val missing = e["missing"]!!.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
            assertTrue("D · f · DMIN · κr" in missing, "${p.code}: $missing")
            val urls = e["sourceIds"]!!.jsonArray.map { sources.getValue(it.jsonPrimitive.content).str("url") }
            assertTrue(p.url in urls, "${p.code}: ${p.url} ∉ $urls")
            assertTrue(e.str("page")!!.substringBefore(' ') in p.source!!, p.code)
            assertNotNull(p.note)
        }
    }

    @Test
    fun everyBoringBarIsAPreset() {
        val codes = COMPANY_PRESETS.map { it.code }.toSet()
        var n = 0
        for ((h, e) in entries) {
            val kind = h.str("kind")!!
            if ("소경" in kind) {
                assertTrue(h.str("code") in codes, h.str("code"))
                n++
            } else if (kind.startsWith("보링바") && !obsolete(e)) {
                assertTrue(e.str("designation") in codes, "${h.str("code")} ${e.str("maker")}")
                n++
            }
        }
        // 소경 3(PICCO 는 계열 하나) + 보링바 20
        assertEquals(COMPANY_PRESETS.size, n)
    }

    @Test
    fun simHoldersMatchJson() {
        assertEquals(holders.map { it.str("code")!! }.toSet(), COMPANY_HOLDERS.map { it.code }.toSet())
        assertEquals(COMPANY_HOLDERS.size, COMPANY_HOLDERS.map { it.id }.distinct().size)
        for (h in holders) {
            val code = h.str("code")!!
            val kind = h.str("kind")!!
            val rows = COMPANY_HOLDERS.filter { it.code == code }
            val makers = h.makers().filter { !obsolete(it) }
            val sim = h["simulator"]?.jsonObject
            // 줄들의 제조사를 모으면 원본의 (단종 아닌) 제조사 그대로
            assertEquals(makers.map { short(it.str("maker")!!) }.sorted(), rows.flatMap { it.makers }.sorted(), code)
            for (r in rows) {
                val group = when {
                    "나사" in kind -> HolderGroup.THREAD
                    "소경" in kind -> HolderGroup.SMALL
                    kind.startsWith("외경 바이트") -> HolderGroup.OUTER
                    kind.startsWith("보링바") -> HolderGroup.BORING
                    else -> fail("$code: 모르는 종류 $kind")
                }
                assertEquals(group, r.group, code)
                assertEquals(if (group == HolderGroup.THREAD) ToolKind.THREAD else ToolKind.TURN, r.kind, code)
                if (r.makers.isEmpty()) {
                    assertTrue(r.insert == null && r.kappa == null, "$code: 제조사를 모르면 값도 없다")
                }
                for (m in r.makers) {
                    val v = makers.single { short(it.str("maker")!!) == m }.obj("values")
                    assertEquals(v.str("insertShape"), r.insert?.name, "$code $m 인서트")
                    assertEquals(if (sim != null) sim.num("kappa") else v.num("KAPR"), r.kappa, "$code $m κr")
                    val bar = when (group) {
                        HolderGroup.BORING -> assertNotNull(v.num("DCON"), "$code $m DCON")
                        HolderGroup.SMALL -> v.num("DCB")
                        else -> null
                    }
                    assertEquals(bar, r.bar, "$code $m 바 지름")
                }
                if (r.makers.isEmpty()) assertEquals(null, r.bar, "$code: 제조사를 모르면 바 지름도 없다")
            }
        }
    }
}
