package com.bmt.nosecalc

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** 갈 곳이 없으면(첫 화면, 뜬 것 없음) 꺼져서 시스템이 앱을 닫는다 */
@Composable
internal actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
