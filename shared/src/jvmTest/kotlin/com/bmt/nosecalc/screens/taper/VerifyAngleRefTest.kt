package com.bmt.nosecalc.screens.taper

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.CompMode
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.screens.TaperScreen
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-10-28 각도 기준): 구현의 도우미(complementText · userAngleExample)를 쓰지 않고 손 계산 · atan 으로 따로 맞춰 본다.
 * 1) 사용자 예를 손으로: X축 30° → Z1 = −23.8 − 3.15·tan30°, 보정점(팁 3 · R0.8 · 뒤집기 G41)은 r(1 − tan(θ/2)) 식으로
 * 2) 무작위 형상 · 종류 · 방향 · 뒤집기 · 각(5~85°): X 기준 θ = Z 기준 90 − θ, 자동 각도 = atan 으로 따로 구한 값
 * 3) 실제 화면(ImageComposeScene)에서 같은 숫자가 보이는지
 */
class VerifyAngleRefTest {
    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    /** 사용자 화면 그대로: 외경 형상 type, T1 ⇄ T2 바꿈, R0.8, X1 41 · Z1 비움 · X2 34.7 · Z2 −23.8 · 각도 30 */
    private fun userState(type: Int) = TaperState(TaperKind.OUTER, false, type, open = true).apply {
        toggleFlip()
        setPoint("x1", "41")
        setPoint("z1", "")
        setPoint("x2", "34.7")
        setPoint("z2", "-23.8")
        setPoint("ang", "30")
        setShared("noseR", "0.8")
    }

    private fun form(s: TaperState, ref: AngleRef): SlopeForm {
        val n = s.kind.points
        return SlopeForm(
            s.kind, s.type!!, s.reverse, s.shared("noseR").toDouble(), s.tip,
            (1..n).map { s.point("x$it").toDoubleOrNull() }, (1..n).map { s.point("z$it").toDoubleOrNull() }, s.point("ang").toDoubleOrNull(),
            s.shared("zClear").toDoubleOrNull() ?: 0.0, (1..n).map { s.point("r$it").toDoubleOrNull() ?: 0.0 },
            s.point("start").toDoubleOrNull(), s.point("end").toDoubleOrNull(), s.flip, angleRef = ref,
        )
    }

    /** NC 를 모달로 읽어 G01 로 간 자리마다 "X.. Z.." (주석 · G00 은 자리만 바꾼다) */
    private fun g01Path(nc: List<String>): List<String> {
        var x = ""
        var z = ""
        var g01 = false
        val out = mutableListOf<String>()
        for (line in nc) {
            if (line.startsWith("(")) continue
            for (w in line.split(' ')) {
                when {
                    w == "G00" -> g01 = false
                    w == "G01" -> g01 = true
                    w.startsWith("X") -> x = w
                    w.startsWith("Z") -> z = w
                }
            }
            if (g01) out += "$x $z"
        }
        return out
    }

    /** 1) 손 계산 — 화면 상태(SlopeForm) 그대로 */
    @Test
    fun userExampleByHand() {
        val r = 0.8
        val dr = (41 - 34.7) / 2 // 3.15
        val thetaX = 30.0
        val thetaZ = 90 - thetaX
        val z1 = -23.8 - dr * tan(rad(thetaX)) // 사용자 식: 3.15 × tan30 = 1.8187 → −25.6187
        assertEquals("-25.619", Fmt.fixed3(z1))
        // 팁 3 가상 인선: 수평 요소와 만나는 꼭짓점은 Z 로 r(1 − tan(θz/2)), 단면과 만나는 꼭짓점은 X(직경)로 2r(1 − tan(θx/2)) 안쪽
        val dzH = r * (1 - tan(rad(thetaZ) / 2)) // 0.338
        val dxV = 2 * r * (1 - tan(rad(thetaX) / 2)) // 1.171
        for (type in 1..4) {
            val s = userState(type)
            val res = TaperSolve.solve(form(s, AngleRef.X))
            val sol = assertNotNull(res.solution, "형상 $type ${res.warnings}")
            assertTrue(TaperWarning.AGAINST_TOOL !in res.warnings, "형상 $type ${res.warnings}")
            assertEquals(Side.G41, sol.side)
            assertEquals(3, sol.tip)
            val (t1, t2) = sol.points
            assertEquals(z1, t1.vertex.z, 1e-9)
            assertEquals(41.0, t1.vertex.xDia, 1e-9)
            assertEquals(-23.8, t2.vertex.z, 1e-9)
            // 뒤집으면 T1 앞이 단면인 형상은 2 · 4, T2 뒤가 단면인 형상은 1 · 2
            val frontV = type == 2 || type == 4
            val backV = type == 1 || type == 2
            val p1 = t1.nodes.single().prog
            val p2 = t2.nodes.single().prog
            if (frontV) {
                assertEquals(41 - dxV, p1.xDia, 1e-9, "형상 $type T1′ X")
                assertEquals(z1, p1.z, 1e-9)
            } else {
                assertEquals(41.0, p1.xDia, 1e-9)
                assertEquals(z1 - dzH, p1.z, 1e-9, "형상 $type T1′ Z")
            }
            if (backV) {
                assertEquals(34.7 - dxV, p2.xDia, 1e-9, "형상 $type T2′ X")
                assertEquals(-23.8, p2.z, 1e-9)
            } else {
                assertEquals(34.7, p2.xDia, 1e-9)
                assertEquals(-23.8 - dzH, p2.z, 1e-9, "형상 $type T2′ Z")
            }
            // 기계 보정 NC 는 도면 좌표로 그 경사(T1 → T2 를 G01 한 줄로), 직접 보정 NC 는 손으로 구한 보정점 — X · Z 는 모달로 따라간다
            val slope41 = "X41. Z-25.619" to "X34.7 Z-23.8"
            assertTrue(slope41 in g01Path(sol.ncG41G42).zipWithNext(), "형상 $type ${sol.ncG41G42}")
            val g40 = directNc(sol.ncG40, r, sol.tip)
            val slope40 = "X${Fmt.nc(p1.xDia)} Z${Fmt.nc(p1.z)}" to "X${Fmt.nc(p2.xDia)} Z${Fmt.nc(p2.z)}"
            assertTrue(slope40 in g01Path(g40).zipWithNext(), "형상 $type $slope40 $g40")
            assertEquals("30.000", Fmt.fixed3(sol.shownAngles(AngleRef.X).single()))
            // 같은 칸 30 을 Z축으로 읽으면 예전 값
            val zSol = assertNotNull(TaperSolve.solve(form(s, AngleRef.Z)).solution)
            assertEquals(-23.8 - dr / tan(rad(30.0)), zSol.points[0].vertex.z, 1e-9)
            assertEquals("-29.256", Fmt.fixed3(zSol.points[0].vertex.z))
        }
        // 형상 3 의 손 계산 숫자(골든 taper-b-angle-x30 과 같아야 한다)
        assertEquals("-25.957", Fmt.fixed3(z1 - dzH))
        assertEquals("-24.138", Fmt.fixed3(-23.8 - dzH))
        assertEquals("39.829", Fmt.fixed3(41 - dxV))
        assertEquals("33.529", Fmt.fixed3(34.7 - dxV))
    }

    private fun close(a: TaperResult, b: TaperResult, what: String) {
        assertEquals(a.warnings, b.warnings, what)
        val sa = a.solution
        val sb = b.solution
        if (sa == null || sb == null) {
            assertEquals(sa == null, sb == null, what)
            return
        }
        assertEquals(sa.side, sb.side, what)
        assertEquals(sa.tip, sb.tip, what)
        sa.points.zip(sb.points).forEach { (p, q) ->
            assertEquals(p.vertex.z, q.vertex.z, 1e-9, what)
            assertEquals(p.vertex.x, q.vertex.x, 1e-9, what)
            p.nodes.zip(q.nodes).forEach { (m, n) ->
                assertEquals(m.prog.z, n.prog.z, 1e-9, what)
                assertEquals(m.prog.x, n.prog.x, 1e-9, what)
            }
        }
        assertEquals(sa.ncG41G42, sb.ncG41G42, what)
        assertEquals(sa.ncG40, sb.ncG40, what)
    }

    private fun Random.angleText(): String {
        val m = nextInt(5_000, 85_001) // 5.000 ~ 85.000
        return "${m / 1000}.${(m % 1000).toString().padStart(3, '0')}"
    }

    /** 2) 무작위: 같은 경사를 X θ · Z 90 − θ 로 넣으면 같은 결과, 보이는 각은 atan 으로 따로 구한 값 */
    @Test
    fun randomSlopesBothRefs() {
        val rnd = Random(20261028L xor 0x5eed)
        var solvedGiven = 0
        var solvedAuto = 0
        repeat(4000) { iter ->
            val kind = if (rnd.nextBoolean()) TaperKind.OUTER else TaperKind.INNER
            val type = 1 + rnd.nextInt(5)
            val rev = rnd.nextBoolean()
            val flip = rnd.nextBoolean()
            val s = TaperState(kind, rev, type, open = true).example().apply { if (flip) toggleFlip() }
            // 예의 오르내림 방향은 두고 크기만 바꾼다(형상 그림과 맞게)
            val x1 = s.point("x1").toDouble()
            val z1 = s.point("z1").toDouble()
            val sx = sign(s.point("x2").toDouble() - x1)
            val sz = sign(s.point("z2").toDouble() - z1)
            val dr = 0.5 + rnd.nextDouble() * 12
            val dz = 0.5 + rnd.nextDouble() * 20
            val x2 = x1 + sx * 2 * dr
            val z2 = z1 + sz * dz
            s.setPoint("x2", Fmt.nc(x2).removeSuffix("."))
            s.setPoint("z2", Fmt.nc(z2).removeSuffix("."))
            s.setShared("noseR", listOf("0.2", "0.4", "0.8", "1.2")[rnd.nextInt(4)])
            s.setShared("zClear", if (rnd.nextBoolean()) "0" else "1")
            if (rnd.nextInt(4) == 0) s.setPoint("r1", "0.3")
            val what = "#$iter ${kind.label} 형상 $type 역 $rev 뒤집기 $flip"
            // (a) 각도 빈칸: 넷이 다 찼다 — 자동 각도가 기준마다 atan 값
            run {
                s.setPoint("ang", "")
                val xs = (1..2).map { s.point("x$it").toDouble() / 2 }
                val zs = (1..2).map { s.point("z$it").toDouble() }
                val ar = abs(xs[1] - xs[0])
                val az = abs(zs[1] - zs[0])
                val rx = TaperSolve.solve(form(s, AngleRef.X))
                val rz = TaperSolve.solve(form(s, AngleRef.Z))
                close(rx, rz, "$what 자동")
                rx.solution?.let { sol ->
                    solvedAuto++
                    assertEquals(deg(atan2(az, ar)), sol.shownAngles(AngleRef.X).single(), 1e-9, what)
                    assertEquals(deg(atan2(ar, az)), sol.shownAngles(AngleRef.Z).single(), 1e-9, what)
                }
            }
            // (b) 각도를 넣고 넷 가운데 하나를 비운다
            val blank = listOf("x1", "z1", "x2", "z2")[rnd.nextInt(4)]
            val keep = s.point(blank)
            s.setPoint(blank, "")
            val xText = rnd.angleText()
            val thetaX = xText.toDouble()
            // Z 칸 글은 손으로: 90 − θ(소수 셋째 자리 그대로)
            val zm = 90_000 - (xText.replace(".", "").toInt())
            val zText = "${zm / 1000}.${(zm % 1000).toString().padStart(3, '0')}"
            s.setPoint("ang", xText)
            val rx = TaperSolve.solve(form(s, AngleRef.X))
            s.setPoint("ang", zText)
            val rz = TaperSolve.solve(form(s, AngleRef.Z))
            close(rx, rz, "$what $blank 비움 X$xText Z$zText")
            rx.solution?.let { sol ->
                solvedGiven++
                val (p, q) = sol.points.map { it.vertex }
                val ar = abs(q.x - p.x)
                val az = abs(q.z - p.z)
                // X 기준: 단면(세로선)에서 잰 각 → Z 길이 = 반경 차 × tan θx
                assertEquals(ar * tan(rad(thetaX)), az, 1e-7 * (1 + az), "$what $blank")
                assertEquals(thetaX, sol.shownAngles(AngleRef.X).single(), 1e-7, what)
                assertEquals(zText.toDouble(), sol.shownAngles(AngleRef.Z).single(), 1e-7, what)
            }
            s.setPoint(blank, keep)
        }
        assertTrue(solvedAuto > 2500, "자동 각도 풀린 수 $solvedAuto")
        assertTrue(solvedGiven > 2000, "각도로 푼 수 $solvedGiven")
    }

    /** 2중 테이퍼: 각도 칸이 없어 결과는 기준과 상관없고, 각1 · 각2 는 칸 순서의 두 경사를 atan 으로 */
    @Test
    fun randomDoubleShownAngles() {
        val rnd = Random(77)
        var solved = 0
        repeat(1500) {
            val kind = if (rnd.nextBoolean()) TaperKind.OUTER_DOUBLE else TaperKind.INNER_DOUBLE
            val type = 1 + rnd.nextInt(4)
            val s = TaperState(kind, rnd.nextBoolean(), type, open = true).example().apply { if (rnd.nextBoolean()) toggleFlip() }
            val x = (1..3).map { s.point("x$it").toDouble() }.toMutableList()
            val z = (1..3).map { s.point("z$it").toDouble() }.toMutableList()
            for (i in 1..2) {
                x[i] = x[i - 1] + sign(s.point("x${i + 1}").toDouble() - s.point("x$i").toDouble()) * 2 * (0.5 + rnd.nextDouble() * 8)
                z[i] = z[i - 1] + sign(s.point("z${i + 1}").toDouble() - s.point("z$i").toDouble()) * (0.5 + rnd.nextDouble() * 10)
            }
            (1..3).forEach { i ->
                s.setPoint("x$i", Fmt.nc(x[i - 1]).removeSuffix("."))
                s.setPoint("z$i", Fmt.nc(z[i - 1]).removeSuffix("."))
            }
            val rx = TaperSolve.solve(form(s, AngleRef.X))
            val rz = TaperSolve.solve(form(s, AngleRef.Z))
            close(rx, rz, "2중")
            val sol = rx.solution ?: return@repeat
            solved++
            val xs = (1..3).map { s.point("x$it").toDouble() / 2 }
            val zs = (1..3).map { s.point("z$it").toDouble() }
            val wantX = (0..1).map { deg(atan2(abs(zs[it + 1] - zs[it]), abs(xs[it + 1] - xs[it]))) }
            val wantZ = (0..1).map { deg(atan2(abs(xs[it + 1] - xs[it]), abs(zs[it + 1] - zs[it]))) }
            sol.shownAngles(AngleRef.X).zip(wantX).forEach { (a, b) -> assertEquals(b, a, 1e-9) }
            sol.shownAngles(AngleRef.Z).zip(wantZ).forEach { (a, b) -> assertEquals(b, a, 1e-9) }
        }
        assertTrue(solved > 1000, "풀린 수 $solved")
    }

    /** 범위 밖 각도: X 0° · 90° 는 경사가 아니다(BAD_ANGLE, 각도 칸 빨강). 아주 큰 X 각은 Z 가 10 m 를 넘어 BAD_INPUT */
    @Test
    fun badAnglesInX() {
        val s = userState(3)
        for (a in listOf("0", "90", "-5", "95")) {
            s.setPoint("ang", a)
            val f = form(s, AngleRef.X)
            val r = TaperSolve.solve(f)
            assertNull(r.solution, a)
            assertEquals(listOf(TaperWarning.BAD_ANGLE), r.warnings, a)
            assertEquals(setOf("ang"), taperWarningFields(TaperWarning.BAD_ANGLE, f), a)
        }
        s.setPoint("ang", "89.99")
        val r = TaperSolve.solve(form(s, AngleRef.X))
        assertNull(r.solution)
        assertEquals(listOf(TaperWarning.BAD_INPUT), r.warnings)
        println("X축 89.99° → ${r.warnings}: ${taperWarningText(TaperWarning.BAD_INPUT, form(s, AngleRef.X))}")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.texts(merged: Boolean = false): List<String> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        return semanticsOwners.flatMap { walk(if (merged) it.rootSemanticsNode else it.unmergedRootSemanticsNode) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.clickables(): List<List<String>> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        return semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
            .filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
            .map { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.tap(text: String) {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        val node = semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.single { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }
        node.config[SemanticsActions.OnClick].action!!.invoke()
    }

    /** 3) 실제 화면: 처음 기준(X축)에서 사용자 예가 Z1 자동 −25.619 · 직접 보정 NC 의 손 계산 보정점으로 보이고, 'Z축 기준' 을 누르면 칸만 60 */
    @Test
    fun userExampleOnScreen() {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        assertEquals(AngleRef.X, app.angleRef)
        app.compMode = CompMode.DIRECT
        val st = userState(3)
        val scene = ImageComposeScene(420, 5200, Density(1f)) { AppHost(app) { TaperScreen(st) } }
        try {
            scene.render(0)
            val t = scene.texts()
            val all = t.joinToString("\n")
            assertTrue("각도 (X축 기준)" in t, all)
            assertTrue("-25.619" in t, "자동 Z1: $all")
            assertTrue("G01 X41. Z-25.957" in all && "G01 X34.7 Z-24.138" in all, "직접 보정 NC: $all")
            println("누르는 칸(합친 글): ${scene.clickables().filter { l -> l.any { "축" in it } }}")
            scene.tap("Z축 기준")
            scene.render(100_000_000)
            assertEquals(AngleRef.Z, app.angleRef)
            assertEquals("60", st.point("ang"))
            val t2 = scene.texts()
            val all2 = t2.joinToString("\n")
            assertTrue("각도 (Z축 기준)" in t2 && "각도 (X축 기준)" !in t2, all2)
            assertTrue("-25.619" in t2, "같은 경사: $all2")
            assertTrue("G01 X41. Z-25.957" in all2, all2)
        } finally {
            scene.close()
        }
    }

    /** 3) 화면의 자동 각도(빈 각도 칸): 무작위 점에서 기준마다 atan 값이 칸에 보인다 */
    @Test
    fun autoAngleOnScreen() {
        val rnd = Random(11)
        repeat(8) { i ->
            val ref = if (i % 2 == 0) AngleRef.X else AngleRef.Z
            val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply { angleRef = ref }
            val st = TaperState(TaperKind.OUTER, false, 3, open = true).example()
            val dr = 1 + rnd.nextDouble() * 8
            val dz = 1 + rnd.nextDouble() * 15
            val x2 = Fmt.nc(40 + 2 * dr).removeSuffix(".")
            val z2 = Fmt.nc(-dz).removeSuffix(".")
            st.setPoint("x2", x2)
            st.setPoint("z2", z2)
            val ar = (x2.toDouble() - 40) / 2
            val az = abs(z2.toDouble())
            val want = Fmt.fixed3(if (ref == AngleRef.X) deg(atan2(az, ar)) else deg(atan2(ar, az)))
            val scene = ImageComposeScene(420, 4200, Density(1f)) { AppHost(app) { TaperScreen(st) } }
            try {
                scene.render(0)
                val t = scene.texts()
                assertTrue("각도 (${ref.label})" in t, "$t")
                assertTrue(want in t, "$ref X2 $x2 Z2 $z2 → $want: $t")
                assertTrue("$want°" in t, "결과 카드 $want°: $t")
            } finally {
                scene.close()
            }
        }
    }
}
