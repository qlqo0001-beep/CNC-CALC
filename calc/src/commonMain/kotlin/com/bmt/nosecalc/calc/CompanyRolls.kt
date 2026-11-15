package com.bmt.nosecalc.calc

/*
 * 회사 전조 롤 표 — 회사가 쓰는 LMT Fette 축방향 전조 머리 · 롤(사용자 결정 2026-11-11: 머리 모델 · 롤 번호(AT##)를 앱에 적는다).
 * 코드 상수로만 둔다(조사 JSON docs/research/roll-heads.json 은 Fette · 공개 자료만 — 둘을 섞지 않는다).
 *
 *  - 규격은 UN 20산 일곱 크기. 찾는 열쇠는 (크기 글, 산수 20) — 계열 이름이 7/16 · 1/2 은 UNF, 9/16 · 5/8 은 UN(일정 피치),
 *    3/4 · 7/8 · 1 은 UNEF 로 제각각이라 계열 · label 로 찾지 않는다.
 *  - 머리가 둘 이상인 규격은 [CompanyRoll.heads] 의 첫째가 처음 값(회사 공정 글에서 더 많이 쓴 머리, 1 : 1 이면 회사 표 왼쪽부터 — 결정 D2).
 *    회사 표에서 X 를 친 칸(안 쓰는 롤)은 넣지 않았다(결정 D1).
 *  - 소재 꼴(자동단조 · 자동핵사)별 전조 전 지름 · 회전수 S 와 개수 n 은 회사 YM 프로그램 원문을 손으로 확인한 값만 쓴다(결정 D3).
 *    아직 확인 전이라 모두 null 이다 — 확인한 뒤 [CompanyStock] 칸만 채운다. 한 값으로 모이지 않는 칸(동률 · 흩어짐)은 확인 뒤에도 null 로 둔다.
 *  - 회사 이송: 20산 F1.22(결정 D7′ — 산수 20 이면 비율 1.22 / 1.27, [RollHeadCalc] 의 비율 사슬).
 */

/** 소재 꼴 — 회사 프로그램이 둘로 나뉜다. 화면 처음 값은 [HEX](결정 D16) */
enum class RollStock { FORGED, HEX }

/** 회사 머리 · 롤 한 짝: 머리 모델(예 "F34") · 롤 번호(예 "AT08") */
data class CompanyHead(val model: String, val roll: String)

/**
 * 소재 꼴 하나의 회사 값 — 원문을 확인한 값만. 확인 전 · 흩어짐은 null.
 * [blankDia] 전조 전 지름(앞 공정 정삭 X, mm) · [blankCount] 그 값을 쓴 블록 수, [rpm] 회전수 S · [rpmCount] 그 값을 쓴 블록 수
 */
data class CompanyStock(
    val blankDia: Double? = null,
    val blankCount: Int? = null,
    val rpm: Long? = null,
    val rpmCount: Int? = null,
)

/** 규격 한 줄. [heads] 첫째가 처음 값 */
data class CompanyRoll(val size: String, val heads: List<CompanyHead>, val forged: CompanyStock, val hex: CompanyStock) {
    val defaultHead: CompanyHead get() = heads.first()

    fun stock(s: RollStock): CompanyStock = when (s) {
        RollStock.FORGED -> forged
        RollStock.HEX -> hex
    }
}

object CompanyRolls {
    /** 회사 롤의 산수(모두 20산) */
    const val TPI = 20

    /** 회사 20산 이송 F(mm/rev) — 결정 D7 */
    const val FEED_20TPI = 1.22

    private val NONE = CompanyStock()

    /** 일곱 크기(지름 순) */
    val ALL: List<CompanyRoll> = listOf(
        CompanyRoll("7/16", listOf(CompanyHead("F23", "AT09"), CompanyHead("F2", "AT06")), NONE, NONE),
        CompanyRoll("1/2", listOf(CompanyHead("F2", "AT06"), CompanyHead("F23", "AT09")), NONE, NONE),
        CompanyRoll("9/16", listOf(CompanyHead("F23", "AT01")), NONE, NONE),
        CompanyRoll("5/8", listOf(CompanyHead("F23", "AT01")), NONE, NONE),
        CompanyRoll("3/4", listOf(CompanyHead("F34", "AT08"), CompanyHead("F233400", "AT02")), NONE, NONE),
        CompanyRoll("7/8", listOf(CompanyHead("F34", "AT07")), NONE, NONE),
        CompanyRoll("1", listOf(CompanyHead("F34", "AT05")), NONE, NONE),
    )

    /** [spec] 의 회사 줄 — UN 20산이고 크기가 표에 있을 때만 */
    fun of(spec: ThreadSpec?): CompanyRoll? =
        spec?.takeIf { it.kind == ThreadKind.UN && it.tpi == TPI }?.let { s -> ALL.firstOrNull { it.size == s.size } }

    /** 회사 롤 규격의 ThreadTable 줄(지름 순) — 사이즈 시트의 '회사 롤 규격' 묶음 */
    val specs: List<ThreadSpec> get() = ALL.map { r -> ThreadTable.UN.single { it.size == r.size && it.tpi == TPI } }
}
