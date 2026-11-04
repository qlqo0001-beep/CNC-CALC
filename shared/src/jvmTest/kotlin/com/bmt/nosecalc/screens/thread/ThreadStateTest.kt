package com.bmt.nosecalc.screens.thread

import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSeries
import androidx.compose.runtime.saveable.SaverScope
import kotlin.math.abs
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
        s.switchKind(ThreadKind.METRIC)
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
