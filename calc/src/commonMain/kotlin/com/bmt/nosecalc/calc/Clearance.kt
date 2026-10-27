package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 보링바 안전 여유의 직선(유클리드) 거리(사용자 결정 2026-10-28 "안전 여유는 직선 거리로 잰다").
 * 볼록 조각(인서트 조각 · 바 · 최소 가공경 구간, 움직임이면 그 볼록 껍질)과 소재 경계 토막(직선 · 원호) 사이 가장 짧은 거리를 정확히 구한다:
 * 겹치지 않는 볼록 다각형과 소재 사이 거리는 둘레끼리의 거리라, 변 × 토막마다 정확한 최솟값(만나면 0)의 최솟값이다.
 * 변 × 토막의 후보: 변의 두 끝에서 토막까지, 토막의 두 끝에서 변까지, 원호는 중심에서 변의 법선 방향 ± R 점(원호 위면)에서 변까지 —
 * 선분과 원호 사이 거리의 극점은 이 셋 가운데 하나다(원호 안쪽 점에서 변에 내린 수선이 원호의 반지름 방향과 겹치는 곳이 마지막 것).
 * 겹치는지(다각형이 소재 안으로 들어갔는지)는 반경 여유가 정한다([clearOf]) — 다각형이 통째로 소재 안에 들면 둘레끼리는 떨어져 있다
 */

/** 소재 경계 한 토막. [opposite] = 축 너머 거울(맞은편 벽). 상자는 거리 가지치기용 */
internal class Wall(val seg: Seg, val opposite: Boolean) {
    val z0: Double
    val z1: Double
    val x0: Double
    val x1: Double

    init {
        val pts = mutableListOf(seg.start, seg.end)
        if (seg is Arc) {
            for (t in listOf(0.0, PI / 2, PI, 1.5 * PI)) {
                val p = seg.center + Pt(cos(t), sin(t)) * seg.radius
                if (onArc(seg, p)) pts += p
            }
        }
        z0 = pts.minOf { it.z }
        z1 = pts.maxOf { it.z }
        x0 = pts.minOf { it.x }
        x1 = pts.maxOf { it.x }
    }
}

/** 점 p 에서 요소까지 거리 */
internal fun distTo(s: Seg, p: Pt): Double = when (s) {
    is Line -> {
        val d = s.end - s.start
        val l2 = d dot d
        val t = if (l2 <= 0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0)
        (p - (s.start + d * t)).length
    }
    is Arc -> {
        val v = p - s.center
        if (v.length > EPS && onArc(s, p)) abs(v.length - s.radius) else min((p - s.start).length, (p - s.end).length)
    }
}

/**
 * 점 p 가 원호 위(중심에서 본 방향이 원호 각 안)인가 — 외적으로(각 함수 없이, 거리 고리에서 자주 부른다). 진행 방향을 반시계로 맞춘 시작 · 끝 방향
 * s · e 와 v = p − 중심: 원호 각 ≤ 180° 면 s 에서 v, v 에서 e 가 둘 다 반시계(또는 같은 방향), 넘으면 나머지 쐐기(< 180°) 안이 아님.
 * 각 1e-12 rad 쯤의 여유
 */
internal fun onArc(a: Arc, p: Pt): Boolean {
    val sg = if (a.cw) -1.0 else 1.0
    val s = a.start - a.center
    val e = a.end - a.center
    val v = p - a.center
    val vl = v.length
    if (vl <= 0) return true
    val rr = s.length
    val tol = -1e-12 * rr * vl
    if ((a.end - a.start).length <= EPS) return (s cross v) * sg >= tol && (s dot v) > 0
    val cse = (s cross e) * sg
    val csv = (s cross v) * sg
    val cve = (v cross e) * sg
    return if (cse > 0 || (cse == 0.0 && (s dot e) < 0 && csv >= 0)) {
        // 원호 각 ≤ 180°(꼭 180° 는 s 왼쪽 반평면)
        if (cse == 0.0) csv >= tol else csv >= tol && cve >= tol
    } else {
        // 원호 각 > 180°: 나머지 쐐기 안(e 에서 v, v 에서 s 가 둘 다 반시계)이 아니면 위
        !(csv < -tol && cve < -tol)
    }
}

/** 요소 위에서 점 p 와 가장 가까운 점 */
internal fun nearestOn(s: Seg, p: Pt): Pt = when (s) {
    is Line -> {
        val d = s.end - s.start
        val l2 = d dot d
        s.start + d * (if (l2 <= 0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0))
    }
    is Arc -> {
        val v = p - s.center
        if (v.length > EPS && onArc(s, p)) s.center + v.unit() * s.radius else if ((p - s.start).length <= (p - s.end).length) s.start else s.end
    }
}

/** 선분 [e] 와 요소 [w] 의 교점(없으면 null) */
internal fun crossing(e: Line, w: Seg): Pt? {
    val d = e.end - e.start
    val dd = d dot d
    if (dd <= 0) return null
    fun onE(p: Pt) = ((p - e.start) dot d) / dd in 0.0..1.0
    return when (w) {
        is Line -> {
            val f = w.end - w.start
            val den = d cross f
            if (abs(den) <= 1e-18) return null
            val t = ((w.start - e.start) cross f) / den
            val u = ((w.start - e.start) cross d) / den
            if (t in 0.0..1.0 && u in 0.0..1.0) e.start + d * t else null
        }
        is Arc -> Intersect.lineCircle(e.start, d, w.center, w.radius).firstOrNull { onE(it) && onArc(w, it) }
    }
}

/** 선분 [e] 와 요소 [w] 사이 가장 짧은 거리와 [w] 쪽 그 점(만나면 0) */
internal fun segGap(e: Line, w: Seg): Pair<Double, Pt> {
    crossing(e, w)?.let { return 0.0 to it }
    var best = Double.POSITIVE_INFINITY
    var at = w.start
    fun cand(d: Double, p: Pt) {
        if (d < best) {
            best = d
            at = p
        }
    }
    for (p in listOf(e.start, e.end)) nearestOn(w, p).let { cand((p - it).length, it) }
    for (q in listOf(w.start, w.end)) cand(distTo(e, q), q)
    // 원호 안쪽 점: 이은 선이 변에 수직이고 원호의 반지름 방향인 곳 = 중심 ± R·(변의 법선)
    if (w is Arc && (e.end - e.start).length > 0) {
        val n = (e.end - e.start).unit().left()
        for (s in listOf(1.0, -1.0)) {
            val q = w.center + n * (w.radius * s)
            if (onArc(w, q)) cand(distTo(e, q), q)
        }
    }
    return best to at
}

/**
 * 볼록 다각형 [poly](꼭짓점 차례)와 [walls] 가운데 [opposite] 쪽이고 [skip] 깃발이 없는 토막들 사이 직선 거리와 소재 쪽 그 점.
 * 맞닿거나 만나면 0. 토막이 없으면 +∞. 상자 사이 거리로 먼 토막은 건너뛴다(거리의 아래 한계라 결과는 같다)
 */
internal fun clearance(poly: List<Pt>, walls: List<Wall>, opposite: Boolean, skip: BooleanArray?): Gap {
    if (poly.isEmpty()) return Gap.CLEAR
    val pz0 = poly.minOf { it.z }
    val pz1 = poly.maxOf { it.z }
    val px0 = poly.minOf { it.x }
    val px1 = poly.maxOf { it.x }
    val edges = if (poly.size == 1) listOf(Line(poly[0], poly[0])) else poly.indices.map { Line(poly[it], poly[(it + 1) % poly.size]) }
    var best = Double.POSITIVE_INFINITY
    var at = Pt(0.0, 0.0)
    // 상자 사이 거리(아래 한계) 순으로 보고, 그것이 지금 최솟값 이상이면 멈춘다
    val order = walls.indices.filter { walls[it].opposite == opposite && (skip == null || !skip[it]) }.map { i ->
        val w = walls[i]
        val dz = max(0.0, max(w.z0 - pz1, pz0 - w.z1))
        val dx = max(0.0, max(w.x0 - px1, px0 - w.x1))
        i to sqrt(dz * dz + dx * dx)
    }.sortedBy { it.second }
    for ((i, low) in order) {
        if (low >= best) break
        val w = walls[i]
        for (e in edges) {
            val (d, p) = if ((e.end - e.start).length <= 0) distTo(w.seg, e.start) to nearestOn(w.seg, e.start) else segGap(e, w.seg)
            if (d < best) {
                best = d
                at = p
            }
        }
    }
    return if (best.isFinite()) Gap(best, at.z, at.x) else Gap.CLEAR
}
