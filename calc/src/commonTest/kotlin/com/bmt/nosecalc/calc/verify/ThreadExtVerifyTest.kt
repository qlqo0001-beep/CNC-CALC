package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieInput
import com.bmt.nosecalc.calc.DieWarning
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSeries
import com.bmt.nosecalc.calc.ThreadSpec
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ThreadTol
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.calc.sim.DwellStep
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.SimExamples
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.StepSrc
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NBS Handbook H28 (1969) Part I 5 · 6 절의 UN 한계 치수 — 앱 코드를 부르지 않는 따로 짠 모형(V1 독립 검증, 2026-11-06).
 * 인치. D = 호칭 지름, p = 1/산수. 물림 길이 LE: UNC · UNF = D, UNEF · 20UN(UN) = 9p (H28 6.1 p. 2.20 · 7절 p. 2.21).
 * 피치경 공차 = C·(0.0015·D^(1/3) + 0.0015·√LE + 0.015·p^(2/3)), C 1A 1.5 · 2A 1 · 3A 0.75 · 1B 1.95 · 2B 1.3 · 3B 0.975,
 * 허용차 = 0.3 × (C 1 합)(1A · 2A), 바깥지름 공차 0.060·p^(2/3)(1A 0.090), 기본 E = D − 0.649519p · Ks = D − 1.226869p · Kn = D − 1.082532p.
 * 암나사 안지름 공차(ASME B1.1 의 식 — 기억으로 적고 JSON 98줄로 맞춰 봄): f = 0.05·p^(2/3) + 0.03·p/D − 0.002 를
 * 1B · 2B 는 [0.25p − 0.4p², 0.394p], 3B 는 [13산 이상 0.23p − 1.5p² · 12산 이하 0.120p, 0.394p] 로 자른다.
 * [rounded] = true 면 표 2.21 의 반올림 절차(증분 6자리 · 공차 · 허용차 · 기본 치수 4자리, 안지름 최소 3자리, 1B · 2B 최대 3자리).
 */
internal object H28Model {
    private val C = mapOf("1A" to 1.5, "2A" to 1.0, "3A" to 0.75, "1B" to 1.95, "2B" to 1.3, "3B" to 0.975)

    /** 소수 n 자리, 반은 올림(양수만) */
    fun rnd(x: Double, n: Int): Double {
        val f = 10.0.pow(n)
        return floor(x * f + 0.5 + 1e-6) / f
    }

    /** 표 2.21 칸 이름 그대로(majorMax … allowance · pitchTol). 숫나사 minorMax = Ks − 허용차(표의 숫나사 골 칸) */
    data class Cells(
        val majorMax: Double?, val majorMin: Double, val pitchMax: Double, val pitchMin: Double,
        val minorMax: Double, val minorMin: Double?, val allowance: Double?, val pitchTol: Double,
    )

    fun le(series: String, d: Double, p: Double): Double = when (series) {
        "UNC", "UNF" -> d
        "UNEF", "UN" -> 9 * p
        else -> error(series)
    }

    fun cells(d: Double, tpi: Int, series: String, cls: String, rounded: Boolean, leOverride: Double? = null): Cells {
        val p = 1.0 / tpi
        val le = leOverride ?: le(series, d, p)
        fun q(x: Double, n: Int) = if (rounded) rnd(x, n) else x
        val base = q(0.0015 * cbrt(d), 6) + q(0.0015 * sqrt(le), 6) + q(0.015 * p.pow(2.0 / 3), 6)
        val e = q(d - 0.649519 * p, 4)
        val ks = q(d - 1.226869 * p, 4)
        val kn = d - 1.082532 * p
        val tol = q(C.getValue(cls) * base, 4)
        return if (cls.endsWith("A")) {
            val es = if (cls == "3A") 0.0 else q(0.3 * base, 4)
            val mt = q((if (cls == "1A") 0.090 else 0.060) * p.pow(2.0 / 3), 4)
            Cells(d - es, d - es - mt, e - es, e - es - tol, ks - es, null, es, tol)
        } else {
            val f = 0.05 * p.pow(2.0 / 3) + 0.03 * p / d - 0.002
            val lo = if (cls == "3B") (if (tpi >= 13) 0.23 * p - 1.5 * p * p else 0.120 * p) else 0.25 * p - 0.4 * p * p
            val mtol = min(max(f, lo), 0.394 * p)
            val minMin = if (rounded) rnd(kn, 3) else kn
            val minMax = when {
                !rounded -> kn + mtol
                cls == "3B" -> rnd(kn, 4) + rnd(mtol, 4)
                else -> rnd(rnd(kn, 4) + rnd(mtol, 4), 3)
            }
            Cells(null, d, e + tol, e, minMax, minMin, null, tol)
        }
    }
}

/**
 * V1 독립 검증 — 나사 확장(가지 thread-extension, a2a3818..fcac31f) 가운데 계산 · 규격.
 * 기대값은 규격 식 · 손 계산 · 위 [H28Model] 로 낸다(앱 출력을 기대값으로 쓰지 않는다). JSON 원본과의 대조는 jvmTest ThreadExtJsonVerifyTest.
 */
class ThreadExtVerifyTest {
    private val inch = 25.4

    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }

    // ───────────── 1. UNEF · 20UN ─────────────

    /**
     * ThreadTable.UN 의 모든 줄(UNC · UNF · UNEF · UN) × 그 계열에 허용된 급수: ThreadTol.limits = 반올림 없는 H28 모형(1e-6 mm).
     * UNC · UNF 줄은 기존 값의 회귀 확인을 겸한다(LE = D).
     */
    @Test
    fun unLimitsEqualIndependentModel() {
        val bad = mutableListOf<String>()
        var n = 0
        for (s in ThreadTable.UN) {
            val dIn = s.d / inch
            val tpi = s.tpi!!
            for (c in ThreadClass.choices(s.series, false) + ThreadClass.choices(s.series, true)) {
                n++
                val m = H28Model.cells(dIn, tpi, s.series.name, c.label, rounded = false)
                val lim = ThreadTol.limits(s, c)
                fun chk(what: String, model: Double, app: Double) {
                    if (abs(model * inch - app) > 2e-6) bad += // 6산 UNC: 앱 5√3/8 · 모형 1.082532 차 1.04e-6 mm
 "${s.label}-${c.label} $what: 모형 ${model * inch} · 앱 $app"
                }
                chk("피치경 최대", m.pitchMax, lim.pitchDia.max)
                chk("피치경 최소", m.pitchMin, lim.pitchDia.min)
                if (c.internal) {
                    chk("안지름 최소", m.minorMin!!, lim.crest.min)
                    chk("안지름 최대", m.minorMax, lim.crest.max)
                    chk("바깥지름 최소(골 한계)", m.majorMin, lim.rootLimit)
                    chk("EI", 0.0, lim.deviation)
                } else {
                    chk("바깥지름 최대", m.majorMax!!, lim.crest.max)
                    chk("바깥지름 최소", m.majorMin, lim.crest.min)
                    chk("es", -m.allowance!!, lim.deviation)
                }
            }
        }
        assertTrue(n > 400, "급수 줄 수 $n")
        assertTrue(bad.isEmpty(), "H28 모형과 다른 칸 ${bad.size}:\n" + bad.take(40).joinToString("\n"))
    }

    /** 판별 줄: 3/4-20 UNEF-2A 피치경 최소 — 표 0.7118. 9P 모형 0.71180 · D 모형 0.71142. 앱은 9P 쪽이어야 한다 */
    @Test
    fun engagementDiscriminator() {
        val s = un("3/4", 20)
        assertEquals(ThreadSeries.UNEF, s.series)
        val app = ThreadTol.limits(s, ThreadClass.UN2A).pitchDia.min / inch
        val le9p = H28Model.cells(0.75, 20, "UNEF", "2A", false).pitchMin
        val leD = H28Model.cells(0.75, 20, "UNEF", "2A", false, leOverride = 0.75).pitchMin
        assertTrue(abs(le9p - 0.7118) < 0.00005 && abs(leD - 0.7118) > 0.0003, "모형 자체: 9P $le9p · D $leD")
        assertTrue(abs(app - le9p) < 1e-8, "앱 $app · 9P $le9p · D $leD")
        // 1/2-20 UNF(20UN 겹침, UNF 규칙) 는 LE = D
        val unf = un("1/2", 20)
        assertEquals(ThreadSeries.UNF, unf.series)
        assertTrue(abs(ThreadTol.limits(unf, ThreadClass.UN2A).pitchDia.min / inch - H28Model.cells(0.5, 20, "UNF", "2A", false).pitchMin) < 1e-8)
        // 3/8-20 UN 은 9P
        val un20 = un("3/8", 20)
        assertEquals(ThreadSeries.UN, un20.series)
        assertTrue(abs(ThreadTol.limits(un20, ThreadClass.UN2A).pitchDia.min / inch - H28Model.cells(0.375, 20, "UN", "2A", false).pitchMin) < 1e-8)
    }

    /** 급수 거르기: UNEF · UN 은 2A · 3A · 2B · 3B(표 2.21 에 1A · 1B 줄 없음), UNC · UNF 는 여섯 다. 1A 를 넣으면 IllegalArgumentException(문서) */
    @Test
    fun classFiltering() {
        for (series in listOf(ThreadSeries.UNEF, ThreadSeries.UN)) {
            assertEquals(listOf(ThreadClass.UN2A, ThreadClass.UN3A), ThreadClass.choices(series, false), "$series 숫")
            assertEquals(listOf(ThreadClass.UN2B, ThreadClass.UN3B), ThreadClass.choices(series, true), "$series 암")
        }
        for (series in listOf(ThreadSeries.UNC, ThreadSeries.UNF)) {
            assertEquals(listOf(ThreadClass.UN1A, ThreadClass.UN2A, ThreadClass.UN3A), ThreadClass.choices(series, false), "$series 숫")
            assertEquals(listOf(ThreadClass.UN1B, ThreadClass.UN2B, ThreadClass.UN3B), ThreadClass.choices(series, true), "$series 암")
        }
        assertFailsWith<IllegalArgumentException> { ThreadTol.limits(un("3/4", 20), ThreadClass.UN1A) }
        assertFailsWith<IllegalArgumentException> { ThreadTol.limits(un("3/8", 20), ThreadClass.UN1B) }
        // 20UN 겹침 1/4-20 은 UNC 라 1A · 1B 가 있다(H28 3.6 — 그 계열의 공차 · 급수)
        assertEquals(ThreadSeries.UNC, un("1/4", 20).series)
        ThreadTol.limits(un("1/4", 20), ThreadClass.UN1A)
    }

    /** 20UN 묶음: 37 크기(1/4 ~ 3), 겹침 8개는 그 계열 줄. 크기 목록은 H28 표 2.16 (unified-extra.json series.UN20 에서 옮긴 이 시험의 자료) */
    @Test
    fun un20Group() {
        val sizes = listOf(
            "1/4", "5/16", "3/8", "7/16", "1/2", "9/16", "5/8", "11/16", "3/4", "13/16", "7/8", "15/16", "1", "1-1/16", "1-1/8", "1-3/16",
            "1-1/4", "1-5/16", "1-3/8", "1-7/16", "1-1/2", "1-9/16", "1-5/8", "1-11/16", "1-3/4", "1-13/16", "1-7/8", "1-15/16", "2",
            "2-1/8", "2-1/4", "2-3/8", "2-1/2", "2-5/8", "2-3/4", "2-7/8", "3",
        )
        val overlap = mapOf(
            "1/4" to ThreadSeries.UNC, "7/16" to ThreadSeries.UNF, "1/2" to ThreadSeries.UNF, "3/4" to ThreadSeries.UNEF,
            "13/16" to ThreadSeries.UNEF, "7/8" to ThreadSeries.UNEF, "15/16" to ThreadSeries.UNEF, "1" to ThreadSeries.UNEF,
        )
        val g = ThreadTable.group(UnGroup.UN20)
        assertEquals(sizes, g.map { it.size })
        for (s in g) {
            assertEquals(20, s.tpi, s.size)
            assertEquals(overlap[s.size] ?: ThreadSeries.UN, s.series, s.size)
            assertEquals(1.27, s.pitch, 1e-12)
        }
        // (사이즈, 산수)마다 한 줄 — 겹치는 줄은 같은 인스턴스
        assertEquals(ThreadTable.UN.size, ThreadTable.UN.map { it.size to it.tpi }.distinct().size)
        assertEquals(
            mapOf(ThreadSeries.UNC to 23, ThreadSeries.UNF to 24, ThreadSeries.UNEF to 25, ThreadSeries.UN to 29),
            ThreadTable.UN.groupingBy { it.series }.eachCount(),
        )
    }

    // ───────────── 3. 다이스 헤드 ─────────────

    private data class DieHand(val s: Long, val vcUsed: Double, val f: Double, val fNc: Double, val time: Double, val pull: Double, val full: Double?)

    /**
     * 손 계산: S = 1000·Vc/(π·d) 반올림(반 올림), F = 비율 × P, 시간 = 60·(시작 − 끝)/(F·S) + 멈춤, 완전 나사 끝 = 끝 + 목 × P.
     * 당김(운동학): 물린 뒤 기계가 Lm = 물림 Z − 끝 Z 를 가는 동안 회전 수 = Lm / (비율·P), 체이서는 회전마다 P(자기 리드) · 기계는 비율·P 를
     * 나아가므로 머리 앞쪽이 끌려 나간 길이 = 회전 수 × (1 − 비율)·P = (1 − 비율)/비율 · Lm (= (1 − 비율) × 체이서가 간 길이).
     */
    private fun hand(d: Double, p: Double, vc: Double?, rpm: Double?, z0: Double, z1: Double, ratio: Double, dwell: Double, face: Double?, throat: Double?): DieHand {
        val s = floor((rpm ?: (1000 * vc!! / (PI * d))) + 0.5).toLong()
        val f = ratio * p
        return DieHand(
            s, PI * d * s / 1000, f, floor(f * 1000 + 0.5) / 1000, 60 * (z0 - z1) / (f * s) + dwell, Double.NaN,
            throat?.let { z1 + it * p },
        ).copy(pull = (1 - ratio) / ratio * ((face ?: z0) - z1))
    }

    /** 회사 표기(이 시험이 따로 짠 것): 0.001 반올림, 끝 0 지움, 소수점 꼭, 앞 0 뺌 */
    private fun w(v: Double): String {
        val m = floor(abs(v) * 1000 + 0.5).toLong()
        val frac = (m % 1000).toString().padStart(3, '0').trimEnd('0')
        val int = (m / 1000).toString().let { if (it == "0" && frac.isNotEmpty()) "" else it }
        return (if (v < 0 && m != 0L) "-" else "") + int + "." + frac
    }

    private class DieCase(
        val tag: String, val spec: ThreadSpec, val vc: Double?, val rpm: Double?, val z0: Double, val z1: Double,
        val ratio: Double = 0.96, val dwell: Double = 1.0, val face: Double? = null, val throat: Double? = null, val float: Double? = null,
    )

    private val dieCases = listOf(
        DieCase("1/2-20 UNF Vc15", un("1/2", 20), 15.0, null, 5.0, -18.0, throat = 2.0),
        DieCase("3/4-20 UNEF Vc10 끝면 0", un("3/4", 20), 10.0, null, 6.0, -25.0, face = 0.0, throat = 1.5),
        DieCase("3/8-20 UN S900 끝면 −1", un("3/8", 20), null, 900.0, 3.0, -15.5, face = -1.0),
        DieCase("1-20 UNEF Vc 12.5 비율 0.97", un("1", 20), 12.5, null, 4.0, -30.0, ratio = 0.97, dwell = 0.5),
        DieCase("1/4-20 UNC Vc 8", un("1/4", 20), 8.0, null, 5.0, -12.0),
        DieCase("M12×1.75 S230 (예제 ⑮)", ThreadTable.METRIC.single { it.size == "M12" && abs(it.pitch - 1.75) < 1e-9 }, null, 230.0, 6.0, -20.0, face = 0.0),
        DieCase("M10×1.25 Vc 9 비율 1", ThreadTable.METRIC.single { it.size == "M10" && abs(it.pitch - 1.25) < 1e-9 }, 9.0, null, 4.0, -16.0, ratio = 1.0),
    )

    @Test
    fun dieHeadHandCalc() {
        for (c in dieCases) {
            val r = DieHeadCalc.calc(
                DieInput(
                    spec = c.spec, vc = c.vc, rpm = c.rpm, zStart = c.z0, zEnd = c.z1, ratio = c.ratio, dwell = c.dwell,
                    floatTravel = c.float, throatThreads = c.throat, zFace = c.face,
                ),
            )
            val s = assertNotNull(r.solution, "${c.tag}: ${r.warnings}")
            val h = hand(c.spec.d, c.spec.pitch, c.vc, c.rpm, c.z0, c.z1, c.ratio, c.dwell, c.face, c.throat)
            assertEquals(h.s, s.rpm, "${c.tag} S")
            assertTrue(abs(h.vcUsed - s.vcUsed) < 1e-9, "${c.tag} 쓴 Vc ${s.vcUsed} · 손 ${h.vcUsed}")
            assertTrue(abs(h.f - s.feed) < 1e-12, "${c.tag} F ${s.feed} · 손 ${h.f}")
            assertTrue(abs(h.f * h.s - s.feedPerMin) < 1e-9, "${c.tag} 분당 이송")
            assertTrue(abs(h.time - s.timeSec) < 1e-9, "${c.tag} 시간 ${s.timeSec} · 손 ${h.time}")
            if (h.full == null) assertNull(s.fullThreadEndZ, c.tag) else assertTrue(abs(h.full - s.fullThreadEndZ!!) < 1e-12, "${c.tag} 완전 나사 끝")
            val prog = buildList {
                add("G97S${h.s}")
                add("G0X0.Z20.")
                add("Z${w(c.z0)}")
                add("G1Z${w(c.z1)}F${w(h.fNc)}")
                if (floor(c.dwell * 1000 + 0.5) > 0) add("G4U${w(c.dwell)}")
                add("G0Z150.")
            }
            assertEquals(prog, s.program, c.tag)
            assertEquals(if (c.ratio == 1.0) null else DieHeadCalc.CATALOG_FEED_NOTE, s.note, "${c.tag} 안내")
            assertEquals(emptyList(), r.warnings, c.tag)
        }
    }

    @Test
    fun dieHeadWarningsByHand() {
        val s = un("1/2", 20)
        fun calc(i: DieInput) = DieHeadCalc.calc(i)
        val base = DieInput(spec = s, vc = 15.0, zStart = 5.0, zEnd = -18.0)
        // 당김(손 0.958 · 어느 식으로도 0.9 위 · 1.0 아래) — 플로트 1.0 이면 없음, 0.9 이면 FLOAT_EXCEEDED (경계 값 차는 dieFloatPullKinematics)
        assertEquals(emptyList(), calc(base.copy(floatTravel = 1.0)).warnings)
        assertEquals(listOf(DieWarning.FLOAT_EXCEEDED), calc(base.copy(floatTravel = 0.9)).warnings)
        // 끝면 0 이면 당김 0.75(손) → 플로트 0.76 은 넘지 않음
        assertEquals(emptyList(), calc(base.copy(floatTravel = 0.76, zFace = 0.0)).warnings)
        // 비율 1.02 → RATIO_OVER_ONE, 당김 음수(밀림)
        val over = calc(base.copy(ratio = 1.02))
        assertEquals(listOf(DieWarning.RATIO_OVER_ONE), over.warnings)
        assertTrue(over.solution!!.floatPull < 0)
        // 완전 나사 끝 = −18 + 18 × 1.27 = 4.86 ≥ 시작 5? 아님 → 목 19산: −18 + 24.13 = 6.13 ≥ 5 → SHORT_THREAD
        assertEquals(emptyList(), calc(base.copy(throatThreads = 18.0)).warnings)
        assertEquals(listOf(DieWarning.SHORT_THREAD), calc(base.copy(throatThreads = 19.0)).warnings)
        // 멈춤 0 → G4 줄 없음
        assertTrue(calc(base.copy(dwell = 0.0)).solution!!.program.none { it.startsWith("G4") })
        // Z 순서
        assertEquals(listOf(DieWarning.Z_ORDER), calc(base.copy(zStart = -18.0)).warnings)
        assertNull(calc(base.copy(zStart = -18.0)).solution)
    }

    /**
     * 끝 당김 길이 = (1 − 비율)/비율 · (물림 Z − 끝 Z) — 회전 수가 기계 이송(비율·P) 으로 정해지므로. 앱이 (1 − 비율)·(물림 Z − 끝 Z) 를 쓰면
     * 1/비율 배(0.96 이면 4.2 %) 작게 나온다. FLOAT_EXCEEDED 도 같은 값으로 가린다(1/2-20 UNF Z5 → −18: 손 0.9583, 플로트 0.94 → 넘음).
     */
    @Test
    fun dieFloatPullKinematics() {
        val bad = mutableListOf<String>()
        for (c in dieCases) {
            val s = DieHeadCalc.calc(
                DieInput(spec = c.spec, vc = c.vc, rpm = c.rpm, zStart = c.z0, zEnd = c.z1, ratio = c.ratio, dwell = c.dwell, zFace = c.face),
            ).solution!!
            val h = hand(c.spec.d, c.spec.pitch, c.vc, c.rpm, c.z0, c.z1, c.ratio, c.dwell, c.face, null)
            if (abs(h.pull - s.floatPull) > 1e-9) bad += "${c.tag}: 당김 앱 ${s.floatPull} · 손 ${h.pull}"
        }
        val w = DieHeadCalc.calc(DieInput(spec = un("1/2", 20), vc = 15.0, zStart = 5.0, zEnd = -18.0, floatTravel = 0.94)).warnings
        if (w != listOf(DieWarning.FLOAT_EXCEEDED)) bad += "1/2-20 UNF Z5 → −18 플로트 0.94: 손 당김 0.9583 > 0.94 인데 경고 $w"
        assertTrue(bad.isEmpty(), bad.joinToString(" / "))
    }

    /**
     * 다이스 글을 GSim 에 넣어 G1 · 멈춤 시간이 손 계산과 맞는지(NC F 는 0.001 반올림 — 계산기 시간은 반올림 전 F 라 그 몫만큼 다르다).
     */
    @Test
    fun dieProgramTimeInSimulator() {
        for (c in dieCases) {
            val sol = assertNotNull(
                DieHeadCalc.calc(
                    DieInput(spec = c.spec, vc = c.vc, rpm = c.rpm, zStart = c.z0, zEnd = c.z1, ratio = c.ratio, dwell = c.dwell, zFace = c.face),
                ).solution,
            )
            val h = hand(c.spec.d, c.spec.pitch, c.vc, c.rpm, c.z0, c.z1, c.ratio, c.dwell, c.face, null)
            val text = (listOf(sol.program.first(), "T0505M03") + sol.program.drop(1) + "M30").joinToString("\n")
            val tools = mapOf(5 to ToolOffset(kind = ToolKind.DIE, dieDia = c.spec.d, diePitch = c.spec.pitch, threadDeg = 60.0))
            val r = GSim.run(SimInput(text, MachineSetup(), tools))
            assertNull(r.stop, "${c.tag}: 멈춤 ${r.stop}")
            val cut = r.steps.filterIsInstance<MoveStep>().single { it.src == StepSrc.DIE_CUT }
            val g1Sim = cut.t1 - cut.t0
            val g1Hand = 60 * (c.z0 - c.z1) / (h.fNc * h.s)
            assertTrue(abs(g1Sim - g1Hand) < 1e-9, "${c.tag}: G1 시간 GSim $g1Sim · 손(NC F ${h.fNc}) $g1Hand")
            assertTrue(abs(c.z0 - cut.tip.start.z) < 1e-9 && abs(c.z1 - cut.tip.end.z) < 1e-9 && abs(cut.tip.start.x) < 1e-9, "${c.tag}: G1 경로")
            val dwell = r.steps.filterIsInstance<DwellStep>().sumOf { it.t1 - it.t0 }
            assertTrue(abs(dwell - c.dwell) < 1e-9, "${c.tag}: 멈춤 $dwell")
            // 계산기 시간 − GSim(G1 + 멈춤) = 반올림 F 차 몫
            val roundPart = 60 * (c.z0 - c.z1) / h.s * (1 / h.f - 1 / h.fNc)
            assertTrue(abs(sol.timeSec - (g1Sim + dwell) - roundPart) < 1e-9, "${c.tag}: 계산 ${sol.timeSec} · GSim ${g1Sim + dwell} · 반올림 몫 $roundPart")
            assertTrue(abs(roundPart) <= 60 * (c.z0 - c.z1) / h.s * 0.0005 / (h.f * h.fNc) + 1e-12)
        }
    }

    /**
     * 예제 ⑮(O9023): 다이스 구간 손 계산 — G97S230 · G0X0.Z20. · Z6. · G1Z-20.F1.68 · G4U1. · G0Z150.
     * 앞 공구 끝 G0Z200.(X20 → 반경 10)에서: 급송 꺾임 max(10/20000, 180/24000) 분 = 0.45 s, Z20 → 6 = 14/24000 분 = 0.035 s,
     * G1 26 mm / (1.68 × 230) 분 = 4.0372671 s, 멈춤 1 s, Z−20 → 150 = 170/24000 분 = 0.425 s. 계산기(같은 입력) 시간 = 5.0372671 s.
     */
    @Test
    fun example15DieTime() {
        val ex = SimExamples.all.single { it.text.startsWith("O9023") }
        val r = GSim.run(SimInput(ex.text, MachineSetup(), ex.tools))
        assertNull(r.stop, "멈춤 ${r.stop}")
        val steps = r.steps
        val iCut = steps.indexOfFirst { it is MoveStep && it.src == StepSrc.DIE_CUT }
        assertTrue(iCut > 0)
        val cut = steps[iCut] as MoveStep
        val g1 = 60 * 26.0 / (1.68 * 230)
        assertTrue(abs(cut.t1 - cut.t0 - g1) < 1e-9, "G1 ${cut.t1 - cut.t0} · 손 $g1")
        // 줄 번호(1부터)로 다이스 블록을 나눈다 — 꺾인 급송은 두 토막(X 가 먼저 끝나는 점 Z188 에서 꺾임)
        val lines = ex.text.lines()
        val l0 = lines.indexOf("T0505M03") + 2
        assertEquals("G0X0.Z20.", lines[l0 - 1])
        fun sec(line: Int) = steps.filter { it.line == line }.sumOf { it.t1 - it.t0 }
        val expect = listOf("G0X0.Z20." to 0.45, "Z6." to 0.035, "G1Z-20.F1.68" to g1, "G4U1." to 1.0, "G0Z150." to 0.425)
        for ((j, e) in expect.withIndex()) {
            assertEquals(e.first, lines[l0 - 1 + j])
            assertTrue(abs(sec(l0 + j) - e.second) < 1e-9, "${e.first}: GSim ${sec(l0 + j)} · 손 ${e.second}")
        }
        val app = steps.filterIsInstance<MoveStep>().filter { it.line == l0 }
        assertTrue(app.all { it.kind == MoveKind.RAPID } && abs(app.first().tip.start.z - 200) < 1e-9 && abs(app.first().tip.start.x - 10) < 1e-9, "진입 ${app.map { it.tip }}")
        assertEquals(l0 + 2, cut.line)
        val dw = steps.filterIsInstance<DwellStep>().filter { it.line == cut.line + 1 }
        assertEquals(1, dw.size, "G4U1. 한 번")
        assertTrue(abs(dw.single().sec - 1.0) < 1e-12)
        // 다이스 구간 합 = 0.45 + 0.035 + G1 + 1 + 0.425
        val block = (l0..l0 + 4).sumOf { sec(it) }
        assertTrue(abs(block - (0.45 + 0.035 + g1 + 1 + 0.425)) < 1e-9, "다이스 구간 $block")
        // 계산기 같은 입력의 시간 = G1 + 멈춤 (F1.68 은 0.001 에 딱 맞아 반올림 몫 없음)
        val spec = ThreadTable.METRIC.single { it.size == "M12" && abs(it.pitch - 1.75) < 1e-9 }
        assertEquals(ThreadKind.METRIC, spec.kind)
        val sol = assertNotNull(DieHeadCalc.calc(DieInput(spec = spec, rpm = 230.0, zStart = 6.0, zEnd = -20.0, zFace = 0.0)).solution)
        assertTrue(abs(sol.timeSec - (g1 + 1)) < 1e-9, "계산기 ${sol.timeSec}")
        // 예제 글의 다이스 블록 = 계산기 글
        val body = ex.text.lines()
        val i0 = body.indexOf("G97S230")
        assertEquals(listOf("G97S230", "T0505M03") + sol.program.drop(1), body.subList(i0, i0 + 7))
        // 총 시간 = 단계 시간 합(빈틈 없이 이어짐)
        assertTrue(abs(r.totalSec - steps.last().t1) < 1e-9 || r.totalSec >= steps.last().t1, "총 ${r.totalSec} · 끝 단계 ${steps.last().t1}")
    }
}
