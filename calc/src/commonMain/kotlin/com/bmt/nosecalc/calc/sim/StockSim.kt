package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.wrap2Pi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 나사 특징(설계 5.4). 나사 패스는 래스터를 깎지 않는다(나선은 회전체가 아니라 한 열에서 한 각도만 깎는다 — 회전체로 빼면 산까지 없어짐).
 * [root] = 지금까지 가장 깊은 패스의 골 선(가상 인선 경로 — 노즈 0 · 팁 0 나사 공구면 골 자리), [lead] = 리드,
 * [angleDeg] = 공구 설정의 나사 각(빈칸 60°), [outer] = 바깥 나사(후퇴가 +X), [line] = 처음 패스의 줄.
 */
data class ThreadFeature(val root: Line, val lead: Double, val angleDeg: Double, val outer: Boolean, val line: Int)

enum class ContactKind { RAPID_CUT, CHUCK }

/**
 * 급송 깎음 · 척 닿음(설계 5.5). [step] = 그 [MoveStep] 의 `run.steps` 번호, [t] = 처음 닿는 시각(이분법 — 1/2^20 이동 안),
 * [at] = 그때 가상 인선, [area] = 그 이동이 깎은 넓이(mm², 반단면 — 척은 0).
 */
data class Contact(val kind: ContactKind, val step: Int, val line: Int, val t: Double, val at: Pt, val area: Double)

/**
 * 남은 소재 한 조각: [outline] = 닫힌 꺾은선(칠하기, z · r ≥ 0 — [bottom] 다음 [top] 을 거꾸로, 반시계), [edges] = 테두리 선(축 위 · 조각 사이 이음은 뺀다),
 * [bottom] · [top] = 아래(안쪽) · 위(바깥) 사슬 — 둘 다 z 가 커지는 순, 양 끝 z 같음(3D 메시 `RevolveMesh` 가 돌린다).
 */
data class StockPiece(val outline: List<Pt>, val edges: List<List<Pt>>, val bottom: List<Pt>, val top: List<Pt>)

/** 그 시각의 남은 소재 단면(위쪽 반) + 나사 특징 */
data class StockProfile(val pieces: List<StockPiece>, val threads: List<ThreadFeature>)

/**
 * 소재 · 재료 제거(설계 5.2 · 5.4 · 5.5) — (z, r) 래스터.
 * 척 면([chuckZ] = 끝면 − 돌출)부터 끝면까지 Z 를 [dz] 열로 나누고, 열마다 소재가 있는 반경 구간 [r0, r1, r2, r3 …](정렬, Double — 9.2 손 계산
 * 1e-9 를 지키려고 Float 대신)을 둔다. 처음은 [Ød/2, ØD/2]. 열 배열은 바꾸지 않고 새로 만들어 끼운다(복사 없이 저장점 · 미리보기를 만든다).
 *
 * 이동마다 쓸고 간 영역 = 시작 · 끝 자세의 공구 다각형([ToolShape]) 볼록 껍질(직선). 원호는 현 높이 [ARC_SAG] 이하로 나눠 같은 방법.
 * 열 가운데 Z 에서 껍질의 x 범위를 재고, 축 아래(x < 0)는 |x| 로 접는다(회전체). 반경은 정확, Z 오차 ≤ 기울기 × dz/2.
 * 나사(THREAD) 이동은 깎지 않고 [ThreadFeature] 로 남긴다. 급송이 깎으면 [ContactKind.RAPID_CUT], 공구가 척(척 면 왼쪽, 반경 < 척 지름/2)에
 * 들어가면 [ContactKind.CHUCK].
 *
 * [cutTo] 는 앞으로는 이어서, 뒤로는 저장점(이동 [saveEvery] 개마다 — 합 40개 이하)에서 다시 깎는다. 한 스레드에서만 쓴다.
 */
class StockSim(val spec: StockSpec, val run: SimRun, private val tools: Map<Int, ToolOffset>, dz: Double? = null) {
    val chuckZ: Double = spec.faceZ - spec.length
    val dz: Double = dz ?: max(DZ, spec.length / MAX_COLS)
    val columns: Int = max(1, ceil(spec.length / this.dz - 1e-9).toInt())

    private val initial: DoubleArray =
        if (spec.dia > 0 && spec.boreDia < spec.dia) doubleArrayOf(max(spec.boreDia, 0.0) / 2, spec.dia / 2) else EMPTY
    private var cols: Array<DoubleArray> = Array(columns) { initial }
    private var view: Array<DoubleArray> = cols

    /** 이동 걸음의 run.steps 번호(시간 순) */
    private val moves: IntArray = run.steps.indices.filter { run.steps[it] is MoveStep }.toIntArray()
    private val saveEvery: Int = max(500, moves.size / 40 + 1)
    private val saves = ArrayList<Pair<Int, Array<DoubleArray>>>().apply { add(0 to cols.copyOf()) }
    private var done = 0
    private val shapes = HashMap<Triple<Int?, Double, Int>, List<Pt>>()
    private val found = HashMap<Int, Contact>()
    private val threadGroups: List<ThreadGroup> = threadGroups()

    /** 지금 보이는 시각([cutTo] 의 값) */
    var time: Double = 0.0
        private set

    /** 지금까지 찾은 닿음(시각 순). 끝까지 한 번 깎으면([cutTo] totalSec) 모두 */
    val contacts: List<Contact> get() = found.values.sortedWith(compareBy<Contact>({ it.t }, { it.kind.ordinal }))

    /** 열 가운데 Z */
    fun zOf(col: Int): Double = chuckZ + (col + 0.5) * dz

    /** 지금 보이는 열의 구간(테스트용 복사) */
    fun intervals(col: Int): DoubleArray = view[col].copyOf()

    /** 그 Z 에서 소재 가장 바깥 반경(없으면 null) */
    fun outerAt(z: Double): Double? {
        val i = floor((z - chuckZ) / dz).toInt()
        if (i !in 0 until columns) return null
        return view[i].takeIf { it.isNotEmpty() }?.last()
    }

    /** 그 Z 에서 소재 가장 안쪽 반경(구멍 벽 — 통짜면 0, 없으면 null) */
    fun innerAt(z: Double): Double? {
        val i = floor((z - chuckZ) / dz).toInt()
        if (i !in 0 until columns) return null
        return view[i].takeIf { it.isNotEmpty() }?.first()
    }

    /** 시각 t 의 소재로 — 앞으로는 이어서, 뒤로는 저장점에서 */
    fun cutTo(t: Double) {
        val tt = t.coerceAtLeast(0.0)
        // t1 ≤ tt 인 이동 수
        var lo = 0
        var hi = moves.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (step(mid).t1 <= tt) lo = mid + 1 else hi = mid
        }
        val target = lo
        if (target < done) {
            val sp = saves.last { it.first <= target }
            done = sp.first
            cols = sp.second.copyOf()
        }
        while (done < target) {
            sweep(done, 1.0, cols, record = true)
            done++
            if (done % saveEvery == 0 && saves.last().first < done) saves += done to cols.copyOf()
        }
        view = if (done < moves.size && tt > step(done).t0 && step(done).t1 > step(done).t0) {
            val m = step(done)
            val v = cols.copyOf()
            sweep(done, ((tt - m.t0) / (m.t1 - m.t0)).coerceIn(0.0, 1.0), v, record = false)
            v
        } else {
            cols
        }
        time = tt
    }

    private fun step(k: Int): MoveStep = run.steps[moves[k]] as MoveStep

    private fun shapeOf(m: MoveStep): List<Pt> = shapes.getOrPut(Triple(m.offset, m.r, m.tipNo)) {
        ToolShape.outline((m.offset?.let { tools[it] } ?: ToolOffset()).copy(noseR = m.r, tip = m.tipNo))
    }

    /** 이동 k 를 f(0~1)까지 arr 에서 깎는다. record = 닿음을 적는다(끝까지 깎을 때만) */
    private fun sweep(k: Int, f: Double, arr: Array<DoubleArray>, record: Boolean) {
        val m = step(k)
        val shape = shapeOf(m)
        if (shape.isEmpty()) return
        if (record) {
            chuckHit(m, shape)?.let { (ff, at) ->
                found[k * 2 + ContactKind.CHUCK.ordinal] = Contact(ContactKind.CHUCK, moves[k], m.line, m.t0 + ff * (m.t1 - m.t0), at, 0.0)
            }
        }
        if (m.kind == MoveKind.THREAD) return
        // 나사 패스 바로 뒤 급송(나사 골에서 빠짐)은 깎지 않는다 — 래스터에는 나사 골이 없어 헛경보가 된다
        if (m.kind == MoveKind.RAPID && k > 0 && step(k - 1).kind == MoveKind.THREAD) return
        if (record && m.kind == MoveKind.RAPID) {
            val dry = cutSeg(m.tip, 1.0, shape, arr, dry = true)
            if (dry > RAPID_AREA) {
                // 처음 닿는 자리: 깎는 넓이가 한도의 반을 넘는 가장 작은 f
                var a = 0.0
                var b = 1.0
                repeat(20) {
                    val c = (a + b) / 2
                    if (cutSeg(m.tip, c, shape, arr, dry = true) > RAPID_AREA / 2) b = c else a = c
                }
                val area = cutSeg(m.tip, 1.0, shape, arr, dry = false)
                found[k * 2 + ContactKind.RAPID_CUT.ordinal] =
                    Contact(ContactKind.RAPID_CUT, moves[k], m.line, m.t0 + b * (m.t1 - m.t0), SimRun.at(m.tip, b), area)
                return
            }
        }
        cutSeg(m.tip, f, shape, arr, dry = false)
    }

    /** 가상 인선이 seg 를 f 까지 갈 때의 자리들(원호는 현 높이 ARC_SAG 이하) */
    private fun chord(seg: Seg, f: Double): List<Pt> = when (seg) {
        is Line -> listOf(seg.start, SimRun.at(seg, f))
        is Arc -> {
            val r = seg.radius
            val step = if (r > ARC_SAG) 2 * acos(1 - ARC_SAG / r) else PI / 8
            val n = max(1, ceil(seg.sweepRad() * f / step).toInt())
            (0..n).map { SimRun.at(seg, f * it / n) }
        }
    }

    private fun cutSeg(seg: Seg, f: Double, shape: List<Pt>, arr: Array<DoubleArray>, dry: Boolean): Double {
        val pts = chord(seg, f)
        var area = 0.0
        for (i in 0 until pts.size - 1) area += cutHull(hull(shape, pts[i], pts[i + 1]), arr, dry)
        return area
    }

    /** 껍질이 덮는 열에서 그 반경 범위를 뺀다. 뺀 넓이(mm²)를 돌려준다. dry = 넓이만 잼 */
    private fun cutHull(h: List<Pt>, arr: Array<DoubleArray>, dry: Boolean): Double {
        var zMin = Double.POSITIVE_INFINITY
        var zMax = Double.NEGATIVE_INFINITY
        for (p in h) {
            zMin = min(zMin, p.z)
            zMax = max(zMax, p.z)
        }
        val i0 = max(0, ceil((zMin - chuckZ) / dz - 0.5 - 1e-9).toInt())
        val i1 = min(columns - 1, floor((zMax - chuckZ) / dz - 0.5 + 1e-9).toInt())
        if (i0 > i1) return 0.0
        val w = i1 - i0 + 1
        val lo = DoubleArray(w) { Double.POSITIVE_INFINITY }
        val hi = DoubleArray(w) { Double.NEGATIVE_INFINITY }
        for (e in h.indices) {
            val p = h[e]
            val q = h[(e + 1) % h.size]
            val za = min(p.z, q.z)
            val zb = max(p.z, q.z)
            val a = max(i0, ceil((za - chuckZ) / dz - 0.5 - 1e-9).toInt())
            val b = min(i1, floor((zb - chuckZ) / dz - 0.5 + 1e-9).toInt())
            if (a > b) continue
            val flat = zb - za < 1e-12
            for (i in a..b) {
                val j = i - i0
                if (flat) {
                    lo[j] = min(lo[j], min(p.x, q.x))
                    hi[j] = max(hi[j], max(p.x, q.x))
                } else {
                    val x = p.x + (q.x - p.x) * ((zOf(i) - p.z) / (q.z - p.z))
                    if (x < lo[j]) lo[j] = x
                    if (x > hi[j]) hi[j] = x
                }
            }
        }
        var removed = 0.0
        for (j in 0 until w) {
            val l = lo[j]
            val u = hi[j]
            if (l > u) continue
            // 회전체: 축 아래는 |x| 로 접는다
            val (a, b) = when {
                l >= 0 -> l to u
                u <= 0 -> -u to -l
                else -> 0.0 to max(-l, u)
            }
            val col = arr[i0 + j]
            if (col.isEmpty() || b <= col[0] || a >= col[col.size - 1]) continue
            var cut = 0.0
            var keep: DoubleArray? = null
            var n = 0
            for (s in 0 until col.size / 2) {
                val s0 = col[2 * s]
                val s1 = col[2 * s + 1]
                if (s1 <= a || s0 >= b) {
                    if (!dry) keep = put(keep, n, s0, s1, col.size + 2).also { n += 2 }
                    continue
                }
                cut += min(s1, b) - max(s0, a)
                if (!dry) {
                    if (a - s0 > SLIVER) keep = put(keep, n, s0, a, col.size + 2).also { n += 2 }
                    if (s1 - b > SLIVER) keep = put(keep, n, b, s1, col.size + 2).also { n += 2 }
                }
            }
            if (cut <= 0) continue
            removed += cut
            if (!dry) arr[i0 + j] = if (n == 0) EMPTY else keep!!.copyOf(n)
        }
        return removed * dz
    }

    private fun put(buf: DoubleArray?, n: Int, a: Double, b: Double, cap: Int): DoubleArray {
        val out = buf ?: DoubleArray(cap)
        out[n] = a
        out[n + 1] = b
        return out
    }

    /** 척에 들어가는 첫 f(0~1)와 그때 가상 인선. 안 들어가면 null */
    private fun chuckHit(m: MoveStep, shape: List<Pt>): Pair<Double, Pt>? {
        val rr = spec.chuckDia / 2
        fun hits(f: Double): Boolean {
            val pts = chord(m.tip, f)
            if (pts.size == 1 || f == 0.0) return inChuck(shape.map { it + pts[0] }, rr)
            for (i in 0 until pts.size - 1) if (inChuck(hull(shape, pts[i], pts[i + 1]), rr)) return true
            return false
        }
        if (!hits(1.0)) return null
        if (hits(0.0)) return 0.0 to m.tip.start
        var a = 0.0
        var b = 1.0
        repeat(20) {
            val c = (a + b) / 2
            if (hits(c)) b = c else a = c
        }
        return b to SimRun.at(m.tip, b)
    }

    /** 볼록 다각형이 척(z < 척 면, |x| < 척 반지름) 안으로 들어가나 — 척 면에 닿기만 하면 아님 */
    private fun inChuck(poly: List<Pt>, rr: Double): Boolean {
        if (poly.none { it.z < chuckZ - CONTACT }) return false
        // z ≤ 척 면 쪽으로 자른 다각형의 x 범위
        var xl = Double.POSITIVE_INFINITY
        var xh = Double.NEGATIVE_INFINITY
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            if (p.z <= chuckZ) {
                xl = min(xl, p.x)
                xh = max(xh, p.x)
            }
            if ((p.z - chuckZ) * (q.z - chuckZ) < 0) {
                val x = p.x + (q.x - p.x) * (chuckZ - p.z) / (q.z - p.z)
                xl = min(xl, x)
                xh = max(xh, x)
            }
        }
        return xl < rr - CONTACT && xh > -rr + CONTACT
    }

    // ---------------------------------------------------------------- 나사

    private class ThreadGroup(val line: Int, val lead: Double, val angle: Double, val outer: Boolean) {
        val passes = ArrayList<Pair<Double, Line>>()
        var z0 = Double.POSITIVE_INFINITY
        var z1 = Double.NEGATIVE_INFINITY
    }

    private fun threadGroups(): List<ThreadGroup> {
        val out = ArrayList<ThreadGroup>()
        var cur: ThreadGroup? = null
        for ((k, si) in moves.withIndex()) {
            val m = run.steps[si] as MoveStep
            if (m.kind == MoveKind.FEED) cur = null
            if (m.kind != MoveKind.THREAD) continue
            // 한 패스의 이어진 나사 토막 가운데 첫 것만 골 선 — 뒤 토막은 끝 빼기(G92 · G76 r)
            if (k > 0 && step(k - 1).kind == MoveKind.THREAD) continue
            val seg = m.tip as? Line ?: continue
            if (abs(seg.end.z - seg.start.z) < abs(seg.end.x - seg.start.x)) continue
            val za = min(seg.start.z, seg.end.z)
            val zb = max(seg.start.z, seg.end.z)
            val lead = m.feed ?: 0.0
            val g = cur?.takeIf { abs(it.lead - lead) < 1e-9 && za < it.z1 && zb > it.z0 }
                ?: ThreadGroup(m.line, lead, (m.offset?.let { tools[it] }?.threadDeg) ?: 60.0, outerAfter(k, seg)).also { out += it }
            g.passes += m.t0 to seg
            g.z0 = min(g.z0, za)
            g.z1 = max(g.z1, zb)
            cur = g
        }
        return out
    }

    /** 나사 패스 뒤 첫 급송이 +X 로 빠지면 바깥 나사 */
    private fun outerAfter(k: Int, seg: Line): Boolean {
        for (j in k + 1 until moves.size) {
            val m = step(j)
            if (m.kind == MoveKind.THREAD) continue
            val dx = m.tip.end.x - seg.end.x
            if (abs(dx) > 1e-9) return dx > 0
        }
        return true
    }

    private fun threadsAt(t: Double): List<ThreadFeature> = threadGroups.mapNotNull { g ->
        // 시작한 패스만(그 시각에 깎는 중인 패스 포함)
        val done = g.passes.filter { it.first < t }
        if (done.isEmpty()) return@mapNotNull null
        val deepest = if (g.outer) done.minBy { (it.second.start.x + it.second.end.x) } else done.maxBy { (it.second.start.x + it.second.end.x) }
        ThreadFeature(deepest.second, g.lead, g.angle, g.outer, g.line)
    }

    // ---------------------------------------------------------------- 단면

    /**
     * 지금 소재의 단면. 열 구간을 이웃 열과 하나씩 이어 '가닥' 을 만들고(갈라지거나 합쳐지면 가닥을 끊음), 가닥마다 아래 · 위 사슬을
     * Douglas–Peucker(허용 tol mm)로 줄인다. 테두리 = 위 사슬 · 축 위가 아닌 아래 사슬 · 가닥 끝에서 이웃 열이 덮지 않는 세로 토막.
     */
    fun profile(tol: Double = 0.002): StockProfile {
        val v = view
        val pieces = ArrayList<StockPiece>()
        var prevIv = EMPTY
        var prevStrands = arrayOfNulls<Strand>(0)
        for (i in 0..columns) {
            val cur = if (i < columns) v[i] else EMPTY
            val m = cur.size / 2
            val pm = prevIv.size / 2
            // 흔한 꼴(앞 열 · 이 열 모두 구간 하나, 겹침)은 배열 없이 가닥을 잇는다
            if (m == 1 && pm == 1 && min(prevIv[1], cur[1]) - max(prevIv[0], cur[0]) > 1e-9) {
                prevStrands[0]!!.add(zOf(i), cur[0], cur[1])
                prevIv = cur
                continue
            }
            val curCount = IntArray(m)
            val prevCount = IntArray(pm)
            val curMatch = IntArray(m) { -1 }
            for (p in 0 until pm) for (j in 0 until m) {
                if (min(prevIv[2 * p + 1], cur[2 * j + 1]) - max(prevIv[2 * p], cur[2 * j]) > 1e-9) {
                    curCount[j]++
                    prevCount[p]++
                    curMatch[j] = p
                }
            }
            val next = arrayOfNulls<Strand>(m)
            val kept = BooleanArray(pm)
            for (j in 0 until m) {
                val p = curMatch[j]
                if (curCount[j] == 1 && prevCount[p] == 1) {
                    next[j] = prevStrands[p]
                    kept[p] = true
                }
            }
            for (p in 0 until pm) if (!kept[p]) pieces += finish(prevStrands[p]!!, i - 1, v, tol)
            for (j in 0 until m) {
                val s = next[j] ?: Strand(i).also { next[j] = it }
                s.add(zOf(i), cur[2 * j], cur[2 * j + 1])
            }
            prevIv = cur
            prevStrands = next
        }
        return StockProfile(pieces, threadsAt(time))
    }

    private class Strand(val start: Int) {
        var n = 0
        var z = DoubleArray(64)
        var a = DoubleArray(64)
        var b = DoubleArray(64)
        fun add(zc: Double, lo: Double, hi: Double) {
            if (n == z.size) {
                z = z.copyOf(n * 2)
                a = a.copyOf(n * 2)
                b = b.copyOf(n * 2)
            }
            z[n] = zc
            a[n] = lo
            b[n] = hi
            n++
        }
    }

    private fun finish(s: Strand, last: Int, v: Array<DoubleArray>, tol: Double): StockPiece {
        val zL = chuckZ + s.start * dz
        val zR = chuckZ + (last + 1) * dz
        val n = s.n
        // 사슬: 양 끝은 열 가장자리까지
        val zs = DoubleArray(n + 2)
        val bot = DoubleArray(n + 2)
        val top = DoubleArray(n + 2)
        zs[0] = zL
        bot[0] = s.a[0]
        top[0] = s.b[0]
        for (i in 0 until n) {
            zs[i + 1] = s.z[i]
            bot[i + 1] = s.a[i]
            top[i + 1] = s.b[i]
        }
        zs[n + 1] = zR
        bot[n + 1] = s.a[n - 1]
        top[n + 1] = s.b[n - 1]
        val topS = simplify(zs, top, 0, n + 1, tol)
        val botS = simplify(zs, bot, 0, n + 1, tol)
        val outline = botS + topS.asReversed()
        val edges = ArrayList<List<Pt>>()
        edges += topS
        // 아래 사슬은 축(r = 0)에 붙은 곳을 빼고
        var i = 0
        while (i <= n + 1) {
            if (bot[i] <= AXIS) {
                i++
                continue
            }
            var j = i
            while (j + 1 <= n + 1 && bot[j + 1] > AXIS) j++
            if (j > i) edges += simplify(zs, bot, i, j, tol)
            i = j + 1
        }
        // 가닥 끝 세로 토막: 이웃 열이 덮지 않는 부분만
        fun cap(z: Double, lo: Double, hi: Double, nb: Int) {
            val cover = if (nb in 0 until columns) v[nb] else EMPTY
            var from = lo
            for (k in 0 until cover.size / 2) {
                val c0 = cover[2 * k]
                val c1 = cover[2 * k + 1]
                if (c1 <= from || c0 >= hi) continue
                if (c0 > from + 1e-9) edges += listOf(Pt(z, from), Pt(z, min(c0, hi)))
                from = max(from, c1)
                if (from >= hi) break
            }
            if (hi > from + 1e-9) edges += listOf(Pt(z, from), Pt(z, hi))
        }
        cap(zL, s.a[0], s.b[0], s.start - 1)
        cap(zR, s.a[n - 1], s.b[n - 1], last + 1)
        return StockPiece(outline, edges, botS, topS)
    }

    /** Douglas–Peucker(반복형) — zs · rs 의 [from, to] 를 허용 tol 로 */
    private fun simplify(zs: DoubleArray, rs: DoubleArray, from: Int, to: Int, tol: Double): List<Pt> {
        if (to <= from) return listOf(Pt(zs[from], rs[from]))
        val keep = BooleanArray(to - from + 1)
        keep[0] = true
        keep[to - from] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(from to to)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val dzab = zs[b] - zs[a]
            val drab = rs[b] - rs[a]
            val len = kotlin.math.sqrt(dzab * dzab + drab * drab)
            var worst = -1.0
            var at = -1
            for (k in a + 1 until b) {
                val d = if (len < 1e-15) kotlin.math.hypot(zs[k] - zs[a], rs[k] - rs[a])
                else abs((zs[k] - zs[a]) * drab - (rs[k] - rs[a]) * dzab) / len
                if (d > worst) {
                    worst = d
                    at = k
                }
            }
            if (worst > tol) {
                keep[at - from] = true
                stack.addLast(a to at)
                stack.addLast(at to b)
            }
        }
        val out = ArrayList<Pt>()
        for (k in from..to) if (keep[k - from]) out += Pt(zs[k], rs[k])
        return out
    }

    companion object {
        /** 열 간격(mm) — 돌출이 길면 열 [MAX_COLS] 개가 되게 넓힌다 */
        const val DZ = 0.01
        const val MAX_COLS = 30_000

        /** 원호를 나누는 현 높이(mm) */
        const val ARC_SAG = 0.002

        /** 급송 깎음으로 보는 넓이(mm²) */
        const val RAPID_AREA = 0.001

        /** 닿음으로 보는 깊이(mm) — 면에 닿기만 한 것은 아님 */
        const val CONTACT = 1e-6

        private const val SLIVER = 1e-9
        private const val AXIS = 1e-9
        private val EMPTY = DoubleArray(0)

        /** 점 둘에 놓인 볼록 다각형 shape 의 볼록 껍질(반시계) — 직선 이동이 쓸고 간 영역 */
        internal fun hull(shape: List<Pt>, a: Pt, b: Pt): List<Pt> {
            if ((b - a).length <= 1e-12) return shape.map { it + a }
            val pts = ArrayList<Pt>(shape.size * 2)
            for (p in shape) pts += p + a
            for (p in shape) pts += p + b
            return convexHull(pts)
        }

        /** 단조 사슬 볼록 껍질(반시계, 같은 직선 위 점은 뺌) */
        internal fun convexHull(input: List<Pt>): List<Pt> {
            val p = input.sortedWith(compareBy<Pt>({ it.z }, { it.x }))
            if (p.size < 3) return p
            fun cross(o: Pt, a: Pt, b: Pt) = (a.z - o.z) * (b.x - o.x) - (a.x - o.x) * (b.z - o.z)
            val h = ArrayList<Pt>(p.size * 2)
            for (q in p) {
                while (h.size >= 2 && cross(h[h.size - 2], h[h.size - 1], q) <= 0) h.removeAt(h.size - 1)
                h += q
            }
            val lower = h.size + 1
            for (i in p.size - 2 downTo 0) {
                val q = p[i]
                while (h.size >= lower && cross(h[h.size - 2], h[h.size - 1], q) <= 0) h.removeAt(h.size - 1)
                h += q
            }
            h.removeAt(h.size - 1)
            return h
        }
    }
}

/**
 * 소재 · 원점 자동 값(설계 5.1 · 4.1) — 화면이 빈칸일 때 쓰고 `autoValue` 로 보인다.
 * 설계대로: 봉재 지름 = 이송 이동(G01~G03 · 사이클 깎기 · 다듬질)이 닿은 가장 큰 X 를 1 mm 단위로 올림(정수면 +1 — 가장 바깥 이송도 소재에 닿게,
 * 10 mm 아래면 10 — 축 위 드릴만 있는 글), 끝면(기장여유) = 이송이 닿은 가장 큰 Z(0 보다 작으면 0), 돌출 = 끝면 − 가장 작은 Z + 10,
 * 척 지름 = 2.5 × ØD 를 100~300 으로 자름. 원점 = X max(200, ØD + 100) · Z 끝면 + 100.
 *
 * 구현에서 더한 것(우리가 정함 — 예제를 돌려 보니 설계 규칙만으로는 헛경보가 쏟아졌다, 설계 15.2):
 * - **공기 점**: 첫 이송 앞 급송의 끝(진입점)과 고정 사이클의 시작점 A 는 소재 밖이다(사이클 시작점은 소재 밖에 둔다 — S 4-102 그림).
 *   이송이 그 점에서 시작해(G71 층 · G75 쪼기 · G90 Z 이송) 위 규칙이 그 점까지 소재로 잡으면, 급송 절입이 모두 '급송 깎음' 이 된다.
 *   그 점이 소재 안이면 끝면을 그 Z 로 내리거나 지름을 그 X 로 줄인다(덜 바뀌는 쪽).
 * - **구멍**: 안쪽 공구(팁 1 · 2 · 6)의 이송이 있으면 구멍 지름 = 그 이송의 가장 작은 X − 2 mm(내림) — 통짜 소재에 내경 바가 들어가면
 *   급송 후퇴가 모두 소재를 지나는 헛경보가 되어서(설계 5.5 가 말한 그 경보).
 * - 나사 이동은 넣지 않는다 — 나사 시작 Z 는 리드 몇 배 바깥(들어가는 여유)이다. 이송이 나사뿐이면 지름은 나사 이동의 가장 큰 X 로,
 *   끝면은 나사 시작 Z − 3 리드로 한다.
 */
object StockAuto {
    fun of(run: SimRun): StockSpec? {
        val moves = run.steps.filterIsInstance<MoveStep>()
        val feed = moves.filter { it.kind == MoveKind.FEED }
        val threads = moves.filter { it.kind == MoveKind.THREAD }
        val use = feed.ifEmpty { threads }
        if (use.isEmpty()) return null
        var xMax = Double.NEGATIVE_INFINITY
        var zMax = Double.NEGATIVE_INFINITY
        var zMin = Double.POSITIVE_INFINITY
        for (s in use) {
            for (p in extremes(s.tip)) {
                xMax = max(xMax, abs(p.x))
                zMax = max(zMax, p.z)
                zMin = min(zMin, p.z)
            }
        }
        if (feed.isEmpty()) zMax -= 3 * (threads.maxOf { it.feed ?: 0.0 })
        var r = max(10.0, floor(2 * xMax + 1e-9) + 1) / 2
        var face = max(zMax, 0.0)
        // 공기 점: 첫 이송 앞 급송의 끝 · 사이클 시작점
        val air = ArrayList<Pt>()
        val first = moves.indexOfFirst { it.kind != MoveKind.RAPID }
        for ((k, m) in moves.withIndex()) {
            if (m.kind == MoveKind.RAPID && (first < 0 || k < first)) air += m.tip.end
            // 사이클의 첫 이동(G70 의 끝 복귀는 Q 블록 끝 — 소재 면 위라 뺀다)
            val start = m.src in CYCLE && m.src != StepSrc.CYCLE_RETURN
            if (start && (k == 0 || moves[k - 1].line != m.line || moves[k - 1].src !in CYCLE)) air += m.tip.start
        }
        var changed = true
        var guard = 0
        while (changed && guard++ <= air.size) {
            changed = false
            for (p in air) {
                if (p.z < face - 1e-9 && abs(p.x) < r - 1e-9) {
                    if (r - abs(p.x) <= face - p.z) r = abs(p.x) else face = p.z
                    changed = true
                }
            }
        }
        val dia = 2 * r
        val inner = feed.filter { it.tipNo == 1 || it.tipNo == 2 || it.tipNo == 6 }
        var bore = if (inner.isEmpty()) 0.0 else max(0.0, floor(2 * inner.minOf { m -> extremes(m.tip).minOf { abs(it.x) } } + 1e-9) - 2)
        if (bore >= dia) bore = 0.0
        return StockSpec(dia, max(face - zMin, 0.0) + 10, face, bore, chuckOf(dia))
    }

    private val CYCLE = setOf(StepSrc.CYCLE_APPROACH, StepSrc.CYCLE_CUT, StepSrc.CYCLE_RETRACT, StepSrc.CYCLE_RETURN, StepSrc.CYCLE_FINISH)

    fun chuckOf(dia: Double): Double = (2.5 * dia).coerceIn(100.0, 300.0)

    /** 원점(G28 · 시작 자리 — 공작물 좌표, x 반경) */
    fun home(dia: Double, faceZ: Double): Pt = Pt(faceZ + 100, max(200.0, dia + 100) / 2)

    /** 토막의 끝점 + 원호가 지나는 축 방향 점(가장 큰 X · Z 를 놓치지 않게) */
    fun extremes(s: Seg): List<Pt> = when (s) {
        is Line -> listOf(s.start, s.end)
        is Arc -> {
            val out = mutableListOf(s.start, s.end)
            val a0 = angleOf(s.start - s.center)
            val sweep = s.sweepRad()
            for (q in 0 until 4) {
                val a = q * PI / 2
                // 진행 방향으로 잰 a0 에서 a 까지
                val d = if (s.cw) wrap2Pi(a0 - a) else wrap2Pi(a - a0)
                if (d > 0 && d < sweep) out += s.center + Pt(kotlin.math.cos(a), kotlin.math.sin(a)) * s.radius
            }
            out
        }
    }
}
