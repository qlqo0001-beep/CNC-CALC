package com.bmt.nosecalc.calc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/*
 * 다이스 헤드 카탈로그(고정형 자동 열림 — 공작물이 돌고 터렛의 머리는 서 있다). 자료는 calc/catalog/die-heads.json —
 * docs/research/die-heads.json(Wagner · Landis · TACO 공식 자료, 출처 · 쪽 대조 끝)의 바이트 사본이고, 빌드가 Kotlin 문자열([DieHeadCatalogJson])로
 * 만들어 넣는다(드릴 카탈로그와 같은 길 — calc/build.gradle.kts 의 generateCatalogs). 두 파일이 같은지, 앱이 읽은 모델이 원본의 모든 값을
 * 그대로 갖는지는 jvmTest 의 DieHeadCatalogJsonTest 가 본다.
 *
 * 모델: 읽기는 `ignoreUnknownKeys = false`(모르는 칸이 생기면 실패 — DrillCatalog 와 같은 원칙). 앱이 쓰는 칸(출처 · 절삭 속도 · 체이서 ·
 * 체이서 홀더 · 열림 · 플로트 · 이송 규칙)은 타입을 주고, 화면이 쓰지 않는 조사 기록(머리 모델 사양 headTypes · 목 throatTypes · 릴리프 폭 ·
 * 고침 · 뺌 · 대조 · 검산 기록)과 메이커마다 꼴이 다른 칸(체이서의 tpi · pitch · throat · setOf · sizeRange — 글 · 배열 · 수가 섞임)은
 * [JsonElement] 로 값 그대로 들고 다닌다(버리는 값이 없다).
 *
 * 조회:
 *  - [DieHeadCatalog.speeds] — 절삭 속도 줄(Vc 최소 · 권장 · 최대 m/min). 권장값이 없으면 만들지 않는다(출처 두 표 모두 범위만 — vcRec 는 null 그대로).
 *    사다리꼴 나사 줄(Wagner Z39-2 의 Trapezgewinde)은 뺀다 — 이 앱의 다이스는 60° M · UN 만 깎는다.
 *  - [DieHeadCatalog.chasers] — 나사 꼴 · 피치가 맞는 체이서 줄. 체이서는 피치만 정하고 지름 범위는 머리의 홀더가 정한다(Landis 원문 —
 *    체이서 줄의 sizeRange 글). 그래서 크기로 거르지 않는다 — 홀더 표([DieMaker.chaserHolders])는 UNC · UNF · 미터 범위만 있어 UNEF · 20UN 을 거르면
 *    모두 빠진다. 피치 목록이 배열인 줄만 맞춘다(Landis 28줄. Wagner 체이서 줄은 표기 예 글뿐이고 TACO 는 나사 꼴 · 피치가 비어 있다).
 */

/** 출처 참조(쪽은 원문 표기 그대로) */
@Serializable
data class DieRef(val sourceId: String, val page: String)

@Serializable
data class DieSource(
    val id: String,
    /** "official" · "manufacturer"(제조사 설명서를 제3자 사이트로 읽음) · "secondary"(값으로 안 씀) */
    val sourceType: String,
    val maker: String? = null,
    val title: String,
    val edition: String? = null,
    val url: String? = null,
    /** 모델마다 따로 있는 PDF(Wagner 데이터 시트) */
    val urls: Map<String, String>? = null,
    val listedAt: String? = null,
    val alsoUrl: String? = null,
    val readVia: String? = null,
    val pages: String? = null,
    val note: String? = null,
)

/** 여는 방식 · 닫는 방식 */
@Serializable
data class DieOpening(
    val mechanism: String,
    val closing: String? = null,
    val quote: String,
    val mounting: String? = null,
    val caution: String? = null,
    val deTrip: String? = null,
    val sourceRefs: List<DieRef>,
)

/** 플로트 · 열림 행정 자료 한 묶음. [values] 는 원문 단위([unit]) 그대로(Landis 인치 분수 글 · TACO mm 수) */
@Serializable
data class DieFloatItem(
    val what: String,
    val values: Map<String, JsonPrimitive>,
    val unit: String,
    val caution: String? = null,
    val sourceId: String,
    val page: String,
)

/** 열릴 때 · 열리기까지 축 방향으로 더 움직이는 길이. 세 곳 모두 고정형의 열림 행정 mm 는 없다(notFound) */
@Serializable
data class DieFloat(
    val items: List<DieFloatItem>,
    val value: Double? = null,
    val note: String,
    val sourceRefs: List<DieRef>? = null,
)

/** 이송과 리드(피치)의 관계 — [feedToLead] 는 원문이 말한 비율(같게 = 1), 원문에 없으면 null */
@Serializable
data class DieFeedRule(
    val rule: String? = null,
    val feedToLead: Double? = null,
    val quote: String? = null,
    val quoteContext: String? = null,
    val manualRule: String? = null,
    val related: String? = null,
    val deCushion: String? = null,
    val leadTolerance: String? = null,
    val note: String? = null,
    val sourceRefs: List<DieRef>,
)

/** 절삭 속도 표의 한 줄(m/min). 표에 없는 칸은 null — 권장값이 없으면 만들지 않는다 */
@Serializable
data class DieSpeed(
    /** 원문 재질 이름 */
    val material: String,
    val materialKo: String,
    /** 나사 종류 칸(Wagner Z39-2 표만 — 일반 삼각 · 사다리꼴) */
    val threadType: String? = null,
    /** "P" "M" "K" "N" "S" "H" — 원문에 없으면 null(지금 24줄 모두 없음) */
    val isoGroup: String? = null,
    val chaserMaterial: String? = null,
    val vcMin: Double? = null,
    val vcRec: Double? = null,
    val vcMax: Double? = null,
    val sourceId: String,
    val page: String,
) {
    val iso: IsoGroup? get() = isoGroup?.let { IsoGroup.valueOf(it) }
}

/**
 * 체이서 한 묶음(원문 표의 한 줄). [tpi] · [pitch] · [throat] 는 Landis 는 배열(그 체이서로 나는 산수 · 피치 · 목 칸), Wagner 는 표기 예 글,
 * TACO 는 null — 배열일 때만 [tpis] · [pitches] · [throats] 에 담긴다. [setOf] 는 한 벌 개수(수 또는 "4 · 6" 글)
 */
@Serializable
data class DieChaser(
    val designation: String? = null,
    val threadForm: String? = null,
    val chaserSize: String? = null,
    val setOf: JsonElement? = null,
    val tpi: JsonElement? = null,
    val pitch: JsonElement? = null,
    val sizeRange: JsonElement? = null,
    val throat: JsonElement? = null,
    val note: String? = null,
    val set: JsonElement? = null,
    val materials: JsonElement? = null,
    val sourceRefs: List<DieRef>? = null,
    val sourceId: String,
    val page: String,
) {
    /** 산수 목록("20" · "11-1/2") — 원문이 배열일 때만 */
    val tpis: List<String> get() = strings(tpi)

    /** 미터 피치 목록("1.50" — mm) — 원문이 배열일 때만 */
    val pitches: List<String> get() = strings(pitch)

    /** 목 종류("20° Short") — 원문이 배열일 때만 */
    val throats: List<String> get() = strings(throat)

    private fun strings(e: JsonElement?): List<String> = (e as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
}

/**
 * 체이서 홀더 표의 한 줄. Wagner 줄은 표기 · 품번 · 가공 범위(골지름 mm) · 홀더 각 · 체이서 크기 · 나사 열(미터 보통 · UNC · UNF · NPT · R),
 * Landis 줄은 범위 · 나사 꼴 · 리드각(HELIX ANGLE) · 체이서 크기. 원문 오식으로 보이는 칸도 고치지 않았다([note])
 */
@Serializable
data class DieHolderRow(
    val designation: String? = null,
    val itemNo: String? = null,
    val cuttingRangeMinorDiaMm: String? = null,
    val holderAngleDeg: Double? = null,
    val holderAngleExtra: String? = null,
    val chaserDimMm: String? = null,
    val chaserSize: String,
    val mCoarse: List<String>? = null,
    @SerialName("UNC") val unc: List<String>? = null,
    @SerialName("UNF") val unf: List<String>? = null,
    @SerialName("NPT") val npt: List<String>? = null,
    @SerialName("R_taperPipe") val rTaperPipe: List<String>? = null,
    val page: Int? = null,
    val range: String? = null,
    val threadForm: String? = null,
    val helixAngle: String? = null,
    val note: String? = null,
)

/** 홀더 표 하나(Wagner 는 머리 크기마다 [table], Landis 는 머리 [head] 마다) */
@Serializable
data class DieChaserHolder(
    val table: String? = null,
    val forHeads: String? = null,
    val head: String? = null,
    val sourceId: String,
    val page: String? = null,
    val pages: List<Int>? = null,
    val notes: List<String>? = null,
    val rows: List<DieHolderRow>,
)

/** 체이서 크기(Wagner) */
@Serializable
data class DieChaserSize(
    val size: String,
    val dimMm: String,
    val usedIn: String,
    val note: String? = null,
    val sourceRefs: List<DieRef>,
)

/** 출처가 붙은 글 한 칸 */
@Serializable
data class DieText(val value: String, val sourceRefs: List<DieRef>)

/** 메이커 하나. [key] = wagner · landis · taco */
@Serializable
data class DieMaker(
    val key: String,
    val name: String,
    /** 머리 모델 사양(원문 칸 그대로 — 메이커마다 칸이 다르다) */
    val headTypes: List<JsonObject>,
    val headTypesNotes: List<String>,
    val opening: DieOpening,
    val floatTravel: DieFloat,
    val feedRule: DieFeedRule,
    val speeds: List<DieSpeed>,
    val speedsNote: String? = null,
    val speedsNotes: List<String>? = null,
    val materialLimit: String? = null,
    val chasers: List<DieChaser>,
    val chasersNote: String? = null,
    val chaserCodeNote: String? = null,
    /** 목 종류(Wagner 배열 · Landis 객체 — 원문 꼴 그대로) */
    val throatTypes: JsonElement? = null,
    val reliefWidth: JsonElement? = null,
    val chaserSizes: List<DieChaserSize>? = null,
    val chaserMaterials: DieText? = null,
    val chaserHolders: List<DieChaserHolder>? = null,
    val chaserHoldersNote: String? = null,
) {
    /** 화면 이름 */
    val label: String
        get() = when (key) {
            "wagner" -> "Wagner"
            "landis" -> "Landis"
            "taco" -> "TACO"
            else -> name
        }
}

/** 원문 파일 전체(최상위 칸 그대로) */
@Serializable
data class DieHeadCatalogData(
    val title: String,
    val researchedAt: String,
    val rules: List<String>,
    /** 칸 풀이 */
    val fields: Map<String, String>,
    val sources: List<DieSource>,
    val makers: List<DieMaker>,
    val notFound: List<String>,
    val notes: String,
    val removed: JsonElement,
    val corrected: JsonElement,
    val crossCheck: JsonElement,
    val verification: JsonElement,
    val confidence: String,
)

/** 절삭 속도 한 줄과 그 메이커 */
data class DieSpeedHit(val maker: DieMaker, val speed: DieSpeed)

/** 맞는 체이서 한 줄과 그 메이커 */
data class DieChaserHit(val maker: DieMaker, val chaser: DieChaser)

object DieHeadCatalog {
    private val json = Json { ignoreUnknownKeys = false }

    /** 원문 전체. 처음 부를 때 읽는다 */
    val data: DieHeadCatalogData by lazy { json.decodeFromString(DieHeadCatalogJson.text()) }

    /** 3곳(JSON 순서: Wagner · Landis · TACO) */
    val makers: List<DieMaker> get() = data.makers

    fun maker(key: String): DieMaker? = makers.firstOrNull { it.key == key }

    fun source(id: String): DieSource? = data.sources.firstOrNull { it.id == id }

    /**
     * 절삭 속도 줄 — [maker] 키(null = 모두), [iso] 군(null = 모두. 주면 그 군 줄과 **군이 없는 줄** — 출처가 군을 정하지 않은 줄은 뺄 수 없어
     * 남기고 화면이 재질 글로 고르게 한다. 지금 자료는 모두 군이 없다). 사다리꼴 나사 줄은 뺀다(60° M · UN 다이스가 아님)
     */
    fun speeds(maker: String? = null, iso: IsoGroup? = null): List<DieSpeedHit> =
        makers.filter { maker == null || it.key == maker }.flatMap { m ->
            m.speeds.filter { s ->
                s.threadType?.startsWith("Trapez") != true && (iso == null || s.iso == null || s.iso == iso)
            }.map { DieSpeedHit(m, it) }
        }

    /**
     * [spec] 의 나사 꼴 · 피치가 맞는 체이서 줄: UN 은 나사 꼴 "UN" 이고 산수 목록에 그 산수가, 미터는 나사 꼴 "mm …" 이고 피치 목록에 그 피치가 있는 줄
     * (UNC · UNF · UNEF · 20UN 모두 같은 UN 꼴 체이서 — 체이서는 피치만 정한다)
     */
    fun chasers(spec: ThreadSpec): List<DieChaserHit> = makers.flatMap { m ->
        m.chasers.filter { c ->
            when (spec.kind) {
                ThreadKind.UN -> c.threadForm == "UN" && spec.tpi.toString() in c.tpis
                ThreadKind.METRIC -> c.threadForm?.startsWith("mm") == true && c.pitches.any { abs(it.toDouble() - spec.pitch) < EPS }
            }
        }.map { DieChaserHit(m, it) }
    }
}
