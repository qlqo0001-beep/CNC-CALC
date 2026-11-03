package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StopStep
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 재생 시계(설계 7.4 — M00 · M01 · 싱글 블록 · |◀ ▶| · 끝 · 척) · 속도 미끄럼 · 글 꼴 · 설정 저장 */
class PlayStateTest {
    private val od = mapOf(1 to ToolOffset(0.4, 3))

    private fun run(vararg lines: String): SimRun =
        GSim.run(SimInput((listOf("T0101", "G97 S1000 M03", "G99") + lines.toList() + "M30").joinToString("\n"), MachineSetup(), od))

    /** 한 번에 10 초씩(×1) 끝까지 또는 멈출 때까지 */
    private fun PlayState.go(speed: Float = 1f, sb: Boolean = false, m01: Boolean = false): PlayEvent? {
        playing = true
        repeat(100_000) { advance(10.0, speed, sb, m01)?.let { return it } }
        return null
    }

    @Test
    fun m00AlwaysStopsM01OnlyWhenOn() {
        val r = run("G00 X40. Z2.", "G01 Z-10. F0.2", "M00", "G01 X50.", "M01", "G00 Z5.")
        val stops = r.steps.filterIsInstance<StopStep>()
        val p = PlayState(r)
        assertEquals(PlayEvent.Stop(6, false), p.go())
        assertEquals(stops[0].t0, p.t, 1e-12)
        assertFalse(p.playing)
        // M01 꺼짐 → 지나서 끝
        assertEquals(PlayEvent.End, p.go())
        assertTrue(p.atEnd)
        // 처음부터, M01 켬 → M00 · M01 에서 각각 멈춤
        p.reset()
        assertEquals(PlayEvent.Stop(6, false), p.go(m01 = true))
        assertEquals(PlayEvent.Stop(8, true), p.go(m01 = true))
        assertEquals(stops[1].t0, p.t, 1e-12)
        assertEquals(PlayEvent.End, p.go(m01 = true))
        // 끝에서 재생 = 처음부터
        p.toggle()
        assertEquals(0.0, p.t)
        assertTrue(p.playing)
    }

    /** 싱글 블록: 블록 끝마다, 고정 사이클은 층(pass)마다 멈춘다 — 09 꼴 예제 */
    @Test
    fun singleBlockStopsAtBlockAndCyclePass() {
        val ex = exampleList[0]
        val r = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        val p = PlayState(r)
        var n = 0
        while (true) {
            val ev = p.go(sb = true) ?: break
            if (ev == PlayEvent.End) break
            assertEquals(PlayEvent.BlockEnd, ev)
            n++
        }
        // 멈춘 수 = 0 초 뒤 블록 끝 수(시각 0 의 블록은 재생을 누르기 전 · 마지막 끝은 End)
        assertEquals(p.bounds.count { it > 1e-9 } - 1, n)
        // G71 줄(7줄)의 층마다 따로 — 그 줄 안 멈춤이 층 수 이상
        val g71 = r.steps.filterIsInstance<MoveStep>().filter { it.line == 7 && it.pass != null }.mapNotNull { it.pass }.toSet()
        val inG71 = p.bounds.count { b -> r.steps.any { it.line == 7 && it.t1 == b } }
        assertTrue(g71.size >= 10 && inG71 >= g71.size, "층 ${g71.size} · 멈춤 $inG71")
    }

    /** |◀ = 블록 처음(이미 처음이면 앞 블록) · ▶| = 블록 끝(이미 끝이면 다음 블록 끝) · 줄 누르기 */
    @Test
    fun blockJumps() {
        val r = run("G00 X40. Z2.", "G01 Z-10. F0.2", "G01 X50.", "G00 Z5.")
        val m = r.steps.filterIsInstance<MoveStep>()
        val cut = m.first { it.line == 5 }
        val p = PlayState(r)
        p.seek((cut.t0 + cut.t1) / 2)
        p.blockBack()
        assertEquals(cut.t0, p.t, 1e-12)
        p.blockBack()
        assertEquals(m.first { it.line == 4 }.t0, p.t, 1e-12)
        p.seek((cut.t0 + cut.t1) / 2)
        p.blockForward()
        assertEquals(cut.t1, p.t, 1e-12)
        p.blockForward()
        assertEquals(m.first { it.line == 6 }.t1, p.t, 1e-12)
        assertTrue(p.goLine(7))
        assertEquals(m.first { it.line == 7 }.t0, p.t, 1e-12)
        assertFalse(p.goLine(99))
    }

    /** 한 프레임이 블록 끝과 그 뒤 M00 을 같이 덮어 싱글 블록이 블록 끝에서 먼저 멈춰도 M00 은 남는다(싱글 블록을 끄고 이어도 M00 에서 멈춤) */
    @Test
    fun singleBlockKeepsLaterM00() {
        val r = run("G00 X40. Z2.", "G01 Z-10. F0.2", "G01 X42.", "G01 Z-11.", "M00", "G00 X60.")
        val m = r.steps.filterIsInstance<MoveStep>()
        val m00 = r.steps.filterIsInstance<StopStep>().single()
        val p = PlayState(r)
        p.seek(m.first { it.line == 5 }.t1)
        p.playing = true
        // 10 초 한 프레임 = 6줄 끝(0.3 초 뒤) · 7줄 · M00(0.6 초 뒤)을 다 덮는다
        assertEquals(PlayEvent.BlockEnd, p.advance(10.0, 1f, singleBlock = true, optStop = false))
        assertEquals(m.first { it.line == 6 }.t1, p.t, 1e-12)
        assertEquals(PlayEvent.Stop(8, false), p.go())
        assertEquals(m00.t0, p.t, 1e-12)
        assertEquals(PlayEvent.End, p.go())
    }

    /** 척에 닿으면 그 시각이 끝 — 재생이 거기서 Chuck 으로 멈춘다 */
    @Test
    fun chuckLimitStops() {
        val r = run("G00 X40. Z2.", "G01 Z-45. F0.2", "G01 X60.")
        val p = PlayState(r)
        val at = r.totalSec / 3
        p.setChuck(at)
        assertEquals(PlayEvent.Chuck, p.go())
        assertEquals(at, p.t, 1e-12)
        p.setChuck(null)
        assertEquals(PlayEvent.End, p.go())
        assertEquals(r.totalSec, p.t, 1e-12)
    }

    /** 빠르기: 실제 1초 × 배수 = 프로그램 초 */
    @Test
    fun speedScalesProgramTime() {
        val r = run("G00 X40. Z2.", "G01 Z-100. F0.05")
        val p = PlayState(r)
        p.playing = true
        assertNull(p.advance(0.5, 20f, false, false))
        assertEquals(10.0, p.t, 1e-12)
    }

    @Test
    fun speedSliderIsLogWithTicks() {
        assertEquals(1f, PlayState.speedAt(0f))
        assertEquals(200f, PlayState.speedAt(1f))
        for (t in PlayState.TICKS) assertEquals(t, PlayState.speedAt(PlayState.posOf(t)))
        assertEquals(0.5f, PlayState.posOf(kotlin.math.sqrt(200.0).toFloat()), 1e-6f)
        assertEquals("x200", speedText(200f))
        assertEquals("x2.5", speedText(2.5f))
    }

    @Test
    fun texts() {
        assertEquals("07:48", timeText(468.9))
        assertEquals("1:02:03", timeText(3723.0))
        assertEquals("0.08", feedText(0.08))
        assertEquals("0.125", feedText(0.125))
        assertEquals("6.00", feedText(6.0))
        assertEquals("300", feedText(300.0))
        assertEquals("12", numText(12.0))
        assertEquals("0.4", numText(0.4))
        assertEquals("-2.186", numText(-2.186))
    }

    /** 기계 · 읽기 · 보기 설정은 저장소에 남고(다시 켜도 그대로), 빈칸 · 틀린 칸은 가정 값 */
    @Test
    fun machineSetupPersistsAndFeedsRun() {
        val kv = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })
        val s = SimState(kv)
        s.setMachine(MachineField.RAPID_X, "30")
        s.setMachine(MachineField.RAPID_Z, "abc")
        s.changeRapidPath(RapidPath.LINEAR)
        s.changeDecimal(DecimalMode.CALCULATOR)
        s.changeView(ViewFlags(ghost = false, nose = true))
        val t = SimState(kv)
        assertEquals("30", t.machineText[MachineField.RAPID_X])
        assertEquals(RapidPath.LINEAR, t.rapidPath)
        assertEquals(DecimalMode.CALCULATOR, t.decimal)
        assertEquals(ViewFlags(ghost = false, nose = true), t.view)
        val m = t.runSetup().machine
        assertEquals(30_000.0, m.rapidX)
        assertEquals(MachineSetup().rapidZ, m.rapidZ, "틀린 칸은 가정 값")
        assertEquals(RapidPath.LINEAR, m.rapidPath)
        assertEquals(DecimalMode.CALCULATOR, m.decimal)
        assertIs<FieldValue.Bad>(fieldValue("abc", machineOk(MachineField.RAPID_Z)))
        // 보정 시작 · 취소 형식: 처음 형식 C(회사 기계), 고르면 남는다
        assertEquals(CompStartup.C, t.compStartup)
        assertEquals(CompStartup.C, m.compStartup)
        t.changeCompStartup(CompStartup.AB)
        assertEquals(CompStartup.AB, SimState(kv).runSetup().machine.compStartup)
    }

    /** G30 제2 원점 칸: 빈칸이면 원점(G28)과 같고, 한 축만 넣으면 다른 축은 원점 값 */
    @Test
    fun secondHomeFeedsG30() {
        val kv = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })
        val s = SimState(kv)
        s.editor.insert("G00 X40. Z2.\nG30 U0. W0.\nM30")
        s.setMachine(MachineField.HOME_X, "300")
        s.setMachine(MachineField.HOME_Z, "80")
        fun end() = s.runOf(s.editor.text, s.tools).steps.filterIsInstance<MoveStep>().last().tip.end
        assertEquals(300.0, end().xDia, 1e-12)
        assertEquals(80.0, end().z, 1e-12)
        s.setMachine(MachineField.HOME2_Z, "150")
        assertEquals(300.0, end().xDia, 1e-12)
        assertEquals(150.0, end().z, 1e-12)
        s.setMachine(MachineField.HOME2_X, "250")
        assertEquals(250.0, end().xDia, 1e-12)
        assertEquals("250", SimState(kv).machineText[MachineField.HOME2_X])
    }

    /** 화면을 떠났다 와도(앱을 켠 동안) 소재 칸 · 속도 · 싱글 블록 · M01 이 남는다(Saver) */
    @Test
    fun saverKeepsTrajectorySettings() {
        val kv = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })
        val s = SimState(kv)
        s.setStock(StockField.DIA, "60")
        s.setStock(StockField.BORE, "30")
        s.speed = 50f
        s.singleBlock = true
        s.optStop = true
        val saver = SimState.saver(kv)
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(s) }
        val r = saver.restore(assertNotNull(saved))!!
        assertEquals(mapOf(StockField.DIA to "60", StockField.BORE to "30"), r.stockText)
        assertEquals(50f, r.speed)
        assertTrue(r.singleBlock && r.optStop)
    }

    /** 원점 자동(설계 4.1): X max(200, Ø + 100) · Z 끝면 + 100 — 소재 칸을 넣으면 그 값으로, 원점 칸을 넣으면 그대로 */
    @Test
    fun homeFollowsStock() {
        val kv = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })
        val s = SimState(kv)
        s.editor.insert("T0101\nG97 S1000 M03\nG99\nG00 X40. Z2.\nG01 Z-30. F0.2\nG01 X52.\nM30")
        s.tools = od
        // 자동 소재 Ø53 · 끝면 Z2 → 원점 X200 · Z102
        assertEquals(102.0, s.runOf(s.editor.text, s.tools).start.z, 1e-12)
        s.setStock(StockField.DIA, "160")
        s.setStock(StockField.FACE, "5")
        val r = s.runOf(s.editor.text, s.tools)
        assertEquals(260.0, r.start.xDia, 1e-12)
        assertEquals(105.0, r.start.z, 1e-12)
        s.setMachine(MachineField.HOME_X, "300")
        s.setMachine(MachineField.HOME_Z, "80")
        val h = s.runOf(s.editor.text, s.tools).start
        assertEquals(300.0, h.xDia, 1e-12)
        assertEquals(80.0, h.z, 1e-12)
        // 소재: 구멍 ≥ 지름이면 틀린 칸
        s.setStock(StockField.BORE, "200")
        val (spec, bad) = stockOf(s.stockText, r)
        assertNull(spec)
        assertEquals(setOf(StockField.BORE), bad)
        s.setStock(StockField.BORE, "")
        assertNotNull(stockOf(s.stockText, r).first)
    }
}
