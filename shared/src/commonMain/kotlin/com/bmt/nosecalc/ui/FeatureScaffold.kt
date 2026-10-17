package com.bmt.nosecalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.ui.theme.AppTheme

/**
 * 기능 화면 틀: 헤더 + 본문. 헤더 버튼은
 * 테이퍼(첫 화면) = APPS · 테마 · 도움말, 나머지 = 테마 · 도움말 · 홈.
 * 테이퍼에서 APPS 를 누르면 헤더 아래로 APP MENU 가 펼쳐지고 본문은 흐려진다(01).
 * help = 도움말 바텀시트에 보일 문단들, helpExtra = 문단 뒤에 붙일 그림(테이퍼의 팁 번호 그림 등).
 */
@Composable
fun FeatureScaffold(
    feature: Feature,
    help: List<String>,
    helpExtra: (@Composable () -> Unit)? = null,
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
            CircleIconButton(AppIcons.Doc, "도움말", { state.helpOpen = true }, tint = c.green, glow = true)
            if (feature != Feature.TAPER) CircleIconButton(AppIcons.Home, "처음 화면", { state.home() })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { Body(menuOpen, content) }
    }
    if (state.helpOpen) HelpSheet(feature, help, helpExtra) { state.helpOpen = false }
}

/** 본문 + (테이퍼) 헤더 아래로 펼쳐지는 APP MENU. 메뉴가 열리면 본문은 흐려지고 덮개를 누르면 닫힌다 */
@Composable
private fun Body(menuOpen: Boolean, content: @Composable BoxScope.() -> Unit) {
    val state = LocalAppState.current
    val c = AppTheme.colors
    val blur by animateDpAsState(if (menuOpen) 5.dp else 0.dp)
    Box(Modifier.fillMaxSize().blur(blur).windowInsetsPadding(WindowInsets.navigationBars), content = content)
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

/** APP MENU 패널(01): 머리(BMT 로고 · CLOSE) → 기능 카드 6장 → BMT 배너 */
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
        Spacer(Modifier.height(32.dp))
        BmtBanner()
    }
}

@Composable
private fun MenuCard(f: Feature, onClick: () -> Unit) {
    val c = AppTheme.colors
    val color = c.features.getValue(f).menu
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.lineCard, shape)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureIcon(f, Modifier.size(29.dp))
        Spacer(Modifier.width(17.dp))
        Column(Modifier.weight(1f)) {
            Text(f.menuTitle, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(f.menuSub, color = c.textBody, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(15.dp), tint = color.copy(alpha = 0.3f))
    }
}

/** 원본의 프리미엄 배너 자리 — BMT 사내 앱 표시 */
@Composable
private fun BmtBanner() {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(87.dp)
            .shadow(if (c.dark) 0.dp else 6.dp, shape, spotColor = c.bmtBanner.first())
            .clip(shape)
            .background(Brush.horizontalGradient(c.bmtBanner))
            .border(2.dp, c.bmtBannerBorder, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(BmtMark, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(c.onAccent))
            Spacer(Modifier.width(10.dp))
            Text("BMT 노즈R 계산기", color = c.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em)
        }
        Spacer(Modifier.height(5.dp))
        Text("사내 전용", color = c.onAccent.copy(alpha = 0.88f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em)
    }
}

/** 헤더 도움말 버튼 → 화면별 사용법 */
@Composable
private fun HelpSheet(feature: Feature, help: List<String>, extra: (@Composable () -> Unit)?, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            FeatureIcon(feature, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("사용법", color = c.textStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(feature.menuSub, color = c.textMuted, fontSize = 13.sp)
            }
        }
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            help.forEachIndexed { i, p ->
                Row {
                    Box(
                        Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(c.field).border(1.dp, c.lineField, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Text(p, color = c.textBody, fontSize = 15.sp, lineHeight = 23.sp)
                }
            }
            extra?.invoke()
        }
        Spacer(Modifier.height(8.dp))
    }
}
