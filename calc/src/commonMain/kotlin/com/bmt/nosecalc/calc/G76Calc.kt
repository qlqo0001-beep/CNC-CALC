package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/*
 * 나사 G76 (FANUC 두 줄 복합 나사 절삭 사이클).
 *
 *   G76 P(m)(r)(a) Q(Δdmin) R(d);
 *   G76 X(U) Z(W) R(i) P(k) Q(Δd) F(L);
 *
 *   m 정삭 반복 횟수(01~99) · r 면취량(00~99, 0.1L 단위) · a 공구 날 끝 각도(80 · 60 · 55 · 30 · 29 · 0 가운데, 두 자리)
 *   Δdmin 최소 절입(반경) · d 정삭 여유(반경) · X Z 나사 끝점(X = 숫나사 골 · 암나사 바깥 쪽) · i 테이퍼량(0 = 곧은 나사, 이 앱은 안 씀)
 *   k 나사산 높이(반경) · Δd 첫 절입(반경) · L 리드.
 *   n 번째 절입 깊이 = Δd·√n (한 회 절입이 Δdmin 보다 작으면 Δdmin), 거친 절입은 k − d 까지, 마지막 정삭(d 를 깎는 회)을 m 번.
 *   단위 · 소수점(B-64604EN-1/01 4.2.7 의 표): Δdmin · k · Δd 는 소수점 불가(최소 지령 단위 — 미터 IS-B 면 μm 정수),
 *   d · i 는 소수점 가능. 모두 반경값. F 리드는 미터 입력 0.0001~500.0000 mm(같은 매뉴얼 표 3.2 (a)).
 *   출처: FANUC Series 0+-MODEL F Lathe 조작 설명서 B-64604EN-1/01 4.2.7 "Multiple Threading Cycle (G76)" ·
 *         FANUC Series 0/00/0-Mate Lathe 조작 설명서 B-61394E/08 13.2.7 (예: G76 P011060 Q100 R200; G76 X60.64 Z25.0 P3680 Q1800 F6.0;
 *         — 리드 6 의 숫나사 k = 3.68 = 0.6134 × 6).
 *   교차: Haas ST/TL 프로그래밍 워크북 109~113쪽(한 줄 G76 — K = 산 높이(반경), D = 첫 절입, "D" 표 = K/√횟수,
 *         나사 표의 외경 K = 0.6134P · 내경 K = 0.5413P, 예 O00113 3/4-16 외경 K0.0383).
 */

/** 가공 방향. 원본 08.jpg 의 '정가공(우) · 역가공(좌)' */
enum class ThreadDir {
    /** 정가공: Z 시작 > 목표, 척 쪽(Z−)으로 깎음 — 보통 바이트 · 주축 정회전이면 오른나사 */
    FORWARD,

    /** 역가공: Z 시작 < 목표, 척 반대쪽(Z+)으로 — 같은 바이트 · 회전이면 왼나사 */
    REVERSE,
}

/** G76 경고. 위의 일곱은 계산을 멈춘다([G76Result.solution] = null), 아래 둘은 숫자와 함께 */
enum class G76Warning {
    /** 규격 · Z 시작 · 가공 목표 Z · 첫 절입 가운데 빈 것이 있다 */
    MISSING_INPUT,

    /**
     * 숫자가 아님, 크기 한도 밖, 목표 0~100% 밖, 정삭 횟수 1~99 밖, 면취량 0~99(0.1L) 밖, 각도가 FANUC 6가지 밖,
     * 첫 절입 · 최소 절입 · 정삭 여유 · 인선 R · X 여유 < 0(첫 절입은 0 이하 — μm 로 반올림해 0 이면), 회전수 1 미만(S0 이 되지 않게),
     * 급수가 규격 · 내외경과 안 맞음
     */
    BAD_INPUT,

    /** 방향과 Z 순서가 반대(정가공인데 시작 ≤ 목표, 역가공인데 시작 ≥ 목표) */
    Z_ORDER,

    /**
     * 인선 R 이 너무 커서 날이 이웃 산봉우리까지 깎는다 — 목표 산봉우리 지름 높이에서 날 단면 폭이 피치 P 보다 넓다
     * (R > H/3 이라 피치선에 끝 원호가 걸릴 때만 생긴다. 그 아래 R 은 산봉우리 폭이 R 과 상관없이 정해진다)
     */
    TIP_TOO_BIG,

    /** 정삭 여유 d ≥ 나사산 높이 k (매뉴얼: "Specify a value smaller than the height of thread as the finishing allowance") */
    FINISH_GE_HEIGHT,

    /** 첫 절입 Δd < 최소 절입 Δdmin — 첫 회를 어떻게 다루는지 기계마다 달라 숫자를 내지 않는다 */
    FIRST_CUT_LT_MIN,

    /** 절입 횟수가 [G76Calc.MAX_PASSES] 를 넘는다 — 첫 절입 · 최소 절입 입력 실수로 본다 */
    TOO_MANY_PASSES,

    /** 내경 사이클 시작 X(안지름 최소 − 2 × X 여유)가 0 이하 */
    CLEARANCE_TOO_BIG,

    /* ↑ 멈춤 · ↓ 숫자와 함께 */

    /** 인선 R 이 커서 골이 규격 한계를 넘는다(숫나사 골지름 > d1 + es, 암나사 바깥지름 < D + EI) — 상대 나사 산봉우리에 닿을 수 있다 */
    ROOT_INTERFERES,

    /** 첫 절입 ≥ k − d — 거친 절입이 한 번(한 번에 k − d 까지) */
    ONE_ROUGH_PASS,
}

/**
 * 입력. 도면 값(규격 · Z 시작 · 목표 · 첫 절입)은 비면 [G76Warning.MISSING_INPUT], 가공 값은 비면 기본값(아래 `G76Calc` 상수).
 * X 는 직경값, 길이 mm.
 */
data class G76Input(
    /** true = 내경(암나사) */
    val inner: Boolean = false,
    val spec: ThreadSpec? = null,
    /** 공차 급수. null = [ThreadClass.default] (6g · 6H · 2A · 2B) */
    val cls: ThreadClass? = null,
    val dir: ThreadDir = ThreadDir.FORWARD,
    /** 사이클 시작점 Z(원본 'Z축 시작점') */
    val zStart: Double? = null,
    /** 나사 끝 Z(원본 '가공 목표점(Z)') */
    val zEnd: Double? = null,
    /** 회전수. 넣으면 프로그램 첫 줄 `G97 S…` 와 분당 이송. G76 자체엔 안 들어감 */
    val rpm: Double? = null,
    /** a: 공구 날 끝 각도(FANUC 80 · 60 · 55 · 30 · 29 · 0). 원본 '나사산 각도'. 나사 형상은 늘 60° 이고 이것은 절입 방향(a/2)만 바꾼다 */
    val angle: Int? = null,
    /** r: 면취량(끝 빼기), 0.1 리드 단위 0~99(10 = 1.0P) */
    val chamfer: Int? = null,
    /** 공차역 안 목표 위치 %: 0 = 최대 실체(숫나사 큰 쪽 · 암나사 작은 쪽), 100 = 최소 실체 */
    val target: Double? = null,
    /** 인선 R(날 끝 반지름). null = 규격 기본 깊이와 맞는 값(숫나사 H/6, 암나사 H/8) */
    val tipR: Double? = null,
    /** m: 정삭 반복 횟수 1~99 */
    val finishCount: Int? = null,
    /** Δdmin: 최소 절입(반경, mm) */
    val minCut: Double? = null,
    /** d: 정삭 여유(반경, mm) */
    val finishAllow: Double? = null,
    /** Δd: 첫 절입(반경, mm). 정해진 기본값이 없어 넣어야 한다 */
    val firstCut: Double? = null,
    /** 사이클 시작 X 의 여유(반경, mm): 숫나사 = 바깥지름 최대 + 2×, 암나사 = 안지름 최소 − 2× */
    val xClear: Double? = null,
)

/** 분석표 한 줄. [depth] = 그 회까지 누적 절입(반경, 산봉우리 = 0), [cut] = 그 회 절입, [xDia] = 그 회 날 끝 X */
data class G76Pass(val n: Int, val depth: Double, val cut: Double, val xDia: Double, val finish: Boolean)

data class G76Solution(
    val limits: ThreadLimits,
    /** 실제 쓴 목표 %(빈칸이면 기본) */
    val target: Double,
    /** 실제 쓴 인선 R */
    val tipR: Double,
    /** 목표 피치경 */
    val pitchDia: Double,
    /** 목표 산봉우리 지름 — 숫나사는 나사 내기 전 깎아 둘 바깥지름, 암나사는 구멍 지름. k 는 이 지름에서 잰다 */
    val crestDia: Double,
    /** G76 X(숫나사 골 · 암나사 바깥 쪽 목표, 반올림 전) */
    val x: Double,
    /** k 나사산 높이(반경, 0.001 로 반올림한 지령값) */
    val height: Double,
    /** F 리드 */
    val lead: Double,
    /** 사이클 시작점 X(직경) */
    val startX: Double,
    /** 실제 쓴 m · r · a · Δdmin · d · Δd (지령 단위로 반올림한 값) */
    val finishCount: Int,
    val chamfer: Int,
    val angle: Int,
    val minCut: Double,
    val finishAllow: Double,
    val firstCut: Double,
    /** 거친 절입 + 정삭 m 회 */
    val passes: List<G76Pass>,
    /** G76 두 줄 */
    val nc: List<String>,
    /** (G97S…) · G0 시작점 · G76 두 줄(회사 표기 — 낱말 붙여 씀 · 앞 0 뺌) */
    val program: List<String>,
    /**
     * G92 다회 글(회사 흐름): (G97S…) · `G0X(시작 X)Z20.` · `Z(시작 Z)` · `M23` · `G92X(1회)Z(끝 Z)F(리드)` · `X(2회)` … · `M24` · `G0Z200.` —
     * X 줄은 [passes] 의 X 그대로, M23 · M24 = 나사 끝 빼기 켬 · 끔
     */
    val g92: List<String>,
    /** G92 다듬질 블록: 같은 꼴(M23 · M24 포함)에 마지막 X 두 번(스프링 패스 — 외경 버를 없앤 뒤 다른 공구 블록 다음에 따로) */
    val g92Finish: List<String>,
    /** 분당 이송 = 회전수 × 리드(회전수를 넣었을 때) */
    val feedPerMin: Double?,
)

data class G76Result(val solution: G76Solution?, val warnings: List<G76Warning>)

object G76Calc {
    /** FANUC 가 받는 날 끝 각도(B-64604EN 4.2.7 "One of six kinds of angle, 80°, 60°, 55°, 30°, 29°, and 0°") */
    val ANGLES = listOf(80, 60, 55, 30, 29, 0)

    /** 기본값 — 원본 08.jpg 화면 값: 'G76 P02 1060 Q35 R0.02'(m 2 · 면취 1.0P · 60° · Δdmin 0.035 · d 0.02) · 'TARGET: 80%' */
    const val FINISH_COUNT_DEFAULT = 2
    const val CHAMFER_DEFAULT = 10
    const val ANGLE_DEFAULT = 60
    const val MIN_CUT_DEFAULT = 0.035
    const val FINISH_ALLOW_DEFAULT = 0.02
    const val TARGET_DEFAULT = 80.0

    /** 사이클 시작 X 여유(반경) — 원본에 없어 홈 계산의 X 여유 기본(1 mm)과 같게 */
    const val X_CLEAR_DEFAULT = 1.0

    const val MAX_PASSES = 1000
    private const val MAX_RPM = 100_000.0

    fun calc(i: G76Input): G76Result {
        val spec = i.spec
        if (spec == null || i.zStart == null || i.zEnd == null || i.firstCut == null) return stop(G76Warning.MISSING_INPUT)
        val cls = i.cls ?: ThreadClass.default(spec.kind, i.inner)
        val target = i.target ?: TARGET_DEFAULT
        val m = i.finishCount ?: FINISH_COUNT_DEFAULT
        val r = i.chamfer ?: CHAMFER_DEFAULT
        val a = i.angle ?: ANGLE_DEFAULT
        val minCut = i.minCut ?: MIN_CUT_DEFAULT
        val allow = i.finishAllow ?: FINISH_ALLOW_DEFAULT
        val xClear = i.xClear ?: X_CLEAR_DEFAULT
        val reals = listOfNotNull(i.zStart, i.zEnd, i.rpm, target, i.tipR, minCut, allow, i.firstCut, xClear, spec.d, spec.pitch)
        val bad = reals.any { !it.isFinite() } || tooBig(i.zStart, i.zEnd, i.tipR, minCut, allow, i.firstCut, xClear) ||
            cls.kind != spec.kind || cls.internal != i.inner || spec.d <= 0 || spec.pitch <= 0 ||
            target !in 0.0..100.0 || m !in 1..99 || r !in 0..99 || a !in ANGLES ||
            (i.tipR ?: 0.0) < 0 || minCut < 0 || allow < 0 || xClear < 0 || i.firstCut <= 0 ||
            (i.rpm != null && (i.rpm < 1 || i.rpm > MAX_RPM))
        if (bad) return stop(G76Warning.BAD_INPUT)
        val firstUm = um(i.firstCut)
        if (firstUm < 1) return stop(G76Warning.BAD_INPUT)
        if (if (i.dir == ThreadDir.FORWARD) i.zStart <= i.zEnd else i.zStart >= i.zEnd) return stop(G76Warning.Z_ORDER)

        val lim = ThreadTol.limits(spec, cls)
        val t = target / 100
        val h = lim.h
        val tipR = i.tipR ?: if (i.inner) h / 8 else h / 6
        // 0% = 최대 실체: 숫나사는 큰 쪽, 암나사는 작은 쪽
        val pd = if (i.inner) lim.pitchDia.min + t * lim.pitchDia.width else lim.pitchDia.max - t * lim.pitchDia.width
        val crest = if (i.inner) lim.crest.min + t * lim.crest.width else lim.crest.max - t * lim.crest.width
        // 피치경 = 홈 폭이 P/2 인 지름 → 날 단면의 반폭이 P/4 인 높이가 피치선이다([tipDepth])
        val below = tipDepth(spec.pitch, tipR)
        val x = if (i.inner) pd + 2 * below else pd - 2 * below
        val xNc = r3(x)
        val kExact = (if (i.inner) xNc - crest else crest - xNc) / 2
        // 산봉우리 높이(날 끝에서 k)의 날 단면 폭이 P 를 넘으면 이웃 홈을 깎는 날이 산봉우리를 깎아 목표 지름이 안 남는다
        if (um(kExact) < 1 || 2 * halfWidth(tipR, kExact) > spec.pitch + EPS) return stop(G76Warning.TIP_TOO_BIG)
        val kUm = um(kExact)
        val k = kUm / 1000.0
        val d = r3(allow)
        if (d >= k) return stop(G76Warning.FINISH_GE_HEIGHT)
        val minUm = um(minCut)
        if (firstUm < minUm) return stop(G76Warning.FIRST_CUT_LT_MIN)
        val startX = if (i.inner) lim.crest.min - 2 * xClear else lim.crest.max + 2 * xClear
        if (startX <= 0) return stop(G76Warning.CLEARANCE_TOO_BIG)

        val depths = schedule(k, d, firstUm / 1000.0, minUm / 1000.0, m) ?: return stop(G76Warning.TOO_MANY_PASSES)
        val passes = depths.mapIndexed { n, (depth, cut) ->
            val back = 2 * (k - depth)
            G76Pass(n + 1, depth, cut, if (i.inner) xNc - back else xNc + back, n >= depths.size - m)
        }
        val warnings = buildList {
            // 반올림 전 X 로 본다(기본 인선 R 의 암나사는 목표 0% 에서 꼭 한계 위라 0.001 반올림이 경고를 내지 않게)
            if (if (i.inner) x < lim.rootLimit - EPS else x > lim.rootLimit + EPS) add(G76Warning.ROOT_INTERFERES)
            if (firstUm / 1000.0 >= k - d - EPS) add(G76Warning.ONE_ROUGH_PASS)
        }
        val nc = blocks(m, r, a, minUm, d, xNc, i.zEnd, kUm, firstUm, spec.pitch)
        val program = listOfNotNull(
            i.rpm?.let { "G97S${it.roundToLong()}" },
            "G0X${Fmt.word(startX)}Z${Fmt.word(i.zStart)}",
        ) + nc
        val g92 = g92(i.rpm, startX, i.zStart, i.zEnd, passes.map { it.xDia }, spec.pitch)
        val g92Finish = g92(i.rpm, startX, i.zStart, i.zEnd, List(2) { passes.last().xDia }, spec.pitch)
        val sol = G76Solution(
            lim, target, tipR, pd, crest, x, k, spec.pitch, startX, m, r, a, minUm / 1000.0, d, firstUm / 1000.0,
            passes, nc, program, g92, g92Finish, i.rpm?.let { it * spec.pitch },
        )
        return G76Result(sol, warnings)
    }

    /**
     * 날 끝이 피치선(홈 폭 P/2)에서 얼마나 깊은지(반경). 60° V 날 끝 R 의 단면: 끝 원호와 옆날이 만나는 반폭은 R·cos30° 이다.
     *  - R ≤ H/3(= P/(2√3)): 피치선의 반폭 P/4 가 옆날 위 → V 꼭짓점이 피치선에서 H/2, 원호 끝은 꼭짓점에서 R 안쪽 → H/2 − R
     *  - R > H/3: P/4 가 끝 원호 위 → R − √(R² − P²/16)
     * 두 식은 R = H/3 에서 같다(H/6).
     */
    internal fun tipDepth(p: Double, r: Double): Double {
        val h = sqrt(3.0) / 2 * p
        return if (r <= h / 3) h / 2 - r else r - sqrt(r * r - p * p / 16)
    }

    /** 60° V 날(끝 R) 단면의 반폭 — 날 끝에서 높이 [y](반경). 끝 원호는 높이 R/2(= R(1 − sin30°))까지, 그 위는 옆날(tan30°) */
    internal fun halfWidth(r: Double, y: Double): Double =
        if (y <= r / 2) sqrt(max(0.0, r * r - (r - y) * (r - y))) else r * sqrt(3.0) / 2 + (y - r / 2) / sqrt(3.0)

    /**
     * 절입표(누적 깊이, 그 회 절입) — 거친 절입: n 회 깊이 Δd·√n, 그 회 절입이 Δdmin 보다 작으면 앞 깊이 + Δdmin,
     * k − d 에 닿거나 넘으면 k − d 로 끝. 그다음 정삭 m 회(첫 회가 d 를 깎고 나머지는 같은 깊이 — 0 절입).
     * 거친 + 정삭 횟수가 [MAX_PASSES] 를 넘으면 null.
     */
    internal fun schedule(k: Double, d: Double, first: Double, min: Double, m: Int): List<Pair<Double, Double>>? {
        val rough = k - d
        val out = mutableListOf<Pair<Double, Double>>()
        var prev = 0.0
        var n = 1
        while (true) {
            // 이번 회(늘 한 줄을 더한다)까지 넣은 합으로 본다
            if (out.size + 1 + m > MAX_PASSES) return null
            var dn = first * sqrt(n.toDouble())
            if (dn - prev < min) dn = prev + min
            if (dn >= rough - EPS) {
                out += rough to rough - prev
                break
            }
            out += dn to dn - prev
            prev = dn
            n++
        }
        repeat(m) { j -> out += k to if (j == 0) d else 0.0 }
        return out
    }

    /**
     * G76 두 줄 글(회사 표기 `G76P021060Q35R.02`). [minCutUm] · [heightUm] · [firstCutUm] 는 최소 지령 단위(μm) 정수, [finish] · [x] · [z] 는
     * mm(0.001 로 씀), [lead] 는 0.0001 까지. 곧은 나사라 둘째 줄 R(i) 는 쓰지 않는다(매뉴얼 예처럼)
     */
    internal fun blocks(
        m: Int, chamfer: Int, angle: Int, minCutUm: Long, finish: Double,
        x: Double, z: Double, heightUm: Long, firstCutUm: Long, lead: Double,
    ): List<String> = listOf(
        "G76P${two(m)}${two(chamfer)}${two(angle)}Q${minCutUm}R${Fmt.word(finish)}",
        "G76X${Fmt.word(x)}Z${Fmt.word(z)}P${heightUm}Q${firstCutUm}F${lead4(lead)}",
    )

    /**
     * G92 다회 글(회사 흐름, 계획 단계 6): (`G97S…`) · `G0X(시작 X)Z(진입 Z)` · `Z(시작 Z)` · `M23` · `G92X(x₁)Z(끝 Z)F(리드)` · `X(x₂)` … · `M24` · `G0Z(물러남)`.
     * G92 는 모달이라 둘째 회부터 X 만 쓰고, 같은 X 를 또 써도 그 줄에서 한 번 더 깎는다(정삭 · 스프링 패스). 사이클은 회마다 시작점으로 돌아온다.
     * 진입 · 물러남은 G41/G42 글과 같은 상수(`CompFrame`) — 공구를 바꾼 뒤 Z20. 에서 X 를 맞추고 Z 로 들어가며(꺾인 G00 이어도 공기), 끝나면 Z 만 물러난다.
     * M23 · M24 = 나사 끝 빼기 켬 · 끔(흔한 FANUC 계열 기계 M 정의 — 기계마다 다를 수 있음, 사용자 결정 11-04). G92 바로 앞 · 마지막 X 바로 뒤에 혼자 줄로 둔다
     * (사이클 블록에 M 을 같이 쓰면 오류 — S 4-93). M24 줄은 G92 모드 안이라 제어기는 그 줄에서 마지막 X 를 한 번 더 돈 뒤 끝 빼기를 끈다(FF 4.1.6).
     * 안 쓰는 것: 주축 M03/M04 · 공구 호출. 나사산 각도 a · 면취량 r 은 G76 만의 낱말이다(G92 는 X 로 곧게 절입)
     */
    internal fun g92(rpm: Double?, startX: Double, zStart: Double, zEnd: Double, xs: List<Double>, lead: Double): List<String> {
        val zApp = CompFrame.approachZ(max(zStart, zEnd))
        return listOfNotNull(rpm?.let { "G97S${it.roundToLong()}" }) +
            "G0X${Fmt.word(startX)}Z${Fmt.word(zApp)}" +
            "Z${Fmt.word(zStart)}" +
            "M23" +
            "G92X${Fmt.word(xs.first())}Z${Fmt.word(zEnd)}F${lead4(lead)}" +
            xs.drop(1).map { "X${Fmt.word(it)}" } +
            "M24" +
            "G0Z${Fmt.word(max(CompFrame.RETRACT_Z, zApp))}"
    }

    private fun two(v: Int) = v.toString().padStart(2, '0')

    /** mm → μm 정수(5 는 올림, Fmt 와 같은 방식) */
    private fun um(v: Double): Long = floor(abs(v) * 1000 + 0.5 + 1e-6).toLong() * (if (v < 0) -1 else 1)

    /** 찍히는 0.001 값 그대로 */
    private fun r3(v: Double): Double = Fmt.nc(v).toDouble()

    /** 리드 F: 0.0001 까지, 끝 0 지움, 소수점은 꼭, 앞 0 뺌(1.5 → "1.5", 6 → "6.", 25.4/13 → "1.9538", 25.4/40 → ".635") */
    private fun lead4(v: Double): String {
        val q = floor(v * 10_000 + 0.5 + 1e-6).toLong()
        return Fmt.shortLead("${q / 10_000}.${(q % 10_000).toString().padStart(4, '0').trimEnd('0')}")
    }

    private fun stop(w: G76Warning) = G76Result(null, listOf(w))
}
