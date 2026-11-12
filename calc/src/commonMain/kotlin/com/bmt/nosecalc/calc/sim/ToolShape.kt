package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 공구 단면(설계 5.3) — 가상 인선(프로그램 점) = (0, 0) 기준 볼록 다각형(반시계, z 오른쪽 · x 위, x 반경).
 * 홀더 몸통은 보지 않는다(인서트만). 노즈 원은 현 높이 [SAG] 이하로 나눠 꼭짓점으로 넣는다(축 방향 끝점은 꼭 넣음 —
 * 깎은 반경이 노즈 끝과 정확히 같게). 안쪽 공구만 바 지름으로 몸통 단면([body])을 따로 낸다(그림 · 몸통 닿음 — 설계 20절).
 *
 * - 바이트(TURN, 팁 1~4): `calc-design.md` '공구 단면(BarShape)' 식을 팁 2 기준으로(노즈 중심 원점, e1 · e2 · N),
 *   팁 1 = Z 거울 · 3 = X 거울 · 4 = 둘 다. 절입각 κr 빈칸 = C 95° · D · V · T 93°, 날 길이 C · D 12 · V · T 16 mm(가정 — 설계 5.3).
 * - 홈(GROOVE, 팁 1~4): 폭 w 직사각형 + 아래 두 모서리 노즈 R. 팁 3 = 왼쪽 아래 모서리 기준(몸통 위), 4 = Z 거울, 2 = X 거울, 1 = 둘 다.
 * - 나사(THREAD): 끝각 a(빈칸 60°)의 V, 끝 R. 팁 1 · 2 · 6 은 안쪽(꼭짓점 위), 그 밖은 바깥(꼭짓점 아래).
 * - 둥근(ROUND) · 바이트/홈의 팁 0 · 5~9: 노즈 원만.
 * - 다이스 헤드(DIE): 모양 없음 — 래스터를 깎지 않는다(체이서 넷이 둘레를 한꺼번에 깎는 나사라 회전체로 빼면 산까지 없어진다. 나사 모양은
 *   [StockSim] 이 공구 설정의 d · P · 각으로 그린다 — 설계 19절). 머리 몸통 충돌도 보지 않는다.
 * 모양이 없으면(노즈 0 인 원 · 폭 없는 홈 · 다이스 …) 빈 목록 — 소재를 깎지 않는다.
 */
object ToolShape {
    /** 노즈 원을 나누는 현 높이(mm) */
    const val SAG = 0.001

    /** 홈 인서트 높이(가정, mm) — 깊은 홈도 몸통이 덮게 */
    const val GROOVE_DEPTH = 12.0

    /** 나사 인서트 높이(가정, mm) */
    const val THREAD_DEPTH = 8.0

    /** 날 길이(가정 — 설계 5.3) */
    fun edgeOf(s: InsertShape): Double = when (s) {
        InsertShape.C, InsertShape.D -> 12.0
        InsertShape.V, InsertShape.T -> 16.0
    }

    /** 절입각 κr 빈칸일 때(가정 — 설계 5.3) */
    fun kappaOf(s: InsertShape): Double = if (s == InsertShape.C) 95.0 else 93.0

    /** 자동 바 지름의 아래 끝(mm) — 공구가 중심까지 가는 글에서도 몸통이 보이게 */
    const val BAR_MIN = 1.0

    /** 바 지름([ToolOffset.barDia])이 받는 범위(mm) — 화면 칸과 저장소 읽기가 같이 쓴다. 범위 밖(0 · 음수 · 숫자 아님 · 500 초과)은 자동으로 돈다 */
    fun barOk(v: Double): Boolean = v > 0 && v <= 500

    /** 공구 단면(가상 인선 기준) — 인서트만(깎는 모양). 비어 있으면 모양 없음 */
    fun outline(tool: ToolOffset): List<Pt> = shape(tool, null)

    /** [outline] 을 가상 인선 [at] 으로 옮긴 것 */
    fun polygon(tool: ToolOffset, at: Pt): List<Pt> = outline(tool).map { it + at }

    /**
     * 몸통 단면(그림 · '몸통 닿음' — 설계 20절). 안쪽 공구([inner])는 날끝에서 −x 로 바 지름 W([barOf]) 만큼: W 가 인서트 깊이보다 작으면 인서트를
     * x ≥ −W 로 자르고, 크면 바 띠(두 점)를 더한 볼록 껍질 — 띠의 앞은 바이트면 주절삭날 연장선(인서트 아래 앞 모서리보다 앞으로 나가지 않게), 뒤는 인서트 맨 뒤 z,
     * 홈 · 나사는 인서트의 z 범위 그대로. 그 밖의 공구는 [outline] 그대로. [fit] = 자동 값의 바탕([barFit] 의 그 보정 번호 값).
     */
    fun body(tool: ToolOffset, fit: Double? = null): List<Pt> = shape(tool, barOf(tool, fit))

    /** 안쪽 공구(몸통이 날끝 아래 −x 쪽 — 구멍 안에서 깎는다): 바이트 · 홈의 팁 1 · 2, 나사의 팁 1 · 2 · 6 */
    fun inner(tool: ToolOffset): Boolean = when (tool.kind) {
        ToolKind.TURN, ToolKind.GROOVE -> tool.tip == 1 || tool.tip == 2
        ToolKind.THREAD -> tool.tip == 1 || tool.tip == 2 || tool.tip == 6
        ToolKind.ROUND, ToolKind.DIE -> false
    }

    /**
     * 쓰이는 바 지름(mm): 넣은 값([ToolOffset.barDia] — [barOk] 일 때), 빈칸이면 자동 = 인서트 깊이(날끝에서 인서트 등까지)와 [fit] 가운데 작은 쪽
     * ([BAR_MIN] 이상 — [fit] 이 없으면 인서트 깊이). 안쪽 공구가 아니거나 인서트 모양이 없으면(노즈 원뿐) null — 바 지름을 쓰지 않는다.
     */
    fun barOf(tool: ToolOffset, fit: Double? = null): Double? {
        if (!inner(tool) || !hasInsert(tool)) return null
        tool.barDia?.takeIf(::barOk)?.let { return it }
        val h = -outline(tool).minOf { it.x }
        return if (fit == null) h else min(h, max(BAR_MIN, fit))
    }

    /** 자동 바 지름의 바탕: 보정 번호마다 그 번호의 이동(가상 인선)이 지나는 가장 작은 |x| 의 두 배 — 그 공구가 들어가는 가장 작은 구멍 지름 */
    fun barFit(run: SimRun): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        for (s in run.steps) {
            val m = s as? MoveStep ?: continue
            val n = m.offset ?: continue
            val d = 2 * StockAuto.extremes(m.tip).minOf { abs(it.x) }
            if (d < (out[n] ?: Double.POSITIVE_INFINITY)) out[n] = d
        }
        return out
    }

    private fun noseOf(tool: ToolOffset): Double = max(tool.noseR ?: 0.0, 0.0)

    private fun tipOf(tool: ToolOffset): Int = (tool.tip ?: 0).takeIf { it in 0..9 } ?: 0

    /** 인서트 다각형을 그리는가(아니면 노즈 원 · 모양 없음) */
    private fun hasInsert(tool: ToolOffset): Boolean {
        val tip = tipOf(tool)
        return when (tool.kind) {
            ToolKind.TURN -> tip in 1..4 && kappaOk(tool)
            ToolKind.GROOVE -> {
                val w = tool.width
                tip in 1..4 && w != null && w > 0 && w >= 2 * noseOf(tool)
            }
            ToolKind.THREAD -> true
            ToolKind.ROUND, ToolKind.DIE -> false
        }
    }

    private fun kappaOk(tool: ToolOffset): Boolean {
        val kDeg = tool.kappaDeg ?: kappaOf(tool.insert)
        return kDeg > 0 && kDeg < 180 && 180 - kDeg - tool.insert.epsDeg >= 0
    }

    /** [bar] = 안쪽 공구의 바 지름(null = 인서트만) */
    private fun shape(tool: ToolOffset, bar: Double?): List<Pt> {
        val r = noseOf(tool)
        val tip = tipOf(tool)
        return when (tool.kind) {
            ToolKind.TURN -> if (hasInsert(tool)) turn(tool, r, tip, bar) else circle(r, tip)
            ToolKind.GROOVE -> if (hasInsert(tool)) groove(tool.width!!, r, tip, bar) else circle(r, tip)
            ToolKind.THREAD -> thread(tool.threadDeg ?: 60.0, r, tip, bar)
            ToolKind.ROUND -> circle(r, tip)
            ToolKind.DIE -> emptyList()
        }
    }

    /** 노즈 원(중심 = −팁 벡터) */
    private fun circle(r: Double, tip: Int): List<Pt> {
        if (r <= 0) return emptyList()
        val c = Pt(0.0, 0.0) - NoseComp.tipVector(tip, r)
        return arc(c, r, 0.0, 2 * PI).dropLast(1)
    }

    /**
     * 중심 c, 반지름 r 의 원호를 a0 → a1(반시계, 라디안) 꼭짓점으로. 끝점 둘 다 넣고, 사이 축 방향 점(0 · 90 · 180 · 270°)도 넣는다
     */
    private fun arc(c: Pt, r: Double, a0: Double, a1: Double): List<Pt> {
        val sweep = a1 - a0
        if (r <= 0 || sweep <= 0) return listOf(c + Pt(cos(a0), sin(a0)) * r)
        val step = if (r > SAG) 2 * acos(1 - SAG / r) else PI / 4
        val n = ceil(sweep / step).toInt().coerceIn(2, 96)
        val angles = (0..n).map { a0 + sweep * it / n }.toMutableList()
        // 축 방향 끝점(깎는 반경 · Z 끝)이 꼭 꼭짓점이 되게
        var q = ceil(a0 / (PI / 2)) * (PI / 2)
        while (q < a1 - 1e-12) {
            if (q > a0 + 1e-12) angles += q
            q += PI / 2
        }
        return angles.sorted().map { a -> c + Pt(cos(a), sin(a)) * r }
    }

    private fun mirror(pts: List<Pt>, z: Boolean, x: Boolean): List<Pt> {
        if (!z && !x) return pts
        val m = pts.map { Pt(if (z) -it.z else it.z, if (x) -it.x else it.x) }
        // 거울 하나는 돌아가는 방향을 바꾼다 — 반시계로 되돌림
        return if (z != x) m.asReversed() else m
    }

    /**
     * 안쪽 공구 단면(몸통 −x, 날끝 x = 0)에 바 지름 [w]: 인서트 깊이보다 작으면 x ≥ −w 로 자르고, 크면 바 띠의 두 점 ([za], −w) · ([zb], −w) 를 더한 볼록 껍질
     */
    private fun withBar(base: List<Pt>, w: Double, za: Double, zb: Double): List<Pt> {
        val h = -base.minOf { it.x }
        return when {
            w < h - 1e-9 -> clipAbove(base, -w)
            w > h + 1e-9 -> StockSim.convexHull(base + Pt(za, -w) + Pt(zb, -w))
            else -> base
        }
    }

    /** 볼록 다각형의 x ≥ x0 쪽(도는 방향 그대로) */
    private fun clipAbove(pts: List<Pt>, x0: Double): List<Pt> {
        val out = ArrayList<Pt>(pts.size + 2)
        for (i in pts.indices) {
            val p = pts[i]
            val q = pts[(i + 1) % pts.size]
            if (p.x >= x0) out += p
            if ((p.x - x0) * (q.x - x0) < 0) out += Pt(p.z + (q.z - p.z) * ((x0 - p.x) / (q.x - p.x)), x0)
        }
        return dedupe(out)
    }

    /** 바이트 — 팁 2 기준(BarShape 식)을 만들어 거울로. [bar] 는 팁 1 · 2 에서만 온다(거울은 Z 뿐이라 팁 2 틀에서 자른다) */
    private fun turn(tool: ToolOffset, r: Double, tip: Int, bar: Double?): List<Pt> {
        val kDeg = tool.kappaDeg ?: kappaOf(tool.insert)
        val eDeg = tool.insert.epsDeg
        val kpDeg = 180 - kDeg - eDeg
        if (kDeg <= 0 || kDeg >= 180 || kpDeg < 0) return circle(r, tip)
        val k = kDeg * PI / 180
        val eps = eDeg * PI / 180
        val kp = kpDeg * PI / 180
        val e1 = Pt(-cos(k), -sin(k))
        val e2 = Pt(cos(kp), -sin(kp))
        val sum = e1 + e2
        val corner = sum * (-r / sin(eps / 2) / sum.length)
        val l = edgeOf(tool.insert)
        val t1 = Pt(-sin(k), cos(k)) * r
        val t2 = Pt(sin(kp), cos(kp)) * r
        val body = listOf(t1, corner + e1 * l) +
            (if (tool.insert == InsertShape.T) emptyList() else listOf(corner + (e1 + e2) * l)) +
            listOf(corner + e2 * l, t2)
        // 노즈: T2(각 90° − κr′) 에서 반시계로 T1(90° + κr) 까지 — 끝점은 body 에 이미 있다
        val nose = if (r > 0) arc(Pt(0.0, 0.0), r, PI / 2 - kp, PI / 2 + k).drop(1).dropLast(1) else emptyList()
        val tipRef = Pt(-r, r) // 팁 2 가상 인선 = 노즈 중심 + 팁 벡터
        val base = dedupe(body + nose).map { it - tipRef }
        val barred = if (bar == null) base else {
            // 바 띠의 앞: 주절삭날(T1 에서 e1 쪽) 연장선이 x = −bar 와 만나는 z — 인서트 아래 앞 모서리보다 앞(−z)으로도(κr < 90°),
            // 인서트 맨 뒤보다 뒤로도(아주 굵은 바) 나가지 않는다
            val a = t1 - tipRef
            val low = corner + e1 * l - tipRef
            val back = base.maxOf { it.z }
            withBar(base, bar, max(a.z + e1.z * ((-bar - a.x) / e1.x), low.z).coerceAtMost(back), back)
        }
        return mirror(barred, z = tip == 1 || tip == 4, x = tip == 3 || tip == 4)
    }

    /** 홈 — 팁 3 기준(왼쪽 아래 모서리, 몸통 위). [bar] 는 팁 1 · 2(몸통 아래)에서만 온다 */
    private fun groove(w: Double, r: Double, tip: Int, bar: Double?): List<Pt> {
        val h = max(GROOVE_DEPTH, 3 * w)
        val pts = mutableListOf(Pt(0.0, h))
        if (r > 0) {
            pts += arc(Pt(r, r), r, PI, 1.5 * PI)
            pts += arc(Pt(w - r, r), r, 1.5 * PI, 2 * PI)
        } else {
            pts += Pt(0.0, 0.0)
            pts += Pt(w, 0.0)
        }
        pts += Pt(w, h)
        val base = mirror(dedupe(pts), z = tip == 4 || tip == 1, x = tip == 2 || tip == 1)
        return if (bar == null) base else withBar(base, bar, base.minOf { it.z }, base.maxOf { it.z })
    }

    /** 나사 V — 바깥(꼭짓점 아래) 기준, 안쪽은 X 거울. 노즈 중심 = −팁 벡터. [bar] 는 안쪽(팁 1 · 2 · 6)에서만 온다 */
    private fun thread(aDeg: Double, r: Double, tip: Int, bar: Double?): List<Pt> {
        val inner = tip == 1 || tip == 2 || tip == 6
        val h = (aDeg.coerceIn(10.0, 150.0) / 2) * PI / 180
        // 바깥 V 의 노즈 중심: 안쪽이면 거울 뒤 자리가 −팁 벡터가 되게 미리 뒤집어 둔다
        val tv = NoseComp.tipVector(tip, r)
        val c = Pt(-tv.z, if (inner) tv.x else -tv.x)
        val pl = c + Pt(-cos(h), -sin(h)) * r
        val pr = c + Pt(cos(h), -sin(h)) * r
        val top = c.x + THREAD_DEPTH
        val farR = pr + Pt(sin(h), cos(h)) * ((top - pr.x) / cos(h))
        val farL = pl + Pt(-sin(h), cos(h)) * ((top - pl.x) / cos(h))
        val nose = if (r > 0) arc(c, r, PI + h, 2 * PI - h) else listOf(c)
        val pts = dedupe(nose + listOf(farR, farL))
        val base = mirror(pts, z = false, x = inner)
        return if (bar == null) base else withBar(base, bar, base.minOf { it.z }, base.maxOf { it.z })
    }

    private fun dedupe(pts: List<Pt>): List<Pt> {
        val out = ArrayList<Pt>(pts.size)
        for (p in pts) if (out.isEmpty() || (out.last() - p).length > 1e-12) out += p
        while (out.size > 1 && (out.first() - out.last()).length <= 1e-12) out.removeAt(out.size - 1)
        return out
    }

    /** 다각형 넓이(반시계면 양수) — 테스트 · 모양 확인용 */
    fun area(pts: List<Pt>): Double {
        var s = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            s += a.z * b.x - b.z * a.x
        }
        return s / 2
    }

    /** z 범위 · x 범위(테스트용) */
    internal fun box(pts: List<Pt>): Pair<Pt, Pt> =
        Pt(pts.minOf { it.z }, pts.minOf { it.x }) to Pt(pts.maxOf { it.z }, pts.maxOf { it.x })
}
