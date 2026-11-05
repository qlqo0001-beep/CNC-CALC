package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.PipeInput
import com.bmt.nosecalc.calc.PipeSolution
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 관용 테이퍼 나사의 G92 테이퍼 글(PipeThreadCalc — `G92X…Z…I…F…` · 둘째 회부터 X 만)을 GSim 에 넣어 본다(지은 치수).
 * - 멈춤 0 · 문제 0
 * - 깎는 회 수 = 분석표 회차 수 + 1(M24 줄 — G92 모드의 M 만 줄은 사이클을 한 번 더 돈 뒤 M, FF 4.1.6 · 곧은 나사 G92ThreadTest 와 같음)
 * - 회마다 THREAD 이동 끝 = (분석표 X, 끝 Z), 시작 = (끝 X + 2I, 시작 Z) — I 는 첫 줄에만 쓰고 모달로 이어진다.
 *   이 시험이 부호를 못 박는다: 시뮬레이터 GCycles.single 은 A′ = (A.z, B.x + I)(시작 반경 = 끝 반경 + I — FANUC S 4-96 · Haas I 와 같음)이고,
 *   계산기는 숫나사 I < 0(시작 쪽이 가늚) · 암나사 I > 0(입구 쪽이 굵음)을 낸다. 기울기는 반경 |I| / (시작 Z − 끝 Z) = 1/32(지름 1/16).
 * - 진입은 Z20. 에서 X 를 맞춘 뒤 Z 로만 시작 Z 까지, 끝은 시작점에서 Z 로만 Z200., 다듬질 블록은 마지막 X 두 번 + M24 줄
 * - 낱말 R 로 쓴 글(FANUC 0i 기본 형식)이 I 글과 이동이 같다
 */
class G92TaperThreadTest {
    private val pre = listOf("T0101", "G97S500M03")
    private val tools = mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD))
    private val tol = 1e-3

    private fun pt(size: String) = PipeTable.PT.single { it.size == size }
    private fun npt(size: String) = PipeTable.NPT.single { it.size == size }

    private val cases = listOf(
        "R 1/2 숫" to PipeInput(spec = pt("1/2"), zStart = 6.0, zEnd = -14.0, firstCut = 0.4, rpm = 530.0),
        "Rc 1/2 암" to PipeInput(inner = true, spec = pt("1/2"), zStart = 4.0, zEnd = -16.0, firstCut = 0.3),
        "1/2-14 NPT 숫" to PipeInput(spec = npt("1/2"), zStart = 6.0, zEnd = -14.0, firstCut = 0.4),
        "1/2-14 NPT 암 기준면 −5" to PipeInput(inner = true, spec = npt("1/2"), zRef = -5.0, zStart = -1.0, zEnd = -21.0, firstCut = 0.4),
        "R 1/8 숫 F.9071" to PipeInput(spec = pt("1/8"), zStart = 3.0, zEnd = -9.0, firstCut = 0.15),
        "1-11.5 NPT 암 F2.2087 · I .781" to PipeInput(inner = true, spec = npt("1"), zStart = 5.0, zEnd = -20.0, firstCut = 0.4),
        "R 1/2 숫 게이지 +1산" to PipeInput(spec = pt("1/2"), zStart = 6.0, zEnd = -16.0, firstCut = 0.4, gaugeShift = 1.0),
    )

    private fun run(tag: String, lines: List<String>): SimRun {
        val r = GSim.run(SimInput((pre + lines + "M30").joinToString("\n"), MachineSetup(), tools))
        assertNull(r.stop, "$tag: 멈춤 ${r.stop}\n$lines")
        assertEquals(emptyList(), r.issues.map { it.code }, "$tag: 문제\n$lines")
        return r
    }

    /** 회마다 나사 이동(끝 빼기 설정 0 이라 회마다 한 토막) */
    private fun cuts(r: SimRun): List<MoveStep> {
        val g = r.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.THREAD }.groupBy { it.line to it.pass }.values.toList()
        g.forEach { assertEquals(1, it.size, "끝 빼기 없음 — 한 회 한 토막") }
        return g.map { it.single() }
    }

    private fun near(e: Double, a: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    /** 첫 G92 줄의 테이퍼 낱말 값 */
    private fun taperWord(s: PipeSolution, word: Char): Double =
        s.g92.single { it.startsWith("G92") }.substringAfter("Z").let { Regex("$word(-?[0-9.]+)").find(it)!!.groupValues[1].toDouble() }

    @Test
    fun taperG92ThroughSimulator() {
        for ((tag, i) in cases) {
            val s = assertNotNull(PipeThreadCalc.calc(i).solution, tag)
            val zs = i.zStart!!
            val ze = i.zEnd!!
            val taper = taperWord(s, 'I')
            assertEquals(s.taper, taper, "$tag: 글의 I = 지령값")
            // 부호: 숫나사 −, 암나사 + · 크기 (시작 Z − 끝 Z)/32 (0.001 반올림 안)
            assertTrue(if (i.inner) taper > 0 else taper < 0, "$tag: I 부호 $taper")
            assertTrue(abs(abs(taper) - (zs - ze) / 32) <= 0.0005 + 1e-12, "$tag: |I| = L/32")
            // I 는 첫 G92 줄에만
            assertEquals(1, s.g92.count { it.contains("I") }, "$tag: I 는 한 줄")
            val r = run(tag, s.g92)
            val c = cuts(r)
            assertEquals(s.passes.size + 1, c.size, "$tag: 회차 수(분석표 + M24 줄)")
            assertEquals(pre.size + s.g92.size - 1, c.last().line, "$tag: 마지막 회는 M24 줄")
            for ((m, x) in c.zip(s.passes.map { it.xDia } + s.passes.last().xDia)) {
                val w = "$tag 줄 ${m.line} 회 ${m.pass}"
                near(x, m.tip.end.xDia, "$w 끝 X = 분석표")
                near(ze, m.tip.end.z, "$w 끝 Z")
                near(zs, m.tip.start.z, "$w 시작 Z")
                near(m.tip.end.xDia + 2 * taper, m.tip.start.xDia, "$w 시작 X = 끝 X + 2I")
                // 숫나사는 시작 쪽이 가늘고 암나사는 굵다 — 기울기 반경 1/32
                assertTrue(if (i.inner) m.tip.start.x > m.tip.end.x else m.tip.start.x < m.tip.end.x, "$w 테이퍼 방향")
                val slope = (m.tip.start.x - m.tip.end.x) / (m.tip.start.z - m.tip.end.z)
                assertTrue(abs(abs(slope) - 1.0 / 32) <= 0.0005 / (zs - ze) + 1e-12, "$w 기울기 $slope")
            }
            near(s.passes.last().xDia, c.last().tip.end.xDia, "$tag 마지막 X")
            // 진입 · 물러남(곧은 나사와 같은 꼴): Z20. 에서 시작 X → Z 로 시작 Z, 끝은 시작점에서 Z 로 Z200.
            val moves = r.steps.filterIsInstance<MoveStep>()
            val zLine = pre.size + s.g92.indexOfFirst { it.startsWith("Z") } + 1
            val approach = moves.single { it.line == zLine }
            near(20.0, approach.tip.start.z, "$tag 진입 Z")
            near(zs, approach.tip.end.z, "$tag 시작 Z")
            near(s.startX, approach.tip.start.xDia, "$tag 진입 X")
            near(s.startX, approach.tip.end.xDia, "$tag 진입은 Z 로만")
            val last = moves.last()
            assertEquals(MoveKind.RAPID, last.kind)
            near(zs, last.tip.start.z, "$tag 사이클 뒤 시작점")
            near(200.0, last.tip.end.z, "$tag 물러남 Z")
            near(s.startX, last.tip.end.xDia, "$tag 물러남은 Z 로만")
            // 사이클 ③ 급송 X 는 시작 X 로 돌아온다 — 숫나사는 바깥, 암나사는 축 쪽이라 회마다 나사 산에서 떨어진다
            assertTrue(if (i.inner) s.startX < s.passes.minOf { it.xDia } else s.startX > s.passes.maxOf { it.xDia }, "$tag 시작 X 쪽")
            // 다듬질 블록: 마지막 X 두 번 + M24 줄 — 같은 테이퍼
            val fin = cuts(run("$tag 다듬질", s.g92Finish))
            assertEquals(3, fin.size, "$tag: 다듬질 회 수")
            for (m in fin) {
                near(s.passes.last().xDia, m.tip.end.xDia, "$tag 다듬질 끝 X")
                near(m.tip.end.xDia + 2 * taper, m.tip.start.xDia, "$tag 다듬질 시작 X")
            }
        }
    }

    @Test
    fun rWordMovesSameAsI() {
        for ((tag, i) in cases) {
            val si = assertNotNull(PipeThreadCalc.calc(i).solution, tag)
            val sr = assertNotNull(PipeThreadCalc.calc(i.copy(taperWord = 'R')).solution, tag)
            assertEquals(si.taper, taperWord(sr, 'R'), "$tag: R 값 = I 값")
            val mi = run("$tag I", si.g92).steps.filterIsInstance<MoveStep>()
            val mr = run("$tag R", sr.g92).steps.filterIsInstance<MoveStep>()
            assertEquals(mi.size, mr.size, "$tag: 이동 수")
            for ((a, b) in mi.zip(mr)) {
                assertEquals(a.kind, b.kind, "$tag 줄 ${a.line}")
                assertEquals(a.tip.start, b.tip.start, "$tag 줄 ${a.line} 시작")
                assertEquals(a.tip.end, b.tip.end, "$tag 줄 ${a.line} 끝")
            }
        }
    }
}
