package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.screens.RoundHelp
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundInnerStep
import java.io.File
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 독립 검증(2026-11-14) C — 사용자 답 "관통이면 넘어가도 됌" 의 화면 글: 실제 화면(ImageComposeScene)을 그리고 '자세히' 를 모두 펴서 NC 카드 아랫글이
 * 관통 / 막힘에 맞게 갈리는지, 그리고 그 글이 실제 계산(RoundCalc)의 동작과 맞는지 본다. 도움말 한 줄도.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class Verify1113ThroughScreenTest {
    private class Screen(height: Int = 6000, content: @Composable () -> Unit) : AutoCloseable {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply { setTheme(false) }
        private val scene: ImageComposeScene
        private var t = 0L
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

        fun texts(): List<String> = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace("⁠", "").replace(' ', ' ') } }

        /** '자세히' 를 모두 편다(누를 때마다 다시 찾는다) */
        fun openAll(text: String = "자세히") {
            repeat(30) {
                val hit = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.firstOrNull { n ->
                    n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
                } ?: return
                hit.config[SemanticsActions.OnClick].action!!.invoke()
                frames(3)
            }
        }

        override fun close() = scene.close()
    }

    private fun shown(st: RoundState, mode: CompMode): String = Screen { RoundScreen(st) }.use { s ->
        s.app.compMode = mode
        s.frames()
        s.openAll()
        s.texts().joinToString("\n")
    }

    private val holeNote = "관통 구멍이라 시작 좌표 너머에서 들어와 끝까지 깎습니다"
    private val cornerNote = "구석이 조금"
    private val passNote = "관통 구멍은 끝 좌표 너머가 구멍이라 그 너머로 지나가 구멍 벽을 끝까지 깎은 뒤"

    /** 뒤집은 관통(내경 5 예제 D): 기계 · 직접 둘 다 '시작 좌표 너머에서 들어와' 글, 구석 글은 없다 */
    @Test
    fun flippedThroughSaysEntersFromBeyond() {
        for (mode in CompMode.entries) {
            val text = shown(roundInnerStep().apply { setFlipped(5, true) }, mode)
            assertTrue(holeNote in text, "$mode: 관통 글이 없다\n$text")
            assertFalse(cornerNote in text, "$mode: 관통인데 구석 글\n$text")
        }
    }

    /** 막힌 시작(외경 3 뒤집기 — 척 쪽 끝 직선)은 구석 글 그대로, 관통 글 없음. 내경 5 막힘 뒤집기(바닥 축에서 시작)도 관통 글 없음 */
    @Test
    fun blockedKeepsOldNotes() {
        for (mode in CompMode.entries) {
            val outer = shown(roundExample(3, open = true).apply { setFlipped(3, true) }, mode)
            assertTrue(cornerNote in outer, "$mode 외경 3 뒤집기: 구석 글이 없다\n$outer")
            assertFalse(holeNote in outer || passNote in outer, "$mode 외경 3 뒤집기: 관통 글\n$outer")
            val blind = shown(roundInnerStep().apply { through = false; setFlipped(5, true) }, mode)
            assertFalse(holeNote in blind || passNote in blind, "$mode 내경 5 막힘 뒤집기: 관통 글\n$blind")
            val blindBasic = shown(roundInnerStep().apply { through = false }, mode)
            assertFalse(holeNote in blindBasic || passNote in blindBasic, "$mode 내경 5 막힘: 관통 글\n$blindBasic")
        }
    }

    /** 바꾸지 않은 방향의 관통: 기계 보정은 '끝 좌표 너머로 지나가' 글(실제로 NC 가 끝 좌표 너머로 간다), 직접 보정은 그 글 없음 */
    @Test
    fun basicThroughMachineSaysPassesEnd() {
        val st = roundInnerStep()
        val machine = shown(st, CompMode.MACHINE)
        assertTrue(passNote in machine, "기계: 너머로 지나간다는 글이 없다\n$machine")
        val direct = shown(roundInnerStep(), CompMode.DIRECT)
        assertFalse(passNote in direct, "직접: 기계 보정 글이 직접 보정에\n$direct")
    }

    /** 기계 보정 글이 관통 구멍 끝 좌표 너머로 가나(G40 앞 줄들의 가장 작은 Z 가 끝 좌표보다 작다 — NC 로 가른다) */
    private fun passesEnd(st: RoundState): Boolean {
        val sol = RoundCalc.machine(st.input(5)).solution!!
        val endZ = sol.drawing.segs.last().end.z
        val zs = sol.nc.takeWhile { it != "G40" }.mapNotNull { Regex("Z(-?[0-9]*\\.?[0-9]*)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        return zs.min() < endZ - 5e-4
    }

    private val blockedNote = "막힌 끝처럼 끝 좌표에서 공구 쪽으로 뺍니다"

    /**
     * 좁은 관통 구멍의 되돌림(RoundCalc.throughFrame — 너머로 빼는 두 안 모두 빼는 길을 못 찾으면 막힌 끝): NC 는 끝 좌표 너머로 가지 않고 구석이 남는다.
     * 화면은 '너머로 지나가 구멍 벽을 끝까지 깎은 뒤' 라고 하지 않고 THROUGH_END_BLOCKED 띠와 막힌 끝 글을 보인다(고침 2026-11-14 — 전에는 그 글이 보였다)
     */
    @Test
    fun narrowFallbackDoesNotClaimPassing() {
        val st = roundInnerExample(5).apply {
            through = true
            mapOf("cr1" to "0", "cr2" to "0", "mainR" to "4.165", "d1" to "4.321", "d2" to "6.038", "l1" to "4.357", "l2" to "").forEach { (k, v) -> setDim(5, k, v) }
            setShared("noseR", "1.73")
            setShared("zClear", "2.668")
            setShared("leg", "3.297")
        }
        val res = RoundCalc.machine(st.input(5))
        assertTrue(RoundWarning.THROUGH_END_BLOCKED in res.warnings && !passesEnd(st), "재현 전제(되돌림)가 아니다: ${st.input(5)} ${res.warnings} ${res.solution?.nc}")
        val text = shown(st, CompMode.MACHINE)
        assertFalse(passNote in text, "되돌림(막힌 끝 — 구석 남음)인데 '너머로 지나가 끝까지 깎은 뒤' 글이 보인다: ${res.solution?.nc}\n$text")
        assertTrue(blockedNote in text && "구멍 끝의 노즈R 구간에 구석이 조금" in text, "되돌림 글 · 경고 띠가 없다\n$text")
    }

    /**
     * 전에 경고 없이 되돌아가던 꼴(검증 2026-11-14 찾은 것 1 — Ø4.97 · 노즈 1.2, 그때 NC 끝 `G1Z-4.548` `X.07F.3`): 고친 뒤에는 끝 좌표 너머로 c 지나간 뒤
     * 입구 쪽으로 비켜 빼므로 NC 가 끝 좌표 너머로 가고, 화면의 '너머로 지나가 끝까지' 글이 실제와 맞는다(되돌림 글 · 띠는 없다)
     */
    @Test
    fun silentFallbackDoesNotClaimPassing() {
        val st = roundInnerExample(5).apply {
            through = true
            mapOf("cr1" to "0.977", "cr2" to "0", "mainR" to "5.532", "d1" to "4.97", "d2" to "8.667", "l1" to "1.994", "l2" to "").forEach { (k, v) -> setDim(5, k, v) }
            setShared("noseR", "1.2")
            setShared("zClear", "2")
            setShared("leg", "1.05")
        }
        val res = RoundCalc.machine(st.input(5))
        val nc = res.solution!!.nc
        assertTrue(passesEnd(st) && RoundWarning.THROUGH_END_BLOCKED !in res.warnings, "끝 좌표 너머로 가지 않는다: $nc ${res.warnings}")
        val text = shown(st, CompMode.MACHINE)
        assertTrue(passNote in text, "너머로 지나가는데 그 글이 없다: $nc\n$text")
        assertFalse(blockedNote in text, "너머로 지나가는데 되돌림 글: $nc\n$text")
    }

    /**
     * 뒤집은 관통이 NOSE_UNREACHABLE(노즈R > D1/2 — 기본 방향도 못 푼다)일 때 빨간 띠 글: 관통은 시작 좌표 너머에서 들어오니 '그 시작 좌표를 넘지 않고 닿으려면
     * 그 직선이 노즈R 보다 길어야' 는 관통에 맞지 않는다(RoundModel.roundWarningText 가 관통을 모른다)
     */
    @Test
    fun flippedThroughUnreachableTextFitsThrough() {
        val st = roundInnerStep().apply {
            setDim(5, "d1", "2")
            setShared("noseR", "1.2")
            setFlipped(5, true)
        }
        val res = RoundCalc.calc(st.input(5))
        assertTrue(res.solution == null && RoundWarning.NOSE_UNREACHABLE in res.warnings, "재현 전제(NOSE_UNREACHABLE)가 아니다: ${st.input(5)} ${res.warnings}")
        assertTrue(RoundCalc.calc(st.input(5).copy(flip = false)).solution == null, "기본 방향은 풀린다")
        val text = shown(st, CompMode.MACHINE)
        assertFalse("시작 좌표를 넘지 않고 닿으려면" in text, "관통인데 막힌 시작 글:\n$text")
    }

    /** 도움말: 관통 한 줄 */
    @Test
    fun helpHasThroughLine() {
        val all = RoundHelp.flatMap { it.items }.joinToString("\n")
        assertTrue("관통 구멍은 그 너머가 구멍이라 시작 좌표 너머에서 들어와 끝까지 깎습니다" in all, all)
    }
}
