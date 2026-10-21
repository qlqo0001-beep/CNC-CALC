package com.bmt.nosecalc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.HolderCard
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundInnerStep
import com.bmt.nosecalc.screens.round.roundManualInnerMouth
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import com.bmt.nosecalc.screens.round.withA16
import com.bmt.nosecalc.ui.theme.Space
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
        // 내경이라 보링바 카드(빈칸 — 파란 안내)까지
        shot("round-manual-inner-rev", 3780) { roundManualInnerRev() }
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

    /** 도움말 묶음: 고르기 화면은 '형상 고르기' 부터, 입력 화면은 '치수 넣기'(그림 먼저)부터, 내경 입력 화면은 '보링바 간섭' 부터 */
    @Test
    fun help() {
        Shots.both("round-help", setup = { it.helpOpen = true }) { AppHost(it) { RoundScreen(RoundState()) } }
        Shots.both("round-help-input", setup = { it.helpOpen = true }) { AppHost(it) { RoundScreen(roundExample(2, open = true)) } }
        Shots.both("round-help-holder", setup = { it.helpOpen = true }) { AppHost(it) { RoundScreen(roundInnerExample(3)) } }
    }

    /**
     * 내경: 고르기(형상 2~6 · 언더컷 표), 손 계산 돔(바 없음 — 파란 안내), 프리셋 A16R-SCLCR 09 로 닿음(기계 보정 — 띠 · 빨간 줄 · 단면) ·
     * 관통 단차는 안 닿음(직접 보정), 3점호 입구 R(안 닿음)
     */
    @Test
    fun inner() {
        shot("round-pick-inner", 1180) { RoundState().apply { typeInner = true } }
        shot("round-inner-b3", 3700, comp = CompMode.DIRECT) { roundInnerExample(3) }
        shot("round-inner-b3-holder", 4100) { roundInnerExample(3).withA16() }
        shot("round-inner-b5-through-holder-clear", 4200, comp = CompMode.DIRECT) { roundInnerStep().withA16() }
        shot("round-manual-inner-holder", 3850) { roundManualInnerMouth().withA16() }
    }

    /** 보링바 칸만: 프리셋을 고른 뒤 f 를 8 로 고침 — '수정됨' · 끝날각 줄 · f 오류 */
    @Test
    fun holderCard() {
        Shots.both("round-inner-holder-card", 1060, setup = { it.open(Feature.ROUND) }) {
            AppHost(it) {
                val s = remember { roundInnerExample(3).withA16().apply { setHolder("f", "8") } }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.gutter)) { HolderCard(s, 0.4) }
            }
        }
    }
}
