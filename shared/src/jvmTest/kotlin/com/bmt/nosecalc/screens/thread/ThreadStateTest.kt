package com.bmt.nosecalc.screens.thread

import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.PipeInput
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSeries
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.UnGroup
import androidx.compose.runtime.saveable.SaverScope
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 나사 화면 상태: 입력 만들기 · 빈칸 · 범위 밖 칸 짚기 · 저장 · G76 낱말 · 단면 그림이 계산과 같은지 */
class ThreadStateTest {
    /** 원본 08 화면 = calc-design.md 손 계산: 화면 낱말을 이으면 계산기 NC 글 그대로 */
    @Test
    fun originalScreenNumbers() {
        val s = threadOriginal()
        val r = G76Calc.calc(s.input())
        val sol = assertNotNull(r.solution)
        assertEquals(emptyList(), r.warnings)
        assertEquals(listOf("G97S1821", "G0X8.322Z5.", "G76P021060Q35R.02", "G76X4.688Z-25.P734Q200F1.27"), sol.program)
        val words = g76Words(s, sol)
        assertEquals(sol.nc, words.map(::ncText))
        // 고칠 수 있는 칸: 첫 줄 m · Δdmin · d, 둘째 줄 Z · Δd — 나머지는 자동(주황)
        assertEquals(
            listOf(ThreadField.FINISH_COUNT, null, ThreadField.MIN_CUT, ThreadField.ALLOW, null, ThreadField.Z_END, null, ThreadField.FIRST_CUT, null),
            words.flatten().map { it.field },
        )
        // 비운 가공 값(m · Δdmin · d)은 기본값 표시
        assertTrue(words[0].filter { it.field != null }.all { it.byDefault })
        assertFalse(words[1].any { it.byDefault })
    }

    /** 처음은 모두 빈칸: 파란 안내의 '남은 것' 넷, 둘째 줄의 Z · Q 는 파란 '?' 이고 첫 줄은 기본값 그대로 */
    @Test
    fun emptyWaits() {
        val s = ThreadState()
        val r = G76Calc.calc(s.input())
        assertEquals(listOf(G76Warning.MISSING_INPUT), r.warnings)
        assertEquals(listOf("사이즈", "Z축 시작점", "가공 목표점 (Z)", "첫 절입 Δd"), s.missing())
        assertEquals(emptySet(), s.bad())
        val words = g76Words(s, null)
        assertEquals("G76P021060Q35R.02", ncText(words[0]))
        assertEquals("G76X—Z?P—Q?F—", ncText(words[1]))
        assertTrue(words[1].filter { it.letter == "Z" || it.letter == "Q" }.all { it.missing })
        assertEquals(ThreadClass.M6g, s.shownClass)
        assertEquals(60, s.shownAngle)
        assertEquals(10, s.shownChamfer)
    }

    /** 칸마다 범위 밖 값: 화면이 짚는 칸(bad)이 있으면 계산기가 BAD_INPUT, 없으면 BAD_INPUT 이 아니다 */
    @Test
    fun badFieldsMatchCalc() {
        val values = listOf("-1", "0", "0.0004", "0.0005", "0.5", "1", "1.5", "2.5", "99", "100", "100.5", "120", "20000", "100000", "100001", "-20000")
        var checked = 0
        for (f in ThreadField.entries) for (v in values) for (inner in listOf(false, true)) {
            val s = threadOriginal()
            s.switchInner(inner)
            s.set(f, v)
            val w = G76Calc.calc(s.input()).warnings
            assertEquals(s.bad().isNotEmpty(), G76Warning.BAD_INPUT in w, "$f = $v (내경 $inner): ${s.bad()} / $w")
            if (s.bad().isNotEmpty()) assertEquals(setOf(f), s.bad())
            checked++
        }
        assertTrue(checked > 300)
    }

    /** 멈춘 경고가 짚는 칸 */
    @Test
    fun flaggedFields() {
        val s = threadZOrder()
        val w = G76Calc.calc(s.input()).warnings
        assertEquals(listOf(G76Warning.Z_ORDER), w)
        assertEquals(setOf(ThreadField.Z_START, ThreadField.Z_END), s.flagged(w))
        val few = threadOriginal(values = mapOf("firstCut" to "0.01"))
        val w2 = G76Calc.calc(few.input()).warnings
        assertEquals(listOf(G76Warning.FIRST_CUT_LT_MIN), w2)
        assertEquals(setOf(ThreadField.FIRST_CUT, ThreadField.MIN_CUT), few.flagged(w2))
    }

    /** 스크린샷 상태가 뜻한 경고를 낸다(노란 둘 · 빨간 Z 순서 · 역가공은 경고 없음 · 4h 는 식) */
    @Test
    fun exampleStates() {
        assertEquals(listOf(G76Warning.ROOT_INTERFERES, G76Warning.ONE_ROUGH_PASS), G76Calc.calc(threadWarn().input()).warnings)
        assertEquals(emptyList(), G76Calc.calc(threadReverse().input()).warnings)
        assertTrue(G76Calc.calc(threadFormula().input()).solution!!.limits.byFormula)
        assertFalse(G76Calc.calc(threadOriginal().input()).solution!!.limits.byFormula)
        val bad = threadBadWhileWaiting()
        assertEquals(listOf(G76Warning.MISSING_INPUT), G76Calc.calc(bad.input()).warnings)
        assertEquals(setOf(ThreadField.TARGET), bad.bad())
    }

    /** 외경/내경 · 나사 종류를 바꾸면 그에 딸린 급수는 기본으로, 종류를 바꾸면 사이즈도 비운다. 사이즈를 고르면 보통 나사(UNC)가 먼저 */
    @Test
    fun choicesReset() {
        val s = threadOriginal()
        s.pickClass(ThreadClass.UN3A)
        s.switchInner(true)
        assertNull(s.cls)
        assertEquals(ThreadClass.UN2B, s.shownClass)
        s.switchFamily(ThreadFamily.METRIC)
        assertNull(s.spec)
        assertEquals(ThreadClass.M6H, s.shownClass)
        s.pickSize("M12")
        assertEquals(ThreadSeries.M_COARSE, s.spec?.series)
        assertEquals(1.75, s.spec?.pitch)
        // 같은 사이즈를 다시 고르면 고른 가는 피치가 그대로
        val fine = com.bmt.nosecalc.calc.ThreadTable.pitches(ThreadKind.METRIC, "M12").last()
        s.pickSpec(fine)
        s.pickSize("M12")
        assertEquals(fine, s.spec)
    }

    /**
     * UNEF · 20UN 크기 고르기: 지금 묶음(UNC)에 없는 11/16 은 UNC · UNF · UNEF · UN 차례의 첫 줄(UNEF 24)이 들어가고 묶음이 UNEF 로 따라간다,
     * 3/8 의 피치 시트는 UNC 16 · UNF 24 · UNEF 32 · 20UN 20 차례. 20UN 과 겹치는 줄의 이름은 "3/4-20 UNEF (20UN)", 일정 피치 줄은 "3/8-20 UN"
     * (사용자 결정 2026-11-05). 고른 1A 는 1A 가 없는 UNEF 로 가면 기본(2A)으로 돌아간다.
     * 3/4-20 UNEF 2A 의 G76 둘째 줄 = calc G76CalcTest.unefHandNumbers 손 계산(X17.369 · k 0.741 — Z 끝만 이 상태의 −25)
     */
    @Test
    fun unefAnd20UnSizes() {
        val s = threadOriginal()
        s.pickSize("11/16")
        assertEquals("11/16-24 UNEF", s.spec?.label)
        assertEquals(
            listOf("24 산" to "UNEF · 피치 1.058 mm · 11/16-24 UNEF", "20 산" to "20UN · 피치 1.270 mm · 11/16-20 UN"),
            ThreadTable.pitches(ThreadKind.UN, "11/16").map(::pitchRow),
        )
        assertEquals(listOf("16 산", "24 산", "32 산", "20 산"), ThreadTable.pitches(ThreadKind.UN, "3/8").map { pitchRow(it).first })
        assertEquals("3/8-20 UN", specTitle(ThreadTable.pitches(ThreadKind.UN, "3/8").last()))
        assertEquals("1/4-20 UNC (20UN)", specTitle(threadOriginal().spec!!))
        assertEquals("1/4-28 UNF", specTitle(ThreadTable.pitches(ThreadKind.UN, "1/4")[1]))
        assertEquals(UnGroup.UNEF, s.unGroup)
        s.unGroup = UnGroup.UNC
        s.pickSize("3/4")
        assertEquals(ThreadSeries.UNC, s.spec?.series)
        s.pickClass(ThreadClass.UN1A)
        val unef = ThreadTable.pitches(ThreadKind.UN, "3/4").single { it.tpi == 20 }
        s.pickSpec(unef)
        assertEquals("3/4-20 UNEF (20UN)", specTitle(unef))
        assertEquals("20 산" to "UNEF · 피치 1.270 mm · 3/4-20 UNEF (20UN)", pitchRow(unef))
        assertNull(s.cls)
        assertEquals(ThreadClass.UN2A, s.shownClass)
        assertEquals(listOf("2A", "3A"), s.classChoices.map { it.label })
        val sol = assertNotNull(G76Calc.calc(s.input()).solution)
        assertEquals("G76X17.369Z-25.P741Q200F1.27", sol.nc[1])
        // 3A 는 UNEF 에도 있어 그대로, 사이즈를 고르기 전(spec 없음)의 급수 시트는 그 종류의 급수 전부
        s.pickClass(ThreadClass.UN3A)
        s.pickSpec(ThreadTable.pitches(ThreadKind.UN, "13/16").single { it.tpi == 20 })
        assertEquals(ThreadClass.UN3A, s.cls)
        assertEquals(listOf("1A", "2A", "3A"), ThreadState(family = ThreadFamily.UN).classChoices.map { it.label })
        // 만들 때 · 되살릴 때도 계열에 없는 급수는 기본으로 — 그 상태로 한계 치수 상자(ThreadTol.limits)를 그려도 예외가 없다
        // (무작위 상태 1-1/8-20 UN + 1A 가 Stage3RobustnessTest.drawRandom 을 깼던 회귀)
        val made = ThreadState(family = ThreadFamily.UN, spec = ThreadTable.pitches(ThreadKind.UN, "1-1/8").single { it.tpi == 20 }, cls = ThreadClass.UN1A)
        assertNull(made.cls)
        assertEquals(ThreadClass.UN2A, made.shownClass)
        com.bmt.nosecalc.calc.ThreadTol.limits(made.spec!!, made.shownClass!!)
        assertEquals(ThreadClass.UN1A, ThreadState(family = ThreadFamily.UN, spec = threadOriginal().spec, cls = ThreadClass.UN1A).cls)
    }

    /** 저장 · 되살리기: 모든 칸 · 고른 것(NC 탭 포함 — 처음 값은 G92 다회)이 그대로 */
    @Test
    fun saverRoundTrip() {
        assertEquals(ThreadNc.G92, ThreadState().nc)
        val s = threadWarn().apply {
            pickClass(ThreadClass.UN3A)
            angle = 55
            chamfer = 15
            nc = ThreadNc.G76
        }
        val scope = SaverScope { true }
        val saved = with(ThreadState.Saver) { scope.save(s) }!!
        val back = ThreadState.Saver.restore(saved)!!
        assertEquals(s.input(), back.input())
        assertEquals(s.material, back.material)
        assertEquals(s.advOpen, back.advOpen)
        assertEquals(ThreadNc.G76, back.nc)
        ThreadField.entries.forEach { assertEquals(s.value(it), back.value(it)) }
    }

    /** 자리표시는 계산기의 기본값 그대로, 빈칸이면 그 값으로 푼다 */
    @Test
    fun placeholdersAreDefaults() {
        val s = threadOriginal()
        val sol = assertNotNull(G76Calc.calc(s.input()).solution)
        assertEquals("기본 80", s.placeholder(ThreadField.TARGET))
        assertEquals(80.0, sol.target)
        assertEquals("기본 0.035", s.placeholder(ThreadField.MIN_CUT))
        assertEquals(0.035, sol.minCut)
        assertEquals("기본 0.02", s.placeholder(ThreadField.ALLOW))
        assertEquals(0.02, sol.finishAllow)
        assertEquals("기본 2", s.placeholder(ThreadField.FINISH_COUNT))
        assertEquals(2, sol.finishCount)
        assertEquals("기본 ${com.bmt.nosecalc.calc.Fmt.fixed3(sol.tipR)}", s.placeholder(ThreadField.TIP_R))
        assertEquals("", s.placeholder(ThreadField.Z_START))
        assertEquals("", s.placeholder(ThreadField.FIRST_CUT))
    }

    /**
     * 단면 그림이 계산과 같은 형상: 피치선(산봉우리에서 crestToPitch)의 홈 반폭이 P/4(피치경의 뜻), 골은 산봉우리에서 k.
     * 외경 · 내경 · 넣은 인선 R(≤ H/3) 모두, 그리고 값이 없을 때의 견본도
     */
    @Test
    fun sectionMatchesCalc() {
        for (s in listOf(threadOriginal(), threadMetricInner(), threadFormula(), threadOriginal(values = mapOf("tipR" to "0.2")))) {
            val sol = assertNotNull(G76Calc.calc(s.input()).solution)
            val g = sectionGeom(s.inner, sol)
            // k 는 μm 로 반올림한 지령값이라 0.001 안에서
            assertTrue(abs(toolHalfWidth(g.tipR, g.k - g.crestToPitch) - g.p / 4) < 0.001, "${s.spec?.label}: ${toolHalfWidth(g.tipR, g.k - g.crestToPitch)} vs ${g.p / 4}")
            assertEquals(g.k, sol.height)
            assertEquals(sol.passes.size - sol.finishCount, g.depths.size)
        }
        for (inner in listOf(false, true)) {
            val g = sectionGeom(inner, null)
            assertTrue(abs(toolHalfWidth(g.tipR, g.k - g.crestToPitch) - 0.25) < 1e-9)
            assertEquals(if (inner) 0.541266 else 0.613435, g.k, 1e-6)
        }
        assertEquals(sqrt(3.0) / 2 / 6, defaultTipR(threadOriginal().spec!!, false) / threadOriginal().spec!!.pitch, 1e-12)
    }

    /**
     * 종류 넷(사용자 결정 2026-11-05): 종류를 바꾸면 사이즈(곧은 나사 · 관용 테이퍼 둘 다) · 급수를 비우고 숫자칸은 그대로. 관용 테이퍼에는 급수가 없고(null),
     * PT 이름은 외경 R · 내경 Rc 로 따라간다. 곧은 나사 칸(공차 목표 · 인선 R)은 관용 테이퍼 화면에 없고, 거기서 넣은 값은 범위 밖으로 짚지 않는다
     */
    @Test
    fun familySwitchClears() {
        assertEquals(listOf("M (미터)", "UN (유니파이)", "PT (관용 테이퍼)", "NPT (미국 관용 테이퍼)"), ThreadFamily.entries.map(::kindText))
        val s = threadOriginal().apply { pickClass(ThreadClass.UN3A) }
        s.set(ThreadField.TARGET, "120")
        assertEquals(setOf(ThreadField.TARGET), s.bad())
        s.switchFamily(ThreadFamily.PT)
        assertNull(s.spec)
        assertNull(s.cls)
        assertNull(s.pipe)
        assertNull(s.shownClass)
        assertEquals(emptyList(), s.classChoices)
        assertEquals("5.0", s.value(ThreadField.Z_START))
        assertEquals(emptySet(), s.bad())
        assertEquals(listOf("사이즈"), s.missing())
        assertFalse(ThreadField.TARGET in s.fields || ThreadField.TIP_R in s.fields)
        assertTrue(ThreadField.Z_REF in s.fields && ThreadField.GAUGE_SHIFT in s.fields)
        assertFalse(ThreadField.Z_REF in threadOriginal().fields || ThreadField.GAUGE_SHIFT in threadOriginal().fields)
        s.pickPipe(PipeTable.PT.single { it.size == "1/2" })
        assertEquals("R 1/2", s.pipe?.name(s.inner))
        s.switchInner(true)
        assertEquals("Rc 1/2", s.pipe?.name(s.inner))
        s.switchFamily(ThreadFamily.NPT)
        assertNull(s.pipe)
        s.switchFamily(ThreadFamily.UN)
        assertNull(s.pipe)
        assertEquals(ThreadClass.UN2B, s.shownClass)
        // 같은 종류를 다시 고르면 그대로
        s.pickSize("1/2")
        s.switchFamily(ThreadFamily.UN)
        assertEquals("1/2", s.spec?.size)
    }

    /**
     * 20UN 묶음의 격자: 자주 쓰는 다섯(1/4 · 3/8 · 1/2 · 3/4 · 1 — 사용자 결정 2026-11-05)이 먼저, 나머지 32 크기는 지름 순(H28 표 2.16 의 37 크기가 한 번씩).
     * 20UN 에서 3/4 를 고르면 3/4-20 UNEF 이고 이름은 "3/4-20 UNEF (20UN)", 3/8 은 일정 피치 줄 "3/8-20 UN". 다른 묶음의 격자는 지름 순.
     * 피치 시트에서 다른 묶음의 줄을 고르면 묶음이 따라가고, 같은 크기를 다시 고르면 고른 줄 그대로
     */
    @Test
    fun un20Grid() {
        assertEquals(listOf("1/4", "3/8", "1/2", "3/4", "1"), COMPANY_20UN)
        val grid = unSizes(UnGroup.UN20)
        assertEquals(COMPANY_20UN, grid.take(5))
        assertEquals(37, grid.size)
        assertEquals(grid.size, grid.toSet().size)
        val rest = grid.drop(5)
        // 지름 순 = 인치 값 순(분수 이름을 따로 풀어 견줌 — "1-3/16" = 1 + 3/16)
        fun inch(n: String): Double {
            val (w, f) = if ('-' in n) n.substringBefore('-') to n.substringAfter('-') else if ('/' in n) "0" to n else n to "0/1"
            val (a, c) = f.split('/').map { it.toDouble() }
            return w.toDouble() + a / c
        }
        assertEquals(rest.sortedBy(::inch), rest)
        assertEquals("5/16", rest.first())
        assertEquals("3", rest.last())
        assertEquals(ThreadTable.group(UnGroup.UNEF).map { it.size }, unSizes(UnGroup.UNEF))
        assertEquals("#12", unSizes(UnGroup.UNEF).first())

        val s = ThreadState(family = ThreadFamily.UN)
        s.unGroup = UnGroup.UN20
        s.pickSize("3/4")
        assertEquals("3/4-20 UNEF", s.spec?.label)
        assertEquals("3/4-20 UNEF (20UN)", specTitle(s.spec!!))
        assertEquals(UnGroup.UN20, s.unGroup)
        s.pickSize("3/8")
        assertEquals("3/8-20 UN", specTitle(s.spec!!))
        s.pickSize("1")
        assertEquals("1-20 UNEF (20UN)", specTitle(s.spec!!))
        // UNC 묶음에서 3/4 → UNC 10, 피치 시트에서 UNF 16 을 고르면 묶음이 UNF 로, 3/4 를 다시 고르면 UNF 16 그대로
        s.unGroup = UnGroup.UNC
        s.pickSize("3/4")
        assertEquals("3/4-10 UNC", s.spec?.label)
        s.pickSpec(ThreadTable.pitches(ThreadKind.UN, "3/4").single { it.tpi == 16 })
        assertEquals(UnGroup.UNF, s.unGroup)
        s.pickSize("3/4")
        assertEquals("3/4-16 UNF", s.spec?.label)
        // 20UN 줄(3/4-20 UNEF)은 UNEF · 20UN 두 묶음에 든다 — 20UN 묶음이면 그대로
        s.unGroup = UnGroup.UN20
        s.pickSpec(ThreadTable.pitches(ThreadKind.UN, "3/4").single { it.tpi == 20 })
        assertEquals(UnGroup.UN20, s.unGroup)
    }

    /**
     * 관용 테이퍼 입력: 화면 칸 → PipeInput 칸이 하나씩 맞는다(빈칸은 null — 계산기가 기준면 · 옮김 0, 가공 값은 기본값). 정삭 횟수가 정수가 아니면 0(→ BAD_INPUT).
     * R 3/4 숫나사 손 계산(지은 치수 — 기준면 0 · Z5 → −15 · Δd 0.35): P = 25.4/14 = 1.8142857, h = 0.640327P = 1.1617361, 게이지 면 Zg = −a = −9.53,
     * 게이지 피치경 = d − h = 26.441 − 1.1617361 = 25.2792639, E(−15) = 25.2792639 + (−9.53 + 15)/16 = 25.6211389, 끝 골 X = E − h = 24.4594028 → 24.459,
     * k = 1.162, 시작 X = E + h + 2 × 1 = 28.782875 → 28.783, I = −(5 + 15)/32 = −.625, 첫 회 X = 24.459 + 2(1.162 − 0.35) = 26.083
     */
    @Test
    fun pipeInputFields() {
        val s = ThreadState(
            inner = true, family = ThreadFamily.NPT, pipe = PipeTable.NPT.single { it.size == "1" }, taperWord = 'R',
            values = mapOf(
                "zRef" to "-1.5", "zStart" to "3", "zEnd" to "-30", "rpm" to "350", "gaugeShift" to "-0.5", "firstCut" to "0.25",
                "minCut" to "0.04", "allow" to "0.03", "m" to "3", "xClear" to "0.8", "target" to "50", "tipR" to "0.3",
            ),
        )
        assertEquals(
            PipeInput(
                inner = true, spec = PipeTable.NPT.single { it.size == "1" }, zRef = -1.5, zStart = 3.0, zEnd = -30.0, rpm = 350.0, gaugeShift = -0.5,
                firstCut = 0.25, minCut = 0.04, finishAllow = 0.03, finishCount = 3, xClear = 0.8, taperWord = 'R',
            ),
            s.pipeInput(),
        )
        assertEquals(PipeInput(), ThreadState(family = ThreadFamily.PT).pipeInput())
        assertEquals(0, ThreadState(family = ThreadFamily.PT, values = mapOf("m" to "2.5")).pipeInput().finishCount)

        val sol = assertNotNull(PipeThreadCalc.calc(threadPtExt().pipeInput()).solution)
        assertEquals(listOf("G97S480", "G0X28.783Z20.", "Z5.", "M23", "G92X26.083Z-15.I-.625F1.8143"), sol.g92.take(5))
        assertEquals("X24.459", sol.g92[sol.g92.size - 3])
    }

    /** 관용 테이퍼 칸마다 범위 밖 값: 화면이 짚는 칸이 있으면 계산기가 BAD_INPUT, 없으면 BAD_INPUT 은 숫나사 끝 골 X ≤ 0 일 때뿐(그때 끝 Z · 기준면 Z 를 짚음) */
    @Test
    fun pipeBadFieldsMatchCalc() {
        val values = listOf("-1", "0", "0.0004", "0.5", "1", "1.25", "1.26", "1.5", "1.6", "99", "100", "100.5", "20000", "100000", "100001", "-20000", "-9000")
        var checked = 0
        var geometry = 0
        for (base in listOf(threadPtExt(), threadPtInt(), threadNptExt(), threadNptInt())) for (f in base.fields) for (v in values) {
            val s = ThreadState(base.inner, base.family, pipe = base.pipe, values = ThreadField.entries.associate { it.key to base.value(it) } + (f.key to v))
            val w = PipeThreadCalc.calc(s.pipeInput()).warnings
            if (s.bad().isNotEmpty()) {
                assertEquals(setOf(f), s.bad(), "$f = $v")
                assertTrue(G76Warning.BAD_INPUT in w, "$f = $v: $w")
            } else if (G76Warning.BAD_INPUT in w) {
                geometry++
                assertFalse(s.inner, "$f = $v: 암나사인데 칸 밖 BAD_INPUT")
                assertEquals(setOf(ThreadField.Z_END, ThreadField.Z_REF), s.flagged(w))
                assertTrue("끝 골 X" in threadWarningText(G76Warning.BAD_INPUT, s))
            }
            checked++
        }
        assertTrue(checked > 400)
        assertTrue(geometry > 0, "숫나사 끝 골 X ≤ 0 사례가 없다")
        // 게이지 면 옮김 한도 = 표의 공차(PT 숫 1 · 암 1¼, NPT 1)
        assertEquals(setOf(ThreadField.GAUGE_SHIFT), threadPtExt().apply { set(ThreadField.GAUGE_SHIFT, "1.01") }.bad())
        assertEquals(emptySet(), threadPtInt().apply { set(ThreadField.GAUGE_SHIFT, "-1.25") }.bad())
        assertTrue("±1¼ 산" in threadWarningText(G76Warning.BAD_INPUT, threadPtInt().apply { set(ThreadField.GAUGE_SHIFT, "1.3") }))
    }

    /** 관용 테이퍼의 멈춤 · 확인 경고: 시작 Z 가 기준면 안이면 두 Z 칸을 짚고 숫자 든 글, 나사 길이가 짧으면 노랑(테 없음)과 숫자 든 글 */
    @Test
    fun pipeWarnings() {
        val inside = threadPtExt().apply { set(ThreadField.Z_REF, "6") }
        val w = PipeThreadCalc.calc(inside.pipeInput()).warnings
        assertEquals(listOf(G76Warning.START_INSIDE), w)
        assertEquals(setOf(ThreadField.Z_START, ThreadField.Z_REF), inside.flagged(w))
        assertTrue("Z5. 이 기준면(공작물 끝면) Z6. 안" in threadWarningText(G76Warning.START_INSIDE, inside), threadWarningText(G76Warning.START_INSIDE, inside))
        // 3/4-14 NPT 숫나사: L2 = 0.5457 in × 25.4 = 13.86078 mm > 나사 길이 12 → 가공 목표점 Z−13.861 이하(0.001 내림)
        val short = threadNptExt()
        val r = PipeThreadCalc.calc(short.pipeInput())
        assertEquals(listOf(G76Warning.SHORT_THREAD), r.warnings)
        val text = threadWarningText(G76Warning.SHORT_THREAD, short, pipe = r.solution)
        assertTrue("나사 길이 12.000 mm" in text && "규격 최소 13.861 mm(L2)" in text && "Z-13.861 이하" in text, text)
        assertEquals(emptySet(), short.flagged(r.warnings))
        // 곧은 나사의 Z 순서 글은 방향을 말하고, 관용 테이퍼는 정가공만
        assertTrue("정가공(Z−)만" in threadWarningText(G76Warning.Z_ORDER, threadPtExt()))
    }

    /** 저장 · 되살리기: 관용 테이퍼(크기 · 기준면 · 옮김 · 낱말) · UN 묶음까지 그대로, 옛 글(2026-11-05 전 — 종류 "UN" · 칸 10개 · NC 탭으로 끝)도 읽는다 */
    @Test
    fun saverPipeAndOld() {
        val scope = SaverScope { true }
        for (s in listOf(threadPtInt(), threadNptInt(), thread20Un(), threadUnef())) {
            val back = ThreadState.Saver.restore(with(ThreadState.Saver) { scope.save(s) }!!)!!
            assertEquals(s.family, back.family)
            assertEquals(s.pipe, back.pipe)
            assertEquals(s.spec, back.spec)
            assertEquals(s.unGroup, back.unGroup)
            assertEquals(s.taperWord, back.taperWord)
            assertEquals(s.pipeInput(), back.pipeInput())
            assertEquals(s.input(), back.input())
            ThreadField.entries.forEach { assertEquals(s.value(it), back.value(it), "${s.family} $it") }
        }
        // 옛 글: 내외 · 종류 · 규격 · 급수 · 소재 · 방향 · 각도 · 면취량 · 고급 펼침 + 칸 10개(Z 시작 · 회전수 · 목표 · 공차 목표 · 인선 R · m · Δdmin · d · X 여유 · Δd) + NC 탭
        val old = listOf("false", "UN", "UNC|1/4|${threadOriginal().spec!!.pitch}", "UN3A", "M", "FORWARD", "55", "15", "true", "5.0", "1821", "-25.0", "", "", "", "", "", "", "0.2", "G76")
        val back = ThreadState.Saver.restore(old)!!
        assertEquals(ThreadFamily.UN, back.family)
        assertEquals("1/4-20 UNC", back.spec?.label)
        assertEquals(ThreadClass.UN3A, back.cls)
        assertEquals(ThreadNc.G76, back.nc)
        assertEquals("0.2", back.value(ThreadField.FIRST_CUT))
        assertEquals("", back.value(ThreadField.Z_REF))
        assertEquals(UnGroup.UNC, back.unGroup)
        assertEquals('I', back.taperWord)
        assertNull(back.pipe)
        assertEquals(threadOriginal().apply { pickClass(ThreadClass.UN3A); angle = 55; chamfer = 15 }.input(), back.input())
        assertEquals(ThreadFamily.METRIC, ThreadState.Saver.restore(old.toMutableList().apply { set(1, "METRIC"); set(2, "M_COARSE|M10|1.5") })!!.family)
    }

    /**
     * 관용 테이퍼 단면 = 규격 모양(독립 값): 피치선(깊이 h/2)의 홈 반폭이 P/4, NPT 산봉우리 · 골 평탄 너비 = H28 표 VII.1 의 최소 평탄 0.038p(3자리),
     * PT 는 골 원호의 바닥이 깊이 h(0.640327P) 와 0.0005P 안 · 산봉우리 꼭대기가 0 과 0.0005P 안, 윤곽은 피치선 점 (P/4, h/2) 둘레 점대칭
     */
    @Test
    fun pipeSectionShape() {
        for (kind in PipeKind.entries) for (inner in listOf(false, true)) {
            val g = pipeSectionGeom(kind, inner, null, null)
            val pr = assertNotNull(g.profile)
            assertEquals(if (kind == PipeKind.PT) 55 else 60, pr.angleDeg)
            assertEquals(0.25, pipeGrooveHalf(pr, 1.0, g.k, g.k / 2), 1e-9)
            val pts = pipeOutline(pr, 1.0, g.k)
            assertEquals(0.0, pts.first().first, 1e-12)
            // 꺾은선(윤곽)까지의 거리
            fun dist(zq: Double, dq: Double): Double = pts.zipWithNext().minOf { (a, b) ->
                val (vz, vd) = b.first - a.first to b.second - a.second
                val t = (((zq - a.first) * vz + (dq - a.second) * vd) / (vz * vz + vd * vd)).coerceIn(0.0, 1.0)
                hypot(a.first + t * vz - zq, a.second + t * vd - dq)
            }
            for ((z, d) in pts) {
                // 점대칭: (z, d) ↔ (P/2 − z, h − d) 가 윤곽 위(꺾은선과 1e-3 안 — 원호 12조각의 활꼴 높이 0.0006P)
                val zm = 0.5 - z
                val dm = g.k - d
                if (zm < 0) continue
                assertTrue(dist(zm, dm) < 1e-3, "$kind ($z, $d) → ${dist(zm, dm)}")
            }
            val deepest = pts.maxOf { it.second }
            val top = pts.minOf { it.second }
            when (kind) {
                PipeKind.PT -> {
                    assertEquals(0.640327, deepest, 5e-4)
                    assertEquals(0.0, top, 5e-4)
                }
                PipeKind.NPT -> {
                    assertEquals(0.8, deepest, 1e-12)
                    val flat = pts.filter { abs(it.second - 0.8) < 1e-12 }.let { it[1].first - it[0].first }
                    assertEquals(0.038, flat, 5e-4)
                    assertEquals(0.038, pts[1].first * 2, 5e-4)
                }
            }
        }
        // 풀렸으면 끝 Z 의 지름 · k 지령값 · 거친 절입 깊이
        val s = threadPtExt()
        val sol = assertNotNull(PipeThreadCalc.calc(s.pipeInput()).solution)
        val g = pipeSectionGeom(PipeKind.PT, false, s.pipe, sol)
        assertEquals(sol.height, g.k)
        assertEquals(sol.passes.size - sol.finishCount, g.depths.size)
        assertEquals("골 X" to "24.459", g.root)
        assertEquals("r 0.250", g.rText)
    }

    @Test
    fun texts() {
        assertEquals("1.27", leadText(1.27))
        assertEquals("6.", leadText(6.0))
        assertEquals("1.9538", leadText(25.4 / 13))
        assertEquals("1.0P", chamferText(10))
        assertEquals("0.5P", chamferText(5))
        assertEquals("2A (보통)", classText(ThreadClass.UN2A))
        assertEquals("4h (정밀)", classText(ThreadClass.M4h))
        assertEquals("20 산", pitchText(threadOriginal().spec!!))
        assertEquals(35L, um(0.035))
        assertEquals(1L, um(0.0005))
    }
}
