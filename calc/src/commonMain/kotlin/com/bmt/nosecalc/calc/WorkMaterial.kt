package com.bmt.nosecalc.calc

/*
 * 재료분석 — ISO 피삭재 군(P M K N S H) 설명과 대표 재질의 규격 대조. 절삭속도 숫자는 넣지 않는다(공식 출처가 있는 것은
 * 드릴 카탈로그 값뿐 — [DrillCatalog]).
 *
 * 출처(확인함, 2026-10-28):
 *  - 군 설명: Mitsubishi Materials 교육 자료 "Classification" · "Group P/M/K/N/S/H"
 *      https://www.mmc-carbide.com/permanent/courses/78/classification.html (group-p.html … group-h.html) — 요약해 옮김.
 *      "ISO adopted a new standard based on six main groups … based on material machinability"(2003).
 *  - 규격 대조(KS · ISO · JIS · 미국): KORLOY Cutting Tools_Turning 2025-2026 F3~F6 "Workpiece Material Grades"(PDF 431~434쪽)
 *      https://www.korloy.com/en/ebook/Cutting%20Tools_Turning%2025-26(EM)/assets/contents/download.pdf
 *      칸 글을 그대로 옮겼다(쪽 그림과 글자층 좌표로 같은 줄인지 확인). 표에 '-' 인 칸은 null.
 *  - S · H 의 예와 경도: 드릴 카탈로그 자료(docs/research/drill-catalog.json, 검증 끝)의 재질 줄.
 */

/** ISO 피삭재 군 */
enum class IsoGroup { P, M, K, N, S, H }

/** 대표 재질 하나 — 규격 대조표의 한 줄 */
data class MaterialRef(
    /** 한글 이름 */
    val name: String,
    /** KS 기호(표에 없으면 null) */
    val ks: String?,
    /** ISO 기호(표 칸 그대로, 여럿이면 ", ") */
    val iso: String?,
    val jis: String?,
    /** 미국 칸(AISI/SAE · UNS · ASTM — 표 칸 그대로) */
    val us: String?,
    val note: String? = null,
    /** 출처 + 쪽 */
    val source: String,
)

data class IsoGroupInfo(
    val group: IsoGroup,
    /** 짧은 이름(예: 강) */
    val title: String,
    /** 들어가는 재료 */
    val covers: String,
    /** 가공 특성 */
    val machining: String,
    val examples: List<MaterialRef>,
    val source: String,
)

object WorkMaterials {
    private const val KORLOY = "KORLOY Turning 2025-2026"
    private const val MMC = "Mitsubishi Materials 교육 자료 'Group %s' (mmc-carbide.com/permanent/courses/78)"

    private fun k(page: String) = "$KORLOY $page"

    val groups: List<IsoGroupInfo> = listOf(
        IsoGroupInfo(
            IsoGroup.P, "강",
            covers = "연강 · 탄소강 · 합금강(주강 포함).",
            machining = "이어지는 칩이 나와 칩 처리와 용착이 문제다. 연강은 무르고 잘 붙어 칩 처리 · 용착에 강한 공구를, " +
                "탄소가 많은 강은 단단하고 절삭열이 커 내열성 있는 재종을 고른다.",
            examples = listOf(
                MaterialRef("기계구조용 탄소강", "SM20C", null, "S20C", "1020", source = k("F3 (PDF 431쪽)")),
                MaterialRef("기계구조용 탄소강", "SM45C", "C45, C45E4, C45M2", "S45C", "1045, 1046", source = k("F3 (PDF 431쪽)")),
                MaterialRef("크롬몰리브덴강", "SCM435(H)", "34CrMo4, 34CrMoS4", "SCM435(H)", "(4135H), 4137(H)", source = k("F4 (PDF 432쪽)")),
                MaterialRef("크롬몰리브덴강", "SCM440(H)", "42CrMo4, 42CrMoS4", "SCM440(H)", "4140(H), 4142(H)", source = k("F4 (PDF 432쪽)")),
                MaterialRef("니켈크롬몰리브덴강", "SNCM439", null, "SNCM439", "4340", source = k("F3 (PDF 431쪽)")),
                MaterialRef("쾌삭강", "SUM24L", "11SMnPb28", "SUM24L", "12L14", source = k("F5 (PDF 433쪽)")),
            ),
            source = MMC.replace("%s", "P"),
        ),
        IsoGroupInfo(
            IsoGroup.M, "스테인리스강",
            covers = "스테인리스강(오스테나이트계 등).",
            machining = "점성이 크고 가공 경화가 심하며 열이 잘 빠지지 않아 절삭저항 · 절삭열이 크다. 용착 · 경사면 마모 · 경계(노치) 마모와 " +
                "버(burr)가 잘 생긴다 — 날카로운 날과 내열 재종.",
            examples = listOf(
                MaterialRef("오스테나이트계 스테인리스", "STS303", "X10CrNiS18-9", "SUS303", "S30300, 303", source = k("F5 (PDF 433쪽)")),
                MaterialRef("오스테나이트계 스테인리스", "STS304", "X5CrNi18-9, X2CrNi18-9", "SUS304", "S30400, 304", source = k("F5 (PDF 433쪽)")),
                MaterialRef("오스테나이트계 스테인리스", "STS304L", "X2CrNi19-11", "SUS304L", "S30403, 304L", source = k("F5 (PDF 433쪽)")),
                MaterialRef("오스테나이트계 스테인리스", "STS316", "X5CrNiMo17-12-2, X3CrNiMo17-12-3", "SUS316", "S31600, 316", source = k("F5 (PDF 433쪽)")),
                MaterialRef(
                    "오스테나이트계 스테인리스", "STS316L", "X2CrNiMo17-12-2, X2CrNiMo17-12-3, X2CrNiMo18-14-3", "SUS316L", "S31603, 316L",
                    source = k("F5 (PDF 433쪽)"),
                ),
                MaterialRef(
                    "페라이트계 스테인리스", "STS430", "X6Cr17", "SUS430", "S43000, 430", note = FERRITIC_NOTE, source = k("F5 (PDF 433쪽)"),
                ),
                MaterialRef(
                    "마르텐사이트계 스테인리스", "STS410", "X12Cr13", "SUS410", "S41000, 410", note = FERRITIC_NOTE, source = k("F5 (PDF 433쪽)"),
                ),
            ),
            source = MMC.replace("%s", "M"),
        ),
        IsoGroupInfo(
            IsoGroup.K, "주철",
            covers = "회주철 · 구상흑연주철 · 가단주철 등 주철.",
            machining = "부서지는 칩이 나와 진동으로 날이 이 빠지기 쉽고 긁힘 마모가 크다(절삭저항은 강보다 낮다). " +
                "구상흑연 · 가단주철은 칩이 이어지기도 하고 저항이 커 경사면 마모가 날 가까이 생긴다. 주물 겉면(스케일)이 단단하다.",
            examples = listOf(
                MaterialRef("회주철", "GC250", "250", "FC250", "No 35 B", note = "ISO 칸은 GC100~GC350 이 한 칸(100 · 150 · 200 · 250 · 300 · 350)", source = k("F6 (PDF 434쪽)")),
                MaterialRef("구상흑연주철", "GCD400-15", "400-15", "FCD400", "60-40-18", source = k("F6 (PDF 434쪽)")),
                MaterialRef("구상흑연주철", "GCD600-3", "600-3", "FCD600", "80-55-06", source = k("F6 (PDF 434쪽)")),
            ),
            source = MMC.replace("%s", "K"),
        ),
        IsoGroupInfo(
            IsoGroup.N, "비철금속",
            covers = "알루미늄 · 마그네슘 · 구리 합금 등 철이 아닌 금속.",
            machining = "무르고 잘 붙어 칩 처리와 용착(구성인선)이 문제 — 절삭저항이 낮은 날카로운 공구. 실리콘(Si)이 많은 알루미늄 합금은 " +
                "단단한 입자로 마모가 크다. 마그네슘 칩은 불이 붙을 수 있다.",
            examples = listOf(
                MaterialRef("알루미늄 합금 압출재", "A6061S", "AlMg1SiCu", "A6061S", "6061", source = k("F6 (PDF 434쪽)")),
                MaterialRef("알루미늄 합금 압출재", "A7075S", "AlZn5.5MgCu", "A7075S", "7075", source = k("F6 (PDF 434쪽)")),
                MaterialRef("알루미늄 합금 주물", "AC4C", "Al-Si7Mg(Fe)", "AC4C", "356.0", source = k("F6 (PDF 434쪽)")),
                MaterialRef("알루미늄 합금 다이캐스팅", "ALDC8", null, "ADC12", "383.0", source = k("F6 (PDF 434쪽)")),
            ),
            source = MMC.replace("%s", "N"),
        ),
        IsoGroupInfo(
            IsoGroup.S, "내열합금 · 티타늄",
            covers = "니켈 · 코발트 · 철계 내열합금(초내열합금)과 티타늄 · 티타늄 합금.",
            machining = "절삭저항이 크고 열이 잘 빠지지 않아 날 끝에 열이 몰린다 — 날카로운 날, 내열 재종, 칩 방향 조절.",
            examples = listOf(
                MaterialRef(
                    "니켈계 내열합금", null, null, null, "Inconel 718",
                    note = "KS 대조 없음(대조표에 초내열합금 줄이 없다)", source = "Mitsubishi MVE 절삭조건표 'Heat Resistant Alloys Inconel718 etc' (MMC-MVE p.1)",
                ),
                MaterialRef(
                    "티타늄 α+β 합금", null, null, null, null,
                    note = "인장강도 1050 N/mm² 급. KS 대조 없음", source = "ISCAR 홀메이킹 카탈로그 2025 재질 No.37 · YG-1 YE24 VDI 37",
                ),
            ),
            source = MMC.replace("%s", "S"),
        ),
        IsoGroupInfo(
            IsoGroup.H, "고경도강",
            covers = "담금질 · 뜨임으로 경화한 강, 칠드 주철 등 열처리로 단단해진 재료.",
            machining = "경도로 공구가 빨리 닳는다. 충격과 높은 절삭열을 견디는 날 · 재종, 충분한 기계 · 고정구 강성이 필요하다. " +
                "표면만 경화한 것은 경계 마모를 주의.",
            examples = listOf(
                MaterialRef("냉간 금형용 합금공구강", "STD11", null, "SKD11", "D2", note = HARD_NOTE, source = k("F4 (PDF 432쪽)")),
                MaterialRef("열간 금형용 합금공구강", "STD61", "X40CrMoV5-1", "SKD61", "H13", note = HARD_NOTE, source = k("F4 (PDF 432쪽)")),
            ),
            source = MMC.replace("%s", "H"),
        ),
    )

    fun of(g: IsoGroup): IsoGroupInfo = groups.first { it.group == g }

    private const val FERRITIC_NOTE =
        "공구사마다 P 또는 M 에 둔다 — Sandvik 재질 코드는 P5 지만 CoroDrill 870 절삭표는 M 칸(드릴 카탈로그 자료). 쓰는 공구 표의 재질 줄을 따른다"
    private const val HARD_NOTE =
        "담금질 · 뜨임으로 경화한 것. 풀림 상태는 합금강(P). H 로 보는 경도는 공구사 표마다 다르다 — Mitsubishi MVE 40~55 HRC, Sandvik 860 47~60 HRC, ISCAR No.38 55 HRC"
}
