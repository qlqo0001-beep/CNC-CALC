package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 같은 공구 자세를 두 프로그램으로. [direct] = 직접 보정(G40) 프로그램의 가상 인선 좌표, [directLine] = 그 풀이 nc 의 줄(0부터).
 * [machine] = 기계 보정(G41/G42) 프로그램(노즈R 0 으로 푼 도면 좌표 풀이)의 도면 명령 좌표, [machineLine] = 그 풀이 nc 의 줄 —
 * 도면 좌표 풀이가 없거나 그 프로그램에는 그런 명령점이 없으면 null(기계 보정 진입에서 닿지 않음, 블록을 그 자리에서 끝낼 수 없음).
 * 줄은 각 풀이의 nc 기준이다(화면이 붙이는 첫 줄 주석은 세지 않는다)
 */
data class ProgAt(val direct: Pt, val directLine: Int, val machine: Pt?, val machineLine: Int?)

/** 닿는 곳([HolderHit])의 처음 · 마지막을 두 프로그램 좌표 · 줄로 */
data class HitAt(val hit: HolderHit, val first: ProgAt, val last: ProgAt)

/**
 * 안전 끝점: 진입부터 경로를 따라 여유가 처음으로 안전 기준 아래로 떨어지기 바로 앞(1e-6 mm 안).
 * [element] −2 = 기계 보정 진입의 Z20. 자리에 선 자세(직접 보정은 [StopWarning.UNSAFE_AT_ENTRY]), −1 = 진입 이동(기계 보정은 진입 블록 —
 * Z20. 자리 다음부터 첫 깎는 줄까지, 어느 블록인지는 [at] 의 machineLine), k = program.segs[k],
 * [t] = 요소(기계 보정 진입은 그 블록) 길이 비율, [pose] = 그 자세(기계 보정 진입은 drawn = 그 명령점).
 * [part] · [opposite] = 바로 다음에 기준을 넘는 부분과 벽(맞은편 벽이면 true), [margin] = 이 자세에서 그 부분의 여유(mm, 직선 거리 — 깎는 면은 뺀다,
 * 공구대는 Z, 겹치면 반경 겹침) — 기준(안전 여유, 인서트의 깎는 쪽 벽은 0)과 같다. 겹침으로 넘어가는 곳이면 기준보다 클 수 있다.
 * [at] = 두 프로그램 좌표 · 줄: 기계 보정은 그 블록을 여기서 끝내면 제어기가 법선으로 보정해 노즈가 이 자세에 서는 도면 명령점
 * (그 자세에서 블록을 끝낼 수 없으면 null), 기계 보정 진입이면 그 명령점이고 direct 는 그 자세의 실제 인선 자리.
 * [gap] = 이 자세에서 바와 소재 사이 직선(유클리드) 거리(mm), [gapAt] = 그 소재 쪽 가장 가까운 점(맞은편 벽이면 x < 0) — 깎는 면도 넣은 그대로
 * (예: 막힌 바닥을 깎는 바 앞면 0.226). 기준은 [margin](깎는 면을 뺀 것)
 */
data class SafeEnd(
    val element: Int,
    val t: Double,
    val pose: HolderPose,
    val part: HolderPart,
    val opposite: Boolean,
    val margin: Double,
    val at: ProgAt,
    val gap: Double,
    val gapAt: Pt,
)

/** 후퇴: [LIFT] = X 를 축 쪽으로 조금 뺀 뒤 Z 로 진입점 Z 까지 G00, [REVERSE] = 온 길을 거꾸로 이송(G01 · G02/G03)으로 진입점까지 */
enum class RetractKind { LIFT, REVERSE }

/**
 * 안전 끝점에서 끊은 프로그램 하나. [nc] = G00 진입부터 후퇴까지 전체(첫 줄 주석 없음). 앞 [kept] 줄은 원래 nc 와 같은 글,
 * 그 뒤 마지막 블록(들), [retractFrom] 줄부터 후퇴. [element] · [t] = 마지막 블록 끝에서 실제 공구 자세가 놓인 곳
 * (노즈 중심 경로 = 프로그램 경로의 요소와 비율), [stop] = 마지막 블록 끝 좌표(쓴 글을 다시 읽은 값 — 직접 보정은 가상 인선,
 * 기계 보정은 도면 명령점), [pose] = 그때 실제 공구 자세, [retractClear] = 후퇴 동안 가장 좁은 여유(mm, 빼기 시작할 때 벽에
 * 붙어 있는 인서트의 깎는 쪽은 뺀다. 기계 보정 후퇴를 여유 0 으로 골랐으면 안전 여유로는 모자란 노즈 원 거리도 든다), [lack] = 안전 끝점까지 노즈 경로를 따라 덜 간 길이(mm — 반올림 되돌리기, 기계 보정은 블록을
 * 그 자리에서 끝낼 수 없어 앞 요소 끝에서 멈춘 것까지)
 */
data class HolderCut(
    val nc: List<String>,
    val kept: Int,
    val retractFrom: Int,
    val element: Int,
    val t: Double,
    val stop: Pt,
    val pose: HolderPose,
    val retract: RetractKind,
    val retractClear: Double,
    val lack: Double,
)

/**
 * [UNSAFE_AT_ENTRY] = 직접 보정 프로그램의 G00 진입점에 선 자세부터 안전 기준이 모자라 가공할 수 없다, [NOTHING_CUT] = 직접 보정이 첫 가공점에
 * 닿기 전에 멈춰 깎는 것이 없다, [MACHINE_ENTRY] = 기계 보정 프로그램이 제 진입(Z20. 자리 또는 보정 중 진입 블록)에서 안전 기준이 모자라
 * 깎을 수 없다([HolderPlan.machineEnd] 가 −2 · −1), [RETRACT_TIGHT] = 후퇴 여유가 안전 여유보다 좁다(겹치지는 않는다),
 * [MACHINE_EXIT] = 기계 보정 프로그램의 형상은 끝까지 안전한데 그 원래 끝(빼는 줄 → `G40` → `G0Z200.`)에서 보링바가 소재에 닿는다 — 경로 끝을 끝점으로
 * 삼아([HolderPlan.machineEnd] = 마지막 요소 t 1) 빠지는 길만 바꾼 NC 를 [HolderPlan.machine] 에 낸다(못 바꾸면 null)
 */
enum class StopWarning { UNSAFE_AT_ENTRY, NOTHING_CUT, MACHINE_ENTRY, RETRACT_TIGHT, MACHINE_EXIT }

/**
 * 보링바 간섭 검사 + 안전 끝점. [result] = [HolderCheck.check] 그대로(직접 보정 경로에서 닿는 곳), [hits] = 그 닿는 곳의 두 프로그램 좌표 · 줄
 * (같은 순서 — 진입점 · 진입 이동의 기계 보정 좌표는 기계 보정 프로그램 자기 진입([machineEntry])에서 같은 부분이 닿는 곳, 안 닿으면 null),
 * [end] = 직접 보정 프로그램의 안전 끝점(경로 전체가 안전하거나 검사를 안 했거나 진입점부터 안 되면 null), [direct] · [machine] = 잘린 직접 보정 ·
 * 기계 보정 NC(그 프로그램의 끝점이 없거나 깎는 것이 없으면 null, 기계 보정은 도면 좌표 풀이가 없어도 null).
 * [machineEnd] = 기계 보정 프로그램의 안전 끝점: 진입(Z20. 자리 −2 · 진입 블록 −1)은 그 프로그램 자기 경로로 보고, 진입이 되면 형상은
 * 직접 보정과 같은 노즈 경로라 같은 끝점(도면 좌표 풀이가 없거나 끝까지 안전하면 null — 원래 끝에서 닿으면 경로 끝, [StopWarning.MACHINE_EXIT]).
 * [machineEntry] = 기계 보정 프로그램 자기 진입(Z20. 자리에 선 자세 −2 · 진입 블록 −1)에서 닿는 곳 — 좌표는 그 명령점(기계 보정 화면의 간섭 목록은
 * 이것 + [hits] 의 형상 요소). 도면 좌표 풀이가 없으면 null
 */
data class HolderPlan(
    val result: HolderResult,
    val safe: Double,
    val end: SafeEnd?,
    val hits: List<HitAt>,
    val direct: HolderCut?,
    val machine: HolderCut?,
    val warnings: List<StopWarning>,
    val machineEnd: SafeEnd?,
    val machineEntry: List<HitAt>?,
)

/**
 * 보링바 안전 끝점과 잘린 NC. 간섭이 나는 곳까지 가지 않고 그 직전 안전한 자세에서 멈춘 뒤 빠진다(나머지는 다른 공구로).
 *
 * 안전 기준(사용자 결정 2026-10-27 · 10-28): 바 · 공구대 · 최소 가공경 · 인서트의 맞은편 벽 쪽은 공구 단면과 소재 사이 직선(유클리드) 거리 ≥ 안전 여유
 * ([SAFE_DEFAULT] 0.5 mm, [clearOf]), 인서트의 깎는 쪽 벽은 겹치지 않음(≥ −[CUT_TOL]) — 날은 노즈 접점 옆에서 원래 벽에 붙어 있어(직선 벽에서 r(1 − cos κr′))
 * 안전 여유를 둘 수 없다. 예외: 지금 깎는(또는 오목 이음을 지나 곧 깎을) 주절삭날 쪽 직선 면과 그 연장(축 너머 반쪽)에는 공구가 겹치지 않음만 본다
 * ([Bore.faceFlags] — 막힌 바닥을 깎는 바 앞면은 주절삭날 연장선이라 면에서 공구 모양만큼만 떨어진다). 겹침 깊이 · 닿음은 반경 겹침이다.
 *
 * 두 프로그램의 진입은 다르다: 직접 보정은 G00 점의 인선에서 첫 보정점으로 곧게, 기계 보정(회사 꼴)은 `G41`/`G42` 혼자 줄 뒤 `G0X…Z20.` 자리에서
 * 보정 중 진입 블록들(노즈는 보정선 위, 꺾임은 교점 — 팁과 상관없다)로 첫 점까지. 그래서 진입은 프로그램마다 따로 보고, 형상은 같은 노즈 경로다.
 * 기계 보정은 끝(빼는 줄 → `G40` → `G0Z200.`)도 프로그램에 있어 따로 본다([StopWarning.MACHINE_EXIT]).
 *
 * 움직임의 여유: 볼록 조각을 직선으로 옮긴 자리 전체 = 처음 · 끝 자리의 볼록 껍질이라, 껍질의 여유가 그 움직임 동안의 정확한 최솟값이다.
 * 원호 구간은 두 끝과 두 끝 접선의 교점으로 덮은 껍질(조금 작게 = 안전 쪽)을 쓴다. 그래서 표본 사이에서 잠깐 기준을 넘는 곳도 놓치지 않는다
 */
object HolderStop {
    /** 안전 여유 기본값(mm) */
    const val SAFE_DEFAULT = 0.5

    /** 인서트의 깎는 쪽 벽은 이만큼(mm)까지 겹쳐도 겹치지 않은 것으로 — 노즈 경로 계산 오차(1e-7) */
    const val CUT_TOL = 1e-6

    /** 0.001 로 쓴 글을 다시 읽은 자세는 기준보다 이만큼(mm)까지 모자라도 받는다 */
    const val TEXT_TOL = 1e-4

    /** 후퇴 때 X 를 축 쪽으로 빼는 양(반경, mm). 안전 여유가 더 크면 그만큼 */
    const val LIFT = 0.2

    /** 안전 끝점을 좁히는 끝 폭(mm) */
    internal const val STOP_TOL = 1e-7

    /** 기준과 같은 여유는 안전 — 부동소수 오차 */
    internal const val OK_TOL = 1e-9

    /** 반올림 한 칸(mm) */
    internal const val GRID = 1e-3

    /** 안전 여유 칸 값이 맞나(0 이상, 한도 안) */
    fun safeOk(safe: Double): Boolean = safe.isFinite() && safe >= 0 && safe <= MAX_MM

    /**
     * [sol] = 직접 보정(G40) 풀이, [drawn] = 같은 입력의 기계 보정 프로그램 풀이(`RoundCalc.machine` — 도면 좌표 · 회사 꼴, 없으면 null.
     * 진입 명령점(`approach`)이 없는 풀이 — 노즈R 0 으로 푼 직접 보정 꼴 — 는 기계 보정 프로그램이 아니라 null 처럼 본다), [r] = 노즈R,
     * [faceAtStart] · [bar] = [HolderCheck.check] 와 같다, [safe] = 안전 여유(mm). 안전 여유 값이 틀리면 result.skip = BAD_INPUT
     */
    fun plan(sol: RoundSolution, drawn: RoundSolution?, r: Double, faceAtStart: Boolean, bar: BoringBar, safe: Double = SAFE_DEFAULT): HolderPlan {
        // 가공 방향을 뒤집은 풀이는 보지 않는다(HolderSkip.FLIPPED) — 화면이 기본 방향으로 돌려 확인하라고 알린다.
        // 아래(와 `replay`)는 `approach` 가 있으면 기계 보정 프로그램으로 본다 — 뒤집은 풀이는 직접 보정도 `approach`(접근 점)를 갖고 기계 보정은 진입 뒤 이송 점
        // (`leadIn`)이 더 있으므로, 뒤집은 방향의 검사를 넣을 때는 그 가름부터 따로 둘 것(지금은 여기서 돌아가 닿지 않는다)
        if (sol.flip) {
            return HolderPlan(HolderResult(HolderSkip.FLIPPED, emptyList(), null, null, null), safe, null, emptyList(), null, null, emptyList(), null, null)
        }
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, r, faceAtStart, bar)
        if (res.skip != null) return HolderPlan(res, safe, null, emptyList(), null, null, emptyList(), null, null)
        if (!safeOk(safe)) {
            return HolderPlan(
                HolderResult(HolderSkip.BAD_INPUT, emptyList(), null, null, null), safe, null, emptyList(), null, null, emptyList(), null, null,
            )
        }
        val machine = drawn?.takeIf { it.approach.isNotEmpty() && it.drawing.segs.size == sol.drawing.segs.size && it.program.segs.size == sol.program.segs.size }
        return StopJob(sol, machine, r, Bore(sol.drawing, faceAtStart), bar, safe).plan(res)
    }
}

/** 마지막 블록까지 쓴 글([kept] = 원래 nc 그대로인 앞 줄 수)과 그 글을 다시 읽은 움직임 */
private class Written(val nc: NcLines, val kept: Int, val read: List<Seg?>)

/** 안전 끝점 · 잘린 NC 계산 한 번 */
internal class StopJob(
    private val sol: RoundSolution,
    private val drawn: RoundSolution?,
    private val r: Double,
    private val bore: Bore,
    bar: BoringBar,
    private val safe: Double,
) {
    val tool = BarShape(bar, r, bore.reverse)
    val tv = NoseComp.tipVector(sol.tip, r)

    /** 깎는 동안의 안전 기준 */
    val cut = Need(safe, -HolderStop.CUT_TOL)

    /**
     * 쓴 글을 다시 읽은 자세의 기준: 바 · 공구대 · 최소 가공경 · 인서트 맞은편은 안전 여유 − [HolderStop.TEXT_TOL](모형 차이 1e-5 를 남긴다).
     * 인서트의 깎는 쪽은 보지 않는다 — 날은 원래 벽에 붙어 있어 쓴 글의 반올림(0.0007 mm)만큼 들어갈 수 있고(NC 전체가 그렇다),
     * 면 바로 옆에서는 Z 반올림 0.0005 에 반경 겹침이 크게 뛴다. 정확한 경로에서는 겹치지 않음을 이미 봤다
     */
    private val text = Need(safe - HolderStop.TEXT_TOL + 1e-5, Double.NEGATIVE_INFINITY)

    /** 가상 인선 움직임(−1 = 진입 이동, k = program.segs[k]), 길이 0 은 뺀다 */
    private val moves: List<Pair<Int, Seg>> =
        (listOf<Pair<Int, Seg>>(-1 to Line(sol.entry, sol.program.segs.first().start)) + sol.program.segs.withIndex().map { it.index to it.value })
            .filter { lengthOf(it.second) > EPS }

    /** 움직임마다 시작까지의 경로 길이(마지막 = 전체) */
    private val cum = DoubleArray(moves.size + 1).also { c -> for (i in moves.indices) c[i + 1] = c[i] + lengthOf(moves[i].second) }

    fun plan(res: HolderResult): HolderPlan {
        val entryHits = machineEntry()
        val hits = res.hits.map { h ->
            if (h.element >= 0) {
                HitAt(h, shapeAt(h.element, h.first), shapeAt(h.element, h.last))
            } else {
                // 진입은 두 프로그램의 경로가 다르다: 기계 보정 좌표는 그 프로그램 자기 진입에서 같은 부분이 닿는 곳(안 닿으면 null)
                val m = entryHits.firstOrNull { it.hit.element == h.element && it.hit.part == h.part }
                fun of(p: HolderPose, q: ProgAt?) = ProgAt(p.tip, sol.lineOf(h.element, p.tip), q?.machine, q?.machineLine)
                HitAt(h, of(h.first, m?.first), of(h.last, m?.last))
            }
        }
        var shape = firstEnd(shape = true)
        // 깎는 면 사슬은 끝내 깎을 면까지만: 끝점 요소 뒤 면의 예외를 빼고 다시 찾는다. 예외를 빼면 여유가 줄기만 해 끝점은 앞으로만 간다 — 요소가 그대로면 끝
        while (shape != null && shape.first.element < faceLimit) {
            val before = faces
            faceLimit = shape.first.element
            if (faces.indices.all { faces[it] contentEquals before[it] }) break
            shape = firstEnd(shape = true)
        }
        val warnings = mutableListOf<StopWarning>()
        // 직접 보정: G00 점의 자세 → 진입 이동 → 형상
        var end: SafeEnd? = null
        var direct: HolderCut? = null
        if (clear(listOf(sol.entry - tv), -2).slack(cut) < -HolderStop.OK_TOL) {
            warnings += StopWarning.UNSAFE_AT_ENTRY
        } else {
            val e = firstEnd(shape = false) ?: shape
            if (e != null) {
                end = e.first
                if (e.first.element >= 0) direct = directCut(e.second)
                if (direct == null) warnings += StopWarning.NOTHING_CUT
            }
        }
        // 기계 보정: Z20. 자리의 자세 → 진입 블록 → 형상(직접 보정과 같은 노즈 경로) → (끝까지 되면) 원래 끝
        var mEnd = if (drawn == null) null else machineEntryEnd() ?: shape?.first
        var mStop = shape?.second
        // 형상이 끝까지 안전하면 원래 끝(빼는 줄 → G40 → G0Z200.)도 본다 — 닿으면 경로 끝에서 끊은 것처럼 빠지는 길만 바꾼다
        if (drawn != null && mEnd == null) {
            originalExitHit()?.let { (part, opp) ->
                mEnd = pathEnd(part, opp)
                mStop = cum[moves.size]
                warnings += StopWarning.MACHINE_EXIT
            }
        }
        val me = mEnd
        val machine = if (me != null && me.element >= 0) machineCut(mStop!!) else null
        if (me != null && me.element < 0) warnings += StopWarning.MACHINE_ENTRY
        if (listOfNotNull(direct, machine).any { it.retractClear < safe - HolderStop.TEXT_TOL }) warnings += StopWarning.RETRACT_TIGHT
        return HolderPlan(res, safe, end, hits, direct, machine, warnings, me, if (drawn == null) null else entryHits)
    }

    /**
     * 직접 보정의 진입 이동(shape = false) 또는 형상 요소(true)를 따라 기준이 처음 깨지는 곳과 그 경로 길이. 없으면 null.
     * 형상 끝점의 기계 보정 좌표는 블록을 그 자리에서 끝낼 명령점, 진입 이동은 직접 보정에만 있어 null
     */
    private fun firstEnd(shape: Boolean): Pair<SafeEnd, Double>? {
        for ((i, mv) in moves.withIndex()) {
            val (k, s) = mv
            if ((k >= 0) != shape) continue
            val ns = noseOf(s)
            val (a, b) = firstShort(ns, cut, k) ?: continue
            val n = pointOn(ns, a)
            val (part, opp) = clear(span(ns, a, b), k).worst(cut)
            val p = poseAt(k, n)
            val at = if (k < 0) ProgAt(p.tip, sol.lineOf(k, p.tip), null, null) else safeAt(k, n)
            val (g, gp) = barGap(n)
            return SafeEnd(k, a, p, part, opp, clear(listOf(n), k).margin(part, opp), at, g, gp) to (cum[i] + a * lengthOf(s))
        }
        return null
    }

    // ------------------------------------------------------------------ 기계 보정의 진입

    /**
     * 기계 보정 프로그램의 진입 블록 하나(모두 요소 −1): [line] = 그 풀이 nc 의 줄, 명령점 [cmdFrom] → [cmdTo], 그동안 노즈 [noseFrom] → [noseTo]
     * (명령점과 같은 비율로 곧게). 회사 꼴(`RoundCalc.machine`)은 `G41` 혼자 줄 뒤 Z20. 자리 → (X 맞춤) → 진입점 → 첫 점이 모두 보정 중 블록이라
     * 노즈는 보정선 위(꺾임은 두 보정선의 교점 — `CompFrame.noses`, 형식 · 팁과 상관없다)
     */
    private class MBlock(val line: Int, val cmdFrom: Pt, val cmdTo: Pt, val noseFrom: Pt, val noseTo: Pt) {
        val nose = Line(noseFrom, noseTo)
        fun cmd(u: Double) = cmdFrom + (cmdTo - cmdFrom) * u
        fun noseAt(u: Double) = if (u <= 0 || lengthOf(nose) <= EPS) noseFrom else pointOn(nose, u)
    }

    /** 기계 보정 진입 명령점: Z20. 자리 → (X 맞춤) → 진입점 → 첫 점. 도면 좌표 풀이가 없으면 null([HolderStop.plan] 이 회사 꼴만 넘긴다) */
    private val mCmds: List<Pt>? = drawn?.let { it.approach + it.program.segs.first().start }

    /** 진입 G0 줄들(혼자 줄 `G41` 은 빼고 — 첫째가 Z20. 자리를 쓴 줄) + 첫 깎는 줄. 명령점 i → i + 1 블록은 [i + 1] 번째 줄, Z20. 자리의 자세(−2)는 [0] 번째 줄(`G0X…Z20.`) */
    private val mLines: List<Int> = drawn?.let { d -> d.ncRefs.indices.filter { d.ncRefs[it].element == -2 }.drop(1) + d.ncRefs.indices.filter { d.ncRefs[it].element == -1 } }.orEmpty()

    /** 그 명령점마다 노즈 중심 — 처음은 Z 줄의 법선, 끝(첫 점)은 첫 요소와 한 직선이라 법선 */
    private val mNoses: List<Pt>? = mCmds?.let { c ->
        val first = drawn!!.program.segs.firstOrNull { lengthOf(it) > EPS } ?: return@let null
        CompFrame.noses(c, sol.side, r, then = CompFrame.startDir(first))
    }

    /** 기계 보정 진입의 처음 자세(Z20. 자리)의 노즈 */
    private val mNose0: Pt? = mNoses?.first()

    /** 기계 보정 진입 블록들(Z20. 자리 다음, 첫 형상 블록 앞). 도면 좌표 풀이가 없으면 빈 목록 */
    private val mBlocks: List<MBlock> = run {
        val c = mCmds ?: return@run emptyList()
        val n = mNoses ?: return@run emptyList()
        c.indices.drop(1).map { i -> MBlock(mLines.getOrElse(i) { mLines.last() }, c[i - 1], c[i], n[i - 1], n[i]) }
    }

    /** 기계 보정 진입 블록 [b] 비율 u 의 자세: tip = 실제 인선 자리, drawn = 명령점 */
    private fun mPose(b: MBlock, u: Double): HolderPose = b.noseAt(u).let { HolderPose(it + tv, it, b.cmd(u)) }

    /** 진입 처음(Z20. 자리)의 자세 */
    private fun mPose0(): HolderPose = HolderPose(mNose0!! + tv, mNose0, mCmds!!.first())

    private fun mProg(k: Int, p: HolderPose, line: Int) = ProgAt(p.tip, sol.lineOf(k, p.tip), p.drawn, line)

    /**
     * 기계 보정 진입 자세의 부분별 여유: [HolderCheck.margins] 에 노즈 원을 더한다 — 인서트 조각의 노즈는 현 T1–T2 라(형상에서는 보정으로 벽에서 r)
     * 보정이 덜 걸린 G00 점 · 시작 블록에서 노즈 원이 벽 · 입구 모서리를 파는 것을 못 본다. 노즈 원 겹침은 인서트로 센다(자리는 노즈 중심)
     */
    private fun entryMargins(nose: Pt): Margins {
        val m = HolderCheck.margins(bore, tool, nose)
        val g = noseSweep(nose, nose)
        return if (g < m.insert.m) Margins(Gap(g, nose.z, nose.x), m.bar, m.turret, m.minBore) else m
    }

    /**
     * 기계 보정 프로그램 자기 진입에서 닿는 곳(여유 < −[HolderCheck.CONTACT_TOL]): Z20. 자리에 선 자세(−2) · 진입 블록마다(−1).
     * 고르기는 [HolderCheck.check] 와 같다 — [HolderCheck.STEP] 간격, 처음 · 마지막은 이분법 40번, 가장 깊은 곳은 표본, 비율은 그 블록 위.
     * 인서트에는 노즈 원도 든다([entryMargins])
     */
    private fun machineEntry(): List<HitAt> {
        if (mBlocks.isEmpty()) return emptyList()
        val out = mutableListOf<HitAt>()
        fun scan(k: Int, n: Int, b: MBlock?) {
            val line = b?.line ?: mLines.first()
            fun pose(u: Double) = if (b == null) mPose0() else mPose(b, u)
            val ts = DoubleArray(n + 1) { if (n == 0) 0.0 else it.toDouble() / n }
            val poses = ts.map(::pose)
            val ms = poses.map { entryMargins(it.nose) }
            val found = mutableListOf<HolderHit>()
            for (part in HolderPart.entries) {
                fun touch(m: Margins) = m[part].m < -HolderCheck.CONTACT_TOL
                val jf = ms.indexOfFirst(::touch)
                if (jf < 0) continue
                val jl = ms.indexOfLast(::touch)
                fun edge(off: Double, on: Double): Double {
                    var a = off
                    var c = on
                    repeat(40) {
                        val mid = (a + c) / 2
                        if (touch(entryMargins(pose(mid).nose))) c = mid else a = mid
                    }
                    return c
                }
                val from = if (jf == 0) 0.0 else edge(ts[jf - 1], ts[jf])
                val to = if (jl == n) ts[n] else edge(ts[jl + 1], ts[jl])
                val jw = ms.indices.minBy { ms[it][part].m }
                val g = ms[jw][part]
                val w = poses[jw]
                found += HolderHit(k, part, pose(from), pose(to), w, from, to, -g.m, g.x < 0, g.z - (w.nose + tool.ref).z)
            }
            out += found.sortedBy { it.fromT }.map { HitAt(it, mProg(k, it.first, line), mProg(k, it.last, line)) }
        }
        scan(-2, 0, null)
        for (b in mBlocks) {
            val len = lengthOf(b.nose)
            if (len > EPS) scan(-1, max(1, ceil(len / HolderCheck.STEP).toInt()), b)
        }
        return out
    }

    /**
     * 기계 보정 프로그램 자기 진입에서 기준이 처음 깨지는 곳: G00 점의 자세(−2) · 진입 블록(−1, t = 그 블록 위 비율 · 줄은 at.machineLine). 진입이 되면 null.
     * 기준은 형상과 같고(진입이라 깎는 면 예외는 없다), 노즈 원도 소재에 들어가지 않아야 한다(인서트 깎는 쪽과 같은 '겹치지 않음' — 그 끝점은 인서트 ·
     * 깎는 쪽, 여유 = 노즈 원 거리)
     */
    private fun machineEntryEnd(): SafeEnd? {
        if (mBlocks.isEmpty()) return null
        fun end(k: Int, p: HolderPose, u: Double, line: Int, c: Clear, noseBad: Boolean): SafeEnd {
            val (part, opp) = if (noseBad) HolderPart.INSERT to false else c.worst(cut)
            val margin = if (noseBad) noseSweep(p.nose, p.nose) else clear(listOf(p.nose), k).margin(part, opp)
            val (g, gp) = barGap(p.nose)
            return SafeEnd(k, u, p, part, opp, margin, mProg(k, p, line), g, gp)
        }
        val n0 = mNose0!!
        val c0 = clear(listOf(n0), -2)
        val nose0 = noseSweep(n0, n0) < -HolderStop.CUT_TOL
        if (nose0 || c0.slack(cut) < -HolderStop.OK_TOL) return end(-2, mPose0(), 0.0, mLines.first(), c0, nose0 && c0.slack(cut) >= -HolderStop.OK_TOL)
        for (b in mBlocks) {
            if (lengthOf(b.nose) <= EPS) continue
            val (a, e) = firstShort(b.nose, cut, -1, nose = true) ?: continue
            val c = clear(span(b.nose, a, e), -1)
            return end(-1, mPose(b, a), a, b.line, c, c.slack(cut) >= -HolderStop.OK_TOL)
        }
        return null
    }

    // ------------------------------------------------------------------ 기계 보정 원래 끝

    /**
     * 기계 보정 원래 프로그램의 끝(마지막 형상 블록 뒤 빼는 줄들 → `G40` → `G0Z200.`)에서 보링바가 소재에 닿나(겹침 — 인서트 깎는 쪽은 첫 빼는 줄에서
     * 벽에 붙어 있어 뺀다). 노즈 자리는 [machineExit] 와 같은 모형: 다시 읽은 글로 마지막 블록과 첫 빼는 줄의 보정선 교점, 빼는 줄 사이 꺾임은 교점,
     * 마지막 빼는 줄 끝은 법선, 그 뒤 형식 C 의 G40 옮김(→ 끝점 − 팁 벡터)과 +Z, 형식 A · B 의 비스듬한 `G0Z200.`(껍질이라 Z200. 까지 정확).
     * 닿으면 그 부분 · 벽(맞은편이면 true), 아니면 null. `CompFrame` 은 보링바를 모르고 빼는 줄 · G40 자리를 정한다(예: 축 너머 X−2r 에서 끝나는 바닥 면 —
     * 팁 2 의 G40 옮김이 노즈를 반경 −2r 로 보내 바가 맞은편 벽에 닿는다). 첫 빼는 줄 뒤의 움직임은 **노즈 원**도 본다(인서트 조각의 노즈는 현 T1–T2 라
     * 노즈 원이 벽을 파는 것을 못 본다 — 3점호 끝 접선을 따라 뺀 뒤 G40 옮김이 늘인 구멍 벽을 0.001 mm 쯤 판다, 11-04 검증): 소재까지 거리 − r < −1e-4 면 인서트
     */
    private fun originalExitHit(): Pair<HolderPart, Boolean>? {
        val d = drawn ?: return null
        val n = d.drawing.segs.size
        val read = readNc(d.nc)
        val first = d.ncRefs.indexOfFirst { it.element == n }
        val iG40 = d.nc.indexOfLast { it == "G40" }
        if (first < 1 || iG40 <= first) return null
        val last = read[first - 1] ?: return null
        val exits = (first until iG40).map { read[it] as? Line ?: return null }
        fun dir(l: Line) = (l.end - l.start).unit()
        val noses = mutableListOf(cornerWith(last, exits.first()) ?: return null)
        for (i in 0 until exits.size - 1) noses += CompFrame.corner(exits[i].end, dir(exits[i]), dir(exits[i + 1]), sol.side, r)
        val xe = exits.last().end
        val ne = xe + exitNormal(dir(exits.last())) * r
        noses += ne
        val nC = xe - tv
        val far = Pt(CompFrame.RETRACT_Z, xe.x) - tv
        val needCut = Need(-HolderStop.TEXT_TOL, Double.NEGATIVE_INFINITY)
        val needAir = Need(-HolderStop.TEXT_TOL, -HolderStop.TEXT_TOL)
        val moves = noses.zipWithNext().mapIndexed { i, (a, b) -> Triple(a, b, if (i == 0) needCut else needAir) } +
            listOf(Triple(ne, nC, needAir), Triple(nC, far, needAir), Triple(ne, far, needAir))
        for ((i, m) in moves.withIndex()) {
            val (a, b, need) = m
            val c = textClear(listOf(a, b), need, n - 1)
            if (c.slack(need) < -HolderStop.OK_TOL) return c.worst(need)
            if (i > 0 && noseSweep(a, upTo(a, b, noseTop(0.0))) < -HolderStop.TEXT_TOL) return HolderPart.INSERT to false
        }
        return null
    }

    /** 경로 끝(마지막 요소 t 1)의 끝점 — [StopWarning.MACHINE_EXIT] 일 때 기계 보정 끝점. 부분 · 벽은 원래 끝에서 닿는 것, 여유는 이 자세에서 그 부분의 여유 */
    private fun pathEnd(part: HolderPart, opp: Boolean): SafeEnd {
        val (k, s) = moves.last()
        val nose = pointOn(noseOf(s), 1.0)
        val (g, gp) = barGap(nose)
        return SafeEnd(k, 1.0, poseAt(k, nose), part, opp, clear(listOf(nose), k).margin(part, opp), safeAt(k, nose), g, gp)
    }

    // ------------------------------------------------------------------ 여유

    /**
     * 깎는 면 사슬(앞 요소 면)이 닿을 수 있는 마지막 요소. [plan] 이 안전 끝점 요소로 좁힌다 — 끝점이 그 면 앞이면 그 면은 끝내 깎지 않으니
     * 예외가 아니다(사용자 결정 2026-10-28 '그 밖은 예외 없음', 10-28 검증). 검사기는 계획의 끝점 요소를 넣어 같은 기준으로 본다
     */
    internal var faceLimit = Int.MAX_VALUE
        set(v) {
            field = v
            faces = facesUpTo(v)
        }

    private fun facesUpTo(limit: Int): List<BooleanArray?> = sol.drawing.segs.indices.map { bore.faceFlags(sol.drawing, sol.side, tool, it, limit) }

    /** 경로 요소 k 의 깎는 면 깃발([Bore.faceFlags], 사슬은 [faceLimit] 까지) — 요소마다 한 번 */
    private var faces: List<BooleanArray?> = facesUpTo(Int.MAX_VALUE)

    /**
     * 노즈 중심을 [noses] 에 차례로 둔 움직임 전체(볼록 껍질)의 부분별 여유([clearOf]). 하나면 그 자세. [k] = 그 움직임의 경로 요소 —
     * 형상 요소(≥ 0)면 그 깎는 면을 바 · 최소 가공경 구간의 직선 거리에서 뺀다(진입 −2 · −1 은 예외 없음)
     */
    fun clear(noses: List<Pt>, k: Int): Clear = clearOf(bore, tool, noses, faces.getOrNull(k))

    /**
     * 쓴 글 검사는 Z 를 반 칸(0.0005 mm) 앞뒤로 옮긴 자세 가운데 좋은 쪽으로 본다: Z 반올림은 반경 여유로 재면 면 · 가파른 벽에서 크게 뛴다
     * (면에 붙어 깎는 자세 — 최소 가공경 구간의 앞 끝, κr 90° 바 앞 — 가 0.0005 넘어가면 막힌 바닥 너머(반경 0)를 본다). 실제 거리는 0.0005 다.
     * X 반올림은 그대로 본다
     */
    private val textShifts = listOf(Pt(0.0, 0.0), Pt(HolderStop.GRID / 2, 0.0), Pt(-HolderStop.GRID / 2, 0.0))

    /** 쓴 글 자세(또는 움직임)의 여유: [textShifts] 가운데 [need] 에 가장 좋은 것 */
    private fun textClear(noses: List<Pt>, need: Need, k: Int): Clear = textShifts.map { sh -> clear(noses.map { it + sh }, k) }.maxBy { it.slack(need) }

    /**
     * 노즈 중심 움직임 [s] 를 따라 [need] 가 처음 깨지는 칸 (a, b) — 요소 비율, 폭 [HolderStop.STOP_TOL] mm 이하, a 까지는 안전.
     * 끝까지 안전하면 null. 구간 껍질의 여유가 기준 이상이면 그 구간 전체가 안전하다(직선은 정확, 원호는 안전 쪽) — 아니면 반으로 나눈다.
     * [shifts] 가 여럿이면(쓴 글) 그 가운데 하나로 구간 전체가 되면 된다
     */
    private fun firstShort(s: Seg, need: Need, k: Int, shifts: List<Pt> = NO_SHIFT, nose: Boolean = false): Pair<Double, Double>? {
        val len = lengthOf(s)
        fun go(a: Double, b: Double): Pair<Double, Double>? {
            val pts = span(s, a, b)
            // nose = 노즈 원도 소재에 들어가지 않아야(직선 움직임만 — 기계 보정 진입)
            val noseOk = !nose || noseSweep(pointOn(s, a), pointOn(s, b)) >= -HolderStop.CUT_TOL
            if (noseOk && shifts.any { sh -> clear(pts.map { it + sh }, k).slack(need) >= -HolderStop.OK_TOL }) return null
            if ((b - a) * len <= HolderStop.STOP_TOL) return a to b
            val m = (a + b) / 2
            return go(a, m) ?: go(m, b)
        }
        // 원호는 45° 조각부터(껍질이 너무 크지 않게)
        val n = if (s is Arc) max(1, ceil(s.sweepRad() / (PI / 4)).toInt()) else 1
        for (i in 0 until n) go(i.toDouble() / n, (i + 1).toDouble() / n)?.let { return it }
        return null
    }

    /** 쓴 글 움직임 동안 가장 좁은 여유(보고용, [textShifts] 가운데 좋은 쪽): 직선은 정확, 원호는 0.05 mm 조각마다 덮는 껍질(조금 작게 = 안전 쪽) */
    private fun least(ns: Seg, withInsCut: Boolean, k: Int): Double {
        fun of(pts: List<Pt>) = textShifts.maxOf { sh -> clear(pts.map { it + sh }, k).least(withInsCut) }
        if (ns is Line) return of(listOf(ns.start, ns.end))
        val n = maxOf(1, ceil(lengthOf(ns) / 0.05).toInt(), ceil(4 * (ns as Arc).sweepRad() / PI).toInt())
        return (0 until n).minOf { of(span(ns, it.toDouble() / n, (it + 1).toDouble() / n)) }
    }

    // ------------------------------------------------------------------ 자리 · 좌표

    private fun noseOf(s: Seg): Seg = s.moved(tv * -1.0)

    private fun poseAt(k: Int, nose: Pt) = HolderPose(nose + tv, nose, HolderCheck.drawnOf(sol.drawing, k, nose))

    /** 요소 k 의 비율 t 자리의 경로 길이. 길이 0 요소는 다음 움직임의 시작 */
    private fun pos(k: Int, t: Double): Double {
        val i = moves.indexOfFirst { it.first == k }
        if (i >= 0) return cum[i] + t * lengthOf(moves[i].second)
        val j = moves.indexOfFirst { it.first > k }
        return if (j < 0) cum[moves.size] else cum[j]
    }

    /** 경로 길이 s 의 자리(요소, 비율). 움직임 시작과 같은 s 는 앞 움직임의 끝. s ≤ 0 이면 null */
    private fun at(s: Double): Pair<Int, Double>? {
        if (s <= 0) return null
        val i = (0 until moves.size).firstOrNull { s <= cum[it + 1] } ?: (moves.size - 1)
        return moves[i].first to ((s - cum[i]) / (cum[i + 1] - cum[i])).coerceIn(0.0, 1.0)
    }

    /** 형상 요소 k 의 닿는 곳 자세 → 두 프로그램. 기계 보정은 노즈가 닿는 도면 점(같은 노즈 경로라 그 블록의 명령점) */
    private fun shapeAt(k: Int, p: HolderPose): ProgAt =
        ProgAt(p.tip, sol.lineOf(k, p.tip), drawn?.let { p.drawn }, drawn?.lineOf(k, p.drawn))

    /**
     * 형상 요소 k 의 안전 끝점 → 두 프로그램. 기계 보정은 블록을 거기서 끝내면 노즈가 그 자세에 서는 도면 명령점 — 그 자세에서 블록을 끝낼 수
     * 없으면(꼭짓점 둘레 원호, 볼록 모서리에서 늘인 보정선의 앞쪽) null: 노즈가 닿는 도면 점에서 끝내면 노즈가 끝점 자세에 서지 않는다
     */
    private fun safeAt(k: Int, nose: Pt): ProgAt {
        val p = poseAt(k, nose)
        val m = drawn?.let { foot(k, nose) }
        return ProgAt(p.tip, sol.lineOf(k, p.tip), m, m?.let { drawn!!.lineOf(k, it) })
    }

    /** 블록(도면 좌표) 끝 p 에서 공구 쪽 단위 법선 — G41 은 진행 방향 왼쪽, 원호는 볼록이면 원 바깥 */
    private fun normal(d: Seg, p: Pt): Pt = when (d) {
        is Line -> (d.end - d.start).unit().left() * (if (sol.side == Side.G41) 1.0 else -1.0)
        is Arc -> (p - d.center).unit() * (if ((sol.side == Side.G41) == d.cw) 1.0 else -1.0)
    }

    /**
     * 기계 보정: 요소 j 블록을 도면 명령점 P 에서 끝내면 제어기는 그 블록의 법선으로 보정해 노즈 중심이 P + r·n 에 선다(문서의 끝 블록 규칙).
     * 노즈 중심 c 가 그 자리가 되는 P(요소 j 의 직선 · 원 위 — 볼록 모서리에서는 끝을 조금 넘어 늘인 곳). 블록이 앞으로 가지 않거나
     * 노즈 경로 요소가 그 도면 요소의 보정선이 아니면(꼭짓점 둘레 원호 등) null
     */
    private fun foot(j: Int, c: Pt): Pt? {
        val d = drawn?.program?.segs?.getOrNull(j) ?: return null
        val p = when (d) {
            is Line -> {
                val v = d.end - d.start
                if (v.length <= EPS) return null
                d.start + v * (((c - d.start) dot v) / (v dot v))
            }
            is Arc -> {
                val v = c - d.center
                if (v.length <= EPS || d.radius <= EPS) return null
                d.center + v.unit() * d.radius
            }
        }
        if ((p + normal(d, p) * r - c).length > 1e-6) return null
        if (paramOf(d, p) <= 1e-9) return null
        return p
    }

    // ------------------------------------------------------------------ 잘린 NC

    /** 직접 보정(G40): 가상 인선 경로를 안전 끝점(경로 길이 [sEnd])에서 끊고 빠진다 */
    private fun directCut(sEnd: Double): HolderCut? {
        var lim = sEnd
        repeat(MAX_TRY) {
            val (k, t) = at(lim) ?: return null
            if (k < 0) return null
            val seg = sol.program.segs[k]
            val len = lengthOf(seg)
            val start = pos(k, 0.0)
            // 0.001 로 쓴 점이 경로를 따라 안전 끝점을 넘으면 한 칸 뒤로
            var tt = t
            var p: Pt? = null
            while (tt > 1e-12) {
                val q = pointOn(seg, tt)
                if (start + paramOf(seg, rounded(q)) * len <= sEnd + 1e-12) {
                    p = q
                    break
                }
                tt -= HolderStop.GRID / len
            }
            if (p == null) {
                lim = start
                return@repeat
            }
            val end = p
            fun write(): Written {
                val nc = replay(sol, k)
                val kept = nc.lines.size
                nc.element = k
                if (seg is Arc) nc.arcUntil(seg, end) else nc.move("G1", end)
                return Written(nc, kept, readNc(nc.lines))
            }
            val w = write()
            if (w.nc.lines.size == w.kept) {
                lim = start
                return@repeat
            }
            val stopTip = w.read.last()!!.end
            val stopT = paramOf(seg, stopTip)
            val written = start + stopT * len
            // 다시 읽은 글을 따라 깎는 동안 기준을 넘는 곳이 있으면 그 앞으로
            textShort(w.read, w.nc.refs, 1, w.read.size)?.let {
                lim = min(it, written) - HolderStop.GRID
                return@repeat
            }
            val back = retract(::write, stopTip, k, sol.entry.z)
            if (back == null) {
                lim = written - HolderStop.GRID
                return@repeat
            }
            val nose = stopTip - tv
            return HolderCut(
                back.lines, w.kept, back.from, k, stopT, stopTip, HolderPose(stopTip, nose, HolderCheck.drawnOf(sol.drawing, k, nose)),
                back.kind, back.clear, max(0.0, sEnd - written),
            )
        }
        return null
    }

    /**
     * 기계 보정(G41/G42, 도면 좌표 · 회사 꼴 — 계획 단계 4): 마지막 블록 뒤에 공구 쪽(끝 법선)으로 빼는 줄 하나(`F.3`, 보정 켠 채) → `G40` 혼자 줄 → `G0Z200.`.
     * 빼는 줄이 마지막 블록과 직각(안쪽 꺾임)이라 제어기는 두 보정선의 교점에 노즈를 세운다 — 그래서 마지막 블록을 끝 법선 자리가 안전 끝점이 되는 도면 점 P 보다
     * 더 늘인 P′(직선은 r, 원호는 각 asin(r/ρ))까지 써서 그 교점이 안전 끝점(또는 덜 간 곳)이 되게 한다. 교점은 다시 읽은 글로 잡는다 — 마지막 블록과
     * 빼는 줄(끝을 0.001 로 써서 방향이 끝 법선에서 조금 돈다)의 보정선 교점. 그것이 경로를 따라 안전 끝점을 넘으면 한 칸 뒤로.
     * 그 자세가 블록을 끝낼 수 없는 곳(꼭짓점 둘레 원호, 볼록 모서리에서 늘인 보정선의 앞쪽)이면 앞 요소 끝(보정선의 교점)에서 멈춘다.
     * 거꾸로 후퇴는 쓰지 않는다 — 깎은 길을 보정 켠 채 되짚으려면 보정 쪽을 바꿔야 하고 원호 뒤에서는 FANUC 이 경로를 정하지 않았다(FF 5.4.3 191쪽)
     */
    private fun machineCut(sEnd: Double): HolderCut? {
        val d = drawn ?: return null
        var lim = sEnd
        repeat(MAX_TRY) {
            val (j, t) = at(lim) ?: return null
            if (j < 0) return null
            val cseg = noseOf(sol.program.segs[j])
            val dseg = d.program.segs[j]
            val len = lengthOf(cseg)
            val start = pos(j, 0.0)
            // P′ 고르기: 끝 법선으로 빼는 줄을 둔 교점(어림)이 안전 끝점 앞인 가장 먼 쓴 점
            var tt = t
            var hit: Written? = null
            while (tt > 1e-12) {
                val p = foot(j, pointOn(cseg, tt)) ?: break
                val w = writeMachine(d, j, dseg, extended(dseg, p))
                if (w.nc.lines.size == w.kept) break
                val jn = cornerNose(w.read.last()!!) ?: break
                val th = paramOf(cseg, jn)
                if ((jn - pointOn(cseg, th.coerceIn(0.0, 1.0))).length > 2e-3) break
                if (start + th * len <= sEnd + 1e-12) {
                    hit = w
                    break
                }
                tt -= HolderStop.GRID / len
            }
            if (hit == null) {
                lim = start
                return@repeat
            }
            val p = hit.read.last()!!.end
            val back = machineExit({ writeMachine(d, j, dseg, p) }, j, safe) ?: machineExit({ writeMachine(d, j, dseg, p) }, j, 0.0)
            // 실제 교점(다시 읽은 빼는 줄로)이 안전 끝점을 넘거나 그 요소의 노즈 경로 위가 아니면 한 칸 뒤로
            val jc = back?.nose
            val th = jc?.let { paramOf(cseg, it) }
            val written = start + (th ?: paramOf(cseg, cornerNose(hit.read.last()!!) ?: cseg.start)) * len
            if (back == null || jc == null || th == null || (jc - pointOn(cseg, th.coerceIn(0.0, 1.0))).length > 2e-3 || written > sEnd + 1e-12) {
                lim = min(lim, written) - HolderStop.GRID
                return@repeat
            }
            // 다시 읽은 마지막 블록(들)을 제어기처럼: 앞 블록과의 이음 = 노즈 경로 요소 j 의 시작, 같은 원호 조각 사이는 이어지고,
            // 블록 끝은 빼는 줄과의 교점(jc). 그 보정선을 따라 기준을 본다
            var from = cseg.start
            var short = false
            for (i in hit.kept until hit.read.size) {
                val b = hit.read[i] ?: continue
                val o = offsetOf(b)
                val to = if (i == hit.read.size - 1) jc else o.end
                val m: Seg = if (o is Arc) Arc(from, to, o.center, o.cw) else Line(from, to)
                if (lengthOf(m) > EPS && firstShort(m, text, j, textShifts) != null) short = true
                from = to
            }
            if (short) {
                lim = written - HolderStop.GRID
                return@repeat
            }
            return HolderCut(back.lines, hit.kept, back.from, j, th, p, HolderPose(jc + tv, jc, p), back.kind, back.clear, max(0.0, sEnd - written))
        }
        return null
    }

    /**
     * 끝 법선 자리가 [p] 인 블록을 직각 빼는 줄과의 교점이 그 자리가 되게 늘인 도면 점: 직선은 진행 방향으로 r, 원호는 같은 원 위로 asin(r/ρ)(ρ = 보정 반지름)
     */
    private fun extended(d: Seg, p: Pt): Pt = when (d) {
        is Line -> p + (d.end - d.start).unit() * r
        is Arc -> {
            val rho = (p + normal(d, p) * r - d.center).length
            val a = angleOf(p - d.center) + (if (d.cw) -1 else 1) * asin(min(1.0, r / rho))
            d.center + Pt(cos(a), sin(a)) * d.radius
        }
    }

    /** 빼는 줄(진행 [n] = 블록 끝의 공구 쪽 법선)의 공구 쪽 법선 — 블록 진행의 반대쪽 */
    private fun exitNormal(n: Pt): Pt = if (sol.side == Side.G41) n.left() else n.left() * -1.0

    /** 다시 읽은 마지막 블록 [b] 와 그 끝에서 공구 쪽(끝 법선)으로 정확히 빼는 줄의 보정선 교점(노즈 중심 — P′ 를 고르는 어림). 없으면 null */
    private fun cornerNose(b: Seg): Pt? {
        val n = normal(b, b.end)
        return cornerWith(b, Line(b.end, b.end + n))
    }

    /** 다시 읽은 블록 [b] 와 그 끝에서 이어지는 직선 [ex] 의 보정선 교점(노즈 중심 — 안쪽 꺾임). 없으면 null */
    private fun cornerWith(b: Seg, ex: Line): Pt? {
        val u = (ex.end - ex.start).unit()
        val p0 = ex.start + exitNormal(u) * r
        val near = b.end + normal(b, b.end) * r
        return when (b) {
            // 끝 블록과 한 직선으로 이어지는 빼는 줄(관통 구멍 끝 — 끝 좌표 너머로 곧게, 2026-11-13)은 보정선 교점이 없다 — 노즈는 끝 점의 법선 자리
            // (`CompFrame.junction` 과 같다. 전에는 null 이라 원래 끝 검사를 말없이 건너뛰었다)
            is Line -> if (abs((b.end - b.start).unit() cross u) < 1e-9 && ((b.end - b.start) dot u) > 0) near
            else Intersect.lineLine(b.start + normal(b, b.start) * r, b.end - b.start, p0, u)
            is Arc -> Intersect.lineCircle(p0, u, b.center, (near - b.center).length).minByOrNull { (it - near).length }
        }
    }

    /** 물러남의 노즈 원을 재는 Z 끝(빼는 [level] 마다): 도면 맨 위 + r + level + 1 mm — 그 위는 입구 면 위 공기이거나 같은 구멍(반직선)이 이어진다 */
    private fun noseTop(level: Double): Double = min(CompFrame.RETRACT_Z, drawingTop + r + level + 1.0)

    private val drawingTop: Double = sol.drawing.segs.maxOf { s -> maxOf(s.start.z, s.end.z, if (s is Arc) s.center.z + s.radius else s.start.z) }

    /** 노즈 [a] → [b] 직선을 Z [top] 에서 자른 것(그 위는 보지 않는다) */
    private fun upTo(a: Pt, b: Pt, top: Double): Pt = if (b.z <= top || b.z <= a.z) b else a + (b - a) * ((top - a.z) / (b.z - a.z)).coerceIn(0.0, 1.0)

    /**
     * 기계 보정 후퇴(회사 꼴): 공구 쪽(끝 법선)으로 빼는 줄 `G1 … F.3` → `G40` → `G0Z200.`. 빼는 줄 길이 = r + δ(노즈가 벽에서 δ 떨어짐),
     * δ 는 max([level], [HolderStop.LIFT], √2·r + G40 여유)부터. 다시 읽은 글로 확인: 빼는 줄(실제 교점 → 빼는 줄 끝 Xe 의 법선 자리), 형식 C 의 G40 옮김
     * (그 자리 → Xe − 팁 벡터)과 그 뒤 `G0Z200.`(+Z), 형식 A · B 의 `G0Z200.`(그 자리 → (Z200, Xe 의 X) − 팁 벡터로 곧게 — 남은 벡터를 지우며 간다)의
     * 부분 여유(껍질이라 Z200. 까지 정확)와 노즈 원(벽에 끌리지 않음 — 직접 보정 빼기와 같은 규칙, 간 길이를 이어서 [noseTop] 까지),
     * 그리고 G40 자리 규칙(`CompFrame`) — Xe 둘레 (1 + √2)·r + 여유와 그 뒤 +Z 띠 안에 소재가 없다(팁 1~9 의 노즈가 어디로 옮겨져도 안 닿음).
     * 돌려주는 [Back.nose] = 실제 교점(마지막 블록 끝 노즈)
     */
    private fun machineExit(base: () -> Written, k: Int, level: Double): Back? {
        // 노즈 원 거리(중심 거리 − r)로 잰 G40 자리 규칙
        val g40 = sqrt(2.0) * r + CompFrame.G40_MARGIN
        val d0 = max(max(level, HolderStop.LIFT), g40)
        val top = noseTop(level)
        // 안전 여유로 고를 때는 처음 되는 것, 여유 0 으로 고를 때는 후퇴 여유가 가장 넓은 것
        var best: Back? = null
        for (delta in listOf(d0, d0 + 0.5, d0 + 1.0, d0 + 2.0)) {
            val w = base()
            val nc = w.nc
            val from = nc.lines.size
            val last = w.read.last() ?: return null
            val n = normal(last, last.end)
            val xe0 = last.end + n * (r + delta)
            nc.element = RETRACT
            nc.move("G1", xe0, f = CompFrame.FEED_EXIT)
            nc.alone("G40")
            nc.move("G0", Pt(CompFrame.RETRACT_Z, xe0.x))
            val read = readNc(nc.lines)
            val ex = read.getOrNull(from) as? Line ?: continue
            val jc = cornerWith(last, ex) ?: continue
            val xe = ex.end
            val ne = xe + exitNormal((xe - ex.start).unit()) * r
            val nC = xe - tv
            val far = Pt(CompFrame.RETRACT_Z, xe.x) - tv
            val needCut = Need(level - HolderStop.TEXT_TOL, Double.NEGATIVE_INFINITY)
            val needAir = Need(level - HolderStop.TEXT_TOL, -HolderStop.TEXT_TOL)
            val moves = listOf(Triple(jc, ne, needCut), Triple(ne, nC, needAir), Triple(nC, far, needAir), Triple(ne, far, needAir))
            var low = Double.POSITIVE_INFINITY
            val ok = moves.all { (a, b, need) ->
                val c = textClear(listOf(a, b), need, k)
                low = min(low, c.least(false))
                c.slack(need) >= -HolderStop.OK_TOL
            }
            if (!ok) continue
            // 노즈 원: 빼는 줄 → (형식 C) G40 옮김 → Z, (형식 A · B) 빼는 줄 → 비스듬히 Z. 여유 0 으로 고를 때는 안전 여유로는 모자란 곳의 노즈 거리도 후퇴 여유에 넣는다
            val nl = doubleArrayOf(Double.POSITIVE_INFINITY)
            val s1 = (ne - jc).length
            if (!noseLeaves(jc, ne, 0.0, level, safe, nl) || !noseLeaves(ne, nC, s1, level, safe, nl) || !noseLeaves(nC, upTo(nC, far, top), s1 + (nC - ne).length, level, safe, nl)) continue
            if (!noseLeaves(ne, upTo(ne, far, top), s1, level, safe, nl)) continue
            if (noseGap(xe) < g40 - NOSE_TOL || noseSweep(xe, Pt(top, xe.x)) < g40 - NOSE_TOL) continue
            val b = Back(nc.lines.toList(), from, RetractKind.LIFT, min(low, nl[0]), jc)
            if (level >= safe) return b
            if (best == null || b.clear > best.clear) best = b
        }
        val z = machineExitZ(base, k, level)
        return if (best == null || (z != null && z.clear > best.clear)) z else best
    }

    /**
     * [machineExit] 이 안 될 때(공구 쪽으로 빼면 바 맞은편이 모자람 — 구멍 바닥 구면 등): 공구 쪽으로 조금 뺀 뒤 보정 켠 채 Z 로 진입점 Z 까지
     * (직접 보정의 Z 후퇴와 같은 길) → `G40`(구멍 밖) → `G0Z200.`. 짧은 줄 길이는 노즈가 다음 Z 줄과의 꺾임에서 거꾸로 가지 않는 가장 짧은 것부터,
     * Z 줄 노즈가 벽에서 더 떨어지게 max([level], [HolderStop.LIFT]) · + r · + 2r 더 긴 것까지 본다(노즈 원 규칙 · 고르기는 [machineExit] 와 같다)
     */
    private fun machineExitZ(base: () -> Written, k: Int, level: Double): Back? {
        val g40 = sqrt(2.0) * r + CompFrame.G40_MARGIN
        val entryZ = drawn!!.entry.z
        val top = noseTop(level)
        val last0 = base().read.last() ?: return null
        val n = normal(last0, last0.end)
        // 짧은 줄의 노즈 이동: 처음 교점에서 r, 끝(Z 줄과 꺾임 φ)에서 r·tan(φ/2) 를 먹는다
        val c = n.z * (if (entryZ >= last0.end.z) 1.0 else -1.0)
        if (c < -0.85) return null
        val tanHalf = if (c >= 1 - 1e-12) 0.0 else (1 - c) / sqrt(1 - c * c)
        val l0 = r + r * tanHalf + 2 * CompFrame.MIN_MOVE
        var best: Back? = null
        for (extra in listOf(0.0, max(level, HolderStop.LIFT), max(level, HolderStop.LIFT) + r, max(level, HolderStop.LIFT) + 2 * r)) {
            val w = base()
            val nc = w.nc
            val from = nc.lines.size
            val last = w.read.last() ?: return null
            val x1 = last.end + n * (l0 + extra)
            nc.element = RETRACT
            nc.move("G1", x1, f = CompFrame.FEED_EXIT)
            nc.move("G1", Pt(entryZ, x1.x))
            nc.alone("G40")
            nc.move("G0", Pt(CompFrame.RETRACT_Z, x1.x))
            val read = readNc(nc.lines)
            val a = read.getOrNull(from) as? Line ?: return null
            val b = read.getOrNull(from + 1) as? Line ?: return null
            val u1 = (a.end - a.start).unit()
            val u2 = (b.end - b.start).unit()
            if ((u1 dot u2) < -1e-9) return null
            val jc = cornerWith(last, a) ?: return null
            val n1 = CompFrame.corner(a.end, u1, u2, sol.side, r)
            val n2 = b.end + exitNormal(u2) * r
            if (((n1 - jc) dot u1) < 0 || ((n2 - n1) dot u2) < 0) continue
            val nC = b.end - tv
            val far = Pt(CompFrame.RETRACT_Z, b.end.x) - tv
            val needCut = Need(level - HolderStop.TEXT_TOL, Double.NEGATIVE_INFINITY)
            val needAir = Need(level - HolderStop.TEXT_TOL, -HolderStop.TEXT_TOL)
            val moves = listOf(Triple(jc, n1, needCut), Triple(n1, n2, needAir), Triple(n2, nC, needAir), Triple(nC, far, needAir), Triple(n2, far, needAir))
            var low = Double.POSITIVE_INFINITY
            val ok = moves.all { (p, q, need) ->
                val cl = textClear(listOf(p, q), need, k)
                low = min(low, cl.least(false))
                cl.slack(need) >= -HolderStop.OK_TOL
            }
            if (!ok) continue
            val nl = doubleArrayOf(Double.POSITIVE_INFINITY)
            val s1 = (n1 - jc).length
            val s2 = s1 + (n2 - n1).length
            if (!noseLeaves(jc, n1, 0.0, level, safe, nl) || !noseLeaves(n1, upTo(n1, n2, top), s1, level, safe, nl)) continue
            if (!noseLeaves(n2, nC, s2, level, safe, nl) || !noseLeaves(nC, upTo(nC, far, top), s2 + (nC - n2).length, level, safe, nl) || !noseLeaves(n2, upTo(n2, far, top), s2, level, safe, nl)) continue
            if (noseGap(b.end) < g40 - NOSE_TOL || noseSweep(b.end, Pt(top, b.end.x)) < g40 - NOSE_TOL) continue
            val back = Back(nc.lines.toList(), from, RetractKind.LIFT, min(low, nl[0]), jc)
            if (level >= safe) return back
            if (best == null || back.clear > best.clear) best = back
        }
        return best
    }

    /** 도면 좌표 블록을 공구 쪽으로 r 민 보정선(노즈 중심): 직선은 평행 이동, 원호는 같은 중심 · 볼록 R + r, 오목 R − r */
    private fun offsetOf(b: Seg): Seg = when (b) {
        is Line -> Line(b.start + normal(b, b.start) * r, b.end + normal(b, b.end) * r)
        is Arc -> Arc(b.start + normal(b, b.start) * r, b.end + normal(b, b.end) * r, b.center, b.cw)
    }

    private fun writeMachine(d: RoundSolution, j: Int, dseg: Seg, p: Pt): Written {
        val nc = replay(d, j)
        val kept = nc.lines.size
        nc.element = j
        if (dseg is Arc) nc.arcUntil(dseg, p) else nc.move("G1", p)
        return Written(nc, kept, readNc(nc.lines))
    }

    /**
     * 다시 읽은 직접 보정 글의 줄 [from] ~ [until] 을 따라 [text] 기준이 처음 깨지는 곳의 경로 길이(원래 경로에 투영). 다 되면 null
     */
    private fun textShort(read: List<Seg?>, refs: List<NcRef>, from: Int, until: Int): Double? {
        for (i in from until until) {
            val s = read[i] ?: continue
            val ns = noseOf(s)
            val e = refs[i].element
            val (a, _) = firstShort(ns, text, e, textShifts) ?: continue
            val tip = pointOn(ns, a) + tv
            val seg = if (e < 0) Line(sol.entry, sol.program.segs.first().start) else sol.program.segs[e]
            return pos(e, paramOf(seg, tip))
        }
        return null
    }

    /** 후퇴를 붙인 글. [nose] = 기계 보정 빼는 줄의 실제 교점(마지막 블록 끝 노즈) */
    private class Back(val lines: List<String>, val from: Int, val kind: RetractKind, val clear: Double, val nose: Pt? = null)

    /**
     * 후퇴(직접 보정). [base] = 마지막 블록까지 쓴 글(새로), [stopTip] = 그 끝의 실제 가상 인선, [k] = 그 자리의 요소, [entryZ] = 그 프로그램의
     * 진입점 Z(기계 보정은 [machineExit]).
     * ① X 를 축 쪽으로 빼고 Z 로 진입점 Z 까지: 안전 여유 이상 → ② 온 길을 거꾸로 → ① 여유 ≥ 0. 고른 것은 다시 읽은 글로 확인한다.
     * 여유는 깎는 동안과 같은 직선 거리 · 같은 예외(인서트가 깎던 벽은 겹치지 않음, 바 · 최소 가공경 구간은 깎던 면에 겹치지 않음 — 빼기는 그 자리의
     * 요소 k, 거꾸로 후퇴는 되짚는 요소의 면). 벽에 붙어 끌리지 않는 것은 노즈 원([noseLeaves])이 본다
     */
    private fun retract(base: () -> Written, stopTip: Pt, k: Int, entryZ: Double): Back? =
        lift(base, stopTip, k, entryZ, safe) ?: reverse(base, stopTip, k) ?: lift(base, stopTip, k, entryZ, 0.0)

    /**
     * ① X 빼기 δ(반경, 축 쪽) → G00 Z. δ = 0 일 때 Z 로 빼는 움직임의 껍질 여유 A(바 깎는 쪽) · B(바 · 인서트 맞은편)로 [level] 을 맞추는 δ 를
     * 고른다: 공구를 축 쪽으로 δ 옮기면 맞은편 직선 거리는 δ 보다 더 줄지 않으므로 δ ≤ hi = B − level 이면 맞은편이 level 이상이고,
     * 깎는 쪽은 많아야 δ 늘어 δ ≥ lo = level − A 가 필요하다(충분하지는 않다 — 확인은 [liftBy]).
     * 후보는 δ = 0(Z 로만 — 단면 · 바닥 · 가파른 벽처럼 노즈가 +Z 로 바로 떨어질 때)과 max(안전 여유, [HolderStop.LIFT]) 를 [lo, hi] 에 넣은 것
     * (반올림 한 칸 여유). 후보마다 [liftBy] 가 다시 읽은 글로 부분 여유와 노즈 원([noseLeaves] — 벽에 끌리지 않음)을 확인한다.
     * 인서트 깎는 쪽은 깎는 동안처럼 겹치지 않음만 본다(빼기 시작에 날이 벽에 붙어 있다)
     */
    private fun lift(base: () -> Written, stopTip: Pt, k: Int, entryZ: Double, level: Double): Back? {
        val n0 = stopTip - tv
        // 쓴 글 자세라 Z 반 칸 옮긴 것 가운데 좋은 쪽(값마다 따로 — δ 후보만 고르고 확인은 [liftBy] 가 한다)
        val sweeps = textShifts.map { sh -> clear(listOf(n0 + sh, n0 + sh + Pt(entryZ - stopTip.z, 0.0)), k) }
        val a = sweeps.maxOf { it.barCut.m }
        val b = sweeps.maxOf { min(it.barOpp.m, it.insOpp.m) }
        if (sweeps.maxOf { min(it.turret.m, it.minBore.m) } < level) return null
        val lo = level - a
        val hi = b - level
        // 빼지 않고 Z 로만(노즈가 벽에서 바로 떨어질 때 — 단면 · 바닥 · 가파른 벽), 아니면 X 를 뺀 뒤
        val tries = mutableListOf<Double>()
        // 멈춘 자세는 쓴 글이라 기준보다 반올림 허용(TEXT_TOL)만큼 모자랄 수 있다
        if (lo <= HolderStop.TEXT_TOL && hi >= -HolderStop.TEXT_TOL) tries += 0.0
        val l = max(lo, 0.0) + HolderStop.GRID
        val h = hi - HolderStop.GRID
        if (l <= h) tries += max(level, HolderStop.LIFT).coerceIn(l, h)
        for (delta in tries) liftBy(base, stopTip, k, entryZ, level, delta)?.let { return it }
        return null
    }

    private fun liftBy(base: () -> Written, stopTip: Pt, k: Int, entryZ: Double, level: Double, delta: Double): Back? {
        val n0 = stopTip - tv
        val w = base()
        val nc = w.nc
        val from = nc.lines.size
        nc.element = RETRACT
        val q1 = stopTip - Pt(0.0, delta)
        if (delta > 0) nc.move("G1", q1)
        nc.move("G0", Pt(entryZ, q1.x))
        val read = readNc(nc.lines)
        if (read.size == from) return null
        // 다시 읽은 후퇴: 첫 블록은 실제 인선에서. 축 쪽 빼기는 깎는 쪽(위) 소재에서 멀어지기만 해
        // 인서트 깎는 쪽을 보지 않고, Z 로 빼는 움직임은 겹치지 않음만(쓴 글 반올림까지) 본다
        val p1 = read[from]!!.end
        val firstNeed = Need(level - HolderStop.TEXT_TOL, if (delta > 0) Double.NEGATIVE_INFINITY else -HolderStop.TEXT_TOL)
        val first = textClear(listOf(n0, p1 - tv), firstNeed, k)
        if (first.slack(firstNeed) < -HolderStop.OK_TOL) return null
        var low = first.least(false)
        // 노즈 원: 벽에 붙은 채 끌리거나 다른 소재에 닿지 않게
        var travel = 0.0
        if (!noseLeaves(n0, p1 - tv, travel, level)) return null
        travel += (p1 - tv - n0).length
        for (i in from + 1 until read.size) {
            val s = read[i] ?: continue
            val zNeed = Need(level - HolderStop.TEXT_TOL, -HolderStop.TEXT_TOL)
            val c = textClear(listOf(s.start - tv, s.end - tv), zNeed, k)
            if (c.slack(zNeed) < -HolderStop.OK_TOL) return null
            if (!noseLeaves(s.start - tv, s.end - tv, travel, level)) return null
            travel += lengthOf(s)
            low = min(low, c.least(false))
        }
        return Back(nc.lines.toList(), from, RetractKind.LIFT, low)
    }

    // ------------------------------------------------------------------ 후퇴의 노즈 원

    /**
     * 노즈 중심 [nose] 자세에서 바(볼록 다각형 — 앞은 주절삭날 연장선, 뒤는 공구대 면)와 소재 경계 사이 직선(유클리드) 거리와 소재 쪽 가장
     * 가까운 점 — 깎는 면도 넣은 그대로의 거리(예 C 의 바 앞면 ~ 바닥 면 0.226). 기준은 [Clear] 의 바 여유(깎는 면을 뺀 것)다
     */
    fun barGap(nose: Pt): Pair<Double, Pt> {
        val poly = tool.body.pts.map { it + nose }
        val c = clearance(poly, bore.walls, false, null)
        val o = clearance(poly, bore.walls, true, null)
        val g = if (o.m < c.m) o else c
        return g.m to Pt(g.z, g.x)
    }

    /**
     * 노즈 중심이 [a] → [b] 로 곧게 가는 동안 노즈 원과 소재 사이 가장 짧은 거리(mm, 정확한 최솟값): 선분과 소재 경계(맞은편 포함) 사이 거리 − r.
     * 한 끝이 소재 안이면 −r(경계를 넘지 않고 소재 안에만 있는 움직임도 끝이 안이다)
     */
    private fun noseSweep(a: Pt, b: Pt): Double {
        if (abs(a.x) >= bore.rho(a.z) || abs(b.x) >= bore.rho(b.z)) return -r
        val e = Line(a, b)
        var d = Double.POSITIVE_INFINITY
        for (w in bore.walls) d = min(d, if (lengthOf(e) <= EPS) distTo(w.seg, a) else segGap(e, w.seg).first)
        return d - r
    }

    /** 노즈 원과 소재 사이 거리(mm): 노즈 중심에서 소재 경계(맞은편 벽 포함)까지 − r. 중심이 소재 안이면 음수 */
    private fun noseGap(c: Pt): Double {
        if (abs(c.x) >= bore.rho(c.z)) return -r
        var d = Double.POSITIVE_INFINITY
        for (w in bore.walls) d = min(d, distTo(w.seg, c))
        return d - r
    }

    /**
     * 후퇴 직선 움직임(노즈 중심 [p0] → [p1], 후퇴 시작부터 [s0] mm 간 뒤)에서 노즈 원이 소재에서 떨어지나: 간 길이 s 에서
     * 거리 ≥ min(L, s/2), L = max([level], [MIN_LIFT]/2) — 벽에 붙은 채 끌리지 않고(처음엔 간 길이의 절반 이상 멀어짐) 그 뒤 L 이상.
     * 0.002 mm(처음 0.2 mm) · 0.01 mm 마다 재고, 판판한 곳(L 에 닿은 뒤)은 표본 사이(1.5 배 기울기)까지 덮게 그만큼 더 본다.
     * 쓴 글의 반올림(0.0007 mm)으로 멈춘 노즈가 벽에 그만큼 들어가 있을 수 있어 [NOSE_TOL] 까지 받는다.
     * [low] 를 주면 [watch](안전 여유) 기준으로는 모자란 표본의 거리 가운데 가장 작은 것을 거기에 모은다 — 여유 0 으로 고른 기계 보정 후퇴가 노즈로 얼마나 좁은지
     */
    private fun noseLeaves(p0: Pt, p1: Pt, s0: Double, level: Double, watch: Double = level, low: DoubleArray? = null): Boolean {
        val big = max(level, MIN_LIFT / 2)
        val bigW = max(watch, MIN_LIFT / 2)
        val len = (p1 - p0).length
        var s = 0.0
        while (true) {
            val st = if (s0 + s < 0.2) 0.002 else 0.01
            val need = min(big, (s0 + s) / 2)
            val slop = if ((s0 + s) / 2 >= big) 0.75 * st else 0.0
            val p = if (len <= 0) p0 else p0 + (p1 - p0) * (s / len)
            val g = noseGap(p)
            if (g < need + slop - NOSE_TOL) return false
            if (low != null && g < min(bigW, (s0 + s) / 2) + (if ((s0 + s) / 2 >= bigW) 0.75 * st else 0.0) - NOSE_TOL) low[0] = min(low[0], g)
            if (s >= len) return true
            s = min(len, s + st)
        }
    }

    /**
     * ② 온 길(가상 인선 경로)을 거꾸로 진입점까지(직접 보정만 — 기계 보정은 [machineExit]).
     * 줄마다 되짚는 요소의 깎는 면 예외를 쓴다(마지막 진입점으로 가는 줄은 진입 이동 — 예외 없음)
     */
    private fun reverse(base: () -> Written, stopTip: Pt, k: Int): Back? {
        val w = base()
        val nc = w.nc
        val from = nc.lines.size
        nc.element = RETRACT
        val els = mutableListOf<Int>()
        fun mark(e: Int) {
            while (els.size < nc.lines.size - from) els += e
        }
        var cur = stopTip
        for (j in k downTo 0) {
            val s = sol.program.segs[j]
            if (lengthOf(s) <= EPS) continue
            when (s) {
                is Line -> nc.move("G1", s.start)
                is Arc -> if ((cur - s.start).length > EPS) nc.arc(Arc(cur, s.start, s.center, !s.cw))
            }
            mark(j)
            cur = s.start
        }
        nc.move("G1", sol.entry)
        mark(-1)
        val read = readNc(nc.lines)
        var low = Double.POSITIVE_INFINITY
        for (i in from until read.size) {
            val s = read[i] ?: continue
            val e = els[i - from]
            val ns = noseOf(s)
            if (firstShort(ns, text, e, textShifts) != null) return null
            low = min(low, least(ns, false, e))
        }
        return Back(nc.lines.toList(), from, RetractKind.REVERSE, low)
    }

    private companion object {
        const val MAX_TRY = 200

        val NO_SHIFT = listOf(Pt(0.0, 0.0))

        /** 후퇴 줄의 NcRef 요소 번호 */
        const val RETRACT = -3

        /** 후퇴 때 노즈 원이 벽에서 떨어져야 하는 거리의 두 배(mm): 처음 0.05 mm 를 가는 동안 0.025 mm 까지 멀어져야 끌리지 않는다 */
        const val MIN_LIFT = 0.05

        /** 노즈 원 거리의 받는 오차(mm) — 쓴 글의 반올림 */
        const val NOSE_TOL = 1e-3
    }
}

/**
 * 풀이의 NC 를 요소 [upTo] 앞까지 다시 쓴다 — RoundCalc 가 쓴 것과 같은 순서 · 같은 글. 직접 보정: G00 진입 → G01 시작점 → 요소마다.
 * 기계 보정(회사 꼴): 보정 혼자 줄 → 진입 G0 줄들 → G1 첫 점 F.1 → 요소마다(`CompFrame.write` 와 같다)
 */
private fun replay(sol: RoundSolution, upTo: Int): NcLines = NcLines().apply {
    element = -2
    if (sol.approach.isNotEmpty()) {
        alone("${sol.side}")
        for (p in sol.approach) move("G0", p)
        element = -1
        move("G1", sol.program.segs.first().start, f = CompFrame.FEED_CUT)
    } else {
        move("G0", sol.entry)
        element = -1
        move("G1", sol.program.segs.first().start)
    }
    for (k in 0 until upTo) {
        element = k
        val s = sol.program.segs[k]
        if (s is Arc) arc(s) else move("G1", s.end)
    }
}

/** 0.001 로 쓴 점(Fmt.nc 를 다시 읽은 값 — NC 글의 Fmt.word 와 같은 수) */
private fun rounded(p: Pt) = Pt(Fmt.nc(p.z).toDouble(), Fmt.nc(p.xDia).toDouble() / 2)

/**
 * NC 글을 다시 읽는다: 줄마다 움직임(첫 줄 G0 은 시작점이라 null). 빠진 축 · 이동 G 는 앞 줄 값(모달), X 는 직경 → 반경. G 는 수로 본다(G2 = G02)
 * G2/G3 은 끝점 · R(180° 이하 쪽)로 중심을 다시 구한다 — 제어기가 하는 대로. G40 같은 말은 건너뛴다
 */
internal fun readNc(lines: List<String>): List<Seg?> {
    var z: Double? = null
    var x: Double? = null
    var g = -1
    val out = mutableListOf<Seg?>()
    for (line in lines) {
        var nz = z
        var nx = x
        var rr: Double? = null
        var axis = false
        for (w in ncWords(line)) {
            when (w[0]) {
                'G' -> motionOf(w)?.let { g = it }
                'X' -> nx = w.substring(1).toDouble() / 2
                'Z' -> nz = w.substring(1).toDouble()
                'R' -> rr = w.substring(1).toDouble()
            }
            if (w[0] == 'X' || w[0] == 'Z') axis = true
        }
        val from = if (z != null && x != null) Pt(z, x) else null
        // 좌표 전의 축 없는 줄(회사 꼴 첫 줄 `G41` 혼자)은 움직임이 없다
        if (!axis && from == null) {
            out += null
            continue
        }
        require(nz != null && nx != null) { "좌표가 없는 줄: $line" }
        val to = Pt(nz, nx)
        out += when {
            from == null -> null
            // 축 없는 줄(`G40` 혼자 — 앞 줄이 원호여도)은 움직이지 않는다
            axis && (g == 2 || g == 3) -> arcR(from, to, requireNotNull(rr) { "원호에 R 이 없다: $line" }, g == 2)
            else -> Line(from, to)
        }
        z = nz
        x = nx
    }
    return out
}

/** 이동 G 낱말(G0 ~ G3 · G00 ~ G03)이면 그 번호 */
private fun motionOf(w: String): Int? = w.drop(1).toIntOrNull()?.takeIf { it in 0..3 && w.length <= 3 }

/** 끝점 R 원호(180° 이하). G2 = 이 그림에서 시계 → 중심은 현 진행 방향 오른쪽 */
private fun arcR(a: Pt, b: Pt, rr: Double, cw: Boolean): Arc {
    val ch = b - a
    val h = ch.length / 2
    val d = sqrt(max(rr * rr - h * h, 0.0))
    return Arc(a, b, (a + b) * 0.5 + ch.unit().left() * (if (cw) -d else d), cw)
}

/** 요소 위(또는 가까운) 점 p 의 길이 비율. 직선은 투영, 원호는 시작에서 진행 방향으로 잰 각 — 시작보다 조금 앞이면 음수, 끝을 넘으면 1 보다 크다 */
internal fun paramOf(s: Seg, p: Pt): Double = when (s) {
    is Line -> {
        val d = s.end - s.start
        val l2 = d dot d
        if (l2 <= 0) 0.0 else ((p - s.start) dot d) / l2
    }
    is Arc -> {
        val sw = s.sweepRad()
        val a = wrap2Pi((if (s.cw) -1 else 1) * (angleOf(p - s.center) - angleOf(s.start - s.center)))
        if (sw <= 0) 0.0 else (if (a > PI + sw / 2) a - 2 * PI else a) / sw
    }
}

/** 요소 [a, b] 구간 움직임을 덮는 점들: 직선은 두 끝, 원호는 두 끝과 두 끝 접선의 교점(구간 각 < 180°) */
internal fun span(s: Seg, a: Double, b: Double): List<Pt> {
    val p = pointOn(s, a)
    val q = pointOn(s, b)
    if (s !is Arc) return listOf(p, q)
    val d = s.sweepRad() * (b - a)
    if (d <= 1e-12) return listOf(p, q)
    val mid = angleOf(s.start - s.center) + (if (s.cw) -1 else 1) * s.sweepRad() * (a + b) / 2
    return listOf(p, q, s.center + Pt(cos(mid), sin(mid)) * (s.radius / cos(d / 2)))
}

/** 볼록 껍질(반시계, 한 줄 위 점은 뺀다) */
internal fun hull(pts: List<Pt>): List<Pt> {
    val s = pts.sortedWith(compareBy<Pt>({ it.z }, { it.x }))
    if (s.size < 3) return s
    fun turn(o: Pt, a: Pt, b: Pt) = (a - o) cross (b - o)
    val lower = mutableListOf<Pt>()
    for (p in s) {
        while (lower.size >= 2 && turn(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
        lower += p
    }
    val upper = mutableListOf<Pt>()
    for (p in s.asReversed()) {
        while (upper.size >= 2 && turn(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
        upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

private fun Seg.moved(v: Pt): Seg = when (this) {
    is Line -> Line(start + v, end + v)
    is Arc -> Arc(start + v, end + v, center + v, cw)
}
