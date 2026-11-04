package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Input
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-04) — 회사 꼴 G41/G42 글 · 나사 G92 글을 시뮬레이터 `GSim` 으로 깨뜨려 본다. 앱의 틀 코드(`CompFrame`)와
 * 구현자의 성공 기준 시험(`CompanyFrameVerifyTest`)과는 **다른 생성기**(씨앗 · 범위 · 모양)와 따로 짠 기하(점 · 선분 · 원호 거리, 닫은 도면 안팎)로 본다.
 * ① 테이퍼 · 겹테이퍼 · 라운드 TYPE 1~6 · 3점호(역방향 · 앞으로 부푼 것 포함) · 보링바 잘린 기계 보정 글 × 형식 A · B / C × 팁 0~9:
 *    멈춤 · 깎는 줄 노즈의 형식 · 팁 차이 · 모든 이동(G40 · G0 · 형식 C 옮김 포함)의 도면 파먹음(내경 · 외경 모두 축 거울 포함) ·
 *    노즈 중심이 닫은 도면(그린 구간 밑 소재 — 그린 선에 안 닿고 소재 속을 지나는 물러남도) 속으로 가지 않음 ·
 *    막힌 끝 노즈가 벽에 닿고 넘지 않음 · 빈 끝 접점이 끝 좌표 너머 · 축 끝 노즈 중심이 축 · 막힌 시작 너머로 안 감 ·
 *    급송 속도 비를 바꾼 꺾인 G00(LRP=0)도 공기. 보정 중 진입 G0 이 두 축인 글은 출력만(SOFT_)
 * ② 직접 보정(G40) 글과 G41/G42 글의 깎는 노즈(깎는 요소에 닿는 곳)가 서로의 경로 위(0.001). 끝 · 시작 자리 차이는 두 프로그램의 끝 규칙
 *    (테이퍼 — 문서 식 팁 벡터·u, 라운드 직접 보정 — 끝 법선) 차이라 출력만, 가운데는 반올림 0.003 을 넘으면 실패
 * ③ 나사 G92 무작위(바깥 · 안 · 정 · 역 · 피치 여럿): 멈춤 0 · 회차 X · 마지막 X = G76 X · 시작점이 산봉우리 밖 · M23/M24 끝 빼기가 설정대로 ·
 *    M23/M24 를 뺀 옛 글은 설정대로 늘 끝 빼기
 * ④ 표기: 따로 짠 옛 표기 되돌리기와 경로가 같음
 * 알려진 한계(도면 알람 · 좁은 구멍 · 짧은 막힌 끝 · 역방향/부푼 3점호)는 빼지 않고 꼬리표로 따로 센다. 단언은 꼬리표 '기본' 의 실패(SOFT_ 빼고) 0.
 * 더해(2026-11-04 짧은 막힌 끝 · 시작 고침): 어느 꼬리표든 노즈가 막힌 끝 · 시작 너머로 간 글(BLOCKED_PAST · START_PAST)은 앱이 SHORT_END_PAST 경고를 낸 것뿐이어야 한다.
 * 더해(2026-11-05): 도면 알람 · 좁은 구멍 밖의 꼬리표(짧은 막힌 끝 · 시작, 역방향 · 부푼 3점호)도 빼는 줄 · G40 · 물러남의 실패(SOFT_ 빼고)는 앱이 EXIT_NOT_FOUND
 * 경고를 낸 것뿐이어야 한다(전에는 '기본' 밖 꼬리표의 빼는 길 파먹음을 실패로 치지 않아, 짧은 막힌 끝의 첫 빼는 줄이 축을 넘어 맞은편을 파도 통과했을 것이다)
 * 형상 줄의 바깥 꺾임 90° 넘음 멈춤(COMP_ACUTE_OUTER)은 도면 모양이라 시뮬레이터 한계로 센다(FANUC 은 토막을 끼워 돈다)
 */
class VerifyCompanyFlowTest {
    private val pre = listOf("T0101", "G97S1000M03", "G99F.1")

    /** NC 0.001 반올림까지 받는 허용(mm) */
    private val tol = 1e-3

    private enum class End { BLOCKED, FREE, AXIS, OTHER }

    private class Case(
        val group: String, val tag: String, val frame: List<String>, val direct: List<String>?, val directTip: Int, val r: Double,
        val drawing: List<Seg>, val inner: Boolean, val front: Double, val end: End, val endP: Pt, val endU: Pt,
        val startWall: Pair<Pt, Pt>?, val label: String, val cutFrom: Int = 0, val cutTo: Int = drawing.size,
        val stdTip: Boolean = true,
    )

    private class Tally(val name: String) {
        var cases = 0
        var runs = 0
        val labels = linkedMapOf<String, Int>()
        val kinds = linkedMapOf<String, Int>()
        val first = linkedMapOf<String, String>()
        var mainFails = 0

        /** 막힌 끝 · 시작 너머로 간 글 가운데 SHORT_END_PAST 경고가 없는 것(꼬리표 상관없이) */
        var silentPast = 0

        /** 빼는 줄 · G40 · 물러남이 실패한 글 가운데 EXIT_NOT_FOUND 경고가 없는 것(도면 알람 · 좁은 구멍 빼고) */
        var silentExit = 0
        var simLimit = 0
        var devMax = 0.0
        var devCases = 0
        val devBins = IntArray(4)

        /** 직접 보정 대비 끝 노즈 차 (기계 끝 노즈 − 직접 끝 노즈)·끝 방향 — 갈래(끝 종류 · 표준 팁)마다 값 몇 개 */
        val endDiff = linkedMapOf<String, MutableList<Double>>()

        fun fail(label: String, kind: String, msg: String) {
            val k = "$label|$kind"
            kinds[k] = (kinds[k] ?: 0) + 1
            if (k !in first) first[k] = msg
            if (label == MAIN && "SOFT_" !in kind) mainFails++
        }

        fun report(): String = buildString {
            appendLine("== $name: 사례 $cases · 실행 $runs · 꼬리표 $labels · 형상 줄 바깥 꺾임 멈춤(시뮬레이터 한계) $simLimit")
            appendLine("   직접 보정 대비 깎는 노즈 차 최대 $devMax · (≤.001 ≤.002 ≤.01 >.01) ${devBins.toList()} / $devCases 사례")
            for ((k, v) in endDiff) appendLine("   끝 노즈 차 $k: ${v.size}개 · 최소 ${v.minOrNull()} · 최대 ${v.maxOrNull()} · 0.001 넘음 ${v.count { abs(it) > 1e-3 }}")
            for ((k, n) in kinds) appendLine("   실패 $k × $n")
            for ((k, m) in first) appendLine("-- 첫 예 $k\n$m")
        }
    }

    // ================================================================ 따로 짠 기하

    private fun ang(v: Pt) = atan2(v.x, v.z)

    private fun sweep(a: Arc): Double = a.sweepDeg * PI / 180

    /** 원호 중심에서 본 p 의 방향이 원호 안인가 */
    private fun within(a: Arc, p: Pt): Boolean {
        val a0 = ang(a.start - a.center)
        val ap = ang(p - a.center)
        var d = if (a.cw) a0 - ap else ap - a0
        d = ((d % (2 * PI)) + 2 * PI) % (2 * PI)
        return d <= sweep(a) + 1e-12
    }

    private fun dPt(s: Seg, p: Pt): Double = when (s) {
        is Line -> {
            val v = s.end - s.start
            val l2 = v dot v
            if (l2 < 1e-24) (p - s.start).length
            else (p - (s.start + v * (((p - s.start) dot v) / l2).coerceIn(0.0, 1.0))).length
        }
        is Arc -> {
            val c = (p - s.center).length
            if (c > 1e-12 && within(s, p)) abs(c - s.radius) else min((p - s.start).length, (p - s.end).length)
        }
    }

    private fun crosses(a: Line, b: Line): Boolean {
        val d1 = a.end - a.start
        val d2 = b.end - b.start
        val den = d1 cross d2
        if (abs(den) < 1e-15) return false
        val w = b.start - a.start
        val t = (w cross d2) / den
        val u = (w cross d1) / den
        return t in 0.0..1.0 && u in 0.0..1.0
    }

    /** 선분 ~ 선분 · 원호 정확한 거리(끝점 · 수선 · 만남) */
    private fun dLine(l: Line, s: Seg): Double = when (s) {
        is Line -> if (crosses(l, s)) 0.0 else minOf(dPt(s, l.start), dPt(s, l.end), dPt(l, s.start), dPt(l, s.end))
        is Arc -> {
            var best = minOf(dPt(s, l.start), dPt(s, l.end), dPt(l, s.start), dPt(l, s.end))
            val v = l.end - l.start
            val len = v.length
            if (len > 1e-12) {
                val u = v * (1 / len)
                val s0 = (s.center - l.start) dot u
                val foot = l.start + u * s0
                val h2 = s.radius * s.radius - (s.center - foot).length.let { it * it }
                if (h2 >= 0) {
                    val h = sqrt(h2)
                    for (k in listOf(s0 - h, s0 + h)) if (k in 0.0..len && within(s, l.start + u * k)) best = 0.0
                }
                if (s0 in 0.0..len) {
                    val fc = foot - s.center
                    val fl = fc.length
                    if (fl > 1e-12 && within(s, s.center + fc * (s.radius / fl))) best = min(best, abs(fl - s.radius))
                }
            }
            best
        }
    }

    private fun samples(s: Seg, step: Double): List<Pt> = when (s) {
        is Line -> {
            val n = max(1, ((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val a0 = ang(s.start - s.center)
            val sw = sweep(s) * (if (s.cw) -1 else 1)
            val n = max(2, (abs(sw) * s.radius / step).toInt())
            (0..n).map { s.center + Pt(cos(a0 + sw * it / n), sin(a0 + sw * it / n)) * s.radius }
        }
    }

    private fun gap(nose: Seg, mat: List<Seg>): Double = when (nose) {
        is Line -> mat.minOf { dLine(nose, it) }
        // 선분 ~ 원호 거리는 대칭이라 정확한 식을 쓰고, 원호 ~ 원호만 0.02 mm 표본
        is Arc -> mat.minOf { m -> if (m is Line) dLine(m, nose) else samples(nose, 0.02).minOf { p -> dPt(m, p) } }
    }

    private fun mirrorX(s: Seg): Seg = when (s) {
        is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
        is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
    }

    private fun dirStart(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).let { it * (1 / it.length) }
        is Arc -> (s.start - s.center).let { Pt(-it.x, it.z) * ((if (s.cw) -1.0 else 1.0) / it.length) }
    }

    private fun dirEnd(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).let { it * (1 / it.length) }
        is Arc -> (s.end - s.center).let { Pt(-it.x, it.z) * ((if (s.cw) -1.0 else 1.0) / it.length) }
    }

    private fun lengthOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> s.radius * sweep(s)
    }

    /**
     * 닫은 도면(그린 구간의 소재): 외경은 두 끝에서 축으로, 내경은 두 끝에서 바깥(먼 X)으로 닫는다. [stubs] = 두 끝을 닫는 선 — 그림 너머로
     * 이어지는 소재의 경계라 노즈가 r 보다 가까이 오면 그린 데 밖 소재를 판다. [xs] = −1 이면 축 거울 쪽
     */
    private class Region(val poly: List<Pt>, val stubs: List<Line>, val valid: Boolean)

    /**
     * 끝을 닫는 점: 끝 접선이 그린 구간 쪽(Z 로 되돌아옴 — 언더컷)으로 꺾여 닫는 쪽(외경 축 · 내경 바깥)으로 11° 넘게 가면 그 접선을 따라
     * (그 사이는 공기), 아니면 곧장 X 로(그린 구간 밑만 — 끝 너머는 모른다). [back] = 끝에서 그린 구간 쪽 Z 부호
     */
    private fun closeAt(p: Pt, u: Pt, target: Double, back: Double): Pt {
        val toward = sign(target - p.x)
        return if (u.x * toward > 0.2 && u.z * back > 1e-9) p + u * ((target - p.x) / u.x) else Pt(p.z, target)
    }

    private fun region(d: List<Seg>, inner: Boolean, xs: Double): Region {
        val p = mutableListOf<Pt>()
        for (s in d) p += samples(s, 0.2).dropLast(1)
        p += d.last().end
        val first = d.first().start
        val last = d.last().end
        val big = p.maxOf { abs(it.x) } + 3.0
        val target = if (inner) xs * big else 0.0
        val a1 = closeAt(last, dirEnd(d.last()), target, sign(first.z - last.z))
        val a2 = closeAt(first, dirStart(d.first()) * -1.0, target, sign(last.z - first.z))
        val stubs = listOf(Line(last, a1), Line(a2, first)).filter { (it.end - it.start).length > 1e-9 }
        // 닫는 선이 도면을 가로지르면(Z 로 되돌아오는 윤곽 · 접선 닫기끼리 엇갈림) 안팎을 가릴 수 없어 그 사례는 닫은 도면 검사를 하지 않는다
        val shrunk = stubs.map { l ->
            val u = (l.end - l.start) * (1 / (l.end - l.start).length)
            Line(l.start + u * 1e-4, l.end - u * 1e-4)
        }
        val valid = shrunk.none { l -> d.any { dLine(l, it) < 1e-7 } } && (shrunk.size < 2 || !crosses(shrunk[0], shrunk[1]))
        p += a1
        p += a2
        return Region(p, stubs, valid)
    }

    private fun footInside(s: Seg, p: Pt): Boolean = when (s) {
        is Line -> {
            val v = s.end - s.start
            val t = ((p - s.start) dot v) / (v dot v)
            t >= -1e-6 && t <= 1 + 1e-6
        }
        is Arc -> within(s, p)
    }

    private fun inside(poly: List<Pt>, p: Pt): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.x > p.x) != (b.x > p.x)) {
                val z = a.z + (p.x - a.x) * (b.z - a.z) / (b.x - a.x)
                if (p.z < z) c = !c
            }
            j = i
        }
        return c
    }

    private fun depth(poly: List<Pt>, p: Pt): Double = poly.indices.minOf { dPt(Line(poly[it], poly[(it + 1) % poly.size]), p) }

    private fun same(a: Seg, b: Seg): Boolean =
        a::class == b::class && (a.start - b.start).length <= 1e-6 && (a.end - b.end).length <= 1e-6 &&
            (a !is Arc || (a.center - (b as Arc).center).length <= 1e-6)

    private fun f3(v: Double) = Fmt.nc(v)

    private fun xz(p: Pt) = "X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)}"

    private fun r3(v: Double) = Fmt.nc(v).toDouble()

    private fun sim(text: List<String>, r: Double, tip: Int, form: CompStartup, front: Double, rx: Double = 20_000.0, rz: Double = 24_000.0): SimRun =
        GSim.run(
            SimInput(
                (pre + text + "M30").joinToString("\n"),
                MachineSetup(rapidX = rx, rapidZ = rz, home = Pt(front * 100.0, 100.0), compStartup = form),
                mapOf(1 to ToolOffset(r, tip)),
            ),
        )

    // ================================================================ 사례 하나 보기

    private fun check(c: Case, t: Tally) {
        t.cases++
        t.labels[c.label] = (t.labels[c.label] ?: 0) + 1
        val seen = mutableSetOf<String>()
        fun bad(kind: String, msg: String) {
            if (!seen.add(kind)) return
            t.fail(c.label, "${c.group}:$kind", "[${c.group} · ${c.label}] $kind: ${c.tag}\n  $msg\n  G41/G42: ${c.frame.joinToString(" / ")}")
            if ((kind == "BLOCKED_PAST" || kind == "START_PAST") && "SHORT_END_PAST" !in c.tag) t.silentPast++
            val role = kind.substringAfter('@', "").substringBefore('_')
            if ("SOFT_" !in kind && role in listOf("빼는 줄", "G40", "물러남") && c.label != "도면 알람" && c.label != "좁은 구멍" && "EXIT_NOT_FOUND" !in c.tag) t.silentExit++
        }
        val f = c.frame
        val iCut = f.indexOfFirst { it.endsWith("F.1") }
        val iExit = f.indexOfFirst { it.contains("F.3") }
        val iG40 = f.indexOf("G40")
        if (f.firstOrNull() !in listOf("G41", "G42") || iCut < 2 || iExit <= iCut || iG40 < iExit || iG40 != f.size - 2 || !f.last().startsWith("G0Z")) {
            bad("STRUCT", "회사 틀이 아님")
            return
        }
        if (!f[1].startsWith("G0X") || !f[1].contains("Z")) bad("STRUCT_G0", "둘째 줄이 G0X…Z… 가 아님: ${f[1]}")
        // 보정 중 진입 G0 은 한 축씩 — 두 축이면 LRP=0 에서 꺾여 간다
        for (k in 2 until iCut) {
            val axes = Regex("[XZUW]").findAll(f[k]).count()
            if (axes != 1 || f[k].contains("G")) bad("SOFT_LRP_TWO_AXIS", "보정 중 진입 G0 줄이 한 축이 아님: ${f[k]}")
        }
        val cutLines = (pre.size + iCut + 1)..(pre.size + iExit)
        val exitLine = pre.size + iExit + 1
        val firstG0 = pre.size + 2
        val mat = c.drawing + c.drawing.map(::mirrorX)
        val regions = listOf(region(c.drawing, c.inner, 1.0), region(c.drawing.map(::mirrorX), c.inner, -1.0)).filter { it.valid }
        val stubs = regions.flatMap { it.stubs }
        fun role(line: Int): String {
            val k = line - pre.size - 1
            return when {
                k < 0 -> "앞"
                k == 0 -> "보정 혼자 줄"
                k < iCut -> "진입 G0"
                k == iCut -> "첫 깎는 줄"
                k < iExit -> "형상"
                k < iG40 -> "빼는 줄"
                k == iG40 -> "G40"
                else -> "물러남"
            }
        }
        var ref: List<Seg>? = null
        for (form in listOf(CompStartup.AB, CompStartup.C)) for (tip in 0..9) {
            t.runs++
            val run = sim(f, c.r, tip, form, c.front)
            val where = "형식 $form · 팁 $tip"
            val stop = run.stop
            if (stop != null) {
                // 형상 줄의 바깥 꺾임 90° 넘음은 도면 모양 — FANUC 은 토막을 끼워 도는데 시뮬레이터가 그리지 않는다(시뮬레이터 한계)
                if (stop.code.toString() == "COMP_ACUTE_OUTER" && role(stop.line) == "형상") {
                    t.simLimit++
                    return
                }
                bad("STOP_${stop.code}@${role(stop.line)}", "$where: ${stop.line - pre.size}줄 '${f.getOrNull(stop.line - pre.size - 1)}'")
                continue
            }
            val moves = run.steps.filterIsInstance<MoveStep>()
            val cut = moves.filter { it.line in cutLines && it.kind == MoveKind.FEED }.map { it.nose }
            val r0 = ref
            if (r0 == null) {
                ref = cut
            } else if (r0.size != cut.size || r0.indices.any { !same(r0[it], cut[it]) }) {
                val k = r0.indices.firstOrNull { it >= cut.size || !same(r0[it], cut[it]) } ?: cut.size
                bad("FORM_TIP_DIFF", "$where: 깎는 노즈가 첫 실행과 다름(걸음 $k: ${r0.getOrNull(k)} / ${cut.getOrNull(k)})")
            }
            for (m in moves) {
                val lineText = f.getOrNull(m.line - pre.size - 1) ?: "(앞)"
                val g = gap(m.nose, mat)
                if (g < c.r - tol) {
                    val kind = "GOUGE@${role(m.line)}_${m.src}"
                    bad(kind, "$where: ${m.line - pre.size}줄 '$lineText' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 이 도면을 ${f3(c.r - g)} 팜")
                }
                for (rg in regions) {
                    // 노즈 중심 길을 닫은 도면의 상자 안에서 0.25 mm 마다(원호 0.2 mm) — 그린 선에 안 닿고 소재 속을 지나는 이동(그린 데 밑으로 물러남 등)
                    val zs = rg.poly.minOf { it.z } to rg.poly.maxOf { it.z }
                    val xs2 = rg.poly.minOf { it.x } to rg.poly.maxOf { it.x }
                    val pts = samples(m.nose, if (m.nose is Arc) 0.2 else 0.25).filter { it.z in zs.first..zs.second && it.x in xs2.first..xs2.second } +
                        listOf(m.nose.start, m.nose.end)
                    var deep = 0.0
                    var at: Pt? = null
                    for (q in pts) if (inside(rg.poly, q)) {
                        val dq = depth(rg.poly, q)
                        if (dq > deep) {
                            deep = dq
                            at = q
                        }
                    }
                    if (deep > tol) bad("INSIDE@${role(m.line)}", "$where: ${m.line - pre.size}줄 '$lineText' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 의 중심이 닫은 도면 속(가장 깊은 곳 ${at?.let { xz(it) }} 깊이 ${f3(deep)})")
                }
                val gs = stubs.minOfOrNull { gap(m.nose, listOf(it)) } ?: Double.POSITIVE_INFINITY
                if (gs < c.r - tol && g >= c.r - tol) {
                    bad("SOFT_STUB@${role(m.line)}", "$where: ${m.line - pre.size}줄 '$lineText' 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 이 그림 끝 너머 소재 경계에 ${f3(gs)}")
                }
            }
            val after = moves.filter { it.line > firstG0 }.flatMap { samples(it.nose, 0.02) }
            when (c.end) {
                End.BLOCKED -> {
                    val past = after.maxOf { (it - c.endP) dot c.endU }
                    if (past > -c.r + tol) bad("BLOCKED_PAST", "$where: 막힌 끝 ${xz(c.endP)} 너머로 노즈가 ${f3(past + c.r)} 감")
                    val reach = cut.flatMap { samples(it, 0.02) }.maxOfOrNull { (it - c.endP) dot c.endU } ?: Double.NEGATIVE_INFINITY
                    if (reach < -c.r - tol) bad("BLOCKED_SHORT", "$where: 막힌 끝 벽에 ${f3(-c.r - reach)} 못 닿음")
                }
                End.FREE -> {
                    val ex = moves.filter { it.line == exitLine }.flatMap { samples(it.nose, 0.02) }
                    val beyond = ex.maxOfOrNull { (it - c.endP) dot c.endU } ?: Double.NEGATIVE_INFINITY
                    if (beyond < -tol) bad("FREE_SHORT", "$where: 빈 끝 ${xz(c.endP)} 에서 노즈 접점이 끝 좌표에 ${f3(-beyond)} 못 감")
                }
                End.AXIS -> {
                    val low = moves.filter { it.line in cutLines || it.line == exitLine }.flatMap { samples(it.nose, 0.02) }.minOf { abs(it.x) }
                    if (low > tol) bad("AXIS_SHORT", "$where: 축에서 끝나는데 노즈 중심이 축에서 ${f3(low)}")
                }
                End.OTHER -> {}
            }
            c.startWall?.let { (s, back) ->
                val past = after.maxOf { (it - s) dot back }
                if (past > -c.r + tol) bad("START_PAST", "$where: 막힌 시작 ${xz(s)} 너머로 노즈가 ${f3(past + c.r)} 감")
            }
        }
        // 급송 속도 비를 바꿔도(꺾이는 자리가 바뀜) 꺾인 G00 이 공기
        for ((rx, rz) in listOf(30_000.0 to 8_000.0, 6_000.0 to 30_000.0)) for (form in listOf(CompStartup.AB, CompStartup.C)) {
            val run = sim(f, c.r, c.directTip, form, c.front, rx, rz)
            val st = run.stop
            if (st != null) {
                if (!(st.code.toString() == "COMP_ACUTE_OUTER" && role(st.line) == "형상")) bad("STOP_RATIO@${role(st.line)}", "급송 X $rx · Z $rz 형식 $form: $st")
                continue
            }
            for (m in run.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.RAPID }) {
                val g = gap(m.nose, mat)
                if (g < c.r - tol) bad("GOUGE_DOGLEG@${role(m.line)}", "급송 X $rx · Z $rz 형식 $form: ${m.line - pre.size}줄 노즈 ${xz(m.nose.start)} → ${xz(m.nose.end)} 이 ${f3(c.r - g)} 팜")
            }
        }
        // ② 직접 보정 글과 깎는 노즈 비교 — 도면에 닿는 표본이 서로의 노즈 경로 위
        val d = c.direct ?: return
        val dr = sim(d, c.r, c.directTip, CompStartup.C, c.front)
        if (dr.stop != null) {
            bad("DIRECT_STOP", "직접 보정 글이 멈춤 ${dr.stop}\n  직접: ${d.joinToString(" / ")}")
            return
        }
        val fr = sim(f, c.r, c.directTip, CompStartup.C, c.front)
        if (fr.stop != null) return
        val dNose = dr.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.FEED }.map { it.nose }
        val fNose = fr.steps.filterIsInstance<MoveStep>().filter { it.line in cutLines && it.kind == MoveKind.FEED }.map { it.nose }
        if (dNose.isEmpty() || fNose.isEmpty()) return
        // 깎는 요소(비운 앞뒤 요소는 뺌)에 수선 발이 그 요소 안에 떨어지며 r 로 닿는 표본만
        val cutSegs = c.drawing.subList(c.cutFrom, c.cutTo)
        fun contact(n: List<Seg>) = n.flatMap { samples(it, 0.01) }.filter { p -> cutSegs.any { s -> dPt(s, p) <= c.r + 2e-3 && footInside(s, p) } }
        fun worst(ps: List<Pt>, path: List<Seg>): Pair<Double, Pt?> {
            var w = 0.0
            var at: Pt? = null
            for (p in ps) {
                val e = path.minOf { dPt(it, p) }
                if (e > w) {
                    w = e
                    at = p
                }
            }
            return w to at
        }
        val (devF, atF) = worst(contact(fNose), dNose)
        val (devD, atD) = worst(contact(dNose), fNose)
        val dev = max(devF, devD)
        if (c.end != End.OTHER) {
            val k = "${c.group} ${c.end} ${if (c.stdTip) "표준 팁" else "비표준 팁"}"
            val diff = (fNose.last().end - dNose.last().end) dot c.endU
            t.endDiff.getOrPut(k) { mutableListOf() } += diff
        }
        t.devCases++
        t.devMax = max(t.devMax, dev)
        t.devBins[if (dev <= 1e-3) 0 else if (dev <= 2e-3) 1 else if (dev <= 1e-2) 2 else 3]++
        if (dev > tol) {
            val at = if (devF >= devD) atF else atD
            val zone = when {
                at == null -> "?"
                // 깎는 요소의 처음 · 끝 꺾임 둘레(2r) — 끝 규칙과 거의 나란한 첫 꺾임의 교점 차이가 여기서 난다
                (at - c.endP).length <= 2 * c.r + 0.05 || (at - cutSegs.last().end).length <= 2 * c.r + 0.05 -> "끝"
                (at - c.drawing.first().start).length <= 2 * c.r + 0.05 || (at - cutSegs.first().start).length <= 2 * c.r + 0.05 -> "시작"
                else -> "가운데"
            }
            // 끝 · 시작 자리는 두 프로그램의 끝 규칙이 다르다(테이퍼 — 문서 식 팁 벡터·u, 라운드 직접 보정 — 끝 법선) — 출력만.
            // 가운데는 NC 0.001 반올림(두 글 모두 — 짧은 요소 · 거의 나란한 꺾임에서 교점이 더 움직인다) 0.003 까지 출력만
            val soft = zone != "가운데" || dev <= 3e-3
            bad(
                "${if (soft) "SOFT_" else ""}DIRECT_DIFF@$zone·${if (c.stdTip) "표준 팁" else "비표준 팁"}",
                "깎는 노즈: 기계 보정 접촉점 ~ 직접 보정 경로 ${f3(devF)}${atF?.let { " (${xz(it)})" } ?: ""}, 직접 접촉점 ~ 기계 경로 ${f3(devD)}${atD?.let { " (${xz(it)})" } ?: ""}\n" +
                    "  직접(팁 ${c.directTip}): ${d.joinToString(" / ")}",
            )
        }
    }

    // ================================================================ 테이퍼(다른 생성기)

    private fun endKind(last: Line, u: Pt, inner: Boolean, front: Double): End {
        val face = abs(u.x) > abs(u.z)
        return if (face) {
            if (u.x < 0) {
                if (last.end.x <= 1e-6) End.AXIS else if (inner) End.FREE else End.BLOCKED
            } else {
                if (inner) End.BLOCKED else End.FREE
            }
        } else {
            if (u.z * front < 0) End.BLOCKED else End.FREE
        }
    }

    private fun startBlocked(first: Line, u: Pt, inner: Boolean, front: Double): Boolean {
        val face = abs(u.x) > abs(u.z)
        return if (face) {
            if (inner) u.x < 0 else u.x > 0 && first.start.x > 1e-6
        } else {
            u.z * front > 0
        }
    }

    private fun narrow(d: List<Seg>, r: Double): Boolean {
        val minR = d.flatMap { samples(it, 0.1) }.filter { it.x > 1e-6 }.minOfOrNull { it.x } ?: 0.0
        return minR < (1 + sqrt(2.0)) * r + 0.02
    }

    private fun taperCase(tag: String, res: TaperResult, inner: Boolean, mirror: Boolean, r: Double, std: Boolean): Case? {
        val s = res.solution ?: return null
        val d = s.drawing.segs
        val first = d.first()
        val last = d.last()
        val front = if (mirror) -1.0 else 1.0
        val uF = dirStart(first)
        val uB = dirEnd(last)
        var end = End.OTHER
        var short = false
        if (s.toEnd && last is Line) {
            end = endKind(last, uB, inner, front)
            if (end == End.BLOCKED && lengthOf(last) < 2 * r + 0.1) short = true
        }
        var wall: Pair<Pt, Pt>? = null
        if (s.fromStart && first is Line && startBlocked(first, uF, inner, front)) {
            wall = first.start to uF * -1.0
            if (lengthOf(first) < 2 * r + 0.1) short = true
        }
        val gone = d.indices.any { k -> lengthOf(s.center.segs[k]) < 1e-6 && lengthOf(d[k]) > 1e-6 }
        val alarm = TaperWarning.CONCAVE_R_LE_NOSE in res.warnings || TaperWarning.NOSE_GT_SLOPE in res.warnings || gone
        val label = when {
            alarm -> "도면 알람"
            inner && narrow(d, r) -> "좁은 구멍"
            short -> "짧은 막힌 끝·시작"
            else -> MAIN
        }
        return Case(
            "테이퍼", "$tag · 경고 ${res.warnings} · 팁 ${s.tip}", s.ncG41G42, s.ncG40, s.tip, r, d, inner, front, end, last.end, uB, wall, label,
            if (s.fromStart) 0 else 1, if (s.toEnd) d.size else d.size - 1, std,
        )
    }

    private fun taperCases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        fun pick(vararg l: Double): Double = l[rnd.nextInt(l.size)]
        repeat(count) {
            val inner = rnd.nextBoolean()
            val dbl = rnd.nextInt(5) == 0
            val reverse = rnd.nextInt(4) == 0
            val flip = rnd.nextInt(5) < 2
            val mirror = !reverse && rnd.nextInt(4) == 0
            val r = if (rnd.nextBoolean()) pick(0.1, 0.2, 0.4, 0.8, 1.2, 1.6) else r3(0.1 * (1 + rnd.nextInt(16)))
            // Z 여유 c 는 다섯에 둘이 0 ~ r 미만
            val zc = if (rnd.nextInt(5) < 2) r3(u(0.0, r * 0.999)) else pick(0.0, r, 0.5, 1.0, 2.0, 3.0)
            val tip: Int? = if (rnd.nextInt(10) < 3) null else rnd.nextInt(10)
            fun cr() = if (rnd.nextInt(20) < 13) 0.0 else r3(pick(0.05, 0.1, r * 0.5, r, r * 1.5, 1.0, 2.5))
            fun leg() = r3(if (rnd.nextInt(4) == 0) u(0.05, 2.5 * r) else u(1.0, 15.0))
            // 아주 얕은 각 · 거의 90° · 보통
            fun angDz(): Pair<Double, Double> = when (rnd.nextInt(7)) {
                0 -> u(0.3, 3.0) to r3(u(0.5, 15.0))
                1 -> u(86.0, 89.7) to r3(u(0.02, 0.8))
                else -> u(3.0, 86.0) to r3(if (rnd.nextInt(8) == 0) u(0.03, 2.5 * r) else u(0.3, 15.0))
            }
            val x1 = r3(if (inner) u(2.0, 120.0) else u(0.5, 150.0))
            val z1 = r3(u(-8.0, 6.0))
            if (!dbl) {
                val type = 1 + rnd.nextInt(5)
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val (deg, dz) = angDz()
                val x2 = r3(x1 + up * 2 * dz * tan(deg * PI / 180))
                if (x2 < 0.5 || x2 > 600.0 || dz <= 0) return@repeat
                val z2 = r3(z1 - dz)
                val frontFace = type <= 2
                val backFace = type == 2 || type == 4
                val start = if (rnd.nextBoolean()) null else if (frontFace) {
                    if (!inner && rnd.nextInt(6) == 0) 0.0 else max(0.0, r3(x1 - up * 2 * leg()))
                } else r3(z1 + leg())
                val end = if (rnd.nextBoolean()) null else if (backFace) {
                    if (inner && rnd.nextInt(6) == 0) 0.0 else max(0.0, r3(x2 + up * 2 * leg()))
                } else r3(z2 - leg())
                var inp = TaperInput(inner, type, reverse, r, tip, x1, z1, x2, z2, null, zc, cr(), cr(), start = start, end = end, approach = !mirror)
                if (flip) inp = inp.copy(x1 = inp.x2, z1 = inp.z2, x2 = inp.x1, z2 = inp.z1, cornerR1 = inp.cornerR2, cornerR2 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
                taperCase(inp.toString(), TaperCalc.calc(inp), inner, mirror, r, tip == null || tip == NoseComp.standardTip(inner, reverse))?.let { out += it }
            } else {
                val (a1, dz1) = angDz()
                val (a2, dz2) = angDz()
                val s1 = if (rnd.nextBoolean()) 1.0 else -1.0
                val s2 = if (rnd.nextBoolean()) 1.0 else -1.0
                val x2 = r3(x1 + s1 * 2 * dz1 * tan(a1 * PI / 180))
                val x3 = r3(x2 + s2 * 2 * dz2 * tan(a2 * PI / 180))
                if (x2 < 0.5 || x3 < 0.5 || x2 > 600 || x3 > 600) return@repeat
                val z2 = r3(z1 - dz1)
                val z3 = r3(z2 - dz2)
                var inp = DoubleTaperInput(
                    inner, reverse, r, tip, x1, z1, x2, z2, x3, z3, zc, cr(), cr(), cr(),
                    start = if (rnd.nextBoolean()) r3(z1 + leg()) else null, end = if (rnd.nextBoolean()) r3(z3 - leg()) else null,
                    approach = !mirror,
                )
                if (flip) inp = inp.copy(x1 = inp.x3, z1 = inp.z3, x3 = inp.x1, z3 = inp.z1, cornerR1 = inp.cornerR3, cornerR3 = inp.cornerR1, start = inp.end, end = inp.start, flip = true)
                taperCase(inp.toString(), TaperCalc.calcDouble(inp), inner, mirror, r, tip == null || tip == NoseComp.standardTip(inner, reverse))?.let { out += it }
            }
        }
        return out
    }

    // ================================================================ 라운드 · 3점호 · 보링바(다른 생성기)

    private fun roundCase(group: String, tag: String, machine: RoundResult, direct: RoundResult, inner: Boolean, r: Double, extra: String?, std: Boolean = true): Case? {
        val s = machine.solution ?: return null
        val ds = direct.solution ?: return null
        val d = s.drawing.segs
        val last = d.last()
        val uB = dirEnd(last)
        val end = when {
            last.end.x <= 1e-6 -> End.AXIS
            last is Line && abs(uB.z) >= abs(uB.x) && uB.z < 0 -> End.BLOCKED
            last is Line && abs(uB.x) > abs(uB.z) && ((uB.x > 0) == inner) -> End.BLOCKED
            else -> End.FREE
        }
        val short = end == End.BLOCKED && lengthOf(last) < 2 * r + 0.1
        val gone = d.indices.any { k -> k < ds.center.segs.size && lengthOf(ds.center.segs[k]) < 1e-6 && lengthOf(d[k]) > 1e-6 }
        val alarm = RoundWarning.CONCAVE_R_LE_NOSE in direct.warnings || gone
        val label = when {
            alarm -> "도면 알람"
            inner && narrow(d, r) -> "좁은 구멍"
            extra != null -> extra
            short -> "짧은 막힌 끝·시작"
            else -> MAIN
        }
        return Case(group, "$tag · 경고 ${machine.warnings} · 끝 $end · 팁 ${ds.tip}", s.nc, ds.nc, ds.tip, r, d, inner, 1.0, end, last.end, uB, null, label, stdTip = std)
    }

    private fun noseOf(rnd: Random): Double =
        if (rnd.nextBoolean()) listOf(0.1, 0.2, 0.4, 0.8, 1.2, 1.6)[rnd.nextInt(6)] else r3(0.1 * (1 + rnd.nextInt(16)))

    private fun roundInput(rnd: Random, inner: Boolean, r: Double = noseOf(rnd)): RoundInput {
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        val type = if (inner) 2 + rnd.nextInt(5) else 1 + rnd.nextInt(6)
        val zc = if (rnd.nextInt(5) < 2) r3(u(0.0, r * 0.999)) else listOf(0.0, r, 0.5, 1.0, 2.5)[rnd.nextInt(5)]
        val tip: Int? = if (rnd.nextInt(10) < 3) null else rnd.nextInt(10)
        fun cr() = if (rnd.nextInt(10) < 4) 0.0 else r3(listOf(0.05, 0.2, r * 0.6, r, r * 1.8, 1.0, 3.0)[rnd.nextInt(7)])
        val leg = r3(if (rnd.nextInt(5) == 0) u(0.1, 2.5 * r) else u(1.0, 12.0))
        val bigR = r3(u(1.0, 45.0))
        val base = RoundInput(type = type, noseR = r, tip = tip, zClear = zc, legLen = leg, inner = inner)
        return when (type) {
            1 -> base.copy(mainR = bigR, l1 = r3(u(0.05, 1.98) * bigR), cornerR1 = cr())
            2 -> base.copy(mainR = bigR, d1 = r3(u(0.1, 1.98) * bigR), l1 = r3(u(0.3, 15.0)), cornerR1 = cr(), l2Ref = if (rnd.nextBoolean()) L2Ref.A else L2Ref.B)
            3 -> base.copy(mainR = bigR, l1 = r3(if (rnd.nextInt(8) == 0) bigR else u(0.05, 1.0) * bigR), cornerR1 = cr())
            4 -> base.copy(mainR = bigR, l1 = r3(if (rnd.nextInt(8) == 0) bigR else u(1.0, 1.98) * bigR), cornerR1 = cr())
            5 -> {
                val d2 = r3(u(0.5, 1.99) * bigR)
                base.copy(mainR = bigR, d2 = d2, d1 = r3(u(0.1, 0.97) * d2), l1 = r3(u(0.5, 12.0)), cornerR1 = cr(), cornerR2 = cr(), through = inner && rnd.nextInt(3) == 0)
            }
            else -> {
                val a = u(0.1, 0.95) * bigR
                val w = sqrt(bigR * bigR - a * a)
                val l2 = r3(w - u(-0.9, 0.9) * min(w, bigR))
                val dz = l2 - w
                val top = if (dz >= 0) bigR else sqrt(max(0.0, bigR * bigR - dz * dz))
                base.copy(
                    mainR = bigR, d1 = r3(2 * a), d2 = r3(2 * (top + u(0.2, 10.0))), l1 = r3(u(0.5, 12.0)), l2 = l2, cornerR1 = cr(), cornerR2 = cr(),
                    through = inner && rnd.nextInt(3) == 0,
                )
            }
        }
    }

    /** 3점호: 원 위 세 점(정 · 역방향, 앞으로 부푼 것도). 꼬리표: 역방향 · 부풂 */
    private fun arc3Input(rnd: Random, inner: Boolean, r: Double = noseOf(rnd)): Pair<Arc3Input, String?>? {
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        val rr = u(1.0, 40.0)
        val c = Pt(u(-25.0, 5.0), if (inner) u(0.2 * rr + 2, rr + 30) else u(-0.8 * rr, rr + 25))
        val a0 = u(0.0, 2 * PI)
        val sw = u(0.15, 2.8) * (if (rnd.nextBoolean()) 1 else -1)
        val pts = listOf(0.0, 0.5, 1.0).map { c + Pt(cos(a0 + sw * it), sin(a0 + sw * it)) * rr }
        val sampled = (0..40).map { c + Pt(cos(a0 + sw * it / 40), sin(a0 + sw * it / 40)) * rr }
        if (sampled.any { it.x < 0.3 } || abs(pts[2].z - pts[0].z) < 0.2) return null
        val reverse = pts[2].z > pts[0].z
        val bulge = if (reverse) sampled.any { it.z < pts[0].z - 1e-6 } else sampled.any { it.z > pts[0].z + 1e-6 }
        val zc = if (rnd.nextInt(5) < 2) r3(u(0.0, r * 0.999)) else listOf(0.0, r, 0.5, 1.0, 2.0)[rnd.nextInt(5)]
        val tip: Int? = if (rnd.nextInt(10) < 3) null else rnd.nextInt(10)
        val inp = Arc3Input(inner, r, tip, r3(pts[0].xDia), r3(pts[0].z), r3(pts[1].xDia), r3(pts[1].z), r3(pts[2].xDia), r3(pts[2].z), zc)
        val label = when {
            reverse && bulge -> "3점호 역방향+부풂"
            reverse -> "3점호 역방향"
            bulge -> "3점호 부풂"
            else -> null
        }
        return inp to label
    }

    private fun bar(rnd: Random, r: Double): BoringBar {
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        val shape = InsertShape.entries[rnd.nextInt(InsertShape.entries.size)]
        val dia = r3(u(5.0, 40.0))
        val kr = r3(u(91.0, min(115.0, 178.0 - shape.epsDeg)))
        val f = r3(HolderCheck.fMin(dia, kr, r) + u(0.1, 8.0))
        var b = BoringBar(dia, f, shape, r3(HolderCheck.edgeMin(shape, r) + u(0.2, 12.0)), kr)
        if (rnd.nextInt(3) == 0) b = b.copy(overhang = r3(HolderCheck.overhangMin(b, r) + u(1.0, 80.0)))
        if (rnd.nextInt(3) == 0) b = b.copy(dMin = r3(u(dia, 2 * f + 6)))
        return b
    }

    private fun roundCases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        repeat(count) {
            val inp = roundInput(rnd, rnd.nextBoolean())
            roundCase("라운드", inp.toString(), RoundCalc.machine(inp), RoundCalc.calc(inp), inp.inner, inp.noseR, null, inp.tip == null || inp.tip == NoseComp.standardTip(inp.inner, false))?.let { out += it }
        }
        return out
    }

    private fun arc3Cases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        repeat(count) {
            val (inp, label) = arc3Input(rnd, rnd.nextBoolean()) ?: return@repeat
            val rev = inp.z3!! > inp.z1!!
            roundCase("3점호", inp.toString(), RoundCalc.machine(inp), RoundCalc.arc3(inp), inp.inner, inp.noseR, label, inp.tip == null || inp.tip == NoseComp.standardTip(inp.inner, rev))?.let { out += it }
        }
        return out
    }

    private fun holderCases(seed: Int, count: Int): List<Case> {
        val rnd = Random(seed)
        val out = mutableListOf<Case>()
        repeat(count) {
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val arc = rnd.nextInt(3) == 0
            val tag: String
            val calc: RoundResult
            val machine: RoundResult
            var label: String? = null
            if (arc) {
                val (a, l) = arc3Input(rnd, true, r) ?: return@repeat
                tag = a.toString()
                calc = RoundCalc.arc3(a)
                machine = RoundCalc.machine(a)
                label = l
            } else {
                val inp = roundInput(rnd, true, r)
                tag = inp.toString()
                calc = RoundCalc.calc(inp)
                machine = RoundCalc.machine(inp)
            }
            val sol = calc.solution ?: return@repeat
            val b = bar(rnd, r)
            for (safe in listOf(0.0, 0.4)) {
                val plan = HolderStop.plan(sol, machine.solution, r, !arc, b, safe)
                val cutNc = plan.machine?.nc ?: continue
                val d = sol.drawing.segs
                val lab = when {
                    RoundWarning.CONCAVE_R_LE_NOSE in calc.warnings -> "도면 알람"
                    narrow(d, r) -> "좁은 구멍"
                    label != null -> label
                    else -> MAIN
                }
                out += Case("보링바", "$tag + $b · 안전 여유 $safe · ${plan.warnings}", cutNc, null, sol.tip, r, d, true, 1.0, End.OTHER, d.last().end, Pt(0.0, 0.0), null, lab)
            }
        }
        return out
    }

    // ================================================================ 시험

    private fun finish(t: Tally) {
        println(t.report())
        assertEquals(0, t.mainFails, t.report())
        assertEquals(0, t.silentPast, "막힌 끝 · 시작 너머로 가는데 SHORT_END_PAST 경고가 없는 글\n" + t.report())
        assertEquals(0, t.silentExit, "빼는 줄 · G40 · 물러남이 실패하는데 EXIT_NOT_FOUND 경고가 없는 글(도면 알람 · 좁은 구멍 빼고)\n" + t.report())
    }

    @Test
    fun tapersBreak() {
        val t = Tally("테이퍼 G41/G42 (씨앗 7707)")
        for (c in taperCases(7707, 1600)) check(c, t)
        assertTrue(t.cases > 600, t.report())
        finish(t)
    }

    @Test
    fun roundsBreak() {
        val t = Tally("라운드 · 3점호 기계 보정 (씨앗 8808 · 9909)")
        for (c in roundCases(8808, 1400) + arc3Cases(9909, 900)) check(c, t)
        assertTrue(t.cases > 500, t.report())
        finish(t)
    }

    @Test
    fun holderCutBreak() {
        val t = Tally("보링바 잘린 기계 보정 글 (씨앗 6606)")
        for (c in holderCases(6606, 260)) check(c, t)
        assertTrue(t.cases > 20, t.report())
        finish(t)
    }

    // ================================================================ ③ 나사 G92

    @Test
    fun threadG92Break() {
        val rnd = Random(30117)
        val tools = mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD))
        val tpre = listOf("T0101", "G97S1000M03")
        fun run(lines: List<String>, chamfer: Double) =
            GSim.run(SimInput((tpre + lines + "M30").joinToString("\n"), MachineSetup(g92ChamferLeads = chamfer), tools))
        fun groups(r: SimRun) = r.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.THREAD }.groupBy { it.line to it.pass }.values.toList()
        fun u(a: Double, b: Double) = a + (b - a) * rnd.nextDouble()
        val fails = linkedMapOf<String, Int>()
        val first = linkedMapOf<String, String>()
        var cases = 0
        var solved = 0
        repeat(400) {
            cases++
            val inner = rnd.nextBoolean()
            val spec = if (rnd.nextInt(3) != 0) ThreadTable.METRIC[rnd.nextInt(ThreadTable.METRIC.size)] else ThreadTable.UN[rnd.nextInt(ThreadTable.UN.size)]
            val reverse = rnd.nextInt(4) == 0
            val len = r3(u(3.0, 40.0))
            val zs = if (!reverse) r3(u(1.0, 8.0)) else r3(-len - u(1.0, 5.0))
            val ze = if (!reverse) -len else r3(u(-2.0, 0.0))
            val inp = G76Input(
                inner = inner, spec = spec, dir = if (reverse) ThreadDir.REVERSE else ThreadDir.FORWARD, zStart = zs, zEnd = ze,
                rpm = if (rnd.nextBoolean()) null else r3(u(200.0, 2500.0)), target = if (rnd.nextBoolean()) null else r3(u(0.0, 100.0)),
                firstCut = r3(u(0.04, 0.6)), finishCount = if (rnd.nextBoolean()) null else 1 + rnd.nextInt(4),
            )
            val s = G76Calc.calc(inp).solution ?: return@repeat
            solved++
            val tag = "$inp"
            val seen = mutableSetOf<String>()
            fun bad(kind: String, msg: String) {
                fails[kind] = (fails[kind] ?: 0) + (if (seen.add(kind)) 1 else 0)
                if (kind !in first) first[kind] = "$kind: $tag\n  $msg\n  G92: ${s.g92.joinToString(" / ")}"
            }
            val g = s.g92.indexOfFirst { it.startsWith("G92") }
            if (g < 1 || s.g92[g - 1] != "M23" || s.g92.takeLast(2).first() != "M24" || !s.g92.last().startsWith("G0Z")) bad("STRUCT", "M23 · M24 자리")
            val xs = s.passes.map { it.xDia }
            val last = r3(s.x)
            val sgn = sign(ze - zs)
            // 끝 빼기 0: 멈춤 0 · 회차 X · 마지막 X
            val r0 = run(s.g92, 0.0)
            if (r0.stop != null) {
                bad("STOP", "${r0.stop}")
                return@repeat
            }
            if (r0.issues.isNotEmpty()) bad("ISSUE", "${r0.issues.map { it.code }}")
            val g0 = groups(r0)
            if (g0.size != xs.size + 1) bad("PASS_COUNT", "회차 ${g0.size}, 분석표 + 1 = ${xs.size + 1}")
            for ((k, grp) in g0.withIndex()) {
                val want = xs.getOrElse(k) { xs.last() }
                if (grp.size != 1) bad("PIECES_OFF", "끝 빼기 0 인데 ${k + 1}회가 ${grp.size} 토막")
                val p = grp.first().tip
                if (abs(p.end.xDia - want) > tol) bad("PASS_X", "${k + 1}회 X ${f3(p.end.xDia)} ≠ ${f3(want)}")
                if (abs(p.start.z - zs) > tol || abs(grp.last().tip.end.z - ze) > tol) bad("PASS_Z", "${k + 1}회 Z ${f3(p.start.z)} → ${f3(grp.last().tip.end.z)}")
            }
            if (abs(g0.last().first().tip.end.xDia - last) > tol) bad("LAST_X", "마지막 X ${f3(g0.last().first().tip.end.xDia)} ≠ G76 X ${f3(last)}")
            val g76 = groups(run(s.program, 0.0))
            if (g76.isEmpty() || abs(g76.last().first().tip.end.xDia - last) > tol) bad("G76_LAST_X", "G76 마지막 회 X ${g76.lastOrNull()?.first()?.tip?.end?.xDia}")
            // 시작점 · 나사 구간 안 급송이 산봉우리 밖(공기)
            val crest = s.limits.crest
            if (if (inner) !(s.startX > 0 && s.startX < crest.min) else s.startX <= crest.max) bad("START_X", "시작 X ${f3(s.startX)} · 산봉우리 ${crest}")
            val lo = min(zs, ze) + 1e-6
            val hi = max(zs, ze) - 1e-6
            for (m in r0.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.RAPID }) {
                for (p in listOf(m.tip.start, m.tip.end, (m.tip.start + m.tip.end) * 0.5)) {
                    if (p.z !in lo..hi) continue
                    val air = if (inner) p.xDia <= crest.min + 1e-9 else p.xDia >= crest.max - 1e-9
                    if (!air) bad("RAPID_IN_PART", "${m.line - tpre.size}줄 급송 ${xz(m.tip.start)} → ${xz(m.tip.end)} 이 산봉우리 안")
                }
            }
            // 끝 빼기 1 리드 · 0.5 리드: M23 뒤 모든 회(M24 줄 회 포함)가 곧은 토막 + 시작 X 쪽으로 빼는 토막
            for (k in listOf(1.0, 0.5)) {
                val rk = run(s.g92, k)
                if (rk.stop != null) {
                    bad("STOP_CHAMFER", "${rk.stop}")
                    continue
                }
                for ((i, grp) in groups(rk).withIndex()) {
                    if (grp.size != 2) {
                        bad("PULLOUT_MISSING", "끝 빼기 $k 리드인데 ${i + 1}회가 ${grp.size} 토막")
                        continue
                    }
                    val x = grp[0].tip.end.x
                    // 글에 쓴 리드(F — 0.0001 로 반올림)로 — 시뮬레이터는 글을 읽는다
                    val leadNc = Regex("F([0-9.]+)").find(s.g92[g])!!.groupValues[1].toDouble()
                    val c = min(k * leadNc, abs(ze - zs))
                    // 사이클 시작점 X 도 글에 쓴 값(0.001)으로
                    val sx = Regex("X(-?[0-9.]+)").find(s.g92.first { it.startsWith("G0X") })!!.groupValues[1].toDouble() / 2
                    val lift = min(c, max(0.0, abs(sx - x)))
                    val wantZ = ze - sgn * c
                    val wantX = x + sign(sx - x) * lift
                    if (abs(grp[0].tip.end.z - wantZ) > 1e-6 || abs(grp[1].tip.end.z - ze) > 1e-6 || abs(grp[1].tip.end.x - wantX) > 1e-6) {
                        bad("PULLOUT_SHAPE", "끝 빼기 $k: ${i + 1}회 ${xz(grp[0].tip.end)} → ${xz(grp[1].tip.end)} (손 계산 Z${f3(wantZ)} → X${f3(2 * wantX)} Z${f3(ze)})")
                    }
                }
                // M23 · M24 를 뺀 옛 글: 처음 켬이라 늘 설정대로 끝 빼기, 회 수 = 분석표
                val old = s.g92.filter { it != "M23" && it != "M24" }
                val ro = run(old, k)
                val go = groups(ro)
                if (ro.stop != null || go.size != xs.size || go.any { it.size != 2 }) bad("OLD_PROGRAM", "M 없는 글: 멈춤 ${ro.stop} · 회 ${go.size} · 토막 ${go.map { it.size }}")
                val gk = groups(rk)
                for (i in go.indices) if (i < gk.size && (go[i].size != gk[i].size || go[i].indices.any { j -> !same(go[i][j].tip, gk[i][j].tip) })) {
                    bad("OLD_VS_NEW", "${i + 1}회가 M 있는 글과 다름")
                }
            }
            // M24 를 앞에 두면 끝 빼기 없음(설정이 있어도)
            val off = run(listOf("M24") + s.g92.filter { it != "M23" && it != "M24" }, 1.0)
            if (groups(off).any { it.size != 1 }) bad("M24_OFF", "M24 뒤에도 끝 빼기")
            // 다듬질 블록
            val fin = groups(run(s.g92Finish, 0.0))
            if (fin.size != 3 || fin.any { abs(it.first().tip.end.xDia - last) > tol }) bad("FINISH", "다듬질 회 ${fin.size} · X ${fin.map { f3(it.first().tip.end.xDia) }}")
        }
        val rep = buildString {
            appendLine("== 나사 G92: 사례 $cases · 풀림 $solved · 실패 $fails")
            for ((k, m) in first) appendLine("-- 첫 예 $m")
        }
        println(rep)
        assertTrue(solved > 150, rep)
        assertEquals(0, fails.values.sum(), rep)
    }

    // ================================================================ ④ 표기 — 따로 짠 옛 꼴 되돌리기

    /** 회사 표기 → 옛 꼴(낱말 사이 빈칸, G0~G3 → G00~G03, 앞 0, 이동 줄마다 G00~G03) */
    private fun oldStyle(lines: List<String>): List<String> {
        var motion: Int? = null
        val word = Regex("([A-Z])(-?[0-9]*\\.?[0-9]*)")
        return lines.map { line ->
            if (line.startsWith("(")) return@map line
            val ws = word.findAll(line).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val gs = ws.filter { it.first == "G" }.mapNotNull { it.second.toDoubleOrNull()?.toInt() }
            val own = gs.lastOrNull { it in listOf(0, 1, 2, 3, 90, 92, 94) }
            val other = gs.any { it !in listOf(0, 1, 2, 3, 90, 92, 94, 40, 41, 42, 96, 97, 98, 99) }
            val hasAxis = ws.any { it.first in listOf("X", "Z", "U", "W") }
            val out = ws.map { (k, v) ->
                when {
                    k == "G" && v.length == 1 -> "G0$v"
                    k in listOf("X", "Z", "U", "W", "R", "I", "K", "F") && v.startsWith(".") -> "${k}0$v"
                    k in listOf("X", "Z", "U", "W", "R", "I", "K", "F") && v.startsWith("-.") -> "$k-0${v.drop(1)}"
                    else -> "$k$v"
                }
            }.toMutableList()
            val m = motion
            if (own != null) motion = own
            else if (!other && hasAxis && m != null && m in 0..3) out.add(out.indexOfFirst { it.first() in "XZUW" }, "G0$m")
            out.joinToString(" ")
        }
    }

    @Test
    fun notationSamePath() {
        val rnd = Random(4404)
        val shown = mutableListOf<String>()
        var compared = 0
        fun cmp(tag: String, text: List<String>, tools: Map<Int, ToolOffset>, machine: MachineSetup = MachineSetup()) {
            val old = oldStyle(text)
            val a = GSim.run(SimInput((pre + text + "M30").joinToString("\n"), machine, tools))
            val b = GSim.run(SimInput((pre + old + "M30").joinToString("\n"), machine, tools))
            assertEquals(b.stop?.code, a.stop?.code, "$tag 멈춤\n$text\n$old")
            val ma = a.steps.filterIsInstance<MoveStep>()
            val mb = b.steps.filterIsInstance<MoveStep>()
            assertEquals(mb.size, ma.size, "$tag 이동 수\n$text\n$old")
            for ((p, q) in ma.zip(mb)) {
                assertEquals(q.kind, p.kind, "$tag ${p.line}줄")
                assertTrue(same(p.nose, q.nose) && same(p.tip, q.tip), "$tag ${p.line}줄 새 ${p.nose} / 옛 ${q.nose}\n$text\n$old")
            }
            compared++
            if (shown.size < 4) shown += "$tag\n  새: ${text.joinToString(" / ")}\n  옛: ${old.joinToString(" / ")}"
        }
        for (c in taperCases(rnd.nextInt(), 120).take(40)) {
            cmp("테이퍼 G41/G42 ${c.tag}", c.frame, mapOf(1 to ToolOffset(c.r, c.directTip)), MachineSetup(home = Pt(c.front * 100, 100.0)))
            c.direct?.let { cmp("테이퍼 직접 ${c.tag}", it, mapOf(1 to ToolOffset(c.r, c.directTip)), MachineSetup(home = Pt(c.front * 100, 100.0))) }
        }
        for (c in roundCases(rnd.nextInt(), 150).take(30) + arc3Cases(rnd.nextInt(), 80).take(15)) {
            cmp("라운드 기계 ${c.tag}", c.frame, mapOf(1 to ToolOffset(c.r, c.directTip)))
            c.direct?.let { cmp("라운드 직접 ${c.tag}", it, mapOf(1 to ToolOffset(c.r, c.directTip))) }
        }
        val thr = mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD))
        for (spec in listOf(ThreadTable.METRIC[5], ThreadTable.METRIC[20], ThreadTable.UN[3])) for (inner in listOf(false, true)) {
            val s = G76Calc.calc(G76Input(inner = inner, spec = spec, zStart = 4.0, zEnd = -18.0, firstCut = 0.2)).solution ?: continue
            cmp("G92 $spec 안 $inner", s.g92, thr, MachineSetup(g92ChamferLeads = 1.0))
            cmp("G92 다듬질 $spec", s.g92Finish, thr, MachineSetup(g92ChamferLeads = 1.0))
            cmp("G76 $spec", s.program, thr)
        }
        println("== 표기: 견준 글 $compared\n" + shown.joinToString("\n"))
        assertTrue(compared > 100)
    }

    private companion object {
        const val MAIN = "기본"
    }
}
