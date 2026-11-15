package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * 전조 다이스 — LMT Fette 축방향 전조 머리(터렛에 서 있고 공작물이 돈다)로 숫나사를 눌러 만드는 글(전조 구간만).
 *
 *   G97S… · G0X0.Z(진입 20.) · Z(시작 — 빈칸 5.) · G1Z(끝)F… · G4U(멈춤 1.) · G0Z(물러남 150.)   ← 다이스 헤드와 같은 꼴([headProgram])
 *
 * 근거(docs/research/roll-heads.json — LMT Fette 사용 설명서 F1–F3 EVO · 카탈로그):
 *  - 전조 전 지름 dV ≈ d2 − 0.03 mm(d2 = 유효지름). 앱의 추천 = 급수(UN 2A · 미터 6g) 유효지름 범위를 0.03 내린 범위, 한 값은 그 가운데
 *    (결정 D21 — 설명서가 d2 를 공차 안 어디로 잡을지 말하지 않아 메인 루프가 정함, 끝 보고에서 물음).
 *    dV 는 NC 에 들어가지 않는다 — 앞 공정 정삭 X 에 쓰는 값.
 *  - 회전수 n ≈ 1000·v / (π·dV) — d 는 전조 전 지름(설명서 5.2.1). v 는 재질 표(Tab. 6): 1.4401(STS316) 30–50, 1.4301(STS304) 35–55 m/min.
 *    표는 범위만 준다 — 추천 한 값은 범위 아래 끝(사용자 결정 2026-11-14).
 *  - 기계 이송 f ≈ 0.95 × P(피치보다 5 % 작게, 설명서 5.2.4). 20산이 아닌 규격의 비율 처음 값으로 쓴다(결정 D20 — 계획은 0.96 이었으나 출처 있는 값으로 바꿈, 끝 보고에서 물음).
 *  - 열림 행정 a(Tab. 8: F2 · F23 · F233400 3 mm, F3 · F34 4 mm): 끝 Z 에서 이송을 멈추면 머리가 a 만큼 더 나가며 열린다(x ≈ lU − a).
 *    그래서 실제 전조 끝 ≈ 끝 Z − a, 전조 길이 ≈ (끝면 Z − 끝 Z) + a. 최대 전조 길이는 카탈로그 선택표(F23 일부 19 · F34 일부 24 · F233400 73 mm).
 *  - 머리가 열리려면 충분히 머물러야 한다(설명서 5.2) — 멈춤 0(G4 줄 없음)이면 알린다.
 *
 * 사슬:
 *  - 회전수: 넣은 회전수 → (소재가 스테인리스이고 회사 표 규격이면) 회사 S(규격 × 소재 꼴) → Fette 추천(스테인리스만 — 다른 ISO 군은 재질 표 줄을
 *    가를 수 없어 없음) → 없으면 MISSING_INPUT(회전수는 NC 에 들어간다).
 *  - 이송 비율: 넣은(저장한) 비율 → 산수 20 이면 회사 F1.22(비율 1.22/1.27 — F 를 1.22 로 찍는다) → Fette 0.95(D20).
 *  - 전조 전 지름(회전수에 쓰는 값): 회사 값(규격 × 소재 꼴, 확인한 값만) → Fette 추천 한 값.
 * 숫나사 · M · UN · 곧은 나사 · 정가공(Z−)만 — 타입이 막는다(내외 칸이 없고 관용 테이퍼 [PipeSpec] 은 받지 않는다).
 */

/** 스테인리스 줄(Fette 재질 표 Tab. 6) — 화면 처음 값 STS316(회사가 주로 씀) */
enum class RollSteel(val materialNo: String, val label: String) {
    STS316("1.4401", "STS316"),
    STS304("1.4301", "STS304"),
}

/** 전조 다이스 경고. 위의 넷은 계산을 멈춘다([RollResult.solution] = null), 아래는 숫자와 함께 */
enum class RollWarning {
    /** 규격 · 끝 Z 가 비었거나, 회전수가 비었는데 회사 값 · Fette 추천도 없다 */
    MISSING_INPUT,

    /** 숫자가 아님, 크기 한도 밖, 비율 ≤ 0, 멈춤 < 0, 회전수 < 1, 찍힐 S 가 1 미만 · [G76Calc.MAX_RPM] 초과, 찍힐 F(0.001)가 0 */
    BAD_INPUT,

    /** 머리를 정했는데 그 머리의 지름 범위(카탈로그) 밖이거나, 머리를 안 정했는데 지름 범위에 드는 EVOline 머리가 없다 — 회사 표의 머리 짝은 멈추지 않는다 */
    OUT_OF_HEAD_RANGE,

    /** 시작 Z ≤ 끝 Z, 진입 Z < 시작 Z, 물러남 Z < 시작 Z, 끝면 Z > 시작 Z · ≤ 끝 Z */
    Z_ORDER,

    /* ↑ 멈춤 · ↓ 숫자와 함께 */

    /** 비율 > 1 — 기계 이송이 피치보다 빠르다(Fette 는 피치보다 5 % 작게) */
    RATIO_OVER_ONE,

    /** 넣은 회전수가 Fette 재질 표 범위의 회전수(아래 끝 · 위 끝을 반올림한 S) 밖(스테인리스일 때만 — 회사 값 · 추천 값에는 내지 않는다) */
    RPM_OUT_OF_RANGE,

    /** 회사 표의 머리 짝인데 카탈로그 지름 범위 밖 — 멈추지 않고 알린다 */
    HEAD_RANGE_COMPANY,

    /** 산수(UN) · 피치(미터)가 그 머리 카탈로그 롤 목록의 범위 밖 — 롤을 따로 확인 */
    ROLL_NOT_LISTED,

    /** 전조 길이((끝면 Z − 끝 Z) + a)가 최대 전조 길이(카탈로그 선택표 — [RollHeadCatalog.maxRollLength])를 넘는다 */
    ROLL_LENGTH_OVER,

    /** 끝 멈춤이 0(찍힐 값 0 — G4 줄 없음): 머리가 열리기 전에 급송으로 물러난다(설명서 5.2 '열릴 만큼 충분히 머문다') */
    DWELL_ZERO,
}

/** 회전수를 정한 근거 */
enum class RollRpmBasis { INPUT, COMPANY, FETTE }

/** 이송 비율을 정한 근거 */
enum class RollRatioBasis { INPUT, COMPANY, FETTE }

/** 회전수에 쓴 전조 전 지름의 근거 */
enum class RollBlankBasis { COMPANY, FETTE }

/** 입력. 길이 mm, X 는 늘 0(머리가 중심에) */
data class RollInput(
    val spec: ThreadSpec? = null,
    /** null = 스테인리스(M)로 본다(결정 D6) */
    val material: IsoGroup? = null,
    val steel: RollSteel = RollSteel.STS316,
    /** 머리 회사 표기("F34") — null = 회사 처음 값, 회사 표 밖이면 지름 범위에 드는 첫 EVOline 머리 */
    val head: String? = null,
    val stock: RollStock = RollStock.HEX,
    /** 회전수 직접(min⁻¹) */
    val rpm: Double? = null,
    /** 시작 Z — null = [RollHeadCalc.Z_START_DEFAULT] */
    val zStart: Double? = null,
    val zEnd: Double? = null,
    /** 이송 비율 F / P — null = 사슬(회사 20산 → Fette) */
    val ratio: Double? = null,
    val dwell: Double = RollHeadCalc.DWELL_DEFAULT,
    val retractZ: Double = RollHeadCalc.RETRACT_Z_DEFAULT,
    val approachZ: Double = RollHeadCalc.APPROACH_Z_DEFAULT,
    /** 공작물 끝면 Z — 전조 길이 검사를 여기부터. null = 시작 Z */
    val zFace: Double? = null,
)

/** 전조 전 지름: 회사 값(확인한 값만) · Fette 추천 범위와 한 값 · 회전수에 쓴 값 */
data class RollBlank(
    val company: Double?,
    val companyCount: Int?,
    val recMin: Double,
    val recMax: Double,
    val rec: Double,
    val used: Double,
    val usedBasis: RollBlankBasis,
)

/** 사람이 안 넣었을 때 쓰는 값(자리표시 · 요령 방향의 기준) — 계산이 멈춰도 낼 수 있는 것 */
data class RollDefaults(
    val head: RollHeadType?,
    val companyHead: CompanyHead?,
    val blank: RollBlank?,
    /** 회사 S → Fette 추천(없으면 null) */
    val rpm: Long?,
    val rpmBasis: RollRpmBasis?,
    /** Fette 범위의 회전수(스테인리스일 때) */
    val rpmRange: Pair<Long, Long>?,
    val speedRow: RollSpeed?,
    val ratio: Double,
    val ratioBasis: RollRatioBasis,
)

data class RollSolution(
    val head: RollHeadType,
    /** 회사 롤(이 규격 · 머리가 회사 표에 있을 때) */
    val companyHead: CompanyHead?,
    val blank: RollBlank,
    /** 찍는 S */
    val rpm: Long,
    val rpmBasis: RollRpmBasis,
    /** 사람이 안 넣었으면 쓰였을 S(요령 방향 기준) */
    val baseRpm: Long?,
    val rpmRange: Pair<Long, Long>?,
    val speedRow: RollSpeed?,
    /** 쓴 전조 속도 = π·dV·S/1000 (m/min, dV = [RollBlank.used]) */
    val vUsed: Double,
    /** 이송(mm/rev) — 회사 20산이면 1.22, 아니면 비율 × P */
    val feed: Double,
    val ratio: Double,
    val ratioBasis: RollRatioBasis,
    val baseRatio: Double,
    val feedPerMin: Double,
    /** G1 + 멈춤(초) */
    val timeSec: Double,
    val zStart: Double,
    /** 열림 행정 a(머리에 값이 없으면 null) */
    val openingMovement: Double?,
    /** 실제 전조 끝 ≈ 끝 Z − a */
    val rollEndZ: Double?,
    /** 전조 길이 ≈ (끝면 Z − 끝 Z) + a, 끝면 = 넣은 끝면 · 없으면 시작 Z */
    val rolledLength: Double?,
    /** 최대 전조 길이(선택표 → 머리 칸, 없으면 무제한 null) */
    val maxRollLength: Double?,
    /** 전조 뒤 바깥지름 범위(UN 2A · 미터 6g) */
    val crest: Span,
    /** 넣은 회전수가 기준보다 크면 RPM_UP, 작으면 RPM_DOWN(같거나 기준이 없으면 null) */
    val rpmDirection: RollTipGroup?,
    /** 넣은 비율이 기준보다 크면 RATIO_UP, 작으면 RATIO_DOWN */
    val ratioDirection: RollTipGroup?,
    val program: List<String>,
)

data class RollResult(val solution: RollSolution?, val warnings: List<RollWarning>)

object RollHeadCalc {
    /** 시작 Z 빈칸(결정 D8 — 사용자 결정 2026-11-11 'z5.') */
    const val Z_START_DEFAULT = 5.0

    const val DWELL_DEFAULT = DieHeadCalc.DWELL_DEFAULT
    const val RETRACT_Z_DEFAULT = DieHeadCalc.RETRACT_Z_DEFAULT
    const val APPROACH_Z_DEFAULT = DieHeadCalc.APPROACH_Z_DEFAULT

    /** Fette 이송 비율(카탈로그에 값이 없으면 쓰는 값과 같다 — 설명서 5.2.4) */
    val FETTE_RATIO: Double get() = RollHeadCatalog.data.feed.ratioRec ?: 0.95

    /** 전조 전 지름 = d2 − 이 값(설명서 5.1.1) */
    val BLANK_OFFSET: Double get() = RollHeadCatalog.data.blankDia.offsetMm ?: 0.03

    /** 지름 범위에 드는 EVOline 머리(JSON 차례) */
    fun headsFor(spec: ThreadSpec): List<RollHeadType> = RollHeadCatalog.evoHeads.filter { inRange(it, spec) }

    /** 지름이 머리의 카탈로그 범위 안인가(범위가 비었으면 안으로 본다) */
    fun inRange(h: RollHeadType, spec: ThreadSpec): Boolean =
        (h.threadDiaMinMm == null || spec.d >= h.threadDiaMinMm - EPS) && (h.threadDiaMaxMm == null || spec.d <= h.threadDiaMaxMm + EPS)

    /** 산수 · 피치가 그 머리 롤 목록 범위 안인가 */
    fun rollListed(h: RollHeadType, spec: ThreadSpec): Boolean = when (spec.kind) {
        ThreadKind.UN -> spec.tpi!!.toDouble().let { t -> (h.tpiMin == null || t >= h.tpiMin - EPS) && (h.tpiMax == null || t <= h.tpiMax + EPS) }
        ThreadKind.METRIC -> (h.pitchMinMm == null || spec.pitch >= h.pitchMinMm - EPS) && (h.pitchMaxMm == null || spec.pitch <= h.pitchMaxMm + EPS)
    }

    /** 고른 머리: 넣은 표기 → 회사 처음 값 → 지름 범위에 들고 롤 목록에 산수 · 피치가 있는 첫 EVOline 머리 → 지름 범위에 드는 첫 머리 */
    fun pickHead(spec: ThreadSpec, key: String?): RollHeadType? {
        key?.let { return RollHeadCatalog.head(it) }
        CompanyRolls.of(spec)?.let { return RollHeadCatalog.head(it.defaultHead.model) }
        val heads = headsFor(spec)
        return heads.firstOrNull { rollListed(it, spec) } ?: heads.firstOrNull()
    }

    /** Fette 추천 전조 전 지름 범위 · 한 값(급수 기본 — UN 2A · 미터 6g 의 유효지름 − 0.03) */
    fun recommendedBlank(spec: ThreadSpec): Triple<Double, Double, Double> {
        val pd = ThreadTol.limits(spec, ThreadClass.default(spec.kind, false)).pitchDia
        val off = BLANK_OFFSET
        return Triple(pd.min - off, pd.max - off, (pd.min + pd.max) / 2 - off)
    }

    /** 사람이 안 넣었을 때 쓰는 값 */
    fun defaults(i: RollInput): RollDefaults {
        val spec = i.spec ?: return RollDefaults(null, null, null, null, null, null, null, FETTE_RATIO, RollRatioBasis.FETTE)
        val head = pickHead(spec, i.head)
        val company = CompanyRolls.of(spec)
        val companyHead = head?.let { h -> company?.heads?.firstOrNull { it.model == h.key } }
        val stock = company?.stock(i.stock)
        val (lo, hi, mid) = recommendedBlank(spec)
        val blank = if (stock?.blankDia != null) {
            RollBlank(stock.blankDia, stock.blankCount, lo, hi, mid, stock.blankDia, RollBlankBasis.COMPANY)
        } else {
            RollBlank(null, null, lo, hi, mid, mid, RollBlankBasis.FETTE)
        }
        val stainless = (i.material ?: IsoGroup.M) == IsoGroup.M
        val row = if (stainless) RollHeadCatalog.speed(i.steel.materialNo) else null
        fun n(v: Double) = (1000 * v / (PI * blank.used)).roundToLong()
        val range = row?.let { r -> if (r.vMinMpm != null && r.vMaxMpm != null) n(r.vMinMpm) to n(r.vMaxMpm) else null }
        val companyRpm = stock?.rpm?.takeIf { stainless }
        val (rpm, basis) = when {
            companyRpm != null -> companyRpm to RollRpmBasis.COMPANY
            row?.vMinMpm != null -> n(row.vMinMpm) to RollRpmBasis.FETTE
            else -> null to null
        }
        val (ratio, ratioBasis) = if (spec.kind == ThreadKind.UN && spec.tpi == CompanyRolls.TPI) {
            CompanyRolls.FEED_20TPI / spec.pitch to RollRatioBasis.COMPANY
        } else {
            FETTE_RATIO to RollRatioBasis.FETTE
        }
        return RollDefaults(head, companyHead, blank, rpm, basis, range, row, ratio, ratioBasis)
    }

    fun calc(i: RollInput): RollResult {
        val spec = i.spec
        if (spec == null || i.zEnd == null) return stop(RollWarning.MISSING_INPUT)
        val zStart = i.zStart ?: Z_START_DEFAULT
        val reals = listOfNotNull(i.rpm, i.zStart, i.zEnd, i.ratio, i.dwell, i.retractZ, i.approachZ, i.zFace)
        val bad = reals.any { !it.isFinite() } || tooBig(zStart, i.zEnd, i.retractZ, i.approachZ, i.dwell, i.zFace) ||
            (i.ratio != null && i.ratio <= 0) || i.dwell < 0 || (i.rpm != null && i.rpm < 1)
        if (bad) return stop(RollWarning.BAD_INPUT)
        val d = defaults(i)
        val head = d.head ?: return stop(RollWarning.OUT_OF_HEAD_RANGE)
        val inRange = inRange(head, spec)
        if (!inRange && d.companyHead == null) return stop(RollWarning.OUT_OF_HEAD_RANGE)
        val s = i.rpm?.roundToLong() ?: d.rpm ?: return stop(RollWarning.MISSING_INPUT)
        val rpmBasis = if (i.rpm != null) RollRpmBasis.INPUT else d.rpmBasis!!
        val ratio = i.ratio ?: d.ratio
        val ratioBasis = if (i.ratio != null) RollRatioBasis.INPUT else d.ratioBasis
        // 회사 20산은 F 를 1.22 로(비율 × P 는 부동소수 끝자리가 흔들린다)
        val feed = if (ratioBasis == RollRatioBasis.COMPANY) CompanyRolls.FEED_20TPI else ratio * spec.pitch
        if (s < 1 || s > G76Calc.MAX_RPM || G76Calc.um(feed) < 1 || tooBig(feed)) return stop(RollWarning.BAD_INPUT)
        if (zStart <= i.zEnd || i.approachZ < zStart || i.retractZ < zStart) return stop(RollWarning.Z_ORDER)
        if (i.zFace != null && (i.zFace > zStart || i.zFace <= i.zEnd)) return stop(RollWarning.Z_ORDER)

        val blank = d.blank!!
        val vUsed = PI * blank.used * s / 1000
        val a = head.openingMovementMm
        val face = i.zFace ?: zStart
        val rolledLength = a?.let { (face - i.zEnd) + it }
        val feedPerMin = Cutting.feedRate(feed, s.toDouble())
        val row = d.speedRow
        val maxLen = RollHeadCatalog.maxRollLength(head, spec)
        val warnings = buildList {
            if (ratio > 1 + EPS) add(RollWarning.RATIO_OVER_ONE)
            // 범위는 찍히는 S(반올림)로 본다 — 추천 S(아래 끝을 반올림)를 그대로 넣었는데 v 가 30 아래 29.98 이라 경고가 나지 않게
            val range = d.rpmRange
            if (rpmBasis == RollRpmBasis.INPUT && range != null && (s < range.first || s > range.second)) add(RollWarning.RPM_OUT_OF_RANGE)
            if (!inRange) add(RollWarning.HEAD_RANGE_COMPANY)
            if (!rollListed(head, spec)) add(RollWarning.ROLL_NOT_LISTED)
            if (rolledLength != null && maxLen != null && rolledLength > maxLen + EPS) add(RollWarning.ROLL_LENGTH_OVER)
            if (!headDwells(i.dwell)) add(RollWarning.DWELL_ZERO)
        }
        fun dir(v: Double, base: Double?, up: RollTipGroup, down: RollTipGroup): RollTipGroup? = when {
            base == null || abs(v - base) <= 1e-6 * maxOf(1.0, abs(base)) -> null
            v > base -> up
            else -> down
        }
        val sol = RollSolution(
            head = head,
            companyHead = d.companyHead,
            blank = blank,
            rpm = s,
            rpmBasis = rpmBasis,
            baseRpm = d.rpm,
            rpmRange = d.rpmRange,
            speedRow = row,
            vUsed = vUsed,
            feed = feed,
            ratio = ratio,
            ratioBasis = ratioBasis,
            baseRatio = d.ratio,
            feedPerMin = feedPerMin,
            timeSec = 60 * (zStart - i.zEnd) / feedPerMin + i.dwell,
            zStart = zStart,
            openingMovement = a,
            rollEndZ = a?.let { i.zEnd - it },
            rolledLength = rolledLength,
            maxRollLength = maxLen,
            crest = ThreadTol.limits(spec, ThreadClass.default(spec.kind, false)).crest,
            rpmDirection = if (rpmBasis == RollRpmBasis.INPUT) dir(s.toDouble(), d.rpm?.toDouble(), RollTipGroup.RPM_UP, RollTipGroup.RPM_DOWN) else null,
            ratioDirection = if (ratioBasis == RollRatioBasis.INPUT) dir(ratio, d.ratio, RollTipGroup.RATIO_UP, RollTipGroup.RATIO_DOWN) else null,
            program = headProgram(s, i.approachZ, zStart, i.zEnd, feed, i.dwell, i.retractZ),
        )
        return RollResult(sol, warnings)
    }

    private fun stop(w: RollWarning) = RollResult(null, listOf(w))
}
