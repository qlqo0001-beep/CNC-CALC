package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.CompanyRolls
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieHeadCatalog
import com.bmt.nosecalc.calc.DieWarning
import com.bmt.nosecalc.calc.DrillCatalog
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarWarning
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.RollHeadCalc
import com.bmt.nosecalc.calc.RollHeadCatalog
import com.bmt.nosecalc.calc.RollSteel
import com.bmt.nosecalc.calc.RollStock
import com.bmt.nosecalc.calc.RollWarning
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.screens.CuttingScreen
import com.bmt.nosecalc.screens.PolarScreen
import com.bmt.nosecalc.screens.ThreadScreen
import com.bmt.nosecalc.screens.cutting.CutBox
import com.bmt.nosecalc.screens.cutting.CutTab
import com.bmt.nosecalc.screens.cutting.CuttingState
import com.bmt.nosecalc.screens.cutting.MANUAL
import com.bmt.nosecalc.screens.cutting.basicPreview
import com.bmt.nosecalc.screens.cutting.basicView
import com.bmt.nosecalc.screens.cutting.cutWarningText
import com.bmt.nosecalc.screens.cutting.drillLines
import com.bmt.nosecalc.screens.cutting.drillPreview
import com.bmt.nosecalc.screens.cutting.drillWarningText
import com.bmt.nosecalc.screens.cutting.rowSub
import com.bmt.nosecalc.screens.cutting.sourceText
import com.bmt.nosecalc.screens.polar.PolarField
import com.bmt.nosecalc.screens.polar.PolarState
import com.bmt.nosecalc.screens.polar.SimStart
import com.bmt.nosecalc.screens.polar.ncTokens
import com.bmt.nosecalc.screens.polar.polarGeom
import com.bmt.nosecalc.screens.polar.polarResultCells
import com.bmt.nosecalc.screens.polar.polarWarningText
import com.bmt.nosecalc.screens.polar.simPlan
import com.bmt.nosecalc.screens.previewLine
import com.bmt.nosecalc.screens.resultCells
import com.bmt.nosecalc.screens.thread.DieField
import com.bmt.nosecalc.screens.thread.DieStore
import com.bmt.nosecalc.screens.thread.Pick
import com.bmt.nosecalc.screens.thread.RollField
import com.bmt.nosecalc.screens.thread.RollStore
import com.bmt.nosecalc.screens.thread.ThreadFamily
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadMethod
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.chaserRows
import com.bmt.nosecalc.screens.thread.dieFieldHint
import com.bmt.nosecalc.screens.thread.dieFlagged
import com.bmt.nosecalc.screens.thread.dieMissing
import com.bmt.nosecalc.screens.thread.dieResultCells
import com.bmt.nosecalc.screens.thread.dieSpeedKey
import com.bmt.nosecalc.screens.thread.dieSpeedNote
import com.bmt.nosecalc.screens.thread.dieWarningText
import com.bmt.nosecalc.screens.thread.fieldHint
import com.bmt.nosecalc.screens.thread.g76Words
import com.bmt.nosecalc.screens.thread.gaugeRows
import com.bmt.nosecalc.screens.thread.ncText
import com.bmt.nosecalc.screens.thread.pipeOutline
import com.bmt.nosecalc.screens.thread.pipeResultCells
import com.bmt.nosecalc.screens.thread.pipeSectionGeom
import com.bmt.nosecalc.screens.thread.rollBlankCells
import com.bmt.nosecalc.screens.thread.rollConditionHint
import com.bmt.nosecalc.screens.thread.rollDirectionText
import com.bmt.nosecalc.screens.thread.rollFeedFact
import com.bmt.nosecalc.screens.thread.rollFieldHint
import com.bmt.nosecalc.screens.thread.rollFlagged
import com.bmt.nosecalc.screens.thread.rollHeadChoices
import com.bmt.nosecalc.screens.thread.rollHeadRow
import com.bmt.nosecalc.screens.thread.rollHeadText
import com.bmt.nosecalc.screens.thread.rollMissing
import com.bmt.nosecalc.screens.thread.rollNcExplain
import com.bmt.nosecalc.screens.thread.rollPlaceholder
import com.bmt.nosecalc.screens.thread.rollReferenceText
import com.bmt.nosecalc.screens.thread.rollResultCells
import com.bmt.nosecalc.screens.thread.rollWarningText
import com.bmt.nosecalc.screens.thread.sectionGeom
import com.bmt.nosecalc.screens.thread.threadWarningText
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 3단계 화면(나사 G76 — 관용 테이퍼 · 다이스 헤드 · 전조 다이스 포함 · 턴밀 극좌표 · 재료·절삭조건)이 잘못된 입력에서 죽지 않는가 — [InputRobustnessTest] 와 같은 방식.
 * 키패드로 칠 수 있는 글(빈칸 · 0 · 음수 · 12자리 · 아주 작은 수 · '.' · '-')을 칸마다 흔들고, 고르는 칸(사이즈 · 급수 · 도형 · 메이커 · 행 …)도 섞는다.
 * 1) 화면이 쓰는 글 함수(경고 · 결과 칸 · 미리보기 · 낱말)가 예외 없이 나오고 찍는 숫자가 유한한지(극좌표는 PolarStateTest.fuzzNoCrash 가 같은 일을 한다),
 * 2) 그런 상태의 화면을 실제로 그려 본다(키패드 · 다크 · 직접 보정 · 시뮬레이션 모달 · 탭 셋 섞어서).
 */
class Stage3RobustnessTest {
    private val pool = listOf(
        "", "0", "-0", ".", "-", "0.001", "0.0001", "0.0004", "0.035", "0.2", "0.5", "1", "1.5", "2", "3", "5", "-5", "10", "-25", "25", "40",
        "60", "70", "80", "99", "100", "100.5", "1000", "9999.999", "-9999.999", "10000.001", "123456.789", "999999999999", "-999999999999",
        "0.00000000001", "0.00001", "34.9999999", "79.9999999",
    )
    private val rnd = Random(20261028)
    private var solved = 0
    private fun pick() = pool[rnd.nextInt(pool.size)]

    /** 칸 글 흔들기: 처음 값 그대로 · 조금 바꿈 · 빈칸 · 극단 값 */
    private fun nudge(text: String): String = when (rnd.nextInt(8)) {
        0 -> pick()
        1 -> ""
        2, 3 -> ((text.toDoubleOrNull() ?: 0.0) + rnd.nextInt(-3000, 3001) / 1000.0).toString()
        else -> text
    }

    private fun show(vararg v: Double) = v.forEach { Fmt.fixed3(it) }

    private fun check(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            fail("$what 에서 예외: $t")
        }
    }

    // ── 나사 G76 ──

    /** 무작위 나사 화면 상태 — 종류는 families 가운데(관용 테이퍼면 크기 · UN 묶음 · 테이퍼 낱말도 무작위, 급수는 없음) */
    private fun randomThread(families: List<ThreadFamily> = ThreadFamily.entries): ThreadState {
        val family = families[rnd.nextInt(families.size)]
        val kind = family.kind
        val inner = rnd.nextBoolean()
        val specs = kind?.let { ThreadTable.of(it) }.orEmpty()
        val pipes = family.pipeKind?.let { PipeTable.of(it) }.orEmpty()
        val spec = if (specs.isEmpty() || rnd.nextInt(10) == 0) null else specs[rnd.nextInt(specs.size)]
        val pipe = if (pipes.isEmpty() || rnd.nextInt(10) == 0) null else pipes[rnd.nextInt(pipes.size)]
        val classes = ThreadClass.entries.filter { it.kind == kind && it.internal == inner }
        val base = mapOf("zStart" to "5", "zEnd" to "-25", "firstCut" to "0.2")
        return ThreadState(
            inner = inner,
            family = family,
            spec = spec,
            pipe = pipe,
            unGroup = UnGroup.entries[rnd.nextInt(UnGroup.entries.size)],
            taperWord = if (rnd.nextInt(3) == 0) 'R' else 'I',
            cls = if (classes.isEmpty() || rnd.nextBoolean()) null else classes[rnd.nextInt(classes.size)],
            material = if (rnd.nextBoolean()) null else IsoGroup.entries[rnd.nextInt(6)],
            dir = if (rnd.nextInt(4) == 0) ThreadDir.REVERSE else ThreadDir.FORWARD,
            angle = if (rnd.nextBoolean()) null else G76Calc.ANGLES[rnd.nextInt(G76Calc.ANGLES.size)],
            chamfer = if (rnd.nextBoolean()) null else rnd.nextInt(21),
            values = ThreadField.entries.associate { f ->
                f.key to (if (rnd.nextInt(3) == 0) nudge(base[f.key] ?: "") else base[f.key] ?: "")
            },
            advOpen = rnd.nextBoolean(),
        )
    }

    @Test
    fun thread() {
        repeat(20_000) {
            val s = randomThread(listOf(ThreadFamily.METRIC, ThreadFamily.UN))
            check("나사 ${s.spec} ${ThreadField.entries.map { s.value(it) }}") {
                val r = G76Calc.calc(s.input())
                val sol = r.solution
                s.missing()
                s.flagged(r.warnings)
                ThreadField.entries.forEach { s.placeholder(it); fieldHint(it, s.inner) }
                r.warnings.forEach { threadWarningText(it, s, sol) }
                threadWarningText(G76Warning.MISSING_INPUT, s)
                if (s.bad().isNotEmpty()) threadWarningText(G76Warning.BAD_INPUT, s)
                g76Words(s, sol).forEach { ncText(it) }
                val g = sectionGeom(s.inner, sol)
                assertTrue(listOf(g.p, g.k, g.crestToPitch, g.tipR).all { it.isFinite() } && g.depths.all { it.isFinite() }, "단면 그림 값: $g")
                if (sol != null) {
                    solved++
                    resultCells(s, sol)
                    show(sol.pitchDia, sol.crestDia, sol.x, sol.startX, sol.height, sol.lead, sol.tipR)
                    sol.passes.forEach { show(it.depth, it.cut, it.xDia) }
                    sol.feedPerMin?.let { show(it) }
                }
            }
        }
        assertTrue(solved > 5_000, "결과가 나온 사례가 너무 적다: $solved")
    }

    /** 관용 테이퍼(PT · NPT): 같은 흔들기로 계산기 · 화면 글 함수(경고 · 게이지 표 · 결과 칸 · 단면 윤곽)가 예외 없이 유한한 숫자를 낸다 */
    @Test
    fun pipeThread() {
        var pipeSolved = 0
        repeat(10_000) {
            val s = randomThread(listOf(ThreadFamily.PT, ThreadFamily.NPT))
            check("관용 나사 ${s.family} ${s.pipe?.size} 내경 ${s.inner} ${ThreadField.entries.map { s.value(it) }}") {
                val r = PipeThreadCalc.calc(s.pipeInput())
                val sol = r.solution
                s.missing()
                s.flagged(r.warnings)
                s.fields.forEach { s.placeholder(it); fieldHint(it, s.inner, pipe = true) }
                r.warnings.forEach { threadWarningText(it, s, pipe = sol) }
                threadWarningText(G76Warning.MISSING_INPUT, s)
                if (s.bad().isNotEmpty()) threadWarningText(G76Warning.BAD_INPUT, s)
                gaugeRows(s, sol)
                val g = pipeSectionGeom(s.family.pipeKind!!, s.inner, s.pipe, sol)
                assertTrue(listOf(g.p, g.k, g.crestToPitch, g.tipR).all { it.isFinite() } && g.depths.all { it.isFinite() }, "단면 그림 값: $g")
                assertTrue(pipeOutline(g.profile!!, g.p, g.k).all { (z, d) -> z.isFinite() && d.isFinite() }, "단면 윤곽: $g")
                if (sol != null) {
                    pipeSolved++
                    pipeResultCells(s, sol)
                    show(sol.gaugeZ, sol.gaugePitch, sol.pitchAtEnd, sol.crestAtRef, sol.crestAtEnd, sol.x, sol.height, sol.taper, sol.startX, sol.lead, sol.threadLen, sol.needLen)
                    sol.passes.forEach { show(it.depth, it.cut, it.xDia) }
                    sol.feedPerMin?.let { show(it) }
                }
            }
        }
        assertTrue(pipeSolved > 2_500, "결과가 나온 사례가 너무 적다: $pipeSolved")
    }

    /** 무작위 다이스 헤드 화면 상태(미터 · UN 숫나사)와 다이스 조건 저장소 글 — 칸마다 셋에 하나를 흔든다 */
    private fun randomDie(): Pair<ThreadState, Map<String, String>> {
        val kind = if (rnd.nextBoolean()) ThreadKind.METRIC else ThreadKind.UN
        val specs = ThreadTable.of(kind)
        val speeds = DieHeadCatalog.speeds()
        val material = if (rnd.nextBoolean()) null else IsoGroup.entries[rnd.nextInt(6)]
        val base = mapOf("zStart" to "5", "zEnd" to "-24", "rpm" to "", "zRef" to "")
        val s = ThreadState(
            family = if (kind == ThreadKind.METRIC) ThreadFamily.METRIC else ThreadFamily.UN,
            spec = if (rnd.nextInt(10) == 0) null else specs[rnd.nextInt(specs.size)],
            material = material,
            values = base.mapValues { (_, v) -> if (rnd.nextInt(3) == 0) nudge(v) else v },
            method = ThreadMethod.DIE,
            dieSpeed = if (rnd.nextBoolean()) null else dieSpeedKey(speeds[rnd.nextInt(speeds.size)]),
        )
        val dieBase = mapOf(DieField.VC to "8", DieField.RATIO to "", DieField.DWELL to "", DieField.RETRACT to "", DieField.FLOAT to "", DieField.THROAT to "")
        val store = dieBase.entries.associate { (f, v) -> DieStore.key(f, material) to (if (rnd.nextInt(3) == 0) nudge(v) else v) }
        return s to store
    }

    /** 다이스 헤드: 같은 흔들기로 계산기 · 화면 글 함수(경고 · 짚는 칸 · 결과 칸 · 체이서 표 · 카탈로그 줄)가 예외 없이 유한한 숫자를 낸다 */
    @Test
    fun dieThread() {
        var dieSolved = 0
        repeat(10_000) {
            val (s, store) = randomDie()
            val d = DieStore(KeyValueStore(File.createTempFile("bmt-die", ".properties").apply { delete() }).apply { store.forEach { (k, v) -> put(k, v) } })
            check("다이스 ${s.spec} ${s.fields.map { s.value(it) }} $store") {
                val r = DieHeadCalc.calc(s.dieInput(d))
                val sol = r.solution
                dieMissing(s, d)
                dieFlagged(s, d, r.warnings)
                s.fields.forEach { s.placeholder(it); dieFieldHint(it) }
                r.warnings.forEach { dieWarningText(it, s, d, sol) }
                dieWarningText(DieWarning.MISSING_INPUT, s, d)
                dieWarningText(DieWarning.BAD_INPUT, s, d)
                dieSpeedNote(s)
                s.spec?.let(::chaserRows)
                if (sol != null) {
                    dieSolved++
                    dieResultCells(s, d, sol)
                    show(sol.vcUsed, sol.feed, sol.feedPerMin, sol.timeSec, sol.floatPull)
                    sol.fullThreadEndZ?.let { show(it) }
                }
            }
        }
        assertTrue(dieSolved > 2_500, "결과가 나온 사례가 너무 적다: $dieSolved")
    }

    /**
     * 무작위 전조 다이스 화면 상태(미터 · UN 숫나사 — 셋에 하나는 회사 롤 규격 일곱)와 전조 조건 저장소 글 — 칸마다 셋에 하나를 흔든다.
     * 머리는 그 규격의 선택지 가운데(가끔 아무 EVOline 머리 — 지름 범위 밖일 수도), 소재는 셋에 둘이 비움(스테인리스로 봄)
     */
    private fun randomRoll(): Pair<ThreadState, Map<String, String>> {
        val kind = if (rnd.nextBoolean()) ThreadKind.METRIC else ThreadKind.UN
        val specs = ThreadTable.of(kind)
        val spec = when (rnd.nextInt(10)) {
            0 -> null
            1, 2, 3 -> CompanyRolls.specs[rnd.nextInt(CompanyRolls.specs.size)]
            else -> specs[rnd.nextInt(specs.size)]
        }
        val choices = spec?.let(::rollHeadChoices).orEmpty()
        val base = mapOf("zStart" to "", "zEnd" to "-16.37", "rpm" to "", "zRef" to "")
        val s = ThreadState(
            family = if ((spec?.kind ?: kind) == ThreadKind.METRIC) ThreadFamily.METRIC else ThreadFamily.UN,
            spec = spec,
            material = if (rnd.nextInt(3) != 0) null else IsoGroup.entries[rnd.nextInt(6)],
            values = base.mapValues { (_, v) -> if (rnd.nextInt(3) == 0) nudge(v) else v },
            unGroup = UnGroup.entries[rnd.nextInt(UnGroup.entries.size)],
            method = ThreadMethod.ROLL,
            rollHead = if (choices.isEmpty() || rnd.nextBoolean()) null else choices[rnd.nextInt(choices.size)],
            rollStock = RollStock.entries[rnd.nextInt(RollStock.entries.size)],
            rollSteel = RollSteel.entries[rnd.nextInt(RollSteel.entries.size)],
        )
        if (rnd.nextInt(12) == 0) s.rollHead = RollHeadCatalog.evoHeads[rnd.nextInt(RollHeadCatalog.evoHeads.size)].key
        val store = RollField.entries.associate { f -> RollStore.key(f) to (if (rnd.nextInt(3) == 0) nudge("") else "") }
        return s to store
    }

    /** 전조 다이스: 같은 흔들기로 계산기 · 화면 글 함수(경고 · 짚는 칸 · 자리표시 · 지름 칸 · 머리 칸 · 결과 칸 · 요령 방향 · NC 설명)가 예외 없이 유한한 숫자를 낸다 */
    @Test
    fun rollThread() {
        var rollSolved = 0
        repeat(10_000) {
            val (s, store) = randomRoll()
            val r = RollStore(KeyValueStore(File.createTempFile("bmt-roll", ".properties").apply { delete() }).apply { store.forEach { (k, v) -> put(k, v) } })
            check("전조 ${s.spec} ${s.material} 머리 ${s.rollHead} ${s.fields.map { s.value(it) }} $store") {
                val res = RollHeadCalc.calc(s.rollInput(r))
                val sol = res.solution
                rollMissing(s)
                rollFlagged(s, r, res.warnings)
                s.fields.forEach { s.placeholder(it); rollFieldHint(it) }
                RollField.entries.forEach { rollPlaceholder(s, it); rollConditionHint(it) }
                res.warnings.forEach { rollWarningText(it, s, r, sol) }
                rollWarningText(RollWarning.MISSING_INPUT, s, r)
                rollWarningText(RollWarning.BAD_INPUT, s, r)
                rollBlankCells(s)
                rollHeadText(s)
                rollReferenceText(s)
                s.spec?.let { sp -> s.rollHeadChoices.forEach { rollHeadRow(sp, it) } }
                s.rollDefaults.blank?.let { show(it.used, it.rec, it.recMin, it.recMax) }
                if (sol != null) {
                    rollSolved++
                    rollResultCells(s, r, sol)
                    rollDirectionText(sol)
                    rollFeedFact(sol)
                    rollNcExplain(s.rollInput(r).retractZ, sol.openingMovement)
                    show(sol.vUsed, sol.feed, sol.ratio, sol.feedPerMin, sol.timeSec, sol.zStart, sol.crest.min, sol.crest.max)
                    listOfNotNull(sol.rollEndZ, sol.rolledLength).forEach { show(it) }
                }
            }
        }
        println("전조 흔들기: 결과가 나온 사례 $rollSolved / 10000")
        assertTrue(rollSolved > 2_000, "결과가 나온 사례가 너무 적다: $rollSolved")
    }

    // ── 재료·절삭조건 ──

    private fun randomCutting(): CuttingState {
        val makers = DrillCatalog.makers
        val maker = when (rnd.nextInt(8)) {
            0 -> null
            1 -> MANUAL
            else -> makers[rnd.nextInt(makers.size)].key
        }
        val m = maker?.let { DrillCatalog.maker(it) }
        val series = m?.let { if (rnd.nextInt(8) == 0) null else rnd.nextInt(it.series.size) }
        val rows = series?.let { m.series[it].conditions.size } ?: 0
        val row = if (rows == 0 || rnd.nextInt(8) == 0) null else rnd.nextInt(rows)
        val base = mapOf(
            CutBox.D to "50", CutBox.VC to "150", CutBox.F to "0.2", CutBox.Z to "", CutBox.LENGTH to "120", CutBox.NOSE_R to "0.8",
            CutBox.SMAX to "2000", CutBox.DRILL_D to "10", CutBox.DRILL_VC to "100", CutBox.DRILL_F to "0.2",
        )
        val s = CuttingState(
            tab = CutTab.entries[rnd.nextInt(3)],
            maker = maker,
            series = series,
            iso = if (rnd.nextBoolean()) null else IsoGroup.entries[rnd.nextInt(6)],
            row = row,
            values = CutBox.entries.associate { b -> b.key to (if (rnd.nextInt(3) == 0) nudge(base[b] ?: "") else base[b] ?: "") },
        )
        // 화면처럼 넣은 차례로 기준을 바꾼다(n · fz 를 넣으면 Vc · f 칸은 빈다)
        if (rnd.nextInt(3) == 0) s.set(CutBox.N, nudge("1000"))
        if (rnd.nextInt(3) == 0) s.set(CutBox.FZ, nudge("0.05"))
        if (row != null && rnd.nextBoolean()) s.pickRow(row)
        return s
    }

    @Test
    fun cutting() {
        var drilled = 0
        repeat(20_000) {
            val s = randomCutting()
            check("절삭 ${s.maker} ${s.series} ${s.row} ${CutBox.entries.map { s.value(it) }}") {
                val r = Cutting.calc(s.input())
                val v = basicView(s, r)
                r.warnings.forEach { cutWarningText(it, s, r) }
                basicPreview(s, v)
                listOfNotNull(r.vc, r.n, r.f, r.vf, r.timeMin, r.timeSec, r.rz, r.ra).forEach { show(it) }
                s.drillMissing()
                s.drillSeries?.let { ser -> ser.conditions.forEach { rowSub(ser, it) } }
                val d = s.drill() ?: return@check
                drilled++
                drillLines(d)
                d.warnings.forEach { drillWarningText(it, s, d) }
                drillPreview(d)
                sourceText(d)
                listOfNotNull(d.vc, d.n, d.f, d.vf).forEach { sp -> listOfNotNull(sp.min, sp.rec, sp.max).forEach { show(it) } }
            }
        }
        assertTrue(drilled > 5_000, "드릴 결과가 나온 사례가 너무 적다: $drilled")
    }

    // ── 화면을 실제로 그려 본다 ──

    private fun draw(dark: Boolean, direct: Boolean, store: Map<String, String> = emptyMap(), content: @Composable () -> Unit) {
        val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }).apply { store.forEach { (k, v) -> put(k, v) } })
        state.setTheme(dark)
        if (direct) state.compMode = CompMode.DIRECT
        val scene = ImageComposeScene(400, 1600, Density(1f)) { AppHost(state) { content() } }
        try {
            repeat(3) { scene.render(it * 100_000_000L).close() }
        } finally {
            scene.close()
        }
    }

    private fun randomPolar(): PolarState {
        val base = mapOf("od" to "80", "w" to "70", "rc" to "3", "rot" to "", "tool" to "10", "clear" to "", "depth" to "-5", "feed" to "100")
        return PolarState(
            inner = rnd.nextInt(3) == 0,
            shape = PolarShape.entries[rnd.nextInt(PolarShape.entries.size)],
            xDiameter = rnd.nextBoolean(),
            values = PolarField.entries.associate { f -> f.key to (if (rnd.nextInt(3) == 0) nudge(base[f.key] ?: "") else base[f.key] ?: "") },
        )
    }

    /** PolarStateTest.fuzzNoCrash 와 같은 일을 이 파일의 값 모음(아주 작은 코너 R · 맞변 절반에 붙은 R 포함)으로 */
    @Test
    fun polar() {
        var solvedPolar = 0
        repeat(20_000) {
            val s = randomPolar()
            check("극좌표 ${s.shape} 내측 ${s.inner} ${PolarField.entries.map { s.value(it) }}") {
                val r = PolarG12.calc(s.input())
                val sol = r.solution
                r.warnings.forEach { polarWarningText(it, s, sol) }
                if (s.bad().isNotEmpty() || s.shapeBad) polarWarningText(PolarWarning.BAD_INPUT, s)
                s.flagged(r.warnings)
                PolarField.entries.forEach { s.placeholder(it); s.label(it); s.hint(it) }
                val g = polarGeom(s, sol)
                g.shape.forEach { seg -> assertTrue(listOf(seg.start, seg.end).all { it.z.isFinite() && it.x.isFinite() }, "그림 점: $seg") }
                if (sol != null) {
                    solvedPolar++
                    polarResultCells(s, sol, false)
                    polarResultCells(s, sol, true)
                    previewLine(s, sol)
                    for (p in listOf(sol.ncG41G42, sol.ncG40)) {
                        p.blocks.forEach { b -> ncTokens(b); assertTrue(listOfNotNull(b.x, b.c, b.z).all { it.isFinite() }, b.text) }
                        val plan = simPlan(sol, p, s.num(PolarField.FEED)!!)
                        assertTrue(plan.feedSeconds.isFinite())
                        plan.toolAt(p.blocks.lastIndex / 2, .5)
                    }
                }
            }
        }
        assertTrue(solvedPolar > 5_000, "결과가 나온 사례가 너무 적다: $solvedPolar")
    }

    @Test
    fun drawRandom() {
        repeat(120) { i ->
            val dark = rnd.nextBoolean()
            val direct = rnd.nextBoolean()
            val t = randomThread()
            val tEdit = if (rnd.nextInt(3) == 0) ThreadField.entries[rnd.nextInt(ThreadField.entries.size)] else null
            if (tEdit?.advanced == true) t.advOpen = true
            check("나사 화면 $i ${t.spec} ${ThreadField.entries.map { t.value(it) }} 키패드 $tEdit") { draw(dark, false) { ThreadScreen(t, tEdit) } }
            val (die, dieStore) = randomDie()
            check("다이스 화면 $i ${die.spec} ${die.fields.map { die.value(it) }} $dieStore") { draw(dark, false, dieStore) { ThreadScreen(die) } }

            val p = randomPolar()
            val pEdit = if (rnd.nextInt(3) == 0) PolarField.entries[rnd.nextInt(PolarField.entries.size)] else null
            val sim = if (pEdit == null && rnd.nextInt(3) == 0) SimStart(0, rnd.nextDouble()) else null
            check("극좌표 화면 $i ${p.shape} 내측 ${p.inner} ${PolarField.entries.map { p.value(it) }} 키패드 $pEdit 시뮬 $sim") {
                draw(dark, direct) { PolarScreen(p, pEdit, sim = sim) }
            }

            val c = randomCutting()
            val boxes = if (c.tab == CutTab.DRILL) listOf(CutBox.DRILL_D, CutBox.DRILL_VC, CutBox.DRILL_F) else CutBox.entries.take(9)
            val cEdit = if (rnd.nextInt(3) == 0) boxes[rnd.nextInt(boxes.size)] else null
            check("절삭 화면 $i ${c.tab} ${c.maker} ${c.series} ${c.row} ${CutBox.entries.map { c.value(it) }} 키패드 $cEdit") {
                draw(dark, false) { CuttingScreen(c, cEdit) }
            }
        }
        // 전조 다이스(위 120 회의 차례를 바꾸지 않게 뒤에 따로): 키패드 · 머리 · 사이즈 · 가공 소재 시트를 섞어서
        repeat(40) { i ->
            val (roll, rollStore) = randomRoll()
            val dark = rnd.nextBoolean()
            val sheet = if (rnd.nextInt(4) == 0) listOf(Pick.ROLL_HEAD, Pick.SIZE, Pick.MATERIAL)[rnd.nextInt(3)] else null
            val edit = if (sheet == null && rnd.nextInt(3) == 0) roll.fields[rnd.nextInt(roll.fields.size)] else null
            check("전조 화면 $i ${roll.spec} ${roll.material} 머리 ${roll.rollHead} ${roll.fields.map { roll.value(it) }} $rollStore 키패드 $edit 시트 $sheet") {
                draw(dark, false, rollStore) { ThreadScreen(roll, edit, sheet) }
            }
        }
    }

    /** 칸마다 극단 값 하나씩(다른 칸은 그럴듯한 값) — 큰 수 · 아주 작은 수 · 음수 · 0 */
    @Test
    fun drawExtremes() {
        for (v in listOf("999999999999", "-999999999999", "0.00000000001", "0", "-")) {
            for (f in ThreadField.entries) {
                val t = ThreadState(
                    spec = ThreadTable.pitches(ThreadKind.METRIC, "M10").first(),
                    values = mapOf("zStart" to "5", "zEnd" to "-25", "firstCut" to "0.2", f.key to v),
                    advOpen = true,
                )
                check("나사 $f = $v") { draw(false, false) { ThreadScreen(t) } }
                // 관용 테이퍼(PT 숫나사 · NPT 암나사 번갈아)
                val pt = f.ordinal % 2 == 0
                val pipe = ThreadState(
                    inner = !pt,
                    family = if (pt) ThreadFamily.PT else ThreadFamily.NPT,
                    pipe = PipeTable.of(if (pt) PipeKind.PT else PipeKind.NPT).first { it.size == "1/2" },
                    values = mapOf("zStart" to "5", "zEnd" to "-25", "firstCut" to "0.2", f.key to v),
                    advOpen = true,
                )
                check("관용 나사 $f = $v") { draw(false, false) { ThreadScreen(pipe) } }
            }
            // 다이스 헤드: 화면 칸 넷 · 다이스 조건 칸 여섯
            val dieBase = mapOf(DieStore.key(DieField.VC, null) to "8")
            for (f in listOf(ThreadField.Z_START, ThreadField.RPM, ThreadField.Z_END, ThreadField.Z_REF)) {
                val die = ThreadState(spec = ThreadTable.pitches(ThreadKind.METRIC, "M10").first(), values = mapOf("zStart" to "5", "zEnd" to "-25", f.key to v), method = ThreadMethod.DIE)
                check("다이스 $f = $v") { draw(false, false, dieBase) { ThreadScreen(die) } }
            }
            for (f in DieField.entries) {
                val die = ThreadState(spec = ThreadTable.pitches(ThreadKind.UN, "1/2").first(), values = mapOf("zStart" to "5", "zEnd" to "-25"), method = ThreadMethod.DIE)
                check("다이스 조건 $f = $v") { draw(false, false, dieBase + (DieStore.key(f, null) to v)) { ThreadScreen(die) } }
            }
            // 전조 다이스: 화면 칸 넷(3/4-20 회사 롤 규격) · 전조 조건 칸 셋(M12×1.5 — 회사 표 밖)
            for (f in listOf(ThreadField.Z_START, ThreadField.RPM, ThreadField.Z_END, ThreadField.Z_REF)) {
                val roll = ThreadState(
                    family = ThreadFamily.UN, spec = CompanyRolls.specs.single { it.size == "3/4" }, unGroup = UnGroup.UN20,
                    values = mapOf("zEnd" to "-16.37", f.key to v), method = ThreadMethod.ROLL,
                )
                check("전조 $f = $v") { draw(false, false) { ThreadScreen(roll) } }
            }
            for (f in RollField.entries) {
                val roll = ThreadState(spec = ThreadTable.pitches(ThreadKind.METRIC, "M12").first { it.pitch == 1.5 }, values = mapOf("zEnd" to "-16.37"), method = ThreadMethod.ROLL)
                check("전조 조건 $f = $v") { draw(false, false, mapOf(RollStore.key(f) to v)) { ThreadScreen(roll) } }
            }
            for (f in PolarField.entries) {
                val p = PolarState(values = mapOf("od" to "80", "w" to "70", "rc" to "3", "tool" to "10", "depth" to "-5", "feed" to "100", f.key to v))
                check("극좌표 $f = $v") { draw(false, false) { PolarScreen(p) } }
            }
            for (b in CutBox.entries) {
                val tab = if (b.cut == null) CutTab.DRILL else CutTab.BASIC
                val c = CuttingState(
                    tab = tab, maker = if (b == CutBox.DRILL_D) "sandvik" else MANUAL, series = if (b == CutBox.DRILL_D) 0 else null,
                    values = mapOf("d" to "50", "vc" to "150", "f" to "0.2", "len" to "120", "noseR" to "0.8", "smax" to "2000", "drillD" to "10", "drillVc" to "100", "drillF" to "0.2", b.key to v),
                ).apply { if (b == CutBox.DRILL_D) pickRow(0) }
                check("절삭 $b = $v") { draw(false, false) { CuttingScreen(c) } }
            }
        }
    }
}
