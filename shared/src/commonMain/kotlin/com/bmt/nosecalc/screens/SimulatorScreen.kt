package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoonHelp
import com.bmt.nosecalc.ui.ComingSoonPage
import com.bmt.nosecalc.ui.FeatureScaffold

/** FANUC 호환 시뮬레이터(준비 중) */
@Composable
fun SimulatorScreen() {
    FeatureScaffold(Feature.SIMULATOR, ComingSoonHelp) {
        ComingSoonPage(Feature.SIMULATOR)
    }
}
