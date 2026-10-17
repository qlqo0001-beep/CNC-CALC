package com.bmt.nosecalc

import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import kotlin.test.Test

/** 라운드 고르기·입력 화면을 라이트/다크로 → build/shots/round-*. 원본 04 와 나란히 본다 */
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
        for (t in 1..6) shot("round-b$t", 2450) { RoundState(type = t, open = true) }
        shot("round-b3-contact", 2000) { RoundState(type = 3, open = true, comp = false) }
        shot("round-b2-error", 1500) { RoundState(type = 2, open = true).apply { setDim(2, "l2", "30") } }
        shot("round-manual", 2150) { RoundState(type = MANUAL, open = true) }
    }
}
