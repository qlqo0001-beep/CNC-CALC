package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.Shots
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.StockProfile
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.ui.ViewerState
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 큰 프로그램 재생 값(설계 5.4 · 15.2-8 · 15.7): M98 고리로 이동 10만 개(실행 한도 [GSim.EXEC_LIMIT] 의 절반 — 0.0008 mm 씩 깎는 층 2만 5천 개와
 * 되짚는 급송) — 실행 · 경로 만들기 · 소재 끝까지 깎기 · ×200 · 60 fps 재생 프레임(소재 이어 깎기 + 단면 + 2D 뷰 그리기).
 * PC 의 CPU 래스터(Skia — 실제 창은 GPU)로 잰 값이라 실제보다 느린 쪽이다. 잰 값은 build/perf/traj-big.txt 와 설계 문서 15.7 에 적는다.
 */
class TrajPerfTest {
    private val big = listOf(
        "O1000 (큰 프로그램 - 층 2만 5천)", "T0101", "G97 S1000 M03", "G99", "G00 X102. Z2.", "M98 P1001 L5", "G00 X200. Z100.", "M30",
        "O1001", "M98 P1002 L5000", "M99",
        "O1002", "G01 Z-60. F0.3", "G00 U1.", "Z2.", "U-1.0016", "M99",
    ).joinToString("\n")

    private inline fun ms(block: () -> Unit): Double {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1e6
    }

    @Test
    fun bigProgramPlaysSmoothly() {
        val tools = mapOf(1 to ToolOffset(0.4, 3))
        lateinit var run: SimRun
        val tRun = ms { run = GSim.run(SimInput(big, MachineSetup(), tools)) }
        assertNull(run.stop, "멈춤: ${run.stop}")
        val moves = run.steps.size
        assertTrue(moves > 90_000, "이동 $moves")
        lateinit var paths: SimPaths
        val tPaths = ms { paths = SimPaths(run) }
        val spec = requireNotNull(StockAuto.of(run))
        lateinit var stock: StockSim
        val tCut = ms { stock = StockSim(spec, run, tools).also { it.cutTo(run.totalSec) } }
        val tol = max(0.002, spec.length / 15_000)

        val play = PlayState(run)
        val profile = mutableStateOf<StockProfile?>(null)
        val view = ViewerState()
        // 견줌: 같은 소재 · 보기에 이동 하나짜리 경로(긋는 양만 다름)
        val shown = mutableStateOf(run to paths)
        val app = AppState(KeyValueStore(File.createTempFile("bmt-perf", ".properties").apply { delete() }))
        val d = Shots.DENSITY
        val scene = ImageComposeScene((412 * d).roundToInt(), (600 * d).roundToInt(), Density(d)) {
            AppHost(app) {
                Sim2DView(
                    shown.value.first, shown.value.second, spec, profile, { _, _ -> null }, play, null, measure = false, angle = false, flags = ViewFlags(),
                    tools = tools, contacts = emptyList(), view = view, insets = listOf(16.dp, 108.dp, 64.dp, 150.dp), onPick = {},
                )
            }
        }
        // 큰 프로그램 가운데쯤부터(저장점에서 되감기) ×200 · 60 fps 로 60 프레임: 한 프레임 = 프로그램 200/60 초
        val start = run.totalSec * 0.5
        play.seek(start)
        val tRewind = ms {
            stock.cutTo(start)
            profile.value = stock.profile(tol)
        }
        try {
            var clock = 0L
            repeat(5) { // 글꼴 · 자리 잡기
                scene.render(clock).close()
                clock += 16_000_000
            }
            val cut = ArrayList<Double>()
            val draw = ArrayList<Double>()
            var t = start
            repeat(60) {
                t += 200.0 / 60
                play.seek(t)
                cut += ms {
                    stock.cutTo(t)
                    profile.value = stock.profile(tol)
                }
                clock += 16_000_000
                draw += ms { scene.render(clock).close() }
            }
            val one = GSim.run(SimInput(listOf("T0101", "G97 S1000 M03", "G99", "G00 X102. Z2.", "G01 Z-60. F0.3", "M30").joinToString("\n"), MachineSetup(), tools))
            shown.value = one to SimPaths(one)
            val base = ArrayList<Double>()
            repeat(20) {
                clock += 16_000_000
                play.seek(t + it)
                base += ms { scene.render(clock).close() }
            }
            val baseAvg = base.drop(5).average()
            val line = "큰 프로그램: 이동 $moves · 실행 ${tRun.roundToInt()} ms · 경로 ${tPaths.roundToInt()} ms · 끝까지 깎기 ${tCut.roundToInt()} ms · " +
                "되감기 ${tRewind.roundToInt()} ms · 프레임 깎기+단면 평균 ${"%.2f".format(cut.average())} / 가장 긴 ${"%.2f".format(cut.max())} ms · " +
                "그리기(CPU 래스터) 평균 ${"%.1f".format(draw.average())} / 가장 긴 ${"%.1f".format(draw.max())} ms · 이동 하나 경로 그리기 평균 ${"%.1f".format(baseAvg)} ms"
            println(line)
            File("build/perf").apply { mkdirs() }.resolve("traj-big.txt").writeText(line + "\n")
            // 재생 프레임의 소재 일이 16 ms 안, 그리기가 경로 수에 거의 매이지 않음(급송 점선을 통째로 만들 때는 한 장 2.5 초였다)
            assertTrue(cut.average() < 16, line)
            assertTrue(draw.average() < 60 && draw.average() < baseAvg * 3 + 10, line)
        } finally {
            scene.close()
        }
    }
}
