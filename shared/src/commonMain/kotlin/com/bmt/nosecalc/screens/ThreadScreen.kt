package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.ComingSoonHelp
import com.bmt.nosecalc.ui.ComingSoonPage
import com.bmt.nosecalc.ui.FeatureScaffold

/** G76 나사(준비 중) */
@Composable
fun ThreadScreen() {
    FeatureScaffold(Feature.THREAD, ComingSoonHelp) {
        ComingSoonPage(Feature.THREAD)
    }
}
