package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset

/**
 * 궤적 창 스크린샷 · 골든이 같이 쓰는 장면(설계 9.4). 재생은 멈춘 채 시각을 고정해 찍는다(재생 중간 고정).
 * [prep] = 편집기 상태(예제 · 소재 칸 · 공구표), [ui] = 고른 것 · 보기 · 시각.
 */
internal class TrajScene(val name: String, val prep: SimState.() -> Unit, val ui: TrajUi.(SimRun) -> Unit = {})

internal object TrajScenes {
    /** 06 꼴: 내경 면취 G41(예제 ⑩) · 소재 Ø60 · 구멍 Ø30 · 끝면 Z0 · 'G01 X34. Z-2.' 고스트(보정 좌표 X34.000 Z-2.186) 고름 · 재생 60 % */
    val inner06 = TrajScene("sim-2d", {
        perform(Pending.Example(9))
        stockText = mapOf(StockField.DIA to "60", StockField.BORE to "30", StockField.FACE to "0", StockField.LENGTH to "40")
    }) { run ->
        lastT = run.totalSec * 0.6
        pick = Pick(run.ghosts.indexOfFirst { it.line == 7 }, measure = false)
        viewer.focus(Pt(-4.0, 18.0), 3.5f)
    }

    /** 10 꼴: 09 글(예제 ①) · 측정 모드 · ',C1.' 줄(15줄)의 면취 시작 고스트의 명령점 · 끝까지 */
    val measure10 = TrajScene("sim-2d-measure", { perform(Pending.Example(0)) }) { run ->
        lastT = run.totalSec
        measure = true
        pick = Pick(run.ghosts.indexOfFirst { it.line == 15 }, measure = true)
        viewer.focus(Pt(-30.0, 23.0), 4f)
    }

    /** 재생 중간: 09 글의 G71 황삭 층이 반쯤 — 남은 소재가 층 모양 */
    val mid = TrajScene("sim-2d-mid", { perform(Pending.Example(0)) }) { run -> lastT = run.totalSec * 0.42 }

    /** 각도 확인(06 의 56.31° 꼴): 09 글 끝 · ∠ 켬 */
    val angle = TrajScene("sim-2d-angle", { perform(Pending.Example(0)) }) { run ->
        lastT = run.totalSec
        this.angle = true
        viewer.focus(Pt(-12.0, 16.0), 2.2f)
    }

    /** 나사(S 4-116 G76 — 예제 ⑦): 남은 소재에 마지막 패스 깊이의 톱니 */
    val thread = TrajScene("sim-2d-thread", {
        perform(Pending.Example(6))
        stockText = mapOf(StockField.DIA to "68", StockField.FACE to "90", StockField.LENGTH to "80")
    }) { run -> lastT = run.totalSec }

    /** 척 닿음: 돌출 20 인데 Z−30 까지 — 빨간 띠 · ✕ · 재생이 닿은 자리에서 멈춤 */
    val chuck = TrajScene("sim-2d-chuck", {
        editor.replaceAll("O3001 (척 닿음 보기)\nT0101\nG97 S1000 M03\nG99\nG00 X40. Z2.\nG01 Z-30. F0.2\nG01 X60.\nG00 Z50.\nM30")
        tools = mapOf(1 to ToolOffset(0.4, 3))
        stockText = mapOf(StockField.DIA to "50", StockField.LENGTH to "20", StockField.FACE to "0")
    }) { run -> lastT = run.totalSec }

    /** 오류로 멈춘 글(편집기 오류 보기 글): 빨간 띠 '6줄에서 멈춤' + [편집기로] */
    val stop = TrajScene("sim-stop-error", { editor.replaceAll(SimShotsTest.ERRORS) }) { run -> lastT = run.totalSec }

    val all = listOf(inner06, measure10, mid, angle, thread, chuck, stop)

    /**
     * 05 꼴 부품(우리 글 — 05 의 3D 처럼 바깥 나사 · 홈 · 테이퍼, 안쪽 나사 · 테이퍼 구멍): 외경 G71/G70 → 홈 G75 둘(Ø52 · 나사 도피) → 바깥 나사 G92 M30×2 →
     * 내경 G71(U−)/G70 → 안 나사 G92 M24×1.5. 소재 Ø60 · 돌출 70 · 기장여유 +1 · 구멍 Ø18(미리 뚫음).
     */
    val PART05 = listOf(
        "O3105 (3D 보기 - 05 꼴)", "T0101", "G50 S2500", "G96 S180 M03", "G99", "G00 X64. Z3.", "G71 U1.5 R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25",
        "N10 G00 X24.", "G01 Z0. F0.12", "X30. Z-3.", "Z-26.", "X38.", "X40. Z-27.", "Z-40.", "X52. Z-48.", "Z-56.", "N20 X62.", "G70 P10 Q20", "G00 X100. Z50.",
        "T0303", "G97 S700 M03", "G00 X56. Z-53.", "G75 R0.5", "G75 X44. Z-54. P1500 Q1000 F0.08", "G00 Z-25.", "X34.", "G75 R0.5", "G75 X26. Z-26. P1000 Q1000 F0.08",
        "G00 X100. Z50.",
        "T0404", "G97 S800 M03", "G00 X34. Z5.", "G92 X29.2 Z-23. F2.", "X28.6", "X28.1", "X27.7", "X27.5", "X27.4", "G00 X100. Z50.",
        "T0202", "G96 S150 M03", "G00 X17. Z3.", "G71 U1. R0.5", "G71 P30 Q40 U-0.3 W0.1 F0.15",
        "N30 G00 X26.", "G01 Z0. F0.1", "X22.4 Z-1.8", "Z-16.", "X20. Z-30.", "Z-36.", "N40 X17.", "G70 P30 Q40", "G00 Z50.", "X100.",
        "T0505", "G97 S600 M03", "G00 X21. Z4.", "G92 X22.9 Z-14. F1.5", "X23.3", "X23.6", "X23.8", "X23.95", "G00 Z50.", "X100.", "M30",
    ).joinToString("\n")

    val PART05_TOOLS = mapOf(
        1 to ToolOffset(0.4, 3),
        2 to ToolOffset(0.4, 2),
        3 to ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0),
        4 to ToolOffset(0.0, 0, ToolKind.THREAD),
        5 to ToolOffset(0.0, 2, ToolKind.THREAD),
    )

    private val part05Prep: SimState.() -> Unit = {
        editor.replaceAll(PART05)
        tools = PART05_TOOLS
        stockText = mapOf(StockField.DIA to "60", StockField.LENGTH to "70", StockField.FACE to "1", StockField.BORE to "18")
    }

    /** 3D 재생 중간(05 꼴 부품): 외경 · 홈 · 바깥 나사를 마치고 구멍을 G71 로 넓히는 중 — 1/4 절개(처음 켬) */
    val part3d = TrajScene("sim-3d", part05Prep) { run ->
        mode3d = true
        lastT = run.totalSec * 0.8
    }

    /** 3D 끝(05 와 같은 꼴 — 바깥 · 안 나사, 홈, 테이퍼 구멍) */
    val part3dEnd = TrajScene("sim-3d-end", part05Prep) { run ->
        mode3d = true
        lastT = run.totalSec
    }

    /** 3D 절개 끔 · 자유 켬 · 돌린 시점(뒤쪽 아래에서) */
    val part3dFull = TrajScene("sim-3d-full", part05Prep) { run ->
        mode3d = true
        lastT = run.totalSec
        cam.quarter = false
        cam.free = true
        cam.yaw = 125f
        cam.pitch = 18f
        cam.touched = true
    }

    /** 3D 재생 중간(09 글 — G71 층이 반쯤): 층 모양 계단 */
    val mid3d = TrajScene("sim-3d-mid", { perform(Pending.Example(0)) }) { run ->
        mode3d = true
        lastT = run.totalSec * 0.42
    }

    /** 3D 나사(S 4-116 G76) */
    val thread3d = TrajScene("sim-3d-thread", {
        perform(Pending.Example(6))
        stockText = mapOf(StockField.DIA to "68", StockField.FACE to "90", StockField.LENGTH to "80")
    }) { run ->
        mode3d = true
        lastT = run.totalSec
    }

    val all3d = listOf(part3d, part3dEnd, part3dFull, mid3d, thread3d)

    fun sim(kv: KeyValueStore, s: TrajScene): SimState = SimState(kv).apply {
        s.prep(this)
        run = runOf(editor.text, tools)
        trajOpen = true
    }

    fun ui(sim: SimState, s: TrajScene): TrajUi = TrajUi().apply { s.ui(this, sim.run) }
}
