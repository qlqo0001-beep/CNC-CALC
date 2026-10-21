package com.bmt.nosecalc

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleFigure
import com.bmt.nosecalc.screens.triangle.TriangleState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 직각삼각형 화면을 라이트/다크로 → build/shots/triangle-* */
class TriangleShotsTest {
    private fun shot(name: String, heightDp: Int = 1480, editing: TriField? = null, frames: Int = 40, frameMs: Long = 100, state: () -> TriangleState) =
        Shots.both(name, heightDp, frames, frameMs = frameMs) { AppHost(it) { TriangleScreen(state(), editing) } }

    @Test
    fun shots() {
        // 처음 화면(모두 빈칸 · 파랑 안내)
        shot("triangle-empty", Shots.HEIGHT_DP) { TriangleState() }
        shot("triangle") { TriangleState(mapOf("a" to "10", "angA" to "25")) }
        // 높이 · 빗변을 넣고 높이를 Z 로 — 가파른 모양(67°)
        shot("triangle-swap") {
            TriangleState(mapOf("b" to "12", "c" to "13"), baseIsZ = false)
        }
        // 가파름(76°)은 58° 로 눌러 그린다
        shot("triangle-steep") { TriangleState(mapOf("a" to "3", "b" to "12")) }
        shot("triangle-error", Shots.HEIGHT_DP) { TriangleState(mapOf("angA" to "30", "angB" to "60")) }
        // 높이 b 를 키패드로 고치는 중: 작은 그림에 b 변 · 칸만 초점색. 칸을 끌어올리는 스크롤은 폰 프레임(16ms)으로 찍는다
        shot("triangle-keypad", Shots.HEIGHT_DP, TriField.B_SIDE, frames = 150, frameMs = 16) {
            TriangleState(mapOf("a" to "10", "angA" to "25"))
        }
    }

    /** 그림의 칸 다섯은 눌러 고를 수 있고(둘레 8dp 까지), 칸에서 먼 곳(왼쪽 위 구석)은 아무것도 고르지 않는다 */
    @Test
    fun chipsPick() {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        val s = TriangleState(mapOf("a" to "10", "angA" to "25"))
        val picked = mutableListOf<TriField>()
        val scene = ImageComposeScene(400, 290, Density(1f)) {
            AppHost(app) { TriangleFigure(s, RightTriangle.calc(s.input()), onPick = { picked += it }) }
        }
        try {
            var t = 0L
            fun frame() = scene.render(t).close().also { t += 50_000_000L }
            fun tap(x: Int, y: Int): TriField? {
                picked.clear()
                scene.sendPointerEvent(PointerEventType.Press, Offset(x.toFloat(), y.toFloat()))
                frame()
                scene.sendPointerEvent(PointerEventType.Release, Offset(x.toFloat(), y.toFloat()))
                frame()
                return picked.singleOrNull()
            }
            repeat(4) { frame() }
            // 칸은 둘레를 더하면 60px 넘게 크므로 20px 격자면 어느 칸이든 한 번은 맞는다
            val hit = (0 until 290 step 20).flatMap { y -> (0 until 400 step 20).mapNotNull { x -> tap(x, y) } }.toSet()
            assertEquals(TriField.entries.toSet(), hit)
            assertNull(tap(4, 4))
        } finally {
            scene.close()
        }
    }
}
