package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 관용 테이퍼 나사 PT · NPT 의 G92 테이퍼 글(PipeThreadCalc). 기대값은 손 계산(식과 수를 주석에 — 표 값은 docs/research/pipe-threads.json 의
 * JIS B 0203 付表1 · H28 1957 Part II 표 VII.2)이고, 치수(기준면 · Z · 절입 · 회전수)는 새로 지은 값이다.
 * 표의 모든 칸이 JSON 과 같은지는 jvmTest PipeThreadJsonTest, G92 글을 시뮬레이터로 돌린 결과는 sim/G92TaperThreadTest 가 본다.
 */
class PipeThreadTest {
    private fun pt(size: String) = PipeTable.PT.single { it.size == size }
    private fun npt(size: String) = PipeTable.NPT.single { it.size == size }

    private fun ok(r: PipeResult, warnings: List<G76Warning> = emptyList()): PipeSolution {
        assertEquals(warnings, r.warnings)
        return assertNotNull(r.solution)
    }

    private fun stops(w: G76Warning, i: PipeInput) {
        val r = PipeThreadCalc.calc(i)
        assertNull(r.solution, "$w: $i")
        assertEquals(listOf(w), r.warnings, "$i")
    }

    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    private val p14 = 25.4 / 14

    /** 지은 치수 — 손 계산 네 가지 */
    private val rExt = PipeInput(spec = pt("1/2"), zStart = 6.0, zEnd = -14.0, firstCut = 0.4, rpm = 530.0)
    private val rcInt = PipeInput(inner = true, spec = pt("1/2"), zStart = 4.0, zEnd = -16.0, firstCut = 0.3)
    private val nptExt = PipeInput(spec = npt("1/2"), zStart = 6.0, zEnd = -14.0, firstCut = 0.4, rpm = 470.0)
    private val nptInt = PipeInput(inner = true, spec = npt("1/2"), zRef = -5.0, zStart = -1.0, zEnd = -21.0, firstCut = 0.4)

    @Test
    fun tableInvariants() {
        assertEquals(
            listOf("1/16", "1/8", "1/4", "3/8", "1/2", "3/4", "1", "1 1/4", "1 1/2", "2", "2 1/2", "3", "3 1/2", "4", "5", "6"),
            PipeTable.PT.map { it.size },
        )
        assertEquals(
            listOf("1/16", "1/8", "1/4", "3/8", "1/2", "3/4", "1", "1 1/4", "1 1/2", "2", "2 1/2", "3", "3 1/2", "4"),
            PipeTable.NPT.map { it.size },
        )
        assertEquals(PipeTable.PT, PipeTable.of(PipeKind.PT))
        assertEquals(PipeTable.NPT, PipeTable.of(PipeKind.NPT))
        for (s in PipeTable.PT + PipeTable.NPT) {
            near(25.4 / s.tpi, s.pitch, 1e-12, "${s.label} 피치")
            when (s.kind) {
                PipeKind.PT -> {
                    assertEquals(55, s.angleDeg)
                    near(0.640327 * s.pitch, s.h, 1e-12, "${s.label} h")
                    assertTrue(s.rootR != null, s.label)
                }
                PipeKind.NPT -> {
                    assertEquals(60, s.angleDeg)
                    near(0.8 * s.pitch, s.h, 1e-12, "${s.label} h")
                    assertNull(s.rootR, s.label)
                    assertEquals(1.0, s.tolTurns)
                    assertEquals(1.0, s.tolTurnsInner)
                }
            }
            // 숫나사 유효 나사는 게이지 면을 넘어 간다, 크기가 커지면 게이지 면 피치경도 커진다
            assertTrue(s.extLen > s.gaugeLen, s.label)
        }
        for (list in listOf(PipeTable.PT, PipeTable.NPT)) {
            assertTrue(list.zipWithNext().all { (a, b) -> b.gaugePitch > a.gaugePitch }, "지름 순")
        }
        // 이름: PT 는 숫 R · 암 Rc, NPT 는 산수 11.5 를 그대로
        assertEquals("PT 1/2", pt("1/2").label)
        assertEquals("R 1/2", pt("1/2").name(false))
        assertEquals("Rc 1 1/4", pt("1 1/4").name(true))
        assertEquals("1/2-14 NPT", npt("1/2").label)
        assertEquals("1/2-14 NPT", npt("1/2").name(true))
        assertEquals("1 1/4-11.5 NPT", npt("1 1/4").label)
    }

    /**
     * R 1/2 숫나사(JIS 付表1: 14산 · d 20.955 · a 8.16 · f 5.0), 기준면 Z0 · Z6 → −14 · Δd 0.4 · 530 rpm:
     * P = 25.4/14 = 1.8142857, h = 0.640327P = 1.1617361 → k 1.162. 게이지 면 Zg = 0 − 8.16 = −8.16, Eg = d − h = 19.7932639.
     * E(−14) = Eg + (−8.16 + 14)/16 = Eg + 0.365 = 20.1582639 → 끝 골 X = E − h = 20.955 + 0.365 − 2h = 18.9965278 → X18.997.
     * 끝 Z 바깥지름 = E + h = 21.32 → 시작 X = 21.32 + 2 × 1 = 23.32, 기준면 바깥지름 = 20.955 − 8.16/16 = 20.445.
     * I = −(6 − (−14))/32 = −0.625. 절입 깊이 0.4√n — 0.4 · 0.5657 · 0.6928 · 0.8 · 0.8944 · 0.9798 · 1.0583 · 1.1314, 다음 1.2 ≥ k − d = 1.142 라
     * 1.142, 정삭 1.162 두 번. 회차 X = 18.997 + 2(1.162 − 깊이): 20.521 · 20.1896 · 19.9354 · 19.721 · 19.5321 · 19.3614 · 19.2044 · 19.0583 ·
     * 19.037 · 18.997 · 18.997. 나사 길이 14 ≥ a + f = 13.16.
     */
    @Test
    fun rHalfExternalHandNumbers() {
        val s = ok(PipeThreadCalc.calc(rExt))
        near(-8.16, s.gaugeZ, 1e-12, "게이지 면 Z")
        near(19.7932639, s.gaugePitch, 1e-6, "게이지 면 피치경")
        near(20.1582639, s.pitchAtEnd, 1e-6, "끝 피치경")
        near(18.9965278, s.x, 1e-6, "끝 골 X")
        near(21.32, s.crestAtEnd, 1e-9, "끝 바깥지름")
        near(20.445, s.crestAtRef, 1e-9, "기준면 바깥지름")
        near(23.32, s.startX, 1e-9, "시작 X")
        assertEquals(1.162, s.height)
        assertEquals(-0.625, s.taper)
        near(p14, s.lead, 1e-12, "리드")
        near(530 * p14, s.feedPerMin!!, 1e-9, "분당 이송")
        assertEquals(14.0, s.threadLen)
        near(13.16, s.needLen, 1e-9, "최소 길이 a + f")
        assertEquals(11, s.passes.size)
        assertEquals(listOf(false, false, false, false, false, false, false, false, false, true, true), s.passes.map { it.finish })
        assertEquals(
            listOf(
                "G97S530", "G0X23.32Z20.", "Z6.", "M23", "G92X20.521Z-14.I-.625F1.8143",
                "X20.19", "X19.935", "X19.721", "X19.532", "X19.361", "X19.204", "X19.058", "X19.037", "X18.997", "X18.997",
                "M24", "G0Z200.",
            ),
            s.g92,
        )
        assertEquals(listOf("G97S530", "G0X23.32Z20.", "Z6.", "M23", "G92X18.997Z-14.I-.625F1.8143", "X18.997", "M24", "G0Z200."), s.g92Finish)
    }

    /**
     * Rc 1/2 암나사, 기준면 Z0 = 게이지 면(JIS 의 암나사 기준경 자리 — 관 끝 쪽) · Z4 → −16 · Δd 0.3:
     * E(−16) = Eg − (0 + 16)/16 = 19.7932639 − 1 = 18.7932639 → 끝 골 X = E + h = 20.955 − 1 = 19.955.
     * 끝 Z 안지름 = E − h = 17.6315278 → 시작 X = 17.6315278 − 2 = 15.6315278 → X15.632, 기준면 안지름 = Eg − h = 18.6315278.
     * I = +(4 + 16)/32 = 0.625(입구 쪽이 굵다). 깊이 0.3√n — 0.3 · 0.4243 · 0.5196 · 0.6 · 0.6708 · 0.7348 · 0.7937 · 0.8485 · 0.9 · 0.9487 · 0.995 ·
     * 1.0392 · 1.0817 · 1.1225, 다음 1.1619 ≥ 1.142 라 1.142, 정삭 두 번. 회차 X = 19.955 − 2(1.162 − 깊이). 나사 길이 16 ≥ l = 12.7.
     */
    @Test
    fun rcHalfInternalHandNumbers() {
        val s = ok(PipeThreadCalc.calc(rcInt))
        near(0.0, s.gaugeZ, 1e-12, "게이지 면 Z")
        near(18.7932639, s.pitchAtEnd, 1e-6, "끝 피치경")
        near(19.955, s.x, 1e-9, "끝 골 X")
        near(17.6315278, s.crestAtEnd, 1e-6, "끝 안지름")
        near(18.6315278, s.crestAtRef, 1e-6, "기준면 안지름")
        near(15.6315278, s.startX, 1e-6, "시작 X")
        assertEquals(0.625, s.taper)
        assertNull(s.feedPerMin)
        near(12.7, s.needLen, 1e-12, "최소 길이 l")
        assertEquals(
            listOf(
                "G0X15.632Z20.", "Z4.", "M23", "G92X18.231Z-16.I.625F1.8143",
                "X18.48", "X18.67", "X18.831", "X18.973", "X19.101", "X19.218", "X19.328", "X19.431", "X19.528", "X19.621", "X19.709",
                "X19.794", "X19.876", "X19.915", "X19.955", "X19.955", "M24", "G0Z200.",
            ),
            s.g92,
        )
        assertEquals(listOf("G0X15.632Z20.", "Z4.", "M23", "G92X19.955Z-16.I.625F1.8143", "X19.955", "M24", "G0Z200."), s.g92Finish)
    }

    /**
     * 1/2-14 NPT 숫나사(H28 1957 Part II 표 VII.2: E1 0.77843 in · L1 0.320 in · L2 0.5337 in), 기준면 Z0 · Z6 → −14 · Δd 0.4 · 470 rpm:
     * E1 = 19.772122 mm, L1 = 8.128 mm → Zg = −8.128, h = 0.8P = 1.4514286 → k 1.451.
     * E(−14) = 19.772122 + (−8.128 + 14)/16 = 19.772122 + 0.367 = 20.139122 → 끝 골 X = E − h = 18.6876934 → X18.688.
     * 끝 Z 바깥지름 = E + h = 21.5905506 → 시작 X = 23.5905506 → X23.591, 기준면 바깥지름 = 19.772122 − 0.508 + 1.4514286 = 20.7155506.
     * I = −0.625. 깊이 0.4√n — … 1.2 · 1.2649 · 1.3266 · 1.3856, 다음 1.4422 ≥ k − d = 1.431 이라 1.431, 정삭 두 번.
     * 회차 X = 18.688 + 2(1.451 − 깊이). 나사 길이 14 ≥ L2 = 13.55598.
     */
    @Test
    fun nptExternalHandNumbers() {
        val s = ok(PipeThreadCalc.calc(nptExt))
        near(-8.128, s.gaugeZ, 1e-9, "게이지 면 Z")
        near(19.772122, s.gaugePitch, 1e-9, "게이지 면 피치경 E1")
        near(18.6876934, s.x, 1e-6, "끝 골 X")
        near(20.7155506, s.crestAtRef, 1e-6, "기준면 바깥지름")
        near(23.5905506, s.startX, 1e-6, "시작 X")
        assertEquals(1.451, s.height)
        assertEquals(-0.625, s.taper)
        near(13.55598, s.needLen, 1e-9, "최소 길이 L2")
        assertEquals(
            listOf(
                "G97S470", "G0X23.591Z20.", "Z6.", "M23", "G92X20.79Z-14.I-.625F1.8143",
                "X20.459", "X20.204", "X19.99", "X19.801", "X19.63", "X19.473", "X19.327", "X19.19", "X19.06", "X18.937", "X18.819",
                "X18.728", "X18.688", "X18.688", "M24", "G0Z200.",
            ),
            s.g92,
        )
    }

    /**
     * 1/2-14 NPT 암나사, 기준면 Z−5(입구 면 = 플러그 게이지 노치 자리) · Z−1 → −21 · Δd 0.4:
     * E(−21) = 19.772122 − (−5 + 21)/16 = 18.772122 → 끝 골 X = E + h = 20.2235506 → X20.224.
     * 끝 Z 안지름 = E − h = 17.3206934 → 시작 X = 15.3206934 → X15.321. I = +(−1 + 21)/32 = 0.625.
     * 회차 X = 20.224 − 2(1.451 − 깊이), 깊이는 숫나사와 같다. 나사 길이 −5 + 21 = 16 ≥ L1 + L3 = (0.320 + 0.2143) × 25.4 = 13.57122.
     */
    @Test
    fun nptInternalHandNumbers() {
        val s = ok(PipeThreadCalc.calc(nptInt))
        near(-5.0, s.gaugeZ, 1e-12, "게이지 면 Z = 기준면")
        near(20.2235506, s.x, 1e-6, "끝 골 X")
        near(15.3206934, s.startX, 1e-6, "시작 X")
        assertEquals(0.625, s.taper)
        assertEquals(16.0, s.threadLen)
        near(13.57122, s.needLen, 1e-9, "최소 길이 L1 + L3")
        assertEquals(
            listOf(
                "G0X15.321Z20.", "Z-1.", "M23", "G92X18.122Z-21.I.625F1.8143",
                "X18.453", "X18.708", "X18.922", "X19.111", "X19.282", "X19.439", "X19.585", "X19.722", "X19.852", "X19.975", "X20.093",
                "X20.184", "X20.224", "X20.224", "M24", "G0Z200.",
            ),
            s.g92,
        )
    }

    /**
     * I 의 부호와 크기: 숫나사 −(시작 Z − 끝 Z)/32 · 암나사 +(…)/32, 0.001 로 반올림한 지령값. 길이 25 면 0.78125 → .781.
     * 다른 산수 · 크기에서도 같은 규칙(테이퍼는 1/16 고정)이고, 리드 F 는 곧은 나사와 같은 넷째 자리(11.5산 25.4/11.5 = 2.2086957 → F2.2087).
     */
    @Test
    fun taperSignAndSize() {
        for (spec in listOf(pt("1/8"), pt("2"), npt("1"), npt("4"))) {
            for (inner in listOf(false, true)) {
                // 큰 크기는 나사 길이 20 이 규격 최소보다 짧아 SHORT_THREAD 가 같이 나온다 — 여기서는 I 만 본다
                val s = assertNotNull(PipeThreadCalc.calc(PipeInput(inner = inner, spec = spec, zStart = 5.0, zEnd = -20.0, firstCut = 0.3)).solution)
                assertEquals(if (inner) 0.781 else -0.781, s.taper, spec.name(inner))
                assertTrue(s.g92.single { it.startsWith("G92") }.contains(if (inner) "I.781F" else "I-.781F"), "${s.g92}")
            }
        }
        assertTrue(ok(PipeThreadCalc.calc(PipeInput(spec = npt("1"), zStart = 5.0, zEnd = -20.0, firstCut = 0.3))).g92.single { it.startsWith("G92") }.endsWith("F2.2087"))
        assertTrue(ok(PipeThreadCalc.calc(PipeInput(spec = pt("1/8"), zStart = 5.0, zEnd = -10.0, firstCut = 0.2))).g92.single { it.startsWith("G92") }.endsWith("F.9071"))
    }

    /** 테이퍼 낱말 R(FANUC 0i 기본 형식): 첫 G92 줄의 I 자리만 R, 나머지 글은 같다. I · R 밖은 BAD_INPUT */
    @Test
    fun taperWordR() {
        val i = ok(PipeThreadCalc.calc(rExt))
        val r = ok(PipeThreadCalc.calc(rExt.copy(taperWord = 'R')))
        assertEquals(i.g92.map { it.replace("I-.625", "R-.625") }, r.g92)
        assertEquals("G92X20.521Z-14.R-.625F1.8143", r.g92[4])
        assertEquals(i.g92Finish.map { it.replace("I-.625", "R-.625") }, r.g92Finish)
        assertEquals(i.passes, r.passes)
        stops(G76Warning.BAD_INPUT, rExt.copy(taperWord = 'K'))
        stops(G76Warning.BAD_INPUT, rExt.copy(taperWord = 'i'))
    }

    /**
     * 게이지 면 옮김 s 산: 숫나사는 피치경을 s·P/16 가늘게(끝 골 X 도 −s·P/16), 암나사는 굵게(+s·P/16). P/16 = 1.8142857/16 = 0.1133929.
     * R 1/2 숫나사 s = +1: 끝 골 X = 18.9965278 − 0.1133929 = 18.8831349 → X18.883, 첫 회 X = 18.883 + 2(1.162 − 0.4) = 20.407.
     * 최소 길이도 s·P 만큼(게이지 면이 척 쪽으로 P): 13.16 + 1.8142857 = 14.9742857.
     */
    @Test
    fun gaugeShiftMovesX() {
        val q = p14 / 16
        for ((tag, base) in listOf("R 숫" to rExt, "Rc 암" to rcInt, "NPT 숫" to nptExt, "NPT 암" to nptInt)) {
            val x0 = ok(PipeThreadCalc.calc(base)).x
            for (sh in listOf(1.0, -1.0, 0.5)) {
                val s = assertNotNull(PipeThreadCalc.calc(base.copy(gaugeShift = sh)).solution, "$tag $sh")
                near(x0 + (if (base.inner) 1 else -1) * sh * q, s.x, 1e-9, "$tag 옮김 $sh 산 X")
                near(PipeThreadCalc.calc(base).solution!!.needLen + sh * p14, s.needLen, 1e-9, "$tag 옮김 $sh 산 최소 길이")
            }
        }
        val plus = assertNotNull(PipeThreadCalc.calc(rExt.copy(gaugeShift = 1.0)).solution)
        near(18.8831349, plus.x, 1e-6, "R 1/2 +1산 X")
        near(14.9742857, plus.needLen, 1e-6, "R 1/2 +1산 최소 길이")
        assertEquals("G92X20.407Z-14.I-.625F1.8143", plus.g92[4])
        assertEquals("X18.883", plus.g92[plus.g92.size - 3])
    }

    /**
     * 게이지 공차(산): PT 숫 1산(1/16 ~ 2) · 1½산(2½ ~ 6), PT 암 1¼ · 1½산(ISO 7-1 Table 1 의 10 · 14열 — JIS b · c 는 산 × P 를 0.01 로 둥근 값),
     * NPT ±1산(H28 1957 Part II 8항). 공차 안은 숫자, 밖은 BAD_INPUT
     */
    @Test
    fun gaugeShiftTolerance() {
        assertNotNull(PipeThreadCalc.calc(rExt.copy(gaugeShift = 1.0)).solution)
        assertNotNull(PipeThreadCalc.calc(rExt.copy(gaugeShift = -1.0)).solution)
        stops(G76Warning.BAD_INPUT, rExt.copy(gaugeShift = 1.05))
        assertNotNull(PipeThreadCalc.calc(rcInt.copy(gaugeShift = 1.25)).solution)
        stops(G76Warning.BAD_INPUT, rcInt.copy(gaugeShift = -1.3))
        val big = PipeInput(spec = pt("2 1/2"), zStart = 5.0, zEnd = -30.0, firstCut = 0.4)
        assertNotNull(PipeThreadCalc.calc(big.copy(gaugeShift = 1.5)).solution)
        stops(G76Warning.BAD_INPUT, big.copy(gaugeShift = 1.6))
        assertNotNull(PipeThreadCalc.calc(nptInt.copy(gaugeShift = -1.0)).solution)
        stops(G76Warning.BAD_INPUT, nptInt.copy(gaugeShift = 1.01))
        stops(G76Warning.BAD_INPUT, nptExt.copy(gaugeShift = Double.NaN))
    }

    /**
     * 나사 길이 경고(숫자와 함께): 기준면 − 끝 Z < 규격 최소 + s·P. R 1/2 숫 13.16 · Rc 1/2 암 12.7 · NPT 1/2 숫 13.55598 · 암 13.57122.
     * 같으면 경고 없음(경계).
     */
    @Test
    fun shortThread() {
        ok(PipeThreadCalc.calc(rExt.copy(zEnd = -13.0)), listOf(G76Warning.SHORT_THREAD))
        ok(PipeThreadCalc.calc(rExt.copy(zEnd = -13.16)))
        // +1산 옮기면 14 < 14.974 — 같은 끝 Z 가 짧아진다
        ok(PipeThreadCalc.calc(rExt.copy(gaugeShift = 1.0)), listOf(G76Warning.SHORT_THREAD))
        ok(PipeThreadCalc.calc(rcInt.copy(zEnd = -12.0)), listOf(G76Warning.SHORT_THREAD))
        ok(PipeThreadCalc.calc(rcInt.copy(zEnd = -12.7)))
        ok(PipeThreadCalc.calc(nptExt.copy(zEnd = -13.5)), listOf(G76Warning.SHORT_THREAD))
        ok(PipeThreadCalc.calc(nptInt.copy(zEnd = -18.5)), listOf(G76Warning.SHORT_THREAD))
        // 끝 Z 가 기준면 밖이면 나사 길이 ≤ 0 — 숫자는 내고 경고
        val out = ok(PipeThreadCalc.calc(rExt.copy(zStart = 10.0, zEnd = 2.0)), listOf(G76Warning.SHORT_THREAD))
        assertEquals(-2.0, out.threadLen)
    }

    /** 멈추는 입력과 숫자와 함께 나오는 경고 — 곧은 나사와 같은 경계(시작 Z 가 기준면 위는 됨) */
    @Test
    fun warnings() {
        for (i in listOf(rExt.copy(spec = null), rExt.copy(zStart = null), rExt.copy(zEnd = null), rExt.copy(firstCut = null))) {
            stops(G76Warning.MISSING_INPUT, i)
        }
        for (i in listOf(
            rExt.copy(rpm = 0.5), rExt.copy(rpm = 200_000.0), rExt.copy(firstCut = 0.0), rExt.copy(firstCut = 0.0004),
            rExt.copy(finishCount = 0), rExt.copy(finishCount = 100), rExt.copy(minCut = -0.01), rExt.copy(finishAllow = -0.01),
            rExt.copy(xClear = -1.0), rExt.copy(zStart = Double.NaN), rExt.copy(zRef = Double.POSITIVE_INFINITY), rExt.copy(zEnd = -20_000.0),
            // 숫나사 끝 골 X ≤ 0: R 1/16 은 끝 Z 가 기준면에서 바깥으로 100 mm 넘게 가면 피치경이 0 아래(7.14 + (−3.97 − 200)/16 < 0)
            PipeInput(spec = pt("1/16"), zStart = 300.0, zEnd = 200.0, firstCut = 0.1),
        )) stops(G76Warning.BAD_INPUT, i)
        stops(G76Warning.Z_ORDER, rExt.copy(zStart = -14.0))
        stops(G76Warning.Z_ORDER, rExt.copy(zStart = -20.0))
        // 시작 Z 가 기준면 안(숫 · 암 같다), 기준면 위는 됨
        stops(G76Warning.START_INSIDE, rExt.copy(zStart = -0.5))
        stops(G76Warning.START_INSIDE, nptInt.copy(zStart = -6.0))
        ok(PipeThreadCalc.calc(rExt.copy(zStart = 0.0)))
        ok(PipeThreadCalc.calc(nptInt.copy(zStart = -5.0)))
        stops(G76Warning.FINISH_GE_HEIGHT, rExt.copy(finishAllow = 1.162))
        stops(G76Warning.FIRST_CUT_LT_MIN, rExt.copy(firstCut = 0.02))
        stops(G76Warning.TOO_MANY_PASSES, rExt.copy(firstCut = 0.001, minCut = 0.0))
        // Rc 1/16 암나사 끝 Z 안지름 = 7.142 − 1/16·(0 + 10) − 0.581 = 5.936 → 여유 3 이면 시작 X = −0.064
        stops(G76Warning.CLEARANCE_TOO_BIG, PipeInput(inner = true, spec = pt("1/16"), zStart = 3.0, zEnd = -10.0, firstCut = 0.1, xClear = 3.0))
        // 첫 절입 ≥ k − d = 1.142: 거친 절입 한 번
        val one = ok(PipeThreadCalc.calc(rExt.copy(firstCut = 1.2)), listOf(G76Warning.ONE_ROUGH_PASS))
        assertEquals(3, one.passes.size)
        ok(PipeThreadCalc.calc(rExt.copy(firstCut = 1.2, zEnd = -13.0)), listOf(G76Warning.ONE_ROUGH_PASS, G76Warning.SHORT_THREAD))
    }
}
