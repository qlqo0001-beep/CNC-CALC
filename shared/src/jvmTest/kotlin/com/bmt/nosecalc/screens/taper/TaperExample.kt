package com.bmt.nosecalc.screens.taper

/**
 * 2단계의 처음 값(그림 모양대로 바로 풀리는 예와 Z · X 여유 1.0). 화면은 이제 도면 치수 · 여유 0 에서 시작하므로
 * 스크린샷 · 골든 · 견고성 시험이 이 값을 넣어 예전과 같은 결과 화면을 그린다
 */
internal object TaperExample {
    /** 정방향 (X 직경, Z) — T1(오른쪽)부터 */
    private fun forward(kind: TaperKind, type: Int): Pair<List<Double>, List<Double>> = when (kind) {
        TaperKind.OUTER -> (if (type == 5) listOf(50.0, 40.0) else listOf(40.0, 50.0)) to listOf(0.0, -5.0)
        TaperKind.INNER -> (if (type == 5) listOf(30.0, 40.0) else listOf(40.0, 30.0)) to listOf(0.0, -5.0)
        TaperKind.OUTER_DOUBLE -> when (type) {
            1 -> listOf(40.0, 46.0, 52.0) to listOf(0.0, -6.0, -9.0)
            2 -> listOf(40.0, 50.0, 40.0) to listOf(0.0, -5.0, -10.0)
            3 -> listOf(50.0, 40.0, 50.0) to listOf(0.0, -5.0, -10.0)
            else -> listOf(52.0, 46.0, 40.0) to listOf(0.0, -3.0, -9.0)
        }
        TaperKind.INNER_DOUBLE -> when (type) {
            1 -> listOf(52.0, 46.0, 40.0) to listOf(0.0, -6.0, -9.0)
            2 -> listOf(50.0, 40.0, 50.0) to listOf(0.0, -5.0, -10.0)
            3 -> listOf(40.0, 50.0, 40.0) to listOf(0.0, -5.0, -10.0)
            else -> listOf(40.0, 46.0, 52.0) to listOf(0.0, -3.0, -9.0)
        }
        TaperKind.GROOVE -> emptyList<Double>() to emptyList()
    }

    /** name = x1·z1·x2·z2·x3·z3. 역방향은 그림이 뒤집혀 T1 이 왼쪽 — 등 쪽 면(Z −30 부근)을 역바이트로 깎는 값 */
    fun point(kind: TaperKind, type: Int, reverse: Boolean, name: String): String {
        val (xs, zs) = forward(kind, type)
        val i = name.drop(1).toInt() - 1
        val v = if (name[0] == 'x') xs[i] else if (reverse) -30 - zs[i] else zs[i]
        return if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
    }

    /** 홈(calc-design.md 의 예: Z −13, −15.5, −18) */
    val groove = mapOf("zStart" to "-10", "width" to "8", "od" to "40", "bottom" to "30", "xClear" to "1.0")
}

/** 이 상태(종류 · 형상 · 방향)의 점 X · Z 와 Z 여유, 또는 홈 치수에 [TaperExample] 값을 넣는다 */
internal fun TaperState.example(): TaperState = apply {
    if (kind.groove) {
        TaperExample.groove.forEach { (k, v) -> setShared(k, v) }
    } else {
        for (i in 1..kind.points) for (a in "xz") setPoint("$a$i", TaperExample.point(kind, type!!, reverse, "$a$i"))
        setShared("zClear", "1.0")
    }
}
