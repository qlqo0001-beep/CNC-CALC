package com.bmt.nosecalc

import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import kotlin.test.Test

/** 라운드 고르기·입력 화면을 라이트/다크로 → build/shots/round-*. 원본 04 와 나란히 본다. 계산 상태는 예시 치수로, 보정 방식은 처음 값(기계 보정) 또는 comp */
class RoundShotsTest {
    private fun shot(
        name: String,
        heightDp: Int = Shots.HEIGHT_DP,
        frames: Int = 40,
        frameMs: Long = 100,
        comp: CompMode = CompMode.MACHINE,
        state: () -> RoundState,
    ) = Shots.both(
        name, heightDp, frames,
        // 앱에서처럼 테이퍼 위에 연 화면(헤더에 ←)
        setup = {
            it.open(Feature.ROUND)
            it.compMode = comp
        },
        frameMs = frameMs,
    ) { AppHost(it) { RoundScreen(state()) } }

    @Test
    fun pick() {
        shot("round-a") { RoundState() }
        shot("round-a-all", 1180) { RoundState() }
    }

    /** 형상 1 은 처음 보정 방식(기계 보정 — taper-b 와 같은 스위치), 형상 2~6 · 3점호는 직접 보정(합성 R 궤적), round-b3-contact 는 형상 3 의 기계 보정 */
    @Test
    fun input() {
        shot("round-b1", 3320) { roundExample(1, open = true) }
        for (t in 2..6) shot("round-b$t", 3320, comp = CompMode.DIRECT) { roundExample(t, open = true) }
        shot("round-b3-contact", 2720) { roundExample(3, open = true) }
        shot("round-b2-error", 1700) { roundExample(2, open = true).apply { setDim(2, "l2", "30") } }
        shot("round-manual", 2720, comp = CompMode.DIRECT) { roundExample(MANUAL, open = true) }
        shot("round-manual-inner-rev", 2720) { roundManualInnerRev() }
    }

    /** 처음 화면: 치수 · 세 점이 모두 0 — 파란 안내만, 빨강 없이 그림이 보인다 */
    @Test
    fun empty() {
        shot("round-b3-empty", 1500) { RoundState(type = 3, open = true) }
        shot("round-manual-empty", 1500) { RoundState(type = MANUAL, open = true) }
    }

    /**
     * 키패드(폰 높이): 그림은 고치는 치수 · 점 하나만 초점색, 위에 결과 한 줄, 오른쪽 아래 "다음", 고치는 칸은 시트 위로.
     * 칸을 끌어올리는 스크롤이 0.1초 걸음에서는 출렁이므로 폰 프레임(16ms)으로 찍는다
     */
    @Test
    fun keypad() {
        shot("round-b2-keypad", frames = 150, frameMs = 16) { roundExample(2, open = true).apply { editing = "l2" } }
        shot("round-manual-keypad", frames = 150, frameMs = 16) { roundExample(MANUAL, open = true).apply { editing = "x2" } }
    }

    /** 폰 첫 화면: 노드 표가 화면 밖이라 아래 결과 띠(첫 점 · 끝 점 · 외 n점)가 뜬다 */
    @Test
    fun dock() = shot("round-b-top") { roundExample(1, open = true) }

    /** 도움말 묶음: 고르기 화면은 '형상 고르기' 부터, 입력 화면은 '치수 넣기'(그림 먼저)부터 */
    @Test
    fun help() {
        Shots.both("round-help", setup = { it.helpOpen = true }) { AppHost(it) { RoundScreen(RoundState()) } }
        Shots.both("round-help-input", setup = { it.helpOpen = true }) { AppHost(it) { RoundScreen(roundExample(2, open = true)) } }
    }
}
