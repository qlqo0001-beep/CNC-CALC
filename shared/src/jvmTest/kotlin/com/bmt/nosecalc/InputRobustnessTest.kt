package com.bmt.nosecalc

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.TriangleInput
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.dims
import com.bmt.nosecalc.screens.taper.SlopeForm
import com.bmt.nosecalc.screens.taper.shownAngles
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperSolve
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.cutEnd
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.taper.taperWarningFields
import com.bmt.nosecalc.screens.taper.taperWarningText
import com.bmt.nosecalc.screens.thread.ThreadFamily
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.gaugeRows
import com.bmt.nosecalc.screens.thread.pipeResultCells
import com.bmt.nosecalc.screens.thread.threadWarningText
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
        // 뒤집기(T1 ⇄ 마지막 T)는 따로 난수 — 원래 사례(같은 난수 순서)는 그대로 두고 절반쯤을 뒤집어 본다. 각도 기준(X축 · Z축)도 따로
        val flips = Random(20261027)
        val refs = Random(20261028)
        repeat(60_000) {
            val kind = kinds[rnd.nextInt(kinds.size)]
            val st = TaperState(kind, rnd.nextBoolean(), 1 + rnd.nextInt(kind.types), open = true).example()
            if (flips.nextBoolean()) st.toggleFlip()
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
                st.shared("zClear").toDoubleOrNull() ?: 0.0,
                (1..n).map { st.point("r$it").toDoubleOrNull() ?: 0.0 },
                st.point("start").toDoubleOrNull(),
                st.point("end").toDoubleOrNull(),
                st.flip,
                angleRef = if (refs.nextBoolean()) AngleRef.X else AngleRef.Z,
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
                s.shownAngles(f.angleRef).forEach { show(it) }
                show(s.entry)
                show(s.exit)
                show(s.drawing.segs.first().start)
                show(s.drawing.segs.last().end)
                show(s.cutEnd)
            }
        }
        assertTrue(solved > 10_000, "결과가 나온 사례가 너무 적다: $solved")
    }

    /**
     * T1–T2 원호(피드백 2026-11-10): 홑테이퍼에 호를 켜고 점 · 모서리 R · 시작 · 끝 · R · 볼록/오목 · 노즈R 을 흔든다. 예외 0, 찍는 숫자 유한,
     * 경고 글 · 짚는 칸 · 최소 R 도 만들어 본다(화면이 그리는 것). 이 시험만의 난수라 위 [taper] 의 사례는 그대로다
     */
    @Test
    fun taperArc() {
        val shake = Random(20261110)
        var arcs = 0
        repeat(40_000) {
            val kind = if (shake.nextBoolean()) TaperKind.OUTER else TaperKind.INNER
            val st = TaperState(kind, shake.nextBoolean(), 1 + shake.nextInt(5), open = true).example()
            if (shake.nextBoolean()) st.toggleFlip()
            st.setArc(true)
            st.setArcConcave(shake.nextBoolean())
            // R: 흔한 값 · 가장 작은 R 언저리(예시 치수는 5) · 치는 중(빈칸 · 0 · 점 · 부호) · 극단 값
            st.setPoint(
                "arcR",
                when (shake.nextInt(10)) {
                    0 -> pick()
                    1 -> listOf("", "0", ".", "-", "0.", "-5")[shake.nextInt(6)]
                    2 -> listOf("5", "5.001", "4.999", "4.99", "3.536", "3.535")[shake.nextInt(6)]
                    else -> (shake.nextInt(3000, 60_000) / 1000.0).toString()
                },
            )
            val names = listOf("x1", "z1", "r1", "x2", "z2", "r2", "ang", "start", "end")
            for (name in names) if (shake.nextInt(4) == 0) st.setPoint(name, nudge(st.point(name)))
            if (shake.nextInt(3) == 0) st.setShared("noseR", nudge(st.shared("noseR")))
            if (shake.nextInt(5) == 0) st.setShared("zClear", nudge(st.shared("zClear")))
            if (shake.nextInt(5) == 0) st.tip = shake.nextInt(10)
            val f = SlopeForm(
                kind, st.type!!, st.reverse,
                st.shared("noseR").toDoubleOrNull() ?: Double.NaN,
                st.tip,
                (1..2).map { st.point("x$it").toDoubleOrNull() },
                (1..2).map { st.point("z$it").toDoubleOrNull() },
                st.point("ang").toDoubleOrNull(),
                st.shared("zClear").toDoubleOrNull() ?: 0.0,
                (1..2).map { st.point("r$it").toDoubleOrNull() ?: 0.0 },
                st.point("start").toDoubleOrNull(),
                st.point("end").toDoubleOrNull(),
                st.flip,
                angleRef = AngleRef.X,
                arc = st.arc,
                arcR = st.point("arcR").toDoubleOrNull(),
                arcConcave = st.arcConcave,
            )
            check("테이퍼 원호 $f") {
                f.arcMinR?.let { show(it) }
                f.waiting
                val res = TaperSolve.solve(f)
                res.warnings.forEach {
                    assertTrue(taperWarningText(it, f).isNotBlank())
                    taperWarningFields(it, f)
                }
                val s = res.solution ?: return@check
                arcs++
                val a = s.arc ?: fail("호가 없다")
                show(a.r)
                a.progR?.let { show(it) }
                show(a.center)
                s.points.forEach { p ->
                    show(p.vertex)
                    p.nodes.forEach { show(it.drawn); show(it.prog); show(it.dXDia, it.dZ) }
                    p.corner?.let { c -> show(c.r); c.progR?.let { show(it) } }
                }
                show(s.entry)
                show(s.exit)
                s.approach.forEach { show(it) }
                show(s.cutEnd)
                assertTrue((s.ncG40 + s.ncG41G42).none { "NaN" in it || "Infinity" in it })
            }
        }
        assertTrue(arcs > 5_000, "호 결과가 나온 사례가 너무 적다: $arcs")
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
            val st = roundExample(type, open = true)
            for (d in dims(type)) if (rnd.nextInt(3) == 0) st.setDim(type, d.key, nudge(st.dim(type, d.key)))
            if (rnd.nextInt(3) == 0) st.setShared("noseR", nudge(st.shared("noseR")))
            if (rnd.nextInt(5) == 0) st.setShared("leg", nudge(st.shared("leg")))
            if (rnd.nextInt(5) == 0) st.tip = rnd.nextInt(10)
            val i = st.input(type)
            check("라운드 $i") {
                // 기계 보정 프로그램(화면의 도면 좌표 NC — G00 을 띄움)도 같은 칸 값으로
                RoundCalc.machine(i).solution?.let { m -> show(m.entry); m.approach.forEach { show(it) } }
                val s = RoundCalc.calc(i).solution ?: return@check
                solved++
                s.nodes.forEach { show(it.drawn); show(it.prog) }
                s.arcs.forEach { a -> show(a.r); a.progR?.let { show(it) } }
                show(s.entry)
            }
            val m = roundExample(MANUAL, open = true).apply {
                inner = rnd.nextBoolean()
                for (name in listOf("x1", "z1", "x2", "z2", "x3", "z3")) if (rnd.nextInt(3) == 0) setPoint(name, nudge(point(name)))
                if (rnd.nextInt(3) == 0) setShared("noseR", nudge(shared("noseR")))
            }.arc3()
            check("3점호 $m") {
                RoundCalc.machine(m).solution?.let { d -> show(d.entry); d.approach.forEach { show(it) } }
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

    /**
     * 나사 종류 넷 가운데 관용 테이퍼(PT · NPT — 곧은 나사는 Stage3RobustnessTest.thread): 칸 글을 흔들어 계산기 · 화면 글 함수(경고 · 게이지 표 · 결과 칸 · G92 글)가
     * 예외 없이 유한한 숫자를 내는가. 크기 · 내외경 · 테이퍼 낱말도 무작위
     */
    @Test
    fun pipeThread() {
        val base = mapOf(
            "zRef" to "0", "zStart" to "5", "zEnd" to "-15", "rpm" to "500", "firstCut" to "0.3",
            "gaugeShift" to "0", "m" to "2", "minCut" to "0.035", "allow" to "0.02", "xClear" to "1",
        )
        var pipeSolved = 0
        repeat(20_000) {
            val family = if (rnd.nextBoolean()) ThreadFamily.PT else ThreadFamily.NPT
            val specs = PipeTable.of(family.pipeKind!!)
            val s = ThreadState(
                inner = rnd.nextBoolean(),
                family = family,
                pipe = specs[rnd.nextInt(specs.size)],
                // 칸마다 셋에 하나만 흔든다(Stage3RobustnessTest 와 같은 비율) — 다 흔들면 거의 늘 멈춤이라 결과 길을 못 지난다
                values = base.mapValues { (_, v) -> if (rnd.nextInt(3) == 0) nudge(v) else v },
                taperWord = if (rnd.nextBoolean()) 'I' else 'R',
            )
            check("관용 나사 ${s.pipe?.label} 내경 ${s.inner} ${base.keys.map { s.value(ThreadField.entries.first { f -> f.key == it }) }}") {
                val r = PipeThreadCalc.calc(s.pipeInput())
                val sol = r.solution
                r.warnings.forEach { threadWarningText(it, s, pipe = sol) }
                if (s.bad().isNotEmpty()) threadWarningText(G76Warning.BAD_INPUT, s)
                gaugeRows(s, sol)
                if (sol == null) return@check
                pipeSolved++
                pipeResultCells(s, sol)
                show(sol.x, sol.startX, sol.taper, sol.height, sol.crestAtRef, sol.crestAtEnd, sol.threadLen, sol.needLen)
                sol.passes.forEach { show(it.depth, it.cut, it.xDia) }
                assertTrue((sol.g92 + sol.g92Finish).none { "NaN" in it || "Infinity" in it }, "G92 글: ${sol.g92}")
            }
        }
        assertTrue(pipeSolved > 5_000, "결과가 나온 사례가 너무 적다: $pipeSolved")
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
                    TaperState(type = 1, open = true).example().apply {
                        setPoint("x2", v)
                        setPoint("r1", v)
                        setPoint("start", v)
                        setShared("noseR", tiny)
                    },
                )
            }
            // T1–T2 원호: R 칸 · 점 칸에 극단 값
            draw { TaperScreen(TaperState(type = 1, open = true).example().apply { setArc(true); setPoint("arcR", v) }) }
            draw { TaperScreen(TaperState(TaperKind.INNER, type = 3, open = true).example().apply { setArc(true); setArcConcave(true); setPoint("arcR", "10"); setPoint("x2", v) }) }
            draw { TaperScreen(TaperState(TaperKind.OUTER_DOUBLE, type = 1, open = true).example().apply { setPoint("z3", v) }) }
            draw { TaperScreen(TaperState(TaperKind.GROOVE, type = 1, open = true).example().apply { setShared("width", v) }) }
            draw { RoundScreen(roundExample(3, open = true).apply { setDim(3, "mainR", v) }) }
            draw { RoundScreen(roundExample(MANUAL, open = true).apply { setPoint("x2", v) }) }
            draw { TriangleScreen(TriangleState(mapOf("angA" to "25")).apply { set(TriField.A_SIDE, v) }) }
        }
        // T1–T2 원호의 R 을 치는 중간 상태(빈칸 · 0 · 점 · 부호) · 가장 작은 R 언저리 · R 과소 · 아주 큰 R, 볼록 · 오목, 두 보정 방식
        for (v in listOf("", "0", ".", "-", "0.", "5", "4.999", "4", "3.5", "9999", "10000.5")) for (concave in listOf(false, true)) {
            draw {
                TaperScreen(
                    TaperState(type = 1, open = true).example().apply {
                        setArc(true)
                        setArcConcave(concave)
                        setPoint("arcR", v)
                        setPoint("r1", "0.5")
                    },
                )
            }
        }
        // 좌표를 다 넣기 전(같은 점 · 수평 · 빈칸)
        for ((x2, z2) in listOf("40" to "0", "40" to "-5", "50" to "0", "" to "-5", "50" to "")) {
            draw {
                TaperScreen(
                    TaperState(type = 1, open = true).example().apply {
                        setArc(true)
                        setPoint("arcR", "10")
                        setPoint("x2", x2)
                        setPoint("z2", z2)
                    },
                )
            }
        }
    }
}
