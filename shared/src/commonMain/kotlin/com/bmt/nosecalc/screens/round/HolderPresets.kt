package com.bmt.nosecalc.screens.round

import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.InsertShape

/**
 * 보링바 프리셋 한 줄: 제조사 카탈로그 값 — 바 지름 D(DCON) · f(WF) · 최소 가공경(DMIN) · 절입각 κr(KAPR) · 인서트 모양 ·
 * 날 길이(품번 크기 숫자, DCMT 07 → 7 — 실제 날은 0~1 mm 더 길다). url = 값을 읽은 곳. 돌출 길이 · 노즈 R 은 넣지 않는다.
 * null 칸 = 출처에 없는 값(지어내지 않는다) — 고르면 그 칸은 빈칸이 되고 사용자가 넣는다.
 * [company] = 회사에서 쓰는 홀더(시트 맨 위 '회사 사용' 묶음), [source] = 출처 글(카탈로그 · 판 · 쪽), [note] = 값이 비는 까닭 같은 한 줄
 */
internal class HolderPreset(
    val maker: String,
    val code: String,
    val dia: Double?,
    val f: Double?,
    val dMin: Double?,
    val kappa: Double?,
    val shape: InsertShape?,
    val edge: Double?,
    val url: String,
    val company: Boolean = false,
    val source: String? = null,
    val note: String? = null,
) {
    /** 계산기 바. 출처에 없는 칸이 있으면 null */
    val bar: BoringBar?
        get() = BoringBar(dia ?: return null, f ?: return null, shape ?: return null, edge ?: return null, kappa ?: return null, dMin = dMin)

    /** '출처:' 줄에 쓰는 사이트 이름 */
    val host: String get() = url.substringAfter("://").substringBefore('/')

    /** 채우는 칸(RoundState.holder 이름 → 글). 출처에 없는 칸은 빈칸 */
    fun fields(): Map<String, String> = mapOf(
        "dia" to dia.text(), "f" to f.text(), "dmin" to dMin.text(), "kr" to kappa.text(), "shape" to (shape?.name ?: ""), "edge" to edge.text(),
    )

    private fun Double?.text() = this?.let(::plain) ?: ""
}

/** 샌드빅 코로만트: 제품 페이지(값은 같은 품번의 제품 API — DCONMS · WF · DMIN · KAPR) */
private fun sandvik(code: String, d: Double, f: Double, dMin: Double, kr: Double, shape: InsertShape, edge: Double) =
    HolderPreset("샌드빅", code, d, f, dMin, kr, shape, edge, "https://www.sandvik.coromant.com/en-gb/product-details?c=${code.replace(" ", "%20")}")

/** 미쓰비시 머티리얼: 웹 카탈로그의 시리즈 표(id) */
private fun mitsubishi(code: String, d: Double, f: Double, dMin: Double, kr: Double, shape: InsertShape, edge: Double, id: Int) =
    HolderPreset("미쓰비시", code, d, f, dMin, kr, shape, edge, "https://www.mitsubishicarbide.net/mhg/enuk/boring/$id")

/** 한국야금: 보링바 카탈로그 PDF(Cutwel 이 올려 둔 것) */
private fun korloy(code: String, d: Double, f: Double, dMin: Double, kr: Double, shape: InsertShape, edge: Double) =
    HolderPreset("한국야금", code, d, f, dMin, kr, shape, edge, "https://www.cutwel.co.uk/media/product-pdfs/downloads/Korloy%20Boring%20Bars.pdf")

/*
 * 회사 사용 보링바(사용자 10-29 "회사에서 사용하는 팁홀더도 찾아서 홀더 종류에 추가"). 값 · 출처는 docs/research/company-holders.json
 * (presetCandidates · holders — 출처를 다시 열어 칸마다 대조한 것)과 같다 — jvmTest CompanyHoldersJsonTest 가 칸마다 본다.
 * 같은 품번도 제조사마다 f · DMIN · κr 이 달라(예: C08K-SCLCR06 한국야금 f 5 · 대구텍 f 6, STFCR κr 한국야금 90 · 대구텍 91) 제조사마다 줄을 나눈다.
 * 샌드빅 S12M-STFCR 11 · S16R-STFCR 11 은 단종 표시라 넣지 않았다.
 */
private const val KORLOY_DC = "https://catalog.korloy.com/app/main.do"
private const val KORLOY_T2526 = "https://www.korloy.com/en/ebook/Cutting%20Tools_Turning%2025-26(EM)/assets/contents/download.pdf"
private const val TAEGUTEC_T2026 = "https://www.taegutec.com/pages/wp-content/uploads/kboard_attached/11/202603/69b8a59826f185433660.pdf"
private const val KYOCERA_F2024 = "https://asia.kyocera.com/products/cuttingtools/wp-content/uploads/2025/02/07_Chapter_F_2024_KAP.pdf"
private const val ISCAR_PICCO = "https://www.iscar.com/products.aspx/lang/en/productid/39"
private const val KOR_C = "KORLOY Turning 2025-2026 B132 · 디지털 카탈로그"
private const val KOR_S = "KORLOY Turning 2025-2026 B137"
private const val TTK_C = "TaeguTec Turning 2026 A175"
private const val TTK_S = "TaeguTec Turning 2026 A182"
private const val MMC = "Mitsubishi 웹 카탈로그 제품 페이지"
private const val KYO = "KYOCERA 2024-2026 F156 · F157"

private fun company(maker: String, code: String, d: Double, f: Double, dMin: Double, kr: Double, shape: InsertShape, edge: Double, url: String, source: String) =
    HolderPreset(maker, code, d, f, dMin, kr, shape, edge, url, company = true, source = source)

/** 출처에 바 값이 없는 회사 공구(슬리브 · 계열 이름) — 칸은 빈칸, 사용자가 넣는다 */
private fun companyBlank(maker: String, code: String, url: String, source: String, note: String) =
    HolderPreset(maker, code, null, null, null, null, null, null, url, company = true, source = source, note = note)

/** 회사 사용 23 줄: 카바이드 자루 SCLCR → 강 자루 STFCR → 소경(바 지름 순, 같은 품번은 한국야금 · 대구텍 · 미쓰비시 순) */
internal val COMPANY_PRESETS: List<HolderPreset> = listOf(
    company("한국야금", "C08K-SCLCR-06", 8.0, 5.0, 10.0, 95.0, InsertShape.C, 6.0, KORLOY_DC, KOR_C),
    company("대구텍", "C08K SCLCR 06", 8.0, 6.0, 11.0, 95.0, InsertShape.C, 6.0, TAEGUTEC_T2026, TTK_C),
    company("한국야금", "C10K-SCLCR-06", 10.0, 6.0, 12.0, 95.0, InsertShape.C, 6.0, KORLOY_DC, KOR_C),
    company("대구텍", "C10K SCLCR 06", 10.0, 7.0, 13.0, 95.0, InsertShape.C, 6.0, TAEGUTEC_T2026, TTK_C),
    company("미쓰비시", "C10KSCLCR06", 10.0, 7.0, 13.0, 95.0, InsertShape.C, 6.0, "https://www.mmc-carbide.com/en_jp/webcatalog/product/20055542", MMC),
    company("한국야금", "C12M-SCLCR-06", 12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0, KORLOY_DC, KOR_C),
    company("대구텍", "C12M SCLCR 06", 12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0, TAEGUTEC_T2026, TTK_C),
    company("미쓰비시", "C12MSCLCR06", 12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0, "https://www.mmc-carbide.com/en_jp/webcatalog/product/20055543", MMC),
    company("한국야금", "C12M-SCLCR-09", 12.0, 9.0, 16.0, 95.0, InsertShape.C, 9.0, KORLOY_DC, KOR_C),
    company("대구텍", "C12M SCLCR 09", 12.0, 9.0, 16.0, 95.0, InsertShape.C, 9.0, TAEGUTEC_T2026, TTK_C),
    company("한국야금", "C16R-SCLCR-09", 16.0, 10.0, 20.0, 95.0, InsertShape.C, 9.0, KORLOY_DC, KOR_C),
    company("대구텍", "C16R SCLCR 09", 16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0, TAEGUTEC_T2026, TTK_C),
    company("미쓰비시", "C16RSCLCR09", 16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0, "https://www.mmc-carbide.com/en_jp/webcatalog/product/20055544", MMC),
    company("대구텍", "S10K STFCR 09", 10.0, 6.0, 12.0, 91.0, InsertShape.T, 9.0, TAEGUTEC_T2026, TTK_S),
    company("한국야금", "S12M-STFCR-09", 12.0, 9.0, 16.0, 90.0, InsertShape.T, 9.0, KORLOY_T2526, KOR_S),
    company("대구텍", "S12M STFCR 09", 12.0, 9.0, 16.0, 91.0, InsertShape.T, 9.0, TAEGUTEC_T2026, TTK_S),
    company("한국야금", "S12M-STFCR-11", 12.0, 9.0, 16.0, 90.0, InsertShape.T, 11.0, KORLOY_T2526, KOR_S),
    company("대구텍", "S12M STFCR 1102", 12.0, 9.0, 16.0, 91.0, InsertShape.T, 11.0, TAEGUTEC_T2026, TTK_S),
    company("한국야금", "S16R-STFCR-11", 16.0, 11.0, 20.0, 90.0, InsertShape.T, 11.0, KORLOY_T2526, KOR_S),
    company("대구텍", "S16R STFCR 1102", 16.0, 11.0, 20.0, 91.0, InsertShape.T, 11.0, TAEGUTEC_T2026, TTK_S),
    companyBlank("교세라", "SH0516-100", KYOCERA_F2024, KYO, "보링바 슬리브(구멍 Ø5) — 값은 끼운 바의 카탈로그로 넣으세요"),
    companyBlank("교세라", "SH0616-100", KYOCERA_F2024, KYO, "보링바 슬리브(구멍 Ø6) — 값은 끼운 바의 카탈로그로 넣으세요"),
    companyBlank("이스카", "PICCO", ISCAR_PICCO, "ISCAR PICCO 제품 페이지", "초경 소경 보링바 계열 이름 — 품번을 몰라 값이 없습니다"),
)

/** 출처를 확인한 카탈로그 28 줄(제조사 순). 이스카는 KAPR 을 내지 않아 뺐다 */
private val CATALOG_PRESETS: List<HolderPreset> = listOf(
    sandvik("A10K-SCLCR 06", 10.0, 6.0, 12.0, 95.0, InsertShape.C, 6.0),
    sandvik("A10K-SDUCR 07", 10.0, 7.0, 13.0, 93.0, InsertShape.D, 7.0),
    sandvik("A12M-SCLCR 06", 12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0),
    sandvik("A12M-SDUCR 07", 12.0, 9.0, 16.0, 93.0, InsertShape.D, 7.0),
    sandvik("A16R-SCLCR 09", 16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0),
    sandvik("A16R-SDUCR 07-R", 16.0, 11.0, 20.0, 93.0, InsertShape.D, 7.0),
    sandvik("A16R-SDQCR 07", 16.0, 11.0, 20.0, 107.5, InsertShape.D, 7.0),
    sandvik("A16R-SVUBR 11-E", 16.0, 13.0, 22.0, 93.0, InsertShape.V, 11.0),
    sandvik("A16R-SVQBR 11-E", 16.0, 13.0, 22.0, 107.5, InsertShape.V, 11.0),
    sandvik("A16R-PCLNR 09", 16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0),
    sandvik("A20S-SCLCR 09", 20.0, 13.0, 25.0, 95.0, InsertShape.C, 9.0),
    sandvik("A20S-SDUCR 11-R", 20.0, 13.0, 25.0, 93.0, InsertShape.D, 11.0),
    sandvik("A20S-SVQBR 11-E", 20.0, 15.0, 27.0, 107.5, InsertShape.V, 11.0),
    sandvik("A25T-SCLCR 09", 25.0, 17.0, 32.0, 95.0, InsertShape.C, 9.0),
    sandvik("A25T-PDUNR 11", 25.0, 17.0, 32.0, 93.0, InsertShape.D, 11.0),
    sandvik("A32T-SCLCR 12", 32.0, 22.0, 40.0, 95.0, InsertShape.C, 12.0),
    mitsubishi("S10HSCLCR06", 10.0, 7.0, 13.0, 95.0, InsertShape.C, 6.0, 10000115),
    mitsubishi("S16MSCLCR09", 16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0, 10000115),
    mitsubishi("S16MSDUCR07", 16.0, 11.0, 20.0, 93.0, InsertShape.D, 7.0, 10000120),
    mitsubishi("S20QSDUCR11", 20.0, 13.0, 25.0, 93.0, InsertShape.D, 11.0, 10000120),
    mitsubishi("S16MSDQCR07", 16.0, 11.0, 20.0, 107.5, InsertShape.D, 7.0, 10000118),
    mitsubishi("S16MSVQCR11", 16.0, 11.0, 20.0, 117.5, InsertShape.V, 11.0, 10000127),
    mitsubishi("S20QSVUCR11", 20.0, 13.0, 25.0, 93.0, InsertShape.V, 11.0, 10000128),
    mitsubishi("S16MSTFCR11", 16.0, 11.0, 20.0, 91.0, InsertShape.T, 11.0, 10000123),
    mitsubishi("A32SPCLNR12", 32.0, 22.0, 44.0, 95.0, InsertShape.C, 12.0, 10000106),
    korloy("S16R-SDUCR-07", 16.0, 11.0, 20.0, 93.0, InsertShape.D, 7.0),
    korloy("S16R-SCLCR-09", 16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0),
    korloy("S16R-SDQCR-07", 16.0, 11.0, 20.0, 107.5, InsertShape.D, 7.0),
)

/** 프리셋 시트의 전체 줄: 회사 사용 23 → 카탈로그 28. 품번은 모두 다르다(RoundState 가 품번으로 고른 줄을 기억한다) */
internal val HOLDER_PRESETS: List<HolderPreset> = COMPANY_PRESETS + CATALOG_PRESETS
