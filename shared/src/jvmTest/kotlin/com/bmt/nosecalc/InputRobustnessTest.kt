package com.bmt.nosecalc

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.TriangleInput
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.dims
import com.bmt.nosecalc.screens.taper.SlopeForm
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperSolve
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleState
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 잘못된 입력에서 앱이 죽지 않는가. 키패드로 칠 수 있는 글(숫자 12자리·− ·.·빈칸)만 넣는다 — 화면처럼 toDoubleOrNull 로 바꿔
 * 계산기에 넘기고, 예외가 없고 화면에 찍는 숫자가 모두 유한한지(Fmt 가 NaN·무한대면 예외를 낸다) 본다.
 * 마지막으로 극단 값 화면을 실제로 그려 본다(형상 뷰어·자동 글자 크기).
 */
class InputRobustnessTest {
    private val pool = listOf(
        "", "0", "-0", "0.001", "0.0001", "0.4", "1", "1.2", "5", "-5", "10", "-10", "-20", "30", "-30", "40", "-40", "45",
        "50", "89.999", "90", "100", "9999.999", "-9999.999", "10000.001", "123456.789", "999999999999", "-999999999999",
        "0.00000000001",
    )
    private val rnd = Random(20261017)
    private var solved = 0
    private fun pick() = pool[rnd.nextInt(pool.size)]

    /** 대개는 그럴듯한 값(±60 mm, 셋째 자리), 다섯에 하나는 위 극단 값 */
    private fun num(): Double? =
        if (rnd.nextInt(5) == 0) pick().toDoubleOrNull() else (rnd.nextInt(-60_000, 60_001) / 1000.0)

    /** 화면 칸 글 흔들기: 처음 값 그대로 · 조금 바꿈 · 빈칸 · 극단 값 — 결과가 나오는 경우가 많이 지나가게 */
    private fun nudge(text: String): String = when (rnd.nextInt(8)) {
        0 -> pick()
        1 -> ""
        2, 3, 4 -> ((text.toDoubleOrNull() ?: 0.0) + rnd.nextInt(-3000, 3001) / 1000.0).toString()
        else -> text
    }

    private fun show(vararg v: Double) = v.forEach { Fmt.fixed3(it) }
    private fun show(p: Pt) = show(p.xDia, p.z)

    private fun check(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            fail("$what 에서 예외: $t")
        }
    }

    @Test
    fun taper() {
        val kinds = listOf(TaperKind.OUTER, TaperKind.INNER, TaperKind.OUTER_DOUBLE, TaperKind.INNER_DOUBLE)
        repeat(60_000) {
            val kind = kinds[rnd.nextInt(kinds.size)]
            val st = TaperState(kind, rnd.nextBoolean(), 1 + rnd.nextInt(kind.types), open = true)
            val n = kind.points
            val names = (1..n).flatMap { listOf("x$it", "z$it", "r$it") } + listOf("ang", "start", "end")
            for (name in names) if (rnd.nextInt(3) == 0) st.setPoint(name, nudge(st.point(name)))
            if (rnd.nextInt(3) == 0) st.setShared("noseR", nudge(st.shared("noseR")))
            if (rnd.nextInt(5) == 0) st.setShared("zClear", nudge(st.shared("zClear")))
            if (rnd.nextInt(5) == 0) st.tip = rnd.nextInt(10)
            // SlopeScreen 과 같은 변환
            val f = SlopeForm(
                kind, st.type!!, st.reverse,
                st.shared("noseR").toDoubleOrNull() ?: Double.NaN,
                st.tip,
                (1..n).map { st.point("x$it").toDoubleOrNull() },
                (1..n).map { st.point("z$it").toDoubleOrNull() },
                st.point("ang").toDoubleOrNull(),
                st.shared("zClear").toDoubleOrNull() ?: 1.0,
                (1..n).map { st.point("r$it").toDoubleOrNull() ?: 0.0 },
                st.point("start").toDoubleOrNull(),
                st.point("end").toDoubleOrNull(),
            )
            check("테이퍼 $f") {
                val s = TaperSolve.solve(f).solution ?: return@check
                solved++
                s.points.forEach { p ->
                    show(p.vertex)
                    p.nodes.forEach { show(it.drawn); show(it.prog); show(it.dXDia, it.dZ) }
                    p.corner?.let { c -> show(c.r); c.progR?.let { show(it) } }
                }
                s.anglesDeg.forEach { show(it) }
                show(s.entry)
                show(s.exit)
                show(s.drawing.segs.first().start)
                show(s.drawing.segs.last().end)
            }
        }
        assertTrue(solved > 10_000, "결과가 나온 사례가 너무 적다: $solved")
    }

    @Test
    fun groove() {
        repeat(20_000) {
            val i = GrooveInput(num(), num(), num(), num(), num(), num() ?: 0.5, GrooveRef.entries.random(rnd), num() ?: 1.0)
            check("홈 $i") {
                val s = GrooveCalc.calc(i).solution ?: return@check
                s.steps.forEach { show(it.z, it.xDia) }
                show(s.pitch, s.retractXDia)
            }
        }
    }

    @Test
    fun round() {
        repeat(30_000) {
            val type = 1 + rnd.nextInt(6)
            val st = RoundState(type = type, open = true)
            for (d in dims(type)) if (rnd.nextInt(3) == 0) st.setDim(type, d.key, nudge(st.dim(type, d.key)))
            if (rnd.nextInt(3) == 0) st.setShared("noseR", nudge(st.shared("noseR")))
            if (rnd.nextInt(5) == 0) st.setShared("leg", nudge(st.shared("leg")))
            if (rnd.nextInt(5) == 0) st.tip = rnd.nextInt(10)
            val i = st.input(type)
            check("라운드 $i") {
                RoundCalc.calc(i.copy(noseR = 0.0)).solution
                val s = RoundCalc.calc(i).solution ?: return@check
                solved++
                s.nodes.forEach { show(it.drawn); show(it.prog) }
                s.arcs.forEach { a -> show(a.r); a.progR?.let { show(it) } }
                show(s.entry)
            }
            val m = RoundState(type = MANUAL, open = true).apply {
                inner = rnd.nextBoolean()
                for (name in listOf("x1", "z1", "x2", "z2", "x3", "z3")) if (rnd.nextInt(3) == 0) setPoint(name, nudge(point(name)))
                if (rnd.nextInt(3) == 0) setShared("noseR", nudge(shared("noseR")))
            }.arc3()
            check("3점호 $m") {
                RoundCalc.arc3(m.copy(noseR = 0.0)).solution
                val s = RoundCalc.arc3(m).solution ?: return@check
                solved++
                s.nodes.forEach { show(it.drawn); show(it.prog) }
                s.arcs.forEach { a -> show(a.r); show(a.center) }
                show(s.entry)
            }
        }
        assertTrue(solved > 10_000, "결과가 나온 사례가 너무 적다: $solved")
    }

    @Test
    fun triangle() {
        repeat(20_000) {
            val i = TriangleInput(num(), num(), num(), num(), num(), rnd.nextBoolean())
            check("삼각형 $i") {
                val s = RightTriangle.calc(i).solution ?: return@check
                show(s.a, s.b, s.c, s.angleA, s.angleB, s.dz, s.dxDia)
            }
        }
    }

    /** 극단 값으로 화면을 실제로 그린다(그리다 죽지 않는가) */
    @Test
    fun drawExtremes() {
        val big = "999999999999"
        val tiny = "0.001"
        fun draw(content: @androidx.compose.runtime.Composable () -> Unit) {
            val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
            val scene = ImageComposeScene(400, 1600, Density(1f)) { AppHost(state) { content() } }
            try {
                repeat(4) { scene.render(it * 100_000_000L).close() }
            } finally {
                scene.close()
            }
        }
        for (v in listOf(big, tiny, "-$big", "0")) {
            draw {
                TaperScreen(
                    TaperState(type = 1, open = true).apply {
                        setPoint("x2", v)
                        setPoint("r1", v)
                        setPoint("start", v)
                        setShared("noseR", tiny)
                    },
                )
            }
            draw { TaperScreen(TaperState(TaperKind.OUTER_DOUBLE, type = 1, open = true).apply { setPoint("z3", v) }) }
            draw { TaperScreen(TaperState(TaperKind.GROOVE, type = 1, open = true).apply { setShared("width", v) }) }
            draw { RoundScreen(RoundState(type = 3, open = true).apply { setDim(3, "mainR", v) }) }
            draw { RoundScreen(RoundState(type = MANUAL, open = true).apply { setPoint("x2", v) }) }
            draw { TriangleScreen(TriangleState().apply { set(TriField.A_SIDE, v) }) }
        }
    }
}
