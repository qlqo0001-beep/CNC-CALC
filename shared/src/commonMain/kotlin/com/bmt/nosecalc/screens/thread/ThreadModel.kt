package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Input
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSeries
import com.bmt.nosecalc.calc.ThreadSpec
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.WorkMaterials
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 숫자칸. key = 상태 열쇠 · 칸 묶음 id, advanced = '고급 공차 및 인선 설정' 접이 안의 칸.
 * 도면 값(Z 시작 · 목표 · 첫 절입)은 빈칸 = 아직 안 넣음, 가공 값은 빈칸 = 기본값(자리표시 '기본 …')
 */
internal enum class ThreadField(val key: String, val label: String, val unit: String, val advanced: Boolean = false) {
    Z_START("zStart", "Z축 시작점", "mm"),
    RPM("rpm", "회전수 (RPM)", ""),
    Z_END("zEnd", "가공 목표점 (Z)", "mm"),
    TARGET("target", "공차 목표", "%", advanced = true),
    TIP_R("tipR", "인선 R", "mm", advanced = true),
    FINISH_COUNT("m", "정삭 횟수 m", "회", advanced = true),
    MIN_CUT("minCut", "최소 절입 Δdmin", "mm", advanced = true),
    ALLOW("allow", "정삭 여유 d", "mm", advanced = true),
    X_CLEAR("xClear", "시작 X 여유", "mm", advanced = true),
    FIRST_CUT("firstCut", "첫 절입 Δd", "mm"),
}

/** 고르기 시트(드롭다운) 종류 */
internal enum class Pick { CLASS, KIND, SIZE, PITCH, MATERIAL, ANGLE, CHAMFER }

/**
 * 나사 G76 화면 상태. 숫자칸 값은 쓴 그대로의 글(빈칸 = 안 넣음 · 기본값).
 * 처음엔 모두 빈칸 · 사이즈 안 고름(미터) — 파랑 안내가 남은 값을 적는다. 고르는 칸(급수 · 각도 · 면취량)은 null = 기본(원본 08 의 값).
 * 급수는 외경/내경 · 나사 종류에 딸려 있어 그 둘을 바꾸면 기본으로 돌아간다
 */
@Stable
internal class ThreadState(
    inner: Boolean = false,
    kind: ThreadKind = ThreadKind.METRIC,
    spec: ThreadSpec? = null,
    cls: ThreadClass? = null,
    material: IsoGroup? = null,
    dir: ThreadDir = ThreadDir.FORWARD,
    angle: Int? = null,
    chamfer: Int? = null,
    values: Map<String, String> = emptyMap(),
    advOpen: Boolean = false,
) {
    var inner by mutableStateOf(inner)
        private set
    var kind by mutableStateOf(kind)
        private set
    var spec by mutableStateOf(spec)
        private set

    /** null = 기본 급수(6g · 6H · 2A · 2B) */
    var cls by mutableStateOf(cls)
        private set

    /** 기록용(계산에 안 씀 — 출처 있는 나사 절삭 속도 자료가 없다) */
    var material by mutableStateOf(material)
    var dir by mutableStateOf(dir)

    /** null = 60° */
    var angle by mutableStateOf(angle)

    /** null = 10 (1.0P) */
    var chamfer by mutableStateOf(chamfer)

    /** 고급 설정 펼침 */
    var advOpen by mutableStateOf(advOpen)
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    fun value(f: ThreadField): String = values[f.key] ?: ""
    fun set(f: ThreadField, v: String) {
        values[f.key] = v
    }

    fun num(f: ThreadField): Double? = value(f).toDoubleOrNull()

    fun switchInner(v: Boolean) {
        inner = v
        if (cls?.internal != v) cls = null
    }

    fun switchKind(k: ThreadKind) {
        if (k == kind) return
        kind = k
        spec = null
        cls = null
    }

    /** 사이즈를 고르면 보통 나사(UN 은 UNC) 피치가 먼저 — 호칭만 쓴 도면(M10)은 보통 나사다 */
    fun pickSize(size: String) {
        if (spec?.size == size) return
        spec = ThreadTable.pitches(kind, size).first()
    }

    fun pickSpec(s: ThreadSpec) {
        spec = s
    }

    fun pickClass(c: ThreadClass) {
        cls = c
    }

    /** 화면에 보이는 급수(고르지 않았으면 기본) */
    val shownClass: ThreadClass get() = cls ?: ThreadClass.default(kind, inner)

    val shownAngle: Int get() = angle ?: G76Calc.ANGLE_DEFAULT
    val shownChamfer: Int get() = chamfer ?: G76Calc.CHAMFER_DEFAULT

    /** 계산기 입력 — 숫자칸은 toDoubleOrNull(빈칸 = null), 정삭 횟수가 정수가 아니면 0(→ BAD_INPUT) */
    fun input(): G76Input = G76Input(
        inner = inner,
        spec = spec,
        cls = cls,
        dir = dir,
        zStart = num(ThreadField.Z_START),
        zEnd = num(ThreadField.Z_END),
        rpm = num(ThreadField.RPM),
        angle = angle,
        chamfer = chamfer,
        target = num(ThreadField.TARGET),
        tipR = num(ThreadField.TIP_R),
        finishCount = num(ThreadField.FINISH_COUNT)?.let { if (it == floor(it) && abs(it) <= 1000) it.toInt() else 0 },
        minCut = num(ThreadField.MIN_CUT),
        finishAllow = num(ThreadField.ALLOW),
        firstCut = num(ThreadField.FIRST_CUT),
        xClear = num(ThreadField.X_CLEAR),
    )

    /** 아직 안 넣은 도면 값(파랑 안내의 '남은 것') */
    fun missing(): List<String> = buildList {
        if (spec == null) add("사이즈")
        if (num(ThreadField.Z_START) == null) add("Z축 시작점")
        if (num(ThreadField.Z_END) == null) add("가공 목표점 (Z)")
        if (num(ThreadField.FIRST_CUT) == null) add("첫 절입 Δd")
    }

    /** 범위 밖 칸 — 계산기의 BAD_INPUT 과 같은 규칙(G76Calc.calc). 값 부족과 따로 보아 넣자마자 빨갛게 짚는다 */
    fun bad(): Set<ThreadField> = ThreadField.entries.filter { f ->
        val v = num(f) ?: return@filter false
        if (!v.isFinite()) return@filter true
        when (f) {
            ThreadField.Z_START, ThreadField.Z_END -> abs(v) > MAX_MM
            ThreadField.RPM -> v < 1 || v > MAX_RPM
            ThreadField.TARGET -> v < 0 || v > 100
            ThreadField.FINISH_COUNT -> v != floor(v) || v < 1 || v > 99
            ThreadField.FIRST_CUT -> v <= 0 || um(v) < 1 || v > MAX_MM
            ThreadField.TIP_R, ThreadField.MIN_CUT, ThreadField.ALLOW, ThreadField.X_CLEAR -> v < 0 || v > MAX_MM
        }
    }.toSet()

    /** 계산을 멈춘 경고가 가리키는 칸(빨간 테) */
    fun flagged(warnings: List<G76Warning>): Set<ThreadField> = bad() + warnings.flatMap { w ->
        when (w) {
            G76Warning.Z_ORDER -> listOf(ThreadField.Z_START, ThreadField.Z_END)
            G76Warning.TIP_TOO_BIG -> listOf(ThreadField.TIP_R)
            G76Warning.FINISH_GE_HEIGHT -> listOf(ThreadField.ALLOW)
            G76Warning.FIRST_CUT_LT_MIN, G76Warning.TOO_MANY_PASSES -> listOf(ThreadField.FIRST_CUT, ThreadField.MIN_CUT)
            G76Warning.CLEARANCE_TOO_BIG -> listOf(ThreadField.X_CLEAR)
            else -> emptyList()
        }
    }

    /** 자리표시: 가공 값은 '기본 …'(빈칸이면 그 값으로 계산), 회전수는 '안 씀', 도면 값은 비움 */
    fun placeholder(f: ThreadField): String = when (f) {
        ThreadField.RPM -> "안 씀"
        ThreadField.TARGET -> "기본 ${plain(G76Calc.TARGET_DEFAULT)}"
        ThreadField.TIP_R -> spec?.let { "기본 ${Fmt.fixed3(defaultTipR(it, inner))}" } ?: "기본 규격값"
        ThreadField.FINISH_COUNT -> "기본 ${G76Calc.FINISH_COUNT_DEFAULT}"
        ThreadField.MIN_CUT -> "기본 ${plain(G76Calc.MIN_CUT_DEFAULT)}"
        ThreadField.ALLOW -> "기본 ${plain(G76Calc.FINISH_ALLOW_DEFAULT)}"
        ThreadField.X_CLEAR -> "기본 ${plain(G76Calc.X_CLEAR_DEFAULT)}"
        else -> ""
    }

    companion object {
        private const val MAX_RPM = 100_000.0

        val Saver = listSaver<ThreadState, String>(
            save = { s ->
                listOf(
                    s.inner.toString(), s.kind.name, s.spec?.let { "${it.series.name}|${it.size}|${it.pitch}" } ?: "",
                    s.cls?.name ?: "", s.material?.name ?: "", s.dir.name, s.angle?.toString() ?: "", s.chamfer?.toString() ?: "",
                    s.advOpen.toString(),
                ) + ThreadField.entries.map { s.value(it) }
            },
            restore = { l ->
                val kind = ThreadKind.valueOf(l[1])
                val spec = l[2].split('|').takeIf { it.size == 3 }?.let { (series, size, p) ->
                    ThreadTable.of(kind).firstOrNull { it.series == ThreadSeries.valueOf(series) && it.size == size && it.pitch == p.toDouble() }
                }
                ThreadState(
                    inner = l[0].toBoolean(),
                    kind = kind,
                    spec = spec,
                    cls = l[3].takeIf { it.isNotEmpty() }?.let { ThreadClass.valueOf(it) },
                    material = l[4].takeIf { it.isNotEmpty() }?.let { IsoGroup.valueOf(it) },
                    dir = ThreadDir.valueOf(l[5]),
                    angle = l[6].toIntOrNull(),
                    chamfer = l[7].toIntOrNull(),
                    values = ThreadField.entries.mapIndexed { i, f -> f.key to l[9 + i] }.toMap(),
                    advOpen = l[8].toBoolean(),
                )
            },
        )
    }
}

/** 빈칸 인선 R(계산기와 같은 규칙): 숫나사 H/6, 암나사 H/8 — 최대 실체에서 규격 기본 깊이 */
internal fun defaultTipR(spec: ThreadSpec, inner: Boolean): Double {
    val h = sqrt(3.0) / 2 * spec.pitch
    return if (inner) h / 8 else h / 6
}

/** mm → μm 정수(계산기와 같은 반올림 — 5 는 올림) */
internal fun um(v: Double): Long = floor(abs(v) * 1000 + 0.5 + 1e-6).toLong() * (if (v < 0) -1 else 1)

/** 리드 F 글(계산기와 같다): 0.0001 까지, 끝 0 지움, 소수점은 꼭 — 1.27 · 6. · 1.9538 */
internal fun leadText(v: Double): String {
    val q = floor(v * 10_000 + 0.5 + 1e-6).toLong()
    return "${q / 10_000}.${(q % 10_000).toString().padStart(4, '0').trimEnd('0')}"
}

/** 끝 0 없는 숫자 글(80 · 0.035) */
internal fun plain(v: Double): String = Fmt.nc(v).removeSuffix(".")

internal fun two(v: Int): String = v.toString().padStart(2, '0')

/** 면취량 글: 10 → "1.0P" */
internal fun chamferText(r: Int): String = "${r / 10}.${r % 10}P"

/** 나사 종류 글 — 좁은 칸에서 '…' 로 줄어도 기호(M · UN)가 남게 앞에 */
internal fun kindText(k: ThreadKind): String = if (k == ThreadKind.METRIC) "M (미터)" else "UN (유니파이)"

/**
 * 공차 급수의 등급 이름: ISO 965-1 의 공차 품질(정밀 fine · 보통 medium · 거침 coarse — 4h 정밀, 6g · 6h 보통, 8g 거침),
 * UN 은 NBS Handbook H28 (1969) Part I 4.2 의 급수 설명(1A · 1B 조립 쉬움, 2A · 2B 가장 많이 씀, 3A · 3B 맞춤 · 리드 · 각도 정밀)
 */
internal fun classQuality(c: ThreadClass): String = when (c) {
    ThreadClass.M4h, ThreadClass.UN3A, ThreadClass.UN3B -> "정밀"
    ThreadClass.M8g -> "거침"
    ThreadClass.UN1A, ThreadClass.UN1B -> "헐거움"
    else -> "보통"
}

/** 드롭다운 칸 글: "2A (보통)" · "6g (보통)" */
internal fun classText(c: ThreadClass): String = "${c.label} (${classQuality(c)})"

/** 고르기 시트의 설명 한 줄 */
internal fun classNote(c: ThreadClass): String = when (c) {
    ThreadClass.M4h -> "공차가 좁음 · 틈새 없음 (h) · 공차는 식으로 계산 (표 자료 없음)"
    ThreadClass.M6g -> "기본 · 작은 틈새 (g)"
    ThreadClass.M6h -> "틈새 없음 (h)"
    ThreadClass.M8g -> "공차가 넓음 · 작은 틈새 (g) · 공차는 식으로 계산 (표 자료 없음)"
    ThreadClass.M6H -> "기본 · 틈새 없음 (H)"
    ThreadClass.M6G -> "작은 틈새 (G)"
    ThreadClass.UN1A -> "조립이 쉬운 헐거운 맞춤 · 허용차 있음"
    ThreadClass.UN2A -> "기본 · 가장 많이 씀 · 허용차 있음"
    ThreadClass.UN3A -> "맞춤 · 리드 · 각도가 중요할 때 · 허용차 없음"
    ThreadClass.UN1B -> "조립이 쉬운 헐거운 맞춤"
    ThreadClass.UN2B -> "기본 · 가장 많이 씀"
    ThreadClass.UN3B -> "맞춤 · 리드 · 각도가 중요할 때"
}

/** 소재 칸 글: "M 스테인리스강" */
internal fun materialText(g: IsoGroup): String = "${g.name} ${WorkMaterials.of(g).title}"

/** 피치 칸 글(좁은 칸): 미터 "1.5", UN "20 산" */
internal fun pitchText(s: ThreadSpec): String = if (s.tpi != null) "${s.tpi} 산" else plain(s.pitch)

/** 피치 시트 줄: "1.5 mm · 보통 나사" · "20 산 · UNC" */
internal fun pitchRow(s: ThreadSpec): Pair<String, String> = when (s.series) {
    ThreadSeries.M_COARSE -> "${plain(s.pitch)} mm" to "보통 나사 · ${s.label}"
    ThreadSeries.M_FINE -> "${plain(s.pitch)} mm" to "가는 나사 · ${s.label}"
    ThreadSeries.UNC, ThreadSeries.UNF -> "${s.tpi} 산" to "${s.series.name} · 피치 ${Fmt.fixed3(s.pitch)} mm · ${s.label}"
}

/**
 * G76 한 줄의 낱말. letter = 주소(P · Q · R · X · Z · F, 면취량 · 각도 두 자리 묶음은 ""), field = 눌러서 고칠 칸(null = 자동 계산 값),
 * missing = 넣어야 하는데 빈칸("?"), byDefault = 빈칸이라 기본값을 쓴 것, note = 자동 값을 누르면 띄울 설명
 */
internal data class NcWord(
    val letter: String,
    val value: String,
    val field: ThreadField? = null,
    val missing: Boolean = false,
    val byDefault: Boolean = false,
    val note: String? = null,
)

/** 낱말 줄 → NC 글("G76 P021060 Q35 R0.02") — 주소 없는 낱말은 앞 낱말에 붙는다 */
internal fun ncText(words: List<NcWord>): String = buildString {
    append("G76")
    words.forEach { w ->
        if (w.letter.isNotEmpty()) append(' ')
        append(w.letter).append(w.value)
    }
}

/**
 * G76 두 줄의 낱말(원본 08 의 'G76 [P 02] 1060 [Q 35] [R 0.02]' 꼴). 풀렸으면 계산기 NC 글을 그대로 쪼갠다(화면 = 복사 글),
 * 아직이면 넣은 값 · 기본값으로 첫 줄을 쓰고 둘째 줄의 X · P(k) 는 "—", 빈 도면 값은 "?"
 */
internal fun g76Words(s: ThreadState, sol: G76Solution?): List<List<NcWord>> {
    val raNote = "${two(s.shownChamfer)}${two(s.shownAngle)} = 면취량 ${chamferText(s.shownChamfer)} (${two(s.shownChamfer)}) · 나사산 각도 ${s.shownAngle}° (${two(s.shownAngle)}) — 설정 카드에서 고릅니다"
    fun blank(f: ThreadField) = s.value(f).isEmpty()
    if (sol != null) {
        val l1 = sol.nc[0].split(' ').drop(1)
        val l2 = sol.nc[1].split(' ').drop(1)
        fun word(w: String, f: ThreadField?, note: String? = null) = NcWord(w.take(1), w.drop(1), f, byDefault = f != null && blank(f), note = note)
        val p = l1[0]
        return listOf(
            listOf(
                NcWord("P", p.substring(1, 3), ThreadField.FINISH_COUNT, byDefault = blank(ThreadField.FINISH_COUNT)),
                NcWord("", p.substring(3), note = raNote),
                word(l1[1], ThreadField.MIN_CUT),
                word(l1[2], ThreadField.ALLOW),
            ),
            listOf(
                word(l2[0], null, "X = 나사 끝 골 지름 — 공차 목표 % · 인선 R 로 정해집니다 (고급 설정)"),
                word(l2[1], ThreadField.Z_END),
                word(l2[2], null, "P = 나사산 높이 k (μm) — 목표 산봉우리 지름에서 X 까지 (반경)"),
                word(l2[3], ThreadField.FIRST_CUT),
                word(l2[4], null, "F = 리드 (한 줄 나사는 피치) — 사이즈 · 피치에서"),
            ),
        )
    }
    fun edit(f: ThreadField, letter: String, default: String, fmt: (Double) -> String): NcWord {
        val t = s.value(f)
        val v = t.toDoubleOrNull()
        return when {
            t.isEmpty() -> NcWord(letter, default, f, byDefault = true)
            v != null && f !in s.bad() -> NcWord(letter, fmt(v), f)
            else -> NcWord(letter, t, f)
        }
    }
    fun need(f: ThreadField, letter: String, fmt: (Double) -> String): NcWord {
        val v = s.num(f)
        return if (v == null) NcWord(letter, "?", f, missing = true) else if (f in s.bad()) NcWord(letter, s.value(f), f) else NcWord(letter, fmt(v), f)
    }
    return listOf(
        listOf(
            edit(ThreadField.FINISH_COUNT, "P", two(G76Calc.FINISH_COUNT_DEFAULT)) { two(it.toInt()) },
            NcWord("", two(s.shownChamfer) + two(s.shownAngle), note = raNote),
            edit(ThreadField.MIN_CUT, "Q", um(G76Calc.MIN_CUT_DEFAULT).toString()) { um(it).toString() },
            edit(ThreadField.ALLOW, "R", Fmt.nc(G76Calc.FINISH_ALLOW_DEFAULT)) { Fmt.nc(it) },
        ),
        listOf(
            NcWord("X", "—", note = "X 는 사이즈 · Z · 첫 절입을 넣으면 계산합니다"),
            need(ThreadField.Z_END, "Z") { Fmt.nc(it) },
            NcWord("P", "—", note = "P(k) 는 사이즈 · Z · 첫 절입을 넣으면 계산합니다"),
            need(ThreadField.FIRST_CUT, "Q") { um(it).toString() },
            NcWord("F", s.spec?.let { leadText(it.pitch) } ?: "—", note = "F = 리드 (한 줄 나사는 피치) — 사이즈 · 피치에서"),
        ),
    )
}

/** 경고 글. 값 부족은 파랑 안내, 멈춤은 빨강, 숫자와 함께 나온 것(ROOT_INTERFERES · ONE_ROUGH_PASS)은 노랑 */
internal fun threadWarningText(w: G76Warning, s: ThreadState, sol: G76Solution? = null): String = when (w) {
    G76Warning.MISSING_INPUT -> "값을 넣으면 바로 계산합니다 — 남은 것: ${s.missing().joinToString(" · ")}."
    G76Warning.BAD_INPUT -> badText(s)
    G76Warning.Z_ORDER -> if (s.dir == ThreadDir.FORWARD) {
        "정가공(우)은 Z축 시작점이 가공 목표점보다 커야 합니다 (척 쪽 Z− 로 깎음, 예: 5 → −25). 왼나사라면 방향을 역가공(좌)으로 바꾸세요."
    } else {
        "역가공(좌)은 Z축 시작점이 가공 목표점보다 작아야 합니다 (척 반대쪽 Z+ 로 깎음, 예: −30 → 0)."
    }
    G76Warning.TIP_TOO_BIG -> "인선 R 이 너무 커서 날이 이웃 산봉우리까지 깎습니다. 인서트 카탈로그의 날 끝 R 을 확인하세요."
    G76Warning.FINISH_GE_HEIGHT -> "정삭 여유 d 가 나사산 높이 k 이상입니다. 정삭 여유를 줄이세요."
    G76Warning.FIRST_CUT_LT_MIN -> "첫 절입 Δd 가 최소 절입 Δdmin 보다 작습니다. 첫 절입을 키우거나 최소 절입을 줄이세요."
    G76Warning.TOO_MANY_PASSES ->
        "절입 횟수가 ${G76Calc.MAX_PASSES}회를 넘습니다. 첫 절입 Δd 와 최소 절입 Δdmin 을 확인하세요 (단위 mm · 반경 — 0.2 를 0.0002 로 넣지 않았는지)."
    G76Warning.CLEARANCE_TOO_BIG -> "사이클 시작 X(안지름 − 2 × 시작 X 여유)가 0 이하입니다. 고급 설정의 시작 X 여유를 줄이세요."
    G76Warning.ROOT_INTERFERES -> {
        val lim = sol?.limits?.rootLimit?.let { " Ø${Fmt.fixed3(it)}" } ?: ""
        if (s.inner) {
            "골(바깥지름 쪽)이 규격 최소$lim 보다 작습니다 — 인선 R 이 커서 골이 덜 파였습니다. 상대 숫나사 산봉우리에 닿을 수 있으니 인선 R 을 확인하세요."
        } else {
            // 숫자 끝 소리에 맞는 조사(칠 → 을, 이 → 를). 값이 없으면 "최대를"
            "골지름이 규격 최대$lim${if (lim.isNotEmpty() && lim.last() in "013678") "을" else "를"} 넘습니다 — 인선 R 이 커서 골이 덜 파였습니다. 상대 암나사 산봉우리에 닿을 수 있으니 인선 R 을 확인하세요."
        }
    }
    G76Warning.ONE_ROUGH_PASS ->
        "첫 절입이 '나사산 높이 − 정삭 여유' 이상이라 거친 절입이 한 번입니다 (한 번에 거의 다 깎음). 의도한 것인지 확인하세요."
}

/** BAD_INPUT 글: 틀린 칸마다 범위 */
private fun badText(s: ThreadState): String {
    val parts = s.bad().map { f ->
        when (f) {
            ThreadField.Z_START, ThreadField.Z_END -> "${f.label} ±${MAX_MM.toInt()} mm 안"
            ThreadField.RPM -> "회전수 1 ~ 100000"
            ThreadField.TARGET -> "공차 목표 0 ~ 100 %"
            ThreadField.FINISH_COUNT -> "정삭 횟수 1 ~ 99 (정수)"
            ThreadField.FIRST_CUT -> "첫 절입 0.001 mm 이상 (μm 단위로 지령)"
            else -> "${f.label} 0 이상"
        }
    }
    return if (parts.isEmpty()) "넣은 값이 범위 밖입니다." else "넣은 값이 범위 밖입니다 — ${parts.joinToString(" · ")}."
}

/** 숫자 칸 키패드 안내 줄 */
internal fun fieldHint(f: ThreadField, inner: Boolean): String = when (f) {
    ThreadField.Z_START -> "사이클 시작 Z — 나사 앞 공기 중 (예: 5). 처음 몇 mm 는 리드가 맞지 않아 나사보다 앞에서 시작"
    ThreadField.RPM -> "주축 회전수 — 넣으면 첫 줄 G97 S · 분당 이송 · Vc 를 보입니다 (비우면 안 씀)"
    ThreadField.Z_END -> "나사 끝 Z — 척 쪽은 − (예: −25)"
    ThreadField.TARGET -> "공차역 안 목표 % — 0 = 최대 실체(${if (inner) "암나사 작은 쪽" else "숫나사 큰 쪽"}), 100 = 최소 실체 · 비우면 80"
    ThreadField.TIP_R -> "인서트 날 끝 R (카탈로그 값) · 비우면 규격 기본 깊이와 맞는 R (${if (inner) "H/8" else "H/6"})"
    ThreadField.FINISH_COUNT -> "마지막 정삭 반복 횟수 (1~99) — G76 첫 줄 P 앞 두 자리 · 비우면 2"
    ThreadField.MIN_CUT -> "한 회 최소 절입 (반경) — G76 첫 줄 Q (μm) · 비우면 0.035"
    ThreadField.ALLOW -> "마지막 정삭 여유 (반경) — G76 첫 줄 R · 비우면 0.02"
    ThreadField.FIRST_CUT -> "첫 절입 깊이 (반경) — G76 둘째 줄 Q (μm) · n 회 깊이 = Δd × √n · 분석표 횟수를 보고 고침"
    ThreadField.X_CLEAR -> "사이클 시작 X 여유 (반경) — ${if (inner) "안지름 최소 − 2 ×" else "바깥지름 최대 + 2 ×"} · 비우면 1"
}
