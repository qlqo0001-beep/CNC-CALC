package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.RefG4x
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test

/**
 * 판정용 진단(2026-10-28, simulator-design.md 13.7) — 단언 없음, 출력만(테스트 결과 XML 의 system-out).
 * 테이퍼 G41/G42 글의 끝 · 시작 · 후퇴와 라운드 안내 글을 두 모형으로 돌려 노즈 자리를 숫자로 낸다:
 * GSim(calc.sim GComp — 급송 꺾임/직선) · RefG4x(= VerifyFlipTest.simG4x(typeB = false) 를 옮긴 것, 급송은 늘 직선).
 * 사례 묶음은 SimVerifyTest.taperCases 와 같다. 고칠 규칙 후보도 같은 사례에 돌려 본다(코드는 고치지 않음).
 */
class DiagG4xTest {
    private val pre = listOf("T0101", "G97 S1000 M03", "G99 F0.2")
    private val tol = 1e-3

    private fun sim(lines: List<String>, r: Double, tip: Int, rapid: RapidPath = RapidPath.DOGLEG): SimRun =
        GSim.run(SimInput((pre + lines).joinToString("\n"), MachineSetup(rapidPath = rapid), mapOf(1 to ToolOffset(r, tip))))

    private fun moves(run: SimRun) = run.steps.filterIsInstance<MoveStep>()
    private fun f4(v: Double) = if (v.isNaN()) "NaN" else ((v * 10000).roundToLong() / 10000.0).toString()
    private fun c(p: Pt) = "(Z${f4(p.z)} X${f4(p.xDia)})"

    // ---------------------------------------------------------------- 기하(SimVerifyTest 와 같은 식)
    private fun ang(v: Pt) = atan2(v.x, v.z)
    private fun wrap(a: Double): Double {
        val t = 2 * PI
        var r = a % t
        if (r < 0) r += t
        return r
    }
    private fun radiusOf(a: Arc) = (a.start - a.center).length
    private fun sweepOf(a: Arc): Double {
        if ((a.end - a.start).length < 1e-12) return 0.0
        val d = ang(a.end - a.center) - ang(a.start - a.center)
        return wrap(if (a.cw) -d else d)
    }
    private fun lenOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> radiusOf(s) * sweepOf(s)
    }
    private fun at(s: Seg, f: Double): Pt = when (s) {
        is Line -> s.start + (s.end - s.start) * f
        is Arc -> {
            val a = ang(s.start - s.center) + (if (s.cw) -1.0 else 1.0) * sweepOf(s) * f
            s.center + Pt(cos(a), sin(a)) * radiusOf(s)
        }
    }
    private fun pts(s: Seg, step: Double = 0.01): List<Pt> {
        val n = max(4, ceil(lenOf(s) / step).toInt())
        return (0..n).map { at(s, it.toDouble() / n) }
    }
    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 < 1e-24) (p - s.start).length else (s.start + d * (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0) - p).length
        }
        is Arc -> {
            val sw = sweepOf(s)
            val rad = radiusOf(s)
            val u = wrap((if (s.cw) -1.0 else 1.0) * (ang(p - s.center) - ang(s.start - s.center)))
            if (rad > 1e-12 && sw > 0 && u <= sw) abs((p - s.center).length - rad) else min((p - s.start).length, (p - s.end).length)
        }
    }
    private fun distSet(p: Pt, segs: List<Seg>) = segs.minOf { dist(p, it) }
    private fun unitOf(v: Pt) = v * (1 / v.length)
    private fun tanEnd(s: Seg): Pt = when (s) {
        is Line -> unitOf(s.end - s.start)
        is Arc -> unitOf(s.end - s.center).let { Pt(-it.x, it.z) * (if (s.cw) -1.0 else 1.0) }
    }
    /** 노즈 토막이 도면선을 파고든 깊이(r − 가장 가까운 거리, + 면 파고듦) */
    private fun depth(nose: Seg, draw: List<Seg>, r: Double) = r - pts(nose).minOf { distSet(it, draw) }

    /** 꺾인 급송(축마다 따로 — X 20 m/min 반경 · Z 24 m/min, MachineSetup 처음 값)의 노즈 토막 */
    private fun dogleg(a: Pt, b: Pt): List<Seg> {
        val dz = b.z - a.z
        val dx = b.x - a.x
        if (abs(dz) <= 1e-9 || abs(dx) <= 1e-9) return listOf(Line(a, b))
        val tX = abs(dx) / 20_000.0
        val tZ = abs(dz) / 24_000.0
        if (abs(tX - tZ) <= 1e-12 * max(tX, tZ)) return listOf(Line(a, b))
        val br = if (tX < tZ) Pt(a.z + (if (dz > 0) 1 else -1) * 24_000.0 * tX, b.x) else Pt(b.z, a.x + (if (dx > 0) 1 else -1) * 20_000.0 * tZ)
        return listOf(Line(a, br), Line(br, b))
    }

    /** RefG4x(= VerifyFlipTest.simG4x 형식 A) 노즈 경로(시작 블록부터 취소 블록까지) */
    private fun ref(lines: List<String>, r: Double, tip: Int): List<Seg>? {
        val tv = NoseComp.tipVector(tip, r)
        val out = RefG4x.run(RefG4x.parse(lines.filter { !it.startsWith("(") }), RefG4x.P(tv.z, tv.x), r) ?: return null
        return out.map {
            when (it) {
                is RefG4x.Ln -> Line(Pt(it.a.z, it.a.x), Pt(it.b.z, it.b.x))
                is RefG4x.Ac -> Arc(Pt(it.a.z, it.a.x), Pt(it.b.z, it.b.x), Pt(it.c.z, it.c.x), it.cw)
            }
        }
    }

    // ---------------------------------------------------------------- 사례(SimVerifyTest.taperCases 그대로)
    private fun TaperInput.flipped() =
        copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start, flip = !flip)
    private fun DoubleTaperInput.flipped() =
        copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start, flip = !flip)

    private fun taperCases(): List<Triple<String, Double, TaperResult>> {
        val out = mutableListOf<Triple<String, Double, TaperResult>>()
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (type in 1..5) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 0.8)) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = Fmt.nc(60 + up * 2 * 6 * tan(deg * PI / 180)).toDouble()
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, x2, -6.0, cornerR1 = r1, cornerR2 = r2)
                val right = if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                val left = if (type == 2 || type == 4) inp.copy(end = Fmt.nc(x2 + up * 12).toDouble()) else inp.copy(end = -12.0)
                for (i in listOf(inp, right, left).flatMap { listOf(it, it.flipped()) }) out += Triple(i.toString(), r, TaperCalc.calc(i))
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            val inp = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
            for (i in listOf(inp, inp.copy(start = 4.0), inp.copy(end = -14.0), inp.copy(start = 4.0, end = -14.0)).flatMap { listOf(it, it.flipped()) }) {
                out += Triple(i.toString(), 0.4, TaperCalc.calcDouble(i))
            }
        }
        return out
    }

    private fun cutOf(segs: List<Seg>, fromStart: Boolean, toEnd: Boolean): List<Seg> {
        var l = segs
        if (!fromStart) l = l.drop(1)
        if (!toEnd) l = l.dropLast(1)
        return l.filter { lenOf(it) > 1e-9 }
    }

    private fun flag(tag: String, key: String) = Regex("$key=([^,)]+)").find(tag)?.groupValues?.get(1)
    private fun shapeKey(tag: String): String {
        val dbl = tag.startsWith("Double")
        val inner = flag(tag, "inner") == "true"
        val rev = flag(tag, "reverse") == "true"
        val flip = flag(tag, "flip") == "true"
        val endGiven = flag(tag, "end") != "null"
        val startGiven = flag(tag, "start") != "null"
        return (if (rev) "calcREV" else "fwd") + (if (flip) "+flip" else "") + " " + (if (inner) "ID" else "OD") +
            (if (dbl) " double" else " TYPE${flag(tag, "type")}") + (if (startGiven) " start" else "") + (if (endGiven) " end" else "")
    }

    // ================================================================ 1. 재현 글 두 개 + 끝 대표 사례 — 두 모형 숫자

    private val repA = "(R0.8 T1) / G00 X63.2 Z1.6 / G41 G01 X60. Z0. / G01 X48. Z-6. / G40 G00 X44.869 Z-6.434"
    private val repB = "(R0.8 T4) / G00 X51.208 Z1.6 / G42 G01 X60. Z0. / G01 X92.97 Z-6. / G40 G00 X99.339 Z-5.799"

    private fun detail(name: String, tag: String, r: Double, res: TaperResult, lines0: List<String>? = null) {
        val s = res.solution!!
        val lines = lines0 ?: s.ncG41G42
        val draw = s.drawing.segs
        val cen = cutOf(s.center.segs, s.fromStart, s.toEnd)
        println("==== $name")
        println("  case: $tag")
        println("  warnings=${res.warnings} side=${s.side} tip=${s.tip} tv=${c(NoseComp.tipVector(s.tip, r))}")
        println("  G41/G42 NC: " + lines.joinToString(" / "))
        println("  G40 NC    : " + s.ncG40.joinToString(" / "))
        println("  drawing   : " + draw.joinToString(" ; ") { "${it::class.simpleName} ${c(it.start)}->${c(it.end)}" })
        println("  calc nose path (cut) start ${c(cen.first().start)} end ${c(cen.last().end)}")
        for (rp in listOf(RapidPath.DOGLEG, RapidPath.LINEAR)) {
            val run = sim(lines, r, s.tip, rp)
            println("  GSim $rp stop=${run.stop?.code} issues=${run.issues.map { it.code }}")
            val ms = moves(run)
            val first = ms.firstOrNull { it.line > pre.size }?.line ?: 0
            for (m in ms.filter { it.line > first }) {
                println("    [${(pre + lines)[m.line - 1]}] ${m.kind} nose ${c(m.nose.start)} -> ${c(m.nose.end)} depth ${f4(depth(m.nose, draw, r))}")
            }
        }
        val rf = ref(lines, r, s.tip)
        if (rf == null) println("  RefG4x: null (concave R<r)") else {
            println("  RefG4x (simG4x Type A, straight G00):")
            for (p in rf) println("    ${p::class.simpleName} nose ${c(p.start)} -> ${c(p.end)} depth ${f4(depth(p, draw, r))}")
        }
        val d = sim(s.ncG40, r, s.tip)
        val dm = moves(d)
        val df = dm.firstOrNull { it.line > pre.size }?.line ?: 0
        println("  direct(G40) GSim DOGLEG:")
        for (m in dm.filter { it.line > df }) println("    [${(pre + s.ncG40)[m.line - 1]}] ${m.kind} nose ${c(m.nose.start)} -> ${c(m.nose.end)} depth ${f4(depth(m.nose, draw, r))}")
    }

    @Test
    fun reproductions() {
        val cases = taperCases()
        for ((name, rep) in listOf("2-1 start block gouge" to repA, "2-2 cancel G00 gouge" to repB)) {
            val hit = cases.firstOrNull { it.third.solution?.ncG41G42?.joinToString(" / ") == rep }
            if (hit == null) println("==== $name: not found in taperCases: $rep") else detail(name, hit.first, hit.second, hit.third)
        }
        // 문제 1 대표: 뒤집기 + 끝점 넣음 외경 TYPE3 20° r0.4 · r0.8, 내경 TYPE3, 계산기 역방향 TYPE2
        for ((inner, type, rr) in listOf(Triple(false, 3, 0.4), Triple(false, 3, 0.8), Triple(true, 3, 0.4))) {
            val up = if (inner == (type == 5)) 1.0 else -1.0
            val x2 = Fmt.nc(60 + up * 2 * 6 * tan(20 * PI / 180)).toDouble()
            val inp = TaperInput(inner, type, false, rr, null, 60.0, 0.0, x2, -6.0).copy(start = 6.0).flipped()
            detail("1 end: flip+end ${if (inner) "ID" else "OD"} TYPE$type r$rr", inp.toString(), rr, TaperCalc.calc(inp))
        }
        // 같은 끝을 정방향(뒤집지 않음, 끝 Z-12 — 척 쪽 벽)으로: 안쪽 모서리 교점이 직접 보정 끝과 같은가
        val x2 = Fmt.nc(60 + 2 * 6 * tan(20 * PI / 180)).toDouble()
        val fwd = TaperInput(false, 3, false, 0.4, null, 60.0, 0.0, x2, -6.0, end = -12.0)
        detail("1 ref: forward OD TYPE3 end Z-12 (wall beyond)", fwd.toString(), 0.4, TaperCalc.calc(fwd))
    }

    /** 고친 글 후보를 GSim(꺾인 · 직선 급송)으로 — 멈춤, 블록마다 노즈 · 파고듦, 끝 꼭짓점 남김, 노즈가 가장 멀리 간 Z */
    private fun variant(name: String, lines: List<String>, r: Double, tip: Int, draw: List<Seg>, vEnd: Pt?) {
        println("  -- $name: " + lines.joinToString(" / "))
        for (rp in listOf(RapidPath.DOGLEG, RapidPath.LINEAR)) {
            val run = sim(lines, r, tip, rp)
            val ms = moves(run)
            val first = ms.firstOrNull { it.line > pre.size }?.line ?: 0
            val mv = ms.filter { it.line > first }
            val left = if (vEnd == null || mv.isEmpty()) Double.NaN else mv.flatMap { pts(it.nose) }.minOf { (it - vEnd).length } - r
            val zMax = mv.flatMap { pts(it.nose) }.maxOfOrNull { it.z + r } ?: Double.NaN
            val worst = mv.maxByOrNull { depth(it.nose, draw, r) }
            println(
                "     $rp stop=${run.stop?.code}@${run.stop?.line?.let { it - pre.size }} vertexLeft ${f4(left)} noseMaxZ ${f4(zMax)} " +
                    "worst gouge ${worst?.let { f4(depth(it.nose, draw, r)) + " [" + (pre + lines)[it.line - 1] + "]" }}",
            )
            if (rp == RapidPath.DOGLEG) for (m in mv) println("       [${(pre + lines)[m.line - 1]}] ${m.kind} nose ${c(m.nose.start)} -> ${c(m.nose.end)}")
        }
    }

    @Test
    fun fixVariants() {
        // 문제 1: 뒤집기 + 끝점 넣음 외경 TYPE3 r0.4 — 끝 Z6 은 부품 앞(자유 끝)
        run {
            val r = 0.4
            val inp = TaperInput(false, 3, false, r, null, 60.0, 0.0, 64.368, -6.0).copy(start = 6.0).flipped()
            val s = TaperCalc.calc(inp).solution!!
            val draw = s.drawing.segs
            val v = draw.last().end
            val nc = s.ncG41G42
            val body = nc.dropLast(1)
            println("==== fix 1 (end): ${inp}")
            variant("V0 now (perpendicular G40 G00 = inner corner)", nc, r, s.tip, draw, v)
            variant("V1 extend last G01 by r (Z6.4) then G40 G00 X62.", body.dropLast(1) + "G01 Z6.4" + "G40 G00 X62.", r, s.tip, draw, v)
            variant("V2 cancel along end tangent G40 G01 Z7. then G00 X62.", body + "G40 G01 Z7." + "G00 X62.", r, s.tip, draw, v)
            variant("V3 G40 alone (no move) then G00 X62. (FF 5.4.4(e))", body + "G40" + "G00 X62.", r, s.tip, draw, v)
            variant("V4 G40 G00 X62. I0. K1. (FF 5.4.4(g)-(i))", body + "G40 G00 X62. I0. K1.", r, s.tip, draw, v)
            // V3/V4 의 FANUC 경로(설명서): 끝 법선 e + nA·r 에서 후퇴점 − 팁 벡터로. GSim 은 지금 멈추므로 손으로
            val open = sim(body, r, s.tip)
            val oEnd = moves(open).last().nose.end
            val tv = NoseComp.tipVector(s.tip, r)
            val ret = dogleg(oEnd, Pt.ofDia(6.0, 62.0) - tv)
            println("     V3/V4 by manual: end nose ${c(oEnd)} vertexLeft ${f4((oEnd - v).length - r)} retract ${ret.joinToString { c(it.start) + "->" + c(it.end) }} gouge ${f4(ret.maxOf { depth(it, draw, r) })}")
        }
        // 문제 2-1: 시작 블록 — 재현 A(계산기 역방향 + 뒤집기, 내경 T1)와 화면이 만드는 정방향 외경 TYPE5 70°(T3) — 첫 요소에 공구 쪽 직각 + lead(r 뒤)
        run {
            val inpA = TaperInput(true, 1, true, 0.8, null, 48.0, -6.0, 60.0, 0.0, flip = true)
            val sA = TaperCalc.calc(inpA).solution!!
            println("==== fix 2-1 (start) A: $inpA")
            variant("now", sA.ncG41G42, 0.8, sA.tip, sA.drawing.segs, null)
            variant("perpendicular onto lead (E - t*r), then along", listOf("G00 X58.586 Z1.838", "G41 G01 X61.131 Z0.566", "G01 X60. Z0.") + sA.ncG41G42.drop(3), 0.8, sA.tip, sA.drawing.segs, null)
            val x2 = Fmt.nc(60 - 2 * 6 * tan(70 * PI / 180)).toDouble()
            val inp5 = TaperInput(false, 5, false, 0.8, null, 60.0, 0.0, x2, -6.0)
            val s5 = TaperCalc.calc(inp5).solution!!
            println("==== fix 2-1 (start) fwd OD TYPE5 70: $inp5 warn=${TaperCalc.calc(inp5).warnings}")
            variant("now", s5.ncG41G42, 0.8, s5.tip, s5.drawing.segs, null)
            // E = (0, 30), t = 경사 단위, G42 → n = −left(t). lead = E − t·r, G00 = lead + n·(c + lift)
            val e = Pt(0.0, 30.0)
            val t = unitOf(Pt.ofDia(-6.0, x2) - e)
            val n = Pt(-t.x, t.z) * -1.0
            val tv = NoseComp.tipVector(s5.tip, 0.8)
            val lift = max(0.0, 0.8 + (n dot tv))
            val lead = e - t * 0.8
            val g0 = lead + n * (1.0 + lift)
            fun xz(p: Pt) = "X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)}"
            variant(
                "perpendicular onto lead (E - t*r), then along (lift ${f4(lift)})",
                listOf("G00 ${xz(g0)}", "G42 G01 ${xz(lead)}", "G01 X60. Z0.") + s5.ncG41G42.drop(3), 0.8, s5.tip, s5.drawing.segs, null,
            )
        }
        // 문제 2-2: 취소 G00 — 재현 B 와 화면이 만드는 정방향 외경 TYPE1(끝 비움, 모서리 R) — G40 G01 로(직선)
        run {
            val inpB = TaperInput(false, 1, true, 0.8, null, 92.97, -6.0, 60.0, 0.0, flip = true)
            val sB = TaperCalc.calc(inpB).solution!!
            println("==== fix 2-2 (cancel) B: $inpB")
            variant("now", sB.ncG41G42, 0.8, sB.tip, sB.drawing.segs, null)
            variant("G40 G01 instead of G40 G00", sB.ncG41G42.dropLast(1) + sB.ncG41G42.last().replace("G40 G00", "G40 G01"), 0.8, sB.tip, sB.drawing.segs, null)
            for ((cr1, cr2) in listOf(0.0 to 0.0, 1.5 to 0.0, 2.0 to 0.6)) {
                val x2 = Fmt.nc(60 + 2 * 6 * tan(70 * PI / 180)).toDouble()
                val inp = TaperInput(false, 1, false, 0.8, null, 60.0, 0.0, x2, -6.0, cornerR1 = cr1, cornerR2 = cr2)
                val s = TaperCalc.calc(inp).solution!!
                println("==== fix 2-2 (cancel) fwd OD TYPE1 70 CR $cr1/$cr2: warn=${TaperCalc.calc(inp).warnings}")
                variant("now", s.ncG41G42, 0.8, s.tip, s.drawing.segs, null)
                variant("G40 G01 instead of G40 G00", s.ncG41G42.dropLast(1) + s.ncG41G42.last().replace("G40 G00", "G40 G01"), 0.8, s.tip, s.drawing.segs, null)
            }
        }
    }

    // ================================================================ 2. 끝 차이(문제 1) 갈래 — 계산기 역방향(화면이 안 만듦) 나눔

    private class EndRow(
        val key: String, val ratio: Double, val inner: Boolean, val simLeft: Double, val dirLeft: Double, val simBeyond: Double, val dirBeyond: Double,
        val fixLeft: Double, val fixBeyond: Double, val fixGouge: Double, val nc: String, val tvT: Double,
    )

    @Test
    fun endClassification() {
        val rows = mutableListOf<EndRow>()
        var cases = 0
        var stops = 0
        for ((tag, r, res) in taperCases()) {
            val s = res.solution ?: continue
            cases++
            val lines = s.ncG41G42
            val run = sim(lines, r, s.tip)
            if (run.stop != null) {
                stops++
                continue
            }
            val iStart = pre.size + lines.indexOfFirst { "G41" in it || "G42" in it } + 1
            val iCancel = pre.size + lines.indexOfFirst { "G40" in it } + 1
            val ms = moves(run)
            val shape = ms.filter { it.line in (iStart + 1) until iCancel }
            val cancelMv = ms.filter { it.line == iCancel }
            if (shape.isEmpty() || cancelMv.isEmpty()) continue
            val cen = cutOf(s.center.segs, s.fromStart, s.toEnd)
            val endNose = shape.last().nose.end
            val d = (endNose - cen.last().end).length
            if (d <= tol) continue
            val lastCmd = shape.last().cmd!!
            val cancelCmd = cancelMv.first().cmd!!
            val e = lastCmd.end
            val tA = tanEnd(lastCmd)
            val tB = unitOf(cancelCmd.end - cancelCmd.start)
            val side = if (lines.any { "G41" in it }) 1.0 else -1.0
            val innerCorner = (tA cross tB) * side > 1e-6
            // 끝 꼭짓점에 남는 살 · 끝 좌표를 진행 방향으로 지나간 노즈 끝(+ 면 지나감)
            fun left(noses: List<Seg>) = noses.flatMap { pts(it) }.minOf { (it - e).length } - r
            fun beyond(noses: List<Seg>) = noses.flatMap { pts(it) }.maxOf { ((it - e) dot tA) + r }
            val simN = (shape + cancelMv).map { it.nose }
            val dRun = sim(s.ncG40, r, s.tip)
            val dm = moves(dRun)
            val df = dm.firstOrNull { it.line > pre.size }?.line ?: 0
            val dirN = dm.filter { it.line > df + 1 }.map { it.nose }
            // 고칠 규칙 후보: 취소를 이동 없는 G40 블록으로(FF 5.4.4 (e) · F0 14.3.4 — 앞 블록 끝 법선, 다음 이동에서 벡터를 지움)
            // = 마지막 형상 블록 끝 노즈 e + nA·r 에서 후퇴점 − 팁 벡터로 꺾인 급송. G40 I K(K = 끝 접선) 도 같은 자리(FF 그림 5.4.4 (i))
            val open = sim(lines.dropLast(1), r, s.tip)
            val oms = moves(open)
            val oshape = oms.filter { it.line in (iStart + 1) until iCancel }
            if (open.stop != null || oshape.isEmpty()) continue
            val oEnd = oshape.last().nose.end
            val tv = NoseComp.tipVector(s.tip, r)
            val retract = dogleg(oEnd, cancelCmd.end - tv)
            val fixN = oshape.map { it.nose } + retract
            val fixGouge = retract.maxOf { depth(it, s.drawing.segs, r) }
            val fixGougeLin = depth(Line(oEnd, cancelCmd.end - tv), s.drawing.segs, r)
            if (fixGouge > tol) println("  fix retract gouge: dogleg ${f4(fixGouge)} straight ${f4(fixGougeLin)} | ${shapeKey(tag)} | ${lines.joinToString(" / ")}")
            rows += EndRow(
                shapeKey(tag) + (if (innerCorner) " | cancel=inner" else " | cancel=outer/tangent"), d / r, innerCorner,
                left(simN), left(dirN), beyond(simN), beyond(dirN), left(fixN), beyond(fixN), fixGouge, lines.joinToString(" / "), (tv dot tA) / r,
            )
        }
        println("end diff (G41/G42 sim end nose vs calc nose path end > 0.001): ${rows.size} of $cases solved cases (sim stops $stops)")
        println("  calcREV (calc reverse=true, the screen never sends it): ${rows.count { it.key.startsWith("calcREV") }}, screen-reachable fwd: ${rows.count { !it.key.startsWith("calcREV") }}")
        println("  of which G41/G42 leaves more at end vertex than direct (>0.001): ${rows.count { it.simLeft > it.dirLeft + tol }}" +
            " (screen-reachable ${rows.count { !it.key.startsWith("calcREV") && it.simLeft > it.dirLeft + tol }})")
        println("  of which G41/G42 nose goes farther past end coordinate than direct (>0.001): ${rows.count { it.simBeyond > it.dirBeyond + tol }}")
        val lo = rows.filter { it.simLeft > it.dirLeft + tol }
        for ((name, set) in listOf("all leftover" to lo, "screen-reachable leftover" to lo.filter { !it.key.startsWith("calcREV") })) {
            println(
                "  $name ${set.size}: sim left ${f4(set.minOfOrNull { it.simLeft } ?: 0.0)}~${f4(set.maxOfOrNull { it.simLeft } ?: 0.0)} (>=0.1: ${set.count { it.simLeft >= 0.1 }}), " +
                    "cancel inner ${set.count { it.inner }}; fix 'G40 alone' left > direct ${set.count { it.fixLeft > it.dirLeft + tol }}, " +
                    "past end > direct ${set.count { it.fixBeyond > it.dirBeyond + tol }}, retract gouge ${set.count { it.fixGouge > tol }} (max ${f4(set.maxOfOrNull { it.fixGouge } ?: 0.0)})",
            )
        }
        println("  tv.tA/r < 0 rows ${rows.count { it.tvT < -1e-6 }} (leftover among them ${rows.count { it.tvT < -1e-6 && it.simLeft > it.dirLeft + tol }}); " +
            "leftover rows with tv.tA/r >= 0: ${lo.count { it.tvT >= -1e-6 }} (max left ${f4(lo.filter { it.tvT >= -1e-6 }.maxOfOrNull { it.simLeft } ?: 0.0)})")
        println("  leftover >= 0.01 by tv.tA/r: " + lo.filter { it.simLeft >= 0.01 }.groupBy { f4(it.tvT) }.mapValues { it.value.size })
        for (x in lo.filter { !it.key.startsWith("calcREV") && it.fixBeyond > it.dirBeyond + tol }.take(4)) println("  fix past-end row: ${x.key} left ${f4(x.simLeft)} tv.tA/r ${f4(x.tvT)} | ${x.nc}")
        for ((k, v) in rows.groupBy { it.key }.toSortedMap()) {
            println(
                "  ${v.size} $k: tv.tA/r ${f4(v.minOf { it.tvT })}~${f4(v.maxOf { it.tvT })}; diff ${f4(v.minOf { it.ratio })}r~${f4(v.maxOf { it.ratio })}r; vertex left sim ${f4(v.minOf { it.simLeft })}~${f4(v.maxOf { it.simLeft })} " +
                    "direct ${f4(v.minOf { it.dirLeft })}~${f4(v.maxOf { it.dirLeft })} fix ${f4(v.minOf { it.fixLeft })}~${f4(v.maxOf { it.fixLeft })}; " +
                    "past-end sim ${f4(v.maxOf { it.simBeyond })} direct ${f4(v.maxOf { it.dirBeyond })} fix ${f4(v.maxOf { it.fixBeyond })} | e.g. ${v.maxBy { it.simLeft - it.dirLeft }.nc}",
            )
        }
    }

    // ================================================================ 3. 파고듦(문제 2) 갈래 — 블록 · 급송 모양 · 두 모형

    @Test
    fun gougeClassification() {
        data class G(val key: String, val depth: Double, val lin: Double, val ref: Double, val warned: Boolean, val nc: String)
        val rows = mutableListOf<G>()
        for ((tag, r, res) in taperCases()) {
            val s = res.solution ?: continue
            val lines = s.ncG41G42
            val draw = s.drawing.segs
            fun deepest(run: SimRun): Pair<Double, MoveStep>? {
                val ms = moves(run)
                val first = ms.firstOrNull { it.line > pre.size }?.line ?: return null
                return ms.filter { it.line > first }.map { depth(it.nose, draw, r) to it }.maxByOrNull { it.first }
            }
            val run = sim(lines, r, s.tip)
            if (run.stop != null) continue
            val (dd, m) = deepest(run)?.takeIf { it.first > tol } ?: continue
            // 직선 급송으로 돌린 같은 블록의 깊이(SimVerifyTest 의 linearToo 는 글 전체에서 가장 깊은 것)
            val lin = moves(sim(lines, r, s.tip, RapidPath.LINEAR)).filter { it.line == m.line }.maxOfOrNull { depth(it.nose, draw, r) } ?: 0.0
            val text = (pre + lines)[m.line - 1]
            val kind = when {
                "G41" in text || "G42" in text -> "start"
                "G40" in text -> "cancel"
                m.kind == MoveKind.RAPID -> "G00(G40 state)"
                else -> "shape"
            }
            // RefG4x 의 같은 블록(시작 = 첫 조각, 취소 = 마지막 조각)
            val rf = ref(lines, r, s.tip)
            val refD = when {
                rf == null -> Double.NaN
                kind == "start" -> depth(rf.first(), draw, r)
                kind == "cancel" -> depth(rf.last(), draw, r)
                else -> rf.maxOf { depth(it, draw, r) }
            }
            rows += G("${shapeKey(tag)} | $kind ${m.kind}", dd, lin, refD, TaperWarning.AGAINST_TOOL in res.warnings, lines.joinToString(" / "))
        }
        println("G41/G42 gouge (> 0.001): ${rows.size}; calcREV ${rows.count { it.key.startsWith("calcREV") }}, screen-reachable fwd ${rows.count { !it.key.startsWith("calcREV") }}")
        println("  AGAINST_TOOL warned ${rows.count { it.warned }}, same block with linear rapid still gouges ${rows.count { it.lin > tol }}, RefG4x (straight G00) gouges same block ${rows.count { it.ref > tol }}")
        for (kind in listOf("start", "cancel")) for (reach in listOf(true, false)) {
            val v = rows.filter { it.key.contains("| $kind") && it.key.startsWith("calcREV") != reach }
            if (v.isEmpty()) continue
            println(
                "  $kind ${if (reach) "screen-reachable" else "calcREV"}: ${v.size}, depth max ${f4(v.maxOf { it.depth })}, warned ${v.count { it.warned }}, " +
                    "linear same block > 0.001: ${v.count { it.lin > tol }} (max ${f4(v.maxOf { it.lin })}), RefG4x > 0.001: ${v.count { it.ref > tol }}; " +
                    "unwarned ${v.count { !it.warned }} (max ${f4(v.filter { !it.warned }.maxOfOrNull { it.depth } ?: 0.0)})",
            )
        }
        for ((k, v) in rows.groupBy { it.key }.toSortedMap()) {
            val w = v.maxBy { it.depth }
            println(
                "  ${v.size} $k: depth ${f4(v.minOf { it.depth })}~${f4(w.depth)}, linear ${f4(v.maxOf { it.lin })}, RefG4x ${f4(v.maxOf { if (it.ref.isNaN()) -1.0 else it.ref })}, " +
                    "warned ${v.count { it.warned }}/${v.size} | worst ${w.nc}",
            )
        }
    }

    // ================================================================ 4. 라운드 안내(문제 3) — 첫 G01 에 G42 · 고칠 시작 블록 후보

    @Test
    fun roundStart() {
        val r = 0.4
        val i = RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0, noseR = r)
        val s = RoundCalc.calc(i).solution!!
        val dr = RoundCalc.calc(i.copy(noseR = 0.0)).solution!!
        val draw = s.drawing.segs
        val p0 = draw.first().start
        println("==== round TYPE1 SR15 L20 CR1 r0.4: side=${s.side} tip=${s.tip} p0=${c(p0)} first seg ${draw.first()::class.simpleName} ${c(draw.first().start)}->${c(draw.first().end)}")
        println("  direct NC: " + s.nc.joinToString(" / "))
        println("  drawn  NC: " + dr.nc.joinToString(" / "))
        val k = dr.nc.indexOfFirst { it.startsWith("G01") }
        val body = dr.nc.drop(k + 1)
        val last = dr.drawing.segs.last { lenOf(it) > 1e-9 }
        val pe = last.end + tanEnd(last) * 2.0
        val cancel = "G40 G01 X${Fmt.nc(pe.xDia)} Z${Fmt.nc(pe.z)}"
        val variants = listOf(
            "guide now: G42 on first G01" to (dr.nc.take(k) + "${s.side} ${dr.nc[k]}" + body + cancel),
            "start collinear with first element (from below axis, +X)" to (listOf("G00 X-1.6 Z0.", "${s.side} G01 X0.") + body + cancel),
            "start perpendicular onto lead r beyond axis (X-2r), then along" to (listOf("G00 X-0.8 Z1.", "${s.side} G01 Z0.", "G01 X0.") + body + cancel),
        )
        for ((name, nc) in variants) {
            val run = sim(nc, r, s.tip)
            val ms = moves(run)
            val feeds = ms.filter { it.kind == MoveKind.FEED }
            val gap = if (feeds.isEmpty()) Double.NaN else feeds.flatMap { pts(it.nose) }.minOf { (it - p0).length } - r
            val first = ms.firstOrNull { it.line > pre.size }?.line ?: 0
            val gouge = ms.filter { it.line > first }.maxOfOrNull { depth(it.nose, draw, r) } ?: 0.0
            println("  [$name] stop=${run.stop?.code} gap at p0 ${f4(gap)} max gouge ${f4(gouge)}")
            println("    NC: " + nc.take(5).joinToString(" / ") + " ...")
            for (m in ms.filter { it.line > first }.take(3)) println("    [${(pre + nc)[m.line - 1]}] nose ${c(m.nose.start)} -> ${c(m.nose.end)}")
            val rf = ref(nc.drop(0), r, s.tip)
            if (rf != null) println("    RefG4x start piece ${c(rf.first().start)} -> ${c(rf.first().end)}; gap ${f4(rf.flatMap { pts(it) }.minOf { (it - p0).length } - r)}")
        }
        // 3점호: 안내대로 첫 G01 에 G42 — 첫 도면점 남김 · 시작 블록과 첫 원호 시작 접선의 모서리
        for (a in listOf(
            Arc3Input(false, 0.4, null, 40.0, 0.0, 46.0, -3.0, 40.0, -6.0), Arc3Input(false, 0.4, null, 40.0, 0.0, 35.0, -3.0, 40.0, -6.0),
            Arc3Input(false, 0.4, null, 20.0, 0.0, 30.0, -5.0, 36.0, -12.0),
        )) {
            val sa = RoundCalc.arc3(a).solution ?: continue
            val da = RoundCalc.arc3(a.copy(noseR = 0.0)).solution ?: continue
            val kk = da.nc.indexOfFirst { it.startsWith("G01") }
            val la = da.drawing.segs.last { lenOf(it) > 1e-9 }
            val pa = la.end + tanEnd(la) * 2.0
            val nc = da.nc.take(kk) + "${sa.side} ${da.nc[kk]}" + da.nc.drop(kk + 1) + "G40 G01 X${Fmt.nc(pa.xDia)} Z${Fmt.nc(pa.z)}"
            val q0 = sa.drawing.segs.first().start
            val run = sim(nc, a.noseR, sa.tip)
            val ms = moves(run)
            val gap = ms.filter { it.kind == MoveKind.FEED }.flatMap { pts(it.nose) }.minOf { (it - q0).length } - a.noseR
            println("==== arc3 $a: side=${sa.side} tip=${sa.tip} gap at first point ${f4(gap)} stop=${run.stop?.code}")
            println("    NC: " + nc.joinToString(" / "))
            val first = ms.firstOrNull { it.line > pre.size }?.line ?: 0
            for (m in ms.filter { it.line > first }.take(2)) println("    [${(pre + nc)[m.line - 1]}] nose ${c(m.nose.start)} -> ${c(m.nose.end)}")
            println("    direct NC: " + sa.nc.take(3).joinToString(" / "))
        }
    }

    // ================================================================ 5. 계산기 역방향 사례가 화면(정방향 · 팁 직접 고름)으로도 나오나

    @Test
    fun calcReverseVsScreenCustomTip() {
        var same = 0
        var diff = 0
        val ex = mutableListOf<String>()
        for (inner in listOf(false, true)) for (type in 1..5) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 0.8)) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = Fmt.nc(60 + up * 2 * 6 * tan(deg * PI / 180)).toDouble()
                val inp = TaperInput(inner, type, true, r, null, 60.0, 0.0, x2, -6.0, cornerR1 = r1, cornerR2 = r2)
                val right = if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                val left = if (type == 2 || type == 4) inp.copy(end = Fmt.nc(x2 + up * 12).toDouble()) else inp.copy(end = -12.0)
                for (i in listOf(inp, right, left).flatMap { listOf(it, it.flipped()) }) {
                    // 같은 가공 방향 · 같은 바이트를 화면이 보내는 꼴(계산기 정방향, 뒤집기 반대, 팁 = 역바이트 팁을 사용자가 고름)
                    val j = i.flipped().copy(reverse = false, tip = NoseComp.standardTip(inner, true))
                    val a = TaperCalc.calc(i).solution ?: continue
                    val b = TaperCalc.calc(j).solution ?: continue
                    if (a.ncG41G42 == b.ncG41G42 && a.ncG40 == b.ncG40) same++ else {
                        diff++
                        if (ex.size < 3) ex += "$i | calcREV: ${a.ncG41G42.joinToString(" / ")} | screen: ${b.ncG41G42.joinToString(" / ")}"
                    }
                }
            }
        }
        println("calc reverse=true cases vs screen equivalent (reverse=false, flip toggled, user tip = reverse tip): same NC $same, different $diff")
        for (e in ex) println("  diff e.g. $e")
        // 재현 A · B 를 화면 꼴(정방향, 뒤집기 끔, 팁 1 · 4 직접)로
        val a2 = TaperCalc.calc(TaperInput(true, 1, false, 0.8, 1, 60.0, 0.0, 48.0, -6.0))
        val b2 = TaperCalc.calc(TaperInput(false, 1, false, 0.8, 4, 60.0, 0.0, 92.97, -6.0))
        println("  screen ID TYPE1 tip1: ${a2.warnings} ${a2.solution?.ncG41G42?.joinToString(" / ")} == repA ${a2.solution?.ncG41G42?.joinToString(" / ") == repA}")
        println("  screen OD TYPE1 tip4: ${b2.warnings} ${b2.solution?.ncG41G42?.joinToString(" / ")} == repB ${b2.solution?.ncG41G42?.joinToString(" / ") == repB}")
    }
}
