package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieInput
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.ThreadTable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 다이스 헤드(DIE 공구 — 설계 19절). 제어기 동작(G1 이송 · G4 · G0)은 정확, 나사 모양은 공구 설정(d · P · 각)에서 온 모형.
 * 예제 ⑮(O9023 — 지은 치수: M12×1.75 · 다이스 d 12 · P 1.75 · 60°, S230, Z6 → −20, F1.68 = 0.96 × 1.75)와 멈춤 · 알림 꼴.
 * 기대값은 손 계산(식과 수를 주석에).
 */
class DieHeadSimTest {
    private val ex = SimExamples.all.single { it.text.startsWith("O9023") }
    private val die = mapOf(5 to ToolOffset(kind = ToolKind.DIE, dieDia = 12.0, diePitch = 1.75))
    private val pre = listOf("T0505", "G97S230M03", "G0X0.Z20.", "Z6.")

    private fun lineOf(text: String, s: String): Int = text.lines().indexOf(s).also { assertTrue(it >= 0, s) } + 1

    private fun near(e: Double, a: Double, what: String, tol: Double = 1e-9) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    /**
     * ⑮: 멈춤 0(문제는 꼬리 M99 안내뿐). 다이스 G1 = 이송(FEED) · DIE_CUT, F1.68 회전당, 회전수 230, X0 에서 Z6 → Z−20.
     * 시간(손 계산): 길이 6 − (−20) = 26, 분당 이송 1.68 × 230 = 386.4 → 60 × 26 / 386.4 = 4.037267080745342 초,
     * 바로 뒤 G4U1. = 1 초 → G1 처음 ~ G4 끝 = 5.037267080745342 초. 그다음 G0Z150. 은 X0 에서 Z 로만.
     * (계산기 DieSolution.feed 는 반올림 전 값 0.96 × P — 여기서는 1.68 이 딱 떨어져 NC F 와 같다. 시뮬레이터는 늘 NC 의 F 글 값으로 잰다)
     */
    @Test
    fun exampleCutDwellRetract() {
        val r = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        assertNull(r.stop, "${r.stop}")
        assertEquals(listOf(IssueCode.M99_MAIN), r.issues.map { it.code })
        val g1Line = lineOf(ex.text, "G1Z-20.F1.68")
        val g1 = r.steps.filterIsInstance<MoveStep>().single { it.src == StepSrc.DIE_CUT }
        assertEquals(g1Line, g1.line)
        assertEquals(MoveKind.FEED, g1.kind)
        assertEquals(1.68, g1.feed)
        assertTrue(g1.perRev)
        assertEquals(230.0, g1.rpm0)
        assertEquals(230.0, g1.rpm1)
        assertEquals(5, g1.offset)
        assertEquals(DieCut(12.0, 1.75, 60.0), g1.die)
        assertEquals(Line(Pt(6.0, 0.0), Pt(-20.0, 0.0)), g1.tip)
        near(60 * 26 / (1.68 * 230), g1.t1 - g1.t0, "G1 시간")
        near(4.037267080745342, g1.t1 - g1.t0, "G1 시간(수)")
        // G1 블록 끝의 상태 걸음(F 가 바뀜 — 시간 0) 뒤 바로 드웰
        val i = r.steps.indexOf(g1)
        assertTrue(r.steps.subList(i + 1, r.steps.indexOfFirst { it is DwellStep }).all { it is StateStep && it.t0 == g1.t1 })
        val dwell = r.steps.filterIsInstance<DwellStep>().single()
        assertEquals(lineOf(ex.text, "G4U1."), dwell.line)
        assertEquals(1.0, dwell.sec)
        assertEquals(g1.t1, dwell.t0)
        near(5.037267080745342, dwell.t1 - g1.t0, "G1 + G4")
        val back = r.steps.subList(r.steps.indexOf(dwell), r.steps.size).filterIsInstance<MoveStep>().first()
        assertEquals(lineOf(ex.text, "G0Z150."), back.line)
        assertEquals(MoveKind.RAPID, back.kind)
        assertEquals(StepSrc.BLOCK, back.src)
        assertEquals(Pt(150.0, 0.0), back.tip.end)
        // 다이스 공구의 급송은 보통 급송(DIE_CUT 은 G01 만)
        assertTrue(r.steps.filterIsInstance<MoveStep>().filter { it.offset == 5 && it.kind == MoveKind.RAPID }.all { it.src == StepSrc.BLOCK && it.die == null })
    }

    /** ⑮ 의 다이스 줄 = 나사 화면 계산기(DieHeadCalc) 글(공구 줄 T0505M03 만 끼움) — 계산기 글이 바뀌면 예제도 같이 고친다 */
    @Test
    fun exampleIsCalculatorText() {
        val spec = ThreadTable.METRIC.single { it.size == "M12" && it.pitch == 1.75 }
        val s = assertNotNull(DieHeadCalc.calc(DieInput(spec = spec, rpm = 230.0, zStart = 6.0, zEnd = -20.0)).solution)
        // 처음 값(사용자 결정 2026-11-05): 이송 0.96P = 1.68 · 멈춤 1초 · 진입 Z20 · 물러남 Z150
        assertEquals(listOf("G97S230", "G0X0.Z20.", "Z6.", "G1Z-20.F1.68", "G4U1.", "G0Z150."), s.program)
        val lines = ex.text.lines()
        val from = lines.indexOf(s.program.first())
        assertEquals(s.program.take(1) + "T0505M03" + s.program.drop(1), lines.subList(from, from + s.program.size + 1))
    }

    /**
     * 나사 특징(모형): 리드 = 다이스 피치 1.75(F 1.68 이 아님) · 60° · 바깥, 골 = 수평선 반경 d/2 − 0.613435P = 6 − 1.07351125 = 4.92648875,
     * Z 는 G1 의 시작 ~ 끝(6 → −20). 다이스는 래스터를 깎지 않는다 — 바이트가 깎은 Ø11.8(반경 5.9)이 그대로. 자동 소재에서 닿음 없음.
     * G1 이 시작하기 전에는 나사가 없다.
     */
    @Test
    fun threadFeatureFromToolSettings() {
        val r = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        val spec = assertNotNull(StockAuto.of(r))
        val s = StockSim(spec, r, ex.tools)
        s.cutTo(r.totalSec)
        assertTrue(s.contacts.isEmpty(), "${s.contacts}")
        val th = s.profile().threads.single()
        assertEquals(1.75, th.lead)
        assertEquals(60.0, th.angleDeg)
        assertTrue(th.outer)
        assertEquals(lineOf(ex.text, "G1Z-20.F1.68"), th.line)
        near(6.0, th.root.start.z, "골 시작 Z")
        near(-20.0, th.root.end.z, "골 끝 Z")
        near(4.92648875, th.root.start.x, "골 반경(시작)")
        near(4.92648875, th.root.end.x, "골 반경(끝)")
        near(5.9, assertNotNull(s.outerAt(-10.0)), "Z−10 소재 반경")
        val g1 = r.steps.filterIsInstance<MoveStep>().single { it.src == StepSrc.DIE_CUT }
        s.cutTo(g1.t0 - 1e-6)
        assertTrue(s.profile().threads.isEmpty())
    }

    /** 멈추는 꼴 다섯(설계 19절 — 그 경로 · 동작은 지원 안 함): X ≠ 0 · +Z · 원호 · G98 · 고정 사이클 */
    @Test
    fun stopForms() {
        // X ≠ 0: 시작이 X10(급송은 받음) · 끝이 X2
        SimKit.stops(listOf("T0505", "G97S230M03", "G0X10.Z6.", "G1Z-20.F1.68", "M30"), IssueCode.DIE_PATH, 4, die)
        val endX = SimKit.stops(pre + listOf("G1X2.Z-20.F1.68", "M30"), IssueCode.DIE_PATH, 5, die)
        assertEquals("X2.", endX.stop?.word)
        // +Z(끝 Z ≥ 시작 Z)
        assertEquals("Z10.", SimKit.stops(pre + listOf("G1Z10.F1.68", "M30"), IssueCode.DIE_PATH, 5, die).stop?.word)
        // 원호(X0 → X0 이어도) · 모서리
        SimKit.stops(pre + listOf("G2Z-20.R20.F1.68", "M30"), IssueCode.DIE_PATH, 5, die)
        SimKit.stops(pre + listOf("G1Z-10.F1.68,C1.", "X4.", "M30"), IssueCode.DIE_PATH, 5, die)
        // G98(분당 이송)
        SimKit.stops(listOf("T0505", "G97S230M03", "G98", "G0X0.Z6.", "G1Z-20.F386.", "M30"), IssueCode.DIE_NOT_PER_REV, 5, die)
        // 고정 사이클: G92 · G74(첫 줄은 모달 값만이라 받고 둘째 줄에서)
        val g92 = SimKit.stops(pre + listOf("G92X0.Z-20.F1.75", "M30"), IssueCode.DIE_IN_CYCLE, 5, die)
        assertEquals(listOf("G92"), g92.stop?.args)
        SimKit.stops(pre + listOf("G74R1.", "G74Z-20.Q5000F.1", "M30"), IssueCode.DIE_IN_CYCLE, 6, die)
        // 멈춘 블록 앞까지는 그린다 — 마지막 이동은 Z6. 급송
        assertEquals(Pt(6.0, 0.0), SimKit.moves(endX).last().tip.end)
    }

    /** 같은 글을 나사 공구(THREAD)나 보정 번호 00 으로 돌리면 보통 G01(BLOCK) — 다이스 규칙은 DIE 공구만 */
    @Test
    fun onlyDieToolGetsDieRules() {
        val text = pre + listOf("G1X2.Z-20.F1.68", "M30")
        val th = SimKit.ok(text, mapOf(5 to ToolOffset(kind = ToolKind.THREAD, dieDia = 12.0, diePitch = 1.75)))
        assertTrue(SimKit.moves(th).none { it.src == StepSrc.DIE_CUT || it.die != null })
        val noOffset = SimKit.ok(listOf("T0500") + pre.drop(1) + listOf("G1Z-20.F1.68", "M30"), die)
        assertTrue(SimKit.moves(noOffset).none { it.src == StepSrc.DIE_CUT })
    }

    /**
     * 알림(돌기는 계속): 다이스 지름 · 피치가 비거나 나사 각이 60° · 55° 가 아니면 DIE_DATA_MISSING(경로만 — 나사 모양 없음, args = 공구 · 보정),
     * F > P 면 DIE_FEED_OVER_PITCH(args = F · P). F = P 는 알림 없음. 55° 는 받는다(골 0.640327P).
     */
    @Test
    fun warnForms() {
        val text = pre + listOf("G1Z-20.F1.68", "G4U1.", "G0Z150.", "M30")
        for (t in listOf(ToolOffset(kind = ToolKind.DIE), ToolOffset(kind = ToolKind.DIE, dieDia = 12.0), ToolOffset(kind = ToolKind.DIE, dieDia = 12.0, diePitch = 1.75, threadDeg = 29.0))) {
            val r = SimKit.ok(text, mapOf(5 to t))
            val w = r.issues.single { it.code == IssueCode.DIE_DATA_MISSING }
            assertEquals(5, w.line)
            assertEquals(IssueLevel.WARN, w.level)
            assertEquals(listOf("5", "5"), w.args)
            val g1 = SimKit.moves(r).single { it.src == StepSrc.DIE_CUT }
            assertNull(g1.die, "$t")
            val s = StockSim(StockSpec(14.0, 40.0, 0.0, 0.0, 100.0), r, mapOf(5 to t)).also { it.cutTo(r.totalSec) }
            assertTrue(s.profile().threads.isEmpty(), "$t")
        }
        val p55 = SimKit.ok(text, mapOf(5 to ToolOffset(kind = ToolKind.DIE, dieDia = 12.0, diePitch = 1.75, threadDeg = 55.0)))
        assertTrue(p55.issues.none { it.code == IssueCode.DIE_DATA_MISSING })
        assertEquals(DieCut(12.0, 1.75, 55.0), SimKit.moves(p55).single { it.src == StepSrc.DIE_CUT }.die)

        val over = SimKit.ok(pre + listOf("G1Z-20.F1.8", "M30"), die)
        val o = over.issues.single { it.code == IssueCode.DIE_FEED_OVER_PITCH }
        assertEquals(IssueLevel.WARN, o.level)
        assertEquals("F1.8", o.word)
        assertEquals(listOf("1.8", "1.75"), o.args)
        assertTrue(SimKit.ok(pre + listOf("G1Z-20.F1.75", "M30"), die).issues.none { it.code == IssueCode.DIE_FEED_OVER_PITCH })
    }
}
