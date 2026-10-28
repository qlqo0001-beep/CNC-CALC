package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Pt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 시뮬레이터 테스트 공용 — 글을 줄 목록으로 받아 돌린다 */
internal object SimKit {
    /** 보통 공구 1번: 0.4R · 팁 3(외경) */
    val OD = mapOf(1 to ToolOffset(0.4, 3))

    fun run(lines: List<String>, tools: Map<Int, ToolOffset> = OD, machine: MachineSetup = MachineSetup()): SimRun =
        GSim.run(SimInput(lines.joinToString("\n"), machine, tools))

    fun ok(lines: List<String>, tools: Map<Int, ToolOffset> = OD, machine: MachineSetup = MachineSetup()): SimRun {
        val r = run(lines, tools, machine)
        assertNull(r.stop, "멈춤: ${r.stop}\n${r.issues}")
        return r
    }

    /** 멈춘 문제가 code 이고 그 줄이 line 이어야 */
    fun stops(lines: List<String>, code: IssueCode, line: Int? = null, tools: Map<Int, ToolOffset> = OD, machine: MachineSetup = MachineSetup()): SimRun {
        val r = run(lines, tools, machine)
        val s = assertNotNull(r.stop, "멈춰야 한다: $code — ${r.issues}")
        assertEquals(code, s.code, "멈춘 문제 ${r.stop}")
        if (line != null) assertEquals(line, s.line, "멈춘 줄 ${r.stop}")
        return r
    }

    fun moves(r: SimRun): List<MoveStep> = r.steps.filterIsInstance<MoveStep>()

    /** 그 줄의 마지막 고스트 */
    fun ghost(r: SimRun, line: Int): Ghost = r.ghosts.last { it.line == line }

    /** 직경 X · Z 를 0.001 글로("X44.000 Z-29.234") */
    fun xz(p: Pt): String = "X${Fmt.fixed3(p.xDia)} Z${Fmt.fixed3(p.z)}"

    fun near(a: Pt, b: Pt, tol: Double = 1e-9, msg: String = "") =
        assertTrue((a - b).length <= tol, "$msg 기대 ${xz(a)} (${a}), 실제 ${xz(b)} (${b}) 차이 ${(a - b).length}")

    fun has(r: SimRun, code: IssueCode): Boolean = r.issues.any { it.code == code }

    /** 앞에 붙이는 준비 줄(공구 · 회전 · 이송) */
    val PRE = listOf("T0101", "G97 S1000 M03", "G99")
}
