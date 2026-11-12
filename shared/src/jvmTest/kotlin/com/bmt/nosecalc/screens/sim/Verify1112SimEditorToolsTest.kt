package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.sim.GText
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.screens.SimulatorScreen
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2026-11-12 통합 고침 S1(편집기 쓰는 줄 × 공구 저장) 독립 검증 — 구현자의 시험과 따로 쓴 검사.
 * 무작위: SimulatorScreen 과 같은 배선(상태만 시험이 쥔다)에 키패드 · 새 줄 · 지우기 · 줄 옮김 · [저장](번호 · 덮어쓰기 창) · [목록](저장한 것 · 예제 · 새 프로그램,
 * 묻는 창 취소 · 버리고) · [⋯] 새 프로그램 · 궤적 확인(공구 설정 시트에서 공구표 · 바 지름 · 소재 바꿈) · 쓰는 중 공구표 바꿈(모델) · 화면 닫고 다시 · 프로세스
 * 죽음(Saver 있음 · 없음)을 섞어 누르고, 매번 시험이 따로 쥔 기대(저장한 글 · 설정 그림자, 기준 설정)와 맞춘다.
 * 진짜 `SimulatorScreen()` 은 보이는 글 · 저장소로 몇 길을 따로 본다. 글 · 공구 값은 새로 쓴 것.
 */
@OptIn(ExperimentalComposeUiApi::class)
class Verify1112SimEditorToolsTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-v1112s", ".properties").apply { delete() })

    /** G42 를 쓰는 글(공구 1 의 노즈 R · 팁이 없으면 멈춤) */
    private val textP = "O2001 (P)\nT0101\nG97S1000M03\nG99\nG0X60.Z50.\nG42\nG0X40.Z20.\nZ1.\nG1Z-20.F.1\nX44.F.3\nG40\nG0Z50.\nM30\n"
    private val textQ = "O2002 (Q)\nT0202\nG97S800M03\nG99\nG0X30.Z5.\nG1Z-15.F.15\nX36.\nG0Z5.\nM30\n"
    private val toolsP = mapOf(1 to ToolOffset(0.4, 3), 2 to ToolOffset(0.2, 2, barDia = 6.0))
    private val toolsQ = mapOf(1 to ToolOffset(0.8, 3), 2 to ToolOffset(0.4, 3, kappaDeg = 93.0))

    /** O2002(공구 Q) · O2001(공구 P · 소재 Ø50) 을 저장하고 O2001 을 연 저장소 */
    private fun seeded(kv: KeyValueStore = store()): KeyValueStore {
        SimState(kv).apply {
            tools = toolsQ
            editor.replaceAll(textQ)
            assertTrue(requestSave()!!.startsWith("O2002 저장했습니다"))
            tools = toolsP
            setStock(StockField.DIA, "50")
            editor.replaceAll(textP, textP.indexOf("F.1") + 3)
            assertTrue(requestSave()!!.startsWith("O2001 저장했습니다"))
        }
        return kv
    }

    private inner class Stage(val kv: KeyValueStore, val sim: SimState?) {
        val app = AppState(kv).also { it.open(Feature.SIMULATOR) }
        private val ui = UiQueue()
        private val scene = ImageComposeScene(412, 915, Density(1f), coroutineContext = ui) {
            AppHost(app) {
                val s = sim
                if (s == null) SimulatorScreen() else {
                    val traj = remember { TrajUi() }
                    SimulatorPage(
                        s,
                        onTrajectory = {
                            s.ensureRun()
                            s.trajOpen = true
                        },
                        onTools = {
                            s.ensureRun()
                            traj.sheet = TrajSheet.TOOLS
                            s.trajOpen = true
                        },
                    )
                    if (s.trajOpen) {
                        TrajectoryWindow(
                            s, traj,
                            onClose = {
                                s.trajOpen = false
                                traj.sheet = null
                            },
                            onEditor = { line ->
                                s.trajOpen = false
                                traj.sheet = null
                                s.goToLine(line)
                            },
                        )
                    }
                }
            }
        }
        private var clock = 0L

        fun frame() {
            clock += 16_000_000L
            ui.drain()
            scene.render(clock).close()
        }

        fun idle(ms: Long) {
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                Thread.sleep(15)
                frame()
            }
        }

        fun await(what: String, cond: () -> Boolean) {
            val until = System.currentTimeMillis() + 20_000
            while (!cond()) {
                assertTrue(System.currentTimeMillis() < until, "기다려도 안 됨: $what — 화면 글 ${texts()}")
                Thread.sleep(15)
                frame()
            }
        }

        private fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }

        private fun SemanticsNode.words(): List<String> =
            (config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
                .map { it.replace("⁠", "") }

        fun texts(): List<String> = nodes().flatMap { it.words() }

        fun shows(part: String) = texts().any { part in it }

        private fun find(label: String) = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && n.words().any { it == label } }

        fun can(label: String) = find(label) != null

        /** 누를 것이 나올 때까지 기다렸다 누른다(시트 · 창은 한 프레임 늦게 뜬다) */
        fun tap(label: String) {
            await("누를 것: $label") { find(label) != null }
            find(label)!!.config[SemanticsActions.OnClick].action?.invoke()
            frame()
            frame()
        }

        private var closed = false

        /** 두 번 닫아도 된다(죽음 흉내에서 새 화면을 만들다 실패하면 finally 가 옛 화면을 또 닫는다 — 원래 오류를 가리지 않게) */
        fun close() {
            if (closed) return
            closed = true
            scene.close()
        }
    }

    private data class Saved(val text: String, val setup: String)

    private fun same(a: SimRun, b: SimRun) = a.stop == b.stop && a.issues == b.issues && a.steps.size == b.steps.size && a.ghosts.size == b.ghosts.size

    /**
     * 무작위 섞어 누르기. 매 걸음 뒤: 저장소의 작업 중 설정 = 지금 설정, 저장한 글 · 설정 = 그림자, '● 고침' = 그림자로 셈한 것(화면 글도),
     * 궤적 창이 열려 있으면 쓰는 줄 없음 · 실행 = 지금 글 전체 · 지금 공구표로 새로 돈 것(멈춤이면 창 띠에 그 줄), 쉰 뒤엔 실행 = 검사 글 · 지금 공구표.
     */
    private fun monkey(seed: Int, steps: Int) {
        val rnd = Random(seed)
        // 프로세스가 죽었다 살면 새 저장소 객체가 같은 파일을 다시 읽는다(옛 객체의 글은 옛 프로세스 것)
        val file = File.createTempFile("bmt-v1112m", ".properties").apply { delete() }
        var kv = seeded(KeyValueStore(file))
        val shadow = HashMap<Int, Saved>()
        shadow[2001] = Saved(kv.get(ProgramStore.key(2001))!!, kv.get(ProgramStore.setupKey(2001))!!)
        shadow[2002] = Saved(kv.get(ProgramStore.key(2002))!!, kv.get(ProgramStore.setupKey(2002))!!)
        var sim = SimState(kv)
        var base: ProgramSetup = sim.setupNow().canonical()
        var st = Stage(kv, sim)
        val log = ArrayList<String>()

        fun baseline(o: Origin): String = when (o) {
            Origin.New -> ""
            is Origin.Saved -> shadow.getValue(o.number).text
            is Origin.Example -> exampleList[o.index].text
        }

        fun textMod() = sim.editor.text != baseline(sim.origin)
        fun setupMod() = sim.origin is Origin.Saved && sim.setupNow().canonical() != base

        fun check(where: String) {
            assertEquals(sim.setupNow().canonical(), ProgramSetup.decode(kv.get(ProgramStore.DRAFT_SETUP)!!)!!.canonical(), "$where: 작업 중 설정 저장소")
            for ((n, s) in shadow) {
                assertEquals(s.text, sim.programs.load(n), "$where: O$n 글")
                assertEquals(s.setup, sim.programs.setup(n), "$where: O$n 설정")
            }
            assertEquals(textMod(), sim.textModified, "$where: 글 고침")
            assertEquals(setupMod(), sim.setupModified, "$where: 설정 고침")
            if (sim.trajOpen) {
                assertNull(sim.editor.writingLine, "$where: 궤적 창이 열렸는데 쓰는 줄")
                assertEquals(Triple(sim.editor.text, sim.tools, sim.runSetup()), sim.runKey, "$where: 궤적 창의 실행 열쇠")
                val fresh = sim.runOf(sim.editor.text, sim.tools)
                assertTrue(same(fresh, sim.run), "$where: 궤적 창의 실행이 지금 글 전체 · 공구표와 다르다 ${sim.run.stop} / ${fresh.stop}")
                fresh.stop?.let { s -> st.await("$where: 궤적 창 멈춤 띠 ${s.line}") { st.shows("${lineLabel(s.line)}에서 멈춤") } }
            } else if (sim.ask == null && sim.sheet == null) {
                val want = when {
                    sim.modified && sim.editor.text.isNotEmpty() -> "● 고침"
                    sim.origin is Origin.Saved -> "✓ 저장됨"
                    sim.origin is Origin.Example -> "예제"
                    else -> "새 프로그램"
                }
                st.await("$where: 상태 글 $want") { st.shows(want) }
                for (other in listOf("● 고침", "✓ 저장됨").filter { it != want }) assertFalse(st.shows(other), "$where: 상태 글이 $want 인데 $other 도 보임 ${st.texts()}")
            }
        }

        fun settle(where: String) {
            if (sim.trajOpen) return
            st.await("$where: 검사 글로 다시 돎") { sim.runKey == Triple(sim.editor.checkedText, sim.tools, sim.runSetup()) }
            assertTrue(same(sim.runOf(sim.editor.checkedText, sim.tools), sim.run), "$where: 쉰 뒤 실행이 검사 글과 다르다")
        }

        fun mutateTools() {
            when (rnd.nextInt(4)) {
                0 -> sim.tools = sim.tools + (1 to ToolOffset(listOf(0.2, 0.4, 0.8)[rnd.nextInt(3)], 3))
                1 -> sim.tools = sim.tools + (2 to (sim.tools[2] ?: ToolOffset(0.2, 2)).copy(barDia = listOf(null, 5.0, 8.0)[rnd.nextInt(3)]))
                2 -> sim.setStock(StockField.DIA, listOf("", "50", "70")[rnd.nextInt(3)])
                else -> sim.tools = sim.tools - 1
            }
        }

        /** 여는 쪽이 바뀌었을 때 기대(글 · 설정 · 쓰는 줄 · 기준) */
        fun opened(what: Pending, before: ProgramSetup) {
            assertNull(sim.ask)
            assertNull(sim.editor.writingLine)
            assertEquals(sim.editor.text, sim.editor.checkedText)
            when (what) {
                is Pending.Load -> {
                    val s = shadow.getValue(what.number)
                    assertEquals(EditorState.clean(s.text), sim.editor.text)
                    assertEquals(Origin.Saved(what.number), sim.origin)
                    val want = s.setup.takeIf { it.isNotEmpty() }?.let { ProgramSetup.decode(it)!! } ?: before
                    assertEquals(want.canonical(), sim.setupNow().canonical(), "연 O${what.number} 의 설정")
                    base = sim.setupNow().canonical()
                }
                is Pending.Example -> {
                    val e = exampleList[what.index]
                    assertEquals(EditorState.clean(e.text), sim.editor.text)
                    assertEquals(before.tools + e.tools, sim.tools)
                }
                Pending.New -> {
                    assertEquals("", sim.editor.text)
                    assertEquals(before.canonical(), sim.setupNow().canonical())
                }
            }
            assertFalse(sim.modified, "연 뒤 고침")
        }

        fun request(what: Pending, tapIt: () -> Unit) {
            val beforeText = sim.editor.text
            val beforeSetup = sim.setupNow()
            val ask = textMod() && beforeText.isNotBlank() || setupMod()
            tapIt()
            assertEquals(ask, sim.ask is SimAsk.Unsaved, "묻는 창(글 고침 ${textMod()} · 설정 고침 ${setupMod()})")
            if (ask) {
                if (rnd.nextBoolean()) {
                    log += "  → 취소"
                    st.tap("취소")
                    assertNull(sim.ask)
                    assertEquals(beforeText, sim.editor.text)
                    assertEquals(beforeSetup, sim.setupNow())
                    sim.sheet = null
                    st.frame()
                    return
                }
                log += "  → 버리고"
                st.tap(if (what == Pending.New) "버리고 새로" else "버리고 열기")
            }
            opened(what, beforeSetup)
        }

        fun save() {
            st.tap("저장")
            var guard = 0
            while (guard++ < 3) {
                when (val a = sim.ask) {
                    null -> {
                        val n = GText.parse(sim.editor.text).units.first().number
                        if (n != null && sim.origin == Origin.Saved(n) && sim.programs.load(n) == sim.editor.text) {
                            shadow[n] = Saved(sim.editor.text, sim.setupNow().encode())
                            base = sim.setupNow().canonical()
                            assertNull(sim.editor.writingLine, "저장 뒤 쓰는 줄")
                            log += "  → O$n 저장"
                        }
                        return
                    }
                    SimAsk.Number -> {
                        if (rnd.nextBoolean()) {
                            log += "  → 번호 창 취소"
                            st.tap("취소")
                            return
                        }
                        val n = 2003 + rnd.nextInt(2)
                        log += "  → 번호 $n"
                        sim.saveAs(n)
                        st.frame()
                    }
                    is SimAsk.Overwrite -> {
                        assertTrue(a.number in shadow && a.number != (sim.origin as? Origin.Saved)?.number, "덮어쓰기 창 $a")
                        if (rnd.nextBoolean()) {
                            log += "  → 덮어쓰기 취소"
                            st.tap("취소")
                            return
                        }
                        log += "  → 덮어쓰기"
                        st.tap("덮어쓰기")
                    }
                    else -> error("뜻밖의 창 $a")
                }
            }
        }

        fun restart(kind: Int) {
            val text = sim.editor.text
            val setup = sim.setupNow()
            val origin = sim.origin
            when (kind) {
                0 -> { // 화면을 떠났다 옴(닫힘 = onDispose 가 줄을 끝내고 임시 글을 남김)
                    st.close()
                    sim = SimState(kv)
                    assertEquals(text, sim.editor.text, "다시 연 글")
                }
                1 -> { // 프로세스 죽음(Saver 저장 뒤) — 새 프로세스는 파일을 새로 읽는다. 옛 화면은 새 상태를 만든 뒤에 닫는다(그 글은 옛 저장소 객체로만)
                    val saved = with(SimState.saver(kv)) { SaverScope { true }.save(sim) }
                    val old = st
                    kv = KeyValueStore(file)
                    sim = SimState.saver(kv).restore(assertNotNull(saved))!!
                    old.close()
                    assertEquals(text, sim.editor.text, "Saver 복원 글")
                }
                else -> { // 갑자기 죽음(Saver 없음) — 0.5초 안의 글은 잃을 수 있다(임시 글 그대로)
                    val draft = kv.get(ProgramStore.DRAFT) ?: ""
                    val old = st
                    kv = KeyValueStore(file)
                    sim = SimState(kv)
                    old.close()
                    assertEquals(EditorState.clean(draft), sim.editor.text, "죽은 뒤 글 = 임시 글")
                }
            }
            assertEquals(origin, sim.origin, "다시 켠 뒤 어디서 온 글")
            assertEquals(setup.canonical(), sim.setupNow().canonical(), "다시 켠 뒤 설정")
            assertNull(sim.editor.writingLine)
            assertEquals(sim.editor.text, sim.editor.checkedText)
            st = Stage(kv, sim)
            repeat(3) { st.frame() }
        }

        val keys = listOf("0", "1", "5", ".", "X", "Z", "G", "-", "F")
        try {
            repeat(steps) { i ->
                val a = rnd.nextInt(100)
                val before = "글 ${sim.editor.text.length}자 · 쓰는 줄 ${sim.editor.writingLine} · ${sim.origin}"
                when {
                    a < 28 -> {
                        val k = keys[rnd.nextInt(keys.size)]
                        log += "$i 키 $k ($before)"
                        if (!sim.keypadOpen) st.tap("키패드")
                        st.tap(k)
                    }
                    a < 33 -> {
                        log += "$i 새 줄 ($before)"
                        if (!sim.keypadOpen) st.tap("키패드")
                        st.tap("새 줄")
                    }
                    a < 38 -> {
                        log += "$i 지우기 ($before)"
                        if (!sim.keypadOpen) st.tap("키패드")
                        st.tap("지우기")
                    }
                    a < 44 -> {
                        val d = if (rnd.nextBoolean()) 1 else -1
                        log += "$i 줄 옮김 $d ($before)"
                        sim.editor.vertical(d)
                        st.frame()
                    }
                    a < 52 -> {
                        log += "$i 저장 ($before)"
                        save()
                    }
                    a < 64 -> {
                        val pick = rnd.nextInt(10)
                        val nums = shadow.keys.sorted()
                        log += "$i 목록 $pick ($before)"
                        st.tap("목록")
                        when {
                            pick < 5 -> {
                                val n = nums[rnd.nextInt(nums.size)]
                                log += "  O$n"
                                request(Pending.Load(n)) { st.tap(ProgramStore.oText(n)) }
                            }
                            pick < 8 -> {
                                // 목록은 LazyColumn — 화면에 든 예제만 누를 수 있다
                                st.await("예제 줄") { exampleList.any { st.can(it.name) } }
                                val shown = exampleList.indices.filter { st.can(exampleList[it].name) }
                                val ex = shown[rnd.nextInt(shown.size)]
                                log += "  예제 $ex"
                                request(Pending.Example(ex)) { st.tap(exampleList[ex].name) }
                            }
                            else -> request(Pending.New) { st.tap("새 프로그램") }
                        }
                    }
                    a < 67 -> {
                        log += "$i ⋯ 새 프로그램 ($before)"
                        st.tap("더 보기")
                        request(Pending.New) { st.tap("새 프로그램") }
                    }
                    a < 78 -> {
                        log += "$i 궤적 확인 ($before)"
                        if (sim.keypadOpen && rnd.nextBoolean()) st.tap("키패드 닫기")
                        st.tap("궤적 확인")
                        st.await("궤적 창") { sim.trajOpen }
                        check("$i 궤적 창")
                        if (rnd.nextBoolean()) {
                            log += "  공구 설정에서 바꿈"
                            st.await("공구 설정 단추") { st.can("공구 설정") || st.can("공구") }
                            st.tap(if (st.can("공구 설정")) "공구 설정" else "공구")
                            mutateTools()
                            st.frame()
                            st.await("공구를 바꾼 뒤 다시 돎") { sim.runKey == Triple(sim.editor.text, sim.tools, sim.runSetup()) }
                            check("$i 공구 바꾼 궤적 창")
                        }
                        st.tap("닫기")
                        if (sim.trajOpen) st.tap("닫기")
                        assertFalse(sim.trajOpen, "궤적 창이 닫히지 않음")
                    }
                    a < 84 -> {
                        log += "$i 쓰는 중 공구표 바꿈(모델) ($before)"
                        if (sim.editor.writingLine == null) {
                            if (!sim.keypadOpen) st.tap("키패드")
                            st.tap("0")
                        }
                        val wl = sim.editor.writingLine
                        val gen = sim.editor.checkGen
                        val checked = sim.editor.checkedText
                        mutateTools()
                        st.frame()
                        assertEquals(wl, sim.editor.writingLine)
                        assertEquals(gen, sim.editor.checkGen)
                        assertEquals(checked, sim.editor.checkedText)
                        settle("$i 쓰는 중 공구표")
                        wl?.let { l -> assertTrue(shownIssues(sim.editor, sim.run).none { it.line - 1 == l }) }
                    }
                    a < 90 -> {
                        log += "$i 화면 닫고 다시 ($before)"
                        restart(0)
                    }
                    a < 95 -> {
                        val k = 1 + rnd.nextInt(2)
                        if (k == 2) st.idle(rnd.nextLong(0, 700))
                        log += "$i 프로세스 죽음 ${if (k == 1) "Saver" else "갑자기"} ($before)"
                        restart(k)
                    }
                    else -> {
                        log += "$i 쉼 ($before)"
                        st.idle(rnd.nextLong(100, 600))
                        settle("$i 쉼")
                    }
                }
                check("$i")
            }
            log += "끝: 저장한 것 ${shadow.keys.sorted()}"
        } catch (e: Throwable) {
            throw AssertionError("씨앗 $seed — 마지막 걸음들:\n" + log.takeLast(25).joinToString("\n") + "\n원인: ${e.message}", e)
        } finally {
            st.close()
        }
        println("Verify1112 monkey $seed: ${log.size} 줄, 저장 ${log.count { "저장" in it && "→" in it }} · 열기 ${log.count { "버리고" in it }}")
    }

    @Test
    fun monkeySeed1() = monkey(11201, 120)

    @Test
    fun monkeySeed2() = monkey(11202, 120)

    @Test
    fun monkeySeed3() = monkey(11203, 120)

    @Test
    fun monkeySeed4() = monkey(11204, 120)

    /** 더 길게: 환경 변수 BMT_V1112 = 씨앗 수(씨앗마다 300걸음). 없으면 건너뜀 — 전체 시험을 늘리지 않으려고 */
    @Test
    fun monkeyLong() {
        val n = System.getenv("BMT_V1112")?.toIntOrNull() ?: return
        for (k in 0 until n) {
            monkey(11300 + k, 300)
            System.gc()
        }
    }

    // ---- 쓰는 중 궤적 확인 뒤 실행 객체가 바뀌지 않는가(구현자 22.1 — `SimRun` 구조 같음에 기댐) — 예제 16개 모두

    /** 같은 글 · 공구표로 두 번 돈 실행이 같은 값이어야 0.3초 뒤 편집기 효과가 넣는 실행이 무시된다 */
    @Test
    fun exampleRunsAreEqualValues() {
        val sim = SimState(store())
        val bad = exampleList.filter { e -> sim.runOf(e.text, e.tools) != sim.runOf(e.text, e.tools) }.map { it.name }
        assertTrue(bad.isEmpty(), "두 번 돈 실행이 같은 값이 아닌 예제: $bad")
    }

    /** 예제마다: 글 끝 빈 줄에 빈칸을 쳐 쓰는 중 → 궤적 확인 → 1초 쉬어도 궤적 창이 보는 실행 객체 그대로(재생 · 소재가 새로 시작하지 않음) */
    @Test
    fun trajectoryRunStaysForEveryExample() {
        val bad = ArrayList<String>()
        for (e in exampleList) {
            val kv = store()
            kv.put(ProgramStore.DRAFT, e.text)
            kv.put(ProgramStore.DRAFT_CURSOR, EditorState.clean(e.text).length.toString())
            kv.put(ProgramStore.DRAFT_SETUP, ProgramSetup(e.tools).encode())
            val sim = SimState(kv)
            val st = Stage(kv, sim)
            try {
                repeat(3) { st.frame() }
                st.await("첫 실행 ${e.name}") { sim.runKey != null }
                st.tap("빈칸")
                if (sim.editor.writingLine == null) {
                    bad += "${e.name}: 빈칸을 쳐도 쓰는 줄이 아님"
                    continue
                }
                st.tap("궤적 확인")
                st.await("궤적 창") { sim.trajOpen }
                val run = sim.run
                st.idle(1000)
                if (run !== sim.run) bad += "${e.name}: 궤적 창을 연 뒤 실행 객체가 바뀜(같은 값 ${run == sim.run})"
            } finally {
                st.close()
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString(" / "))
    }

    // ---- 진짜 SimulatorScreen() — 보이는 글과 저장소만

    private fun real(kv: KeyValueStore, block: Stage.() -> Unit) {
        val s = Stage(kv, null)
        try {
            repeat(5) { s.frame() }
            s.block()
        } finally {
            s.close()
        }
    }

    /** 쓰는 줄에 숨은 오류(F.1X) → 궤적 확인: 창 띠가 그 줄 멈춤(조용히 돌지 않음) · 닫으면 편집기 띠에 오류 · 저장소 글은 쓰던 글 */
    @Test
    fun realHiddenErrorStopsInTrajectory() {
        val kv = seeded()
        real(kv) {
            await("O2001") { shows("O2001") && shows("✓ 저장됨") }
            tap("X")
            assertTrue(shows("● 고침"), "${texts()}")
            assertFalse(shows("오류"), "쓰는 줄의 오류가 떴다 ${texts()}")
            tap("궤적 확인")
            await("궤적 창 멈춤 띠") { shows("9줄에서 멈춤") }
            tap("닫기")
            await("편집기 오류 띠") { shows("오류 1") }
            idle(700)
            assertEquals(textP.replace("F.1\n", "F.1X\n"), kv.get(ProgramStore.DRAFT))
        }
    }

    /** 쓰는 중 [저장] → 저장한 글에 쓰던 글자가 들어가고(잃지 않음) 화면을 다시 열어도 '✓ 저장됨' · 같은 공구 */
    @Test
    fun realSaveWhileWritingThenReopen() {
        val kv = seeded()
        real(kv) {
            await("O2001") { shows("O2001") }
            tap("5")
            tap("저장")
            assertEquals(textP.replace("F.1\n", "F.15\n"), kv.get(ProgramStore.key(2001)))
            assertEquals(toolsP, ProgramSetup.decode(kv.get(ProgramStore.setupKey(2001))!!)!!.tools)
            await("저장됨") { shows("✓ 저장됨") && !shows("● 고침") }
        }
        real(kv) {
            await("다시 연 화면") { shows("O2001") && shows("✓ 저장됨") }
            assertFalse(shows("● 고침"), "${texts()}")
        }
    }

    /**
     * 쓰는 중 [목록] → O2002 '버리고 열기' → 화면 닫고 다시: O2002 · 공구 Q · '✓ 저장됨'. 다시 쓰는 중 [⋯] 새 프로그램 '버리고 새로' → 빈 편집기 · 공구표 그대로 ·
     * 다시 열어도 빈 편집기(옛 글이 되살아나지 않음)
     */
    @Test
    fun realDiscardOpenAndNewSurviveReopen() {
        val kv = seeded()
        real(kv) {
            await("O2001") { shows("O2001") }
            tap("0")
            tap("목록")
            tap("O2002")
            tap("버리고 열기")
            await("O2002") { shows("O2002") && !shows("O2001") }
        }
        assertEquals(textQ, kv.get(ProgramStore.DRAFT))
        assertEquals(toolsQ, ProgramSetup.decode(kv.get(ProgramStore.DRAFT_SETUP)!!)!!.tools)
        real(kv) {
            await("다시 연 O2002") { shows("O2002") && shows("✓ 저장됨") }
            tap("1")
            tap("더 보기")
            tap("새 프로그램")
            tap("버리고 새로")
            await("빈 편집기") { shows("새 프로그램") && !shows("O2002") }
        }
        assertEquals("", kv.get(ProgramStore.DRAFT))
        assertEquals(toolsQ, ProgramSetup.decode(kv.get(ProgramStore.DRAFT_SETUP)!!)!!.tools)
        assertEquals(textQ, kv.get(ProgramStore.key(2002)))
        real(kv) {
            await("다시 연 빈 편집기") { shows("새 프로그램") }
            assertFalse(shows("O2002") || shows("O2001"), "${texts()}")
        }
    }
}
