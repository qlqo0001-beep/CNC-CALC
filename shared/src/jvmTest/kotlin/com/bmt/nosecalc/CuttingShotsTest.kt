package com.bmt.nosecalc

import com.bmt.nosecalc.screens.CuttingHelp
import com.bmt.nosecalc.screens.CuttingScreen
import com.bmt.nosecalc.screens.cutting.CutBox
import com.bmt.nosecalc.screens.cutting.CutTab
import com.bmt.nosecalc.screens.cutting.CuttingState
import com.bmt.nosecalc.screens.cutting.cutBad
import com.bmt.nosecalc.screens.cutting.cutFeedTooBig
import com.bmt.nosecalc.screens.cutting.cutFromN
import com.bmt.nosecalc.screens.cutting.cutG96
import com.bmt.nosecalc.screens.cutting.cutHand
import com.bmt.nosecalc.screens.cutting.drillBetween
import com.bmt.nosecalc.screens.cutting.drillEmpty
import com.bmt.nosecalc.screens.cutting.drillIscarRows
import com.bmt.nosecalc.screens.cutting.drillManual
import com.bmt.nosecalc.screens.cutting.drillMitsubishi
import com.bmt.nosecalc.screens.cutting.drillNoFeed
import com.bmt.nosecalc.screens.cutting.drillOutsideRow
import com.bmt.nosecalc.screens.cutting.drillSandvik
import kotlin.test.Test
import kotlin.test.assertEquals

/** 재료·절삭조건 화면을 라이트/다크로 → build/shots/cutting-* (원본 docs/store/03.jpg 와 나란히 본다) */
class CuttingShotsTest {
    private fun shot(
        name: String,
        heightDp: Int = Shots.HEIGHT_DP,
        editing: CutBox? = null,
        widthDp: Int = Shots.WIDTH_DP,
        fontScale: Float = 1f,
        frames: Int = 40,
        frameMs: Long = 100,
        state: () -> CuttingState,
    ) = Shots.both(
        name, heightDp, frames, setup = { it.open(Feature.CUTTING) }, widthDp = widthDp, fontScale = fontScale, frameMs = frameMs,
    ) { AppHost(it) { CuttingScreen(state(), editing) } }

    /** 기본계산기: 처음(빈칸) · 손 계산 값 · 회전수와 날당 이송 · G96 막힘 · 잘못된 칸 · 노즈 R 보다 큰 이송 */
    @Test
    fun basic() {
        shot("cutting-basic-empty") { CuttingState() }
        shot("cutting-basic", 1700) { cutHand() }
        shot("cutting-basic-n", 1700) { cutFromN() }
        shot("cutting-basic-g96", 1800) { cutG96() }
        shot("cutting-basic-bad", 1800) { cutBad() }
        shot("cutting-basic-feed", 1700) { cutFeedTooBig() }
    }

    /** 재료분석: 첫 화면 · 여섯 군 전체 */
    @Test
    fun material() {
        shot("cutting-material") { CuttingState(tab = CutTab.MATERIAL) }
        shot("cutting-material-long", 4600) { CuttingState(tab = CutTab.MATERIAL) }
    }

    /** 드릴링: 처음(안 고름) · 행 목록 · 손 계산 값 · 회전수 표 · 행 밖(다른 행 단추) · 구간 사이 · 이송 없는 행 · 직접 입력 */
    @Test
    fun drill() {
        shot("cutting-drill-empty") { drillEmpty() }
        shot("cutting-drill-empty-long", 1500) { drillEmpty() }
        shot("cutting-drill-rows", 2700) { drillIscarRows() }
        shot("cutting-drill", 2500) { drillSandvik() }
        shot("cutting-drill-table-rpm", 2700) { drillMitsubishi() }
        shot("cutting-drill-outside-row", 2400) { drillOutsideRow() }
        shot("cutting-drill-between", 2500) { drillBetween() }
        shot("cutting-drill-no-feed", 2400) { drillNoFeed() }
        shot("cutting-drill-manual", 1500) { drillManual() }
    }

    /** 키패드(기본계산기 Vc · 드릴 직경) · 도움말(드릴링 탭에서 열면 드릴링 묶음부터) */
    @Test
    fun overlays() {
        shot("cutting-keypad", editing = CutBox.VC, frames = 150, frameMs = 16) { cutHand() }
        shot("cutting-drill-keypad", editing = CutBox.DRILL_D, frames = 150, frameMs = 16) { drillSandvik() }
        assertEquals(listOf("탭 셋", "기본계산기", "재료분석", "드릴링", "경고"), CuttingHelp.map { it.title })
        Shots.both("cutting-help", setup = { it.open(Feature.CUTTING); it.helpOpen = true }) { AppHost(it) { CuttingScreen(drillSandvik()) } }
    }

    /** 좁은 폰(360dp) · 큰 글꼴(1.3배) */
    @Test
    fun narrow() {
        shot("cutting-basic-w360-fs130", 2100, widthDp = 360, fontScale = 1.3f) { cutG96() }
        shot("cutting-drill-w360-fs130", 3400, widthDp = 360, fontScale = 1.3f) { drillOutsideRow() }
        shot("cutting-material-w360-fs130", 1400, widthDp = 360, fontScale = 1.3f) { CuttingState(tab = CutTab.MATERIAL) }
        shot("cutting-drill-empty-w360-fs130", widthDp = 360, fontScale = 1.3f) { drillEmpty() }
    }
}
