package com.bmt.nosecalc.calc

import kotlin.math.abs

/** 이론 꼭짓점에 모서리 R 을 접하게 끼우기 — 라운드·테이퍼가 같이 쓴다 */
internal object Corners {
    /**
     * [segs] = 모서리 R 을 끼운 윤곽. [prev] 의 k번 = k번 모서리 바로 앞 요소의 번호,
     * [arc] 의 k번 = 그 모서리에 끼운 원호의 번호(= prev + 1). R 0 이거나 접해서 이어지는 곳이면 null.
     */
    class Result(val segs: List<Seg>, val prev: List<Int>, val arc: List<Int?>)

    /**
     * segs 의 이음점마다 모서리 R(0 = 그대로)을 끼운다. corners[k] = segs[k] 와 segs[k+1] 사이.
     * 앞 모서리가 줄인 요소에 다음 모서리를 끼우므로 두 R 의 접점이 한 요소 위에서 겹치면 알아챈다.
     * 어느 모서리 R 이 이웃 요소를 다 먹거나 닿을 자리가 없으면 null. 두 요소가 접해서 이어지면(꼭짓점이 없으면) 그 R 은 무시한다.
     */
    fun insert(segs: List<Seg>, corners: List<Double>): Result? {
        val out = mutableListOf<Seg>()
        val prev = mutableListOf<Int>()
        val arcs = mutableListOf<Int?>()
        var cur = segs[0]
        for ((k, rc) in corners.withIndex()) {
            val next = segs[k + 1]
            if (rc <= 0 || smooth(cur, next)) {
                prev += out.size
                arcs += null
                out += cur
                cur = next
            } else {
                val (a, arc, b) = fillet(cur, next, rc) ?: return null
                prev += out.size
                out += a
                arcs += out.size
                out += arc
                cur = b
            }
        }
        out += cur
        return Result(out, prev, arcs)
    }

    /** a 끝에서 b 로 접해서(같은 방향으로) 이어지나 */
    private fun smooth(a: Seg, b: Seg): Boolean {
        val ta = tangentAt(a, a.end)
        val tb = tangentAt(b, a.end)
        return abs(ta cross tb) <= 1e-12 && (ta dot tb) > 0
    }

    /**
     * a 끝(= b 시작) 꼭짓점에 반지름 rc 원호를 접하게 끼운다. 반환: (줄인 a, 모서리 원호, 줄인 b). 안 들어가면 null.
     * 모서리 원 중심은 꺾이는 쪽에서 직선과 rc, 원과는 R − rc(그 원 안쪽) 또는 R + rc(바깥) 떨어진 점 —
     * 직선 간·직선-원·원-원 모두 그 두 자취의 교점이다. 접점은 직선 위 수선의 발, 원 위에서는 중심 방향.
     */
    private fun fillet(a: Seg, b: Seg, rc: Double): Triple<Seg, Arc, Seg>? {
        val p = a.end
        val ta = tangentAt(a, p)
        val tb = tangentAt(b, p)
        val left = (ta cross tb) > 0
        val cands = when {
            a is Line && b is Line -> listOfNotNull(Intersect.lineLine(offsetOf(a, left, rc), ta, offsetOf(b, left, rc), tb))
            a is Line && b is Arc -> lociCircle(b, left, rc)?.let { Intersect.lineCircle(offsetOf(a, left, rc), ta, b.center, it) }
            a is Arc && b is Line -> lociCircle(a, left, rc)?.let { Intersect.lineCircle(offsetOf(b, left, rc), tb, a.center, it) }
            else -> {
                a as Arc
                b as Arc
                val ra = lociCircle(a, left, rc)
                val rb = lociCircle(b, left, rc)
                if (ra == null || rb == null) null else Intersect.circleCircle(a.center, ra, b.center, rb)
            }
        } ?: emptyList()
        for (f in cands.sortedBy { (it - p).length }) {
            val pa = touch(a, f)
            val pb = touch(b, f)
            val da = along(a, pa)
            val db = along(b, pb)
            if (da <= EPS || da >= lengthOf(a) || db <= 0 || db >= lengthOf(b) - EPS) continue
            val arc = Arc(pa, pb, f, cw = !left)
            // 다른 쪽 교점을 잘못 고르면 접선 방향이 뒤집힌다
            if ((tangentAt(arc, pa) - tangentAt(a, pa)).length > 1e-9 || (tangentAt(arc, pb) - tangentAt(b, pb)).length > 1e-9) continue
            return Triple(cut(a, a.start, pa), arc, cut(b, pb, b.end))
        }
        return null
    }

    /** 직선을 꺾이는 쪽으로 rc 민 직선 위의 한 점 */
    private fun offsetOf(l: Line, left: Boolean, rc: Double): Pt {
        val n = (l.end - l.start).unit().left()
        return l.start + n * (if (left) rc else -rc)
    }

    /** 모서리 원 중심의 자취(같은 중심 원)의 반지름. 진행 방향 왼쪽은 반시계 원호의 안쪽. 0 이하면 null */
    private fun lociCircle(a: Arc, left: Boolean, rc: Double): Double? {
        val r = if (left == !a.cw) a.radius - rc else a.radius + rc
        return if (r > EPS) r else null
    }

    private fun tangentAt(s: Seg, p: Pt): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> (p - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
    }

    private fun touch(s: Seg, f: Pt): Pt = when (s) {
        is Line -> {
            val d = (s.end - s.start).unit()
            s.start + d * ((f - s.start) dot d)
        }
        is Arc -> s.center + (f - s.center).unit() * s.radius
    }

    /** 시작점에서 p 까지 진행 방향으로 간 길이 */
    private fun along(s: Seg, p: Pt): Double = when (s) {
        is Line -> (p - s.start) dot (s.end - s.start).unit()
        is Arc -> {
            val t = angleOf(p - s.center) - angleOf(s.start - s.center)
            wrap2Pi(if (s.cw) -t else t) * s.radius
        }
    }

    private fun lengthOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> s.sweepRad() * s.radius
    }

    private fun cut(s: Seg, from: Pt, to: Pt): Seg = when (s) {
        is Line -> Line(from, to)
        is Arc -> Arc(from, to, s.center, s.cw)
    }
}
