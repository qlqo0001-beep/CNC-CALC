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
import com.bmt.nosecalc.screens.taper.example
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

    /** 결과 화면은 [example] 값(2단계 처음 값)으로 — 화면은 이제 도면 치수 · 여유 0 에서 시작한다. 끝점을 비우면 마지막 T 점에서 끝난다 */
    @Test
    fun input() {
        // 처음 화면: 치수 0 → 빨간 멈춤 대신 파란 안내, 그림은 그대로
        shot("taper-b-empty") { TaperState(type = 1, open = true) }
        shot("taper-b", 2280) { TaperState(type = 1, open = true).example() }
        // 외경 형상 1: T1 모서리 R1, 시작점 단면 X30 → 결과 카드에 R 시작·끝 접점, NC 는 직접 보정 탭
        shot("taper-b-corner", 2340) {
            TaperState(type = 1, open = true).example().apply {
                setPoint("r1", "1")
                setPoint("start", "30")
                ncTab = 1
            }
        }
        // 외경 형상 3: T2 모서리 R0.5, 끝점 Z-20
        shot("taper-b-outer3", 2440) {
            TaperState(type = 3, open = true).example().apply {
                setPoint("r2", "0.5")
                setPoint("end", "-20")
            }
        }
        // 내경 형상 1(원본 06): 끝점 = 구멍 깊이 Z-20
        shot("taper-b-inner1", 2300) { TaperState(TaperKind.INNER, type = 1, open = true).example().apply { setPoint("end", "-20") } }
        // Z2 를 비우고 각도로 풀면 "자동" 값이 단위와 겹치지 않아야 한다(412dp)
        shot("taper-b-auto", 1100) {
            TaperState(type = 1, open = true).example().apply {
                setPoint("z2", "")
                setPoint("ang", "34.1")
            }
        }
        shot("taper-b-reverse", 2280) { TaperState(TaperKind.INNER, reverse = true, type = 1, open = true).example() }
        shot("taper-b-double", 2560) { TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example().apply { setPoint("r2", "1") } }
        // 각도 30 ≠ 두 점의 45° → CONTRADICTION: 빨간 띠는 요약 바로 아래, X1 · Z1 · X2 · Z2 · 각도에 빨간 테
        shot("taper-b-error", 1150) { TaperState(type = 3, open = true).example().apply { setPoint("ang", "30") } }
        // 정방향인데 Z2 를 + 로(척 반대쪽) → Z 부호 글, Z 칸만 빨간 테
        shot("taper-b-zsign", 1150) { TaperState(type = 1, open = true).example().apply { setPoint("z2", "5") } }
        // Z 여유 0.5 < 노즈 R 0.8 → 진입은 노즈 R 만큼(입력 칸 아래 파란 안내)
        shot("taper-b-zclear", 1300) {
            TaperState(type = 3, open = true).example().apply {
                setShared("noseR", "0.8")
                setShared("zClear", "0.5")
            }
        }
        shot("taper-groove-empty") { TaperState(TaperKind.GROOVE, type = 1, open = true) }
        shot("taper-groove", 2200) { TaperState(TaperKind.GROOVE, type = 1, open = true).example() }
        // 인서트 폭 9 > 홈 폭 8 → 두 칸에 빨간 테
        shot("taper-groove-error") { TaperState(TaperKind.GROOVE, type = 2, open = true).example().apply { setShared("insert", "9") } }
    }

    @Test
    fun tip() {
        Shots.both("taper-tip") {
            AppHost(it) {
                TaperScreen(TaperState(type = 1, open = true).example())
                TipSheet(4, 3, onPick = {}, onDismiss = {})
            }
        }
        Shots.both("taper-tiphelp", heightDp = 620) {
            AppHost(it) { Column(Modifier.fillMaxSize().padding(10.dp)) { AppCard { TipHelp() } } }
        }
    }
}
