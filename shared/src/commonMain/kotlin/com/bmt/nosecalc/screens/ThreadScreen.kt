package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoon
import com.bmt.nosecalc.ui.FeatureScaffold

/** G76 나사 */
@Composable
fun ThreadScreen() {
    FeatureScaffold(Feature.THREAD, ThreadHelp) {
        ComingSoon(Feature.THREAD)
    }
}

/** 헤더 도움말 버튼의 사용법(화면을 채우면 그에 맞춰 고친다) */
internal val ThreadHelp = listOf(
    "외경/내경과 나사 규격(종류 · 사이즈 · 산수)을 고르면 규격표 값이 채워집니다.",
    "Z 시작점 · 목표점과 회전수를 넣으면 G76 두 줄과 절입 회차별 분석이 나옵니다.",
)
