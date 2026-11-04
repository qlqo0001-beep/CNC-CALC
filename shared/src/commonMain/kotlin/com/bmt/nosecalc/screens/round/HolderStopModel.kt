package com.bmt.nosecalc.screens.round

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.HolderCut
import com.bmt.nosecalc.calc.HolderHit
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderPlan
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.ProgAt
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RetractKind
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.SafeEnd
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.StopWarning
import com.bmt.nosecalc.calc.ncWords
import com.bmt.nosecalc.screens.taper.directNc
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/*
 * 보링바 안전 끝점 화면(사용자 요청 2026-10-27 "간섭충돌 나는 이송 실제 좌표를 정확하게 … 간섭이 발생하지 않는 좌표까지 가공"):
 * 계산기 HolderStop.plan 의 결과를 지금 고른 NC(기계 보정 = 도면 명령 좌표 · 직접 보정 = 가상 인선)로 읽어 간섭 목록 · 가공 가능 끝점 · 잘린 NC 를 만든다.
 * 줄 번호는 NC 카드 그대로(0부터, 직접 보정은 첫 줄 주석까지 센다). 화면(HolderStopCards.kt)과 골든(UiGoldenTest)이 같은 글을 쓴다
 */

/** 0.1 까지(5 는 올림): 1.626 → "1.6", 2 → "2.0" */
internal fun fixed1(v: Double) = Fmt.fixed3(floor(v * 10 + 0.5) / 10).dropLast(2)

/** 좌표 글 'X18.006 Z-11.409'(X 직경) */
internal fun xz(p: Pt) = "X${Fmt.fixed3(p.xDia)} Z${Fmt.fixed3(p.z)}"

/** 부분 · 벽 이름: '바 · 맞은편 벽', '공구대 · 입구 끝면', '최소 가공경 Ø20' */
internal fun partName(part: HolderPart, opposite: Boolean, bar: BoringBar): String = when (part) {
    HolderPart.BAR -> "바 · ${if (opposite) "맞은편 벽" else "깎는 쪽 벽"}"
    HolderPart.INSERT -> "인서트 · ${if (opposite) "맞은편 벽" else "깎는 쪽 벽"}"
    HolderPart.TURRET -> "공구대 · 입구 끝면"
    HolderPart.MIN_BORE -> "최소 가공경 Ø${plain(bar.dMin ?: 0.0)}"
}

/**
 * 닿는 곳의 깊이 글. 인서트 · 바는 반경 겹침과 그 점이 인선에서 이송 방향으로 앞 · 뒤 얼마인지 — 정방향은 +Z 가 뒤, 뒤보링(reverse — 이송 +Z)은 +Z 가 앞
 */
internal fun hitDetail(hit: HolderHit, reverse: Boolean): String = when (hit.part) {
    HolderPart.INSERT, HolderPart.BAR -> {
        val side = if ((hit.behind < 0) != reverse) "앞" else "뒤"
        "반경 겹침 최대 ${Fmt.fixed3(hit.depth)} mm (인선에서 ${fixed1(abs(hit.behind))} mm $side)"
    }
    HolderPart.TURRET -> "돌출 길이가 ${Fmt.fixed3(hit.depth)} mm 모자람"
    HolderPart.MIN_BORE -> "헤드가 들어가는 구멍이 반경 ${Fmt.fixed3(hit.depth)} mm 좁음"
}

/** 두 프로그램 좌표 가운데 지금 NC 의 것과 그 카드 줄(0부터). 기계 보정인데 도면 좌표 풀이가 없으면 직접 보정 값 */
internal fun progOf(p: ProgAt, comp: Boolean): Pair<Pt, Int> {
    val m = p.machine
    val ml = p.machineLine
    return if (!comp && m != null && ml != null) m to ml else p.direct to p.directLine + 1
}

/**
 * 간섭 목록 한 줄(지금 NC 기준). [line] · [lineTo] = 닿기 시작 · 끝의 전체 NC 카드 줄(0부터), [from] · [to] = 닿기 시작 · 끝 좌표
 * (기계 보정 = 도면 명령 좌표 — 진입점 · 진입 이동은 기계 보정 프로그램 자기 진입(회사 꼴의 Z20. 자리 · 보정 켠 진입 블록)에서 닿는 명령점, 직접 보정 = 가상 인선),
 * [part] = 부분 · 벽, [detail] = 깊이
 */
internal class HitRow(val hit: HolderHit, val part: String, val line: Int, val lineTo: Int, val from: Pt, val to: Pt, val detail: String) {
    /** '6줄' · '5~6줄'(1부터) */
    val lines: String get() = if (line == lineTo) "${line + 1}줄" else "${line + 1}~${lineTo + 1}줄"
    val coords: String get() = "${xz(from)} → ${xz(to)}"
    val text: String get() = "$lines $part: $coords, $detail"
}

/**
 * 간섭 목록: 바 · 인서트 · 공구대 닿음을 먼저, 최소 가공경(입체 어림)은 뒤에 — 둘 다 가공 순서. reverse = 뒤보링.
 * 기계 보정은 진입이 직접 보정과 달라(도면 G00 점 · 보정 시작 블록) 그 프로그램 자기 진입에서 닿는 곳 + 형상(같은 노즈 경로)에서 닿는 곳
 */
internal fun hitRows(plan: HolderPlan, comp: Boolean, bar: BoringBar, reverse: Boolean): List<HitRow> {
    val own = plan.machineEntry
    val list = if (comp || own == null) plan.hits else own + plan.hits.filter { it.hit.element >= 0 }
    return list.sortedBy { it.hit.part == HolderPart.MIN_BORE }.map { h ->
        val (a, la) = progOf(h.first, comp)
        val (b, lb) = progOf(h.last, comp)
        HitRow(h.hit, partName(h.hit.part, h.hit.opposite, bar), la, lb, a, b, hitDetail(h.hit, reverse))
    }
}

/** 가공 가능 끝점 카드의 모양: 끊고 빠짐 · 끝까지 안전 · 진입점부터 안 됨 · 깎기 전에 멈춤 · 기계 보정 잘린 NC 없음 */
internal enum class StopKind { CUT, CLEAR, UNSAFE, NOTHING, NO_MACHINE }

/**
 * 가공 가능 끝점(지금 NC 기준). [head] = 한 줄 머리, [where] = 끊은 줄('6줄 중간에서 끊음'), [lack] = 안전 끝점보다 덜 깎음(기계 보정이 블록을 그 자리에서
 * 끝낼 수 없을 때), [near] = 그곳의 가장 가까운 부분 · 여유(직선 거리 — 공구대는 Z), [face] = 깎는 면 예외가 쓰인 곳(작은 글 — 바 앞면이 지금 깎는 면에
 * 안전 여유보다 가까이 붙음), [retract] = 후퇴 방식 한 줄, [note] = 설명, [tight] = 후퇴 여유가 안전 여유보다 좁음(노랑).
 * [nc] = 카드에 줄 잘린 NC(직접 보정은 첫 줄 주석부터), [endLine] = 끊은 마지막 블록의 카드 줄(0부터), [retractLines] = 후퇴 카드 줄,
 * [at] = 멈추는 좌표(쓴 글 그대로 — 깎기 전에 멈추면 정확한 안전 끝점), [nose] = 그때 노즈 중심(보링바를 그릴 자리), [cut] = 잘린 프로그램,
 * [exitOnly] = 기계 보정 형상은 끝까지 안전하고 원래 끝(빼는 줄 → G40 → G0Z200.)에서만 닿아 빠지는 길만 바꾼 것([StopWarning.MACHINE_EXIT])
 */
internal class StopView(
    val kind: StopKind,
    val head: String,
    val where: String? = null,
    val near: String? = null,
    val retract: String? = null,
    val note: String? = null,
    val tight: String? = null,
    val nc: List<String>? = null,
    val endLine: Int? = null,
    val retractLines: IntRange? = null,
    val at: Pt? = null,
    val nose: Pt? = null,
    val cut: HolderCut? = null,
    val lack: String? = null,
    val face: String? = null,
    val exitOnly: Boolean = false,
) {
    /** 골든 · 시험용 한 덩어리 글(카드에 보이는 차례) */
    val text: String get() = listOfNotNull(head, where, lack, near, face, retract, note, tight).joinToString("\n")
}

/**
 * 가공 가능 끝점. comp = 직접 보정(G40 프로그램 · 가상 인선 · 첫 줄 주석), 아니면 기계 보정(도면 좌표 프로그램 [drawn] · 도면 명령 좌표 — 마지막 블록은
 * 빼는 줄과의 보정선 교점에 노즈가 안전 끝점에 서는 명령점 P′, 그 뒤 빼는 줄 → G40 → G0Z200.). [sol] = G40 풀이, noseR · bar = 칸 값.
 * 기계 보정은 그 프로그램 자기 진입(Z20. 자리 · 보정 중 진입 블록)으로 본 끝점(plan.machineEnd — 원래 끝에서 닿으면 경로 끝) — 직접 보정과 진입이 다르다
 */
internal fun stopView(plan: HolderPlan, comp: Boolean, sol: RoundSolution, drawn: RoundSolution?, noseR: Double, bar: BoringBar): StopView {
    val safe = "${plain(plan.safe)} mm"
    if (!comp && drawn != null && plan.machineEntry != null) return machineStop(plan, sol, drawn, bar, safe)
    if (StopWarning.UNSAFE_AT_ENTRY in plan.warnings) {
        return StopView(
            StopKind.UNSAFE, "진입점부터 안전 여유가 모자랍니다",
            note = "G00 진입점에 선 자세부터 보링바가 소재와 $safe 떨어지지 못해 이 보링바로는 깎을 수 없습니다. 다른 보링바를 고르거나 안전 여유를 줄이세요. 닿는 곳은 위 목록에 있습니다.",
        )
    }
    val end = plan.end ?: return StopView(StopKind.CLEAR, "끝까지 안전합니다", note = clearNote(plan, comp, sol, drawn, safe))
    val near = nearText(end, bar, safe)
    val cut = if (comp) plan.direct else plan.machine
    if (cut == null) {
        // 깎기 전에 멈춤: 끝점은 진입 이동 위 — 그 자리(정확한 값)와 보링바 자세만
        val at = progOf(end.at, comp).first
        return if (StopWarning.NOTHING_CUT in plan.warnings) {
            StopView(
                StopKind.NOTHING, "첫 가공점에 닿기 전에 멈춥니다", near = near,
                note = "진입 이동 ${xz(at)} 에서 안전 여유가 모자라 깎는 것이 없습니다. 다른 보링바를 고르거나 안전 여유를 줄이세요.",
                at = at, nose = end.pose.nose,
            )
        } else {
            noMachine(end, near, plan.safe)
        }
    }
    return cutView(plan, cut, end, comp, sol, drawn, noseR, near, safe)
}

/** 기계 보정 프로그램의 끝점 카드: 진입(Z20. 자리)부터 안 됨 · 진입 블록에서 멈춤 · 끝까지 안전 · 잘린 NC 없음 · 끊고 빠짐 */
private fun machineStop(plan: HolderPlan, sol: RoundSolution, drawn: RoundSolution, bar: BoringBar, safe: String): StopView {
    val end = plan.machineEnd ?: return StopView(StopKind.CLEAR, "끝까지 안전합니다", note = clearNote(plan, false, sol, drawn, safe))
    val near = nearText(end, bar, safe)
    // 직접 보정은 진입이 달라 깎을 수 있나(잘린 NC 가 있거나 끝까지 안전)
    val direct = plan.direct != null || (plan.end == null && StopWarning.UNSAFE_AT_ENTRY !in plan.warnings)
    val other = if (direct) "직접 보정(G40)은 진입이 달라 깎을 수 있습니다 — 보정 방식을 직접 보정으로 바꾸세요." else "다른 보링바를 고르거나 안전 여유를 줄이세요."
    if (end.element == -2) {
        return StopView(
            StopKind.UNSAFE, "진입점부터 안전 여유가 모자랍니다", near = near,
            note = "기계 보정(${sol.side}) 프로그램의 진입 시작(${xz(drawn.approach.firstOrNull() ?: drawn.entry)})부터 " +
                "보링바가 소재와 $safe 떨어지지 못해 이 프로그램으로는 깎을 수 없습니다. $other",
        )
    }
    if (end.element == -1) {
        val at = end.at.machine ?: end.pose.drawn
        // 진입 블록: Z20. 자리 → (X 맞춤) → 진입점 → 첫 점(모두 보정 중 — 요소 −1, 줄은 at.machineLine)
        val first = drawn.ncRefs.indexOfFirst { it.element == -1 }
        val line = end.at.machineLine ?: first
        val block = if (line == first) "첫 깎는 줄(${line + 1}줄 — 진입점에서 첫 점으로)" else "진입 줄(${line + 1}줄 — 보정 켠 채 진입점으로)"
        return StopView(
            StopKind.NOTHING, "첫 가공점에 닿기 전에 멈춥니다", near = near,
            note = "$block 명령점 ${xz(at)} 에서 안전 여유가 모자라 깎는 것이 없습니다. $other",
            at = at, nose = end.pose.nose,
        )
    }
    val cut = plan.machine ?: return noMachine(end, near, plan.safe)
    return cutView(plan, cut, end, false, sol, drawn, 0.0, near, safe)
}

/**
 * 기계 보정 잘린 NC 를 만들 수 없음: 안전 끝점이 첫 가공점 바로 뒤. 깃발 자리는 블록을 거기서 끝낼 명령점, 그런 명령점이 없으면(그 자세에서 블록을 끝낼 수 없음)
 * 노즈가 닿는 도면 점
 */
private fun noMachine(end: SafeEnd, near: String, safe: Double) = StopView(
    StopKind.NO_MACHINE, "기계 보정 잘린 NC 를 만들 수 없습니다", near = near,
    note = "안전 끝점이 첫 가공점 바로 뒤라 기계 보정(G41/G42) 프로그램은 끊을 자리가 없습니다. 직접 보정(G40)으로 바꾸면 잘린 NC 가 나옵니다.",
    at = end.at.machine ?: end.pose.drawn, nose = end.pose.nose, face = faceNote(end, safe),
)

/** 끊고 빠짐: 잘린 NC · 끊은 줄 · 후퇴 */
private fun cutView(
    plan: HolderPlan, cut: HolderCut, end: SafeEnd, comp: Boolean, sol: RoundSolution, drawn: RoundSolution?, noseR: Double, near: String, safe: String,
): StopView {
    val shift = if (comp) 1 else 0
    val nc = if (comp) directNc(cut.nc, noseR, sol.tip) else cut.nc
    val full = if (comp) directNc(sol.nc, noseR, sol.tip) else drawn?.nc
    val endLine = cut.retractFrom - 1 + shift
    // 마지막 블록이 원래 글 그대로면 그 블록 끝에서, 아니면 블록 중간에서 끊은 것
    val whole = full?.getOrNull(endLine) == nc[endLine]
    val back = (cut.retractFrom + shift) until nc.size
    val tight = if (StopWarning.RETRACT_TIGHT in plan.warnings && cut.retractClear < plan.safe - HolderStop.TEXT_TOL) {
        "후퇴 중 가장 좁은 여유(직선 거리) ${Fmt.fixed3(cut.retractClear)} mm 가 안전 여유 $safe 보다 좁습니다(겹치지는 않음). 후퇴 길을 확인하세요."
    } else {
        null
    }
    // 기계 보정은 블록을 안전 끝점에서 끝낼 수 없으면(그 자세로 블록을 끝낼 수 없음 — 앞 요소 끝까지, 또는 공구 쪽 빼는 줄에서 바가 소재로 들어감 — 앞으로 당김)
    // 그만큼 앞에서 멈춘다 — 직접 보정보다 덜 깎는 양(반올림 한 칸보다 클 때만)
    val lack = if (!comp && cut.lack > LACK_SHOWN) {
        "안전 끝점보다 경로로 ${Fmt.fixed3(cut.lack)} mm 앞에서 끊음 — 이 블록을 안전 끝점에서 끝내고 공구 쪽으로 뺄 수 없어 그만큼 앞에서 멈춥니다(직접 보정은 그만큼 더 깎음)."
    } else {
        null
    }
    // 기계 보정 형상은 끝까지 안전하고 원래 끝(빼는 줄 → G40 → G0Z200.)에서만 닿음 — 끝점은 경로 끝, 빠지는 길만 바꿨다
    val exitOnly = !comp && StopWarning.MACHINE_EXIT in plan.warnings
    return StopView(
        StopKind.CUT, if (exitOnly) "끝까지 깎고 빠지는 길만 바꿉니다" else "여기까지 깎고 빠집니다",
        where = if (exitOnly) "${endLine + 1}줄(형상 끝)까지 깎음" else "${endLine + 1}줄 ${if (whole) "끝" else "중간"}에서 끊음",
        near = near,
        retract = retractText(cut, nc.slice(back), cancel = !comp),
        tight = tight,
        nc = nc, endLine = endLine, retractLines = back, at = cut.stop, nose = cut.pose.nose, cut = cut,
        lack = lack, face = faceNote(end, plan.safe), exitOnly = exitOnly,
    )
}

/** 덜 깎은 양을 보이는 문턱(mm) — 반올림 되돌리기(0.001 칸)보다 큰 것만 */
private const val LACK_SHOWN = 0.01

/**
 * 안전 끝점에서 기준에 가장 가까운 부분 · 여유. 여유는 공구 단면과 소재 사이 직선(유클리드) 거리(사용자 결정 2026-10-28 — 공구대는 면끼리라 Z 여유,
 * 깎는 면 예외는 뺀 값). 인서트의 깎는 쪽 벽은 기준이 '겹치지 않음'(날은 깎는 벽에 붙어 깎는다). 겹친 자세(기계 보정 G00 점부터 모자람)는 겹침 깊이
 * (반경 겹침 — 노즈 원이면 그 깊이)를 적는다. 부분이 면을 넘는 순간 여유가 뛰는 곳(예: 최소 가공경 구간이 입구 끝면을 넘음 — 끝면 앞은 공기라 여유가 끝없다)은
 * 숫자 대신 그렇다고 적는다
 */
private fun nearText(end: SafeEnd, bar: BoringBar, safe: String): String {
    val rule = if (end.part == HolderPart.INSERT && !end.opposite) "겹치지 않음 — 날은 깎는 벽에 붙어도 됨" else "안전 여유 $safe"
    val m = end.margin
    val gap = when {
        !m.isFinite() || m >= 1e4 -> "면을 넘자마자 여유가 모자람"
        // 0.001 자리에서 음수로 찍히는 것만 겹침(인서트 깎는 쪽은 −1e-6 까지 겹치지 않음)
        m < -0.0005 -> "겹침 ${Fmt.fixed3(-m)} mm"
        end.part == HolderPart.TURRET -> "Z 여유 ${Fmt.fixed3(m)} mm"
        else -> "여유(직선 거리) ${Fmt.fixed3(m)} mm"
    }
    return "가장 가까운 곳: ${partName(end.part, end.opposite, bar)} — $gap (기준: $rule)"
}

/**
 * 깎는 면 예외(사용자 결정 2026-10-28)가 끝점에서 쓰였으면 그 한 줄(작은 글). 형상 요소(k ≥ 0)에서 바와 소재 직선 거리([SafeEnd.gap] — 깎는 면도 넣은 것)가
 * 안전 여유보다 짧으면 그 가장 가까운 소재는 깎는 면이다 — 끝점까지는 바의 나머지가 안전 여유를 지켰다. 막힌 바닥 · 단 면을 깎는 바 앞면(주절삭날
 * 연장선)이 면에서 공구 모양만큼만 떨어지는 것(예 C 0.226). 진입(−2 · −1)에는 예외가 없다
 */
private fun faceNote(end: SafeEnd, safe: Double): String? {
    if (end.element < 0 || !end.gap.isFinite() || end.gap >= safe - HolderStop.TEXT_TOL) return null
    val p = end.gapAt
    val side = if (p.x < 0) "맞은편 " else ""
    return "깎는 면 예외: 바 앞면이 지금 깎는 면(${side}X${Fmt.fixed3(abs(p.xDia))} Z${Fmt.fixed3(p.z)})에서 ${Fmt.fixed3(end.gap)} mm — " +
        "바 앞면은 주절삭날 연장선이라 깎는 면에는 겹치지 않음만 봅니다."
}

/**
 * 후퇴 방식 한 줄: X 빼기 → Z(그 줄 글) 또는 온 길을 거꾸로(줄 수), 그리고 후퇴 중 가장 좁은 여유. cancel = 기계 보정(회사 꼴) — 벽에서 공구 쪽으로
 * 빼는 줄(보정 켠 채) → G40 혼자 줄 → G0Z200.
 */
private fun retractText(cut: HolderCut, lines: List<String>, cancel: Boolean): String {
    val how = when {
        cancel -> "벽에서 공구 쪽으로 빼고(보정 켠 채) G40 → Z 로: ${lines.joinToString(" → ")}"
        cut.retract == RetractKind.REVERSE -> "온 길을 거꾸로 이송으로 진입점까지 (${lines.size}줄)"
        lines.size >= 2 -> "X 를 축 쪽으로 빼고 Z 로: ${lines.joinToString(" → ")}"
        else -> "그 자리에서 Z 로 (노즈가 벽에서 바로 떨어짐): ${lines.joinToString(" → ")}"
    }
    val clear = cut.retractClear.takeIf { it.isFinite() && it < 1e4 }?.let { " · 후퇴 중 가장 좁은 여유(직선 거리) ${Fmt.fixed3(it)} mm" }.orEmpty()
    return "후퇴: $how$clear"
}

/** 끝까지 안전할 때: 안전 여유와 바 · 소재 사이 가장 좁은 여유(직선 거리 — 깎는 면은 뺀 것, 지금 NC 의 줄) */
private fun clearNote(plan: HolderPlan, comp: Boolean, sol: RoundSolution, drawn: RoundSolution?, safe: String): String {
    val prog = if (comp) sol else drawn
    val at = plan.result.barClearAt
    val e = plan.result.barClearElement
    val narrow = plan.result.barClear
    val where = if (prog != null && at != null && e != null && narrow != null && narrow.isFinite()) {
        " 바와 소재 사이 가장 좁은 여유(직선 거리) ${Fmt.fixed3(narrow)} mm (${cardLine(prog, comp, e, at) + 1}줄)."
    } else {
        ""
    }
    return "안전 여유 $safe 를 지키며 경로 끝까지 깎습니다.$where"
}

/**
 * NC 줄을 [from] 에서 이어 읽은 움직임(그림용 — 잘린 NC 의 후퇴 길): 빠진 축 · 이동 G 는 앞 값(모달 — 첫 줄 앞은 G1: 후퇴 첫 줄이 G 를 빼는 것은
 * 앞 깎는 줄이 G1 일 때뿐, G 는 수로 본다), X 는 직경 → 반경, G2/G3 은 끝점 · R(180° 이하 쪽)로 중심을 다시 구한다 — 제어기가 하는 대로.
 * G40 같은 말은 건너뛰고, 움직임이 없는 줄은 뺀다
 */
internal fun ncMoves(from: Pt, lines: List<String>): List<Seg> {
    var cur = from
    var g = 1
    val out = mutableListOf<Seg>()
    for (line in lines) {
        var z = cur.z
        var x = cur.x
        var rr: Double? = null
        for (w in ncWords(line)) {
            val v = w.drop(1).toDoubleOrNull()
            when (w.firstOrNull()) {
                'G' -> w.drop(1).toIntOrNull()?.takeIf { it in 0..3 && w.length <= 3 }?.let { g = it }
                'X' -> if (v != null) x = v / 2
                'Z' -> if (v != null) z = v
                'R' -> rr = v
            }
        }
        val to = Pt(z, x)
        if ((to - cur).length < 1e-9) continue
        val r = rr
        out += if ((g == 2 || g == 3) && r != null) arcR(cur, to, r, g == 2) else Line(cur, to)
        cur = to
    }
    return out
}

/** 끝점 R 원호(180° 이하). G2 = 이 그림에서 시계 → 중심은 현 진행 방향 오른쪽 */
private fun arcR(a: Pt, b: Pt, r: Double, cw: Boolean): Arc {
    val ch = b - a
    val h = ch.length / 2
    val d = sqrt(max(r * r - h * h, 0.0))
    val left = Pt(-ch.x / ch.length, ch.z / ch.length)
    return Arc(a, b, (a + b) * 0.5 + left * (if (cw) -d else d), cw)
}
