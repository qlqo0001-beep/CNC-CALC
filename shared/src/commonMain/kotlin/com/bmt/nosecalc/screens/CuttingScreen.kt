package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoon
import com.bmt.nosecalc.ui.FeatureScaffold

/** 재료·절삭조건 */
@Composable
fun CuttingScreen() {
    FeatureScaffold(Feature.CUTTING, CuttingHelp) {
        ComingSoon(Feature.CUTTING)
    }
}

/** 헤더 도움말 버튼의 사용법(화면을 채우면 그에 맞춰 고친다) */
internal val CuttingHelp = listOf(
    "탭에서 기본계산기 · 재료분석 · 드릴링을 고릅니다.",
    "절삭속도(Vc)와 회전수(RPM)는 서로 바꿔 계산합니다. 칸을 누르면 숫자 키패드가 뜹니다.",
    "카탈로그 값은 참고값입니다. 실제 조건은 공구 상태와 기계에 맞춰 조정하세요.",
)
