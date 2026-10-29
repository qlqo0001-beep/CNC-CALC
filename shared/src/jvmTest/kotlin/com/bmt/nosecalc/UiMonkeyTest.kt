@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package com.bmt.nosecalc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.screens.CuttingScreen
import com.bmt.nosecalc.screens.PolarScreen
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.SimulatorScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.ThreadScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.cutting.CutTab
import com.bmt.nosecalc.screens.cutting.CuttingState
import com.bmt.nosecalc.screens.cutting.cutBad
import com.bmt.nosecalc.screens.cutting.cutFeedTooBig
import com.bmt.nosecalc.screens.cutting.cutFromN
import com.bmt.nosecalc.screens.cutting.cutG96
import com.bmt.nosecalc.screens.cutting.cutHand
import com.bmt.nosecalc.screens.cutting.drillBetween
import com.bmt.nosecalc.screens.cutting.drillEmpty
import com.bmt.nosecalc.screens.cutting.drillIscarRows
import com.bmt.nosecalc.screens.cutting.drillManual
import com.bmt.nosecalc.screens.cutting.drillManualBad
import com.bmt.nosecalc.screens.cutting.drillMitsubishi
import com.bmt.nosecalc.screens.cutting.drillNoFeed
import com.bmt.nosecalc.screens.cutting.drillNoVc
import com.bmt.nosecalc.screens.cutting.drillOutsideRow
import com.bmt.nosecalc.screens.cutting.drillOutsideSeries
import com.bmt.nosecalc.screens.cutting.drillSandvik
import com.bmt.nosecalc.screens.polar.PolarState
import com.bmt.nosecalc.screens.polar.SimStart
import com.bmt.nosecalc.screens.polar.polarBadWhileWaiting
import com.bmt.nosecalc.screens.polar.polarInnerSmallR
import com.bmt.nosecalc.screens.polar.polarInnerSquare
import com.bmt.nosecalc.screens.polar.polarOriginal07
import com.bmt.nosecalc.screens.polar.polarOriginal12
import com.bmt.nosecalc.screens.polar.polarTriangle
import com.bmt.nosecalc.screens.polar.polarTwoFlat
import com.bmt.nosecalc.screens.polar.polarWgeOd
import com.bmt.nosecalc.screens.round.HOLDER_PRESETS
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerBlindC
import com.bmt.nosecalc.screens.round.roundInnerDomeA
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundInnerSocketF
import com.bmt.nosecalc.screens.round.roundInnerStep
import com.bmt.nosecalc.screens.round.roundManualBackBoring
import com.bmt.nosecalc.screens.round.roundManualInnerMouth
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import com.bmt.nosecalc.screens.sim.UiQueue
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.taper.userAngleExample
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.threadBadWhileWaiting
import com.bmt.nosecalc.screens.thread.threadFormula
import com.bmt.nosecalc.screens.thread.threadMetricInner
import com.bmt.nosecalc.screens.thread.threadOriginal
import com.bmt.nosecalc.screens.thread.threadReverse
import com.bmt.nosecalc.screens.thread.threadWarn
import com.bmt.nosecalc.screens.thread.threadZOrder
import com.bmt.nosecalc.screens.triangle.TriangleState
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.PillButton
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * 몽키 시험(사용자 10-28 "팁번호 설정시 팅기는 문제" — 시뮬레이터 공구 시트의 '고르기' 가 앱을 껐다. 값만 흔든 견고성 시험은
 * 시트 · 단추를 실제로 눌러 들어가지 않아 못 잡았다). 시뮬레이터 말고 모든 화면에서 **누를 수 있는 것을 다 눌러 본다**.
 *
 * - 장면: 실제 화면(App 또는 시작 상태를 넣은 같은 틀)을 [ImageComposeScene] 에 폰 크기로 — 폭 412 · 360dp, 글꼴 1.0 · 1.3, 라이트 · 다크를
 *   시작 상태마다 돌려 쓴다. 효과는 [UiQueue](앱처럼 한 UI 스레드 — 헤드리스 경합을 없앤다), 클립보드는 가짜, 저장소는 안드로이드처럼
 *   Bundle 에 넣을 수 있는 값만 받는 [SaveableStateRegistry].
 * - ① 넓이 우선 누르기: 의미 트리에서 OnClick(누르기) · ScrollBy(끝까지 굴리기) 가 있는 노드를 모아 하나씩 누르고 프레임을 그린다.
 *   누른 뒤 새로 생긴 노드(시트 · 창 · 메뉴 · 다른 화면)를 다시 모아 깊이 [Plan.depth] 까지. 시스템 뒤로도 한 번씩.
 *   숫자칸을 누르면 키패드가 열린다 — 그 자리에서 키(숫자 · . · − · C · ⌫ · 비우기)를 무작위로(극단 값 꼴 섞어) 누르고 확인 · 취소 · 뒤로로 닫는다.
 *   누른 뒤 화면 글이 바뀌면 뒤로 두 번까지 되돌려 보고, 안 돌아오면 처음 상태에서 경로를 다시 밟는다(경로는 늘 기록).
 *   닫기 · 뒤로 · 취소 · 확인 · 덮개 · 메뉴의 기능 카드는 그 경로의 끝이다.
 * - ② 손가락 몽키: 진짜 터치(아무 데나 · 단추 가운데 누름, 끌기, 두 손가락 벌리기, 두 번 누름, 뒤로) — 의미 트리에 없는 pointerInput(그림 누르기 ·
 *   형상 뷰어 끌기 · 확대 · 시뮬레이션 막대)까지.
 * - ③ 저장 · 복원: 무작위로 누른 뒤 저장소를 performSave → 자바 직렬화(안드로이드가 Bundle 을 쓸 때처럼) → 새 장면에 복원해 그려 보고 더 누른다.
 * 시작 상태: 빈칸 처음 화면 · 예시(TaperExample · roundExample · CuttingExample …) · 오류 상태 · 극단 값(0 · 음수 · 12자리 · 아주 작은 수).
 * 예외(누르기 · 그리기 · 효과)마다 **누르기 경로 · 시작 상태 · 화면 설정 · 스택**을 모으고, 짧은 경로로 다시 밟아 재현되는지 본다.
 * 교착은 감시 스레드가 [STALL_MS] 동안 프레임이 없으면 스택을 떠 둔다. 씨앗은 고정(시작 상태 이름 · 설정에서 만든다) — 같은 차례로 밟고
 * (어디까지 가는지는 시간 예산 · PC 속도에 따른다), 찾은 것은 기록한 경로를 그대로 다시 밟아 재현한다.
 * 결과는 build/monkey/<시험>.txt. 재현되는 앱 예외가 있으면 실패한다(헤드리스에서만 나는 것으로 보이는 것은 적기만 한다).
 * 시험 하나가 15분을 넘지 않게 기능별로 나눴다(--tests 로 하나씩).
 */
class UiMonkeyTest {
    data class Cfg(val widthDp: Int, val font: Float, val dark: Boolean) {
        override fun toString() = "${widthDp}dp · 글꼴 $font · ${if (dark) "다크" else "라이트"}"
    }

    /** 시작 상태: 이름(씨앗도 여기서) · 앱 상태 맞추기 · 그릴 것 */
    class Start(val name: String, val setup: (AppState) -> Unit, val content: @Composable (AppState) -> Unit) {
        override fun toString() = name
    }

    /**
     * 시작 상태 하나를 얼마나: 넓이 우선 깊이 · 시간(초), 손가락 몽키 횟수, 저장 · 복원 걸음.
     * lead = 손가락 몽키를 시작하기 전에 누를 글(예: '크게 보기') — 의미 트리에 없는 곳(뷰어 끌기 · 확대)에 손가락을 모은다
     */
    data class Plan(val depth: Int, val seconds: Int, val touches: Int = 0, val restoreSteps: Int = 0, val lead: List<String> = emptyList())

    // ───────────────────────── 누르기 기록

    sealed class Act {
        /** 의미 노드 누르기 */
        data class Tap(val key: String) : Act() {
            override fun toString() = "누름[${short(key)}]"
        }

        /** 숫자칸을 눌러 키패드를 열고 keys 를 차례로(마지막은 닫기: 확인 · 취소 · 뒤로 · 비우기) */
        data class Type(val key: String, val keys: MutableList<String>) : Act() {
            override fun toString() = "칸[${short(key)}] 키 ${keys.joinToString(" ")}"
        }

        /** 굴릴 수 있는 것을 끝까지(한 화면씩) */
        data class Scroll(val key: String) : Act() {
            override fun toString() = "굴림[${short(key)}]"
        }

        data object Back : Act() {
            override fun toString() = "뒤로"
        }

        /** 진짜 손가락(dp): 누름 · 두 번 누름 · 끌기 · 두 손가락 벌리기(배율) */
        data class Touch(val x: Float, val y: Float, val double: Boolean = false) : Act() {
            override fun toString() = "${if (double) "두 번 " else ""}터치(${x.roundToInt()},${y.roundToInt()})"
        }

        data class Drag(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Act() {
            override fun toString() = "끌기(${x0.roundToInt()},${y0.roundToInt()}→${x1.roundToInt()},${y1.roundToInt()})"
        }

        data class Pinch(val x: Float, val y: Float, val scale: Float) : Act() {
            override fun toString() = "벌리기(${x.roundToInt()},${y.roundToInt()} ×$scale)"
        }
    }

    // ───────────────────────── 찾은 것

    /** where = 누르기 · 그리기 · 효과 · 교착 · 저장 · 글 */
    class Finding(val test: String, val start: String, val cfg: Cfg, val path: List<Act>, val where: String, val error: Throwable?, val note: String = "") {
        var count = 1
        var shortPath: List<Act>? = null
        var reproduced: Boolean? = null
        val harness: Boolean get() = error?.let(::harnessLike) ?: false
        val sig: String get() = error?.let(::signatureOf) ?: "$where:$note"
    }

    private class ReplayMismatch(msg: String) : Exception(msg)
    private class EffectError(cause: Throwable) : Exception("효과(코루틴)에서 예외: $cause", cause)
    private class RenderError(cause: Throwable) : Exception("그리기에서 예외: $cause", cause)
    private class TapError(cause: Throwable) : Exception("누르기에서 예외: $cause", cause)

    private class Clip : ClipboardManager {
        var held: AnnotatedString? = null
        override fun getText(): AnnotatedString? = held
        override fun setText(annotatedString: AnnotatedString) {
            held = annotatedString
        }
    }

    /** 의미 노드 하나: key = 종류 + 글 + 같은 글 중 몇째 */
    private class Cand(val key: String, val label: String, val click: (() -> Boolean)?, val scroll: ((Float, Float) -> Boolean)?, val node: SemanticsNode)

    // ───────────────────────── 장면

    /**
     * 설정(폭 · 글꼴 · 테마)마다 장면 하나를 두고 내용만 바꿔(setContent) 다시 쓴다. 장면을 새로 만들면 그때마다 글꼴(noto_sans_kr 10MB)이
     * 네이티브 메모리에 다시 올라가 장면당 35MB 쯤 남는다(GC 로도 안 풀림 — 시험 JVM 이 10GB 넘게 자라 죽었다). 예외가 난 장면은 버리고 새로 만든다
     */
    private inner class Pooled(val cfg: Cfg) {
        val ui = UiQueue()
        val bg: MutableList<Throwable> = Collections.synchronizedList(ArrayList())
        val scene = ImageComposeScene(
            (cfg.widthDp * DENSITY).roundToInt(), (HEIGHT_DP * DENSITY).roundToInt(), Density(DENSITY, cfg.font),
            coroutineContext = ui + CoroutineExceptionHandler { _, e -> bg += e },
        ) {}
        var clock = 0L
        var pointerMs = 0L
        var fresh = true

        fun dispose() {
            runCatching { scene.close() }
            // ImageComposeScene.close 는 그림판(Surface, 화면 크기 네이티브 메모리)을 닫지 않는다 — 바로 닫는다
            try {
                (SURFACE.get(scene) as AutoCloseable).close()
            } catch (t: Throwable) {
                count("그림판 닫기 실패")
            }
        }
    }

    private val pool = HashMap<Cfg, Pooled>()

    private fun takeScene(cfg: Cfg): Pooled = synchronized(pool) { pool.remove(cfg) } ?: Pooled(cfg).also { count("장면 만듦") }

    private fun disposePool() = synchronized(pool) {
        pool.values.forEach { it.dispose() }
        pool.clear()
    }

    private inner class Session(
        val start: Start,
        val cfg: Cfg,
        val store: File = tempStore(),
        restored: Map<String, List<Any?>>? = null,
    ) : AutoCloseable {
        val state = AppState(KeyValueStore(store))
        val clip = Clip()
        val registry = SaveableStateRegistry(restored) { bundleable(it) }
        val log = ArrayList<Act>()
        private val p = takeScene(cfg)
        val scene: ImageComposeScene get() = p.scene
        private val ui get() = p.ui
        private val bg get() = p.bg
        val clock: Long get() = p.clock

        /** 예외가 난 장면(그림 · 효과 · 손가락 상태가 어떤지 모른다) — 다시 쓰지 않는다 */
        private var broken = false
        private var closed = false

        init {
            current = this
            bg.clear()
            state.setTheme(cfg.dark)
            start.setup(state)
            // 방금 쓴 상태의 알림을 이 스레드에서 끝낸다(Shots.take 와 같은 까닭)
            Snapshot.sendApplyNotifications()
            try {
                scene.setContent {
                    CompositionLocalProvider(LocalClipboardManager provides clip, LocalSaveableStateRegistry provides registry) { start.content(state) }
                }
                // 글꼴 리소스는 비동기로 읽힌다(한 번 읽으면 캐시) — 새 장면의 처음 몇 프레임은 실제 시간을 조금 흘린다
                frames(4, sleepMs = if (p.fresh) 5 else 0)
                p.fresh = false
            } catch (t: Throwable) {
                broken = true
                close()
                throw t
            }
        }

        fun frames(n: Int, sleepMs: Long = 0) = repeat(n) {
            if (sleepMs > 0) Thread.sleep(sleepMs)
            p.clock += FRAME_NS
            try {
                ui.drain()
                Snapshot.sendApplyNotifications()
                scene.render(p.clock).close()
                ui.drain()
            } catch (t: Throwable) {
                broken = true
                throw RenderError(t)
            }
            progress.set(System.currentTimeMillis())
            if (bg.isNotEmpty()) {
                broken = true
                throw EffectError(bg.removeAt(0))
            }
        }

        /**
         * 누를 수 있는 노드. 덮개(글 없이 누를 수 있고 화면 절반 넘게 덮는 것 — 시트 · 메뉴 뒤 덮개) 아래에 깔린 노드는 뺀다:
         * 덮개보다 앞(먼저 그려짐)이면서 가운데가 덮개 안이거나 화면 밖(스크롤에 가려 자리가 빈)인 것 — 손으로는 누를 수 없다
         */
        fun cands(): List<Cand> {
            val nodes = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
            val area = cfg.widthDp * DENSITY * HEIGHT_DP * DENSITY
            val scrims = nodes.withIndex().filter { (_, n) ->
                val b = n.boundsInRoot
                n.config.getOrNull(SemanticsActions.OnClick) != null && labelOf(n).isEmpty() && b.width * b.height >= area * 0.5f
            }
            val out = ArrayList<Cand>()
            val seen = HashMap<String, Int>()
            for ((i, n) in nodes.withIndex()) {
                val c = n.config
                if (SemanticsProperties.Disabled in c) continue
                val click = c.getOrNull(SemanticsActions.OnClick)?.action
                val scroll = c.getOrNull(SemanticsActions.ScrollBy)?.action
                if (click == null && scroll == null) continue
                val b = n.boundsInRoot
                if (scrims.any { (j, sc) -> j > i && (b.isEmpty || b.center in sc.boundsInRoot) }) continue
                val label = labelOf(n)
                if (click != null) {
                    val base = "누름:$label"
                    val k = seen.merge(base, 1, Int::plus)!! - 1
                    out += Cand("$base#$k", label, click, null, n)
                }
                if (scroll != null) {
                    val base = "굴림:$label"
                    val k = seen.merge(base, 1, Int::plus)!! - 1
                    out += Cand("$base#$k", label, null, scroll, n)
                }
            }
            return out
        }

        /** 화면 상태를 가르는 글: 모든 노드의 글 · 설명 · 선택 · 켬 */
        fun texts(): List<String> = scene.semanticsOwners.flatMap { o -> walk(o.unmergedRootSemanticsNode) }.flatMap { n ->
            val c = n.config
            c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(
                    c.getOrNull(SemanticsProperties.Selected)?.let { "선택=$it" },
                    c.getOrNull(SemanticsProperties.ToggleableState)?.let { "켬=$it" },
                    c.getOrNull(SemanticsProperties.StateDescription),
                )
        }

        fun clearToast() {
            state.toast?.let { state.clearToast(it) }
        }

        private fun find(key: String): Cand = cands().firstOrNull { it.key == key } ?: throw ReplayMismatch("노드 없음: $key")

        private fun click(c: Cand) {
            try {
                c.click!!.invoke()
            } catch (t: Throwable) {
                broken = true
                throw TapError(t)
            }
            Snapshot.sendApplyNotifications()
        }

        /** 키패드가 열려 있나(확인 · 지우기 키) */
        fun keypadOpen(): Boolean {
            val labels = cands().mapTo(HashSet()) { it.label }
            return "확인" in labels && "지우기" in labels
        }

        /** 키패드 키 하나(같은 글이면 맨 위 층 — 나중 노드). 닫는 키는 '뒤로' 도 */
        fun key(k: String) {
            if (k == "뒤로") {
                state.back()
                Snapshot.sendApplyNotifications()
                frames(3)
                return
            }
            val c = cands().lastOrNull { it.click != null && it.label == k } ?: return
            click(c)
            frames(if (k in CLOSERS) 4 else 1)
        }

        /** 다시 밟기(기록 그대로) */
        fun replay(a: Act) {
            log += a
            when (a) {
                is Act.Tap -> {
                    click(find(a.key))
                    frames(4)
                }
                is Act.Type -> {
                    if (a.key != OPEN_PAD) {
                        click(find(a.key))
                        frames(3)
                    }
                    a.keys.forEach(::key)
                }
                is Act.Scroll -> {
                    find(a.key)
                    scrollEnd(a.key)
                }
                Act.Back -> {
                    state.back()
                    Snapshot.sendApplyNotifications()
                    frames(4)
                }
                else -> touch(a)
            }
        }

        /** 탐색: 노드 하나를 누른다. 키패드가 열리면 무작위 키를 눌러 닫고 [Act.Type] 으로 남긴다 */
        fun explore(c: Cand, rnd: Random): Act {
            if (c.scroll != null) {
                val a = Act.Scroll(c.key)
                log += a
                scrollEnd(c.key)
                return a
            }
            val tap = Act.Tap(c.key)
            log += tap
            click(c)
            frames(3)
            if (!keypadOpen()) {
                frames(1)
                return tap
            }
            val type = Act.Type(c.key, ArrayList())
            log[log.lastIndex] = type
            val labels = cands().mapTo(HashSet()) { it.label }
            for (k in randomKeys(rnd, "비우기 · 자동 계산" in labels)) {
                type.keys += k
                key(k)
            }
            return type
        }

        fun back(): Boolean {
            log += Act.Back
            val r = state.back()
            Snapshot.sendApplyNotifications()
            frames(4)
            return r
        }

        /**
         * 굴릴 수 있는 것을 한 화면씩 끝까지(게으른 목록도 다 그려지게). 40번까지. 걸음마다 노드를 다시 찾는다 — 굴리다 화면에서 빠진(닫히는 메뉴 등)
         * 노드의 동작을 부르면 Compose 가 'node does not have an owner' 를 던지는데, 손으로는 그럴 수 없는 시험 틀의 일이다
         */
        private fun scrollEnd(key: String) {
            repeat(40) {
                val c = cands().firstOrNull { it.key == key && it.node.layoutInfo.isAttached } ?: return
                val step = c.node.boundsInRoot.height.coerceAtLeast(200f) * 0.8f
                val horizontal = c.node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) == null &&
                    c.node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
                val moved = try {
                    if (horizontal) c.scroll!!(step, 0f) else c.scroll!!(0f, step)
                } catch (t: Throwable) {
                    throw TapError(t)
                }
                frames(1)
                if (!moved) return
            }
            frames(2)
        }

        /** 진짜 손가락 */
        fun touch(a: Act) {
            fun p(x: Float, y: Float) = Offset(x * DENSITY, y * DENSITY)
            when (a) {
                is Act.Touch -> {
                    repeat(if (a.double) 2 else 1) {
                        pointer(PointerEventType.Press, listOf(p(a.x, a.y)))
                        pointer(PointerEventType.Release, listOf(p(a.x, a.y)), pressed = false)
                        frames(1)
                    }
                    frames(3)
                }
                is Act.Drag -> {
                    pointer(PointerEventType.Press, listOf(p(a.x0, a.y0)))
                    for (i in 1..8) {
                        val t = i / 8f
                        pointer(PointerEventType.Move, listOf(p(a.x0 + (a.x1 - a.x0) * t, a.y0 + (a.y1 - a.y0) * t)))
                        if (i % 3 == 0) frames(1)
                    }
                    pointer(PointerEventType.Release, listOf(p(a.x1, a.y1)), pressed = false)
                    frames(4)
                }
                is Act.Pinch -> {
                    val d0 = 30f
                    fun two(d: Float) = listOf(p(a.x - d, a.y), p(a.x + d, a.y))
                    pointer(PointerEventType.Press, two(d0).take(1))
                    pointer(PointerEventType.Press, two(d0))
                    for (i in 1..6) {
                        pointer(PointerEventType.Move, two(d0 * (1 + (a.scale - 1) * i / 6f)))
                        if (i % 2 == 0) frames(1)
                    }
                    val end = two(d0 * a.scale)
                    pointer(PointerEventType.Release, end, pressed = false, releasedOnly = 1)
                    pointer(PointerEventType.Release, end.take(1), pressed = false)
                    frames(4)
                }
                else -> error("손가락 아님: $a")
            }
        }

        /** pressed = 놓는 손가락 상태. releasedOnly = 그 번호 손가락만 놓고 나머지는 누른 채 */
        private fun pointer(type: PointerEventType, at: List<Offset>, pressed: Boolean = true, releasedOnly: Int? = null) {
            p.pointerMs = maxOf(p.pointerMs + 16, p.clock / 1_000_000)
            val ps = at.mapIndexed { i, o ->
                ComposeScenePointer(PointerId(i.toLong()), o, if (releasedOnly != null) i != releasedOnly else pressed, PointerType.Touch)
            }
            try {
                scene.sendPointerEvent(type, ps, timeMillis = p.pointerMs)
            } catch (t: Throwable) {
                broken = true
                throw TapError(t)
            }
            Snapshot.sendApplyNotifications()
        }

        override fun close() {
            if (closed) return
            closed = true
            if (current === this) current = null
            // 내용을 비워 이 화면의 구성 · 효과를 끝내고, 멀쩡하면 장면을 돌려준다
            if (!broken) {
                try {
                    scene.setContent {}
                    p.clock += FRAME_NS
                    ui.drain()
                    Snapshot.sendApplyNotifications()
                    scene.render(p.clock).close()
                    ui.drain()
                    broken = bg.isNotEmpty()
                } catch (t: Throwable) {
                    broken = true
                }
            }
            if (broken) {
                count("장면 버림")
                p.dispose()
            } else {
                synchronized(pool) { pool.put(cfg, p)?.dispose() }
            }
        }
    }

    // ───────────────────────── 실행 한 번(시험 메서드 하나)

    private val findings = ArrayList<Finding>()
    private val stats = LinkedHashMap<String, Int>()
    private val tapped = HashSet<String>()
    private val oddTexts = HashSet<String>()
    private var testName = ""

    @Volatile private var current: Session? = null
    private val progress = AtomicLong(System.currentTimeMillis())

    private fun count(what: String, n: Int = 1) {
        stats.merge(what, n, Int::plus)
    }

    private fun record(start: Start, cfg: Cfg, path: List<Act>, t: Throwable) {
        val (where, cause) = when (t) {
            is TapError -> "누르기" to t.cause!!
            is RenderError -> "그리기" to t.cause!!
            // 다시 구성하다 난 예외도 Recomposer 코루틴을 거쳐 처리기로 온다 — 그리기로 친다
            is EffectError -> (if (composing(t.cause!!)) "그리기" else "효과") to t.cause!!
            else -> "시험 틀" to t
        }
        add(Finding(testName, start.name, cfg, path.toList(), where, cause))
    }

    private fun add(f: Finding) {
        val same = findings.firstOrNull { it.sig == f.sig }
        if (same != null) {
            same.count++
            // 더 짧은 경로를 남긴다
            if (f.path.size < same.path.size) findings[findings.indexOf(same)] = f.also { it.count = same.count }
            return
        }
        findings += f
    }

    /** 처음 상태에서 path 를 밟은 장면. 밟다가 예외면 기록하고 null, 노드가 안 맞으면(경로 어긋남) null */
    private fun open(start: Start, cfg: Cfg, path: List<Act>): Session? {
        val s = try {
            newSession(start, cfg)
        } catch (t: Throwable) {
            record(start, cfg, emptyList(), t)
            return null
        }
        try {
            path.forEach(s::replay)
            return s
        } catch (e: ReplayMismatch) {
            count("다시 밟기 어긋남")
        } catch (t: Throwable) {
            record(start, cfg, s.log, t)
        }
        s.close()
        return null
    }

    /** 그림판(네이티브 메모리)을 못 만들면 GC 를 돌리고 한 번 더 */
    private fun newSession(start: Start, cfg: Cfg): Session = try {
        Session(start, cfg)
    } catch (t: Throwable) {
        if ("Failed Surface.makeRaster" !in (t.message ?: "")) throw t
        count("그림판 다시 만듦")
        System.gc()
        Thread.sleep(500)
        Session(start, cfg)
    }

    private fun checkTexts(s: Session) {
        s.texts().filter { t -> ODD.any { it in t } }.forEach { t ->
            if (oddTexts.add(t)) add(Finding(testName, s.start.name, s.cfg, s.log.toList(), "글", null, "화면 글에 NaN · Infinity · null: '$t'"))
        }
        s.clip.held?.text?.let { t ->
            if (ODD.any { it in t } && oddTexts.add(t)) add(Finding(testName, s.start.name, s.cfg, s.log.toList(), "글", null, "복사한 글에 NaN · Infinity · null: '$t'"))
        }
    }

    /** ① 넓이 우선 누르기 */
    private fun bfs(start: Start, cfg: Cfg, plan: Plan, rnd: Random) {
        class Entry(val path: List<Act>, val depth: Int, val only: Set<String>?)

        val deadline = System.currentTimeMillis() + (plan.seconds * 1000L * SCALE).toLong()
        val queue = ArrayDeque(listOf(Entry(emptyList(), 0, null)))
        val seen = HashSet<String>()
        while (queue.isNotEmpty() && System.currentTimeMillis() < deadline) {
            val e = queue.removeFirst()
            count("넓이 우선 상태")
            var s: Session? = open(start, cfg, e.path) ?: continue
            try {
                val base = s!!.cands()
                val baseKeys = base.mapTo(HashSet()) { it.key }
                s.clearToast()
                val baseTexts = s.texts()
                val todo = base.filter { e.only == null || it.key in e.only }.map { it.key } + BACK
                for (key in todo) {
                    if (System.currentTimeMillis() > deadline) break
                    if (s == null) s = open(start, cfg, e.path) ?: break
                    val ses = s
                    val act: Act
                    try {
                        act = if (key == BACK) {
                            ses.back()
                            Act.Back
                        } else {
                            val c = ses.cands().firstOrNull { it.key == key } ?: run { count("노드 사라짐"); null }
                            if (c == null) {
                                if (!ses.restoreOrNull(baseTexts)) { ses.close(); s = null }
                                continue
                            }
                            tapped += c.label
                            ses.explore(c, rnd)
                        }
                        count("누름")
                        checkTexts(ses)
                    } catch (t: Throwable) {
                        record(start, cfg, ses.log, t)
                        ses.close()
                        s = null
                        continue
                    }
                    val label = key.substringAfter(':').substringBeforeLast('#')
                    if (key != BACK && !terminal(label) && e.depth + 1 < plan.depth) {
                        val fresh = ses.cands().filter { it.key !in baseKeys }
                        if (fresh.isNotEmpty()) {
                            val sig = if (e.depth == 0) fresh.map { it.key }.sorted().joinToString("\n") else fresh.map { norm(it.label) }.toSortedSet().joinToString("\n")
                            if (seen.add(sig)) queue += Entry(e.path + act, e.depth + 1, fresh.mapTo(HashSet()) { it.key })
                        }
                    }
                    try {
                        if (!ses.restoreOrNull(baseTexts)) {
                            ses.close()
                            s = null
                        }
                    } catch (t: Throwable) {
                        record(start, cfg, ses.log, t)
                        ses.close()
                        s = null
                    }
                }
            } finally {
                s?.close()
            }
        }
        if (queue.isNotEmpty()) count("시간 끝에 남은 상태", queue.size)
    }

    /** 뒤로 두 번까지로 처음 글로 돌아오면 true */
    private fun Session.restoreOrNull(base: List<String>): Boolean {
        clearToast()
        frames(1)
        if (texts() == base) return true
        repeat(2) {
            back()
            clearToast()
            frames(1)
            if (texts() == base) {
                count("뒤로 되돌림")
                return true
            }
        }
        count("처음부터 다시")
        return false
    }

    /** ② 손가락 몽키(진짜 터치) */
    private fun touches(start: Start, cfg: Cfg, n: Int, rnd: Random, lead: List<String> = emptyList()) {
        var s: Session? = null
        var left = n
        while (left > 0) {
            if (s == null) {
                val ns = newSession(start, cfg)
                s = ns
                for (label in lead) {
                    val c = ns.cands().firstOrNull { it.click != null && it.label == label }
                    count(if (c == null) "손가락 앞 '$label' 못 찾음" else "손가락 앞 '$label' 누름")
                    c?.let { ns.explore(it, rnd) }
                }
            }
            val ses = s
            val w = cfg.widthDp.toFloat()
            val h = HEIGHT_DP.toFloat()
            try {
                val a: Act = when (rnd.nextInt(100)) {
                    in 0..34 -> Act.Touch(rnd.nextFloat() * w, rnd.nextFloat() * h)
                    in 35..59 -> {
                        // 누를 수 있는 것의 가운데를 진짜 손가락으로(덮개에 가린 것은 덮개가 받는다 — 폰과 같다)
                        val cs = ses.cands().filter { it.click != null }
                        val b = cs.getOrNull(rnd.nextInt(cs.size.coerceAtLeast(1)))?.node?.boundsInRoot
                        if (b == null || b.isEmpty) Act.Touch(rnd.nextFloat() * w, rnd.nextFloat() * h)
                        else Act.Touch(b.center.x / DENSITY, b.center.y / DENSITY)
                    }
                    in 60..74 -> {
                        val x = rnd.nextFloat() * w
                        val y = rnd.nextFloat() * h
                        if (rnd.nextBoolean()) Act.Drag(x, y, x + rnd.nextInt(-80, 81), y + rnd.nextInt(-500, 501))
                        else Act.Drag(x, y, x + rnd.nextInt(-300, 301), y + rnd.nextInt(-60, 61))
                    }
                    in 75..81 -> Act.Pinch(rnd.nextFloat() * w * .6f + w * .2f, rnd.nextFloat() * h * .6f + h * .2f, listOf(0.4f, 0.7f, 1.6f, 3f).random(rnd))
                    in 82..86 -> Act.Touch(rnd.nextFloat() * w, rnd.nextFloat() * h, double = true)
                    in 87..91 -> Act.Back
                    else -> {
                        // 키패드가 열렸으면 키 몇 개
                        if (ses.keypadOpen()) {
                            val keys = randomKeys(rnd, false)
                            ses.log += Act.Type(OPEN_PAD, keys.toMutableList())
                            keys.forEach(ses::key)
                            left--
                            count("손가락")
                            checkTexts(ses)
                            continue
                        }
                        Act.Touch(rnd.nextFloat() * w, rnd.nextFloat() * h)
                    }
                }
                if (a == Act.Back) {
                    // 더 갈 곳이 없으면 안드로이드는 앱을 닫는다 — 처음부터
                    if (!ses.back()) {
                        ses.close()
                        s = null
                    }
                } else {
                    ses.log += a
                    ses.touch(a)
                }
                count("손가락")
                left--
                checkTexts(ses)
                ses.clearToast()
            } catch (t: Throwable) {
                record(start, cfg, ses.log, t)
                ses.close()
                s = null
                left--
            }
        }
        s?.close()
    }

    /** ③ 저장 · 복원: 무작위로 누르고 → 저장(직렬화) → 새 장면에 복원 → 그리고 더 누른다 */
    private fun restore(start: Start, cfg: Cfg, steps: Int, rnd: Random) {
        val store = tempStore()
        val s1 = try {
            Session(start, cfg, store)
        } catch (t: Throwable) {
            record(start, cfg, emptyList(), t)
            return
        }
        val before: List<String>
        val saved: Map<String, List<Any?>>
        try {
            randomWalk(s1, steps, rnd)
            before = s1.texts()
            val raw = try {
                s1.registry.performSave()
            } catch (t: Throwable) {
                add(Finding(testName, start.name, cfg, s1.log.toList(), "저장", t, "performSave"))
                return
            }
            saved = try {
                val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { o -> o.writeObject(toBundle(raw)) } }.toByteArray()
                @Suppress("UNCHECKED_CAST")
                fromBundle(ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }) as Map<String, List<Any?>>
            } catch (t: Throwable) {
                add(Finding(testName, start.name, cfg, s1.log.toList(), "저장", t, "Bundle(직렬화)에 못 넣음"))
                return
            }
            count("저장 · 복원")
        } catch (t: Throwable) {
            record(start, cfg, s1.log, t)
            return
        } finally {
            s1.close()
        }
        val path = s1.log.toList() + Act.Tap(RESTORED)
        val feature = s1.state.current
        val s2 = try {
            Session(start, cfg, store, restored = saved)
        } catch (t: Throwable) {
            add(Finding(testName, start.name, cfg, path, "복원", unwrap(t), "복원한 장면 그리기"))
            return
        }
        try {
            // 화면 쌓임(AppState.stack)은 저장하지 않아 안드로이드가 앱을 되살리면 첫 화면이다 — 적어 두고, 사람처럼 메뉴로 그 화면에 가서 칸 값을 비교한다
            if (s2.state.current != feature) {
                add(Finding(testName, start.name, cfg, path, "복원 차이", null, "화면 쌓임은 저장되지 않음 — 복원 뒤 ${s2.state.current.title} (저장 때 ${feature.title})"))
                s2.state.open(feature)
                Snapshot.sendApplyNotifications()
                s2.frames(4)
            }
            val after = s2.texts()
            val lost = (before - after.toSet()).take(6)
            if (lost.isNotEmpty()) add(Finding(testName, start.name, cfg, path, "복원 차이", null, "복원 뒤 없어진 글: $lost"))
            randomWalk(s2, steps / 2, rnd)
        } catch (t: Throwable) {
            add(Finding(testName, start.name, cfg, path + s2.log, "복원 뒤", unwrap(t)))
        } finally {
            s2.close()
        }
    }

    /** 무작위로 steps 번 누르고(키패드는 무작위 키), 끝에 위에 뜬 것(키패드 · 시트 · 메뉴 — 저장하지 않는다)을 닫는다 */
    private fun randomWalk(s: Session, steps: Int, rnd: Random) {
        repeat(steps) {
            val cs = s.cands().filter { c -> !c.label.startsWith(Feature.SIMULATOR.menuTitle) }
            if (cs.isEmpty()) return@repeat
            val c = cs[rnd.nextInt(cs.size)]
            tapped += c.label
            s.explore(c, rnd)
            count("누름")
            checkTexts(s)
            s.clearToast()
        }
        var guard = 0
        while ((s.state.overlays.isNotEmpty() || s.state.menuOpen || s.state.helpOpen) && guard++ < 4) s.back()
        // 메뉴가 접히는 애니메이션이 끝날 때까지(남은 메뉴 글이 저장 전 글에 섞이지 않게)
        s.frames(10)
    }

    /** 한 시작 상태를 감시 스레드 아래에서(교착이면 스택을 떠 두고 넘어간다) */
    private fun guarded(start: Start, cfg: Cfg, what: String, block: () -> Unit) {
        var failure: Throwable? = null
        val th = Thread({
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }, "monkey-${start.name}").apply { isDaemon = true }
        progress.set(System.currentTimeMillis())
        th.start()
        while (th.isAlive) {
            th.join(2_000)
            if (th.isAlive && System.currentTimeMillis() - progress.get() > STALL_MS) {
                val s = current
                val stack = th.stackTrace
                add(
                    Finding(
                        testName, start.name, cfg, s?.log?.toList().orEmpty(), "교착", null,
                        "$what: ${STALL_MS / 1000}초 동안 프레임 없음\n" + stack.take(40).joinToString("\n") { "      at $it" },
                    ),
                )
                th.interrupt()
                current = null
                return
            }
        }
        failure?.let { record(start, cfg, current?.log.orEmpty(), it) }
    }

    /** 시작 상태들을 돌린다. 설정은 시작 상태마다 차례로 돌려 쓴다(cfgShift 로 어긋나게). MonkeyRecheckTest 도 부른다 */
    internal fun monkey(name: String, starts: List<Pair<Start, Plan>>, cfgShift: Int = 0) {
        // 짧은 배율로도 약 7분 — BMT_MONKEY 가 있을 때만(평소 jvmTest 에서는 건너뜀, selfCheck 는 늘 돈다)
        assumeTrue(System.getenv("BMT_MONKEY") != null, "BMT_MONKEY 가 없어 건너뜀(몽키 시험)")
        testName = name
        val t0 = System.currentTimeMillis()
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        val stray = Collections.synchronizedList(ArrayList<Pair<String, Throwable>>())
        Thread.setDefaultUncaughtExceptionHandler { th, e -> stray += th.name to e }
        try {
            for ((i, sp) in starts.withIndex()) {
                val (start, plan) = sp
                val cfg = CFGS[(i + cfgShift) % CFGS.size]
                val seed = (start.name + cfg).hashCode()
                count("시작 상태")
                guarded(start, cfg, "넓이 우선") { bfs(start, cfg, plan, Random(seed)) }
                val nTouch = (plan.touches * SCALE).roundToInt()
                if (nTouch > 0) {
                    // 손가락 몽키는 다른 설정으로(같은 시작 상태를 다른 폭 · 글꼴 · 테마에서)
                    val cfg2 = CFGS[(i + cfgShift + 1) % CFGS.size]
                    guarded(start, cfg2, "손가락") { touches(start, cfg2, nTouch, Random(seed + 1), plan.lead) }
                }
                if (plan.restoreSteps > 0) {
                    val cfg3 = CFGS[(i + cfgShift + 2) % CFGS.size]
                    guarded(start, cfg3, "저장 · 복원") { restore(start, cfg3, plan.restoreSteps, Random(seed + 2)) }
                }
            }
            for ((th, e) in stray) add(Finding(testName, "(다른 스레드 $th)", CFGS[0], emptyList(), "다른 스레드", e))
            verify(starts.map { it.first })
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(prevHandler)
            disposePool()
        }
        val secs = (System.currentTimeMillis() - t0) / 1000
        val report = report(secs)
        File(Shots.dir.parentFile, "monkey").apply { mkdirs() }.resolve("$name.txt").writeText(report)
        println(report)
        val real = findings.filter { it.error != null && it.reproduced == true && !it.harness }
        assertTrue(real.isEmpty(), "재현되는 앱 예외 ${real.size}건 — build/monkey/$name.txt\n" + real.joinToString("\n\n") { describe(it) })
    }

    /**
     * 예외마다 새 장면에서 같은 경로를 다시 밟아 재현되는지 보고, 재현되면 경로를 줄인다(앞에서부터 하나씩 빼 보고 그래도 같은 예외면 뺀 채로 —
     * 다시 밟기 40번 · 3분까지). 다시 밟기는 감시 스레드 아래(멈추면 재현 안 됨으로)
     */
    private fun verify(starts: List<Start>) {
        val deadline = System.currentTimeMillis() + 3 * 60_000L
        for (f in findings.toList()) {
            val err = f.error ?: continue
            if (f.where == "저장" || f.where == "복원" || f.where == "복원 뒤" || f.where == "다른 스레드") continue
            val start = starts.firstOrNull { it.name == f.start } ?: continue
            val sig = signatureOf(err)
            fun fails(path: List<Act>): Boolean {
                var again: Throwable? = null
                val th = Thread({
                    val s = try {
                        Session(start, f.cfg)
                    } catch (t: Throwable) {
                        again = unwrap(t)
                        return@Thread
                    }
                    try {
                        path.forEach(s::replay)
                    } catch (t: Throwable) {
                        again = unwrap(t)
                    } finally {
                        s.close()
                    }
                }, "monkey-verify").apply { isDaemon = true }
                th.start()
                th.join(120_000)
                if (th.isAlive) {
                    th.interrupt()
                    return false
                }
                return again?.let { signatureOf(it) == sig } ?: false
            }
            f.reproduced = fails(f.path)
            if (f.reproduced != true) continue
            var path = f.path
            var i = 0
            var tries = 0
            while (i < path.size - 1 && tries++ < 40 && System.currentTimeMillis() < deadline) {
                val cut = path.take(i) + path.drop(i + 1)
                if (fails(cut)) path = cut else i++
            }
            f.shortPath = path
        }
    }

    private fun unwrap(t: Throwable): Throwable = if (t is TapError || t is RenderError || t is EffectError) t.cause ?: t else t

    private fun describe(f: Finding): String = buildString {
        append("■ [${f.where}] ${f.start} · ${f.cfg}")
        if (f.count > 1) append(" (같은 예외 ${f.count}번)")
        f.reproduced?.let { append(if (it) " · 다시 밟아 재현됨" else " · 다시 밟아 재현 안 됨") }
        if (f.harness) append(" · 헤드리스 경합 꼴")
        append('\n')
        append("  경로(${f.path.size}): ").append(if (f.path.isEmpty()) "(처음 그리기)" else f.path.joinToString(" → ")).append('\n')
        f.shortPath?.takeIf { it.size < f.path.size }?.let { p ->
            append("  줄인 경로(${p.size}): ").append(if (p.isEmpty()) "(처음 그리기)" else p.joinToString(" → ")).append('\n')
        }
        if (f.note.isNotEmpty()) append("  ").append(f.note).append('\n')
        f.error?.let { e ->
            append("  예외: $e\n")
            stackOf(e).forEach { append("      at $it\n") }
            var c = e.cause
            var depth = 0
            while (c != null && depth++ < 3) {
                append("  원인: $c\n")
                stackOf(c).take(8).forEach { append("      at $it\n") }
                c = c.cause
            }
        }
    }

    private fun report(secs: Long): String = buildString {
        append("UiMonkeyTest.$testName — ${secs}초\n")
        stats.forEach { (k, v) -> append("  $k: $v\n") }
        append("  누른 것(다른 글): ${tapped.size}\n")
        val errs = findings.filter { it.error != null }
        append("찾은 것: 예외 ${errs.size}건(재현 ${errs.count { it.reproduced == true }}) · 그 밖 ${findings.size - errs.size}건\n\n")
        findings.forEach { append(describe(it)).append('\n') }
        append("\n누른 글 목록:\n")
        tapped.sorted().forEach { append("  ").append(short(it)).append('\n') }
    }

    // ───────────────────────── 시작 상태

    internal fun app(name: String, f: Feature, setup: (AppState) -> Unit = {}) = Start(name, { it.open(f); setup(it) }) { App(it) }

    /** App 과 같은 틀(화면 쌓임 · 저장 묶음)에 f 화면만 처음 상태를 넣어 그린다 — 메뉴로 다른 기능에 가도 그대로 */
    internal fun screen(name: String, f: Feature, setup: (AppState) -> Unit = {}, body: @Composable () -> Unit) =
        Start(name, { it.open(f); setup(it) }) { s -> AppWith(s, f, body) }

    @Composable
    private fun AppWith(state: AppState, f0: Feature, body: @Composable () -> Unit) = AppHost(state) {
        val saved = rememberSaveableStateHolder()
        val f = state.current
        saved.SaveableStateProvider(f.name) {
            if (f == f0) body() else when (f) {
                Feature.TAPER -> TaperScreen()
                Feature.CUTTING -> CuttingScreen()
                Feature.ROUND -> RoundScreen()
                Feature.SIMULATOR -> SimulatorScreen()
                Feature.TRIANGLE -> TriangleScreen()
                Feature.POLAR -> PolarScreen()
                Feature.THREAD -> ThreadScreen()
            }
        }
    }

    private val deep = Plan(depth = 4, seconds = 150, touches = 120, restoreSteps = 12)
    private val mid = Plan(depth = 3, seconds = 50, touches = 40, restoreSteps = 8)
    private val shallow = Plan(depth = 2, seconds = 20)

    private fun taperEx(kind: TaperKind, reverse: Boolean, type: Int, edit: TaperState.() -> Unit = {}) =
        TaperState(kind, reverse, type, open = true).example().apply(edit)

    /** 테이퍼 예시: 종류 × 정 · 역 × 형상마다(얕게), 형상 1 정방향은 깊게 */
    private fun taperKinds(vararg kinds: TaperKind) = buildList {
        for (k in kinds) for (rev in listOf(false, true)) for (t in 1..k.types) {
            val plan = if (t == 1 && !rev) mid.copy(seconds = 80) else shallow
            add(screen("테이퍼 예시 ${k.label} ${if (rev) "역" else "정"} 형상 $t", Feature.TAPER) { TaperScreen(taperEx(k, rev, t)) } to plan)
        }
    }

    // ───────────────────────── 시험(BMT_MONKEY=1 에서 하나 15분 안)

    /** 앱 메뉴(7기능 카드 · 화면 테마 · 닫기) · 헤더(뒤로 · 메뉴 · 도움말 — 시트의 모든 묶음)를 기능마다 */
    @Test
    fun shell() = monkey(
        "shell",
        Feature.entries.filter { it != Feature.SIMULATOR }.flatMap { f ->
            listOf(
                app("${f.title} 메뉴 열림", f) { it.menuOpen = true } to Plan(depth = 3, seconds = 40),
                app("${f.title} 도움말 열림", f) { it.helpOpen = true } to Plan(depth = 2, seconds = 25),
            )
        } + listOf(app("테이퍼 처음(빈칸) 화면", Feature.TAPER) to Plan(depth = 3, seconds = 60, touches = 150, restoreSteps = 15)),
    )

    /** 테이퍼: 처음(빈칸) 형상 고르기 → 홑 · 2중 · 홈 / 외경 · 내경 / 정 · 역 / 형상, 입력 칸 · 팁 시트 · 결과 띠 · 뷰어 · NC */
    @Test
    fun taperBlank() = monkey(
        "taperBlank",
        listOf(app("테이퍼 처음(빈칸)", Feature.TAPER) to deep.copy(seconds = 300)) + TaperKind.entries.map { k ->
            screen("테이퍼 빈 입력 ${k.label} 형상 1", Feature.TAPER) { TaperScreen(TaperState(k, type = 1, open = true)) } to mid.copy(seconds = 70)
        },
    )

    /** 테이퍼 홑(외경 · 내경) 예시 — 정 · 역 · 뒤집기 · 보정 방식 · 각도 기준 · 팁 · 모서리 R · 시작 · 끝 · 결과 띠 · 뷰어 · NC */
    @Test
    fun taperSingle() = monkey("taperSingle", taperKinds(TaperKind.OUTER, TaperKind.INNER))

    /** 테이퍼 2중(외경 · 내경) 예시 */
    @Test
    fun taperDouble() = monkey("taperDouble", taperKinds(TaperKind.OUTER_DOUBLE, TaperKind.INNER_DOUBLE), cfgShift = 2)

    /** 테이퍼 홈 · 사용자 예 · 직접 보정 · 오류 · 극단 값 상태 */
    @Test
    fun taperErrors() = monkey(
        "taperErrors",
        listOf(
            screen("테이퍼 예시 외경 홈 형상 1", Feature.TAPER) { TaperScreen(taperEx(TaperKind.GROOVE, false, 1)) } to mid,
            screen("테이퍼 예시 외경 홈 형상 2", Feature.TAPER) { TaperScreen(taperEx(TaperKind.GROOVE, false, 2)) } to shallow,
            screen("테이퍼 사용자 예(X축 30°)", Feature.TAPER) { TaperScreen(userAngleExample()) } to mid,
            screen("테이퍼 사용자 예(Z축 기준)", Feature.TAPER, { it.angleRef = AngleRef.Z }) { TaperScreen(userAngleExample()) } to shallow,
            screen("테이퍼 예시 직접 보정 · 뒤집음", Feature.TAPER, { it.compMode = CompMode.DIRECT }) {
                TaperScreen(taperEx(TaperKind.OUTER, false, 3) { toggleFlip() })
            } to mid,
            screen("테이퍼 오류: 다섯 값 모순", Feature.TAPER) { TaperScreen(taperEx(TaperKind.OUTER, false, 1) { setPoint("ang", "10") }) } to mid,
            screen("테이퍼 오류: 노즈R 50", Feature.TAPER) { TaperScreen(taperEx(TaperKind.OUTER, false, 2) { setShared("noseR", "50") }) } to mid,
            screen("테이퍼 오류: 모서리 R 100", Feature.TAPER) { TaperScreen(taperEx(TaperKind.OUTER_DOUBLE, false, 1) { setPoint("r2", "100") }) } to mid,
            screen("테이퍼 오류: 같은 점", Feature.TAPER) { TaperScreen(taperEx(TaperKind.INNER, false, 1) { setPoint("x2", "40"); setPoint("z2", "0") }) } to shallow,
            screen("테이퍼 극단: 12자리", Feature.TAPER) {
                TaperScreen(taperEx(TaperKind.OUTER, false, 1) { setPoint("x2", "999999999999"); setPoint("z2", "-999999999999"); setPoint("start", "999999999999") })
            } to mid,
            screen("테이퍼 극단: 아주 작은 수", Feature.TAPER) {
                TaperScreen(taperEx(TaperKind.OUTER, true, 4) { setShared("noseR", "0.00000000001"); setPoint("r1", "0.00000000001"); setPoint("x1", "0.0001") })
            } to mid,
            screen("테이퍼 극단: 0 · 음수", Feature.TAPER) {
                TaperScreen(taperEx(TaperKind.INNER_DOUBLE, true, 2) { setPoint("x1", "0"); setPoint("x2", "-5"); setShared("noseR", "0"); setShared("zClear", "-10") })
            } to shallow,
            screen("테이퍼 극단: 홈 폭 0 · 바닥 > 외경", Feature.TAPER) {
                TaperScreen(taperEx(TaperKind.GROOVE, false, 1) { setShared("width", "0"); setShared("bottom", "999999999999") })
            } to shallow,
            screen("테이퍼 극단: 각도 89.999 · 팁 0", Feature.TAPER) {
                TaperScreen(taperEx(TaperKind.OUTER, false, 5) { setPoint("z2", ""); setPoint("ang", "89.999"); tip = 0 })
            } to shallow,
        ),
        cfgShift = 1,
    )

    /** 라운드 외경 형상 1~6 · 3점호 · 오류 · 극단(처음 빈칸 화면부터 깊게) */
    @Test
    fun round() = monkey(
        "round",
        buildList {
            add(app("라운드 처음(빈칸)", Feature.ROUND) to deep.copy(seconds = 240))
            add(screen("라운드 빈 입력 형상 1", Feature.ROUND) { RoundScreen(RoundState(1, open = true)) } to shallow)
            for (t in 1..6) add(screen("라운드 예시 외경 형상 $t", Feature.ROUND) { RoundScreen(roundExample(t, open = true)) } to (if (t == 3) mid else shallow))
            add(screen("라운드 예시 3점호", Feature.ROUND) { RoundScreen(roundExample(MANUAL, open = true)) } to mid)
            add(screen("라운드 오류: 주 R 0 · 모서리 R 큼", Feature.ROUND) { RoundScreen(roundExample(2, open = true).apply { setDim(2, "mainR", "0"); setDim(2, "cr1", "50") }) } to shallow)
            add(
                screen("라운드 극단: 12자리 · 아주 작은 노즈", Feature.ROUND) {
                    RoundScreen(roundExample(5, open = true).apply { setDim(5, "d2", "999999999999"); setShared("noseR", "0.00000000001") })
                } to shallow,
            )
            add(screen("라운드 극단: 3점 한 줄", Feature.ROUND) { RoundScreen(roundExample(MANUAL, open = true).apply { setPoint("x2", "40"); setPoint("z2", "-5") }) } to shallow)
        },
        cfgShift = 2,
    )

    /** 라운드 내경 형상 1~6 · 보링바 카드 · 프리셋(회사 사용 줄 · 바 값 없는 슬리브 줄 포함) · 안전 끝점 카드 · 3점호 내경 */
    @Test
    fun roundInner() = monkey(
        "roundInner",
        buildList {
            for (t in 1..6) add(screen("라운드 예시 내경 형상 $t", Feature.ROUND) { RoundScreen(roundInnerExample(t)) } to (if (t == 5) mid else shallow))
            add(screen("라운드 내경 관통 계단", Feature.ROUND) { RoundScreen(roundInnerStep()) } to shallow)
            add(screen("라운드 보링바 A 돔", Feature.ROUND) { RoundScreen(roundInnerDomeA()) } to mid.copy(seconds = 80))
            add(screen("라운드 보링바 C 막힘", Feature.ROUND) { RoundScreen(roundInnerBlindC()) } to mid)
            add(screen("라운드 보링바 F 소켓", Feature.ROUND) { RoundScreen(roundInnerSocketF()) } to shallow)
            add(screen("라운드 3점호 내경 입구", Feature.ROUND) { RoundScreen(roundManualInnerMouth()) } to shallow)
            add(screen("라운드 3점호 뒤보링", Feature.ROUND) { RoundScreen(roundManualBackBoring()) } to mid)
            add(screen("라운드 3점호 내경 역", Feature.ROUND) { RoundScreen(roundManualInnerRev()) } to shallow)
            // 회사 사용 프리셋(10-29): 고른 줄의 출처 두 줄 · 바 값이 없어 빈칸이 된 카드(바 없음 안내)에서 프리셋 시트를 다시 연다
            add(screen("라운드 보링바 회사 프리셋(대구텍 C16R)", Feature.ROUND) { RoundScreen(roundInnerExample(3).apply { pickPreset(HOLDER_PRESETS.single { it.code == "C16R SCLCR 09" }) }) } to mid)
            add(screen("라운드 보링바 빈 프리셋(슬리브)", Feature.ROUND) { RoundScreen(roundInnerExample(5).apply { pickPreset(HOLDER_PRESETS.single { it.code == "SH0516-100" }) }) } to mid)
        },
        cfgShift = 3,
    )

    /** 직각삼각형(칸 · 그림 누르기 · 밑변 축 · 지우기) */
    @Test
    fun triangle() = monkey(
        "triangle",
        listOf(
            app("직각삼각형 처음(빈칸)", Feature.TRIANGLE) to deep.copy(seconds = 120),
            screen("직각삼각형 예시 a30 · A25", Feature.TRIANGLE) { TriangleScreen(TriangleState(mapOf("a" to "30", "angA" to "25"))) } to mid,
            screen("직각삼각형 밑변 X", Feature.TRIANGLE) { TriangleScreen(TriangleState(mapOf("c" to "50", "b" to "20"), baseIsZ = false)) } to shallow,
            screen("직각삼각형 오류: c < a", Feature.TRIANGLE) { TriangleScreen(TriangleState(mapOf("a" to "50", "c" to "30"))) } to mid,
            screen("직각삼각형 극단", Feature.TRIANGLE) { TriangleScreen(TriangleState(mapOf("a" to "999999999999", "angA" to "89.999"))) } to shallow,
        ),
        cfgShift = 1,
    )

    /** 나사 G76(규격 · 급수 · 방향 · 고급 설정 접이 · 분석표) */
    @Test
    fun thread() = monkey(
        "thread",
        listOf(
            app("나사 처음(빈칸)", Feature.THREAD) to deep.copy(seconds = 240),
            screen("나사 원본 08", Feature.THREAD) { ThreadScreen(threadOriginal()) } to mid,
            screen("나사 내경 M10 고급 펼침", Feature.THREAD) { ThreadScreen(threadMetricInner()) } to mid,
            screen("나사 공차 식", Feature.THREAD) { ThreadScreen(threadFormula()) } to shallow,
            screen("나사 경고 둘", Feature.THREAD) { ThreadScreen(threadWarn()) } to shallow,
            screen("나사 오류: Z 순서", Feature.THREAD) { ThreadScreen(threadZOrder()) } to shallow,
            screen("나사 역가공", Feature.THREAD) { ThreadScreen(threadReverse()) } to shallow,
            screen("나사 값 모자람 + 범위 밖", Feature.THREAD) { ThreadScreen(threadBadWhileWaiting()) } to shallow,
            screen("나사 극단", Feature.THREAD) {
                ThreadScreen(
                    ThreadState(
                        spec = ThreadTable.pitches(ThreadKind.METRIC, "M10").first(), material = IsoGroup.S, dir = ThreadDir.REVERSE,
                        values = mapOf("zStart" to "999999999999", "zEnd" to "-999999999999", "firstCut" to "0.00000000001", "rpm" to "0"), advOpen = true,
                    ),
                )
            } to mid,
        ),
        cfgShift = 3,
    )

    /** 턴밀 극좌표(도형 · 외측/내측 · 반경/직경 · 시뮬레이션 모달) */
    @Test
    fun polar() = monkey(
        "polar",
        listOf(
            app("극좌표 처음(빈칸)", Feature.POLAR) to deep.copy(seconds = 180),
            screen("극좌표 원본 12", Feature.POLAR) { PolarScreen(polarOriginal12()) } to mid.copy(seconds = 90),
            // 시뮬레이션 모달을 연 채로 — 속도 막대(pointerInput 뿐, 의미 트리에 없음) · 경로 그림은 손가락 몽키만 닿는다
            screen("극좌표 시뮬레이션 열림", Feature.POLAR) { PolarScreen(polarOriginal12(), sim = SimStart(-1, 0.0)) } to Plan(depth = 2, seconds = 30, touches = 150),
            screen("극좌표 원본 07", Feature.POLAR) { PolarScreen(polarOriginal07()) } to shallow,
            screen("극좌표 내측 사각", Feature.POLAR) { PolarScreen(polarInnerSquare()) } to shallow,
            screen("극좌표 내측 작은 R", Feature.POLAR) { PolarScreen(polarInnerSmallR()) } to shallow,
            screen("극좌표 2면폭", Feature.POLAR) { PolarScreen(polarTwoFlat()) } to shallow,
            screen("극좌표 삼각 반경 지령", Feature.POLAR) { PolarScreen(polarTriangle()) } to mid,
            screen("극좌표 오류: W ≥ 외경", Feature.POLAR) { PolarScreen(polarWgeOd()) } to shallow,
            screen("극좌표 값 모자람 + 범위 밖", Feature.POLAR) { PolarScreen(polarBadWhileWaiting()) } to shallow,
            screen("극좌표 극단", Feature.POLAR) {
                PolarScreen(
                    PolarState(
                        shape = PolarShape.SQUARE,
                        values = mapOf("od" to "999999999999", "w" to "0.00000000001", "rc" to "0", "tool" to "0", "depth" to "-999999999999", "feed" to "0.00000000001"),
                    ),
                )
            } to mid,
        ),
    )

    /** 재료·절삭조건: 기본계산기 · 재료분석 탭(ISO 군 고르기) */
    @Test
    fun cutting() = monkey(
        "cutting",
        listOf(
            app("절삭 처음(빈칸)", Feature.CUTTING) to deep.copy(seconds = 200),
            screen("절삭 손 계산", Feature.CUTTING) { CuttingScreen(cutHand()) } to mid,
            screen("절삭 회전수에서", Feature.CUTTING) { CuttingScreen(cutFromN()) } to shallow,
            screen("절삭 G96 막힘", Feature.CUTTING) { CuttingScreen(cutG96()) } to shallow,
            screen("절삭 오류: 지름 0", Feature.CUTTING) { CuttingScreen(cutBad()) } to shallow,
            screen("절삭 이송 큼", Feature.CUTTING) { CuttingScreen(cutFeedTooBig()) } to shallow,
            screen("절삭 재료분석 탭", Feature.CUTTING) { CuttingScreen(CuttingState(tab = CutTab.MATERIAL)) } to mid,
            screen("절삭 극단", Feature.CUTTING) {
                CuttingScreen(CuttingState(values = mapOf("d" to "999999999999", "vc" to "0.00000000001", "f" to "-5", "len" to "0", "noseR" to "999999999999", "smax" to "0")))
            } to mid,
        ),
        cfgShift = 2,
    )

    /** 재료·절삭조건 드릴링 탭: 카탈로그 메이커 · 종류 · 행 · ISO 군 고르기, 직접 입력 */
    @Test
    fun drill() = monkey(
        "drill",
        listOf(
            screen("드릴 처음", Feature.CUTTING) { CuttingScreen(drillEmpty()) } to mid.copy(seconds = 100),
            screen("드릴 ISCAR P 군", Feature.CUTTING) { CuttingScreen(drillIscarRows()) } to mid,
            screen("드릴 Sandvik 손 계산", Feature.CUTTING) { CuttingScreen(drillSandvik()) } to shallow,
            screen("드릴 Mitsubishi", Feature.CUTTING) { CuttingScreen(drillMitsubishi()) } to shallow,
            screen("드릴 행 범위 밖", Feature.CUTTING) { CuttingScreen(drillOutsideRow()) } to shallow,
            screen("드릴 이송 표 사이", Feature.CUTTING) { CuttingScreen(drillBetween()) } to shallow,
            screen("드릴 시리즈 범위 밖", Feature.CUTTING) { CuttingScreen(drillOutsideSeries()) } to shallow,
            screen("드릴 이송 없음", Feature.CUTTING) { CuttingScreen(drillNoFeed()) } to shallow,
            screen("드릴 Vc 없음", Feature.CUTTING) { CuttingScreen(drillNoVc()) } to shallow,
            screen("드릴 직접 입력", Feature.CUTTING) { CuttingScreen(drillManual()) } to shallow,
            screen("드릴 직접 입력 잘못", Feature.CUTTING) { CuttingScreen(drillManualBad()) } to shallow,
        ),
        cfgShift = 3,
    )

    // ───────────────────────── 시험 틀 자체 점검

    /**
     * 함정 화면: 원래 꺼짐(시트 안 '고르기' 가 빈 값으로 그림을 던지게 함)과 같은 꼴 셋 — 시트를 열고 '고르기' 를 누르면 그리기에서,
     * '터짐' 은 누르는 자리에서, '효과' 는 LaunchedEffect 에서 예외. 시트 밖 '아무것' 은 아무 일 없다
     */
    @Composable
    private fun Trap() {
        var sheet by remember { mutableStateOf(false) }
        var boom by remember { mutableStateOf(false) }
        var effect by remember { mutableStateOf(false) }
        Column(Modifier.padding(24.dp)) {
            Text("함정 화면")
            Text("공구 설정", Modifier.padding(8.dp).clickable { sheet = true })
            Text("아무것", Modifier.padding(8.dp).clickable { })
        }
        if (sheet) {
            BottomSheet(onDismiss = { sheet = false }) {
                Text("고르기", Modifier.padding(8.dp).clickable { boom = true })
                Text("터짐", Modifier.padding(8.dp).clickable { error("누르기 함정") })
                Text("효과", Modifier.padding(8.dp).clickable { effect = true })
                PillButton("닫기", null, { sheet = false })
            }
        }
        if (boom) error("그리기 함정: tipVector(-1)")
        if (effect) LaunchedEffect(Unit) { error("효과 함정") }
    }

    /** 함정 화면에서 넓이 우선 누르기가 세 예외를 모두 잡고, 다시 밟아 재현하고, 경로를 '공구 설정 → 고르기' 처럼 두 걸음으로 줄이는가 */
    @Test
    fun selfCheck() {
        testName = "selfCheck"
        val st = Start("함정", {}) { s -> AppHost(s) { Trap() } }
        try {
            guarded(st, CFGS[0], "넓이 우선") { bfs(st, CFGS[0], Plan(depth = 3, seconds = (30 / SCALE).toInt()), Random(1)) }
            verify(listOf(st))
        } finally {
            disposePool()
        }
        val got = findings.filter { it.error != null }.associateBy { it.where }
        val text = findings.joinToString("\n\n") { describe(it) }
        assertEquals(setOf("누르기", "그리기", "효과"), got.keys, text)
        assertTrue(got.values.all { it.reproduced == true && !it.harness }, text)
        val want = mapOf("누르기" to "터짐", "그리기" to "고르기", "효과" to "효과")
        for ((where, last) in want) {
            val p = got.getValue(where).shortPath!!
            assertEquals(2, p.size, "$where: $p")
            assertTrue(p[0].toString().contains("공구 설정") && p[1].toString().contains(last), "$where: $p")
        }
    }

    private companion object {
        /**
         * 시간 · 손가락 횟수 배율. 처음 값 0.05 는 jvmTest 전체에 끼어 도는 짧은 점검(시험 12개 합쳐 몇 분)이고, 제대로 돌릴 때는 환경 변수로 1
         * (시험 하나 15분 안, 12개 합쳐 1시간 남짓). 환경 변수는 Gradle 이 입력으로 보지 않으니 --rerun 을 붙인다:
         * `cmd /c "set BMT_MONKEY=1&& gwh.bat :shared:jvmTest --tests com.bmt.nosecalc.UiMonkeyTest.taperBlank --rerun"`
         */
        val SCALE: Double = System.getenv("BMT_MONKEY")?.toDoubleOrNull() ?: 0.05

        val CFGS = listOf(Cfg(412, 1f, false), Cfg(360, 1.3f, true), Cfg(412, 1.3f, true), Cfg(360, 1f, false))
        const val HEIGHT_DP = 915
        val SURFACE: java.lang.reflect.Field = ImageComposeScene::class.java.getDeclaredField("surface").apply { isAccessible = true }
        const val DENSITY = Shots.DENSITY
        const val FRAME_NS = 100_000_000L
        const val STALL_MS = 90_000L
        const val BACK = "(뒤로)"
        const val OPEN_PAD = "(열린 키패드)"
        const val RESTORED = "(저장 → 복원)"

        /** 그 경로의 끝(누른 뒤 더 들어가지 않는다) */
        val TERMINAL = setOf("닫기", "뒤로", "취소", "확인", "")
        val CLOSERS = setOf("확인", "취소", "비우기 · 자동 계산")
        val ODD = listOf("NaN", "Infinity", "null")

        fun terminal(label: String) = label in TERMINAL || label.startsWith("닫기") || Feature.entries.any { label.startsWith(it.menuTitle) }

        fun short(s: String) = s.replace('\n', ' ').let { if (it.length > 48) it.take(47) + "…" else it }

        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

        fun labelOf(n: SemanticsNode): String {
            val c = n.config
            val parts = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            return parts.joinToString(" ").replace('\n', ' ').trim()
        }

        /** 깊은 곳의 같은 꼴 가리기: 숫자 → # */
        fun norm(s: String) = s.replace(Regex("-?[0-9]+(\\.[0-9]+)?"), "#")

        fun tempStore() = File.createTempFile("bmt-monkey", ".properties").apply { delete(); deleteOnExit() }

        /**
         * 안드로이드 SaveableStateRegistry 의 canBeSaved(DisposableSaveableStateRegistry.canBeSavedToBundle) 흉내: 스냅숏 상태는 값으로,
         * 함수는 안 되고, Serializable · String 만(이 앱은 Parcelable 을 안 쓴다)
         */
        fun bundleable(v: Any): Boolean = when (v) {
            is SnapshotMutableState<*> ->
                (v.policy === neverEqualPolicy<Any?>() || v.policy === structuralEqualityPolicy<Any?>() || v.policy === referentialEqualityPolicy<Any?>()) &&
                    (v.value?.let(::bundleable) ?: true)
            is Function<*> -> false
            is String, is java.io.Serializable -> true
            // 게으른 목록의 처음 열쇠 — 안드로이드에서는 Parcelable(DefaultLazyKey), PC 판은 그냥 클래스라 이름으로 받는다
            else -> v.javaClass.name == LAZY_KEY
        }

        /**
         * 안드로이드는 스냅숏 상태(ParcelableSnapshotMutableState)와 게으른 목록 열쇠(DefaultLazyKey)를 Parcelable 로 Bundle 에 넣는다 —
         * PC 판의 같은 클래스는 Serializable 이 아니라 직렬화 전에 상자에 담고 읽은 뒤 되돌린다. 나머지는 그대로 자바 직렬화
         */
        private class StateBox(val value: Any?, val policy: Int) : java.io.Serializable
        private class LazyKeyBox(val index: Int) : java.io.Serializable

        fun toBundle(v: Any?): Any? = when {
            v is SnapshotMutableState<*> -> StateBox(
                toBundle(v.value),
                when (v.policy) {
                    neverEqualPolicy<Any?>() -> 0
                    referentialEqualityPolicy<Any?>() -> 1
                    else -> 2
                },
            )
            v is Map<*, *> -> HashMap(v.entries.associate { (k, x) -> toBundle(k) to toBundle(x) })
            v is List<*> -> ArrayList(v.map(::toBundle))
            v != null && v.javaClass.name == LAZY_KEY -> LazyKeyBox(v.javaClass.getDeclaredMethod("getIndex").invoke(v) as Int)
            else -> v
        }

        fun fromBundle(v: Any?): Any? = when (v) {
            is StateBox -> mutableStateOf(
                fromBundle(v.value),
                when (v.policy) {
                    0 -> neverEqualPolicy()
                    1 -> referentialEqualityPolicy()
                    else -> structuralEqualityPolicy()
                },
            )
            is Map<*, *> -> v.entries.associate { (k, x) -> fromBundle(k) to fromBundle(x) }
            is List<*> -> v.map(::fromBundle)
            is LazyKeyBox -> Class.forName(LAZY_KEY).getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(v.index)
            else -> v
        }

        const val LAZY_KEY = "androidx.compose.foundation.lazy.layout.DefaultLazyKey"

        /** 키패드 키: 대개 무작위, 가끔 극단 값 꼴(0 · 12자리 넘게 · '−' 뿐 · '.' 뿐 · 아주 작은 수 · 음수). 마지막은 닫기 */
        fun randomKeys(rnd: Random, blankable: Boolean): List<String> {
            val digits = (0..9).map { "$it" }
            val body: List<String> = when (rnd.nextInt(10)) {
                0 -> listOf("0")
                1 -> List(14) { "9" }
                2 -> listOf("−")
                3 -> listOf(".")
                4 -> listOf("0", ".", "0", "0", "0", "0", "1")
                5 -> listOf("−") + List(1 + rnd.nextInt(4)) { digits.random(rnd) }
                6 -> listOf("C")
                else -> List(1 + rnd.nextInt(7)) {
                    when (rnd.nextInt(12)) {
                        0 -> "."
                        1 -> "−"
                        2 -> "지우기"
                        3 -> "C"
                        else -> digits.random(rnd)
                    }
                }
            }
            val close = when (rnd.nextInt(10)) {
                0 -> "취소"
                1 -> "뒤로"
                2 -> if (blankable) "비우기 · 자동 계산" else "확인"
                else -> "확인"
            }
            return body + close
        }

        /** 맨 위 10줄 + 앱(com.bmt) 줄 8개까지 */
        fun stackOf(t: Throwable): List<StackTraceElement> {
            val st = t.stackTrace.toList()
            return (st.take(10) + st.drop(10).filter { it.className.startsWith("com.bmt.") }.take(8)).distinct()
        }

        fun signatureOf(t: Throwable): String {
            val root = generateSequence(t) { it.cause }.last()
            val frame = root.stackTrace.firstOrNull { it.className.startsWith("com.bmt.") } ?: root.stackTrace.firstOrNull()
            return "${root.javaClass.name}:${norm(root.message.orEmpty().lineSequence().firstOrNull().orEmpty()).take(120)}@$frame"
        }

        /** 화면을 구성하다 난 예외인가(Recomposer · 구성 단계가 스택에 있다) */
        fun composing(t: Throwable): Boolean = generateSequence(t) { it.cause }.any { e ->
            e.stackTrace.any { it.className.startsWith("androidx.compose.runtime.Recomposer") || it.className == "androidx.compose.runtime.RecomposeScopeImpl" }
        }

        /** 헤드리스 장면에서만 나는 경합의 꼴(stage4-report — UiQueue 로 막았지만 혹시) */
        fun harnessLike(t: Throwable): Boolean {
            val all = generateSequence(t) { it.cause }.toList()
            return all.any { e ->
                "performMeasureAndLayout called during measure layout" in (e.message ?: "") ||
                    // PC 의 네이티브 메모리가 모자라 장면 그림판을 못 만듦(같은 PC 의 다른 일 탓일 수도) — 앱과 상관없다
                    "Failed Surface.makeRaster" in (e.message ?: "") ||
                    e.stackTrace.any { it.methodName == "addPendingInvalidationsLocked" }
            }
        }
    }
}
