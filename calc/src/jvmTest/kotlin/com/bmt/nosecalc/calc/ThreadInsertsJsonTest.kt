package com.bmt.nosecalc.calc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 앱의 나사 인서트 표([ThreadInserts])가 조사 원본(docs/research/thread-inserts.json — 출처 대조 끝남)과 맞는지. JSON 의 inserts 40줄을 차례대로
 * 앱 줄과 맞춘다: 제조사 · 오른손 · 왼손 이름 · 내외 · 꼴 · 산수 · IC · 풀 프로파일 · 출처 id · 쪽은 글 그대로, 날 끝 R 은 원문 글 "R 0.137P" 의 계수
 * (칸이 없거나 null 이면 앱도 null). [ThreadInserts.match] 가 고르는 줄도 본다.
 */
class ThreadInsertsJsonTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다(작업 폴더 ${System.getProperty("user.dir")})")
    }

    private val rows = Json.parseToJsonElement(findUp("docs/research/thread-inserts.json").readText(Charsets.UTF_8))
        .jsonObject.getValue("inserts").jsonArray.map { it.jsonObject }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    @Test
    fun everyRowMatchesJson() {
        assertEquals(40, rows.size)
        assertEquals(rows.size, ThreadInserts.ALL.size, "줄 수")
        for ((r, a) in rows.zip(ThreadInserts.ALL)) {
            val what = r.str("designation")
            assertEquals(r.str("maker"), a.maker, what)
            assertEquals(r.str("designation"), a.designation, what)
            assertEquals(r.str("lhDesignation"), a.lhDesignation, what)
            assertEquals(r.str("internal").toBoolean(), a.internal, what)
            assertEquals(r.str("form"), a.form.name, what)
            assertEquals(r.str("tpi").toDouble(), a.tpi, what)
            assertEquals(r.str("ic"), a.ic, what)
            assertEquals(r.str("fullProfile").toBoolean(), a.fullProfile, what)
            assertEquals(r.str("sourceId"), a.sourceId, what)
            assertEquals(r.str("page"), a.page, what)
            val tip = r["tipR"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            if (tip == null) assertNull(a.tipRPerPitch, what)
            else {
                val k = Regex("""R ([0-9.]+)P""").matchEntire(tip)?.groupValues?.get(1)?.toDouble() ?: fail("$what: 날 끝 R 글 $tip")
                assertEquals(k, a.tipRPerPitch, what)
            }
        }
        // 날 끝 R 이 있는 줄은 VARDEX BSPT 8줄뿐
        assertEquals(8, ThreadInserts.ALL.count { it.tipRPerPitch != null })
        assertTrue(ThreadInserts.ALL.filter { it.tipRPerPitch != null }.all { it.maker == "Vargus (VARDEX)" && it.form == InsertForm.BSPT })
    }

    /** 고르기: 20산 UN 외 · 내는 두 회사 하나씩, BSPT 14산 외는 VARDEX(R 0.137 × 25.4/14 = 0.248557) · Carmex(R 없음), 없는 산수는 빈 목록 */
    @Test
    fun matchPicksRows() {
        assertEquals(listOf("3ER20UN", "16 ER 20 UN"), ThreadInserts.match(false, InsertForm.UN, 20.0).map { it.designation })
        assertEquals(listOf("3IR20UN", "16 IR 20 UN"), ThreadInserts.match(true, InsertForm.UN, 20.0).map { it.designation })
        assertEquals(listOf("3EL20UN", "16 EL 20 UN"), ThreadInserts.match(false, InsertForm.UN, 20.0).map { it.lhDesignation })
        val bspt = ThreadInserts.match(false, InsertForm.BSPT, 14.0)
        assertEquals(listOf("3ER14BSPT", "16 ER 14 BSPT"), bspt.map { it.designation })
        assertTrue(abs(0.24855714285714287 - bspt[0].tipR!!) < 1e-12, "${bspt[0].tipR}")
        assertNull(bspt[1].tipR)
        assertEquals(listOf("3IR11.5NPT", "16 IR 11.5 NPT"), ThreadInserts.match(true, InsertForm.NPT, 11.5).map { it.designation })
        assertTrue(ThreadInserts.match(false, InsertForm.UN, 16.0).isEmpty())
        assertTrue(ThreadInserts.match(true, InsertForm.NPT, 19.0).isEmpty())
        assertTrue(ThreadInserts.ALL.all { it.ic == "3/8\"" && it.fullProfile })
    }

    /**
     * 내경 줄의 최소 구멍(가장 작은 홀더의 Min. Bore): VARDEX 는 3IR20UN 줄 minBore.holderMinBore 글의 첫 수(인치) × 25.4 — 8쪽 추천표의 가장 작은 홀더
     * (holderForSmallest NVR050-3)이고 다른 내경 줄의 추천 홀더도 같은 -3 홀더라 모두 같은 값, Carmex 는 16 IR 20 UN 줄 minBore.holders 의 가장 작은 값(mm,
     * 나머지 내경 줄은 'see' 로 같은 포켓 16 홀더). 외경 줄은 null
     */
    @Test
    fun minBoreFromJson() {
        fun mb(designation: String) = rows.single { it.str("designation") == designation }.getValue("minBore").jsonObject
        val vdxIn = Regex("^([0-9.]+) in").find(mb("3IR20UN").str("holderMinBore"))!!.groupValues[1].toDouble() * 25.4
        assertTrue(mb("3IR20UN").str("holderForSmallest").startsWith("NVR050-3"))
        val cmx = mb("16 IR 20 UN").getValue("holders").jsonArray.minOf { it.jsonObject.str("minBore").toDouble() }
        assertEquals(17.018, vdxIn, 1e-9)
        assertEquals(16.0, cmx)
        for ((r, a) in rows.zip(ThreadInserts.ALL)) {
            val what = r.str("designation")
            if (!a.internal) {
                assertNull(a.minBore, what)
                continue
            }
            val want = if (a.maker == "Carmex") cmx else vdxIn
            assertEquals(want, a.minBore!!, 1e-9, what)
            // Carmex 다른 내경 줄은 16 IR 20 UN 의 홀더를 가리킨다
            if (a.maker == "Carmex" && what != "16 IR 20 UN") assertTrue(mb(what).str("see").startsWith("16 IR 20 UN"), what)
        }
    }
}
