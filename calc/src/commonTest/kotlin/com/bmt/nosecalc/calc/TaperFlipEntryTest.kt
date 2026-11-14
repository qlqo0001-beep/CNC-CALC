package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.verify.GeometricVerifyTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 통합 고침 2026-11-12 — 공작물 · 공구가 상할 수 있는 테이퍼 NC 두 가지. 글을 시뮬레이터(형식 C)로 돌려 노즈를 잰다.
 * - T1: 뒤집은(T1 ⇄ T2) 기계 보정의 진입 급송이 깎을 면 · 시작 좌표의 벽에 붙어 지났다(라운드 V1 과 같은 꼴 — 골든 taper-b-flip-outer3 의
 *   `G41` `G0X52.Z20.` `Z-20.` `G1X50.F.1` 은 G41 이 걸린 Z 급송의 노즈 밑이 명령선에서 2r 밀려 Ø50 위 0.2 로 지나고, 급송 끝이 벽 평면에 틈 0).
 *   비운 수평 시작은 X 급송 끝의 노즈가 시작 요소(깎지 않는 면)에 틈 0 으로 내려앉았다.
 * - T2: 내경 직접 보정 끝(단면을 축까지)의 후퇴가 축 너머로 Z 여유만큼 G0 로 가서 노즈가 맞은편(같은 회전면)을 팠다(검증 V7-2 — Z 여유 5 · 노즈 0.8 에서 0.11~0.16).
 * 여기서 c = max(Z 여유, 노즈R).
 */
class TaperFlipEntryTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    private fun ok(r: TaperResult, tag: String = ""): TaperSolution = assertNotNull(r.solution, "$tag ${r.warnings}")

    /** [mirror] = 화면 역방향(거울 풀이)의 계산기 글 — 부품 앞이 계산기 −Z 라 공구 교환 자리도 그쪽에 둔다 */
    private fun sim(nc: List<String>, r: Double, tip: Int, mirror: Boolean = false): SimRun = GSim.run(
        SimInput(
            (pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = CompStartup.C, home = Pt(if (mirror) -100.0 else 100.0, 100.0)),
            mapOf(1 to ToolOffset(r, tip)),
        ),
    )

    /** 요소 위 점(0.02 mm 마다) */
    private fun dense(s: Seg): List<Pt> = when (s) {
        is Line -> {
            val n = max(2, ((s.end - s.start).length / 0.02).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            val sw = s.sweepDeg * PI / 180 * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * s.radius / 0.02).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * s.radius }
        }
    }

    /** 소재 = 도면 + 축 거울(회전체라 맞은편도 같은 면) */
    private fun material(s: TaperSolution) = s.drawing.segs + s.drawing.segs.map(FrameMaterial::mirror)

    /** 막힌 시작이면 (시작 좌표, 너머 쪽 단위 방향) — 계산기와 따로 정한다. 짧은 막힌 시작을 뺄 자리가 없어 한 직선으로 들어오는 꼴(SHORT_END_PAST)은 빼고 */
    private fun startWall(s: TaperSolution, w: List<TaperWarning>, inner: Boolean, mirror: Boolean, r: Double): Pair<Pt, Pt>? {
        if (!s.fromStart) return null
        val first = s.drawing.segs.first()
        val uF = (first.end - first.start).unit()
        val blocked = if (abs(uF.z) > abs(uF.x)) (if (mirror) uF.z < 0 else uF.z > 0) else (uF.x > 0) != inner && !(!inner && first.start.x <= 1e-9)
        val nF = if (s.side == Side.G41) uF.left() else uF.left() * -1.0
        val short = ((s.center.segs[0].end - (first.start + nF * r + uF * r)) dot uF) < 0.05
        return if (blocked && !(short && TaperWarning.SHORT_END_PAST in w)) first.start to uF * -1.0 else null
    }

    /**
     * 기계 보정 글의 진입 급송(보정 혼자 줄 · 공구 교환 자리에서 오는 첫 G0X…Z20. 뒤, 첫 이송 앞)의 노즈와 소재(도면 + 축 거울 + [wall]) 사이 가장 작은 틈.
     * 진입에서 멈추면 null
     */
    private fun rapidGap(s: TaperSolution, r: Double, wall: Pair<Pt, Pt>?, mirror: Boolean = false): Double? {
        val run = sim(s.ncG41G42, r, s.tip, mirror)
        val moves = run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size + 2 }
        val feed = moves.indexOfFirst { it.kind == MoveKind.FEED }
        if (feed <= 0) return null
        val stop = run.stop
        if (stop != null && stop.line <= moves[feed].line) return null
        val mat = material(s)
        return moves.take(feed).flatMap { dense(it.nose) }.minOf { p ->
            min(mat.minOf { distTo(it, p) }, wall?.let { (at, back) -> -((p - at) dot back) } ?: Double.MAX_VALUE)
        } - r
    }

    // ---------------------------------------------------------------- T1: 뒤집은 기계 보정의 진입 급송

    /** 뒤집기 골든의 입력(화면 예제 값)과 같은 꼴 — 막힌 시작(외경 · 내경), 비운 수평 시작(2중 · 원호 · 형상 1 · 내경) */
    private val goldens = listOf(
        "outer3" to TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, cornerR1 = 0.5, start = -20.0, flip = true),
        "inner1" to TaperInput(inner = true, type = 1, x1 = 30.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, start = -20.0, flip = true),
        "arc" to TaperInput(type = 3, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, cornerR1 = 1.5, cornerR2 = 1.0, flip = true, arc = true, arcR = 8.0, arcConvex = false),
        "outer1" to TaperInput(type = 1, x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, flip = true),
        "inner1 blank" to TaperInput(inner = true, type = 1, x1 = 30.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, flip = true),
    )

    private val double = DoubleTaperInput(x1 = 40.0, z1 = -10.0, x2 = 50.0, z2 = -5.0, x3 = 40.0, z3 = 0.0, cornerR2 = 1.0, flip = true)

    /** 뒤집기 골든의 입력들(Z 여유 1 · 0 · 5, 노즈 0.4 · 0.8): 진입 급송 노즈가 도면 · 축 거울 · 막힌 시작의 벽에서 c 이상 */
    @Test
    fun flippedMachineRapidKeepsZClear() {
        val fails = mutableListOf<String>()
        for (zc in listOf(1.0, 0.0, 5.0)) for (r in listOf(0.4, 0.8)) {
            val cases = goldens.map { (n, i) -> Triple(n, i.inner, TaperCalc.calc(i.copy(zClear = zc, noseR = r))) } +
                Triple("double", false, TaperCalc.calcDouble(double.copy(zClear = zc, noseR = r)))
            for ((name, inner, res) in cases) {
                val s = ok(res, name)
                val c = max(zc, r)
                val gap = rapidGap(s, r, startWall(s, res.warnings, inner, false, r))
                if (gap == null || gap < c - 2e-3) fails += "$name Z 여유 $zc 노즈 $r: 급송 틈 $gap < $c\n${s.ncG41G42.joinToString(" ")}"
            }
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 격자: 뒤집은 홑테이퍼(외경 · 내경 형상 1~5 × 직선 · T1–T2 원호 볼록 · 오목) · 2중 테이퍼(형상 1~4) × 노즈 0.2 · 0.4 · 0.8 · 1.2 × Z 여유 0 · 1 · 5 ×
     * 시작 비움 · 3 · 1 mm × 끝 비움 · 3 mm × 모서리 R 0 · 0.5. 기계 보정의 진입 급송 노즈가 소재에서 c 이상인 것이 거의 다이고, 자리가 없어도 c/4 밑으로는 안 간다.
     * 화면 역방향(거울) + 뒤집기는 실제로는 부품 앞에서 들어와(계산기 역방향 + 뒤집기와 같은 글) 이 고침 밖이라 넣지 않는다
     */
    @Test
    fun flippedEntryRapidSweep() {
        val count = sortedMapOf<String, Int>()
        val worst = sortedMapOf<String, Pair<Double, String>>()
        val fails = mutableListOf<String>()
        val alarms = mutableListOf<String>()
        var runs = 0
        var full = 0
        var least = Double.MAX_VALUE
        fun see(tag: String, inner: Boolean, mirror: Boolean, res: TaperResult, r: Double, c: Double) {
            val s = res.solution ?: return
            // 오목 R ≤ 노즈R · 노즈보다 짧은 경사(앱 경고)는 제어기가 형상에서 알람을 내는 꼴 — 시뮬레이터가 앞을 읽어 진입 줄에서 멈춰 세기만 한다
            if (TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings) {
                count["형상 알람 꼴"] = (count["형상 알람 꼴"] ?: 0) + 1
                return
            }
            val gap = rapidGap(s, r, startWall(s, res.warnings, inner, mirror, r), mirror)
            if (gap == null) {
                count["진입 알람"] = (count["진입 알람"] ?: 0) + 1
                if (alarms.size < 4) alarms += "$tag: 진입에서 멈춤 ${sim(s.ncG41G42, r, s.tip, mirror).stop?.let { "${it.code} ${it.line - pre.size}줄" }} ${res.warnings}\n${s.ncG41G42.joinToString(" ")}"
                if (fails.size < 30) fails += "$tag: 진입에서 멈춤 ${res.warnings}\n${s.ncG41G42.joinToString(" ")}"
                return
            }
            runs++
            least = min(least, gap)
            if (gap >= c - 2e-3) full++
            val key = (if (inner) "내경" else "외경") + " " + when {
                gap >= c - 2e-3 -> "c 이상"
                gap >= c / 2 - 2e-3 -> "c/2 이상"
                gap >= c / 4 - 2e-3 -> "c/4 이상"
                else -> "c/4 미만"
            }
            count[key] = (count[key] ?: 0) + 1
            val w = worst[key]
            if (w == null || gap / c < w.first) worst[key] = (gap / c) to "$tag 틈 $gap (c $c)\n${s.ncG41G42.take(8).joinToString(" ")}"
            if (gap < c / 4 - 2e-3 && fails.size < 60) fails += "$tag: 급송 틈 $gap < c/4 (c $c) ${res.warnings}\n${s.ncG41G42.joinToString(" ")}"
        }
        for (inner in listOf(false, true)) for (type in 1..5) for (kind in 0..2) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0)) {
            val up = if (inner == (type == 5)) 1.0 else -1.0
            val x1 = if (inner) (if (type == 5) 30.0 else 40.0) else (if (type == 5) 50.0 else 40.0)
            val x2 = x1 + up * 10.0
            val arcR = TaperCalc.arcMinR(x1, 0.0, x2, -5.0)?.let { ceil(it * 2 * 1000) / 1000 }
            for (lead in listOf(null, 3.0, 1.0)) for (tail in listOf(null, 3.0)) for (cr in listOf(0.0, 0.5)) {
                val start = lead?.let { if (type <= 2) x1 - up * 2 * it else it }
                val end = tail?.let { if (type == 2 || type == 4) x2 + up * 2 * it else -5.0 - it }
                // 그림 순서(T1 오른쪽)로 세워 화면처럼 칸을 맞바꿔 뒤집는다
                val base = TaperInput(
                    inner, type, false, r, null, x1, 0.0, x2, -5.0, null, zc, cr, cr, start = start, end = end,
                    arc = kind > 0, arcR = arcR, arcConvex = kind == 1,
                )
                val inp = base.copy(x1 = base.x2, z1 = base.z2, x2 = base.x1, z2 = base.z1, cornerR1 = base.cornerR2, cornerR2 = base.cornerR1, start = base.end, end = base.start, flip = true)
                see(inp.toString(), inner, false, TaperCalc.calc(inp), r, max(zc, r))
            }
        }
        for (inner in listOf(false, true)) for (type in 1..4) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0)) {
            // 그림 순서 X(외경 기준 — 내경은 구멍 쪽으로 같은 꼴): 1 두 번 커짐 · 2 가운데 큼 · 3 가운데 작음 · 4 두 번 작아짐
            val xs = when (type) {
                1 -> listOf(40.0, 46.0, 52.0)
                2 -> listOf(40.0, 50.0, 40.0)
                3 -> listOf(50.0, 40.0, 50.0)
                else -> listOf(52.0, 46.0, 40.0)
            }.let { if (inner) it.map { x -> 92.0 - x } else it }
            for (lead in listOf(null, 3.0, 1.0)) for (tail in listOf(null, 3.0)) for (cr in listOf(0.0, 0.5)) {
                val base = DoubleTaperInput(inner, false, r, null, xs[0], 0.0, xs[1], -5.0, xs[2], -10.0, zc, cr, cr, cr, start = lead, end = tail?.let { -10.0 - it })
                val inp = base.copy(x1 = base.x3, z1 = base.z3, x3 = base.x1, z3 = base.z1, start = base.end, end = base.start, flip = true)
                see(inp.toString(), inner, false, TaperCalc.calcDouble(inp), r, max(zc, r))
            }
        }
        println("뒤집은 테이퍼 진입 급송 틈: $count")
        alarms.forEach(::println)
        fails.take(60).forEach(::println)
        for ((k, v) in worst) if (!k.endsWith("c 이상")) println("  $k 가장 작은 비 ${v.first}: ${v.second}")
        println("  가장 작은 틈 $least · 잰 글 $runs · c 이상 $full")
        assertTrue(runs > 2500 && full > runs * 0.9, "잰 글 $runs · c 이상 $full")
        assertEquals(emptyList(), fails, fails.take(8).joinToString("\n\n"))
    }

    /**
     * 검증 F2(2026-11-13): 뒤집은 내경 작은 구멍(바닥 Ø6 → 입구 Ø10 · 깊이 8 을 바닥 단면부터 — 앱 경고 AGAINST_TOOL). c 가 구멍 반지름보다 크면 P 후보
     * (전 진입점까지 c · c + 2r + g)가 다 맞은편 벽 위 · 속에 놓여 전 규칙으로 떨어졌고, 급송 `Z-8.` 의 노즈가 맞은편 Ø6 벽 · 모서리 R 을 0.055 팠거나(①)
     * 틈 0 으로 닿았는데(②) ENTRY_NOT_FOUND 도 없었다. 구멍 안에 c/4 자리가 있으니 거기서 찾는다
     */
    @Test
    fun flippedSmallHoleEntryFindsRoom() {
        val one = TaperInput(inner = true, type = 2, noseR = 0.2, x1 = 6.0, z1 = -8.0, x2 = 10.0, z2 = 0.0, zClear = 5.0, cornerR1 = 0.5, cornerR2 = 0.5, start = 4.0, flip = true)
        val fails = mutableListOf<String>()
        for (i in listOf(one, one.copy(noseR = 0.4, start = null))) {
            val res = TaperCalc.calc(i)
            val s = ok(res)
            val c = max(i.zClear, i.noseR)
            val gap = rapidGap(s, i.noseR, startWall(s, res.warnings, true, false, i.noseR))
            if (gap == null || gap < c / 4 - 2e-3 || TaperWarning.ENTRY_NOT_FOUND in res.warnings) fails += "$i: 급송 틈 $gap (c $c) ${res.warnings}\n${s.ncG41G42.joinToString(" ")}"
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 검증 F3(2026-11-13): 막힌 수평 시작 앞 끝 직선이 짧은 역테이퍼(외경 형상 5 뒤집기 · 시작 Z−6 · 경사가 Z−5 부터)는 이웃 위 높이로 오면 급송 틈 c 를 지키는데
     * 이웃 위를 보기 전에 g 를 c/4 까지 줄여 `Z-5.55` 급송이 경사 위 0.25(c 1)로 지났다 — 같은 g 에서 이웃 위 높이도 본 뒤 줄인다
     */
    @Test
    fun flippedShortBlockedStartTriesOverTopBeforeLessClear() {
        val i = TaperInput(type = 5, noseR = 0.2, x1 = 40.0, z1 = -5.0, x2 = 50.0, z2 = 0.0, zClear = 1.0, start = -6.0, flip = true)
        val res = TaperCalc.calc(i)
        val s = ok(res)
        val gap = rapidGap(s, i.noseR, startWall(s, res.warnings, false, false, i.noseR))
        assertTrue(gap != null && gap >= 1.0 - 2e-3, "급송 틈 $gap ${res.warnings}\n${s.ncG41G42.joinToString(" ")}")
    }

    // ---------------------------------------------------------------- T2: 내경 축 끝의 후퇴

    /**
     * 글(직접 보정 [machine] = false — 노즈 = 인선 − 팁 벡터, 기계 보정 — 형식 C 노즈)을 돌려 첫 이동 줄 뒤 모든 이동의 노즈가 소재(도면 + 축 거울)를 파는 가장 큰 깊이와 그 줄
     */
    private fun gouge(s: TaperSolution, r: Double, machine: Boolean): Pair<Double, String> {
        val nc = if (machine) s.ncG41G42 else s.ncG40.filter { !it.startsWith("(") }.let { l ->
            val g1 = l.indexOfFirst { it.startsWith("G1") || it.startsWith("G01") }
            l.mapIndexed { k, t -> if (k == g1) t + "F.1" else t }
        }
        val run = sim(nc, r, s.tip)
        run.stop?.let { return Double.POSITIVE_INFINITY to "멈춤 ${it.code} ${it.line - pre.size}줄" }
        val tv = NoseComp.tipVector(s.tip, r)
        val skip = pre.size + (if (machine) 2 else 1)
        val mat = material(s)
        var worst = 0.0
        var at = ""
        for (m in run.steps.filterIsInstance<MoveStep>().filter { it.line > skip }) {
            val nose = if (machine) m.nose else m.tip.let { if (it is Line) Line(it.start - tv, it.end - tv) else (it as Arc).let { a -> Arc(a.start - tv, a.end - tv, a.center - tv, a.cw) } }
            val d = r - dense(nose).minOf { p -> mat.minOf { distTo(it, p) } }
            if (d > worst) {
                worst = d
                at = "${m.kind} '${nc.getOrNull(m.line - pre.size - 1)}'"
            }
        }
        return worst to at
    }

    /** 검증 V7-2 의 재현 입력(내경 형상 2 · 바닥 단면 X0 까지 · 노즈 0.8 · Z 여유 5)과 같은 점의 직선: 끝 후퇴가 맞은편을 파지 않는다(직접 · 기계 보정) */
    @Test
    fun innerAxisEndRetractKeepsFarSide() {
        val base = TaperInput(
            inner = true, type = 2, noseR = 0.8, x1 = 20.324, z1 = -2.0, x2 = 9.23, z2 = -2.504, zClear = 5.0, cornerR2 = 0.4,
            start = 29.082, end = 0.0, arc = true, arcR = 50.771, arcConvex = true,
        )
        val fails = mutableListOf<String>()
        for (i in listOf(base, base.copy(arc = false), base.copy(arc = false, cornerR2 = 0.0), base.copy(arc = false, zClear = 1.0))) {
            val res = TaperCalc.calc(i)
            val s = ok(res)
            for (m in listOf(false, true)) {
                // 오목 모서리 R ≤ 노즈R(앱 경고)은 기계 보정 글이 형상에서 알람으로 멈추는 꼴 — 끝 후퇴와 상관없어 보지 않는다
                if (m && TaperWarning.CONCAVE_R_LE_NOSE in res.warnings) continue
                val (g, at) = gouge(s, i.noseR, m)
                if (g > 1e-3) fails += "$i ${if (m) "기계" else "직접"}: $at 이 $g 팜\n${(if (m) s.ncG41G42 else s.ncG40).joinToString(" ")}"
            }
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 검증 F1(2026-11-13): 좁은 바닥(Ø6 → Ø2 · 깊이 6)을 X0 까지 깎는 직접 보정, 표준 팁 2 — 인선이 X0 이면 노즈 중심이 축 너머 r 이라 마지막 `X0.` 이송이
     * 맞은편 경사를 0.357(노즈 0.8), 원호 볼록 · 노즈 1.2 에서 1.166 팠다. 노즈 중심이 축에 서는 인선 X = 2r 에서 멈추고 공구 쪽 Z 로 뺀다
     */
    @Test
    fun innerAxisEndStandardTipStopsCenterOnAxis() {
        val base = TaperInput(inner = true, type = 2, noseR = 0.8, x1 = 6.0, z1 = 0.0, x2 = 2.0, z2 = -6.0, zClear = 0.0, start = 12.0, end = 0.0)
        val s = ok(TaperCalc.calc(base))
        // 끝 단면 줄: 인선 X1.6(노즈 중심 X0) 뒤 구멍 입구 쪽 Z 로 c(= 노즈R)
        assertEquals(listOf("X1.6", "G0Z-5.2"), s.ncG40.filter { !it.startsWith("(") }.takeLast(2))
        val fails = mutableListOf<String>()
        for (i in listOf(base, base.copy(noseR = 1.2, arc = true, arcR = 20.0, arcConvex = true))) {
            val t = ok(TaperCalc.calc(i))
            val (g, at) = gouge(t, i.noseR, false)
            if (g > 1e-3) fails += "$i: $at 이 $g 팜\n${t.ncG40.joinToString(" ")}"
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 재확인 RC-2(2026-11-14): 뒤집어 내경 바닥 단면을 중심(시작 X0)부터 깎는 직접 보정이 인선을 X0 에 두어 표준 팁은 노즈 중심이 축 너머 r 로 내려가
     * 구멍 반지름 < 2r 이면 맞은편 벽을 팠다(바닥 Ø4 Z−4 → Ø5.2 Z−2 · 오목 R3.634 · 노즈 1.6 · Z 여유 5 의 `G0X0.Z1.` `G1Z-4.` 가 0.630, 직선은 노즈 1.2 에서 0.089).
     * 끝 쪽 X0(위)과 같이 어느 팁이든 노즈 중심을 축에 세워 시작한다 — 표준 팁은 인선 X +2r, 비표준 팁(3 · 4 · 8)은 X −2r.
     * 형상 2 · 4 × 원호(오목 · 볼록) · 직선 × 노즈 0.4~1.6 × 팁 × Z 여유: 직접 보정은 맞은편 · 도면을 파지 않고 노즈 중심이 축에서 시작, 기계 보정도 파지 않는다
     * (기계 보정의 G40 은 반대 끝 — 입구 쪽 — 이라 바닥 반지름과 상관없어 노즈 1.2 · 1.6 도 본다. 재확인2 R2-R2V-2: 전에는 끝 X0 시험의 G40 조건을 옮겨 와 건너뛰었다.
     * 다만 형상 4 · 노즈 1.6 은 그 끝이 Ø5.2 내경 — G40 자리 규칙 반지름 3.882 보다 좁아 빼는 길을 못 짜고 EXIT_NOT_FOUND(Z 여유 5 는 ENTRY_NOT_FOUND 도)로 알린다.
     * 그렇게 알린 글의 기계 보정 파고듦은 알려진 한계라 세기만 한다)
     */
    @Test
    fun innerAxisStartCenterOnAxis() {
        val base = TaperInput(
            inner = true, type = 2, noseR = 1.6, x1 = 4.0, z1 = -4.0, x2 = 5.2, z2 = -2.0, zClear = 5.0, start = 0.0, flip = true,
            arc = true, arcR = 3.634, arcConvex = false,
        )
        // 진입: 인선 X3.2(노즈 중심 X0)에서 Z 로 바닥까지
        assertEquals(listOf("G0X3.2Z1.", "G1Z-4."), ok(TaperCalc.calc(base)).ncG40.filter { !it.startsWith("(") }.take(2))
        val fails = mutableListOf<String>()
        var runs = 0
        var machineBig = 0 // 노즈 1.2 · 1.6 의 기계 보정을 잰 글
        var warned = 0 // 진입 · 빼는 길 못 찾음을 알린 기계 보정 파고듦
        for (type in listOf(2, 4)) for (shape in 0..2) for (r in listOf(0.4, 0.8, 1.2, 1.6)) for (tip in listOf(null, 1, 3, 4, 8)) for (zc in listOf(0.0, 1.0, 5.0)) {
            val i = base.copy(type = type, noseR = r, tip = tip, zClear = zc, arc = shape < 2, arcR = if (shape < 2) 3.634 else null, arcConvex = shape == 1)
            val res = TaperCalc.calc(i)
            val s = res.solution ?: continue
            if (TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings) continue
            runs++
            val nose0 = s.center.segs.first().start
            if (abs(nose0.x) > 1e-9) fails += "$i: 노즈 중심이 X${nose0.xDia} 에서 시작\n${s.ncG40.joinToString(" ")}"
            for (m in listOf(false, true)) {
                val (g, at) = gouge(s, r, m)
                if (m && r >= 1.2) machineBig++
                if (g > 1e-3 && m && (TaperWarning.ENTRY_NOT_FOUND in res.warnings || TaperWarning.EXIT_NOT_FOUND in res.warnings)) warned++
                else if (g > 1e-3) fails += "$i ${if (m) "기계" else "직접"}: $at 이 $g 팜 ${res.warnings}\n${(if (m) s.ncG41G42 else s.ncG40).joinToString(" ")}"
            }
        }
        println("내경 축 시작: 푼 글 $runs · 노즈 1.2 · 1.6 기계 보정 $machineBig · 길 못 찾음을 알린 기계 보정 파고듦 $warned · 실패 ${fails.size}")
        assertTrue(runs > 200 && machineBig > 100, "푼 글 $runs · 노즈 1.2 · 1.6 기계 보정 $machineBig")
        assertEquals(emptyList(), fails, fails.take(6).joinToString("\n\n"))
    }

    /**
     * 재확인2 B2-V2-1(2026-11-14): 내경 바닥 단면의 시작 · 끝 X 가 0 은 아니지만 인선을 거기 두면 노즈 중심이 축 너머인 값(팁 벡터 X 가 바깥인 팁 —
     * 표준 · 1 · 2 · 6 — 의 X < 2r. 화면에 칠 수 있는 X0.001 등)은 X0 고침 밖이라 옛 글처럼 맞은편 벽을 팠다(바닥 Ø4 → Ø5.2 · 오목 R3.634 · 노즈 1.6 의
     * `G0X.001Z1.` `G1Z-4.` 가 0.630 · 직선 노즈 1.6 에서 0.756, 끝 X0.001 의 `G1X.001` 도 같다). X0 과 같이 노즈 중심을 축에 세운다.
     * 노즈 중심이 축 너머로 가지 않는 X · 팁(팁 벡터 X 가 0 · 축 쪽)은 그대로 인선이 넣은 X 에. 형상 2 × 원호(오목 · 볼록) · 직선 × 노즈 0.8~1.6 × X × 팁 1~8:
     * 직접 보정은 맞은편 · 도면을 파지 않는다. 기계 보정도 본다(끝 쪽은 G40 이 바닥 둘레라 위 `innerAxisEndSweep` 처럼 G40 자리 규칙 반지름보다 좁은 바닥을 뺀다)
     */
    @Test
    fun innerBottomNearAxisCenterOnAxis() {
        val start = TaperInput(
            inner = true, type = 2, noseR = 1.6, x1 = 4.0, z1 = -4.0, x2 = 5.2, z2 = -2.0, zClear = 5.0, start = 0.001, flip = true,
            arc = true, arcR = 3.634, arcConvex = false,
        )
        val end = start.copy(x1 = 5.2, z1 = -2.0, x2 = 4.0, z2 = -4.0, start = null, end = 0.001, flip = false)
        // 재현: X0.001 도 X0 과 같은 글(인선 X3.2 — 노즈 중심 X0)
        assertEquals(listOf("G0X3.2Z1.", "G1Z-4."), ok(TaperCalc.calc(start)).ncG40.filter { !it.startsWith("(") }.take(2))
        assertEquals(listOf("G1X3.2", "G0Z1."), ok(TaperCalc.calc(end)).ncG40.filter { !it.startsWith("(") }.takeLast(2))
        val fails = mutableListOf<String>()
        var runs = 0
        var warned = 0 // 진입 · 빼는 길 못 찾음을 알린 기계 보정 파고듦(위 시험과 같은 알려진 한계)
        for (atStart in listOf(true, false)) for (shape in 0..2) for (r in listOf(0.8, 1.2, 1.6)) for (x in listOf(0.0005, 0.001, 0.002, 0.01, 0.5, 2.0))
            for (tip in listOf(null, 1, 2, 3, 4, 5, 6, 7, 8)) {
                val i = (if (atStart) start.copy(start = x) else end.copy(end = x))
                    .copy(noseR = r, tip = tip, arc = shape < 2, arcR = if (shape < 2) 3.634 else null, arcConvex = shape == 1)
                val res = TaperCalc.calc(i)
                val s = res.solution ?: continue
                if (TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings) continue
                runs++
                val tv = NoseComp.tipVector(s.tip, r)
                val nose = if (atStart) s.center.segs.first().start else s.center.segs.last().end
                // 인선을 넣은 X 에 두면 노즈 중심이 축 너머면 축에, 아니면 그대로
                val want = max(0.0, x / 2 - tv.x)
                if (abs(nose.x - want) > 1e-9) fails += "$i: 노즈 중심 X${nose.xDia} (기대 X${want * 2})\n${s.ncG40.joinToString(" ")}"
                for (m in listOf(false, true)) {
                    if (m && !atStart && 2.0 < CompFrame.g40Radius(r)) continue
                    val (g, at) = gouge(s, r, m)
                    if (g > 1e-3 && m && (TaperWarning.ENTRY_NOT_FOUND in res.warnings || TaperWarning.EXIT_NOT_FOUND in res.warnings)) warned++
                    else if (g > 1e-3) fails += "$i ${if (m) "기계" else "직접"}: $at 이 $g 팜 ${res.warnings}\n${(if (m) s.ncG41G42 else s.ncG40).joinToString(" ")}"
                }
            }
        println("내경 바닥 축 가까이: 푼 글 $runs · 길 못 찾음을 알린 기계 보정 파고듦 $warned · 실패 ${fails.size}")
        assertTrue(runs > 300, "푼 글 $runs")
        assertEquals(emptyList(), fails, fails.take(6).joinToString("\n\n"))
    }

    /**
     * 재확인2 B2-V2-2(2026-11-14): 축으로 끝나는 단면(내경 바닥 끝 · 외경 앞면 끝 X0)은 노즈 중심을 축에 세우는데, 팁 벡터 X 가 축 쪽인 팁(내경 3 · 4 · 8,
     * 외경 표준 3 · 8)은 그 옮김 전의 끝점(인선 X0 — 노즈 중심 +r)으로 BAD_LEG 를 봐 마지막 꺾임의 노즈 중심 X 가 r 보다 작으면 멈췄다(뒤집은 시작 X0 은 옮긴 뒤
     * 봐 풀림). 옮긴 끝점으로 본다 — 내경 형상 2 · Ø3.2 Z−4 → Ø2 Z−6(오목 R5.451 · 직선) · 노즈 0.8 · Z 여유 0, 외경 형상 1 뒤집기 · Ø3 Z−1 → Ø1(· 1.4) Z0 ·
     * 노즈 0.8 · Z 여유 1: 풀리고 노즈 중심이 축에서 끝나며 직접 보정이 도면 · 맞은편을 파지 않는다. 외경은 기계 보정도(내경 Ø2 바닥은 G40 자리 규칙 반지름보다 좁아 뺀다)
     */
    @Test
    fun axisEndTipTowardAxisSolves() {
        val inner = TaperInput(inner = true, type = 2, noseR = 0.8, x1 = 3.2, z1 = -4.0, x2 = 2.0, z2 = -6.0, zClear = 0.0, end = 0.0, arc = true, arcR = 5.451, arcConvex = false)
        val outer = TaperInput(type = 1, noseR = 0.8, x1 = 3.0, z1 = -1.0, x2 = 1.0, z2 = 0.0, zClear = 1.0, end = 0.0, flip = true)
        val cases = listOf(inner, inner.copy(arc = false, arcR = null)).flatMap { b -> listOf(3, 4, 8).map { b.copy(tip = it) } } +
            listOf(1.0, 1.4).flatMap { x2 -> listOf(null, 8).map { outer.copy(x2 = x2, tip = it) } }
        val fails = mutableListOf<String>()
        for (i in cases) {
            val res = TaperCalc.calc(i)
            val s = res.solution
            if (s == null) {
                fails += "$i: 멈춤 ${res.warnings}"
                continue
            }
            val nose = s.center.segs.last().end
            if (abs(nose.x) > 1e-9) fails += "$i: 노즈 중심이 X${nose.xDia} 에서 끝남\n${s.ncG40.joinToString(" ")}"
            for (m in listOf(false, true)) {
                if (m && i.inner) continue
                val (g, at) = gouge(s, i.noseR, m)
                if (g > 1e-3) fails += "$i ${if (m) "기계" else "직접"}: $at 이 $g 팜 ${res.warnings}\n${(if (m) s.ncG41G42 else s.ncG40).joinToString(" ")}"
            }
        }
        assertEquals(emptyList(), fails, fails.joinToString("\n\n"))
    }

    /**
     * 격자: 내경 바닥 단면을 축(X0)까지 깎는 끝(형상 2 · 4 × 직선 · 원호 × 노즈 0.2~1.2 × Z 여유 0 · 1 · 5 × 바닥 지름 4 · 10 · 20 × 모서리 R 0 · 0.5 —
     * 뒤집으면 축에서 시작하는 진입):
     * 직접 · 기계 보정 모두 어느 이동도 맞은편(축 거울)과 도면을 파지 않는다. 기계 보정은 바닥 반지름이 G40 자리 규칙 반지름보다 좁은 구멍(G40 옮김이 팁에 따라
     * 맞은편에 닿을 수 있는 알려진 한계 — `CompanyFrameVerifyTest` 와 같은 제외)은 뺀다. 화면 역방향(거울)은 넣지 않는다 — 거울의 부품 앞은 계산기 −Z 라
     * 이 그림(구멍이 +Z 로 열림)이 실제로는 척 쪽으로 열린 막힌 구멍이 된다
     */
    @Test
    fun innerAxisEndSweep() {
        val fails = mutableListOf<String>()
        var runs = 0
        for (type in listOf(2, 4)) for (arc in listOf(false, true)) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0))
            for (bottom in listOf(4.0, 10.0, 20.0)) for (cr in listOf(0.0, 0.5)) for (flip in listOf(false, true)) {
                val x1 = bottom + 14.0
                val arcR = if (arc) TaperCalc.arcMinR(x1, 0.0, bottom, -3.0)?.let { ceil(it * 3 * 1000) / 1000 } else null
                val start = if (type == 2) x1 + 6.0 else 3.0
                val base = TaperInput(
                    true, type, false, r, null, x1, 0.0, bottom, -3.0, null, zc, cr, cr, start = start, end = 0.0,
                    arc = arc, arcR = arcR, arcConvex = true,
                )
                // 뒤집으면 바닥 단면이 축에서 시작하는 요소가 된다(진입 쪽)
                val i = if (!flip) base else base.copy(x1 = base.x2, z1 = base.z2, x2 = base.x1, z2 = base.z1, start = base.end, end = base.start, flip = true)
                val res = TaperCalc.calc(i)
                val s = res.solution ?: continue
                if (TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings) continue
                for (m in listOf(false, true)) {
                    if (m && bottom / 2 < CompFrame.g40Radius(r)) continue
                    runs++
                    val (g, at) = gouge(s, r, m)
                    if (g > 1e-3 && fails.size < 20) fails += "$i ${if (m) "기계" else "직접"}: $at 이 $g 팜 ${res.warnings}\n${(if (m) s.ncG41G42 else s.ncG40).joinToString(" ")}"
                }
            }
        println("내경 축 끝: 잰 글 $runs · 실패 ${fails.size}")
        fails.take(4).forEach(::println)
        assertTrue(runs > 600, "잰 글 $runs")
        assertEquals(emptyList(), fails, fails.take(6).joinToString("\n\n"))
    }

    /**
     * 라운드의 내경(축에서 끝나는 막힌 바닥 · 돔 · 원뿔 바닥)에는 같은 꼴(축 너머 G0 후퇴)이 없음을 확인한다 — 무작위 내경 라운드 가운데 축에서 끝나는 것의
     * 직접 · 기계 보정 글을 돌려 어느 이동도 맞은편(축 거울)을 파지 않는다
     */
    @Test
    fun roundInnerAxisEndHasNoPastAxisRetract() {
        val rnd = Random(20261112)
        val g = GeometricVerifyTest()
        var runs = 0
        val fails = mutableListOf<String>()
        repeat(600) {
            val type = 2 + rnd.nextInt(5)
            val inp = g.randomRound(type, rnd, true).input
            for (m in listOf(false, true)) {
                val res = if (m) RoundCalc.machine(inp) else RoundCalc.calc(inp)
                val s = res.solution ?: continue
                if (s.drawing.segs.last().end.x > 1e-9 || RoundWarning.CONCAVE_R_LE_NOSE in res.warnings) continue
                val r = inp.noseR
                // 기계 보정은 노즈에 비해 좁은 구멍(G40 옮김의 알려진 한계 — `CompanyFrameVerifyTest` 와 같은 제외)을 뺀다
                val minR = s.drawing.segs.flatMap { listOf(it.start.x, it.end.x) }.filter { it > 1e-6 }.minOrNull() ?: 0.0
                if (m && minR < CompFrame.g40Radius(r)) continue
                val nc = if (m) s.nc else s.nc.mapIndexed { k, l -> if (k == s.nc.indexOfFirst { it.startsWith("G1") }) l + "F.1" else l }
                val run = sim(nc, r, s.tip)
                if (run.stop != null) continue
                runs++
                val tv = NoseComp.tipVector(s.tip, r)
                val mat = s.drawing.segs.map(FrameMaterial::mirror)
                for (st in run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size + (if (m) 2 else 1) }) {
                    val nose = if (m) st.nose else st.tip.let { if (it is Line) Line(it.start - tv, it.end - tv) else (it as Arc).let { a -> Arc(a.start - tv, a.end - tv, a.center - tv, a.cw) } }
                    val d = r - dense(nose).minOf { p -> mat.minOf { distTo(it, p) } }
                    if (d > 1e-3 && fails.size < 10) fails += "$inp ${if (m) "기계" else "직접"}: ${st.kind} 줄 ${st.line - pre.size} 이 맞은편을 $d 팜\n${s.nc.joinToString(" ")}"
                }
            }
        }
        println("라운드 내경 축 끝: 잰 글 $runs · 실패 ${fails.size}")
        assertTrue(runs > 50, "잰 글 $runs")
        assertEquals(emptyList(), fails, fails.take(5).joinToString("\n\n"))
    }
}
