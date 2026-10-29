package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset

/** 홀더 고르기 시트의 묶음 */
internal enum class HolderGroup(val label: String) { OUTER("외경 바이트"), THREAD("나사"), BORING("보링바"), SMALL("소경 보링") }

/**
 * 회사에서 쓰는 홀더 한 줄(사용자 10-29 "회사에서 사용하는 팁홀더도 찾아서 홀더 종류에 추가") — 공구 설정의 '홀더 고르기'.
 * 홀더가 정하는 칸만 든다: 종류 · 인서트 모양 · 절입각 κr. 값은 docs/research/company-holders.json(제조사 카탈로그 · 출처 대조 끝)과 같다 —
 * jvmTest CompanyHoldersJsonTest 가 본다. null = 출처에 없음(지어내지 않는다).
 * 노즈 R(인서트 코너 R) · 팁 번호 · 홈 폭 · 나사 각(인서트 모양)은 홀더가 정하지 않아 건드리지 않는다. 팁 번호는 홀더 손(L/R)과
 * 외경/내경으로도 정할 수 없다 — 같은 손의 홀더가 척 쪽으로 깎는지 반대로 깎는지는 주축 회전 방향(M03/M04)과 다는 방식(터렛 앞/뒤 · 인서트 위/아래)에 따른다.
 * 같은 품번도 제조사마다 κr 이 다르면(STFCR 한국야금 90° · 대구텍 91°) [makers] 로 줄을 나눈다. 단종 표시 제조사는 넣지 않았다.
 * κr 은 시뮬레이터 기준(Z 이송 — 척 쪽 선삭, [com.bmt.nosecalc.calc.sim.ToolShape])일 때만 넣는다: 정면(X) 이송 기준인 K 꼴(DCKNL)의
 * 카탈로그 75° 는 넣지 않는다(json 의 `simulator` 칸).
 */
internal class CompanyHolder(
    val code: String,
    val group: HolderGroup,
    val kind: ToolKind,
    val insert: InsertShape?,
    val kappa: Double?,
    val makers: List<String>,
    val note: String? = null,
) {
    /** 고른 홀더를 기억하는 이름(같은 품번이 제조사로 나뉜 줄도 다르게) */
    val id: String get() = "$code|${makers.joinToString(",")}"

    /** 이 홀더를 고른 공구: 종류, 바이트면 인서트 모양(출처에 없으면 그대로) · κr(출처에 없으면 빈칸 = 가정 값). 나머지 칸은 그대로 */
    fun fill(t: ToolOffset): ToolOffset =
        if (kind == ToolKind.TURN) t.copy(kind = kind, insert = insert ?: t.insert, kappaDeg = kappa) else t.copy(kind = kind)

    /** 고른 뒤 홀더가 정한 칸을 고쳤나('수정됨'). 홀더가 비운 칸에 넣은 값은 고친 것이 아니다 */
    fun edited(t: ToolOffset): Boolean =
        t.kind != kind || (kind == ToolKind.TURN && ((insert != null && t.insert != insert) || (kappa != null && t.kappaDeg != kappa)))
}

private val KOR_TTK = listOf("한국야금", "대구텍")
private val KOR_TTK_MMC = listOf("한국야금", "대구텍", "미쓰비시")

private fun bar(code: String, insert: InsertShape, kappa: Double, makers: List<String>) =
    CompanyHolder(code, HolderGroup.BORING, ToolKind.TURN, insert, kappa, makers)

/** 회사 사용 홀더 20 줄(17 품번): 외경 바이트 → 나사(이 두 묶음 안은 품번 알파벳 순) → 보링바(카바이드 SCLCR · 강 STFCR, 지름 순) → 소경 */
internal val COMPANY_HOLDERS: List<CompanyHolder> = listOf(
    CompanyHolder(
        "DCKNL2525M12", HolderGroup.OUTER, ToolKind.TURN, InsertShape.C, null, listOf("한국야금", "샌드빅"),
        note = "정면 깎기 홀더 — 카탈로그 κr 75° 는 정면(X) 이송 기준이고 C 인서트의 100° 모서리로 깎아 시뮬레이터로는 그릴 수 없습니다. " +
            "κr 은 빈칸(C 95° 가정) — 끝면 쪽 날만 실제와 같습니다.",
    ),
    CompanyHolder("MVJNR2525M16", HolderGroup.OUTER, ToolKind.TURN, InsertShape.V, 93.0, KOR_TTK),
    CompanyHolder("PCLNL2525M12", HolderGroup.OUTER, ToolKind.TURN, InsertShape.C, 95.0, listOf("한국야금", "대구텍", "샌드빅", "미쓰비시")),
    CompanyHolder("SNR0020L09A", HolderGroup.THREAD, ToolKind.THREAD, null, null, listOf("세코"), note = "내경 나사"),
    CompanyHolder("THCER2525M16", HolderGroup.THREAD, ToolKind.THREAD, null, null, emptyList(), note = "외경 나사 — 제조사 공식 카탈로그 · 제품 페이지에서 찾지 못했습니다."),
    bar("C08K-SCLCR06", InsertShape.C, 95.0, KOR_TTK),
    bar("C10K-SCLCR06", InsertShape.C, 95.0, KOR_TTK_MMC),
    bar("C12M-SCLCR06", InsertShape.C, 95.0, KOR_TTK_MMC),
    bar("C12M-SCLCR09", InsertShape.C, 95.0, KOR_TTK),
    bar("C16R-SCLCR09", InsertShape.C, 95.0, KOR_TTK_MMC),
    bar("S10K-STFCR09", InsertShape.T, 91.0, listOf("대구텍")),
    bar("S12M-STFCR09", InsertShape.T, 90.0, listOf("한국야금")),
    bar("S12M-STFCR09", InsertShape.T, 91.0, listOf("대구텍")),
    bar("S12M-STFCR11", InsertShape.T, 90.0, listOf("한국야금")),
    bar("S12M-STFCR11", InsertShape.T, 91.0, listOf("대구텍")),
    bar("S16R-STFCR11", InsertShape.T, 90.0, listOf("한국야금")),
    bar("S16R-STFCR11", InsertShape.T, 91.0, listOf("대구텍")),
    CompanyHolder("SH0516-100", HolderGroup.SMALL, ToolKind.TURN, null, null, listOf("교세라"), note = "보링바 슬리브(구멍 Ø5) — 인서트 · κr 은 끼운 바의 값을 넣으세요."),
    CompanyHolder("SH0616-100", HolderGroup.SMALL, ToolKind.TURN, null, null, listOf("교세라"), note = "보링바 슬리브(구멍 Ø6) — 인서트 · κr 은 끼운 바의 값을 넣으세요."),
    CompanyHolder("PICCO", HolderGroup.SMALL, ToolKind.TURN, null, null, listOf("이스카"), note = "초경 소경 보링바 계열 이름 — 품번을 몰라 인서트 · κr 은 직접 넣으세요."),
)
