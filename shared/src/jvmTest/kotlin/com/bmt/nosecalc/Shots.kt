package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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

    /** 누르기 전에 그리는 프레임 수(글꼴을 읽어 자리가 잡힌 뒤) */
    private const val TAP_FRAME = 10

    /**
     * 라이트·다크 두 장. setup 은 그리기 전에 앱 상태를 맞춘다(메뉴 열기·화면 이동 등).
     * content 는 App(state) 이거나, 부품만 볼 때는 AppHost(state) { … } 로 감싼다.
     * frameMs = 한 프레임에 흘리는 시간. 키패드가 고치는 칸을 시트 위로 끌어올리는 스크롤처럼 프레임마다 다시 겨누는 애니메이션은
     * 0.1초 걸음이면 출렁여 자리가 매번 달라진다 — 그런 장면은 16(폰 프레임)으로 frames 를 늘려 찍는다
     * tapDp = 몇 프레임 그린 뒤 한 번 누르고 뗄 자리(dp) — 칸을 눌러 연 키패드처럼 상태로 열 수 없는 것을 찍는다.
     * scrollToEnd = 몇 프레임 그린 뒤 휠을 크게 굴려 페이지를 끝까지 내린다(아래 결과 띠처럼 내려야 보이는 것).
     */
    fun both(
        name: String,
        heightDp: Int = HEIGHT_DP,
        frames: Int = 40,
        setup: (AppState) -> Unit = {},
        widthDp: Int = WIDTH_DP,
        fontScale: Float = 1f,
        frameMs: Long = 100,
        tapDp: Offset? = null,
        scrollToEnd: Boolean = false,
        content: @Composable (AppState) -> Unit,
    ) {
        take(name, dark = false, heightDp, frames, setup, widthDp, fontScale, frameMs, tapDp, scrollToEnd, content)
        take(name, dark = true, heightDp, frames, setup, widthDp, fontScale, frameMs, tapDp, scrollToEnd, content)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun take(
        name: String,
        dark: Boolean,
        heightDp: Int = HEIGHT_DP,
        frames: Int = 40,
        setup: (AppState) -> Unit = {},
        widthDp: Int = WIDTH_DP,
        fontScale: Float = 1f,
        frameMs: Long = 100,
        tapDp: Offset? = null,
        scrollToEnd: Boolean = false,
        content: @Composable (AppState) -> Unit,
    ): File {
        val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        state.setTheme(dark)
        setup(state)
        val scene = ImageComposeScene(
            width = (widthDp * DENSITY).roundToInt(),
            height = (heightDp * DENSITY).roundToInt(),
            density = Density(DENSITY, fontScale),
        ) { content(state) }
        try {
            // 글꼴 리소스는 비동기로 읽혀 몇 프레임 뒤에 바뀐다. 프레임마다 frameMs(보통 0.1초)씩 흘려 애니메이션도 끝낸다
            var t = 0L
            var img = scene.render(t)
            repeat(frames) { i ->
                // 손가락(터치)으로 — 마우스면 뗀 뒤에도 포인터가 남아 그 자리 단추가 올린 색으로 찍힌다
                if (tapDp != null && i == TAP_FRAME) {
                    scene.sendPointerEvent(PointerEventType.Press, tapDp * DENSITY, type = PointerType.Touch)
                    scene.sendPointerEvent(PointerEventType.Release, tapDp * DENSITY, type = PointerType.Touch)
                }
                // 화면 왼쪽 끝(단추 밖)에서 굴린다 — 마우스가 남아도 올린 색이 찍히지 않게
                if (scrollToEnd && i == TAP_FRAME) {
                    scene.sendPointerEvent(PointerEventType.Scroll, Offset(2f, heightDp / 2f) * DENSITY, scrollDelta = Offset(0f, 1000f))
                }
                Thread.sleep(15)
                t += frameMs * 1_000_000L
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
