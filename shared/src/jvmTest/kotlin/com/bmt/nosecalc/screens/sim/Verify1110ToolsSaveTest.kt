package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.sim.ToolShape
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2026-11-10 공구 갈래 독립 검증(피드백 3 — 공구 저장). 구현자의 시험과 따로 짠 검사: PC 저장 파일을 다시 읽는 '껐다 켬', 99개 표 · 모든 칸,
 * 620edc6 이 쓴 파일, 깨진 값을 세 열쇠에 한꺼번에, 지우기 · 번호 바꿈 · 같은 번호 두 번, 저장 뒤 고치고 껐다 켬.
 */
class Verify1110ToolsSaveTest {
    private fun file() = File.createTempFile("bmt-v1110", ".properties").apply { delete() }

    /** 껐다 켬 = 같은 파일을 새 KeyValueStore 로 다시 읽고 새 상태 객체 */
    private fun restart(f: File) = SimState(KeyValueStore(f))

    private fun SimState.type(text: String) = editor.replaceAll(text)

    private fun SimState.open(n: Int): String? {
        val r = request(Pending.Load(n))
        return (ask as? SimAsk.Unsaved)?.let { perform(it.then) } ?: r
    }

    /** 99 개 번호 — 종류 · 인서트 · 칸마다 받는 범위의 끝 값 · 반올림이 안 되는 수 */
    private val big: Map<Int, ToolOffset> = (1..99).associateWith { n ->
        val kind = ToolKind.entries[n % ToolKind.entries.size]
        ToolOffset(
            noseR = listOf(0.0, 0.1 + 0.2, 50.0, 1.0 / 3)[n % 4],
            tip = n % 10,
            kind = kind,
            insert = InsertShape.entries[n % InsertShape.entries.size],
            kappaDeg = listOf(null, 0.001, 179.999, 107.5)[n % 4],
            width = listOf(100.0, null, 0.0001, 2.5)[n % 4],
            threadDeg = listOf(55.0, 179.9, null, 60.0)[n % 4],
            dieDia = listOf(null, 200.0, 12.7, 1e-6)[n % 4],
            diePitch = listOf(20.0, null, 1.75, 0.0001)[n % 4],
            barDia = listOf(500.0, 0.001, null, 16.0, 1.0 / 7)[n % 5],
        )
    }

    private val holders = COMPANY_HOLDERS.filter { it.makers.isNotEmpty() }.take(6)

    @Test
    fun bigTableEveryFieldSurvivesSaveOpenAndRestart() {
        val f = file()
        val s = SimState(KeyValueStore(f))
        s.type("O2001 (BIG)\nT0101\nG0X20.Z2.\nM30")
        s.tools = big
        holders.forEachIndexed { i, h -> s.pickHolder(90 + i, h) }
        s.tools = s.tools + big.filterKeys { it >= 90 } // 홀더가 채운 칸을 다시 끝 값으로(홀더 이름표는 남음 — '수정됨')
        StockField.entries.forEachIndexed { i, fld -> s.setStock(fld, listOf("40", "55.5", "0.3", "12", "150")[i]) }
        val tools = s.tools
        val hs = s.toolHolders
        val st = s.stockText
        assertEquals(big, tools)
        assertEquals(6, hs.size)
        assertEquals("O2001 저장했습니다 — 공구 · 소재 설정도 함께", s.requestSave())

        // 다 바꾼 뒤 다시 열기
        s.tools = mapOf(1 to ToolOffset(0.8, 3))
        s.toolHolders = emptyMap()
        StockField.entries.forEach { s.setStock(it, "") }
        assertTrue(s.modified)
        assertNotNull(s.open(2001))
        assertEquals(tools, s.tools)
        assertEquals(hs, s.toolHolders)
        assertEquals(st, s.stockText)
        assertFalse(s.modified)

        // 껐다 켬(파일을 새로 읽음) — 작업 중 값 · 저장한 값 둘 다
        val r = restart(f)
        assertEquals(tools, r.tools)
        assertEquals(hs, r.toolHolders)
        assertEquals(st, r.stockText)
        assertFalse(r.modified)
        r.tools = emptyMap()
        assertNotNull(r.open(2001))
        assertEquals(tools, r.tools)
        // 홀더 이름표가 실제 홀더로 돌아온다(한글 제조사 이름 포함)
        holders.forEachIndexed { i, h -> assertEquals(h.id, r.holderOf(90 + i)?.id) }
    }

    /** 620edc6 의 PC 코드가 쓴 파일(앞 회차가 그 코드를 돌려 만든 것, 회사 글 아님) */
    private val legacy = listOf(
        "#Wed Nov 11 02:11:00 KST 2026",
        "compMode=MACHINE",
        "sim/draft=O1001 (BORE X6)\\nT0303\\nG97S1500M03\\nG0X6.Z2.\\nG1Z-10.F.05\\nG0X5.Z2.\\nM30\\n",
        "sim/draft/cursor=68",
        "sim/draft/from=p1001",
        "sim/keypad=0",
        "sim/machine=rx\\=30;path\\=DOGLEG;dec\\=STANDARD;skip\\=0;suv\\=C",
        "sim/prog/1001=O1001 (BORE X6)\\nT0303\\nG97S1500M03\\nG0X6.Z2.\\nG1Z-10.F.05\\nG0X5.Z2.\\nM30",
        "sim/prog/1001/at=1794268800000",
        "sim/prog/21=",
        "sim/prog/21/at=",
        "sim/progs=1001",
        "sim/view=10111",
        "theme=dark",
    ).joinToString("\n")

    @Test
    fun legacyFileUpgrade() {
        val f = file().apply { writeText(legacy, Charsets.UTF_8) }
        val s = restart(f)
        assertEquals(emptyMap(), s.tools)
        assertTrue(s.editor.text.startsWith("O1001 (BORE X6)"))
        val mine = mapOf(3 to ToolOffset(0.2, 2, barDia = 5.0))
        s.tools = mine
        assertNull(s.open(1001), "예전 프로그램은 알림 없이")
        assertEquals("O1001 (BORE X6)\nT0303\nG97S1500M03\nG0X6.Z2.\nG1Z-10.F.05\nG0X5.Z2.\nM30", s.editor.text)
        assertEquals(mine, s.tools)
        // 저장 안 하고 껐다 켬 → 공구 남음, 예전 프로그램이라 '고침'(연 뒤 공구를 넣음)은 아님
        val r = restart(f)
        assertEquals(mine, r.tools)
        assertFalse(r.modified)
        // 옛 파일의 다른 열쇠는 그대로
        assertEquals("30", r.machineText[MachineField.RAPID_X])
        assertEquals(listOf(1001), r.programs.numbers())
    }

    /** 세 열쇠(작업 중 · 기준 · 프로그램 설정)가 한꺼번에 깨져도 켜지고 글은 열린다. 열린 공구표로 그림 · 실행이 던지지 않는다 */
    @Test
    fun brokenValuesEverywhere() {
        val bads = listOf(
            "", "v1", "end", "v1\n", "\nend", "v2\ntools=1|0.4|3|TURN|C||||||\nend", "v1\ntools=1|0.4|3|TURN|C||||",
            "v1\ntools=1|abc|99|ZZZ|Q|-5|1e400|NaN|Infinity|-0|1e-320\nend",
            "v1\ntools=;;;|||;1;0|1|1|TURN|C|||;100|1|1|TURN|C|||;-3|1|1|TURN|C|||\nholders=a=b=c;=;5=;7=x\nstock=dia=abc;len=1e5;face=-;bore=..;chuck=99999999999999999\nend",
            "v1\ntools=1|0.4|3|TURN|C|||||||||||||\nend", "v1\n" + "tools=" + "1|0.4|3|TURN|C|||;".repeat(7000) + "\nend",
            "v1\ntools=1|0.4|2|TURN|C|||||5" + "0".repeat(400) + "\nend", "\u0000\u0001￿", "v1\nv1\nend\nend",
        )
        for (bad in bads) {
            val f = file()
            val kv = KeyValueStore(f)
            kv.put(ProgramStore.key(3001), "O3001\nT0101\nG0X20.Z2.\nG1Z-5.F.2\nM30")
            kv.put(ProgramStore.setupKey(3001), bad)
            kv.put(ProgramStore.atKey(3001), "1")
            kv.put(ProgramStore.LIST, "3001")
            kv.put(ProgramStore.DRAFT_FROM, "p3001")
            kv.put(ProgramStore.DRAFT, "O3001")
            kv.put(ProgramStore.DRAFT_SETUP, bad)
            kv.put(ProgramStore.DRAFT_BASE, bad)
            val s = restart(f)
            s.modified
            s.open(3001)
            assertEquals("O3001\nT0101\nG0X20.Z2.\nG1Z-5.F.2\nM30", s.editor.text, "글: ${bad.take(40)}")
            for ((n, t) in s.tools) {
                assertTrue(n in 1..99, "번호 $n")
                ToolShape.body(t, 4.0)
                ToolShape.outline(t)
            }
            s.runOf(s.editor.text, s.tools)
            stockOf(s.stockText, s.run)
            restart(f).tools
        }
    }

    /** 지운 번호에 새로 저장: 옛 공구가 살아나지 않는다. 번호 바꿔 저장: 옛 번호는 옛 공구 그대로. 같은 번호 두 번: 나중 것 */
    @Test
    fun deleteRenumberSaveTwice() {
        val f = file()
        val s = SimState(KeyValueStore(f))
        val a = mapOf(1 to ToolOffset(0.4, 3), 2 to ToolOffset(0.2, 2, barDia = 6.0))
        val b = mapOf(1 to ToolOffset(0.8, 3, insert = InsertShape.D))
        val c = mapOf(5 to ToolOffset(0.1, 6, ToolKind.THREAD, barDia = 9.0))

        s.type("O6001\nT0101\nM30")
        s.tools = a
        s.requestSave()
        s.delete(6001)
        assertEquals("", KeyValueStore(f).get(ProgramStore.setupKey(6001)))
        s.tools = emptyMap()
        s.type("O6001\nT0202\nM30")
        s.requestSave()
        s.tools = b
        s.open(6001)
        assertEquals(emptyMap(), s.tools, "지운 번호의 옛 공구가 살아나면 안 됨")

        // 번호 바꿔 저장(O6002 새 번호)
        s.type("O6002\nT0101\nM30")
        s.tools = b
        s.requestSave()
        s.type("O6003\nT0101\nM30")
        s.tools = c
        s.requestSave()
        assertEquals(b, restart(f).also { it.open(6002) }.tools)
        assertEquals(c, restart(f).also { it.open(6003) }.tools)

        // 같은 번호 두 번 — 나중 공구
        val r = restart(f)
        r.open(6002)
        r.tools = a
        r.requestSave()
        r.tools = c
        r.requestSave()
        r.tools = b
        r.open(6002)
        assertEquals(c, r.tools)

        // 덮어쓰기: 6003 글을 6002 번호로 → 묻고 덮음 → 6002 의 공구 = 그때 공구
        r.open(6003)
        r.type("O6002\nT0505\nM30")
        r.tools = b
        assertNull(r.requestSave())
        val ow = r.ask as SimAsk.Overwrite
        r.save(ow.number)
        assertEquals(b, restart(f).also { it.open(6003) }.let { it.open(6002); it.tools })
        assertEquals(c, restart(f).also { it.open(6003) }.tools, "덮어쓰기 뒤에도 6003 은 그대로")
    }

    /** 저장 → 공구 고침 → 저장 안 하고 껐다 켬: 고친 공구가 남고 '● 고침', 그 프로그램을 다시 열면 묻고 저장 때 값으로 */
    @Test
    fun editAfterSaveSurvivesRestartAndAsks() {
        val f = file()
        val s = SimState(KeyValueStore(f))
        val a = mapOf(3 to ToolOffset(0.4, 2, barDia = 8.0))
        s.type("O8001\nT0303\nM30")
        s.tools = a
        s.requestSave()
        s.tools = s.tools + (3 to ToolOffset(0.4, 2, barDia = 10.0))
        val r = restart(f)
        assertEquals(10.0, r.tools[3]?.barDia)
        assertTrue(r.modified)
        assertFalse(r.textModified)
        assertNull(r.request(Pending.Load(8001)))
        assertTrue(r.ask is SimAsk.Unsaved, "설정만 고친 채 열기 → 묻는다")
        r.perform((r.ask as SimAsk.Unsaved).then)
        assertEquals(a, r.tools)
        assertFalse(r.modified)
    }

    /** 새 프로그램(⋯ 새 프로그램)으로 넘어가도 공구표는 그대로 */
    @Test
    fun newProgramKeepsTools() {
        val f = file()
        val s = SimState(KeyValueStore(f))
        val a = mapOf(4 to ToolOffset(0.0, 2, ToolKind.GROOVE, width = 2.0, barDia = 7.0))
        s.tools = a
        s.request(Pending.New)
        (s.ask as? SimAsk.Unsaved)?.let { s.perform(it.then) }
        assertEquals(a, s.tools)
        assertEquals(a, restart(f).tools)
    }

    /**
     * 예제에서 공구를 고친 뒤(글은 그대로) 저장한 프로그램을 열면 묻지 않고 공구표가 통째로 바뀐다(토스트만) — 설계 결정. 고친 값은 되돌릴 길이 없다(지금 동작을 적어 둔다 — V3 (a), 사용자에게 물음).
     * 편집기 글이 빈칸이어도 저장한 프로그램의 설정을 고쳤으면 묻는다(V3 (b) 고침 — 예전엔 글이 빈칸이면 설정을 고쳤어도 묻지 않고 바꿨다).
     */
    @Test
    fun replacedWithoutAskingCases() {
        val f = file()
        val s = SimState(KeyValueStore(f))
        val saved = mapOf(1 to ToolOffset(0.4, 3))
        s.type("O9001\nT0101\nM30")
        s.tools = saved
        s.requestSave()
        s.request(Pending.Example(0))
        (s.ask as? SimAsk.Unsaved)?.let { s.perform(it.then) }
        val mine = s.tools + (1 to ToolOffset(1.2, 3, barDia = 3.0))
        s.tools = mine
        val toast = s.request(Pending.Load(9001))
        assertNull(s.ask, "예제에서 고친 공구 — 묻지 않음")
        assertNotNull(toast)
        assertEquals(saved, s.tools)

        // 저장한 프로그램 + 설정 고침 + 글 빈칸
        s.tools = mine
        assertTrue(s.setupModified)
        s.type("")
        s.request(Pending.Load(9001))
        val ask = s.ask
        assertTrue(ask is SimAsk.Unsaved, "글이 빈칸이어도 설정을 고쳤으면 묻는다: $ask")
        assertEquals(mine, s.tools, "묻는 동안은 그대로")
        s.perform((ask as SimAsk.Unsaved).then)
        assertEquals(saved, s.tools)
    }

    /** PC 저장은 put 마다 파일을 통째로 다시 쓴다 — 공구 칸 한 글자마다 걸리는 시간(프로그램 60개 · 각 1만 자 · 공구 99개) */
    @Test
    fun writeCostPerToolKeystroke() {
        val f = file()
        val s = SimState(KeyValueStore(f))
        s.tools = big
        repeat(60) { i ->
            s.type("O${5000 + i}\n" + "G1X20.Z-5.F.2\n".repeat(700))
            s.requestSave()
        }
        val t0 = System.nanoTime()
        repeat(20) { k -> s.tools = s.tools + (1 to ToolOffset(0.1 * (k + 1), 3)) }
        val ms = (System.nanoTime() - t0) / 1e6 / 20
        println("VERIFY1110 파일 ${f.length() / 1024} KB · 공구 칸 한 번 쓰기 ${"%.1f".format(ms)} ms")
        assertTrue(ms < 2000, "한 번 쓰기 $ms ms")
    }
}
