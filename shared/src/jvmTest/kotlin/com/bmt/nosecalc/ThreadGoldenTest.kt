package com.bmt.nosecalc

import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.RollHeadCalc
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.screens.resultCells
import com.bmt.nosecalc.screens.thread.DieStore
import com.bmt.nosecalc.screens.thread.METHOD_ORDER
import com.bmt.nosecalc.screens.thread.ROLL_TIP_GROUPS
import com.bmt.nosecalc.screens.thread.RollField
import com.bmt.nosecalc.screens.thread.RollStore
import com.bmt.nosecalc.screens.thread.ThreadField
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
import com.bmt.nosecalc.screens.thread.methodText
import com.bmt.nosecalc.screens.thread.ncText
import com.bmt.nosecalc.screens.thread.pipeResultCells
import com.bmt.nosecalc.screens.thread.pitchRow
import com.bmt.nosecalc.screens.thread.rollBad
import com.bmt.nosecalc.screens.thread.rollBlankCells
import com.bmt.nosecalc.screens.thread.rollDirectionText
import com.bmt.nosecalc.screens.thread.rollFeedFact
import com.bmt.nosecalc.screens.thread.rollHeadRow
import com.bmt.nosecalc.screens.thread.rollHeadText
import com.bmt.nosecalc.screens.thread.rollNcExplain
import com.bmt.nosecalc.screens.thread.rollPlaceholder
import com.bmt.nosecalc.screens.thread.rollReferenceText
import com.bmt.nosecalc.screens.thread.rollResultCells
import com.bmt.nosecalc.screens.thread.rollTipGroupTitle
import com.bmt.nosecalc.screens.thread.rollTipsText
import com.bmt.nosecalc.screens.thread.rollWarningText
import com.bmt.nosecalc.screens.thread.threadRoll
import com.bmt.nosecalc.screens.thread.threadRollEmpty
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

    /**
     * 전조 다이스(2026-11-14): 입력(전조 조건 저장소 포함) · 가공 방식 세 칸 · 자리표시 · 머리 · 롤 칸과 시트 줄 · 경고 글 · 범위 밖 칸 ·
     * 전조 전 지름 칸 · S · v · F · 시간 · 실제 끝 · 전조 길이 · NC · NC 사실 · 결과 칸 · 요령 방향 · 참고 줄 · 요령 카드 글.
     * 위 16개(곧은 나사 · 관용 테이퍼 · 다이스 헤드)와 따로라 그 글은 그대로다
     */
    @Test
    fun roll() {
        // 3/4-20 은 고친 상태(회전수 620 · 비율 1.04 · 멈춤 0 — 스크린샷 thread-roll-warn)의 노랑 · 요령 방향 · NC 도 뒤에 붙인다
        val warn = mapOf(RollStore.key(RollField.RATIO) to "1.04", RollStore.key(RollField.DWELL) to "0")
        roll("thread-roll", threadRoll(), emptyMap()) { rollText(threadRoll().apply { set(ThreadField.RPM, "620") }, warn, "고침 ") }
        roll("thread-roll-empty", threadRollEmpty(), emptyMap())
    }

    private fun rollStore(store: Map<String, String>) =
        RollStore(KeyValueStore(File.createTempFile("bmt-roll", ".properties").apply { delete() }).apply { store.forEach { (k, v) -> put(k, v) } })

    /** 고친 상태의 경고 글 · NC · 회전수 · 이송 칸 · 요령 방향(앞에 [tag]) */
    private fun rollText(s: ThreadState, store: Map<String, String>, tag: String): String {
        val r = rollStore(store)
        val i = s.rollInput(r)
        val res = RollHeadCalc.calc(i)
        val sol = res.solution
        return buildString {
            line("${tag}입력 회전수 ${i.rpm} · 비율 ${i.ratio} · 멈춤 ${i.dwell}")
            line("${tag}경고 ${res.warnings}")
            res.warnings.forEach { line("  ${rollWarningText(it, s, r, sol)}") }
            if (sol == null) return@buildString
            sol.program.forEach { line("${tag}NC $it") }
            line("${tag}NC 사실 ${rollFeedFact(sol)}")
            rollResultCells(s, r, sol).take(5).forEach { (a, b, c) -> line("${tag}칸 $a = $b ($c)") }
            line("${tag}방향 ${sol.rpmDirection} · ${sol.ratioDirection}")
            rollDirectionText(sol).forEach { line("  $it") }
        }
    }

    private fun roll(name: String, s: ThreadState, store: Map<String, String>, extra: () -> String = { "" }) {
        val r = rollStore(store)
        val i = s.rollInput(r)
        val res = RollHeadCalc.calc(i)
        val sol = res.solution
        val text = buildString {
            line("${if (s.inner) "내경" else "외경"} · ${kindText(s.family)} · ${s.spec?.let(::specTitle) ?: "사이즈 없음"} · ${s.method} · 소재 ${s.material} · 묶음 ${s.unGroup.label}")
            line("가공 방식 ${METHOD_ORDER.joinToString(" | ") { methodText(it) }} (고름 ${METHOD_ORDER.indexOf(s.method)}) · 좁은 폰 ${METHOD_ORDER.joinToString(" | ") { methodText(it, narrow = true) }}")
            line("입력 강종 ${i.steel} · 머리 ${i.head} · 소재 종류 ${i.stock} · 회전수 ${i.rpm} · Z ${i.zStart} → ${i.zEnd} · 끝면 ${i.zFace} · 비율 ${i.ratio} · 멈춤 ${i.dwell} · 물러남 ${i.retractZ}")
            line("자리표시 ${s.fields.joinToString(" · ") { "${it.key} '${s.placeholder(it)}'" }} · ${RollField.entries.joinToString(" · ") { "${it.key} '${rollPlaceholder(s, it)}'" }}")
            line("머리 · 롤 ${rollHeadText(s)}")
            s.spec?.let { sp -> s.rollHeadChoices.forEach { k -> rollHeadRow(sp, k).let { (a, b) -> line("머리 시트 $a · $b") } } }
            line("경고 ${res.warnings}")
            res.warnings.forEach { line("  ${rollWarningText(it, s, r, sol)}") }
            if (s.bad().isNotEmpty() || rollBad(s, r).isNotEmpty()) line("범위 밖 ${s.bad()} ${rollBad(s, r)}")
            s.rollDefaults.blank?.let { b -> line("전조 전 지름 X${f(b.used)} (${b.usedBasis}) · 추천 ${f(b.recMin)} ~ ${f(b.recMax)} · 회사 ${b.company?.let(::f)}") }
            rollBlankCells(s).forEach { (a, b, c) -> line("지름 칸 $a = $b ($c)") }
            line(rollReferenceText(s))
            // 요령 카드 글(규격과 상관없이 같다 — 사이즈를 고른 골든에만)
            if (s.spec != null) ROLL_TIP_GROUPS.forEach { g -> rollTipsText(g).forEach { line("요령 ${rollTipGroupTitle(g)}: $it") } }
            if (sol == null) return@buildString
            line(
                "S ${sol.rpm} (${sol.rpmBasis}, 기준 ${sol.baseRpm}, 범위 ${sol.rpmRange}) · v ${f(sol.vUsed)} · F ${f(sol.feed)} · 비율 ${f(sol.ratio)} (${sol.ratioBasis}) · " +
                    "분당 ${f(sol.feedPerMin)} · 시간 ${f(sol.timeSec)}",
            )
            line(
                "머리 ${sol.head.key} · 회사 롤 ${sol.companyHead?.roll} · a ${sol.openingMovement} · 실제 끝 ${sol.rollEndZ?.let(::f)} · 전조 길이 ${sol.rolledLength?.let(::f)} · " +
                    "최대 ${sol.maxRollLength} · 바깥지름 ${f(sol.crest.min)} ~ ${f(sol.crest.max)}",
            )
            sol.program.forEach { line("NC $it") }
            line("NC 사실 전조 구간만 — T · M03 없음 · G99 회전당 이송 상태로 · ${rollFeedFact(sol)}")
            line("NC 설명 ${rollNcExplain(i.retractZ, sol.openingMovement)}")
            rollResultCells(s, r, sol).forEach { (a, b, c) -> line("칸 $a = $b ($c)") }
            line("방향 ${sol.rpmDirection} · ${sol.ratioDirection}")
            rollDirectionText(sol).forEach { line("  $it") }
        } + extra()
        golden(name, text)
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
            line("카탈로그 줄 ${s.shownDieSpeed?.speed?.let(::speedShort)} · ${dieSpeedNote(s, sol)}")
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
