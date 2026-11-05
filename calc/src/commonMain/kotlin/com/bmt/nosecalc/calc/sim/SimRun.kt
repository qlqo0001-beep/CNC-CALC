package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.angleOf
import kotlin.math.cos
import kotlin.math.sin

enum class MoveKind { RAPID, FEED, THREAD }

/**
 * 이동이 어디서 나왔나. 사이클 값은 고정 사이클 단계가 쓴다. G28_VIA · G28_HOME 은 G30(제2 원점)도.
 * SHIFT_START · SHIFT_CANCEL = 형식 C 의 이동 없는 G41/G42 · G40 블록에서 노즈를 보정값만큼 옮긴 이동(명령 경로는 그 자리 한 점 — [MachineSetup.compStartup]).
 * DIE_CUT = 다이스 헤드(DIE 공구)의 G01 — 이송(시간은 F)이지만 소재를 래스터로 깎지 않고 [MoveStep.die] 로 나사 모양을 남긴다(설계 19절)
 */
enum class StepSrc { BLOCK, CORNER, CYCLE_APPROACH, CYCLE_CUT, CYCLE_RETRACT, CYCLE_RETURN, CYCLE_FINISH, G28_VIA, G28_HOME, SHIFT_START, SHIFT_CANCEL, DIE_CUT }

/** 다이스 헤드 한 번 깎기의 나사(공구 설정에서 — 모형): 호칭 지름 [dia] · 피치 [pitch](리드) · 나사 각 [angleDeg](60 · 55) */
data class DieCut(val dia: Double, val pitch: Double, val angleDeg: Double)

enum class SpindleDir { STOP, CW, CCW }

/** 실행 결과의 한 걸음(시간 순). [line] = 편집기 줄, [unit] = 프로그램 단위, [t0] · [t1] = 초 */
sealed interface SimStep {
    val line: Int
    val unit: Int
    val t0: Double
    val t1: Double
}

/**
 * 이동 하나. [tip] = 실제 가상 인선 경로(보정 뒤 — 위치 표시 · 시간), [nose] = 노즈 중심 경로(= tip − 팁 벡터),
 * [cmd] = 보정 전 명령 경로 — 그 블록(또는 끼운 모서리)의 경로 전체라 꺾인 급송의 두 토막은 같은 [cmd] 를 가진다.
 * [feed] = mm/rev([perRev]) 또는 mm/min, 급송은 null. [css] = G96 Vc(m/min), [sCmd] = 마지막 S(G96 이면 Vc, G97 이면 rpm).
 * [rpm0] · [rpm1] = 처음 · 끝 회전수(주축 정지면 0, G96 급송은 끝점 X 로 정한 값 — S 3-82).
 * [die] = 다이스 헤드 깎기([StepSrc.DIE_CUT])의 나사 — 공구 설정의 d · P 가 비었으면 null(경로만).
 */
data class MoveStep(
    override val line: Int,
    override val unit: Int,
    override val t0: Double,
    override val t1: Double,
    val kind: MoveKind,
    val tip: Seg,
    val nose: Seg,
    val cmd: Seg?,
    val src: StepSrc,
    val pass: Int?,
    val model: Boolean,
    val tool: Int?,
    val offset: Int?,
    val r: Double,
    val tipNo: Int,
    val feed: Double?,
    val perRev: Boolean,
    val css: Double?,
    val sCmd: Double?,
    val rpm0: Double,
    val rpm1: Double,
    val die: DieCut? = null,
) : SimStep

data class DwellStep(override val line: Int, override val unit: Int, override val t0: Double, override val t1: Double, val sec: Double) : SimStep

/** T 실행. 공구 번호가 바뀌면 교환 시간(처음 T 는 0 — 그 전 공구를 모른다). [toTool] 0 = 공구대 그대로(T0 · T00) */
data class ToolStep(
    override val line: Int, override val unit: Int, override val t0: Double, override val t1: Double,
    val fromTool: Int?, val toTool: Int, val offset: Int,
) : SimStep

/** M00 · M01([optional]) — 재생이 멈추는 자리. 시간 0 */
data class StopStep(override val line: Int, override val unit: Int, override val t0: Double, override val t1: Double, val optional: Boolean) : SimStep

/**
 * 블록 끝의 주축 · 절삭유 · S · F 상태(바뀐 블록마다, 시간 0). [rpm] = G97 의 회전수(상한으로 자른 값, G96 이면 null),
 * [css] = G96 Vc, [feed] · [perRev] = 지금 F 와 G99.
 */
data class StateStep(
    override val line: Int, override val unit: Int, override val t0: Double, override val t1: Double,
    val spindle: SpindleDir, val rpm: Double?, val coolant: Boolean, val css: Double?, val sCmd: Double?, val feed: Double?, val perRev: Boolean,
) : SimStep

/** 끝: [m] = 2 · 30 · 99(주 프로그램의 M99) */
data class EndStep(override val line: Int, override val unit: Int, override val t0: Double, override val t1: Double, val m: Int) : SimStep

/**
 * 고스트(06 · 10): 실행한 블록 끝마다. [cmd] = 명령점(측정점), [tip] = 그 자리 실제 인선(보정 좌표), [nose] = 노즈 중심,
 * [step] = 그 자리에서 끝나는 [MoveStep] 번호.
 */
data class Ghost(val line: Int, val step: Int, val cmd: Pt, val tip: Pt, val nose: Pt, val r: Double, val tipNo: Int)

/** 재생 시각 [t] 의 모습 */
data class SimPose(
    val t: Double,
    val step: Int,
    val line: Int,
    val tip: Pt,
    val nose: Pt,
    val tool: Int?,
    val offset: Int?,
    val kind: MoveKind?,
    val rpm: Double,
    val sCmd: Double?,
    val css: Boolean,
    val feed: Double?,
    val perRev: Boolean,
)

/**
 * 실행 결과. [steps] = 시간 순(멈췄으면 그 앞까지), [issues] = 글 + 실행 문제(줄 순), [stop] = 실행을 멈춘 문제(없으면 끝까지),
 * [start] = 실행 시작 때 인선 자리(기계 원점), [offsetsUsed] = T 로 쓴 보정 번호(처음 쓴 순서, 0 빼고),
 * [compOffsets] = G41/G42 를 건 보정 번호, [extent] = 인선 이동이 닿은 상자(최소, 최대 — 원호 볼록한 데 포함).
 */
data class SimRun(
    val program: ParsedProgram,
    val steps: List<SimStep>,
    val issues: List<SimIssue>,
    val stop: SimIssue?,
    val totalSec: Double,
    val ghosts: List<Ghost>,
    val offsetsUsed: List<Int>,
    val compOffsets: Set<Int>,
    val extent: Pair<Pt, Pt>?,
    val start: Pt,
) {
    /** t 에 있는 걸음 — t0 ≤ t 인 마지막 걸음(시간 0 걸음이 이어지면 그 뒤 것). 걸음이 없으면 −1 */
    fun stepAt(t: Double): Int {
        if (steps.isEmpty()) return -1
        var lo = 0
        var hi = steps.size - 1
        if (t < steps[0].t0) return 0
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (steps[mid].t0 <= t) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** 그 걸음이 든 블록의 시작 시각(같은 줄 · 단위의 이어진 걸음 가운데 처음) — |◀ · 코드 목록 누르기 */
    fun blockStart(stepIndex: Int): Double {
        val s = steps[stepIndex]
        var i = stepIndex
        while (i > 0 && steps[i - 1].line == s.line && steps[i - 1].unit == s.unit) i--
        return steps[i].t0
    }

    /** 그 걸음이 든 블록의 끝 시각 — ▶| · 싱글 블록 */
    fun blockEnd(stepIndex: Int): Double {
        val s = steps[stepIndex]
        var i = stepIndex
        while (i < steps.size - 1 && steps[i + 1].line == s.line && steps[i + 1].unit == s.unit) i++
        return steps[i].t1
    }

    private val lastMove: IntArray by lazy {
        var k = -1
        IntArray(steps.size) { i -> if (steps[i] is MoveStep) k = i; k }
    }
    private val lastState: IntArray by lazy {
        var k = -1
        IntArray(steps.size) { i -> if (steps[i] is MoveStep || steps[i] is StateStep) k = i; k }
    }

    /** 재생 시각의 모습. 이동 안에서는 매개변수로 보간(원호는 각) — G96 이송의 실제 속도 변화는 그리지 않는다 */
    fun poseAt(t: Double): SimPose {
        val i = stepAt(t)
        if (i < 0) return SimPose(t, -1, 0, start, start, null, null, null, 0.0, null, false, null, true)
        val s = steps[i]
        val mi = lastMove[i]
        val mv = if (mi >= 0) steps[mi] as MoveStep else null
        val inMove = s is MoveStep
        val f = if (inMove && s.t1 > s.t0) ((t - s.t0) / (s.t1 - s.t0)).coerceIn(0.0, 1.0) else 1.0
        val tip = mv?.let { if (inMove) at(it.tip, f) else it.tip.end } ?: start
        val nose = mv?.let { if (inMove) at(it.nose, f) else it.nose.end } ?: start
        val si = lastState[i]
        return when (val st = if (si >= 0) steps[si] else null) {
            is MoveStep -> SimPose(
                t, i, s.line, tip, nose, mv?.tool, mv?.offset, if (inMove) st.kind else null,
                if (inMove) st.rpm0 + (st.rpm1 - st.rpm0) * f else st.rpm1, st.sCmd, st.css != null, st.feed, st.perRev,
            )
            is StateStep -> SimPose(
                t, i, s.line, tip, nose, mv?.tool, mv?.offset, null,
                if (st.spindle == SpindleDir.STOP) 0.0 else st.rpm ?: mv?.rpm1 ?: 0.0, st.sCmd, st.css != null, st.feed, st.perRev,
            )
            else -> SimPose(t, i, s.line, tip, nose, mv?.tool, mv?.offset, null, 0.0, null, false, null, true)
        }
    }

    companion object {
        /** 토막 위 매개변수 f(0~1)의 점 — 원호는 각으로(궤적 창이 원호를 꺾은선으로 그릴 때도 쓴다) */
        fun at(s: Seg, f: Double): Pt = when (s) {
            is Line -> s.start + (s.end - s.start) * f
            is Arc -> {
                val a = angleOf(s.start - s.center) + (if (s.cw) -1 else 1) * s.sweepRad() * f
                s.center + Pt(cos(a), sin(a)) * s.radius
            }
        }
    }
}
