package com.bmt.nosecalc.calc

import kotlin.math.abs

/*
 * 관용 테이퍼 나사 — 바이트로 깎는 G92 테이퍼 다회 글(사용자 결정 2026-11-05: 다이스 · G76 없이 G92 만, 숫나사 · 암나사).
 *
 * 규격(자료 원본 docs/research/pipe-threads.json — 출처 대조 끝남, 표 값은 원문 단위 그대로 옮긴 것):
 *  - PT: JIS B 0203:1999 付表1(1/16 ~ 6) · 附属書1付表1(3 1/2 — ISO 에 없는 크기). 숫나사 R · 암나사 Rc(옛 이름 PT), 55°,
 *    1/16 테이퍼(지름), 산 높이 h = 0.640327P, 둥글기 r = 0.137278P, 산의 이등분선이 축에 수직(付表1 그림 · 備考1).
 *    기준경(게이지 면)의 숫나사 바깥지름 d, 유효지름 d2 = d − h, 골지름 d1 = d − 2h. ISO 7-1:1994 Table 1(IS 554:1999 로 읽음)과
 *    지름 · h 는 같고, 길이 · 허용차의 자리 수(JIS 0.01 · ISO 0.1 mm)와 암나사 기준면 자리(JIS 관 끝 쪽 · ISO 끝면에서 0.5P 안)가 다르다
 *    — 앱은 JIS 값(요청이 PT 이름이라 계획에서 정한 기본).
 *  - NPT: NBS Handbook H28 (1957) Part II 7절 표 VII.2(= ANSI B2.1, 1/16 ~ 24 OD 가운데 1/16 ~ 4 — 선반 바이트로 깎는 범위로 정함), 60°,
 *    1/16 테이퍼, h = 0.8p(깎인 산의 기본 최대 깊이 — VII-2-2), 손 조임 면(게이지 노치)의 피치경 E1 · 길이 L1, 유효 나사 L2, 렌치 조임 L3,
 *    제품 허용차 = 게이지 ±1산(8항). 인치 표 값 × 25.4 를 mm 로 둔다.
 *
 * 기하(X 는 지름, 피치선은 산 높이 가운데 — 두 꼴 모두 대칭 형상이라 바깥지름 = E + h, 골지름 = E − h):
 *  - 기준면 zRef(빈칸 0) = 공작물 끝면. 숫나사는 관 끝(작은 쪽 끝면), 암나사는 입구 면.
 *  - 게이지 면 Zg: 숫나사 zRef − 게이지 길이(PT a · NPT L1 — 작은 끝에서 척 쪽), 암나사 zRef(JIS 의 암나사 기준경 자리 · NPT 플러그 게이지 노치가
 *    입구 면과 맞는 자리). 피치선 지름 E(z): 숫나사 Eg + (Zg − z)/16(척 쪽으로 굵어짐), 암나사 Eg − (Zg − z)/16(안쪽으로 가늘어짐).
 *  - 게이지 면 옮김 s(산, 빈칸 0, + = 조립 때 더 깊이 들어가는 쪽): 숫나사 Eg − s·P/16, 암나사 Eg + s·P/16(한 산 = 축 P = 지름 P/16).
 *    |s| 가 게이지 공차(PT 숫 1 · 1½산 · 암 1¼ · 1½산, NPT ±1산) 밖이면 멈춘다.
 *  - 끝 골 X = E(끝 Z) ∓ h(숫 − · 암 +), k = h — **풀 프로파일 인서트 가정**(인서트가 산봉우리까지 함께 만든다. 부분 프로파일 · NPT 평탄부
 *    공차는 다루지 않음). 절입표 · 회차 X 는 곧은 나사와 같다([G76Calc.schedule], 회차 X = 끝 X ± 2(k − 누적 깊이)).
 *  - G92 테이퍼 I(또는 R) = 사이클 시작 Z 쪽 반경 − 끝 반경 = ∓(시작 Z − 끝 Z)/32(지름 차 L/16 의 반 — 숫 − · 암 +).
 *    FANUC S 4-96 · 시뮬레이터 `GCycles.single` 의 A′ = (A.z, B.x + R)와 같은 부호. 시작 X: 숫나사 = 끝 Z 바깥지름(가장 굵은 곳) + 2 × 여유,
 *    암나사 = 끝 Z 안지름(가장 가는 곳) − 2 × 여유. 정가공(Z−)만 — 왼나사 테이퍼는 범위 밖.
 */

/** 관용 테이퍼 나사 종류 */
enum class PipeKind {
    /** 관용 테이퍼 나사(55°) — 숫나사 R · 암나사 Rc, JIS B 0203 (옛 이름 PT) */
    PT,

    /** 미국 관용 테이퍼 나사(60°) — ANSI B2.1 · ASME B1.20.1 */
    NPT,
}

/**
 * 관용 테이퍼 나사 하나. 길이 · 지름은 mm(NPT 는 표의 인치 × 25.4). [size] 는 표의 호칭("1/2" · "1 1/4"), [tpi] 는 25.4 mm 당 산수(NPT 11.5 도 있음).
 * 표 칸과 앱 칸의 대응은 [PipeTable] 주석.
 */
data class PipeSpec(
    val kind: PipeKind,
    val size: String,
    val tpi: Double,
    /** 게이지 면 피치경 — PT 기준경 d − h(付表1 의 d2 와 0.0005 안), NPT E1 */
    val gaugePitch: Double,
    /** 게이지 길이 = 숫나사 작은 끝(관 끝)에서 게이지 면까지 — PT 기준 길이 a, NPT 손 조임 길이 L1 */
    val gaugeLen: Double,
    /** 숫나사 게이지 면 위치 허용차(± 산) — PT 1 · 1½(ISO 10열 · JIS b = 산 × P), NPT 1 */
    val tolTurns: Double,
    /** 암나사 게이지 면 위치 허용차(± 산) — PT 1¼ · 1½(ISO 14열 · JIS c = 산 × P), NPT 1 */
    val tolTurnsInner: Double,
    /**
     * 숫나사 최소 나사 길이(작은 끝에서, 게이지 면이 제자리일 때): PT a + f(f = 기준경 위치에서 큰 쪽으로의 유효 나사부 최소 — JIS 5.2 ·
     * ISO 7.2.1 의 접합 여유, ISO 15열과 같은 뜻), NPT L2(유효 나사 길이)
     */
    val extLen: Double,
    /** 암나사 최소 나사 길이(입구 면에서): PT l(JIS — 기준경 위치에서 작은 쪽으로의 유효 나사부 최소), NPT L1 + L3(손 조임 + 렌치 조임) */
    val intLen: Double,
    /** 산봉우리 · 골 둥글기 r(PT 만 — JIS 付表1, 인서트 날 끝 R 을 고를 때 참고) */
    val rootR: Double? = null,
    /** 관 바깥지름(참고) — PT 배관용 탄소강 강관(JIS 付表1 참고 칸, 1/16 은 없음), NPT D */
    val pipeOd: Double? = null,
) {
    /** 피치 = 25.4 / 산수 */
    val pitch: Double get() = 25.4 / tpi

    /** 나사산 각 */
    val angleDeg: Int
        get() = when (kind) {
            PipeKind.PT -> 55
            PipeKind.NPT -> 60
        }

    /** 산 높이(반경) — PT 0.640327P, NPT 0.8P */
    val h: Double
        get() = when (kind) {
            PipeKind.PT -> 0.640327 * pitch
            PipeKind.NPT -> 0.8 * pitch
        }

    /** "PT 1/2" · "1/2-14 NPT" (숫 · 암을 가르는 이름은 [name]) */
    val label: String
        get() = when (kind) {
            PipeKind.PT -> "PT $size"
            PipeKind.NPT -> "$size-${Fmt.nc(tpi).trimEnd('.')} NPT"
        }

    /** 숫 · 암 이름: PT 는 "R 1/2" · "Rc 1/2"(JIS B 0203 의 기호), NPT 는 둘 다 "1/2-14 NPT" */
    fun name(inner: Boolean): String = when (kind) {
        PipeKind.PT -> "${if (inner) "Rc" else "R"} $size"
        PipeKind.NPT -> label
    }
}

/** 관용 테이퍼 나사 표 — 표 값은 docs/research/pipe-threads.json 에서 옮겼다(jvmTest PipeThreadJsonTest 가 모든 칸을 견준다) */
object PipeTable {
    /**
     * JIS B 0203:1999 付表1(3 1/2 은 附属書1付表1) — 크기, 산수, 기준경 바깥지름 d, 기준 길이 a, 접합 여유 f, 암나사 유효 나사부 l, 둥글기 r,
     * 강관 바깥지름(참고), 게이지 공차(숫 · 암, 산). 게이지 면 피치경 = d − 0.640327P(ISO 7-1 Table 1 주: 바깥지름을 먼저 환산하고 h 를 뺐다).
     * 공차 산은 ISO 7-1 의 규정값(10 · 14열) — JIS b · c 는 그 산 × P 를 0.01 mm 로 둥근 값이다(1/2: 1 × 1.8143 = 1.81 · 1¼ × 1.8143 = 2.27).
     */
    val PT: List<PipeSpec> = listOf(
        // 크기, 산수, d, a, f, l, r, 강관, 숫 · 암 공차(산)
        pt("1/16", 28, 7.723, 3.97, 2.5, 6.2, 0.12, null, 1.0, 1.25),
        pt("1/8", 28, 9.728, 3.97, 2.5, 6.2, 0.12, 10.5, 1.0, 1.25),
        pt("1/4", 19, 13.157, 6.01, 3.7, 9.4, 0.18, 13.8, 1.0, 1.25),
        pt("3/8", 19, 16.662, 6.35, 3.7, 9.7, 0.18, 17.3, 1.0, 1.25),
        pt("1/2", 14, 20.955, 8.16, 5.0, 12.7, 0.25, 21.7, 1.0, 1.25),
        pt("3/4", 14, 26.441, 9.53, 5.0, 14.1, 0.25, 27.2, 1.0, 1.25),
        pt("1", 11, 33.249, 10.39, 6.4, 16.2, 0.32, 34.0, 1.0, 1.25),
        pt("1 1/4", 11, 41.910, 12.70, 6.4, 18.5, 0.32, 42.7, 1.0, 1.25),
        pt("1 1/2", 11, 47.803, 12.70, 6.4, 18.5, 0.32, 48.6, 1.0, 1.25),
        pt("2", 11, 59.614, 15.88, 7.5, 22.8, 0.32, 60.5, 1.0, 1.25),
        pt("2 1/2", 11, 75.184, 17.46, 9.2, 26.7, 0.32, 76.3, 1.5, 1.5),
        pt("3", 11, 87.884, 20.64, 9.2, 29.8, 0.32, 89.1, 1.5, 1.5),
        pt("3 1/2", 11, 100.330, 22.23, 9.2, 31.4, 0.32, 101.6, 1.5, 1.5),
        pt("4", 11, 113.030, 25.40, 10.4, 35.8, 0.32, 114.3, 1.5, 1.5),
        pt("5", 11, 138.430, 28.58, 11.5, 40.1, 0.32, 139.8, 1.5, 1.5),
        pt("6", 11, 163.830, 28.58, 11.5, 40.1, 0.32, 165.2, 1.5, 1.5),
    )

    /**
     * NBS Handbook H28 (1957) Part II 표 VII.2(5 · 6쪽) 1/16 ~ 4 — 크기, 산수, 관 바깥지름 D, E1, L1, L2, L3(모두 인치 — × 25.4 로 mm).
     * E0(관 끝 피치경) = E1 − L1/16 이라 따로 두지 않는다. 게이지 공차 ±1산(8항).
     */
    val NPT: List<PipeSpec> = listOf(
        // 크기, 산수, D, E1, L1, L2, L3 (인치)
        npt("1/16", 27.0, 0.3125, 0.28118, 0.160, 0.2611, 0.1111),
        npt("1/8", 27.0, 0.405, 0.37360, 0.1615, 0.2639, 0.1111),
        npt("1/4", 18.0, 0.540, 0.49163, 0.2278, 0.4018, 0.1667),
        npt("3/8", 18.0, 0.675, 0.62701, 0.240, 0.4078, 0.1667),
        npt("1/2", 14.0, 0.840, 0.77843, 0.320, 0.5337, 0.2143),
        npt("3/4", 14.0, 1.050, 0.98887, 0.339, 0.5457, 0.2143),
        npt("1", 11.5, 1.315, 1.23863, 0.400, 0.6828, 0.2609),
        npt("1 1/4", 11.5, 1.660, 1.58338, 0.420, 0.7068, 0.2609),
        npt("1 1/2", 11.5, 1.900, 1.82234, 0.420, 0.7235, 0.2609),
        npt("2", 11.5, 2.375, 2.29627, 0.436, 0.7565, 0.2609),
        npt("2 1/2", 8.0, 2.875, 2.76216, 0.682, 1.1375, 0.2500),
        npt("3", 8.0, 3.500, 3.38850, 0.766, 1.2000, 0.2500),
        npt("3 1/2", 8.0, 4.000, 3.88881, 0.821, 1.2500, 0.2500),
        npt("4", 8.0, 4.500, 4.38712, 0.844, 1.3000, 0.2500),
    )

    fun of(kind: PipeKind): List<PipeSpec> = when (kind) {
        PipeKind.PT -> PT
        PipeKind.NPT -> NPT
    }

    private fun pt(
        size: String, tpi: Int, d: Double, a: Double, f: Double, l: Double, r: Double, od: Double?, tol: Double, tolInner: Double,
    ): PipeSpec = PipeSpec(
        PipeKind.PT, size, tpi.toDouble(), gaugePitch = d - 0.640327 * (25.4 / tpi), gaugeLen = a, tolTurns = tol, tolTurnsInner = tolInner,
        extLen = a + f, intLen = l, rootR = r, pipeOd = od,
    )

    private fun npt(size: String, tpi: Double, d: Double, e1: Double, l1: Double, l2: Double, l3: Double): PipeSpec = PipeSpec(
        PipeKind.NPT, size, tpi, gaugePitch = e1 * 25.4, gaugeLen = l1 * 25.4, tolTurns = 1.0, tolTurnsInner = 1.0,
        extLen = l2 * 25.4, intLen = (l1 + l3) * 25.4, pipeOd = d * 25.4,
    )
}

/**
 * 입력. 도면 값(규격 · Z 시작 · 끝 Z · 첫 절입)은 비면 [G76Warning.MISSING_INPUT], 가공 값은 비면 곧은 나사와 같은 기본값([G76Calc] 상수).
 * X 는 직경값, 길이 mm. 정가공(Z−)만이라 방향 칸이 없다
 */
data class PipeInput(
    /** true = 암나사(Rc · NPT 암) */
    val inner: Boolean = false,
    val spec: PipeSpec? = null,
    /** 기준면 Z(공작물 끝면 — 숫나사 관 끝 · 암나사 입구 면). null = 0 */
    val zRef: Double? = null,
    /** 사이클 시작점 Z(기준면 밖이어야 함) */
    val zStart: Double? = null,
    /** 나사 끝 Z */
    val zEnd: Double? = null,
    /** 회전수. 넣으면 첫 줄 `G97S…` 와 분당 이송 */
    val rpm: Double? = null,
    /** 게이지 면 옮김(산, + = 조립 때 더 깊이 — 숫나사 가늘게 · 암나사 굵게). null = 0 */
    val gaugeShift: Double? = null,
    /** Δd: 첫 절입(반경, mm) — 기본값 없음 */
    val firstCut: Double? = null,
    /** Δdmin: 최소 절입(반경, mm) */
    val minCut: Double? = null,
    /** d: 정삭 여유(반경, mm) */
    val finishAllow: Double? = null,
    /** m: 정삭 횟수 1~99 */
    val finishCount: Int? = null,
    /** 사이클 시작 X 여유(반경, mm) */
    val xClear: Double? = null,
    /** G92 테이퍼 낱말 — 'I'(기본, 사용자 결정 2026-11-05) · 'R'(FANUC 0i 기본 형식 — I 는 Series 10/11 형식일 때. Haas 는 I) */
    val taperWord: Char = 'I',
)

data class PipeSolution(
    /** 게이지 면 Z(숫나사 기준면 − 게이지 길이, 암나사 기준면) */
    val gaugeZ: Double,
    /** 게이지 면 피치경(게이지 면 옮김을 넣은 값) */
    val gaugePitch: Double,
    /** 끝 Z 의 피치경 */
    val pitchAtEnd: Double,
    /** 나사 내기 전에 깎아 둘 테이퍼(숫나사 바깥지름 = E + h · 암나사 안지름 = E − h) — 기준면 · 끝 Z */
    val crestAtRef: Double,
    val crestAtEnd: Double,
    /** 끝 골 X(숫나사 골 · 암나사 바깥 쪽, 반올림 전) = E(끝 Z) ∓ h */
    val x: Double,
    /** k 나사산 높이(반경) = h 를 0.001 로 */
    val height: Double,
    /** G92 테이퍼 값(반경, 0.001 로 반올림한 지령값) = ∓(시작 Z − 끝 Z)/32 — 숫나사 −, 암나사 + */
    val taper: Double,
    /** 사이클 시작점 X(직경) */
    val startX: Double,
    /** F 리드(= 피치) */
    val lead: Double,
    /** 실제 쓴 m · Δdmin · d · Δd (지령 단위로 반올림한 값) */
    val finishCount: Int,
    val minCut: Double,
    val finishAllow: Double,
    val firstCut: Double,
    /** 거친 절입 + 정삭 m 회 — 회차 X 는 끝 Z 의 X(시작 Z 쪽은 X + 2 × [taper]) */
    val passes: List<G76Pass>,
    /** G92 다회 글 — 곧은 나사와 같은 꼴에 첫 G92 줄만 `I…`(또는 `R…`) */
    val g92: List<String>,
    /** 다듬질 블록(마지막 X 두 번) */
    val g92Finish: List<String>,
    /** 분당 이송 = 회전수 × 리드(회전수를 넣었을 때) */
    val feedPerMin: Double?,
    /** 나사 길이 = 기준면 − 끝 Z */
    val threadLen: Double,
    /** 규격 최소 나사 길이([PipeSpec.extLen] · [PipeSpec.intLen]) + 게이지 면 옮김 × P — 더 짧으면 [G76Warning.SHORT_THREAD] */
    val needLen: Double,
)

data class PipeResult(val solution: PipeSolution?, val warnings: List<G76Warning>)

object PipeThreadCalc {
    /** 받는 테이퍼 낱말 */
    val TAPER_WORDS = listOf('I', 'R')

    fun calc(i: PipeInput): PipeResult {
        val spec = i.spec
        if (spec == null || i.zStart == null || i.zEnd == null || i.firstCut == null) return stop(G76Warning.MISSING_INPUT)
        val zRef = i.zRef ?: 0.0
        val shift = i.gaugeShift ?: 0.0
        val m = i.finishCount ?: G76Calc.FINISH_COUNT_DEFAULT
        val minCut = i.minCut ?: G76Calc.MIN_CUT_DEFAULT
        val allow = i.finishAllow ?: G76Calc.FINISH_ALLOW_DEFAULT
        val xClear = i.xClear ?: G76Calc.X_CLEAR_DEFAULT
        val tol = if (i.inner) spec.tolTurnsInner else spec.tolTurns
        val reals = listOf(zRef, i.zStart, i.zEnd, shift, minCut, allow, i.firstCut, xClear, spec.gaugePitch, spec.tpi) + listOfNotNull(i.rpm)
        val bad = reals.any { !it.isFinite() } || tooBig(zRef, i.zStart, i.zEnd, minCut, allow, i.firstCut, xClear) ||
            spec.gaugePitch <= 0 || spec.tpi <= 0 || abs(shift) > tol + EPS || i.taperWord !in TAPER_WORDS || m !in 1..99 ||
            minCut < 0 || allow < 0 || xClear < 0 || i.firstCut <= 0 || (i.rpm != null && (i.rpm < 1 || i.rpm > G76Calc.MAX_RPM))
        if (bad) return stop(G76Warning.BAD_INPUT)
        val firstUm = G76Calc.um(i.firstCut)
        if (firstUm < 1) return stop(G76Warning.BAD_INPUT)
        if (i.zStart <= i.zEnd) return stop(G76Warning.Z_ORDER)
        if (i.zStart < zRef - EPS) return stop(G76Warning.START_INSIDE)

        val p = spec.pitch
        val h = spec.h
        val gaugeZ = if (i.inner) zRef else zRef - spec.gaugeLen
        val eg = if (i.inner) spec.gaugePitch + shift * p / 16 else spec.gaugePitch - shift * p / 16
        // 1/16 테이퍼: 숫나사는 척 쪽(Z−)으로 굵어지고, 암나사는 입구에서 안쪽(Z−)으로 가늘어진다
        fun pitchAt(z: Double) = if (i.inner) eg - (gaugeZ - z) / 16 else eg + (gaugeZ - z) / 16
        fun crestAt(z: Double) = if (i.inner) pitchAt(z) - h else pitchAt(z) + h
        val pEnd = pitchAt(i.zEnd)
        val x = if (i.inner) pEnd + h else pEnd - h
        if (!i.inner && x <= 0) return stop(G76Warning.BAD_INPUT)
        val kUm = G76Calc.um(h)
        val k = kUm / 1000.0
        val d = G76Calc.r3(allow)
        if (d >= k) return stop(G76Warning.FINISH_GE_HEIGHT)
        val minUm = G76Calc.um(minCut)
        if (firstUm < minUm) return stop(G76Warning.FIRST_CUT_LT_MIN)
        val crestEnd = crestAt(i.zEnd)
        val startX = if (i.inner) crestEnd - 2 * xClear else crestEnd + 2 * xClear
        if (startX <= 0) return stop(G76Warning.CLEARANCE_TOO_BIG)

        val depths = G76Calc.schedule(k, d, firstUm / 1000.0, minUm / 1000.0, m) ?: return stop(G76Warning.TOO_MANY_PASSES)
        val xNc = G76Calc.r3(x)
        val passes = depths.mapIndexed { n, (depth, cut) ->
            val back = 2 * (k - depth)
            G76Pass(n + 1, depth, cut, if (i.inner) xNc - back else xNc + back, n >= depths.size - m)
        }
        // 시작 Z 쪽 반경 − 끝 반경: 지름 차 (시작 Z − 끝 Z)/16 의 반. 숫나사는 시작 쪽이 가늘어 −, 암나사는 굵어 +
        val taper = G76Calc.r3((if (i.inner) 1 else -1) * (i.zStart - i.zEnd) / 32)
        val threadLen = zRef - i.zEnd
        val needLen = (if (i.inner) spec.intLen else spec.extLen) + shift * p
        val warnings = buildList {
            if (firstUm / 1000.0 >= k - d - EPS) add(G76Warning.ONE_ROUGH_PASS)
            if (threadLen < needLen - EPS) add(G76Warning.SHORT_THREAD)
        }
        val xs = passes.map { it.xDia }
        val g92 = G76Calc.g92(i.rpm, startX, i.zStart, i.zEnd, xs, p, taper, i.taperWord)
        val g92Finish = G76Calc.g92(i.rpm, startX, i.zStart, i.zEnd, List(2) { xs.last() }, p, taper, i.taperWord)
        val sol = PipeSolution(
            gaugeZ, eg, pEnd, crestAt(zRef), crestEnd, x, k, taper, startX, p, m, minUm / 1000.0, d, firstUm / 1000.0,
            passes, g92, g92Finish, i.rpm?.let { it * p }, threadLen, needLen,
        )
        return PipeResult(sol, warnings)
    }

    private fun stop(w: G76Warning) = PipeResult(null, listOf(w))
}
