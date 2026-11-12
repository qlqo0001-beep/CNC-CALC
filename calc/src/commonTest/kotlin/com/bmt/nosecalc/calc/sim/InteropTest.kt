package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.ncWords
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 테이퍼 계산기가 낸 NC 를 시뮬레이터로(`docs/simulator-design.md` 9.3 InteropTest).
 * G41/G42 글(도면 좌표 — 회사 꼴, 계획 단계 4)을 넣으면, 형상 블록 사이 이음점의 실제 인선 = 직접 보정 글(ncG40)의 점이어야 한다.
 * 첫 깎는 줄(F.1) 끝과 마지막 형상 블록 끝은 뺀다 — 처음 · 끝은 진입 · 빼는 줄과의 관계로 정해진다(`calc-design.md` '진입점과 NC' —
 * 그 자리는 SimVerifyTest · TaperCalcTest 가 본다). 뒤집은 진입(2026-11-12 통합 고침)은 F.1 뒤 첫 깎는 점까지 이송 줄이 하나 더 있어 그 줄 끝도 뺀다.
 * 같은 사례 묶음은 TaperCalcTest.g41g42ProgramCompensatesToDirectProgram 과 같다. 테이퍼에서 STOP 이 나오면 실패.
 * 글은 0.001 로 반올림돼 있어(도면 · 직접 보정 모두) 허용 0.002.
 */
class InteropTest {
    private val pre = listOf("T0101", "G97 S1000 M03", "G99 F0.2")
    private var worst = 0.0

    private fun rad(deg: Double) = deg * PI / 180

    private fun TaperInput.flipped() =
        copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start, flip = !flip)

    private fun DoubleTaperInput.flipped() =
        copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start, flip = !flip)

    @Test
    fun taperNcThroughSimulator() {
        var checked = 0
        var joints = 0
        var concave = 0
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (type in 1..5) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 0.8)) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = 60 + up * 2 * 6 * tan(rad(deg))
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, Fmt.nc(x2).toDouble(), -6.0, cornerR1 = r1, cornerR2 = r2)
                val right = if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                val left = if (type == 2 || type == 4) inp.copy(end = Fmt.nc(x2 + up * 12).toDouble()) else inp.copy(end = -12.0)
                for (i in listOf(inp, right, left).flatMap { listOf(it, it.flipped()) }) {
                    val res = TaperCalc.calc(i)
                    val s = res.solution ?: continue
                    if (TaperWarning.CONCAVE_R_LE_NOSE in res.warnings) {
                        // 계산기가 이미 '오목 R ≤ 노즈 R' 을 알린 모양 — 제어기는 R < r 이면 알람(H 61)
                        val run = GSim.run(SimInput((pre + s.ncG41G42).joinToString("\n"), MachineSetup(), mapOf(1 to ToolOffset(r, s.tip))))
                        assertTrue(run.stop == null || run.stop!!.code == IssueCode.COMP_ARC_SMALL, "$i ${run.stop}")
                        concave++
                        continue
                    }
                    joints += check(s, r, i.toString(), TaperWarning.AGAINST_TOOL in res.warnings)
                    checked++
                }
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            val inp = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
            for (i in listOf(inp, inp.copy(start = 4.0), inp.copy(end = -14.0), inp.copy(start = 4.0, end = -14.0)).flatMap { listOf(it, it.flipped()) }) {
                val res = TaperCalc.calcDouble(i)
                val s = res.solution ?: continue
                joints += check(s, 0.4, i.toString(), TaperWarning.AGAINST_TOOL in res.warnings)
                checked++
            }
        }
        println("InteropTest taper checked=$checked joints=$joints concave=$concave worst=$worst against=${against.size}")
        for (a in against.take(30)) println("  $a")
        assertTrue(checked > 2400, "확인한 사례 $checked")
        assertTrue(joints > 2000, "이음점 $joints")
    }

    /** 바이트 반대(AGAINST_TOOL) 경고가 난 사례 가운데 시뮬레이터가 멈춘 것 — 알리기만(VerifyFlipTest 와 같은 방침: 화면이 이미 '바이트와 반대' 를 띄운다) */
    private val against = mutableListOf<String>()

    /** 이음점 수를 돌려준다 */
    private fun check(s: TaperSolution, r: Double, tag: String, againstTool: Boolean): Int {
        val run = GSim.run(SimInput((pre + s.ncG41G42).joinToString("\n"), MachineSetup(), mapOf(1 to ToolOffset(r, s.tip))))
        if (againstTool && run.stop != null) {
            against += "${run.stop!!.code} flip=${"flip=true" in tag} ${s.ncG41G42}"
            return 0
        }
        assertTrue(run.stop == null, "$tag 멈춤 ${run.stop}\n${s.ncG41G42}")
        val direct = GSim.run(SimInput((pre + s.ncG40).joinToString("\n"), MachineSetup(), mapOf(1 to ToolOffset(r, s.tip))))
        assertTrue(direct.stop == null, "$tag 직접 보정 멈춤 ${direct.stop}")
        val pts = direct.ghosts.map { it.cmd }
        val lines = s.ncG41G42
        // 첫 깎는 줄(F.1)과 첫 빼는 줄(F.3) — 그 사이가 형상 블록
        val iFirst = lines.indexOfFirst { it.endsWith("F.1") }
        val iExit = lines.indexOfFirst { "F.3" in it }
        assertTrue(iFirst > 0 && iExit > iFirst + 1, "$tag 회사 꼴이 아님 $lines")
        // 첫 깎는 점(도면의 첫 깎는 요소 처음)에 닿는 줄 — 뒤집은 진입이면 F.1 다음 이송 줄
        val fc = s.drawing.segs[if (s.fromStart) 0 else 1].start
        var x = 0.0
        var z = 0.0
        val at = lines.map { l ->
            for (w in ncWords(l)) when (w[0]) {
                'X' -> x = w.drop(1).toDouble() / 2
                'Z' -> z = w.drop(1).toDouble()
            }
            Pt(z, x)
        }
        val iCut = (iFirst until iExit).firstOrNull { (at[it] - fc).length < 1e-3 } ?: iFirst
        var n = 0
        for (i in iCut + 1 until iExit - 1) {
            val line = pre.size + i + 1
            val tip = run.ghosts.last { it.line == line }.tip
            val d = pts.minOf { (it - tip).length }
            worst = max(worst, d)
            assertTrue(d <= 0.002, "$tag ${lines[i]} 인선 ${SimKit.xz(tip)} 가 직접 보정 글에 없다(가장 가까운 것까지 $d)\n${s.ncG41G42}\n${s.ncG40}")
            n++
        }
        return n
    }
}
