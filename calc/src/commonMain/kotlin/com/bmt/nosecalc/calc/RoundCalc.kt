package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 라운드 경고. 화면이 한국어 문구를 고른다.
 * 위 여섯 개는 계산을 멈춘다 — [RoundResult.solution] 이 null 이고 경고는 그 하나뿐.
 * 아래 둘은 숫자와 함께 나온다.
 */
enum class RoundWarning {
    /** 값이 모자람. 비울 수 있는 값(구 중심이 축 위라 나머지로 정해지는 것)은 하나까지 */
    MISSING_INPUT,

    /** 길이·지름·R ≤ 0, 모서리 R·노즈R·Z 여유 < 0, 뒤 요소 길이 ≤ 0, 팁 0~9 밖, X < 0, 숫자가 아님, 좌표·치수가 ±[MAX_MM] 밖 */
    BAD_INPUT,

    /** 비울 수 있는 값까지 다 넣었는데 서로 0.001 mm 넘게 안 맞다 */
    CONTRADICTION,

    /** 치수로 그 TYPE 모양이 안 나온다(예: L ≥ 2R, R < D/2, D1 ≥ D2, 돔이 플랜지보다 큼, 세 점이 한 줄·겹침·시작과 끝 Z 가 같음) */
    SHAPE_MISMATCH,

    /** 모서리 R 이 너무 커서 이웃 요소를 다 먹거나 닿을 자리가 없다 */
    CORNER_R_TOO_BIG,

    /** 노즈R 에 비해 요소가 짧거나 좁아 공구가 닿지 못한다 — 도면 형상이 안 나온다 */
    NOSE_UNREACHABLE,

    /**
     * 오목 원호 R − 노즈R ≤ 0. 보정 원호가 없어진다(arcs 의 progR = null).
     * R = r 이면 공구가 꼭 맞아 정확하지만, R < r 이면 그 자리에 도면보다 큰 R(= r)이 남는다.
     */
    CONCAVE_R_LE_NOSE,

    /** 형상 일부가 바이트 방향과 반대다(노즈 접점이 팁 쪽 사분면 밖, 예: 공의 뒤쪽·왼쪽 면) — 끝날·여유각 확인 */
    AGAINST_TOOL,
}

/** TYPE 2 의 L2 를 어디서 재나. A = 왼쪽 이음점 ~ 오른쪽 끝(= L1 + B), B = 두 이음점 사이 */
enum class L2Ref { A, B }

/**
 * 라운드 TYPE 1~6(원본 04.jpg). 외경, 척 쪽(−Z)으로 깎음(G42). X 는 직경값, Z0 = 부품 오른쪽 끝.
 * 주 원호(Main R)는 중심이 축 위인 구다. 그래서 TYPE 2~5 에서는 R·D·L 가운데 하나가 나머지로 정해진다 —
 * 그 묶음([mainR] 과 TYPE 2 의 d1·l2, TYPE 3·4 의 d1·l1, TYPE 5 의 d2·d1·l2)은 하나를 비워도 되고, 다 넣으면 맞는지 본다.
 * TYPE 1·3·4 의 Corner R·D·L 은 [cornerR1]·[d1]·[l1] 에 넣는다.
 */
data class RoundInput(
    val type: Int = 1,
    val mainR: Double? = null,
    /** Corner R(1). TYPE 1 은 양쪽, TYPE 2 는 두 이음점 모두 이 값 */
    val cornerR1: Double? = null,
    /** Corner R(2) — TYPE 5·6 */
    val cornerR2: Double? = null,
    /** D1(TYPE 2·5·6) 또는 D(TYPE 3·4) */
    val d1: Double? = null,
    /** D2 — TYPE 5·6 */
    val d2: Double? = null,
    /** L1(TYPE 2·5·6) 또는 L(TYPE 1·3·4) */
    val l1: Double? = null,
    /** L2 — TYPE 2·5·6 */
    val l2: Double? = null,
    val l2Ref: L2Ref = L2Ref.B,
    val noseR: Double = 0.4,
    /** 가상 인선 번호(외경 정방향 표준 3) */
    val tip: Int = 3,
    /** 진입 여유: 첫 보정점에서 Z+ 로 이만큼 */
    val zClear: Double = 1.0,
    /** 마지막 원통·축(TYPE 2~6) 길이 — 그림과 마지막 NC 줄에만 쓰인다 */
    val legLen: Double = 5.0,
)

/** 수동 모드(CAD 3점호): 원호 위 세 점(시작 → 가운데 → 끝). 점 순서가 가공 방향이다. X 는 직경값 */
data class Arc3Input(
    val inner: Boolean = false,
    val noseR: Double = 0.4,
    /** null = 표준(외경 3·역 4, 내경 2·역 1). 역방향 = 끝 Z 가 시작보다 큼 */
    val tip: Int? = null,
    val x1: Double? = null,
    val z1: Double? = null,
    val x2: Double? = null,
    val z2: Double? = null,
    val x3: Double? = null,
    val z3: Double? = null,
    /**
     * 진입 여유: 첫 보정점에서 Z 로 이만큼 바깥(역방향은 −Z 쪽). 그쪽으로 물러나면 노즈 원이 시작점 옆을 파는 원호
     * (시작 접선이 재료로 가파르게 내려가는 오목 홈 등)는 시작 접선을 거꾸로 따라 이만큼
     */
    val zClear: Double = 1.0,
)

/** 도면 원호 하나. [index] = drawing.segs 번호, [progR] = 보정(합성) R — 볼록 R + r, 오목 R − r, 없어졌으면 null */
data class RoundArc(val index: Int, val cw: Boolean, val center: Pt, val r: Double, val progR: Double?)

data class RoundSolution(
    /** TYPE 모드: 빈 값을 채운 입력. 3점 모드는 null */
    val filled: RoundInput?,
    /** 노드 표: 요소 끝점마다 도면점과 보정점(가상 인선 = 프로그램 좌표). 가공 순서 */
    val nodes: List<CompPoint>,
    /** 원호 표(G02 = cw) */
    val arcs: List<RoundArc>,
    val tip: Int,
    /** G41/G42 로 프로그램할 때의 보정 쪽 */
    val side: Side,
    /** G00 진입점(프로그램 좌표) */
    val entry: Pt,
    /** G00 진입 → G01 시작점 → G01/G02/G03 보정 경로. R 지령, 바뀐 축만 */
    val nc: List<String>,
    /** 도면 윤곽(접점 모드), 가공 순서 */
    val drawing: Contour,
    /** 노즈 중심 궤적(합성 R). segs[k] ↔ drawing.segs[k] */
    val center: Contour,
    /** 가상 인선 경로 = 프로그램 경로. segs[k] ↔ drawing.segs[k] */
    val program: Contour,
)

data class RoundResult(val solution: RoundSolution?, val warnings: List<RoundWarning>)

/**
 * 라운드R 계산. 도면 윤곽은 직선·주 원호의 이론 꼭짓점에 모서리 R 을 접하게 끼워(접점 모드) 만들고,
 * 보정은 [NoseComp] 의 일반 해법(원호는 같은 중심, 볼록 R + r · 오목 R − r — 합성 R)에 맡긴다.
 */
object RoundCalc {
    /** 다 넣은 값끼리 이만큼(mm)까지 달라도 받아 준다 — 도면 치수를 0.001 로 반올림해 넣어도 되게 */
    private const val LEN_TOL = 1e-3

    /** [againstTool] 의 사분면 경계 여유(내적·rad, 각으로 0.06~0.08°) */
    private const val QUADRANT_TOL = 1e-3

    fun calc(i: RoundInput): RoundResult = guarded {
        require(i.type in 1..6) { "TYPE 은 1~6: ${i.type}" }
        checkTool(i.noseR, i.tip, i.zClear)
        if (!(i.legLen > 0 && i.legLen.isFinite())) stop(RoundWarning.BAD_INPUT)
        if (tooBig(i.mainR, i.cornerR1, i.cornerR2, i.d1, i.d2, i.l1, i.l2, i.legLen)) stop(RoundWarning.BAD_INPUT)
        val s = when (i.type) {
            1 -> type1(i)
            2 -> type2(i)
            3, 4 -> type34(i)
            5 -> type5(i)
            else -> type6(i)
        }
        val drawing = Contour(Corners.insert(s.segs, s.corners)?.segs ?: stop(RoundWarning.CORNER_R_TOO_BIG))
        finish(drawing, i.noseR, Side.G42, i.tip, Pt(i.zClear, 0.0), s.filled)
    }

    fun arc3(i: Arc3Input): RoundResult = guarded {
        val v = listOf(i.x1, i.z1, i.x2, i.z2, i.x3, i.z3)
        if (v.any { it == null }) stop(RoundWarning.MISSING_INPUT)
        if (v.any { !it!!.isFinite() } || listOf(i.x1!!, i.x2!!, i.x3!!).any { it < 0 } || tooBig(*v.toTypedArray())) {
            stop(RoundWarning.BAD_INPUT)
        }
        checkTool(i.noseR, i.tip, i.zClear)
        val p1 = Pt.ofDia(i.z1!!, i.x1)
        val p2 = Pt.ofDia(i.z2!!, i.x2)
        val p3 = Pt.ofDia(i.z3!!, i.x3)
        if ((p2 - p1).length <= EPS || (p3 - p2).length <= EPS || (p3 - p1).length <= EPS) stop(RoundWarning.SHAPE_MISMATCH)
        // 중심 = 두 현의 수직이등분선 교점. 평행이면 세 점이 한 줄
        val c = Intersect.lineLine((p1 + p2) * 0.5, (p2 - p1).left(), (p2 + p3) * 0.5, (p3 - p2).left())
            ?: stop(RoundWarning.SHAPE_MISMATCH)
        // 거의 한 줄이면 중심이 아주 멀리 간다(R 이 한도를 넘으면 한 줄로 본다)
        if (abs(p3.z - p1.z) <= EPS || tooBig(c.z, c.x)) stop(RoundWarning.SHAPE_MISMATCH)
        val reverse = p3.z > p1.z
        val arc = Arc(p1, p3, c, cw = ((p2 - p1) cross (p3 - p2)) < 0)
        val side = Side.of(i.inner, reverse)
        // 원호 하나뿐이라 오목 R < r 이면 남는 요소가 없다
        if ((side == Side.G41) != arc.cw && arc.radius < i.noseR - EPS) stop(RoundWarning.NOSE_UNREACHABLE)
        val tip = i.tip ?: NoseComp.standardTip(i.inner, reverse)
        // 진입: 첫 보정점에서 Z 로 zClear 바깥. 그쪽이 공구 쪽 법선과 반대면(시작 접선이 재료로 가파르게 내려가는 오목 홈 등)
        // Z 로 물러난 노즈 원이 시작점 옆 재료를 판다 → 그때는 시작 접선을 거꾸로 따라 zClear 만큼
        val away = Pt(if (reverse) -1.0 else 1.0, 0.0)
        val radial = (p1 - c).unit()
        val toTool = if ((side == Side.G41) == arc.cw) radial else radial * -1.0
        val back = radial.left() * (if (arc.cw) 1.0 else -1.0)
        val entryShift = if ((away dot toTool) >= 0) away * i.zClear else back * i.zClear
        finish(Contour(listOf(arc)), i.noseR, side, tip, entryShift, null)
    }

    /** 이론 윤곽(꼭짓점 그대로) + 꼭짓점마다 모서리 R(0 = 그대로). corners[k] = segs[k] 와 segs[k+1] 사이 */
    private class Shape(val filled: RoundInput, val segs: List<Seg>, val corners: List<Double>)

    /** 술통: 구 중심 (−L/2, 0), 양끝 면 Z0·Z−L */
    private fun type1(i: RoundInput): Shape {
        need(i.mainR, i.l1, i.cornerR1)
        positive(i.mainR, i.l1)
        cornerOk(i.cornerR1)
        val bigR = i.mainR!!
        val l = i.l1!!
        if (l >= 2 * bigR) stop(RoundWarning.SHAPE_MISMATCH)
        val h = sqrt(bigR * bigR - l * l / 4)
        val c = i.cornerR1!!
        return Shape(
            i,
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, h)),
                Arc(Pt(0.0, h), Pt(-l, h), Pt(-l / 2, 0.0), cw = false),
                Line(Pt(-l, h), Pt(-l, 0.0)),
            ),
            listOf(c, c),
        )
    }

    /** 아령: 축 끝 면 → 축 L1 → 공(이음점 사이 2s, 구 중심 (−L1 − s, 0)) → 축. R² = (D1/2)² + s² */
    private fun type2(i: RoundInput): Shape {
        need(i.l1, i.cornerR1)
        oneBlank(i.mainR, i.d1, i.l2)
        positive(i.mainR, i.d1, i.l1, i.l2)
        cornerOk(i.cornerR1)
        val l1 = i.l1!!
        val toB = if (i.l2Ref == L2Ref.A) l1 else 0.0
        val a0 = i.d1?.let { it / 2 }
        val s0 = i.l2?.let { (it - toB) / 2 }
        if (s0 != null && s0 <= 0) stop(RoundWarning.SHAPE_MISMATCH)
        val bigR: Double
        val a: Double
        val s: Double
        when {
            i.mainR == null -> {
                a = a0!!
                s = s0!!
                bigR = hypot(a, s)
            }
            a0 == null -> {
                bigR = i.mainR
                s = s0!!
                if (bigR <= s) stop(RoundWarning.SHAPE_MISMATCH)
                a = sqrt(bigR * bigR - s * s)
            }
            else -> {
                bigR = i.mainR
                a = a0
                if (bigR <= a) stop(RoundWarning.SHAPE_MISMATCH)
                s = sqrt(bigR * bigR - a * a)
                if (s0 != null && 2 * abs(s - s0) > LEN_TOL) stop(RoundWarning.CONTRADICTION)
            }
        }
        val zc = -l1 - s
        val zl = zc - s
        val c = i.cornerR1!!
        return Shape(
            i.copy(mainR = bigR, d1 = 2 * a, l2 = 2 * s + toB),
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, a)),
                Line(Pt(0.0, a), Pt(-l1, a)),
                Arc(Pt(-l1, a), Pt(zl, a), Pt(zc, 0.0), cw = false),
                Line(Pt(zl, a), Pt(zl - i.legLen, a)),
            ),
            listOf(0.0, c, c),
        )
    }

    /**
     * 돔(3)·공(4): 끝 (0, 0), 구 중심 (−R, 0), 원통·축과의 이음점 (−L, D/2). (L − R)² + (D/2)² = R² 이고
     * 돔은 이음점이 중심 오른쪽(L ≤ R), 공은 왼쪽(L ≥ R).
     */
    private fun type34(i: RoundInput): Shape {
        need(i.cornerR1)
        oneBlank(i.mainR, i.d1, i.l1)
        positive(i.mainR, i.d1, i.l1)
        cornerOk(i.cornerR1)
        val ball = i.type == 4
        val h0 = i.d1?.let { it / 2 }
        val bigR: Double
        val h: Double
        val l: Double
        when {
            i.mainR == null -> {
                h = h0!!
                l = i.l1!!
                if (if (ball) l < h else l > h) stop(RoundWarning.SHAPE_MISMATCH)
                bigR = (l * l + h * h) / (2 * l)
            }
            h0 == null -> {
                bigR = i.mainR
                l = i.l1!!
                if (l >= 2 * bigR || (if (ball) l < bigR else l > bigR)) stop(RoundWarning.SHAPE_MISMATCH)
                h = sqrt(l * (2 * bigR - l))
            }
            else -> {
                bigR = i.mainR
                h = h0
                if (bigR < h) stop(RoundWarning.SHAPE_MISMATCH)
                l = bigR + (if (ball) 1 else -1) * sqrt(bigR * bigR - h * h)
                if (i.l1 != null && abs(l - i.l1) > LEN_TOL) stop(RoundWarning.CONTRADICTION)
            }
        }
        return Shape(
            i.copy(mainR = bigR, d1 = 2 * h, l1 = l),
            listOf(
                Arc(Pt(0.0, 0.0), Pt(-l, h), Pt(-bigR, 0.0), cw = false),
                Line(Pt(-l, h), Pt(-l - i.legLen, h)),
            ),
            listOf(i.cornerR1!!),
        )
    }

    /**
     * 큰 원통 D2 → 볼록 구면 → 작은 축 D1. 구 중심 (zc, 0) 에서 작은 축 이음점 (−L1, a) 는 zc + u,
     * 큰 원통 이음점 (−L1 − L2, b) 는 zc + v (u = √(R² − a²), v = √(R² − b²), 둘 다 중심 오른쪽) → L2 = u − v.
     */
    private fun type5(i: RoundInput): Shape {
        need(i.l1, i.cornerR1, i.cornerR2)
        oneBlank(i.mainR, i.d2, i.d1, i.l2)
        positive(i.mainR, i.d2, i.d1, i.l1, i.l2)
        cornerOk(i.cornerR1, i.cornerR2)
        val a0 = i.d1?.let { it / 2 }
        val b0 = i.d2?.let { it / 2 }
        if (a0 != null && b0 != null && a0 >= b0) stop(RoundWarning.SHAPE_MISMATCH)
        val bigR: Double
        val a: Double
        val b: Double
        val l2: Double
        when {
            i.mainR == null -> {
                a = a0!!
                b = b0!!
                l2 = i.l2!!
                val k = (b * b - a * a) / l2 // u + v (u² − v² = b² − a², u − v = L2)
                if (k < l2) stop(RoundWarning.SHAPE_MISMATCH) // v < 0
                val u = (k + l2) / 2
                bigR = sqrt(u * u + a * a)
            }
            a0 == null -> {
                bigR = i.mainR
                b = b0!!
                l2 = i.l2!!
                if (bigR < b) stop(RoundWarning.SHAPE_MISMATCH)
                val u = sqrt(bigR * bigR - b * b) + l2
                if (u >= bigR) stop(RoundWarning.SHAPE_MISMATCH)
                a = sqrt(bigR * bigR - u * u)
            }
            b0 == null -> {
                bigR = i.mainR
                a = a0
                l2 = i.l2!!
                if (bigR <= a) stop(RoundWarning.SHAPE_MISMATCH)
                val v = sqrt(bigR * bigR - a * a) - l2
                if (v < 0) stop(RoundWarning.SHAPE_MISMATCH)
                b = sqrt(bigR * bigR - v * v)
            }
            else -> {
                bigR = i.mainR
                a = a0
                b = b0
                if (bigR < b) stop(RoundWarning.SHAPE_MISMATCH)
                l2 = sqrt(bigR * bigR - a * a) - sqrt(bigR * bigR - b * b)
                if (i.l2 != null && abs(l2 - i.l2) > LEN_TOL) stop(RoundWarning.CONTRADICTION)
            }
        }
        val l1 = i.l1!!
        val zc = -l1 - sqrt(bigR * bigR - a * a)
        val z2 = -l1 - l2
        return Shape(
            i.copy(mainR = bigR, d1 = 2 * a, d2 = 2 * b, l2 = l2),
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, a)),
                Line(Pt(0.0, a), Pt(-l1, a)),
                Arc(Pt(-l1, a), Pt(z2, b), Pt(zc, 0.0), cw = false),
                Line(Pt(z2, b), Pt(z2 - i.legLen, b)),
            ),
            listOf(0.0, i.cornerR1!!, i.cornerR2!!),
        )
    }

    /**
     * 플랜지 D2 → 면 → 돔 → 작은 축 D1. 구 중심은 작은 축 이음점에서 u = √(R² − a²) 왼쪽, 플랜지 면 z = −L1 − L2.
     * 돔 밑동 높이 = 구가 면을 자르는 높이(입력이 아님). 남는 값이 없다.
     */
    private fun type6(i: RoundInput): Shape {
        need(i.mainR, i.d2, i.d1, i.l1, i.l2, i.cornerR1, i.cornerR2)
        positive(i.mainR, i.d2, i.d1, i.l1, i.l2)
        cornerOk(i.cornerR1, i.cornerR2)
        val bigR = i.mainR!!
        val a = i.d1!! / 2
        val b = i.d2!! / 2
        val l1 = i.l1!!
        if (a >= b || bigR <= a) stop(RoundWarning.SHAPE_MISMATCH)
        val zc = -l1 - sqrt(bigR * bigR - a * a)
        val zf = -l1 - i.l2!!
        val dz = zf - zc
        if (abs(dz) >= bigR) stop(RoundWarning.SHAPE_MISMATCH)
        val hf = sqrt(bigR * bigR - dz * dz)
        // 돔의 가장 높은 곳: 중심이 면 오른쪽이면 꼭대기(R), 아니면 면에서
        if ((if (dz <= 0) bigR else hf) >= b) stop(RoundWarning.SHAPE_MISMATCH)
        return Shape(
            i,
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, a)),
                Line(Pt(0.0, a), Pt(-l1, a)),
                Arc(Pt(-l1, a), Pt(zf, hf), Pt(zc, 0.0), cw = false),
                Line(Pt(zf, hf), Pt(zf, b)),
                Line(Pt(zf, b), Pt(zf - i.legLen, b)),
            ),
            listOf(0.0, i.cornerR1!!, i.cornerR2!!, 0.0),
        )
    }

    /** 보정·노드 표·NC. [NoseComp] 의 일반 해법 그대로 */
    private fun finish(drawing: Contour, r: Double, side: Side, tip: Int, entryShift: Pt, filled: RoundInput?): RoundResult {
        val center = try {
            NoseComp.centerPath(drawing, r, side)
        } catch (e: IllegalArgumentException) {
            stop(RoundWarning.NOSE_UNREACHABLE)
        }
        if (center.warnings.any { it is CompWarning.Unreachable }) stop(RoundWarning.NOSE_UNREACHABLE)
        val program = NoseComp.tipPath(drawing, r, side, tip).path

        val warnings = mutableListOf<RoundWarning>()
        // 공구가 원 안쪽(오목): G41 이면 반시계, G42 면 시계 원호
        if (drawing.segs.any { it is Arc && (side == Side.G41) != it.cw && it.radius - r <= EPS }) {
            warnings += RoundWarning.CONCAVE_R_LE_NOSE
        }
        if (tip in 1..4 && againstTool(drawing, side, tip)) warnings += RoundWarning.AGAINST_TOOL

        val drawn = listOf(drawing.segs.first().start) + drawing.segs.map { it.end }
        val prog = listOf(program.segs.first().start) + program.segs.map { it.end }
        // 없어진 오목 원호 자리에는 이웃을 잇는 꼭짓점 원호가 들어갈 수 있다 — 그것은 합성 R 이 아니다
        val gone = center.warnings.map { it.index }.toSet()
        val arcs = drawing.segs.withIndex().mapNotNull { (k, s) ->
            (s as? Arc)?.let { RoundArc(k, it.cw, it.center, it.radius, if (k in gone) null else (program.segs[k] as? Arc)?.radius) }
        }
        val entry = prog[0] + entryShift
        val nc = NcLines().apply {
            move("G00", entry)
            move("G01", prog[0])
            for (s in program.segs) if (s is Arc) arc(s) else move("G01", s.end)
        }.lines
        return RoundResult(
            RoundSolution(
                filled = filled,
                nodes = drawn.zip(prog) { d, p -> CompPoint(d, p) },
                arcs = arcs,
                tip = tip,
                side = side,
                entry = entry,
                nc = nc,
                drawing = drawing,
                center = center.path,
                program = program,
            ),
            warnings,
        )
    }

    /**
     * 노즈 접점이 팁 쪽 사분면(팁 벡터 ±45°) 밖인 요소가 있나([TaperCalc] 와 같은 기준). 원호는 접점 방향이 돈 각
     * = 원호 각이라, 90° 를 넘거나 양 끝 가운데 하나가 사분면 밖이면 밖이다.
     */
    private fun againstTool(c: Contour, side: Side, tip: Int): Boolean {
        val toTip = NoseComp.tipVector(tip, 1.0).unit()
        // 사분면 경계(접점 45°·원호 90°)를 0.1° 쯤 넘는 것까지는 안으로 본다. 3점호는 CAD 점을 넷째 자리로 넣어 중심·접선이
        // 조금 흔들려서, 단면에서 외경으로 도는 보통의 볼록 R(90°)이 경계를 1e-6 쯤 넘어 경고가 났다
        fun off(toTool: Pt) = (toTool dot toTip) > -sqrt(0.5) + QUADRANT_TOL
        fun arcToTool(a: Arc, p: Pt): Pt {
            val n = (p - a.center).unit()
            return if ((side == Side.G41) == a.cw) n else n * -1.0 // 볼록이면 공구가 원 바깥
        }
        return c.segs.any { s ->
            when (s) {
                is Line -> {
                    val d = s.end - s.start
                    d.length > EPS && off(d.unit().left() * (if (side == Side.G41) 1.0 else -1.0))
                }
                is Arc -> {
                    val sw = s.sweepRad()
                    sw * s.radius > EPS && (sw > PI / 2 + QUADRANT_TOL || off(arcToTool(s, s.start)) || off(arcToTool(s, s.end)))
                }
            }
        }
    }

    private fun need(vararg v: Double?) {
        if (v.any { it == null }) stop(RoundWarning.MISSING_INPUT)
    }

    private fun oneBlank(vararg v: Double?) {
        if (v.count { it == null } > 1) stop(RoundWarning.MISSING_INPUT)
    }

    private fun positive(vararg v: Double?) {
        if (v.any { it != null && !(it > 0 && it.isFinite()) }) stop(RoundWarning.BAD_INPUT)
    }

    private fun cornerOk(vararg v: Double?) {
        if (v.any { it != null && !(it >= 0 && it.isFinite()) }) stop(RoundWarning.BAD_INPUT)
    }

    private fun checkTool(r: Double, tip: Int?, zClear: Double) {
        val ok = r >= 0 && r.isFinite() && zClear >= 0 && zClear.isFinite() && (tip == null || tip in 0..9) && !tooBig(r, zClear)
        if (!ok) stop(RoundWarning.BAD_INPUT)
    }

    /** 계산을 멈추는 경고. [guarded] 가 받아 결과로 바꾼다 */
    private class Halt(val warning: RoundWarning) : Exception()

    private fun stop(w: RoundWarning): Nothing = throw Halt(w)

    private inline fun guarded(block: () -> RoundResult): RoundResult =
        try {
            block()
        } catch (h: Halt) {
            RoundResult(null, listOf(h.warning))
        }
}
