package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoon
import com.bmt.nosecalc.ui.FeatureScaffold

/** FANUC 호환 시뮬레이터 */
@Composable
fun SimulatorScreen() {
    FeatureScaffold(Feature.SIMULATOR, SimulatorHelp) {
        ComingSoon(Feature.SIMULATOR)
    }
}

/** 헤더 도움말 버튼의 사용법(화면을 채우면 그에 맞춰 고친다) */
internal val SimulatorHelp = listOf(
    "G코드를 전용 키패드로 쓰거나 예제를 불러옵니다.",
    "재생하면 공구 궤적을 2D · 3D 로 보여 주고 사이클 타임을 계산합니다.",
    "블록마다 노즈R 보정 좌표를 확인할 수 있습니다.",
)
