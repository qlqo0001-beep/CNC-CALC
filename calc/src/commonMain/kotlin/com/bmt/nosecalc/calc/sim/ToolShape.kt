package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 공구 단면(설계 5.3) — 가상 인선(프로그램 점) = (0, 0) 기준 볼록 다각형(반시계, z 오른쪽 · x 위, x 반경).
 * 홀더 몸통은 보지 않는다(인서트만). 노즈 원은 현 높이 [SAG] 이하로 나눠 꼭짓점으로 넣는다(축 방향 끝점은 꼭 넣음 —
 * 깎은 반경이 노즈 끝과 정확히 같게).
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

    /** 공구 단면(가상 인선 기준). 비어 있으면 모양 없음 */
    fun outline(tool: ToolOffset): List<Pt> {
        val r = max(tool.noseR ?: 0.0, 0.0)
        val tip = (tool.tip ?: 0).takeIf { it in 0..9 } ?: 0
        return when (tool.kind) {
            ToolKind.TURN -> if (tip in 1..4) turn(tool, r, tip) else circle(r, tip)
            ToolKind.GROOVE -> {
                val w = tool.width
                if (tip in 1..4 && w != null && w > 0 && w >= 2 * r) groove(w, r, tip) else circle(r, tip)
            }
            ToolKind.THREAD -> thread(tool.threadDeg ?: 60.0, r, tip)
            ToolKind.ROUND -> circle(r, tip)
            ToolKind.DIE -> emptyList()
        }
    }

    /** [outline] 을 가상 인선 [at] 으로 옮긴 것 */
    fun polygon(tool: ToolOffset, at: Pt): List<Pt> = outline(tool).map { it + at }

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

    /** 바이트 — 팁 2 기준(BarShape 식)을 만들어 거울로 */
    private fun turn(tool: ToolOffset, r: Double, tip: Int): List<Pt> {
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
        return mirror(base, z = tip == 1 || tip == 4, x = tip == 3 || tip == 4)
    }

    /** 홈 — 팁 3 기준(왼쪽 아래 모서리, 몸통 위) */
    private fun groove(w: Double, r: Double, tip: Int): List<Pt> {
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
        return mirror(dedupe(pts), z = tip == 4 || tip == 1, x = tip == 2 || tip == 1)
    }

    /** 나사 V — 바깥(꼭짓점 아래) 기준, 안쪽은 X 거울. 노즈 중심 = −팁 벡터 */
    private fun thread(aDeg: Double, r: Double, tip: Int): List<Pt> {
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
        return mirror(pts, z = false, x = inner)
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
