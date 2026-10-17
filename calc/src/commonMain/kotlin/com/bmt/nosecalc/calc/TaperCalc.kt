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

    /** 지름 ≤ 0, 노즈R·모서리 R < 0, Z 여유 ≤ 0, 팁 번호가 0~9 밖, 숫자가 아님, 좌표·치수(채운 값 포함)가 ±[MAX_MM] 밖 */
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
     * 노즈R 이 경사 길이보다 크다. 공구는 경사에 닿고 숫자도 맞다(못 닿으면 이 경고 대신 NOSE_UNREACHABLE 로 멈춘다).
     * 다만 오목 모서리 쪽에는 R 대신 r 이 남아 경사가 거의 안 남을 수 있으니 도면을 확인할 것
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
     */
    AGAINST_TOOL,

    /** 진입점이 중심(X0)을 넘는다(가는 핀 끝의 가파른 면취를 경사 연장으로 들어갈 때) — Z 여유를 줄일 것 */
    ENTRY_PAST_AXIS,
}

/**
 * 경사 하나(Outer Dia / Inner Dia). T1 = 그림 오른쪽(Z 큰 쪽) 점, T2 = 왼쪽 점. X 는 직경값.
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
    /** 진입·후퇴 여유(> 0). 진입은 Z 로, 후퇴는 끝점에서 이만큼(노즈R 보다 짧으면 노즈R) */
    val zClear: Double = 1.0,
    /** T1·T2 의 모서리 R. 0 = 날카로운 꼭짓점 */
    val cornerR1: Double = 0.0,
    val cornerR2: Double = 0.0,
    /**
     * 시작점 = T1 앞 요소의 바깥 끝. 그 요소가 단면(TYPE 1·2)이면 X(직경), 수평이면 Z. 넣으면 프로그램이 여기서 시작한다.
     * null = 그림은 T1 에서 5 mm(단면은 반경 5)이고, 정방향 프로그램은 앞 요소를 깎지 않고 경사(또는 앞 요소) 연장선에서 들어간다
     */
    val start: Double? = null,
    /**
     * 끝점 = T2 뒤 요소의 바깥 끝. 그 요소가 단면(TYPE 2·4)이면 X(직경), 수평이면 Z. null = T2 에서 5 mm, 중심 쪽 단면은 X0 에서 멈춘다.
     * 역방향(reverse)은 가공이 끝점 쪽에서 시작하므로 시작점·끝점의 구실이 바뀐다
     */
    val end: Double? = null,
)

/**
 * 겹테이퍼(Outer/Inner Double Taper). T1 오른쪽, T2 가운데, T3 왼쪽. 앞뒤 요소는 수평.
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
    /** T1, T2(, T3) — 그림 오른쪽부터 */
    val points: List<TaperPoint>,
    /** i번 = T(i+1) – T(i+2) 경사각(°) */
    val anglesDeg: List<Double>,
    val tip: Int,
    /** G41/G42 프로그램의 보정 쪽 */
    val side: Side,
    /** 직접 보정 프로그램의 G00 진입점·후퇴점(프로그램 좌표) */
    val entry: Pt,
    val exit: Pt,
    /**
     * 가공 시작 쪽 요소를 그 끝 좌표부터 깎나(좌표를 넣었을 때). false 면 그 요소는 깎지 않고 경사(또는 그 요소의 보정선)를
     * 늘인 진입점에서 첫 꺾임점′ 으로 바로 간다 — 그때 program.segs[0] 은 그 요소의 보정선(참고)이다
     */
    val fromStart: Boolean,
    /**
     * 기본 프로그램 — 도면 좌표에 G41/G42 를 걸어 기계가 보정한다. 첫 줄은 공구 보정 화면에 넣을 값 주석 `(R0.4 T3)`.
     * G00 진입(G40 상태) → G41/G42 G01 시작점(진입 이동 ≥ 노즈R) → 형상(모서리 R 은 G02/G03 도면 R) → G40 G00 후퇴
     */
    val ncG41G42: List<String>,
    /** 직접 보정 프로그램 — G40 상태로 앱이 계산한 보정 좌표(모서리 원호는 보정 R). 시작(넣었을 때)·끝은 인선이 그 좌표에 오게 */
    val ncG40: List<String>,
    /** 도면선: 시작점 → T 점들(모서리 R 원호 포함) → 끝점, 가공 순서(정방향은 오른쪽부터, 역방향은 왼쪽부터) */
    val drawing: Contour,
    /** 노즈 중심 경로 = [program] − 팁 벡터. segs[k] ↔ drawing.segs[k] */
    val center: Contour,
    /** 직접 보정 프로그램 경로(가상 인선). segs[k] ↔ drawing.segs[k]. 끝은 끝점′, 처음은 [fromStart] 면 시작점′ */
    val program: Contour,
)

data class TaperResult(val solution: TaperSolution?, val warnings: List<TaperWarning>)

object TaperCalc {
    /** 노즈R 고르기(직접 입력도 된다) */
    val NOSE_R_CHOICES = listOf(0.2, 0.4, 0.8, 1.2)

    /** 시작·끝점을 비웠을 때 T 점에서 떨어진 거리(mm, 단면은 반경) */
    const val LEG_DEFAULT = 5.0

    /** 다섯 값을 다 넣었을 때 각도가 점과 이만큼(°)까지 달라도 받아 준다 — 각도를 소수 셋째 자리로 반올림해 넣어도 되게 */
    private const val ANGLE_TOL = 1e-3

    fun calc(i: TaperInput): TaperResult = guarded {
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
        val cut = (if (i.reverse) i.end else i.start) != null
        solve(listOf(front, p1, p2, back), listOf(i.cornerR1, i.cornerR2), i.inner, i.reverse, i.noseR, i.tip, i.zClear, cut)
    }

    fun calcDouble(i: DoubleTaperInput): TaperResult = guarded {
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.start, i.end)
        val v = listOf(i.x1, i.z1, i.x2, i.z2, i.x3, i.z3)
        if (v.any { it == null }) stop(TaperWarning.MISSING_INPUT)
        if (v.any { !it!!.isFinite() } || listOf(i.x1, i.x2, i.x3).any { it!! <= 0 } || tooBig(*v.toTypedArray())) stop(TaperWarning.BAD_INPUT)
        val p = listOf(Pt.ofDia(i.z1!!, i.x1!!), Pt.ofDia(i.z2!!, i.x2!!), Pt.ofDia(i.z3!!, i.x3!!))
        slopeDeg(p[0], p[1])
        slopeDeg(p[1], p[2])
        val pts = listOf(horizontal(p[0], 1.0, i.start)) + p + horizontal(p[2], -1.0, i.end)
        val cut = (if (i.reverse) i.end else i.start) != null
        solve(pts, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.inner, i.reverse, i.noseR, i.tip, i.zClear, cut)
    }

    /**
     * drawn = [시작점, T1, T2, (T3,) 끝점] 그림 오른쪽부터, corners = T 점마다 모서리 R. 보정은 [NoseComp] 의 일반 해법 그대로.
     * cutFirst = 가공 시작 쪽 요소의 바깥 끝 좌표를 넣었나(정방향은 시작점, 역방향은 끝점).
     */
    private fun solve(
        drawn: List<Pt>, corners: List<Double>, inner: Boolean, reverse: Boolean, r: Double, tipIn: Int?, zClear: Double,
        cutFirst: Boolean,
    ): TaperResult {
        val tip = tipIn ?: NoseComp.standardTip(inner, reverse)
        val side = Side.of(inner, reverse)
        val path = if (reverse) drawn.reversed() else drawn
        val cr = if (reverse) corners.reversed() else corners
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
        val segs = comp.path.segs.toMutableList()
        val first = drawing.segs.first()
        val last = drawing.segs.last()
        val uF = (first.end - first.start).unit()
        val uB = (last.end - last.start).unit()
        // 진입·후퇴 거리. G41/G42 시작 이동이 노즈R 보다 짧으면 안 된다
        val c = max(zClear, r)

        // 끝: 인선(프로그램 점)이 끝점 좌표에 오게 — 끝 요소의 보정선 위에서 끝점에 내린 발. 공구가 끝좌표를 넘지 않는다
        // (Haas 워크북 56쪽 O00055 `N14 Z-2.375`, 53쪽 O00052 처럼). 후퇴: 끝 요소가 수평이면 공구 쪽으로 직각,
        // 단면이면 바깥으로 이어서(직각으로 빼면 G41/G42 제어기가 안쪽 모서리 교점을 잡아 단면 끝 r 을 덜 깎는다)
        val e0 = foot(last.end, segs.last().start, uB)
        // 인선이 끝좌표에 왔을 때 노즈가 끝 요소에 한 번도 닿지 않으면(요소가 노즈R 쯤보다 짧다) 그 요소는 깎이지 않는다
        if (((e0 - segs.last().start) dot uB) < -EPS || ((e0 - tv - last.start) dot uB) < -EPS) stop(TaperWarning.BAD_LEG)
        segs[segs.lastIndex] = Line(segs.last().start, e0)
        val leave = if (isHorizontal(uB)) toolNormal(uB, side) else uB
        val exit = e0 + leave * c

        // G41/G42 프로그램의 G00 점은 G40 상태라 인선이 그 점에 있다(노즈 중심 = 점 − 팁 벡터). 공구 쪽 법선 n 인 선 위의 점이면
        // 노즈가 선에서 −(n·팁 벡터) 떨어진다. 표준 팁이 제 쪽 요소를 따라가면 r 이상(접하거나 떨어짐)이고, 바이트 반대쪽 요소
        // (AGAINST_TOOL)나 다른 팁이면 r 보다 가까워 선을 넘는다 — 그만큼(lift) 공구 쪽으로 띄워야 한다
        fun lift(n: Pt) = max(0.0, r + (n dot tv))
        // 띄운 G00 점에서 시작 요소 선에 직각으로 들어가 선 위의 lead 로 가면, 그 이동 끝에서 노즈는 교점 규칙대로 lead 보다 r 만큼
        // 선을 따라 앞에 선다. lead 를 그만큼 뒤에 두어 그때의 인선이 직접 보정의 진입점(entry)과 같게 한다
        fun leadFor(entry: Pt) = foot(entry - tv, first.start, uF) - uF * r
        // 시작. 좌표를 넣었으면 끝과 같이 인선을 그 좌표에 두고, 시작 요소가 수평이면 그 연장선, 단면이면 공구 쪽에서 Z 로 들어간다.
        // 비우면 그 요소는 깎지 않는다(그림·재료만): 첫 꺾임이 날카로운 볼록이면 경사선을 Z 로 c 바깥까지 늘인 점(경사각을 따라가
        // X 가 정확하다), 오목이거나 모서리 R 이면 시작 요소의 보정선을 c 만큼 늘인 점에서 들어간다
        val entry: Pt
        var entryD: Pt // G41/G42 프로그램의 진입점(도면 좌표)
        var leadD: Pt? = null // 띄운 진입점에서 직각으로 들어간 뒤 선을 따라 startD 로 갈 점
        val startD: Pt // G41/G42 보정 시작 이동의 끝
        val fromSeg: Int // 직접 보정에서 진입 뒤 따라갈 첫 요소
        var s0: Pt? = null
        if (cutFirst) {
            val p = foot(first.start, segs.first().start, uF)
            if (((segs.first().end - p) dot uF) < -EPS || ((first.end - (p - tv)) dot uF) < -EPS) stop(TaperWarning.BAD_LEG)
            segs[0] = Line(p, segs[0].end)
            val n = toolNormal(uF, side)
            val approach = if (isHorizontal(uF)) uF * -1.0 else n
            s0 = p
            entry = p + approach * c
            if (isHorizontal(uF)) {
                entryD = first.start + approach * c
                if (lift(n) > EPS) leadD = leadFor(entry).also { entryD = it + n * (c + lift(n)) }
            } else {
                entryD = first.start + n * (c + lift(n))
            }
            startD = first.start
            fromSeg = 0
        } else {
            val convex = filleted.arc[0] == null && (((path[1] - path[0]) cross (path[2] - path[1])) > 0) == (side == Side.G42)
            if (convex) {
                // 경사 연장선은 재료가 아니라(볼록 모서리 바깥) 띄우지 않는다
                val back = path[1] - path[2]
                entry = segs[0].end + back * (c / abs(back.z))
                entryD = path[1] + back * (c / abs(back.z))
            } else {
                entry = segs[0].end - uF * c
                entryD = first.end - uF * c
                val n = toolNormal(uF, side)
                if (lift(n) > EPS) leadD = leadFor(entry).also { entryD = it + n * (c + lift(n)) }
            }
            startD = first.end
            fromSeg = 1
        }
        val program = Contour(segs)

        val warnings = mutableListOf<TaperWarning>()
        val slopes = 1 until drawn.size - 2 // drawn[k] – drawn[k+1] 이 경사
        if (slopes.any { r > (drawn[it + 1] - drawn[it]).length }) warnings += TaperWarning.NOSE_GT_SLOPE
        // 공구가 원 안쪽(오목): G41 이면 반시계, G42 면 시계 원호
        if (drawing.segs.any { it is Arc && (side == Side.G41) != it.cw && it.radius - r <= EPS }) warnings += TaperWarning.CONCAVE_R_LE_NOSE
        if (tip in 1..4 && againstTool(drawing, side, tip)) warnings += TaperWarning.AGAINST_TOOL
        if (entry.x < -EPS) warnings += TaperWarning.ENTRY_PAST_AXIS

        val ncG40 = NcLines().apply {
            move("G00", entry)
            // 시작점을 넣었으면 시작점′(시작 요소가 수평이면 진입점·시작점′ 이 그 보정선 위라 한 줄로 간다), 비웠으면 첫 꺾임점′
            if (s0 == null) move("G01", segs[0].end) else if (!isHorizontal(uF)) move("G01", s0)
            for (s in segs.drop(fromSeg)) if (s is Arc) arc(s) else move("G01", s.end)
            move("G00", exit)
        }.lines
        val ncG41G42 = listOf("(R${Fmt.nc(r).removeSuffix(".")} T$tip)") + NcLines().apply {
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
            for (s in drawing.segs.drop(fromSeg)) if (s is Arc) arc(s) else move("G01", s.end)
            // 취소 뒤 G40 상태의 인선 = 후퇴점. 진입과 같이 노즈가 끝 요소 선을 넘지 않게 lift 만큼 공구 쪽으로
            val nB = toolNormal(uB, side)
            move("G40 G00", last.end + leave * c + nB * lift(nB))
        }.lines

        // 꼭짓점 결과: 가공 순서의 k번 모서리 = 그림 순서의 (역방향이면 뒤집은) 번호
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
                anglesDeg = slopes.map { slopeDeg(drawn[it], drawn[it + 1]) },
                tip = tip,
                side = side,
                entry = entry,
                exit = exit,
                fromStart = cutFirst,
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
     * 도면 요소 가운데 노즈 접점이 팁 쪽 사분면(팁 벡터 ±45°) 밖인 것이 있나. 가상 인선은 Z·X 축에 평행한 두 접선이
     * 인선이라는 가정이라, 접점이 그 사분면 밖이면 실제 바이트의 날·여유각이 맞는지 따로 봐야 한다.
     * 모서리 R 원호는 접점 방향이 양옆 직선 사이에서만 돌므로 현의 방향으로 봐도 같다.
     */
    private fun againstTool(c: Contour, side: Side, tip: Int): Boolean {
        val toTip = NoseComp.tipVector(tip, 1.0).unit()
        return c.segs.any {
            val toTool = toolNormal((it.end - it.start).unit(), side)
            // 접점 방향 = −toTool. 팁 벡터와 45° 넘게 벌어지면 밖
            (toTool dot toTip) > -sqrt(0.5) + 1e-9
        }
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
        val ok = r >= 0 && r.isFinite() && zClear > 0 && zClear.isFinite() && (tip == null || tip in 0..9) &&
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
