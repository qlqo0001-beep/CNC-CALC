package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Input
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadTable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 나사 G92 다회 글(계획 단계 6 — `G76Solution.g92` · `g92Finish`)을 GSim 에 넣어 본다. 대표 나사(골든 thread*.txt 의 1/4-20 UNC 바깥 ·
 * M10×1.5 6H 안 · M10×1.5 4h 바깥 · 역가공과 M3×0.5 안 · 1/2-13 UNC · 3/4-20 UNEF 바깥 · 3/8-20 UN 바깥 · 안)마다
 * - 멈춤 0 · 문제 0(M23 · M24 는 아는 코드라 '기계 M' 알림도 없음)
 * - 나사를 깎는 회 수 = 분석표 회차 수 + 1, 회마다 깎는 X = 분석표 X = GSim 이 같은 나사의 G76 두 줄로 그린 회차 X(0.001 안) — 마지막 X = G76 X(정삭).
 *   + 1 은 M24 줄: G92 모드 안의 M 만 블록이라 FANUC 은 그 줄에서 사이클을 한 번 더 돈 뒤 M 을 실행한다
 *   (FF 4.1.6 '- Block in which no move command is specified' · F0 13.1 주 2 — simulator-design.md 13.1 · 18.1). 그 회의 X 는 마지막 X 그대로
 * - 깎는 줄은 시작 Z → 끝 Z, 회마다 시작점으로 돌아오고, 진입은 Z20. 에서 X 를 맞춘 뒤 Z 로만, 끝은 시작점에서 Z 로만 물러난다
 * - M23 은 G92 바로 앞 줄, M24 는 마지막 X 바로 뒤 줄(혼자 줄 — 사이클 블록에 M 을 같이 쓰면 오류, S 4-93)
 * - 다듬질 블록은 마지막 X 두 번(+ M24 줄 한 번)
 */
class G92ThreadTest {
    private val pre = listOf("T0101", "G97S1000M03")
    private val tools = mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD))
    private val tol = 1e-3

    private val cases = listOf(
        "1/4-20 UNC 바깥" to G76Input(spec = un("1/4", 20), zStart = 5.0, zEnd = -25.0, firstCut = 0.2, rpm = 1821.0),
        "M10×1.5 6H 안 50%" to G76Input(inner = true, spec = metric(10.0, 1.5), zStart = 3.0, zEnd = -20.0, firstCut = 0.15, target = 50.0),
        "M10×1.5 4h 바깥" to G76Input(spec = metric(10.0, 1.5), cls = ThreadClass.M4h, zStart = 3.0, zEnd = -20.0, firstCut = 0.15, rpm = 1200.0),
        "1/4-20 UNC 역가공" to G76Input(spec = un("1/4", 20), dir = ThreadDir.REVERSE, zStart = -30.0, zEnd = 0.0, firstCut = 0.2, rpm = 1821.0),
        "M3×0.5 안" to G76Input(inner = true, spec = metric(3.0, 0.5), zStart = 2.0, zEnd = -6.0, firstCut = 0.05),
        "1/2-13 UNC 바깥 m3" to G76Input(spec = un("1/2", 13), zStart = 5.0, zEnd = -30.0, firstCut = 0.3, finishCount = 3),
        "3/4-20 UNEF 바깥" to G76Input(spec = un("3/4", 20), zStart = 5.0, zEnd = -20.0, firstCut = 0.2, rpm = 600.0),
        "3/8-20 UN 바깥" to G76Input(spec = un("3/8", 20), zStart = 5.0, zEnd = -15.0, firstCut = 0.2),
        "3/8-20 UN 안" to G76Input(inner = true, spec = un("3/8", 20), zStart = 3.0, zEnd = -12.0, firstCut = 0.2),
    )

    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
    private fun metric(d: Double, p: Double) = ThreadTable.METRIC.single { it.d == d && it.pitch == p }

    private fun run(tag: String, lines: List<String>, machine: MachineSetup = MachineSetup()): SimRun {
        // 카드 글은 프로그램 조각이라 끝(M30)을 붙여 돌린다
        val r = GSim.run(SimInput((pre + lines + "M30").joinToString("\n"), machine, tools))
        assertNull(r.stop, "$tag: 멈춤 ${r.stop}\n$lines")
        assertEquals(emptyList(), r.issues.map { it.code }, "$tag: 문제\n$lines")
        return r
    }

    /** 나사 이동을 회마다(줄 · 회 번호로) 묶음 — 끝 빼기가 있으면 한 회가 곧은 토막 + 빼는 토막 둘 */
    private fun threadGroups(r: SimRun): List<List<MoveStep>> =
        r.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.THREAD }.groupBy { it.line to it.pass }.values.toList()

    /** 회마다 첫 나사 이동 — 곧은 나사라 끝 빼기가 있어도 첫 토막 끝의 X 가 그 회의 X */
    private fun passes(r: SimRun): List<MoveStep> = threadGroups(r).map { it.first() }

    private fun near(e: Double, a: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    @Test
    fun g92ThroughSimulator() {
        for ((tag, i) in cases) {
            val s: G76Solution = assertNotNull(G76Calc.calc(i).solution, tag)
            val xNc = Fmt.nc(s.x).toDouble()
            val zs = i.zStart!!
            val ze = i.zEnd!!
            // M23 은 G92 바로 앞, M24 는 마지막 X 바로 뒤(그다음이 G0Z200.)
            val g = s.g92.indexOfFirst { it.startsWith("G92") }
            assertEquals("M23", s.g92[g - 1], "$tag: G92 앞 줄")
            assertEquals(listOf("M24", "G0Z200."), s.g92.takeLast(2), "$tag: 끝 두 줄")
            assertEquals(s.passes.size, s.g92.size - g - 2, "$tag: G92 · X 줄 수 = 분석표 회차 수")
            val r = run("$tag G92", s.g92)
            val cuts = passes(r)
            // 회차 수 = 분석표 + M24 줄의 한 회, 회마다 X = 분석표(M24 줄은 마지막 X 그대로), 마지막 = G76 X
            assertEquals(s.passes.size + 1, cuts.size, "$tag: 회차 수(분석표 + M24 줄)")
            assertEquals(pre.size + s.g92.size - 1, cuts.last().line, "$tag: 마지막 회는 M24 줄")
            for ((c, x) in cuts.zip(s.passes.map { it.xDia } + s.passes.last().xDia)) {
                near(x, c.tip.end.xDia, "$tag 줄 ${c.line} 회 ${c.pass} X")
                near(zs, c.tip.start.z, "$tag 줄 ${c.line} 시작 Z")
                near(ze, c.tip.end.z, "$tag 줄 ${c.line} 끝 Z")
                assertTrue(abs(c.tip.start.x - c.tip.end.x) <= 1e-9, "$tag 줄 ${c.line}: 곧은 나사")
            }
            near(xNc, cuts.last().tip.end.xDia, "$tag 마지막 X")
            // G76 두 줄을 GSim 이 풀어 그린 회차 X 와 같다(같은 절입표 — G76 글에는 M24 줄이 없어 회차 수가 하나 적다)
            val g76 = passes(run("$tag G76", s.program))
            assertEquals(s.passes.size, g76.size, "$tag: G76 회차 수")
            for ((a, b) in cuts.zip(g76)) near(b.tip.end.xDia, a.tip.end.xDia, "$tag G76 ${b.pass}회 X")
            // 진입: 시작 X 를 Z20. 에서 맞춘 뒤 Z 로만 시작 Z 까지, 끝: 시작점에서 Z 로만 Z200.
            val moves = r.steps.filterIsInstance<MoveStep>()
            val zLine = pre.size + s.g92.indexOfFirst { it.startsWith("Z") } + 1
            val approach = moves.filter { it.line == zLine }
            assertEquals(1, approach.size, "$tag: 진입 Z 줄")
            near(20.0, approach.single().tip.start.z, "$tag 진입 Z")
            near(zs, approach.single().tip.end.z, "$tag 시작 Z")
            near(s.startX, approach.single().tip.start.xDia, "$tag 진입 X")
            near(s.startX, approach.single().tip.end.xDia, "$tag 진입은 Z 로만")
            val last = moves.last()
            assertEquals(MoveKind.RAPID, last.kind)
            assertEquals(pre.size + s.g92.size, last.line, "$tag: 끝은 G0Z200. 줄")
            near(zs, last.tip.start.z, "$tag: 사이클 뒤 시작점")
            near(200.0, last.tip.end.z, "$tag 물러남 Z")
            near(s.startX, last.tip.start.xDia, "$tag 물러남 시작 X")
            near(s.startX, last.tip.end.xDia, "$tag 물러남은 Z 로만")
            // 다듬질 블록: 같은 꼴(M23 · M24) — G92 줄과 X 한 줄로 마지막 X 두 번, M24 줄에서 한 번 더
            val fg = s.g92Finish.indexOfFirst { it.startsWith("G92") }
            assertEquals("M23", s.g92Finish[fg - 1], "$tag: 다듬질 G92 앞 줄")
            assertEquals(listOf("X${Fmt.word(xNc)}", "M24", "G0Z200."), s.g92Finish.takeLast(3), "$tag: 다듬질 끝")
            val fin = passes(run("$tag 다듬질", s.g92Finish))
            assertEquals(3, fin.size, "$tag: 다듬질 회 수(두 번 + M24 줄)")
            fin.forEach { near(xNc, it.tip.end.xDia, "$tag 다듬질 X") }
        }
    }

    /**
     * UNEF · 20UN 의 G92 글 손 계산(H28 식 · 물림 길이 LE = 9P = 0.45 인치, 목표 80%, 기본 인선 R, Δd 0.2 · Δdmin 0.035 · d 0.02 · m 2, 지은 치수).
     * 절입 깊이 Δd·√n — 0.2 · 0.283 · 0.346 · 0.4 · 0.447 · 0.49 · 0.529 · 0.566, 그다음은 √n 증분이 0.035 보다 작아 +0.035 씩, k − d 에서 자름, 정삭 두 번.
     * 회차 X = 끝 X ± 2(k − 깊이), G92 줄은 그 X 를 0.001 로(끝 0 지움). 진입 · M23 · M24 · 물러남은 g92HandNumbers 와 같은 꼴.
     *  - 3/4-20 UNEF 2A 바깥 Z5 → −20: X17.369 · k 0.741 (G76CalcTest.unefHandNumbers), 시작 X 21.016.
     *    깊이 0.6007 · 0.6357 · 0.6707 · 0.7057 · 0.721(k − d) → X 17.369 + 2 × (0.741 − 깊이).
     *  - 3/8-20 UN 2A 바깥 Z5 → −15: B = 0.0015∛0.375 + 0.0015√0.45 + 0.015∛0.05² = 0.0010817 + 0.0010062 + 0.0020358 = 0.0041237,
     *    허용차 0.0012371 → 피치경 0.3371632 ~ 0.3412869 in(H28 표 2.21 3/8-20 UN-2A 0.3372 ~ 0.3413), 바깥지름 0.3656196 ~ 0.3737629(표 0.3657 ~ 0.3738).
     *    목표 80%: 피치경 0.3379879 in = 8.584894 mm, 바깥지름 0.3672483 in = 9.328106 mm → X = 8.584894 − 0.733235 = 7.851659 → X7.852,
     *    k = (9.328106 − 7.852)/2 = 0.738053 → 0.738, 시작 X = 0.3737629 × 25.4 + 2 = 11.493577 → X11.494. 깊이 끝 0.718(k − d).
     *  - 3/8-20 UN 2B 안 Z3 → −12: 피치경 0.3425240 ~ 0.3478849 in(표 0.3425 ~ 0.3479), 안지름 공차 0.25p − 0.4p² = 0.0115000
     *    → 안지름 0.3208734 ~ 0.3323734(표 0.321 ~ 0.332). 목표 80%: 피치경 0.3425240 + 0.8 × 1.30 × 0.0041237 = 0.3468127 in = 8.809043 mm,
     *    안지름 0.3208734 + 0.8 × 0.0115 = 0.3300734 in = 8.383865 mm, 인선 R = H/8 → X = 8.809043 + 0.649519 × 1.27 = 9.633932 → X9.634,
     *    k = (9.634 − 8.383865)/2 = 0.625068 → 0.625, 시작 X = 8.150185 − 2 = 6.150185 → X6.15. 깊이 0.5657 · 0.6007 · 0.605(k − d).
     */
    @Test
    fun unefAndUn20HandNumbers() {
        val unef = assertNotNull(G76Calc.calc(G76Input(spec = un("3/4", 20), zStart = 5.0, zEnd = -20.0, firstCut = 0.2, rpm = 600.0)).solution)
        assertEquals(
            listOf(
                "G97S600", "G0X21.016Z20.", "Z5.", "M23", "G92X18.451Z-20.F1.27",
                "X18.285", "X18.158", "X18.051", "X17.957", "X17.871", "X17.793", "X17.72", "X17.65", "X17.58", "X17.51", "X17.44", "X17.409",
                "X17.369", "X17.369", "M24", "G0Z200.",
            ),
            unef.g92,
        )
        val ext = assertNotNull(G76Calc.calc(G76Input(spec = un("3/8", 20), zStart = 5.0, zEnd = -15.0, firstCut = 0.2)).solution)
        assertEquals(
            listOf(
                "G0X11.494Z20.", "Z5.", "M23", "G92X8.928Z-15.F1.27",
                "X8.762", "X8.635", "X8.528", "X8.434", "X8.348", "X8.27", "X8.197", "X8.127", "X8.057", "X7.987", "X7.917", "X7.892",
                "X7.852", "X7.852", "M24", "G0Z200.",
            ),
            ext.g92,
        )
        assertEquals(0.738, ext.height, "3/8-20 UN 바깥 k")
        val inn = assertNotNull(G76Calc.calc(G76Input(inner = true, spec = un("3/8", 20), zStart = 3.0, zEnd = -12.0, firstCut = 0.2)).solution)
        assertEquals(ThreadClass.UN2B, inn.limits.cls)
        assertEquals(
            listOf(
                "G0X6.15Z20.", "Z3.", "M23", "G92X8.784Z-12.F1.27",
                "X8.95", "X9.077", "X9.184", "X9.278", "X9.364", "X9.442", "X9.515", "X9.585", "X9.594", "X9.634", "X9.634", "M24", "G0Z200.",
            ),
            inn.g92,
        )
        assertEquals(0.625, inn.height, "3/8-20 UN 안 k")
    }

    /**
     * M23 · M24 = 나사 끝 빼기 켬 · 끔(모달, 처음 켬). 지은 나사(시작점 X10 Z5, G92 끝 Z-25, 리드 1.27)를 설정 '끝 빼기 1 리드' 로 돌린다.
     * 손 계산(GCycles.threadCut — 끝 Z 앞 c = 1 × 1.27 에서 시작해 끝 Z 에서 반경 c 빠짐): 켜면 한 회가 두 토막 —
     * 곧은 토막 끝 Z-25 + 1.27 = Z-23.73, 빼는 토막 끝 Z-25 · X = 회의 X + 2 × 1.27 = X + 2.54(시작점 X10 아래라 자르지 않음). 끄면 한 토막(끝 Z-25 · 회의 X).
     * G92 모드 안의 M 만 줄은 사이클을 한 번 더 돈 **뒤** M 을 실행한다(FF 4.1.6) — M23 줄의 회는 끈 채, M24 줄의 회는 켠 채.
     * G76 의 r 끝 빼기도 같은 모달을 따른다(P011000 = 정삭 1회 · r 1.0L · 날 각 0 — 곧은 절입이라 마지막 회 끝 X4.688, 켜면 X4.688 + 2.54 = X7.228).
     */
    @Test
    fun threadPullOutM23M24() {
        val machine = MachineSetup(g92ChamferLeads = 1.0)
        val c = 1.27
        fun on(g: List<MoveStep>, x: Double, what: String) {
            assertEquals(2, g.size, "$what: 끝 빼기 켬 — 두 토막")
            assertEquals(-25.0 + c, g[0].tip.end.z, 1e-9, "$what 곧은 토막 끝 Z")
            near(x, g[0].tip.end.xDia, "$what 곧은 토막 X")
            assertEquals(-25.0, g[1].tip.end.z, 1e-9, "$what 빼는 토막 끝 Z")
            near(x + 2 * c, g[1].tip.end.xDia, "$what 빼는 토막 끝 X")
        }
        fun off(g: List<MoveStep>, x: Double, what: String) {
            assertEquals(1, g.size, "$what: 끝 빼기 끔 — 한 토막")
            assertEquals(-25.0, g[0].tip.end.z, 1e-9, "$what 끝 Z")
            near(x, g[0].tip.end.xDia, "$what 끝 X")
        }
        // M23/M24 없음 = 전과 같음(설정대로 켬)
        val none = threadGroups(run("M 없음", listOf("G0X10.Z5.", "G92X5.76Z-25.F1.27", "X5.59", "G0Z200."), machine))
        assertEquals(2, none.size)
        on(none[0], 5.76, "M 없음 1회")
        on(none[1], 5.59, "M 없음 2회")
        // M24 뒤는 끔, M23 뒤는 켬 — G92 모드 안의 M 줄은 한 번 더 돈 뒤 M. 줄 번호는 pre 2줄 뒤 3부터
        val lines = listOf(
            "G0X10.Z5.", "M24", "G92X5.76Z-25.F1.27", "X5.59", "M23", "X5.5", "M24", "X5.45", "G0Z200.",
            "M23", "G0X10.Z5.", "G92X5.4Z-25.F1.27", "G0Z200.",
        )
        val g = threadGroups(run("M23 · M24", lines, machine))
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 14), g.map { it.first().line }, "회마다 줄")
        off(g[0], 5.76, "M24 뒤 G92")
        off(g[1], 5.59, "M24 뒤 X")
        off(g[2], 5.59, "M23 줄(돈 뒤 켬)")
        on(g[3], 5.5, "M23 뒤 X")
        on(g[4], 5.5, "M24 줄(돈 뒤 끔)")
        off(g[5], 5.45, "M24 뒤 X")
        on(g[6], 5.4, "다시 M23 뒤 G92")
        // G76 의 r 끝 빼기: 처음(켬)은 첫 줄 r, M24 뒤는 0, M23 뒤는 다시 r — 마지막 회(깊이 k)만 본다
        fun g76Last(m: String?): List<MoveStep> {
            val text = listOfNotNull("G0X10.Z5.", m, "G76P011000Q100R.02", "G76X4.688Z-25.P734Q300F1.27", "G0Z200.")
            return threadGroups(run("G76 $m", text, machine)).last()
        }
        on(g76Last(null), 4.688, "G76 M 없음")
        off(g76Last("M24"), 4.688, "G76 M24 뒤")
        on(g76Last("M23"), 4.688, "G76 M23 뒤")
    }
}
