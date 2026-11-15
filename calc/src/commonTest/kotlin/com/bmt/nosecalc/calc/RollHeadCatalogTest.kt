package com.bmt.nosecalc.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 전조 머리 카탈로그(RollHeadCatalog) — 기대값은 roll-heads.json 을 파이썬으로 읽어 손으로 옮긴 값(출처: LMT Fette 카탈로그 머리 쪽 그림표 ·
 * 사용 설명서 F1–F3 EVO Tab. 6 · Tab. 8).
 */
class RollHeadCatalogTest {
    @Test
    fun heads() {
        assertEquals(listOf("F2", "F23", "F233400", "F3", "F34", "F3-1RN", "F34-1RN"), RollHeadCatalog.data.heads.map { it.key })
        assertEquals(listOf("F2", "F23", "F233400", "F3", "F34"), RollHeadCatalog.evoHeads.map { it.key })
        // (지름 최소, 최대, 산수 최소, 최대, 열림 행정 a)
        val expected = mapOf(
            "F2" to listOf(8.0, 16.0, 11.0, 24.0, 3.0),
            "F23" to listOf(8.0, 22.0, 14.0, 32.0, 3.0),
            "F233400" to listOf(16.0, 36.0, 11.0, 26.0, 3.0),
            "F3" to listOf(12.0, 22.0, 9.0, 20.0, 4.0),
            "F34" to listOf(12.0, 30.0, 10.0, 28.0, 4.0),
        )
        for ((k, v) in expected) {
            val h = assertNotNull(RollHeadCatalog.head(k), k)
            assertEquals(v, listOf(h.threadDiaMinMm, h.threadDiaMaxMm, h.tpiMin, h.tpiMax, h.openingMovementMm), k)
            assertTrue(h.refs.isNotEmpty(), k)
        }
        assertEquals(73.0, RollHeadCatalog.head("F233400")?.maxRollLengthMm)
        // F RN 계열은 앱이 고르는 머리가 아니고 열림 행정 칸이 없다
        assertNull(RollHeadCatalog.head("F3-1RN"))
        assertNull(RollHeadCatalog.data.heads.single { it.key == "F3-1RN" }.openingMovementMm)
        // 선택표 최대 전조 길이: F23 3/4 · 7/8 19, F34 7/8 · 1 24, F233400 5/8~1 73, 그 밖 무제한(3/4 · F34, 7/16 · F2, 미터)
        fun len(h: String, spec: ThreadSpec) = RollHeadCatalog.maxRollLength(RollHeadCatalog.head(h)!!, spec)
        fun un(size: String) = ThreadTable.UN.single { it.size == size && it.tpi == 20 }
        assertEquals(19.0, len("F23", un("3/4")))
        assertEquals(19.0, len("F23", un("7/8")))
        assertNull(len("F23", un("5/8")))
        assertEquals(24.0, len("F34", un("7/8")))
        assertEquals(24.0, len("F34", un("1")))
        assertNull(len("F34", un("3/4")))
        assertEquals(73.0, len("F233400", un("5/8")))
        assertEquals(73.0, len("F233400", ThreadTable.METRIC.first { it.size == "M20" }))
        assertNull(len("F2", un("7/16")))
        for (r in RollHeadCatalog.data.rollLengths) assertTrue(r.refs.isNotEmpty() && RollHeadCatalog.head(r.head) != null, r.head)
        // F233400 은 모델 이름(주문 번호가 아님)
        assertEquals(true, RollHeadCatalog.data.modelNaming.isModelName)
    }

    @Test
    fun processValues() {
        val d = RollHeadCatalog.data
        assertEquals(0.03, d.blankDia.offsetMm)
        assertEquals("blank", d.speedBasisDia.value)
        assertEquals(0.95, d.feed.ratioRec)
        assertEquals(true, d.stainless.splits304vs316)
        val s316 = assertNotNull(RollHeadCatalog.speed("1.4401"))
        assertEquals(30.0 to 50.0, s316.vMinMpm to s316.vMaxMpm)
        val s304 = assertNotNull(RollHeadCatalog.speed("1.4301"))
        assertEquals(35.0 to 55.0, s304.vMinMpm to s304.vMaxMpm)
        // 모든 속도 줄이 범위만(권장 한 값 없음)
        assertTrue(d.speeds.all { it.vRecMpm == null })
    }

    /** 요령 33줄: 묶음별 수, 모든 출처 id 가 출처 목록에 있음, 다른 회사 자료 없음, SETUP 은 차례 1~9 */
    @Test
    fun tips() {
        val d = RollHeadCatalog.data
        val counts = RollTipGroup.entries.associateWith { RollHeadCatalog.tips(it).size }
        assertEquals(
            mapOf(
                RollTipGroup.RPM_UP to 3, RollTipGroup.RPM_DOWN to 3, RollTipGroup.RATIO_UP to 1, RollTipGroup.RATIO_DOWN to 0,
                RollTipGroup.BLANK_BIG to 4, RollTipGroup.BLANK_SMALL to 3, RollTipGroup.SETUP to 9, RollTipGroup.OTHER to 10,
            ),
            counts,
        )
        val ids = d.sources.map { it.id }.toSet()
        for (t in d.tips) {
            assertTrue(t.refs.isNotEmpty(), t.symptomKo)
            for (r in t.refs) assertTrue(r.sourceId in ids, "${r.sourceId} — ${t.symptomKo}")
            assertFalse(t.secondary, t.symptomKo)
        }
        assertTrue(d.sources.none { it.sourceType == "secondary" })
        assertEquals((1..9).toList(), RollHeadCatalog.tips(RollTipGroup.SETUP).map { it.order })
        // 머리 · 속도 줄의 출처도 목록 안
        for (h in d.heads) for (r in h.refs) assertTrue(r.sourceId in ids, "${h.model} ${r.sourceId}")
        for (s in d.speeds) for (r in s.refs) assertTrue(r.sourceId in ids, "${s.material} ${r.sourceId}")
        assertNotNull(RollHeadCatalog.source("FET_OI_F1F3_EVO"))
        assertNull(RollHeadCatalog.source("NONE"))
    }

    /**
     * 우리가 쓴 한국어 글(검토 V3-1-2 · V1-1-3 · V4-1-4): 화면에 나가는 요령 글 · 시작 · 끝 멈춤 · 그 밖 한계는 소수점이 점이다(출처 원문을 옮긴 quote 는 원문 그대로).
     * 열림 행정 a 의 4 mm 머리는 F3 · F34 둘(설명서 Tab. 8 '3 → 4'), F 형 EVO 고정 전용 목록에도 F3. 조사 JSON 에는 회사 맥락(회사 나사 · 회사 머리 · 일곱 규격)을 적지 않는다
     */
    @Test
    fun koreanTexts() {
        val d = RollHeadCatalog.data
        val decimalComma = Regex("\\d,\\d")
        val texts = d.tips.flatMap { listOfNotNull(it.symptomKo, it.causeKo, it.remedyKo) } +
            listOfNotNull(d.start.ruleKo, d.endDwell.ruleKo) + d.otherLimits.map { it.textKo }
        for (t in texts) assertFalse(decimalComma.containsMatchIn(t), t)
        assertTrue(d.start.ruleKo!!.contains("F2·F23·F233400 3 mm, F3·F34 4 mm"))
        assertTrue(d.endDwell.ruleKo!!.contains("F2·F23·F233400 3 mm, F3·F34 4 mm"))
        assertTrue(d.otherLimits.first().textKo.contains("(F2·F23·F233400·F3·F34)"))
        val json = RollHeadCatalogJson.text()
        for (w in listOf("회사 나사", "회사 머리", "일곱 규격")) assertFalse(w in json, w)
        // 검토 V1-2-2: 시작 규칙의 20산 최소 언더컷 g 에 크기 3 칸(설명서 Tab. 5 — 1K 1.916, 2K 3.187)도
        assertTrue(d.start.ruleKo!!.contains("1.916 mm('3' 칸)") && d.start.ruleKo!!.contains("2K 3.227 / 3.187 / 3.267 mm"), d.start.ruleKo)
        // 검토 V1-2-1: 못 찾은 칸 'R2 blankDia.rows' — d2 표(2A, UNC 인쇄 408 · UNF 409 · UNEF 410)의 20산 여덟(13/16 · 15/16 도), 표에 없는 UN 계열에 11/16-20 도
        val blankRows = d.notFound.single { it.field == "R2 blankDia.rows" }.reason
        assertFalse("다섯 규격" in blankRows, blankRows)
        for (w in listOf("1/4 UNC", "13/16", "15/16", "11/16-20")) assertTrue(w in blankRows, w)
        // 검토 V1-3-1: 찾은 범위 · 읽은 쪽에 1/4-20 UNC 표(인쇄 408 = PDF 410)도 — 'UNC 인쇄 408' 을 들면서 범위는 409 부터라 앞뒤가 어긋났다
        assertTrue("카탈로그 부록(인쇄 408-421)" in blankRows && "UNC 인쇄 408" in blankRows, blankRows)
        assertFalse("409-421" in blankRows, blankRows)
        assertTrue("206, 408-410, 413-414, 418-421 (PDF 17-24, 90, 112, 208, 410-412, 415-416, 420-423쪽)" in json, "meta pages R2")
        // 검토 V3-2-2 · V3-2-3 · V3-2-4: 화면 · 도움말에 나가는 요령 글 — 설명서 이름은 줄표 'F1–F3'(붙임표 'F1-F3' 은 keepDash 가 못 묶어 'F1- / F3' 로 갈렸다),
        // 증상에 출처 이름을 다시 적지 않음(끝 출처 괄호와 겹침), 코드 이름(BLANK_BIG 등) 대신 화면 묶음 이름
        val codeName = Regex("[A-Z]+_[A-Z]+")
        for (t in d.tips) {
            for (x in listOfNotNull(t.symptomKo, t.causeKo, t.remedyKo)) {
                assertFalse("F1-F3" in x, x)
                assertFalse(codeName.containsMatchIn(x), x)
            }
            assertFalse("설명서" in t.symptomKo, t.symptomKo)
        }
    }
}
