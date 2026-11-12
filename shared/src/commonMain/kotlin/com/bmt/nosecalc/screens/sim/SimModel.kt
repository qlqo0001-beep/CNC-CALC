package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.GText
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimExample
import com.bmt.nosecalc.calc.sim.SimExamples
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.sim.ToolShape

/** 저장한 프로그램 한 줄(목록) */
internal data class ProgramInfo(val number: Int, val comment: String?, val lines: Int, val savedAt: Long?)

/**
 * 프로그램 저장소(설계 7.3) — 앱 설정과 같은 [KeyValueStore]. 열쇠: `sim/prog/<번호>` = 글, `sim/prog/<번호>/at` = 저장 시각(ms),
 * `sim/prog/<번호>/setup` = 그 프로그램에 딸린 공구표 · 홀더 · 소재 설정([ProgramSetup] 글 — 없음 · 빈칸 = 설정 없음, 2026-11-10 피드백 3 전에 저장한 것),
 * `sim/progs` = 번호 목록(최근 저장 먼저, 쉼표). KeyValueStore 에 지우기가 없어 지운 프로그램은 목록에서 빼고 글 · 설정을 빈칸으로 덮는다.
 */
internal class ProgramStore(private val kv: KeyValueStore) {
    fun numbers(): List<Int> = kv.get(LIST)?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    fun exists(n: Int): Boolean = n in numbers()

    fun load(n: Int): String? = if (exists(n)) kv.get(key(n)) else null

    /** 그 프로그램에 딸린 설정 글. null · 빈칸 = 설정 없음 */
    fun setup(n: Int): String? = if (exists(n)) kv.get(setupKey(n)) else null

    /** 글 → 설정 → 시각 → 목록(새 번호는 목록이 마지막 — 도중에 꺼져도 반쪽이 목록에 뜨지 않는다). 설정을 안 주면 빈칸(덮은 번호의 옛 설정이 살아나지 않게) */
    fun save(n: Int, text: String, setup: String = "") {
        kv.put(key(n), text)
        kv.put(setupKey(n), setup)
        kv.put(atKey(n), nowMillis().toString())
        kv.put(LIST, (listOf(n) + numbers().filter { it != n }).joinToString(","))
    }

    fun delete(n: Int) {
        kv.put(LIST, numbers().filter { it != n }.joinToString(","))
        kv.put(key(n), "")
        kv.put(setupKey(n), "")
        kv.put(atKey(n), "")
    }

    fun list(): List<ProgramInfo> = numbers().mapNotNull { n ->
        load(n)?.let { ProgramInfo(n, firstComment(it), it.count { ch -> ch == '\n' } + 1, kv.get(atKey(n))?.toLongOrNull()) }
    }

    companion object {
        const val LIST = "sim/progs"
        const val DRAFT = "sim/draft"
        const val DRAFT_CURSOR = "sim/draft/cursor"
        /** 임시 글이 무엇에서 왔나: "p<번호>" 저장한 프로그램 · "e<예제 번호>" 예제 · 빈칸 새 글 */
        const val DRAFT_FROM = "sim/draft/from"
        const val KEYPAD = "sim/keypad"

        /** 작업 중 공구표 · 홀더 · 소재 설정([ProgramSetup] — 바뀔 때마다) · '고침' 을 가르는 기준 설정(저장한 프로그램을 연 때만, 빈칸 = 견주지 않음) */
        const val DRAFT_SETUP = "sim/draft/setup"
        const val DRAFT_BASE = "sim/draft/base"

        /** 기계 설정 · 프로그램 읽기(앱 전체 하나 — 설계 7.3) · 보기 스위치 */
        const val MACHINE = "sim/machine"
        const val VIEW = "sim/view"
        const val MAX_CHARS = 100_000
        const val MAX_PROGRAMS = 200

        fun key(n: Int) = "sim/prog/$n"
        fun atKey(n: Int) = "sim/prog/$n/at"
        fun setupKey(n: Int) = "sim/prog/$n/setup"

        /** 첫 주석 글(괄호 안) — 목록 · 프로그램 줄의 이름 */
        fun firstComment(text: String): String? = Regex("""\(([^)\n]*)\)""").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        /** O 번호 글(O0021 처럼 4자리 이상) */
        fun oText(n: Int) = "O" + n.toString().padStart(4, '0')
    }
}

/** 편집기 글이 어디서 왔나 — 프로그램 줄의 '저장됨 · 예제 · 고침' */
internal sealed interface Origin {
    data object New : Origin
    data class Saved(val number: Int) : Origin
    data class Example(val index: Int) : Origin
}

internal enum class SimSheet { PROGRAMS, MORE, ISSUES }

/** 저장하지 않은 글이 있을 때 미뤄 둔 일 */
internal sealed interface Pending {
    data class Load(val number: Int) : Pending
    data class Example(val index: Int) : Pending
    data object New : Pending
}

/** 묻는 창 */
internal sealed interface SimAsk {
    /** O 번호가 없어 저장 이름을 묻는다 */
    data object Number : SimAsk
    data class Overwrite(val number: Int) : SimAsk
    data class Unsaved(val then: Pending) : SimAsk
    data class Delete(val number: Int) : SimAsk
}

/**
 * 시뮬레이터 편집기 화면 상태(설계 8.6 `SimState`): 글(임시 저장 `sim/draft`) · 저장소 · 키패드 · 찾기 · 공구표 · 마지막 실행 결과 · 시트 · 묻는 창.
 * 공구표 · 홀더 이름표 · 소재 칸은 바뀔 때마다 `sim/draft/setup` 에 써서 앱을 꺼도 남고, 프로그램 '저장' 에 딸려 간다(2026-11-10 피드백 3 — C10 을 뒤집음, 설계 20.2).
 * [startTools] 를 주면 그 공구표로 시작한다(null = 저장소의 작업 중 값).
 */
@Stable
internal class SimState(private val kv: KeyValueStore, startTools: Map<Int, ToolOffset>? = null) {
    val programs = ProgramStore(kv)
    val editor = EditorState(kv.get(ProgramStore.DRAFT) ?: "", kv.get(ProgramStore.DRAFT_CURSOR)?.toIntOrNull() ?: 0)

    var origin: Origin by mutableStateOf(readOrigin())
        private set

    /** [origin] 의 글(저장한 글 · 예제 글). 편집기 글과 다르면 '고침' */
    private var baseline: String by mutableStateOf(baselineOf(origin))

    /** 글이 저장한 글 · 예제 글과 다르다 */
    val textModified: Boolean get() = editor.text != baseline

    /** 저장한 프로그램을 연 뒤 공구표 · 홀더 · 소재 칸을 고쳤다(견주기 꼴 — 기본값 줄 · 빈 칸은 없는 것과 같다). 예제 · 새 글은 보지 않는다 */
    val setupModified: Boolean get() = origin is Origin.Saved && setupNow().canonical() != baseSetup

    /** '● 고침' — 글이, 저장한 프로그램이면 설정도 저장 때와 다르다 */
    val modified: Boolean get() = textModified || setupModified

    var keypadOpen: Boolean
        get() = keypad
        set(v) {
            keypad = v
            kv.put(ProgramStore.KEYPAD, if (v) "1" else "0")
        }
    private var keypad by mutableStateOf(kv.get(ProgramStore.KEYPAD) != "0")

    var shift by mutableStateOf(ShiftState.OFF)
    var findOpen by mutableStateOf(false)
    var query by mutableStateOf("")
        private set

    /** 찾는 글이 없음(찾기 줄에 '없음') */
    var findMiss by mutableStateOf(false)
        private set

    /** 작업 중 설정(저장소 — 없거나 읽지 못하면 빈 것) */
    private val draftSetup = kv.get(ProgramStore.DRAFT_SETUP)?.let(ProgramSetup::decode) ?: ProgramSetup()

    /** 공구표(보정 번호 → 값). 넣을 때마다 `sim/draft/setup` 에 쓴다 */
    var tools: Map<Int, ToolOffset>
        get() = toolsState
        set(v) {
            toolsState = v
            saveSetup()
        }
    private var toolsState by mutableStateOf(startTools ?: draftSetup.tools)

    /** 공구 설정에서 고른 회사 홀더(보정 번호 → [CompanyHolder.id]). 칸 값은 [tools] 에 있고 이것은 이름표뿐 — 공구표와 같이 저장한다 */
    var toolHolders: Map<Int, String>
        get() = holdersState
        set(v) {
            holdersState = v
            saveSetup()
        }
    private var holdersState by mutableStateOf(draftSetup.holders)

    fun holderOf(n: Int): CompanyHolder? = toolHolders[n]?.let { id -> COMPANY_HOLDERS.firstOrNull { it.id == id } }

    /** 홀더를 고르면 그 보정 번호의 종류 · 인서트 · κr · 바 지름을 채운다([CompanyHolder.fill]). null = 고르지 않음(칸 값은 그대로) */
    fun pickHolder(n: Int, h: CompanyHolder?) {
        if (h == null) {
            toolHolders = toolHolders - n
            return
        }
        holdersState = holdersState + (n to h.id)
        tools = tools + (n to h.fill(tools[n] ?: ToolOffset()))
    }

    // ---- 시뮬레이션 설정(설계 7.8 — 궤적 창). 소재는 공구표와 같이 저장(설계 20.2), 기계 · 읽기 · 보기는 앱 전체 하나(`sim/machine` · `sim/view`)

    /** 소재 칸 글(빈칸 = 자동). 넣을 때마다 `sim/draft/setup` 에 쓴다 */
    var stockText: Map<StockField, String>
        get() = stockState
        set(v) {
            stockState = v
            saveSetup()
        }
    private var stockState by mutableStateOf(draftSetup.stock)

    init {
        if (startTools != null) saveSetup()
    }

    /** '고침' 을 가르는 기준 설정(견주기 꼴). 저장한 프로그램을 열거나 저장할 때 지금 설정으로 — `sim/draft/base` 에 남겨 다시 켜도 같다 */
    private var baseSetup: ProgramSetup by mutableStateOf(initBase())

    /** 기계 칸 글(빈칸 = 가정) */
    var machineText: Map<MachineField, String> by mutableStateOf(readMachine())
        private set
    var rapidPath: RapidPath by mutableStateOf(readEnum(MACHINE_PATH, RapidPath.DOGLEG))
        private set
    var decimal: DecimalMode by mutableStateOf(readEnum(MACHINE_DECIMAL, DecimalMode.STANDARD))
        private set
    var blockSkip: Boolean by mutableStateOf(SetupText.decode(kv.get(ProgramStore.MACHINE))[MACHINE_SKIP] == "1")
        private set

    /** 보정 시작 · 취소 형식(No.5003#1 SUV) — 처음 형식 C(회사 기계) */
    var compStartup: CompStartup by mutableStateOf(readEnum(MACHINE_SUV, CompStartup.C))
        private set
    var view: ViewFlags by mutableStateOf(ViewFlags.decode(kv.get(ProgramStore.VIEW)))
        private set

    /** 재생 속도 배수 · 싱글 블록 · M01(앱을 켠 동안) */
    var speed by mutableFloatStateOf(10f)
    var singleBlock by mutableStateOf(false)
    var optStop by mutableStateOf(false)

    fun setMachine(f: MachineField, v: String) {
        machineText = machineText + (f to v)
        saveMachine()
    }

    fun changeRapidPath(p: RapidPath) {
        rapidPath = p
        saveMachine()
    }

    fun changeDecimal(d: DecimalMode) {
        decimal = d
        saveMachine()
    }

    fun changeBlockSkip(on: Boolean) {
        blockSkip = on
        saveMachine()
    }

    fun changeCompStartup(v: CompStartup) {
        compStartup = v
        saveMachine()
    }

    fun changeView(v: ViewFlags) {
        view = v
        kv.put(ProgramStore.VIEW, v.encode())
    }

    fun setStock(f: StockField, v: String) {
        stockText = stockText + (f to v)
    }

    private fun readMachine(): Map<MachineField, String> {
        val m = SetupText.decode(kv.get(ProgramStore.MACHINE))
        return MachineField.entries.mapNotNull { f -> m[f.key]?.let { f to it } }.toMap()
    }

    private inline fun <reified E : Enum<E>> readEnum(key: String, d: E): E =
        SetupText.decode(kv.get(ProgramStore.MACHINE))[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: d

    private fun saveMachine() {
        val m = machineText.entries.associate { it.key.key to it.value } +
            mapOf(MACHINE_PATH to rapidPath.name, MACHINE_DECIMAL to decimal.name, MACHINE_SKIP to if (blockSkip) "1" else "0", MACHINE_SUV to compStartup.name)
        kv.put(ProgramStore.MACHINE, SetupText.encode(m))
    }

    /** 지금 설정 한 벌(백그라운드 실행에 넘김) */
    fun runSetup(): RunSetup {
        fun mv(f: MachineField) = fieldValue(machineText[f], machineOk(f)).orNull()
        fun sv(f: StockField) = fieldValue(stockText[f], stockOk(f)).orNull()
        return RunSetup(
            machineOf(machineText, rapidPath, decimal, blockSkip, compStartup), mv(MachineField.HOME_X), mv(MachineField.HOME_Z), sv(StockField.DIA), sv(StockField.FACE),
            mv(MachineField.HOME2_X), mv(MachineField.HOME2_Z),
        )
    }

    /** 마지막 실행 결과(줄을 끝내고 0.3초 뒤 다시 — 화면이 [runOf] 를 검사 글([EditorState.checkedText])로 백그라운드에서 불러 넣는다) */
    var run: SimRun by mutableStateOf(runOf(editor.text, tools))

    /** [run] 을 만든 글 · 공구표 · 설정(궤적 창을 열 때 낡았으면 바로 다시 돈다) */
    var runKey: Any? = null

    /** 궤적 창이 열려 있나(도움말이 '궤적 창 조작' 부터 — 설계 7.11) */
    var trajOpen by mutableStateOf(false)

    /** 0.3초를 기다리지 않고 지금 글 · 설정으로(궤적 창을 열 때 — 고친 직후 눌러도 그 글을 보이게). 쓰던 줄도 끝내고 글 전체로 돈다 */
    fun ensureRun() {
        editor.finishLine()
        val setup = runSetup()
        val k = Triple(editor.text, tools, setup)
        if (runKey != k) {
            run = runOf(editor.text, tools, setup)
            runKey = k
        }
        editor.ran(editor.checkGen)
    }

    var sheet: SimSheet? by mutableStateOf(null)
    var ask: SimAsk? by mutableStateOf(null)

    /** 저장 목록이 바뀐 횟수(목록 시트가 다시 읽는다) */
    var listVersion by mutableIntStateOf(0)
        private set

    /**
     * 실행. 원점 칸이 빈칸이면(설계 4.1 자동 — 소재 Ø · 끝면에서) 한 번 돌려 자동 소재를 보고, 원점이 다르면 그 원점으로 다시 돈다
     * (자동 소재는 이송 이동만 보므로 원점과 상관없다).
     */
    fun runOf(text: String, tools: Map<Int, ToolOffset>, setup: RunSetup = runSetup()): SimRun {
        val first = GSim.run(SimInput(text, setup.machineAt(setup.home(null)), tools))
        if (setup.homeX != null && setup.homeZ != null) return first
        val home = setup.home(StockAuto.of(first))
        return if (home == first.start) first else GSim.run(SimInput(text, setup.machineAt(home), tools))
    }

    fun saveDraft() {
        kv.put(ProgramStore.DRAFT, editor.text)
        kv.put(ProgramStore.DRAFT_CURSOR, editor.cursor.toString())
        kv.put(
            ProgramStore.DRAFT_FROM,
            when (val o = origin) {
                Origin.New -> ""
                is Origin.Saved -> "p${o.number}"
                is Origin.Example -> "e${o.index}"
            },
        )
    }

    private fun readOrigin(): Origin {
        val f = kv.get(ProgramStore.DRAFT_FROM) ?: return Origin.New
        val n = f.drop(1).toIntOrNull() ?: return Origin.New
        return when {
            f.startsWith("p") && programs.exists(n) -> Origin.Saved(n)
            f.startsWith("e") && n in exampleList.indices -> Origin.Example(n)
            else -> Origin.New
        }
    }

    private fun baselineOf(o: Origin): String = when (o) {
        Origin.New -> ""
        is Origin.Saved -> programs.load(o.number) ?: ""
        is Origin.Example -> exampleList[o.index].text
    }

    private fun set(text: String, o: Origin) {
        editor.replaceAll(text)
        origin = o
        baseline = baselineOf(o)
        rebase()
        findMiss = false
        saveDraft()
    }

    // ---- 공구표 · 홀더 · 소재 설정 한 벌(설계 20.2)

    fun setupNow(): ProgramSetup = ProgramSetup(tools, toolHolders, stockText)

    private fun saveSetup() = kv.put(ProgramStore.DRAFT_SETUP, setupNow().encode())

    /** 설정을 통째로 바꾸고 한 번만 쓴다 */
    private fun applySetup(s: ProgramSetup) {
        toolsState = s.tools
        holdersState = s.holders
        stockState = s.stock
        saveSetup()
    }

    /** 기준 설정 = 지금. 저장한 프로그램일 때만 `sim/draft/base` 에 남긴다(그 밖은 빈칸 = 견주지 않음) */
    private fun rebase() {
        baseSetup = setupNow().canonical()
        kv.put(ProgramStore.DRAFT_BASE, if (origin is Origin.Saved) baseSetup.encode() else "")
    }

    /** 처음 기준 설정: 남긴 기준 → (이 기능 전의 저장소 · 깨진 기준이면) 그 프로그램에 저장된 설정 → 지금(= 고침 아님). 찾아낸 기준은 남겨 다음에 켜도 같게 */
    private fun initBase(): ProgramSetup {
        val n = (origin as? Origin.Saved)?.number ?: return setupNow().canonical()
        kv.get(ProgramStore.DRAFT_BASE)?.takeIf { it.isNotEmpty() }?.let(ProgramSetup::decode)?.let { return it.canonical() }
        val b = (programs.setup(n)?.takeIf { it.isNotEmpty() }?.let(ProgramSetup::decode) ?: setupNow()).canonical()
        kv.put(ProgramStore.DRAFT_BASE, b.encode())
        return b
    }

    /** 그 프로그램에 딸린 설정으로 통째로 바꾼다(설정 없음 = 예전 형식 → 그대로). 돌려주는 글 = 토스트 */
    private fun loadSetup(n: Int): String? {
        val raw = programs.setup(n)?.takeIf { it.isNotEmpty() } ?: return null
        val o = ProgramStore.oText(n)
        val s = ProgramSetup.decode(raw) ?: return "$o 열었습니다 — 저장된 공구 · 소재 설정을 읽지 못해 지금 설정을 그대로 둡니다"
        val changed = s.canonical() != setupNow().canonical()
        applySetup(s)
        return if (changed) "$o 열었습니다 — 공구 · 소재 설정도 저장된 값으로 바꿨습니다" else null
    }

    // ---- 저장 · 불러오기(설계 7.3). 돌려주는 글 = 토스트

    /** '저장': 첫 O 번호를 이름으로. 없으면 번호를 묻고, 다른 프로그램과 번호가 같으면 덮어쓸지 묻는다 */
    fun requestSave(): String? {
        if (editor.text.length > ProgramStore.MAX_CHARS) return "프로그램이 너무 깁니다 — ${ProgramStore.MAX_CHARS / 10_000}만 자까지 저장합니다"
        val n = GText.parse(editor.text).units.first().number
        when {
            n == null -> ask = SimAsk.Number
            n != (origin as? Origin.Saved)?.number && programs.exists(n) -> ask = SimAsk.Overwrite(n)
            else -> return save(n)
        }
        return null
    }

    /** 번호를 받아 첫 줄에 O 번호를 넣고 저장 */
    fun saveAs(n: Int): String? {
        ask = null
        val c = editor.cursor
        val line = ProgramStore.oText(n) + "\n"
        editor.moveTo(0)
        editor.insert(line)
        editor.moveTo(c + line.length)
        return requestSave()
    }

    /** 덮어쓰기까지 정한 저장 */
    fun save(n: Int): String {
        ask = null
        if (!programs.exists(n) && programs.numbers().size >= ProgramStore.MAX_PROGRAMS) {
            return "프로그램은 ${ProgramStore.MAX_PROGRAMS}개까지 — 목록에서 지운 뒤 저장하세요"
        }
        val setup = setupNow()
        programs.save(n, editor.text, setup.encode())
        origin = Origin.Saved(n)
        baseline = editor.text
        rebase()
        listVersion++
        saveDraft()
        return "${ProgramStore.oText(n)} 저장했습니다" + if (setup.isEmpty) "" else " — 공구 · 소재 설정도 함께"
    }

    /** 다른 글로 바꾸기 전: 저장하지 않은 글(빈칸은 잃을 것이 없다) · 설정이 있으면 묻는다. 돌려주는 글 = 토스트([perform]) */
    fun request(p: Pending): String? {
        if (textModified && editor.text.isNotBlank() || setupModified) {
            ask = SimAsk.Unsaved(p)
            return null
        }
        return perform(p)
    }

    /** 돌려주는 글 = 토스트(저장한 프로그램을 열며 공구 · 소재 설정을 바꿨거나 읽지 못했을 때) */
    fun perform(p: Pending): String? {
        ask = null
        sheet = null
        when (p) {
            is Pending.Load -> {
                val text = programs.load(p.number) ?: return null
                val note = loadSetup(p.number)
                set(text, Origin.Saved(p.number))
                return note
            }
            is Pending.Example -> exampleList.getOrNull(p.index)?.let {
                holdersState = holdersState - it.tools.keys
                tools = tools + it.tools
                set(it.text, Origin.Example(p.index))
            }
            Pending.New -> set("", Origin.New)
        }
        return null
    }

    fun delete(n: Int): String {
        ask = null
        programs.delete(n)
        if ((origin as? Origin.Saved)?.number == n) {
            origin = Origin.New
            baseline = ""
            rebase()
            saveDraft()
        }
        listVersion++
        return "${ProgramStore.oText(n)} 을 지웠습니다"
    }

    // ---- 키패드 · 찾기

    /** 키패드 키: 찾기 줄이 열려 있으면 찾는 글로, 아니면 편집기로 */
    fun key(k: CncKey) {
        if (findOpen) {
            when (k) {
                is CncKey.Text -> editQuery(query + EditorState.clean(k.s).replace("\n", ""))
                CncKey.Back -> editQuery(query.dropLast(1))
                CncKey.Enter -> findNext(true)
            }
            return
        }
        when (k) {
            is CncKey.Text -> editor.insert(k.s)
            CncKey.Back -> editor.backspace()
            CncKey.Enter -> editor.newline()
        }
    }

    fun press(k: PadKey) {
        val (key, next) = pressKey(k, shift)
        shift = next
        key(key)
    }

    fun openFind() {
        editor.selectedText()?.takeIf { it.length <= 40 && '\n' !in it }?.let { query = it }
        findOpen = true
        findMiss = false
    }

    /** 찾는 글을 고치면 지금 자리(고른 글의 처음)부터 다시 찾는다 */
    fun editQuery(q: String) {
        query = q
        if (q.isEmpty()) {
            findMiss = false
            return
        }
        val from = editor.selection?.first ?: editor.cursor
        val r = editor.find(q, from, true)
        findMiss = r == null
        r?.let { editor.select(it) }
    }

    fun findNext(forward: Boolean) {
        val sel = editor.selection
        val from = if (forward) sel?.let { it.last + 1 } ?: editor.cursor else sel?.first ?: editor.cursor
        val r = editor.find(query, from, forward)
        findMiss = query.isNotEmpty() && r == null
        r?.let { editor.select(it) }
    }

    /** 그 줄 처음으로 커서(궤적 창의 '편집기로') */
    fun goToLine(line: Int) {
        if (line <= 0) return
        editor.moveTo(editor.lineStart((line - 1).coerceAtMost(editor.lineCount - 1)))
    }

    /** 문제 목록에서 고른 줄로 커서(낱말이 있으면 그 처음) */
    fun goTo(i: SimIssue) {
        if (i.line <= 0) return
        val line = (i.line - 1).coerceAtMost(editor.lineCount - 1)
        val start = editor.lineStart(line)
        editor.moveTo(start + (i.cols?.first ?: 0).coerceAtMost(editor.lineEnd(line) - start))
        sheet = null
    }

    companion object {
        private const val MACHINE_PATH = "path"
        private const val MACHINE_DECIMAL = "dec"
        private const val MACHINE_SKIP = "skip"
        private const val MACHINE_SUV = "suv"

        /**
         * 화면을 떠났다 와도(앱을 켠 동안) 찾기 · SHIFT · 재생 속도 · 싱글 블록 · M01 을 남긴다. 글 · 커서 · 공구표 · 홀더 이름표 · 소재 칸은 저장소에서 다시 읽는다
         * (프로세스가 죽었다 살 때 낡은 상태 묶음이 더 새 저장소 값을 덮지 않게 — 설계 20.2). 0 · 4 · 8 번 칸은 옛 목록과 차례를 맞추려 빈칸으로 둔다
         */
        fun saver(kv: KeyValueStore): Saver<SimState, Any> = Saver(
            save = { s ->
                s.saveDraft()
                arrayListOf(
                    "", s.query, if (s.findOpen) "1" else "0", s.shift.name, "", s.speed.toString(),
                    if (s.singleBlock) "1" else "0", if (s.optStop) "1" else "0", "",
                )
            },
            restore = { v ->
                @Suppress("UNCHECKED_CAST")
                val l = v as List<String>
                SimState(kv).apply {
                    query = l[1]
                    findOpen = l[2] == "1"
                    shift = ShiftState.valueOf(l[3])
                    if (l.size >= 8) {
                        speed = l[5].toFloatOrNull() ?: speed
                        singleBlock = l[6] == "1"
                        optStop = l[7] == "1"
                    }
                }
            },
        )

        /**
         * 공구표 글: 보정 번호마다 `n|노즈R|팁|종류|인서트|κr|폭|나사각|다이스 지름|다이스 피치|바 지름` 을 ; 로 잇는다(빈칸 = 없음).
         * 읽기는 다이스 칸이 없던 옛 8칸 글 · 바 지름 칸이 없던 10칸 글도 받는다(종류는 이름 — 모르는 이름은 바이트, 범위 밖 바 지름은 빈칸 = 자동)
         */
        fun encodeTools(t: Map<Int, ToolOffset>): String = t.entries.sortedBy { it.key }.joinToString(";") { (n, o) ->
            listOf(n, o.noseR, o.tip, o.kind.name, o.insert.name, o.kappaDeg, o.width, o.threadDeg, o.dieDia, o.diePitch, o.barDia).joinToString("|") { it?.toString() ?: "" }
        }

        fun decodeTools(s: String): Map<Int, ToolOffset> = s.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split('|')
            if (f.size < 8) return@mapNotNull null
            val n = f[0].toIntOrNull() ?: return@mapNotNull null
            n to ToolOffset(
                noseR = f[1].toDoubleOrNull(),
                tip = f[2].toIntOrNull(),
                kind = ToolKind.entries.firstOrNull { it.name == f[3] } ?: ToolKind.TURN,
                insert = InsertShape.entries.firstOrNull { it.name == f[4] } ?: InsertShape.C,
                kappaDeg = f[5].toDoubleOrNull(),
                width = f[6].toDoubleOrNull(),
                threadDeg = f[7].toDoubleOrNull(),
                dieDia = f.getOrNull(8)?.toDoubleOrNull(),
                diePitch = f.getOrNull(9)?.toDoubleOrNull(),
                barDia = f.getOrNull(10)?.toDoubleOrNull()?.takeIf(ToolShape::barOk),
            )
        }.toMap()
    }
}

/** 예제 목록(calc `SimExamples.all`) */
internal val exampleList: List<SimExample> get() = SimExamples.all

/** 앱 설정 저장소를 쓰는 화면 상태. 화면을 떠났다 와도 남는다 */
@Composable
internal fun rememberSimState(kv: KeyValueStore): SimState = rememberSaveable(kv, saver = SimState.saver(kv)) { SimState(kv) }
