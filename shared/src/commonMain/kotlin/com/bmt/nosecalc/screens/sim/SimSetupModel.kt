package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.calc.sim.ToolOffset

/** 시뮬레이션 설정의 소재 칸(설계 7.8 '소재') */
internal enum class StockField(val key: String) { DIA("dia"), LENGTH("len"), FACE("face"), BORE("bore"), CHUCK("chuck") }

/** 시뮬레이션 설정의 기계 칸(설계 7.8 '기계' · '프로그램 읽기' 의 숫자 칸) — 단위는 화면 그대로(m/min · 초 · rpm · 직경 X). HOME2 = G30 제2 원점 */
internal enum class MachineField(val key: String) {
    RAPID_X("rx"), RAPID_Z("rz"), TOOL_CHANGE("tc"), MAX_RPM("rpm"), HOME_X("hx"), HOME_Z("hz"), HOME2_X("h2x"), HOME2_Z("h2z"), G92_CHAMFER("g92"),
}

/** 보기 스위치(설계 7.8 '보기') — 처음: 고스트 · 급송 · 황삭 층 · 소재 칠하기 켬, 노즈 중심 경로 끔 */
internal data class ViewFlags(
    val ghost: Boolean = true,
    val nose: Boolean = false,
    val rapid: Boolean = true,
    val layers: Boolean = true,
    val fill: Boolean = true,
) {
    fun encode(): String = listOf(ghost, nose, rapid, layers, fill).joinToString("") { if (it) "1" else "0" }

    companion object {
        fun decode(s: String?): ViewFlags {
            if (s == null || s.length != 5 || s.any { it != '0' && it != '1' }) return ViewFlags()
            return ViewFlags(s[0] == '1', s[1] == '1', s[2] == '1', s[3] == '1', s[4] == '1')
        }
    }
}

/**
 * 한 번의 실행에 쓰는 설정(화면 상태를 백그라운드 스레드에서 읽지 않게 미리 뽑아 넘긴다). [homeX] · [homeZ] null = 자동(설계 4.1 —
 * X max(200, 소재 Ø + 100) · Z 끝면 + 100), [stockDia] · [stockFace] = 소재 칸(null = 자동 — 원점 자동 값에 쓴다),
 * [home2X] · [home2Z] = G30 제2 원점 칸(null = 그 축은 원점과 같음).
 */
internal data class RunSetup(
    val machine: MachineSetup,
    val homeX: Double? = null,
    val homeZ: Double? = null,
    val stockDia: Double? = null,
    val stockFace: Double? = null,
    val home2X: Double? = null,
    val home2Z: Double? = null,
) {
    /** G30 제2 원점(공작물 좌표, x 반경) — 칸이 둘 다 빈칸이면 null(= [home]) */
    fun home2(home: Pt): Pt? =
        if (home2X == null && home2Z == null) null else Pt(home2Z ?: home.z, home2X?.let { it / 2 } ?: home.x)

    /** 원점 [home] 으로 실행할 기계 설정(제2 원점도 그 원점에 맞춘다) */
    fun machineAt(home: Pt): MachineSetup = machine.copy(home = home, home2 = home2(home))

    /** 원점(공작물 좌표, x 반경). auto = 첫 실행의 자동 소재 */
    fun home(auto: StockSpec?): Pt {
        val dia = stockDia ?: auto?.dia
        val face = stockFace ?: auto?.faceZ
        val a = if (dia != null && face != null) StockAuto.home(dia, face) else MachineSetup().home
        return Pt(homeZ ?: a.z, homeX?.let { it / 2 } ?: a.x)
    }
}

/** 설정 글: `열쇠=값;…`(값에 ; = 없음) */
internal object SetupText {
    fun encode(m: Map<String, String>): String = m.entries.filter { it.value.isNotEmpty() }.joinToString(";") { "${it.key}=${it.value}" }

    fun decode(s: String?): Map<String, String> = s.orEmpty().split(';').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
    }.toMap()
}

/**
 * 프로그램에 딸린 설정 한 벌(2026-11-10 피드백 3 — 설계 20.2): 공구표 · 고른 홀더 이름표 · 소재 칸. '저장' 은 `sim/prog/<번호>/setup` 에,
 * 작업 중 값은 `sim/draft/setup` 에 쓴다. 글은 첫 줄 `v1` 과 끝 줄 `end` 사이에 `tools=`([SimState.encodeTools]) · `holders=번호=홀더 id;…` · `stock=`([SetupText]).
 * 쓰기는 값 그대로(읽으면 `==` — 소재는 빈 칸만 빠짐), '고침' · "바뀌었나" 는 [canonical] 끼리 견준다.
 */
internal data class ProgramSetup(
    val tools: Map<Int, ToolOffset> = emptyMap(),
    val holders: Map<Int, String> = emptyMap(),
    val stock: Map<StockField, String> = emptyMap(),
) {
    fun encode(): String = listOf(
        VERSION,
        "tools=" + SimState.encodeTools(tools),
        "holders=" + holders.entries.filter { oneLine(it.value) }.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" },
        "stock=" + SetupText.encode(stock.entries.filter { oneLine(it.value) }.sortedBy { it.key }.associate { it.key.key to it.value }),
        END,
    ).joinToString("\n")

    /** 견주기 꼴: 기본값 공구 줄(없는 줄과 같게 돈다) · 빈 홀더 id · 빈 소재 칸(자동)을 뺀 것 */
    fun canonical(): ProgramSetup = ProgramSetup(
        tools.filterValues { it != ToolOffset() },
        holders.filterValues { it.isNotEmpty() },
        stock.filterValues { it.isNotBlank() },
    )

    val isEmpty: Boolean get() = canonical() == ProgramSetup()

    companion object {
        const val VERSION = "v1"
        const val END = "end"
        const val MAX_CHARS = 100_000

        private fun oneLine(v: String) = v.none { it == ';' || it == '=' || it == '\n' }

        /**
         * 읽기 — 던지지 않는다. null = 읽지 못함(모르는 버전 · 잘림(`end` 없음) · 10만 자 넘음). 모르는 줄 · 틀린 공구 · 홀더 줄은 그 줄만,
         * 범위 밖 공구 칸 · 키패드 글이 아닌 소재 칸은 그 칸만 버린다(디스크의 글은 사람이 고칠 수 있다)
         */
        fun decode(s: String): ProgramSetup? {
            if (s.length > MAX_CHARS) return null
            val lines = s.trimEnd().split('\n')
            if (lines.size < 2 || lines.first().trim() != VERSION || lines.last().trim() != END) return null
            var r = ProgramSetup()
            for (line in lines.subList(1, lines.size - 1)) {
                val i = line.indexOf('=')
                if (i <= 0) continue
                val v = line.substring(i + 1)
                when (line.substring(0, i)) {
                    "tools" -> r = r.copy(tools = SimState.decodeTools(v).filterKeys { it in 1..99 }.mapValues { it.value.inRange() })
                    "holders" -> r = r.copy(
                        holders = v.split(';').mapNotNull { row ->
                            val n = row.substringBefore('=', "").toIntOrNull()?.takeIf { it in 1..99 } ?: return@mapNotNull null
                            row.substringAfter('=').takeIf { it.isNotEmpty() }?.let { n to it }
                        }.toMap(),
                    )
                    "stock" -> {
                        val m = SetupText.decode(v)
                        r = r.copy(stock = StockField.entries.mapNotNull { f -> m[f.key]?.takeIf(::keypadText)?.let { f to it } }.toMap())
                    }
                }
            }
            return r
        }

        /** 숫자 키패드 글인가(숫자 · `.` · `-`, 16자까지). 범위 밖 수는 그대로 둔다 — 화면이 빨간 칸으로 알린다([stockOf]) */
        private fun keypadText(v: String) = v.isNotEmpty() && v.length <= 16 && v.all { it in '0'..'9' || it == '.' || it == '-' }

        /** 공구 설정 칸이 받는 범위([ToolRange]) 밖이면 빈칸 — 팁이 0~9 밖이면 팁 고르기 그림이 던진다. 바 지름은 [SimState.decodeTools] 가 거른다 */
        private fun ToolOffset.inRange() = copy(
            noseR = noseR?.takeIf(ToolRange::noseR),
            tip = tip?.takeIf(ToolRange::tip),
            kappaDeg = kappaDeg?.takeIf(ToolRange::kappa),
            width = width?.takeIf(ToolRange::width),
            threadDeg = threadDeg?.takeIf(ToolRange::threadDeg),
            dieDia = dieDia?.takeIf(ToolRange::dieDia),
            diePitch = diePitch?.takeIf(ToolRange::diePitch),
        )
    }
}

/** 공구 설정 칸이 받는 범위 — 칸(SimSettingsSheet)과 저장 글 읽기([ProgramSetup])가 같이 쓴다(한쪽만 바꾸면 저장한 값이 다시 켤 때 빈칸이 된다). 바 지름은 `ToolShape.barOk` */
internal object ToolRange {
    fun noseR(v: Double) = v >= 0 && v <= 50
    fun tip(v: Int) = v in 0..9
    fun kappa(v: Double) = v > 0 && v < 180
    fun width(v: Double) = v > 0 && v <= 100
    fun threadDeg(v: Double) = v > 0 && v < 180
    fun dieDia(v: Double) = v > 0 && v <= 200
    fun diePitch(v: Double) = v > 0 && v <= 20
}

/** 칸 글 → 값. 빈칸 = null(자동), 숫자가 아니거나 범위 밖 = [Bad] */
internal sealed interface FieldValue {
    data object Blank : FieldValue
    data class Ok(val v: Double) : FieldValue
    data object Bad : FieldValue
}

internal fun fieldValue(text: String?, ok: (Double) -> Boolean): FieldValue {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return FieldValue.Blank
    val v = t.toDoubleOrNull() ?: return FieldValue.Bad
    return if (v.isFinite() && ok(v)) FieldValue.Ok(v) else FieldValue.Bad
}

internal fun FieldValue.orNull(): Double? = (this as? FieldValue.Ok)?.v

/** 기계 칸마다 받는 범위 */
internal fun machineOk(f: MachineField): (Double) -> Boolean = when (f) {
    MachineField.RAPID_X, MachineField.RAPID_Z -> { v -> v > 0 && v <= 200 }
    MachineField.TOOL_CHANGE -> { v -> v in 0.0..600.0 }
    MachineField.MAX_RPM -> { v -> v > 0 && v <= 100_000 }
    MachineField.HOME_X, MachineField.HOME2_X -> { v -> v in 0.0..10_000.0 }
    MachineField.HOME_Z, MachineField.HOME2_Z -> { v -> v in -10_000.0..10_000.0 }
    MachineField.G92_CHAMFER -> { v -> v in 0.0..9.9 }
}

/** 소재 칸마다 받는 범위 */
internal fun stockOk(f: StockField): (Double) -> Boolean = when (f) {
    StockField.DIA, StockField.LENGTH, StockField.CHUCK -> { v -> v > 0 && v <= 10_000 }
    StockField.FACE -> { v -> v in -10_000.0..10_000.0 }
    StockField.BORE -> { v -> v in 0.0..10_000.0 }
}

/**
 * 기계 칸 · 스위치 → [MachineSetup](원점 · 제2 원점은 [RunSetup.machineAt] 이 정한다). 빈칸 · 틀린 칸은 가정 값(설계 4.1).
 */
internal fun machineOf(text: Map<MachineField, String>, path: RapidPath, decimal: DecimalMode, skip: Boolean, comp: CompStartup): MachineSetup {
    val d = MachineSetup()
    fun v(f: MachineField) = fieldValue(text[f], machineOk(f)).orNull()
    return MachineSetup(
        rapidX = v(MachineField.RAPID_X)?.let { it * 1000 } ?: d.rapidX,
        rapidZ = v(MachineField.RAPID_Z)?.let { it * 1000 } ?: d.rapidZ,
        rapidPath = path,
        toolChangeSec = v(MachineField.TOOL_CHANGE) ?: d.toolChangeSec,
        maxRpm = v(MachineField.MAX_RPM) ?: d.maxRpm,
        decimal = decimal,
        blockSkip = skip,
        g92ChamferLeads = v(MachineField.G92_CHAMFER) ?: 0.0,
        compStartup = comp,
    )
}

/** 소재 칸과 자동 값으로 만든 소재. 칸이 틀렸거나(범위 · 구멍 ≥ 지름) 자동 값도 없으면 null + 틀린 칸 */
internal fun stockOf(text: Map<StockField, String>, run: SimRun): Pair<StockSpec?, Set<StockField>> {
    val auto = StockAuto.of(run)
    val bad = mutableSetOf<StockField>()
    fun v(f: StockField): Double? = when (val x = fieldValue(text[f], stockOk(f))) {
        FieldValue.Blank -> null
        FieldValue.Bad -> null.also { bad += f }
        is FieldValue.Ok -> x.v
    }
    val dia = v(StockField.DIA) ?: auto?.dia
    val face = v(StockField.FACE) ?: auto?.faceZ
    // 끝면만 바꾸면 돌출은 자동 값의 척 면을 그대로(척 면 = 자동 끝면 − 자동 돌출)
    val len = v(StockField.LENGTH) ?: auto?.let { a -> face?.let { it - (a.faceZ - a.length) } }?.takeIf { it > 0 }
    val bore = v(StockField.BORE) ?: auto?.boreDia ?: 0.0
    val chuck = v(StockField.CHUCK) ?: dia?.let { StockAuto.chuckOf(it) }
    if (dia != null && bore >= dia) bad += StockField.BORE
    if (bad.isNotEmpty() || dia == null || face == null || len == null || chuck == null) return null to bad
    return StockSpec(dia, len, face, bore, chuck) to bad
}

/** 칸에 보일 수(끝 0 없이): 12.0 → "12", 0.4 → "0.4" */
internal fun numText(v: Double): String {
    if (!v.isFinite()) return ""
    val r = kotlin.math.round(v * 1000) / 1000
    return if (r == kotlin.math.floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
}
