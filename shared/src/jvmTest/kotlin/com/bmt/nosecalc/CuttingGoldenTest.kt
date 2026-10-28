package com.bmt.nosecalc

import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.screens.cutting.CutBox
import com.bmt.nosecalc.screens.cutting.CuttingState
import com.bmt.nosecalc.screens.cutting.basicPreview
import com.bmt.nosecalc.screens.cutting.basicView
import com.bmt.nosecalc.screens.cutting.cutBad
import com.bmt.nosecalc.screens.cutting.cutFeedTooBig
import com.bmt.nosecalc.screens.cutting.cutFromN
import com.bmt.nosecalc.screens.cutting.cutG96
import com.bmt.nosecalc.screens.cutting.cutHand
import com.bmt.nosecalc.screens.cutting.cutWarningText
import com.bmt.nosecalc.screens.cutting.drillBetween
import com.bmt.nosecalc.screens.cutting.drillEmpty
import com.bmt.nosecalc.screens.cutting.drillIscarRows
import com.bmt.nosecalc.screens.cutting.drillLines
import com.bmt.nosecalc.screens.cutting.drillManual
import com.bmt.nosecalc.screens.cutting.drillManualBad
import com.bmt.nosecalc.screens.cutting.drillMitsubishi
import com.bmt.nosecalc.screens.cutting.drillNoFeed
import com.bmt.nosecalc.screens.cutting.drillNoVc
import com.bmt.nosecalc.screens.cutting.drillOutsideRow
import com.bmt.nosecalc.screens.cutting.drillOutsideSeries
import com.bmt.nosecalc.screens.cutting.drillPreview
import com.bmt.nosecalc.screens.cutting.drillSandvik
import com.bmt.nosecalc.screens.cutting.drillWarningText
import com.bmt.nosecalc.screens.cutting.sourceText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 재료·절삭조건 화면의 숫자 지킴이(UiGoldenTest 와 같은 방식 · 같은 golden 폴더의 cutting-*.txt). 스크린샷 상태마다 화면이 쓰는 글
 * (자동 칸 · 결과 칸 · 경고 · 드릴 표 · 출처)을 적는다. 일부러 바꿨을 때만 다시 쓴다:
 * `gradlew :shared:jvmTest --tests '*CuttingGoldenTest*' -DupdateGolden=true`
 */
class CuttingGoldenTest {
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
        assertEquals(f.readText().replace("\r\n", "\n"), text, "$name: 화면 숫자가 골든과 다르다")
    }

    @Test
    fun basic() {
        basic("cutting-basic-empty", CuttingState())
        basic("cutting-basic", cutHand())
        basic("cutting-basic-n", cutFromN())
        basic("cutting-basic-g96", cutG96())
        basic("cutting-basic-bad", cutBad())
        basic("cutting-basic-feed", cutFeedTooBig())
    }

    @Test
    fun drill() {
        drill("cutting-drill-empty", drillEmpty())
        drill("cutting-drill-rows", drillIscarRows())
        drill("cutting-drill", drillSandvik())
        drill("cutting-drill-table-rpm", drillMitsubishi())
        drill("cutting-drill-outside-row", drillOutsideRow())
        drill("cutting-drill-between", drillBetween())
        drill("cutting-drill-outside-series", drillOutsideSeries())
        drill("cutting-drill-no-feed", drillNoFeed())
        drill("cutting-drill-no-vc", drillNoVc())
        drill("cutting-drill-manual", drillManual())
        drill("cutting-drill-manual-bad", drillManualBad())
    }

    private fun basic(name: String, s: CuttingState) {
        val r = Cutting.calc(s.input())
        val v = basicView(s, r)
        val text = buildString {
            line("입력 " + CutBox.entries.filter { it.cut != null && s.value(it).isNotEmpty() }.joinToString(" ") { "${it.key}=${s.value(it)}" } + " · ${s.speedFrom} · ${s.feedFrom}")
            line("경고 ${r.warnings} · 잘못된 칸 ${r.bad}")
            r.warnings.forEach { line("  ${cutWarningText(it, s, r)}") }
            line("자동 Vc ${v.vcAuto} · n ${v.nAuto} · f ${v.fAuto}")
            line("vf ${v.vf} · 시간 ${v.timeMin} 분 ${v.timeSec} 초 · Rmax ${v.rz} · Ra ${v.ra}")
            v.practical?.let { line(it) }
            v.g96.forEach { line("G96 $it") }
            line("키패드 ${basicPreview(s, v)}")
        }
        golden(name, text)
    }

    private fun drill(name: String, s: CuttingState) {
        val r = s.drill()
        val text = buildString {
            line("메이커 ${s.drillMaker?.label ?: s.maker} · 시리즈 ${s.drillSeries?.series} · 군 ${s.iso} · 행 ${s.condition?.material} · 직경 '${s.value(CutBox.DRILL_D)}'")
            line("남은 것 ${s.drillMissing()}")
            if (r == null) return@buildString
            line("경고 ${r.warnings} · 다른 행 ${r.alternatives}")
            r.warnings.forEach { line("  ${drillWarningText(it, s, r)}") }
            drillLines(r).forEach { l -> line("표 ${l.name} (${l.unit}) ${l.min ?: "—"} | ${l.rec ?: "—"} | ${l.max ?: "—"}") }
            line("구간 ${r.band}")
            line("출처 ${sourceText(r)}")
            line("키패드 ${drillPreview(r)}")
        }
        golden(name, text)
    }

    private fun StringBuilder.line(s: String) = append(s).append('\n')
}
