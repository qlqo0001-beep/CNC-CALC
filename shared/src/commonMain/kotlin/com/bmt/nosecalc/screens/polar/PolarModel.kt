package com.bmt.nosecalc.screens.polar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.PolarBlock
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarInput
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarSolution
import com.bmt.nosecalc.calc.PolarWarning
import kotlin.math.abs

/**
 * 숫자칸. key = 상태 열쇠 · 칸 묶음 id. 이름은 외측/내측 · 도형에 따라 바뀌어 [PolarState.label] 이 준다.
 * 도면 값(외경 · W · 공구경 · 깊이 · 이송)은 빈칸 = 아직 안 넣음, 가공 값(코너 R · 회전 · 여유)은 빈칸 = 0 · 0 · 1
 */
internal enum class PolarField(val key: String, val unit: String) {
    OD("od", "mm"),
    WIDTH("w", "mm"),
    CORNER_R("rc", "mm"),
    ROTATION("rot", "°"),
    TOOL_DIA("tool", "mm"),
    CLEAR("clear", "mm"),
    DEPTH("depth", "mm"),
    FEED("feed", "mm/min"),
}

/** 도형 이름(원본 12 의 '육각형 (Hexagon)' → 한글) */
internal fun shapeName(p: PolarShape): String = when (p) {
    PolarShape.TRIANGLE -> "삼각형"
    PolarShape.SQUARE -> "사각형"
    PolarShape.PENTAGON -> "오각형"
    PolarShape.HEXAGON -> "육각형"
    PolarShape.OCTAGON -> "팔각형"
    PolarShape.TWO_FLAT -> "2면폭"
}

/** 고르기 시트의 설명 한 줄 — W 의 뜻(홀수 각은 내접원 지름) */
internal fun shapeNote(p: PolarShape): String = when (p) {
    PolarShape.TWO_FLAT -> "마주 보는 평면 둘 · W = 두 평면 사이 · 외측만"
    PolarShape.TRIANGLE, PolarShape.PENTAGON -> "정${p.sides}각형 · W = 내접원 지름 (중심에서 변까지 × 2)"
    else -> "정${p.sides}각형 · W = 맞변 폭 (마주 보는 변 사이)"
}

/** 시트에 보이는 도형 순서(원본의 드롭다운 — 육각형이 원본 기본) */
internal val ShapeOrder = listOf(
    PolarShape.TRIANGLE, PolarShape.SQUARE, PolarShape.PENTAGON, PolarShape.HEXAGON, PolarShape.OCTAGON, PolarShape.TWO_FLAT,
)

/**
 * 극좌표 다각가공 화면 상태. 숫자칸 값은 쓴 그대로의 글(빈칸 = 안 넣음 · 기본값). 처음은 모두 빈칸 · 외측 · 육각형(원본 12) · X 직경.
 * xDiameter = G12.1 안의 X 를 직경으로 쓰는지(기계 매개변수마다 다름 — calc-design.md '턴밀 극좌표'). 보정 방식은 앱 전체([com.bmt.nosecalc.AppState.compMode])
 */
@Stable
internal class PolarState(
    inner: Boolean = false,
    shape: PolarShape = PolarShape.HEXAGON,
    xDiameter: Boolean = true,
    values: Map<String, String> = emptyMap(),
) {
    var inner by mutableStateOf(inner)
    var shape by mutableStateOf(shape)
    var xDiameter by mutableStateOf(xDiameter)
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    fun value(f: PolarField): String = values[f.key] ?: ""
    fun set(f: PolarField, v: String) {
        values[f.key] = v
    }

    fun num(f: PolarField): Double? = value(f).toDoubleOrNull()

    /** 2면폭은 코너 R 을 쓰지 않는다(칸을 감춤 — 계산기도 무시) */
    val usesCornerR: Boolean get() = shape != PolarShape.TWO_FLAT

    /** 내측 2면폭은 없는 도형(계산기 BAD_INPUT) — 시트에서 막지만 외측에서 고른 뒤 내측으로 바꾸면 여기 걸린다 */
    val shapeBad: Boolean get() = inner && shape == PolarShape.TWO_FLAT

    /** 보이는 칸(2면폭은 코너 R 없음) */
    val fields: List<PolarField> get() = PolarField.entries.filter { it != PolarField.CORNER_R || usesCornerR }

    fun label(f: PolarField): String = when (f) {
        PolarField.OD -> if (inner) "기초 구멍 지름" else "외경 (OD)"
        PolarField.WIDTH -> when (shape) {
            PolarShape.TWO_FLAT -> "2면 폭 (W)"
            PolarShape.TRIANGLE, PolarShape.PENTAGON -> "내접원 지름 (W)"
            else -> "맞변 폭 (W)"
        }
        PolarField.CORNER_R -> "코너 R"
        PolarField.ROTATION -> "회전"
        PolarField.TOOL_DIA -> "공구경 (D)"
        PolarField.CLEAR -> "여유"
        PolarField.DEPTH -> "깊이 Z"
        PolarField.FEED -> "이송 F"
    }

    /** 계산기 입력 — 숫자칸은 toDoubleOrNull(빈칸 = null) */
    fun input(): PolarInput = PolarInput(
        inner = inner,
        shape = shape,
        od = num(PolarField.OD),
        width = num(PolarField.WIDTH),
        cornerR = if (usesCornerR) num(PolarField.CORNER_R) else null,
        rotationDeg = num(PolarField.ROTATION),
        toolDia = num(PolarField.TOOL_DIA),
        depthZ = num(PolarField.DEPTH),
        feed = num(PolarField.FEED),
        clear = num(PolarField.CLEAR),
        xDiameter = xDiameter,
    )

    /** 아직 안 넣은 도면 값(파랑 안내의 '남은 것') — 계산기 MISSING_INPUT 의 다섯 칸 */
    fun missing(): List<String> = REQUIRED.filter { num(it) == null }.map { shortName(it) }

    /** 안내 · 경고 글의 짧은 칸 이름 */
    fun shortName(f: PolarField): String = when (f) {
        PolarField.OD -> if (inner) "기초 구멍 지름" else "외경"
        PolarField.WIDTH -> "W"
        PolarField.CORNER_R -> "코너 R"
        PolarField.ROTATION -> "회전"
        PolarField.TOOL_DIA -> "공구경"
        PolarField.CLEAR -> "여유"
        PolarField.DEPTH -> "깊이 Z"
        PolarField.FEED -> "이송 F"
    }

    /** 범위 밖 칸 — 계산기의 BAD_INPUT 과 같은 규칙(PolarG12.calc). 값 부족과 따로 보아 넣자마자 빨갛게 짚는다 */
    fun bad(): Set<PolarField> = fields.filter { f ->
        val v = num(f) ?: return@filter false
        if (!v.isFinite()) return@filter true
        when (f) {
            PolarField.OD, PolarField.WIDTH, PolarField.TOOL_DIA -> v <= 0 || v > MAX_MM
            PolarField.CORNER_R, PolarField.CLEAR -> v < 0 || v > MAX_MM
            PolarField.ROTATION -> abs(v) > MAX_MM
            PolarField.DEPTH -> v >= 0 || v < -MAX_MM
            PolarField.FEED -> v <= 0 || v > PolarG12.MAX_FEED
        }
    }.toSet()

    /** 계산을 멈춘 경고가 가리키는 칸(빨간 테) */
    fun flagged(warnings: List<PolarWarning>): Set<PolarField> = bad() + warnings.flatMap { w ->
        when (w) {
            PolarWarning.W_GE_OD, PolarWarning.HOLE_GT_W -> listOf(PolarField.OD, PolarField.WIDTH)
            PolarWarning.CORNER_R_TOO_BIG -> listOf(PolarField.CORNER_R)
            PolarWarning.TOOL_TOO_BIG -> listOf(PolarField.TOOL_DIA)
            PolarWarning.TOOL_GT_HOLE -> listOfNotNull(PolarField.TOOL_DIA, PolarField.OD, PolarField.CLEAR.takeIf { value(it).isNotEmpty() })
            else -> emptyList()
        }
    }

    /** 자리표시: 가공 값은 빈칸이면 계산에 쓰는 값, 도면 값은 비움 */
    fun placeholder(f: PolarField): String = when (f) {
        PolarField.CORNER_R -> "0"
        PolarField.ROTATION -> "0"
        PolarField.CLEAR -> "기본 ${plainNum(PolarG12.CLEAR_DEFAULT)}"
        else -> ""
    }

    /** 키패드 안내 줄 */
    fun hint(f: PolarField): String = when (f) {
        PolarField.OD -> if (inner) "다각형을 깎기 전에 뚫어 둔 구멍 지름 — 공구가 이 구멍으로 내려갑니다" else "소재 외경 (직경) — 도면 Ø 그대로"
        PolarField.WIDTH -> when (shape) {
            PolarShape.TWO_FLAT -> "마주 보는 두 평면 사이 거리"
            PolarShape.TRIANGLE, PolarShape.PENTAGON -> "중심에서 변까지 × 2 (내접원 지름)"
            else -> "마주 보는 두 변 사이 거리 (스패너 치수)"
        }
        PolarField.CORNER_R -> "꼭짓점 둥글기 · 비우면 0 (날카로운 꼭짓점)"
        PolarField.ROTATION -> "X 에서 C(+) 쪽이 + · 0° = 한 변이 C 축에 직각 · 비우면 0"
        PolarField.TOOL_DIA -> "엔드밀 지름 — 공구 보정 화면 R 은 이 값의 반"
        PolarField.CLEAR -> "시작점이 소재(내측은 구멍 벽)에서 떨어지는 거리 · Z 접근 높이 (면 Z0 위) · 비우면 1"
        PolarField.DEPTH -> "깎는 바닥 Z — 면 Z0 아래라 음수 (예: −5)"
        PolarField.FEED -> "극좌표 평면의 접선 속도 mm/min (G98) — 공구 카탈로그 값으로"
    }

    companion object {
        /** 계산기가 기본값 없이 꼭 받는 칸(MISSING_INPUT) */
        val REQUIRED = listOf(PolarField.OD, PolarField.WIDTH, PolarField.TOOL_DIA, PolarField.DEPTH, PolarField.FEED)

        val Saver = listSaver<PolarState, String>(
            save = { s -> listOf(s.inner.toString(), s.shape.name, s.xDiameter.toString()) + PolarField.entries.map { s.value(it) } },
            restore = { l ->
                PolarState(
                    inner = l[0].toBoolean(),
                    shape = PolarShape.valueOf(l[1]),
                    xDiameter = l[2].toBoolean(),
                    values = PolarField.entries.mapIndexed { i, f -> f.key to l[3 + i] }.toMap(),
                )
            },
        )
    }
}

/** 끝 0 없는 숫자 글(80 · 0.5 · −5) */
internal fun plainNum(v: Double): String = Fmt.nc(v).removeSuffix(".")

/** 경고 글(빨강 · 노랑 · 파랑). sol = 숫자와 함께 나온 경고일 때 그 결과 */
internal fun polarWarningText(w: PolarWarning, s: PolarState, sol: PolarSolution? = null): String {
    fun n(f: PolarField) = s.num(f)?.let(::plainNum) ?: "?"
    return when (w) {
        PolarWarning.MISSING_INPUT -> "값을 넣으면 바로 계산합니다 — 남은 것: ${s.missing().joinToString(" · ")}."
        PolarWarning.BAD_INPUT -> badText(s)
        PolarWarning.W_GE_OD -> "W ${n(PolarField.WIDTH)} 가 외경 Ø${n(PolarField.OD)} 이상이라 평면이 소재에 닿지 않습니다. W 는 외경보다 작게 넣으세요."
        PolarWarning.HOLE_GT_W ->
            "기초 구멍 Ø${n(PolarField.OD)} 이 W ${n(PolarField.WIDTH)} 보다 커 평면 가운데가 구멍에 끊깁니다. 구멍 지름은 W 이하여야 합니다."
        PolarWarning.CORNER_R_TOO_BIG -> "코너 R ${n(PolarField.CORNER_R)} 은 W/2 (${s.num(PolarField.WIDTH)?.let { plainNum(it / 2) } ?: "?"}) 보다 작아야 합니다 — 평면이 없어집니다."
        PolarWarning.TOOL_TOO_BIG ->
            "내측은 공구경이 W/2 (${s.num(PolarField.WIDTH)?.let { plainNum(it / 2) } ?: "?"}) 보다 작아야 합니다. " +
                "변 가운데로 들어가는 진입 원호(공구 반경보다 큰 원)를 넣을 자리가 없습니다."
        PolarWarning.TOOL_GT_HOLE -> {
            val r = s.num(PolarField.TOOL_DIA)?.let { it / 2 }
            val clear = s.num(PolarField.CLEAR) ?: PolarG12.CLEAR_DEFAULT
            "공구가 기초 구멍에 들어가지 못합니다 — 공구 반경 + 여유 (${r?.let { plainNum(it + clear) } ?: "?"}) 가 구멍 반경 " +
                "(${s.num(PolarField.OD)?.let { plainNum(it / 2) } ?: "?"}) 보다 큽니다. 구멍을 키우거나 공구 · 여유를 줄이세요."
        }
        PolarWarning.INNER_CORNER_LT_TOOL -> {
            val r = sol?.toolR ?: 0.0
            "코너 R ${s.num(PolarField.CORNER_R)?.let(::plainNum) ?: "0"} 이 공구 반경 ${plainNum(r)} 보다 작아 모서리는 R${plainNum(r)} 로 남습니다. " +
                "기계 보정 프로그램은 그 R 원호를 빼고 날카로운 꼭짓점으로 씁니다 (보정 간섭 알람을 피하려고 — 깎인 모양은 같음). " +
                "도면 R 이 꼭 필요하면 공구경을 R × 2 이하로 줄이세요."
        }
        PolarWarning.STOCK_LEFT -> {
            val where = if (s.inner) "구멍 둘레 모서리 쪽" else "바깥 (소재 원통과 평면 사이)"
            "가장 두꺼운 살 ${sol?.let { Fmt.fixed3(it.stockMax) } ?: "?"} 이 공구경 ${n(PolarField.TOOL_DIA)} 보다 두꺼워 한 바퀴로 다 못 깎습니다 — $where 에 살이 남습니다. " +
                "공구경을 키우거나 여러 번 나눠 깎으세요 (이 앱은 한 바퀴만 만듭니다)."
        }
        PolarWarning.CORNERS_OUTSIDE_OD ->
            "꼭짓점 지름 Ø${sol?.let { Fmt.fixed3(it.cornerDia) } ?: "?"} 이 외경 Ø${n(PolarField.OD)} 보다 커 모서리 끝은 소재 원통면으로 남습니다 " +
                "(꼭짓점이 다 서지 않음). 도면 확인용 — 프로그램은 그대로 나옵니다."
    }
}

/** 범위 밖 값 글: 칸마다 받는 범위 */
private fun badText(s: PolarState): String {
    val max = plainNum(MAX_MM)
    val parts = s.bad().map { f ->
        when (f) {
            PolarField.OD -> "${if (s.inner) "기초 구멍 지름은" else "외경은"} 0 보다 크고 $max 이하"
            PolarField.WIDTH -> "W 는 0 보다 크고 $max 이하"
            PolarField.TOOL_DIA -> "공구경은 0 보다 크고 $max 이하"
            PolarField.CORNER_R -> "코너 R 은 0 이상 $max 이하"
            PolarField.CLEAR -> "여유는 0 이상 $max 이하"
            PolarField.ROTATION -> "회전은 −$max ~ $max°"
            PolarField.DEPTH -> "깊이 Z 는 0 보다 작게 (면 Z0 아래, 예: −5)"
            PolarField.FEED -> "이송 F 는 0 보다 크고 ${plainNum(PolarG12.MAX_FEED)} mm/min 이하"
        }
    }
    val shape = if (s.shapeBad) listOf("2면폭은 외측 가공만 됩니다 — 내측은 다른 도형을 고르세요") else emptyList()
    return "범위 밖 값 — ${(shape + parts).joinToString(" · ")}."
}

/** NC 줄의 낱말 하나. dim = 앞 줄과 같아 프로그램에서 뺀 좌표를 흐리게 보인 것(원본 07 — 복사에는 없음) */
internal data class NcTok(val text: String, val dim: Boolean = false)

/**
 * 시뮬레이션 G코드 목록의 한 줄: 극좌표 평면을 움직이는 줄에서 앞 줄과 같아 뺀 X · C 를 흐린 낱말로 제자리에 넣는다(원본 07 의 `C0.` · `X-87.757`).
 * 뺀 값은 블록의 줄 끝 지령값(PolarBlock.x · c) 그대로 — 계산기와 같은 Fmt.nc. 흐린 낱말을 빼고 이으면 NC 글 그대로다
 */
internal fun ncTokens(b: PolarBlock): List<NcTok> {
    if (b.text.startsWith("(")) return listOf(NcTok(b.text))
    val words = b.text.split(' ').map { NcTok(it) }.toMutableList()
    if (!b.polar || b.path.isEmpty()) return words
    val gEnd = words.indexOfFirst { !it.text.startsWith("G") }.let { if (it < 0) words.size else it }
    var at = gEnd
    if (words.none { it.text.startsWith("X") }) {
        b.x?.let { words.add(at, NcTok("X${Fmt.nc(it)}", dim = true)) }
    }
    at = words.indexOfFirst { it.text.startsWith("X") }.let { if (it < 0) gEnd else it + 1 }
    if (words.none { it.text.startsWith("C") }) {
        b.c?.let { words.add(at, NcTok("C${Fmt.nc(it)}", dim = true)) }
    }
    return words
}

/** 결과 칸(이름 · 값 · 아래 글) — 화면과 골든이 같이 쓴다. direct = 직접 보정(G40) 프로그램을 보는 중 */
internal fun polarResultCells(s: PolarState, sol: PolarSolution, direct: Boolean): List<Triple<String, String, String>> {
    val two = s.shape == PolarShape.TWO_FLAT
    return listOf(
        Triple(
            "공구 보정 화면",
            if (direct) "보정 없음" else "R${Fmt.fixed3(sol.toolR)} · T0",
            if (direct) "G40 · 좌표에 공구 반경 들어 있음" else "G41 · 가상 인선 0 = 공구 중심",
        ),
        Triple("시작점 (G00)", "X${Fmt.nc(2 * sol.startX)} C0.", if (s.inner) "G12.1 앞 · 구멍 안" else "G12.1 앞 · 소재 밖"),
        Triple(if (two) "외경 (모서리 없음)" else "꼭짓점 지름", "Ø${Fmt.fixed3(sol.cornerDia)}", if (two) "2면폭은 원통면이 남음" else if (s.shape.sides % 2 == 0) "맞모서리" else "꼭짓점을 지나는 원"),
        Triple("평면 길이", Fmt.fixed3(sol.flatLength), if (two) "소재 안 현의 길이" else "코너 R 접점 사이"),
        Triple(
            "진입 · 후퇴 원호",
            "R${Fmt.fixed3(if (direct) sol.leadR - sol.toolR else sol.leadR)}",
            if (direct) "공구 중심 (도면 R − 공구 반경)" else "변 가운데 · 사분원",
        ),
        Triple("가장 두꺼운 살", Fmt.fixed3(sol.stockMax), "공구경 ${plainNum(2 * sol.toolR)} 이하면 한 바퀴"),
    )
}
