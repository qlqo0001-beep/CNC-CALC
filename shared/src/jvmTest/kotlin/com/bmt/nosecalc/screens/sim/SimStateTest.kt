package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 저장 · 불러오기 · 임시 글 · 공구표 · 찾기(설계 7.3 · 9.4 SimStateTest) */
class SimStateTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })

    private fun SimState.type(s: String) = editor.insert(s)

    @Test
    fun saveUsesFirstONumberAndListsNewestFirst() {
        val kv = store()
        val s = SimState(kv)
        assertFalse(s.modified)
        s.type("O1001 (FIRST)\nG00 X10.\nM30")
        assertTrue(s.modified)
        assertEquals("O1001 저장했습니다", s.requestSave())
        assertFalse(s.modified)
        assertEquals(Origin.Saved(1001), s.origin)
        s.perform(Pending.New)
        s.type("O0021 (SECOND)\nM30")
        s.requestSave()
        val list = s.programs.list()
        assertEquals(listOf(21, 1001), list.map { it.number })
        assertEquals(listOf("SECOND", "FIRST"), list.map { it.comment })
        assertEquals(listOf(2, 3), list.map { it.lines })
        assertTrue(list.all { it.savedAt != null })
        // 다시 켜도(같은 저장소 · 새 상태) 그대로
        val again = SimState(kv)
        assertEquals("O0021 (SECOND)\nM30", again.programs.load(21))
        assertEquals("O0021 (SECOND)\nM30", again.editor.text)
        assertEquals(Origin.Saved(21), again.origin)
        assertFalse(again.modified)
    }

    @Test
    fun noNumberAsksThenPrependsO() {
        val s = SimState(store())
        s.type("G00 X10.\nM30")
        s.editor.moveTo(4)
        assertNull(s.requestSave())
        assertEquals(SimAsk.Number, s.ask)
        assertEquals("O1234 저장했습니다", s.saveAs(1234))
        assertEquals("O1234\nG00 X10.\nM30", s.editor.text)
        assertEquals(10, s.editor.cursor) // 넣은 줄만큼 뒤로 — 같은 글자 앞
        assertNull(s.ask)
        assertEquals(Origin.Saved(1234), s.origin)
    }

    @Test
    fun sameNumberAsksToOverwrite() {
        val s = SimState(store())
        s.type("O1001\nM30")
        s.requestSave()
        s.perform(Pending.New)
        s.type("O1001 (OTHER)\nM30")
        assertNull(s.requestSave())
        assertEquals(SimAsk.Overwrite(1001), s.ask)
        s.save(1001)
        assertEquals("O1001 (OTHER)\nM30", s.programs.load(1001))
        // 지금 연 프로그램을 고쳐 저장하면 묻지 않는다
        s.type("\n")
        assertEquals("O1001 저장했습니다", s.requestSave())
    }

    @Test
    fun unsavedTextAsksBeforeLoading() {
        val s = SimState(store())
        s.type("O1001\nM30")
        s.requestSave()
        s.perform(Pending.New)
        s.type("G00 X5.")
        s.request(Pending.Load(1001))
        assertIs<SimAsk.Unsaved>(s.ask)
        assertEquals("G00 X5.", s.editor.text)
        s.perform((s.ask as SimAsk.Unsaved).then)
        assertEquals("O1001\nM30", s.editor.text)
        assertNull(s.ask)
        // 고친 것이 없으면 바로
        s.request(Pending.New)
        assertNull(s.ask)
        assertEquals("", s.editor.text)
    }

    @Test
    fun deleteRemovesFromListAndMakesTextUnsaved() {
        val kv = store()
        val s = SimState(kv)
        s.type("O1001\nM30")
        s.requestSave()
        assertEquals("O1001 을 지웠습니다", s.delete(1001))
        assertTrue(s.programs.list().isEmpty())
        assertNull(s.programs.load(1001))
        assertEquals(Origin.New, s.origin)
        assertTrue(s.modified)
        assertEquals(Origin.New, SimState(kv).origin)
    }

    @Test
    fun limits() {
        val s = SimState(store())
        s.type("O1\n" + "G00 X1.\n".repeat(ProgramStore.MAX_CHARS / 8 + 1))
        assertEquals("프로그램이 너무 깁니다 — 10만 자까지 저장합니다", s.requestSave())
        val t = SimState(store())
        repeat(ProgramStore.MAX_PROGRAMS) { t.programs.save(it + 1, "O${it + 1}") }
        t.type("O9999\nM30")
        assertEquals("프로그램은 200개까지 — 목록에서 지운 뒤 저장하세요", t.requestSave())
    }

    @Test
    fun exampleBringsToolsAndIsNotModified() {
        val s = SimState(store())
        val i = exampleList.indexOfFirst { it.text.startsWith("O9010") }
        s.perform(Pending.Example(i))
        assertEquals(Origin.Example(i), s.origin)
        assertFalse(s.modified)
        assertEquals(0.4, s.tools[2]?.noseR)
        s.run = s.runOf(s.editor.text, s.tools)
        assertNull(s.run.stop)
        // 공구표가 없으면 G41 줄에서 멈춘다(공구 설정에 넣으라는 글)
        val bare = s.runOf(s.editor.text, emptyMap())
        assertEquals(IssueCode.TOOL_DATA_MISSING, bare.stop?.code)
    }

    @Test
    fun draftKeepsTextCursorAndOrigin() {
        val kv = store()
        val s = SimState(kv)
        s.perform(Pending.Example(0))
        s.type("(X)")
        s.saveDraft()
        val again = SimState(kv)
        assertEquals(s.editor.text, again.editor.text)
        assertEquals(s.editor.cursor, again.editor.cursor)
        assertEquals(Origin.Example(0), again.origin)
        assertTrue(again.modified)
    }

    @Test
    fun toolsRoundTrip() {
        val t = mapOf(
            1 to ToolOffset(0.4, 3),
            3 to ToolOffset(0.2, 3, ToolKind.GROOVE, InsertShape.D, 93.0, 3.0, null),
            5 to ToolOffset(kind = ToolKind.DIE, threadDeg = 55.0, dieDia = 12.7, diePitch = 1.27),
            7 to ToolOffset(null, null, ToolKind.THREAD, threadDeg = 55.0),
            9 to ToolOffset(0.2, 2, insert = InsertShape.T, kappaDeg = 91.0, barDia = 12.0),
        )
        assertEquals(t, SimState.decodeTools(SimState.encodeTools(t)))
        assertEquals(emptyMap(), SimState.decodeTools(""))
        // 새 글은 11칸(… 나사각 | 다이스 지름 | 다이스 피치 | 바 지름 — 빈칸 = 자동), 다이스 칸이 없던 옛 8칸 글 · 바 지름이 없던 10칸 글도 읽는다(없는 칸은 빈칸)
        assertEquals("5|||DIE|C|||55.0|12.7|1.27|", SimState.encodeTools(mapOf(5 to t.getValue(5))))
        assertEquals("9|0.2|2|TURN|T|91.0|||||12.0", SimState.encodeTools(mapOf(9 to t.getValue(9))))
        assertEquals(mapOf(5 to t.getValue(5)), SimState.decodeTools("5|||DIE|C|||55.0|12.7|1.27"))
        // 받는 범위 밖의 바 지름(0 · 음수 · 숫자 아님 · 500 초과)은 빈칸(자동)으로 읽는다
        for (bad in listOf("0.0", "-3.0", "NaN", "Infinity", "abc", "500.5")) {
            assertEquals(mapOf(9 to t.getValue(9).copy(barDia = null)), SimState.decodeTools("9|0.2|2|TURN|T|91.0|||||$bad"), bad)
        }
        assertEquals(
            mapOf(1 to ToolOffset(0.4, 3), 7 to ToolOffset(null, null, ToolKind.THREAD, threadDeg = 55.0)),
            SimState.decodeTools("1|0.4|3|TURN|C|||;7|||THREAD|C|||55.0"),
        )
    }

    @Test
    fun keypadGoesToFindWhenOpen() {
        val s = SimState(store())
        s.type("G01 Z-30. ,C1.\nG01 X54.\nG01 Z-30.")
        s.editor.moveTo(0)
        s.openFind()
        for (k in "Z-30") s.key(CncKey.Text(k.toString()))
        assertEquals("Z-30", s.query)
        assertEquals("Z-30", s.editor.selectedText())
        assertEquals(4, s.editor.selection!!.first)
        s.key(CncKey.Enter)
        assertTrue(s.editor.selection!!.first > 20)
        s.key(CncKey.Text("9"))
        assertTrue(s.findMiss)
        s.key(CncKey.Back)
        assertFalse(s.findMiss)
        // 편집기 글은 그대로
        assertEquals("G01 Z-30. ,C1.\nG01 X54.\nG01 Z-30.", s.editor.text)
        s.findOpen = false
        s.press(CncRows[3][4]) // 0 — 고른 글을 바꾼다
        assertEquals("G01 Z-30. ,C1.\nG01 X54.\nG01 0.", s.editor.text)
    }

    @Test
    fun keypadPreferenceIsRemembered() {
        val kv = store()
        assertTrue(SimState(kv).keypadOpen)
        SimState(kv).keypadOpen = false
        assertFalse(SimState(kv).keypadOpen)
    }
}
