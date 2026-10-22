package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoonHelp
import com.bmt.nosecalc.ui.ComingSoonPage
import com.bmt.nosecalc.ui.FeatureScaffold

/** 턴밀 극좌표(G12.1) 다각가공(준비 중) */
@Composable
fun PolarScreen() {
    FeatureScaffold(Feature.POLAR, ComingSoonHelp) {
        ComingSoonPage(Feature.POLAR)
    }
}
