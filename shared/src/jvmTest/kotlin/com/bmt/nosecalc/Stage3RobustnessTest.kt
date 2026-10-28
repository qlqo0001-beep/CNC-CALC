package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DrillCatalog
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarWarning
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
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
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.fieldHint
import com.bmt.nosecalc.screens.thread.g76Words
import com.bmt.nosecalc.screens.thread.ncText
import com.bmt.nosecalc.screens.thread.sectionGeom
import com.bmt.nosecalc.screens.thread.threadWarningText
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 3단계 화면(나사 G76 · 턴밀 극좌표 · 재료·절삭조건)이 잘못된 입력에서 죽지 않는가 — [InputRobustnessTest] 와 같은 방식.
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

    private fun randomThread(): ThreadState {
        val kind = if (rnd.nextBoolean()) ThreadKind.METRIC else ThreadKind.UN
        val inner = rnd.nextBoolean()
        val specs = ThreadTable.of(kind)
        val spec = if (rnd.nextInt(10) == 0) null else specs[rnd.nextInt(specs.size)]
        val classes = ThreadClass.entries.filter { it.kind == kind && it.internal == inner }
        val base = mapOf("zStart" to "5", "zEnd" to "-25", "firstCut" to "0.2")
        return ThreadState(
            inner = inner,
            kind = kind,
            spec = spec,
            cls = if (rnd.nextBoolean()) null else classes[rnd.nextInt(classes.size)],
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
            val s = randomThread()
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

    private fun draw(dark: Boolean, direct: Boolean, content: @Composable () -> Unit) {
        val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
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
