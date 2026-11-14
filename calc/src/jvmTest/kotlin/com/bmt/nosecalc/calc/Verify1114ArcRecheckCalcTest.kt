package com.bmt.nosecalc.calc

import com.bmt.nosecalc.calc.sim.MoveStep
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test

/**
 * 재검(2026-11-14): 고침 단계가 F1(내경 T1–T2 호 접선 진입을 축에서 자름) 뒤 `V1113.g40Worse` 잣대에서 내경 글의 첫 줄(공구 교환 자리 → 진입 G00)을 빼고 잰다 —
 * 그 잣대가 잡던 사례가 무엇인지(첫 줄만의 일인지, 진입점이 어디로 갔는지) 옛 jar(b305c71)와 견주어 보인다. `Verify1113RegressionTest.taperSameAsOldOutsideFlip` 과
 * 같은 씨앗 · 같은 난수 차례로 홑테이퍼 입력을 다시 만들고, 내경 원호만 옛 잣대(첫 줄 포함) · 새 잣대(첫 줄 뺌)로 잰다. 알림만(실패하지 않는다) —
 * build/verify1114arcrecheck/v1113-firstline.txt
 */
class Verify1114ArcRecheckCalcTest {
    private fun r3(v: Double) = Math.round(v * 1000) / 1000.0

    private fun deepest(ms: List<MoveStep>, r: Double, mat: List<Seg>): Pair<Double, MoveStep?> {
        var worst = Double.NEGATIVE_INFINITY
        var by: MoveStep? = null
        for (m in ms) for (p in V1112.samples(m.nose)) {
            val d = r - V1112.minDist(mat, p)
            if (d > worst) {
                worst = d
                by = m
            }
        }
        return worst to by
    }

    @Test
    fun firstLineGaugeCases() {
        assumeTrue(V1112.oldJar.isFile, "옛 jar 가 없어 건너뜀: ${V1112.oldJar}")
        val out = StringBuilder()
        val rnd = Random(1313)
        val combos = listOf(Triple(false, false, true), Triple(false, false, false), Triple(true, false, false), Triple(false, true, true), Triple(true, true, true))
        val crs = listOf(0.0, 0.0, 0.2, 0.5, 1.0)
        var innerArc = 0
        var changed = 0
        var oldGaugeOnly = 0
        repeat(2500) {
            val inner = rnd.nextBoolean()
            val type = 1 + rnd.nextInt(5)
            val r = listOf(0.2, 0.4, 0.8, 1.2)[rnd.nextInt(4)]
            val tip = if (rnd.nextInt(3) == 0) rnd.nextInt(10) else null
            val up = if (inner == (type == 5)) 1.0 else -1.0
            val dx = r3(rnd.nextDouble(0.5, 20.0))
            val len = r3(rnd.nextDouble(0.5, 15.0))
            val x1 = r3(if (up > 0) rnd.nextDouble(2.0, 60.0) else rnd.nextDouble(dx + 1.0, dx + 60.0))
            val x2 = r3(x1 + up * dx)
            val kind = rnd.nextInt(3)
            val arcR = if (kind > 0) TaperCalc.arcMinR(x1, 0.0, x2, -len)?.let { ceil(it * listOf(1.0, 1.3, 3.0)[rnd.nextInt(3)] * 1000) / 1000 } else null
            val zc = listOf(0.0, 0.5, 1.0, 3.0, 5.0)[rnd.nextInt(5)]
            val lead = listOf(null, 0.5, 1.0, 3.0)[rnd.nextInt(4)]
            val tail = listOf(null, 0.5, 1.0, 3.0)[rnd.nextInt(4)]
            val axisBottom = inner && (type == 2 || type == 4) && rnd.nextInt(3) == 0
            for ((flip, reverse, approach) in combos) {
                var i = V1113.taper(inner, type, r, zc, tip, lead, tail, crs[rnd.nextInt(5)], x1, x2, len, arcR, kind == 1, false, reverse, approach, crs[rnd.nextInt(5)])
                if (!inner || arcR == null) continue
                if (axisBottom) i = i.copy(end = 0.0)
                if (flip) i = i.copy(x1 = i.x2, z1 = i.z2, x2 = i.x1, z2 = i.z1, cornerR1 = i.cornerR2, cornerR2 = i.cornerR1, start = i.end, end = i.start, flip = true)
                innerArc++
                val c = V1112.Case(i.toString(), inner, r, max(zc, r), single = i)
                val res = c.calc()
                val s = res.solution ?: continue
                val o = V1112.oldTaper(c)
                val old = o.nc40 ?: continue
                if (s.ncG40 == old || V1112.axisEnd(s, inner)) continue
                changed++
                val segs = s.drawing.segs
                val rg = V1113.cutRange(s)
                if (rg.isEmpty()) continue
                val cut = segs.subList(rg.first, rg.last + 1)
                val mat = cut + cut.map(V1112::mirror) + V1112.walls(segs, inner, s.fromStart, s.toEnd)
                val nNew = V1112.direct(s.ncG40)
                val nOld = V1112.direct(old)
                val a = V1112.sim(nNew, r, s.tip)
                val b = V1112.sim(nOld, r, s.tip)
                if (a.stop != null || b.stop != null) continue
                val from = V1112.pre.size + 1
                val (gNewAll, byNew) = deepest(V1113.moves(a), r, mat)
                val (gOldAll, _) = deepest(V1113.moves(b), r, mat)
                val (gNewRest, _) = deepest(V1113.moves(a).filter { it.line > from }, r, mat)
                val (gOldRest, _) = deepest(V1113.moves(b).filter { it.line > from }, r, mat)
                val oldGauge = gNewAll > max(gOldAll, 0.0) + 2e-3 && gOldAll < r - 0.02
                val newGauge = gNewRest > max(gOldRest, 0.0) + 2e-3 && gOldRest < r - 0.02
                if (oldGauge && !newGauge) {
                    oldGaugeOnly++
                    val zMax = segs.maxOf { max(it.start.z, it.end.z) }
                    out.appendLine("---- 옛 잣대만 잡음: 새 ${V1113.f(gNewAll)}(첫 줄 뒤 ${V1113.f(gNewRest)}) · 옛 ${V1113.f(gOldAll)}(첫 줄 뒤 ${V1113.f(gOldRest)}) · 가장 깊은 새 줄 ${byNew?.let { it.line - V1112.pre.size }}")
                    out.appendLine("입력 $i · 경고 ${res.warnings} · 그림 Z 최대 ${V1113.f(zMax)} · 새 진입 ${s.entry} · 접근 ${s.approach}")
                    out.appendLine("새 G40 ${s.ncG40}")
                    out.appendLine("옛 G40 $old")
                    out.appendLine("도면 ${segs.joinToString(" | ")}")
                }
            }
        }
        out.insert(0, "내경 원호 $innerArc · 직접 보정 글 바뀜 $changed · 옛 잣대(첫 줄 포함)만 잡은 것 $oldGaugeOnly\n")
        File("build/verify1114arcrecheck").mkdirs()
        File("build/verify1114arcrecheck/v1113-firstline.txt").writeText(out.toString())
        println(out)
    }
}
