package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaverScope
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
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.blankable
import com.bmt.nosecalc.screens.round.withBar
import java.io.File
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 독립 검증(2026-11-10 피드백 1 · 2) — 실제 화면(ImageComposeScene)으로:
 * - 피드백 1: 형상 5 에 주 R · D2 · D1 · L1 을 넣어 자동 계산된 화면에서 노즈R 칸을 열고 고친다 — 외·내경 × 보정 탭 × 가공 방향(스위치를 눌러 바꾼 뒤에도). 예외 0
 * - 피드백 2 "값 계승": 화면의 '가공 방향 바꾸기' 스위치를 누른 앞뒤로 저장되는 모든 글(Saver)이 스위치 글 하나 빼고 같다. 외경 ⇄ 내경 · 형상 왕복 · 되살리기도
 * - 형상 1~6 × 외·내경 × 가공 방향 × 보정 탭 × 노즈R 글 × Z 여유 × 끝 직선 × 비운 칸 × 팁 × 보링바 — 그리기까지 예외 0
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class Verify1110RoundUiTest {
    private class Screen(height: Int = 4200, content: @Composable () -> Unit) : AutoCloseable {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply { setTheme(false) }
        private val scene: ImageComposeScene
        private var t = 0L
        private val thrown: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

        init {
            Snapshot.sendApplyNotifications()
            scene = ImageComposeScene(412, height, Density(1f), coroutineContext = CoroutineExceptionHandler { _, e -> thrown += e }) { AppHost(app) { content() } }
            frames(8)
        }

        /** 다시 그린다. 그리던 중 난 예외(앱에서는 꺼짐)는 여기서 던진다 */
        fun frames(n: Int = 3) = repeat(n) {
            Snapshot.sendApplyNotifications()
            scene.render(t).close()
            t += 100_000_000L
            if (thrown.isNotEmpty()) throw AssertionError("화면을 그리다 예외(앱이 꺼진다): ${thrown[0]}", thrown[0])
        }

        private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

        fun texts(): List<String> = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace("⁠", "").replace(' ', ' ') } }

        private fun clickables(text: String) = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }

        fun tap(text: String) {
            val hit = clickables(text)
            assertEquals(1, hit.size, "누를 것 '$text' 의 수")
            hit.single().config[SemanticsActions.OnClick].action!!.invoke()
            frames(4)
        }

        fun key(k: Key) {
            app.handleKey(KeyEvent(k, KeyEventType.KeyDown))
            frames(3)
        }

        val keypadOpen: Boolean get() = app.overlays.isNotEmpty()

        override fun close() = scene.close()
    }

    /** 화면 글 가운데 NC 줄 하나가 꼭 그 글인 것이 있나 */
    private fun hasLine(texts: List<String>, line: String) = texts.any { t -> t.lines().any { it.trim() == line } }

    /** 상태의 모든 글(Saver 가 내는 목록) */
    private fun stored(s: RoundState): Map<String, String> =
        (with(RoundState.Saver) { SaverScope { true }.save(s)!! } as List<*>).filterIsInstance<String>().drop(2).chunked(2).associate { it[0] to it[1] }

    private val dimsOf = mapOf(
        1 to mapOf("mainR" to "20", "cr1" to "1", "l1" to "20"),
        2 to mapOf("mainR" to "10", "cr1" to "1", "d1" to "12", "l1" to "10"),
        3 to mapOf("cr1" to "1", "mainR" to "12.5", "d1" to "20"),
        4 to mapOf("cr1" to "0.5", "mainR" to "10", "d1" to "12"),
        // 사용자가 말한 상태: 주 R · D2 · D1 · L1 (L2 는 자동)
        5 to mapOf("mainR" to "30", "d2" to "50", "d1" to "30", "l1" to "10"),
        6 to mapOf("mainR" to "10", "d2" to "30", "d1" to "12", "l1" to "5", "l2" to "6"),
    )

    private fun filled(type: Int, inner: Boolean) = RoundState(type = type, open = true).apply {
        typeInner = inner
        dimsOf.getValue(type).forEach { (k, v) -> setDim(type, k, v) }
    }

    private fun flipKey(type: Int, inner: Boolean) = "flip/t$type/${if (inner && type != 1) "in" else "out"}"

    /** 노즈R 칸을 열어 0 . 8 → 확인, 다시 열어 ⌫ 로 지우고 큰 값, 취소 */
    private fun editNose(s: Screen, st: RoundState, tag: String) {
        st.setShared("noseR", "0.4")
        s.frames()
        s.tap("직접")
        assertTrue(s.keypadOpen, "$tag: 키패드가 안 열렸다")
        s.tap("0")
        assertEquals("0", st.shared("noseR"), tag)
        s.tap(".")
        s.tap("8")
        assertEquals("0.8", st.shared("noseR"), tag)
        s.tap("확인")
        assertFalse(s.keypadOpen, tag)
        s.tap("직접")
        s.key(Key.Backspace)
        s.key(Key.Backspace)
        s.key(Key.Backspace)
        s.key(Key.Nine)
        s.key(Key.Nine)
        s.key(Key.Nine)
        assertEquals("999", st.shared("noseR"), tag)
        s.key(Key.Period)
        s.key(Key.Zero)
        s.key(Key.Five)
        s.tap("취소")
        assertEquals("0.8", st.shared("noseR"), tag)
        st.setShared("noseR", "0.4")
        s.frames()
    }

    /** 피드백 1 의 조작을 두 방향에서, 그리고 스위치를 눌러 바꾼 앞뒤의 값 계승 */
    @Test
    fun reportedFlowInBothDirections() {
        for (inner in listOf(false, true)) for (mode in CompMode.entries) {
            val st = filled(5, inner)
            val tag = "형상 5 · 내경 $inner · $mode"
            Screen { RoundScreen(st) }.use { s ->
                s.app.compMode = mode
                s.frames()
                assertTrue("9.398" in s.texts(), "$tag: L2 자동 값이 없다")
                editNose(s, st, "$tag · 기본 방향")
                val before = stored(st)
                assertTrue(s.texts().any { it == (if (inner) "입구 → 구멍 바닥 (Z−) · G41" else "앞 끝 → 척 쪽 (Z−) · G42") }, "$tag: 기본 방향 글 ${s.texts().filter { "G4" in it }}")
                if (mode == CompMode.MACHINE) assertTrue(hasLine(s.texts(), if (inner) "G41" else "G42"), "$tag: 기본 방향 NC 첫 줄")
                s.tap("가공 방향 바꾸기")
                assertTrue(st.flipped(5), "$tag: 스위치를 눌러도 안 바뀜")
                // 값 계승: 스위치 글 하나만 다르다
                assertEquals(before, stored(st) - flipKey(5, inner), "$tag: 스위치를 누른 뒤 다른 칸이 바뀜")
                assertTrue(s.texts().any { it == (if (inner) "구멍 바닥 → 입구 (Z+) · G42" else "척 쪽 끝 → 앞 끝 (Z+) · G41") }, "$tag: 바꾼 방향 글 ${s.texts().filter { "G4" in it }}")
                assertTrue("9.398" in s.texts(), "$tag: 바꾼 뒤 L2 자동 값이 없다")
                if (mode == CompMode.MACHINE) {
                    assertTrue(hasLine(s.texts(), if (inner) "G42" else "G41"), "$tag: 바꾼 방향 NC 첫 줄")
                    assertFalse(hasLine(s.texts(), if (inner) "G41" else "G42"), "$tag: 바꾼 뒤에도 옛 보정 G 가 NC 에 있다")
                }
                editNose(s, st, "$tag · 바꾼 방향")
                assertEquals(before, stored(st) - flipKey(5, inner), "$tag: 바꾼 방향에서 노즈R 을 고친 뒤")
                s.tap("가공 방향 바꾸기")
                assertFalse(st.flipped(5), tag)
                assertEquals(before, stored(st) - flipKey(5, inner), "$tag: 되돌린 뒤")
            }
        }
    }

    /** 외경 ⇄ 내경 · 형상 왕복 · 되살리기(Saver)에서 칸 값이 남는가 — 모든 형상의 칸 · 공구 · 보링바 · 3점호 칸을 채워 놓고 */
    @Test
    fun switchesKeepEveryValue() {
        val st = RoundState(type = 5, open = true)
        for ((t, d) in dimsOf) d.forEach { (k, v) -> st.setDim(t, k, v) }
        st.setShared("noseR", "0.8")
        st.setShared("zClear", "2")
        st.setShared("leg", "7")
        st.withBar("8", "4.5", "D", "7.75", "93")
        mapOf("x1" to "30", "z1" to "0", "x2" to "44.142", "z2" to "-2.929", "x3" to "50", "z3" to "-10").forEach { (k, v) -> st.setPoint(k, v) }
        st.tip = 3
        fun values() = stored(st).filterKeys { !it.startsWith("flip/") && it != "inner" && it != "m/flip" }
        val before = values()
        val outer = (1..6).associateWith { st.input(it) }
        // 외경에서 형상마다 바꾸기
        for (t in 1..6) {
            st.type = t
            st.setFlipped(t, true)
            assertEquals(outer.getValue(t).copy(flip = true), st.input(t), "형상 $t 외경 뒤집기")
        }
        assertEquals(before, values(), "외경 뒤집기 뒤")
        // 내경으로: 치수 · 공구 칸은 그대로(팁만 외·내경 따로), 외경에서 켠 스위치는 내경으로 안 넘어온다
        st.typeInner = true
        assertEquals(before, values(), "내경으로 바꾼 뒤")
        for (t in 2..6) {
            val want = outer.getValue(t).copy(inner = true, tip = null)
            assertEquals(want, st.input(t), "형상 $t 내경 — 외경 값 계승")
            st.setFlipped(t, true)
            assertEquals(want.copy(flip = true), st.input(t), "형상 $t 내경 뒤집기")
        }
        st.manualFlip = true
        assertEquals(before, values(), "내경 뒤집기 뒤")
        // 다시 외경: 처음 값 + 켜 둔 스위치
        st.typeInner = false
        for (t in 1..6) assertEquals(outer.getValue(t).copy(flip = true), st.input(t), "형상 $t 외경으로 돌아옴")
        // 되살리기
        val back = with(RoundState.Saver) { restore(SaverScope { true }.save(st)!!)!! }
        assertEquals(stored(st), stored(back))
        for (t in 1..6) assertEquals(st.input(t), back.input(t))
        assertEquals(st.arc3(), back.arc3())
        assertTrue(back.arc3().flip)
    }

    private val noseTexts = listOf("0", "", ".", "-", "0.05", "0.8", "5", "400", "0.4")

    /** 형상 1~6 × 외·내경 × 보정 탭마다 화면 하나를 띄우고 상태(가공 방향 · 관통 · 노즈R 글 · Z 여유 · 끝 직선 · 비운 칸 · 팁 · 보링바)를 바꿔 가며 다시 그린다 */
    @Test
    fun stateSweepRenders() {
        var drawn = 0
        for (inner in listOf(false, true)) for (type in (if (inner) 2..6 else 1..6)) for (mode in CompMode.entries) {
            val st = filled(type, inner)
            Screen { RoundScreen(st) }.use { s ->
                s.app.compMode = mode
                s.frames()
                fun draw(tag: String) {
                    try {
                        s.frames(3)
                        drawn++
                    } catch (e: AssertionError) {
                        throw AssertionError("형상 $type · 내경 $inner · $mode · $tag — ${stored(st)}: ${e.message}", e.cause)
                    }
                }
                for (flip in listOf(true, false)) {
                    st.setFlipped(type, flip)
                    for (through in (if (inner && type >= 5) listOf(false, true) else listOf(false))) {
                        st.through = through
                        for (zc in listOf("", "1")) for (leg in listOf("5.0", "0.3", "0")) {
                            st.setShared("zClear", zc)
                            st.setShared("leg", leg)
                            for (n in noseTexts) {
                                st.setShared("noseR", n)
                                draw("뒤집기 $flip 관통 $through Z여유 '$zc' 끝직선 '$leg' 노즈 '$n'")
                            }
                        }
                    }
                    st.setShared("zClear", "")
                    st.setShared("leg", "5.0")
                    st.setShared("noseR", "0.4")
                    // 비울 수 있는 칸을 하나씩 비우고(다른 칸은 채움), 다 비움
                    val full = dimsOf.getValue(type)
                    for (b in blankable(type)) {
                        // 자동 계산되는 칸을 채워 놓고 다른 칸을 비운다
                        val l2 = if (type == 5) "9.398" else if (type == 2) "16" else null
                        if (l2 != null) st.setDim(type, "l2", l2)
                        if (type == 3) st.setDim(type, "l1", "5")
                        if (type == 4) st.setDim(type, "l1", "18")
                        val keep = st.dim(type, b)
                        st.setDim(type, b, "")
                        draw("뒤집기 $flip · $b 비움")
                        st.setDim(type, b, "0")
                        draw("뒤집기 $flip · $b 0")
                        st.setDim(type, b, keep)
                    }
                    for (k in full.keys) st.setDim(type, k, "")
                    draw("뒤집기 $flip · 다 비움")
                    for ((k, v) in full) st.setDim(type, k, v)
                    if (type in 2..5) st.setDim(type, if (type == 3 || type == 4) "l1" else "l2", "")
                    // 팁 0~9
                    for (tip in 0..9) {
                        st.tip = tip
                        draw("뒤집기 $flip · 팁 $tip")
                    }
                    st.tip = null
                    if (inner) {
                        st.withBar("8", "4.5", "D", "7.75", "93")
                        for (n in listOf("0", "0.4", "5")) {
                            st.setShared("noseR", n)
                            draw("뒤집기 $flip · 보링바 · 노즈 '$n'")
                        }
                        st.setShared("noseR", "0.4")
                    }
                }
            }
        }
        println("Verify1110RoundUiTest.stateSweepRenders: 그린 상태 $drawn")
        assertTrue(drawn > 2000, "그린 상태 수 $drawn")
    }

    /** 3점호: 스위치를 눌러 바꾸고(값 계승), 노즈R 글 · 외·내경 · 역방향 점 순서로 그린다 */
    @Test
    fun manualFlipRenders() {
        val pointSets = listOf(
            mapOf("x1" to "30", "z1" to "0", "x2" to "44.142", "z2" to "-2.929", "x3" to "50", "z3" to "-10"),
            mapOf("x1" to "50", "z1" to "-10", "x2" to "44.142", "z2" to "-2.929", "x3" to "30", "z3" to "0"),
            mapOf("x1" to "30", "z1" to "0", "x2" to "22.929", "z2" to "-1.464", "x3" to "20", "z3" to "-5"),
            mapOf("x1" to "0", "z1" to "0", "x2" to "14.142", "z2" to "-2.929", "x3" to "20", "z3" to "-10"),
        )
        for (mode in CompMode.entries) {
            val st = RoundState(type = MANUAL, open = true)
            pointSets[0].forEach { (k, v) -> st.setPoint(k, v) }
            Screen { RoundScreen(st) }.use { s ->
                s.app.compMode = mode
                s.frames()
                val before = stored(st)
                assertTrue(s.texts().any { it == "P1 → P3 · G42" }, "$mode: 기본 글 ${s.texts().filter { "G4" in it }}")
                s.tap("가공 방향 바꾸기")
                assertTrue(st.manualFlip)
                assertEquals(before, stored(st) - "m/flip", "$mode: 3점호 스위치를 누른 뒤 다른 칸이 바뀜")
                assertTrue(s.texts().any { it == "P3 → P1 · G41" }, "$mode: 바꾼 글 ${s.texts().filter { "G4" in it }}")
                // 고침 단계(2026-11-11 — V2): 뒤집은 3점호의 진입이 'P3 너머에서 접선으로' 에서 'P3 너머로 가지 않고 P3 앞 · 공구 쪽에서' 로 바뀌어 안내 글도 바뀌었다
                assertTrue(s.texts().any { "P3 너머(척 · 구멍 안쪽)로 가지 않고" in it }, "$mode: 바꾼 방향 안내 글이 없다")
                if (mode == CompMode.MACHINE) assertTrue(hasLine(s.texts(), "G41"), "$mode: 바꾼 NC 첫 줄")
                for (inner in listOf(false, true)) for (flip in listOf(true, false)) for (p in pointSets) for (n in noseTexts) for (zc in listOf("", "1")) {
                    st.inner = inner
                    st.manualFlip = flip
                    p.forEach { (k, v) -> st.setPoint(k, v) }
                    st.setShared("noseR", n)
                    st.setShared("zClear", zc)
                    try {
                        s.frames(3)
                    } catch (e: AssertionError) {
                        throw AssertionError("3점호 · $mode · 내경 $inner · 뒤집기 $flip · $p · 노즈 '$n' · Z여유 '$zc': ${e.message}", e.cause)
                    }
                }
            }
        }
    }

    /** 내경 + 보링바 + 가공 방향 바꾸기: 검사를 안 한다는 안내가 뜨고(말없이 틀린 값 금지) 안전 끝점 · 잘린 NC 글이 남지 않는다. 끄면 검사가 돌아온다 */
    @Test
    fun flippedInnerWithBarExplains() {
        for (mode in CompMode.entries) {
            val st = filled(5, true).apply {
                setShared("zClear", "1")
                withBar("8", "4.5", "D", "7.75", "93")
            }
            Screen { RoundScreen(st) }.use { s ->
                s.app.compMode = mode
                s.frames()
                val base = s.texts()
                assertFalse(base.any { "가공 방향을 바꾼 프로그램은 보링바 간섭 검사" in it }, "$mode: 기본 방향인데 안내가 뜸")
                s.tap("가공 방향 바꾸기")
                val flipped = s.texts()
                assertTrue(flipped.any { "가공 방향을 바꾼 프로그램은 보링바 간섭 검사" in it }, "$mode: 바꾼 방향 보링바 안내가 없다")
                val leftovers = flipped.filter { ("안전 끝점" in it || "가공 가능" in it || "잘린" in it) && "하지 않습니다" !in it && "가공 방향 바꾸기" !in it }
                assertTrue(leftovers.isEmpty(), "$mode: 바꾼 방향인데 보링바 결과 글이 남음 $leftovers")
                s.tap("가공 방향 바꾸기")
                assertFalse(s.texts().any { "가공 방향을 바꾼 프로그램은 보링바 간섭 검사" in it }, "$mode: 되돌렸는데 안내가 남음")
            }
        }
    }
}
