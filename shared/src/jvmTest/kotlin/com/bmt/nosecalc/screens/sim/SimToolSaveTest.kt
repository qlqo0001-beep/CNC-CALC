package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 공구 저장(2026-11-10 피드백 3 — "프로그램을 저장하면 공구도 저장, 껐다 켜면 초기화"): 프로그램 '저장' 에 공구표 · 홀더 이름표 · 소재 칸이 딸려 가고,
 * 열면 그 값으로 바뀌며, 작업 중 값은 앱을 껐다 켜도 남는다(설계 20.2)
 */
class SimToolSaveTest {
    private fun file() = File.createTempFile("bmt-sim", ".properties").apply { delete() }
    private fun store() = KeyValueStore(file())
    private fun holder(code: String, maker: String? = null) =
        COMPANY_HOLDERS.single { it.code == code && (maker == null || maker in it.makers) }

    private val toolsA = mapOf(
        1 to ToolOffset(0.4, 3, kappaDeg = 95.0),
        3 to ToolOffset(0.2, 2, ToolKind.TURN, InsertShape.T, 91.0, barDia = 5.0),
        5 to ToolOffset(0.2, 3, ToolKind.GROOVE, InsertShape.D, null, 3.0),
        8 to ToolOffset(kind = ToolKind.DIE, threadDeg = 60.0, dieDia = 12.7, diePitch = 1.75),
    )
    private val toolsB = mapOf(1 to ToolOffset(0.8, 3), 2 to ToolOffset(0.4, 2, kappaDeg = 107.5, barDia = 8.0))

    /** 모든 칸(바 지름 포함) · 홀더를 고른 기본값 줄 · 홀더 둘 · 소재 둘 */
    private fun SimState.fill() {
        tools = toolsA
        pickHolder(7, holder("DCKNL2525M12")) // 기본값과 같은 줄(κr 빈칸 · C · 바이트)
        pickHolder(3, holder("S10K-STFCR09", "대구텍"))
        setStock(StockField.DIA, "20")
        setStock(StockField.BORE, "5")
    }

    private fun SimState.open(n: Int): String? {
        val r = request(Pending.Load(n))
        return (ask as? SimAsk.Unsaved)?.let { perform(it.then) } ?: r
    }

    // ---- 형식(ProgramSetup)

    @Test
    fun formatRoundTripAndFixedText() {
        val x = ProgramSetup(
            mapOf(
                1 to ToolOffset(0.4, 3, kappaDeg = 95.0),
                3 to ToolOffset(0.2, 2, insert = InsertShape.T, kappaDeg = 91.0, barDia = 5.0),
                5 to ToolOffset(0.2, 3, ToolKind.GROOVE, InsertShape.D, null, 3.0),
                7 to ToolOffset(kind = ToolKind.THREAD, tip = 2, threadDeg = 60.0, barDia = 12.0),
                8 to ToolOffset(kind = ToolKind.DIE, threadDeg = 60.0, dieDia = 12.7, diePitch = 1.75),
            ),
            mapOf(3 to "S10K-STFCR09|대구텍", 1 to "PCLNL2525M12|한국야금,대구텍"),
            mapOf(StockField.DIA to "20", StockField.BORE to "5", StockField.FACE to "0.5"),
        )
        val s = x.encode()
        assertEquals(
            "v1\n" +
                "tools=1|0.4|3|TURN|C|95.0|||||;3|0.2|2|TURN|T|91.0|||||5.0;5|0.2|3|GROOVE|D||3.0||||;7||2|THREAD|C|||60.0|||12.0;8|||DIE|C|||60.0|12.7|1.75|\n" +
                "holders=1=PCLNL2525M12|한국야금,대구텍;3=S10K-STFCR09|대구텍\n" +
                "stock=dia=20;face=0.5;bore=5\n" +
                "end",
            s,
        )
        assertEquals(x, ProgramSetup.decode(s))
        // 빈 설정도 글이 있다(설정 없음 = 빈칸과 다르다)
        assertEquals("v1\ntools=\nholders=\nstock=\nend", ProgramSetup().encode())
        assertEquals(ProgramSetup(), ProgramSetup.decode("v1\ntools=\nholders=\nstock=\nend"))
        assertEquals(ProgramSetup(), ProgramSetup.decode("v1\nend"))
        // 끝의 빈 줄 · 공백은 보지 않는다
        assertEquals(x, ProgramSetup.decode("$s\n\n  "))
    }

    @Test
    fun writesValuesAsIsComparesCanonical() {
        val a = ProgramSetup(mapOf(1 to ToolOffset(0.4, 3), 5 to ToolOffset()), mapOf(1 to "X|Y"), mapOf(StockField.DIA to "20", StockField.LENGTH to ""))
        val r = assertNotNull(ProgramSetup.decode(a.encode()))
        assertEquals(a.tools, r.tools) // 기본값 줄도 남는다
        assertEquals(mapOf(StockField.DIA to "20"), r.stock) // 빈 소재 칸만 빠진다
        // 기본값 줄 · 빈 홀더 id · 빈 소재 칸만 다르면 견주기 꼴이 같다
        val b = ProgramSetup(mapOf(1 to ToolOffset(0.4, 3), 9 to ToolOffset()), mapOf(1 to "X|Y", 4 to ""), mapOf(StockField.DIA to "20", StockField.CHUCK to " "))
        assertEquals(a.canonical(), b.canonical())
        assertTrue(ProgramSetup(mapOf(2 to ToolOffset()), mapOf(2 to ""), mapOf(StockField.DIA to "")).isEmpty)
        // 보정 번호는 1~99 만 읽는다
        assertEquals(setOf(1, 99), ProgramSetup.decode("v1\ntools=0|0.4|3|TURN|C||||||;1|0.4|3|TURN|C||||||;99||||C||||||;100|0.4|3|TURN|C||||||\nend")!!.tools.keys)
    }

    @Test
    fun unreadableTextsAreNullNeverThrow() {
        for (bad in listOf(
            "v2\ntools=\nend",
            "v1\ntools=1|0.4|3|TURN|C|95.0", // 잘림(end 없음)
            "v1",
            "end",
            "",
            "\u0000\u0001zz\nend",
            "v1\ntools=\n" + "x".repeat(ProgramSetup.MAX_CHARS) + "\nend",
            "tools=\nv1\nend",
        )) {
            assertNull(ProgramSetup.decode(bad), bad.take(30))
        }
    }

    @Test
    fun lenientRowsAndFields() {
        val r = assertNotNull(
            ProgramSetup.decode(
                "v1\nfoo=bar\nno equals line\n" +
                    "tools=1|0.4|3|TURN|C|95.0;2|0.4|3|TURN|C|95.0||||;x|0.4|3|TURN|C||||||;4|0.2|2|LASER|Q|91.0|||||;6|0.2|2|TURN|T|91.0|||||9.0|extra|more\n" +
                    "holders=1=A|B;zz=C|D;2=;150=E|F;3\n" +
                    "stock=dia=20;bogus=1;len=abc;face=1234567890123456789;bore=0.00000000001;chuck=-5\n" +
                    "end",
            ),
        )
        // 8칸 미만 · 번호가 숫자 아닌 줄만 버림, 620edc6 의 10칸 줄 → 바 지름 빈칸, 모르는 종류 · 인서트 → 바이트 · C, 칸이 더 많으면 아는 칸만
        assertEquals(
            mapOf(
                2 to ToolOffset(0.4, 3, kappaDeg = 95.0),
                4 to ToolOffset(0.2, 2, kappaDeg = 91.0),
                6 to ToolOffset(0.2, 2, insert = InsertShape.T, kappaDeg = 91.0, barDia = 9.0),
            ),
            r.tools,
        )
        assertEquals(mapOf(1 to "A|B"), r.holders)
        // 키패드 글이 아닌 칸 · 17자 넘는 칸 · 모르는 열쇠는 버리고, 범위 밖 수는 그대로(화면이 빨간 칸으로)
        assertEquals(mapOf(StockField.DIA to "20", StockField.BORE to "0.00000000001", StockField.CHUCK to "-5"), r.stock)
    }

    @Test
    fun outOfRangeToolFieldsReadBlank() {
        val rows = listOf(
            "1|-1|12|TURN|C|0|0|180|0|0|", "2|NaN|-1|TURN|C|180|100.5|0|200.5|20.5|", "3|Infinity|3|TURN|C|-5|-1|-1|-1|-1|0",
            "4|51|9|TURN|C|179.9|100|179.9|200|20|500", "5|0|0|TURN|C|0.1|0.1|0.1|0.1|0.1|0.1",
        )
        val t = assertNotNull(ProgramSetup.decode("v1\ntools=${rows.joinToString(";")}\nend")).tools
        assertEquals(ToolOffset(), t[1])
        assertEquals(ToolOffset(), t[2])
        assertEquals(ToolOffset(tip = 3), t[3])
        assertEquals(ToolOffset(null, 9, kappaDeg = 179.9, width = 100.0, threadDeg = 179.9, dieDia = 200.0, diePitch = 20.0, barDia = 500.0), t[4])
        assertEquals(ToolOffset(0.0, 0, kappaDeg = 0.1, width = 0.1, threadDeg = 0.1, dieDia = 0.1, diePitch = 0.1, barDia = 0.1), t[5])
        // 저장소에 팁 12 가 든 작업 중 설정이 있어도 화면은 0~9 팁만 본다(팁 고르기 그림이 0~9 밖에서 던진다)
        val kv = store()
        kv.put(ProgramStore.DRAFT_SETUP, "v1\ntools=1|0.4|12|TURN|C|95.0|||||;2|0.4|-1|TURN|C|95.0|||||\nend")
        val s = SimState(kv)
        assertEquals(setOf(1, 2), s.tools.keys)
        assertTrue(s.tools.values.all { it.tip == null || it.tip in 0..9 })
    }

    @Test
    fun storeKeepsTextExactly() {
        val f = file()
        val text = ProgramSetup(toolsA, mapOf(3 to "S10K-STFCR09|대구텍", 1 to "PCLNL2525M12|한국야금,대구텍,샌드빅,미쓰비시"), mapOf(StockField.DIA to "20")).encode()
        KeyValueStore(f).put(ProgramStore.setupKey(1001), text)
        assertEquals(text, KeyValueStore(f).get(ProgramStore.setupKey(1001)))
    }

    /** ToolOffset 에 칸을 더하고 공구표 글에 안 넣으면 실패한다(칸이 모두 홑값일 때의 칸 세기) */
    @Test
    fun everyToolFieldIsWritten() {
        assertEquals(ToolOffset().toString().count { it == '=' } + 1, SimState.encodeTools(mapOf(1 to ToolOffset())).split('|').size)
        val all = ToolOffset(0.8, 2, ToolKind.THREAD, InsertShape.V, 93.0, 2.5, 60.0, 12.7, 1.75, 16.0)
        assertTrue(ToolOffset::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.all { f ->
            f.isAccessible = true
            f.get(all) != f.get(ToolOffset())
        })
        assertEquals(mapOf(4 to all), ProgramSetup.decode(ProgramSetup(mapOf(4 to all)).encode())!!.tools)
    }

    @Test
    fun holderIdsFitOneLine() {
        assertTrue(COMPANY_HOLDERS.all { h -> h.id.isNotEmpty() && h.id.none { it == ';' || it == '=' || it == '\n' } })
    }

    // ---- 상태(SimState)

    @Test
    fun reopenRestoresSetupSavedWithProgram() {
        val s = SimState(store())
        s.editor.insert("O1001 (BORE X6)\nT0303\nG0X6.Z2.\nM30")
        s.fill()
        s.requestSave()
        val tools = s.tools
        val holders = s.toolHolders
        val stock = s.stockText
        assertEquals(ToolOffset(), tools[7])

        s.tools = s.tools + (1 to ToolOffset(0.8, 3)) + (9 to ToolOffset(0.4, 2))
        s.pickHolder(3, null)
        s.setStock(StockField.DIA, "30")
        s.request(Pending.Load(1001))
        (s.ask as? SimAsk.Unsaved)?.let { s.perform(it.then) }
        assertEquals(tools, s.tools)
        assertEquals(holders, s.toolHolders)
        assertEquals(stock, s.stockText)
    }

    @Test
    fun workingSetupSurvivesRestart() {
        val f = file()
        val kv = KeyValueStore(f)
        val s = SimState(kv)
        s.fill()
        for (again in listOf(SimState(kv), SimState(KeyValueStore(f)))) {
            assertEquals(s.tools, again.tools)
            assertEquals(s.toolHolders, again.toolHolders)
            assertEquals(s.stockText, again.stockText)
        }
    }

    /** 620edc6 의 PC 저장 코드(Properties.store)가 실제로 쓴 꼴 — 공구 · 소재 열쇠가 없고, 지운 O0021 은 빈 값으로 남아 있다 */
    private val legacy = """
        #Wed Nov 11 02:11:00 KST 2026
        compMode=MACHINE
        sim/draft=O1001 (BORE X6)\nT0303\nG97S1500M03\nG0X6.Z2.\nG1Z-10.F.05\nG0X5.Z2.\nM30\n
        sim/draft/cursor=68
        sim/draft/from=p1001
        sim/keypad=0
        sim/machine=rx\=30;path\=DOGLEG;dec\=STANDARD;skip\=0;suv\=C
        sim/prog/1001=O1001 (BORE X6)\nT0303\nG97S1500M03\nG0X6.Z2.\nG1Z-10.F.05\nG0X5.Z2.\nM30
        sim/prog/1001/at=1794268800000
        sim/prog/21=
        sim/prog/21/at=
        sim/progs=1001
        sim/view=10111
        theme=dark
    """.trimIndent()

    private fun legacyStore(): Pair<File, KeyValueStore> {
        val f = file()
        f.writeText(legacy, Charsets.UTF_8)
        return f to KeyValueStore(f)
    }

    @Test
    fun legacyStoreOpensAndLeavesToolsAlone() {
        val (_, kv) = legacyStore()
        val s = SimState(kv)
        assertEquals(Origin.Saved(1001), s.origin)
        assertEquals("O1001 (BORE X6)\nT0303\nG97S1500M03\nG0X6.Z2.\nG1Z-10.F.05\nG0X5.Z2.\nM30\n", s.editor.text)
        assertEquals(listOf(1001), s.programs.numbers())
        assertNull(s.programs.load(21))
        assertNull(s.programs.setup(1001))
        assertEquals(emptyMap(), s.tools)
        assertEquals("30", s.machineText[MachineField.RAPID_X])
        // 공구를 넣고 예전 형식 프로그램을 열면 공구표는 그대로, 알림 없음
        s.tools = toolsB
        assertTrue(s.modified)
        assertNull(s.open(1001))
        assertEquals("O1001 (BORE X6)\nT0303\nG97S1500M03\nG0X6.Z2.\nG1Z-10.F.05\nG0X5.Z2.\nM30", s.editor.text)
        assertEquals(toolsB, s.tools)
        // 다시 '저장' 하면 그때부터 설정이 딸린다
        assertEquals("O1001 저장했습니다 — 공구 · 소재 설정도 함께", s.requestSave())
        assertTrue(kv.get(ProgramStore.setupKey(1001))!!.let { it.startsWith("v1\n") && it.endsWith("\nend") })
        s.tools = toolsA
        s.open(1001)
        assertEquals(toolsB, s.tools)
    }

    @Test
    fun brokenSetupKeepsTextAndTools() {
        for (bad in listOf("v9\ntools=1|0.4|3|TURN|C||||||\nend", "v1\ntools=1|0.4|3|TURN|C|95.0", "\u0000쓰레기")) {
            val kv = store()
            val s = SimState(kv)
            s.programs.save(1001, "O1001\nM30", bad)
            s.tools = toolsB
            assertEquals("O1001 열었습니다 — 저장된 공구 · 소재 설정을 읽지 못해 지금 설정을 그대로 둡니다", s.open(1001))
            assertEquals("O1001\nM30", s.editor.text)
            assertEquals(toolsB, s.tools)
            assertFalse(s.modified)
        }
        // 작업 중 설정 · 기준 설정이 깨져도 꺼지지 않는다
        val kv = store()
        kv.put(ProgramStore.DRAFT_SETUP, "v1\ntools=1|0.4|3")
        assertEquals(emptyMap(), SimState(kv).tools)
        val s = SimState(kv)
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.requestSave()
        kv.put(ProgramStore.DRAFT_BASE, "zzz")
        val again = SimState(kv)
        assertEquals(toolsA, again.tools)
        assertFalse(again.modified) // 깨진 기준 → 그 프로그램에 저장된 설정
    }

    @Test
    fun openToastOnlyWhenSetupChanges() {
        val s = SimState(store())
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.requestSave()
        s.perform(Pending.New)
        s.editor.insert("O1002\nM30")
        s.tools = toolsB
        s.setStock(StockField.DIA, "40")
        s.requestSave()
        assertEquals("O1001 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다", s.open(1001))
        assertEquals(emptyMap(), s.stockText) // 통째로 — 1001 은 소재 칸이 비어 있었다
        assertNull(s.open(1001)) // 같으면 알림 없음
        s.programs.save(5, "O0005\nM30") // 설정 없이(예전 형식과 같다)
        assertNull(s.open(5))
        assertEquals(toolsA, s.tools)
    }

    @Test
    fun deleteClearsSetup() {
        val kv = store()
        val s = SimState(kv)
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.requestSave()
        s.delete(1001)
        assertEquals("", kv.get(ProgramStore.setupKey(1001)))
        assertNull(s.programs.setup(1001))
        // 같은 번호를 설정 없이 다시 저장하면 옛 공구가 돌아오지 않는다
        s.programs.save(1001, "O1001\nM30")
        s.tools = toolsB
        s.perform(Pending.Load(1001))
        assertEquals(toolsB, s.tools)
    }

    @Test
    fun overwriteRenumberAndSaveTwice() {
        val s = SimState(store())
        s.editor.insert("O1001 (A)\nM30")
        s.tools = toolsA
        s.requestSave()
        // 다른 글이 같은 번호로 덮어쓰기
        s.perform(Pending.New)
        s.editor.insert("O1001 (B)\nM30")
        s.tools = toolsB
        assertNull(s.requestSave())
        assertEquals(SimAsk.Overwrite(1001), s.ask)
        s.save(1001)
        s.tools = emptyMap()
        s.perform(Pending.New)
        s.perform(Pending.Load(1001))
        assertEquals("O1001 (B)\nM30", s.editor.text)
        assertEquals(toolsB, s.tools)
        // 번호를 바꿔 저장: 새 번호에 글 + 지금 설정, 옛 번호는 그대로
        s.editor.replaceAll("O1002 (C)\nM30")
        s.tools = toolsA
        assertEquals("O1002 저장했습니다 — 공구 · 소재 설정도 함께", s.requestSave())
        s.perform(Pending.Load(1001))
        assertEquals("O1001 (B)\nM30" to toolsB, s.editor.text to s.tools)
        s.perform(Pending.Load(1002))
        assertEquals("O1002 (C)\nM30" to toolsA, s.editor.text to s.tools)
        // 같은 번호 두 번: 목록에 한 줄, 설정은 둘째 것
        s.tools = toolsB
        s.requestSave()
        assertEquals(listOf(1002, 1001), s.programs.numbers())
        assertEquals(toolsB, ProgramSetup.decode(s.programs.setup(1002)!!)!!.tools)
    }

    @Test
    fun setupChangesMarkModifiedOnlyForSavedProgram() {
        val s = SimState(store())
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.setStock(StockField.DIA, "20")
        s.requestSave()
        assertFalse(s.modified)
        s.tools = s.tools + (1 to ToolOffset(0.8, 3))
        assertTrue(s.modified && s.setupModified && !s.textModified)
        s.tools = toolsA
        assertFalse(s.modified)
        s.setStock(StockField.DIA, "25")
        assertTrue(s.modified)
        s.setStock(StockField.DIA, "20")
        assertFalse(s.modified)
        s.toolHolders = s.toolHolders + (3 to "S10K-STFCR09|대구텍")
        assertTrue(s.modified)
        s.toolHolders = s.toolHolders - 3
        assertFalse(s.modified)
        // 기본값 줄 · 빈 소재 칸은 없는 것과 같다
        s.tools = s.tools + (9 to ToolOffset())
        s.setStock(StockField.LENGTH, "")
        assertFalse(s.modified)
        // 예제 · 새 글은 글만 본다
        s.perform(Pending.Example(0))
        s.tools = s.tools + (1 to ToolOffset(1.2, 3))
        assertFalse(s.modified)
        s.perform(Pending.New)
        s.tools = toolsB
        assertFalse(s.modified)
    }

    @Test
    fun setupOnlyChangeAsksBeforeOpeningOther() {
        val s = SimState(store())
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.requestSave()
        s.perform(Pending.New)
        s.editor.insert("O1002\nM30")
        s.tools = toolsB
        s.requestSave()
        s.tools = s.tools + (1 to ToolOffset(1.6, 3))
        val now = s.tools
        assertNull(s.request(Pending.Load(1001)))
        assertIs<SimAsk.Unsaved>(s.ask)
        assertEquals("O1002\nM30" to now, s.editor.text to s.tools)
        assertEquals("O1001 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다", s.perform((s.ask as SimAsk.Unsaved).then))
        assertEquals("O1001\nM30" to toolsA, s.editor.text to s.tools)
    }

    @Test
    fun changedButUnsavedSetupSurvivesRestart() {
        val kv = store()
        val s = SimState(kv)
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.requestSave()
        s.tools = toolsB
        val again = SimState(kv)
        assertEquals(toolsB, again.tools)
        assertTrue(again.modified)
        again.open(1001)
        assertEquals(toolsA, again.tools)
        assertFalse(again.modified)
    }

    @Test
    fun newAndExampleKeepWorkingSetup() {
        val s = SimState(store())
        s.tools = toolsA
        s.setStock(StockField.DIA, "20")
        s.perform(Pending.New)
        assertEquals(toolsA, s.tools)
        assertEquals(mapOf(StockField.DIA to "20"), s.stockText)
        val i = exampleList.indexOfFirst { 2 in it.tools }
        s.pickHolder(2, holder("MVJNR2525M16"))
        s.perform(Pending.Example(i))
        assertEquals(toolsA + exampleList[i].tools, s.tools)
        assertNull(s.holderOf(2))
        assertEquals(mapOf(StockField.DIA to "20"), s.stockText)
    }

    @Test
    fun saverDoesNotCarryToolsStoreWins() {
        val kv = store()
        val s = SimState(kv)
        s.fill()
        s.openFind()
        s.editQuery("G01")
        s.speed = 50f
        s.singleBlock = true
        val saver = SimState.saver(kv)
        val r = saver.restore(assertNotNull(with(saver) { SaverScope { true }.save(s) }))!!
        assertEquals(s.setupNow(), r.setupNow())
        assertEquals("G01" to 50f, r.query to r.speed)
        assertTrue(r.singleBlock)
        // 620edc6 꼴 9칸 목록(0 · 4 · 8 번에 다른 공구 · 소재 · 홀더)을 받아도 저장소 값이 이긴다
        val old = listOf(SimState.encodeTools(toolsB), "", "0", "OFF", "dia=99", "10.0", "0", "0", "1=PCLNL2525M12|한국야금")
        val o = saver.restore(old)!!
        assertEquals(s.setupNow(), o.setupNow())
    }

    @Test
    fun bigTableRoundTrips() {
        val s = SimState(store())
        s.editor.insert("O1001\nM30")
        s.tools = (1..99).associateWith { ToolOffset(0.8, 2, ToolKind.THREAD, InsertShape.V, 93.25, 2.5, 60.0, 12.7, 1.75, 16.5) }
        s.toolHolders = (1..99).associateWith { COMPANY_HOLDERS[it % COMPANY_HOLDERS.size].id }
        val tools = s.tools
        s.requestSave()
        assertTrue(s.programs.setup(1001)!!.length < ProgramSetup.MAX_CHARS)
        s.tools = emptyMap()
        s.perform(Pending.New)
        s.perform(Pending.Load(1001))
        assertEquals(tools, s.tools)
        assertEquals(99, s.toolHolders.size)
    }

    @Test
    fun deletingOpenProgramKeepsTools() {
        val s = SimState(store())
        s.editor.insert("O1001\nM30")
        s.tools = toolsA
        s.requestSave()
        s.delete(1001)
        assertEquals(Origin.New, s.origin)
        assertEquals(toolsA, s.tools)
        assertTrue(s.modified)
    }

    @Test
    fun legacyProgramChangedStaysModifiedAfterSaverAndRestart() {
        val (f, kv) = legacyStore()
        val s = SimState(kv)
        assertFalse(s.setupModified) // 글은 고치던 중(임시 글 끝 줄 바꿈) — 설정만 본다
        s.tools = toolsB
        assertTrue(s.setupModified)
        val saver = SimState.saver(kv)
        assertTrue(saver.restore(assertNotNull(with(saver) { SaverScope { true }.save(s) }))!!.setupModified)
        assertTrue(SimState(KeyValueStore(f)).setupModified)
    }

    @Test
    fun saveToastMentionsSetupOnlyWhenThere() {
        val s = SimState(store())
        s.editor.insert("O1001\nM30")
        s.tools = mapOf(3 to ToolOffset())
        assertEquals("O1001 저장했습니다", s.requestSave())
        s.setStock(StockField.DIA, "20")
        assertEquals("O1001 저장했습니다 — 공구 · 소재 설정도 함께", s.requestSave())
    }

    @Test
    fun constructorToolsAreWritten() {
        val kv = store()
        assertEquals(toolsA, SimState(kv, toolsA).tools)
        assertEquals(toolsA, SimState(kv).tools)
    }

    @Test
    fun textsNoLongerSayOnlyWhileOn() {
        val help = SimHelp.flatMap { it.items }
        assertTrue(help.none { "공구표" in it && "켠 동안만" in it })
        assertTrue(help.any { "'저장' 은 그때의 공구표 · 소재 칸도 함께 저장" in it })
    }

    /**
     * PC 저장은 임시 파일에 다 쓴 뒤 바꿔치기한다(검증 V7 — 공구 칸 한 글자마다 설정 파일을 그 자리에서 통째로 다시 써, 쓰는 도중 꺼지면 저장한 프로그램을 다 잃을 수 있었다).
     * 새 글을 다 쓰지 못하면(여기선 임시 파일 자리를 폴더로 막음) 예전 파일이 그대로 남고, 다 쓰면 임시 파일이 남지 않는다.
     */
    @Test
    fun pcStoreReplacesFileWhole() {
        val f = file()
        val kv = KeyValueStore(f)
        kv.put("sim/prog/1001", "O1001\nM30")
        val before = f.readBytes()
        val tmp = File(f.path + ".tmp")
        assertFalse(tmp.exists(), "다 쓰면 임시 파일이 남지 않음")
        assertTrue(tmp.mkdir())
        try {
            runCatching { kv.put("sim/draft/setup", "v1\nend") }
            assertTrue(before.contentEquals(f.readBytes()), "새 글을 다 쓰지 못하면 예전 파일 그대로")
        } finally {
            tmp.delete()
        }
        kv.put("sim/draft/setup", "v1\nend")
        val again = KeyValueStore(f)
        assertEquals("O1001\nM30", again.get("sim/prog/1001"))
        assertEquals("v1\nend", again.get("sim/draft/setup"))
        assertFalse(tmp.exists())
    }
}
