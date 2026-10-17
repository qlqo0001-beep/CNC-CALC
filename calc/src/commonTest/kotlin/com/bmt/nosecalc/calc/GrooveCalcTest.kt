package com.bmt.nosecalc.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrooveCalcTest {
    private val base = GrooveInput(zStart = -10.0, width = 8.0, od = 40.0, bottom = 30.0, insert = 3.0, overlap = 0.5)

    private fun ok(r: GrooveResult): GrooveSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        assertEquals(emptyList(), r.warnings)
        return r.solution
    }

    /** 폭 8 홈을 3 인서트로, 겹침 0.5(피치 2.5): 오른쪽 벽부터 −13, −15.5, 마지막은 왼쪽 벽 −18 */
    @Test
    fun leftEdgeReferenceNumbers() {
        val s = ok(GrooveCalc.calc(base))
        assertEquals(listOf(-13.0, -15.5, -18.0), s.steps.map { it.z })
        assertTrue(s.steps.all { it.xDia == 30.0 })
        assertEquals(2.5, s.pitch, 1e-12)
        assertEquals(42.0, s.retractXDia, 1e-12)
        assertEquals(
            listOf(
                "G00 X42. Z-13.", "G01 X30.", "G00 X42.",
                "G00 Z-15.5", "G01 X30.", "G00 X42.",
                "G00 Z-18.", "G01 X30.", "G00 X42.",
                // 벽·바닥 정삭: 오른쪽 벽을 타고 내려가 바닥을 지나 왼쪽 벽을 타고 올라온다
                "G00 Z-13.", "G01 X30.", "G01 Z-18.", "G01 X42.",
            ),
            s.nc,
        )
        assertEquals(
            listOf(Pt.ofDia(-13.0, 42.0), Pt.ofDia(-13.0, 30.0), Pt.ofDia(-18.0, 30.0), Pt.ofDia(-18.0, 42.0)),
            listOf(s.finish.segs[0].start) + s.finish.segs.map { it.end },
        )
        assertEquals(
            listOf(Pt.ofDia(-10.0, 40.0), Pt.ofDia(-10.0, 30.0), Pt.ofDia(-18.0, 30.0), Pt.ofDia(-18.0, 40.0)),
            listOf(s.drawing.segs[0].start) + s.drawing.segs.map { it.end },
        )
    }

    /** 오른쪽 인선 기준이면 프로그램 Z 가 인서트 폭만큼 오른쪽: −10, −12.5, −15 */
    @Test
    fun rightEdgeReference() {
        val s = ok(GrooveCalc.calc(base.copy(ref = GrooveRef.RIGHT)))
        assertEquals(listOf(-10.0, -12.5, -15.0), s.steps.map { it.z })
        assertEquals(3, GrooveRef.LEFT.tip)
        assertEquals(4, GrooveRef.RIGHT.tip)
    }

    /**
     * 폭·겹침을 바꿔 가며: 첫 단은 오른쪽 벽, 마지막 단은 왼쪽 벽에 꼭 맞고, 날이 벽을 넘지 않으며,
     * 단 사이는 피치(마지막 간격만 짧을 수 있다 — G75 의 K 와 같은 방식).
     */
    @Test
    fun stepsFitBetweenWalls() {
        for (ref in GrooveRef.entries) for (t in listOf(2.0, 3.0, 4.0)) for (ov in listOf(0.0, 0.3, 1.0)) {
            var w = t
            while (w <= 6 * t) {
                val z0 = 7.0
                val s = ok(GrooveCalc.calc(GrooveInput(z0, w, 50.0, 44.0, t, ov, ref)))
                val off = if (ref == GrooveRef.LEFT) 0.0 else t
                val lefts = s.steps.map { it.z - off } // 날 왼쪽 끝
                val tag = "$ref T$t 겹침$ov W$w"
                assertEquals(z0 - t, lefts.first(), 1e-12, "$tag 첫 단 = 오른쪽 벽")
                assertEquals(z0 - w, lefts.last(), 1e-12, "$tag 마지막 단 = 왼쪽 벽")
                for (zl in lefts) assertTrue(zl >= z0 - w - 1e-12 && zl + t <= z0 + 1e-12, "$tag 벽 침범 $zl")
                val gaps = lefts.zipWithNext { a, b -> a - b }
                for (g in gaps.dropLast(1)) assertEquals(t - ov, g, 1e-9, tag)
                gaps.lastOrNull()?.let { assertTrue(it > 1e-9 && it <= t - ov + 1e-9, "$tag 마지막 간격 $it") }
                w += 0.37
            }
        }
    }

    /** 폭 = 인서트 폭이면 한 번만 */
    @Test
    fun grooveAsWideAsInsert() {
        val s = ok(GrooveCalc.calc(base.copy(width = 3.0)))
        assertEquals(listOf(-13.0), s.steps.map { it.z })
        assertEquals(listOf("G00 X42. Z-13.", "G01 X30.", "G00 X42.", "G01 X30.", "G01 X42."), s.nc)
    }

    /** 피치로 딱 나눠떨어지면 단을 하나 더 만들지 않는다 */
    @Test
    fun exactDivisionNoExtraStep() {
        val s = ok(GrooveCalc.calc(base.copy(width = 3.0 + 2.5 * 4)))
        assertEquals(5, s.steps.size)
    }

    @Test
    fun interferenceStops() {
        fun stops(expected: List<GrooveWarning>, i: GrooveInput) {
            val r = GrooveCalc.calc(i)
            assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다: $i")
            assertEquals(expected, r.warnings)
        }
        stops(listOf(GrooveWarning.TOOL_WIDER_THAN_GROOVE), base.copy(width = 2.9))
        stops(listOf(GrooveWarning.BOTTOM_NOT_BELOW_OD), base.copy(bottom = 40.0))
        stops(listOf(GrooveWarning.BOTTOM_NOT_BELOW_OD), base.copy(bottom = 41.0))
        stops(listOf(GrooveWarning.OVERLAP_TOO_BIG), base.copy(overlap = 3.0))
        // 여럿이면 다 알려 준다
        stops(
            listOf(GrooveWarning.TOOL_WIDER_THAN_GROOVE, GrooveWarning.BOTTOM_NOT_BELOW_OD, GrooveWarning.OVERLAP_TOO_BIG),
            base.copy(width = 2.0, bottom = 45.0, overlap = 3.5),
        )
        stops(listOf(GrooveWarning.MISSING_INPUT), GrooveInput())
        stops(listOf(GrooveWarning.MISSING_INPUT), base.copy(insert = null))
        for (bad in listOf(
            base.copy(width = 0.0), base.copy(insert = -3.0), base.copy(bottom = -1.0), base.copy(overlap = -0.1),
            base.copy(xClear = -1.0), base.copy(od = Double.NaN), base.copy(zStart = Double.POSITIVE_INFINITY),
        )) stops(listOf(GrooveWarning.BAD_INPUT), bad)
        // 단이 1000 을 넘으면 입력 실수로 본다
        stops(listOf(GrooveWarning.TOO_MANY_STEPS), base.copy(width = 3000.0))
        // 겹침이 인서트 폭에 아주 가까우면 단 수가 Int 를 넘는다 — 넘쳐 음수가 되어 빈 목록으로 죽던 것
        stops(listOf(GrooveWarning.TOO_MANY_STEPS), base.copy(overlap = 3.0 - 1e-11))
        // 크기 한도(±MAX_MM)
        stops(listOf(GrooveWarning.BAD_INPUT), base.copy(width = 999_999_999_999.0))
    }

    @Test
    fun defaultsForScreen() {
        val i = GrooveInput()
        assertEquals(GrooveRef.LEFT, i.ref)
        assertEquals(0.5, i.overlap)
        assertEquals(1.0, i.xClear)
    }
}
