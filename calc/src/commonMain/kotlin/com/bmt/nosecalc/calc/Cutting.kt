package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt

/*
 * 재료·절삭조건 — 기본계산기(선반 · 드릴 공통 식). 단위: 지름 · 길이 mm, Vc m/min, n min⁻¹(rpm), f mm/rev, vf mm/min, 거칠기 μm.
 *
 * 식과 출처(모두 확인함, 2026-10-28):
 *  - Vc = π·D·n/1000, n = 1000·Vc/(π·D), Tc = lm/(fn·n)(분)
 *      Sandvik Coromant "General turning formulas and definitions"
 *      https://www.sandvik.coromant.com/en-gb/knowledge/machining-formulas-definitions/general-turning-formulas-definitions
 *      · KORLOY Cutting Tools_Turning 2025-2026 F10 "Calculation formulas for machining"(PDF 438쪽) "vc = π×D×n/1000 (m/min)"
 *  - vf = fn·n (선반 · 드릴): KORLOY 같은 쪽 "fn = vf/n (mm/rev)" · Haas "Mill Speeds and Feeds (Metric)" 'FEED DRILLS Vf = fn x n'
 *      https://www.haascnc.com/content/dam/haascnc/en/service/reference/programming-workbooks/mill---speeds-and-feeds-formulas---metric.pdf
 *      · Sandvik "Drilling formulas and definitions" 예 "15 × 1000/vf = 15 × 1000/(fn × n)"
 *  - 날당 이송: 같은 Haas 쪽 'Vf = fz x Z x n' 과 'Vf = fn x n' → fn = fz·Z.
 *      날 수 빈칸 = 2: Sandvik "Drilling formulas and definitions" 의 솔리드 초경 · 교환 날 드릴 그림 "Two edges from the center to the periphery"
 *      https://www.sandvik.coromant.com/en-us/knowledge/machining-formulas-definitions/drilling-formulas-definitions
 *  - 이론 거칠기 Rmax(최대 높이) = fn²/(8r)·1000 μm, 실제는 강 Rmax × (1.5~3), 주철 Rmax × (3~5): KORLOY Turning 2025-2026 F10(PDF 438쪽).
 *  - Ra: 위 식이 깔고 있는 같은 이론 단면(노즈 원호를 포물선 z = x²/(2r) 로 본 것 — 이 단면의 최대 높이가 꼭 f²/(8r))을
 *      Ra 의 정의(평균선에서 벗어난 거리의 산술 평균, ISO 4287 / KS B ISO 4287)로 적분한 값 = f²/(18√3·r)·1000 μm ≈ Rmax/3.9.
 *      흔히 쓰는 f²/(32r) 은 이것을 Rmax/4 로 줄인 것(2.6% 작다). 유도는 docs/calc-design.md "재료·절삭조건" 절.
 *  - G96 · G50 S: FANUC Series 0/00/0-Mate Lathe B-61394E/08 9.3 "CONSTANT SURFACE SPEED CONTROL (G96, G97)" —
 *      "G50 S_ ; The maximum spindle speed (rpm) follows S.", "a spindle speed higher than the value specified in G50S_;
 *      (maximum spindle speed) is clamped at the maximum spindle speed." · B-64604EN-1/01 G 코드 표 "G50 (계통 B · C 는 G92)
 *      Coordinate system setting or max spindle speed clamp".
 */

/** 기본계산기 입력 칸 */
enum class CutField { D, VC, N, F, FZ, Z, LENGTH, NOSE_R, SMAX }

/** 회전수 줄에서 사용자가 넣는 쪽 */
enum class SpeedFrom {
    /** 절삭속도 Vc 를 넣고 회전수 n 을 구한다 */
    VC,

    /** 회전수 n 을 넣고 절삭속도 Vc 를 구한다 */
    N,
}

/** 이송 줄에서 사용자가 넣는 쪽 */
enum class FeedFrom {
    /** 회전당 이송 f(mm/rev) 를 바로 */
    F,

    /** 날당 이송 fz × 날 수 Z (드릴) */
    FZ,
}

enum class CutWarning {
    /** 숫자가 아님, 0 이하, 크기 한도 밖(길이 [MAX_MM], 속도 [Cutting.MAX_SPEED]), 날 수 1~[Cutting.MAX_TEETH] 밖 — 칸은 [CutResult.bad] */
    BAD_INPUT,

    /** 이송 ≥ 2 × 노즈 R — 노즈 원호만으로 된 이론 단면이 아니다. 거칠기를 내지 않는다 */
    FEED_TOO_BIG_FOR_NOSE,

    /** G96: 넣은 지름에서 G50 S 최대 회전수에 막힌다(실제 주속이 Vc 보다 낮다) */
    RPM_CLAMPED,
}

/**
 * 기본계산기 입력. 모두 빈칸(null) 으로 시작 — 빈칸인 줄은 결과도 비어 있다(0 으로 계산하지 않는다).
 * 날 수만 빈칸 = [Cutting.TEETH_DEFAULT].
 */
data class CutInput(
    /** 지름(선반 = 가공 지름, 드릴 = 드릴 지름) */
    val d: Double? = null,
    val speedFrom: SpeedFrom = SpeedFrom.VC,
    /** 절삭속도 m/min ([SpeedFrom.VC] 일 때 씀) */
    val vc: Double? = null,
    /** 회전수 min⁻¹ ([SpeedFrom.N] 일 때 씀) */
    val n: Double? = null,
    val feedFrom: FeedFrom = FeedFrom.F,
    /** 회전당 이송 mm/rev ([FeedFrom.F]) */
    val f: Double? = null,
    /** 날당 이송 mm ([FeedFrom.FZ]) */
    val fz: Double? = null,
    /** 날 수(빈칸 2) */
    val z: Int? = null,
    /** 가공 길이 mm */
    val length: Double? = null,
    /** 노즈 R mm(이론 거칠기) */
    val noseR: Double? = null,
    /** G50 S 최대 회전수 min⁻¹(G96) */
    val smax: Double? = null,
)

/**
 * G96(주속 일정)에서 G50 S 로 막힐 때. [dLimit] 보다 작은 지름에서는 회전수가 [smax] 에 막혀 주속이 떨어진다.
 * 지름을 넣지 않았으면 [nCommand] · [n] · [vcActual] 은 null.
 */
data class G96Result(
    val vc: Double,
    val smax: Double,
    /** 1000·Vc/(π·Smax) — 이 지름부터 막힘 */
    val dLimit: Double,
    /** 그 지름에서 G96 이 내려는 회전수 */
    val nCommand: Double?,
    /** 실제 회전수 = min(nCommand, Smax) */
    val n: Double?,
    val clamped: Boolean,
    /** 실제 주속 = π·D·n/1000 */
    val vcActual: Double?,
)

data class CutResult(
    /** 절삭속도(넣었거나 n 에서 구한 것) */
    val vc: Double?,
    /** 회전수(넣었거나 Vc 에서 구한 것) */
    val n: Double?,
    /** 회전당 이송(넣었거나 fz × Z) */
    val f: Double?,
    /** fz 로 구했을 때 쓴 날 수 */
    val z: Int?,
    /** 분당 이송 vf = f·n */
    val vf: Double?,
    /** 가공 시간(분) = L / vf */
    val timeMin: Double?,
    /** 이론 최대 높이 거칠기(Rmax) μm */
    val rz: Double?,
    /** 이론 Ra μm */
    val ra: Double?,
    val g96: G96Result?,
    /** 잘못 넣은 칸 */
    val bad: Set<CutField>,
    val warnings: List<CutWarning>,
) {
    /** 가공 시간(초) */
    val timeSec: Double? get() = timeMin?.let { it * 60 }
}

object Cutting {
    /** 속도(Vc · n · Smax) 한도 — 키패드 오타가 그대로 계산되지 않게 */
    const val MAX_SPEED = 1_000_000.0
    const val TEETH_DEFAULT = 2
    const val MAX_TEETH = 100

    /** 실제 거칠기 ≈ 이론 Rmax × 이 범위(KORLOY Turning 2025-2026 F10 "Practical surface roughness") */
    val PRACTICAL_STEEL = 1.5..3.0
    val PRACTICAL_CAST_IRON = 3.0..5.0

    /** 절삭속도 m/min = π·D·n/1000 */
    fun vc(n: Double, d: Double): Double = PI * d * n / 1000

    /** 회전수 min⁻¹ = 1000·Vc/(π·D) */
    fun rpm(vc: Double, d: Double): Double = 1000 * vc / (PI * d)

    /** 분당 이송 mm/min = f·n */
    fun feedRate(f: Double, n: Double): Double = f * n

    /** 회전당 이송 mm/rev = fz·Z */
    fun feedPerRev(fz: Double, z: Int): Double = fz * z

    /** 가공 시간(분) = L/(f·n) */
    fun timeMin(length: Double, f: Double, n: Double): Double = length / (f * n)

    /** 이론 최대 높이 거칠기 μm = f²/(8r)·1000 */
    fun rz(f: Double, r: Double): Double = f * f / (8 * r) * 1000

    /** 이론 Ra μm = f²/(18√3·r)·1000 (같은 포물선 단면의 Ra) */
    fun ra(f: Double, r: Double): Double = f * f / (18 * sqrt(3.0) * r) * 1000

    fun calc(i: CutInput): CutResult {
        val bad = mutableSetOf<CutField>()
        fun len(field: CutField, v: Double?) = v?.also { if (!(it.isFinite() && it > 0 && it <= MAX_MM)) bad += field }
        fun speed(field: CutField, v: Double?) = v?.also { if (!(it.isFinite() && it > 0 && it <= MAX_SPEED)) bad += field }
        val d = len(CutField.D, i.d)
        val vcIn = if (i.speedFrom == SpeedFrom.VC) speed(CutField.VC, i.vc) else null
        val nIn = if (i.speedFrom == SpeedFrom.N) speed(CutField.N, i.n) else null
        val fIn = if (i.feedFrom == FeedFrom.F) len(CutField.F, i.f) else null
        val fz = if (i.feedFrom == FeedFrom.FZ) len(CutField.FZ, i.fz) else null
        val z = if (i.feedFrom == FeedFrom.FZ) (i.z ?: TEETH_DEFAULT).also { if (it !in 1..MAX_TEETH) bad += CutField.Z } else null
        val length = len(CutField.LENGTH, i.length)
        val r = len(CutField.NOSE_R, i.noseR)
        val smax = speed(CutField.SMAX, i.smax)
        fun ok(field: CutField, v: Double?) = v.takeIf { field !in bad }

        val dOk = ok(CutField.D, d)
        val vcOk = ok(CutField.VC, vcIn)
        val nOk = ok(CutField.N, nIn)
        val vc = vcOk ?: if (nOk != null && dOk != null) vc(nOk, dOk) else null
        val n = nOk ?: if (vcOk != null && dOk != null) rpm(vcOk, dOk) else null
        val f = ok(CutField.F, fIn) ?: ok(CutField.FZ, fz)?.let { v -> z?.takeIf { CutField.Z !in bad }?.let { feedPerRev(v, it) } }
        val vf = if (f != null && n != null) feedRate(f, n) else null
        val time = ok(CutField.LENGTH, length)?.let { l -> vf?.let { l / it } }

        val warnings = mutableListOf<CutWarning>()
        if (bad.isNotEmpty()) warnings += CutWarning.BAD_INPUT
        val rOk = ok(CutField.NOSE_R, r)
        var rz: Double? = null
        var ra: Double? = null
        if (f != null && rOk != null) {
            if (f >= 2 * rOk) warnings += CutWarning.FEED_TOO_BIG_FOR_NOSE else {
                rz = rz(f, rOk)
                ra = ra(f, rOk)
            }
        }
        val g96 = ok(CutField.SMAX, smax)?.let { s -> vc?.let { g96(it, s, dOk) } }
        if (g96?.clamped == true) warnings += CutWarning.RPM_CLAMPED
        return CutResult(vc, n, f, z?.takeIf { f != null }, vf, time, rz, ra, g96, bad, warnings)
    }

    /** G96 Vc · G50 S 최대 회전수 · (지름) → 막히는지 */
    fun g96(vc: Double, smax: Double, d: Double?): G96Result {
        val nCmd = d?.let { rpm(vc, it) }
        val n = nCmd?.let { min(it, smax) }
        return G96Result(
            vc = vc, smax = smax, dLimit = 1000 * vc / (PI * smax),
            nCommand = nCmd, n = n, clamped = nCmd != null && nCmd > smax, vcActual = if (d != null && n != null) vc(n, d) else null,
        )
    }
}
