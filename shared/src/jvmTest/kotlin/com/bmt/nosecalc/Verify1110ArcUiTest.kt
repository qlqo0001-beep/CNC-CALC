package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(피드백 2026-11-10 의 7 — 테이퍼 T1–T2 원호)의 화면 쪽: 못 그리는 값 · 치는 중간 값에서 화면을 실제로 그려 꺼지지 않는지,
 * 상태가 저장 · 복원되는지, T1 ⇄ T2 · 종류 · 형상 · 방향을 바꿔도 원호 칸이 제자리인지. 그림 몇 장은 build/shots/verify1110arc-*.png 로 남긴다
 */
class Verify1110ArcUiTest {
    private fun draw(width: Int = 400, height: Int = 1600, fontScale: Float = 1f, comp: CompMode = CompMode.MACHINE, content: @Composable () -> Unit) {
        val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        state.compMode = comp
        val scene = ImageComposeScene(width, height, Density(1f, fontScale)) { AppHost(state) { content() } }
        try {
            repeat(3) { scene.render(it * 100_000_000L).close() }
        } finally {
            scene.close()
        }
    }

    private fun arc(s: TaperState, r: String, concave: Boolean) = s.apply {
        setArc(true)
        setPoint("arcR", r)
        setArcConcave(concave)
    }

    /** 외경 · 내경 × 형상 1~5 × 역방향 × 뒤집기 × 볼록 · 오목 × R 칸 글(치는 중 · 0 · 음수 · 현/2 아래 · 최소 R 언저리 · 아주 큰 값) — 두 보정 방식을 번갈아 */
    @Test
    fun drawArcStates() {
        val rs = listOf("", "0", ".", "-", "0.", "-3", "0.0001", "3.53", "4.999", "5", "7", "99999", "123456789012")
        var n = 0
        val bad = mutableListOf<String>()
        for (kind in listOf(TaperKind.OUTER, TaperKind.INNER)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (concave in listOf(false, true)) for (r in rs) {
                n++
                val k = n
                val what = "$kind 형상 $type 역 $reverse 뒤집기 $flip 오목 $concave R '$r'"
                try {
                    draw(comp = if (k % 2 == 0) CompMode.MACHINE else CompMode.DIRECT) {
                        TaperScreen(
                            arc(TaperState(kind, reverse, type, open = true).example(), r, concave).apply {
                                if (flip) toggleFlip()
                                // 셋에 하나는 모서리 R · 시작 · 끝도
                                if (k % 3 == 0) {
                                    setPoint("r1", "0.5")
                                    setPoint("r2", if (k % 2 == 0) "50" else "1")
                                }
                            },
                        )
                    }
                } catch (e: Throwable) {
                    bad += "$what → $e"
                }
            }
        assertEquals(emptyList(), bad.take(10), "그리다 던진 것 ${bad.size} 건 / $n")
    }

    /** 좌표를 하나씩 채워 가는 중간 · T1 = T2 · 수평 · 수직 · 키패드를 R 칸에 연 채 · 좁은 폰 큰 글꼴 · 고르기 화면 */
    @Test
    fun drawTyping() {
        val bad = mutableListOf<String>()
        fun tryDraw(what: String, width: Int = 400, fontScale: Float = 1f, comp: CompMode = CompMode.MACHINE, state: () -> TaperState) {
            try {
                draw(width, 1600, fontScale, comp) { TaperScreen(state()) }
            } catch (e: Throwable) {
                bad += "$what → $e"
            }
        }
        val fills = listOf("x1" to "40", "z1" to "0", "x2" to "50", "z2" to "-5", "arcR" to "1", "arcR" to "10", "arcR" to "10.", "arcR" to "10.5")
        for (kind in listOf(TaperKind.OUTER, TaperKind.INNER)) for (k in 0..fills.size) {
            tryDraw("$kind 채우기 $k") { TaperState(kind, type = 1, open = true).apply { setArc(true); fills.take(k).forEach { (a, b) -> setPoint(a, b) } } }
        }
        for ((x2, z2) in listOf("40" to "0", "40" to "-5", "50" to "0", "0" to "-5", "-50" to "-5", "50" to "5", "30" to "-5")) for (comp in CompMode.values()) {
            tryDraw("T2 X$x2 Z$z2 $comp", comp = comp) {
                arc(TaperState(type = 1, open = true).example(), "10", false).apply { setPoint("x2", x2); setPoint("z2", z2) }
            }
        }
        for (field in listOf("arcR", "x1", "z2", "r1", "start", "end", "ang")) for (w in listOf(400, 320)) {
            tryDraw("키패드 $field 폭 $w", width = w, fontScale = if (w == 320) 1.3f else 1f) {
                arc(TaperState(TaperKind.INNER, type = 3, open = true).example(), "12", true).apply { editing = field }
            }
        }
        // 노즈R · Z 여유 극단, 팁 0~9
        for (nose in listOf("", "0", ".", "5", "12", "999999")) tryDraw("노즈R '$nose'") { arc(TaperState(type = 3, open = true).example(), "12", true).apply { setShared("noseR", nose) } }
        for (tip in 0..9) tryDraw("팁 $tip", comp = CompMode.DIRECT) { arc(TaperState(type = 2, open = true).example(), "30", tip % 2 == 0).apply { this.tip = tip } }
        // 고르기 화면: 형상마다 호를 켠 그림
        for (kind in listOf(TaperKind.OUTER, TaperKind.INNER)) for (reverse in listOf(false, true)) {
            tryDraw("고르기 $kind 역 $reverse", width = 320, fontScale = 1.3f) {
                TaperState(kind, reverse).apply {
                    for (t in 1..5) {
                        type = t
                        setArc(true)
                        setArcConcave(t % 2 == 0)
                        if (t > 3) toggleFlip()
                    }
                    type = null
                }
            }
        }
        // 2중 · 홈은 원호 열쇠가 있어도 직선(칸이 없다)
        tryDraw("2중") { TaperState(TaperKind.OUTER_DOUBLE, type = 1, open = true).example().apply { setArc(true); setPoint("arcR", "3") } }
        tryDraw("홈") { TaperState(TaperKind.GROOVE, type = 1, open = true).example().apply { setArc(true); setPoint("arcR", "3") } }
        assertEquals(emptyList(), bad, "그리다 던진 것")
    }

    /** 원호 켬 · R · 볼록/오목이 저장 · 복원되고, 형상 · 방향 · 종류마다 따로이며, T1 ⇄ T2 로 바뀌지 않는다 */
    @Test
    fun stateKeepsArc() {
        val s = arc(TaperState(type = 1, open = true).example(), "12.5", true)
        s.setPoint("r1", "0.3")
        // 저장 · 복원(화면 회전 · 프로세스 되살림)
        val saved = with(TaperState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(s) }!!
        val back = TaperState.Saver.restore(saved)!!
        assertTrue(back.arc && back.arcConcave, "복원 뒤 원호 · 오목")
        assertEquals("12.5", back.point("arcR"))
        assertEquals("0.3", back.point("r1"))
        // T1 ⇄ T2: 점 · 모서리 R 은 맞바뀌고 원호 값은 그대로
        val x1 = back.point("x1")
        back.toggleFlip()
        assertEquals(x1, back.point("x2"))
        assertEquals("0.3", back.point("r2"))
        assertTrue(back.arc && back.arcConcave && back.point("arcR") == "12.5", "뒤집은 뒤 원호 값")
        back.toggleFlip()
        // 다른 형상 · 방향 · 종류는 직선에서 시작하고, 돌아오면 남아 있다
        back.type = 2
        assertTrue(!back.arc && back.point("arcR").isEmpty(), "형상 2 는 직선")
        back.type = 1
        back.reverse = true
        assertTrue(!back.arc, "역방향은 따로")
        back.reverse = false
        back.kind = TaperKind.INNER
        assertTrue(!back.arc, "내경은 따로")
        back.kind = TaperKind.OUTER
        assertTrue(back.arc && back.arcConcave && back.point("arcR") == "12.5", "돌아오면 그대로")
        // 직선으로 돌아가도 R 값은 남고, 다시 켜면 그대로
        back.setArc(false)
        assertTrue(!back.arc && back.arcShape(1) == null)
        assertEquals("12.5", back.point("arcR"))
        back.setArc(true)
        assertEquals(false, back.arcShape(1), "오목 = false")
        // 2중 · 홈은 열쇠가 있어도 원호가 아니다
        val d = TaperState(TaperKind.OUTER_DOUBLE, type = 1, open = true).apply { setArc(true) }
        assertTrue(!d.arc && d.arcShape(1) == null)
    }

    /** 눈으로 볼 그림 몇 장(라이트): 내경 뒤집기 오목 + 모서리 R(직접 보정), 외경 역방향 볼록(기계 보정), 좁은 폰 큰 글꼴 R 과소 */
    @Test
    fun shots() {
        Shots.take("verify1110arc-inner-flip", dark = false, heightDp = 2900, setup = { it.compMode = CompMode.DIRECT }) {
            AppHost(it) {
                TaperScreen(
                    arc(TaperState(TaperKind.INNER, type = 3, open = true).example(), "12", true).apply {
                        setPoint("r1", "0.5")
                        setPoint("r2", "1")
                        toggleFlip()
                    },
                )
            }
        }
        Shots.take("verify1110arc-outer-reverse", dark = false, heightDp = 2700) {
            AppHost(it) { TaperScreen(arc(TaperState(TaperKind.OUTER, reverse = true, type = 1, open = true).example(), "8", false)) }
        }
        Shots.take("verify1110arc-narrow-error", dark = true, heightDp = 1500, widthDp = 320, fontScale = 1.3f) {
            AppHost(it) { TaperScreen(arc(TaperState(TaperKind.INNER, type = 1, open = true).example(), "2", true)) }
        }
        Shots.take("verify1110arc-pick", dark = false, widthDp = 360) {
            AppHost(it) {
                TaperScreen(
                    TaperState(TaperKind.INNER).apply {
                        for (t in 1..5) {
                            type = t
                            setArc(true)
                            setArcConcave(t % 2 == 0)
                        }
                        type = null
                    },
                )
            }
        }
    }
}
