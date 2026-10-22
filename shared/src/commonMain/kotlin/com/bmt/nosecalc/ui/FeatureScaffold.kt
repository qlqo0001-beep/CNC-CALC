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
import androidx.compose.ui.graphics.Color
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
 * 기능 화면 틀: 헤더 + 본문. 헤더는 모든 화면이 같다 — [← 뒤로] 제목 · 메뉴 · 도움말.
 * ← 는 앞 화면이나 화면 안 앞 단계(입력 → 형상 고르기)가 있을 때만 보인다. 화면 테마는 메뉴 안에서 고른다.
 * 메뉴를 누르면 헤더 아래로 APP MENU 가 펼쳐지고 본문은 흐려진다(01).
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
    Column(Modifier.fillMaxSize().background(c.page)) {
        FeatureHeader(feature, dimmed = state.menuOpen, onBack = if (state.hasBackStep) ({ state.back() }) else null) {
            AppsButton(state.menuOpen) { state.menuOpen = !state.menuOpen }
            CircleIconButton(AppIcons.Help, "도움말", { state.helpOpen = true }, outlined = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { Body(feature, content) }
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

/** 본문 + 헤더 아래로 펼쳐지는 APP MENU. 메뉴가 열리면 본문은 흐려지고 덮개를 누르면 닫힌다 */
@Composable
private fun Body(feature: Feature, content: @Composable BoxScope.() -> Unit) {
    val state = LocalAppState.current
    val c = AppTheme.colors
    val menuOpen = state.menuOpen
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
        OnBack(enabled = state.menuOpen, layer = true) { state.menuOpen = false }
        AppMenu(feature, onClose = { state.menuOpen = false })
    }
}

/**
 * APP MENU 패널(01): 머리(BMT 로고 · 닫기) → 화면 테마 → 지금 쓸 수 있는 계산 → '준비 중' 묶음 → 조용한 BMT 바닥글.
 * 지금 화면 카드는 누르면 메뉴만 닫히고, 준비 중 카드는 화면을 옮기지 않고 안내 토스트만 띄운다
 */
@Composable
private fun AppMenu(current: Feature, onClose: () -> Unit) {
    val state = LocalAppState.current
    val c = AppTheme.colors
    val (ready, soon) = Feature.menu.partition { it.ready }
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
        ThemeRow()
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.5.dp)) {
            // 지금 화면을 다시 열면 쌓인 그대로 메뉴만 닫힌다(AppState.open)
            ready.forEach { f -> MenuCard(f, current = f == current) { state.open(f) } }
        }
        Text(
            "준비 중",
            Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
            color = c.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.5.dp)) {
            soon.forEach { f -> MenuCard(f) { state.toast("${f.menuTitle}: 준비 중인 기능입니다") } }
        }
        MenuFooter()
    }
}

/** 화면 테마: 자동(휴대폰 · PC 설정 따라) · 밝게 · 어둡게. 고른 것은 다음에 켜도 남는다 */
@Composable
private fun ThemeRow() {
    val state = LocalAppState.current
    val c = AppTheme.colors
    val themes = listOf(null, false, true)
    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("화면 테마", color = c.textBody, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Segmented(
            listOf("자동", "밝게", "어둡게"),
            themes.indexOf(state.darkSaved),
            { state.setTheme(themes[it]) },
            Modifier.weight(1f),
            color = c.teal,
            fontSize = 14,
        )
    }
}

/**
 * 기능 카드: 40dp 아이콘 판(기능색 옅게) + 제목(중립색) · 부제 + 꺾쇠. 지금 화면은 청록 2dp 테두리에 꺾쇠 대신 '지금 화면'.
 * 아직 안 만든 기능은 낮은 카드(56dp)에 부제 없이 아이콘 · 제목을 흐리게, 꺾쇠 자리에 '준비 중'
 */
@Composable
private fun MenuCard(f: Feature, current: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val dim = if (f.ready) 1f else 0.6f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (f.ready) 72.dp else 56.dp)
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(c.card)
            .border(if (current) 2.dp else 1.dp, if (current) c.teal else c.lineCard, shape)
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
                fontSize = if (f.ready) 16.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (f.ready) {
                Spacer(Modifier.height(2.dp))
                Text(f.menuSub, color = c.textBody, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            current -> Tag("지금 화면", c.tealDeep, c.teal.copy(alpha = .12f))
            f.ready -> Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.textHint)
            else -> Tag("준비 중", c.textMuted, c.field, c.lineField)
        }
    }
}

/** 카드 오른쪽 작은 표(11.5sp 굵게, 모서리 6dp) */
@Composable
private fun Tag(text: String, color: Color, bg: Color, border: Color = bg) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text,
        Modifier.clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
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
                Text(keepWords(p), color = c.textBody, fontSize = 14.5.sp, lineHeight = 22.sp)
            }
        }
    }
}
