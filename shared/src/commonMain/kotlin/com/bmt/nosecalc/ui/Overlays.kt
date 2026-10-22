package com.bmt.nosecalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Radius
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    OnBack(layer = true, onBack = onDismiss)
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

/**
 * 아래에서 올라오는 시트(위 모서리 20dp, 220ms). PC 넓은 창에서는 가운데 560dp. 다크는 그림자 대신 테두리로 경계를 보인다.
 * 손잡이 띠(24dp)를 끌면 시트가 따라 내려오고, 80dp 넘게 내려 놓으면 onDismiss, 아니면 제자리로 돌아간다.
 * scrimAlpha = 뒤 덮개 짙기(키패드는 옅게 — 뒤에서 바뀌는 값이 보이게), maxHeightFraction = 창 높이에 대한 최대 높이 비율.
 * onHeight = 시트 높이(px, 아래 시스템 막대 빼고)가 바뀔 때 — 키패드 묶음이 고치는 칸을 시트 위로 올린다.
 */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    onKey: ((KeyEvent) -> Boolean)? = null,
    scrimAlpha: Float = 0.6f,
    maxHeightFraction: Float? = null,
    onHeight: ((Int) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Overlay(onDismiss, onKey = onKey) {
        val c = AppTheme.colors
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        var drag by remember { mutableFloatStateOf(0f) }
        val dismissAt = with(LocalDensity.current) { 80.dp.toPx() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(tween(150)), exit = fadeOut()) { Scrim(onDismiss, alpha = scrimAlpha) }
            AnimatedVisibility(
                shown,
                Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(220)) { it },
            ) {
                val shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
                Column(
                    Modifier
                        .offset { IntOffset(0, drag.roundToInt()) }
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .then(if (maxHeightFraction != null) Modifier.heightIn(max = maxHeight * maxHeightFraction) else Modifier)
                        .shadow(if (c.dark) 0.dp else 16.dp, shape)
                        .clip(shape)
                        .background(c.card)
                        // 다크에선 그림자 대신 테두리(모달 테두리와 같은 색) — 어두운 뒤 화면에서 시트 경계가 보이게
                        .then(if (c.dark) Modifier.border(1.dp, c.lineStrong, shape) else Modifier)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .then(if (onHeight != null) Modifier.onSizeChanged { onHeight(it.height) } else Modifier)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .draggable(
                                rememberDraggableState { drag = (drag + it).coerceAtLeast(0f) },
                                Orientation.Vertical,
                                onDragStopped = {
                                    if (drag > dismissAt) onDismiss() else animate(drag, 0f, animationSpec = tween(150)) { v, _ -> drag = v }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(30.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
                    }
                    content()
                }
            }
        }
    }
}

/** 가운데 모달 카드(흰 카드, 모서리 20dp, 1dp 연한 테두리, 닫기는 보통 알약). 뒤 화면은 흐리게 */
@Composable
fun Modal(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Overlay(onDismiss, blurBehind = true) {
        val c = AppTheme.colors
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(shown, enter = fadeIn()) { Scrim(onDismiss) }
            AnimatedVisibility(shown, enter = fadeIn() + scaleIn(initialScale = 0.94f)) {
                val shape = RoundedCornerShape(Radius.sheet)
                Column(
                    Modifier
                        .padding(20.dp)
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, shape)
                        .clip(shape)
                        .background(c.card)
                        .border(1.dp, c.lineCard, shape)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(c.subtle).padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f), color = c.textStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        PillButton("닫기", AppIcons.Close, onDismiss)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
                    Column(Modifier.padding(18.dp), content = content)
                }
            }
        }
    }
}

/**
 * [AppState.toast] 를 화면 아래(바닥에서 96dp — 결과 띠 위)에 2.5초 띄운다. 헤더·탭을 가리지 않게. 모서리 12dp · 가운데 글자 · 최대 480dp.
 * 바탕은 짙은 띠(다크는 밝은 띠)
 */
@Composable
internal fun ToastHost(state: AppState) {
    val t = state.toast ?: return
    LaunchedEffect(t) {
        delay(2500)
        state.clearToast(t)
    }
    val shown = remember(t) { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(shown, enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }) {
            Text(
                t.text,
                Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.toast).padding(horizontal = 18.dp, vertical = 12.dp),
                color = AppTheme.colors.onToast,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 시트 머리: 왼쪽 제목, 오른쪽 보조 글(trailing)과 단추(action — 키패드의 "취소") */
@Composable
fun SheetTitle(title: String, trailing: String = "", action: (@Composable () -> Unit)? = null) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f), style = AppType.sheetTitle, color = c.textStrong)
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(trailing, color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        action?.invoke()
    }
}
