package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class G76CalcTest {
    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
    private fun m(d: Double, p: Double) = ThreadTable.METRIC.single { it.d == d && it.pitch == p }

    /** 원본 08.jpg 의 설정: 외경 · UN 1/4-20 · 2A · 정가공 · Z5 → Z−25 (첫 절입은 화면 밖이라 0.2 로) */
    private val original = G76Input(spec = un("1/4", 20), zStart = 5.0, zEnd = -25.0, firstCut = 0.2)

    private fun ok(r: G76Result, warnings: List<G76Warning> = emptyList()): G76Solution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        assertEquals(warnings, r.warnings)
        return r.solution
    }

    private fun stops(w: G76Warning, i: G76Input) {
        val r = G76Calc.calc(i)
        assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다: $w")
        assertEquals(listOf(w), r.warnings)
    }

    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    /**
     * FANUC Series 0 Lathe 조작 설명서 B-61394E/08 13.2.7 예(리드 6, 외경 68 → 골 60.64, k 3.68, 첫 절입 1.8,
     * m 1 · 면취 1.0L · 60°, Δdmin 0.1, d 0.2): `G76 P011060 Q100 R200 ;` `G76 X60.64 Z25.0 P3680 Q1800 F6.0 ;`.
     * 같은 값을 넣으면 같은 낱말이 나온다(R 은 소수점을 쓸 수 있어 R0.2 = R200 μm, Z25. = Z25.0).
     */
    @Test
    fun fanucManualExample() {
        assertEquals(
            listOf("G76 P011060 Q100 R0.2", "G76 X60.64 Z25. P3680 Q1800 F6."),
            G76Calc.blocks(1, 10, 60, 100, 0.2, 60.64, 25.0, 3680, 1800, 6.0).legacy,
        )
        // 같은 나사를 계산기로: M68×6(ISO 261) 숫나사 6h, 목표 0%(최대 실체 = 기본 바깥지름 68), 기본 인선 R
        val m68 = ThreadSpec(ThreadSeries.M_COARSE, "M68", 68.0, 6.0)
        val s = ok(G76Calc.calc(G76Input(spec = m68, cls = ThreadClass.M6h, zStart = 130.0, zEnd = 25.0, firstCut = 1.8,
            target = 0.0, finishCount = 1, minCut = 0.1, finishAllow = 0.2)))
        // 기본 깊이 0.61343P = 3.6806 → 매뉴얼은 3.68 로 반올림해 X60.64. 계산기는 μm 까지: X60.639 · P3681
        assertEquals(listOf("G76P011060Q100R.2", "G76X60.639Z25.P3681Q1800F6."), s.nc)
        near(60.64, s.x, 0.002, "X")
        near(3.68, s.height, 0.0015, "k")
    }

    /** 원본 08.jpg 의 첫 줄 `G76 P02 1060 Q35 R0.02` = 빈칸 기본값(m 2 · 면취 1.0P · 60° · Δdmin 0.035 · d 0.02) — 글은 회사 표기 */
    @Test
    fun originalFirstLineFromDefaults() {
        val s = ok(G76Calc.calc(original))
        assertEquals("G76P021060Q35R.02", s.nc[0])
        assertEquals(80.0, s.target)
        assertEquals(listOf(2, 10, 60), listOf(s.finishCount, s.chamfer, s.angle))
        // 면취 0.5P · 각도 55 · m 1 · Δdmin 0.05 · d 0.1
        val t = ok(G76Calc.calc(original.copy(chamfer = 5, angle = 55, finishCount = 1, minCut = 0.05, finishAllow = 0.1)))
        assertEquals("G76P010555Q50R.1", t.nc[0])
    }

    /**
     * 원본 화면 손 계산 — 1/4-20 UNC 2A(H28 식, 인치): 기본 공차 B = 0.0015∛0.25 + 0.0015√0.25 + 0.015∛0.05² = 0.0037308,
     * 허용차 0.3B = 0.0011192, 바깥지름 공차 0.060∛0.05² = 0.0081433. 목표 80%:
     * 피치경 = (0.25 − 0.649519×0.05 − 0.0011192) − 0.8×0.0037308 = 0.2134202 in = 5.420874 mm,
     * 바깥지름 = (0.25 − 0.0011192) − 0.8×0.0081433 = 0.2423662 in = 6.156101 mm,
     * 인선 R = H/6 → X = 피치경 − H + 2R = 피치경 − 0.577350P = 4.687639 → X4.688, k = (6.156101 − 4.688)/2 = 0.73405 → P734.
     * 시작 X = 바깥지름 최대 6.321572 + 2 = 8.322.
     */
    @Test
    fun originalScreenHandNumbers() {
        val s = ok(G76Calc.calc(original.copy(rpm = 1821.0)))
        near(5.420874, s.pitchDia, 1e-6, "피치경")
        near(6.156101, s.crestDia, 1e-6, "바깥지름")
        near(4.687639, s.x, 1e-6, "X")
        near(0.734, s.height, 1e-12, "k")
        assertEquals(1.27, s.lead)
        assertEquals(
            listOf("G97S1821", "G0X8.322Z5.", "G76P021060Q35R.02", "G76X4.688Z-25.P734Q200F1.27"),
            s.program,
        )
        assertEquals(s.program.takeLast(2), s.nc)
        near(1821 * 1.27, s.feedPerMin!!, 1e-9, "분당 이송")
        // 회전수를 비우면 G97 줄이 없다
        assertEquals("G0X8.322Z5.", ok(G76Calc.calc(original)).program.first())
    }

    /**
     * G92 다회 글(회사 흐름) 손 계산 — 원본 화면 값: X 줄 = 분석표 X(X4.688 + 2(k − 깊이), 1회 깊이 0.2 → 4.688 + 2 × 0.534 = 5.756) 15줄,
     * 깊이는 Δd·√n(0.2 · 0.283 · 0.346 …), 그 회 절입이 Δdmin 0.035 보다 작으면 앞 깊이 + 0.035, k − d = 0.714 에서 자름(13회 X4.728), 마지막 둘은 정삭(G76 X 그대로).
     * 시작점은 G76 과 같은 X8.322 를 Z20. 에서 맞추고 Z5. 로, 끝나면 Z200. 까지 Z 만. M23(끝 빼기 켬)은 G92 바로 앞, M24(끔)는 마지막 X 바로 뒤.
     * 다듬질 블록은 같은 꼴에 마지막 X 두 번
     */
    @Test
    fun g92HandNumbers() {
        val s = ok(G76Calc.calc(original.copy(rpm = 1821.0)))
        assertEquals(
            listOf(
                "G97S1821", "G0X8.322Z20.", "Z5.", "M23", "G92X5.756Z-25.F1.27",
                "X5.59", "X5.463", "X5.356", "X5.262", "X5.176", "X5.098", "X5.025", "X4.955", "X4.885", "X4.815", "X4.745", "X4.728",
                "X4.688", "X4.688", "M24", "G0Z200.",
            ),
            s.g92,
        )
        assertEquals(listOf("G97S1821", "G0X8.322Z20.", "Z5.", "M23", "G92X4.688Z-25.F1.27", "X4.688", "M24", "G0Z200."), s.g92Finish)
        // X 줄 수 = 분석표 회차 수, 회전수를 비우면 G97 줄이 없다
        val x = ok(G76Calc.calc(original)).g92
        assertEquals("G0X8.322Z20.", x.first())
        assertEquals(s.passes.size, x.count { it.startsWith("X") || it.startsWith("G92") })
        // 암나사: 시작 X 는 구멍 안(안지름 최소 − 2), 마지막 X = G76 X10.09 — 같은 꼴(M23 · M24 도 같은 자리)
        val i = ok(G76Calc.calc(G76Input(inner = true, spec = m(10.0, 1.5), zStart = 3.0, zEnd = -20.0, firstCut = 0.3, target = 50.0)))
        assertEquals(listOf("G0X6.376Z20.", "Z3.", "M23"), i.g92.take(3))
        assertTrue(i.g92[3].startsWith("G92X") && i.g92[3].endsWith("Z-20.F1.5"), i.g92[3])
        assertEquals(listOf("X10.09", "X10.09", "M24", "G0Z200."), i.g92.takeLast(4))
        assertEquals(listOf("G0X6.376Z20.", "Z3.", "M23", "G92X10.09Z-20.F1.5", "X10.09", "M24", "G0Z200."), i.g92Finish)
        // 역가공: 시작 Z 는 척 쪽, 끝 Z 쪽으로(Z+) 깎는다 — 진입 Z 는 두 Z 의 큰 쪽(3)이 15 아래라 그대로 Z20.
        val r = ok(G76Calc.calc(original.copy(dir = ThreadDir.REVERSE, zStart = -28.0, zEnd = 3.0)))
        assertEquals(listOf("G0X8.322Z20.", "Z-28.", "M23", "G92X5.756Z3.F1.27"), r.g92.take(4))
        // 시작 Z 가 Z20 가까이(15 이상)면 진입 Z 를 올림 + 20 으로(G41/G42 글과 같은 규칙), Z200. 보다 멀면 물러남도 거기까지
        val near20 = ok(G76Calc.calc(original.copy(zStart = 15.5))).g92
        assertEquals(listOf("G0X8.322Z36.", "Z15.5"), near20.take(2))
        assertEquals("G0Z200.", near20.last())
        val far = ok(G76Calc.calc(original.copy(zStart = 300.0, zEnd = 250.0))).g92
        assertEquals(listOf("G0X8.322Z320.", "Z300."), far.take(2))
        assertEquals("G0Z320.", far.last())
        // 리드는 G76 F 와 같은 넷째 자리 · 앞 0 뺌
        assertTrue(ok(G76Calc.calc(G76Input(spec = un("1/2", 13), zStart = 5.0, zEnd = -30.0, firstCut = 0.3))).g92[3].endsWith("F1.9538"))
        assertTrue(ok(G76Calc.calc(G76Input(spec = un("#4", 40), zStart = 2.0, zEnd = -6.0, firstCut = 0.05))).g92[3].endsWith("F.635"))
    }

    /**
     * 미터 손 계산(ISO 965-1 표 값 — M10×1.5 는 ISO 965-2 표 1 · 2 의 줄): 6g 목표 50%: es −32 μm, Td2 132, Td 236 →
     * 피치경 9.025721 − 0.032 − 0.066 = 8.927721, 바깥지름 10 − 0.032 − 0.118 = 9.850, X = 8.927721 − 0.866025 = 8.061696 → X8.062,
     * k = (9.850 − 8.062)/2 = 0.894 → P894.
     * 6H 목표 50%: TD2 180 · TD1 300 → 피치경 9.025721 + 0.090 = 9.115721, 안지름 8.376202 + 0.150 = 8.526202,
     * 인선 R = H/8 → X = 피치경 + 0.649519P = 10.090000 → X10.09, k = (10.090 − 8.526202)/2 = 0.78190 → P782, 시작 X = 8.376 − 2.
     */
    @Test
    fun metricHandNumbers() {
        val e = ok(G76Calc.calc(G76Input(spec = m(10.0, 1.5), zStart = 3.0, zEnd = -20.0, firstCut = 0.3, target = 50.0)))
        near(8.927721, e.pitchDia, 1e-6, "6g 피치경")
        near(9.850, e.crestDia, 1e-9, "6g 바깥지름")
        near(8.061696, e.x, 1e-6, "6g X")
        assertEquals("G76X8.062Z-20.P894Q300F1.5", e.nc[1])
        val i = ok(G76Calc.calc(G76Input(inner = true, spec = m(10.0, 1.5), zStart = 3.0, zEnd = -20.0, firstCut = 0.3, target = 50.0)))
        near(9.115721, i.pitchDia, 1e-6, "6H 피치경")
        near(8.526202, i.crestDia, 1e-6, "6H 안지름")
        near(10.090000, i.x, 1e-6, "6H X")
        assertEquals("G76X10.09Z-20.P782Q300F1.5", i.nc[1])
        assertEquals("G0X6.376Z3.", i.program.first())
    }

    /**
     * 목표 0%(최대 실체)에서 기본 인선 R 의 깊이 = 규격 기본 깊이: 숫나사 k = 0.613435P(FANUC 예 3.68/6, Haas 표 외경 K = 0.6134P),
     * 암나사 k = 0.541266P(Haas 표 내경 K = 0.5413P). 급수 · 사이즈와 상관없다(es · EI 는 두 지름을 같이 옮긴다).
     * Haas 워크북 O00113 3/4-16 외경 K0.0383 · 표의 7 산 내경 K .0773 과 맞다.
     */
    @Test
    fun basicDepthAtMaximumMaterial() {
        for (s in ThreadTable.METRIC.filter { it.d >= 3 } + ThreadTable.UN.filter { it.d >= 6 }) {
            for (c in ThreadClass.entries.filter { it.kind == s.kind }) {
                val r = G76Calc.calc(G76Input(inner = c.internal, spec = s, cls = c, zStart = 5.0, zEnd = -20.0, firstCut = 0.05, target = 0.0, xClear = 0.1))
                val sol = ok(r, r.warnings.filter { it == G76Warning.ONE_ROUGH_PASS })
                val f = if (c.internal) 5 * sqrt(3.0) / 16 else 17 * sqrt(3.0) / 48 // 0.541266 · 0.613435
                near(f * s.pitch, sol.height, 0.0011, "${s.label} ${c.label} k")
            }
        }
        val haasOd = ok(G76Calc.calc(G76Input(spec = un("3/4", 16), zStart = 5.0, zEnd = -30.0, firstCut = 0.3, target = 0.0)))
        near(0.0383 * 25.4, haasOd.height, 0.0001 * 25.4, "3/4-16 K")
        val haasId = ok(G76Calc.calc(G76Input(inner = true, spec = un("1-1/8", 7), zStart = 5.0, zEnd = -30.0, firstCut = 0.3, target = 0.0)))
        near(0.0773 * 25.4, haasId.height, 0.0001 * 25.4, "1-1/8-7 내경 K")
    }

    /** 목표 %: 0 = 최대 실체(숫나사 큰 쪽 · 암나사 작은 쪽), 100 = 최소 실체, 빈칸 = 80 */
    @Test
    fun targetPosition() {
        for (inner in listOf(false, true)) {
            val base = G76Input(inner = inner, spec = m(20.0, 2.5), zStart = 5.0, zEnd = -30.0, firstCut = 0.3)
            val l = ok(G76Calc.calc(base)).limits
            for (t in listOf(0.0, 25.0, 80.0, 100.0)) {
                val s = ok(G76Calc.calc(base.copy(target = t)))
                val pd = if (inner) l.pitchDia.min + t / 100 * l.pitchDia.width else l.pitchDia.max - t / 100 * l.pitchDia.width
                val crest = if (inner) l.crest.min + t / 100 * l.crest.width else l.crest.max - t / 100 * l.crest.width
                near(pd, s.pitchDia, 1e-12, "피치경 $inner $t")
                near(crest, s.crestDia, 1e-12, "산봉우리 $inner $t")
            }
            assertEquals(ok(G76Calc.calc(base.copy(target = 80.0))), ok(G76Calc.calc(base)))
        }
    }

    /**
     * 인선 R: 숫나사는 X 가 2ΔR 커지고 암나사는 작아진다(R ≤ H/3 — 옆날이 피치선에 걸림). 0 이면 날카로운 V(X = 피치경 ∓ H).
     * R > H/3 이면 피치선에 끝 원호가 걸려 날 끝은 피치선 아래 R − √(R² − P²/16) — R = H/3 에서 두 식이 같다(H/6).
     */
    @Test
    fun tipRadiusMovesX() {
        for (inner in listOf(false, true)) {
            val base = G76Input(inner = inner, spec = m(16.0, 2.0), zStart = 5.0, zEnd = -30.0, firstCut = 0.3, target = 50.0)
            val sharp = ok(G76Calc.calc(base.copy(tipR = 0.0)))
            val h = sharp.limits.h
            near(if (inner) sharp.pitchDia + h else sharp.pitchDia - h, sharp.x, 1e-12, "날카로운 V $inner")
            val r = ok(G76Calc.calc(base.copy(tipR = 0.2)))
            near(sharp.x + (if (inner) -0.4 else 0.4), r.x, 1e-12, "R0.2 $inner")
            val sign = if (inner) 1 else -1
            // 이만큼 큰 R 은 골이 규격 한계를 넘는다(경고와 함께 숫자)
            val atThird = ok(G76Calc.calc(base.copy(tipR = h / 3)), listOf(G76Warning.ROOT_INTERFERES))
            near(sharp.pitchDia + sign * h / 3, atThird.x, 1e-9, "R H/3 $inner")
            val big = 0.45 * h
            val arc = ok(G76Calc.calc(base.copy(tipR = big)), listOf(G76Warning.ROOT_INTERFERES))
            near(sharp.pitchDia + sign * 2 * (big - sqrt(big * big - 0.25)), arc.x, 1e-9, "R 0.45H(원호가 피치선에 걸림) $inner")
        }
        assertEquals(sqrt(3.0) / 6, G76Calc.tipDepth(2.0, sqrt(3.0) / 3), 1e-12)
    }

    /**
     * TIP_TOO_BIG — 산봉우리 높이(날 끝에서 k)의 날 단면 폭이 P 를 넘으면 이웃 홈의 날이 산봉우리를 깎는다.
     * M16×2 6g 목표 80%(피치경 14.534962 · 바깥지름 15.738): R 0.6H(1.0392) → 깊이 R − √(R² − 0.25) = 0.1282, X 14.279, k 0.7295,
     * 그 높이 날 폭 2·(R·cos30° + (k − R/2)·tan30°) = 2.042 > 2 → 멈춤. R 0.5H 는 X 14.217 · k 0.7605 · 폭 1.878 → 숫자(골 한계 경고).
     */
    @Test
    fun tipTooBigCutsNeighbourCrest() {
        val base = G76Input(spec = m(16.0, 2.0), zStart = 5.0, zEnd = -30.0, firstCut = 0.3)
        val h = sqrt(3.0)
        stops(G76Warning.TIP_TOO_BIG, base.copy(tipR = 0.6 * h))
        val s = ok(G76Calc.calc(base.copy(tipR = 0.5 * h)), listOf(G76Warning.ROOT_INTERFERES))
        assertTrue(2 * G76Calc.halfWidth(s.tipR, s.height) < 2.0)
    }

    /**
     * 골 한계(ROOT_INTERFERES): 숫나사 골 > d1 + es 이면(목표 0% 에서 R > H/4), 암나사 바깥 < D + EI 이면(목표 0% 에서 R > H/8).
     * 기본 인선 R(H/6 · H/8)은 어느 목표에서도 경고하지 않는다.
     */
    @Test
    fun rootInterference() {
        val p = 2.0
        val h = sqrt(3.0) / 2 * p
        val ext = G76Input(spec = m(16.0, p), zStart = 5.0, zEnd = -30.0, firstCut = 0.05, target = 0.0)
        ok(G76Calc.calc(ext.copy(tipR = h / 4 - 0.001)))
        ok(G76Calc.calc(ext.copy(tipR = h / 4 + 0.001)), listOf(G76Warning.ROOT_INTERFERES))
        val int = ext.copy(inner = true)
        ok(G76Calc.calc(int.copy(tipR = h / 8 - 0.001)))
        ok(G76Calc.calc(int.copy(tipR = h / 8 + 0.001)), listOf(G76Warning.ROOT_INTERFERES))
        for (t in listOf(0.0, 50.0, 100.0)) {
            ok(G76Calc.calc(ext.copy(target = t)))
            ok(G76Calc.calc(int.copy(target = t)))
        }
    }

    /**
     * 절입표 — n 회 깊이 Δd·√n, 그 회 절입 < Δdmin 이면 앞 + Δdmin, k − d 에서 끝, 정삭 m 회(첫 회 d, 나머지 0).
     * k 0.92 · d 0.02(거친 끝 0.9) · Δd 0.3:
     *   Δdmin 0.035 → 0.3, 0.42426, 0.51962, 0.6, 0.67082, 0.73485, 0.79373, 0.84853, 0.9(= 0.3·√9) · 정삭 0.92, 0.92
     *   Δdmin 0.08  → 0.3, 0.42426, 0.51962, 0.6, 0.68, 0.76, 0.84, 0.9(0.92 를 k − d 로 자름) · 정삭
     */
    @Test
    fun passSchedule() {
        val a = G76Calc.schedule(0.92, 0.02, 0.3, 0.035, 2)!!
        val expA = listOf(0.3, 0.3 * sqrt(2.0), 0.3 * sqrt(3.0), 0.6, 0.3 * sqrt(5.0), 0.3 * sqrt(6.0), 0.3 * sqrt(7.0), 0.3 * sqrt(8.0), 0.9, 0.92, 0.92)
        assertEquals(expA.size, a.size)
        expA.zip(a).forEachIndexed { n, (e, p) -> near(e, p.first, 1e-12, "깊이 ${n + 1}") }
        a.forEachIndexed { n, p -> near(p.first - (if (n == 0) 0.0 else a[n - 1].first), p.second, 1e-12, "절입 ${n + 1}") }
        near(0.02, a[9].second, 1e-12, "정삭 첫 회 = d")
        near(0.0, a[10].second, 0.0, "정삭 둘째 = 0")

        val b = G76Calc.schedule(0.92, 0.02, 0.3, 0.08, 1)!!
        val expB = listOf(0.3, 0.3 * sqrt(2.0), 0.3 * sqrt(3.0), 0.6, 0.68, 0.76, 0.84, 0.9, 0.92)
        assertEquals(expB.size, b.size)
        expB.zip(b).forEachIndexed { n, (e, p) -> near(e, p.first, 1e-12, "Δdmin 깊이 ${n + 1}") }
        // 모든 거친 절입(마지막 자른 회 빼고)은 Δdmin 이상
        b.dropLast(2).forEach { assertTrue(it.second >= 0.08 - 1e-12) }

        // d = 0: 거친 절입이 k 까지, 정삭은 모두 0 절입
        val c = G76Calc.schedule(0.5, 0.0, 0.2, 0.05, 3)!!
        near(0.5, c[c.size - 4].first, 1e-12, "d 0 거친 끝")
        assertTrue(c.takeLast(3).all { it.first == 0.5 && it.second == 0.0 })
        // 첫 절입이 k − d 이상이면 한 번에
        assertEquals(listOf(0.9 to 0.9, 0.92 to 0.02), G76Calc.schedule(0.92, 0.02, 1.0, 0.0, 1))
        assertNull(G76Calc.schedule(3.0, 0.0, 0.001, 0.0, 1), "너무 많은 회")
    }

    /** 분석표의 X: 숫나사 X + 2(k − 깊이), 암나사 X − 2(k − 깊이). 마지막(정삭)은 지령 X, 정삭 표시는 끝 m 줄 */
    @Test
    fun passTableX() {
        for (inner in listOf(false, true)) {
            val s = ok(G76Calc.calc(G76Input(inner = inner, spec = m(24.0, 3.0), zStart = 5.0, zEnd = -40.0, firstCut = 0.4, finishCount = 3)))
            val xNc = ncWords(s.nc[1]).single { it.startsWith("X") }.drop(1).toDouble()
            for (p in s.passes) near(if (inner) xNc - 2 * (s.height - p.depth) else xNc + 2 * (s.height - p.depth), p.xDia, 1e-9, "${p.n}")
            assertEquals(xNc, s.passes.last().xDia)
            assertEquals(listOf(false, true, true, true), s.passes.takeLast(4).map { it.finish })
            assertEquals((1..s.passes.size).toList(), s.passes.map { it.n })
            near(s.height, s.passes.sumOf { it.cut }, 1e-9, "절입 합 = k")
            near(0.4, s.passes.first().cut, 1e-12, "첫 절입")
            // 숫나사는 X 가 줄어들며, 암나사는 늘어나며 들어간다
            s.passes.zipWithNext().forEach { (a, b) -> assertTrue(if (inner) b.xDia >= a.xDia else b.xDia <= a.xDia) }
        }
    }

    /** 방향: 정가공은 시작 Z > 목표, 역가공은 시작 Z < 목표(Z+ 로 깎음). F 는 부호 없이 리드 */
    @Test
    fun direction() {
        stops(G76Warning.Z_ORDER, original.copy(zStart = -25.0, zEnd = 5.0))
        stops(G76Warning.Z_ORDER, original.copy(zEnd = 5.0))
        stops(G76Warning.Z_ORDER, original.copy(dir = ThreadDir.REVERSE))
        val r = ok(G76Calc.calc(original.copy(dir = ThreadDir.REVERSE, zStart = -28.0, zEnd = 3.0)))
        assertEquals("G0X8.322Z-28.", r.program.first())
        assertEquals("G76X4.688Z3.P734Q200F1.27", r.nc[1])
    }

    /** 리드 F 는 0.0001 까지(FANUC 미터 입력 0.0001~500.0000 mm): 13 산 1.9538, 24 산 1.0583, 7 산 3.6286, 미터 1.5, 40 산 .635(앞 0 뺌) */
    @Test
    fun leadDigits() {
        fun lead(s: ThreadSpec) = ok(G76Calc.calc(G76Input(spec = s, zStart = 5.0, zEnd = -30.0, firstCut = 0.3))).nc[1].substringAfter("F")
        assertEquals("1.9538", lead(un("1/2", 13)))
        assertEquals("1.0583", lead(un("#10", 24)))
        assertEquals("3.6286", lead(un("1-1/8", 7)))
        assertEquals("1.5", lead(m(10.0, 1.5)))
        assertEquals("4.", lead(m(52.0, 4.0)))
        assertEquals(".635", ok(G76Calc.calc(G76Input(spec = un("#4", 40), zStart = 2.0, zEnd = -6.0, firstCut = 0.05))).nc[1].substringAfter("F"))
    }

    /** 멈추는 입력 */
    @Test
    fun stoppingInputs() {
        stops(G76Warning.MISSING_INPUT, G76Input())
        stops(G76Warning.MISSING_INPUT, original.copy(spec = null))
        stops(G76Warning.MISSING_INPUT, original.copy(zStart = null))
        stops(G76Warning.MISSING_INPUT, original.copy(zEnd = null))
        stops(G76Warning.MISSING_INPUT, original.copy(firstCut = null))
        for (bad in listOf(
            original.copy(angle = 45), original.copy(finishCount = 0), original.copy(finishCount = 100),
            original.copy(chamfer = 100), original.copy(chamfer = -1), original.copy(target = 101.0), original.copy(target = -1.0),
            original.copy(cls = ThreadClass.UN2B), original.copy(cls = ThreadClass.M6g), original.copy(inner = true, cls = ThreadClass.UN2A),
            original.copy(zStart = Double.NaN), original.copy(firstCut = 0.0), original.copy(firstCut = 0.0004), original.copy(firstCut = -0.1),
            original.copy(minCut = -0.01), original.copy(finishAllow = -0.01), original.copy(tipR = -0.1), original.copy(xClear = -1.0),
            original.copy(rpm = 0.0), original.copy(rpm = 0.4), original.copy(rpm = Double.POSITIVE_INFINITY), original.copy(zEnd = -20000.0),
        )) stops(G76Warning.BAD_INPUT, bad)
        stops(G76Warning.TIP_TOO_BIG, original.copy(tipR = 1.0))
        stops(G76Warning.FINISH_GE_HEIGHT, original.copy(finishAllow = 0.734))
        ok(G76Calc.calc(original.copy(finishAllow = 0.733, firstCut = 0.8)), listOf(G76Warning.ONE_ROUGH_PASS))
        stops(G76Warning.FIRST_CUT_LT_MIN, original.copy(firstCut = 0.03))
        stops(G76Warning.TOO_MANY_PASSES, original.copy(firstCut = 0.001, minCut = 0.0))
        // M3 암나사: 안지름 최소 2.459 − 2 × 1 = 0.459 > 0 이면 되고, M2(1.567 − 2) 는 X 여유를 줄여야 한다
        ok(G76Calc.calc(G76Input(inner = true, spec = m(3.0, 0.5), zStart = 2.0, zEnd = -6.0, firstCut = 0.1)))
        stops(G76Warning.CLEARANCE_TOO_BIG, G76Input(inner = true, spec = m(2.0, 0.4), zStart = 2.0, zEnd = -5.0, firstCut = 0.05))
        ok(G76Calc.calc(G76Input(inner = true, spec = m(2.0, 0.4), zStart = 2.0, zEnd = -5.0, firstCut = 0.05, xClear = 0.5)))
    }

    /** 첫 절입 = Δdmin 이면 거친 절입이 모두 Δdmin(√n 증가분이 처음부터 작다) */
    @Test
    fun firstCutEqualsMinimum() {
        val s = ok(G76Calc.calc(original.copy(firstCut = 0.035)))
        val rough = s.passes.filter { !it.finish }
        rough.dropLast(1).forEach { near(0.035, it.cut, 1e-12, "절입 ${it.n}") }
        near(s.height - s.finishAllow, rough.last().depth, 1e-12, "거친 끝")
    }
}
