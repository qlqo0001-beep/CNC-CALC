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
import com.bmt.nosecalc.calc.GrooveInput
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
import kotlin.math.abs

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
    TaperKind.GROOVE -> listOf("왼쪽 인선 기준 (팁 3번)", "오른쪽 인선 기준 (팁 4번)")
}[type - 1]

/** 형상 카드 머리 띠의 짧은 이름(A 화면). 긴 이름은 [typeName] */
internal fun typeShort(kind: TaperKind, type: Int): String = when (kind) {
    TaperKind.OUTER -> listOf("끝 면취", "경사 단차", "테이퍼", "테이퍼·어깨", "역테이퍼")
    TaperKind.INNER -> listOf("입구 면취", "경사 단차", "테이퍼", "테이퍼·안쪽 턱", "역테이퍼")
    TaperKind.OUTER_DOUBLE -> listOf("2단 커짐", "가운데 큼", "가운데 작음", "2단 작아짐")
    TaperKind.INNER_DOUBLE -> listOf("2단 작아짐", "가운데 작음", "가운데 큼", "2단 커짐")
    TaperKind.GROOVE -> listOf("왼쪽 인선", "오른쪽 인선")
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

    fun point(name: String): String = values[pointKey(name)] ?: TaperDefaults.point(name)
    fun setPoint(name: String, v: String) {
        // 비어 있던(자동) 다섯 값 칸에 값이 들어오면 기억한다 — 다섯 값이 어긋나면 그 칸을 다시 비우자고 권한다
        if (name in FIVE && point(name).isEmpty() && v.isNotEmpty()) values[lastAutoKey] = name
        values[pointKey(name)] = v
    }
    private fun pointKey(name: String) = "p/${kind.name}/$type/$dir/$name"

    /** 비어 있다가 마지막으로 값이 들어온 다섯 값 칸(x1 · z1 · x2 · z2 · ang, 종류 · 형상 · 방향마다). 없으면 null */
    val lastAuto: String? get() = values[lastAutoKey]
    private val lastAutoKey get() = "ui/lastAuto/${kind.name}/$type/$dir"

    /** 팁 번호 직접 지정(종류·방향마다). null = 표준 */
    var tip: Int?
        get() = values["tip/${kind.name}/$dir"]?.toIntOrNull()
        set(v) { values["tip/${kind.name}/$dir"] = v?.toString() ?: "" }

    fun shared(name: String): String = values["s/$name"] ?: TaperDefaults.shared(name)
    fun setShared(name: String, v: String) { values["s/$name"] = v }

    /** 입력 화면을 열 때 키패드를 열어 둘 칸 id(x1 · z2 · insert …). 화면이 처음 그려질 때 한 번 읽는다(스크린샷 시험) */
    var editing: String?
        get() = values["ui/editing"]?.ifEmpty { null }
        set(v) { values["ui/editing"] = v.orEmpty() }

    /** 다섯 값이 어긋날 때(CONTRADICTION) 비우자고 권할 칸: 마지막에 채운 자동 칸 · 각도 가운데 지금 찬 것(둘까지) */
    fun contradictionFixes(): List<String> = listOfNotNull(lastAuto, "ang").distinct().filter { point(it).isNotEmpty() }

    companion object {
        /** 단일 테이퍼에서 하나를 비워 계산하는 다섯 값 */
        private val FIVE = setOf("x1", "z1", "x2", "z2", "ang")

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

/**
 * 처음 값. 도면 치수(점 X · Z, 홈 시작 Z · 폭 · 외경 · 바닥)와 여유(Z 여유 · X 여유)는 0 에서 시작한다 — 예시 값이 도면 값처럼
 * 남지 않게. 공구 값(노즈 R · 인서트 폭 · 겹침)은 흔한 값, 각도 · 모서리 R · 시작 · 끝점은 비워 둔다
 */
internal object TaperDefaults {
    /** name = x1·z1·x2·z2·x3·z3·ang·r1·r2·r3(모서리 R)·start·end */
    fun point(name: String): String = if (name[0] == 'x' || name[0] == 'z') "0" else ""

    fun shared(name: String): String = when (name) {
        "noseR" -> "0.4"
        "insert" -> "3"
        "overlap" -> "0.5"
        "zClear", "zStart", "width", "od", "bottom", "xClear" -> "0"
        else -> ""
    }
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
    /** 시작점(T1 앞)·끝점(마지막 T 뒤): 그 요소가 단면이면 X(직경), 수평이면 Z. null = 그 요소는 깎지 않는다(그림은 5 mm) */
    val start: Double? = null,
    val end: Double? = null,
) {
    /**
     * 도면 치수를 아직 다 넣지 않았다: X(직경) 0 인 점이 있거나 두 점 이상이 Z 0 에 겹친다(처음 값 그대로).
     * 이때 계산이 멈춘 것은 빨간 멈춤이 아니라 "값을 넣으면 계산합니다" 안내로 보인다
     */
    val waiting: Boolean get() = x.any { it == 0.0 } || z.count { it == 0.0 } > 1

    /** Z 가 방향과 거꾸로 간다: 정방향은 척 쪽(Z−)으로 깎으니 마지막 Z 가 T1 보다 크면, 역방향은 작으면 */
    val zBackward: Boolean
        get() {
            val a = z.first() ?: return false
            val b = z.last() ?: return false
            return if (reverse) b < a else b > a
        }
}

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

/** 깎기가 끝나는 도면 점(끝점 칸의 자동 값): 끝점을 넣었으면 그 좌표, 비웠으면 마지막 T 점(모서리 R 이면 그 끝) */
internal val TaperSolution.cutEnd: Pt get() = drawing.segs.let { if (toEnd) it.last().end else it[it.lastIndex - 1].end }

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

/**
 * 경고 글. 계산을 멈춘 것(solution = null)은 빨강, 나머지는 노랑. 값이 모자란 것(MISSING_INPUT)은 파란 안내 글.
 * 형상 불일치가 Z 를 거꾸로 넣은 탓이면([SlopeForm.zBackward]) 방향마다 Z 가 어느 쪽으로 가는지 예를 든다
 */
internal fun taperWarningText(w: TaperWarning, f: SlopeForm): String = when (w) {
    TaperWarning.MISSING_INPUT ->
        if (f.kind.double) "세 점의 X · Z 를 모두 넣으면 바로 계산합니다."
        else "값을 넣으면 바로 계산합니다 — X1 · Z1 · X2 · Z2 · 각도 가운데 넷."
    TaperWarning.CONTRADICTION -> "다섯 값이 모두 찼고 서로 맞지 않습니다. 하나를 비워 계산하세요."
    TaperWarning.BAD_ANGLE -> "각도는 0° 보다 크고 90° 보다 작아야 합니다. 두 점이 수평·수직으로 놓이면 경사가 아닙니다."
    TaperWarning.SHAPE_MISMATCH -> when {
        !f.zBackward -> "값이 고른 형상 그림과 맞지 않습니다. 점의 좌우 순서(그림의 T1·T2 자리)와 X 가 커지는 쪽을 확인하세요."
        f.reverse -> "Z 부호 · 순서를 확인하세요. 역방향은 T1 이 척 쪽이라 Z 가 커집니다 (예: Z1 −30 → Z2 −25)."
        else -> "Z 부호 · 순서를 확인하세요. 정방향은 척 쪽으로 깎아 Z 가 작아집니다 (예: Z1 0 → Z2 −5). 척 쪽 Z 는 − 입니다."
    }
    TaperWarning.BAD_INPUT ->
        "입력이 잘못됐습니다. 지름은 0 보다 크고, 노즈R·모서리 R·Z 여유는 0 이상이어야 합니다(노즈R 이 0 이면 Z 여유를 넣으세요 — 진입 거리가 없습니다). " +
            "좌표·치수는 ±${MAX_MM.toInt()} mm 안이어야 합니다(각도가 너무 작으면 점이 그 밖으로 나갑니다)."
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
    TaperWarning.ENTRY_PAST_AXIS -> "진입점이나 후퇴점이 중심(X0)을 넘습니다. Z 여유를 줄이세요."
}

internal fun grooveWarningText(w: GrooveWarning): String = when (w) {
    GrooveWarning.MISSING_INPUT -> "시작 Z · 홈 폭 · 외경 · 바닥 지름 · 인서트 폭을 넣으면 바로 계산합니다."
    GrooveWarning.BAD_INPUT -> "입력이 잘못됐습니다. 폭은 0 보다 크고, 바닥 지름·겹침·X 여유는 0 이상, 모든 값은 ±${MAX_MM.toInt()} mm 안이어야 합니다."
    GrooveWarning.TOOL_WIDER_THAN_GROOVE -> "침범: 인서트 폭 T 가 홈 폭 W 보다 큽니다. 홈이 도면보다 넓어집니다."
    GrooveWarning.BOTTOM_NOT_BELOW_OD -> "침범: 바닥 Ød 가 외경 ØD 보다 작지 않습니다."
    GrooveWarning.OVERLAP_TOO_BIG -> "겹침이 인서트 폭 이상입니다. 옆으로 나아가지 못합니다."
    GrooveWarning.TOO_MANY_STEPS -> "단이 1000 개를 넘습니다. 겹침·폭 값을 확인하세요."
}

/**
 * 계산을 멈춘 경고가 가리키는 칸(빨간 테 + 멈춤 아이콘). 키 = x1 · z1 · … · ang · r1 … · start · end · noseR · zClear.
 * 숫자와 함께 나오는 노랑 경고와 값이 모자란 안내(MISSING_INPUT)는 칸을 칠하지 않는다
 */
internal fun taperWarningFields(w: TaperWarning, f: SlopeForm): Set<String> {
    val xs = f.x.indices.map { "x${it + 1}" }
    val zs = f.z.indices.map { "z${it + 1}" }
    fun big(v: Double?) = v != null && abs(v) > MAX_MM
    return when (w) {
        TaperWarning.CONTRADICTION -> setOf("x1", "z1", "x2", "z2", "ang")
        // 각도 칸이 범위 밖이 아니면 두 점이 수평 · 수직으로 놓인 것
        TaperWarning.BAD_ANGLE -> if (f.angle.let { it != null && (it <= 0 || it >= 90) }) setOf("ang") else (xs + zs).toSet()
        TaperWarning.SHAPE_MISMATCH -> if (f.zBackward) zs.toSet() else (xs + zs).toSet()
        TaperWarning.BAD_INPUT -> buildSet {
            f.x.forEachIndexed { i, v -> if (v != null && (v <= 0 || big(v))) add(xs[i]) }
            f.z.forEachIndexed { i, v -> if (big(v)) add(zs[i]) }
            f.corners.forEachIndexed { i, v -> if (v < 0 || big(v)) add("r${i + 1}") }
            if (f.noseR.isNaN() || f.noseR < 0 || big(f.noseR)) add("noseR")
            if (f.zClear < 0 || big(f.zClear) || (f.zClear == 0.0 && f.noseR == 0.0)) add("zClear")
            if (big(f.start)) add("start")
            if (big(f.end)) add("end")
        }
        // 넣은 쪽만(둘 다 비었으면 둘 다)
        TaperWarning.BAD_LEG ->
            listOfNotNull("start".takeIf { f.start != null }, "end".takeIf { f.end != null }).ifEmpty { listOf("start", "end") }.toSet()
        TaperWarning.CORNER_R_TOO_BIG -> f.corners.indices.filter { f.corners[it] > 0 }.map { "r${it + 1}" }.toSet()
        TaperWarning.NOSE_UNREACHABLE -> setOf("noseR")
        else -> emptySet()
    }
}

/** 홈 치수를 아직 넣지 않았다(폭 · 외경 0 = 처음 값) — 멈춤 대신 안내 */
internal val GrooveInput.waiting: Boolean get() = width == 0.0 || od == 0.0

/** 홈 경고가 가리키는 칸(키 = TaperState.shared 이름). 모두 계산을 멈춘다 */
internal fun grooveWarningFields(w: GrooveWarning, i: GrooveInput): Set<String> = when (w) {
    GrooveWarning.MISSING_INPUT -> emptySet()
    GrooveWarning.BAD_INPUT -> buildSet {
        fun big(v: Double?) = v != null && abs(v) > MAX_MM
        if (big(i.zStart)) add("zStart")
        if (big(i.od)) add("od")
        if (i.width.let { it != null && (it <= 0 || big(it)) }) add("width")
        if (i.insert.let { it != null && (it <= 0 || big(it)) }) add("insert")
        if (i.bottom.let { it != null && (it < 0 || big(it)) }) add("bottom")
        if (i.overlap < 0 || big(i.overlap)) add("overlap")
        if (i.xClear < 0 || big(i.xClear)) add("xClear")
    }
    GrooveWarning.TOOL_WIDER_THAN_GROOVE -> setOf("insert", "width")
    GrooveWarning.BOTTOM_NOT_BELOW_OD -> setOf("od", "bottom")
    GrooveWarning.OVERLAP_TOO_BIG -> setOf("overlap", "insert")
    GrooveWarning.TOO_MANY_STEPS -> setOf("overlap", "width")
}
