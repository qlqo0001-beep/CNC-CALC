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
import com.bmt.nosecalc.calc.HolderSkip
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RetractKind
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

    /** 예 A(돔 + 바 A) 풀이 · 계획. safe = 안전 여유 칸 글 */
    private class DomeA(safe: String = "0.5") {
        val s = roundInnerDomeA().apply { setHolder("safe", safe) }
        val input = s.input(3)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val bar = s.holderBar()!!
        val plan = HolderStop.plan(sol, drawn, 0.4, true, bar, s.holderSafe())
    }

    /**
     * 간섭 목록: 예 A 는 구 원호(요소 2)에서 바 · 인서트가 맞은편 벽에 닿는다(calc-design 표). 줄은 NC 카드 그대로(1부터, 직접 보정은 첫 줄 주석까지),
     * 좌표는 직접 보정 = 가상 인선 · 기계 보정 = 노즈가 닿는 도면 점(도면 명령 좌표), 닿기 시작 → 끝
     */
    @Test
    fun hitList() {
        val a = DomeA()
        val hit = a.plan.result.hits.first { it.part == HolderPart.BAR }
        assertTrue(abs(hit.first.tip.z + 11.409) < 3e-3 && abs(hit.first.tip.xDia - 18.006) < 3e-3, "${hit.first.tip}")
        val comp = hitRows(a.plan, comp = true, a.bar, reverse = false)
        assertEquals(listOf(HolderPart.BAR, HolderPart.INSERT), comp.map { it.hit.part })
        assertEquals(5, comp[0].line)
        assertEquals(
            "6줄 바 · 맞은편 벽: ${xz(hit.first.tip)} → ${xz(hit.last.tip)}, 반경 겹침 최대 ${Fmt.fixed3(hit.depth)} mm (인선에서 1.6 mm 뒤)",
            comp[0].text,
        )
        assertTrue(comp[1].text.startsWith("6줄 인서트 · 맞은편 벽: X"), comp[1].text)
        val machine = hitRows(a.plan, comp = false, a.bar, reverse = false)
        assertEquals(4, machine[0].line)
        assertEquals(hit.first.drawn, machine[0].from)
        assertEquals(hit.last.drawn, machine[0].to)
        assertTrue(machine[0].text.startsWith("5줄 바 · 맞은편 벽: ${xz(hit.first.drawn)} → "), machine[0].text)
        // 둘 다 같은 G40 요소 · 같은 NC 블록을 가리킨다
        assertEquals(4, a.sol.lineOf(2, hit.first.tip))
        assertEquals(4, a.drawn.lineOf(2, hit.first.drawn))
    }

    /** 목록은 바 · 인서트 · 공구대 닿음을 먼저, 최소 가공경(입체 어림)은 뒤에 — 프리셋 A16R-SCLCR 09(DMIN 20) */
    @Test
    fun hitListMinBoreLast() {
        val s = roundInnerExample(3).withA16()
        val input = s.input(3)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val bar = s.holderBar()!!
        val rows = hitRows(HolderStop.plan(sol, drawn, 0.4, true, bar, s.holderSafe()), comp = false, bar, reverse = false)
        assertEquals(5, rows.size, "${rows.map { it.hit.part }}")
        assertEquals(listOf(HolderPart.BAR, HolderPart.INSERT), rows.take(2).map { it.hit.part })
        assertTrue(rows.drop(2).all { it.hit.part == HolderPart.MIN_BORE && it.text.contains("최소 가공경 Ø20: X") && it.detail.startsWith("헤드가 들어가는 구멍이 반경 ") }, "${rows.map { it.text }}")
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
        assertTrue(hitDetail(hit, reverse = false).endsWith("(인선에서 2.0 mm 뒤)"))
        assertTrue(hitDetail(hit, reverse = true).endsWith("(인선에서 2.0 mm 앞)"))
        assertTrue(hitDetail(hit.copy(behind = -0.3), reverse = true).endsWith("(인선에서 0.3 mm 뒤)"))

        val bar = BoringBar(40.0, 25.0, InsertShape.C, 9.0, 95.0)
        val input = roundManualInnerRev().arc3()
        val sol = RoundCalc.arc3(input).solution!!
        val drawn = RoundCalc.arc3(input.copy(noseR = 0.0)).solution!!
        val plan = HolderStop.plan(sol, drawn, 0.4, false, bar)
        val res = plan.result
        assertTrue(res.hits.isNotEmpty() && res.hits.all { it.part == HolderPart.BAR } && res.hits.any { it.behind > 1 }, "${res.hits.map { it.part to it.behind }}")
        val rows = hitRows(plan, comp = false, bar, reverse = backBoring(sol))
        // 기계 보정 목록 = 그 프로그램 자기 진입(G00 점 · 보정 시작 블록)에서 닿는 곳 + 형상 요소에서 닿는 곳(직접 보정과 같은 노즈 경로)
        assertEquals(plan.machineEntry!!.size + res.hits.count { it.element >= 0 }, rows.size)
        for (row in rows) assertTrue(row.text.endsWith("mm ${if (row.hit.behind > 0) "앞" else "뒤"})"), "${row.hit.behind} ${row.text}")
        // 기계 보정의 진입(−2 G00 점 · −1 보정 시작 블록) 좌표는 그 프로그램 자기 진입의 명령점 — 뒤보링 진입점은 이미 구멍 안이라
        // 도면 첫 점(Z−10)이 아니라 제 G00 블록 Z−11 에서 닿고, 시작 블록에서는 블록 위 비율의 명령점
        assertEquals(-2, rows.first().hit.element)
        assertTrue(rows.first().text.startsWith("1줄 바 · 깎는 쪽 벽: X50.000 Z-11.000 → "), rows.first().text)
        for (row in rows.filter { it.hit.element == -1 }) {
            val p = drawn.entry + (drawn.program.segs.first().start - drawn.entry) * row.hit.fromT
            assertTrue((row.from - p).length < 1e-9, "${row.from} $p")
        }
        // 진입점부터 닿으니 깎을 수 없다 — 잘린 NC 없음
        assertEquals(StopKind.UNSAFE, stopView(plan, false, sol, drawn, 0.4, bar).kind)
    }

    /**
     * 가공 가능 끝점 · 잘린 NC — 예 A, 안전 여유 0(calc-design 손 계산): 구 원호를 바 맞은편 여유 0 이 되는 Z−11.404 X18.014 에서 끊고, 노즈가 +Z 로 바로
     * 떨어져 Z 로만 뺀다. 직접 보정 `G03 X18.014 Z-11.404 R12.1` → `G00 Z0.6`(6줄 — 첫 줄 주석), 기계 보정 `G03 X17.785 Z-11.285 R12.5` → `G40 G00 X18.016 Z1.`(5줄 —
     * G40 은 끝 블록 법선 보정으로 선 노즈의 인선 자리)
     */
    @Test
    fun stopDomeSafe0() {
        val a = DomeA("0")
        val d = stopView(a.plan, comp = true, a.sol, a.drawn, 0.4, a.bar)
        assertEquals(StopKind.CUT, d.kind)
        assertEquals(5, d.endLine)
        assertEquals("G03 X18.014 Z-11.404 R12.1", d.nc!![5])
        assertEquals(listOf("G00 Z0.6"), d.nc.slice(d.retractLines!!))
        assertEquals("(G40 DIRECT R0.4 T2)", d.nc.first())
        // 앞 줄은 원래 NC 그대로
        assertEquals(directNc(a.sol.nc, 0.4, 2).take(5), d.nc.take(5))
        assertEquals("X18.014 Z-11.404", xz(d.at!!))
        assertEquals("6줄 중간에서 끊음", d.where)
        assertTrue(d.near!!.startsWith("가장 가까운 곳: 바 · 맞은편 벽 — 반경 여유 0.000 mm (기준: 안전 여유 0 mm)"), d.near)
        assertTrue(d.retract!!.startsWith("후퇴: 그 자리에서 Z 로 (노즈가 벽에서 바로 떨어짐): G00 Z0.6 · 후퇴 중 가장 좁은 반경 여유 "), d.retract)
        val m = stopView(a.plan, comp = false, a.sol, a.drawn, 0.4, a.bar)
        assertEquals(StopKind.CUT, m.kind)
        assertEquals(4, m.endLine)
        assertEquals("G03 X17.785 Z-11.285 R12.5", m.nc!![4])
        assertEquals(listOf("G40 G00 X18.016 Z1."), m.nc.slice(m.retractLines!!))
        assertEquals("5줄 중간에서 끊음", m.where)
        assertEquals("X17.785 Z-11.285", xz(m.at!!))
    }

    /**
     * 예 A, 안전 여유 0.5: 축 쪽으로 빼면 바 맞은편이 0.5 아래라 온 길을 거꾸로(같은 중심의 반대 방향 원호). 후퇴 길을 다시 읽으면 진입점으로 돌아가고
     * 원호는 프로그램 원호와 같은 중심이다(뷰어의 파란 점선)
     */
    @Test
    fun stopDomeReverse() {
        val a = DomeA()
        val d = stopView(a.plan, comp = true, a.sol, a.drawn, 0.4, a.bar)
        assertEquals("G03 X19.001 Z-10.874 R12.1", d.nc!![d.endLine!!])
        assertEquals(listOf("G02 X19.739 Z-10.432 R12.1", "G02 X20. Z-10.059 R0.6", "G01 Z-0.4", "G01 Z0.6"), d.nc.slice(d.retractLines!!))
        assertTrue(d.retract!!.startsWith("후퇴: 온 길을 거꾸로 이송으로 진입점까지 (4줄)"), d.retract)
        val back = ncMoves(d.at!!, d.nc.slice(d.retractLines!!))
        assertEquals(4, back.size)
        assertTrue((back.last().end - a.sol.entry).length < 1e-3, "${back.last().end}")
        val arcs = back.filterIsInstance<Arc>()
        assertEquals(2, arcs.size)
        assertTrue(arcs.all { it.cw }, "$arcs")
        assertTrue((arcs[0].center - (a.sol.program.segs[2] as Arc).center).length < 2e-3, "${arcs[0].center}")
        assertTrue((arcs[1].center - (a.sol.program.segs[1] as Arc).center).length < 2e-3, "${arcs[1].center}")
        val m = stopView(a.plan, comp = false, a.sol, a.drawn, 0.4, a.bar)
        assertEquals("G03 X18.803 Z-10.738 R12.5", m.nc!![m.endLine!!])
        assertEquals("G40 G01 X19.001 Z-10.875", m.nc[m.retractLines!!.first])
    }

    /**
     * 끝까지 안전(관통 단차 + A16 — 가장 좁은 곳 2.970) · 깎기 전에 멈춤(돔 + A16 의 DMIN 20 = 구멍 Ø20, 안전 여유 0.5) · 안전 여유 칸
     * (처음 0.5, 비우면 0.5, 0 은 됨, 음수는 칸이 빨갛고 검사 안 함, Saver 로 남음)
     */
    @Test
    fun stopKinds() {
        val step = roundInnerStep().withA16()
        val input = step.input(5)
        val sol = RoundCalc.calc(input).solution!!
        val drawn = RoundCalc.calc(input.copy(noseR = 0.0)).solution!!
        val bar = step.holderBar()!!
        val plan = HolderStop.plan(sol, drawn, 0.4, true, bar, step.holderSafe())
        assertTrue(plan.result.hits.isEmpty() && plan.end == null)
        val n = drawn.lineOf(plan.result.barClearElement!!, plan.result.barClearAt!!.drawn) + 1
        val clear = stopView(plan, comp = false, sol, drawn, 0.4, bar)
        assertEquals(StopKind.CLEAR, clear.kind)
        assertEquals("끝까지 안전합니다\n안전 여유 0.5 mm 를 지키며 경로 끝까지 깎습니다. 바와 구멍 벽 사이 가장 좁은 반경 여유 2.970 mm (${n}줄).", clear.text)
        assertNull(clear.nc)

        val dome = roundInnerExample(3).withA16()
        val di = dome.input(3)
        val dsol = RoundCalc.calc(di).solution!!
        val ddrawn = RoundCalc.calc(di.copy(noseR = 0.0)).solution!!
        val dplan = HolderStop.plan(dsol, ddrawn, 0.4, true, dome.holderBar()!!, dome.holderSafe())
        for (comp in listOf(false, true)) {
            val v = stopView(dplan, comp, dsol, ddrawn, 0.4, dome.holderBar()!!)
            assertEquals(StopKind.NOTHING, v.kind)
            assertNull(v.nc)
            assertTrue(v.near!!.startsWith("가장 가까운 곳: 최소 가공경 Ø20 — "), v.near)
            assertNotNull(v.at)
        }

        val s = RoundState()
        assertEquals("0.5", s.holder("safe"))
        assertEquals(0.5, s.holderSafe())
        s.setHolder("safe", "")
        assertEquals(0.5, s.holderSafe())
        s.setHolder("safe", "0")
        assertEquals(0.0, s.holderSafe())
        assertTrue(HolderStop.safeOk(s.holderSafe()))
        s.setHolder("safe", "-0.3")
        assertFalse(HolderStop.safeOk(s.holderSafe()))
        assertEquals(HolderSkip.BAD_INPUT, HolderStop.plan(sol, drawn, 0.4, true, bar, s.holderSafe()).result.skip)
        s.setHolder("safe", "0.8")
        // 프리셋은 안전 여유를 바꾸지 않는다
        s.pickPreset(HOLDER_PRESETS.first())
        assertEquals("0.8", with(RoundState.Saver) { restore(SaverScope { true }.save(s)!!)!! }.holder("safe"))
    }

    /**
     * 안전 끝점 글이 어떤 결과에서도 예외 없이 나온다(Fmt 는 무한대 · NaN 에서 멈춘다 — 면을 넘는 순간의 여유가 끝없는 곳 등):
     * 내경 예시 형상 2~6 · 3점호 입구 R · 뒤보링 × 프리셋 28 × 안전 여유 0 · 0.5 × 두 보정 방식, 후퇴 길 읽기는 진입점 Z 까지
     */
    @Test
    fun holderStopTextsNeverThrow() {
        val states = (2..6).map { t -> roundInnerExample(t) to t } + listOf(roundManualInnerMouth() to MANUAL, roundManualBackBoring() to MANUAL)
        var cuts = 0
        for ((st, t) in states) {
            val (sol, drawn, face) = if (t == MANUAL) {
                val a = st.arc3()
                Triple(RoundCalc.arc3(a).solution!!, RoundCalc.arc3(a.copy(noseR = 0.0)).solution, false)
            } else {
                val i = st.input(t)
                Triple(RoundCalc.calc(i).solution!!, RoundCalc.calc(i.copy(noseR = 0.0)).solution, true)
            }
            for (p in HOLDER_PRESETS) for (safe in listOf(0.0, 0.5)) {
                val plan = HolderStop.plan(sol, drawn, 0.4, face, p.bar, safe)
                if (plan.result.skip != null) continue
                for (comp in listOf(false, true)) {
                    val rows = hitRows(plan, comp, p.bar, backBoring(sol))
                    rows.forEach { it.text }
                    val v = stopView(plan, comp, sol, drawn, 0.4, p.bar)
                    v.text
                    val nc = v.nc ?: continue
                    cuts++
                    val back = ncMoves(v.at!!, nc.slice(v.retractLines!!))
                    // 기계 보정의 거꾸로 후퇴는 G40 뒤라 인선 경로(직접 보정 진입점)로, X 빼기 후퇴는 제 진입점 Z 로
                    val entryZ = if (comp || v.cut!!.retract == RetractKind.REVERSE) sol.entry.z else drawn!!.entry.z
                    assertTrue(back.isNotEmpty() && abs(back.last().end.z - entryZ) < 1e-3, "${p.code} $t $safe $comp ${nc.slice(v.retractLines!!)}")
                }
            }
        }
        assertTrue(cuts > 20, "잘린 NC 가 나온 경우 $cuts")
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
