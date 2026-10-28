package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.remember
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.Shots
import kotlin.test.Test

/**
 * 궤적 창 3D 스크린샷(설계 9.4 · 16절) → build/shots/sim-3d*-light.png · -dark.png. 원본 docs/store/05 와 나란히 본다.
 * 재생은 멈춘 채 [TrajScenes] 의 시각에 고정해 찍는다(재생 중간 고정). 3D 그림은 백그라운드에서 그려지므로 프레임을 넉넉히 흘린다.
 */
class Traj3DShotsTest {
    private fun shot(s: TrajScene, name: String = s.name, widthDp: Int = 412, heightDp: Int = 915, fontScale: Float = 1f) =
        Shots.both(name, heightDp = heightDp, widthDp = widthDp, fontScale = fontScale, frames = 80, setup = { it.open(Feature.SIMULATOR) }) { state ->
            val sim = remember { TrajScenes.sim(state.store, s) }
            val ui = remember { TrajScenes.ui(sim, s) }
            AppHost(state) {
                SimulatorPage(sim, onTools = {}) {}
                TrajectoryWindow(sim, ui, onClose = {}, onEditor = {})
            }
        }

    /** 05 꼴 부품 재생 중간(바깥 나사를 내는 중) — 1/4 절개 */
    @Test
    fun midPlayback() = shot(TrajScenes.part3d)

    @Test
    fun end() = shot(TrajScenes.part3dEnd)

    @Test
    fun fullFree() = shot(TrajScenes.part3dFull)

    @Test
    fun g71Layers() = shot(TrajScenes.mid3d)

    @Test
    fun thread() = shot(TrajScenes.thread3d)

    /** 좁은 폰 · 큰 글꼴 · PC 넓은 창 · 낮은 창 */
    @Test
    fun narrowAndLarge() {
        shot(TrajScenes.part3d, "sim-3d-w360", widthDp = 360)
        shot(TrajScenes.part3d, "sim-3d-fs130", fontScale = 1.3f)
        shot(TrajScenes.part3dEnd, "pc-wide-3d", widthDp = 1280, heightDp = 720)
        shot(TrajScenes.part3dEnd, "pc-short-3d", heightDp = 640)
    }
}
