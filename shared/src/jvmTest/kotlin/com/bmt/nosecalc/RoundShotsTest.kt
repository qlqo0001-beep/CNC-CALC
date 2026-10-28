package com.bmt.nosecalc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.HolderCard
import com.bmt.nosecalc.screens.round.HolderShow
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.RoundViewer
import com.bmt.nosecalc.screens.round.HitRow
import com.bmt.nosecalc.screens.round.HolderNcCard
import com.bmt.nosecalc.screens.round.NcTone
import com.bmt.nosecalc.screens.round.hitRows
import com.bmt.nosecalc.screens.round.stopView
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerBlindC
import com.bmt.nosecalc.screens.round.roundInnerDomeA
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundInnerSocketF
import com.bmt.nosecalc.screens.round.roundManualBackBoring
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

    /**
     * 처음 화면: 치수 · 세 점이 모두 빈칸(사용자 요청 2026-10-28) — 파란 안내만, 빨강 없이 그림이 보인다(이름표만). 모서리 R · Z 여유는 흐린 '비우면 0'.
     * 외경 · 내경 형상 3 은 칸 하나(주 R)만 넣은 상태도 — 아직 모자라 안내 그대로, 그림에 그 값만
     */
    @Test
    fun empty() {
        shot("round-b3-empty", 1500) { RoundState(type = 3, open = true) }
        shot("round-manual-empty", 1500) { RoundState(type = MANUAL, open = true) }
        shot("round-b3-one", 1500) { RoundState(type = 3, open = true).apply { setDim(3, "mainR", "12.5") } }
        shot("round-inner-b3-empty", 2000) { RoundState(type = 3, open = true).apply { typeInner = true } }
        shot("round-inner-b3-one", 2000) { RoundState(type = 3, open = true).apply { typeInner = true; setDim(3, "mainR", "12.5") } }
    }

    /**
     * 키패드(폰 높이): 그림은 고치는 치수 · 점 하나만 초점색, 위에 결과 한 줄, 오른쪽 아래 두 칸 높이 "확인", 고치는 칸은 시트 위로.
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
     * 내경: 고르기(형상 2~6 · 언더컷 표), 손 계산 돔(바 없음 — 파란 안내), 프리셋 A16R-SCLCR 09 로 닿음(기계 보정 — DMIN 20 이라 깎기 전에 멈춤) ·
     * 관통 단차는 안 닿음(직접 보정 — 끝까지 안전), 3점호 입구 R, 돔 팁 0(기계 보정 G00 을 띄워 입구 벽에 직각으로 들어감 — 형상 확인의 진입 경로 두 토막)
     */
    @Test
    fun inner() {
        shot("round-pick-inner", 1180) { RoundState().apply { typeInner = true } }
        shot("round-inner-b3", 3700, comp = CompMode.DIRECT) { roundInnerExample(3) }
        shot("round-inner-b3-holder", 5000) { roundInnerExample(3).withA16() }
        shot("round-inner-b5-through-holder-clear", 4700, comp = CompMode.DIRECT) { roundInnerStep().withA16() }
        shot("round-manual-inner-holder", 4700) { roundManualInnerMouth().withA16() }
        shot("round-inner-b3-tip0", 3700) { roundInnerExample(3).apply { tip = 0 } }
    }

    /**
     * 안전 끝점(사용자 요청 2026-10-27): calc-design 보링바 예 A(돔 + 바 A, 기계 보정 — 구 원호 중간에서 끊고 온 길을 거꾸로) ·
     * F(소켓 + Ø8 바, 직접 보정 — 아래 인서트 조각이 맞은편 벽) · 뒤보링(DMIN 22, 기계 보정 — 헤드가 Ø20 쪽에서 모자람, X 빼고 Z 로) ·
     * C(막힌 단차 + 바 A, 기계 보정 — 바닥 면에서 바 맞은편이 안전 여유, 바 앞면은 깎는 면 예외라 끝점 카드에 작은 글).
     * 간섭 없음은 [inner] 의 관통 단차
     */
    @Test
    fun safeStop() {
        shot("round-inner-a-stop", 4900) { roundInnerDomeA() }
        shot("round-inner-f-stop", 4900, comp = CompMode.DIRECT) { roundInnerSocketF() }
        shot("round-manual-back-stop", 4600) { roundManualBackBoring() }
        shot("round-inner-c-stop", 4900) { roundInnerBlindC() }
        // 간섭 목록의 첫 줄(바 · 맞은편 벽)을 누른 뒤: 초점색 띠, 형상 확인이 그 처음 점으로 3배, 그 자세의 보링바, NC 가 전체로 바뀌어 5줄을 짚음
        Shots.both(
            "round-inner-a-stop-tap", 4900,
            setup = { it.open(Feature.ROUND) }, tapDp = Offset(150f, 2395f),
        ) { AppHost(it) { RoundScreen(roundInnerDomeA()) } }
    }

    /** NC 카드 전체 보기: 간섭 목록에서 누른 줄(초점색 바탕) · 간섭 줄(빨강) — 예 A 의 기계 보정 */
    @Test
    fun ncFullFocus() {
        Shots.both("round-inner-nc-full-focus", 430, setup = { it.open(Feature.ROUND) }) {
            AppHost(it) {
                val s = remember { roundInnerDomeA() }
                val input = s.input(3)
                val sol = remember { RoundCalc.calc(input).solution!! }
                val drawn = remember { RoundCalc.machine(input).solution!! }
                val bar = s.holderBar()!!
                val rows: List<HitRow> = remember { hitRows(HolderStop.plan(sol, drawn, input.noseR, true, bar), false, bar, false) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.gutter)) {
                    HolderNcCard(
                        drawn.nc, "전체 프로그램(확인용)입니다 — 확인용.", "전체 (간섭 포함)", emptyList(),
                        tones = rows.flatMap { r -> r.line..r.lineTo }.associateWith { NcTone.HIT }, focus = rows.first().line, danger = true,
                    )
                }
            }
        }
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

    /**
     * 형상 확인 크게 보기(보링바 예 A — 돔 + 바 A, 기계 보정): 카드 머리 오른쪽 '크게 보기' 를 눌러 연다 — 안전 끝 깃발 · 후퇴 점선 · 그 자세의 보링바,
     * 보링바 자리 고르기도 아래 단추 줄과 함께
     */
    @Test
    fun holderFull() {
        Shots.both("round-inner-holder-full", setup = { it.open(Feature.ROUND) }, tapDp = Offset(366f, 50f)) {
            AppHost(it) {
                val s = remember { roundInnerDomeA() }
                val input = s.input(3)
                val sol = remember { RoundCalc.calc(input).solution!! }
                val drawn = remember { RoundCalc.machine(input).solution!! }
                val bar = s.holderBar()!!
                val plan = remember { HolderStop.plan(sol, drawn, input.noseR, true, bar, s.holderSafe()) }
                Column(Modifier.fillMaxSize().padding(Space.gutter)) {
                    RoundViewer(
                        sol, false, input.noseR, drawn, true,
                        HolderShow(bar, plan.result, true, hitRows(plan, false, bar, false), stopView(plan, false, sol, drawn, input.noseR, bar)),
                    )
                }
            }
        }
    }
}
