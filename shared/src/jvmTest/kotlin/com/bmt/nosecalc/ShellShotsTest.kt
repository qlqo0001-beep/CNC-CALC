package com.bmt.nosecalc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.TaperHelp
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.FeatureIcon
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.HelpSection
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import com.bmt.nosecalc.ui.topicJosa
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 앱 틀(헤더 단추 · 기능 아이콘 · 준비 중 화면 · 도움말 시트 · 넓은 PC 창)을 라이트/다크로 → build/shots */
class ShellShotsTest {
    /**
     * 헤더 단추(테이퍼 · 라운드, 라운드를 연 뒤라 둘 다 ←)와 알약 단추: 누르는 자리(레이아웃 크기)를 재어 48dp 이상인지 보고,
     * 그 자리를 빨간 점선 네모로 덧그려 찍는다
     */
    @Test
    fun headerButtons() {
        val boxes = clickBoxes(HEADER_H) { HeaderGallery() }
        assertEquals(
            listOf("뒤로", "메뉴", "도움말", "뒤로", "메뉴", "도움말", "닫기", "형상 다시 고르기"),
            boxes.map { it.first },
        )
        boxes.forEach { (name, r) ->
            assertTrue(r.width.roundToInt() >= 48 && r.height.roundToInt() >= 48, "$name 누르는 자리 ${r.width} × ${r.height}dp")
        }
        Shots.both("header-buttons", heightDp = HEADER_H, setup = { it.open(Feature.ROUND) }) {
            AppHost(it) {
                Box(Modifier.fillMaxSize()) {
                    HeaderGallery()
                    Canvas(Modifier.matchParentSize()) {
                        boxes.forEach { (_, r) ->
                            drawRect(
                                Color.Red, Offset(r.left.dp.toPx(), r.top.dp.toPx()), Size(r.width.dp.toPx(), r.height.dp.toPx()),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 라운드에서 연 메뉴: 헤더 ← · 메뉴, 화면 테마, 지금 화면 표(라운드), 아래 '준비 중' 묶음.
     * 준비 중 카드(G코드 시뮬레이터)를 눌러 뜬 토스트까지 — 토스트는 실제 시간 2.5초면 사라지므로 누른 뒤 몇 프레임만
     */
    @Test
    fun menuFromRound() = Shots.both(
        "menu-round",
        frames = 14,
        tapDp = Offset(200f, 574f),
        setup = {
            it.open(Feature.ROUND)
            it.menuOpen = true
        },
    ) { App(it) }

    /** 좁은 폰 · 큰 글꼴 메뉴: 화면 테마 세 칸과 카드 글이 잘리지 않는지 */
    @Test
    fun menuNarrow() = Shots.both(
        "menu-w360",
        widthDp = 360,
        fontScale = 1.3f,
        setup = {
            it.open(Feature.TRIANGLE)
            it.menuOpen = true
        },
    ) { App(it) }

    /** 기능 아이콘 일곱: 메뉴 판(40dp 판에 24dp) · 준비 중 화면(56dp) 크기로 */
    @Test
    fun icons() = Shots.both("shell-icons", heightDp = 300) { AppHost(it) { IconGallery() } }

    /** 준비 중 화면을 좁은 폰 · 큰 글꼴로: 이름 뒤 줄바꿈이 어절을 쪼개지 않는지 */
    @Test
    fun comingSoonNarrow() =
        Shots.both("feature-polar-w360", widthDp = 360, fontScale = 1.3f, setup = { it.open(Feature.POLAR) }) { App(it) }

    /** 도움말 시트: 머리의 닫기 · 묶음 제목 · 끝에 늘 붙는 장갑 묶음(준비 중 화면은 짧아 한눈에 보인다) */
    @Test
    fun helpSheet() = Shots.both(
        "help-polar",
        setup = {
            it.open(Feature.POLAR)
            it.helpOpen = true
        },
    ) { App(it) }

    /** 묶음 제목 · 그림 먼저 · helpStart: 둘째 묶음(그림 + 문단)부터 열린다. 첫 묶음 제목은 위로 밀려 안 보여야 한다 */
    @Test
    fun helpSections() = Shots.both("help-sections", setup = { it.helpOpen = true }) {
        AppHost(it) { FeatureScaffold(Feature.TAPER, TaperHelp, helpStart = 1) {} }
    }

    /** 넓은 PC 창(1280dp): 본문은 가운데 640dp 에 머물고 카드 · 그림은 폰과 같은 크기, 헤더만 창 폭 */
    @Test
    fun wideWindow() = Shots.both("taper-b-wide", heightDp = 1400, widthDp = 1280) {
        AppHost(it) { TaperScreen(TaperState(type = 1, open = true).example()) }
    }

    @Test
    fun comingSoonJosa() {
        assertEquals("은", topicJosa(Feature.CUTTING.menuTitle))
        assertEquals("는", topicJosa(Feature.SIMULATOR.menuTitle))
        assertEquals("은", topicJosa(Feature.POLAR.menuTitle))
        assertEquals("은", topicJosa("나사 G76 계산"))
        assertEquals("는", topicJosa("G76"))
        assertEquals(listOf(Feature.TAPER, Feature.ROUND, Feature.TRIANGLE), Feature.entries.filter { it.ready })
    }

    private companion object {
        const val HEADER_H = 240
    }
}

/** 테이퍼 헤더 + 라운드 헤더(둘 다 ← · 메뉴 · 도움말) + 알약 단추 둘 */
@Composable
private fun HeaderGallery() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.height(80.dp)) { FeatureScaffold(Feature.TAPER, emptyList<HelpSection>()) {} }
        Box(Modifier.height(80.dp)) { FeatureScaffold(Feature.ROUND, emptyList<HelpSection>()) {} }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("닫기", AppIcons.Close, {})
            PillButton("형상 다시 고르기", null, {})
        }
    }
}

@Composable
private fun IconGallery() {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter, vertical = Space.cardGap)) {
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            Text("기능 아이콘 — 메뉴 판 40dp(24dp) · 준비 중 56dp", style = AppType.caption, color = c.textMuted)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Feature.entries.forEach { f ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.features.getValue(f).menu.copy(alpha = if (c.dark) .18f else .12f)),
                        contentAlignment = Alignment.Center,
                    ) { FeatureIcon(f, Modifier.size(24.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Feature.entries.take(4).forEach { FeatureIcon(it, Modifier.size(56.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Feature.entries.drop(4).forEach { FeatureIcon(it, Modifier.size(56.dp)) }
                Box(Modifier.size(56.dp))
            }
        }
    }
}

/**
 * 누를 수 있는 것(OnClick)마다 이름(설명 · 글)과 누르는 자리(dp). 크기는 레이아웃 노드 바깥 크기(최소 누름 크기 포함),
 * 가운데는 안쪽 좌표의 가운데 — 버튼의 여백 · 최소 크기 모두 위아래 · 좌우가 같아 가운데가 맞는다
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun clickBoxes(heightDp: Int, content: @Composable () -> Unit): List<Pair<String, Rect>> {
    val d = Shots.DENSITY
    val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
    state.setTheme(false)
    // 라운드를 연 뒤 — 헤더에 ← 가 보이게
    state.open(Feature.ROUND)
    val scene = ImageComposeScene((Shots.WIDTH_DP * d).roundToInt(), (heightDp * d).roundToInt(), Density(d)) {
        AppHost(state) { content() }
    }
    try {
        // 글꼴이 몇 프레임 뒤에 바뀌어 알약 폭이 달라지므로 Shots 처럼 여러 프레임 흘린다
        repeat(40) {
            Thread.sleep(15)
            scene.render(it * 100_000_000L).close()
        }
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
            .filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
            .map { n ->
                val name = n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()
                val center = n.layoutInfo.coordinates.boundsInRoot().center / d
                val size = Size(n.layoutInfo.width / d, n.layoutInfo.height / d)
                name to Rect(center - Offset(size.width / 2, size.height / 2), size)
            }
            .sortedWith(compareBy({ (it.second.top / 40).toInt() }, { it.second.left }))
    } finally {
        scene.close()
    }
}
