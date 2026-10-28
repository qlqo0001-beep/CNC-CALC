package com.bmt.nosecalc.calc.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 편집기 '목록' 의 예제(설계 7.3 · 9.3): 모두 끝까지 돌고 STOP 문제가 없다 */
class ExamplesTest {
    @Test
    fun allExamplesRunWithoutStop() {
        assertEquals(11, SimExamples.all.size)
        for (e in SimExamples.all) {
            val r = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
            assertNull(r.stop, "${e.name}: 멈춤 ${r.stop}")
            assertTrue(r.issues.none { it.level == IssueLevel.STOP }, "${e.name}: ${r.issues}")
            assertTrue(r.steps.any { it is MoveStep }, "${e.name}: 이동이 없다")
            assertTrue(r.steps.last() is EndStep, "${e.name}: M30 으로 끝나야")
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
