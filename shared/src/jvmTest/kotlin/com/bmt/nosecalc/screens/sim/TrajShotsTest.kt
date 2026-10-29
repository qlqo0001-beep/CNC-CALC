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

    /**
     * 05 꼴 부품의 공구 다섯에 회사 홀더를 고른 공구표(외경 PCLNL — κr 을 93 으로 고쳐 '수정됨' · 내경 S16R-STFCR11 대구텍(제조사로 나뉜 줄) ·
     * 외경 나사 THCER · 내경 나사 SNR — 홈은 고르지 않음)
     */
    private val holders = TrajScene("sim-tools-holder", {
        editor.replaceAll(TrajScenes.PART05)
        tools = TrajScenes.PART05_TOOLS
        for ((n, code, maker) in listOf(Triple(1, "PCLNL2525M12", null), Triple(2, "S16R-STFCR11", "대구텍"), Triple(4, "THCER2525M16", null), Triple(5, "SNR0020L09A", null))) {
            pickHolder(n, COMPANY_HOLDERS.single { it.code == code && (maker == null || maker in it.makers) })
        }
        tools = tools + (1 to tools.getValue(1).copy(kappaDeg = 93.0))
        stockText = mapOf(StockField.DIA to "60", StockField.LENGTH to "70", StockField.FACE to "1", StockField.BORE to "18")
    }) { run -> lastT = run.totalSec }

    /** 공구 설정 시트 위에 연 '홀더 고르기' 시트(회사 사용 — 묶음 넷, 제조사로 나뉜 줄은 품번 옆 제조사) */
    private fun holderSheet(name: String, widthDp: Int = 412, fontScale: Float = 1f, heightDp: Int = 915) =
        Shots.both(name, heightDp = heightDp, widthDp = widthDp, fontScale = fontScale, setup = { it.open(Feature.SIMULATOR) }) { state ->
            val sim = remember { TrajScenes.sim(state.store, holders) }
            val ui = remember { TrajScenes.ui(sim, holders).apply { sheet = TrajSheet.TOOLS } }
            AppHost(state) {
                SimulatorPage(sim, onTools = {}) {}
                TrajectoryWindow(sim, ui, onClose = {}, onEditor = {})
                HolderSheet(sim.holderOf(2), onPick = {}) {}
            }
        }

    /** 회사 홀더(10-29): 고른 공구 카드(품번 · 묶음 · 까닭 줄)와 홀더 고르기 시트 — 좁은 폰 · 큰 글꼴도 */
    @Test
    fun companyHolders() {
        shot(holders, "sim-tools-holder", heightDp = 2400, sheet = TrajSheet.TOOLS)
        shot(holders, "sim-tools-holder-fs130-w360", widthDp = 360, heightDp = 2800, fontScale = 1.3f, sheet = TrajSheet.TOOLS)
        holderSheet("sim-holders")
        holderSheet("sim-holders-w360", 360)
        holderSheet("sim-holders-fs130-w360", 360, 1.3f)
        // 20 줄이 다 보이게(시트 높이는 화면의 85 %)
        holderSheet("sim-holders-all", heightDp = 2000)
    }

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
