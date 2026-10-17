package com.bmt.nosecalc.calc

import kotlin.math.ceil

/**
 * 홈 바이트의 프로그램 기준 인선. [tip] = 공구 보정 화면에 넣을 가상 인선 번호.
 * 폭 T 인 홈 바이트는 두 모서리에 작은 노즈R 이 있고, 기준 모서리의 가상 인선이 왼쪽이면 3, 오른쪽이면 4 다.
 * 팁 8(아래 가운데)은 노즈 중심 바로 아래라, 폭 = 2r 인 풀R 인서트일 때만 날 가운데와 맞는다.
 * 출처: Haas ST/TL 프로그래밍 워크북 76쪽 — 홈 바이트 오른쪽 모서리로 깎는 예에 "Tip Direction 4 ...
 * since the corner that is cutting is pointing down in the X- Z+ direction" (주소는 [Arc] 주석).
 */
enum class GrooveRef(val tip: Int) {
    /** 왼쪽(척 쪽) 인선 — 소재 끝면에 Z 를 맞추면 닿는 쪽 */
    LEFT(3),

    /** 오른쪽 인선 */
    RIGHT(4),
}

/** 홈 경고. 모두 계산을 멈춘다([GrooveResult.solution] = null). 침범 경고는 한꺼번에 다 알려 준다 */
enum class GrooveWarning {
    /** 홈 시작 Z·홈 폭·외경·바닥 지름·인서트 폭 가운데 빈 것이 있다 */
    MISSING_INPUT,

    /** 폭 ≤ 0, 바닥 지름·겹침·X 여유 < 0, 숫자가 아님, 값이 ±[MAX_MM] 밖 */
    BAD_INPUT,

    /** 인서트 폭 T > 홈 폭 W — 홈이 도면보다 넓어진다 */
    TOOL_WIDER_THAN_GROOVE,

    /** 바닥 지름 ≥ 외경 */
    BOTTOM_NOT_BELOW_OD,

    /** 겹침 ≥ 인서트 폭 — 옆으로 나아가지 못한다 */
    OVERLAP_TOO_BIG,

    /** 단이 [GrooveCalc.MAX_STEPS] 를 넘는다 — 겹침·폭 입력 실수로 본다 */
    TOO_MANY_STEPS,
}

/** Outer Groove. 홈은 오른쪽 벽 [zStart] 에서 왼쪽으로 [width]. X 는 직경값 */
data class GrooveInput(
    /** 홈 시작 Z = 오른쪽 벽 */
    val zStart: Double? = null,
    /** 홈 폭 W */
    val width: Double? = null,
    /** 외경 D */
    val od: Double? = null,
    /** 바닥 지름 d */
    val bottom: Double? = null,
    /** 인서트 폭 T */
    val insert: Double? = null,
    /** 단 겹침. 피치 = T − 겹침 */
    val overlap: Double = 0.5,
    val ref: GrooveRef = GrooveRef.LEFT,
    /** 외경 위 여유(반경). 빠지는 X = D + 2 × 여유 */
    val xClear: Double = 1.0,
)

/** 한 단: 진입 Z(기준 인선의 프로그램 좌표)와 목표 X(직경) */
data class GrooveStep(val z: Double, val xDia: Double)

data class GrooveSolution(
    /** 오른쪽 벽부터. 첫 단은 날 오른쪽이 오른쪽 벽에, 마지막 단은 날 왼쪽이 왼쪽 벽에 꼭 맞는다 */
    val steps: List<GrooveStep>,
    /** 단 사이 거리(마지막 간격만 짧을 수 있다) */
    val pitch: Double,
    /** 단마다 빠지는 X(직경) */
    val retractXDia: Double,
    /** 단마다 G00 → G01 바닥 → G00 빠짐, 끝에 벽·바닥 정삭 */
    val nc: List<String>,
    /** 홈 도면선: 오른쪽 벽 위(외경) → 바닥 → 왼쪽 벽 위 */
    val drawing: Contour,
    /** 벽·바닥 정삭 경로(기준 인선의 프로그램 점): 오른쪽 벽 위 → 바닥 → 왼쪽 벽 → 위 */
    val finish: Contour,
)

data class GrooveResult(val solution: GrooveSolution?, val warnings: List<GrooveWarning>)

/**
 * 홈 가공(다단 진입 + 정삭). 벽·바닥이 모두 Z·X 축에 평행해 가상 인선이 도면선 위에 그대로 놓이므로
 * 노즈R 보정량은 0 이다 — 기준 인선만 맞으면 된다. 바닥 두 모서리에는 인서트 노즈R 만큼 R 이 남는다.
 * 단 나누기는 G75 의 K 처럼 피치로 나아가고 마지막만 벽에 맞춘다(Haas 워크북 106쪽 "the last interval along Z
 * will be less than K").
 */
object GrooveCalc {
    const val MAX_STEPS = 1000

    fun calc(i: GrooveInput): GrooveResult {
        val need = listOf(i.zStart, i.width, i.od, i.bottom, i.insert)
        if (need.any { it == null }) return stop(GrooveWarning.MISSING_INPUT)
        val z0 = i.zStart!!
        val w = i.width!!
        val od = i.od!!
        val d = i.bottom!!
        val t = i.insert!!
        val finite = (need + i.overlap + i.xClear).all { it!!.isFinite() }
        if (!finite || w <= 0 || t <= 0 || d < 0 || i.overlap < 0 || i.xClear < 0 || tooBig(*(need + i.overlap + i.xClear).toTypedArray())) {
            return stop(GrooveWarning.BAD_INPUT)
        }
        val hits = buildList {
            if (t > w + EPS) add(GrooveWarning.TOOL_WIDER_THAN_GROOVE)
            if (d >= od) add(GrooveWarning.BOTTOM_NOT_BELOW_OD)
            if (i.overlap >= t) add(GrooveWarning.OVERLAP_TOO_BIG)
        }
        if (hits.isNotEmpty()) return GrooveResult(null, hits)

        val pitch = t - i.overlap
        // 나눠떨어질 때 부동소수 찌꺼기로 단이 하나 더 생기지 않게 조금 뺀다
        // 단 수는 Double 로 견준 뒤 Int 로 — 겹침이 인서트 폭에 아주 가까우면 Int 를 넘쳐 음수가 된다
        val steps0 = 1 + ceil((w - t) / pitch - 1e-9).coerceAtLeast(0.0)
        if (steps0 > MAX_STEPS) return stop(GrooveWarning.TOO_MANY_STEPS)
        val n = steps0.toInt()
        val off = if (i.ref == GrooveRef.LEFT) 0.0 else t // 날 왼쪽 끝 → 기준 인선
        val steps = (0 until n).map { k ->
            val bladeLeft = if (k == n - 1) z0 - w else z0 - t - k * pitch
            GrooveStep(bladeLeft + off, d)
        }
        val xr = od + 2 * i.xClear
        val first = steps.first().z
        val last = steps.last().z
        val finish = Contour.polyline(Pt.ofDia(first, xr), Pt.ofDia(first, d), Pt.ofDia(last, d), Pt.ofDia(last, xr))
        val nc = NcLines().apply {
            for (s in steps) {
                move("G00", Pt.ofDia(s.z, xr))
                move("G01", Pt.ofDia(s.z, s.xDia))
                move("G00", Pt.ofDia(s.z, xr))
            }
            val f = listOf(finish.segs.first().start) + finish.segs.map { it.end }
            move("G00", f[0])
            f.drop(1).forEach { move("G01", it) }
        }.lines
        val drawing = Contour.polyline(Pt.ofDia(z0, od), Pt.ofDia(z0, d), Pt.ofDia(z0 - w, d), Pt.ofDia(z0 - w, od))
        return GrooveResult(GrooveSolution(steps, pitch, xr, nc, drawing, finish), emptyList())
    }

    private fun stop(w: GrooveWarning) = GrooveResult(null, listOf(w))
}
