package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StopStep
import kotlin.math.ln
import kotlin.math.pow

/** 재생이 스스로 멈춘 까닭(토스트 · 단추 모양) */
internal sealed interface PlayEvent {
    /** M00 · M01(optional) — 그 줄 */
    data class Stop(val line: Int, val optional: Boolean) : PlayEvent

    /** 싱글 블록: 블록(사이클은 층 · 패스) 끝 */
    data object BlockEnd : PlayEvent

    /** 끝(프로그램 끝 또는 오류로 멈춘 자리) */
    data object End : PlayEvent

    /** 척에 닿음 — 재생이 그 자리에서 멈춘다(설계 5.5) */
    data object Chuck : PlayEvent
}

/**
 * 궤적 창 재생 시계(설계 7.4 · 8.6 `PlayState`). [t] = 프로그램 시각(초). 화면은 프레임마다 [advance] 에 실제 흐른 초와 속도 배수를 넘긴다
 * (Compose 프레임 시계 — 폰 · PC 같은 빠르기). 스스로 멈추는 곳: M00(늘) · M01(켜면) · 싱글 블록의 블록 끝 · [limit](끝 또는 척 닿음).
 * |◀ · ▶| 는 [SimRun.blockStart] · [SimRun.blockEnd](줄로 묶음 — 사이클은 사이클 전체)로 간다.
 */
@Stable
internal class PlayState(val run: SimRun) {
    var t by mutableDoubleStateOf(0.0)
        private set
    var playing by mutableStateOf(false)

    /** 재생이 멈추는 끝. 척에 닿으면 그 시각(소재 계산이 끝나면 화면이 넣는다) */
    var limit by mutableDoubleStateOf(run.totalSec)
        private set
    var chuckAt: Double? = null
        private set

    /** M00 · M01 걸음(시각 순) */
    private val stops: List<Pair<Double, StopStep>> = run.steps.filterIsInstance<StopStep>().map { it.t0 to it }
    private var stopsPassed = 0

    /** 싱글 블록이 멈추는 시각(블록 끝 · 사이클 층/패스 끝 — 같은 시각은 하나) */
    val bounds: DoubleArray = blockBounds(run)

    val atEnd: Boolean get() = t >= limit - 1e-9

    fun setChuck(at: Double?) {
        chuckAt = at
        limit = at ?: run.totalSec
        if (t > limit) seek(limit)
    }

    /** 실제 dt 초 · 배수 speed 만큼 앞으로. 스스로 멈추면 그 까닭 */
    fun advance(dt: Double, speed: Float, singleBlock: Boolean, optStop: Boolean): PlayEvent? {
        if (!playing) return null
        val from = t
        var to = from + dt * speed
        var ev: PlayEvent? = null
        var hit = -1
        for (k in stopsPassed until stops.size) {
            val (ts, st) = stops[k]
            if (ts > to) break
            if (!st.optional || optStop) {
                to = ts
                ev = PlayEvent.Stop(st.line, st.optional)
                hit = k
                break
            }
        }
        if (singleBlock) {
            val b = nextBound(from)
            if (b != null && b <= to && (ev == null || b < to)) {
                to = b
                ev = PlayEvent.BlockEnd
            }
        }
        if (to >= limit) {
            to = limit
            ev = if (chuckAt != null) PlayEvent.Chuck else PlayEvent.End
        }
        // 지난 정지: 멈춘 그 정지까지, 아니면 멈춘 시각까지의 꺼진 M01 만. 싱글 블록이 M00 앞의 블록 끝에서 먼저 멈추면
        // 그 M00 은 남겨 다음 재생에서 멈춘다(같은 프레임에 찾았다고 지나치지 않게)
        if (ev is PlayEvent.Stop) {
            stopsPassed = hit + 1
        } else {
            while (stopsPassed < stops.size && stops[stopsPassed].first <= to + 1e-9 && stops[stopsPassed].second.optional && !optStop) stopsPassed++
        }
        t = to
        if (ev != null) playing = false
        return ev
    }

    private fun nextBound(from: Double): Double? {
        var lo = 0
        var hi = bounds.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (bounds[mid] <= from + 1e-9) lo = mid + 1 else hi = mid
        }
        return bounds.getOrNull(lo)
    }

    /** 그 시각으로(재생 상태는 그대로). 그 앞의 M00 · M01 은 지난 것으로 */
    fun seek(to: Double) {
        t = to.coerceIn(0.0, limit)
        stopsPassed = stops.count { it.first < t - 1e-9 }
    }

    /** 재생 / 멈춤. 끝에서 누르면 처음부터 */
    fun toggle() {
        if (playing) {
            playing = false
            return
        }
        if (atEnd) seek(0.0)
        playing = true
    }

    fun reset() {
        playing = false
        seek(0.0)
    }

    private fun sameBlock(a: Int, b: Int) = run.steps[a].line == run.steps[b].line && run.steps[a].unit == run.steps[b].unit

    /** |◀ 지금 블록 처음으로(이미 처음이면 앞 블록) — 멈춤 */
    fun blockBack() {
        playing = false
        val n = run.steps.size
        if (n == 0) return seek(0.0)
        val i = run.stepAt(t).coerceIn(0, n - 1)
        var s = run.blockStart(i)
        if (t - s <= 1e-9) {
            var j = i
            while (j > 0 && sameBlock(j - 1, j)) j--
            s = if (j > 0) run.blockStart(j - 1) else 0.0
        }
        seek(s)
    }

    /** ▶| 지금 블록 끝까지(이미 끝이면 다음 블록 끝) — 멈춤 */
    fun blockForward() {
        playing = false
        val n = run.steps.size
        if (n == 0) return
        var i = run.stepAt(t).coerceIn(0, n - 1)
        var e = run.blockEnd(i)
        while (e <= t + 1e-9) {
            var j = i
            while (j < n - 1 && sameBlock(j, j + 1)) j++
            if (j >= n - 1) {
                e = limit
                break
            }
            i = j + 1
            e = run.blockEnd(i)
        }
        seek(minOf(e, limit))
    }

    /** 그 줄의 첫 블록 처음으로(코드 목록 누르기). 실행되지 않은 줄이면 false */
    fun goLine(line: Int): Boolean {
        val i = run.steps.indexOfFirst { it.line == line }
        if (i < 0) return false
        playing = false
        seek(run.blockStart(i))
        return true
    }

    companion object {
        /** 속도 배수(로그 미끄럼): 위치 0~1 ↔ ×1~×200 */
        const val MAX_SPEED = 200f
        val TICKS = listOf(1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f)

        fun speedAt(pos: Float): Float {
            val s = MAX_SPEED.toDouble().pow(pos.coerceIn(0f, 1f).toDouble()).toFloat()
            // 눈금 가까이(로그 폭 2.5 %)면 눈금 값으로
            TICKS.firstOrNull { kotlin.math.abs(posOf(it) - pos) < 0.025f }?.let { return it }
            return if (s < 10) kotlin.math.round(s * 10) / 10 else kotlin.math.round(s)
        }

        fun posOf(speed: Float): Float = (ln(speed.coerceIn(1f, MAX_SPEED).toDouble()) / ln(MAX_SPEED.toDouble())).toFloat()

        /** 블록 끝 시각: 줄 · 단위가 바뀌거나 사이클 이동의 층(pass)이 바뀌는 곳(설계 7.4 '싱글 블록') */
        fun blockBounds(run: SimRun): DoubleArray {
            val s = run.steps
            val out = ArrayList<Double>()
            var pass: Int? = null
            for (i in s.indices) {
                val st = s[i]
                if (st is MoveStep) pass = st.pass
                val last = i == s.size - 1
                val next = if (last) null else s[i + 1]
                val passChange = next is MoveStep && st is MoveStep && next.pass != pass && st.line == next.line
                if (last || next!!.line != st.line || next.unit != st.unit || passChange) {
                    if (out.isEmpty() || st.t1 > out.last() + 1e-9) out += st.t1
                }
            }
            return out.toDoubleArray()
        }
    }
}

/** 시간 글: mm:ss(한 시간 넘으면 h:mm:ss) — 초는 내림 */
internal fun timeText(sec: Double): String {
    val s = sec.coerceAtLeast(0.0).toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(ss)}" else "${two(m)}:${two(ss)}"
}

/** 이송 F 글: 100 이상은 정수, 아니면 소수 둘째 자리까지 채우고 셋째는 있으면(0.08 · 0.125 · 6.00) */
internal fun feedText(v: Double): String {
    if (v >= 100) return kotlin.math.round(v).toLong().toString()
    val s = (kotlin.math.round(v * 1000) / 1000).toString()
    val dot = s.indexOf('.')
    val dec = if (dot < 0) "" else s.substring(dot + 1)
    val whole = if (dot < 0) s else s.substring(0, dot)
    return "$whole." + dec.padEnd(2, '0')
}

/** 속도 칩 글: x200 · x2.5 */
internal fun speedText(v: Float): String = "x" + if (v >= 10 || v == kotlin.math.floor(v)) v.toInt().toString() else v.toString()
