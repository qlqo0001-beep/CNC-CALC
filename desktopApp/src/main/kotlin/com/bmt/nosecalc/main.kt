package com.bmt.nosecalc

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bmt.nosecalc.ui.BmtMark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.GraphicsEnvironment

fun main() {
    // 테마와 창 크기가 한 설정 파일을 같이 쓴다(따로 만들면 서로 덮어쓴다)
    val store = KeyValueStore()
    val state = AppState(store)
    // 지난번 창 크기 · 자리. 처음이면 480 × 960, 자리는 운영체제가 정한다
    val position = savedPosition(store)
    val size = DpSize(store.dp("win.w") ?: 480.dp, store.dp("win.h") ?: 960.dp)
    application {
        val win = rememberWindowState(position = position, size = size)
        // 옮기거나 크기를 바꾸고 0.5초 멈추면 저장. 최대화 · 최소화된 크기와 자리는 저장하지 않는다
        LaunchedEffect(win) {
            snapshotFlow { listOf(win.placement, win.isMinimized, win.size, win.position) }.collectLatest {
                if (win.placement != WindowPlacement.Floating || win.isMinimized) return@collectLatest
                delay(500)
                store.put("win.w", "${win.size.width.value}")
                store.put("win.h", "${win.size.height.value}")
                if (win.position.isSpecified) {
                    store.put("win.x", "${win.position.x.value}")
                    store.put("win.y", "${win.position.y.value}")
                }
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "BMT 노즈R 계산기",
            icon = rememberVectorPainter(BmtMark),
            state = win,
            // Esc = 뒤로, 키패드가 떠 있으면 숫자 키
            onPreviewKeyEvent = state::handleKey,
        ) {
            App(state)
        }
    }
}

private fun KeyValueStore.dp(key: String): Dp? = get(key)?.toFloatOrNull()?.dp

/** 저장된 창 자리. 모니터를 빼서 그 자리가 어느 화면에도 없으면 운영체제에 맡긴다(창이 화면 밖에 열리지 않게) */
private fun savedPosition(store: KeyValueStore): WindowPosition {
    val x = store.dp("win.x") ?: return WindowPosition.PlatformDefault
    val y = store.dp("win.y") ?: return WindowPosition.PlatformDefault
    // 제목 막대 안쪽 한 점이 보이는 화면에 있어야 끌어 옮길 수 있다
    val onScreen = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
        it.defaultConfiguration.bounds.contains(x.value.toInt() + 40, y.value.toInt() + 10)
    }
    return if (onScreen) WindowPosition(x, y) else WindowPosition.PlatformDefault
}
