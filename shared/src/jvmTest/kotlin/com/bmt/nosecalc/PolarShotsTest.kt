package com.bmt.nosecalc

import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.screens.PolarHelp
import com.bmt.nosecalc.screens.PolarScreen
import com.bmt.nosecalc.screens.polar.PolarField
import com.bmt.nosecalc.screens.polar.PolarState
import com.bmt.nosecalc.screens.polar.SPEEDS
import com.bmt.nosecalc.screens.polar.SimStart
import com.bmt.nosecalc.screens.polar.polarBadWhileWaiting
import com.bmt.nosecalc.screens.polar.polarInnerSmallR
import com.bmt.nosecalc.screens.polar.polarInnerSquare
import com.bmt.nosecalc.screens.polar.polarOriginal07
import com.bmt.nosecalc.screens.polar.polarOriginal12
import com.bmt.nosecalc.screens.polar.polarTriangle
import com.bmt.nosecalc.screens.polar.polarTwoFlat
import com.bmt.nosecalc.screens.polar.polarWgeOd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 턴밀 극좌표 화면을 라이트/다크로 → build/shots/polar-* (원본 docs/store/12.jpg · 07.jpg 와 나란히 본다) */
class PolarShotsTest {
    private fun shot(
        name: String,
        heightDp: Int = 3000,
        editing: PolarField? = null,
        sheet: Boolean = false,
        sim: SimStart? = null,
        direct: Boolean = false,
        widthDp: Int = Shots.WIDTH_DP,
        fontScale: Float = 1f,
        frames: Int = 40,
        frameMs: Long = 100,
        scrollToEnd: Boolean = false,
        state: () -> PolarState,
    ) = Shots.both(
        name, heightDp, frames,
        setup = {
            it.open(Feature.POLAR)
            if (direct) it.compMode = CompMode.DIRECT
        },
        widthDp = widthDp, fontScale = fontScale, frameMs = frameMs, scrollToEnd = scrollToEnd,
    ) { AppHost(it) { PolarScreen(state(), editing, sheet, sim) } }

    /** 재생 중간에 멈춘 시뮬레이션: text 줄(없으면 줄 번호 at)의 frac 만큼 */
    private fun simAt(s: PolarState, direct: Boolean, text: String? = null, at: Int = 0, frac: Double = .5, single: Boolean = false): SimStart {
        val sol = PolarG12.calc(s.input()).solution!!
        val p = if (direct) sol.ncG40 else sol.ncG41G42
        val i = if (text != null) p.blocks.indexOfFirst { it.text == text } else at
        assertTrue(i >= 0, "줄이 없다: $text")
        return SimStart(i, frac, single, SPEEDS.indexOf(5))
    }

    @Test
    fun shots() {
        // 처음 화면(모두 빈칸 · 예시 비율 그림 · 파랑 안내)
        shot("polar-empty", Shots.HEIGHT_DP) { PolarState() }
        shot("polar-empty-long", 1700) { PolarState() }
        // 원본 12 의 값 — 화면 전체(기계 보정 · 직접 보정)
        shot("polar") { polarOriginal12() }
        shot("polar-direct", direct = true) { polarOriginal12() }
        shot("polar-07") { polarOriginal07() }
        shot("polar-inner", 3100) { polarInnerSquare() }
        shot("polar-inner-small-r", 3200) { polarInnerSmallR() }
        shot("polar-two-flat") { polarTwoFlat() }
        shot("polar-triangle", 3200) { polarTriangle() }
        shot("polar-error", 1700) { polarWgeOd() }
        shot("polar-bad-waiting", 1700) { polarBadWhileWaiting() }
    }

    /**
     * 시뮬레이션 모달(원본 07): 재생 중간에 멈춘 장면. 원본 07 의 윤곽 줄(`G01 X50.807 C-36.` `G02 X36.95 C-40. R8.` …)은 원본 12 의 값(80 · 70 · R3)에
     * 공구경 10 을 넣은 직접 보정(G40) 프로그램과 같다 — 그 프로그램 · 같은 줄(`G01 X-36.95`, C-40. 흐림)로 찍는다
     */
    @Test
    fun simulation() {
        shot("polar-sim", Shots.HEIGHT_DP, direct = true, sim = simAt(polarOriginal12(), true, "G1X-36.95")) { polarOriginal12() }
        // 기계 보정(G41 — 제어기 보정 흉내 경로)
        shot("polar-sim-g41", Shots.HEIGHT_DP, sim = simAt(polarOriginal12(), false, "G1X-36.95")) { polarOriginal12() }
        // 싱글 블록 켬 · 줄 끝에서 멈춤
        val n = PolarG12.calc(polarOriginal12().input()).solution!!.ncG41G42.blocks.size
        shot("polar-sim-single", Shots.HEIGHT_DP, sim = simAt(polarOriginal12(), false, at = n * 2 / 3, frac = 1.0, single = true)) { polarOriginal12() }
        // 내측 사각형 — 가운데 줄
        val m = PolarG12.calc(polarInnerSquare().input()).solution!!.ncG41G42.blocks.size
        shot("polar-sim-inner", Shots.HEIGHT_DP, sim = simAt(polarInnerSquare(), false, at = m / 2, frac = .6)) { polarInnerSquare() }
        // 시작 전(공구 없음 · 띠 없음)
        shot("polar-sim-start", Shots.HEIGHT_DP, sim = SimStart(-1, 0.0)) { polarOriginal12() }
        // 짧은 PC 창(412 × 640)
        shot("polar-sim-pc-short", 640, direct = true, sim = simAt(polarOriginal12(), true, "G1X-36.95")) { polarOriginal12() }
    }

    /** 도형 시트 · 키패드 · 아래 결과 띠 */
    @Test
    fun overlays() {
        shot("polar-sheet", Shots.HEIGHT_DP, sheet = true) { polarOriginal12() }
        shot("polar-sheet-inner", Shots.HEIGHT_DP, sheet = true) { polarInnerSquare() }
        shot("polar-keypad", Shots.HEIGHT_DP, editing = PolarField.WIDTH, frames = 150, frameMs = 16) { polarOriginal12() }
        shot("polar-keypad-feed", Shots.HEIGHT_DP, editing = PolarField.FEED, frames = 150, frameMs = 16) { polarOriginal12() }
        shot("polar-dock", Shots.HEIGHT_DP, scrollToEnd = true) { polarOriginal12() }
    }

    /** 좁은 폰(360dp) · 큰 글꼴(1.3배) */
    @Test
    fun narrow() {
        shot("polar-w360-fs130", 3600, widthDp = 360, fontScale = 1.3f) { polarOriginal12() }
        shot("polar-sim-w360-fs130", Shots.HEIGHT_DP, widthDp = 360, fontScale = 1.3f, direct = true, sim = simAt(polarOriginal12(), true, "G1X-36.95")) { polarOriginal12() }
    }

    /** 도움말: 묶음 다섯 + 끝의 '장갑을 끼고 쓸 때' */
    @Test
    fun help() {
        assertEquals(listOf("도형 고르기", "값 넣기", "가공 옵션과 보정", "프로그램과 시뮬레이션", "경고"), PolarHelp.map { it.title })
        Shots.both("polar-help", setup = { it.open(Feature.POLAR); it.helpOpen = true }) { App(it) }
    }
}
