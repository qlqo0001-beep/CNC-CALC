package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.calc.sim.SimRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 편집기 화면 글 지킴이(`UiGoldenTest` 와 같은 방식): 스크린샷 상태의 줄 표(⛔ · ▲ · ●)와 문제 글, 예제마다 문제 목록,
 * 모든 문제 코드의 화면 글. 바뀌면 실패 — 일부러 바꿨을 때만 `-DupdateGolden=true` 로 다시 쓴다.
 */
class SimGoldenTest {
    private val dir = File("src/jvmTest/resources/golden")
    private val update = System.getProperty("updateGolden") == "true"

    private fun golden(name: String, text: String) {
        val f = File(dir, "$name.txt")
        if (update) {
            dir.mkdirs()
            f.writeText(text)
            return
        }
        assertTrue(f.exists(), "골든 파일이 없다: $f (-DupdateGolden=true 로 만든다)")
        assertEquals(f.readText().replace("\r\n", "\n"), text, "$name: 편집기 글이 골든과 다르다")
    }

    private fun mark(l: IssueLevel) = when (l) {
        IssueLevel.STOP -> "⛔"
        IssueLevel.WARN -> "▲"
        IssueLevel.INFO -> "●"
    }

    private fun issueLine(i: SimIssue) = "${mark(i.level)} ${lineLabel(i.line)} ${i.code}: ${simIssueText(i)}"

    /** 편집기에 보이는 그대로: 줄마다 '번호 표 글', 그 아래 문제 글 */
    private fun editorView(text: String, run: SimRun): String = buildString {
        val byLine = run.issues.filter { it.line > 0 }.groupBy { it.line }
        text.split('\n').forEachIndexed { i, line ->
            val iss = byLine[i + 1].orEmpty()
            val m = iss.minByOrNull { it.level.ordinal }?.let { mark(it.level) } ?: " "
            appendLine("${(i + 1).toString().padStart(3)} $m $line")
            iss.forEach { appendLine("        ${mark(it.level)} ${simIssueText(it)}") }
        }
        appendLine("멈춤: ${run.stop?.let { issueLine(it) } ?: "없음 — 끝까지"}")
        appendLine("문제 띠: ${issueCounts(run.issues).ifEmpty { "(없음)" }}")
    }

    @Test
    fun errorsProgram() {
        val run = GSim.run(SimInput(SimShotsTest.ERRORS, MachineSetup()))
        golden("sim-editor-errors", editorView(SimShotsTest.ERRORS, run))
        // 줄 번호와 함께 멈춘다(설계 0.1)
        assertEquals(6, run.stop?.line)
    }

    @Test
    fun examples() {
        val text = buildString {
            for ((i, e) in exampleList.withIndex()) {
                val run = GSim.run(SimInput(e.text, MachineSetup(), e.tools))
                appendLine("== ${i + 1}. ${e.name} [${e.text.substringBefore(' ')}] — ${e.source}")
                appendLine("   첫 주석: ${ProgramStore.firstComment(e.text)} · ${e.text.count { it == '\n' } + 1}줄 · 멈춤 ${run.stop?.let { lineLabel(it.line) } ?: "없음"}")
                run.issues.forEach { appendLine("   " + issueLine(it)) }
                assertFalse(run.issues.any { it.level == IssueLevel.STOP }, e.name)
            }
        }
        golden("sim-editor-examples", text)
    }

    /** 모든 문제 코드의 화면 글(본보기 낱말 · 값) — 빠진 코드 · 비어 있는 자리('null', 빈 괄호)가 없다 */
    @Test
    fun allIssueTexts() {
        val text = buildString {
            for (code in IssueCode.entries) {
                val i = SimIssue(code, 12, 4..7, sampleWord(code), sampleArgs(code))
                val t = simIssueText(i)
                assertTrue(t.isNotBlank() && "null" !in t && "()" !in t && "  " !in t.trim(), "$code: '$t'")
                appendLine("${mark(code.level)} $code: $t")
            }
            // 보정 시작 · 취소 형식 A · B 설정의 글(args 없음 — 위는 처음 값 형식 C 의 글)
            for (code in listOf(IssueCode.COMP_START_ZERO, IssueCode.COMP_CANCEL_ALONE, IssueCode.COMP_TYPE_A)) {
                appendLine("${mark(code.level)} $code(A · B): ${simIssueText(SimIssue(code, 12))}")
            }
            // G41 ↔ G42 바꿈의 다른 갈래(바꾼 바로 다음 취소 · 원호 끝에서 두 쪽 보정선이 안 만남)
            for (a in listOf("G40", "11")) {
                appendLine("${mark(IssueLevel.STOP)} COMP_SWITCH($a): ${simIssueText(SimIssue(IssueCode.COMP_SWITCH, 12, args = listOf(a)))}")
            }
            // 다이스 경로 멈춤의 낱말 없는 갈래(앞 블록에서 X 를 옮긴 뒤 G01 — 이 블록에 X 가 없음)
            val diePath = simIssueText(SimIssue(IssueCode.DIE_PATH, 12))
            assertTrue(!diePath.startsWith(":") && "null" !in diePath, diePath)
            appendLine("${mark(IssueLevel.STOP)} DIE_PATH(낱말 없음): $diePath")
        }
        golden("sim-issue-texts", text)
    }

    private fun sampleWord(c: IssueCode): String? = when (c) {
        IssueCode.BAD_CHAR -> "@"
        IssueCode.NO_NUMBER -> "X"
        IssueCode.DUP_ADDRESS, IssueCode.ABS_INC_BOTH -> "X41."
        IssueCode.DUP_GROUP -> "G01"
        IssueCode.TWO_M -> "M05"
        IssueCode.UNKNOWN_ADDRESS -> "D1."
        IssueCode.HAAS_FORMAT -> "D0.1"
        IssueCode.G01_CORNER_IKR -> "R1."
        IssueCode.UNSUPPORTED_G, IssueCode.CYCLE_ONESHOT -> "G32"
        IssueCode.WORK_OFFSET -> "G55"
        IssueCode.G50_COORD -> "X100."
        IssueCode.G80_IN_CYCLE -> "G80"
        IssueCode.DECIMAL_NOT_ALLOWED -> "Q3."
        IssueCode.NO_POINT_FEED -> "F2"
        IssueCode.NO_POINT_COORD -> "X44"
        IssueCode.T_FORMAT -> "T1"
        IssueCode.BAD_VALUE -> "F-1."
        IssueCode.WORD_NOT_USED -> "R1."
        IssueCode.G04_MIXED -> "X1."
        IssueCode.RPM_OVER_LIMIT -> "S5000"
        IssueCode.T_WITH_FEED -> "T0202"
        IssueCode.COMP_TOOL_CHANGE -> "T0202"
        IssueCode.CORNER_C_ARC, IssueCode.CORNER_TOO_BIG, IssueCode.CORNER_NO_VERTEX, IssueCode.CORNER_NO_NEXT, IssueCode.CORNER_ON_RAPID -> ",C1."
        IssueCode.CYCLE_PQ_FORBIDDEN -> "G04"
        IssueCode.CYCLE_BLOCK_TM -> "M08"
        IssueCode.CYCLE_REPEAT_MST -> "M05"
        IssueCode.G7X_RELIEF -> "R0.1"
        IssueCode.G73_COUNT -> "R1"
        IssueCode.M98_IN_PQ -> "M98"
        IssueCode.MACHINE_M -> "M71" // M23 · M24 는 나사 끝 빼기로 아는 코드가 됐다(설계 18.8)
        IssueCode.DUP_PROGRAM -> "O1001"
        IssueCode.NUMBER_TOO_LONG, IssueCode.NUMBER_TOO_BIG -> "X1234567890"
        IssueCode.MID_BLOCK_SKIP -> "/"
        IssueCode.DIE_PATH -> "X2."
        IssueCode.DIE_IN_CYCLE -> "G92"
        IssueCode.DIE_FEED_OVER_PITCH -> "F1.8"
        else -> null
    }

    private fun sampleArgs(c: IssueCode): List<String> = when (c) {
        IssueCode.DUP_GROUP -> listOf("G00")
        IssueCode.NO_POINT_COORD -> listOf("0.044")
        IssueCode.ABS_INC_BOTH, IssueCode.G71_NOT_MONOTONE -> listOf("X")
        IssueCode.WORD_MISSING -> listOf("Z")
        IssueCode.NO_CLAMP_G96, IssueCode.RPM_OVER_LIMIT -> listOf("4000")
        IssueCode.ARC_R_SHORT, IssueCode.ARC_END_OFF -> listOf("0.012")
        IssueCode.COMP_ACUTE_OUTER, IssueCode.COMP_INTERFERENCE, IssueCode.COMP_CANCEL_NO_MOVE, IssueCode.COMP_START_ZERO -> listOf("11")
        IssueCode.COMP_CANCEL_ALONE, IssueCode.COMP_TYPE_A -> listOf("C")
        IssueCode.COMP_ARC_SMALL -> listOf("0.3", "0.4")
        IssueCode.TOOL_DATA_MISSING -> listOf("1", "1")
        IssueCode.CYCLE_PQ_FORBIDDEN, IssueCode.CYCLE_FIRST_LINE, IssueCode.CYCLE_PQ_BEFORE, IssueCode.G7X_P_BLOCK, IssueCode.M98_IN_PQ -> listOf("G71")
        IssueCode.CYCLE_REPEAT_MST, IssueCode.CYCLE_ONESHOT -> listOf("G90")
        IssueCode.CYCLE_PQ_NOT_FOUND -> listOf("Q", "N25")
        IssueCode.CYCLE_PQ_DUP -> listOf("N10")
        IssueCode.G7X_SIGN -> listOf("U")
        IssueCode.SUB_NOT_FOUND -> listOf("N77")
        IssueCode.SUB_SKIPPED -> listOf("9000")
        IssueCode.SUB_NEST -> listOf("4")
        IssueCode.SUB_NO_M99 -> listOf("O2000")
        IssueCode.DIE_IN_CYCLE -> listOf("G92")
        IssueCode.DIE_DATA_MISSING -> listOf("5", "5")
        IssueCode.DIE_FEED_OVER_PITCH -> listOf("1.8", "1.75")
        else -> emptyList()
    }
}
