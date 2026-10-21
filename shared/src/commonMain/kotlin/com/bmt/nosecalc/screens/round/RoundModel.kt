package com.bmt.nosecalc.screens.round

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
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

/** TYPE 짧은 이름(고르기 카드 머리 · 입력 화면 요약) */
internal fun roundTypeShort(type: Int): String = when (type) {
    1 -> "술통"
    2 -> "아령"
    3 -> "원통 + 돔"
    4 -> "축 + 공"
    5 -> "원통 + 구면 + 축"
    6 -> "플랜지 + 돔 + 축"
    else -> "CAD 3점호"
}

/**
 * 라운드 화면 상태. 입력값은 쓴 그대로의 글.
 * 치수 칸은 TYPE 마다 따로, 노즈R·팁·Z 여유·끝 직선 길이는 함께 쓴다. 수동(3점호) 칸은 따로.
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

    /** 도면 치수. 처음 값은 모두 0(예시 치수 없음) */
    fun dim(type: Int, key: String): String = values["t$type/$key"] ?: "0"
    fun setDim(type: Int, key: String, v: String) { values["t$type/$key"] = v }

    /** TYPE 2 의 L2 기준 */
    var l2Ref: L2Ref
        get() = if (values["l2ref"] == "A") L2Ref.A else L2Ref.B
        set(v) { values["l2ref"] = v.name }

    fun shared(name: String): String = values["s/$name"] ?: RoundDefaults.shared(name)
    fun setShared(name: String, v: String) { values["s/$name"] = v }

    /** 팁 번호 직접 지정. null = 표준(TYPE 은 3, 수동은 외경·내경·방향에 따라) */
    var tip: Int?
        get() = values["tip"]?.toIntOrNull()
        set(v) { values["tip"] = v?.toString() ?: "" }

    var manualTip: Int?
        get() = values["m/tip"]?.toIntOrNull()
        set(v) { values["m/tip"] = v?.toString() ?: "" }

    /** 수동: x1·z1·x2·z2·x3·z3. 처음 값은 모두 0 */
    fun point(name: String): String = values["m/$name"] ?: "0"
    fun setPoint(name: String, v: String) { values["m/$name"] = v }

    var inner: Boolean
        get() = values["m/inner"] == "true"
        set(v) { values["m/inner"] = v.toString() }

    /**
     * TYPE 입력을 계산기 입력으로. 그 TYPE 의 칸만 넣고 나머지는 null. 노즈R 빈칸은 NaN(계산기가 BAD_INPUT).
     * 모서리 R 빈칸은 0. 길이 · 지름 · 주 R 은 0 보다 커야 하므로 0(처음 값)은 아직 안 넣은 것 — 빈칸처럼 null 로 넘긴다
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
            tip = tip ?: STANDARD_TIP,
            zClear = shared("zClear").toDoubleOrNull() ?: 0.0,
            legLen = shared("leg").toDoubleOrNull() ?: 5.0,
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
        /** TYPE 1~6 은 외경 정방향(척 쪽, G42) 하나 */
        const val STANDARD_TIP = 3

        val Saver = listSaver<RoundState, String>(
            save = { s -> listOf(s.type?.toString() ?: "", s.open.toString()) + s.values.flatMap { listOf(it.key, it.value) } },
            restore = { l -> RoundState(l[0].toIntOrNull(), l[1].toBoolean(), l.drop(2).chunked(2).associate { it[0] to it[1] }) },
        )
    }
}

/**
 * 공구 칸 처음 값. 노즈R 은 흔한 값, 끝 직선 길이는 그림용 5.0. 도면 치수 · 세 점 · Z 여유는 0 에서 시작한다(예시 치수 없음).
 * Z 여유 0 이어도 계산기가 진입점을 노즈R 만큼 띄운다
 */
internal object RoundDefaults {
    fun shared(name: String): String = when (name) {
        "noseR" -> "0.4"
        "zClear" -> "0"
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
 * 화면에 보일 경고(오목 R ≤ 노즈R 을 맨 앞에). 3점호의 세 점 가운데 둘이 같은 자리면(처음 값 0 · 0 그대로 등)
 * 계산기의 모양 불일치 대신 값 부족 — 아직 덜 넣은 것이라 빨강이 아닌 안내로 보인다
 */
internal fun shownWarnings(r: RoundResult, arc3: Arc3Input? = null): List<RoundWarning> {
    val overlap = arc3 != null && listOf(arc3.x1 to arc3.z1, arc3.x2 to arc3.z2, arc3.x3 to arc3.z3).distinct().size < 3
    return r.warnings
        .map { if (it == RoundWarning.SHAPE_MISMATCH && overlap) RoundWarning.MISSING_INPUT else it }
        .sortedBy { it != RoundWarning.CONCAVE_R_LE_NOSE }
}

/** 경고가 가리키는 치수 칸(빨간 테두리). 값 부족은 안내라 칸을 붉히지 않는다 */
internal fun roundWarningFields(w: RoundWarning, type: Int): Set<String> = when (w) {
    RoundWarning.CONTRADICTION -> blankable(type).toSet()
    RoundWarning.CORNER_R_TOO_BIG -> dims(type).map { it.key }.filter { it in CORNERS }.toSet()
    else -> emptySet()
}

/**
 * 경고 글. 계산을 멈춘 것은 빨강, 숫자와 함께 나온 것은 노랑, 값 부족은 파란 안내. type = [MANUAL] 이면 3점호.
 * empty = 아직 안 넣은(빈칸 · 0) 치수 칸 key — 값 부족 안내에 넣을 칸 이름을 든다
 */
internal fun roundWarningText(w: RoundWarning, type: Int, empty: Set<String> = emptySet()): String {
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
            "입력이 잘못됐습니다. 길이 · 지름 · R 은 0 보다 크고, 모서리 R · 노즈R · Z 여유는 0 이상, 끝 직선 길이는 0 보다 커야 합니다. X 는 0 이상, 모든 값은 ±${MAX_MM.toInt()} mm 안입니다."
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
            if (type == MANUAL) "오목 원호의 R 이 노즈R 보다 작아 공구가 들어가지 못합니다. 노즈R 을 줄이세요."
            else "노즈R 에 비해 요소가 너무 짧거나 좁아 공구가 닿지 못합니다. 도면 형상이 안 나옵니다 — 노즈R 을 줄이세요."
        RoundWarning.CONCAVE_R_LE_NOSE ->
            "오목 R 이 노즈R 이하입니다(R − r ≤ 0). 그 자리의 보정 원호가 없어집니다. R = r 이면 정확하지만, R < r 이면 도면보다 큰 R(= 노즈R)이 남습니다."
        RoundWarning.AGAINST_TOOL ->
            "형상 일부가 바이트 방향과 반대입니다(왼쪽 면 · 공의 뒤쪽 등 척 쪽을 향한 면). 그 부분은 35° 인서트 · 역바이트 · 절단 등으로 깎이는지 따로 확인하세요."
    }
}
