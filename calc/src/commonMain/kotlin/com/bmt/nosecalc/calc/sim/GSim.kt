package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.angleOf
import com.bmt.nosecalc.calc.wrap2Pi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * G코드 실행기(`docs/simulator-design.md` 1~4절). 글 → 시간 순 이동 목록 · 고스트 · 문제 · 사이클 타임.
 * 예외를 던지지 않는다 — 실행을 멈추는 문제는 [SimRun.stop] 으로, 그 앞까지의 걸음은 그대로 둔다.
 *
 * 파서 · 모달 · G00~G03 · `,C` `,R` · G04 · G28 · G30 · G50 S · G96/G97 · G98/G99 · M · T · S · F ·
 * G40/G41/G42(형식 A 모형, [GComp] — 이동 없는 시작 · 취소는 기계 설정 [MachineSetup.compStartup] 의 형식 A · B / C) · M98/M99 · 시간,
 * 고정 사이클 G90/G92/G94 · G70~G76(FANUC 2줄 형식 — 기하는 [GCycles], 블록 읽기 · 모달 · P~Q 는 여기 — 설계 문서 1.6 · 12절).
 */
object GSim {
    /** 실행 블록 수 한도(M99 P · M98 L 고리) */
    const val EXEC_LIMIT = 200_000

    /** G90/G92/G94 모드의 축 없는 M 블록에서 멈추는 M — 사이클과의 순서가 결과를 바꾼다(주축 · 끝 · 부프로그램, 설계 13.1) */
    internal val REPEAT_STOP_M = setOf(2, 3, 4, 5, 30, 98, 99)

    /** 부프로그램 겹침 한도(S 4-131: 4단) */
    const val SUB_NEST = 4

    fun run(input: SimInput): SimRun = Runner(input).run()
}

private class StopSim(val issue: SimIssue, val fromParse: Boolean = false) : RuntimeException()

/** 보정 상태에서 이 이동의 자리(3.2): 끔 · 시작 블록 · 보정 중 · 취소 블록 */
private enum class CompMode { OFF, START, ON, CANCEL }

/** 블록을 실행한 때의 상태(이동을 늦게 내보내도 그때 값으로 시간을 잰다) */
private data class Ctx(
    val tool: Int?,
    val offset: Int?,
    val r: Double,
    val tipNo: Int,
    val tv: Pt,
    val feed: Double?,
    val perRev: Boolean,
    val css: Double?,
    val sCmd: Double?,
    val nMax: Double,
    val spindle: SpindleDir,
)

private data class CornerReq(val chamfer: Boolean, val size: Double, val word: Word)

/** 명령 경로 한 토막(보정 전) */
private data class Mv(
    val seg: Seg,
    val kind: MoveKind,
    val line: Int,
    val unit: Int,
    val src: StepSrc,
    val ctx: Ctx,
    val mode: CompMode,
    val gc: GComp?,
    val corner: CornerReq?,
    val ghost: Boolean = true,
    /** 사이클 층 · 패스 번호와 모형 이동인지(MoveStep.pass · model) */
    val pass: Int? = null,
    val model: Boolean = false,
    /** MoveStep.cmd 로 보일 명령 경로(null = [seg]) — G71 여유 패스가 보정 전 윤곽을 보이려고 */
    val cmdSeg: Seg? = null,
    /** 급송을 기계 설정과 상관없이 축마다 따로(꺾인 경로) — G70 의 시작점 복귀(FF 4.2.4 주) */
    val dogleg: Boolean = false,
    /** 이동 없는 G40 뒤 첫 직선 이동: 노즈가 이 자리(끝 법선)에서 끝점 − 팁 벡터로(null = 보통 G40 상태 이동) */
    val noseFrom: Pt? = null,
    /** 이 이동 뒤에 내보낼 걸음(M · 상태) — 이동을 늦게 내보내도 시간 순을 지킨다. copy 는 같은 목록을 나눠 쓴다 */
    val after: MutableList<() -> Unit> = mutableListOf(),
)

/** G90/G92/G94 모달 값(S 4-91 · 4-93: 다음 블록에 X(G94 는 Z)만 써도 같은 사이클). x 반경 · 절대 */
private data class Cyc(val g: Int, val x: Double, val z: Double, val taper: Double, val lead: Double?, val pass: Int)

/** 사이클 동안만 쓰는 F · S(설계 1.6: 사이클 뒤 F · S · G96/97 · G98/99 는 부르기 전 값으로) */
private class FS(val f: Double?, val s: Double?, val css: Boolean, val perRev: Boolean)

/** M98 호출 쌓임: 돌아갈 단위 · 블록, 남은 반복 */
private class Frame(val unit: Int, val ret: Int, var left: Int)

/**
 * 형식 C 의 이동 없는 시작 블록(설계 3.2): 지령점 [at] 에서 노즈를 P − t → P + r·n₁ 로 옮기는데 n₁ 은 다음 이동 블록의 법선이라
 * 그 블록이 올 때까지 미룬다. [kind] = 그 블록의 모달(G00 급송 · G01 이송), [ctx] = 그 블록 때 상태, [after] = 그 사이 블록의 M · 상태(시간 순을 지킨다)
 */
private class CStart(val line: Int, val unit: Int, val at: Pt, val kind: MoveKind, val ctx: Ctx) {
    val after: MutableList<() -> Unit> = mutableListOf()
}

private sealed interface Flow
private object Next : Flow
private object End : Flow
private class Jump(val unit: Int, val idx: Int) : Flow

private class Runner(val input: SimInput) {
    val m = input.machine
    val prog = GText.parse(input.text, m.decimal)
    val issues = mutableListOf<SimIssue>()
    val steps = ArrayList<SimStep>()
    val ghosts = ArrayList<Ghost>()
    var t = 0.0
    var lastMoveIdx = -1

    // ---- 모달(2.2) — 처음 상태 H 29: G00 G18 G40 G54 G80 G97 G99 (+G21)
    var motion: Int? = 0
    var g4x: Side? = null
    var css = false
    var perRev = true
    var s: Double? = null
    var f: Double? = null
    var clamp: Double? = null
    var spindle = SpindleDir.STOP
    var coolant = false
    var tool: Int? = null
    var offset = 0
    var cmd: Pt = m.home

    // ---- 보정 — gc 는 블록을 실행한 때의 보정 세션(시작 블록에서 만들고 취소 블록에서 비움)
    var gc: GComp? = null
    var noMoveCancelLine: Int? = null

    /** 이동 없이 끈 보정의 노즈 자리(마지막 보정 블록 끝 법선) — 다음 직선 이동이 여기서 시작한다(FF 5.4.4 그림 5.4.4(e)) */
    var noMoveNose: Pt? = null

    /** 형식 C 기계(이동 없는 시작 · 취소 블록에서 옮김 — [MachineSetup.compStartup]) */
    val typeC = m.compStartup == CompStartup.C

    /** 형식 C 의 이동 없는 시작 — 다음 이동 블록을 기다림([CStart]) */
    var cStart: CStart? = null
    var idle = 0
    var noOffsetWarned = false

    // ---- 내보내기 단계: 모서리(held) → 보정(pend) → 걸음
    var held: Mv? = null
    var pend: Mv? = null
    var pendStart = Pt(0.0, 0.0)
    var pendStartup = false

    // ---- 고정 사이클(1.6): G90/G92/G94 모달 값 · 첫 줄 모달 값(S 4-102 등 'This value is modal') — 한 번도 안 주면 CYCLE_FIRST_LINE
    var cyc: Cyc? = null
    var g71d: Double? = null
    var g71e: Double? = null
    var g72d: Double? = null
    var g72e: Double? = null
    var g73i: Double? = null
    var g73k: Double? = null
    var g73n: Int? = null
    var g74e: Double? = null
    var g75e: Double? = null
    var g76m: Int? = null
    var g76r: Int? = null
    var g76a: Int? = null
    var g76min: Double? = null
    var g76d: Double? = null

    /**
     * 나사 끝 빼기 켬(M23) · 끔(M24) 모달 — 흔한 FANUC 계열 기계 M 정의(H 111, 기계마다 다를 수 있음). 처음은 켬: M23/M24 가 없는 글은 전처럼
     * G92 는 설정 [MachineSetup.g92ChamferLeads], G76 은 첫 줄 r 로 뺀다. 끄면 둘 다 끝 빼기 0
     */
    var threadPullOut = true

    /** 마른 실행(G71~G73 이 P~Q 윤곽을 보정 모형으로 미리 돌림): null 이 아니면 걸음 대신 여기에 담는다 */
    var dry: MutableList<GCycles.Piece>? = null

    var clampWarned = false
    val offsetsUsed = LinkedHashSet<Int>()
    val compOffsets = LinkedHashSet<Int>()
    var execCount = 0
    var lastLine = 0

    fun run(): SimRun {
        var stop: SimIssue? = null
        try {
            exec()
        } catch (e: StopSim) {
            stop = e.issue
            if (!e.fromParse) issues += e.issue
            held = null
            pend = null
            cStart = null
        }
        // G71~G73 의 마른 실행과 G70 이 같은 P~Q 에서 같은 문제(COMP_TYPE_A 등)를 내면 하나만
        val all = (prog.issues + issues).distinct().sortedBy { it.line }
        return SimRun(prog, steps, all, stop, t, ghosts, offsetsUsed.toList(), compOffsets.toSet(), extent(), m.home)
    }

    // ================================================================ 블록 순서

    private fun exec() {
        var ui = 0
        var bi = 0
        val stack = ArrayList<Frame>()
        while (true) {
            val u = prog.units[ui]
            if (bi >= u.blocks.size) {
                if (stack.isEmpty()) {
                    finishOpen()
                    issues += SimIssue(IssueCode.NO_END, lastLine)
                    return
                }
                fail(IssueCode.SUB_NO_M99, u.blocks.lastOrNull()?.line ?: u.firstLine, args = listOf("O" + (u.number ?: 0)))
            }
            val b = u.blocks[bi]
            prog.stopOf(b)?.let { throw StopSim(it, fromParse = true) }
            // 낱말 없는 블록(`;` 만 · 주석만 · N 만)은 건너뛴다 — 단 G90/G92/G94 모달 값이 있으면 FANUC 은 그 블록에서도
            // 사이클을 한 번 더 돈다(FF 4.1.6 '- Block in which no move command is specified' · F0 13.1 주 2(1))
            if ((b.skip && m.blockSkip) || (b.words.isEmpty() && cyc == null)) {
                bi++
                continue
            }
            if (++execCount > GSim.EXEC_LIMIT) fail(IssueCode.EXEC_LIMIT, b.line)
            lastLine = b.line
            when (val fl = block(b, ui, bi, stack)) {
                Next -> bi++
                End -> return
                is Jump -> {
                    ui = fl.unit
                    bi = fl.idx
                }
            }
        }
    }

    // ================================================================ 블록 하나

    private fun block(b: Block, ui: Int, bi: Int, stack: MutableList<Frame>): Flow {
        val line = b.line
        val gs = b.gs
        val g0 = gs.firstOrNull { GText.group(it) == 0 }
        val gMotion = gs.lastOrNull { GText.group(it) == 1 }
        val mot = gMotion ?: motion
        val axes = b.has("X") || b.has("Z") || b.has("U") || b.has("W")
        val mWord = b.w("M")
        val mCode = mWord?.value?.toInt()

        // ,C/,R 블록 다음은 G01~G03 이동이어야 한다(사이에 다른 블록 — G04 포함 — 도 안 됨: 드웰 자리 출처 없음)
        held?.let { h ->
            if (g0 != null || !axes || mot !in 1..3) fail(IssueCode.CORNER_NO_NEXT, h.line, h.corner!!.word, listOf(line.toString()))
        }
        if (motion == null && gMotion == null && axes && g0 == null) fail(IssueCode.CYCLE_MOTION_MISSING, line)
        // G90/G92/G94 모드 중 한 번 G 는 받지 않는다(FF 4.1.6: G04 밖의 한 번 G 는 공통 모달 값을 지우고 모드는 남긴다 — 그 뒤를 그리지 않고 G00~G03 으로 먼저 끝내게)
        if (isSingle(mot) && g0 != null && g0 != 4) fail(IssueCode.CYCLE_ONESHOT, line, gWord(b, g0), listOf("G$mot"))
        if (g0 == 4) return dwell(b)
        if (g0 != null && g0 in 70..76) return fixedCycle(b, g0, ui, bi, stack)
        // 모달 값이 있으면 축 없는 블록도 사이클(FF 4.1.6 · F0 13.1 주 2). 값이 없으면(모드에 막 들어온 `G90 F0.2` 같은 줄) 사이클을 돌지 않는다(FF 4.1.6)
        if (isSingle(mot) && (axes || cyc != null)) return singleCycle(b, mot!!, gMotion)
        val moveBlock = g0 == null && (axes || (mot in 2..3 && (b.has("I") || b.has("K"))))
        checkWords(b, gs, g0, mot, mCode, moveBlock)

        val before = listOf(spindle, coolant, css, s, f, perRev, clamp)
        // 모달 G
        for ((g, w) in b.words.filter { it.key == "G" }.map { it.value.toInt() to it }) when (g) {
            96 -> if (!css) {
                css = true
                if (!b.has("S")) s = null // 단위가 바뀐 S 를 그대로 쓰지 않는다(출처 없음 — 쓸 때 S_MISSING)
            }
            97 -> if (css) {
                css = false
                if (!b.has("S")) s = null
            }
            98 -> if (perRev) {
                perRev = false
                if (!b.has("F")) f = null // 단위가 바뀐 F 를 그대로 쓰지 않는다(출처 없음 — 쓸 때 FEED_MISSING)
            }
            99 -> if (!perRev) {
                perRev = true
                if (!b.has("F")) f = null
            }
            80 -> if (motion == 90 || motion == 92 || motion == 94) fail(IssueCode.G80_IN_CYCLE, line, w)
            40 -> g4x = null
            41 -> g4x = Side.G41
            42 -> g4x = Side.G42
        }
        if (gMotion != null && gMotion != motion && isSingle(motion)) leaveSingle()
        if (gMotion != null) motion = gMotion

        // F(H 50 · S 1-14)
        b.w("F")?.let { w ->
            if (perRev && !w.point) fail(IssueCode.NO_POINT_FEED, line, w)
            if (w.value <= 0) fail(IssueCode.BAD_VALUE, line, w)
            f = w.value
        }
        // S · G50 S(H 49)
        val sW = b.w("S")
        if (g0 == 50) {
            if (sW == null) fail(IssueCode.WORD_MISSING, line, args = listOf("S"))
            if (sW.value <= 0) fail(IssueCode.BAD_VALUE, line, sW)
            clamp = sW.value
        } else if (sW != null) {
            s = sW.value
            if (!css && sW.value > nMax()) issues += SimIssue(IssueCode.RPM_OVER_LIMIT, line, sW.cols, sW.text, listOf(Fmt.nc(nMax())))
        }
        // T(H 65 · S 3-78) — 이동 앞
        val tW = b.w("T")
        if (tW != null) {
            if (moveBlock && mot in 1..3) fail(IssueCode.T_WITH_FEED, line, tW)
            val (nt, no) = parseT(tW.raw)
            val newTool = nt ?: tool
            if (gc != null && no != 0 && (no != offset || newTool != tool)) fail(IssueCode.COMP_TOOL_CHANGE, line, tW)
            val from = tool
            val change = from != null && newTool != null && newTool != from
            val sec = if (change) m.toolChangeSec else 0.0
            queue { t0 -> ToolStep(line, b.unit, t0, t0 + sec, from, newTool ?: 0, no) }
            tool = newTool
            offset = no
            if (no != 0) offsetsUsed += no
        }

        val wantOn = g4x != null && offset != 0
        gc?.let { if (wantOn && g4x != it.side) gc = switchSide(it, b) }

        var moved = false
        when {
            g0 == 28 || g0 == 30 -> {
                // FF 5.4.3 '- Command canceling the offset vector temporarily': G28 · G30 앞에 G40 으로 끈다(보정 중이면 벡터가 잠시 사라짐 — 그 경로는 그리지 않음)
                if (gc != null || wantOn) fail(IssueCode.COMP_AT_G28, line, b.words.first { it.key == "G" && it.value.toInt() == g0 })
                moved = g28(b, g0)
            }
            moveBlock -> moved = move(b, mot!!, wantOn)
            else -> moved = compNoMove(line, b.unit, wantOn)
        }
        if (g4x != null && offset == 0 && moved && !noOffsetWarned) {
            noOffsetWarned = true
            issues += SimIssue(IssueCode.COMP_NO_OFFSET, line)
        }
        countIdle(line, moved, mCode)

        // M — 블록 이동이 끝난 뒤(H 133)
        var flow: Flow = Next
        var end: Int? = null
        when (mCode) {
            null -> {}
            0, 1 -> queue { t0 -> StopStep(line, b.unit, t0, t0, mCode == 1) }
            2, 30 -> end = mCode
            3 -> spindle = SpindleDir.CW
            4 -> spindle = SpindleDir.CCW
            5 -> spindle = SpindleDir.STOP
            8 -> coolant = true
            9 -> coolant = false
            23 -> threadPullOut = true
            24 -> threadPullOut = false
            98 -> flow = call(b, ui, bi, stack)
            99 -> {
                if (stack.isEmpty()) {
                    issues += SimIssue(IssueCode.M99_MAIN, line, mWord.cols, mWord.text)
                    end = 99
                } else flow = ret(b, ui, stack)
            }
            else -> issues += SimIssue(IssueCode.MACHINE_M, line, mWord.cols, mWord.text)
        }
        if (listOf(spindle, coolant, css, s, f, perRev, clamp) != before) stateStep(line, b.unit)
        val endM = end
        if (endM != null) {
            finishOpen()
            queue { t0 -> EndStep(line, b.unit, t0, t0, endM) }
            return End
        }
        return flow
    }

    // ================================================================ 고정 사이클(1.6 · 12절) — 기하는 GCycles

    private fun isSingle(g: Int?) = g == 90 || g == 92 || g == 94

    private fun gWord(b: Block, g: Int): Word? = b.words.firstOrNull { it.key == "G" && it.value.toInt() == g }

    /** G90/G92/G94 모드를 떠남(01 그룹 다른 G). G92 의 F 는 리드라 이송으로 남기지 않는다(FANUC 은 F 가 하나라 리드가 이송이 됨 — 출처 없어 비운다) */
    private fun leaveSingle() {
        if (cyc?.g == 92) f = null
        cyc = null
    }

    /**
     * G90 · G92 · G94 블록(S 4-91~4-100, H 127~132). 받는 낱말: 그 G · X Z U W · R(G90/G92 는 I, G94 는 K 도 — H 127 · 131) · F.
     * 축과 S · T · M(G96~G99) 이 같이 있으면 CYCLE_BLOCK_TM(S 4-93 'X T0505 M05 ; Error'). 보정 중이면 COMP_IN_CYCLE(H 73 — 그 경로가 출처에 없다).
     * X · Z · R 은 모달(S 4-93 · 4-96 · 4-99: 다음 블록에 X(G94 는 Z)만). 처음 블록은 X(U) · Z(W) 둘 다.
     * 축 없는 블록(모달 값이 있을 때 — EOB 만 · 주석만 · F 만 · R 만 …)도 같은 사이클을 한 번 더 돈다(FF 4.1.6 · F0 13.1 주 2(1)).
     * 축 없이 S · T · M(G96~G99) 이 있으면 FANUC 은 사이클과 그 기능을 '함께' 실행한다(F0 주 2(3)). 순서가 결과를 바꾸는 S · T · 주축(M03~M05) ·
     * 끝(M02 · M30) · M98/M99 · G96~G99 는 CYCLE_REPEAT_MST, 그 밖의 M(기계 M · M00 · M01 · M08 · M09 · M23 · M24)은 사이클을 한 번 더 돈 뒤 M 을 실행한다([cycleM]).
     */
    private fun singleCycle(b: Block, g: Int, gMotion: Int?): Flow {
        val line = b.line
        val taperKey = if (g == 94) "K" else "I"
        val axes = b.has("X") || b.has("Z") || b.has("U") || b.has("W")
        fun mst(w: Word): Nothing =
            if (axes) fail(IssueCode.CYCLE_BLOCK_TM, line, w) else fail(IssueCode.CYCLE_REPEAT_MST, line, w, listOf("G$g"))
        for (w in b.words) when (w.key) {
            "X", "Z", "U", "W", "R", "F", taperKey -> {}
            "S", "T" -> mst(w)
            "M" -> if (axes || w.value.toInt() in GSim.REPEAT_STOP_M) mst(w)
            "G" -> when (w.value.toInt()) {
                90, 92, 94, 40, 41, 42, 21, 54 -> {}
                // FF 4.1.6 '- Plane selection command': 모드 중 평면 G 는 사이클 공통 모달 값을 지운다 — 한 번 G 와 같이 막는다
                18 -> if (cyc?.g == g) fail(IssueCode.CYCLE_ONESHOT, line, w, listOf("G$g"))
                80 -> fail(IssueCode.G80_IN_CYCLE, line, w)
                96, 97, 98, 99 -> mst(w)
                else -> fail(IssueCode.WORD_NOT_USED, line, w)
            }
            else -> fail(IssueCode.WORD_NOT_USED, line, w)
        }
        if (b.has("R") && b.has(taperKey)) fail(IssueCode.WORD_NOT_USED, line, b.w(taperKey))
        for (w in b.words) if (w.key == "G") when (w.value.toInt()) {
            40 -> g4x = null
            41 -> g4x = Side.G41
            42 -> g4x = Side.G42
        }
        if (gc != null || g4x != null) fail(IssueCode.COMP_IN_CYCLE, line, gWord(b, g) ?: b.words.firstOrNull { it.key == "G" && it.value.toInt() in 40..42 })
        noMoveCancelLine?.let { fail(IssueCode.COMP_CANCEL_NO_MOVE, line, args = listOf(it.toString())) }
        if (gMotion != null && gMotion != motion) leaveSingle()
        motion = g
        val prev = cyc?.takeIf { it.g == g }
        val a = cmd
        val tx = b.w("X")?.let { it.value / 2 } ?: b.w("U")?.let { a.x + it.value / 2 } ?: prev?.x
            ?: fail(IssueCode.WORD_MISSING, line, args = listOf("X"))
        val tz = b.w("Z")?.value ?: b.w("W")?.let { a.z + it.value } ?: prev?.z
            ?: fail(IssueCode.WORD_MISSING, line, args = listOf("Z"))
        val taper = (b.w("R") ?: b.w(taperKey))?.value ?: prev?.taper ?: 0.0
        var lead = prev?.lead
        val c: Ctx
        if (g == 92) {
            b.w("F")?.let { w -> lead = leadOf(w, line) }
            val ld = lead ?: fail(IssueCode.WORD_MISSING, line, args = listOf("F"))
            threadSpindle(line)
            c = ctx().copy(feed = ld, perRev = true)
        } else {
            b.w("F")?.let { w ->
                if (perRev && !w.point) fail(IssueCode.NO_POINT_FEED, line, w)
                if (w.value <= 0) fail(IssueCode.BAD_VALUE, line, w)
                f = w.value
            }
            checkFeed(line)
            c = ctx()
        }
        val pass = (prev?.pass ?: 0) + 1
        val chamfer = if (g == 92 && threadPullOut) m.g92ChamferLeads * lead!! else 0.0
        emitCycle(GCycles.single(g, a, Pt(tz, tx), taper, chamfer, pass), line, b.unit, c)
        cyc = Cyc(g, tx, tz, taper, lead, pass)
        cmd = a
        b.w("M")?.let { cycleM(b, it) }
        return Next
    }

    /**
     * 사이클 모드의 M 만 블록(FF 4.1.6): 사이클을 한 번 더 돈 뒤 M — 정지 · 절삭유 · 나사 끝 빼기 · 기계 M 만 온다([singleCycle] 이 나머지를 막았다).
     * 나사 끝의 `M24` 줄은 마지막 X 를 끝 빼기 켠 채 한 번 더 깎은 뒤 끈다
     */
    private fun cycleM(b: Block, w: Word) {
        val before = coolant
        when (val mc = w.value.toInt()) {
            0, 1 -> queue { t0 -> StopStep(b.line, b.unit, t0, t0, mc == 1) }
            8 -> coolant = true
            9 -> coolant = false
            23 -> threadPullOut = true
            24 -> threadPullOut = false
            else -> issues += SimIssue(IssueCode.MACHINE_M, b.line, w.cols, w.text)
        }
        if (coolant != before) stateStep(b.line, b.unit)
    }

    /** G70~G76 블록. 첫 줄(G71~G73 은 P · Q 없음, G74~G76 은 축 없음 — S 4-103 주 1 · 4-112 주 1 · 4-116 주 1)은 모달 값만 */
    private fun fixedCycle(b: Block, g: Int, ui: Int, bi: Int, stack: MutableList<Frame>): Flow {
        val line = b.line
        b.words.firstOrNull { it.key == "G" && it.value.toInt() != g }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
        val second = when (g) {
            70 -> true
            71, 72, 73 -> b.has("P") || b.has("Q")
            else -> b.has("X") || b.has("Z") || b.has("U") || b.has("W")
        }
        if (!second) {
            firstLine(b, g)
            countIdle(line, false, null)
            return Next
        }
        b.w("T")?.let { fail(IssueCode.T_WITH_FEED, line, it) } // S 2-24: T 블록은 G00
        b.w("M")?.let { fail(IssueCode.CYCLE_BLOCK_TM, line, it) } // 사이클 앞 · 뒤 어디서 도는지 출처 없음
        // H 73 · H 77: 보정 중에 사이클을 부르지 않는다(G70~G73 의 보정은 P 블록에서)
        if (gc != null || g4x != null) fail(IssueCode.COMP_IN_CYCLE, line, gWord(b, g))
        noMoveCancelLine?.let { fail(IssueCode.COMP_CANCEL_NO_MOVE, line, args = listOf(it.toString())) }
        return when (g) {
            70 -> g70(b, ui, stack)
            71, 72, 73 -> g7173(b, g, ui, bi)
            74, 75 -> g7475(b, g)
            else -> g76(b)
        }
    }

    /** 첫 줄 모달 값(S 4-102 · 4-105 · 4-106 · 4-112 · 4-113 · 4-115). G73 의 R = 패스 수(정수), G76 의 P = mm rr aa(2자리씩) · Q = µm */
    private fun firstLine(b: Block, g: Int) {
        val line = b.line
        val allowed = when (g) {
            71 -> setOf("G", "U", "R")
            72 -> setOf("G", "W", "R")
            73 -> setOf("G", "U", "W", "R")
            74, 75 -> setOf("G", "R")
            else -> setOf("G", "P", "Q", "R")
        }
        b.words.firstOrNull { it.key !in allowed }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
        if (b.words.none { it.key != "G" }) fail(IssueCode.WORD_MISSING, line, args = listOf(allowed.first { it != "G" }))
        fun pos(w: Word?): Double? = w?.let { if (it.value <= 0) fail(IssueCode.BAD_VALUE, line, it); it.value }
        fun nonNeg(w: Word?): Double? = w?.let { if (it.value < 0) fail(IssueCode.BAD_VALUE, line, it); it.value }
        when (g) {
            71 -> {
                pos(b.w("U"))?.let { g71d = it }
                nonNeg(b.w("R"))?.let { g71e = it }
            }
            72 -> {
                pos(b.w("W"))?.let { g72d = it }
                nonNeg(b.w("R"))?.let { g72e = it }
            }
            73 -> {
                b.w("U")?.let { g73i = it.value }
                b.w("W")?.let { g73k = it.value }
                b.w("R")?.let {
                    // FF 4.2.3 주: d 는 소수점을 받되 반올림한 정수를 패스 수로(`R3.0` = 3 — FF 그림 4.2.3(b) O0001)
                    val n = floor(it.value + 0.5).toInt()
                    // 첫 패스 자리를 출처마다 다르게 적어(H 98 · S 4-106) 2 패스 이상만
                    if (n < 2) fail(IssueCode.G73_COUNT, line, it)
                    g73n = n
                }
            }
            74 -> nonNeg(b.w("R"))?.let { g74e = it }
            75 -> nonNeg(b.w("R"))?.let { g75e = it }
            else -> {
                b.w("P")?.let {
                    if (it.raw.trimStart('+').length > 6) fail(IssueCode.BAD_VALUE, line, it)
                    val v = it.value.toLong()
                    val mm = (v / 10000).toInt()
                    if (mm < 1) fail(IssueCode.BAD_VALUE, line, it) // 다듬질 0 번이면 여유 d 가 남는다
                    g76m = mm
                    g76r = ((v / 100) % 100).toInt()
                    g76a = (v % 100).toInt()
                }
                b.w("Q")?.let { g76min = it.value / 1000 }
                nonNeg(b.w("R"))?.let { g76d = it }
            }
        }
    }

    /**
     * G70(H 82, S 4-108): 지금 자리를 시작점으로 → P~Q 를 보통 블록처럼(F · S · T · G41/G42 · `,C` `,R` 모두 — S 4-108 주 1) →
     * Q 블록 뒤 급송으로 시작점 — 기계 설정과 상관없이 늘 축마다 따로(FF 4.2.4 '- Return to the cycle start point' 주: LRP 와 상관없이 비직선) →
     * G70 블록 다음 블록(FF 4.2.4 · F0 13.2.4 주 2 · H 82 — P~Q 가 바로 뒤에 있으면 그 블록들을 보통 블록으로 다시 실행한다).
     * P 블록은 01 그룹 G 를 써야 한다(사이클 앞 모달을 이어 쓰지 않음 — 우리가 정함).
     */
    private fun g70(b: Block, ui: Int, stack: MutableList<Frame>): Flow {
        val line = b.line
        b.words.firstOrNull { it.key !in setOf("G", "P", "Q") }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
        val (p, q) = findPQ(b, ui, allowSame = true)
        scanPQ(ui, p, q, 70)
        val start = cmd
        val sv = saveFS()
        motion = null
        val blocks = prog.units[ui].blocks
        for (idx in p..q) {
            val pb = blocks[idx]
            if ((pb.skip && m.blockSkip) || pb.words.isEmpty()) continue
            if (++execCount > GSim.EXEC_LIMIT) fail(IssueCode.EXEC_LIMIT, pb.line)
            lastLine = pb.line
            block(pb, ui, idx, stack) // M98 · M99 · M02 · M30 · 사이클은 scanPQ 가 막았다 — 늘 Next
        }
        held?.let { fail(IssueCode.CORNER_NO_NEXT, it.line, it.corner!!.word) } // S 4-117 주 4
        if (gc != null || g4x != null) fail(IssueCode.CYCLE_COMP_OPEN, blocks[q].line)
        noMoveCancelLine?.let { fail(IssueCode.COMP_CANCEL_NO_MOVE, line, args = listOf(it.toString())) }
        if ((start - cmd).length > 1e-9) {
            pushMove(Mv(Line(cmd, start), MoveKind.RAPID, line, b.unit, StepSrc.CYCLE_RETURN, ctx(), CompMode.OFF, null, null, ghost = false, dogleg = true))
        }
        cmd = start
        restoreFS(sv, line, b.unit)
        motion = null
        return Next
    }

    /**
     * G71 · G72 · G73 둘째 줄(S 4-102~4-107, H 78 · 92 · 98). P~Q 의 F · S · T · M 은 쓰지 않고 이 줄(또는 그때 모달)의 것(S 4-103 · FF 4.2.1 주 3).
     * 윤곽 = P~Q 를 A 에서 보정 모형으로 미리 돌린 실제 인선 경로(P~Q 안 G41/G42 — H 77 · 81 예 O00082 — 설계 3.4).
     * 끝나면 Q 블록 다음 블록(FF 4.2.1 '- Operations') — P~Q 가 사이클 블록 앞이면 되돌아와 되풀이하므로 CYCLE_PQ_BEFORE.
     */
    private fun g7173(b: Block, g: Int, ui: Int, bi: Int): Flow {
        val line = b.line
        val gw = gWord(b, g)
        b.words.firstOrNull { it.key !in setOf("G", "P", "Q", "U", "W", "F", "S") }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
        val haveFirst = when (g) {
            71 -> g71d != null && g71e != null
            72 -> g72d != null && g72e != null
            else -> g73i != null && g73k != null && g73n != null
        }
        if (!haveFirst) fail(IssueCode.CYCLE_FIRST_LINE, line, gw, listOf("G$g"))
        val (p, q) = findPQ(b, ui, allowSame = false)
        scanPQ(ui, p, q, g)
        if (q < bi) fail(IssueCode.CYCLE_PQ_BEFORE, line, b.w("P"), listOf("G$g"))
        val pRapid = pBlock(prog.units[ui].blocks[p], g)
        val du = b.w("U")?.value ?: 0.0
        val dw = b.w("W")?.value ?: 0.0
        val sv = saveFS()
        cycleFS(b, allowS = true)
        checkFeed(line)
        val a = cmd
        val c = ctx()
        val (pEnd, contour) = pqTip(ui, p, q)
        if (contour.isEmpty()) fail(IssueCode.CYCLE_PQ_ORDER, line, b.w("Q"))
        val off = Pt(dw, du / 2)
        val moves = when (g) {
            73 -> GCycles.pattern(a, pEnd, contour, g73i!!, g73k!!, g73n!!, off, pRapid)
            else -> {
                val d = if (g == 71) g71d!! else g72d!!
                val e = if (g == 71) g71e!! else g72e!!
                when (val r = GCycles.rough(a, pEnd, contour, d, e, off, g == 72, pRapid)) {
                    is GCycles.Bad -> fail(r.code, r.line ?: line, if (r.line == null) b.w(r.args.firstOrNull() ?: "") else null, r.args)
                    is GCycles.Ok -> r.moves
                }
            }
        }
        emitCycle(moves, line, b.unit, c)
        cmd = a
        restoreFS(sv, line, b.unit)
        motion = null
        return Jump(ui, q + 1)
    }

    /**
     * P 블록(S 4-103 · 4-106, H 80 · 98): G00 또는 G01. G71 은 X(U)만 · G72 는 Z(W)만(형식 I — 둘 다면 형식 II 로 지원 안 함). 급송이면 true.
     */
    private fun pBlock(pb: Block, g: Int): Boolean {
        val line = pb.line
        val mg = pb.gs.lastOrNull { GText.group(it) == 1 }
        if (mg == null || mg !in 0..1) {
            fail(IssueCode.G7X_P_BLOCK, line, pb.words.firstOrNull { it.key == "G" && GText.group(it.value.toInt()) == 1 }, listOf("G$g"))
        }
        if (g != 73) {
            val hx = pb.has("X") || pb.has("U")
            val hz = pb.has("Z") || pb.has("W")
            if (hx && hz) fail(if (g == 71) IssueCode.G71_TYPE2 else IssueCode.G72_TYPE2, line, if (g == 71) pb.w("Z") ?: pb.w("W") else pb.w("X") ?: pb.w("U"))
            if (if (g == 71) !hx else !hz) fail(IssueCode.G7X_P_BLOCK, line, args = listOf("G$g"))
        }
        return mg == 0
    }

    /**
     * G74 · G75 둘째 줄(S 4-112~4-113, H 102~108): X(U) Z(W) P(Δi µm) Q(Δk µm) R(Δd) F. G74 는 Z(W), G75 는 X(U) 가 있어야 한다.
     * 바닥 도피 Δd ≠ 0 은 지원 안 함(G7X_RELIEF — 방향이 기계마다, H 102 · 106 '드물게 쓰고 위험').
     */
    private fun g7475(b: Block, g: Int): Flow {
        val line = b.line
        b.words.firstOrNull { it.key !in setOf("G", "X", "Z", "U", "W", "P", "Q", "R", "F") }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
        val e = (if (g == 74) g74e else g75e) ?: fail(IssueCode.CYCLE_FIRST_LINE, line, gWord(b, g), listOf("G$g"))
        b.w("R")?.let { if (abs(it.value) > 1e-12) fail(IssueCode.G7X_RELIEF, line, it) }
        if (g == 74 && !(b.has("Z") || b.has("W"))) fail(IssueCode.WORD_MISSING, line, args = listOf("Z"))
        if (g == 75 && !(b.has("X") || b.has("U"))) fail(IssueCode.WORD_MISSING, line, args = listOf("X"))
        val a = cmd
        val end = target(b)
        // S 4-117 주 5: P · Q 는 입력 최소 단위(µm) 정수
        val shift = (b.w(if (g == 74) "P" else "Q")?.value ?: 0.0) / 1000
        val depth = (b.w(if (g == 74) "Q" else "P")?.value ?: 0.0) / 1000
        val sv = saveFS()
        cycleFS(b, allowS = false)
        checkFeed(line)
        val c = ctx()
        when (val r = GCycles.peck(g == 75, a, end, shift, depth, e)) {
            is GCycles.Bad -> fail(r.code, line, args = r.args)
            is GCycles.Ok -> emitCycle(r.moves, line, b.unit, c)
        }
        cmd = a
        restoreFS(sv, line, b.unit)
        motion = null
        return Next
    }

    /**
     * G76 둘째 줄(S 4-115~4-116, H 109~111): X(U) Z(W) R(i) P(k µm) Q(Δd µm) F(리드). X · Z · P · Q · F 가 있어야 한다.
     * 리드 F 는 이송으로 남기지 않는다(사이클 뒤 F 를 비움 — G92 와 같은 까닭).
     */
    private fun g76(b: Block): Flow {
        val line = b.line
        b.words.firstOrNull { it.key !in setOf("G", "X", "Z", "U", "W", "R", "P", "Q", "F") }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
        if (!(b.has("X") || b.has("U"))) fail(IssueCode.WORD_MISSING, line, args = listOf("X"))
        if (!(b.has("Z") || b.has("W"))) fail(IssueCode.WORD_MISSING, line, args = listOf("Z"))
        val mm = g76m
        val rr = g76r
        val aa = g76a
        val dmin = g76min
        val fin = g76d
        if (mm == null || rr == null || aa == null || dmin == null || fin == null) fail(IssueCode.CYCLE_FIRST_LINE, line, gWord(b, 76), listOf("G76"))
        val kW = b.w("P") ?: fail(IssueCode.WORD_MISSING, line, args = listOf("P"))
        val dW = b.w("Q") ?: fail(IssueCode.WORD_MISSING, line, args = listOf("Q"))
        val k = kW.value / 1000
        val d1 = dW.value / 1000
        if (k <= 0) fail(IssueCode.BAD_VALUE, line, kW)
        if (d1 <= 0) fail(IssueCode.BAD_VALUE, line, dW)
        if (fin > k) fail(IssueCode.BAD_VALUE, line, kW, listOf("R"))
        val fW = b.w("F") ?: fail(IssueCode.WORD_MISSING, line, args = listOf("F"))
        val lead = leadOf(fW, line)
        threadSpindle(line)
        val a = cmd
        val end = target(b)
        val c = ctx().copy(feed = lead, perRev = true)
        when (val r = GCycles.thread(a, end, b.w("R")?.value ?: 0.0, k, d1, dmin, fin, mm, if (threadPullOut) rr * lead / 10 else 0.0, aa.toDouble())) {
            is GCycles.Bad -> fail(r.code, line, args = r.args)
            is GCycles.Ok -> emitCycle(r.moves, line, b.unit, c)
        }
        cmd = a
        if (f != null) {
            f = null
            stateStep(line, b.unit)
        }
        motion = null
        return Next
    }

    /** 나사 리드 F: 소수점 필수(정수 F 의 단위가 기계마다 — S 1-14), 0 보다 커야 */
    private fun leadOf(w: Word, line: Int): Double {
        if (!w.point) fail(IssueCode.NO_POINT_FEED, line, w)
        if (w.value <= 0) fail(IssueCode.BAD_VALUE, line, w)
        return w.value
    }

    /** 나사는 주축 회전에 맞춰 나가므로 G98 이어도 주축이 돌아야 한다. G96 이면 경고(회전수가 바뀌면 나사가 어긋남) */
    private fun threadSpindle(line: Int) {
        if (spindle == SpindleDir.STOP) fail(IssueCode.SPINDLE_STOPPED, line)
        val sv = s
        if (sv == null || sv <= 0) fail(IssueCode.S_MISSING, line)
        if (css) issues += SimIssue(IssueCode.THREAD_IN_CSS, line)
    }

    /**
     * P · Q 순번 블록을 부른 단위 안에서 찾는다(H 82: 앞에서부터). 없으면 CYCLE_PQ_NOT_FOUND, 둘이면 CYCLE_PQ_DUP(설계 1.1),
     * Q 가 P 보다 앞이면(G71~G73 은 같아도 — 윤곽이 없다) CYCLE_PQ_ORDER.
     */
    private fun findPQ(b: Block, ui: Int, allowSame: Boolean): Pair<Int, Int> {
        val line = b.line
        val pW = b.w("P") ?: fail(IssueCode.WORD_MISSING, line, args = listOf("P"))
        val qW = b.w("Q") ?: fail(IssueCode.WORD_MISSING, line, args = listOf("Q"))
        val blocks = prog.units[ui].blocks
        fun find(w: Word): Int {
            val n = w.value.toInt()
            val idx = blocks.indices.filter { blocks[it].n == n }
            if (idx.isEmpty()) fail(IssueCode.CYCLE_PQ_NOT_FOUND, line, w, listOf(w.key, "N$n"))
            if (idx.size > 1) fail(IssueCode.CYCLE_PQ_DUP, line, w, listOf("N$n"))
            return idx[0]
        }
        val p = find(pW)
        val q = find(qW)
        if (q < p || (q == p && !allowSame)) fail(IssueCode.CYCLE_PQ_ORDER, line, qW)
        return p to q
    }

    /**
     * P~Q 안에서 받지 않는 것(S 4-117 주 2 · 3): M98/M99(M98_IN_PQ), G04 밖의 한 번 G, G00~G03 밖의 01 그룹, 06 그룹(G20 · G21).
     * 우리가 더 막는 것: M02 · M30(사이클 가운데서 끝남), G71~G73 은 G96~G99 도(FF 4.2.1 주 4: 매개변수 SSC 에 따라 무시 여부가 다름).
     * G71~G73 의 다른 M 은 받고 무시한다(FF 4.2.1 주 3: M 은 F · S · T 와 같이 — 황삭 동안 쓰지 않음. G70 에서는 실행).
     * P~Q 블록의 글 오류도 여기서 멈춘다(G71~G73 은 그 블록을 실행하지 않고 읽기만 한다).
     */
    private fun scanPQ(ui: Int, p: Int, q: Int, g: Int) {
        val blocks = prog.units[ui].blocks
        for (idx in p..q) {
            val pb = blocks[idx]
            if (pb.skip && m.blockSkip) continue
            prog.stopOf(pb)?.let { throw StopSim(it, fromParse = true) }
            for (w in pb.words) when (w.key) {
                "G" -> {
                    val gv = w.value.toInt()
                    val grp = GText.group(gv)
                    val bad = (grp == 0 && gv != 4) || (grp == 1 && gv !in 0..3) || grp == 6 || (g != 70 && (grp == 2 || grp == 5))
                    if (bad) fail(IssueCode.CYCLE_PQ_FORBIDDEN, pb.line, w, listOf("G$g"))
                }
                "M" -> {
                    val mv = w.value.toInt()
                    if (mv == 98 || mv == 99) fail(IssueCode.M98_IN_PQ, pb.line, w, listOf("G$g"))
                    if (mv == 2 || mv == 30) fail(IssueCode.CYCLE_PQ_FORBIDDEN, pb.line, w, listOf("G$g"))
                }
            }
        }
    }

    /**
     * P~Q 를 A(지금 자리)에서 보정 모형으로 마른 실행 — (A′ = P 블록 끝 실제 인선, A′→B 윤곽 토막). 걸음 · 시간 · 고스트는 남기지 않는다.
     * F · S · T 는 쓰지 않는다(S 4-103). 모서리 · 보정은 실제 실행과 같은 [pushMove] 를 지나 G70 과 같은 경로가 나온다.
     */
    private fun pqTip(ui: Int, p: Int, q: Int): Pair<Pt, List<GCycles.Piece>> {
        val a = cmd
        val saveMotion = motion
        val saveG4x = g4x
        val saveNoMove = noMoveCancelLine
        val saveNoMoveNose = noMoveNose
        val saveCStart = cStart
        val saveIdle = idle
        val out = mutableListOf<GCycles.Piece>()
        val blocks = prog.units[ui].blocks
        dry = out
        motion = null
        try {
            for (idx in p..q) {
                val pb = blocks[idx]
                if ((pb.skip && m.blockSkip) || pb.words.isEmpty()) continue
                dryBlock(pb)
            }
            held?.let { fail(IssueCode.CORNER_NO_NEXT, it.line, it.corner!!.word) } // S 4-117 주 4
            if (gc != null || g4x != null) fail(IssueCode.CYCLE_COMP_OPEN, blocks[q].line)
        } finally {
            dry = null
            cmd = a
            motion = saveMotion
            g4x = saveG4x
            noMoveCancelLine = saveNoMove
            noMoveNose = saveNoMoveNose
            cStart = saveCStart
            idle = saveIdle
        }
        val pLine = blocks[p].line
        val first = out.firstOrNull()
        return if (first != null && first.line == pLine && !first.corner) first.tip.end to out.drop(1) else a to out
    }

    /** 마른 실행의 블록 하나 — [block] 에서 F · S · T · M · 시간을 뺀 것(G04 는 윤곽에 영향 없음) */
    private fun dryBlock(b: Block) {
        val line = b.line
        val gs = b.gs
        val gMotion = gs.lastOrNull { GText.group(it) == 1 }
        val mot = gMotion ?: motion
        val axes = b.has("X") || b.has("Z") || b.has("U") || b.has("W")
        held?.let { h -> if (4 in gs || !axes || mot !in 1..3) fail(IssueCode.CORNER_NO_NEXT, h.line, h.corner!!.word, listOf(line.toString())) }
        if (motion == null && gMotion == null && axes) fail(IssueCode.CYCLE_MOTION_MISSING, line)
        if (4 in gs) {
            b.words.firstOrNull { !(it.key == "G" && it.value.toInt() == 4) && it.key !in setOf("X", "U", "P") }?.let { fail(IssueCode.G04_MIXED, line, it) }
            countIdle(line, false, null)
            return
        }
        val moveBlock = axes || (mot in 2..3 && (b.has("I") || b.has("K")))
        checkWords(b, gs, null, mot, null, moveBlock)
        for (gv in gs) when (gv) {
            40 -> g4x = null
            41 -> g4x = Side.G41
            42 -> g4x = Side.G42
        }
        if (gMotion != null) motion = gMotion
        val wantOn = g4x != null && offset != 0
        gc?.let { if (wantOn && g4x != it.side) gc = switchSide(it, b) }
        val moved = if (moveBlock) move(b, mot!!, wantOn) else compNoMove(line, b.unit, wantOn)
        if (g4x != null && offset == 0 && moved && !noOffsetWarned) {
            noOffsetWarned = true
            issues += SimIssue(IssueCode.COMP_NO_OFFSET, line)
        }
        countIdle(line, moved, null)
    }

    /** 사이클 줄의 F · S(사이클 동안만 — 끝나면 [restoreFS]) */
    private fun cycleFS(b: Block, allowS: Boolean) {
        val line = b.line
        b.w("F")?.let { w ->
            if (perRev && !w.point) fail(IssueCode.NO_POINT_FEED, line, w)
            if (w.value <= 0) fail(IssueCode.BAD_VALUE, line, w)
            f = w.value
        }
        b.w("S")?.let { w ->
            if (!allowS) fail(IssueCode.WORD_NOT_USED, line, w)
            s = w.value
            if (!css && w.value > nMax()) issues += SimIssue(IssueCode.RPM_OVER_LIMIT, line, w.cols, w.text, listOf(Fmt.nc(nMax())))
        }
    }

    private fun saveFS() = FS(f, s, css, perRev)

    private fun restoreFS(v: FS, line: Int, unit: Int) {
        if (f == v.f && s == v.s && css == v.css && perRev == v.perRev) return
        f = v.f
        s = v.s
        css = v.css
        perRev = v.perRev
        stateStep(line, unit)
    }

    /** 사이클 이동을 내보낸다(보정 없음 — 실제 인선 = 사이클 경로). 고스트는 두지 않는다(설계 2.3: 사이클 층 · 후퇴 제외) */
    private fun emitCycle(moves: List<GCycles.Mov>, line: Int, unit: Int, c: Ctx) {
        if (moves.size > GSim.EXEC_LIMIT) fail(IssueCode.EXEC_LIMIT, line)
        for (mv in moves) {
            pushMove(Mv(mv.seg, mv.kind, line, unit, mv.src, c, CompMode.OFF, null, null, ghost = false, pass = mv.pass, model = mv.model, cmdSeg = mv.cmd))
        }
    }

    /** 이 블록 명령이 쓰지 않는 주소를 막는다(틀리게 흉내 내지 않게) */
    private fun checkWords(b: Block, gs: List<Int>, g0: Int?, mot: Int?, mCode: Int?, moveBlock: Boolean) {
        val line = b.line
        val xzuw = setOf("X", "Z", "U", "W")
        val used = mutableSetOf("G", "M", "T", "S", "F")
        when (g0) {
            28 -> used += xzuw
            // G30 P2(P 는 빼도 됨 — F0 6 '2nd reference position return'). P3 · P4(제3 · 제4 원점)는 자리를 몰라 지원 안 함
            30 -> {
                used += xzuw + "P"
                b.w("P")?.let { if (it.value.toInt() != 2) fail(IssueCode.BAD_VALUE, line, it) }
            }
            50 -> {
                b.words.firstOrNull { it.key in xzuw }?.let { fail(IssueCode.G50_COORD, line, it) }
                used.clear()
                // G50 S 와 같은 블록의 M 은 받는다(블록 끝에 실행 — H 133). FANUC(F0 9.3 · FF)에는 막는 말이 없다 — 'G50 S 는 다른 명령 없이' 는 H 49(Haas)
                used += setOf("G", "S", "M")
            }
            null -> if (moveBlock) {
                used += xzuw
                when (mot) {
                    0 -> b.words.firstOrNull { it.key == ",C" || it.key == ",R" }?.let { fail(IssueCode.CORNER_ON_RAPID, line, it) }
                    1 -> {
                        if (40 in gs) b.words.firstOrNull { it.key == "I" || it.key == "K" }?.let { fail(IssueCode.COMP_G40_IK, line, it) }
                        // FANUC · S 3-79 의 직각 면취 · 라운드(G01 … I/K/R) 와 Haas 모서리 — 받지 않는다(,C ,R 로)
                        b.words.firstOrNull { it.key == "I" || it.key == "K" || it.key == "R" }?.let { fail(IssueCode.G01_CORNER_IKR, line, it) }
                        used += setOf(",C", ",R")
                    }
                    2, 3 -> used += setOf("R", "I", "K", ",C", ",R")
                }
            }
        }
        when (mCode) {
            98 -> used += setOf("P", "L")
            99 -> used += "P"
        }
        b.words.firstOrNull { it.key !in used }?.let { fail(IssueCode.WORD_NOT_USED, line, it) }
    }

    /** T 글 → (공구 번호 — null = 공구대 그대로, 보정 번호). 뒤 2자리 = 보정(H 65 · S 3-78) */
    private fun parseT(raw: String): Pair<Int?, Int> {
        if (raw.length <= 2) return null to 0 // T0 · T00
        val tl = raw.dropLast(2).toInt()
        return (if (tl == 0) null else tl) to raw.takeLast(2).toInt()
    }

    private fun nMax(): Double = min(clamp ?: Double.POSITIVE_INFINITY, m.maxRpm)

    /** 이송 조건(H 50 · S 3-81): F 가 있고, G99 면 주축이 돌고 S 가 있어야. G96 에 G50 S 가 없으면 한 번 경고 */
    private fun checkFeed(line: Int) {
        if (f == null) fail(IssueCode.FEED_MISSING, line)
        if (perRev) {
            if (spindle == SpindleDir.STOP) fail(IssueCode.SPINDLE_STOPPED, line)
            val sv = s
            if (sv == null || sv <= 0) fail(IssueCode.S_MISSING, line)
            if (css && clamp == null && !clampWarned) {
                clampWarned = true
                issues += SimIssue(IssueCode.NO_CLAMP_G96, line, args = listOf(Fmt.nc(m.maxRpm)))
            }
        }
    }

    /** 주축 · 절삭유 · S · F 상태 걸음(시간 0) */
    private fun stateStep(line: Int, unit: Int) {
        val st = StateStep(line, unit, 0.0, 0.0, spindle, if (css) null else s?.let { min(it, nMax()) }, coolant, if (css) s else null, s, f, perRev)
        queue { t0 -> st.copy(t0 = t0, t1 = t0) }
    }

    private fun ctx(): Ctx {
        val td = if (offset != 0) input.tools[offset] else null
        val r = td?.noseR ?: 0.0
        val tip = td?.tip?.takeIf { it in 0..9 } ?: 0
        return Ctx(
            tool, offset.takeIf { it != 0 }, r, tip, NoseComp.tipVector(tip, r),
            f, perRev, if (css) s else null, s, nMax(), spindle,
        )
    }

    private fun target(b: Block): Pt {
        var z = cmd.z
        var x = cmd.x
        b.w("X")?.let { x = it.value / 2 }
        b.w("U")?.let { x = cmd.x + it.value / 2 }
        b.w("Z")?.let { z = it.value }
        b.w("W")?.let { z = cmd.z + it.value }
        return Pt(z, x)
    }

    // ================================================================ 명령

    /** G04(S 3-72): X/U = 초(소수점 규칙 적용), P = ms 정수. 다른 명령과 한 블록 금지 */
    private fun dwell(b: Block): Flow {
        val line = b.line
        b.words.firstOrNull { !(it.key == "G" && it.value.toInt() == 4) && it.key !in setOf("X", "U", "P") }
            ?.let { fail(IssueCode.G04_MIXED, line, it) }
        val ws = listOfNotNull(b.w("X"), b.w("U"), b.w("P"))
        if (ws.isEmpty()) fail(IssueCode.WORD_MISSING, line, args = listOf("X"))
        if (ws.size > 1) fail(IssueCode.G04_MIXED, line, ws[1])
        val w = ws[0]
        val sec = if (w.key == "P") w.value / 1000 else w.value
        if (sec < 0) fail(IssueCode.BAD_VALUE, line, w)
        queue { t0 -> DwellStep(line, b.unit, t0, t0 + sec, sec) }
        countIdle(line, false, null)
        return Next
    }

    /**
     * G28(S 2-47~2-48 · H 48): 쓴 축만 급송으로 중간점 → 원점. U0 · W0 = 중간점 없이.
     * G30(F0 6 · FF G 코드 표 '2nd reference position return'): 같은 꼴로 제2 원점([MachineSetup.home2], 없으면 G28 원점)
     */
    private fun g28(b: Block, g: Int): Boolean {
        val line = b.line
        if (!(b.has("X") || b.has("Z") || b.has("U") || b.has("W"))) fail(IssueCode.WORD_MISSING, line, args = listOf("X"))
        noMoveCancelLine?.let { fail(IssueCode.COMP_CANCEL_NO_MOVE, line, args = listOf(it.toString())) }
        val via = target(b)
        val ref = if (g == 30) m.home2 ?: m.home else m.home
        val home = Pt(
            if (b.has("Z") || b.has("W")) ref.z else via.z,
            if (b.has("X") || b.has("U")) ref.x else via.x,
        )
        val c = ctx()
        val start = cmd
        var moved = false
        if ((via - start).length > 1e-9) {
            pushMove(Mv(Line(start, via), MoveKind.RAPID, line, b.unit, StepSrc.G28_VIA, c, CompMode.OFF, null, null, ghost = false))
            moved = true
        }
        if ((home - via).length > 1e-9) {
            pushMove(Mv(Line(via, home), MoveKind.RAPID, line, b.unit, StepSrc.G28_HOME, c, CompMode.OFF, null, null))
            moved = true
        }
        cmd = home
        return moved
    }

    /** G00~G03 이동 블록. 움직였으면 true */
    private fun move(b: Block, mot: Int, wantOn: Boolean): Boolean {
        val line = b.line
        val start = cmd
        val tgt = target(b)
        val cornerW = b.w(",C") ?: b.w(",R")
        val zero = (tgt - start).length <= 1e-9
        if (zero && mot <= 1) {
            held?.let { fail(IssueCode.CORNER_NO_NEXT, it.line, it.corner!!.word, listOf(line.toString())) }
            // 형식 A · B 는 움직임 0 인 블록을 시작 블록으로 받지 않는다. 형식 C 는 '이동 없는 시작 블록'(FF 5.4.2 시작 조건 (4) · 표 5.4.1(a))
            if (gc == null && wantOn && !typeC) fail(IssueCode.COMP_START_ZERO, line)
            if (cornerW != null) fail(IssueCode.CORNER_NO_VERTEX, line, cornerW)
            return compNoMove(line, b.unit, wantOn)
        }
        val seg: Seg = if (mot <= 1) Line(start, tgt) else arc(b, start, tgt, mot == 2)
        val corner = cornerW?.let {
            if (it.value <= 0) fail(IssueCode.BAD_VALUE, line, it)
            CornerReq(it.key == ",C", it.value, it)
        }
        if (mot >= 1 && dry == null) checkFeed(line) // 마른 실행(G71~G73 윤곽)은 P~Q 의 F · S 를 쓰지 않는다
        // 이동 없이 보정을 끈 뒤 첫 이동: 남은 벡터를 이 이동에서 지운다 — 노즈는 끝 법선 자리에서 이 블록 끝점(G40 상태)으로 곧게
        // (FF 5.4.4 그림 5.4.4(e) 204쪽 형식 A·B 'The remaining vectors are canceled with the next move command', F0 14.3.4 228쪽).
        // 직선(G00/G01)만 — 원호 · 모서리 · 곧바로 다시 보정 시작은 그 경로를 출처로 확인하지 못해 멈춘다
        val fromNose = noMoveCancelLine?.let { at ->
            if (mot >= 2 || wantOn || cornerW != null) fail(IssueCode.COMP_CANCEL_NO_MOVE, line, args = listOf(at.toString()))
            noMoveCancelLine = null
            noMoveNose
        }
        val cur = gc
        val mode = when {
            cur == null && wantOn -> CompMode.START
            cur != null && wantOn -> CompMode.ON
            cur != null -> CompMode.CANCEL
            else -> CompMode.OFF
        }
        var g = cur
        when (mode) {
            CompMode.START -> {
                if (seg is Arc) fail(IssueCode.COMP_START_ARC, line)
                val td = input.tools[offset]
                val r = td?.noseR
                val tip = td?.tip
                if (r == null || tip == null || tip !in 0..9 || r < 0) {
                    fail(IssueCode.TOOL_DATA_MISSING, line, args = listOf(tool?.toString() ?: "", offset.toString()))
                }
                g = GComp(r, tip, g4x!!)
                gc = g
                compOffsets += offset
            }
            CompMode.CANCEL -> {
                if (seg is Arc) fail(IssueCode.COMP_CANCEL_ARC, line)
                gc = null
            }
            else -> {}
        }
        val kind = if (mot == 0) MoveKind.RAPID else MoveKind.FEED
        pushMove(Mv(seg, kind, line, b.unit, StepSrc.BLOCK, ctx(), mode, g, corner, noseFrom = fromNose))
        cmd = tgt
        return true
    }

    /**
     * 원호(1.9 — H 41~46 · S 2-27~2-30). R > 0 = 180° 이하, R < 0 = 넘음. I · K = 시작점에서 중심까지 증분, I 는 반경값(H 44).
     * 현/2 가 |R| 을 0.001 이하로 넘으면 반원, 더 넘으면 ARC_R_SHORT. I · K 의 끝점이 원에서 0.002 넘게 벗어나면 ARC_END_OFF.
     */
    private fun arc(b: Block, start: Pt, end: Pt, cw: Boolean): Arc {
        val line = b.line
        val rW = b.w("R")
        val iW = b.w("I")
        val kW = b.w("K")
        if (rW != null && (iW != null || kW != null)) fail(IssueCode.ARC_R_AND_IK, line, iW ?: kW)
        if (rW == null && iW == null && kW == null) fail(IssueCode.ARC_NO_CENTER, line)
        val chord = (end - start).length
        if (chord <= 1e-9) fail(IssueCode.FULL_CIRCLE, line)
        val c = if (rW != null) {
            val rr = rW.value
            val half = chord / 2
            if (half > abs(rr) + 0.001) fail(IssueCode.ARC_R_SHORT, line, rW, listOf(Fmt.nc(half - abs(rr))))
            val h = sqrt(max(0.0, rr * rr - half * half))
            val mid = (start + end) * 0.5
            val nl = (end - start).let { it * (1 / it.length) }.let { Pt(-it.x, it.z) }
            // 시계(G02) + 180° 이하 → 중심이 진행 오른쪽
            if (cw != (rr < 0)) mid - nl * h else mid + nl * h
        } else {
            val cc = start + Pt(kW?.value ?: 0.0, iW?.value ?: 0.0)
            val off = abs((end - cc).length - (start - cc).length)
            if (off > 0.002) fail(IssueCode.ARC_END_OFF, line, iW ?: kW, listOf(Fmt.nc(off)))
            cc
        }
        return Arc(start, end, c, cw)
    }

    private fun call(b: Block, ui: Int, bi: Int, stack: MutableList<Frame>): Flow {
        val line = b.line
        val pW = b.w("P") ?: fail(IssueCode.WORD_MISSING, line, args = listOf("P"))
        val lW = b.w("L")
        val raw = pW.raw
        // S 4-131: M98 P xxxx yyyy(앞 4자리 반복 · 뒤 4자리 번호) 또는 P yyyy L n. 둘 다 있으면 L(예 `P30077 L2` = O0077 두 번)
        val num = raw.takeLast(4).toInt()
        val times = when {
            lW != null -> lW.value.toInt()
            raw.length > 4 -> raw.dropLast(4).toInt()
            else -> 1
        }
        if (lW != null && (times < 1 || times > 9999)) fail(IssueCode.BAD_VALUE, line, lW)
        val units = prog.units.indices.filter { prog.units[it].number == num }
        // 글에 없는 부프로그램은 기계에만 있는 것으로 보고 건너뛴다(노랑) — 그 안의 이동 · 시간은 그리지 않는다
        if (units.isEmpty()) {
            issues += SimIssue(IssueCode.SUB_SKIPPED, line, pW.cols, pW.text, listOf(num.toString()))
            return Next
        }
        if (units.size > 1) fail(IssueCode.DUP_PROGRAM, line, pW, listOf(num.toString()))
        if (stack.size >= GSim.SUB_NEST) fail(IssueCode.SUB_NEST, line, pW, listOf(GSim.SUB_NEST.toString()))
        stack += Frame(ui, bi + 1, max(1, times))
        return Jump(units[0], 0)
    }

    /** 부프로그램의 M99(S 4-132): 반복이 남으면 처음부터, 아니면 부른 블록 다음 — P 면 부른 쪽 N(앞으로 찾고 없으면 처음부터) */
    private fun ret(b: Block, ui: Int, stack: MutableList<Frame>): Flow {
        val fr = stack.last()
        if (fr.left > 1) {
            fr.left--
            return Jump(ui, 0)
        }
        stack.removeAt(stack.lastIndex)
        val pW = b.w("P") ?: return Jump(fr.unit, fr.ret)
        val n = pW.value.toInt()
        val blocks = prog.units[fr.unit].blocks
        val idx = (fr.ret until blocks.size).firstOrNull { blocks[it].n == n } ?: blocks.indices.firstOrNull { blocks[it].n == n }
            ?: fail(IssueCode.SUB_NOT_FOUND, b.line, pW, listOf("N$n"))
        return Jump(fr.unit, idx)
    }

    // ================================================================ 보정 세션

    /**
     * 움직임 없는 블록(축이 없거나 움직임 0)에서 보정이 꺼지거나(G40 · T□□00) 켜지면(G41/G42 · 보정 번호) 그 처리. 형식 C 의 옮김이 있었으면 true.
     * 형식 A · B 의 이동 없는 시작은 할 일이 없다 — 다음 이동 블록이 시작 블록(FF 179쪽 'The tool does not operate in a start-up block').
     * G02/G03 모드의 이동 없는 시작 · 취소는 형식과 무관하게 멈춘다(FF 5.4.1 'When performing offset cancel, circular arc commands (G02 and G03)
     * are not available' · 시작 조건 (3), F0 알람표 PS0034 'The start up or cancel was going to be performed in the G02 or G03 mode') — 형식 C 는 [shiftKind]
     */
    private fun compNoMove(line: Int, unit: Int, wantOn: Boolean): Boolean = when {
        gc != null && !wantOn -> {
            if (!typeC && (motion == 2 || motion == 3)) fail(IssueCode.COMP_CANCEL_ARC, line)
            cancelNoMove(line, unit)
            typeC
        }
        gc == null && wantOn && typeC -> {
            startNoMove(line, unit)
            true
        }
        else -> {
            if (gc == null && wantOn && (motion == 2 || motion == 3)) fail(IssueCode.COMP_START_ARC, line)
            false
        }
    }

    /** 형식 C 의 옮김 이동 종류 = 그 블록의 01 그룹 모달. G00/G01 만(FF 5.4.2 시작 조건 (3) — G02/G03 모드면 알람 PS0034) */
    private fun shiftKind(line: Int, start: Boolean): MoveKind = when (motion) {
        0 -> MoveKind.RAPID
        1 -> MoveKind.FEED.also { if (dry == null) checkFeed(line) }
        null -> fail(IssueCode.CYCLE_MOTION_MISSING, line)
        90, 92, 94 -> fail(IssueCode.COMP_IN_CYCLE, line)
        else -> fail(if (start) IssueCode.COMP_START_ARC else IssueCode.COMP_CANCEL_ARC, line)
    }

    /**
     * 형식 C 의 이동 없는 시작(FF 180쪽 'For type C — The tool shifts by the compensation value in the direction vertical to the block with tool
     * movement subsequent to the start-up block', 표 5.4.1(a)): 보정 세션을 지금 열고, 노즈 옮김(P − t → P + r·n₁)은 다음 이동 블록이 와서
     * n₁ 을 알 때 그린다([compPush]). 그 블록은 시작 블록이 아니라 보정 중 블록이다(끝 = 다음 블록과의 이음).
     */
    private fun startNoMove(line: Int, unit: Int) {
        val kind = shiftKind(line, start = true)
        val td = input.tools[offset]
        val r = td?.noseR
        val tip = td?.tip
        if (r == null || tip == null || tip !in 0..9 || r < 0) {
            fail(IssueCode.TOOL_DATA_MISSING, line, args = listOf(tool?.toString() ?: "", offset.toString()))
        }
        gc = GComp(r, tip, g4x!!)
        compOffsets += offset
        cStart = CStart(line, unit, cmd, kind, ctx())
    }

    /**
     * 이동 없는 블록에서 보정을 끔(G40 · T□□00) — 마지막 보정 블록을 끝 법선 자리까지 그린다('In the block preceding the cancel block,
     * a vector is created … in the vertical direction' — FF 5.4.4 그림 5.4.4(e) 204쪽, F0 14.3.4 228쪽).
     * 형식 A · B: 'The tool does not operate in the cancel block' — 다음 직선 이동이 그 자리에서 벡터를 지운다([move]).
     * 형식 C: 'The tool shifts by the compensation value in the direction vertical to the block preceding the cancel block'(FF 그림 5.4.4(f) 205쪽) —
     * 이 블록에서 노즈가 끝 법선 E + r·nₑ → 지령점 − 팁 벡터(G40 상태 노즈)로 옮긴다(그 블록의 모달 G00/G01).
     * G02/G03 모드면 형식과 무관하게 COMP_CANCEL_ARC(FANUC 알람 PS0034 — [compNoMove] · [shiftKind]).
     */
    private fun cancelNoMove(line: Int, unit: Int) {
        held?.let { fail(IssueCode.CORNER_NO_NEXT, it.line, it.corner!!.word, listOf(line.toString())) }
        if (!typeC) {
            noMoveNose = pend?.let { it.gc!!.openEnd(it.seg) }
            closeOpen()
            gc = null
            noMoveCancelLine = line
            issues += SimIssue(IssueCode.COMP_CANCEL_ALONE, line)
            return
        }
        // 이동 없는 G41/G42 뒤 이동 블록 없이 끔 — 시작 옮김의 방향(다음 이동 블록의 법선)이 없다
        cStart?.let { fail(IssueCode.COMP_START_ZERO, line, args = listOf(it.line.toString())) }
        val kind = shiftKind(line, start = false)
        val from = pend?.let { it.gc!!.openEnd(it.seg) }
        closeOpen()
        gc = null
        if (from != null) {
            pushMove(Mv(Line(cmd, cmd), kind, line, unit, StepSrc.SHIFT_CANCEL, ctx(), CompMode.OFF, null, null, noseFrom = from))
        }
        issues += SimIssue(IssueCode.COMP_CANCEL_ALONE, line, args = listOf("C"))
    }

    /**
     * 보정 중 G41 ↔ G42 바꿈(FF 5.4.3 189쪽 'The offset direction can be changed in the offset mode … However, the change is not available in the
     * start-up block and the block following it'): 바뀐 쪽 세션 — 이음점은 다음 이동 블록이 [compPush] 에 올 때 [GComp.switchTo].
     * 시작 블록(이동 있는 시작 · 형식 C 의 이동 없는 시작) 바로 다음 블록이면 COMP_SWITCH.
     */
    private fun switchSide(cur: GComp, b: Block): GComp {
        if (cStart != null || pend?.mode == CompMode.START) fail(IssueCode.COMP_SWITCH, b.line, b.words.firstOrNull { it.key == "G" && it.value.toInt() in 41..42 })
        return GComp(cur.r, cur.tip, g4x!!)
    }

    /** 보정 중 움직임 없는 블록이 둘 이어지면 멈춘다(H 58 — 한두 블록 앞을 읽는다). M98/M99 블록은 세지 않는다(S 3-77 주 2) */
    private fun countIdle(line: Int, moved: Boolean, mCode: Int?) {
        if (gc == null || moved) {
            idle = 0
            return
        }
        if (mCode == 98 || mCode == 99) return
        if (++idle >= 2) fail(IssueCode.COMP_IDLE_BLOCKS, line)
    }

    /** 정상 끝(M30 · M02 · 글 끝): 열린 모서리는 오류, 열린 보정은 끝 법선 자리까지 그리고 경고 */
    private fun finishOpen() {
        held?.let { fail(IssueCode.CORNER_NO_NEXT, it.line, it.corner!!.word) }
        // 형식 C 의 이동 없는 시작 뒤 이동 블록 없이 끝 — 옮김은 그리지 않고 미룬 M · 상태만
        cStart?.let { cs ->
            cStart = null
            cs.after.forEach { it() }
            issues += SimIssue(IssueCode.COMP_NOT_CANCELLED, lastLine)
        }
        if (pend != null) {
            closeOpen()
            issues += SimIssue(IssueCode.COMP_NOT_CANCELLED, lastLine)
        }
    }

    // ================================================================ 내보내기: 모서리 → 보정 → 걸음

    private fun pushMove(mv: Mv) {
        val h = held
        if (h == null) {
            if (mv.corner != null) held = mv else compPush(mv)
            return
        }
        held = null
        val cr = h.corner!!
        when (val res = GCorner.apply(h.seg, mv.seg, cr.chamfer, cr.size)) {
            is GCorner.Bad -> fail(res.code, h.line, cr.word)
            is GCorner.Ok -> {
                compPush(h.copy(seg = res.a, corner = null, after = mutableListOf()))
                val cm = if (h.mode == CompMode.START || h.mode == CompMode.ON) CompMode.ON else CompMode.OFF
                // 모서리 블록의 M · 상태는 끼운 모서리 뒤로
                compPush(h.copy(seg = res.corner, src = StepSrc.CORNER, mode = cm, corner = null))
                val nb = mv.copy(seg = res.b)
                if (nb.corner != null) held = nb else compPush(nb)
            }
        }
    }

    private fun compPush(mv: Mv) {
        when (mv.mode) {
            CompMode.OFF -> {
                val tv = mv.ctx.tv
                emitMove(mv, mv.noseFrom?.let { Line(it, mv.seg.end - tv) } ?: shift(mv.seg, tv * -1.0), tv)
                if (mv.ghost) ghost(mv.line, mv.seg.end, mv.seg.end - tv, tv, mv.ctx.r, mv.ctx.tipNo)
                runAfter(mv)
            }
            CompMode.START -> {
                pend = mv
                pendStart = mv.seg.start - mv.gc!!.tv
                pendStartup = true
            }
            CompMode.ON, CompMode.CANCEL -> {
                val g = mv.gc!!
                val seg = mv.seg
                if (seg is Arc && !g.arcOk(seg)) fail(IssueCode.COMP_ARC_SMALL, mv.line, args = listOf(Fmt.nc(seg.radius), Fmt.nc(g.r)))
                val cancel = mv.mode == CompMode.CANCEL
                val cs = cStart
                if (pend == null && cs != null) {
                    // 형식 C 의 이동 없는 시작: 이 블록(시작 뒤 첫 이동 블록) 처음의 공구 쪽 법선 n₁ 으로 그 블록 자리에서 P − t → P + r·n₁
                    cStart = null
                    val p1 = cs.at + g.normal(GComp.tanAt(seg, seg.start)) * g.r
                    emitMove(Mv(Line(cs.at, cs.at), cs.kind, cs.line, cs.unit, StepSrc.SHIFT_START, cs.ctx, CompMode.ON, g, null), Line(cs.at - g.tv, p1), g.tv)
                    ghost(cs.line, cs.at, p1, g.tv, g.r, g.tip)
                    cs.after.forEach { it() }
                    if (cancel) {
                        // 곧바로 이동 있는 취소(형식 A): 옮긴 자리에서 끝점 − 팁 벡터로 곧게
                        emitMove(mv, Line(p1, seg.end - g.tv), g.tv)
                        ghost(mv.line, seg.end, seg.end - g.tv, g.tv, g.r, g.tip)
                        runAfter(mv)
                    } else {
                        pend = mv
                        pendStart = p1
                        pendStartup = false
                    }
                    return
                }
                val p = pend!!
                val pg = p.gc!!
                // 보정 중 G41 ↔ G42 바꿈([switchSide]) 뒤 첫 이동 블록 — 바꾼 바로 다음 취소는 그 경로가 출처에 없어 멈춘다
                val joint = if (pg.side == g.side) g.junction(p.seg, seg, pendStartup, cancel) else {
                    if (cancel) fail(IssueCode.COMP_SWITCH, mv.line, args = listOf("G40"))
                    pg.switchTo(g, p.seg, seg)
                }
                when (val res = joint) {
                    is GComp.Bad -> fail(res.code, mv.line, args = listOf(p.line.toString()))
                    is GComp.Ok -> {
                        if (res.outer) issues += SimIssue(IssueCode.COMP_TYPE_A, if (pendStartup) p.line else mv.line, args = if (typeC) listOf("C") else emptyList())
                        finalizePend(res.p)
                        if (!cancel) {
                            pend = mv
                            pendStart = res.p
                            pendStartup = false
                        } else {
                            emitMove(mv, Line(res.p, seg.end - g.tv), g.tv)
                            ghost(mv.line, seg.end, seg.end - g.tv, g.tv, g.r, g.tip)
                            runAfter(mv)
                        }
                    }
                }
            }
        }
    }

    /** 늦춘 보정 블록을 노즈 점 j 에서 끝내 내보낸다 */
    private fun finalizePend(j: Pt) {
        val p = pend!!
        val g = p.gc!!
        val nose: Seg = if (pendStartup) Line(pendStart, j) else {
            val (sg, err) = g.piece(p.seg, pendStart, j)
            if (err != null) fail(err, p.line)
            sg!!
        }
        pend = null
        emitMove(p, nose, g.tv)
        ghost(p.line, p.seg.end, j, g.tv, g.r, g.tip)
        runAfter(p)
    }

    private fun runAfter(mv: Mv) {
        val list = mv.after.toList()
        mv.after.clear()
        list.forEach { it() }
    }

    /** 보정을 끄지 않고 끝내는 마지막 블록: 끝 법선 자리까지 */
    private fun closeOpen() {
        val p = pend ?: return
        finalizePend(p.gc!!.openEnd(p.seg))
    }

    /** 이동이 아닌 걸음 — 늦춘 이동이 있으면 그 뒤로 미룬다 */
    private fun queue(make: (Double) -> SimStep) {
        if (dry != null) return
        val action = {
            val st = make(t)
            steps += st
            t = st.t1
        }
        val h = held
        val p = pend
        val cs = cStart
        when {
            h != null -> h.after += action
            p != null -> p.after += action
            cs != null -> cs.after += action
            else -> action()
        }
    }

    private fun emitMove(mv: Mv, nose: Seg, tv: Pt) {
        val tip = shift(nose, tv)
        if (GTime.length(tip) <= 1e-12) return
        dry?.let {
            it += GCycles.Piece(tip, mv.cmdSeg ?: mv.seg, mv.kind == MoveKind.RAPID, mv.line, mv.src == StepSrc.CORNER)
            return
        }
        val c = mv.ctx
        if (mv.kind == MoveKind.THREAD) {
            // 나사(G92 · G76): 리드는 긴 축으로 잰다. G96 이면 그 토막 시작 X 의 회전수로 고정(THREAD_IN_CSS 경고 — 설계 4.3)
            val n = rpmAt(c, tip.start.x)
            val axis = max(abs(tip.end.z - tip.start.z), abs(tip.end.x - tip.start.x))
            add(mv, tip, tv, c.feed, axis / (c.feed!! * n) * 60, n, n)
        } else if (mv.kind == MoveKind.RAPID) {
            val br = GTime.rapidBreak(tip.start, tip.end, if (mv.dogleg) m.copy(rapidPath = RapidPath.DOGLEG) else m)
            val pieces = if (br == null) listOf(Line(tip.start, tip.end)) else listOf(Line(tip.start, br), Line(br, tip.end))
            // G96 급송은 끝점 X 로 회전수를 정한다(S 3-82)
            val rpm = rpmAt(c, tip.end.x)
            for (pc in pieces) {
                val dur = GTime.rapidSec(pc.start, pc.end, m)
                add(mv, pc, tv, null, dur, rpm, rpm)
            }
        } else {
            val dur = GTime.feedSec(tip, c.feed!!, c.perRev, c.css, c.sCmd, c.nMax)
            add(mv, tip, tv, c.feed, dur, rpmAt(c, tip.start.x), rpmAt(c, tip.end.x))
        }
    }

    private fun add(mv: Mv, tip: Seg, tv: Pt, feed: Double?, dur: Double, rpm0: Double, rpm1: Double) {
        val c = mv.ctx
        val r = mv.gc?.r ?: c.r
        val tipNo = mv.gc?.tip ?: c.tipNo
        steps += MoveStep(
            mv.line, mv.unit, t, t + dur, mv.kind, tip, shift(tip, tv * -1.0), mv.cmdSeg ?: mv.seg, mv.src, mv.pass, mv.model,
            c.tool, c.offset, r, tipNo, feed, c.perRev, c.css, c.sCmd, rpm0, rpm1,
        )
        t += dur
        lastMoveIdx = steps.lastIndex
    }

    private fun ghost(line: Int, cmdPt: Pt, nose: Pt, tv: Pt, r: Double, tipNo: Int) {
        if (dry != null) return
        ghosts +=Ghost(line, lastMoveIdx, cmdPt, nose + tv, nose, r, tipNo)
    }

    private fun rpmAt(c: Ctx, x: Double): Double = when {
        c.spindle == SpindleDir.STOP -> 0.0
        c.css != null -> GTime.cssRpm(x, c.css, c.nMax)
        else -> min(c.sCmd ?: 0.0, c.nMax)
    }

    private fun shift(s: Seg, v: Pt): Seg = when (s) {
        is Line -> Line(s.start + v, s.end + v)
        is Arc -> Arc(s.start + v, s.end + v, s.center + v, s.cw)
    }

    private fun extent(): Pair<Pt, Pt>? {
        var lo: Pt? = null
        var hi: Pt? = null
        fun take(p: Pt) {
            lo = lo?.let { Pt(min(it.z, p.z), min(it.x, p.x)) } ?: p
            hi = hi?.let { Pt(max(it.z, p.z), max(it.x, p.x)) } ?: p
        }
        for (st in steps) {
            if (st !is MoveStep) continue
            val s = st.tip
            take(s.start)
            take(s.end)
            if (s is Arc) {
                val a0 = angleOf(s.start - s.center)
                val sw = s.sweepRad()
                for (q in 0 until 4) {
                    val ang = q * PI / 2
                    val u = wrap2Pi((if (s.cw) -1 else 1) * (ang - a0))
                    if (u < sw) take(s.center + Pt(cos(ang), sin(ang)) * s.radius)
                }
            }
        }
        val l = lo ?: return null
        return l to hi!!
    }

    private fun fail(code: IssueCode, line: Int, w: Word? = null, args: List<String> = emptyList()): Nothing =
        throw StopSim(SimIssue(code, line, w?.cols, w?.text, args))
}
