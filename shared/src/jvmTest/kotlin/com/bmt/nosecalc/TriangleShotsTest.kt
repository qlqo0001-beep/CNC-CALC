package com.bmt.nosecalc

import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleState
import kotlin.test.Test

/** 직각삼각형 화면을 라이트/다크로 → build/shots/triangle-* */
class TriangleShotsTest {
    private fun shot(name: String, heightDp: Int = 1480, state: () -> TriangleState) =
        Shots.both(name, heightDp) { AppHost(it) { TriangleScreen(state()) } }

    @Test
    fun shots() {
        shot("triangle") { TriangleState() }
        // 높이 · 빗변을 넣고 높이를 Z 로 — 가파른 모양(67°)
        shot("triangle-swap") {
            TriangleState(mapOf("b" to "12", "c" to "13"), baseIsZ = false)
        }
        // 가파름(76°)은 58° 로 눌러 그린다
        shot("triangle-steep") { TriangleState(mapOf("a" to "3", "b" to "12")) }
        shot("triangle-error", Shots.HEIGHT_DP) {
            TriangleState().apply {
                clear()
                set(TriField.ANGLE_A, "30")
                set(TriField.ANGLE_B, "60")
            }
        }
    }
}
