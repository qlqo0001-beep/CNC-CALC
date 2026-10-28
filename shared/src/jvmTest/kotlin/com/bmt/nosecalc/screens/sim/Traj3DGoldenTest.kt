package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.geometry.Rect
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.sim.Mesh
import com.bmt.nosecalc.calc.sim.RevolveMesh
import com.bmt.nosecalc.calc.sim.Section
import com.bmt.nosecalc.calc.sim.StockProfile
import com.bmt.nosecalc.calc.sim.StockSim
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 3D 숫자 지킴이(`TrajGoldenTest` 와 같은 방식, 골든 `sim-3d`): 예제마다 끝의 남은 소재 메시(멈춤 1/4 · 온통 · 끄는 중 — 삼각형 수 · 부피)와
 * 단면을 돌린 부피(손 계산 — 2π∬r dA, 96 토막 다각형 몫), 척 메시, 3D 장면마다 그 시각의 메시와 412 × 500 px 그림의 칠한 픽셀 · 단면 픽셀.
 * 나사가 없는 예제는 메시 부피(온통) = 단면 회전 부피 × 다각형 몫(1e-4 안) — 메시가 단면을 그대로 돌렸는지. 바뀌면 실패 — 일부러 바꿨을 때만 `-DupdateGolden=true`.
 */
class Traj3DGoldenTest {
    private val dir = File("src/jvmTest/resources/golden")
    private val update = System.getProperty("updateGolden") == "true"

    private fun store() = KeyValueStore(File.createTempFile("bmt-sim3d", ".properties").apply { delete() })

    private fun golden(name: String, text: String) {
        val f = File(dir, "$name.txt")
        if (update) {
            dir.mkdirs()
            f.writeText(text)
            return
        }
        assertTrue(f.exists(), "골든 파일이 없다: $f (-DupdateGolden=true 로 만든다)")
        assertEquals(f.readText().replace("\r\n", "\n"), text, "$name: 3D 숫자가 골든과 다르다")
    }

    /** 단면을 축 둘레로 돌린 부피(mm³) = 2π∬ r dA = −π/3 Σ Δz (r₀² + r₀r₁ + r₁²) (반시계 고리) */
    private fun revolved(p: StockProfile): Double = p.pieces.sumOf { pc ->
        val o = pc.outline
        var s = 0.0
        for (i in o.indices) {
            val a = o[i]
            val b = o[(i + 1) % o.size]
            s += (b.z - a.z) * (a.x * a.x + a.x * b.x + b.x * b.x)
        }
        -PI / 3 * s
    }

    /** 96 토막 다각형 넓이 / 원 넓이 */
    private val poly96 = 96 * 0.5 * kotlin.math.sin(2 * PI / 96) / PI

    private fun f1(v: Double) = "%.1f".format(v)

    private fun StringBuilder.meshLines(p: StockProfile, threads: Boolean) {
        val q = RevolveMesh.part(p, Section.QUARTER, RevolveMesh.SEG_IDLE, RevolveMesh.TOL_IDLE, RevolveMesh.TRI_IDLE)
        val full = RevolveMesh.part(p, Section.FULL, RevolveMesh.SEG_IDLE, RevolveMesh.TOL_IDLE, RevolveMesh.TRI_IDLE)
        val drag = RevolveMesh.part(p, Section.QUARTER, RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG)
        val rev = revolved(p)
        val vFull = RevolveMesh.volume(full)
        appendLine(
            "   메시: 1/4 삼각형 ${q.triangles} (단면 ${q.tag.count { it == Mesh.SECTION }}) · 부피 ${f1(RevolveMesh.volume(q))} / 온통 ${full.triangles} · 부피 ${f1(vFull)} / " +
                "끄는 중 ${drag.triangles} — 단면 회전 ${f1(rev)} mm³ × 다각형 ${f1(rev * poly96)}",
        )
        assertTrue(q.triangles <= RevolveMesh.TRI_IDLE && full.triangles <= RevolveMesh.TRI_IDLE && drag.triangles <= RevolveMesh.TRI_DRAG)
        if (!threads && rev > 0) assertEquals(rev * poly96, vFull, rev * 1e-4, "나사 없는 메시 부피 = 단면 회전 × 다각형 몫")
        if (threads) assertTrue(vFull < rev * poly96, "나사 홈만큼 작다")
    }

    @Test
    fun examples() {
        val text = buildString {
            for ((i, e) in exampleList.withIndex()) {
                val sim = SimState(store()).apply {
                    perform(Pending.Example(i))
                    run = runOf(editor.text, tools)
                }
                val run = sim.run
                appendLine("== ${i + 1}. ${e.name}")
                val spec = stockOf(sim.stockText, run).first
                if (spec == null) {
                    appendLine("   소재 없음")
                    continue
                }
                val s = StockSim(spec, run, sim.tools).also { it.cutTo(run.totalSec) }
                val p = s.profile()
                appendLine("   조각 ${p.pieces.size} · 나사 ${p.threads.size} · 척 메시 ${RevolveMesh.chuck(spec, RevolveMesh.SEG_IDLE, Section.QUARTER).triangles}")
                meshLines(p, p.threads.isNotEmpty())
            }
        }
        golden("sim-3d", text)
    }

    /** 3D 스크린샷 장면: 그 시각의 메시 · 412 × 500 px 그림(처음 시점)의 칠한 픽셀 · 단면 픽셀 */
    @Test
    fun scenes() {
        val text = buildString {
            for (sc in TrajScenes.all3d) {
                val sim = TrajScenes.sim(store(), sc)
                val ui = TrajScenes.ui(sim, sc)
                val run = sim.run
                val spec = stockOf(sim.stockText, run).first ?: continue
                val s = StockSim(spec, run, sim.tools).also { it.cutTo(ui.lastT) }
                val p = s.profile()
                appendLine("== ${sc.name} — 시각 ${timeText(ui.lastT)} / ${timeText(run.totalSec)} · 1/4 ${if (ui.cam.quarter) "켬" else "끔"} · 나사 ${p.threads.size}")
                meshLines(p, p.threads.isNotEmpty())
                val view = viewOf(ui.cam, sceneOf(spec), 412, 500, Rect(16f, 108f, 348f, 400f))
                val job = Job3D(p, spec, ui.cam.quarter, false, view, 412, 500)
                val rd = Renderer3D()
                val (pm, cm) = rd.meshes(job)
                val marks = Array(4) { t -> IntArray(256) { (0xFF shl 24) or (t + 1) } }
                val r = ZRaster(412, 500)
                r.clear()
                val m = view.matrix(withSpin = true)
                r.draw(cm, m, view.f, view.cx, view.cy, view.near, marks)
                r.draw(pm, m, view.f, view.cx, view.cy, view.near, marks)
                val by = IntArray(4) { t -> r.argb.count { it == marks[t][0] } }
                appendLine("   그림 412 × 500: 겉 ${by[0]} · 단면 ${by[1]} · 척 ${by[2]} · 턱 ${by[3]} 픽셀 · 그린 삼각형 ${r.drawn}")
            }
        }
        golden("sim-3d-scenes", text)
        // 재생하면 깎여 간다: 05 꼴 부품의 중간(0.8) 메시 부피 > 끝 부피
        val sim = TrajScenes.sim(store(), TrajScenes.part3dEnd)
        val spec = stockOf(sim.stockText, sim.run).first!!
        val s = StockSim(spec, sim.run, sim.tools)
        fun vol(t: Double): Double {
            s.cutTo(t)
            return RevolveMesh.volume(RevolveMesh.part(s.profile(), Section.FULL, 48, 0.01, RevolveMesh.TRI_IDLE))
        }
        val v0 = vol(0.0)
        val vMid = vol(sim.run.totalSec * 0.5)
        val vEnd = vol(sim.run.totalSec)
        assertTrue(v0 > vMid && vMid > vEnd && abs(v0 - vEnd) > 1000, "부피 $v0 → $vMid → $vEnd")
    }
}
