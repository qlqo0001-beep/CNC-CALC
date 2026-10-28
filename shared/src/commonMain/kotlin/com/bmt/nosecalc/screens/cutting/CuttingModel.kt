package com.bmt.nosecalc.screens.cutting

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.CutField
import com.bmt.nosecalc.calc.CutInput
import com.bmt.nosecalc.calc.CutResult
import com.bmt.nosecalc.calc.CutWarning
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DrillCatalog
import com.bmt.nosecalc.calc.DrillCondition
import com.bmt.nosecalc.calc.DrillMaker
import com.bmt.nosecalc.calc.DrillResult
import com.bmt.nosecalc.calc.DrillSeries
import com.bmt.nosecalc.calc.DrillWarning
import com.bmt.nosecalc.calc.FeedBand
import com.bmt.nosecalc.calc.FeedFrom
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.Span3
import com.bmt.nosecalc.calc.SpeedFrom
import com.bmt.nosecalc.calc.WorkMaterials
import kotlin.math.abs
import kotlin.math.floor

/** 탭 셋(원본 03 의 순서) */
internal enum class CutTab(val label: String) { BASIC("기본계산기"), MATERIAL("재료분석"), DRILL("드릴링") }

/**
 * 숫자칸. key = 상태 열쇠 · 칸 묶음 id. 기본계산기 칸은 [cut] 으로 계산기 칸과 잇는다(빨간 테 = 계산기의 [CutResult.bad]).
 * 모두 빈칸에서 시작 — 빈칸인 줄은 결과도 비운다(0 으로 계산하지 않음). 날 수만 빈칸 = 2(자리표시 '기본 2').
 */
internal enum class CutBox(val key: String, val label: String, val unit: String, val cut: CutField?) {
    D("d", "지름 D", "mm", CutField.D),
    VC("vc", "절삭 속도 Vc", "m/min", CutField.VC),
    N("n", "회전수 n", "rpm", CutField.N),
    F("f", "회전당 이송 f", "mm/rev", CutField.F),
    FZ("fz", "날당 이송 fz", "mm", CutField.FZ),
    Z("z", "날 수 Z", "날", CutField.Z),
    LENGTH("len", "가공 길이 L", "mm", CutField.LENGTH),
    NOSE_R("noseR", "노즈 R", "mm", CutField.NOSE_R),
    SMAX("smax", "G50 S 최대 회전수", "rpm", CutField.SMAX),

    // 드릴링 탭
    DRILL_D("drillD", "드릴 직경 Ø", "mm", null),
    DRILL_VC("drillVc", "절삭 속도 Vc", "m/min", null),
    DRILL_F("drillF", "회전당 이송 f", "mm/rev", null),
}

/** 드릴링 메이커 버튼의 '직접 입력'(카탈로그 없이 Vc · f) */
internal const val MANUAL = "manual"

/**
 * 재료·절삭조건 화면 상태. 숫자칸 값은 쓴 그대로의 글(빈칸 = 안 넣음).
 * 기본계산기: Vc 와 n, f 와 fz 는 **마지막에 넣은 쪽이 기준**이다 — 한쪽에 넣으면 다른 쪽 글을 비워 그 칸은 자동 값(청록)을 보인다.
 * 드릴링: 메이커 → 공구 종류(시리즈) → 피삭재(ISO 군 → 행) → 직경. 처음엔 아무것도 고르지 않는다(고른 것 없이 숫자가 나오지 않게).
 * 메이커를 바꾸면 시리즈 · 행을, 시리즈를 바꾸면 행을 비운다(표가 달라서). ISO 군은 남긴다.
 */
@Stable
internal class CuttingState(
    tab: CutTab = CutTab.BASIC,
    speedFrom: SpeedFrom = SpeedFrom.VC,
    feedFrom: FeedFrom = FeedFrom.F,
    maker: String? = null,
    series: Int? = null,
    iso: IsoGroup? = null,
    row: Int? = null,
    values: Map<String, String> = emptyMap(),
) {
    var tab by mutableStateOf(tab)
    var speedFrom by mutableStateOf(speedFrom)
        private set
    var feedFrom by mutableStateOf(feedFrom)
        private set

    /** 메이커 열쇠(sandvik · iscar · mitsubishi · yg1 · korloy) 또는 [MANUAL], null = 안 고름 */
    var maker by mutableStateOf(maker)
        private set

    /** 메이커의 시리즈 번호 */
    var series by mutableStateOf(series)
        private set

    /** 피삭재 군(행 목록 거르기) */
    var iso by mutableStateOf(iso)
        private set

    /** 시리즈 표의 행 번호([DrillSeries.conditions]) */
    var row by mutableStateOf(row)
        private set
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    fun value(b: CutBox): String = values[b.key] ?: ""
    fun num(b: CutBox): Double? = value(b).toDoubleOrNull()

    /** 칸에 넣기. Vc · n, f · fz 는 넣은 쪽이 기준이 되고 다른 쪽 글은 비운다 */
    fun set(b: CutBox, v: String) {
        values[b.key] = v
        when (b) {
            CutBox.VC -> { speedFrom = SpeedFrom.VC; values[CutBox.N.key] = "" }
            CutBox.N -> { speedFrom = SpeedFrom.N; values[CutBox.VC.key] = "" }
            CutBox.F -> { feedFrom = FeedFrom.F; values[CutBox.FZ.key] = "" }
            CutBox.FZ -> { feedFrom = FeedFrom.FZ; values[CutBox.F.key] = "" }
            else -> Unit
        }
    }

    /** 계산기 입력. 날 수가 1~100 정수가 아니면 0(→ 계산기의 BAD_INPUT) */
    fun input(): CutInput = CutInput(
        d = num(CutBox.D),
        speedFrom = speedFrom,
        vc = num(CutBox.VC),
        n = num(CutBox.N),
        feedFrom = feedFrom,
        f = num(CutBox.F),
        fz = num(CutBox.FZ),
        z = num(CutBox.Z)?.let { if (it == floor(it) && abs(it) <= Cutting.MAX_TEETH) it.toInt() else 0 },
        length = num(CutBox.LENGTH),
        noseR = num(CutBox.NOSE_R),
        smax = num(CutBox.SMAX),
    )

    // ── 드릴링 ──

    val drillMaker: DrillMaker? get() = maker?.let { DrillCatalog.maker(it) }
    val drillSeries: DrillSeries? get() = series?.let { drillMaker?.series?.getOrNull(it) }
    val condition: DrillCondition? get() = row?.let { drillSeries?.conditions?.getOrNull(it) }
    val manual: Boolean get() = maker == MANUAL

    fun pickMaker(key: String) {
        if (key == maker) return
        maker = key
        series = null
        row = null
    }

    fun pickSeries(i: Int) {
        if (i == series) return
        series = i
        row = null
    }

    fun pickIso(g: IsoGroup) {
        iso = g
        if (condition?.iso != g) row = null
    }

    fun pickRow(i: Int) {
        row = i
        drillSeries?.conditions?.getOrNull(i)?.let { iso = it.iso }
    }

    /** 드릴 조회 결과. 메이커 · 시리즈 · 행을 다 고르기 전(직접 입력은 메이커만)이면 null */
    fun drill(): DrillResult? {
        val d = num(CutBox.DRILL_D)
        if (manual) return DrillCatalog.manual(d, num(CutBox.DRILL_VC), num(CutBox.DRILL_F))
        val m = drillMaker ?: return null
        val s = drillSeries ?: return null
        val r = row?.takeIf { it in s.conditions.indices } ?: return null
        return DrillCatalog.lookup(m, s, r, d)
    }

    /** 드릴링에서 아직 고르지 · 넣지 않은 것(파랑 안내의 '남은 것') */
    fun drillMissing(): List<String> = buildList {
        if (maker == null) add("메이커")
        if (!manual) {
            if (drillSeries == null) add("공구 종류")
            if (condition == null) add("피삭재")
        }
        if (value(CutBox.DRILL_D).isEmpty()) add("직경")
        if (manual && value(CutBox.DRILL_VC).isEmpty()) add("절삭 속도 Vc")
    }

    companion object {
        private val boxes = CutBox.entries

        val Saver = listSaver<CuttingState, String>(
            save = { s ->
                listOf(
                    s.tab.name, s.speedFrom.name, s.feedFrom.name, s.maker ?: "", s.series?.toString() ?: "", s.iso?.name ?: "",
                    s.row?.toString() ?: "",
                ) + boxes.map { s.value(it) }
            },
            restore = { l ->
                CuttingState(
                    tab = CutTab.valueOf(l[0]),
                    speedFrom = SpeedFrom.valueOf(l[1]),
                    feedFrom = FeedFrom.valueOf(l[2]),
                    maker = l[3].takeIf { it.isNotEmpty() },
                    series = l[4].toIntOrNull(),
                    iso = l[5].takeIf { it.isNotEmpty() }?.let { IsoGroup.valueOf(it) },
                    row = l[6].toIntOrNull(),
                    values = boxes.mapIndexed { i, b -> b.key to l[7 + i] }.toMap(),
                )
            },
        )
    }
}

// ── 숫자 글 ──

/** 소수 digits 자리(5 는 올림). 화면 숫자는 모두 이것으로 — 골든이 같은 글을 적는다 */
internal fun fixed(v: Double, digits: Int): String {
    var p = 1L
    repeat(digits) { p *= 10 }
    val neg = v < 0
    val q = floor(abs(v) * p + 0.5 + 1e-9).toLong()
    val body = if (digits == 0) "${q / p}" else "${q / p}.${(q % p).toString().padStart(digits, '0')}"
    return if (neg && q != 0L) "-$body" else body
}

/** 끝 0 을 지운 글(최대 digits 자리): 120 · 79.2 · 0.025 */
internal fun trim(v: Double, digits: Int): String = fixed(v, digits).let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it }

/** 회전수 · 분당 이송(드릴 표) — 정수 */
internal fun rpmText(v: Double): String = fixed(v, 0)

/** 이송 f: 소수 셋째 자리까지, 적어도 둘째 자리(0.20 · 0.025) */
internal fun feedText(v: Double): String = trim(v, 3).let { t ->
    val dot = t.indexOf('.')
    when {
        dot < 0 -> "$t.00"
        t.length - dot - 1 < 2 -> t + "0".repeat(2 - (t.length - dot - 1))
        else -> t
    }
}

/** 직경 글(끝 0 없음): 10 · 20.99 · 6.3 */
internal fun diaText(v: Double): String = Fmt.nc(v).removeSuffix(".")

// ── 기본계산기 화면 글 ──

/** 기본계산기 결과를 화면 글로(화면과 골든이 같이 쓴다). null = 그 줄은 아직 계산 안 됨 */
internal data class BasicView(
    /** 기준이 아닌 쪽 칸의 자동 값(Vc 를 넣었으면 n, n 을 넣었으면 Vc) */
    val vcAuto: String?,
    val nAuto: String?,
    /** fz 로 구한 f */
    val fAuto: String?,
    val vf: String?,
    val timeMin: String?,
    val timeSec: String?,
    val rz: String?,
    val ra: String?,
    /** 실제 거칠기 어림(강 · 주철) */
    val practical: String?,
    val g96: List<String>,
)

internal fun basicView(s: CuttingState, r: CutResult): BasicView {
    val vcAuto = if (s.speedFrom == SpeedFrom.N) r.vc?.let { fixed(it, 1) } else null
    val nAuto = if (s.speedFrom == SpeedFrom.VC) r.n?.let { fixed(it, 0) } else null
    val fAuto = if (s.feedFrom == FeedFrom.FZ) r.f?.let { fixed(it, 3) } else null
    val practical = r.rz?.let { rz ->
        val st = Cutting.PRACTICAL_STEEL
        val ci = Cutting.PRACTICAL_CAST_IRON
        "실제 Rmax 어림: 강 ${fixed(rz * st.start, 2)} ~ ${fixed(rz * st.endInclusive, 2)} μm · 주철 ${fixed(rz * ci.start, 2)} ~ ${fixed(rz * ci.endInclusive, 2)} μm"
    }
    val g = r.g96
    val g96 = buildList {
        if (g == null) return@buildList
        add("Ø${Fmt.fixed3(g.dLimit)} 보다 작은 지름부터 회전수가 S${fixed(g.smax, 0)} 에 막힙니다 (Vc ${trim(g.vc, 1)} m/min)")
        val d = s.num(CutBox.D)
        val nCmd = g.nCommand
        val n = g.n
        val vcAct = g.vcActual
        if (d != null && nCmd != null && n != null && vcAct != null) {
            if (g.clamped) {
                add("Ø${diaText(d)}: G96 이 내려는 회전수 ${fixed(nCmd, 0)} → 막혀서 S${fixed(g.smax, 0)} · 실제 주속 ${fixed(vcAct, 1)} m/min")
                r.f?.let { f -> add("막힌 회전수로 분당 이송 ${fixed(Cutting.feedRate(f, n), 1)} mm/min (f × S${fixed(g.smax, 0)})") }
            } else {
                add("Ø${diaText(d)}: 회전수 ${fixed(nCmd, 0)} — 막히지 않음")
            }
        }
    }
    return BasicView(
        vcAuto = vcAuto,
        nAuto = nAuto,
        fAuto = fAuto,
        vf = r.vf?.let { fixed(it, 1) },
        timeMin = r.timeMin?.let { fixed(it, 2) },
        timeSec = r.timeSec?.let { fixed(it, 1) },
        rz = r.rz?.let { fixed(it, 2) },
        ra = r.ra?.let { fixed(it, 2) },
        practical = practical,
        g96 = g96,
    )
}

/** 기본계산기 경고 글(화면이 고른 한국어) */
internal fun cutWarningText(w: CutWarning, s: CuttingState, r: CutResult): String = when (w) {
    CutWarning.BAD_INPUT ->
        "빨간 칸(${r.bad.mapNotNull { f -> CutBox.entries.firstOrNull { it.cut == f }?.label }.joinToString(" · ")}) 값은 계산에 쓰지 않습니다 — " +
            "0 보다 큰 숫자로 넣으세요(지름 · 길이 · 이송 · 노즈 R 은 10000 mm 이하, Vc · 회전수는 1000000 이하, 날 수는 1 ~ 100 의 정수). " +
            "그 칸에 기대는 결과만 비우고 나머지는 계산합니다."
    CutWarning.FEED_TOO_BIG_FOR_NOSE ->
        "이송 f ${r.f?.let { fixed(it, 3) }} mm/rev — 노즈 R 의 두 배(${s.num(CutBox.NOSE_R)?.let { fixed(it * 2, 3) }}) 이상입니다. 노즈 원호만으로 된 " +
            "이론 단면이 아니어서 거칠기를 내지 않습니다."
    CutWarning.RPM_CLAMPED -> {
        val g = r.g96
        "G96 이면 이 지름에서 회전수가 G50 S${g?.let { fixed(it.smax, 0) }} 에 막힙니다 — 실제 주속은 ${g?.vcActual?.let { fixed(it, 1) }} m/min 입니다. " +
            "위의 회전수 · 분당 이송 · 가공 시간은 식의 회전수(${r.n?.let { fixed(it, 0) }}) 기준이고, 기계 회전수는 S${g?.let { fixed(it.smax, 0) }} 입니다(아래 G96 카드)."
    }
}

/** 키패드 위 한 줄: 나온 결과만 */
internal fun basicPreview(s: CuttingState, v: BasicView): String? = listOfNotNull(
    (if (s.speedFrom == SpeedFrom.VC) v.nAuto?.let { "n $it rpm" } else v.vcAuto?.let { "Vc $it m/min" }),
    v.vf?.let { "vf $it mm/min" },
    v.timeMin?.let { "$it 분" },
    v.rz?.let { "Rmax $it μm" },
).joinToString(" · ").takeIf { it.isNotEmpty() }

// ── 드릴링 화면 글 ──

/** 시리즈 형식 → 한글 이름 */
internal fun drillTypeText(type: String): String = when (type) {
    "solid-carbide" -> "솔리드 초경 드릴"
    "exchangeable-head" -> "헤드 교환식 드릴"
    "indexable" -> "인서트 드릴 (인덱서블)"
    else -> type
}

/** 급유 글: 앞의 영어 낱말(internal · external · both)만 한글로, 나머지(카탈로그 각주)는 그대로 */
internal fun coolantText(c: String): String {
    val head = c.substringBefore(' ')
    val ko = when (head) {
        "internal" -> "내부 급유"
        "external" -> "외부 급유"
        "both" -> "내부 · 외부 급유"
        else -> return c
    }
    return ko + c.removePrefix(head)
}

/** 시리즈 이름을 카드 제목(첫 ' (' · ' — ' 앞)과 나머지로 */
internal fun seriesTitle(name: String): Pair<String, String?> {
    val cut = listOf(name.indexOf(" ("), name.indexOf(" — ")).filter { it > 0 }.minOrNull() ?: return name to null
    return name.substring(0, cut) to name.substring(cut + 1).trim()
}

/** 재질 글 'English / 한글' 의 한글 쪽(없으면 통째) */
internal fun materialKo(c: DrillCondition): String = c.material.substringAfter(" / ", c.material)

/** 재질 글의 카탈로그 원문(영어) 쪽 */
internal fun materialEn(c: DrillCondition): String = c.material.substringBefore(" / ")

/** 직경 범위 글: Ø3 ~ 16 (줄이 '~' 앞뒤에서 바뀌지 않게 붙는 띄어쓰기) */
internal fun rangeText(a: Double, b: Double): String = "Ø${diaText(a)}$TILDE${diaText(b)}"

internal fun rangeText(r: ClosedFloatingPointRange<Double>): String = rangeText(r.start, r.endInclusive)

/** ' ~ ' — 앞뒤가 줄 바꿈 없는 띄어쓰기(U+00A0) */
internal const val TILDE = "\u00A0~\u00A0"

/** 행 목록의 한 줄 아래 글: 경도 · Vc · 직경 범위 */
internal fun rowSub(series: DrillSeries, c: DrillCondition): String = listOfNotNull(
    c.hardness,
    when {
        c.rpmTable -> "회전수 표"
        c.vcMin == null && c.vcRec == null && c.vcMax == null -> "Vc 칸 없음"
        else -> "Vc\u00A0" + spanText(Span3(c.vcMin, c.vcRec, c.vcMax)) { trim(it, 1) }
    },
    rangeText(DrillCatalog.rowRange(series, c)),
).joinToString(" · ")

/** 최소 ~ 최대 (권장 r) */
private fun spanText(s: Span3, f: (Double) -> String): String {
    val range = listOfNotNull(s.min, s.max).joinToString(TILDE) { f(it) }
    val rec = s.rec ?: return range
    return if (range.isNotEmpty()) "$range (권장 ${f(rec)})" else "권장 ${f(rec)}"
}

/** 결과 표 한 줄: 이름 · 단위 · 최소 · 권장 · 최대(없는 칸 null) */
internal data class DrillLine(val name: String, val unit: String, val min: String?, val rec: String?, val max: String?)

internal fun drillLines(r: DrillResult): List<DrillLine> {
    fun line(name: String, unit: String, s: Span3?, f: (Double) -> String) = DrillLine(name, unit, s?.min?.let(f), s?.rec?.let(f), s?.max?.let(f))
    return listOf(
        line("절삭 속도 Vc", if (r.vcConverted) "m/min · 환산" else "m/min", r.vc) { trim(it, 1) },
        line("회전수 n", if (r.nFromTable) "rpm · 표 값" else "rpm", r.n, ::rpmText),
        line("이송 f", "mm/rev", r.f, ::feedText),
        line("분당 이송 vf", "mm/min", r.vf, ::rpmText),
    )
}

/** 이송 구간 글: Ø8 열 · Ø10 ~ 11.99 구간 */
internal fun bandText(b: FeedBand): String = if (b.dMin == b.dMax) "Ø${diaText(b.dMin)} 열" else "${rangeText(b.dMin, b.dMax)} 구간"

private fun bandValues(b: FeedBand): String = listOfNotNull(
    Span3(b.fMin, b.fRec, b.fMax).takeUnless { it.isEmpty }?.let { "f " + spanText(it, ::feedText) },
    b.rpm?.let { "$it rpm" },
).joinToString(" · ")

/** 출처 줄: 카탈로그 · 판 · 쪽 */
internal fun sourceText(r: DrillResult): String? {
    val src = r.source ?: return null
    return listOfNotNull(src.title, src.edition, r.page?.let { "쪽 $it" }).joinToString(" · ")
}

/** 드릴 경고의 한국어 글(화면 · 골든) */
internal fun drillWarningText(w: DrillWarning, s: CuttingState, r: DrillResult): String {
    val series = s.drillSeries
    val c = s.condition
    val d = r.diameter?.let(::diaText)
    return when (w) {
        DrillWarning.MISSING_DIAMETER, DrillWarning.MISSING_VC ->
            "값을 넣으면 바로 계산합니다 — 남은 것: ${s.drillMissing().joinToString(" · ")}"
        DrillWarning.MISSING_FEED -> "이송 f 를 넣으면 분당 이송 vf 도 계산합니다(지금은 회전수만)."
        DrillWarning.BAD_INPUT -> "빨간 칸은 0 보다 큰 숫자로 넣으세요(직경 · 이송은 10000 mm 이하, Vc 는 1000000 이하)."
        DrillWarning.OUTSIDE_ROW -> {
            val range = if (series != null && c != null) rangeText(DrillCatalog.rowRange(series, c)) else ""
            "직경 Ø$d: 이 행의 직경 범위($range) 밖입니다 — 표에 없는 직경이라 숫자를 내지 않습니다." +
                if (r.alternatives.isNotEmpty()) " 같은 재질의 다른 직경 행이 있습니다." else " 다른 공구 종류(시리즈)를 고르세요."
        }
        DrillWarning.OUTSIDE_SERIES ->
            "직경 Ø$d: 이 시리즈의 카탈로그 주문 범위(${series?.let { rangeText(it.diameterMin, it.diameterMax) }}) 밖입니다 — " +
                "절삭 조건표에는 그 열이 있어 값은 냈지만, 그 직경의 공구가 있는지 카탈로그에서 확인하세요."
        DrillWarning.NOT_IN_FEED_TABLE ->
            "직경 Ø$d: 이송 표의 직경 사이입니다 — 보간하지 않아 " + (if (c?.rpmTable == true) "회전수 · Vc · " else "") + "이송 · 분당 이송을 비웁니다. " +
                listOfNotNull(
                    r.below?.let { "아래 ${bandText(it)}: ${bandValues(it)}" },
                    r.above?.let { "위 ${bandText(it)}: ${bandValues(it)}" },
                ).joinToString(" / ")
        DrillWarning.NO_FEED_IN_TABLE ->
            "이 행은 카탈로그 표에 이송 값이 없습니다(각주만) — 이송 · 분당 이송을 비웁니다. 깊이 각주: ${series?.depth ?: "—"}"
        DrillWarning.NO_VC_IN_TABLE -> "이 행은 카탈로그 표의 Vc 칸이 비어 있습니다 — 이송만 보이고 회전수 · 분당 이송은 비웁니다."
    }
}

/** 멈춤(숫자 없음) 경고 */
internal val DrillWarning.stops: Boolean
    get() = this == DrillWarning.MISSING_DIAMETER || this == DrillWarning.MISSING_VC || this == DrillWarning.BAD_INPUT || this == DrillWarning.OUTSIDE_ROW

/** 키패드 위 한 줄: 권장(없으면 범위) Vc · n · f · vf */
internal fun drillPreview(r: DrillResult?): String? {
    if (r == null || r.warnings.any { it.stops }) return null
    fun pick(s: Span3?, f: (Double) -> String) = s?.let { x -> x.rec?.let(f) ?: listOfNotNull(x.min, x.max).joinToString("~") { f(it) }.ifEmpty { null } }
    return listOfNotNull(
        pick(r.vc) { trim(it, 1) }?.let { "Vc $it" },
        pick(r.n, ::rpmText)?.let { "n $it" },
        pick(r.f, ::feedText)?.let { "f $it" },
        pick(r.vf, ::rpmText)?.let { "vf $it" },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** ISO 군 짧은 이름(재료분석 자료의 title) */
internal fun isoTitle(g: IsoGroup): String = WorkMaterials.of(g).title
