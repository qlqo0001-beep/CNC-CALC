package com.bmt.nosecalc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.TipHelp
import com.bmt.nosecalc.screens.taper.TipSheet
import com.bmt.nosecalc.ui.AppCard
import kotlin.test.Test

/** 테이퍼·홈 화면 A·B 를 라이트/다크로 → build/shots/taper-*. 원본 02·11 과 나란히 본다 */
class TaperShotsTest {
    private fun shot(name: String, heightDp: Int = Shots.HEIGHT_DP, state: () -> TaperState) =
        Shots.both(name, heightDp) { AppHost(it) { TaperScreen(state()) } }

    @Test
    fun pick() {
        shot("taper-a-inner") { TaperState(TaperKind.INNER) }
        shot("taper-a-reverse") { TaperState(reverse = true) }
        shot("taper-a-inner-reverse") { TaperState(TaperKind.INNER, reverse = true) }
        shot("taper-a-double") { TaperState(TaperKind.OUTER_DOUBLE) }
        shot("taper-a-inner-double") { TaperState(TaperKind.INNER_DOUBLE) }
        shot("taper-a-groove") { TaperState(TaperKind.GROOVE) }
    }

    @Test
    fun input() {
        shot("taper-b", 2150) { TaperState(type = 1, open = true) }
        // 외경 형상 1: T1 모서리 R1, 시작점 단면 X30 → 결과 카드에 R 시작·끝 접점, NC 는 직접 보정 탭
        shot("taper-b-corner", 2350) {
            TaperState(type = 1, open = true).apply {
                setPoint("r1", "1")
                setPoint("start", "30")
                ncTab = 1
            }
        }
        // 외경 형상 3: T2 모서리 R0.5, 끝점 Z-20
        shot("taper-b-outer3", 2300) {
            TaperState(type = 3, open = true).apply {
                setPoint("r2", "0.5")
                setPoint("end", "-20")
            }
        }
        // 내경 형상 1(원본 06): 끝점 = 구멍 깊이 Z-20
        shot("taper-b-inner1", 2150) { TaperState(TaperKind.INNER, type = 1, open = true).apply { setPoint("end", "-20") } }
        // Z2 를 비우고 각도로 풀면 "자동" 값이 단위와 겹치지 않아야 한다(412dp)
        shot("taper-b-auto", 1100) {
            TaperState(type = 1, open = true).apply {
                setPoint("z2", "")
                setPoint("ang", "34.1")
            }
        }
        shot("taper-b-reverse", 2150) { TaperState(TaperKind.INNER, reverse = true, type = 1, open = true) }
        shot("taper-b-double", 2300) { TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).apply { setPoint("r2", "1") } }
        shot("taper-b-error") { TaperState(type = 3, open = true).apply { setPoint("ang", "30") } }
        shot("taper-groove", 2200) { TaperState(TaperKind.GROOVE, type = 1, open = true) }
        shot("taper-groove-error") { TaperState(TaperKind.GROOVE, type = 2, open = true).apply { setShared("insert", "9") } }
    }

    @Test
    fun tip() {
        Shots.both("taper-tip") {
            AppHost(it) {
                TaperScreen(TaperState(type = 1, open = true))
                TipSheet(4, 3, onPick = {}, onDismiss = {})
            }
        }
        Shots.both("taper-tiphelp", heightDp = 620) {
            AppHost(it) { Column(Modifier.fillMaxSize().padding(10.dp)) { AppCard { TipHelp() } } }
        }
    }
}
