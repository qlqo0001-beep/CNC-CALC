package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * 턴밀 극좌표 다각가공 — FANUC 극좌표 보간 G12.1(G112) / 취소 G13.1(G113).
 *
 * 출처(확인함, 2026-10-28):
 *   A. FANUC Series 0+-MODEL F Lathe 조작 설명서 B-64604EN-1/01 3.1 "POLAR COORDINATE INTERPOLATION (G12.1, G13.1)" 16~23쪽,
 *      5.3 · 5.4(공구 반경 보정 시작 · 취소), 4.6(모서리 R 과 보정), 표 2 (a) G 코드 목록 12~13쪽.
 *   B. FANUC Series 0/00/0-Mate Lathe 조작 설명서 B-61394E/08 4.4 "POLAR COORDINATE INTERPOLATION (G112, G113)" 48~52쪽.
 *
 *   - G12.1 · G13.1 은 따로 한 줄씩(A "Specify G12.1 and G13.1 in Separate Blocks").
 *   - 평면 = 직선축 X(첫째 축) + 그에 직각인 가상축 C(둘째 축). 원점 = 회전축 중심(A "a local coordinate system (or workpiece
 *     coordinate system) where the center of the rotary axis is the origin ... must be set"). 가상축 단위는 직선축과 같은 mm
 *     (A "The unit for coordinates on the hypothetical axis is the same as the unit for the linear axis").
 *     지령은 이 평면의 직교 좌표 — 중심 너머의 점은 X 가 음수다(A 의 예 `G01 X-20.0`, B 의 예 `G01 X-40.0`). 제어기가 실제 X(중심에서 거리)와
 *     C 각도로 바꾼다(A "Actual coordinates are displayed"). 원본 07.jpg 의 `X (DIA) -36.951 · C -40.` 이 그 직교 좌표다.
 *   - 원호: G02 · G03 은 X 를 첫째, C 를 둘째 축으로 본 평면(XpYp 처럼 I · J, 또는 R — A "I and J in the Xp-Yp plane when the linear
 *     axis is the X-axis", "The radius of an arc can be specified also with an R command"). 그래서 X 오른쪽 · C 위로 그린 평면에서
 *     G02 = 시계. A 의 예(바깥 윤곽을 반시계로 돌며 `G42` · 볼록 모서리 `G03 X10.0 C20.0 R10.0`)와 원본 07.jpg(시계로 돌며 `G02 … R8.`)가 맞는다.
 *   - 가상축은 G12.1 바로 뒤 0, 그때 공구 자리를 회전각 0 으로 본다(A "The hypothetical axis is at coordinate 0 immediately after G12.1 is
 *     specified. Polar interpolation is started assuming the rotation angle of 0 for the position of the tool"). 그래서 G12.1 앞에서
 *     `G00 X… C0.`(A · B 의 예 `G00 X120.0 C0 Z_`) — 시작점은 늘 X 축 위(C = 0).
 *   - 모드 안에서 쓸 수 있는 G: G01, G02/G03, G04, G40/G41/G42, G65~G67, G90/G91, 분당 · 회전당 이송(A 표: G94/G95 = 계통 B·C, B: G98/G99).
 *     **G00 은 목록에 없다** — 모드 안 이동은 모두 G01 · G02 · G03.
 *   - 이송 F 는 평면(직교 좌표)의 접선 속도 mm/min(A "The unit for the feedrate is mm/min"). 그래서 앞에 `G98`(G 코드 계통 A 의 분당 이송,
 *     A 표 2 (a) "G98 G94 G94 … Feed per minute").
 *   - 보정: G41/G42 를 쓸 수 있고 보정한 경로를 극좌표로 바꾼다(A "The polar coordinate interpolation is performed for a path obtained
 *     after tool radius compensation"). **G12.1 · G13.1 은 G40 상태에서만** — 보정을 걸고 모드를 바꾸면 알람 PS0037(A 20쪽).
 *     G40 취소 블록은 극좌표 평면의 축을 움직여야 한다(A "be sure to specify the polar coordinate axis to cancel the offset vector").
 *     보정 시작 · 취소는 G00/G01 직선에서만(A 5.3 "If circular interpolation (G02, G03) is specified, alarm PS0034").
 *     공구 보정 번호는 G12.1 앞에서(A "A tool offset must be specified before the G12.1 mode is set").
 *   - X 의 직경/반경: **매뉴얼끼리 다르다.** B 의 예는 "X axis is by diameter programming, C axis is by radius programming" 이라 적고
 *     X40.0(반경 20) · `G03 X20.0 C20.0 R10.0` 으로 쓰고, A 의 같은 그림 예는 X20.0 · `G03 X10.0 C20.0 R10.0`(X 가 반경) 이며 본문은
 *     "Whether a diameter or radius is specified for the first axis in the plane is the same as for the rotary axis regardless of the
 *     specification for the first axis"(회전축은 반경 → X 도 반경으로 읽힘). 기계 · 매개변수마다 다르므로 [PolarInput.xDiameter] 로 고른다.
 *     기본은 직경(원본 07.jpg 의 `X (DIA)` · `X92.376` = 맞모서리 지름, B 의 예). R · I · J 는 어느 쪽이든 실제 mm(B 의 `R10.0` · `I10.0`).
 *   - 중심 가까이 가면 C 축 속도가 커진다(A 20~22쪽, F < L·R·π/180 — R 은 기계의 C 축 최대 이송이라 이 앱은 모른다).
 *
 * 공구 보정 화면의 값: R = 공구 반경, 가상 인선 T = 0(중심 — Haas ST/TL 워크북 69~70쪽 표의 T0/T9 = 공구 중심이 프로그램 점).
 * 기계마다 다른 것(C 축 모드 M 코드, 회전 공구 회전 M 코드 · 회전수, T 번호)은 넣지 않는다 — 프로그램 앞에 붙여 쓴다.
 */

/** 도형. [sides] = 변 수(2면폭은 2) */
enum class PolarShape(val sides: Int) {
    TRIANGLE(3),
    SQUARE(4),
    PENTAGON(5),
    HEXAGON(6),
    OCTAGON(8),

    /** 2면폭: 마주 보는 두 평면만(나머지는 소재 원통면). 외측만 */
    TWO_FLAT(2),
}

/** 경고. 위는 계산을 멈춘다([PolarResult.solution] = null, 경고 하나), 아래는 숫자와 함께 */
enum class PolarWarning {
    /** 외경(내측은 구멍 지름) · W · 공구경 · 깊이 Z · 이송 F 가운데 빈 것이 있다 */
    MISSING_INPUT,

    /**
     * 숫자 아님 · 크기 한도 밖, 지름 · W · 공구경 · F ≤ 0, 코너 R · 여유 < 0, 깊이 Z ≥ 0(면 Z0 아래를 깎는다), F 가 [PolarG12.MAX_FEED] 넘음,
     * 내측 2면폭(없는 도형)
     */
    BAD_INPUT,

    /** 외측: W ≥ 외경 — 평면이 소재에 닿지 않는다 */
    W_GE_OD,

    /** 내측: 구멍 지름 > W — 평면 가운데가 구멍에 끊긴다 */
    HOLE_GT_W,

    /** 코너 R ≥ W/2 — 평면이 없어진다 */
    CORNER_R_TOO_BIG,

    /** 내측: 공구경 ≥ W/2 — 진입 · 후퇴 원호(반지름 > 공구 반경)를 그릴 자리가 없다 */
    TOOL_TOO_BIG,

    /** 내측: 공구 반경 + 여유 > 구멍 반경 — 공구가 구멍 안으로 내려가지 못한다 */
    TOOL_GT_HOLE,

    /* ↑ 멈춤 · ↓ 숫자와 함께 */

    /** 내측 코너 R < 공구 반경 — 모서리는 공구 반경으로 남는다. G41/G42 프로그램은 그 R 원호를 빼고 날카로운 꼭짓점으로 쓴다 */
    INNER_CORNER_LT_TOOL,

    /** 가장 두꺼운 살 > 공구경 — 한 바퀴로 다 못 깎는다(바깥 · 안쪽에 살이 남음). 여러 번 나눠 깎는다 */
    STOCK_LEFT,

    /** 외측 다각형: 꼭짓점(코너 R 끝)이 소재 밖 — 모서리 끝은 소재 원통면으로 남는다 */
    CORNERS_OUTSIDE_OD,
}

/**
 * 입력. 도면 값(외경 · W · 공구경 · 깊이 Z)과 이송 F 는 비면 [PolarWarning.MISSING_INPUT] — F 는 출처 있는 기본값이 없어서.
 * 코너 R · 회전은 비면 0, 여유는 비면 [PolarG12.CLEAR_DEFAULT]. 길이 mm, 지름은 직경값.
 */
data class PolarInput(
    /** true = 내측(구멍 안 다각형) */
    val inner: Boolean = false,
    val shape: PolarShape = PolarShape.HEXAGON,
    /** 외측 = 소재 외경, 내측 = 기초 구멍 지름 */
    val od: Double? = null,
    /** W: 맞변 폭(짝수 각 · 2면폭). 홀수 각(3 · 5)은 내접원 지름(= 2 × 중심에서 변까지) */
    val width: Double? = null,
    /** 코너 R(2면폭은 쓰지 않음) */
    val cornerR: Double? = null,
    /** 회전(°): X 에서 C(+) 쪽이 +. 0° 는 한 변이 C 축에 직각(육각형이면 꼭짓점이 X 축 위 — 원본 12.jpg) */
    val rotationDeg: Double? = null,
    /** 공구경(엔드밀 지름) */
    val toolDia: Double? = null,
    /** 깊이 Z(깎는 바닥 Z, 면 Z0 아래라 음수) */
    val depthZ: Double? = null,
    /** 이송 F(mm/min, 극좌표 평면의 접선 속도) */
    val feed: Double? = null,
    /** 여유(mm): 시작점이 소재(내측은 구멍 벽)에서 떨어지는 거리, Z 접근 높이(면 Z0 위). 빈칸 = 1 */
    val clear: Double? = null,
    /** G12.1 안 X 를 직경값으로 쓴다(기본, 원본 · FANUC 0 의 예). false = 반경값(0i-F 매뉴얼의 예). 기계 매개변수를 확인한다 */
    val xDiameter: Boolean = true,
)

/**
 * NC 한 줄과 그 줄 동안의 움직임(시뮬레이션용).
 * [path] = 이 줄 동안 공구 중심이 극좌표 평면에서 지나는 길(Pt — [PolarG12.pt] 참고). 평면 이동이 없는 줄(Z · G12.1 · 주석)은 빈 목록.
 * G41/G42 프로그램은 제어기가 보정한 공구 중심(시작 · 취소 · 볼록 꼭짓점 처리 포함), 직접 보정 프로그램은 쓴 좌표 그대로.
 * [x] · [c] · [z] = 줄 끝의 지령값(프로그램에 쓰는 수 — X 는 G12.1 앞이면 직경, 안이면 [PolarInput.xDiameter] 에 따라. 정하기 전은 null).
 * G12.1 앞 `C0.` 은 각도 0°, 안의 C 는 mm.
 */
data class PolarBlock(
    val text: String,
    val path: List<Seg>,
    val x: Double?,
    val c: Double?,
    val z: Double?,
    /** 이 줄이 G12.1 모드 안(G12.1 줄 포함, G13.1 줄 빼고) */
    val polar: Boolean,
)

data class PolarProgram(val blocks: List<PolarBlock>) {
    val lines: List<String> get() = blocks.map { it.text }

    /** 극좌표 평면의 공구 중심 경로 전체(G12.1 시작점부터) */
    val toolPath: Contour get() = Contour(blocks.flatMap { it.path })
}

data class PolarSolution(
    /** 다 깎은 단면 윤곽(도면, 코너 R 그대로) — 가공 순서 · 방향, 시작 = 첫 변의 진입점. 2면폭은 평면 둘 + 소재 원호 둘 */
    val shape: Contour,
    /** 외측 소재 반경 · 내측 구멍 반경 */
    val stockR: Double,
    val toolR: Double,
    /** 보정 쪽 — 외측은 시계, 내측은 반시계로 돌아 늘 공구가 진행 방향 왼쪽 */
    val side: Side,
    /** 진입 · 후퇴 원호 반지름(도면, 직접 보정은 이것 − 공구 반경) */
    val leadR: Double,
    /** G12.1 시작점 X(반경, C = 0). 외측은 소재 밖, 내측은 구멍 안 */
    val startX: Double,
    /** 꼭짓점 지름(코너 R 끝을 지나는 원 — 짝수 각이면 맞모서리). 2면폭은 외경 */
    val cornerDia: Double,
    /** 한 평면의 길이(코너 R 접점 사이). 2면폭은 소재 안 현의 길이 */
    val flatLength: Double,
    /** 가장 두꺼운 살(윤곽에서 잰 거리) — 공구경보다 크면 [PolarWarning.STOCK_LEFT] */
    val stockMax: Double,
    /** 기계 보정: 도면 좌표 + G41, 첫 줄 `(R… T0)` */
    val ncG41G42: PolarProgram,
    /** 직접 보정: 공구 중심 좌표 G40, 첫 줄 `(G40 DIRECT R… T0)` */
    val ncG40: PolarProgram,
)

data class PolarResult(val solution: PolarSolution?, val warnings: List<PolarWarning>)

object PolarG12 {
    /** 여유 기본(mm) — 원본에 없어 다른 계산의 여유 기본(1 mm)과 같게 */
    const val CLEAR_DEFAULT = 1.0

    /** 이송 상한(mm/min) — 숫자 실수 막이 */
    const val MAX_FEED = 100_000.0

    /**
     * 극좌표 평면의 점 (X 반경, C) 을 Pt 로: `Pt(z = −C, x = X)`. 화면이 Pt 를 Z 오른쪽 · X 위로 그리면 원본 그림(X 위, C(+) 왼쪽)이 되고,
     * X–C 평면을 돌린 것이라(거울 아님) [Arc.cw] = G02 가 그대로다. Pt.xDia = 직경값 X.
     */
    fun pt(x: Double, c: Double) = Pt(-c, x)

    /** Pt 의 X(반경) */
    fun xOf(p: Pt) = p.x

    /** Pt 의 C(mm) */
    fun cOf(p: Pt) = -p.z

    fun calc(i: PolarInput): PolarResult {
        val od = i.od
        val w = i.width
        val d = i.toolDia
        val depth = i.depthZ
        val f = i.feed
        if (od == null || w == null || d == null || depth == null || f == null) return stop(PolarWarning.MISSING_INPUT)
        val twoFlat = i.shape == PolarShape.TWO_FLAT
        val rcIn = if (twoFlat) 0.0 else i.cornerR ?: 0.0
        val rotDeg = i.rotationDeg ?: 0.0
        val clear = i.clear ?: CLEAR_DEFAULT
        val bad = listOf(od, w, d, depth, f, rcIn, rotDeg, clear).any { !it.isFinite() } ||
            tooBig(od, w, d, depth, rcIn, rotDeg, clear) || od <= 0 || w <= 0 || d <= 0 || rcIn < 0 || clear < 0 ||
            depth >= 0 || f <= 0 || f > MAX_FEED || (i.inner && twoFlat)
        if (bad) return stop(PolarWarning.BAD_INPUT)
        // NC 단위(0.001)보다 천 배 작은 코너 R 은 꼭짓점이다 — 그대로 두면 R 의 방향이 수치 오차에 묻혀(1e-11 이면 보정 원호 끝이 3 μm 흔들림)
        // 쓸모없는 오차만 생긴다(3단계 최종 점검)
        val rc = if (rcIn < TINY_R) 0.0 else rcIn

        val a = w / 2
        val rs = od / 2
        val r = d / 2
        if (!i.inner && a >= rs) return stop(PolarWarning.W_GE_OD)
        if (i.inner && rs > a) return stop(PolarWarning.HOLE_GT_W)
        if (!twoFlat && rc >= a) return stop(PolarWarning.CORNER_R_TOO_BIG)
        if (i.inner && a <= 2 * r) return stop(PolarWarning.TOOL_TOO_BIG)
        if (i.inner && rs < r + clear) return stop(PolarWarning.TOOL_GT_HOLE)

        val rot = rotDeg * PI / 180
        val plan = if (twoFlat) twoFlatPlan(a, rs, r, clear, rot) else if (i.inner) innerPlan(i.shape.sides, a, rs, r, rc, clear, rot)
        else outerPlan(i.shape.sides, a, rs, r, rc, clear, rot)

        val head = Head(plan.start, clear, depth, f)
        val rText = Fmt.nc(r).removeSuffix(".")
        val ncG41 = program("(R$rText T0)", head, plan, r, i.xDiameter, direct = false)
        val ncG40 = program("(G40 DIRECT R$rText T0)", head, plan, r, i.xDiameter, direct = true)

        val warnings = buildList {
            if (i.inner && rc < r - EPS) add(PolarWarning.INNER_CORNER_LT_TOOL)
            if (plan.stockMax > 2 * r + EPS) add(PolarWarning.STOCK_LEFT)
            if (!i.inner && !twoFlat && plan.cornerDia / 2 > rs + EPS) add(PolarWarning.CORNERS_OUTSIDE_OD)
        }
        return PolarResult(
            PolarSolution(
                plan.shape, rs, r, Side.G41, plan.leadR, plan.start.x, plan.cornerDia, plan.flatLength, plan.stockMax, ncG41, ncG40,
            ),
            warnings,
        )
    }

    private fun stop(w: PolarWarning) = PolarResult(null, listOf(w))

    /** 이보다 작은 코너 R 은 0(꼭짓점) */
    private const val TINY_R = 1e-6

    // ------------------------------------------------------------------ 경로 계획

    /**
     * 한 번 깎기: G40 으로 [o] 까지([pre] — 외측은 원점 둘레 원호, 내측은 직선), G41 시작 이동 o → comp[0].start(진입 원호의 시작 Q, 그 원호의
     * 시작 접선과 한 직선), 보정 모드 요소 [comp](진입 원호 · 윤곽 · 후퇴 원호, 도면), G40 취소 이동 comp.last().end → [o3](후퇴 원호의
     * 끝 접선과 한 직선). 시작 · 취소가 다음 · 앞 요소와 한 직선이면 FANUC Type A · B 가 같고(보정 벡터 = 그 원호에 직각 r) 직접 보정과 같은 점이다.
     */
    private class Cut(val pre: List<Seg>, val o: Pt, val comp: List<Seg>, val o3: Pt)

    private class Plan(
        val start: Pt, val cuts: List<Cut>, val shape: Contour, val leadR: Double,
        val cornerDia: Double, val flatLength: Double, val stockMax: Double,
    )

    /** 시작점 · Z 접근 · 깊이 · 이송 */
    private class Head(val start: Pt, val zUp: Double, val depth: Double, val feed: Double)

    /** 정다각형(변 n, 중심에서 변까지 a, 회전 rot). 변 k 의 바깥 법선은 X 에서 C 쪽으로 90° + rot + k·360°/n */
    private class Polygon(val n: Int, val a: Double, val rot: Double) {
        val half = PI / n

        fun normal(k: Int) = dirXC(PI / 2 + rot + 2 * PI * k / n)

        /** 변 k 와 k+1(반시계로 다음) 사이 모서리 원의 중심(반지름 rc). rc = 0 이면 꼭짓점 */
        fun cornerCenter(k: Int, rc: Double): Pt {
            val nk = normal(k)
            val ap = a - rc
            return nk * ap + nk.left() * (ap * tan(half))
        }

        /** 반시계로 한 바퀴(모서리 R = rc). 변 k0 가운데에서 시작해 거기서 끝난다 */
        fun ccw(k0: Int, rc: Double): List<Seg> {
            val out = mutableListOf<Seg>()
            val p0 = normal(k0) * a
            var cur = p0
            for (j in 0 until n) {
                val k = k0 + j
                val cc = cornerCenter(k, rc)
                val t1 = cc + normal(k) * rc
                out += Line(cur, t1)
                cur = if (rc > 0) {
                    val t2 = cc + normal(k + 1) * rc
                    out += Arc(t1, t2, cc, cw = false)
                    t2
                } else {
                    t1
                }
            }
            out += Line(cur, p0)
            return out
        }

        /**
         * 모서리 R = rc 인 다각형 안쪽 점에서 둘레까지 거리. 둥근 다각형 = 중심에서 변까지 a − rc 인 작은 다각형(core)을 rc 만큼 부풀린 것이라
         * core 안이면 rc + (core 변까지 가장 가까운 거리), 밖이면 rc − (core 까지 거리).
         */
        fun insideDist(p: Pt, rc: Double): Double {
            val ap = a - rc
            val slack = (0 until n).minOf { ap - (p dot normal(it)) }
            if (slack >= 0) return rc + slack
            val outside = (0 until n).minOf { k -> segDist(p, cornerCenter(k - 1, rc), cornerCenter(k, rc)) }
            return rc - outside
        }
    }

    private fun outerPlan(n: Int, a: Double, rs: Double, r: Double, rc: Double, clear: Double, rot: Double): Plan {
        val poly = Polygon(n, a, rot)
        val lead = 2 * r
        val lMin = max(clear, r)
        // 시작 원(반지름 rho): 소재 밖 r + 여유, 그리고 시작 이동 길이가 lMin 이상이 되게
        val rho = max(rs + r + clear, hypot(a + lead + lMin, lead))
        val l = sqrt(rho * rho - lead * lead) - a - lead
        // 시계로 돈다(공구 = 진행 왼쪽 = 바깥, G41). 진행 방향 d = −left(n): O = (a + lead + l)n − lead·d
        fun oOf(k: Int): Pt {
            val nk = poly.normal(k)
            return nk * (a + lead + l) + nk.left() * lead
        }
        val k0 = (0 until n).minBy { abs(angXC(oOf(it))) }
        val start = PolarG12.pt(rho, 0.0)
        val contour = reverse(poly.ccw(k0, rc))
        val cut = leads(contour, lead, l, pre = { o -> aroundOrigin(start, o) })
        return Plan(
            start, listOf(cut), Contour(contour), lead,
            cornerDia = 2 * ((a - rc) / cos(poly.half) + rc),
            flatLength = 2 * (a - rc) * tan(poly.half),
            stockMax = rs - a,
        )
    }

    private fun innerPlan(n: Int, a: Double, rs: Double, r: Double, rc: Double, clear: Double, rot: Double): Plan {
        val poly = Polygon(n, a, rot)
        // 진입 원 반지름: 2r(외측과 같게), 좁으면 a/2. r < lead ≤ a − r 라 원호도 시작점(−lead·d)의 공구도 내접원 안에 든다
        val lead = min(2 * r, a / 2)
        val start = PolarG12.pt(rs - r - clear, 0.0)
        // 반시계로 돈다(공구 = 진행 왼쪽 = 안쪽, G41). 진행 방향 d = left(n). O = Q − (a − lead)·n = −lead·d
        val k0 = (0 until n).minBy { (poly.normal(it).left() * -lead - start).length }
        // 코너 R ≤ r 은 공구가 못 따라간다 — G41 프로그램은 날카로운 꼭짓점(제어기가 두 보정선의 교점을 잡음 = 직접 보정과 같은 점)
        val contour = poly.ccw(k0, if (rc <= r + EPS) 0.0 else rc)
        val cut = leads(contour, lead, a - lead, pre = { o -> listOf(Line(start, o)) })
        // 가장 두꺼운 살 = 구멍 밖 점에서 둘레까지 거리의 최댓값. 둘레까지 거리는 볼록 도형 안에서 오목 함수라(중심에서 가장 크다) 구멍 밖 영역의
        // 최대는 구멍 둘레에 있고, 둘레 위에서는 꼭짓점 방향이 가장 멀다 — 그 방향 점이 core 안이면 가까운 두 변이 가장 멀고, core 밖이면
        // core 꼭짓점이 가장 가깝다(그때 변 방향 점은 더 밖)
        val stockMax = poly.insideDist(dirXC(PI / 2 + rot + poly.half) * rs, rc)
        return Plan(
            start, listOf(cut), Contour(poly.ccw(k0, rc)), lead,
            cornerDia = 2 * ((a - rc) / cos(poly.half) + rc),
            flatLength = 2 * (a - rc) * tan(poly.half),
            stockMax = stockMax,
        )
    }

    private fun twoFlatPlan(a: Double, rs: Double, r: Double, clear: Double, rot: Double): Plan {
        val lead = 2 * r
        val lMin = max(clear, r)
        // 진입점: 평면 직선이 소재 + 여유 원과 만나는 곳(소재 밖) — 평면 전체를 깎는다
        val te = sqrt((rs + clear) * (rs + clear) - a * a)
        val rho = max(rs + r + clear, hypot(a + lead + lMin, te + lead))
        val l = sqrt(rho * rho - (te + lead) * (te + lead)) - a - lead
        val ns = listOf(dirXC(PI / 2 + rot), dirXC(PI / 2 + rot + PI))
        // 시계로(진행 d = −left(n)): O = (a + lead + l)n − (te + lead)d
        fun oOf(j: Int) = ns[j] * (a + lead + l) + ns[j].left() * (te + lead)
        val first = (0..1).minBy { abs(angXC(oOf(it))) }
        val order = listOf(first, 1 - first)
        val start = PolarG12.pt(rho, 0.0)
        val cuts = mutableListOf<Cut>()
        var cur = start
        for (j in order) {
            val n0 = ns[j]
            val dd = n0.left() * -1.0
            val cut = leads(listOf(Line(n0 * a - dd * te, n0 * a + dd * te)), lead, l, pre = { o -> aroundOrigin(cur, o) })
            cuts += cut
            cur = cut.o3
        }
        // 다 깎은 단면: 평면(소재 안 현) 둘 + 소재 원호 둘, 시계로
        val s = sqrt(rs * rs - a * a)
        val chords = order.map { j -> ns[j] to ns[j].left() * -1.0 }.map { (n0, dd) -> Line(n0 * a - dd * s, n0 * a + dd * s) }
        val origin = Pt(0.0, 0.0)
        val shape = Contour(
            listOf(chords[0], Arc(chords[0].end, chords[1].start, origin, cw = true), chords[1], Arc(chords[1].end, chords[0].start, origin, cw = true)),
        )
        return Plan(start, cuts, shape, lead, cornerDia = 2 * rs, flatLength = 2 * s, stockMax = rs - a)
    }

    /**
     * 윤곽 [contour](공구 = 진행 왼쪽)에 진입 · 후퇴 원호(반지름 [lead], 공구 쪽에 중심 — 반시계)와 그 접선 위 시작 · 취소 이동(길이 [l])을 붙인다.
     * 닫힌 다각형은 같은 점(변 가운데)에서 들어가고 나와 보정한 경로가 그 점에서 꼭 맞는다(이음 자국 · 남는 살 없음).
     */
    private fun leads(contour: List<Seg>, lead: Double, l: Double, pre: (Pt) -> List<Seg>): Cut {
        val e = contour.first().start
        val d0 = tanStart(contour.first())
        val m0 = d0.left()
        val q = e + m0 * lead - d0 * lead
        val leadIn = Arc(q, e, e + m0 * lead, cw = false)
        val x = contour.last().end
        val d1 = tanEnd(contour.last())
        val m1 = d1.left()
        val q2 = x + m1 * lead + d1 * lead
        val leadOut = Arc(x, q2, x + m1 * lead, cw = false)
        val o = q + m0 * l
        return Cut(pre(o), o, listOf(leadIn) + contour + leadOut, q2 + m1 * l)
    }

    /** G40 상태로 원점 둘레(같은 반지름) 짧은 쪽 원호 — 소재 밖에서 C 만 돈다. 같은 점이면 없음 */
    private fun aroundOrigin(from: Pt, to: Pt): List<Seg> {
        val sweep = wrapPi(angleOf(to) - angleOf(from))
        if (abs(sweep) * from.length <= EPS) return emptyList()
        return listOf(Arc(from, to, Pt(0.0, 0.0), cw = sweep < 0))
    }

    // ------------------------------------------------------------------ 보정

    /**
     * 보정 모드 요소의 공구 중심 — 진행 왼쪽(G41)으로 r. 요소 i 의 움직임 목록(첫 것이 그 요소의 보정선).
     * 접해서 이어지는 곳은 그대로, 날카로운 꼭짓점(변끼리만 생김)은
     * - 안쪽(왼쪽으로 꺾임): 두 보정선의 교점 — 제어기(A 5.4.3)와 직접 보정이 같다.
     * - 바깥(오른쪽으로 꺾임): 직접 보정은 꼭짓점 둘레 반지름 r 원호(따로 한 블록 — 공구가 꼭짓점에 붙어 돈다),
     *   기계 흉내는 FANUC: 둔각(안쪽 각 ≥ 90°)은 늘인 두 보정선의 교점, 예각은 두 보정선을 r 늘인 끝을 잇는 꺾은선(A 5.4.3).
     */
    private fun centerPaths(comp: List<Seg>, r: Double, direct: Boolean): List<List<Seg>> {
        val off = comp.map { offset(it, r) }.toMutableList()
        val extra = List(comp.size) { mutableListOf<Seg>() }
        for (k in 0 until comp.size - 1) {
            val ta = tanEnd(comp[k])
            val tb = tanStart(comp[k + 1])
            val cr = ta cross tb
            val dt = ta dot tb
            if (abs(cr) <= 1e-9 && dt > 0) continue
            // 원호가 낀 이음(진입 · 후퇴 원호, 모서리 R)은 만들 때부터 접한다 — 꺾여 보이면 접선의 수치 오차다. 아주 작은 모서리 R(키패드로
            // 칠 수 있는 0.00000000001 등)은 접선 오차가 1e-9 를 넘어 꼭짓점으로 보였고, 원호를 직선으로 바꾸다 앱이 죽었다(3단계 최종 점검)
            val la = off[k] as? Line ?: continue
            val lb = off[k + 1] as? Line ?: continue
            if (cr > 0 || (!direct && dt >= 0)) {
                val x = Intersect.lineLine(la.start, la.end - la.start, lb.start, lb.end - lb.start)!!
                off[k] = Line(la.start, x)
                off[k + 1] = Line(x, lb.end)
            } else if (direct) {
                extra[k] += Arc(la.end, lb.start, comp[k].end, cw = true)
            } else {
                val p1 = la.end + ta * r
                val p2 = lb.start - tb * r
                extra[k] += listOf(Line(la.end, p1), Line(p1, p2), Line(p2, lb.start))
            }
        }
        return off.indices.map { listOf(off[it]) + extra[it] }
    }

    /** 진행 왼쪽으로 r 민 요소. 원호는 같은 중심, 반지름 R − r(반시계) · R + r(시계) */
    private fun offset(s: Seg, r: Double): Seg = when (s) {
        is Line -> {
            val n = (s.end - s.start).unit().left() * r
            Line(s.start + n, s.end + n)
        }
        is Arc -> {
            val k = (s.radius + if (s.cw) r else -r) / s.radius
            Arc(s.center + (s.start - s.center) * k, s.center + (s.end - s.center) * k, s.center, s.cw)
        }
    }

    // ------------------------------------------------------------------ NC

    private fun program(comment: String, h: Head, plan: Plan, r: Double, xDia: Boolean, direct: Boolean): PolarProgram {
        val b = Builder(xDia)
        b.text(comment)
        b.text("G98")
        b.lathe(h.start)
        b.zMove("G00", h.zUp, null)
        b.zMove("G01", h.depth, h.feed)
        b.g12(h.feed)
        for (cut in plan.cuts) {
            for (s in cut.pre) b.seg("G01", s, null)
            val paths = centerPaths(cut.comp, r, direct)
            val first = paths.first().first().start
            val last = paths.last().last().end
            if (direct) {
                b.move("G01", first, listOf(Line(cut.o, first)))
                for (segs in paths) for (s in segs) b.seg("G01", s, null)
                b.move("G01", cut.o3, listOf(Line(last, cut.o3)))
            } else {
                b.move("G41 G01", cut.comp.first().start, listOf(Line(cut.o, first)))
                for ((k, s) in cut.comp.withIndex()) b.seg("G01", s, if (s is Arc) null else paths[k], r)
                b.move("G40 G01", cut.o3, listOf(Line(last, cut.o3)))
            }
        }
        b.g13()
        b.zMove("G00", h.zUp, null)
        return PolarProgram(b.blocks)
    }

    /**
     * 줄 모으기. 앞 줄과 같은 X · C 글은 빼고(모달), 움직임 없는 줄은 버린다(길은 앞 줄에 붙임). 줄마다 G 를 쓴다(원본 07.jpg 처럼).
     * 90° 넘는 원호는 같은 각으로 나눠 줄마다 90° 이하(NcLines 와 같은 까닭 — R 지령 원호의 중심 오차).
     */
    private class Builder(private val xDia: Boolean) {
        val blocks = mutableListOf<PolarBlock>()
        private var xw: String? = null
        private var cw: String? = null
        private var x: Double? = null
        private var c: Double? = null
        private var z: Double? = null
        private var polar = false
        private var feed: Double? = null

        fun text(t: String) {
            blocks += PolarBlock(t, emptyList(), x, c, z, polar)
        }

        /** G12.1 앞: X(직경) · C0 */
        fun lathe(p: Pt) {
            x = 2 * p.x
            c = 0.0
            text("G00 X${Fmt.nc(2 * p.x)} C0.")
        }

        fun zMove(g: String, zz: Double, f: Double?) {
            z = zz
            text("$g Z${Fmt.nc(zz)}" + (f?.let { " F${Fmt.nc(it)}" } ?: ""))
        }

        /** G12.1 — 모드 안 첫 이동 줄은 X · C 를 다 쓰고 F 를 붙인다 */
        fun g12(f: Double) {
            polar = true
            xw = null
            cw = null
            feed = f
            text("G12.1")
        }

        fun g13() {
            polar = false
            xw = null
            cw = null
            text("G13.1")
        }

        fun move(g: String, p: Pt, path: List<Seg>) = put(g, p, "", path)

        /** 도면 요소 한 개. 원호는 조각마다 줄, 직선은 [linePath](기계 흉내의 보정 경로 — null 이면 쓴 그대로) */
        fun seg(g: String, s: Seg, linePath: List<Seg>?, r: Double = 0.0) {
            when (s) {
                is Line -> put(g, s.end, "", linePath ?: listOf(s))
                is Arc -> for (piece in pieces(s)) {
                    put(if (s.cw) "G02" else "G03", piece.end, " R" + Fmt.nc(s.radius), listOf(if (r > 0) offset(piece, r) else piece))
                }
            }
        }

        private fun put(g: String, p: Pt, tail: String, path: List<Seg>) {
            val xv = if (xDia) 2 * p.x else p.x
            val nx = Fmt.nc(xv)
            val nc = Fmt.nc(-p.z)
            val words = (if (nx != xw) " X$nx" else "") + (if (nc != cw) " C$nc" else "")
            xw = nx
            cw = nc
            x = xv
            c = -p.z
            if (words.isEmpty()) {
                // 0.001 로 같은 자리 — 줄은 버리고 길만 앞 줄에 붙인다
                val prev = blocks.removeAt(blocks.lastIndex)
                blocks += prev.copy(path = prev.path + path)
                return
            }
            val fw = feed?.let { " F${Fmt.nc(it)}" } ?: ""
            feed = null
            blocks += PolarBlock(g + words + tail + fw, path, x, c, z, polar)
        }
    }

    /** 90° 이하 조각으로 나눈 원호(NcLines.arc 와 같은 나눔) */
    private fun pieces(a: Arc): List<Arc> {
        val k = max(1, ceil(a.sweepDeg / 90 - 1e-3).toInt())
        if (k == 1) return listOf(a)
        val a0 = angleOf(a.start - a.center)
        val step = (if (a.cw) -1 else 1) * a.sweepRad() / k
        val pts = (0..k).map { q ->
            when (q) {
                0 -> a.start
                k -> a.end
                else -> a.center + Pt(cos(a0 + step * q), sin(a0 + step * q)) * a.radius
            }
        }
        return pts.zipWithNext { p, q -> Arc(p, q, a.center, a.cw) }
    }

    // ------------------------------------------------------------------ 기하 도우미

    /** X 에서 C 쪽으로 잰 각 φ 의 단위 벡터(Pt(z = −C, x = X)) */
    private fun dirXC(phi: Double) = Pt(-sin(phi), cos(phi))

    /** X 에서 C 쪽으로 잰 각 */
    private fun angXC(p: Pt) = atan2(-p.z, p.x)

    private fun reverse(segs: List<Seg>): List<Seg> = segs.asReversed().map {
        when (it) {
            is Line -> Line(it.end, it.start)
            is Arc -> Arc(it.end, it.start, it.center, !it.cw)
        }
    }

    private fun tanStart(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> arcTangent(s, s.start)
    }

    private fun tanEnd(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> arcTangent(s, s.end)
    }

    private fun arcTangent(a: Arc, p: Pt): Pt {
        val v = (p - a.center).unit().left()
        return if (a.cw) v * -1.0 else v
    }

    private fun segDist(p: Pt, s: Pt, e: Pt): Double {
        val v = e - s
        val t = (((p - s) dot v) / (v dot v)).coerceIn(0.0, 1.0)
        return (p - (s + v * t)).length
    }
}
