package com.bmt.nosecalc

import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import kotlin.test.Test

/** 라운드 고르기·입력 화면을 라이트/다크로 → build/shots/round-*. 원본 04 와 나란히 본다. 계산 상태는 예시 치수로 */
class RoundShotsTest {
    private fun shot(name: String, heightDp: Int = Shots.HEIGHT_DP, state: () -> RoundState) =
        Shots.both(name, heightDp) { AppHost(it) { RoundScreen(state()) } }

    @Test
    fun pick() {
        shot("round-a") { RoundState() }
        shot("round-a-all", 1180) { RoundState() }
    }

    @Test
    fun input() {
        for (t in 1..6) shot("round-b$t", 2800) { roundExample(t, open = true) }
        shot("round-b3-contact", 2350) { roundExample(3, open = true, comp = false) }
        shot("round-b2-error", 1700) { roundExample(2, open = true).apply { setDim(2, "l2", "30") } }
        shot("round-manual", 2400) { roundExample(MANUAL, open = true) }
        shot("round-manual-inner-rev", 2400) { roundManualInnerRev() }
    }

    /** 처음 화면: 치수 · 세 점이 모두 0 — 파란 안내만, 빨강 없이 그림이 보인다 */
    @Test
    fun empty() {
        shot("round-b3-empty", 1500) { RoundState(type = 3, open = true) }
        shot("round-manual-empty", 1500) { RoundState(type = MANUAL, open = true) }
    }
}
