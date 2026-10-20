package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoonHelp
import com.bmt.nosecalc.ui.ComingSoonPage
import com.bmt.nosecalc.ui.FeatureScaffold

/** 재료·절삭조건(준비 중) */
@Composable
fun CuttingScreen() {
    FeatureScaffold(Feature.CUTTING, ComingSoonHelp) {
        ComingSoonPage(Feature.CUTTING)
    }
}
