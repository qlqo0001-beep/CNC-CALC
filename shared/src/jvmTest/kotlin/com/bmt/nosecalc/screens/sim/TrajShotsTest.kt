package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.remember
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.Shots
import kotlin.test.Test

/**
 * 궤적 창 스크린샷(설계 9.4) → build/shots/sim-2d*-light.png · -dark.png. 원본 docs/store/06 · 10 과 나란히 본다.
 * 재생은 멈춘 채 [TrajScenes] 의 시각에 고정해 찍는다. 편집기 화면 위에 실제와 같은 위층으로 그린다.
 */
class TrajShotsTest {
    private fun shot(
        s: TrajScene,
        name: String = s.name,
        widthDp: Int = 412,
        heightDp: Int = 915,
        fontScale: Float = 1f,
        sheet: TrajSheet? = null,
    ) = Shots.both(name, heightDp = heightDp, widthDp = widthDp, fontScale = fontScale, setup = { it.open(Feature.SIMULATOR) }) { state ->
        val sim = remember { TrajScenes.sim(state.store, s) }
        val ui = remember { TrajScenes.ui(sim, s).apply { this.sheet = sheet } }
        AppHost(state) {
            SimulatorPage(sim, onTools = {}) {}
            TrajectoryWindow(sim, ui, onClose = {}, onEditor = {})
        }
    }

    @Test
    fun original06() = shot(TrajScenes.inner06)

    @Test
    fun original10() = shot(TrajScenes.measure10)

    @Test
    fun midPlayback() = shot(TrajScenes.mid)

    @Test
    fun angle() = shot(TrajScenes.angle)

    @Test
    fun thread() = shot(TrajScenes.thread)

    @Test
    fun chuck() = shot(TrajScenes.chuck)

    @Test
    fun stopError() = shot(TrajScenes.stop)

    @Test
    fun settingsSheet() = shot(TrajScenes.mid, "sim-settings", sheet = TrajSheet.SETTINGS)

    @Test
    fun toolsSheet() = shot(TrajScenes.inner06, "sim-tools", sheet = TrajSheet.TOOLS)

    @Test
    fun legendSheet() = shot(TrajScenes.measure10, "sim-legend", sheet = TrajSheet.LEGEND)

    /** 큰 글꼴 · 좁은 폰 · PC 넓은 창 · 낮은 창 */
    @Test
    fun narrowAndLarge() {
        shot(TrajScenes.inner06, "sim-2d-w360", widthDp = 360)
        shot(TrajScenes.inner06, "sim-2d-fs130", fontScale = 1.3f)
        shot(TrajScenes.mid, "sim-2d-fs130-w360", widthDp = 360, fontScale = 1.3f)
        shot(TrajScenes.measure10, "pc-wide-traj", widthDp = 1280, heightDp = 720)
        shot(TrajScenes.inner06, "pc-short-traj", heightDp = 640)
    }
}
