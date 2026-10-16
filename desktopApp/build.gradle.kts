plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

// PC 확인용 창. createDistributable 로 JRE 를 품은 exe 폴더를 만든다(설치 없이 더블클릭)
compose.desktop {
    application {
        mainClass = "com.bmt.nosecalc.MainKt"

        nativeDistributions {
            packageName = "BMT-NoseR"
            packageVersion = "0.1.0"
        }
    }
}
