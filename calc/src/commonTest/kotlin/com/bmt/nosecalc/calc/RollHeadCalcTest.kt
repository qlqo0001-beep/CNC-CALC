package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 전조 다이스 글(RollHeadCalc). 기대값은 손 계산(파이썬 — 식과 수를 주석에). 끝 Z −16.37 등 치수는 새로 지은 값이다.
 * 근거: dV = 2A(6g) 유효지름 가운데 − 0.03, n = 1000·v/(π·dV), v = 재질 표 아래 끝(STS316 30 · STS304 35 m/min), 20산 F1.22, 그 밖 0.95 × P,
 * 열림 행정 a(F2 · F23 · F233400 3, F3 · F34 4). 회사 지름 · S 는 아직 null 이라 회전수는 Fette 추천이다.
 */
class RollHeadCalcTest {
    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
    private fun m(size: String, pitch: Double) = ThreadTable.METRIC.single { it.size == size && it.pitch == pitch }
    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")

    private fun ok(i: RollInput, warnings: List<RollWarning> = emptyList()): RollSolution {
        val r = RollHeadCalc.calc(i)
        assertEquals(warnings, r.warnings, "$i")
        return assertNotNull(r.solution)
    }

    private fun stops(w: RollWarning, i: RollInput) {
        val r = RollHeadCalc.calc(i)
        assertNull(r.solution, "$w: $i")
        assertEquals(listOf(w), r.warnings, "$i")
    }

    private val base = RollInput(spec = un("3/4", 20), zEnd = -16.37)

    /**
     * 3/4-20 UNEF(d 19.05 · P 1.27), 2A 유효지름 18.079662 ~ 18.191546(카탈로그 부록 18.080 / 18.191) → dV 18.049662 ~ 18.161546, 가운데 18.105604.
     * STS316 30 m/min: n = 30000/(π × 18.105604) = 527.42 → S527, 50 m/min → 879. 쓴 v = π × 18.105604 × 527/1000 = 29.975987.
     * 20산 → F1.22(비율 1.22/1.27 = 0.960630). 시간 = 60 × (5 + 16.37)/(1.22 × 527) + 1 = 2.994276.
     * 머리 F34(회사 처음 값 · AT08), a = 4 → 실제 끝 Z −20.37, 전조 길이 (5 + 16.37) + 4 = 25.37(끝면이 없어 시작 Z 부터).
     * 바깥지름 2A 18.809596 ~ 19.016435.
     */
    @Test
    fun threeQuarterDefaults() {
        val s = ok(base)
        assertEquals("F34", s.head.key)
        assertEquals(CompanyHead("F34", "AT08"), s.companyHead)
        near(18.049662, s.blank.recMin, 1e-6, "dV 최소")
        near(18.161546, s.blank.recMax, 1e-6, "dV 최대")
        near(18.105604, s.blank.rec, 1e-6, "dV")
        assertNull(s.blank.company)
        assertEquals(RollBlankBasis.FETTE, s.blank.usedBasis)
        assertEquals(527L, s.rpm)
        assertEquals(RollRpmBasis.FETTE, s.rpmBasis)
        assertEquals(527L to 879L, s.rpmRange)
        near(29.975987, s.vUsed, 1e-6, "v")
        assertEquals(1.22, s.feed)
        near(0.960630, s.ratio, 1e-6, "비율")
        assertEquals(RollRatioBasis.COMPANY, s.ratioBasis)
        near(2.994276, s.timeSec, 1e-6, "시간")
        assertEquals(5.0, s.zStart)
        assertEquals(4.0, s.openingMovement)
        near(-20.37, s.rollEndZ!!, 1e-9, "실제 끝")
        near(25.37, s.rolledLength!!, 1e-9, "전조 길이")
        near(18.809596, s.crest.min, 1e-6, "바깥지름 최소")
        near(19.016435, s.crest.max, 1e-6, "바깥지름 최대")
        assertNull(s.rpmDirection)
        assertNull(s.ratioDirection)
        assertEquals(listOf("G97S527", "G0X0.Z20.", "Z5.", "G1Z-16.37F1.22", "G4U1.", "G0Z150."), s.program)
    }

    /** STS304 35 m/min → 30000 대신 35000/(π × 18.105604) = 615.33 → S615, 범위 615 ~ 967 */
    @Test
    fun sts304() {
        val s = ok(base.copy(steel = RollSteel.STS304))
        assertEquals(615L, s.rpm)
        assertEquals(615L to 967L, s.rpmRange)
        assertEquals("1.4301", s.speedRow?.materialNo)
    }

    /**
     * 일곱 규격 × 두 소재 꼴 — 회사 값이 아직 없어 소재 꼴과 상관없이 같고, 머리는 회사 처음 값, F 는 1.22, 경고 없음
     * (끝면 0 · 끝 −16.37 → 전조 길이 16.37 + a ≤ 20.37 — F34 7/8 · 1 의 선택표 24 mm 안).
     * 1/2-20 UNF(물림 길이 = 호칭 지름): dV 11.757998 → S812, 7/16-20 UNF: dV 10.172944 → S939.
     */
    @Test
    fun sevenSizesBothStocks() {
        val heads = mapOf("7/16" to "F23", "1/2" to "F2", "9/16" to "F23", "5/8" to "F23", "3/4" to "F34", "7/8" to "F34", "1" to "F34")
        for (spec in CompanyRolls.specs) for (stock in RollStock.entries) {
            val s = ok(RollInput(spec = spec, stock = stock, zEnd = -16.37, zFace = 0.0))
            assertEquals(heads[spec.size], s.head.key, spec.size)
            assertEquals(CompanyRolls.of(spec)!!.defaultHead, s.companyHead, spec.size)
            assertEquals("G1Z-16.37F1.22", s.program[3], spec.size)
            assertEquals(RollRpmBasis.FETTE, s.rpmBasis, spec.size)
            assertNull(s.blank.company, spec.size)
        }
        assertEquals(812L, ok(RollInput(spec = un("1/2", 20), zEnd = -16.37)).rpm)
        near(11.757998, ok(RollInput(spec = un("1/2", 20), zEnd = -16.37)).blank.rec, 1e-6, "1/2 dV")
        assertEquals(939L, ok(RollInput(spec = un("7/16", 20), zEnd = -16.37)).rpm)
        // 다른 머리 고르기(회사 둘째 짝): 3/4 F233400 · AT02, a = 3
        val alt = ok(base.copy(head = "F233400"))
        assertEquals(CompanyHead("F233400", "AT02"), alt.companyHead)
        assertEquals(3.0, alt.openingMovement)
    }

    /**
     * 표 밖 규격 M12×1.5(6g): 유효지름 10.853721 ~ 10.993721 → dV 가운데 10.893721, S = 30000/(π × 10.893721) = 876.59 → 877.
     * 이송 Fette 0.95 × 1.5 = 1.425. 머리는 지름 범위에 드는 첫 EVOline 머리 F2(Ø8–16), 회사 롤 없음. 바깥지름 6g 11.732 ~ 11.968.
     */
    @Test
    fun metricOutsideCompanyTable() {
        val s = ok(RollInput(spec = m("M12", 1.5), zEnd = -16.37))
        assertEquals("F2", s.head.key)
        assertNull(s.companyHead)
        near(10.893721, s.blank.rec, 1e-6, "dV")
        assertEquals(877L, s.rpm)
        near(1.425, s.feed, 1e-12, "F")
        assertEquals(0.95, s.ratio)
        assertEquals(RollRatioBasis.FETTE, s.ratioBasis)
        assertEquals("G1Z-16.37F1.425", s.program[3])
        near(11.732, s.crest.min, 1e-9, "6g 최소")
        near(11.968, s.crest.max, 1e-9, "6g 최대")
        assertEquals(listOf("F2", "F23", "F3", "F34"), RollHeadCalc.headsFor(m("M12", 1.5)).map { it.key })
    }

    /** 표 밖 20산 1-5/16-20 UN(Ø33.34): F233400(Ø16–36)만, 20산이라 F1.22(결정 D7′), S295. 1-1/2-20(Ø38.1)은 범위에 드는 머리가 없어 멈춤 */
    @Test
    fun outsideTableTwentyTpi() {
        val s = ok(RollInput(spec = un("1-5/16", 20), zEnd = -16.37))
        assertEquals("F233400", s.head.key)
        assertNull(s.companyHead)
        assertEquals("G1Z-16.37F1.22", s.program[3])
        assertEquals(295L, s.rpm)
        stops(RollWarning.OUT_OF_HEAD_RANGE, RollInput(spec = un("1-1/2", 20), zEnd = -16.37))
        // 회사 짝이 아닌 머리를 범위 밖에서 고르면 멈춤(9/16 Ø14.29 < F233400 Ø16)
        stops(RollWarning.OUT_OF_HEAD_RANGE, RollInput(spec = un("9/16", 20), head = "F233400", zEnd = -16.37))
        stops(RollWarning.OUT_OF_HEAD_RANGE, base.copy(head = "F9"))
    }

    /** 스테인리스가 아니면 회사 S · Fette 추천이 없다 — 회전수를 넣어야 하고, 넣으면 범위 경고 없음 */
    @Test
    fun nonStainlessNeedsRpm() {
        stops(RollWarning.MISSING_INPUT, base.copy(material = IsoGroup.P))
        val s = ok(base.copy(material = IsoGroup.P, rpm = 700.0))
        assertEquals(700L, s.rpm)
        assertEquals(RollRpmBasis.INPUT, s.rpmBasis)
        assertNull(s.rpmRange)
        assertNull(s.rpmDirection)
        // 비운 소재 = 스테인리스
        assertEquals(527L, ok(base.copy(material = IsoGroup.M)).rpm)
    }

    /**
     * 넣은 회전수가 범위의 S(527 ~ 879 — 아래 끝 · 위 끝을 반올림) 밖이면 노랑 + 방향: S880 → RPM_OUT_OF_RANGE · RPM_UP, S879 → 안 · RPM_UP,
     * 아래 경계는 STS304 범위(615 ~ 967)로 본다: S614 → 밖 · RPM_DOWN, S615(기준과 같음 — v 34.981 이 35 아래지만 반올림한 아래 끝 그대로) → 경고 · 방향 없음,
     * S967 → 안 · RPM_UP, S968 → 밖 · RPM_UP
     */
    @Test
    fun rpmBoundsAndDirection() {
        val up = ok(base.copy(rpm = 880.0), listOf(RollWarning.RPM_OUT_OF_RANGE))
        assertEquals(RollTipGroup.RPM_UP, up.rpmDirection)
        assertEquals(527L, up.baseRpm)
        assertEquals(RollTipGroup.RPM_UP, ok(base.copy(rpm = 879.0)).rpmDirection)
        val s304 = base.copy(steel = RollSteel.STS304)
        assertEquals(RollTipGroup.RPM_DOWN, ok(s304.copy(rpm = 614.0), listOf(RollWarning.RPM_OUT_OF_RANGE)).rpmDirection)
        assertNull(ok(s304.copy(rpm = 615.0)).rpmDirection)
        assertEquals(RollTipGroup.RPM_UP, ok(s304.copy(rpm = 967.0)).rpmDirection)
        assertEquals(RollTipGroup.RPM_UP, ok(s304.copy(rpm = 968.0), listOf(RollWarning.RPM_OUT_OF_RANGE)).rpmDirection)
        // 1.4 는 반올림해 S1(범위 아래)
        assertEquals("G97S1", ok(base.copy(rpm = 1.4), listOf(RollWarning.RPM_OUT_OF_RANGE)).program[0])
    }

    /** 비율: 1.04 → RATIO_OVER_ONE · RATIO_UP(F = 1.04 × 1.27 = 1.3208 → F1.321), 0.9 → RATIO_DOWN(1.143), 1 은 경고 없음 · RATIO_UP */
    @Test
    fun ratio() {
        val over = ok(base.copy(ratio = 1.04), listOf(RollWarning.RATIO_OVER_ONE))
        assertEquals(RollTipGroup.RATIO_UP, over.ratioDirection)
        assertEquals("G1Z-16.37F1.321", over.program[3])
        assertEquals(RollRatioBasis.INPUT, over.ratioBasis)
        near(0.960630, over.baseRatio, 1e-6, "기준 비율")
        val down = ok(base.copy(ratio = 0.9))
        assertEquals(RollTipGroup.RATIO_DOWN, down.ratioDirection)
        assertEquals("G1Z-16.37F1.143", down.program[3])
        assertEquals(RollTipGroup.RATIO_UP, ok(base.copy(ratio = 1.0)).ratioDirection)
        assertNull(ok(base.copy(ratio = 1.22 / 1.27)).ratioDirection)
    }

    @Test
    fun zStartDwellAndProgram() {
        assertEquals("Z3.", ok(base.copy(zStart = 3.0)).program[2])
        // 멈춤 0 → G4 줄 없음(머리가 열리기 전에 물러남 — DWELL_ZERO), 시간 = G1 만. 0.0004 도 찍히면 0
        val noDwell = ok(base.copy(dwell = 0.0), listOf(RollWarning.DWELL_ZERO))
        ok(base.copy(dwell = 0.0004), listOf(RollWarning.DWELL_ZERO))
        ok(base.copy(dwell = 0.001))
        assertEquals(listOf("G97S527", "G0X0.Z20.", "Z5.", "G1Z-16.37F1.22", "G0Z150."), noDwell.program)
        near(1.994276, noDwell.timeSec, 1e-6, "시간")
        assertEquals("G0Z120.", ok(base.copy(retractZ = 120.0)).program.last())
    }

    /**
     * 최대 전조 길이(카탈로그 선택표): 3/4-20 · F233400(a 3) 73 — 끝면 0 · 끝 −75 → 75 + 3 = 78 > 73 경고, −70 → 73 은 없음.
     * 1-20 · F34(a 4) 24 — 끝 −20 → 24 없음, −20.01 → 24.01 경고. 3/4-20 · F23(a 3) 19 — 끝 −16 → 19 없음, −16.5 → 19.5 경고.
     * 3/4-20 · F34 는 선택표 한도가 없다(무제한 — 끝 −200 도 경고 없음)
     */
    @Test
    fun rollLength() {
        val over = ok(base.copy(head = "F233400", zFace = 0.0, zEnd = -75.0), listOf(RollWarning.ROLL_LENGTH_OVER))
        near(78.0, over.rolledLength!!, 1e-9, "전조 길이")
        assertEquals(73.0, over.maxRollLength)
        ok(base.copy(head = "F233400", zFace = 0.0, zEnd = -70.0))
        val one = RollInput(spec = un("1", 20), zFace = 0.0)
        assertEquals(24.0, ok(one.copy(zEnd = -20.0)).maxRollLength)
        ok(one.copy(zEnd = -20.01), listOf(RollWarning.ROLL_LENGTH_OVER))
        ok(base.copy(head = "F23", zFace = 0.0, zEnd = -16.0))
        ok(base.copy(head = "F23", zFace = 0.0, zEnd = -16.5), listOf(RollWarning.ROLL_LENGTH_OVER))
        val free = ok(base.copy(zFace = 0.0, zEnd = -200.0))
        assertNull(free.maxRollLength)
        // 끝면을 안 넣으면 시작 Z 부터: 1-20 · Z5 → −16.37 이면 21.37 + 4 = 25.37 > 24
        ok(RollInput(spec = un("1", 20), zEnd = -16.37), listOf(RollWarning.ROLL_LENGTH_OVER))
    }

    /** M16×2 · F34: Ø16 은 범위(12–30) 안, 피치 2 는 롤 목록(1–1.5) 밖 → ROLL_NOT_LISTED */
    @Test
    fun rollNotListed() {
        ok(RollInput(spec = m("M16", 2.0), head = "F34", zEnd = -16.37), listOf(RollWarning.ROLL_NOT_LISTED))
        // 회사 일곱 규격 · 모든 회사 머리 짝은 범위 안 · 롤 목록 안
        for (r in CompanyRolls.ALL) for (h in r.heads) {
            val spec = un(r.size, 20)
            ok(RollInput(spec = spec, head = h.model, zEnd = -16.37, zFace = 0.0))
        }
        // 회사 표 밖은 롤 목록에 피치가 있는 머리를 먼저: M20×2.5(Ø20) → F23(피치 ≤ 1.5) · F233400(≤ 2) · F34(≤ 1.5) 를 건너 F3(1.5–2.5)
        val m20 = ok(RollInput(spec = m("M20", 2.5), zEnd = -16.37))
        assertEquals("F3", m20.head.key)
        assertEquals(4.0, m20.openingMovement)
    }

    @Test
    fun stopsAndMissing() {
        stops(RollWarning.MISSING_INPUT, RollInput(zEnd = -16.37))
        stops(RollWarning.MISSING_INPUT, base.copy(zEnd = null))
        stops(RollWarning.BAD_INPUT, base.copy(ratio = 0.0))
        stops(RollWarning.BAD_INPUT, base.copy(dwell = -1.0))
        stops(RollWarning.BAD_INPUT, base.copy(rpm = 0.5))
        stops(RollWarning.BAD_INPUT, base.copy(zEnd = Double.NaN))
        stops(RollWarning.BAD_INPUT, base.copy(zEnd = -10_001.0))
        stops(RollWarning.BAD_INPUT, base.copy(rpm = 200_000.0))
        // 찍힐 F 0: 비율 0.0003 × 1.27 = 0.000381 → 0
        stops(RollWarning.BAD_INPUT, base.copy(ratio = 0.0003))
        // Z 순서: 시작 ≤ 끝 · 진입 < 시작 · 물러남 < 시작 · 끝면 > 시작 · 끝면 ≤ 끝
        stops(RollWarning.Z_ORDER, base.copy(zEnd = 5.0))
        stops(RollWarning.Z_ORDER, base.copy(zStart = 25.0))
        stops(RollWarning.Z_ORDER, base.copy(retractZ = 3.0))
        stops(RollWarning.Z_ORDER, base.copy(zFace = 6.0))
        stops(RollWarning.Z_ORDER, base.copy(zFace = -16.37))
        // 경계: 시작 = 진입 20 · 끝면 = 시작은 풀림
        ok(base.copy(zStart = 20.0))
        ok(base.copy(zFace = 5.0))
    }

    /** 사람이 안 넣었을 때의 값(자리표시) — 계산이 멈춰도 낸다 */
    @Test
    fun defaultsWithoutZ() {
        val d = RollHeadCalc.defaults(RollInput(spec = un("3/4", 20)))
        assertEquals(527L, d.rpm)
        assertEquals(RollRpmBasis.FETTE, d.rpmBasis)
        near(0.960630, d.ratio, 1e-6, "비율")
        assertEquals("F34", d.head?.key)
        val none = RollHeadCalc.defaults(RollInput())
        assertNull(none.rpm)
        assertEquals(0.95, none.ratio)
        assertNull(RollHeadCalc.defaults(RollInput(spec = un("3/4", 20), material = IsoGroup.N)).rpm)
    }
}
