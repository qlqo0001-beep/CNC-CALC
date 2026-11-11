package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.Shots
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * 시뮬레이터 몽키 시험(사용자 10-28 "팁번호 설정시 팅기는 문제" — 공구 설정의 팁 번호 '고르기' 를 누르면 앱이 꺼졌다. 값만 흔드는
 * [SimRobustnessTest] 는 시트 · 단추를 실제로 눌러 들어가지 않아 못 잡았다). 누를 수 있는 것을 다 눌러 본다.
 *
 * 장면: 실제 화면([SimulatorScreen] 과 같은 짜임 — 편집기 + 궤적 창, 상태만 시험이 맞춘다)을 [ImageComposeScene] 에 띄운다
 * (폭 412 · 360dp, 글꼴 1.0 · 1.3, 라이트 · 다크). 효과는 [UiQueue] 로 시험 스레드에서(앱처럼 한 UI 스레드 — 헤드리스 경합을 없앤다).
 *
 * 누를 것: 의미 트리에서 OnClick(· OnLongClick)이 있고 꺼지지 않았고, 위에 뜬 층(시트 · 창 · 메뉴 덮개)에 가리지 않은 노드 — 손가락이 닿는 것만.
 * 의미 동작으로 누른다(손가락 누름과 같은 onClick). 손짓만 받는 곳(2D · 3D 뷰 · 속도 막대 · 손잡이 · SHIFT 길게)은 실제 터치 이벤트로
 * 누르기 · 끌기 · 길게 · 휠을 섞고, 2D 궤적 창의 처음 층에서는 겨눠서(뷰포트 격자 = 고스트 · 측정점 고르기, 속도 막대) 누른다. 숫자 키패드가 열리면 키(숫자 · . · − · C · ⌫)를 무작위로 누르고 확인 · 취소 · 비우기 · 바깥으로 닫는다.
 *
 * 탐색(넓이 우선): 한 층의 후보를 차례로 누르고(한 장면에서 이어서 — 상태가 쌓인다) 몇 프레임 그린다. 누른 뒤 새 노드가 생기면
 * (시트 · 창 · 메뉴 · 찾기 줄 …) 그 경로를 다음 깊이로 줄에 넣는다. 층이 가려지거나 닫히면 여는 단추를 다시 누르거나 뒤로 한 번,
 * 그래도 안 돌아오면 처음 상태에서 경로를 다시 밟는다. 앱 화면을 떠나면 그 경로는 끝.
 *
 * 예외(그리기 · 누르기 · LaunchedEffect · 백그라운드)는 시작 상태 · 설정 · 처음부터의 누르기 경로 · 스택을 모으고, 끝에 그 경로를 다시
 * 밟아 재현되는지 본다. 교착은 감시 스레드가(진행이 [STALL_MS] 동안 없으면) 스택을 남긴다. 실행(GSim)이 [RUN_WAIT_MS] 안에 안 끝나도 적는다.
 * 씨앗 [SEED] 고정. 한 부분(시험 함수)은 [PART_MS] 안에서 끊는다(15분 안). 기록은 build/monkey/<부분>.txt.
 */
class SimMonkeyTest {
    /**
     * 부분마다 8~11분 · 합쳐 약 50분 — BMT_MONKEY 가 있을 때만(평소 jvmTest 에서는 건너뜀). 값은 보지 않는다(UiMonkeyTest 와 달리 배율이 아니다 — 부분 길이는
     * [PART_MS] 고정). MonkeyRecheckTest 처럼 직접 부르면 이 검사를 거치지 않는다
     */
    @BeforeTest
    fun onlyWhenAsked() = assumeTrue(System.getenv("BMT_MONKEY") != null, "BMT_MONKEY 가 없어 건너뜀(약 50분 걸리는 몽키 시험)")

    // ---- 설정 · 시작 상태

    internal data class Cfg(val w: Int, val h: Int, val font: Float, val dark: Boolean) {
        override fun toString() = "${w}dp·글꼴 $font·${if (dark) "다크" else "라이트"}"
    }

    internal class Start(
        val name: String,
        val traj: Boolean = false,
        val sheet: TrajSheet? = null,
        val kv: (KeyValueStore) -> Unit = {},
        val make: (KeyValueStore) -> SimState = { SimState(it) },
        val prep: SimState.() -> Unit = {},
    )

    /** 누르기 하나. 경로는 이것의 목록(처음 상태에서 차례로) */
    private sealed interface Act {
        /** 의미 노드 — key = 설명 · 글, nth = 같은 key 의 몇 번째, long = 길게(OnLongClick · SHIFT 는 실제 터치로 길게) */
        data class Tap(val key: String, val nth: Int, val long: Boolean = false) : Act {
            override fun toString() = "'$key'" + (if (nth > 0) "#$nth" else "") + (if (long) " 길게" else "")
        }

        /** 숫자 키패드: 0-9 . -(부호) C <(지우기) 차례로, 끝 D 확인 · X 취소 · B 비우기 · S 바깥 누름 */
        data class Pad(val keys: String) : Act {
            override fun toString() = "키패드[$keys]"
        }

        /** 스크롤 칸을 끝 쪽으로(목록 아래 항목을 꺼낸다) */
        data class Scroll(val key: String, val nth: Int, val dy: Float) : Act {
            override fun toString() = "스크롤 '$key' ${dy.roundToInt()}"
        }

        /** 터치(장면 비율 자리): 0 누름 · 1 끌기 · 2 길게 · 3 휠(마우스) · 4 두 손가락 핀치(dx = 배율) */
        data class Touch(val kind: Int, val x: Float, val y: Float, val dx: Float = 0f, val dy: Float = 0f) : Act {
            override fun toString() = listOf("누름", "끌기", "길게", "휠", "핀치")[kind] + "(${pct(x)},${pct(y)}" +
                (if (kind == 1 || kind == 3) " → ${pct(dx)},${pct(dy)}" else "") + (if (kind == 4) " ×$dx" else "") + ")"
        }

        data object Back : Act {
            override fun toString() = "뒤로"
        }
    }

    /** 후보 노드: 손가락이 닿고(덮이지 않음) 꺼지지 않은 것 */
    private class Cand(val node: SemanticsNode, val key: String, val nkey: String, val nth: Int, val long: Boolean, val pointer: Boolean, val center: Offset)

    private sealed interface Res {
        data object Ok : Res
        data object Miss : Res
        class Fail(val e: Throwable, val where: String) : Res
    }

    /**
     * [UiQueue] 처럼 효과의 재개를 줄에 쌓았다가 시험 스레드가 프레임 사이에 돌린다. 다만 장면을 닫은 뒤에 오는 재개는 그 자리에서 돌린다 —
     * 줄에 남겨 두면 Recomposer 의 취소가 끝나지 않아 전역 스냅숏 관찰자로 남고, 닫은 장면 전체(Skia 그림 포함)가 풀리지 않는다
     * (장면을 수백 번 만드는 이 시험에서 시험 JVM 이 14 GB 까지 커져 죽었다 — 시험 틀만의 일)
     */
    private class SceneQueue : CoroutineDispatcher() {
        private val q = ConcurrentLinkedQueue<Runnable>()

        @Volatile var closed = false

        override fun isDispatchNeeded(context: CoroutineContext) = !closed

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (closed) block.run() else q.add(block)
        }

        fun drain() {
            while (true) (q.poll() ?: return).run()
        }
    }

    /** 앱이 끝나지 않음(실행 · 소재 깎기 · 교착) */
    private class Hang(msg: String) : RuntimeException(msg)

    /** 감시가 버린 일꾼 — 조용히 끝낸다 */
    private class Abandoned : RuntimeException()

    private class Failure(val start: String, val cfg: Cfg, val trail: List<Act>, val where: String, val e: Throwable, val extra: String = "") {
        val sig: String = signature(e)
        var repro: String = "다시 밟지 않음"
    }

    // ---- 한 장면

    /** 한 부분(시험 함수)의 공용: 진행 시각(감시) · 세대(버린 일꾼 끊기) · 지금 장면 · 모은 것 */
    private inner class Part(val name: String, budgetMs: Long) {
        val began = System.currentTimeMillis()
        val deadline = began + budgetMs
        var renderNs = 0L
        var frameCount = 0
        var peakMb = 0L
        var memoryStop = false

        /** 시험 JVM 의 메모리(커밋)가 MEM_LIMIT_MB 를 넘으면 GC, 그래도 넘으면 이 부분을 끝낸다 — 같은 PC 의 다른 일(테섭 · 다른 빌드)을 지킨다 */
        fun guardMemory() {
            if (frameCount % 30 != 0) return
            val mb = committedMb()
            peakMb = max(peakMb, mb)
            if (mb < MEM_LIMIT_MB) return
            System.gc()
            Thread.sleep(200)
            if (committedMb() >= MEM_LIMIT_MB && !memoryStop) {
                memoryStop = true
                log += "  (메모리 ${mb}MB — 이 부분을 여기서 끝냄)"
                throw Abandoned()
            }
        }
        val progress = AtomicLong(System.nanoTime())
        @Volatile var generation = 0
        @Volatile var session: Session? = null
        @Volatile var jobName = ""
        val failures: MutableList<Failure> = Collections.synchronizedList(ArrayList())
        val harness: MutableList<String> = Collections.synchronizedList(ArrayList())
        val log: MutableList<String> = Collections.synchronizedList(ArrayList())
        val uncaught: MutableList<Throwable> = Collections.synchronizedList(ArrayList())
        var sweeps = 0
        var steps = 0
        var resets = 0
        var misses = 0
        var leftScreen = 0
        var unexplored = 0

        /** 다시 확인의 무작위 깊은 걸음 수([walk]) */
        var walks = 0

        /** 누른 것(이름 → 횟수) · 누르기가 낸 효과(고스트 고름 · 속도 · 보기 옮김 → 횟수) — 보고에서 무엇을 실제로 건드렸는지 본다 */
        val pressed = ConcurrentHashMap<String, Int>()
        val effects = ConcurrentHashMap<String, Int>()

        fun record(s: Session, f: Res.Fail) {
            val e = f.e
            if (isHarness(e)) {
                harness += "${s.start.name} · ${s.cfg}: ${e.javaClass.simpleName} ${e.message?.take(80)}"
                return
            }
            failures += Failure(s.start.name, s.cfg, s.trail.toList(), f.where, e)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private inner class Session(val part: Part, val start: Start, val cfg: Cfg) : AutoCloseable {
        private val gen = part.generation
        private val file = File.createTempFile("bmt-monkey", ".properties").apply { delete() }
        val store = KeyValueStore(file).also(start.kv)
        val app = AppState(store).apply {
            setTheme(cfg.dark)
            open(Feature.SIMULATOR)
        }
        val sim: SimState = start.make(store).apply {
            start.prep(this)
            run = runOf(editor.text, tools)
            runKey = Triple(editor.text, tools, runSetup())
            trajOpen = start.traj
        }
        val traj = TrajUi().apply { sheet = start.sheet }
        val trail = ArrayList<Act>()
        private val effectErrors = Collections.synchronizedList(ArrayList<Throwable>())
        private val ui = SceneQueue()
        private var clock = 0L
        val wpx = (cfg.w * DENS).roundToInt()
        val hpx = (cfg.h * DENS).roundToInt()
        private val scene: ImageComposeScene

        init {
            Snapshot.sendApplyNotifications()
            scene = ImageComposeScene(
                wpx, hpx, Density(DENS, cfg.font),
                coroutineContext = ui + CoroutineExceptionHandler { _, e -> effectErrors += e },
            ) {
                // SimulatorScreen 과 같은 짜임(상태만 시험이 넣는다). 편집기는 표시 노드로 감싸 위에 뜬 층이 있을 때 가려진 것을 가른다
                AppHost(app) {
                    Box(Modifier.fillMaxSize().semantics { testTag = SCREEN }) {
                        SimulatorPage(
                            sim,
                            onTools = {
                                sim.ensureRun()
                                traj.sheet = TrajSheet.TOOLS
                                sim.trajOpen = true
                            },
                        ) {
                            sim.ensureRun()
                            sim.trajOpen = true
                        }
                    }
                    if (sim.trajOpen) {
                        TrajectoryWindow(
                            sim, traj,
                            onClose = {
                                sim.trajOpen = false
                                traj.sheet = null
                            },
                            onEditor = { line ->
                                sim.trajOpen = false
                                traj.sheet = null
                                sim.goToLine(line)
                            },
                        )
                    }
                }
            }
            part.session = this
        }

        fun frames(n: Int, sleepMs: Long = 4) = repeat(n) {
            if (part.generation != gen) throw Abandoned()
            part.guardMemory()
            if (sleepMs > 0) Thread.sleep(sleepMs)
            Snapshot.sendApplyNotifications()
            ui.drain()
            clock += 100_000_000L
            val t = System.nanoTime()
            scene.render(clock).close()
            part.renderNs += System.nanoTime() - t
            if (++part.frameCount % GC_FRAMES == 0) System.gc()
            ui.drain()
            part.progress.set(System.nanoTime())
        }

        private fun runFresh() = sim.runKey == Triple(sim.editor.checkedText, sim.tools, sim.runSetup())

        /** 몇 프레임 + (waitRun 이면) 글 · 공구 · 설정을 바꾼 뒤의 실행이 끝날 때까지 */
        fun settle(waitRun: Boolean): Res {
            try {
                frames(3)
                if (waitRun) {
                    val t0 = System.currentTimeMillis()
                    while (!runFresh()) {
                        if (System.currentTimeMillis() - t0 > RUN_WAIT_MS) {
                            return Res.Fail(Hang("실행(GSim)이 ${RUN_WAIT_MS / 1000}초 안에 끝나지 않음 — 글 ${sim.editor.text.length}자"), "실행 기다림")
                        }
                        frames(1, 15)
                    }
                }
            } catch (e: Abandoned) {
                throw e
            } catch (e: Throwable) {
                return Res.Fail(e, "그리기(구성 · 배치 · 그림)")
            }
            return pending()
        }

        /** 효과 · 백그라운드 · 다른 스레드에서 난 예외 */
        fun pending(): Res {
            effectErrors.firstOrNull()?.let { e ->
                effectErrors.clear()
                return Res.Fail(e, "효과(LaunchedEffect · 백그라운드)")
            }
            part.uncaught.firstOrNull()?.let { e ->
                part.uncaught.clear()
                return Res.Fail(e, "다른 스레드")
            }
            return Res.Ok
        }

        /** 끝내기 전: 실행 · 소재 깎기(‘소재를 깎는 중’ 칩)가 끝날 때까지 — 늦게 나는 예외도 거둔다 */
        fun finish(): Res {
            val r = settle(waitRun = true)
            if (r != Res.Ok) return r
            val t0 = System.currentTimeMillis()
            try {
                while (texts().any { "소재를 깎는 중" in it }) {
                    if (System.currentTimeMillis() - t0 > STOCK_WAIT_MS) {
                        return Res.Fail(Hang("소재 깎기가 ${STOCK_WAIT_MS / 1000}초 안에 끝나지 않음"), "소재 기다림")
                    }
                    frames(1, 20)
                }
            } catch (e: Abandoned) {
                throw e
            } catch (e: Throwable) {
                return Res.Fail(e, "그리기(구성 · 배치 · 그림)")
            }
            return pending()
        }

        private fun flat(): Pair<List<SemanticsNode>, IntArray> {
            val list = ArrayList<SemanticsNode>()
            val ends = ArrayList<Int>()
            fun walk(n: SemanticsNode) {
                val i = list.size
                list += n
                ends += 0
                n.children.forEach(::walk)
                ends[i] = list.size
            }
            scene.semanticsOwners.forEach { walk(it.rootSemanticsNode) }
            return list to ends.toIntArray()
        }

        /** 위에 뜬 층 수(시트 · 묻는 창 · 궤적 창 · 키패드 + 메뉴) — 줄면 무엇을 닫은 것 */
        fun layers() = app.overlays.size + (if (app.menuOpen) 1 else 0)

        fun texts(): List<String> = flat().first.flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

        /** 글이 label 인 (눌리지 않는) 노드의 자리 — 숫자 칸 라벨 */
        fun textBounds(label: String): Rect? =
            flat().first.firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }?.let(::boundsOf)

        private fun boundsOf(n: SemanticsNode): Rect {
            val b = n.boundsInRoot
            return if (b.width > 0f && b.height > 0f) b else Rect(n.positionInRoot, n.size.toSize())
        }

        private fun fullScreen(r: Rect) = r.width >= wpx * 0.95f && r.height >= hpx * 0.6f

        /**
         * 손가락이 닿는 후보(그린 차례 = 의미 트리 앞 순서): 위에 뜬 층이 있으면 편집기 노드는 가려진다(모든 층이 화면 전체를 덮는다 —
         * 시트 · 묻는 창의 덮개, 궤적 창). 뒤에 그린(위에 있는) 눌리는 노드가 가운데를 덮거나 화면 전체 덮개면 가려진 것.
         * scroll = 스크롤 칸(같은 규칙)
         */
        fun candidates(scroll: Boolean = false): List<Cand> {
            val (nodes, ends) = flat()
            val screen = nodes.indexOfFirst { it.config.getOrNull(SemanticsProperties.TestTag) == SCREEN }
            val layered = app.overlays.isNotEmpty() && screen >= 0
            val clickAt = nodes.indices.filter { nodes[it].config.getOrNull(SemanticsActions.OnClick) != null }
            val bounds = nodes.map { it.boundsInRoot }
            val out = ArrayList<Cand>()
            val seen = HashMap<String, Int>()
            for (i in nodes.indices) {
                val n = nodes[i]
                val c = n.config
                val click = c.getOrNull(SemanticsActions.OnClick) != null
                val longClick = c.getOrNull(SemanticsActions.OnLongClick) != null
                val scrolls = c.getOrNull(SemanticsActions.ScrollBy) != null
                if (scroll) {
                    if (!scrolls) continue
                } else if (!click && !longClick) continue
                if (c.contains(SemanticsProperties.Disabled)) continue
                if (layered && i in screen until ends[screen]) continue
                val b = boundsOf(n)
                val at = b.center
                val hidden = clickAt.any { j -> j >= ends[i] && (bounds[j].contains(at) || fullScreen(bounds[j])) }
                if (hidden) continue
                val k = keyOf(n, b)
                val nth = seen.merge(k, 1, Int::plus)!! - 1
                val nk = normKey(n, k)
                if (scroll) {
                    out += Cand(n, k, nk, nth, long = false, pointer = false, at)
                    continue
                }
                if (click) out += Cand(n, k, nk, nth, long = false, pointer = false, at)
                if (longClick) out += Cand(n, k, nk, nth, long = true, pointer = false, at)
                // SHIFT 길게 = 잠금(손짓으로만 받는다)
                if (c.getOrNull(SemanticsProperties.Role) == Role.Button &&
                    c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("SHIFT") }
                ) {
                    out += Cand(n, k, nk, nth, long = true, pointer = true, at)
                }
            }
            return out
        }

        private fun keyOf(n: SemanticsNode, b: Rect): String {
            val c = n.config
            val s = listOfNotNull(
                c.getOrNull(SemanticsActions.OnClick)?.label,
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("/"),
                c.getOrNull(SemanticsProperties.Text)?.joinToString("/") { it.text },
            ).filter { it.isNotBlank() }.joinToString("|").replace('\n', ' ').take(48)
            return s.ifEmpty { if (fullScreen(b)) "(바깥 덮개)" else "(이름 없음 ${(b.width / DENS).roundToInt()}×${(b.height / DENS).roundToInt()})" }
        }

        /** 층 비교 · 중복 줄이기용: 첫 이름(누름 이름 · 설명 · 첫 글)만, 숫자는 # */
        private fun normKey(n: SemanticsNode, key: String): String {
            val c = n.config
            val first = c.getOrNull(SemanticsActions.OnClick)?.label
                ?: c.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: c.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                ?: key
            return first.replace(Regex("[0-9]+"), "#").take(24)
        }

        /** 누르기 하나 + 정리. id 가 있으면 그 노드(한 장면 안), 없으면 key · nth 로 찾는다 */
        fun step(a: Act, id: Int? = null, waitRun: Boolean = true): Res {
            part.steps++
            val pick0 = traj.pick
            val speed0 = sim.speed
            val view0 = traj.viewer.userView
            try {
                when (a) {
                    is Act.Tap -> {
                        val all = candidates()
                        val t = id?.let { i -> all.firstOrNull { it.node.id == i && it.long == a.long } }
                            ?: all.firstOrNull { it.key == a.key && it.nth == a.nth && it.long == a.long }
                            ?: return Res.Miss
                        trail += Act.Tap(t.key, t.nth, t.long)
                        part.pressed.merge(t.key.substringBefore('|').take(28) + (if (t.long) " (길게)" else ""), 1, Int::plus)
                        press(t)
                    }
                    is Act.Pad -> {
                        trail += a
                        for (k in a.keys) {
                            val label = PAD_LABEL.getValue(k)
                            val t = candidates().lastOrNull { it.key == label && !it.long } ?: return Res.Miss
                            part.pressed.merge("키패드 $label", 1, Int::plus)
                            press(t)
                            frames(1)
                        }
                    }
                    is Act.Scroll -> {
                        val t = candidates(scroll = true).firstOrNull { it.key == a.key && it.nth == a.nth } ?: return Res.Miss
                        trail += a
                        part.pressed.merge("스크롤", 1, Int::plus)
                        t.node.config[SemanticsActions.ScrollBy].action?.invoke(0f, a.dy * DENS)
                    }
                    is Act.Touch -> {
                        trail += a
                        part.pressed.merge("손짓 " + a.toString().substringBefore('('), 1, Int::plus)
                        touch(a)
                    }
                    Act.Back -> {
                        trail += a
                        part.pressed.merge("뒤로(키)", 1, Int::plus)
                        app.back()
                    }
                }
            } catch (e: Abandoned) {
                throw e
            } catch (e: Throwable) {
                return Res.Fail(e, "누르기(onClick)")
            }
            val r = settle(waitRun)
            traj.pick?.takeIf { it != pick0 }?.let { part.effects.merge(if (it.measure) "측정점 고름" else "고스트 고름", 1, Int::plus) }
            if (sim.speed != speed0) part.effects.merge("속도 바뀜", 1, Int::plus)
            if (traj.viewer.userView != view0) part.effects.merge("2D 보기 옮김 · 확대 · 맞춤", 1, Int::plus)
            return r
        }

        /**
         * 겨눈 손짓(의미 노드가 없어 손짓으로만 받는 곳): 2D 궤적 창이면 뷰포트를 격자로 눌러 고스트를 고르고, 측정을 켜 측정점을 고른 뒤 끄고,
         * 속도 막대를 세 곳 누르고 끈다. 자리는 장면 비율로 적어 다시 밟을 수 있다(무작위 손짓만으로는 작은 고스트 원에 거의 닿지 않는다)
         */
        fun aimed(cols: Int = 6, rows: Int = 5, measure: Boolean = true): List<Act> {
            if (!sim.trajOpen || traj.sheet != null) return emptyList()
            // 다시 확인: 3D 에서도 카메라 손짓(두 손가락 오므리기 · 벌리기 · 끌기)
            if (traj.mode3d) return if (recheck) listOf(Act.Touch(4, 0.5f, 0.35f, 0.5f), Act.Touch(4, 0.5f, 0.35f, 2.2f), Act.Touch(1, 0.5f, 0.35f, 0.25f, 0.1f)) else emptyList()
            val speed = textBounds("속도") ?: return emptyList()
            val cands = candidates()
            val bottom = (textBounds("가공 시간")?.top ?: speed.top) - 4 * DENS
            fun grid(cols: Int, rows: Int) = (0 until rows).flatMap { r ->
                (0 until cols).map { c -> Offset(wpx * (c + 0.5f) / cols, hpx * 0.08f + (bottom - hpx * 0.08f) * (r + 0.5f) / rows) }
            }.filter { p -> cands.none { it.node.boundsInRoot.contains(p) } }.map { Act.Touch(0, it.x / wpx, it.y / hpx) }
            val out = ArrayList<Act>(grid(cols, rows))
            if (recheck) {
                // 폰의 확대는 두 손가락이다(휠은 PC 뿐) — 뷰포트 가운데에서 오므리고 벌린다
                val mid = (hpx * 0.08f + bottom) / 2 / hpx
                out += Act.Touch(4, 0.5f, mid, 0.5f)
                out += Act.Touch(4, 0.5f, mid, 2.2f)
            }
            if (measure) cands.firstOrNull { it.key.startsWith("측정") && !it.long }?.let { m ->
                val tap = Act.Tap(m.key, m.nth)
                out += tap
                out += grid(5, 4)
                out += tap
            }
            val a = speed.right + 12 * DENS
            val b = max(a + 1, wpx - 76 * DENS)
            val y = speed.center.y / hpx
            listOf(a, (a + b) / 2, b).forEach { out += Act.Touch(0, it / wpx, y) }
            out += Act.Touch(1, b / wpx, y, (a - b) / wpx, 0f)
            return out
        }

        private fun press(t: Cand) {
            when {
                t.pointer -> hold(t.center)
                t.long -> t.node.config[SemanticsActions.OnLongClick].action?.invoke()
                else -> t.node.config[SemanticsActions.OnClick].action?.invoke()
            }
        }

        private fun send(type: PointerEventType, at: Offset) = scene.sendPointerEvent(type, at, type = PointerType.Touch)

        /** 길게 누름(손짓 감지는 실제 시간 0.5초) */
        private fun hold(at: Offset) {
            send(PointerEventType.Press, at)
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 700) frames(1, 25)
            send(PointerEventType.Release, at)
        }

        private fun touch(a: Act.Touch) {
            val p = Offset(a.x * wpx, a.y * hpx)
            when (a.kind) {
                0 -> {
                    send(PointerEventType.Press, p)
                    send(PointerEventType.Release, p)
                }
                1 -> {
                    send(PointerEventType.Press, p)
                    var q = p
                    for (i in 1..6) {
                        q = p + Offset(a.dx * wpx * i / 6, a.dy * hpx * i / 6)
                        send(PointerEventType.Move, q)
                        frames(1)
                    }
                    send(PointerEventType.Release, q)
                }
                2 -> hold(p)
                3 -> scene.sendPointerEvent(PointerEventType.Scroll, p, scrollDelta = Offset(a.dx * 20, a.dy * 20), type = PointerType.Mouse)
                else -> pinch(p, a.dx)
            }
        }

        /** 두 손가락을 at 양옆에 대고 사이를 scale 배로(UiMonkeyTest 와 같은 꼴), 한 손가락씩 뗀다 */
        @OptIn(InternalComposeUiApi::class)
        private fun pinch(at: Offset, scale: Float) {
            val d0 = 40 * DENS
            fun two(d: Float, up: Int? = null) = listOf(at - Offset(d, 0f), at + Offset(d, 0f)).mapIndexed { i, o ->
                ComposeScenePointer(PointerId(i.toLong()), o, i != up, PointerType.Touch)
            }
            scene.sendPointerEvent(PointerEventType.Press, two(d0).take(1))
            scene.sendPointerEvent(PointerEventType.Press, two(d0))
            for (i in 1..6) {
                scene.sendPointerEvent(PointerEventType.Move, two(d0 * (1 + (scale - 1) * i / 6f)))
                frames(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, two(d0 * scale, up = 1))
            scene.sendPointerEvent(PointerEventType.Release, two(d0 * scale, up = 0).take(1))
        }

        override fun close() {
            if (part.session === this) part.session = null
            runCatching { scene.close() }
            ui.closed = true
            runCatching { ui.drain() }
            file.delete()
        }
    }

    // ---- 넓이 우선 탐색

    /** 처음 상태에서 경로를 밟은 장면. 실패하면 적고 null */
    private fun Part.open(start: Start, cfg: Cfg, path: List<Act>): Session? {
        resets++
        val s = try {
            session(start, cfg)
        } catch (e: Abandoned) {
            throw e
        } catch (e: Throwable) {
            failures += Failure(start.name, cfg, emptyList(), "장면 만들기", e)
            return null
        }
        var r = s.settle(waitRun = true)
        for (a in path) {
            if (r != Res.Ok) break
            r = s.step(a)
        }
        when (r) {
            Res.Ok -> return s
            Res.Miss -> misses++
            is Res.Fail -> record(s, r)
        }
        s.close()
        return null
    }

    /**
     * 새 장면. Skia 그림판(네이티브 메모리)은 GC 가 돌아야 풀리는데 자바 힙은 넉넉해 GC 가 드물다 — 장면을 수백 번 만들면
     * `Surface.makeRasterN32Premul` 이 실패한다(시험 틀만의 일). 몇 번마다 GC, 실패하면 GC 뒤 한 번 더
     */
    private fun Part.session(start: Start, cfg: Cfg): Session {
        if (resets % 8 == 0) System.gc()
        return try {
            Session(this, start, cfg)
        } catch (e: IllegalArgumentException) {
            if ("makeRaster" !in (e.message ?: "")) throw e
            System.gc()
            Thread.sleep(300)
            Session(this, start, cfg)
        }
    }

    internal class Job(val start: Start, val cfg: Cfg, val maxDepth: Int)

    /** 탐색할 곳: 경로 · 깊이. skip = 같은 층에 노드만 더 생긴 것(문제 띠 · 찾기 줄 · 코드 목록)이면 부모가 이미 누른 노드(새 것만 누른다) */
    private class Visit(val path: List<Act>, val depth: Int, val skip: Set<String>?)

    /** 시작 상태 하나 × 설정 하나: 처음 층부터 넓이 우선, 깊이 maxDepth 까지 */
    private fun Part.explore(job: Job, until: Long) {
        val queue = ArrayDeque<Visit>()
        queue += Visit(emptyList(), 0, null)
        val seenLayers = HashSet<String>()
        val seenActs = HashSet<String>()
        while (queue.isNotEmpty()) {
            if (System.currentTimeMillis() > until) {
                unexplored += queue.size
                log += "  (시간 끝 — 남은 경로 ${queue.size}개)"
                return
            }
            val v = queue.removeFirst()
            sweep(job, v, until) { child, sig, actKey, skip ->
                if (v.depth < job.maxDepth && seenActs.add(actKey) && seenLayers.add(sig)) queue += Visit(child, v.depth + 1, skip)
            }
        }
        if (recheck) walk(job, until)
    }

    /**
     * 다시 확인의 무작위 깊은 걸음: 넓이 우선은 같은 노드 묶음을 다시 들어가지 않아(앞서 고른 종류 · 팁 · 칸 값이 달라도) 깊이 2~3 에서 끝난다.
     * 남은 시간에 처음 상태에서 job.maxDepth 번씩 무작위로 누른다(손가락이 닿는 노드 · 키패드 · 손짓 · 뒤로). 경로는 그대로 남아 다시 밟을 수 있다
     */
    private fun Part.walk(job: Job, until: Long) {
        val rnd = Random(seed xor (job.start.name + job.cfg + "걸음").hashCode().toLong())
        while (System.currentTimeMillis() < until) {
            val s = open(job.start, job.cfg, emptyList()) ?: return
            walks++
            try {
                var failed = false
                for (d in 0 until job.maxDepth) {
                    val cands = s.candidates()
                    val pad = cands.any { it.key == "확인" } && cands.any { it.key == "C" } && cands.any { it.key == "지우기" }
                    val pick = cands.randomOrNull(rnd)
                    val wait = d % 2 == 1
                    val r = when {
                        pad && rnd.nextBoolean() -> s.step(Act.Pad(padKeys(rnd)), waitRun = wait)
                        pick == null || rnd.nextInt(12) == 0 -> s.step(Act.Back, waitRun = wait)
                        rnd.nextInt(6) == 0 -> s.step(gesture(rnd, rnd.nextInt(5)), waitRun = wait)
                        else -> s.step(Act.Tap(pick.key, pick.nth, pick.long), pick.node.id, wait)
                    }
                    if (r is Res.Fail) {
                        record(s, r)
                        failed = true
                        break
                    }
                    if (r == Res.Miss) misses++
                    if (s.app.current != Feature.SIMULATOR) {
                        leftScreen++
                        break
                    }
                }
                if (!failed) (s.finish() as? Res.Fail)?.let { record(s, it) }
            } finally {
                s.close()
            }
        }
    }

    /**
     * 한 층 쓸기: 후보를 차례로 누른다(한 장면 안에서 이어서). 새 노드가 생기면 onChild(경로, 새 노드 묶음, 누른 것, 건너뛸 것).
     * 층이 가려지거나 닫히면 여는 단추 다시 → 뒤로 → 처음부터 경로 다시.
     */
    private fun Part.sweep(job: Job, v: Visit, until: Long, onChild: (List<Act>, String, String, Set<String>?) -> Unit) {
        val path = v.path
        val first = open(job.start, job.cfg, path) ?: return
        var s: Session? = first
        sweeps++
        try {
            val base = first.candidates()
            val baseKeys = base.map { it.nkey }.toSet()
            val rnd = Random(seed xor (job.start.name + job.cfg + path).hashCode().toLong())
            val acts = ArrayList<Pair<Act, Int?>>()
            // 겨눈 손짓은 처음 층에서만(층마다 하면 50번씩 늘어 부분 시간을 넘는다) — 쓸기 전 상태(2D · 시트 없음)에서 먼저.
            // 다시 확인은 처음 층을 촘촘히(10×8), 깊은 층도 성기게(4×3 · 측정 없이) 한다
            when {
                v.depth == 0 && recheck -> first.aimed(10, 8)
                v.depth == 0 -> first.aimed()
                recheck -> first.aimed(4, 3, measure = false)
                else -> emptyList()
            }.forEach { acts += it to null }
            base.filter { v.skip == null || it.nkey !in v.skip }.forEach { acts += Act.Tap(it.key, it.nth, it.long) to it.node.id }
            if (base.any { it.key == "확인" } && base.any { it.key == "C" } && base.any { it.key == "지우기" }) {
                repeat(PAD_RUNS) { acts += Act.Pad(padKeys(rnd)) to null }
            }
            first.candidates(scroll = true).filter { v.skip == null || it.nkey !in v.skip }.forEach { acts += Act.Scroll(it.key, it.nth, 2000f) to null }
            repeat(if (recheck) GESTURES + 1 else GESTURES) { acts += gesture(rnd, it) to null }
            log += "${job.start.name} · ${job.cfg} · 깊이 ${v.depth}: ${path.joinToString(" → ").ifEmpty { "(처음)" }} — 후보 ${base.size} · 누름 ${acts.size}"
            for ((i, pair) in acts.withIndex()) {
                if (System.currentTimeMillis() > until) break
                val (act, id) = pair
                val cur = s ?: open(job.start, job.cfg, path) ?: return
                s = cur
                val beforeKeys = cur.candidates().map { it.nkey }.toSet()
                val beforeLayers = cur.layers()
                val r = cur.step(act, id, waitRun = i % 4 == 3)
                when (r) {
                    Res.Miss -> {
                        misses++
                        continue
                    }
                    is Res.Fail -> {
                        record(cur, r)
                        cur.close()
                        s = null
                        continue
                    }
                    Res.Ok -> {}
                }
                if (cur.app.current != Feature.SIMULATOR) {
                    leftScreen++
                    cur.close()
                    s = null
                    continue
                }
                val after = cur.candidates()
                val afterKeys = after.map { it.nkey }.toSet()
                val added = afterKeys - beforeKeys
                val covered = lost(baseKeys, afterKeys) > 0.5
                // 새 층 · 새 노드만 다음 깊이로: 닫는 것(취소 · 확인 · 예제 열기)은 아래 층이 다시 보일 뿐이고, 끌기 · 길게 · 휠은 끝 자리가 상태마다 달라
                // 다시 밟기 어렵다(누름은 된다 — 고스트를 고르면 좌표 카드에 '고스트 원 안내' 가 생긴다)
                val done = cur.trail.last()
                if (added.isNotEmpty() && cur.layers() >= beforeLayers && (done is Act.Tap || done is Act.Scroll || (done is Act.Touch && done.kind == 0))) {
                    val actKey = done.toString()
                    // 숫자 키패드는 칸마다 따로(키패드 모양은 같아도 칸마다 값 · 뜻이 다르다)
                    val pad = "확인" in added && "C" in added && "지우기" in added
                    onChild(path + done, added.sorted().joinToString(",") + (if (pad) "@$actKey" else ""), actKey, if (covered) null else beforeKeys)
                }
                if (covered) s = restore(cur, path, baseKeys)
            }
            s?.let { cur ->
                val r = cur.finish()
                if (r is Res.Fail) record(cur, r)
            }
        } finally {
            s?.close()
        }
    }

    private fun lost(base: Set<String>, now: Set<String>) = base.count { it !in now }.toDouble() / max(1, base.size)

    /** 쓸던 층이 가려지거나 닫혔다: 여는 단추를 다시(닫힌 시트 · 키패드) → 뒤로(덮은 시트 · 창) → 안 되면 null(처음부터) */
    private fun Part.restore(s: Session, path: List<Act>, baseKeys: Set<String>): Session? {
        val tries = listOfNotNull(path.lastOrNull()?.takeIf { it is Act.Tap }, Act.Back)
        for (a in tries) {
            when (val r = s.step(a)) {
                Res.Miss -> continue
                is Res.Fail -> {
                    record(s, r)
                    s.close()
                    return null
                }
                Res.Ok -> {}
            }
            if (s.app.current != Feature.SIMULATOR) break
            if (lost(baseKeys, s.candidates().map { it.nkey }.toSet()) <= 0.5) return s
        }
        s.close()
        return null
    }

    private fun padKeys(rnd: Random): String {
        if (rnd.nextInt(3) == 0) return PAD_EXTREMES[rnd.nextInt(PAD_EXTREMES.size)]
        val n = 1 + rnd.nextInt(8)
        return (1..n).map { "0123456789.-C<"[rnd.nextInt(14)] }.joinToString("") + "DDDXSB"[rnd.nextInt(6)]
    }

    private fun gesture(rnd: Random, i: Int): Act.Touch {
        fun f() = 0.04f + rnd.nextFloat() * 0.92f
        return when (i % (if (recheck) 5 else 4)) {
            0 -> Act.Touch(0, f(), f())
            1 -> Act.Touch(1, f(), f(), rnd.nextFloat() * 0.6f - 0.3f, rnd.nextFloat() * 0.6f - 0.3f)
            2 -> Act.Touch(2, f(), f())
            3 -> Act.Touch(3, f(), f(), 0f, if (rnd.nextBoolean()) 3f else -3f)
            else -> Act.Touch(4, f(), f(), listOf(0.4f, 0.7f, 1.6f, 3f)[rnd.nextInt(4)])
        }
    }

    // ---- 부분 돌리기(감시 · 보고)

    /** 일꾼 스레드에서 차례로. 진행이 STALL_MS 동안 없으면 교착으로 적고 그 일꾼을 버린 뒤 다음 일부터 */
    private fun Part.runJobs(jobs: List<Pair<String, (Long) -> Unit>>) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            var from = 0
            while (from < jobs.size) {
                val next = AtomicInteger(from)
                val myGen = ++generation
                val body = Runnable {
                    while (generation == myGen && !memoryStop) {
                        val k = next.get()
                        if (k >= jobs.size) break
                        jobName = jobs[k].first
                        val left = deadline - System.currentTimeMillis()
                        val until = System.currentTimeMillis() + max(0L, left / (jobs.size - k))
                        try {
                            jobs[k].second(until)
                        } catch (_: Abandoned) {
                            return@Runnable
                        } catch (e: Throwable) {
                            failures += Failure(jobName, C412, emptyList(), "시험 틀", e)
                        }
                        next.incrementAndGet()
                    }
                }
                val worker = Thread(body, "monkey-$name").apply {
                    isDaemon = true
                    start()
                }
                progress.set(System.nanoTime())
                var stalled = false
                while (worker.isAlive) {
                    worker.join(1000)
                    if (worker.isAlive && System.nanoTime() - progress.get() > STALL_MS * 1_000_000) {
                        stalled = true
                        val s = session
                        val stacks = buildString {
                            append("일꾼 스택:\n")
                            worker.stackTrace.take(30).forEach { append("    at $it\n") }
                            Thread.getAllStackTraces().filter { (t, _) -> t != worker && (t.name.startsWith("DefaultDispatcher") || t.name.startsWith("AWT")) }
                                .forEach { (t, st) ->
                                    if (st.any { it.className.startsWith("com.bmt") || it.className.startsWith("androidx.compose") }) {
                                        append("  ${t.name} (${t.state}):\n")
                                        st.take(15).forEach { append("    at $it\n") }
                                    }
                                }
                        }
                        val hang = Hang("교착 — ${STALL_MS / 1000}초 동안 프레임이 나아가지 않음")
                        failures += Failure(s?.start?.name ?: jobName, s?.cfg ?: C412, s?.trail?.toList().orEmpty(), "교착(감시)", hang, stacks)
                        generation++
                        worker.interrupt()
                        break
                    }
                }
                from = if (stalled) next.get() + 1 else jobs.size
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(prev)
        }
    }

    /** 찾은 예외마다(같은 것은 하나) 처음부터 그 경로를 다시 밟아 재현되는지 */
    private fun Part.reproduce(starts: Map<String, Start>) {
        val uniq = failures.groupBy { it.sig }.values.map { it.first() }
        val jobs = uniq.take(MAX_REPRO).mapNotNull { f ->
            val st = starts[f.start] ?: return@mapNotNull null
            if (f.trail.isEmpty()) return@mapNotNull null
            "재현 ${f.sig}" to { _: Long ->
                val s = session(st, f.cfg)
                try {
                    var r: Res = s.settle(true)
                    for (a in f.trail) {
                        if (r != Res.Ok) break
                        r = s.step(a)
                    }
                    if (r == Res.Ok) r = s.finish()
                    f.repro = when {
                        r is Res.Fail && signature(r.e) == f.sig -> "재현됨(같은 예외)"
                        r is Res.Fail -> "다른 예외로 끝남: ${signature(r.e)}"
                        r == Res.Miss -> "경로 중간 노드를 못 찾음(상태가 시각에 따라 다름)"
                        else -> "재현 안 됨"
                    }
                } finally {
                    s.close()
                }
            }
        }
        val before = failures.size
        runJobs(jobs)
        // 재현 중 새로 난 것은 원래 목록에 섞지 않는다(위 repro 글에 적었다)
        while (failures.size > before) failures.removeAt(failures.size - 1)
    }

    private fun Part.report(extra: List<String> = emptyList()): String {
        val uniq = failures.groupBy { it.sig }
        val text = buildString {
            append("SimMonkeyTest · $name — 씨앗 $seed · ${(System.currentTimeMillis() - began) / 1000}초(그리기 ${renderNs / 1_000_000_000}초)\n")
            append("시험 JVM 메모리 최대 ${peakMb}MB${if (memoryStop) " — 한도를 넘어 일찍 끝냄" else ""}\n")
            append("층 쓸기 $sweeps · 누르기 $steps · 처음부터 다시 $resets · 못 찾은 노드 $misses · 화면 떠남 $leftScreen · 시간에 못 간 경로 $unexplored\n")
            if (walks > 0) append("무작위 깊은 걸음(처음 상태에서 깊이만큼) $walks\n")
            append("시험 틀 예외(헤드리스만 — 앱 결함 아님) ${harness.size}\n")
            harness.take(10).forEach { append("  $it\n") }
            append("\n예외 ${failures.size}건 · 종류 ${uniq.size}\n")
            for ((sig, list) in uniq) {
                val f = list.first()
                append("\n■ $sig — ${list.size}번 · ${f.where}\n")
                append("  재현: ${f.repro}\n")
                append("  시작: ${f.start} · ${f.cfg}\n")
                append("  경로(${f.trail.size}): ${f.trail.joinToString(" → ")}\n")
                append("  ${f.e}\n")
                f.e.stackTrace.take(14).forEach { append("    at $it\n") }
                f.e.cause?.let { c ->
                    append("  원인: $c\n")
                    c.stackTrace.take(8).forEach { append("    at $it\n") }
                }
                if (f.extra.isNotEmpty()) append(f.extra)
            }
            append("\n누르기가 낸 효과: ${effects.entries.sortedBy { it.key }.joinToString(" · ") { "${it.key} ${it.value}" }.ifEmpty { "없음" }}\n")
            append("누른 것(${pressed.size}가지 — 이름 횟수):\n")
            pressed.entries.sortedBy { it.key }.chunked(8).forEach { row -> append("  ${row.joinToString(" · ") { "${it.key} ${it.value}" }}\n") }
            append("\n탐색한 층:\n")
            log.forEach { append("  $it\n") }
            extra.forEach { append("$it\n") }
        }
        val out = File(Shots.dir.parentFile, "monkey/$name.txt")
        out.parentFile.mkdirs()
        out.writeText(text)
        println(text.lines().take(60).joinToString("\n"))
        return text
    }

    private fun runPart(name: String, jobs: List<Job>, extra: List<Pair<String, (Part, Long) -> Unit>> = emptyList()) {
        val part = Part(name, PART_MS)
        part.runJobs(jobs.map { j -> "${j.start.name} · ${j.cfg}" to { until: Long -> part.explore(j, until) } } +
            extra.map { (n, f) -> n to { until: Long -> f(part, until) } })
        part.reproduce((jobs.map { it.start } + EXTRA_STARTS).associateBy { it.name })
        part.report()
        val real = part.failures.groupBy { it.sig }
        assertTrue(real.isEmpty(), "$name: 예외 ${real.size}종류 — build/monkey/$name.txt\n" + real.keys.joinToString("\n"))
    }

    /** 씨앗(처음 값 SEED) · 다시 확인 켬(겨눈 손짓을 모든 층에서 · 처음 층은 촘촘히, 두 손가락 핀치) — [recheck] 만 바꾼다 */
    private var seed = SEED
    private var recheck = false

    /**
     * 다시 확인(MonkeyRecheckTest): 다른 씨앗 · 시작 상태 · 깊이로 같은 탐색. matrix = 공구 카드 차례([toolMatrix])를 돌릴 시작 상태 · 설정.
     * 메모리 한도에 첫머리부터 걸려 거의 못 누르면(껍데기 통과) 실패로 친다
     */
    internal fun recheck(name: String, seed: Long, jobs: List<Job>, matrix: List<Pair<Start, Cfg>> = emptyList()) {
        this.seed = seed
        recheck = true
        val part = Part(name, PART_MS)
        part.runJobs(
            jobs.map { j -> "${j.start.name} · ${j.cfg}" to { until: Long -> part.explore(j, until) } } +
                matrix.map { (st, cfg) -> "${st.name} · 종류 × 인서트 × 팁 · $cfg" to { until: Long -> part.toolMatrix(cfg, until, st) } },
        )
        part.reproduce((jobs.map { it.start } + matrix.map { it.first }).associateBy { it.name })
        part.report()
        val real = part.failures.groupBy { it.sig }
        assertTrue(real.isEmpty(), "$name: 예외 ${real.size}종류 — build/monkey/$name.txt\n" + real.keys.joinToString("\n"))
        assertTrue(part.steps >= MIN_STEPS, "$name: 누르기 ${part.steps}번뿐(메모리 최대 ${part.peakMb}MB) — 껍데기 통과")
    }

    // ---- 부분(시험 함수) — 하나에 15분 안

    /** 화면 A 편집기 ①: 빈 글(키패드 열림 · 닫힘) · 오류 글 — 키패드 · SHIFT · 찾기 · 저장 · 목록 · ⋯ · 묻는 창 · 문제 띠 · 메뉴 · 도움말 → 궤적 창 → 시트 */
    @Test
    fun editorBlankAndErrors() = runPart(
        "editor-1",
        listOf(
            Job(EMPTY, C412, 4),
            Job(EMPTY, C360, 4),
            Job(Start("빈 글 · 키패드 닫힘", prep = { keypadOpen = false }), C412L, 4),
            Job(Start("오류 글", prep = { editor.replaceAll(SimShotsTest.ERRORS) }), C412, 4),
        ),
    )

    /** 화면 A 편집기 ②: 오류 줄 커서 · 극단 값 글 · 공구 없음 · 저장된 값 복원 · 예제(편집기) */
    @Test
    fun editorOtherStarts() = runPart(
        "editor-2",
        listOf(
            Job(Start("오류 글(멈춤 줄 커서)", prep = { editor.replaceAll(SimShotsTest.ERRORS); editor.moveTo(editor.lineStart(5) + 9) }), C360S, 4),
            Job(Start("극단 값 글", prep = { editor.replaceAll(EXTREME) }), C412L, 4),
            Job(Start("공구 없음(G42 · T1212)", prep = { editor.replaceAll(NO_TOOL) }), C360, 4),
            Job(RESTORED, C412, 4),
            Job(Start("예제 1 편집기", prep = { perform(Pending.Example(0)) }), C360S, 4),
            Job(Start("예제 9 편집기", prep = { perform(Pending.Example(8)) }), C412L, 4),
        ),
    )

    private fun trajJob(i: Int) = Job(Start("예제 ${i + 1} 궤적 창", traj = true, prep = { perform(Pending.Example(i)) }), CFGS[i % CFGS.size], 3)

    /** 화면 B 궤적 창 ①: 예제 1~6(2D/3D · 재생 · 속도 · 싱글 블록 · M01 · 측정 · 각도 · 맞춤 · 고스트 · 코드 목록 · 범례 · 설정 · 공구 · 도움말) */
    @Test
    fun trajectoryExamplesA() = runPart("trajectory-1", (0 until 6).map(::trajJob))

    /** 화면 B 궤적 창 ②: 예제 7~11 · 오류 글(멈춤 띠 '편집기로') · 공구 없음(멈춤 띠 '공구 설정') · 빈 글 */
    @Test
    fun trajectoryExamplesB() = runPart(
        "trajectory-2",
        (6 until exampleList.size).map(::trajJob) + listOf(
            Job(Start("오류 글 궤적 창(멈춤 띠)", traj = true, prep = { editor.replaceAll(SimShotsTest.ERRORS) }), C412, 3),
            Job(Start("공구 없음 궤적 창(멈춤 띠 '공구 설정')", traj = true, prep = { editor.replaceAll(NO_TOOL) }), C360, 3),
            Job(Start("빈 글 궤적 창", traj = true), C360S, 3),
        ),
    )

    /** 공구 설정 시트: 공구표 빈 · 일부 · 극단 값 · 공구 넷, 그리고 종류 5 × 인서트 4 × 팁 시트의 모든 번호를 차례로(노즈 R · κr · 폭 · 각도 · 다이스 지름 칸 끝 값) */
    @Test
    fun toolSheet() = runPart(
        "tools",
        listOf(
            Job(Start("공구표 빈(T1212 G42)", traj = true, sheet = TrajSheet.TOOLS, prep = { editor.replaceAll(NO_TOOL) }), C412, 3),
            Job(
                Start("공구표 일부(노즈 R 만)", traj = true, sheet = TrajSheet.TOOLS, prep = {
                    editor.replaceAll(NO_TOOL)
                    tools = mapOf(12 to ToolOffset(noseR = 0.4))
                }),
                C360, 3,
            ),
            Job(
                Start("공구표 극단 값", traj = true, sheet = TrajSheet.TOOLS, prep = {
                    editor.replaceAll(NO_TOOL)
                    tools = mapOf(12 to ToolOffset(noseR = 50.0, tip = 0, insert = InsertShape.V, kappaDeg = 179.9), 99 to ToolOffset(noseR = 0.0, tip = 8))
                }),
                C412L, 3,
            ),
            Job(MULTI_TOOLS, C360S, 3),
            Job(Start("빈 글 공구 설정(카드 없음)", traj = true, sheet = TrajSheet.TOOLS), C412, 3),
        ),
        extra = listOf(
            "종류 × 인서트 × 팁 · 412" to { p, until -> p.toolMatrix(C412, until) },
            "종류 × 인서트 × 팁 · 360" to { p, until -> p.toolMatrix(C360, until) },
            "회사 홀더 차례 · 360 글꼴 1.3" to { p, until -> p.holderMatrix(C360, until) },
        ),
    )

    /** 시뮬레이션 설정 시트: 소재 칸 빈 · 이상한 값, 기계 칸 이상한 값 · 읽기 방식(모든 칸에 키패드 무작위) */
    @Test
    fun settingsSheet() = runPart(
        "settings",
        listOf(
            Job(Start("예제 1 · 소재 빈", traj = true, sheet = TrajSheet.SETTINGS, prep = { perform(Pending.Example(0)) }), C412, 3),
            Job(
                Start("예제 4 · 소재 이상한 값", traj = true, sheet = TrajSheet.SETTINGS, prep = {
                    perform(Pending.Example(3))
                    WEIRD_STOCK.forEach { (f, v) -> setStock(f, v) }
                }),
                C360, 3,
            ),
            Job(
                Start("오류 글 · 기계 이상한 값", traj = true, sheet = TrajSheet.SETTINGS, kv = ::weirdMachine, prep = { editor.replaceAll(SimShotsTest.ERRORS) }),
                C412L, 3,
            ),
            Job(
                Start("극단 값 글 · 소재 · 기계 이상", traj = true, sheet = TrajSheet.SETTINGS, kv = ::weirdMachine, prep = {
                    editor.replaceAll(EXTREME)
                    WEIRD_STOCK.forEach { (f, v) -> setStock(f, v.reversed().filter { it != '-' }.ifEmpty { "0" }) }
                }),
                C360S, 3,
            ),
            Job(Start("빈 글 설정", traj = true, sheet = TrajSheet.SETTINGS), C412, 3),
        ),
    )

    /**
     * 공구 카드 하나(T1212 · G42)에서 종류 5가지 × (바이트면 인서트 4) × 팁 시트의 모든 번호(1~8 · 0 · 표준 되돌리기)를 차례로 누르고,
     * 노즈 R · κr · 폭 · 각도 · 다이스 지름 칸에 끝 값을 넣은 뒤(다이스도 G42 번호라 노즈 R · 팁이 보인다) 시트를 닫아 2D · 3D 를 그리고 재생한다. 팁을 고를 때마다 시트 뒤의 궤적 창도 다시 그린다.
     */
    private fun Part.toolMatrix(cfg: Cfg, until: Long, start: Start = TOOL_CARD) {
        val s = open(start, cfg, emptyList()) ?: return
        log += "${start.name} · $cfg: 종류 × 인서트 × 팁"
        try {
            fun go(r: Res): Boolean = when (r) {
                Res.Ok -> true
                Res.Miss -> {
                    misses++
                    log += "  못 찾음: ${s.trail.lastOrNull()}"
                    true
                }
                is Res.Fail -> {
                    record(s, r)
                    false
                }
            }
            fun tap(pred: (Cand) -> Boolean): Res {
                val t = s.candidates().firstOrNull(pred) ?: return Res.Miss
                return s.step(Act.Tap(t.key, t.nth, t.long), t.node.id)
            }
            /** 숫자 칸: 라벨(눌리지 않는 글) 바로 아래, 왼쪽 끝이 같은 값 상자 */
            fun field(label: String): Res {
                val l = s.textBounds(label) ?: return Res.Miss
                val t = s.candidates().filter { !it.long && it.center.y > l.bottom && abs(it.node.boundsInRoot.left - l.left) < 30 * DENS }
                    .minByOrNull { it.center.y } ?: return Res.Miss
                return s.step(Act.Tap(t.key, t.nth), t.node.id)
            }
            val tips = (1..8).map { "$it" } + listOf("0", "표준")
            val nose = listOf("0D", "0.0001D", "50D", "B", "-0.4D", "999999999999D")
            var ni = 0
            outer@ for (kind in listOf("바이트", "홈", "나사", "둥근", "다이스")) {
                if (System.currentTimeMillis() > until) break
                if (!go(tap { it.key == kind })) break
                val inserts = if (kind == "바이트") InsertShape.entries.map { "${it.name} " } else listOf<String?>(null)
                for (ins in inserts) {
                    if (ins != null && !go(tap { it.key.startsWith(ins) && it.key.endsWith("°") })) break@outer
                    for (tip in tips) {
                        if (!go(tap { TIP_BOX.matches(it.key) })) break@outer
                        val ok = tap { c ->
                            when (tip) {
                                "0" -> c.key.startsWith("0 ·")
                                "표준" -> c.key.startsWith("표준")
                                else -> c.key == tip || c.key.startsWith("$tip/")
                            }
                        }
                        if (!go(ok)) break@outer
                    }
                    // 칸 끝 값: 노즈 R + 종류 칸
                    if (!go(field("노즈 R (공구 반경)"))) break@outer
                    if (!go(s.step(Act.Pad(nose[ni++ % nose.size])))) break@outer
                    val extraField = when (kind) {
                        "바이트" -> "절입각 κr" to listOf("0.1D", "179.9D", "180D", "-D")
                        "홈" -> "홈 폭" to listOf("0.001D", "100D", "100.1D", "0D")
                        "나사" -> "나사 각도" to listOf("1D", "179.99D", "0D", "B")
                        "다이스" -> "다이스 지름" to listOf("0.001D", "200D", "200.1D", "0D")
                        else -> null
                    }
                    if (extraField != null) {
                        if (!go(field(extraField.first))) break@outer
                        if (!go(s.step(Act.Pad(extraField.second[ni % extraField.second.size])))) break@outer
                    }
                    // 시트를 닫고 궤적 창 2D → 재생 → 3D → 2D, 다시 공구 시트
                    if (!go(s.step(Act.Back))) break@outer
                    for (k in listOf("재생", "3D", "블록 끝까지", "2D")) if (!go(tap { it.key == k || it.key.startsWith("$k|") || it.key.endsWith("|$k") })) break@outer
                    if (!go(tap { it.key == "공구 설정" || it.key == "공구" || it.key.startsWith("공구 설정") })) break@outer
                }
            }
            val r = s.finish()
            if (r is Res.Fail) record(s, r)
        } finally {
            s.close()
        }
    }

    /**
     * 회사 홀더 차례(10-29 '홀더 고르기'): 공구 카드(T1212 · G42, 노즈 R 0.4 · 팁 3)에서 홀더 칸 → 시트의 줄을 [COMPANY_HOLDERS] 차례로 모두 고르고,
     * 고를 때마다 카드 값(종류 · 인서트 · κr — [CompanyHolder.fill])과 이름표가 맞는지 본다(틀리면 실패로 적음). 다섯 줄마다 시트를 닫고 2D 재생 · 3D 를 그린다.
     * 끝에 '고르지 않음' 으로 되돌린다
     */
    private fun Part.holderMatrix(cfg: Cfg, until: Long) {
        val s = open(HOLDER_CARD, cfg, emptyList()) ?: return
        log += "${HOLDER_CARD.name} · $cfg: 회사 홀더 ${COMPANY_HOLDERS.size}줄"
        try {
            fun go(r: Res): Boolean = when (r) {
                Res.Ok -> true
                // 시트 · 줄을 못 찾으면 새 시트를 누르지 못한 것 — 실패로 적는다
                Res.Miss -> {
                    misses++
                    record(s, Res.Fail(AssertionError("못 찾음: ${s.trail.lastOrNull()} 다음"), "홀더 고르기"))
                    false
                }
                is Res.Fail -> {
                    record(s, r)
                    false
                }
            }
            /** 궤적 창 단추는 상태에 따라 없을 수 있다(toolMatrix 처럼 못 찾으면 적기만) */
            fun soft(r: Res): Boolean = when (r) {
                Res.Ok -> true
                Res.Miss -> {
                    misses++
                    log += "  못 찾음: ${s.trail.lastOrNull()}"
                    true
                }
                is Res.Fail -> {
                    record(s, r)
                    false
                }
            }
            fun tap(pred: (Cand) -> Boolean, k: Int = 0): Res {
                val t = s.candidates().filter(pred).getOrNull(k) ?: return Res.Miss
                return s.step(Act.Tap(t.key, t.nth, t.long), t.node.id)
            }
            val holderBox = { c: Cand -> c.key.endsWith("/고르기") && !TIP_BOX.matches(c.key) && !c.long }
            for ((i, h) in COMPANY_HOLDERS.withIndex()) {
                if (System.currentTimeMillis() > until) break
                if (!go(tap(holderBox))) break
                // 바라는 값 = 고르기 전 공구에 홀더가 정하는 칸만 채운 것(나사 홀더는 앞 홀더의 κr 을 그대로 둔다)
                val want = h.fill(s.sim.tools[12] ?: ToolOffset())
                // 같은 품번이 제조사로 나뉜 줄은 앞에서 몇 번째인지로
                val k = COMPANY_HOLDERS.take(i).count { it.code == h.code }
                if (!go(tap({ c -> c.key.substringAfter('|').startsWith("${h.code}/") && !c.long }, k))) break
                val got = s.sim.tools[12]
                if (s.sim.holderOf(12)?.id != h.id || got != want) {
                    record(s, Res.Fail(AssertionError("홀더 ${h.id}: 이름표 ${s.sim.holderOf(12)?.id} · 공구 $got (바라는 값 $want)"), "홀더 고르기"))
                    break
                }
                if (i % 5 == 4) {
                    if (!go(s.step(Act.Back))) break
                    var ok = true
                    for (key in listOf("재생", "3D", "블록 끝까지", "2D")) {
                        ok = soft(tap({ it.key == key || it.key.startsWith("$key|") || it.key.endsWith("|$key") }))
                        if (!ok) break
                    }
                    if (!ok || !go(tap({ it.key == "공구 설정" || it.key == "공구" || it.key.startsWith("공구 설정") }))) break
                }
            }
            if (go(tap(holderBox))) go(tap({ it.key.substringAfter('|').startsWith("고르지 않음/") }))
            if (s.sim.holderOf(12) != null) record(s, Res.Fail(AssertionError("고르지 않음 뒤에도 이름표 ${s.sim.holderOf(12)?.id}"), "홀더 고르기"))
            val r = s.finish()
            if (r is Res.Fail) record(s, r)
        } finally {
            s.close()
        }
    }

    internal companion object {
        const val SEED = 20261028L

        /** 공구 카드의 팁 번호 칸('—' · 'n번' + 고르기). 홀더 칸도 '고르기' 로 끝나 둘을 가른다 */
        val TIP_BOX = Regex("^(—|[0-9]+번)/고르기$")

        /** 다시 확인 한 부분에서 이보다 적게 누르면 껍데기 통과 */
        const val MIN_STEPS = 200

        /** 화면 밀도(헤드리스는 그림 크기만 다르다 — dp 배치 · 글꼴 배율은 같다. 그리기가 시간의 2/3 라 폰 2.625 대신 1) */
        const val DENS = 1f
        const val SCREEN = "monkey-screen"

        /** 한 부분(시험 함수)의 탐색 시간 — 재현 · 보고까지 15분 안 */
        const val PART_MS = 11 * 60_000L
        const val STALL_MS = 60_000L
        const val RUN_WAIT_MS = 20_000L
        const val STOCK_WAIT_MS = 30_000L
        const val PAD_RUNS = 4

        /** 그림(Skia 비트맵 — 3D 는 프레임마다 하나)은 GC 가 돌아야 풀린다. 힙이 작아 GC 가 드물면 네이티브 메모리가 쌓여 JVM 이 죽었다 */
        const val GC_FRAMES = 120

        /** 시험 JVM 커밋 한도(힙 512 MB + 네이티브) */
        const val MEM_LIMIT_MB = 2500L

        fun committedMb(): Long =
            (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).committedVirtualMemorySize / (1 shl 20)
        const val GESTURES = 4
        const val MAX_REPRO = 8

        val C412 = Cfg(412, 915, 1f, false)
        val C360 = Cfg(360, 800, 1.3f, true)
        val C412L = Cfg(412, 915, 1.3f, true)
        val C360S = Cfg(360, 800, 1f, false)
        val CFGS = listOf(C412, C360, C412L, C360S)

        val PAD_LABEL = mapOf(
            '0' to "0", '1' to "1", '2' to "2", '3' to "3", '4' to "4", '5' to "5", '6' to "6", '7' to "7", '8' to "8", '9' to "9",
            '.' to ".", '-' to "−", 'C' to "C", '<' to "지우기", 'D' to "확인", 'X' to "취소", 'B' to "비우기 · 자동 계산", 'S' to "(바깥 덮개)",
        )

        /** 키패드 끝 값: 0 · − 뿐 · . 뿐 · −0 · 12자리 · 아주 작은 수 · 음수 · 지우고 확인 · 취소 · 바깥 · 비우기 */
        val PAD_EXTREMES = listOf("0D", "-D", ".D", "-0D", "999999999999D", "0.00000000001D", "-5D", "C<D", "123.456X", "7S", "B", "9999999999999.9D")

        /** G42 를 쓰는데 공구표에 보정 12 가 없다 → 6줄에서 TOOL_DATA_MISSING (SimToolShortcutTest) */
        const val NO_TOOL = "O0001\nT1212\nG97 S1000 M03\nG99\nG00 X44. Z2.\nG42 G01 X40. Z0. F0.2\nG01 Z-20.\nG40 G00 X44.\nM30"

        /** 공구 넷(바이트 G42 · 홈 · 나사 G92 · 안쪽 G41) */
        const val MULTI = "O0003 (공구 넷)\nT0101\nG97 S1000 M03\nG99\nG00 X44. Z2.\nG42 G01 X40. Z0. F0.2\nG01 Z-20.\nG40 G00 X100. Z100.\n" +
            "T0202\nG00 X44. Z-10.\nG01 X30. F0.05\nG00 X100.\nT0303\nG00 X44. Z5.\nG92 X39.2 Z-15. F1.5\nX38.6\nG00 X100. Z100.\n" +
            "T0404\nG00 X20. Z2.\nG41 G01 X24. Z0. F0.1\nG01 Z-10.\nG40 G00 X20. Z100.\nM30"

        /** 극단 값: 0 · 음수 · 12자리 · 아주 작은 수 */
        const val EXTREME = "O0002 (극단 값)\nT0101\nG97 S0 M03\nG99\nG00 X999999999999. Z0.000000000001\nG01 X-0.000000000001 Z-999999999999. F0.\n" +
            "G01 X0. Z0. F999999999999.\nG02 X0.000000000001 Z-0.000000000001 R0.000000000001\nG03 X12. Z-1. R-5.\nG04 P999999999999\nG50 S0\nG96 S999999999999\nM30"

        val WEIRD_STOCK = mapOf(
            StockField.DIA to "0", StockField.LENGTH to "-5", StockField.FACE to "999999999999",
            StockField.BORE to "0.00000000001", StockField.CHUCK to "-0.5",
        )

        val EMPTY = Start("빈 글")

        val MULTI_TOOLS = Start("공구 넷 · 극단 값", traj = true, sheet = TrajSheet.TOOLS, prep = {
            editor.replaceAll(MULTI)
            tools = mapOf(
                1 to ToolOffset(noseR = 0.0, tip = 8),
                2 to ToolOffset(noseR = 0.0, tip = 4, kind = ToolKind.GROOVE, width = 100.0),
                3 to ToolOffset(noseR = 0.0001, tip = 7, kind = ToolKind.THREAD, threadDeg = 179.99),
                4 to ToolOffset(noseR = 50.0, tip = 1, kind = ToolKind.ROUND),
                99 to ToolOffset(tip = 5, insert = InsertShape.T, kappaDeg = 0.001),
            )
        })

        fun weirdMachine(kv: KeyValueStore) {
            val m = mapOf(
                "rx" to "0", "rz" to "-1", "tc" to "999999999999", "rpm" to "0.00000000001", "hx" to "-200", "hz" to "0", "h2x" to "-1", "h2z" to "99999",
                "g92" to "9.9", "path" to RapidPath.LINEAR.name, "dec" to DecimalMode.CALCULATOR.name, "skip" to "1", "suv" to "Z",
            )
            kv.put(ProgramStore.MACHINE, SetupText.encode(m))
            kv.put(ProgramStore.VIEW, "01010")
        }

        /**
         * 저장된 값 복원: 저장한 프로그램 둘 · 고친 임시 글(저장본에서) · 기계 이상한 값 · 보기 끔 → 화면 상태 Saver 로 저장했다가 되살린다
         * (공구표 · 찾기 열림 · SHIFT 잠금 · 소재 칸 · 속도 · 싱글 블록 · M01)
         */
        val RESTORED = Start(
            "저장된 값 복원",
            kv = { kv ->
                val p = ProgramStore(kv)
                p.save(1001, exampleList[0].text)
                p.save(2001, SimShotsTest.ERRORS)
                kv.put(ProgramStore.DRAFT, exampleList[0].text + "\nG01 X1.")
                kv.put(ProgramStore.DRAFT_CURSOR, "999999")
                kv.put(ProgramStore.DRAFT_FROM, "p1001")
                kv.put(ProgramStore.KEYPAD, "1")
                weirdMachine(kv)
            },
            make = { kv ->
                val a = SimState(kv).apply {
                    tools = mapOf(1 to ToolOffset(noseR = 0.8, tip = 3), 12 to ToolOffset(tip = 2, kind = ToolKind.GROOVE))
                    openFind()
                    editQuery("G01")
                    shift = ShiftState.LOCK
                    setStock(StockField.DIA, "0.00000000001")
                    setStock(StockField.FACE, "-3")
                    speed = 200f
                    singleBlock = true
                    optStop = true
                }
                val saver = SimState.saver(kv)
                val saved = with(saver) { SaverScope { true }.save(a) }!!
                saver.restore(saved)!!
            },
        )

        /** 시작 상태 이름 → 시작 상태(재현할 때 — 부분의 일 목록 밖에서 만든 것) */
        val TOOL_CARD = Start("공구 카드 차례(빈 공구표)", traj = true, sheet = TrajSheet.TOOLS, prep = { editor.replaceAll(NO_TOOL) })
        val HOLDER_CARD = Start("회사 홀더 차례(T1212 노즈 R 0.4 · 팁 3)", traj = true, sheet = TrajSheet.TOOLS, prep = {
            editor.replaceAll(NO_TOOL)
            tools = mapOf(12 to ToolOffset(noseR = 0.4, tip = 3))
        })
        val EXTRA_STARTS = listOf(TOOL_CARD, HOLDER_CARD)

        fun pct(f: Float) = "${(f * 100).roundToInt()}%"

        /** 헤드리스 장면에서만 나는 것(두 스레드가 한 레이아웃 · 스냅숏 알림 경합 — 앱은 한 UI 스레드) */
        fun isHarness(e: Throwable): Boolean =
            "performMeasureAndLayout called during measure layout" in (e.message ?: "") ||
                (e is NullPointerException && e.stackTrace.any { it.methodName == "addPendingInvalidationsLocked" })

        /** 같은 예외 가르기: 종류 + 앱 코드 첫 자리(없으면 첫 자리) + 원인의 같은 것 */
        fun signature(e: Throwable): String {
            fun one(t: Throwable): String {
                val at = t.stackTrace.firstOrNull { it.className.startsWith("com.bmt") } ?: t.stackTrace.firstOrNull()
                val msg = if (t is Hang) " ${t.message?.substringBefore(" — ")}" else ""
                return "${t.javaClass.simpleName}$msg @ ${at?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }}"
            }
            return one(e) + (e.cause?.takeIf { it !== e }?.let { " ← " + one(it) } ?: "")
        }
    }
}
