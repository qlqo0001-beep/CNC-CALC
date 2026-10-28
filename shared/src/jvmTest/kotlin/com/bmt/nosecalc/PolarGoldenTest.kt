package com.bmt.nosecalc

import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarProgram
import com.bmt.nosecalc.calc.PolarSolution
import com.bmt.nosecalc.screens.minSec
import com.bmt.nosecalc.screens.polar.PolarField
import com.bmt.nosecalc.screens.polar.PolarState
import com.bmt.nosecalc.screens.polar.ncTokens
import com.bmt.nosecalc.screens.polar.polarBadWhileWaiting
import com.bmt.nosecalc.screens.polar.polarInnerSmallR
import com.bmt.nosecalc.screens.polar.polarInnerSquare
import com.bmt.nosecalc.screens.polar.polarOriginal07
import com.bmt.nosecalc.screens.polar.polarOriginal12
import com.bmt.nosecalc.screens.polar.polarResultCells
import com.bmt.nosecalc.screens.polar.polarTriangle
import com.bmt.nosecalc.screens.polar.polarTwoFlat
import com.bmt.nosecalc.screens.polar.polarWarningText
import com.bmt.nosecalc.screens.polar.polarWgeOd
import com.bmt.nosecalc.screens.polar.simPlan
import com.bmt.nosecalc.screens.previewLine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 극좌표 화면의 숫자 · NC 지킴이(UiGoldenTest 와 같은 방식 · 같은 golden 폴더의 polar-*.txt). 스크린샷 상태마다 화면과 똑같이
 * 계산기 입력을 만들어 경고 글 · 결과 칸(두 보정 방식) · 키패드 한 줄 · 두 프로그램(시뮬레이션 목록의 흐린 좌표 포함) · 이송 시간을 적는다.
 * 일부러 바꿨을 때만 다시 쓴다: `gradlew :shared:jvmTest --tests '*PolarGoldenTest*' -DupdateGolden=true`
 */
class PolarGoldenTest {
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
        assertEquals(f.readText().replace("\r\n", "\n"), text, "$name: 화면 숫자·NC 가 골든과 다르다")
    }

    @Test
    fun polar() {
        polar("polar-empty", PolarState())
        polar("polar", polarOriginal12())
        polar("polar-07", polarOriginal07())
        polar("polar-inner", polarInnerSquare())
        polar("polar-inner-small-r", polarInnerSmallR())
        polar("polar-two-flat", polarTwoFlat())
        polar("polar-triangle", polarTriangle())
        polar("polar-error", polarWgeOd())
        polar("polar-bad-waiting", polarBadWhileWaiting())
    }

    private fun polar(name: String, s: PolarState) {
        val r = PolarG12.calc(s.input())
        val sol = r.solution
        val text = buildString {
            line("${if (s.inner) "내측" else "외측"} · ${s.shape} · X ${if (s.xDiameter) "직경" else "반경"} · " + PolarField.entries.joinToString(" ") { "${it.key}=${s.value(it)}" })
            line("경고 ${r.warnings}")
            r.warnings.forEach { line("  ${polarWarningText(it, s, sol)}") }
            if (s.bad().isNotEmpty()) line("범위 밖 ${s.bad()}")
            if (sol == null) return@buildString
            line("한 줄 ${previewLine(s, sol)}")
            for (direct in listOf(false, true)) {
                polarResultCells(s, sol, direct).forEach { (a, b, c) -> line("${if (direct) "G40" else "G41"} 칸 $a = $b ($c)") }
            }
            program("G41", sol.ncG41G42, s.num(PolarField.FEED)!!, sol)
            program("G40", sol.ncG40, s.num(PolarField.FEED)!!, sol)
        }
        golden(name, text)
    }

    /** 프로그램 줄(복사 글)과 시뮬레이션 목록에 보이는 줄(흐린 좌표는 [ ])이 다를 때만 '→' 뒤에 */
    private fun StringBuilder.program(name: String, p: PolarProgram, feed: Double, sol: PolarSolution) {
        p.blocks.forEach { b ->
            val shown = ncTokens(b).joinToString(" ") { if (it.dim) "[${it.text}]" else it.text }
            line("$name ${b.text}" + if (shown != b.text) "   → $shown" else "")
        }
        val plan = simPlan(sol, p, feed)
        line("$name 이송 시간 ${Fmt.fixed3(plan.feedSeconds)} 초 (${minSec(plan.feedSeconds)})")
    }

    private fun StringBuilder.line(s: String) = append(s).append('\n')
}
