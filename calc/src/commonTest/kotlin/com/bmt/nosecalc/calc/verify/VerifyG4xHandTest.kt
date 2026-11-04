package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.CompPoint
import com.bmt.nosecalc.calc.CompWarning
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.CornerArc
import com.bmt.nosecalc.calc.Corners
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.EPS
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NcLines
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperPoint
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.distTo
import com.bmt.nosecalc.calc.legacy
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.tooBig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-10-29) — simulator-design.md 13.7 고침(TaperCalc.ncG41G42 끝 · 시작 · 후퇴, 이동 없는 G40, 라운드 안내)을
 * 손 계산 · FANUC 설명서(FF B-64604EN-1/01 5.4.2 · 5.4.4 · 5.4.6 · 부록 A No.1401, F0 B-61394E/08 14.3) · 사용자 결정으로 본다.
 * 2026-11-04: G41/G42 글이 회사 꼴(계획 단계 4 — 보정 혼자 줄 · Z20. 진입 · F.1 · F.3 빼는 줄 · 이동 없는 G40 · G0Z200.)이 되어 같은 사례를 새 꼴의
 * 손 계산으로 다시 세웠다(HEAD 글의 파고듦 · 남김 손 계산은 그대로 — 고치기 전 기록). 라운드 안내는 없어지고 계산기가 회사 꼴 글을 낸다.
 * - [handNumbers]: 재현 글 둘과 대표 사례(뒤집기 외경 · 내경 TYPE 3 끝점 넣음, 계산기 역방향 + 뒤집기 TYPE 2 의 0.508)를 손으로 푼 NC 글 · 노즈 자리와
 *   고친 계산기 · GSim 을 맞춘다. 고치기 전(HEAD) 글의 파고듦 · 남김도 손으로 다시 낸다.
 * - [sweepUserDecisions]: 넓은 사례(팁 0~8, r 0.4 · 0.8, Z 여유 0 포함)에서 직접 보정 글 · 보정점 불변(HEAD 사본 [TaperCalcHead] 와 맞춤),
 *   끝좌표 너머 · 취소 뒤 G00, 파고듦(꺾인 · 직선 급송), 끝 꼭짓점 남김, 형식 C(SUV = 1) 기계의 이동 없는 G40, 길이 0 보정 블록,
 *   부품 앞 끝면 뒤 G00 을 센다. 숫자는 출력, 단언은 지켜져야 하는 것만.
 * - [roundGuide]: 라운드 기계 보정 글(회사 꼴)의 시작 · 끝을 손 계산과 GSim(형식 A · B / C)으로.
 */
class VerifyG4xHandTest {
    private val pre = listOf("T0101", "G97 S1000 M03", "G99 F0.2")
    private val tol = 1e-3

    private fun sim(nc: List<String>, r: Double, tip: Int, rapid: RapidPath = RapidPath.DOGLEG): SimRun =
        GSim.run(SimInput((pre + nc).joinToString("\n"), MachineSetup(rapidPath = rapid, compStartup = CompStartup.AB), mapOf(1 to ToolOffset(r, tip))))

    private fun moves(run: SimRun) = run.steps.filterIsInstance<MoveStep>().filter { it.line > pre.size }

    /** nc 의 k 번 줄(0 부터)의 노즈 처음 · 끝 — 그 줄에 이동이 없으면 null */
    private fun nose(run: SimRun, k: Int): Pair<Pt, Pt>? {
        val ms = moves(run).filter { it.line == pre.size + k + 1 }
        return if (ms.isEmpty()) null else ms.first().nose.start to ms.last().nose.end
    }

    private fun f4(v: Double) = ((v * 10000).roundToLong() / 10000.0).toString()
    private fun c(p: Pt) = "(Z${f4(p.z)} X${f4(p.xDia)})"
    private fun at(p: Pt) = "X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)}"

    private fun near(want: Pt, got: Pt?, what: String, t: Double = tol) {
        assertTrue(got != null && (got - want).length <= t, "$what: 손 ${c(want)} · GSim ${got?.let { c(it) }}")
    }

    private fun pts(s: Seg, step: Double = 0.02): List<Pt> = when (s) {
        is Line -> {
            val n = max(4, ceil((s.end - s.start).length / step).toInt())
            (0..n).map { s.start + (s.end - s.start) * (it.toDouble() / n) }
        }
        is Arc -> {
            val rr = (s.start - s.center).length
            val a0 = atan2(s.start.x - s.center.x, s.start.z - s.center.z)
            var a1 = atan2(s.end.x - s.center.x, s.end.z - s.center.z)
            if (s.cw) { while (a1 > a0) a1 -= 2 * PI } else { while (a1 < a0) a1 += 2 * PI }
            val n = max(4, ceil(abs(a1 - a0) * rr / step).toInt())
            (0..n).map { val a = a0 + (a1 - a0) * it / n; Pt(s.center.z + rr * cos(a), s.center.x + rr * sin(a)) }
        }
    }

    private fun segDist(a: Pt, b: Pt, p: Pt) = distTo(Line(a, b), p)

    /** 두 직선 p + u·s, q + v·t 의 교점 */
    private fun meet(p: Pt, u: Pt, q: Pt, v: Pt): Pt = p + u * (((q - p) cross v) / (u cross v))

    /** FANUC 비직선 급송(LRP = 0 — 축마다 따로, GSim 처음 값 X 반경 20 000 · Z 24 000 mm/min)의 꺾임점 */
    private fun doglegCorner(a: Pt, b: Pt): Pt {
        val tz = abs(b.z - a.z) / 24000.0
        val tx = abs(b.x - a.x) / 20000.0
        return if (tz <= tx) Pt(b.z, a.x + (b.x - a.x) * (tz / tx)) else Pt(a.z + (b.z - a.z) * (tx / tz), b.x)
    }

    /** 노즈 경로(nc 의 [from] 번 줄부터 — 기계 자리에서 첫 G00 으로 오는 이동은 뺌)가 도면에 r 보다 가까이 간 가장 깊은 값과 그 줄 */
    private fun gouge(run: SimRun, draw: List<Seg>, r: Double, from: Int): Pair<Double, Int> {
        var best = -1.0
        var line = -1
        for (m in moves(run)) {
            val k = m.line - pre.size - 1
            if (k < from) continue
            for (p in pts(m.nose)) {
                val d = r - draw.minOf { distTo(it, p) }
                if (d > best) {
                    best = d
                    line = k
                }
            }
        }
        return best to line
    }

    /** 노즈 경로([from] 줄부터)가 점 v 에 가장 가까이 간 거리 − r = v 에 남는 살 */
    private fun leftAt(run: SimRun, v: Pt, r: Double, from: Int) =
        moves(run).filter { it.line - pre.size - 1 >= from }.flatMap { pts(it.nose, 0.005) }.minOf { (it - v).length } - r

    private val xw = Regex("""X(-?[0-9.]+)""")
    private val zw = Regex("""Z(-?[0-9.]+)""")

    /** 줄마다 그 줄 뒤 프로그램 점(도면 좌표 · 모달) — 주석 줄은 앞 점 그대로, 처음은 null */
    private fun progPts(nc: List<String>): List<Pt?> {
        var x: Double? = null
        var z: Double? = null
        return nc.map { l ->
            if (!l.startsWith("(")) {
                xw.find(l)?.let { x = it.groupValues[1].toDouble() / 2 }
                zw.find(l)?.let { z = it.groupValues[1].toDouble() }
            }
            if (x != null && z != null) Pt(z!!, x!!) else null
        }
    }

    private fun TaperInput.flipped() =
        copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start, flip = !flip)

    private fun DoubleTaperInput.flipped() =
        copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start, flip = !flip)

    private fun right(u: Pt) = u.left() * -1.0

    private val shapeRe = Regex("""inner=(\w+), type=(\d).*start=([^,]+), end=([^,]+), flip=(\w+)""")

    /** 사례 글에서 모양 이름(외경 · 내경, TYPE, 뒤집기, 시작 · 끝점 넣음) */
    private fun shapeKey(tag: String): String {
        val m = shapeRe.find(tag)?.groupValues ?: return "겹테이퍼" + (if ("flip=true" in tag) " 뒤집기" else "")
        return "${if (m[1] == "true") "내경" else "외경"} TYPE${m[2]}${if (m[5] == "true") " 뒤집기" else ""} 칸 시작${if (m[3] == "null") " 비움" else " 넣음"} · 칸 끝${if (m[4] == "null") " 비움" else " 넣음"}"
    }

    // ================================================================ 1. 손 계산

    /** 회사 꼴 좌표 낱말(X 직경 · Z) */
    private fun xzw(p: Pt) = "X${Fmt.word(p.xDia)}Z${Fmt.word(p.z)}"

    /** G41/G42 글을 형식 A · B 와 C 로 — 깎는 줄 노즈는 형식과 상관없다(회사 꼴, 보정 줄이 이동 없는 혼자 줄) */
    private fun forms(nc: List<String>, r: Double, tip: Int, rapid: RapidPath = RapidPath.DOGLEG): List<SimRun> = CompStartup.values().map { f ->
        GSim.run(SimInput((pre + nc).joinToString("\n"), MachineSetup(rapidPath = rapid, compStartup = f), mapOf(1 to ToolOffset(r, tip))))
    }

    @Test
    fun handNumbers() {
        val s2 = sqrt(0.5)
        val c = 1.0 // Z 여유 1 > r

        // ---- 재현 A: 내경 형상 1 X60 Z0 / X48 Z-6, 0.8R, 팁 1(역보링바) — 화면 정방향 + 팁 직접 고름 · 계산기 역방향 + 뒤집기가 같은 글
        run {
            val r = 0.8
            val tv = NoseComp.tipVector(1, r)
            assertEquals(Pt(r, r), tv)
            val e1 = Pt(0.0, 30.0)
            val e2 = Pt(-6.0, 24.0)
            val uS = Pt(-s2, -s2)
            val nS = uS.left() // G41 = 진행 왼쪽(구멍 안쪽 · 앞)
            // 회사 꼴(계획 단계 4): T1 볼록 → 진입점 = 경사 연장선을 Z 로 c(Z1 X62). 첫 깎는 줄이 −Z 라 Z20. 에서 Z 줄로 곧장 — Z 줄 노즈(G41 · −Z 는 −X 쪽)
            // 반경 30.2 는 Z1 둘레에 도면이 없어 된다. 끝(비움, 볼록 T2)은 경사를 큰 축으로 c 늘인 X46 Z-7, G40 자리 (1 + √2)·r + 0.02 = 1.951 이 구멍(반경 24 —
            // 비운 끝 요소의 반직선)에서 1 이라 −X 로 0.951 더(반경 22.049)
            val room = (1 + sqrt(2.0)) * r + 0.02
            val e0 = e1 + (e1 - e2) * (c / 6.0)
            val x1 = e2 + (e2 - e1) * (c / 6.0)
            val q = Pt(x1.z, 24.0 - room)
            val want = listOf("G41", "G0X${Fmt.word(e0.xDia)}Z20.", "Z${Fmt.word(e0.z)}", "G1X60.Z0.F.1", "X48.Z-6.", "${xzw(x1)}F.3", "X${Fmt.word(q.xDia)}", "G40", "G0Z200.")
            assertEquals(listOf("G41", "G0X62.Z20.", "Z1.", "G1X60.Z0.F.1", "X48.Z-6.", "X46.Z-7.F.3", "X44.097", "G40", "G0Z200."), want)
            val inps = listOf(
                TaperInput(inner = true, type = 1, noseR = r, tip = 1, x1 = 60.0, z1 = 0.0, x2 = 48.0, z2 = -6.0),
                TaperInput(true, 1, true, r, null, 48.0, -6.0, 60.0, 0.0, flip = true),
            )
            for (inp in inps) {
                val res = TaperCalc.calc(inp)
                assertTrue(TaperWarning.AGAINST_TOOL in res.warnings, "$inp")
                assertEquals(want, res.solution!!.ncG41G42, "$inp")
            }
            val s = TaperCalc.calc(inps[0]).solution!!
            for (rp in RapidPath.values()) for (run in forms(want, r, 1, rp)) {
                assertNull(run.stop, "${run.stop}")
                // Z 줄 끝(진입점): Z 줄(−Z, 공구 쪽 −X) 보정선 반경 30.2 와 첫 깎는 줄(경사 연장) 보정선의 교점 — 경사 보정선 위
                val pz = e1 + nS * r
                near(pz + uS * ((31.0 - r - pz.x) / uS.x), nose(run, 2)?.second, "재현 A 진입점 노즈(교점)")
                // 첫 깎는 줄 · 끝 블록은 경사와 한 직선(진입 · 빼기)이라 노즈가 T1 · T2 의 경사 법선 — 접점이 그 꼭짓점
                near(e1 + nS * r, nose(run, 3)?.second, "재현 A 첫 깎는 점 노즈")
                near(e2 + nS * r, nose(run, 4)?.second, "재현 A 끝 노즈")
                assertTrue(gouge(run, s.drawing.segs, r, 2).first <= tol, "재현 A 파고듦 ${gouge(run, s.drawing.segs, r, 2)}")
                assertTrue(leftAt(run, e1, r, 2) <= tol && leftAt(run, e2, r, 2) <= tol, "재현 A 꼭짓점 남김")
            }
            // HEAD: G00 X63.2 Z1.6 → 노즈 (Z0.8, X61.6) 에서 같은 교점으로 곧게 — 손: 모서리까지 가장 가까운 곳 t = 2/3, 0.6532 → 0.1468 파고듦
            val hd = r - segDist(Pt(0.8, 30.8), e1 + nS * r, e1)
            assertEquals(0.1468, hd, 2e-4)
            val headNc = TaperCalcHead.calc(inps[0]).solution!!.ncG41G42.legacy
            assertEquals(listOf("(R0.8 T1)", "G00 X63.2 Z1.6", "G41 G01 X60. Z0.", "G01 X48. Z-6.", "G40 G00 X44.869 Z-6.434"), headNc)
            for (rp in RapidPath.values()) assertEquals(hd, gouge(sim(headNc, r, 1, rp), s.drawing.segs, r, 2).first, 1e-3, "HEAD 재현 A $rp")
            println("재현 A 손: 첫 깎는 점 노즈 ${c(e1 + nS * r)} · 끝 노즈 ${c(e2 + nS * r)} · G40 자리 ${c(q)} · HEAD 파고듦 ${f4(hd)}")
        }

        // ---- 재현 B: 외경 형상 1 X60 Z0 / X92.97 Z-6(70°), 0.8R, 팁 4 — 계산기 역방향 + 뒤집기(화면에 없는 조합)
        run {
            val r = 0.8
            val tv = NoseComp.tipVector(4, r)
            val e1 = Pt(0.0, 30.0)
            val e2 = Pt.ofDia(-6.0, 92.97)
            val uS = (e2 - e1).unit()
            val nS = right(uS) // G42
            // 회사 꼴: T1 볼록 → 진입점 = 경사 연장선을 큰 축으로 c — 70° 경사(Z 6 · 반경 16.485)라 반경으로 1: 반경 29(X58), Z 6/16.485 = 0.364
            // (2026-11-04 고침 전에는 Z 로 1 — 반경 30 − 16.485/6). Z 줄로 곧장: G42 · −Z 진행 노즈는 +X 쪽 반경 29.4, 꺾임 노즈 = 진입점 + (a + b)·r/(1 + a·b),
            // a = (0, 1), b = 경사 오른쪽 (0.940, 0.342) → (Z0.924, 반경 29.8) — Z0.924 위로 도면이 없다. 끝(비움, T2 볼록) → 경사를 큰 축(X)으로 c — X94.97 Z-6.364.
            // G40 자리 1.951: 외경 반직선(반경 46.485)에서 1 → +X 로 0.951(반경 48.436 — T2 까지도 1.985)
            val room = (1 + sqrt(2.0)) * r + 0.02
            val e0 = e1 + (e1 - e2) * (c / max(6.0, e2.x - e1.x))
            val x1 = e2 + (e2 - e1) * (c / (e2.x - e1.x))
            val q = Pt(x1.z, e2.x + room)
            val want = listOf("G42", "G0X${Fmt.word(e0.xDia)}Z20.", "Z${Fmt.word(e0.z)}", "G1X60.Z0.F.1", "X92.97Z-6.", "${xzw(x1)}F.3", "X${Fmt.word(q.xDia)}", "G40", "G0Z200.")
            assertEquals(listOf("G42", "G0X58.Z20.", "Z.364", "G1X60.Z0.F.1", "X92.97Z-6.", "X94.97Z-6.364F.3", "X96.873", "G40", "G0Z200."), want)
            val inp = TaperInput(false, 1, true, r, null, 92.97, -6.0, 60.0, 0.0, flip = true)
            val s = TaperCalc.calc(inp).solution!!
            assertEquals(want, s.ncG41G42)
            for (rp in RapidPath.values()) for (run in forms(want, r, 4, rp)) {
                assertNull(run.stop)
                near(e1 + nS * r, nose(run, 3)?.second, "재현 B 첫 깎는 점 노즈(경사 법선)")
                near(e2 + nS * r, nose(run, 4)?.second, "재현 B 끝 노즈(경사 법선)")
                assertTrue(gouge(run, s.drawing.segs, r, 2).first <= tol, "재현 B 파고듦")
            }
            // HEAD 취소 `G40 G00 X99.339 Z-5.799`: 취소 방향이 공구 쪽으로 꺾여(안쪽) 경사 보정선과 취소 보정선의 교점 S 에 서고(FF 그림 5.4.4(a)),
            // 거기서 G40 노즈(지령점 − 팁 벡터)로 축마다 따로 급송 — 첫 토막이 T2(Z-6 X92.97) 모서리를 깎는다
            val pe = Pt.ofDia(-5.799, 99.339)
            val tB = (pe - e2).unit()
            val sP = meet(e1 + nS * r, uS, e2 + right(tB) * r, tB)
            val endN = pe - tv
            val k = doglegCorner(sP, endN)
            val hb = r - min(segDist(sP, k, e2), segDist(k, endN, e2))
            val hbLin = r - segDist(sP, endN, e2)
            val headNc = TaperCalcHead.calc(inp).solution!!.ncG41G42.legacy
            assertEquals("G40 G00 X99.339 Z-5.799", headNc.last())
            val hrun = sim(headNc, r, 4)
            near(sP, nose(hrun, 3)?.second, "HEAD 재현 B 교점 S", 2e-3)
            val headDog = moves(hrun).filter { it.line == pre.size + headNc.size }
            assertEquals(2, headDog.size, "꺾인 급송 두 토막")
            near(k, headDog.first().nose.end, "HEAD 재현 B 꺾임점", 2e-3)
            val g = gouge(hrun, s.drawing.segs, r, 2).first
            assertEquals(hb, g, 2e-3)
            assertTrue(hbLin <= tol, "직선 급송이면 안 파고듦 $hbLin")
            println("재현 B 손: 첫 깎는 점 노즈 ${c(e1 + nS * r)} · 끝 노즈 ${c(e2 + nS * r)} · HEAD S ${c(sP)} 꺾임 ${c(k)} 파고듦 ${f4(hb)}(직선 ${f4(hbLin)}) · GSim ${f4(g)}")
        }

        // ---- 문제 1 대표: 뒤집기 외경 · 내경 형상 3, 끝점 Z6(부품 앞 끝, 너머가 빈 끝) — 사용자 결정 ③: 끝 좌표 너머로 곧게 c
        for ((inner, r) in listOf(false to 0.4, false to 0.8, true to 0.4)) {
            val x2 = if (inner) 55.632 else 64.368
            val inp = TaperInput(inner, 3, false, r, null, 60.0, 0.0, x2, -6.0, start = 6.0).flipped()
            val res = TaperCalc.calc(inp)
            val s = res.solution!!
            val tip = if (inner) 2 else 3
            val side = if (inner) Side.G42 else Side.G41 // 뒤집기 G41/G42 자동: 외경 위→아래 G41, 내경은 반대
            assertEquals(tip, s.tip)
            assertEquals(side, s.side)
            val tv = NoseComp.tipVector(tip, r)
            val e = Pt(6.0, 30.0)
            val uB = Pt(1.0, 0.0)
            val nB = if (side == Side.G41) uB.left() else right(uB)
            assertEquals(-r, tv dot uB, 1e-12)
            // 빈 끝: 끝 블록 `Z6.` 뒤 곧게 c(Z7) — G40 자리 (1 + √2)·r + 0.02 가 끝 꼭짓점에서 c = 1 보다 크면(0.8R: 1.951) 같은 쪽으로 더 간 줄(Z7.951)
            val room = (1 + sqrt(2.0)) * r + 0.02
            val tail = listOf("Z6.", "Z7.F.3") + (if (room > c) listOf("Z${Fmt.word(e.z + room)}") else emptyList()) + listOf("G40", "G0Z200.")
            assertEquals(tail, s.ncG41G42.takeLast(tail.size), "$inp")
            assertEquals("G00 ${if (inner) "X58." else "X62."}", s.ncG40.legacy.last(), "직접 보정 후퇴는 그대로")
            val k = s.ncG41G42.indexOf("Z6.")
            val drun = sim(s.ncG40.legacy, r, tip)
            val dEnd = nose(drun, s.ncG40.legacy.size - 2)!!.second
            near(e - tv, dEnd, "직접 보정 끝 노즈 = 끝좌표 − 팁 벡터")
            for (run in forms(s.ncG41G42, r, tip)) {
                assertNull(run.stop)
                near(e + nB * r, nose(run, k)?.second, "P1 ${if (inner) "내경" else "외경"} r$r 끝 노즈(끝 법선 — 접점 = 끝좌표 Z6)")
                // (G41/G42 끝 노즈) − (직접 보정 끝 노즈) = u·(팁 벡터·u) — 빈 끝은 끝 법선
                near(dEnd + uB * (tv dot uB), nose(run, k)?.second, "끝 차이 u·(tv·u)")
                assertTrue(leftAt(run, e, r, 2) <= tol, "끝 꼭짓점 남김 ${leftAt(run, e, r, 2)}")
                assertTrue(gouge(run, s.drawing.segs, r, 2).first <= tol)
            }
            // HEAD `G40 G00 X62.`: 교점 S = E + nB·r − uB·r, 꺾인 급송으로 G40 노즈 E + nB·c − tv 로 — 끝 꼭짓점 남김(손)
            val sP = e + nB * r - uB * r
            val endN = e + nB * c - tv
            val kk = doglegCorner(sP, endN)
            val hl = min(segDist(sP, kk, e), segDist(kk, endN, e)) - r
            val hlLin = segDist(sP, endN, e) - r
            val headNc = TaperCalcHead.calc(inp).solution!!.ncG41G42.legacy
            assertEquals("G40 G00 ${if (inner) "X58." else "X62."}", headNc.last())
            val hrun = sim(headNc, r, tip)
            near(sP, nose(hrun, headNc.size - 2)?.second, "HEAD 교점")
            assertEquals(hl, leftAt(hrun, e, r, 2), 1e-3)
            println("P1 ${if (inner) "내경" else "외경"} r$r 손: 끝 노즈 ${c(e + nB * r)} · 직접 보정 ${c(dEnd)} · HEAD 교점 ${c(sP)} 남김 ${f4(hl)}(직선 급송 ${f4(hlLin)})")
        }
        // 같은 형상 정방향, 끝점 Z-12(척 쪽, 너머가 벽): 공구 쪽 직각 c(X66.368) — 교점 = 직접 보정 끝 노즈(팁 벡터·u = r). G40 자리는 벽 너머(Z < −12)에 붙어 +Z 로 0.986
        run {
            val r = 0.4
            val s = TaperCalc.calc(TaperInput(false, 3, false, r, null, 60.0, 0.0, 64.368, -6.0, end = -12.0)).solution!!
            assertEquals(listOf("Z-12.", "X66.368F.3", "Z-11.014", "G40", "G0Z200."), s.ncG41G42.takeLast(5))
            val e = Pt.ofDia(-12.0, 64.368)
            val uB = Pt(-1.0, 0.0)
            val nB = right(uB)
            val tv = NoseComp.tipVector(3, r)
            assertEquals(r, tv dot uB, 1e-12)
            for (run in forms(s.ncG41G42, r, 3)) {
                near(e + nB * r - uB * r, nose(run, s.ncG41G42.indexOf("Z-12."))?.second, "벽 쪽 끝: 교점")
                near(e - tv, e + nB * r - uB * r, "벽 쪽 끝: 교점 = 직접 보정 끝 노즈")
            }
        }

        // ---- 0.508: 외경 형상 2 X60 Z0 / X64.368 Z-6(20°), 0.8R, 팁 4 — 계산기 역방향 + 뒤집기, 시작 · 끝점 비움(어깨 단면을 깎지 않음)
        run {
            val r = 0.8
            val tv = NoseComp.tipVector(4, r)
            val e1 = Pt(0.0, 30.0)
            val e2 = Pt.ofDia(-6.0, 64.368)
            val uS = (e2 - e1).unit()
            val nS = right(uS)
            val uB = Pt(0.0, 1.0) // 어깨 단면을 위로
            val nB = right(uB)
            // 오목 꺾임: 경사 보정선과 어깨 보정선의 교점 = 직접 보정의 마지막 노즈. 회사 꼴은 끝(비움, 오목)을 어깨 선을 따라 c 빼서 제어기가 그 교점을 잡는다.
            // 그 빼는 줄(X66.368)은 c = 1 이 2r 보다 짧아 두 안쪽 꺾임 노즈가 거꾸로 0.36 — 0.41 더(X67.188). G40 자리 1.951: 어깨 선 위라 +Z 로, 경사 보정선 거리
            // d·0.342 + 1·0.94 = 1.951 → d = 2.958(Z-3.042, 빼는 줄을 더 간 0.41 도 그대로)
            val nc0 = meet(e1 + nS * r, uS, e2 + nB * r, uB)
            val room = (1 + sqrt(2.0)) * r + 0.02
            val sn = uS.x // 경사의 X 성분(0.342)
            val d = (room - (-uS.z) * c) / sn
            val want = listOf("G42", "G0X59.272Z20.", "Z1.", "G1X60.Z0.F.1", "X64.368Z-6.", "X67.188F.3", "Z${Fmt.word(e2.z + d)}", "G40", "G0Z200.")
            assertEquals("Z-3.042", want[6])
            val inp = TaperInput(false, 2, true, r, null, 60.0, 0.0, 64.368, -6.0).flipped()
            val s = TaperCalc.calc(inp).solution!!
            assertEquals(want, s.ncG41G42)
            val left = (nc0 - e2).length - r
            assertEquals(0.1766, left, 2e-4) // 오목 110° 꺾임에 0.8R 이 못 들어가 남는 것(직접 보정도 같음)
            for (run in forms(want, r, 4)) {
                assertNull(run.stop)
                near(nc0, nose(run, 4)?.second, "0.508 꺾임 교점")
                assertEquals(left, leftAt(run, e2, r, 2), 1e-3)
            }
            assertEquals(left, leftAt(sim(s.ncG40.legacy, r, 4), e2, r, 1), 1e-3)
            // 같은 끝을 TYPE 4(앞 요소 수평)로: 끝 네 줄 · 남김 같음
            val s4 = TaperCalc.calc(TaperInput(false, 4, true, r, null, 60.0, 0.0, 64.368, -6.0).flipped()).solution!!
            assertEquals(want.takeLast(4), s4.ncG41G42.takeLast(4))
            assertEquals(left, leftAt(sim(s4.ncG41G42, r, 4), e2, r, 2), 1e-3)
            // HEAD `G40 G00 X66.368 Z-4.4`: 취소가 공구 쪽으로 꺾여 교점 S(경사 중간)에 서고 꺾인 급송 — 남김 0.508(손)
            val pe = Pt.ofDia(-4.4, 66.368)
            val tB = (pe - e2).unit()
            val sP = meet(e1 + nS * r, uS, e2 + right(tB) * r, tB)
            val endN = pe - tv
            val k = doglegCorner(sP, endN)
            val hl = min(segDist(sP, k, e2), segDist(k, endN, e2)) - r
            assertEquals(0.508, hl, 2e-3)
            val headNc = TaperCalcHead.calc(inp).solution!!.ncG41G42.legacy
            assertEquals("G40 G00 X66.368 Z-4.4", headNc.last())
            assertEquals(hl, leftAt(sim(headNc, r, 4), e2, r, 2), 2e-3)
            println("0.508 손: 꺾임 교점 ${c(nc0)} · 남김 ${f4(left)} = 직접 보정 · HEAD 교점 ${c(sP)} 꺾임 ${c(k)} 남김 ${f4(hl)}")
        }

        // ---- 너머가 빈 단면 시작: 뒤집기 외경 형상 2(칸 T1 X50 Z-5 · T2 X40 Z0), 시작 X60 · 끝 X30, 0.8R T3. F = (Z-5, 반경 30), u = −X, n = +Z
        // 회사 꼴: 진입점 = 단면 연장선 X62 Z-5, 첫 깎는 줄은 단면과 한 직선이라 노즈가 F 의 법선(Z-4.2, 반경 30) — 접점 = F 라 어깨 위 끝에 살이 안 남는다
        // (직접 보정의 첫 노즈는 인선이 F 라 반경 30.8)
        run {
            val r = 0.8
            val s = TaperCalc.calc(TaperInput(false, 2, false, r, null, 50.0, -5.0, 40.0, 0.0, null, 1.0, start = 60.0, end = 30.0, flip = true)).solution!!
            val g4 = s.ncG41G42
            val k = g4.indexOfFirst { it.endsWith("F.1") }
            assertEquals(listOf("G41", "G0X62.Z20.", "Z-5.", "G1X60.F.1"), g4.take(k + 1))
            val f = Pt(-5.0, 30.0)
            for (run in forms(g4, r, 3)) {
                assertNull(run.stop, "${run.stop}")
                near(Pt(-4.2, 30.0), nose(run, k)?.second, "빈 단면 시작: 첫 깎는 점 노즈 = F 의 법선")
                assertTrue(leftAt(run, f, r, k) <= tol, "첫 꼭짓점 남김 ${leftAt(run, f, r, k)}(HEAD 0.302)")
                assertTrue(gouge(run, s.drawing.segs, r, k).first <= tol)
            }
        }
        // 검증 2 ①: 뒤집기 내경 형상 4, 표준 T2, 0.4R, 시작 단면 0.357 < r — 고치기 전 `G01 X10.417` 에서 COMP_INTERFERENCE
        // 검증 2 ②: 외경 형상 1, 팁 9, 1.0R, T1 모서리 R0.2, 시작 비움 — 띄운 단면 진입 뒤 `G01` 의 보정 이동이 0 이라 R0.2 의 반올림으로 간섭
        for ((inp, tip) in listOf(
            TaperInput(true, 4, false, 0.4, null, 10.417, null, 46.301, -4.505, 77.517, 0.3, start = 9.703, end = null, flip = true) to 2,
            TaperInput(false, 1, false, 1.0, 9, 115.959, -1.751, 299.33, null, 83.599, 0.0, cornerR1 = 0.2, start = null, end = -26.408) to 9,
        )) {
            val s = TaperCalc.calc(inp).solution!!
            for (run in forms(s.ncG41G42, inp.noseR, tip)) {
                assertNull(run.stop, "$inp ${run.stop} ${s.ncG41G42}")
                assertTrue(gouge(run, s.drawing.segs, inp.noseR, 2).first <= tol, "$inp")
            }
        }
        // 막힌 끝: 정방향 외경 형상 3, 팁 6, 1.0R, 끝 Z-7.877(척 쪽), Z 여유 2 — 공구 쪽 직각 c = 2 로 빼서 교점 노즈 (Z-6.877, 반경 21.029 + 1)에서 멈추고
        // (두 안쪽 꺾임 노즈가 같은 점이라 0.05 더 — X46.158), G40 자리는 +Z 로 경사를 2.434 비킬 때까지(Z3.823). 노즈가 Z-6.877 을 넘지 않는다(노즈 끝 = 끝좌표)
        run {
            val r = 1.0
            val s = TaperCalc.calc(TaperInput(false, 3, false, r, 6, 40.66, 4.331, 42.058, 2.033, null, 2.0, end = -7.877)).solution!!
            val g4 = s.ncG41G42
            assertEquals(listOf("Z-7.877", "X46.158F.3", "Z3.823", "G40", "G0Z200."), g4.takeLast(5))
            val k = g4.indexOf("Z-7.877")
            for (run in forms(g4, r, 6)) {
                assertNull(run.stop, "${run.stop}")
                near(Pt(-6.877, 21.029 + r), nose(run, k)?.second, "막힌 끝 교점")
                val far = moves(run).filter { it.line - pre.size - 1 >= k }.flatMap { pts(it.nose) }.minOf { it.z }
                assertTrue(far >= -6.877 - tol, "막힌 끝 너머로 노즈 중심 Z$far")
                assertTrue(gouge(run, s.drawing.segs, r, 2).first <= tol)
            }
        }
    }

    // ================================================================ 2. 넓은 사례 — 사용자 결정 · 현장 위험

    private class Case(val tag: String, val inner: Boolean, val calcRev: Boolean, val tipChosen: Boolean, val r: Double, val new: TaperResult, val head: TaperResult)

    private fun cases(): List<Case> {
        val out = mutableListOf<Case>()
        fun add(i: TaperInput) = out.add(Case(i.toString(), i.inner, i.reverse, i.tip != null, i.noseR, TaperCalc.calc(i), TaperCalcHead.calc(i)))
        fun addD(i: DoubleTaperInput) = out.add(Case(i.toString(), i.inner, i.reverse, i.tip != null, i.noseR, TaperCalc.calcDouble(i), TaperCalcHead.calcDouble(i)))
        val corners = listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (type in 1..5) for (deg in listOf(20.0, 45.0, 70.0)) {
            for (r in listOf(0.4, 0.8)) for ((ci, cr) in corners.withIndex()) for (tip in listOf<Int?>(null, 1, 2, 3, 4, 0, 5, 6, 7, 8)) for (zc in listOf(1.0, 0.0)) {
                if (tip != null && ci % 3 != 0) continue
                if ((tip ?: 1) !in 1..4 && (r != 0.4 || ci != 0)) continue
                if (zc == 0.0 && (tip != null || ci != 0)) continue
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = Fmt.nc(60 + up * 2 * 6 * tan(deg * PI / 180)).toDouble()
                val inp = TaperInput(inner, type, reverse, r, tip, 60.0, 0.0, x2, -6.0, zClear = zc, cornerR1 = cr.first, cornerR2 = cr.second)
                val st = if (type <= 2) 60 - up * 12 else 6.0
                val en = if (type == 2 || type == 4) Fmt.nc(x2 + up * 12).toDouble() else -12.0
                for (i in listOf(inp, inp.copy(start = st), inp.copy(end = en), inp.copy(start = st, end = en)).flatMap { listOf(it, it.flipped()) }) add(i)
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            for (r in listOf(0.4, 0.8)) for (tip in listOf<Int?>(null, 1, 2, 3, 4)) {
                if (tip != null && r != 0.4) continue
                val inp = DoubleTaperInput(inner, reverse, r, tip, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
                for (i in listOf(inp, inp.copy(start = 4.0), inp.copy(end = -14.0), inp.copy(start = 4.0, end = -14.0)).flatMap { listOf(it, it.flipped()) }) addD(i)
            }
        }
        return out
    }

    /** 깎는 도면(깎지 않는 앞뒤 참고 요소 뺌) */
    private fun cutOf(s: TaperSolution): List<Seg> {
        var l = s.drawing.segs
        if (!s.fromStart) l = l.drop(1)
        if (!s.toEnd) l = l.dropLast(1)
        return l.filter { (it.end - it.start).length > 1e-9 }
    }

    private class Tal {
        val n = mutableMapOf<String, Int>()
        val ex = mutableMapOf<String, MutableList<String>>()
        val worst = mutableMapOf<String, Double>()
        fun add(k: String, v: Double = 0.0, e: String? = null) {
            n[k] = (n[k] ?: 0) + 1
            if (v > (worst[k] ?: -1.0)) {
                worst[k] = v
                if (e != null) ex.getOrPut(k) { mutableListOf() }.add(0, e)
            } else if (e != null && (ex[k]?.size ?: 0) < 2) ex.getOrPut(k) { mutableListOf() }.add(e)
        }
        fun print(title: String) {
            println("==== $title")
            for (k in n.keys.sorted()) {
                println("  ${n[k]}  $k${worst[k]?.takeIf { it > 0 }?.let { " (가장 큰 값 ${((it * 10000).roundToLong() / 10000.0)})" } ?: ""}")
                for (e in ex[k].orEmpty().take(2)) println("      예: $e")
            }
        }
    }

    @Test
    fun sweepUserDecisions() {
        val t = Tal()
        var total = 0
        var solved = 0
        for (cs in cases()) {
            total++
            val s = cs.new.solution
            val h = cs.head.solution
            if ((s == null) != (h == null)) t.add("풀림 여부가 HEAD 와 다름", 0.0, "${cs.tag} ${cs.head.warnings} → ${cs.new.warnings}")
            if (s == null || h == null) continue
            solved++
            val screen = !cs.calcRev && !cs.tipChosen
            val grp = if (screen) "화면 표준 팁" else if (cs.calcRev) "계산기 역방향" else "팁 직접 고름"
            val against = TaperWarning.AGAINST_TOOL in cs.new.warnings
            val r = cs.r
            val tag = "${cs.tag.substringBefore(", approach")} | ${s.ncG41G42.legacy.joinToString(" / ")}"
            // --- 사용자 결정: 직접 보정 글 · 보정점 · 진입 · 후퇴 · 도면 그대로
            if (s.ncG40.legacy != h.ncG40.legacy || s.points != h.points || s.entry != h.entry || s.exit != h.exit || s.approach != h.approach ||
                s.drawing != h.drawing || s.center != h.center || s.program != h.program || s.tip != h.tip || s.side != h.side ||
                s.fromStart != h.fromStart || s.toEnd != h.toEnd || s.anglesDeg != h.anglesDeg
            ) t.add("직접 보정 글 · 보정점이 HEAD 와 다름", 0.0, cs.tag)
            // HEAD 뒤에 더한 G41/G42 글만의 경고(2026-11-04 — 짧은 막힌 끝 · 시작을 못 뺌 · 진입 길 못 찾음, 2026-11-05 — 짧은 요소를 뺌 · 빼는 길 못 찾음)는
            // 따로 센다(HEAD 에는 없던 알림이라 견주지 않는다)
            val added = setOf(TaperWarning.SHORT_END_PAST, TaperWarning.ENTRY_NOT_FOUND, TaperWarning.SHORT_END_SKIPPED, TaperWarning.EXIT_NOT_FOUND)
            if (cs.new.warnings - added != cs.head.warnings) t.add("[$grp] 경고가 HEAD 와 다름 ${cs.head.warnings} → ${cs.new.warnings}", 0.0, tag)
            for (w in cs.new.warnings.filter { it in added }) t.add("[$grp] HEAD 뒤에 더한 경고 $w · ${cs.new.warnings}", 0.0, tag)
            // --- 글 모양 분류(회사 꼴: 진입 G0 줄 수 · 빼는 줄 수)
            val g4 = s.ncG41G42.legacy
            val hg = h.ncG41G42.legacy
            val iStart = g4.indexOfFirst { it.endsWith(" F0.1") }
            val iCancel = g4.indexOf("G40")
            val hStart = hg.indexOfFirst { "G41" in it || "G42" in it }
            t.add("[$grp] 글 갈래: 진입 G0 ${iStart - 1}줄 · 빼는 줄 ${iCancel - g4.indexOfFirst { " F0.3" in it }}줄", 0.0, tag)
            // 깎는 줄 ~ G40 사이(첫 깎는 줄 · 형상 · 빼는 줄)에 G00 이 없다
            if (iStart < 0 || iCancel < iStart || g4.subList(iStart, iCancel).any { "G00" in it }) t.add("[$grp] 보정 시작 뒤에 G00", 0.0, tag)
            val prog = progPts(g4)
            val tv = NoseComp.tipVector(s.tip, r)
            val endEl = s.drawing.segs.last()
            val uB = (endEl.end - endEl.start).unit()
            // --- 사용자 결정: 넣은 끝좌표 너머로 가는 프로그램 좌표(도면 좌표) — HEAD 와 견줌
            if (s.toEnd) {
                fun beyond(nc: List<String>, k0: Int) = progPts(nc).drop(k0 + 1).filterNotNull().maxOf { (it - endEl.end) dot uB }
                val bN = beyond(g4, iStart)
                val bH = beyond(hg, hStart)
                if (bN > 5e-4) t.add("[$grp] 끝좌표 너머 프로그램 좌표(${if (bN > bH + 5e-4) "HEAD 보다 더" else "HEAD 와 같음"})", bN, tag)
            }
            // --- GSim
            val run = sim(g4, r, s.tip)
            val hrun = sim(hg, r, s.tip)
            if (run.stop != null) {
                t.add("[$grp] G41/G42 글이 GSim 에서 멈춤 ${run.stop!!.code}${if (hrun.stop != null) " (HEAD 도 ${hrun.stop!!.code})" else " (HEAD 는 안 멈춤)"}", 0.0, tag)
                continue
            }
            val newIssues = run.issues.map { it.code }.toSet() - hrun.issues.map { it.code }.toSet() - setOf(IssueCode.COMP_CANCEL_ALONE)
            if (newIssues.isNotEmpty()) t.add("[$grp] GSim 새 알림 $newIssues", 0.0, tag)
            // 형식 C(회사 기계 SUV = 1): 보정 혼자 줄에서 노즈가 옮겨진다 — 멈춤 · 파고듦 없음(꺾인 급송)
            val runC = forms(g4, r, s.tip)[1]
            if (runC.stop != null) t.add("[$grp] 형식 C 에서 멈춤 ${runC.stop!!.code}", 0.0, tag)
            else gouge(runC, s.drawing.segs, r, 2).let { (g, gl) -> if (g > tol) t.add("[$grp] G41/G42 파고듦 · 형식 C${if (against) " · AGAINST_TOOL" else " · 경고 없음"} · 줄 '${g4[gl]}'", g, tag) }
            val drun = sim(s.ncG40.legacy, r, s.tip)
            val draw = s.drawing.segs
            for (rp in RapidPath.values()) {
                val rr = if (rp == RapidPath.DOGLEG) run else sim(g4, r, s.tip, rp)
                val (g, gl) = gouge(rr, draw, r, 2)
                if (g > tol) t.add("[$grp] G41/G42 파고듦 · ${rp}${if (against) " · AGAINST_TOOL" else " · 경고 없음"} · 줄 '${g4[gl]}'", g, tag)
            }
            val dg = gouge(drun, draw, r, 1).first
            if (dg > tol) t.add("[$grp] 직접 보정 파고듦", dg, tag)
            // 끝 · 첫 꼭짓점 남김: G41/G42 ≤ 직접 보정
            val cut = cutOf(s)
            for ((nm, v) in listOf("끝" to cut.last().end, "첫" to cut.first().start)) {
                val ln = leftAt(run, v, r, 2)
                val ld = leftAt(drun, v, r, 1)
                if (ln > ld + tol) {
                    val hl = if (hrun.stop != null) null else leftAt(hrun, v, r, 2)
                    val same = if (hl == null) "HEAD 멈춤" else if (abs(hl - ln) <= tol) "HEAD 와 같음(이번에 안 바뀜)" else "HEAD ${f4(hl)}"
                    t.add("[$grp] $nm 꼭짓점에 직접 보정보다 살이 더 남음 · $same · ${shapeKey(cs.tag)}", ln - ld, "$tag || 직접 ${s.ncG40.legacy.joinToString(" / ")} || 남김 G41/G42 ${f4(ln)} 직접 ${f4(ld)}")
                }
            }
            // 막힌 끝(척 쪽으로 끝난 수평 · 재료 쪽으로 끝난 단면 — 외경 X0 빼고)은 끝 쪽으로 직접 보정보다 더 가지 않는다(끝 요소 방향의 가장 먼 노즈).
            // 빈 끝 · 비운 끝은 회사 꼴이 끝 좌표 너머로 곧게 빼므로(사용자 결정 ③) 더 가는 것이 맞다 — 출력만
            val face = abs(uB.x) > abs(uB.z)
            val wall = s.toEnd && if (face) (uB.x < 0) != cs.inner && !(!cs.inner && endEl.end.x <= 1e-9) else uB.z < 0
            val farN = moves(run).filter { it.line - pre.size - 1 >= 2 }.flatMap { pts(it.nose) }.maxOf { it dot uB }
            val farD = moves(drun).filter { it.line - pre.size - 1 >= 1 }.flatMap { pts(it.nose) }.maxOf { it dot uB }
            if (farN > farD + tol) t.add("[$grp] 노즈가 직접 보정보다 끝 쪽으로 더 감(${if (wall) "막힌 끝" else if (s.toEnd) "빈 끝" else "끝점 비움"})", farN - farD, tag)
            // --- 접근 급송(시작에서 도면과 0.05 넘게 떨어진 G00 — 첫 G00 뺌)이 깎는 도면에 닿듯 지나감(여유 < 0.01): 다듬질 여유를 급송으로 깎을 수 있음
            fun rapidGap(rn: SimRun) = moves(rn).filter { m ->
                m.line - pre.size - 1 >= 2 && m.kind == com.bmt.nosecalc.calc.sim.MoveKind.RAPID && cut.minOf { distTo(it, m.nose.start) } - r > 0.05
            }.flatMap { pts(it.nose) }.minOfOrNull { p -> cut.minOf { distTo(it, p) } - r }
            val gN = rapidGap(run)
            val gH = if (hrun.stop == null) rapidGap(hrun) else null
            // (회사 꼴의 접근 급송은 보정 중이라 HEAD 의 G40 상태 급송과 견줄 수 없다 — 새로 생겼는지가 아니라 있는지를 본다)
            val zc0 = "zClear=0.0," in cs.tag
            if (gN != null && gN < 0.01) t.add("[$grp] G41/G42 접근 급송이 깎는 도면에 닿듯 지나감${if (zc0) "(Z 여유 0)" else ""} · ${if (gH != null && gH < 0.01) "HEAD 도" else "HEAD 는 없음"} · ${shapeKey(cs.tag)}", 0.01 - gN, tag)
            // --- 보정 중 길이 0 블록(지령 이동 > 0.05 인데 노즈 이동 < 0.001) — FF 5.4.6 방향 검사는 노즈 이동의 방향을 지령과 견준다
            for (k in iStart + 1 until iCancel) {
                if (!g4[k].startsWith("G01")) continue
                val p0 = prog[k - 1] ?: continue
                val p1 = prog[k] ?: continue
                if ((p1 - p0).length < 0.05) continue
                val nm = nose(run, k) ?: continue
                val d = nm.second - nm.first
                if (d.length < tol) {
                    val sg = d dot (p1 - p0).unit()
                    t.add("[$grp] 보정 중 노즈 이동 길이 0 블록 · ${if (sg < -1e-6) "거꾸로(반올림)" else "앞으로 · 0"} · ${if (k >= g4.indexOfFirst { " F0.3" in it }) "빼는 줄" else "형상 줄"}", abs(sg), tag)
                }
            }
            // --- 부품 앞 끝면 뒤(척 쪽)의 첫 G00 — 접근 줄이 없을 때(앞 위치에서 X · Z 가 따로 오면 부품 앞을 지날 수 있음, 검증 2026-10-27 원칙)
            fun behind(nc: List<String>, sol: TaperSolution): Boolean {
                val ps = sol.drawing.segs.flatMap { listOf(it.start, it.end) }.toMutableList()
                if (!sol.fromStart) ps.remove(sol.drawing.segs.first().start)
                if (!sol.toEnd) ps.remove(sol.drawing.segs.last().end)
                val zFront = ps.maxOf { it.z }
                val g00s = nc.drop(1).takeWhile { it.startsWith("G00") }.size
                val p = progPts(nc)[1]!!
                return g00s == 1 && p.z < zFront - 1e-3
            }
            val bN = behind(g4, s)
            val bH = behind(hg, h)
            if (bN) t.add("[$grp] 첫 G00 이 부품 앞 끝면 뒤 · 접근 줄 없음 · ${if (cs.inner) "내경" else "외경"} · ${if (bH) "HEAD 도" else "새로 생김"}${if (against) " · AGAINST_TOOL" else " · 경고 없음"}", 0.0, tag)
        }
        println("사례 $total · 풀림 $solved")
        t.print("넓은 사례 — 고친 TaperCalc 의 G41/G42 글 (GSim 처음 값 = 꺾인 급송)")
        assertTrue(solved > 8000, "풀린 사례 $solved")
        assertTrue(t.n.keys.none { it.startsWith("직접 보정 글 · 보정점") }, "직접 보정 글 · 보정점이 바뀌었다")
        assertTrue(t.n.keys.none { "보정 시작 뒤에 G00" in it }, "취소 · 후퇴에 G00")
        // 13.7 고침이 지키는 것(넓은 사례에서도): G41/G42 글이 도면을 파고들지 않음(꺾인 · 직선 급송), 화면 표준 팁의 끝 꼭짓점 남김 ≤ 직접 보정,
        // 끝점을 넣었으면 노즈가 직접 보정보다 끝 쪽으로 더 가지 않음, 경고 · 풀림 그대로
        assertTrue(t.n.keys.none { "G41/G42 파고듦" in it }, "G41/G42 파고듦")
        assertTrue(t.n.keys.none { it.startsWith("[화면 표준 팁] 끝 꼭짓점") }, "화면 표준 팁 끝 꼭짓점 남김")
        assertTrue(t.n.keys.none { "끝 쪽으로 더 감(막힌 끝)" in it }, "막힌 끝좌표 쪽으로 더 감")
        assertTrue(t.n.keys.none { "경고가 HEAD 와 다름" in it || "풀림 여부" in it }, "경고 · 풀림이 바뀜")
        // 접근 급송이 깎는 도면에 닿듯 지나가지 않는다(회사 꼴의 진입 G0 은 보정 중이라 HEAD 의 G40 상태 급송과 견주지 않고 있는지를 본다).
        // Z 여유 0(진입 거리 = 노즈R)에 단면 바깥 끝에서 시작하는 글(뒤집기 형상 2 · 4 의 어깨 · 계산기 역방향의 앞 단면)은 진입 Z 줄이 단면 연장선을 타고 내려와
        // 꺾임 노즈가 시작 모서리에서 0.002 쯤까지 온다 — 사용자가 고른 최소 여유라 출력만 하고 남은 일로 적는다(진입 거리 c 에서 √((c − r)² + r²) − r)
        assertTrue(t.n.keys.none { "접근 급송" in it && "(Z 여유 0)" !in it }, "접근 급송이 깎는 도면에 닿듯 지나감")
        assertTrue(t.n.keys.none { "형식 C 에서 멈춤" in it }, "형식 C 에서 멈춘 G41/G42 글")
        // 2026-10-29 판정 고침(13.9): 고치기 전(HEAD)에 멈추지 않던 G41/G42 글이 GSim 에서 멈추지 않음 · 화면 표준 팁 첫 G00 이 앞 끝면 뒤에 새로 생기지 않음
        assertTrue(t.n.keys.none { "HEAD 는 안 멈춤" in it }, "새로 멈춘 G41/G42 글")
        assertTrue(t.n.keys.none { it.startsWith("[화면 표준 팁] 첫 G00 이 부품 앞 끝면 뒤") && "새로 생김" in it }, "화면 표준 팁 새 앞 끝면 뒤 G00")
    }

    // ================================================================ 3. 라운드 기계 보정 글

    private class RS(val tag: String, val r: Double, val s: RoundSolution, val direct: com.bmt.nosecalc.calc.RoundResult)

    /**
     * 라운드 기계 보정 글(RoundCalc.machine — 회사 꼴, 계획 단계 4)의 시작 · 끝을 손 계산과 GSim(형식 A · B / C)으로.
     * (2026-10-28~29 에는 화면의 '기계 보정 안내'(첫 점보다 노즈R + 0.01 앞에 직각으로 G42 G01, 끝은 이동 없는 G40 뒤 G01)를 보았다. 안내는 화면이 회사 꼴 글을
     * 직접 내게 되며 없어졌고 — 사용자 결정 2026-10-29 ① — 그 안내의 '원호 뒤 이동 없는 G40' 은 G02/G03 모드 취소라 FANUC 알람이다. 같은 성질 — 첫 점 남김 없음 ·
     * 축에서 시작 · 끝나는 모양의 노즈가 축에 닿음 · 파고듦 없음 — 을 계산기가 낸 글로 본다)
     */
    @Test
    fun roundGuide() {
        // 손: RoundInput(type=1, mainR=15, l1=20, cornerR1=1, noseR=0.4) — 축 위 X0 Z0 에서 단면을 올라가 R1 · SR15 · R1 · 뒤 단면으로 축까지(Z-20)
        val r = 0.4
        val i0 = RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0, noseR = r)
        val s0 = RoundCalc.machine(i0).solution!!
        assertEquals(Side.G42, s0.side)
        assertEquals(3, s0.tip)
        val d = s0.drawing.segs
        val p0 = d.first().start
        val pe = d.last().end
        assertEquals(Pt(0.0, 0.0), p0)
        assertEquals(-20.0, pe.z, 1e-9)
        assertEquals(0.0, pe.x, 1e-9)
        // 진입: 축에서 시작하는 단면(+X)이라 축 너머 c = max(Z 여유 1, r) = 1 에서 그 선과 한 직선으로(X-2 · Z0) — Z20. 에서 Z 줄로 곧장.
        // 첫 깎는 줄 `G1X0.` 은 단면과 한 직선이라 노즈가 X0 의 법선(Z0.4 · 축) — 축에 살이 안 남는다.
        // 끝: 뒤 단면(−X)이 축에서 끝나 X −2r(X-.8)까지 쓰고 공구 쪽(−Z)으로 c(Z-21) — 안쪽 꺾임 교점 노즈 (Z-20.4, 축).
        // G40 자리 Z-21 X-.8 은 뒤 단면에서 1.0 ≥ 0.986, +Z 물러남 띠가 공(가장 큰 반경 15 — Z-10)을 지나야 해 바깥 15 + 0.986 = 반경 15.986(X31.971)으로 한 줄 더
        val room = (1 + sqrt(2.0)) * r + 0.02
        assertEquals(listOf("G42", "G0X-2.Z20.", "Z0.", "G1X0.F.1"), s0.nc.take(4))
        assertEquals(listOf("G1X-.8", "Z-21.F.3", "X${Fmt.word(2 * (15.0 + room))}", "G40", "G0Z200."), s0.nc.takeLast(5))
        assertEquals("X31.971", s0.nc[s0.nc.size - 3])
        // 그 사이 형상 줄은 도면 점(단면 위 끝 → R1 → SR15 → R1 → 뒤 단면 위 끝) — 마지막 X-.8 은 뒤 단면을 축 너머 r 까지
        val mid = progPts(s0.nc).subList(4, s0.nc.size - 5).map { it!! }
        assertEquals(d.size - 1, mid.size)
        for ((k, sg) in d.dropLast(1).withIndex()) near(sg.end, mid[k], "라운드 형상 점 $k", 6e-4)
        for (run in forms(s0.nc, r, s0.tip)) {
            assertNull(run.stop, "${run.stop}")
            // Z 줄(−Z, 공구 쪽 +X) → 첫 깎는 줄(+X, 공구 쪽 +Z) 안쪽 꺾임: 노즈 (Z0.4, 반경 −0.6)
            near(Pt(0.4, -0.6), nose(run, 2)?.second, "라운드 진입점 노즈(교점)")
            near(Pt(0.4, 0.0), nose(run, 3)?.second, "라운드 첫 점 노즈 = 축(법선)")
            near(Pt(-20.4, 0.0), nose(run, 8)?.second, "라운드 뒤 단면 끝 노즈 = 축(교점)")
            assertTrue(leftAt(run, p0, r, 2) <= tol && leftAt(run, pe, r, 2) <= tol, "라운드 축 끝 남김")
            assertTrue(gouge(run, d, r, 2).first <= tol, "라운드 파고듦")
        }

        // 모든 라운드 모양(TYPE 1~6 외경 · 내경, 3점호 — 오목 시작 포함) × 0.4 · 0.8R × 형식 A · B / C: 멈춤 없음, 첫 점 남김 없음, 파고듦 없음
        val shapes = listOf(
            RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0),
            RoundInput(type = 1, mainR = 10.0, l1 = 6.0, cornerR1 = 2.5),
            RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 1.0),
            RoundInput(type = 2, mainR = 6.0, d1 = 10.0, l1 = 3.0, cornerR1 = 1.5),
            RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0),
            RoundInput(type = 3, mainR = 30.0, d1 = 20.0, cornerR1 = 3.0),
            RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0),
            RoundInput(type = 4, mainR = 5.0, d1 = 4.0, cornerR1 = 1.5),
            RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 16.0, l1 = 6.0, cornerR1 = 1.0, cornerR2 = 2.0),
            RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 10.0, l1 = 4.0, cornerR1 = 1.2, cornerR2 = 1.5),
            RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 1.2, cornerR2 = 1.5),
            RoundInput(type = 6, mainR = 15.0, d2 = 50.0, d1 = 10.0, l1 = 3.0, l2 = 20.0, cornerR1 = 1.2, cornerR2 = 1.2),
        )
        val sols = mutableListOf<RS>()
        for (sh in shapes) for (inner in listOf(false, true)) for (rr in listOf(0.4, 0.8)) {
            if (inner && sh.type == 1) continue
            val i = sh.copy(noseR = rr, inner = inner)
            val s = RoundCalc.machine(i).solution ?: continue
            sols += RS(i.toString(), rr, s, RoundCalc.calc(i))
        }
        for (a in listOf(
            Arc3Input(false, 0.4, null, 40.0, 0.0, 46.0, -3.0, 40.0, -6.0),
            Arc3Input(false, 0.4, null, 40.0, 0.0, 35.0, -3.0, 40.0, -6.0),
            Arc3Input(true, 0.4, null, 40.0, 0.0, 45.0, -3.0, 40.0, -6.0),
            Arc3Input(false, 0.4, null, 20.0, 0.0, 30.0, -5.0, 36.0, -12.0),
        )) for (rr in listOf(0.4, 0.8)) {
            val i = a.copy(noseR = rr)
            val s = RoundCalc.machine(i).solution ?: continue
            sols += RS(i.toString(), rr, s, RoundCalc.arc3(i))
        }
        val t = Tal()
        for (rs in sols) {
            val grp = if ("Arc3" in rs.tag) "3점호" else if ("inner=true" in rs.tag) "내경" else "외경"
            val q0 = rs.s.drawing.segs.first { (it.end - it.start).length > 1e-9 }.start
            for ((k, run) in forms(rs.s.nc, rs.r, rs.s.tip).withIndex()) {
                val form = CompStartup.values()[k]
                val st = run.stop
                if (st != null) {
                    if (com.bmt.nosecalc.calc.RoundWarning.CONCAVE_R_LE_NOSE in rs.direct.warnings && st.code == IssueCode.COMP_ARC_SMALL) t.add("[$grp] 오목 R ≤ r 알람 꼴(따로 셈)")
                    else t.add("[$grp] $form GSim 멈춤 ${st.code}", 0.0, "${rs.tag} | ${rs.s.nc.joinToString(" / ")}")
                    continue
                }
                val l = leftAt(run, q0, rs.r, 2)
                if (l > tol) t.add("[$grp] $form 첫 점 남김", l, "${rs.tag} | ${rs.s.nc.joinToString(" / ")}")
                val g = gouge(run, rs.s.drawing.segs, rs.r, 2)
                if (g.first > tol) t.add("[$grp] $form 파고듦 (줄 '${rs.s.nc[g.second]}')", g.first, "${rs.tag} | ${rs.s.nc.joinToString(" / ")}")
                else if (l <= tol) t.add("[$grp] $form 남김 · 파고듦 없음")
            }
        }
        t.print("라운드 기계 보정 글(회사 꼴, 표준 팁)")
        assertTrue(t.n.keys.none { "멈춤" in it || "첫 점 남김" in it || "파고듦 (" in it }, "라운드 기계 보정 글")
        assertTrue(sols.size >= 50, "글 ${sols.size}")
    }
}

// ================================================================ HEAD(972c95e) TaperCalc 그대로 — 이름만 TaperCalcHead · headStop · headGuarded
private object TaperCalcHead {
    /** 노즈R 고르기(직접 입력도 된다) */
    val NOSE_R_CHOICES = listOf(0.2, 0.4, 0.8, 1.2)

    /** 시작·끝점을 비웠을 때 그림에서 T 점에 붙이는 앞뒤 요소의 길이(mm, 단면은 반경). 그 요소는 깎지 않는다 */
    const val LEG_DEFAULT = 5.0

    /** 다섯 값을 다 넣었을 때 각도가 점과 이만큼(°)까지 달라도 받아 준다 — 각도를 소수 셋째 자리로 반올림해 넣어도 되게 */
    private const val ANGLE_TOL = 1e-3

    fun calc(input: TaperInput): TaperResult = headGuarded {
        // 뒤집으면 칸의 T1 이 그림 왼쪽 점이다 — 그림 순서(T1 = 오른쪽)로 바꿔 놓고 풀고, 가공만 반대 끝부터(solve)
        val i = with(input) {
            if (flip) copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start) else this
        }
        require(i.type in 1..5) { "TYPE 은 1~5: ${i.type}" }
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2), i.start, i.end)
        val given = listOf(i.x1, i.z1, i.x2, i.z2, i.angleDeg)
        if (given.count { it == null } > 1) headStop(TaperWarning.MISSING_INPUT)
        if (given.any { it != null && !it.isFinite() }) headStop(TaperWarning.BAD_INPUT)
        val ang = i.angleDeg
        if (ang != null && (ang <= 0 || ang >= 90)) headStop(TaperWarning.BAD_ANGLE)
        // T1 에서 왼쪽 T2 로 갈 때 X 가 커지면 +1: 외경 TYPE 1~4, 내경 TYPE 5
        val up = if (i.inner == (i.type == 5)) 1.0 else -1.0
        val t = ang?.let { tan(it * PI / 180) }
        // 빈 값은 많아야 하나라 차례로 채우면 된다
        val x1 = i.x1 ?: (i.x2!! - up * 2 * leftward(i.z1!!, i.z2!!) * t!!)
        val x2 = i.x2 ?: (x1 + up * 2 * leftward(i.z1!!, i.z2!!) * t!!)
        val z1 = i.z1 ?: (i.z2!! + rise(x1, x2, up) / t!!)
        val z2 = i.z2 ?: (z1 - rise(x1, x2, up) / t!!)
        // 아주 작은 각도로 푼 점도 한도를 넘을 수 있다
        if (x1 <= 0 || x2 <= 0 || tooBig(x1, z1, x2, z2)) headStop(TaperWarning.BAD_INPUT)
        val p1 = Pt.ofDia(z1, x1)
        val p2 = Pt.ofDia(z2, x2)
        val deg = slopeDeg(p1, p2, up)
        if (ang != null && abs(deg - ang) > ANGLE_TOL) headStop(TaperWarning.CONTRADICTION)

        // 그림대로: TYPE 1·2 는 T1 에 단면, TYPE 2·4 는 T2 에 단면, 나머지는 수평. 단면은 경사의 오르내림을 잇는다
        val front = if (i.type <= 2) vertical(p1, -up, i.start) else horizontal(p1, 1.0, i.start)
        val back = if (i.type == 2 || i.type == 4) vertical(p2, up, i.end) else horizontal(p2, -1.0, i.end)
        solve(listOf(front, p1, p2, back), listOf(i.cornerR1, i.cornerR2), i.inner, i.reverse, i.flip, i.noseR, i.tip, i.zClear, i.start, i.end, i.approach)
    }

    fun calcDouble(input: DoubleTaperInput): TaperResult = headGuarded {
        // 뒤집으면 칸의 T1 이 그림 왼쪽 점(T2 는 그대로) — 그림 순서로 바꿔 놓고 푼다
        val i = with(input) {
            if (flip) copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start) else this
        }
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.start, i.end)
        val v = listOf(i.x1, i.z1, i.x2, i.z2, i.x3, i.z3)
        if (v.any { it == null }) headStop(TaperWarning.MISSING_INPUT)
        if (v.any { !it!!.isFinite() } || listOf(i.x1, i.x2, i.x3).any { it!! <= 0 } || tooBig(*v.toTypedArray())) headStop(TaperWarning.BAD_INPUT)
        val p = listOf(Pt.ofDia(i.z1!!, i.x1!!), Pt.ofDia(i.z2!!, i.x2!!), Pt.ofDia(i.z3!!, i.x3!!))
        slopeDeg(p[0], p[1])
        slopeDeg(p[1], p[2])
        val pts = listOf(horizontal(p[0], 1.0, i.start)) + p + horizontal(p[2], -1.0, i.end)
        solve(pts, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.inner, i.reverse, i.flip, i.noseR, i.tip, i.zClear, i.start, i.end, i.approach)
    }

    /**
     * drawn = [시작점, T1, T2, (T3,) 끝점] 그림 오른쪽부터, corners = T 점마다 모서리 R, start · end = 그림의 앞(오른쪽)·뒤 요소 끝.
     * 보정은 [NoseComp] 의 일반 해법 그대로. 앞뒤 요소는 그 바깥 끝 좌표를 넣었을 때만 깎는다.
     * 가공은 역방향(역바이트)과 뒤집기 가운데 하나만 켜졌으면 그림 왼쪽부터(Z+) — 가공 시작 쪽은 그때 end, 아니면 start.
     * 바이트(팁)는 역방향만 따른다(뒤집어도 같은 바이트). 보정 쪽·진입·후퇴·NC 는 모두 가공 순서(path)로 정하므로 뒤집기도 그대로 맞는다
     */
    private fun solve(
        drawn: List<Pt>, corners: List<Double>, inner: Boolean, reverse: Boolean, flip: Boolean, r: Double, tipIn: Int?, zClear: Double,
        start: Double?, end: Double?, approach: Boolean,
    ): TaperResult {
        val leftFirst = reverse != flip
        val cutFirst = (if (leftFirst) end else start) != null
        val cutLast = (if (leftFirst) start else end) != null
        val tip = tipIn ?: NoseComp.standardTip(inner, reverse)
        // G41/G42 = 진행 방향에서 본 공구 쪽. 같은 바이트로 반대 끝부터 깎으면 반대 쪽(외경 형상 1 뒤집기 = G41)
        val side = Side.of(inner, leftFirst)
        val path = if (leftFirst) drawn.reversed() else drawn
        val cr = if (leftFirst) corners.reversed() else corners
        val filleted = Corners.insert(path.zipWithNext { a, b -> Line(a, b) }, cr) ?: headStop(TaperWarning.CORNER_R_TOO_BIG)
        val drawing = Contour(filleted.segs)
        val comp = try {
            NoseComp.tipPath(drawing, r, side, tip)
        } catch (e: IllegalArgumentException) {
            headStop(TaperWarning.NOSE_UNREACHABLE)
        }
        // 요소 하나가 빠지면 도면 모양이 안 나온다. 오목 모서리 R < r(ArcTooSmall)은 r 이 남는 것으로 알린다
        if (comp.warnings.any { it is CompWarning.Unreachable }) headStop(TaperWarning.NOSE_UNREACHABLE)
        val gone = comp.warnings.map { it.index }.toSet()
        val tv = NoseComp.tipVector(tip, r)
        // 팁의 방향만(노즈R 0 이어도 바이트가 어느 쪽을 보는지)
        val tv1 = NoseComp.tipVector(tip, 1.0)
        val segs = comp.path.segs.toMutableList()
        val first = drawing.segs.first()
        val last = drawing.segs.last()
        val uF = (first.end - first.start).unit()
        val uB = (last.end - last.start).unit()
        // 진입·후퇴 거리. G41/G42 시작 이동이 노즈R 보다 짧으면 안 된다
        val c = max(zClear, r)

        // G41/G42 프로그램의 G00 점은 G40 상태라 인선이 그 점에 있다(노즈 중심 = 점 − 팁 벡터). 공구 쪽 법선 n 인 선 위의 점이면
        // 노즈가 선에서 −(n·팁 벡터) 떨어진다. 표준 팁이 제 쪽 요소를 따라가면 r 이상(접하거나 떨어짐)이고, 바이트 반대쪽 요소
        // (AGAINST_TOOL)나 다른 팁이면 r 보다 가까워 선을 넘는다 — 그만큼(lift) 공구 쪽으로 띄워야 한다
        fun lift(n: Pt) = max(0.0, r + (n dot tv))
        // 띄운 G00 점에서 시작 요소 선에 직각으로 들어가 선 위의 lead 로 가면, 그 이동 끝에서 노즈는 교점 규칙대로 lead 보다 r 만큼
        // 선을 따라 앞에 선다. lead 를 그만큼 뒤에 두어 그때의 인선이 직접 보정의 진입점(entry)과 같게 한다
        fun leadFor(entry: Pt) = foot(entry - tv, first.start, uF) - uF * r
        // 꺾임 k(경로 점 path[k])가 날카로운 볼록 모서리인가 — 공구가 재료 모서리 바깥을 돈다
        fun convexAt(k: Int, corner: Int) =
            filleted.arc[corner] == null && (((path[k] - path[k - 1]) cross (path[k + 1] - path[k])) > 0) == (side == Side.G42)

        // 끝. 좌표를 넣었으면 인선(프로그램 점)이 끝점 좌표에 오게 — 끝 요소의 보정선 위에서 끝점에 내린 발. 공구가 끝좌표를 넘지 않는다
        // (Haas 워크북 56쪽 O00055 `N14 Z-2.375`, 53쪽 O00052 처럼). 후퇴: 끝 요소가 수평이면 공구 쪽으로 직각,
        // 단면이면 바깥으로 이어서(직각으로 빼면 G41/G42 제어기가 안쪽 모서리 교점을 잡아 단면 끝 r 을 덜 깎는다) — 다만 단면을
        // 재료 쪽(외경은 중심 쪽, 내경은 바깥)으로 깎아 끝나면(뒤집기·계산기 역방향의 앞 단면) 끝 X 너머도 같은 단면이나 보스라
        // 공구 쪽으로 직각(이어 가면 G00 이 끝 X 너머를 급송으로 깎는다). 그 단면을 중심(X0)까지 깎으면 노즈 중심이 중심선에 닿는
        // 데까지 간 뒤 공구 쪽으로 직각(안 그러면 중심에 살이 남는다 — 위 toAxis).
        // 비우면 그 요소는 깎지 않는다(그림·재료만): 마지막 꺾임점(모서리 R 이면 그 끝)′ 에서 끝 요소의 공구 쪽으로 c 빠진다.
        // G41/G42 는 시작과 거울로 — 날카로운 볼록이면 경사를 그대로 늘여, 아니면 끝 요소 선을 따라 보정을 푼다(안쪽 모서리 교점).
        // 바이트 반대쪽 모양이면 인선이 노즈 중심보다 꺾임 쪽에 있어, 노즈가 꺾임을 벗어나게 경사 쪽은 lift, 끝 요소 쪽은
        // 직접 보정의 마지막 점이 꺾임에서 끝 요소를 따라 간 만큼 더 간다
        val nB = toolNormal(uB, side)
        val exit: Pt
        val exitD: Pt // G41/G42 프로그램의 후퇴점(도면 좌표)
        var endD = last.end // G41/G42 프로그램의 마지막 형상 점(도면 좌표)
        if (cutLast) {
            var e0 = foot(last.end, segs.last().start, uB)
            // 인선이 끝좌표에 왔을 때 노즈가 끝 요소에 한 번도 닿지 않으면(요소가 노즈R 쯤보다 짧다) 그 요소는 깎이지 않는다
            if (((e0 - segs.last().start) dot uB) < -EPS || ((e0 - tv - last.start) dot uB) < -EPS) headStop(TaperWarning.BAD_LEG)
            val face = !isHorizontal(uB)
            val inward = face && (uB.x < 0) != inner
            // 외경 단면을 중심(X0)까지: 노즈 중심이 중심선에 닿아야 중심에 살이 안 남는다. 직접 보정은 인선을 그만큼(T3 이면 X−2r) 더,
            // G41/G42 는 단면을 중심 너머 r(X−2r)까지 쓰고 Z 로 빼며 취소한다 — 안쪽 모서리라 제어기가 교점(노즈 중심 = 중심선)을 잡는다.
            // 너머는 이미 깎은 같은 면이지만 급송은 단면에서 떨어지는 Z 로만(검증 2026-10-27, 현장 안전)
            val toAxis = inward && !inner && last.end.x <= EPS
            if (toAxis) {
                e0 += uB * max(0.0, tv dot uB)
                endD = last.end + uB * r
            }
            segs[segs.lastIndex] = Line(segs.last().start, e0)
            val leave = if (!face || inward) nB else uB
            exit = e0 + leave * c
            exitD = endD + leave * c + nB * lift(nB)
        } else {
            val tl = segs.last().start
            exit = tl + nB * c
            exitD = if (convexAt(path.size - 2, cr.lastIndex)) {
                val ahead = path[path.size - 2] - path[path.size - 3]
                val nS = toolNormal(ahead.unit(), side)
                last.start + ahead * (c / abs(ahead.z)) + nS * lift(nS)
            } else {
                last.start + uB * (c + max(0.0, (tl - last.start) dot uB)) + nB * lift(nB)
            }
        }

        // 시작. 좌표를 넣었으면 끝과 같이 인선을 그 좌표에 두고, 시작 요소가 수평이면 그 연장선, 단면이면 공구 쪽에서 Z 로 들어간다.
        // 다만 뒤집어 수평 시작 요소를 팁의 Z 와 반대로 깎으면(외경·구멍을 척 쪽·구멍 바닥 쪽 끝부터 Z+ 로) 시작 좌표 너머는 날 앞쪽 —
        // 척·구멍 바닥이라 연장선으로 넘어 들어가지 않고 공구 쪽에서 시작 좌표에 직각으로(X 로 c) 들어간다(단면 시작과 같은 꼴).
        // 비우면 그 요소는 깎지 않는다(그림·재료만). 진입은 첫 깎는 점에서 Z 로 c 바깥: 첫 꺾임이 날카로운 볼록이면 경사선을 늘인 점
        // (경사각을 따라가 X 가 정확하다), 아니면 시작 요소가 수평이면 그 보정선을 늘인 점, 단면이면 공구 쪽 점
        val entry: Pt
        var entryD: Pt // G41/G42 프로그램의 진입점(도면 좌표)
        var leadD: Pt? = null // 띄운 진입점에서 직각으로 들어간 뒤 선을 따라 startD 로 갈 점
        val startD: Pt // G41/G42 보정 시작 이동의 끝
        val fromSeg: Int // 직접 보정에서 진입 뒤 따라갈 첫 요소
        var s0: Pt? = null
        val nF = toolNormal(uF, side)
        // 넣은 시작점: 수평 시작 요소의 연장선을 따라 들어가나(뒤집어 팁의 Z 와 반대로 깎는 것은 빼고 — 그때와 단면은 공구 쪽에서 직각으로)
        val along = isHorizontal(uF) && !(flip && uF.z * tv1.z <= 0)
        if (cutFirst) {
            val p = foot(first.start, segs.first().start, uF)
            if (((segs.first().end - p) dot uF) < -EPS || ((first.end - (p - tv)) dot uF) < -EPS) headStop(TaperWarning.BAD_LEG)
            segs[0] = Line(p, segs[0].end)
            val inDir = if (along) uF * -1.0 else nF
            s0 = p
            entry = p + inDir * c
            if (along) {
                entryD = first.start + inDir * c
                if (lift(nF) > EPS) leadD = leadFor(entry).also { entryD = it + nF * (c + lift(nF)) }
            } else {
                entryD = first.start + nF * (c + lift(nF))
            }
            startD = first.start
            fromSeg = 0
        } else {
            if (convexAt(1, 0)) {
                // 경사 연장선은 재료가 아니라(볼록 모서리 바깥) 띄우지 않는다. 다만 바이트 반대쪽 팁(lift > 0)이면 그 G00 점의 노즈가
                // 깎지 않는 시작 요소 선을 넘을 수 있다 — 노즈가 그 선에서 r 떨어질 때까지 연장선을 따라 더 나간다(표준 팁은 그대로)
                val back = path[1] - path[2]
                entry = segs[0].end + back * (c / abs(back.z))
                entryD = path[1] + back * (c / abs(back.z))
                val k = back.unit() dot nF
                if (lift(nF) > EPS && k > EPS && lift(nF) / k > (entryD - path[1]).length) entryD = path[1] + back.unit() * (lift(nF) / k)
            } else if (isHorizontal(uF)) {
                entry = segs[0].end - uF * c
                // 도면 꼭짓점에서 c. 다만 첫 보정점이 꼭짓점보다 뒤에 있으면(뒤집어 인선이 노즈 뒤에 끌려가는 팁 — 노즈 중심이 인선보다
                // 앞) 직접 보정 진입점과 같은 자리까지 물러난다. 아니면 G40 상태의 노즈가 다음 요소(경사)에 박힌다
                entryD = first.end - uF * (c + max(0.0, (first.end - segs[0].end) dot uF))
                if (lift(nF) > EPS) leadD = leadFor(entry).also { entryD = it + nF * (c + lift(nF)) }
            } else {
                // 단면: 공구 쪽에서 Z 로. G41/G42 는 단면 선 위 lead 에 직각으로 들어간 뒤 선을 따라 간다 — 노즈는 교점 규칙대로
                // lead 보다 r 앞, 곧 직접 보정의 첫 깎는 점에 선다
                entry = segs[0].end + nF * c
                leadD = leadFor(segs[0].end).also { entryD = it + nF * (c + lift(nF)) }
            }
            startD = first.end
            fromSeg = 1
        }
        val program = Contour(segs)
        val toSeg = if (cutLast) segs.size else segs.size - 1 // 깎는 요소의 끝(끝 요소를 깎지 않으면 빼고)

        // 접근 줄: 뒤집어 그림 왼쪽(척 쪽 · 구멍 안)에서 시작하면 진입 G00 점이 부품 앞 끝면(넣은 앞 요소 끝이나 T 점 가운데 가장 오른쪽)보다
        // 척 쪽이다. G00 은 X · Z 가 따로 움직일 수 있어(dog-leg) 앞 위치에서 바로 오면 입구 단면 · 면취나 부품 앞을 지날 수 있으므로
        // 먼저 앞 끝면 바깥(Z + c)에서 X 를 맞추고 Z 로만 들어간다(검증 2026-10-27, 현장 안전). 그 X 로 Z 를 따라 오는 동안 노즈가 지나는
        // 도면(진입점에서 노즈 왼쪽 끝부터 오른쪽)보다 바깥이어야 한다 — 아니면(역테이퍼 꼴 · 겹테이퍼 산 너머) 그 바깥 X(+ c)로 Z 를 맞춘 뒤
        // X 로 진입점에 간다. 안 뒤집은 것 · 계산기 역방향은 지금 그대로. 그림의 꺾은선(모서리 R 전)이 모서리 R 윤곽을 바깥에서 감싼다
        val zFront = (if (start != null) drawn else drawn.drop(1)).maxOf { it.z }
        fun approachTo(e: Pt): List<Pt> {
            if (!approach || !flip || reverse || e.z >= zFront - EPS) return emptyList()
            val from = e.z - tv.z - r
            val xs = path.zipWithNext().flatMap { (a, b) ->
                listOfNotNull(
                    a.x.takeIf { a.z >= from }, b.x.takeIf { b.z >= from },
                    if ((a.z - from) * (b.z - from) < 0) a.x + (b.x - a.x) * (from - a.z) / (b.z - a.z) else null,
                )
            }
            // 노즈가 도면 바깥에 있으려면 인선 X 가 이 값 바깥(외경 위 · 내경 아래)
            val lim = if (inner) xs.min() + tv.x - r else xs.max() + tv.x + r
            if (if (inner) e.x <= lim + EPS else e.x >= lim - EPS) return listOf(Pt(zFront + c, e.x))
            val xa = if (inner) lim - c else lim + c
            return listOf(Pt(zFront + c, xa), Pt(e.z, xa))
        }
        val approachG40 = approachTo(entry)
        val approachG4 = approachTo(entryD)

        val warnings = mutableListOf<TaperWarning>()
        // 꺾임 k(경로 점 path[k])가 오목이고 모서리 R < r — 그 자리에 R 대신 r 이 남는다(볼록 끝은 다 깎인다)
        fun leavesR(k: Int) = ((path[k] - path[k - 1]) cross (path[k + 1] - path[k])).let { abs(it) > EPS && (it > 0) != (side == Side.G42) } &&
            cr[k - 1] < r
        val slopes = 1 until path.size - 2 // path[k] – path[k+1] 이 경사(앞뒤 요소 빼고)
        if (slopes.any { r > (path[it + 1] - path[it]).length && (leavesR(it) || leavesR(it + 1)) }) warnings += TaperWarning.NOSE_GT_SLOPE
        // 공구가 원 안쪽(오목): G41 이면 반시계, G42 면 시계 원호
        if (drawing.segs.any { it is Arc && (side == Side.G41) != it.cw && it.radius - r <= EPS }) warnings += TaperWarning.CONCAVE_R_LE_NOSE
        if (tip in 1..4 && againstTool(drawing, drawing.segs.subList(fromSeg, toSeg), side, tip)) warnings += TaperWarning.AGAINST_TOOL
        if ((listOf(entry, exit, entryD, exitD) + approachG40 + approachG4).any { it.x < -EPS }) warnings += TaperWarning.ENTRY_PAST_AXIS

        val ncG40 = NcLines().apply {
            for (p in approachG40) move("G0", p)
            move("G0", entry)
            // 시작점을 넣었으면 시작점′(시작 요소가 수평이면 진입점·시작점′ 이 그 보정선 위라 한 줄로 간다), 비웠으면 첫 꺾임점′
            if (s0 == null) move("G1", segs[0].end) else if (!along) move("G1", s0)
            for (s in segs.subList(fromSeg, toSeg)) if (s is Arc) arc(s) else move("G1", s.end)
            move("G0", exit)
        }.lines
        val ncG41G42 = listOf("(R${Fmt.nc(r).removeSuffix(".")} T$tip)") + NcLines().apply {
            for (p in approachG4) move("G0", p)
            move("G0", entryD)
            // 보정 시작·취소는 직선 이동(G00/G01)에서, 원호 블록은 안 된다 — Haas ST/TL 워크북 62쪽 APPROACH·DEPARTURE MOVES.
            // 시작 이동 끝에서 보정이 다 걸린다(같은 쪽). 시작 이동 길이 ≥ c ≥ r, 취소 이동 ≥ r(66쪽 "twice the tool radius" 는 직경값).
            // 시작 이동은 첫 형상 요소와 한 직선이거나, 그 선에 공구 쪽에서 직각으로 들어간다(안쪽 모서리 = 두 보정선의 교점)
            if (leadD != null) {
                move("G1", leadD, "$side")
                move("G1", startD)
            } else {
                move("G1", startD, "$side")
            }
            for (s in drawing.segs.subList(fromSeg, toSeg)) if (s is Arc) arc(s) else move("G1", if (s === last) endD else s.end)
            // 취소 뒤 G40 상태의 인선 = 후퇴점. 진입과 같이 노즈가 끝 요소 선을 넘지 않게 lift 만큼 공구 쪽으로
            move("G0", exitD, "G40")
        }.lines

        // 꼭짓점 결과: 가공 순서의 k번 모서리. 칸 순서는 그림 순서를 뒤집기만큼, 가공 순서는 그림 순서를 leftFirst 만큼 뒤집은 것이라
        // 둘은 역방향(reverse)일 때만 다르다
        val byPath = cr.indices.map { k ->
            val a = filleted.arc[k]
            if (a == null) {
                val i = filleted.prev[k]
                TaperPoint(path[k + 1], listOf(CompPoint(drawing.segs[i].end, segs[i].end)), null)
            } else {
                val arc = drawing.segs[a] as Arc
                val pa = segs[a]
                TaperPoint(
                    path[k + 1],
                    listOf(CompPoint(arc.start, pa.start), CompPoint(arc.end, pa.end)),
                    CornerArc(arc.radius, if (a in gone) null else (pa as? Arc)?.radius, arc.cw, arc.center),
                )
            }
        }
        return TaperResult(
            TaperSolution(
                points = if (reverse) byPath.reversed() else byPath,
                anglesDeg = slopes.map { slopeDeg(drawn[it], drawn[it + 1]) }.let { if (flip) it.reversed() else it },
                tip = tip,
                side = side,
                entry = entry,
                exit = exit,
                approach = approachG40,
                fromStart = cutFirst,
                toEnd = cutLast,
                ncG41G42 = ncG41G42,
                ncG40 = ncG40,
                drawing = drawing,
                center = Contour(segs.map { it.shifted(tv * -1.0) }),
                program = program,
            ),
            warnings,
        )
    }

    /** 점 p 를 q 를 지나는 방향 u 의 직선에 내린 발 */
    private fun foot(p: Pt, q: Pt, u: Pt): Pt = q + u * ((p - q) dot u)

    private fun isHorizontal(u: Pt) = abs(u.z) > abs(u.x)

    /** 진행 방향 u 에서 공구 쪽 단위 법선(G41 왼쪽, G42 오른쪽) */
    private fun toolNormal(u: Pt, side: Side) = if (side == Side.G41) u.left() else u.left() * -1.0

    private fun Seg.shifted(v: Pt): Seg = when (this) {
        is Line -> Line(start + v, end + v)
        is Arc -> Arc(start + v, end + v, center + v, cw)
    }

    /**
     * 바이트와 반대인 곳이 있나. 둘 가운데 하나면 그렇다.
     * - 모양: 도면 요소 가운데 노즈 접점이 팁 쪽 사분면(팁 벡터 ±45°) 밖인 것. 가상 인선은 Z·X 축에 평행한 두 접선이
     *   인선이라는 가정이라, 접점이 그 사분면 밖이면 실제 바이트의 날·여유각이 맞는지 따로 봐야 한다. 진행 방향과 상관없다.
     * - 진행([cut] = 깎는 요소, 가공 순서): 팁의 Z 와 반대로 가며 45° 보다 눕힌 요소를 깎는다. 그런 요소의 접점은 사분면 안에서도
     *   끝날(부절삭날 — 팁 벡터의 X 쪽, 외경 T3 이면 아래를 보는 날) 쪽 반에 있고, 깎이는 자리(접점에서 진행 쪽)가 그 끝날 쪽으로
     *   돈다 — 역방향 깎기(back turning). 예: 뒤집은 외경 형상 3 이 외경을 Z+ 로. 단면(어느 쪽으로든)과 45° 이상 경사는
     *   주절삭날 쪽이라 괜찮다 — 정방향 프로파일이 어깨를 X+ 로 오르는 것, 뒤집은 외경 형상 1 이 45° 면취를 내려와 단면을 중심 쪽으로
     *   깎는 것. 정방향(뒤집지 않은 표준 팁)은 늘 팁의 Z 쪽으로 가므로 이 판정이 없던 때와 같다.
     * 모서리 R 원호는 접점 방향이 양옆 직선 사이에서만 돌므로 현의 방향으로 본다.
     */
    private fun againstTool(c: Contour, cut: List<Seg>, side: Side, tip: Int): Boolean {
        val toTip = NoseComp.tipVector(tip, 1.0).unit()
        val shape = c.segs.any {
            val toTool = toolNormal((it.end - it.start).unit(), side)
            // 접점 방향 = −toTool. 팁 벡터와 45° 넘게 벌어지면 밖
            (toTool dot toTip) > -sqrt(0.5) + 1e-9
        }
        val travel = cut.any {
            val u = (it.end - it.start).unit()
            // 45° 는 눕힌 것이 아니다(면취 X40 Z0 / X50 Z-5 를 tan 으로 푼 값의 반올림 오차를 넘게 1e-9)
            abs(u.z) - abs(u.x) > 1e-9 && u.z * toTip.z < 0
        }
        return shape || travel
    }

    /** a(오른쪽) → b(왼쪽) 경사의 각(°). [up] 이 있으면 X 가 그쪽으로 변해야 한다 */
    private fun slopeDeg(a: Pt, b: Pt, up: Double? = null): Double {
        val dz = a.z - b.z
        val dx = b.x - a.x
        if (abs(dz) <= EPS || abs(dx) <= EPS) headStop(TaperWarning.BAD_ANGLE)
        if (dz < 0 || (up != null && dx * up < 0)) headStop(TaperWarning.SHAPE_MISMATCH)
        return atan2(abs(dx), dz) * 180 / PI
    }

    /** T1 에서 T2 까지 왼쪽으로 간 Z 거리(> 0) */
    private fun leftward(z1: Double, z2: Double): Double {
        if (z1 - z2 <= 0) headStop(TaperWarning.SHAPE_MISMATCH)
        return z1 - z2
    }

    /** T1 에서 T2 까지 TYPE 방향으로 오르내린 반경 거리(> 0) */
    private fun rise(x1: Double, x2: Double, up: Double): Double {
        val h = up * (x2 - x1) / 2
        if (h <= 0) headStop(TaperWarning.SHAPE_MISMATCH)
        return h
    }

    /**
     * p 에서 위(s > 0)·아래로 가는 단면의 끝. xDia = 입력 X(직경), null 이면 반경 [LEG_DEFAULT] 만큼(아래로는 X0 에서 멈춤).
     * 입력이 그 방향에 없거나 X < 0 이면 [TaperWarning.BAD_LEG]
     */
    private fun vertical(p: Pt, s: Double, xDia: Double?): Pt {
        if (xDia == null) return Pt(p.z, if (s > 0) p.x + LEG_DEFAULT else max(p.x - LEG_DEFAULT, 0.0))
        if (xDia < 0 || (xDia / 2 - p.x) * s <= EPS) headStop(TaperWarning.BAD_LEG)
        return Pt.ofDia(p.z, xDia)
    }

    /** p 에서 오른쪽(s > 0)·왼쪽으로 가는 수평 요소의 끝. z = 입력 Z, null 이면 [LEG_DEFAULT] 만큼 */
    private fun horizontal(p: Pt, s: Double, z: Double?): Pt {
        if (z == null) return Pt(p.z + s * LEG_DEFAULT, p.x)
        if ((z - p.z) * s <= EPS) headStop(TaperWarning.BAD_LEG)
        return Pt(z, p.x)
    }

    private fun checkCommon(r: Double, tip: Int?, zClear: Double, corners: List<Double>, start: Double?, end: Double?) {
        // Z 여유 0 은 된다(진입·후퇴 거리 = 노즈R). 둘 다 0 이면 진입·후퇴 이동이 없어 G41/G42 를 걸고 풀 수 없다
        val ok = r >= 0 && r.isFinite() && zClear >= 0 && zClear.isFinite() && max(r, zClear) > 0 && (tip == null || tip in 0..9) &&
            corners.all { it >= 0 && it.isFinite() } && listOfNotNull(start, end).all { it.isFinite() } &&
            !tooBig(r, zClear, start, end, *corners.toTypedArray())
        if (!ok) headStop(TaperWarning.BAD_INPUT)
    }
}

private class HeadStop(val warning: TaperWarning) : Exception()

private fun headStop(w: TaperWarning): Nothing = throw HeadStop(w)

private inline fun headGuarded(block: () -> TaperResult): TaperResult =
    try {
        block()
    } catch (s: HeadStop) {
        TaperResult(null, listOf(s.warning))
    }
