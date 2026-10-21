package com.bmt.nosecalc.screens.taper

import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 화면의 역방향(그림을 좌우로 뒤집은 형상)은 Z 를 뒤집어 정방향 계산기로 풀고 되돌린다.
 * 같은 실물 형상을 계산기의 reverse = true(형상 그대로, Z+ 로 깎음)로 따로 풀어 숫자·두 NC 가 같은지 맞춰 본다.
 */
class TaperSolveTest {
    private fun form(
        kind: TaperKind, type: Int, reverse: Boolean, x: List<Double?>, z: List<Double?>,
        r: Double = 0.4, tip: Int? = null, angle: Double? = null,
        corners: List<Double> = List(x.size) { 0.0 }, start: Double? = null, end: Double? = null,
    ) = SlopeForm(kind, type, reverse, r, tip, x, z, angle, 1.0, corners, start, end)

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
     * 화면 T1(왼쪽) = 계산기 T2, 화면 시작점(T1 앞, 왼쪽) = 계산기 끝점 — 모서리 R 도 점을 바꿔 넣는다
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
                        for ((cr, legs) in listOf(listOf(0.0, 0.0) to null, listOf(1.5, 0.6) to (-36.0 to -20.0))) {
                            val ui = TaperSolve.solve(
                                form(kind, uiType, true, listOf(x1, x2), listOf(z1, z2), r, corners = cr, start = legs?.first, end = legs?.second),
                            )
                            val ref = TaperCalc.calc(
                                TaperInput(
                                    inner, calcType, true, r, null, x2, z2, x1, z1,
                                    cornerR1 = cr[1], cornerR2 = cr[0], start = legs?.second, end = legs?.first,
                                ),
                            )
                            same(ui, ref, listOf(1, 0), "${kind.label} 화면 형상 $uiType ${deg}° r$r R$cr 시작·끝 $legs")
                        }
                    }
                }
            }
        }
    }

    /** 2중 테이퍼는 형상이 그림뿐이라 화면 역방향 = 계산기 reverse 에 점 순서만 뒤집은 것 */
    @Test
    fun reverseDoubleMatchesCalcReverse() {
        for (kind in listOf(TaperKind.OUTER_DOUBLE, TaperKind.INNER_DOUBLE)) {
            for (type in 1..4) {
                for (r in listOf(0.2, 0.4, 0.8)) {
                    val x = (1..3).map { TaperExample.point(kind, type, true, "x$it").toDouble() }
                    val z = (1..3).map { TaperExample.point(kind, type, true, "z$it").toDouble() }
                    for (cr in listOf(listOf(0.0, 0.0, 0.0), listOf(0.5, 1.0, 0.3))) {
                        val ui = TaperSolve.solve(form(kind, type, true, x, z, r, corners = cr, start = -40.0, end = -16.0))
                        val ref = TaperCalc.calcDouble(
                            DoubleTaperInput(
                                kind.inner, true, r, null, x[2], z[2], x[1], z[1], x[0], z[0],
                                cornerR1 = cr[2], cornerR2 = cr[1], cornerR3 = cr[0], start = -16.0, end = -40.0,
                            ),
                        )
                        assertNotNull(ref.solution, "${kind.label} 형상 $type r$r R$cr ${ref.warnings}")
                        same(ui, ref, listOf(2, 1, 0), "${kind.label} 형상 $type r$r R$cr")
                    }
                }
            }
        }
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
            listOf("(R0.4 T3)", "G00 X38. Z1.", "G42 G01 X40. Z0.", "G01 X50. Z-5.", "G40 G00 X52. Z-6."),
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

    /** 처음 값: 도면 치수는 0, 각도 · 모서리 R · 시작 · 끝점은 빈칸 → 계산 전이라 멈춤이 아닌 안내(waiting) */
    @Test
    fun defaultsWaitForDimensions() {
        for (kind in TaperKind.entries.filter { !it.groove }) {
            for (type in 1..kind.types) {
                for (rev in listOf(false, true)) {
                    val s = TaperState(kind, rev, type, open = true)
                    val n = kind.points
                    for (i in 1..n) assertEquals("0" to "0", s.point("x$i") to s.point("z$i"))
                    for (name in listOf("ang", "r1", "start", "end")) assertEquals("", s.point(name))
                    val f = form(kind, type, rev, List(n) { 0.0 }, List(n) { 0.0 })
                    assertNull(TaperSolve.solve(f).solution)
                    assertTrue(f.waiting)
                }
            }
        }
        val g = TaperState(TaperKind.GROOVE, type = 1, open = true)
        val input = GrooveInput(
            g.shared("zStart").toDouble(), g.shared("width").toDouble(), g.shared("od").toDouble(), g.shared("bottom").toDouble(),
            g.shared("insert").toDouble(), g.shared("overlap").toDouble(), xClear = g.shared("xClear").toDouble(),
        )
        assertNull(GrooveCalc.calc(input).solution)
        assertTrue(input.waiting)
        assertEquals("0", g.shared("xClear"))
        assertEquals("0", g.shared("zClear"))
    }

    /**
     * 처음 화면에서 키패드로 치수를 넣는 흐름(정방향 형상 1, 홈 왼쪽 인선): 0 에서 시작해 칸 순서대로 넣는 동안은 파란 안내(waiting)이거나 답이고
     * 빨간 멈춤은 없다 — 테이퍼는 마지막 치수가 들어가는 순간 답이 나온다. 키패드는 누를 때마다 값을 넣으므로 한 자리씩(4 → 40, − 만은 넣지 않는다)
     */
    @Test
    fun freshStartSolvesAsYouType() {
        fun typed(v: String) = (1..v.length).map { v.take(it) }.filter { it != "-" }
        for (kind in TaperKind.entries.filter { !it.groove }) {
            val s = TaperState(kind, type = 1, open = true)
            val names = (1..kind.points).flatMap { listOf("x$it", "z$it") }.filter { TaperExample.point(kind, 1, false, it) != "0" }
            for (name in names) {
                for (t in typed(TaperExample.point(kind, 1, false, name))) {
                    s.setPoint(name, t)
                    val f = formOf(s)
                    val done = name == names.last() && t == TaperExample.point(kind, 1, false, name)
                    assertEquals(!done, f.waiting, "${kind.label} $name=$t")
                    if (done) assertNotNull(TaperSolve.solve(f).solution, kind.label)
                }
            }
        }
        val g = TaperState(TaperKind.GROOVE, type = 1, open = true)
        for ((name, v) in TaperExample.groove) {
            for (t in typed(v)) {
                g.setShared(name, t)
                val input = GrooveInput(
                    g.shared("zStart").toDouble(), g.shared("width").toDouble(), g.shared("od").toDouble(), g.shared("bottom").toDouble(),
                    g.shared("insert").toDouble(), g.shared("overlap").toDouble(), xClear = g.shared("xClear").toDouble(),
                )
                val r = GrooveCalc.calc(input)
                assertTrue(r.solution != null || input.waiting, "홈 $name=$t ${r.warnings}")
            }
        }
    }

    /** SlopeScreen 의 입력 → SlopeForm 그대로 */
    private fun formOf(s: TaperState): SlopeForm {
        val n = s.kind.points
        return SlopeForm(
            s.kind, s.type!!, s.reverse, s.shared("noseR").toDouble(), s.tip,
            (1..n).map { s.point("x$it").toDoubleOrNull() }, (1..n).map { s.point("z$it").toDoubleOrNull() }, s.point("ang").toDoubleOrNull(),
            s.shared("zClear").toDoubleOrNull() ?: 0.0, (1..n).map { s.point("r$it").toDoubleOrNull() ?: 0.0 },
            s.point("start").toDoubleOrNull(), s.point("end").toDoubleOrNull(),
        )
    }

    /**
     * 현장: "Z 여유를 바꿔도 G00 이 따라오지 않는다". 화면의 모든 종류 · 형상 · 방향(모서리 R · 시작점 유무)에서 Z 여유 0 ~ 3 을 바꾸면
     * 두 NC 의 G00 진입과 화면의 진입점(직접 보정 G00)이 첫 깎는 점에서 Z 로 꼭 max(Z 여유, 노즈 R) — 정방향은 +Z(척 반대쪽),
     * 역방향은 −Z — 떨어진다. 첫 깎는 점 = 직접 보정은 첫 보정점(시작점을 넣었으면 시작점′), G41/G42 는 첫 도면점.
     * 홈은 단마다 G00 X = 외경 + 2 × X 여유
     */
    @Test
    fun entryFollowsZClearance() {
        val clear = listOf(0.0, 0.2, 0.5, 1.0, 2.0, 3.0)
        var n = 0
        for (kind in TaperKind.entries.filter { !it.groove }) for (type in 1..kind.types) for (rev in listOf(false, true)) {
            val x = (1..kind.points).map { TaperExample.point(kind, type, rev, "x$it").toDouble() }
            val z = (1..kind.points).map { TaperExample.point(kind, type, rev, "z$it").toDouble() }
            val (frontV, _) = legsVertical(kind, type)
            // 시작 요소 6 mm: 단면은 그 요소가 T1 에서 뻗는 쪽으로 X, 수평은 T1 바깥쪽 Z
            val dx = if (kind.inner) 12.0 else -12.0
            val start = if (frontV) x[0] + dx else z[0] + (if (rev) -6.0 else 6.0)
            for (r in listOf(0.4, 0.8)) for (r1 in listOf(0.0, 1.0)) for (lead in listOf(false, true)) for (zc in clear) {
                val corners = List(kind.points) { if (it == 0) r1 else 0.0 }
                val f = SlopeForm(kind, type, rev, r, null, x, z, null, zc, corners, if (lead) start else null)
                val what = "${kind.label} 형상 $type ${if (rev) "역" else "정"} r$r R$r1 시작점 $lead Z 여유 $zc"
                val s = assertNotNull(TaperSolve.solve(f).solution, what)
                val c = maxOf(zc, r)
                val dir = if (rev) -1.0 else 1.0
                val skip = if (s.fromStart) 0 else 1
                assertEquals("G00 X${Fmt.nc(s.entry.xDia)} Z${Fmt.nc(s.entry.z)}", s.ncG40.first(), "$what 화면 진입점 = G00")
                assertEquals(s.program.segs[skip].start.z + dir * c, s.entry.z, 1e-9, "$what 직접 보정 ${s.ncG40}")
                val g00 = s.ncG41G42[1]
                assertTrue(g00.startsWith("G00 "), "$what ${s.ncG41G42}")
                assertTrue(g00.endsWith(" Z${Fmt.nc(s.drawing.segs[skip].start.z + dir * c)}"), "$what G41/G42 ${s.ncG41G42}")
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
