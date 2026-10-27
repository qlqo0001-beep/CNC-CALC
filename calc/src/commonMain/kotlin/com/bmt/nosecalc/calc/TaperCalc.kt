package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 테이퍼 경고. 화면이 한국어 문구를 고른다.
 * 위 여덟 개는 계산을 멈춘다 — [TaperResult.solution] 이 null 이고 경고는 그 하나뿐(틀린 숫자를 내지 않으려고).
 * 아래 넷은 숫자와 함께 나온다.
 */
enum class TaperWarning {
    /** 값이 모자람. 한 경사는 X1·Z1·X2·Z2·각도 가운데 넷, 겹테이퍼는 세 점의 X·Z 모두 */
    MISSING_INPUT,

    /** 다섯 값을 다 넣었는데 각도가 점과 0.001° 넘게 다르다 */
    CONTRADICTION,

    /** 경사각이 0° 이하·90° 이상, 또는 경사의 두 점이 수평·수직으로 놓였다 */
    BAD_ANGLE,

    /** 점이 고른 모양과 안 맞는다: T2 가 T1 왼쪽이 아니거나, X 오르내림이 TYPE 과 반대 */
    SHAPE_MISMATCH,

    /**
     * 지름 ≤ 0, 노즈R·모서리 R·Z 여유 < 0, 노즈R 과 Z 여유가 둘 다 0(진입·후퇴 거리가 없다), 팁 번호가 0~9 밖, 숫자가 아님,
     * 좌표·치수(채운 값 포함)가 ±[MAX_MM] 밖
     */
    BAD_INPUT,

    /**
     * 시작점·끝점 좌표가 잘못됐다: T 점의 반대쪽이거나 같은 자리, X < 0, 또는 T 점에 너무 가까워
     * 노즈R 보정점을 지나 공구가 되돌아가야 한다
     */
    BAD_LEG,

    /** 모서리 R 이 안 들어간다: 접점이 경사·앞뒤 요소 끝(시작·끝점)을 넘거나, 두 R 의 접점이 경사 위에서 겹친다 */
    CORNER_R_TOO_BIG,

    /**
     * 노즈R 에 비해 경사(또는 앞뒤 요소)가 짧아 공구가 그 요소의 어느 점에도 닿지 못한다 — 도면 형상이 안 나온다.
     * 한쪽 끝이 볼록이어도 다른 끝 오목 모서리에 접한 원이 요소 밖에서 닿으면 이렇다
     */
    NOSE_UNREACHABLE,

    /**
     * 노즈R 이 경사 길이보다 크고, 그 경사 끝 하나 이상이 오목 모서리(모서리 R < r)다. 공구는 경사에 닿고 숫자도 맞다
     * (못 닿으면 이 경고 대신 NOSE_UNREACHABLE 로 멈춘다). 다만 오목 모서리 쪽에는 R 대신 r 이 남아 경사가 거의 안 남을 수 있으니
     * 도면을 확인할 것. 양 끝이 볼록이면(C0.3 면취에 0.8R) 다 깎여 경고하지 않는다
     */
    NOSE_GT_SLOPE,

    /**
     * 오목 모서리 R ≤ 노즈R. R = r 이면 공구가 꼭 맞아 정확하고, R < r 이면 그 자리에 도면보다 큰 R(= r)이 남는다
     * ([CornerArc.progR] = null). G41/G42 프로그램은 R < r 이면 기계가 간섭 알람을 낼 수 있다
     */
    CONCAVE_R_LE_NOSE,

    /**
     * 형상 일부가 바이트 방향과 반대다(노즈 접점이 팁 쪽 사분면 밖) — 끝날·여유각이 간섭할 수 있다.
     * 정방향의 역테이퍼(TYPE 5), 역방향의 TYPE 1~4 가 그렇다. 정/역방향을 확인.
     * 진행 방향도 본다: 뒤집어([TaperInput.flip]) 팁의 Z 와 반대로 45° 보다 눕힌 요소를 깎으면(뒤집은 외경 형상 3 이 외경을 Z+ 로) 그렇다
     */
    AGAINST_TOOL,

    /**
     * 진입점이나 후퇴점이 중심(X0)을 넘는다(가는 핀 끝·좁은 구멍의 가파른 경사를 그 연장으로 드나들 때) — Z 여유를 줄일 것.
     * 외경 끝 단면을 중심(X0)까지 깎으면 노즈 중심이 중심선에 닿게 인선이 일부러 중심 너머(X −2r)까지 가서도 난다(그 뒤 Z 로 빠진다. 화면 글이 둘을 나눈다)
     */
    ENTRY_PAST_AXIS,
}

/**
 * 경사 하나(Outer Dia / Inner Dia). T1 = 그림 오른쪽(Z 큰 쪽) 점, T2 = 왼쪽 점 — [flip] 이면 반대(T1 = 왼쪽 점). X 는 직경값.
 * X1·Z1·X2·Z2·각도 가운데 하나를 비워 두면 나머지로 푼다(보통 각도).
 */
data class TaperInput(
    /** false = Outer Dia(외경), true = Inner Dia(내경) */
    val inner: Boolean = false,
    /** 형상 TYPE 1~5 (docs/calc-design.md 의 표) */
    val type: Int = 1,
    /** 역방향(Rev.) = 역바이트로 Z+ 쪽으로 깎음 */
    val reverse: Boolean = false,
    val noseR: Double = 0.4,
    /** 가상 인선 번호. null = 표준(외경 3·역 4, 내경 2·역 1) */
    val tip: Int? = null,
    val x1: Double? = null,
    val z1: Double? = null,
    val x2: Double? = null,
    val z2: Double? = null,
    /** 경사각(°, Z 축에서 잼) */
    val angleDeg: Double? = null,
    /** 진입·후퇴 여유(≥ 0). 진입은 첫 깎는 점에서 Z 로, 후퇴는 끝에서 이만큼(노즈R 보다 짧으면 노즈R) */
    val zClear: Double = 1.0,
    /** T1·T2 의 모서리 R. 0 = 날카로운 꼭짓점 */
    val cornerR1: Double = 0.0,
    val cornerR2: Double = 0.0,
    /**
     * 시작점 = T1 앞 요소의 바깥 끝. 그 요소가 단면(TYPE 1·2, 뒤집으면 TYPE 2·4)이면 X(직경), 수평이면 Z. 넣으면 프로그램이 여기서 시작한다.
     * null = 그림은 T1 에서 5 mm(단면은 반경 5)이고, 정방향 프로그램은 앞 요소를 깎지 않고 T1(모서리 R 이면 그 시작)′ 에서 시작한다
     * — 경사(또는 수평인 앞 요소) 연장선이나 단면 바깥에서 Z 로 들어간다
     */
    val start: Double? = null,
    /**
     * 끝점 = T2 뒤 요소의 바깥 끝. 그 요소가 단면(TYPE 2·4, 뒤집으면 TYPE 1·2)이면 X(직경), 수평이면 Z. 넣으면 프로그램이 그 요소를 여기까지 깎는다.
     * null = 그림은 T2 에서 5 mm(중심 쪽 단면은 X0 에서 멈춤)이고, 프로그램은 그 요소를 깎지 않고 T2(모서리 R 이면 그 끝)′ 에서 끝난다.
     * 역방향(reverse)은 가공이 끝점 쪽에서 시작하므로 시작점·끝점의 구실이 바뀐다
     */
    val end: Double? = null,
    /**
     * 가공 방향 뒤집기(T1 ⇄ T2). 같은 형상(TYPE 그림 그대로)을 같은 바이트(팁 그대로)로 **반대 끝부터** 깎는다.
     * 칸의 뜻도 따라간다: T1(x1·z1·[cornerR1]) = 반대 끝 점(그림 왼쪽 점 — 외경 형상 1 이면 외경 쪽 위 점)으로 먼저 깎는 점,
     * [start] = 그 앞 요소(외경 형상 1 이면 외경 Z), [end] = T2 뒤 요소(외경 형상 1 이면 단면 X)의 바깥 끝.
     * 진행이 반대라 보정 쪽도 반대(외경 G41 · 내경 G42). 보정점은 공구 쪽·팁 벡터로만 정해지므로 안 뒤집은 것과 같은 점(순서만 반대)
     */
    val flip: Boolean = false,
    /**
     * 뒤집어 척 쪽 · 구멍 안에서 시작할 때 진입 G00 앞에 접근 줄(부품 앞 끝면 바깥에서 X 를 맞추고 Z 로)을 넣나([TaperSolution.approach]).
     * 계산기의 +Z 가 부품 앞(자유 끝)이라는 뜻이다 — 화면 역방향은 그림을 Z 로 뒤집어 풀어(+Z 가 실제 척 쪽) 끈다
     */
    val approach: Boolean = true,
)

/**
 * 겹테이퍼(Outer/Inner Double Taper). T1 오른쪽, T2 가운데, T3 왼쪽([flip] 이면 T1 ⇄ T3). 앞뒤 요소는 수평.
 * 세 점의 X(직경)·Z 를 모두 넣는다 — 각도는 결과로 나온다.
 */
data class DoubleTaperInput(
    val inner: Boolean = false,
    val reverse: Boolean = false,
    val noseR: Double = 0.4,
    val tip: Int? = null,
    val x1: Double? = null,
    val z1: Double? = null,
    val x2: Double? = null,
    val z2: Double? = null,
    val x3: Double? = null,
    val z3: Double? = null,
    val zClear: Double = 1.0,
    val cornerR1: Double = 0.0,
    val cornerR2: Double = 0.0,
    val cornerR3: Double = 0.0,
    /** 시작점 Z(T1 앞), 끝점 Z(T3 뒤). 뜻은 [TaperInput.start]·[TaperInput.end] 와 같다 */
    val start: Double? = null,
    val end: Double? = null,
    /** 가공 방향 뒤집기(T1 ⇄ T3, T2 그대로). 뜻은 [TaperInput.flip] 과 같다 — T1 = 그림 왼쪽 점부터 깎는다 */
    val flip: Boolean = false,
    /** 뒤집은 진입 앞 접근 줄. 뜻은 [TaperInput.approach] 와 같다 */
    val approach: Boolean = true,
)

/** 도면 점과 보정점(가상 인선 = 직접 보정 프로그램 좌표). x 는 반경 — 화면은 xDia */
data class CompPoint(val drawn: Pt, val prog: Pt) {
    /** ΔX(직경) = 보정 − 도면 */
    val dXDia: Double get() = prog.xDia - drawn.xDia
    val dZ: Double get() = prog.z - drawn.z
}

/** 꼭짓점에 끼운 모서리 R */
data class CornerArc(
    /** 도면 R */
    val r: Double,
    /** 보정 R(합성 R) = 볼록 R + r, 오목 R − r. 오목 R ≤ r 이면 원호가 없어져 null */
    val progR: Double?,
    /** 가공 순서로 갈 때의 방향. true = G02 */
    val cw: Boolean,
    /** 원호 중심(도면) */
    val center: Pt,
)

/** 꼭짓점 T1·T2(·T3) 하나 */
data class TaperPoint(
    /** 입력 꼭짓점(비웠던 값도 채움). 모서리 R 이 있으면 이론 꼭짓점 */
    val vertex: Pt,
    /** 모서리 R 이 없으면 꼭짓점 하나(drawn = vertex), 있으면 R 시작·끝 두 접점 — 가공 순서 */
    val nodes: List<CompPoint>,
    /** 모서리 R. 0 이거나 이웃과 접해서 이어지면 null */
    val corner: CornerArc?,
)

data class TaperSolution(
    /**
     * T1, T2(, T3) — 칸 순서. 정방향은 가공 순서다(그림 오른쪽부터, [TaperInput.flip] 이면 왼쪽부터).
     * 계산기 역방향(reverse)은 지금처럼 마지막 점부터 깎는다
     */
    val points: List<TaperPoint>,
    /** i번 = T(i+1) – T(i+2) 경사각(°), 칸 순서 */
    val anglesDeg: List<Double>,
    val tip: Int,
    /** G41/G42 프로그램의 보정 쪽 */
    val side: Side,
    /**
     * 직접 보정 프로그램의 G00 진입점·후퇴점(프로그램 좌표). 진입점은 첫 깎는 점에서 Z 로 max(Z 여유, 노즈R) 바깥 —
     * 뒤집어 넣은 수평 시작점부터 깎을 때만 그 시작점에서 공구 쪽으로 X 로(척·구멍 바닥 쪽으로 넘어 들어가지 않게)
     */
    val entry: Pt,
    val exit: Pt,
    /**
     * 직접 보정 프로그램에서 [entry] 앞 G00 점들(접근 줄). 뒤집어 척 쪽 · 구멍 안에서 시작하면 부품 앞 끝면 바깥(Z + c)에서 X 를 맞추고
     * Z 로 들어간다 — 그 X 로 오면 도면에 닿을 때(역테이퍼 꼴 · 겹테이퍼 산 너머)는 바깥 X 로 Z 를 맞춘 뒤 X 로. 그 밖은 빈 목록
     */
    val approach: List<Pt>,
    /**
     * 가공 시작 쪽 요소를 그 끝 좌표부터 깎나(좌표를 넣었을 때). false 면 그 요소는 깎지 않고 경사(또는 그 요소의 보정선)를
     * 늘인 진입점에서 첫 꺾임점′ 으로 바로 간다 — 그때 program.segs[0] 은 그 요소의 보정선(참고)이다
     */
    val fromStart: Boolean,
    /**
     * 가공 끝 쪽 요소를 그 끝 좌표까지 깎나(좌표를 넣었을 때). false 면 마지막 T 점(모서리 R 이면 그 끝)′ 에서 끝나고 그 요소는
     * 깎지 않는다 — 그때 program.segs 의 마지막은 그 요소의 보정선(참고)이다
     */
    val toEnd: Boolean,
    /**
     * 기본 프로그램 — 도면 좌표에 G41/G42 를 걸어 기계가 보정한다. 첫 줄은 공구 보정 화면에 넣을 값 주석 `(R0.4 T3)`.
     * (접근 줄 G00 —) G00 진입(G40 상태) → G41/G42 G01 시작점(진입 이동 ≥ 노즈R) → 형상(모서리 R 은 G02/G03 도면 R) → G40 G00 후퇴.
     * 외경 단면을 중심(X0)까지 깎아 끝나면 그 단면을 X −2r 까지 쓰고 Z 로 빼며 취소(제어기의 안쪽 모서리 교점이 노즈 중심을 중심선에)
     */
    val ncG41G42: List<String>,
    /**
     * 직접 보정 프로그램 — G40 상태로 앱이 계산한 보정 좌표(모서리 원호는 보정 R). 시작(넣었을 때)·끝은 인선이 그 좌표에 오게
     * (외경 단면을 중심 X0 까지 깎는 끝만은 노즈 중심이 중심선에 닿게 — [program]). 뒤집어 척 쪽에서 시작하면 진입 G00 앞에 접근 줄([approach])
     */
    val ncG40: List<String>,
    /** 도면선: 시작점 → T 점들(모서리 R 원호 포함) → 끝점, 가공 순서(정방향은 오른쪽부터, 역방향·뒤집기는 왼쪽부터, 둘 다면 오른쪽부터) */
    val drawing: Contour,
    /** 노즈 중심 경로 = [program] − 팁 벡터. segs[k] ↔ drawing.segs[k] */
    val center: Contour,
    /**
     * 직접 보정 프로그램 경로(가상 인선). segs[k] ↔ drawing.segs[k]. 끝은 [toEnd] 면 끝점′(외경 단면을 중심 X0 까지 깎으면 노즈 중심이
     * 중심선에 닿는 점 — 인선이 앞서 가는 팁이면 X0 너머), 처음은 [fromStart] 면 시작점′
     */
    val program: Contour,
)

data class TaperResult(val solution: TaperSolution?, val warnings: List<TaperWarning>)

object TaperCalc {
    /** 노즈R 고르기(직접 입력도 된다) */
    val NOSE_R_CHOICES = listOf(0.2, 0.4, 0.8, 1.2)

    /** 시작·끝점을 비웠을 때 그림에서 T 점에 붙이는 앞뒤 요소의 길이(mm, 단면은 반경). 그 요소는 깎지 않는다 */
    const val LEG_DEFAULT = 5.0

    /** 다섯 값을 다 넣었을 때 각도가 점과 이만큼(°)까지 달라도 받아 준다 — 각도를 소수 셋째 자리로 반올림해 넣어도 되게 */
    private const val ANGLE_TOL = 1e-3

    fun calc(input: TaperInput): TaperResult = guarded {
        // 뒤집으면 칸의 T1 이 그림 왼쪽 점이다 — 그림 순서(T1 = 오른쪽)로 바꿔 놓고 풀고, 가공만 반대 끝부터(solve)
        val i = with(input) {
            if (flip) copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start) else this
        }
        require(i.type in 1..5) { "TYPE 은 1~5: ${i.type}" }
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2), i.start, i.end)
        val given = listOf(i.x1, i.z1, i.x2, i.z2, i.angleDeg)
        if (given.count { it == null } > 1) stop(TaperWarning.MISSING_INPUT)
        if (given.any { it != null && !it.isFinite() }) stop(TaperWarning.BAD_INPUT)
        val ang = i.angleDeg
        if (ang != null && (ang <= 0 || ang >= 90)) stop(TaperWarning.BAD_ANGLE)
        // T1 에서 왼쪽 T2 로 갈 때 X 가 커지면 +1: 외경 TYPE 1~4, 내경 TYPE 5
        val up = if (i.inner == (i.type == 5)) 1.0 else -1.0
        val t = ang?.let { tan(it * PI / 180) }
        // 빈 값은 많아야 하나라 차례로 채우면 된다
        val x1 = i.x1 ?: (i.x2!! - up * 2 * leftward(i.z1!!, i.z2!!) * t!!)
        val x2 = i.x2 ?: (x1 + up * 2 * leftward(i.z1!!, i.z2!!) * t!!)
        val z1 = i.z1 ?: (i.z2!! + rise(x1, x2, up) / t!!)
        val z2 = i.z2 ?: (z1 - rise(x1, x2, up) / t!!)
        // 아주 작은 각도로 푼 점도 한도를 넘을 수 있다
        if (x1 <= 0 || x2 <= 0 || tooBig(x1, z1, x2, z2)) stop(TaperWarning.BAD_INPUT)
        val p1 = Pt.ofDia(z1, x1)
        val p2 = Pt.ofDia(z2, x2)
        val deg = slopeDeg(p1, p2, up)
        if (ang != null && abs(deg - ang) > ANGLE_TOL) stop(TaperWarning.CONTRADICTION)

        // 그림대로: TYPE 1·2 는 T1 에 단면, TYPE 2·4 는 T2 에 단면, 나머지는 수평. 단면은 경사의 오르내림을 잇는다
        val front = if (i.type <= 2) vertical(p1, -up, i.start) else horizontal(p1, 1.0, i.start)
        val back = if (i.type == 2 || i.type == 4) vertical(p2, up, i.end) else horizontal(p2, -1.0, i.end)
        solve(listOf(front, p1, p2, back), listOf(i.cornerR1, i.cornerR2), i.inner, i.reverse, i.flip, i.noseR, i.tip, i.zClear, i.start, i.end, i.approach)
    }

    fun calcDouble(input: DoubleTaperInput): TaperResult = guarded {
        // 뒤집으면 칸의 T1 이 그림 왼쪽 점(T2 는 그대로) — 그림 순서로 바꿔 놓고 푼다
        val i = with(input) {
            if (flip) copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start) else this
        }
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.start, i.end)
        val v = listOf(i.x1, i.z1, i.x2, i.z2, i.x3, i.z3)
        if (v.any { it == null }) stop(TaperWarning.MISSING_INPUT)
        if (v.any { !it!!.isFinite() } || listOf(i.x1, i.x2, i.x3).any { it!! <= 0 } || tooBig(*v.toTypedArray())) stop(TaperWarning.BAD_INPUT)
        val p = listOf(Pt.ofDia(i.z1!!, i.x1!!), Pt.ofDia(i.z2!!, i.x2!!), Pt.ofDia(i.z3!!, i.x3!!))
        slopeDeg(p[0], p[1])
        slopeDeg(p[1], p[2])
        val pts = listOf(horizontal(p[0], 1.0, i.start)) + p + horizontal(p[2], -1.0, i.end)
        solve(pts, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.inner, i.reverse, i.flip, i.noseR, i.tip, i.zClear, i.start, i.end, i.approach)
    }

    /**
     * drawn = [시작점, T1, T2, (T3,) 끝점] 그림 오른쪽부터, corners = T 점마다 모서리 R, start · end = 그림의 앞(오른쪽)·뒤 요소 끝.
     * 보정은 [NoseComp] 의 일반 해법 그대로. 앞뒤 요소는 그 바깥 끝 좌표를 넣었을 때만 깎는다.
     * 가공은 역방향(역바이트)과 뒤집기 가운데 하나만 켜졌으면 그림 왼쪽부터(Z+) — 가공 시작 쪽은 그때 end, 아니면 start.
     * 바이트(팁)는 역방향만 따른다(뒤집어도 같은 바이트). 보정 쪽·진입·후퇴·NC 는 모두 가공 순서(path)로 정하므로 뒤집기도 그대로 맞는다
     */
    private fun solve(
        drawn: List<Pt>, corners: List<Double>, inner: Boolean, reverse: Boolean, flip: Boolean, r: Double, tipIn: Int?, zClear: Double,
        start: Double?, end: Double?, approach: Boolean,
    ): TaperResult {
        val leftFirst = reverse != flip
        val cutFirst = (if (leftFirst) end else start) != null
        val cutLast = (if (leftFirst) start else end) != null
        val tip = tipIn ?: NoseComp.standardTip(inner, reverse)
        // G41/G42 = 진행 방향에서 본 공구 쪽. 같은 바이트로 반대 끝부터 깎으면 반대 쪽(외경 형상 1 뒤집기 = G41)
        val side = Side.of(inner, leftFirst)
        val path = if (leftFirst) drawn.reversed() else drawn
        val cr = if (leftFirst) corners.reversed() else corners
        val filleted = Corners.insert(path.zipWithNext { a, b -> Line(a, b) }, cr) ?: stop(TaperWarning.CORNER_R_TOO_BIG)
        val drawing = Contour(filleted.segs)
        val comp = try {
            NoseComp.tipPath(drawing, r, side, tip)
        } catch (e: IllegalArgumentException) {
            stop(TaperWarning.NOSE_UNREACHABLE)
        }
        // 요소 하나가 빠지면 도면 모양이 안 나온다. 오목 모서리 R < r(ArcTooSmall)은 r 이 남는 것으로 알린다
        if (comp.warnings.any { it is CompWarning.Unreachable }) stop(TaperWarning.NOSE_UNREACHABLE)
        val gone = comp.warnings.map { it.index }.toSet()
        val tv = NoseComp.tipVector(tip, r)
        // 팁의 방향만(노즈R 0 이어도 바이트가 어느 쪽을 보는지)
        val tv1 = NoseComp.tipVector(tip, 1.0)
        val segs = comp.path.segs.toMutableList()
        val first = drawing.segs.first()
        val last = drawing.segs.last()
        val uF = (first.end - first.start).unit()
        val uB = (last.end - last.start).unit()
        // 진입·후퇴 거리. G41/G42 시작 이동이 노즈R 보다 짧으면 안 된다
        val c = max(zClear, r)

        // G41/G42 프로그램의 G00 점은 G40 상태라 인선이 그 점에 있다(노즈 중심 = 점 − 팁 벡터). 공구 쪽 법선 n 인 선 위의 점이면
        // 노즈가 선에서 −(n·팁 벡터) 떨어진다. 표준 팁이 제 쪽 요소를 따라가면 r 이상(접하거나 떨어짐)이고, 바이트 반대쪽 요소
        // (AGAINST_TOOL)나 다른 팁이면 r 보다 가까워 선을 넘는다 — 그만큼(lift) 공구 쪽으로 띄워야 한다
        fun lift(n: Pt) = max(0.0, r + (n dot tv))
        // 띄운 G00 점에서 시작 요소 선에 직각으로 들어가 선 위의 lead 로 가면, 그 이동 끝에서 노즈는 교점 규칙대로 lead 보다 r 만큼
        // 선을 따라 앞에 선다. lead 를 그만큼 뒤에 두어 그때의 인선이 직접 보정의 진입점(entry)과 같게 한다
        fun leadFor(entry: Pt) = foot(entry - tv, first.start, uF) - uF * r
        // 꺾임 k(경로 점 path[k])가 날카로운 볼록 모서리인가 — 공구가 재료 모서리 바깥을 돈다
        fun convexAt(k: Int, corner: Int) =
            filleted.arc[corner] == null && (((path[k] - path[k - 1]) cross (path[k + 1] - path[k])) > 0) == (side == Side.G42)

        // 끝. 좌표를 넣었으면 인선(프로그램 점)이 끝점 좌표에 오게 — 끝 요소의 보정선 위에서 끝점에 내린 발. 공구가 끝좌표를 넘지 않는다
        // (Haas 워크북 56쪽 O00055 `N14 Z-2.375`, 53쪽 O00052 처럼). 후퇴: 끝 요소가 수평이면 공구 쪽으로 직각,
        // 단면이면 바깥으로 이어서(직각으로 빼면 G41/G42 제어기가 안쪽 모서리 교점을 잡아 단면 끝 r 을 덜 깎는다) — 다만 단면을
        // 재료 쪽(외경은 중심 쪽, 내경은 바깥)으로 깎아 끝나면(뒤집기·계산기 역방향의 앞 단면) 끝 X 너머도 같은 단면이나 보스라
        // 공구 쪽으로 직각(이어 가면 G00 이 끝 X 너머를 급송으로 깎는다). 그 단면을 중심(X0)까지 깎으면 노즈 중심이 중심선에 닿는
        // 데까지 간 뒤 공구 쪽으로 직각(안 그러면 중심에 살이 남는다 — 위 toAxis).
        // 비우면 그 요소는 깎지 않는다(그림·재료만): 마지막 꺾임점(모서리 R 이면 그 끝)′ 에서 끝 요소의 공구 쪽으로 c 빠진다.
        // G41/G42 는 시작과 거울로 — 날카로운 볼록이면 경사를 그대로 늘여, 아니면 끝 요소 선을 따라 보정을 푼다(안쪽 모서리 교점).
        // 바이트 반대쪽 모양이면 인선이 노즈 중심보다 꺾임 쪽에 있어, 노즈가 꺾임을 벗어나게 경사 쪽은 lift, 끝 요소 쪽은
        // 직접 보정의 마지막 점이 꺾임에서 끝 요소를 따라 간 만큼 더 간다
        val nB = toolNormal(uB, side)
        val exit: Pt
        val exitD: Pt // G41/G42 프로그램의 후퇴점(도면 좌표)
        var endD = last.end // G41/G42 프로그램의 마지막 형상 점(도면 좌표)
        if (cutLast) {
            var e0 = foot(last.end, segs.last().start, uB)
            // 인선이 끝좌표에 왔을 때 노즈가 끝 요소에 한 번도 닿지 않으면(요소가 노즈R 쯤보다 짧다) 그 요소는 깎이지 않는다
            if (((e0 - segs.last().start) dot uB) < -EPS || ((e0 - tv - last.start) dot uB) < -EPS) stop(TaperWarning.BAD_LEG)
            val face = !isHorizontal(uB)
            val inward = face && (uB.x < 0) != inner
            // 외경 단면을 중심(X0)까지: 노즈 중심이 중심선에 닿아야 중심에 살이 안 남는다. 직접 보정은 인선을 그만큼(T3 이면 X−2r) 더,
            // G41/G42 는 단면을 중심 너머 r(X−2r)까지 쓰고 Z 로 빼며 취소한다 — 안쪽 모서리라 제어기가 교점(노즈 중심 = 중심선)을 잡는다.
            // 너머는 이미 깎은 같은 면이지만 급송은 단면에서 떨어지는 Z 로만(검증 2026-10-27, 현장 안전)
            val toAxis = inward && !inner && last.end.x <= EPS
            if (toAxis) {
                e0 += uB * max(0.0, tv dot uB)
                endD = last.end + uB * r
            }
            segs[segs.lastIndex] = Line(segs.last().start, e0)
            val leave = if (!face || inward) nB else uB
            exit = e0 + leave * c
            exitD = endD + leave * c + nB * lift(nB)
        } else {
            val tl = segs.last().start
            exit = tl + nB * c
            exitD = if (convexAt(path.size - 2, cr.lastIndex)) {
                val ahead = path[path.size - 2] - path[path.size - 3]
                val nS = toolNormal(ahead.unit(), side)
                last.start + ahead * (c / abs(ahead.z)) + nS * lift(nS)
            } else {
                last.start + uB * (c + max(0.0, (tl - last.start) dot uB)) + nB * lift(nB)
            }
        }

        // 시작. 좌표를 넣었으면 끝과 같이 인선을 그 좌표에 두고, 시작 요소가 수평이면 그 연장선, 단면이면 공구 쪽에서 Z 로 들어간다.
        // 다만 뒤집어 수평 시작 요소를 팁의 Z 와 반대로 깎으면(외경·구멍을 척 쪽·구멍 바닥 쪽 끝부터 Z+ 로) 시작 좌표 너머는 날 앞쪽 —
        // 척·구멍 바닥이라 연장선으로 넘어 들어가지 않고 공구 쪽에서 시작 좌표에 직각으로(X 로 c) 들어간다(단면 시작과 같은 꼴).
        // 비우면 그 요소는 깎지 않는다(그림·재료만). 진입은 첫 깎는 점에서 Z 로 c 바깥: 첫 꺾임이 날카로운 볼록이면 경사선을 늘인 점
        // (경사각을 따라가 X 가 정확하다), 아니면 시작 요소가 수평이면 그 보정선을 늘인 점, 단면이면 공구 쪽 점
        val entry: Pt
        var entryD: Pt // G41/G42 프로그램의 진입점(도면 좌표)
        var leadD: Pt? = null // 띄운 진입점에서 직각으로 들어간 뒤 선을 따라 startD 로 갈 점
        val startD: Pt // G41/G42 보정 시작 이동의 끝
        val fromSeg: Int // 직접 보정에서 진입 뒤 따라갈 첫 요소
        var s0: Pt? = null
        val nF = toolNormal(uF, side)
        // 넣은 시작점: 수평 시작 요소의 연장선을 따라 들어가나(뒤집어 팁의 Z 와 반대로 깎는 것은 빼고 — 그때와 단면은 공구 쪽에서 직각으로)
        val along = isHorizontal(uF) && !(flip && uF.z * tv1.z <= 0)
        if (cutFirst) {
            val p = foot(first.start, segs.first().start, uF)
            if (((segs.first().end - p) dot uF) < -EPS || ((first.end - (p - tv)) dot uF) < -EPS) stop(TaperWarning.BAD_LEG)
            segs[0] = Line(p, segs[0].end)
            val inDir = if (along) uF * -1.0 else nF
            s0 = p
            entry = p + inDir * c
            if (along) {
                entryD = first.start + inDir * c
                if (lift(nF) > EPS) leadD = leadFor(entry).also { entryD = it + nF * (c + lift(nF)) }
            } else {
                entryD = first.start + nF * (c + lift(nF))
            }
            startD = first.start
            fromSeg = 0
        } else {
            if (convexAt(1, 0)) {
                // 경사 연장선은 재료가 아니라(볼록 모서리 바깥) 띄우지 않는다. 다만 바이트 반대쪽 팁(lift > 0)이면 그 G00 점의 노즈가
                // 깎지 않는 시작 요소 선을 넘을 수 있다 — 노즈가 그 선에서 r 떨어질 때까지 연장선을 따라 더 나간다(표준 팁은 그대로)
                val back = path[1] - path[2]
                entry = segs[0].end + back * (c / abs(back.z))
                entryD = path[1] + back * (c / abs(back.z))
                val k = back.unit() dot nF
                if (lift(nF) > EPS && k > EPS && lift(nF) / k > (entryD - path[1]).length) entryD = path[1] + back.unit() * (lift(nF) / k)
            } else if (isHorizontal(uF)) {
                entry = segs[0].end - uF * c
                // 도면 꼭짓점에서 c. 다만 첫 보정점이 꼭짓점보다 뒤에 있으면(뒤집어 인선이 노즈 뒤에 끌려가는 팁 — 노즈 중심이 인선보다
                // 앞) 직접 보정 진입점과 같은 자리까지 물러난다. 아니면 G40 상태의 노즈가 다음 요소(경사)에 박힌다
                entryD = first.end - uF * (c + max(0.0, (first.end - segs[0].end) dot uF))
                if (lift(nF) > EPS) leadD = leadFor(entry).also { entryD = it + nF * (c + lift(nF)) }
            } else {
                // 단면: 공구 쪽에서 Z 로. G41/G42 는 단면 선 위 lead 에 직각으로 들어간 뒤 선을 따라 간다 — 노즈는 교점 규칙대로
                // lead 보다 r 앞, 곧 직접 보정의 첫 깎는 점에 선다
                entry = segs[0].end + nF * c
                leadD = leadFor(segs[0].end).also { entryD = it + nF * (c + lift(nF)) }
            }
            startD = first.end
            fromSeg = 1
        }
        val program = Contour(segs)
        val toSeg = if (cutLast) segs.size else segs.size - 1 // 깎는 요소의 끝(끝 요소를 깎지 않으면 빼고)

        // 접근 줄: 뒤집어 그림 왼쪽(척 쪽 · 구멍 안)에서 시작하면 진입 G00 점이 부품 앞 끝면(넣은 앞 요소 끝이나 T 점 가운데 가장 오른쪽)보다
        // 척 쪽이다. G00 은 X · Z 가 따로 움직일 수 있어(dog-leg) 앞 위치에서 바로 오면 입구 단면 · 면취나 부품 앞을 지날 수 있으므로
        // 먼저 앞 끝면 바깥(Z + c)에서 X 를 맞추고 Z 로만 들어간다(검증 2026-10-27, 현장 안전). 그 X 로 Z 를 따라 오는 동안 노즈가 지나는
        // 도면(진입점에서 노즈 왼쪽 끝부터 오른쪽)보다 바깥이어야 한다 — 아니면(역테이퍼 꼴 · 겹테이퍼 산 너머) 그 바깥 X(+ c)로 Z 를 맞춘 뒤
        // X 로 진입점에 간다. 안 뒤집은 것 · 계산기 역방향은 지금 그대로. 그림의 꺾은선(모서리 R 전)이 모서리 R 윤곽을 바깥에서 감싼다
        val zFront = (if (start != null) drawn else drawn.drop(1)).maxOf { it.z }
        fun approachTo(e: Pt): List<Pt> {
            if (!approach || !flip || reverse || e.z >= zFront - EPS) return emptyList()
            val from = e.z - tv.z - r
            val xs = path.zipWithNext().flatMap { (a, b) ->
                listOfNotNull(
                    a.x.takeIf { a.z >= from }, b.x.takeIf { b.z >= from },
                    if ((a.z - from) * (b.z - from) < 0) a.x + (b.x - a.x) * (from - a.z) / (b.z - a.z) else null,
                )
            }
            // 노즈가 도면 바깥에 있으려면 인선 X 가 이 값 바깥(외경 위 · 내경 아래)
            val lim = if (inner) xs.min() + tv.x - r else xs.max() + tv.x + r
            if (if (inner) e.x <= lim + EPS else e.x >= lim - EPS) return listOf(Pt(zFront + c, e.x))
            val xa = if (inner) lim - c else lim + c
            return listOf(Pt(zFront + c, xa), Pt(e.z, xa))
        }
        val approachG40 = approachTo(entry)
        val approachG4 = approachTo(entryD)

        val warnings = mutableListOf<TaperWarning>()
        // 꺾임 k(경로 점 path[k])가 오목이고 모서리 R < r — 그 자리에 R 대신 r 이 남는다(볼록 끝은 다 깎인다)
        fun leavesR(k: Int) = ((path[k] - path[k - 1]) cross (path[k + 1] - path[k])).let { abs(it) > EPS && (it > 0) != (side == Side.G42) } &&
            cr[k - 1] < r
        val slopes = 1 until path.size - 2 // path[k] – path[k+1] 이 경사(앞뒤 요소 빼고)
        if (slopes.any { r > (path[it + 1] - path[it]).length && (leavesR(it) || leavesR(it + 1)) }) warnings += TaperWarning.NOSE_GT_SLOPE
        // 공구가 원 안쪽(오목): G41 이면 반시계, G42 면 시계 원호
        if (drawing.segs.any { it is Arc && (side == Side.G41) != it.cw && it.radius - r <= EPS }) warnings += TaperWarning.CONCAVE_R_LE_NOSE
        if (tip in 1..4 && againstTool(drawing, drawing.segs.subList(fromSeg, toSeg), side, tip)) warnings += TaperWarning.AGAINST_TOOL
        if ((listOf(entry, exit, entryD, exitD) + approachG40 + approachG4).any { it.x < -EPS }) warnings += TaperWarning.ENTRY_PAST_AXIS

        val ncG40 = NcLines().apply {
            for (p in approachG40) move("G00", p)
            move("G00", entry)
            // 시작점을 넣었으면 시작점′(시작 요소가 수평이면 진입점·시작점′ 이 그 보정선 위라 한 줄로 간다), 비웠으면 첫 꺾임점′
            if (s0 == null) move("G01", segs[0].end) else if (!along) move("G01", s0)
            for (s in segs.subList(fromSeg, toSeg)) if (s is Arc) arc(s) else move("G01", s.end)
            move("G00", exit)
        }.lines
        val ncG41G42 = listOf("(R${Fmt.nc(r).removeSuffix(".")} T$tip)") + NcLines().apply {
            for (p in approachG4) move("G00", p)
            move("G00", entryD)
            // 보정 시작·취소는 직선 이동(G00/G01)에서, 원호 블록은 안 된다 — Haas ST/TL 워크북 62쪽 APPROACH·DEPARTURE MOVES.
            // 시작 이동 끝에서 보정이 다 걸린다(같은 쪽). 시작 이동 길이 ≥ c ≥ r, 취소 이동 ≥ r(66쪽 "twice the tool radius" 는 직경값).
            // 시작 이동은 첫 형상 요소와 한 직선이거나, 그 선에 공구 쪽에서 직각으로 들어간다(안쪽 모서리 = 두 보정선의 교점)
            if (leadD != null) {
                move("$side G01", leadD)
                move("G01", startD)
            } else {
                move("$side G01", startD)
            }
            for (s in drawing.segs.subList(fromSeg, toSeg)) if (s is Arc) arc(s) else move("G01", if (s === last) endD else s.end)
            // 취소 뒤 G40 상태의 인선 = 후퇴점. 진입과 같이 노즈가 끝 요소 선을 넘지 않게 lift 만큼 공구 쪽으로
            move("G40 G00", exitD)
        }.lines

        // 꼭짓점 결과: 가공 순서의 k번 모서리. 칸 순서는 그림 순서를 뒤집기만큼, 가공 순서는 그림 순서를 leftFirst 만큼 뒤집은 것이라
        // 둘은 역방향(reverse)일 때만 다르다
        val byPath = cr.indices.map { k ->
            val a = filleted.arc[k]
            if (a == null) {
                val i = filleted.prev[k]
                TaperPoint(path[k + 1], listOf(CompPoint(drawing.segs[i].end, segs[i].end)), null)
            } else {
                val arc = drawing.segs[a] as Arc
                val pa = segs[a]
                TaperPoint(
                    path[k + 1],
                    listOf(CompPoint(arc.start, pa.start), CompPoint(arc.end, pa.end)),
                    CornerArc(arc.radius, if (a in gone) null else (pa as? Arc)?.radius, arc.cw, arc.center),
                )
            }
        }
        return TaperResult(
            TaperSolution(
                points = if (reverse) byPath.reversed() else byPath,
                anglesDeg = slopes.map { slopeDeg(drawn[it], drawn[it + 1]) }.let { if (flip) it.reversed() else it },
                tip = tip,
                side = side,
                entry = entry,
                exit = exit,
                approach = approachG40,
                fromStart = cutFirst,
                toEnd = cutLast,
                ncG41G42 = ncG41G42,
                ncG40 = ncG40,
                drawing = drawing,
                center = Contour(segs.map { it.shifted(tv * -1.0) }),
                program = program,
            ),
            warnings,
        )
    }

    /** 점 p 를 q 를 지나는 방향 u 의 직선에 내린 발 */
    private fun foot(p: Pt, q: Pt, u: Pt): Pt = q + u * ((p - q) dot u)

    private fun isHorizontal(u: Pt) = abs(u.z) > abs(u.x)

    /** 진행 방향 u 에서 공구 쪽 단위 법선(G41 왼쪽, G42 오른쪽) */
    private fun toolNormal(u: Pt, side: Side) = if (side == Side.G41) u.left() else u.left() * -1.0

    private fun Seg.shifted(v: Pt): Seg = when (this) {
        is Line -> Line(start + v, end + v)
        is Arc -> Arc(start + v, end + v, center + v, cw)
    }

    /**
     * 바이트와 반대인 곳이 있나. 둘 가운데 하나면 그렇다.
     * - 모양: 도면 요소 가운데 노즈 접점이 팁 쪽 사분면(팁 벡터 ±45°) 밖인 것. 가상 인선은 Z·X 축에 평행한 두 접선이
     *   인선이라는 가정이라, 접점이 그 사분면 밖이면 실제 바이트의 날·여유각이 맞는지 따로 봐야 한다. 진행 방향과 상관없다.
     * - 진행([cut] = 깎는 요소, 가공 순서): 팁의 Z 와 반대로 가며 45° 보다 눕힌 요소를 깎는다. 그런 요소의 접점은 사분면 안에서도
     *   끝날(부절삭날 — 팁 벡터의 X 쪽, 외경 T3 이면 아래를 보는 날) 쪽 반에 있고, 깎이는 자리(접점에서 진행 쪽)가 그 끝날 쪽으로
     *   돈다 — 역방향 깎기(back turning). 예: 뒤집은 외경 형상 3 이 외경을 Z+ 로. 단면(어느 쪽으로든)과 45° 이상 경사는
     *   주절삭날 쪽이라 괜찮다 — 정방향 프로파일이 어깨를 X+ 로 오르는 것, 뒤집은 외경 형상 1 이 45° 면취를 내려와 단면을 중심 쪽으로
     *   깎는 것. 정방향(뒤집지 않은 표준 팁)은 늘 팁의 Z 쪽으로 가므로 이 판정이 없던 때와 같다.
     * 모서리 R 원호는 접점 방향이 양옆 직선 사이에서만 돌므로 현의 방향으로 본다.
     */
    private fun againstTool(c: Contour, cut: List<Seg>, side: Side, tip: Int): Boolean {
        val toTip = NoseComp.tipVector(tip, 1.0).unit()
        val shape = c.segs.any {
            val toTool = toolNormal((it.end - it.start).unit(), side)
            // 접점 방향 = −toTool. 팁 벡터와 45° 넘게 벌어지면 밖
            (toTool dot toTip) > -sqrt(0.5) + 1e-9
        }
        val travel = cut.any {
            val u = (it.end - it.start).unit()
            // 45° 는 눕힌 것이 아니다(면취 X40 Z0 / X50 Z-5 를 tan 으로 푼 값의 반올림 오차를 넘게 1e-9)
            abs(u.z) - abs(u.x) > 1e-9 && u.z * toTip.z < 0
        }
        return shape || travel
    }

    /** a(오른쪽) → b(왼쪽) 경사의 각(°). [up] 이 있으면 X 가 그쪽으로 변해야 한다 */
    private fun slopeDeg(a: Pt, b: Pt, up: Double? = null): Double {
        val dz = a.z - b.z
        val dx = b.x - a.x
        if (abs(dz) <= EPS || abs(dx) <= EPS) stop(TaperWarning.BAD_ANGLE)
        if (dz < 0 || (up != null && dx * up < 0)) stop(TaperWarning.SHAPE_MISMATCH)
        return atan2(abs(dx), dz) * 180 / PI
    }

    /** T1 에서 T2 까지 왼쪽으로 간 Z 거리(> 0) */
    private fun leftward(z1: Double, z2: Double): Double {
        if (z1 - z2 <= 0) stop(TaperWarning.SHAPE_MISMATCH)
        return z1 - z2
    }

    /** T1 에서 T2 까지 TYPE 방향으로 오르내린 반경 거리(> 0) */
    private fun rise(x1: Double, x2: Double, up: Double): Double {
        val h = up * (x2 - x1) / 2
        if (h <= 0) stop(TaperWarning.SHAPE_MISMATCH)
        return h
    }

    /**
     * p 에서 위(s > 0)·아래로 가는 단면의 끝. xDia = 입력 X(직경), null 이면 반경 [LEG_DEFAULT] 만큼(아래로는 X0 에서 멈춤).
     * 입력이 그 방향에 없거나 X < 0 이면 [TaperWarning.BAD_LEG]
     */
    private fun vertical(p: Pt, s: Double, xDia: Double?): Pt {
        if (xDia == null) return Pt(p.z, if (s > 0) p.x + LEG_DEFAULT else max(p.x - LEG_DEFAULT, 0.0))
        if (xDia < 0 || (xDia / 2 - p.x) * s <= EPS) stop(TaperWarning.BAD_LEG)
        return Pt.ofDia(p.z, xDia)
    }

    /** p 에서 오른쪽(s > 0)·왼쪽으로 가는 수평 요소의 끝. z = 입력 Z, null 이면 [LEG_DEFAULT] 만큼 */
    private fun horizontal(p: Pt, s: Double, z: Double?): Pt {
        if (z == null) return Pt(p.z + s * LEG_DEFAULT, p.x)
        if ((z - p.z) * s <= EPS) stop(TaperWarning.BAD_LEG)
        return Pt(z, p.x)
    }

    private fun checkCommon(r: Double, tip: Int?, zClear: Double, corners: List<Double>, start: Double?, end: Double?) {
        // Z 여유 0 은 된다(진입·후퇴 거리 = 노즈R). 둘 다 0 이면 진입·후퇴 이동이 없어 G41/G42 를 걸고 풀 수 없다
        val ok = r >= 0 && r.isFinite() && zClear >= 0 && zClear.isFinite() && max(r, zClear) > 0 && (tip == null || tip in 0..9) &&
            corners.all { it >= 0 && it.isFinite() } && listOfNotNull(start, end).all { it.isFinite() } &&
            !tooBig(r, zClear, start, end, *corners.toTypedArray())
        if (!ok) stop(TaperWarning.BAD_INPUT)
    }
}

/** 계산을 멈추는 경고. [guarded] 가 받아 결과로 바꾼다 */
private class Stop(val warning: TaperWarning) : Exception()

private fun stop(w: TaperWarning): Nothing = throw Stop(w)

private inline fun guarded(block: () -> TaperResult): TaperResult =
    try {
        block()
    } catch (s: Stop) {
        TaperResult(null, listOf(s.warning))
    }
