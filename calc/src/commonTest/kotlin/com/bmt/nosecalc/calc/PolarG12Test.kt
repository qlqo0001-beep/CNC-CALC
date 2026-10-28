package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 턴밀 극좌표 다각가공(G12.1). NC 글을 이 파일에서 따로 읽어(구현의 Pt · Intersect · offset 을 쓰지 않음) 본다.
 * - 직접 보정(G40) 프로그램: 쓴 좌표 = 공구 중심.
 * - 기계 보정(G41) 프로그램: FANUC 보정을 흉내 낸다 — 블록마다 진행 왼쪽으로 r(원호는 R ∓ r), 접한 이음은 그대로, 안쪽 꼭짓점은 두 보정선의 교점,
 *   바깥 꼭짓점은 둔각이면 교점 · 예각이면 r 늘인 꺾은선. 시작 · 취소 이동은 다음 · 앞 원호와 한 직선이어야 하고(아니면 실패) 그 끝 · 처음 벡터는 원호에 직각 r.
 * 둘 다 ① 공구가 다 깎은 단면을 파고들지 않음(공구 중심에서 단면까지 ≥ r) ② 단면 둘레의 모든 점에 공구가 닿음(경로까지 ≤ r) 을 본다.
 * 허용 0.003 mm(NC 0.001 반올림 · R 지령 원호의 중심 다시 구하기).
 */
class PolarG12Test {

    // ================================================================ 손 계산 — 프로그램 글 통째

    /**
     * 외측 사각형 W40(중심에서 변 20), 코너 R0, 외경 50, 공구 D10(r 5), 여유 1, Z-5, F100, 회전 0.
     * - 진입 원 반지름 2r = 10, 시작 이동 최소 max(여유, r) = 5. 시작 원 반지름 = max(25 + 5 + 1, √(35² + 10²)) = √1325 = 36.4005.
     *   시작 이동 길이 = √(1325 − 100) − 30 = 5.
     * - 변 법선 90° · 180° · 270° · 0°. 각 변의 시작점 O = 35n + 10·left(n) — X 축에서 가장 가까운 것은 +X 변(15.9°): O = (35, 10).
     * - 진입 원호 중심 E + 10n = (30, 0), Q = (30, 10), E = 변 가운데 (20, 0). 시계로: (20,0) → (20,-20) → (-20,-20) → (-20,20) → (20,20) → (20,0).
     *   후퇴 원호 → (30, -10), 취소 → (35, -10).
     * - 직접 보정: Q′ = (30, 5), 진입 원호 반지름 5 → (25, 0), 변은 5 바깥, 꼭짓점마다 반지름 5 원호(G02), 후퇴 → (30, -5), 취소 → (35, -10).
     * - 꼭짓점 반경 20√2 = 28.3 > 25 → CORNERS_OUTSIDE_OD. 살 두께 25 − 20 = 5 ≤ 10.
     */
    @Test
    fun handOuterSquare() {
        val s = ok(
            PolarG12.calc(PolarInput(shape = PolarShape.SQUARE, od = 50.0, width = 40.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)),
            listOf(PolarWarning.CORNERS_OUTSIDE_OD),
        )
        assertEquals(
            listOf(
                "(R5 T0)", "G98", "G00 X72.801 C0.", "G00 Z1.", "G01 Z-5. F100.", "G12.1",
                "G03 X70. C10. R36.401 F100.",
                "G41 G01 X60.",
                "G03 X40. C0. R10.",
                "G01 C-20.", "G01 X-40.", "G01 C20.", "G01 X40.", "G01 C0.",
                "G03 X60. C-10. R10.",
                "G40 G01 X70.",
                "G13.1", "G00 Z1.",
            ),
            s.ncG41G42.lines,
        )
        assertEquals(
            listOf(
                "(G40 DIRECT R5 T0)", "G98", "G00 X72.801 C0.", "G00 Z1.", "G01 Z-5. F100.", "G12.1",
                "G03 X70. C10. R36.401 F100.",
                "G01 X60. C5.",
                "G03 X50. C0. R5.",
                "G01 C-20.", "G02 X40. C-25. R5.",
                "G01 X-40.", "G02 X-50. C-20. R5.",
                "G01 C20.", "G02 X-40. C25. R5.",
                "G01 X40.", "G02 X50. C20. R5.",
                "G01 C0.",
                "G03 X60. C-5. R5.",
                "G01 X70. C-10.",
                "G13.1", "G00 Z1.",
            ),
            s.ncG40.lines,
        )
        near(sqrt(1325.0), s.startX, 1e-9, "시작 X(반경)")
        near(10.0, s.leadR, 1e-12, "진입 원호")
        near(2 * 20 * sqrt(2.0), s.cornerDia, 1e-9, "꼭짓점 지름")
        near(40.0, s.flatLength, 1e-9, "변 길이")
        near(5.0, s.stockMax, 1e-12, "살 두께")
        assertEquals(Side.G41, s.side)
    }

    /**
     * 내측 사각형 W40, 코너 R6, 구멍 Ø20, 공구 D10, 여유 1.
     * - 시작 X = 10 − 5 − 1 = 4(구멍 안, 공구 끝이 구멍 벽에서 1). 진입 원 반지름 min(2r, a/2) = 10.
     * - 반시계로 돈다. 변마다 시작점 O = −10·left(n): +C 변(법선 90°)이면 (10, 0) — 시작점(4, 0)에서 가장 가깝다.
     *   Q = (a − 10)n − 10·left(n) = (10, 10), 원호 중심 (0, 10) → E = (0, 20). 모서리 R6 중심 (±14, ±14).
     * - 직접 보정: 모서리 원호 반지름 6 − 5 = 1, 변은 5 안쪽.
     * - 살: 구멍 둘레 45° 점 (7.07, 7.07) 에서 변까지 12.93 > 10 → STOCK_LEFT.
     */
    @Test
    fun handInnerSquare() {
        val s = ok(
            PolarG12.calc(
                PolarInput(inner = true, shape = PolarShape.SQUARE, od = 20.0, width = 40.0, cornerR = 6.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0),
            ),
            listOf(PolarWarning.STOCK_LEFT),
        )
        assertEquals(
            listOf(
                "(R5 T0)", "G98", "G00 X8. C0.", "G00 Z1.", "G01 Z-5. F100.", "G12.1",
                "G01 X20. C0. F100.",
                "G41 G01 C10.",
                "G03 X0. C20. R10.",
                "G01 X-28.", "G03 X-40. C14. R6.",
                "G01 C-14.", "G03 X-28. C-20. R6.",
                "G01 X28.", "G03 X40. C-14. R6.",
                "G01 C14.", "G03 X28. C20. R6.",
                "G01 X0.",
                "G03 X-20. C10. R10.",
                "G40 G01 C0.",
                "G13.1", "G00 Z1.",
            ),
            s.ncG41G42.lines,
        )
        assertEquals(
            listOf(
                "(G40 DIRECT R5 T0)", "G98", "G00 X8. C0.", "G00 Z1.", "G01 Z-5. F100.", "G12.1",
                "G01 X20. C0. F100.",
                "G01 X10. C10.",
                "G03 X0. C15. R5.",
                "G01 X-28.", "G03 X-30. C14. R1.",
                "G01 C-14.", "G03 X-28. C-15. R1.",
                "G01 X28.", "G03 X30. C-14. R1.",
                "G01 C14.", "G03 X28. C15. R1.",
                "G01 X0.",
                "G03 X-10. C10. R5.",
                "G01 X-20. C0.",
                "G13.1", "G00 Z1.",
            ),
            s.ncG40.lines,
        )
        near(4.0, s.startX, 1e-12, "시작 X")
        near(6 + (14 - 10 * cos(PI / 4)), s.stockMax, 1e-6, "살 두께(45° 방향)")
    }

    /**
     * 외측 2면폭 W30, 외경 48, 공구 D10, 여유 1. 평면 직선이 소재 + 여유 원(반지름 25)과 만나는 곳 ±20 에서 들어가 나온다.
     * 시작 원 반지름 max(30, √(30² + 30²)) = 42.426, 시작 이동 = √(1800 − 900) − 25 = 5. 변마다 O = 30n − 30d —
     * −C 변(진행 −X)의 O = (30, −30) 이 X 축에서 가깝다(−45°) → 그 변부터, 시계로 90° 돌아 +C 변.
     */
    @Test
    fun handTwoFlat() {
        val s = ok(PolarG12.calc(PolarInput(shape = PolarShape.TWO_FLAT, od = 48.0, width = 30.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)))
        assertEquals(
            listOf(
                "(R5 T0)", "G98", "G00 X84.853 C0.", "G00 Z1.", "G01 Z-5. F100.", "G12.1",
                "G02 X60. C-30. R42.426 F100.",
                "G41 G01 C-25.",
                "G03 X40. C-15. R10.",
                "G01 X-40.",
                "G03 X-60. C-25. R10.",
                "G40 G01 C-30.",
                "G02 C30. R42.426",
                "G41 G01 C25.",
                "G03 X-40. C15. R10.",
                "G01 X40.",
                "G03 X60. C25. R10.",
                "G40 G01 C30.",
                "G13.1", "G00 Z1.",
            ),
            s.ncG41G42.lines,
        )
        near(48.0, s.cornerDia, 1e-12, "2면폭 꼭짓점 지름 = 외경")
        near(2 * sqrt(24.0 * 24 - 15 * 15), s.flatLength, 1e-9, "현 길이")
        val sh = Shape(PolarShape.TWO_FLAT, 15.0, 0.0, 0.0, 24.0)
        checkProgram(s.ncG40, sh, inner = false, r = 5.0, rs = 24.0, clear = 1.0, xDia = true, machine = false, tag = "2면폭 직접")
        checkProgram(s.ncG41G42, sh, inner = false, r = 5.0, rs = 24.0, clear = 1.0, xDia = true, machine = true, tag = "2면폭 G41")
    }

    /**
     * 원본 07.jpg 의 프로그램(육각형 맞변 80 · 코너 R8, 시계로) 줄이 그대로 나온다. 앞 줄과 같은 축 값은 빠진다(원본은 흐리게 보여 줌):
     * `G01 X50.807 C-36.` `G02 X36.95 C-40. R8.` `G01 X-36.95 C-40.` `G02 X-50.807 C-36. R8.` `G01 X-87.757 C-4.`
     * `G02 X-87.757 C4. R8.` `G01 X-50.807 C36.` `G02 X-36.95 C40. R8.` `G01 X36.95 C40.`
     * 손 계산: 중심에서 모서리 원 중심 (40 − 8)/cos30° = 36.950, 접점은 변 가운데에서 32·tan30° = 18.475 → X 36.95(직경), 경사 변 위 접점
     * (C −40 + 18.475·… ) = (25.403, −36) → X 50.807. 원본은 처음 꼭짓점(X92.376 C0.)을 날카롭게 남겼지만 이 앱은 여섯 모서리 다 R8.
     */
    @Test
    fun originalHexagonLines() {
        val s = ok(PolarG12.calc(PolarInput(od = 100.0, width = 80.0, cornerR = 8.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)))
        val want = listOf(
            "G01 X50.807 C-36.", "G02 X36.95 C-40. R8.", "G01 X-36.95", "G02 X-50.807 C-36. R8.", "G01 X-87.757 C-4.",
            "G02 C4. R8.", "G01 X-50.807 C36.", "G02 X-36.95 C40. R8.", "G01 X36.95",
        )
        val lines = s.ncG41G42.lines
        val at = lines.windowed(want.size).indexOf(want)
        assertTrue(at > 0, "원본 07 의 줄이 없다:\n${lines.joinToString("\n")}")
        // 원본 12.jpg 기본값(외경 80 · W70 · R3): 모서리 R 끝은 반경 36.950 + 3 = 39.950 < 40 이라 소재 안
        val d = ok(PolarG12.calc(PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)))
        near(2 * (32 / cos(PI / 6) + 3), d.cornerDia, 1e-9, "꼭짓점 지름")
        near(2 * 32 * tan(PI / 6), d.flatLength, 1e-9, "변 길이")
    }

    /** 육각형 W70 · R3: 단면의 변은 중심에서 35, 모서리 원 반지름 3 · 중심 반경 32/cos30°, 접점은 변 가운데에서 32·tan30° */
    @Test
    fun hexagonWidthAndCornerTangents() {
        for (rot in listOf(0.0, 12.5, -40.0)) {
            val s = ok(PolarG12.calc(PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, rotationDeg = rot, toolDia = 10.0, depthZ = -5.0, feed = 100.0)))
            val segs = s.shape.segs
            assertEquals(13, segs.size, "변 가운데에서 시작 · 끝: 직선 7 + 원호 6")
            for (g in segs) when (g) {
                is Line -> {
                    val a = v(g.start)
                    val b = v(g.end)
                    val dir = (b - a).unit()
                    near(35.0, abs(a cross dir), 1e-9, "변까지 거리")
                    // 접점은 변 가운데(중심에서 내린 발)에서 32·tan30° 안
                    val foot = a - dir * (a dot dir)
                    assertTrue((a - foot).len <= 32 * tan(PI / 6) + 1e-9 && (b - foot).len <= 32 * tan(PI / 6) + 1e-9)
                }
                is Arc -> {
                    near(3.0, g.radius, 1e-9, "모서리 R")
                    near(32 / cos(PI / 6), v(g.center).len, 1e-9, "모서리 원 중심")
                    near(60.0, g.sweepDeg, 1e-9, "모서리 원호 각")
                    assertTrue(g.cw, "외측은 시계(G02)")
                }
            }
        }
    }

    // ================================================================ 회전 · X 반경 모드

    /** 회전 θ 의 단면을 −θ 돌리면 회전 0 의 단면 위에 있다(점마다 거리 0) */
    @Test
    fun rotationRotatesShape() {
        for (shape in listOf(PolarShape.TRIANGLE, PolarShape.SQUARE, PolarShape.PENTAGON, PolarShape.HEXAGON, PolarShape.OCTAGON)) {
            for (rot in listOf(7.5, 30.0, -100.0, 400.0)) {
                for (inner in listOf(false, true)) {
                    val i = PolarInput(
                        inner = inner, shape = shape, od = if (inner) 20.0 else 90.0, width = 60.0, cornerR = 4.0, rotationDeg = rot,
                        toolDia = 8.0, depthZ = -3.0, feed = 200.0,
                    )
                    val s = ok(PolarG12.calc(i), null)
                    val ref = Shape(shape, 30.0, 4.0, 0.0, 0.0)
                    val back = -rot * PI / 180
                    for (p in sample(s.shape.segs.map { toM(it) }, 0.1)) {
                        near(0.0, ref.sd(p.rot(back)), 1e-9, "$shape 회전 $rot")
                    }
                    // 회전한 단면으로 NC 를 따로 확인
                    val sh = Shape(shape, 30.0, 4.0, rot, 0.0)
                    checkProgram(s.ncG40, sh, inner, 4.0, i.od!! / 2, 1.0, true, machine = false, tag = "$shape $rot $inner 직접")
                    checkProgram(s.ncG41G42, sh, inner, 4.0, i.od!! / 2, 1.0, true, machine = true, tag = "$shape $rot $inner G41")
                }
            }
        }
    }

    /** X 반경 모드: G12.1 안 X 가 직경 모드의 절반, G12.1 앞 X(선반 X 축)는 그대로 직경. C · R 은 같다 */
    @Test
    fun xRadiusMode() {
        val base = PolarInput(od = 90.0, width = 70.0, cornerR = 3.0, rotationDeg = 10.0, toolDia = 12.0, depthZ = -4.0, feed = 150.0)
        val dia = ok(PolarG12.calc(base))
        val rad = ok(PolarG12.calc(base.copy(xDiameter = false)))
        for ((pd, pr) in listOf(dia.ncG41G42 to rad.ncG41G42, dia.ncG40 to rad.ncG40)) {
            assertEquals(pd.blocks.size, pr.blocks.size)
            for ((bd, br) in pd.blocks.zip(pr.blocks)) {
                if (bd.polar && bd.text != "G12.1") near(bd.x!! / 2, br.x!!, 0.0011, "X 반경: ${br.text}")
                else assertEquals(bd.text, br.text)
                assertEquals(bd.c, br.c)
                assertEquals(word(bd.text, 'R'), word(br.text, 'R'))
            }
            val sh = Shape(PolarShape.HEXAGON, 35.0, 3.0, 10.0, 0.0)
            checkProgram(pr, sh, false, 6.0, 45.0, 1.0, xDia = false, machine = pr === rad.ncG41G42, tag = "반경 모드")
        }
    }

    // ================================================================ 경로 — 모양 · 크기 두루

    /**
     * 다각형 5가지 × 외측/내측 × 코너 R(0 · r 의 절반 · r · 2r) × 회전 × 공구경 × X 직경/반경.
     * 두 프로그램 모두 파고듦 없음 · 둘레 전부 닿음 · 시작점 안전 · 블록 규칙.
     */
    @Test
    fun toolCenterIsOffsetByRadius() {
        var n = 0
        for (shape in listOf(PolarShape.TRIANGLE, PolarShape.SQUARE, PolarShape.PENTAGON, PolarShape.HEXAGON, PolarShape.OCTAGON)) {
            for (inner in listOf(false, true)) for (d in listOf(2.0, 10.0)) for (rf in listOf(0.0, 0.5, 1.0, 2.0)) {
                for (rot in listOf(0.0, 17.3, -45.0)) for (xDia in listOf(true, false)) {
                    val r = d / 2
                    val rc = rf * r
                    val a = 25.0
                    val od = if (inner) 2 * (a * 0.6) else 2 * (a + 3.0)
                    val i = PolarInput(inner, shape, od, 2 * a, rc, rot, d, -6.0, 120.0, null, xDia)
                    val s = ok(PolarG12.calc(i), null)
                    val sh = Shape(shape, a, rc, rot, 0.0)
                    checkProgram(s.ncG40, sh, inner, r, od / 2, 1.0, xDia, machine = false, tag = "$shape in=$inner D$d R$rc rot$rot dia=$xDia 직접")
                    checkProgram(s.ncG41G42, sh, inner, r, od / 2, 1.0, xDia, machine = true, tag = "$shape in=$inner D$d R$rc rot$rot dia=$xDia G41")
                    n++
                }
            }
        }
        assertEquals(5 * 2 * 2 * 4 * 3 * 2, n)
    }

    /** 무작위 모양 · 크기(씨앗 고정): 멈추지 않으면 늘 두 프로그램이 위 조건을 지킨다. 멈춤은 그 까닭이 입력과 맞아야 한다 */
    @Test
    fun randomShapes() {
        val rnd = Random(20261028)
        var solved = 0
        repeat(400) { k ->
            val shape = PolarShape.entries[rnd.nextInt(PolarShape.entries.size)]
            val inner = shape != PolarShape.TWO_FLAT && rnd.nextBoolean()
            val a = 1.0 + rnd.nextDouble() * 80
            val r = 0.2 + rnd.nextDouble() * (if (inner) a * 0.6 else 15.0)
            val rc = if (rnd.nextInt(4) == 0) 0.0 else rnd.nextDouble() * a * 0.9
            val clear = rnd.nextDouble() * 3
            val rs = if (inner) rnd.nextDouble() * a * 1.05 else a * (1.0 + rnd.nextDouble() * 0.8)
            val rot = rnd.nextDouble() * 720 - 360
            val xDia = rnd.nextBoolean()
            val i = PolarInput(inner, shape, 2 * rs, 2 * a, rc, rot, 2 * r, -1.0 - rnd.nextDouble() * 10, 50.0 + rnd.nextDouble() * 500, clear, xDia)
            val res = PolarG12.calc(i)
            val sol = res.solution
            if (sol == null) {
                val w = res.warnings.single()
                val expect = when {
                    !inner && a >= rs -> PolarWarning.W_GE_OD
                    inner && rs > a -> PolarWarning.HOLE_GT_W
                    shape != PolarShape.TWO_FLAT && rc >= a -> PolarWarning.CORNER_R_TOO_BIG
                    inner && a <= 2 * r -> PolarWarning.TOOL_TOO_BIG
                    inner && rs < r + clear -> PolarWarning.TOOL_GT_HOLE
                    else -> fail("#$k 까닭 없이 멈춤: $w $i")
                }
                assertEquals(expect, w, "#$k $i")
                return@repeat
            }
            solved++
            val sh = Shape(shape, a, if (shape == PolarShape.TWO_FLAT) 0.0 else rc, rot, rs)
            checkProgram(sol.ncG40, sh, inner, r, rs, clear, xDia, machine = false, tag = "#$k 직접 $i")
            checkProgram(sol.ncG41G42, sh, inner, r, rs, clear, xDia, machine = true, tag = "#$k G41 $i")
            // 경고: 살 · 모서리
            val stock = if (inner) innerStock(sh, rs) else rs - a
            assertEquals(stock > 2 * r + 1e-6, PolarWarning.STOCK_LEFT in res.warnings, "#$k 살 $stock 공구 ${2 * r}")
            if (inner) assertEquals(rc < r - 1e-6, PolarWarning.INNER_CORNER_LT_TOOL in res.warnings, "#$k 내측 R")
        }
        assertTrue(solved > 150, "푼 사례가 너무 적다: $solved")
    }

    // ================================================================ 경고 · 멈춤

    @Test
    fun stops() {
        val base = PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)
        stop(PolarWarning.MISSING_INPUT, PolarInput())
        stop(PolarWarning.MISSING_INPUT, base.copy(feed = null))
        stop(PolarWarning.MISSING_INPUT, base.copy(depthZ = null))
        stop(PolarWarning.MISSING_INPUT, base.copy(toolDia = null))
        stop(PolarWarning.BAD_INPUT, base.copy(depthZ = 0.0))
        stop(PolarWarning.BAD_INPUT, base.copy(depthZ = 2.0))
        stop(PolarWarning.BAD_INPUT, base.copy(cornerR = -1.0))
        stop(PolarWarning.BAD_INPUT, base.copy(toolDia = 0.0))
        stop(PolarWarning.BAD_INPUT, base.copy(feed = 0.0))
        stop(PolarWarning.BAD_INPUT, base.copy(feed = 1e6))
        stop(PolarWarning.BAD_INPUT, base.copy(clear = -0.1))
        stop(PolarWarning.BAD_INPUT, base.copy(od = Double.NaN))
        stop(PolarWarning.BAD_INPUT, base.copy(width = 1e5))
        stop(PolarWarning.BAD_INPUT, base.copy(inner = true, shape = PolarShape.TWO_FLAT, od = 20.0))
        stop(PolarWarning.W_GE_OD, base.copy(width = 80.0))
        stop(PolarWarning.W_GE_OD, base.copy(shape = PolarShape.TWO_FLAT, width = 90.0))
        stop(PolarWarning.CORNER_R_TOO_BIG, base.copy(cornerR = 35.0))
        val inner = base.copy(inner = true, od = 30.0)
        stop(PolarWarning.HOLE_GT_W, inner.copy(od = 70.1))
        stop(PolarWarning.TOOL_TOO_BIG, inner.copy(toolDia = 35.0))
        stop(PolarWarning.TOOL_GT_HOLE, inner.copy(od = 11.9))
        ok(PolarG12.calc(inner.copy(od = 12.0)), listOf(PolarWarning.INNER_CORNER_LT_TOOL, PolarWarning.STOCK_LEFT)) // r + 여유 = 6 꼭
        // 빈 코너 R · 회전 · 여유 = 0 · 0 · 1
        val blank = ok(PolarG12.calc(base.copy(cornerR = null, rotationDeg = null, clear = null)), null)
        val zero = ok(PolarG12.calc(base.copy(cornerR = 0.0, rotationDeg = 0.0, clear = 1.0)), null)
        assertEquals(zero.ncG41G42.lines, blank.ncG41G42.lines)
    }

    /**
     * 키패드로 칠 수 있는 아주 작은 코너 R(0.00000000001 — 12자리)에서 죽던 것(3단계 최종 점검, Arc → Line 형변환 예외).
     * 0.001 아래 R 은 NC 로는 꼭짓점과 같다 — 두 프로그램 글이 R0 과 같아야 한다. 모서리 R 이 맞변 절반에 아주 가까운 것도(변 길이 ≈ 0) 죽지 않는다
     */
    @Test
    fun tinyCornerR() {
        for (shape in listOf(PolarShape.TRIANGLE, PolarShape.SQUARE, PolarShape.HEXAGON, PolarShape.OCTAGON)) {
            val base = PolarInput(shape = shape, od = 80.0, width = 70.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)
            val zero = assertNotNull(PolarG12.calc(base.copy(cornerR = 0.0)).solution, "$shape R0")
            for (rc in listOf(1e-11, 1e-9, 1e-7, 1e-5)) {
                val s = assertNotNull(PolarG12.calc(base.copy(cornerR = rc)).solution, "$shape R$rc")
                assertEquals(zero.ncG41G42.lines, s.ncG41G42.lines, "$shape R$rc 기계 보정")
                assertEquals(zero.ncG40.lines, s.ncG40.lines, "$shape R$rc 직접 보정")
            }
            for (rc in listOf(34.9999, 34.9999999, 34.9999999999)) {
                for (inner in listOf(false, true)) {
                    val s = assertNotNull(PolarG12.calc(base.copy(inner = inner, od = if (inner) 20.0 else 80.0, cornerR = rc)).solution, "$shape 내측 $inner R$rc")
                    for (p in listOf(s.ncG41G42, s.ncG40)) p.blocks.forEach { b -> assertTrue(listOfNotNull(b.x, b.c, b.z).all { it.isFinite() }, "$shape R$rc ${b.text}") }
                }
            }
        }
    }

    @Test
    fun warnings() {
        // 외측 육각형 W70 · 외경 80 · R0: 꼭짓점 반경 40.415 > 40, 살 5 ≤ 공구경 10
        ok(PolarG12.calc(PolarInput(od = 80.0, width = 70.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)), listOf(PolarWarning.CORNERS_OUTSIDE_OD))
        // R3 이면 39.950 — 소재 안
        ok(PolarG12.calc(PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0)))
        // 공구경 4 < 살 5
        ok(
            PolarG12.calc(PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, toolDia = 4.0, depthZ = -5.0, feed = 100.0)),
            listOf(PolarWarning.STOCK_LEFT),
        )
        // 내측 코너 R2 < r 3: G41 프로그램은 R 없이(날카롭게), 직접 보정은 두 보정선의 교점.
        // 구멍 Ø44: 꼭짓점 방향 살 = 2 + (23 − 22·cos30°) = 5.95 < 공구경 6
        val s = ok(
            PolarG12.calc(PolarInput(inner = true, od = 44.0, width = 50.0, cornerR = 2.0, toolDia = 6.0, depthZ = -5.0, feed = 100.0)),
            listOf(PolarWarning.INNER_CORNER_LT_TOOL),
        )
        near(2 + (23 - 22 * cos(PI / 6)), s.stockMax, 1e-9, "내측 살")
        val arcs = s.ncG41G42.lines.filter { it.startsWith("G02") || it.startsWith("G03") }
        assertTrue(arcs.all { word(it, 'R') == 6.0 }, "모서리 R2 원호가 없어야(진입 · 후퇴 R6 만): $arcs")
        // 내측 R = r 꼭: 경고 없음(정확), 원호도 없다(보정 원호 반지름 0)
        val e = ok(PolarG12.calc(PolarInput(inner = true, od = 44.0, width = 50.0, cornerR = 3.0, toolDia = 6.0, depthZ = -5.0, feed = 100.0)))
        assertTrue(e.ncG40.lines.none { word(it, 'R') == 0.0 })
    }

    // ================================================================ 블록 규칙(FANUC G12.1)

    /**
     * B-64604EN-1/01 3.1 · B-61394E/08 4.4: G12.1 · G13.1 은 혼자 한 줄, 그 앞에 X · C0 자리(가상축 0 = 회전각 0), 모드 안에는 G00 이 없고,
     * G41/G42 시작 · G40 취소는 모드 안의 G01, G13.1 앞에 취소, 분당 이송 G98 이 이송보다 먼저.
     */
    @Test
    fun blockRules() {
        for (i in listOf(
            PolarInput(od = 80.0, width = 70.0, cornerR = 3.0, toolDia = 10.0, depthZ = -5.0, feed = 100.0),
            PolarInput(inner = true, shape = PolarShape.PENTAGON, od = 20.0, width = 50.0, cornerR = 1.0, toolDia = 8.0, depthZ = -5.0, feed = 100.0),
            PolarInput(shape = PolarShape.TWO_FLAT, od = 50.0, width = 40.0, rotationDeg = 33.0, toolDia = 8.0, depthZ = -5.0, feed = 100.0),
        )) {
            val s = ok(PolarG12.calc(i), null)
            for ((prog, g41) in listOf(s.ncG41G42 to true, s.ncG40 to false)) {
                val l = prog.lines
                assertEquals(1, l.count { it == "G12.1" })
                assertEquals(1, l.count { it == "G13.1" })
                val on = l.indexOf("G12.1")
                val off = l.indexOf("G13.1")
                assertTrue(l.indexOf("G98") in 0 until on)
                assertTrue(Regex("""G00 X[\d.]+ C0\.""").matches(l[on - 3]), "G12.1 앞 X · C0: ${l[on - 3]}")
                val inside = l.subList(on + 1, off)
                assertTrue(inside.none { it.startsWith("G00") }, "모드 안 G00")
                assertTrue(inside.first().contains(" X") && inside.first().contains(" C") && inside.first().contains(" F"), inside.first())
                val starts = inside.filter { "G41" in it || "G42" in it }
                val cancels = inside.filter { "G40" in it }
                if (g41) {
                    assertTrue(starts.isNotEmpty() && starts.all { it.startsWith("G41 G01 ") })
                    assertEquals(starts.size, cancels.size)
                    assertTrue(cancels.all { it.startsWith("G40 G01 ") })
                    // 보정은 모드 안에서 시작해 모드 안에서 끝난다
                    var comp = false
                    for (t in inside) {
                        if ("G41" in t) { assertTrue(!comp); comp = true }
                        if ("G40" in t) { assertTrue(comp); comp = false }
                    }
                    assertTrue(!comp, "G13.1 앞에 G40")
                    assertTrue(l.subList(0, on).none { "G41" in it })
                } else {
                    assertTrue(starts.isEmpty() && cancels.isEmpty())
                }
                // 첫 시작 이동까지(G40 위치 잡기)는 두 프로그램이 같은 줄
                val su = s.ncG41G42.lines.indexOfFirst { "G41" in it }
                assertEquals(s.ncG41G42.lines.subList(1, su), s.ncG40.lines.subList(1, su))
                // 줄과 블록이 맞고, 공구 경로가 이어진다(Contour 가 끊기면 예외)
                assertEquals(l.size, prog.blocks.size)
                prog.toolPath
                assertTrue(prog.blocks.subList(on + 1, off).all { it.polar } && !prog.blocks[off].polar && prog.blocks[on].polar)
            }
        }
    }

    // ================================================================ 독립 검사 도구

    /** NC 를 읽어 공구 중심 경로(직접 = 그대로, 기계 = FANUC 보정 흉내)를 만들고 단면 · 소재와 견준다 */
    private fun checkProgram(
        prog: PolarProgram, sh: Shape, inner: Boolean, r: Double, rs: Double, clear: Double, xDia: Boolean, machine: Boolean, tag: String,
    ) {
        val tol = 0.003
        val parsed = parse(prog.lines, xDia, tag)
        val path = if (machine) emulate(parsed, r, tag) else parsed.flatMap { it.moves }.map { it.m }
        // 시작점: X 축 위, 외측은 소재 + 여유 밖, 내측은 구멍 − 여유 안
        val s0 = parsed.first().start
        near(0.0, s0.c, 1e-9, "$tag 시작 C")
        if (inner) assertTrue(s0.len + r <= rs - clear + tol, "$tag 시작점이 구멍 안이 아님 ${s0.len}")
        else assertTrue(s0.len - r >= rs + clear - tol, "$tag 시작점이 소재 밖이 아님 ${s0.len}")
        // 보정 전 G40 이동(외측 · 2면폭): 소재 밖. 직접 보정 프로그램은 G41 이 없어 가를 수 없다 — 같은 줄인 것은 blockRules 가 본다
        if (!inner && machine) for (b in parsed) if (b.kind == Kind.PRE) for (p in sample(b.moves.map { it.m }, 0.05)) {
            assertTrue(p.len - r >= rs + clear - tol, "$tag G40 이동이 소재에 닿음 $p")
        }
        // ① 파고듦 없음. 외측 단면 = 도형 ∩ 소재 원(꼭짓점이 소재 밖이면 그 너머는 공기 — CORNERS_OUTSIDE_OD).
        //    두 볼록 집합의 교집합까지 거리 ≥ 각 거리의 큰 쪽이라, 큰 쪽 ≥ r 이면 파고들지 않는다
        val step = max(0.01, sh.size / 800)
        for (p in sample(path, step)) {
            val dd = if (inner) sh.sd(p) else max(sh.sd(p), p.len - rs)
            if (inner) assertTrue(-dd >= r - tol, "$tag 벽을 파고듦: 중심 $p 거리 ${-dd} < r $r")
            else assertTrue(dd >= r - tol, "$tag 단면을 파고듦: 중심 $p 거리 $dd < r $r")
        }
        // ② 둘레 전부 닿음
        for (q in sh.targets(inner, r)) {
            val dmin = path.minOf { dist(q, it) }
            assertTrue(dmin <= r + tol, "$tag 덜 깎음: 둘레 $q 에서 경로까지 $dmin > r $r")
        }
    }

    private enum class Kind { PRE, START, COMP, CANCEL }

    private class PMove(val m: M, val text: String)

    /** 보정 단계별 묶음. START · CANCEL 은 한 이동, COMP 는 보정 모드의 이동들 */
    private class Part(val kind: Kind, val moves: List<PMove>) {
        val start get() = moves.first().m.s
    }

    /** 극좌표 모드 안 이동을 읽는다. X 는 xDia 면 반으로. R 지령 원호는 작은 쪽(≤ 180°) */
    private fun parse(lines: List<String>, xDia: Boolean, tag: String): List<Part> {
        val on = lines.indexOf("G12.1")
        val off = lines.indexOf("G13.1")
        val lathe = lines[on - 3]
        var cur = V(word(lathe, 'X')!! / 2, 0.0)
        val parts = mutableListOf<Part>()
        var buf = mutableListOf<PMove>()
        var kind = Kind.PRE
        fun flush(next: Kind) {
            if (buf.isNotEmpty()) parts += Part(kind, buf)
            buf = mutableListOf()
            kind = next
        }
        for (t in lines.subList(on + 1, off)) {
            val x = word(t, 'X')?.let { if (xDia) it / 2 else it } ?: cur.x
            val c = word(t, 'C') ?: cur.c
            val e = V(x, c)
            val m: M = when {
                t.startsWith("G02") || t.startsWith("G03") -> arcR(cur, e, word(t, 'R')!!, t.startsWith("G03"), "$tag $t")
                t.contains("G01") -> ML(cur, e)
                else -> fail("$tag 모르는 줄 $t")
            }
            when {
                "G41" in t -> { flush(Kind.START); buf += PMove(m, t); flush(Kind.COMP) }
                "G40" in t -> { flush(Kind.CANCEL); buf += PMove(m, t); flush(Kind.PRE) }
                else -> buf += PMove(m, t)
            }
            cur = e
        }
        flush(Kind.PRE)
        return parts
    }

    /** FANUC 보정 흉내(이 파일의 식). 시작 · 취소는 이웃 원호와 한 직선이어야 한다 */
    private fun emulate(parts: List<Part>, r: Double, tag: String): List<M> {
        val out = mutableListOf<M>()
        var k = 0
        while (k < parts.size) {
            val p = parts[k]
            if (p.kind == Kind.PRE) { out += p.moves.map { it.m }; k++; continue }
            assertEquals(Kind.START, p.kind, tag)
            val su = p.moves.single().m
            val comp = parts[k + 1].moves.map { it.m }
            val cancel = parts[k + 2].moves.single().m
            assertEquals(Kind.CANCEL, parts[k + 2].kind, tag)
            // 한 직선(반올림 허용 0.2°)
            assertTrue(angle(dirOf(su), tanS(comp.first())) < 0.0035, "$tag 시작 이동이 진입 원호와 한 직선이 아님")
            assertTrue(angle(tanE(comp.last()), dirOf(cancel)) < 0.0035, "$tag 취소 이동이 후퇴 원호와 한 직선이 아님")
            val off = comp.map { offsetM(it, r) }.toMutableList()
            val extra = List(comp.size) { mutableListOf<M>() }
            for (j in 0 until comp.size - 1) {
                val ta = tanE(comp[j])
                val tb = tanS(comp[j + 1])
                val turn = atan2(ta cross tb, ta dot tb)
                if (abs(turn) < 0.0175) {
                    if ((off[j].e - off[j + 1].s).len > 1e-12) extra[j] += ML(off[j].e, off[j + 1].s)
                    continue
                }
                val a = off[j] as? ML ?: fail("$tag 원호에서 꺾임")
                val b = off[j + 1] as? ML ?: fail("$tag 원호로 꺾임")
                if (turn > 0 || (ta dot tb) >= 0) {
                    val x = meet(a.s, a.e - a.s, b.s, b.e - b.s)
                    off[j] = ML(a.s, x)
                    off[j + 1] = ML(x, b.e)
                } else {
                    val p1 = a.e + ta * r
                    val p2 = b.s - tb * r
                    extra[j] += listOf(ML(a.e, p1), ML(p1, p2), ML(p2, b.s))
                }
            }
            out += ML(su.s, off.first().s)
            for (j in comp.indices) { out += off[j]; out += extra[j] }
            out += ML(off.last().e, cancel.e)
            k += 3
        }
        return out
    }

    // ---------------------------------------------------------------- 기하(이 파일만의 것)

    private data class V(val x: Double, val c: Double) {
        operator fun plus(o: V) = V(x + o.x, c + o.c)
        operator fun minus(o: V) = V(x - o.x, c - o.c)
        operator fun times(k: Double) = V(x * k, c * k)
        infix fun dot(o: V) = x * o.x + c * o.c

        /** > 0 이면 o 가 반시계 쪽(X 오른쪽 · C 위) */
        infix fun cross(o: V) = x * o.c - c * o.x
        val len get() = sqrt(x * x + c * c)
        fun unit() = this * (1 / len)
        fun left() = V(-c, x)
        fun rot(t: Double) = V(x * cos(t) - c * sin(t), x * sin(t) + c * cos(t))
    }

    private sealed interface M { val s: V; val e: V }
    private data class ML(override val s: V, override val e: V) : M
    private data class MA(override val s: V, override val e: V, val ctr: V, val ccw: Boolean) : M {
        val rad get() = (s - ctr).len

        /** 진행 방향으로 돈 각 [0, 2π) */
        val sweep: Double
            get() {
                val a = atan2((e - ctr).c, (e - ctr).x) - atan2((s - ctr).c, (s - ctr).x)
                val d = if (ccw) a else -a
                return ((d % (2 * PI)) + 2 * PI) % (2 * PI)
            }
    }

    /** 구현의 Pt(z = −C, x = X) → (X, C) */
    private fun v(p: Pt) = V(p.x, -p.z)

    private fun toM(s: Seg): M = when (s) {
        is Line -> ML(v(s.start), v(s.end))
        is Arc -> MA(v(s.start), v(s.end), v(s.center), !s.cw)
    }

    private fun arcR(s: V, e: V, rr: Double, ccw: Boolean, tag: String): MA {
        val ch = e - s
        val h2 = rr * rr - ch.dot(ch) / 4
        assertTrue(h2 > -1e-6, "$tag R 이 현의 절반보다 작다")
        val mid = (s + e) * 0.5
        val perp = ch.unit().left() * sqrt(max(0.0, h2))
        return MA(s, e, if (ccw) mid + perp else mid - perp, ccw)
    }

    private fun dirOf(m: M) = (m.e - m.s).unit()
    private fun tanS(m: M): V = when (m) {
        is ML -> dirOf(m)
        is MA -> (m.s - m.ctr).unit().left().let { if (m.ccw) it else it * -1.0 }
    }
    private fun tanE(m: M): V = when (m) {
        is ML -> dirOf(m)
        is MA -> (m.e - m.ctr).unit().left().let { if (m.ccw) it else it * -1.0 }
    }
    private fun angle(a: V, b: V) = abs(atan2(a cross b, a dot b))

    /** 진행 왼쪽으로 r */
    private fun offsetM(m: M, r: Double): M = when (m) {
        is ML -> { val n = dirOf(m).left() * r; ML(m.s + n, m.e + n) }
        is MA -> {
            val rr = m.rad + if (m.ccw) -r else r
            assertTrue(rr > 1e-9, "보정 원호 반지름 ≤ 0")
            val k = rr / m.rad
            MA(m.ctr + (m.s - m.ctr) * k, m.ctr + (m.e - m.ctr) * k, m.ctr, m.ccw)
        }
    }

    private fun meet(p1: V, d1: V, p2: V, d2: V): V {
        val den = d1 cross d2
        assertTrue(abs(den) > 1e-12, "평행")
        return p1 + d1 * (((p2 - p1) cross d2) / den)
    }

    private fun sample(ms: List<M>, step: Double): List<V> = ms.flatMap { m ->
        when (m) {
            is ML -> { val k = max(1, ceil((m.e - m.s).len / step).toInt()); (0..k).map { m.s + (m.e - m.s) * (it.toDouble() / k) } }
            is MA -> {
                val sw = m.sweep
                val k = max(1, ceil(sw * m.rad / step).toInt())
                val a0 = atan2((m.s - m.ctr).c, (m.s - m.ctr).x)
                (0..k).map { val t = a0 + (if (m.ccw) 1 else -1) * sw * it / k; m.ctr + V(cos(t), sin(t)) * m.rad }
            }
        }
    }

    private fun dist(q: V, m: M): Double = when (m) {
        is ML -> {
            val d = m.e - m.s
            val l2 = d dot d
            if (l2 < 1e-24) (q - m.s).len else {
                val t = (((q - m.s) dot d) / l2).coerceIn(0.0, 1.0)
                (q - (m.s + d * t)).len
            }
        }
        is MA -> {
            val v = q - m.ctr
            val a0 = atan2((m.s - m.ctr).c, (m.s - m.ctr).x)
            val aq = atan2(v.c, v.x)
            val rel = (((if (m.ccw) aq - a0 else a0 - aq) % (2 * PI)) + 2 * PI) % (2 * PI)
            if (rel <= m.sweep) abs(v.len - m.rad) else min((q - m.s).len, (q - m.e).len)
        }
    }

    /** 다 깎은 단면(정다각형 + 모서리 R, 또는 2면폭 = 외경 원 ∩ 두 평면 사이). sd = 단면 밖 +, 안 − 거리 */
    private class Shape(val shape: PolarShape, val a: Double, val rc: Double, val rotDeg: Double, val rs: Double) {
        val n = shape.sides
        val size = max(a, rs)
        private val rot = rotDeg * PI / 180
        private fun nrm(k: Int): V { val t = PI / 2 + rot + 2 * PI * k / n; return V(cos(t), sin(t)) }
        private val ap = a - rc
        private val core = (0 until n).map { k -> val t = PI / 2 + rot + 2 * PI * k / n + PI / n; V(cos(t), sin(t)) * (ap / cos(PI / n)) }

        fun sd(p: V): Double {
            if (shape == PolarShape.TWO_FLAT) {
                val n0 = nrm(0)
                val inside = p.len <= rs && abs(p dot n0) <= a
                if (inside) return -min(rs - p.len, a - abs(p dot n0))
                val cands = mutableListOf<V>()
                val onCircle = p * (rs / p.len)
                if (abs(onCircle dot n0) <= a) cands += onCircle
                val t = n0.left()
                for (sgn in listOf(1.0, -1.0)) {
                    val foot = n0 * (a * sgn) + t * (p dot t)
                    if (foot.len <= rs) cands += foot
                    val half = sqrt(rs * rs - a * a)
                    cands += n0 * (a * sgn) + t * half
                    cands += n0 * (a * sgn) - t * half
                }
                return cands.minOf { (p - it).len }
            }
            val slack = (0 until n).minOf { ap - (p dot nrm(it)) }
            if (slack >= 0) return -(rc + slack)
            val dCore = (0 until n).minOf { k -> segDist(p, core[(k + n - 1) % n], core[k]) }
            return dCore - rc
        }

        private fun segDist(p: V, s: V, e: V): Double {
            val d = e - s
            val t = (((p - s) dot d) / (d dot d)).coerceIn(0.0, 1.0)
            return (p - (s + d * t)).len
        }

        /** 공구가 닿아야 할 둘레 점들. 내측 R < r 이면 모서리 근처(공구가 못 들어가는 곳)는 뺀다 */
        fun targets(inner: Boolean, r: Double): List<V> {
            val out = mutableListOf<V>()
            val step = max(0.02, size / 400)
            if (shape == PolarShape.TWO_FLAT) {
                val half = sqrt(rs * rs - a * a)
                for (sgn in listOf(1.0, -1.0)) {
                    val n0 = nrm(0) * sgn
                    val k = ceil(2 * half / step).toInt()
                    for (j in 0..k) out += n0 * a + n0.left() * (-half + 2 * half * j / k)
                }
                return out
            }
            val reach = if (inner && rc < r) (a - r) * tan(PI / n) else ap * tan(PI / n)
            for (kk in 0 until n) {
                val nk = nrm(kk)
                val k = ceil(2 * reach / step).toInt()
                for (j in 0..k) out += nk * a + nk.left() * (-reach + 2 * reach * j / k)
                if (rc > 0 && !(inner && rc < r)) {
                    val ctr = core[kk]
                    val t0 = PI / 2 + rot + 2 * PI * kk / n
                    val m = max(2, ceil(rc * (2 * PI / n) / step).toInt())
                    for (j in 0..m) { val t = t0 + (2 * PI / n) * j / m; out += ctr + V(cos(t), sin(t)) * rc }
                }
            }
            return out
        }
    }

    /** 내측 살 두께(따로 짠 식): 구멍 둘레 점마다 단면 둘레까지 거리의 최댓값 */
    private fun innerStock(sh: Shape, rs: Double): Double {
        val rot = sh.rotDeg * PI / 180
        val vertexDirs = (0 until sh.n).map { k -> PI / 2 + rot + 2 * PI * k / sh.n + PI / sh.n }
        val angles = (0 until 3600).map { it * PI / 1800 } + vertexDirs
        return angles.maxOf { -sh.sd(V(cos(it), sin(it)) * rs) }
    }

    // ---------------------------------------------------------------- 작은 도우미

    private fun word(t: String, k: Char): Double? = Regex("""(?:^|\s)$k(-?[\d.]+)""").find(t)?.groupValues?.get(1)?.toDouble()

    /** [warnings] = null 이면 경고는 보지 않는다 */
    private fun ok(r: PolarResult, warnings: List<PolarWarning>? = emptyList()): PolarSolution {
        assertNotNull(r.solution, "계산이 멈췄다: ${r.warnings}")
        if (warnings != null) assertEquals(warnings, r.warnings)
        return r.solution
    }

    private fun stop(w: PolarWarning, i: PolarInput) {
        val r = PolarG12.calc(i)
        assertNull(r.solution, "멈춰야 하는데 숫자가 나왔다: $w $i")
        assertEquals(listOf(w), r.warnings, "$i")
    }

    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")
}
