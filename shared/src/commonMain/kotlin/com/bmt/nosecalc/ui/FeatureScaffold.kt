package com.bmt.nosecalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

/** 도움말 시트의 한 묶음: 제목(빈 글이면 없이) → 그림(extra, 제목 바로 아래 — 그림 먼저) → 글머리표 문단들 */
data class HelpSection(val title: String, val items: List<String>, val extra: (@Composable () -> Unit)? = null)

/**
 * 기능 화면 틀: 헤더 + 본문. 헤더 버튼은
 * 테이퍼(첫 화면) = APPS · 테마 · 도움말, 나머지 = 테마 · 도움말 · 홈.
 * 테이퍼에서 APPS 를 누르면 헤더 아래로 APP MENU 가 펼쳐지고 본문은 흐려진다(01).
 * help = 도움말 시트의 묶음들(끝에 '장갑을 끼고 쓸 때' 가 늘 붙는다), helpStart = 시트를 열면 먼저 보일 묶음(입력 화면이면 값 넣기 등).
 * 본문은 가운데 최대 640dp — 넓은 PC 창에서 카드 · 칸이 늘어나지 않게. 헤더는 창 폭 그대로.
 */
@Composable
fun FeatureScaffold(
    feature: Feature,
    help: List<HelpSection>,
    helpStart: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalAppState.current
    val c = AppTheme.colors
    val menuOpen = feature == Feature.TAPER && state.menuOpen
    Column(Modifier.fillMaxSize().background(c.page)) {
        FeatureHeader(feature, dimmed = menuOpen) {
            if (feature == Feature.TAPER) AppsButton(menuOpen) { state.menuOpen = !state.menuOpen }
            if (c.dark) {
                CircleIconButton(AppIcons.Moon, "밝은 테마로", { state.setDark(false) }, tint = c.moon)
            } else {
                CircleIconButton(AppIcons.Sun, "어두운 테마로", { state.setDark(true) }, tint = c.sun)
            }
            CircleIconButton(AppIcons.Help, "도움말", { state.helpOpen = true }, outlined = true)
            if (feature != Feature.TAPER) CircleIconButton(AppIcons.Home, "처음 화면", { state.home() })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { Body(menuOpen, content) }
    }
    if (state.helpOpen) HelpSheet(feature, help, helpStart) { state.helpOpen = false }
}

/** 예전 꼴(문단 목록 + 끝 그림): 제목 없는 한 묶음으로 싼다 — 그림은 묶음 맨 위로 간다 */
@Composable
fun FeatureScaffold(
    feature: Feature,
    help: List<String>,
    helpExtra: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = FeatureScaffold(feature, listOf(HelpSection("", help, helpExtra)), content = content)

/** 본문 + (테이퍼) 헤더 아래로 펼쳐지는 APP MENU. 메뉴가 열리면 본문은 흐려지고 덮개를 누르면 닫힌다 */
@Composable
private fun Body(menuOpen: Boolean, content: @Composable BoxScope.() -> Unit) {
    val state = LocalAppState.current
    val c = AppTheme.colors
    val blur by animateDpAsState(if (menuOpen) 5.dp else 0.dp)
    Box(Modifier.fillMaxSize().blur(blur).windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 640.dp).fillMaxSize(), content = content)
    }
    AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(interactionSource = null, indication = null) { state.menuOpen = false },
        )
    }
    AnimatedVisibility(
        menuOpen,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        OnBack(enabled = state.menuOpen) { state.menuOpen = false }
        AppMenu(onPick = { state.open(it) }, onClose = { state.menuOpen = false })
    }
}

/** APP MENU 패널(01): 머리(BMT 로고 · CLOSE) → 기능 카드 6장 → 조용한 BMT 바닥글 */
@Composable
private fun AppMenu(onPick: (Feature) -> Unit, onClose: () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.menuTop, c.menuBottom)))
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 34.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            BmtLogo(height = 26.dp, color = c.bmtBlue)
            Spacer(Modifier.weight(1f))
            PillButton("닫기", AppIcons.Close, onClose)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.5.dp)) {
            Feature.menu.forEach { f -> MenuCard(f) { onPick(f) } }
        }
        MenuFooter()
    }
}

/**
 * 기능 카드: 40dp 아이콘 판(기능색 옅게) + 제목(중립색) · 부제 + 꺾쇠.
 * 아직 안 만든 기능은 아이콘·제목을 흐리게 하고 꺾쇠 자리에 '준비 중' 표시(눌러도 준비 중 화면이 열린다)
 */
@Composable
private fun MenuCard(f: Feature, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val dim = if (f.ready) 1f else 0.6f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.lineCard, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .alpha(dim)
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.features.getValue(f).menu.copy(alpha = if (c.dark) .18f else .12f)),
            contentAlignment = Alignment.Center,
        ) {
            FeatureIcon(f, Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                f.menuTitle,
                color = c.textStrong.copy(alpha = dim),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(f.menuSub, color = c.textBody, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (f.ready) {
            Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.textHint)
        } else {
            val tag = RoundedCornerShape(6.dp)
            Text(
                "준비 중",
                Modifier.clip(tag).background(c.field).border(1.dp, c.lineField, tag).padding(horizontal = 8.dp, vertical = 3.dp),
                color = c.textMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** 메뉴 맨 아래 BMT 사내 앱 표시(원본의 프리미엄 배너 자리). 눌리는 단추처럼 보이지 않게 바탕 없이 흐린 글 */
@Composable
private fun MenuFooter() {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BmtMark, null, Modifier.size(16.dp), tint = c.textMuted)
        Spacer(Modifier.width(6.dp))
        Text("BMT 노즈R 계산기 · 사내 전용", color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 모든 화면 도움말 끝에 붙는 묶음 */
private val GloveHelp = HelpSection(
    "장갑을 끼고 쓸 때",
    listOf("장갑을 낀 채 누르려면 휴대폰 설정 > 디스플레이 > 터치 민감도(삼성)를 켜고, 터치되는 장갑을 쓰세요."),
)

/** 헤더 도움말 버튼 → 화면별 사용법. 머리(아이콘 · 사용법 · 닫기) 아래 묶음들을 창 높이 85% 까지, start 묶음부터 보인다 */
@Composable
private fun HelpSheet(feature: Feature, help: List<HelpSection>, start: Int, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val sections = help + GloveHelp
    BottomSheet(onDismiss, maxHeightFraction = 0.85f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeatureIcon(feature, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("사용법", style = AppType.sheetTitle, color = c.textStrong)
                Text(feature.menuSub, color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            PillButton("닫기", AppIcons.Close, onDismiss)
        }
        LazyColumn(Modifier.weight(1f, fill = false), state = rememberLazyListState(start.coerceIn(sections.indices))) {
            items(sections) { HelpBlock(it) }
        }
    }
}

/** 묶음 하나: 위 16dp 띄우고 제목(15sp) → 그림 → 글머리표 문단(14.5sp / 22sp) */
@Composable
private fun HelpBlock(s: HelpSection) {
    val c = AppTheme.colors
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.title.isNotEmpty()) Text(s.title, color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        s.extra?.invoke()
        s.items.forEach { p ->
            Row {
                Text("•", Modifier.width(16.dp), color = c.teal, fontSize = 14.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Text(p, color = c.textBody, fontSize = 14.5.sp, lineHeight = 22.sp)
            }
        }
    }
}
