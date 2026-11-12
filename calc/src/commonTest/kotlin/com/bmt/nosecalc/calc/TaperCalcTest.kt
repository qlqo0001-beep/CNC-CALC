package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
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

    // ---- 회사 꼴 G41/G42 글(계획 단계 4, 사용자 결정 2026-10-29) 읽기 · 따로 짠 노즈 모형 ----

    /** 회사 꼴 글 한 줄(낱말 붙여 씀): 이동 G(모달), 그 줄 뒤 프로그램 점(도면 좌표 · 모달 — 처음 보정 혼자 줄은 아직 없음), 그 줄의 F · R · 보정 G */
    private class FLine(val text: String, val g: Int, val p: Pt?, val f: Double?, val rr: Double?, val comp: Int?)

    private fun frameLines(nc: List<String>): List<FLine> {
        var g = -1
        var x: Double? = null
        var z: Double? = null
        return nc.map { line ->
            var comp: Int? = null
            var f: Double? = null
            var rr: Double? = null
            for (w in ncWords(line)) {
                val v = w.substring(1)
                when (w[0]) {
                    'G' -> when (val n = v.toInt()) {
                        0, 1, 2, 3 -> g = n
                        40, 41, 42 -> comp = n
                        else -> error("모르는 G$n: $line")
                    }
                    'X' -> x = v.toDouble() / 2
                    'Z' -> z = v.toDouble()
                    'F' -> f = v.toDouble()
                    'R' -> rr = v.toDouble()
                    else -> error("모르는 낱말 $w: $line")
                }
            }
            FLine(line, g, if (x != null && z != null) Pt(z!!, x!!) else null, f, rr, comp)
        }
    }

    /**
     * 회사 꼴 틀로 나눈 G41/G42 글: [approach] = 보정 혼자 줄 뒤 G0 점들(첫 점이 X…Z20.), [first] = 첫 깎는 줄(G1 … F.1) 끝 = 첫 깎는 점,
     * [shape] = 형상 줄(G1 · G2 · G3), [exits] = 빼는 줄 끝점들(첫 줄에 F.3), 그 뒤 `G40` 혼자 · `G0Z…`(X 그대로) — 틀이 아니면 실패.
     * [lead] = 뒤집은 진입(2026-11-12 통합 고침)의 이송 점: 급송 끝 → `G1 P F.1` → (첫 깎는 점) — [firstCut] 을 주고 첫 깎는 줄 끝이 그 점이 아니면
     * 그 점에 닿는 줄까지를 진입 이송으로 떼어 낸다([first] = 첫 깎는 점)
     */
    private class G4Frame(
        val side: String, val approach: List<Pt>, val first: Pt, val shape: List<FLine>, val exits: List<Pt>, val retractZ: Double, val lead: List<Pt> = emptyList(),
    )

    private fun frameOf(nc: List<String>, tag: String, firstCut: Pt? = null): G4Frame {
        val ls = frameLines(nc)
        val iCut = ls.indexOfFirst { it.f == 0.1 }
        val iExit = ls.indexOfFirst { it.f == 0.3 }
        val iG40 = ls.indexOfFirst { it.comp == 40 }
        assertTrue(ls[0].text == "G41" || ls[0].text == "G42", "$tag 첫 줄이 보정 혼자 줄이 아니다 $nc")
        assertTrue(iCut >= 3 && iExit > iCut && iG40 == ls.size - 2, "$tag 틀(F.1 · F.3 · G40)이 아니다 $nc")
        assertTrue(ls.subList(1, iCut).all { it.g == 0 && it.f == null && it.comp == null && it.p != null }, "$tag 진입은 G0 만 $nc")
        assertTrue(Regex("""G0X-?[0-9.]+Z-?[0-9.]+""").matches(ls[1].text), "$tag 진입 첫 줄은 G0X…Z… $nc")
        assertEquals(1, ls[iCut].g, "$tag 첫 깎는 줄은 G1 $nc")
        assertTrue(ls.subList(iCut + 1, iExit).all { it.g in 1..3 && it.f == null && it.comp == null }, "$tag 형상 줄 $nc")
        assertTrue(ls.subList(iExit, iG40).all { it.g == 1 && it.comp == null } && ls.subList(iExit + 1, iG40).all { it.f == null }, "$tag 빼는 줄은 G1 $nc")
        assertEquals("G40", ls[iG40].text, "$tag G40 은 혼자 줄 $nc")
        val last = ls.last()
        assertTrue(last.g == 0 && Regex("""G0Z-?[0-9.]+""").matches(last.text), "$tag 물러남은 G0Z… 한 줄 $nc")
        val k = if (firstCut == null || near(ls[iCut].p!!, firstCut)) iCut else (iCut + 1 until iExit).firstOrNull { ls[it].g == 1 && near(ls[it].p!!, firstCut) }
        assertTrue(k != null, "$tag 첫 깎는 점 $firstCut 에 닿는 이송 줄이 없다 $nc")
        return G4Frame(
            ls[0].text, ls.subList(1, iCut).map { it.p!! }, ls[k!!].p!!, ls.subList(k + 1, iExit), ls.subList(iExit, iG40).map { it.p!! }, last.p!!.z,
            ls.subList(iCut, k).map { it.p!! },
        )
    }

    /** 진행 방향 u 의 공구 쪽 단위 법선 — G41 왼쪽, G42 오른쪽(계산기 코드와 따로) */
    private fun sideN(u: Pt, side: String) = if (side == "G41") Pt(-u.x, u.z) else Pt(u.x, -u.z)

    /**
     * 보정 중 직선 uA → 직선 uB 꺾임 c 의 노즈 중심 — FANUC 보정 중 규칙(FF 표 5.4.1(a)): 두 보정선(공구 쪽으로 r)의 교점, 한 직선이면 법선.
     * 회사 꼴 틀의 꺾임(진입 · 첫 깎는 줄 · 빼는 줄)은 모두 안쪽이거나 한 직선이다
     */
    private fun noseAt(c: Pt, uA: Pt, uB: Pt, side: String, r: Double): Pt {
        val a = sideN(uA, side)
        val b = sideN(uB, side)
        val den = uA cross uB
        if (abs(den) < 1e-9) return c + b * r
        return c + a * r + uA * ((((b - a) * r) cross uB) / den)
    }

    /** 점 p 에서 꺾은선 pts 까지 거리 */
    private fun polyDist(p: Pt, pts: List<Pt>) = pts.zipWithNext().minOf { (a, b) -> segDist(p, a, b) }

    /** 선분 a–b 에서 꺾은선까지 거리(끝점 · 꺾은선 점 · 교차로 — 직선끼리라 정확) */
    private fun polySegDist(a: Pt, b: Pt, pts: List<Pt>): Double = pts.zipWithNext().minOf { (p, q) ->
        val d1 = b - a
        val d2 = q - p
        val den = d1 cross d2
        if (abs(den) > 1e-15) {
            val t = ((p - a) cross d2) / den
            val u = ((p - a) cross d1) / den
            if (t in 0.0..1.0 && u in 0.0..1.0) return@minOf 0.0
        }
        minOf(segDist(a, p, q), segDist(b, p, q), segDist(p, a, b), segDist(q, a, b))
    }

    /** G40 자리 규칙 반지름(계획 단계 4 · calc-design '진입점과 NC'): 팁 1~9 의 G40 옮김 √2·r + 보정 법선 r + 여유 0.02 */
    private fun g40Room(r: Double) = (1 + sqrt(2.0)) * r + 0.02

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
     * 두 NC 를 가공 순서의 점(도면 [order] 의 시작점 → T 점 → 끝점)으로 맞춰 본다. 진입·후퇴 거리 c = max(Z 여유 1, r).
     * - 직접 보정(G40): 진입(시작 요소가 수평이면 그 연장선, 단면이면 공구 쪽에서 Z) → (단면이면 시작점′) → 보정점 → 끝점′ → 후퇴.
     *   시작·끝점′ 은 인선이 그 좌표에 오는 점 [legFoot]. 끝점을 비우면 끝점′ 없이 마지막 보정점에서 끝 요소의 공구 쪽으로 c 후퇴.
     * - G41/G42(회사 꼴 — [checkFrame]): 보정 혼자 줄 → G0 X…Z20. → (X 맞춤) → 진입점 → G1 첫 깎는 점 F.1 → 도면 점 → 빼는 줄 F.3 → G40 → G0Z200.
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
        // 끝: 끝점을 넣었으면 인선이 끝점에 오고(끝점′) 수평이면 공구 쪽으로, 단면이면 이어서 c — 단면을 재료 쪽(외경은 중심 쪽 X−,
        // 내경은 바깥 X+)으로 깎아 끝나면(그림의 앞 단면을 거꾸로) 너머도 같은 단면이라 공구 쪽으로(이 격자의 끝 X 는 중심이 아니다).
        // 비웠으면 마지막 보정점에서 공구 쪽으로 c.
        val g40End: List<Pt>
        if (s.toEnd) {
            val inward = !lastH && (en.x < tl.x) != inner
            val leave = if (lastH || inward) nB else (en - tl).unit()
            val e0 = legFoot(en, tl, inner, tip, r)
            assertPt(e0 + leave * c, s.exit, msg = "$tag 후퇴점")
            g40End = listOf(e0, s.exit)
        } else {
            assertPt(corners.last() + nB * c, s.exit, msg = "$tag 후퇴점이 마지막 보정점에서 끝 요소의 공구 쪽으로 c")
            g40End = listOf(s.exit)
        }
        val u0 = (order[1] - st).unit()
        val tv0 = NoseComp.tipVector(tip, r)
        // 진입: 시작점을 넣었으면 그 요소의 연장선(수평)·공구 쪽(단면)에서 시작점으로. 비웠으면 시작 요소는 깎지 않고
        // 첫 꺾임이 볼록이면 경사선을(손으로 정한 표 slopeEntry), 아니면 수평인 시작 요소는 그 보정선을 늘인 곳에서,
        // 단면은 공구 쪽(Z)에서 첫 보정점으로
        val nF = toolNormal(st, order[1], inner)
        val g40: List<Pt>
        if (s.fromStart) {
            // 뒤집어 수평 시작 요소를 팁의 Z 와 반대로 깎으면 시작 좌표 너머(척·구멍 바닥)로 넘지 않고 공구 쪽에서 직각으로 — 단면 시작과 같은 꼴
            val along = firstH && !(flip && u0.z * tv0.z <= 0)
            val approach = if (along) (st - order[1]).unit() else nF
            val s0 = legFoot(st, order[1], inner, tip, r)
            assertPt(s0 + approach * c, s.entry, msg = "$tag 진입점")
            g40 = listOf(s.entry) + (if (along) emptyList() else listOf(s0)) + corners
        } else {
            val back = order[1] - order[2]
            val lead = (st - order[1]).unit()
            if (slopeEntry) {
                assertPt(corners[0] + back * (c / abs(back.z)), s.entry, msg = "$tag 진입점이 보정 경사선 위 Z 로 c 바깥")
            } else if (firstH) {
                assertPt(corners[0] + lead * c, s.entry, msg = "$tag 진입점이 시작 요소 보정선 위 c 바깥")
            } else {
                assertPt(corners[0] + nF * c, s.entry, msg = "$tag 진입점이 첫 보정점에서 공구 쪽(Z)으로 c")
            }
            g40 = listOf(s.entry) + corners
        }
        // 접근 줄: 뒤집어 척 쪽(그림 왼쪽)에서 시작하면 진입 G00 이 앞 끝면(T 점 · 넣은 앞 요소 끝 가운데 가장 오른쪽)보다 왼쪽이라
        // 앞 끝면 + c 에서 X 를 맞추고 Z 로 들어간다. 왼쪽부터 깎을 때 앞 요소는 끝 요소(order 의 마지막)다
        val zFront = (order.subList(1, order.size - 1) + listOfNotNull(order.last().takeIf { s.toEnd })).maxOf { it.z }
        fun app(e: Pt) = if (flip && leftFirst && e.z < zFront - 1e-9) approachOf(e, order, zFront, inner, tip, r, c) else emptyList()
        val app40 = app(s.entry)
        assertEquals(app40.size, s.approach.size, "$tag 접근 줄 ${s.ncG40.legacy}")
        assertEquals((app40 + g40 + g40End).map(::printed), ncTrace(s.ncG40.legacy), "$tag G40 ${s.ncG40.legacy}")
        assertEquals(
            app40.map { "G00" } + listOf("G00") + g40.drop(1).map { "G01" } + (if (s.toEnd) listOf("G01", "G00") else listOf("G00")),
            s.ncG40.legacy.map { it.take(3) }, tag,
        )
        checkFrame(s, order, inner, leftFirst, flip, r, c, tag, slopeEntry, slopeExit)
    }

    /**
     * 회사 꼴 G41/G42 글(계획 단계 4 · calc-design '진입점과 NC')을 손으로 정한 규칙과 따로 짠 노즈 모형([noseAt])으로 본다. order = 가공 순서 도면 점,
     * 모서리 R 없음, Z 여유 1(c = max(1, r)), 부품 앞이 +Z(화면 역방향 아님).
     * - 틀: 보정 혼자 줄(표대로 G41/G42) → G0X…Z20. → G0 Z 만 → (G0 X 만 · 공구 쪽 직각) → 진입점 E → G1 첫 깎는 점 F.1 → 도면 점 → 빼는 줄 → G40 → G0Z200.
     * - 진입점(첫 깎는 점에서 c): 넣은 시작 좌표 너머가 막혔으면(척 · 구멍 바닥 쪽에서 앞으로 깎기 시작하는 수평, 바깥으로 깎기 시작하는 단면 — 외경 X0 빼고)
     *   공구 쪽 직각, 아니면 첫 깎는 요소와 한 직선으로 너머에서. 비운 시작은 첫 꺾임이 볼록이면 경사 연장선(큰 축으로 c), 아니면 시작 요소 선 위로 c.
     *   첫 깎는 줄의 노즈 이동이 0.05 보다 짧으면(안쪽 꺾임 둘 사이) 그 줄을 따라 그만큼 더 물린다.
     * - 첫 빼는 줄: 막힌 끝(척 · 구멍 바닥 쪽으로 끝난 수평, 재료 쪽으로 끝난 단면)은 공구 쪽 직각으로 c, 빈 끝은 끝 좌표 너머로 곧게 c(사용자 결정 ③),
     *   비운 끝은 볼록이면 경사를 큰 축으로 c, 아니면 끝 요소를 따라 c — 노즈 이동이 0.05 보다 짧으면 그만큼 더.
     * - G40 자리 규칙: 마지막 빼는 줄 끝에서 (1 + √2)·r + 0.02 안에 도면이 없고, 거기서 +Z 로 물러나는 띠에도 없다.
     * - 진입 · 빼는 줄의 노즈(보정 중, 따로 짠 모형)가 도면에 r 보다 가까이 가지 않고, 빼는 줄마다 노즈가 0.05 이상 앞으로 간다(거꾸로 가면 간섭 알람)
     * - 뒤집기(2026-11-12 통합 고침 — 척 쪽 · 구멍 안에서 시작): 위 진입점의 급송이 c 를 못 지키면 급송은 그 앞 점 P 에서 공구 쪽으로 떨어진 A 에서 멈추고
     *   `G1 P F.1` → 첫 깎는 점(두 이송 줄 — A → P 와 P → 첫 깎는 점은 직각). 막힌 시작은 늘 그 꼴(P = 첫 깎는 점 + 공구 쪽 2r + g 이상, A = P + 앞으로 r + g).
     *   어느 쪽이든 급송 노즈가 도면에서 c/4 이상(시뮬레이터로 잰 격자는 `TaperFlipEntryTest`)
     */
    private fun checkFrame(
        s: TaperSolution, order: List<Pt>, inner: Boolean, leftFirst: Boolean, flip: Boolean, r: Double, c: Double, tag: String, slopeEntry: Boolean, slopeExit: Boolean,
    ) {
        val nc = s.ncG41G42
        val f = frameOf(nc, tag, if (s.fromStart) order.first() else order[1])
        val side = expectedSide(inner, leftFirst)
        assertEquals(side, f.side, "$tag 보정 쪽 $nc")
        val st = order.first()
        val en = order.last()
        val tl = order[order.size - 2]
        val u0 = (order[1] - st).unit()
        val uB = (en - tl).unit()
        fun horiz(u: Pt) = abs(u.z) > abs(u.x)
        val firstCut = if (s.fromStart) st else order[1]
        val lastCut = if (s.toEnd) en else tl
        fun near(a: Pt, b: Pt, t: Double = 0.0011) = abs(a.xDia - b.xDia) <= 2 * t && abs(a.z - b.z) <= t
        // 형상: 첫 깎는 점 → 도면 점(손으로 만든 order) → 마지막 깎는 점
        assertTrue(near(f.first, firstCut), "$tag 첫 깎는 점 ${f.first} ≠ $firstCut $nc")
        val want = order.subList(order.indexOf(firstCut) + 1, order.indexOf(lastCut) + 1)
        assertEquals(want.size, f.shape.size, "$tag 형상 줄 수 $nc")
        for ((a, b) in want.zip(f.shape)) assertTrue(near(a, b.p!!), "$tag 형상 점 ${b.text} ≠ $a $nc")
        // 진입점
        val app = f.approach
        assertEquals(20.0, app[0].z, 1e-12, "$tag 진입 Z20. $nc")
        assertEquals(app[0].x, app[1].x, 1e-12, "$tag 둘째 G0 은 Z 만 $nc")
        for (k in 2 until app.size - 1) assertTrue(abs(app[k].x - app[k - 1].x) < 1e-12 || abs(app[k].z - app[k - 1].z) < 1e-12, "$tag G0 은 한 축씩 $nc")
        val nF = sideN(u0, side)
        val e0 = if (s.fromStart) {
            val blocked = if (horiz(u0)) u0.z > 0 else (u0.x > 0) != inner && !(!inner && st.x <= 1e-9)
            if (blocked) st + nF * c else st - u0 * c
        } else if (slopeEntry) {
            // 볼록 첫 꺾임: 경사를 큰 축으로 c(끝 쪽과 같다 — 2026-11-04 고침 전에는 Z 로 c 라 가파른 경사에서 X 가 축 너머까지 갔다)
            val back = order[1] - order[2]
            order[1] + back * (c / max(abs(back.z), abs(back.x)))
        } else {
            order[1] - u0 * c
        }
        val e = app.last()
        val prev = (e - app[app.size - 2]).unit()
        val uG = (firstCut - e0).unit()
        if (f.lead.isEmpty()) {
            // 첫 깎는 줄의 노즈 이동 = 첫 깎는 점의 꺾임 노즈 − 진입점의 꺾임 노즈(진입 G0 → 첫 깎는 줄)
            val moved = (noseAt(firstCut, uG, u0.takeIf { s.fromStart } ?: (order[2] - order[1]).unit(), side, r) - noseAt(e0, prev, uG, side, r)) dot uG
            val eWant = if (moved >= 0.05) e0 else e0 - uG * (0.05 - moved)
            assertTrue(near(e, eWant), "$tag 진입점 $e ≠ 손 $eWant(첫 깎는 점에서 c${if (moved < 0.05) " + 노즈 이동 0.05 까지" else ""}) $nc")
        } else {
            // 뒤집은 진입의 두 이송 줄: P 는 전 진입점의 줄(첫 깎는 점으로 들어오는 방향 uG) 위 그보다 멀리, A → P 는 그 줄에 직각으로 공구 쪽에서 c/4 이상
            assertTrue(flip && f.lead.size == 1, "$tag 진입 이송 줄은 뒤집기에서 하나 $nc")
            val p = f.lead.single()
            val back = firstCut - p
            val blocked = s.fromStart && (if (horiz(u0)) u0.z > 0 else (u0.x > 0) != inner && !(!inner && st.x <= 1e-9))
            assertTrue(
                abs(back.unit() cross uG) < 2e-3 && (back dot uG) >= (if (blocked) 2 * r + c / 4 else (firstCut - e0).length) - 2e-3,
                "$tag P $p 가 전 진입점 $e0 의 줄 밖 $nc",
            )
            val up = e - p
            assertTrue(abs(up.unit() dot uG) < 2e-3 && (up dot sideN(uG, side)) >= c / 4 - 2e-3, "$tag 급송 끝 $e 가 P 의 공구 쪽 직각이 아니다 $nc")
        }
        // 첫 빼는 줄
        val nB = sideN(uB, side)
        val uLast = (lastCut - order[order.indexOf(lastCut) - 1]).unit()
        val (dir, len) = if (s.toEnd) {
            val blocked = if (horiz(uB)) uB.z < 0 else (uB.x < 0) != inner && !(!inner && en.x <= 1e-9)
            if (blocked) nB to c else uB to c
        } else if (slopeExit) {
            val ahead = tl - order[order.size - 3]
            ahead.unit() to ahead.length * (c / max(abs(ahead.z), abs(ahead.x)))
        } else {
            uB to c
        }
        val x1 = f.exits[0]
        val uNext = if (f.exits.size > 1) (f.exits[1] - x1).unit() else dir
        // 길이 l 의 첫 빼는 줄 끝 — 노즈 이동이 0.05 보다 짧으면 그만큼 더
        fun x1Of(l: Double): Pt {
            val b = lastCut + dir * l
            val m = (noseAt(b, dir, uNext, side, r) - noseAt(lastCut, uLast, dir, side, r)) dot dir
            return b + dir * max(0.0, 0.05 - m)
        }
        // 그 끝에서 G40 자리 규칙 · 물러남 띠를 못 맞추면(막힌 끝 위의 역테이퍼 등) 첫 빼는 줄을 같은 방향으로 max(r, 0.5) 씩 더 늘여 다시 짠다(문서의 규칙)
        val k = (0..400).firstOrNull { near(x1, x1Of(len + it * max(r, 0.5))) }
        assertTrue(k != null, "$tag 첫 빼는 줄 끝 $x1 ≠ 손 ${x1Of(len)}(+ max(r, 0.5)·k) $nc")
        // G40 자리 규칙 · 물러남 띠(도면 꺾은선 — 0.001 반올림까지), 그 자리는 끝에서 멀지 않다(이 격자는 다리 5 mm)
        val q = f.exits.last()
        assertTrue((q - lastCut).length < 30.0, "$tag G40 자리 $q 가 마지막 깎는 점에서 ${(q - lastCut).length} $nc")
        assertTrue(polyDist(q, order) >= g40Room(r) - 0.0015, "$tag G40 자리 ${q} 에서 도면까지 ${polyDist(q, order)} < ${g40Room(r)} $nc")
        assertTrue(polySegDist(q, Pt(200.0, q.x), order) >= g40Room(r) - 0.0015, "$tag 물러남 띠 $nc")
        assertEquals(200.0, f.retractZ, 1e-12, "$tag G0Z200. $nc")
        val (inNoses, outNoses) = frameNoses(f, (want.first() - firstCut).unit(), uLast, side, r)
        // 뒤집으면(계산기 역방향 + 뒤집기는 정방향과 같은 쪽이라 빼고) 급송 노즈(Z20. 부터 급송 끝까지)가 도면에서 c/4 이상
        if (flip && leftFirst) for (k in 0 until app.size - 1) assertTrue(polySegDist(inNoses[k], inNoses[k + 1], order) >= r + c / 4 - 1e-3, "$tag 급송 노즈가 도면에 c/4 안 $nc")
        for ((a, b) in inNoses.drop(1).zipWithNext()) assertTrue(polySegDist(a, b, order) >= r - 1e-3, "$tag 진입 노즈가 도면에 닿는다 $a → $b $nc")
        val outDirs = (listOf(lastCut) + f.exits).zipWithNext { a, b -> (b - a).unit() }
        for ((k, ab) in outNoses.zipWithNext().withIndex()) {
            val (a, b) = ab
            if (k > 0) assertTrue(polySegDist(a, b, order) >= r - 1e-3, "$tag 빼는 노즈가 도면에 닿는다 $a → $b $nc")
            assertTrue(((b - a) dot outDirs[k]) >= 0.05 - 1e-3, "$tag 빼는 줄 ${k + 1} 의 노즈 이동 ${(b - a) dot outDirs[k]} < 0.05 $nc")
        }
    }

    /**
     * 틀의 보정 중 노즈 자리(따로 짠 모형 [noseAt]): 진입 = G0 점들(첫 Z20. 점은 다음 줄의 법선 — 그 앞 이동은 공구 교환 자리라 모름) + 첫 깎는 점(첫 형상 요소 처음 접선 [uFirst]),
     * 빼기 = 마지막 깎는 점(마지막 형상 요소 끝 접선 [uLast]) + 빼는 점들(마지막은 법선)
     */
    private fun frameNoses(f: G4Frame, uFirst: Pt, uLast: Pt, side: String, r: Double): Pair<List<Pt>, List<Pt>> {
        val inPts = f.approach + f.lead + f.first
        val inDirs = inPts.zipWithNext { a, b -> (b - a).unit() }
        val inNoses = inPts.indices.map { k ->
            when (k) {
                0 -> inPts[0] + sideN(inDirs[0], side) * r
                inPts.lastIndex -> noseAt(inPts[k], inDirs[k - 1], uFirst, side, r)
                else -> noseAt(inPts[k], inDirs[k - 1], inDirs[k], side, r)
            }
        }
        val outPts = listOf(f.shape.last().p!!) + f.exits
        val outDirs = outPts.zipWithNext { a, b -> (b - a).unit() }
        val outNoses = outPts.indices.map { k ->
            when (k) {
                0 -> noseAt(outPts[0], uLast, outDirs[0], side, r)
                outPts.lastIndex -> outPts[k] + sideN(outDirs[k - 1], side) * r
                else -> noseAt(outPts[k], outDirs[k - 1], outDirs[k], side, r)
            }
        }
        return inNoses to outNoses
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
        assertEquals(listOf("G00 X37.531 Z1.", "G01 X39.531 Z0.", "G01 X50. Z-5.234", "G00 X52."), s.ncG40.legacy)
        // G41/G42(회사 꼴 — 계획 단계 4): G42 혼자 → Z20. 에서 X38 → 면취 연장선 Z1(첫 깎는 점에서 Z 로 1) → G1 T1 F.1 → 면취 →
        // 볼록 끝이라 면취를 큰 축으로 1 더 늘인 X52 Z-6 으로 빼는 줄 F.3(둘레 1.0 안에 외경 X50 — G40 자리 규칙 (1 + √2)·0.4 + 0.02 = 0.986 이라 한 줄) → G40 → G0Z200.
        assertEquals(listOf("G42", "G0X38.Z20.", "Z1.", "G1X40.Z0.F.1", "X50.Z-5.", "X52.Z-6.F.3", "G40", "G0Z200."), s.ncG41G42)
        // 끝점 Z-10(막힌 끝): 벽을 따라 +X 로 빼고(노즈가 교점 X50.8 Z-9.6 — Z-10 의 법선에 닿음) 벽 너머에서 +Z 로 0.986 멀어진 뒤 G40
        assertEquals(
            listOf("G42", "G0X38.Z20.", "Z1.", "G1X40.Z0.F.1", "X50.Z-5.", "Z-10.", "X52.F.3", "Z-9.014", "G40", "G0Z200."),
            ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = -10.0))).ncG41G42,
        )
        // 시작점 = 단면 X30: 공구 쪽(Z+1)에서 들어가 인선을 X30 Z0 에 맞추고 단면부터 깎는다
        val f = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 30.0)))
        assertEquals(listOf("G00 X30. Z1.", "G01 Z0.", "G01 X39.531", "G01 X50. Z-5.234", "G00 X52."), f.ncG40.legacy)
        assertEquals(
            // 바깥으로 깎기 시작하는 단면(너머 보스 — 막힌 시작): 공구 쪽(Z+1)에서 직각으로 X30 Z0 에
            listOf("G42", "G0X30.Z20.", "Z1.", "G1Z0.F.1", "X40.", "X50.Z-5.", "X52.Z-6.F.3", "G40", "G0Z200."),
            f.ncG41G42,
        )
        // 끝점 = Z-10: 인선이 Z-10 에 멈춘 뒤 X 로 1 빠진다
        val e = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = -10.0)))
        assertEquals(listOf("G00 X37.531 Z1.", "G01 X39.531 Z0.", "G01 X50. Z-5.234", "G01 Z-10.", "G00 X52."), e.ncG40.legacy)
        assertEquals(
            listOf("G42", "G0X38.Z20.", "Z1.", "G1X40.Z0.F.1", "X50.Z-5.", "Z-10.", "X52.F.3", "Z-9.014", "G40", "G0Z200."),
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
     *   더 늘인 점 — 면취 45° 라 X 는 직경으로 2 커진다. 직접 보정 X52 Z-6.234.
     *   그 G00 점이 척 쪽(앞 끝면 Z0 보다 왼쪽)이라 앞에 접근 줄 `G00 X52. Z1.`(앞 끝면 + c 에서 X 를 맞춤) → `G00 Z-6.`(Z 로만) —
     *   X52 는 오른쪽 도면(면취 · 단면, 가장 높은 X50)보다 위라 그대로 Z 로 온다.
     * - 후퇴(끝점 = 단면 X 를 비움 → 단면은 깎지 않음): 직접 보정은 T2′ 에서 끝 요소(단면)의 공구 쪽(+Z)으로 1 → Z1.
     * - G41/G42(회사 꼴 — 계획 단계 4): 진입점 = 도면 면취선 연장 X52 Z-6(첫 깎는 점 T1 에서 Z 로 c). 첫 깎는 줄(X52 Z-6 → X50 Z-5)이 +Z 로 가서
     *   Z20. 에서 Z 줄로 곧장 오면 Z 줄과 거꾸로 — 다른 X 에서 Z 를 맞추고 X 로 들어간다. 바깥(+X)으로 반경 max(r, 0.25) = 0.4 씩:
     *   반경 26.4(X52.8)는 X 줄이 r 이라 두 안쪽 꺾임의 노즈(Z 줄 → X 줄: Z-5.6 · 반경 26.0, X 줄 → 첫 깎는 줄: 면취 보정선 위 Z-5.6 · 반경 26.166)가
     *   거꾸로 0.166 → 안 됨, 반경 26.8(X53.6)은 노즈가 0.234 앞으로 가고(≥ 0.05), Z 줄 노즈(반경 26.4, 아래 끝 26.0)는 외경 X50(25) 위,
     *   X 줄 노즈는 면취에서 0.4 → `G0X53.6Z20.` / `Z-6.` / `X52.`. 첫 깎는 줄은 면취와 한 직선이라 노즈가 T1 의 면취 법선에 선다.
     *   끝(비움): 마지막 꺾임 T2 가 볼록이라 면취를 큰 축으로 c 더 늘인 X38 Z1 로 뺀다(F.3). G40 자리 규칙 (1 + √2)·0.4 + 0.02 = 0.986:
     *   거기서 단면 Z0 까지 1.0, T2 까지 1.414 — 한 줄로 되고, +Z 물러남 띠도 단면에서 1.0
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
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G00 Z1."), s.ncG40.legacy)
        // G41/G42 진입(2026-11-12 통합 고침 — 뒤집기): 전 진입(X53.6 에서 Z, 진입점 X52 Z-6 으로 X)은 급송 끝 노즈(−5.6, 26.166)가 외경에서 0.766(c = 1 미만) —
        // 급송은 진입점 P(X52 Z-6)에서 면취 연장선의 공구 쪽 법선 (1, 1)/√2 으로 2r + c = 1.8 인 A(Z−4.727, X54.546)에서 멈추고 `G1 P F.1` → T1(X50 Z-5).
        // A 로는 Z 줄로 곧장(급송 → P 줄이 안쪽 꺾임), 급송 끝 노즈 (−4.5615, 26.8728)는 외경 끝에서 1.923(틈 1.523) — 손 계산은 VerifyFlipTest.handOuterType1Flip
        assertEquals(listOf("G41", "G0X54.546Z20.", "Z-4.727", "G1X52.Z-6.F.1", "X50.Z-5.", "X40.Z0.", "X38.Z1.F.3", "G40", "G0Z200."), s.ncG41G42)
        assertEquals(listOf(Pt.ofDia(1.0, 52.0)), s.approach)
        // 안 뒤집은 것과 같은 실제 점: 직접 보정 X39.531 Z0 → X50 Z-5.234 가 X50 Z-5.234 → X39.531 Z0 으로 순서만 바뀐다
        val fwd = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        for ((a, b) in fwd.points.reversed().zip(s.points)) {
            assertPt(a.nodes.single().drawn, b.nodes.single().drawn)
            assertPt(a.nodes.single().prog, b.nodes.single().prog)
        }
        assertEquals(Side.G42, fwd.side)

        // 끝점 = 단면 X30(중심 쪽으로 X30 까지 깎음): 인선이 X30 Z0 에 오고, 단면의 공구 쪽(+Z)으로 1 빠진다. X30 아래는 아직 같은 단면이거나
        // 보스라 단면을 따라 이어 가면 G00 이 X28 까지 급송으로 깎는다(검증 2026-10-27). G41/G42 도 막힌 끝이라 첫 빼는 줄이 공구 쪽(+Z) 직각 c(Z1) —
        // 안쪽 꺾임이라 제어기가 교점(노즈 중심 X30.8 Z0.4)을 잡는다: 직접 보정의 노즈 중심(인선 X30 Z0 − 팁 벡터)과 같은 자리.
        // G40 자리 규칙: Z1 X30 은 벽 너머(반경 < 15 — 막힌 쪽 반평면)에 붙어 있어 벽에서 멀어지는 쪽(+X)으로 0.986 → 반경 15.986 = X31.971
        // (거기서 단면 Z0 까지 1.0 — 둘 다 0.986 이상)
        val e = ok(TaperCalc.calc(flip.copy(end = 30.0)))
        assertEquals(emptyList(), TaperCalc.calc(flip.copy(end = 30.0)).warnings)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G01 X30.", "G00 Z1."), e.ncG40.legacy)
        assertEquals(listOf("G41", "G0X54.546Z20.", "Z-4.727", "G1X52.Z-6.F.1", "X50.Z-5.", "X40.Z0.", "X30.", "Z1.F.3", "X31.971", "G40", "G0Z200."), e.ncG41G42)
        // Z 여유 0 이면 c = r = 0.4: 진입점 X50.8 Z-5.4(면취 연장)로 X 줄이 오면 급송 끝 노즈(Z−5, 반경 25.566)가 외경에서 0.166 — c 미만이라(2026-11-12 통합 고침)
        // 급송은 진입점 P(X50.8 Z-5.4)에서 면취 연장선의 공구 쪽 법선 (1, 1)/√2 으로 2r + c = 1.2 인 A(Z−4.551, X52.497)에서 멈추고 `G1 P F.1` → T1.
        // 첫 빼는 줄 Z0.4 는 r 이라 두 안쪽 꺾임 노즈(단면 → 빼는 줄: Z0.4 · 반경 15.4, 빼는 줄 → +X: Z0 · 반경 15.4)가 0.4 거꾸로 — 0.05 앞으로 가게 0.45 더(Z.85).
        // G40 자리: Z0.4 에서 +X 로 단면 위 끝 X40 Z0 · 면취를 0.986 비킬 때까지 — 면취 보정선 (0.4 + x − 20)/√2 = 0.986 → 반경 20.994(X41.988), 그 줄도 같이 Z.85 로
        assertEquals(
            listOf("G41", "G0X52.497Z20.", "Z-4.551", "G1X50.8Z-5.4F.1", "X50.Z-5.", "X40.Z0.", "X30.", "Z.85F.3", "X41.988", "G40", "G0Z200."),
            ok(TaperCalc.calc(flip.copy(end = 30.0, zClear = 0.0))).ncG41G42,
        )
        // 끝점 = 단면 X0(중심까지): 인선(T3, 노즈 중심보다 r 앞)을 X0 에 멈추면 노즈 중심이 반경 0.4 에 서서 중심에 살(법선 0.166)이 남는다 —
        // 직접 보정은 노즈 중심이 중심선에 닿는 X-0.8(−2r) 까지 깎은 뒤 공구 쪽(+Z)으로 빠진다(검증 2026-10-27: 단면을 따라 X-2.8 로 급송하지 않음).
        // G41/G42 도 단면을 X-0.8 까지 쓰고 공구 쪽(+Z)으로 c 빼는 줄(Z1) — 안쪽 꺾임이라 제어기가 단면 보정선(z = r)과 빼는 줄 보정선(X-0.8 에서 +Z,
        // G41 은 왼쪽 = +X 로 r → 반경 −0.4 + 0.4 = 0)의 교점을 잡아 노즈 중심이 Z0.4 · 중심선(직접 보정과 같은 자리). Z1 에서 단면까지 1.0 ≥ 0.986 이라 한 줄.
        // 인선이 중심 너머라 ENTRY_PAST_AXIS(화면 글은 이 경우를 따로 말한다)
        val ax = TaperCalc.calc(flip.copy(end = 0.0))
        assertEquals(listOf(TaperWarning.ENTRY_PAST_AXIS), ax.warnings)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G01 X-0.8", "G00 Z1."), ok(ax).ncG40.legacy)
        assertEquals(listOf("G41", "G0X54.546Z20.", "Z-4.727", "G1X52.Z-6.F.1", "X50.Z-5.", "X40.Z0.", "X-.8", "Z1.F.3", "G40", "G0Z200."), ok(ax).ncG41G42)
        assertPt(Pt(0.4, 0.0), ok(ax).center.segs.last().end, msg = "노즈 중심이 중심선(반경 0)에")
        // 시작점 = 외경 Z-10(외경부터): 외경을 Z+ 로 깎는다 — 바이트(T3, 척 쪽으로 깎는 날)와 반대라 AGAINST_TOOL.
        // Z-10 너머(척 쪽)는 날 앞이라 외경 연장선(Z-11)으로 넘어 들어가지 않고 공구 쪽(외경 위 X52)에서 Z-10 에 직각으로 들어간다.
        // G41/G42 도 막힌 시작(2026-11-12 통합 고침 — 전에는 진입점 X52 Z-10 이라 급송 노즈 밑이 외경 위 0.2, 급송 끝이 Z-10 벽에 틈 0): 급송은 공구 쪽 2r + c = 1.8
        // (X53.6) · 앞으로 r + c = 1.4(Z-8.6)에서 멈추고 `G1Z-10.F.1` → `X50.`. 벽을 따라 내려오는 줄(X53.6 → X50)은 다음 블록(외경 Z+)과 안쪽 꺾임이라
        // 노즈 중심 X50.8 Z-9.6 = 직접 보정의 인선 X50 Z-10 − 팁 벡터
        val st = TaperCalc.calc(flip.copy(start = -10.0))
        val sf = ok(st)
        assertEquals(listOf(TaperWarning.AGAINST_TOOL), st.warnings)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-10.", "G01 X50.", "G01 Z-5.234", "G01 X39.531 Z0.", "G00 Z1."), sf.ncG40.legacy)
        assertEquals(listOf("G41", "G0X53.6Z20.", "Z-8.6", "G1Z-10.F.1", "X50.", "Z-5.", "X40.Z0.", "X38.Z1.F.3", "G40", "G0Z200."), sf.ncG41G42)
        assertPt(Pt.ofDia(-10.0, 52.0), sf.entry)
    }

    /**
     * 뒤집어 넣은 시작점·끝점은 그 좌표를 넘어 척·구멍 바닥·단면 안쪽으로 가지 않는다(검증 2026-10-27, 현장 안전).
     * - 내경 형상 1(06.jpg) 뒤집기, 시작 = 구멍 깊이 Z-20: 막힌 구멍이면 Z-21 은 바닥 속이다. G00 은 Z-20 에서 중심 쪽(X32)에 서고 X34 로 들어간다.
     *   그 앞 접근 줄: 구멍 밖(입구 단면 Z0 + c = Z1)에서 X32 를 맞추고 Z 로만 들어간다(구멍 X34 보다 안쪽이라 벽에 안 닿는다).
     * - 외경 형상 3 뒤집기, 시작 = 큰 외경 Z-20: 척 쪽 Z-21 이 아니라 외경 위 X52 Z-20(앞 끝면은 작은 외경 T 점 Z0 → 접근 X52 Z1).
     * - 내경 형상 1 뒤집기, 끝 = 입구 단면 X50: 단면을 바깥으로 X50 까지 — X52 로 이어 가지 않고 공구 쪽(+Z)으로 빠진다.
     * 안 뒤집은 것(시작이 자유 끝 쪽)은 지금처럼 연장선으로 들어온다(접근 줄 없음).
     * G41/G42(회사 꼴 — 계획 단계 4)도 같은 원칙: 막힌 시작은 공구 쪽에서 직각으로 들어가고(진입점 = 시작 좌표 + 공구 쪽 c), 막힌 끝은 공구 쪽 직각으로
     * 빼며(첫 빼는 줄 = 끝 좌표 + 공구 쪽 c), G40 자리 규칙 (1 + √2)·0.4 + 0.02 = 0.986 이 모자라면 벽에서 멀어지게 한 줄 더 — 노즈가 넣은 좌표 너머로 가지 않는다
     */
    @Test
    fun flippedLegsStayInsideGivenCoordinates() {
        val i1 = TaperInput(inner = true, x1 = 34.0, z1 = -2.0, x2 = 40.0, z2 = 0.0, flip = true)
        val s = ok(TaperCalc.calc(i1.copy(start = -20.0)))
        assertEquals(Side.G42, s.side)
        assertEquals(listOf("G00 X32. Z1.", "G00 Z-20.", "G01 X34.", "G01 Z-2.186"), s.ncG40.legacy.take(4))
        // G41/G42: 막힌 시작(구멍 바닥 쪽 Z-20) — 2026-11-12 통합 고침 전에는 진입점이 공구 쪽(축 쪽) X32 라 Z 줄 노즈(G42 · −Z 진행은 +X 쪽, 반경 16.4)의
        // 위 끝 16.8 이 구멍 벽(17)에서 0.2, 급송 끝이 Z-20 벽에 틈 0. 이제 급송은 축 쪽 2r + c = 1.8(X30.4) · 앞으로 r + c = 1.4(Z-18.6)에서 멈추고
        // `G1Z-20.F.1` → `X34.`(Z 줄 노즈 위 끝 16.0 — 벽에서 c)
        assertEquals(listOf("G42", "G0X30.4Z20.", "Z-18.6", "G1Z-20.F.1", "X34.", "Z-2."), s.ncG41G42.take(6))
        // 시작을 비우면(06.jpg 를 구멍 쪽부터): T1 이 볼록이라 진입점은 면취 연장선을 큰 축으로 c — 경사가 Z 2 · 반경 3 이라 반경으로 1: 반경 17 − 1 = 16(X32),
        // Z −2 − 2/3 = −2.667(2026-11-04 고침 전에는 Z 로 c — X31 Z-3). 첫 깎는 줄이 +Z 라 Z 줄로 곧장 못 오고 축 쪽 X 에서 Z 를 맞춘다 — 걸음 0.4:
        // 반경 15.6 은 X 줄이 r 이라 두 안쪽 꺾임 노즈(Z−2.267 · 반경 16.0 → 면취 보정선 위 반경 15.879 = 진입점 + (0.4, −0.121))가 0.121 거꾸로,
        // 반경 15.2(X30.4)은 0.279 앞으로 — 그 급송 끝 노즈는 구멍(17)에서 c 보다 가까워(2026-11-12 통합 고침) 급송은 진입점 P(X32 Z-2.667)에서 면취 연장선의
        // 공구 쪽(G42 — 진행 오른쪽) 법선 (0.832, −0.555) 으로 2r + c = 1.8 인 Z-1.169 · 반경 15.0015(X30.003)에서 멈추고 `G1 P F.1` → T1
        assertEquals(listOf("G42", "G0X30.003Z20.", "Z-1.169", "G1X32.Z-2.667F.1", "X34.Z-2."), ok(TaperCalc.calc(i1)).ncG41G42.take(5))
        val o3 = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, start = -20.0, flip = true)))
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-20."), o3.ncG40.legacy.take(2))
        assertEquals(listOf("G41", "G0X53.6Z20.", "Z-18.6", "G1Z-20.F.1", "X50."), o3.ncG41G42.take(5))
        val e1 = ok(TaperCalc.calc(i1.copy(end = 50.0)))
        assertEquals(listOf("G01 X50.", "G00 Z1."), e1.ncG40.legacy.takeLast(2))
        // G41/G42: 공구 쪽(+Z) 직각 c(Z1) — G40 자리가 벽 너머 반평면(반경 > 25)에 붙어 −X 로 0.986 더(반경 24.014 = X48.029, 단면 Z0 에서 1.0)
        assertEquals(listOf("X50.", "Z1.F.3", "X48.029", "G40", "G0Z200."), e1.ncG41G42.takeLast(5))
        // 안 뒤집으면 시작(작은 외경)이 자유 끝(Z+) 쪽 — 연장선 Z6 에서, 접근 줄 없이
        val fwd = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 5.0)))
        assertEquals("G00 X40. Z6.", fwd.ncG40.legacy.first())
        assertEquals(emptyList(), fwd.approach)
        // 역테이퍼(외경 형상 5) 뒤집기: 척 쪽 작은 외경(X40)부터 — 진입 X42 로 Z 를 따라 오면 오른쪽 큰 외경(X50)에 닿는다.
        // 그 바깥 X50 + 2c = X52 로 Z 를 맞춘 뒤 X 로 내려간다(세 줄)
        val o5 = ok(TaperCalc.calc(TaperInput(type = 5, x1 = 40.0, z1 = -5.0, x2 = 50.0, z2 = 0.0, start = -20.0, flip = true)))
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-20.", "G00 X42.", "G01 X40."), o5.ncG40.legacy.take(4))
        // G41/G42(막힌 시작 — 2026-11-12 통합 고침): 급송 끝 A = 공구 쪽 2r + c = 1.8(X43.6) · 앞으로 r + c = 1.4(Z-18.6). Z 줄을 X43.6 으로 오면 노즈(반경 21.4)가
        // 오른쪽 큰 외경(25)을 지나므로 그 바깥(25 + 2r + c = 반경 26.8, X53.6)에서 Z 를 맞추고 X 로 내려간 뒤 `G1Z-20.F.1` → `X40.`
        assertEquals(listOf("G41", "G0X53.6Z20.", "Z-18.6", "X43.6", "G1Z-20.F.1", "X40."), o5.ncG41G42.take(6))
        assertEquals(listOf(Pt.ofDia(1.0, 52.0), Pt.ofDia(-20.0, 52.0)), o5.approach)
        // 화면 역방향(그림을 Z 로 뒤집어 푼다 — 계산기의 +Z 가 실제 척 쪽)은 접근 줄을 끈다
        val mi = ok(TaperCalc.calc(i1.copy(start = -20.0, approach = false)))
        assertEquals("G0X32.Z-20.", mi.ncG40.first())
        // G41/G42 는 계산기 −Z 가 부품 앞이라 Z-20 너머(계산기 −Z)가 빈 시작 — 구멍 연장선 Z-21 에서 한 직선으로. 진입 Z 는 계산기 −Z 쪽: 진입점이
        // 부품 앞 끝에서 21 이라 올림 21 + 20 = 41 → Z-41., 물러남 Z-200.(화면이 Z 를 되돌린다). 실제로는 부품 앞에서 들어오는 진입이라 2026-11-12 뒤집은 진입
        // 고침(척 쪽 · 구멍 안에서 시작할 때만)을 받지 않는다 — 계산기 역방향 + 뒤집기와 같은 글
        assertEquals(listOf("G42", "G0X34.Z-41.", "Z-21.", "G1Z-20.F.1"), mi.ncG41G42.take(4))
        assertEquals("G0Z-200.", mi.ncG41G42.last())
        // 계산기 역방향(화면은 안 씀)도 앞 단면을 X− 로 깎아 끝나면 같다: X48 로 이어 가지 않고 공구 쪽(+Z)으로 c = 1. 0.8R 이라 c < 2r — 두 안쪽 꺾임
        // 노즈(Z0.8 · 반경 25.8 → Z0.2 · 반경 25.8)가 0.6 거꾸로라 0.65 더(Z1.65). G40 자리 (1 + √2)·0.8 + 0.02 = 1.951: 벽에서 +X 로, 단면(Z0 까지 1)으로는 모자라
        // 단면 위 끝 X60 Z0 에서 1.951 — √(1 + (x − 30)²) = 1.951 → 반경 31.675(X63.351, 경사 쪽 발은 그 선분 밖이라 꼭짓점이 가장 가깝다)
        val rv = ok(TaperCalc.calc(TaperInput(type = 2, reverse = true, noseR = 0.8, x1 = 60.0, z1 = 0.0, x2 = 64.368, z2 = -6.0, start = 50.0)))
        assertEquals(listOf("X60.Z0.", "X50.", "Z1.65F.3", "X63.351", "G40", "G0Z200."), rv.ncG41G42.takeLast(6))
        // 안 뒤집은 정방향의 끝 단면(어깨를 바깥으로)은 너머가 공기라 곧게 c 더(X62, 사용자 결정 ③) — 거기서 단면까지 1.0 ≥ 0.986 이라 한 줄
        assertEquals(
            listOf("X60.", "X62.F.3", "G40", "G0Z200."),
            ok(TaperCalc.calc(TaperInput(type = 4, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = 60.0))).ncG41G42.takeLast(4),
        )
    }

    /**
     * 회사 꼴 G41/G42 의 시작 · 끝(계획 단계 4 — 2026-10-28 의 FANUC 시작 · 취소 손 예를 같은 도형으로 다시, 손 계산). 보정 시작 · 취소 줄이
     * 이동 없는 혼자 줄이라 깎는 줄의 노즈는 형식 A · B / C 와 팁에 상관없이 보정 중 규칙(안쪽 = 두 보정선의 교점, 한 직선 = 법선)으로 선다 — 시뮬레이터로도 본다.
     * - 뒤집기 외경 형상 3, 끝점 = 작은 외경 Z5(부품 앞 끝, 너머가 빈 끝): 진입은 뒤집기 외경 형상 1 과 같다(T1 볼록 — 2026-11-12 뒤집은 진입: X54.546 Z-4.727 에서 진입점 X52 Z-6 으로 이송).
     *   빈 끝은 끝 좌표 너머로 곧게 c(Z6, 사용자 결정 ③) — 끝 블록 노즈가 Z5 의 법선(Z5 · 반경 20.4)에 서서 접점 = 끝좌표, 그 뒤 더 간다.
     *   Z6 X40 에서 작은 외경 끝까지 1.0 ≥ 0.986 이라 한 줄.
     * - 같은 형상을 정방향, 끝점 = 큰 외경 Z-10(척 쪽, 너머가 벽): 공구 쪽 직각 c(X52) — 안쪽 꺾임 교점 노즈 = 직접 보정의 끝 노즈(팁 벡터·u = r).
     *   G40 자리는 벽 너머 반평면(Z < −10)에 붙어 +Z 로 0.986(Z-9.014 — 큰 외경까지 1.0).
     * - 재현 A(내경 형상 1, 0.8R, 팁 1 — 역보링바를 정방향으로, AGAINST_TOOL): T1 볼록 → 진입점 = 경사 연장선 Z1 X62. 첫 깎는 줄이 −Z 라 Z 줄로 곧장
     *   (G41 · −Z 진행 노즈는 −X 쪽 반경 30.2 — Z1 둘레엔 도면이 없다). 첫 깎는 줄은 경사와 한 직선이라 노즈가 T1 의 경사 법선(Z0.566 · 반경 29.434)에 선다.
     *   끝(비움, 볼록 T2)은 경사를 큰 축으로 c 늘인 X46 Z-7 — G40 자리 (1 + √2)·0.8 + 0.02 = 1.951 이 구멍 반직선(반경 24)에서 1 이라 −X 로 0.951 더(반경 22.049, X44.097).
     *   +Z 물러남 띠는 T2 에서 1.951(경사 쪽 발은 그 선분 밖).
     * - 너머가 빈 단면 시작(뒤집기 외경 형상 2, 시작 = 어깨 바깥 X60, 0.8R 표준 팁 3): 단면을 바깥으로 깎기 시작하는 게 아니라 안쪽으로라 빈 시작 — 진입점은
     *   단면 연장선 X62 Z-5(c = 1). 첫 깎는 줄이 −X 라 Z 줄로 곧장(Z 줄 노즈 반경 30.2 — 아래 끝 29.4 가 면취(Z-4.2 에서 반경 24.2) 위, 어깨 위 끝 X60 Z-5 에서 0.825 — 그 급송 끝 노즈 틈 0.025 가 c 보다 작아 2026-11-12 부터 급송은 공구 쪽 Z 로 2r + c = 2.6 인 Z-2.4 에서 멈추고 `G1Z-5.F.1`).
     *   첫 깎는 줄은 단면과 한 직선이라 노즈가 X60 의 법선(Z-4.2 · 반경 30)에 서서 접점 = 시작 좌표 — 어깨 위 끝에 살이 안 남는다.
     *   끝(X30, 막힌 끝)은 공구 쪽 c = 1 이 2r 보다 짧아 두 안쪽 꺾임 노즈(Z0.8 · 반경 15.8 → Z0.2 · 반경 15.8)가 0.6 거꾸로 — 0.65 더(Z1.65).
     *   G40 자리 1.951: +X 로 단면(Z0 까지 1)을 지나 면취 보정선까지 (1 + x − 20)/√2 = 1.951 → 반경 21.759(X43.519, 면취 위 발).
     * - 너머가 막힌 끝(정방향 외경 형상 3 의 척 쪽 끝 Z-7.877, 1.0R 팁 6, Z 여유 2): 공구 쪽 직각 c = 2 — 두 안쪽 꺾임 노즈가 같은 점(Z-6.877 · 반경 22.029)이라
     *   0.05 더(X46.158). 노즈 끝이 끝좌표에서 멈춘다(직접 보정은 노즈 중심이 Z-7.877 — 너머로 1.0). G40 자리 2.434: 큰 외경(반경 21.029)에서 2.05 라
     *   +Z 로 경사를 지날 때까지 — 경사 보정선 거리 0.291·(z − 2.033) + 2·0.9567 = 2.434 → Z3.823(빼는 줄도 0.05 더 간 X46.158 그대로)
     * - 역테이퍼 내경 TYPE 5 표준 팁 2: T1 볼록 → 진입점 = 경사 연장선 X59.272 Z1, 첫 깎는 줄이 −Z 라 Z 줄로 곧장(노즈 위 끝 29.236 이 앞 구멍 30 안).
     *   끝(비움, 오목 T2)은 구멍을 따라 c(Z-7) — 그 점은 구멍 선 위라 −X 로 0.986, +Z 물러남 띠가 앞 구멍(반경 30)을 지나야 해 반경 29.014(X58.029)까지 한 줄로
     */
    @Test
    fun fanucStartAndCancelNumbers() {
        val f3 = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, end = 5.0, flip = true)))
        assertEquals(listOf("G41", "G0X54.546Z20.", "Z-4.727", "G1X52.Z-6.F.1", "X50.Z-5.", "X40.Z0.", "Z5.", "Z6.F.3", "G40", "G0Z200."), f3.ncG41G42)
        assertEquals("G00 X42.", f3.ncG40.legacy.last(), "직접 보정 후퇴는 그대로")
        for (form in CompStartup.values()) assertPt(Pt(5.0, 20.4), simNoses(f3.ncG41G42, 0.4, 3, form).getValue(6), 1e-6, "f3 $form 끝 블록 노즈 = Z5 의 법선")
        val w3 = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = -10.0)))
        assertEquals(listOf("Z-10.", "X52.F.3", "Z-9.014", "G40", "G0Z200."), w3.ncG41G42.takeLast(5))
        val a = TaperCalc.calc(TaperInput(inner = true, type = 1, noseR = 0.8, tip = 1, x1 = 60.0, z1 = 0.0, x2 = 48.0, z2 = -6.0))
        assertTrue(TaperWarning.AGAINST_TOOL in a.warnings)
        assertEquals(listOf("G41", "G0X62.Z20.", "Z1.", "G1X60.Z0.F.1", "X48.Z-6.", "X46.Z-7.F.3", "X44.097", "G40", "G0Z200."), ok(a).ncG41G42)
        val nS = Pt(sqrt(0.5), -sqrt(0.5)) * 0.8
        for (form in CompStartup.values()) {
            val n = simNoses(ok(a).ncG41G42, 0.8, 1, form)
            assertPt(Pt(0.0, 30.0) + nS, n.getValue(3), 1e-6, "재현 A $form 첫 깎는 점 노즈 = T1 의 경사 법선")
            assertPt(Pt(-6.0, 24.0) + nS, n.getValue(4), 1e-6, "재현 A $form 끝 노즈 = T2 의 경사 법선")
        }
        val f2 = ok(TaperCalc.calc(TaperInput(false, 2, false, 0.8, null, 50.0, -5.0, 40.0, 0.0, null, 1.0, start = 60.0, end = 30.0, flip = true)))
        assertEquals(
            listOf("G41", "G0X62.Z20.", "Z-2.4", "G1Z-5.F.1", "X60.", "X50.", "X40.Z0.", "X30.", "Z1.65F.3", "X43.519", "G40", "G0Z200."),
            f2.ncG41G42,
        )
        for (form in CompStartup.values()) assertPt(Pt(-4.2, 30.0), simNoses(f2.ncG41G42, 0.8, 3, form).getValue(4), 1e-6, "f2 $form 첫 깎는 점 노즈 = X60 의 법선")
        val w6 = ok(TaperCalc.calc(TaperInput(false, 3, false, 1.0, 6, 40.66, 4.331, 42.058, 2.033, null, 2.0, end = -7.877)))
        assertEquals(listOf("Z-7.877", "X46.158F.3", "Z3.823", "G40", "G0Z200."), w6.ncG41G42.takeLast(5))
        for (form in CompStartup.values()) {
            val run = GSim.run(SimInput((simPre + w6.ncG41G42).joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(1.0, 6))))
            assertNull(run.stop, "w6 $form ${run.stop}")
            val k = simPre.size + w6.ncG41G42.indexOf("Z-7.877") + 1
            val after = run.steps.filterIsInstance<MoveStep>().filter { it.line >= k }
            assertPt(Pt(-6.877, 22.029), after.last { it.line == k }.nose.end, 1e-6, "w6 $form 막힌 끝 노즈 = 교점")
            assertTrue(after.all { min(it.nose.start.z, it.nose.end.z) >= -6.877 - 1e-6 }, "w6 $form 노즈가 막힌 끝 너머로")
        }
        val i5 = ok(TaperCalc.calc(TaperInput(inner = true, type = 5, x1 = 60.0, z1 = 0.0, x2 = 64.368, z2 = -6.0)))
        assertEquals(listOf("G41", "G0X59.272Z20.", "Z1.", "G1X60.Z0.F.1", "X64.368Z-6.", "Z-7.F.3", "X58.029", "G40", "G0Z200."), i5.ncG41G42)
    }

    private val simPre = listOf("T0101", "G97S1000M03", "G99")

    /** G41/G42 글을 시뮬레이터(형식 [form])로 돌려 글 줄(0부터)마다 그 줄 끝 노즈 — 이동 없는 줄은 없음. 멈추면 실패 */
    private fun simNoses(nc: List<String>, r: Double, tip: Int, form: CompStartup): Map<Int, Pt> {
        val run = GSim.run(SimInput((simPre + nc).joinToString("\n"), MachineSetup(compStartup = form), mapOf(1 to ToolOffset(r, tip))))
        assertNull(run.stop, "$form ${run.stop} $nc")
        return run.steps.filterIsInstance<MoveStep>().filter { it.line > simPre.size }.groupBy { it.line - simPre.size - 1 }.mapValues { it.value.last().nose.end }
    }

    /**
     * 위 손 계산을 모든 조합으로: 넣은 시작·끝 좌표 너머가 재료(척 · 구멍 바닥 · 남은 단면)인 쪽으로 노즈가 넘어가지 않는다.
     * - 시작: 뒤집어 넣은 수평 시작 요소(그림의 뒤 요소 — 척 · 구멍 바닥 쪽). 바깥 끝 방향 o 로 노즈가 가장 멀리 간 데 = (점 − 팁 벡터)·o + r 가
     *   시작 좌표·o 이하 — 직접 보정의 G00 · 경로 점. G41/G42(회사 꼴)는 앞으로(+Z) 깎기 시작하는 것(척 · 구멍 바닥 쪽 — 막힌 시작)에서 보정 중 진입 노즈
     *   (따로 짠 모형 [frameNoses] — Z20. 부터 첫 깎는 점까지)가 노즈·o + r ≤ 시작 좌표·o. 계산기 역방향 + 뒤집기의 시작(앞 끝에서 −Z 로)은 너머가 부품 앞(공기)이라
     *   G41/G42 는 연장선으로 들어온다(직접 보정은 팁 쪽 규칙으로 직각 — 그대로 본다).
     * - 끝: 단면을 재료 쪽(외경 X−, 내경 X+)으로 깎아 끝나는 것(뒤집기 · 계산기 역방향의 앞 단면). 중심 X0 까지 깎는 것은 너머가 깎은 같은 면이라 빼고.
     *   G41/G42 는 빼는 줄의 노즈와 G40 뒤 노즈(마지막 빼는 점 − 팁 벡터).
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
                val shape4 = s.drawing.segs.drop(if (s.fromStart) 0 else 1).dropLast(if (s.toEnd) 0 else 1)
                val f4 = frameOf(s.ncG41G42, "$inner $type $reverse $flip", shape4.first().start)
                val (in4, out4) = frameNoses(f4, startTangent(shape4.first()), endTangent(shape4.last()), s.side.name, r)
                val path = s.approach + listOf(s.entry, s.program.segs.first().start) + s.program.segs.map { it.end } + s.exit
                val tag = "${if (inner) "내경" else "외경"} TYPE$type ${if (reverse) "역" else "정"}${if (flip) " 뒤집기" else ""} ${deg}° r$r R$r1/$r2 여유$zc"
                val f = s.drawing.segs.first()
                val oF = (f.start - f.end).unit()
                if (flip && abs(oF.z) > abs(oF.x)) {
                    val lim = f.start dot oF
                    for (p in path) assertTrue(reach(p, oF) <= lim + 1e-6, "$tag 직접 보정 $p 의 노즈가 시작 좌표를 넘는다 ${s.ncG40.legacy}")
                    if (oF.z < 0) for (p in in4) assertTrue((p dot oF) + r <= lim + 0.0015, "$tag G41/G42 진입 노즈 $p 가 시작 좌표를 넘는다 ${s.ncG41G42}")
                    starts++
                }
                val l = s.drawing.segs.last()
                val oL = (l.end - l.start).unit()
                if (abs(oL.x) > abs(oL.z) && (oL.x < 0) != inner && l.end.x > 1e-9) {
                    val lim = l.end dot oL
                    for (p in path) assertTrue(reach(p, oL) <= lim + 1e-6, "$tag 직접 보정 $p 의 노즈가 끝 X 를 넘는다 ${s.ncG40.legacy}")
                    for (p in out4 + (f4.exits.last() - tv)) assertTrue((p dot oL) + r <= lim + 0.0015, "$tag G41/G42 빼는 노즈 $p 가 끝 X 를 넘는다 ${s.ncG41G42}")
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
     * 시작점 Z5 를 넣으면 거기 Z+1 에서. 끝점을 비웠으니 T2′ 에서 끝난다.
     * G41/G42(회사 꼴): 진입점 = 작은 외경 선 위 T1 에서 c(Z1 X40) — Z20. 에서 Z 줄로 곧장, 첫 깎는 줄은 같은 선이라 노즈가 그 선을 타고 T1 의 교점에 선다.
     * 끝(비움, T2 볼록)은 테이퍼를 큰 축으로 c 늘인 X52 Z-6 — 큰 외경(반직선)까지 1.0, T2 까지 1.414 ≥ 0.986 이라 한 줄
     */
    @Test
    fun outerType3Numbers() {
        val s = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)))
        assertEquals(listOf("G00 X40. Z0.766", "G01 Z-0.234", "G01 X50. Z-5.234", "G00 X52."), s.ncG40.legacy)
        assertEquals(listOf("G42", "G0X40.Z20.", "Z1.", "G1Z0.F.1", "X50.Z-5.", "X52.Z-6.F.3", "G40", "G0Z200."), s.ncG41G42)
        val f = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 5.0)))
        assertEquals(listOf("G00 X40. Z6.", "G01 Z-0.234", "G01 X50. Z-5.234", "G00 X52."), f.ncG40.legacy)
        // 시작점 Z5(작은 외경 — 너머가 부품 앞이라 빈 시작): 그 선 연장 Z6 에서 한 직선으로
        assertEquals(listOf("G42", "G0X40.Z20.", "Z6.", "G1Z5.F.1", "Z0.", "X50.Z-5.", "X52.Z-6.F.3", "G40", "G0Z200."), f.ncG41G42)
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
        assertEquals(listOf("G00 X43.558 Z1.", "G01 X40.558 Z0.", "G01 X34. Z-2.186", "G01 Z-30.", "G00 X32."), s.ncG40.legacy)
        // G41/G42(회사 꼴): T1 볼록이라 진입점 = 면취 연장선을 큰 축으로 c — 56.31° 경사(Z 2 · 반경 3)라 반경으로 1: Z 0 + 2/3 = 0.667, 반경 20 + 1 = 21(X42)
        // (2026-11-04 고침 전에는 Z 로 1 — X43 Z1. 가파른 경사에서 X 가 크게 갔다). Z 줄로 곧장 — G41 · −Z 진행 노즈는 −X 쪽 반경 20.6, 꺾임 노즈
        // = 진입점 + (a + b)·r/(1 + a·b), a = (0, −1), b = 경사 왼쪽 (0.832, −0.555) → (Z0.881, 반경 20.6): 입구 단면(Z0)에서 0.881, T1 에서 1.066. 첫 깎는 줄 노즈 이동
        // 0.988 ≥ 0.05. 끝 Z-30 은 구멍 바닥 쪽(막힌 끝)이라 공구 쪽(축 쪽) 직각 c(X32) — G40 자리가 벽 너머 반평면(Z < −30)에 붙어 +Z 로 0.986(Z-29.014, 구멍까지 1.0)
        assertEquals(
            listOf("G41", "G0X42.Z20.", "Z.667", "G1X40.Z0.F.1", "X34.Z-2.", "Z-30.", "X32.F.3", "Z-29.014", "G40", "G0Z200."),
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
     * 경고는 모양에서 오는 것은 같고, 진행 방향(AGAINST_TOOL)·진입 자리(ENTRY_PAST_AXIS)·G41/G42 진입 · 빼기(SHORT_END_PAST · SHORT_END_SKIPPED · ENTRY_NOT_FOUND ·
     * EXIT_NOT_FOUND)를 따르는 것만 다를 수 있다
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
        // G41/G42 글의 진입 · 빼기 경고(짧은 막힌 끝 · 시작을 뺌 · 못 뺌, 진입 · 빼는 길 못 찾음)도 진행 방향을 따른다 — 뒤집으면 끝이 시작이 되고 진입 · 빼기 규칙이 다르다
        val byDirection = setOf(
            TaperWarning.AGAINST_TOOL, TaperWarning.ENTRY_PAST_AXIS, TaperWarning.SHORT_END_PAST, TaperWarning.SHORT_END_SKIPPED, TaperWarning.ENTRY_NOT_FOUND,
            TaperWarning.EXIT_NOT_FOUND,
        )
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
        assertEquals("G01 X50. Z-5.234", blank.ncG40.legacy[blank.ncG40.legacy.size - 2])
        assertEquals("G01 Z-10.", end.ncG40.legacy[end.ncG40.legacy.size - 2])
        // TYPE 4: 끝 = 어깨 단면 위 X60. 인선이 X60 에 오고, 단면은 바깥(위)으로 이어서 빠진다
        val s4 = ok(TaperCalc.calc(base.copy(type = 4, start = 8.0, end = 60.0)))
        assertEquals(listOf("G00 X40. Z9.", "G01 Z-0.234", "G01 X49.531 Z-5.", "G01 X60.", "G00 X62."), s4.ncG40.legacy)
        // G41/G42: 빈 끝 단면이라 곧게 c(X62) — 단면까지 1.0 이라 한 줄
        assertEquals(listOf("X60.", "X62.F.3", "G40", "G0Z200."), s4.ncG41G42.takeLast(4))
        assertPt(Pt.ofDia(-5.0, 60.0), s4.drawing.segs.last().end)
        // 내경 TYPE 1: 끝 = 구멍 깊이 Z-20
        val si = ok(TaperCalc.calc(TaperInput(inner = true, x1 = 40.0, z1 = 0.0, x2 = 34.0, z2 = -2.0, end = -20.0)))
        assertEquals("G01 Z-20.", si.ncG40.legacy[si.ncG40.legacy.size - 2])
        // G41/G42: 막힌 끝 — Z-20 까지 깎고 공구 쪽(축 쪽)으로 c, 벽 너머 반평면에서 +Z 로 0.986
        assertEquals(listOf("Z-20.", "X32.F.3", "Z-19.014", "G40", "G0Z200."), si.ncG41G42.takeLast(5))
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
        assertEquals("G00 X38.731 Z0.4", s.ncG40.legacy.first())
        // G41/G42: 진입점도 면취 연장선을 Z 로 c = r = 0.4(X39.2 Z.4) — Z20. 에서 Z 줄로. 끝은 볼록 T2 의 면취를 큰 축으로 0.4(X50.8 Z-5.4) 늘이고,
        // 거기서 외경 반직선까지 0.4 < 0.986 이라 +X 로 0.586 더(반경 25.986, X51.971)
        assertEquals(listOf("G42", "G0X39.2Z20.", "Z.4", "G1X40.Z0.F.1", "X50.Z-5.", "X50.8Z-5.4F.3", "X51.971", "G40", "G0Z200."), s.ncG41G42)
        // 노즈R 0 이면 Z 여유가 진입 거리
        assertEquals("G00 X39. Z0.5", ok(TaperCalc.calc(base.copy(noseR = 0.0, zClear = 0.5))).ncG40.legacy.first())
        stops(TaperWarning.BAD_INPUT, TaperCalc.calc(base.copy(noseR = 0.0)))
        val d = DoubleTaperInput(noseR = 0.0, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, x3 = 40.0, z3 = -10.0, zClear = 0.0)
        stops(TaperWarning.BAD_INPUT, TaperCalc.calcDouble(d))
        ok(TaperCalc.calcDouble(d.copy(noseR = 0.4)))
    }

    /**
     * 현장: "Z 여유를 바꿔도 G00 이 따라오지 않는다". 모든 형상 · 방향 · 모서리 R · 시작점 유무에서 두 NC 의 진입점이 첫 깎는 점에서 꼭
     * c = max(Z 여유, 노즈R) 떨어져야 한다.
     * - 직접 보정(G00 진입 · 화면 진입점): 첫 보정점(시작점을 넣었으면 시작점′)에서 Z 로 c. 하나만 다르다: 뒤집어 넣은 수평 시작점(척 쪽 외경 ·
     *   구멍 바닥 쪽 내경)부터 깎으면 Z 로 c 는 시작점 너머(척 · 바닥)라, 그 Z 에서 공구 쪽으로 X 로 c.
     * - G41/G42(회사 꼴 — 진입 G0 의 마지막 점, 도면 좌표): 첫 깎는 도면점에서 c — 넣은 시작 좌표 너머가 막혔으면(척 · 구멍 바닥 쪽 수평, 바깥으로 깎기 시작하는
     *   단면 — 외경 X0 빼고) 공구 쪽 직각, 아니면 첫 깎는 요소와 한 직선으로 너머. 비운 시작은 첫 꺾임이 날카로운 볼록이면 경사 연장선(큰 축으로 c — 45° 를 넘는
     *   경사는 X 로 c, 2026-11-04),
     *   아니면 시작 요소 선 위로 c. 첫 깎는 줄의 노즈 이동(따로 짠 모형 [noseAt])이 0.05 보다 짧으면 그 줄을 따라 모자란 만큼 더 물린다
     */
    @Test
    fun entryFollowsZClearance() {
        var checked = 0
        var pulled = 0
        fun check(tag: String, res: TaperResult, zClear: Double, r: Double, flip: Boolean, inner: Boolean) {
            val s = ok(res)
            val c = max(zClear, r)
            // 진입 G00 = 처음 G00 줄들의 마지막(뒤집어 척 쪽에서 시작하면 그 앞에 접근 줄)
            val g40 = ncTrace(s.ncG40.legacy)[s.approach.size]
            assertEquals(entryAt(s.ncG40.legacy), s.approach.size, "$tag 접근 줄 수")
            assertEquals(printed(s.entry), g40, "$tag 화면 진입점 = 직접 보정 G00")
            val skip = if (s.fromStart) 0 else 1
            val first = s.program.segs[skip].start
            val d0 = s.drawing.segs[0]
            val u0 = (d0.end - d0.start).unit()
            if (flip && s.fromStart && abs(u0.z) > abs(u0.x) && u0.z * NoseComp.tipVector(s.tip, 1.0).z <= 0) {
                assertEquals(first.z, s.entry.z, 1e-9, "$tag 직접 보정 진입 Z ${s.ncG40.legacy}")
                assertEquals(c, abs(s.entry.x - first.x), 1e-9, "$tag 직접 보정 진입 X ${s.ncG40.legacy}")
            } else {
                assertEquals(c, abs(s.entry.z - first.z), 1e-9, "$tag 직접 보정 진입 Z ${s.ncG40.legacy}")
            }
            // G41/G42 진입점(손 규칙)
            val firstD = s.drawing.segs[skip].start
            val f = frameOf(s.ncG41G42, tag, firstD)
            val side = s.side.name
            val d1 = s.drawing.segs[1]
            val u1 = startTangent(s.drawing.segs[skip])
            val nF = sideN(u0, side)
            val horiz = abs(u0.z) > abs(u0.x)
            val blocked = s.fromStart && (if (horiz) u0.z > 0 else (u0.x > 0) != inner && !(!inner && d0.start.x <= 1e-9))
            val convex = d1 is Line && ((u0 cross startTangent(d1)) > 0) == (side == "G42")
            val e0 = when {
                blocked -> d0.start + nF * c
                s.fromStart -> d0.start - u0 * c
                convex -> d0.end - startTangent(d1) * (c / max(abs(startTangent(d1).z), abs(startTangent(d1).x)))
                else -> d0.end - u0 * c
            }
            val e = f.approach.last()
            val prev = (e - f.approach[f.approach.size - 2]).unit()
            val uG = (firstD - e0).unit()
            if (f.lead.isEmpty()) {
                val moved = (noseAt(firstD, uG, u1, side, r) - noseAt(e0, prev, uG, side, r)) dot uG
                val eWant = if (moved >= 0.05) e0 else e0 - uG * (0.05 - moved)
                assertTrue(
                    abs(e.xDia - eWant.xDia) <= 0.0011 && abs(e.z - eWant.z) <= 0.0011,
                    "$tag G41/G42 진입점 $e ≠ 손 $eWant(${if (blocked) "막힌 시작 — 공구 쪽 직각" else if (s.fromStart) "빈 시작 — 한 직선" else if (convex) "볼록 — 경사 연장" else "시작 요소 선 위"}) ${s.ncG41G42}",
                )
            } else {
                // 뒤집은 진입(2026-11-12 통합 고침): 급송 끝 A → `G1 P F.1` → 첫 깎는 점. P 는 위 진입점의 줄(방향 uG) 위 — 막힌 시작은 공구 쪽 2r + c/4 이상,
                // 그 밖은 위 진입점보다 멀리 — A 는 P 에서 그 줄에 직각으로 공구 쪽 c/4 이상
                assertTrue(flip && f.lead.size == 1, "$tag 진입 이송 줄은 뒤집기에서 하나 ${s.ncG41G42}")
                val p = f.lead.single()
                val back = firstD - p
                assertTrue(
                    abs(back.unit() cross uG) < 2e-3 && (back dot uG) >= (if (blocked) 2 * r + c / 4 else (firstD - e0).length) - 2e-3,
                    "$tag G41/G42 P $p 가 손 진입점 $e0 의 줄 밖 ${s.ncG41G42}",
                )
                assertTrue(abs((e - p).unit() dot uG) < 2e-3 && ((e - p) dot sideN(uG, side)) >= c / 4 - 2e-3, "$tag G41/G42 급송 끝 $e 가 P 의 공구 쪽 직각이 아니다 ${s.ncG41G42}")
            }
            if (blocked) pulled++
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
                check(i.toString(), TaperCalc.calc(i), zc, r, false, inner)
                // 뒤집기: 같은 그림을 반대 끝부터 — 가공 시작 쪽 요소가 반대 끝이다
                val f = (if (!lead) inp else if (reverse) right else left).flipped()
                check(f.toString(), TaperCalc.calc(f), zc, r, true, inner)
            }
        }
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))) {
            for (rc in listOf(0.0, 1.0)) for (zc in zs) {
                val i = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, zc, rc, rc, rc)
                check(i.toString(), TaperCalc.calcDouble(i), zc, 0.4, false, inner)
                check(i.flipped().toString(), TaperCalc.calcDouble(i.flipped()), zc, 0.4, true, inner)
            }
        }
        assertTrue(checked > 3000, "확인한 사례 $checked")
        assertTrue(pulled > 50, "막힌 시작(공구 쪽 직각 진입) $pulled")
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
            s.ncG40.legacy,
        )
        // G41/G42: 시작 단면 X30 을 바깥으로 깎기 시작(너머 보스 — 막힌 시작)이라 공구 쪽(Z+1)에서 직각으로 X30 Z0 에. 끝은 볼록 T2 의 면취 연장 X52 Z-6
        assertEquals(
            listOf("G42", "G0X30.Z20.", "Z1.", "G1Z0.F.1", "X39.172", "G3X40.586Z-.293R1.", "G1X50.Z-5.", "X52.Z-6.F.3", "G40", "G0Z200."),
            s.ncG41G42,
        )
        // T2 는 날카로운 그대로
        assertPt(Pt(-5 - NoseR.taperDeltaZ(0.4, 45.0), 25.0), sharp(s.points[1]))
        // 시작점을 비우면 T1 에 모서리 R 이 있어 경사가 아니라 단면 바깥(공구 쪽 Z+1)에서 R 시작′ 으로 들어간다.
        // G41/G42 는 단면 선 위 R 시작에서 c 아래(X37.172 Z0 — 시작 요소 선 위)가 진입점: 첫 깎는 줄이 +X 라 Z 줄로 곧장(꺾임 노즈 Z0.4 · 반경 18.986 —
        // 단면에서 r), 첫 깎는 줄은 단면 · R 시작 접선과 한 직선이라 노즈가 R 시작의 법선에 선다(0.6 앞으로)
        val b0 = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 1.0)))
        assertEquals(listOf("G00 X38.372 Z1.", "G01 Z0.", "G03 X40.351 Z-0.41 R1.4"), b0.ncG40.legacy.take(3))
        assertEquals(listOf("G42", "G0X37.172Z20.", "Z0.", "G1X39.172F.1", "G3X40.586Z-.293R1."), b0.ncG41G42.take(5))
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
        assertEquals(listOf("G00 X40. Z6.", "G01 Z0.014", "G02 X40.351 Z-0.41 R0.6", "G01 X50. Z-5.234", "G00 X52."), s.ncG40.legacy)
        // G41/G42: 시작점 Z5 의 작은 외경 연장 Z6 에서 한 직선으로 Z5 → R 시작 Z.414 → 도면 R1(G2)
        assertEquals(listOf("G42", "G0X40.Z20.", "Z6.", "G1Z5.F.1", "Z.414", "G2X40.586Z-.293R1.", "G1X50.Z-5."), s.ncG41G42.take(7))
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
            assertTrue(s.ncG40.legacy.none { it.startsWith("G02") || it.startsWith("G03") }, "R$rc ${s.ncG40.legacy}")
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
     * G41/G42 프로그램(회사 꼴)의 보정 구간 — 진입 G0 마지막 줄 · 첫 깎는 줄(F.1) · 형상 · 첫 빼는 줄(F.3), 도면 좌표 — 을 글에서 읽어 NoseComp(보정 중 규칙:
     * 안쪽 = 두 보정선의 교점, 한 직선 = 법선)로 보정하면 형상의 이음점마다 직접 보정 프로그램의 좌표와 0.001 안에서 같다.
     * 처음 · 끝 점은 약속이 달라 문서의 식으로 본다(calc-design '진입점과 NC'). 손으로 정한 시작점′ · 끝점′ = 인선이 그 좌표에 오는 점 s0 · e0
     * (그 요소 보정선 위 발 — 직접 보정의 약속):
     * - 처음: 넣은 시작 좌표 너머가 막혔으면 공구 쪽 직각 진입(안쪽 꺾임 교점) → (G41/G42) − s0 = u·(r + 팁 벡터·u), 빈 시작은 한 직선 진입(법선) → u·(팁 벡터·u).
     *   비운 시작: 날카로운 볼록이면 경사 연장선 진입이라 노즈가 경사 법선에 선다 — 직접 보정의 경사 보정선 위, 그 첫 점(교점)보다 앞.
     *   그 밖(오목 · 모서리 R)은 시작 요소 선을 따라 들어가 제어기가 교점 = 직접 보정의 첫 점을 잡는다.
     * - 끝: 막힌 끝(요소가 노즈R 쯤보다 길 때)은 공구 쪽 직각 빼기(교점 — 노즈 끝이 끝좌표에서 멈춤) → (G41/G42) − e0 = u·(팁 벡터·u − r),
     *   빈 끝 · 짧은 막힌 끝 · 축에서 끝나는 단면(X −2r 까지 쓰고 직각)은 끝 법선 → u·(팁 벡터·u). 비운 끝은 볼록이면 경사 법선(직접 보정의 경사 보정선 위 ·
     *   그 끝점보다 앞), 그 밖은 끝 요소를 따라 빼서 교점 = 직접 보정의 마지막 점.
     * 함께: 틀(보정 혼자 줄 · G0 진입 · F.1 · F.3 · G40 혼자 · G0Z200.), 보정 쪽은 표대로, 형상 줄 = 도면(시작 · 끝점을 비웠으면 그 요소는 빼고),
     * 첫 깎는 줄 · 빼는 줄의 노즈가 0.05 이상 앞으로(거꾸로 가면 간섭 알람).
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
                    compareNc(s, i.toString(), i.inner, r)
                    checked++
                }
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            val inp = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
            for (i in listOf(inp, inp.copy(start = 4.0), inp.copy(end = -14.0), inp.copy(start = 4.0, end = -14.0)).flatMap { listOf(it, it.flipped()) }) {
                val s = TaperCalc.calcDouble(i).solution ?: continue
                compareNc(s, i.toString(), i.inner, 0.4)
                checked++
            }
        }
        assertTrue(checked > 2400, "확인한 사례 $checked")
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

    private fun endTangent(sg: Seg): Pt = when (sg) {
        is Line -> (sg.end - sg.start).unit()
        is Arc -> (sg.end - sg.center).unit().left() * (if (sg.cw) -1.0 else 1.0)
    }

    private fun compareNc(s: TaperSolution, tag: String, inner: Boolean, r: Double) {
        val side = s.side.name
        val f = frameOf(s.ncG41G42, tag, s.drawing.segs[if (s.fromStart) 0 else 1].start)
        assertEquals(side, f.side, tag)
        val tv = NoseComp.tipVector(s.tip, r)
        val d = s.drawing.segs
        val first = d.first()
        val last = d.last()
        val u0 = startTangent(first)
        val uB = endTangent(last)
        val nF = sideN(u0, side)
        val nB = sideN(uB, side)
        fun horiz(u: Pt) = abs(u.z) > abs(u.x)
        // 글의 형상 줄 = 도면(시작 · 끝점을 비웠으면 그 요소는 빼고). 축에서 끝나는 단면은 X −2r 까지(노즈 중심이 축에)
        val axisEnd = s.toEnd && last is Line && !horiz(uB) && uB.x < 0 && last.end.x <= 1e-9
        val exact0 = d.drop(if (s.fromStart) 0 else 1).dropLast(if (s.toEnd) 0 else 1)
        val exact = if (axisEnd) exact0.dropLast(1) + Line(last.start, last.end + uB * r) else exact0
        val shape = splitArcs(exact)
        assertEquals(shape.size, f.shape.size, "$tag 형상 줄 수 ${s.ncG41G42}")
        for ((a, b) in shape.zip(f.shape)) assertTrue(near(a.end, b.p!!), "$tag 형상 줄 ${b.text} ≠ 도면 ${a.end}")
        val firstCut = shape.first().start
        assertTrue(near(f.first, firstCut), "$tag 첫 깎는 점 ${f.first} ≠ $firstCut")
        val lastCut = shape.last().end
        // 보정할 윤곽(반올림 전 도면 + 글의 진입 · 빼는 점 — 도면 요소와 한 직선이거나 공구 쪽 직각이면 그 방향으로 맞춘다)
        val u1 = startTangent(shape.first())
        val uL = endTangent(shape.last())
        // 뒤집은 진입(2026-11-12)은 급송 끝 → P 이송 줄 뒤 P → 첫 깎는 점이 첫 깎는 줄과 같은 몫이다
        val inPts = f.approach + f.lead
        val e = inPts.last()
        val eLen = (firstCut - e).length
        val eDir = listOf(u1, sideN(u1, side) * -1.0).firstOrNull { abs((firstCut - e).unit() cross it) < 1e-3 && ((firstCut - e) dot it) > 0 }
        val eX = if (eDir != null) firstCut - eDir * eLen else e
        val x1 = f.exits[0]
        val xLen = (x1 - lastCut).length
        val xDir = listOf(uL, sideN(uL, side)).firstOrNull { abs((x1 - lastCut).unit() cross it) < 1e-3 && ((x1 - lastCut) dot it) > 0 }
        val x1X = if (xDir != null) lastCut + xDir * xLen else x1
        // (진입 Z 줄 · X 맞춤 줄은 넣지 않는다 — NoseComp 는 윤곽 전체의 닿는 곳을 풀어 Z20. 부터의 긴 공기 줄이 겹테이퍼 골 위를 지나면 풀지 않는다.
        // 첫 깎는 줄 처음 · 첫 빼는 줄 끝의 노즈는 따로 짠 [noseAt] 으로)
        val contour = listOf(Line(eX, firstCut)) + shape + Line(lastCut, x1X)
        val comp = try {
            NoseComp.tipPath(Contour(contour), r, s.side, s.tip)
        } catch (e: IllegalArgumentException) {
            throw AssertionError("$tag ${e.message} ${s.ncG41G42} $contour")
        }
        // 오목 R < r 은 보정 원호가 점이 된다(직접 보정도 같다 — CONCAVE_R_LE_NOSE). 그 밖에 빠지는 요소는 없어야 한다
        assertTrue(comp.warnings.all { it is CompWarning.ArcTooSmall } && comp.path.segs.size == contour.size, "$tag 보정이 요소를 뺐다 ${comp.warnings}")
        val tips = comp.path.segs.map { it.end }
        // 첫 깎는 줄 · 첫 빼는 줄의 노즈 이동 ≥ 0.05 — 진입 G0 → 첫 깎는 줄, 첫 빼는 줄 → 다음 빼는 줄 꺾임의 노즈는 따로 짠 모형
        val uG = (firstCut - eX).unit()
        val noseE = noseAt(eX, (eX - inPts[inPts.size - 2]).unit(), uG, side, r)
        assertTrue(((tips[0] - tv - noseE) dot uG) >= 0.05 - 1e-3, "$tag 첫 깎는 줄 노즈 이동 ${s.ncG41G42}")
        val uX = (x1X - lastCut).unit()
        val noseX = noseAt(x1X, uX, if (f.exits.size > 1) (f.exits[1] - x1).unit() else uX, side, r)
        assertTrue(((noseX - (tips[tips.size - 2] - tv)) dot uX) >= 0.05 - 1e-3, "$tag 첫 빼는 줄 노즈 이동 ${s.ncG41G42}")
        val mine = distinct(tips.dropLast(1)) // 첫 깎는 점, 형상 줄 끝마다(점이 된 원호는 하나로)

        // 직접 보정: 진입점 ~ 후퇴 G00 앞
        val g0 = parseMoves(s.ncG40.legacy).let { m -> m.drop(m.indexOfFirst { it.first != listOf("G00") }) }
        assertEquals(listOf("G00"), g0.last().first, tag)
        val direct = distinct(ends(g0.dropLast(1).map { it.second }))
        val entry = direct[0]
        val body = direct.drop(1)

        // 처음
        var m = mine
        var b = body
        if (s.fromStart) {
            val s0 = first.start + nF * r + tv - u0 * (tv dot u0)
            val blocked0 = if (horiz(u0)) u0.z > 0 else (u0.x > 0) != inner && !(!inner && first.start.x <= 1e-9)
            // 시작 요소가 노즈R 쯤보다 짧아 직각 진입의 교점이 그 요소의 보정 끝보다 뒤면 한 직선으로(알려진 한계 — 끝의 짧은 막힌 끝과 같다)
            val short = ((s.center.segs[0].end - (first.start + nF * r + u0 * r)) dot u0) < 0.05
            val blocked = blocked0 && !short
            val want = s0 + u0 * ((if (blocked) r else 0.0) + (tv dot u0))
            assertTrue(near(m[0], want), "$tag 처음 점 ${m[0]} ≠ s0 + u·(${if (blocked) "r + " else ""}팁 벡터·u) = $want\n${s.ncG41G42}\n${s.ncG40.legacy}")
            // 직접 보정이 시작점′ 을 따로 쓰면(단면 · 직각 진입) 그 점은 건너뛴다. 연장선 진입이면 진입선 위
            if (near(body[0], s0)) b = b.drop(1) else assertTrue(onLine(s0, entry, body[0]), "$tag 직접 보정 시작점′ $s0")
            m = m.drop(1)
        } else if (d[1] is Line && ((u0 cross startTangent(d[1])) > 0) == (side == "G42")) {
            // 날카로운 볼록 첫 꺾임: 노즈가 경사 법선 — 직접 보정의 경사 보정선(첫 점 → 둘째 점) 위, 첫 점(교점)보다 앞
            assertTrue(onLine(m[0], body[0], body[1]) && ((m[0] - body[0]) dot (body[1] - body[0])) >= -1e-3, "$tag 처음 점 ${m[0]} 이 경사 보정선 ${body[0]} → ${body[1]} 위가 아니다")
            m = m.drop(1)
            b = b.drop(1)
        }
        // 끝
        if (s.toEnd) {
            val e0 = last.end + nB * r + tv - uB * (tv dot uB)
            val blocked = if (horiz(uB)) uB.z < 0 else (uB.x < 0) != inner && !(!inner && last.end.x <= 1e-9)
            val short = (((last.end + nB * r - uB * r) - (s.center.segs.last().start)) dot uB) < 0.05
            val wall = blocked && !short
            val want = e0 + uB * ((tv dot uB) - (if (wall) r else 0.0))
            assertTrue(near(m.last(), want), "$tag 끝 점 ${m.last()} ≠ e0 + u·(팁 벡터·u${if (wall) " − r" else ""}) = $want\n${s.ncG41G42}\n${s.ncG40.legacy}")
            m = m.dropLast(1)
            b = b.dropLast(1)
        } else if (last is Line && exact.last() is Line && ((endTangent(exact.last()) cross uB) > 0) == (side == "G42")) {
            // 날카로운 볼록 끝 꺾임(비운 끝): 노즈가 경사 법선 — 직접 보정의 마지막 경사 보정선 위, 그 끝(교점)보다 앞
            val p0 = body[body.size - 2]
            val p1 = body.last()
            assertTrue(onLine(m.last(), p0, p1) && ((p1 - m.last()) dot (p1 - p0)) >= -1e-3, "$tag 끝 점 ${m.last()} 이 경사 보정선 $p0 → $p1 위가 아니다")
            m = m.dropLast(1)
            b = b.dropLast(1)
        }
        assertEquals(b.size, m.size, "$tag 점 개수 G41/G42=$m G40=$b\n${s.ncG41G42}\n${s.ncG40.legacy}")
        for (k in m.indices) assertTrue(near(m[k], b[k]), "$tag ${k}번 점: G41/G42 보정 ${m[k]} ≠ 직접 보정 ${b[k]}\n${s.ncG41G42}\n${s.ncG40.legacy}")
    }
}
