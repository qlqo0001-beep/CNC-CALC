package com.bmt.nosecalc.calc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 앱에 넣은 드릴 카탈로그가 조사 원본(docs/research/drill-catalog.json)과 **값 하나 다르지 않은지**.
 *  1. calc/catalog/drill-catalog.json 이 원본과 바이트까지 같다.
 *  2. 빌드가 만든 문자열([DrillCatalogJson])이 그 파일 글 그대로다.
 *  3. 앱이 읽은 자료(타입 있는 모델)를 다시 JSON 으로 바꿔 원본 JSON 트리와 칸마다 맞춘다 — 모든 메이커 · 시리즈 · 행 · 이송 구간 ·
 *     출처 · 각주. 숫자는 Double 로, 원본에 없는 칸은 null 로 본다. 원본에 모델이 모르는 칸이 있으면 읽기가 실패한다(ignoreUnknownKeys = false).
 */
class DrillCatalogJsonTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다(작업 폴더 ${System.getProperty("user.dir")})")
    }

    private val original = findUp("docs/research/drill-catalog.json")
    private val copy = findUp("calc/catalog/drill-catalog.json")

    @Test
    fun copyIsByteIdentical() {
        assertContentEquals(original.readBytes(), copy.readBytes(), "calc/catalog 의 사본이 원본과 다르다")
        assertEquals(copy.readText(Charsets.UTF_8), DrillCatalogJson.text(), "빌드가 만든 문자열이 사본과 다르다")
    }

    @Test
    fun everyValueMatchesOriginal() {
        val orig = Json.parseToJsonElement(original.readText(Charsets.UTF_8))
        val mine = Json.encodeToJsonElement(DrillCatalog.makers)
        var leaves = 0
        fun cmp(o: JsonElement, m: JsonElement, path: String) {
            when (o) {
                is JsonObject -> {
                    val mo = m as? JsonObject ?: fail("$path: 원본은 객체, 앱은 $m")
                    for (k in o.keys + mo.keys) cmp(o[k] ?: JsonNull, mo[k] ?: JsonNull, "$path.$k")
                }
                is JsonArray -> {
                    val ma = m as? JsonArray ?: fail("$path: 원본은 배열, 앱은 $m")
                    assertEquals(o.size, ma.size, "$path 개수")
                    o.forEachIndexed { i, e -> cmp(e, ma[i], "$path[$i]") }
                }
                is JsonNull -> assertEquals(JsonNull, m, path)
                is JsonPrimitive -> {
                    val mp = m as? JsonPrimitive ?: fail("$path: 원본은 값, 앱은 $m")
                    assertEquals(o.isString, mp.isString, "$path 글/숫자")
                    if (o.isString) assertEquals(o.content, mp.content, path)
                    else assertEquals(o.content.toDouble(), mp.content.toDouble(), path)
                    leaves++
                }
            }
        }
        cmp(orig, mine, "$")
        // 손으로 센 값: 5사 · 35 시리즈 · 519 행 · 이송 구간 3052
        val makers = orig.jsonArray
        assertEquals(5, makers.size)
        val drills = makers.flatMap { it.jsonObject["catalog"]!!.jsonObject["drills"]!!.jsonArray }
        assertEquals(35, drills.size)
        val conds = drills.flatMap { it.jsonObject["conditions"]!!.jsonArray }
        assertEquals(519, conds.size)
        assertEquals(3052, conds.sumOf { it.jsonObject["feeds"]!!.jsonArray.size })
        // 원본의 null 아닌 값 전부(파이썬으로 센 수)
        assertEquals(18_214, leaves, "비교한 값 개수")
    }
}
