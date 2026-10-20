package com.bmt.nosecalc

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import com.bmt.nosecalc.screens.taper.SlopeForm
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperSolve
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.cutEnd
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.taper.signed
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 화면에 나오는 숫자·NC 지킴이. 스크린샷 상태마다 화면과 똑같이 계산기 입력을 만들어 경고·좌표(Fmt.fixed3)·NC 를 글로 적고
 * golden/<이름>.txt 와 맞춰 본다. 화면을 다듬다가 숫자나 NC 가 한 글자라도 바뀌면 여기서 걸린다.
 * 일부러 바꿨을 때만 다시 쓴다: `gradlew :shared:jvmTest --tests '*UiGoldenTest*' -DupdateGolden=true`
 */
class UiGoldenTest {
    private val dir = File("src/jvmTest/resources/golden")
    private val update = System.getProperty("updateGolden") == "true"

    private fun golden(name: String, text: String) {
        val f = File(dir, "$name.txt")
        if (update) {
            dir.mkdirs()
            f.writeText(text)
            return
        }
        assertTrue(f.exists(), "골든 파일이 없다: $f (-DupdateGolden=true 로 만든다)")
        // 윈도우에서 줄끝이 CRLF 로 꺼내져도 같게 본다
        assertEquals(f.readText().replace("\r\n", "\n"), text, "$name: 화면 숫자·NC 가 골든과 다르다")
    }

    /** 화면은 도면 치수 0 에서 시작하므로 [example] 값(2단계 처음 값)을 넣은 상태 — 스크린샷과 같다 */
    @Test
    fun taper() {
        slope("taper-b", TaperState(type = 1, open = true).example())
        slope(
            "taper-b-corner",
            TaperState(type = 1, open = true).example().apply {
                setPoint("r1", "1")
                setPoint("start", "30")
                ncTab = 1
            },
        )
        slope(
            "taper-b-outer3",
            TaperState(type = 3, open = true).example().apply {
                setPoint("r2", "0.5")
                setPoint("end", "-20")
            },
        )
        slope("taper-b-inner1", TaperState(TaperKind.INNER, type = 1, open = true).example().apply { setPoint("end", "-20") })
        slope(
            "taper-b-auto",
            TaperState(type = 1, open = true).example().apply {
                setPoint("z2", "")
                setPoint("ang", "34.1")
            },
        )
        slope("taper-b-reverse", TaperState(TaperKind.INNER, reverse = true, type = 1, open = true).example())
        slope("taper-b-double", TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example().apply { setPoint("r2", "1") })
        slope("taper-b-error", TaperState(type = 3, open = true).example().apply { setPoint("ang", "30") })
    }

    @Test
    fun groove() {
        groove("taper-groove", TaperState(TaperKind.GROOVE, type = 1, open = true).example())
        groove("taper-groove-error", TaperState(TaperKind.GROOVE, type = 2, open = true).example().apply { setShared("insert", "9") })
    }

    @Test
    fun round() {
        // 화면은 0 에서 시작한다 — 스크린샷과 같은 예시 치수로
        for (t in 1..6) roundType("round-b$t", roundExample(t, open = true), t)
        roundType("round-b3-contact", roundExample(3, open = true, comp = false), 3)
        roundType("round-b2-error", roundExample(2, open = true).apply { setDim(2, "l2", "30") }, 2)
        roundType("round-b3-empty", RoundState(type = 3, open = true), 3)
        manual("round-manual", roundExample(MANUAL, open = true))
        manual("round-manual-inner-rev", roundManualInnerRev())
        manual("round-manual-empty", RoundState(type = MANUAL, open = true))
    }

    @Test
    fun triangle() {
        triangle("triangle-empty", TriangleState())
        triangle("triangle", TriangleState(mapOf("a" to "10", "angA" to "25")))
        triangle("triangle-swap", TriangleState(mapOf("b" to "12", "c" to "13"), baseIsZ = false))
        triangle("triangle-steep", TriangleState(mapOf("a" to "3", "b" to "12")))
        triangle("triangle-error", TriangleState(mapOf("angA" to "30", "angB" to "60")))
    }

    /** SlopeScreen 의 입력 → SlopeForm 그대로 */
    private fun slope(name: String, s: TaperState) {
        val kind = s.kind
        val type = s.type!!
        val n = kind.points
        val form = SlopeForm(
            kind, type, s.reverse,
            s.shared("noseR").toDoubleOrNull() ?: Double.NaN,
            s.tip,
            (1..n).map { s.point("x$it").toDoubleOrNull() },
            (1..n).map { s.point("z$it").toDoubleOrNull() },
            s.point("ang").toDoubleOrNull(),
            s.shared("zClear").toDoubleOrNull() ?: 0.0,
            (1..n).map { s.point("r$it").toDoubleOrNull() ?: 0.0 },
            s.point("start").toDoubleOrNull(),
            s.point("end").toDoubleOrNull(),
        )
        val r = TaperSolve.solve(form)
        val text = buildString {
            line("표준 팁 T${NoseComp.standardTip(kind.inner, s.reverse)} · ${Side.of(kind.inner, s.reverse)}")
            line("경고 ${r.warnings}")
            val sol = r.solution ?: return@buildString
            line("팁 T${sol.tip} · ${sol.side} · 시작 요소 깎음 ${sol.fromStart} · 끝 요소 깎음 ${sol.toEnd}")
            sol.points.forEachIndexed { i, p ->
                line("T${i + 1} 꼭짓점 ${xz(p.vertex)}")
                p.nodes.forEach { line("  도면 ${xz(it.drawn)} → 보정 ${xz(it.prog)}  ΔX ${signed(it.dXDia)} ΔZ ${signed(it.dZ)}") }
                p.corner?.let {
                    line("  모서리 ${if (it.cw) "G02" else "G03"} R${f(it.r)} → 보정 R${f(it.progR)} 중심 ${xz(it.center)}")
                }
            }
            line("각도 ${sol.anglesDeg.map(::f)}")
            line("진입점 ${xz(sol.entry)} 후퇴점 ${xz(sol.exit)}")
            line("도면 끝 ${xz(sol.drawing.segs.last().end)} · 깎는 끝 ${xz(sol.cutEnd)}")
            line("NC G41/G42")
            sol.ncG41G42.forEach { line("  $it") }
            line("NC G40")
            sol.ncG40.forEach { line("  $it") }
        }
        golden(name, text)
    }

    /** GrooveScreen 의 입력 → GrooveInput 그대로 */
    private fun groove(name: String, s: TaperState) {
        fun v(key: String) = s.shared(key)
        val input = GrooveInput(
            zStart = v("zStart").toDoubleOrNull(),
            width = v("width").toDoubleOrNull(),
            od = v("od").toDoubleOrNull(),
            bottom = v("bottom").toDoubleOrNull(),
            insert = v("insert").toDoubleOrNull(),
            overlap = v("overlap").toDoubleOrNull() ?: 0.5,
            ref = if (s.type == 1) GrooveRef.LEFT else GrooveRef.RIGHT,
            xClear = v("xClear").toDoubleOrNull() ?: 0.0,
        )
        val r = GrooveCalc.calc(input)
        val text = buildString {
            line("경고 ${r.warnings}")
            val sol = r.solution ?: return@buildString
            line("단 ${sol.steps.size} · 피치 ${f(sol.pitch)} · 빠짐 X${f(sol.retractXDia)}")
            sol.steps.forEachIndexed { i, st -> line("  ${i + 1}  Z${f(st.z)}  X${f(st.xDia)}") }
            line("정삭 ${(listOf(sol.finish.segs.first().start) + sol.finish.segs.map { it.end }).map(::xz)}")
            line("NC")
            sol.nc.forEach { line("  $it") }
        }
        golden(name, text)
    }

    /** RoundTypeScreen: RoundState.input(type), 도면 좌표 NC = 노즈R 0 */
    private fun roundType(name: String, s: RoundState, type: Int) {
        val input = s.input(type)
        roundText(name, RoundCalc.calc(input), RoundCalc.calc(input.copy(noseR = 0.0)).solution?.nc, "팁 T${input.tip}")
    }

    /** ManualScreen: RoundState.arc3(), 도면 좌표 NC = 노즈R 0 */
    private fun manual(name: String, s: RoundState) {
        val input = s.arc3()
        roundText(name, RoundCalc.arc3(input), RoundCalc.arc3(input.copy(noseR = 0.0)).solution?.nc, "표준 팁 T${s.manualStandardTip()}")
    }

    private fun roundText(name: String, r: RoundResult, drawnNc: List<String>?, head: String) {
        val text = buildString {
            line(head)
            line("경고 ${r.warnings}")
            val sol = r.solution ?: return@buildString
            sol.filled?.let { fi ->
                line("치수 R${f(fi.mainR)} D1 ${f(fi.d1)} D2 ${f(fi.d2)} L1 ${f(fi.l1)} L2 ${f(fi.l2)}")
            }
            line("팁 T${sol.tip} · ${sol.side}")
            sol.nodes.forEachIndexed { i, n -> line("P${i + 1} 도면 ${xz(n.drawn)} → 보정 ${xz(n.prog)}") }
            sol.arcs.forEach { a ->
                val sweep = (sol.drawing.segs[a.index] as Arc).sweepDeg
                line("원호 ${a.index} ${if (a.cw) "G02" else "G03"} R${f(a.r)} → 보정 R${f(a.progR)} 중심 ${xz(a.center)} 각 ${f(sweep)}")
            }
            line("진입점 ${xz(sol.entry)}")
            line("NC 보정")
            sol.nc.forEach { line("  $it") }
            line("NC 도면 좌표")
            drawnNc?.forEach { line("  $it") }
        }
        golden(name, text)
    }

    private fun triangle(name: String, s: TriangleState) {
        val r = RightTriangle.calc(s.input())
        val text = buildString {
            line("경고 ${r.warnings}")
            val sol = r.solution ?: return@buildString
            TriField.entries.forEach { line("${it.label} ${f(it.of(sol))}") }
            line("X${f(sol.dxDia)} (반경 ${f(sol.dxDia / 2)}) Z${f(sol.dz)}")
        }
        golden(name, text)
    }

    private fun f(v: Double?) = v?.let(Fmt::fixed3) ?: "-"
    private fun xz(p: Pt) = "X${f(p.xDia)} Z${f(p.z)}"
    private fun StringBuilder.line(s: String) = append(s).append('\n')
}
