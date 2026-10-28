package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperWarning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증 — 가공 방향 뒤집기(flip, T1 ⇄ T2 · 겹테이퍼 T1 ⇄ T3).
 *
 * 계산기가 낸 NC 글 두 가지를 이 파일에서 따로 읽어 노즈 원을 굴린다.
 * - 직접 보정(G40): 노즈 중심 = 인선 − 팁 벡터.
 * - 기계 보정(G41/G42): 제어기 보정을 흉내 낸다. 블록마다 진행 방향의 공구 쪽으로 r 오프셋(원호는 R ± r),
 *   블록 사이 안쪽 모서리는 두 오프셋의 교점, 바깥 모서리는 둔각이면 교점·예각이면 r 연장 꺾은선.
 *   보정 시작·취소 블록은 직선 이동(인선 점 ↔ 보정된 점). 그 끝 모서리가 바깥이면 FANUC Type A(다음/앞 블록에 직각)와
 *   Type B(교점) 둘 다 굴린다.
 * 재료 = 도면(TYPE 표로 따로 만든 윤곽 + 모서리 R) + 양 끝을 접선 방향으로 늘인 반직선. 외경은 그림 순서의 왼쪽, 내경은 오른쪽이 재료.
 * 그리고 넣은 시작 · 끝 좌표 너머가 막힌 쪽(현장 안전 원칙 2026-10-27 — 척 · 구멍 바닥 · 보스가 있다고 본다): −Z 로 가는 수평 요소의 끝 너머,
 * 재료 쪽으로 깎는 단면(외경 X− · 내경 X+)의 끝 X 너머(중심 X0 까지는 빼고). 노즈는 어떤 이동에서도 그 너머로 가지 않아야 한다.
 * 진입 G00 앞의 접근 줄(G40 상태 G00, 뒤집어 척 쪽에서 시작할 때)도 똑같이 굴린다.
 * 구현의 Pt·Intersect·NoseComp·NcLines·Corners 를 쓰지 않는다.
 */
class VerifyFlipTest {

    // ================================================================ 손 계산(사용자 질문의 예)

    /**
     * 외경 형상 1: 단면 X40 Z0 → 45° 면취 → 외경 X50 Z-5, 노즈 R0.4, 팁 3. 뒤집기 = 외경 쪽(X50 Z-5)부터 면취를 내려와 단면 쪽으로.
     * - 진행 방향 (Z+, X−)/√2, 재료는 면취 아래 왼쪽 → 공구는 진행 방향의 왼쪽 → G41.
     * - 꼭짓점 X50 Z-5: 외경 오프셋 x = 25.4, 면취 오프셋 x + z = 20 + 0.4√2 → 노즈 중심 (−4.8343, 25.4)
     *   → 인선 = 중심 + (−0.4, −0.4) = Z−5.234 X50.000.
     * - 꼭짓점 X40 Z0: 단면 오프셋 z = 0.4 ∩ 면취 오프셋 → 중심 (0.4, 20.1657) → 인선 Z0 X39.531.
     * - 진입: 첫 꺾임(외경–면취)이 볼록 → 면취 연장선에서 Z 로 c = max(1, 0.4) = 1: 도면 (Z−6, X52), 직접 보정 (Z−6.234, X52).
     *   그 점이 척 쪽(앞 끝면 Z0 보다 왼쪽)이라 앞에 접근 줄(현장 안전 원칙 2026-10-27): 앞 끝면 + c = Z1 에서 X52 를 맞추고 Z 로만.
     *   X52 는 오른쪽 도면(가장 높은 X50)보다 위라 그 X 로 온다.
     * - 후퇴: 마지막 꺾임(면취–단면)이 볼록 → G41/G42 는 면취를 Z 로 1 더 늘인 (Z1, X38), 직접 보정은 단면 공구 쪽(+Z)으로 1.
     */
    @Test
    fun handOuterType1Flip() {
        val r = 0.4
        val k = 20 + r * sqrt(2.0)
        val tA = P(k - 25.4, 25.4) + P(-r, -r)
        val tB = P(0.4, k - 0.4) + P(-r, -r)
        assertEquals("-5.234", f3(tA.z))
        assertEquals("50.000", f3(2 * tA.x))
        assertEquals("0.000", f3(tB.z))
        assertEquals("39.531", f3(2 * tB.x))
        val s = TaperCalc.calc(TaperInput(x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, flip = true)).solution!!
        assertEquals(Side.G41, s.side)
        assertEquals(3, s.tip)
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G00 Z1."), s.ncG40)
        assertEquals(listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G40 G01 X38. Z1."), s.ncG41G42)

        // 제어기 흉내: 시작 이동(면취와 한 직선, 길이 √2 ≥ r) 끝의 노즈 중심 = (−5, 25) + r(1, 1)/√2, 취소 앞 = (0, 20) + r(1, 1)/√2
        val drawing = fillet(listOf(P(0.0, 15.0), P(0.0, 20.0), P(-5.0, 25.0), P(-10.0, 25.0)), listOf(0.0, 0.0))!!
        val bnd = Bnd(extend(drawing), matLeft = true)
        val tv = tipVec(3, r)
        val g = simG4x(parse(s.ncG41G42.drop(1)), tv, r, typeB = false)!!
        val q = r / sqrt(2.0)
        assertTrue(g.centers.any { (it.b - P(-5 + q, 25 + q)).len < 1e-3 }, "시작 이동 끝의 노즈 중심")
        assertTrue(g.centers.any { (it.b - P(q, 20 + q)).len < 1e-3 }, "취소 앞 노즈 중심")
        // G00 점(G40 상태)에서 노즈 중심 = (−5.6, 26.4): 외경선(반경 25)까지 1.4, 노즈 원은 외경 위로 1.0 떨어짐
        assertEquals(1.4, bnd.sd(P(-6.0, 26.0) - tv), 1e-9)
        val d = simDirect(parse(s.ncG40), tv)
        assertTrue(minSd(d, bnd).first >= r - 1e-3)
        assertTrue(minSd(g.centers, bnd).first >= r - 1e-3)
    }

    /**
     * 같은 예로 단면을 중심(끝 X0)까지(현장 안전 원칙 2026-10-27): 직접 보정의 인선을 X0 에 멈추면 노즈 중심이 반경 r 에 서서
     * 중심에 살(법선 0.4√2 − 0.4 = 0.166)이 남고, 그 살을 다음 급송이 깎는다. 노즈 중심이 중심선에 닿게 인선을 X −2r 까지 보내고
     * 단면에서 떨어지는 +Z 로 뺀다. G41/G42 도 단면을 X −2r 까지 쓰고 Z 로 빼며 취소 — 안쪽 모서리 교점에서 노즈 중심이 중심선.
     * 두 프로그램 모두: 단면을 중심까지 남김 없이 깎고, 급송(접근 · 진입 · 후퇴)에서 노즈가 재료와 겹치지 않는다
     */
    @Test
    fun handOuterType1FlipToCenter() {
        val r = 0.4
        val s = TaperCalc.calc(TaperInput(x1 = 50.0, z1 = -5.0, x2 = 40.0, z2 = 0.0, end = 0.0, flip = true)).solution!!
        assertEquals(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G01 X-0.8", "G00 Z1."), s.ncG40)
        assertEquals(
            listOf("(R0.4 T3)", "G00 X52. Z1.", "G00 Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G01 X-0.8", "G40 G01 Z1."),
            s.ncG41G42,
        )
        val tv = tipVec(3, r)
        // 그림 오른쪽부터: 단면 끝 X0 → T(X40 Z0) → T(X50 Z-5) → 외경(비움, 5)
        val drawing = fillet(listOf(P(0.0, 0.0), P(0.0, 20.0), P(-5.0, 25.0), P(-10.0, 25.0)), listOf(0.0, 0.0))!!
        val bnd = Bnd(extend(drawing), matLeft = true)
        val face = sampleOf(drawing[0], 0.01)
        val d = simDirect(parse(s.ncG40), tv)
        val g = simG4x(parse(s.ncG41G42.drop(1)), tv, r, typeB = false)!!
        for ((name, c) in listOf("직접" to d, "G41/G42" to g.centers)) {
            assertTrue(minSd(c, bnd).first >= r - TOL, "$name 파고듦 ${minSd(c, bnd)}")
            val left = face.maxOf { q -> c.minOf { (q - it.nearest(q)).len } - r }
            assertTrue(left < TOL, "$name 단면(중심까지) 남김 $left")
            // 마지막 이동(급송)은 +Z 로만 — 단면을 따라 중심 너머로 가지 않는다
            val last = c.last()
            assertTrue(abs(last.b.x - last.a.x) < 1e-9 && last.b.z > last.a.z, "$name 후퇴 $last")
        }
        // 일부러 틀린 끝(인선 X0 에서 멈추고 Z 로 빼기): 중심에 0.166 이 남는 것을 이 해석기가 잡는다
        val wrong = simDirect(parse(listOf("G00 X52. Z1.", "G00 Z-6.234", "G01 X50. Z-5.234", "G01 X39.531 Z0.", "G01 X0.", "G00 Z1.")), tv)
        val left = face.maxOf { q -> wrong.minOf { (q - it.nearest(q)).len } - r }
        assertEquals(0.4 * sqrt(2.0) - 0.4, left, 2e-3)
    }

    /** 이 파일의 해석기가 눈뜬장님이 아닌지: 일부러 틀린 NC 는 잡아야 한다 */
    @Test
    fun simulatorCatchesWrongNc() {
        val r = 0.4
        val tv = tipVec(3, r)
        val bnd = Bnd(extend(fillet(listOf(P(0.0, 15.0), P(0.0, 20.0), P(-5.0, 25.0), P(-10.0, 25.0)), listOf(0.0, 0.0))!!), matLeft = true)
        val chamfer = listOf(Ln(P(0.0, 20.0), P(-5.0, 25.0)))
        val samples = sampleOf(chamfer[0], 0.01)
        fun leftover(c: List<Pc>) = samples.maxOf { q -> c.minOf { (q - it.nearest(q)).len } - r }
        // 뒤집기 G41 을 G42 로 — 반대쪽 보정이면 면취로 2r 파고든다
        val wrongSide = simG4x(parse(listOf("G00 X52. Z-6.", "G42 G01 X50. Z-5.", "G01 X40. Z0.", "G40 G00 X38. Z1.")), tv, r, false)!!
        assertTrue(minSd(wrongSide.centers, bnd).first < -r + 1e-3, "G42 로 바꾸면 파고듦")
        // 맞는 G41 은 깨끗
        val right = simG4x(parse(listOf("G00 X52. Z-6.", "G41 G01 X50. Z-5.", "G01 X40. Z0.", "G40 G00 X38. Z1.")), tv, r, false)!!
        assertTrue(minSd(right.centers, bnd).first > r - 1e-3)
        assertTrue(leftover(right.centers) < 1e-3)
        // 직접 보정에 도면 좌표를 그대로(보정 안 함) — 면취를 0.234/√2·… 덜 깎는다
        val raw = simDirect(parse(listOf("G00 X52. Z-6.", "G01 X50. Z-5.", "G01 X40. Z0.", "G00 Z1.")), tv)
        assertTrue(leftover(raw) > 0.1, "보정 안 한 좌표는 덜깎음 ${leftover(raw)}")
        // 직접 보정 끝점을 반대로(X39.531 → X40.469) — 0.234 직경만큼 덜 내려가 면취 끝을 덜 깎는다
        val short = simDirect(parse(listOf("G00 X52. Z-6.234", "G01 X50. Z-5.234", "G01 X40.469 Z0.", "G00 Z1.")), tv)
        assertTrue(leftover(short) > 0.05, "끝점이 모자라면 덜깎음 ${leftover(short)}")
    }

    // ================================================================ 격자

    @Test
    fun singleTaperGrid() {
        val rep = Rep("홑테이퍼")
        for (flip in BOOLS) for (inner in BOOLS) for (type in 1..5) for (reverse in BOOLS) {
            for (deg in listOf(15.0, 30.0, 45.0, 60.0, 75.0)) for (r in listOf(0.2, 0.4, 0.8, 1.2)) {
                for (cr in listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.5, 0.4 to 0.4)) for (legs in 0..3) {
                    single(rep, inner, type, reverse, flip, r, deg, cr, legs and 1 != 0, legs and 2 != 0, 1.0)
                }
            }
        }
        // Z 여유 0(진입 거리 = r)·3
        for (flip in BOOLS) for (inner in BOOLS) for (type in 1..5) for (reverse in BOOLS) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 1.2)) for (zc in listOf(0.0, 3.0)) {
                for (cr in listOf(0.0 to 0.0, 0.0 to 1.0)) for (legs in 0..3) {
                    single(rep, inner, type, reverse, flip, r, deg, cr, legs and 1 != 0, legs and 2 != 0, zc)
                }
            }
        }
        rep.finish()
    }

    @Test
    fun doubleTaperGrid() {
        val rep = Rep("겹테이퍼")
        val k60 = 2 * 5 * tan(PI / 3)
        // 그림 오른쪽부터 직경: 산·골·오르막·내리막·예각 산(60°+60°)·예각 골
        val shapes = listOf(
            listOf(40.0, 52.0, 46.0), listOf(52.0, 40.0, 48.0), listOf(40.0, 46.0, 60.0), listOf(60.0, 50.0, 40.0),
            listOf(40.0, 40.0 + k60, 40.0), listOf(40.0 + k60, 40.0, 40.0 + k60),
        )
        for (flip in BOOLS) for (inner in BOOLS) for (reverse in BOOLS) for (xs in shapes) {
            for (r in listOf(0.2, 0.4, 0.8, 1.2)) for (cr in listOf(listOf(0.0, 0.0, 0.0), listOf(1.0, 2.0, 0.5), listOf(0.3, 0.0, 1.2))) {
                for (legs in 0..3) for (zc in listOf(0.0, 1.0)) {
                    double(rep, inner, reverse, flip, r, xs, cr, legs and 1 != 0, legs and 2 != 0, zc)
                }
            }
        }
        rep.finish()
    }

    // ================================================================ 사례 만들기

    /** cr = (그림 오른쪽 점 R, 왼쪽 점 R). front/backGiven = 그림 앞(오른쪽)·뒤(왼쪽) 요소 끝 좌표를 넣나 */
    private fun single(
        rep: Rep, inner: Boolean, type: Int, reverse: Boolean, flip: Boolean, r: Double, deg: Double,
        cr: Pair<Double, Double>, frontGiven: Boolean, backGiven: Boolean, zClear: Double,
    ) {
        // TYPE 표(설계 문서)를 그대로: 그림 오른쪽 → 왼쪽으로 지름이 커지는 것은 외경 1~4 · 내경 5
        val rises = if (inner) type == 5 else type != 5
        val right = P(0.0, 30.0)
        val dx = 6 * tan(deg * PI / 180)
        val left = P(-6.0, 30.0 + if (rises) dx else -dx)
        if (left.x <= 0.5) return
        val frontV = type <= 2
        val backV = type == 2 || type == 4
        val fl = if (frontGiven) 6.0 else 5.0
        val bl = if (backGiven) 6.0 else 5.0
        // 단면은 경사의 오르내림을 잇는다: 앞 단면은 외경이면 아래에서 T1 로 올라오고 내경이면 위에서 내려온다. 뒤 단면은 그 반대쪽으로 이어 간다
        val front = if (frontV) P(right.z, max(right.x + (if (inner) fl else -fl), 0.0)) else P(right.z + fl, right.x)
        val back = if (backV) P(left.z, max(left.x + (if (inner) -bl else bl), 0.0)) else P(left.z - bl, left.x)
        val fVal = if (!frontGiven) null else if (frontV) 2 * front.x else front.z
        val bVal = if (!backGiven) null else if (backV) 2 * back.x else back.z
        val inp = if (!flip) {
            TaperInput(
                inner, type, reverse, r, null, 2 * right.x, right.z, 2 * left.x, left.z, null, zClear,
                cornerR1 = cr.first, cornerR2 = cr.second, start = fVal, end = bVal,
            )
        } else {
            // 뒤집으면 칸 T1 = 그림 왼쪽 점, 시작 = 그 앞 요소(그림 뒤 요소), 끝 = 그림 앞 요소
            TaperInput(
                inner, type, reverse, r, null, 2 * left.x, left.z, 2 * right.x, right.z, null, zClear,
                cornerR1 = cr.second, cornerR2 = cr.first, start = bVal, end = fVal, flip = true,
            )
        }
        runCase(
            rep, inp.toString(), TaperCalc.calc(inp), inner, reverse, flip, r,
            listOf(front, right, left, back), listOf(cr.first, cr.second), frontGiven, backGiven,
        )
    }

    private fun double(
        rep: Rep, inner: Boolean, reverse: Boolean, flip: Boolean, r: Double, xs: List<Double>, cr: List<Double>,
        frontGiven: Boolean, backGiven: Boolean, zClear: Double,
    ) {
        val t = listOf(P(0.0, xs[0] / 2), P(-5.0, xs[1] / 2), P(-10.0, xs[2] / 2))
        val front = P(t[0].z + (if (frontGiven) 6.0 else 5.0), t[0].x)
        val back = P(t[2].z - (if (backGiven) 6.0 else 5.0), t[2].x)
        val fVal = if (frontGiven) front.z else null
        val bVal = if (backGiven) back.z else null
        val k = if (flip) listOf(2, 1, 0) else listOf(0, 1, 2)
        val inp = DoubleTaperInput(
            inner, reverse, r, null,
            2 * t[k[0]].x, t[k[0]].z, 2 * t[k[1]].x, t[k[1]].z, 2 * t[k[2]].x, t[k[2]].z, zClear,
            cornerR1 = cr[k[0]], cornerR2 = cr[k[1]], cornerR3 = cr[k[2]],
            start = if (flip) bVal else fVal, end = if (flip) fVal else bVal, flip = flip,
        )
        runCase(rep, inp.toString(), TaperCalc.calcDouble(inp), inner, reverse, flip, r, listOf(front) + t + back, cr, frontGiven, backGiven)
    }

    // ================================================================ 한 사례 검사

    /**
     * pic = 그림 오른쪽부터 [앞 끝, T…, 뒤 끝], cr = T 점마다 R(그림 순서).
     * 가공은 계산기 역방향과 뒤집기 가운데 하나만 켜졌으면 그림 왼쪽 끝부터(설계 문서 "가공 방향 뒤집기")
     */
    private fun runCase(
        rep: Rep, id0: String, res: TaperResult, inner: Boolean, reverse: Boolean, flip: Boolean, r: Double,
        pic: List<P>, cr: List<Double>, frontGiven: Boolean, backGiven: Boolean,
    ) {
        rep.cases++
        val tag = (if (flip) "뒤집기" else "기존") + (if (reverse) "·계산기역" else "·정")
        val id = "$id0 → ${res.warnings}"
        val drawing = fillet(pic, cr)
        val sol = res.solution
        if (drawing == null) {
            if (sol != null) rep.bad("$tag R판정", "모서리 R 이 안 들어가는데 숫자를 냈다: $id")
            else rep.stop(res.warnings.toString())
            return
        }
        if (sol == null) {
            rep.stop(res.warnings.toString())
            if (res.warnings.single() in listOf(TaperWarning.CORNER_R_TOO_BIG, TaperWarning.BAD_LEG, TaperWarning.SHAPE_MISMATCH)) {
                rep.bad("$tag 멈춤", "내 도면은 그려지는데 ${res.warnings} 로 멈춤: $id")
            }
            return
        }
        rep.solved++
        val travelLeft = reverse != flip
        val matLeft = !inner
        // 바이트는 정/역방향 스위치만 따른다(외경 3·역 4, 내경 2·역 1). 보정 쪽 = 진행 방향에서 본 공구(재료 반대) 쪽
        val expTip = if (inner) (if (reverse) 1 else 2) else (if (reverse) 4 else 3)
        val expSide = if (matLeft != travelLeft) Side.G42 else Side.G41
        if (sol.tip != expTip) rep.bad("$tag 팁", "팁 ${sol.tip} ≠ $expTip: $id")
        if (sol.side != expSide) rep.bad("$tag 보정쪽", "보정 쪽 ${sol.side} ≠ $expSide: $id")
        val tv = tipVec(sol.tip, r)

        // 칸 순서의 꼭짓점: 뒤집으면 그림 왼쪽부터
        val tPic = pic.subList(1, pic.size - 1)
        val tCell = if (flip) tPic.reversed() else tPic
        for ((k, p) in sol.points.withIndex()) {
            if (abs(p.vertex.z - tCell[k].z) > 1e-6 || abs(p.vertex.x - tCell[k].x) > 1e-6) {
                rep.bad("$tag 칸순서", "points[$k] = Z${p.vertex.z} X${2 * p.vertex.x}, 칸 T${k + 1} = ${tCell[k]}: $id")
            }
        }
        for ((k, a) in sol.anglesDeg.withIndex()) {
            val u = tCell[k + 1] - tCell[k]
            val want = atan2(abs(u.x), abs(u.z)) * 180 / PI
            if (abs(a - want) > 1e-6) rep.bad("$tag 각도순서", "anglesDeg[$k] = $a, 칸 경사 ${k + 1} = $want: $id")
        }

        val bnd = Bnd(extend(drawing), matLeft)
        val ncD = parse(sol.ncG40)
        val head = sol.ncG41G42.first()
        if (head != "(R${nc(r)} T${sol.tip})") rep.bad("$tag 주석", "첫 줄 $head: $id")
        val ncG = parse(sol.ncG41G42.drop(1))
        val g4 = ncG.mapNotNull { it.comp }.filter { it != 40 }.distinct()
        if (g4 != listOf(if (expSide == Side.G41) 41 else 42)) rep.bad("$tag G41/G42글", "NC 의 보정 코드 $g4, 진행으로 본 쪽 $expSide: $id")
        // 보정 시작 줄 앞은 G40 상태 G00 들(접근 줄 + 진입). 취소는 마지막 줄(G40 직선) 또는 이동 없는 G40 한 줄 + 마지막 직선 이동
        val k0 = ncG.indexOfFirst { it.comp != null }
        val kc = ncG.indexOfFirst { it.comp == 40 }
        val alone = kc > 0 && (ncG[kc].p - ncG[kc - 1].p).len < 1e-9
        if (k0 < 1 || kc < k0 + 2 || ncG.subList(0, k0).any { it.g != 0 } || ncG[k0].comp == 40 || ncG[k0].g !in 0..1 ||
            (!alone && ncG[kc].g !in 0..1) || ncG.subList(k0 + 1, kc).any { it.comp != null } ||
            (if (alone) kc != ncG.size - 2 || ncG.last().comp != null || ncG.last().g !in 0..1 else kc != ncG.size - 1)
        ) {
            rep.bad("$tag G41/G42글", "보정 시작·취소 줄 꼴: ${sol.ncG41G42}")
            return
        }
        if (alone) rep.info("$tag 이동 없는 G40 취소")
        val startLen = (ncG[k0].p - ncG[k0 - 1].p).len
        val cancelLen = (ncG.last().p - ncG[ncG.size - 2].p).len
        if (startLen < r - 1e-6) rep.bad("$tag 시작이동", "보정 시작 이동 ${f4(startLen)} < r $r: ${sol.ncG41G42}")
        if (cancelLen < r - 1e-6) rep.bad("$tag 취소이동", "보정 취소 이동 ${f4(cancelLen)} < r $r: ${sol.ncG41G42}")

        // 깎아야 할 도면: 경사·모서리 R 은 늘, 앞뒤 요소는 넣었을 때만. 빼는 곳: 오목 모서리(R < r)에 r 이 남는 자리,
        // 넣은 앞뒤 요소의 바깥 끝 r(인선을 끝좌표에 두면 노즈 둥근 데가 지나가는 자리)
        val excl = mutableListOf<Pair<P, Double>>()
        for (k in 1 until pic.size - 1) {
            val a = (pic[k] - pic[k - 1]).unit()
            val b = (pic[k + 1] - pic[k]).unit()
            val turn = a cross b
            val concave = if (matLeft) turn < -1e-12 else turn > 1e-12
            if (concave && cr[k - 1] < r - 1e-9) {
                val beta = abs(atan2(turn, a dot b))
                excl += pic[k] to (r * tan(beta / 2) + 0.01)
            }
        }
        // 다만 가공 끝 쪽 요소를 노즈가 인선보다 앞서 깎아 끝나면(팁 벡터 · 진행 ≤ 0 — 뒤집어 부품 앞 끝으로 · 다른 팁) 직접 보정의 노즈는 끝좌표를
        // 지나고 G41/G42 는 노즈 접점이 끝좌표에 서야 한다(FF 5.4.4 — 공구 쪽 직각 취소의 교점은 거기 못 닿는다, 설계 13.7) — 끝 꼭짓점까지 본다
        fun trimmed(end: P, nb: P) = !(if (travelLeft) end === pic.first() else end === pic.last()) || (tv dot (end - nb).unit()) > 1e-9
        if (frontGiven && trimmed(pic.first(), pic[1])) excl += pic.first() to (r + 0.01)
        if (backGiven && trimmed(pic.last(), pic[pic.size - 2])) excl += pic.last() to (r + 0.01)
        val cut = drawing.indices.filter { (it != 0 || frontGiven) && (it != drawing.lastIndex || backGiven) }
        val samples = cut.flatMap { i -> sampleOf(drawing[i], 0.01) }.filter { q -> excl.none { (q - it.first).len < it.second } }

        // 넣은 시작 · 끝 좌표 너머가 막힌 쪽(현장 안전 원칙): −Z 로 가는 수평 요소(척 · 구멍 바닥), 재료 쪽으로 깎는 단면(외경 X− · 내경 X+,
        // 중심 X0 까지는 너머가 이미 깎은 같은 면이라 빼고). 계산기 역방향(화면이 쓰지 않음)은 역바이트가 척 쪽 홈에서 들어오는 규칙이라 빼고
        val walls = if (reverse) emptyList() else buildList {
            for ((given, end, nb) in listOf(Triple(frontGiven, pic.first(), pic[1]), Triple(backGiven, pic.last(), pic[pic.size - 2]))) {
                if (!given) continue
                val o = (end - nb).unit()
                val horiz = abs(o.z) > abs(o.x)
                if ((horiz && o.z < 0) || (!horiz && (o.x < 0) == matLeft && end.x > 1e-9)) add(end to o)
            }
        }
        // 접근 줄: 뒤집어 척 쪽(그림 왼쪽)에서 시작해 진입 G00 이 앞 끝면(T 점 · 넣은 앞 요소 끝 가운데 가장 오른쪽)보다 왼쪽이면,
        // 처음 G00 은 앞 끝면 바깥(Z ≥ 앞 끝면 + r)이고 그 뒤 G00 은 한 축씩(두 축이 따로 움직여 입구 · 부품 앞에 닿지 않게)
        val zFront = (tPic.map { it.z } + listOfNotNull(pic.first().z.takeIf { frontGiven })).max()
        for ((name, nc) in listOf("직접" to ncD, "G41/G42" to ncG)) {
            val lead = nc.indexOfFirst { it.g != 0 || it.comp != null }
            val e = nc[lead - 1].p
            if (!flip || reverse || e.z >= zFront - 1e-6) continue
            rep.info("$tag 척 쪽 진입 $name")
            if (lead < 2 || nc[0].p.z < zFront + r - 1e-6) rep.bad("$tag 접근줄", "$name 진입 $e 이 척 쪽인데 앞 끝면 바깥 G00 이 없다 ${sol.ncG41G42} ${sol.ncG40}: $id")
            for (k in 1 until lead) {
                if (abs(nc[k].p.z - nc[k - 1].p.z) > 1e-6 && abs(nc[k].p.x - nc[k - 1].p.x) > 1e-6) {
                    rep.bad("$tag 접근줄두축", "$name G00 이 두 축을 함께 ${nc[k - 1].p} → ${nc[k].p}: $id")
                }
            }
        }
        // G00 점(프로그램 좌표)도 넣은 좌표 너머로 가지 않는다
        for ((w, o) in walls) {
            for (b in ncD.filter { it.g == 0 } + ncG.filter { it.g == 0 }) {
                if (((b.p - w) dot o) > TOL) rep.bad("$tag G00너머", "G00 ${b.p} 이 넣은 좌표 $w 너머(${f4((b.p - w) dot o)}) ${sol.ncG40} ${sol.ncG41G42}: $id")
            }
        }

        // 직접 보정
        val dc = simDirect(ncD, tv)
        check(rep, "$tag 직접", id, sol.ncG40, dc, bnd, samples, r, ncD.indexOfFirst { it.g != 0 } - 1, walls)
        // G41/G42 — 오목 R < r 은 제어기 간섭 알람(계산기도 CONCAVE_R_LE_NOSE)이라 굴리지 않는다
        val ga = simG4x(ncG, tv, r, typeB = false)
        if (ga == null) {
            rep.info("$tag G41/G42 오목 R<r 알람(안 굴림)")
            if (TaperWarning.CONCAVE_R_LE_NOSE !in res.warnings) rep.bad("$tag 알람경고", "오목 R < r 인데 경고 없음: $id")
        } else {
            check(rep, "$tag G4x-A", id, sol.ncG41G42, ga.centers, bnd, samples, r, k0 - 1, walls)
            if (ga.outerStart) rep.info("$tag 시작 모서리 바깥(Type A/B 다름)")
            if (ga.outerCancel) rep.info("$tag 취소 모서리 바깥(Type A/B 다름)")
            if (ga.outerStart || ga.outerCancel) {
                check(rep, "$tag G4x-B", id, sol.ncG41G42, simG4x(ncG, tv, r, typeB = true)!!.centers, bnd, samples, r, k0 - 1, walls)
            }
        }

        // 날카로운 T 꼭짓점의 보정점(내가 푼 오프셋 교점 + 팁 벡터)이 직접 보정 NC 에 진행 순서대로, 도면점이 G41/G42 NC 에 진행 순서대로
        if (TaperWarning.NOSE_GT_SLOPE !in res.warnings) {
            val order = if (travelLeft) (tPic.indices).reversed() else tPic.indices
            val dPts = ncD.map { it.p }
            val gPts = ncG.map { it.p }
            var lastD = -1
            var lastG = -1
            for (k in order) {
                if (cr[k] > 0) continue
                val v = tPic[k]
                val pi = pic.indexOf(v)
                val a = (v - pic[pi - 1]).unit()
                val b = (pic[pi + 1] - v).unit()
                val na = if (matLeft) a.left() * -1.0 else a.left()
                val nb = if (matLeft) b.left() * -1.0 else b.left()
                val c = lineMeet(v + na * r, a, v + nb * r, b) ?: continue
                val want = c + tv
                val di = dPts.indexOfFirst { (it - want).len < 1.2e-3 }
                val gi = gPts.indexOfFirst { (it - v).len < 1.2e-3 }
                if (di < 0) rep.bad("$tag 보정점", "T 점 $v 의 보정점 $want 이 직접 보정 NC 에 없다 ${sol.ncG40}: $id")
                else if (di <= lastD) rep.bad("$tag 보정순서", "보정점 $want 의 순서가 진행과 다르다 ${sol.ncG40}: $id")
                else lastD = di
                if (gi < 0) rep.bad("$tag 도면점", "T 점 $v 이 G41/G42 NC 에 없다 ${sol.ncG41G42}: $id")
                else if (gi <= lastG) rep.bad("$tag 도면순서", "T 점 $v 의 순서가 진행과 다르다 ${sol.ncG41G42}: $id")
                else lastG = gi
            }
        }

        // 바이트 날: 넣은 수평 앞뒤 요소를 팁의 Z 와 반대로 깎으면 AGAINST_TOOL 이 나야 한다(외경을 T3 로 Z+ 따위)
        for ((given, i) in listOf(frontGiven to 0, backGiven to drawing.lastIndex)) {
            if (!given) continue
            val e = drawing[i]
            val u = (e.b - e.a).unit() * (if (travelLeft) -1.0 else 1.0)
            if (abs(u.z) > abs(u.x) && u.z * tv.z < 0 && TaperWarning.AGAINST_TOOL !in res.warnings) {
                rep.bad("$tag 바이트반대", "수평 요소를 팁 Z 와 반대로 깎는데 AGAINST_TOOL 없음: $id")
            }
        }
        if (flip && !reverse && !frontGiven && !backGiven && pic.size == 4 && cr.all { it == 0.0 } && r == 0.4) {
            val deg = atan2(abs(pic[2].x - pic[1].x), abs(pic[2].z - pic[1].z)) * 180 / PI
            val w = if (TaperWarning.AGAINST_TOOL in res.warnings) "경고" else "없음"
            rep.info("뒤집기 정방향 ${if (inner) "내경" else "외경"} 앞뒤 비움 ${f0(deg)}° AGAINST_TOOL $w")
        }
        // 진입 G00 점이 첫 깎는 점보다 반대쪽 끝(그림)으로 가 있나 — 실물에서 그쪽에 무엇이 있는지 앱은 모른다
        val e0 = ncG[k0 - 1].p
        val zMax = tPic.maxOf { it.z }
        if (flip && !reverse && e0.z < zMax - 1e-9) rep.info("$tag 정방향: G41/G42 G00 진입점이 그림 오른쪽 끝 T 보다 척 쪽" + if (inner) "(구멍 안)" else "")
    }

    /**
     * 노즈 원 굴리기: 파고듦(여유 < r) · 덜깎음(닿아야 할 도면점까지 거리 > r) · 넣은 좌표 너머(walls = (끝 점, 너머 방향))로 넘어감.
     * lead = 진입 G00 점 조각의 번호(그 앞은 접근 줄)
     */
    private fun check(
        rep: Rep, tag0: String, id: String, nc: List<String>, centers: List<Pc>, bnd: Bnd, samples: List<P>, r: Double,
        lead: Int = 0, walls: List<Pair<P, P>> = emptyList(),
    ) {
        // 경고(AGAINST_TOOL 등)가 함께 났는지로 나눈다 — 경고 없이 틀린 숫자가 제일 나쁘다
        val tag = tag0 + if ("AGAINST_TOOL" in id.substringAfterLast("→")) WARNED else "(경고 없음)"
        // 조각마다 따로 재서 어디서 파고드는지(lead = G00 점, 그 앞 접근 줄, 다음 = 진입·보정 시작 이동, 마지막 = 후퇴·보정 취소)
        fun where(i: Int) = when {
            i < lead -> "접근줄"
            i == lead -> "G00점"
            i == lead + 1 -> "진입이동"
            i == centers.lastIndex -> "후퇴이동"
            else -> "형상"
        }
        for ((i, pc) in centers.withIndex()) {
            val (sd, at) = minSd(listOf(pc), bnd)
            rep.worstGouge = max(rep.worstGouge, r - sd)
            if (sd < r - TOL) {
                rep.bad("$tag 파고듦-${where(i)}", "노즈 원이 재료로 ${f4(r - sd)} mm (노즈 중심 $at) $nc: $id", r - sd)
                break
            }
        }
        // 넣은 시작 · 끝 좌표 너머(척 · 구멍 바닥 · 보스): 노즈 바깥 끝 = 중심·o + r
        for ((w, o) in walls) {
            for ((i, pc) in centers.withIndex()) {
                val n = max(1, ceil(pc.length / 0.004).toInt())
                val over = (0..n).maxOf { ((pc.at(it.toDouble() / n) - w) dot o) + r }
                if (over > TOL) {
                    rep.bad("$tag 너머-${where(i)}", "노즈가 넣은 좌표 $w 너머로 ${f4(over)} mm $nc: $id", over)
                    break
                }
            }
        }
        var worst = 0.0
        var wAt: P? = null
        for (q in samples) {
            val d = centers.minOf { (q - it.nearest(q)).len } - r
            if (d > worst) {
                worst = d
                wAt = q
            }
        }
        rep.worstLeft = max(rep.worstLeft, worst)
        if (worst > TOL) rep.bad("$tag 덜깎음", "도면 $wAt 에서 ${f4(worst)} mm 남음 $nc: $id", worst)
    }

    // ================================================================ NC 읽기·굴리기

    private class Blk(val g: Int, val comp: Int?, val p: P, val rr: Double?)

    /** 주석 줄은 건너뛴다. X 는 직경 → 반경, 빠진 축은 앞 줄 값 */
    private fun parse(lines: List<String>): List<Blk> {
        var z: Double? = null
        var x: Double? = null
        var g = -1
        val out = mutableListOf<Blk>()
        for (line in lines) {
            if (line.startsWith("(")) continue
            var comp: Int? = null
            var rr: Double? = null
            for (tok in line.trim().split(" ")) {
                val v = tok.substring(1)
                when (tok[0]) {
                    'G' -> when (val n = v.toInt()) {
                        0, 1, 2, 3 -> g = n
                        40, 41, 42 -> comp = n
                        else -> error("모르는 G$n: $line")
                    }
                    'X' -> x = v.toDouble() / 2
                    'Z' -> z = v.toDouble()
                    'R' -> rr = v.toDouble()
                    else -> error("모르는 낱말 $tok: $line")
                }
            }
            out += Blk(g, comp, P(z!!, x!!), rr)
        }
        return out
    }

    /** a → b, 반지름 R, cw = G02(이 그림에서 시계) 인 원호(180° 이하)의 중심 */
    private fun arcCenter(a: P, b: P, rr: Double, cw: Boolean): P {
        val d = b - a
        val half = d.len / 2
        require(rr >= half - 2e-3) { "R $rr 이 현의 반 $half 보다 작다" }
        val h = sqrt(max(0.0, rr * rr - half * half))
        val m = (a + b) * 0.5
        val nl = d.unit().left()
        return if (cw) m - nl * h else m + nl * h
    }

    private fun simDirect(b: List<Blk>, tv: P): List<Pc> {
        val out = mutableListOf<Pc>(Ln(b[0].p - tv, b[0].p - tv))
        for (k in 1 until b.size) {
            val a = b[k - 1].p
            val e = b[k].p
            out += when (b[k].g) {
                0, 1 -> Ln(a - tv, e - tv)
                2, 3 -> Ac(a - tv, e - tv, arcCenter(a, e, b[k].rr!!, b[k].g == 2) - tv, b[k].g == 2)
                else -> error("${b[k].g}")
            }
        }
        return out
    }

    /** 프로그램 블록 하나(도면 좌표). c = 원호 중심 */
    private class Ent(val s: P, val e: P, val c: P?, val cw: Boolean) {
        fun tanAt(p: P): P = if (c == null) (e - s).unit() else (p - c).unit().left() * (if (cw) -1.0 else 1.0)
        fun tanS() = tanAt(s)
        fun tanE() = tanAt(e)
    }

    private class G4xSim(val centers: List<Pc>, val outerStart: Boolean, val outerCancel: Boolean)

    /**
     * 기계 보정 흉내. 처음 G41/G42 줄 앞은 G40 상태 G00 들(접근 줄 · 진입 — 노즈 중심 = 인선 − 팁 벡터),
     * 그 다음 b[0] = 진입 G00(G40), b[1] = G41/G42 시작(직선), b[1..m] 보정 블록, b[m+1] = G40 취소(직선), 그 뒤 G40 상태 줄들.
     * 취소가 이동 없는 G40 한 줄이면 앞 블록 끝 법선에 서고 다음 이동에서 벡터를 지운다 — 형식 A·B 같음
     * (FF 5.4.4 그림 5.4.4(e) 204쪽, F0 14.3.4 228쪽 — 2026-10-28 더함).
     * 오목 원호가 R < r 이면(제어기 알람) null
     */
    private fun simG4x(all: List<Blk>, tv: P, r: Double, typeB: Boolean): G4xSim? {
        val k0 = all.indexOfFirst { it.comp != null }
        val pre = (1 until k0).map { Ln(all[it - 1].p - tv, all[it].p - tv) }
        val kc = (k0 + 1 until all.size).first { all[it].comp == 40 }
        val alone = (all[kc].p - all[kc - 1].p).len < 1e-9
        val b = all.subList(k0 - 1, if (alone) kc else kc + 1)
        val side = if (b[1].comp == 41) 1.0 else -1.0
        val ents = (1 until b.size).map { k ->
            val a = b[k - 1].p
            val e = b[k].p
            if (b[k].g == 2 || b[k].g == 3) Ent(a, e, arcCenter(a, e, b[k].rr!!, b[k].g == 2), b[k].g == 2) else Ent(a, e, null, false)
        }
        val m = if (alone) ents.size else ents.size - 1 // ents[m] = 취소 블록(이동 없는 G40 이면 없음)
        fun n(t: P) = t.left() * side
        fun rho(en: Ent): Double {
            val c = en.c!!
            val toward = (n(en.tanS()) dot (c - en.s)) > 0
            val rr = (en.s - c).len
            return if (toward) rr - r else rr + r
        }
        for (k in 1 until m) if (ents[k].c != null && rho(ents[k]) < -1e-9) return null
        var outerStart = false
        var outerCancel = false

        /** A 끝(= B 처음) 모서리에서 노즈 중심이 지나는 점들 */
        fun junction(ia: Int): List<P> {
            val a = ents[ia]
            val bb = ents[ia + 1]
            val e = a.e
            val ta = a.tanE()
            val tb = bb.tanS()
            val na = n(ta)
            val nb = n(tb)
            val cr = ta cross tb
            val dt = ta dot tb
            if (abs(cr) < 1e-9 && dt > 0) return listOf(e + na * r)
            val inner = cr * side > 0
            val startup = ia == 0
            val cancel = !alone && ia + 1 == m
            if (inner) {
                // 취소 블록의 오프셋은 그 직선을 r 민 선
                return listOf(meet(a, bb, na, nb, e, r, if (cancel) null else bb.c?.let { rho(bb) }, if (startup) null else a.c?.let { rho(a) }))
            }
            if (startup) outerStart = true
            if (cancel) outerCancel = true
            if (startup && !typeB) return listOf(e + nb * r)
            if (cancel && !typeB) return listOf(e + na * r)
            return if (dt >= 0) {
                listOf(meet(a, bb, na, nb, e, r, if (cancel) null else bb.c?.let { rho(bb) }, if (startup) null else a.c?.let { rho(a) }))
            } else {
                listOf(e + na * r, e + na * r + ta * r, e + nb * r - tb * r, e + nb * r)
            }
        }

        val out = mutableListOf<Pc>()
        var cur = b[0].p - tv
        out += Ln(cur, cur)
        for (k in 0 until m) {
            val en = ents[k]
            // 이동 없는 G40 앞 블록의 끝 = 그 블록 끝 법선
            val j = if (alone && k == m - 1) listOf(en.e + n(en.tanE()) * r) else junction(k)
            out += if (k == 0 || en.c == null) Ln(cur, j.first()) else Ac(cur, j.first(), en.c, en.cw)
            for (q in j.zipWithNext()) out += Ln(q.first, q.second)
            cur = j.last()
        }
        // 취소 블록(이동 없는 G40 이면 그 다음 이동)은 보정 자리에서 끝점(G40 상태)으로 곧게, 그 뒤 G40 상태 줄들
        for (k in kc + (if (alone) 1 else 0) until all.size) {
            if (k > kc && (all[k].p - all[k - 1].p).len < 1e-9) continue
            out += Ln(cur, all[k].p - tv)
            cur = all[k].p - tv
        }
        return G4xSim(pre + out, outerStart, outerCancel)
    }

    /**
     * 두 블록 오프셋의 교점(e 에 가까운 것). 직선은 (끝점 + n r, 접선), 원호는 (중심, ρ). rhoB·rhoA = null 이면 직선으로 본다
     */
    private fun meet(a: Ent, b: Ent, na: P, nb: P, e: P, r: Double, rhoB: Double?, rhoA: Double?): P {
        val guess = e + (na + nb) * (r / 2)
        val pa = e + na * r
        val pb = e + nb * r
        val ta = a.tanE()
        val tb = b.tanS()
        val cands: List<P> = when {
            rhoA == null && rhoB == null -> listOfNotNull(lineMeet(pa, ta, pb, tb))
            rhoA == null -> lineCircle(pa, ta, b.c!!, rhoB!!)
            rhoB == null -> lineCircle(pb, tb, a.c!!, rhoA)
            else -> circleCircle(a.c!!, rhoA, b.c!!, rhoB)
        }
        return cands.minByOrNull { (it - guess).len } ?: (e + na * r)
    }

    // ================================================================ 도면·재료

    /** 꼭짓점 pts(그림 순서)에 모서리 R 을 끼운 윤곽. 안 들어가면 null */
    private fun fillet(pts: List<P>, cr: List<Double>): List<Pc>? {
        val out = mutableListOf<Pc>()
        var cur = pts[0]
        for (k in 1 until pts.size - 1) {
            val rr = cr[k - 1]
            val v = pts[k]
            val a = (v - pts[k - 1]).unit()
            val b = (pts[k + 1] - v).unit()
            if (rr <= 0 || abs(a cross b) < 1e-9 && (a dot b) > 0) {
                if ((v - cur).len > 1e-12) out += Ln(cur, v)
                cur = v
                continue
            }
            val turn = atan2(a cross b, a dot b)
            val t = rr * tan(abs(turn) / 2)
            val t1 = v - a * t
            val t2 = v + b * t
            if (((t1 - cur) dot a) < -1e-9 || t > (pts[k + 1] - v).len + 1e-9) return null
            val c = t1 + (if (turn > 0) a.left() else a.left() * -1.0) * rr
            if ((t1 - cur).len > 1e-12) out += Ln(cur, t1)
            out += Ac(t1, t2, c, turn < 0)
            cur = t2
        }
        val last = pts.last()
        if (((last - cur) dot (last - pts[pts.size - 2])) < -1e-9) return null
        if ((last - cur).len > 1e-12) out += Ln(cur, last)
        return out
    }

    /** 양 끝을 접선 방향으로 늘인 반직선(1000 mm)을 붙인다 */
    private fun extend(d: List<Pc>): List<Pc> {
        val u0 = d.first().tanStart()
        val un = d.last().tanEnd()
        return listOf(Ln(d.first().a - u0 * 1000.0, d.first().a)) + d + Ln(d.last().b, d.last().b + un * 1000.0)
    }

    /** 경계까지 부호 거리: 재료 밖 +, 안 −. 가장 가까운 점이 이음점이면 그 모서리의 쐐기로 안팎을 본다 */
    private class Bnd(val pcs: List<Pc>, val matLeft: Boolean) {
        fun sd(q: P): Double {
            var best = Double.POSITIVE_INFINITY
            var bi = 0
            var bp = q
            for ((i, pc) in pcs.withIndex()) {
                val p = pc.nearest(q)
                val d = (q - p).len
                if (d < best) {
                    best = d
                    bi = i
                    bp = p
                }
            }
            if (best < 1e-12) return 0.0
            val pc = pcs[bi]
            val j = when {
                bi > 0 && (bp - pc.a).len < 1e-9 -> bi - 1
                bi < pcs.lastIndex && (bp - pc.b).len < 1e-9 -> bi
                else -> -1
            }
            val inMat = if (j >= 0) {
                val v = pcs[j].b
                val ta = pcs[j].tanEnd()
                val tb = pcs[j + 1].tanStart()
                val w = q - v
                val sa = ta cross w
                val sb = tb cross w
                val turn = ta cross tb
                if (matLeft) {
                    if (turn >= 0) sa > 0 && sb > 0 else sa > 0 || sb > 0
                } else {
                    if (turn <= 0) sa < 0 && sb < 0 else sa < 0 || sb < 0
                }
            } else {
                val s = pc.tanAt(bp) cross (q - bp)
                if (matLeft) s > 0 else s < 0
            }
            return if (inMat) -best else best
        }
    }

    /** 경로를 0.004 mm 마다 재서 가장 작은 부호 거리. 부호 거리는 1-립시츠라 참값 ≥ 잰 값 − 0.002 */
    private fun minSd(pcs: List<Pc>, bnd: Bnd): Pair<Double, P> {
        var best = Double.POSITIVE_INFINITY
        var at = P(0.0, 0.0)
        for (pc in pcs) {
            val n = max(1, ceil(pc.length / 0.004).toInt())
            for (k in 0..n) {
                val p = pc.at(k.toDouble() / n)
                val s = bnd.sd(p)
                if (s < best) {
                    best = s
                    at = p
                }
            }
        }
        return best to at
    }

    private fun sampleOf(pc: Pc, step: Double): List<P> {
        val n = max(1, ceil(pc.length / step).toInt())
        return (0..n).map { pc.at(it.toDouble() / n) }
    }

    // ================================================================ 기하(이 파일만의 것)

    private data class P(val z: Double, val x: Double) {
        operator fun plus(o: P) = P(z + o.z, x + o.x)
        operator fun minus(o: P) = P(z - o.z, x - o.x)
        operator fun times(k: Double) = P(z * k, x * k)
        infix fun dot(o: P) = z * o.z + x * o.x
        infix fun cross(o: P) = z * o.x - x * o.z
        val len: Double get() = hypot(z, x)
        fun unit() = this * (1 / len)

        /** 반시계 90°(Z 오른쪽, X 위) */
        fun left() = P(-x, z)
        override fun toString() = "(Z${f4(z)} X${f4(2 * x)})"
    }

    private sealed class Pc {
        abstract val a: P
        abstract val b: P
        abstract val length: Double
        abstract fun at(t: Double): P
        abstract fun nearest(q: P): P
        abstract fun tanAt(p: P): P
        fun tanStart() = tanAt(a)
        fun tanEnd() = tanAt(b)
    }

    private class Ln(override val a: P, override val b: P) : Pc() {
        private val d = b - a
        override val length = d.len
        override fun at(t: Double) = a + d * t
        override fun nearest(q: P): P {
            val l2 = d dot d
            if (l2 < 1e-24) return a
            return a + d * (((q - a) dot d) / l2).coerceIn(0.0, 1.0)
        }
        override fun tanAt(p: P) = d.unit()
    }

    /** a → b, 중심 c, cw = 시계(G02) */
    private class Ac(override val a: P, override val b: P, val c: P, val cw: Boolean) : Pc() {
        val rad = (a - c).len
        private val a0 = ang(a - c)
        private val dir = if (cw) -1.0 else 1.0
        val sweep = if ((a - b).len < 1e-12) 0.0 else wrap(dir * (ang(b - c) - a0))
        override val length = rad * sweep
        override fun at(t: Double): P {
            val f = a0 + dir * sweep * t
            return c + P(cos(f), sin(f)) * rad
        }
        override fun nearest(q: P): P {
            if (rad < 1e-12 || (q - c).len < 1e-12) return a
            if (wrap(dir * (ang(q - c) - a0)) <= sweep + 1e-12) return c + (q - c).unit() * rad
            return if ((q - a).len <= (q - b).len) a else b
        }
        override fun tanAt(p: P) = (p - c).unit().left() * dir
    }

    private class Rep(val name: String) {
        var cases = 0
        var solved = 0
        var worstGouge = Double.NEGATIVE_INFINITY
        var worstLeft = 0.0
        val stops = mutableMapOf<String, Int>()
        val infos = mutableMapOf<String, Int>()
        val bads = linkedMapOf<String, MutableList<String>>()
        fun stop(w: String) {
            stops[w] = (stops[w] ?: 0) + 1
        }
        fun info(t: String) {
            infos[t] = (infos[t] ?: 0) + 1
        }
        val worst = mutableMapOf<String, Pair<Double, String>>()
        fun bad(t: String, msg: String, size: Double = 0.0) {
            bads.getOrPut(t) { mutableListOf() } += msg
            if (size > (worst[t]?.first ?: Double.NEGATIVE_INFINITY)) worst[t] = size to msg
        }
        fun finish() {
            val sb = StringBuilder()
            sb.appendLine("[$name] 사례 $cases, 계산됨 $solved, 가장 깊은 파고듦 ${f4(worstGouge)} mm(음수 = 여유), 가장 큰 남김 ${f4(worstLeft)} mm")
            sb.appendLine("  멈춤: ${stops.entries.sortedBy { it.key }}")
            for ((k, v) in infos.entries.sortedBy { it.key }) sb.appendLine("  참고 $k: $v")
            for ((k, v) in bads.entries.sortedBy { it.key }) {
                sb.appendLine("  ${if (WARNED in k) "알림(경고와 함께)" else "실패"} $k: ${v.size}")
                for (m in v.take(2)) sb.appendLine("    - $m")
                worst[k]?.let { if (it.first > 0) sb.appendLine("    - 가장 큰 것: ${it.second}") }
            }
            println(esc(sb.toString()))
            // 바이트 반대(AGAINST_TOOL) 경고가 함께 난 사례의 G41/G42 흉내 결과는 알리기만 한다 — 화면이 이미 "바이트와 반대" 를 띄우고,
            // 그 가운데 뒤집기 것은 화면이 만들지 않는 계산기 역방향 + 뒤집기뿐이다(뒤집기 정방향은 0 건)
            assertTrue(bads.keys.none { WARNED !in it }, esc(sb.toString()))
            assertTrue(bads.keys.none { it.startsWith("뒤집기·정") }, esc(sb.toString()))
        }
    }

    private companion object {
        val BOOLS = listOf(false, true)

        /** 파고듦·남김 허용(NC 0.001 반올림 + 0.004 mm 간격 재기) */
        const val TOL = 2e-3

        const val WARNED = "(바이트반대 경고 있음)"

        /** 팁 번호 → 노즈 중심에서 가상 인선으로(Δz, Δx). Haas 표 */
        fun tipVec(t: Int, r: Double) = when (t) {
            1 -> P(r, r)
            2 -> P(-r, r)
            3 -> P(-r, -r)
            4 -> P(r, -r)
            else -> error("팁 $t")
        }

        fun ang(v: P) = atan2(v.x, v.z)
        fun wrap(a: Double): Double {
            val t = a % (2 * PI)
            return if (t < 0) t + 2 * PI else t
        }

        fun lineMeet(p1: P, d1: P, p2: P, d2: P): P? {
            val den = d1 cross d2
            if (abs(den) < 1e-12) return null
            return p1 + d1 * (((p2 - p1) cross d2) / den)
        }

        fun lineCircle(p: P, d: P, c: P, rr: Double): List<P> {
            val u = d.unit()
            val w = p - c
            val bq = w dot u
            val disc = bq * bq - ((w dot w) - rr * rr)
            if (disc < -1e-12) return emptyList()
            val s = sqrt(max(0.0, disc))
            return listOf(p + u * (-bq - s), p + u * (-bq + s))
        }

        fun circleCircle(c1: P, r1: Double, c2: P, r2: Double): List<P> {
            val d = (c2 - c1).len
            if (d < 1e-12) return emptyList()
            val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
            val h2 = r1 * r1 - a * a
            if (h2 < -1e-12) return emptyList()
            val u = (c2 - c1).unit()
            val m = c1 + u * a
            val h = sqrt(max(0.0, h2))
            return listOf(m + u.left() * h, m - u.left() * h)
        }

        /** 0.001 반올림 고정 셋째 자리 */
        fun f3(v: Double): String {
            val n = (abs(v) * 1000).roundToLong()
            return (if (v < 0 && n != 0L) "-" else "") + (n / 1000) + "." + (n % 1000).toString().padStart(3, '0')
        }

        fun f4(v: Double) = ((v * 10000).roundToLong() / 10000.0).toString()
        fun f0(v: Double) = v.roundToLong().toString()

        /** 테스트 출력은 콘솔 인코딩에서 한글이 깨진다 — ASCII 밖 글자는 백슬래시 u XXXX 로 */
        fun esc(s: String) = buildString {
            for (ch in s) if (ch.code < 128) append(ch) else append("\\u" + ch.code.toString(16).padStart(4, '0'))
        }

        /** 주석의 노즈R 표기("0.4", "1.2", 정수면 "1") */
        fun nc(v: Double): String = ((v * 1000).roundToLong() / 1000.0).toString().removeSuffix(".0")
    }
}
