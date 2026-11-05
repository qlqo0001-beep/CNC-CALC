package com.bmt.nosecalc.calc

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 다이스 헤드 카탈로그 조회 규칙. 기대값은 원본 docs/research/die-heads.json 을 따로(파이썬으로) 읽어 센 수 · 칸이다.
 * 원본의 모든 값이 모델에 그대로 있는지는 jvmTest 의 DieHeadCatalogJsonTest.
 */
class DieHeadCatalogTest {
    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
    private fun m(size: String, pitch: Double) = ThreadTable.METRIC.single { it.size == size && it.pitch == pitch }

    private fun sizes(spec: ThreadSpec) = DieHeadCatalog.chasers(spec).map { it.maker.key to it.chaser.chaserSize }

    @Test
    fun makersAndSources() {
        assertEquals(listOf("wagner", "landis", "taco"), DieHeadCatalog.makers.map { it.key })
        assertEquals(listOf("Wagner", "Landis", "TACO"), DieHeadCatalog.makers.map { it.label })
        assertEquals(21, DieHeadCatalog.data.sources.size)
        assertEquals("manufacturer", DieHeadCatalog.source("WAG_ZR26I")?.sourceType)
        assertEquals("Standard Stock & Non Stock Chasers (2쪽)", DieHeadCatalog.source("LAN_CH")?.title)
        assertNull(DieHeadCatalog.source("NONE"))
        assertNull(DieHeadCatalog.maker("fette"))
        // 모든 줄의 출처 id 가 출처 목록에 있다
        for (mk in DieHeadCatalog.makers) {
            val ids = mk.speeds.map { it.sourceId } + mk.chasers.map { it.sourceId } + mk.chasers.flatMap { c -> c.sourceRefs.orEmpty().map { it.sourceId } } +
                mk.chaserHolders.orEmpty().map { it.sourceId } + mk.opening.sourceRefs.map { it.sourceId } + mk.feedRule.sourceRefs.map { it.sourceId } +
                mk.floatTravel.items.map { it.sourceId }
            for (id in ids) assertNotNull(DieHeadCatalog.source(id), "${mk.key} $id")
        }
        // 홀더 표: Wagner 5표 57줄 · Landis 7표 105줄 · TACO 없음
        assertEquals(listOf(13, 10, 11, 9, 14), DieHeadCatalog.maker("wagner")!!.chaserHolders!!.map { it.rows.size })
        assertEquals(listOf(10, 10, 18, 24, 16, 17, 10), DieHeadCatalog.maker("landis")!!.chaserHolders!!.map { it.rows.size })
        assertNull(DieHeadCatalog.maker("taco")!!.chaserHolders)
        val first = DieHeadCatalog.maker("wagner")!!.chaserHolders!!.first().rows.first()
        assertEquals("ZR12-A5,00", first.designation)
        assertEquals(listOf("Nr. 1-64", "Nr. 10-24", "1/4-20"), first.unc)
        assertEquals(5.0, first.holderAngleDeg)
        assertEquals("5/8\" 5F", DieHeadCatalog.maker("landis")!!.chaserHolders!!.first().head)
    }

    /**
     * 절삭 속도: Wagner 24줄(ZR 26-I 표 11 · Z39-2 표 12 · 관 한 줄) 가운데 사다리꼴 6줄을 뺀 18줄. Landis · TACO 는 표가 없다.
     * 권장값은 두 표 모두 없다(vcRec null 그대로 — 가운데 값을 만들지 않음). ISO 군은 출처에 없어(모두 null) 군을 줘도 같은 18줄
     */
    @Test
    fun speedsKeepSourceValues() {
        val all = DieHeadCatalog.speeds()
        assertEquals(18, all.size)
        assertTrue(all.all { it.maker.key == "wagner" })
        assertTrue(all.none { it.speed.threadType?.startsWith("Trapez") == true })
        assertEquals(24, DieHeadCatalog.maker("wagner")!!.speeds.size)
        assertTrue(all.all { it.speed.vcRec == null && it.speed.isoGroup == null && it.speed.iso == null })
        assertEquals(all, DieHeadCatalog.speeds("wagner"))
        assertEquals(all, DieHeadCatalog.speeds(iso = IsoGroup.P))
        assertEquals(all, DieHeadCatalog.speeds("wagner", IsoGroup.N))
        assertTrue(DieHeadCatalog.speeds("landis").isEmpty())
        assertTrue(DieHeadCatalog.speeds("taco").isEmpty())

        val s0 = all.first().speed
        assertEquals("Machining steel", s0.material)
        assertEquals("쾌삭강", s0.materialKo)
        assertEquals(15.0, s0.vcMin)
        assertEquals(50.0, s0.vcMax)
        assertEquals("WAG_ZR26I", s0.sourceId)
        assertEquals("29 (14.1, Table 4)", s0.page)
        // Z39-2 일반 삼각 나사 줄(사다리꼴 짝은 빠짐): 쾌삭강 10–20, 황동 · 청동 15–(최대 없음 — '15 und höher')
        val z = all.filter { it.speed.sourceId == "WAG_Z392" }.map { Triple(it.speed.material, it.speed.vcMin, it.speed.vcMax) }
        assertEquals(
            listOf(
                Triple("Automatenstahl", 10.0, 20.0), Triple("C 35", 10.0, 15.0), Triple("C 60", 3.0, 6.0), Triple("VCMö 135", 2.0, 4.0),
                Triple("VA-Stähle", 3.0, 5.0), Triple("Messing und Bronze", 15.0, null), Triple("Rohre und Fittings", 10.0, 20.0),
            ),
            z,
        )
    }

    /** UN: 나사 꼴 "UN" 체이서 가운데 산수 목록에 그 산수가 있는 줄 — 계열(UNC · UNF · UNEF · 20UN)과 상관없다 */
    @Test
    fun unChasersByTpi() {
        val twenty = listOf("5/8 x 1-1/8", "3/4 x 1-5/8", "15/16 x 2-1/8", "1-3/64 x 3", "1-3/64 x 4").map { "landis" to it }
        assertEquals(twenty, sizes(un("1/2", 20)))
        assertEquals(twenty, sizes(un("3/4", 20)))
        assertEquals(ThreadSeries.UNEF, un("3/4", 20).series)
        assertEquals(twenty, sizes(un("3/8", 20)))
        assertEquals(ThreadSeries.UN, un("3/8", 20).series)
        // #10-32: 32 가 있는 것은 앞 셋뿐(1-3/64 x 3 은 8~24, x 4 는 8~28)
        assertEquals(listOf("5/8 x 1-1/8", "3/4 x 1-5/8", "15/16 x 2-1/8").map { "landis" to it }, sizes(un("#10", 32)))
        // 1-1/2-6 UNC: 1-7/64 x 4(6 · 7) · 1-1/4 x 2-3/4(4-1/2 · 5 · 5-1/2 · 6)
        assertEquals(listOf("1-7/64 x 4", "1-1/4 x 2-3/4").map { "landis" to it }, sizes(un("1-1/2", 6)))

        val hit = DieHeadCatalog.chasers(un("1/2", 20)).first().chaser
        assertEquals("UN", hit.threadForm)
        assertEquals(listOf("20° Long", "20° Short", "30° Spec. Short", "45° No Throat"), hit.throats)
        assertEquals(listOf("12", "13", "16", "18", "20", "24", "28", "32"), hit.tpis)
        assertEquals(JsonPrimitive(4), hit.setOf)
        assertEquals("LAN_CH", hit.sourceId)
        assertEquals("1쪽", hit.page)
        assertEquals("2쪽", DieHeadCatalog.chasers(un("1/2", 20)).last().chaser.page)
    }

    /** 미터: 나사 꼴 "mm (미터)" 체이서 가운데 피치 목록("1.50" 처럼 글)에 그 피치가 있는 줄 */
    @Test
    fun metricChasersByPitch() {
        assertEquals(
            listOf("5/8 x 1-1/8", "3/4 x 1-5/8", "15/16 x 2-1/8", "1-3/64 x 3", "1-3/64 x 4").map { "landis" to it },
            sizes(m("M10", 1.5)),
        )
        assertEquals(listOf("landis" to "1-7/64 x 4"), sizes(m("M36", 4.0)))
        assertEquals(listOf("landis" to "1-1/2 x 5"), sizes(m("M48", 5.0)))
        assertEquals(listOf("landis" to "1-1/2 x 5"), sizes(m("M64", 6.0)))
        assertTrue(sizes(m("M1", 0.25)).isEmpty())
        // Wagner 체이서 줄은 표기 예 글뿐이라(피치 목록 없음) 맞추지 않는다, TACO 는 나사 꼴이 비어 있다
        val w = DieHeadCatalog.maker("wagner")!!.chasers
        assertTrue(w.all { it.tpis.isEmpty() && it.pitches.isEmpty() })
        assertTrue(DieHeadCatalog.maker("taco")!!.chasers.all { it.threadForm == null })
    }

    /** 이송 규칙 · 플로트 — 안내(이송 = 피치)의 근거와, 고정형 열림 행정이 없다는 것 */
    @Test
    fun feedRuleAndFloat() {
        assertEquals(listOf(1.0, 1.0, null), DieHeadCatalog.makers.map { it.feedRule.feedToLead })
        val wag = DieHeadCatalog.maker("wagner")!!.floatTravel
        assertTrue(wag.items.isEmpty())
        assertNull(wag.value)
        val lan = DieHeadCatalog.maker("landis")!!.floatTravel.items.single()
        assertEquals("in", lan.unit)
        assertEquals(JsonPrimitive("1/32"), lan.values["32-14P"])
        val taco = DieHeadCatalog.maker("taco")!!.floatTravel.items.single()
        assertEquals("mm", taco.unit)
        assertEquals(JsonPrimitive(22), taco.values["DR 3/8\""])
    }
}
