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
 * 노즈 자리는 FANUC 보정 중 규칙(안쪽 = 두 보정선의 교점, 바깥 90° 이하 = 늘인 교점, 한 직선 = 법선 — FF 표 5.4.1(a) 173쪽)으로 셈한다.
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

    /** 보정 중 꺾임 c(직선 uA → 직선 uB)의 노즈 중심: 두 보정선의 교점(바깥이면 늘인 교점, 한 직선이면 법선) */
    fun corner(c: Pt, uA: Pt, uB: Pt, side: Side, r: Double): Pt {
        val a = normal(uA, side)
        val b = normal(uB, side)
        val k = 1 + (a dot b)
        // 거꾸로 꺾임(180°)은 교점이 없다 — 부르는 쪽이 만들지 않는다. 그래도 오면 법선
        return if (k < 1e-9) c + b * r else c + (a + b) * (r / k)
    }

    /**
     * 짧은 막힌 끝 · 시작 요소를 빼고 이웃 요소 끝(처음)에서 공구 쪽 직각으로 빼거나(들어가거나) 할 때, 안쪽 꺾임의 노즈(이웃 요소의 노즈 중심 길 [nose] 와
     * 빼는(들어가는) 줄의 보정선 — [at] 을 지나는 방향 [dir] 의 줄을 공구 쪽으로 r 민 선 — 의 교점)가 그 길 위에 [MIN_MOVE] 이상 남는가. 아니면 그 이웃 블록이
     * 거꾸로 가 간섭 알람이다(짧은 경사 뒤). [fromStart] = 길의 처음에서 잰다(빼기 — 이웃 블록의 노즈가 처음에서 교점까지 간다), 아니면 끝에서(들어가기).
     * 이웃이 원호(모서리 R)면 보지 않는다(false — 전처럼 곧게): 진입 · 빼기의 꺾임 노즈를 접선으로 어림하는 틀 검사([entry] · [exit])가 원호 이웃의 교점을
     * 틀리게 봐 진입을 못 찾고 곧은 Z 줄로 떨어진 적이 있다(검증 2026-11-04 — 겹테이퍼 · 작은 오목 R)
     */
    fun roomOn(nose: Seg, fromStart: Boolean, at: Pt, dir: Pt, side: Side, r: Double): Boolean {
        if (nose !is Line) return false
        val d = nose.end - nose.start
        val len = d.length
        if (len <= EPS) return false
        val q = Intersect.lineLine(at + normal(dir, side) * r, dir, nose.start, d) ?: return false
        val t = ((q - nose.start) dot d) / (len * len)
        return t in 0.0..1.0 && (if (fromStart) t else 1 - t) * len >= MIN_MOVE
    }

    /**
     * 진입 명령점: [(zApp, X)] + (바깥에서 X 를 맞추면 [(e.z, X)]) + [e]. 그 뒤 첫 깎는 줄이 e → [s], 첫 깎는 요소의 처음 접선이 [u1].
     * [e0] = 첫 깎는 점에서 c 떨어진 진입점(부르는 쪽이 정한다 — 첫 깎는 요소와 한 직선이거나 그 앞 요소 선 위, 넣은 시작에 직각이면 공구 쪽),
     * [outward] = 도면 바깥 X 쪽(외경 +1 · 내경 −1), [zTop] = 도면의 부품 앞 쪽 끝 Z([front] > 0 이면 가장 큰 Z, < 0 이면 가장 작은 Z),
     * [front] = 부품 앞 Z 쪽(화면 역방향의 거울 풀이는 −1 — 진입 · 물러남이 계산기 −Z 쪽).
     * 첫 깎는 줄의 노즈 이동이 [MIN_MOVE] 보다 짧으면 진입점을 그 줄을 따라 더 물린다
     */
    fun entry(mat: FrameMaterial, side: Side, r: Double, outward: Double, e0: Pt, s: Pt, u1: Pt, zTop: Double, front: Double = 1.0): List<Pt> =
        if (front > 0) entry0(mat, side, r, outward, e0, s, u1, zTop)
        else entry0(mat.flipped(), side.other(), r, outward, fz(e0), fz(s), fz(u1), -zTop).map(::fz)

    private fun entry0(mat: FrameMaterial, side: Side, r: Double, outward: Double, e0: Pt, s: Pt, u1: Pt, zTop: Double): List<Pt> {
        val uG = (s - e0).unit()
        val zDir = Pt(-1.0, 0.0)
        val xDir = Pt(0.0, -outward)
        fun moved(prev: Pt, e: Pt) = (corner(s, uG, u1, side, r) - corner(e, prev, uG, side, r)) dot uG
        fun shifted(prev: Pt): Pt = moved(prev, e0).let { if (it >= MIN_MOVE) e0 else e0 - uG * (MIN_MOVE - it) }
        fun ok(a: Pt, b: Pt) = mat.dist(Line(a, b)) >= r - TOUCH_TOL
        fun g1Ok(prev: Pt, e: Pt) = ok(corner(e, prev, uG, side, r), corner(s, uG, u1, side, r))
        val zApp = approachZ(max(zTop, e0.z))
        // Z 줄 노즈: 시작은 그 줄의 법선, 끝은 다음 줄과의 꺾임
        // 노즈가 도면 선에 안 닿아도 소재 속을 지날 수 있다(외경 밑 · 내경 벽 너머) — 그 Z 구간의 도면보다 바깥(외경 위 · 내경 축 쪽)이어야 한다
        fun outside(x: Double, z0: Double): Boolean {
            val nose = x + normal(zDir, side).x * r
            return if (outward > 0) nose - r >= mat.extremeX(z0, zApp, max = true) - TOUCH_TOL
            else nose + r <= mat.extremeX(z0, zApp, max = false) + TOUCH_TOL
        }
        fun zOk(x: Double, end: Pt, next: Pt): Boolean {
            val stop = corner(end, zDir, next, side, r)
            return outside(x, stop.z) && ok(Pt(zApp, x) + normal(zDir, side) * r, stop)
        }
        // 1) Z 줄로 곧장 진입점에 — 첫 깎는 줄이 +Z 로 가면 Z 줄과 거꾸로라 안 된다
        val direct = shifted(zDir)
        val directOk = uG.z <= DIR_TOL && zOk(direct.x, direct, uG) && g1Ok(zDir, direct)
        if (directOk) return listOf(Pt(zApp, direct.x), direct)
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
            val far = if (edge.isFinite()) edge + sgn * (2 * r + step) else e.x
            val starts = listOf(far).filter { (it - e.x) * sgn > step }
            for (k in 1..SEARCH_STEPS + starts.size) {
                var x = if (k <= starts.size) starts[k - 1] else e.x + sgn * step * (k - starts.size)
                if (x < 0 && e.x >= 0) x = 0.0
                val at = Pt(e.z, x)
                val xa = corner(at, zDir, xDir, side, r)
                val xb = corner(e, xDir, uG, side, r)
                // X 맞춤 줄의 노즈 이동도 MIN_MOVE 이상(그 줄이 노즈R 쯤이면 보정 이동이 0 — 반올림으로 거꾸로)
                if (((xb - xa) dot xDir) >= MIN_MOVE && zOk(x, at, xDir) && ok(xa, xb) && g1Ok(xDir, e)) {
                    return listOf(Pt(zApp, x), at, e)
                }
                if (x == 0.0) break
            }
        }
        // 3) 첫 깎는 줄의 공구 쪽에서 직각으로 진입점에: Z 줄 → X 맞춤 → 공구 쪽 점 P3 = e + n·L → e(직각 — 안쪽 꺾임) → 첫 깎는 줄.
        //    뒤집은 그림(화면 역방향)에서 단면을 그 뒤쪽(척 쪽)에서 타고 오르는 진입
        val nG = normal(uG, side)
        val toE = nG * -1.0
        for (len in listOf(max(r, 0.25) * 2 + MIN_MOVE, g40Radius(r))) {
            val p3 = shifted(toE) + nG * len
            val e = p3 - nG * len
            for (sgn in listOf(outward, -outward)) {
                val xDir = Pt(0.0, -sgn)
                if ((xDir dot toE) < -DIR_TOL) continue
                val edge = mat.extremeX(p3.z, zApp, max = sgn > 0)
                val far = if (edge.isFinite()) edge + sgn * (2 * r + max(r, 0.25)) else p3.x
                for (k in 0..SEARCH_STEPS) {
                    var x = (if (k == 0) far else p3.x + sgn * max(r, 0.25) * k)
                    if (x < 0 && p3.x >= 0) x = 0.0
                    if ((x - p3.x) * sgn <= MIN_MOVE) continue
                    val at = Pt(p3.z, x)
                    val c0 = corner(at, zDir, xDir, side, r)
                    val c1 = corner(p3, xDir, toE, side, r)
                    val c2 = corner(e, toE, uG, side, r)
                    if (((c1 - c0) dot xDir) >= MIN_MOVE && ((c2 - c1) dot toE) >= MIN_MOVE && zOk(x, at, xDir) && ok(c0, c1) && ok(c1, c2) && g1Ok(toE, e)) {
                        return listOf(Pt(zApp, x), at, p3, e)
                    }
                    if (x == 0.0) break
                }
            }
        }
        // 찾지 못하면(노즈보다 좁은 구멍 등) 곧장 — 부르는 쪽 경고가 알린다
        return if (uG.z <= DIR_TOL) listOf(Pt(zApp, direct.x), direct) else listOf(Pt(zApp, direct.x + outward * g40Radius(r)), Pt(direct.z, direct.x + outward * g40Radius(r)), direct)
    }

    /**
     * 빼는 줄 명령점들. [p] = 마지막 깎는 점, [uP] = 그 끝 진행 방향(마지막 요소의 끝 접선), [x1] = 첫 빼는 줄 끝(부르는 쪽이 끝의 종류대로 —
     * 막힌 끝은 벽을 따라 공구 쪽, 빈 끝은 끝 좌표 너머로 곧게, 끝을 비우면 끝 요소를 따라 c), [away] = G40 자리 규칙이 모자랄 때 더 갈 방향(단위),
     * [outward] = 물러날 X 쪽(외경 +1 · 내경 −1). 내경은 축 너머로 가지 않는다(X ≥ 0). [minFirst] = 첫 빼는 줄의 노즈 이동 최소(축에서 끝나는 원호 뒤는 더 짧게).
     * [checkFirst] = 첫 빼는 줄의 노즈 길도 소재에서 r 이상인 것만 받는다(3점호 끝 접선은 그 너머 윤곽을 몰라 — 축 너머 맞은편 · 닫은 도면에 닿는지 본다)
     */
    fun exit(
        mat: FrameMaterial, side: Side, r: Double, outward: Double, p: Pt, uP: Pt, x1: Pt, away: Pt,
        front: Double = 1.0, minFirst: Double = MIN_MOVE, stretch: Boolean = true, checkFirst: Boolean = false,
    ): List<Pt> =
        if (front > 0) exit0(mat, side, r, outward, p, uP, x1, away, minFirst, stretch, checkFirst)
        else exit0(mat.flipped(), side.other(), r, outward, fz(p), fz(uP), fz(x1), fz(away), minFirst, stretch, checkFirst).map(::fz)

    private fun exit0(
        mat: FrameMaterial, side: Side, r: Double, outward: Double, p: Pt, uP: Pt, x1: Pt, away: Pt, minFirst: Double, stretch: Boolean, checkFirst: Boolean,
    ): List<Pt> {
        // G40 자리 규칙 · 물러남 띠를 못 맞추거나 뒤 줄이 도면에 닿으면 첫 빼는 줄을 그 방향으로 더 늘여 다시 짠다(막힌 끝 위의 역테이퍼 · 언더컷 ·
        // 끝 요소 선 위에서 시작한 G40 자리). 그래도 안 되면 G40 자리로 비키는 줄을 더 길게(물러남 X 맞춤 줄이 도면 밑을 지날 때 — 끝이 축 쪽으로 말려
        // 들어간 3점호에서 비키는 줄이 짧으면 X 맞춤 줄이 호 아래를 판다). [checkFirst] 면 비키는 줄부터 — 첫 줄(끝 접선)을 늘이면 그 너머 모르는 윤곽
        // 밑으로 오래 가지만 비키는 줄은 공구 쪽 공기로 간다. 끝내 못 맞추면(노즈에 비해 좁은 구멍) 마지막 안 — 내경은 축 위가 가장 넓다
        val dir1 = (x1 - p).unit()
        val step = max(r, 0.5)
        fun ok(plan: List<Pt>) = valid(mat, side, r, p, uP, plan, checkFirst)
        val best = plan(mat, side, r, outward, p, uP, x1, away, minFirst)
        if (ok(best) || !stretch) return best
        fun longerAway(): List<Pt>? {
            for (k in 1..AWAY_STEPS) {
                val plan = plan(mat, side, r, outward, p, uP, x1, away, minFirst, g40Radius(r) + step * k)
                if (ok(plan)) return plan
            }
            return null
        }
        if (checkFirst) longerAway()?.let { return it }
        for (k in 1..SEARCH_STEPS) {
            val q = x1 + dir1 * (step * k)
            if (outward < 0 && q.x < min(0.0, x1.x) - EPS) break
            val plan = plan(mat, side, r, outward, p, uP, q, away, minFirst)
            if (ok(plan)) return plan
            // 첫 줄이 이미 소재에 닿으면 더 늘여도 닿는다
            if (checkFirst && !firstClear(mat, side, r, p, uP, plan)) break
        }
        if (!checkFirst) longerAway()?.let { return it }
        return best
    }

    /** G40 자리 · 물러남 띠에 소재가 없고, 빼는 줄마다(첫 줄은 [checkFirst] 일 때만 — 끝의 종류대로 부르는 쪽이 정한 것이라) 노즈 길이 도면에 닿지 않나 */
    private fun valid(mat: FrameMaterial, side: Side, r: Double, p: Pt, uP: Pt, out: List<Pt>, checkFirst: Boolean): Boolean {
        val rc = g40Radius(r) - PLAN_TOL
        val last = out.last()
        if (mat.dist(last) < rc || mat.dist(Line(last, Pt(RETRACT_Z, last.x))) < rc) return false
        if (checkFirst && !firstClear(mat, side, r, p, uP, out)) return false
        val n = noses(listOf(p) + out, side, r, first = uP)
        return n.zipWithNext().drop(1).all { (a, b) -> mat.dist(Line(a, b)) >= r - PLAN_TOL }
    }

    /** 첫 빼는 줄의 노즈 길(끝은 다음 줄과의 꺾임)이 소재에서 r 이상인가 */
    private fun firstClear(mat: FrameMaterial, side: Side, r: Double, p: Pt, uP: Pt, out: List<Pt>): Boolean {
        val n = noses(listOf(p) + out, side, r, first = uP)
        return mat.dist(Line(n[0], n[1])) >= r - PLAN_TOL
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
    private fun plan(mat: FrameMaterial, side: Side, r: Double, outward: Double, p: Pt, uP: Pt, x1: Pt, away: Pt, minFirst: Double, extra: Double = 0.0): List<Pt> {
        val out = planOnce(mat, side, r, outward, p, uP, x1, away, minFirst, extra)
        if ((out[0] - x1).length <= EPS) return out
        val rc = g40Radius(r) - PLAN_TOL
        val last = out.last()
        return if (mat.dist(last) < rc || mat.dist(Line(last, Pt(RETRACT_Z, last.x))) < rc) planOnce(mat, side, r, outward, p, uP, out[0], away, minFirst, extra) else out
    }

    private fun planOnce(mat: FrameMaterial, side: Side, r: Double, outward: Double, p: Pt, uP: Pt, x1: Pt, away: Pt, minFirst: Double, extra: Double): List<Pt> {
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
            val uPrev = if (i == 0) uP else (a - (if (i == 1) p else out[i - 2])).unit()
            val next = if (i + 1 < out.size) (out[i + 1] - out[i]).unit() else u
            val m = (corner(out[i], u, next, side, r) - corner(a, uPrev, u, side, r)) dot u
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
