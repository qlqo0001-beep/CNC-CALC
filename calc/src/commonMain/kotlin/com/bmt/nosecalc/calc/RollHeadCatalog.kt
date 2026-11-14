package com.bmt.nosecalc.calc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/*
 * 축방향 전조 머리 카탈로그(LMT Fette — 머리는 터렛에 서 있고 공작물이 돈다). 자료는 calc/catalog/roll-heads.json —
 * docs/research/roll-heads.json(LMT Fette 공식 PDF 조사 · 대조 끝)의 바이트 사본이고, 빌드가 Kotlin 문자열([RollHeadCatalogJson])로 만들어 넣는다
 * (다이스 헤드 카탈로그와 같은 길 — calc/build.gradle.kts 의 generateCatalogs). 다이스 헤드 카탈로그([DieHeadCatalog] — 깎는 체이서 머리)와 섞지 않는다.
 * 두 파일이 같은지, 앱이 읽은 모델이 원본의 모든 값을 그대로 갖는지는 jvmTest 의 RollHeadCatalogJsonTest 가 본다.
 *
 * 모델: 읽기는 `ignoreUnknownKeys = false`(모르는 칸이 생기면 실패). 앱이 쓰는 칸(출처 · 머리 · 전조 전 지름 · 속도 · 이송 · 요령)은 타입을 주고,
 * 화면이 쓰지 않는 조사 기록(고친 칸 · 대조가 더 찾은 값 · 대조 기록)은 [JsonElement] 로 값 그대로 들고 다닌다.
 * 회사(사용자) 값은 여기에 없다 — [CompanyRolls].
 */

/** 출처 참조(쪽은 원문 표기 그대로 — "인쇄 84 (PDF 86쪽) Tab. 8") */
@Serializable
data class RollRef(val sourceId: String, val page: String, val quote: String? = null)

@Serializable
data class RollSource(
    val id: String,
    /** "official" · "distributor"(대리점이 올린 같은 회사 자료) — 다른 회사 자료(secondary)는 지금 없다 */
    val sourceType: String,
    val maker: String,
    val title: String,
    val edition: String? = null,
    val url: String,
    val listedAt: String? = null,
    val readVia: String,
    val pages: String,
)

/**
 * 머리 모델 한 줄. 지름 범위는 카탈로그 머리 쪽 그림표, 피치 · 산수 범위는 그 머리 롤 쪽의 표준 나사 롤 목록 최소 · 최대(공식 범위 칸이 아니다 —
 * 미터 롤 기준이라 인치 롤은 더 굵을 수 있다). [openingMovementMm] = 설명서 Tab. 8 열림 행정 a(F RN 계열은 null)
 */
@Serializable
data class RollHeadType(
    /** "F2 EVO" · "F233400 EVO" · "F3-1RN X129" */
    val model: String,
    val family: String? = null,
    val design: String? = null,
    val threadDiaMinMm: Double? = null,
    val threadDiaMaxMm: Double? = null,
    val threadDiaInch: String? = null,
    val pitchMinMm: Double? = null,
    val pitchMaxMm: Double? = null,
    val tpiMin: Double? = null,
    val tpiMax: Double? = null,
    val maxRollLengthMm: Double? = null,
    val maxRpm: Double? = null,
    val maxSpeedNote: String? = null,
    val rollDiaMm: Double? = null,
    val shank: String? = null,
    val opening: String? = null,
    val rollDesignation: String? = null,
    val notesKo: String? = null,
    val openingMovementMm: Double? = null,
    val refs: List<RollRef>,
) {
    /** 회사 표기(앞의 모델 낱말 — "F2" · "F233400" · "F3-1RN") */
    val key: String get() = model.substringBefore(' ')

    /** EVOline 머리(F1–F3 EVO 사용 설명서의 계열)인가 — 앱이 고르는 머리 */
    val evo: Boolean get() = model.endsWith(" EVO")
}

/** 출처가 붙은 글 한 칸(ruleKo 꼴) */
@Serializable
data class RollRule(val ruleKo: String? = null, val refs: List<RollRef>)

@Serializable
data class RollModelNaming(val f233400Ko: String, val isModelName: Boolean? = null, val systemKo: String? = null, val refs: List<RollRef>)

@Serializable
data class RollDesignation(val ruleKo: String? = null, val example: String? = null, val refs: List<RollRef>)

@Serializable
data class RollOpening(val mechanismKo: String? = null, val closingKo: String? = null, val quote: String? = null, val refs: List<RollRef>)

/** 전조 전 지름: dV ≈ d2 − [offsetMm](d2 = 유효지름) */
@Serializable
data class RollBlankDia(
    val ruleKo: String? = null,
    val formula: String? = null,
    val basis: String? = null,
    val toleranceKo: String? = null,
    /** 카탈로그 부록 표 줄(지금 비었다 — UN 20산 전조 전 지름 표가 자료에 없다) */
    val rows: List<JsonElement>,
    val refs: List<RollRef>,
    val offsetMm: Double? = null,
)

/** 재질별 권장 전조 속도(m/min) — 범위만(권장 한 값 vRecMpm 은 모든 표에 없다) */
@Serializable
data class RollSpeed(
    val material: String,
    val materialKo: String,
    val isoGroup: String? = null,
    val tensileMpa: String? = null,
    val vMinMpm: Double? = null,
    val vMaxMpm: Double? = null,
    val vRecMpm: Double? = null,
    val noteKo: String? = null,
    val refs: List<RollRef>,
    /** 원문 재료 번호("1.4401") */
    val materialNo: String? = null,
)

/** 회전수 식의 지름 — "blank"(전조 전 지름) · "nominal" · "pitch" · "unknown" */
@Serializable
data class RollSpeedBasis(val value: String, val explanationKo: String? = null, val refs: List<RollRef>)

@Serializable
data class RollStainless(val splits304vs316: Boolean? = null, val explanationKo: String? = null, val refs: List<RollRef>)

/** CNC 기계 이송 / 피치 — [ratioRec] = 설명서 값(0.95) */
@Serializable
data class RollFeedRule(
    val ruleKo: String? = null,
    val quote: String? = null,
    val ratioMin: Double? = null,
    val ratioMax: Double? = null,
    val ratioRec: Double? = null,
    val refs: List<RollRef>,
)

@Serializable
data class RollWorkpiece(
    val elongationMinPct: Double? = null,
    val tensileMaxMpa: Double? = null,
    val hardnessKo: String? = null,
    val chamferKo: String? = null,
    val notesKo: String? = null,
    val refs: List<RollRef>,
)

@Serializable
data class RollLimit(val textKo: String, val refs: List<RollRef>)

/** 요령 묶음 */
enum class RollTipGroup { RPM_UP, RPM_DOWN, RATIO_UP, RATIO_DOWN, BLANK_BIG, BLANK_SMALL, SETUP, OTHER }

/** 요령 한 줄(증상 · 원인 · 대책). [order] 는 SETUP 의 차례. [secondary] = 다른 회사 자료(지금 없다) */
@Serializable
data class RollTip(
    val group: String,
    val order: Int? = null,
    val symptomKo: String,
    val causeKo: String? = null,
    val remedyKo: String,
    val quote: String? = null,
    val secondary: Boolean,
    val refs: List<RollRef>,
) {
    val tipGroup: RollTipGroup get() = RollTipGroup.valueOf(group)
}

/** (머리, 산수, 크기)마다 최대 전조 길이(mm, 나사끝 포함) — 카탈로그 선택표 */
@Serializable
data class RollLength(val head: String, val tpi: Int, val sizes: List<String>, val maxMm: Double, val noteKo: String? = null, val refs: List<RollRef>)

@Serializable
data class RollNotFound(val field: String, val reason: String)

/** 원문 파일 전체(최상위 칸 그대로) */
@Serializable
data class RollHeadCatalogData(
    val title: String,
    val researchedAt: String,
    val rules: List<String>,
    val fields: Map<String, String>,
    val sources: List<RollSource>,
    val heads: List<RollHeadType>,
    val modelNaming: RollModelNaming,
    val rollDesignation: RollDesignation,
    val opening: RollOpening,
    val blankDia: RollBlankDia,
    val speeds: List<RollSpeed>,
    val speedBasisDia: RollSpeedBasis,
    val stainless: RollStainless,
    val feed: RollFeedRule,
    val workpiece: RollWorkpiece,
    val lubrication: RollRule,
    val start: RollRule,
    val endDwell: RollRule,
    val otherLimits: List<RollLimit>,
    val rollLengths: List<RollLength>,
    val tips: List<RollTip>,
    /** 묶음마다 찾은 정도(조사 기록) */
    val coverage: Map<String, String>,
    val notFound: List<RollNotFound>,
    val corrected: JsonElement,
    val checkerAdded: JsonElement,
    val crossCheck: JsonElement,
    val confidence: String,
)

object RollHeadCatalog {
    private val json = Json { ignoreUnknownKeys = false }

    /** 원문 전체. 처음 부를 때 읽는다 */
    val data: RollHeadCatalogData by lazy { json.decodeFromString(RollHeadCatalogJson.text()) }

    fun source(id: String): RollSource? = data.sources.firstOrNull { it.id == id }

    /** 앱이 고르는 EVOline 머리(JSON 차례 — F2 · F23 · F233400 · F3 · F34) */
    val evoHeads: List<RollHeadType> get() = data.heads.filter { it.evo }

    /** 회사 표기("F34")의 EVOline 머리 */
    fun head(key: String): RollHeadType? = evoHeads.firstOrNull { it.key == key }

    /** [h] 머리로 [spec] 을 전조할 때 최대 전조 길이(mm) — 선택표 줄([RollHeadCatalogData.rollLengths]) → 머리 칸 → null(무제한) */
    fun maxRollLength(h: RollHeadType, spec: ThreadSpec): Double? =
        data.rollLengths.firstOrNull { it.head == h.key && spec.kind == ThreadKind.UN && it.tpi == spec.tpi && spec.size in it.sizes }?.maxMm
            ?: h.maxRollLengthMm

    /** 재료 번호("1.4401")의 속도 줄 */
    fun speed(materialNo: String): RollSpeed? = data.speeds.firstOrNull { it.materialNo == materialNo }

    /** 묶음의 요령(JSON 차례, SETUP 은 차례대로) */
    fun tips(group: RollTipGroup): List<RollTip> =
        data.tips.filter { it.tipGroup == group }.let { l -> if (group == RollTipGroup.SETUP) l.sortedBy { it.order ?: Int.MAX_VALUE } else l }
}
