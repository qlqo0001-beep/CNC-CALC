package com.bmt.nosecalc.calc.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 편집기 '목록' 의 예제(설계 7.3 · 9.3): 모두 끝까지 돌고 STOP 문제가 없다 */
class ExamplesTest {
    @Test
    fun allExamplesRunWithoutStop() {
        assertEquals(14, SimExamples.all.size)
        for (e in SimExamples.all) {
            val r = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
            assertNull(r.stop, "${e.name}: 멈춤 ${r.stop}")
            assertTrue(r.issues.none { it.level == IssueLevel.STOP }, "${e.name}: ${r.issues}")
            assertTrue(r.steps.any { it is MoveStep }, "${e.name}: 이동이 없다")
            // 회사 꼴 예제는 주 프로그램 M99 로 끝난다(설계 18.5)
            assertTrue((r.steps.last() as? EndStep)?.m in setOf(30, 99), "${e.name}: M30 · M99 로 끝나야")
        }
    }

    /**
     * 회사 꼴 예제(O9020~ — 설계 18.5): 보정 시작 · 취소 형식 A · B 와 C 에서 깎는 줄(이송 블록)의 노즈 경로가 같다 —
     * 다른 곳은 혼자 줄 G41/G42 · G40 둘레(공기)뿐. 외경 예제의 'G40 바로 G41' 은 형식 A · B 에서 멈추므로(COMP_CANCEL_NO_MOVE) 그 앞까지 견준다
     */
    @Test
    fun companyStyleCutsSameInTypeAAndC() {
        val company = SimExamples.all.filter { it.text.startsWith("O902") }
        assertEquals(3, company.size)
        for (e in company) {
            val c = GSim.run(SimInput(e.text, MachineSetup(compStartup = CompStartup.C), e.tools))
            val a = GSim.run(SimInput(e.text, MachineSetup(compStartup = CompStartup.AB), e.tools))
            assertNull(c.stop, e.name)
            val until = a.stop?.let { assertEquals(IssueCode.COMP_CANCEL_NO_MOVE, it.code, e.name); it.line } ?: Int.MAX_VALUE
            fun cuts(r: SimRun) = r.steps.filterIsInstance<MoveStep>()
                .filter { it.kind != MoveKind.RAPID && it.line < until && (it.src == StepSrc.BLOCK || it.src == StepSrc.CORNER || it.src == StepSrc.CYCLE_CUT) }
            val ca = cuts(a)
            val cc = cuts(c)
            assertTrue(ca.size >= 4, "${e.name}: 깎는 줄 ${ca.size}")
            assertEquals(ca.map { it.line }, cc.map { it.line }, e.name)
            for ((p, q) in ca.zip(cc)) {
                for ((u, v) in listOf(p.nose.start to q.nose.start, p.nose.end to q.nose.end)) {
                    assertTrue((u - v).length < 1e-9, "${e.name} ${p.line}줄: 형식 A · B $u / C $v")
                }
            }
        }
    }

    /** 첫 줄 = O 번호 + 주석(목록의 이름), 번호는 서로 다르고, 편집기 입력처럼 대문자(주석 밖) */
    @Test
    fun examplesHaveNumberAndComment() {
        val numbers = SimExamples.all.map { GText.parse(it.text).units.first().number }
        assertTrue(numbers.all { it != null }, "$numbers")
        assertEquals(numbers.size, numbers.toSet().size)
        for (e in SimExamples.all) {
            val first = e.text.lineSequence().first()
            assertTrue(Regex("""^O\d{4} \(.+\)$""").matches(first), first)
            val code = e.text.replace(Regex("""\([^)]*\)"""), "")
            assertEquals(code.uppercase(), code, e.name)
        }
    }

    /** 부프로그램 예제: O9012 를 세 번 — 홈 Z −10 · −18 · −26 */
    @Test
    fun subprogramExampleCutsThreeGrooves() {
        val e = SimExamples.all.first { it.text.startsWith("O9011") }
        val r = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
        val grooves = r.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.FEED }.map { it.tip.end.z }
        assertEquals(listOf(-10.0, -18.0, -26.0), grooves.map { kotlin.math.round(it * 1000) / 1000 })
    }
}
