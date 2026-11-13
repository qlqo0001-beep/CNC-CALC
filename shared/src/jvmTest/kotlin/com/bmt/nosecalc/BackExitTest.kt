@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package com.bmt.nosecalc

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
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
import com.bmt.nosecalc.screens.sim.UiQueue
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.util.Collections
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 사용자 보고(2026-11-13, 안드로이드 폰): "앱에서 여러번 작업시 메인화면에서 여러번 뒤로가기를 눌러야 앱이 꺼지는 문제".
 * 앱 안의 뒤로가 새는 길이 있는지 진짜 [App] 을 폰 크기(412 × 915dp) 장면에 띄워 몰아붙인다:
 * 메뉴로 모든 화면을 여러 차례 오가고(같은 화면 다시 열기 포함), 헤더 ←, 테이퍼 · 라운드 형상 고르기 → 입력(B),
 * 숫자 키패드 · 바텀시트 · 모달 · 도움말 · 메뉴를 열고 닫기(닫기 단추 · 바깥 누르기 · 끌어내리기 · 뒤로 키 각각),
 * 시뮬레이터 키패드 · 찾기 · 궤적 창 · 공구 설정 시트, 시트가 열린 채로 다른 화면 열기, 씨앗 고정 무작위 수백 단계.
 *
 * 뒤로 한 번마다 본다: back() 결과 = 누르기 전 canGoBack(안드로이드 BackHandler 가 켜진 채인데 처리 못 하는 일이 없게),
 * true 면 보이는 것(의미 트리의 글 · 스택 · 위에 뜬 층 수) 가운데 하나는 바뀐다(보이지 않는 일만 하고 true 면 그 한 번이 '헛뒤로'),
 * false 면 아무 것도 안 바뀐다. 메인(테이퍼)에 오면: 뜬 층 · 메뉴 · 입력(B) 수만큼 뒤로가 처리되고 그다음 한 번은 false ·
 * canGoBack false, 그때 state.overlays 는 비고 state.backs 는 테이퍼의 단계 하나(꺼짐)만 남는다.
 */
class BackExitTest {
    private class Clip : ClipboardManager {
        var held: AnnotatedString? = null
        override fun getText(): AnnotatedString? = held
        override fun setText(annotatedString: AnnotatedString) {
            held = annotatedString
        }
    }

    /** 닫는 법 */
    private enum class Close { BUTTON, OUTSIDE, DRAG, BACK }

    private class Stage(val name: String, val w: Int = 412, val h: Int = 915) : AutoCloseable {
        val state = AppState(KeyValueStore(File.createTempFile("bmt-back", ".properties").apply { delete(); deleteOnExit() }))
        private val ui = UiQueue()
        private val bg: MutableList<Throwable> = Collections.synchronizedList(ArrayList())
        private val clip = Clip()
        private val scene: ImageComposeScene
        private var clock = 0L
        private var pointerMs = 0L
        val path = ArrayList<String>()
        val leaks = ArrayList<String>()
        val stats = LinkedHashMap<String, Int>()

        init {
            Snapshot.sendApplyNotifications()
            scene = ImageComposeScene(w, h, Density(1f), coroutineContext = ui + CoroutineExceptionHandler { _, e -> bg += e }) {
                CompositionLocalProvider(LocalClipboardManager provides clip) { App(state) }
            }
            // 글꼴 리소스는 비동기로 읽힌다 — 처음 몇 프레임은 실제 시간을 조금 흘린다
            repeat(6) {
                Thread.sleep(5)
                frames(1)
            }
            frames(STEP)
        }

        fun bump(what: String) {
            stats.merge(what, 1, Int::plus)
        }

        fun tail(n: Int = 30) = path.takeLast(n).joinToString(" → ")

        fun leak(what: String) {
            bump("새는 길")
            if (leaks.size < 20) leaks += "[$name] $what — 스택 ${state.stack}, 층 ${state.overlays.size}, 경로 끝: ${tail()}"
        }

        fun expect(ok: Boolean, what: () -> String) {
            if (!ok) leak(what())
        }

        fun frames(n: Int) = repeat(n) {
            clock += FRAME_NS
            ui.drain()
            Snapshot.sendApplyNotifications()
            scene.render(clock).close()
            ui.drain()
            if (bg.isNotEmpty()) throw AssertionError("[$name] 효과(코루틴)에서 예외 — 경로 끝: ${tail()}", bg.removeAt(0))
        }

        // ───────────────────────── 의미 트리

        private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        private fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        private fun clickable(n: SemanticsNode) = n.config.getOrNull(SemanticsActions.OnClick) != null && SemanticsProperties.Disabled !in n.config

        fun labelOf(n: SemanticsNode): String {
            val c = n.config
            val parts = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            return parts.joinToString(" ").replace('\n', ' ').replace("⁠", "").trim()
        }

        /** 덮개(글 없이 누를 수 있고 화면 절반 넘게 덮는 것 — 시트 · 모달 · 메뉴 뒤) 자리 */
        private fun scrims(ns: List<SemanticsNode>): List<Int> = ns.indices.filter { i ->
            val b = ns[i].boundsInRoot
            clickable(ns[i]) && labelOf(ns[i]).isEmpty() && b.width * b.height >= w * h * 0.5f
        }

        /** 누를 수 있는 것. 덮개 아래(덮개보다 먼저 그려지고 가운데가 덮개 안)는 뺀다 — 손으로 못 누른다 */
        fun cands(): List<SemanticsNode> {
            val ns = nodes()
            val sc = scrims(ns)
            return ns.filterIndexed { i, n ->
                clickable(n) && sc.none { j -> j > i && (n.boundsInRoot.isEmpty || n.boundsInRoot.center in ns[j].boundsInRoot) }
            }
        }

        fun labels(): List<String> = cands().map(::labelOf)

        fun can(label: String) = labels().any { it == label }

        /** 보이는 것: 의미 트리의 모든 글 · 설명 · 선택 · 켬 + 스택 + 위에 뜬 층 수(토스트는 뺀다) */
        private fun look(): String {
            state.toast?.let(state::clearToast)
            frames(1)
            val t = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.flatMap { n ->
                val c = n.config
                c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    listOfNotNull(
                        c.getOrNull(SemanticsProperties.Selected)?.let { "선택=$it" },
                        c.getOrNull(SemanticsProperties.ToggleableState)?.let { "켬=$it" },
                        c.getOrNull(SemanticsProperties.StateDescription),
                    )
            }
            return (t + "스택=${state.stack}" + "층=${state.overlays.size}").joinToString("|")
        }

        private fun click(n: SemanticsNode) {
            n.config[SemanticsActions.OnClick].action?.invoke()
            Snapshot.sendApplyNotifications()
        }

        // ───────────────────────── 누르기

        /** 글이 맞는 맨 위(나중 노드) 것을 누른다. 없으면 false */
        fun tap(label: String, match: (String) -> Boolean = { it == label }): Boolean {
            val n = cands().lastOrNull { match(labelOf(it)) } ?: return false
            path += "누름[$label]"
            click(n)
            frames(STEP)
            return true
        }

        fun mustTap(label: String, match: (String) -> Boolean = { it == label }) {
            assertTrue(tap(label, match), "[$name] 누를 것이 없음: $label — 보이는 것 ${labels()} — 경로 끝: ${tail()}")
        }

        /** 뒤로 키(안드로이드 뒤로 · PC Esc 와 같은 길) */
        fun back(how: String = "뒤로"): Boolean {
            frames(4)
            val can = state.canGoBack
            val before = look()
            val r = state.back()
            Snapshot.sendApplyNotifications()
            path += "$how(${if (r) "처리" else "없음"})"
            frames(AFTER)
            val after = look()
            bump(if (r) "뒤로 처리" else "뒤로 없음(앱 닫힘)")
            if (r != can) leak("canGoBack=$can 인데 back()=$r")
            if (r && before == after) leak("back() 이 true 인데 보이는 것이 그대로 — 헛뒤로")
            if (!r && before != after) leak("back() 이 false 인데 화면이 바뀜")
            return r
        }

        /** 헤더 ← (있을 때만). 누르면 보이는 것이 바뀌어야 한다 */
        fun headerBack(): Boolean {
            val n = cands().lastOrNull { labelOf(it) == "뒤로" } ?: return false
            frames(4)
            val before = look()
            path += "헤더 ←"
            click(n)
            frames(AFTER)
            bump("헤더 ←")
            if (before == look()) leak("헤더 ← 를 눌렀는데 보이는 것이 그대로")
            return true
        }

        /** 바깥 누르기 = 맨 위 덮개 */
        fun tapOutside(): Boolean {
            val ns = nodes()
            val i = scrims(ns).lastOrNull() ?: return false
            path += "바깥 누름"
            click(ns[i])
            frames(STEP)
            bump("바깥 누름")
            return true
        }

        private fun pointer(type: PointerEventType, at: Offset, pressed: Boolean) {
            pointerMs = maxOf(pointerMs + 16, clock / 1_000_000)
            scene.sendPointerEvent(type, listOf(ComposeScenePointer(PointerId(0), at, pressed, PointerType.Touch)), timeMillis = pointerMs)
            Snapshot.sendApplyNotifications()
        }

        /** 진짜 손가락으로 누르기 */
        fun touch(x: Float, y: Float) {
            pointer(PointerEventType.Press, Offset(x, y), true)
            pointer(PointerEventType.Release, Offset(x, y), false)
            frames(STEP)
        }

        fun drag(x0: Float, y0: Float, x1: Float, y1: Float) {
            pointer(PointerEventType.Press, Offset(x0, y0), true)
            for (i in 1..10) {
                val t = i / 10f
                pointer(PointerEventType.Move, Offset(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t), true)
                if (i % 3 == 0) frames(1)
            }
            pointer(PointerEventType.Release, Offset(x1, y1), false)
            frames(STEP)
        }

        /**
         * 맨 위 바텀시트의 손잡이 띠(시트 맨 위 24dp)를 끌어내린다. 손잡이에는 의미 노드가 없어 맨 위 덮개 뒤 노드(시트 안)의 가장 높은 자리
         * 바로 위를 잡는다. 모달 · 덮개 없는 온 화면 층(궤적 창 · 크게 보기)은 끌어내리기가 없다. 닫혔으면 true
         */
        fun dragSheetDown(): Boolean {
            val top = state.overlays.lastOrNull() ?: return false
            if (top.blurBehind) return false
            val ns = nodes()
            val i = scrims(ns).lastOrNull() ?: return false
            val minTop = ns.drop(i + 1).map { it.boundsInRoot }.filter { !it.isEmpty && it.top > 0f && it.top < h }.minOfOrNull { it.top } ?: return false
            val n0 = state.overlays.size
            path += "끌어내림"
            // 창 밖으로 나간 손가락 이벤트는 장면이 받지 않는다 — 낮은 시트(공구 설정 빈 표)는 창 바닥 바로 위까지만 끈다
            drag(w / 2f, minTop - 10f, w / 2f, minOf(minTop + 250f, h - 2f))
            val closed = state.overlays.size < n0
            bump(if (closed) "끌어내려 닫힘" else "끌어내렸는데 안 닫힘")
            return closed
        }

        /** 숫자 키패드가 떠 있나(키패드의 확인 · 취소 · 지우기) */
        fun padOpen(): Boolean {
            if (state.overlays.isEmpty()) return false
            val l = labels()
            return "확인" in l && "취소" in l && "지우기" in l
        }

        /** 숫자칸(52dp 높이 칸)들 */
        private fun fieldBoxes(): List<SemanticsNode> = cands().filter {
            val b = it.boundsInRoot
            b.height in 50f..54f && b.width >= 40f && b.center.y in 0f..h.toFloat()
        }

        /** label 칸(칸 이름 글 다음의 첫 누를 것)을 눌러 키패드를 연다 */
        fun openField(label: String): Boolean {
            val ns = nodes()
            val i = ns.indexOfFirst { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
            if (i < 0) return false
            val box = ns.drop(i + 1).firstOrNull(::clickable) ?: return false
            path += "칸[$label]"
            click(box)
            frames(STEP)
            return padOpen()
        }

        /** 아무 숫자칸 하나를 진짜 손가락으로 */
        fun openAnyField(rnd: Random): Boolean {
            val fs = fieldBoxes()
            if (fs.isEmpty()) return false
            val b = fs[rnd.nextInt(fs.size)].boundsInRoot
            path += "칸(${b.center.x.toInt()},${b.center.y.toInt()})"
            touch(b.center.x, b.center.y)
            bump(if (padOpen()) "키패드 열림" else "칸 눌렀는데 키패드 없음")
            return padOpen()
        }

        /** 아무 누를 것 하나를 진짜 손가락으로(덮개 없는 온 화면 층 아래 것은 층이 받는다 — 손과 같다) */
        fun randomTouch(rnd: Random) {
            val cs = cands().filter { val c = it.boundsInRoot.center; c.x in 0f..w.toFloat() && c.y in 0f..h.toFloat() }
            if (cs.isEmpty()) return
            val n = cs[rnd.nextInt(cs.size)]
            val c = n.boundsInRoot.center
            path += "터치[${labelOf(n).take(24)}]"
            touch(c.x, c.y)
        }

        /** 메뉴 → f 카드. 메뉴 단추가 덮여 있으면(시트 · 모달 · 궤적 창) false */
        fun menuTo(f: Feature): Boolean {
            if (!state.menuOpen && !tap("메뉴")) return false
            val ok = tap(f.menuTitle) { it.startsWith(f.menuTitle) }
            if (ok) bump("메뉴로 화면")
            return ok
        }

        /** 바로 다른 화면(시트 · 키패드 · 모달이 열린 채로 떠나는 길) */
        fun openDirect(f: Feature) {
            path += "open($f) 바로(층 ${state.overlays.size})"
            state.open(f)
            Snapshot.sendApplyNotifications()
            frames(STEP)
            bump("바로 열기")
        }

        /** 열린 층 하나를 how 로 닫는다. button = 닫기 단추 글. 닫혔으면 true */
        fun closeTop(how: Close, button: String = "닫기"): Boolean {
            val n0 = state.overlays.size
            val m0 = state.menuOpen
            when (how) {
                Close.BUTTON -> mustTap(button)
                Close.OUTSIDE -> assertTrue(tapOutside(), "[$name] 덮개 없음 — 경로 끝: ${tail()}")
                Close.DRAG -> dragSheetDown()
                Close.BACK -> back()
            }
            return state.overlays.size < n0 || (m0 && !state.menuOpen)
        }

        // ───────────────────────── 메인(테이퍼) 확인

        /** 테이퍼 A 화면(형상 고르기)이 보이나 — 형상 2 카드 */
        fun pickVisible() = labels().any { it.startsWith("형상 2") }

        /**
         * 메인(테이퍼)에서: 보이는 것(뜬 층 · 메뉴 · 입력 B) 수만큼만 뒤로가 처리되고 그다음은 false · canGoBack false.
         * 그 뒤 state.overlays 비고, state.backs 는 테이퍼 단계 하나(꺼짐). 처리된 뒤로 수를 돌려준다
         */
        fun checkMain(why: String): Int {
            frames(AFTER)
            expect(state.stack == listOf(Feature.TAPER)) { "$why: 메인인데 스택 ${state.stack}" }
            val bOpen = state.hasBackStep
            val pick = pickVisible()
            if (state.overlays.isEmpty() && !state.menuOpen) {
                expect(bOpen != pick) { "$why: 테이퍼 단계 OnBack(켜짐=$bOpen) 과 보이는 화면(형상 고르기=$pick) 이 어긋남" }
            }
            val visible = state.overlays.size + (if (state.menuOpen) 1 else 0) + (if (bOpen) 1 else 0)
            var n = 0
            while (n <= 30 && back("메인 뒤로")) n++
            bump("메인에서 처리된 뒤로 $n 번")
            expect(n == visible) { "$why: 메인에서 뒤로 $n 번 처리 뒤 꺼짐 — 보이는 것은 $visible (층 · 메뉴 · 입력)" }
            expect(!state.canGoBack) { "$why: 끝까지 눌렀는데 canGoBack" }
            expect(state.overlays.isEmpty()) { "$why: 보이지 않는 overlays ${state.overlays.size}" }
            expect(state.backs.none { it.enabled.value }) { "$why: 켜진 OnBack ${state.backs.count { it.enabled.value }}" }
            expect(state.backs.size == 1) { "$why: state.backs ${state.backs.size} 개(테이퍼 단계 하나여야)" }
            expect(pickVisible()) { "$why: 끝까지 눌렀는데 형상 고르기가 안 보임" }
            return n
        }

        /** (가) 아무 것도 없으면 back() false · canGoBack false. (나) 입력(B)이 열려 있으면 한 번에 그것만 닫히고 다음은 false */
        fun checkAB(why: String) {
            checkMain("$why (가) 앞")
            expect(!state.canGoBack && !back("(가) 뒤로")) { "$why (가): 아무 것도 없는데 뒤로가 처리됨" }
            mustTap("형상 1") { it.startsWith("형상 1") }
            expect(state.hasBackStep && !pickVisible()) { "$why (나): 형상 1 을 눌렀는데 입력(B)이 아님" }
            expect(state.canGoBack) { "$why (나): 입력(B)인데 canGoBack false" }
            expect(back("(나) 뒤로 1")) { "$why (나): 첫 뒤로가 처리 안 됨" }
            expect(pickVisible() && !state.hasBackStep) { "$why (나): 첫 뒤로 뒤 형상 고르기가 아님" }
            expect(!back("(나) 뒤로 2")) { "$why (나): 두 번째 뒤로도 처리됨" }
            expect(!state.canGoBack) { "$why (나): 끝인데 canGoBack" }
        }

        /** 뒤로만 눌러 앱이 꺼질 때까지(모든 한 번이 보이는 것을 바꿔야 한다 — back 이 본다). 마지막은 메인 확인 */
        fun drainToExit(why: String) {
            var n = 0
            while (n < 60 && (state.stack.size > 1 || state.overlays.isNotEmpty() || state.menuOpen)) {
                if (!back("끝까지 뒤로")) break
                n++
            }
            expect(state.stack.size == 1) { "$why: 뒤로 $n 번 눌러도 메인에 못 감 ${state.stack}" }
            checkMain("$why (뒤로 $n 번으로 메인)")
        }

        override fun close() {
            runCatching { scene.close() }
        }
    }

    /** 정한 차례: 화면마다 여는 것 · 닫는 법을 하나씩 다 */
    @Test
    fun scriptedTour() {
        Stage("차례").use { s ->
            s.checkAB("처음")

            // ── 테이퍼 B: 숫자 키패드(확인 · 취소 · 바깥 · 끌어내림 · 뒤로)
            s.mustTap("형상 1") { it.startsWith("형상 1") }
            for ((how, button) in listOf(Close.BUTTON to "확인", Close.BUTTON to "취소", Close.OUTSIDE to "", Close.DRAG to "", Close.BACK to "")) {
                assertTrue(s.openField("X1"), "[차례] X1 칸 키패드가 안 열림 — ${s.labels()}")
                assertTrue(s.closeTop(how, button), "[차례] 키패드를 $how/$button 로 못 닫음 — 경로 끝: ${s.tail()}")
                assertTrue(!s.padOpen() && s.state.hasBackStep, "[차례] 키패드를 닫았는데 입력(B)도 닫힘 — ${s.tail()}")
            }
            // 팁 번호 시트(닫기 단추 없음): 바깥 · 끌어내림 · 뒤로
            for (how in listOf(Close.OUTSIDE, Close.DRAG, Close.BACK)) {
                s.mustTap("팁 번호 칸") { it.contains("바꾸기") }
                assertTrue(s.state.overlays.size == 1, "[차례] 팁 시트가 안 열림")
                assertTrue(s.closeTop(how), "[차례] 팁 시트를 $how 로 못 닫음")
            }
            // 도움말(닫기 · 바깥 · 끌어내림 · 뒤로)
            for (how in Close.entries) {
                s.mustTap("도움말")
                assertTrue(s.state.helpOpen && s.state.overlays.size == 1, "[차례] 도움말이 안 열림")
                assertTrue(s.closeTop(how), "[차례] 도움말을 $how 로 못 닫음 — ${s.tail()}")
                assertTrue(!s.state.helpOpen, "[차례] 도움말 상태가 남음")
            }
            // 메뉴(닫기 · 바깥 · 뒤로 · 메뉴 단추 · 지금 화면 카드)
            for (how in listOf(Close.BUTTON, Close.OUTSIDE, Close.BACK)) {
                s.mustTap("메뉴")
                assertTrue(s.state.menuOpen, "[차례] 메뉴가 안 열림")
                assertTrue(s.closeTop(how), "[차례] 메뉴를 $how 로 못 닫음 — ${s.tail()}")
            }
            s.mustTap("메뉴")
            s.mustTap("메뉴")
            assertTrue(!s.state.menuOpen, "[차례] 메뉴 단추로 안 닫힘")
            assertTrue(s.menuTo(Feature.TAPER) && !s.state.menuOpen && s.state.hasBackStep, "[차례] 지금 화면 카드 — 메뉴만 닫히고 입력(B)은 그대로여야")
            // 헤더 ← 로 B → A
            assertTrue(s.headerBack() && s.pickVisible(), "[차례] 헤더 ← 로 형상 고르기로 안 감")
            s.checkMain("테이퍼 한 바퀴 뒤")
            // B 의 '형상 다시 고르기' 로 A — 단계 OnBack 도 꺼져야(남으면 메인에서 헛뒤로)
            s.mustTap("형상 2") { it.startsWith("형상 2") }
            s.mustTap("형상 다시 고르기")
            assertTrue(s.pickVisible(), "[차례] '형상 다시 고르기' 로 A 가 안 보임")
            s.checkMain("'형상 다시 고르기' 뒤")

            // ── 라운드: 형상 1 → B → 키패드 연 채로 다른 화면(바로) → 다시 라운드(B 는 남고 키패드는 닫힘)
            assertTrue(s.menuTo(Feature.ROUND))
            s.mustTap("형상 1") { it.startsWith("형상 1") }
            assertTrue(s.state.hasBackStep, "[차례] 라운드 입력(B)이 안 열림")
            assertTrue(s.openAnyField(Random(1)), "[차례] 라운드 숫자칸 키패드가 안 열림")
            s.openDirect(Feature.TRIANGLE)
            assertTrue(s.state.overlays.isEmpty(), "[차례] 라운드를 떠났는데 키패드 층이 남음 ${s.state.overlays.size}")
            assertTrue(s.menuTo(Feature.ROUND) && s.state.stack == listOf(Feature.TAPER, Feature.ROUND), "[차례] 라운드 다시 열기 ${s.state.stack}")
            assertTrue(s.state.hasBackStep && s.state.overlays.isEmpty(), "[차례] 돌아온 라운드: 입력(B)은 남고 키패드는 닫혀야")
            assertTrue(s.headerBack() && s.pickVisible() && s.state.current == Feature.ROUND, "[차례] 라운드 헤더 ← 는 입력(B)만 닫아야")
            s.mustTap("형상 3") { it.startsWith("형상 3") }
            s.mustTap("다시 고르기")
            assertTrue(s.pickVisible() && !s.state.backs.any { it.enabled.value }, "[차례] 라운드 '다시 고르기' 뒤 켜진 OnBack 이 남음")
            // 같은 화면 다시 열기 여러 번
            repeat(3) { assertTrue(s.menuTo(Feature.ROUND)) }
            assertTrue(s.state.stack == listOf(Feature.TAPER, Feature.ROUND), "[차례] 같은 화면 다시 열기 ${s.state.stack}")

            // ── 시뮬레이터: 키패드(처음은 열림) · 찾기 · 궤적 창 · 공구 설정 시트 · 모달
            assertTrue(s.menuTo(Feature.SIMULATOR))
            assertTrue(s.can("키패드 닫기"), "[차례] 시뮬레이터 키패드가 처음에 안 열림 — ${s.labels()}")
            assertTrue(s.back() && s.can("키패드"), "[차례] 뒤로로 시뮬레이터 키패드가 안 닫힘")
            s.mustTap("키패드")
            s.mustTap("찾기")
            assertTrue(s.can("찾기 닫기"), "[차례] 찾기 줄이 안 열림")
            assertTrue(s.back() && s.can("키패드 닫기") && !s.can("찾기 닫기"), "[차례] 뒤로가 찾기만 닫아야")
            s.mustTap("찾기")
            s.mustTap("찾기")
            s.mustTap("키패드 닫기")
            s.mustTap("키패드")
            s.mustTap("궤적 확인") { it.startsWith("궤적 확인") }
            assertTrue(s.state.overlays.size == 1, "[차례] 궤적 창이 안 열림 — ${s.labels()}")
            for (how in Close.entries) {
                s.mustTap("공구 설정") { it == "공구 설정" || it == "공구" }
                assertTrue(s.state.overlays.size == 2, "[차례] 공구 설정 시트가 안 열림 — ${s.labels()}")
                assertTrue(s.closeTop(how), "[차례] 공구 설정 시트를 $how 로 못 닫음 — ${s.tail()}")
                assertTrue(s.state.overlays.size == 1, "[차례] 공구 시트를 닫았는데 궤적 창도 닫힘")
            }
            // 궤적 창 위 도움말 → 뒤로는 도움말만
            s.mustTap("도움말")
            assertTrue(s.state.overlays.size == 2 && s.state.helpOpen, "[차례] 궤적 창 도움말")
            assertTrue(s.back() && s.state.overlays.size == 1, "[차례] 뒤로가 도움말만 닫아야")
            assertTrue(s.back() && s.state.overlays.isEmpty(), "[차례] 뒤로로 궤적 창이 안 닫힘")
            // 저장(번호 없는 새 글) → 번호 모달: 닫기 · 바깥 · 뒤로
            for (how in listOf(Close.BUTTON, Close.OUTSIDE, Close.BACK)) {
                s.mustTap("저장")
                assertTrue(s.state.overlays.size == 1, "[차례] 저장 번호 모달이 안 열림 — ${s.labels()}")
                assertTrue(s.closeTop(how), "[차례] 모달을 $how 로 못 닫음")
            }
            // 궤적 창 + 공구 시트가 열린 채로 메인으로(바로)
            s.mustTap("궤적 확인") { it.startsWith("궤적 확인") }
            s.mustTap("공구 설정") { it == "공구 설정" || it == "공구" }
            assertTrue(s.state.overlays.size == 2)
            s.openDirect(Feature.TAPER)
            s.checkMain("궤적 창 · 공구 시트 연 채로 메인")

            // ── 모든 화면을 두 바퀴(같은 화면 다시 열기 · 도움말 · 메뉴 섞어) 뒤 메뉴로 메인
            repeat(2) {
                for (f in Feature.menu + Feature.menu.reversed()) {
                    assertTrue(s.menuTo(f), "[차례] 메뉴로 $f")
                    if (s.tap("도움말")) s.closeTop(Close.entries[(f.ordinal + it) % 4])
                }
            }
            assertTrue(s.menuTo(Feature.TAPER))
            s.checkAB("두 바퀴 뒤 메뉴로 메인")

            // 다시 여러 화면을 쌓고 뒤로만 눌러 끝까지
            for (f in listOf(Feature.ROUND, Feature.SIMULATOR, Feature.THREAD, Feature.POLAR)) assertTrue(s.menuTo(f))
            s.drainToExit("쌓은 뒤 뒤로만")
            s.checkAB("끝")

            println("[BackExit] 차례: ${s.stats}")
            assertTrue(s.leaks.isEmpty(), s.leaks.joinToString("\n"))
        }
    }

    /** 씨앗 고정 무작위: 수백 단계마다 메인으로 가서 본다(메뉴로 · 바로 · 뒤로만 돌아가며) */
    @Test
    fun randomWalk() {
        val leaks = ArrayList<String>()
        // 씨앗 · 창(dp): 보통 폰 둘 + 좁고 낮은 폰 하나
        for ((seed, size) in listOf(11 to (412 to 915), 23 to (412 to 915), 37 to (360 to 800))) {
            Stage("씨앗 $seed", size.first, size.second).use { s ->
                val rnd = Random(seed)
                var home = 0
                for (step in 1..STEPS) {
                    when (rnd.nextInt(100)) {
                        in 0..39 -> s.randomTouch(rnd)
                        in 40..49 -> s.back()
                        in 50..53 -> s.headerBack()
                        in 54..59 -> s.tapOutside()
                        in 60..64 -> s.dragSheetDown()
                        in 65..76 -> s.menuTo(Feature.menu[rnd.nextInt(Feature.menu.size)])
                        in 77..80 -> s.tap("메뉴")
                        in 81..85 -> s.tap("도움말")
                        in 86..92 -> s.openAnyField(rnd)
                        else -> s.openDirect(Feature.entries[rnd.nextInt(Feature.entries.size)])
                    }
                    if (step % 40 == 0) {
                        when (home++ % 3) {
                            0 -> {
                                // 사용자처럼: 뜬 층을 뒤로로 닫고 메뉴 → 테이퍼
                                var guard = 0
                                while ((s.state.overlays.isNotEmpty() || !s.can("메뉴") && !s.state.menuOpen) && guard++ < 20) s.back()
                                if (s.menuTo(Feature.TAPER)) s.checkMain("$step 걸음: 메뉴로 메인") else s.drainToExit("$step 걸음: 메뉴 못 눌러 뒤로만")
                            }
                            1 -> {
                                s.openDirect(Feature.TAPER)
                                s.checkMain("$step 걸음: 바로 메인")
                            }
                            else -> s.drainToExit("$step 걸음: 뒤로만")
                        }
                    }
                }
                s.drainToExit("끝: 뒤로만")
                s.checkAB("끝")
                println("[BackExit] 씨앗 $seed: ${s.stats}")
                leaks += s.leaks
            }
        }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
    }

    private companion object {
        const val FRAME_NS = 100_000_000L

        /** 누른 뒤 그리는 프레임(0.6초) */
        const val STEP = 6

        /** 뒤로 뒤 그리는 프레임(1.5초 — 메뉴 접힘 애니메이션까지 끝나게) */
        const val AFTER = 15

        val STEPS = System.getenv("BMT_BACK_STEPS")?.toIntOrNull() ?: 500
    }
}
