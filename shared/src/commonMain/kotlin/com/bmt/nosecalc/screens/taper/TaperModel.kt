package com.bmt.nosecalc.screens.taper

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.AngleRef
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.CompPoint
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
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
import com.bmt.nosecalc.calc.ncWords
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

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
 * 겹테이퍼는 둘 다 수평. 화면 역방향은 그림을 뒤집을 뿐이라 같다. 뒤집기(flip)는 T1 이 반대 끝이라 앞뒤가 바뀐다
 * (뒤집으면 시작이 단면인 형상은 2 · 4, 끝이 단면인 형상은 1 · 2)
 */
internal fun legsVertical(kind: TaperKind, type: Int, flip: Boolean = false): Pair<Boolean, Boolean> =
    (if (kind.double) false to false else (type <= 2) to (type == 2 || type == 4)).let { if (flip) it.second to it.first else it }

/** 형상(TYPE) 이름(가공 순서로 읽는다: 앞 요소 → 경사 → 뒤 요소). 뒤집으면(flip) 화살로 이은 이름은 순서를 거꾸로 */
internal fun typeName(kind: TaperKind, type: Int, flip: Boolean = false): String = when (kind) {
    TaperKind.OUTER -> listOf("단면 → 면취 → 외경", "단면 → 경사 → 단면", "작은 외경 → 테이퍼 → 큰 외경", "작은 외경 → 테이퍼 → 어깨 단면", "역테이퍼 (척 쪽으로 작아짐)")
    TaperKind.INNER -> listOf("단면 → 입구 면취 → 내경", "단면 → 경사 → 단면", "큰 내경 → 테이퍼 → 작은 내경", "큰 내경 → 테이퍼 → 안쪽 단면", "역테이퍼 (안쪽으로 커짐)")
    TaperKind.OUTER_DOUBLE -> listOf("지름이 두 번 커짐 (2단 테이퍼)", "가운데 지름이 큼 (산)", "가운데 지름이 작음 (골)", "지름이 두 번 작아짐 (역테이퍼 2단)")
    TaperKind.INNER_DOUBLE -> listOf("지름이 두 번 작아짐 (안쪽 2단 테이퍼)", "가운데 지름이 작음", "가운데 지름이 큼", "지름이 두 번 커짐 (역테이퍼 2단)")
    TaperKind.GROOVE -> listOf("왼쪽 인선 기준 (팁 3번)", "오른쪽 인선 기준 (팁 4번)")
}[type - 1].let { if (flip) it.split(" → ").reversed().joinToString(" → ") else it }

/**
 * 화면의 보정 쪽(G41/G42 = 진행 방향에서 본 공구 쪽). 역방향(그림 좌우 반전 · 역바이트)과 뒤집기(같은 바이트로 반대 끝부터)는
 * 둘 다 진행을 반대로 하므로 하나만 켜졌으면 반대 쪽 — 외경 형상 1 을 뒤집으면 G41. 계산 결과의 side 와 같다(TaperSolveTest)
 */
internal fun screenSide(inner: Boolean, reverse: Boolean, flip: Boolean): Side = Side.of(inner, reverse != flip)

/**
 * 뒤집기 스위치 아래 한 줄: T1 → 마지막 T 가 그림에서 어디서 어디로 가는지와 보정 쪽(예: "T1 아래 → T2 위 · G42").
 * 홑테이퍼는 위아래 — 외경 1~4 는 T1 이 아래(5 는 위), 내경은 그림이 위아래로 뒤집혀 반대. 2중은 위아래가 한쪽으로 가지 않아(산 · 골)
 * 좌우 — 정방향은 T1 이 오른쪽, 역방향(그림 좌우 반전)은 왼쪽. 뒤집으면 T1 자리가 반대 끝이다
 */
internal fun travelText(kind: TaperKind, type: Int, reverse: Boolean, flip: Boolean): String {
    val ends = when {
        kind.double -> if (reverse) "왼쪽" to "오른쪽" else "오른쪽" to "왼쪽"
        (type == 5) == kind.inner -> "아래" to "위"
        else -> "위" to "아래"
    }
    val (a, b) = if (flip) ends.second to ends.first else ends
    return "T1 $a → T${kind.points} $b · ${screenSide(kind.inner, reverse, flip)}"
}

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
 * 가공 방향 뒤집기([flip], T1 ⇄ 마지막 T)도 점 칸과 같은 (종류, TYPE, 방향)마다 — 칸 값이 그 뜻(T1 = 먼저 깎는 점)을 따라 자리를 바꾸므로.
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

    /** 점 칸(x1·z1·x2·z2·x3·z3·ang·r1·r2·r3(모서리 R)·start·end) — 처음은 모두 빈칸([TaperDefaults]) */
    fun point(name: String): String = values[pointKey(name)] ?: ""
    fun setPoint(name: String, v: String) {
        // 비어 있던(자동) 다섯 값 칸에 값이 들어오면 기억한다 — 다섯 값이 어긋나면 그 칸을 다시 비우자고 권한다
        if (name in FIVE && point(name).isEmpty() && v.isNotEmpty()) values[lastAutoKey] = name
        values[pointKey(name)] = v
    }
    private fun pointKey(name: String) = "p/${kind.name}/$type/$dir/$name"

    /** 비어 있다가 마지막으로 값이 들어온 다섯 값 칸(x1 · z1 · x2 · z2 · ang, 종류 · 형상 · 방향마다). 없으면 null */
    val lastAuto: String? get() = values[lastAutoKey]
    private val lastAutoKey get() = "ui/lastAuto/${kind.name}/$type/$dir"

    /**
     * 가공 방향 뒤집기(종류 · 형상 · 방향마다, 처음은 꺼짐 — 이 열쇠가 없던 옛 저장 값도 꺼짐). 켜면 T1 = 그림의 반대 끝 점(먼저 깎는 점)이고
     * 같은 바이트로 그 끝부터 깎는다(G41/G42 가 바뀜). 바꿀 때는 [toggleFlip] — 칸 값을 같은 실제 점에 남게 맞바꾼다
     */
    val flip: Boolean get() = flipped(type)

    /** 형상 t 의 뒤집기(A 화면 카드 그림이 형상마다 읽는다) */
    fun flipped(t: Int?): Boolean = values["flip/${kind.name}/$t/$dir"] == "1"

    /**
     * T1 ⇄ 마지막 T(2중은 T3, T2 그대로). 넣은 값이 같은 실제 점에 남게 칸을 맞바꾼다: X1·Z1 ↔ X2·Z2(2중 X3·Z3), 모서리 R1 ↔ R2(R3),
     * 시작점 ↔ 끝점(앞뒤 요소가 맞바뀐다 — 단면이면 X, 수평이면 Z 인 뜻도 따라간다). 빈칸(자동)은 빈칸으로 옮긴다. 각도는 같은 경사라 그대로.
     * 마지막에 채운 자동 칸(lastAuto)도 같은 칸으로 따라간다
     */
    fun toggleFlip() {
        val n = kind.points
        val pairs = listOf("x1" to "x$n", "z1" to "z$n", "r1" to "r$n", "start" to "end")
        for ((a, b) in pairs) {
            val va = point(a)
            values[pointKey(a)] = point(b)
            values[pointKey(b)] = va
        }
        val swap = pairs.flatMap { (a, b) -> listOf(a to b, b to a) }.toMap()
        lastAuto?.let { values[lastAutoKey] = swap[it] ?: it }
        values["flip/${kind.name}/$type/$dir"] = if (flip) "" else "1"
    }

    /**
     * T1–T2 사이를 직선(테이퍼 · 면취) 대신 원호로(피드백 2026-11-10 — 홑테이퍼만, 점 칸처럼 종류 · 형상 · 방향마다. 처음은 직선 — 이 열쇠가 없던 옛 저장 값도 직선).
     * 그 호의 R 은 점 칸 "arcR"([point]), 볼록 · 오목은 [arcConcave]. 형상의 성질이라 T1 ⇄ T2([toggleFlip])로 바뀌지 않는다.
     * 직선으로 돌아가도 R · 각도 칸 값은 남는다
     */
    val arc: Boolean get() = arcOn(type)

    /** 형상 t 의 T1–T2 원호 켬(A 화면 카드 그림이 형상마다 읽는다) */
    fun arcOn(t: Int?): Boolean = !kind.double && !kind.groove && values["arc/${kind.name}/$t/$dir"] == "1"
    fun setArc(on: Boolean) { values["arc/${kind.name}/$type/$dir"] = if (on) "1" else "" }

    /** 그 호가 오목(소재가 움푹 — 직선보다 더 깎음)인가. 처음은 볼록(소재가 불룩 — 덜 깎음) */
    val arcConcave: Boolean get() = values["arcIn/${kind.name}/$type/$dir"] == "1"
    fun setArcConcave(v: Boolean) { values["arcIn/${kind.name}/$type/$dir"] = if (v) "1" else "" }

    /** 형상 t 의 그림에 그릴 T1–T2 원호: null = 직선, true = 볼록, false = 오목 */
    fun arcShape(t: Int?): Boolean? = if (arcOn(t)) values["arcIn/${kind.name}/$t/$dir"] != "1" else null

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

    /**
     * 각도 기준을 바꿀 때([switchAngleRef]): 모든 종류 · 형상 · 방향의 각도 칸을 같은 실제 경사의 다른 기준 값으로(30 → 60, [complementText]).
     * 각도 칸은 기준을 따로 적지 않고 지금 기준(AppState.angleRef)으로 읽으므로 한꺼번에 바꾼다. 빈칸(자동)은 빈칸 — 화면이 새 기준으로 다시 보인다
     */
    fun complementAngles() {
        values.keys.filter { it.startsWith("p/") && it.endsWith("/ang") }.forEach { k -> values[k] = complementText(values.getValue(k)) }
    }

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
 * 처음 값. 도면 치수(점 X · Z, 홈 시작 Z · 폭 · 외경 · 바닥)와 여유(Z 여유 · X 여유)는 빈칸에서 시작한다(사용자 요청 2026-10-28
 * "0 이 적힌 좌표들을 다 빈칸으로" — 예전 2026-10-17 결정은 0). 도면 치수의 빈칸은 아직 안 넣은 값(단일 테이퍼는 다섯 값 가운데 하나를
 * 자동 계산), 여유의 빈칸은 0 이다. 공구 값(노즈 R · 인서트 폭 · 겹침)은 흔한 값, 각도 · 모서리 R · 시작 · 끝점도 빈칸
 */
internal object TaperDefaults {
    fun shared(name: String): String = when (name) {
        "noseR" -> "0.4"
        "insert" -> "3"
        "overlap" -> "0.5"
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
    /** 가공 방향 뒤집기: x · z · corners 의 T1 = 그림의 반대 끝 점(먼저 깎는 점), start = 그 앞 요소. calc 의 TaperInput.flip 과 같은 뜻 */
    val flip: Boolean = false,
    /** [angle] 을 잰 축(화면 스위치 AppState.angleRef). 기본이 없다 — 부르는 쪽이 늘 밝힌다(calc 에는 [zAngle]) */
    val angleRef: AngleRef,
    /** T1–T2 사이를 직선 대신 원호로(홑테이퍼만 — calc 의 TaperInput.arc). 그때 [angle] 은 읽지 않고 [arcR] 과 네 좌표 가운데 셋 이상(빈 하나는 접하는 자리로)을 넣는다 */
    val arc: Boolean = false,
    /** 그 호의 R(빈칸 = null) */
    val arcR: Double? = null,
    /** 오목(더 깎음). false = 볼록(덜 깎음) */
    val arcConcave: Boolean = false,
) {
    /** calc 의 각(Z 축에서 잰 각). X 기준이면 90° − 각도 칸 */
    val zAngle: Double? get() = angle?.let(angleRef::toZ)

    /**
     * 도면 치수를 아직 다 넣지 않았다: X(직경) 0 인 점이 있거나 두 점 이상이 Z 0 에 겹친다(예전 처음 값 0 을 그대로 둔 상태 · 0 을 치는 중).
     * 이때 계산이 멈춘 것은 빨간 멈춤이 아니라 "값을 넣으면 계산합니다" 안내로 보인다. 빈칸(지금 처음 값)은 계산기의 MISSING_INPUT 이 같은 안내를 낸다
     */
    val waiting: Boolean get() = x.any { it == 0.0 } || z.count { it == 0.0 } > 1 || (arc && arcR == 0.0)

    /** T1–T2 원호의 가장 작은 R(0.001 로 올림 — 칸 아래 안내 · 경고 글). 두 점을 아직 다 안 넣었거나 수평 · 수직이면 null */
    val arcMinR: Double?
        get() {
            val v = listOf(x[0], z[0], x[1], z[1]).map { it ?: return null }
            return TaperCalc.arcMinR(v[0], v[1], v[2], v[3])?.let { ceil(it * 1000 - 1e-6) / 1000 }
        }

    /**
     * Z 가 방향과 거꾸로 간다: 정방향은 척 쪽(Z−)으로 깎으니 마지막 Z 가 T1 보다 크면, 역방향은 작으면.
     * 뒤집으면(flip) 반대 끝부터 깎으므로 반대
     */
    val zBackward: Boolean
        get() {
            val a = z.first() ?: return false
            val b = z.last() ?: return false
            return if (reverse != flip) b < a else b > a
        }
}

/**
 * 테이퍼 풀기. 정방향은 계산기 그대로.
 * 역방향(Rev.)은 **그림을 좌우로 뒤집은 형상**을 역바이트로 Z+ 쪽으로 깎는 것이라(T1 = 처음 깎는 왼쪽 점),
 * Z 를 뒤집어 정방향 형상으로 풀고 결과(점·경로·진입점·NC·팁·보정 쪽)를 다시 뒤집는다.
 * 노즈R 보정은 거울에 대해 그대로 옮겨지는 기하라(팁 3↔4, 2↔1, G42↔G41) 계산기의 정방향 해법을 그대로 쓴다.
 * 계산기의 reverse = true 는 "형상은 그대로 두고 방향만 Z+" 라서 화면의 역방향과 뜻이 다르다(TaperSolveTest 가 두 쪽을 맞춰 본다).
 * 뒤집기(flip)는 계산기의 flip 에 그대로 넘긴다 — 칸의 뜻(T1 = 먼저 깎는 반대 끝 점)이 같고, 거울에 대해서도 그대로 옮겨진다.
 * 다만 계산기의 접근 줄(뒤집어 척 쪽에서 시작할 때 앞 끝면 바깥에서 X 를 맞추고 Z 로 들어가는 G00)은 계산기의 +Z 가 부품 앞이라는 뜻이라
 * 거울로 푸는 역방향에서는 끈다(approach = false) — 거울의 +Z 는 실제 척 쪽이고, 역방향 + 뒤집기의 실제 진입은 부품 앞(자유 끝)이다.
 */
internal object TaperSolve {
    fun solve(f: SlopeForm): TaperResult {
        val m = f.reverse
        fun zz(v: Double?) = if (m) v?.let { -it } else v
        val tip = if (m) f.tip?.let(Mirror::tip) else f.tip
        // 시작·끝점이 Z 면 같이 뒤집는다(X 는 그대로). 뒤집기면 시작·끝 요소가 맞바뀐 뜻으로
        val (fv, bv) = legsVertical(f.kind, f.type, f.flip)
        val start = if (fv) f.start else zz(f.start)
        val end = if (bv) f.end else zz(f.end)
        val c = f.corners
        val r = if (f.kind.double) {
            TaperCalc.calcDouble(
                DoubleTaperInput(
                    f.kind.inner, false, f.noseR, tip,
                    f.x[0], zz(f.z[0]), f.x[1], zz(f.z[1]), f.x[2], zz(f.z[2]), f.zClear,
                    cornerR1 = c[0], cornerR2 = c[1], cornerR3 = c[2], start = start, end = end, flip = f.flip, approach = !m,
                ),
            ).let { doubleShape(it, f) }
        } else {
            TaperCalc.calc(
                TaperInput(
                    f.kind.inner, f.type, false, f.noseR, tip, f.x[0], zz(f.z[0]), f.x[1], zz(f.z[1]), f.zAngle, f.zClear,
                    cornerR1 = c[0], cornerR2 = c[1], start = start, end = end, flip = f.flip, approach = !m,
                    // 볼록 · 오목 · R 은 형상의 성질이라 거울(역방향)에도 그대로
                    arc = f.arc, arcR = f.arcR, arcConvex = !f.arcConcave,
                ),
            )
        }
        return if (m) r.copy(solution = r.solution?.let(Mirror::solution)) else r
    }

    /**
     * 겹테이퍼 계산기는 세 점이면 어떤 꺾임도 받는다. 고른 TYPE 그림과 X 오르내림이 다르면 멈춘다.
     * TYPE 1 = 두 번 커짐, 2 = 가운데가 큼, 3 = 가운데가 작음, 4 = 두 번 작아짐(외경 기준, 내경은 반대) — 그림 순서(T1 = 오른쪽)로 본다.
     * 뒤집으면 점이 칸 순서(그림 왼쪽부터)라 거꾸로 놓고 본다
     */
    private fun doubleShape(r: TaperResult, f: SlopeForm): TaperResult {
        val p = r.solution?.points?.let { if (f.flip) it.reversed() else it } ?: return r
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

/*
 * 각도 기준(사용자 결정 2026-10-28): 계산(TaperCalc)의 각은 늘 Z 축에서 잰 각이고, 화면은 X축(단면) · Z축 기준을 고른다(AppState.angleRef, 처음 X).
 * 바꾸는 곳은 화면 경계 — 각도 칸 → SlopeForm.zAngle, 결과 각 → shownAngles. X 각 = 90° − Z 각이라 두 쪽이 같은 식이다
 */

/** 이 기준으로 잰 각(°) → calc 의 Z 축 각 */
internal fun AngleRef.toZ(deg: Double): Double = if (this == AngleRef.X) 90.0 - deg else deg

/** calc 의 Z 축 각(°) → 이 기준으로 잰 각 */
internal fun AngleRef.fromZ(zDeg: Double): Double = toZ(zDeg)

/** 칸 · 결과 이름 뒤에 붙는 기준: "X축 기준" · "Z축 기준" */
internal val AngleRef.label: String get() = if (this == AngleRef.X) "X축 기준" else "Z축 기준"

/** 결과 각(칸 순서 — 2중은 각1 · 각2)을 이 기준으로 */
internal fun TaperSolution.shownAngles(ref: AngleRef): List<Double> = anglesDeg.map(ref::fromZ)

/**
 * 각도 칸 글을 다른 기준의 같은 경사로: 90 − 값, 넣은 소수 자리 그대로(30 → 60, 59.744 → 30.256, 12.50 → 77.50).
 * 숫자가 아니면(빈칸 · "-") 그대로. 두 번 바꾸면 처음 숫자
 */
internal fun complementText(t: String): String {
    val v = t.toDoubleOrNull() ?: return t
    val dec = if ('.' in t) t.substringAfter('.').length else 0
    var scale = 1L
    repeat(dec) { scale *= 10 }
    val m = round(abs(90.0 - v) * scale).toLong()
    val sign = if (90.0 - v < 0 && m != 0L) "-" else ""
    val frac = if (dec > 0) "." + (m % scale).toString().padStart(dec, '0') else ""
    return "$sign${m / scale}$frac"
}

/** 각도 기준 스위치: 기준을 바꾸고(저장) 각도 칸을 같은 실제 경사의 새 기준 값으로. 같은 기준이면 그대로 */
internal fun switchAngleRef(app: AppState, s: TaperState, to: AngleRef) {
    if (app.angleRef == to) return
    s.complementAngles()
    app.angleRef = to
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

    private val tipWord = Regex("""T(\d)\)""")
    private val swap = mapOf("G2" to "G3", "G3" to "G2", "G41" to "G42", "G42" to "G41")

    /**
     * NC 줄 뒤집기(회사 표기 — 낱말 붙여 씀): Z 부호, 원호 방향(G2↔G3), 보정 쪽(G41↔G42), 주석 `(R0.4 T3)` 의 팁 번호.
     * Fmt.word 는 절댓값을 반올림하고 부호를 붙이므로(0 은 부호 없음) 글에서 부호를 바꾼 것이 뒤집은 점을 Fmt.word 로 쓴 것과 같다.
     * 앞 줄과 같은 값 · 이동 G 를 빼는 규칙도 글끼리의 같음이라 그대로 맞는다(G2↔G3 를 모든 줄에서 바꾸므로 빠진 G 도 바뀐 쪽).
     */
    fun nc(line: String): String {
        if (line.startsWith("(")) return line.replace(tipWord) { "T${tip(it.groupValues[1].toInt())})" }
        return ncWords(line).joinToString("") { w ->
            when {
                w in swap -> swap.getValue(w)
                !w.startsWith("Z") -> w
                w.startsWith("Z-") -> "Z" + w.substring(2)
                w.drop(1).all { it == '0' || it == '.' } -> w
                else -> "Z-" + w.substring(1)
            }
        }
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
        approach = s.approach.map(::pt),
        ncG41G42 = s.ncG41G42.map(::nc),
        ncG40 = s.ncG40.map(::nc),
        drawing = contour(s.drawing),
        center = contour(s.center),
        program = contour(s.program),
        arc = s.arc?.let { it.copy(cw = !it.cw, center = pt(it.center)) },
    )
}

/**
 * 경고 글. 계산을 멈춘 것(solution = null)은 빨강, 나머지는 노랑. 값이 모자란 것(MISSING_INPUT)은 파란 안내 글.
 * 형상 불일치가 Z 를 거꾸로 넣은 탓이면([SlopeForm.zBackward]) 방향마다 Z 가 어느 쪽으로 가는지 예를 든다
 */
internal fun taperWarningText(w: TaperWarning, f: SlopeForm): String = if (f.arc) arcWarningText(w, f) ?: straightWarningText(w, f) else straightWarningText(w, f)

/**
 * T1–T2 를 원호로 냈을 때([SlopeForm.arc]) 글이 달라지는 경고(나머지는 null — 직선 글 그대로). 직선의 '경사' · '각도' 말을 원호 · R 로 바꾸고,
 * R 이 작아 멈춘 것(ARC_R_TOO_SMALL)은 이 두 점의 가장 작은 R 을(빈 좌표를 접하는 자리로 풀다 멈췄으면 넣은 쪽 차를) 적는다
 */
private fun arcWarningText(w: TaperWarning, f: SlopeForm): String? = when (w) {
    TaperWarning.MISSING_INPUT -> "값을 넣으면 바로 계산합니다 — R 과 X1 · Z1 · X2 · Z2 가운데 셋 (빈 좌표 하나는 원호가 앞뒤 요소에 접하는 자리로 계산합니다)."
    TaperWarning.BAD_ANGLE -> "두 점이 같은 자리이거나 수평 · 수직으로 놓였습니다. T1 · T2 는 그림처럼 X · Z 가 모두 달라야 합니다."
    TaperWarning.BAD_INPUT ->
        "입력이 잘못됐습니다. 지름과 T1–T2 원호의 R 은 0 보다 크고, 노즈R·모서리 R·Z 여유는 0 이상이어야 합니다(노즈R 이 0 이면 Z 여유를 넣으세요 — 진입 거리가 없습니다). " +
            "좌표·치수는 ±${MAX_MM.toInt()} mm 안이어야 합니다." +
            // 넣은 값이 모두 맞으면 빈 좌표를 접하는 자리로 푼 값이 그 밖이다(칸을 짚지 않는다)
            (if ((f.x + f.z).count { it == null } == 1) " 빈 좌표를 원호가 접하는 자리로 계산한 지름이 0 이하이거나 한도 밖이면 이 글이 납니다 — 값과 R 을 확인하세요." else "")
    TaperWarning.CORNER_R_TOO_BIG ->
        "모서리 R 이 들어가지 않습니다. 접점이 T1–T2 원호나 앞뒤 요소(시작·끝점까지)를 넘거나, 두 R 의 접점이 원호 위에서 겹칩니다. 모서리 R 을 줄이세요."
    TaperWarning.ARC_R_TOO_SMALL -> {
        val min = f.arcMinR
        val x1 = f.x[0]
        val z1 = f.z[0]
        val x2 = f.x[1]
        val z2 = f.z[1]
        val len = if (x1 != null && z1 != null && x2 != null && z2 != null) Pt((z2 - z1), (x2 - x1) / 2).length else null
        // 빈 좌표를 접하는 자리로 풀다 멈춘 것: 넣은 쪽 차(빈 칸이 Z 면 반경 차, X 면 Z 거리)가 R 보다 크다 — 1/4 원보다 큰 호가 된다
        val gap = when {
            x1 != null && x2 != null && (z1 == null) != (z2 == null) -> "두 점의 반경 차 ${Fmt.fixed3(abs(x2 - x1) / 2)}"
            z1 != null && z2 != null && (x1 == null) != (x2 == null) -> "두 점의 Z 거리 ${Fmt.fixed3(abs(z2 - z1))}"
            else -> null
        }
        "R 이 너무 작습니다. " +
            when {
                gap != null -> "빈 좌표를 원호가 앞뒤 요소에 접하는 자리로 풀려면 R 이 $gap 이상이어야 합니다. "
                min != null && len != null -> "이 두 점 사이 원호는 R ${Fmt.fixed3(min)} 이상이어야 합니다(두 점 사이 거리 ${Fmt.fixed3(len)}). "
                else -> ""
            } +
            "그보다 작으면 호가 T1 · T2 너머로 부풀어 앞뒤 요소를 넘습니다. 꼭짓점을 둥글리는 R 은 점 줄의 '모서리 R' 칸에 넣으세요."
    }
    TaperWarning.ARC_NO_TANGENT ->
        "빈 좌표를 계산할 수 없습니다. 이 형상의 ${if (f.arcConcave) "오목" else "볼록"} 원호는 앞뒤 요소(수평 · 단면) 어느 쪽에도 접하지 않아 접점이 정해지지 않습니다. " +
            "네 좌표를 모두 넣거나 볼록 · 오목을 확인하세요."
    TaperWarning.NOSE_UNREACHABLE ->
        "노즈R 에 비해 T1–T2 원호(또는 앞뒤 요소)가 짧거나 오목 원호의 R 이 작아 공구가 닿지 못합니다. 도면 형상이 안 나옵니다 — 노즈R 을 줄이거나 원호의 R 을 키우세요."
    // 노즈R 이 두 점 사이보다 크거나, 오목 원호 전체가 오목 꼭짓점 뒤 노즈 그늘이라 원호의 노즈 길이 없어진 것(calc 의 같은 경고)
    TaperWarning.NOSE_GT_SLOPE -> "노즈R 에 비해 T1–T2 원호가 짧거나 오목 꼭짓점 바로 뒤에 있습니다. 숫자는 맞지만 오목 모서리 쪽에 노즈R 이 남아 원호가 거의(또는 전혀) 안 남을 수 있습니다. 도면을 확인하세요."
    TaperWarning.CONCAVE_R_LE_NOSE ->
        "오목 R(모서리 R · T1–T2 원호)이 노즈R 이하입니다. 같으면 꼭 맞고, 작으면 그 자리에 노즈R 만큼의 R 이 남습니다. G41/G42 프로그램은 기계가 간섭 알람을 낼 수 있습니다."
    else -> null
}

private fun straightWarningText(w: TaperWarning, f: SlopeForm): String = when (w) {
    TaperWarning.MISSING_INPUT ->
        if (f.kind.double) "세 점의 X · Z 를 모두 넣으면 바로 계산합니다."
        else "값을 넣으면 바로 계산합니다 — X1 · Z1 · X2 · Z2 · 각도 가운데 넷."
    TaperWarning.CONTRADICTION -> "다섯 값이 모두 찼고 서로 맞지 않습니다. 하나를 비워 계산하세요."
    TaperWarning.BAD_ANGLE -> "각도는 0° 보다 크고 90° 보다 작아야 합니다. 두 점이 수평·수직으로 놓이면 경사가 아닙니다."
    // T1–T2 원호에서만 난다(글은 arcWarningText)
    TaperWarning.ARC_R_TOO_SMALL -> "T1–T2 원호의 R 이 너무 작습니다. 호가 T1 · T2 너머로 부풉니다."
    TaperWarning.ARC_NO_TANGENT -> "T1–T2 원호가 앞뒤 요소에 접하지 않아 빈 좌표를 계산할 수 없습니다. 네 좌표를 모두 넣으세요."
    TaperWarning.SHAPE_MISMATCH -> when {
        !f.zBackward -> "값이 고른 형상 그림과 맞지 않습니다. 점의 좌우 순서(그림의 T1·T2 자리)와 X 가 커지는 쪽을 확인하세요."
        f.flip && f.reverse -> "Z 부호 · 순서를 확인하세요. T1 ⇄ T${f.x.size} 를 바꿔 T1 이 척 반대쪽이라 Z 가 작아집니다 (예: Z1 −25 → Z${f.x.size} −30)."
        f.flip -> "Z 부호 · 순서를 확인하세요. T1 ⇄ T${f.x.size} 를 바꿔 T1 이 척 쪽이라 Z 가 커집니다 (예: Z1 −5 → Z${f.x.size} 0). 척 쪽 Z 는 − 입니다."
        f.reverse -> "Z 부호 · 순서를 확인하세요. 역방향은 T1 이 척 쪽이라 Z 가 커집니다 (예: Z1 −30 → Z2 −25)."
        else -> "Z 부호 · 순서를 확인하세요. 정방향은 척 쪽으로 깎아 Z 가 작아집니다 (예: Z1 0 → Z2 −5). 척 쪽 Z 는 − 입니다."
    }
    TaperWarning.BAD_INPUT ->
        "입력이 잘못됐습니다. 지름은 0 보다 크고, 노즈R·모서리 R·Z 여유는 0 이상이어야 합니다(노즈R 이 0 이면 Z 여유를 넣으세요 — 진입 거리가 없습니다). " +
            "좌표·치수는 ±${MAX_MM.toInt()} mm 안이어야 합니다(각도가 0° 나 90° 에 너무 가까우면 계산한 점이 그 밖으로 나갑니다)."
    TaperWarning.BAD_LEG ->
        "시작점·끝점 값이 맞지 않습니다. 그 요소 방향(단면은 X, 수평은 Z)으로 T 점 바깥에 있어야 하고, 노즈R 보다 충분히 떨어져야 합니다 " +
            "(가까우면 공구가 되돌아가거나 노즈가 그 요소에 닿지 못합니다)."
    TaperWarning.CORNER_R_TOO_BIG ->
        "모서리 R 이 들어가지 않습니다. 접점이 경사나 앞뒤 요소(시작·끝점까지)를 넘거나, 두 R 의 접점이 경사 위에서 겹칩니다. R 을 줄이세요."
    TaperWarning.NOSE_UNREACHABLE -> "노즈R 에 비해 경사(또는 앞뒤 요소)가 너무 짧아 공구가 닿지 못합니다. 도면 형상이 안 나옵니다 — 노즈R 을 줄이세요."
    TaperWarning.NOSE_GT_SLOPE -> "노즈R 이 경사 길이보다 큽니다. 숫자는 맞지만 오목 모서리 쪽에 노즈R 이 남아 경사가 거의 안 남을 수 있습니다. 도면을 확인하세요."
    TaperWarning.CONCAVE_R_LE_NOSE ->
        "오목 모서리 R 이 노즈R 이하입니다. 같으면 꼭 맞고, 작으면 그 자리에 노즈R 만큼의 R 이 남습니다. G41/G42 프로그램은 기계가 간섭 알람을 낼 수 있습니다."
    // 뒤집으면 모양뿐 아니라 진행 방향도 본다(calc: 팁의 Z 와 반대로 45° 보다 눕힌 요소를 깎음 — 외경 · 내경을 바이트 반대쪽으로)
    TaperWarning.AGAINST_TOOL ->
        if (f.flip) {
            "형상 일부나 가공 방향이 바이트와 반대입니다(역테이퍼 꼴·반대쪽 단면, 또는 뒤집어서 외경·내경처럼 누운 요소를 바이트 반대쪽으로 깎음). " +
                "실제 바이트의 끝날 여유각이 맞아야 깎입니다. 정/역방향과 T1 ⇄ T${f.x.size} 바꾸기를 확인하세요."
        } else {
            "형상 일부가 바이트 방향과 반대입니다(역테이퍼 꼴·반대쪽 단면). 실제 바이트의 끝날 여유각이 맞아야 깎입니다. 정/역방향을 확인하세요."
        }
    // 끝 단면을 중심(X0)까지 깎으면 노즈가 중심에 닿게 인선이 일부러 중심 너머 X −2r 까지 간 뒤 Z 로 빠진다(calc: 중심에 살이 안 남게) —
    // Z 여유를 줄이라는 말은 맞지 않는다. 내경 바닥 단면은 G41/G42 가 X −2r 뒤, 직접 보정은 노즈 중심이 축에 선 뒤(표준 팁은 인선 X +2r — 비표준 팁 3 · 4 · 8 은
    // X −2r) Z 로 뺀다(2026-11-12 통합 고침 · 검증 F1 2026-11-13 — 전에는 직접 보정이 축 너머로 가 노즈가 맞은편을 팠다).
    // 뒤집어 척 쪽 · 구멍 안에서 시작하면(화면 역방향이 아닐 때) G41/G42 진입이 급송을 소재에서 떼려고 일부러 중심 너머로 돌아 들어갈 수 있다(검증 F4) — Z 여유를 줄이라는
    // 말만으로는 맞지 않는다
    TaperWarning.ENTRY_PAST_AXIS ->
        if (legsVertical(f.kind, f.type, f.flip).second && f.end == 0.0) {
            if (f.kind.inner) {
                "바닥 단면을 중심(X0)까지 깎아 공구가 중심 너머로 갑니다(노즈 끝이 중심을 다 깎게 — 중심에 살이 남지 않습니다). " +
                    "기계 보정(G41/G42)은 X−${Fmt.nc(2 * f.noseR)} 까지, 직접 보정(G40)은 노즈 중심이 중심에 서는 데까지" +
                    (if (f.tip == null) "(표준 팁이면 인선 X${Fmt.nc(2 * f.noseR)})" else "") +
                    " 간 뒤 Z 로 빠집니다(노즈 중심이 중심 너머로 가지 않아 맞은편을 건드리지 않습니다). " +
                    "기계가 X− 로 그만큼 갈 수 있는지 확인하세요."
            } else {
                "단면을 중심(X0)까지 깎아 인선이 중심 너머 X−${Fmt.nc(2 * f.noseR)} 까지 간 뒤 Z 로 빠집니다(노즈 끝이 중심을 다 깎게 — 중심에 살이 남지 않습니다). " +
                    "기계가 X− 로 그만큼 갈 수 있는지 확인하세요."
            }
        } else if (f.flip && !f.reverse) {
            "진입점이나 후퇴점이 중심(X0)을 넘습니다. 뒤집어 " + (if (f.kind.inner) "구멍 안" else "척 쪽") + "에서 시작하면 기계 보정(G41/G42) 진입이 급송을 소재에서 " +
                "떼려고 일부러 중심 너머로 돌아 들어갈 수 있습니다 — 기계가 X− 로 그만큼 갈 수 있는지 확인하세요. 경사 연장선이 중심을 넘는 것이면" +
                // 내경은 중심 너머가 맞은편 구멍 벽이다(검증 뒤 고침 2026-11-14 F1)
                (if (f.kind.inner) " 노즈가 맞은편 구멍 벽에 닿을 수 있으니" else "") + " Z 여유를 줄이세요."
        } else {
            "진입점이나 후퇴점이 중심(X0)을 넘습니다." + (if (f.kind.inner) " 내경은 노즈가 중심 너머 맞은편 구멍 벽에 닿을 수 있습니다." else "") + " Z 여유를 줄이세요."
        }
    // 짧은 막힌 끝 · 시작 요소를 뺄 수 없는 꼴(calc '진입점과 NC' 끝 · 진입 — 앞뒤 요소에 직각 빼기 · 들어가기 교점이 남지 않음)
    TaperWarning.SHORT_END_PAST ->
        "막힌 끝(또는 시작) 요소가 노즈R 보다 짧은데, 그 앞뒤 요소에 벽을 따라 빼거나 들어갈 자리가 없어 기계 보정(G41/G42) 프로그램이 그 요소를 뺄 수 없습니다. " +
            "그 요소를 따라 곧게 빼거나 들어가므로 노즈가 끝 · 시작 좌표 너머(벽 · 보스 속)로 갑니다. 직접 보정(G40) 프로그램을 쓰거나 노즈R 을 줄이세요."
    TaperWarning.ENTRY_NOT_FOUND ->
        "기계 보정(G41/G42) 프로그램의 진입 길을 도면에 닿지 않게 짜지 못했습니다(Z20. 에서 내려오는 길이 도면에 닿거나, 두 축으로 가는 진입 G0 이 급송 속도 비에 따라 꺾여 닿습니다). 진입 줄을 손으로 고치거나 직접 보정(G40) 프로그램을 쓰세요."
    // 빼는 길 못 찾음(calc '진입점과 NC' 끝 — G40 자리 규칙 · 물러남 띠 · 빼는 줄 노즈 길 · 바깥 90° 이하 꺾임을 함께 못 맞춤)
    TaperWarning.EXIT_NOT_FOUND ->
        "기계 보정(G41/G42) 프로그램의 빼는 길(빼는 줄 · G40 · G0Z200.)을 도면에서 넉넉히 떨어지게 짜지 못했습니다(노즈에 비해 좁은 구멍 등). " +
            "팁 번호 · 보정 형식에 따라 빼는 줄이나 G40 · G0Z200. 이 도면에 닿을 수 있으니 빼는 줄을 손으로 고치거나 직접 보정(G40) 프로그램을 쓰세요."
    // 짧은 막힌 끝 · 시작 요소를 뺀 꼴(calc '진입점과 NC' — 노즈가 벽을 넘지 않는 대신 그 요소는 깎지 않음)
    TaperWarning.SHORT_END_SKIPPED ->
        "막힌 끝(또는 시작) 요소가 노즈R 보다 짧아, 기계 보정(G41/G42) 프로그램은 그 요소(와 붙은 모서리 R)를 깎지 않고 벽을 따라 빼거나 들어갑니다. " +
            "노즈가 벽 너머로 가지 않는 대신 그 자리에 살이 남으니, 따로 다듬거나 노즈R 을 줄이세요."
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
        // 각도 칸이 범위 밖이 아니면 두 점이 수평 · 수직으로 놓인 것(원호는 각도 칸을 읽지 않는다)
        TaperWarning.BAD_ANGLE -> if (!f.arc && f.angle.let { it != null && (it <= 0 || it >= 90) }) setOf("ang") else (xs + zs).toSet()
        TaperWarning.SHAPE_MISMATCH -> if (f.zBackward) zs.toSet() else (xs + zs).toSet()
        TaperWarning.BAD_INPUT -> buildSet {
            f.x.forEachIndexed { i, v -> if (v != null && (v <= 0 || big(v))) add(xs[i]) }
            f.z.forEachIndexed { i, v -> if (big(v)) add(zs[i]) }
            f.corners.forEachIndexed { i, v -> if (v < 0 || big(v)) add("r${i + 1}") }
            if (f.noseR.isNaN() || f.noseR < 0 || big(f.noseR)) add("noseR")
            if (f.zClear < 0 || big(f.zClear) || (f.zClear == 0.0 && f.noseR == 0.0)) add("zClear")
            if (big(f.start)) add("start")
            if (big(f.end)) add("end")
            if (f.arc && f.arcR.let { it != null && (it <= 0 || big(it)) }) add("arcR")
        }
        // 넣은 쪽만(둘 다 비었으면 둘 다)
        TaperWarning.BAD_LEG ->
            listOfNotNull("start".takeIf { f.start != null }, "end".takeIf { f.end != null }).ifEmpty { listOf("start", "end") }.toSet()
        TaperWarning.CORNER_R_TOO_BIG -> f.corners.indices.filter { f.corners[it] > 0 }.map { "r${it + 1}" }.toSet()
        // 오목 원호의 R 이 노즈R 보다 작아 멈춘 것이면 그 R 칸도
        TaperWarning.NOSE_UNREACHABLE -> if (f.arc && f.arcConcave && f.arcR.let { it != null && it < f.noseR }) setOf("noseR", "arcR") else setOf("noseR")
        TaperWarning.ARC_R_TOO_SMALL -> setOf("arcR")
        else -> emptySet()
    }
}

/** 홈 치수를 아직 넣지 않았다(폭 · 외경 0 — 예전 처음 값. 지금 처음 값인 빈칸은 MISSING_INPUT) — 멈춤 대신 안내 */
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
