package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 보정 구간 밖(공기) 이동을 잴 소재: 도면 요소 + 끝을 늘인 반직선(긴 선분) + 막힌 쪽 반평면(넣은 끝좌표 너머가 척 · 구멍 바닥 · 보스) + 그린 구간의 소재([fill]).
 * [walls] 는 (점, 단위 방향) — (q − 점)·방향 > 0 인 곳이 소재. [fill] 을 주면 [segs] 에 도면과 그 축 거울이 들어 있어야 한다(소재 경계)
 */
internal class FrameMaterial(val segs: List<Seg>, val walls: List<Pair<Pt, Pt>> = emptyList(), val fill: FrameFill? = null) {
    /** 거리를 잴 경계: 도면 · 반직선 + 닫은 도면의 닫는 선 */
    private val edges = if (fill == null) segs else segs + fill.stubs

    /** 점에서 소재까지 거리(소재 안이면 0) */
    fun dist(p: Pt): Double {
        if (fill != null && fill.inside(p)) return 0.0
        var d = edges.minOfOrNull { distTo(it, p) } ?: Double.POSITIVE_INFINITY
        for ((at, dir) in walls) d = min(d, max(0.0, -((p - at) dot dir)))
        return d
    }

    /** 선분에서 소재까지 거리(만나면 0). 닫은 도면 속을 지나면 그 경계를 가로지르거나 끝점이 속이다 */
    fun dist(s: Line): Double {
        if (fill != null && (fill.inside(s.start) || fill.inside(s.end))) return 0.0
        var d = edges.minOfOrNull { segGap(s, it).first } ?: Double.POSITIVE_INFINITY
        for ((at, dir) in walls) d = min(d, max(0.0, min(-((s.start - at) dot dir), -((s.end - at) dot dir))))
        return d
    }

    /**
     * Z 구간 [z0, z1] 에 걸친 도면(축 거울 · X < 0 은 빼고)의 가장 큰([max]) · 작은 X. 걸친 것이 없으면 −∞ · +∞.
     * 원호는 양 끝과 사분점(구간 안)만 본다 — 진입 Z 줄이 소재 속을 지나는지 가리는 데 쓴다(닿음은 [dist] 가 본다)
     */
    fun extremeX(z0: Double, z1: Double, max: Boolean): Double {
        var best = if (max) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        fun see(x: Double) {
            if (x < -EPS) return
            best = if (max) kotlin.math.max(best, x) else min(best, x)
        }
        for (s in segs) {
            when (s) {
                is Line -> {
                    val a = s.start
                    val b = s.end
                    val lo = kotlin.math.max(z0, min(a.z, b.z))
                    val hi = min(z1, kotlin.math.max(a.z, b.z))
                    if (lo > hi + EPS) continue
                    if (abs(b.z - a.z) <= EPS) {
                        see(a.x)
                        see(b.x)
                    } else {
                        for (z in listOf(lo, hi)) see(a.x + (b.x - a.x) * (z - a.z) / (b.z - a.z))
                    }
                }
                is Arc -> {
                    val pts = mutableListOf(s.start, s.end)
                    for (k in 0 until 4) {
                        val q = s.center + Pt(kotlin.math.cos(k * kotlin.math.PI / 2), kotlin.math.sin(k * kotlin.math.PI / 2)) * s.radius
                        if (onArc(s, q)) pts += q
                    }
                    for (q in pts) if (q.z in z0..z1) see(q.x)
                }
            }
        }
        return best
    }

    companion object {
        /** 반직선을 대신하는 선분 길이(mm) — 크기 한도의 두 배 */
        private const val RAY = 2 * MAX_MM

        /** 반직선 하나(시작점에서 단위 방향 [dir] 으로) */
        fun ray(from: Pt, dir: Pt): Line = Line(from, from + dir * RAY)

        /** 축 거울(맞은편 — 회전체라 그 자리에도 소재) — 빼는 줄 · G40 옮김이 축 너머 부품 반대쪽을 파지 않게 */
        fun mirror(s: Seg): Seg = when (s) {
            is Line -> Line(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x))
            is Arc -> Arc(Pt(s.start.z, -s.start.x), Pt(s.end.z, -s.end.x), Pt(s.center.z, -s.center.x), !s.cw)
        }
    }
}

/**
 * 그린 구간의 소재(닫은 도면) — 외경은 [drawing] 과 두 끝에서 축으로 닫는 선 사이, 내경은 [drawing] 과 두 끝에서 바깥(먼 X)으로 닫는 선 사이. 회전체라 축 거울도.
 * 선분 거리만으로는 그린 선에서 떨어진 소재 속(외경 호 밑 · 내경 벽 속)을 모른다(검증 2026-11-04: 외경 3점호 끝 접선이 축 쪽으로 꺾이면 `G0Z200.` 이
 * 그린 호 밑 부품 속을 지났고, 끝이 +Z 로 되돌아오는 호에서는 형식 C 의 G40 옮김이 끝 접선 안쪽 소재로 들어갔다).
 * 끝을 닫는 선은 X 로 곧게(그린 구간 밑만 — 끝 너머는 모른다). 다만 끝 접선이 그린 구간 쪽(Z 로 되돌아옴)으로 꺾여 닫는 쪽으로 가파르게(X 성분 [STEEP] 넘게)
 * 가면 그 접선을 따라 닫는다 — 곧게 닫은 선은 그때 공구 쪽이라 끝 너머로 곧게 빼는 길(사용자 결정 2026-10-29 ③)을 막는다. 두 닫는 선이 만나면(되돌아오는
 * 끝 접선이 시작 쪽 닫는 선에 닿음) 그 점에서 닫는다. 닫는 선이 도면을 가로지르면(Z 로 되돌아오는 윤곽) 곧게 닫아 보고, 그래도 가로지르면 안팎을 가릴 수 없어
 * [valid] 가 false 다(부르는 쪽은 쓰지 않는다)
 */
internal class FrameFill(val drawing: List<Seg>, val inner: Boolean) {
    /** 두 끝을 닫는 선(축 거울 빼고). 내경은 바깥으로 [FAR] 까지 */
    private val closers: List<Line>

    /** 닫는 선과 그 축 거울 — 소재 경계라 거리를 잰다 */
    val stubs: List<Line>

    val valid: Boolean

    init {
        val first = drawing.first().start
        val last = drawing.last().end
        val pick = closed(first, last, true) ?: closed(first, last, false)
        valid = pick != null
        closers = pick ?: emptyList()
        stubs = closers + closers.map { FrameMaterial.mirror(it) as Line }
    }

    /**
     * 두 끝의 닫는 선(끝 쪽 사슬 + 시작 쪽 사슬). [steep] = 가파르게 되돌아오는 끝 접선을 따라 닫을지. 닫는 선이 도면을 가로지르면 null.
     * 두 사슬이 서로 만나면(되돌아오는 끝 접선이 시작 쪽 닫는 선에 닿음) 그 점에서 잘라 거기서 닫는다
     */
    private fun closed(first: Pt, last: Pt, steep: Boolean): List<Line>? {
        val target = if (inner) FAR else 0.0
        fun close(p: Pt, u: Pt, back: Double): List<Line> {
            val toward = if (target > p.x) 1.0 else -1.0
            if (abs(target - p.x) <= EPS) return emptyList()
            if (steep && u.x * toward > STEEP && u.z * back > 1e-9) {
                // 접선을 따라 닫는 쪽 높이까지(내경은 그 뒤 곧게 바깥으로)
                val reach = if (inner) min(FAR, drawingTop() + 3.0) else 0.0
                val a = p + u * ((reach - p.x) / u.x)
                return listOfNotNull(Line(p, a), if (inner) Line(a, Pt(a.z, FAR)) else null)
            }
            return listOf(Line(p, Pt(p.z, target)))
        }
        var end = close(last, CompFrame.endDir(drawing.last()), if (first.z > last.z) 1.0 else -1.0)
        var start = close(first, CompFrame.startDir(drawing.first()) * -1.0, if (last.z > first.z) 1.0 else -1.0)
        fun trimmed(l: Line): Line {
            val d = l.end - l.start
            val k = min(1e-6, d.length / 4)
            return Line(l.start + d.unit() * k, l.end - d.unit() * k)
        }
        if ((end + start).any { c -> drawing.any { crossing(trimmed(c), it) != null } }) return null
        // 끝 쪽 사슬을 따라 가며 시작 쪽 사슬과 처음 만나는 점
        for ((i, a) in end.withIndex()) {
            val hit = start.withIndex().mapNotNull { (j, b) -> crossing(trimmed(a), trimmed(b))?.let { Triple(j, it, ((it - a.start) dot (a.end - a.start))) } }
                .minByOrNull { it.third } ?: continue
            val (j, x, _) = hit
            end = end.take(i) + Line(a.start, x)
            start = start.take(j) + Line(start[j].start, x)
            break
        }
        return (end + start).filter { (it.end - it.start).length > EPS }
    }

    private fun drawingTop(): Double = drawing.maxOf {
        when (it) {
            is Line -> max(it.start.x, it.end.x)
            is Arc -> if (onArc(it, it.center + Pt(0.0, it.radius))) it.center.x + it.radius else max(it.start.x, it.end.x)
        }
    }

    /**
     * 점이 닫은 도면 속인가 — 세로선(Z = 점의 Z)이 경계를 몇 번 가로지르나(홀수면 속). 외경은 점 위로(닫는 축 선은 늘 아래), 내경은 점에서 축까지(닫는 먼 선은 늘 위).
     * 축 너머 점은 거울로. 세로선이 꼭짓점을 지나 두 요소에서 함께 세지 않게 Z 를 아주 조금 비킨다
     */
    fun inside(p: Pt): Boolean {
        if (!valid) return false
        val x = abs(p.x)
        val z = p.z + Z_NUDGE
        var n = 0
        for (s in drawing) n += hits(s, z, x)
        for (s in closers) n += hits(s, z, x)
        return n % 2 == 1
    }

    /** 요소가 세로선 Z = [z] 와 만나는 점 가운데 셀 것의 수: 외경은 X > [x], 내경은 0 ≤ X < [x] */
    private fun hits(s: Seg, z: Double, x: Double): Int {
        fun counts(xx: Double) = if (inner) xx < x && xx >= 0 else xx > x
        return when (s) {
            is Line -> {
                val a = s.start
                val b = s.end
                if ((a.z - z) * (b.z - z) >= 0) 0 else if (counts(a.x + (b.x - a.x) * (z - a.z) / (b.z - a.z))) 1 else 0
            }
            is Arc -> {
                val dz = z - s.center.z
                val rr = s.radius
                if (abs(dz) >= rr) return 0
                val h = sqrt(rr * rr - dz * dz)
                listOf(s.center.x + h, s.center.x - h).count { xx -> counts(xx) && strictlyOn(s, Pt(z, xx)) }
            }
        }
    }

    /** 원호 위(끝점 여유 없이) — 이웃 요소와 같은 점을 두 번 세지 않게 */
    private fun strictlyOn(a: Arc, q: Pt): Boolean {
        val along = wrap2Pi((if (a.cw) -1 else 1) * (angleOf(q - a.center) - angleOf(a.start - a.center)))
        return along < a.sweepRad()
    }

    private companion object {
        /** 내경 닫는 선의 바깥 끝(mm) */
        const val FAR = 2 * MAX_MM

        /** 끝 접선을 따라 닫는 기울기(닫는 쪽 X 성분, sin 11.5°) */
        const val STEEP = 0.2

        /** 세로선을 비키는 Z(mm) */
        const val Z_NUDGE = 1.234567e-9
    }
}

/**
 * 회사 꼴 G41/G42 글의 틀(계획 단계 4, 사용자 결정 2026-10-29 — 회사 기계 = 보정 시작 · 취소 형식 C):
 * `G42`(혼자 줄) / `G0X{X}Z20.` / (`X…` — 바깥에서 X 를 맞출 때) / `Z{진입 Z}` / `G1 … F.1`(첫 깎는 줄) / 형상(도면 좌표) / 빼는 줄 `… F.3`(보정 켠 채 공기로) /
 * `G40`(혼자 줄) / `G0Z200.`.
 *
 * 보정 줄이 둘 다 이동 없는 혼자 줄이라 형식 C 에서는 그 자리에서, 형식 A · B 에서는 앞뒤 공기 이동에서 노즈가 옮겨진다 — 어느 쪽이든 공기 속이다.
 * Z 줄부터 마지막 빼는 줄까지는 두 형식 모두 보정 중 블록이라(형식 A 는 `G0X…Z20.` 이 시작 블록) **깎는 줄의 노즈는 형식 · 팁 번호와 상관없다**.
 * 팁 번호가 들어가는 곳은 공기 중 둘뿐이다: `G42` 줄(공구 교환 자리)과 `G40` 줄 · 그 뒤 `G0Z200.`. 그래서
 * - **G40 자리 규칙**: 마지막 빼는 줄 끝(명령점)에서 [g40Radius] = (1 + √2)·r + [G40_MARGIN] 안에 소재가 없어야 한다 — 노즈 중심은 그 점에서
 *   r(보정 법선) · |팁 벡터| ≤ √2·r 안에서만 옮겨지므로 팁 1~9 모두 도면을 파지 않는다. 모자라면 벽 · 도면에서 멀어지는 빼는 줄을 하나 더.
 * - **물러남**: `G0Z200.` 은 Z 만 움직인다 — 그 점에서 +Z 로 가는 띠(반지름 같은 값)에 소재가 없어야 한다. 모자라면 바깥(외경 +X · 내경 −X)으로 X 를 맞추는 빼는 줄.
 * - 진입 Z 줄 · X 맞춤 줄 · 첫 깎는 줄의 노즈는 보정선 위(도면에서 r) — 도면에 닿지 않는지 재고, 닿으면 바깥 X 에서 Z 를 맞춘 뒤 X 로 들어간다.
 * - 첫 깎는 줄 · 빼는 줄의 보정 이동(노즈가 움직이는 길이)은 [MIN_MOVE] 이상 — 0.001 반올림으로 거꾸로 가 간섭 검사(FF 5.4.6)에 걸리지 않게.
 * 노즈 자리는 FANUC 보정 중 규칙(안쪽 = 두 보정선의 교점, 바깥 90° 이하 = 늘인 교점, 한 직선 = 법선 — FF 표 5.4.1(a) 173쪽)으로 셈한다 — 원호와의 꺾임은 그 보정 원과의 교점([junction]).
 * 바깥으로 90° 넘게 꺾는 공기 줄은 짜지 않는다([acuteOuter] — 제어기는 꼭짓점을 도는 토막을 끼우는데 그 모양을 출처로 확인하지 못했다, 시뮬레이터 COMP_ACUTE_OUTER).
 * 진입 길을 못 찾으면 [Approach.found] = false(부르는 쪽 경고 ENTRY_NOT_FOUND), 빼는 길을 못 찾으면 [Exit.found] = false(EXIT_NOT_FOUND).
 */
internal object CompFrame {
    /** 진입 Z(공구를 바꾼 뒤 늘 가는 자리) */
    const val APPROACH_Z = 20.0

    /** 물러남 Z */
    const val RETRACT_Z = 200.0

    /** 깎는 이송(mm/rev) — 첫 깎는 줄 */
    const val FEED_CUT = 0.1

    /** 빼는 이송(mm/rev) — 보정 켠 채 공기로 빼는 줄 */
    const val FEED_EXIT = 0.3

    /** G40 자리 규칙의 여유(mm) — NC 0.001 반올림 · 계산 오차보다 넉넉히 */
    const val G40_MARGIN = 0.02

    /** 첫 깎는 줄 · 빼는 줄에서 노즈가 보정선을 따라 움직이는 길이의 최소(mm) */
    const val MIN_MOVE = 0.05

    /** 노즈가 도면에 붙어 지나가는 것(같은 선 위)을 닿음으로 보지 않는 허용(mm) */
    private const val TOUCH_TOL = 1e-7

    /**
     * 진입 줄끼리 꺾임이 거꾸로(90° 넘음)인지 가리는 방향 내적의 허용 — 0.006° 쯤. 3점호는 0.0001 로 쓴 점에서 원을 다시 구해 시작 접선이 축에서
     * 몇 e-6 rad 비낄 수 있다. 1e-9 로 가리면 그 비낌만으로 구멍 안에서 X 를 맞추는 진입을 버리고 찾지 못한 곧은 Z 줄로 떨어졌다
     * (뒤보링 X50 Z−10 → X30 Z0 R10: `G0X52.Z20.` → `Z-10.` 이 입구 턱을 지나감 — 검증 2026-11-04)
     */
    private const val DIR_TOL = 1e-4

    /** 빼는 줄 뒤 줄의 노즈 길이 도면에 들어가도 되는 깊이(mm) — 축에서 끝나는 오목 원호 뒤 축 너머로 조금 지날 때의 맞은편 구면 파임 */
    private const val PLAN_TOL = 1e-4

    /** 빼는 줄 길이를 찾는 한도(mm) */
    private const val SEARCH_MM = 200.0

    /** 바깥 X 를 찾는 걸음 수 한도 */
    private const val SEARCH_STEPS = 400

    /** G40 자리로 비키는 줄을 늘여 보는 걸음 수 한도 */
    private const val AWAY_STEPS = 40

    /** G40 자리 규칙 · 물러남 띠의 반지름(mm) */
    fun g40Radius(r: Double) = (1 + sqrt(2.0)) * r + G40_MARGIN

    /** 진입 Z — 도면(나사는 시작 · 끝 Z)의 가장 큰 Z 가 [top]. 늘 [APPROACH_Z], Z20 가까이까지 오면(드묾) 그만큼 더 멀리서(올림 + 20) */
    fun approachZ(top: Double): Double = if (top < APPROACH_Z - 5.0) APPROACH_Z else ceil(top) + APPROACH_Z

    /** 요소 처음의 진행 방향(단위) — 원호는 그 점의 접선 */
    fun startDir(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> (s.start - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
    }

    /** 요소 끝의 진행 방향(단위) */
    fun endDir(s: Seg): Pt = when (s) {
        is Line -> (s.end - s.start).unit()
        is Arc -> (s.end - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
    }

    /** 진행 방향 u 에서 공구 쪽 단위 법선(G41 왼쪽, G42 오른쪽) */
    fun normal(u: Pt, side: Side): Pt = if (side == Side.G41) u.left() else u.left() * -1.0

    /**
     * 보정 중 꺾임(진행 방향 [uA] → [uB])이 바깥으로 90° 넘게(제자리로 되돌아감 포함) 꺾이나 — 시뮬레이터 `GComp.junction` 의 COMP_ACUTE_OUTER 와 같은 가름
     * (안쪽 = 공구 쪽으로 도는 꺾임, G41 이면 왼쪽)
     */
    fun acuteOuter(uA: Pt, uB: Pt, side: Side): Boolean {
        // 길이 0 인 줄(방향 NaN)은 꺾임이 없다
        if (!((uA dot uB) < 0)) return false
        val cr = uA cross uB
        return abs(cr) < 1e-9 || ((cr > 0) != (side == Side.G41))
    }

    /**
     * [p] 에서 [dir] 로 [len] 간 점. 내경([inner])이 축 쪽(−X)으로 가면 축까지만 — 넘으면 맞은편 벽(회전체라 축 너머도 부품)이다.
     * 막힌 끝의 첫 빼는 줄(벽을 따라 공구 쪽 c)이 구멍 반지름보다 길면 노즈가 맞은편 벽을 팠다(검증 2026-11-05: 내경 TYPE 2 공 뒤 구멍 Ø3.478 에서 c = 5 —
     * `G1X-6.522F.3` 이 맞은편 모서리 R 을 1.4 팠다)
     */
    fun untilAxis(p: Pt, dir: Pt, len: Double, inner: Boolean): Pt =
        p + dir * (if (inner && dir.x < -1e-12) min(len, max(0.0, p.x) / -dir.x) else len)

    /**
     * 진입 Z 줄을 축 쪽으로 찾을 때의 X 한도 — 노즈 중심이 축에 서는 명령 X. Z 줄 노즈가 +X 쪽으로 밀리는 보정 쪽이면 축 너머 r, 아니면 축.
     * [front] < 0 = 화면 역방향(Z 거울 — 진입 Z 줄이 +Z 로 가 보정 쪽이 바뀐다). 이 한도 안의 진입점은 축 너머가 아니다(노즈가 축을 넘지 않음)
     */
    fun axisLow(side: Side, r: Double, front: Double = 1.0): Double = min(0.0, -normal(Pt(-front, 0.0), side).x * r)

    /** 보정 중 꺾임 c(직선 uA → 직선 uB)의 노즈 중심: 두 보정선의 교점(바깥이면 늘인 교점, 한 직선이면 법선) */
    fun corner(c: Pt, uA: Pt, uB: Pt, side: Side, r: Double): Pt {
        val a = normal(uA, side)
        val b = normal(uB, side)
        val k = 1 + (a dot b)
        // 거꾸로 꺾임(180°)은 교점이 없다 — 부르는 쪽이 만들지 않는다. 그래도 오면 법선
        return if (k < 1e-9) c + b * r else c + (a + b) * (r / k)
    }

    /**
     * 보정 중 꺾임 c 의 노즈 중심 — 앞 요소 끝 접선 [uA] · 뒤 요소 처음 접선 [uB]. 원호 요소([arcA] = 앞 · [arcB] = 뒤 — 하나만)는 접선의 보정선 대신
     * 그 보정 원(같은 중심, 공구가 원 밖이면 R + r · 안이면 R − r)과의 교점: 두 보정선의 교점 가운데 c + (nA + nB)·r/2 에 가까운 것(안쪽 · 바깥 90° 이하
     * 모두 — FF 표 5.4.1(a), 시뮬레이터 `GComp.junction` 과 같은 규칙). 접선으로 어림하면 원호 꺾임의 노즈를 틀리게 봐 진입을 못 찾고 곧은 Z 줄로
     * 떨어졌다(검증 2026-11-04 — 짧은 막힌 시작 뒤 모서리 R). 거의 접해서 이어지면(0.001 반올림 자국쯤 — `GComp` 와 같은 각) 법선.
     * 교점이 없으면(보정선끼리 못 만남 — 제어기 간섭 알람) null. 오목 R ≤ r(보정 원이 없음 — 도면 알람 꼴, 앱 경고 CONCAVE_R_LE_NOSE)은 전처럼 접선으로 어림한다
     * (그 꼴은 진입 · 빼기와 상관없이 제어기가 멈추니 진입 길 못 찾음으로 보지 않는다)
     */
    fun junction(c: Pt, uA: Pt, uB: Pt, side: Side, r: Double, arcA: Arc? = null, arcB: Arc? = null): Pt? {
        val arc = arcA ?: arcB ?: return corner(c, uA, uB, side, r)
        val na = normal(uA, side)
        val nb = normal(uB, side)
        if (abs(uA cross uB) < min(1e-3, 5e-4 / max(r, 1e-9)) && (uA dot uB) > 0) return c + na * r
        val n = if (arc === arcA) na else nb
        val rho = if ((n dot (arc.center - c)) > 0) arc.radius - r else arc.radius + r
        if (rho <= EPS) return corner(c, uA, uB, side, r)
        val (nl, ul) = if (arc === arcA) nb to uB else na to uA
        val guess = c + (na + nb) * (r / 2)
        return Intersect.lineCircle(c + nl * r, ul, arc.center, rho).minByOrNull { (it - guess).length }
    }

    /**
     * 짧은 막힌 끝 · 시작 요소를 빼고 이웃 요소 끝(처음)에서 공구 쪽 직각으로 빼거나(들어가거나) 할 때, 꺾임의 노즈(이웃 요소의 노즈 중심 길 [nose] 와
     * 빼는(들어가는) 줄의 보정선 — [at] 을 지나는 방향 [dir] 의 줄을 공구 쪽으로 r 민 선 — 의 교점)가 그 길 위에 [MIN_MOVE] 이상 남는가. 아니면 그 이웃 블록이
     * 거꾸로 가 간섭 알람이다(짧은 경사 뒤). [fromStart] = 길의 처음에서 잰다(빼기 — 이웃 블록의 노즈가 처음에서 교점까지 간다), 아니면 끝에서(들어가기).
     * 이웃이 원호(모서리 R)면 노즈 원과의 정확한 교점([junction] 과 같은 고름)이 그 원호 위에 있고(원호 밖 — 늘인 원 위 — 이면 false), 거기서 빼는 줄이 원을 떠나야
     * (들어가는 줄은 원으로 와야) 한다.
     * 전에는 원호 이웃을 보지 않고 곧게 뺐다(노즈가 벽 속으로 — 검증 2026-11-04 '짧은 막힌 끝·시작' 꼬리표 라운드 47건)
     */
    fun roomOn(nose: Seg, fromStart: Boolean, at: Pt, dir: Pt, side: Side, r: Double): Boolean {
        val off = at + normal(dir, side) * r
        return when (nose) {
            is Line -> {
                val d = nose.end - nose.start
                val len = d.length
                if (len <= EPS) return false
                val q = Intersect.lineLine(off, dir, nose.start, d) ?: return false
                val t = ((q - nose.start) dot d) / (len * len)
                t in 0.0..1.0 && (if (fromStart) t else 1 - t) * len >= MIN_MOVE
            }
            is Arc -> {
                val rho = nose.radius
                if (rho <= EPS) return false
                // 꺾임 at 에서 원호의 접선(노즈 원과 같은 중심)
                val tAt = (at - nose.center).unit().left() * (if (nose.cw) -1.0 else 1.0)
                val guess = at + (normal(tAt, side) + normal(dir, side)) * (r / 2)
                val q = Intersect.lineCircle(off, dir, nose.center, rho).minByOrNull { (it - guess).length } ?: return false
                // 그 교점에서 노즈가 원을 떠나는(빼기) · 원으로 오는(들어가기) 쪽이어야 한다 — 볼록(공구가 원 밖)은 빼는 줄이 원 밖으로, 오목(안)은 원 안으로.
                // 거꾸로면 고른 교점이 빼는(들어가는) 줄이 원을 가로지르는 첫 점이라 그 줄 노즈가 원호를 판다(구의 아래 옆면 끝에서 위로 빼기 등)
                val convex = (normal(tAt, side) dot (at - nose.center)) > 0
                if (((q - nose.center) dot dir) * (if (convex == fromStart) 1 else -1) < -1e-9) return false
                val sweep = nose.sweepRad()
                val along = wrap2Pi((if (nose.cw) -1 else 1) * (angleOf(q - nose.center) - angleOf(nose.start - nose.center)))
                along <= sweep + 1e-12 && (if (fromStart) along else sweep - along) * rho >= MIN_MOVE
            }
        }
    }

    /**
     * 짧은 막힌 끝: G41/G42 가 남길 요소 수(drawing 앞에서부터). 끝 요소를 빼고 그 앞 요소 끝에서 [dir](끝 요소의 공구 쪽 법선 — 벽을 따라)로 뺄 때
     * 그 꺾임이 앞 요소의 노즈 길([noses] — drawing 과 같은 번호) 위에 남으면([roomOn]) 그 수. 앞 요소가 모서리 R(원호)인데 안 남으면(작은 R · 볼록 R 의
     * 짧은 호 — 교점이 원호 앞) 그 원호도 빼고 그 앞에서 다시 — 빼는 줄은 늘 [dir] 방향이라 노즈는 남긴 끝에서 r 뒤(벽에서 그보다 더)에 선다.
     * 뺀 요소가 그 빼는 줄 뒤(끝 방향 [along] 으로 남긴 끝보다 뒤)로 나오면(90° 넘게 꺾인 R) 노즈 길이 그것을 팔 수 있어 그만둔다.
     * 남길 요소가 [minKeep] 개보다 적어지거나 직선에서 못 맞추면 null(빼지 못함 — 부르는 쪽이 곧게 빼고 경고)
     */
    fun keepBeforeShortEnd(drawing: List<Seg>, noses: List<Seg>, along: Pt, dir: Pt, side: Side, r: Double, minKeep: Int = 1): Int? {
        var k = drawing.size - 1
        while (k >= minKeep) {
            val at = drawing[k - 1].end
            if (drawing.subList(k, drawing.size).any { lowest(it, along) < (at dot along) - 1e-9 }) return null
            if (roomOn(noses[k - 1], true, at, dir, side, r)) return k
            if (drawing[k - 1] !is Arc) return null
            k--
        }
        return null
    }

    /**
     * 짧은 막힌 시작: G41/G42 가 뺄 앞 요소 수 — [keepBeforeShortEnd] 를 거꾸로(시작 요소를 빼고 다음 요소 처음으로 [dir] — 시작 요소의 공구 쪽에서 직각 —
     * 진입, 다음이 모서리 R 인데 교점이 안 남으면 그것도 빼고). [along] = 시작 요소 방향. 뺄 요소가 [maxDrop] 개를 넘거나 직선에서 못 맞추면 null
     */
    fun dropBeforeShortStart(drawing: List<Seg>, noses: List<Seg>, along: Pt, dir: Pt, side: Side, r: Double, maxDrop: Int = drawing.size - 1): Int? {
        var k = 1
        while (k <= maxDrop) {
            val at = drawing[k].start
            if (drawing.subList(0, k).any { lowest(it, along * -1.0) < -(at dot along) - 1e-9 }) return null
            if (roomOn(noses[k], false, at, dir, side, r)) return k
            if (drawing[k] !is Arc) return null
            k++
        }
        return null
    }

    /** 요소 위 점 p 의 p·[u] 가운데 가장 작은 값(원호는 그 방향 반대쪽 끝점 — 원호 위면) */
    private fun lowest(s: Seg, u: Pt): Double {
        val ends = min(s.start dot u, s.end dot u)
        if (s !is Arc) return ends
        val q = s.center - u * s.radius
        return if (onArc(s, q)) min(ends, q dot u) else ends
    }

    /**
     * 진입 명령점: [(zApp, X)] + (바깥에서 X 를 맞추면 [(e.z, X)]) + [e]. 그 뒤 첫 깎는 줄이 e → [s], 첫 깎는 요소의 처음 접선이 [u1].
     * [e0] = 첫 깎는 점에서 c 떨어진 진입점(부르는 쪽이 정한다 — 첫 깎는 요소와 한 직선이거나 그 앞 요소 선 위, 넣은 시작에 직각이면 공구 쪽),
     * [outward] = 도면 바깥 X 쪽(외경 +1 · 내경 −1), [zTop] = 도면의 부품 앞 쪽 끝 Z([front] > 0 이면 가장 큰 Z, < 0 이면 가장 작은 Z),
     * [front] = 부품 앞 Z 쪽(화면 역방향의 거울 풀이는 −1 — 진입 · 물러남이 계산기 −Z 쪽).
     * 첫 깎는 줄의 노즈 이동이 [MIN_MOVE] 보다 짧으면 진입점을 그 줄을 따라 더 물린다. [firstArc] = 첫 깎는 요소가 원호면 그것(첫 깎는 점의 꺾임 노즈를
     * 그 보정 원과의 교점으로 — [junction]).
     * [clear] = 진입 급송(G0 줄들)의 노즈가 소재에서 더 떨어져야 하는 거리(mm) — 급송 노즈 중심이 소재에서 r + clear 이상인 길만 받는다(첫 깎는 줄은 그대로 r).
     * 기본 0 = 닿지만 않으면 된다. 뒤집은 라운드는 척 쪽 · 구멍 안에서 시작해 급송이 깎을 면 위를 지나므로 Z 여유를 준다(검증 2026-11-11)
     * [lastResort] = false 면 세 길을 다 못 찾았을 때 마지막 길(곧장 · 바깥에서 X 맞춤 — Z 줄 꺾임으로 물린 진입점)을 '찾음' 으로 내지 않는다(found = false).
     * 그 길은 X 로 오면서 Z 줄 꺾임으로 진입점을 물려 완만한 경사에서는 진입점이 경사 연장선을 따라 수십 mm 척 쪽으로 간다 — 뒤집은 테이퍼가 진입 후보를 고를 때
     * (2026-11-12 통합 고침). 기본 true = 전과 같다
     */
    fun entry(
        mat: FrameMaterial, side: Side, r: Double, outward: Double, e0: Pt, s: Pt, u1: Pt, zTop: Double, front: Double = 1.0, firstArc: Arc? = null,
        clear: Double = 0.0, lastResort: Boolean = true,
    ): Approach =
        if (front > 0) entry0(mat, side, r, outward, e0, s, u1, zTop, firstArc, clear, lastResort)
        else entry0(mat.flipped(), side.other(), r, outward, fz(e0), fz(s), fz(u1), -zTop, firstArc?.flippedZ() as Arc?, clear, lastResort)
            .let { a -> Approach(a.points.map(::fz), a.found) }

    /**
     * 진입 명령점들([entry])과 도면을 파지 않는 길을 찾았는지. [found] = false 면 어느 길도 맞추지 못해 곧장 내려간 길이 도면에 닿거나 판다(또는 찾은 길이
     * 직각 진입의 두 축 G0 뿐인데 급송 속도 비가 크게 치우치면 그 꺾인 길이 닿는다) — 부르는 쪽이 화면 경고(ENTRY_NOT_FOUND)로 알린다(전에는 경고 없이 곧은 Z 줄을
     * 냈다 — 검증 2026-11-04 에서 11 mm 판 적이 있다)
     */
    class Approach(val points: List<Pt>, val found: Boolean)

    private fun entry0(
        mat: FrameMaterial, side: Side, r: Double, outward: Double, e0: Pt, s: Pt, u1: Pt, zTop: Double, firstArc: Arc?, clear: Double, lastResort: Boolean,
    ): Approach {
        val uG = (s - e0).unit()
        val zDir = Pt(-1.0, 0.0)
        // 첫 깎는 점의 꺾임 노즈 — 없으면(보정선끼리 못 만남) 이 첫 깎는 줄로는 간섭 알람이라 어느 길도 안 된다
        val sNose = junction(s, uG, u1, side, r, arcB = firstArc)
        fun moved(prev: Pt, e: Pt) = ((sNose ?: e) - corner(e, prev, uG, side, r)) dot uG
        fun shifted(prev: Pt): Pt = moved(prev, e0).let { if (it >= MIN_MOVE || sNose == null) e0 else e0 - uG * (MIN_MOVE - it) }
        // 급송 노즈 길(소재에서 r + clear 이상) · 첫 깎는 줄의 노즈 길(r 이상)
        fun ok(a: Pt, b: Pt, tol: Double = TOUCH_TOL) = mat.dist(Line(a, b)) >= r + clear - tol
        fun g1Ok(prev: Pt, e: Pt, tol: Double = TOUCH_TOL) = sNose != null && mat.dist(Line(corner(e, prev, uG, side, r), sNose)) >= r - tol
        val zApp = approachZ(max(zTop, e0.z))
        // Z 줄 노즈: 시작은 그 줄의 법선, 끝은 다음 줄과의 꺾임
        // 노즈가 도면 선에 안 닿아도 소재 속을 지날 수 있다(외경 밑 · 내경 벽 너머) — 그 Z 구간의 도면보다 바깥(외경 위 · 내경 축 쪽)이어야 한다
        fun outside(x: Double, z0: Double, tol: Double): Boolean {
            val nose = x + normal(zDir, side).x * r
            return if (outward > 0) nose - r >= mat.extremeX(z0, zApp, max = true) + clear - tol
            else nose + r <= mat.extremeX(z0, zApp, max = false) - clear + tol
        }
        fun zOk(x: Double, end: Pt, next: Pt, tol: Double = TOUCH_TOL): Boolean {
            val stop = corner(end, zDir, next, side, r)
            return outside(x, stop.z, tol) && ok(Pt(zApp, x) + normal(zDir, side) * r, stop, tol)
        }
        // 축 쪽으로 X 를 찾을 때의 한도: 노즈 중심이 축에 서는 명령 X(Z 줄 노즈가 +X 쪽으로 밀리는 보정 쪽이면 축 너머 r — 노즈보다 조금 넓은 목을 지나 안쪽 방으로 가는
        // 진입. 전에는 명령 X0 에서 멈춰 노즈가 r 밀린 채 목에 걸렸다), 아니면 축
        val xLow = axisLow(side, r)
        // 1) Z 줄로 곧장 진입점에 — 첫 깎는 줄이 +Z 로 가면 Z 줄과 거꾸로라 안 된다
        val direct = shifted(zDir)
        val directOk = uG.z <= DIR_TOL && zOk(direct.x, direct, uG) && g1Ok(zDir, direct)
        if (directOk) return Approach(listOf(Pt(zApp, direct.x), direct), true)
        // 2) 다른 X 에서 Z 를 맞추고 X 로 진입점에 — 먼저 도면 바깥 쪽(외경 위 · 내경 축 쪽), 안 되면 반대쪽. X 줄 → 첫 깎는 줄 꺾임이
        //    공구 쪽(안쪽 — 교점)이거나 바깥 90° 이하여야 한다(넘으면 제어기가 꼭짓점을 도는 토막을 끼운다)
        for (sgn in listOf(outward, -outward)) {
            val xDir = Pt(0.0, -sgn)
            val inside = ((xDir cross uG) > 0) == (side == Side.G41)
            if (if (inside) (xDir dot uG) < -0.95 else (xDir dot uG) < -DIR_TOL) continue
            val e = shifted(xDir)
            val step = max(r, 0.25)
            // 먼저 그 Z 구간 도면 바깥(+ 2r)부터 — 멀면 걸음으로는 못 간다
            val edge = mat.extremeX(e.z, zApp, max = sgn > 0)
            val far = if (edge.isFinite()) edge + sgn * (2 * r + max(step, clear)) else e.x
            val starts = listOf(far).filter { (it - e.x) * sgn > step }
            for (k in 1..SEARCH_STEPS + starts.size) {
                var x = if (k <= starts.size) starts[k - 1] else e.x + sgn * step * (k - starts.size)
                if (x < xLow && e.x >= 0) x = xLow
                val at = Pt(e.z, x)
                val xa = corner(at, zDir, xDir, side, r)
                val xb = corner(e, xDir, uG, side, r)
                // X 맞춤 줄의 노즈 이동도 MIN_MOVE 이상(그 줄이 노즈R 쯤이면 보정 이동이 0 — 반올림으로 거꾸로)
                if (((xb - xa) dot xDir) >= MIN_MOVE && zOk(x, at, xDir) && ok(xa, xb) && g1Ok(xDir, e)) {
                    return Approach(listOf(Pt(zApp, x), at, e), true)
                }
                if (x == xLow) break
            }
        }
        // 3) 첫 깎는 줄의 공구 쪽에서 직각으로 진입점에: Z 줄 → X 맞춤 → 공구 쪽 점 P3 = e + n·L → e(직각 — 안쪽 꺾임) → 첫 깎는 줄.
        //    뒤집은 그림(화면 역방향)에서 단면을 그 뒤쪽(척 쪽)에서 타고 오르는 진입.
        //    P3 → e 는 첫 깎는 줄이 비스듬하면 두 축 G0 이다 — LRP=0(회사 기계)이면 두 축이 따로 가 급송 속도 비에 따라 꺾이는 자리가 다르므로, 노즈 길은
        //    곧은 길만이 아니라 두 꺾인 길(Z 먼저 · X 먼저 — 어느 속도 비의 길도 그 사이)이 모두 소재에서 r 이상이어야 한다(검증 2026-11-05: 급송 X5000 · Z40000
        //    에서 꺾인 노즈가 축 거울 도면을 0.192 팠다)
        //    꺾인 길만 닿는 안(곧은 길은 공기 — 급송 속도 비가 크게 치우칠 때만 닿음)은 따로 두었다가 어느 길도 못 찾으면 그것을 낸다(found = false — 곧장 내려가는
        //    길은 어느 속도 비에서도 판다)
        fun legsOk(a: Pt, b: Pt): Boolean = listOf(Pt(b.z, a.x), Pt(a.z, b.x)).all { k -> ok(a, k) && ok(k, b) }
        var loose: List<Pt>? = null
        val nG = normal(uG, side)
        val toE = nG * -1.0
        for (len in listOf(max(r, 0.25) * 2 + MIN_MOVE, g40Radius(r))) {
            val p3 = shifted(toE) + nG * len
            val e = p3 - nG * len
            for (sgn in listOf(outward, -outward)) {
                val xDir = Pt(0.0, -sgn)
                if ((xDir dot toE) < -DIR_TOL) continue
                val edge = mat.extremeX(p3.z, zApp, max = sgn > 0)
                val far = if (edge.isFinite()) edge + sgn * (2 * r + max(max(r, 0.25), clear)) else p3.x
                for (k in 0..SEARCH_STEPS) {
                    var x = (if (k == 0) far else p3.x + sgn * max(r, 0.25) * k)
                    if (x < xLow && p3.x >= 0) x = xLow
                    if ((x - p3.x) * sgn <= MIN_MOVE) continue
                    val at = Pt(p3.z, x)
                    val c0 = corner(at, zDir, xDir, side, r)
                    val c1 = corner(p3, xDir, toE, side, r)
                    val c2 = corner(e, toE, uG, side, r)
                    if (((c1 - c0) dot xDir) >= MIN_MOVE && ((c2 - c1) dot toE) >= MIN_MOVE && zOk(x, at, xDir) && ok(c0, c1) && ok(c1, c2) && g1Ok(toE, e)) {
                        if (legsOk(c1, c2)) return Approach(listOf(Pt(zApp, x), at, p3, e), true)
                        if (loose == null) loose = listOf(Pt(zApp, x), at, p3, e)
                    }
                    if (x == xLow) break
                }
            }
        }
        // 찾지 못하면(노즈보다 좁은 구멍 · 그 Z 구간을 도면이 막음 등) 곧장 — 그 길의 노즈가 도면에서 r(0.001 반올림까지) 떨어지면 판 것은 아니다.
        // 닿거나 파면 found = false — 부르는 쪽이 경고한다. 다만 첫 깎는 요소가 오목 R ≤ r(보정 원이 없음 — 도면 알람 꼴, 경고 CONCAVE_R_LE_NOSE)이면
        // 첫 깎는 점에 노즈가 닿을 자리가 없어 제어기가 그 원호에서 멈춘다 — 그 탓인 첫 깎는 줄은 빼고 진입 G0 만 본다(진입 길 탓이 아니다)
        loose?.let { return Approach(it, false) }
        if (!lastResort) return Approach(listOf(Pt(zApp, direct.x), direct), false)
        val tol = 1e-3
        val gone = firstArc != null && (normal(u1, side) dot (firstArc.center - s)) > 0 && firstArc.radius - r <= EPS
        if (uG.z <= DIR_TOL) return Approach(listOf(Pt(zApp, direct.x), direct), zOk(direct.x, direct, uG, tol) && (gone || g1Ok(zDir, direct, tol)))
        val xo = direct.x + outward * g40Radius(r)
        val at = Pt(direct.z, xo)
        val xDir = Pt(0.0, -outward)
        val safe = zOk(xo, at, xDir, tol) && ok(corner(at, zDir, xDir, side, r), corner(direct, xDir, uG, side, r), tol) && (gone || g1Ok(xDir, direct, tol))
        return Approach(listOf(Pt(zApp, xo), at, direct), safe)
    }

    /**
     * 빼는 줄 명령점들. [p] = 마지막 깎는 점, [uP] = 그 끝 진행 방향(마지막 요소의 끝 접선), [x1] = 첫 빼는 줄 끝(부르는 쪽이 끝의 종류대로 —
     * 막힌 끝은 벽을 따라 공구 쪽, 빈 끝은 끝 좌표 너머로 곧게, 끝을 비우면 끝 요소를 따라 c), [away] = G40 자리 규칙이 모자랄 때 더 갈 방향(단위),
     * [outward] = 물러날 X 쪽(외경 +1 · 내경 −1). 내경은 축 너머로 가지 않는다(X ≥ 0). [minFirst] = 첫 빼는 줄의 노즈 이동 최소(축에서 끝나는 원호 뒤는 더 짧게).
     * [checkFirst] = 첫 빼는 줄의 노즈 길도 소재에서 r 이상인 것만 받는다(3점호 끝 접선은 그 너머 윤곽을 몰라 — 축 너머 맞은편 · 닫은 도면에 닿는지 본다).
     * [lastArc] = 마지막 깎는 요소가 원호면 그것(첫 빼는 줄과의 꺾임 노즈를 그 보정 원과의 교점으로 — [junction]. 짧은 막힌 끝을 빼고 모서리 R 끝에서 직각으로 뺄 때)
     */
    fun exit(
        mat: FrameMaterial, side: Side, r: Double, outward: Double, p: Pt, uP: Pt, x1: Pt, away: Pt,
        front: Double = 1.0, minFirst: Double = MIN_MOVE, stretch: Boolean = true, checkFirst: Boolean = false, lastArc: Arc? = null,
    ): Exit =
        if (front > 0) exit0(Ends(mat, side, r, p, uP, lastArc), outward, x1, away, minFirst, stretch, checkFirst)
        else exit0(Ends(mat.flipped(), side.other(), r, fz(p), fz(uP), lastArc?.flippedZ() as Arc?), outward, fz(x1), fz(away), minFirst, stretch, checkFirst)
            .let { x -> Exit(x.points.map(::fz), x.found) }

    /**
     * 빼는 줄 명령점들([exit])과 규칙에 맞는 길을 찾았는지. [found] = false 면 어느 안도 G40 자리 규칙 · 물러남 띠 · 빼는 줄 노즈 길(소재에서 r) · 꺾임
     * (바깥 90° 이하 — [acuteOuter])을 함께 맞추지 못해 마지막 안을 냈다 — 부르는 쪽이 화면 경고(EXIT_NOT_FOUND)로 알린다(전에는 경고 없이 냈다 —
     * 검증 2026-11-05: 깊은 언더컷 3점호의 빼는 줄 · 물러남이 부품을 가로질렀다)
     */
    class Exit(val points: List<Pt>, val found: Boolean)

    /** 빼는 줄을 짤 때 늘 같은 것: 소재 · 보정 쪽 · r · 마지막 깎는 점 [p] 과 그 끝 접선 [uP] · 마지막 깎는 원호 [arc] */
    private class Ends(val mat: FrameMaterial, val side: Side, val r: Double, val p: Pt, val uP: Pt, val arc: Arc?) {
        /** 마지막 깎는 요소 → 첫 빼는 줄(방향 [u]) 꺾임의 노즈. 원호 뒤 교점이 없으면 null */
        fun first(u: Pt): Pt? = junction(p, uP, u, side, r, arcA = arc)
    }

    private fun exit0(e: Ends, outward: Double, x1: Pt, away: Pt, minFirst: Double, stretch: Boolean, checkFirst: Boolean): Exit {
        val r = e.r
        val p = e.p
        // G40 자리 규칙 · 물러남 띠를 못 맞추거나 뒤 줄이 도면에 닿으면 첫 빼는 줄을 그 방향으로 더 늘여 다시 짠다(막힌 끝 위의 역테이퍼 · 언더컷 ·
        // 끝 요소 선 위에서 시작한 G40 자리). 그래도 안 되면 G40 자리로 비키는 줄을 더 길게(물러남 X 맞춤 줄이 도면 밑을 지날 때 — 끝이 축 쪽으로 말려
        // 들어간 3점호에서 비키는 줄이 짧으면 X 맞춤 줄이 호 아래를 판다). [checkFirst] 면 두 쪽을 걸음 순으로 번갈아(비키는 줄 먼저) — 첫 줄(끝 접선)을 오래
        // 늘이면 그 너머 모르는 윤곽 밑으로 가지만, 비키는 줄만 오래 늘여도 그린 구간 밖으로 간다. 끝내 못 맞추면(노즈에 비해 좁은 구멍) 마지막 안(found = false) — 내경은 축 위가 가장 넓다
        val dir1 = (x1 - p).unit()
        val step = max(r, 0.5)
        fun ok(plan: List<Pt>) = valid(e, plan, checkFirst)
        val best = plan(e, outward, x1, away, minFirst)
        if (ok(best)) return Exit(best, true)
        if (!stretch) return Exit(best, false)
        fun longerAway(): List<Pt>? {
            for (k in 1..AWAY_STEPS) {
                val plan = plan(e, outward, x1, away, minFirst, g40Radius(r) + step * k)
                if (ok(plan)) return plan
            }
            return null
        }
        if (checkFirst) {
            // 비키는 줄을 늘이는 안과 첫 줄(끝 접선)을 조금 늘이는 안을 걸음 순으로 번갈아 — 덜 늘여 맞는 쪽. 비키는 줄만 먼저 늘이면, 바깥 90° 넘는 꺾임 안을
            // 버린 뒤 비키는 줄이 끝 접선의 공구 쪽으로 13 mm 가 그린 구간 밖 모르는 곳(3점호 시작 앞 — 대개 이어지는 원통 속)까지 갔다(2026-11-05: 끝 접선이
            // 위로 가며 −Z 로 기우는 역방향 3점호 — 끝 접선으로 1.4 면 맞는다)
            var along = true
            for (k in 1..max(AWAY_STEPS, SEARCH_STEPS)) {
                if (k <= AWAY_STEPS) plan(e, outward, x1, away, minFirst, g40Radius(r) + step * k).let { if (ok(it)) return Exit(it, true) }
                if (along) {
                    val q = x1 + dir1 * (step * k)
                    if (outward < 0 && q.x < min(0.0, x1.x) - EPS) {
                        along = false
                    } else {
                        val plan = plan(e, outward, q, away, minFirst)
                        if (ok(plan)) return Exit(plan, true)
                        // 첫 줄이 이미 소재에 닿으면 더 늘여도 닿는다
                        if (!firstClear(e, plan)) along = false
                    }
                }
                if (!along && k >= AWAY_STEPS) break
            }
            return Exit(best, false)
        }
        for (k in 1..SEARCH_STEPS) {
            val q = x1 + dir1 * (step * k)
            if (outward < 0 && q.x < min(0.0, x1.x) - EPS) break
            val plan = plan(e, outward, q, away, minFirst)
            if (ok(plan)) return Exit(plan, true)
        }
        longerAway()?.let { return Exit(it, true) }
        return Exit(best, false)
    }

    /**
     * G40 자리 · 물러남 띠에 소재가 없고, 빼는 줄마다(첫 줄은 [checkFirst] 일 때만 — 끝의 종류대로 부르는 쪽이 정한 것이라) 노즈 길이 도면에 닿지 않나.
     * 원호 끝 → 첫 빼는 줄 꺾임에 교점이 없거나(간섭 알람) 마지막 깎는 요소 · 빼는 줄끼리 바깥으로 90° 넘게 꺾이면([acuteOuter]) 안 된다
     * (검증 2026-11-05: 역방향 3점호에서 G40 자리로 비키는 줄 → 물러남 X 맞춤 줄이 바깥 101° 로 꺾여 시뮬레이터가 COMP_ACUTE_OUTER 로 멈췄다)
     */
    private fun valid(e: Ends, out: List<Pt>, checkFirst: Boolean): Boolean {
        val rc = g40Radius(e.r) - PLAN_TOL
        val last = out.last()
        if (e.mat.dist(last) < rc || e.mat.dist(Line(last, Pt(RETRACT_Z, last.x))) < rc) return false
        if (e.first((out[0] - e.p).unit()) == null) return false
        val dirs = listOf(e.uP) + (listOf(e.p) + out).zipWithNext { a, b -> (b - a).unit() }
        if (dirs.zipWithNext().any { (a, b) -> acuteOuter(a, b, e.side) }) return false
        if (checkFirst && !firstClear(e, out)) return false
        val n = exitNoses(e, out)
        return n.zipWithNext().drop(1).all { (a, b) -> e.mat.dist(Line(a, b)) >= e.r - PLAN_TOL }
    }

    /** 빼는 줄 명령점들의 노즈 자리(처음은 마지막 깎는 점 — 원호 뒤는 그 교점) */
    private fun exitNoses(e: Ends, out: List<Pt>): List<Pt> {
        val n = noses(listOf(e.p) + out, e.side, e.r, first = e.uP)
        return listOf(e.first((out[0] - e.p).unit()) ?: n[0]) + n.drop(1)
    }

    /** 첫 빼는 줄의 노즈 길(끝은 다음 줄과의 꺾임)이 소재에서 r 이상인가 */
    private fun firstClear(e: Ends, out: List<Pt>): Boolean {
        val n = exitNoses(e, out)
        return e.mat.dist(Line(n[0], n[1])) >= e.r - PLAN_TOL
    }

    /** 0 ~ [limit] 에서 [good] 이 되는 가장 작은 값(늘리며 찾은 뒤 반으로 좁힘). 없으면 null */
    private fun least(limit: Double, start: Double, good: (Double) -> Boolean): Double? {
        var hi = min(start, limit)
        while (!good(hi)) {
            if (hi >= limit) return null
            hi = min(limit, hi * 2)
        }
        var lo = 0.0
        repeat(30) {
            val mid = (lo + hi) / 2
            if (good(mid)) hi = mid else lo = mid
        }
        return hi
    }

    /**
     * [extra] > 0 이면 G40 자리로 비키는 줄을 적어도 그만큼(소재가 없어도). 노즈 이동 [MIN_MOVE] 를 맞추느라 첫 줄을 늘이면 뒤 점이 같이 옮겨져
     * G40 자리 · 물러남 띠가 모자랄 수 있다 — 그때는 늘인 첫 줄 끝에서 다시 짠다(검증 2026-11-04: 끝 접선이 축 쪽으로 가는 3점호에서 X 맞춤 점이
     * 첫 줄 방향으로 0.05 밀려 띠가 0.05 모자랐다)
     */
    private fun plan(e: Ends, outward: Double, x1: Pt, away: Pt, minFirst: Double, extra: Double = 0.0): List<Pt> {
        val out = planOnce(e, outward, x1, away, minFirst, extra)
        if ((out[0] - x1).length <= EPS) return out
        val rc = g40Radius(e.r) - PLAN_TOL
        val last = out.last()
        return if (e.mat.dist(last) < rc || e.mat.dist(Line(last, Pt(RETRACT_Z, last.x))) < rc) planOnce(e, outward, out[0], away, minFirst, extra) else out
    }

    private fun planOnce(e: Ends, outward: Double, x1: Pt, away: Pt, minFirst: Double, extra: Double): List<Pt> {
        val mat = e.mat
        val side = e.side
        val r = e.r
        val p = e.p
        val rc = g40Radius(r)
        val out = mutableListOf(x1)
        // 둘째 줄: G40 자리에 소재가 있으면 [away] 로 — 가장 짧게
        // (못 맞추면 — 노즈에 비해 좁은 구멍 — 그래도 G40 자리 규칙 반지름만큼 가서 다음 X 맞춤이 첫 줄을 거꾸로 되짚지 않게)
        // 내경은 축 너머(맞은편 벽)로 가지 않는다 — 축 쪽으로 가는 둘째 줄은 축까지만 찾는다
        val reach = if (outward < 0 && away.x < -1e-9) max(0.0, x1.x / -away.x) else SEARCH_MM
        if (mat.dist(x1) < rc || extra > 0) {
            val found = if (mat.dist(x1) < rc) least(reach, rc / 8) { mat.dist(x1 + away * it) >= rc } ?: min(rc, reach) else 0.0
            val d = min(reach, max(found, extra))
            if (d > EPS) out += x1 + away * d
        }
        // 물러남: 마지막 점에서 +Z 띠(와 G40 자리)가 비어야 한다. 아니면 X 를 맞춘다 — 외경은 바깥(+X), 내경은 축 쪽(축에서 멈춤)
        val last = out.last()
        fun band(q: Pt) = min(mat.dist(q), mat.dist(Line(q, Pt(RETRACT_Z, q.x))))
        // 띠는 [valid] 와 같은 허용(PLAN_TOL)으로 잰다 — 둘째 줄이 꼭 rc 만큼 간 점(막힌 끝 벽 반평면에서 rc)이 소수 오차로 모자라 보여
        // X 를 찾는 한도(SEARCH_MM)까지 보내지 않게(외경 역테이퍼 막힌 끝에서 X 가 200 mm 밖으로 갔다 — 검증 2026-11-04)
        val rcBand = rc - PLAN_TOL
        if (band(last) < rcBand) {
            val dir = if (outward > 0) 1.0 else if (last.x > 0) -1.0 else 1.0
            val limit = if (outward > 0) SEARCH_MM else abs(last.x)
            val d = least(limit, rc) { band(Pt(last.z, last.x + dir * it)) >= rcBand } ?: limit
            val q = Pt(last.z, last.x + dir * d)
            // 앞 줄과 한 직선(X 로 가던 줄)이면 그 줄을 늘인다
            val prev = if (out.size >= 2) out[out.size - 2] else p
            if ((q - last).length > EPS) {
                if (abs((last - prev).unit() cross (q - last).unit()) < 1e-9 && ((last - prev) dot (q - last)) > 0) out[out.lastIndex] = q else out += q
            }
        }
        // 빼는 줄마다 노즈 이동 ≥ MIN_MOVE(앞 꺾임 교점 → 다음 꺾임 교점). 모자라면 그 줄을 늘이고 뒤 점을 같이 옮긴다
        for (i in out.indices) {
            val a = if (i == 0) p else out[i - 1]
            val u = (out[i] - a).unit()
            val next = if (i + 1 < out.size) (out[i + 1] - out[i]).unit() else u
            // 앞 꺾임: 첫 줄은 마지막 깎는 요소와의 꺾임(원호면 교점 — 없으면 법선으로 어림하고 [valid] 가 그 짬을 버린다)
            val from = if (i == 0) e.first(u) ?: corner(a, e.uP, u, side, r) else corner(a, (a - (if (i == 1) p else out[i - 2])).unit(), u, side, r)
            val m = (corner(out[i], u, next, side, r) - from) dot u
            val need = if (i == 0) minFirst else MIN_MOVE
            if (m < need) {
                val shift = u * (need - m)
                for (j in i until out.size) out[j] = out[j] + shift
            }
        }
        return out
    }

    /**
     * 보정 중 직선 명령점 [cmds](처음 → 끝)의 노즈 중심 자리: 처음은 [first] 방향 줄과의 꺾임(null 이면 법선), 끝은 [then] 방향과의 꺾임(null 이면 법선)
     */
    fun noses(cmds: List<Pt>, side: Side, r: Double, first: Pt? = null, then: Pt? = null): List<Pt> {
        val dirs = cmds.zipWithNext { a, b -> (b - a).unit() }
        return cmds.indices.map { i ->
            val uA = if (i == 0) first ?: dirs[0] else dirs[i - 1]
            val uB = if (i == cmds.lastIndex) then ?: dirs[i - 1] else dirs[i]
            corner(cmds[i], uA, uB, side, r)
        }
    }

    /** Z 거울(화면 역방향은 그림을 Z 로 뒤집어 풀어 계산기의 −Z 가 실제 부품 앞이다 — [TaperInput.approach]) */
    private fun fz(p: Pt) = Pt(-p.z, p.x)

    private fun Side.other() = if (this == Side.G41) Side.G42 else Side.G41

    private fun Seg.flippedZ(): Seg = when (this) {
        is Line -> Line(fz(start), fz(end))
        is Arc -> Arc(fz(start), fz(end), fz(center), !cw)
    }

    private fun FrameMaterial.flipped() = FrameMaterial(
        segs.map { it.flippedZ() },
        walls.map { fz(it.first) to fz(it.second) },
        fill?.let { f -> FrameFill(f.drawing.map { it.flippedZ() }, f.inner).takeIf { it.valid } },
    )

    /**
     * 틀 줄 쓰기: 보정 혼자 줄 → 진입(G0) → 첫 깎는 줄(G1 F.1) → [shape] → 빼는 줄(G1 F.3) → G40 → G0Z200.
     * [front] = 부품 앞 Z 쪽(화면 역방향의 거울 풀이는 −1 — 물러남이 Z−200 으로 쓰이고 화면이 Z 를 되돌린다)
     */
    fun write(
        nc: NcLines, side: Side, approach: List<Pt>, first: Pt, shape: NcLines.() -> Unit, exits: List<Pt>,
        refApproach: Int = -2, refFirst: Int = -1, refExit: Int = Int.MAX_VALUE, front: Double = 1.0,
    ) {
        nc.element = refApproach
        nc.alone("$side")
        for (p in approach) nc.move("G0", p)
        nc.element = refFirst
        nc.move("G1", first, f = FEED_CUT)
        nc.shape()
        nc.element = refExit
        for (p in exits) nc.move("G1", p, f = FEED_EXIT)
        nc.alone("G40")
        nc.move("G0", Pt(front * RETRACT_Z, exits.lastOrNull()?.x ?: first.x))
    }
}
