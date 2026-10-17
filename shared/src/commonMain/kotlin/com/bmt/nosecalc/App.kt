package com.bmt.nosecalc

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.CuttingScreen
import com.bmt.nosecalc.screens.PolarScreen
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.SimulatorScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.ThreadScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.ui.ToastHost
import com.bmt.nosecalc.ui.theme.AppTheme

@Composable
fun App(state: AppState) {
    AppHost(state) {
        // 화면을 떠났다 돌아와도 rememberSaveable 로 둔 입력값은 남는다
        val saved = rememberSaveableStateHolder()
        val f = state.current
        saved.SaveableStateProvider(f.name) {
            when (f) {
                Feature.TAPER -> TaperScreen()
                Feature.CUTTING -> CuttingScreen()
                Feature.ROUND -> RoundScreen()
                Feature.SIMULATOR -> SimulatorScreen()
                Feature.TRIANGLE -> TriangleScreen()
                Feature.POLAR -> PolarScreen()
                Feature.THREAD -> ThreadScreen()
            }
        }
    }
}

/** 테마 + 앱 상태 + 위에 뜨는 층(바텀시트·모달·토스트) + 뒤로 처리. 스크린샷 시험도 이걸로 그린다 */
@Composable
internal fun AppHost(state: AppState, content: @Composable () -> Unit) {
    AppTheme(state.isDark(isSystemInDarkTheme())) {
        CompositionLocalProvider(LocalAppState provides state) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.page)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                val blur = state.overlays.any { it.blurBehind }
                Box(Modifier.fillMaxSize().blur(if (blur) 6.dp else 0.dp)) { content() }
                state.overlays.forEach { key(it) { it.content() } }
                ToastHost(state)
            }
            SystemBack(state.canGoBack) { state.back() }
        }
    }
}

/** 안드로이드 뒤로 키. PC 는 창이 Esc 를 [AppState.handleKey] 로 넘긴다 */
@Composable
internal expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
