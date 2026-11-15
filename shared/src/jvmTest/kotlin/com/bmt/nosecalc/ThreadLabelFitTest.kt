package com.bmt.nosecalc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.ThreadScreen
import com.bmt.nosecalc.screens.thread.ChoiceField
import com.bmt.nosecalc.screens.thread.METHOD_LONG_ROOM
import com.bmt.nosecalc.screens.thread.METHOD_ORDER
import com.bmt.nosecalc.screens.thread.ROLL_MATERIAL_EMPTY
import com.bmt.nosecalc.screens.thread.ThreadTopCard
import com.bmt.nosecalc.screens.thread.methodFontSize
import com.bmt.nosecalc.screens.thread.methodNarrow
import com.bmt.nosecalc.screens.thread.methodRoom
import com.bmt.nosecalc.screens.thread.methodText
import com.bmt.nosecalc.screens.thread.threadRoll
import com.bmt.nosecalc.screens.thread.threadRollEmpty
import com.bmt.nosecalc.ui.theme.Space
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 나사 화면 전조 다이스의 글 잘림(검토 2026-11-14 a3 · a4) — 실제 글꼴(noto_sans_kr)로 그린 장면(밀도 2.625)에서 칸 글이 한 줄에 다 드는지 본다.
 * PC(JVM) Skia 는 '…' 로 줄인 줄도 isLineEllipsized 가 늘 false 라(docs/ui-design.md 가공 방식), 글 배치(GetTextLayoutResult)의 한 줄 폭(최대 고유 폭)을
 * 칸이 준 폭과 견준다. PC 는 자동 줄임도 '…' 와 같이 쓰면 줄지 않아 가장 큰 글 크기로 재는 셈이다(안드로이드보다 빡빡하다)
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ThreadLabelFitTest {
    private class Scene(widthDp: Int, heightDp: Int, fontScale: Float, content: @Composable () -> Unit) : AutoCloseable {
        private val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply {
            setTheme(false)
            open(Feature.THREAD)
        }
        private val scene: ImageComposeScene
        private var t = 0L

        init {
            Snapshot.sendApplyNotifications()
            scene = ImageComposeScene(
                (widthDp * Shots.DENSITY).roundToInt(), (heightDp * Shots.DENSITY).roundToInt(), Density(Shots.DENSITY, fontScale),
            ) { AppHost(app) { content() } }
            // 글꼴 리소스는 비동기로 읽혀 몇 프레임 뒤에 바뀐다(Shots 와 같이 프레임마다 쉬며 40장)
            frames(40, sleep = 15)
        }

        fun frames(n: Int = 2, sleep: Long = 0) = repeat(n) {
            Snapshot.sendApplyNotifications()
            if (sleep > 0) Thread.sleep(sleep)
            scene.render(t).close()
            t += 100_000_000L
        }

        private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

        /** 그린 글과 그 배치 */
        fun layouts(): List<Pair<String, TextLayoutResult>> = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.mapNotNull { n ->
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: return@mapNotNull null
            val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return@mapNotNull null
            val out = mutableListOf<TextLayoutResult>()
            get(out)
            out.firstOrNull()?.let { text to it }
        }

        override fun close() = scene.close()
    }

    /** 한 줄에 다 드나 — 글의 한 줄 폭 ≤ 칸이 준 폭(반 px 여유) */
    private fun fits(r: TextLayoutResult) = r.multiParagraph.intrinsics.maxIntrinsicWidth <= r.layoutInput.constraints.maxWidth + 0.5f

    private fun TextLayoutResult.widths() = "글 ${r1(multiParagraph.intrinsics.maxIntrinsicWidth)}px > 칸 ${layoutInput.constraints.maxWidth}px"

    private fun r1(v: Float) = (v * 10).roundToInt() / 10.0

    /**
     * a4: 가공 방식 세 칸 — 화면 폭 320 ~ 760dp × 글꼴 0.85 · 1 · 1.3배(2배는 412dp 부터 — 그보다 좁으면 가장 작은 11sp 로도 '전조 다이스' 가 넘친다)
     * 모두 세 칸 글이 '…' 없이 든다. 넓은 갈래(첫 칸 '바이트 (G92 · G76)')도 실제로 나온다.
     * 옛 문턱(글꼴 배율로 나눈 칸 줄 폭 420dp — 화면 460 ~ 472dp)에서는 긴 이름이 잘렸다
     */
    @Test
    fun methodLabelsFit() {
        val labels = METHOD_ORDER.map { methodText(it) }.toSet() + METHOD_ORDER.map { methodText(it, narrow = true) }
        for ((fs, from) in listOf(0.85f to 320, 1f to 320, 1.3f to 320, 2f to 412)) {
            var width by mutableStateOf(from)
            var wide = 0
            Scene(800, 140, fs, content = {
                Box(Modifier.width(width.dp).padding(horizontal = Space.gutter)) { ThreadTopCard(threadRoll()) }
            }).use { scene ->
                for (w in from..760 step 3) {
                    width = w
                    scene.frames()
                    val shown = scene.layouts().filter { it.first in labels }
                    assertEquals(3, shown.size, "${w}dp × $fs: ${shown.map { it.first }}")
                    for ((text, r) in shown) assertTrue(fits(r), "${w}dp × $fs '$text' 이 잘림 — ${r.widths()}")
                    if (shown.any { it.first == methodText(METHOD_ORDER[0]) }) wide++
                }
                // 샷 thread-roll-w490 의 폭: 넓은 갈래의 아래 끝 언저리(칸 글 자리 134.5)에서 긴 이름이 나오고 든다
                if (fs == 1f) {
                    width = 490
                    scene.frames()
                    val long = scene.layouts().filter { it.first == methodText(METHOD_ORDER[0]) }
                    assertEquals(1, long.size, "490dp 에서 긴 이름이 안 나옴")
                    assertTrue(fits(long.single().second), "490dp: ${long.single().second.widths()}")
                }
            }
            if (fs <= 1.3f) assertTrue(wide > 0, "글꼴 $fs 배: 넓은 갈래가 한 번도 안 나옴")
        }
        // 갈래 규칙(칸 줄 폭 = 화면 − 40): 360dp · 130% 는 '바이트' 14sp, 412dp 폰은 '바이트' 15sp, 옛 문턱 바로 위(화면 460 · 472dp)와
        // 360dp 폰 · 글꼴 0.85배(나눈 폭 423.5)도 '바이트', 칸 글 자리 134 이상(화면 약 490dp 부터)이 '바이트 (G92 · G76)'
        assertEquals(true to 14, methodRoom(320f, 1.3f).let { methodNarrow(it) to methodFontSize(it) })
        assertEquals(true to 15, methodRoom(372f, 1f).let { methodNarrow(it) to methodFontSize(it) })
        assertTrue(methodNarrow(methodRoom(420f, 1f)) && methodNarrow(methodRoom(432f, 1f)) && methodNarrow(methodRoom(320f, .85f)))
        assertFalse(methodNarrow(methodRoom(448f, 1f)))
        assertTrue(methodNarrow(methodRoom(447f, 1f)))
        assertEquals(134f, METHOD_LONG_ROOM)
    }

    /**
     * a3: 전조 화면 가공 소재 칸의 빈칸 글 — 360dp · 130% 와 412dp 에서 '…' 없이 든다. 옛 글 '스테인리스 (STS316 등) — 고르지 않음' 은 같은 칸에서 잘렸다
     * (같은 폭의 칸에 옛 글을 넣어 이 잼이 잘림을 잡는지도 본다)
     */
    @Test
    fun rollMaterialEmptyFits() {
        val old = "스테인리스 (STS316 등) — 고르지 않음"
        for ((w, fs) in listOf(360 to 1.3f, Shots.WIDTH_DP to 1f)) {
            for (state in listOf(::threadRoll, ::threadRollEmpty)) {
                Scene(w, 1400, fs, content = { ThreadScreen(state()) }).use { scene ->
                    val field = scene.layouts().filter { it.first == ROLL_MATERIAL_EMPTY }
                    assertEquals(1, field.size, "${w}dp × $fs")
                    assertTrue(fits(field.single().second), "${w}dp × $fs: ${field.single().second.widths()}")
                }
            }
            // 같은 폭의 칸(화면 − 양옆 gutter · 카드 안 여백)에 옛 글과 새 글
            Scene(w, 200, fs, content = {
                Box(Modifier.width(w.dp).padding(horizontal = Space.gutter + Space.cardPad)) { ChoiceField(null, old, empty = true) {} }
            }).use { scene ->
                val r = scene.layouts().single { it.first == old }.second
                if (fs > 1f) assertFalse(fits(r), "옛 글이 ${w}dp × $fs 에서 들어감 — 잼이 잘림을 못 잡는다")
            }
        }
    }
}
