package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.Shots
import com.bmt.nosecalc.calc.sim.GText
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 긴 프로그램(글 5천 줄 — CAM 이 낸 점 줄 같은 글) 값(4단계 최종 점검): 글 읽기 · 실행 · 경로 · 소재,
 * 편집기(첫 그림 · 끝으로 가기 · 끝에서 · 가운데에서 키 하나마다 한 장), 궤적 창(열기 · ×200 재생 프레임 · 3D).
 * 잰 값은 build/perf/sim-long.txt 와 docs/stage4-report.md. 줄 번호 4자리 편집기 스크린샷 sim-editor-long.
 */
class SimLongProgramTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-long", ".properties").apply { delete() })

    private inline fun ms(block: () -> Unit): Double {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1e6
    }

    private fun f3(v: Double) = "%.3f".format(v)

    /** 외경 Ø20 → Ø50 의 매끈한 곡선(Z0 → Z−60, 점 n 개 — X 는 줄지 않음) */
    private fun curve(n: Int): List<String> = (1..n).map { k ->
        val s = k.toDouble() / n
        "X${f3(20 + 30 * (1 - cos(PI * s / 2)))} Z${f3(-60 * s)}"
    }

    /**
     * ① 손으로 쓴 황삭 500 번(4줄씩 — G00 X · G01 Z−60. · G00 U1. · Z2.) ② G71/G70 의 P~Q 가 곡선 점 1 500 줄
     * ③ 다른 공구로 G42 다듬질 — 같은 곡선 점 1 500 줄 · Z−62 · 어깨 · G40 후퇴. 모두 5 천 줄쯤.
     * (곡선 끝 0.04 mm 토막에서 바로 어깨로 꺾으면 안쪽 모서리 간섭 — FANUC 도 알람이라 시뮬레이터가 COMP_INTERFERENCE 로 멈춘다. 그래서 Z−62 를 둔다)
     */
    private val program: String = buildList {
        addAll(listOf("O4000 (긴 프로그램 - 5천 줄)", "T0101", "G50 S2500", "G96 S200 M03", "G99"))
        for (k in 0 until 500) {
            addAll(listOf("G00 X${f3(63.9 - k * 0.02)} Z2.", "G01 Z-60. F0.25", "G00 U1.", "Z2."))
        }
        addAll(listOf("G00 X64. Z2.", "G71 U1.5 R0.5", "G71 P100 Q200 U0.4 W0.1 F0.25", "N100 G00 X20.", "G01 Z0. F0.12"))
        addAll(curve(1500))
        addAll(listOf("N200 X62.", "G70 P100 Q200", "G00 X100. Z50."))
        addAll(listOf("T0202", "G96 S220 M03", "G00 X16. Z3.", "G42 G01 X20. Z0. F0.1"))
        addAll(curve(1500))
        addAll(listOf("Z-62.", "X62.", "G40 G00 X70. Z5.", "G00 X100. Z50.", "M30"))
    }.joinToString("\n")

    private val tools = mapOf(1 to ToolOffset(0.8, 3), 2 to ToolOffset(0.4, 3))

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.tapText(label: String): Boolean {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        val node = semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.firstOrNull { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null &&
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label }
        } ?: return false
        node.config[SemanticsActions.OnClick].action?.invoke()
        return true
    }

    private fun stats(v: List<Double>) = "평균 ${"%.1f".format(v.average())} / 가장 긴 ${"%.1f".format(v.max())} ms"

    @Test
    fun longProgram() {
        val lines = program.count { it == '\n' } + 1
        assertTrue(lines > 4900, "줄 $lines")
        val sim = SimState(store(), tools).apply { editor.replaceAll(program) }
        // 글 읽기 · 색 낱말(편집기는 보이는 줄만 칠한다 — 전체는 위쪽 값)
        GText.parse(program)
        val tParse = ms { GText.parse(program) }
        val tTokens = ms { program.split('\n').forEach { GText.tokens(it) } }
        // 실행(원점 자동이면 두 번 돈다 — 화면과 같은 runOf), 데운 뒤 세 번 평균
        sim.run = sim.runOf(sim.editor.text, sim.tools)
        val tRun = (0 until 3).map { ms { sim.run = sim.runOf(sim.editor.text, sim.tools) } }.average()
        val run = sim.run
        assertNull(run.stop, "멈춤: ${run.stop}")
        val tPaths = ms { SimPaths(run) }
        val spec = requireNotNull(stockOf(sim.stockText, run).first)
        lateinit var stock: StockSim
        val tCut = ms { stock = StockSim(spec, run, sim.tools).also { it.cutTo(run.totalSec) } }
        val tol = max(0.002, spec.length / 15_000)
        val tProfile = ms { stock.profile(tol) }

        // 편집기: 첫 그림 → 끝으로 → 끝에서 키 20 번 → 가운데(2 500줄)에서 키 20 번
        val app = AppState(store())
        val traj = mutableStateOf<TrajUi?>(null)
        val d = Shots.DENSITY
        val ui = UiQueue()
        val scene = ImageComposeScene((412 * d).roundToInt(), (915 * d).roundToInt(), Density(d), coroutineContext = ui) {
            AppHost(app) {
                SimulatorPage(sim) {}
                traj.value?.let { TrajectoryWindow(sim, it, onClose = {}, onEditor = {}) }
            }
        }
        var clock = 0L
        fun frame(): Double = ms {
            clock += 16_000_000L
            ui.drain()
            scene.render(clock).close()
        }
        val digit = CncRows.flatten().first { it.main == "5" }
        val back = CncRows.flatten().first { it.main == "⌫" }
        try {
            val first = frame()
            repeat(5) { frame() }
            val toEnd = ms { sim.editor.moveTo(sim.editor.text.length) } + frame() + frame()
            repeat(3) { frame() }
            val typeEnd = (0 until 20).map { i ->
                ms { sim.press(if (i % 2 == 0) digit else back) } + frame()
            }
            sim.editor.moveTo(sim.editor.lineStart(2500))
            repeat(5) { frame() }
            val typeMid = (0 until 20).map { i ->
                ms { sim.press(if (i % 2 == 0) digit else back) } + frame()
            }
            // 글을 고친 뒤 0.3 초 뒤 백그라운드 실행 — 그동안 화면이 멈추지 않는지(프레임 값)
            val idle = (0 until 30).map {
                Thread.sleep(20)
                frame()
            }
            // 궤적 창: 열기(ensureRun · 첫 프레임들) → 소재 끝까지 깎기(백그라운드)가 끝나기를 기다림 → ×200 재생 30 프레임 → 3D
            sim.speed = 200f
            val open = ms {
                sim.ensureRun()
                traj.value = TrajUi()
            } + frame() + frame()
            Thread.sleep(3000)
            repeat(5) { frame() }
            scene.tapText("재생")
            val play2d = (0 until 30).map { frame() }
            traj.value!!.mode3d = true
            val first3d = frame()
            val play3d = (0 until 30).map {
                Thread.sleep(16)
                frame()
            }
            val row = listOf(
                "긴 프로그램: 글 $lines 줄 · 이동 ${run.steps.size} · 가공 ${timeText(run.totalSec)}",
                "글 읽기 ${"%.0f".format(tParse)} ms · 모든 줄 색 낱말 ${"%.0f".format(tTokens)} ms · 실행(runOf) ${"%.0f".format(tRun)} ms · 경로 ${"%.0f".format(tPaths)} ms · " +
                    "소재 끝까지 깎기 ${"%.0f".format(tCut)} ms · 단면 ${"%.0f".format(tProfile)} ms",
                "편집기(CPU 래스터): 첫 그림 ${"%.0f".format(first)} ms · 끝으로 가기 ${"%.0f".format(toEnd)} ms · 끝에서 키 하나 ${stats(typeEnd)} · " +
                    "2500줄에서 키 하나 ${stats(typeMid)} · 다시 실행 중 프레임 ${stats(idle)}",
                "궤적 창: 열기 ${"%.0f".format(open)} ms · 2D ×200 재생 프레임 ${stats(play2d)} · 3D 첫 프레임 ${"%.0f".format(first3d)} ms · 3D 재생 프레임 ${stats(play3d)}",
            ).joinToString("\n")
            println(row)
            File("build/perf").apply { mkdirs() }.resolve("sim-long.txt").writeText(row + "\n")
            // PC JVM 기준: 실행은 1 초 안, 키 하나 한 장은 평균 100 ms 안(같은 PC 에서 다른 빌드가 같이 돌아 넉넉히)
            assertTrue(tRun < 1500, row)
            assertTrue(typeEnd.average() < 100 && typeMid.average() < 100, row)
        } finally {
            scene.close()
        }
    }

    /** 줄 번호 4자리 · 긴 줄 · 문제 없는 큰 글 — 커서 4 990줄(줄 번호 칸이 잘리지 않는지) */
    @Test
    fun longProgramShot() = Shots.both("sim-editor-long", setup = { it.open(Feature.SIMULATOR) }) { state ->
        val sim = remember {
            SimState(state.store, tools).apply {
                editor.replaceAll(program)
                editor.moveTo(editor.lineStart(4990) + 3)
                run = runOf(editor.text, tools)
            }
        }
        AppHost(state) { SimulatorPage(sim) {} }
    }
}
