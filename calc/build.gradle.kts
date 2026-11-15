import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

// 카탈로그 자료 catalog/*.json(docs/research 의 검증 끝난 조사 결과를 바이트 그대로 복사한 것)을 Kotlin 문자열로 만든다 —
// 리소스 파일을 플랫폼마다 따로 읽지 않고 JVM · 안드로이드가 같은 코드로 읽게. 클래스 파일의 문자열 상수 하나는 64KB 까지라 나눠 붙인다.
// (JSON 파일, 만들 object 이름): 드릴 카탈로그 → DrillCatalogJson, 다이스 헤드 → DieHeadCatalogJson, 전조 머리 → RollHeadCatalogJson
val generateCatalogs = tasks.register("generateCatalogs") {
    val catalogs = listOf("drill-catalog.json" to "DrillCatalogJson", "die-heads.json" to "DieHeadCatalogJson", "roll-heads.json" to "RollHeadCatalogJson")
        .map { (json, name) -> Triple(file("catalog/$json"), json, name) }
    val outDir = layout.buildDirectory.dir("generated/catalogs/kotlin")
    inputs.files(catalogs.map { it.first })
    inputs.property("objects", catalogs.joinToString { "${it.second}=${it.third}" })
    outputs.dir(outDir)
    doLast {
        for ((src, json, name) in catalogs) {
            val text = src.readText(Charsets.UTF_8)
            val chunks = mutableListOf<String>()
            val cur = StringBuilder()
            var bytes = 0
            var i = 0
            while (i < text.length) {
                // 서로게이트 쌍은 나누지 않는다. 크기는 클래스 파일의 modified UTF-8 로 센다
                val n = if (text[i].isHighSurrogate() && i + 1 < text.length) 2 else 1
                val piece = text.substring(i, i + n)
                val size = piece.sumOf { c -> if (c.code == 0) 2 else if (c.code < 0x80) 1 else if (c.code < 0x800) 2 else 3 }
                if (bytes + size > 60_000) {
                    chunks += cur.toString()
                    cur.setLength(0)
                    bytes = 0
                }
                cur.append(piece)
                bytes += size
                i += n
            }
            if (cur.isNotEmpty()) chunks += cur.toString()
            fun lit(s: String) = buildString {
                append('"')
                for (c in s) when {
                    c == '\\' -> append("\\\\")
                    c == '"' -> append("\\\"")
                    c == '$' -> append("\\$")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c.code < 0x20 -> append("\\u" + c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
                append('"')
            }
            val out = outDir.get().asFile.resolve("com/bmt/nosecalc/calc/$name.kt")
            out.parentFile.mkdirs()
            out.writeText(
                buildString {
                    appendLine("// 만든 파일 — calc/build.gradle.kts 의 generateCatalogs 가 catalog/$json 에서. 손대지 말 것")
                    appendLine("package com.bmt.nosecalc.calc")
                    appendLine()
                    appendLine("internal object $name {")
                    appendLine("    fun text(): String = StringBuilder(${text.length})")
                    for (c in chunks) appendLine("        .append(${lit(c)})")
                    appendLine("        .toString()")
                    appendLine("}")
                },
                Charsets.UTF_8,
            )
        }
    }
}

// 계산·G코드 해석·카탈로그 자료. 화면에 기대지 않는다 — 테스트는 :calc:jvmTest
kotlin {
    jvm {
        // JUnit4 실행기는 한글 경로(D:\제작플긴)에서 테스트 클래스를 못 찾는다 — JUnit Platform 은 된다
        testRuns.named("test") { executionTask.configure { useJUnitPlatform() } }
    }

    androidLibrary {
        namespace = "com.bmt.nosecalc.calc"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateCatalogs)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
