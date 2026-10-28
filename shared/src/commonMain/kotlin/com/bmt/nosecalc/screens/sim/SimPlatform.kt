package com.bmt.nosecalc.screens.sim

/** 지금 시각(1970 부터 ms) — 저장 시각 */
internal expect fun nowMillis(): Long

/** 저장 시각을 이 기기의 시간대로 '10-28 14:03' */
internal expect fun savedAtText(epochMs: Long): String
