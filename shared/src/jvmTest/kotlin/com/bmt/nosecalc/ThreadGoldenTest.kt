package com.bmt.nosecalc

import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.screens.resultCells
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.g76Words
import com.bmt.nosecalc.screens.thread.ncText
import com.bmt.nosecalc.screens.thread.threadBadWhileWaiting
import com.bmt.nosecalc.screens.thread.threadFormula
import com.bmt.nosecalc.screens.thread.threadMetricInner
import com.bmt.nosecalc.screens.thread.threadOriginal
import com.bmt.nosecalc.screens.thread.threadReverse
import com.bmt.nosecalc.screens.thread.threadWarn
import com.bmt.nosecalc.screens.thread.threadWarningText
import com.bmt.nosecalc.screens.thread.threadZOrder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 나사 G76 화면의 숫자 · NC 지킴이(UiGoldenTest 와 같은 방식 · 같은 golden 폴더의 thread-*.txt). 스크린샷 상태마다 화면과 똑같이
 * 계산기 입력을 만들어 경고 글 · 한계 치수 · 목표 · G76 두 줄(화면 낱말을 이은 글 = 계산기 글) · 분석표 · 프로그램(G76 · G92 다회 · 다듬질 블록) · 결과 칸을 적는다.
 * 일부러 바꿨을 때만 다시 쓴다: `gradlew :shared:jvmTest --tests '*ThreadGoldenTest*' -DupdateGolden=true`
 */
class ThreadGoldenTest {
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
    fun thread() {
        thread("thread-empty", ThreadState())
        thread("thread", threadOriginal())
        thread("thread-metric-inner", threadMetricInner())
        thread("thread-formula", threadFormula())
        thread("thread-warn", threadWarn())
        thread("thread-zorder", threadZOrder())
        thread("thread-reverse", threadReverse())
        thread("thread-bad-waiting", threadBadWhileWaiting())
    }

    private fun thread(name: String, s: ThreadState) {
        val r = G76Calc.calc(s.input())
        val sol = r.solution
        val words = g76Words(s, sol)
        if (sol != null) assertEquals(sol.nc, words.map(::ncText), "$name: 화면의 G76 낱말이 NC 글과 다르다")
        val text = buildString {
            line("${if (s.inner) "내경" else "외경"} · ${s.spec?.label ?: "사이즈 없음"} · ${s.shownClass.label} · ${s.dir} · a${s.shownAngle} · r${s.shownChamfer}")
            line("경고 ${r.warnings}")
            r.warnings.forEach { line("  ${threadWarningText(it, s, sol)}") }
            if (s.bad().isNotEmpty()) line("범위 밖 ${s.bad()}")
            words.forEach { line("화면 ${ncText(it)}") }
            if (sol == null) return@buildString
            val lim = sol.limits
            line("H ${f(lim.h)} 기본 피치경 ${f(lim.basicPitch)} 기본 안지름 ${f(lim.basicMinor)} 편차 ${f(lim.deviation)}")
            line("피치경 ${f(lim.pitchDia.min)} ~ ${f(lim.pitchDia.max)} · 산봉우리 ${f(lim.crest.min)} ~ ${f(lim.crest.max)} · 골 한계 ${f(lim.rootLimit)} · 식 ${lim.byFormula}")
            line("목표 ${sol.target}% · 인선 R ${f(sol.tipR)} · 피치경 ${f(sol.pitchDia)} · 산봉우리 ${f(sol.crestDia)} · X ${f(sol.x)} · k ${f(sol.height)} · 시작 X ${f(sol.startX)}")
            sol.passes.forEach { p -> line("  ${p.n} 깊이 ${f(p.depth)} 절입 ${f(p.cut)} X ${f(p.xDia)}${if (p.finish) " 정삭" else ""}") }
            sol.program.forEach { line("NC $it") }
            sol.g92.forEach { line("G92 $it") }
            sol.g92Finish.forEach { line("다듬질 $it") }
            line("분당 이송 ${sol.feedPerMin?.let(::f)}")
            resultCells(s, sol).forEach { (a, b, c) -> line("칸 $a = $b ($c)") }
        }
        golden(name, text)
    }

    private fun StringBuilder.line(s: String) = append(s).append('\n')
    private fun f(v: Double) = Fmt.fixed3(v)
}
