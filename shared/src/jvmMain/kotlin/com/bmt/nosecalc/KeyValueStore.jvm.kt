package com.bmt.nosecalc

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** 기본 위치: 사용자 홈\.bmt-nosecalc\settings.properties */
actual class KeyValueStore(
    private val file: File = File(System.getProperty("user.home"), ".bmt-nosecalc/settings.properties"),
) {
    private val props = Properties().apply {
        if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) }
    }

    actual fun get(key: String): String? = props.getProperty(key)

    /** 임시 파일에 다 쓴 뒤 바꿔치기 — 쓰는 도중 꺼져도 예전 파일이 통째로 남는다(공구 칸은 한 글자마다 쓴다) */
    actual fun put(key: String, value: String) {
        props.setProperty(key, value)
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, null) }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // 윈도우에서 다른 프로그램(백신 등)이 잠깐 잡고 있으면 바꿔치기가 막힌다 — 예전처럼 그 자리에 쓴다
            file.writer(Charsets.UTF_8).use { props.store(it, null) }
            tmp.delete()
        }
    }
}
