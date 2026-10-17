package com.bmt.nosecalc

import java.io.File
import java.util.Properties

/** 기본 위치: 사용자 홈\.bmt-nosecalc\settings.properties */
actual class KeyValueStore(
    private val file: File = File(System.getProperty("user.home"), ".bmt-nosecalc/settings.properties"),
) {
    private val props = Properties().apply {
        if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) }
    }

    actual fun get(key: String): String? = props.getProperty(key)

    actual fun put(key: String, value: String) {
        props.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.writer(Charsets.UTF_8).use { props.store(it, null) }
    }
}
