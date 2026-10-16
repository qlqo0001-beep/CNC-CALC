import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
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
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
