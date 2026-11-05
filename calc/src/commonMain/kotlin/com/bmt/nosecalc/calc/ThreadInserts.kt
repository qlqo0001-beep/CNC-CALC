package com.bmt.nosecalc.calc

import kotlin.math.abs

/*
 * 나사 인서트 자료 — 풀 프로파일 레이다운 인서트, IC 3/8"(VARDEX 크기 3 · Carmex 크기 16 — 두 회사 표가 같은 IC 3/8" 로 적는다).
 * 사용자 결정(2026-11-05): 팁 이름 "3ER 20UN" 을 기준으로 암나사 짝과 관용 테이퍼 인서트를 넣는다.
 *
 * 자료 원본: docs/research/thread-inserts.json(출처 대조 끝남 — jvmTest ThreadInsertsJsonTest 가 모든 줄 · 칸을 견준다). 행이 적어 Kotlin 리스트로 옮겼다.
 *  - VDX_TT: VARDEX Thread Turning(Vargus 인치판 카탈로그 발췌, 2017 무렵) — 쪽은 인쇄 쪽. 인서트 이름은 등급 앞까지 붙여 씀(3ER20UN…).
 *  - CMX_TT: Carmex Thread Turning(카탈로그 발췌, 2017 무렵) — 표 안 표기대로 띄어 씀(16 ER 20 UN).
 *  - 모든 줄이 풀 프로파일(산봉우리까지 만듦 — VARDEX 19쪽 · Carmex 4쪽의 주문 기호 표).
 *  - 날 끝 R 은 출처에 있는 것만: VARDEX BSPT 의 그림 글자 'R0.137P'(53 · 54쪽 — JIS · ISO 의 r = 0.137278P 와 맞음). UN · NPT 는 두 회사 표 ·
 *    그림 글자에 R 이 없고, Carmex BSPT 도 없다 — null(만들지 않음).
 *  - "VARDEX 3ER = Carmex 16ER" 이라는 부름은 판매처 하나에만 있어 넣지 않았다(크기가 같다는 것만 공식 표 둘로 확인).
 * 관용 테이퍼: PT(R · Rc)는 BSPT 인서트(BS 21 = ISO 7-1 = JIS B 0203 의 55° 꼴), NPT 는 NPT 인서트.
 */

/** 인서트 나사 꼴 */
enum class InsertForm { UN, NPT, BSPT }

/** 인서트 한 줄(오른손 이름 + 같은 줄의 왼손 이름) */
data class ThreadInsert(
    /** "Vargus (VARDEX)" · "Carmex" */
    val maker: String,
    /** 오른손 이름(출처 표기 그대로) */
    val designation: String,
    val lhDesignation: String,
    /** true = 내경(암나사) */
    val internal: Boolean,
    val form: InsertForm,
    /** 25.4 mm 당 산수 */
    val tpi: Double,
    /** 내접원 지름(원문 인치 분수) */
    val ic: String,
    val fullProfile: Boolean,
    /** 날 끝 R ÷ P — 출처에 있을 때만(VARDEX BSPT 0.137) */
    val tipRPerPitch: Double? = null,
    val sourceId: String,
    val page: String,
) {
    val pitch: Double get() = 25.4 / tpi

    /** 날 끝 R(mm) = [tipRPerPitch] × P — 출처에 없으면 null */
    val tipR: Double? get() = tipRPerPitch?.let { it * pitch }
}

object ThreadInserts {
    private const val VDX = "Vargus (VARDEX)"
    private const val CMX = "Carmex"

    /** 40줄 — 원본 JSON 차례(VARDEX · Carmex 마다 UN 20산 외 · 내, NPT 외 5 · 내 5, BSPT 외 4 · 내 4) */
    val ALL: List<ThreadInsert> = listOf(
        // VARDEX — VDX_TT 쪽: UN 외 26 · 내 31, NPT 외 56 · 내 58, BSPT 외 53 · 내 54
        vdx(false, InsertForm.UN, 20.0, "26"),
        vdx(true, InsertForm.UN, 20.0, "31"),
        vdx(false, InsertForm.NPT, 27.0, "56"), vdx(false, InsertForm.NPT, 18.0, "56"), vdx(false, InsertForm.NPT, 14.0, "56"),
        vdx(false, InsertForm.NPT, 11.5, "56"), vdx(false, InsertForm.NPT, 8.0, "56"),
        vdx(true, InsertForm.NPT, 27.0, "58"), vdx(true, InsertForm.NPT, 18.0, "58"), vdx(true, InsertForm.NPT, 14.0, "58"),
        vdx(true, InsertForm.NPT, 11.5, "58"), vdx(true, InsertForm.NPT, 8.0, "58"),
        vdx(false, InsertForm.BSPT, 28.0, "53", 0.137), vdx(false, InsertForm.BSPT, 19.0, "53", 0.137),
        vdx(false, InsertForm.BSPT, 14.0, "53", 0.137), vdx(false, InsertForm.BSPT, 11.0, "53", 0.137),
        vdx(true, InsertForm.BSPT, 28.0, "54", 0.137), vdx(true, InsertForm.BSPT, 19.0, "54", 0.137),
        vdx(true, InsertForm.BSPT, 14.0, "54", 0.137), vdx(true, InsertForm.BSPT, 11.0, "54", 0.137),
        // Carmex — CMX_TT 쪽: UN 13, NPT 20, BSPT 23 (외 · 내 같은 쪽)
        cmx(false, InsertForm.UN, 20.0, "13"),
        cmx(true, InsertForm.UN, 20.0, "13"),
        cmx(false, InsertForm.NPT, 27.0, "20"), cmx(false, InsertForm.NPT, 18.0, "20"), cmx(false, InsertForm.NPT, 14.0, "20"),
        cmx(false, InsertForm.NPT, 11.5, "20"), cmx(false, InsertForm.NPT, 8.0, "20"),
        cmx(true, InsertForm.NPT, 27.0, "20"), cmx(true, InsertForm.NPT, 18.0, "20"), cmx(true, InsertForm.NPT, 14.0, "20"),
        cmx(true, InsertForm.NPT, 11.5, "20"), cmx(true, InsertForm.NPT, 8.0, "20"),
        cmx(false, InsertForm.BSPT, 28.0, "23"), cmx(false, InsertForm.BSPT, 19.0, "23"),
        cmx(false, InsertForm.BSPT, 14.0, "23"), cmx(false, InsertForm.BSPT, 11.0, "23"),
        cmx(true, InsertForm.BSPT, 28.0, "23"), cmx(true, InsertForm.BSPT, 19.0, "23"),
        cmx(true, InsertForm.BSPT, 14.0, "23"), cmx(true, InsertForm.BSPT, 11.0, "23"),
    )

    /** 내외 · 꼴 · 산수가 맞는 줄(VARDEX · Carmex 차례) */
    fun match(inner: Boolean, form: InsertForm, tpi: Double): List<ThreadInsert> =
        ALL.filter { it.internal == inner && it.form == form && abs(it.tpi - tpi) < EPS }

    private fun t(tpi: Double) = Fmt.nc(tpi).trimEnd('.')

    /** VARDEX 이름: 크기 3 + E(외) · I(내) + R · L + 산수 + 꼴, 붙여 씀 — "3ER20UN" · "3IL11.5NPT" */
    private fun vdx(internal: Boolean, form: InsertForm, tpi: Double, page: String, tipRPerPitch: Double? = null): ThreadInsert {
        val io = if (internal) "I" else "E"
        return ThreadInsert(
            VDX, "3${io}R${t(tpi)}${form.name}", "3${io}L${t(tpi)}${form.name}", internal, form, tpi, "3/8\"", true, tipRPerPitch, "VDX_TT", page,
        )
    }

    /** Carmex 이름: 크기 16 + ER · IR(오른손) · EL · IL(왼손) + 산수 + 꼴, 띄어 씀 — "16 ER 20 UN" */
    private fun cmx(internal: Boolean, form: InsertForm, tpi: Double, page: String): ThreadInsert {
        val io = if (internal) "I" else "E"
        return ThreadInsert(
            CMX, "16 ${io}R ${t(tpi)} ${form.name}", "16 ${io}L ${t(tpi)} ${form.name}", internal, form, tpi, "3/8\"", true, null, "CMX_TT", page,
        )
    }
}
