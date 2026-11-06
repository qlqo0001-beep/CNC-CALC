package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * 다이스 헤드 나사 — 고정형 자동 열림 다이스 헤드(공작물이 돌고 터렛의 머리는 서 있다)로 숫나사를 한 번에 깎는 글(깎는 구간만).
 *
 *   (G97S…) · G0X0.Z(진입) · Z(시작) · G1Z(끝)F(비율 × P) · G4U(멈춤 초) · G0Z(물러남)
 *
 * 사용자 결정(2026-11-05): 이송 F = 피치 × 0.96(비율 처음 값), 끝 멈춤 G4U1.(1초), 물러남 Z150., 이 NC 꼴. 화면에서 바꾼다.
 * 카탈로그(docs/research/die-heads.json)는 이송을 피치와 같게(리드와 맞게) 쓴다 — Wagner 'Mit steigungsgenauem Vorschub'(이송 = 리드) ·
 * Landis 'must be correct for the lead of the thread'. 피치보다 작게 하라는 비율은 세 곳 공식 자료 어디에도 없다(TACO 는 이송 글이 없다).
 * 그래서 비율이 1 이 아니면 [DieSolution.note] 에 한 줄 안내를 둔다(경고 아님).
 *
 * 계산:
 *  - S = round(1000·Vc/(π·d))(d = 호칭 바깥지름) — 회전수를 직접 넣으면 그것(반올림). 쓴 Vc = π·d·S/1000(찍힌 S 의 절삭 속도).
 *  - F = 비율 × P. NC 는 0.001 자리(`F1.219`) — 시간 · 당김은 반올림 전 값으로 낸다(차 0.0005 mm/rev 이하).
 *  - 시간(초) = 60·(시작 Z − 끝 Z)/(F·S) + 멈춤. 진입 · 물러남 급송은 넣지 않는다.
 *  - 끝 당김 길이 = (1 − 비율)/비율 × (물림 Z − 끝 Z): 비율 < 1 이면 체이서가 자기 리드(P)로 나아가 머리 앞쪽이 몸통보다 회전마다 (1 − 비율)P 씩
 *    앞으로 끌려 나간 몫의 합 — 물린 뒤 기계가 (물림 Z − 끝 Z) 를 가는 동안 회전 수는 (물림 Z − 끝 Z)/(비율 × P) 이다(= (1 − 비율) × 체이서가 간 길이).
 *    머리의 플로트(축 방향 여유)가 받아야 한다. 물림 Z = 공작물 끝면 Z(넣었을 때 — 물리기 전엔 끌리지 않음),
 *    없으면 시작 Z(끝면보다 밖이라 실제보다 크게 본 값). Landis 는 이 너트 작용으로 머리를 당겨 연다(pull-off — 'This nut engagement is used to pull Landmatic type die heads open').
 *  - 멈춤 동안 체이서 이동 = S/60 × 멈춤 × P(G4 줄이 없으면 0): G1 이 끝나 기계가 서도 공작물은 돌아 체이서가 제 리드로 더 들어가고, 머리 앞쪽이
 *    플로트(열림 행정)까지 끌려 나가야 머리가 열린다. 당김 + 이 이동이 플로트에 못 미치면 닫힌 체이서를 급송(G0 물러남)으로 끌어낸다.
 *  - 완전 나사 끝 Z = 끝 Z + 목 산수 × P(체이서 목 길이만큼은 완전 나사가 아님 — 목 산수를 넣었을 때만). 당김이 없다고 본(가장 짧게 본) 값이다 —
 *    실제 체이서 끝은 G1 끝보다 당김 길이와 멈춤 동안 열릴 때까지 더 끌린 몫만큼 더 들어간다. 플로트를 넣으면 가장 깊게 본 체이서 끝 = 끝 Z − 플로트.
 * 숫나사 · M · UN 만 — 타입이 막는다(내외 칸이 없고 관용 테이퍼 [PipeSpec] 은 받지 않는다). 정가공(Z−)만.
 */

/** 다이스 헤드 경고. 위의 셋은 계산을 멈춘다([DieResult.solution] = null), 아래 다섯은 숫자와 함께 */
enum class DieWarning {
    /** 규격 · Z 시작 · Z 끝이 비었거나, Vc 와 회전수가 둘 다 비었다 */
    MISSING_INPUT,

    /**
     * 숫자가 아님, 크기 한도 밖, 비율 ≤ 0, 멈춤 · 플로트 · 목 산수 < 0, Vc ≤ 0 · > [Cutting.MAX_SPEED], 회전수 < 1, 찍힐 S 가 1 미만 · [G76Calc.MAX_RPM] 초과,
     * 찍힐 F(0.001)가 0
     */
    BAD_INPUT,

    /**
     * Z 순서: 시작 Z ≤ 끝 Z(정가공만), 진입 Z < 시작 Z, 물러남 Z < 시작 Z(급송이 공작물 쪽으로 간다), 끝면 Z(넣었을 때) > 시작 Z(시작점으로 급송하면
     * 공작물에 닿는다) · ≤ 끝 Z(깎을 나사가 없다)
     */
    Z_ORDER,

    /* ↑ 멈춤 · ↓ 숫자와 함께 */

    /**
     * 비율 > 1 — 기계 이송이 체이서 리드보다 빨라 머리를 밀어붙인다(당겨 여는 머리가 끝에서 안 열릴 수 있고, Wagner 고장표는 물릴 때 이송이
     * 너무 크면 두 줄 나사가 된다고 쓴다)
     */
    RATIO_OVER_ONE,

    /** 끝 당김 길이 > 플로트 — G1 끝 전에 머리가 열려 나사가 덜 깎인다(플로트를 넣었을 때만) */
    FLOAT_EXCEEDED,

    /** 완전 나사 끝 Z ≥ 물림 Z(끝면 Z, 없으면 시작 Z) — 나사 길이가 목 길이(목 산수 × P) 이하라 완전 나사가 생기지 않는다(목 산수를 넣었을 때만) */
    SHORT_THREAD,

    /**
     * 끝 멈춤 동안 머리가 열리지 않을 수 있다 — 닫힌 체이서를 급송(G0 물러남)으로 끌어낸다(나사 끝이 뜯기거나 머리가 상함).
     * 플로트를 안 넣었으면 G4 줄이 없을 때(멈춤 0), 넣었으면 당김 + 멈춤 동안 체이서 이동 < 플로트일 때. [FLOAT_EXCEEDED](G1 도중 열림)와는 함께 나지 않는다
     */
    DWELL_SHORT,

    /** 시작 Z − 끝면 Z < 피치 — 급송 끝이 공작물 끝면에 너무 가깝다(체이서 목이 급송으로 닿을 수 있음, 끝면을 넣었을 때만) */
    START_NEAR,
}

/** 입력. 길이 mm, X 는 늘 0(머리가 중심에). 공정 값(비율 · 멈춤 · 물러남 · 진입)은 사용자 결정 처음 값([DieHeadCalc] 상수) */
data class DieInput(
    val spec: ThreadSpec? = null,
    /** 절삭 속도 m/min — [rpm] 이 있으면 쓰지 않는다 */
    val vc: Double? = null,
    /** 회전수 직접(min⁻¹) — 있으면 [vc] 대신 */
    val rpm: Double? = null,
    /** 시작 Z(`Z…` — 공작물 끝면 밖) */
    val zStart: Double? = null,
    /** G1 끝 Z */
    val zEnd: Double? = null,
    /** 이송 비율 F / P */
    val ratio: Double = DieHeadCalc.RATIO_DEFAULT,
    /** 끝 멈춤(초) — 찍힐 값(0.001)이 0 이면 G4 줄을 쓰지 않는다 */
    val dwell: Double = DieHeadCalc.DWELL_DEFAULT,
    val retractZ: Double = DieHeadCalc.RETRACT_Z_DEFAULT,
    val approachZ: Double = DieHeadCalc.APPROACH_Z_DEFAULT,
    /** 머리 플로트(축 방향 여유, mm) — 카탈로그에 값이 없어 빈칸이 처음 */
    val floatTravel: Double? = null,
    /** 체이서 목 산수 — 카탈로그 값이 머리 · 목 종류마다라 빈칸이 처음 */
    val throatThreads: Double? = null,
    /** 공작물 끝면 Z(체이서가 물리기 시작하는 곳) — 끝 당김 길이 · 완전 나사 검사를 여기부터 센다. null = 시작 Z(크게 본 값) */
    val zFace: Double? = null,
)

data class DieSolution(
    /** 찍는 S */
    val rpm: Long,
    /** 쓴 절삭 속도 = π·d·S/1000 (m/min) */
    val vcUsed: Double,
    /** 이송 = 비율 × P (mm/rev, 반올림 전 — NC 는 0.001 자리) */
    val feed: Double,
    /** 분당 이송 = [feed] × S */
    val feedPerMin: Double,
    /** 깎는 시간 + 멈춤(초) */
    val timeSec: Double,
    /** 끝 당김 길이 = (1 − 비율)/비율 × (물림 Z − 끝 Z), 물림 Z = 끝면 Z(없으면 시작 Z) — 음수면 밀림(비율 > 1) */
    val floatPull: Double,
    /** 완전 나사 끝 Z = 끝 Z + 목 산수 × P (목 산수를 넣었을 때만) */
    val fullThreadEndZ: Double?,
    /** 끝 멈춤 동안 체이서가 더 들어가는 길이 = S/60 × 멈춤 × P (G4 줄이 없으면 0) */
    val dwellTravel: Double,
    /** 가장 깊게 본 체이서 끝 Z = 끝 Z − 플로트(머리가 플로트 끝까지 끌려 열린다고 봄 — 플로트를 넣었을 때만) */
    val chaserEndZ: Double?,
    /** `G97S…` · `G0X0.Z…` · `Z…` · `G1Z…F…` · (`G4U…`) · `G0Z…` */
    val program: List<String>,
    /** 비율이 1 이 아니면 [DieHeadCalc.CATALOG_FEED_NOTE](정보 — 경고 아님), 1 이면 null */
    val note: String?,
)

data class DieResult(val solution: DieSolution?, val warnings: List<DieWarning>)

object DieHeadCalc {
    /** 이송 비율 처음 값 — 사용자 결정(2026-11-05) */
    const val RATIO_DEFAULT = 0.96

    /** 끝 멈춤 처음 값(초) — 사용자 결정(2026-11-05) */
    const val DWELL_DEFAULT = 1.0

    /** 물러남 Z 처음 값 — 사용자 결정(2026-11-05). 앱의 다른 공구(200)와 다르다 */
    const val RETRACT_Z_DEFAULT = 150.0

    /** 진입 Z 처음 값 — 다른 공구 글과 같은 Z20.([CompFrame.APPROACH_Z]) */
    const val APPROACH_Z_DEFAULT = CompFrame.APPROACH_Z

    /** 비율이 1 이 아닐 때 함께 보이는 안내 */
    const val CATALOG_FEED_NOTE = "카탈로그(Wagner · Landis)는 이송을 피치와 같게(리드와 맞게) 쓴다"

    fun calc(i: DieInput): DieResult {
        val spec = i.spec
        if (spec == null || i.zStart == null || i.zEnd == null || (i.vc == null && i.rpm == null)) return stop(DieWarning.MISSING_INPUT)
        val reals = listOfNotNull(i.vc, i.rpm, i.zStart, i.zEnd, i.ratio, i.dwell, i.retractZ, i.approachZ, i.floatTravel, i.throatThreads, i.zFace)
        val bad = reals.any { !it.isFinite() } ||
            tooBig(i.zStart, i.zEnd, i.retractZ, i.approachZ, i.floatTravel, i.dwell, i.throatThreads?.times(spec.pitch), i.zFace) ||
            i.ratio <= 0 || i.dwell < 0 || (i.floatTravel ?: 0.0) < 0 || (i.throatThreads ?: 0.0) < 0 ||
            (i.vc != null && (i.vc <= 0 || i.vc > Cutting.MAX_SPEED)) || (i.rpm != null && i.rpm < 1)
        if (bad) return stop(DieWarning.BAD_INPUT)
        val p = spec.pitch
        val s = (i.rpm ?: Cutting.rpm(i.vc!!, spec.d)).roundToLong()
        val feed = i.ratio * p
        if (s < 1 || s > G76Calc.MAX_RPM || G76Calc.um(feed) < 1 || tooBig(feed)) return stop(DieWarning.BAD_INPUT)
        if (i.zStart <= i.zEnd || i.approachZ < i.zStart || i.retractZ < i.zStart) return stop(DieWarning.Z_ORDER)
        if (i.zFace != null && (i.zFace > i.zStart || i.zFace <= i.zEnd)) return stop(DieWarning.Z_ORDER)

        val len = i.zStart - i.zEnd
        // 물림 Z: 체이서가 공작물에 물리기 시작하는 곳(끝면, 없으면 시작 Z) — 당김 · 완전 나사는 여기부터
        val engage = i.zFace ?: i.zStart
        val feedPerMin = Cutting.feedRate(feed, s.toDouble())
        // 회전 수 = (물림 Z − 끝 Z)/(비율 × P), 회전마다 머리 앞쪽이 (1 − 비율)P 씩 끌려 나간다
        val pull = (1 - i.ratio) / i.ratio * (engage - i.zEnd)
        val fullEnd = i.throatThreads?.let { i.zEnd + it * p }
        val g4 = G76Calc.um(i.dwell) >= 1
        val dwellTravel = if (g4) s / 60.0 * i.dwell * p else 0.0
        val floatExceeded = i.floatTravel != null && pull > i.floatTravel + EPS
        val warnings = buildList {
            if (i.ratio > 1 + EPS) add(DieWarning.RATIO_OVER_ONE)
            if (floatExceeded) add(DieWarning.FLOAT_EXCEEDED)
            if (fullEnd != null && fullEnd >= engage - EPS) add(DieWarning.SHORT_THREAD)
            val noOpen = if (i.floatTravel == null) !g4 else pull + dwellTravel < i.floatTravel - EPS
            if (!floatExceeded && noOpen) add(DieWarning.DWELL_SHORT)
            if (i.zFace != null && i.zStart - i.zFace < p - EPS) add(DieWarning.START_NEAR)
        }
        val program = listOfNotNull(
            "G97S$s",
            "G0X0.Z${Fmt.word(i.approachZ)}",
            "Z${Fmt.word(i.zStart)}",
            "G1Z${Fmt.word(i.zEnd)}F${Fmt.word(feed)}",
            "G4U${Fmt.word(i.dwell)}".takeIf { g4 },
            "G0Z${Fmt.word(i.retractZ)}",
        )
        val sol = DieSolution(
            s, Cutting.vc(s.toDouble(), spec.d), feed, feedPerMin, 60 * len / feedPerMin + i.dwell, pull, fullEnd, dwellTravel,
            i.floatTravel?.let { i.zEnd - it }, program, CATALOG_FEED_NOTE.takeIf { abs(i.ratio - 1) > EPS },
        )
        return DieResult(sol, warnings)
    }

    private fun stop(w: DieWarning) = DieResult(null, listOf(w))
}
