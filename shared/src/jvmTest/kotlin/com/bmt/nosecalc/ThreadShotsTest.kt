package com.bmt.nosecalc

import com.bmt.nosecalc.screens.ThreadScreen
import com.bmt.nosecalc.screens.ThreadHelp
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.screens.thread.Pick
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadNc
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.thread20Un
import com.bmt.nosecalc.screens.thread.threadDie
import com.bmt.nosecalc.screens.thread.threadDieEmpty
import com.bmt.nosecalc.screens.thread.threadDieStore
import com.bmt.nosecalc.screens.thread.threadBadWhileWaiting
import com.bmt.nosecalc.screens.thread.threadFormula
import com.bmt.nosecalc.screens.thread.threadMetricInner
import com.bmt.nosecalc.screens.thread.threadNptInt
import com.bmt.nosecalc.screens.thread.threadPtExt
import com.bmt.nosecalc.screens.thread.threadOriginal
import com.bmt.nosecalc.screens.thread.threadReverse
import com.bmt.nosecalc.screens.thread.threadWarn
import com.bmt.nosecalc.screens.thread.threadZOrder
import kotlin.test.Test
import kotlin.test.assertEquals

/** 나사 G76 화면을 라이트/다크로 → build/shots/thread-* (원본 docs/store/08.jpg 와 나란히 본다) */
class ThreadShotsTest {
    private fun shot(
        name: String,
        heightDp: Int = 2900,
        editing: ThreadField? = null,
        sheet: Pick? = null,
        widthDp: Int = Shots.WIDTH_DP,
        fontScale: Float = 1f,
        frames: Int = 40,
        frameMs: Long = 100,
        scrollToEnd: Boolean = false,
        store: Map<String, String> = emptyMap(),
        state: () -> ThreadState,
    ) = Shots.both(
        name, heightDp, frames,
        setup = { a ->
            a.open(Feature.THREAD)
            store.forEach { (k, v) -> a.store.put(k, v) }
        },
        widthDp = widthDp, fontScale = fontScale, frameMs = frameMs, scrollToEnd = scrollToEnd,
    ) { AppHost(it) { ThreadScreen(state(), editing, sheet) } }

    @Test
    fun shots() {
        // 처음 화면(모두 빈칸 · 파랑 안내) — 첫 화면과 아래까지
        shot("thread-empty", Shots.HEIGHT_DP) { ThreadState() }
        shot("thread-empty-long", 1700) { ThreadState() }
        // 원본 08 의 값 — 화면 전체
        shot("thread") { threadOriginal() }
        shot("thread-metric-inner", 3000) { threadMetricInner() }
        shot("thread-formula") { threadFormula() }
        shot("thread-warn", 3100) { threadWarn() }
        shot("thread-zorder", 1500) { threadZOrder() }
        shot("thread-reverse", 1500) { threadReverse() }
        shot("thread-bad-waiting", 1900) { threadBadWhileWaiting() }
    }

    /** 키패드(첫 절입 — G76 카드 안 칸) · 고르기 시트 · 아래 결과 띠 */
    @Test
    fun overlays() {
        shot("thread-keypad", Shots.HEIGHT_DP, editing = ThreadField.FIRST_CUT, frames = 150, frameMs = 16) { threadOriginal() }
        shot("thread-keypad-adv", Shots.HEIGHT_DP, editing = ThreadField.TARGET, frames = 150, frameMs = 16) { threadOriginal().apply { advOpen = true } }
        shot("thread-sheet-class", Shots.HEIGHT_DP, sheet = Pick.CLASS) { threadOriginal() }
        shot("thread-sheet-size", Shots.HEIGHT_DP, sheet = Pick.SIZE) { ThreadState() }
        shot("thread-sheet-material", Shots.HEIGHT_DP, sheet = Pick.MATERIAL) { threadOriginal() }
        shot("thread-dock", Shots.HEIGHT_DP, scrollToEnd = true) { threadOriginal() }
    }

    /** 아래 NC 영역: G92 다회 탭(처음) + 다듬질 블록 · G76 두 줄 탭 · 내경 G92 · 좁은 폰 큰 글꼴 */
    @Test
    fun nc() {
        shot("thread-nc-g92", 1500, scrollToEnd = true) { threadOriginal() }
        shot("thread-nc-g76", 1500, scrollToEnd = true) { threadOriginal().apply { nc = ThreadNc.G76 } }
        shot("thread-nc-g92-inner", 1500, scrollToEnd = true) { threadMetricInner() }
        shot("thread-nc-g92-w360-fs130", 1800, widthDp = 360, fontScale = 1.3f, scrollToEnd = true) { threadOriginal() }
        shot("thread-nc-g76-w360-fs130", 1500, widthDp = 360, fontScale = 1.3f, scrollToEnd = true) { threadOriginal().apply { nc = ThreadNc.G76 } }
    }

    /** 좁은 폰(360dp) · 큰 글꼴(1.3배) */
    @Test
    fun narrow() {
        shot("thread-w360-fs130", 3400, widthDp = 360, fontScale = 1.3f) { threadOriginal().apply { advOpen = true } }
        shot("thread-empty-w360-fs130", Shots.HEIGHT_DP, widthDp = 360, fontScale = 1.3f) { ThreadState() }
    }

    /**
     * 나사 확장(2026-11-05): 종류 시트(넷) · 20UN 사이즈 시트(계열 고르기 + 자주 쓰는 다섯) · PT 숫나사 전체 · NPT 암나사 전체 · PT NC 영역(I/R 고르기 + 다듬질),
     * 각각 보통 폭과 좁은 폰(360dp) · 큰 글꼴(1.3배)
     */
    @Test
    fun extension() {
        for ((w, fs, tail) in listOf(Triple(Shots.WIDTH_DP, 1f, ""), Triple(360, 1.3f, "-w360-fs130"))) {
            shot("thread-kind-sheet$tail", Shots.HEIGHT_DP, sheet = Pick.KIND, widthDp = w, fontScale = fs) { threadOriginal() }
            shot("thread-size-sheet-20un$tail", Shots.HEIGHT_DP, sheet = Pick.SIZE, widthDp = w, fontScale = fs) { thread20Un() }
            shot("thread-pt$tail", if (fs > 1f) 3700 else 3000, widthDp = w, fontScale = fs) { threadPtExt().apply { advOpen = true } }
            shot("thread-npt-inner$tail", if (fs > 1f) 3500 else 2900, widthDp = w, fontScale = fs) { threadNptInt() }
            shot("thread-pt-nc$tail", if (fs > 1f) 1900 else 1500, widthDp = w, fontScale = fs, scrollToEnd = true) { threadPtExt() }
        }
    }

    /**
     * 다이스 헤드 · 인서트(2026-11-06): 다이스 화면 전체(회사 값 저장소에 Vc · 플로트 · 목) · 아래 체이서 표와 NC · 처음(파랑 안내 · 가공 방식 줄) ·
     * 카탈로그 절삭 속도 시트 · 인서트 시트 · 바이트 고급 설정의 인서트 칸, 좁은 폰(360dp) · 큰 글꼴(1.3배)
     */
    @Test
    fun die() {
        val store = threadDieStore()
        shot("thread-die", 2500, store = store) { threadDie() }
        shot("thread-die-chasers", 1700, scrollToEnd = true, store = store) { threadDie() }
        shot("thread-die-empty", Shots.HEIGHT_DP) { threadDieEmpty() }
        shot("thread-die-speed-sheet", Shots.HEIGHT_DP, sheet = Pick.DIE_SPEED, store = store) { threadDie() }
        shot("thread-insert-sheet", Shots.HEIGHT_DP, sheet = Pick.INSERT) { threadOriginal() }
        shot("thread-insert", 3000) { threadOriginal().apply { advOpen = true; pickInsert(insertChoices.first()) } }
        shot("thread-die-w360-fs130", 3300, widthDp = 360, fontScale = 1.3f, store = store) { threadDie() }
        shot("thread-die-chasers-w360-fs130", 2000, widthDp = 360, fontScale = 1.3f, scrollToEnd = true, store = store) { threadDie() }
        shot("thread-insert-sheet-w360-fs130", Shots.HEIGHT_DP, sheet = Pick.INSERT, widthDp = 360, fontScale = 1.3f) { threadOriginal() }
        shot("thread-insert-w360-fs130", 3600, widthDp = 360, fontScale = 1.3f) { threadOriginal().apply { advOpen = true; pickInsert(insertChoices.last()) } }
    }

    /** 도움말: 묶음 일곱 + 끝의 '장갑을 끼고 쓸 때' */
    @Test
    fun help() {
        assertEquals(
            listOf("나사 고르기", "값 넣기", "고급 공차 및 인선 설정", "G92 · G76 프로그램과 분석", "관용 테이퍼 나사 (PT · NPT)", "다이스 헤드", "경고"),
            ThreadHelp.map { it.title },
        )
        Shots.both("thread-help", setup = { it.open(Feature.THREAD); it.helpOpen = true }) { App(it) }
        // 새 두 묶음(관용 테이퍼 · 다이스 헤드)부터 연 도움말
        for ((start, tail) in listOf(4 to "pipe", 5 to "die")) {
            Shots.both("thread-help-$tail", setup = { it.open(Feature.THREAD); it.helpOpen = true }) {
                AppHost(it) { FeatureScaffold(Feature.THREAD, ThreadHelp, helpStart = start) {} }
            }
        }
    }
}
