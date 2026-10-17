package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** 같은 점으로 보는 거리(mm). 입력 최소 단위 0.001 보다 한참 작다 */
internal const val EPS = 1e-9

/**
 * 받는 좌표·치수의 크기 한도(mm, 절댓값). 넘으면 계산기가 BAD_INPUT 으로 멈춘다 — 잘못 친 큰 수(12자리)에서
 * 부동소수 오차가 [EPS] 를 넘어 NaN·끊긴 윤곽이 나와 앱이 죽지 않게. 10 m 는 선반 공작물보다 한참 크다
 */
const val MAX_MM = 10_000.0

/** 하나라도 [MAX_MM] 를 넘으면 true(null 은 건너뜀) */
internal fun tooBig(vararg v: Double?) = v.any { it != null && abs(it) > MAX_MM }

/**
 * 선반 평면의 점 또는 벡터. 그림은 Z 오른쪽 +, X 위 + (후방 공구대 기준).
 * x 는 반경값이다 — 직경값과는 [xDia]·[ofDia] 로만 바꾼다.
 */
data class Pt(val z: Double, val x: Double) {
    operator fun plus(o: Pt) = Pt(z + o.z, x + o.x)
    operator fun minus(o: Pt) = Pt(z - o.z, x - o.x)
    operator fun times(k: Double) = Pt(z * k, x * k)
    infix fun dot(o: Pt) = z * o.z + x * o.x

    /** 양수면 o 가 이 벡터에서 반시계 쪽 */
    infix fun cross(o: Pt) = z * o.x - x * o.z
    val length: Double get() = sqrt(z * z + x * x)

    /** 직경값 X (화면·G코드 표기) */
    val xDia: Double get() = 2 * x

    internal fun unit(): Pt = this * (1 / length)

    /** 반시계로 90° 돌린 벡터 = 진행 방향의 왼쪽 */
    internal fun left(): Pt = Pt(-x, z)

    companion object {
        /** 직경값 X 로 만든다. 인자 순서는 생성자와 같은 (z, x) */
        fun ofDia(z: Double, xDia: Double) = Pt(z, xDia / 2)
    }
}

sealed interface Seg {
    val start: Pt
    val end: Pt
}

data class Line(override val start: Pt, override val end: Pt) : Seg

/**
 * 원호. cw = true 가 G02(시계), false 가 G03(반시계) — 이 앱 그림(Z 오른쪽, X 위)에서 보이는 그대로.
 *
 * 근거: FANUC 은 G18(ZX 평면)의 시계/반시계를 오른손 좌표계의 +Y 에서 −Y 쪽으로 내려다보고 정한다.
 * Y = Z × X 이므로 Z 오른쪽·X 위로 그린 평면에서 +Y 는 보는 사람 쪽 — 곧 이 그림이 +Y 에서 본 모습이다.
 * 실제 프로그램으로도 맞춰 봤다: Haas ST/TL 프로그래밍 워크북 43~44쪽 예제
 * (https://haascnc.com/content/dam/haascnc/videos/bonus-content/g71-essential-tips-tod/lathe---programming-workbook.pdf)
 * `X1.5 Z0` → `G03 X2. Z-0.25 R0.25` 는 이 그림에서 반시계 1/4 원, `G02 X5. Z-2.5 I1.5 K0.` 는 시계 1/4 원이다.
 * 전방 공구대 기계는 그림만 X 가 아래로 뒤집힐 뿐 프로그램은 같다.
 *
 * 전체 원(start = end)은 다루지 않는다 — 길이 0 으로 본다.
 */
data class Arc(override val start: Pt, override val end: Pt, val center: Pt, val cw: Boolean) : Seg {
    val radius: Double get() = (start - center).length

    /** 진행 방향으로 돈 각(도), 0 이상 360 미만 */
    val sweepDeg: Double get() = sweepRad() * 180 / PI

    internal fun sweepRad(): Double {
        if ((end - start).length <= EPS) return 0.0
        val a = angleOf(end - center) - angleOf(start - center)
        return wrap2Pi(if (cw) -a else a)
    }
}

/** 이어진 요소들. 앞 요소의 끝점 = 다음 요소의 시작점 */
data class Contour(val segs: List<Seg>) {
    init {
        for ((i, pair) in segs.zipWithNext().withIndex()) {
            require((pair.first.end - pair.second.start).length <= 1e-6) {
                "윤곽이 끊겼다: ${i}번 끝 ${pair.first.end} ≠ ${i + 1}번 시작 ${pair.second.start}"
            }
        }
    }

    companion object {
        fun polyline(vararg pts: Pt) = Contour(pts.toList().zipWithNext { a, b -> Line(a, b) })
    }
}

/** 교점. 직선은 지나는 점 p 와 방향 d(길이 상관없음)로 준다 — 선분이 아니라 무한 직선이다 */
object Intersect {
    /** 평행이면 null */
    fun lineLine(p1: Pt, d1: Pt, p2: Pt, d2: Pt): Pt? {
        val den = d1 cross d2
        if (abs(den) <= 1e-12 * d1.length * d2.length) return null
        return p1 + d1 * (((p2 - p1) cross d2) / den)
    }

    /** 0~2개. 접하면 1개 */
    fun lineCircle(p: Pt, d: Pt, c: Pt, r: Double): List<Pt> {
        val u = d.unit()
        val foot = p + u * ((c - p) dot u)
        val h = (c - foot).length
        if (h > r + EPS) return emptyList()
        if (h >= r - EPS) return listOf(foot)
        val s = sqrt(r * r - h * h)
        return listOf(foot - u * s, foot + u * s)
    }

    /** 0~2개. 접하면 1개, 같은 중심이면 0개 */
    fun circleCircle(c1: Pt, r1: Double, c2: Pt, r2: Double): List<Pt> {
        val v = c2 - c1
        val d = v.length
        if (d <= EPS || d > r1 + r2 + EPS || d < abs(r1 - r2) - EPS) return emptyList()
        val e = v * (1 / d)
        val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
        val base = c1 + e * a
        val h2 = r1 * r1 - a * a
        if (h2 <= 0) return listOf(base)
        val h = e.left() * sqrt(h2)
        return listOf(base + h, base - h)
    }
}

/** +Z 에서 반시계로 잰 각(라디안) */
internal fun angleOf(v: Pt) = atan2(v.x, v.z)

/** [0, 2π) */
internal fun wrap2Pi(a: Double): Double {
    val m = a % (2 * PI)
    return if (m < 0) m + 2 * PI else m
}

/** (−π, π] */
internal fun wrapPi(a: Double): Double {
    val m = wrap2Pi(a)
    return if (m > PI) m - 2 * PI else m
}
