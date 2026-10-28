package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Pt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 3D 단면 절개(설계 6.2): QUARTER = θ ∈ (0°, 90°)(처음 시점에서 앞 위)를 빼고 θ = 0° · 90° 두 단면을 채운다 */
enum class Section { FULL, QUARTER }

/**
 * 삼각형 메시(설계 6.2 · 8.5). 좌표 mm — x = 공작물 축(Z), y = r cos θ(위), z = r sin θ(앞, 오른손 좌표: θ = 0 위 · 90° 앞).
 * [nrm] = 꼭짓점 법선(바깥쪽 단위 벡터), [tri] = 꼭짓점 번호 셋씩 — 바깥에서 보아 반시계(뒷면 버리기에 쓴다),
 * [tag] = 삼각형마다 [PART](겉) · [SECTION](단면) · [CHUCK](척 몸통) · [JAW](척 턱).
 */
class Mesh(val pos: FloatArray, val nrm: FloatArray, val tri: IntArray, val tag: ByteArray) {
    val vertices: Int get() = pos.size / 3
    val triangles: Int get() = tri.size / 3

    companion object {
        const val PART: Byte = 0
        const val SECTION: Byte = 1
        const val CHUCK: Byte = 2
        const val JAW: Byte = 3
        val EMPTY = Mesh(FloatArray(0), FloatArray(0), IntArray(0), ByteArray(0))
    }
}

/**
 * 남은 소재 · 척 메시(설계 6.2). 소재 단면 조각([StockPiece])마다 아래 · 위 사슬을 Douglas–Peucker(허용 tol)로 줄이고, 닫힌 고리(아래 사슬 → 위 사슬 거꾸로)의
 * 꼭짓점을 θ 로 [segments] 토막 돌린다. 이웃 고리 사이 띠마다 꼭짓점을 따로 두고 법선은 띠의 단면 방향 × 둘레 방향 — 이웃 띠와 35° 안이면 평균(둥근 곳은 매끈,
 * 어깨 · 모따기는 모서리). 축 위 사슬(통짜)은 돌리지 않는다.
 *
 * 나사([ThreadFeature] — 래스터는 나사를 깎지 않는다, 설계 5.4)는 바깥 나사면 위 사슬, 안 나사면 아래 사슬의 그 구간을 **나선 줄**로 바꾼다:
 * 줄 u 의 θ 자리 z = u + 리드 · θ/2π(구간 끝에서 자름) — 골 가운데 z_c(θ) = 골 선 위끝 − 리드/2 + 리드 · θ/2π − k · 리드(오른나사로 그림, θ = 0 단면은 2D 톱니와 같은 자리)라
 * z − z_c 가 줄마다 일정해 골 · 산 모서리가 줄에 딱 놓인다(설계의 'Z 를 리드/16 로 나눔' 대신 — 같은 날카로움을 줄 1/2~1/4 로). 반경 = min(소재 r(z), 골 선 r(z) + |z − z_c| / tan(a/2))
 * (안 나사는 max · 빼기), 골 가운데가 패스의 Z 범위 밖이면 그 골은 없다(나사 끝에서 골이 끝남).
 *
 * 1/4 절개면 θ = 90° ~ 360° 만 돌리고 두 단면을 사다리꼴로 채운다(아래 · 위 사슬이 z 로 단조라 z 를 합친 띠로 — tag [Mesh.SECTION]).
 * 삼각형이 [part] 의 maxTri 를 넘으면 나사 줄 → tol 두 배 → 토막 반 순서로 줄인다(설계 6.2). 그래도 넘치면(잘게 깎인 큰 소재 — 4단계 무작위 시험에서 찾음)
 * 나사 홈을 빼고 tol 을 [LAST_TOL] 까지 계속 두 배로 — 3D 만 거칠어지고 2D 는 그대로다.
 */
object RevolveMesh {
    /** 끄는 동안 · 재생 중(설계 6.2): 토막 · 허용 오차 · 삼각형 한도 */
    const val SEG_DRAG = 32
    const val TOL_DRAG = 0.02
    const val TRI_DRAG = 40_000

    /** 멈춰 있을 때 */
    const val SEG_IDLE = 96
    const val TOL_IDLE = 0.005
    const val TRI_IDLE = 120_000

    /** 나사 한 리드를 나누는 고른 줄 수(골 · 산 모서리 줄은 따로) */
    private const val FILL = 8
    private const val MIN_SEG = 16
    private const val MAX_TOL = 0.5

    /** 한도를 지키려고 마지막에 키우는 tol 의 끝(mm) — 소재 칸이 받는 가장 큰 지름 10 000 의 몫 */
    private const val LAST_TOL = 1_000.0
    private const val AXIS = 1e-9

    /** 띠 사이 법선을 평균하는 한계 — 35° */
    private val CREASE = cos(35 * PI / 180)
    private const val TWO_PI = 2 * PI

    /** 척 턱 길이(척 면에서 Z−) — 2D 뷰의 척 그림(Sim2DView)과 같은 값 */
    fun jawLength(spec: StockSpec): Double = max(15.0, spec.dia * 0.4)

    /** 남은 소재 메시. 삼각형이 maxTri 를 넘으면 줄인다 */
    fun part(p: StockProfile, section: Section, segments: Int, tol: Double, maxTri: Int): Mesh {
        var t = tol
        var fill = FILL
        var seg = segments
        var threads = true
        var plan = plan(p, section, seg, t, fill, threads)
        repeat(48) {
            if (plan.triangles <= maxTri) return build(plan)
            when {
                threads && plan.threadTriangles * 2 > plan.triangles && fill > 2 -> fill /= 2
                t < MAX_TOL -> t *= 2
                seg > MIN_SEG -> seg /= 2
                threads && fill > 1 -> fill /= 2
                // 그래도 넘치면(아주 고운 긴 나사) 나사 홈은 그리지 않는다
                threads -> threads = false
                t < LAST_TOL -> t *= 2
                else -> return build(trim(plan, maxTri))
            }
            plan = plan(p, section, seg, t, fill, threads)
        }
        return build(trim(plan, maxTri))
    }

    /**
     * 마지막 수: 조각이 아주 많으면(점 같은 둥근 인서트가 층마다 떠 있는 가는 고리를 남김 — 4단계 무작위 시험) tol 로는 줄지 않는다.
     * 넓은 조각부터 한도까지 그리고 나머지(작은 조각)는 3D 에서 뺀다. 2D 는 그대로 다 그린다.
     */
    private fun trim(plan: Plan, maxTri: Int): Plan {
        if (plan.triangles <= maxTri) return plan
        val kept = ArrayList<Loop>()
        var tris = 0
        for (l in plan.loops.sortedByDescending { it.area }) {
            if (tris + l.tris > maxTri) continue
            kept += l
            tris += l.tris
        }
        return Plan(plan.thetas, plan.section, kept, tris, 0)
    }

    /**
     * 척(설계 6.1 · 05): 몸통 원판(반지름 척 지름/2, 앞 모서리 모따기) + 턱 셋(θ = 0° · 120° · 240° 상자 — 척 면까지, 05 처럼 원판이 앞서 보이게 낮게) + 턱에 물린 소재(척 면 왼쪽 턱 길이만큼, 처음 지름 ·
     * 구멍 — 절개면 같이 자른다). 척 면 = 끝면 − 돌출, 턱 길이 = [jawLength](2D 의 척 그림 · 닿음 검사 자리와 같다). 몸통 · 턱은 늘 온전히.
     */
    fun chuck(spec: StockSpec, segments: Int, section: Section): Mesh {
        val buf = MeshBuf()
        val face = spec.faceZ - spec.length
        val jaw = jawLength(spec)
        val big = spec.chuckDia / 2
        val body = 0.35 * big
        val x1 = face - jaw
        val x0 = x1 - body
        val c = 0.05 * big
        val full = thetas(Section.FULL, segments)
        revolve(buf, loopOf(listOf(Pt(x0, 0.0), Pt(x1, 0.0)), listOf(Pt(x0, big), Pt(x1 - c, big), Pt(x1, big - c))), full, null, Mesh.CHUCK)
        val rs = spec.dia / 2
        val span = max(0.15 * big, 0.55 * (0.92 * big - rs))
        val hw = 0.07 * big
        for (k in 0 until 3) box(buf, k * TWO_PI / 3, rs, rs + span, -hw, hw, face - jaw, face, Mesh.JAW)
        val bore = max(0.0, spec.boreDia / 2)
        if (rs > bore) {
            val th = thetas(section, segments)
            revolve(buf, loopOf(listOf(Pt(face - jaw, bore), Pt(face, bore)), listOf(Pt(face - jaw, rs), Pt(face, rs))), th, section.takeIf { it == Section.QUARTER }, Mesh.PART)
        }
        return buf.mesh()
    }

    /** 닫힌 메시의 부피(mm³ — 발산 정리). 테스트 · 골든용 */
    fun volume(m: Mesh): Double {
        var v = 0.0
        val p = m.pos
        val t = m.tri
        for (k in 0 until m.triangles) {
            val a = 3 * t[3 * k]
            val b = 3 * t[3 * k + 1]
            val c = 3 * t[3 * k + 2]
            val ax = p[a].toDouble(); val ay = p[a + 1].toDouble(); val az = p[a + 2].toDouble()
            val bx = p[b].toDouble(); val by = p[b + 1].toDouble(); val bz = p[b + 2].toDouble()
            val cx = p[c].toDouble(); val cy = p[c + 1].toDouble(); val cz = p[c + 2].toDouble()
            v += ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)
        }
        return v / 6
    }

    // ---------------------------------------------------------------- 계획(삼각형 수를 먼저 센다)

    private class Plan(val thetas: DoubleArray, val section: Section, val loops: List<Loop>, val triangles: Int, val threadTriangles: Int)

    /** 조각 하나의 고리: rows[0 until nb] = 아래 사슬(z 증가), 나머지 = 위 사슬(z 감소) */
    private class Loop(val rows: List<Row>, val nb: Int) {
        /** 이 고리의 삼각형 수 · 단면 넓이(mm²) — [trim] 이 쓴다 */
        var tris = 0
        var area = 0.0
    }

    /** 단면 사슬(z 증가) — 반경을 z 로 잇는다 */
    private class Chain(val z: DoubleArray, val r: DoubleArray) {
        fun at(q: Double): Double {
            if (q <= z[0]) return r[0]
            val n = z.size
            if (q >= z[n - 1]) return r[n - 1]
            var lo = 0
            var hi = n - 1
            while (hi - lo > 1) {
                val mid = (lo + hi) ushr 1
                if (z[mid] <= q) lo = mid else hi = mid
            }
            val dz = z[hi] - z[lo]
            return if (dz <= 0) r[hi] else r[lo] + (r[hi] - r[lo]) * ((q - z[lo]) / dz)
        }
    }

    /** 나사 구간 [ta, tb] — [depth] = 구간 가운데의 골 깊이(산 모서리 줄 자리) */
    private class Helix(th: ThreadFeature, val ta: Double, val tb: Double, val depth: Double) {
        val lead = th.lead
        val half = tan(th.angleDeg.coerceIn(1.0, 179.0) / 2 * PI / 180)
        val outer = th.outer
        private val a = th.root.start
        private val b = th.root.end
        val zlo = min(a.z, b.z)
        val zhi = max(a.z, b.z)

        /** θ = 0 의 첫 골 가운데(2D 톱니와 같은 자리 — 골 선 위끝에서 리드/2 안쪽) */
        val p0 = zhi - lead / 2

        fun root(z: Double): Double {
            if (abs(b.z - a.z) < 1e-12) return a.x
            return a.x + (b.x - a.x) * ((z - a.z) / (b.z - a.z)).coerceIn(0.0, 1.0)
        }

        /** θ 에서 z 의 골 V 반경(가장 가까운 골 — 골 가운데가 패스 Z 범위 안인 것만). 없으면 NaN */
        fun groove(z: Double, theta: Double): Double {
            val ph = p0 + lead * theta / TWO_PI
            val k0 = ((ph - z) / lead).roundToInt()
            var best = Double.POSITIVE_INFINITY
            for (k in k0 - 1..k0 + 1) {
                val c = ph - k * lead
                if (c < zlo - 1e-9 || c > zhi + 1e-9) continue
                best = min(best, abs(z - c))
            }
            if (best == Double.POSITIVE_INFINITY) return Double.NaN
            return if (outer) root(z) + best / half else root(z) - best / half
        }
    }

    /** 고리의 한 줄: 보통 줄은 z = u 고리, 나선 줄은 z = u + 리드 · θ/2π(구간에서 자름) */
    private class Row(val u: Double, val chain: Chain, val helix: Helix?)

    private fun thetas(section: Section, segments: Int): DoubleArray = when (section) {
        Section.FULL -> DoubleArray(segments + 1) { TWO_PI * it / segments }
        Section.QUARTER -> {
            val n = max(3, (segments * 3 + 2) / 4)
            DoubleArray(n + 1) { PI / 2 + 1.5 * PI * it / n }
        }
    }

    private fun plan(p: StockProfile, section: Section, segments: Int, tol: Double, fill: Int, threads: Boolean): Plan {
        val th = thetas(section, segments)
        val strip = 2 * (th.size - 1)
        val loops = ArrayList<Loop>()
        var tris = 0
        var thTris = 0
        for (piece in p.pieces) {
            if (piece.bottom.size < 2 || piece.top.size < 2) continue
            val bot = chainOf(simplify(piece.bottom, tol))
            val top = chainOf(simplify(piece.top, tol))
            val zL = top.z[0]
            val zR = top.z[top.z.size - 1]
            val outer = if (threads) helices(p.threads.filter { it.outer }, top, zL, zR, inner = false) else emptyList()
            val inner = if (threads) helices(p.threads.filter { !it.outer }, bot, zL, zR, inner = true) else emptyList()
            val bRows = rows(bot, inner, fill)
            val tRows = rows(top, outer, fill)
            val loop = Loop(bRows + tRows.asReversed(), bRows.size)
            loops += loop
            // 축 위 사슬의 띠는 돌리지 않는다
            var n = 0
            for (i in loop.rows.indices) {
                val a = loop.rows[i]
                val b = loop.rows[(i + 1) % loop.rows.size]
                if (onAxis(a) && onAxis(b)) continue
                n++
                if (a.helix != null || b.helix != null) thTris += strip
            }
            loop.tris = n * strip + if (section == Section.QUARTER) 2 * 2 * loop.rows.size else 0
            loop.area = areaOf(piece.outline)
            tris += loop.tris
        }
        return Plan(th, section, loops, tris, thTris)
    }

    private fun onAxis(r: Row) = r.helix == null && r.chain.at(r.u) <= AXIS

    /** 단면 다각형 넓이(신발끈) */
    private fun areaOf(o: List<Pt>): Double {
        var a = 0.0
        for (i in o.indices) {
            val p = o[i]
            val q = o[(i + 1) % o.size]
            a += p.z * q.x - q.z * p.x
        }
        return abs(a) / 2
    }

    private fun chainOf(pts: List<Pt>) = Chain(DoubleArray(pts.size) { pts[it].z }, DoubleArray(pts.size) { pts[it].x })

    /** 이 사슬에 걸리는 나사 구간들(z 순, 겹치면 뒤의 것은 뺀다) */
    private fun helices(ths: List<ThreadFeature>, ch: Chain, zL: Double, zR: Double, inner: Boolean): List<Helix> {
        val out = ArrayList<Helix>()
        for (t in ths) {
            if (t.lead <= 1e-6) continue
            val zlo = min(t.root.start.z, t.root.end.z)
            val zhi = max(t.root.start.z, t.root.end.z)
            val probe = Helix(t, zL, zR, 0.0)
            val zm = ((zlo + zhi) / 2).coerceIn(zL, zR)
            val base = ch.at(zm)
            if (inner && base <= AXIS) continue
            val depth = if (t.outer) base - probe.root(zm) else probe.root(zm) - base
            if (depth <= 1e-6) continue
            val margin = t.lead + depth * probe.half
            val ta = max(zL, zlo - margin)
            val tb = min(zR, zhi + margin)
            if (tb - ta < 1e-6) continue
            if (out.any { ta < it.tb && tb > it.ta }) continue
            out += Helix(t, ta, tb, depth)
        }
        return out.sortedBy { it.ta }
    }

    /** 사슬의 줄(z 증가): 나사 구간 밖 꼭짓점은 보통 줄, 구간은 나선 줄 */
    private fun rows(ch: Chain, hs: List<Helix>, fill: Int): List<Row> {
        val out = ArrayList<Row>()
        var next = 0
        for (i in ch.z.indices) {
            val z = ch.z[i]
            while (next < hs.size && hs[next].ta < z - 1e-9) helixRows(out, ch, hs[next++], fill)
            // 구간 안 · 경계의 꼭짓점은 나선 줄이 맡는다(경계에 보통 줄을 두면 끝면이 골을 덮는다)
            if (hs.any { z >= it.ta - 1e-9 && z <= it.tb + 1e-9 }) continue
            out += Row(z, ch, null)
        }
        while (next < hs.size) helixRows(out, ch, hs[next++], fill)
        return out
    }

    /** 나선 줄: 리드마다 골(0) · 산 모서리(±w) · 산 가운데(리드/2) · 고른 줄(리드/fill) — 첫 줄 = ta 고리, 끝 줄 = tb 고리 */
    private fun helixRows(out: MutableList<Row>, ch: Chain, h: Helix, fill: Int) {
        val lead = h.lead
        val w = h.depth * h.half
        val offs = ArrayList<Double>()
        offs += 0.0
        offs += lead / 2
        if (w < lead / 2 - 1e-9) {
            offs += w
            offs += lead - w
        }
        for (j in 1 until fill) offs += lead * j / fill
        offs.sort()
        val uniq = ArrayList<Double>()
        for (o in offs) if (uniq.isEmpty() || o - uniq.last() > lead * 1e-4) uniq += o
        val u0 = h.ta - lead
        val u1 = h.tb
        out += Row(u0, ch, h)
        var m = floor((u0 - h.p0) / lead).toInt()
        while (true) {
            val base = h.p0 + m * lead
            if (base > u1) break
            for (o in uniq) {
                val u = base + o
                if (u > u0 + 1e-9 && u < u1 - 1e-9) out += Row(u, ch, h)
            }
            m++
        }
        out += Row(u1, ch, h)
    }

    // ---------------------------------------------------------------- 만들기

    private fun build(plan: Plan): Mesh {
        val buf = MeshBuf()
        val caps = plan.section.takeIf { it == Section.QUARTER }
        for (loop in plan.loops) revolve(buf, loop, plan.thetas, caps, Mesh.PART)
        return buf.mesh()
    }

    private fun loopOf(bottom: List<Pt>, top: List<Pt>): Loop {
        val b = chainOf(bottom)
        val t = chainOf(top)
        val rows = bottom.map { Row(it.z, b, null) } + top.asReversed().map { Row(it.z, t, null) }
        return Loop(rows, bottom.size)
    }

    private fun zOf(row: Row, th: Double): Double {
        val h = row.helix ?: return row.u
        return (row.u + h.lead * th / TWO_PI).coerceIn(h.ta, h.tb)
    }

    private fun rOf(row: Row, z: Double, th: Double): Double {
        val base = row.chain.at(z)
        val h = row.helix ?: return base
        val g = h.groove(z, th)
        if (g.isNaN()) return base
        return if (h.outer) max(0.0, min(base, g)) else max(base, g)
    }

    /**
     * 고리를 θ 로 돌린다. 띠(줄 i → i+1)마다 꼭짓점을 따로 두고 법선 = 둘레 방향 × 단면 방향(바깥쪽) — 이웃 띠와 35° 안이면 평균.
     * caps 가 있으면(1/4) 첫 · 끝 θ 단면을 채운다.
     */
    private fun revolve(buf: MeshBuf, loop: Loop, th: DoubleArray, caps: Section?, tag: Byte) {
        val rows = loop.rows
        val n = rows.size
        if (n < 2) return
        val nj = th.size
        val cs = DoubleArray(nj) { cos(th[it]) }
        val sn = DoubleArray(nj) { sin(th[it]) }
        val zz = Array(n) { i -> DoubleArray(nj) { j -> zOf(rows[i], th[j]) } }
        val rr = Array(n) { i -> DoubleArray(nj) { j -> rOf(rows[i], zz[i][j], th[j]) } }
        // 둘레 방향(∂P/∂θ): 보통 줄 (0, −r sinθ, r cosθ), 나선 줄은 z' = 리드/2π(자르지 않은 곳) · r' 는 수치 미분
        val tz = Array(n) { DoubleArray(nj) }
        val tr = Array(n) { DoubleArray(nj) }
        for (i in 0 until n) {
            if (rows[i].helix == null) continue
            val d = 1e-4
            for (j in 0 until nj) {
                val za = zOf(rows[i], th[j] - d)
                val zb = zOf(rows[i], th[j] + d)
                tz[i][j] = (zb - za) / (2 * d)
                tr[i][j] = (rOf(rows[i], zb, th[j] + d) - rOf(rows[i], za, th[j] - d)) / (2 * d)
            }
        }
        // 띠 법선(띠 s = 줄 s → s+1, 열 j)
        val sx = Array(n) { DoubleArray(nj) }
        val sy = Array(n) { DoubleArray(nj) }
        val sz = Array(n) { DoubleArray(nj) }
        val ok = Array(n) { BooleanArray(nj) }
        val live = BooleanArray(n)
        for (s in 0 until n) {
            val a = s
            val b = (s + 1) % n
            if (onAxis(rows[a]) && onAxis(rows[b])) continue
            var any = false
            for (j in 0 until nj) {
                val dz = zz[b][j] - zz[a][j]
                val dr = rr[b][j] - rr[a][j]
                if (abs(dz) + abs(dr) > 1e-12) any = true
                // 단면 방향 D(3D) · 둘레 방향 T(두 줄 평균)
                val dx3 = dz
                val dy3 = dr * cs[j]
                val dz3 = dr * sn[j]
                val r = (rr[a][j] + rr[b][j]) / 2
                val trr = (tr[a][j] + tr[b][j]) / 2
                val tx3 = (tz[a][j] + tz[b][j]) / 2
                val ty3 = trr * cs[j] - r * sn[j]
                val tz3 = trr * sn[j] + r * cs[j]
                var nx = ty3 * dz3 - tz3 * dy3
                var ny = tz3 * dx3 - tx3 * dz3
                var nz = tx3 * dy3 - ty3 * dx3
                // 바깥쪽 기준: 단면에서 반시계 고리의 오른쪽 (dr, −dz)
                val lx = dr
                val ly = -dz * cs[j]
                val lz = -dz * sn[j]
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                val ll = sqrt(lx * lx + ly * ly + lz * lz)
                if (len < 1e-12 * max(1.0, ll)) {
                    if (ll < 1e-15) continue
                    nx = lx; ny = ly; nz = lz
                } else if (nx * lx + ny * ly + nz * lz < 0) {
                    nx = -nx; ny = -ny; nz = -nz
                }
                val l2 = sqrt(nx * nx + ny * ny + nz * nz)
                sx[s][j] = nx / l2
                sy[s][j] = ny / l2
                sz[s][j] = nz / l2
                ok[s][j] = true
            }
            // 두 줄이 겹친 열(나선 줄이 구간 끝에서 잘린 곳)은 가까운 열의 법선을 빌린다 — 그 열에 붙은 삼각형은 넓이가 있다
            val first = ok[s].indexOfFirst { it }
            if (first < 0) continue
            var last = first
            for (j in 0 until nj) {
                if (ok[s][j]) {
                    last = j
                    continue
                }
                val from = if (j < first) first else last
                sx[s][j] = sx[s][from]
                sy[s][j] = sy[s][from]
                sz[s][j] = sz[s][from]
            }
            for (j in 0 until nj) ok[s][j] = true
            live[s] = any
        }
        fun smooth(s: Int, o: Int, j: Int, out: DoubleArray) {
            out[0] = sx[s][j]; out[1] = sy[s][j]; out[2] = sz[s][j]
            if (!live[o] || !ok[o][j]) return
            val d = sx[s][j] * sx[o][j] + sy[s][j] * sy[o][j] + sz[s][j] * sz[o][j]
            if (d < CREASE) return
            val x = sx[s][j] + sx[o][j]
            val y = sy[s][j] + sy[o][j]
            val z = sz[s][j] + sz[o][j]
            val l = sqrt(x * x + y * y + z * z)
            out[0] = x / l; out[1] = y / l; out[2] = z / l
        }
        val na = DoubleArray(3)
        val nb = DoubleArray(3)
        val ia = IntArray(nj)
        val ib = IntArray(nj)
        for (s in 0 until n) {
            if (!live[s]) continue
            val a = s
            val b = (s + 1) % n
            for (j in 0 until nj) {
                if (!ok[s][j]) {
                    ia[j] = -1
                    ib[j] = -1
                    continue
                }
                smooth(s, (s - 1 + n) % n, j, na)
                smooth(s, (s + 1) % n, j, nb)
                ia[j] = buf.v(zz[a][j], rr[a][j] * cs[j], rr[a][j] * sn[j], na[0], na[1], na[2])
                ib[j] = buf.v(zz[b][j], rr[b][j] * cs[j], rr[b][j] * sn[j], nb[0], nb[1], nb[2])
            }
            for (j in 0 until nj - 1) {
                if (ia[j] < 0 || ia[j + 1] < 0) continue
                buf.t(ia[j], ia[j + 1], ib[j + 1], tag)
                buf.t(ia[j], ib[j + 1], ib[j], tag)
            }
        }
        if (caps != null) {
            cap(buf, loop, zz, rr, 0, th[0], start = true)
            cap(buf, loop, zz, rr, nj - 1, th[nj - 1], start = false)
        }
    }

    /**
     * 1/4 단면 하나(θ 평면): 아래 · 위 사슬(그 θ 의 z · r — 둘 다 z 로 단조)을 z 순으로 합쳐 띠(사다리꼴)로 채운다.
     * 법선 = 뺀 쪽(첫 θ 면은 θ 가 작아지는 쪽, 끝 θ 면은 커지는 쪽).
     */
    private fun cap(buf: MeshBuf, loop: Loop, zz: Array<DoubleArray>, rr: Array<DoubleArray>, j: Int, theta: Double, start: Boolean) {
        val n = loop.rows.size
        val nb = loop.nb
        val nt = n - nb
        if (nb < 2 || nt < 2) return
        val bz = DoubleArray(nb) { zz[it][j] }
        val br = DoubleArray(nb) { rr[it][j] }
        val tz = DoubleArray(nt) { zz[n - 1 - it][j] }
        val tr = DoubleArray(nt) { rr[n - 1 - it][j] }
        val c = cos(theta)
        val s = sin(theta)
        val nx = 0.0
        val ny = if (start) s else -s
        val nz = if (start) -c else c
        fun interp(z: DoubleArray, r: DoubleArray, k: Int, q: Double): Double = when {
            k <= 0 -> r[0]
            k >= z.size -> r[z.size - 1]
            z[k] - z[k - 1] <= 1e-12 -> r[k - 1]
            else -> r[k - 1] + (r[k] - r[k - 1]) * ((q - z[k - 1]) / (z[k] - z[k - 1])).coerceIn(0.0, 1.0)
        }
        var i = 0
        var k = 0
        var prevB = -1
        var prevT = -1
        while (i < nb || k < nt) {
            val takeB = k >= nt || (i < nb && bz[i] <= tz[k])
            val z: Double
            val rb: Double
            val rt: Double
            if (takeB) {
                z = bz[i]
                rb = br[i]
                rt = interp(tz, tr, k, z)
                i++
            } else {
                z = tz[k]
                rt = tr[k]
                rb = interp(bz, br, i, z)
                k++
            }
            val vb = buf.v(z, rb * c, rb * s, nx, ny, nz)
            val vt = buf.v(z, rt * c, rt * s, nx, ny, nz)
            if (prevB >= 0) {
                buf.t(prevB, vb, vt, Mesh.SECTION)
                buf.t(prevB, vt, prevT, Mesh.SECTION)
            }
            prevB = vb
            prevT = vt
        }
    }

    /** 척 턱: θ 방향 상자(반경 r0~r1 · 접선 t0~t1 · 축 x0~x1), 면마다 평평한 법선 */
    private fun box(buf: MeshBuf, th: Double, r0: Double, r1: Double, t0: Double, t1: Double, x0: Double, x1: Double, tag: Byte) {
        val c = cos(th)
        val s = sin(th)
        fun quad(a: DoubleArray, b: DoubleArray, d: DoubleArray, e: DoubleArray, nx: Double, ny: Double, nz: Double) {
            val i0 = buf.v(a[0], a[1], a[2], nx, ny, nz)
            val i1 = buf.v(b[0], b[1], b[2], nx, ny, nz)
            val i2 = buf.v(d[0], d[1], d[2], nx, ny, nz)
            val i3 = buf.v(e[0], e[1], e[2], nx, ny, nz)
            buf.t(i0, i1, i2, tag)
            buf.t(i0, i2, i3, tag)
        }
        fun p(x: Double, r: Double, t: Double) = doubleArrayOf(x, r * c - t * s, r * s + t * c)
        quad(p(x0, r0, t0), p(x0, r1, t0), p(x0, r1, t1), p(x0, r0, t1), -1.0, 0.0, 0.0)
        quad(p(x1, r0, t0), p(x1, r1, t0), p(x1, r1, t1), p(x1, r0, t1), 1.0, 0.0, 0.0)
        quad(p(x0, r0, t0), p(x1, r0, t0), p(x1, r0, t1), p(x0, r0, t1), 0.0, -c, -s)
        quad(p(x0, r1, t0), p(x1, r1, t0), p(x1, r1, t1), p(x0, r1, t1), 0.0, c, s)
        quad(p(x0, r0, t0), p(x1, r0, t0), p(x1, r1, t0), p(x0, r1, t0), 0.0, s, -c)
        quad(p(x0, r0, t1), p(x1, r0, t1), p(x1, r1, t1), p(x0, r1, t1), 0.0, -s, c)
    }

    /** Douglas–Peucker(반복형, 양 끝 남김) */
    private fun simplify(pts: List<Pt>, tol: Double): List<Pt> {
        val n = pts.size
        if (n <= 2) return pts
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Long>()
        stack.addLast(n - 1L)
        while (stack.isNotEmpty()) {
            val e = stack.removeLast()
            val a = (e ushr 32).toInt()
            val b = (e and 0xFFFFFFFFL).toInt()
            if (b - a < 2) continue
            val pa = pts[a]
            val pb = pts[b]
            val dz = pb.z - pa.z
            val dr = pb.x - pa.x
            val len = sqrt(dz * dz + dr * dr)
            var worst = -1.0
            var at = -1
            for (k in a + 1 until b) {
                val q = pts[k]
                val d = if (len < 1e-15) sqrt((q.z - pa.z) * (q.z - pa.z) + (q.x - pa.x) * (q.x - pa.x))
                else abs((q.z - pa.z) * dr - (q.x - pa.x) * dz) / len
                if (d > worst) {
                    worst = d
                    at = k
                }
            }
            if (worst > tol) {
                keep[at] = true
                stack.addLast((a.toLong() shl 32) or at.toLong())
                stack.addLast((at.toLong() shl 32) or b.toLong())
            }
        }
        val out = ArrayList<Pt>()
        for (k in 0 until n) if (keep[k]) out += pts[k]
        return out
    }

    /** 늘어나는 꼭짓점 · 삼각형 버퍼. [t] 는 넓이 0 을 버리고 꼭짓점 법선 쪽에서 보아 반시계가 되게 돌린다 */
    private class MeshBuf {
        var pos = FloatArray(3 * 1024)
        var nrm = FloatArray(3 * 1024)
        var nv = 0
        var tri = IntArray(3 * 1024)
        var tag = ByteArray(1024)
        var nt = 0

        fun v(x: Double, y: Double, z: Double, nx: Double, ny: Double, nz: Double): Int {
            if (3 * nv + 3 > pos.size) {
                pos = pos.copyOf(pos.size * 2)
                nrm = nrm.copyOf(nrm.size * 2)
            }
            val k = 3 * nv
            pos[k] = x.toFloat()
            pos[k + 1] = y.toFloat()
            pos[k + 2] = z.toFloat()
            nrm[k] = nx.toFloat()
            nrm[k + 1] = ny.toFloat()
            nrm[k + 2] = nz.toFloat()
            return nv++
        }

        fun t(a: Int, b: Int, c: Int, tg: Byte) {
            val pa = 3 * a
            val pb = 3 * b
            val pc = 3 * c
            val ux = (pos[pb] - pos[pa]).toDouble()
            val uy = (pos[pb + 1] - pos[pa + 1]).toDouble()
            val uz = (pos[pb + 2] - pos[pa + 2]).toDouble()
            val wx = (pos[pc] - pos[pa]).toDouble()
            val wy = (pos[pc + 1] - pos[pa + 1]).toDouble()
            val wz = (pos[pc + 2] - pos[pa + 2]).toDouble()
            val gx = uy * wz - uz * wy
            val gy = uz * wx - ux * wz
            val gz = ux * wy - uy * wx
            if (gx * gx + gy * gy + gz * gz < 1e-16) return
            val sx = (nrm[pa] + nrm[pb] + nrm[pc]).toDouble()
            val sy = (nrm[pa + 1] + nrm[pb + 1] + nrm[pc + 1]).toDouble()
            val sz = (nrm[pa + 2] + nrm[pb + 2] + nrm[pc + 2]).toDouble()
            val flip = gx * sx + gy * sy + gz * sz < 0
            if (3 * nt + 3 > tri.size) {
                tri = tri.copyOf(tri.size * 2)
                tag = tag.copyOf(tag.size * 2)
            }
            tri[3 * nt] = a
            tri[3 * nt + 1] = if (flip) c else b
            tri[3 * nt + 2] = if (flip) b else c
            tag[nt] = tg
            nt++
        }

        fun mesh() = Mesh(pos.copyOf(3 * nv), nrm.copyOf(3 * nv), tri.copyOf(3 * nt), tag.copyOf(nt))
    }
}
