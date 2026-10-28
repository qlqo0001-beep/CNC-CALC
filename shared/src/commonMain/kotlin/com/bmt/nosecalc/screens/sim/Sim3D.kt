package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bmt.nosecalc.calc.sim.Mesh
import com.bmt.nosecalc.calc.sim.RevolveMesh
import com.bmt.nosecalc.calc.sim.Section
import com.bmt.nosecalc.calc.sim.StockProfile
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.time.TimeSource

/** 처음 시점(05 처럼 오른쪽 앞 위에서 내려다봄 — 공작물 축이 화면에서 비스듬) */
private const val YAW0 = 50f
private const val PITCH0 = 27f

/** 세로 시야(설계 6.3 — 35°) */
private const val FOV_DEG = 35f

/** '회전' 이 켜지면 공작물이 축 둘레로 도는 빠르기(설계 6.3 — 초당 20°) */
private const val SPIN_DEG_PER_SEC = 20f

/** 끄는 동안 · 재생 중 해상도(설계 6.4 — 화면 픽셀의 0.5배) */
internal const val LOW_SCALE = 0.5f

/**
 * 3D 보기 카메라 · 손짓 상태(설계 6.3). 처음 = 05 의 시점(오른쪽 앞 위), 소재(척 턱 ~ 끝면)를 겹친 단추 · 카드를 뺀 곳에 맞춘다.
 * [yaw] = 위에서 보아 앞(+z)에서 오른쪽으로 돈 각, [pitch] = 내려다본 각, [zoom] = 맞춘 거리의 배수(크면 가까이), [panX]~[panZ] = 맞춘 가운데에서 옮긴 것(mm).
 * [spin] = 공작물(척 포함)이 축 둘레로 돈 각 — '회전'(켬/끔)이 켜지면 초당 20°. [free] = '자유'(한 손가락이 시점 돌리기, 끄면 이동). [quarter] = 1/4 단면 절개(처음 켬 — 05).
 */
@Stable
internal class Cam3D {
    var yaw by mutableFloatStateOf(YAW0)
    var pitch by mutableFloatStateOf(PITCH0)
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var panZ by mutableFloatStateOf(0f)
    var spin by mutableFloatStateOf(0f)
    var spinning by mutableStateOf(false)
    var free by mutableStateOf(false)
    var quarter by mutableStateOf(true)

    /** 손가락 · 마우스로 끄는 중(낮은 해상도로 그림) */
    var dragging by mutableStateOf(false)

    /** 처음 손짓을 했나(05 의 안내 칩을 숨김) */
    var touched by mutableStateOf(false)

    /** 맞춤(⤢): 처음 시점 · 거리 · 가운데, 회전 각도 처음으로 */
    fun fit() {
        yaw = YAW0
        pitch = PITCH0
        zoom = 1f
        panX = 0f
        panY = 0f
        panZ = 0f
        spin = 0f
    }

    fun zoomBy(f: Float) {
        zoom = (zoom * f).coerceIn(0.25f, 40f)
    }

    /** 자유 시점: 오른쪽으로 끌면 공작물이 오른쪽으로 돈다(카메라는 왼쪽으로) */
    fun orbit(dxDp: Float, dyDp: Float) {
        yaw = (yaw - dxDp * 0.4f) % 360f
        pitch = (pitch + dyDp * 0.4f).coerceIn(-85f, 85f)
    }

    /** 이동: 손가락을 따라 그림이 움직이게(가운데 깊이에서 픽셀 → mm) */
    fun pan(dxPx: Float, dyPx: Float, v: View3D) {
        val k = v.dist / v.f
        panX += (-v.rx * dxPx + v.ux * dyPx) * k
        panY += (-v.ry * dxPx + v.uy * dyPx) * k
        panZ += (-v.rz * dxPx + v.uz * dyPx) * k
    }
}

/** 장면 크기(소재에서): 맞출 구(가운데 x · 반지름), 바닥 높이 · 격자 간격 · 넓이 */
internal class Scene3D(val cx: Float, val radius: Float, val floorY: Float, val gridStep: Float, val extent: Float)

internal fun sceneOf(spec: StockSpec): Scene3D {
    val jaw = RevolveMesh.jawLength(spec)
    // 맞추는 것은 소재(척 면 ~ 끝면) + 턱에 물린 곳 조금 — 05 처럼 부품이 화면을 채우고 척 원판은 뒤로 넘친다
    val x0 = spec.faceZ - spec.length - 0.25 * jaw
    val x1 = spec.faceZ
    val r = spec.dia / 2
    val half = (x1 - x0) / 2
    val big = max(spec.chuckDia / 2, r)
    val extent = 3 * max(big, half + r + jaw)
    // 격자 간격: 넓이를 열두 칸쯤으로 나누는 1 · 2 · 5 × 10ⁿ
    val raw = extent / 6
    val pw = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * pw }.first { it >= raw }
    return Scene3D(((x0 + x1) / 2).toFloat(), sqrt(half * half + r * r).toFloat(), (-big * 1.06).toFloat(), step.toFloat(), extent.toFloat())
}

/**
 * 한 장의 시점(원근 — 설계 6.3): 눈 [ex]~[ez], 오른쪽 · 위 · 앞 단위 벡터, 초점 거리 [f](px) · 주점 [cx] · [cy](px — 겹친 단추를 뺀 곳의 가운데),
 * [dist] = 눈 ~ 가운데, [spin] = 공작물 회전(도). 보기 좌표 x = 오른쪽 · y = 위 · z = 앞(깊이, 양수가 눈앞).
 */
internal data class View3D(
    val ex: Float, val ey: Float, val ez: Float,
    val rx: Float, val ry: Float, val rz: Float,
    val ux: Float, val uy: Float, val uz: Float,
    val fx: Float, val fy: Float, val fz: Float,
    val f: Float, val cx: Float, val cy: Float, val dist: Float, val spin: Float,
) {
    val near: Float get() = dist * 0.01f

    /** 모형(공작물 좌표) → 보기 좌표 3×4 행렬. withSpin = 공작물 회전(x 축 둘레)을 넣음(바닥은 넣지 않음) */
    fun matrix(withSpin: Boolean): FloatArray {
        val a = if (withSpin) spin * PI.toFloat() / 180f else 0f
        val c = cos(a)
        val s = sin(a)
        fun row(x: Float, y: Float, z: Float): FloatArray =
            floatArrayOf(x, y * c + z * s, -y * s + z * c, -(x * ex + y * ey + z * ez))
        return row(rx, ry, rz) + row(ux, uy, uz) + row(fx, fy, fz)
    }
}

internal fun viewOf(cam: Cam3D, scene: Scene3D, w: Int, h: Int, area: Rect): View3D {
    val f = (h / 2f) / tan(FOV_DEG / 2 * PI.toFloat() / 180f)
    val half = atan(max(min(area.width, area.height), 1f) / 2 / f)
    // 구가 맞춤 곳에 꼭 닿지 않게 조금 물러선다
    val d = 1.12f * scene.radius / sin(half) / cam.zoom
    val tx = scene.cx + cam.panX
    val ty = cam.panY
    val tz = cam.panZ
    val yaw = cam.yaw * PI.toFloat() / 180f
    val pitch = cam.pitch * PI.toFloat() / 180f
    val ex = tx + d * cos(pitch) * sin(yaw)
    val ey = ty + d * sin(pitch)
    val ez = tz + d * cos(pitch) * cos(yaw)
    var fx = tx - ex
    var fy = ty - ey
    var fz = tz - ez
    val fl = sqrt(fx * fx + fy * fy + fz * fz)
    fx /= fl; fy /= fl; fz /= fl
    // 오른쪽 = 앞 × 위(0, 1, 0), 위 = 오른쪽 × 앞
    var rx = -fz
    val ry = 0f
    var rz = fx
    val rl = sqrt(rx * rx + rz * rz)
    rx /= rl; rz /= rl
    val ux = ry * fz - rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy - ry * fx
    return View3D(ex, ey, ez, rx, ry, rz, ux, uy, uz, fx, fy, fz, f, area.center.x, area.center.y, d, cam.spin)
}

/**
 * CPU z-버퍼 래스터라이저(설계 6.4): 오목한 홈 · 구멍 · 나사와 1/4 단면이 겹쳐도 앞뒤가 맞게. 꼭짓점마다 밝기(주변광 0.35 + 람베르트 0.65 + 금속 반짝임,
 * 빛 = 카메라 왼쪽 위)를 정해 화면에서 잇고(구로 음영) 깊이는 1/z 를 잇는다. 뒷면(바깥에서 보아 반시계 감김이 화면에서 뒤집힌 것)은 버린다.
 * 눈 앞 [View3D.near] 를 넘는 삼각형은 그 평면에서 잘라 그린다. [argb] 는 그리지 않은 곳이 투명(0) — 바닥 격자 위에 얹는다.
 */
internal class ZRaster(val w: Int, val h: Int) {
    val argb = IntArray(w * h)
    private val depth = FloatArray(w * h)
    private var sx = FloatArray(0)
    private var sy = FloatArray(0)
    private var sd = FloatArray(0)
    private var si = FloatArray(0)
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var vz = FloatArray(0)

    /** 그린(뒷면 · 화면 밖이 아닌) 삼각형 수 — 잰 값 · 시험용 */
    var drawn = 0
        private set

    fun clear() {
        argb.fill(0)
        depth.fill(0f)
        drawn = 0
    }

    fun draw(mesh: Mesh, m: FloatArray, f: Float, cx: Float, cy: Float, near: Float, ramps: Array<IntArray>) {
        val n = mesh.vertices
        if (n == 0) return
        if (sx.size < n) {
            sx = FloatArray(n); sy = FloatArray(n); sd = FloatArray(n); si = FloatArray(n)
            vx = FloatArray(n); vy = FloatArray(n); vz = FloatArray(n)
        }
        val p = mesh.pos
        val nr = mesh.nrm
        for (i in 0 until n) {
            val k = 3 * i
            val x = p[k]
            val y = p[k + 1]
            val z = p[k + 2]
            val xv = m[0] * x + m[1] * y + m[2] * z + m[3]
            val yv = m[4] * x + m[5] * y + m[6] * z + m[7]
            val zv = m[8] * x + m[9] * y + m[10] * z + m[11]
            val nx = nr[k]
            val ny = nr[k + 1]
            val nz = nr[k + 2]
            val ax = m[0] * nx + m[1] * ny + m[2] * nz
            val ay = m[4] * nx + m[5] * ny + m[6] * nz
            val az = m[8] * nx + m[9] * ny + m[10] * nz
            si[i] = shade(ax, ay, az)
            vx[i] = xv
            vy[i] = yv
            vz[i] = zv
            if (zv > near) {
                val iz = 1f / zv
                sx[i] = cx + f * xv * iz
                sy[i] = cy - f * yv * iz
                sd[i] = iz
            }
        }
        val t = mesh.tri
        val tags = mesh.tag
        for (k in 0 until mesh.triangles) {
            val a = t[3 * k]
            val b = t[3 * k + 1]
            val c = t[3 * k + 2]
            val ramp = ramps[tags[k].toInt()]
            if (vz[a] > near && vz[b] > near && vz[c] > near) {
                fill(sx[a], sy[a], sd[a], si[a], sx[b], sy[b], sd[b], si[b], sx[c], sy[c], sd[c], si[c], ramp)
            } else if (vz[a] > near || vz[b] > near || vz[c] > near) {
                clipped(a, b, c, f, cx, cy, near, ramp)
            }
        }
    }

    /** 눈 앞 평면에서 자른 다각형(3~4 점)을 부채꼴로 */
    private fun clipped(a: Int, b: Int, c: Int, f: Float, cx: Float, cy: Float, near: Float, ramp: IntArray) {
        val ids = intArrayOf(a, b, c)
        val ox = FloatArray(4)
        val oy = FloatArray(4)
        val od = FloatArray(4)
        val oi = FloatArray(4)
        var n = 0
        fun put(x: Float, y: Float, z: Float, i: Float) {
            val iz = 1f / z
            ox[n] = cx + f * x * iz
            oy[n] = cy - f * y * iz
            od[n] = iz
            oi[n] = i
            n++
        }
        for (e in 0 until 3) {
            val p = ids[e]
            val q = ids[(e + 1) % 3]
            val pin = vz[p] > near
            val qin = vz[q] > near
            if (pin) put(vx[p], vy[p], vz[p], si[p])
            if (pin != qin) {
                val tt = (near - vz[p]) / (vz[q] - vz[p])
                put(vx[p] + (vx[q] - vx[p]) * tt, vy[p] + (vy[q] - vy[p]) * tt, near, si[p] + (si[q] - si[p]) * tt)
            }
        }
        for (k in 1 until n - 1) fill(ox[0], oy[0], od[0], oi[0], ox[k], oy[k], od[k], oi[k], ox[k + 1], oy[k + 1], od[k + 1], oi[k + 1], ramp)
    }

    /** 삼각형 하나: 픽셀 가운데 표본, 줄마다 세 변과의 교차로 가로 구간, 1/z · 밝기는 화면에서 평면으로 잇는다 */
    private fun fill(
        x0: Float, y0: Float, d0: Float, i0: Float,
        x1: Float, y1: Float, d1: Float, i1: Float,
        x2: Float, y2: Float, d2: Float, i2: Float,
        ramp: IntArray,
    ) {
        val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        // 화면 y 가 아래로 — 바깥에서 보아 반시계(앞면)는 음수. 0 이상 = 뒷면 · 넓이 0
        if (area >= 0f) return
        val ymin = min(y0, min(y1, y2))
        val ymax = max(y0, max(y1, y2))
        val r0 = max(0, ceil(ymin - 0.5f).toInt())
        val r1 = min(h - 1, ceil(ymax - 0.5f).toInt() - 1)
        if (r0 > r1) return
        val xmin = min(x0, min(x1, x2))
        val xmax = max(x0, max(x1, x2))
        if (xmax < 0f || xmin > w) return
        val inv = 1f / area
        val ddx = ((d1 - d0) * (y2 - y0) - (d2 - d0) * (y1 - y0)) * inv
        val ddy = ((d2 - d0) * (x1 - x0) - (d1 - d0) * (x2 - x0)) * inv
        val idx = ((i1 - i0) * (y2 - y0) - (i2 - i0) * (y1 - y0)) * inv
        val idy = ((i2 - i0) * (x1 - x0) - (i1 - i0) * (x2 - x0)) * inv
        drawn++
        for (row in r0..r1) {
            val yc = row + 0.5f
            var xl = Float.POSITIVE_INFINITY
            var xr = Float.NEGATIVE_INFINITY
            if ((y0 <= yc && y1 > yc) || (y1 <= yc && y0 > yc)) {
                val x = x0 + (yc - y0) * (x1 - x0) / (y1 - y0)
                if (x < xl) xl = x
                if (x > xr) xr = x
            }
            if ((y1 <= yc && y2 > yc) || (y2 <= yc && y1 > yc)) {
                val x = x1 + (yc - y1) * (x2 - x1) / (y2 - y1)
                if (x < xl) xl = x
                if (x > xr) xr = x
            }
            if ((y2 <= yc && y0 > yc) || (y0 <= yc && y2 > yc)) {
                val x = x2 + (yc - y2) * (x0 - x2) / (y0 - y2)
                if (x < xl) xl = x
                if (x > xr) xr = x
            }
            if (xl > xr) continue
            val c0 = max(0, ceil(xl - 0.5f).toInt())
            val c1 = min(w - 1, ceil(xr - 0.5f).toInt() - 1)
            if (c0 > c1) continue
            val dxs = c0 + 0.5f - x0
            val dys = yc - y0
            var d = d0 + ddx * dxs + ddy * dys
            var li = i0 + idx * dxs + idy * dys
            var at = row * w + c0
            for (col in c0..c1) {
                if (d > depth[at]) {
                    depth[at] = d
                    val q = li.toInt()
                    argb[at] = ramp[if (q < 0) 0 else if (q > 255) 255 else q]
                }
                d += ddx
                li += idx
                at++
            }
        }
    }

    companion object {
        // 빛(보기 좌표, 표면에서 빛 쪽): 카메라 왼쪽 위 앞
        private val L = norm(-0.45f, 0.65f, -0.62f)

        // 반짝임(Blinn): 빛과 눈(0, 0, −1) 사이
        private val H = norm(L[0], L[1], L[2] - 1f)

        private fun norm(x: Float, y: Float, z: Float): FloatArray {
            val l = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / l, y / l, z / l)
        }

        /** 보기 좌표 법선 → 음영 띠 번호(0~255 — 밝기 0 ~ 1.35) */
        fun shade(nx: Float, ny: Float, nz: Float): Float {
            val diff = max(0f, nx * L[0] + ny * L[1] + nz * L[2])
            val h = max(0f, nx * H[0] + ny * H[1] + nz * H[2])
            val h2 = h * h
            val h4 = h2 * h2
            val h8 = h4 * h4
            val spec = h8 * h8 * h8
            val light = 0.35f + 0.65f * diff + 0.35f * spec
            return light * (255f / 1.35f)
        }

        /** tag 마다 256 칸 음영 띠([Sim3DColors] 의 자리 색을 잇는다) */
        val RAMPS: Array<IntArray> by lazy {
            Array(Sim3DColors.ramps.size) { t ->
                val cs = Sim3DColors.ramps[t]
                val st = Sim3DColors.STOPS
                IntArray(256) { i ->
                    val v = i / 255f * 1.35f
                    var k = 0
                    while (k < st.size - 2 && v > st[k + 1]) k++
                    val u = ((v - st[k]) / (st[k + 1] - st[k])).coerceIn(0f, 1f)
                    lerpArgb(cs[k], cs[k + 1], u)
                }
            }
        }

        private fun lerpArgb(a: Int, b: Int, u: Float): Int {
            fun ch(s: Int): Int {
                val x = (a shr s) and 0xFF
                val y = (b shr s) and 0xFF
                return (x + (y - x) * u).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

/** 한 번 그린 3D 그림: [view] = 그린 시점(바닥 격자를 같은 시점으로), [low] = 낮은 해상도 · 메시 */
internal class Frame3D(val image: ImageBitmap, val view: View3D, val low: Boolean, val triangles: Int)

/** 그릴 일 하나(화면 상태를 백그라운드에 넘기려고 뽑아 둔 값) — 단면은 같은 객체인지로 견준다(데이터 비교는 크다) */
internal class Job3D(
    val profile: StockProfile?,
    val spec: StockSpec?,
    val quarter: Boolean,
    val low: Boolean,
    val view: View3D,
    val w: Int,
    val h: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is Job3D && other.profile === profile && other.spec == spec && other.quarter == quarter && other.low == low &&
            other.view == view && other.w == w && other.h == h

    override fun hashCode(): Int = view.hashCode() * 31 + w * 7 + h

    /** 단면 말고는 같은 그림인가(시점 · 크기 · 절개 · 품질) */
    fun sameFrame(o: Job3D): Boolean = o.view == view && o.w == w && o.h == h && o.quarter == quarter && o.low == low && o.spec == spec

    fun withProfile(p: StockProfile?): Job3D = Job3D(p, spec, quarter, low, view, w, h)
}

/**
 * 메시를 만들고(설계 6.2 — 끄는 동안 · 재생 중 S 32 · tol 0.02 · 4만, 멈추면 S 96 · tol 0.005 · 12만) z-버퍼로 그려 그림으로(설계 6.4 — 끄는 동안 0.5배).
 * 단면 · 절개 · 품질이 같으면 메시를 다시 쓰고, 그림은 셋을 돌려 쓴다(화면이 그리는 그림에 쓰지 않게). 한 번에 한 스레드에서만 부른다.
 */
internal class Renderer3D {
    private var partOf: StockProfile? = null
    private var partSec: Section? = null
    private var partLow = false
    private var part: Mesh = Mesh.EMPTY
    private var chuckOf: StockSpec? = null
    private var chuckSec: Section? = null
    private var chuckLow = false
    private var chuck: Mesh = Mesh.EMPTY
    private val rasters = ArrayList<ZRaster>()
    private val pool = arrayOfNulls<ImageBitmap>(3)
    private var slot = 0

    /** 마지막 한 장의 잰 값(ms) — 성능 시험 · 설계 16절 */
    var meshMs = 0.0
        private set
    var drawMs = 0.0
        private set

    fun willRemesh(job: Job3D): Boolean {
        val sec = if (job.quarter) Section.QUARTER else Section.FULL
        return job.profile !== partOf || sec != partSec || job.low != partLow || job.spec != chuckOf || sec != chuckSec || job.low != chuckLow
    }

    fun meshes(job: Job3D): Pair<Mesh, Mesh> {
        val sec = if (job.quarter) Section.QUARTER else Section.FULL
        val mark = TimeSource.Monotonic.markNow()
        var built = false
        if (job.profile !== partOf || sec != partSec || job.low != partLow) {
            part = job.profile?.let {
                if (job.low) RevolveMesh.part(it, sec, RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG)
                else RevolveMesh.part(it, sec, RevolveMesh.SEG_IDLE, RevolveMesh.TOL_IDLE, RevolveMesh.TRI_IDLE)
            } ?: Mesh.EMPTY
            partOf = job.profile
            partSec = sec
            partLow = job.low
            built = true
        }
        if (job.spec != chuckOf || sec != chuckSec || job.low != chuckLow) {
            chuck = job.spec?.let { RevolveMesh.chuck(it, if (job.low) RevolveMesh.SEG_DRAG else RevolveMesh.SEG_IDLE, sec) } ?: Mesh.EMPTY
            chuckOf = job.spec
            chuckSec = sec
            chuckLow = job.low
            built = true
        }
        if (built) meshMs = mark.elapsedNow().inWholeMicroseconds / 1000.0
        return part to chuck
    }

    /** z-버퍼 그림만(그림으로 바꾸기 전 — 시험이 픽셀을 본다) */
    fun raster(job: Job3D): ZRaster {
        val (pm, cm) = meshes(job)
        val scale = if (job.low) LOW_SCALE else 1f
        val w = max(1, (job.w * scale).roundToInt())
        val h = max(1, (job.h * scale).roundToInt())
        val mark = TimeSource.Monotonic.markNow()
        val r = rasters.firstOrNull { it.w == w && it.h == h } ?: ZRaster(w, h).also {
            // 낮은 · 높은 해상도 둘만 둔다
            if (rasters.size >= 2) rasters.removeAt(0)
            rasters += it
        }
        r.clear()
        val v = job.view
        val k = w.toFloat() / job.w
        val m = v.matrix(withSpin = true)
        r.draw(cm, m, v.f * k, v.cx * k, v.cy * k, v.near, ZRaster.RAMPS)
        r.draw(pm, m, v.f * k, v.cx * k, v.cy * k, v.near, ZRaster.RAMPS)
        drawMs = mark.elapsedNow().inWholeMicroseconds / 1000.0
        return r
    }

    fun render(job: Job3D): Frame3D {
        val r = raster(job)
        val img = argbToImageBitmap(r.argb, r.w, r.h, pool[slot])
        pool[slot] = img
        slot = (slot + 1) % pool.size
        return Frame3D(img, job.view, job.low, part.triangles + chuck.triangles)
    }
}

/** 맞춤에 쓰는 곳: 겹친 단추 · 카드 자리를 뺀 곳(왼 · 위 · 오른 · 아래 dp) */
internal fun Density.area3d(size: IntSize, insets: List<Dp>): Rect {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val l = insets[0].toPx()
    val t = insets[1].toPx()
    val r = w - insets[2].toPx()
    val b = h - insets[3].toPx()
    return if (r - l < 40 || b - t < 40) Rect(0f, 0f, max(w, 1f), max(h, 1f)) else Rect(l, t, r, b)
}

/**
 * 3D 보기(설계 6 — 05): 어두운 바탕 · 원근 바닥 격자 · 척(몸통 + 턱 셋) · 남은 소재 회전 몸체(1/4 절개면 단면을 밝게). 공구는 그리지 않는다(05).
 * 그리기는 백그라운드에서(메시 → z-버퍼 → 그림) 한 장씩 — 끄는 중 · '회전' · 재생 중이면 낮은 품질(0.5배 · 거친 메시), 멈추면 높은 품질로 다시.
 * 재생 중 메시는 0.1 초에 한 번까지만 다시 만든다(설계 6.2). 손짓: 한 손가락 = '자유' 면 돌리기 · 아니면 이동, 두 손가락 = 확대 · 이동, 휠 = 가까이/멀리.
 */
@Composable
internal fun Sim3DView(
    spec: StockSpec?,
    profile: State<StockProfile?>,
    cam: Cam3D,
    playing: () -> Boolean,
    insets: List<Dp>,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scene = remember(spec) { spec?.let(::sceneOf) }
    val renderer = remember { Renderer3D() }
    var frame by remember { mutableStateOf<Frame3D?>(null) }
    // 그리기 고리는 한 번만 시작한다 — 단면 상태(소재 계산이 끝나면 새 객체) · 소재 · 장면은 늘 지금 것으로
    val profileNow by rememberUpdatedState(profile)
    val specNow by rememberUpdatedState(spec)
    val sceneNow by rememberUpdatedState(scene)
    val insetsNow by rememberUpdatedState(insets)
    val playingNow by rememberUpdatedState(playing)

    fun viewNow(): View3D? {
        val sc = sceneNow ?: return null
        val s = size
        if (s.width <= 0 || s.height <= 0) return null
        return viewOf(cam, sc, s.width, s.height, density.area3d(s, insetsNow))
    }

    fun jobNow(): Job3D? {
        val v = viewNow() ?: return null
        val low = cam.dragging || cam.spinning || playingNow()
        return Job3D(profileNow.value, specNow, cam.quarter, low, v, size.width, size.height)
    }

    LaunchedEffect(renderer) {
        var last: Job3D? = null
        var meshMark = TimeSource.Monotonic.markNow()
        while (true) {
            var job = snapshotFlow { jobNow() }.filterNotNull().first { it != last }
            // 재생 중 메시 다시 만들기는 0.1 초에 한 번까지(그사이 바뀐 단면은 다음 것에 묶인다). 시점도 바뀌었으면(끌기 · 회전) 앞 단면으로 시점만 먼저
            val prev = last
            if (prev != null && job.low && job.profile !== prev.profile) {
                val wait = 100 - meshMark.elapsedNow().inWholeMilliseconds
                if (wait > 0) {
                    if (job.sameFrame(prev)) {
                        delay(wait)
                        job = jobNow() ?: continue
                    } else {
                        job = job.withProfile(prev.profile)
                    }
                }
            }
            val remesh = renderer.willRemesh(job)
            val f = withContext(Dispatchers.Default) { renderer.render(job) }
            if (remesh) meshMark = TimeSource.Monotonic.markNow()
            frame = f
            last = job
            // 그 그림이 한 번 그려진 뒤에 다음 그림을 쓴다(돌려 쓰는 그림 셋)
            withFrameNanos { }
        }
    }
    LaunchedEffect(cam.spinning) {
        if (!cam.spinning) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (cam.spinning) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                cam.spin = (cam.spin + SPIN_DEG_PER_SEC * dt) % 360f
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(c.viewport3d)
            .onSizeChanged { size = it }
            .pointerInput(cam) { camGestures(cam) { viewNow() } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fr = frame
            val v = fr?.view ?: viewNow()
            val sc = scene
            if (v != null && sc != null) floorGrid(v, sc)
            if (fr != null) {
                drawImage(
                    fr.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(fr.image.width, fr.image.height),
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                    filterQuality = if (fr.low) FilterQuality.Low else FilterQuality.None,
                )
            }
        }
    }
}

/** 원근 바닥 격자(05): 척 아래 평면, 가운데에서 멀수록 옅게. 눈이 바닥 아래면 그리지 않는다 */
private fun DrawScope.floorGrid(v: View3D, s: Scene3D) {
    if (v.ey <= s.floorY) return
    val m = v.matrix(withSpin = false)
    val y = s.floorY
    val e = s.extent
    val step = s.gridStep
    val near = v.near
    val base = Sim3DColors.grid
    val k = ceil(e / step).toInt()
    val sub = 12
    fun seg(x0: Float, z0: Float, x1: Float, z1: Float) {
        for (i in 0 until sub) {
            val ax = x0 + (x1 - x0) * i / sub
            val az = z0 + (z1 - z0) * i / sub
            val bx = x0 + (x1 - x0) * (i + 1) / sub
            val bz = z0 + (z1 - z0) * (i + 1) / sub
            val da = m[8] * ax + m[9] * y + m[10] * az + m[11]
            val db = m[8] * bx + m[9] * y + m[10] * bz + m[11]
            if (da <= near || db <= near) continue
            val mx = (ax + bx) / 2 - s.cx
            val mz = (az + bz) / 2
            val fade = (1f - sqrt(mx * mx + mz * mz) / e).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            val pa = Offset(v.cx + v.f * (m[0] * ax + m[1] * y + m[2] * az + m[3]) / da, v.cy - v.f * (m[4] * ax + m[5] * y + m[6] * az + m[7]) / da)
            val pb = Offset(v.cx + v.f * (m[0] * bx + m[1] * y + m[2] * bz + m[3]) / db, v.cy - v.f * (m[4] * bx + m[5] * y + m[6] * bz + m[7]) / db)
            drawLine(base.copy(alpha = 0.9f * fade * fade), pa, pb, 1f)
        }
    }
    for (i in -k..k) {
        val z = i * step
        seg(s.cx - k * step, z, s.cx + k * step, z)
        val x = s.cx + i * step
        seg(x, -k * step, x, k * step)
    }
}

/**
 * 손짓(설계 6.3): 한 손가락 · 마우스 끌기 = '자유' 면 시점 돌리기 · 아니면 이동, 두 손가락 = 확대 · 이동, 휠 = 가까이/멀리.
 * 끄는 동안 [Cam3D.dragging](낮은 품질), 처음 손짓에 [Cam3D.touched](안내 칩을 숨김).
 */
private suspend fun PointerInputScope.camGestures(cam: Cam3D, view: () -> View3D?) = awaitEachGesture {
    val first = awaitPointerEvent()
    val ch = first.changes.first()
    if (first.type == PointerEventType.Scroll) {
        cam.zoomBy(1.15f.pow(-ch.scrollDelta.y))
        cam.touched = true
        ch.consume()
        return@awaitEachGesture
    }
    if (!ch.changedToDown()) return@awaitEachGesture
    val slop = viewConfiguration.touchSlop
    var pan = Offset.Zero
    var zoom = 1f
    var active = false
    try {
        while (true) {
            val e = awaitPointerEvent()
            val pressed = e.changes.count { it.pressed }
            if (pressed == 0) break
            val pc = e.calculatePan()
            val zc = e.calculateZoom()
            pan += pc
            zoom *= zc
            if (!active) active = pan.getDistance() > slop || abs(1 - zoom) * e.calculateCentroidSize(useCurrent = false) > slop
            if (!active) continue
            cam.dragging = true
            cam.touched = true
            val v = view()
            when {
                pressed >= 2 -> {
                    cam.zoomBy(zc)
                    if (v != null) cam.pan(pc.x, pc.y, v)
                }
                cam.free -> cam.orbit(pc.x / density, pc.y / density)
                v != null -> cam.pan(pc.x, pc.y, v)
            }
            e.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    } finally {
        cam.dragging = false
    }
}
