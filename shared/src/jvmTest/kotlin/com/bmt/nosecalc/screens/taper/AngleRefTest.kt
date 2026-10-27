package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import java.io.File
import kotlin.math.PI
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 각도 기준(사용자 결정 2026-10-28): 화면의 각도는 X축(단면 · 세로선) · Z축(가로선) 기준을 고르고(처음 X축, 저장),
 * 계산(TaperCalc)은 늘 Z 축에서 잰 각이라 화면 경계에서 X 각 = 90° − Z 각으로 바꾼다
 */
class AngleRefTest {
    /** SlopeScreen 의 입력 → SlopeForm 그대로(각도 칸은 ref 기준) */
    private fun formOf(s: TaperState, ref: AngleRef): SlopeForm {
        val n = s.kind.points
        return SlopeForm(
            s.kind, s.type!!, s.reverse, s.shared("noseR").toDouble(), s.tip,
            (1..n).map { s.point("x$it").toDoubleOrNull() }, (1..n).map { s.point("z$it").toDoubleOrNull() }, s.point("ang").toDoubleOrNull(),
            s.shared("zClear").toDoubleOrNull() ?: 0.0, (1..n).map { s.point("r$it").toDoubleOrNull() ?: 0.0 },
            s.point("start").toDoubleOrNull(), s.point("end").toDoubleOrNull(), s.flip, angleRef = ref,
        )
    }

    private fun xz(p: Pt) = "X${Fmt.fixed3(p.xDia)} Z${Fmt.fixed3(p.z)}"

    /** 화면 · 복사에 나오는 글(각도 빼고): 경고, 점마다 꼭짓점 · 도면 → 보정 접점 · 모서리 R, 진입 · 후퇴 · 접근 줄, 두 NC */
    private fun text(r: TaperResult): String = buildString {
        appendLine("경고 ${r.warnings}")
        val s = r.solution ?: return@buildString
        appendLine("팁 ${s.tip} ${s.side} ${s.fromStart} ${s.toEnd}")
        s.points.forEach { p ->
            appendLine(xz(p.vertex))
            p.nodes.forEach { appendLine("  ${xz(it.drawn)} → ${xz(it.prog)} ΔX ${signed(it.dXDia)} ΔZ ${signed(it.dZ)}") }
            p.corner?.let { appendLine("  ${it.cw} ${Fmt.fixed3(it.r)} ${it.progR?.let(Fmt::fixed3)}") }
        }
        appendLine("${xz(s.entry)} ${xz(s.exit)} ${s.approach.map(::xz)} 끝 ${xz(s.cutEnd)}")
        s.ncG41G42.forEach(::appendLine)
        directNc(s.ncG40, 0.4, s.tip).forEach(::appendLine)
    }

    /**
     * (a) 사용자 예 그대로: 외경 · 뒤집기 · R0.8 · X1 41 · X2 34.7 · Z2 −23.8 · 각도 30 · Z1 비움.
     * X축 기준(처음 값)이면 사용자의 손 계산 41 − 34.7 = 6.3, ÷2 = 3.15, × tan30° = 1.819, + 23.8 → Z1 = −25.619,
     * Z축 기준이면 예전 값 3.15 ÷ tan30° = 5.456 → −29.256. 형상 1~4(외경이 척 쪽으로 커지는 꼴) 모두
     */
    @Test
    fun userExample() {
        val dx = (41 - 34.7) / 2
        for (type in 1..4) {
            val s = userAngleExample(type)
            assertEquals(listOf("41", "", "34.7", "-23.8", "30"), listOf("x1", "z1", "x2", "z2", "ang").map(s::point))
            val x = TaperSolve.solve(formOf(s, AngleRef.X))
            val sx = assertNotNull(x.solution, "형상 $type ${x.warnings}")
            assertEquals("-25.619", Fmt.fixed3(sx.points[0].vertex.z), "형상 $type X축 기준 Z1")
            assertEquals(-23.8 - dx * tan(30 * PI / 180), sx.points[0].vertex.z, 1e-9)
            assertEquals("41.000", Fmt.fixed3(sx.points[0].vertex.xDia))
            assertEquals(Side.G41, sx.side, "뒤집은 외경 정방향")
            assertEquals(listOf("30.000"), sx.shownAngles(AngleRef.X).map(Fmt::fixed3))
            assertEquals(listOf("60.000"), sx.shownAngles(AngleRef.Z).map(Fmt::fixed3))
            val z = TaperSolve.solve(formOf(s, AngleRef.Z))
            val sz = assertNotNull(z.solution, "형상 $type ${z.warnings}")
            assertEquals("-29.256", Fmt.fixed3(sz.points[0].vertex.z), "형상 $type Z축 기준 Z1")
            assertEquals(-23.8 - dx / tan(30 * PI / 180), sz.points[0].vertex.z, 1e-9)
            assertEquals(listOf("30.000"), sz.shownAngles(AngleRef.Z).map(Fmt::fixed3))
        }
    }

    /**
     * (b) 같은 경사를 두 기준으로(Z 각 θ ↔ X 각 90 − θ, 칸 글은 complementText): 모든 종류 · 형상 · 방향 · 뒤집기에서 X1 · Z1 · X2 · Z2 가운데
     * 하나를 비우고 풀면 경고 · 점 · 보정점 · 진입 · 두 NC 가 글자까지 같고, 보이는 각만 그 기준(X = 90 − Z).
     * 2중 테이퍼는 각도 칸이 없어 결과가 기준과 상관없고 보이는 각1 · 각2 만 X = 90 − Z
     */
    @Test
    fun sameSlopeEitherRef() {
        var solved = 0
        val degs = listOf("5", "17.3", "30", "45", "59.744", "72.7", "85")
        for (kind in listOf(TaperKind.OUTER, TaperKind.INNER)) for (type in 1..5) for (rev in listOf(false, true)) for (flip in listOf(false, true)) {
            for (blank in listOf("x1", "z1", "x2", "z2")) for (zText in degs) for (r1 in listOf("", "1")) {
                val s = TaperState(kind, rev, type, open = true).example().apply {
                    if (flip) toggleFlip()
                    setPoint(blank, "")
                    setPoint("r1", r1)
                }
                val what = "${kind.label} 형상 $type 역 $rev 뒤집기 $flip $blank 비움 Z각 $zText R$r1"
                s.setPoint("ang", zText)
                val z = TaperSolve.solve(formOf(s, AngleRef.Z))
                val xText = complementText(zText)
                s.setPoint("ang", xText)
                val x = TaperSolve.solve(formOf(s, AngleRef.X))
                assertEquals(text(z), text(x), what)
                val sz = z.solution ?: continue
                val sx = assertNotNull(x.solution)
                solved++
                assertEquals(Fmt.fixed3(zText.toDouble()), Fmt.fixed3(sz.shownAngles(AngleRef.Z).single()), what)
                assertEquals(Fmt.fixed3(xText.toDouble()), Fmt.fixed3(sx.shownAngles(AngleRef.X).single()), what)
            }
        }
        assertTrue(solved > 600, "풀린 사례 $solved")
        for (kind in listOf(TaperKind.OUTER_DOUBLE, TaperKind.INNER_DOUBLE)) for (type in 1..4) for (rev in listOf(false, true)) for (flip in listOf(false, true)) {
            val s = TaperState(kind, rev, type, open = true).example().apply { if (flip) toggleFlip() }
            val x = TaperSolve.solve(formOf(s, AngleRef.X))
            val z = TaperSolve.solve(formOf(s, AngleRef.Z))
            assertEquals(text(z), text(x))
            val sol = assertNotNull(x.solution)
            assertEquals(2, sol.anglesDeg.size)
            assertEquals(sol.anglesDeg.map { Fmt.fixed3(90 - it) }, sol.shownAngles(AngleRef.X).map(Fmt::fixed3))
            assertEquals(sol.anglesDeg.map(Fmt::fixed3), sol.shownAngles(AngleRef.Z).map(Fmt::fixed3))
        }
    }

    /** 칸 글 바꾸기: 90 − 값, 넣은 소수 자리 그대로. 숫자가 아니면 그대로. 두 번 바꾸면 처음 숫자 */
    @Test
    fun complementKeepsDecimals() {
        val cases = mapOf(
            "30" to "60", "60" to "30", "59.744" to "30.256", "12.50" to "77.50", "30." to "60", "0.0001" to "89.9999", "89.999" to "0.001",
            "90" to "0", "100" to "-10", "-10" to "100", "-0" to "90", "45" to "45", "" to "", "-" to "-", "." to ".",
            "999999999999" to "-999999999909",
        )
        cases.forEach { (a, b) -> assertEquals(b, complementText(a), "\"$a\"") }
        val rnd = Random(20261028)
        repeat(20_000) {
            val dec = rnd.nextInt(0, 6)
            var scale = 1L
            repeat(dec) { scale *= 10 }
            val m = rnd.nextLong(-200 * scale, 200 * scale)
            val t = (if (m < 0) "-" else "") + (kotlin.math.abs(m) / scale) + if (dec > 0) "." + (kotlin.math.abs(m) % scale).toString().padStart(dec, '0') else ""
            val c = complementText(t)
            assertEquals(90 - t.toDouble(), c.toDouble(), 1e-9, t)
            assertEquals(t.toDouble(), complementText(c).toDouble(), 1e-12, t)
            assertEquals(dec, if ('.' in c) c.substringAfter('.').length else 0, t)
        }
    }

    /**
     * (c) 스위치: 기준을 바꾸면 각도 칸이 같은 실제 경사의 새 기준 값으로(모든 종류 · 형상 · 방향의 각도 칸 — 칸은 기준을 따로 적지 않는다),
     * 풀린 결과는 그대로. 빈칸(자동) 각도는 빈칸이고 새 기준으로 다시 보인다. 같은 기준을 누르면 그대로, 되돌리면 처음 숫자
     */
    @Test
    fun switchConvertsFields() {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        assertEquals(AngleRef.X, app.angleRef, "처음은 X축 기준")
        // 외경 형상 3: Z2 를 비우고 X축 60° 로 푼다(= Z축 30°)
        val s = TaperState(type = 3, open = true).example().apply {
            setPoint("z2", "")
            setPoint("ang", "60")
            type = 1
            setPoint("ang", "12.50")
            reverse = true
            type = 2
            setPoint("ang", "")
            reverse = false
            type = 3
        }
        val before = TaperSolve.solve(formOf(s, app.angleRef))
        assertEquals("-8.660", Fmt.fixed3(assertNotNull(before.solution).points[1].vertex.z))
        switchAngleRef(app, s, AngleRef.Z)
        assertEquals(AngleRef.Z, app.angleRef)
        assertEquals("30", s.point("ang"))
        assertEquals(text(before), text(TaperSolve.solve(formOf(s, app.angleRef))), "같은 경사")
        s.type = 1
        assertEquals("77.50", s.point("ang"))
        s.reverse = true
        s.type = 2
        assertEquals("", s.point("ang"))
        s.reverse = false
        s.type = 3
        // 같은 기준을 또 눌러도 그대로
        switchAngleRef(app, s, AngleRef.Z)
        assertEquals("30", s.point("ang"))
        switchAngleRef(app, s, AngleRef.X)
        assertEquals("60", s.point("ang"))
        s.type = 1
        assertEquals("12.50", s.point("ang"))
        // 빈칸(자동) 각도: 외경 형상 1 X40 Z0 / X60 Z-5 는 Z축 63.435° = X축 26.565°. 칸은 빈칸 그대로, 보이는 자동 값만 기준을 따른다
        s.setPoint("ang", "")
        listOf("x1" to "40", "z1" to "0", "x2" to "60", "z2" to "-5").forEach { (k, v) -> s.setPoint(k, v) }
        val sol = assertNotNull(TaperSolve.solve(formOf(s, app.angleRef)).solution)
        assertEquals("26.565", Fmt.fixed3(sol.shownAngles(app.angleRef).single()))
        switchAngleRef(app, s, AngleRef.Z)
        assertEquals("", s.point("ang"))
        val solZ = assertNotNull(TaperSolve.solve(formOf(s, app.angleRef)).solution)
        assertEquals("63.435", Fmt.fixed3(solZ.shownAngles(app.angleRef).single()))
        assertEquals(text(TaperSolve.solve(formOf(s, AngleRef.X))), text(TaperSolve.solve(formOf(s, AngleRef.Z))), "빈 각도는 기준과 상관없다")
    }

    /**
     * (d) 저장 · 되살리기: 기준은 앱 설정(KeyValueStore, 테이퍼 화면 전체 하나)이라 다음에 켜도 그대로이고 모르는 값은 처음 값(X축).
     * 각도 칸 글은 화면 상태(Saver)로 되살아나 저장된 기준으로 읽는다 — 스위치가 둘을 함께 바꾸므로 어긋나지 않는다
     */
    @Test
    fun refIsSavedAndRestored() {
        val file = File.createTempFile("bmt-kv", ".properties").apply { delete() }
        assertEquals(AngleRef.X, AppState(KeyValueStore(file)).angleRef)
        val app = AppState(KeyValueStore(file))
        val s = TaperState(type = 3, open = true).example().apply {
            setPoint("z2", "")
            setPoint("ang", "60")
        }
        switchAngleRef(app, s, AngleRef.Z)
        val again = AppState(KeyValueStore(file))
        assertEquals(AngleRef.Z, again.angleRef)
        val back = with(TaperState.Saver) { restore(androidx.compose.runtime.saveable.SaverScope { true }.save(s)!!)!! }
        assertEquals("30", back.point("ang"))
        assertEquals(text(TaperSolve.solve(formOf(s, AngleRef.Z))), text(TaperSolve.solve(formOf(back, again.angleRef))))
        again.angleRef = AngleRef.X
        assertEquals(AngleRef.X, AppState(KeyValueStore(file)).angleRef)
        KeyValueStore(file).put("taperAngleRef", "Y")
        assertEquals(AngleRef.X, AppState(KeyValueStore(file)).angleRef, "모르는 값은 처음 값")
    }

    /**
     * (e) BAD_INPUT 의 괄호는 기준과 상관없이 맞는 말: 사용자 예에서 Z1 을 비우고 X축 89.99° · Z축 0.01° 는 둘 다 Z 가 10 m 밖이다
     * (옛 글 "각도가 너무 작으면" 은 X축 89.99° 에서 거꾸로였다)
     */
    @Test
    fun badInputTextFitsBothRefs() {
        val s = TaperState(TaperKind.OUTER, false, 3, open = true).apply {
            toggleFlip()
            setPoint("x1", "41")
            setPoint("z1", "")
            setPoint("x2", "34.7")
            setPoint("z2", "-23.8")
            setShared("noseR", "0.8")
        }
        for ((ref, a) in listOf(AngleRef.X to "89.99", AngleRef.Z to "0.01")) {
            s.setPoint("ang", a)
            val f = formOf(s, ref)
            assertEquals(listOf(TaperWarning.BAD_INPUT), TaperSolve.solve(f).warnings, "$ref $a")
            val t = taperWarningText(TaperWarning.BAD_INPUT, f)
            assertTrue("각도가 0° 나 90° 에 너무 가까우면" in t && "너무 작으면" !in t, t)
        }
    }

    /**
     * (f) 스위치 모양: 1배 글꼴은 줄 52dp 그대로(칸 46dp). 1.3배는 두 줄이 다 들어가게 줄이 높아진다 — 고정 52dp 일 때 아랫줄 '단면' 이
     * 남은 높이로 눌려 칸 끝에서 잘렸다(360dp 반 줄 폭 140dp 는 짧은 글 "X축"). 누르는 칸의 읽는 글은 "X축 기준, 단면" 한 번(안 놓는 짧은 글은 빼고)
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun switchFitsLargeFontAndReadsOnce() {
        val d = 2.625f
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        for ((fs, w) in listOf(1f to 164, 1.3f to 164, 1.3f to 140)) {
            val what = "글꼴 $fs · 폭 ${w}dp"
            val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
            val scene = ImageComposeScene((200 * d).toInt(), (200 * d).toInt(), Density(d, fs)) {
                AppHost(app) { AngleRefSwitch(AngleRef.X, {}, Modifier.width(w.dp)) }
            }
            try {
                // 글꼴은 몇 프레임 뒤에 읽힌다
                var t = 0L
                repeat(10) {
                    scene.render(t).close()
                    t += 100_000_000L
                }
                val cells = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
                assertEquals(
                    listOf(listOf("X축 기준", "단면"), listOf("Z축 기준", "수평")),
                    cells.map { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }, what,
                )
                if (fs == 1f) cells.forEach { assertEquals(46f, it.boundsInRoot.height / d, .5f, "$what 칸 높이") }
                val subs = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                    .filter { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "단면" || it.text == "수평" } }
                assertEquals(2, subs.size, what)
                subs.zip(cells).forEach { (sub, cell) ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    sub.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                    assertFalse(layouts.single().didOverflowHeight, "$what 아랫줄이 눌림")
                    assertTrue(sub.boundsInRoot.bottom <= cell.boundsInRoot.bottom + .5f, "$what 아랫줄이 칸 밖")
                }
            } finally {
                scene.close()
            }
        }
    }
}
