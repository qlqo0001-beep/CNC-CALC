package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Input
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Legacy
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarInput
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ncWords
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 회사 표기(설계 18.6 — `G1X40.Z-.5` · 낱말 붙여 씀 · 이동 G 는 바뀔 때만)로 바꾼 NC 글이 옛 꼴(`G01 X40. Z-0.5` · 이동 줄마다 G — [Legacy])과
 * 같은 경로인가. 대표 입력(테이퍼 G41/G42 · 직접 보정, 겹테이퍼 모서리 R, 뒤집기 · 내경, 라운드 직접 · 기계, 3점호, 홈, 나사 G76, 보링바 잘린 글,
 * 시뮬레이터 예제 전부)마다 두 글을 GSim 에 넣어 이동마다 종류 · 줄 · 노즈 경로(시작 · 끝 · 원호 중심)가 0.001 안이고 멈춤 · 문제 목록이 같아야 한다.
 * 극좌표 G12.1 은 GSim 이 받지 않아 줄마다 풀어 쓴 지령(이동 G · 보정 G · X · C · Z · F · R)을 견준다.
 */
class NotationTest {
    private val tol = 1e-3
    private val pre = listOf("T0101", "G97S1000M03", "G99F.2")

    private fun cmp(tag: String, newText: List<String>, tools: Map<Int, ToolOffset>) {
        val oldText = Legacy.nc(newText)
        // 표기가 정말 바뀌었나(옛 꼴과 다르고, 새 글에는 빈칸 · G00 · '0.' 앞 0 이 없다)
        assertTrue(newText != oldText, "$tag: 옛 꼴과 같다")
        // 주석(괄호 안 — 예제 첫 줄 `O#### (이름)` 의 빈칸 포함)은 빼고 본다
        for (l in newText.map { it.substringBefore('(').trimEnd() }) {
            assertTrue(' ' !in l, "$tag: 빈칸 '$l'")
            assertTrue(ncWords(l).none { it.matches(Regex("G0[0-3]")) || it.matches(Regex("[A-Z]-?0\\.\\d+")) }, "$tag: 옛 표기 '$l'")
        }
        val a = GSim.run(SimInput(newText.joinToString("\n"), MachineSetup(), tools))
        val b = GSim.run(SimInput(oldText.joinToString("\n"), MachineSetup(), tools))
        assertEquals(b.stop?.code, a.stop?.code, "$tag: 멈춤 ${a.stop} / ${b.stop}")
        assertEquals(b.issues.map { it.code to it.line }, a.issues.map { it.code to it.line }, "$tag: 문제")
        val ma = a.steps.filterIsInstance<MoveStep>()
        val mb = b.steps.filterIsInstance<MoveStep>()
        assertEquals(mb.size, ma.size, "$tag: 이동 수\n$newText\n$oldText")
        assertTrue(ma.size >= 3, "$tag: 이동 ${ma.size}")
        for ((p, q) in ma.zip(mb)) {
            assertEquals(q.kind, p.kind, "$tag ${p.line}줄 종류")
            assertEquals(q.line, p.line, "$tag 줄")
            val pts = mutableListOf(p.nose.start to q.nose.start, p.nose.end to q.nose.end)
            val ap = p.nose as? Arc
            val aq = q.nose as? Arc
            assertEquals(aq != null, ap != null, "$tag ${p.line}줄 원호")
            if (ap != null && aq != null) pts += ap.center to aq.center
            for ((u, v) in pts) assertTrue((u - v).length <= tol, "$tag ${p.line}줄: 새 $u / 옛 $v")
        }
    }

    private fun taper(tag: String, s: TaperSolution, r: Double) {
        cmp("$tag G41/G42", pre + s.ncG41G42, mapOf(1 to ToolOffset(r, s.tip)))
        cmp("$tag 직접 보정", pre + s.ncG40, mapOf(1 to ToolOffset(r, s.tip)))
    }

    @Test
    fun taperPathsSame() {
        val single = listOf(
            "외경 1" to TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0),
            "외경 1 시작 · 끝" to TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, start = 30.0, end = -10.0),
            "외경 모서리 R" to TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, cornerR1 = 0.5, cornerR2 = 1.0, end = -10.0),
            "내경 1 0.8R" to TaperInput(inner = true, noseR = 0.8, x1 = 40.0, z1 = 0.0, x2 = 30.0, z2 = -5.0),
            "외경 1 뒤집기" to TaperInput(x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, start = -10.0, end = 5.0, flip = true),
            "외경 0.5 소수" to TaperInput(noseR = 0.2, x1 = 0.5, z1 = -0.25, x2 = 3.6, z2 = -1.75, start = 0.0),
        )
        for ((tag, i) in single) {
            val s = assertNotNull(TaperCalc.calc(i).solution, tag)
            taper(tag, s, i.noseR)
        }
        val dbl = DoubleTaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0, x3 = 44.0, z3 = -12.0, cornerR2 = 2.0)
        taper("겹테이퍼", assertNotNull(TaperCalc.calcDouble(dbl).solution), dbl.noseR)
    }

    @Test
    fun roundAndGroovePathsSame() {
        val rounds = listOf(
            RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0),
            RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, legLen = 10.0, inner = true),
            RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, inner = true),
        )
        for (i in rounds) {
            val tag = "라운드 TYPE ${i.type}${if (i.inner) " 내경" else ""}"
            val d = assertNotNull(RoundCalc.calc(i).solution, tag)
            cmp("$tag 직접 보정", pre + d.nc, mapOf(1 to ToolOffset(i.noseR, d.tip)))
            val m = assertNotNull(RoundCalc.machine(i).solution, tag)
            cmp("$tag 기계 보정", pre + m.nc, mapOf(1 to ToolOffset(i.noseR, m.tip)))
        }
        val a3 = Arc3Input(noseR = 0.4, x1 = 20.0, z1 = 0.0, x2 = 26.0, z2 = -3.0, x3 = 30.0, z3 = -8.0)
        cmp("3점호", pre + assertNotNull(RoundCalc.arc3(a3).solution).nc, mapOf(1 to ToolOffset(0.4, 3)))
        val g = assertNotNull(GrooveCalc.calc(GrooveInput(zStart = -10.0, width = 8.0, od = 40.0, bottom = 30.5, insert = 3.0, overlap = 0.5)).solution)
        cmp("홈", pre + g.nc, mapOf(1 to ToolOffset(0.0, 0)))
    }

    @Test
    fun threadAndHolderPathsSame() {
        for ((tag, i) in listOf(
            "1/4-20 UNC" to G76Input(spec = ThreadTable.UN.single { it.size == "1/4" && it.tpi == 20 }, zStart = 5.0, zEnd = -25.0, firstCut = 0.2, rpm = 1000.0),
            "M3×0.5 안" to G76Input(inner = true, spec = ThreadTable.METRIC.single { it.d == 3.0 && it.pitch == 0.5 }, zStart = 2.0, zEnd = -6.0, firstCut = 0.05),
        )) {
            val s = assertNotNull(G76Calc.calc(i).solution, tag)
            cmp("나사 $tag", listOf("T0101", "G97S1000M03") + s.program, mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD)))
        }
        // 보링바 잘린 글(돔 내경 · 돌출 길게): 직접 보정 · 기계 보정 둘 다
        val r = 0.4
        val inp = RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 30.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0, legLen = 5.0, inner = true, noseR = r)
        val sol = assertNotNull(RoundCalc.calc(inp).solution)
        val drawn = assertNotNull(RoundCalc.machine(inp).solution)
        val p = HolderStop.plan(sol, drawn, r, true, BoringBar(16.0, 11.0, InsertShape.C, 9.67, 95.0, overhang = 16.4), 0.5)
        val direct = assertNotNull(p.direct, "직접 보정 잘린 글")
        val machine = assertNotNull(p.machine, "기계 보정 잘린 글")
        cmp("보링바 직접 보정", pre + direct.nc, mapOf(1 to ToolOffset(r, sol.tip)))
        cmp("보링바 기계 보정", pre + machine.nc, mapOf(1 to ToolOffset(r, sol.tip)))
    }

    @Test
    fun examplesPathsSame() {
        // 회사 꼴 예제(O9020~)는 처음부터 이 표기라 옛 꼴로 되돌린 글과만 견준다
        for (e in SimExamples.all) cmp(e.name, e.text.lines(), e.tools)
    }

    /** 극좌표: 줄마다 풀어 쓴 지령(빠진 낱말은 앞 줄 값)이 새 글 · 옛 글에서 같다 */
    @Test
    fun polarCommandsSame() {
        val inputs = listOf(
            PolarInput(shape = PolarShape.SQUARE, od = 50.0, width = 40.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0),
            PolarInput(od = 100.0, width = 80.0, cornerR = 8.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0),
            PolarInput(inner = true, shape = PolarShape.SQUARE, od = 20.0, width = 40.0, cornerR = 6.0, toolDia = 10.0, depthZ = -0.5, feed = 80.0),
            PolarInput(shape = PolarShape.TWO_FLAT, od = 48.0, width = 30.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0, xDiameter = false),
        )
        for ((n, i) in inputs.withIndex()) {
            val s = assertNotNull(PolarG12.calc(i).solution, "$n")
            for ((tag, prog) in listOf("G41" to s.ncG41G42, "G40" to s.ncG40)) {
                val new = prog.lines
                val old = Legacy.nc(new)
                assertTrue(new != old, "$n $tag")
                val a = resolve(new)
                val b = resolve(old)
                assertEquals(b.size, a.size)
                for ((k, pair) in a.zip(b).withIndex()) {
                    val (u, v) = pair
                    assertEquals(v.first, u.first, "$n $tag ${k + 1}줄 ${new[k]} / ${old[k]}")
                    for ((x, y) in u.second.zip(v.second)) {
                        assertTrue((x.isNaN() && y.isNaN()) || abs(x - y) <= tol, "$n $tag ${k + 1}줄 ${new[k]} / ${old[k]}")
                    }
                }
            }
        }
    }

    /** 줄마다 (이동 G · 보정 G · 그 밖 G, [X C Z F R]) — 빠진 이동 G · X · C · Z · F 는 앞 줄 값, R 은 그 줄만(없으면 0) */
    private fun resolve(lines: List<String>): List<Pair<String, List<Double>>> {
        var g = "-"
        var comp = "40"
        val v = doubleArrayOf(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
        return lines.map { line ->
            if (line.startsWith("(")) return@map line to emptyList()
            var rr = 0.0
            val others = mutableListOf<String>()
            for (w in ncWords(line)) {
                val num = w.drop(1)
                when (w[0]) {
                    'G' -> when (val c = num.toDoubleOrNull()?.toString() ?: num) {
                        "0.0", "1.0", "2.0", "3.0" -> g = c
                        "40.0", "41.0", "42.0" -> comp = c
                        else -> others += c
                    }
                    'X' -> v[0] = num.toDouble()
                    'C' -> v[1] = num.toDouble()
                    'Z' -> v[2] = num.toDouble()
                    'F' -> v[3] = num.toDouble()
                    'R' -> rr = num.toDouble()
                }
            }
            "$g $comp ${others.joinToString(",")}" to (v.toList() + rr)
        }
    }
}
