package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaperCalcTest {
    private val angles = (5..85 step 5).map { it.toDouble() }
    private val radii = listOf(0.2, 0.4, 0.8, 1.2)

    private fun rad(deg: Double) = deg * PI / 180

    private fun ok(r: TaperResult): TaperSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        return r.solution
    }

    private fun stops(w: TaperWarning, r: TaperResult) {
        assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다")
        assertEquals(listOf(w), r.warnings)
    }

    private fun printed(p: Pt) = Fmt.nc(p.xDia) to Fmt.nc(p.z)

    /** NC 줄을 따라가며 줄마다 그 뒤의 위치(찍힌 글). 빠진 축은 앞 값 그대로(모달), 주석 줄은 건너뛴다 */
    /** 처음 G00 줄들(접근 줄 + 진입) 가운데 마지막 = 진입 G00 이 [ncTrace] 의 몇 번째인가 */
    private fun entryAt(nc: List<String>) = nc.filter { !it.startsWith("(") }.indexOfFirst { !it.startsWith("G00") } - 1

    /**
     * 뒤집어 그림 왼쪽(척 쪽 · 구멍 안)에서 시작할 때 진입 G00 점 e 앞의 접근 줄(설계 문서 "가공 방향 뒤집기" ③, 손으로 정한 규칙):
     * 앞 끝면 Z + c 에서 X 를 맞추고 Z 로만 온다. 그 X 로 오는 동안 노즈(진입점에서 노즈 왼쪽 끝부터 오른쪽)가 도면 꺾은선 pic 바깥이
     * 아니면 그 바깥(외경은 가장 높은 곳, 내경은 가장 낮은 곳)에서 c 더 떨어진 X 로 Z 를 맞춘 뒤 X 로. 꺾은선은 0.01 mm 마다 잰다
     */
    private fun approachOf(e: Pt, pic: List<Pt>, zFront: Double, inner: Boolean, tip: Int, r: Double, c: Double): List<Pt> {
        val tv = NoseComp.tipVector(tip, r)
        val from = e.z - tv.z - r
        val xs = pic.zipWithNext().flatMap { (a, b) ->
            val n = max(1, ceil((b - a).length / 0.01).toInt())
            (0..n).map { a + (b - a) * (it.toDouble() / n) }.filter { it.z >= from - 1e-9 }.map { it.x }
        }
        // 노즈 바깥 끝(외경은 아래, 내경은 위) = 인선 − 팁 벡터 ∓ r
        val lim = if (inner) xs.min() + tv.x - r else xs.max() + tv.x + r
        val clear = if (inner) e.x <= lim + 1e-6 else e.x >= lim - 1e-6
        if (clear) return listOf(Pt(zFront + c, e.x))
        val xa = if (inner) lim - c else lim + c
        return listOf(Pt(zFront + c, xa), Pt(e.z, xa))
    }

    private fun ncTrace(nc: List<String>): List<Pair<String, String>> {
        var x = ""
        var z = ""
        return nc.filter { !it.startsWith("(") }.map { line ->
            for (w in line.split(" ")) {
                when (w[0]) {
                    'X' -> x = w.substring(1)
                    'Z' -> z = w.substring(1)
                }
            }
            x to z
        }
    }

    /**
     * 계산기와 따로 정한 공구 쪽 단위 법선. 단면(수직선)은 모든 TYPE 에서 재료가 왼쪽(척 쪽)이라 +Z,
     * 그 밖은 외경이면 위(+X), 내경이면 아래(−X). G41/G42 를 쓰지 않는다.
     */
    private fun toolNormal(p: Pt, q: Pt, inner: Boolean): Pt {
        val d = q - p
        val n = Pt(-d.x, d.z) * (1 / d.length)
        val good = if (abs(d.z) < 1e-12) n.z > 0 else (n.x > 0) != inner
        return if (good) n else n * -1.0
    }

    /** 일반 해법(따로 구현): 선 a–c, c–b 를 공구 쪽으로 r 밀고 팁 벡터를 더한 두 직선의 교점 */
    private fun cornerByLines(a: Pt, c: Pt, b: Pt, inner: Boolean, tip: Int, r: Double): Pt {
        val v = NoseComp.tipVector(tip, r)
        val s1 = toolNormal(a, c, inner) * r + v
        val s2 = toolNormal(c, b, inner) * r + v
        return Intersect.lineLine(c + s1, c - a, c + s2, b - c)!!
    }

    /** 닫힌 식(아무 꺾임이나): 노즈 중심 = c + r(n1 + n2)/(1 + n1·n2), 여기에 팁 벡터 */
    private fun cornerByBisector(a: Pt, c: Pt, b: Pt, inner: Boolean, tip: Int, r: Double): Pt {
        val n1 = toolNormal(a, c, inner)
        val n2 = toolNormal(c, b, inner)
        return c + (n1 + n2) * (r / (1 + (n1 dot n2))) + NoseComp.tipVector(tip, r)
    }

    /** 앞뒤 요소 p–q 의 끝 p 를 그 요소의 보정선(도면선 + 공구 쪽 r + 팁 벡터)에 내린 발 = 인선이 p 의 좌표에 오는 점 */
    private fun legFoot(p: Pt, q: Pt, inner: Boolean, tip: Int, r: Double): Pt {
        val u = (q - p).unit()
        val off = toolNormal(p, q, inner) * r + NoseComp.tipVector(tip, r)
        return p + off - u * (off dot u)
    }

    /**
     * 손으로 푼 꺾임점 이동량(표준 팁). [normal] = 경사가 바이트 쪽을 향함(외경·내경 TYPE 1~4),
     * 아니면 역테이퍼(TYPE 5). [vertical] = 그 꺾임점의 이웃이 단면.
     * - 정방향: 이웃 수평 −r(1 − tan(θ/2)) [NoseR], 역테이퍼면 −r(1 + tan(θ/2)) [NoseCompTest 의 손 유도],
     *   이웃 단면 X 로 ∓r(1 − tan((90° − θ)/2)) [NoseR].
     * - 역방향 TYPE 5 = 정방향 TYPE 3 을 Z 로 뒤집은 것 → +r(1 − tan(θ/2)).
     * - 역방향 TYPE 1~4(바이트와 반대): T4 의 인선은 수평선 위에선 X 가 그대로, +Z 단면에선 2r 떨어진다.
     *   경사선(법선 (sinθ, cosθ), 이동 (r sinθ + r, r cosθ − r))과 만나는 점을 풀면
     *   수평 이웃 +r(1 + tan(θ/2)), 단면 이웃 (2r, −r(1 − tan((90° − θ)/2))).
     * - 내경은 외경을 X 로 뒤집은 것(T3↔T2, T4↔T1) → X 이동만 부호가 바뀐다.
     */
    private fun closedShift(inner: Boolean, normal: Boolean, reverse: Boolean, vertical: Boolean, deg: Double, r: Double): Pt {
        val dzA = NoseR.taperDeltaZ(r, deg)
        val dzB = r * (1 + tan(rad(deg) / 2))
        val dx = NoseR.taperDeltaXDia(r, deg) / 2 * (if (inner) 1.0 else -1.0)
        return when {
            vertical && !reverse -> Pt(0.0, dx)
            vertical -> Pt(2 * r, dx)
            !reverse -> Pt(if (normal) -dzA else -dzB, 0.0)
            else -> Pt(if (normal) dzB else dzA, 0.0)
        }
    }

    /** 점 p 에서 선분 a–b 까지 거리 */
    private fun segDist(p: Pt, a: Pt, b: Pt): Double {
        val d = b - a
        val t = (((p - a) dot d) / (d dot d)).coerceIn(0.0, 1.0)
        return (p - (a + d * t)).length
    }

    private fun sharp(p: TaperPoint): Pt {
        assertNull(p.corner)
        return p.nodes.single().prog
    }

    /** 표준 G41/G42 표: 외경 정방향 G42 · 역방향 G41, 내경 정방향 G41 · 역방향 G42 (Haas 워크북 60쪽) */
    private fun expectedSide(inner: Boolean, reverse: Boolean) = when {
        !inner && !reverse -> "G42"
        !inner -> "G41"
        !reverse -> "G41"
        else -> "G42"
    }

    /**
     * G40 상태(G41/G42 프로그램의 G00 점)에서 인선을 선 위에 두면 노즈가 선을 넘는 만큼: max(0, r + n·팁 벡터).
     * 표준 팁이 제 쪽 요소면 0, 바이트 반대쪽 단면(역방향 바이트 T4·T1 과 +Z 를 보는 단면)이면 2r.
     */
    private fun lift(n: Pt, tip: Int, r: Double) = max(0.0, r + (n dot NoseComp.tipVector(tip, r)))

    /**
     * 두 NC 를 가공 순서의 점(도면 [order] 의 시작점 → T 점 → 끝점)으로 맞춰 본다. 진입·후퇴 거리 c = max(Z 여유 1, r).
     * - 직접 보정(G40): 진입(시작 요소가 수평이면 그 연장선, 단면이면 공구 쪽에서 Z) → (단면이면 시작점′) → 보정점 → 끝점′ → 후퇴.
     *   시작·끝점′ 은 인선이 그 좌표에 오는 점 [legFoot]. 끝점을 비우면 끝점′ 없이 마지막 보정점에서 끝 요소의 공구 쪽으로 c 후퇴.
     * - G41/G42: 주석 → G00 진입 → G4x G01 시작점(진입 거리 ≥ r) → T 점(도면) → 끝점 → G40 G01 후퇴.
     *   재료인 선(시작·끝 요소)을 따라 들고 날 때 [lift] > 0 이면 G00 점을 공구 쪽으로 c + lift 띄우고, 들어갈 때는
     *   그 선에 직각으로 G4x G01 한 뒤 선을 따라 G01 로 간다. 끝점을 비우면 끝점 없이 마지막 T 점에서 보정을 푼다 —
     *   마지막 꺾임이 볼록이면 경사를 Z 로 c 더 늘인 점으로, 아니면 끝 요소 선을 따라 c(손으로 정한 표 slopeExit).
     *   바이트 반대쪽 모양이면 노즈가 꺾임을 벗어나게 더 간다
     */
    private fun checkNc(
        s: TaperSolution, order: List<Pt>, corners: List<Pt>, inner: Boolean, leftFirst: Boolean, flip: Boolean, r: Double, tag: String,
        slopeEntry: Boolean, slopeExit: Boolean,
    ) {
        val tip = s.tip
        val c = max(1.0, r)
        val st = order.first()
        val en = order.last()
        val tl = order[order.size - 2] // 마지막 T 점
        val firstH = abs(order[1].z - st.z) > 1e-12
        val lastH = abs(en.z - tl.z) > 1e-12
        val nB = toolNormal(tl, en, inner)
        val tv = NoseComp.tipVector(tip, r)
        // 끝: 끝점을 넣었으면 인선이 끝점에 오고(끝점′) 수평이면 공구 쪽으로, 단면이면 이어서 c — 단면을 재료 쪽(외경은 중심 쪽 X−,
        // 내경은 바깥 X+)으로 깎아 끝나면(그림의 앞 단면을 거꾸로) 너머도 같은 단면이라 공구 쪽으로(이 격자의 끝 X 는 중심이 아니다).
        // 비웠으면 마지막 보정점에서 공구 쪽으로 c.
        // G41/G42 는 공구 쪽 직각 취소(안쪽 모서리 — 교점)가 끝 꼭짓점에 닿는 것이 팁 벡터·u = r 일 때뿐이라, 그보다 작으면 끝 블록 뒤에
        // 이동 없는 G40 한 줄(글 위치는 앞 줄 그대로) 뒤 G01 로 빠진다(FANUC 은 끝 법선 — FF 5.4.4 그림 5.4.4(e)). 바깥으로 이어 빼는 단면도
        // 바이트 반대쪽(lift > 0)이면 같은 꼴. 끝점을 비운 바이트 반대쪽 끝도: 볼록이면 경사 끝 법선에 서게 이동 없는 G40, 오목이면 끝 요소 선을 따라 직접 보정의 마지막 노즈 자리(바로 너머)까지 한 블록 더 간 뒤
        // 이동 없는 G40 과 직접 보정의 후퇴점으로
        val g40End: List<Pt>
        val g4End: List<Pt>
        var alone = false
        if (s.toEnd) {
            val inward = !lastH && (en.x < tl.x) != inner
            val leave = if (lastH || inward) nB else (en - tl).unit()
            val e0 = legFoot(en, tl, inner, tip, r)
            assertPt(e0 + leave * c, s.exit, msg = "$tag 후퇴점")
            g40End = listOf(e0, s.exit)
            // 너머가 막힌 끝(척 쪽으로 깎아 끝난 수평, 재료 쪽으로 깎아 끝난 단면 — 외경 X0 은 빼고)은 늘 공구 쪽 직각 취소(교점 — 노즈 끝이 끝좌표에서 멈춤)
            val uE = (en - tl).unit()
            val wall = if (lastH) uE.z < 0 else inward && !(!inner && en.x <= 1e-9)
            alone = if (lastH || inward) !wall && (tv dot uE) < r - 1e-9 else lift(nB, tip, r) > 1e-9
            val ex = en + leave * c + nB * lift(nB, tip, r)
            // 막힌 끝에서 팁 벡터·u < r 이면 보정 중에 직각 후퇴점보다 r 더 위까지 한 줄 더 간 뒤 취소를 r − 팁 벡터·u 만큼 척 반대쪽으로 비켜 c 더(노즈가 벽 위로)
            val drift = r - (tv dot uE)
            g4End = if (alone) listOf(en, ex) else if (wall && drift > 1e-9) listOf(ex + nB * r, ex + nB * (r + c) - uE * drift) else listOf(ex)
        } else {
            assertPt(corners.last() + nB * c, s.exit, msg = "$tag 후퇴점이 마지막 보정점에서 끝 요소의 공구 쪽으로 c")
            g40End = listOf(s.exit)
            val ahead = tl - order[order.size - 3]
            val nS = toolNormal(order[order.size - 3], tl, inner)
            val uB = (en - tl).unit()
            // 바이트 반대쪽이면 노즈가 꺾임을 벗어나게: 경사 쪽 lift, 끝 요소 쪽은 마지막 보정점이 꺾임에서 끝 요소를 따라 간 만큼 더
            g4End = if (slopeExit) {
                alone = lift(nS, tip, r) > 1e-9
                val ex = tl + ahead * (c / abs(ahead.z)) + nS * lift(nS, tip, r)
                if (alone) listOf(tl, ex) else listOf(ex)
            } else if (lift(nB, tip, r) > 1e-9) {
                alone = true
                // 끝 요소 선 위 직접 보정의 마지막 노즈 접점 바로 너머 — 0.001 격자의 다음 칸(반올림으로 거꾸로 가지 않게)
                val raw = tl + uB * max(0.0, ((corners.last() - tv) - tl) dot uB)
                fun past(v: Double, d: Double) = if (d > 0) (floor(v * 1000 + 1e-6) + 1) / 1000 else (ceil(v * 1000 - 1e-6) - 1) / 1000
                val sk = if (lastH) Pt(past(raw.z, uB.z), raw.x) else Pt.ofDia(raw.z, past(raw.xDia, uB.x))
                listOf(sk, sk, s.exit)
            } else {
                listOf(tl + uB * (c + max(0.0, (corners.last() - tl) dot uB)))
            }
        }
        val shape = order.dropLast(if (s.toEnd) 0 else 1)
        // 시작 요소 선을 따라 들어가는 진입(선 위의 점 q, 법선 n). 노즈가 선을 넘으면 띄운 점 → 선 위의 점 q2 로 직각 진입
        val u0 = (order[1] - st).unit()
        val tv0 = NoseComp.tipVector(tip, r)
        fun onLine(q: Pt, n: Pt, q2: Pt): List<Pt> = lift(n, tip, r).let { if (it <= 1e-9) listOf(q) else listOf(q2 + n * (c + it), q2) }
        // 띄운 진입의 lead 를 노즈R 보다 이만큼 더(다음 G01 의 보정 노즈 이동이 0 이 되지 않게 — TaperCalc.LEAD_PAST)
        val past = 0.01
        var convexLead = false
        // 진입: 시작점을 넣었으면 그 요소의 연장선(수평)·공구 쪽(단면)에서 시작점으로. 비웠으면 시작 요소는 깎지 않고
        // 첫 꺾임이 볼록이면 경사선을(손으로 정한 표 slopeEntry), 아니면 수평인 시작 요소는 그 보정선을 늘인 곳에서,
        // 단면은 공구 쪽(Z)에서 첫 보정점으로 — G41/G42 는 단면 선 위 점에 직각으로 들어가 선을 따라 첫 T 점으로
        val nF = toolNormal(st, order[1], inner)
        val g40: List<Pt>
        val g4Start: List<Pt>
        if (s.fromStart) {
            // 뒤집어 수평 시작 요소를 팁의 Z 와 반대로 깎으면 시작 좌표 너머(척·구멍 바닥)로 넘지 않고 공구 쪽에서 직각으로 — 단면 시작과 같은 꼴
            val along = firstH && !(flip && u0.z * tv0.z <= 0)
            val approach = if (along) (st - order[1]).unit() else nF
            val s0 = legFoot(st, order[1], inner, tip, r)
            assertPt(s0 + approach * c, s.entry, msg = "$tag 진입점")
            g40 = listOf(s.entry) + (if (along) emptyList() else listOf(s0)) + corners
            // 연장선 진입의 띄운 lead 는 시작 좌표에서 max(c, r + past). 직각 진입은 너머가 빈 시작(재료 쪽으로 깎기 시작하는 단면 · 화면 역방향 뒤집기의
            // 수평)에서 팁 벡터·u > −r 이면 lead = 시작점 − u(r + max(팁 벡터·u, past)) 에 직각으로 들어가 선을 따라 시작점(노즈 = 직접 보정)
            val tu = tv0 dot u0
            val wallS = if (firstH) u0.z > 0 && u0.z * tv0.z <= 0 else (u0.x > 0) != inner && !(!inner && st.x <= 1e-9)
            val lead = if (!along && !wallS && tu > -r + 1e-9) st - u0 * (r + max(tu, past)) else null
            g4Start = when {
                along -> onLine(st + approach * c, nF, st - u0 * max(c, r + past))
                lead != null -> listOf(lead + nF * (c + lift(nF, tip, r)), lead)
                else -> listOf(st + nF * (c + lift(nF, tip, r)))
            } + shape
        } else {
            val back = order[1] - order[2]
            val lead = (st - order[1]).unit()
            if (slopeEntry) {
                assertPt(corners[0] + back * (c / abs(back.z)), s.entry, msg = "$tag 진입점이 보정 경사선 위 Z 로 c 바깥")
                // 바이트 반대 팁(시작 요소 쪽 lift > 0)이면 G40 노즈가 깎지 않는 시작 요소 선에서 r 떨어질 때까지 연장선을 따라 더
                val bu = back.unit()
                val k = bu dot nF
                val far = lift(nF, tip, r).let { if (it > 1e-9 && k > 1e-9) it / k else 0.0 }
                // FANUC 시작 블록의 노즈는 G40 노즈(G00 − 팁 벡터)에서 E + n·r 까지 곧게 — 그 선이 도면 꺾은선(깎지 않는 시작 요소 포함)에 r 보다
                // 가까우면(바이트 반대쪽 팁) 경사 연장선 위 lead = T − u·r 에 공구 쪽에서 직각으로(c + lift 띄운 G00) 들어가 선을 따라 T 로
                val ext = order[1] + bu * max(c / abs(bu.z), far)
                val nS = toolNormal(order[1], order[2], inner)
                val from = ext - tv0
                val to = order[1] + nS * r
                val hits = (0..400).any { q ->
                    val p = from + (to - from) * (q / 400.0)
                    order.zipWithNext().minOf { (a, b) -> segDist(p, a, b) } < r - 5e-4
                }
                g4Start = if (hits) {
                    convexLead = true
                    val ld = order[1] + bu * (r + past)
                    listOf(ld + nS * (c + lift(nS, tip, r)), ld) + shape.drop(1)
                } else {
                    listOf(ext) + shape.drop(1)
                }
            } else if (firstH) {
                assertPt(corners[0] + lead * c, s.entry, msg = "$tag 진입점이 시작 요소 보정선 위 c 바깥")
                // G41/G42 는 도면 꼭짓점에서 c — 첫 보정점이 꼭짓점보다 뒤(lead 쪽)면(뒤집어 인선이 노즈 뒤에 끌려감) 직접 보정 진입점까지
                // 띄우면 그 이동 끝(교점 규칙)의 인선이 직접 보정의 진입점이 되게 q2 = (진입점 − 팁 벡터 를 선에 내린 발) − u·r
                g4Start = onLine(order[1] + lead * (c + max(0.0, (corners[0] - order[1]) dot lead)), nF, st + u0 * ((s.entry - tv0 - st) dot u0) - u0 * r) +
                    shape.drop(1)
            } else {
                assertPt(corners[0] + nF * c, s.entry, msg = "$tag 진입점이 첫 보정점에서 공구 쪽(Z)으로 c")
                // 직각으로 들어가 선 위 q 에 서면 노즈는 q 보다 r 앞 — 첫 보정점의 노즈 자리 바로 앞(past)
                val q = st + u0 * ((corners[0] - tv0 - st) dot u0) - u0 * (r + past)
                g4Start = listOf(q + nF * (c + lift(nF, tip, r)), q) + shape.drop(1)
            }
            g40 = listOf(s.entry) + corners
        }
        // 접근 줄: 뒤집어 척 쪽(그림 왼쪽)에서 시작하면 진입 G00 이 앞 끝면(T 점 · 넣은 앞 요소 끝 가운데 가장 오른쪽)보다 왼쪽이라
        // 앞 끝면 + c 에서 X 를 맞추고 Z 로 들어간다. 왼쪽부터 깎을 때 앞 요소는 끝 요소(order 의 마지막)다
        val zFront = (order.subList(1, order.size - 1) + listOfNotNull(order.last().takeIf { s.toEnd })).maxOf { it.z }
        fun app(e: Pt) = if (flip && leftFirst && e.z < zFront - 1e-9) approachOf(e, order, zFront, inner, tip, r, c) else emptyList()
        val app40 = app(s.entry)
        assertEquals(app40.size, s.approach.size, "$tag 접근 줄 ${s.ncG40}")
        assertEquals((app40 + g40 + g40End).map(::printed), ncTrace(s.ncG40), "$tag G40 ${s.ncG40}")
        assertEquals(
            app40.map { "G00" } + listOf("G00") + g40.drop(1).map { "G01" } + (if (s.toEnd) listOf("G01", "G00") else listOf("G00")),
            s.ncG40.map { it.take(3) }, tag,
        )

        val g4 = s.ncG41G42
        // G41/G42 의 볼록 첫 꺾임 띄운 진입은 안 뒤집어도(계산기 역방향 빼고) 앞 끝면 뒤면 같은 접근 줄
        val e4 = g4Start.first()
        val app4 = if (convexLead && leftFirst == flip && !flip && e4.z < zFront - 1e-9) approachOf(e4, order, zFront, inner, tip, r, c) else app(e4)
        assertEquals("(R${Fmt.nc(r).removeSuffix(".")} T$tip)", g4[0], tag)
        assertEquals((app4 + g4Start + g4End).map(::printed), ncTrace(g4), "$tag G41/G42 $g4")
        // 보정 쪽은 진행 방향으로 정해진다: 그림 왼쪽부터(Z+) 깎으면 표의 역방향 쪽
        val side = expectedSide(inner, leftFirst)
        val k = 2 + app4.size
        assertTrue(g4.subList(1, k).all { it.startsWith("G00 ") }, "$tag 접근 · 진입 $g4")
        assertTrue(g4[k].startsWith("$side G01 "), "$tag 보정 시작 ${g4[k]}")
        if (alone) {
            assertTrue(g4[g4.size - 2] == "G40" && g4.last().startsWith("G01 "), "$tag 이동 없는 G40 뒤 G01 후퇴 $g4")
        } else {
            assertTrue(g4.last().startsWith("G40 G01 "), "$tag 보정 취소 ${g4.last()}")
        }
        assertTrue(g4.drop(k + 1).dropLast(if (alone) 2 else 1).all { it.startsWith("G01 ") }, "$tag $g4")
        assertTrue(c >= r, "진입 이동 ≥ r")
    }

    /**
     * 외경·내경 × TYPE 1~5 × 정/역방향 × 뒤집기 × 5°~85° × r 0.2~1.2 에서
     * 보정점이 일반 해법·이등분선 식·손 닫힌 식과 모두 맞고, 두 NC 가 약속대로인지.
     * 뒤집기(flip)는 같은 그림·같은 바이트를 반대 끝부터 깎는다 — 칸의 T1 이 그림 왼쪽 점, 보정점은 공구 쪽·팁 벡터로만
     * 정해지므로 안 뒤집은 것과 같은 점(순서만 반대), 보정 쪽은 반대.
     */
    @Test
    fun everyTypeAndDirection() {
        val a = 40.0 // 그림 오른쪽 점 반경(X80)
        val len = 2.0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true)) {
            for (deg in angles) for (r in radii) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                // t1·t2·f·b 는 그림 순서(오른쪽부터). 뒤집으면 칸에는 t2 가 T1 으로 들어간다
                val t1 = Pt(0.0, a)
                val t2 = Pt(-len, a + up * len * tan(rad(deg)))
                // 그림 왼쪽부터(Z+) 깎나: 역방향(역바이트)과 뒤집기 가운데 하나만 켜졌을 때
                val leftFirst = reverse != flip
                val tag = "${if (inner) "내경" else "외경"} TYPE$type ${if (reverse) "역" else "정"}${if (flip) " 뒤집기" else ""} ${deg}° r$r"
                /** 그림의 앞(오른쪽 점 쪽)·뒤 요소 끝 좌표로 입력을 만든다. 뒤집으면 칸이 반대 끝부터라 점·시작·끝을 바꿔 넣는다 */
                fun input(front: Double?, back: Double?) = if (flip) {
                    TaperInput(inner, type, reverse, r, x1 = t2.xDia, z1 = t2.z, x2 = t1.xDia, z2 = t1.z, start = back, end = front, flip = true)
                } else {
                    TaperInput(inner, type, reverse, r, x1 = t1.xDia, z1 = t1.z, x2 = t2.xDia, z2 = t2.z, start = front, end = back)
                }
                val res = TaperCalc.calc(input(null, null))
                val s = ok(res)
                // 공구(팁)는 방향 스위치만 따르고, 보정 쪽은 진행 방향을 따른다
                val tip = NoseComp.standardTip(inner, reverse)
                assertEquals(tip, s.tip, tag)
                assertEquals(Side.of(inner, leftFirst), s.side, tag)
                assertEquals(deg, s.anglesDeg.single(), 1e-9, tag)
                // points 는 칸 순서(T1 부터) — 뒤집으면 그림 왼쪽 점부터
                val pic = if (flip) s.points.reversed() else s.points
                assertPt(t1, pic[0].vertex, msg = tag)
                assertPt(t2, pic[1].vertex, msg = tag)

                // 그림대로의 앞뒤 요소: TYPE 1·2 는 T1 에 단면, TYPE 2·4 는 T2 에 단면, 나머지는 수평. 비우면 5(단면은 반경)
                val frontV = type <= 2
                val backV = type == 2 || type == 4
                val f = if (frontV) t1 + Pt(0.0, -up * 5) else t1 + Pt(5.0, 0.0)
                val b = if (backV) t2 + Pt(0.0, up * 5) else t2 + Pt(-5.0, 0.0)
                val p1 = sharp(pic[0])
                val p2 = sharp(pic[1])
                assertPt(cornerByLines(f, t1, t2, inner, tip, r), p1, msg = "$tag T1′ 일반 해법")
                assertPt(cornerByLines(t1, t2, b, inner, tip, r), p2, msg = "$tag T2′ 일반 해법")
                assertPt(cornerByBisector(f, t1, t2, inner, tip, r), p1, msg = "$tag T1′ 이등분선")
                assertPt(cornerByBisector(t1, t2, b, inner, tip, r), p2, msg = "$tag T2′ 이등분선")
                assertPt(t1 + closedShift(inner, type != 5, reverse, frontV, deg, r), p1, msg = "$tag T1′ 닫힌 식")
                assertPt(t2 + closedShift(inner, type != 5, reverse, backV, deg, r), p2, msg = "$tag T2′ 닫힌 식")
                assertEquals(p1.xDia - t1.xDia, pic[0].nodes.single().dXDia, 1e-12, tag)
                assertEquals(p1.z - t1.z, pic[0].nodes.single().dZ, 1e-12, tag)

                // 모양: 정방향은 역테이퍼(TYPE 5)만, 역방향은 TYPE 1~4 가 바이트와 반대(접점이 팁 사분면 밖).
                // 진행: 뒤집으면 팁의 Z 와 반대로 가므로 45° 보다 눕힌 깎는 요소(경사 < 45°, 넣은 수평 앞뒤 요소)가 끝날 쪽으로 깎인다
                val shapeAgainst = reverse != (type == 5)
                val against = shapeAgainst || (flip && deg < 45)
                assertEquals(if (against) listOf(TaperWarning.AGAINST_TOOL) else emptyList(), res.warnings, tag)

                val order = listOf(f, t1, t2, b).let { if (leftFirst) it.reversed() else it }
                val corners = if (leftFirst) listOf(p2, p1) else listOf(p1, p2)
                // 볼록 꺾임(재료 모서리가 공구 쪽으로 나옴): T1 은 TYPE 1·2·5, T2 는 TYPE 1·3. 왼쪽부터면 T2 에서 시작해 T1 에서 끝난다
                val convex1 = type == 1 || type == 2 || type == 5
                val convex2 = type == 1 || type == 3
                val slopeEntry = if (leftFirst) convex2 else convex1
                val slopeExit = if (leftFirst) convex1 else convex2
                checkNc(s, order, corners, inner, leftFirst, flip, r, tag, slopeEntry, slopeExit)
                val fIn = if (frontV) f.xDia else f.z
                val bIn = if (backV) b.xDia else b.z
                // 시작점을 넣으면 그 요소(가공 시작 쪽)부터 깎는다
                val res2 = TaperCalc.calc(if (leftFirst) input(null, bIn) else input(fIn, null))
                val s2 = ok(res2)
                assertTrue(s2.fromStart && !s.fromStart, tag)
                assertEquals(against || (flip && !(if (leftFirst) backV else frontV)), TaperWarning.AGAINST_TOOL in res2.warnings, "$tag 시작점")
                checkNc(s2, order, corners, inner, leftFirst, flip, r, "$tag 시작점", slopeEntry, slopeExit)
                // 끝점을 넣으면 끝 요소를 그 좌표까지 깎는다(비우면 마지막 T 점에서 끝)
                val res3 = TaperCalc.calc(if (leftFirst) input(fIn, null) else input(null, bIn))
                val s3 = ok(res3)
                assertTrue(s3.toEnd && !s.toEnd && !s3.fromStart, tag)
                assertEquals(against || (flip && !(if (leftFirst) frontV else backV)), TaperWarning.AGAINST_TOOL in res3.warnings, "$tag 끝점")
                checkNc(s3, order, corners, inner, leftFirst, flip, r, "$tag 끝점", slopeEntry, slopeExit)
            }
        }
    }

    /** 외경 TYPE 1 (단면 → C5 면취 → 외경50), 0.4R 정방향 — 숫자를 손으로 */
    @Test
    fun outerType1Numbers() {
        val s = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        // T1 이웃은 단면 → X 로만 0.4(1 − tan22.5°)×2 = 0.469, T2 이웃은 수평 → Z 로만 0.234
        val t1 = s.points[0].nodes.single()
        val t2 = s.points[1].nodes.single()
        assertEquals("39.531", Fmt.fixed3(t1.prog.xDia))
        assertEquals("0.000", Fmt.fixed3(t1.prog.z))
        assertEquals("-0.469", Fmt.fixed3(t1.dXDia))
        assertEquals("50.000", Fmt.fixed3(t2.prog.xDia))
        assertEquals("-5.234", Fmt.fixed3(t2.prog.z))
        assertEquals(45.0, s.anglesDeg.single(), 1e-12)
        // 시작점을 비우면 단면은 깎지 않고 보정 면취선을 Z+1 까지 연장한 곳에서 들어간다 → X 가 직경으로 2 줄어든다.
        // 끝점을 비우면 외경은 깎지 않고 T2′ 에서 X 로 1 빠진다
        assertEquals(listOf("G00 X37.531 Z1.", "G01 X39.531 Z0.", "G01 X50. Z-5.234", "G00 X52."), s.ncG40)
        // G41/G42: 도면 면취선 연장 X38 Z1 에서 G42 로 T1 까지(보정 시작 이동 = 면취와 한 직선, 길이 1.41 ≥ r).
        // T2 는 볼록이라 면취선을 Z 로 1 더 늘인 X52 Z-6 으로 보정을 푼다
        assertEquals(
            listOf("(R0.4 T3)", "G00 X38. Z1.", "G42 G01 X40. Z0.", "G01 X50. Z-5.", "G40 G01 X52. Z-6."),
            s.ncG41G42,
        )
        // 시작점 = 단면 X30: 공구 쪽(Z+1)에서 들어가 인선을 X30 Z0 에 맞추고 단면부터 깎는다
        val f = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 30.0)))
        assertEquals(listOf("G00 X30. Z1.", "G01 Z0.", "G01 X39.531", "G01 X50. Z-5.234", "G00 X52."), f.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X30. Z1.", "G42 G01 Z0.", "G01 X40.", "G01 X50. Z-5.", "G40 G01 X52. Z-6."),
            f.ncG41G42,
        )
        // 끝점 = Z-10: 인선이 Z-10 에 멈춘 뒤 X 로 1 빠진다
        val e = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = -10.0)))
        assertEquals(listOf("G00 X37.531 Z1.", "G01 X39.531 Z0.", "G01 X50. Z-5.234", "G01 Z-10.", "G00 X52."), e.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X38. Z1.", "G42 G01 X40. Z0.", "G01 X50. Z-5.", "G01 Z-10.", "G40 G01 X52."),
            e.ncG41G42,
        )
    }

    /**
     * 외경 TYPE 1 뒤집기(T1 ⇄ T2, 사용자 요청 2026-10-27): 같은 그림(단면 → C5 면취 → 외경50)을 외경 쪽 위 점부터 —
     * 면취를 타고 내려와 단면을 중심 쪽으로. 칸의 T1 = X50 Z-5(먼저 깎는 점), T2 = X40 Z0. 바이트는 그대로 T3,
     * 진행이 Z+·X− 라 공구가 진행 방향 왼쪽 → G41. 숫자를 손으로:
     * - 보정점은 안 뒤집은 것과 같은 점(공구 쪽·팁 벡터로만 정해짐), 순서만 반대: T1′ X50 Z-5.234(외경 이웃 → Z 로 0.4(1 − tan22.5°)),
     *   T2′ X39.531 Z0(단면 이웃 → X 로 2×0.234).
     * - 진입(시작점 = 외경 Z 를 비움 → 외경은 깎지 않음): 첫 꺾임 T1 이 볼록이라 보정 면취선을 첫 깎는 점에서 Z 로 c = max(1, 0.4) = 1
     *   더 늘인 점 — 면취 45° 라 X 는 직경으로 2 커진다. 직접 보정 X52 Z-6.234, G41/G42 는 도면 면취선 연장 X52 Z-5-1 = Z-6.
     *   G41 시작 이동(X52 Z-6 → X50 Z-5)은 첫 형상(면취)과 한 직선이고 길이 1.414 ≥ r, 공구는 외경 위(노즈 중심 X52.8 Z-5.6)라 재료 밖.
     *   그 G00 점이 척 쪽(앞 끝면 Z0 보다 왼쪽)이라 앞에 접근 줄 `G00 X52. Z1.`(앞 끝면 + c 에서 X 를 맞춤) → `G00 Z-6.`(Z 로만) —
     *   X52 는 오른쪽 도면(면취 · 단면, 가장 높은 X50)보다 위라 그대로 Z 로 온다.
     * - 후퇴(끝점 = 단면 X 를 비움 → 단면은 깎지 않음): 직접 보정은 T2′ 에서 끝 요소(단면)의 공구 쪽(+Z)으로 1 → Z1.
     *   G41/G42 는 마지막 꺾임 T2 가 볼록이라 면취를 Z 로 1 더 늘인 X38 Z1 로 보정을 푼다(안 뒤집은 것의 진입점과 같은 자리).
     */
    @Test
    fun flippedOuterType1Numbers() {
        val flip = TaperInput(x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, flip = true)
        val res = TaperCalc.calc(flip)
        val s = ok(res)
        assertEquals(emptyList(), res.warnings, "면취 45° 를 내려오고 단면을 중심 쪽으로 — 바이트와 맞다")
        assertEquals(3, s.tip)
        assertEquals(Side.G41, s.side)
        val t1 = s.points[0].nodes.single()
        val t2 = s.points[1].nodes.single()
        assertPt(Pt.ofDia(-5.0, 50.0), t1.drawn)
        assertPt(Pt.ofDia(0.0, 40.0), t2.drawn)
        assertEquals("50.000", Fmt.fixed3(t1.prog.xDia))
        assertEquals("-5.234", Fmt.fixed3(t1.prog.z))
        assertEquals("39.531", Fmt.fixed3(t2.prog.xDia))
        assertEquals("0.000", Fmt.fixed3(t2.prog.z))
        assertEquals(45.0, s.anglesDeg.single(), 1e-12)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G00 Z1."), s.ncG40)
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G40 G01 X38. Z1."), s.ncG41G42)
        assertEquals(listOf(Pt.ofDia(1.0, 52.0)), s.approach)
        // 안 뒤집은 것과 같은 실제 점: 직접 보정 X39.531 Z0 → X50 Z-5.234 가 X50 Z-5.234 → X39.531 Z0 으로 순서만 바뀐다
        val fwd = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        for ((a, b) in fwd.points.reversed().zip(s.points)) {
            assertPt(a.nodes.single().drawn, b.nodes.single().drawn)
            assertPt(a.nodes.single().prog, b.nodes.single().prog)
        }
        assertEquals(Side.G42, fwd.side)

        // 끝점 = 단면 X30(중심 쪽으로 X30 까지 깎음): 인선이 X30 Z0 에 오고, 단면의 공구 쪽(+Z)으로 1 빠진다. X30 아래는 아직 같은 단면이거나
        // 보스라 단면을 따라 이어 가면 G00 이 X28 까지 급송으로 깎는다(검증 2026-10-27). G41/G42 는 취소 이동이 공구 쪽 직각이라 제어기가
        // 안쪽 모서리 교점(노즈 중심 X30.8 Z0.4)을 잡는다 — 직접 보정의 노즈 중심(인선 X30 Z0 − 팁 벡터)과 같은 자리
        val e = ok(TaperCalc.calc(flip.copy(end = 30.0)))
        assertEquals(emptyList(), TaperCalc.calc(flip.copy(end = 30.0)).warnings)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G01 X30.", "G00 Z1."), e.ncG40)
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G01 X30.", "G40 G01 Z1."), e.ncG41G42)
        // Z 여유 0 이면 후퇴도 노즈R 만큼(Z0.4)
        assertEquals("G40 G01 Z0.4", ok(TaperCalc.calc(flip.copy(end = 30.0, zClear = 0.0))).ncG41G42.last())
        // 끝점 = 단면 X0(중심까지): 인선(T3, 노즈 중심보다 r 앞)을 X0 에 멈추면 노즈 중심이 반경 0.4 에 서서 중심에 살(법선 0.166)이 남는다 —
        // 직접 보정은 노즈 중심이 중심선에 닿는 X-0.8(−2r) 까지 깎은 뒤 공구 쪽(+Z)으로 빠진다(검증 2026-10-27: 단면을 따라 X-2.8 로 급송하지 않음).
        // G41/G42 도 단면을 X-0.8 까지 쓰고 Z 로 빼며 취소한다 — 안쪽 모서리라 제어기가 단면 보정선(z = r)과 후퇴선 보정선(X-0.8 에서 +Z,
        // G41 은 왼쪽 = +X 로 r → 반경 −0.4 + 0.4 = 0)의 교점을 잡아 노즈 중심이 Z0.4 · 중심선(직접 보정과 같은 자리).
        // 인선이 중심 너머라 ENTRY_PAST_AXIS(화면 글은 이 경우를 따로 말한다)
        val ax = TaperCalc.calc(flip.copy(end = 0.0))
        assertEquals(listOf(TaperWarning.ENTRY_PAST_AXIS), ax.warnings)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G01 X-0.8", "G00 Z1."), ok(ax).ncG40)
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G01 X-0.8", "G40 G01 Z1."), ok(ax).ncG41G42)
        assertPt(Pt(0.4, 0.0), ok(ax).center.segs.last().end, msg = "노즈 중심이 중심선(반경 0)에")
        // 시작점 = 외경 Z-10(외경부터): 외경을 Z+ 로 깎는다 — 바이트(T3, 척 쪽으로 깎는 날)와 반대라 AGAINST_TOOL.
        // Z-10 너머(척 쪽)는 날 앞이라 외경 연장선(Z-11)으로 넘어 들어가지 않고 공구 쪽(외경 위 X52)에서 Z-10 에 직각으로 들어간다.
        // G41 시작 이동(X52 → X50)은 다음 블록(외경 Z+)과 안쪽 모서리라 노즈 중심 X50.8 Z-9.6 = 직접 보정의 인선 X50 Z-10 − 팁 벡터
        val st = TaperCalc.calc(flip.copy(start = -10.0))
        val sf = ok(st)
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), st.warnings)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-10.", "G01 X50.", "G01 Z-5.234", "G01 X39.531 Z0.", "G00 Z1."), sf.ncG40)
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-10.", "G41 G01 X50.", "G01 Z-5.", "G01 X40. Z0.", "G40 G01 X38. Z1."), sf.ncG41G42)
        assertPt(Pt.ofDia(-10.0, 52.0), sf.entry)
    }

    /**
     * 뒤집어 넣은 시작점·끝점은 그 좌표를 넘어 척·구멍 바닥·단면 안쪽으로 가지 않는다(검증 2026-10-27, 현장 안전).
     * - 내경 형상 1(06.jpg) 뒤집기, 시작 = 구멍 깊이 Z-20: 막힌 구멍이면 Z-21 은 바닥 속이다. G00 은 Z-20 에서 중심 쪽(X32)에 서고 X34 로 들어간다.
     *   그 앞 접근 줄: 구멍 밖(입구 단면 Z0 + c = Z1)에서 X32 를 맞추고 Z 로만 들어간다(구멍 X34 보다 안쪽이라 벽에 안 닿는다).
     * - 외경 형상 3 뒤집기, 시작 = 큰 외경 Z-20: 척 쪽 Z-21 이 아니라 외경 위 X52 Z-20(앞 끝면은 작은 외경 T 점 Z0 → 접근 X52 Z1).
     * - 내경 형상 1 뒤집기, 끝 = 입구 단면 X50: 단면을 바깥으로 X50 까지 — X52 로 이어 가지 않고 공구 쪽(+Z)으로 빠진다.
     * 안 뒤집은 것(시작이 자유 끝 쪽)은 지금처럼 연장선으로 들어온다(접근 줄 없음)
     */
    @Test
    fun flippedLegsStayInsideGivenCoordinates() {
        val i1 = TaperInput(inner = true, x1 = 34.0, z1 = -2.0, x2 = 40.0, z2 = 0.0, flip = true)
        val s = ok(TaperCalc.calc(i1.copy(start = -20.0)))
        assertEquals(Side.G42, s.side)
        assertEquals(listOf("G00 X32. Z1.", "G00 Z-20.", "G01 X34.", "G01 Z-2.186"), s.ncG40.take(4))
        assertEquals(listOf("(R0.4 T2)", "G00 X32. Z1.", "G00 Z-20.", "G42 G01 X34.", "G01 Z-2."), s.ncG41G42.take(5))
        // 시작을 비우면(06.jpg 를 구멍 쪽부터): 첫 G00 이 구멍 안 X31 Z-3 — 구멍 밖 Z1 에서 X31 을 맞추고 Z 로
        assertEquals(listOf("(R0.4 T2)", "G00 X31. Z1.", "G00 Z-3.", "G42 G01 X34. Z-2."), ok(TaperCalc.calc(i1)).ncG41G42.take(4))
        val o3 = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, start = -20.0, flip = true)))
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-20."), o3.ncG40.take(2))
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-20.", "G41 G01 X50."), o3.ncG41G42.subList(1, 4))
        val e1 = ok(TaperCalc.calc(i1.copy(end = 50.0)))
        assertEquals(listOf("G01 X50.", "G00 Z1."), e1.ncG40.takeLast(2))
        assertEquals(listOf("G01 X50.", "G40 G01 Z1."), e1.ncG41G42.takeLast(2))
        // 안 뒤집으면 시작(작은 외경)이 자유 끝(Z+) 쪽 — 연장선 Z6 에서, 접근 줄 없이
        val fwd = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 5.0)))
        assertEquals("G00 X40. Z6.", fwd.ncG40.first())
        assertEquals(emptyList(), fwd.approach)
        // 역테이퍼(외경 형상 5) 뒤집기: 척 쪽 작은 외경(X40)부터 — 진입 X42 로 Z 를 따라 오면 오른쪽 큰 외경(X50)에 닿는다.
        // 그 바깥 X50 + 2c = X52 로 Z 를 맞춘 뒤 X 로 내려간다(세 줄)
        val o5 = ok(TaperCalc.calc(TaperInput(type = 5, x1 = 40.0, z1 = -5.0, x2 = 50.0, z2 = 0.0, start = -20.0, flip = true)))
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-20.", "G00 X42.", "G01 X40."), o5.ncG40.take(4))
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-20.", "G00 X42.", "G41 G01 X40."), o5.ncG41G42.subList(1, 5))
        assertEquals(listOf(Pt.ofDia(1.0, 52.0), Pt.ofDia(-20.0, 52.0)), o5.approach)
        // 화면 역방향(그림을 Z 로 뒤집어 푼다 — 계산기의 +Z 가 실제 척 쪽)은 접근 줄을 끈다
        assertEquals("G00 X32. Z-20.", ok(TaperCalc.calc(i1.copy(start = -20.0, approach = false))).ncG41G42[1])
        // 계산기 역방향(화면은 안 씀)도 앞 단면을 X− 로 깎아 끝나면 같다(설계 문서의 띄운 진입 예): X48 로 이어 가지 않고 공구 쪽 Z 로 c + lift(1 + 1.6)
        val rv = ok(TaperCalc.calc(TaperInput(type = 2, reverse = true, noseR = 0.8, x1 = 60.0, z1 = 0.0, x2 = 64.368, z2 = -6.0, start = 50.0)))
        assertEquals(listOf("G01 X60. Z0.", "G01 X50.", "G40 G01 Z2.6"), rv.ncG41G42.takeLast(3))
        // 안 뒤집은 정방향의 끝 단면(어깨를 바깥으로)은 너머가 공기라 지금처럼 이어서 빠진다
        assertEquals("G40 G01 X62.", ok(TaperCalc.calc(TaperInput(type = 4, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = 60.0))).ncG41G42.last())
    }

    /**
     * FANUC 시작 · 취소 규칙에 맞춘 G41/G42 끝 · 시작(2026-10-28, simulator-design.md 13.7 — 손 계산):
     * - 뒤집기 외경 형상 3, 끝점 = 작은 외경 Z5(부품 앞 끝, 너머가 빈 끝): 팁 3 · 진행 +Z → 팁 벡터·u = −0.4 < r. 공구 쪽 직각 취소(안쪽 모서리)면
     *   교점 Z4.6 에 서서 끝 꼭짓점에 0.166 이 남는다 → `G01 Z5.` 뒤 이동 없는 `G40` 한 줄(끝 법선 — 노즈 X40.8 Z5, 접점 = 끝좌표) · `G01 X42.`(후퇴점은
     *   직각 후퇴와 같은 점, 끝좌표 너머 좌표 없음).
     * - 같은 형상을 정방향, 끝점 = 큰 외경 Z-10(척 쪽, 너머가 벽): 팁 벡터·u = r 이라 직각 취소의 교점 = 직접 보정의 끝 노즈 → `G40 G01 X52.`.
     * - 재현 A(내경 형상 1, 0.8R, 팁 1 — 역보링바를 정방향으로, AGAINST_TOOL): 볼록 첫 꺾임 연장선으로 들어가면 시작 블록의 곧은 노즈 경로가
     *   끝면 모서리를 0.147 자른다 → 경사 연장선 위 lead = T1 − u·(r + 0.01) = X61.146 Z0.573 에 직각으로(G00 = lead + n·(c + lift) = X58.6 Z1.846) 들어가
     *   `G01 X60. Z0.`. 끝(비움, 볼록 T2)은 경사 쪽 lift > 0 이라 이동 없는 `G40` 뒤 `G01` 로 전과 같은 후퇴점.
     * - 2026-10-29 검증 고침: 너머가 빈 단면 시작(뒤집기 외경 형상 2, 시작 = 어깨 바깥 X60, 표준 팁 3 — 팁 벡터·u = +r)은 lead = F − u·2r(X63.2)에
     *   직각으로 들어가 `G01 X60.` — 노즈가 직접 보정의 첫 노즈(반경 30.8)에 서서 어깨를 끝까지 깎는다(전에는 F 에 직각이라 교점 반경 29.2 에서 시작해 0.302 남음).
     *   너머가 막힌 끝(정방향 외경 형상 3 의 척 쪽 끝 Z-7.877, 팁 6 — 팁 벡터·u = 0)은 이동 없는 G40 이 아니라 공구 쪽 직각 `G40 G01` — 노즈 끝이 끝좌표에서 멈춘다.
     */
    @Test
    fun fanucStartAndCancelNumbers() {
        val f3 = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, end = 5.0, flip = true)))
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z6.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G01 Z5.", "G40", "G01 X42."), f3.ncG41G42)
        assertEquals("G00 X42.", f3.ncG40.last(), "직접 보정 후퇴점 = G41/G42 후퇴점")
        val w3 = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = -10.0)))
        assertEquals(listOf("G01 Z-10.", "G40 G01 X52."), w3.ncG41G42.takeLast(2))
        val a = TaperCalc.calc(TaperInput(inner = true, type = 1, noseR = 0.8, tip = 1, x1 = 60.0, z1 = 0.0, x2 = 48.0, z2 = -6.0))
        assertTrue(TaperWarning.AGAINST_TOOL in a.warnings)
        // lead = T1 − u·(r + 0.01) = Z0.573 X61.146 — `G01 X60. Z0.` 의 보정 노즈가 0.01 앞으로 간다(0 이면 반올림으로 거꾸로 가 간섭 검사에 걸릴 수 있다,
        // 검증 2026-10-29). G00 = lead + n·(c + lift) = lead + n·1.8
        assertEquals(
            listOf("(R0.8 T1)", "G00 X58.6 Z1.846", "G41 G01 X61.146 Z0.573", "G01 X60. Z0.", "G01 X48. Z-6.", "G40", "G01 X44.869 Z-6.434"),
            ok(a).ncG41G42,
        )
        // 너머가 빈 단면 시작: F = Z-5 X60, u = −X, n = +Z, 팁 벡터·u = +0.8 → lead = F − u·1.6 = X63.2 Z-5, G00 = lead + n·1(lift 0) = Z-4 →
        // 앞 끝면 Z0 + 1 에서 X 를 맞추는 접근 줄. 시작 블록 끝 노즈 = lead + r(n + u) = (Z-4.2, 반경 30.8) = 직접 보정의 첫 노즈(F + r·n − u·0.8)
        val f2 = ok(TaperCalc.calc(TaperInput(false, 2, false, 0.8, null, 50.0, -5.0, 40.0, 0.0, null, 1.0, start = 60.0, end = 30.0, flip = true)))
        assertEquals(
            listOf("(R0.8 T3)", "G00 X63.2 Z1.", "G00 Z-4.", "G41 G01 Z-5.", "G01 X60.", "G01 X50.", "G01 X40. Z0.", "G01 X30.", "G40 G01 Z1."),
            f2.ncG41G42,
        )
        // 너머가 막힌 끝(척 쪽으로 깎아 끝남), 팁 6(팁 벡터·u = 0): 공구 쪽 직각 — 교점 노즈(Z-6.877)의 끝이 끝좌표 Z-7.877 에서 멈춘다
        // (직접 보정은 인선이 끝좌표라 노즈 중심이 Z-7.877 — 노즈가 끝좌표 너머로 1.0). 그 교점에서 바로 취소하면 G40 상태 노즈(후퇴점 − 팁 벡터)로
        // 가며 Z 로 r − 팁 벡터·u = 1 밀려 벽에 들어가므로, 보정 중에 직각 후퇴점보다 r 더(X42.058 + 2·(c 2 + lift 2 + r 1) = X52.058) 간 뒤
        // 취소는 X 로 c 더, Z 로 1 비켜(X56.058 Z-6.877 — 노즈 Z-6.877 그대로)
        val w6 = ok(TaperCalc.calc(TaperInput(false, 3, false, 1.0, 6, 40.66, 4.331, 42.058, 2.033, null, 2.0, end = -7.877)))
        assertEquals(listOf("G01 Z-7.877", "G01 X52.058", "G40 G01 X56.058 Z-6.877"), w6.ncG41G42.takeLast(3))
        // 역테이퍼 내경 TYPE 5 표준 팁 2: 볼록 첫 꺾임 연장선 진입이 입구 모서리를 0.002 쯤 깎아 띄운 lead 꼴 — 그 G00(Z-0.012)이 앞 끝면 Z0 뒤 · 입구 안이라
        // 앞 끝면 + 1 에서 X 를 맞추고 Z 로 들어가는 접근 줄(전에는 `G00 X57.545 Z-0.021` 이 첫 줄)
        val i5 = ok(TaperCalc.calc(TaperInput(inner = true, type = 5, x1 = 60.0, z1 = 0.0, x2 = 64.368, z2 = -6.0)))
        assertEquals(listOf("(R0.4 T2)", "G00 X57.538 Z1.", "G00 Z-0.012", "G41 G01 X59.72 Z0.385", "G01 X60. Z0."), i5.ncG41G42.take(5))
    }

    /**
     * 위 손 계산을 모든 조합으로: 넣은 시작·끝 좌표 너머가 재료(척 · 구멍 바닥 · 남은 단면)인 쪽으로 노즈가 넘어가지 않는다.
     * - 시작: 뒤집어 넣은 수평 시작 요소(그림의 뒤 요소 — 척 · 구멍 바닥 쪽). 바깥 끝 방향 o 로 노즈가 가장 멀리 간 데 = (점 − 팁 벡터)·o + r 가
     *   시작 좌표·o 이하 — 직접 보정의 G00 · 경로 점, G41/G42 의 G00(G40 상태라 인선이 그 점).
     * - 끝: 단면을 재료 쪽(외경 X−, 내경 X+)으로 깎아 끝나는 것(뒤집기 · 계산기 역방향의 앞 단면). 중심 X0 까지 깎는 것은 너머가 깎은 같은 면이라 빼고.
     * 계산기 역방향(안 뒤집음)의 시작은 역바이트가 뒤(척 쪽 홈)에서 들어오는 지금 규칙이라 보지 않는다
     */
    @Test
    fun givenLegsAreNotOvershot() {
        var starts = 0
        var ends = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true)) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in radii) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.6)) for (zc in listOf(0.0, 1.0, 2.5)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = 60 + up * 2 * 6 * tan(rad(deg))
                val front = if (type <= 2) 60 - up * 12 else 6.0
                val back = if (type == 2 || type == 4) x2 + up * 12 else -12.0
                val base = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, x2, -6.0, zClear = zc, cornerR1 = r1, cornerR2 = r2, start = front, end = back)
                val s = TaperCalc.calc(if (flip) base.flipped() else base).solution ?: continue
                val tv = NoseComp.tipVector(s.tip, r)
                fun reach(p: Pt, o: Pt) = ((p - tv) dot o) + r
                val g4 = ncTrace(s.ncG41G42)
                // G41/G42 의 G40 상태 점: 접근 줄 · 진입 G00(인선이 그 점)과 후퇴
                val g4Rapid = g4.take(entryAt(s.ncG41G42) + 1).map { Pt.ofDia(it.second.toDouble(), it.first.toDouble()) }
                val g4Last = g4.last().let { Pt.ofDia(it.second.toDouble(), it.first.toDouble()) }
                val path = s.approach + listOf(s.entry, s.program.segs.first().start) + s.program.segs.map { it.end } + s.exit
                val tag = "${if (inner) "내경" else "외경"} TYPE$type ${if (reverse) "역" else "정"}${if (flip) " 뒤집기" else ""} ${deg}° r$r R$r1/$r2 여유$zc"
                val f = s.drawing.segs.first()
                val oF = (f.start - f.end).unit()
                if (flip && abs(oF.z) > abs(oF.x)) {
                    val lim = f.start dot oF
                    for (p in path) assertTrue(reach(p, oF) <= lim + 1e-6, "$tag 직접 보정 $p 의 노즈가 시작 좌표를 넘는다 ${s.ncG40}")
                    for (p in g4Rapid) assertTrue(reach(p, oF) <= lim + 0.0015, "$tag G41/G42 G00 의 노즈가 시작 좌표를 넘는다 ${s.ncG41G42}")
                    starts++
                }
                val l = s.drawing.segs.last()
                val oL = (l.end - l.start).unit()
                if (abs(oL.x) > abs(oL.z) && (oL.x < 0) != inner && l.end.x > 1e-9) {
                    val lim = l.end dot oL
                    for (p in path) assertTrue(reach(p, oL) <= lim + 1e-6, "$tag 직접 보정 $p 의 노즈가 끝 X 를 넘는다 ${s.ncG40}")
                    assertTrue(reach(g4Last, oL) <= lim + 0.0015, "$tag G41/G42 후퇴 G00 의 노즈가 끝 X 를 넘는다 ${s.ncG41G42}")
                    ends++
                }
            }
        }
        // 뒤집기의 수평 시작(형상 1 · 3 · 5) · 단면 끝(형상 1 · 2), 계산기 역방향의 단면 끝(형상 1 · 2)이 다 들어왔나
        assertTrue(starts > 500 && ends > 500, "시작 $starts · 끝 $ends")
    }

    /**
     * 뒤집은 진행이 바이트 날과 맞지 않으면 AGAINST_TOOL — 팁 사분면(모양)만 보면 진행 방향과 무관해 못 잡는다.
     * 외경 TYPE 3 을 뒤집으면 외경을 Z+ 로 깎는다(T3 의 끝날 쪽). 경사는 45° 보다 눕혀져 있을 때만(45° 면취를 내려오는 것은 된다).
     * 단면을 중심 쪽(X−)으로 깎는 것은 바이트와 맞다. 내경(T2)도 같다. 안 뒤집은 정방향 TYPE 1~4 는 지금처럼 경고가 없다
     */
    @Test
    fun flippedTravelAgainstToolWarns() {
        fun w(i: TaperInput) = TaperCalc.calc(i).also { ok(it) }.warnings
        val t3 = TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, flip = true) // 큰 외경 쪽부터, 45°
        assertEquals(emptyList(), w(t3))
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), w(t3.copy(end = 5.0)), "작은 외경을 Z+ 로")
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), w(t3.copy(start = -10.0)), "큰 외경을 Z+ 로")
        // 15° 테이퍼를 Z+ 로 내려가며
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), w(TaperInput(type = 3, x1 = 50.0, z1 = -18.66, x2 = 40.0, z2 = 0.0, flip = true)))
        // 안 뒤집으면 같은 것이 다 바이트와 맞다
        assertEquals(emptyList(), w(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 5.0, end = -10.0)))
        assertEquals(emptyList(), w(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -18.66)))
        // 외경 TYPE 4 뒤집기: 어깨 단면을 X− 로 내려오고 45° 테이퍼 — 경고 없음. 작은 외경까지(끝점 Z) 깎으면 경고
        val t4 = TaperInput(type = 4, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, flip = true)
        assertEquals(emptyList(), w(t4.copy(start = 60.0)))
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), w(t4.copy(end = 5.0)))
        // 내경 TYPE 1 뒤집기(06.jpg 를 구멍 쪽부터): 56.3° 면취를 올라와 단면을 바깥으로 — 경고 없음. 구멍(Z+)부터 깎으면 경고
        val i1 = TaperInput(inner = true, x1 = 34.0, z1 = -2.0, x2 = 40.0, z2 = 0.0, flip = true)
        assertEquals(emptyList(), w(i1))
        assertEquals(Side.G42, ok(TaperCalc.calc(i1)).side)
        assertEquals(emptyList(), w(i1.copy(end = 50.0)))
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), w(i1.copy(start = -20.0)))
    }

    /**
     * 외경 TYPE 3 (작은 외경 → 테이퍼 → 큰 외경): T1 이 오목이라 앞 외경선 위에서 진입 — 첫 보정점 Z-0.234 에서 Z 로 1.
     * 시작점 Z5 를 넣으면 거기 Z+1 에서. 끝점을 비웠으니 T2′ 에서 끝난다
     */
    @Test
    fun outerType3Numbers() {
        val s = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        assertEquals(listOf("G00 X40. Z0.766", "G01 Z-0.234", "G01 X50. Z-5.234", "G00 X52."), s.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X40. Z1.", "G42 G01 Z0.", "G01 X50. Z-5.", "G40 G01 X52. Z-6."),
            s.ncG41G42,
        )
        val f = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 5.0)))
        assertEquals(listOf("G00 X40. Z6.", "G01 Z-0.234", "G01 X50. Z-5.234", "G00 X52."), f.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X40. Z6.", "G42 G01 Z5.", "G01 Z0.", "G01 X50. Z-5.", "G40 G01 X52. Z-6."),
            f.ncG41G42,
        )
    }

    /**
     * 원본 06.jpg: `G00 X40. Z0.` → `G01 X34. Z-2.` → `G01 Z-30.` 의 보정좌표 X34.000 Z-2.186, 각 56.31°.
     * 내경 입구 면취 = 내경 TYPE 1 (T1 위가 단면, T2 왼쪽이 내경). 끝점 = 구멍 깊이 Z-30.
     */
    @Test
    fun innerType1MatchesStoreScreenshot06() {
        val s = ok(TaperCalc.calc(TaperInput(inner = true, type = 1, x1 = 40.0, z1 = 0.0, x2 = 34.0, z2 = -2.0, end = -30.0)))
        val t2 = s.points[1].nodes.single()
        assertEquals("34.000", Fmt.fixed3(t2.prog.xDia))
        assertEquals("-2.186", Fmt.fixed3(t2.prog.z))
        assertEquals(atan(1.5) * 180 / PI, s.anglesDeg.single(), 1e-12)
        assertEquals(2, s.tip)
        assertEquals(Side.G41, s.side)
        // T1′: 단면 이웃 → X 로 2×0.4(1 − tan((90° − 56.31°)/2)) = 0.558 커짐. 진입은 보정 면취선을 Z+1 로, 끝은 Z-30 에서 X 로 1 빠짐
        assertEquals(listOf("G00 X43.558 Z1.", "G01 X40.558 Z0.", "G01 X34. Z-2.186", "G01 Z-30.", "G00 X32."), s.ncG40)
        assertEquals(
            listOf("(R0.4 T2)", "G00 X43. Z1.", "G41 G01 X40. Z0.", "G01 X34. Z-2.", "G01 Z-30.", "G40 G01 X32."),
            s.ncG41G42,
        )
    }

    /** X1·Z1·X2·Z2·각도 가운데 아무거나 하나를 비우면 나머지로 푼다 */
    @Test
    fun solvesAnyOneMissingValue() {
        val deg = 30.0
        val dx = 2 * 5 * tan(rad(deg)) // Z 5 사이의 직경 차
        // (내경?, TYPE, X1, X2): 외경 1~4 는 T2 가 높고, 외경 5·내경 1~4 는 낮고, 내경 5 는 높다
        val shapes = listOf(
            Triple(false, 3, 40.0 to 40 + dx), Triple(false, 5, 40.0 to 40 - dx),
            Triple(true, 3, 40.0 to 40 - dx), Triple(true, 5, 40.0 to 40 + dx),
            Triple(false, 1, 40.0 to 40 + dx), Triple(true, 4, 40.0 to 40 - dx),
        )
        for ((inner, type, xs) in shapes) for (missing in 0..4) {
            val v = mutableListOf<Double?>(xs.first, 3.0, xs.second, -2.0, deg)
            v[missing] = null
            val tag = "내경=$inner TYPE$type 빈칸 $missing"
            val s = ok(TaperCalc.calc(TaperInput(inner, type, x1 = v[0], z1 = v[1], x2 = v[2], z2 = v[3], angleDeg = v[4])))
            assertEquals(xs.first, s.points[0].vertex.xDia, 1e-9, tag)
            assertEquals(3.0, s.points[0].vertex.z, 1e-9, tag)
            assertEquals(xs.second, s.points[1].vertex.xDia, 1e-9, tag)
            assertEquals(-2.0, s.points[1].vertex.z, 1e-9, tag)
            assertEquals(deg, s.anglesDeg.single(), 1e-9, tag)
        }
    }

    @Test
    fun missingOrContradictoryInputStops() {
        stops(TaperWarning.MISSING_INPUT, TaperCalc.calc(TaperInput()))
        stops(TaperWarning.MISSING_INPUT, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0)))
        // 다섯 값이 다 있으면 각도가 점과 맞아야 한다(0.001° 까지)
        stops(TaperWarning.CONTRADICTION, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, angleDeg = 44.0)))
        ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, angleDeg = 45.0005)))
        stops(TaperWarning.CONTRADICTION, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, angleDeg = 45.002)))
    }

    @Test
    fun angleZeroOrNinetyStops() {
        for (deg in listOf(0.0, 90.0, -5.0, 95.0)) {
            stops(TaperWarning.BAD_ANGLE, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, z2 = -5.0, angleDeg = deg)))
        }
        // 점이 수평(같은 X)·수직(같은 Z)
        stops(TaperWarning.BAD_ANGLE, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 40.0, z2 = -5.0)))
        stops(TaperWarning.BAD_ANGLE, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = 0.0)))
    }

    @Test
    fun pointsAgainstTheShapeStop() {
        // T2 가 T1 오른쪽
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = 5.0)))
        // 외경 TYPE 3 은 T2 가 높아야
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(TaperInput(type = 3, x1 = 50.0, z1 = 0.0, x2 = 40.0, z2 = -5.0)))
        // 외경 TYPE 5 는 T2 가 낮아야
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(TaperInput(type = 5, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        // 내경 TYPE 1 은 T2 가 낮아야, 내경 TYPE 5 는 높아야
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(TaperInput(inner = true, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(TaperInput(inner = true, type = 5, x1 = 50.0, z1 = 0.0, x2 = 40.0, z2 = -5.0)))
        // 비운 Z 를 풀 때도: 각도는 있는데 X 오르내림이 반대
        stops(TaperWarning.SHAPE_MISMATCH, TaperCalc.calc(TaperInput(x1 = 50.0, z1 = 0.0, x2 = 40.0, angleDeg = 30.0)))
    }

    @Test
    fun badNumbersStop() {
        val good = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)
        for (bad in listOf(
            good.copy(noseR = -0.1), good.copy(zClear = -1.0), good.copy(noseR = 0.0, zClear = 0.0), good.copy(tip = 10),
            good.copy(x1 = -40.0), good.copy(x2 = Double.NaN), good.copy(noseR = Double.POSITIVE_INFINITY),
            good.copy(cornerR1 = -1.0), good.copy(cornerR2 = Double.NaN), good.copy(start = Double.NaN),
            // 풀어 보니 지름이 음수: 외경 TYPE 5, X10 에서 Z 20 사이 30° 로 내려감
            TaperInput(type = 5, x1 = 10.0, z1 = 0.0, z2 = -20.0, angleDeg = 30.0),
            // 크기 한도(±MAX_MM): 잘못 친 12자리 수, 아주 작은 각도로 푼 Z(5 / tan 0.01° ≈ 28648 mm)
            good.copy(x2 = 999_999_999_999.0), good.copy(end = -10_000.001), good.copy(cornerR1 = 20_000.0),
            TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, angleDeg = 0.01),
        )) stops(TaperWarning.BAD_INPUT, TaperCalc.calc(bad))
        stops(TaperWarning.BAD_INPUT, TaperCalc.calcDouble(DoubleTaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, x3 = 50.0, z3 = -20_000.0)))
        assertFailsWith<IllegalArgumentException> { TaperCalc.calc(good.copy(type = 6)) }
    }

    /** C0.3 면취에 노즈 0.8: 볼록 두 모서리라 다 깎이고 숫자도 맞다 — 오목 모서리가 없으니 NOSE_GT_SLOPE 도 없다(내경 · 역방향도) */
    @Test
    fun noseLongerThanConvexChamferDoesNotWarn() {
        val r = 0.8
        val res = TaperCalc.calc(TaperInput(noseR = r, x1 = 39.4, z1 = 0.0, x2 = 40.0, z2 = -0.3))
        val s = ok(res)
        assertEquals(emptyList(), res.warnings)
        assertPt(Pt(0.0, 19.7) + closedShift(false, true, false, true, 45.0, r), sharp(s.points[0]), 1e-9)
        assertPt(Pt(-0.3, 20.0) + closedShift(false, true, false, false, 45.0, r), sharp(s.points[1]), 1e-9)
        assertEquals(emptyList(), TaperCalc.calc(TaperInput(inner = true, noseR = r, x1 = 40.6, z1 = 0.0, x2 = 40.0, z2 = -0.3)).warnings)
        val rev = TaperCalc.calc(TaperInput(reverse = true, noseR = r, x1 = 39.4, z1 = 0.0, x2 = 40.0, z2 = -0.3))
        ok(rev)
        assertTrue(TaperWarning.NOSE_GT_SLOPE !in rev.warnings)
    }

    /**
     * 오목 T1(외경 TYPE 3)의 짧은 경사 0.583 에 노즈 0.8: 오목에 접한 원이 경사 위 0.8·tan(15.48°) = 0.222 에서 닿아 숫자는 맞지만
     * T1 에 r 이 남는다 — NOSE_GT_SLOPE(역방향도 같은 모양이라 같다). 오목 모서리 R = r 이면 r 이 남지 않아 CONCAVE_R_LE_NOSE 만
     */
    @Test
    fun noseLongerThanSlopeAtConcaveCornerWarns() {
        val i = TaperInput(type = 3, noseR = 0.8, x1 = 40.0, z1 = 0.0, x2 = 40.6, z2 = -0.5)
        val res = TaperCalc.calc(i)
        ok(res)
        assertEquals(listOf(TaperWarning.NOSE_GT_SLOPE), res.warnings)
        val rev = TaperCalc.calc(i.copy(reverse = true))
        ok(rev)
        assertTrue(TaperWarning.NOSE_GT_SLOPE in rev.warnings)
        val fillet = TaperCalc.calc(i.copy(cornerR1 = 0.8))
        ok(fillet)
        assertEquals(listOf(TaperWarning.CONCAVE_R_LE_NOSE), fillet.warnings)
        // 내경 TYPE 3(큰 내경 → 테이퍼 → 작은 내경)도 T1 이 오목
        val inner = TaperCalc.calc(TaperInput(inner = true, type = 3, noseR = 0.8, x1 = 40.6, z1 = 0.0, x2 = 40.0, z2 = -0.5))
        ok(inner)
        assertEquals(listOf(TaperWarning.NOSE_GT_SLOPE), inner.warnings)
    }

    /** 오목 모서리 둘(TYPE 4) 사이의 짧은 경사에 큰 노즈: 공구가 경사에 못 닿는다 — 숫자를 내지 않는다 */
    @Test
    fun noseCannotReachShortSlopeStops() {
        stops(
            TaperWarning.NOSE_UNREACHABLE,
            TaperCalc.calc(TaperInput(type = 4, noseR = 1.2, x1 = 40.0, z1 = 0.0, x2 = 40.4, z2 = -0.2)),
        )
    }

    /**
     * 한쪽 끝이 볼록, 다른 끝이 오목인 짧은 경사도 깎이지 않으면 멈춘다(전에는 볼록 쪽 연장 교점 때문에 못 알아채고 숫자를 냈다).
     * - 외경 TYPE 5 역방향: T2 는 오목(공구 쪽 사이각 101.7°)이라 두 요소에 접한 원은 경사 위 1.2/tan(50.85°) = 0.976 에서
     *   닿는데 경사는 0.627 이다. T1 은 볼록.
     * - 겹테이퍼 T1(볼록)–T2(골, 74.3°): 골에 접한 원이 첫 경사 위 1.2/tan(37.15°) = 1.58 에서 닿는데 경사는 1.221 이다.
     *   경사가 r 보다 길어 NOSE_GT_SLOPE 로도 못 잡는 경우다.
     */
    @Test
    fun shortSlopeNextToConvexCornerStops() {
        stops(
            TaperWarning.NOSE_UNREACHABLE,
            TaperCalc.calc(
                TaperInput(type = 5, reverse = true, noseR = 1.2, x1 = 39.096, z1 = -17.254, x2 = 37.869, z2 = -17.381),
            ),
        )
        stops(
            TaperWarning.NOSE_UNREACHABLE,
            TaperCalc.calcDouble(DoubleTaperInput(noseR = 1.2, x1 = 50.0, z1 = 0.0, x2 = 48.6, z2 = -1.0, x3 = 60.0, z3 = -3.0)),
        )
    }

    /** 외경인데 내경 팁(2)을 고르면 외경선이 팁 반대쪽 — 경고 */
    @Test
    fun tipOverrideAgainstShapeWarns() {
        val res = TaperCalc.calc(TaperInput(type = 3, tip = 2, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0))
        assertEquals(2, ok(res).tip)
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), res.warnings)
        // 팁 0(중심)은 사분면이 없어 보지 않는다
        assertEquals(emptyList(), TaperCalc.calc(TaperInput(type = 3, tip = 0, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)).warnings)
    }

    /** 같은 그림을 반대 끝부터: 칸의 점·모서리 R·시작·끝을 맞바꾸고 flip 을 뒤집는다(겹테이퍼는 T1 ⇄ T3, T2 그대로) */
    private fun TaperInput.flipped() =
        copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start, flip = !flip)

    private fun DoubleTaperInput.flipped() =
        copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start, flip = !flip)

    private fun Seg.backwards(): Seg = when (this) {
        is Line -> Line(end, start)
        is Arc -> Arc(end, start, center, !cw)
    }

    private fun assertBackwards(a: Contour, b: Contour, tag: String) {
        assertEquals(a.segs.size, b.segs.size, tag)
        for ((p, q) in a.segs.reversed().map { it.backwards() }.zip(b.segs)) {
            assertEquals(p is Arc, q is Arc, "$tag $p ↔ $q")
            assertPt(p.start, q.start, msg = tag)
            assertPt(p.end, q.end, msg = tag)
            if (p is Arc && q is Arc) {
                assertEquals(p.cw, q.cw, tag)
                assertPt(p.center, q.center, msg = tag)
            }
        }
    }

    /**
     * u 를 뒤집은 것이 f: 꼭짓점 보정점(가상 인선)은 같은 점이고 순서만 반대, 보정 쪽은 반대, 팁은 같다.
     * 모서리 R 은 같은 원호를 반대로 지난다(G02 ↔ G03, 보정 R 같음). 도면·노즈 중심·직접 보정 경로도 거꾸로 같다.
     * 경고는 모양에서 오는 것은 같고, 진행 방향(AGAINST_TOOL)·진입 자리(ENTRY_PAST_AXIS)를 따르는 것만 다를 수 있다
     */
    private fun assertFlipped(u: TaperResult, f: TaperResult, tag: String) {
        val a = u.solution
        val b = f.solution
        if (a == null) {
            assertNull(b, "$tag 안 뒤집은 것은 멈추는데 뒤집은 것은 숫자가 나왔다")
            assertEquals(u.warnings, f.warnings, tag)
            return
        }
        assertNotNull(b, "$tag 뒤집으면 멈춘다 ${f.warnings}")
        val byDirection = setOf(TaperWarning.AGAINST_TOOL, TaperWarning.ENTRY_PAST_AXIS)
        assertEquals(u.warnings - byDirection, f.warnings - byDirection, tag)
        assertEquals(a.tip, b.tip, tag)
        assertTrue(a.side != b.side, "$tag 보정 쪽 ${a.side} ${b.side}")
        assertEquals(a.fromStart, b.toEnd, tag)
        assertEquals(a.toEnd, b.fromStart, tag)
        assertEquals(a.anglesDeg.size, b.anglesDeg.size, tag)
        for ((p, q) in a.anglesDeg.reversed().zip(b.anglesDeg)) assertEquals(p, q, 1e-9, tag)
        assertEquals(a.points.size, b.points.size, tag)
        for ((p, q) in a.points.reversed().zip(b.points)) {
            assertPt(p.vertex, q.vertex, msg = "$tag 꼭짓점")
            assertEquals(p.nodes.size, q.nodes.size, tag)
            for ((m, n) in p.nodes.reversed().zip(q.nodes)) {
                assertPt(m.drawn, n.drawn, msg = "$tag 도면 점")
                assertPt(m.prog, n.prog, msg = "$tag 보정점")
            }
            val c = p.corner
            val d = q.corner
            assertEquals(c == null, d == null, tag)
            if (c != null && d != null) {
                assertEquals(c.r, d.r, 1e-12, tag)
                assertEquals(c.progR == null, d.progR == null, tag)
                if (c.progR != null) assertEquals(c.progR, d.progR!!, 1e-9, tag)
                assertPt(c.center, d.center, msg = tag)
                assertEquals(!c.cw, d.cw, "$tag 모서리 R 은 반대로 지난다")
            }
        }
        assertBackwards(a.drawing, b.drawing, "$tag 도면")
        assertBackwards(a.center, b.center, "$tag 노즈 중심")
        assertBackwards(a.program, b.program, "$tag 직접 보정 경로")
    }

    /**
     * 뒤집기(사용자 요청 2026-10-27): 모든 종류(외경·내경) × 형상 1~5 × 정/역방향 × 각 × r × 모서리 R × 시작·끝점 유무와
     * 겹테이퍼에서, 뒤집은 결과의 보정점이 안 뒤집은 것과 같고(순서만 반대) 보정 쪽이 반대인지
     */
    @Test
    fun flipKeepsCompPointsReversesOrder() {
        var checked = 0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) {
            for (deg in listOf(10.0, 30.0, 45.0, 60.0, 80.0)) for (r in radii) {
                for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6, 0.3 to 0.3)) {
                    val up = if (inner == (type == 5)) 1.0 else -1.0
                    val x2 = 60 + up * 2 * 6 * tan(rad(deg))
                    val base = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, x2, -6.0, cornerR1 = r1, cornerR2 = r2)
                    // 그림의 앞(T1 앞)·뒤(T2 뒤) 요소를 6 mm(단면은 X, 수평은 Z)
                    val front = if (type <= 2) 60 - up * 12 else 6.0
                    val back = if (type == 2 || type == 4) x2 + up * 12 else -12.0
                    for ((st, en) in listOf(null to null, front to null, null to back, front to back)) {
                        val i = base.copy(start = st, end = en)
                        assertFlipped(TaperCalc.calc(i), TaperCalc.calc(i.flipped()), i.toString())
                        checked++
                    }
                }
            }
        }
        // 산·골·오르막·내리막, 두 경사각이 다르게(각도 순서도 본다)
        val shapes = listOf(listOf(40.0, 50.0, 44.0), listOf(50.0, 40.0, 46.0), listOf(40.0, 44.0, 56.0), listOf(56.0, 46.0, 40.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (r in radii) {
            for (rc in listOf(listOf(0.0, 0.0, 0.0), listOf(1.0, 2.0, 0.5), listOf(0.3, 0.0, 1.2))) {
                for ((st, en) in listOf(null to null, 4.0 to null, null to -14.0, 4.0 to -14.0)) {
                    val i = DoubleTaperInput(
                        inner, reverse, r, null, x[0], 0.0, x[1], -5.0, x[2], -10.0,
                        cornerR1 = rc[0], cornerR2 = rc[1], cornerR3 = rc[2], start = st, end = en,
                    )
                    assertFlipped(TaperCalc.calcDouble(i), TaperCalc.calcDouble(i.flipped()), i.toString())
                    checked++
                }
            }
        }
        assertTrue(checked > 8000, "확인한 사례 $checked")
    }

    @Test
    fun defaultsForScreen() {
        val i = TaperInput()
        assertEquals(0.4, i.noseR)
        assertEquals(1.0, i.zClear)
        assertEquals(1, i.type)
        assertEquals(false, i.reverse)
        assertEquals(false, i.flip)
        assertEquals(false, DoubleTaperInput().flip)
        assertEquals(null, i.tip)
        assertEquals(0.0, i.cornerR1)
        assertEquals(null, i.start)
        assertEquals(listOf(0.2, 0.4, 0.8, 1.2), TaperCalc.NOSE_R_CHOICES)
    }

    // ---- 시작점·끝점 ----

    /**
     * 비운 시작·끝점의 그림 = 넣은 5 mm 값과 같다. 단면은 X(직경), 수평은 Z. 깎는 것은 넣었을 때만 —
     * 끝점을 비우면 T2′ 에서 끝나고, 넣으면 그 좌표까지 끝 요소를 깎는다
     */
    @Test
    fun legCoordinates() {
        val base = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)
        val blank = ok(TaperCalc.calc(base))
        val end = ok(TaperCalc.calc(base.copy(end = -10.0)))
        assertEquals(blank.drawing, end.drawing)
        assertEquals(blank.points, end.points)
        assertEquals(blank.drawing, ok(TaperCalc.calc(base.copy(start = 30.0))).drawing)
        assertTrue(!blank.toEnd && end.toEnd)
        assertEquals("G01 X50. Z-5.234", blank.ncG40[blank.ncG40.size - 2])
        assertEquals("G01 Z-10.", end.ncG40[end.ncG40.size - 2])
        // TYPE 4: 끝 = 어깨 단면 위 X60. 인선이 X60 에 오고, 단면은 바깥(위)으로 이어서 빠진다
        val s4 = ok(TaperCalc.calc(base.copy(type = 4, start = 8.0, end = 60.0)))
        assertEquals(listOf("G00 X40. Z9.", "G01 Z-0.234", "G01 X49.531 Z-5.", "G01 X60.", "G00 X62."), s4.ncG40)
        assertEquals("G40 G01 X62.", s4.ncG41G42.last())
        assertPt(Pt.ofDia(-5.0, 60.0), s4.drawing.segs.last().end)
        // 내경 TYPE 1: 끝 = 구멍 깊이 Z-20
        val si = ok(TaperCalc.calc(TaperInput(inner = true, x1 = 40.0, z1 = 0.0, x2 = 34.0, z2 = -2.0, end = -20.0)))
        assertEquals("G01 Z-20.", si.ncG40[si.ncG40.size - 2])
        assertEquals("G01 Z-20.", si.ncG41G42[si.ncG41G42.size - 2])
    }

    @Test
    fun legOnTheWrongSideStops() {
        val t1 = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0) // 단면 시작 X 는 40 아래, 끝 Z 는 -5 왼쪽
        for (bad in listOf(t1.copy(start = 40.0), t1.copy(start = 45.0), t1.copy(start = -2.0), t1.copy(end = -5.0), t1.copy(end = -3.0))) {
            stops(TaperWarning.BAD_LEG, TaperCalc.calc(bad))
        }
        val t3 = t1.copy(type = 3) // 수평 시작 Z 는 0 오른쪽
        stops(TaperWarning.BAD_LEG, TaperCalc.calc(t3.copy(start = -1.0)))
        // 끝 Z 가 T2 에서 0.1: 볼록 T2 의 보정점(Z-5.234)이 이미 끝좌표를 지나 공구가 되돌아가야 한다
        stops(TaperWarning.BAD_LEG, TaperCalc.calc(t3.copy(end = -5.1)))
        // 0.3: 인선이 Z-5.3 에 멈추면 노즈 중심은 Z-4.9 라 노즈가 X50 외경(Z-5 ~ -5.3)에 한 번도 닿지 않는다(T2 에서도 0.012 떨어짐)
        stops(TaperWarning.BAD_LEG, TaperCalc.calc(t3.copy(end = -5.3)))
        ok(TaperCalc.calc(t3.copy(end = -5.5)))
        // 내경 TYPE 4 의 안쪽 단면은 아래로 — 끝 X 는 T2 보다 작고 0 이상
        val i4 = TaperInput(inner = true, type = 4, x1 = 40.0, z1 = 0.0, x2 = 30.0, z2 = -5.0)
        ok(TaperCalc.calc(i4.copy(end = 0.0)))
        stops(TaperWarning.BAD_LEG, TaperCalc.calc(i4.copy(end = 32.0)))
    }

    // ---- Z 여유(진입) ----

    /** Z 여유 0 도 된다 — 진입·후퇴 거리는 max(Z 여유, 노즈R). 노즈R 과 Z 여유가 둘 다 0 이면 진입 이동이 없어 멈춘다 */
    @Test
    fun zeroClearance() {
        val base = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, zClear = 0.0)
        // 면취선 연장을 Z 로 노즈R(0.4) 바깥
        val s = ok(TaperCalc.calc(base))
        assertEquals("G00 X38.731 Z0.4", s.ncG40.first())
        assertEquals("G00 X39.2 Z0.4", s.ncG41G42[1])
        // 노즈R 0 이면 Z 여유가 진입 거리
        assertEquals("G00 X39. Z0.5", ok(TaperCalc.calc(base.copy(noseR = 0.0, zClear = 0.5))).ncG40.first())
        stops(TaperWarning.BAD_INPUT, TaperCalc.calc(base.copy(noseR = 0.0)))
        val d = DoubleTaperInput(noseR = 0.0, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, x3 = 40.0, z3 = -10.0, zClear = 0.0)
        stops(TaperWarning.BAD_INPUT, TaperCalc.calcDouble(d))
        ok(TaperCalc.calcDouble(d.copy(noseR = 0.4)))
    }

    /**
     * 현장: "Z 여유를 바꿔도 G00 이 따라오지 않는다". 모든 형상 · 방향 · 모서리 R · 시작점 유무에서 두 NC 의 G00 진입과
     * 화면의 진입점(직접 보정)이 첫 깎는 점에서 Z 로 꼭 max(Z 여유, 노즈R) 떨어져야 한다 — 직접 보정은 첫 보정점(시작점을 넣었으면
     * 시작점′), G41/G42 는 첫 도면점. 표준 팁은 띄울 일이 없다(lift 0) — 계산기 자체의 역방향(화면은 쓰지 않는다)에서 바이트와
     * 반대인 단면으로 들어갈 때만 G41/G42 진입을 노즈가 단면을 넘지 않게 더 띄운다.
     * 하나만 다르다: 뒤집어 넣은 수평 시작점(척 쪽 외경 · 구멍 바닥 쪽 내경)부터 깎으면 Z 로 c 는 시작점 너머(척 · 바닥)라, 그 Z 에서 공구 쪽으로 X 로 c
     */
    @Test
    fun entryFollowsZClearance() {
        var checked = 0
        var pulled = 0
        fun check(tag: String, res: TaperResult, zClear: Double, r: Double, flip: Boolean) {
            val s = ok(res)
            val c = max(zClear, r)
            // 진입 G00 = 처음 G00 줄들의 마지막(뒤집어 척 쪽에서 시작하면 그 앞에 접근 줄)
            val g40 = ncTrace(s.ncG40)[s.approach.size]
            assertEquals(entryAt(s.ncG40), s.approach.size, "$tag 접근 줄 수")
            assertEquals(printed(s.entry), g40, "$tag 화면 진입점 = 직접 보정 G00")
            val skip = if (s.fromStart) 0 else 1
            val first = s.program.segs[skip].start
            val g4 = ncTrace(s.ncG41G42)[entryAt(s.ncG41G42)]
            val firstD = s.drawing.segs[skip].start
            val u0 = (s.drawing.segs[0].end - s.drawing.segs[0].start).unit()
            if (flip && s.fromStart && abs(u0.z) > abs(u0.x) && u0.z * NoseComp.tipVector(s.tip, 1.0).z <= 0) {
                assertEquals(first.z, s.entry.z, 1e-9, "$tag 직접 보정 진입 Z ${s.ncG40}")
                assertEquals(c, abs(s.entry.x - first.x), 1e-9, "$tag 직접 보정 진입 X ${s.ncG40}")
                assertEquals(Fmt.nc(firstD.z), g4.second, "$tag G41/G42 진입 Z ${s.ncG41G42}")
                assertEquals(2 * c, abs(g4.first.toDouble() - firstD.xDia), 0.001 + 1e-9, "$tag G41/G42 진입 X ${s.ncG41G42}")
                pulled++
                checked++
                return
            }
            assertEquals(c, abs(s.entry.z - first.z), 1e-9, "$tag 직접 보정 진입 Z ${s.ncG40}")
            val dz = abs(g4.second.toDouble() - Fmt.nc(firstD.z).toDouble())
            // 깎지 않는 수평 시작 요소 선을 따라 들어가면 첫 보정점이 도면 꼭짓점보다 뒤일 때(뒤집기 — 인선이 노즈 뒤에 끌려감)
            // 직접 보정 진입점까지 물러난다: max(c, 직접 보정 진입점의 Z 거리)
            val d0 = s.drawing.segs[0]
            val alongLeg = !s.fromStart && abs(d0.end.x - d0.start.x) < 1e-12 && Fmt.nc(firstD.xDia) == g4.first
            val want = if (alongLeg) max(c, abs(s.entry.z - firstD.z)) else c
            if (TaperWarning.AGAINST_TOOL in res.warnings) {
                // 바이트 반대 모양의 띄운 진입: 첫 깎는 점에서 Z 로 c 이상이거나, 직각으로 들어가 따라갈 선(볼록 첫 꺾임이면 첫 깎는 요소의
                // 연장선 — 2026-10-28)에서 c 이상 띄운 점
                val p4 = Pt.ofDia(g4.second.toDouble(), g4.first.toDouble())
                val d1 = s.drawing.segs[skip]
                val off = abs((d1.end - d1.start).unit() cross (p4 - d1.start))
                assertTrue(dz >= c - 0.001 || off >= c - 0.001, "$tag G41/G42 진입 Z ${s.ncG41G42}")
            } else {
                assertEquals(Fmt.nc(firstD.z + (if (s.entry.z > first.z) want else -want)), g4.second, "$tag G41/G42 진입 Z ${s.ncG41G42}")
            }
            checked++
        }
        val zs = listOf(0.0, 0.2, 0.5, 1.0, 2.0, 3.0)
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (r in listOf(0.4, 0.8)) {
            val up = if (inner == (type == 5)) 1.0 else -1.0
            for ((r1, r2) in listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0)) for (lead in listOf(false, true)) for (zc in zs) {
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, 60 + up * 10, -5.0, zClear = zc, cornerR1 = r1, cornerR2 = r2)
                // 그림 오른쪽(시작점)·왼쪽(끝점) 요소를 6 mm 깎기(단면은 X, 수평은 Z) — 정방향은 오른쪽이 가공 시작 쪽
                val right = if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                val left = if (type == 2 || type == 4) inp.copy(end = 60 + up * 22) else inp.copy(end = -11.0)
                val i = when {
                    !lead -> inp
                    reverse -> left
                    else -> right
                }
                check(i.toString(), TaperCalc.calc(i), zc, r, false)
                // 뒤집기: 같은 그림을 반대 끝부터 — 가공 시작 쪽 요소가 반대 끝이다
                val f = (if (!lead) inp else if (reverse) right else left).flipped()
                check(f.toString(), TaperCalc.calc(f), zc, r, true)
            }
        }
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))) {
            for (rc in listOf(0.0, 1.0)) for (zc in zs) {
                val i = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, zc, rc, rc, rc)
                check(i.toString(), TaperCalc.calcDouble(i), zc, 0.4, false)
                check(i.flipped().toString(), TaperCalc.calcDouble(i.flipped()), zc, 0.4, true)
            }
        }
        assertTrue(checked > 3000, "확인한 사례 $checked")
        assertTrue(pulled > 50, "뒤집어 넣은 수평 시작점(X 로 진입) $pulled")
    }

    // ---- 모서리 R ----

    /**
     * 외경 TYPE 1, T1(단면 → 45° 면취, 볼록 왼쪽 꺾임)에 R1, 0.4R. 손 계산:
     * 접선 길이 = R·tan(22.5°) = 0.41421 → R 시작 (Z0, X39.172), R 끝 (Z−0.293, X40.586), 중심 (Z−1, X39.172), G03.
     * 보정 R = 1.4. 보정 접점 = 중심 + (접점 − 중심)×1.4 + 팁 벡터(−0.4, −0.4)
     *   → 시작 (Z0, X38.372), 끝 (Z−0.41, X40.351).
     */
    @Test
    fun convexCornerR() {
        val res = TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 1.0, start = 30.0))
        val s = ok(res)
        assertEquals(emptyList(), res.warnings)
        val p = s.points[0]
        val k = tan(rad(22.5))
        val c = Pt(-1.0, 20 - k)
        assertEquals(CornerArc(1.0, 1.4, false, c).copy(center = p.corner!!.center), p.corner)
        assertPt(c, p.corner.center, 1e-12)
        assertEquals(1.4, p.corner.progR!!, 1e-12)
        val a = Pt(0.0, 20 - k)
        val b = Pt(0.0, 20.0) + Pt(-1.0, 1.0) * (k / sqrt(2.0))
        assertPt(a, p.nodes[0].drawn, 1e-12)
        assertPt(b, p.nodes[1].drawn, 1e-12)
        val tv = Pt(-0.4, -0.4)
        assertPt(c + (a - c) * 1.4 + tv, p.nodes[0].prog, 1e-12)
        assertPt(c + (b - c) * 1.4 + tv, p.nodes[1].prog, 1e-12)
        assertEquals(
            listOf("G00 X30. Z1.", "G01 Z0.", "G01 X38.372", "G03 X40.351 Z-0.41 R1.4", "G01 X50. Z-5.234", "G00 X52."),
            s.ncG40,
        )
        assertEquals(
            listOf(
                "(R0.4 T3)", "G00 X30. Z1.", "G42 G01 Z0.", "G01 X39.172", "G03 X40.586 Z-0.293 R1.", "G01 X50. Z-5.",
                "G40 G01 X52. Z-6.",
            ),
            s.ncG41G42,
        )
        // T2 는 날카로운 그대로
        assertPt(Pt(-5 - NoseR.taperDeltaZ(0.4, 45.0), 25.0), sharp(s.points[1]))
        // 시작점을 비우면 T1 에 모서리 R 이 있어 경사가 아니라 단면 바깥(공구 쪽 Z+1)에서 R 시작′ 으로 들어간다.
        // G41/G42 는 단면 선의 r + 0.01 아래(X38.352 — 다음 `G01 X39.172` 의 보정 노즈가 0.01 앞으로 가게, 0 이면 반올림으로 거꾸로 가 간섭 검사에
        // 걸릴 수 있다)에 직각으로 들어가 선을 따라 R 시작까지
        val b0 = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 1.0)))
        assertEquals(listOf("G00 X38.372 Z1.", "G01 Z0.", "G03 X40.351 Z-0.41 R1.4"), b0.ncG40.take(3))
        assertEquals(
            listOf("(R0.4 T3)", "G00 X38.352 Z1.", "G42 G01 Z0.", "G01 X39.172", "G03 X40.586 Z-0.293 R1."),
            b0.ncG41G42.take(5),
        )
    }

    /**
     * 외경 TYPE 3, T1(작은 외경 → 45° 경사, 오목 오른쪽 꺾임)에 R1: 중심 (Z0.414, X42), G02, 보정 R = 1 − 0.4 = 0.6.
     * 보정 접점 → 시작 (Z0.014, X40), 끝 (Z−0.41, X40.351).
     */
    @Test
    fun concaveCornerR() {
        val res = TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 1.0, start = 5.0))
        val s = ok(res)
        assertEquals(emptyList(), res.warnings)
        val k = tan(rad(22.5))
        val p = s.points[0]
        val c = Pt(k, 21.0)
        assertPt(c, p.corner!!.center, 1e-12)
        assertEquals(true, p.corner.cw)
        assertEquals(0.6, p.corner.progR!!, 1e-12)
        val a = Pt(k, 20.0)
        val b = Pt(-k / sqrt(2.0), 20 + k / sqrt(2.0))
        val tv = Pt(-0.4, -0.4)
        assertPt(c + (a - c) * 0.6 + tv, p.nodes[0].prog, 1e-12)
        assertPt(c + (b - c) * 0.6 + tv, p.nodes[1].prog, 1e-12)
        assertEquals(listOf("G00 X40. Z6.", "G01 Z0.014", "G02 X40.351 Z-0.41 R0.6", "G01 X50. Z-5.234", "G00 X52."), s.ncG40)
        assertEquals("G02 X40.586 Z-0.293 R1.", s.ncG41G42[4])
    }

    /** 오목 R ≤ 노즈R: R = r 은 정확(보정 원호가 점), R < r 은 r 이 남는다 — 둘 다 경고와 함께 숫자, progR = null */
    @Test
    fun concaveCornerRNotBiggerThanNose() {
        for (rc in listOf(0.4, 0.3)) {
            val res = TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = rc))
            val s = ok(res)
            assertEquals(listOf(TaperWarning.CONCAVE_R_LE_NOSE), res.warnings, "R$rc")
            assertNull(s.points[0].corner!!.progR, "R$rc")
            assertEquals(rc, s.points[0].corner!!.r, 1e-12)
            assertTrue(s.ncG40.none { it.startsWith("G02") || it.startsWith("G03") }, "R$rc ${s.ncG40}")
        }
        // R = r 이면 보정점이 날카로운 모서리와 같다(공구가 꼭 맞게 들어간다)
        val s = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 0.4)))
        assertPt(Pt(-NoseR.taperDeltaZ(0.4, 45.0), 20.0), s.points[0].nodes[0].prog)
    }

    @Test
    fun cornerRTooBigStops() {
        val base = TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0) // 면취 길이 7.07, 45° 꺾임 → 접선 길이 0.414R
        stops(TaperWarning.CORNER_R_TOO_BIG, TaperCalc.calc(base.copy(cornerR1 = 30.0)))
        ok(TaperCalc.calc(base.copy(cornerR1 = 5.0, cornerR2 = 5.0)))
        // 두 R 의 접점이 경사 위에서 겹친다: 0.414 × 10 × 2 = 8.28 > 7.07
        stops(TaperWarning.CORNER_R_TOO_BIG, TaperCalc.calc(base.copy(cornerR1 = 10.0, cornerR2 = 10.0)))
        // 앞 요소(단면 X39 ~ X40, 반경 0.5)보다 긴 접선
        stops(TaperWarning.CORNER_R_TOO_BIG, TaperCalc.calc(base.copy(start = 39.0, cornerR1 = 2.0)))
        // 겹테이퍼 가운데
        val d = DoubleTaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, x3 = 40.0, z3 = -10.0)
        ok(TaperCalc.calcDouble(d.copy(cornerR2 = 2.0)))
        stops(TaperWarning.CORNER_R_TOO_BIG, TaperCalc.calcDouble(d.copy(cornerR2 = 20.0)))
    }

    /** R 0 은 지금(모서리 R 없을 때)과 같다 */
    @Test
    fun zeroCornerRIsSharp() {
        val a = TaperInput(type = 2, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)
        assertEquals(TaperCalc.calc(a), TaperCalc.calc(a.copy(cornerR1 = 0.0, cornerR2 = 0.0)))
        assertTrue(ok(TaperCalc.calc(a)).points.all { it.corner == null && it.nodes.size == 1 })
    }

    // ---- G41/G42 프로그램 ↔ 직접 보정 프로그램 ----

    /** NC 글 → 요소(직선·R 지령 원호, 90° 이하). 주석 줄은 건너뛴다. 줄마다 (G 코드들, 요소) — 첫 G00 은 위치만 */
    private fun parseMoves(nc: List<String>): List<Pair<List<String>, Seg>> {
        var p: Pt? = null
        val out = mutableListOf<Pair<List<String>, Seg>>()
        for (line in nc.filter { !it.startsWith("(") }) {
            val w = line.split(" ")
            val g = w.filter { it[0] == 'G' }
            val v = w.filter { it[0] != 'G' }.associate { it[0] to it.substring(1).toDouble() }
            val q = Pt(v['Z'] ?: p!!.z, v['X']?.let { it / 2 } ?: p!!.x)
            val from = p
            p = q
            if (from == null) continue
            val cw = "G02" in g
            out += g to if (cw || "G03" in g) {
                val rr = v.getValue('R')
                val h = (q - from).length / 2
                val ch = (q - from).unit()
                val d = sqrt(max(rr * rr - h * h, 0.0))
                Arc(from, q, (from + q) * 0.5 + Pt(-ch.x, ch.z) * (if (cw) -d else d), cw)
            } else {
                Line(from, q)
            }
        }
        return out
    }

    private fun ends(c: List<Seg>) = listOf(c.first().start) + c.map { it.end }

    /** 앞 점과 같은 점(1e-6)은 뺀다 — 직접 보정 NC 는 안 움직이는 줄을 쓰지 않는다 */
    private fun distinct(ps: List<Pt>) = ps.fold(listOf<Pt>()) { acc, q -> if (acc.isNotEmpty() && (acc.last() - q).length < 1e-6) acc else acc + q }

    private fun near(a: Pt, b: Pt) = abs(a.xDia - b.xDia) <= 0.001 + 1e-9 && abs(a.z - b.z) <= 0.001 + 1e-9

    /**
     * G41/G42 프로그램의 형상 부분(보정 시작 블록 끝 = 시작점 ~ G40 앞 = 끝점, 도면 좌표)을 글에서 읽어 NoseComp 로 보정하면
     * 직접 보정 프로그램의 좌표와 0.001 안에서 같다. 보정은 시작·끝을 열린 끝(그 요소의 법선)으로 본다:
     * 시작 블록 끝에서 보정이 다 걸리고, 마지막 블록 끝에서 노즈 접점이 끝좌표에 온다.
     * 처음 점은 약속이 달라 따로 본다 — 직접 보정은 인선을 시작 좌표에 두므로 단면에 직각으로 들어가면 (G41/G42) − (직접 보정) = u·(r + 팁 벡터·u).
     * 끝 점(끝점을 넣었을 때)은 취소 블록과의 관계로 정해져(FF 5.4.4) 제어기 모형(시뮬레이터 GSim)으로 본다: (G41/G42) − (직접 보정)
     * = u·min(0, 팁 벡터·u) — 공구 쪽 직각 취소의 교점은 너머가 벽인 끝(팁 벡터·u = r)에서 직접 보정과 같고, 그 밖은 이동 없는 G40 ·
     * 바깥으로 이어 빼기로 노즈 접점이 끝좌표에 선다(2026-10-28 고침 — 전에는 u·(팁 벡터·u), 곧 'G41/G42 인선이 r 더 감' 이라 했으나 안쪽 모서리
     * 취소의 교점을 빠뜨린 것이었다, simulator-design.md 13.7).
     * 끝점을 비웠으면 끝 요소는 없고 보정 취소가 마지막 꺾임을 정한다: 볼록이면 경사를 그대로 늘여 나가(바이트 반대쪽이면 이동 없는 G40) 노즈가
     * 경사 끝에 직각으로 서고(직접 보정의 마지막 점은 그 경사 보정선 위), 아니면 끝 요소 방향으로 나가 제어기가 끝 요소와의 교점
     * (= 직접 보정의 마지막 점)을 잡는다(바이트 반대쪽이면 끝 요소 선을 따라 한 블록 더 간 뒤 이동 없는 G40).
     * 함께: 보정 시작은 G01 이고 길이 ≥ r, 취소는 G40 G01 직선(또는 이동 없는 G40 + G01), G41/G42 는 표대로.
     */
    @Test
    fun g41g42ProgramCompensatesToDirectProgram() {
        var checked = 0
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (type in 1..5) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 0.8)) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = 60 + up * 2 * 6 * tan(rad(deg))
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, Fmt.nc(x2).toDouble(), -6.0, cornerR1 = r1, cornerR2 = r2)
                // 시작·끝점을 비운 것과, 그림 오른쪽(T1 앞)·왼쪽(T2 뒤) 요소를 6 mm 깎는 것(단면은 X, 수평은 Z).
                // 뒤집기(같은 그림을 반대 끝부터)는 칸의 점·시작·끝을 맞바꿔 넣는다
                val right = if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                val left = if (type == 2 || type == 4) inp.copy(end = Fmt.nc(x2 + up * 12).toDouble()) else inp.copy(end = -12.0)
                for (i in listOf(inp, right, left).flatMap { listOf(it, it.flipped()) }) {
                    val s = TaperCalc.calc(i).solution ?: continue
                    compareNc(s, i.toString(), i.inner)
                    checked++
                }
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            val inp = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
            for (i in listOf(inp, inp.copy(start = 4.0), inp.copy(end = -14.0), inp.copy(start = 4.0, end = -14.0)).flatMap { listOf(it, it.flipped()) }) {
                val s = TaperCalc.calcDouble(i).solution ?: continue
                compareNc(s, i.toString(), i.inner)
                checked++
            }
        }
        assertTrue(checked > 2400, "확인한 사례 $checked")
        println("g41g42ProgramCompensatesToDirectProgram: 사례 $checked, 제어기 모형이 멈춰 끝 점을 못 본 사례 $controllerSkipped")
        assertTrue(controllerSkipped < checked / 20, "제어기 모형이 멈춘 사례 $controllerSkipped")
    }

    private fun onLine(p: Pt, a: Pt, b: Pt) = abs((b - a).unit() cross (p - a)) <= 0.001

    /** NcLines 처럼 90° 넘는 원호를 같은 각으로 나눈다 — 글의 줄과 점을 맞춰 보려고 */
    private fun splitArcs(c: List<Seg>): List<Seg> = c.flatMap { a ->
        if (a !is Arc) return@flatMap listOf(a)
        val k = max(1, ceil(a.sweepDeg / 90 - 1e-3).toInt())
        val a0 = angleOf(a.start - a.center)
        val step = (if (a.cw) -1 else 1) * a.sweepRad() / k
        val pts = listOf(a.start) + (1 until k).map { q -> a.center + Pt(cos(a0 + step * q), sin(a0 + step * q)) * a.radius } + a.end
        pts.zipWithNext { p, q -> Arc(p, q, a.center, a.cw) }
    }

    private fun startTangent(sg: Seg): Pt = when (sg) {
        is Line -> (sg.end - sg.start).unit()
        is Arc -> (sg.start - sg.center).unit().left() * (if (sg.cw) -1.0 else 1.0)
    }

    private fun compareNc(s: TaperSolution, tag: String, inner: Boolean) {
        // 접근 줄(뒤집어 척 쪽에서 시작할 때 진입 G00 앞의 G00)은 빼고 진입 G00 부터 맞춘다
        val g4 = parseMoves(s.ncG41G42).let { m -> m.drop(m.indexOfFirst { it.first != listOf("G00") }) }
        val approach = g4.first()
        assertEquals(listOf(s.side.name, "G01"), approach.first, "$tag 보정 시작 블록")
        val noseR = Regex("""\(R([\d.]+) T(\d)\)""").find(s.ncG41G42[0])!!.groupValues
        val rr = noseR[1].toDouble()
        assertEquals(s.tip, noseR[2].toInt(), tag)
        val app = approach.second
        assertTrue((app.end - app.start).length >= rr - 1e-9, "$tag 진입 이동 < r")
        // 취소: 마지막 줄 G40 G01(직선 — 급송이면 축마다 따로 갈 수 있어서), 또는 이동 없는 G40 한 줄 + 마지막 G01
        val alone = g4[g4.size - 2].first == listOf("G40")
        assertEquals(if (alone) listOf("G01") else listOf("G40", "G01"), g4.last().first, "$tag 보정 취소 블록")
        assertTrue((g4.last().second.end - g4.last().second.start).length >= rr - 1e-9, "$tag 취소 이동 < r")
        val cancelAt = g4.size - (if (alone) 2 else 1)
        assertTrue(g4.subList(1, cancelAt).all { "G01" in it.first || "G02" in it.first || "G03" in it.first }, tag)

        // 글의 형상 줄 = 도면(시작·끝점을 비웠으면 그 요소는 빼고). 띄워 들어가면 앞에 시작 요소 선(볼록 첫 꺾임이면 첫 요소 선)을 따르는 G01 이
        // 한 줄 더 있다. 끝점을 비운 바이트 반대쪽 오목 끝(이동 없는 G40)이면 G40 앞에 끝 요소 선을 따르는 G01 이 한 줄 더 있다
        val exact = s.drawing.segs.drop(if (s.fromStart) 0 else 1).dropLast(if (s.toEnd) 0 else 1)
        val back = s.drawing.segs.last()
        val endTurn = (exact.last().end - exact.last().start) cross (back.end - back.start)
        val convexEnd = !s.toEnd && exact.last() is Line && (endTurn > 0) == (s.side == Side.G42)
        val text0 = g4.subList(1, cancelAt).map { it.second }
        // (모서리 R 이 끝 요소에 접해 끝나면 그 끝 법선이 이미 직접 보정의 마지막 노즈라 그 줄이 없다)
        val skim = alone && !s.toEnd && !convexEnd && text0.last().let { sk ->
            sk is Line && near(sk.start, back.start) && abs((sk.end - sk.start).unit() cross (back.end - back.start).unit()) < 1e-3
        }
        // 막힌 끝(팁 벡터·u < r)이면 취소 앞에 끝 요소에 직각인 G01 한 줄(보정 중 — 노즈가 벽을 따라 올라감)
        val upMove = s.toEnd && !alone && text0.size >= 2 && text0.last().let { up ->
            up is Line && near(up.start, exact.last().end) && abs((up.end - up.start).unit() dot (exact.last().end - exact.last().start).unit()) < 1e-3
        }
        val text = if (skim || upMove) text0.dropLast(1) else text0
        val lead = if (text.size == splitArcs(exact).size + 1) text.first() else null
        val body4 = if (lead == null) text else text.drop(1)
        assertEquals(splitArcs(exact).size, body4.size, "$tag 형상 줄 수 ${s.ncG41G42}")
        for ((a, b) in splitArcs(exact).zip(body4)) assertTrue(near(a.end, b.end) && near(a.start, b.start), "$tag 형상 줄 $b ≠ 도면 $a")
        val line0 = s.drawing.segs.first()
        // 띄운 진입 뒤 선이 놓인 선: 시작 요소(오목 · 단면 진입 · 넣은 시작점) 또는 첫 깎는 요소(볼록 첫 꺾임의 연장선)
        val leadOn = lead?.let { l ->
            listOf(line0, exact.first()).firstOrNull { it is Line && abs((l.end - l.start).unit() cross (it.end - it.start).unit()) < 1e-3 }
        }
        if (lead != null) {
            assertTrue(lead is Line && near(lead.end, exact.first().start), "$tag 띄운 진입 뒤 선 ${s.ncG41G42}")
            assertTrue(leadOn != null, "$tag 띄운 진입 뒤 선이 시작 요소 · 첫 요소와 한 직선이 아니다")
            assertTrue(abs((app.end - app.start).unit() dot (leadOn.end - leadOn.start).unit()) < 1e-3, "$tag 띄운 진입은 그 선에 직각")
        }

        // 보정은 반올림 전 도면으로 한다(글을 다시 읽은 원호는 접선이 0.001 만큼 틀어져 이음이 흔들린다).
        // 시작 이동은 글의 G00 점에서, 선을 따라 들어가는 이동은 그 선에 맞춘다
        val leadX = lead?.let {
            val l0 = leadOn!!
            val uL = (l0.end - l0.start).unit()
            Line(l0.start + uL * ((it.start - l0.start) dot uL), exact.first().start)
        }
        val target = leadX?.start ?: exact.first().start
        val t0 = startTangent(exact.first())
        val appX = if (leadX == null && abs((app.end - app.start).unit() cross t0) < 1e-3) {
            Line(target - t0 * (app.end - app.start).length, target)
        } else {
            Line(app.start, target)
        }
        // 끝 요소를 깎지 않을 때 볼록이 아니면 보정 취소가 끝 요소 방향으로 나간다(바이트 반대쪽이면 그 방향의 G01 한 줄 뒤 이동 없는 G40) —
        // 그 방향의 짧은 줄로 꺾임을 잡는다(그 줄의 끝 점은 뺀다)
        val tail = if (s.toEnd || convexEnd) emptyList() else listOf(Line(back.start, back.start + (back.end - back.start).unit()))
        // 볼록 첫 꺾임의 띄운 진입: 뒤 선이 첫 요소와 한 직선이라 첫 요소를 lead 까지 늘인 하나로 본다(접해 이어지는 점이 둘로 갈리지 않게)
        val slopeLead = leadX != null && leadOn !== line0
        val contour = if (slopeLead) listOf(appX, Line(leadX!!.start, exact.first().end)) + exact.drop(1) + tail else listOf(appX) + listOfNotNull(leadX) + exact + tail
        val comp = NoseComp.tipPath(Contour(contour), rr, s.side, s.tip).path.segs
        val mine = distinct(ends(splitArcs(comp)).drop(1)).dropLast(tail.size)

        // 직접 보정: 진입점 ~ 후퇴 G00 앞
        val g0 = parseMoves(s.ncG40).let { m -> m.drop(m.indexOfFirst { it.first != listOf("G00") }) }
        assertEquals(listOf("G00"), g0.last().first, tag)
        val direct = distinct(ends(g0.dropLast(1).map { it.second }))
        val entry = direct[0]
        val body = direct.drop(1)
        val uF = startTangent(exact.first())
        val last = exact.last()
        val uB = (last.end - last.start).unit()
        val tv = NoseComp.tipVector(s.tip, rr)
        val extra = if (lead != null && !slopeLead) 1 else 0
        val lineStart = exact.first() is Line && abs(((leadX ?: appX).end - (leadX ?: appX).start).unit() cross uF) < 1e-9
        // 처음 점들: 시작 이동(·띄운 진입 뒤 선)이 첫 형상 요소와 한 직선이면 G41/G42 쪽 처음 점들은 직접 보정이 지나는 진입선 위에 있다.
        // 시작점을 넣고 단면에 직각으로 들어가면 (G41/G42) − (직접 보정, 인선이 시작 좌표) = u·(r + 팁 벡터·u).
        // 시작 요소를 따라 들어가 첫 꺾임점에서 바로 형상으로 가면(오목) 띄운 진입의 점만 진입선 위에 더 있다
        val (m, b) = when {
            s.fromStart && lead != null && !slopeLead && abs((app.end - app.start).unit() dot uF) < 1e-3 -> {
                // 너머가 빈 시작에 직각으로 띄워 들어감(lead = 시작점 − u(r + 팁 벡터·u)): 노즈가 직접 보정의 첫 노즈 자리(팁 벡터·u = 0 이면 그 바로 앞
                // LEAD_PAST — 시작 요소 보정선 위)에 선다
                val k0 = mine.indexOfFirst { near(it, body[0]) }
                assertTrue(k0 in 0..1, "$tag 띄운 직각 진입의 처음 점 ${mine.take(2)} ≠ 직접 보정 ${body[0]}")
                for (q in mine.take(k0)) assertTrue(onLine(q, body[0], body[0] + uF) && (q - body[0]).length <= 0.0105, "$tag 처음 점 $q 이 ${body[0]} 바로 앞이 아니다")
                // lead → 시작점 → 다음 꺾임이 한 직선이라 시작점의 보정점(같은 선 위)이 하나 더 있을 수 있다
                mine.drop(k0 + 1).dropWhile { body.size > 1 && onLine(it, body[0], body[1]) && !near(it, body[1]) } to body.drop(1)
            }
            lineStart -> {
                for (q in mine.take(1 + extra)) assertTrue(onLine(q, entry, body[0]), "$tag 처음 점 $q 이 진입선 $entry → ${body[0]} 위에 없다")
                mine.drop(1 + extra) to (if (s.fromStart) body else body.drop(1))
            }
            s.fromStart -> {
                assertTrue(near(mine[0], body[0] + uF * (rr + (tv dot uF))), "$tag 처음 점 ${mine[0]} ≠ ${body[0]}")
                mine.drop(1) to body.drop(1)
            }
            else -> {
                // 단면 선의 r 앞에 직각으로 들어가면 노즈가 바로 첫 보정점에 선다(반올림한 글로 다시 풀면 그 점이 둘로 갈릴 수 있다)
                // 띄운 단면 진입은 노즈가 첫 보정점 자리 바로 앞(LEAD_PAST — 깎지 않는 단면 보정선 위)에 선다
                val u00 = (line0.end - line0.start).unit()
                val k0 = mine.indexOfLast { near(it, body[0]) }.takeIf { it in 0..extra } ?: extra
                for (q in mine.take(k0)) {
                    val justBefore = abs(u00 cross (q - body[0])) <= 0.001 && (q - body[0]).length <= 0.0105
                    assertTrue(onLine(q, entry, body[0]) || near(q, body[0]) || justBefore, "$tag 처음 점 $q 이 진입선 $entry → ${body[0]} 위에 없다")
                }
                mine.drop(k0) to body
            }
        }
        assertEquals(m.size, b.size, "$tag 점 개수 G41/G42=$m G40=$b\n${s.ncG41G42}\n${s.ncG40}")
        for (k in m.indices) {
            // 끝점을 비우고 볼록이면 G41/G42 쪽 노즈는 경사 끝에 직각 — 직접 보정의 마지막 경사 보정선 위, 그 끝보다 앞이다
            if (k == m.lastIndex && convexEnd) {
                val p0 = direct[direct.size - 2]
                assertTrue(onLine(m[k], p0, b[k]) && ((b[k] - m[k]) dot (b[k] - p0)) >= -1e-3, "$tag 끝 점 ${m[k]} ≠ 경사 보정선 $p0 → ${b[k]}")
                continue
            }
            // 끝 점(끝점을 넣었을 때): 끝 블록 끝은 취소 블록과의 관계로 정해져 NoseComp 의 열린 끝(끝 법선)으로는 못 본다 — 제어기 모형(시뮬레이터 GSim,
            // FANUC 형식 A — 설계 3절 · FF 5.4.4)으로. 직접 보정은 인선을 끝좌표에 둔다. G41/G42 는 공구 쪽 직각 취소(안쪽 모서리 교점)면 직접 보정과
            // 같은 자리(팁 벡터·u = r), 이동 없는 G40 · 바깥으로 이어 빼기면 끝 법선(노즈 접점 = 끝좌표). 너머가 막힌 끝(척 쪽 수평 · 재료 쪽 단면,
            // 외경 X0 빼고)은 늘 교점(노즈 끝이 끝좌표에서 멈춤)
            // → (G41/G42) − (직접 보정) = 막힌 끝 u·(팁 벡터·u − r), 바깥으로 이어 빼는 단면 u·(팁 벡터·u), 그 밖 u·min(0, 팁 벡터·u)
            if (k == m.lastIndex && s.toEnd) {
                val ctl = controllerEndTip(s, rr, upMove)
                if (ctl == null) {
                    controllerSkipped++
                    continue
                }
                val face = abs(uB.z) <= abs(uB.x)
                val inwardB = face && (uB.x < 0) != inner
                val wallB = if (!face) uB.z < 0 else inwardB && !(!inner && last.end.x <= 1e-9)
                val tu = tv dot uB
                val want = b[k] + uB * (if (wallB) tu - rr else if (face && !inwardB) tu else min(0.0, tu))
                assertTrue(near(ctl, want), "$tag 끝 점: 제어기 모형의 G41/G42 인선 $ctl ≠ $want(직접 보정 ${b[k]})\n${s.ncG41G42}\n${s.ncG40}")
                continue
            }
            assertTrue(near(m[k], b[k]), "$tag ${k}번 점: G41/G42 보정 ${m[k]} ≠ 직접 보정 ${b[k]}\n${s.ncG41G42}\n${s.ncG40}")
        }
    }

    /** 제어기 모형이 멈춘 사례(오목 R < r 간섭 알람 · 바깥 예각 등 — SimVerifyTest 가 따로 본다) */
    private var controllerSkipped = 0

    /**
     * 제어기 모형(시뮬레이터 GSim — FANUC 형식 A 시작 · 취소, 설계 simulator-design.md 3절)으로 G41/G42 글을 돌려 마지막 형상 블록(G40 앞)
     * 끝의 인선(노즈 + 팁 벡터). 멈추면 null
     */
    private fun controllerEndTip(s: TaperSolution, r: Double, upMove: Boolean = false): Pt? {
        val pre = listOf("T0101", "G97 S1000 M03", "G99 F0.2")
        val run = GSim.run(SimInput((pre + s.ncG41G42).joinToString("\n"), MachineSetup(), mapOf(1 to ToolOffset(r, s.tip))))
        if (run.stop != null) return null
        // 막힌 끝의 직각 한 줄(보정 중 후퇴)이 있으면 그 앞 줄이 마지막 형상 블록
        val iCancel = pre.size + s.ncG41G42.indexOfFirst { "G40" in it } + 1 - (if (upMove) 1 else 0)
        val last = run.steps.filterIsInstance<MoveStep>().last { it.line < iCancel }
        return last.nose.end + NoseComp.tipVector(s.tip, r)
    }
}
