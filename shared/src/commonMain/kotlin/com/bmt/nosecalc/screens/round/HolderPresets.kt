package com.bmt.nosecalc.screens.round

import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.InsertShape

/**
 * 보링바 프리셋 한 줄: 제조사 카탈로그 값 — 바 지름 D(DCON) · f(WF) · 최소 가공경(DMIN) · 절입각 κr(KAPR) · 인서트 모양 ·
 * 날 길이(품번 크기 숫자, DCMT 07 → 7 — 실제 날은 0~1 mm 더 길다). url = 값을 읽은 곳. 돌출 길이 · 노즈 R 은 넣지 않는다
 */
internal class HolderPreset(
    val maker: String,
    val code: String,
    val dia: Double,
    val f: Double,
    val dMin: Double,
    val kappa: Double,
    val shape: InsertShape,
    val edge: Double,
    val url: String,
) {
    val bar: BoringBar get() = BoringBar(dia, f, shape, edge, kappa, dMin = dMin)

    /** '출처:' 줄에 쓰는 사이트 이름 */
    val host: String get() = url.substringAfter("://").substringBefore('/')

    /** 채우는 칸(RoundState.holder 이름 → 글) */
    fun fields(): Map<String, String> =
        mapOf("dia" to plain(dia), "f" to plain(f), "dmin" to plain(dMin), "kr" to plain(kappa), "shape" to shape.name, "edge" to plain(edge))
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

/** 출처를 확인한 28 줄(제조사 순). 이스카는 KAPR 을 내지 않아 뺐다 */
internal val HOLDER_PRESETS: List<HolderPreset> = listOf(
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
