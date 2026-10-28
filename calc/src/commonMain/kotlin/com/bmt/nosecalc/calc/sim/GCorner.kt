package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Corners
import com.bmt.nosecalc.calc.EPS
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Seg
import kotlin.math.abs

/**
 * 모서리 `,C` · `,R`(`docs/simulator-design.md` 1.7) — 블록 A 끝 꼭짓점(= B 처음)에 면취 · 라운드를 끼운다.
 * - `,R r`: 두 요소에 접하는 반지름 r 원호(S 2-31~2-32). 직선 · 원호 모든 짝 — [Corners.insert] 를 그대로 쓴다.
 * - `,C c`: 가상 꼭짓점에서 두 선을 따라 c 떨어진 두 점을 잇는 직선(HG — 원본 10.jpg 손 계산 X44.000 Z−29.234 로 맞춰 봄).
 *   이웃이 원호면 지원 안 함(CORNER_C_ARC — 원호를 따라 잴지 현으로 잴지 출처가 없다).
 */
internal object GCorner {
    sealed interface Res
    data class Ok(val a: Seg, val corner: Seg, val b: Seg) : Res
    data class Bad(val code: IssueCode) : Res

    fun apply(a: Seg, b: Seg, chamfer: Boolean, size: Double): Res {
        val ta = GComp.tanAt(a, a.end)
        val tb = GComp.tanAt(b, b.start)
        // 접해서 이어지거나 제자리로 되돌아가면 끼울 꼭짓점이 없다
        if (abs(ta cross tb) <= 1e-12) return Bad(IssueCode.CORNER_NO_VERTEX)
        if (chamfer) {
            if (a !is Line || b !is Line) return Bad(IssueCode.CORNER_C_ARC)
            val la = (a.end - a.start).length
            val lb = (b.end - b.start).length
            if (la - size <= EPS || lb - size <= EPS) return Bad(IssueCode.CORNER_TOO_BIG)
            val p1 = a.end - ta * size
            val p2 = b.start + tb * size
            return Ok(Line(a.start, p1), Line(p1, p2), Line(p2, b.end))
        }
        val res = Corners.insert(listOf(a, b), listOf(size)) ?: return Bad(IssueCode.CORNER_TOO_BIG)
        if (res.arc[0] == null) return Bad(IssueCode.CORNER_NO_VERTEX)
        val arc = res.segs[1] as Arc
        return Ok(res.segs[0], arc, res.segs[2])
    }
}
