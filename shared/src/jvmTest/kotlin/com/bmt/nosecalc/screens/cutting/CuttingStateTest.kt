package com.bmt.nosecalc.screens.cutting

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.calc.CutField
import com.bmt.nosecalc.calc.CutWarning
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DrillCatalog
import com.bmt.nosecalc.calc.DrillWarning
import com.bmt.nosecalc.calc.FeedFrom
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.SpeedFrom
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 재료·절삭조건 화면 상태: 칸 넣기 규칙 · 드릴 고르기 · 화면 숫자 글이 계산기 값과 같은지 · 저장 */
class CuttingStateTest {
    @Test
    fun startsEmpty() {
        val s = CuttingState()
        assertEquals(CutTab.BASIC, s.tab)
        assertTrue(CutBox.entries.all { s.value(it).isEmpty() })
        val r = Cutting.calc(s.input())
        val v = basicView(s, r)
        assertEquals(listOf(null, null, null, null, null, null, null), listOf(v.vcAuto, v.nAuto, v.fAuto, v.vf, v.timeMin, v.rz, v.ra))
        assertTrue(r.warnings.isEmpty())
        assertNull(basicPreview(s, v))
        // 드릴링도 아무것도 안 고름 — 숫자 없음, 남은 것 넷
        assertNull(drillEmpty().drill())
        assertEquals(listOf("메이커", "공구 종류", "피삭재", "직경"), drillEmpty().drillMissing())
    }

    /** Vc · n, f · fz 는 나중에 넣은 쪽이 기준 — 다른 쪽 글을 비우고 자동 값을 보인다 */
    @Test
    fun lastTypedWins() {
        val s = cutHand()
        assertEquals(SpeedFrom.VC, s.speedFrom)
        assertEquals("955", basicView(s, Cutting.calc(s.input())).nAuto)
        s.set(CutBox.N, "1000")
        assertEquals(SpeedFrom.N, s.speedFrom)
        assertEquals("", s.value(CutBox.VC))
        val v = basicView(s, Cutting.calc(s.input()))
        assertNull(v.nAuto)
        assertEquals(fixed(PI * 50 * 1000 / 1000, 1), v.vcAuto)
        s.set(CutBox.FZ, "0.1")
        assertEquals(FeedFrom.FZ, s.feedFrom)
        assertEquals("", s.value(CutBox.F))
        assertEquals("0.200", basicView(s, Cutting.calc(s.input())).fAuto)
        s.set(CutBox.Z, "3")
        assertEquals("0.300", basicView(s, Cutting.calc(s.input())).fAuto)
        s.set(CutBox.F, "0.25")
        assertEquals(FeedFrom.F, s.feedFrom)
        assertEquals("", s.value(CutBox.FZ))
    }

    /** 손 계산(calc-design.md)과 화면 글: n 955 · vf 191.0 · 0.63 분(37.7 초) · Rmax 6.25 · Ra 1.60 */
    @Test
    fun handNumbers() {
        val s = cutHand()
        val v = basicView(s, Cutting.calc(s.input()))
        assertEquals("955", v.nAuto)
        assertEquals("191.0", v.vf)
        assertEquals("0.63", v.timeMin)
        assertEquals("37.7", v.timeSec)
        assertEquals("6.25", v.rz)
        assertEquals("1.60", v.ra)
        val g = cutG96()
        val gv = basicView(g, Cutting.calc(g.input()))
        assertTrue(gv.g96[0].startsWith("Ø31.831 "), gv.g96[0])
        assertTrue("3183 → 막혀서 S2000" in gv.g96[1] && "125.7 m/min" in gv.g96[1], gv.g96[1])
        assertTrue("300.0 mm/min" in gv.g96[2], gv.g96[2])
    }

    /** 잘못된 칸 = 계산기의 bad(빨간 테) — 날 수는 정수 1~100 만 */
    @Test
    fun badFields() {
        val r = Cutting.calc(cutBad().input())
        assertEquals(setOf(CutField.D), r.bad)
        assertTrue(CutWarning.BAD_INPUT in r.warnings)
        assertNull(r.n)
        assertTrue(r.rz != null)
        listOf("2.5" to true, "0" to true, "101" to true, "1" to false, "100" to false).forEach { (z, bad) ->
            val s = CuttingState().apply {
                set(CutBox.FZ, "0.1")
                set(CutBox.Z, z)
            }
            assertEquals(bad, CutField.Z in Cutting.calc(s.input()).bad, "날 수 $z")
        }
        val m = drillManualBad()
        assertEquals(setOf(CutBox.DRILL_VC), m.drillBad(m.drill()))
        val d = drillSandvik().apply { set(CutBox.DRILL_D, "-3") }
        assertEquals(setOf(CutBox.DRILL_D), d.drillBad(d.drill()))
    }

    /** 메이커를 바꾸면 시리즈 · 행을, 시리즈를 바꾸면 행을 비운다. 행을 고르면 그 군으로, 군을 바꾸면 맞지 않는 행을 비운다 */
    @Test
    fun drillPicks() {
        val s = drillSandvik()
        assertEquals(IsoGroup.P, s.iso)
        s.pickIso(IsoGroup.M)
        assertNull(s.row)
        s.pickRow(6)
        assertEquals(IsoGroup.M, s.iso)
        s.pickSeries(1)
        assertNull(s.row)
        assertEquals(IsoGroup.M, s.iso)
        s.pickSeries(0)
        s.pickRow(0)
        s.pickMaker("sandvik")
        assertEquals(0, s.row)
        s.pickMaker("iscar")
        assertNull(s.series)
        assertNull(s.row)
        assertEquals(IsoGroup.P, s.iso)
        assertEquals("10", s.value(CutBox.DRILL_D))
        assertEquals(listOf("공구 종류", "피삭재"), s.drillMissing())
        s.pickMaker(MANUAL)
        assertEquals(listOf("절삭 속도 Vc"), s.drillMissing())
    }

    /** 화면 표의 글 = 계산기 값을 반올림한 것(손 계산 Sandvik 860 Ø10 · Mitsubishi MVE Ø6.3 · 직접 입력) */
    @Test
    fun drillScreenNumbers() {
        val r = drillSandvik().drill()!!
        assertEquals(
            listOf(
                DrillLine("절삭 속도 Vc", "m/min", "120", "145", "170"),
                DrillLine("회전수 n", "rpm", "3820", "4615", "5411"),
                DrillLine("이송 f", "mm/rev", "0.20", "0.25", "0.30"),
                DrillLine("분당 이송 vf", "mm/min", "764", "1154", "1623"),
            ),
            drillLines(r),
        )
        val m = drillMitsubishi().drill()!!
        assertEquals(DrillLine("절삭 속도 Vc", "m/min · 환산", null, "79.2", null), drillLines(m)[0])
        assertEquals(DrillLine("회전수 n", "rpm · 표 값", null, "4000", null), drillLines(m)[1])
        assertEquals(DrillLine("분당 이송 vf", "mm/min", "520", "800", "1040"), drillLines(m)[3])
        val x = drillManual().drill()!!
        assertEquals(listOf("3183", "637"), listOf(drillLines(x)[1].rec, drillLines(x)[3].rec))
    }

    /** 경고: 행 밖 → 다른 직경 행 · 구간 사이 → 앞뒤 구간 · 시리즈 밖 · 이송 없음 · Vc 없음 */
    @Test
    fun drillWarnings() {
        val out = drillOutsideRow()
        val r = out.drill()!!
        assertEquals(listOf(DrillWarning.OUTSIDE_ROW), r.warnings)
        assertEquals(listOf(5), r.alternatives)
        assertTrue("Ø10\u00A0~\u00A020.99" in drillWarningText(DrillWarning.OUTSIDE_ROW, out, r))
        assertEquals("Ø21\u00A0~\u00A033", rangeText(DrillCatalog.rowRange(out.drillSeries!!, out.drillSeries!!.conditions[5])))
        val b = drillBetween()
        val br = b.drill()!!
        assertEquals(listOf(DrillWarning.NOT_IN_FEED_TABLE), br.warnings)
        val bt = drillWarningText(DrillWarning.NOT_IN_FEED_TABLE, b, br)
        assertTrue("아래 Ø4 열" in bt && "위 Ø6 열" in bt, bt)
        assertEquals(listOf(DrillWarning.OUTSIDE_SERIES), drillOutsideSeries().drill()!!.warnings)
        assertEquals(listOf(DrillWarning.NO_FEED_IN_TABLE), drillNoFeed().drill()!!.warnings)
        assertEquals(listOf(DrillWarning.NO_VC_IN_TABLE), drillNoVc().drill()!!.warnings)
    }

    @Test
    fun texts() {
        assertEquals("0.20", feedText(0.2))
        assertEquals("0.025", feedText(0.025))
        assertEquals("1.00", feedText(1.0))
        assertEquals("79.2", trim(79.168, 1))
        assertEquals("120", trim(120.0, 1))
        assertEquals("CoroDrill 870" to "(<6xD)", seriesTitle("CoroDrill 870 (<6xD)"))
        assertEquals("CoroDrill 860-GM" to null, seriesTitle("CoroDrill 860-GM"))
        assertEquals("내부 급유 (외부 급유만 쓸 때는 L/D=3까지)", coolantText("internal (외부 급유만 쓸 때는 L/D=3까지)"))
        assertEquals("모름", coolantText("모름"))
        // 모든 시리즈 형식 · 급유 첫 낱말이 한글로 바뀐다(모르는 형식이 영어로 남지 않게)
        DrillCatalog.makers.flatMap { it.series }.forEach { d ->
            assertTrue(drillTypeText(d.type) != d.type, d.type)
            val head = d.coolant.substringBefore(' ')
            if (head in listOf("internal", "external", "both")) assertTrue(coolantText(d.coolant).first() in '가'..'힣', d.coolant)
        }
    }

    @Test
    fun saverRoundTrip() {
        val s = drillSandvik().apply {
            set(CutBox.N, "800")
            set(CutBox.FZ, "0.04")
        }
        val scope = SaverScope { true }
        val saved = with(CuttingState.Saver) { scope.save(s) }!!
        val back = CuttingState.Saver.restore(saved)!!
        assertEquals(listOf(s.tab, s.speedFrom, s.feedFrom, s.maker, s.series, s.iso, s.row), listOf(back.tab, back.speedFrom, back.feedFrom, back.maker, back.series, back.iso, back.row))
        assertEquals(CutBox.entries.map { s.value(it) }, CutBox.entries.map { back.value(it) })
    }
}
