package com.bmt.nosecalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** 화면 맨 위 층에 그릴 것 하나(바텀시트·모달). [App] 이 쌓인 순서대로 그린다 */
internal class OverlayEntry(val blurBehind: Boolean) {
    var content: @Composable () -> Unit by mutableStateOf({})
    var onKey: ((KeyEvent) -> Boolean)? = null
}

/** 부른 자리와 상관없이 화면 전체 위에 띄운다. 뒤로(뒤로 키·Esc)를 누르면 onDismiss */
@Composable
internal fun Overlay(
    onDismiss: () -> Unit,
    blurBehind: Boolean = false,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val state = LocalAppState.current
    val entry = remember { OverlayEntry(blurBehind) }
    SideEffect {
        entry.content = content
        entry.onKey = onKey
    }
    DisposableEffect(state) {
        state.overlays.add(entry)
        onDispose { state.overlays.remove(entry) }
    }
    OnBack(onBack = onDismiss)
}

/** 뒤 화면을 막고 누르면 닫는 덮개 */
@Composable
private fun Scrim(onDismiss: () -> Unit, alpha: Float = 1f) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.scrim.copy(alpha = AppTheme.colors.scrim.alpha * alpha))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
    )
}

/** 아래에서 올라오는 시트(위 모서리 16dp, 끌기 손잡이). PC 넓은 창에서는 가운데 560dp */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Overlay(onDismiss, onKey = onKey) {
        val c = AppTheme.colors
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) { Scrim(onDismiss, alpha = 0.6f) }
            AnimatedVisibility(
                shown,
                Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
            ) {
                val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .shadow(16.dp, shape)
                        .clip(shape)
                        .background(c.card)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(30.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
                    }
                    content()
                }
            }
        }
    }
}

/** 가운데 모달 카드(07: 흰 카드, 모서리 15dp, 2dp 테두리). 뒤 화면은 흐리게 */
@Composable
fun Modal(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Overlay(onDismiss, blurBehind = true) {
        val c = AppTheme.colors
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(shown, enter = fadeIn()) { Scrim(onDismiss) }
            AnimatedVisibility(shown, enter = fadeIn() + scaleIn(initialScale = 0.94f)) {
                val shape = RoundedCornerShape(15.dp)
                Column(
                    Modifier
                        .padding(20.dp)
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, shape)
                        .clip(shape)
                        .background(c.card)
                        .border(2.dp, c.lineStrong, shape)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(c.subtle).padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f), color = c.textStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        PillButton("닫기", AppIcons.Close, onDismiss, danger = true)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
                    Column(Modifier.padding(18.dp), content = content)
                }
            }
        }
    }
}

/** [AppState.toast] 를 화면 위쪽에 2초 띄운다(10.jpg 자리 · 모서리 10dp · 가운데 글자). 바탕은 짙은 띠(다크는 밝은 띠) */
@Composable
internal fun ToastHost(state: AppState) {
    val t = state.toast ?: return
    LaunchedEffect(t) {
        delay(2000)
        state.clearToast(t)
    }
    val shown = remember(t) { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 84.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { -it / 2 }) {
            Text(
                t.text,
                Modifier.clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.toast).padding(horizontal = 18.dp, vertical = 11.dp),
                color = AppTheme.colors.onToast,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 시트 머리: 왼쪽 제목, 오른쪽 보조 글 */
@Composable
fun SheetTitle(title: String, trailing: String = "") {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f), color = c.textStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(trailing, color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
