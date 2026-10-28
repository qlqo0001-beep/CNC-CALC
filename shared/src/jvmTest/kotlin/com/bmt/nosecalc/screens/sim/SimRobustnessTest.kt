package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.Shots
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.RevolveMesh
import com.bmt.nosecalc.calc.sim.Section
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.util.Collections
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 시뮬레이터 화면이 죽지 않는가(4단계 최종 점검 — `InputRobustnessTest` 방식).
 * ① 예제 11개를 실제 화면으로 그린다: 편집기(문제 · 목록 · ⋯ 시트) → 궤적 창 2D(×200 재생 · 측정 · 각도 · 설정 · 공구 · 범례 창) → 3D → 다시 편집기.
 * ② 무작위 프로그램 — 예제를 흔든 글(줄 지움 · 겹침 · 숫자 · G 코드 바꿈 · 낱말 더함 · 키패드 글자 끼움)과 키패드 · 붙여넣기 글자만 무작위로 —
 *    × 무작위 공구표(설정 창이 받는 범위) · 소재 칸 · 기계 칸 · 소수점 방식 · 급송 모양 · 블록 건너뛰기로, 화면이 부르는 일을 모두 부른다:
 *    실행(`runOf`) · 문제 글 · 좌표 · 시간 글 · 재생(앞뒤 · 싱글 블록 · M01) · 경로 · 소재 깎기 · 단면 · 3D 메시 · z-버퍼. 몇 개는 실제 화면으로 그린다.
 * 예외 · 백그라운드 예외 없음, 시간 유한, 문제 줄 번호가 글 안, 화면 글에 NaN · Infinity · null 없음을 본다(좌표 글 Fmt 는 NaN 이면 예외).
 * 잰 값은 build/perf/sim-robust.txt.
 */
class SimRobustnessTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-rob", ".properties").apply { delete() })

    private val errors = Collections.synchronizedList(ArrayList<String>())

    private fun check(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            errors += "$what: $t @ " + t.stackTrace.take(4).joinToString(" < ")
        }
    }

    /**
     * 시험 틀에서만 나는 예외: ImageComposeScene 의 처음 효과 디스패처(Unconfined)면 snapshotFlow 가 PC 의 전역 스냅숏 알림(AWT 스레드)으로 깨어
     * 시험 스레드가 그리는 중에 LazyList.scrollToItem 을 부른다(두 스레드가 한 레이아웃을 건드림). 앱은 안드로이드 · PC 모두 한 UI 스레드라 나지 않는다.
     * 장면을 [UiQueue] 로 돌려 없앴다 — 혹시 또 나면 세기만 한다
     */
    private var harness = 0

    private fun text(what: String, s: String) {
        if (s.isBlank() || "NaN" in s || "Infinity" in s || "null" in s) errors += "$what: 화면 글 '$s'"
    }

    // ---- 실제 화면

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.tapText(label: String): Boolean {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        val node = semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.firstOrNull { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null &&
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label }
        } ?: return false
        node.config[SemanticsActions.OnClick].action?.invoke()
        return true
    }

    /**
     * 편집기 + 궤적 창을 실제 화면으로: 편집기 시트 → 궤적 창 2D(재생 ×200 · 측정 · 각도 · 시트 셋) → 3D(재생) → 닫기.
     * [full] 이 아니면 2D 재생 · 3D 만(무작위 글).
     */
    private fun drive(label: String, sim: SimState, full: Boolean) {
        val app = AppState(store())
        app.setTheme(Random(label.hashCode()).nextBoolean())
        val traj = mutableStateOf<TrajUi?>(null)
        val failures = Collections.synchronizedList(ArrayList<Throwable>())
        val handler = CoroutineExceptionHandler { _, e -> failures += e }
        val d = Shots.DENSITY
        val ui = UiQueue()
        val scene = ImageComposeScene(
            (412 * d).roundToInt(), (915 * d).roundToInt(), Density(d), coroutineContext = ui + handler,
        ) {
            AppHost(app) {
                SimulatorPage(sim) {}
                traj.value?.let { TrajectoryWindow(sim, it, onClose = { traj.value = null }, onEditor = { l -> traj.value = null; sim.goToLine(l) }) }
            }
        }
        var clock = 0L
        fun frames(n: Int) = repeat(n) {
            Thread.sleep(10)
            clock += 100_000_000L
            ui.drain()
            scene.render(clock).close()
        }
        fun step(name: String, n: Int = 4, act: () -> Unit) = check("$label · $name") {
            act()
            frames(n)
        }
        try {
            frames(4)
            if (full) {
                for (s in listOf(SimSheet.ISSUES, SimSheet.PROGRAMS, SimSheet.MORE)) {
                    step("편집기 $s") { sim.sheet = s }
                    step("편집기 시트 닫기") { sim.sheet = null }
                }
            }
            sim.speed = 200f
            step("궤적 창 열기", 8) {
                sim.ensureRun()
                traj.value = TrajUi()
            }
            step("재생 ×200", 30) { scene.tapText("재생") }
            val run = sim.run
            if (full) {
                val ui = traj.value ?: TrajUi().also { traj.value = it }
                step("측정") {
                    ui.measure = true
                    ui.pick = run.ghosts.indices.lastOrNull()?.let { Pick(it, measure = true) }
                }
                step("고스트") {
                    ui.measure = false
                    ui.pick = run.ghosts.indices.firstOrNull()?.let { Pick(it, measure = false) }
                }
                step("각도") { ui.angle = true }
                for (s in TrajSheet.entries) {
                    step("시트 $s") { ui.sheet = s }
                    step("시트 닫기") { ui.sheet = null }
                }
                step("싱글 블록 · M01 재생", 12) {
                    sim.singleBlock = true
                    sim.optStop = true
                    scene.tapText("리셋")
                    scene.tapText("재생")
                }
                sim.singleBlock = false
                sim.optStop = false
            }
            val ui = traj.value ?: TrajUi().also { traj.value = it }
            step("3D", 10) {
                ui.mode3d = true
                scene.tapText("리셋")
            }
            step("3D 재생", 20) { scene.tapText("재생") }
            step("3D 자유 · 절개 끔", 10) {
                ui.cam.free = true
                ui.cam.quarter = false
                ui.cam.yaw = 200f
            }
            step("궤적 창 닫기") { traj.value = null }
        } finally {
            scene.close()
        }
        for (e in failures) {
            if ("performMeasureAndLayout called during measure layout" in (e.message ?: "")) harness++
            else errors += "$label 백그라운드: $e @ " + e.stackTrace.take(4).joinToString(" < ")
        }
    }

    @Test
    fun examplesThroughScreens() {
        for ((i, e) in exampleList.withIndex()) {
            val sim = SimState(store()).apply {
                perform(Pending.Example(i))
                run = runOf(editor.text, tools)
            }
            check("예제 ${i + 1} 계산") { exercise("예제 ${i + 1} ${e.name}", sim, Random(i)) }
            drive("예제 ${i + 1} ${e.name}", sim, full = true)
        }
        assertTrue(errors.isEmpty(), "예제 ${errors.size}건:\n" + errors.take(30).joinToString("\n"))
    }

    // ---- 화면이 부르는 일(백그라운드 포함)을 직접

    private val w3 = 200
    private val h3 = 230

    /** 한 프로그램: 화면 글 · 재생 · 경로 · 소재 · 3D */
    private fun exercise(label: String, sim: SimState, rnd: Random) {
        val run: SimRun = sim.run
        val lines = sim.editor.lineCount
        if (!run.totalSec.isFinite() || run.totalSec < 0) errors += "$label: 전체 시간 ${run.totalSec}"
        text("$label 시간", timeText(run.totalSec))
        for (i in run.issues + listOfNotNull(run.stop)) {
            if (i.line !in 0..lines) errors += "$label: 문제 줄 ${i.line} / $lines (${i.code})"
            text("$label ${i.code}", simIssueText(i))
            text("$label 줄", lineLabel(i.line))
        }
        issueCounts(run.issues)
        for (g in run.ghosts) {
            xzText(g.cmd)
            xzText(g.tip)
        }
        if (run.steps.any { !it.t0.isFinite() || !it.t1.isFinite() || it.t1 < it.t0 - 1e-9 }) errors += "$label: 걸음 시각"
        repeat(6) {
            val t = if (it == 0) run.totalSec else rnd.nextDouble() * max(run.totalSec, 1e-9)
            val pose = run.poseAt(t)
            text("$label 위치", xzText(pose.tip))
            text("$label S", sText(pose))
            fUnit(pose)
            pose.feed?.let { f -> text("$label F", feedText(f)) }
            text("$label 줄", lineLabel(pose.line))
        }
        // 재생: 앞뒤 · 싱글 블록 · M01 · 코드 줄 누르기
        val play = PlayState(run)
        play.toggle()
        repeat(40) { play.advance(0.1, 200f, rnd.nextInt(4) == 0, rnd.nextBoolean())?.let { if (!play.playing) play.toggle() } }
        play.blockBack()
        play.blockForward()
        play.seek(rnd.nextDouble() * (run.totalSec + 1))
        play.goLine(rnd.nextInt(lines + 2))
        play.reset()
        val paths = SimPaths(run)
        if (paths.cmd.any { !it.isFinite() } || paths.rapid.any { !it.isFinite() }) errors += "$label: 경로 좌표"
        // 소재: 화면처럼 끝까지 한 번 → 재생 시각을 따라(뒤로는 저장점에서)
        val (spec, _) = stockOf(sim.stockText, run)
        if (spec != null) {
            val s = StockSim(spec, run, sim.tools)
            s.cutTo(run.totalSec)
            s.contacts.forEach { c -> xzText(c.at) }
            val tol = max(0.002, spec.length / 15_000)
            s.cutTo(rnd.nextDouble() * run.totalSec)
            s.profile(tol)
            s.cutTo(run.totalSec)
            val prof = s.profile(tol)
            if (prof.pieces.any { pc -> pc.outline.any { p -> !p.xDia.isFinite() || !p.z.isFinite() } }) errors += "$label: 단면 좌표"
            val quarter = rnd.nextBoolean()
            val mesh = RevolveMesh.part(prof, if (quarter) Section.QUARTER else Section.FULL, RevolveMesh.SEG_DRAG, RevolveMesh.TOL_DRAG, RevolveMesh.TRI_DRAG)
            if (mesh.triangles > RevolveMesh.TRI_DRAG) errors += "$label: 삼각형 ${mesh.triangles} — 소재 ${sim.stockText} · 공구 ${sim.tools} · 글 ${sim.editor.text.lines().joinToString("|")}"
            val cam = Cam3D().apply {
                yaw = rnd.nextFloat() * 360
                pitch = rnd.nextFloat() * 170 - 85
                zoom = 0.2f + rnd.nextFloat() * 6
            }
            val view = viewOf(cam, sceneOf(spec), w3, h3, Rect(8f, 20f, w3 - 30f, h3 - 40f))
            Renderer3D().raster(Job3D(prof, spec, quarter, rnd.nextBoolean(), view, w3, h3))
        }
    }

    // ---- 무작위 글

    private val gCodes = listOf(
        "G00", "G01", "G02", "G03", "G04", "G18", "G20", "G21", "G28", "G40", "G41", "G42", "G50", "G54", "G55", "G65", "G70", "G71", "G72", "G73",
        "G74", "G75", "G76", "G80", "G81", "G90", "G92", "G94", "G96", "G97", "G98", "G99", "G1", "G0", "G999",
    )
    private val mCodes = listOf("M00", "M01", "M02", "M03", "M04", "M05", "M08", "M09", "M10", "M30", "M41", "M98", "M99", "M3", "M198")
    private val addrs = listOf("X", "Z", "U", "W", "I", "K", "R", "F", "S", "P", "Q", "D", "L", "T", "N", "O", "H", "A", "C", "E", ",C", ",R")
    private val nums = listOf(
        "", "0", "0.", "-0.", "1", "1.", "-1.", ".5", "-.5", "0.001", "0.0001", "44", "44.", "-30.", "100.", "1000.", "99999.", "123456789", "9999999999",
        "3.14159", "--1", "1..2", "-", ".", "0101", "1500", "2.", "0.2", "60.", "-60.",
    )
    private val padChars = "ONG789XZS456,MUW123-FTR0.()/IKLPCQ \n"
    private val pasteBits = listOf("(한글 주석)", "\t", "\r\n", "%", ";", "#", "=", "[", "]", "*", "가", "x10.", "g01", "(", ")", "((", "/", ",", " ")

    private fun num(rnd: Random): String = if (rnd.nextInt(3) == 0) nums.random(rnd) else (rnd.nextInt(-80_000, 80_001) / 1000.0).toString()
    private fun word(rnd: Random): String = when (rnd.nextInt(6)) {
        0 -> gCodes.random(rnd)
        1 -> mCodes.random(rnd)
        else -> addrs.random(rnd) + num(rnd)
    }

    private val number = Regex("""-?\d*\.?\d+\.?""")

    /** 예제를 흔든다(1~6번) */
    private fun mutate(src: String, rnd: Random): String {
        val ls = src.split('\n').toMutableList()
        repeat(1 + rnd.nextInt(6)) {
            if (ls.isEmpty()) ls += ""
            val i = rnd.nextInt(ls.size)
            val l = ls[i]
            when (rnd.nextInt(10)) {
                0 -> ls.removeAt(i)
                1 -> ls.add(i, l)
                2, 3 -> {
                    val ms = number.findAll(l).toList()
                    if (ms.isNotEmpty()) {
                        val m = ms.random(rnd)
                        ls[i] = l.replaceRange(m.range, num(rnd).trimStart('-').let { if (rnd.nextBoolean()) "-$it" else it })
                    }
                }
                4 -> ls[i] = "$l ${word(rnd)}"
                5 -> ls.add(i, (0..rnd.nextInt(4)).joinToString(" ") { word(rnd) })
                6 -> ls[i] = l.take(rnd.nextInt(l.length + 1))
                7 -> {
                    val p = rnd.nextInt(l.length + 1)
                    val ins = if (rnd.nextInt(4) == 0) pasteBits.random(rnd) else (0..rnd.nextInt(3)).map { padChars.random(rnd) }.joinToString("")
                    ls[i] = l.substring(0, p) + ins + l.substring(p)
                }
                8 -> {
                    val j = rnd.nextInt(ls.size)
                    ls[i] = ls[j].also { ls[j] = l }
                }
                else -> ls[i] = Regex("""G\d+""").replace(l) { if (rnd.nextInt(3) == 0) gCodes.random(rnd) else it.value }
            }
        }
        return ls.joinToString("\n")
    }

    private fun randomText(rnd: Random): String = when (rnd.nextInt(3)) {
        0 -> (0 until rnd.nextInt(400)).map { padChars.random(rnd) }.joinToString("")
        1 -> (0 until rnd.nextInt(30)).joinToString("\n") { (0..rnd.nextInt(5)).joinToString(" ") { word(rnd) } }
        else -> "O1234 (무작위)\nT0101\nG97 S1000 M03\nG99\n" + (0 until rnd.nextInt(40)).joinToString("\n") { (0..rnd.nextInt(4)).joinToString(" ") { word(rnd) } } + "\nM30"
    }

    /** 공구 설정 창이 받는 범위(노즈 R 0~50 · 팁 0~9 · κr 0~180 · 홈 폭 0~100 · 나사 각 0~180)와 빈칸 */
    private fun randomTools(rnd: Random): Map<Int, ToolOffset> = (1..rnd.nextInt(1, 6)).associate { rnd.nextInt(0, 13) to tool(rnd) }

    private fun tool(rnd: Random): ToolOffset = ToolOffset(
        noseR = listOf(null, 0.0, 0.0001, 0.2, 0.4, 0.8, 1.2, 3.0, 50.0).random(rnd),
        tip = if (rnd.nextInt(6) == 0) null else rnd.nextInt(0, 10),
        kind = ToolKind.entries.random(rnd),
        insert = InsertShape.entries.random(rnd),
        kappaDeg = listOf(null, 0.1, 45.0, 93.0, 95.0, 107.5, 179.9).random(rnd),
        width = listOf(null, 0.01, 2.0, 3.0, 100.0).random(rnd),
        threadDeg = listOf(null, 0.1, 29.0, 55.0, 60.0, 179.9).random(rnd),
    )

    private val fieldNums = listOf("", "", "", "0", "-5", "0.001", "10", "18", "30", "60", "68", "90", "200", "10000", "10000.001", "-10000", "1.", ".", "-", "99999999")

    @Test
    fun fuzzPrograms() {
        val rnd = Random(20261028)
        val n = 2400
        var slowest = 0.0
        var slowestLabel = ""
        var stops = 0
        var drawn = 0
        val t0 = System.nanoTime()
        repeat(n) { k ->
            val label = "무작위 #$k"
            val sim = SimState(store())
            val ex = exampleList.random(rnd)
            val src = if (rnd.nextInt(5) == 0) randomText(rnd) else mutate(ex.text, rnd)
            check("$label 준비") {
                sim.editor.replaceAll(src)
                sim.tools = if (rnd.nextInt(3) == 0) randomTools(rnd) else ex.tools
                if (rnd.nextInt(3) == 0) sim.stockText = StockField.entries.associateWith { fieldNums.random(rnd) }
                if (rnd.nextInt(4) == 0) MachineField.entries.forEach { f -> sim.setMachine(f, fieldNums.random(rnd)) }
                if (rnd.nextInt(5) == 0) sim.changeDecimal(DecimalMode.CALCULATOR)
                if (rnd.nextInt(4) == 0) sim.changeRapidPath(RapidPath.LINEAR)
                if (rnd.nextInt(6) == 0) sim.changeBlockSkip(true)
            }
            val c0 = System.nanoTime()
            check("$label 실행 ${src.take(60).replace('\n', '|')}") {
                sim.run = sim.runOf(sim.editor.text, sim.tools)
                if (sim.run.stop != null) stops++
                exercise(label, sim, rnd)
            }
            val ms = (System.nanoTime() - c0) / 1e6
            if (ms > slowest) {
                slowest = ms
                slowestLabel = "$label (${sim.editor.lineCount}줄 · 이동 ${sim.run.steps.size})"
            }
            // 25 개에 하나는 실제 화면(2D 재생 · 3D)으로
            if (k % 25 == 0) {
                drive(label, sim, full = k % 100 == 0)
                drawn++
            }
        }
        val sec = (System.nanoTime() - t0) / 1e9
        val line = "무작위 $n 개(멈춘 것 $stops · 화면으로 그린 것 $drawn): 전체 ${"%.1f".format(sec)} 초 · 가장 오래 걸린 계산 ${"%.0f".format(slowest)} ms — $slowestLabel · " +
            "문제 ${errors.size} · 시험 틀 예외(레이아웃 경합) $harness"
        println(line)
        File("build/perf").apply { mkdirs() }.resolve("sim-robust.txt").writeText(line + "\n")
        assertTrue(errors.isEmpty(), "무작위 ${errors.size}건:\n" + errors.take(30).joinToString("\n"))
        assertTrue(slowest < 10_000, line)
    }
}
