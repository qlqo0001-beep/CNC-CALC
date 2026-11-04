package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.ncWords
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 비교용 기준 구현 — 앱의 `verify/VerifyFlipTest.simG4x(typeB = false)`(테이퍼 검증의 기계 보정 흉내)를 그대로 옮겨 왔다.
 * 그 함수는 private 이라 부를 수 없고, 테이퍼 파일은 이 가지에서 고치지 않기로 해서 옮겼다(결정 목록).
 * 구현의 Pt · Intersect · NoseComp · GComp 를 쓰지 않는다. 고친 것 둘: 결과에서 G40 접근 줄(pre)은 뺐고,
 * '접해서 이어짐' 의 꺾임 한도를 1e-9 대신 시뮬레이터와 같은 min(1e-3, 0.0005/r)(NC 0.001 반올림 자국 — GComp 주석)로 했다.
 * 바깥 꺾임 > 90°(dt < 0)는 원본처럼 4점 꺾은선을 낸다 — 시뮬레이터는 그 자리에서 멈추므로 비교에서 빠진다.
 */
internal object RefG4x {
    data class P(val z: Double, val x: Double) {
        operator fun plus(o: P) = P(z + o.z, x + o.x)
        operator fun minus(o: P) = P(z - o.z, x - o.x)
        operator fun times(k: Double) = P(z * k, x * k)
        infix fun dot(o: P) = z * o.z + x * o.x
        infix fun cross(o: P) = z * o.x - x * o.z
        val len: Double get() = hypot(z, x)
        fun unit() = this * (1 / len)
        fun left() = P(-x, z)
    }

    sealed class Pc {
        abstract val a: P
        abstract val b: P
    }

    class Ln(override val a: P, override val b: P) : Pc()

    class Ac(override val a: P, override val b: P, val c: P, val cw: Boolean) : Pc()

    class Blk(val g: Int, val comp: Int?, val p: P, val rr: Double?)

    /**
     * 낱말 G0~3 · G40~42 · X(직경) · Z · R · F(버림)만(빈칸이 있든 없든 — `ncWords`). 빠진 축 · 이동 G 는 앞 줄 값.
     * 좌표가 아직 없는 처음 보정 혼자 줄(회사 꼴 `G42` — 계획 단계 4)은 다음 이동 줄의 보정 G 로 붙이고, 그 앞에 시뮬레이터 처음 공구 자리(기계 원점, G0)를 둔다
     */
    fun parse(lines: List<String>): List<Blk> {
        var z: Double? = null
        var x: Double? = null
        var g = -1
        var pending: Int? = null
        val out = mutableListOf<Blk>()
        for (line in lines) {
            var comp: Int? = null
            var rr: Double? = null
            for (tok in ncWords(line)) {
                val v = tok.substring(1)
                when (tok[0]) {
                    'G' -> when (val n = v.toInt()) {
                        0, 1, 2, 3 -> g = n
                        40, 41, 42 -> comp = n
                        else -> error("모르는 G$n: $line")
                    }
                    'X' -> x = v.toDouble() / 2
                    'Z' -> z = v.toDouble()
                    'R' -> rr = v.toDouble()
                    'F' -> {}
                    else -> error("모르는 낱말 $tok: $line")
                }
            }
            if (z == null || x == null) {
                pending = comp ?: error("좌표 없는 줄: $line")
                continue
            }
            if (pending != null) {
                val home = MachineSetup().home
                out += Blk(0, null, P(home.z, home.x), null)
                comp = pending
                pending = null
            }
            out += Blk(g, comp, P(z!!, x!!), rr)
        }
        return out
    }

    /** a → b, 반지름 R, cw = G02 인 원호(180° 이하)의 중심 */
    fun arcCenter(a: P, b: P, rr: Double, cw: Boolean): P {
        val d = b - a
        val half = d.len / 2
        require(rr >= half - 2e-3) { "R $rr 이 현의 반 $half 보다 작다" }
        val h = sqrt(max(0.0, rr * rr - half * half))
        val m = (a + b) * 0.5
        val nl = d.unit().left()
        return if (cw) m - nl * h else m + nl * h
    }

    private class Ent(val s: P, val e: P, val c: P?, val cw: Boolean) {
        fun tanAt(p: P): P = if (c == null) (e - s).unit() else (p - c).unit().left() * (if (cw) -1.0 else 1.0)
        fun tanS() = tanAt(s)
        fun tanE() = tanAt(e)
    }

    /**
     * 기계 보정 흉내. all = G40 접근 줄들 · G41/G42 시작(직선) · 보정 블록 · G40 취소(직선) · (G40 상태 줄들) — 노즈 중심 경로(시작 블록부터).
     * 이동 없는 G40 한 줄이면 앞 블록 끝 법선에 서고 다음 이동에서 벡터를 지운다(FF 5.4.4 그림 5.4.4(e) 204쪽 형식 A·B, F0 14.3.4 228쪽 — 2026-10-28 더함).
     * 오목 원호가 R < r 이면 null
     */
    fun run(all: List<Blk>, tv: P, r: Double): List<Pc>? {
        val k0 = all.indexOfFirst { it.comp != null }
        val kc = (k0 + 1 until all.size).first { all[it].comp == 40 }
        val alone = (all[kc].p - all[kc - 1].p).len < 1e-9
        val b = all.subList(k0 - 1, if (alone) kc else kc + 1)
        val side = if (b[1].comp == 41) 1.0 else -1.0
        val ents = (1 until b.size).map { k ->
            val a = b[k - 1].p
            val e = b[k].p
            if (b[k].g == 2 || b[k].g == 3) Ent(a, e, arcCenter(a, e, b[k].rr!!, b[k].g == 2), b[k].g == 2) else Ent(a, e, null, false)
        }
        val m = if (alone) ents.size else ents.size - 1 // ents[m] = 취소 블록(이동 없는 G40 이면 없음)
        fun n(t: P) = t.left() * side
        fun rho(en: Ent): Double {
            val c = en.c!!
            val toward = (n(en.tanS()) dot (c - en.s)) > 0
            val rr = (en.s - c).len
            return if (toward) rr - r else rr + r
        }
        for (k in 1 until m) if (ents[k].c != null && rho(ents[k]) < -1e-9) return null

        fun junction(ia: Int): List<P> {
            val a = ents[ia]
            val bb = ents[ia + 1]
            val e = a.e
            val ta = a.tanE()
            val tb = bb.tanS()
            val na = n(ta)
            val nb = n(tb)
            val cr = ta cross tb
            val dt = ta dot tb
            if (abs(cr) < minOf(1e-3, 5e-4 / max(r, 1e-9)) && dt > 0) return listOf(e + na * r)
            val inner = cr * side > 0
            val startup = ia == 0
            val cancel = !alone && ia + 1 == m
            if (inner) {
                return listOf(meet(a, bb, na, nb, e, r, if (cancel) null else bb.c?.let { rho(bb) }, if (startup) null else a.c?.let { rho(a) }))
            }
            if (startup) return listOf(e + nb * r)
            if (cancel) return listOf(e + na * r)
            return if (dt >= 0) {
                listOf(meet(a, bb, na, nb, e, r, if (cancel) null else bb.c?.let { rho(bb) }, if (startup) null else a.c?.let { rho(a) }))
            } else {
                listOf(e + na * r, e + na * r + ta * r, e + nb * r - tb * r, e + nb * r)
            }
        }

        val out = mutableListOf<Pc>()
        var cur = b[0].p - tv
        for (k in 0 until m) {
            val en = ents[k]
            // 이동 없는 G40 앞 블록의 끝 = 그 블록 끝 법선
            val j = if (alone && k == m - 1) listOf(en.e + n(en.tanE()) * r) else junction(k)
            out += if (k == 0 || en.c == null) Ln(cur, j.first()) else Ac(cur, j.first(), en.c, en.cw)
            for (q in j.zipWithNext()) out += Ln(q.first, q.second)
            cur = j.last()
        }
        // 취소 블록(이동 없는 G40 이면 그 다음 이동)은 보정 자리에서 끝점(G40 상태)으로 곧게, 그 뒤 G40 상태 줄들
        for (k in kc + (if (alone) 1 else 0) until all.size) {
            if (k > kc && (all[k].p - all[k - 1].p).len < 1e-9) continue
            out += Ln(cur, all[k].p - tv)
            cur = all[k].p - tv
        }
        return out
    }

    private fun meet(a: Ent, b: Ent, na: P, nb: P, e: P, r: Double, rhoB: Double?, rhoA: Double?): P {
        val guess = e + (na + nb) * (r / 2)
        val pa = e + na * r
        val pb = e + nb * r
        val ta = a.tanE()
        val tb = b.tanS()
        val cands: List<P> = when {
            rhoA == null && rhoB == null -> listOfNotNull(lineMeet(pa, ta, pb, tb))
            rhoA == null -> lineCircle(pa, ta, b.c!!, rhoB!!)
            rhoB == null -> lineCircle(pb, tb, a.c!!, rhoA)
            else -> circleCircle(a.c!!, rhoA, b.c!!, rhoB)
        }
        return cands.minByOrNull { (it - guess).len } ?: (e + na * r)
    }

    private fun lineMeet(p1: P, d1: P, p2: P, d2: P): P? {
        val den = d1 cross d2
        if (abs(den) < 1e-12) return null
        return p1 + d1 * (((p2 - p1) cross d2) / den)
    }

    private fun lineCircle(p: P, d: P, c: P, rr: Double): List<P> {
        val u = d.unit()
        val w = p - c
        val bq = w dot u
        val disc = bq * bq - ((w dot w) - rr * rr)
        if (disc < -1e-12) return emptyList()
        val s = sqrt(max(0.0, disc))
        return listOf(p + u * (-bq - s), p + u * (-bq + s))
    }

    private fun circleCircle(c1: P, r1: Double, c2: P, r2: Double): List<P> {
        val d = (c2 - c1).len
        if (d < 1e-12) return emptyList()
        val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
        val h2 = r1 * r1 - a * a
        if (h2 < -1e-12) return emptyList()
        val u = (c2 - c1).unit()
        val m = c1 + u * a
        val h = sqrt(max(0.0, h2))
        return listOf(m + u.left() * h, m - u.left() * h)
    }

    fun ang(v: P) = atan2(v.x, v.z)

    fun wrap(a: Double): Double {
        val t = a % (2 * PI)
        return if (t < 0) t + 2 * PI else t
    }
}
