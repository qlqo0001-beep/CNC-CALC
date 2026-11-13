package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 관통 구멍(내경 TYPE 5 · 6 의 [RoundInput.through])은 그 끝 좌표 너머가 같은 지름의 구멍이다 — 사용자 답 2026-11-13 "관통이면 넘어가도 됌".
 * 가공 방향을 바꾸면 관통 구멍 쪽 끝에서 시작하는데, 시작 좌표를 벽으로 보지 않고 그 너머(구멍 연장선 쪽)에서 들어와 구멍 벽을 시작 좌표까지 다 깎는다
 * (전에는 노즈가 시작 좌표의 법선에 닿아 멈춰 그 끝의 노즈R 구간에 (√2 − 1)·r 까지 구석이 남았다). 기본 방향의 기계 보정도 같은 원리로 끝 좌표 너머로
 * 지나간 뒤 공구 쪽으로 뺀다(전에는 막힌 끝 — 같은 구석).
 * 글을 시뮬레이터로 돌려 본다: 멈춤 0, 어느 이동도 노즈가 소재(도면 · 축 거울 · 구멍 연장선 · 입구 끝면)를 파지 않음(0.001 반올림까지), 구멍 벽의
 * 덜 깎임 ≤ 0.001, 뒤집은 글의 진입 급송 틈 ≥ c(= max(Z 여유, 노즈R) — 구멍이 좁아 자리가 없으면 c/4 · 구멍 여유까지), 기계 보정은 회사 흐름
 */
class RoundThroughTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99")

    /** 내경 5 관통 — 주 R25 · D2 40 · D1 30 · L1 6(L2 자동 5) · 입구 구멍 깊이 5 */
    private val t5 = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, inner = true, through = true)

    /** 내경 6 관통 — 주 R20 · D2 50 · D1 20 · L1 5 · L2 10 · 입구 구멍 깊이 5 */
    private val t6 = RoundInput(type = 6, mainR = 20.0, d2 = 50.0, d1 = 20.0, l1 = 5.0, l2 = 10.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, inner = true, through = true)

    /** 좁은 관통 구멍(D1 8) — 노즈와 Z 여유가 크면 공구 쪽 자리가 c 보다 좁다 */
    private val narrow = RoundInput(type = 6, mainR = 10.0, d2 = 30.0, d1 = 8.0, l1 = 3.0, l2 = 7.165, cornerR1 = 0.5, cornerR2 = 0.5, legLen = 5.0, inner = true, through = true)

    /**
     * 노즈에 비해 아주 좁은 관통 구멍(D1 ≲ 5r — 노즈 1.2 에서 Ø4.97 · Ø4.332): 끝 좌표 너머 구멍 속에 G40 자리((1 + √2)·r)가 없어 너머로 c 지난 뒤
     * 입구 쪽으로 비켜 빼고(검증 2026-11-14 찾은 것 1 — 전에는 말없이 막힌 끝으로 되돌아가 구석이 남았다), 뒤집은 진입 급송은 노즈 중심이 축까지만
     * (찾은 것 2 — 전에는 축 너머로 가 맞은편 벽 틈이 구멍 자리보다 작았다)
     */
    private val tiny = RoundInput(type = 5, mainR = 5.532, cornerR1 = 0.977, cornerR2 = 0.0, d1 = 4.97, d2 = 8.667, l1 = 1.994, legLen = 1.05, inner = true, through = true)
    private val tiny2 = RoundInput(type = 5, mainR = 10.44, cornerR1 = 0.0, cornerR2 = 1.222, d1 = 4.332, d2 = 8.356, l1 = 1.898, legLen = 3.827, inner = true, through = true)

    private val shapes = listOf(t5, t5.copy(cornerR1 = 1.5, cornerR2 = 1.5), t6, t6.copy(cornerR1 = 1.5, cornerR2 = 1.5), narrow, tiny, tiny2)

    private fun mirror(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    private fun shifted(s: Seg, v: Pt): Seg = when (s) {
        is Line -> Line(s.start + v, s.end + v)
        is Arc -> Arc(s.start + v, s.end + v, s.center + v, s.cw)
    }

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

    private class Run(val stop: String?, val gouge: Double, val under: Double, val rapidGap: Double?, val text: String)

    /**
     * 글 하나를 돌린다. [machine] = 기계 보정 글(형식 C), 아니면 직접 보정 글(노즈 = 인선 − 팁 벡터, 첫 G1 에 F 를 붙여). 소재는 도면 + 관통 구멍 연장선
     * (구멍 끝 좌표에서 Z− 로 같은 지름) + 입구 끝면 + 그 축 거울. 돌려주는 것: 가장 깊은 파고듦, 구멍 벽(D1 직선)의 가장 큰 덜 깎임, 첫 이송 앞 급송의 가장 작은 틈
     */
    private fun run(s: RoundSolution, r: Double, machine: Boolean): Run {
        val g1 = s.nc.indexOfFirst { it.startsWith("G1") }
        val nc = if (machine) s.nc else s.nc.mapIndexed { k, l -> if (k == g1) l + "F.1" else l }
        val text = nc.joinToString("\n")
        val sim = GSim.run(SimInput((pre + nc + "M30").joinToString("\n"), MachineSetup(compStartup = CompStartup.C), mapOf(1 to ToolOffset(r, s.tip))))
        sim.stop?.let { return Run("${it.code} ${it.line - pre.size}줄", 0.0, 0.0, null, text) }
        val tv = NoseComp.tipVector(s.tip, r)
        // 보정 혼자 줄 · 공구 교환 자리에서 오는 첫 G0 은 부품에서 멀어 뺀다
        val moves = sim.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size + (if (machine) 2 else 1) }
        val noses = moves.map { if (machine) it.nose else shifted(it.tip, tv * -1.0) }
        val segs = s.drawing.segs
        // 관통 구멍 벽 = 뒤집었으면 첫 요소, 아니면 마지막 요소(Z− 쪽 끝이 구멍 끝 좌표)
        val hole = (if (s.flip) segs.first() else segs.last()) as Line
        val end = if (hole.start.z < hole.end.z) hole.start else hole.end
        val mouth = if (s.flip) segs.last().end else segs.first().start
        val stock = (segs + Line(end, end + Pt(-300.0, 0.0)) + Line(mouth, mouth + Pt(0.0, 300.0))).let { it + it.map(::mirror) }
        fun clear(p: Pt) = stock.minOf { distTo(it, p) } - r
        val gouge = noses.flatMap(::dense).maxOf { -clear(it) }
        val feed = noses.filterIndexed { k, _ -> moves[k].kind == MoveKind.FEED }
        val under = dense(hole).maxOf { p -> feed.minOf { distTo(it, p) } - r }
        val rapids = noses.zip(moves).takeWhile { it.second.kind == MoveKind.RAPID }.map { it.first }
        val gap = rapids.flatMap(::dense).minOfOrNull(::clear)
        return Run(null, gouge, under, gap, text)
    }

    /**
     * 관통 TYPE 5 · 6 × 가공 방향(바꿈 · 기본) × 기계 · 직접 × 노즈R 0.2 · 0.4 · 0.8 · 1.2 × Z 여유 0 · 1 · 5(팁 표준 2):
     * 알람 0 · 파고듦 0 · 구멍 벽 덜 깎임 ≤ 0.001, 뒤집은 글은 진입 급송 틈 ≥ c(공구 쪽 자리가 c 보다 좁으면 c/4 와 그 자리 가운데 작은 것)
     */
    @Test
    fun throughHoleIsCutToItsEnd() {
        val fails = mutableListOf<String>()
        var runs = 0
        var worstUnder = 0.0
        for (shape in shapes) for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (zc in listOf(0.0, 1.0, 5.0)) for (flip in listOf(true, false)) for (machine in listOf(true, false)) {
            val i = shape.copy(noseR = r, zClear = zc, flip = flip)
            val res = if (machine) RoundCalc.machine(i) else RoundCalc.calc(i)
            val s = res.solution
            if (s == null) {
                fails += "풀이 없음 ${res.warnings} $i"
                continue
            }
            // 오목 R ≤ 노즈R 은 도면 알람 꼴(경고) — 방향 · 관통과 상관없이 제어기가 멈춘다
            if (RoundWarning.CONCAVE_R_LE_NOSE in res.warnings) continue
            val bad = res.warnings.filter {
                it == RoundWarning.ENTRY_NOT_FOUND || it == RoundWarning.EXIT_NOT_FOUND || it == RoundWarning.THROUGH_END_BLOCKED || it.name.startsWith("SHORT_END")
            }
            if (bad.isNotEmpty()) fails += "경고 $bad $i"
            val m = run(s, r, machine)
            runs++
            worstUnder = max(worstUnder, m.under)
            val tag = (if (machine) "기계 " else "직접 ") + (if (flip) "뒤집기 " else "기본 ") + "$i "
            if (m.stop != null) {
                fails += "${tag}알람 ${m.stop}\n${m.text}"
                continue
            }
            if (m.gouge > 1e-3) fails += "${tag}파고듦 ${m.gouge}\n${m.text}"
            if (m.under > 1e-3) fails += "${tag}구멍 벽 덜 깎임 ${m.under}\n${m.text}"
            if (flip) {
                val c = max(zc, r)
                val room = i.d1!! / 2 - r
                val want = if (room >= c) c else min(c / 4, room)
                val gap = m.rapidGap
                if (gap == null || gap < want - 2e-3) fails += "${tag}급송 틈 $gap < $want (c $c)\n${m.text}"
            }
        }
        println("관통 구멍: 글 $runs · 가장 큰 구멍 벽 덜 깎임 $worstUnder · 실패 ${fails.size} " + fails.groupingBy { it.substringBefore(" RoundInput").substringBefore("(") + " " + Regex("(알람|파고듦|덜 깎임|급송 틈)").find(it)?.value }.eachCount())
        assertTrue(runs >= 200, "글 $runs")
        assertEquals(0, fails.size, fails.take(4).joinToString("\n\n"))
    }

    /**
     * 노즈가 구멍을 거의 채우는 좁은 관통 구멍(Ø4.321 · 노즈 1.73)은 끝 좌표 너머로 빼면 G40 자리 규칙을 못 맞춘다(너머도 같은 지름의 구멍) — 그때는 전 규칙
     * (막힌 끝: 공구 쪽으로 빼고 입구 쪽 +Z 로 비켜 G40)으로 짜고 EXIT_NOT_FOUND 로 알린다. 너머로 뺀 글은 G40 옮김이 맞은편 벽을 1.30 팠고 막힌 끝은 0.26
     * (`Verify1113RegressionTest.roundSameAsOld` 의 무작위 사례 — 옛 글과 같아진다). 끝 좌표 너머로 c 지난 뒤 입구 쪽으로 비키는 안도 못 찾는 꼴이라
     * 구석이 남으니 THROUGH_END_BLOCKED 도 단다(화면이 '너머로 지나가 끝까지 깎음' 이라고 하지 않게 — 검증 2026-11-14)
     */
    @Test
    fun narrowThroughEndFallsBackToBlockedExit() {
        val i = RoundInput(type = 5, mainR = 4.165, cornerR1 = 0.0, cornerR2 = 0.0, d1 = 4.321, d2 = 6.038, l1 = 4.357, noseR = 1.73, zClear = 2.668, legLen = 3.297, inner = true, through = true)
        val res = RoundCalc.machine(i)
        val nc = res.solution!!.nc
        assertTrue(RoundWarning.EXIT_NOT_FOUND in res.warnings && RoundWarning.THROUGH_END_BLOCKED in res.warnings, "${res.warnings}")
        assertEquals(listOf("G1Z-8.346", "X-2.699F.3", "Z-4.149", "X.861", "G40", "G0Z200."), nc.takeLast(6), nc.joinToString("\n"))
    }

    /** 기계 보정 글은 회사 흐름이다: 보정 혼자 줄 → G0X…Z20. → 한 축 G0 → 첫 깎는 줄 G1 … F.1 → … → 빼는 줄 F.3 → G40 혼자 줄 → G0Z200. */
    @Test
    fun throughMachineFollowsCompanyFlow() {
        for (shape in shapes) for (r in listOf(0.2, 0.8)) for (zc in listOf(0.0, 1.0)) for (flip in listOf(true, false)) {
            val i = shape.copy(noseR = r, zClear = zc, flip = flip)
            val nc = RoundCalc.machine(i).solution!!.nc
            val text = "$i\n" + nc.joinToString("\n")
            assertEquals(if (flip) "G42" else "G41", nc[0], text)
            assertTrue(Regex("G0X-?[0-9]*\\.[0-9]*Z20\\.").matches(nc[1]), text)
            val cut = nc.indexOfFirst { it.endsWith("F.1") }
            assertTrue(cut >= 3 && nc[cut].startsWith("G1"), text)
            for (k in 2 until cut) assertTrue(!nc[k].contains("G") && !(nc[k].contains("X") && nc[k].contains("Z")), "진입 G0 은 한 축씩 " + text)
            assertEquals(1, nc.count { it.endsWith("F.1") }, text)
            assertEquals(1, nc.count { it.endsWith("F.3") }, text)
            assertEquals(listOf("G40", "G0Z200."), nc.takeLast(2), text)
        }
    }
}
