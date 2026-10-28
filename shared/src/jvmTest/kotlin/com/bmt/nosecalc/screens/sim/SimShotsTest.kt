package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.remember
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.Shots
import kotlin.test.Test

/**
 * 시뮬레이터 편집기 스크린샷(설계 9.4) → build/shots/sim-*-light.png · -dark.png. 원본 docs/store/09.jpg 와 나란히 본다.
 * 화면 상태는 [SimState] 를 직접 맞춰(예제 열기 · 커서 · 시트 · 묻는 창) 실제 화면과 같은 [SimulatorPage] 로 그린다.
 */
class SimShotsTest {
    companion object {
        /** 오류 보기 글: 5줄 소수점 없는 X(노랑), 6줄 X 두 번(빨강 — 실행이 여기서 멈춤), 7줄 받지 않는 주소 D(빨강) */
        const val ERRORS = "O2001 (오류 보기)\nT0101\nG97 S1000 M03\nG99\nG00 X44 Z2.\nG01 X40. X41. F0.2\nG01 Z-20. D1.\nM30"
    }

    private fun shot(name: String, widthDp: Int = 412, heightDp: Int = 915, fontScale: Float = 1f, prep: SimState.() -> Unit) =
        Shots.both(name, heightDp = heightDp, widthDp = widthDp, fontScale = fontScale, setup = { it.open(Feature.SIMULATOR) }) { state ->
            val sim = remember {
                SimState(state.store).apply {
                    prep()
                    run = runOf(editor.text, tools)
                }
            }
            AppHost(state) { SimulatorPage(sim) {} }
        }

    /** 09 꼴: 예제 ①(09 글 + G70) · 키패드 열림 · 커서 8줄 */
    private val original09: SimState.() -> Unit = {
        perform(Pending.Example(0))
        editor.moveTo(editor.lineStart(7))
    }

    private val errors: SimState.() -> Unit = {
        editor.replaceAll(ERRORS)
        editor.moveTo(editor.lineStart(5) + 9)
    }

    @Test
    fun editor() = shot("sim-editor", prep = original09)

    @Test
    fun editorClosedKeypad() = shot("sim-editor-closed") {
        original09()
        keypadOpen = false
    }

    @Test
    fun editorShift() = shot("sim-editor-shift") {
        original09()
        shift = ShiftState.ONCE
    }

    @Test
    fun editorShiftLock() = shot("sim-editor-shift-lock", widthDp = 360) {
        original09()
        shift = ShiftState.LOCK
    }

    @Test
    fun editorErrors() = shot("sim-editor-errors", prep = errors)

    @Test
    fun editorIssues() = shot("sim-editor-issues") {
        errors()
        sheet = SimSheet.ISSUES
    }

    @Test
    fun editorFind() = shot("sim-editor-find") {
        original09()
        editor.moveTo(0)
        openFind()
        editQuery("Z-30")
    }

    @Test
    fun editorEmpty() = shot("sim-editor-empty") {}

    @Test
    fun programs() = shot("sim-programs") {
        programs.save(1001, exampleList[0].text)
        programs.save(2001, ERRORS)
        perform(Pending.Load(1001))
        sheet = SimSheet.PROGRAMS
    }

    @Test
    fun more() = shot("sim-more") {
        original09()
        sheet = SimSheet.MORE
    }

    @Test
    fun askDelete() = shot("sim-ask-delete") {
        programs.save(1001, exampleList[0].text)
        perform(Pending.Load(1001))
        ask = SimAsk.Delete(1001)
    }

    @Test
    fun askNumber() = shot("sim-ask-number") {
        editor.insert("G00 X10.\nM30")
        ask = SimAsk.Number
    }

    @Test
    fun askUnsaved() = shot("sim-ask-unsaved") {
        original09()
        editor.insert("G04 X1.\n")
        ask = SimAsk.Unsaved(Pending.Example(1))
    }

    /** 큰 글꼴 · 좁은 폰 · PC 넓은 창 */
    @Test
    fun narrowAndLarge() {
        shot("sim-editor-w360", widthDp = 360, prep = original09)
        shot("sim-editor-fs130", fontScale = 1.3f, prep = original09)
        shot("sim-editor-errors-w360", widthDp = 360, prep = errors)
        shot("sim-editor-closed-fs130-w360", widthDp = 360, fontScale = 1.3f) {
            errors()
            keypadOpen = false
        }
        shot("pc-wide-sim", widthDp = 1280, heightDp = 720, prep = original09)
        shot("pc-short-sim", heightDp = 640, prep = original09)
    }
}
