package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Intersect
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.wrapPi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 제어기 노즈R 보정 모형(형식 A — `docs/simulator-design.md` 3절, `calc-design.md` 테이퍼 '진입점과 NC').
 * 블록을 하나씩 받아 한 블록 늦게 노즈 중심 경로를 낸다(H 58: 보정은 한두 블록 앞을 읽는다). 이 클래스는 기하만 —
 * 블록 순서 · 상태는 [GSim] 이 쥔다.
 *
 * - 보정선: 직선은 공구 쪽(G41 = 진행 왼쪽, H 59~60)으로 r 평행, 원호는 같은 중심 R ± r(공구가 중심 쪽이면 −).
 * - 이음점(블록 A 끝 = B 처음 e, 접선 tA · tB, 공구 쪽 법선 nA · nB):
 *   접해서 이어짐 → e + nA·r · 안쪽 모서리 → 두 보정선의 교점(e + (nA + nB)·r/2 에 가까운 것) ·
 *   바깥 꺾임 ≤ 90° → 두 보정선을 늘인 교점 · 바깥 꺾임 > 90° → 지원 안 함(COMP_ACUTE_OUTER — 제어기가 끼우는 토막을 출처로 확인 못 함).
 *   다만 90° 를 아주 조금 넘고 넘은 몫(끝 접선을 늘인 교점과 형식 C 가 끼우는 토막의 차이)이 0.001 mm 안이면 90° 처럼 교점([OUTER_SIN] · [OUTER_TOL] — 설계 22절).
 *   시작 블록 끝이 바깥이면 e + nB·r, 취소 블록 앞이 바깥이면 e + nA·r(형식 A).
 * - 보정 중 G41 ↔ G42 바꿈: 두 쪽 보정선의 교점, 없으면 B 처음 법선([switchTo] — FF 5.4.3).
 * - 간섭: 이음점 사이 보정 토막이 거꾸로 가면 COMP_INTERFERENCE(H 61), 오목 원호 R < r 은 COMP_ARC_SMALL.
 * 같은 규칙의 독립 구현이 테스트의 `RefG4x`(앱 `VerifyFlipTest.simG4x(typeB = false)` 를 옮긴 것)다.
 */
internal class GComp(val r: Double, val tip: Int, val side: Side) {
    /** 노즈 중심 → 가상 인선 */
    val tv: Pt = NoseComp.tipVector(tip, r)
    private val sgn = if (side == Side.G41) 1.0 else -1.0

    /**
     * 접해서 이어짐으로 보는 꺾임(sin θ). NC 글은 0.001 로 반올림돼 도면에서 접하던 이음(모서리 R · 접선 원호)이 1e-4 ~ 1e-3 rad 틀어진다.
     * 그 각에서 보정선의 교점은 거의 접한 직선 · 원이 만나는 자리라 0.001 반올림이 수 µm 로 커지고(√ 증폭) 앞뒤가 뒤집혀 간섭으로 보인다.
     * 노즈가 r·θ ≤ 0.0005 mm(입력 최소 단위의 반) 안에서만 달라지는 각까지는 접한 것으로 본다 — 1e-3 rad 를 넘지 않게.
     */
    private val smooth = min(1e-3, 5e-4 / max(r, 1e-9))

    sealed interface Res

    /** [outer] = 시작 · 취소가 바깥 모서리이고 형식 A · B 의 노즈가 0.0005 mm 넘게 다름(INFO COMP_TYPE_A) */
    data class Ok(val p: Pt, val outer: Boolean) : Res
    data class Bad(val code: IssueCode) : Res

    /** 진행 방향 t 의 공구 쪽 단위 법선 */
    fun normal(t: Pt): Pt = t.left() * sgn

    /** 원호 보정선 반지름 R ± r. 공구가 중심 쪽(오목)이면 R − r */
    fun rho(a: Arc): Double {
        val toward = (normal(tanAt(a, a.start)) dot (a.center - a.start)) > 0
        return if (toward) a.radius - r else a.radius + r
    }

    /** 보정 중 원호가 받아들일 수 있나(R < r 이면 COMP_ARC_SMALL) */
    fun arcOk(a: Arc): Boolean = rho(a) >= -1e-9

    /** 마지막 보정 블록을 다음 블록 없이 끝낼 때(취소 없이 끝 · 이동 없는 취소)의 노즈 자리 = 끝 법선 */
    fun openEnd(a: Seg): Pt = a.end + normal(tanAt(a, a.end)) * r

    /**
     * A 끝 = B 처음 이음점의 노즈 중심. [startup] = A 가 시작 블록, [cancel] = B 가 취소 블록(둘 다 직선).
     */
    fun junction(a: Seg, b: Seg, startup: Boolean, cancel: Boolean): Res {
        val e = a.end
        val ta = tanAt(a, e)
        val tb = tanAt(b, b.start)
        val na = normal(ta)
        val nb = normal(tb)
        val cr = ta cross tb
        val dt = ta dot tb
        if (abs(cr) < smooth && dt > 0) return Ok(e + na * r, false)
        if (abs(cr) < 1e-9) return Bad(IssueCode.COMP_ACUTE_OUTER) // 제자리로 되돌아감 = 180° 바깥 꺾임
        val guess = e + (na + nb) * (r / 2)
        if (cr * sgn > 0) {
            // 안쪽 모서리: 교점. 없으면 보정선끼리 못 만남 = 간섭
            val p = meet(a, b, e, na, nb, guess) ?: return Bad(IssueCode.COMP_INTERFERENCE)
            return Ok(p, false)
        }
        // 시작 · 취소가 바깥 모서리면 형식 A(법선)와 B(교점)의 노즈가 r·tan(θ/2) 만큼 다르다 — 그 차이가 입력 최소 단위의 반(0.0005 mm) 안이면
        // (계산기 글의 '한 직선' 시작 · 취소가 0.001 반올림으로 아주 조금 꺾인 것) 형식 B 기계도 같은 길이라 알리지 않는다(검증 2026-10-29)
        val typeAB = r * abs(cr) / (1 + dt) > REVERSE
        if (startup) return Ok(e + nb * r, typeAB)
        if (cancel) return Ok(e + na * r, typeAB)
        if (dt < -OUTER_SIN) return Bad(IssueCode.COMP_ACUTE_OUTER)
        val p = meet(a, b, e, na, nb, guess) ?: return Bad(IssueCode.COMP_ACUTE_OUTER)
        // 90° 를 아주 조금 넘음(꼭 90° 도면의 소수 잡음 · 계산기 글의 받는 띠): 넘은 몫 — 끝 접선을 늘인 교점과 형식 C 가 끼우는 토막
        // e + nA·r + tA·r → e + nB·r − tB·r 의 두 끝 사이 거리 r·(tan(θ/2) − 1) = r·((1 − tA·tB)/|tA × tB| − 1) — 이 OUTER_TOL 안일 때만 교점으로.
        // 원호는 보정선이 휘는 몫(≈ r²/2ρ)만큼 교점이 더 떨어지지만 꼭 90° 에서도 생기는 볼록 꼭짓점 밖 공기라 따지지 않는다(설계 22.4)
        if (dt < 0 && r * ((1 - dt) / abs(cr) - 1) > OUTER_TOL) return Bad(IssueCode.COMP_ACUTE_OUTER)
        return Ok(p, false)
    }

    /**
     * 보정 중 G41 ↔ G42 바꿈의 이음점(FF 5.4.3 189~191쪽 'Change in the offset direction in the offset mode'): A 는 이 쪽, B 는 [next] 쪽 보정선의
     * 교점('a vector is generated at the intersection of the tool nose radius center path of that block and … of a preceding block') — 여럿이면
     * 이음점 e 에 가까운 것. 교점이 없으면 B 처음 법선 e + nB·r('the vector normal to block B is created at the start point of block B' — A 의 노즈는
     * 그 자리로 곧게). A 가 원호인데 교점이 없으면 그 길(191쪽 '끝이 원호 위에 없는 원호')은 옮기지 않아 COMP_SWITCH.
     */
    fun switchTo(next: GComp, a: Seg, b: Seg): Res {
        val e = a.end
        val na = normal(tanAt(a, e))
        val nb = next.normal(tanAt(b, b.start))
        val p = meetAll(off(a, e, na), next.off(b, e, nb)).minByOrNull { (it - e).length }
        return when {
            p != null -> Ok(p, false)
            a is Arc -> Bad(IssueCode.COMP_SWITCH)
            else -> Ok(e + nb * r, false)
        }
    }

    /**
     * 블록 [a](보정 중 — 시작 · 취소 아님)의 노즈 토막 s → e. 0.0005 mm(입력 최소 단위의 반) 넘게 거꾸로 가면 COMP_INTERFERENCE
     * (H 61 — 그보다 작은 것은 0.001 반올림 자국이라 길이 0 으로 본다). R = r 오목 원호(보정선이 한 점)나 길이 0 은 [Line].
     */
    fun piece(a: Seg, s: Pt, e: Pt): Pair<Seg?, IssueCode?> = when (a) {
        is Line -> {
            val t = (a.end - a.start).let { it * (1 / it.length) }
            if (((e - s) dot t) < -REVERSE) null to IssueCode.COMP_INTERFERENCE else Line(s, e) to null
        }
        is Arc -> {
            val rh = rho(a)
            if (rh <= 1e-9) Line(s, e) to null
            else {
                val c = a.center
                val dir = if (a.cw) -1.0 else 1.0
                val ds = wrapPi(dir * (angleOf(s - c) - angleOf(a.start - c)))
                val de = wrapPi(dir * (angleOf(e - c) - angleOf(a.end - c)))
                val sweep = a.sweepRad() - ds + de
                when {
                    sweep * rh < -REVERSE -> null to IssueCode.COMP_INTERFERENCE
                    sweep <= 1e-9 -> Line(s, e) to null
                    else -> Arc(s, e, c, a.cw) to null
                }
            }
        }
    }

    /** 두 보정선(직선은 e 의 법선 자리를 지나는 무한 직선, 원호는 원)의 교점 가운데 guess 에 가까운 것 */
    private fun meet(a: Seg, b: Seg, e: Pt, na: Pt, nb: Pt, guess: Pt): Pt? =
        meetAll(off(a, e, na), off(b, e, nb)).minByOrNull { (it - guess).length }

    /** 두 보정선의 교점 전부 */
    private fun meetAll(oa: Off, ob: Off): List<Pt> =
        when {
            oa is OffLine && ob is OffLine -> listOfNotNull(Intersect.lineLine(oa.p, oa.d, ob.p, ob.d))
            oa is OffLine && ob is OffCircle -> lineCircle(oa, ob)
            oa is OffCircle && ob is OffLine -> lineCircle(ob, oa)
            else -> {
                oa as OffCircle
                ob as OffCircle
                when {
                    oa.rad <= 1e-9 -> listOf(oa.c).filter { abs((it - ob.c).length - ob.rad) <= 1e-7 }
                    ob.rad <= 1e-9 -> listOf(ob.c).filter { abs((it - oa.c).length - oa.rad) <= 1e-7 }
                    else -> Intersect.circleCircle(oa.c, oa.rad, ob.c, ob.rad)
                }
            }
        }

    private fun lineCircle(l: OffLine, c: OffCircle): List<Pt> =
        if (c.rad <= 1e-9) {
            val u = l.d * (1 / l.d.length)
            listOf(c.c).filter { abs(u cross (it - l.p)) <= 1e-7 }
        } else Intersect.lineCircle(l.p, l.d, c.c, c.rad)

    private sealed interface Off
    private class OffLine(val p: Pt, val d: Pt) : Off
    private class OffCircle(val c: Pt, val rad: Double) : Off

    private fun off(s: Seg, e: Pt, n: Pt): Off = when (s) {
        is Line -> OffLine(e + n * r, s.end - s.start)
        is Arc -> OffCircle(s.center, rho(s))
    }

    companion object {
        /** 거꾸로 가도 간섭으로 보지 않는 길이(mm) */
        const val REVERSE = 5e-4

        /** 90° 를 넘는 바깥 꺾임을 교점으로 받는 끝(넘은 각의 sin — 0.57°) · 그때 넘은 몫(끝 접선을 늘인 교점과 형식 C 가 끼우는 토막의 차이) 한도(mm) — [junction], 설계 22절 */
        const val OUTER_SIN = 1e-2
        const val OUTER_TOL = 1e-3

        /** 요소 s 의 점 p 에서 진행 방향 단위 접선 */
        fun tanAt(s: Seg, p: Pt): Pt = when (s) {
            is Line -> (s.end - s.start).let { it * (1 / it.length) }
            is Arc -> (p - s.center).let { it * (1 / it.length) }.left() * (if (s.cw) -1.0 else 1.0)
        }
    }
}
