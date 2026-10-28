package com.bmt.nosecalc.calc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * 드릴 카탈로그(재료·절삭조건의 드릴링 탭). 자료는 calc/catalog/drill-catalog.json — docs/research/drill-catalog.json(5사 공식 카탈로그,
 * 출처 · 쪽 대조 끝)을 값 그대로 옮긴 것이고, 빌드가 Kotlin 문자열([DrillCatalogJson])로 만들어 넣는다. 두 파일이 같은지,
 * 모든 행 · 칸이 원본 JSON 과 같은지는 jvmTest 의 DrillCatalogJsonTest 가 본다.
 *
 * 조회: 메이커 → 시리즈 → ISO/재질(행) → 직경 → Vc(최소 · 권장 · 최대) · n · f · vf + 출처.
 *  - f 는 **그 직경이 든 구간의 값 그대로**(dMin ≤ D ≤ dMax). 보간하지 않는다. 구간 사이(예: 표가 Ø4 · Ø6 뿐인데 Ø5)는 비우고
 *    앞뒤 구간을 함께 돌려준다.
 *  - n = 1000·Vc/(π·D), vf = f·n(최소는 최소끼리, 최대는 최대끼리 — 범위의 양 끝). 식 출처는 Cutting.kt.
 *  - 회전수만 있는 행(Mitsubishi MVE · MVS · MNS — 표가 직경마다 회전수를 준다)은 그 회전수를 그대로 쓰고 Vc 는 π·D·n/1000 으로 환산(표시).
 *  - 권장값이 없는 행(범위만)은 권장을 만들지 않는다(가운데 값을 계산해 넣지 않음).
 */

@Serializable
data class CatalogSource(
    val id: String,
    val title: String,
    val url: String,
    val edition: String? = null,
    val note: String? = null,
)

/** 이송 표의 한 구간. dMin = dMax 이면 표의 그 직경 열 하나 */
@Serializable
data class FeedBand(
    val dMin: Double,
    val dMax: Double,
    val fMin: Double? = null,
    val fRec: Double? = null,
    val fMax: Double? = null,
    /** 표의 회전수 min⁻¹(Mitsubishi MVE · MVS · MNS) */
    val rpm: Int? = null,
) {
    operator fun contains(d: Double): Boolean = d >= dMin - EPS && d <= dMax + EPS
}

/** 시리즈 표의 한 행(재질 하나) */
@Serializable
data class DrillCondition(
    /** "P" "M" "K" "N" "S" "H" — [iso] */
    val isoGroup: String,
    /** "영문 / 한글" (카탈로그 표기) */
    val material: String,
    val hardness: String? = null,
    val vcMin: Double? = null,
    val vcRec: Double? = null,
    val vcMax: Double? = null,
    /** 직경 오름차순, 겹치지 않음. 비어 있으면 이송 값이 표에 없다 */
    val feeds: List<FeedBand>,
    val sourceId: String,
    val page: String,
) {
    val iso: IsoGroup get() = IsoGroup.valueOf(isoGroup)

    /** 회전수 표 행(Vc 칸 없음, 구간마다 회전수) */
    val rpmTable: Boolean get() = feeds.isNotEmpty() && feeds.all { it.rpm != null }
}

@Serializable
data class DrillSeries(
    val series: String,
    /** "solid-carbide" · "exchangeable-head" · "indexable" */
    val type: String,
    /** 카탈로그(주문표)의 직경 범위 */
    val diameterMin: Double,
    val diameterMax: Double,
    /** 가공 깊이 · 깊이별 감소 같은 카탈로그 각주 */
    val depth: String,
    val coolant: String,
    val conditions: List<DrillCondition>,
)

@Serializable
data class RemovedRow(val series: String, val isoGroup: String, val reason: String)

@Serializable
data class MakerCatalog(
    val manufacturer: String,
    val sources: List<CatalogSource>,
    val drills: List<DrillSeries>,
    /** 찾지 못한 것 · 넣지 않은 것 */
    val notFound: List<String>,
    /** 검증 방법 · 읽는 법 · 카탈로그 각주 */
    val notes: String,
)

/** 메이커 하나. [key] = sandvik · iscar · mitsubishi · yg1 · korloy */
@Serializable
data class DrillMaker(
    val key: String,
    val catalog: MakerCatalog,
    /** 검증에서 뺀 행과 까닭 */
    val removed: List<RemovedRow>,
    /** 검증에서 고친 것 */
    val corrected: List<String>,
    val confidence: String,
) {
    /** 화면 버튼 이름 */
    val label: String
        get() = when (key) {
            "sandvik" -> "Sandvik"
            "iscar" -> "ISCAR"
            "mitsubishi" -> "Mitsubishi"
            "yg1" -> "YG-1"
            "korloy" -> "KORLOY"
            else -> catalog.manufacturer
        }

    val series: List<DrillSeries> get() = catalog.drills

    fun source(id: String): CatalogSource? = catalog.sources.firstOrNull { it.id == id }
}

/** 최소 · 권장 · 최대(표에 없는 칸은 null) */
data class Span3(val min: Double?, val rec: Double?, val max: Double?) {
    val isEmpty: Boolean get() = min == null && rec == null && max == null

    private fun map(f: (Double) -> Double) = Span3(min?.let(f), rec?.let(f), max?.let(f))

    companion object {
        internal fun of(min: Double?, rec: Double?, max: Double?) = Span3(min, rec, max).takeUnless { it.isEmpty }
        internal fun map(s: Span3?, f: (Double) -> Double) = s?.map(f)?.takeUnless { it.isEmpty }
    }
}

enum class DrillWarning {
    /** 직경이 빈칸 */
    MISSING_DIAMETER,

    /** 직접 입력: Vc 가 빈칸 */
    MISSING_VC,

    /** 직접 입력: 이송 f 가 빈칸(n 만 나온다) */
    MISSING_FEED,

    /** 숫자가 아님 · 0 이하 · 크기 한도 밖 */
    BAD_INPUT,

    /**
     * 이 행의 직경 범위(이송 구간 처음~끝, 이송이 없는 행은 시리즈 범위) 밖 — 숫자를 내지 않는다.
     * 같은 재질이 직경 구간마다 다른 행이면 [DrillResult.alternatives] 에 맞는 행이 있다.
     */
    OUTSIDE_ROW,

    // ↑ 멈춤 · ↓ 숫자와 함께(일부 칸이 빈다)

    /** 시리즈 카탈로그 직경 범위 밖(절삭표에는 그 열이 있다) — 그 직경의 공구가 있는지 확인 */
    OUTSIDE_SERIES,

    /** 그 직경이 이송 표 구간 사이에 있다 — f · vf 없음(보간 안 함, 앞뒤 구간은 [DrillResult.below] · [DrillResult.above]). 회전수 표 행은 n · Vc 도 없음 */
    NOT_IN_FEED_TABLE,

    /** 이 행은 표에 이송 값이 없다(각주만 — 예: Mitsubishi TAFL "L/D=2,3 값의 80%") — f · vf 없음 */
    NO_FEED_IN_TABLE,

    /** 이 행은 표에 Vc 가 없다(빈칸으로 인쇄) — n · vf 없음, f 만 */
    NO_VC_IN_TABLE,
}

data class DrillResult(
    val diameter: Double?,
    /** m/min. [vcConverted] 면 표 회전수에서 환산한 값(권장 칸만) */
    val vc: Span3?,
    val vcConverted: Boolean,
    /** min⁻¹. [nFromTable] 면 표의 회전수(권장 칸만), 아니면 Vc 에서 계산 */
    val n: Span3?,
    val nFromTable: Boolean,
    /** 그 직경이 든 이송 구간(없으면 null) */
    val band: FeedBand?,
    /** mm/rev = [band] 값 그대로(직접 입력이면 넣은 값) */
    val f: Span3?,
    /** mm/min */
    val vf: Span3?,
    /** [DrillWarning.NOT_IN_FEED_TABLE] 일 때 바로 아래 · 위 구간 */
    val below: FeedBand?,
    val above: FeedBand?,
    /** [DrillWarning.OUTSIDE_ROW] 일 때 이 직경이 드는 같은 재질(같은 ISO 군 · 재질 글의 " [" 앞이 같음)의 다른 행 번호 */
    val alternatives: List<Int>,
    val source: CatalogSource?,
    val page: String?,
    val warnings: List<DrillWarning>,
)

object DrillCatalog {
    private val json = Json { ignoreUnknownKeys = false }

    /** 5사(JSON 순서: Sandvik · ISCAR · Mitsubishi · YG-1 · KORLOY). 처음 부를 때 읽는다 */
    val makers: List<DrillMaker> by lazy { json.decodeFromString(DrillCatalogJson.text()) }

    fun maker(key: String): DrillMaker? = makers.firstOrNull { it.key == key }

    /**
     * 같은 재질인지 볼 이름: 재질 글에서 " [" 앞(직경 구간 · 브레이커 · 재종 표기 앞). 같은 재질이 직경 구간마다 행이 나뉜 곳
     * (Sandvik 870 "[Ø10-20.99 vc …]" · "[Ø21-33 vc …]", Mitsubishi TAF "[Ø12–14.5 …]" · "[Ø15–56 …]")이 이 앞부분이 같다
     */
    private fun baseMaterial(c: DrillCondition) = c.material.substringBefore(" [")

    /** 행이 받는 직경 범위: 이송 구간 처음~끝, 이송이 없는 행은 시리즈 범위 */
    fun rowRange(series: DrillSeries, c: DrillCondition): ClosedFloatingPointRange<Double> =
        if (c.feeds.isEmpty()) series.diameterMin..series.diameterMax else c.feeds.first().dMin..c.feeds.last().dMax

    /** 카탈로그 행으로 조회. [condition] = [DrillSeries.conditions] 의 번호 */
    fun lookup(maker: DrillMaker, series: DrillSeries, condition: Int, diameter: Double?): DrillResult {
        val c = series.conditions[condition]
        val source = maker.source(c.sourceId)
        fun stop(w: DrillWarning, alt: List<Int> = emptyList()) =
            DrillResult(diameter, null, false, null, false, null, null, null, null, null, alt, source, c.page, listOf(w))
        if (diameter == null) return stop(DrillWarning.MISSING_DIAMETER)
        if (!(diameter.isFinite() && diameter > 0 && diameter <= MAX_MM)) return stop(DrillWarning.BAD_INPUT)
        val d = diameter
        val range = rowRange(series, c)
        if (d < range.start - EPS || d > range.endInclusive + EPS) {
            val alt = series.conditions.indices.filter { j ->
                val o = series.conditions[j]
                j != condition && o.isoGroup == c.isoGroup && baseMaterial(o) == baseMaterial(c) &&
                    d in rowRange(series, o).let { it.start - EPS..it.endInclusive + EPS }
            }
            return stop(DrillWarning.OUTSIDE_ROW, alt)
        }
        val warnings = mutableListOf<DrillWarning>()
        if (d < series.diameterMin - EPS || d > series.diameterMax + EPS) warnings += DrillWarning.OUTSIDE_SERIES
        val band = c.feeds.firstOrNull { d in it }
        var below: FeedBand? = null
        var above: FeedBand? = null
        if (c.feeds.isEmpty()) warnings += DrillWarning.NO_FEED_IN_TABLE
        else if (band == null) {
            warnings += DrillWarning.NOT_IN_FEED_TABLE
            below = c.feeds.lastOrNull { it.dMax < d }
            above = c.feeds.firstOrNull { it.dMin > d }
        }
        val f = band?.let { Span3.of(it.fMin, it.fRec, it.fMax) }

        val vc: Span3?
        val n: Span3?
        if (c.rpmTable) {
            val rpm = band?.rpm?.toDouble()
            n = rpm?.let { Span3(null, it, null) }
            vc = rpm?.let { Span3(null, Cutting.vc(it, d), null) }
        } else {
            vc = Span3.of(c.vcMin, c.vcRec, c.vcMax)
            if (vc == null) warnings += DrillWarning.NO_VC_IN_TABLE
            n = Span3.map(vc) { Cutting.rpm(it, d) }
        }
        val vf = vf(f, n)
        return DrillResult(d, vc, c.rpmTable && vc != null, n, c.rpmTable && n != null, band, f, vf, below, above, emptyList(), source, c.page, warnings)
    }

    /** 직접 입력: 직경 · Vc · f(mm/rev) → n · vf */
    fun manual(diameter: Double?, vc: Double?, f: Double?): DrillResult {
        fun stop(w: DrillWarning) = DrillResult(diameter, null, false, null, false, null, null, null, null, null, emptyList(), null, null, listOf(w))
        val bad = listOfNotNull(diameter, vc, f).any { !(it.isFinite() && it > 0) } ||
            (diameter != null && diameter > MAX_MM) || (f != null && f > MAX_MM) || (vc != null && vc > Cutting.MAX_SPEED)
        if (bad) return stop(DrillWarning.BAD_INPUT)
        if (diameter == null) return stop(DrillWarning.MISSING_DIAMETER)
        if (vc == null) return stop(DrillWarning.MISSING_VC)
        val vcs = Span3(null, vc, null)
        val n = Span3(null, Cutting.rpm(vc, diameter), null)
        val fs = f?.let { Span3(null, it, null) }
        return DrillResult(
            diameter, vcs, false, n, false, null, fs, vf(fs, n), null, null, emptyList(), null, null,
            if (f == null) listOf(DrillWarning.MISSING_FEED) else emptyList(),
        )
    }

    /** vf = f·n — 최소는 최소끼리, 최대는 최대끼리(회전수 칸이 권장 하나뿐이면 그것과) */
    private fun vf(f: Span3?, n: Span3?): Span3? {
        if (f == null || n == null) return null
        fun mul(a: Double?, b: Double?) = if (a != null && b != null) Cutting.feedRate(a, b) else null
        return Span3.of(mul(f.min, n.min ?: n.rec), mul(f.rec, n.rec), mul(f.max, n.max ?: n.rec))
    }
}
