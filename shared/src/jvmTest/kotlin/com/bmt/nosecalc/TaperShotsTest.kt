package com.bmt.nosecalc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.TipHelp
import com.bmt.nosecalc.screens.taper.TipSheet
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.taper.userAngleExample
import com.bmt.nosecalc.ui.AppCard
import kotlin.test.Test

/**
 * 테이퍼·홈 화면 A·B 를 라이트/다크로 → build/shots/taper-*. 원본 02·11 과 나란히 본다. 보정 방식은 처음 값(기계 보정), comp 로 바꿔 찍는다.
 * 각도 기준도 처음 값(X축), ref 로 바꿔 찍는다
 */
class TaperShotsTest {
    private fun shot(
        name: String,
        heightDp: Int = Shots.HEIGHT_DP,
        frames: Int = 40,
        frameMs: Long = 100,
        scrollToEnd: Boolean = false,
        comp: CompMode = CompMode.MACHINE,
        ref: AngleRef = AngleRef.X,
        state: () -> TaperState,
    ) = Shots.both(name, heightDp, frames, setup = { it.compMode = comp; it.angleRef = ref }, frameMs = frameMs, scrollToEnd = scrollToEnd) {
        AppHost(it) { TaperScreen(state()) }
    }

    @Test
    fun pick() {
        shot("taper-a-inner") { TaperState(TaperKind.INNER) }
        // 고른 형상(B 에서 돌아온 때): 청록 테두리 + 체크
        shot("taper-a-reverse") { TaperState(reverse = true, type = 2) }
        shot("taper-a-inner-reverse") { TaperState(TaperKind.INNER, reverse = true) }
        shot("taper-a-double") { TaperState(TaperKind.OUTER_DOUBLE) }
        shot("taper-a-inner-double") { TaperState(TaperKind.INNER_DOUBLE) }
        shot("taper-a-groove") { TaperState(TaperKind.GROOVE) }
        // 넓은 창(본문 640dp)은 형상 카드 3열
        Shots.both("taper-a-wide", heightDp = 900, widthDp = 700) { AppHost(it) { TaperScreen(TaperState(TaperKind.OUTER_DOUBLE)) } }
    }

    /** 결과 화면은 [example] 값(2단계 처음 값)으로 — 화면은 이제 도면 치수 · 여유 빈칸에서 시작한다. 끝점을 비우면 마지막 T 점에서 끝난다 */
    @Test
    fun input() {
        // 처음 화면: 치수 빈칸 → 빨간 멈춤 대신 파란 안내, 그림은 그대로(2중 · 칸 하나만 넣은 상태는 아래 fresh)
        shot("taper-b-empty", 1300) { TaperState(type = 1, open = true) }
        // 처음 보정 방식(기계 보정): 결과 카드 맨 위 R · T, 도면 좌표, 접힌 '직접 보정 좌표도 보기' · 뷰어는 도면 좌표 경로 · NC 는 G41/G42
        shot("taper-b", 2540) { TaperState(type = 1, open = true).example() }
        // 외경 형상 1: T1 모서리 R1, 시작점 단면 X30 → 직접 보정: 보정 좌표 카드에 R 시작·끝 접점, NC 첫 줄 '(G40 DIRECT R0.4 T3)'
        shot("taper-b-corner", 2640, comp = CompMode.DIRECT) {
            TaperState(type = 1, open = true).example().apply {
                setPoint("r1", "1")
                setPoint("start", "30")
            }
        }
        // 외경 형상 3: T2 모서리 R0.5, 끝점 Z-20
        shot("taper-b-outer3", 2540) {
            TaperState(type = 3, open = true).example().apply {
                setPoint("r2", "0.5")
                setPoint("end", "-20")
            }
        }
        // 내경 형상 1(원본 06): 끝점 = 구멍 깊이 Z-20
        shot("taper-b-inner1", 2440) { TaperState(TaperKind.INNER, type = 1, open = true).example().apply { setPoint("end", "-20") } }
        // Z2 를 비우고 각도로 풀면 "자동" 값이 단위와 겹치지 않아야 한다(412dp). 34.1° 는 Z축 기준 숫자(골든과 같게 기준을 밝힘)
        shot("taper-b-auto", 1100, ref = AngleRef.Z) {
            TaperState(type = 1, open = true).example().apply {
                setPoint("z2", "")
                setPoint("ang", "34.1")
            }
        }
        // 내경 역방향 · 직접 보정: 팁 1번이 NC 첫 줄 주석에도. 척 쪽(구멍 안)에서 시작해 NC 위에 접근 줄 안내 띠(접힘)
        shot("taper-b-reverse", 2540, comp = CompMode.DIRECT) { TaperState(TaperKind.INNER, reverse = true, type = 1, open = true).example() }
        shot("taper-b-double", 2640) { TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example().apply { setPoint("r2", "1") } }
        // 각도 30 ≠ 두 점의 45° → CONTRADICTION: 빨간 띠는 요약 바로 아래(그 아래 '각도 비우고 계산'), X1 · Z1 · X2 · Z2 · 각도에 빨간 테
        shot("taper-b-error", 1150, ref = AngleRef.Z) { TaperState(type = 3, open = true).example().apply { setPoint("ang", "30") } }
        // 정방향인데 Z2 를 + 로(척 반대쪽) → Z 부호 글, Z 칸만 빨간 테
        shot("taper-b-zsign", 1150) { TaperState(type = 1, open = true).example().apply { setPoint("z2", "5") } }
        // Z 여유 0.5 < 노즈 R 0.8 → 진입은 노즈 R 만큼(입력 칸 아래 파란 안내)
        shot("taper-b-zclear", 1300) {
            TaperState(type = 3, open = true).example().apply {
                setShared("noseR", "0.8")
                setShared("zClear", "0.5")
            }
        }
        shot("taper-groove-empty", 1100) { TaperState(TaperKind.GROOVE, type = 1, open = true) }
        shot("taper-groove", 2280) { TaperState(TaperKind.GROOVE, type = 1, open = true).example() }
        // 인서트 폭 9 > 홈 폭 8 → 두 칸에 빨간 테
        shot("taper-groove-error") { TaperState(TaperKind.GROOVE, type = 2, open = true).example().apply { setShared("insert", "9") } }
    }

    /**
     * 빈칸 시작(사용자 요청 2026-10-28 "0 이 적힌 좌표들을 다 빈칸으로"): 2중 테이퍼 첫 화면, 홑 · 2중 · 홈에 칸 하나만 넣은 상태(홑 · 홈 첫 화면은
     * taper-b-empty · taper-groove-empty) — 빨강 없이 파란 안내만, 결과 · 형상 · NC 는 아직 없고 요약 그림은 개념도 그대로, 여유 칸은 흐린 '비우면 0'
     */
    @Test
    fun fresh() {
        shot("taper-b-one", 1300) { TaperState(type = 1, open = true).apply { setPoint("x1", "40") } }
        shot("taper-b-double-empty", 1300) { TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true) }
        shot("taper-b-double-one", 1300) { TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).apply { setPoint("x1", "40") } }
        shot("taper-groove-one", 1100) { TaperState(TaperKind.GROOVE, type = 1, open = true).apply { setShared("width", "8") } }
    }

    /**
     * T1 ⇄ T2 바꾸기를 켠 화면(골든 UiGoldenTest.taperFlip 과 같은 상태): 스위치(켜짐 · 지금 방향 한 줄), 요약 그림의 T 이름 · 이송 화살 ·
     * 이름 순서, 칸 값이 같은 실제 점에 남음, 결과 카드 · 뷰어 라벨 · NC 가 새 순서(G41). 외경 형상 1 은 직접 보정도. 척 쪽에서 시작하므로 NC 위에 접근 줄 안내 띠(접힘),
     * 고르기 화면의 뒤집은 카드 이름 앞에 ⇄
     */
    @Test
    fun flip() {
        shot("taper-b-flip", 2680) { TaperState(type = 1, open = true).example().apply { toggleFlip() } }
        shot("taper-b-flip-direct", 2760, comp = CompMode.DIRECT) { TaperState(type = 1, open = true).example().apply { toggleFlip() } }
        shot("taper-b-flip-outer3", 2760) {
            TaperState(type = 3, open = true).example().apply {
                setPoint("r2", "0.5")
                setPoint("end", "-20")
                toggleFlip()
            }
        }
        shot("taper-b-flip-inner1", 2660) { TaperState(TaperKind.INNER, type = 1, open = true).example().apply { setPoint("end", "-20"); toggleFlip() } }
        shot("taper-b-flip-double", 2780) { TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example().apply { setPoint("r2", "1"); toggleFlip() } }
        // FANUC 취소 규칙(2026-10-28 — 골든 UiGoldenTest.taperFlip): 뒤집어 앞 끝 Z5 까지 → 움직임 없는 G40 + G01. 화면 역방향 · 역바이트 팁 1 · 뒤집기.
        // 정방향 내경에 역보링바 팁 1(바이트 반대) → 경사 연장선 위 lead 에 직각 진입, 끝은 움직임 없는 G40
        shot("taper-b-flip-outer3-end", 2760) { TaperState(type = 3, open = true).example().apply { setPoint("start", "5"); toggleFlip() } }
        shot("taper-b-flip-reverse", 2660) { TaperState(TaperKind.INNER, reverse = true, type = 1, open = true).example().apply { toggleFlip() } }
        shot("taper-b-tip1-inner1", 2660) { TaperState(TaperKind.INNER, type = 1, open = true).example().apply { tip = 1 } }
        // 고르기 화면: 뒤집은 형상 1 카드만 T 이름 · 화살이 반대
        shot("taper-a-flip") { TaperState(type = 1).apply { toggleFlip() } }
        // 키패드 그림도 뒤집은 T1(위 왼쪽 점)을 짚는다
        shot("taper-b-flip-keypad", frames = 150, frameMs = 16) { TaperState(type = 1, open = true).example().apply { toggleFlip(); editing = "x1" } }
    }

    /**
     * 각도 기준(사용자 결정 2026-10-28): 사용자 예(외경 형상 3 · 뒤집기 · R0.8, 각도 30, Z1 비움)를 X축 기준(처음) · Z축 기준으로 —
     * 각도 칸 옆 스위치, 자동 Z1(−25.619 · −29.256), 요약 그림의 호(세로 점선 · 가로 점선), 결과 카드의 각도 이름.
     * 2중 테이퍼는 결과 각 아래 스위치(taper-b-double · -flip-double), 좁은 폰 · 큰 글꼴은 taper-b-w360 · -angle-w360
     */
    @Test
    fun angleRef() {
        shot("taper-b-angle-x30", 1500) { userAngleExample() }
        shot("taper-b-angle-z30", 1500, ref = AngleRef.Z) { userAngleExample() }
        shot("taper-a-angle-z", ref = AngleRef.Z) { TaperState() }
        Shots.both("taper-b-angle-w360", heightDp = 1500, widthDp = 360, fontScale = 1.3f) {
            AppHost(it) { TaperScreen(userAngleExample()) }
        }
        Shots.both("taper-b-double-w360", heightDp = 3200, widthDp = 360, fontScale = 1.3f) {
            AppHost(it) { TaperScreen(TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example()) }
        }
    }

    /** 좁은 폰 · 큰 글꼴(360dp · 1.3): 보정 방식 스위치 두 줄 칸 · 기계 보정 카드의 R · T 와 도면 좌표가 잘리지 않는다 */
    @Test
    fun narrow() {
        Shots.both("taper-b-w360", heightDp = 3000, widthDp = 360, fontScale = 1.3f) {
            AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example()) }
        }
        // 뒤집기 스위치의 제목 · 방향 한 줄과 바뀐 시작 · 끝 칸 이름이 좁은 폰 · 큰 글꼴에서 잘리지 않는다
        Shots.both("taper-b-flip-w360", heightDp = 1400, widthDp = 360, fontScale = 1.3f) {
            AppHost(it) { TaperScreen(TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example().apply { toggleFlip() }) }
        }
    }

    /**
     * 키패드(폰 높이): 그림은 고치는 칸의 점 · 치수만 초점색, 위에 결과 한 줄(기계 보정은 T1 · T2 도면 좌표 / 첫 단 · 끝 단), 오른쪽 아래 두 칸 높이 "확인".
     * 칸을 끌어올리는 스크롤이 0.1초 걸음에서는 출렁이므로 폰 프레임(16ms)으로 찍는다
     */
    @Test
    fun keypad() {
        shot("taper-b-keypad", frames = 150, frameMs = 16) { TaperState(type = 1, open = true).example().apply { editing = "z2" } }
        shot("taper-groove-keypad", frames = 150, frameMs = 16) { TaperState(TaperKind.GROOVE, type = 1, open = true).example().apply { editing = "insert" } }
    }

    /**
     * 폰 첫 화면: 결과 카드가 화면 밖이라 아래 결과 띠(직접 보정이면 T1′ · T2′ 보정 좌표 · 결과 단추)가 뜬다. 기계 보정의 띠(도면 좌표)는 round-b-top.
     * 멈춘 까닭(빨간 띠)은 입력 카드 위라 915dp 폰은 끝까지 내려도 보인다 — 작은 폰(640dp)에서 끝까지 내리면 띠가 빨갛게 그 까닭을 남긴다
     */
    @Test
    fun dock() {
        shot("taper-b-top", comp = CompMode.DIRECT) { TaperState(type = 1, open = true).example() }
        shot("taper-b-error-dock", 640, scrollToEnd = true, ref = AngleRef.Z) { TaperState(type = 3, open = true).example().apply { setPoint("ang", "30") } }
    }

    /**
     * 각도 칸(taper-b 의 (114, 635)dp — T1 ⇄ T2 바꾸기 줄 아래)을 눌러 연 키패드: 처음 기준(X축)의 안내 줄(단면에서 잰 각 · 가로선 · 꼭지각은 Z축으로) · 그림의 세로 점선 호 · '비우기 · 자동 계산'.
     * 묶음 키패드라 칸이 시트 위로 올라온다 — 폰 프레임(16ms)으로 찍는다
     */
    @Test
    fun keypadHint() = Shots.both("taper-b-keypad-ang", frames = 150, frameMs = 16, tapDp = Offset(114f, 635f)) {
        AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example()) }
    }

    /** 입력 화면에서 연 도움말: '값 넣기' 묶음과 팁 번호 그림이 스크롤 없이 보인다 */
    @Test
    fun help() = Shots.both("taper-help", setup = { it.helpOpen = true }) {
        AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example()) }
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
