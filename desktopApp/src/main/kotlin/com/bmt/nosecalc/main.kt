package com.bmt.nosecalc

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bmt.nosecalc.ui.BmtMark

fun main() {
    val state = AppState(KeyValueStore())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "BMT 노즈R 계산기",
            icon = rememberVectorPainter(BmtMark),
            // 폰 크기 창
            state = rememberWindowState(size = DpSize(412.dp, 915.dp)),
            // Esc = 뒤로, 키패드가 떠 있으면 숫자 키
            onPreviewKeyEvent = state::handleKey,
        ) {
            App(state)
        }
    }
}
