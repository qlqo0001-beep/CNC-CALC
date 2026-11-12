package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 테이퍼 경고. 화면이 한국어 문구를 고른다.
 * 위 아홉 개는 계산을 멈춘다 — [TaperResult.solution] 이 null 이고 경고는 그 하나뿐(틀린 숫자를 내지 않으려고).
 * 아래 여덟은 숫자와 함께 나온다.
 */
enum class TaperWarning {
    /** 값이 모자람. 한 경사는 X1·Z1·X2·Z2·각도 가운데 넷, 겹테이퍼는 세 점의 X·Z 모두 */
    MISSING_INPUT,

    /** 다섯 값을 다 넣었는데 각도가 점과 0.001° 넘게 다르다 */
    CONTRADICTION,

    /** 경사각이 0° 이하·90° 이상, 또는 경사의 두 점이 수평·수직으로 놓였다 */
    BAD_ANGLE,

    /** 점이 고른 모양과 안 맞는다: T2 가 T1 왼쪽이 아니거나, X 오르내림이 TYPE 과 반대 */
    SHAPE_MISMATCH,

    /**
     * 지름 ≤ 0, 노즈R·모서리 R·Z 여유 < 0, 노즈R 과 Z 여유가 둘 다 0(진입·후퇴 거리가 없다), 팁 번호가 0~9 밖, 숫자가 아님,
     * 좌표·치수(채운 값 포함)가 ±[MAX_MM] 밖
     */
    BAD_INPUT,

    /**
     * 시작점·끝점 좌표가 잘못됐다: T 점의 반대쪽이거나 같은 자리, X < 0, 또는 T 점에 너무 가까워
     * 노즈R 보정점을 지나 공구가 되돌아가야 한다
     */
    BAD_LEG,

    /** 모서리 R 이 안 들어간다: 접점이 경사·앞뒤 요소 끝(시작·끝점)을 넘거나, 두 R 의 접점이 경사 위에서 겹친다 */
    CORNER_R_TOO_BIG,

    /**
     * T1–T2 사이 원호([TaperInput.arc])의 R 이 작다: R 이 두 점 사이 거리의 절반보다 작거나, 호가 T1 · T2 너머로 부푼다
     * (끝 접선이 형상 사분면 밖 — [TaperCalc.arcMinR] 보다 작음)
     */
    ARC_R_TOO_SMALL,

    /**
     * 노즈R 에 비해 경사(또는 앞뒤 요소)가 짧아 공구가 그 요소의 어느 점에도 닿지 못한다 — 도면 형상이 안 나온다.
     * 한쪽 끝이 볼록이어도 다른 끝 오목 모서리에 접한 원이 요소 밖에서 닿으면 이렇다
     */
    NOSE_UNREACHABLE,

    /**
     * 노즈R 이 경사 길이보다 크고, 그 경사 끝 하나 이상이 오목 모서리(모서리 R < r)다. 공구는 경사에 닿고 숫자도 맞다
     * (못 닿으면 이 경고 대신 NOSE_UNREACHABLE 로 멈춘다). 다만 오목 모서리 쪽에는 R 대신 r 이 남아 경사가 거의 안 남을 수 있으니
     * 도면을 확인할 것. 양 끝이 볼록이면(C0.3 면취에 0.8R) 다 깎여 경고하지 않는다
     */
    NOSE_GT_SLOPE,

    /**
     * 오목 모서리 R ≤ 노즈R. R = r 이면 공구가 꼭 맞아 정확하고, R < r 이면 그 자리에 도면보다 큰 R(= r)이 남는다
     * ([CornerArc.progR] = null). G41/G42 프로그램은 R < r 이면 기계가 간섭 알람을 낼 수 있다
     */
    CONCAVE_R_LE_NOSE,

    /**
     * 형상 일부가 바이트 방향과 반대다(노즈 접점이 팁 쪽 사분면 밖) — 끝날·여유각이 간섭할 수 있다.
     * 정방향의 역테이퍼(TYPE 5), 역방향의 TYPE 1~4 가 그렇다. 정/역방향을 확인.
     * 진행 방향도 본다: 뒤집어([TaperInput.flip]) 팁의 Z 와 반대로 45° 보다 눕힌 요소를 깎으면(뒤집은 외경 형상 3 이 외경을 Z+ 로) 그렇다
     */
    AGAINST_TOOL,

    /**
     * 진입점이나 후퇴점이 중심(X0)을 넘는다(가는 핀 끝·좁은 구멍의 가파른 경사를 그 연장으로 드나들 때) — Z 여유를 줄일 것.
     * 외경 끝 단면을 중심(X0)까지 깎으면 노즈 중심이 중심선에 닿게 인선이 일부러 중심 너머(X −2r)까지 가서도 난다(그 뒤 Z 로 빠진다. 화면 글이 둘을 나눈다)
     */
    ENTRY_PAST_AXIS,

    /**
     * G41/G42 프로그램: 막힌 끝 · 시작 요소(척 · 구멍 바닥 · 보스 쪽)가 노즈R 쯤보다 짧은데 그 요소를 뺄 수 없다 — 앞(뒤) 요소의 노즈 길 위에 벽을 따라
     * 빼는(들어가는) 줄의 교점이 남지 않는다(노즈R 보다 짧은 경사 · 오목 R 뒤 등). 그래서 그 요소를 깎을 때처럼 곧게 빼거나 들어가 노즈가 끝 · 시작
     * 좌표 너머(벽 · 보스 속)로 간다. 직접 보정 프로그램은 인선이 그 좌표에서 멈춘다
     */
    SHORT_END_PAST,

    /**
     * G41/G42 프로그램: 진입 길(Z20. 자리 → 진입점)을 도면에 닿지 않게 짜지 못했다 — 곧장 내려간 길의 노즈가 도면에 닿거나 판다(노즈보다 좁은 구멍 ·
     * 그 Z 구간을 도면이 막음 등), 또는 직각 진입의 두 축 G0 이 급송 속도 비에 따라(LRP=0) 꺾여 닿는다. 글의 진입 줄을 손으로 고쳐야 한다
     */
    ENTRY_NOT_FOUND,

    /**
     * G41/G42 프로그램: 빼는 길(빼는 줄 → G40 → G0Z200.)을 G40 자리 규칙 · 물러남 띠 · 빼는 줄 노즈 길(도면에서 r) · 바깥 90° 이하 꺾임에 맞게 짜지 못했다
     * (노즈에 비해 좁은 구멍 등). 팁 번호 · 보정 형식에 따라 빼는 줄 · G40 · 물러남이 도면에 닿을 수 있다 — 빼는 줄을 손으로 고쳐야 한다
     */
    EXIT_NOT_FOUND,

    /**
     * G41/G42 프로그램: 막힌 끝 · 시작 요소가 노즈R 쯤보다 짧아 그 요소(와 이웃 모서리 R)를 빼고 앞(뒤) 요소에서 벽을 따라 빼거나 들어갔다 — 노즈가 벽 너머로
     * 가지 않는 대신 뺀 요소는 깎이지 않는다(벽 앞에 살이 남음)
     */
    SHORT_END_SKIPPED,
}

/**
 * 경사 하나(Outer Dia / Inner Dia). T1 = 그림 오른쪽(Z 큰 쪽) 점, T2 = 왼쪽 점 — [flip] 이면 반대(T1 = 왼쪽 점). X 는 직경값.
 * X1·Z1·X2·Z2·각도 가운데 하나를 비워 두면 나머지로 푼다(보통 각도).
 */
data class TaperInput(
    /** false = Outer Dia(외경), true = Inner Dia(내경) */
    val inner: Boolean = false,
    /** 형상 TYPE 1~5 (docs/calc-design.md 의 표) */
    val type: Int = 1,
    /** 역방향(Rev.) = 역바이트로 Z+ 쪽으로 깎음 */
    val reverse: Boolean = false,
    val noseR: Double = 0.4,
    /** 가상 인선 번호. null = 표준(외경 3·역 4, 내경 2·역 1) */
    val tip: Int? = null,
    val x1: Double? = null,
    val z1: Double? = null,
    val x2: Double? = null,
    val z2: Double? = null,
    /** 경사각(°, Z 축에서 잼) */
    val angleDeg: Double? = null,
    /** 진입·후퇴 여유(≥ 0). 진입은 첫 깎는 점에서 Z 로, 후퇴는 끝에서 이만큼(노즈R 보다 짧으면 노즈R) */
    val zClear: Double = 1.0,
    /** T1·T2 의 모서리 R. 0 = 날카로운 꼭짓점 */
    val cornerR1: Double = 0.0,
    val cornerR2: Double = 0.0,
    /**
     * 시작점 = T1 앞 요소의 바깥 끝. 그 요소가 단면(TYPE 1·2, 뒤집으면 TYPE 2·4)이면 X(직경), 수평이면 Z. 넣으면 프로그램이 여기서 시작한다.
     * null = 그림은 T1 에서 5 mm(단면은 반경 5)이고, 정방향 프로그램은 앞 요소를 깎지 않고 T1(모서리 R 이면 그 시작)′ 에서 시작한다
     * — 경사(또는 수평인 앞 요소) 연장선이나 단면 바깥에서 Z 로 들어간다
     */
    val start: Double? = null,
    /**
     * 끝점 = T2 뒤 요소의 바깥 끝. 그 요소가 단면(TYPE 2·4, 뒤집으면 TYPE 1·2)이면 X(직경), 수평이면 Z. 넣으면 프로그램이 그 요소를 여기까지 깎는다.
     * null = 그림은 T2 에서 5 mm(중심 쪽 단면은 X0 에서 멈춤)이고, 프로그램은 그 요소를 깎지 않고 T2(모서리 R 이면 그 끝)′ 에서 끝난다.
     * 역방향(reverse)은 가공이 끝점 쪽에서 시작하므로 시작점·끝점의 구실이 바뀐다
     */
    val end: Double? = null,
    /**
     * 가공 방향 뒤집기(T1 ⇄ T2). 같은 형상(TYPE 그림 그대로)을 같은 바이트(팁 그대로)로 **반대 끝부터** 깎는다.
     * 칸의 뜻도 따라간다: T1(x1·z1·[cornerR1]) = 반대 끝 점(그림 왼쪽 점 — 외경 형상 1 이면 외경 쪽 위 점)으로 먼저 깎는 점,
     * [start] = 그 앞 요소(외경 형상 1 이면 외경 Z), [end] = T2 뒤 요소(외경 형상 1 이면 단면 X)의 바깥 끝.
     * 진행이 반대라 보정 쪽도 반대(외경 G41 · 내경 G42). 보정점은 공구 쪽·팁 벡터로만 정해지므로 안 뒤집은 것과 같은 점(순서만 반대)
     */
    val flip: Boolean = false,
    /**
     * 뒤집어 척 쪽 · 구멍 안에서 시작할 때 진입 G00 앞에 접근 줄(부품 앞 끝면 바깥에서 X 를 맞추고 Z 로)을 넣나([TaperSolution.approach]).
     * 계산기의 +Z 가 부품 앞(자유 끝)이라는 뜻이다 — 화면 역방향은 그림을 Z 로 뒤집어 풀어(+Z 가 실제 척 쪽) 끈다.
     * false 면 G41/G42 끝 · 시작의 '넣은 좌표 너머가 막혔나'(척 쪽 수평)도 계산기 +Z 를 척 쪽으로 본다
     */
    val approach: Boolean = true,
    /**
     * T1–T2 사이를 직선(테이퍼 · 면취) 대신 원호로(피드백 2026-11-10). 꼭짓점을 둥글리는 모서리 R([cornerR1] · [cornerR2])과 다르다 — 그 선 자체가 호이고,
     * 모서리 R 은 그 호와 앞뒤 요소가 만나는 T1 · T2 에 그대로 걸린다. 네 좌표를 모두 넣는다([angleDeg] 는 읽지 않는다).
     * false 면 [arcR] · [arcConvex] 는 읽지 않는다(전과 꼭 같은 결과)
     */
    val arc: Boolean = false,
    /** 그 호의 반지름. [arc] 인데 null 이면 MISSING_INPUT. [TaperCalc.arcMinR] 이상이어야 한다(호가 T1 · T2 너머로 부풀지 않게) */
    val arcR: Double? = null,
    /**
     * 볼록 = 소재가 불룩(직선으로 이은 것보다 덜 깎음 — 공구가 원 바깥, 보정 R = R + r). false = 오목(더 깎음, 보정 R = R − r).
     * 형상의 성질이라 [flip] · [reverse] 로 바뀌지 않는다(G02/G03 은 가공 방향에 따라 앱이 정한다)
     */
    val arcConvex: Boolean = true,
)

/**
 * 겹테이퍼(Outer/Inner Double Taper). T1 오른쪽, T2 가운데, T3 왼쪽([flip] 이면 T1 ⇄ T3). 앞뒤 요소는 수평.
 * 세 점의 X(직경)·Z 를 모두 넣는다 — 각도는 결과로 나온다.
 */
data class DoubleTaperInput(
    val inner: Boolean = false,
    val reverse: Boolean = false,
    val noseR: Double = 0.4,
    val tip: Int? = null,
    val x1: Double? = null,
    val z1: Double? = null,
    val x2: Double? = null,
    val z2: Double? = null,
    val x3: Double? = null,
    val z3: Double? = null,
    val zClear: Double = 1.0,
    val cornerR1: Double = 0.0,
    val cornerR2: Double = 0.0,
    val cornerR3: Double = 0.0,
    /** 시작점 Z(T1 앞), 끝점 Z(T3 뒤). 뜻은 [TaperInput.start]·[TaperInput.end] 와 같다 */
    val start: Double? = null,
    val end: Double? = null,
    /** 가공 방향 뒤집기(T1 ⇄ T3, T2 그대로). 뜻은 [TaperInput.flip] 과 같다 — T1 = 그림 왼쪽 점부터 깎는다 */
    val flip: Boolean = false,
    /** 뒤집은 진입 앞 접근 줄. 뜻은 [TaperInput.approach] 와 같다 */
    val approach: Boolean = true,
)

/** 도면 점과 보정점(가상 인선 = 직접 보정 프로그램 좌표). x 는 반경 — 화면은 xDia */
data class CompPoint(val drawn: Pt, val prog: Pt) {
    /** ΔX(직경) = 보정 − 도면 */
    val dXDia: Double get() = prog.xDia - drawn.xDia
    val dZ: Double get() = prog.z - drawn.z
}

/** 꼭짓점에 끼운 모서리 R */
data class CornerArc(
    /** 도면 R */
    val r: Double,
    /** 보정 R(합성 R) = 볼록 R + r, 오목 R − r. 오목 R ≤ r 이면 원호가 없어져 null */
    val progR: Double?,
    /** 가공 순서로 갈 때의 방향. true = G02 */
    val cw: Boolean,
    /** 원호 중심(도면) */
    val center: Pt,
)

/** T1–T2 사이 원호([TaperInput.arc]) — 모서리 R 로 줄인 뒤의 그 호 */
data class SlopeArc(
    /** [TaperSolution.drawing] · program · center 의 요소 번호 */
    val index: Int,
    /** 도면 R */
    val r: Double,
    /** 보정 R(합성 R) = 볼록 R + r, 오목 R − r */
    val progR: Double?,
    /** 가공 순서로 갈 때의 방향. true = G02 */
    val cw: Boolean,
    /** 원호 중심(도면) */
    val center: Pt,
)

/** 꼭짓점 T1·T2(·T3) 하나 */
data class TaperPoint(
    /** 입력 꼭짓점(비웠던 값도 채움). 모서리 R 이 있으면 이론 꼭짓점 */
    val vertex: Pt,
    /** 모서리 R 이 없으면 꼭짓점 하나(drawn = vertex), 있으면 R 시작·끝 두 접점 — 가공 순서 */
    val nodes: List<CompPoint>,
    /** 모서리 R. 0 이거나 이웃과 접해서 이어지면 null */
    val corner: CornerArc?,
)

data class TaperSolution(
    /**
     * T1, T2(, T3) — 칸 순서. 정방향은 가공 순서다(그림 오른쪽부터, [TaperInput.flip] 이면 왼쪽부터).
     * 계산기 역방향(reverse)은 지금처럼 마지막 점부터 깎는다
     */
    val points: List<TaperPoint>,
    /** i번 = T(i+1) – T(i+2) 경사각(°), 칸 순서 */
    val anglesDeg: List<Double>,
    val tip: Int,
    /** G41/G42 프로그램의 보정 쪽 */
    val side: Side,
    /**
     * 직접 보정 프로그램의 G00 진입점·후퇴점(프로그램 좌표). 진입점은 첫 깎는 점에서 Z 로 max(Z 여유, 노즈R) 바깥 —
     * 뒤집어 넣은 수평 시작점부터 깎을 때만 그 시작점에서 공구 쪽으로 X 로(척·구멍 바닥 쪽으로 넘어 들어가지 않게)
     */
    val entry: Pt,
    val exit: Pt,
    /**
     * 직접 보정 프로그램에서 [entry] 앞 G00 점들(접근 줄). 뒤집어 척 쪽 · 구멍 안에서 시작하면 부품 앞 끝면 바깥(Z + c)에서 X 를 맞추고
     * Z 로 들어간다 — 그 X 로 오면 도면에 닿을 때(역테이퍼 꼴 · 겹테이퍼 산 너머)는 바깥 X 로 Z 를 맞춘 뒤 X 로. 그 밖은 빈 목록
     */
    val approach: List<Pt>,
    /**
     * 가공 시작 쪽 요소를 그 끝 좌표부터 깎나(좌표를 넣었을 때). false 면 그 요소는 깎지 않고 경사(또는 그 요소의 보정선)를
     * 늘인 진입점에서 첫 꺾임점′ 으로 바로 간다 — 그때 program.segs[0] 은 그 요소의 보정선(참고)이다
     */
    val fromStart: Boolean,
    /**
     * 가공 끝 쪽 요소를 그 끝 좌표까지 깎나(좌표를 넣었을 때). false 면 마지막 T 점(모서리 R 이면 그 끝)′ 에서 끝나고 그 요소는
     * 깎지 않는다 — 그때 program.segs 의 마지막은 그 요소의 보정선(참고)이다
     */
    val toEnd: Boolean,
    /**
     * 기본 프로그램 — 도면 좌표에 G41/G42 를 걸어 기계가 보정한다. 회사 꼴(계획 단계 4, 사용자 결정 2026-10-29 — `CompFrame`):
     * `G42`(혼자 줄) / `G0X…Z20.` / (`X…` — 바깥에서 X 를 맞출 때) / `Z…`(진입점 — 첫 깎는 점에서 c) / `G1 … F.1`(첫 깎는 점) / 형상(모서리 R 은 G2/G3 도면 R) /
     * 빼는 줄 `… F.3`(보정 켠 채 공기로 — 막힌 끝은 벽을 따라 공구 쪽, 빈 끝은 끝 좌표 너머로 곧게, 끝을 비우면 끝 요소를 따라 c, G40 자리 둘레에 도면이 있으면
     * 한 줄 더, +Z 물러남이 도면에 닿으면 바깥으로 X 를 맞추는 줄 더) / `G40`(혼자 줄) / `G0Z200.`. 보정 줄이 이동 없는 혼자 줄이라 깎는 줄의 노즈는
     * 보정 시작 · 취소 형식(A · B · C)과 팁 번호에 상관없다. 공구 보정 값(노즈 R · 팁)은 글에 넣지 않는다(화면에만).
     * 외경 단면을 중심(X0)까지 깎아 끝나면 그 단면을 X −2r 까지 쓰고 공구 쪽으로 빼는 줄(제어기의 안쪽 모서리 교점이 노즈 중심을 중심선에)
     */
    val ncG41G42: List<String>,
    /**
     * 직접 보정 프로그램 — G40 상태로 앱이 계산한 보정 좌표(모서리 원호는 보정 R). 시작(넣었을 때)·끝은 인선이 그 좌표에 오게
     * (외경 단면을 중심 X0 까지 깎는 끝만은 노즈 중심이 중심선에 닿게 — [program]). 뒤집어 척 쪽에서 시작하면 진입 G00 앞에 접근 줄([approach])
     */
    val ncG40: List<String>,
    /** 도면선: 시작점 → T 점들(모서리 R 원호 포함) → 끝점, 가공 순서(정방향은 오른쪽부터, 역방향·뒤집기는 왼쪽부터, 둘 다면 오른쪽부터) */
    val drawing: Contour,
    /** 노즈 중심 경로 = [program] − 팁 벡터. segs[k] ↔ drawing.segs[k] */
    val center: Contour,
    /**
     * 직접 보정 프로그램 경로(가상 인선). segs[k] ↔ drawing.segs[k]. 끝은 [toEnd] 면 끝점′(외경 단면을 중심 X0 까지 깎으면 노즈 중심이
     * 중심선에 닿는 점 — 인선이 앞서 가는 팁이면 X0 너머), 처음은 [fromStart] 면 시작점′
     */
    val program: Contour,
    /** T1–T2 를 원호로 냈으면([TaperInput.arc]) 그 호. 직선이면 null */
    val arc: SlopeArc? = null,
)

data class TaperResult(val solution: TaperSolution?, val warnings: List<TaperWarning>)

object TaperCalc {
    /** 노즈R 고르기(직접 입력도 된다) */
    val NOSE_R_CHOICES = listOf(0.2, 0.4, 0.8, 1.2)

    /** 시작·끝점을 비웠을 때 그림에서 T 점에 붙이는 앞뒤 요소의 길이(mm, 단면은 반경). 그 요소는 깎지 않는다 */
    const val LEG_DEFAULT = 5.0

    /** 다섯 값을 다 넣었을 때 각도가 점과 이만큼(°)까지 달라도 받아 준다 — 각도를 소수 셋째 자리로 반올림해 넣어도 되게 */
    private const val ANGLE_TOL = 1e-3

    /**
     * T1–T2 호가 T1 · T2 너머로 부푼 깊이(mm)를 이만큼까지 받는다 — CAD 값을 0.001 로 반올림해 넣으면 앞뒤 요소에 접하던 호가 아주 조금 넘는다
     * (X50.01 에 R5 → 2.5e-6 mm). 0.01 작은 R(5×5 에 R4.99 → 1e-5 mm)은 막는다
     */
    private const val ARC_OVER_TOL = 5e-6

    /** 호가 앞뒤 요소에 이 각(rad, 단위 접선의 외적)보다 덜 꺾여 이어지면 접한 것으로 본다(꺾임이 아니다) — 제어기 · `CompFrame.junction` 이 법선으로 잇는 각 */
    private const val ARC_SMOOTH = 5e-4

    /**
     * 짧은 막힌 끝 · 시작을 빼고 벽을 따라 빼거나 들어가는 줄이 남는 요소의 접선과 이 각(rad, 단위 접선의 외적)보다 덜 어긋나게 거꾸로 이어지면 되돌아감으로 본다 —
     * NC 0.001 반올림이 호의 접선을 1e-3/현 rad 쯤 옮겨 안쪽 꺾임이 바깥이 되기도 한다(검증 2026-11-12: R12.552 가 179.9993° 바깥)
     */
    private const val REVERSE_TOL = 2e-3

    /**
     * T1(x1 직경, z1)–T2 를 잇는 호([TaperInput.arc])의 가장 작은 R — 호의 양 끝 접선이 형상 사분면 안(호가 두 점의 X · Z 범위를 넘어 부풀지 않는다).
     * 현이 Z 축과 이루는 각 θ 에서 R = 현 / (2·sin(min(θ, 90° − θ))). 이 R 이면 한쪽 끝(45° 면 양쪽)이 앞뒤 요소에 접한다.
     * 두 점이 수평 · 수직 · 같은 자리면 null
     */
    fun arcMinR(x1: Double, z1: Double, x2: Double, z2: Double): Double? {
        val dz = abs(z2 - z1)
        val dx = abs(x2 - x1) / 2
        if (!(dz > EPS && dx > EPS) || !dz.isFinite() || !dx.isFinite()) return null
        return sqrt(dz * dz + dx * dx) / (2 * sin(min(atan2(dx, dz), atan2(dz, dx))))
    }

    /** a–b 를 반지름 R 의 호로 이을 때 호가 두 점의 X · Z 범위를 넘어 부푼 깊이(mm). 안 넘으면 0, R 이 현의 절반보다 작으면(호가 없다) +∞ */
    private fun arcOver(a: Pt, b: Pt, radius: Double): Double {
        val d = b - a
        val q = d.length / (2 * radius)
        if (q > 1) return Double.POSITIVE_INFINITY
        val over = asin(q) - min(atan2(abs(d.x), abs(d.z)), atan2(abs(d.z), abs(d.x)))
        return if (over <= 0) 0.0 else radius * (1 - cos(over))
    }

    /**
     * 가공 순서 a → b 를 잇는 반지름 R 의 작은 호. 볼록(소재가 불룩) = 중심이 소재 쪽(공구 반대쪽), 오목 = 공구 쪽.
     * 방향은 [NoseComp] 의 규칙 그대로: 왼쪽 공구(G41) + 시계 = 볼록
     */
    private fun arcBetween(a: Pt, b: Pt, radius: Double, convex: Boolean, side: Side): Arc {
        val d = b - a
        val h = sqrt(max(0.0, radius * radius - (d dot d) / 4))
        val n = toolNormal(d.unit(), side)
        return Arc(a, b, (a + b) * 0.5 + n * (if (convex) -h else h), cw = (side == Side.G41) == convex)
    }

    fun calc(input: TaperInput): TaperResult = guarded {
        // 뒤집으면 칸의 T1 이 그림 왼쪽 점이다 — 그림 순서(T1 = 오른쪽)로 바꿔 놓고 풀고, 가공만 반대 끝부터(solve)
        val i = with(input) {
            if (flip) copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start) else this
        }
        require(i.type in 1..5) { "TYPE 은 1~5: ${i.type}" }
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2), i.start, i.end)
        // T1–T2 를 호로 내면 네 좌표와 R 을 모두 넣는다 — 각도 칸은 읽지 않는다(도면이 호의 현 각도를 주지 않는다)
        val given = if (i.arc) listOf(i.x1, i.z1, i.x2, i.z2) else listOf(i.x1, i.z1, i.x2, i.z2, i.angleDeg)
        if (given.count { it == null } > (if (i.arc) 0 else 1)) stop(TaperWarning.MISSING_INPUT)
        if (i.arc && i.arcR == null) stop(TaperWarning.MISSING_INPUT)
        if (given.any { it != null && !it.isFinite() }) stop(TaperWarning.BAD_INPUT)
        if (i.arc && (!(i.arcR!! > 0 && i.arcR.isFinite()) || tooBig(i.arcR))) stop(TaperWarning.BAD_INPUT)
        val ang = if (i.arc) null else i.angleDeg
        if (ang != null && (ang <= 0 || ang >= 90)) stop(TaperWarning.BAD_ANGLE)
        // T1 에서 왼쪽 T2 로 갈 때 X 가 커지면 +1: 외경 TYPE 1~4, 내경 TYPE 5
        val up = if (i.inner == (i.type == 5)) 1.0 else -1.0
        val t = ang?.let { tan(it * PI / 180) }
        // 빈 값은 많아야 하나라 차례로 채우면 된다
        val x1 = i.x1 ?: (i.x2!! - up * 2 * leftward(i.z1!!, i.z2!!) * t!!)
        val x2 = i.x2 ?: (x1 + up * 2 * leftward(i.z1!!, i.z2!!) * t!!)
        val z1 = i.z1 ?: (i.z2!! + rise(x1, x2, up) / t!!)
        val z2 = i.z2 ?: (z1 - rise(x1, x2, up) / t!!)
        // 아주 작은 각도로 푼 점도 한도를 넘을 수 있다
        if (x1 <= 0 || x2 <= 0 || tooBig(x1, z1, x2, z2)) stop(TaperWarning.BAD_INPUT)
        val p1 = Pt.ofDia(z1, x1)
        val p2 = Pt.ofDia(z2, x2)
        val deg = slopeDeg(p1, p2, up)
        if (ang != null && abs(deg - ang) > ANGLE_TOL) stop(TaperWarning.CONTRADICTION)
        // 호가 T1 · T2 너머로 부풀면(끝 접선이 형상 사분면 밖) 그림의 형상이 아니다 — 볼록은 단면 · 외경 너머로 튀어나오고 오목은 언더컷
        if (i.arc && arcOver(p1, p2, i.arcR!!) > ARC_OVER_TOL) stop(TaperWarning.ARC_R_TOO_SMALL)

        // 그림대로: TYPE 1·2 는 T1 에 단면, TYPE 2·4 는 T2 에 단면, 나머지는 수평. 단면은 경사의 오르내림을 잇는다
        val front = if (i.type <= 2) vertical(p1, -up, i.start) else horizontal(p1, 1.0, i.start)
        val back = if (i.type == 2 || i.type == 4) vertical(p2, up, i.end) else horizontal(p2, -1.0, i.end)
        solve(
            listOf(front, p1, p2, back), listOf(i.cornerR1, i.cornerR2), i.inner, i.reverse, i.flip, i.noseR, i.tip, i.zClear, i.start, i.end, i.approach,
            if (i.arc) i.arcR!! to i.arcConvex else null,
        )
    }

    fun calcDouble(input: DoubleTaperInput): TaperResult = guarded {
        // 뒤집으면 칸의 T1 이 그림 왼쪽 점(T2 는 그대로) — 그림 순서로 바꿔 놓고 푼다
        val i = with(input) {
            if (flip) copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start) else this
        }
        checkCommon(i.noseR, i.tip, i.zClear, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.start, i.end)
        val v = listOf(i.x1, i.z1, i.x2, i.z2, i.x3, i.z3)
        if (v.any { it == null }) stop(TaperWarning.MISSING_INPUT)
        if (v.any { !it!!.isFinite() } || listOf(i.x1, i.x2, i.x3).any { it!! <= 0 } || tooBig(*v.toTypedArray())) stop(TaperWarning.BAD_INPUT)
        val p = listOf(Pt.ofDia(i.z1!!, i.x1!!), Pt.ofDia(i.z2!!, i.x2!!), Pt.ofDia(i.z3!!, i.x3!!))
        slopeDeg(p[0], p[1])
        slopeDeg(p[1], p[2])
        val pts = listOf(horizontal(p[0], 1.0, i.start)) + p + horizontal(p[2], -1.0, i.end)
        solve(pts, listOf(i.cornerR1, i.cornerR2, i.cornerR3), i.inner, i.reverse, i.flip, i.noseR, i.tip, i.zClear, i.start, i.end, i.approach)
    }

    /**
     * drawn = [시작점, T1, T2, (T3,) 끝점] 그림 오른쪽부터, corners = T 점마다 모서리 R, start · end = 그림의 앞(오른쪽)·뒤 요소 끝.
     * 보정은 [NoseComp] 의 일반 해법 그대로. 앞뒤 요소는 그 바깥 끝 좌표를 넣었을 때만 깎는다.
     * 가공은 역방향(역바이트)과 뒤집기 가운데 하나만 켜졌으면 그림 왼쪽부터(Z+) — 가공 시작 쪽은 그때 end, 아니면 start.
     * 바이트(팁)는 역방향만 따른다(뒤집어도 같은 바이트). 보정 쪽·진입·후퇴·NC 는 모두 가공 순서(path)로 정하므로 뒤집기도 그대로 맞는다.
     * [arc] = (R, 볼록) 이면 T1–T2 요소(홑테이퍼의 1번)를 직선 대신 그 원호로 넣는다 — 모서리 R · 보정 · 회사 틀은 직선 · 원호가 섞인 윤곽을 그대로 다루고,
     * 여기서는 경사를 직선으로 보던 곳(꺾임의 볼록 판정 · 비운 시작 · 끝의 연장선 · 접근 줄 껍질 · 바이트 반대 · 짧은 막힌 끝)만 그 점의 접선으로 본다
     */
    private fun solve(
        drawn: List<Pt>, corners: List<Double>, inner: Boolean, reverse: Boolean, flip: Boolean, r: Double, tipIn: Int?, zClear: Double,
        start: Double?, end: Double?, approach: Boolean, arc: Pair<Double, Boolean>? = null,
    ): TaperResult {
        val leftFirst = reverse != flip
        val cutFirst = (if (leftFirst) end else start) != null
        val cutLast = (if (leftFirst) start else end) != null
        val tip = tipIn ?: NoseComp.standardTip(inner, reverse)
        // G41/G42 = 진행 방향에서 본 공구 쪽. 같은 바이트로 반대 끝부터 깎으면 반대 쪽(외경 형상 1 뒤집기 = G41)
        val side = Side.of(inner, leftFirst)
        val path = if (leftFirst) drawn.reversed() else drawn
        // 모서리 R 을 끼우기 전 요소들. raw[k] = path[k] → path[k+1]
        val raw: List<Seg> = path.zipWithNext { a, b -> Line(a, b) }.let { lines ->
            if (arc == null) lines else lines.mapIndexed { k, l -> if (k == 1) arcBetween(l.start, l.end, arc.first, arc.second, side) else l }
        }
        // T1–T2 호가 모서리 k(경로 점 path[k + 1])에서 앞뒤 요소에 접해 이어지나 — 꺾임이 ARC_SMOOTH 아래거나, 받는 띠(ARC_OVER_TOL — 가장 작은 R 보다 조금 작은 R)
        // 에서 호가 그 요소 너머로 아주 조금 넘어 직선(현)과 반대로 꺾인다
        fun arcTangentAt(k: Int): Boolean {
            val a = raw[1] as Arc
            val chord = (a.end - a.start).unit()
            val uIn = if (k == 0) (raw[0].end - raw[0].start).unit() else CompFrame.endDir(a)
            val uOut = if (k == 0) CompFrame.startDir(a) else (raw[2].end - raw[2].start).unit()
            val straight = if (k == 0) uIn cross chord else chord cross uOut
            val turn = uIn cross uOut
            return (uIn dot uOut) > 0 && (abs(turn) <= ARC_SMOOTH || (turn > 0) != (straight > 0))
        }
        // 접해 이어지는 끝은 꺾인 모서리가 없어 그 모서리 R 은 끼우지 않는다(필렛이 꼭짓점에서 1e-5 mm 안이다). 끼우려 하면 CORNER_R_TOO_BIG('R 을 줄이세요' —
        // 어떤 R 도 안 됨)이 나거나 거꾸로 꺾인 0.001 mm 필렛 줄이 끼어 노즈가 못 도는 오목 R 이 됐다(검증 2026-11-12)
        val cr = (if (leftFirst) corners.reversed() else corners).let { c ->
            if (arc == null) c else c.mapIndexed { k, v -> if (v > 0 && arcTangentAt(k)) 0.0 else v }
        }
        val filleted = Corners.insert(raw, cr) ?: stop(TaperWarning.CORNER_R_TOO_BIG)
        val drawing = Contour(filleted.segs)
        // T1–T2 호(모서리 R 로 줄인 것)의 요소 번호 = 둘째 모서리 바로 앞 요소. 직선이면 null
        val arcIdx = if (arc == null) null else filleted.prev[1]
        val comp = try {
            NoseComp.tipPath(drawing, r, side, tip)
        } catch (e: IllegalArgumentException) {
            stop(TaperWarning.NOSE_UNREACHABLE)
        }
        // 요소 하나가 빠지면 도면 모양이 안 나온다. 오목 모서리 R < r(ArcTooSmall)은 r 이 남는 것으로 알린다 — T1–T2 호가 그러면(오목 R < r) 주 요소가
        // 사라지는 것이라 멈춘다(3점호와 같다)
        if (comp.warnings.any { it is CompWarning.Unreachable || (it is CompWarning.ArcTooSmall && it.index == arcIdx) }) stop(TaperWarning.NOSE_UNREACHABLE)
        val gone = comp.warnings.map { it.index }.toSet()
        val tv = NoseComp.tipVector(tip, r)
        // 팁의 방향만(노즈R 0 이어도 바이트가 어느 쪽을 보는지)
        val tv1 = NoseComp.tipVector(tip, 1.0)
        val segs = comp.path.segs.toMutableList()
        val first = drawing.segs.first()
        val last = drawing.segs.last()
        val uF = (first.end - first.start).unit()
        val uB = (last.end - last.start).unit()
        // 진입·후퇴 거리. G41/G42 시작 이동이 노즈R 보다 짧으면 안 된다
        val c = max(zClear, r)

        // 꺾임 k(경로 점 path[k])로 들어오고 나가는 방향 — T1–T2 가 호면 현이 아니라 그 점의 접선
        fun dirIn(k: Int): Pt = (raw[k - 1] as? Arc)?.let(CompFrame::endDir) ?: (path[k] - path[k - 1])
        fun dirOut(k: Int): Pt = (raw[k] as? Arc)?.let(CompFrame::startDir) ?: (path[k + 1] - path[k])

        // 꺾임 k(경로 점 path[k])가 날카로운 볼록 모서리인가 — 공구가 재료 모서리 바깥을 돈다. 호가 앞뒤 요소에 접해서 이어지면 꺾임이 아니다
        fun convexAt(k: Int, corner: Int) = (dirIn(k) cross dirOut(k)).let {
            filleted.arc[corner] == null && (arc == null || abs(it) > ARC_SMOOTH * dirIn(k).length * dirOut(k).length) && (it > 0) == (side == Side.G42)
        }

        // G41/G42 가 짧은 막힌 끝 · 시작 요소를 뺄 자리를 잴 노즈 길 = 직접 보정의 노즈 중심 길. 다만 비운 시작 · 끝을 T1–T2 호의 접선으로 드나들면(볼록 꺾임)
        // 제어기의 노즈는 그 꼭짓점의 법선에 서므로(직접 보정은 앞뒤 요소 보정선과의 늘인 교점) 호의 노즈 길을 거기서 자른다 — 안 자르면 늘인 토막 위의 교점을
        // 자리로 잘못 봐 호 블록이 거꾸로 간다(간섭 알람 — 노즈R 보다 짧은 호 + 짧은 막힌 끝)
        fun frameNoses(): List<Seg> {
            val ns = segs.map { it.shifted(tv * -1.0) }
            val k = arcIdx ?: return ns
            val a = drawing.segs[k] as Arc
            val n = ns[k] as? Arc ?: return ns
            val from = if (!cutFirst && convexAt(1, 0)) a.center + (a.start - a.center).unit() * n.radius else n.start
            val to = if (!cutLast && convexAt(path.size - 2, cr.lastIndex)) a.center + (a.end - a.center).unit() * n.radius else n.end
            return ns.toMutableList().also { it[k] = Arc(from, to, n.center, n.cw) }
        }

        // 짧은 막힌 끝 · 시작을 빼고 벽을 따라 빼는(들어가는) 줄이 남는 끝(첫) 요소의 접선(단위 [uA] → [uB])에서 바깥으로 90° 넘게 꺾이거나 되돌아가나
        // (`CompFrame.acuteOuter` + NC 반올림 여유 [REVERSE_TOL]). 가장 작은 R 의 호가 그 끝에서 벽과 나란하면 들어가는 줄과 꼭 반대라 제어기가 바깥 꺾임으로
        // 돌아 호를 판다(검증 2026-11-12: 깊이 r + 0.283) — 그런 줄은 짜지 않고 빼지 않는다(SHORT_END_PAST)
        fun turnsBack(uA: Pt, uB: Pt) = CompFrame.acuteOuter(uA, uB, side) || ((uA dot uB) < 0 && abs(uA cross uB) < REVERSE_TOL)

        // 끝(직접 보정). 좌표를 넣었으면 인선(프로그램 점)이 끝점 좌표에 오게 — 끝 요소의 보정선 위에서 끝점에 내린 발. 공구가 끝좌표를 넘지 않는다
        // (Haas 워크북 56쪽 O00055 `N14 Z-2.375`, 53쪽 O00052 처럼). 후퇴: 끝 요소가 수평이면 공구 쪽으로 직각,
        // 단면이면 바깥으로 이어서 — 다만 단면을 재료 쪽(외경은 중심 쪽, 내경은 바깥)으로 깎아 끝나면(뒤집기·계산기 역방향의 앞 단면) 끝 X 너머도
        // 같은 단면이나 보스라 공구 쪽으로 직각(이어 가면 G00 이 끝 X 너머를 급송으로 깎는다). 그 단면을 중심(X0)까지 깎으면 노즈 중심이 중심선에 닿는
        // 데까지 간 뒤 공구 쪽으로 직각(안 그러면 중심에 살이 남는다 — 아래 toAxis).
        // 비우면 그 요소는 깎지 않는다(그림·재료만): 마지막 꺾임점(모서리 R 이면 그 끝)′ 에서 끝 요소의 공구 쪽으로 c 빠진다.
        // G41/G42(회사 꼴 — CompFrame)의 첫 빼는 줄: 막힌 끝(척 · 구멍 바닥 쪽 수평, 재료 쪽 단면 — 보스)은 벽을 따라 공구 쪽으로(안쪽 꺾임이라 제어기가
        // 두 보정선의 교점에 세워 노즈가 끝 요소와 끝좌표의 법선에 함께 닿는다 — 끝좌표를 넘지 않음), 너머가 빈 끝은 끝 좌표 너머로 곧게(노즈 접점이
        // 끝좌표 너머까지 — 사용자 결정 2026-10-29 ③), 끝을 비우면 끝 요소를 따라 c(날카로운 볼록이면 경사를 늘여 — 너머가 공기, 아니면 끝 요소 선 위)
        val nB = toolNormal(uB, side)
        // 화면 역방향(그림을 Z 로 거울해 푼다 — approach = false)은 계산기 +Z 가 실제 척 쪽이다([TaperInput.approach]). 넣은 좌표 너머가 막혔는지 ·
        // G41/G42 의 진입 · 물러남 쪽(부품 앞)을 볼 때 쓴다
        val mirror = !approach
        val exit: Pt
        var endD = last.end // G41/G42 프로그램의 마지막 형상 점(도면 좌표)
        val x1D: Pt // G41/G42 첫 빼는 줄 끝(도면 좌표)
        val awayD: Pt // 그 끝 둘레(G40 자리)에 도면이 있을 때 더 갈 방향
        var blockedEnd = false
        var freeEnd = false
        var toAxisEnd = false // 외경 단면을 중심(X0)까지 — 축 너머 같은 면을 늘인 반직선으로 본다
        var keepLast: Int? = null // G41/G42 가 짧은 막힌 끝 요소(와 그 앞 모서리 R)를 빼고 남길 요소 수 — null 이면 빼지 않음
        var shortPast = false // 짧은 막힌 끝 · 시작 요소를 뺄 수 없어 곧게 빼거나 들어감(SHORT_END_PAST)
        if (cutLast) {
            var e0 = foot(last.end, segs.last().start, uB)
            // 인선이 끝좌표에 왔을 때 노즈가 끝 요소에 한 번도 닿지 않으면(요소가 노즈R 쯤보다 짧다) 그 요소는 깎이지 않는다
            if (((e0 - segs.last().start) dot uB) < -EPS || ((e0 - tv - last.start) dot uB) < -EPS) stop(TaperWarning.BAD_LEG)
            val face = !isHorizontal(uB)
            val inward = face && (uB.x < 0) != inner
            val leave = if (!face || inward) nB else uB
            // 외경 단면을 중심(X0)까지: 노즈 중심이 중심선에 닿아야 중심에 살이 안 남는다. 직접 보정은 인선을 그만큼(T3 이면 X−2r) 더,
            // G41/G42 는 단면을 중심 너머 r(X−2r)까지 쓰고 공구 쪽(Z)으로 빼는 줄 — 안쪽 꺾임이라 제어기가 교점(노즈 중심 = 중심선)을 잡는다.
            // 너머는 이미 깎은 같은 면이지만 급송은 단면에서 떨어지는 Z 로만(검증 2026-10-27, 현장 안전)
            val toAxis = inward && !inner && last.end.x <= EPS
            // 내경 바닥 단면을 중심(X0)까지(너머가 빈 끝이지만 축 너머는 같은 면): G41/G42 도 외경처럼 단면을 X −2r 까지 쓰고 공구 쪽으로(노즈 중심이 축에)
            val axisIn = face && inner && uB.x < 0 && last.end.x <= EPS
            // 넣은 끝좌표 너머가 막힌 쪽인가 — 척 · 구멍 바닥 쪽으로 깎아 끝난 수평 끝(거울이면 계산기 +Z 쪽), 재료 쪽으로 깎아 끝난 단면 끝(보스 — 외경 X0 은 빼고).
            // calc-design '넣은 시작·끝점 너머로 넘지 않기'
            val wall = if (face) inward && !toAxis else if (mirror) uB.z > 0 else uB.z < 0
            // 끝 요소가 짧아(노즈R 쯤) 직각 빼는 줄과의 교점이 그 요소의 보정 시작보다 앞이면 끝 블록이 거꾸로 가 간섭 알람이다(FF 5.4.6).
            // 막힌 끝이면 그 요소는 노즈가 벽 앞에서 닿을 길이 없다 — G41/G42 는 그 요소를 빼고 앞 요소 끝에서 벽을 따라 공구 쪽으로 뺀다(노즈가 끝좌표
            // 너머 벽 · 보스로 가지 않음 — 전에는 빈 끝처럼 곧게 빼 벽 속으로 c 와 G40 옮김만큼 갔다, 검증 2026-11-04). 직접 보정은 그대로
            val shortEnd = (((last.end + nB * r - uB * r) - (segs.last().start - tv)) dot uB) < CompFrame.MIN_MOVE
            // 그 요소를 빼려면 앞 요소의 노즈 길 위에 직각 빼기 교점이 MIN_MOVE 이상 남아야 한다(`CompFrame.keepBeforeShortEnd` — 직선은 그 교점, 모서리 R 은 노즈 원과의
            // 정확한 교점. 교점이 R 앞이면 R 도 빼고 그 앞 요소에서). 못 맞추면(짧은 경사 뒤 등) 전처럼 곧게 빼고 SHORT_END_PAST 로 알린다(노즈가 끝좌표 너머로 감)
            if (wall && shortEnd && drawing.segs.size > 1) {
                keepLast = CompFrame.keepBeforeShortEnd(drawing.segs, frameNoses(), uB, nB, side, r, if (cutFirst) 1 else 2)
                    // T1–T2 호는 빼지 않는다(직선 경사가 그렇듯) — 자리가 없으면 곧게 빼고 알린다
                    ?.takeIf { (arcIdx == null || it > arcIdx) && !turnsBack(CompFrame.endDir(drawing.segs[it - 1]), nB) }
                shortPast = keepLast == null
            }
            if (toAxis) e0 += uB * max(0.0, tv dot uB)
            if (toAxis || axisIn) endD = last.end + uB * r
            toAxisEnd = toAxis
            segs[segs.lastIndex] = Line(segs.last().start, e0)
            exit = e0 + leave * c
            when {
                toAxis || axisIn -> {
                    x1D = endD + nB * c
                    awayD = nB
                }
                // 막힌 끝의 첫 빼는 줄은 벽을 따라 공구 쪽으로 c — 내경은 축까지만(`CompFrame.untilAxis` — 구멍 반지름보다 c 가 길면 맞은편 벽)
                wall && !shortEnd -> {
                    x1D = CompFrame.untilAxis(endD, nB, c, inner)
                    awayD = uB * -1.0
                    blockedEnd = true
                }
                keepLast != null -> {
                    endD = drawing.segs[keepLast - 1].end
                    x1D = CompFrame.untilAxis(endD, nB, c, inner)
                    awayD = uB * -1.0
                    blockedEnd = true
                }
                else -> {
                    // 내경 단면을 중심 쪽으로 깎아 끝나면 축을 넘지 않게(넘으면 맞은편 벽) — 모자라면 공구 쪽으로
                    val room = if (face && inner && uB.x < 0) endD.x else Double.MAX_VALUE
                    x1D = endD + uB * min(c, room)
                    // 축까지 G40 자리 규칙 반지름도 안 남으면(가운데 작은 구멍 — 맞은편 끝과 사이가 그 두 배보다 좁다) 축 쪽으로 더 가도 못 맞추니
                    // 공구 쪽(구멍 입구 쪽)으로 비킨다(검증 2026-11-04: 축 너머 맞은편 벽 속까지 갔다)
                    awayD = if (room < max(c, CompFrame.g40Radius(r))) nB else uB
                    freeEnd = true
                }
            }
        } else {
            exit = segs.last().start + nB * c
            x1D = if (convexAt(path.size - 2, cr.lastIndex)) {
                // 끝 깎는 요소가 T1–T2 호면 그 끝 접선으로 곧게
                val ahead = (raw[path.size - 3] as? Arc)?.let(CompFrame::endDir) ?: (path[path.size - 2] - path[path.size - 3])
                // 경사를 늘여 큰 축으로 c(가파른 경사를 Z 로 c 늘이면 X 가 크게 간다). 내경은 축까지만 — 넘으면 맞은편 벽이고, 물러남 띠를 맞추러
                // 되돌아오는 줄이 바깥으로 90° 넘게 꺾여 간섭 알람이다(검증 2026-11-04: 내경 경사가 X1.718 까지 내려가 `X-4.282Z-13.301` → `X-.713`)
                val k = c / max(abs(ahead.z), abs(ahead.x))
                last.start + ahead * (if (inner && ahead.x < 0) min(k, last.start.x / -ahead.x) else k)
            } else {
                last.start + uB * c
            }
            awayD = nB
        }

        // 시작. 좌표를 넣었으면 끝과 같이 인선을 그 좌표에 두고, 시작 요소가 수평이면 그 연장선, 단면이면 공구 쪽에서 Z 로 들어간다.
        // 다만 뒤집어 수평 시작 요소를 팁의 Z 와 반대로 깎으면(외경·구멍을 척 쪽·구멍 바닥 쪽 끝부터 Z+ 로) 시작 좌표 너머는 날 앞쪽 —
        // 척·구멍 바닥이라 연장선으로 넘어 들어가지 않고 공구 쪽에서 시작 좌표에 직각으로(X 로 c) 들어간다(단면 시작과 같은 꼴).
        // 비우면 그 요소는 깎지 않는다(그림·재료만). 진입은 첫 깎는 점에서 Z 로 c 바깥: 첫 꺾임이 날카로운 볼록이면 경사선을 늘인 점
        // (경사각을 따라가 X 가 정확하다), 아니면 시작 요소가 수평이면 그 보정선을 늘인 점, 단면이면 공구 쪽 점.
        // G41/G42 진입점(도면 좌표)도 첫 깎는 점에서 c 지만 노즈는 보정선 위라 팁과 상관없다: 넣은 시작 좌표 너머가 막혔으면(뒤집어 척 쪽 · 구멍 바닥 쪽에서
        // 시작하는 수평, 바깥으로 깎기 시작하는 단면 — 보스, 외경 X0 은 빼고) 공구 쪽에서 직각으로(안쪽 꺾임 — 노즈가 시작 요소와 시작 좌표의 법선에 함께 닿는다),
        // 아니면 첫 깎는 요소와 한 직선으로 너머(공기)에서. 비운 시작은 볼록이면 경사 연장선(큰 축으로 c), 아니면 시작 요소 선 위(노즈가 그 선을 타고 꺾임의 교점 · R 시작에 선다)
        val entry: Pt
        val e0D: Pt
        val startD: Pt // G41/G42 첫 깎는 줄의 끝(첫 깎는 점, 도면 좌표)
        val fromSeg: Int // 직접 보정에서 진입 뒤 따라갈 첫 요소
        var s0: Pt? = null
        val nF = toolNormal(uF, side)
        // 넣은 시작점: 수평 시작 요소의 연장선을 따라 들어가나(뒤집어 팁의 Z 와 반대로 깎는 것은 빼고 — 그때와 단면은 공구 쪽에서 직각으로)
        val along = isHorizontal(uF) && !(flip && uF.z * tv1.z <= 0)
        var blockedStart = false
        var dropFirst: Int? = null // G41/G42 가 짧은 막힌 시작 요소(와 그 뒤 모서리 R)를 빼고 들어갈 요소 번호 — null 이면 빼지 않음
        if (cutFirst) {
            val p = foot(first.start, segs.first().start, uF)
            if (((segs.first().end - p) dot uF) < -EPS || ((first.end - (p - tv)) dot uF) < -EPS) stop(TaperWarning.BAD_LEG)
            segs[0] = Line(p, segs[0].end)
            val inDir = if (along) uF * -1.0 else nF
            s0 = p
            entry = p + inDir * c
            blockedStart = if (isHorizontal(uF)) (if (mirror) uF.z < 0 else uF.z > 0) else (uF.x > 0) != inner && !(!inner && first.start.x <= EPS)
            // 시작 요소가 짧아(노즈R 쯤) 직각 진입의 교점이 그 요소의 보정 끝보다 뒤면 간섭 알람 — 막힌 시작이면 그 요소는 노즈가 벽 앞에서 닿을 길이 없어
            // G41/G42 는 그 요소를 빼고 다음 요소 처음으로 공구 쪽에서 직각으로 들어간다(전에는 한 직선으로 시작 좌표 너머 벽 속에서 들어왔다, 검증 2026-11-04)
            val shortStart = blockedStart && drawing.segs.size > 1 && ((segs[0].end - tv - (first.start + nF * r + uF * r)) dot uF) < CompFrame.MIN_MOVE
            // 다음 요소의 노즈 길 위에 직각 진입 교점이 남아야 한다(`CompFrame.dropBeforeShortStart` — 모서리 R 은 노즈 원과의 정확한 교점, R 뒤면 R 도 빼고).
            // 남길 깎는 요소가 하나는 있어야 한다(끝 쪽에서 뺀 것 빼고)
            val endKeep = keepLast ?: if (cutLast) drawing.segs.size else drawing.segs.size - 1
            if (shortStart) {
                dropFirst = CompFrame.dropBeforeShortStart(drawing.segs, frameNoses(), uF, nF * -1.0, side, r, endKeep - 1)
                    ?.takeIf { (arcIdx == null || it <= arcIdx) && !turnsBack(nF * -1.0, CompFrame.startDir(drawing.segs[it])) }
            }
            // 자리가 없으면(짧은 경사 등) 전처럼 첫 깎는 요소와 한 직선으로 — 노즈가 시작 좌표 너머로 가서 SHORT_END_PAST 로 알린다
            if (shortStart && dropFirst == null) {
                blockedStart = false
                shortPast = true
            }
            val from = dropFirst?.let { drawing.segs[it].start }
            e0D = if (from != null) from + nF * c else if (blockedStart) first.start + nF * c else first.start - uF * c
            startD = from ?: first.start
            fromSeg = 0
        } else {
            val arc1 = raw[1] as? Arc
            if (arc1 != null && convexAt(1, 0)) {
                // 첫 깎는 요소가 T1–T2 호: 경사 연장선 대신 호의 처음 접선을 거꾸로 늘여 큰 축으로 c(접선이 단면에 가까우면 Z 로 c 는 X 가 발산한다).
                // 직접 보정은 보정 호(늘인 교점에서 시작)의 접선, G41/G42 는 도면 호의 접선 — 첫 깎는 줄이 호에 접해 이어져 노즈가 T1 의 법선에 선다
                val tj = CompFrame.startDir(segs[1] as? Arc ?: arc1)
                entry = segs[0].end - tj * (c / max(abs(tj.z), abs(tj.x)))
                val t1 = CompFrame.startDir(arc1)
                e0D = path[1] - t1 * (c / max(abs(t1.z), abs(t1.x)))
            } else if (convexAt(1, 0)) {
                val back = path[1] - path[2]
                entry = segs[0].end + back * (c / abs(back.z))
                // G41/G42 는 끝 쪽처럼 경사를 늘여 큰 축으로 c(가파른 경사를 Z 로 c 늘이면 X 가 축 너머 수백 mm 까지 간다 — 검증 2026-11-04:
                // 내경 89° 경사에서 `G0X-243.627Z20.` 이 축 너머 맞은편 벽을 지났다)
                e0D = path[1] + back * (c / max(abs(back.z), abs(back.x)))
            } else if (isHorizontal(uF)) {
                entry = segs[0].end - uF * c
                e0D = first.end - uF * c
            } else {
                entry = segs[0].end + nF * c
                e0D = first.end - uF * c
            }
            startD = first.end
            fromSeg = 1
        }
        val program = Contour(segs)
        val toSeg = if (cutLast) segs.size else segs.size - 1 // 깎는 요소의 끝(끝 요소를 깎지 않으면 빼고)
        // G41/G42 가 깎는 요소(짧은 막힌 시작 · 끝 요소는 빼고)
        val fromSegG4 = dropFirst ?: fromSeg
        val toSegG4 = keepLast ?: toSeg

        // 접근 줄(직접 보정): 뒤집어 그림 왼쪽(척 쪽 · 구멍 안)에서 시작하면 진입 G00 점이 부품 앞 끝면(넣은 앞 요소 끝이나 T 점 가운데 가장 오른쪽)보다
        // 척 쪽이다. G00 은 X · Z 가 따로 움직일 수 있어(dog-leg) 앞 위치에서 바로 오면 입구 단면 · 면취나 부품 앞을 지날 수 있으므로
        // 먼저 앞 끝면 바깥(Z + c)에서 X 를 맞추고 Z 로만 들어간다(검증 2026-10-27, 현장 안전). 그 X 로 Z 를 따라 오는 동안 노즈가 지나는
        // 도면(진입점에서 노즈 왼쪽 끝부터 오른쪽)보다 바깥이어야 한다 — 아니면(역테이퍼 꼴 · 겹테이퍼 산 너머) 그 바깥 X(+ c)로 Z 를 맞춘 뒤
        // X 로 진입점에 간다. 안 뒤집은 것 · 계산기 역방향은 지금 그대로. 그림의 꺾은선(모서리 R 전)이 모서리 R 윤곽을 바깥에서 감싼다.
        // (G41/G42 는 CompFrame 이 Z20. 에서 같은 일을 한다 — 진입 Z 줄이 도면에 닿으면 바깥 X 에서 Z 를 맞추고 X 로)
        val zFront = (if (start != null) drawn else drawn.drop(1)).maxOf { it.z }
        fun approachTo(e: Pt): List<Pt> {
            if (!approach || !flip || reverse || e.z >= zFront - EPS) return emptyList()
            val from = e.z - tv.z - r
            val xs = raw.flatMap { s ->
                val a = s.start
                val b = s.end
                listOfNotNull(
                    a.x.takeIf { a.z >= from }, b.x.takeIf { b.z >= from },
                    if (!((a.z - from) * (b.z - from) < 0)) null
                    // T1–T2 호는 잘린 자리 X 를 원 위에서(볼록 호는 현보다 바깥이라 현의 보간이 모자란다)
                    else if (s is Arc) arcXAt(s, from)
                    else a.x + (b.x - a.x) * (from - a.z) / (b.z - a.z),
                )
            }
            // 노즈가 도면 바깥에 있으려면 인선 X 가 이 값 바깥(외경 위 · 내경 아래)
            val lim = if (inner) xs.min() + tv.x - r else xs.max() + tv.x + r
            if (if (inner) e.x <= lim + EPS else e.x >= lim - EPS) return listOf(Pt(zFront + c, e.x))
            val xa = if (inner) lim - c else lim + c
            return listOf(Pt(zFront + c, xa), Pt(e.z, xa))
        }
        val approachG40 = approachTo(entry)

        // G41/G42(회사 꼴): 공기 이동을 잴 소재 = 도면 + 앞뒤 요소를 늘인 반직선(너머가 빈 끝은 공기라 빼고) + 넣은 시작 · 끝 좌표 너머가 막힌 쪽 + 내경은 축 거울
        // 반직선은 수평 요소만(외경 · 구멍이 이어진다) — 단면 요소는 그림 5 mm 너머가 어디까지인지 모르고, 늘이면 막힌 벽이 되어 뒤집기 진입을 막는다.
        // 외경 단면을 중심까지 깎는 끝은 축 너머 같은 면을 늘인다
        val mSegs = drawing.segs + listOfNotNull(
            if (isHorizontal(uF) && first.start.x > EPS) FrameMaterial.ray(first.start, uF * -1.0) else null,
            if (!freeEnd && (toAxisEnd || isHorizontal(uB))) FrameMaterial.ray(last.end, uB) else null,
        )
        val mat = FrameMaterial(
            if (inner) mSegs + mSegs.map(FrameMaterial::mirror) else mSegs,
            listOfNotNull(if (blockedStart) first.start to uF * -1.0 else null, if (blockedEnd) last.end to uB else null),
        )
        val front = if (mirror) -1.0 else 1.0
        val outward = if (inner) -1.0 else 1.0
        val ends = drawing.segs.flatMap { listOf(it.start, it.end) }
        val approachG4 = CompFrame.entry(
            mat, side, r, outward, e0D, startD, CompFrame.startDir(drawing.segs[fromSegG4]),
            if (mirror) ends.minOf { it.z } else ends.maxOf { it.z }, front, drawing.segs[fromSegG4] as? Arc,
        )
        val outG4 = CompFrame.exit(
            mat, side, r, outward, if (cutLast) endD else last.start, CompFrame.endDir(drawing.segs[toSegG4 - 1]), x1D, awayD, front,
            lastArc = drawing.segs[toSegG4 - 1] as? Arc,
        )
        val exitsG4 = outG4.points

        val warnings = mutableListOf<TaperWarning>()
        // 꺾임 k(경로 점 path[k])가 오목이고 모서리 R < r — 그 자리에 R 대신 r 이 남는다(볼록 끝은 다 깎인다)
        fun leavesR(k: Int) = (dirIn(k) cross dirOut(k)).let { abs(it) > EPS && (it > 0) != (side == Side.G42) } &&
            cr[k - 1] < r
        val slopes = 1 until path.size - 2 // path[k] – path[k+1] 이 경사(앞뒤 요소 빼고)
        if (slopes.any { r > (path[it + 1] - path[it]).length && (leavesR(it) || leavesR(it + 1)) }) warnings += TaperWarning.NOSE_GT_SLOPE
        // 공구가 원 안쪽(오목): G41 이면 반시계, G42 면 시계 원호
        if (drawing.segs.any { it is Arc && (side == Side.G41) != it.cw && it.radius - r <= EPS }) warnings += TaperWarning.CONCAVE_R_LE_NOSE
        if (tip in 1..4 && againstTool(drawing, drawing.segs.subList(fromSeg, toSeg), side, tip, arcIdx?.let { drawing.segs[it] })) {
            warnings += TaperWarning.AGAINST_TOOL
        }
        // G41/G42 진입 Z 줄은 노즈 중심이 축에 서는 X(`CompFrame.axisLow` — 보정 쪽에 따라 축 너머 r)까지 찾는다 — 그 안은 노즈가 축을 넘지 않아 알리지 않는다
        // (검증 2026-11-05: 내경 역방향 겹테이퍼의 `G0X-1.874Z20.` 이 노즈 중심 반경 0.163 인데 'Z 여유를 줄이세요' 가 떴다)
        val axisG4 = CompFrame.axisLow(side, r, front)
        if ((listOf(entry, exit, endD) + approachG40 + exitsG4).any { it.x < -EPS } || approachG4.points.any { it.x < axisG4 - EPS }) {
            warnings += TaperWarning.ENTRY_PAST_AXIS
        }
        if (shortPast) warnings += TaperWarning.SHORT_END_PAST
        if (keepLast != null || dropFirst != null) warnings += TaperWarning.SHORT_END_SKIPPED
        if (!approachG4.found) warnings += TaperWarning.ENTRY_NOT_FOUND
        if (!outG4.found) warnings += TaperWarning.EXIT_NOT_FOUND

        val ncG40 = NcLines().apply {
            for (p in approachG40) move("G0", p)
            move("G0", entry)
            // 시작점을 넣었으면 시작점′(시작 요소가 수평이면 진입점·시작점′ 이 그 보정선 위라 한 줄로 간다), 비웠으면 첫 꺾임점′
            if (s0 == null) move("G1", segs[0].end) else if (!along) move("G1", s0)
            for (s in segs.subList(fromSeg, toSeg)) if (s is Arc) arc(s) else move("G1", s.end)
            move("G0", exit)
        }.lines
        // 회사 꼴(CompFrame): G41/G42 혼자 → G0 X…Z20. → (X 맞춤) → Z 진입점 → G1 첫 깎는 점 F.1 → 형상(도면 좌표, 모서리 R 은 G2/G3 도면 R) →
        // 빼는 줄 F.3 → G40 혼자 → G0Z200.
        val ncG41G42 = NcLines().apply {
            CompFrame.write(this, side, approachG4.points, startD, {
                for (s in drawing.segs.subList(fromSegG4, toSegG4)) if (s is Arc) arc(s) else move("G1", if (s === last) endD else s.end)
            }, exitsG4, front = front)
        }.lines

        // 꼭짓점 결과: 가공 순서의 k번 모서리. 칸 순서는 그림 순서를 뒤집기만큼, 가공 순서는 그림 순서를 leftFirst 만큼 뒤집은 것이라
        // 둘은 역방향(reverse)일 때만 다르다
        val byPath = cr.indices.map { k ->
            val a = filleted.arc[k]
            if (a == null) {
                val i = filleted.prev[k]
                TaperPoint(path[k + 1], listOf(CompPoint(drawing.segs[i].end, segs[i].end)), null)
            } else {
                val arc = drawing.segs[a] as Arc
                val pa = segs[a]
                TaperPoint(
                    path[k + 1],
                    listOf(CompPoint(arc.start, pa.start), CompPoint(arc.end, pa.end)),
                    CornerArc(arc.radius, if (a in gone) null else (pa as? Arc)?.radius, arc.cw, arc.center),
                )
            }
        }
        return TaperResult(
            TaperSolution(
                points = if (reverse) byPath.reversed() else byPath,
                anglesDeg = slopes.map { slopeDeg(drawn[it], drawn[it + 1]) }.let { if (flip) it.reversed() else it },
                tip = tip,
                side = side,
                entry = entry,
                exit = exit,
                approach = approachG40,
                fromStart = cutFirst,
                toEnd = cutLast,
                ncG41G42 = ncG41G42,
                ncG40 = ncG40,
                drawing = drawing,
                center = Contour(segs.map { it.shifted(tv * -1.0) }),
                program = program,
                arc = arcIdx?.let { k -> (drawing.segs[k] as Arc).let { SlopeArc(k, it.radius, (segs[k] as? Arc)?.radius, it.cw, it.center) } },
            ),
            warnings,
        )
    }

    /** 점 p 를 q 를 지나는 방향 u 의 직선에 내린 발 */
    private fun foot(p: Pt, q: Pt, u: Pt): Pt = q + u * ((p - q) dot u)

    private fun isHorizontal(u: Pt) = abs(u.z) > abs(u.x)

    /** 진행 방향 u 에서 공구 쪽 단위 법선(G41 왼쪽, G42 오른쪽) */
    private fun toolNormal(u: Pt, side: Side) = if (side == Side.G41) u.left() else u.left() * -1.0

    private fun Seg.shifted(v: Pt): Seg = when (this) {
        is Line -> Line(start + v, end + v)
        is Arc -> Arc(start + v, end + v, center + v, cw)
    }

    /** 두 끝점 사이에서 Z · X 가 한쪽으로만 가는 호(T1–T2 호)의 Z = [z] 자리 X */
    private fun arcXAt(a: Arc, z: Double): Double {
        val h = sqrt(max(0.0, a.radius * a.radius - (z - a.center.z) * (z - a.center.z)))
        val lo = min(a.start.x, a.end.x)
        val hi = max(a.start.x, a.end.x)
        return listOf(a.center.x + h, a.center.x - h).minBy { max(0.0, max(lo - it, it - hi)) }.coerceIn(lo, hi)
    }

    /**
     * 바이트와 반대인 곳이 있나. 둘 가운데 하나면 그렇다.
     * - 모양: 도면 요소 가운데 노즈 접점이 팁 쪽 사분면(팁 벡터 ±45°) 밖인 것. 가상 인선은 Z·X 축에 평행한 두 접선이
     *   인선이라는 가정이라, 접점이 그 사분면 밖이면 실제 바이트의 날·여유각이 맞는지 따로 봐야 한다. 진행 방향과 상관없다.
     * - 진행([cut] = 깎는 요소, 가공 순서): 팁의 Z 와 반대로 가며 45° 보다 눕힌 요소를 깎는다. 그런 요소의 접점은 사분면 안에서도
     *   끝날(부절삭날 — 팁 벡터의 X 쪽, 외경 T3 이면 아래를 보는 날) 쪽 반에 있고, 깎이는 자리(접점에서 진행 쪽)가 그 끝날 쪽으로
     *   돈다 — 역방향 깎기(back turning). 예: 뒤집은 외경 형상 3 이 외경을 Z+ 로. 단면(어느 쪽으로든)과 45° 이상 경사는
     *   주절삭날 쪽이라 괜찮다 — 정방향 프로파일이 어깨를 X+ 로 오르는 것, 뒤집은 외경 형상 1 이 45° 면취를 내려와 단면을 중심 쪽으로
     *   깎는 것. 정방향(뒤집지 않은 표준 팁)은 늘 팁의 Z 쪽으로 가므로 이 판정이 없던 때와 같다.
     * 모서리 R 원호는 접점 방향이 양옆 직선 사이에서만 돌므로 현의 방향으로 본다.
     * T1–T2 호([whole])의 진행 판정만 양 끝 접선으로 본다 — 하나라도 누우면 그 호의 누운 쪽 반이 역방향 깎기다.
     */
    private fun againstTool(c: Contour, cut: List<Seg>, side: Side, tip: Int, whole: Seg? = null): Boolean {
        val toTip = NoseComp.tipVector(tip, 1.0).unit()
        val shape = c.segs.any {
            val toTool = toolNormal((it.end - it.start).unit(), side)
            // 접점 방향 = −toTool. 팁 벡터와 45° 넘게 벌어지면 밖
            (toTool dot toTip) > -sqrt(0.5) + 1e-9
        }
        val travel = cut.any {
            val us = if (it === whole && it is Arc) listOf(CompFrame.startDir(it), CompFrame.endDir(it)) else listOf((it.end - it.start).unit())
            // 45° 는 눕힌 것이 아니다(면취 X40 Z0 / X50 Z-5 를 tan 으로 푼 값의 반올림 오차를 넘게 1e-9)
            us.any { u -> abs(u.z) - abs(u.x) > 1e-9 && u.z * toTip.z < 0 }
        }
        return shape || travel
    }

    /** a(오른쪽) → b(왼쪽) 경사의 각(°). [up] 이 있으면 X 가 그쪽으로 변해야 한다 */
    private fun slopeDeg(a: Pt, b: Pt, up: Double? = null): Double {
        val dz = a.z - b.z
        val dx = b.x - a.x
        if (abs(dz) <= EPS || abs(dx) <= EPS) stop(TaperWarning.BAD_ANGLE)
        if (dz < 0 || (up != null && dx * up < 0)) stop(TaperWarning.SHAPE_MISMATCH)
        return atan2(abs(dx), dz) * 180 / PI
    }

    /** T1 에서 T2 까지 왼쪽으로 간 Z 거리(> 0) */
    private fun leftward(z1: Double, z2: Double): Double {
        if (z1 - z2 <= 0) stop(TaperWarning.SHAPE_MISMATCH)
        return z1 - z2
    }

    /** T1 에서 T2 까지 TYPE 방향으로 오르내린 반경 거리(> 0) */
    private fun rise(x1: Double, x2: Double, up: Double): Double {
        val h = up * (x2 - x1) / 2
        if (h <= 0) stop(TaperWarning.SHAPE_MISMATCH)
        return h
    }

    /**
     * p 에서 위(s > 0)·아래로 가는 단면의 끝. xDia = 입력 X(직경), null 이면 반경 [LEG_DEFAULT] 만큼(아래로는 X0 에서 멈춤).
     * 입력이 그 방향에 없거나 X < 0 이면 [TaperWarning.BAD_LEG]
     */
    private fun vertical(p: Pt, s: Double, xDia: Double?): Pt {
        if (xDia == null) return Pt(p.z, if (s > 0) p.x + LEG_DEFAULT else max(p.x - LEG_DEFAULT, 0.0))
        if (xDia < 0 || (xDia / 2 - p.x) * s <= EPS) stop(TaperWarning.BAD_LEG)
        return Pt.ofDia(p.z, xDia)
    }

    /** p 에서 오른쪽(s > 0)·왼쪽으로 가는 수평 요소의 끝. z = 입력 Z, null 이면 [LEG_DEFAULT] 만큼 */
    private fun horizontal(p: Pt, s: Double, z: Double?): Pt {
        if (z == null) return Pt(p.z + s * LEG_DEFAULT, p.x)
        if ((z - p.z) * s <= EPS) stop(TaperWarning.BAD_LEG)
        return Pt(z, p.x)
    }

    private fun checkCommon(r: Double, tip: Int?, zClear: Double, corners: List<Double>, start: Double?, end: Double?) {
        // Z 여유 0 은 된다(진입·후퇴 거리 = 노즈R). 둘 다 0 이면 진입·후퇴 이동이 없어 G41/G42 를 걸고 풀 수 없다
        val ok = r >= 0 && r.isFinite() && zClear >= 0 && zClear.isFinite() && max(r, zClear) > 0 && (tip == null || tip in 0..9) &&
            corners.all { it >= 0 && it.isFinite() } && listOfNotNull(start, end).all { it.isFinite() } &&
            !tooBig(r, zClear, start, end, *corners.toTypedArray())
        if (!ok) stop(TaperWarning.BAD_INPUT)
    }
}

/** 계산을 멈추는 경고. [guarded] 가 받아 결과로 바꾼다 */
private class Stop(val warning: TaperWarning) : Exception()

private fun stop(w: TaperWarning): Nothing = throw Stop(w)

private inline fun guarded(block: () -> TaperResult): TaperResult =
    try {
        block()
    } catch (s: Stop) {
        TaperResult(null, listOf(s.warning))
    }
