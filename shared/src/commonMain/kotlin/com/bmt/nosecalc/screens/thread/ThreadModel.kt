package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieHeadCatalog
import com.bmt.nosecalc.calc.DieInput
import com.bmt.nosecalc.calc.DieSpeedHit
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Input
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.InsertForm
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.MAX_MM
import com.bmt.nosecalc.calc.PipeInput
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeSolution
import com.bmt.nosecalc.calc.PipeSpec
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadInsert
import com.bmt.nosecalc.calc.ThreadInserts
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSeries
import com.bmt.nosecalc.calc.ThreadSpec
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ThreadTol
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.calc.WorkMaterials
import com.bmt.nosecalc.calc.ncWords
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 숫자칸. key = 상태 열쇠 · 칸 묶음 id, advanced = '고급 공차 및 인선 설정' 접이 안의 칸,
 * straight · pipe = 곧은 나사(미터 · UN) · 관용 테이퍼(PT · NPT) 화면에 있는 칸.
 * 도면 값(Z 시작 · 목표 · 첫 절입)은 빈칸 = 아직 안 넣음, 가공 값은 빈칸 = 기본값(자리표시 '기본 …').
 * 관용 테이퍼의 두 칸(기준면 Z · 게이지 면 옮김)은 맨 끝에 둔다 — 저장 글의 앞 10칸 차례가 옛 글과 같게(Saver)
 */
internal enum class ThreadField(
    val key: String,
    val label: String,
    val unit: String,
    val advanced: Boolean = false,
    val straight: Boolean = true,
    val pipe: Boolean = true,
) {
    Z_START("zStart", "Z축 시작점", "mm"),
    RPM("rpm", "회전수 (RPM)", ""),
    Z_END("zEnd", "가공 목표점 (Z)", "mm"),
    TARGET("target", "공차 목표", "%", advanced = true, pipe = false),
    TIP_R("tipR", "인선 R", "mm", advanced = true, pipe = false),
    FINISH_COUNT("m", "정삭 횟수 m", "회", advanced = true),
    MIN_CUT("minCut", "최소 절입 Δdmin", "mm", advanced = true),
    ALLOW("allow", "정삭 여유 d", "mm", advanced = true),
    X_CLEAR("xClear", "시작 X 여유", "mm", advanced = true),
    FIRST_CUT("firstCut", "첫 절입 Δd", "mm"),

    /** 관용 테이퍼: 공작물 끝면 Z(숫나사 관 끝 · 암나사 입구 면), 빈칸 = 0 */
    Z_REF("zRef", "기준면 Z", "mm", straight = false),

    /** 관용 테이퍼: 게이지 면 옮김(산, + = 조립 때 더 깊이), 빈칸 = 0 */
    GAUGE_SHIFT("gaugeShift", "게이지 면 옮김", "산", advanced = true, straight = false),
}

/**
 * 화면의 나사 종류 넷: 곧은 나사(미터 · UN — 계산기 [ThreadKind], G92 · G76)와 관용 테이퍼(PT · NPT — [PipeKind], G92 테이퍼만 — 사용자 결정 2026-11-05).
 * 이름이 [ThreadKind] 와 같아 옛 저장 글("METRIC" · "UN")을 그대로 읽는다
 */
internal enum class ThreadFamily(val kind: ThreadKind?, val pipeKind: PipeKind?) {
    METRIC(ThreadKind.METRIC, null),
    UN(ThreadKind.UN, null),
    PT(null, PipeKind.PT),
    NPT(null, PipeKind.NPT),
}

/**
 * 20UN 묶음에서 위로 올리는 크기(사용자 결정 2026-11-05 — 주로 쓰는 1/4 · 3/8 · 1/2 · 3/4 · 1). 사이즈 시트에 '자주 쓰는 크기' 로 따로 보인다
 */
internal val COMPANY_20UN = listOf("1/4", "3/8", "1/2", "3/4", "1")

/** UN 사이즈 시트의 한 묶음 크기 차례 — 지름 순, 20UN 은 [COMPANY_20UN] 다섯이 먼저 */
internal fun unSizes(g: UnGroup): List<String> {
    val all = ThreadTable.group(g).map { it.size }
    return when (g) {
        UnGroup.UNC, UnGroup.UNF, UnGroup.UNEF -> all
        UnGroup.UN20 -> COMPANY_20UN + all.filter { it !in COMPANY_20UN }
    }
}

/** 규격 줄이 본디 속한 UN 묶음(일정 피치 줄은 20UN) — 미터는 null */
private fun naturalGroup(s: ThreadSpec): UnGroup? = when (s.series) {
    ThreadSeries.UNC -> UnGroup.UNC
    ThreadSeries.UNF -> UnGroup.UNF
    ThreadSeries.UNEF -> UnGroup.UNEF
    ThreadSeries.UN -> UnGroup.UN20
    ThreadSeries.M_COARSE, ThreadSeries.M_FINE -> null
}

/** 고르기 시트(드롭다운) 종류. INSERT = 인서트(고급 설정), DIE_SPEED = 다이스 카탈로그 절삭 속도 줄 */
internal enum class Pick { CLASS, KIND, SIZE, PITCH, MATERIAL, ANGLE, CHAMFER, INSERT, DIE_SPEED }

/** NC 카드 탭: G92 다회(회사 흐름 — 처음 값, 사용자 결정 10-29) · G76 두 줄 */
internal enum class ThreadNc { G92, G76 }

/**
 * 가공 방식: 바이트(G92 · G76 — 처음) · 다이스 헤드(고정형 자동 열림 — 사용자 결정 2026-11-05: M · UN 숫나사만, 나사 화면 안에서 고른다).
 * 내경이나 관용 테이퍼로 가면 바이트로 돌아간다([ThreadState.switchInner] · [ThreadState.switchFamily])
 */
internal enum class ThreadMethod { BITE, DIE }

/** 다이스 헤드 화면의 숫자칸(끝면 Z 는 관용 테이퍼의 기준면 Z 칸과 같은 값 — 둘 다 공작물 끝면) */
private val DIE_FIELDS = listOf(ThreadField.Z_START, ThreadField.RPM, ThreadField.Z_END, ThreadField.Z_REF)

/**
 * 나사 G76 화면 상태. 숫자칸 값은 쓴 그대로의 글(빈칸 = 안 넣음 · 기본값).
 * 처음엔 모두 빈칸 · 사이즈 안 고름(미터) — 파랑 안내가 남은 값을 적는다. 고르는 칸(급수 · 각도 · 면취량)은 null = 기본(원본 08 의 값).
 * 급수는 외경/내경 · 나사 종류에 딸려 있어 그 둘을 바꾸면 기본으로 돌아간다(새 규격의 계열에 없는 급수면 규격을 바꿀 때도).
 * 곧은 나사(미터 · UN)는 [spec], 관용 테이퍼(PT · NPT)는 [pipe] 를 쓴다 — 종류를 바꾸면 둘 다 비운다
 */
@Stable
internal class ThreadState(
    inner: Boolean = false,
    family: ThreadFamily = ThreadFamily.METRIC,
    spec: ThreadSpec? = null,
    cls: ThreadClass? = null,
    material: IsoGroup? = null,
    dir: ThreadDir = ThreadDir.FORWARD,
    angle: Int? = null,
    chamfer: Int? = null,
    values: Map<String, String> = emptyMap(),
    advOpen: Boolean = false,
    nc: ThreadNc = ThreadNc.G92,
    pipe: PipeSpec? = null,
    unGroup: UnGroup = UnGroup.UNC,
    taperWord: Char = 'I',
    method: ThreadMethod = ThreadMethod.BITE,
    insert: String? = null,
    dieSpeed: String? = null,
) {
    var inner by mutableStateOf(inner)
        private set
    var family by mutableStateOf(family)
        private set

    /** 가공 방식 — 다이스 헤드는 곧은 나사 숫나사에서만(되살린 상태도 같다) */
    var method by mutableStateOf(if (!inner && family.kind != null) method else ThreadMethod.BITE)
        private set

    /** 고른 인서트 열쇠([insertKey]) — 지금 내외 · 종류 · 산수에 맞을 때만 보인다([shownInsert]) */
    var insert by mutableStateOf(insert)
        private set

    /** 다이스 조건 카드의 카탈로그 절삭 속도 줄 열쇠([dieSpeedKey]) — 견주기만(계산에 안 씀) */
    var dieSpeed by mutableStateOf(dieSpeed)
    var spec by mutableStateOf(spec)
        private set

    /** 관용 테이퍼 크기(PT · NPT 일 때) */
    var pipe by mutableStateOf(pipe)
        private set

    /** UN 사이즈 시트의 계열 묶음(UNC · UNF · UNEF · 20UN) — 규격을 고르면 그 줄이 든 묶음으로 따라간다 */
    var unGroup by mutableStateOf(unGroup)

    /** 관용 테이퍼 G92 의 테이퍼 낱말 'I'(처음 — 사용자 결정 2026-11-05) · 'R' */
    var taperWord by mutableStateOf(taperWord)

    /** 곧은 나사의 계산기 종류(관용 테이퍼는 null) */
    val kind: ThreadKind? get() = family.kind

    /** null = 기본 급수(6g · 6H · 2A · 2B). 규격의 계열에 없는 급수(UNEF · UN 의 1A · 1B)는 받지 않고 기본으로 — 되살린 상태도 같다 */
    var cls by mutableStateOf(cls?.takeIf { c -> spec == null || c in ThreadClass.choices(spec.series, inner) })
        private set

    /** 바이트는 기록용(계산에 안 씀 — 출처 있는 나사 절삭 속도 자료가 없다), 다이스 헤드는 회사 Vc 를 소재마다 저장하는 열쇠([DieStore]) */
    var material by mutableStateOf(material)
    var dir by mutableStateOf(dir)

    /** null = 60° */
    var angle by mutableStateOf(angle)

    /** null = 10 (1.0P) */
    var chamfer by mutableStateOf(chamfer)

    /** 고급 설정 펼침 */
    var advOpen by mutableStateOf(advOpen)

    /** NC 카드에 보이는 글(처음은 G92 다회) */
    var nc by mutableStateOf(nc)
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    fun value(f: ThreadField): String = values[f.key] ?: ""
    fun set(f: ThreadField, v: String) {
        values[f.key] = v
    }

    fun num(f: ThreadField): Double? = value(f).toDoubleOrNull()

    /** 내경으로 가면 바이트로 돌아간다(다이스 헤드는 숫나사만) */
    fun switchInner(v: Boolean) {
        inner = v
        if (cls?.internal != v) cls = null
        if (!dieAllowed) method = ThreadMethod.BITE
    }

    /** 나사 종류를 바꾸면 사이즈(곧은 나사 · 관용 테이퍼 둘 다) · 급수를 비운다. 숫자칸은 그대로. 관용 테이퍼로 가면 바이트로 돌아간다 */
    fun switchFamily(f: ThreadFamily) {
        if (f == family) return
        family = f
        spec = null
        pipe = null
        cls = null
        if (!dieAllowed) method = ThreadMethod.BITE
    }

    /** 다이스 헤드를 고를 수 있나 — M · UN 숫나사(사용자 결정 2026-11-05) */
    val dieAllowed: Boolean get() = !inner && family.kind != null

    /** 다이스 헤드 화면인가 */
    val isDie: Boolean get() = method == ThreadMethod.DIE && dieAllowed

    /** 가공 방식 고르기 — 다이스 헤드는 [dieAllowed] 일 때만 받는다 */
    fun pickMethod(m: ThreadMethod) {
        method = if (m == ThreadMethod.DIE && !dieAllowed) ThreadMethod.BITE else m
    }

    /** 인서트 자료의 나사 꼴 — 미터는 자료가 없다(VARDEX · Carmex 의 UN 20산 · NPT · BSPT 만). PT 는 BSPT 인서트(같은 55° 꼴) */
    private val insertForm: InsertForm?
        get() = when (family) {
            ThreadFamily.METRIC -> null
            ThreadFamily.UN -> InsertForm.UN
            ThreadFamily.PT -> InsertForm.BSPT
            ThreadFamily.NPT -> InsertForm.NPT
        }

    /** 인서트 칸을 보이나(이 종류의 인서트 자료가 있다) */
    val hasInsertData: Boolean get() = insertForm != null

    /** 지금 내외 · 종류 · 산수에 맞는 인서트(크기를 안 골랐거나 맞는 줄이 없으면 비었다) */
    val insertChoices: List<ThreadInsert>
        get() {
            val form = insertForm ?: return emptyList()
            val tpi = if (isPipe) pipe?.tpi else spec?.tpi?.toDouble()
            return tpi?.let { ThreadInserts.match(inner, form, it) }.orEmpty()
        }

    /** 고른 인서트(지금 맞는 줄일 때만) */
    val shownInsert: ThreadInsert? get() = insert?.let { k -> insertChoices.firstOrNull { insertKey(it) == k } }

    /** 인서트 고르기(null = 고르지 않음). 카탈로그에 날 끝 R 이 있으면 곧은 나사의 인선 R 칸에 넣는다 — 없으면 이름만(R 은 직접) */
    fun pickInsert(i: ThreadInsert?) {
        insert = i?.let(::insertKey)
        val r = i?.tipR ?: return
        if (!isPipe) set(ThreadField.TIP_R, plain(r))
    }

    /** 고른 카탈로그 절삭 속도 줄 */
    val shownDieSpeed: DieSpeedHit? get() = dieSpeed?.let { k -> DieHeadCatalog.speeds().firstOrNull { dieSpeedKey(it) == k } }

    /**
     * 사이즈를 고르면: 미터는 보통 나사 피치가 먼저(호칭만 쓴 도면 M10 은 보통 나사), UN 은 고른 계열 묶음([unGroup])의 그 크기 줄
     * (묶음에 없는 크기면 UNC · UNF · UNEF · UN 차례의 첫 줄). 같은 크기를 다시 고르면 고른 피치 그대로(UN 은 그 줄이 지금 묶음에 들 때)
     */
    fun pickSize(size: String) {
        val now = spec
        when (val k = kind ?: return) {
            ThreadKind.METRIC -> if (now?.size != size) pickSpec(ThreadTable.pitches(k, size).first())
            ThreadKind.UN -> {
                val group = ThreadTable.group(unGroup)
                if (now?.size == size && now in group) return
                pickSpec(group.firstOrNull { it.size == size } ?: ThreadTable.pitches(k, size).first())
            }
        }
    }

    /** 고른 급수가 새 규격의 계열에 없으면(UNEF · UN 에는 1A · 1B 가 없다) 기본으로. UN 줄이 지금 묶음에 없으면 묶음이 그 줄의 계열로 따라간다 */
    fun pickSpec(s: ThreadSpec) {
        spec = s
        if (cls?.let { it !in ThreadClass.choices(s.series, inner) } == true) cls = null
        if (s !in ThreadTable.group(unGroup)) naturalGroup(s)?.let { unGroup = it }
    }

    /** 관용 테이퍼 크기 고르기(산수는 크기에 딸려 있다) */
    fun pickPipe(p: PipeSpec) {
        pipe = p
    }

    /** 급수 시트의 줄 — 규격을 골랐으면 그 계열의 급수, 관용 테이퍼는 없음(게이지 공차) */
    val classChoices: List<ThreadClass>
        get() = spec?.let { ThreadClass.choices(it.series, inner) } ?: kind?.let { ThreadClass.choices(it, inner) }.orEmpty()

    fun pickClass(c: ThreadClass) {
        cls = c
    }

    /** 화면에 보이는 급수(고르지 않았으면 기본). 관용 테이퍼는 급수가 없어 null */
    val shownClass: ThreadClass? get() = cls ?: kind?.let { ThreadClass.default(it, inner) }

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
        finishCount = finishCount(),
        minCut = num(ThreadField.MIN_CUT),
        finishAllow = num(ThreadField.ALLOW),
        firstCut = num(ThreadField.FIRST_CUT),
        xClear = num(ThreadField.X_CLEAR),
    )

    /** 관용 테이퍼 계산기 입력 — 곧은 나사와 같은 칸(기준면 Z · 게이지 면 옮김은 빈칸 = 0), 방향 · 각도 · 면취량 · 공차 목표 · 인선 R 은 안 씀 */
    fun pipeInput(): PipeInput = PipeInput(
        inner = inner,
        spec = pipe,
        zRef = num(ThreadField.Z_REF),
        zStart = num(ThreadField.Z_START),
        zEnd = num(ThreadField.Z_END),
        rpm = num(ThreadField.RPM),
        gaugeShift = num(ThreadField.GAUGE_SHIFT),
        firstCut = num(ThreadField.FIRST_CUT),
        minCut = num(ThreadField.MIN_CUT),
        finishAllow = num(ThreadField.ALLOW),
        finishCount = finishCount(),
        xClear = num(ThreadField.X_CLEAR),
        taperWord = taperWord,
    )

    /**
     * 다이스 헤드 계산기 입력 — Z 시작 · 끝 · 회전수(직접)는 이 상태의 칸, 끝면 Z 는 기준면 Z 칸(빈칸 = 0 — 관용 테이퍼와 같이 공작물 끝면),
     * 다이스 조건(Vc 는 가공 소재마다 · 비율 · 멈춤 · 물러남 · 플로트 · 목 산수)은 회사 값 저장소. 비율 · 멈춤 · 물러남 빈칸 = 사용자 결정 처음 값
     */
    fun dieInput(d: DieStore): DieInput = DieInput(
        spec = spec,
        vc = d.num(DieField.VC, material),
        rpm = num(ThreadField.RPM),
        zStart = num(ThreadField.Z_START),
        zEnd = num(ThreadField.Z_END),
        ratio = d.num(DieField.RATIO, material) ?: DieHeadCalc.RATIO_DEFAULT,
        dwell = d.num(DieField.DWELL, material) ?: DieHeadCalc.DWELL_DEFAULT,
        retractZ = d.num(DieField.RETRACT, material) ?: DieHeadCalc.RETRACT_Z_DEFAULT,
        floatTravel = d.num(DieField.FLOAT, material),
        throatThreads = d.num(DieField.THROAT, material),
        zFace = num(ThreadField.Z_REF) ?: 0.0,
    )

    private fun finishCount(): Int? = num(ThreadField.FINISH_COUNT)?.let { if (it == floor(it) && abs(it) <= 1000) it.toInt() else 0 }

    /** 관용 테이퍼인가 */
    val isPipe: Boolean get() = family.pipeKind != null

    /**
     * 내경 나사의 가장 가는 구멍 지름(인서트의 최소 구멍과 견줌): 곧은 나사 = 지금 급수의 안지름 최소, 관용 테이퍼 = 끝 Z 안지름(값이 모자라 안 풀리면 null).
     * 외경 · 사이즈 없음은 null
     */
    val insertBore: Double?
        get() = when {
            !inner -> null
            isPipe -> PipeThreadCalc.calc(pipeInput()).solution?.crestAtEnd
            else -> spec?.let { sp -> shownClass?.let { ThreadTol.limits(sp, it).crest.min } }
        }

    /**
     * 이 종류의 화면에 있는 칸(곧은 나사는 기준면 Z · 게이지 면 옮김이 없고, 관용 테이퍼는 공차 목표 · 인선 R 이 없다 — 풀 프로파일 인서트).
     * 다이스 헤드는 Z 시작 · 회전수 · Z 끝 · 끝면 Z 넷(다이스 조건 칸은 [DieStore])
     */
    val fields: List<ThreadField>
        get() = when {
            isPipe -> ThreadField.entries.filter { it.pipe }
            isDie -> DIE_FIELDS
            else -> ThreadField.entries.filter { it.straight }
        }

    /** 게이지 면 옮김 한도(± 산) — 크기를 골랐을 때 */
    private val shiftTol: Double? get() = pipe?.let { if (inner) it.tolTurnsInner else it.tolTurns }

    /** 아직 안 넣은 도면 값(파랑 안내의 '남은 것') */
    fun missing(): List<String> = buildList {
        if ((if (isPipe) pipe else spec) == null) add("사이즈")
        if (num(ThreadField.Z_START) == null) add("Z축 시작점")
        if (num(ThreadField.Z_END) == null) add("가공 목표점 (Z)")
        if (num(ThreadField.FIRST_CUT) == null) add("첫 절입 Δd")
    }

    /**
     * 범위 밖 칸 — 계산기의 BAD_INPUT 과 같은 규칙(G76Calc.calc · PipeThreadCalc.calc). 값 부족과 따로 보아 넣자마자 빨갛게 짚는다.
     * 이 종류의 화면에 없는 칸은 보지 않는다(다른 종류에서 넣은 값이 남아 있어도)
     */
    fun bad(): Set<ThreadField> = fields.filter { f ->
        val v = num(f) ?: return@filter false
        if (!v.isFinite()) return@filter true
        when (f) {
            ThreadField.Z_START, ThreadField.Z_END, ThreadField.Z_REF -> abs(v) > MAX_MM
            ThreadField.RPM -> v < 1 || v > MAX_RPM
            ThreadField.TARGET -> v < 0 || v > 100
            ThreadField.FINISH_COUNT -> v != floor(v) || v < 1 || v > 99
            ThreadField.FIRST_CUT -> v <= 0 || um(v) < 1 || v > MAX_MM
            ThreadField.TIP_R, ThreadField.MIN_CUT, ThreadField.ALLOW, ThreadField.X_CLEAR -> v < 0 || v > MAX_MM
            ThreadField.GAUGE_SHIFT -> shiftTol?.let { abs(v) > it + 1e-9 } ?: false
        }
    }.toSet()

    /**
     * 계산을 멈춘 경고가 가리키는 칸(빨간 테). 관용 테이퍼의 시작 Z 가 기준면 안(START_INSIDE)이면 두 Z, 칸마다는 맞는데 숫나사 끝 골 X ≤ 0 인
     * BAD_INPUT(끝 Z 가 기준면에서 너무 멂)이면 끝 Z · 기준면 Z. 나사 길이가 짧음(SHORT_THREAD)은 숫자와 함께 나온 노랑이라 테를 두르지 않는다(빨강 = 멈춤)
     */
    fun flagged(warnings: List<G76Warning>): Set<ThreadField> {
        val bad = bad()
        return bad + warnings.flatMap { w ->
            when (w) {
                G76Warning.Z_ORDER -> listOf(ThreadField.Z_START, ThreadField.Z_END)
                G76Warning.TIP_TOO_BIG -> listOf(ThreadField.TIP_R)
                G76Warning.FINISH_GE_HEIGHT -> listOf(ThreadField.ALLOW)
                G76Warning.FIRST_CUT_LT_MIN, G76Warning.TOO_MANY_PASSES -> listOf(ThreadField.FIRST_CUT, ThreadField.MIN_CUT)
                G76Warning.CLEARANCE_TOO_BIG -> listOf(ThreadField.X_CLEAR)
                G76Warning.START_INSIDE -> listOf(ThreadField.Z_START, ThreadField.Z_REF)
                G76Warning.BAD_INPUT -> if (isPipe && bad.isEmpty()) listOf(ThreadField.Z_END, ThreadField.Z_REF) else emptyList()
                else -> emptyList()
            }
        }
    }

    /** 자리표시: 가공 값은 '기본 …'(빈칸이면 그 값으로 계산), 회전수는 '안 씀'(다이스 헤드는 'Vc 로' — 비우면 Vc 로 계산), 도면 값은 비움 */
    fun placeholder(f: ThreadField): String = when (f) {
        ThreadField.RPM -> if (isDie) "Vc 로" else "안 씀"
        ThreadField.TARGET -> "기본 ${plain(G76Calc.TARGET_DEFAULT)}"
        ThreadField.TIP_R -> spec?.let { "기본 ${Fmt.fixed3(defaultTipR(it, inner))}" } ?: "기본 규격값"
        ThreadField.FINISH_COUNT -> "기본 ${G76Calc.FINISH_COUNT_DEFAULT}"
        ThreadField.MIN_CUT -> "기본 ${plain(G76Calc.MIN_CUT_DEFAULT)}"
        ThreadField.ALLOW -> "기본 ${plain(G76Calc.FINISH_ALLOW_DEFAULT)}"
        ThreadField.X_CLEAR -> "기본 ${plain(G76Calc.X_CLEAR_DEFAULT)}"
        ThreadField.Z_REF, ThreadField.GAUGE_SHIFT -> "기본 0"
        else -> ""
    }

    companion object {
        private const val MAX_RPM = 100_000.0

        /** 옛 저장 글(2026-11-05 전 — 칸 10개 뒤에 NC 탭으로 끝남)의 칸 수. 새 글은 그 뒤에 관용 테이퍼 크기 · UN 묶음 · 테이퍼 낱말 · 새 칸 둘을 붙인다 */
        private const val OLD_FIELDS = 10

        /**
         * 앞 9칸(내외 · 종류 · 규격 · 급수 · 소재 · 방향 · 각도 · 면취량 · 고급 펼침) + 칸 10개 + NC 탭은 옛 글과 같은 자리 — 종류 이름(METRIC · UN)도 같아
         * 옛 글을 그대로 읽는다(붙인 칸이 없으면 처음 값). 그 뒤 관용 테이퍼 크기 · UN 묶음 · 테이퍼 낱말 · 새 칸 둘(2026-11-05),
         * 가공 방식 · 인서트 · 카탈로그 절삭 속도 줄(2026-11-06)을 붙인다
         */
        val Saver = listSaver<ThreadState, String>(
            save = { s ->
                val (old, added) = ThreadField.entries.partition { it.ordinal < OLD_FIELDS }
                listOf(
                    s.inner.toString(), s.family.name, s.spec?.let { "${it.series.name}|${it.size}|${it.pitch}" } ?: "",
                    s.cls?.name ?: "", s.material?.name ?: "", s.dir.name, s.angle?.toString() ?: "", s.chamfer?.toString() ?: "",
                    s.advOpen.toString(),
                ) + old.map { s.value(it) } + s.nc.name +
                    listOf(s.pipe?.let { "${it.kind.name}|${it.size}" } ?: "", s.unGroup.name, s.taperWord.toString()) + added.map { s.value(it) } +
                    listOf(s.method.name, s.insert ?: "", s.dieSpeed ?: "")
            },
            restore = { l ->
                val family = ThreadFamily.valueOf(l[1])
                val spec = l[2].split('|').takeIf { it.size == 3 }?.let { (series, size, p) ->
                    family.kind?.let { k -> ThreadTable.of(k).firstOrNull { it.series == ThreadSeries.valueOf(series) && it.size == size && it.pitch == p.toDouble() } }
                }
                val tail = 9 + OLD_FIELDS + 1
                val pipe = l.getOrNull(tail)?.split('|')?.takeIf { it.size == 2 }?.let { (kind, size) ->
                    PipeTable.of(PipeKind.valueOf(kind)).firstOrNull { it.size == size }
                }
                // 가공 방식 · 인서트 · 카탈로그 줄(2026-11-06 — 새 칸 뒤)
                val more = tail + 3 + ThreadField.entries.size - OLD_FIELDS
                ThreadState(
                    inner = l[0].toBoolean(),
                    family = family,
                    spec = spec,
                    cls = l[3].takeIf { it.isNotEmpty() }?.let { ThreadClass.valueOf(it) },
                    material = l[4].takeIf { it.isNotEmpty() }?.let { IsoGroup.valueOf(it) },
                    dir = ThreadDir.valueOf(l[5]),
                    angle = l[6].toIntOrNull(),
                    chamfer = l[7].toIntOrNull(),
                    values = ThreadField.entries.associate { f ->
                        f.key to (if (f.ordinal < OLD_FIELDS) l[9 + f.ordinal] else l.getOrNull(tail + 3 + f.ordinal - OLD_FIELDS).orEmpty())
                    },
                    advOpen = l[8].toBoolean(),
                    nc = ThreadNc.valueOf(l[9 + OLD_FIELDS]),
                    pipe = pipe?.takeIf { it.kind == family.pipeKind },
                    // 옛 글(묶음 칸 없음)은 규격 줄의 계열 묶음 — UNC 로 두면 UNF 줄이 묶음 밖에 남아 같은 크기를 누르면 UNC 피치로 바뀐다
                    unGroup = l.getOrNull(tail + 1)?.let { UnGroup.valueOf(it) } ?: spec?.let(::naturalGroup) ?: UnGroup.UNC,
                    taperWord = l.getOrNull(tail + 2)?.firstOrNull() ?: 'I',
                    method = l.getOrNull(more)?.let { ThreadMethod.valueOf(it) } ?: ThreadMethod.BITE,
                    insert = l.getOrNull(more + 1)?.takeIf { it.isNotEmpty() },
                    dieSpeed = l.getOrNull(more + 2)?.takeIf { it.isNotEmpty() },
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

/** 나사 종류 글 — 좁은 칸에서 '…' 로 줄어도 기호(M · UN · PT · NPT)가 남게 앞에 */
internal fun kindText(f: ThreadFamily): String = when (f) {
    ThreadFamily.METRIC -> "M (미터)"
    ThreadFamily.UN -> "UN (유니파이)"
    ThreadFamily.PT -> "PT (관용 테이퍼)"
    ThreadFamily.NPT -> "NPT (미국 관용 테이퍼)"
}

/** 종류 시트의 설명 한 줄 */
internal fun kindNote(f: ThreadFamily): String = when (f) {
    ThreadFamily.METRIC -> "ISO 262 보통 · 가는 나사 M1 ~ M64 · 공차 ISO 965"
    ThreadFamily.UN -> "UNC · UNF · UNEF · 20UN #0 ~ 3 · 공차 ASME B1.1 (치수는 mm 로 바꿔 씀)"
    ThreadFamily.PT -> "숫나사 R · 암나사 Rc 1/16 ~ 6 · 55° · 1/16 테이퍼 · JIS B 0203 · G92 테이퍼로만"
    ThreadFamily.NPT -> "1/16 ~ 4 · 60° · 1/16 테이퍼 · ANSI B2.1 (ASME B1.20.1) · G92 테이퍼로만"
}

/** 피치 칸 · 시트 이름 — 인치 계열은 산수 */
internal fun pitchLabel(f: ThreadFamily): String = when (f) {
    ThreadFamily.METRIC -> "피치"
    ThreadFamily.UN, ThreadFamily.PT, ThreadFamily.NPT -> "산수 (TPI)"
}

/** 공차 · 치수의 규격 이름(급수 상자 · 게이지 공차 상자의 아래 글). long = 급수 시트 설명 글 */
internal fun standardText(f: ThreadFamily, long: Boolean = false): String = when (f) {
    ThreadFamily.METRIC -> if (long) "ISO 965-1 · 965-2" else "ISO 965"
    ThreadFamily.UN -> "ASME B1.1"
    ThreadFamily.PT -> "JIS B 0203"
    // 값의 출처는 NBS H28 (1957) Part II = ANSI B2.1(지금 이름 ASME B1.20.1 — 현행판 원문과는 대조 못 함)
    ThreadFamily.NPT -> "ANSI B2.1 (ASME B1.20.1)"
}

/** 게이지 공차 산 수 글(표의 1 · 1¼ · 1½): 1 → "1", 1.25 → "1¼", 1.5 → "1½" — ¼ 단위가 아니면 소수 */
internal fun turnsText(t: Double): String {
    val n = floor(t * 4 + 0.5).toLong()
    if (abs(t * 4 - n) > 1e-9 || n < 0) return plain(t)
    val frac = listOf("", "¼", "½", "¾")[(n % 4).toInt()]
    return if (n < 4 && frac.isNotEmpty()) frac else "${n / 4}$frac"
}

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

/** 피치 시트 줄: "1.5 mm · 보통 나사" · "20 산 · UNC" · "20 산 · 20UN"(일정 피치 줄) */
internal fun pitchRow(s: ThreadSpec): Pair<String, String> = when (s.series) {
    ThreadSeries.M_COARSE -> "${plain(s.pitch)} mm" to "보통 나사 · ${s.label}"
    ThreadSeries.M_FINE -> "${plain(s.pitch)} mm" to "가는 나사 · ${s.label}"
    ThreadSeries.UNC, ThreadSeries.UNF, ThreadSeries.UNEF -> "${s.tpi} 산" to "${s.series.name} · 피치 ${Fmt.fixed3(s.pitch)} mm · ${specTitle(s)}"
    ThreadSeries.UN -> "${s.tpi} 산" to "${s.tpi}UN · 피치 ${Fmt.fixed3(s.pitch)} mm · ${specTitle(s)}"
}

/**
 * 규격 이름 — 20UN 묶음과 겹치는 UNC · UNF · UNEF 줄은 " (20UN)" 을 붙인다("3/4-20 UNEF (20UN)"), 일정 피치 줄은 "3/8-20 UN" 그대로
 * (이름은 규격 이름 + 20UN — 사용자 결정 2026-11-05)
 */
internal fun specTitle(s: ThreadSpec): String = when (s.series) {
    ThreadSeries.UNC, ThreadSeries.UNF, ThreadSeries.UNEF -> if (ThreadTable.inUn20(s)) "${s.label} (20UN)" else s.label
    ThreadSeries.M_COARSE, ThreadSeries.M_FINE, ThreadSeries.UN -> s.label
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

/** 낱말 줄 → NC 글(회사 표기 "G76P021060Q35R.02" — 낱말을 붙여 쓴다) */
internal fun ncText(words: List<NcWord>): String = buildString {
    append("G76")
    words.forEach { w -> append(w.letter).append(w.value) }
}

/**
 * G76 두 줄의 낱말(원본 08 의 'G76 [P 02] 1060 [Q 35] [R 0.02]' 꼴). 풀렸으면 계산기 NC 글을 그대로 쪼갠다(화면 = 복사 글),
 * 아직이면 넣은 값 · 기본값으로 첫 줄을 쓰고 둘째 줄의 X · P(k) 는 "—", 빈 도면 값은 "?". 숫자는 NC 글과 같은 회사 표기(R.02 — 앞 0 뺌)
 */
internal fun g76Words(s: ThreadState, sol: G76Solution?): List<List<NcWord>> {
    val raNote = "${two(s.shownChamfer)}${two(s.shownAngle)} = 면취량 ${chamferText(s.shownChamfer)} (${two(s.shownChamfer)}) · 나사산 각도 ${s.shownAngle}° (${two(s.shownAngle)}) — 설정 카드에서 고릅니다"
    fun blank(f: ThreadField) = s.value(f).isEmpty()
    if (sol != null) {
        val l1 = ncWords(sol.nc[0]).drop(1)
        val l2 = ncWords(sol.nc[1]).drop(1)
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
            edit(ThreadField.ALLOW, "R", Fmt.word(G76Calc.FINISH_ALLOW_DEFAULT)) { Fmt.word(it) },
        ),
        listOf(
            NcWord("X", "—", note = "X 는 사이즈 · Z · 첫 절입을 넣으면 계산합니다"),
            need(ThreadField.Z_END, "Z") { Fmt.word(it) },
            NcWord("P", "—", note = "P(k) 는 사이즈 · Z · 첫 절입을 넣으면 계산합니다"),
            need(ThreadField.FIRST_CUT, "Q") { um(it).toString() },
            NcWord("F", s.spec?.let { Fmt.shortLead(leadText(it.pitch)) } ?: "—", note = "F = 리드 (한 줄 나사는 피치) — 사이즈 · 피치에서"),
        ),
    )
}

/**
 * 경고 글. 값 부족은 파랑 안내, 멈춤은 빨강, 숫자와 함께 나온 것(ROOT_INTERFERES · ONE_ROUGH_PASS · SHORT_THREAD)은 노랑.
 * 관용 테이퍼 화면은 pipe 에 그 결과를 넘긴다(나사 길이 경고의 숫자)
 */
internal fun threadWarningText(w: G76Warning, s: ThreadState, sol: G76Solution? = null, pipe: PipeSolution? = null): String = when (w) {
    G76Warning.MISSING_INPUT -> "값을 넣으면 바로 계산합니다 — 남은 것: ${s.missing().joinToString(" · ")}."
    G76Warning.BAD_INPUT -> badText(s)
    G76Warning.Z_ORDER -> if (s.isPipe) {
        "관용 테이퍼 나사는 정가공(Z−)만 — Z축 시작점이 가공 목표점보다 커야 합니다 (척 쪽으로 깎음, 예: 5 → −15)."
    } else if (s.dir == ThreadDir.FORWARD) {
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
    // 관용 테이퍼(PipeThreadCalc)만 내는 두 경고 — 곧은 나사 화면에는 나오지 않는다
    G76Warning.START_INSIDE -> {
        val zs = s.num(ThreadField.Z_START)?.let { " Z${Fmt.nc(it)}" } ?: ""
        val ref = s.num(ThreadField.Z_REF)?.takeIf { ThreadField.Z_REF !in s.bad() } ?: 0.0
        "Z축 시작점$zs 이 기준면(공작물 끝면) Z${Fmt.nc(ref)} 안이거나 같습니다 — 시작점에서 X 로 급송 절입하면 날이 공작물 끝 모서리를 뭅니다. " +
            "Z축 시작점을 기준면보다 크게 하세요 (예: 기준면 + 5)."
    }
    G76Warning.SHORT_THREAD -> if (pipe != null) {
        val ref = s.num(ThreadField.Z_REF) ?: 0.0
        "나사 길이 ${Fmt.fixed3(pipe.threadLen)} mm(기준면 Z${Fmt.nc(ref)} ~ 가공 목표점)가 규격 최소 ${Fmt.fixed3(pipe.needLen)} mm" +
            "(${needLenName(s)})보다 짧습니다 — 가공 목표점을 Z${Fmt.nc(floor((ref - pipe.needLen) * 1000 + 1e-6) / 1000)} 이하로 하거나 도면을 확인하세요. " +
            "G92 앞 M23(나사 끝 빼기)을 쓰면 끝의 빼기 길이(기계 설정)만큼 완전 나사가 더 짧으니 그만큼 더 깊게 잡으세요."
    } else {
        "나사 길이(기준면 ~ 가공 목표점)가 규격의 최소 나사 길이보다 짧습니다. 가공 목표점을 확인하세요."
    }
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
    G76Warning.START_NEAR -> {
        val zs = s.num(ThreadField.Z_START)?.let { " Z${Fmt.nc(it)}" } ?: ""
        val ref = s.num(ThreadField.Z_REF)?.takeIf { ThreadField.Z_REF !in s.bad() } ?: 0.0
        val two = s.pipe?.let { Fmt.fixed3(2 * it.pitch) } ?: ""
        "Z축 시작점$zs 이 기준면 Z${Fmt.nc(ref)} 에서 2 × 피치($two mm) 안입니다 — 급송 절입이 끝면에 가깝고, 나사 시작의 가속 구간(서보 지연 — 회전수가 높을수록 김)에 " +
            "끝면 쪽 산${if (s.inner) "(암나사는 입구 면이 게이지 면)" else ""}이 들어가 리드가 틀어질 수 있습니다. Z축 시작점을 기준면 + $two 이상으로 하세요."
    }
}

/** 규격 최소 나사 길이의 이름(SHORT_THREAD 글 · 게이지 표): 숫나사 PT a + f · NPT L2, 암나사 PT l · NPT L1 + L3, 게이지 면을 옮겼으면 + 옮김 */
internal fun needLenName(s: ThreadState): String {
    val base = when (s.family.pipeKind) {
        PipeKind.PT -> if (s.inner) "l" else "a + f"
        PipeKind.NPT -> if (s.inner) "L1 + L3" else "L2"
        null -> ""
    }
    val shift = s.num(ThreadField.GAUGE_SHIFT)?.takeIf { it != 0.0 && ThreadField.GAUGE_SHIFT !in s.bad() }
    return if (shift == null) base else "$base + 게이지 면 옮김 ${plain(shift)}산"
}

/** BAD_INPUT 글: 틀린 칸마다 범위. 관용 테이퍼에서 칸은 맞는데 멈췄으면 숫나사 끝 골 X ≤ 0(끝 Z 가 기준면에서 너무 멂) */
private fun badText(s: ThreadState): String {
    val tol = s.pipe?.let { if (s.inner) it.tolTurnsInner else it.tolTurns }
    val parts = s.bad().map { f ->
        when (f) {
            ThreadField.Z_START, ThreadField.Z_END, ThreadField.Z_REF -> "${f.label} ±${MAX_MM.toInt()} mm 안"
            ThreadField.RPM -> "회전수 1 ~ 100000"
            ThreadField.TARGET -> "공차 목표 0 ~ 100 %"
            ThreadField.FINISH_COUNT -> "정삭 횟수 1 ~ 99 (정수)"
            ThreadField.FIRST_CUT -> "첫 절입 0.001 mm 이상 (μm 단위로 지령)"
            ThreadField.GAUGE_SHIFT -> "게이지 면 옮김 ±${tol?.let(::turnsText) ?: ""} 산 안 (게이지 공차)"
            ThreadField.TIP_R, ThreadField.MIN_CUT, ThreadField.ALLOW, ThreadField.X_CLEAR -> "${f.label} 0 이상"
        }
    }
    return when {
        parts.isNotEmpty() -> "넣은 값이 범위 밖입니다 — ${parts.joinToString(" · ")}."
        s.isPipe -> "숫나사 끝 골 X 가 0 이하입니다 — 가공 목표점이 기준면에서 너무 멉니다. 기준면 Z 와 가공 목표점을 확인하세요."
        else -> "넣은 값이 범위 밖입니다."
    }
}

/** 인서트 열쇠(저장) — 출처 · 이름(한 출처 안에서 이름이 겹치지 않는다) */
internal fun insertKey(i: ThreadInsert): String = "${i.sourceId}|${i.designation}"

/** 인서트 회사 이름(짧게): "Vargus (VARDEX)" → "VARDEX" */
internal fun insertMaker(i: ThreadInsert): String = i.maker.substringAfter('(', i.maker).removeSuffix(")")

/** 인서트 칸 · 시트 줄 글: "3ER20UN · VARDEX" · "16 ER 20 UN · Carmex" */
internal fun insertText(i: ThreadInsert): String = "${i.designation} · ${insertMaker(i)}"

/** 인서트 출처 글: 카탈로그 이름 + 쪽 */
internal fun insertSource(i: ThreadInsert): String {
    val title = when (i.sourceId) {
        "VDX_TT" -> "VARDEX Thread Turning 카탈로그"
        "CMX_TT" -> "Carmex Thread Turning 카탈로그"
        else -> i.sourceId
    }
    return "$title ${i.page}쪽"
}

/** 카탈로그 절삭 속도 줄 열쇠(저장) — 출처 · 원문 재질(사다리꼴 줄을 뺀 18줄 안에서 겹치지 않는다) */
internal fun dieSpeedKey(h: DieSpeedHit): String = "${h.speed.sourceId}|${h.speed.material}"

/** 숫자 칸 키패드 안내 줄 — 다이스 헤드 화면. 끝면 Z 는 관용 테이퍼의 기준면 Z 칸과 같은 값(공작물 끝면) */
internal fun dieFieldHint(f: ThreadField): String = when (f) {
    ThreadField.Z_START -> "다이스 G1 시작 Z — 공작물 끝면 밖 공기 중 (예: 끝면 + 5). 진입 Z20 이하"
    ThreadField.RPM -> "주축 회전수 직접 — 넣으면 다이스 조건의 Vc 대신 이 값 (비우면 Vc 로 계산)"
    ThreadField.Z_END -> "다이스 G1 끝 Z — 여기서 멈춰(끝 멈춤) 머리가 열립니다. 척 쪽은 − (예: −20)"
    ThreadField.Z_REF -> "공작물 끝면 Z — 체이서가 물리기 시작하는 곳. 끝 당김 길이 · 완전 나사를 여기부터 셈 · 비우면 0"
    else -> fieldHint(f, inner = false)
}

/** 숫자 칸 키패드 안내 줄. pipe = 관용 테이퍼 화면(G92 테이퍼만 — G76 낱말 이야기를 빼고, 시작 X 는 끝 Z 지름에서) */
internal fun fieldHint(f: ThreadField, inner: Boolean, pipe: Boolean = false): String = when (f) {
    ThreadField.Z_START -> if (pipe) {
        "사이클 시작 Z — 기준면(공작물 끝면) 밖 공기 중 (예: 기준면 + 5). 테이퍼 I 는 시작 Z ~ 끝 Z 길이의 1/32"
    } else {
        "사이클 시작 Z — 나사 앞 공기 중 (예: 5). 처음 몇 mm 는 리드가 맞지 않아 나사보다 앞에서 시작"
    }
    ThreadField.RPM -> "주축 회전수 — 넣으면 첫 줄 G97 S · 분당 이송 · Vc 를 보입니다 (비우면 안 씀)"
    ThreadField.Z_END -> "나사 끝 Z — 척 쪽은 − (예: −25)"
    ThreadField.TARGET -> "공차역 안 목표 % — 0 = 최대 실체(${if (inner) "암나사 작은 쪽" else "숫나사 큰 쪽"}), 100 = 최소 실체 · 비우면 80"
    ThreadField.TIP_R -> "인서트 날 끝 R (카탈로그 값) · 비우면 규격 기본 깊이와 맞는 R (${if (inner) "H/8" else "H/6"})"
    ThreadField.FINISH_COUNT -> if (pipe) "마지막 정삭 반복 횟수 (1~99) — 끝 X 줄 수 · 비우면 2" else "마지막 정삭 반복 횟수 (1~99) — G76 첫 줄 P 앞 두 자리 · 비우면 2"
    ThreadField.MIN_CUT -> if (pipe) "한 회 최소 절입 (반경) · 비우면 0.035" else "한 회 최소 절입 (반경) — G76 첫 줄 Q (μm) · 비우면 0.035"
    ThreadField.ALLOW -> if (pipe) "마지막 정삭 여유 (반경) · 비우면 0.02" else "마지막 정삭 여유 (반경) — G76 첫 줄 R · 비우면 0.02"
    ThreadField.FIRST_CUT -> if (pipe) {
        "첫 절입 깊이 (반경) — n 회 깊이 = Δd × √n · G92 X 줄 차례 · 분석표 횟수를 보고 고침"
    } else {
        "첫 절입 깊이 (반경) — G76 둘째 줄 Q (μm) · n 회 깊이 = Δd × √n · 분석표 횟수를 보고 고침"
    }
    ThreadField.X_CLEAR -> if (pipe) {
        "사이클 시작 X 여유 (반경) — 끝 Z 의 ${if (inner) "안지름 − 2 ×" else "바깥지름 + 2 ×"} (나사 안에서 ${if (inner) "가장 가는" else "가장 굵은"} 곳) · 비우면 1"
    } else {
        "사이클 시작 X 여유 (반경) — ${if (inner) "안지름 최소 − 2 ×" else "바깥지름 최대 + 2 ×"} · 비우면 1"
    }
    ThreadField.Z_REF ->
        "공작물 끝면 Z — ${if (inner) "암나사 입구 면 = 게이지 면" else "숫나사 관 끝(작은 쪽). 게이지 면은 여기서 게이지 길이만큼 척 쪽"} · 비우면 0"
    ThreadField.GAUGE_SHIFT ->
        "게이지 면 옮김 (산) — + 는 조립 때 더 깊이 들어가는 쪽(${if (inner) "암나사 굵게" else "숫나사 가늘게"}, 한 산 = 지름 P/16) · 게이지 공차 안 · 비우면 0"
}
