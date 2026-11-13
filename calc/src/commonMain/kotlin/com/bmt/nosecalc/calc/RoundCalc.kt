package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 라운드 경고. 화면이 한국어 문구를 고른다.
 * 위 여섯 개는 계산을 멈춘다 — [RoundResult.solution] 이 null 이고 경고는 그 하나뿐.
 * 아래 여섯은 숫자와 함께 나온다(뒤 넷은 기계 보정 프로그램 [RoundCalc.machine] 만).
 */
enum class RoundWarning {
    /** 값이 모자람. 비울 수 있는 값(구 중심이 축 위라 나머지로 정해지는 것)은 하나까지 */
    MISSING_INPUT,

    /** 길이·지름·R ≤ 0, 모서리 R·노즈R·Z 여유 < 0, 노즈R·Z 여유가 둘 다 0(진입 거리 없음), 뒤 요소 길이 ≤ 0, 팁 0~9 밖, X < 0, 숫자가 아님, 좌표·치수가 ±[MAX_MM] 밖 */
    BAD_INPUT,

    /** 비울 수 있는 값까지 다 넣었는데 서로 0.001 mm 넘게 안 맞다 */
    CONTRADICTION,

    /** 치수로 그 TYPE 모양이 안 나온다(예: L ≥ 2R, R < D/2, D1 ≥ D2, 돔이 플랜지보다 큼, 세 점이 한 줄·겹침·시작과 끝 Z 가 같음) */
    SHAPE_MISMATCH,

    /** 모서리 R 이 너무 커서 이웃 요소를 다 먹거나 닿을 자리가 없다 */
    CORNER_R_TOO_BIG,

    /**
     * 노즈R 에 비해 요소가 짧거나 좁아 공구가 닿지 못한다 — 도면 형상이 안 나온다. 내경은 구멍이 좁아(지름 < 2r) 노즈 중심이 축을 넘을 때도,
     * 외경은 노즈 중심이 축을 넘어 부품 반대쪽(축 거울)을 팔 때도(축 가까이에서 끝나는 3점호 등)
     */
    NOSE_UNREACHABLE,

    /**
     * 오목 원호 R − 노즈R ≤ 0. 보정 원호가 없어진다(arcs 의 progR = null).
     * R = r 이면 공구가 꼭 맞아 정확하지만, R < r 이면 그 자리에 도면보다 큰 R(= r)이 남는다.
     */
    CONCAVE_R_LE_NOSE,

    /** 형상 일부가 바이트 방향과 반대다(노즈 접점이 팁 쪽 사분면 밖, 예: 공의 뒤쪽·왼쪽 면) — 끝날·여유각 확인 */
    AGAINST_TOOL,

    /**
     * 기계 보정 프로그램: 막힌 끝 직선(척 · 구멍 바닥 쪽)이 노즈R 쯤보다 짧은데 그 요소를 뺄 수 없다 — 앞 요소의 노즈 길 위에 벽을 따라 빼는 줄의 교점이
     * 남지 않는다. 그래서 끝 접선으로 곧게 빼 노즈가 끝 좌표 너머(벽 속)로 간다
     */
    SHORT_END_PAST,

    /**
     * 기계 보정 프로그램: 진입 길(Z20. 자리 → 진입점)을 도면에 닿지 않게 짜지 못했다 — 곧장 내려간 길의 노즈가 도면에 닿거나 판다(또는 직각 진입의 두 축 G0 이
     * 급송 속도 비에 따라 꺾여 닿는다). 진입 줄을 손으로 고칠 것
     */
    ENTRY_NOT_FOUND,

    /**
     * 기계 보정 프로그램: 빼는 길(빼는 줄 → G40 → G0Z200.)을 G40 자리 규칙 · 물러남 띠 · 빼는 줄 노즈 길(도면에서 r) · 바깥 90° 이하 꺾임에 맞게 짜지 못했다
     * (노즈에 비해 좁은 구멍 · 깊은 언더컷 등). 팁 번호 · 보정 형식에 따라 빼는 줄 · G40 · 물러남이 도면에 닿을 수 있다 — 빼는 줄을 손으로 고칠 것
     */
    EXIT_NOT_FOUND,

    /**
     * 기계 보정 프로그램: 막힌 끝 직선이 노즈R 쯤보다 짧아 그 요소(와 앞 모서리 R)를 빼고 앞 요소 끝에서 벽을 따라 뺐다 — 노즈가 벽 너머로 가지 않는 대신
     * 뺀 요소는 깎이지 않는다(벽 앞에 살이 남음)
     */
    SHORT_END_SKIPPED,
}

/** TYPE 2 의 L2 를 어디서 재나. A = 왼쪽 이음점 ~ 오른쪽 끝(= L1 + B), B = 두 이음점 사이 */
enum class L2Ref { A, B }

/**
 * 라운드 TYPE 1~6(원본 04.jpg). 외경, 척 쪽(−Z)으로 깎음(G42). X 는 직경값, Z0 = 부품 오른쪽 끝.
 * 내경([inner], TYPE 2~6)은 같은 치수의 구멍: 보링바(팁 2 · G41)로 입구에서 안쪽(Z−)으로 깎고 Z0 = 구멍 입구 끝면.
 * 주 원호(Main R)는 중심이 축 위인 구다. 그래서 TYPE 2~5 에서는 R·D·L 가운데 하나가 나머지로 정해진다 —
 * 그 묶음([mainR] 과 TYPE 2 의 d1·l2, TYPE 3·4 의 d1·l1, TYPE 5 의 d2·d1·l2)은 하나를 비워도 되고, 다 넣으면 맞는지 본다.
 * TYPE 1·3·4 의 Corner R·D·L 은 [cornerR1]·[d1]·[l1] 에 넣는다.
 */
data class RoundInput(
    val type: Int = 1,
    val mainR: Double? = null,
    /** Corner R(1). TYPE 1 은 양쪽, TYPE 2 는 두 이음점 모두 이 값 */
    val cornerR1: Double? = null,
    /** Corner R(2) — TYPE 5·6 */
    val cornerR2: Double? = null,
    /** D1(TYPE 2·5·6) 또는 D(TYPE 3·4) */
    val d1: Double? = null,
    /** D2 — TYPE 5·6 */
    val d2: Double? = null,
    /** L1(TYPE 2·5·6) 또는 L(TYPE 1·3·4) */
    val l1: Double? = null,
    /** L2 — TYPE 2·5·6 */
    val l2: Double? = null,
    val l2Ref: L2Ref = L2Ref.B,
    val noseR: Double = 0.4,
    /** 가상 인선 번호. null = 표준(외경 3, 내경 2) */
    val tip: Int? = null,
    /** 진입 여유: 첫 보정점에서 Z+ 로 이만큼(노즈R 보다 작으면 노즈R — 테이퍼와 같다) */
    val zClear: Double = 1.0,
    /**
     * TYPE 2~6 의 끝 직선 길이. 외경 = 마지막 원통·축 길이(그림과 마지막 NC 줄만). 내경 3~6 = 입구 구멍 깊이(Z0 ~ 첫 이론 꼭짓점, 실제 치수).
     * 내경 2 = 공 뒤 구멍 길이(그림과 마지막 NC 줄만)
     */
    val legLen: Double = 5.0,
    /** 내경(보링, 팁 2 · G41). TYPE 2~6 만 */
    val inner: Boolean = false,
    /** 내경 5 · 6 만: 바닥 면 없이 D1 구멍이 뚫림 */
    val through: Boolean = false,
    /**
     * 가공 방향 뒤집기(사용자 요청 2026-11-10 — 라운드에도 반대 보정 쪽 프로그램). 같은 형상 · 같은 치수를 같은 바이트(팁 그대로)로 **반대 끝부터** 깎는다:
     * 외경은 척 쪽 끝 직선에서 앞(Z+)으로 — G41, 내경은 구멍 바닥에서 입구로 — G42. 테이퍼의 [TaperInput.flip] 과 같은 뜻.
     * 보정점은 공구 쪽 · 팁 벡터로만 정해져 안 뒤집은 것과 같은 점(순서만 반대)이다 — 다만 척 · 구멍 바닥 쪽 끝 직선에서 시작할 때는 노즈가 그 시작 좌표를
     * 넘지 않게 닿기만 한다([RoundCalc] 의 '뒤집은 시작')
     */
    val flip: Boolean = false,
)

/** 수동 모드(CAD 3점호): 원호 위 세 점(시작 → 가운데 → 끝). 점 순서가 가공 방향이다. X 는 직경값 */
data class Arc3Input(
    val inner: Boolean = false,
    val noseR: Double = 0.4,
    /** null = 표준(외경 3·역 4, 내경 2·역 1). 역방향 = 끝 Z 가 시작보다 큼 */
    val tip: Int? = null,
    val x1: Double? = null,
    val z1: Double? = null,
    val x2: Double? = null,
    val z2: Double? = null,
    val x3: Double? = null,
    val z3: Double? = null,
    /**
     * 진입 여유: 첫 보정점에서 Z 로 이만큼 바깥(역방향은 −Z 쪽). 그쪽으로 물러나면 노즈 원이 시작점 옆을 파는 원호
     * (시작 접선이 재료로 가파르게 내려가는 오목 홈 등)는 시작 접선을 거꾸로 따라 이만큼. 노즈R 보다 작으면 노즈R
     */
    val zClear: Double = 1.0,
    /**
     * 가공 방향 뒤집기: 같은 원호를 같은 바이트(넣은 점 순서의 표준 팁 그대로)로 끝점(P3)부터 시작점(P1)으로 깎는다 — 보정 쪽이 반대.
     * 뜻은 [RoundInput.flip] 과 같다
     */
    val flip: Boolean = false,
)

/** 도면 원호 하나. [index] = drawing.segs 번호, [progR] = 보정(합성) R — 볼록 R + r, 오목 R − r, 없어졌으면 null */
data class RoundArc(val index: Int, val cw: Boolean, val center: Pt, val r: Double, val progR: Double?)

/**
 * NC 줄 하나가 어디서 나왔나. [element] −2 = G00 진입, −1 = G01 시작점, k = drawing.segs[k]. 90° 넘게 나눈 원호는 [pieces] 조각 가운데 [piece] 번(0부터).
 * 기계 보정 프로그램([RoundCalc.machine])은 −2 = 보정 혼자 줄 · 진입 G0 줄들, −1 = 첫 깎는 줄(G1 F.1), drawing.segs.size = 빼는 줄 · G40 · G0Z200.
 */
data class NcRef(val element: Int, val piece: Int, val pieces: Int)

data class RoundSolution(
    /** TYPE 모드: 빈 값을 채운 입력. 3점 모드는 null */
    val filled: RoundInput?,
    /** 노드 표: 요소 끝점마다 도면점과 보정점(가상 인선 = 프로그램 좌표). 가공 순서 */
    val nodes: List<CompPoint>,
    /** 원호 표(G02 = cw) */
    val arcs: List<RoundArc>,
    val tip: Int,
    /** G41/G42 로 프로그램할 때의 보정 쪽 */
    val side: Side,
    /** G00 진입점(프로그램 좌표) */
    val entry: Pt,
    /** G00 진입 → G01 시작점 → G01/G02/G03 보정 경로. R 지령, 바뀐 축만 */
    val nc: List<String>,
    /** 도면 윤곽(접점 모드), 가공 순서 */
    val drawing: Contour,
    /** 노즈 중심 궤적(합성 R). segs[k] ↔ drawing.segs[k] */
    val center: Contour,
    /** 가상 인선 경로 = 프로그램 경로. segs[k] ↔ drawing.segs[k] */
    val program: Contour,
    /** [nc] 줄마다 하나 */
    val ncRefs: List<NcRef>,
    /**
     * 기계 보정 프로그램([RoundCalc.machine])의 진입 명령점: `G0X…Z20.` 자리 → (바깥에서 X 를 맞추면 그 자리) → 진입점([entry]) — 그 뒤 `G1` 로 첫 점.
     * 직접 보정 풀이는 빈 목록 — 다만 뒤집은([flip]) 직접 보정은 척 쪽 · 구멍 안에서 시작하므로 진입 G00 앞 접근 점(앞 끝면 바깥에서 X 를 맞추고 Z 로)과
     * 진입점이 든다
     */
    val approach: List<Pt> = emptyList(),
    /** 가공 방향을 뒤집은 풀이인가([RoundInput.flip] · [Arc3Input.flip]) — 도면 · 노드 · NC 가 모두 뒤집은 가공 순서다 */
    val flip: Boolean = false,
    /**
     * 기계 보정 프로그램에서 진입점([entry]) 뒤 · 형상의 첫 점 앞에 이송(G1)으로 거치는 명령점들 — 뒤집은 시작만 있다(막힌 시작: 벽 쪽으로 간 자리,
     * 축에서 시작: 축 너머 첫 깎는 점, 3점호: 끝점 너머 자리 위 · 접선 연장선 위). 그림이 진입 길을 NC 그대로 그리는 데 쓴다
     */
    val leadIn: List<Pt> = emptyList(),
) {
    /**
     * element 의 NC 줄 번호(0부터). p = 이 풀이의 프로그램 좌표 점(나눈 원호는 각 비율로 조각을 고름). 줄이 없으면 그 앞 줄
     */
    fun lineOf(element: Int, p: Pt): Int {
        val lines = ncRefs.indices.filter { ncRefs[it].element == element }
        if (lines.isEmpty()) return max(0, ncRefs.indexOfLast { it.element < element })
        val a = program.segs.getOrNull(element) as? Arc
        val pieces = ncRefs[lines.first()].pieces
        if (a == null || pieces == 1) return lines.first()
        // 원호 밖(반올림)이면 가까운 끝으로
        val sweep = a.sweepRad()
        val along = wrap2Pi((if (a.cw) -1 else 1) * (angleOf(p - a.center) - angleOf(a.start - a.center)))
        val frac = if (along <= sweep) along / sweep else if (along > PI + sweep / 2) 0.0 else 1.0
        val piece = min(pieces - 1, floor(frac * pieces).toInt())
        return lines.lastOrNull { ncRefs[it].piece <= piece } ?: lines.first()
    }
}

data class RoundResult(val solution: RoundSolution?, val warnings: List<RoundWarning>)

/**
 * 라운드R 계산. 도면 윤곽은 직선·주 원호의 이론 꼭짓점에 모서리 R 을 접하게 끼워(접점 모드) 만들고,
 * 보정은 [NoseComp] 의 일반 해법(원호는 같은 중심, 볼록 R + r · 오목 R − r — 합성 R)에 맡긴다.
 */
object RoundCalc {
    /** 다 넣은 값끼리 이만큼(mm)까지 달라도 받아 준다 — 도면 치수를 0.001 로 반올림해 넣어도 되게 */
    private const val LEN_TOL = 1e-3

    /** [againstTool] 의 사분면 경계 여유(내적·rad, 각으로 0.06~0.08°) */
    private const val QUADRANT_TOL = 1e-3

    /** 기계 보정 프로그램이 축에서 끝나는 원호(내경 돔 · 공 바닥) 뒤 노즈 중심을 축 너머로 지나게 하는 길이(mm) */
    const val AXIS_PAST = 0.01

    fun calc(i: RoundInput): RoundResult = type(i, null)

    /**
     * 기계 보정(G41/G42) 프로그램 — 도면(접점) 좌표 풀이. 윤곽 · 노드 · 원호 · 형상 줄은 노즈R 0 으로 푼 것(`calc(i.copy(noseR = 0.0))`)과 같고,
     * NC 는 회사 꼴(계획 단계 4, 사용자 결정 2026-10-29): `G42` / `G0X…Z20.` / `Z…` / `G1 첫 점 F.1` / 형상 / 빼는 줄 `F.3` / `G40` / `G0Z200.`(`machineFrame`).
     * 보정 줄이 이동 없는 혼자 줄이라 깎는 줄의 노즈는 보정 시작 · 취소 형식과 팁 번호에 상관없다(노즈R 은 공기 이동을 재는 데만 쓴다).
     * 계산을 멈추는 경고는 [calc] 와 같다 — 직접 보정이 멈추면(노즈가 닿지 못함 · 축을 넘음 등) 그 결과를 그대로 낸다: 같은 노즈라 기계 보정도 제어기가
     * 파거나 알람이다(10-28 검증 — 홈보다 큰 노즈에 글을 내 시작 블록 노즈가 맞은편 벽을 0.28 mm 팠다)
     */
    fun machine(i: RoundInput): RoundResult = calc(i).let { if (it.solution == null) it else type(i, i.noseR) }

    /** 3점호의 기계 보정 프로그램([machine] 과 같은 규칙) */
    fun machine(i: Arc3Input): RoundResult = arc3(i).let { if (it.solution == null) it else arc(i, i.noseR) }

    /** [machineR] = 기계 보정 프로그램의 노즈R(도면 좌표로 풀고 G00 만 띄움), null = 직접 보정 풀이 */
    private fun type(i: RoundInput, machineR: Double?): RoundResult = guarded {
        require(i.type in 1..6) { "TYPE 은 1~6: ${i.type}" }
        require(!(i.inner && i.type == 1)) { "내경은 TYPE 2~6" }
        require(!i.through || (i.inner && i.type in 5..6)) { "관통은 내경 TYPE 5·6 만" }
        checkTool(i.noseR, i.tip, i.zClear)
        if (!(i.legLen > 0 && i.legLen.isFinite())) stop(RoundWarning.BAD_INPUT)
        if (tooBig(i.mainR, i.cornerR1, i.cornerR2, i.d1, i.d2, i.l1, i.l2, i.legLen)) stop(RoundWarning.BAD_INPUT)
        val s = when (i.type) {
            1 -> type1(i)
            2 -> type2(i)
            3, 4 -> type34(i)
            5 -> type5(i)
            else -> type6(i)
        }
        val segs = Corners.insert(s.segs, s.corners)?.segs ?: stop(RoundWarning.CORNER_R_TOO_BIG)
        val base = if (i.inner) innerOf(segs, i.type, i.through) else segs
        // 뒤집기: 같은 윤곽을 거꾸로(원호 방향 반대) — 보정 쪽이 반대, 바이트(팁)는 그대로
        val drawing = Contour(if (i.flip) reversed(base) else base)
        // Z 여유 0 이어도 진입점은 노즈R 만큼 띄운다(테이퍼와 같다)
        val tip = i.tip ?: NoseComp.standardTip(i.inner, false)
        finish(drawing, if (machineR == null) i.noseR else 0.0, Side.of(i.inner, i.flip), tip, Pt(max(i.zClear, i.noseR), 0.0), s.filled, i.inner, machineR, i.flip, typed = true)
    }

    /** 윤곽을 거꾸로(가공 방향 뒤집기): 요소 순서 · 양 끝을 바꾸고 원호는 도는 방향도 */
    private fun reversed(segs: List<Seg>): List<Seg> = segs.asReversed().map {
        when (it) {
            is Line -> Line(it.end, it.start)
            is Arc -> Arc(it.end, it.start, it.center, !it.cw)
        }
    }

    /**
     * 외경 도면(모서리 R 까지 끼운 것) → 같은 치수의 구멍. TYPE 2 는 끝 면만 빼면 구멍 입구 (0, D1/2) 에서 시작한다.
     * 3~6 은 외경 끝(깊이 K)이 구멍 입구 Z0 이 되게 z′ = −z − K 로 뒤집고 거꾸로 간다 — 외경의 끝 면 · 돔 끝이 구멍 바닥.
     * 뒤집기와 거꾸로 가기가 각각 원호 방향을 바꿔 cw 는 그대로다. 재료가 반대쪽이라 모서리 R 의 볼록 · 오목은 저절로 바뀐다.
     * 관통은 바닥 면을 뺀다
     */
    private fun innerOf(segs: List<Seg>, type: Int, through: Boolean): List<Seg> {
        if (type == 2) return segs.drop(1)
        val k = -segs.last().end.z
        fun m(p: Pt) = Pt(-p.z - k, p.x)
        val out = segs.asReversed().map {
            when (it) {
                is Line -> Line(m(it.end), m(it.start))
                is Arc -> Arc(m(it.end), m(it.start), m(it.center), it.cw)
            }
        }
        return if (through) out.dropLast(1) else out
    }

    fun arc3(i: Arc3Input): RoundResult = arc(i, null)

    private fun arc(i: Arc3Input, machineR: Double?): RoundResult = guarded {
        val v = listOf(i.x1, i.z1, i.x2, i.z2, i.x3, i.z3)
        if (v.any { it == null }) stop(RoundWarning.MISSING_INPUT)
        if (v.any { !it!!.isFinite() } || listOf(i.x1!!, i.x2!!, i.x3!!).any { it < 0 } || tooBig(*v.toTypedArray())) {
            stop(RoundWarning.BAD_INPUT)
        }
        checkTool(i.noseR, i.tip, i.zClear)
        val p1 = Pt.ofDia(i.z1!!, i.x1)
        val p2 = Pt.ofDia(i.z2!!, i.x2)
        val p3 = Pt.ofDia(i.z3!!, i.x3)
        if ((p2 - p1).length <= EPS || (p3 - p2).length <= EPS || (p3 - p1).length <= EPS) stop(RoundWarning.SHAPE_MISMATCH)
        // 중심 = 두 현의 수직이등분선 교점. 평행이면 세 점이 한 줄
        val c = Intersect.lineLine((p1 + p2) * 0.5, (p2 - p1).left(), (p2 + p3) * 0.5, (p3 - p2).left())
            ?: stop(RoundWarning.SHAPE_MISMATCH)
        // 거의 한 줄이면 중심이 아주 멀리 간다(R 이 한도를 넘으면 한 줄로 본다)
        if (abs(p3.z - p1.z) <= EPS || tooBig(c.z, c.x)) stop(RoundWarning.SHAPE_MISMATCH)
        // 바이트(표준 팁)는 넣은 점 순서를 따르고, 뒤집으면([Arc3Input.flip]) 같은 바이트로 끝점부터 깎는다 — 보정 쪽 · 진입은 실제 진행([goesUp])으로
        val reverse = p3.z > p1.z
        val goesUp = reverse != i.flip
        val cwIn = ((p2 - p1) cross (p3 - p2)) < 0
        val arc = if (i.flip) Arc(p3, p1, c, !cwIn) else Arc(p1, p3, c, cwIn)
        val side = Side.of(i.inner, goesUp)
        // 원호 하나뿐이라 오목 R < r 이면 남는 요소가 없다
        // (기계 보정 프로그램은 도면 좌표라 노즈R 0 으로 푼다)
        val rg = if (machineR == null) i.noseR else 0.0
        if ((side == Side.G41) != arc.cw && arc.radius < rg - EPS) stop(RoundWarning.NOSE_UNREACHABLE)
        val tip = i.tip ?: NoseComp.standardTip(i.inner, reverse)
        // 진입: 첫 보정점에서 Z 로 zClear 바깥. 그쪽이 공구 쪽 법선과 반대면(시작 접선이 재료로 가파르게 내려가는 오목 홈 등)
        // Z 로 물러난 노즈 원이 시작점 옆 재료를 판다 → 그때는 시작 접선을 거꾸로 따라 zClear 만큼(노즈R 보다 작으면 노즈R)
        val clear = max(i.zClear, i.noseR)
        val away = Pt(if (goesUp) -1.0 else 1.0, 0.0)
        val radial = (arc.start - c).unit()
        val toTool = if ((side == Side.G41) == arc.cw) radial else radial * -1.0
        val back = radial.left() * (if (arc.cw) 1.0 else -1.0)
        val entryShift = if ((away dot toTool) >= 0) away * clear else back * clear
        // 뒤집어 끝점(P3)에서 Z+ 로 깎으면 P3 너머가 척 · 구멍 안쪽이다 — 급송은 P3 너머로 가지 않고 공구 쪽에서 들어온다([finish] 의 '뒤집은 시작')
        finish(Contour(listOf(arc)), rg, side, tip, entryShift, null, i.inner, machineR, i.flip, squareStart = i.flip && goesUp)
    }

    /** 이론 윤곽(꼭짓점 그대로) + 꼭짓점마다 모서리 R(0 = 그대로). corners[k] = segs[k] 와 segs[k+1] 사이 */
    private class Shape(val filled: RoundInput, val segs: List<Seg>, val corners: List<Double>)

    /** 술통: 구 중심 (−L/2, 0), 양끝 면 Z0·Z−L */
    private fun type1(i: RoundInput): Shape {
        need(i.mainR, i.l1, i.cornerR1)
        positive(i.mainR, i.l1)
        cornerOk(i.cornerR1)
        val bigR = i.mainR!!
        val l = i.l1!!
        if (l >= 2 * bigR) stop(RoundWarning.SHAPE_MISMATCH)
        val h = sqrt(bigR * bigR - l * l / 4)
        val c = i.cornerR1!!
        return Shape(
            i,
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, h)),
                Arc(Pt(0.0, h), Pt(-l, h), Pt(-l / 2, 0.0), cw = false),
                Line(Pt(-l, h), Pt(-l, 0.0)),
            ),
            listOf(c, c),
        )
    }

    /** 아령: 축 끝 면 → 축 L1 → 공(이음점 사이 2s, 구 중심 (−L1 − s, 0)) → 축. R² = (D1/2)² + s² */
    private fun type2(i: RoundInput): Shape {
        need(i.l1, i.cornerR1)
        oneBlank(i.mainR, i.d1, i.l2)
        positive(i.mainR, i.d1, i.l1, i.l2)
        cornerOk(i.cornerR1)
        val l1 = i.l1!!
        val toB = if (i.l2Ref == L2Ref.A) l1 else 0.0
        val a0 = i.d1?.let { it / 2 }
        val s0 = i.l2?.let { (it - toB) / 2 }
        if (s0 != null && s0 <= 0) stop(RoundWarning.SHAPE_MISMATCH)
        val bigR: Double
        val a: Double
        val s: Double
        when {
            i.mainR == null -> {
                a = a0!!
                s = s0!!
                bigR = hypot(a, s)
            }
            a0 == null -> {
                bigR = i.mainR
                s = s0!!
                if (bigR <= s) stop(RoundWarning.SHAPE_MISMATCH)
                a = sqrt(bigR * bigR - s * s)
            }
            else -> {
                bigR = i.mainR
                a = a0
                if (bigR <= a) stop(RoundWarning.SHAPE_MISMATCH)
                s = sqrt(bigR * bigR - a * a)
                if (s0 != null && 2 * abs(s - s0) > LEN_TOL) stop(RoundWarning.CONTRADICTION)
            }
        }
        val zc = -l1 - s
        val zl = zc - s
        val c = i.cornerR1!!
        return Shape(
            i.copy(mainR = bigR, d1 = 2 * a, l2 = 2 * s + toB),
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, a)),
                Line(Pt(0.0, a), Pt(-l1, a)),
                Arc(Pt(-l1, a), Pt(zl, a), Pt(zc, 0.0), cw = false),
                Line(Pt(zl, a), Pt(zl - i.legLen, a)),
            ),
            listOf(0.0, c, c),
        )
    }

    /**
     * 돔(3)·공(4): 끝 (0, 0), 구 중심 (−R, 0), 원통·축과의 이음점 (−L, D/2). (L − R)² + (D/2)² = R² 이고
     * 돔은 이음점이 중심 오른쪽(L ≤ R), 공은 왼쪽(L ≥ R).
     */
    private fun type34(i: RoundInput): Shape {
        need(i.cornerR1)
        oneBlank(i.mainR, i.d1, i.l1)
        positive(i.mainR, i.d1, i.l1)
        cornerOk(i.cornerR1)
        val ball = i.type == 4
        val h0 = i.d1?.let { it / 2 }
        val bigR: Double
        val h: Double
        val l: Double
        when {
            i.mainR == null -> {
                h = h0!!
                l = i.l1!!
                if (if (ball) l < h else l > h) stop(RoundWarning.SHAPE_MISMATCH)
                bigR = (l * l + h * h) / (2 * l)
            }
            h0 == null -> {
                bigR = i.mainR
                l = i.l1!!
                if (l >= 2 * bigR || (if (ball) l < bigR else l > bigR)) stop(RoundWarning.SHAPE_MISMATCH)
                h = sqrt(l * (2 * bigR - l))
            }
            else -> {
                bigR = i.mainR
                h = h0
                if (bigR < h) stop(RoundWarning.SHAPE_MISMATCH)
                l = bigR + (if (ball) 1 else -1) * sqrt(bigR * bigR - h * h)
                if (i.l1 != null && abs(l - i.l1) > LEN_TOL) stop(RoundWarning.CONTRADICTION)
            }
        }
        return Shape(
            i.copy(mainR = bigR, d1 = 2 * h, l1 = l),
            listOf(
                Arc(Pt(0.0, 0.0), Pt(-l, h), Pt(-bigR, 0.0), cw = false),
                Line(Pt(-l, h), Pt(-l - i.legLen, h)),
            ),
            listOf(i.cornerR1!!),
        )
    }

    /**
     * 큰 원통 D2 → 볼록 구면 → 작은 축 D1. 구 중심 (zc, 0) 에서 작은 축 이음점 (−L1, a) 는 zc + u,
     * 큰 원통 이음점 (−L1 − L2, b) 는 zc + v (u = √(R² − a²), v = √(R² − b²), 둘 다 중심 오른쪽) → L2 = u − v.
     */
    private fun type5(i: RoundInput): Shape {
        need(i.l1, i.cornerR1, i.cornerR2)
        oneBlank(i.mainR, i.d2, i.d1, i.l2)
        positive(i.mainR, i.d2, i.d1, i.l1, i.l2)
        cornerOk(i.cornerR1, i.cornerR2)
        val a0 = i.d1?.let { it / 2 }
        val b0 = i.d2?.let { it / 2 }
        if (a0 != null && b0 != null && a0 >= b0) stop(RoundWarning.SHAPE_MISMATCH)
        val bigR: Double
        val a: Double
        val b: Double
        val l2: Double
        when {
            i.mainR == null -> {
                a = a0!!
                b = b0!!
                l2 = i.l2!!
                val k = (b * b - a * a) / l2 // u + v (u² − v² = b² − a², u − v = L2)
                if (k < l2) stop(RoundWarning.SHAPE_MISMATCH) // v < 0
                val u = (k + l2) / 2
                bigR = sqrt(u * u + a * a)
            }
            a0 == null -> {
                bigR = i.mainR
                b = b0!!
                l2 = i.l2!!
                if (bigR < b) stop(RoundWarning.SHAPE_MISMATCH)
                val u = sqrt(bigR * bigR - b * b) + l2
                if (u >= bigR) stop(RoundWarning.SHAPE_MISMATCH)
                a = sqrt(bigR * bigR - u * u)
            }
            b0 == null -> {
                bigR = i.mainR
                a = a0
                l2 = i.l2!!
                if (bigR <= a) stop(RoundWarning.SHAPE_MISMATCH)
                val v = sqrt(bigR * bigR - a * a) - l2
                if (v < 0) stop(RoundWarning.SHAPE_MISMATCH)
                b = sqrt(bigR * bigR - v * v)
            }
            else -> {
                bigR = i.mainR
                a = a0
                b = b0
                if (bigR < b) stop(RoundWarning.SHAPE_MISMATCH)
                l2 = sqrt(bigR * bigR - a * a) - sqrt(bigR * bigR - b * b)
                if (i.l2 != null && abs(l2 - i.l2) > LEN_TOL) stop(RoundWarning.CONTRADICTION)
            }
        }
        val l1 = i.l1!!
        val zc = -l1 - sqrt(bigR * bigR - a * a)
        val z2 = -l1 - l2
        return Shape(
            i.copy(mainR = bigR, d1 = 2 * a, d2 = 2 * b, l2 = l2),
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, a)),
                Line(Pt(0.0, a), Pt(-l1, a)),
                Arc(Pt(-l1, a), Pt(z2, b), Pt(zc, 0.0), cw = false),
                Line(Pt(z2, b), Pt(z2 - i.legLen, b)),
            ),
            listOf(0.0, i.cornerR1!!, i.cornerR2!!),
        )
    }

    /**
     * 플랜지 D2 → 면 → 돔 → 작은 축 D1. 구 중심은 작은 축 이음점에서 u = √(R² − a²) 왼쪽, 플랜지 면 z = −L1 − L2.
     * 돔 밑동 높이 = 구가 면을 자르는 높이(입력이 아님). 남는 값이 없다.
     */
    private fun type6(i: RoundInput): Shape {
        need(i.mainR, i.d2, i.d1, i.l1, i.l2, i.cornerR1, i.cornerR2)
        positive(i.mainR, i.d2, i.d1, i.l1, i.l2)
        cornerOk(i.cornerR1, i.cornerR2)
        val bigR = i.mainR!!
        val a = i.d1!! / 2
        val b = i.d2!! / 2
        val l1 = i.l1!!
        if (a >= b || bigR <= a) stop(RoundWarning.SHAPE_MISMATCH)
        val zc = -l1 - sqrt(bigR * bigR - a * a)
        val zf = -l1 - i.l2!!
        val dz = zf - zc
        if (abs(dz) >= bigR) stop(RoundWarning.SHAPE_MISMATCH)
        val hf = sqrt(bigR * bigR - dz * dz)
        // 돔의 가장 높은 곳: 중심이 면 오른쪽이면 꼭대기(R), 아니면 면에서
        if ((if (dz <= 0) bigR else hf) >= b) stop(RoundWarning.SHAPE_MISMATCH)
        return Shape(
            i,
            listOf(
                Line(Pt(0.0, 0.0), Pt(0.0, a)),
                Line(Pt(0.0, a), Pt(-l1, a)),
                Arc(Pt(-l1, a), Pt(zf, hf), Pt(zc, 0.0), cw = false),
                Line(Pt(zf, hf), Pt(zf, b)),
                Line(Pt(zf, b), Pt(zf - i.legLen, b)),
            ),
            listOf(0.0, i.cornerR1!!, i.cornerR2!!, 0.0),
        )
    }

    /**
     * 보정·노드 표·NC. [NoseComp] 의 일반 해법 그대로. [machineR] 가 있으면 [r] = 0 인 도면 좌표 풀이의 G00 을 그 노즈R · 팁에 맞게 띄운다([machine]).
     * [flip] = 가공 방향을 뒤집은 풀이([drawing] · [side] 는 이미 뒤집은 것), [typed] = TYPE 1~6 윤곽(뒤집은 시작 규칙을 쓴다 — 3점호는 그 너머 윤곽을 몰라
     * 방향과 상관없는 제 규칙 그대로).
     *
     * 뒤집은 시작(TYPE) — 기본 방향의 끝 규칙을 시간을 거꾸로 돌린 것이다([machineFrame]):
     * - 척 · 구멍 바닥 쪽 끝 직선(외경 끝 직선 · 내경 공 뒤 구멍 · 관통 구멍)에서 Z+ 로 시작 = 막힌 시작. 넣은 시작 좌표 너머는 척 · 구멍 속이라 공구 쪽에서
     *   직각으로 들어가고, 노즈는 시작 좌표의 법선에 닿기만 한다(넘지 않는다 — calc-design '넣은 시작·끝점 너머로 넘지 않기'). 직접 보정의 첫 보정점도 그 자리
     *   (팁 3 이면 인선이 시작 좌표). 그 직선의 노즈 길이 [CompFrame.MIN_MOVE] 보다 짧으면 벽 앞에서 닿을 길이 없다 — NOSE_UNREACHABLE 로 멈춘다.
     * - 축에서 시작하는 면(내경 막힌 바닥 · 술통 뒷면) · 원호(내경 돔 · 공 바닥): 직접 보정은 첫 보정점(노즈 중심이 축)으로 공구 쪽에서 c.
     * 직접 보정은 진입 G00 앞에 접근 점을 둔다(테이퍼 뒤집기와 같다): 부품 앞 끝면 바깥(Z + c)에서 X 를 맞추고 Z 로만 들어간다 — 그 X 로 오는 노즈가 도면에서
     * c 안으로 지나면 도면 바깥 X(+ c)에서 Z 를 맞춘 뒤 X 로.
     *
     * 급송 여유(검증 2026-11-11): 막힌 시작의 진입점은 첫 보정점에서 공구 쪽 c · 벽에서 앞으로 c 떨어진 자리 — 거기서 비스듬한 이송 한 줄로 첫 보정점에 닿는다
     * (전에는 급송 끝의 노즈가 벽 평면에 틈 0 으로 닿았다). 이웃 요소에 걸려 그 자리가 안 되면 벽에서 c/2 · c/4, 그래도 안 되면 전처럼 벽에 붙어 내려온다.
     * 뒤집어 끝점(P3)에서 Z+ 로 깎는 3점호([squareStart])도 같은 꼴로 들어온다 — 급송 노즈는 P3 의 법선 앞 c · 공구 쪽 c 에서 멈추고 이송 한 줄로 첫 보정점
     * (노즈가 P3 에 닿는 자리 — 원호는 끝까지 깎는다)에 닿는다. 전에는 P3 너머 접선 연장선 위로 급송이 틈 0 으로 내려앉았다. 앞이 원호에 막히면(오목 R)
     * P3 에서 공구 쪽 직각으로만 물러난 자리에서(급송 노즈가 P3 의 법선을 r 넘지만 접선 연장선에서는 떨어져 있다), 그것도 원호를 파면 NOSE_UNREACHABLE
     */
    private fun finish(
        drawing: Contour, r: Double, side: Side, tip: Int, entryShift: Pt, filled: RoundInput?, inner: Boolean, machineR: Double? = null,
        flip: Boolean = false, typed: Boolean = false, squareStart: Boolean = false,
    ): RoundResult {
        val center = try {
            NoseComp.centerPath(drawing, r, side)
        } catch (e: IllegalArgumentException) {
            stop(RoundWarning.NOSE_UNREACHABLE)
        }
        if (center.warnings.any { it is CompWarning.Unreachable }) stop(RoundWarning.NOSE_UNREACHABLE)
        // 내경: 노즈 중심이 축을 넘으면 노즈가 맞은편 벽을 판다(구멍 지름 < 2r)
        if (inner && center.path.segs.any { lowestX(it) < -EPS }) stop(RoundWarning.NOSE_UNREACHABLE)
        // 외경: 노즈 중심이 축을 넘어 축 거울(부품 반대쪽 — 회전체라 그 자리에도 소재) 도면을 0.001 넘게 파면 도면 형상이 안 나온다. 축에서 끝나는 끝 면 · 돔은
        // 노즈 중심이 축에서 멈춰(거울 도면에서 꼭 r) 걸리지 않는다(검증 2026-11-05: 축에서 0.55 떨어져 끝나는 역방향 3점호 — 형상 줄이 축 거울을 0.653 팠는데
        // 경고가 없었다. 같은 노즈 길이라 기계 보정도 같다)
        if (!inner && center.path.segs.any { lowestX(it) < -EPS } && mirrorGouge(center.path.segs, drawing.segs, r)) stop(RoundWarning.NOSE_UNREACHABLE)
        var centerPath = center.path
        var program = NoseComp.tipPath(drawing, r, side, tip).path
        // 뒤집은 TYPE 의 시작: 공구 쪽 법선 nS 와 막힌 시작인지(+Z 로 가는 수평 직선 — 기본 방향의 막힌 끝을 거꾸로)
        val flipStart = flip && typed
        val first = drawing.segs.first()
        val t0 = CompFrame.startDir(first)
        val nS = CompFrame.normal(t0, side)
        val wallStart = flipStart && first is Line && abs(t0.z) > abs(t0.x) && t0.z > 0
        if (wallStart && machineR == null) {
            // 노즈가 시작 좌표의 법선과 첫 직선에 함께 닿는 자리에서 시작한다(시작 좌표 너머 — 척 · 구멍 속 — 로 넘지 않음)
            val c0 = first.start + nS * r + t0 * r
            if (((centerPath.segs[0].end - c0) dot t0) < CompFrame.MIN_MOVE) stop(RoundWarning.NOSE_UNREACHABLE)
            val tv = NoseComp.tipVector(tip, r)
            centerPath = Contour(listOf<Seg>(Line(c0, centerPath.segs[0].end)) + centerPath.segs.drop(1))
            program = Contour(listOf<Seg>(Line(c0 + tv, program.segs[0].end)) + program.segs.drop(1))
        }

        val warnings = mutableListOf<RoundWarning>()
        // 공구가 원 안쪽(오목): G41 이면 반시계, G42 면 시계 원호
        if (drawing.segs.any { it is Arc && (side == Side.G41) != it.cw && it.radius - r <= EPS }) {
            warnings += RoundWarning.CONCAVE_R_LE_NOSE
        }
        // 뒤집으면 진행 방향도 본다(테이퍼와 같은 기준): 팁의 Z 와 반대로 가며 45° 보다 눕힌 요소를 깎으면 끝날 쪽으로 깎는다(외경 · 구멍을 Z+ 로)
        if (tip in 1..4 && (againstTool(drawing, side, tip) || (flip && backTravel(drawing, tip)))) warnings += RoundWarning.AGAINST_TOOL

        val drawn = listOf(drawing.segs.first().start) + drawing.segs.map { it.end }
        val prog = listOf(program.segs.first().start) + program.segs.map { it.end }
        // 없어진 오목 원호 자리에는 이웃을 잇는 꼭짓점 원호가 들어갈 수 있다 — 그것은 합성 R 이 아니다
        val gone = center.warnings.map { it.index }.toSet()
        val arcs = drawing.segs.withIndex().mapNotNull { (k, s) ->
            (s as? Arc)?.let { RoundArc(k, it.cw, it.center, it.radius, if (k in gone) null else (program.segs[k] as? Arc)?.radius) }
        }
        val nc: NcLines
        val entry: Pt
        var approach = emptyList<Pt>()
        var leadIn = emptyList<Pt>()
        if (machineR == null) {
            if (wallStart || squareStart) {
                val c = entryShift.length
                val tv = NoseComp.tipVector(tip, r)
                val nose0 = centerPath.segs.first().start
                // 공구 쪽으로 c — 내경은 노즈 중심이 축을 넘지 않는 데까지(넘으면 맞은편 벽)
                val up = CompFrame.untilAxis(nose0, nS, c, inner) - nose0
                val plain = FrameMaterial(drawing.segs + drawing.segs.map(FrameMaterial::mirror))
                // 급송이 떨어져야 할 소재: 도면 + 시작 좌표의 벽(3점호는 끝점의 법선). 진입점 → 첫 보정점 이송은 막힌 시작이면 벽에 닿기만, 3점호는 도면에 닿기만
                val walled = FrameMaterial(plain.segs, listOf(first.start to t0 * -1.0))
                fun at(e: Pt, gap: Double, rapid: FrameMaterial): Pair<Pt, List<Pt>>? {
                    if (inner && e.x < -EPS) return null
                    if ((if (wallStart) walled else plain).dist(Line(e, nose0)) < r - 1e-7 || rapid.dist(e) < r + gap - 1e-7) return null
                    val pts = flipApproach(drawing, e + tv, e, r, c, inner) + (e + tv)
                    return if (pts.zipWithNext().all { (a, b) -> rapid.dist(Line(a - tv, b - tv)) >= r + gap - 1e-7 }) (e + tv) to pts else null
                }
                // 벽(끝점의 법선)에서 앞으로 g(c → c/2 → c/4) 떨어진 자리 — 3점호의 첫 보정점은 노즈 중심이 법선 위라 r 더
                val ahead = if (wallStart) 0.0 else r
                var pick = listOf(c, c / 2, c / 4).firstNotNullOfOrNull { g -> at(nose0 + up + t0 * (ahead + g), min(g, up.length), walled) }
                if (pick == null && squareStart) {
                    pick = listOf(1.0, 0.5, 0.25).firstNotNullOfOrNull { k -> at(nose0 + up * k, up.length * k, plain) } ?: stop(RoundWarning.NOSE_UNREACHABLE)
                }
                if (pick == null) {
                    // 전 규칙: 벽에 붙어 공구 쪽에서 내려온다
                    val e = prog[0] + up
                    pick = e to (flipApproach(drawing, e, e - tv, r, c, inner) + e)
                }
                entry = pick.first
                approach = pick.second
            } else if (flipStart) {
                val c = entryShift.length
                // 축에서 시작하는 면 · 원호: 공구 쪽에서 첫 보정점으로 c
                entry = prog[0] + nS * c
                approach = flipApproach(drawing, entry, entry - NoseComp.tipVector(tip, r), r, c, inner) + entry
            } else {
                entry = prog[0] + entryShift
            }
            nc = NcLines().apply {
                element = -2
                for (p in approach.dropLast(1)) move("G0", p)
                move("G0", entry)
                element = -1
                move("G1", prog[0])
                for ((k, s) in program.segs.withIndex()) {
                    element = k
                    if (s is Arc) arc(s) else move("G1", s.end)
                }
            }
        } else {
            val (lines, app, lead) = machineFrame(drawing, side, machineR, entryShift.length, inner, warnings, flipStart, squareStart)
            nc = lines
            approach = app
            leadIn = lead
            entry = app.last()
        }
        return RoundResult(
            RoundSolution(
                filled = filled,
                nodes = drawn.zip(prog) { d, p -> CompPoint(d, p) },
                arcs = arcs,
                tip = tip,
                side = side,
                entry = entry,
                nc = nc.lines,
                drawing = drawing,
                center = centerPath,
                program = program,
                ncRefs = nc.refs.toList(),
                approach = approach,
                flip = flip,
                leadIn = leadIn,
            ),
            warnings,
        )
    }

    /**
     * 뒤집은 직접 보정의 접근 점(진입 G00 앞): 진입점 [entry](그때 노즈 중심 [nose])이 부품 앞 끝면보다 척 쪽 · 구멍 안이면 앞 끝면 바깥(Z + c)에서 X 를 맞추고
     * Z 로만 들어간다(G00 은 X · Z 가 따로 움직일 수 있어 앞 위치에서 바로 오면 부품을 지날 수 있다 — 테이퍼 뒤집기의 접근 줄과 같은 규칙).
     * 그 X 로 Z 를 따라 오는 노즈가 지나는 구간(진입점 노즈의 척 쪽 끝부터 앞)의 도면보다 c 넘게 바깥(외경 위 · 내경 축 쪽)이어야 한다 — 아니면 그 바깥 X(+ c)에서
     * Z 를 맞춘 뒤 X 로 진입점에 간다(내경은 노즈 중심이 축을 넘지 않게 — 진입점이 이미 축 쪽 끝이면 곧장). 전에는 여유 없이 '걸리나' 만 봐 아슬아슬하게
     * 안 걸리는 공 꼭대기 위를 0.03 mm 로 급송이 지났다(검증 2026-11-11)
     */
    private fun flipApproach(drawing: Contour, entry: Pt, nose: Pt, r: Double, c: Double, inner: Boolean): List<Pt> {
        val zFront = drawing.segs.maxOf { max(it.start.z, it.end.z) }
        // 앞 끝면 앞인지는 노즈(척 쪽 끝)로 가른다 — 인선 점으로 가르면 팁 벡터의 Z 가 0 · +r 인 팁(0 · 8 · 1 · 4 · 5 …)은 노즈가 끝면 뒤 r ~ 2r 인데
        // 접근 줄 없이 두 축 G0 로 와 끝면 · 구멍 입구를 팠다(최종 확인 F5 2026-11-13: 팁 4 에서 0.3976). 표준 팁(2 · 3)은 인선이 노즈의 척 쪽 끝이라 전과 같다
        if (nose.z - r >= zFront - EPS) return emptyList()
        val edge = FrameMaterial(drawing.segs).extremeX(nose.z - r, zFront, max = !inner)
        val clear = if (inner) nose.x + r <= edge - c + 1e-7 else nose.x - r >= edge + c - 1e-7
        // 앞 끝면 바깥: 노즈의 척 쪽 끝이 끝면에서 c(표준 팁 2 · 3 은 인선 Z = 끝면 + c — 인선이 노즈의 척 쪽 끝이다)
        val zOut = zFront + c + (entry.z - nose.z) + r
        if (clear) return listOf(Pt(zOut, entry.x))
        // 노즈가 도면 바깥에서 c 떨어진 X(인선 X = 노즈 중심 X + 팁 벡터의 X)
        val xa = (if (inner) max(0.0, edge - r - c) else edge + r + c) + (entry.x - nose.x)
        if (if (inner) xa >= entry.x - 1e-7 else xa <= entry.x + 1e-7) return listOf(Pt(zOut, entry.x))
        return listOf(Pt(zOut, xa), Pt(entry.z, xa))
    }

    /** 팁의 Z 와 반대로 가며 45° 보다 눕힌 요소가 있나(뒤집은 가공 — [TaperCalc] 의 진행 판정과 같다). 원호는 두 끝 접선으로 본다 */
    private fun backTravel(c: Contour, tip: Int): Boolean {
        val tz = NoseComp.tipVector(tip, 1.0).z
        fun back(u: Pt) = abs(u.z) - abs(u.x) > 1e-9 && u.z * tz < 0
        return c.segs.any { s ->
            when (s) {
                is Line -> (s.end - s.start).let { it.length > EPS && back(it.unit()) }
                is Arc -> s.sweepRad() * s.radius > EPS && (back(CompFrame.startDir(s)) || back(CompFrame.endDir(s)))
            }
        }
    }

    /**
     * 기계 보정(G41/G42) 프로그램 — 회사 꼴(계획 단계 4, 사용자 결정 2026-10-29 · `CompFrame`): `G42`(혼자 줄) / `G0X…Z20.` / (`X…`) / `Z…` /
     * `G1 첫 점 F.1` / 형상(도면 좌표) / 빼는 줄 `… F.3` / `G40`(혼자 줄) / `G0Z200.`. 돌려주는 둘째 값 = 진입 명령점(Z20. 자리 → (X 맞춤) → 진입점),
     * 셋째 값 = 그 뒤 형상의 첫 점 앞에 이송으로 거치는 명령점([RoundSolution.leadIn]).
     * 진입점은 첫 점에서 첫 요소의 처음 접선을 거꾸로 c(= |entryShift| = max(Z 여유, r)) — 축에서 시작하는 끝 면 · 돔은 축 너머(X −2c), 내경은 입구 구멍을 따라
     * Z 로 c, 3점호는 시작 접선을 따라. 첫 깎는 줄이 첫 요소와 한 직선이라 노즈는 첫 점의 법선(노즈 접점 = 첫 점)에서 시작한다.
     * 끝(라운드는 끝에도 빼는 줄 — 사용자 결정 2026-10-29 ①): 척 · 구멍 바닥 쪽으로 깎아 끝난 직선(외경 끝 직선 · 플랜지 윗면 · 내경 공 뒤 구멍 · 관통 구멍)은
     * 막힌 끝 — 공구 쪽으로 직각(노즈가 끝 점의 법선에 닿아 멈춤), 축(X0)에서 끝나는 끝 면 · 바닥 면은 그 면을 축 너머 r(X −2r)까지 쓰고 공구 쪽으로 직각
     * (제어기의 안쪽 모서리 교점이 노즈 중심을 축에), 축에서 끝나는 원호(내경 돔 · 공 바닥)는 끝 접선으로 축 너머 r + [AXIS_PAST] 뒤 공구 쪽으로(원호 뒤에 바로
     * G40 을 두지 않는다 — G02/G03 모드의 취소는 FANUC 알람 PS0034), 그 밖(3점호)은 끝 접선으로 곧게.
     * 짧은 막힌 끝을 뺐거나(SHORT_END_SKIPPED) 빼지 못하거나(SHORT_END_PAST), 진입 · 빼는 길을 못 찾으면(ENTRY_NOT_FOUND · EXIT_NOT_FOUND) [warnings] 에 더한다.
     *
     * [flipStart] = 뒤집은 TYPE 윤곽(가공 방향 뒤집기 — 사용자 요청 2026-11-10). 끝은 위 규칙 그대로 맞는다(외경은 면 · 돔이 축에서 끝남, 내경은 입구 구멍이
     * 입구 끝면에서 끝남 — 너머가 공기라 곧게 c, 끝 반직선을 소재로 넣지 않는다). 시작은 위 끝 규칙을 거꾸로:
     * - 막힌 시작(척 · 구멍 바닥 쪽 끝 직선에서 Z+ 로): 진입점 = 시작 좌표에서 공구 쪽으로 c(내경은 축까지만), 첫 깎는 줄이 벽을 따라 시작 좌표로 — 안쪽 꺾임이라
     *   노즈가 시작 좌표의 법선과 첫 직선에 함께 닿아 넘지 않는다. 시작 좌표 너머는 소재(벽)로 본다.
     * - 축에서 시작하는 면(내경 막힌 바닥 · 술통 뒷면): 그 면을 축 너머 r(X −2r)부터 쓰고 거기로 공구 쪽에서 직각으로(교점이 노즈 중심을 축에).
     * - 축에서 시작하는 원호(내경 돔 · 공 바닥): 시작 접선 위 축 너머 r + 조금([AXIS_PAST] 까지)으로 공구 쪽에서 들어가 접선 한 줄로 원호 시작점에 —
     *   G1 뒤 바로 원호로 꺾지 않아 노즈가 축에서 원호를 탄다
     *
     * 뒤집은 시작의 급송 여유(검증 2026-11-11 — 전에는 진입점이 시작 좌표에서 c 라, G41/G42 가 걸린 채 가는 급송의 노즈가 명령선에서 2r 밀려 깎을 면 위
     * c − 2r(모자라면 0.05)로 지났고 급송 끝이 벽 평면에 틈 0 으로 닿았다): 급송 노즈가 도면 · 벽에서 g 이상 떨어지게 짠다 — g 는 c, 안 되면(좁은 구멍 ·
     * 이웃 요소에 걸림) c/2 · c/4, 그래도 안 되면 위의 전 규칙 그대로(좁은 목을 지나 들어가는 구멍은 가는 길의 여유만 줄이고 시작 면에서 멈추는 거리는 지킨다).
     * - 막힌 시작: 급송은 시작 좌표에서 공구 쪽 2r + g · 앞으로 r + g 자리까지(노즈 밑이 깎을 면에서 g, 노즈의 척 쪽 끝이 벽에서 g) → 이송으로 벽 쪽(`G1 Z… F.1`)
     *   → 벽을 따라 시작 좌표로(`X…`) → 형상. 첫 깎는 줄이 둘이 된다. 끝 직선이 노즈 지름쯤이라 벽과 이웃 요소 사이에 그 자리가 없으면 이웃 요소보다
     *   바깥 높이에서 벽 쪽으로 이송해 벽을 따라 내려온다.
     * - 축에서 시작하는 면 · 원호: 진입점을 공구 쪽 2r + g 로(급송 끝의 노즈가 그 면에서 g 이상).
     * [squareStart] = 뒤집어 끝점(P3)에서 Z+ 로 깎는 3점호: P3 너머가 척 · 구멍 안쪽이라 급송은 P3 의 법선 앞에서 멈춘다 — 막힌 시작과 같은 자리(공구 쪽 2r + g ·
     * 법선 앞 r + g)까지 급송, 이송으로 P3 너머 r + [CompFrame.MIN_MOVE] 자리 위까지 → 접선 연장선으로 내려와 → 접선 한 줄로 P3 에 — 노즈가 P3 의 법선에서
     * 원호를 탄다(원호를 끝까지 깎는다). 앞이 원호에 막히면(오목 R) 그 자리 위로 곧장 급송해 내려온다. 전에는 P3 너머 접선 연장선 위로 급송이 틈 0 으로 내려앉았다
     */
    private fun machineFrame(
        drawing: Contour, side: Side, r: Double, c: Double, inner: Boolean, warnings: MutableList<RoundWarning>, flipStart: Boolean = false,
        squareStart: Boolean = false,
    ): Triple<NcLines, List<Pt>, List<Pt>> {
        val segs = drawing.segs
        val first = segs.first()
        val last = segs.last()
        val s0 = first.start
        val t0 = CompFrame.startDir(first)
        val nS = CompFrame.normal(t0, side)
        // 뒤집은 시작의 종류: 막힌 시작 / 축에서 시작하는 면 / 축에서 시작하는 원호(시작 접선으로 축 너머 r + past 에서)
        val wallStart = flipStart && first is Line && abs(t0.z) > abs(t0.x) && t0.z > 0
        val faceStart = flipStart && !wallStart && first is Line && s0.x <= EPS
        val arcStart = flipStart && first is Arc && s0.x <= EPS
        val past0 = if (first is Arc && (side == Side.G41) != first.cw) sqrt(1e-4 * max(0.0, first.radius - r)).coerceIn(AXIS_PAST / 5, AXIS_PAST) else AXIS_PAST
        val p = last.end
        val uE = CompFrame.endDir(last)
        val nE = CompFrame.normal(uE, side)
        val outward = if (inner) -1.0 else 1.0
        val atAxis = p.x <= EPS
        var endPt = p
        var minFirst = CompFrame.MIN_MOVE
        var stretch = true
        // 끝 직선이 짧아(노즈R 쯤) 직각 빼는 줄과의 교점이 그 요소의 보정 시작보다 앞이면 끝 블록이 거꾸로 가 간섭 알람 — 막힌 끝이면 노즈가 벽 앞에서
        // 그 요소에 닿을 길이 없어 그 요소를 빼고 앞 요소 끝에서 벽을 따라 공구 쪽으로 뺀다(전에는 접선으로 곧게 빼 벽 속으로 갔다 — 검증 2026-11-04)
        val noses = runCatching { NoseComp.centerPath(drawing, r, side).path.segs }.getOrNull()
        val shortEnd = noses?.last()?.start?.let { ((p + nE * r - uE * r - it) dot uE) < CompFrame.MIN_MOVE } ?: false
        val wallEnd = last is Line && abs(uE.z) > abs(uE.x) && uE.z < 0
        val blocked = wallEnd && !shortEnd
        // 빼려면 앞 요소의 노즈 길 위에 직각 빼기 교점이 MIN_MOVE 이상 남아야 한다(`CompFrame.keepBeforeShortEnd` — 모서리 R · 주 원호는 노즈 원과의 정확한 교점,
        // 교점이 모서리 R 앞이면 그 R 도 빼고 그 앞 요소에서). 못 맞추면 전처럼 곧게 빼고 SHORT_END_PAST 로 알린다(노즈가 끝 좌표 너머로 감)
        val keep = if (wallEnd && shortEnd && segs.size > 1 && noses != null && noses.size == segs.size) CompFrame.keepBeforeShortEnd(segs, noses, uE, nE, side, r) else null
        val skipLast = keep != null
        if (wallEnd && shortEnd) warnings += if (keep == null) RoundWarning.SHORT_END_PAST else RoundWarning.SHORT_END_SKIPPED
        val x1: Pt
        val away: Pt
        // 막힌 끝의 첫 빼는 줄은 벽을 따라 공구 쪽으로 c — 내경은 축까지만(`CompFrame.untilAxis` — 구멍 반지름보다 c 가 길면 맞은편 벽)
        when {
            blocked -> {
                x1 = CompFrame.untilAxis(p, nE, c, inner)
                away = uE * -1.0
            }
            keep != null -> {
                endPt = segs[keep - 1].end
                x1 = CompFrame.untilAxis(endPt, nE, c, inner)
                away = uE * -1.0
            }
            last is Line && atAxis -> {
                endPt = p + uE * r
                x1 = endPt + nE * c
                away = nE
            }
            last is Arc && atAxis -> {
                // 오목 구면은 축 너머로 아주 조금만 지난다(지난 길이 δ 면 맞은편 구면을 δ² / 2(R − r) 판다 — 그 깊이가 5e-5 mm 를 넘지 않게, [AXIS_PAST] 까지)
                val concave = (side == Side.G41) != last.cw
                val past = if (concave) sqrt(1e-4 * max(0.0, last.radius - r)).coerceIn(AXIS_PAST / 5, AXIS_PAST) else AXIS_PAST
                x1 = p + uE * (r + past)
                away = nE
                minFirst = past / 2
                stretch = false
            }
            else -> {
                x1 = p + uE * c
                away = nE
            }
        }
        // 공기 이동을 잴 소재: 도면 + 직선 · 축에서 시작 · 끝나는 요소의 접선을 늘인 반직선(축 너머 같은 면, 끝 직선 너머) + 막힌 끝 너머 + 그린 구간의 소재
        // (닫은 도면 — `FrameFill`) + 축 거울(회전체라 외경도 부품 반대쪽이 있다 — 검증 2026-11-04: 축 가까이에서 끝나는 외경 3점호의 끝 접선 빼는 줄 ·
        // G40 옮김 · G0Z200. 이 축 너머 맞은편 호를 팠다). 3점호 원호의 접선은 늘이지 않는다 — 그 너머 윤곽을 모르고, 늘인 선이 물러남 띠를 막는다.
        // 첫 빼는 줄의 노즈 길도 소재에서 r 이상인지 본다(끝 접선이 축을 넘어 맞은편으로 가면 G40 자리로 비키는 줄이 짧은 접선 뒤에서 꺾는다)
        // 뒤집어 앞(Z+)으로 깎아 끝난 직선(내경 입구 구멍)은 그 너머가 부품 앞 공기다 — 반직선을 넣지 않는다(끝 좌표 너머로 곧게 뺀다)
        val openEnd = flipStart && last is Line && !atAxis && uE.z > 0
        val mSegs = segs + listOfNotNull(
            if (first is Line || s0.x <= EPS) FrameMaterial.ray(s0, t0 * -1.0) else null,
            if ((last is Line && !openEnd) || atAxis) FrameMaterial.ray(p, uE) else null,
        )
        val fill = FrameFill(segs, inner).takeIf { it.valid }
        // (외경 축 거울은 도면만 — 축에서 시작 · 끝나는 면을 늘인 반직선은 축 너머로 가므로 그 거울은 부품 앞을 막는 끝없는 벽이 된다)
        val walls = listOfNotNull(if (blocked || skipLast) p to uE else null, if (wallStart) s0 to t0 * -1.0 else null)
        val mat = FrameMaterial(mSegs + (if (inner) mSegs else segs).map(FrameMaterial::mirror), walls, fill)
        val zTop = segs.maxOf { maxOf(it.start.z, it.end.z) }

        // 진입 한 벌: 진입점 e0 까지 급송(노즈가 소재에서 r + g 이상) → 이송으로 cmds 를 차례로(마지막이 첫 깎는 점) → 형상.
        // [turns] = 진입 줄끼리 · 첫 이송 줄과의 꺾임도 본다 — `CompFrame.entry` 가 길을 못 찾고 내는 마지막 길(곧장 · 바깥에서 X 맞춤)은 닿는지만 보고 꺾임은
        // 안 봐, 바깥 90° 넘는 꺾임 · 거꾸로 가는 보정 블록(제어기 알람)이 '찾음' 으로 올 수 있다(검증 2026-11-11 — 시작 접선이 Z− 쪽인 3점호).
        // [leads] = cmds 가 여럿일 때 둘째 줄부터의 노즈 길(소재에서 r 이상 · 줄마다 MIN_MOVE 이상)도 본다(첫 줄은 `CompFrame.entry` 가 본다)
        class Start(val cmds: List<Pt>, val app: CompFrame.Approach, val ok: Boolean)

        fun start(e0: Pt, cmds: List<Pt>, g: Double, turns: Boolean = true, leads: Boolean = true): Start {
            val u1 = if (cmds.size > 1) (cmds[1] - cmds[0]).unit() else t0
            val app = CompFrame.entry(mat, side, r, outward, e0, cmds[0], u1, zTop, firstArc = if (cmds.size == 1) first as? Arc else null, clear = g)
            var ok = app.found
            if (ok && turns) {
                val pts = app.points + cmds[0]
                val ds = pts.zipWithNext { a, b -> (b - a).unit() }
                val ns = CompFrame.noses(pts, side, r, then = u1)
                ok = (ds + u1).zipWithNext().none { (a, b) -> CompFrame.acuteOuter(a, b, side) } &&
                    ns.zipWithNext().withIndex().all { (k, n) -> ((n.second - n.first) dot ds[k]) > 0 }
            }
            if (ok && leads && cmds.size > 1) {
                val dirs = cmds.zipWithNext { a, b -> (b - a).unit() }
                val into = (cmds[0] - app.points.last()).unit()
                val noses = cmds.indices.map { k ->
                    val uA = if (k == 0) into else dirs[k - 1]
                    if (k < cmds.lastIndex) CompFrame.corner(cmds[k], uA, dirs[k], side, r) else CompFrame.junction(cmds[k], uA, t0, side, r, arcB = first as? Arc)
                }
                ok = noses.zipWithNext().withIndex().all { (k, n) ->
                    val (a, b) = n
                    a != null && b != null && ((b - a) dot dirs[k]) >= CompFrame.MIN_MOVE - 1e-9 && mat.dist(Line(a, b)) >= r - 1e-7
                }
            }
            return Start(cmds, app, ok)
        }

        // 뒤집은 시작: 급송이 시작 면 · 벽에서 g 떨어진 자리에서 멈추고, 가는 길의 노즈는 다른 소재에서도 clear 이상 떨어진다
        // ([ahead] = 3점호: 법선 앞에서 급송을 멈추고 이송으로 끝점 너머 자리 위까지, [over] = 막힌 시작에서 공구 쪽으로 더 띄우는 높이)
        fun flipped(g: Double, clear: Double, ahead: Boolean = true, over: Double = 0.0): Start {
            val reach = over + 2 * r + max(g, CompFrame.MIN_MOVE)
            return when {
                wallStart -> start(s0 + nS * reach + t0 * (r + max(g, CompFrame.MIN_MOVE)), listOf(s0 + nS * reach, s0), clear)
                squareStart -> {
                    val col = s0 - t0 * (r + CompFrame.MIN_MOVE)
                    if (ahead) start(s0 + nS * reach + t0 * (r + max(g, CompFrame.MIN_MOVE)), listOf(col + nS * reach, col, s0), clear)
                    else start(col + nS * reach, listOf(col, s0), clear)
                }
                faceStart -> (s0 - t0 * r).let { start(it + nS * reach, listOf(it), clear) }
                // 접선 줄(축 너머 past → 원호 시작점)은 일부러 짧다 — 전처럼 재지 않는다
                else -> (s0 - t0 * (r + past0)).let { start(it + nS * reach, listOf(it, s0), clear, leads = false) }
            }
        }
        // (시작 면 · 벽에서 멈추는 거리, 가는 길의 여유): 둘 다 c 부터 — 좁은 구멍 · 이웃 요소에 걸리면 가는 길의 여유를, 자리가 모자라면 멈추는 거리도 줄인다.
        // 그래도 없으면 가는 길은 닿지만 않으면 되는 것으로(여유 0 — 좁은 목을 지나 들어가는 구멍. 멈추는 거리만은 지킨다)
        val gaps = listOf(c to c, c to c / 2, c to c / 4, c / 2 to c / 2, c / 2 to c / 4, c / 4 to c / 4)
        val loose = listOf(c to 0.0, c / 2 to 0.0, c / 4 to 0.0)
        // 끝 직선이 노즈 지름쯤이라 벽과 이웃 요소(공 · 모서리 R) 사이에 급송을 세울 자리가 없으면: 이웃 요소보다 바깥(외경 위 · 내경 축 쪽) 높이에서 벽 쪽으로
        // 이송해 벽을 따라 시작 좌표까지 이송으로 내려온다(그 높이 = 급송 노즈가 설 Z 구간의 도면 끝)
        fun overTop(g: Double): Double {
            val edge = outermost(segs, s0.z, s0.z + 3 * r + max(g, CompFrame.MIN_MOVE), top = !inner)
            return if (edge.isFinite()) max(0.0, (edge - s0.x) * outward) else 0.0
        }
        val begin = when {
            wallStart || faceStart || arcStart ->
                gaps.asSequence().map { flipped(it.first, it.second) }.firstOrNull { it.ok }
                    ?: (if (wallStart) gaps.asSequence().map { flipped(it.first, it.second, over = overTop(it.first)) }.firstOrNull { it.ok } else null)
                    ?: loose.asSequence().map { flipped(it.first, it.second) }.firstOrNull { it.ok }
                    ?: (if (wallStart) loose.asSequence().map { flipped(it.first, it.second, over = overTop(it.first)) }.firstOrNull { it.ok } else null)
                    ?: when {
                        // 전 규칙(급송 여유 없이) — 전처럼 `CompFrame.entry` 가 찾았다는 것만 본다
                        wallStart -> start(CompFrame.untilAxis(s0, nS, c, inner), listOf(s0), 0.0, turns = false)
                        faceStart -> (s0 - t0 * r).let { start(it + nS * c, listOf(it), 0.0, turns = false) }
                        else -> (s0 - t0 * (r + past0)).let { start(it + nS * c, listOf(it, s0), 0.0, turns = false, leads = false) }
                    }
            squareStart ->
                gaps.asSequence().map { flipped(it.first, it.second) }.firstOrNull { it.ok }
                    ?: gaps.asSequence().map { flipped(it.first, it.second, ahead = false) }.firstOrNull { it.ok }
                    ?: loose.asSequence().map { flipped(it.first, it.second) }.firstOrNull { it.ok }
                    ?: loose.asSequence().map { flipped(it.first, it.second, ahead = false) }.firstOrNull { it.ok }
                    ?: flipped(0.0, 0.0, ahead = false)
            else -> start(s0 - t0 * c, listOf(s0), 0.0, turns = false)
        }
        if (!begin.ok) warnings += RoundWarning.ENTRY_NOT_FOUND
        val approach = begin.app.points
        val shape = if (keep != null) segs.take(keep) else segs
        val out = CompFrame.exit(
            mat, side, r, outward, endPt, CompFrame.endDir(shape.last()), x1, away, minFirst = minFirst, stretch = stretch, checkFirst = true, lastArc = shape.last() as? Arc,
        )
        if (!out.found) warnings += RoundWarning.EXIT_NOT_FOUND
        val exits = out.points
        val nc = NcLines()
        CompFrame.write(nc, side, approach, begin.cmds.first(), {
            // 형상 앞의 이송 줄들 — 벽을 따라 시작 좌표로 · 축에서 시작하는 원호 앞 접선 줄(첫 깎는 줄과 같은 진입 이동으로 센다)
            for (q in begin.cmds.drop(1)) move("G1", q)
            for ((k, s) in shape.withIndex()) {
                element = k
                if (s is Arc) arc(s) else move("G1", if (k == segs.lastIndex) endPt else s.end)
            }
        }, exits, refExit = segs.size)
        return Triple(nc, approach, begin.cmds.filter { (it - s0).length > EPS })
    }

    /**
     * Z 구간 [z0, z1] 에 걸친 도면의 가장 바깥 X([top] = 가장 큰 — 외경, 아니면 가장 작은 — 내경). 원호는 구간 끝 Z 의 점까지 본다
     * (`FrameMaterial.extremeX` 는 원호의 양 끝 · 사분점만 봐, 구간 안에서 가파르게 오르는 공의 옆면을 놓친다)
     */
    private fun outermost(segs: List<Seg>, z0: Double, z1: Double, top: Boolean): Double {
        var best = FrameMaterial(segs).extremeX(z0, z1, max = top)
        for (a in segs.filterIsInstance<Arc>()) for (z in listOf(z0, z1)) {
            val dz = z - a.center.z
            if (abs(dz) > a.radius) continue
            val h = sqrt(a.radius * a.radius - dz * dz)
            for (x in listOf(a.center.x + h, a.center.x - h)) if (x >= -EPS && onArc(a, Pt(z, x))) best = if (top) max(best, x) else min(best, x)
        }
        return best
    }

    /** 노즈 중심 길 [noses] 가운데 축 너머로 간 요소가 축 거울 도면에서 r 안으로 0.001 넘게 들어가나 */
    private fun mirrorGouge(noses: List<Seg>, drawing: List<Seg>, r: Double): Boolean {
        val mirrored = drawing.map(FrameMaterial::mirror)
        return noses.filter { lowestX(it) < -EPS }.any { n -> mirrored.any { m -> gap(n, m) < r - 1e-3 } }
    }

    /** 두 요소 사이 가장 짧은 거리(만나면 0) */
    private fun gap(a: Seg, b: Seg): Double = when {
        a is Line -> segGap(a, b).first
        b is Line -> segGap(b, a).first
        else -> arcGap(a as Arc, b as Arc)
    }

    /** 두 원호 사이 가장 짧은 거리 — 만나면 0, 아니면 한쪽 끝점에서 다른 원호까지거나 두 중심을 잇는 선 위의 점(두 원호 안쪽끼리) */
    private fun arcGap(a: Arc, b: Arc): Double {
        if (Intersect.circleCircle(a.center, a.radius, b.center, b.radius).any { onArc(a, it) && onArc(b, it) }) return 0.0
        var best = minOf(distTo(b, a.start), distTo(b, a.end), distTo(a, b.start), distTo(a, b.end))
        val d = b.center - a.center
        if (d.length > EPS) {
            val u = d.unit()
            for (q in listOf(a.center + u * a.radius, a.center - u * a.radius)) if (onArc(a, q)) best = min(best, distTo(b, q))
        }
        return best
    }

    /** 요소의 가장 낮은 x. 원호는 맨 아래(−X 쪽)를 지나면 중심 − R */
    private fun lowestX(s: Seg): Double {
        val ends = min(s.start.x, s.end.x)
        if (s !is Arc) return ends
        val down = wrap2Pi((if (s.cw) -1 else 1) * (-PI / 2 - angleOf(s.start - s.center)))
        return if (down <= s.sweepRad()) min(ends, s.center.x - s.radius) else ends
    }

    /**
     * 노즈 접점이 팁 쪽 사분면(팁 벡터 ±45°) 밖인 요소가 있나([TaperCalc] 와 같은 기준). 원호는 접점 방향이 돈 각
     * = 원호 각이라, 90° 를 넘거나 양 끝 가운데 하나가 사분면 밖이면 밖이다.
     */
    private fun againstTool(c: Contour, side: Side, tip: Int): Boolean {
        val toTip = NoseComp.tipVector(tip, 1.0).unit()
        // 사분면 경계(접점 45°·원호 90°)를 0.1° 쯤 넘는 것까지는 안으로 본다. 3점호는 CAD 점을 넷째 자리로 넣어 중심·접선이
        // 조금 흔들려서, 단면에서 외경으로 도는 보통의 볼록 R(90°)이 경계를 1e-6 쯤 넘어 경고가 났다
        fun off(toTool: Pt) = (toTool dot toTip) > -sqrt(0.5) + QUADRANT_TOL
        fun arcToTool(a: Arc, p: Pt): Pt {
            val n = (p - a.center).unit()
            return if ((side == Side.G41) == a.cw) n else n * -1.0 // 볼록이면 공구가 원 바깥
        }
        return c.segs.any { s ->
            when (s) {
                is Line -> {
                    val d = s.end - s.start
                    d.length > EPS && off(d.unit().left() * (if (side == Side.G41) 1.0 else -1.0))
                }
                is Arc -> {
                    val sw = s.sweepRad()
                    sw * s.radius > EPS && (sw > PI / 2 + QUADRANT_TOL || off(arcToTool(s, s.start)) || off(arcToTool(s, s.end)))
                }
            }
        }
    }

    private fun need(vararg v: Double?) {
        if (v.any { it == null }) stop(RoundWarning.MISSING_INPUT)
    }

    private fun oneBlank(vararg v: Double?) {
        if (v.count { it == null } > 1) stop(RoundWarning.MISSING_INPUT)
    }

    private fun positive(vararg v: Double?) {
        if (v.any { it != null && !(it > 0 && it.isFinite()) }) stop(RoundWarning.BAD_INPUT)
    }

    private fun cornerOk(vararg v: Double?) {
        if (v.any { it != null && !(it >= 0 && it.isFinite()) }) stop(RoundWarning.BAD_INPUT)
    }

    private fun checkTool(r: Double, tip: Int?, zClear: Double) {
        // Z 여유 0 은 된다(진입 거리 = 노즈R). 둘 다 0 이면 진입 이동이 없다 — 테이퍼(`TaperCalc.checkCommon`)와 같이 멈춘다: 기계 보정 프로그램의 진입점이
        // 첫 점과 겹쳐 첫 깎는 줄의 방향이 없고(NaN 좌표 — NC 글을 쓰다 예외가 나 화면이 꺼졌다, 2026-11-10 피드백 1), 직접 보정은 G0 이 도면 첫 점에 닿는다
        val ok = r >= 0 && r.isFinite() && zClear >= 0 && zClear.isFinite() && max(r, zClear) > 0 && (tip == null || tip in 0..9) && !tooBig(r, zClear)
        if (!ok) stop(RoundWarning.BAD_INPUT)
    }

    /** 계산을 멈추는 경고. [guarded] 가 받아 결과로 바꾼다 */
    private class Halt(val warning: RoundWarning) : Exception()

    private fun stop(w: RoundWarning): Nothing = throw Halt(w)

    private inline fun guarded(block: () -> RoundResult): RoundResult =
        try {
            block()
        } catch (h: Halt) {
            RoundResult(null, listOf(h.warning))
        }
}
