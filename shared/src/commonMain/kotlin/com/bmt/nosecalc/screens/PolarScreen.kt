package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoon
import com.bmt.nosecalc.ui.FeatureScaffold

/** 턴밀 극좌표(G12.1) 다각가공 */
@Composable
fun PolarScreen() {
    FeatureScaffold(Feature.POLAR, PolarHelp) {
        ComingSoon(Feature.POLAR)
    }
}

/** 헤더 도움말 버튼의 사용법(화면을 채우면 그에 맞춰 고친다) */
internal val PolarHelp = listOf(
    "외측/내측과 도형을 고르고 치수를 넣습니다.",
    "G12.1 극좌표 보간 X-C 프로그램을 만들고 미리보기로 확인합니다.",
)
