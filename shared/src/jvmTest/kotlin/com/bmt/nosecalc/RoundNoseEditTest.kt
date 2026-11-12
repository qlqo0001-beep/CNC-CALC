package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.blankable
import com.bmt.nosecalc.screens.round.dim
import com.bmt.nosecalc.screens.round.dims
import com.bmt.nosecalc.screens.round.plain
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundManualInnerMouth
import com.bmt.nosecalc.screens.round.roundManualInnerRev
import com.bmt.nosecalc.screens.round.withBar
import java.io.File
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 2026-11-10 피드백 1 "라운드 r 계산에서 주r, d2, d1, l1을 입력해서 자동계산이 된 상태에서 노즈r 변경 시도시 팅기는 버그":
 * 실제 화면(ImageComposeScene)에서 그 조작을 그대로 한다 — 형상 5 에 주 R · D2 · D1 · L1 을 넣어 L2 가 자동으로 채워진 화면(Z 여유는 처음 값 = 빈칸)에서
 * 노즈 R '직접' 칩으로 키패드를 열고 값을 친다. 키패드의 첫 키(0 · . · ⌫)가 칸 값을 0 으로 만드는데(`NumEdit.live`), 그때 그리던 중 계산기가
 * 예외(IllegalArgumentException: 숫자가 아니다: NaN — RoundCalc.machineFrame)를 내 앱이 꺼졌다. 예시 상태(`roundExample`)는 Z 여유가 1.0 이라
 * 다른 화면 시험은 이 길을 지나지 않았다.
 * 그 밖에 형상 1~6 × 외·내경 × 비운 칸 × 보정 탭 × 노즈R 글(빈칸 · 0 · 큰 값 · 음수 …)과 3점호를 그리기까지 훑는다(예외 0)
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class RoundNoseEditTest {
    /** 화면 하나(밀도 1, 폭 412 — 결과 · 형상 뷰어까지 보이는 높이). 상태는 밖에서 바꾸고 frames 로 다시 그린다 */
    private class Screen(height: Int = 4200, content: @Composable () -> Unit) : AutoCloseable {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply { setTheme(false) }
        private val scene: ImageComposeScene
        private var t = 0L

        /**
         * 다시 그리는 중(재구성)에 난 예외. 장면은 그것을 render 밖으로 던지지 않고 코루틴 예외 처리기로 넘긴다 — 처리기가 없으면 표준 오류에만 찍히고
         * 시험은 지나간다(앱에서는 꺼진다)
         */
        private val thrown: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

        init {
            Snapshot.sendApplyNotifications()
            scene = ImageComposeScene(412, height, Density(1f), coroutineContext = CoroutineExceptionHandler { _, e -> thrown += e }) { AppHost(app) { content() } }
            frames(8)
        }

        fun frames(n: Int = 3) = repeat(n) {
            Snapshot.sendApplyNotifications()
            scene.render(t).close()
            t += 100_000_000L
            if (thrown.isNotEmpty()) throw AssertionError("화면을 그리다 예외(앱이 꺼진다): ${thrown[0]}", thrown[0])
        }

        private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

        /** 화면 글(낱말 이음 U+2060 은 지우고 붙임 공백은 빈칸으로 읽는다) */
        fun texts(): List<String> = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace("⁠", "").replace(' ', ' ') } }

        /** 그 글의 누를 수 있는 것(하나뿐이어야 한다)을 누른다 */
        fun tap(text: String) {
            val hit = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { n ->
                n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            hit.single().config[SemanticsActions.OnClick].action!!.invoke()
            frames(4)
        }

        /** PC 키보드 */
        fun key(k: Key) {
            app.handleKey(KeyEvent(k, KeyEventType.KeyDown))
            frames(3)
        }

        val keypadOpen: Boolean get() = app.overlays.isNotEmpty()

        override fun close() = scene.close()
    }

    /** 사용자가 말한 상태: 형상 5 에 주 R · D2 · D1 · L1 만(L2 는 비워 자동 계산). 나머지는 화면 처음 값 — 노즈 R 0.4, Z 여유 빈칸 */
    private fun reported(inner: Boolean = false) = RoundState(type = 5, open = true).apply {
        typeInner = inner
        mapOf("mainR" to "30", "d2" to "50", "d1" to "30", "l1" to "10").forEach { (k, v) -> setDim(5, k, v) }
    }

    /** 풀린 화면인가 — 보정 방식 스위치는 풀이가 있을 때만 그린다 */
    private fun solvedShown(s: Screen) = "기계 보정" in s.texts() && "직접 보정" in s.texts()

    /** 키패드를 열고 0 . 8 을 친다(화면 키 · PC 키보드) — 치는 동안 노즈 R 은 0 → 0 → 0.8. 외·내경, 보정 탭 둘 다 */
    @Test
    fun typingNoseOnAutoSolvedShape5() {
        for (inner in listOf(false, true)) for (mode in CompMode.entries) for (hardware in listOf(false, true)) {
            val st = reported(inner)
            Screen { RoundScreen(st) }.use { s ->
                val tag = "내경 $inner · $mode · PC 키 $hardware"
                s.app.compMode = mode
                s.frames()
                // 자동 계산된 상태: L2 = √(30² − 15²) − √(30² − 25²) = 9.398
                assertTrue("9.398" in s.texts(), "$tag: L2 자동 값이 없다")
                assertTrue(solvedShown(s), "$tag: 처음 화면에 NC 가 없다")
                s.tap("직접")
                assertTrue(s.keypadOpen, "$tag: 키패드가 안 열렸다")
                if (hardware) s.key(Key.Zero) else s.tap("0")
                assertEquals("0", st.shared("noseR"), tag)
                // 노즈 R 0 + Z 여유 빈칸: 진입 거리가 없다 — 꺼지지 않고 빨간 안내
                assertTrue(s.texts().any { "노즈R 이 0 이면 Z 여유를 넣으세요" in it }, "$tag: 노즈 R 0 안내가 없다")
                if (hardware) s.key(Key.Period) else s.tap(".")
                assertEquals("0", st.shared("noseR"), tag)
                if (hardware) s.key(Key.Eight) else s.tap("8")
                assertEquals("0.8", st.shared("noseR"), tag)
                if (hardware) s.key(Key.Enter) else s.tap("확인")
                assertFalse(s.keypadOpen, tag)
                assertTrue(solvedShown(s), "$tag: 0.8 을 넣은 뒤 NC 가 없다")
                assertTrue("9.398" in s.texts(), tag)
            }
        }
    }

    /**
     * Z 여유를 넣었으면 노즈 R 0 도 풀린다(진입 거리 = Z 여유) — 치는 동안 결과가 그대로 있고, 노즈 원 반지름 0 으로 형상 · NC · (내경) 보링바 검사까지 그린다
     */
    @Test
    fun typingZeroNoseWithZClearKeepsResult() {
        for (inner in listOf(false, true)) for (mode in CompMode.entries) {
            val st = reported(inner).apply { setShared("zClear", "1") }
            if (inner) st.withBar("8", "4.5", "D", "7.75", "93")
            Screen { RoundScreen(st) }.use { s ->
                val tag = "내경 $inner · $mode"
                s.app.compMode = mode
                s.frames()
                s.tap("직접")
                s.tap("0")
                assertEquals("0", st.shared("noseR"), tag)
                assertTrue(solvedShown(s), "$tag: 노즈 R 0 · Z 여유 1 인데 결과가 없다")
                assertFalse(s.texts().any { "입력이 잘못됐습니다" in it }, tag)
                s.tap(".")
                s.tap("4")
                s.tap("확인")
                assertEquals("0.4", st.shared("noseR"), tag)
                assertTrue(solvedShown(s), tag)
            }
        }
    }

    /** ⌫ 로 고치기(0.4 → "0." → "0" → 빈칸 = 연 때 값) · C · 취소 */
    @Test
    fun backspacingNoseOnAutoSolvedShape5() {
        val st = reported()
        Screen { RoundScreen(st) }.use { s ->
            s.tap("직접")
            s.key(Key.Backspace)
            assertEquals("0", st.shared("noseR"))
            s.key(Key.Backspace)
            assertEquals("0", st.shared("noseR"))
            // 다 지우면 연 때 값(노즈 R 은 비울 수 없다)
            s.key(Key.Backspace)
            assertEquals("0.4", st.shared("noseR"))
            s.key(Key.Five)
            assertEquals("5", st.shared("noseR"))
            s.key(Key.Delete)
            assertEquals("0.4", st.shared("noseR"))
            s.key(Key.Zero)
            assertEquals("0", st.shared("noseR"))
            s.tap("취소")
            assertEquals("0.4", st.shared("noseR"))
            assertTrue(solvedShown(s))
        }
    }

    /** 노즈R 칸에 들어갈 수 있는 글(키패드 · 되살린 상태) */
    private val noseTexts = listOf("0", "0.", ".", "", "-", "0.05", "0.8", "5", "30", "400", "999999999999", "-0.4", "0.4")

    /** 예시 치수를 풀어 모든 칸을 채운 뒤 blank 칸만 비운 상태(자동 계산). blank = null 이면 예시 그대로 */
    private fun shape(type: Int, inner: Boolean, blank: String?): RoundState = roundExample(type, open = true).apply {
        typeInner = inner
        setShared("zClear", "")
        if (blank != null) {
            val filled = RoundCalc.calc(input(type)).solution!!.filled!!
            for (d in dims(type)) if (!d.key.startsWith("cr")) setDim(type, d.key, if (d.key == blank) "" else plain(filled.dim(d.key)!!))
        }
        if (inner) setShared("leg", "10")
    }

    private fun sweep(tag: String, st: RoundState) {
        Screen { RoundScreen(st) }.use { s ->
            // Z 여유 빈칸(처음 값 — 노즈R 0 이면 진입 거리가 없다)에서 모든 글, 그다음 Z 여유 1 에서 노즈R 0(풀린다 — 노즈 원 반지름 0) · 큰 값
            for ((zClear, texts) in listOf("" to noseTexts, "1" to listOf("0", "400", "0.4"))) {
                st.setShared("zClear", zClear)
                for (t in texts) for (mode in CompMode.entries) {
                    st.setShared("noseR", t)
                    s.app.compMode = mode
                    try {
                        s.frames(1)
                    } catch (e: Throwable) {
                        fail("$tag · Z 여유 \"$zClear\" · 노즈R \"$t\" · $mode 에서 예외: ${e.stackTraceToString().lineSequence().take(12).joinToString("\n")}")
                    }
                }
                // 끝은 처음 값(0.4) — 다시 풀려 있어야 한다
                assertTrue(solvedShown(s), "$tag · Z 여유 \"$zClear\": 노즈 R 0.4 로 되돌린 뒤 결과가 없다")
            }
        }
    }

    /** 형상 1~6 × 외·내경 × 비운 칸(자동 계산) × 보정 탭 × 노즈R 글 — 그리기까지 예외 0. 내경의 절반은 보링바 칸을 채워 간섭 검사까지 */
    @Test
    fun noseTextsDrawOnEveryShape() {
        var n = 0
        for (type in 1..6) for (inner in listOf(false, true)) {
            if (inner && type == 1) continue
            for (blank in blankable(type).ifEmpty { listOf(null) }) {
                val st = shape(type, inner, blank)
                if (inner && n++ % 2 == 0) st.withBar("8", "4.5", "D", "7.75", "93")
                if (inner && type >= 5) st.through = n % 3 == 0
                sweep("형상 $type · 내경 $inner · 비운 칸 $blank", st)
            }
        }
    }

    /** 3점호(외경 · 내경 입구 R · 내경 역방향) × 보정 탭 × 노즈R 글 */
    @Test
    fun noseTextsDrawOnArc3() {
        sweep("3점호 외경", roundExample(MANUAL, open = true).apply { setShared("zClear", "") })
        sweep("3점호 내경", roundManualInnerMouth().apply { setShared("zClear", "") }.withBar("8", "4.5", "D", "7.75", "93"))
        sweep("3점호 내경 역방향", roundManualInnerRev().apply { setShared("zClear", "") })
    }

    /**
     * 가공 방향을 바꾼 화면(2026-11-10 피드백 2)도 같은 훑기: 형상 1~6 × 외·내경(막힘 · 관통) · 3점호 × 보정 탭 × 노즈R 글 — 그리기까지 예외 0.
     * 내경은 보링바 칸을 채워 '검사 안 함' 안내 길까지
     */
    @Test
    fun noseTextsDrawWhenFlipped() {
        for (type in 1..6) for (inner in listOf(false, true)) {
            if (inner && type == 1) continue
            for (through in if (inner && type >= 5) listOf(false, true) else listOf(false)) {
                val st = shape(type, inner, blankable(type).firstOrNull())
                if (inner) st.withBar("8", "4.5", "D", "7.75", "93")
                st.through = through
                st.setFlipped(type, true)
                sweep("바꿈 · 형상 $type · 내경 $inner · 관통 $through", st)
            }
        }
        sweep("바꿈 · 3점호 외경", roundExample(MANUAL, open = true).apply { setShared("zClear", ""); manualFlip = true })
        sweep("바꿈 · 3점호 내경", roundManualInnerMouth().apply { setShared("zClear", ""); manualFlip = true }.withBar("8", "4.5", "D", "7.75", "93"))
        sweep("바꿈 · 3점호 내경 역방향", roundManualInnerRev().apply { setShared("zClear", ""); manualFlip = true })
    }
}
