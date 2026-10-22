package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
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
     * - G41/G42: 주석 → G00 진입 → G4x G01 시작점(진입 거리 ≥ r) → T 점(도면) → 끝점 → G40 G00 후퇴.
     *   재료인 선(시작·끝 요소)을 따라 들고 날 때 [lift] > 0 이면 G00 점을 공구 쪽으로 c + lift 띄우고, 들어갈 때는
     *   그 선에 직각으로 G4x G01 한 뒤 선을 따라 G01 로 간다. 끝점을 비우면 끝점 없이 마지막 T 점에서 보정을 푼다 —
     *   마지막 꺾임이 볼록이면 경사를 Z 로 c 더 늘인 점으로, 아니면 끝 요소 선을 따라 c(손으로 정한 표 slopeExit).
     *   바이트 반대쪽 모양이면 노즈가 꺾임을 벗어나게 더 간다
     */
    private fun checkNc(
        s: TaperSolution, order: List<Pt>, corners: List<Pt>, inner: Boolean, reverse: Boolean, r: Double, tag: String,
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
        // 끝: 끝점을 넣었으면 인선이 끝점에 오고(끝점′) 수평이면 공구 쪽으로, 단면이면 이어서 c. 비웠으면 마지막 보정점에서 공구 쪽으로 c
        val g40End: List<Pt>
        val g4End: Pt
        if (s.toEnd) {
            val leave = if (lastH) nB else (en - tl).unit()
            val e0 = legFoot(en, tl, inner, tip, r)
            assertPt(e0 + leave * c, s.exit, msg = "$tag 후퇴점")
            g40End = listOf(e0, s.exit)
            g4End = en + leave * c + nB * lift(nB, tip, r)
        } else {
            assertPt(corners.last() + nB * c, s.exit, msg = "$tag 후퇴점이 마지막 보정점에서 끝 요소의 공구 쪽으로 c")
            g40End = listOf(s.exit)
            val ahead = tl - order[order.size - 3]
            val nS = toolNormal(order[order.size - 3], tl, inner)
            val uB = (en - tl).unit()
            // 바이트 반대쪽이면 노즈가 꺾임을 벗어나게: 경사 쪽 lift, 끝 요소 쪽은 마지막 보정점이 꺾임에서 끝 요소를 따라 간 만큼 더
            g4End = if (slopeExit) {
                tl + ahead * (c / abs(ahead.z)) + nS * lift(nS, tip, r)
            } else {
                tl + uB * (c + max(0.0, (corners.last() - tl) dot uB)) + nB * lift(nB, tip, r)
            }
        }
        val shape = order.dropLast(if (s.toEnd) 0 else 1)
        // 시작 요소 선을 따라 들어가는 진입(선 위의 점 q, 법선 n). 노즈가 선을 넘으면 띄운 점 → 선 위의 점 q2 로 직각 진입.
        // 그 이동 끝(교점 규칙)의 인선이 직접 보정의 진입점이 되게 q2 = (진입점 − 팁 벡터 를 선에 내린 발) − u·r
        val u0 = (order[1] - st).unit()
        val tv0 = NoseComp.tipVector(tip, r)
        fun onLine(q: Pt, n: Pt): List<Pt> = lift(n, tip, r).let {
            if (it <= 1e-9) return@let listOf(q)
            val q2 = st + u0 * ((s.entry - tv0 - st) dot u0) - u0 * r
            listOf(q2 + n * (c + it), q2)
        }
        // 진입: 시작점을 넣었으면 그 요소의 연장선(수평)·공구 쪽(단면)에서 시작점으로. 비웠으면 시작 요소는 깎지 않고
        // 첫 꺾임이 볼록이면 경사선을(손으로 정한 표 slopeEntry), 아니면 수평인 시작 요소는 그 보정선을 늘인 곳에서,
        // 단면은 공구 쪽(Z)에서 첫 보정점으로 — G41/G42 는 단면 선 위 점에 직각으로 들어가 선을 따라 첫 T 점으로
        val nF = toolNormal(st, order[1], inner)
        val g40: List<Pt>
        val g4Start: List<Pt>
        if (s.fromStart) {
            val approach = if (firstH) (st - order[1]).unit() else nF
            val s0 = legFoot(st, order[1], inner, tip, r)
            assertPt(s0 + approach * c, s.entry, msg = "$tag 진입점")
            g40 = listOf(s.entry) + (if (firstH) emptyList() else listOf(s0)) + corners
            g4Start = (if (firstH) onLine(st + approach * c, nF) else listOf(st + nF * (c + lift(nF, tip, r)))) + shape
        } else {
            val back = order[1] - order[2]
            val lead = (st - order[1]).unit()
            if (slopeEntry) {
                assertPt(corners[0] + back * (c / abs(back.z)), s.entry, msg = "$tag 진입점이 보정 경사선 위 Z 로 c 바깥")
                g4Start = listOf(order[1] + back * (c / abs(back.z))) + shape.drop(1)
            } else if (firstH) {
                assertPt(corners[0] + lead * c, s.entry, msg = "$tag 진입점이 시작 요소 보정선 위 c 바깥")
                g4Start = onLine(order[1] + lead * c, nF) + shape.drop(1)
            } else {
                assertPt(corners[0] + nF * c, s.entry, msg = "$tag 진입점이 첫 보정점에서 공구 쪽(Z)으로 c")
                // 직각으로 들어가 선 위 q 에 서면 노즈는 q 보다 r 앞 = 첫 보정점의 노즈 자리
                val q = st + u0 * ((corners[0] - tv0 - st) dot u0) - u0 * r
                g4Start = listOf(q + nF * (c + lift(nF, tip, r)), q) + shape.drop(1)
            }
            g40 = listOf(s.entry) + corners
        }
        assertEquals((g40 + g40End).map(::printed), ncTrace(s.ncG40), "$tag G40 ${s.ncG40}")
        assertEquals(
            listOf("G00") + g40.drop(1).map { "G01" } + (if (s.toEnd) listOf("G01", "G00") else listOf("G00")),
            s.ncG40.map { it.take(3) }, tag,
        )

        val g4 = s.ncG41G42
        assertEquals("(R${Fmt.nc(r).removeSuffix(".")} T$tip)", g4[0], tag)
        assertEquals((g4Start + g4End).map(::printed), ncTrace(g4), "$tag G41/G42 $g4")
        val side = expectedSide(inner, reverse)
        assertTrue(g4[2].startsWith("$side G01 "), "$tag 보정 시작 ${g4[2]}")
        assertTrue(g4.last().startsWith("G40 G00 "), "$tag 보정 취소 ${g4.last()}")
        assertTrue(g4.drop(3).dropLast(1).all { it.startsWith("G01 ") }, "$tag $g4")
        assertTrue(c >= r, "진입 이동 ≥ r")
    }

    /**
     * 외경·내경 × TYPE 1~5 × 정/역방향 × 5°~85° × r 0.2~1.2 에서
     * 보정점이 일반 해법·이등분선 식·손 닫힌 식과 모두 맞고, 두 NC 가 약속대로인지.
     */
    @Test
    fun everyTypeAndDirection() {
        val a = 40.0 // T1 반경(X80)
        val len = 2.0
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) {
            for (deg in angles) for (r in radii) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val t1 = Pt(0.0, a)
                val t2 = Pt(-len, a + up * len * tan(rad(deg)))
                val tag = "${if (inner) "내경" else "외경"} TYPE$type ${if (reverse) "역" else "정"} ${deg}° r$r"
                val res = TaperCalc.calc(
                    TaperInput(inner, type, reverse, r, x1 = t1.xDia, z1 = t1.z, x2 = t2.xDia, z2 = t2.z),
                )
                val s = ok(res)
                val tip = NoseComp.standardTip(inner, reverse)
                assertEquals(tip, s.tip, tag)
                assertEquals(Side.of(inner, reverse), s.side, tag)
                assertEquals(deg, s.anglesDeg.single(), 1e-9, tag)
                assertPt(t1, s.points[0].vertex, msg = tag)
                assertPt(t2, s.points[1].vertex, msg = tag)

                // 그림대로의 앞뒤 요소: TYPE 1·2 는 T1 에 단면, TYPE 2·4 는 T2 에 단면, 나머지는 수평. 비우면 5(단면은 반경)
                val frontV = type <= 2
                val backV = type == 2 || type == 4
                val f = if (frontV) t1 + Pt(0.0, -up * 5) else t1 + Pt(5.0, 0.0)
                val b = if (backV) t2 + Pt(0.0, up * 5) else t2 + Pt(-5.0, 0.0)
                val p1 = sharp(s.points[0])
                val p2 = sharp(s.points[1])
                assertPt(cornerByLines(f, t1, t2, inner, tip, r), p1, msg = "$tag T1′ 일반 해법")
                assertPt(cornerByLines(t1, t2, b, inner, tip, r), p2, msg = "$tag T2′ 일반 해법")
                assertPt(cornerByBisector(f, t1, t2, inner, tip, r), p1, msg = "$tag T1′ 이등분선")
                assertPt(cornerByBisector(t1, t2, b, inner, tip, r), p2, msg = "$tag T2′ 이등분선")
                assertPt(t1 + closedShift(inner, type != 5, reverse, frontV, deg, r), p1, msg = "$tag T1′ 닫힌 식")
                assertPt(t2 + closedShift(inner, type != 5, reverse, backV, deg, r), p2, msg = "$tag T2′ 닫힌 식")
                assertEquals(p1.xDia - t1.xDia, s.points[0].nodes.single().dXDia, 1e-12, tag)
                assertEquals(p1.z - t1.z, s.points[0].nodes.single().dZ, 1e-12, tag)

                // 정방향은 역테이퍼(TYPE 5)만, 역방향은 TYPE 1~4 가 바이트와 반대
                val against = reverse != (type == 5)
                assertEquals(if (against) listOf(TaperWarning.AGAINST_TOOL) else emptyList(), res.warnings, tag)

                val order = listOf(f, t1, t2, b).let { if (reverse) it.reversed() else it }
                // 볼록 꺾임(재료 모서리가 공구 쪽으로 나옴): T1 은 TYPE 1·2·5, T2 는 TYPE 1·3. 역방향은 T2 에서 시작해 T1 에서 끝난다
                val convex1 = type == 1 || type == 2 || type == 5
                val convex2 = type == 1 || type == 3
                val slopeEntry = if (reverse) convex2 else convex1
                val slopeExit = if (reverse) convex1 else convex2
                checkNc(s, order, if (reverse) listOf(p2, p1) else listOf(p1, p2), inner, reverse, r, tag, slopeEntry, slopeExit)
                // 시작점을 넣으면 그 요소부터 깎는다
                val sIn = if (reverse) {
                    if (backV) b.xDia else b.z
                } else {
                    if (frontV) f.xDia else f.z
                }
                val s2 = ok(TaperCalc.calc(TaperInput(inner, type, reverse, r, x1 = t1.xDia, z1 = t1.z, x2 = t2.xDia, z2 = t2.z).let {
                    if (reverse) it.copy(end = sIn) else it.copy(start = sIn)
                }))
                assertTrue(s2.fromStart && !s.fromStart, tag)
                checkNc(s2, order, if (reverse) listOf(p2, p1) else listOf(p1, p2), inner, reverse, r, "$tag 시작점", slopeEntry, slopeExit)
                // 끝점을 넣으면 끝 요소를 그 좌표까지 깎는다(비우면 마지막 T 점에서 끝)
                val eIn = if (reverse) {
                    if (frontV) f.xDia else f.z
                } else {
                    if (backV) b.xDia else b.z
                }
                val s3 = ok(TaperCalc.calc(TaperInput(inner, type, reverse, r, x1 = t1.xDia, z1 = t1.z, x2 = t2.xDia, z2 = t2.z).let {
                    if (reverse) it.copy(start = eIn) else it.copy(end = eIn)
                }))
                assertTrue(s3.toEnd && !s.toEnd && !s3.fromStart, tag)
                checkNc(s3, order, if (reverse) listOf(p2, p1) else listOf(p1, p2), inner, reverse, r, "$tag 끝점", slopeEntry, slopeExit)
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
            listOf("(R0.4 T3)", "G00 X38. Z1.", "G42 G01 X40. Z0.", "G01 X50. Z-5.", "G40 G00 X52. Z-6."),
            s.ncG41G42,
        )
        // 시작점 = 단면 X30: 공구 쪽(Z+1)에서 들어가 인선을 X30 Z0 에 맞추고 단면부터 깎는다
        val f = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 30.0)))
        assertEquals(listOf("G00 X30. Z1.", "G01 Z0.", "G01 X39.531", "G01 X50. Z-5.234", "G00 X52."), f.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X30. Z1.", "G42 G01 Z0.", "G01 X40.", "G01 X50. Z-5.", "G40 G00 X52. Z-6."),
            f.ncG41G42,
        )
        // 끝점 = Z-10: 인선이 Z-10 에 멈춘 뒤 X 로 1 빠진다
        val e = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, end = -10.0)))
        assertEquals(listOf("G00 X37.531 Z1.", "G01 X39.531 Z0.", "G01 X50. Z-5.234", "G01 Z-10.", "G00 X52."), e.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X38. Z1.", "G42 G01 X40. Z0.", "G01 X50. Z-5.", "G01 Z-10.", "G40 G00 X52."),
            e.ncG41G42,
        )
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
            listOf("(R0.4 T3)", "G00 X40. Z1.", "G42 G01 Z0.", "G01 X50. Z-5.", "G40 G00 X52. Z-6."),
            s.ncG41G42,
        )
        val f = ok(TaperCalc.calc(TaperInput(type = 3, x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 5.0)))
        assertEquals(listOf("G00 X40. Z6.", "G01 Z-0.234", "G01 X50. Z-5.234", "G00 X52."), f.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X40. Z6.", "G42 G01 Z5.", "G01 Z0.", "G01 X50. Z-5.", "G40 G00 X52. Z-6."),
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
            listOf("(R0.4 T2)", "G00 X43. Z1.", "G41 G01 X40. Z0.", "G01 X34. Z-2.", "G01 Z-30.", "G40 G00 X32."),
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

    @Test
    fun defaultsForScreen() {
        val i = TaperInput()
        assertEquals(0.4, i.noseR)
        assertEquals(1.0, i.zClear)
        assertEquals(1, i.type)
        assertEquals(false, i.reverse)
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
        assertEquals("G40 G00 X62.", s4.ncG41G42.last())
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
     * 반대인 단면으로 들어갈 때만 G41/G42 진입을 노즈가 단면을 넘지 않게 더 띄운다
     */
    @Test
    fun entryFollowsZClearance() {
        var checked = 0
        fun check(tag: String, res: TaperResult, zClear: Double, r: Double) {
            val s = ok(res)
            val c = max(zClear, r)
            val g40 = ncTrace(s.ncG40).first()
            assertEquals(printed(s.entry), g40, "$tag 화면 진입점 = 직접 보정 G00")
            val skip = if (s.fromStart) 0 else 1
            val first = s.program.segs[skip].start
            assertEquals(c, abs(s.entry.z - first.z), 1e-9, "$tag 직접 보정 진입 Z ${s.ncG40}")
            val g4 = ncTrace(s.ncG41G42).first()
            val firstD = s.drawing.segs[skip].start
            val dz = abs(g4.second.toDouble() - Fmt.nc(firstD.z).toDouble())
            if (TaperWarning.AGAINST_TOOL in res.warnings) {
                assertTrue(dz >= c - 0.001, "$tag G41/G42 진입 Z ${s.ncG41G42}")
            } else {
                assertEquals(Fmt.nc(firstD.z + (if (s.entry.z > first.z) c else -c)), g4.second, "$tag G41/G42 진입 Z ${s.ncG41G42}")
            }
            checked++
        }
        val zs = listOf(0.0, 0.2, 0.5, 1.0, 2.0, 3.0)
        for (inner in listOf(false, true)) for (type in 1..5) for (reverse in listOf(false, true)) for (r in listOf(0.4, 0.8)) {
            val up = if (inner == (type == 5)) 1.0 else -1.0
            for ((r1, r2) in listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0)) for (lead in listOf(false, true)) for (zc in zs) {
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, 60 + up * 10, -5.0, zClear = zc, cornerR1 = r1, cornerR2 = r2)
                // 가공 시작 쪽 요소를 6 mm 깎기(단면은 X, 수평은 Z)
                val i = when {
                    !lead -> inp
                    reverse -> if (type == 2 || type == 4) inp.copy(end = 60 + up * 22) else inp.copy(end = -11.0)
                    else -> if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                }
                check(i.toString(), TaperCalc.calc(i), zc, r)
            }
        }
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))) {
            for (rc in listOf(0.0, 1.0)) for (zc in zs) {
                val i = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, zc, rc, rc, rc)
                check(i.toString(), TaperCalc.calcDouble(i), zc, 0.4)
            }
        }
        assertTrue(checked > 1500, "확인한 사례 $checked")
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
                "G40 G00 X52. Z-6.",
            ),
            s.ncG41G42,
        )
        // T2 는 날카로운 그대로
        assertPt(Pt(-5 - NoseR.taperDeltaZ(0.4, 45.0), 25.0), sharp(s.points[1]))
        // 시작점을 비우면 T1 에 모서리 R 이 있어 경사가 아니라 단면 바깥(공구 쪽 Z+1)에서 R 시작′ 으로 들어간다.
        // G41/G42 는 단면 선의 r 아래(X38.372)에 직각으로 들어가 선을 따라 R 시작까지
        val b0 = ok(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 1.0)))
        assertEquals(listOf("G00 X38.372 Z1.", "G01 Z0.", "G03 X40.351 Z-0.41 R1.4"), b0.ncG40.take(3))
        assertEquals(
            listOf("(R0.4 T3)", "G00 X38.372 Z1.", "G42 G01 Z0.", "G01 X39.172", "G03 X40.586 Z-0.293 R1."),
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
     * 처음·끝 점만은 약속이 달라 따로 본다 — 직접 보정은 인선을 시작·끝 좌표에 두므로 (G41/G42 보정 점) − (직접 보정 점)
     * = u·(팁 벡터·u) (u = 그 요소의 진행 방향). 표준 팁이 수평 끝 요소를 따라가면 G41/G42 쪽 인선이 r 더 간다.
     * 끝점을 비웠으면 끝 요소는 없고 보정 취소가 마지막 꺾임을 정한다: 볼록이면 경사를 그대로 늘여 나가 노즈가 경사 끝에 직각으로
     * 서고(직접 보정의 마지막 점은 그 경사 보정선 위), 아니면 끝 요소 방향으로 나가 제어기가 끝 요소와의 교점(= 직접 보정의 마지막 점)을 잡는다.
     * 함께: 보정 시작은 G01 이고 길이 ≥ r, 취소는 G00 직선, G41/G42 는 표대로.
     */
    @Test
    fun g41g42ProgramCompensatesToDirectProgram() {
        var checked = 0
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (type in 1..5) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 0.8)) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = 60 + up * 2 * 6 * tan(rad(deg))
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, Fmt.nc(x2).toDouble(), -6.0, cornerR1 = r1, cornerR2 = r2)
                // 시작점을 비운 것과, 가공 시작 쪽 요소를 6 mm 깎는 것(단면은 X, 수평은 Z)
                val lead = if (reverse) {
                    if (type == 2 || type == 4) inp.copy(end = Fmt.nc(x2 + up * 12).toDouble()) else inp.copy(end = -12.0)
                } else {
                    if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                }
                for (i in listOf(inp, lead)) {
                    val s = TaperCalc.calc(i).solution ?: continue
                    compareNc(s, i.toString())
                    checked++
                }
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            val inp = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
            for (i in listOf(inp, inp.copy(start = 4.0, end = -14.0))) {
                val s = TaperCalc.calcDouble(i).solution ?: continue
                compareNc(s, i.toString())
                checked++
            }
        }
        assertTrue(checked > 800, "확인한 사례 $checked")
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

    private fun compareNc(s: TaperSolution, tag: String) {
        val g4 = parseMoves(s.ncG41G42)
        val approach = g4.first()
        assertEquals(listOf(s.side.name, "G01"), approach.first, "$tag 보정 시작 블록")
        val noseR = Regex("""\(R([\d.]+) T(\d)\)""").find(s.ncG41G42[0])!!.groupValues
        val rr = noseR[1].toDouble()
        assertEquals(s.tip, noseR[2].toInt(), tag)
        val app = approach.second
        assertTrue((app.end - app.start).length >= rr - 1e-9, "$tag 진입 이동 < r")
        assertEquals(listOf("G40", "G00"), g4.last().first, "$tag 보정 취소 블록")
        assertTrue((g4.last().second.end - g4.last().second.start).length >= rr - 1e-9, "$tag 취소 이동 < r")
        assertTrue(g4.drop(1).dropLast(1).all { "G01" in it.first || "G02" in it.first || "G03" in it.first }, tag)

        // 글의 형상 줄 = 도면(시작·끝점을 비웠으면 그 요소는 빼고). 띄워 들어가면 앞에 시작 요소 선을 따르는 G01 이 한 줄 더 있다
        val exact = s.drawing.segs.drop(if (s.fromStart) 0 else 1).dropLast(if (s.toEnd) 0 else 1)
        val text = g4.drop(1).dropLast(1).map { it.second }
        val lead = if (text.size == splitArcs(exact).size + 1) text.first() else null
        val body4 = if (lead == null) text else text.drop(1)
        assertEquals(splitArcs(exact).size, body4.size, "$tag 형상 줄 수 ${s.ncG41G42}")
        for ((a, b) in splitArcs(exact).zip(body4)) assertTrue(near(a.end, b.end) && near(a.start, b.start), "$tag 형상 줄 $b ≠ 도면 $a")
        val line0 = s.drawing.segs.first()
        val u0 = (line0.end - line0.start).unit()
        if (lead != null) {
            assertTrue(lead is Line && near(lead.end, exact.first().start), "$tag 띄운 진입 뒤 선 ${s.ncG41G42}")
            assertTrue(abs((lead.end - lead.start).unit() cross u0) < 1e-3, "$tag 띄운 진입 뒤 선이 시작 요소와 한 직선이 아니다")
            assertTrue(abs((app.end - app.start).unit() dot u0) < 1e-3, "$tag 띄운 진입은 시작 요소 선에 직각")
        }

        // 보정은 반올림 전 도면으로 한다(글을 다시 읽은 원호는 접선이 0.001 만큼 틀어져 이음이 흔들린다).
        // 시작 이동은 글의 G00 점에서, 선을 따라 들어가는 이동은 그 선에 맞춘다
        val leadX = lead?.let { Line(line0.start + u0 * ((it.start - line0.start) dot u0), exact.first().start) }
        val target = leadX?.start ?: exact.first().start
        val t0 = startTangent(exact.first())
        val appX = if (leadX == null && abs((app.end - app.start).unit() cross t0) < 1e-3) {
            Line(target - t0 * (app.end - app.start).length, target)
        } else {
            Line(app.start, target)
        }
        // 끝 요소를 깎지 않을 때 볼록이 아니면 보정 취소가 끝 요소 방향으로 나간다 — 그 방향의 짧은 줄로 꺾임을 잡는다(그 줄의 끝 점은 뺀다)
        val back = s.drawing.segs.last()
        val endTurn = (exact.last().end - exact.last().start) cross (back.end - back.start)
        val convexEnd = !s.toEnd && exact.last() is Line && (endTurn > 0) == (s.side == Side.G42)
        val tail = if (s.toEnd || convexEnd) emptyList() else listOf(Line(back.start, back.start + (back.end - back.start).unit()))
        val comp = NoseComp.tipPath(Contour(listOf(appX) + listOfNotNull(leadX) + exact + tail), rr, s.side, s.tip).path.segs
        val mine = distinct(ends(splitArcs(comp)).drop(1)).dropLast(tail.size)

        // 직접 보정: 진입점 ~ 후퇴 G00 앞
        val g0 = parseMoves(s.ncG40)
        assertEquals(listOf("G00"), g0.last().first, tag)
        val direct = distinct(ends(g0.dropLast(1).map { it.second }))
        val entry = direct[0]
        val body = direct.drop(1)
        val uF = startTangent(exact.first())
        val last = exact.last()
        val uB = (last.end - last.start).unit()
        val tv = NoseComp.tipVector(s.tip, rr)
        val extra = if (lead != null) 1 else 0
        val lineStart = exact.first() is Line && abs(((leadX ?: appX).end - (leadX ?: appX).start).unit() cross uF) < 1e-9
        // 처음 점들: 시작 이동(·띄운 진입 뒤 선)이 첫 형상 요소와 한 직선이면 G41/G42 쪽 처음 점들은 직접 보정이 지나는 진입선 위에 있다.
        // 시작점을 넣고 단면에 직각으로 들어가면 (G41/G42) − (직접 보정, 인선이 시작 좌표) = u·(r + 팁 벡터·u).
        // 시작 요소를 따라 들어가 첫 꺾임점에서 바로 형상으로 가면(오목) 띄운 진입의 점만 진입선 위에 더 있다
        val (m, b) = when {
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
                val k0 = mine.indexOfLast { near(it, body[0]) }.takeIf { it in 0..extra } ?: extra
                for (q in mine.take(k0)) {
                    assertTrue(onLine(q, entry, body[0]) || near(q, body[0]), "$tag 처음 점 $q 이 진입선 $entry → ${body[0]} 위에 없다")
                }
                mine.drop(k0) to body
            }
        }
        assertEquals(m.size, b.size, "$tag 점 개수 G41/G42=$m G40=$b\n${s.ncG41G42}\n${s.ncG40}")
        for (k in m.indices) {
            // 끝 점: 직접 보정은 인선을 끝좌표에 둔다 → (G41/G42) − (직접 보정) = u·(팁 벡터·u). 표준 팁이 수평 끝 요소를 따라가면 r 더 간다.
            // 끝점을 비우고 볼록이면 G41/G42 쪽 노즈는 경사 끝에 직각 — 직접 보정의 마지막 경사 보정선 위, 그 끝보다 앞이다
            if (k == m.lastIndex && convexEnd) {
                val p0 = direct[direct.size - 2]
                assertTrue(onLine(m[k], p0, b[k]) && ((b[k] - m[k]) dot (b[k] - p0)) >= -1e-3, "$tag 끝 점 ${m[k]} ≠ 경사 보정선 $p0 → ${b[k]}")
                continue
            }
            val want = if (k == m.lastIndex && s.toEnd) b[k] + uB * (tv dot uB) else b[k]
            assertTrue(near(m[k], want), "$tag ${k}번 점: G41/G42 보정 ${m[k]} ≠ 직접 보정 $want\n${s.ncG41G42}\n${s.ncG40}")
        }
    }
}
