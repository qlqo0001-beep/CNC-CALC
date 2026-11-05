package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 소재 · 재료 제거(설계 5 · 9.2 · 9.3 `StockTest`): 손 계산, 따로 짠 표본 깎기, 급송 깎음 · 척 닿음, 나사 특징, 되감기, 자동 소재, 공구 단면.
 */
class StockTest {
    private val od = mapOf(1 to ToolOffset(0.4, 3))
    private val bar50 = StockSpec(50.0, 40.0, 0.0, 0.0, 125.0)

    private fun cut(lines: List<String>, spec: StockSpec = bar50, tools: Map<Int, ToolOffset> = od): StockSim {
        val run = SimKit.ok(SimKit.PRE + lines + "M30", tools)
        return StockSim(spec, run, tools).also { it.cutTo(run.totalSec) }
    }

    private fun col(s: StockSim, z: Double) = ((z - s.chuckZ) / s.dz).toInt()

    /** 9.2: Ø50 · G01 X40. → Z-30., 팁 3 · 0.4R · C 95° — z ∈ (−29.6, 0) 반경 20.000 ±1e-9, 어깨 안쪽 모서리에 반지름 r 살 */
    @Test
    fun turnStep() {
        val s = cut(listOf("G00 X40. Z2.", "G01 Z-30. F0.2", "G01 X52.", "G00 Z10."))
        var checked = 0
        for (i in 0 until s.columns) {
            val z = s.zOf(i)
            val iv = s.intervals(i)
            when {
                z > -29.6 + 1e-6 && z < 0 -> {
                    assertEquals(2, iv.size, "z $z: ${iv.toList()}")
                    assertTrue(abs(iv[1] - 20.0) <= 1e-9, "z $z 반경 ${iv[1]}")
                    checked++
                }
                z < -30 - 1e-6 -> assertEquals(25.0, iv.last(), "z $z 는 깎지 않음")
                z in -29.999..-29.6 -> {
                    // 노즈 원(중심 Z-29.6 · 반경 20.4) 아래 반 — 현으로 나눈 노즈라 원까지 거리(법선)가 현 높이 0.001 안
                    val d = sqrt((z + 29.6) * (z + 29.6) + (iv.last() - 20.4) * (iv.last() - 20.4))
                    assertTrue(abs(d - 0.4) <= ToolShape.SAG + 1e-9, "살 z $z: ${iv.last()} 원까지 ${d - 0.4}")
                }
            }
        }
        assertTrue(checked > 2900, "확인한 열 $checked")
        assertTrue(s.contacts.isEmpty(), "닿음 없음: ${s.contacts}")
    }

    /**
     * 따로 짠 표본 깎기: 경로를 0.005 mm 마다 멈춰 공구 다각형을 찍고(열 가운데 세로선과의 교차), 위에서부터 깎인 반경을 잰다 — 래스터와 0.01 mm 안.
     * 직선 · 오목/볼록 원호 · 테이퍼 · 면.
     */
    @Test
    fun matchesStampedReference() {
        val prog = listOf(
            "G00 X30. Z2.", "G01 Z0. F0.2", "G01 X34. Z-2.", "G01 Z-10.", "G02 X44. Z-15. R5.", "G01 Z-20.",
            "G03 X50. Z-23. R3.", "G01 X52.", "G00 Z5.",
        )
        val s = cut(prog)
        val shape = ToolShape.outline(ToolOffset(0.4, 3))
        val run = s.run
        val samples = ArrayList<Pt>()
        for (m in run.steps.filterIsInstance<MoveStep>()) {
            val len = when (val t = m.tip) {
                is Line -> (t.end - t.start).length
                is Arc -> t.radius * t.sweepDeg * kotlin.math.PI / 180
            }
            val n = max(1, ceil(len / 0.005).toInt())
            for (k in 0..n) samples += SimRun.at(m.tip, k.toDouble() / n)
        }
        var worst = 0.0
        var z = -38.0
        while (z < -0.01) {
            val i = col(s, z)
            val zc = s.zOf(i)
            // 표본 쪽: 구간 목록에서 공구 찍기마다 [lo, hi] 를 뺀다(따로 짠 것)
            var left = listOf(0.0 to 25.0)
            for (p in samples) {
                val (lo, hi) = cross(shape.map { it + p }, zc) ?: continue
                left = left.flatMap { (a, b) ->
                    if (hi <= a || lo >= b) listOf(a to b)
                    else listOfNotNull((a to lo).takeIf { lo > a + 1e-9 }, (hi to b).takeIf { b > hi + 1e-9 })
                }
            }
            val ref = left.maxOfOrNull { it.second } ?: 0.0
            val raster = s.intervals(i).lastOrNull() ?: 0.0
            worst = max(worst, abs(raster - ref))
            assertTrue(abs(raster - ref) <= 0.01, "z $zc 래스터 $raster 표본 $ref")
            z += 0.137
        }
        println("StockTest.matchesStampedReference: 가장 큰 차이 $worst mm")
    }

    /** 볼록 다각형과 세로선 z 의 교차 [lo, hi] */
    private fun cross(poly: List<Pt>, z: Double): Pair<Double, Double>? {
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if (z < min(a.z, b.z) || z > max(a.z, b.z)) continue
            if (abs(b.z - a.z) < 1e-15) {
                lo = min(lo, min(a.x, b.x))
                hi = max(hi, max(a.x, b.x))
            } else {
                val x = a.x + (b.x - a.x) * (z - a.z) / (b.z - a.z)
                lo = min(lo, x)
                hi = max(hi, x)
            }
        }
        return if (lo <= hi) lo to hi else null
    }

    /** 급송이 소재를 지나면 RAPID_CUT — 처음 닿는 자리는 끝면(Z0) 바로 안 */
    @Test
    fun rapidCut() {
        val s = cut(listOf("G00 X40. Z2.", "G00 Z-10.", "G01 X60. F0.2"))
        val c = s.contacts.single { it.kind == ContactKind.RAPID_CUT }
        assertEquals(5, c.line)
        assertTrue(c.at.z in -0.1..0.0 && abs(c.at.x - 20.0) < 1e-9, "닿은 자리 ${SimKit.xz(c.at)}")
        assertTrue(c.area > 10.0, "넓이 ${c.area}")
        val m = s.run.steps[c.step] as MoveStep
        assertTrue(c.t > m.t0 && c.t < m.t1)
        // 이송이 깎은 것은 경고 없음
        assertTrue(cut(listOf("G00 X40. Z2.", "G01 Z-10. F0.2", "G01 X60.", "G00 Z5.")).contacts.isEmpty())
    }

    /**
     * 형식 C 의 이동 없는 G40 옮김(설계 3.2 · 5.5): 끝면을 중심 쪽으로 깎고 끝낸 G41 글 — 팁 4(바이트 반대쪽)면 옮김이 끝 법선(Z0.4)에서
     * 지령점 − 팁 벡터(Z-0.4)로 끝면 속을 지나 CANCEL_CUT(노랑, 7줄). 표준 팁 3 은 깎은 끝면을 따라 올라가 닿음 없음. 형식 A · B 는 그 줄에서 움직이지 않는다.
     * 공구는 둥근(노즈 원만) — 바이트 몸통이 끝면 깎기에서 먼저 소재를 지우지 않게
     */
    @Test
    fun cancelShiftCut() {
        val text = listOf("G00 X44. Z2.", "G41 G01 X40. Z0. F0.1", "G01 X10.", "G40", "G00 Z5.")
        val t4 = mapOf(1 to ToolOffset(0.4, 4, ToolKind.ROUND))
        val bad = cut(text, tools = t4)
        val c = bad.contacts.single { it.kind == ContactKind.CANCEL_CUT }
        assertEquals(7, c.line)
        assertEquals(StepSrc.SHIFT_CANCEL, (bad.run.steps[c.step] as MoveStep).src)
        assertTrue(c.area > 0.1, "넓이 ${c.area}")
        assertTrue(cut(text, tools = mapOf(1 to ToolOffset(0.4, 3, ToolKind.ROUND))).contacts.isEmpty(), "팁 3")
        val ab = SimKit.ok(SimKit.PRE + text + "M30", t4, MachineSetup(compStartup = CompStartup.AB))
        assertTrue(StockSim(bar50, ab, t4).also { it.cutTo(ab.totalSec) }.contacts.none { it.kind == ContactKind.CANCEL_CUT })
    }

    /**
     * G40 옮김이 깎은 면을 따라 미끄러지면(외경 어깨를 타고 X32 로 뺀 뒤 표준 팁 3 — 옮김 · nₑ = 0, 노즈가 +X 로 0.4) 그 너머 소재를 깎아도
     * CANCEL_CUT 이 아니다(설계 5.5 — 자동 소재가 크게 잡은 빈 자리의 헛경보). 같은 글에 팁 4 면 옮김이 어깨(앞 블록 면) 속으로 들어가 CANCEL_CUT(8줄)
     */
    @Test
    fun cancelShiftSlideAlongFaceIsNotCut() {
        val text = listOf("G00 X26. Z2.", "G42 G01 X26. Z0. F0.1", "G01 Z-19.", "G01 X32. F0.3", "G40", "G01 X60.", "G00 Z5.")
        val t3 = mapOf(1 to ToolOffset(0.4, 3, ToolKind.ROUND))
        val slide = cut(text, tools = t3)
        assertTrue(slide.contacts.isEmpty(), "팁 3 ${slide.contacts}")
        val m8 = slide.run.steps.filterIsInstance<MoveStep>().single { it.line == 8 }
        assertEquals(StepSrc.SHIFT_CANCEL, m8.src)
        assertTrue((m8.nose.end - Pt.ofDia(-18.6, 32.8)).length < 1e-9, "팁 3 옮김 끝 ${SimKit.xz(m8.nose.end)}")
        // 옮김은 소재를 깎기는 한다(둥근 공구라 어깨 위가 남아 있음) — 그래도 경고는 없다
        fun area(): Double = (0 until slide.columns).sumOf { i -> slide.intervals(i).toList().chunked(2).sumOf { (a, b) -> b - a } * slide.dz }
        slide.cutTo(m8.t0)
        val before = area()
        slide.cutTo(m8.t1)
        assertTrue(before - area() > 0.1, "옮김이 깎은 넓이 ${before - area()}")
        val t4 =mapOf(1 to ToolOffset(0.4, 4, ToolKind.ROUND))
        val dig = cut(text, tools = t4)
        assertEquals(listOf(ContactKind.CANCEL_CUT to 8), dig.contacts.map { it.kind to it.line })
    }

    /** 척(척 면 Z−40 왼쪽, 반경 < 척 지름/2)에 들어가면 CHUCK — 시각은 닿는 순간 */
    @Test
    fun chuckHit() {
        val s = cut(listOf("G00 X40. Z2.", "G01 Z-45. F0.2", "G01 X60."))
        // 척에 든 채 X 로 올라가는 다음 이송도 닿음 — 첫 것이 그 이송
        val c = s.contacts.first { it.kind == ContactKind.CHUCK }
        assertEquals(5, c.line)
        assertTrue(c.at.z in -40.1..-39.9, "닿은 자리 ${SimKit.xz(c.at)}")
        val m = s.run.steps[c.step] as MoveStep
        assertTrue(c.t > m.t0 && c.t < m.t1)
        // 척 면에 닿기만(공구 왼쪽 끝 = 척 면)은 아님 — 팁 3 의 가장 왼쪽은 가상 인선 Z
        val touch = cut(listOf("G00 X40. Z2.", "G01 Z-39.999 F0.2", "G01 X60."))
        assertTrue(touch.contacts.none { it.kind == ContactKind.CHUCK }, "${touch.contacts}")
    }

    /** 되감기 = 처음부터 깎은 것(저장점에서 다시 — 이동 1 000 개 넘는 G71) */
    @Test
    fun rewindEqualsFresh() {
        val text = SimExamples.all[0].text.replace("G71U1.5R.5", "G71U.05R.02")
        val run = GSim.run(SimInput(text, MachineSetup(), SimExamples.all[0].tools))
        assertTrue(run.stop == null)
        val moves = run.steps.count { it is MoveStep }
        assertTrue(moves > 1000, "이동 $moves")
        val spec = assertNotNull(StockAuto.of(run))
        val a = StockSim(spec, run, SimExamples.all[0].tools)
        a.cutTo(run.totalSec)
        for (t in listOf(run.totalSec * 0.37, run.totalSec * 0.81, run.totalSec * 0.1)) {
            a.cutTo(t)
            val b = StockSim(spec, run, SimExamples.all[0].tools)
            b.cutTo(t)
            for (i in 0 until a.columns) assertTrue(a.intervals(i).contentEquals(b.intervals(i)), "t $t 열 $i")
        }
    }

    /** 나사 패스는 래스터를 깎지 않고 특징으로 — S 4-95 G92(골 X62.64 · 리드 6 · 바깥) */
    @Test
    fun threadFeature() {
        val ex = SimExamples.all[5]
        val run = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        val spec = assertNotNull(StockAuto.of(run))
        // 이송이 나사뿐 — 지름은 나사 가장 큰 X(66.4)를 올림, 끝면 = 나사 시작 Z76.2 − 3 리드
        assertEquals(67.0, spec.dia)
        assertEquals(76.2 - 18, spec.faceZ, 1e-9)
        val s = StockSim(spec, run, ex.tools)
        s.cutTo(run.totalSec)
        assertTrue(s.contacts.isEmpty(), "${s.contacts}")
        val th = s.profile().threads.single()
        assertTrue(th.outer)
        assertEquals(6.0, th.lead)
        assertEquals(60.0, th.angleDeg)
        assertEquals(31.32, th.root.start.x, 1e-9)
        assertEquals(33.5, assertNotNull(s.outerAt(40.0)), 1e-12)
        // 첫 패스 전에는 나사 없음, 둘째 패스 동안은 둘째 패스 깊이(X65)
        s.cutTo(0.0)
        assertTrue(s.profile().threads.isEmpty())
        val second = run.steps.filterIsInstance<MoveStep>().filter { it.kind == MoveKind.THREAD }[1]
        s.cutTo((second.t0 + second.t1) / 2)
        assertEquals(32.5, s.profile().threads.single().root.start.x, 1e-9)
    }

    /** 자동 소재(5.1): 가장 큰 이송 X 올림 · 끝면 = 가장 큰 이송 Z · 돌출 +10 · 척 2.5 배(100~300) */
    @Test
    fun autoStock() {
        val run = SimKit.ok(SimKit.PRE + listOf("G00 X40. Z2.", "G01 Z-30. F0.2", "G01 X52.", "G00 Z10.", "M30"))
        val a = assertNotNull(StockAuto.of(run))
        assertEquals(53.0, a.dia)
        assertEquals(2.0, a.faceZ)
        assertEquals(42.0, a.length)
        assertEquals(132.5, a.chuckDia)
        assertEquals(Pt(102.0, 100.0), StockAuto.home(a.dia, a.faceZ))
        // 원호의 가장 큰 X(볼록한 데)
        val arc = SimKit.ok(SimKit.PRE + listOf("G00 X40. Z2.", "G01 Z0. F0.2", "G03 X40. Z-20. R12.", "G00 X60.", "M30"))
        val r = sqrt(144.0 - 100.0)
        assertEquals(kotlin.math.floor(2 * (20 + 12 - r)) + 1, assertNotNull(StockAuto.of(arc)).dia)
        // 이송이 없으면 없음
        assertEquals(null, StockAuto.of(SimKit.ok(SimKit.PRE + listOf("G00 X40. Z2.", "M30"))))
    }

    /** 공구 단면: 반시계 · 볼록 · 가상 인선이 X · Z 끝(팁 1~4), 홈 폭, 나사 꼭짓점. 다이스 헤드(DIE)는 모양 없음(래스터를 깎지 않음 — 설계 19절) */
    @Test
    fun toolShapes() {
        for (kind in ToolKind.entries) for (tip in 0..9) for (ins in InsertShape.entries) {
            val t = ToolOffset(0.4, tip, kind, ins, width = 3.0, dieDia = 12.0, diePitch = 1.75)
            val p = ToolShape.outline(t)
            if (kind == ToolKind.DIE) {
                assertTrue(p.isEmpty(), "$t")
                continue
            }
            assertTrue(p.size >= 3, "$t")
            assertTrue(ToolShape.area(p) > 0, "반시계 $t")
            for (i in p.indices) {
                val a = p[i]
                val b = p[(i + 1) % p.size]
                val c = p[(i + 2) % p.size]
                assertTrue((b - a).cross(c - b) >= -1e-12, "볼록 $t @$i")
            }
            if (kind == ToolKind.TURN && tip in 1..4 || kind == ToolKind.GROOVE && tip in 1..4) {
                // 가상 인선 = 노즈 원에 Z · X 로 접하는 두 선의 교점 → 다각형의 그쪽 끝이 0
                val tv = NoseComp.tipVector(tip, 0.4)
                val (lo, hi) = ToolShape.box(p)
                assertEquals(0.0, if (tv.z < 0) lo.z else hi.z, 1e-12, "Z 끝 $t")
                assertEquals(0.0, if (tv.x < 0) lo.x else hi.x, 1e-12, "X 끝 $t")
            }
        }
        val g = ToolShape.outline(ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0))
        val (glo, ghi) = ToolShape.box(g)
        assertEquals(0.0, glo.z, 1e-12)
        assertEquals(3.0, ghi.z, 1e-12)
        val th = ToolShape.outline(ToolOffset(0.0, 0, ToolKind.THREAD))
        val apex = th.minBy { it.x }
        assertTrue(abs(apex.z) < 1e-12 && abs(apex.x) < 1e-12, "꼭짓점 $apex")
        // 모양 없음: 노즈 0 · 팁 0 바이트, 폭 없는 홈
        assertTrue(ToolShape.outline(ToolOffset()).isEmpty())
        assertTrue(ToolShape.outline(ToolOffset(0.0, 3, ToolKind.GROOVE)).isEmpty())
    }

    /**
     * 다이스 헤드 나사 모양(설계 19절 — 모형): 다이스만 있는 글(지은 치수 — d 10 · P 1.5 · 55°, S300 · F1.44 · Z5 → −16)을 Ø10 봉재에서.
     * 래스터는 그대로(반경 5 — 다이스는 깎지 않음, 닿음 없음), 골 = 수평선 반경 d/2 − 0.640327P = 5 − 0.9604905 = 4.0395095 · Z5 ~ −16 · 리드 1.5(F 1.44 가 아님) · 55° · 바깥.
     * 같은 글을 60° 로 하면 골 = 5 − 0.613435 × 1.5 = 5 − 0.9201525 = 4.0798475
     */
    @Test
    fun dieThreadFeature() {
        val text = listOf("T0505", "G97S300M03", "G0X0.Z20.", "Z5.", "G1Z-16.F1.44", "G4U1.", "G0Z150.", "M30")
        val spec = StockSpec(10.0, 30.0, 0.0, 0.0, 100.0)
        for ((deg, root) in listOf(55.0 to 4.0395095, 60.0 to 4.0798475)) {
            val tools = mapOf(5 to ToolOffset(kind = ToolKind.DIE, threadDeg = deg, dieDia = 10.0, diePitch = 1.5))
            val run = SimKit.ok(text, tools)
            val s = StockSim(spec, run, tools)
            s.cutTo(run.totalSec)
            assertTrue(s.contacts.isEmpty(), "${s.contacts}")
            for (i in 0 until s.columns) assertTrue(s.intervals(i).contentEquals(doubleArrayOf(0.0, 5.0)), "열 $i 는 그대로")
            val th = s.profile().threads.single()
            assertEquals(1.5, th.lead)
            assertEquals(deg, th.angleDeg)
            assertTrue(th.outer)
            assertEquals(5, th.line)
            assertEquals(5.0, th.root.start.z, 1e-12)
            assertEquals(-16.0, th.root.end.z, 1e-12)
            assertEquals(root, th.root.start.x, 1e-12)
            assertEquals(root, th.root.end.x, 1e-12)
        }
    }

    /**
     * 자동 소재와 다이스 헤드(5.1 · 설계 19절): 다이스 이송은 나사처럼 본다.
     * - 다이스만(d 12 · P 1.75, G1 Z6 → −20): 지름 = 다이스 지름/2 = 6 → floor(12) + 1 = 13, 끝면 = 시작 Z − 3P = 6 − 5.25 = 0.75,
     *   돌출 = 0.75 − (−20) + 10 = 30.75, 척 = 2.5 × 13 = 32.5 → 100(아래 한도).
     * - 예제 ⑮(바이트로 끝면 · 외경을 깎은 뒤 다이스): 다이스 이송(X0 · Z6 부터)은 넣지 않는다 — 이송 가장 큰 X 는 빼는 줄 X20 → 21 이지만
     *   첫 이송 앞 급송 끝(Z0 · X18)이 소재 안이라 지름을 18 로 줄이고(18.5 절 ① 과 같은 규칙: 21/2 − 9 = 1.5 ≤ 끝면 3 − 0), 끝면 = 가장 큰 이송 Z = 면취 앞 G1 의 시작 Z3,
     *   돌출 = 3 − (−24) + 10 = 37.
     */
    @Test
    fun dieAutoStock() {
        val tools = mapOf(5 to ToolOffset(kind = ToolKind.DIE, dieDia = 12.0, diePitch = 1.75))
        val only = SimKit.ok(listOf("T0505", "G97S230M03", "G0X0.Z20.", "Z6.", "G1Z-20.F1.68", "G4U1.", "G0Z150.", "M30"), tools)
        val a = assertNotNull(StockAuto.of(only))
        assertEquals(13.0, a.dia)
        assertEquals(0.75, a.faceZ, 1e-12)
        assertEquals(30.75, a.length, 1e-12)
        assertEquals(0.0, a.boreDia)
        assertEquals(100.0, a.chuckDia)
        val ex = SimExamples.all.single { it.text.startsWith("O9023") }
        val b = assertNotNull(StockAuto.of(GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))))
        assertEquals(18.0, b.dia)
        assertEquals(3.0, b.faceZ, 1e-12)
        assertEquals(37.0, b.length, 1e-12)
    }

    /** 단면 조각: 홈 하나 — 위 · 아래로 갈라진 곳 없이, 테두리에 홈 벽이 들어 있다 */
    @Test
    fun profileOfGroove() {
        val tools = mapOf(3 to ToolOffset(0.0, 3, ToolKind.GROOVE, width = 3.0))
        val run = SimKit.ok(listOf("T0303", "G97 S800 M03", "G99", "G00 X52. Z-10.", "G01 X40. F0.05", "G00 X52.", "M30"), tools)
        val s = StockSim(bar50, run, tools)
        s.cutTo(run.totalSec)
        val p = s.profile(0.001)
        assertEquals(1, p.pieces.size)
        val pts = p.pieces[0].outline
        // 홈 바닥 반경 20 이 Z−10 ~ −7 에
        assertTrue(pts.any { abs(it.x - 20.0) < 1e-9 && it.z in -10.01..-9.99 })
        assertTrue(pts.any { abs(it.x - 20.0) < 1e-9 && it.z in -7.01..-6.99 })
        val walls = p.pieces[0].edges.flatten()
        assertTrue(walls.any { abs(it.z + 10.0) < 0.011 && abs(it.x - 25.0) < 1e-9 })
    }

    /**
     * 재생 한 프레임 값(궤적 창이 프레임마다 하는 일 = cutTo(t) + profile): 이동 1 000 개 넘는 G71(U0.05)을 ×200 으로 60 fps 재생하는 걸음 —
     * 한 프레임 평균 · 가장 긴 것을 적는다(느린 CI 를 위해 평균 50 ms 로만 막음). 뒤로 감기(저장점에서 다시)도 한 번.
     */
    @Test
    fun frameCost() {
        val ex = SimExamples.all[0]
        val text = ex.text.replace("G71U1.5R.5", "G71U.05R.02")
        val run = GSim.run(SimInput(text, MachineSetup(), ex.tools))
        val spec = assertNotNull(StockAuto.of(run))
        val s = StockSim(spec, run, ex.tools)
        s.cutTo(run.totalSec)
        s.cutTo(0.0)
        repeat(30) { s.cutTo(it * 0.2); s.profile(0.004) } // 데우기
        s.cutTo(0.0)
        var worst = 0.0
        var sum = 0.0
        var n = 0
        var t = 0.0
        while (t < run.totalSec) {
            t += 200.0 / 60
            val m = kotlin.time.TimeSource.Monotonic.markNow()
            s.cutTo(t)
            s.profile(0.004)
            val ms = m.elapsedNow().inWholeMicroseconds / 1000.0
            worst = max(worst, ms)
            sum += ms
            n++
        }
        val back = kotlin.time.TimeSource.Monotonic.markNow()
        s.cutTo(run.totalSec * 0.3)
        s.profile(0.004)
        val backMs = back.elapsedNow().inWholeMicroseconds / 1000.0
        println("StockTest.frameCost: 이동 ${run.steps.count { it is MoveStep }} · 프레임 $n · 평균 ${sum / n} ms · 가장 긴 $worst ms · 뒤로 감기 $backMs ms")
        assertTrue(sum / n < 50)
    }

    /** 속도(설계 5.4 목표 PC 50 ms) — 09 꼴 G71 · G70 전체 깎기 시간을 적는다(느린 CI 를 위해 2 초로만 막음) */
    @Test
    fun speedOf09() {
        val ex = SimExamples.all[0]
        val run = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        val spec = assertNotNull(StockAuto.of(run))
        repeat(5) { StockSim(spec, run, ex.tools).apply { cutTo(run.totalSec) }.profile() } // 데우기
        val t0 = kotlin.time.TimeSource.Monotonic.markNow()
        val s = StockSim(spec, run, ex.tools)
        s.cutTo(run.totalSec)
        val cutMs = t0.elapsedNow().inWholeMicroseconds / 1000.0
        val t1 = kotlin.time.TimeSource.Monotonic.markNow()
        s.profile()
        val profMs = t1.elapsedNow().inWholeMicroseconds / 1000.0
        println("StockTest.speedOf09: 이동 ${run.steps.count { it is MoveStep }} · 열 ${s.columns} · 깎기 $cutMs ms · 단면 $profMs ms")
        assertTrue(cutMs < 2000)
    }
}
