package com.bmt.nosecalc.screens.sim

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun nowMillis(): Long = System.currentTimeMillis()

private val savedAtFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")

internal actual fun savedAtText(epochMs: Long): String =
    savedAtFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
