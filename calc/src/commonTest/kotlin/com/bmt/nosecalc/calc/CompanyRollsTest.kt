package com.bmt.nosecalc.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 회사 전조 롤 표(CompanyRolls). 머리 · 롤 짝은 결정 D1 · D2 의 손 표, 소재 꼴별 지름 · S 는 원문 확인 전이라 모두 null 을 못 박는다
 * (확인한 뒤에는 이 시험의 손 표를 확인 결과로 바꾼다).
 */
class CompanyRollsTest {
    /** 결정 D2: 규격 → 머리 · 롤(첫째 = 처음 값). D1 의 X 칸(F23 · AT10, F233400 · AT03, F23 · AT11)은 없다 */
    private val expected = listOf(
        "7/16" to listOf("F23 AT09", "F2 AT06"),
        "1/2" to listOf("F2 AT06", "F23 AT09"),
        "9/16" to listOf("F23 AT01"),
        "5/8" to listOf("F23 AT01"),
        "3/4" to listOf("F34 AT08", "F233400 AT02"),
        "7/8" to listOf("F34 AT07"),
        "1" to listOf("F34 AT05"),
    )

    @Test
    fun headsAndRolls() {
        assertEquals(expected.map { it.first }, CompanyRolls.ALL.map { it.size })
        for ((size, heads) in expected) {
            val r = CompanyRolls.ALL.single { it.size == size }
            assertEquals(heads, r.heads.map { "${it.model} ${it.roll}" }, size)
            assertEquals(heads.first(), "${r.defaultHead.model} ${r.defaultHead.roll}", size)
        }
        val all = CompanyRolls.ALL.flatMap { r -> r.heads.map { "${it.model} ${it.roll}" } }
        for (x in listOf("F23 AT10", "F233400 AT03", "F23 AT11")) assertTrue(x !in all, "X 칸 $x 이 들어 있다")
        // 같은 규격에 같은 짝이 두 번 들지 않는다
        CompanyRolls.ALL.forEach { assertEquals(it.heads.size, it.heads.toSet().size, it.size) }
    }

    /** 원문 확인 전 — 지름 · S · 개수 모두 null */
    @Test
    fun companyValuesPending() {
        for (r in CompanyRolls.ALL) for (s in RollStock.entries) {
            val v = r.stock(s)
            assertNull(v.blankDia, "${r.size} $s")
            assertNull(v.blankCount, "${r.size} $s")
            assertNull(v.rpm, "${r.size} $s")
            assertNull(v.rpmCount, "${r.size} $s")
        }
        assertEquals(1.22, CompanyRolls.FEED_20TPI)
    }

    /** 열쇠는 (크기, 20산): 계열이 UNF · UN · UNEF 로 섞여도 일곱 모두 ThreadTable 줄 하나를 찾는다 */
    @Test
    fun findsByTpiAndSize() {
        val series = CompanyRolls.specs.map { it.series }
        assertEquals(
            listOf(ThreadSeries.UNF, ThreadSeries.UNF, ThreadSeries.UN, ThreadSeries.UN, ThreadSeries.UNEF, ThreadSeries.UNEF, ThreadSeries.UNEF),
            series,
        )
        for (spec in CompanyRolls.specs) {
            assertEquals(20, spec.tpi)
            assertSame(CompanyRolls.ALL.single { it.size == spec.size }, CompanyRolls.of(spec))
        }
        // 같은 크기 다른 산수 · 미터 · 표 밖 20산은 없음
        assertNull(CompanyRolls.of(ThreadTable.UN.single { it.size == "3/4" && it.tpi == 10 }))
        assertNull(CompanyRolls.of(ThreadTable.METRIC.first { it.size == "M12" }))
        assertNull(CompanyRolls.of(ThreadTable.UN.single { it.size == "1-1/16" && it.tpi == 20 }))
        assertNull(CompanyRolls.of(null))
    }
}
