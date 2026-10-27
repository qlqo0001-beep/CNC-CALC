package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
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
 * [element] −2 = G00 점에 선 자세(기계 보정 진입만 — 직접 보정은 [StopWarning.UNSAFE_AT_ENTRY]), −1 = 진입 이동(기계 보정은 보정 시작 블록),
 * k = program.segs[k], [t] = 요소(기계 보정 진입은 보정 시작 블록) 길이 비율, [pose] = 그 자세(기계 보정 진입은 drawn = 그 명령점).
 * [part] · [opposite] = 바로 다음에 기준을 넘는 부분과 벽(맞은편 벽이면 true), [margin] = 이 자세에서 그 부분의 여유(mm, 반경 — 공구대는 Z) —
 * 기준(안전 여유, 인서트의 깎는 쪽 벽은 0)과 같다. 부분이 면을 넘어 여유가 뛰는 곳이면 기준보다 크다.
 * [at] = 두 프로그램 좌표 · 줄: 기계 보정은 그 블록을 여기서 끝내면 제어기가 법선으로 보정해 노즈가 이 자세에 서는 도면 명령점
 * (그 자세에서 블록을 끝낼 수 없으면 null), 기계 보정 진입이면 그 명령점이고 direct 는 그 자세의 실제 인선 자리.
 * [gap] = 이 자세에서 바와 소재 사이 직선(유클리드) 거리(mm), [gapAt] = 그 소재 쪽 가장 가까운 점(맞은편 벽이면 x < 0) —
 * 여유는 반경 방향이라 바 앞면이 비스듬히 지나는 모서리 · 면에서는 직선 거리가 훨씬 짧을 수 있다(보이기만, 기준은 아니다)
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
 * 붙어 있는 인서트의 깎는 쪽은 뺀다), [lack] = 안전 끝점까지 노즈 경로를 따라 덜 간 길이(mm — 반올림 되돌리기, 기계 보정은 블록을
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
 * 닿기 전에 멈춰 깎는 것이 없다, [MACHINE_ENTRY] = 기계 보정 프로그램이 제 G00 점(보정 전 G40 상태) 또는 보정 시작 블록에서 안전 기준이 모자라
 * 깎을 수 없다([HolderPlan.machineEnd] 가 −2 · −1), [RETRACT_TIGHT] = 후퇴 여유가 안전 여유보다 좁다(겹치지는 않는다)
 */
enum class StopWarning { UNSAFE_AT_ENTRY, NOTHING_CUT, MACHINE_ENTRY, RETRACT_TIGHT }

/**
 * 보링바 간섭 검사 + 안전 끝점. [result] = [HolderCheck.check] 그대로(직접 보정 경로에서 닿는 곳), [hits] = 그 닿는 곳의 두 프로그램 좌표 · 줄
 * (같은 순서 — 진입점 · 진입 이동의 기계 보정 좌표는 기계 보정 프로그램 자기 진입([machineEntry])에서 같은 부분이 닿는 곳, 안 닿으면 null),
 * [end] = 직접 보정 프로그램의 안전 끝점(경로 전체가 안전하거나 검사를 안 했거나 진입점부터 안 되면 null), [direct] · [machine] = 잘린 직접 보정 ·
 * 기계 보정 NC(그 프로그램의 끝점이 없거나 깎는 것이 없으면 null, 기계 보정은 도면 좌표 풀이가 없어도 null).
 * [machineEnd] = 기계 보정 프로그램의 안전 끝점: 진입(G00 점 −2 · 보정 시작 블록 −1)은 그 프로그램 자기 경로로 보고, 진입이 되면 형상은
 * 직접 보정과 같은 노즈 경로라 같은 끝점(도면 좌표 풀이가 없거나 끝까지 안전하면 null).
 * [machineEntry] = 기계 보정 프로그램 자기 진입(G00 점에 선 자세 −2 · 보정 시작 블록 −1)에서 닿는 곳 — 좌표는 그 명령점(기계 보정 화면의 간섭 목록은
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
 * 안전 기준(사용자 결정 2026-10-27): 바 · 공구대 · 최소 가공경 · 인서트의 맞은편 벽 쪽은 여유 ≥ 안전 여유([SAFE_DEFAULT] 0.5 mm),
 * 인서트의 깎는 쪽 벽은 겹치지 않음(여유 ≥ −[CUT_TOL]) — 날은 노즈 접점 옆에서 원래 벽에 붙어 있어(직선 벽에서 r(1 − cos κr′)) 안전 여유를 둘 수 없다.
 * 여유는 [HolderCheck] 의 식(반경 방향, 공구대는 Z) 그대로다 — 직선 거리는 [SafeEnd.gap] 으로 보이기만 한다.
 *
 * 두 프로그램의 진입은 다르다: 직접 보정은 G00 점의 인선에서 첫 보정점으로 곧게, 기계 보정은 도면 G00 점(G40 상태 — 노즈 = 점 − 팁 벡터)에서
 * 보정 시작 블록 동안 노즈가 첫 블록 보정선의 시작으로 곧게 간다(제어기 시작 형식 A). 그래서 진입은 프로그램마다 따로 보고, 형상은 같은 노즈 경로다.
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
     * [sol] = 직접 보정(G40) 풀이, [drawn] = 같은 입력을 노즈R 0 으로 푼 도면 좌표 풀이(기계 보정 NC, 없으면 null), [r] = 노즈R,
     * [faceAtStart] · [bar] = [HolderCheck.check] 와 같다, [safe] = 안전 여유(mm). 안전 여유 값이 틀리면 result.skip = BAD_INPUT
     */
    fun plan(sol: RoundSolution, drawn: RoundSolution?, r: Double, faceAtStart: Boolean, bar: BoringBar, safe: Double = SAFE_DEFAULT): HolderPlan {
        val res = HolderCheck.check(sol.drawing, sol.program, sol.entry, sol.tip, r, faceAtStart, bar)
        if (res.skip != null) return HolderPlan(res, safe, null, emptyList(), null, null, emptyList(), null, null)
        if (!safeOk(safe)) {
            return HolderPlan(
                HolderResult(HolderSkip.BAD_INPUT, emptyList(), null, null, null), safe, null, emptyList(), null, null, emptyList(), null, null,
            )
        }
        val machine = drawn?.takeIf { it.drawing.segs.size == sol.drawing.segs.size && it.program.segs.size == sol.program.segs.size }
        return StopJob(sol, machine, r, Bore(sol.drawing, faceAtStart), bar, safe).plan(res)
    }
}

/** 안전 기준: [part] = 바 · 공구대 · 최소 가공경 · 인서트 맞은편 쪽, [insCut] = 인서트 깎는 쪽(−∞ 면 보지 않음) */
internal class Need(val part: Double, val insCut: Double)

/** 한 자세(또는 한 움직임 전체)의 부분별 여유 — 바 · 인서트는 깎는 쪽 벽과 맞은편 벽을 따로 */
internal class Clear(val barCut: Gap, val barOpp: Gap, val insCut: Gap, val insOpp: Gap, val turret: Gap, val minBore: Gap) {
    /** 기준까지 남은 양의 최솟값. 0 이상이면 안전 */
    fun slack(n: Need): Double = minOf(barCut.m - n.part, barOpp.m - n.part, insOpp.m - n.part, turret.m - n.part, minBore.m - n.part, insCut.m - n.insCut)

    /** 기준까지 남은 양이 가장 작은 (부분, 맞은편 벽) */
    fun worst(n: Need): Pair<HolderPart, Boolean> = listOf(
        Triple(HolderPart.BAR, false, barCut.m - n.part),
        Triple(HolderPart.BAR, true, barOpp.m - n.part),
        Triple(HolderPart.INSERT, false, insCut.m - n.insCut),
        Triple(HolderPart.INSERT, true, insOpp.m - n.part),
        Triple(HolderPart.TURRET, false, turret.m - n.part),
        Triple(HolderPart.MIN_BORE, false, minBore.m - n.part),
    ).minBy { it.third }.let { it.first to it.second }

    /** 그 부분 · 벽의 여유 */
    fun margin(part: HolderPart, opposite: Boolean): Double = when (part) {
        HolderPart.BAR -> if (opposite) barOpp.m else barCut.m
        HolderPart.INSERT -> if (opposite) insOpp.m else insCut.m
        HolderPart.TURRET -> turret.m
        HolderPart.MIN_BORE -> minBore.m
    }

    /** 가장 좁은 여유. [withInsCut] = 인서트 깎는 쪽도 */
    fun least(withInsCut: Boolean): Double =
        minOf(barCut.m, barOpp.m, insOpp.m, turret.m, minBore.m, if (withInsCut) insCut.m else Double.POSITIVE_INFINITY)
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
     * 면 바로 옆에서는 Z 반올림 0.0005 에 반경 여유가 크게 뛴다. 정확한 경로에서는 겹치지 않음을 이미 봤다
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
        val shape = firstEnd(shape = true)
        val warnings = mutableListOf<StopWarning>()
        // 직접 보정: G00 점의 자세 → 진입 이동 → 형상
        var end: SafeEnd? = null
        var direct: HolderCut? = null
        if (clear(listOf(sol.entry - tv)).slack(cut) < -HolderStop.OK_TOL) {
            warnings += StopWarning.UNSAFE_AT_ENTRY
        } else {
            val e = firstEnd(shape = false) ?: shape
            if (e != null) {
                end = e.first
                if (e.first.element >= 0) direct = directCut(e.second)
                if (direct == null) warnings += StopWarning.NOTHING_CUT
            }
        }
        // 기계 보정: 제 G00 점의 자세 → 보정 시작 블록 → 형상(직접 보정과 같은 노즈 경로)
        val mEnd = if (drawn == null) null else machineEntryEnd() ?: shape?.first
        val machine = if (mEnd != null && mEnd.element >= 0) machineCut(shape!!.second) else null
        if (mEnd != null && mEnd.element < 0) warnings += StopWarning.MACHINE_ENTRY
        if (listOfNotNull(direct, machine).any { it.retractClear < safe - HolderStop.TEXT_TOL }) warnings += StopWarning.RETRACT_TIGHT
        return HolderPlan(res, safe, end, hits, direct, machine, warnings, mEnd, if (drawn == null) null else entryHits)
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
            val (a, b) = firstShort(ns, cut) ?: continue
            val n = pointOn(ns, a)
            val (part, opp) = clear(span(ns, a, b)).worst(cut)
            val p = poseAt(k, n)
            val at = if (k < 0) ProgAt(p.tip, sol.lineOf(k, p.tip), null, null) else safeAt(k, n)
            val (g, gp) = barGap(n)
            return SafeEnd(k, a, p, part, opp, clear(listOf(n)).margin(part, opp), at, g, gp) to (cum[i] + a * lengthOf(s))
        }
        return null
    }

    // ------------------------------------------------------------------ 기계 보정의 진입

    /** 기계 보정 프로그램의 G00 점(G40 상태)에 선 노즈 — 인선이 도면 G00 점에 있다 */
    private val mNose0: Pt? = drawn?.let { it.entry - tv }

    /** 보정 시작 블록 끝의 노즈: 첫 형상 블록 보정선의 시작(제어기 시작 형식 A — 다음 블록 시작의 법선) */
    private val mNose1: Pt? = drawn?.program?.segs?.firstOrNull { lengthOf(it) > EPS }?.let { offsetOf(it).start }

    /** 보정 시작 블록 비율 u 의 명령점(도면 G00 점 → 도면 첫 점) */
    private fun mCmd(u: Double): Pt {
        val d = drawn!!
        val a = d.entry
        return a + (d.program.segs.first().start - a) * u
    }

    /** 보정 시작 블록 동안 노즈 움직임(G00 점의 노즈 → 첫 보정선 시작). 명령점과 같은 비율로 곧게 간다 */
    private fun mMove(): Line? {
        val a = mNose0 ?: return null
        val b = mNose1 ?: return null
        return Line(a, b)
    }

    /** 기계 보정 진입 비율 u 의 자세: tip = 실제 인선 자리, drawn = 명령점 */
    private fun mPose(u: Double): HolderPose {
        val mv = mMove()!!
        val n = if (u <= 0 || lengthOf(mv) <= EPS) mv.start else pointOn(mv, u)
        return HolderPose(n + tv, n, mCmd(u))
    }

    private fun mProg(k: Int, p: HolderPose) = ProgAt(p.tip, sol.lineOf(k, p.tip), p.drawn, drawn!!.lineOf(k, p.drawn))

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
     * 기계 보정 프로그램 자기 진입에서 닿는 곳(여유 < −[HolderCheck.CONTACT_TOL]): G00 점에 선 자세(−2) · 보정 시작 블록(−1). 고르기는
     * [HolderCheck.check] 와 같다 — [HolderCheck.STEP] 간격, 처음 · 마지막은 이분법 40번, 가장 깊은 곳은 표본, 비율은 보정 시작 블록 위.
     * 인서트에는 노즈 원도 든다([entryMargins])
     */
    private fun machineEntry(): List<HitAt> {
        val mv = mMove() ?: return emptyList()
        val out = mutableListOf<HolderHit>()
        fun scan(k: Int, n: Int) {
            val ts = DoubleArray(n + 1) { if (n == 0) 0.0 else it.toDouble() / n }
            val poses = ts.map(::mPose)
            val ms = poses.map { entryMargins(it.nose) }
            val found = mutableListOf<HolderHit>()
            for (part in HolderPart.entries) {
                fun touch(m: Margins) = m[part].m < -HolderCheck.CONTACT_TOL
                val jf = ms.indexOfFirst(::touch)
                if (jf < 0) continue
                val jl = ms.indexOfLast(::touch)
                fun edge(off: Double, on: Double): Double {
                    var a = off
                    var b = on
                    repeat(40) {
                        val mid = (a + b) / 2
                        if (touch(entryMargins(mPose(mid).nose))) b = mid else a = mid
                    }
                    return b
                }
                val from = if (jf == 0) 0.0 else edge(ts[jf - 1], ts[jf])
                val to = if (jl == n) ts[n] else edge(ts[jl + 1], ts[jl])
                val jw = ms.indices.minBy { ms[it][part].m }
                val g = ms[jw][part]
                val w = poses[jw]
                found += HolderHit(k, part, mPose(from), mPose(to), w, from, to, -g.m, g.x < 0, g.z - (w.nose + tool.ref).z)
            }
            out += found.sortedBy { it.fromT }
        }
        scan(-2, 0)
        val len = lengthOf(mv)
        if (len > EPS) scan(-1, max(1, ceil(len / HolderCheck.STEP).toInt()))
        return out.map { HitAt(it, mProg(it.element, it.first), mProg(it.element, it.last)) }
    }

    /**
     * 기계 보정 프로그램 자기 진입에서 기준이 처음 깨지는 곳: G00 점의 자세(−2) · 보정 시작 블록(−1). 진입이 되면 null.
     * 기준은 형상과 같고, 노즈 원도 소재에 들어가지 않아야 한다(인서트 깎는 쪽과 같은 '겹치지 않음' — 그 끝점은 인서트 · 깎는 쪽, 여유 = 노즈 원 거리)
     */
    private fun machineEntryEnd(): SafeEnd? {
        val mv = mMove() ?: return null
        fun end(k: Int, u: Double, c: Clear, noseBad: Boolean): SafeEnd {
            val p = mPose(u)
            val (part, opp) = if (noseBad) HolderPart.INSERT to false else c.worst(cut)
            val margin = if (noseBad) noseSweep(p.nose, p.nose) else clear(listOf(p.nose)).margin(part, opp)
            val (g, gp) = barGap(p.nose)
            return SafeEnd(k, u, p, part, opp, margin, mProg(k, p), g, gp)
        }
        val c0 = clear(listOf(mv.start))
        val nose0 = noseSweep(mv.start, mv.start) < -HolderStop.CUT_TOL
        if (nose0 || c0.slack(cut) < -HolderStop.OK_TOL) return end(-2, 0.0, c0, nose0 && c0.slack(cut) >= -HolderStop.OK_TOL)
        if (lengthOf(mv) <= EPS) return null
        val (a, b) = firstShort(mv, cut, nose = true) ?: return null
        val c = clear(span(mv, a, b))
        return end(-1, a, c, c.slack(cut) >= -HolderStop.OK_TOL)
    }

    // ------------------------------------------------------------------ 여유

    /** 노즈 중심을 [noses] 에 차례로 둔 움직임 전체(볼록 껍질)의 부분별 여유. 하나면 그 자세 */
    fun clear(noses: List<Pt>): Clear {
        val origin = Pt(0.0, 0.0)
        fun sides(p: Piece): Pair<Gap, Gap> =
            if (noses.size == 1) bore.sides(p, noses[0]) else bore.sides(Piece(hull(noses.flatMap { n -> p.pts.map { it + n } })), origin)
        var ic = Gap.CLEAR
        var io = Gap.CLEAR
        for (p in tool.inserts) {
            val (c, o) = sides(p)
            if (c.m < ic.m) ic = c
            if (o.m < io.m) io = o
        }
        val (bc, bo) = sides(tool.body)
        val zs = noses.map { it.z + tool.ref.z }
        val turret = tool.bar.overhang?.let { o -> bore.face?.let { f -> Gap(zs.min() + o - f, f, 0.0) } } ?: Gap.CLEAR
        val minBore = tool.bar.dMin?.let { d ->
            val back = if (tool.reverse) tool.insZ else 0.0
            bore.narrowest(zs.min() - back, zs.max() - back + tool.insZ).let { Gap(it.m - d / 2, it.z, it.x) }
        } ?: Gap.CLEAR
        return Clear(bc, bo, ic, io, turret, minBore)
    }

    /**
     * 쓴 글 검사는 Z 를 반 칸(0.0005 mm) 앞뒤로 옮긴 자세 가운데 좋은 쪽으로 본다: Z 반올림은 반경 여유로 재면 면 · 가파른 벽에서 크게 뛴다
     * (면에 붙어 깎는 자세 — 최소 가공경 구간의 앞 끝, κr 90° 바 앞 — 가 0.0005 넘어가면 막힌 바닥 너머(반경 0)를 본다). 실제 거리는 0.0005 다.
     * X 반올림은 그대로 본다
     */
    private val textShifts = listOf(Pt(0.0, 0.0), Pt(HolderStop.GRID / 2, 0.0), Pt(-HolderStop.GRID / 2, 0.0))

    /** 쓴 글 자세(또는 움직임)의 여유: [textShifts] 가운데 [need] 에 가장 좋은 것 */
    private fun textClear(noses: List<Pt>, need: Need): Clear = textShifts.map { sh -> clear(noses.map { it + sh }) }.maxBy { it.slack(need) }

    /**
     * 노즈 중심 움직임 [s] 를 따라 [need] 가 처음 깨지는 칸 (a, b) — 요소 비율, 폭 [HolderStop.STOP_TOL] mm 이하, a 까지는 안전.
     * 끝까지 안전하면 null. 구간 껍질의 여유가 기준 이상이면 그 구간 전체가 안전하다(직선은 정확, 원호는 안전 쪽) — 아니면 반으로 나눈다.
     * [shifts] 가 여럿이면(쓴 글) 그 가운데 하나로 구간 전체가 되면 된다
     */
    private fun firstShort(s: Seg, need: Need, shifts: List<Pt> = NO_SHIFT, nose: Boolean = false): Pair<Double, Double>? {
        val len = lengthOf(s)
        fun go(a: Double, b: Double): Pair<Double, Double>? {
            val pts = span(s, a, b)
            // nose = 노즈 원도 소재에 들어가지 않아야(직선 움직임만 — 기계 보정 진입)
            val noseOk = !nose || noseSweep(pointOn(s, a), pointOn(s, b)) >= -HolderStop.CUT_TOL
            if (noseOk && shifts.any { sh -> clear(pts.map { it + sh }).slack(need) >= -HolderStop.OK_TOL }) return null
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
    private fun least(ns: Seg, withInsCut: Boolean): Double {
        fun of(pts: List<Pt>) = textShifts.maxOf { sh -> clear(pts.map { it + sh }).least(withInsCut) }
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
                if (seg is Arc) nc.arcUntil(seg, end) else nc.move("G01", end)
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
            val back = retract(::write, stopTip, k, sol.entry.z, cancel = false)
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
     * 기계 보정(G41/G42, 도면 좌표): 마지막 블록을 도면 명령점 P 에서 끝내 제어기의 끝 블록 법선 보정으로 노즈가 안전 끝점(또는 덜 간 곳)에 서게 하고,
     * 첫 후퇴 직선 블록에 G40. 쓴 P(0.001)를 다시 읽어 그 블록의 법선으로 보정한 노즈가 안전 끝점을 넘으면 한 칸 뒤로.
     * 그 자세가 블록을 끝낼 수 없는 곳(꼭짓점 둘레 원호, 볼록 모서리에서 늘인 보정선의 앞쪽)이면 앞 요소 끝(보정선의 교점)에서 멈춘다
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
            var tt = t
            var hit: Triple<Written, Pt, Double>? = null
            while (tt > 1e-12) {
                val p = foot(j, pointOn(cseg, tt)) ?: break
                val w = writeMachine(d, j, dseg, p)
                if (w.nc.lines.size == w.kept) break
                val last = w.read.last()!!
                val nHat = last.end + normal(last, last.end) * r
                val th = paramOf(cseg, nHat)
                if ((nHat - pointOn(cseg, th.coerceIn(0.0, 1.0))).length > 2e-3) break
                if (start + th * len <= sEnd + 1e-12) {
                    hit = Triple(w, nHat, th)
                    break
                }
                tt -= HolderStop.GRID / len
            }
            if (hit == null) {
                lim = start
                return@repeat
            }
            val (w, nHat, th) = hit
            val written = start + th * len
            // 다시 읽은 마지막 블록(들)을 제어기처럼: 앞 블록과의 이음 = 노즈 경로 요소 j 의 시작, 같은 원호 조각 사이는 이어지고,
            // 블록 끝은 법선 보정(nHat). 그 보정선을 따라 기준을 본다
            var from = cseg.start
            var short = false
            for (i in w.kept until w.read.size) {
                val b = w.read[i] ?: continue
                val o = offsetOf(b)
                val to = if (i == w.read.size - 1) nHat else o.end
                val m: Seg = if (o is Arc) Arc(from, to, o.center, o.cw) else Line(from, to)
                if (lengthOf(m) > EPS && firstShort(m, text, textShifts) != null) short = true
                from = to
            }
            if (short) {
                lim = written - HolderStop.GRID
                return@repeat
            }
            val p = w.read.last()!!.end
            val back = retract({ writeMachine(d, j, dseg, p) }, nHat + tv, j, d.entry.z, cancel = true)
            if (back == null) {
                lim = written - HolderStop.GRID
                return@repeat
            }
            return HolderCut(back.lines, w.kept, back.from, j, th, p, HolderPose(nHat + tv, nHat, p), back.kind, back.clear, max(0.0, sEnd - written))
        }
        return null
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
        if (dseg is Arc) nc.arcUntil(dseg, p) else nc.move("G01", p)
        return Written(nc, kept, readNc(nc.lines))
    }

    /**
     * 다시 읽은 직접 보정 글의 줄 [from] ~ [until] 을 따라 [text] 기준이 처음 깨지는 곳의 경로 길이(원래 경로에 투영). 다 되면 null
     */
    private fun textShort(read: List<Seg?>, refs: List<NcRef>, from: Int, until: Int): Double? {
        for (i in from until until) {
            val s = read[i] ?: continue
            val ns = noseOf(s)
            val (a, _) = firstShort(ns, text, textShifts) ?: continue
            val tip = pointOn(ns, a) + tv
            val e = refs[i].element
            val seg = if (e < 0) Line(sol.entry, sol.program.segs.first().start) else sol.program.segs[e]
            return pos(e, paramOf(seg, tip))
        }
        return null
    }

    /** 후퇴를 붙인 글 */
    private class Back(val lines: List<String>, val from: Int, val kind: RetractKind, val clear: Double)

    /**
     * 후퇴. [base] = 마지막 블록까지 쓴 글(새로), [stopTip] = 그 끝의 실제 가상 인선, [k] = 그 자리의 요소, [entryZ] = 그 프로그램의
     * 진입점 Z, [cancel] = 기계 보정(첫 후퇴 블록에 G40 — 공구는 블록 끝의 보정 자리에서 그 블록 끝점(G40 의 인선)으로 곧게 간다).
     * ① X 를 축 쪽으로 빼고 Z 로 진입점 Z 까지: 안전 여유 이상 → ② 온 길을 거꾸로 → ① 여유 ≥ 0. 고른 것은 다시 읽은 글로 확인한다
     */
    private fun retract(base: () -> Written, stopTip: Pt, k: Int, entryZ: Double, cancel: Boolean): Back? =
        lift(base, stopTip, entryZ, cancel, safe) ?: reverse(base, stopTip, k, cancel) ?: lift(base, stopTip, entryZ, cancel, 0.0)

    /**
     * ① X 빼기 δ(반경, 축 쪽) → G00 Z. Z 로 빼는 움직임은 X 만 옮기면 깎는 쪽 여유가 +δ, 맞은편이 −δ 라 δ = 0 일 때의 껍질 여유 A · B 로
     * [level] 을 맞추는 δ 를 고른다: 깎는 쪽(바 · 인서트 날)이 level 이상인 δ ≥ lo = level − A, 맞은편이 level 이상인 δ ≤ hi = B − level.
     * 후보는 δ = 0(Z 로만 — 단면 · 바닥 · 가파른 벽처럼 노즈가 +Z 로 바로 떨어질 때)과 max(안전 여유, [HolderStop.LIFT]) 를 [lo, hi] 에 넣은 것
     * (반올림 한 칸 여유). 후보마다 [liftBy] 가 다시 읽은 글로 부분 여유와 노즈 원([noseLeaves] — 벽에 끌리지 않음)을 확인한다.
     * 빼기는 벽에 붙은 인서트 날에서 시작하므로 그 쪽은 보지 않는다. 기계 보정은 첫 후퇴 블록에 G40(빼지 않으면 G40 G00 Z)
     */
    private fun lift(base: () -> Written, stopTip: Pt, entryZ: Double, cancel: Boolean, level: Double): Back? {
        val n0 = stopTip - tv
        // 쓴 글 자세라 Z 반 칸 옮긴 것 가운데 좋은 쪽(값마다 따로 — δ 후보만 고르고 확인은 [liftBy] 가 한다)
        val sweeps = textShifts.map { sh -> clear(listOf(n0 + sh, n0 + sh + Pt(entryZ - stopTip.z, 0.0))) }
        val a = sweeps.maxOf { min(it.barCut.m, it.insCut.m) }
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
        for (delta in tries) liftBy(base, stopTip, entryZ, cancel, level, delta)?.let { return it }
        return null
    }

    private fun liftBy(base: () -> Written, stopTip: Pt, entryZ: Double, cancel: Boolean, level: Double, delta: Double): Back? {
        val n0 = stopTip - tv
        val w = base()
        val nc = w.nc
        val from = nc.lines.size
        nc.element = RETRACT
        val q1 = stopTip - Pt(0.0, delta)
        if (delta > 0) {
            nc.move(if (cancel) "G40 G01" else "G01", q1)
            nc.move("G00", Pt(entryZ, q1.x))
        } else {
            nc.move(if (cancel) "G40 G00" else "G00", Pt(entryZ, q1.x))
        }
        val read = readNc(nc.lines)
        if (read.size == from) return null
        // 다시 읽은 후퇴: 첫 블록은 실제 인선(기계 보정은 블록 끝의 보정 자리)에서. 빼기는 인서트 깎는 쪽을 보지 않는다
        val p1 = read[from]!!.end
        val firstNeed = Need(level - HolderStop.TEXT_TOL, if (delta > 0) Double.NEGATIVE_INFINITY else level - HolderStop.TEXT_TOL)
        val first = textClear(listOf(n0, p1 - tv), firstNeed)
        if (first.slack(firstNeed) < -HolderStop.OK_TOL) return null
        var low = first.least(delta <= 0)
        // 노즈 원: 벽에 붙은 채 끌리거나 다른 소재에 닿지 않게
        var travel = 0.0
        if (!noseLeaves(n0, p1 - tv, travel, level)) return null
        travel += (p1 - tv - n0).length
        for (i in from + 1 until read.size) {
            val s = read[i] ?: continue
            val zNeed = Need(level - HolderStop.TEXT_TOL, level - HolderStop.TEXT_TOL)
            val c = textClear(listOf(s.start - tv, s.end - tv), zNeed)
            if (c.slack(zNeed) < -HolderStop.OK_TOL) return null
            if (!noseLeaves(s.start - tv, s.end - tv, travel, level)) return null
            travel += lengthOf(s)
            low = min(low, c.least(true))
        }
        return Back(nc.lines.toList(), from, RetractKind.LIFT, low)
    }

    // ------------------------------------------------------------------ 후퇴의 노즈 원

    /** 소재 경계: 늘인 윤곽 + 입구 면(입구 점에서 위로) — 축 너머 맞은편은 [noseGap] 이 뒤집어 본다 */
    private val boundary: List<Seg> = bore.ext.let { e ->
        val a = e.first().start
        val b = e.last().end
        val mouth = if (a.z >= b.z) a else b
        e + Line(mouth, Pt(mouth.z, mouth.x + 1e5))
    }

    /** 소재 경계 전체: 깎는 쪽 [boundary] 와 축 너머 맞은편(뒤집은 것) */
    private val walls: List<Seg> = boundary + boundary.map { s ->
        fun m(p: Pt) = Pt(p.z, -p.x)
        when (s) {
            is Line -> Line(m(s.start), m(s.end))
            is Arc -> Arc(m(s.start), m(s.end), m(s.center), !s.cw)
        }
    }

    /**
     * 노즈 중심 [nose] 자세에서 바(볼록 다각형 — 앞은 주절삭날 연장선, 뒤는 공구대 면)와 소재 경계 사이 직선(유클리드) 거리와 소재 쪽 가장
     * 가까운 점. 바 변 · 소재 요소 쌍마다 정확한 최솟값(만나면 0): 두 끝에서 상대까지, 원호는 중심에서 변의 법선 방향 ± R 점까지
     */
    fun barGap(nose: Pt): Pair<Double, Pt> {
        val poly = tool.body.pts.map { it + nose }
        var best = Double.POSITIVE_INFINITY
        var at = poly.first()
        for (i in poly.indices) {
            val e = Line(poly[i], poly[(i + 1) % poly.size])
            if (lengthOf(e) <= EPS) continue
            for (w in walls) {
                val (d, p) = segGap(e, w)
                if (d < best) {
                    best = d
                    at = p
                }
            }
        }
        return best to at
    }

    /**
     * 노즈 중심이 [a] → [b] 로 곧게 가는 동안 노즈 원과 소재 사이 가장 짧은 거리(mm, 정확한 최솟값): 선분과 소재 경계(맞은편 포함) 사이 거리 − r.
     * 한 끝이 소재 안이면 −r(경계를 넘지 않고 소재 안에만 있는 움직임도 끝이 안이다)
     */
    private fun noseSweep(a: Pt, b: Pt): Double {
        if (abs(a.x) >= bore.rho(a.z) || abs(b.x) >= bore.rho(b.z)) return -r
        val e = Line(a, b)
        var d = Double.POSITIVE_INFINITY
        for (w in walls) d = min(d, if (lengthOf(e) <= EPS) distTo(w, a) else segGap(e, w).first)
        return d - r
    }

    /** 노즈 원과 소재 사이 거리(mm): 노즈 중심에서 소재 경계(맞은편 벽 포함)까지 − r. 중심이 소재 안이면 음수 */
    private fun noseGap(c: Pt): Double {
        if (abs(c.x) >= bore.rho(c.z)) return -r
        val m = Pt(c.z, -c.x)
        var d = Double.POSITIVE_INFINITY
        for (s in boundary) d = min(d, min(distTo(s, c), distTo(s, m)))
        return d - r
    }

    /**
     * 후퇴 직선 움직임(노즈 중심 [p0] → [p1], 후퇴 시작부터 [s0] mm 간 뒤)에서 노즈 원이 소재에서 떨어지나: 간 길이 s 에서
     * 거리 ≥ min(L, s/2), L = max([level], [MIN_LIFT]/2) — 벽에 붙은 채 끌리지 않고(처음엔 간 길이의 절반 이상 멀어짐) 그 뒤 L 이상.
     * 0.002 mm(처음 0.2 mm) · 0.01 mm 마다 재고, 판판한 곳(L 에 닿은 뒤)은 표본 사이(1.5 배 기울기)까지 덮게 그만큼 더 본다.
     * 쓴 글의 반올림(0.0007 mm)으로 멈춘 노즈가 벽에 그만큼 들어가 있을 수 있어 [NOSE_TOL] 까지 받는다
     */
    private fun noseLeaves(p0: Pt, p1: Pt, s0: Double, level: Double): Boolean {
        val big = max(level, MIN_LIFT / 2)
        val len = (p1 - p0).length
        var s = 0.0
        while (true) {
            val st = if (s0 + s < 0.2) 0.002 else 0.01
            val need = min(big, (s0 + s) / 2)
            val slop = if ((s0 + s) / 2 >= big) 0.75 * st else 0.0
            val p = if (len <= 0) p0 else p0 + (p1 - p0) * (s / len)
            if (noseGap(p) < need + slop - NOSE_TOL) return false
            if (s >= len) return true
            s = min(len, s + st)
        }
    }

    /** ② 온 길(가상 인선 경로)을 거꾸로 진입점까지. 기계 보정은 먼저 G40 으로 지금 인선 자리에 선다(공구는 거의 움직이지 않는다) */
    private fun reverse(base: () -> Written, stopTip: Pt, k: Int, cancel: Boolean): Back? {
        val w = base()
        val nc = w.nc
        val from = nc.lines.size
        nc.element = RETRACT
        if (cancel) {
            val n = nc.lines.size
            nc.move("G40 G01", stopTip)
            // 노즈R 0 이면 보정 자리 = 명령점이라 움직임이 없어 줄이 빠진다 — G40 만 쓴다
            if (nc.lines.size == n) {
                nc.lines += "G40"
                nc.refs += NcRef(RETRACT, 0, 1)
            }
        }
        var cur = stopTip
        for (j in k downTo 0) {
            val s = sol.program.segs[j]
            if (lengthOf(s) <= EPS) continue
            when (s) {
                is Line -> nc.move("G01", s.start)
                is Arc -> if ((cur - s.start).length > EPS) nc.arc(Arc(cur, s.start, s.center, !s.cw))
            }
            cur = s.start
        }
        nc.move("G01", sol.entry)
        val read = readNc(nc.lines)
        var low = Double.POSITIVE_INFINITY
        for (i in from until read.size) {
            val s = read[i] ?: continue
            // G40 블록: 공구는 블록 끝의 보정 자리(실제 인선)에서 그 블록 끝점으로
            val m = if (nc.lines[i].startsWith("G40")) Line(stopTip, s.end) else s
            val ns = noseOf(m)
            if (firstShort(ns, text, textShifts) != null) return null
            low = min(low, least(ns, false))
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

/** 점 p 에서 요소까지 거리 */
private fun distTo(s: Seg, p: Pt): Double = when (s) {
    is Line -> {
        val d = s.end - s.start
        val l2 = d dot d
        val t = if (l2 <= 0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0)
        (p - (s.start + d * t)).length
    }
    is Arc -> {
        val v = p - s.center
        val along = wrap2Pi((if (s.cw) -1 else 1) * (angleOf(v) - angleOf(s.start - s.center)))
        if (v.length > EPS && along <= s.sweepRad()) abs(v.length - s.radius) else min((p - s.start).length, (p - s.end).length)
    }
}

/** 점 p 가 원호 위(진행 방향 각이 원호 안)인가 */
private fun onArc(a: Arc, p: Pt): Boolean =
    wrap2Pi((if (a.cw) -1 else 1) * (angleOf(p - a.center) - angleOf(a.start - a.center))) <= a.sweepRad() + 1e-12

/** 요소 위에서 점 p 와 가장 가까운 점 */
private fun nearestOn(s: Seg, p: Pt): Pt = when (s) {
    is Line -> {
        val d = s.end - s.start
        val l2 = d dot d
        s.start + d * (if (l2 <= 0) 0.0 else (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0))
    }
    is Arc -> {
        val v = p - s.center
        if (v.length > EPS && onArc(s, p)) s.center + v.unit() * s.radius else if ((p - s.start).length <= (p - s.end).length) s.start else s.end
    }
}

/** 선분 [e] 와 요소 [w] 의 교점(없으면 null) */
private fun crossing(e: Line, w: Seg): Pt? {
    val d = e.end - e.start
    fun onE(p: Pt) = ((p - e.start) dot d) / (d dot d) in 0.0..1.0
    return when (w) {
        is Line -> {
            val f = w.end - w.start
            val den = d cross f
            if (abs(den) <= 1e-18) return null
            val t = ((w.start - e.start) cross f) / den
            val u = ((w.start - e.start) cross d) / den
            if (t in 0.0..1.0 && u in 0.0..1.0) e.start + d * t else null
        }
        is Arc -> Intersect.lineCircle(e.start, d, w.center, w.radius).firstOrNull { onE(it) && onArc(w, it) }
    }
}

/** 선분 [e] 와 요소 [w] 사이 가장 짧은 거리와 [w] 쪽 그 점(만나면 0) */
private fun segGap(e: Line, w: Seg): Pair<Double, Pt> {
    crossing(e, w)?.let { return 0.0 to it }
    val cands = mutableListOf<Pair<Double, Pt>>()
    for (p in listOf(e.start, e.end)) nearestOn(w, p).let { cands += (p - it).length to it }
    for (q in listOf(w.start, w.end)) cands += distTo(e, q) to q
    // 원호 안쪽 점: 이은 선이 변에 수직이고 원호의 반지름 방향인 곳 = 중심 ± R·(변의 법선)
    if (w is Arc) {
        val n = (e.end - e.start).unit().left()
        for (s in listOf(1.0, -1.0)) {
            val q = w.center + n * (w.radius * s)
            if (onArc(w, q)) cands += distTo(e, q) to q
        }
    }
    return cands.minBy { it.first }
}

/** 풀이의 NC 를 요소 [upTo] 앞까지 다시 쓴다 — RoundCalc 가 쓴 것과 같은 순서 · 같은 글(G00 진입 → G01 시작점 → 요소마다) */
private fun replay(sol: RoundSolution, upTo: Int): NcLines = NcLines().apply {
    element = -2
    move("G00", sol.entry)
    element = -1
    move("G01", sol.program.segs.first().start)
    for (k in 0 until upTo) {
        element = k
        val s = sol.program.segs[k]
        if (s is Arc) arc(s) else move("G01", s.end)
    }
}

/** 0.001 로 쓴 점(Fmt.nc 를 다시 읽은 값) */
private fun rounded(p: Pt) = Pt(Fmt.nc(p.z).toDouble(), Fmt.nc(p.xDia).toDouble() / 2)

/**
 * NC 글을 다시 읽는다: 줄마다 움직임(첫 줄 G00 은 시작점이라 null). 빠진 축은 앞 줄 값(모달), X 는 직경 → 반경.
 * G02/G03 은 끝점 · R(180° 이하 쪽)로 중심을 다시 구한다 — 제어기가 하는 대로. G40 같은 말은 건너뛴다
 */
internal fun readNc(lines: List<String>): List<Seg?> {
    var z: Double? = null
    var x: Double? = null
    val out = mutableListOf<Seg?>()
    for (line in lines) {
        var g = ""
        var nz = z
        var nx = x
        var rr: Double? = null
        for (w in line.split(' ')) {
            if (w.isEmpty()) continue
            when (w[0]) {
                'G' -> if (w in MOTIONS) g = w
                'X' -> nx = w.substring(1).toDouble() / 2
                'Z' -> nz = w.substring(1).toDouble()
                'R' -> rr = w.substring(1).toDouble()
            }
        }
        val from = if (z != null && x != null) Pt(z, x) else null
        require(nz != null && nx != null) { "좌표가 없는 줄: $line" }
        val to = Pt(nz, nx)
        out += when {
            from == null -> null
            g == "G02" || g == "G03" -> arcR(from, to, requireNotNull(rr) { "원호에 R 이 없다: $line" }, g == "G02")
            else -> Line(from, to)
        }
        z = nz
        x = nx
    }
    return out
}

private val MOTIONS = setOf("G00", "G01", "G02", "G03")

/** 끝점 R 원호(180° 이하). G02 = 이 그림에서 시계 → 중심은 현 진행 방향 오른쪽 */
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
