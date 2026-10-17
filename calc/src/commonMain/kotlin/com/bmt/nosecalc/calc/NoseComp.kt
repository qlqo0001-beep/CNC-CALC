package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 진행 방향을 보고 공구가 윤곽의 왼쪽(G41)에 있나 오른쪽(G42)에 있나. 이 앱 그림(Z 오른쪽, X 위)에서 본 좌우.
 */
enum class Side {
    G41, G42;

    companion object {
        /**
         * 외경을 척 쪽(−Z)으로 깎으면 G42, 내경은 G41. 역방향(+Z)이면 진행이 뒤집혀 반대.
         * 출처: Haas ST/TL 프로그래밍 워크북 59쪽 "normal O.D. turning ... toward the chuck, will require a G42 ...
         * normal I.D. turning will require a G41" (주소는 [Arc] 주석).
         * 단면을 중심 쪽으로 깎는 것처럼 이 표에 없는 경우는 부르는 쪽이 정한다.
         */
        fun of(inner: Boolean, reverse: Boolean): Side = if (inner != reverse) G41 else G42
    }
}

/** 보정 경고. index = 원래 윤곽의 요소 번호 */
sealed interface CompWarning {
    val index: Int

    /** 오목 원호 R 이 노즈R 보다 작다. 공구가 못 들어가 그 자리는 R 대신 r 로 남는다 */
    data class ArcTooSmall(override val index: Int, val arcR: Double, val noseR: Double) : CompWarning

    /**
     * 공구가 이 요소의 어느 점에도 닿지 못한다 — 그 점에서 공구 쪽으로 접한 반지름 r 원이 늘 다른 요소(또는 윤곽 양 끝의
     * 연장선)와 겹친다. 노즈R 에 비해 짧거나 좁은 요소다. 그 요소는 깎이지 않고 앞뒤의 닿는 요소끼리 잇는다.
     */
    data class Unreachable(override val index: Int) : CompWarning
}

/**
 * 보정 결과. path.segs 는 원래 윤곽과 개수·순서가 같다(i번 = 원래 i번).
 * 경고가 난 요소 자리는 길이 0 의 [Line] 이다. 다만 앞뒤를 잇는 데 공구가 볼록 꼭짓점을 돌아 넘어가야 하면
 * 그 자리에 꼭짓점 둘레 반지름 r 의 [Arc] 가 들어간다.
 */
data class CompPath(val path: Contour, val warnings: List<CompWarning>)

/**
 * 가상 인선 노즈R 보정. 일반 해법:
 * 1. 윤곽의 각 요소를 공구 쪽으로 r 만큼 민다(원호는 같은 중심, 볼록 R + r · 오목 R − r).
 * 2. 민 요소마다 노즈 원이 재료(윤곽 + 양 끝을 접선 방향으로 늘인 반직선)와 겹치지 않는 구간만 남긴다.
 *    남는 곳이 없으면 [CompWarning.Unreachable].
 * 3. 남은 구간끼리 잇는다: 오목 모서리는 두 구간이 교점에서 만나고, 볼록 모서리는 두 구간을 늘인 교점(날카로운 모서리).
 *    빠진 요소를 사이에 두고 공구가 볼록 꼭짓점을 돌아야 하면 그 꼭짓점 둘레 원호를 빠진 요소 자리에 넣는다.
 *    → 노즈 중심 경로.
 * 4. 모든 점에 [tipVector] 를 더한다 → 가상 인선 경로 = G41/G42 없이 쓰는 프로그램 좌표.
 */
object NoseComp {
    /** 노즈 원이 재료와 이만큼(mm)까지 겹쳐도 닿은 것으로 본다 — 교점 계산의 반올림 오차 */
    private const val TOL = 1e-7

    /** 같은 점으로 보는 거리(mm) — 두 곡선에서 따로 구한 같은 교점 */
    private const val JOIN = 1e-7

    /**
     * 노즈 중심에서 가상 인선으로 가는 벡터. x 도 반경값이다.
     * 1 = X+Z+, 2 = X+Z−, 3 = X−Z−, 4 = X−Z+, 5 = Z+, 6 = X+, 7 = Z−, 8 = X−, 0·9 = 중심.
     * 출처: Haas ST/TL 프로그래밍 워크북 59·69~70쪽 "TOOL TIP DIRECTION" 표 — 방향은
     * "a vector originating from the center of the tool radius and extending to the imaginary tool tip"
     * (https://haascnc.com/content/dam/haascnc/videos/bonus-content/g71-essential-tips-tod/lathe---programming-workbook.pdf).
     * 같은 표가 Haas Factory Outlet 선반 프로그래밍 교재 74~75쪽에도 있다
     * (https://www.productivity.com/wp-content/uploads/2022/08/Haas-Lathe-Programming-8-2022.pdf).
     */
    fun tipVector(tip: Int, r: Double): Pt = when (tip) {
        1 -> Pt(r, r)
        2 -> Pt(-r, r)
        3 -> Pt(-r, -r)
        4 -> Pt(r, -r)
        5 -> Pt(r, 0.0)
        6 -> Pt(0.0, r)
        7 -> Pt(-r, 0.0)
        8 -> Pt(0.0, -r)
        0, 9 -> Pt(0.0, 0.0)
        else -> throw IllegalArgumentException("팁 번호는 0~9: $tip")
    }

    /** 외경 일반 바이트 3, 외경 역바이트 4, 내경 보링 2, 내경 역보링 1 */
    fun standardTip(inner: Boolean, reverse: Boolean): Int = when {
        !inner && !reverse -> 3
        !inner -> 4
        !reverse -> 2
        else -> 1
    }

    /** 가상 인선 경로 = 프로그램 좌표 */
    fun tipPath(c: Contour, r: Double, side: Side, tip: Int): CompPath {
        val v = tipVector(tip, r)
        val cp = centerPath(c, r, side)
        return cp.copy(path = Contour(cp.path.segs.map { it.shifted(v) }))
    }

    /**
     * 노즈 중심 경로. 이 꼴(요소마다 한 자리, 볼록 모서리는 날카롭게)로 나타낼 수 없는 모양이면 틀린 숫자를 내지 않고
     * IllegalArgumentException 을 던진다: 닿는 요소가 없을 때, 이웃한 두 요소의 볼록 모서리에서 늘인 보정선이 만나지 않을 때
     * (예: 제자리로 되돌아가는 윤곽, 단면에서 날카롭게 오목 원호로 넘어가는 모서리 — 꼭짓점을 도는 원호가 필요하다),
     * 빠진 요소 앞뒤를 꼭짓점 둘레로 이을 수 없거나 그럴 자리가 모자랄 때, 한 요소에서 닿는 곳이 둘로 나뉠 때.
     */
    fun centerPath(c: Contour, r: Double, side: Side): CompPath {
        require(r >= 0) { "노즈R 은 0 이상: $r" }
        val segs = c.segs
        val n = segs.size
        val warnings = mutableListOf<CompWarning>()
        val pieces = arrayOfNulls<Cv>(n)
        for ((i, seg) in segs.withIndex()) {
            pieces[i] = when (seg) {
                is Line -> {
                    val v = seg.end - seg.start
                    if (v.length <= EPS) null
                    else {
                        val d = v.unit()
                        val nrm = if (side == Side.G41) d.left() else d.left() * -1.0
                        CvLine(seg.start + nrm * r, d, v.length)
                    }
                }
                is Arc -> {
                    val sweep = seg.sweepRad()
                    // 왼쪽 공구 + 시계 원호, 오른쪽 공구 + 반시계 원호 = 공구가 원호 바깥(볼록)
                    val rr = if ((side == Side.G41) == seg.cw) seg.radius + r else seg.radius - r
                    when {
                        sweep * seg.radius <= EPS -> null
                        rr < -EPS -> {
                            warnings += CompWarning.ArcTooSmall(i, seg.radius, r)
                            null
                        }
                        // R = r: 공구가 꼭 맞게 들어가 중심 경로는 원호 중심 한 점
                        rr <= EPS -> CvArc(seg.center, 0.0, 0.0, seg.cw, 0.0)
                        else -> CvArc(seg.center, rr, angleOf(seg.start - seg.center), seg.cw, sweep)
                    }
                }
            }
        }

        val mat = Material(segs, r)
        val lo = DoubleArray(n)
        val hi = DoubleArray(n)
        val start = arrayOfNulls<Pt>(n)
        val end = arrayOfNulls<Pt>(n)
        for (i in 0 until n) {
            val cv = pieces[i] ?: continue
            val range = validRange(cv, mat)
            if (range == null) {
                warnings += CompWarning.Unreachable(i)
                continue
            }
            lo[i] = range.first
            hi[i] = range.second
            start[i] = cv.at(lo[i])
            end[i] = cv.at(hi[i])
        }
        val alive = (0 until n).filter { start[it] != null }
        require(alive.isNotEmpty()) { "노즈R 보정 불가: 공구가 닿는 요소가 없다" }

        val links = arrayOfNulls<Arc>(n) // 빠진 요소 자리에 넣는 꼭짓점 둘레 원호
        for ((i, j) in alive.zipWithNext()) {
            val a = pieces[i]!!
            val b = pieces[j]!!
            val e = end[i]!!
            val s = start[j]!!
            if ((e - s).length <= JOIN) {
                start[j] = e // 오목 모서리·접해서 이어지는 곳
                continue
            }
            val p = if (a.len > 0 && b.len > 0) extensionMeet(a, b, (e + s) * 0.5) else null
            if (p != null) {
                // 교점이 두 구간 안: 오목 모서리인데 허용치(TOL) 때문에 두 구간이 교점을 조금 넘었다 — 교점에서 자른다.
                // 거의 일직선으로 꺾이면 넘는 길이가 TOL/꺾인 각 만큼 커진다
                val ta = a.param(p)
                val tb = b.param(p)
                if (ta >= lo[i] - 1e-9 && ta <= hi[i] + 1e-9 && tb >= lo[j] - 1e-9 && tb <= hi[j] + 1e-9) {
                    end[i] = p
                    start[j] = p
                    continue
                }
                // 둘 다 꼭짓점까지 깎인다 → 볼록 모서리: 늘인 교점에서 만나 날카로운 모서리로 남긴다
                if (hi[i] == a.len && lo[j] == 0.0 && extensionClear(a, e, p, true, mat) && extensionClear(b, p, s, false, mat)) {
                    end[i] = p
                    start[j] = p
                    continue
                }
            }
            require(j > i + 1) {
                "노즈R 보정 불가: ${i}번과 ${j}번 요소의 보정선을 이을 수 없다(볼록 모서리면 꼭짓점을 도는 원호가 필요하다)"
            }
            var slot = i
            for ((k, arc) in vertexChain(e, s, i + 1..j, segs, r, side == Side.G41, mat)) {
                slot = listOf(k - 1, k).firstOrNull { it > slot && it < j }
                    ?: throw IllegalArgumentException("노즈R 보정 불가: ${i}번과 ${j}번 요소 사이를 이을 자리가 모자란다")
                links[slot] = arc
                start[j] = arc.end
            }
        }

        val out = ArrayList<Seg>(n)
        var cur = start[alive.first()]!!
        for (i in 0 until n) {
            val st = start[i]
            val link = links[i]
            out += when {
                st != null -> pieces[i]!!.seg(st, end[i]!!)
                link != null -> link
                else -> Line(cur, cur)
            }
            cur = out.last().end
        }
        return CompPath(Contour(out), warnings)
    }

    /** cv 위에서 노즈 원이 재료와 겹치지 않는 구간 [처음, 끝](매개변수). 없으면 null */
    private fun validRange(cv: Cv, m: Material): Pair<Double, Double>? {
        val ts = m.splits(cv)
        val good = BooleanArray(ts.size - 1) { m.clear(cv.at((ts[it] + ts[it + 1]) / 2)) }
        val a = good.indexOfFirst { it }
        if (a < 0) {
            // 구간은 없고 한 점에서만 닿는다(예: 폭이 꼭 2r 인 홈 바닥)
            val pts = ts.filter { m.clear(cv.at(it)) }
            if (pts.isEmpty()) return null
            require((cv.at(pts.last()) - cv.at(pts.first())).length <= JOIN) { SPLIT_MSG }
            return pts.first() to pts.first()
        }
        val b = good.indexOfLast { it }
        require((a..b).all { good[it] }) { SPLIT_MSG }
        return ts[a] to ts[b + 1]
    }

    private const val SPLIT_MSG = "노즈R 보정 불가: 한 요소에서 공구가 닿는 곳이 둘로 나뉜다"

    /** cv 전체에서 노즈 원이 재료와 겹치지 않나 */
    private fun allClear(cv: Cv, m: Material): Boolean {
        val ts = m.splits(cv)
        return ts.all { m.clear(cv.at(it)) } && (0 until ts.size - 1).all { m.clear(cv.at((ts[it] + ts[it + 1]) / 2)) }
    }

    /** 두 조각을 늘인 곡선(직선·원)의 교점 가운데 ref 에 가까운 것. 같은 방향으로 한 직선 위면 ref */
    private fun extensionMeet(a: Cv, b: Cv, ref: Pt): Pt? {
        val cands = when {
            a is CvLine && b is CvLine -> {
                val p = Intersect.lineLine(a.a, a.d, b.a, b.d)
                    ?: return if ((a.d dot b.d) > 0 && abs(a.d cross (b.a - a.a)) <= EPS) ref else null
                listOf(p)
            }
            a is CvLine && b is CvArc -> Intersect.lineCircle(a.a, a.d, b.c, b.rad)
            a is CvArc && b is CvLine -> Intersect.lineCircle(b.a, b.d, a.c, a.rad)
            else -> {
                a as CvArc
                b as CvArc
                Intersect.circleCircle(a.c, a.rad, b.c, b.rad)
            }
        }
        return cands.minByOrNull { (it - ref).length }
    }

    /**
     * cv 를 끝 너머로(forward) 또는 시작 앞으로 늘인 토막 from → to 에서 노즈 원이 재료와 겹치지 않나.
     * 늘이는 것이 아니라 잘라 들어가는 쪽이면 false.
     */
    private fun extensionClear(cv: Cv, from: Pt, to: Pt, forward: Boolean, m: Material): Boolean {
        val t = cv.param(if (forward) to else from)
        val ext = if (forward) t - cv.len else -t
        if (ext < -1e-9) return false
        if (ext <= 1e-9) return true
        val bit = when (cv) {
            is CvLine -> CvLine(from, cv.d, ext)
            is CvArc -> CvArc(cv.c, cv.rad, angleOf(from - cv.c), cv.cw, ext)
        }
        return allClear(bit, m)
    }

    /**
     * e 에서 s 까지 꼭짓점 둘레 반지름 r 원을 따라 잇는다 — 공구가 빠진 요소 위의 볼록 꼭짓점을 돌아 넘어가는 길.
     * 꼭짓점 k = segs[k].start. 공구가 왼쪽(G41)이면 시계로 돈다. 돌려주는 것: (꼭짓점 번호, 원호) 차례대로.
     */
    private fun vertexChain(
        e: Pt, s: Pt, ks: IntRange, segs: List<Seg>, r: Double, cw: Boolean, m: Material,
    ): List<Pair<Int, Arc>> {
        val out = mutableListOf<Pair<Int, Arc>>()
        var x = e
        while ((x - s).length > JOIN) {
            // 구간 끝은 허용치만큼 원 안쪽에 있을 수 있다
            val k = ks.firstOrNull { k -> out.none { it.first == k } && abs((x - segs[k].start).length - r) <= 1e-6 }
                ?: throw IllegalArgumentException(CHAIN_MSG)
            val v = segs[k].start
            val circle = CvArc(v, r, angleOf(x - v), cw, 2 * PI)
            val ts = m.splits(circle)
            var t = 0.0
            for (q in 0 until ts.size - 1) {
                if (!m.clear(circle.at((ts[q] + ts[q + 1]) / 2))) break
                t = ts[q + 1]
                if ((circle.at(t) - s).length <= JOIN) break
            }
            require(t > 0 && t < 2 * PI) { CHAIN_MSG }
            val y = circle.at(t).let { if ((it - s).length <= JOIN) s else it }
            out += k to Arc(x, y, v, cw)
            x = y
        }
        return out
    }

    private const val CHAIN_MSG = "노즈R 보정 불가: 빠진 요소 앞뒤를 꼭짓점 둘레로 이을 수 없다"

    /** 노즈 중심이 지나는 곡선 토막. 매개변수 t ∈ [0, len] — 직선은 길이, 원호는 진행 방향으로 돈 각(라디안) */
    private sealed interface Cv {
        val len: Double
        fun at(t: Double): Pt

        /** 곡선(늘인 직선·원) 위 점 p 의 매개변수. 원호는 토막 가운데에서 ±π 안으로 */
        fun param(p: Pt): Double
    }

    private class CvLine(val a: Pt, val d: Pt, override val len: Double) : Cv {
        override fun at(t: Double) = a + d * t
        override fun param(p: Pt) = (p - a) dot d
    }

    /** rad = 0 이면 한 점(len = 0) */
    private class CvArc(val c: Pt, val rad: Double, val a0: Double, val cw: Boolean, override val len: Double) : Cv {
        override fun at(t: Double): Pt {
            val a = a0 + (if (cw) -t else t)
            return c + Pt(cos(a), sin(a)) * rad
        }

        override fun param(p: Pt): Double {
            val raw = angleOf(p - c) - a0
            return wrapPi((if (cw) -raw else raw) - len / 2) + len / 2
        }
    }

    private fun Cv.seg(s: Pt, e: Pt): Seg =
        if (this is CvArc && rad > EPS && (e - s).length > EPS) Arc(s, e, c, cw) else Line(s, e)

    /** 노즈 중심이 넘으면 재료와의 겹침이 바뀔 수 있는 곡선: 끝없는 직선(지나는 점, 단위 방향)·원 */
    private sealed interface Cut

    private class CutLine(val p: Pt, val d: Pt) : Cut

    private class CutCircle(val c: Pt, val rad: Double) : Cut

    /**
     * 재료 = 윤곽 + 양 끝을 접선 방향으로 늘인 반직선(도면 밖으로도 소재가 이어진다고 본다).
     * 노즈 원(중심 p, 반지름 r)이 재료와 겹치는지는 p 가 어느 요소에 r 보다 가까운지로 본다. 겹침은 p 가 [cuts] 를 넘을 때만
     * 바뀐다 — 요소마다 양쪽으로 r 민 직선·원, 끝점 둘레 반지름 r 원, 연장선을 양쪽으로 민 직선.
     */
    private class Material(private val segs: List<Seg>, private val r: Double) {
        private val rays = mutableListOf<Pair<Pt, Pt>>() // 시작점, 단위 방향
        private val cuts = mutableListOf<Cut>()

        init {
            val live = segs.filter { lengthOf(it) > EPS }
            if (live.isNotEmpty()) {
                rays += segs.first().start to tangent(live.first(), live.first().start) * -1.0
                rays += segs.last().end to tangent(live.last(), live.last().end)
            }
            for (s in segs) {
                cuts += CutCircle(s.start, r)
                cuts += CutCircle(s.end, r)
                when (s) {
                    is Line -> if (lengthOf(s) > EPS) {
                        val d = (s.end - s.start).unit()
                        cuts += CutLine(s.start + d.left() * r, d)
                        cuts += CutLine(s.start - d.left() * r, d)
                    }
                    is Arc -> {
                        cuts += CutCircle(s.center, s.radius + r)
                        if (abs(s.radius - r) > EPS) cuts += CutCircle(s.center, abs(s.radius - r))
                    }
                }
            }
            for ((o, u) in rays) {
                cuts += CutLine(o + u.left() * r, u)
                cuts += CutLine(o - u.left() * r, u)
            }
        }

        /** 중심이 p 인 노즈 원이 재료와 겹치지 않는다(닿는 것은 된다) */
        fun clear(p: Pt): Boolean =
            segs.all { distTo(it, p) >= r - TOL } && rays.all { (o, u) -> (p - (o + u * max(0.0, (p - o) dot u))).length >= r - TOL }

        /** cv 를 [cuts] 와 만나는 곳에서 나눈 매개변수들, 0 과 len 포함, 오름차순 */
        fun splits(cv: Cv): List<Double> {
            val ts = mutableListOf(0.0, cv.len)
            if (cv.len > 0) {
                for (k in cuts) for (p in hits(cv, k)) {
                    val t = cv.param(p)
                    if (t > 0 && t < cv.len) ts += t
                }
            }
            ts.sort()
            val out = ArrayList<Double>(ts.size)
            for (t in ts) {
                if (out.isEmpty() || t - out.last() > 1e-12) out += t
                else if (t == cv.len) out[out.lastIndex] = t
            }
            return out
        }

        private fun hits(cv: Cv, k: Cut): List<Pt> = when (cv) {
            is CvLine -> when (k) {
                is CutLine -> listOfNotNull(Intersect.lineLine(cv.a, cv.d, k.p, k.d))
                is CutCircle -> Intersect.lineCircle(cv.a, cv.d, k.c, k.rad)
            }
            is CvArc -> when (k) {
                is CutLine -> Intersect.lineCircle(k.p, k.d, cv.c, cv.rad)
                is CutCircle -> Intersect.circleCircle(cv.c, cv.rad, k.c, k.rad)
            }
        }

        private fun lengthOf(s: Seg): Double = when (s) {
            is Line -> (s.end - s.start).length
            is Arc -> s.sweepRad() * s.radius
        }

        private fun tangent(s: Seg, p: Pt): Pt = when (s) {
            is Line -> (s.end - s.start).unit()
            is Arc -> (p - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
        }

        private fun distTo(s: Seg, p: Pt): Double = when (s) {
            is Line -> {
                val v = s.end - s.start
                val l2 = v dot v
                if (l2 <= 0) (p - s.start).length
                else (p - (s.start + v * (((p - s.start) dot v) / l2).coerceIn(0.0, 1.0))).length
            }
            is Arc -> {
                val q = p - s.center
                val a = angleOf(q) - angleOf(s.start - s.center)
                val along = wrap2Pi(if (s.cw) -a else a)
                if (q.length > EPS && along <= s.sweepRad()) abs(q.length - s.radius)
                else min((p - s.start).length, (p - s.end).length)
            }
        }
    }

    private fun Seg.shifted(v: Pt): Seg = when (this) {
        is Line -> Line(start + v, end + v)
        is Arc -> Arc(start + v, end + v, center + v, cw)
    }
}
