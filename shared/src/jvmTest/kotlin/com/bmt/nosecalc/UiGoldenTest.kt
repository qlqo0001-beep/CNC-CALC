package com.bmt.nosecalc

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.holderNote
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundInnerStep
import com.bmt.nosecalc.screens.round.roundManualInnerMouth
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import com.bmt.nosecalc.screens.round.withA16
import com.bmt.nosecalc.screens.taper.SlopeForm
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperSolve
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.cutEnd
import com.bmt.nosecalc.screens.taper.directNc
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
 * NC 는 화면 · 복사 그대로다 — 직접 보정(G40) 프로그램은 첫 줄 주석 `(G40 DIRECT R0.4 T3)`([directNc])까지.
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
        roundType("round-b3-contact", roundExample(3, open = true), 3)
        roundType("round-b2-error", roundExample(2, open = true).apply { setDim(2, "l2", "30") }, 2)
        roundType("round-b3-empty", RoundState(type = 3, open = true), 3)
        manual("round-manual", roundExample(MANUAL, open = true))
        manual("round-manual-inner-rev", roundManualInnerRev())
        manual("round-manual-empty", RoundState(type = MANUAL, open = true))
    }

    /** 내경 라운드와 보링바 띠 글 · 빨간 NC 줄(스크린샷과 같은 상태): 손 계산 돔(바 없음 · 프리셋 A16R-SCLCR 09), 관통 단차(안 닿음), 3점호 입구 R */
    @Test
    fun roundInner() {
        roundType("round-inner-b3", roundInnerExample(3), 3)
        roundType("round-inner-b3-holder", roundInnerExample(3).withA16(), 3)
        roundType("round-inner-b5-through-holder-clear", roundInnerStep().withA16(), 5)
        manual("round-manual-inner-holder", roundManualInnerMouth().withA16())
    }

    @Test
    fun triangle() {
        triangle("triangle-empty", TriangleState())
        triangle("triangle", TriangleState(mapOf("a" to "10", "angA" to "25")))
        triangle("triangle-swap", TriangleState(mapOf("b" to "12", "c" to "13"), baseIsZ = false))
        triangle("triangle-steep", TriangleState(mapOf("a" to "3", "b" to "12")))
        triangle("triangle-error", TriangleState(mapOf("angA" to "30", "angB" to "60")))
        // 12자리 · 89.999° 는 한도(MAX_MM)에서 멈춘다(예전엔 Long 끝값으로 잘린 숫자), 3-4-5 셋은 서로 맞아 풀린다
        triangle("triangle-huge", TriangleState(mapOf("a" to "999999999999", "angA" to "89.999")))
        triangle("triangle-three", TriangleState(mapOf("a" to "3", "b" to "4", "c" to "5")))
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
            directNc(sol.ncG40, form.noseR, sol.tip).forEach { line("  $it") }
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
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution
        val tip = input.tip ?: NoseComp.standardTip(input.inner, false)
        roundText(name, RoundCalc.calc(input), drawn, input.noseR, if (input.inner) "내경 팁 T$tip" else "팁 T$tip", s.takeIf { input.inner }, faceAtStart = true)
    }

    /** ManualScreen: RoundState.arc3(), 도면 좌표 NC = 노즈R 0 */
    private fun manual(name: String, s: RoundState) {
        val input = s.arc3()
        roundText(name, RoundCalc.arc3(input), RoundCalc.arc3(input.copy(noseR = 0.0)).solution, input.noseR, "표준 팁 T${s.manualStandardTip()}", s.takeIf { input.inner }, faceAtStart = false)
    }

    /** 보링바(내경에서 바 값이 있을 때): 화면의 holderNote 그대로 — 기계 보정 · 직접 보정의 띠 글과 빨간 NC 줄(1부터) */
    private fun StringBuilder.holderText(s: RoundState, sol: RoundSolution, drawn: RoundSolution?, noseR: Double, faceAtStart: Boolean) {
        val bar = s.holderBar() ?: return
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, noseR, faceAtStart, bar)
        line("보링바 $bar")
        for (comp in listOf(false, true)) {
            val nc = if (comp) directNc(sol.nc, noseR, sol.tip) else drawn?.nc ?: continue
            val note = holderNote(res, bar, sol, drawn, comp, nc) ?: continue
            line(if (comp) "띠 직접 보정" else "띠 기계 보정")
            line("  ${note.text}")
            note.lines.forEach { line("  · $it") }
            line("  빨간 줄 ${note.flagged.sorted().map { it + 1 }}")
        }
    }

    /** inner = 내경 화면의 상태(보링바 칸이 있다), 외경은 null */
    private fun roundText(name: String, r: RoundResult, drawn: RoundSolution?, noseR: Double, head: String, inner: RoundState?, faceAtStart: Boolean) {
        val drawnNc = drawn?.nc
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
            directNc(sol.nc, noseR, sol.tip).forEach { line("  $it") }
            line("NC 도면 좌표")
            drawnNc?.forEach { line("  $it") }
            inner?.let { holderText(it, sol, drawn, noseR, faceAtStart) }
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
