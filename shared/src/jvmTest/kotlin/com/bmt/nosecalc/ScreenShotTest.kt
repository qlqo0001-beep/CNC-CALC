package com.bmt.nosecalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.GrooveWarning
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.TriangleWarning
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.dims
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import com.bmt.nosecalc.screens.round.roundWarningText
import com.bmt.nosecalc.screens.round.withA16
import com.bmt.nosecalc.screens.taper.SlopeForm
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.taper.grooveWarningText
import com.bmt.nosecalc.screens.taper.taperWarningText
import com.bmt.nosecalc.screens.triangle.TriangleState
import com.bmt.nosecalc.screens.triangle.triangleWarningText
import com.bmt.nosecalc.ui.Modal
import com.bmt.nosecalc.ui.NumberPad
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test

/**
 * 화면마다 라이트/다크 PNG → build/shots. 원본 사진(docs/store)과 나란히 보고 다듬는다.
 * 묶음(점검표는 docs/stage2-report.md 6절): 큰 글꼴(-fs130 · 글꼴 1.3배) · 좁은 폰(-w360 · 360dp) 판, 깨지기 쉬운 상태(긴 좌표 · 노란 경고 ·
 * 빨간 띠 여럿 · 80단 홈 · 거울 G40 …), 경고 글 모아 보기, PC 창(넓은 1280dp · 낮은 640dp)
 */
class ScreenShotTest {
    @Test
    fun taper() = Shots.both("02-taper") { App(it) }

    @Test
    fun appMenu() = Shots.both("01-menu", setup = { it.menuOpen = true }) { App(it) }

    @Test
    fun features() {
        // 테이퍼는 02-taper
        (Feature.menu - Feature.TAPER).forEach { f -> Shots.both("feature-${f.name.lowercase()}", setup = { it.open(f) }) { App(it) } }
    }

    @Test
    fun help() = Shots.both("help", setup = { it.helpOpen = true }) { App(it) }

    @Test
    fun keypad() = Shots.both("keypad") {
        AppHost(it) {
            TaperScreen()
            NumberPad("Z축 시작점", "mm", "5.0", onChange = {}, onClose = {})
        }
    }

    /** 키패드 글자칸에 13자(−999999999.999): 줄어 한 줄에 다 보여야 한다 */
    @Test
    fun keypadLong() = Shots.both("keypad-long") {
        AppHost(it) {
            TaperScreen()
            NumberPad("끝점 Z (T2 뒤)", "mm", "-999999999.999", onChange = {}, onClose = {})
        }
    }

    @Test
    fun modal() = Shots.both("modal") {
        AppHost(it) {
            TaperScreen()
            Modal("시뮬레이션", onDismiss = {}) {
                Text("모달 내용 자리", color = AppTheme.colors.textBody)
            }
        }
    }

    /**
     * 큰 글꼴 · 좁은 폰: 장면마다 -fs130(412dp · 글꼴 1.3배)과 -w360(360dp · 글꼴 그대로). 글이 잘리거나 겹치지 않고, 단추가 결과 띠 ·
     * 키패드 아래에 숨지 않고, 범례가 줄을 바꾸는지 본다. 긴 화면은 높이를 글꼴 · 폭만큼 늘려 끝까지
     */
    @Test
    fun largeFontAndNarrow() {
        for (s in scenes) {
            Shots.both("${s.name}-fs130", s.fs130, s.frames, s.setup, fontScale = 1.3f, frameMs = s.frameMs, content = s.content)
            if (s.narrow) Shots.both("${s.name}-w360", s.w360, s.frames, s.setup, widthDp = 360, frameMs = s.frameMs, content = s.content)
        }
    }

    /** 뒤보링(내경 역방향 3점호) + 보링바 A16R-SCLCR 09: 인서트를 Z 로 뒤집은 단면 · 띠의 앞/뒤는 이송 방향 기준 */
    @Test
    fun backBoringHolder() = with(scenes.single { it.name == "round-manual-inner-rev-holder" }) {
        Shots.both(name, heightDp, frames, setup, content = content)
    }

    /** 테이퍼 · 홈에서 찍지 않았던 상태: 4자리 좌표 · 한도 끝, 노란 경고(숫자는 나옴), 거울 G40, 긴 노즈 R, 80단 홈, 빨간 띠 셋 */
    @Test
    fun taperRisks() {
        // 긴 축(Z −1500): 보정 좌표 · 진입점 · 모서리 R 줄의 9자리 숫자가 끝자리까지(직접 보정 카드)
        taperShot("taper-b-big", 2500, CompMode.DIRECT) {
            TaperState(type = 3, open = true).apply {
                setPoint("x1", "1180"); setPoint("z1", "-1500")
                setPoint("x2", "1200"); setPoint("z2", "-1510")
                setPoint("r2", "2")
                setShared("zClear", "1.0")
            }
        }
        // 크기 한도(MAX_MM 10 000) 끝: 기계 보정 카드의 도면 좌표
        taperShot("taper-b-max", 2400) {
            TaperState(type = 3, open = true).apply {
                setPoint("x1", "9980"); setPoint("z1", "-9980")
                setPoint("x2", "9990"); setPoint("z2", "-9985")
                setShared("zClear", "1.0")
            }
        }
        // C0.3 면취 + 노즈 R0.8 → NOSE_GT_SLOPE(노랑, 결과 카드는 그대로)
        taperShot("taper-b-warn-gt", 2800) {
            TaperState(type = 1, open = true).apply {
                setPoint("x1", "49.4"); setPoint("z1", "0"); setPoint("x2", "50"); setPoint("z2", "-0.3")
                setShared("noseR", "0.8")
                setShared("zClear", "1.0")
            }
        }
        // 형상 3 의 오목 T1 R = 노즈 R → CONCAVE_R_LE_NOSE · '보정 R 없음' 줄 · G40 NC
        taperShot("taper-b-warn-concave", 2600, CompMode.DIRECT) { TaperState(type = 3, open = true).example().apply { setPoint("r1", "0.4") } }
        // 외경 형상 5(역테이퍼) → AGAINST_TOOL 은 접힌 파란 한 줄
        taperShot("taper-b-against", 2400) { TaperState(type = 5, open = true).example() }
        // 내경 2중 테이퍼 역방향 · T2 R1 · 직접 보정: 거울 G40 NC(G02↔G03) · 세 점 줄 · 가장 긴 형상 이름
        taperShot("taper-b-inner-double-rev", 2580, CompMode.DIRECT) {
            TaperState(TaperKind.INNER_DOUBLE, reverse = true, type = 1, open = true).example().apply { setPoint("r2", "1") }
        }
        // 11자리 노즈 R · 모서리 R: 직접 칩 · 좁은 모서리 R 칸은 넘치면 "…"(잘린 숫자가 멀쩡한 값처럼 보이지 않게)
        taperShot("taper-b-longnum", 1300) {
            TaperState(TaperKind.OUTER_DOUBLE, type = 1, open = true).example().apply {
                setPoint("r2", "0.12345678901")
                setShared("noseR", "0.12345678901")
            }
        }
        // 폭 120 · 인서트 2 · 겹침 0.5 → 80단: 접힌 단 표 · NC 줄 자름 · 오른쪽 인선(팁 4번)
        taperShot("taper-groove-many", 4200, frames = 12) {
            TaperState(TaperKind.GROOVE, type = 2, open = true).example().apply {
                setShared("width", "120")
                setShared("insert", "2")
            }
        }
        // 인서트 9 > 폭 8 · 바닥 50 > 외경 40 · 겹침 10 ≥ 인서트 → 빨간 띠 셋
        taperShot("taper-groove-errors", 1080) {
            TaperState(TaperKind.GROOVE, type = 1, open = true).example().apply {
                setShared("insert", "9"); setShared("bottom", "50"); setShared("overlap", "10")
            }
        }
    }

    /** 라운드 · 직각삼각형에서 찍지 않았던 상태: 오목 R = 노즈 R(보정 원호 없음), 한 줄 세 점, 12자리 · 89.999°, 세 값이 맞음 */
    @Test
    fun roundAndTriangleRisks() {
        roundShot("round-b2-concave", 3240, CompMode.DIRECT) { roundExample(2, open = true).apply { setDim(2, "cr1", "0.4") } }
        roundShot("round-manual-error", 1200) { roundExample(MANUAL, open = true).apply { setPoint("x2", "40"); setPoint("z2", "-5") } }
        triangleShot("triangle-huge") { TriangleState(mapOf("a" to "999999999999", "angA" to "89.999")) }
        triangleShot("triangle-three") { TriangleState(mapOf("a" to "3", "b" to "4", "c" to "5")) }
    }

    /** 경고 글 모아 보기(띠 색은 화면과 같게): 글 · 줄바꿈 · 한글 · 다크 대비 */
    @Test
    fun warningTexts() {
        val f = SlopeForm(TaperKind.OUTER, 1, false, 0.4, null, listOf(40.0, 50.0), listOf(0.0, -5.0), null, 1.0, listOf(0.0, 0.0))
        val back = f.copy(z = listOf(0.0, 5.0))
        val double = f.copy(kind = TaperKind.OUTER_DOUBLE, x = listOf(40.0, 50.0, 40.0), z = listOf(0.0, -5.0, -10.0), corners = listOf(0.0, 0.0, 0.0))
        val taper = TaperWarning.entries.flatMap { w ->
            when (w) {
                TaperWarning.MISSING_INPUT -> listOf(taperWarningText(w, f), taperWarningText(w, double)).map { it to WarnLevel.Info }
                TaperWarning.SHAPE_MISMATCH -> listOf(f, back, back.copy(reverse = true, z = listOf(-30.0, -35.0))).map { taperWarningText(w, it) to WarnLevel.Error }
                TaperWarning.AGAINST_TOOL -> listOf(taperWarningText(w, f) to WarnLevel.Info)
                TaperWarning.NOSE_GT_SLOPE, TaperWarning.CONCAVE_R_LE_NOSE, TaperWarning.ENTRY_PAST_AXIS -> listOf(taperWarningText(w, f) to WarnLevel.Warn)
                else -> listOf(taperWarningText(w, f) to WarnLevel.Error)
            }
        }
        val groove = GrooveWarning.entries.map { grooveWarningText(it) to if (it == GrooveWarning.MISSING_INPUT) WarnLevel.Info else WarnLevel.Error }
        Shots.both("texts-warn-taper", heightDp = 2080, frames = 12) { AppHost(it) { Texts(listOf("테이퍼" to taper, "외경 홈" to groove)) } }

        val types = (1..6) + MANUAL
        // 처음 화면처럼 치수를 다 비운 값 부족 글(모서리 R 은 비우면 0 이라 빠진 칸이 아니다)
        val round = types.map { t -> roundWarningText(RoundWarning.MISSING_INPUT, t, dims(t).map { it.key }.filterNot { it.startsWith("cr") }.toSet()) to WarnLevel.Info } +
            types.map { t -> roundWarningText(RoundWarning.SHAPE_MISMATCH, t) to WarnLevel.Error } +
            listOf(RoundWarning.BAD_INPUT, RoundWarning.CONTRADICTION, RoundWarning.CORNER_R_TOO_BIG).map { roundWarningText(it, 5) to WarnLevel.Error } +
            listOf(roundWarningText(RoundWarning.BAD_INPUT, 3, inner = true) to WarnLevel.Error) +
            listOf(1, MANUAL).map { roundWarningText(RoundWarning.NOSE_UNREACHABLE, it) to WarnLevel.Error } +
            listOf(roundWarningText(RoundWarning.CONCAVE_R_LE_NOSE, 2) to WarnLevel.Warn) +
            listOf(false, true).map { roundWarningText(RoundWarning.AGAINST_TOOL, 2, inner = it) to WarnLevel.Info }
        val triangle = TriangleWarning.entries.map { triangleWarningText(it) to if (it == TriangleWarning.MISSING_INPUT) WarnLevel.Info else WarnLevel.Error }
        Shots.both("texts-warn-round", heightDp = 2800, frames = 12) { AppHost(it) { Texts(listOf("라운드 R" to round, "직각삼각형" to triangle)) } }
    }

    /**
     * PC 창: 넓게 늘린 창(1280 × 720dp — 본문은 가운데 640dp) 의 메뉴 화면 · 키패드, 낮은 창(412 × 640dp, 150 % 배율 노트북) 의 도움말 · 키패드 —
     * 아래에 붙는 시트가 잘리지 않는지
     */
    @Test
    fun pcWindow() {
        Shots.both("pc-wide-a", widthDp = 1280, heightDp = 720) { App(it) }
        Shots.both("pc-wide-keypad", widthDp = 1280, heightDp = 720, frames = 150, frameMs = 16) {
            AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example().apply { editing = "x1" }) }
        }
        Shots.both("pc-short-help", heightDp = 640, setup = { it.helpOpen = true }) { App(it) }
        Shots.both("pc-short-keypad", heightDp = 640, frames = 150, frameMs = 16) {
            AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example().apply { editing = "z2" }) }
        }
    }

    private fun taperShot(name: String, heightDp: Int, comp: CompMode = CompMode.MACHINE, frames: Int = 40, state: () -> TaperState) =
        Shots.both(name, heightDp, frames, setup = { it.compMode = comp }) { AppHost(it) { TaperScreen(state()) } }

    private fun roundShot(name: String, heightDp: Int, comp: CompMode = CompMode.MACHINE, state: () -> RoundState) =
        Shots.both(name, heightDp, setup = { it.open(Feature.ROUND); it.compMode = comp }) { AppHost(it) { RoundScreen(state()) } }

    private fun triangleShot(name: String, state: () -> TriangleState) =
        Shots.both(name, 1240, setup = { it.open(Feature.TRIANGLE) }) { AppHost(it) { TriangleScreen(state()) } }
}

/**
 * 큰 글꼴 · 좁은 폰 판으로 찍는 장면. 긴 장면은 판마다 끝까지 보이는 높이(fs130 · w360), 폰 높이 장면(첫 화면 · 키패드)은 그대로.
 * narrow = -w360 판도 찍는다(taper-b 는 TaperShotsTest 의 taper-b-w360 — 360dp · 글꼴 1.3 · 끝까지 — 가 이미 찍는다).
 * 키패드는 고치는 칸을 시트 위로 끌어올리는 스크롤이 0.1초 걸음에서 출렁이므로 폰 프레임(16ms)으로 찍는다
 */
private class Scene(
    val name: String,
    val heightDp: Int = Shots.HEIGHT_DP,
    val fs130: Int = heightDp,
    val w360: Int = heightDp,
    keypad: Boolean = false,
    val narrow: Boolean = true,
    val setup: (AppState) -> Unit = {},
    val content: @Composable (AppState) -> Unit,
) {
    val frames = if (keypad) 150 else 40
    val frameMs = if (keypad) 16L else 100L
}

/** 앱에서처럼 테이퍼 위에 연 라운드 화면(헤더에 ←) */
private fun openRound(comp: CompMode = CompMode.MACHINE): (AppState) -> Unit = {
    it.open(Feature.ROUND)
    it.compMode = comp
}

private val scenes = listOf(
    Scene("01-menu", setup = { it.menuOpen = true }) { App(it) },
    // taper-b 의 첫 화면(폰 높이)
    Scene("taper-b", narrow = false) { AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example()) } },
    Scene("taper-b-keypad", keypad = true) { AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example().apply { editing = "z2" }) } },
    Scene("round-b2", fs130 = 3600, w360 = 3200, setup = openRound(CompMode.DIRECT)) { AppHost(it) { RoundScreen(roundExample(2, open = true)) } },
    Scene("round-b2-keypad", keypad = true, setup = openRound()) { AppHost(it) { RoundScreen(roundExample(2, open = true).apply { editing = "l2" }) } },
    Scene("triangle", fs130 = 1360, w360 = 1260, setup = { it.open(Feature.TRIANGLE) }) {
        AppHost(it) { TriangleScreen(TriangleState(mapOf("a" to "10", "angA" to "25"))) }
    },
    Scene("help", setup = { it.helpOpen = true }) { App(it) },
    // 내경 라운드: 고르기(형상 2~6 · 언더컷 표), 돔 + 보링바(빨간 닿음 띠 · 단면 · NC 줄), 뒤보링 + 보링바
    Scene("round-pick-inner", fs130 = 1110, w360 = 990, setup = openRound()) { AppHost(it) { RoundScreen(RoundState().apply { typeInner = true }) } },
    Scene("round-inner-b3-holder", fs130 = 4640, w360 = 4120, setup = openRound()) { AppHost(it) { RoundScreen(roundInnerExample(3).withA16()) } },
    Scene("round-manual-inner-rev-holder", 4000, fs130 = 4500, w360 = 4020, setup = openRound()) { AppHost(it) { RoundScreen(roundManualInnerRev().withA16()) } },
)

/** 묶음 제목 + 경고 띠 목록 */
@Composable
private fun Texts(groups: List<Pair<String, List<Pair<String, WarnLevel>>>>) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.gutter, vertical = Space.cardGap),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (title, items) ->
            Text(title, Modifier.padding(top = 4.dp), style = AppType.cardTitle, color = AppTheme.colors.textStrong)
            items.forEach { (text, level) -> WarningBanner(text, level = level) }
        }
    }
}
