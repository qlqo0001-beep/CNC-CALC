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
 * 앱에 넣은 다이스 헤드 카탈로그가 조사 원본(docs/research/die-heads.json)과 **값 하나 다르지 않은지** — DrillCatalogJsonTest 와 같은 꼴.
 *  1. calc/catalog/die-heads.json 이 원본과 바이트까지 같다.
 *  2. 빌드가 만든 문자열([DieHeadCatalogJson])이 그 파일 글 그대로다.
 *  3. 앱이 읽은 자료(모델)를 다시 JSON 으로 바꿔 원본 JSON 트리와 칸마다 맞춘다 — 출처 · 메이커 · 절삭 속도 · 체이서 · 홀더 표 · 조사 기록.
 *     숫자는 Double 로, 원본에 없는 칸은 null 로 본다. 원본에 모델이 모르는 칸이 있으면 읽기가 실패한다(ignoreUnknownKeys = false).
 *     JsonElement 로 들고 다니는 칸(머리 사양 · 목 · 릴리프 · 조사 기록 · 체이서의 tpi 같은 섞인 칸)은 값 그대로 되돌아오는지 본다.
 */
class DieHeadCatalogJsonTest {
    private fun findUp(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("$rel 을 찾지 못했다(작업 폴더 ${System.getProperty("user.dir")})")
    }

    private val original = findUp("docs/research/die-heads.json")
    private val copy = findUp("calc/catalog/die-heads.json")

    @Test
    fun copyIsByteIdentical() {
        assertContentEquals(original.readBytes(), copy.readBytes(), "calc/catalog 의 사본이 원본과 다르다")
        assertEquals(copy.readText(Charsets.UTF_8), DieHeadCatalogJson.text(), "빌드가 만든 문자열이 사본과 다르다")
    }

    @Test
    fun everyValueMatchesOriginal() {
        val orig = Json.parseToJsonElement(original.readText(Charsets.UTF_8))
        val mine = Json.encodeToJsonElement(DieHeadCatalog.data)
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
        // 손으로 센 값(파이썬): 출처 21 · 메이커 3 · 절삭 속도 24 · 체이서 34 · 홀더 표 줄 162
        val root = orig.jsonObject
        assertEquals(21, root["sources"]!!.jsonArray.size)
        val makers = root["makers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, makers.size)
        assertEquals(24, makers.sumOf { it["speeds"]!!.jsonArray.size })
        assertEquals(34, makers.sumOf { it["chasers"]!!.jsonArray.size })
        assertEquals(162, makers.sumOf { mk -> mk["chaserHolders"]?.jsonArray?.sumOf { it.jsonObject["rows"]!!.jsonArray.size } ?: 0 })
        // 원본의 null 아닌 값 전부(파이썬으로 센 수)
        assertEquals(3102, leaves, "비교한 값 개수")
    }
}
