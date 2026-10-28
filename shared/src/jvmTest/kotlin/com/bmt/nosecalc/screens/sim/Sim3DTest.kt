package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.geometry.Rect
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.sim.Mesh
import com.bmt.nosecalc.calc.sim.StockSim
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 3D 보기(설계 6 · 16절): z-버퍼 래스터라이저(앞면 · 뒷면 · 깊이 · 눈 앞 자르기), 카메라(맞춘 가운데가 주점 · 처음 시점), 05 꼴 부품 글이 멈춤 · 닿음 없이 돈다,
 * 렌더러(1/4 절개면 단면 색 · 재생하면 그림이 바뀜 · 낮은 품질은 0.5배).
 */
class Sim3DTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-sim3d", ".properties").apply { delete() })

    /** 보기 좌표 그대로(카메라 = 원점, 앞 = +z) 행렬 */
    private val ident = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    /** 보기 좌표 삼각형 하나(법선은 눈 쪽 −z) */
    private fun tri(vararg p: Float, tag: Byte = Mesh.PART, flip: Boolean = false): Mesh {
        val idx = if (flip) intArrayOf(0, 2, 1) else intArrayOf(0, 1, 2)
        return Mesh(p, floatArrayOf(0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f), idx, byteArrayOf(tag))
    }

    private fun count(r: ZRaster) = r.argb.count { it != 0 }

    @Test
    fun rasterFrontBackDepthClip() {
        val r = ZRaster(100, 100)
        // 화면에서 반시계로 보이는(바깥에서 보아 반시계) 삼각형: 보기 좌표 x 오른쪽 · y 위
        val front = tri(-1f, -1f, 5f, 1f, -1f, 5f, 0f, 1f, 5f)
        r.clear()
        r.draw(front, ident, 100f, 50f, 50f, 0.1f, ZRaster.RAMPS)
        val n = count(r)
        // 넓이 = 밑변 40px × 높이 40px / 2 = 800
        assertTrue(abs(n - 800) < 60, "앞면 픽셀 $n")
        // 감김이 뒤집힌 것(뒷면)은 버린다
        r.clear()
        r.draw(tri(-1f, -1f, 5f, 1f, -1f, 5f, 0f, 1f, 5f, flip = true), ident, 100f, 50f, 50f, 0.1f, ZRaster.RAMPS)
        assertEquals(0, count(r), "뒷면")
        // 깊이: 가까운 단면(밝은 색 띠)이 먼 겉면 위에 — 그리는 순서와 상관없이
        val far = tri(-1f, -1f, 6f, 1f, -1f, 6f, 0f, 1f, 6f, tag = Mesh.PART)
        val near = tri(-0.5f, -0.5f, 4f, 0.5f, -0.5f, 4f, 0f, 0.5f, 4f, tag = Mesh.SECTION)
        for (order in listOf(listOf(far, near), listOf(near, far))) {
            r.clear()
            for (m in order) r.draw(m, ident, 100f, 50f, 50f, 0.1f, ZRaster.RAMPS)
            val center = r.argb[52 * 100 + 50]
            assertTrue(ZRaster.RAMPS[Mesh.SECTION.toInt()].contains(center), "가운데는 가까운 단면 색")
        }
        // 눈 뒤로 걸친 삼각형은 눈 앞 평면에서 잘라 그린다(멈추지 않고 일부를 그림)
        r.clear()
        r.draw(tri(-1f, -1f, 2f, 1f, -1f, 2f, 0f, 1f, -1f), ident, 100f, 50f, 50f, 0.1f, ZRaster.RAMPS)
        assertTrue(count(r) > 0, "자른 삼각형")
    }

    @Test
    fun cameraFitsSceneToArea() {
        val sim = TrajScenes.sim(store(), TrajScenes.part3dEnd)
        val spec = assertNotNull(stockOf(sim.stockText, sim.run).first)
        val scene = sceneOf(spec)
        val cam = Cam3D()
        val area = Rect(42f, 280f, 1000f, 1900f)
        val v = viewOf(cam, scene, 1081, 2000, area)
        val m = v.matrix(withSpin = false)
        // 맞춘 가운데(축 위 소재 가운데)가 주점(겹친 단추를 뺀 곳의 가운데)에
        val x = scene.cx
        val zv = m[8] * x + m[11]
        val sx = v.cx + v.f * (m[0] * x + m[3]) / zv
        val sy = v.cy - v.f * (m[4] * x + m[7]) / zv
        assertEquals(area.center.x, sx, 0.5f)
        assertEquals(area.center.y, sy, 0.5f)
        // 처음 시점은 오른쪽 앞 위(05): 눈이 끝면 쪽 · 앞 · 위
        assertTrue(v.ex > scene.cx && v.ez > 0 && v.ey > 0)
        // 맞춤은 처음 시점으로
        cam.orbit(100f, -40f)
        cam.zoomBy(3f)
        cam.fit()
        assertEquals(v, viewOf(cam, scene, 1081, 2000, area))
    }

    /** 05 꼴 부품 글: 멈춤 없음 · 급송 깎음 · 척 닿음 없음 · 나사 둘(바깥 M30 · 안 M24) */
    @Test
    fun part05Runs() {
        val sim = TrajScenes.sim(store(), TrajScenes.part3dEnd)
        val run = sim.run
        assertNull(run.stop, "멈춤: ${run.stop}")
        val spec = assertNotNull(stockOf(sim.stockText, run).first)
        val s = StockSim(spec, run, sim.tools).also { it.cutTo(run.totalSec) }
        assertTrue(s.contacts.isEmpty(), "닿음: ${s.contacts}")
        val prof = s.profile()
        assertEquals(listOf(true, false), prof.threads.map { it.outer }, "바깥 · 안 나사")
    }

    /** 렌더러: 1/4 이면 단면 색이 보이고, 재생 중간과 끝 그림이 다르고, 낮은 품질은 0.5배 크기 */
    @Test
    fun rendererQuarterPlaybackLow() {
        val sim = TrajScenes.sim(store(), TrajScenes.part3dEnd)
        val run = sim.run
        val spec = assertNotNull(stockOf(sim.stockText, run).first)
        val s = StockSim(spec, run, sim.tools)
        val scene = sceneOf(spec)
        val cam = Cam3D()
        val view = viewOf(cam, scene, 400, 500, Rect(10f, 60f, 380f, 440f))
        val rd = Renderer3D()
        s.cutTo(run.totalSec)
        val end = s.profile()
        fun shot(p: com.bmt.nosecalc.calc.sim.StockProfile, quarter: Boolean, low: Boolean = false): IntArray =
            rd.raster(Job3D(p, spec, quarter, low, view, 400, 500)).argb.copyOf()
        // 단면 픽셀 세기: 겉면 · 단면 음영 띠는 같은 회색을 나눠 가지므로 tag 마다 한 색인 띠로 다시 그린다
        val marks = Array(4) { t -> IntArray(256) { (0xFF shl 24) or (t + 1) } }
        fun sections(quarter: Boolean): Int {
            val job = Job3D(end, spec, quarter, false, view, 400, 500)
            val (pm, cm) = rd.meshes(job)
            val r = ZRaster(400, 500)
            r.clear()
            val m = view.matrix(withSpin = true)
            r.draw(cm, m, view.f, view.cx, view.cy, view.near, marks)
            r.draw(pm, m, view.f, view.cx, view.cy, view.near, marks)
            return r.argb.count { it == marks[Mesh.SECTION.toInt()][0] }
        }
        val q = shot(end, true)
        val qs = sections(true)
        val fs = sections(false)
        assertTrue(qs > 500 && fs == 0, "1/4 단면 픽셀 $qs · 절개 끔 $fs")
        s.cutTo(run.totalSec * 0.3)
        val mid = shot(s.profile(), true)
        val diff = q.indices.count { q[it] != mid[it] }
        assertTrue(diff > 2000, "재생 중간과 끝 그림 차 $diff 픽셀")
        // 재생 중 메시 제한: 단면만 다르면 같은 그림 자리(기다림), 시점이 다르면 앞 단면으로 시점만 먼저
        val j1 = Job3D(end, spec, true, true, view, 400, 500)
        assertTrue(j1.sameFrame(j1.withProfile(null)) && j1.withProfile(null) != j1)
        assertTrue(!j1.sameFrame(Job3D(end, spec, true, true, view.copy(spin = 10f), 400, 500)))
        val low = rd.raster(Job3D(end, spec, true, true, view, 400, 500))
        assertEquals(200, low.w)
        assertEquals(250, low.h)
        assertTrue(low.argb.count { it != 0 } > 1000)
    }
}
