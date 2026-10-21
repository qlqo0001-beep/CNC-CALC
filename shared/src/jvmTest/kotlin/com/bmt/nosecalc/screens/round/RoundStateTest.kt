package com.bmt.nosecalc.screens.round

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.BarField
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderHit
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderPose
import com.bmt.nosecalc.calc.HolderResult
import com.bmt.nosecalc.calc.HolderSkip
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.screens.taper.directNc
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.oneLine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 라운드 화면 상태 → 계산기 입력, 처음 값 · 예시 값, 경고 보이기, 그림 견본, Saver */
class RoundStateTest {
    private val numbersOnly = setOf(RoundWarning.CONCAVE_R_LE_NOSE, RoundWarning.AGAINST_TOOL)

    @Test
    fun examplesSolveEveryType() {
        val s = roundExample()
        for (t in 1..6) {
            val r = RoundCalc.calc(s.input(t))
            assertNotNull(r.solution, "형상 $t: ${r.warnings}")
            assertTrue(numbersOnly.containsAll(r.warnings), "형상 $t: ${r.warnings}")
        }
        val m = RoundCalc.arc3(s.arc3())
        assertNotNull(m.solution, "수동: ${m.warnings}")
    }

    /** 처음 값은 모두 0 — 형상마다 값 부족(파란 안내), 3점호는 세 점이 겹쳐 모양 불일치 대신 값 부족 */
    @Test
    fun freshStartIsMissingInput() {
        val s = RoundState()
        for (t in 1..6) {
            for (d in dims(t)) assertEquals("0", s.dim(t, d.key))
            assertEquals(listOf(RoundWarning.MISSING_INPUT), shownWarnings(RoundCalc.calc(s.input(t))), "형상 $t")
        }
        val input = s.arc3()
        assertEquals(listOf(RoundWarning.SHAPE_MISMATCH), RoundCalc.arc3(input).warnings)
        assertEquals(listOf(RoundWarning.MISSING_INPUT), shownWarnings(RoundCalc.arc3(input), input))
        assertEquals("0", s.shared("zClear"))
        assertEquals(0.0, s.input(3).zClear)
        assertEquals("0.4", s.shared("noseR"))
    }

    /** 값 부족 안내는 넣을 칸 이름을 든다: 꼭 넣을 칸 + 비울 수 있는 묶음에서 더 넣을 개수 */
    @Test
    fun missingTextNamesFields() {
        val s = RoundState()
        val empty = dims(2).map { it.key }.filter { s.input(2).dim(it) == null }.toSet()
        assertEquals(setOf("mainR", "d1", "l1", "l2"), empty)
        assertEquals("값을 넣으면 바로 계산합니다. 넣을 값: L1, 주 R · D1 · L2 가운데 둘.", roundWarningText(RoundWarning.MISSING_INPUT, 2, empty))
        assertEquals("값을 넣으면 바로 계산합니다. 넣을 값: 주 R · L.", roundWarningText(RoundWarning.MISSING_INPUT, 1, setOf("mainR", "l1")))
    }

    /** 길이 · 지름 · 주 R 의 0 은 빈칸과 같다(비울 수 있는 칸이면 자동). 모서리 R 빈칸은 0 */
    @Test
    fun zeroIsBlankAndBlankCornerIsZero() {
        val s = roundExample()
        s.setDim(3, "l1", "0")
        assertNull(s.input(3).l1)
        assertEquals(5.0, RoundCalc.calc(s.input(3)).solution!!.filled!!.l1!!, 1e-9)
        val blank = roundExample().apply { setDim(5, "cr1", "") }.input(5)
        assertEquals(0.0, blank.cornerR1)
        val r = RoundCalc.calc(blank)
        assertNotNull(r.solution)
        assertEquals(RoundCalc.calc(roundExample().apply { setDim(5, "cr1", "0") }.input(5)), r)
    }

    /** 빨간 칸: 모순은 비울 수 있는 묶음, 모서리 R 이 너무 크면 모서리 R 칸. 값 부족은 칸을 붉히지 않는다 */
    @Test
    fun warningFields() {
        assertEquals(setOf("mainR", "d1", "l2"), roundWarningFields(RoundWarning.CONTRADICTION, 2))
        assertEquals(setOf("cr1", "cr2"), roundWarningFields(RoundWarning.CORNER_R_TOO_BIG, 6))
        assertEquals(setOf("cr1"), roundWarningFields(RoundWarning.CORNER_R_TOO_BIG, 3))
        assertEquals(emptySet(), roundWarningFields(RoundWarning.MISSING_INPUT, 2))
        val r = RoundCalc.calc(roundExample().apply { setDim(2, "l2", "30") }.input(2))
        assertEquals(listOf(RoundWarning.CONTRADICTION), shownWarnings(r))
    }

    /** calc-design.md 의 손 계산(Ø20, SR12.5, CR1, 0.4R, 팁 3)이 예시 값의 형상 3 그대로 나온다. 비운 L 은 5 로 채워진다 */
    @Test
    fun type3ExampleIsHandCalc() {
        val sol = RoundCalc.calc(roundExample().input(3)).solution!!
        assertEquals(listOf("G00 X-0.8 Z1.", "G01 Z0.", "G03 X19.391 Z-4.87 R12.9", "G03 X20. Z-5.741 R1.4", "G01 Z-10.4"), sol.nc)
        assertEquals(5.0, sol.filled!!.l1!!, 1e-9)
    }

    /** 수동 예시 값: 어깨의 볼록 R10(X30 Z0 → X50 Z−10), G03, 90°. 노즈 0.4 팁 3 → 첫 점 X29.2 Z0, 끝 X50 Z−10.4, R10.4 */
    @Test
    fun manualExample() {
        val s = roundExample()
        val sol = RoundCalc.arc3(s.arc3()).solution!!
        val a = sol.arcs.single()
        assertEquals(10.0, a.r, 1e-3)
        assertEquals(10.4, a.progR!!, 1e-3)
        assertTrue(!a.cw)
        assertEquals(90.0, (sol.drawing.segs[a.index] as Arc).sweepDeg, 1e-3)
        assertEquals(29.2, sol.nodes.first().prog.xDia, 1e-3)
        assertEquals(-10.4, sol.nodes.last().prog.z, 1e-3)
        assertEquals(3, s.manualStandardTip())
        assertTrue(!s.manualReverse())
    }

    /** 그 형상의 칸만 계산기로 간다(다른 형상에서 넣은 값이 섞이지 않게). 노즈R 빈칸은 BAD_INPUT */
    @Test
    fun inputTakesOnlyThatTypesFields() {
        val s = roundExample()
        s.setDim(5, "d2", "30")
        val i = s.input(3)
        assertNull(i.d2)
        assertNull(i.cornerR2)
        assertNull(i.l2)
        assertEquals(20.0, i.d1)
        s.setShared("noseR", "")
        assertEquals(listOf(RoundWarning.BAD_INPUT), RoundCalc.calc(s.input(3)).warnings)
    }

    /** 결과 띠 · 키패드 한 줄: 지금 모드의 노드 좌표(처음 · 끝 · 외 n점), 멈추면 경고 첫 문장, 값이 모자라면 없음 */
    @Test
    fun dock() {
        val r = RoundCalc.calc(roundExample().input(1))
        val comp = roundDock(r.solution, shownWarnings(r), 1, comp = true, name = Color.Unspecified)
        // 형상 1 의 바이트 반대쪽 면은 파란 안내라 '확인 필요' 로 세지 않는다
        assertTrue(comp is DockStatus.Ok)
        assertEquals("P1 X-0.800 Z0.000 · P6 X-0.800 Z-12.800 · 외 4점", comp.oneLine())
        assertEquals("P1 X0.000 Z0.000 · P6 X0.000 Z-12.000 · 외 4점", roundDock(r.solution, shownWarnings(r), 1, comp = false, name = Color.Unspecified)!!.oneLine())
        // 보링바가 닿으면 확인할 것 하나 더
        assertEquals(1, (roundDock(r.solution, shownWarnings(r), 1, comp = true, name = Color.Unspecified, holderContact = true) as DockStatus.Warn).count)
        val bad = RoundCalc.calc(roundExample().apply { setDim(2, "l2", "30") }.input(2))
        assertEquals(DockStatus.Error("치수가 서로 맞지 않습니다(0.001 mm 넘게)."), roundDock(bad.solution, shownWarnings(bad), 2, comp = true, name = Color.Unspecified))
        val fresh = RoundCalc.calc(RoundState().input(3))
        assertNull(roundDock(fresh.solution, shownWarnings(fresh), 3, comp = true, name = Color.Unspecified))
    }

    /** 그림은 견본 치수로 만든 윤곽을 그린다 — 풀리지 않으면 카드가 빈다. 내경 2~6 · 관통 5 · 6 도 */
    @Test
    fun artSamplesSolve() {
        for (t in 1..6) assertNotNull(RoundCalc.calc(sampleInput(t)).solution, "형상 $t 그림 견본")
        for (t in 2..6) {
            for (through in if (t >= 5) listOf(false, true) else listOf(false)) {
                val sol = RoundCalc.calc(sampleInput(t, inner = true, through = through)).solution
                assertNotNull(sol, "내경 형상 $t 관통 $through 그림 견본")
                assertEquals(Side.G41, sol.side)
            }
        }
    }

    /** 내경: 형상 2~6 은 내경 · 팁 null(표준 2) · 입구 구멍 깊이, 관통은 5 · 6 만. 외경으로 돌리면 관통을 켜 둬도 외경 */
    @Test
    fun innerInput() {
        val s = roundInnerExample(3).apply { through = true }
        for (t in 2..6) {
            val i = s.input(t)
            assertTrue(i.inner, "형상 $t")
            assertNull(i.tip, "형상 $t")
            assertEquals(10.0, i.legLen)
            assertEquals(t >= 5, i.through, "형상 $t")
        }
        // calc-design 의 내경 손 계산(Ø20 입구 깊이 10, SR12.5, CR1, 0.4R): 팁 2 · G41
        val sol = RoundCalc.calc(s.input(3)).solution!!
        assertEquals(2, sol.tip)
        assertEquals(Side.G41, sol.side)
        assertEquals(listOf("G00 X20. Z0.6", "G01 Z-0.4", "G01 Z-10.059", "G03 X19.739 Z-10.432 R0.6", "G03 X0.8 Z-15. R12.1"), sol.nc)
        s.typeInner = false
        val outer = s.input(5)
        assertFalse(outer.inner)
        assertFalse(outer.through)
        assertEquals(3, RoundCalc.calc(outer).solution!!.tip)
        // 형상 1 은 내경이 없다(고르기 화면에 없음) — 스위치가 켜져 있어도 외경으로 푼다
        assertFalse(roundInnerExample(1).input(1).inner)
    }

    /** 팁 번호는 외경 · 내경 따로(표준이 3 · 2 로 다르다) — 외경에서 고른 팁이 내경 보링으로 넘어가지 않는다 */
    @Test
    fun tipPerSide() {
        val s = roundExample(3, open = true)
        s.tip = 4
        assertEquals(4, s.input(3).tip)
        s.typeInner = true
        assertNull(s.tip)
        assertNull(s.input(3).tip)
        assertEquals(2, RoundCalc.calc(s.input(3)).solution!!.tip)
        s.tip = 6
        assertEquals(6, s.input(3).tip)
        s.typeInner = false
        assertEquals(4, s.input(3).tip)
    }

    /** 보링바 칸: D · f · 모양 · 날 길이 · κr 가 다 숫자여야 바. 돌출 길이 · DMIN 은 비우면 null */
    @Test
    fun holderBarParsing() {
        val s = RoundState()
        assertNull(s.holderBar())
        mapOf("dia" to "16", "f" to "11", "shape" to "C", "edge" to "9", "kr" to "95").forEach { (k, v) -> s.setHolder(k, v) }
        assertEquals(BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0), s.holderBar())
        s.setHolder("ovh", "40")
        s.setHolder("dmin", "20")
        assertEquals(BoringBar(16.0, 11.0, InsertShape.C, 9.0, 95.0, overhang = 40.0, dMin = 20.0), s.holderBar())
        s.setHolder("ovh", "")
        assertNull(s.holderBar()!!.overhang)
        for (k in listOf("dia", "f", "shape", "edge", "kr")) {
            val keep = s.holder(k)
            s.setHolder(k, "")
            assertNull(s.holderBar(), k)
            // 키패드에서 '−' 만 누른 칸(새 음수 시작)도 아직 숫자가 아니다
            s.setHolder(k, "-")
            assertNull(s.holderBar(), k)
            s.setHolder(k, keep)
        }
        assertNotNull(s.holderBar())
    }

    /** 프리셋 28 줄: 출처(https) · 노즈R 0.4 · 0.8 에서 맞는 값. 고르면 D · f · DMIN · κr · 모양 · 날 길이를 채우고(돌출 길이는 그대로), 고치면 수정됨 */
    @Test
    fun presets() {
        assertEquals(28, HOLDER_PRESETS.size)
        assertEquals(HOLDER_PRESETS.size, HOLDER_PRESETS.map { it.code }.distinct().size)
        for (p in HOLDER_PRESETS) {
            assertTrue(p.url.startsWith("https://"), p.code)
            for (r in listOf(0.4, 0.8)) assertEquals(emptySet(), HolderCheck.problems(p.bar, r), "${p.code} 노즈R $r")
        }
        val s = RoundState()
        s.setHolder("ovh", "40")
        s.pickPreset(HOLDER_PRESETS.single { it.code == "A16R-SDUCR 07-R" })
        assertEquals(listOf("16", "11", "20", "93", "D", "7"), listOf("dia", "f", "dmin", "kr", "shape", "edge").map(s::holder))
        assertEquals(BoringBar(16.0, 11.0, InsertShape.D, 7.0, 93.0, overhang = 40.0, dMin = 20.0), s.holderBar())
        assertEquals("www.sandvik.coromant.com", s.preset!!.host)
        assertFalse(s.presetEdited())
        s.setHolder("f", "8")
        assertTrue(s.presetEdited())
        // 직접 입력으로 돌려도 칸 값은 그대로
        s.pickPreset(null)
        assertNull(s.preset)
        assertFalse(s.presetEdited())
        assertEquals("8", s.holder("f"))
        assertEquals("107.5", RoundState().apply { pickPreset(HOLDER_PRESETS.single { it.code == "A16R-SDQCR 07" }) }.holder("kr"))
    }

    /** 칸 오류 글: f 가 모자라면 f 하한(바 윗면이 노즈보다 낮게) */
    @Test
    fun barFieldTexts() {
        val bar = BoringBar(16.0, 8.0, InsertShape.C, 9.0, 95.0)
        assertEquals(setOf(BarField.F), HolderCheck.problems(bar, 0.4))
        // fMin = D/2 + r(1 − cos κr) = 8 + 0.4 × 1.0872
        assertEquals("f 는 8.435 이상이어야 합니다 (바 윗면이 노즈보다 낮게)", barFieldText(BarField.F, bar, 0.4))
        assertEquals(setOf(BarField.KAPPA), HolderCheck.problems(bar.copy(f = 11.0, kappaDeg = 100.0), 0.4))
        assertEquals("절입각 κr 은 90° 이상, 100° 보다 작아야 합니다", barFieldText(BarField.KAPPA, bar.copy(kappaDeg = 100.0), 0.4))
    }

    /**
     * 보링바 띠: 내경 손 계산 돔(바 Ø16 · f 11 · C 9.67 · κr 95°)은 구 원호(요소 2)에서 바 · 인서트가 맞은편 벽에 닿는다.
     * 줄 번호는 NC 카드 그대로(1부터, 직접 보정은 첫 줄 주석까지), 블록은 그 줄 글, 좌표는 직접 보정 = 인선 · 기계 보정 = 도면 점
     */
    @Test
    fun holderBanner() {
        val s = roundInnerExample(3)
        val input = s.input(3)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val bar = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, 0.4, true, bar)
        val hit = res.hits.first { it.part == HolderPart.BAR }
        assertTrue(abs(hit.first.tip.z + 11.409) < 3e-3 && abs(hit.first.tip.xDia - 18.006) < 3e-3, "${hit.first.tip}")
        val direct = directNc(sol.nc, 0.4, sol.tip)
        val comp = holderNote(res, bar, sol, drawn, comp = true, direct)!!
        assertTrue(comp.contact)
        assertEquals("보링바가 소재에 닿습니다 — 2곳", comp.text)
        assertEquals(setOf(5), comp.flagged)
        val p = hit.first.tip
        assertEquals(
            "6줄 G03 X0.8 Z-15. R12.1 — 바가 맞은편 벽에 닿음: 처음 X${Fmt.fixed3(p.xDia)} Z${Fmt.fixed3(p.z)}, " +
                "반경 겹침 최대 ${Fmt.fixed3(hit.depth)} mm (인선에서 1.6 mm 뒤)",
            comp.lines[0],
        )
        assertTrue(comp.lines[1].startsWith("6줄 G03 X0.8 Z-15. R12.1 — 인서트가 맞은편 벽에 닿음: 처음 X"), comp.lines[1])
        val machine = holderNote(res, bar, sol, drawn, comp = false, drawn.nc)!!
        assertEquals(setOf(4), machine.flagged)
        val q = hit.first.drawn
        assertTrue(machine.lines[0].startsWith("5줄 G03 X0. Z-15. R12.5 — 바가 맞은편 벽에 닿음: 처음 X${Fmt.fixed3(q.xDia)} Z${Fmt.fixed3(q.z)}, "), machine.lines[0])
        // 둘 다 같은 G40 요소 · 같은 NC 블록을 가리킨다
        assertEquals(4, sol.lineOf(2, p))
        assertEquals(4, drawn.lineOf(2, q))
    }

    /** 닿지 않으면 가장 좁은 곳(파란 안내), 바 값이 없으면 넣으라는 안내, 값이 틀리면 띠 없음(칸이 빨갛다), 되돌아가는 원호는 검사 안 함 */
    @Test
    fun holderNotes() {
        val input = roundInnerStep().input(5)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val bar = BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0)
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, 0.4, true, bar)
        assertTrue(res.hits.isEmpty())
        val n = drawn.lineOf(res.barClearElement!!, res.barClearAt!!.drawn) + 1
        val clear = holderNote(res, bar, sol, drawn, comp = false, drawn.nc)!!
        assertFalse(clear.contact)
        assertEquals("보링바가 소재에 닿지 않습니다. 바와 구멍 벽 사이 가장 좁은 곳 2.970 mm (${n}줄).", clear.text)
        assertTrue(clear.lines.isEmpty() && clear.flagged.isEmpty())
        assertEquals("보링바 값을 넣으면 공구가 소재에 닿는지 봅니다.", holderNote(null, null, sol, drawn, comp = false, drawn.nc)!!.text)
        val bad = bar.copy(f = 8.0)
        assertNull(holderNote(HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, 0.4, true, bad), bad, sol, drawn, comp = false, drawn.nc))
        val loop = HolderResult(HolderSkip.NOT_MONOTONE, emptyList(), null, null, null)
        assertEquals("원호가 Z 로 되돌아가는 모양이라 홀더 검사를 하지 않습니다.", holderNote(loop, bar, sol, drawn, comp = false, drawn.nc)!!.text)
    }

    /** 띠는 4 줄까지, 나머지는 '외 n곳'. 최소 가공경은 바 · 인서트 닿음 뒤에 */
    @Test
    fun holderBannerOverflow() {
        val s = roundInnerExample(3).withA16()
        val input = s.input(3)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val bar = s.holderBar()!!
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, 0.4, true, bar)
        assertEquals(5, res.hits.size, "${res.hits.map { it.element to it.part }}")
        val note = holderNote(res, bar, sol, drawn, comp = false, drawn.nc)!!
        assertEquals("보링바가 소재에 닿습니다 — 5곳", note.text)
        assertEquals(5, note.lines.size)
        assertEquals("외 1곳", note.lines.last())
        assertTrue(note.lines[0].contains("바가 맞은편 벽") && note.lines[1].contains("인서트가 맞은편 벽"), "${note.lines}")
        assertTrue(note.lines.subList(2, 4).all { it.contains("최소 가공경 Ø20 보다 좁은 곳에 헤드가 들어감: 처음 X") }, "${note.lines}")
        assertEquals(setOf(2, 3, 4), note.flagged)
    }

    /**
     * 뒤보링(3점호 역방향 내경)은 이송이 +Z 라 인선보다 +Z 쪽(바 쪽)이 '앞'. 화면 예 X50 Z−10 → X30 Z0 에 Ø40 · f 25 바 —
     * 바가 입구 Ø30 을 못 지나 진입부터 닿는다(인선보다 11 mm 앞), 입구에 거의 다 와서는 바 앞 끝이 인선보다 조금 뒤에서
     */
    @Test
    fun backBoringSaysAhead() {
        val hit = HolderHit(
            0, HolderPart.BAR, HolderPose(Pt(-5.0, 10.0), Pt(-4.6, 9.6), Pt(-5.0, 10.0)), HolderPose(Pt(-1.0, 12.0), Pt(-0.6, 11.6), Pt(-1.0, 12.0)),
            HolderPose(Pt(-1.0, 12.0), Pt(-0.6, 11.6), Pt(-1.0, 12.0)), 0.2, 1.0, 0.5, true, 2.04,
        )
        val bar = BoringBar(40.0, 25.0, InsertShape.C, 9.0, 95.0)
        assertTrue(holderHitText(hit, 3, 3, "G03 X30. Z0. R10.", comp = true, bar, reverse = false).endsWith("(인선에서 2.0 mm 뒤)"))
        assertTrue(holderHitText(hit, 3, 3, "G03 X30. Z0. R10.", comp = true, bar, reverse = true).endsWith("(인선에서 2.0 mm 앞)"))
        assertTrue(holderHitText(hit.copy(behind = -0.3), 3, 3, "G03 X30. Z0. R10.", comp = true, bar, reverse = true).endsWith("(인선에서 0.3 mm 뒤)"))

        val input = roundManualInnerRev().arc3()
        val sol = RoundCalc.arc3(input).solution!!
        val drawn = RoundCalc.arc3(input.copy(noseR = 0.0)).solution!!
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, 0.4, false, bar)
        assertTrue(res.hits.isNotEmpty() && res.hits.all { it.part == HolderPart.BAR } && res.hits.any { it.behind > 1 }, "${res.hits.map { it.part to it.behind }}")
        val note = holderNote(res, bar, sol, drawn, comp = false, drawn.nc)!!
        assertEquals(res.hits.size, note.lines.size)
        for ((h, line) in res.hits.zip(note.lines)) assertTrue(line.endsWith("mm ${if (h.behind > 0) "앞" else "뒤"})"), "${h.behind} $line")
    }

    /** 형상 확인의 빨간 경로 구간: 직접 보정은 가상 인선, 기계 보정은 도면 좌표 경로에서 노즈가 닿는 도면 점 사이(✕ 표와 같은 곳) */
    @Test
    fun contactBandFollowsShownPath() {
        val s = roundInnerExample(3).withA16()
        val input = s.input(3)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, 0.4, true, s.holderBar()!!)
        for (h in res.hits.filter { it.element >= 0 }) {
            val comp = contactBand(sol, h, comp = true)!!.segs.single()
            assertTrue((comp.start - h.first.tip).length < 1e-9 && (comp.end - h.last.tip).length < 1e-9, "${h.element} ${h.part}")
            val machine = contactBand(drawn, h, comp = false)!!.segs.single()
            assertTrue((machine.start - h.first.drawn).length < 1e-9 && (machine.end - h.last.drawn).length < 1e-9, "${h.element} ${h.part}")
        }
    }

    @Test
    fun stateSurvivesSaver() {
        val s = RoundState(type = 2, open = true)
        s.setDim(2, "l2", "12")
        s.l2Ref = L2Ref.A
        s.inner = true
        s.typeInner = true
        // 팁은 외경 · 내경 따로 — 내경 쪽
        s.tip = 2
        s.through = true
        s.setPoint("x1", "41.5")
        s.pickPreset(HOLDER_PRESETS.single { it.code == "S16MSDUCR07" })
        s.setHolder("ovh", "45")
        val back = with(RoundState.Saver) { restore(SaverScope { true }.save(s)!!)!! }
        assertEquals(2, back.type)
        assertTrue(back.open)
        assertEquals("12", back.dim(2, "l2"))
        assertEquals(L2Ref.A, back.l2Ref)
        assertEquals(2, back.tip)
        assertTrue(back.inner)
        assertTrue(back.typeInner)
        assertTrue(back.through)
        assertEquals("41.5", back.point("x1"))
        assertEquals("S16MSDUCR07", back.preset?.code)
        assertEquals(s.holderBar(), back.holderBar())
        assertEquals(45.0, back.holderBar()!!.overhang)
        back.tip = null
        assertNull(back.tip)
        assertTrue(abs(back.input(2).noseR - 0.4) < 1e-12)
    }
}
