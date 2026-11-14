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
}
