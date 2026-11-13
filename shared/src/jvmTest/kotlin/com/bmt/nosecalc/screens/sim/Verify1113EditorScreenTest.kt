@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bmt.nosecalc.screens.sim

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
import com.bmt.nosecalc.screens.SimulatorScreen
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-11-13 사용자 답 "그 줄을 다 입력할 때까지 에러 표기하지 않음" — 설계 21.6) — 앱의 `SimulatorScreen()` 그대로.
 * 구현자의 시험과 따로 쓴 검사다. 키패드 키 · 단추를 의미 노드로 누르고 화면 글(문제 띠의 수 · 첫 줄, 줄 아래 글, 궤적 창의 멈춤 띠)만 본다. 글은 새로 쓴 것.
 */
class Verify1113EditorScreenTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-v1113s", ".properties").apply { delete() })

    private inner class Stage(draft: String, cursor: Int) {
        private val kv = store().also {
            it.put(ProgramStore.DRAFT, draft)
            it.put(ProgramStore.DRAFT_CURSOR, cursor.toString())
        }
        val app = AppState(kv).also { it.open(Feature.SIMULATOR) }
        private val ui = UiQueue()
        private val scene = ImageComposeScene(412, 915, Density(1f), coroutineContext = ui) {
            AppHost(app) { SimulatorScreen() }
        }
        private var clock = 0L

        fun frame() {
            clock += 16_000_000L
            ui.drain()
            scene.render(clock).close()
        }

        /** 실제 시간 ms 만큼 프레임을 돌린다 */
        fun idle(ms: Long) {
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                Thread.sleep(20)
                frame()
            }
        }

        fun await(what: String, cond: () -> Boolean) {
            val until = System.currentTimeMillis() + 15_000
            while (!cond()) {
                assertTrue(System.currentTimeMillis() < until, "기다려도 안 됨: $what — 화면 글 ${texts()}")
                Thread.sleep(20)
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

        /** 그 글이 든 화면 글의 수(문제 띠 첫 줄 · 줄 아래 글 · 목록이 따로 센다) */
        fun count(part: String) = texts().count { part in it }

        /** 문제 띠의 수 글('오류 1 · 확인 1' 꼴) — 띠가 없으면 null */
        fun strip(): String? = texts().firstOrNull { STRIP.matches(it) }

        /** 그 글(읽어 주는 글)을 가진 누를 수 있는 것을 누른다 */
        fun tap(label: String) {
            val node = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && n.words().any { it == label } }
            requireNotNull(node) { "누를 것이 없다: $label — 화면 글 ${texts()}" }.config[SemanticsActions.OnClick].action?.invoke()
            frame()
        }

        /** 키패드로 한 글자씩(빈칸은 SP) */
        fun type(s: String) = s.forEach { ch -> tap(if (ch == ' ') "빈칸" else ch.toString()) }

        fun close() = scene.close()
    }

    private fun stage(draft: String, cursor: Int = draft.length, block: Stage.() -> Unit) {
        val s = Stage(draft, cursor)
        try {
            repeat(5) { s.frame() }
            s.block()
        } finally {
            s.close()
        }
    }

    /**
     * 마지막 줄(줄바꿈 뒤 빈 줄): 소수점 없는 X(확인) + 글 끝 안내가 있는 글에서 `M30` 을 치는 동안 띠의 수가 '확인 1' 이고(안내만 빠짐) 첫 줄은 5줄 확인,
     * ⏎ 직후 · 새 결과 뒤에도 '확인 1'. `M3` 이면 새 결과 뒤 '확인 1 · 안내 1'
     */
    @Test
    fun lastLineCountsWhileWritingAndAfter() {
        stage(WARN_TEXT) {
            await("첫 실행") { strip() == "확인 1 · 안내 1" }
            for (k in "M30") {
                type(k.toString())
                idle(400)
                assertEquals("확인 1", strip(), "'$k' 뒤 — ${texts()}")
                assertFalse(shows(NO_END), "'$k' 뒤 — ${texts()}")
                assertTrue(shows("5줄: "), "띠 첫 줄은 5줄 확인: ${texts()}")
            }
            tap("새 줄")
            repeat(3) {
                frame()
                assertEquals("확인 1", strip(), "⏎ 직후 — ${texts()}")
            }
            idle(1000)
            assertEquals("확인 1", strip(), "M30 으로 끝난 뒤 — ${texts()}")
        }
        stage(WARN_TEXT) {
            await("첫 실행") { strip() == "확인 1 · 안내 1" }
            type("M3")
            idle(400)
            assertEquals("확인 1", strip(), "${texts()}")
            tap("새 줄")
            frame()
            assertEquals("확인 1", strip(), "⏎ 직후(새 결과 전) — ${texts()}")
            await("새 결과 뒤 7줄 안내") { strip() == "확인 1 · 안내 1" }
        }
    }

    /** 가운데 줄(3줄)을 고치는 동안은 글 끝 안내가 그 줄 때문이 아니라 그대로 보인다 — 띠 '확인 1 · 안내 1', 끝내도 그대로 */
    @Test
    fun middleLineKeepsTheEndNotice() {
        val at = WARN_TEXT.indexOf("G97 S1000 M03") + "G97 S1000 M03".length
        stage(WARN_TEXT, at) {
            await("첫 실행") { strip() == "확인 1 · 안내 1" }
            type(" ")
            idle(500)
            assertEquals("확인 1 · 안내 1", strip(), "가운데 줄을 쓰는 중 — ${texts()}")
            tap("새 줄")
            idle(1000)
            assertEquals("확인 1 · 안내 1", strip(), "끝낸 뒤(가운데에 빈 줄이 하나 늘었다) — ${texts()}")
        }
    }

    /** 글 끝 뒤가 빈 줄 · 빈칸뿐: 빈 꼬리의 첫 줄에서도, 빈 줄 사이 가운데 줄에서도 마지막 줄로 본다. 뒤에 주석이 있으면 아니다 */
    @Test
    fun blankTailIsTheEnd() {
        val tail = WARN_TEXT + "\n  \n\n"
        stage(tail, WARN_TEXT.length) {
            await("첫 실행") { strip() == "확인 1 · 안내 1" }
            type("M30")
            idle(400)
            assertEquals("확인 1", strip(), "빈 꼬리의 첫 줄 — ${texts()}")
            tap("새 줄")
            idle(1000)
            assertEquals("확인 1", strip(), "끝낸 뒤 — ${texts()}")
        }
        stage(tail, WARN_TEXT.length + 1) {
            await("첫 실행") { strip() == "확인 1 · 안내 1" }
            type("M30")
            idle(400)
            assertEquals("확인 1", strip(), "빈 줄 사이 — ${texts()}")
            tap("새 줄")
            idle(1000)
            assertEquals("확인 1", strip(), "끝낸 뒤 — ${texts()}")
        }
        stage(WARN_TEXT + "\n(END)", WARN_TEXT.length) {
            await("첫 실행") { strip() == "확인 1 · 안내 1" }
            type("M30")
            idle(400)
            assertEquals("확인 1 · 안내 1", strip(), "뒤에 주석 줄 — 마지막 줄이 아니다: ${texts()}")
        }
    }

    /** 한 줄짜리 글: 빈 편집기에 첫 블록 → ⏎ 뒤 글 끝 안내 → 둘째 줄에 M30 을 치는 동안 띠 없음 → ⏎ 뒤에도 없음. 줄바꿈 없는 한 줄 'M3' 에 0 을 덧붙여도 같다 */
    @Test
    fun oneLineProgram() {
        stage("") {
            type("G0X40.Z2.")
            idle(500)
            assertEquals(null, strip(), "첫 줄을 쓰는 중 — ${texts()}")
            tap("새 줄")
            await("1줄 안내") { strip() == "안내 1" }
            assertTrue(shows("1줄: $NO_END"), "${texts()}")
            type("M30")
            idle(400)
            assertEquals(null, strip(), "둘째 줄(글 끝)을 쓰는 중 — ${texts()}")
            tap("새 줄")
            frame()
            assertEquals(null, strip(), "⏎ 직후 — ${texts()}")
            idle(1000)
            assertEquals(null, strip(), "M30 뒤 — ${texts()}")
        }
        stage("G0X40.Z2.\nM3") {
            await("첫 실행") { shows(NO_END) }
            type("0")
            idle(400)
            assertFalse(shows(NO_END), "${texts()}")
            tap("새 줄")
            idle(1000)
            assertFalse(shows(NO_END), "M30 뒤 — ${texts()}")
        }
    }

    /**
     * 글 끝 `,C` 블록(다음 블록 없음 — 멈춤 오류)을 가리는 동안 끝 줄에 G00 을 쓰다 ▶ 궤적: 궤적 창이 7줄에서 멈췄다고 알린다(조용히 돌지 않는다).
     * [편집기로] 돌아오면 띠 '오류 1' 과 멈춘 줄 아래 글. 바르게(Z-10.) 끝내고 ▶ 하면 멈춤 띠가 없다
     */
    @Test
    fun hiddenStopStillStopsTheTrajectory() {
        val text = HEAD + "G00 X36. Z2.\nG01 Z0. F0.2\nX40.,C1.\n"
        val msg = "다음 블록이 G01~G03 이동이 아닙니다"
        stage(text) {
            await("첫 실행") { strip()?.startsWith("오류 1") == true }
            type("G00")
            idle(400)
            assertEquals(null, strip(), "끝 줄을 쓰는 중 — ${texts()}")
            assertEquals(0, count(msg), "끝 줄을 쓰는 중 — 멈춘 줄 아래 글도 없다: ${texts()}")
            tap("궤적 확인")
            await("궤적 창의 멈춤 띠") { shows("7줄에서 멈춤") }
            assertTrue(shows(msg), "${texts()}")
            tap("편집기로")
            await("편집기의 문제 띠") { strip()?.startsWith("오류 1") == true }
            assertTrue(count(msg) >= 1, "${texts()}")
        }
        stage(text) {
            await("첫 실행") { strip()?.startsWith("오류 1") == true }
            type("Z-10.")
            tap("궤적 확인")
            idle(800)
            assertFalse(shows("에서 멈춤"), "바르게 끝냈는데 멈춤 띠: ${texts()}")
        }
    }

    /**
     * 끝 줄을 쓰는 동안에도 이미 끝낸 가운데 줄의 멈춤 오류는 보여야 한다: 7줄 `,C` 블록 다음 8줄이 G00(다 쓴 줄)이라 붙은 '다음 블록이 G01~G03 이동이 아님'.
     * 쓰는 줄(9줄)로는 고칠 수 없는 오류다(사용자 답은 "그 줄" — 21.1 다른 줄의 문제는 그대로).
     * [2026-11-13 검증에서 찾음] 코드(CORNER_NO_NEXT)만으로 가려 띠 · 줄 아래 글 · 목록 머리에서 사라진다(설계 21.6 '알고 둔 것') — 고침 단계 판정 대기
     */
    @Test
    fun finishedMiddleStopStaysWhileWritingTheLastLine() {
        val text = HEAD + "G00 X36. Z2.\nG01 Z0. F0.2\nX40.,C1.\nG00 X50.\n"
        stage(text) {
            await("첫 실행") { strip()?.startsWith("오류 1") == true }
            assertTrue(shows("7줄: ,C1."), "${texts()}")
            type("M")
            idle(400)
            assertEquals("오류 1", strip()?.substringBefore(" · 안내"), "끝 줄에 M 하나 — 7줄 멈춤 오류가 숨었다: ${texts()}")
        }
    }

    private companion object {
        const val HEAD = "O0001\nT0101\nG97 S1000 M03\nG99\n"

        /** 5줄 X40(소수점 없음 — 확인) · 6줄 뒤 글 끝(M30 없음 — 안내). 끝은 빈 줄 */
        const val WARN_TEXT = HEAD + "G00 X40 Z2.\nG01 Z-10. F0.2\n"
        const val NO_END = "M30 / M02 없이 글이 끝납니다"
        val STRIP = Regex("^(오류|확인|안내) \\d+( · (오류|확인|안내) \\d+)*$")
    }
}
