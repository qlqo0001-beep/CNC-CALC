package com.bmt.nosecalc.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.screens.sim.SimulatorPage
import com.bmt.nosecalc.screens.sim.TrajUi
import com.bmt.nosecalc.screens.sim.TrajectoryWindow
import com.bmt.nosecalc.screens.sim.rememberSimState

/** FANUC 호환 G코드 시뮬레이터 — 화면 A 편집기 · 화면 B 궤적 창(`screens/sim/`) */
@Composable
fun SimulatorScreen() {
    val app = LocalAppState.current
    val sim = rememberSimState(app.store)
    val traj = remember { TrajUi() }
    SimulatorPage(sim, onTrajectory = {
        sim.ensureRun()
        sim.trajOpen = true
    })
    if (sim.trajOpen) {
        TrajectoryWindow(
            sim, traj,
            onClose = {
                sim.trajOpen = false
                traj.sheet = null
            },
            onEditor = { line ->
                sim.trajOpen = false
                traj.sheet = null
                sim.goToLine(line)
            },
        )
    }
}
