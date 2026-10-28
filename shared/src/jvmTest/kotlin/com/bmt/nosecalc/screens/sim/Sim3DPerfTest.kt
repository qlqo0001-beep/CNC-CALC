package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.geometry.Rect
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.Shots
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.RevolveMesh
import com.bmt.nosecalc.calc.sim.Section
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 3D 잰 값(설계 6.2 · 6.4 · 16절): 메시 만들기(멈춤 S 96 · 끄는 중 S 32) · z-버퍼 한 장(폰 412 × 470dp 뷰포트 = 1082 × 1234 px 의 1배 · 0.5배) ·
 * ×200 재생 프레임(소재 이어 깎기 + 단면 + 거친 메시 + 0.5배 그림 — 실제는 메시를 0.1 초에 한 번만 다시 만드니 위쪽 값) — 05 꼴 부품 · 09 글 · 이동 10만 개 프로그램.
 * PC(빌드 PC · JVM)에서 잰 값이다. build/perf/sim-3d.txt 와 설계 16절에 적는다.
 */
class Sim3DPerfTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-perf3d", ".properties").apply { delete() })

    private inline fun ms(block: () -> Unit): Double {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1e6
    }

    private val w = (412 * Shots.DENSITY).roundToInt()
    private val h = (470 * Shots.DENSITY).roundToInt()
    private val area = Rect(16 * Shots.DENSITY, 160 * Shots.DENSITY, w - 64 * Shots.DENSITY, h - 140 * Shots.DENSITY)

    /** 한 프로그램: 끝 단면의 메시 · 그림, 재생 프레임 */
    private fun measure(name: String, spec: StockSpec, s: StockSim, total: Double, rows: MutableList<String>): Pair<Double, Double> {
        s.cutTo(total)
        val tol = max(0.002, spec.length / 15_000)
        val prof = s.profile(tol)
        val view = viewOf(Cam3D(), sceneOf(spec), w, h, area)
        val rd = Renderer3D()
        // 데우기
        repeat(3) {
            rd.raster(Job3D(prof, spec, true, false, view, w, h))
            rd.raster(Job3D(prof, spec, true, true, view, w, h))
        }
        val meshIdle = ArrayList<Double>()
        val meshDrag = ArrayList<Double>()
        var triIdle = 0
        var triDrag = 0
        repeat(5) {
            meshIdle += ms { triIdle = RevolveMesh.part(prof, Section.QUARTER, RevolveMesh.SEG_IDLE, RevolveMesh.TOL_IDLE, RevolveMesh.TRI_IDLE).triangles }
            meshDrag += ms { triDrag = RevolveMesh.part(prof, Section.QUARTER, RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG).triangles }
        }
        val idle = Job3D(prof, spec, true, false, view, w, h)
        val drag = Job3D(prof, spec, true, true, view, w, h)
        rd.meshes(idle)
        val drawIdle = (0 until 5).map { ms { rd.raster(idle) } }
        rd.meshes(drag)
        val drawDrag = (0 until 10).map { ms { rd.raster(drag) } }
        // ×200 · 60 fps 재생 60 프레임(가운데쯤부터): 이어 깎기 + 단면 + 거친 메시 + 0.5배 그림
        val frame = ArrayList<Double>()
        var t = total * 0.4
        s.cutTo(t)
        repeat(60) {
            t = (t + 200.0 / 60).coerceAtMost(total)
            frame += ms {
                s.cutTo(t)
                val p = s.profile(tol)
                rd.raster(Job3D(p, spec, true, true, view, w, h))
            }
        }
        rows += "$name: 삼각형 멈춤 $triIdle · 끄는 중 $triDrag · 메시 멈춤 ${"%.1f".format(meshIdle.average())} · 끄는 중 ${"%.1f".format(meshDrag.average())} ms · " +
            "그림 ${w}×$h 1배 ${"%.1f".format(drawIdle.average())} · 0.5배 ${"%.1f".format(drawDrag.average())} ms · " +
            "재생 프레임(깎기+단면+메시+0.5배) 평균 ${"%.1f".format(frame.average())} / 가장 긴 ${"%.1f".format(frame.max())} ms"
        assertTrue(triIdle <= RevolveMesh.TRI_IDLE && triDrag <= RevolveMesh.TRI_DRAG)
        return drawDrag.average() to meshDrag.average()
    }

    @Test
    fun meshAndDrawTimes() {
        val rows = ArrayList<String>()
        // 05 꼴 부품
        val sim = TrajScenes.sim(store(), TrajScenes.part3dEnd)
        val spec = stockOf(sim.stockText, sim.run).first!!
        val (draw05, mesh05) = measure("05 꼴 부품", spec, StockSim(spec, sim.run, sim.tools), sim.run.totalSec, rows)
        // 09 글(예제 ①)
        val ex = SimState(store()).apply {
            perform(Pending.Example(0))
            run = runOf(editor.text, tools)
        }
        val spec09 = stockOf(ex.stockText, ex.run).first!!
        measure("09 글 G71/G70", spec09, StockSim(spec09, ex.run, ex.tools), ex.run.totalSec, rows)
        // 이동 10만 개(TrajPerfTest 와 같은 글 — 0.0008 mm 씩 깎는 층 2만 5천)
        val big = listOf(
            "O1000 (큰 프로그램 - 층 2만 5천)", "T0101", "G97 S1000 M03", "G99", "G00 X102. Z2.", "M98 P1001 L5", "G00 X200. Z100.", "M30",
            "O1001", "M98 P1002 L5000", "M99",
            "O1002", "G01 Z-60. F0.3", "G00 U1.", "Z2.", "U-1.0016", "M99",
        ).joinToString("\n")
        val tools = mapOf(1 to ToolOffset(0.4, 3))
        val run = GSim.run(SimInput(big, MachineSetup(), tools))
        val bigSpec = requireNotNull(StockAuto.of(run))
        measure("이동 10만 개", bigSpec, StockSim(bigSpec, run, tools), run.totalSec, rows)
        val text = rows.joinToString("\n")
        println(text)
        File("build/perf").apply { mkdirs() }.resolve("sim-3d.txt").writeText(text + "\n")
        // PC JVM 에서 끄는 중 한 장(0.5배) · 거친 메시가 한 프레임 안 — 폰은 이보다 몇 배 느리다(설계 16절)
        assertTrue(draw05 < 40 && mesh05 < 40, text)
    }
}
