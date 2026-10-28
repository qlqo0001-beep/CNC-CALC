package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.wrap2Pi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * 사이클 타임(`docs/simulator-design.md` 4절). 시간 단위 초, 길이 mm, 속도 mm/min, 회전수 rpm. x 는 반경.
 * 축 가감속 · 주축 가감속 · 블록 처리 시간은 넣지 않는다(4.6 — 실제보다 짧게 나온다).
 */
internal object GTime {
    fun length(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> s.radius * s.sweepRad()
    }

    /**
     * 급송 시간: 축마다 따로 tX = |Δx 반경| / vX, tZ = |Δz| / vZ, 시간 = 큰 쪽(꺾인 경로 · 직선 모두 — S 2-23~2-24 · H 32).
     */
    fun rapidSec(a: Pt, b: Pt, m: MachineSetup): Double = max(abs(b.x - a.x) / m.rapidX, abs(b.z - a.z) / m.rapidZ) * 60

    /**
     * 꺾인 급송(DOGLEG)의 꺾이는 점: 두 축이 같이 출발해 빨리 끝나는 축이 먼저 서는 점. 곧은 경로(한 축만 움직임 · 두 축이 같이 끝남 ·
     * LINEAR 설정)면 null. H 32 의 "45° 로 가다 평행" 은 두 속도가 같을 때의 모습이다.
     */
    fun rapidBreak(a: Pt, b: Pt, m: MachineSetup): Pt? {
        if (m.rapidPath == RapidPath.LINEAR) return null
        val dz = b.z - a.z
        val dx = b.x - a.x
        if (abs(dz) <= 1e-9 || abs(dx) <= 1e-9) return null
        val tX = abs(dx) / m.rapidX
        val tZ = abs(dz) / m.rapidZ
        if (abs(tX - tZ) <= 1e-12 * max(tX, tZ)) return null
        return if (tX < tZ) Pt(a.z + sign(dz) * m.rapidZ * tX, b.x) else Pt(b.z, a.x + sign(dx) * m.rapidX * tZ)
    }

    /** G96 회전수 n(x) = min(1000·Vc / (2π|x|), nMax) — S 3-81: 지금 X 값을 공작물 지름으로 봄 */
    fun cssRpm(x: Double, vc: Double, nMax: Double): Double {
        val ax = abs(x)
        return if (ax <= 1e-12) nMax else min(1000 * vc / (2 * PI * ax), nMax)
    }

    /**
     * 이송 시간(초). [tip] = 실제 인선 경로(보정 뒤 — H 33: F 는 실제 경로를 따라), [f] = mm/rev(perRev) 또는 mm/min.
     * G97: [s] rpm 을 [nMax](G50 S · 기계 최대 중 작은 것)로 자름. G96([css] = Vc m/min): t = (1/F) ∫ ds / n(x(s)),
     * 1/n(x) = max(2π|x| / (1000·Vc), 1/nMax). x = ±x_c(= 1000·Vc / (2π·nMax)) 와 0 에서 나눠 토막마다 닫힌 식으로 적분한다 —
     * 직선은 x 가 1차라 |x| 의 평균 × 길이, 원호는 x = c.x + ρ sin φ 라 ∫ x ρ dφ = ρ(c.x φ − ρ cos φ).
     * 부르는 쪽이 n > 0 · f > 0 을 확인한다.
     */
    fun feedSec(tip: Seg, f: Double, perRev: Boolean, css: Double?, s: Double?, nMax: Double): Double {
        val len = length(tip)
        if (len <= 0) return 0.0
        if (!perRev) return len / f * 60
        if (css == null) return len / (f * min(s!!, nMax)) * 60
        val xc = 1000 * css / (2 * PI * nMax)
        val k = 2 * PI / (1000 * css)
        val sum = when (tip) {
            is Line -> lineSum(tip, xc, k, nMax)
            is Arc -> arcSum(tip, xc, k, nMax)
        }
        return sum / f * 60
    }

    /** ∫ ds / n(x) (분·회전 → 나누기 F 앞) */
    private fun lineSum(l: Line, xc: Double, k: Double, nMax: Double): Double {
        val len = (l.end - l.start).length
        val x0 = l.start.x
        val x1 = l.end.x
        val us = mutableListOf(0.0, 1.0)
        if (abs(x1 - x0) > 1e-15) for (v in listOf(xc, -xc, 0.0)) {
            val u = (v - x0) / (x1 - x0)
            if (u > 0 && u < 1) us += u
        }
        us.sort()
        var sum = 0.0
        for ((ua, ub) in us.zipWithNext()) {
            val xa = x0 + (x1 - x0) * ua
            val xb = x0 + (x1 - x0) * ub
            val lp = len * (ub - ua)
            sum += if (abs((xa + xb) / 2) >= xc) k * lp * abs(xa + xb) / 2 else lp / nMax
        }
        return sum
    }

    private fun arcSum(a: Arc, xc: Double, k: Double, nMax: Double): Double {
        val c = a.center
        val rho = a.radius
        val sweep = a.sweepRad()
        val dir = if (a.cw) -1.0 else 1.0
        val phi0 = angleOf(a.start - c)
        fun phi(u: Double) = phi0 + dir * u
        val us = mutableListOf(0.0, sweep)
        for (v in listOf(xc, -xc, 0.0)) {
            val sv = (v - c.x) / rho
            if (abs(sv) > 1) continue
            val p = asin(sv)
            for (q in listOf(p, PI - p)) {
                val u = wrap2Pi(dir * (q - phi0))
                if (u > 0 && u < sweep) us += u
            }
        }
        us.sort()
        fun g(ph: Double) = rho * (c.x * ph - rho * cos(ph))
        var sum = 0.0
        for ((ua, ub) in us.zipWithNext()) {
            val xm = c.x + rho * sin(phi((ua + ub) / 2))
            sum += if (abs(xm) >= xc) k * abs(g(phi(ub)) - g(phi(ua))) else rho * (ub - ua) / nMax
        }
        return sum
    }
}
