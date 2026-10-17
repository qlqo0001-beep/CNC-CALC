package com.bmt.nosecalc.screens.taper

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.CompPoint
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.GrooveWarning
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperPoint
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning

/** 가공 종류. 순서 = 02 의 탭 번호(1줄 3칸 + 2줄 2칸) */
internal enum class TaperKind(val label: String, val types: Int, val inner: Boolean) {
    OUTER("외경", 5, false),
    INNER("내경", 5, true),
    GROOVE("외경 홈", 2, false),
    OUTER_DOUBLE("외경 2중 테이퍼", 4, false),
    INNER_DOUBLE("내경 2중 테이퍼", 4, true),
    ;

    val double: Boolean get() = this == OUTER_DOUBLE || this == INNER_DOUBLE
    val groove: Boolean get() = this == GROOVE

    /** 점 개수(T1, T2, T3) */
    val points: Int get() = if (double) 3 else 2

    companion object {
        val tabRows = listOf(listOf(OUTER, INNER, GROOVE), listOf(OUTER_DOUBLE, INNER_DOUBLE)).map { r -> r.map { it.label } }
    }
}

/**
 * 앞(T1 앞)·뒤(마지막 T 뒤) 요소가 단면인가 — 시작·끝점 칸이 X(직경)를 받는지(아니면 Z). calc-design.md 의 TYPE 표.
 * 겹테이퍼는 둘 다 수평. 화면 역방향은 그림을 뒤집을 뿐이라 같다
 */
internal fun legsVertical(kind: TaperKind, type: Int): Pair<Boolean, Boolean> =
    if (kind.double) false to false else (type <= 2) to (type == 2 || type == 4)

/** 형상(TYPE) 이름(가공 순서로 읽는다: 앞 요소 → 경사 → 뒤 요소) */
internal fun typeName(kind: TaperKind, type: Int): String = when (kind) {
    TaperKind.OUTER -> listOf("단면 → 면취 → 외경", "단면 → 경사 → 단면", "작은 외경 → 테이퍼 → 큰 외경", "작은 외경 → 테이퍼 → 어깨 단면", "역테이퍼 (척 쪽으로 작아짐)")
    TaperKind.INNER -> listOf("단면 → 입구 면취 → 내경", "단면 → 경사 → 단면", "큰 내경 → 테이퍼 → 작은 내경", "큰 내경 → 테이퍼 → 안쪽 단면", "역테이퍼 (안쪽으로 커짐)")
    TaperKind.OUTER_DOUBLE -> listOf("지름이 두 번 커짐 (2단 테이퍼)", "가운데 지름이 큼 (산)", "가운데 지름이 작음 (골)", "지름이 두 번 작아짐 (역테이퍼 2단)")
    TaperKind.INNER_DOUBLE -> listOf("지름이 두 번 작아짐 (안쪽 2단 테이퍼)", "가운데 지름이 작음", "가운데 지름이 큼", "지름이 두 번 커짐 (역테이퍼 2단)")
    TaperKind.GROOVE -> listOf("왼쪽 인선 기준 (T3)", "오른쪽 인선 기준 (T4)")
}[type - 1]

/**
 * 테이퍼 화면 상태. 입력값은 쓴 그대로의 글로 둔다.
 * 점 칸(X·Z·각도·모서리 R·시작점·끝점)은 (종류, TYPE, 방향)마다 따로다 — 방향을 바꾸면 그림이 좌우로 뒤집혀 T1·T2 의
 * Z 순서가 바뀌고, TYPE 마다 X 오르내림과 시작·끝점의 뜻(X 또는 Z)이 다르다. 노즈R·Z 여유는 공구·기계 쪽 값이라 함께 쓴다.
 */
@Stable
internal class TaperState(
    kind: TaperKind = TaperKind.OUTER,
    reverse: Boolean = false,
    type: Int? = null,
    open: Boolean = false,
    values: Map<String, String> = emptyMap(),
) {
    var kind by mutableStateOf(kind)
    var reverse by mutableStateOf(reverse)

    /** 고른 TYPE(A 화면에서 강조, B 화면의 대상) */
    var type by mutableStateOf(type)

    /** true = B 화면(입력·결과) */
    var open by mutableStateOf(open)
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    private val dir get() = if (reverse) "r" else "f"

    fun point(name: String): String = values[pointKey(name)] ?: TaperDefaults.point(kind, type ?: 1, reverse, name)
    fun setPoint(name: String, v: String) { values[pointKey(name)] = v }
    private fun pointKey(name: String) = "p/${kind.name}/$type/$dir/$name"

    /** 팁 번호 직접 지정(종류·방향마다). null = 표준 */
    var tip: Int?
        get() = values["tip/${kind.name}/$dir"]?.toIntOrNull()
        set(v) { values["tip/${kind.name}/$dir"] = v?.toString() ?: "" }

    fun shared(name: String): String = values["s/$name"] ?: TaperDefaults.shared(name)
    fun setShared(name: String, v: String) { values["s/$name"] = v }

    /** NC 카드에서 고른 프로그램: 0 = G41/G42(기본), 1 = 직접 보정(G40) */
    var ncTab: Int
        get() = values["ui/nc"]?.toIntOrNull() ?: 0
        set(v) { values["ui/nc"] = v.toString() }

    companion object {
        val Saver = listSaver<TaperState, String>(
            save = { s ->
                listOf(s.kind.name, s.reverse.toString(), s.type?.toString() ?: "", s.open.toString()) +
                    s.values.flatMap { listOf(it.key, it.value) }
            },
            restore = { l ->
                TaperState(
                    TaperKind.valueOf(l[0]), l[1].toBoolean(), l[2].toIntOrNull(), l[3].toBoolean(),
                    l.drop(4).chunked(2).associate { it[0] to it[1] },
                )
            },
        )
    }
}

/** 처음 값. 그림 모양대로 맞는 값이라 TYPE 을 누르면 바로 결과가 보인다 */
internal object TaperDefaults {
    /** 정방향 (X 직경, Z) — T1(오른쪽)부터 */
    private fun forward(kind: TaperKind, type: Int): Pair<List<Double>, List<Double>> = when (kind) {
        TaperKind.OUTER -> (if (type == 5) listOf(50.0, 40.0) else listOf(40.0, 50.0)) to listOf(0.0, -5.0)
        TaperKind.INNER -> (if (type == 5) listOf(30.0, 40.0) else listOf(40.0, 30.0)) to listOf(0.0, -5.0)
        TaperKind.OUTER_DOUBLE -> when (type) {
            1 -> listOf(40.0, 46.0, 52.0) to listOf(0.0, -6.0, -9.0)
            2 -> listOf(40.0, 50.0, 40.0) to listOf(0.0, -5.0, -10.0)
            3 -> listOf(50.0, 40.0, 50.0) to listOf(0.0, -5.0, -10.0)
            else -> listOf(52.0, 46.0, 40.0) to listOf(0.0, -3.0, -9.0)
        }
        TaperKind.INNER_DOUBLE -> when (type) {
            1 -> listOf(52.0, 46.0, 40.0) to listOf(0.0, -6.0, -9.0)
            2 -> listOf(50.0, 40.0, 50.0) to listOf(0.0, -5.0, -10.0)
            3 -> listOf(40.0, 50.0, 40.0) to listOf(0.0, -5.0, -10.0)
            else -> listOf(40.0, 46.0, 52.0) to listOf(0.0, -3.0, -9.0)
        }
        TaperKind.GROOVE -> emptyList<Double>() to emptyList()
    }

    /**
     * name = x1·z1·x2·z2·x3·z3·ang·r1·r2·r3(모서리 R)·start·end. 역방향은 그림이 뒤집혀 T1 이 왼쪽 — 등 쪽 면(Z −30 부근)을
     * 역바이트로 깎는 값. 각도·모서리 R(0)·시작·끝점(5 mm)은 비워 둔다
     */
    fun point(kind: TaperKind, type: Int, reverse: Boolean, name: String): String {
        if (name[0] != 'x' && name[0] != 'z') return ""
        val (xs, zs) = forward(kind, type)
        val i = name.drop(1).toInt() - 1
        if (i >= xs.size) return ""
        val v = if (name[0] == 'x') xs[i] else if (reverse) -30 - zs[i] else zs[i]
        return text(v)
    }

    fun shared(name: String): String = when (name) {
        "noseR" -> "0.4"
        "zClear" -> "1.0"
        // 홈(calc-design.md 의 예: Z −13, −15.5, −18)
        "zStart" -> "-10"
        "width" -> "8"
        "od" -> "40"
        "bottom" -> "30"
        "insert" -> "3"
        "overlap" -> "0.5"
        "xClear" -> "1.0"
        else -> ""
    }

    private fun text(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
}

/** 화면 입력을 숫자로 바꾼 것. x·z 는 T1 부터(X 직경), 빈칸은 null */
internal data class SlopeForm(
    val kind: TaperKind,
    val type: Int,
    val reverse: Boolean,
    val noseR: Double,
    val tip: Int?,
    val x: List<Double?>,
    val z: List<Double?>,
    val angle: Double?,
    val zClear: Double,
    /** 점마다 모서리 R(빈칸 = 0) */
    val corners: List<Double>,
    /** 시작점(T1 앞)·끝점(마지막 T 뒤): 그 요소가 단면이면 X(직경), 수평이면 Z. null = 5 mm */
    val start: Double? = null,
    val end: Double? = null,
)

/**
 * 테이퍼 풀기. 정방향은 계산기 그대로.
 * 역방향(Rev.)은 **그림을 좌우로 뒤집은 형상**을 역바이트로 Z+ 쪽으로 깎는 것이라(T1 = 처음 깎는 왼쪽 점),
 * Z 를 뒤집어 정방향 형상으로 풀고 결과(점·경로·진입점·NC·팁·보정 쪽)를 다시 뒤집는다.
 * 노즈R 보정은 거울에 대해 그대로 옮겨지는 기하라(팁 3↔4, 2↔1, G42↔G41) 계산기의 정방향 해법을 그대로 쓴다.
 * 계산기의 reverse = true 는 "형상은 그대로 두고 방향만 Z+" 라서 화면의 역방향과 뜻이 다르다(TaperSolveTest 가 두 쪽을 맞춰 본다).
 */
internal object TaperSolve {
    fun solve(f: SlopeForm): TaperResult {
        val m = f.reverse
        fun zz(v: Double?) = if (m) v?.let { -it } else v
        val tip = if (m) f.tip?.let(Mirror::tip) else f.tip
        // 시작·끝점이 Z 면 같이 뒤집는다(X 는 그대로)
        val (fv, bv) = legsVertical(f.kind, f.type)
        val start = if (fv) f.start else zz(f.start)
        val end = if (bv) f.end else zz(f.end)
        val c = f.corners
        val r = if (f.kind.double) {
            TaperCalc.calcDouble(
                DoubleTaperInput(
                    f.kind.inner, false, f.noseR, tip,
                    f.x[0], zz(f.z[0]), f.x[1], zz(f.z[1]), f.x[2], zz(f.z[2]), f.zClear,
                    cornerR1 = c[0], cornerR2 = c[1], cornerR3 = c[2], start = start, end = end,
                ),
            ).let { doubleShape(it, f) }
        } else {
            TaperCalc.calc(
                TaperInput(
                    f.kind.inner, f.type, false, f.noseR, tip, f.x[0], zz(f.z[0]), f.x[1], zz(f.z[1]), f.angle, f.zClear,
                    cornerR1 = c[0], cornerR2 = c[1], start = start, end = end,
                ),
            )
        }
        return if (m) r.copy(solution = r.solution?.let(Mirror::solution)) else r
    }

    /**
     * 겹테이퍼 계산기는 세 점이면 어떤 꺾임도 받는다. 고른 TYPE 그림과 X 오르내림이 다르면 멈춘다.
     * TYPE 1 = 두 번 커짐, 2 = 가운데가 큼, 3 = 가운데가 작음, 4 = 두 번 작아짐(외경 기준, 내경은 반대).
     */
    private fun doubleShape(r: TaperResult, f: SlopeForm): TaperResult {
        val p = r.solution?.points ?: return r
        val s = if (f.kind.inner) -1 else 1
        val up1 = s * (p[1].vertex.x - p[0].vertex.x) > 0
        val up2 = s * (p[2].vertex.x - p[1].vertex.x) > 0
        val want = when (f.type) {
            1 -> true to true
            2 -> true to false
            3 -> false to true
            else -> false to false
        }
        return if (up1 to up2 == want) r else TaperResult(null, listOf(TaperWarning.SHAPE_MISMATCH))
    }
}

/** Z 뒤집기(그림 좌우 반전) */
internal object Mirror {
    /** 팁 벡터 (Δz, Δx) → (−Δz, Δx): 1↔2, 3↔4, 5↔7, 6·8·0·9 그대로 */
    fun tip(t: Int): Int = when (t) {
        1 -> 2
        2 -> 1
        3 -> 4
        4 -> 3
        5 -> 7
        7 -> 5
        else -> t
    }

    fun pt(p: Pt) = Pt(-p.z, p.x)

    fun contour(c: Contour) = Contour(
        c.segs.map { s ->
            when (s) {
                is Line -> Line(pt(s.start), pt(s.end))
                is Arc -> Arc(pt(s.start), pt(s.end), pt(s.center), !s.cw)
            }
        },
    )

    private val zWord = Regex("""Z(-?)(\d+\.\d*)""")

    private val tipWord = Regex("""T(\d)\)""")
    private val swap = mapOf("G02" to "G03", "G03" to "G02", "G41" to "G42", "G42" to "G41")

    /**
     * NC 줄 뒤집기: Z 부호, 원호 방향(G02↔G03), 보정 쪽(G41↔G42), 주석 `(R0.4 T3)` 의 팁 번호.
     * Fmt.nc 는 절댓값을 반올림하고 부호를 붙이므로(0 은 부호 없음) 글에서 부호를 바꾼 것이 뒤집은 점을 Fmt.nc 로 쓴 것과 같다.
     * 앞 줄과 같은 값을 빼는 규칙도 글끼리의 같음이라 그대로 맞는다.
     */
    fun nc(line: String): String {
        if (line.startsWith("(")) return line.replace(tipWord) { "T${tip(it.groupValues[1].toInt())})" }
        return line.replace(zWord) { m ->
            val (minus, num) = m.destructured
            when {
                minus.isNotEmpty() -> "Z$num"
                num.all { it == '0' || it == '.' } -> "Z$num"
                else -> "Z-$num"
            }
        }.split(' ').joinToString(" ") { swap[it] ?: it }
    }

    fun solution(s: TaperSolution) = s.copy(
        points = s.points.map { p ->
            TaperPoint(
                pt(p.vertex),
                p.nodes.map { CompPoint(pt(it.drawn), pt(it.prog)) },
                p.corner?.let { it.copy(cw = !it.cw, center = pt(it.center)) },
            )
        },
        tip = tip(s.tip),
        side = if (s.side == Side.G41) Side.G42 else Side.G41,
        entry = pt(s.entry),
        exit = pt(s.exit),
        ncG41G42 = s.ncG41G42.map(::nc),
        ncG40 = s.ncG40.map(::nc),
        drawing = contour(s.drawing),
        center = contour(s.center),
        program = contour(s.program),
    )
}

/** 경고 글. 계산을 멈춘 것(solution = null)은 빨강, 나머지는 노랑 */
internal fun taperWarningText(w: TaperWarning, kind: TaperKind): String = when (w) {
    TaperWarning.MISSING_INPUT ->
        if (kind.double) "값이 모자랍니다. T1·T2·T3 의 X·Z 를 모두 넣으세요."
        else "값이 모자랍니다. X1·Z1·X2·Z2·각도 가운데 넷을 넣으세요(하나만 비워 둡니다)."
    TaperWarning.CONTRADICTION -> "다섯 값이 서로 맞지 않습니다. 각도를 비우면 두 점으로 각도를 계산합니다."
    TaperWarning.BAD_ANGLE -> "각도는 0° 보다 크고 90° 보다 작아야 합니다. 두 점이 수평·수직으로 놓이면 경사가 아닙니다."
    TaperWarning.SHAPE_MISMATCH -> "값이 고른 형상 그림과 맞지 않습니다. 점의 좌우 순서(그림의 T1·T2 자리)와 X 가 커지는 쪽을 확인하세요."
    TaperWarning.BAD_INPUT -> "입력이 잘못됐습니다. 지름은 0 보다 크고, 노즈R·모서리 R 은 0 이상, Z 여유는 0 보다 커야 합니다. 좌표·치수는 ±${MAX_MM.toInt()} mm 안이어야 합니다(각도가 너무 작으면 점이 그 밖으로 나갑니다)."
    TaperWarning.BAD_LEG ->
        "시작점·끝점 값이 맞지 않습니다. 그 요소 방향(단면은 X, 수평은 Z)으로 T 점 바깥에 있어야 하고, 노즈R 보다 충분히 떨어져야 합니다 " +
            "(가까우면 공구가 되돌아가거나 노즈가 그 요소에 닿지 못합니다)."
    TaperWarning.CORNER_R_TOO_BIG ->
        "모서리 R 이 들어가지 않습니다. 접점이 경사나 앞뒤 요소(시작·끝점까지)를 넘거나, 두 R 의 접점이 경사 위에서 겹칩니다. R 을 줄이세요."
    TaperWarning.NOSE_UNREACHABLE -> "노즈R 에 비해 경사(또는 앞뒤 요소)가 너무 짧아 공구가 닿지 못합니다. 도면 형상이 안 나옵니다 — 노즈R 을 줄이세요."
    TaperWarning.NOSE_GT_SLOPE -> "노즈R 이 경사 길이보다 큽니다. 숫자는 맞지만 오목 모서리 쪽에 노즈R 이 남아 경사가 거의 안 남을 수 있습니다. 도면을 확인하세요."
    TaperWarning.CONCAVE_R_LE_NOSE ->
        "오목 모서리 R 이 노즈R 이하입니다. 같으면 꼭 맞고, 작으면 그 자리에 노즈R 만큼의 R 이 남습니다. G41/G42 프로그램은 기계가 간섭 알람을 낼 수 있습니다."
    TaperWarning.AGAINST_TOOL -> "형상 일부가 바이트 방향과 반대입니다(역테이퍼 꼴·반대쪽 단면). 실제 바이트의 끝날 여유각이 맞아야 깎입니다. 정/역방향을 확인하세요."
    TaperWarning.ENTRY_PAST_AXIS -> "진입점이 중심(X0)을 넘습니다. Z 여유를 줄이세요."
}

internal fun grooveWarningText(w: GrooveWarning): String = when (w) {
    GrooveWarning.MISSING_INPUT -> "값이 모자랍니다. 시작 Z·홈 폭·외경·바닥 지름·인서트 폭을 모두 넣으세요."
    GrooveWarning.BAD_INPUT -> "입력이 잘못됐습니다. 폭은 0 보다 크고, 바닥 지름·겹침·X 여유는 0 이상, 모든 값은 ±${MAX_MM.toInt()} mm 안이어야 합니다."
    GrooveWarning.TOOL_WIDER_THAN_GROOVE -> "침범: 인서트 폭 T 가 홈 폭 W 보다 큽니다. 홈이 도면보다 넓어집니다."
    GrooveWarning.BOTTOM_NOT_BELOW_OD -> "침범: 바닥 지름 d 가 외경 D 보다 작지 않습니다."
    GrooveWarning.OVERLAP_TOO_BIG -> "겹침이 인서트 폭 이상입니다. 옆으로 나아가지 못합니다."
    GrooveWarning.TOO_MANY_STEPS -> "단이 1000 개를 넘습니다. 겹침·폭 값을 확인하세요."
}
