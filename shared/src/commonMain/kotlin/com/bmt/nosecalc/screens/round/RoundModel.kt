package com.bmt.nosecalc.screens.round

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.BarField
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderPose
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.RoundWarning

/** 수동(CAD 3점호) 카드의 번호. TYPE 1~6 다음 */
internal const val MANUAL = 7

/** 치수 칸 하나. key = RoundInput 필드(mainR·cr1·cr2·d1·d2·l1·l2), label = 그림·칸 이름(04 의 Main R = 주 R, Corner R = 모서리 R) */
internal class DimField(val key: String, val label: String)

/** 형상(TYPE)마다 그림에 적힌 치수(그림 순서). 형상 1·3·4 의 모서리 R·D·L 은 계산기의 cornerR1·d1·l1 */
internal fun dims(type: Int): List<DimField> = when (type) {
    1 -> listOf(DimField("mainR", "주 R"), DimField("cr1", "모서리 R"), DimField("l1", "L"))
    2 -> listOf(DimField("mainR", "주 R"), DimField("cr1", "모서리 R"), DimField("d1", "D1"), DimField("l1", "L1"), DimField("l2", "L2"))
    3, 4 -> listOf(DimField("cr1", "모서리 R"), DimField("mainR", "주 R"), DimField("d1", "D"), DimField("l1", "L"))
    else -> listOf(
        DimField("cr2", "모서리 R(2)"), DimField("mainR", "주 R"), DimField("cr1", "모서리 R(1)"),
        DimField("d2", "D2"), DimField("d1", "D1"), DimField("l1", "L1"), DimField("l2", "L2"),
    )
}

/** 하나까지 비워도 되는 칸(구 중심이 축 위라 나머지로 정해진다). calc-design.md 의 "비울 수 있는 묶음" */
internal fun blankable(type: Int): List<String> = when (type) {
    2 -> listOf("mainR", "d1", "l2")
    3, 4 -> listOf("mainR", "d1", "l1")
    5 -> listOf("mainR", "d2", "d1", "l2")
    else -> emptyList()
}

/** 모서리 R 칸. 비우면 0(날카로운 모서리) — 테이퍼와 같다 */
private val CORNERS = setOf("cr1", "cr2")

/** TYPE 짧은 이름(고르기 카드 머리 · 입력 화면 요약). inner = 같은 치수의 구멍(형상 2~6) */
internal fun roundTypeShort(type: Int, inner: Boolean = false): String = when (type) {
    1 -> "술통"
    2 -> if (inner) "공 모양 홈" else "아령"
    3 -> if (inner) "돔 바닥" else "원통 + 돔"
    4 -> if (inner) "공 바닥" else "축 + 공"
    5 -> if (inner) "구면 단차" else "원통 + 구면 + 축"
    6 -> if (inner) "돔 단차" else "플랜지 + 돔 + 축"
    else -> "CAD 3점호"
}

/** 끝 직선 길이 칸 이름. 내경 3~6 은 실제 치수인 입구 구멍 깊이(Z0 ~ 첫 이론 꼭짓점) */
internal fun legLabel(type: Int, inner: Boolean): String = if (inner && type != 2) "입구 구멍 깊이" else "끝 직선 길이"

/**
 * 라운드 화면 상태. 입력값은 쓴 그대로의 글.
 * 치수 칸은 TYPE 마다 따로, 노즈R·팁·Z 여유·끝 직선 길이 · 보링바 칸은 함께 쓴다. 수동(3점호) 칸은 따로.
 * 보링바 칸은 앱을 켠 동안만 둔다(공구 값은 기억하지 않기로 했다)
 */
@Stable
internal class RoundState(
    type: Int? = null,
    open: Boolean = false,
    values: Map<String, String> = emptyMap(),
) {
    /** 고른 카드: 1~6 = TYPE, [MANUAL] = 수동(3점호) */
    var type by mutableStateOf(type)

    /** true = 입력·결과 화면 */
    var open by mutableStateOf(open)

    /** 입력 화면을 열 때 키패드를 열어 둘 칸 id(치수 key · zClear · leg · x1 · z1 …). 화면이 처음 그려질 때 한 번 읽는다(스크린샷 시험) */
    var editing by mutableStateOf<String?>(null)
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    /** 도면 치수. 처음 값은 모두 빈칸(예시 치수 없음 — 사용자 요청 2026-10-28, 예전엔 0). 모서리 R 빈칸은 0, 나머지 빈칸은 아직 안 넣은 값 */
    fun dim(type: Int, key: String): String = values["t$type/$key"] ?: ""
    fun setDim(type: Int, key: String, v: String) { values["t$type/$key"] = v }

    /** TYPE 2 의 L2 기준 */
    var l2Ref: L2Ref
        get() = if (values["l2ref"] == "A") L2Ref.A else L2Ref.B
        set(v) { values["l2ref"] = v.name }

    fun shared(name: String): String = values["s/$name"] ?: RoundDefaults.shared(name)
    fun setShared(name: String, v: String) { values["s/$name"] = v }

    /**
     * TYPE 화면의 팁 번호 직접 지정. null = 표준(외경 3 · 내경 2). 외경 · 내경([typeInner]) 따로 둔다 —
     * 외경에서 고른 팁이 내경 보링(G41)으로 넘어가지 않게(테이퍼도 가공 종류마다 따로)
     */
    var tip: Int?
        get() = values[tipKey]?.toIntOrNull()
        set(v) { values[tipKey] = v?.toString() ?: "" }
    private val tipKey get() = if (typeInner) "tip/inner" else "tip"

    /** TYPE 화면(고르기 화면 스위치): 내경 = 같은 치수의 구멍(형상 2~6, 보링바 · 팁 2 · G41) */
    var typeInner: Boolean
        get() = values["inner"] == "true"
        set(v) { values["inner"] = v.toString() }

    /** 내경 형상 5 · 6: 관통(바닥 면 없이 D1 구멍이 뚫림). false = 막힘(바닥 면까지) */
    var through: Boolean
        get() = values["through"] == "true"
        set(v) { values["through"] = v.toString() }

    /** 보링바 칸(dia · f · shape · edge · kr · ovh · dmin · safe): 쓴 그대로, 처음은 빈칸 — 안전 여유(safe)만 0.5 */
    fun holder(name: String): String = values["h/$name"] ?: if (name == "safe") Fmt.nc(HolderStop.SAFE_DEFAULT) else ""
    fun setHolder(name: String, v: String) { values["h/$name"] = v }

    /**
     * 안전 여유(mm, 사용자 결정 2026-10-27: 바 · 인서트 · 공구대 · 최소 가공경이 소재와 이만큼 떨어져야 안전 — 기본 0.5, 0 도 된다).
     * 비우면 기본값, 숫자가 아니면 NaN(칸이 빨갛고 검사 안 함). 음수도 [HolderStop.safeOk] 가 막는다
     */
    fun holderSafe(): Double = holder("safe").let { if (it.isBlank()) HolderStop.SAFE_DEFAULT else it.toDoubleOrNull() ?: Double.NaN }

    /** 고른 프리셋. null = 직접 입력 */
    val preset: HolderPreset? get() = HOLDER_PRESETS.firstOrNull { it.code == values["h/preset"] }

    /**
     * 프리셋을 고르면 D · f · DMIN · κr · 모양 · 날 길이를 채운다(돌출 길이 · 노즈 R 은 그대로). 출처에 없는 칸은 빈칸으로(사용자가 넣는다).
     * null = 직접 입력(칸은 그대로)
     */
    fun pickPreset(p: HolderPreset?) {
        values["h/preset"] = p?.code ?: ""
        p?.fields()?.forEach { (k, v) -> setHolder(k, v) }
    }

    /** 프리셋을 고른 뒤 그 카탈로그 값을 고쳤나(프리셋 이름 옆 '(수정됨)'). 프리셋이 비운 칸에 넣은 값은 고친 것이 아니다 */
    fun presetEdited(): Boolean = preset?.fields()?.any { (k, v) -> v.isNotEmpty() && holder(k) != v } ?: false

    /** 보링바 칸 → 계산기 바. D · f · 모양 · 날 길이 · κr 가 비었거나 숫자가 아니면 null. 돌출 길이 · DMIN 은 비우면 null(그 검사 안 함) */
    fun holderBar(): BoringBar? = BoringBar(
        dia = holder("dia").toDoubleOrNull() ?: return null,
        f = holder("f").toDoubleOrNull() ?: return null,
        shape = InsertShape.entries.firstOrNull { it.name == holder("shape") } ?: return null,
        edge = holder("edge").toDoubleOrNull() ?: return null,
        kappaDeg = holder("kr").toDoubleOrNull() ?: return null,
        overhang = holder("ovh").toDoubleOrNull(),
        dMin = holder("dmin").toDoubleOrNull(),
    )

    var manualTip: Int?
        get() = values["m/tip"]?.toIntOrNull()
        set(v) { values["m/tip"] = v?.toString() ?: "" }

    /** 수동: x1·z1·x2·z2·x3·z3. 처음 값은 모두 빈칸(아직 안 넣은 값) */
    fun point(name: String): String = values["m/$name"] ?: ""
    fun setPoint(name: String, v: String) { values["m/$name"] = v }

    var inner: Boolean
        get() = values["m/inner"] == "true"
        set(v) { values["m/inner"] = v.toString() }

    /**
     * TYPE 입력을 계산기 입력으로. 그 TYPE 의 칸만 넣고 나머지는 null. 노즈R 빈칸은 NaN(계산기가 BAD_INPUT).
     * 모서리 R 빈칸은 0. 길이 · 지름 · 주 R 은 0 보다 커야 하므로 0(예전 처음 값)은 아직 안 넣은 것 — 빈칸처럼 null 로 넘긴다.
     * 내경은 형상 2~6 만(형상 1 은 고르기 화면에 없다), 관통은 내경 5 · 6 만
     */
    fun input(type: Int): RoundInput {
        fun n(key: String): Double? {
            if (dims(type).none { it.key == key }) return null
            val v = dim(type, key).toDoubleOrNull()
            return if (key in CORNERS) v ?: 0.0 else v?.takeIf { it != 0.0 }
        }
        return RoundInput(
            type = type,
            mainR = n("mainR"),
            cornerR1 = n("cr1"),
            cornerR2 = n("cr2"),
            d1 = n("d1"),
            d2 = n("d2"),
            l1 = n("l1"),
            l2 = n("l2"),
            l2Ref = l2Ref,
            noseR = shared("noseR").toDoubleOrNull() ?: Double.NaN,
            tip = tip,
            zClear = shared("zClear").toDoubleOrNull() ?: 0.0,
            legLen = shared("leg").toDoubleOrNull() ?: 5.0,
            inner = typeInner && type != 1,
            through = typeInner && through && type in 5..6,
        )
    }

    fun arc3(): Arc3Input = Arc3Input(
        inner = inner,
        noseR = shared("noseR").toDoubleOrNull() ?: Double.NaN,
        tip = manualTip,
        x1 = point("x1").toDoubleOrNull(),
        z1 = point("z1").toDoubleOrNull(),
        x2 = point("x2").toDoubleOrNull(),
        z2 = point("z2").toDoubleOrNull(),
        x3 = point("x3").toDoubleOrNull(),
        z3 = point("z3").toDoubleOrNull(),
        zClear = shared("zClear").toDoubleOrNull() ?: 0.0,
    )

    /** 수동: 끝 Z 가 시작보다 크면 역방향 */
    fun manualReverse(): Boolean {
        val z1 = point("z1").toDoubleOrNull()
        val z3 = point("z3").toDoubleOrNull()
        return z1 != null && z3 != null && z3 > z1
    }

    /** 수동 모드의 표준 팁(외경 · 내경, 정 · 역방향) */
    fun manualStandardTip(): Int = NoseComp.standardTip(inner, manualReverse())

    companion object {
        val Saver = listSaver<RoundState, String>(
            save = { s -> listOf(s.type?.toString() ?: "", s.open.toString()) + s.values.flatMap { listOf(it.key, it.value) } },
            restore = { l -> RoundState(l[0].toIntOrNull(), l[1].toBoolean(), l.drop(2).chunked(2).associate { it[0] to it[1] }) },
        )
    }
}

/**
 * 공구 칸 처음 값. 노즈R 은 흔한 값, 끝 직선 길이는 그림용 5.0. 도면 치수 · 세 점 · Z 여유는 빈칸에서 시작한다(예시 치수 없음).
 * Z 여유 빈칸은 0 — 0 이어도 계산기가 진입점을 노즈R 만큼 띄운다
 */
internal object RoundDefaults {
    fun shared(name: String): String = when (name) {
        "noseR" -> "0.4"
        "leg" -> "5.0"
        else -> ""
    }
}

/** 치수 칸 key(mainR · cr1 · cr2 · d1 · d2 · l1 · l2)의 값 */
internal fun RoundInput.dim(key: String): Double? = when (key) {
    "mainR" -> mainR
    "cr1" -> cornerR1
    "cr2" -> cornerR2
    "d1" -> d1
    "d2" -> d2
    "l1" -> l1
    "l2" -> l2
    else -> null
}

/**
 * 화면에 보일 경고(오목 R ≤ 노즈R 을 맨 앞에). 3점호의 세 점 가운데 둘이 같은 자리면(예전 처음 값 0 · 0 그대로 등)
 * 계산기의 모양 불일치 대신 값 부족 — 아직 덜 넣은 것이라 빨강이 아닌 안내로 보인다
 */
internal fun shownWarnings(r: RoundResult, arc3: Arc3Input? = null, machine: RoundResult? = null): List<RoundWarning> {
    val overlap = arc3 != null && listOf(arc3.x1 to arc3.z1, arc3.x2 to arc3.z2, arc3.x3 to arc3.z3).distinct().size < 3
    // 기계 보정 프로그램([RoundCalc.machine])만 내는 경고(짧은 막힌 끝을 뺌 · 못 뺌, 진입 · 빼는 길 못 찾음)도 함께 — 나머지는 같은 입력의 직접 보정 풀이와 같다
    val own = machine?.warnings.orEmpty().filter { it in MACHINE_ONLY }
    return (r.warnings + own)
        .map { if (it == RoundWarning.SHAPE_MISMATCH && overlap) RoundWarning.MISSING_INPUT else it }
        .sortedBy { it != RoundWarning.CONCAVE_R_LE_NOSE }
}

/** 기계 보정 프로그램만 내는 경고 */
private val MACHINE_ONLY = setOf(RoundWarning.SHORT_END_PAST, RoundWarning.SHORT_END_SKIPPED, RoundWarning.ENTRY_NOT_FOUND, RoundWarning.EXIT_NOT_FOUND)

/** 경고가 가리키는 치수 칸(빨간 테두리). 값 부족은 안내라 칸을 붉히지 않는다 */
internal fun roundWarningFields(w: RoundWarning, type: Int): Set<String> = when (w) {
    RoundWarning.CONTRADICTION -> blankable(type).toSet()
    RoundWarning.CORNER_R_TOO_BIG -> dims(type).map { it.key }.filter { it in CORNERS }.toSet()
    else -> emptySet()
}

/**
 * 경고 글. 계산을 멈춘 것은 빨강, 숫자와 함께 나온 것은 노랑, 값 부족은 파란 안내. type = [MANUAL] 이면 3점호.
 * empty = 아직 안 넣은(빈칸 · 0) 치수 칸 key — 값 부족 안내에 넣을 칸 이름을 든다. inner = 내경(구멍) 형상
 */
internal fun roundWarningText(w: RoundWarning, type: Int, empty: Set<String> = emptySet(), inner: Boolean = false): String {
    val names = dims(type).associate { it.key to it.label }
    val blanks = blankable(type).joinToString(" · ") { names.getValue(it) }
    return when (w) {
        RoundWarning.MISSING_INPUT -> if (type == MANUAL) {
            "값을 넣으면 바로 계산합니다. 원호 위 세 점(시작 · 가운데 · 끝)의 X · Z 를 서로 다른 자리로 넣으세요."
        } else {
            // 비울 수 있는 묶음은 하나는 비워 둬도 되므로 "… 가운데 둘" 처럼 더 넣을 개수로 든다
            val group = blankable(type).filter { it in empty }
            val need = listOf(
                dims(type).filter { it.key in empty && it.key !in blankable(type) }.joinToString(" · ") { it.label },
                if (group.size > 1) "${group.joinToString(" · ") { names.getValue(it) }} 가운데 ${listOf("하나", "둘", "셋")[group.size - 2]}" else "",
            ).filter { it.isNotEmpty() }
            "값을 넣으면 바로 계산합니다." + if (need.isEmpty()) "" else " 넣을 값: ${need.joinToString(", ")}."
        }
        RoundWarning.BAD_INPUT ->
            "입력이 잘못됐습니다. 길이 · 지름 · R 은 0 보다 크고, 모서리 R · 노즈R · Z 여유는 0 이상, ${legLabel(type, inner)}는 0 보다 커야 합니다. X 는 0 이상, 모든 값은 ±${MAX_MM.toInt()} mm 안입니다."
        RoundWarning.CONTRADICTION ->
            "치수가 서로 맞지 않습니다(0.001 mm 넘게). 주 R 은 중심이 축 위인 구라서 $blanks 가운데 하나는 나머지로 정해집니다. 하나를 비우면 계산합니다."
        RoundWarning.SHAPE_MISMATCH -> when (type) {
            1 -> "치수로 술통 모양이 나오지 않습니다. L 은 주 R × 2 보다 짧아야 합니다."
            2 -> "치수로 아령 모양이 나오지 않습니다. 주 R 은 D1/2 보다 커야 하고, L2(A) 는 L1 보다 길어야 합니다."
            3 -> "치수로 돔 모양이 나오지 않습니다. 돔은 L ≤ D/2 ≤ 주 R 이어야 합니다(L 이 D/2 보다 길면 형상 4 공)."
            4 -> "치수로 공 모양이 나오지 않습니다. 공은 D/2 ≤ 주 R, L ≥ D/2 이어야 합니다(L 이 짧으면 형상 3 돔)."
            5 -> "치수로 형상 5 모양이 나오지 않습니다. D1 < D2, 주 R ≥ D2/2 이고, L2 는 구면이 두 지름을 잇는 길이여야 합니다."
            6 -> "치수로 형상 6 모양이 나오지 않습니다. D1 < D2, 주 R > D1/2 이고, 돔이 플랜지 면에 닿되 플랜지(D2)보다 낮아야 합니다."
            else -> "세 점으로 원호가 나오지 않습니다. 세 점이 한 줄이거나 겹치면 안 되고, 시작과 끝의 Z 가 같으면 깎는 방향을 알 수 없습니다."
        }
        RoundWarning.CORNER_R_TOO_BIG -> "모서리 R 이 너무 커서 이웃 요소를 다 먹습니다(닿을 자리가 없음). 모서리 R 을 줄이세요."
        RoundWarning.NOSE_UNREACHABLE ->
            if (type == MANUAL) "오목 원호의 R 이 노즈R 보다 작아 공구가 들어가지 못하거나, 축 가까이에서 노즈 중심이 축을 넘어 부품 반대쪽을 팝니다. 노즈R 을 줄이세요."
            else "노즈R 에 비해 요소가 너무 짧거나 좁아 공구가 닿지 못합니다. 도면 형상이 안 나옵니다 — 노즈R 을 줄이세요."
        RoundWarning.CONCAVE_R_LE_NOSE ->
            "오목 R 이 노즈R 이하입니다(R − r ≤ 0). 그 자리의 보정 원호가 없어집니다. R = r 이면 정확하지만, R < r 이면 도면보다 큰 R(= 노즈R)이 남습니다."
        RoundWarning.AGAINST_TOOL -> if (inner) {
            "형상 일부가 바이트 방향과 반대입니다(구멍 입구 쪽을 향한 면 — 언더컷). 아래 '보링바 (홀더)' 칸에 바 치수를 넣어 인서트 · 바가 닿는지 확인하세요."
        } else {
            "형상 일부가 바이트 방향과 반대입니다(왼쪽 면 · 공의 뒤쪽 등 척 쪽을 향한 면). 그 부분은 35° 인서트 · 역바이트 · 절단 등으로 깎이는지 따로 확인하세요."
        }
        // 짧은 막힌 끝을 뺄 수 없는 꼴(calc 라운드 '기계 보정 프로그램' — 앞 요소에 직각 빼기 교점이 남지 않음)
        RoundWarning.SHORT_END_PAST ->
            "끝 직선(척 · 구멍 바닥 쪽으로 깎아 끝나는 직선)이 노즈R 보다 짧은데, 그 앞 요소에 벽을 따라 뺄 자리가 없어 기계 보정(G41/G42) 프로그램이 그 요소를 뺄 수 없습니다. " +
                "끝 접선으로 곧게 빼므로 노즈가 끝 좌표 너머(벽 속)로 갑니다. 직접 보정 프로그램을 쓰거나 노즈R 을 줄이세요."
        RoundWarning.ENTRY_NOT_FOUND ->
            "기계 보정(G41/G42) 프로그램의 진입 길을 도면에 닿지 않게 짜지 못했습니다(Z20. 에서 내려오는 길이 도면에 닿거나, 두 축으로 가는 진입 G0 이 급송 속도 비에 따라 꺾여 닿습니다). 진입 줄을 손으로 고치거나 직접 보정 프로그램을 쓰세요."
        // 빼는 길 못 찾음(calc 라운드 '기계 보정 프로그램' — G40 자리 규칙 · 물러남 띠 · 빼는 줄 노즈 길 · 바깥 90° 이하 꺾임을 함께 못 맞춤)
        RoundWarning.EXIT_NOT_FOUND ->
            "기계 보정(G41/G42) 프로그램의 빼는 길(빼는 줄 · G40 · G0Z200.)을 도면에서 넉넉히 떨어지게 짜지 못했습니다(노즈에 비해 좁은 구멍 · 깊은 언더컷 등). " +
                "팁 번호 · 보정 형식에 따라 빼는 줄이나 G40 · G0Z200. 이 도면에 닿을 수 있으니 빼는 줄을 손으로 고치거나 직접 보정 프로그램을 쓰세요."
        // 짧은 막힌 끝을 뺀 꼴 — 노즈가 벽을 넘지 않는 대신 그 직선은 깎지 않음
        RoundWarning.SHORT_END_SKIPPED ->
            "끝 직선(척 · 구멍 바닥 쪽으로 깎아 끝나는 직선)이 노즈R 보다 짧아, 기계 보정(G41/G42) 프로그램은 그 직선(과 앞 모서리 R)을 깎지 않고 앞 요소 끝에서 벽을 따라 뺍니다. " +
                "노즈가 벽 너머로 가지 않는 대신 그 자리에 살이 남으니, 따로 다듬거나 노즈R 을 줄이세요."
    }
}

/** 보링바 칸 오류 글(칸 아래). r = 노즈 R. 하한은 다른 칸이 숫자가 아니면 "—" */
internal fun barFieldText(f: BarField, bar: BoringBar, r: Double): String {
    fun mm(v: Double) = if (v.isFinite()) Fmt.fixed3(v) else "—"
    val o = bar.overhang ?: 0.0
    return when (f) {
        BarField.DIA -> "바 지름은 0 보다 커야 합니다"
        BarField.F -> "f 는 ${mm(HolderCheck.fMin(bar.dia, bar.kappaDeg, r))} 이상이어야 합니다 (바 윗면이 노즈보다 낮게)"
        BarField.EDGE -> "날 길이는 ${mm(HolderCheck.edgeMin(bar.shape, r))} 보다 길어야 합니다 (노즈 R 이 두 날에 닿게)"
        BarField.KAPPA -> "절입각 κr 은 90° 이상, ${plain(180 - bar.shape.epsDeg)}° 보다 작아야 합니다"
        // 0 보다 크고 한도 안이면 헤드 길이가 모자란 것(그때는 다른 칸이 다 맞다)
        BarField.OVERHANG -> if (o > 0 && o <= MAX_MM) "돌출 길이는 헤드 길이 ${mm(HolderCheck.overhangMin(bar, r))} 보다 길어야 합니다" else "돌출 길이는 0 보다 커야 합니다"
        BarField.DMIN -> "최소 가공경은 0 보다 커야 합니다"
    }
}

/**
 * 보링바 자세가 걸린 NC 카드 줄(0부터). prog = 카드의 프로그램(comp = 직접 보정: G40 풀이 · 가상 인선 좌표 · 첫 줄 주석만큼 밀림,
 * 아니면 도면 좌표 풀이 · 노즈가 닿는 도면 점), e = 요소(−2 진입점 · −1 진입 이동 · k = segs[k])
 */
internal fun cardLine(prog: RoundSolution, comp: Boolean, e: Int, p: HolderPose): Int = prog.lineOf(e, if (comp) p.tip else p.drawn) + (if (comp) 1 else 0)

/** 뒤보링: 도면이 +Z 로 간다(3점호 역방향 내경) — 보링바 검사와 같은 판정 */
internal fun backBoring(sol: RoundSolution): Boolean = sol.drawing.segs.last().end.z > sol.drawing.segs.first().start.z

/** 쓴 그대로의 숫자: 끝 0 · 소수점 없이(20 → "20", 107.5 → "107.5") */
internal fun plain(v: Double) = Fmt.nc(v).removeSuffix(".")
