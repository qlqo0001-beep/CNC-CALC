package com.bmt.nosecalc

import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.screens.resultCells
import com.bmt.nosecalc.screens.thread.DieStore
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.chaserRows
import com.bmt.nosecalc.screens.thread.chaserSources
import com.bmt.nosecalc.screens.thread.dieBad
import com.bmt.nosecalc.screens.thread.dieCatalogFeedText
import com.bmt.nosecalc.screens.thread.dieResultCells
import com.bmt.nosecalc.screens.thread.dieSpeedNote
import com.bmt.nosecalc.screens.thread.dieWarningText
import com.bmt.nosecalc.screens.thread.speedShort
import com.bmt.nosecalc.screens.thread.threadDie
import com.bmt.nosecalc.screens.thread.threadDieEmpty
import com.bmt.nosecalc.screens.thread.threadDieStore
import com.bmt.nosecalc.screens.thread.g76Words
import com.bmt.nosecalc.screens.thread.gaugeRows
import com.bmt.nosecalc.screens.thread.gaugeSource
import com.bmt.nosecalc.screens.thread.kindText
import com.bmt.nosecalc.screens.thread.leadText
import com.bmt.nosecalc.screens.thread.ncText
import com.bmt.nosecalc.screens.thread.pipeResultCells
import com.bmt.nosecalc.screens.thread.pitchRow
import com.bmt.nosecalc.screens.thread.specTitle
import com.bmt.nosecalc.screens.thread.thread20Un
import com.bmt.nosecalc.screens.thread.threadNptExt
import com.bmt.nosecalc.screens.thread.threadNptInt
import com.bmt.nosecalc.screens.thread.threadPtExt
import com.bmt.nosecalc.screens.thread.threadPtInt
import com.bmt.nosecalc.screens.thread.threadUnef
import com.bmt.nosecalc.screens.thread.unSizes
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

    /**
     * 나사 확장(2026-11-05): UNEF · 20UN 묶음(곧은 나사 — 위와 같은 글 + 이름 · 묶음 · 사이즈 시트 차례 · 피치 시트 줄)과 관용 테이퍼 넷(PT · NPT 숫 · 암 —
     * 게이지 표 · 끝 골 X · I · 시작 X · 분석표 · G92 테이퍼 · 다듬질 · 결과 칸). 위 8개는 이 함수와 따로라 바이트 그대로다
     */
    @Test
    fun extension() {
        val un = { s: ThreadState ->
            listOf(
                "이름 ${s.spec?.let(::specTitle)} · 묶음 ${s.unGroup.label}",
                "사이즈 시트 ${unSizes(s.unGroup).joinToString(" ")}",
            ) + s.spec?.let { sp -> ThreadTable.pitches(ThreadKind.UN, sp.size).map { pitchRow(it).let { (a, b) -> "피치 시트 $a · $b" } } }.orEmpty()
        }
        thread("thread-unef", threadUnef(), un)
        thread("thread-20un", thread20Un(), un)
        pipe("thread-pt-ext", threadPtExt())
        pipe("thread-pt-int", threadPtInt())
        pipe("thread-npt-ext", threadNptExt())
        pipe("thread-npt-int", threadNptInt())
    }

    /**
     * 다이스 헤드(2026-11-06): 입력(회사 값 저장소 포함) · 경고 글 · 범위 밖 칸 · 카탈로그 절삭 속도 줄 · 체이서 규격 표 · S · F · 시간 · 당김 ·
     * 완전 나사 끝 · NC · 카탈로그 안내 · 결과 칸. 위 14개와 따로라 그 글은 그대로다
     */
    @Test
    fun die() {
        die("thread-die", threadDie(), threadDieStore())
        die("thread-die-empty", threadDieEmpty(), emptyMap())
    }

    private fun die(name: String, s: ThreadState, store: Map<String, String>) {
        val d = DieStore(KeyValueStore(File.createTempFile("bmt-die", ".properties").apply { delete() }).apply { store.forEach { (k, v) -> put(k, v) } })
        val i = s.dieInput(d)
        val r = DieHeadCalc.calc(i)
        val sol = r.solution
        val text = buildString {
            line("${if (s.inner) "내경" else "외경"} · ${kindText(s.family)} · ${s.spec?.let(::specTitle) ?: "사이즈 없음"} · ${s.method} · 소재 ${s.material}")
            line("입력 Vc ${i.vc} · 회전수 ${i.rpm} · Z ${i.zStart} → ${i.zEnd} · 끝면 ${i.zFace} · 비율 ${i.ratio} · 멈춤 ${i.dwell} · 물러남 ${i.retractZ} · 플로트 ${i.floatTravel} · 목 ${i.throatThreads}")
            line("경고 ${r.warnings}")
            r.warnings.forEach { line("  ${dieWarningText(it, s, d, sol)}") }
            if (s.bad().isNotEmpty() || dieBad(s, d).isNotEmpty()) line("범위 밖 ${s.bad()} ${dieBad(s, d)}")
            line("카탈로그 줄 ${s.shownDieSpeed?.speed?.let(::speedShort)} · ${dieSpeedNote(s)}")
            s.spec?.let { sp ->
                val rows = chaserRows(sp)
                rows.forEach { line("체이서 ${it.maker} · ${it.size} · ${it.thread} · 목 ${it.throats} · 한 벌 ${it.set} · ${it.sourceId} ${it.page}") }
                chaserSources(rows).forEach { line("체이서 출처 $it") }
            }
            if (sol == null) return@buildString
            line("S ${sol.rpm} · Vc ${f(sol.vcUsed)} · F ${f(sol.feed)} · 분당 ${f(sol.feedPerMin)} · 시간 ${f(sol.timeSec)} · 당김 ${f(sol.floatPull)} · 완전 나사 끝 ${sol.fullThreadEndZ?.let(::f)}")
            sol.program.forEach { line("NC $it") }
            sol.note?.let { line("안내 ${dieCatalogFeedText()}") }
            dieResultCells(s, d, sol).forEach { (a, b, c) -> line("칸 $a = $b ($c)") }
        }
        golden(name, text)
    }

    private fun pipe(name: String, s: ThreadState) {
        val r = PipeThreadCalc.calc(s.pipeInput())
        val sol = r.solution
        val text = buildString {
            line("${if (s.inner) "내경" else "외경"} · ${kindText(s.family)} · ${s.pipe?.name(s.inner) ?: "사이즈 없음"} (${s.pipe?.label}) · 낱말 ${s.taperWord}")
            line("경고 ${r.warnings}")
            r.warnings.forEach { line("  ${threadWarningText(it, s, pipe = sol)}") }
            if (s.bad().isNotEmpty()) line("범위 밖 ${s.bad()}")
            gaugeRows(s, sol).forEach { (a, b) -> line("게이지 $a = $b") }
            s.pipe?.let { line("출처 ${gaugeSource(it)}") }
            if (sol == null) return@buildString
            line("게이지 면 Z ${f(sol.gaugeZ)} · 게이지 피치경 ${f(sol.gaugePitch)} · 끝 피치경 ${f(sol.pitchAtEnd)} · 나사 전 ${f(sol.crestAtRef)} → ${f(sol.crestAtEnd)}")
            line("X ${f(sol.x)} · k ${f(sol.height)} · 테이퍼 ${f(sol.taper)} · 시작 X ${f(sol.startX)} · 리드 ${leadText(sol.lead)} · 길이 ${f(sol.threadLen)} / ${f(sol.needLen)}")
            sol.passes.forEach { p -> line("  ${p.n} 깊이 ${f(p.depth)} 절입 ${f(p.cut)} X ${f(p.xDia)}${if (p.finish) " 정삭" else ""}") }
            sol.g92.forEach { line("G92 $it") }
            sol.g92Finish.forEach { line("다듬질 $it") }
            line("분당 이송 ${sol.feedPerMin?.let(::f)}")
            pipeResultCells(s, sol).forEach { (a, b, c) -> line("칸 $a = $b ($c)") }
        }
        golden(name, text)
    }

    private fun thread(name: String, s: ThreadState, extra: (ThreadState) -> List<String> = { emptyList() }) {
        val r = G76Calc.calc(s.input())
        val sol = r.solution
        val words = g76Words(s, sol)
        if (sol != null) assertEquals(sol.nc, words.map(::ncText), "$name: 화면의 G76 낱말이 NC 글과 다르다")
        val text = buildString {
            line("${if (s.inner) "내경" else "외경"} · ${s.spec?.label ?: "사이즈 없음"} · ${s.shownClass?.label} · ${s.dir} · a${s.shownAngle} · r${s.shownChamfer}")
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
        } + extra(s).joinToString("") { "$it\n" }
        golden(name, text)
    }

    private fun StringBuilder.line(s: String) = append(s).append('\n')
    private fun f(v: Double) = Fmt.fixed3(v)
}
