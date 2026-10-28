package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.GrooveWarning
import com.bmt.nosecalc.calc.NoseR
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 화면의 역방향(그림을 좌우로 뒤집은 형상)은 Z 를 뒤집어 정방향 계산기로 풀고 되돌린다.
 * 같은 실물 형상을 계산기의 reverse = true(형상 그대로, Z+ 로 깎음)로 따로 풀어 숫자·두 NC 가 같은지 맞춰 본다.
 */
class TaperSolveTest {
    /** 이 파일의 각도 숫자는 calc 와 같은 Z축 기준(화면 처음 값은 X축 — 두 기준은 AngleRefTest) */
    private fun form(
        kind: TaperKind, type: Int, reverse: Boolean, x: List<Double?>, z: List<Double?>,
        r: Double = 0.4, tip: Int? = null, angle: Double? = null,
        corners: List<Double> = List(x.size) { 0.0 }, start: Double? = null, end: Double? = null, flip: Boolean = false,
    ) = SlopeForm(kind, type, reverse, r, tip, x, z, angle, 1.0, corners, start, end, flip, angleRef = AngleRef.Z)

    /** 화면의 뒤집기 스위치와 같은 칸 맞바꿈(TaperState.toggleFlip): 점 순서 · 모서리 R 을 거꾸로, 시작점 ↔ 끝점 */
    private fun SlopeForm.flipped() = copy(x = x.reversed(), z = z.reversed(), corners = corners.reversed(), start = end, end = start, flip = !flip)

    private fun near(a: Pt, b: Pt, what: String) {
        assertTrue(abs(a.z - b.z) < 1e-9 && abs(a.x - b.x) < 1e-9, "$what: $a ≠ $b")
    }

    /** order[i] = 화면 T(i+1) 에 해당하는 계산기 점 번호 */
    private fun same(ui: TaperResult, ref: TaperResult, order: List<Int>, what: String) {
        assertEquals(ref.warnings.toSet(), ui.warnings.toSet(), "$what 경고")
        val u = ui.solution
        val f = ref.solution
        if (f == null) {
            assertNull(u, what)
            return
        }
        assertNotNull(u, what)
        assertEquals(f.ncG41G42, u.ncG41G42, "$what G41/G42 NC")
        assertEquals(f.ncG40, u.ncG40, "$what 직접 보정 NC")
        assertEquals(f.tip, u.tip, "$what 팁")
        assertEquals(f.side, u.side, "$what 보정 쪽")
        assertEquals(f.fromStart, u.fromStart, "$what 시작 요소")
        assertEquals(f.toEnd, u.toEnd, "$what 끝 요소")
        near(f.entry, u.entry, "$what 진입점")
        near(f.exit, u.exit, "$what 후퇴점")
        assertEquals(f.program.segs.size, u.program.segs.size)
        f.program.segs.zip(u.program.segs).forEachIndexed { k, (a, b) ->
            near(a.start, b.start, "$what 경로 $k 시작")
            near(a.end, b.end, "$what 경로 $k 끝")
        }
        order.forEachIndexed { i, j ->
            val a = f.points[j]
            val b = u.points[i]
            near(a.vertex, b.vertex, "$what T${i + 1} 꼭짓점")
            assertEquals(a.nodes.size, b.nodes.size, "$what T${i + 1} 접점 수")
            a.nodes.zip(b.nodes).forEach { (p, q) ->
                near(p.drawn, q.drawn, "$what T${i + 1} 도면")
                near(p.prog, q.prog, "$what T${i + 1} 보정")
            }
            assertEquals(a.corner?.cw, b.corner?.cw, "$what T${i + 1} 원호 방향")
            assertEquals(a.corner?.progR, b.corner?.progR, "$what T${i + 1} 보정 R")
        }
    }

    @Test
    fun ncMirror() {
        assertEquals("G00 X37.531 Z-1.", Mirror.nc("G00 X37.531 Z1."))
        assertEquals("G01 Z0.", Mirror.nc("G01 Z0."))
        assertEquals("G01 X50. Z5.234", Mirror.nc("G01 X50. Z-5.234"))
        assertEquals("G01 X-0.8 Z-0.5", Mirror.nc("G01 X-0.8 Z0.5"))
        // 원호 방향·보정 쪽·주석의 팁 번호도 뒤집힌다
        assertEquals("G02 X40.586 Z0.293 R1.", Mirror.nc("G03 X40.586 Z-0.293 R1."))
        assertEquals("G41 G01 X40. Z0.", Mirror.nc("G42 G01 X40. Z0."))
        assertEquals("G40 G00 X52.", Mirror.nc("G40 G00 X52."))
        assertEquals("(R0.4 T4)", Mirror.nc("(R0.4 T3)"))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0).map(Mirror::tip), listOf(2, 1, 4, 3, 7, 6, 5, 8, 9, 0))
    }

    /**
     * 화면 역방향 형상 3 = 실물로는 계산기 TYPE 5(낮은 왼쪽 → 높은 오른쪽), 화면 형상 5 = 계산기 TYPE 3.
     * 화면 T1(왼쪽) = 계산기 T2, 화면 시작점(T1 앞, 왼쪽) = 계산기 끝점 — 모서리 R 도 점을 바꿔 넣는다.
     * 뒤집기도: 화면에서 칸을 맞바꿔 뒤집은 것(같은 실제 점) = 계산기 reverse + flip 에 같은 규칙으로 넣은 것.
     * 화면 쪽은 Z 를 거울로 뒤집어 계산기 정방향 + flip 으로 풀고, 계산기 쪽은 형상 그대로 reverse + flip 이라 길이 다르다
     */
    @Test
    fun reverseSingleMatchesCalcReverse() {
        for (inner in listOf(false, true)) {
            val kind = if (inner) TaperKind.INNER else TaperKind.OUTER
            for ((uiType, calcType) in listOf(3 to 5, 5 to 3)) {
                // 화면 형상의 오르내림: T1 → T2 로 갈 때 X 가 커지면 +1 (계산기와 같은 규칙)
                val up = if (inner == (uiType == 5)) 1.0 else -1.0
                for (deg in 5..85 step 5) {
                    for (r in listOf(0.2, 0.4, 0.8, 1.2)) {
                        val x1 = 60.0
                        val x2 = x1 + up * 2 * 4 * tan(deg * PI / 180)
                        val z1 = -30.0
                        val z2 = -26.0
                        for ((cr, legs) in listOf(listOf(0.0, 0.0) to null, listOf(1.5, 0.6) to (-36.0 to -20.0))) for (flip in listOf(false, true)) {
                            val f = form(kind, uiType, true, listOf(x1, x2), listOf(z1, z2), r, corners = cr, start = legs?.first, end = legs?.second)
                                .let { if (flip) it.flipped() else it }
                            val ui = TaperSolve.solve(f)
                            val ref = TaperCalc.calc(
                                TaperInput(
                                    inner, calcType, true, r, null, f.x[1], f.z[1], f.x[0], f.z[0],
                                    cornerR1 = f.corners[1], cornerR2 = f.corners[0], start = f.end, end = f.start, flip = flip,
                                ),
                            )
                            same(ui, ref, listOf(1, 0), "${kind.label} 화면 형상 $uiType ${deg}° r$r R$cr 시작·끝 $legs 뒤집기 $flip")
                        }
                    }
                }
            }
        }
    }

    /** 2중 테이퍼는 형상이 그림뿐이라 화면 역방향 = 계산기 reverse 에 점 순서만 뒤집은 것(뒤집기도 같은 규칙) */
    @Test
    fun reverseDoubleMatchesCalcReverse() {
        for (kind in listOf(TaperKind.OUTER_DOUBLE, TaperKind.INNER_DOUBLE)) {
            for (type in 1..4) {
                for (r in listOf(0.2, 0.4, 0.8)) {
                    val x = (1..3).map { TaperExample.point(kind, type, true, "x$it").toDouble() }
                    val z = (1..3).map { TaperExample.point(kind, type, true, "z$it").toDouble() }
                    for (cr in listOf(listOf(0.0, 0.0, 0.0), listOf(0.5, 1.0, 0.3))) for (flip in listOf(false, true)) {
                        val f = form(kind, type, true, x, z, r, corners = cr, start = -40.0, end = -16.0).let { if (flip) it.flipped() else it }
                        val ui = TaperSolve.solve(f)
                        val (fx, fz, fc) = Triple(f.x.map { it!! }, f.z.map { it!! }, f.corners)
                        val ref = TaperCalc.calcDouble(
                            DoubleTaperInput(
                                kind.inner, true, r, null, fx[2], fz[2], fx[1], fz[1], fx[0], fz[0],
                                cornerR1 = fc[2], cornerR2 = fc[1], cornerR3 = fc[0], start = f.end, end = f.start, flip = flip,
                            ),
                        )
                        assertNotNull(ref.solution, "${kind.label} 형상 $type r$r R$cr 뒤집기 $flip ${ref.warnings}")
                        same(ui, ref, listOf(2, 1, 0), "${kind.label} 형상 $type r$r R$cr 뒤집기 $flip")
                    }
                }
            }
        }
    }

    /**
     * 뒤집기(같은 형상 · 같은 바이트, 반대 끝부터)는 꼭짓점 보정점이 같은 점이고 순서만 반대 — 화면의 네 조합(정/역방향 × 뒤집기) 모두.
     * 모든 종류 · 형상 · 방향의 예에서 모서리 R · 시작 · 끝점을 넣고 빼며: 뒤집은 결과의 T(i) = 안 뒤집은 결과의 T(n+1−i)(꼭짓점 · 도면 · 보정 접점,
     * 모서리 R 은 반대 방향 · 같은 보정 R), 도면 · 노즈 중심 · 직접 보정 경로가 거꾸로 같고, 팁 같음 · 보정 쪽 반대(= screenSide) · 각도 순서 반대
     */
    @Test
    fun flipKeepsCompPointsReversesOrder() {
        var n = 0
        for (kind in TaperKind.entries.filter { !it.groove }) for (type in 1..kind.types) for (rev in listOf(false, true)) {
            val x = (1..kind.points).map { TaperExample.point(kind, type, rev, "x$it").toDouble() }
            val z = (1..kind.points).map { TaperExample.point(kind, type, rev, "z$it").toDouble() }
            val (frontV, backV) = legsVertical(kind, type)
            val dir = if (rev) -1.0 else 1.0
            // 앞 요소는 T1 바깥(단면이면 T1 에서 경사와 반대로 6 mm — 외경 아래 · 내경 위), 뒤 요소는 마지막 T 바깥
            val up = if (kind.inner) -1.0 else 1.0
            val start = if (frontV) x.first() - up * 12 else z.first() + dir * 6
            val end = if (backV) x.last() + up * 12 else z.last() - dir * 6
            for (r in listOf(0.4, 0.8)) for (cr in listOf(0.0, 0.5)) for (legs in listOf(false, true)) {
                val corners = List(kind.points) { if (it == 0) cr else cr / 2 }
                val f = form(kind, type, rev, x, z, r, corners = corners, start = if (legs) start else null, end = if (legs) end else null)
                val what = "${kind.label} 형상 $type ${if (rev) "역" else "정"} r$r R$cr 시작·끝 $legs"
                val a = assertNotNull(TaperSolve.solve(f).solution, what)
                val res = TaperSolve.solve(f.flipped())
                val b = assertNotNull(res.solution, "$what 뒤집기 ${res.warnings}")
                assertEquals(a.tip, b.tip, what)
                assertTrue(a.side != b.side, what)
                assertEquals(screenSide(kind.inner, rev, false), a.side, what)
                assertEquals(screenSide(kind.inner, rev, true), b.side, what)
                assertEquals(a.anglesDeg.reversed(), b.anglesDeg, what)
                assertEquals(a.points.size, b.points.size)
                a.points.reversed().zip(b.points).forEachIndexed { i, (p, q) ->
                    near(p.vertex, q.vertex, "$what T${i + 1} 꼭짓점")
                    assertEquals(p.nodes.size, q.nodes.size, "$what T${i + 1} 접점 수")
                    p.nodes.reversed().zip(q.nodes).forEach { (u, v) ->
                        near(u.drawn, v.drawn, "$what T${i + 1} 도면")
                        near(u.prog, v.prog, "$what T${i + 1} 보정")
                    }
                    assertEquals(p.corner?.cw?.not(), q.corner?.cw, "$what T${i + 1} 원호 방향")
                    assertTrue(abs((p.corner?.progR ?: 0.0) - (q.corner?.progR ?: 0.0)) < 1e-9 && (p.corner?.progR == null) == (q.corner?.progR == null), "$what T${i + 1} 보정 R")
                }
                for ((ca, cb) in listOf(a.drawing to b.drawing, a.center to b.center, a.program to b.program)) {
                    assertEquals(ca.segs.size, cb.segs.size, what)
                    ca.segs.reversed().zip(cb.segs).forEach { (u, v) ->
                        near(u.start, v.end, "$what 경로")
                        near(u.end, v.start, "$what 경로")
                    }
                }
                n++
            }
        }
        assertEquals(288, n, "확인한 사례")
    }

    /**
     * 손 계산(calc-design.md '가공 방향 뒤집기', flippedOuterType1Numbers)과 화면: 외경 형상 1 X40 Z0 / X50 Z-5, 0.4R 의 예를 넣고
     * 스위치를 켜면 칸 T1 = X50 Z-5, 같은 바이트(팁 3번)로 G41. 직접 보정의 보정점(X50 Z-5.234 · X39.531 Z0)은 안 뒤집은 것과 같고 순서만 반대
     */
    @Test
    fun flippedOuterType1Numbers() {
        val s = TaperState(type = 1, open = true).example().apply { toggleFlip() }
        assertEquals(listOf("50", "-5", "40", "0"), listOf(s.point("x1"), s.point("z1"), s.point("x2"), s.point("z2")))
        val res = TaperSolve.solve(formOf(s))
        assertEquals(emptyList(), res.warnings)
        val sol = assertNotNull(res.solution)
        assertEquals(3, sol.tip)
        assertEquals(Side.G41, sol.side)
        // 진입점이 척 쪽이라 앞 끝면 바깥(Z1)에서 X52 를 맞추고 Z 로만 들어가는 접근 줄이 앞에 있다(calc 손 계산과 같다)
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G40 G01 X38. Z1."), sol.ncG41G42)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G00 Z1."), sol.ncG40)
        assertEquals("T1 위 → T2 아래 · G41", travelText(TaperKind.OUTER, 1, false, true))
        assertEquals("T1 아래 → T2 위 · G42", travelText(TaperKind.OUTER, 1, false, false))
        // 끝점 칸(이제 단면 X)을 비우면 자동 값은 깎기가 끝나는 T2 = X40 Z0
        near(Pt(0.0, 20.0), sol.cutEnd, "깎는 끝")
        assertEquals(false to true, legsVertical(TaperKind.OUTER, 1, flip = true))
    }

    /** 뒤집기 스위치 아래 한 줄의 보정 쪽(screenSide)은 계산 결과와 같다 — 모든 종류 · 형상 · 방향 · 뒤집기 */
    @Test
    fun travelTextMatchesSolution() {
        for (kind in TaperKind.entries.filter { !it.groove }) for (type in 1..kind.types) for (rev in listOf(false, true)) for (flip in listOf(false, true)) {
            val s = TaperState(kind, rev, type, open = true).example().apply { if (flip) toggleFlip() }
            assertEquals(flip, s.flip)
            val sol = assertNotNull(TaperSolve.solve(formOf(s)).solution, "${kind.label} 형상 $type 역 $rev 뒤집기 $flip")
            assertEquals(screenSide(kind.inner, rev, flip), sol.side)
            assertTrue(travelText(kind, type, rev, flip).endsWith(" · ${sol.side}"))
            assertTrue(travelText(kind, type, rev, flip).startsWith("T1 "))
        }
        assertEquals("T1 위 → T2 아래 · G41", travelText(TaperKind.INNER, 1, false, false))
        assertEquals("T1 아래 → T2 위 · G41", travelText(TaperKind.OUTER, 5, false, true))
        assertEquals("T1 왼쪽 → T3 오른쪽 · G41", travelText(TaperKind.OUTER_DOUBLE, 2, false, true))
        assertEquals("T1 오른쪽 → T3 왼쪽 · G42", travelText(TaperKind.OUTER_DOUBLE, 2, true, true))
        assertEquals("외경 → 면취 → 단면", typeName(TaperKind.OUTER, 1, flip = true))
        assertEquals("역테이퍼 (척 쪽으로 작아짐)", typeName(TaperKind.OUTER, 5, flip = true))
    }

    /**
     * 스위치(toggleFlip): 넣은 값이 같은 실제 점에 남게 칸을 맞바꾼다 — X·Z·모서리 R 은 T1 ↔ 마지막 T(2중은 T2 그대로), 시작점 ↔ 끝점,
     * 빈칸(자동)도 빈칸으로, 각도는 그대로, 마지막에 채운 자동 칸도 따라간다. 두 번 바꾸면 처음 그대로. (종류 · 형상 · 방향)마다 따로이고 저장된다
     */
    @Test
    fun toggleFlipSwapsFields() {
        val s = TaperState(type = 3, open = true).example().apply {
            setPoint("z2", "")
            setPoint("ang", "30")
            setPoint("r2", "0.5")
            setPoint("end", "-20")
        }
        assertEquals("ang", s.lastAuto)
        s.setPoint("z1", "")
        s.setPoint("z1", "0")
        assertEquals("z1", s.lastAuto)
        val before = listOf("x1", "z1", "r1", "x2", "z2", "r2", "ang", "start", "end").associateWith(s::point)
        assertFalse(s.flip)
        s.toggleFlip()
        assertTrue(s.flip)
        assertEquals(before["x2"], s.point("x1"))
        assertEquals(before["x1"], s.point("x2"))
        assertEquals("", s.point("z1"))
        assertEquals(before["z1"], s.point("z2"))
        assertEquals("0.5", s.point("r1"))
        assertEquals("", s.point("r2"))
        assertEquals("30", s.point("ang"))
        assertEquals("-20", s.point("start"))
        assertEquals("", s.point("end"))
        assertEquals("z2", s.lastAuto)
        // 뒤집은 칸에서도 같은 실제 형상: 빈 Z1 을 각도로 풀면 원래 Z2 자리
        val sol = assertNotNull(TaperSolve.solve(formOf(s)).solution)
        val plain = assertNotNull(TaperSolve.solve(formOf(TaperState(type = 3, open = true).example().apply { setPoint("z2", ""); setPoint("ang", "30") })).solution)
        near(plain.points[1].vertex, sol.points[0].vertex, "자동 Z")
        // 다른 형상 · 방향은 따로
        s.type = 1
        assertFalse(s.flip)
        s.reverse = true
        s.type = 3
        assertFalse(s.flip)
        s.reverse = false
        assertTrue(s.flipped(3) && !s.flipped(1))
        // 저장 · 되살리기
        val back = with(TaperState.Saver) { restore(androidx.compose.runtime.saveable.SaverScope { true }.save(s)!!)!! }
        assertTrue(back.flip)
        assertEquals("-20", back.point("start"))
        // 두 번 바꾸면 처음 그대로
        s.toggleFlip()
        assertFalse(s.flip)
        assertEquals(before, listOf("x1", "z1", "r1", "x2", "z2", "r2", "ang", "start", "end").associateWith(s::point))
        assertEquals("z1", s.lastAuto)
        // 2중 테이퍼: T1 ⇄ T3, T2 그대로
        val d = TaperState(TaperKind.OUTER_DOUBLE, type = 1, open = true).example().apply { setPoint("r2", "1"); setPoint("r3", "0.3") }
        val dx = (1..3).map { d.point("x$it") }
        d.toggleFlip()
        assertEquals(dx.reversed(), (1..3).map { d.point("x$it") })
        assertEquals(listOf("0.3", "1", ""), (1..3).map { d.point("r$it") })
        // 이 열쇠가 없던 옛 저장 값(뒤집기 전)은 꺼짐
        val old = with(TaperState.Saver) {
            restore(listOf("OUTER", "false", "1", "true", "p/OUTER/1/f/x1", "40", "p/OUTER/1/f/x2", "50", "p/OUTER/1/f/z2", "-5"))!!
        }
        assertFalse(old.flip)
        assertEquals("50", old.point("x2"))
    }

    /**
     * 화면 역방향 외경 형상 1(왼쪽 단면 → 면취 → 오른쪽 외경)을 닫힌 식으로: 정방향 TYPE 1 의 T1 X −C, T2 Z −A 를 Z 로 뒤집은 것.
     * A = r(1 − tan(θ/2)), C(직경) = 2r(1 − tan((90° − θ)/2)) — calc-design.md 보정점 표.
     */
    @Test
    fun reverseOuterType1ClosedForm() {
        for (deg in 5..85 step 5) {
            for (r in listOf(0.2, 0.4, 0.8, 1.2)) {
                val dz = 4.0
                val x1 = 40.0
                val x2 = x1 + 2 * dz * tan(deg * PI / 180)
                val res = TaperSolve.solve(form(TaperKind.OUTER, 1, true, listOf(x1, x2), listOf(-30.0, -30.0 + dz), r))
                val s = assertNotNull(res.solution, "$deg° r$r ${res.warnings}")
                assertEquals(4, s.tip)
                assertEquals(Side.G41, s.side)
                val t1 = s.points[0].nodes.single()
                val t2 = s.points[1].nodes.single()
                assertTrue(abs(t1.dXDia + NoseR.taperDeltaXDia(r, deg.toDouble())) < 1e-9, "T1 ΔX $deg° r$r")
                assertTrue(abs(t1.dZ) < 1e-9, "T1 ΔZ $deg° r$r")
                assertTrue(abs(t2.dXDia) < 1e-9, "T2 ΔX $deg° r$r")
                assertTrue(abs(t2.dZ - NoseR.taperDeltaZ(r, deg.toDouble())) < 1e-9, "T2 ΔZ $deg° r$r")
                // 진입은 T1′ 왼쪽(Z−)
                assertTrue(s.entry.z < t1.prog.z)
                assertEquals("(R$r T4)", s.ncG41G42.first())
                assertTrue(s.ncG41G42[2].startsWith("G41 G01 "), "${s.ncG41G42}")
            }
        }
    }

    /** NC 처음 G00 줄들(접근 줄 + 진입)의 마지막 점 "X… Z…"(모달 — 빠진 축은 앞 줄 값). 주석 줄은 뺀다 */
    private fun entryOf(nc: List<String>): String {
        var x = ""
        var z = ""
        for (line in nc.filter { !it.startsWith("(") }) {
            if (!line.startsWith("G00")) break
            for (w in line.split(" ")) when (w[0]) {
                'X' -> x = w
                'Z' -> z = w
            }
        }
        return "$x $z"
    }

    /** 단면 시작점은 X 라 뒤집지 않는다: 화면 역방향 형상 1 의 시작점 X30 = 도면 첫 요소의 바깥 끝 X30, 그 단면 Z 그대로 */
    @Test
    fun reverseVerticalLegKeepsX() {
        val s = TaperSolve.solve(form(TaperKind.OUTER, 1, true, listOf(40.0, 50.0), listOf(-30.0, -25.0), start = 30.0)).solution!!
        assertEquals(30.0, s.drawing.segs.first().start.xDia, 1e-12)
        assertEquals(-30.0, s.drawing.segs.first().start.z, 1e-12)
        assertTrue(s.fromStart)
        assertEquals("G00 X30. Z-31.", s.ncG40.first())
    }

    @Test
    fun reverseUsesMirroredTip() {
        val x = listOf(40.0, 50.0)
        val z = listOf(-30.0, -25.0)
        val auto = TaperSolve.solve(form(TaperKind.OUTER, 1, true, x, z)).solution!!
        val four = TaperSolve.solve(form(TaperKind.OUTER, 1, true, x, z, tip = 4)).solution!!
        assertEquals(auto.ncG40, four.ncG40)
        assertEquals(auto.ncG41G42, four.ncG41G42)
        val three = TaperSolve.solve(form(TaperKind.OUTER, 1, true, x, z, tip = 3)).solution!!
        assertEquals(3, three.tip)
        assertTrue(three.ncG40 != auto.ncG40)
        assertEquals("(R0.4 T3)", three.ncG41G42.first())
    }

    @Test
    fun forwardIsCalcAsIs() {
        val ui = TaperSolve.solve(form(TaperKind.OUTER, 1, false, listOf(40.0, 50.0), listOf(0.0, -5.0)))
        assertEquals(listOf("G00 X37.531 Z1.", "G01 X39.531 Z0.", "G01 X50. Z-5.234", "G00 X52."), ui.solution!!.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X38. Z1.", "G42 G01 X40. Z0.", "G01 X50. Z-5.", "G40 G01 X52. Z-6."),
            ui.solution!!.ncG41G42,
        )
        // 모서리 R·끝점도 그대로 넘긴다
        val r = TaperSolve.solve(form(TaperKind.OUTER, 1, false, listOf(40.0, 50.0), listOf(0.0, -5.0), corners = listOf(1.0, 0.0), end = -20.0))
        assertEquals(TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 1.0, end = -20.0)), r)
    }

    @Test
    fun doubleTypeMustMatchPicture() {
        // 두 번 커지는 점을 형상 2(가운데가 큼)로 넣으면 멈춘다
        val x = listOf(40.0, 46.0, 52.0)
        val z = listOf(0.0, -6.0, -9.0)
        assertNotNull(TaperSolve.solve(form(TaperKind.OUTER_DOUBLE, 1, false, x, z)).solution)
        val bad = TaperSolve.solve(form(TaperKind.OUTER_DOUBLE, 2, false, x, z))
        assertNull(bad.solution)
        assertEquals(listOf(TaperWarning.SHAPE_MISMATCH), bad.warnings)
        // 내경은 반대: 두 번 작아지는 것이 형상 1
        assertNotNull(TaperSolve.solve(form(TaperKind.INNER_DOUBLE, 1, false, x.reversed(), z)).solution)
        assertNull(TaperSolve.solve(form(TaperKind.INNER_DOUBLE, 1, false, x, z)).solution)
    }

    /** 예(2단계 처음 값)는 모든 종류·형상·방향에서 바로 답이 나와야 한다 — 스크린샷 · 골든이 쓴다 */
    @Test
    fun examplesSolve() {
        for (kind in TaperKind.entries.filter { !it.groove }) {
            for (type in 1..kind.types) {
                for (rev in listOf(false, true)) {
                    val x = (1..kind.points).map { TaperExample.point(kind, type, rev, "x$it").toDouble() }
                    val z = (1..kind.points).map { TaperExample.point(kind, type, rev, "z$it").toDouble() }
                    val res = TaperSolve.solve(form(kind, type, rev, x, z))
                    assertNotNull(res.solution, "${kind.label} 형상 $type ${if (rev) "역" else "정"}: ${res.warnings}")
                    if (rev) assertTrue(z[0] < z[1], "역방향 T1 은 왼쪽")
                }
            }
        }
        val e = TaperExample.groove
        val g = GrooveCalc.calc(
            GrooveInput(
                e.getValue("zStart").toDouble(), e.getValue("width").toDouble(), e.getValue("od").toDouble(), e.getValue("bottom").toDouble(),
                TaperDefaults.shared("insert").toDouble(), TaperDefaults.shared("overlap").toDouble(),
            ),
        )
        assertEquals(listOf(-13.0, -15.5, -18.0), g.solution!!.steps.map { it.z })
    }

    /**
     * 처음 값(사용자 요청 2026-10-28 "0 이 적힌 좌표들을 다 빈칸으로"): 도면 치수(X · Z)와 각도 · 모서리 R · 시작 · 끝점, 홈 치수 · Z 여유 · X 여유가 모두 빈칸 →
     * 계산 전이라 빨간 멈춤이 아닌 파란 안내(MISSING_INPUT). 공구 값(노즈 R · 인서트 폭 · 겹침)은 그대로
     */
    @Test
    fun defaultsStartBlank() {
        for (kind in TaperKind.entries.filter { !it.groove }) {
            for (type in 1..kind.types) {
                for (rev in listOf(false, true)) {
                    val s = TaperState(kind, rev, type, open = true)
                    val n = kind.points
                    for (i in 1..n) assertEquals("" to "", s.point("x$i") to s.point("z$i"))
                    for (name in listOf("ang", "r1", "start", "end")) assertEquals("", s.point(name))
                    assertEquals("", s.shared("zClear"))
                    val r = TaperSolve.solve(formOf(s))
                    assertNull(r.solution)
                    assertEquals(listOf(TaperWarning.MISSING_INPUT), r.warnings, "${kind.label} 형상 $type 역 $rev")
                }
            }
        }
        val g = TaperState(TaperKind.GROOVE, type = 1, open = true)
        for (name in listOf("zStart", "width", "od", "bottom", "xClear", "zClear")) assertEquals("", g.shared(name), name)
        assertEquals(listOf("0.4", "3", "0.5"), listOf("noseR", "insert", "overlap").map(g::shared))
        val r = GrooveCalc.calc(grooveOf(g))
        assertNull(r.solution)
        assertEquals(listOf(GrooveWarning.MISSING_INPUT), r.warnings)
    }

    /**
     * 처음 화면에서 키패드로 치수를 넣는 흐름(정방향 형상 1, 홈 왼쪽 인선): 빈칸에서 시작해 칸 순서대로 넣는 동안은 파란 안내(SlopeScreen 의 waiting)이거나 답이고
     * 빨간 멈춤은 없다 — 테이퍼는 마지막 치수가 들어가는 순간 답이 나온다(각도는 빈칸 = 자동). Z0 인 점도 0 을 넣는다.
     * 키패드는 누를 때마다 값을 넣으므로 한 자리씩(4 → 40, − 만은 넣지 않는다)
     */
    @Test
    fun freshStartSolvesAsYouType() {
        fun typed(v: String) = (1..v.length).map { v.take(it) }.filter { it != "-" }
        for (kind in TaperKind.entries.filter { !it.groove }) {
            val s = TaperState(kind, type = 1, open = true)
            val names = (1..kind.points).flatMap { listOf("x$it", "z$it") }
            for (name in names) {
                for (t in typed(TaperExample.point(kind, 1, false, name))) {
                    s.setPoint(name, t)
                    val f = formOf(s)
                    val r = TaperSolve.solve(f)
                    // SlopeScreen 과 같은 판정: 풀리지 않았으면 값 부족(MISSING_INPUT) · 0 치수(waiting)만 파란 안내
                    val waiting = r.solution == null && (TaperWarning.MISSING_INPUT in r.warnings || f.waiting)
                    val done = name == names.last() && t == TaperExample.point(kind, 1, false, name)
                    assertEquals(!done, waiting, "${kind.label} $name=$t ${r.warnings}")
                    if (done) assertNotNull(r.solution, kind.label)
                }
            }
        }
        val g = TaperState(TaperKind.GROOVE, type = 1, open = true)
        for ((name, v) in TaperExample.groove) {
            for (t in typed(v)) {
                g.setShared(name, t)
                val input = grooveOf(g)
                val r = GrooveCalc.calc(input)
                assertTrue(r.solution != null || GrooveWarning.MISSING_INPUT in r.warnings || input.waiting, "홈 $name=$t ${r.warnings}")
            }
        }
        assertNotNull(GrooveCalc.calc(grooveOf(g)).solution)
    }

    /** GrooveScreen 의 입력 → GrooveInput 그대로(겹침 빈칸 = 0.5, X 여유 빈칸 = 0) */
    private fun grooveOf(g: TaperState) = GrooveInput(
        g.shared("zStart").toDoubleOrNull(), g.shared("width").toDoubleOrNull(), g.shared("od").toDoubleOrNull(), g.shared("bottom").toDoubleOrNull(),
        g.shared("insert").toDoubleOrNull(), g.shared("overlap").toDoubleOrNull() ?: 0.5, xClear = g.shared("xClear").toDoubleOrNull() ?: 0.0,
    )

    /** SlopeScreen 의 입력 → SlopeForm 그대로. 각도 칸은 Z축 기준 숫자(toggleFlipSwapsFields 의 30°) */
    private fun formOf(s: TaperState): SlopeForm {
        val n = s.kind.points
        return SlopeForm(
            s.kind, s.type!!, s.reverse, s.shared("noseR").toDouble(), s.tip,
            (1..n).map { s.point("x$it").toDoubleOrNull() }, (1..n).map { s.point("z$it").toDoubleOrNull() }, s.point("ang").toDoubleOrNull(),
            s.shared("zClear").toDoubleOrNull() ?: 0.0, (1..n).map { s.point("r$it").toDoubleOrNull() ?: 0.0 },
            s.point("start").toDoubleOrNull(), s.point("end").toDoubleOrNull(), s.flip, angleRef = AngleRef.Z,
        )
    }

    /**
     * 현장: "Z 여유를 바꿔도 G00 이 따라오지 않는다". 화면의 모든 종류 · 형상 · 방향(모서리 R · 시작점 유무)에서 Z 여유 0 ~ 3 을 바꾸면
     * 두 NC 의 G00 진입과 화면의 진입점(직접 보정 G00)이 첫 깎는 점에서 Z 로 꼭 max(Z 여유, 노즈 R) — 정방향은 +Z(척 반대쪽),
     * 역방향은 −Z — 떨어진다. 첫 깎는 점 = 직접 보정은 첫 보정점(시작점을 넣었으면 시작점′), G41/G42 는 첫 도면점.
     * 뒤집기(T1 ⇄ T2)도 — 진입 쪽이 반대이고, G41/G42 만 calc 의 예외(뒤집어 수평 시작 요소를 따라 오목 꺾임으로 들어가면 직접 보정 진입점과
     * 같은 Z 까지 물림)가 있어 c 이상 떨어진 것만 본다. 뒤집어 넣은 수평 시작점부터 깎으면 Z 로 c 는 시작점 너머(척 · 구멍 바닥)라
     * 그 Z 에서 공구 쪽으로 X 로 c(calc "가공 방향 뒤집기" ①, 검증 2026-10-27).
     * 홈은 단마다 G00 X = 외경 + 2 × X 여유
     */
    @Test
    fun entryFollowsZClearance() {
        val clear = listOf(0.0, 0.2, 0.5, 1.0, 2.0, 3.0)
        var n = 0
        for (kind in TaperKind.entries.filter { !it.groove }) for (type in 1..kind.types) for (rev in listOf(false, true)) for (flip in listOf(false, true)) {
            // 칸 값은 화면 스위치처럼 맞바꾼 것(같은 실제 점)
            val st = TaperState(kind, rev, type, open = true).example().apply { if (flip) toggleFlip() }
            val x = (1..kind.points).map { st.point("x$it").toDouble() }
            val z = (1..kind.points).map { st.point("z$it").toDouble() }
            val (frontV, _) = legsVertical(kind, type, flip)
            // 시작 요소 6 mm: 단면은 그 요소가 T1 에서 뻗는 쪽으로 X(뒤집으면 반대 끝 단면이라 반대 쪽), 수평은 T1 바깥쪽 Z
            val dx = if (kind.inner != flip) 12.0 else -12.0
            val start = if (frontV) x[0] + dx else z[0] + (if (rev != flip) -6.0 else 6.0)
            for (r in listOf(0.4, 0.8)) for (r1 in listOf(0.0, 1.0)) for (lead in listOf(false, true)) for (zc in clear) {
                val corners = List(kind.points) { if (it == 0) r1 else 0.0 }
                val f = SlopeForm(kind, type, rev, r, null, x, z, null, zc, corners, if (lead) start else null, flip = flip, angleRef = AngleRef.Z)
                val what = "${kind.label} 형상 $type ${if (rev) "역" else "정"} 뒤집기 $flip r$r R$r1 시작점 $lead Z 여유 $zc"
                val res = TaperSolve.solve(f)
                val s = assertNotNull(res.solution, what)
                val c = maxOf(zc, r)
                val dir = if (rev) -1.0 else 1.0
                val skip = if (s.fromStart) 0 else 1
                // 진입 G00 = 처음 G00 줄들의 마지막. 뒤집어 척 쪽에서 시작하면(역방향 아님) 그 앞에 접근 줄 — 거울로 푸는 역방향은 없다
                assertEquals("X${Fmt.nc(s.entry.xDia)} Z${Fmt.nc(s.entry.z)}", entryOf(s.ncG40), "$what 화면 진입점 = G00")
                assertEquals(s.approach.size, s.ncG40.indexOfFirst { !it.startsWith("G00") } - 1, "$what ${s.ncG40}")
                if (rev || !flip) assertEquals(emptyList(), s.approach, "$what ${s.ncG40}")
                for (nc in listOf(s.ncG40, s.ncG41G42.drop(1))) {
                    val k = nc.indexOfFirst { !it.startsWith("G00") }
                    // 접근 줄은 앞 끝면 바깥에서 시작해 한 축씩
                    if (k > 1) assertTrue(nc.subList(1, k).all { it.split(" ").size == 2 }, "$what 접근 줄 $nc")
                }
                val g00 = entryOf(s.ncG41G42)
                assertTrue(s.ncG41G42.drop(1).first().startsWith("G00 "), "$what ${s.ncG41G42}")
                if (flip && s.fromStart && !frontV) {
                    // 넣은 수평 시작점(척 · 구멍 바닥 쪽): 그 Z 에서 공구 쪽으로 X 로 c — 화면 역방향(거울)이어도 X 는 그대로
                    val p0 = s.program.segs[0].start
                    assertEquals(p0.z, s.entry.z, 1e-9, "$what 직접 보정 ${s.ncG40}")
                    assertEquals(c, abs(s.entry.x - p0.x), 1e-9, "$what 직접 보정 ${s.ncG40}")
                    assertTrue(g00.endsWith(" Z${Fmt.nc(s.drawing.segs[0].start.z)}"), "$what G41/G42 ${s.ncG41G42}")
                } else if (flip) {
                    // 뒤집으면 쪽은 시작 요소가 정한다: 단면은 공구 쪽(척 반대쪽), 수평 · 경사 연장선은 진행 반대쪽 — 거리만 본다
                    assertEquals(c, abs(s.entry.z - s.program.segs[skip].start.z), 1e-9, "$what 직접 보정 ${s.ncG40}")
                    val gz = g00.substringAfter(" Z").toDouble()
                    assertTrue(abs(gz - s.drawing.segs[skip].start.z) >= c - 1e-3, "$what G41/G42 ${s.ncG41G42}")
                } else {
                    assertEquals(s.program.segs[skip].start.z + dir * c, s.entry.z, 1e-9, "$what 직접 보정 ${s.ncG40}")
                    // 바이트 반대(역테이퍼 · AGAINST_TOOL)의 볼록 첫 꺾임: 연장선 진입의 보정 시작 블록이 도면을 파고들면 경사 연장선 위 lead 에
                    // 직각으로 들어간다(2026-10-28, calc-design '진입점과 NC') — 그 G00 은 Z 여유가 아니라 첫 깎는 요소 선에서 c 이상 띄운 점
                    val first = s.drawing.segs[skip]
                    val start4 = s.ncG41G42.drop(1).first { !it.startsWith("G00") }
                    // 보정 시작 줄의 끝(빠진 축은 G00 값 그대로)이 첫 깎는 점인가
                    val w4 = start4.split(" ")
                    val x4 = w4.firstOrNull { it.startsWith("X") } ?: g00.substringBefore(" ")
                    val z4 = w4.firstOrNull { it.startsWith("Z") } ?: g00.substringAfter(" ")
                    val onFirst = "$x4 $z4" == "X${Fmt.nc(first.start.xDia)} Z${Fmt.nc(first.start.z)}"
                    if (TaperWarning.AGAINST_TOOL in res.warnings && !onFirst) {
                        val gx = g00.substringAfter("X").substringBefore(" ").toDouble()
                        val gz = g00.substringAfter(" Z").toDouble()
                        val u = first.end - first.start
                        val off = abs(u cross (Pt.ofDia(gz, gx) - first.start)) / u.length
                        assertTrue(off >= c - 1e-3, "$what G41/G42 띄운 진입 ${s.ncG41G42}")
                    } else {
                        assertTrue(g00.endsWith(" Z${Fmt.nc(s.drawing.segs[skip].start.z + dir * c)}"), "$what G41/G42 ${s.ncG41G42}")
                    }
                }
                n++
            }
        }
        assertTrue(n > 1500, "확인한 사례 $n")
        val e = TaperExample.groove
        for (xc in clear) for (ref in GrooveRef.entries) {
            val g = GrooveCalc.calc(
                GrooveInput(
                    e.getValue("zStart").toDouble(), e.getValue("width").toDouble(), e.getValue("od").toDouble(), e.getValue("bottom").toDouble(),
                    3.0, 0.5, ref, xc,
                ),
            ).solution!!
            val rapids = g.nc.filter { it.startsWith("G00") }
            assertTrue(rapids.isNotEmpty() && rapids.all { "X${Fmt.nc(40 + 2 * xc)}" in it || "X" !in it }, "홈 X 여유 $xc ${g.nc}")
            assertEquals(40 + 2 * xc, g.retractXDia, 1e-12)
        }
    }

    @Test
    fun stateSurvivesSaver() {
        val s = TaperState(TaperKind.INNER, reverse = true, type = 4, open = true)
        s.setPoint("x1", "41.5")
        s.setPoint("r2", "1.5")
        s.setPoint("end", "-20")
        s.tip = 2
        s.setShared("noseR", "0.8")
        s.editing = "z2"
        val back = with(TaperState.Saver) {
            restore(androidx.compose.runtime.saveable.SaverScope { true }.save(s)!!)!!
        }
        assertEquals(TaperKind.INNER, back.kind)
        assertTrue(back.reverse && back.open)
        assertEquals(4, back.type)
        assertEquals("41.5", back.point("x1"))
        assertEquals("1.5", back.point("r2"))
        assertEquals("-20", back.point("end"))
        assertEquals(2, back.tip)
        assertEquals("0.8", back.shared("noseR"))
        assertEquals("z2", back.editing)
        back.tip = null
        assertNull(back.tip)
    }
}
