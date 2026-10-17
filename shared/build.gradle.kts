import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

// 폰·PC 가 같이 쓰는 화면
kotlin {
    jvm {
        // JUnit4 실행기는 한글 경로(D:\제작플긴)에서 테스트 클래스를 못 찾는다 — JUnit Platform 은 된다
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                // 헤드리스 스크린샷(ScreenShots) 저장 곳
                val shots = rootDir.resolve("build/shots")
                systemProperty("shotsDir", shots.absolutePath)
                outputs.dir(shots)
            }
        }
    }

    compilerOptions {
        // KeyValueStore 를 expect/actual 클래스로 둔다
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "com.bmt.nosecalc.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.calc)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

compose.resources {
    packageOfResClass = "com.bmt.nosecalc.resources"
}
