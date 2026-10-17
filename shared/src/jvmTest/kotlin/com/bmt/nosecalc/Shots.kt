package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.math.roundToInt

/**
 * 헤드리스 스크린샷: 컴포저블을 폰 크기(412×915dp, 밀도 2.625 = 1082×2402px)로 PNG 를 찍는다.
 * 좁은 폰(widthDp = 360)·큰 글꼴(fontScale = 1.3f)도 찍는다.
 * 저장: build/shots/<이름>-light.png · -dark.png (`gw.bat :shared:jvmTest`). 찍은 것은 docs/store 원본과 나란히 본다.
 */
object Shots {
    const val WIDTH_DP = 412
    const val HEIGHT_DP = 915
    const val DENSITY = 2.625f
    val dir = File(System.getProperty("shotsDir") ?: "build/shots")

    /**
     * 라이트·다크 두 장. setup 은 그리기 전에 앱 상태를 맞춘다(메뉴 열기·화면 이동 등).
     * content 는 App(state) 이거나, 부품만 볼 때는 AppHost(state) { … } 로 감싼다.
     */
    fun both(
        name: String,
        heightDp: Int = HEIGHT_DP,
        frames: Int = 40,
        setup: (AppState) -> Unit = {},
        widthDp: Int = WIDTH_DP,
        fontScale: Float = 1f,
        content: @Composable (AppState) -> Unit,
    ) {
        take(name, dark = false, heightDp, frames, setup, widthDp, fontScale, content)
        take(name, dark = true, heightDp, frames, setup, widthDp, fontScale, content)
    }

    fun take(
        name: String,
        dark: Boolean,
        heightDp: Int = HEIGHT_DP,
        frames: Int = 40,
        setup: (AppState) -> Unit = {},
        widthDp: Int = WIDTH_DP,
        fontScale: Float = 1f,
        content: @Composable (AppState) -> Unit,
    ): File {
        val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        state.setDark(dark)
        setup(state)
        val scene = ImageComposeScene(
            width = (widthDp * DENSITY).roundToInt(),
            height = (heightDp * DENSITY).roundToInt(),
            density = Density(DENSITY, fontScale),
        ) { content(state) }
        try {
            // 글꼴 리소스는 비동기로 읽혀 몇 프레임 뒤에 바뀐다. 프레임마다 0.1초씩 흘려 애니메이션도 끝낸다
            var t = 0L
            var img = scene.render(t)
            repeat(frames) {
                Thread.sleep(15)
                t += 100_000_000L
                // 프레임마다 화면 크기 그림(네이티브 메모리)이 생긴다. 바로 닫아야 긴 스크린샷에서 모자라지 않는다
                img.close()
                img = scene.render(t)
            }
            val out = File(dir, "$name-${if (dark) "dark" else "light"}.png")
            out.parentFile.mkdirs()
            out.writeBytes(requireNotNull(img.encodeToData()).bytes)
            img.close()
            return out
        } finally {
            scene.close()
        }
    }
}
