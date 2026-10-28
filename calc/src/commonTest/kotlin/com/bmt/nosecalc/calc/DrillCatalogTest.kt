package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 조회 규칙. 손 계산은 원본 JSON 을 파이썬으로 따로 읽어 구했다. 원본과 모든 행이 같은지는 jvmTest 의 DrillCatalogJsonTest */
class DrillCatalogTest {
    private fun near(e: Double?, a: Double?, what: String) {
        if (e == null) return assertNull(a, "$what 은 비어야 한다")
        assertNotNull(a, "$what 이 비었다")
        assertTrue(abs(e - a) <= 1e-9 * maxOf(1.0, abs(e)), "$what: 기대 $e, 실제 $a")
    }

    private fun span(e: Span3?, a: Span3?, what: String) {
        if (e == null) return assertNull(a, "$what 은 비어야 한다")
        assertNotNull(a, "$what 이 비었다")
        near(e.min, a.min, "$what 최소"); near(e.rec, a.rec, "$what 권장"); near(e.max, a.max, "$what 최대")
    }

    private fun row(maker: String, series: String, material: String): Triple<DrillMaker, DrillSeries, Int> {
        val m = assertNotNull(DrillCatalog.maker(maker))
        val s = m.series.first { it.series.startsWith(series) }
        val i = s.conditions.indexOfFirst { it.material.startsWith(material) }
        assertTrue(i >= 0, "$maker $series $material 행이 없다")
        return Triple(m, s, i)
    }

    private fun look(maker: String, series: String, material: String, d: Double?): DrillResult {
        val (m, s, i) = row(maker, series, material)
        return DrillCatalog.lookup(m, s, i, d)
    }

    @Test
    fun makersAndCounts() {
        assertEquals(listOf("sandvik", "iscar", "mitsubishi", "yg1", "korloy"), DrillCatalog.makers.map { it.key })
        assertEquals(listOf("Sandvik", "ISCAR", "Mitsubishi", "YG-1", "KORLOY"), DrillCatalog.makers.map { it.label })
        assertEquals(35, DrillCatalog.makers.sumOf { it.series.size })
        assertEquals(519, DrillCatalog.makers.sumOf { m -> m.series.sumOf { it.conditions.size } })
        // 모든 행: ISO 문자가 6군 가운데 하나, 출처 id 가 그 메이커 출처에 있다, 구간이 오름차순 · 겹치지 않음
        for (m in DrillCatalog.makers) for (s in m.series) for (c in s.conditions) {
            c.iso
            assertNotNull(m.source(c.sourceId), "${m.key} ${c.sourceId}")
            c.feeds.zipWithNext { a, b -> assertTrue(a.dMin <= a.dMax && a.dMax < b.dMin, "${s.series} ${c.material}: $a $b") }
            // Vc 가 있거나 회전수 표 둘 중 하나(둘 다 없는 행은 ISCAR SOLIDDRILL No.4 · No.5 — Vc 칸이 비어 인쇄됨)
            val hasVc = c.vcMin != null || c.vcRec != null || c.vcMax != null
            assertTrue(hasVc != c.rpmTable || (m.key == "iscar" && (c.material.startsWith("No.4 ") || c.material.startsWith("No.5 "))), c.material)
        }
    }

    /** Sandvik 860-GM P1.1 Ø10: Vc 120 · 145 · 170, n 3819.72 · 4615.49 · 5411.27, f 0.20 · 0.25 · 0.30(Ø10 열), vf 763.94 · 1153.87 · 1623.38 */
    @Test
    fun sandvikHandNumbers() {
        val r = look("sandvik", "CoroDrill 860", "Unalloyed steel: C = 0.05", 10.0)
        assertTrue(r.warnings.isEmpty(), "${r.warnings}")
        span(Span3(120.0, 145.0, 170.0), r.vc, "Vc")
        assertFalse(r.vcConverted); assertFalse(r.nFromTable)
        span(Span3(3819.7186342054883, 4615.493349664965, 5411.268065124442), r.n, "n")
        span(Span3(0.2, 0.25, 0.3), r.f, "f")
        span(Span3(763.9437268410976, 1153.8733374162412, 1623.3804195373325), r.vf, "vf")
        assertEquals(FeedBand(10.0, 10.0, 0.2, 0.25, 0.3), r.band)
        assertEquals("SRT2020", r.source?.id)
        assertEquals("B70(vc)+B71(fn), PDF 270/271", r.page)
    }

    /** 표가 Ø4 · Ø6 열뿐인데 Ø5: 이송은 비우고(보간 안 함) 앞뒤 열을 돌려준다. Vc · n 은 나온다 */
    @Test
    fun betweenColumnsIsNotInterpolated() {
        val r = look("sandvik", "CoroDrill 860", "Unalloyed steel: C = 0.05", 5.0)
        assertEquals(listOf(DrillWarning.NOT_IN_FEED_TABLE), r.warnings)
        assertNull(r.band); assertNull(r.f); assertNull(r.vf)
        assertEquals(4.0, r.below?.dMax); assertEquals(6.0, r.above?.dMin)
        span(Span3(7639.437268410977, 9230.98669932993, 10822.536130248884), r.n, "n Ø5")
        // i-ONE: 11.99 · 12.09 는 구간 안, 12.05 는 사이(표에 없음). 경계는 그 구간에 든다
        val inA = look("yg1", "i-ONE", "Non-alloy steel (VDI1)", 11.99)
        assertEquals(11.99, inA.band?.dMax)
        val gap = look("yg1", "i-ONE", "Non-alloy steel (VDI1)", 12.05)
        assertEquals(listOf(DrillWarning.NOT_IN_FEED_TABLE), gap.warnings)
        assertEquals(11.99, gap.below?.dMax); assertEquals(12.09, gap.above?.dMin)
        assertEquals(12.09, look("yg1", "i-ONE", "Non-alloy steel (VDI1)", 12.09).band?.dMin)
    }

    /** 권장값이 없으면 만들지 않는다: i-ONE Vc 80~135(권장 없음) Ø13 → n 최소 · 최대만, vf = 0.18 × n최소 · 0.33 × n최대 */
    @Test
    fun noRecommendedIsNotInvented() {
        val r = look("yg1", "i-ONE", "Non-alloy steel (VDI1)", 13.0)
        span(Span3(80.0, null, 135.0), r.vc, "Vc")
        span(Span3(1958.830068823327, null, 3305.5257411393645), r.n, "n")
        span(Span3(0.18, null, 0.33), r.f, "f")
        span(Span3(352.58941238819887, null, 1090.8234945759903), r.vf, "vf")
        // YG-1 DREAM: Vc 권장 110 하나뿐 · 이송은 범위 → vf = 범위 × n권장
        val d = look("yg1", "DREAM DRILLS-GENERAL", "Non-alloy steel (VDI2)", 8.0)
        span(Span3(null, 110.0, null), d.vc, "Vc")
        span(Span3(null, 4376.760935027122, null), d.n, "n")
        span(Span3(787.816968304882, null, 1050.4226244065094), d.vf, "vf")
    }

    /** 회전수 표(Mitsubishi MVE 연강 Ø6.3): n 4000 그대로, Vc 는 환산 79.168, vf = f × 4000 */
    @Test
    fun rpmTableRows() {
        val r = look("mitsubishi", "MVE", "Mild Steel", 6.3)
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.nFromTable); assertTrue(r.vcConverted)
        span(Span3(null, 4000.0, null), r.n, "n")
        span(Span3(null, PI * 6.3 * 4000 / 1000, null), r.vc, "Vc 환산")
        near(79.16813487046278, r.vc?.rec, "Vc 환산 손 계산")
        span(Span3(0.13, 0.2, 0.26), r.f, "f")
        span(Span3(520.0, 800.0, 1040.0), r.vf, "vf")
        // 표에 없는 직경은 회전수도 없다
        val gap = look("mitsubishi", "MVE", "Mild Steel", 5.5)
        assertEquals(listOf(DrillWarning.NOT_IN_FEED_TABLE), gap.warnings)
        assertNull(gap.n); assertNull(gap.vc); assertFalse(gap.nFromTable); assertFalse(gap.vcConverted)
        assertEquals(5.0, gap.below?.dMin); assertEquals(6.3, gap.above?.dMin)
    }

    /** 같은 재질이 직경 구간마다 다른 행(Sandvik 870 P2.5 [Ø10-20.99] · [Ø21-33]): 밖이면 멈추고 맞는 행을 알려 준다 */
    @Test
    fun outsideRowPointsToOtherRow() {
        val (m, s, i) = row("sandvik", "CoroDrill 870", "Low alloy steel: Hardened and tempered P2.5.Z.HT / 저합금강: 경화처리 및 뜨임처리 [Ø10")
        val r = DrillCatalog.lookup(m, s, i, 25.0)
        assertEquals(listOf(DrillWarning.OUTSIDE_ROW), r.warnings)
        assertNull(r.vc); assertNull(r.n); assertNull(r.f)
        assertEquals(1, r.alternatives.size)
        val other = s.conditions[r.alternatives.single()]
        assertTrue(other.material.contains("[Ø21-33"), other.material)
        val ok = DrillCatalog.lookup(m, s, r.alternatives.single(), 25.0)
        assertTrue(ok.warnings.isEmpty())
        span(Span3(50.0, 75.0, 100.0), ok.vc, "Vc Ø21-33 행")
        // Mitsubishi TAF 연강: Ø15–56 의 U1 행에서 Ø13 → Ø12–14.5 행 하나, 거꾸로 Ø20 → 브레이커 U1 · U2 · U3 세 행
        val (tm, ts, ti) = row("mitsubishi", "TAFS", "Mild Steel / 연강 [Ø15–56, 브레이커 U1]")
        val t13 = DrillCatalog.lookup(tm, ts, ti, 13.0)
        assertEquals(listOf(DrillWarning.OUTSIDE_ROW), t13.warnings)
        assertTrue(ts.conditions[t13.alternatives.single()].material.contains("[Ø12–14.5"))
        val small = ts.conditions.indexOfFirst { it.material.startsWith("Mild Steel / 연강 [Ø12–14.5") }
        val t20 = DrillCatalog.lookup(tm, ts, small, 20.0)
        assertEquals(3, t20.alternatives.size)
        assertTrue(t20.alternatives.all { ts.conditions[it].material.startsWith("Mild Steel / 연강 [Ø15–56, 브레이커 U") })
        // 같은 재질의 다른 행이 없으면 빈 목록(860-GM 은 직경으로 나뉜 행이 없다)
        assertTrue(look("sandvik", "CoroDrill 860", "Unalloyed steel: C = 0.05", 18.0).alternatives.isEmpty())
    }

    /** 시리즈 주문 범위(860-GM Ø3~16) 밖이지만 절삭표에 Ø20 열이 있으면 값과 함께 경고 */
    @Test
    fun outsideSeriesWarns() {
        val r = look("sandvik", "CoroDrill 860", "Low alloy steel: Hardened and tempered P2.5", 20.0)
        assertEquals(listOf(DrillWarning.OUTSIDE_SERIES), r.warnings)
        span(Span3(0.23, 0.3, 0.37), r.f, "f Ø20")
        span(Span3(1273.2395447351628, 1591.5494309189535, 1909.8593171027442), r.n, "n Ø20")
        // KORLOY MSD Plus: 라인업 Ø2.5~ 인데 표 첫 칸은 Ø1~4
        val k = look("korloy", "MSD Plus (MSDPH", "Low carbon steel", 2.0)
        assertEquals(listOf(DrillWarning.OUTSIDE_SERIES), k.warnings)
        span(Span3(0.1, null, 0.15), k.f, "f")
    }

    /** 이송 값이 없는 행(TAFL)과 Vc 칸이 빈 행(ISCAR No.4) */
    @Test
    fun missingColumns() {
        val t = look("mitsubishi", "TAFL", "Mild Steel", 20.0)
        assertEquals(listOf(DrillWarning.NO_FEED_IN_TABLE), t.warnings)
        span(Span3(100.0, 140.0, 200.0), t.vc, "Vc")
        span(Span3(1591.5494309189535, 2228.169203286535, 3183.098861837907), t.n, "n")
        assertNull(t.f); assertNull(t.vf)
        // 이송이 없는 행의 범위 = 시리즈 범위(Ø16~56)
        assertEquals(listOf(DrillWarning.OUTSIDE_ROW), look("mitsubishi", "TAFL", "Mild Steel", 12.0).warnings)

        val v = look("iscar", "SOLIDDRILL SCD-ACP3N", "No.4 ", 4.0)
        assertEquals(listOf(DrillWarning.NO_VC_IN_TABLE), v.warnings)
        assertNull(v.vc); assertNull(v.n); assertNull(v.vf)
        span(Span3(0.1, null, 0.2), v.f, "f")
    }

    @Test
    fun badAndMissingDiameter() {
        assertEquals(listOf(DrillWarning.MISSING_DIAMETER), look("sandvik", "CoroDrill 860", "Unalloyed", null).warnings)
        for (d in listOf(0.0, -3.0, Double.NaN, Double.POSITIVE_INFINITY, MAX_MM + 1)) {
            val r = look("sandvik", "CoroDrill 860", "Unalloyed", d)
            assertEquals(listOf(DrillWarning.BAD_INPUT), r.warnings, "Ø$d")
            assertNull(r.vc)
        }
    }

    /** 직접 입력: Ø10 · Vc100 · f0.2 → n 3183.10 · vf 636.62 */
    @Test
    fun manualInput() {
        val r = DrillCatalog.manual(10.0, 100.0, 0.2)
        assertTrue(r.warnings.isEmpty())
        span(Span3(null, 3183.098861837907, null), r.n, "n")
        span(Span3(null, 636.6197723675814, null), r.vf, "vf")
        assertNull(r.source)
        val noF = DrillCatalog.manual(10.0, 100.0, null)
        assertEquals(listOf(DrillWarning.MISSING_FEED), noF.warnings)
        assertNotNull(noF.n); assertNull(noF.vf)
        assertEquals(listOf(DrillWarning.MISSING_DIAMETER), DrillCatalog.manual(null, 100.0, 0.2).warnings)
        assertEquals(listOf(DrillWarning.MISSING_VC), DrillCatalog.manual(10.0, null, 0.2).warnings)
        assertEquals(listOf(DrillWarning.BAD_INPUT), DrillCatalog.manual(10.0, -1.0, 0.2).warnings)
        assertEquals(listOf(DrillWarning.BAD_INPUT), DrillCatalog.manual(10.0, 100.0, 0.0).warnings)
    }

    /**
     * 모든 행 · 모든 구간: 구간 안의 직경(양 끝 · 가운데)이면 f 가 그 구간 값 그대로, Vc 가 행 값 그대로(회전수 표면 n = 표 회전수),
     * vf = f × n 의 같은 칸.
     */
    @Test
    fun everyBandReturnsItsOwnValues() {
        var checked = 0
        for (m in DrillCatalog.makers) for (s in m.series) for ((i, c) in s.conditions.withIndex()) for (b in c.feeds) {
            for (d in listOf(b.dMin, (b.dMin + b.dMax) / 2, b.dMax)) {
                val r = DrillCatalog.lookup(m, s, i, d)
                val what = "${m.key} ${s.series} ${c.material} Ø$d"
                assertEquals(b, r.band, what)
                assertTrue(DrillWarning.NOT_IN_FEED_TABLE !in r.warnings && DrillWarning.OUTSIDE_ROW !in r.warnings, what)
                assertEquals(Span3.of(b.fMin, b.fRec, b.fMax), r.f, what)
                if (c.rpmTable) {
                    assertEquals(b.rpm?.toDouble(), r.n?.rec, what)
                    near(PI * d * b.rpm!! / 1000, r.vc?.rec, what)
                } else {
                    assertEquals(Span3.of(c.vcMin, c.vcRec, c.vcMax), r.vc, what)
                    near(c.vcRec?.let { 1000 * it / (PI * d) }, r.n?.rec, "$what n")
                }
                val n = r.n
                near(if (b.fMin != null && n != null) (n.min ?: n.rec)?.let { b.fMin * it } else null, r.vf?.min, "$what vf 최소")
                near(if (b.fRec != null && n?.rec != null) b.fRec * n.rec else null, r.vf?.rec, "$what vf 권장")
                near(if (b.fMax != null && n != null) (n.max ?: n.rec)?.let { b.fMax * it } else null, r.vf?.max, "$what vf 최대")
                checked++
            }
        }
        assertEquals(3 * 3052, checked)
    }

    /** 재료분석 탭의 S · H 예가 기대는 행이 자료에 있다 */
    @Test
    fun materialNotesMatchCatalogRows() {
        fun has(maker: String, iso: String, text: String, hardness: String? = null) = assertTrue(
            DrillCatalog.maker(maker)!!.series.any { s ->
                s.conditions.any { it.isoGroup == iso && it.material.contains(text) && (hardness == null || it.hardness == hardness) }
            },
            "$maker $iso $text $hardness",
        )
        has("mitsubishi", "S", "Inconel718")
        has("mitsubishi", "H", "Hardened Steel (40─55HRC)")
        has("sandvik", "H", "H1.3.Z.HA", "47-60 HRC")
        has("iscar", "H", "No.38 hardened steel", "55 HRC")
        has("iscar", "S", "No.37 titanium alpha+beta", "Rm 1050 N/mm²")
        has("yg1", "S", "VDI37")
        // 페라이트/마르텐사이트계 스테인리스(P5.0)를 Sandvik 870 표는 M 칸에
        has("sandvik", "M", "P5.0.Z.AN")
    }
}
