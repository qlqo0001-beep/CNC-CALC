package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Seg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 직선(유클리드) 거리 독립 참조 — 구현(Clearance.kt · Holder.kt 의 BarShape · Bore)의 식과 코드를 쓰지 않고 따로 짠다(10-28 검증).
 * - 공구: 꼭짓점이 원점이고 두 날이 0 · ε 방향인 마름모(T 는 삼각형)를 κr − 180° 돌려 노즈 원이 두 날에 접하게 놓는다(뒤보링은 Z 로 뒤집음).
 *   인서트 조각 = 날 접점 T1 · T2 사이를 현으로 자른 다각형을 바 띠 위 · 아래로 자른 것(설계 정의), 바 = 띠 [아래, 위] × [앞, 공구대 면],
 *   앞은 주절삭날이 꼭짓점을 지나는 선(뒤보링은 인서트 끝에서 수직). DMIN 구간 = 축 둘레 지름 DMIN, 인선 z 에서 인서트 끝까지
 * - 소재 경계: 도면 + 입구 쪽(입구 면을 알면 입구 점에서 위로, 모르면 −시작 접선 반직선) + 깊은 끝(축 위가 아니면 끝 접선 반직선) + 입구 면 위로,
 *   그리고 축 너머 거울(맞은편)
 * - 거리: 다각형 변 × 경계 요소마다 — 만나면 0, 아니면 두 끝점에서 상대까지와, 원호는 중심에서 변에 내린 발 쪽 원호 점(발이 원 밖이면 그 방향의
 *   원 위 점, 발이 원 안이면 끝점이 가깝다)
 * - 깎는 면(예외): 경로 요소 k 의 도면 요소가 직선이고 노즈가 닿는 방향이 끝날보다 주절삭날 접점 쪽이면 그 직선, 앞 요소들도 이음이 볼록하지 않은
 *   동안 같은 조건이면 그 직선 — 그 위의 경계 직선은 거리에서 뺀다
 */
internal class EuclidRef(bar: BoringBar, private val r: Double, private val reverse: Boolean) {
    /** 노즈 중심에서 주절삭날 · 끝날 접점 방향(단위) */
    val n1: V
    val n2: V
    val insertUp: List<V>
    val insertLow: List<V>
    val body: List<V>
    val refZ = if (reverse) r else -r
    private val insZ: Double
    private val dMin = bar.dMin

    init {
        val k = bar.kappaDeg * PI / 180
        val e = bar.shape.epsDeg * PI / 180
        val l = bar.edge
        val phi = k - PI
        fun rot(p: V) = V(p.z * cos(phi) - p.x * sin(phi), p.z * sin(phi) + p.x * cos(phi))
        // 꼭짓점에서 노즈 중심까지: 두 날의 이등분선 위 r / sin(ε/2)
        val toNose = rot(V(cos(e / 2), sin(e / 2)) * (r / sin(e / 2)))
        fun place(p: V): V {
            val q = rot(p) - toNose
            return if (reverse) V(-q.z, q.x) else q
        }
        val t = r / tan(e / 2)
        val t1 = place(V(t, 0.0))
        val t2 = place(V(t * cos(e), t * sin(e)))
        val a = place(V(l, 0.0))
        val b = place(V(l * cos(e), l * sin(e)))
        val far = if (bar.shape == InsertShape.T) null else place(V(l + l * cos(e), l * sin(e)))
        val corner = place(V(0.0, 0.0))
        n1 = if (r > 0) t1 * (1 / r) else (rot(V(0.0, -1.0)).let { if (reverse) V(-it.z, it.x) else it })
        n2 = if (r > 0) t2 * (1 / r) else (rot(V(cos(e + PI / 2), sin(e + PI / 2))).let { if (reverse) V(-it.z, it.x) else it })
        val ins = listOfNotNull(t1, a, far, b, t2)
        val mid = r - bar.f
        val top = mid + bar.dia / 2
        val bottom = mid - bar.dia / 2
        insertUp = clipHalf(ins) { it.x - top }
        insertLow = clipHalf(ins) { bottom - it.x }
        val (ft, fb) = if (reverse) {
            val zf = ins.minOf { it.z }
            V(zf, top) to V(zf, bottom)
        } else {
            val d = rot(V(1.0, 0.0)).let { if (reverse) V(-it.z, it.x) else it }
            (corner + d * ((top - corner.x) / d.x)) to (corner + d * ((bottom - corner.x) / d.x))
        }
        val back = refZ + (bar.overhang ?: 1e5)
        body = listOf(fb, V(back, bottom), V(back, top), ft)
        insZ = ins.maxOf { if (reverse) -it.z else it.z } + r
    }

    /** 노즈 [c] 자세의 DMIN 구간(축 둘레 직사각형), 없으면 null */
    fun zone(c: V): List<V>? {
        val d = dMin ?: return null
        val tipZ = c.z + refZ
        val lo = if (reverse) tipZ - insZ else tipZ
        val hi = if (reverse) tipZ else tipZ + insZ
        return listOf(V(lo, -d / 2), V(hi, -d / 2), V(hi, d / 2), V(lo, d / 2))
    }

    companion object {
        /** 소재 경계: (깎는 쪽 사슬, 거울 사슬) */
        fun material(drawing: List<Seg>, faceAtStart: Boolean): Pair<List<El>, List<El>> {
            val els = drawing.map(::elOfSeg)
            val first = els.first()
            val last = els.last()
            fun ray(p: V, d0: V): V? {
                val d = if (abs(d0.z) <= 2e-3) V(0.0, if (d0.x >= 0) 1.0 else -1.0) else d0
                if (d.x >= 0) return p + d * 1e5
                val len = min(1e5, p.x / -d.x)
                if (len <= 1e-12) return null
                return if (len < 1e5) V(p.z + d.z * len, 0.0) else p + d * len
            }
            val head = if (faceAtStart) null else ray(first.a, first.tangent(0.0) * -1.0)?.let { Ln(it, first.a) }
            val tail = if (last.b.x > 1e-9) ray(last.b, last.tangent(1.0))?.let { Ln(last.b, it) } else null
            val ext = listOfNotNull(head) + els + listOfNotNull(tail)
            val mouth = listOf(ext.first().a, ext.last().b).maxBy { it.z }
            val up = ext + Ln(mouth, V(mouth.z, mouth.x + 1e5))
            fun mirror(e: El): El = when (e) {
                is Ln -> Ln(V(e.a.z, -e.a.x), V(e.b.z, -e.b.x))
                is Ac -> Ac(V(e.a.z, -e.a.x), V(e.b.z, -e.b.x), V(e.c.z, -e.c.x), !e.cw)
            }
            return up to up.map(::mirror)
        }

        fun elOfSeg(s: Seg): El = when (s) {
            is Line -> Ln(V(s.start.z, s.start.x), V(s.end.z, s.end.x))
            is Arc -> Ac(V(s.start.z, s.start.x), V(s.end.z, s.end.x), V(s.center.z, s.center.x), s.cw)
        }

        /** 선분 pq 와 요소 e 사이 가장 짧은 거리 */
        fun segEl(p: V, q: V, e: El): Double {
            val d = q - p
            val l2 = d dot d
            fun onSeg(x: V): Double {
                if (l2 == 0.0) return (x - p).len
                val t = (((x - p) dot d) / l2).coerceIn(0.0, 1.0)
                return (x - (p + d * t)).len
            }
            if (crosses(p, q, e)) return 0.0
            var best = min(e.dist(p), e.dist(q))
            best = min(best, min(onSeg(e.a), onSeg(e.b)))
            if (e is Ac && l2 > 0) {
                // 중심에서 변(선분)에 내린 발 F: 원 밖이면 원 위 가장 가까운 점이 c + R·(F − c)/|F − c|
                val t = (((e.c - p) dot d) / l2).coerceIn(0.0, 1.0)
                val f = p + d * t
                val fc = (f - e.c).len
                if (fc > 1e-12) {
                    val on = e.c + (f - e.c) * (e.r / fc)
                    if (e.dist(on) < 1e-9) best = min(best, onSeg(on))
                }
            }
            return best
        }

        private fun crosses(p: V, q: V, e: El): Boolean {
            val d = q - p
            return when (e) {
                is Ln -> {
                    val f = e.b - e.a
                    val den = d cross f
                    if (abs(den) < 1e-18) return false
                    val t = ((e.a - p) cross f) / den
                    val u = ((e.a - p) cross d) / den
                    t in 0.0..1.0 && u in 0.0..1.0
                }
                is Ac -> {
                    // |p + d t − c| = R
                    val w = p - e.c
                    val aa = d dot d
                    if (aa == 0.0) return false
                    val bb = 2 * (w dot d)
                    val cc = (w dot w) - e.r * e.r
                    val disc = bb * bb - 4 * aa * cc
                    if (disc < 0) return false
                    val s = sqrt(disc)
                    listOf((-bb - s) / (2 * aa), (-bb + s) / (2 * aa)).any { t -> t in 0.0..1.0 && e.dist(p + d * t) < 1e-9 }
                }
            }
        }

        /** 볼록 다각형(꼭짓점 차례)과 요소들 사이 가장 짧은 거리 — 겹치는지는 보지 않는다(둘레끼리) */
        fun polyDist(poly: List<V>, els: List<El>): Double {
            var best = Double.POSITIVE_INFINITY
            for (i in poly.indices) {
                val p = poly[i]
                val q = poly[(i + 1) % poly.size]
                for (e in els) best = min(best, segEl(p, q, e))
            }
            return best
        }

        /**
         * 경로 요소 k 에서 빼는 깎는 면 직선들(위 머리말). [left] = 공구가 진행 왼쪽(G41), n1 · n2 = 공구의 날 접점 방향,
         * [upTo] = 앞 요소는 이 요소까지(안전 끝점 요소 — 그 뒤 면은 끝내 깎지 않아 예외가 아니다)
         */
        fun faceLines(drawing: List<Seg>, k: Int, left: Boolean, n1: V, n2: V, upTo: Int = Int.MAX_VALUE): List<Ln> {
            if (k < 0 || k >= drawing.size) return emptyList()
            val els = drawing.map(::elOfSeg)
            fun facing(e: El): Ln? {
                if (e !is Ln || e.length < 1e-9) return null
                val toTool = e.tangent(0.0).ccw() * (if (left) 1.0 else -1.0)
                val u = toTool * -1.0
                return if ((u dot n1) > (u dot n2)) e else null
            }
            val out = listOfNotNull(facing(els[k])).toMutableList()
            // 앞 요소들: 이음이 볼록(공구 반대쪽으로 꺾임)하면 멈춘다
            for (j in k + 1 until els.size) {
                if (j > upTo) break
                val a = els[j - 1]
                val b = els[j]
                if (a.length > 1e-9 && b.length > 1e-9 && (a.tangent(1.0) cross b.tangent(0.0)) * (if (left) 1.0 else -1.0) < -1e-9) break
                facing(b)?.let { out += it }
            }
            return out
        }

        /** 요소 e 가 직선 [l] 들 가운데 하나 위에 놓였나(두 끝이 1e-6 안) */
        fun onAny(e: El, lines: List<Ln>): Boolean = e is Ln && lines.any { l ->
            val u = (l.b - l.a).unit()
            abs((e.a - l.a) cross u) <= 1e-6 && abs((e.b - l.a) cross u) <= 1e-6
        }

        private fun clipHalf(poly: List<V>, f: (V) -> Double): List<V> {
            val out = mutableListOf<V>()
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                val fa = f(a)
                val fb = f(b)
                if (fa >= 0) out += a
                if ((fa >= 0) != (fb >= 0)) out += a + (b - a) * (fa / (fa - fb))
            }
            return if (out.size >= 3) out else emptyList()
        }
    }

    /**
     * 노즈 [c] 자세에서 부분별 직선 거리(겹침은 보지 않는다 — 떨어진 자세에서만 뜻이 있다): (인서트 깎는 쪽, 인서트 맞은편, 바 깎는 쪽, 바 맞은편, DMIN 구간).
     * [skip] = 뺄 깎는 면 직선
     */
    fun gaps(c: V, cut: List<El>, opp: List<El>, skip: List<Ln> = emptyList()): DoubleArray {
        val cu = cut.filterNot { onAny(it, skip) }
        val op = opp.filterNot { onAny(it, skip) }
        fun at(p: List<V>) = p.map { it + c }
        fun d(p: List<V>, els: List<El>) = if (p.isEmpty()) Double.POSITIVE_INFINITY else polyDist(at(p), els)
        val ic = min(d(insertUp, cu), d(insertLow, cu))
        val io = min(d(insertUp, op), d(insertLow, op))
        val z = zone(c)?.let { polyDist(it, cu) } ?: Double.POSITIVE_INFINITY
        return doubleArrayOf(ic, io, d(body, cu), d(body, op), z)
    }
}
