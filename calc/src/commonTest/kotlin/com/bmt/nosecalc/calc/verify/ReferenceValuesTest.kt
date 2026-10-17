package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.NoseR
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TriangleInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.fail

/**
 * 독립 검증 ② — 외부 기준값.
 *
 * 공작기계 회사 교재(Haas, 나카무라토메), 교과서(Goodheart-Willcox), 일본·한국 현장 풀이에 나온 입력과 답을 옮겨
 * 계산기와 맞춘다. 허용치는 출처가 적은 자릿수로 정했다(테스트마다 주석). 출처 안에서 서로 어긋나는 칸(오타)은
 * 그 근거를 적고 뺐다 — 구현 값을 보고 뺀 것은 없다.
 *
 * 공통 약속 맞추기(구현: Z 오른쪽 +, X 위 +, 각도는 Z 축에서 잼, 외경 정방향 = 팁 3·G42):
 * - 외부 자료의 각도도 모두 Z 축에서 잰 각이다. Haas 표 15° 의 Z 보정 .0271 = (1/32)(1 − tan7.5°) 라서 그렇다
 *   (X 축에서 잰 각이면 (1/32)(1 − tan37.5°) = .0073 이어야 한다).
 * - 보정 방향: 외경 팁 3 이면 수평 이웃 꼭짓점은 Z−, 단면 이웃 꼭짓점은 X− 로 옮긴다
 *   (Haas O00055 주석 "adding Z compensation amount" → Z 가 더 음수, "subtracting X compensation amount").
 *   볼록·오목 모서리 모두 크기가 같다(O00055 에 둘 다 있다).
 */
class ReferenceValuesTest {

    /**
     * Haas ST/TL 프로그래밍 워크북 55쪽 "TOOL NOSE RADIUS CALCULATION DIAGRAM" 표(PDF 60쪽).
     * https://haascnc.com/content/dam/haascnc/videos/bonus-content/g71-essential-tips-tod/lathe---programming-workbook.pdf
     *
     * 그림은 외경 단면 → 면취 → 외경(= TaperCalc 외경 TYPE 1)이다. Z-OFFSET 은 면취가 외경(수평)과 만나는 꼭짓점,
     * X-OFFSET 은 면취가 단면과 만나는 꼭짓점에 그려져 있다.
     * - ".015 TNR"·".031 TNR" 은 1/64·1/32 인치다(본문: "for 1/32 and 1/64 insert radiuses"). 0.031 로 풀면
     *   45° Z 가 0.031 × 0.585786 = .01816 → .0182 로 표와 다르다. 1/32 = 0.03125 로 풀면 .018306 → .0183 으로 맞는다.
     * - X-OFFSET 은 직경값이다: 45° 에서 X = 2 × Z (.0366 = 2 × .0183).
     * - 반올림: Z 는 넷째 자리라 허용 0.00005. X 는 반경값을 넷째 자리로 반올림한 뒤 두 배 한 값이다.
     *   예: 1/64·45° 는 반경 .009153 → .0092 → .0184 (참값 .018306), 1/64·15° 는 반경 r(1 − tan37.5°) = .003636
     *   → .0036 → .0072 (참값 .007271). 그래서 X 허용치는 2 × 0.00005 = 0.0001.
     */
    @Test
    fun haasWorkbookChamferTable() {
        val ck = Check("Haas 워크북 55쪽 표")
        // 각도, X .015, Z .015, X .031, Z .031 (표 그대로)
        val rows = listOf(
            doubleArrayOf(15.0, .0072, .0136, .0146, .0271),
            doubleArrayOf(30.0, .0132, .0114, .0264, .0229),
            doubleArrayOf(45.0, .0184, .0092, .0366, .0183),
            doubleArrayOf(60.0, .0228, .0066, .0458, .0132),
        )
        for (row in rows) {
            val deg = row[0]
            for ((r, xOff, zOff) in listOf(Triple(1.0 / 64, row[1], row[2]), Triple(1.0 / 32, row[3], row[4]))) {
                val tag = "${deg}° r=$r"
                val s = taper(ck, tag, TaperInput(type = 1, noseR = r, x1 = 0.75, z1 = 0.0, z2 = -0.25, angleDeg = deg, end = -0.5))
                    ?: continue
                ck.near("$tag T1 ΔX(직경)", -xOff, s.at(0).dXDia, 0.0001)
                ck.near("$tag T1 ΔZ", 0.0, s.at(0).dZ, 0.0)
                ck.near("$tag T2 ΔZ", -zOff, s.at(1).dZ, 0.00005)
                ck.near("$tag T2 ΔX", 0.0, s.at(1).dXDia, 0.0)
                ck.near("$tag NoseR.taperDeltaZ", zOff, NoseR.taperDeltaZ(r, deg), 0.00005)
                ck.near("$tag NoseR.taperDeltaXDia", xOff, NoseR.taperDeltaXDia(r, deg), 0.0001)
            }
        }
        ck.done()
    }

    /**
     * Haas 워크북 56쪽 O00055 "MANUALLY ADDING RADIUS COMPENSATION FOR ANGLES MOVES EXAMPLE"(PDF 61쪽, 주소는 위).
     * 외경 공구, 노즈 .031 TNR(= 1/32: 보정량 .0183·.0366 이 위 표의 1/32 값이다), 손으로 보정한 프로그램.
     *
     * 도면(그림 치수 .250×45° · .250 · 1.250 · .300×45° · 2.375 · Ø1.250 · Ø3.000 을 프로그램과 맞춰 읽음):
     *   끝면 Z0 → Ø.75 → 45° 면취 → Ø1.25 Z−.25 → Ø1.25 를 Z−1.0 까지 → 45° 면취 → Ø1.75 Z−1.25
     *   → 어깨 단면 Z−1.25 를 Ø2.4 까지 → 45° 면취 → Ø3.0 Z−1.55 → Ø3.0 을 Z−2.375 까지.
     * 프로그램: N7 X.7134 Z0 / N9 X1.250 Z−.2683 / N10 Z−1.0183 / N11 X1.7134 Z−1.250 / N12 X2.3634 / N13 X3.0 Z−1.5683 / N14 Z−2.375.
     * 손 풀이(반경, r = 1/32): 볼록 단면→면취 꼭짓점 T 는 노즈 중심 = (r, x + r(√2 − 1)) → 인선 = 중심 + (−r, −r)
     *   = (0, x − r(2 − √2)) → 직경 .75 − .0366 = .7134. 오목 외경→면취(Z−1.0)는 중심 (−1 + r(√2 − 1), .625 + r)
     *   → 인선 Z = −1 − r(2 − √2) = −1.0183. 나머지도 같은 식이다.
     * 마지막 점 Z−2.375 는 꼭짓점이 아니라 열린 끝이다. Haas 는 인선을 도면 끝 Z 에 멈춘다(노즈 접점은 r 만큼 덜 감).
     * NoseComp 만으로는 노즈 접점을 끝에 맞춰 인선이 Z−2.375 − r 로 가므로 X 만 보고, 테이퍼 계산기의 직접 보정 NC 는
     * 끝점 Z−2.375 를 넣으면 Haas 처럼 인선을 끝좌표에 멈춰야 한다(N14 Z-2.375).
     * 허용치: 넷째 자리 반올림 0.00005.
     */
    @Test
    fun haasWorkbookAngleProgram() {
        val ck = Check("Haas O00055")
        val r = 1.0 / 32
        val drawn = listOf(
            Pt.ofDia(0.0, 0.0), Pt.ofDia(0.0, 0.75), Pt.ofDia(-0.25, 1.25), Pt.ofDia(-1.0, 1.25),
            Pt.ofDia(-1.25, 1.75), Pt.ofDia(-1.25, 2.4), Pt.ofDia(-1.55, 3.0), Pt.ofDia(-2.375, 3.0),
        )
        val cp = NoseComp.tipPath(Contour.polyline(*drawn.toTypedArray()), r, Side.of(false, false), NoseComp.standardTip(false, false))
        if (cp.warnings.isNotEmpty()) ck.bad += "경고 ${cp.warnings}"
        // segs[k].end 에 대응하는 Haas 블록 (X 직경, Z)
        val haas = listOf(
            "N7·N8" to (0.7134 to 0.0), "N9" to (1.250 to -0.2683), "N10" to (1.25 to -1.0183),
            "N11" to (1.7134 to -1.250), "N12" to (2.3634 to -1.25), "N13" to (3.0 to -1.5683),
        )
        for ((k, e) in haas.withIndex()) {
            val p = cp.path.segs[k].end
            ck.near("${e.first} X", e.second.first, p.xDia, 0.00005)
            ck.near("${e.first} Z", e.second.second, p.z, 0.00005)
        }
        ck.near("N14 X", 3.0, cp.path.segs[6].end.xDia, 0.00005)

        // 같은 면취 셋을 테이퍼 계산기로: 첫째·셋째는 단면→면취→외경(TYPE 1), 둘째는 외경→면취→어깨(TYPE 4)
        val pieces = listOf(
            Triple(TaperInput(type = 1, noseR = r, x1 = 0.75, z1 = 0.0, x2 = 1.25, z2 = -0.25, end = -0.5), 0.7134 to 0.0, 1.25 to -0.2683),
            Triple(TaperInput(type = 4, noseR = r, x1 = 1.25, z1 = -1.0, x2 = 1.75, z2 = -1.25, end = 2.25), 1.25 to -1.0183, 1.7134 to -1.25),
            Triple(TaperInput(type = 1, noseR = r, x1 = 2.4, z1 = -1.25, x2 = 3.0, z2 = -1.55, end = -2.375), 2.3634 to -1.25, 3.0 to -1.5683),
        )
        for ((n, pc) in pieces.withIndex()) {
            val s = taper(ck, "면취 ${n + 1}", pc.first) ?: continue
            for ((j, e) in listOf(pc.second, pc.third).withIndex()) {
                ck.near("면취 ${n + 1} T${j + 1}′ X", e.first, s.at(j).prog.xDia, 0.00005)
                ck.near("면취 ${n + 1} T${j + 1}′ Z", e.second, s.at(j).prog.z, 0.00005)
            }
            if (n == 2) ck.same("N14 끝", "G01 Z-2.375", s.ncG40[s.ncG40.size - 2])
        }
        ck.done()
    }

    /**
     * Haas 워크북 53쪽 O00052 "MANUALLY ADDING RADIUS COMPENSATION FOR CIRCULAR MOVES EXAMPLE"(PDF 59쪽).
     * 외경 공구 .031 TNR — 여기는 0.031 그대로 썼다(R0.151 = .12 + .031, R0.209 = .24 − .031, R0.391 = .36 + .031).
     * 51~52쪽 본문: 볼록 모서리 R 은 TNR 을 더하고, 오목(fillet) R 은 뺀다.
     *
     * 도면: Ø1.25 끝 모서리 R.120(볼록), Ø1.25 와 어깨(Z−1.13) 사이 R.240(오목), Ø3.0 모서리 R.360(볼록), 전체 2.375.
     * 프로그램(I·K 판): N106 X.948(인쇄는 "X0.048" — 같은 쪽 R 판 N206 이 X0.948 이고 I0 K−.151 로 X1.25 에 닿으려면
     *   .948 이어야 해서 오타로 봄) / N107 G03 X1.25 Z−.151 I0 K−.151 / N108 Z−.921 / N109 G02 X1.668 Z−1.13 I.209 K0
     *   / N110 X2.218 / N111 G03 X3. Z−1.521 I0 K−.391 / N112 Z−2.375. I 는 반경값(시작점 → 중심).
     * R 판 N209 는 X1.667 로 I·K 판 X1.668 과 다르다. 오목 원호 끝 = 노즈 중심 (−1.099, .865) + (−.031, −.031) =
     *   (−1.13, .834) → 직경 1.668 이 맞아서 1.668 을 기대값으로 했다.
     * 손 풀이: 볼록 R.12 의 노즈 중심 원 = 중심 (−.12, .505), 반지름 .151. 시작(법선 +Z) (.031, .505) → 인선 (0, .474)
     *   → X.948 Z0. 끝(법선 +X) (−.12, .656) → 인선 (−.151, .625) → X1.25 Z−.151. 인선 원호 중심 = (−.151, .474).
     * 열린 끝 Z−2.375 는 O00055 와 같은 까닭으로 X 만 본다. 허용치: 셋째 자리 0.0005.
     */
    @Test
    fun haasWorkbookRadiusProgram() {
        val ck = Check("Haas O00052")
        val r = 0.031
        val c = Contour(
            listOf(
                Line(Pt.ofDia(0.0, 0.9), Pt.ofDia(0.0, 1.01)),
                Arc(Pt.ofDia(0.0, 1.01), Pt.ofDia(-0.12, 1.25), Pt.ofDia(-0.12, 1.01), cw = false),
                Line(Pt.ofDia(-0.12, 1.25), Pt.ofDia(-0.89, 1.25)),
                Arc(Pt.ofDia(-0.89, 1.25), Pt.ofDia(-1.13, 1.73), Pt.ofDia(-0.89, 1.73), cw = true),
                Line(Pt.ofDia(-1.13, 1.73), Pt.ofDia(-1.13, 2.28)),
                Arc(Pt.ofDia(-1.13, 2.28), Pt.ofDia(-1.49, 3.0), Pt.ofDia(-1.49, 2.28), cw = false),
                Line(Pt.ofDia(-1.49, 3.0), Pt.ofDia(-2.375, 3.0)),
            ),
        )
        val cp = NoseComp.tipPath(c, r, Side.of(false, false), NoseComp.standardTip(false, false))
        if (cp.warnings.isNotEmpty()) ck.bad += "경고 ${cp.warnings}"
        val tol = 0.0005
        fun end(tag: String, k: Int, x: Double, z: Double) {
            ck.near("$tag X", x, cp.path.segs[k].end.xDia, tol)
            ck.near("$tag Z", z, cp.path.segs[k].end.z, tol)
        }
        fun arc(tag: String, k: Int, cw: Boolean, radius: Double, cz: Double, cx: Double) {
            val a = cp.path.segs[k] as? Arc
            if (a == null) {
                ck.bad += "$tag: 원호가 아니다 ${cp.path.segs[k]}"
                return
            }
            if (a.cw != cw) ck.bad += "$tag: 방향 G0${if (cw) 2 else 3} 이어야 한다"
            ck.near("$tag R", radius, a.radius, tol)
            ck.near("$tag 중심 Z(K)", cz, a.center.z, tol)
            ck.near("$tag 중심 X(I)", cx, a.center.xDia, tol)
        }
        end("N106", 0, 0.948, 0.0)
        end("N107", 1, 1.25, -0.151)
        arc("N107", 1, cw = false, radius = 0.151, cz = -0.151, cx = 0.948)
        end("N108", 2, 1.25, -0.921)
        end("N109", 3, 1.668, -1.13)
        arc("N109", 3, cw = true, radius = 0.209, cz = -0.921, cx = 1.668)
        end("N110", 4, 2.218, -1.13)
        end("N111", 5, 3.0, -1.521)
        arc("N111", 5, cw = false, radius = 0.391, cz = -1.521, cx = 2.218)
        ck.near("N112 X", 3.0, cp.path.segs[6].end.xDia, tol)

        // 원호 하나씩 3점 모드로: 이웃이 접해서 이어지므로 끝점 보정이 위와 같다. Haas R 판 줄과 글자까지 맞춘다.
        // (시작 X·Z, 중심 X·Z(직경), 반지름, 가운데 점 각, 첫 보정점, 기대 원호 줄)
        data class A3(val sz: Double, val sx: Double, val ez: Double, val ex: Double, val cz: Double, val cx: Double,
                      val rad: Double, val midDeg: Double, val first: Pair<Double, Double>, val line: String)
        val arcs = listOf(
            A3(0.0, 1.01, -0.12, 1.25, -0.12, 1.01, 0.12, 45.0, 0.948 to 0.0, "G03 X1.25 Z-0.151 R0.151"), // N207
            A3(-0.89, 1.25, -1.13, 1.73, -0.89, 1.73, 0.24, -135.0, 1.25 to -0.921, "G02 X1.668 Z-1.13 R0.209"), // N209(X 는 위 설명)
            A3(-1.13, 2.28, -1.49, 3.0, -1.49, 2.28, 0.36, 45.0, 2.218 to -1.13, "G03 X3. Z-1.521 R0.391"), // N211
        )
        for (a in arcs) {
            val t = a.midDeg * PI / 180
            val res = RoundCalc.arc3(
                Arc3Input(
                    noseR = r, x1 = a.sx, z1 = a.sz, x2 = a.cx + 2 * a.rad * sin(t), z2 = a.cz + a.rad * cos(t),
                    x3 = a.ex, z3 = a.ez,
                ),
            )
            val s = res.solution
            if (s == null) {
                ck.bad += "3점 ${a.line}: 멈춤 ${res.warnings}"
                continue
            }
            ck.near("3점 ${a.line} 첫 보정점 X", a.first.first, s.nodes[0].prog.xDia, tol)
            ck.near("3점 ${a.line} 첫 보정점 Z", a.first.second, s.nodes[0].prog.z, tol)
            ck.same("3점 NC 원호 줄", a.line, s.nc.last())
        }
        ck.done()
    }

    /**
     * 「NC旋盤のバイブル」 ノーズR補正一覧表 (일본 현장 블로그, 각도 5°~85° × 노즈R 0.1~1.2).
     * https://senban.mmbible.com/lathe/3092/
     * 표의 X 는 반경값으로 읽었다: 45° 에서 X = Z(0.234315 = 0.4(1 − tan22.5°)), 같은 45° 를 나카무라토메 표는
     * 직경으로 X 0.468 = 2 × Z 라 적는다. 식은 Z = r(1 − tan(θ/2)), X = r(1 − tan((90° − θ)/2)) 와 표가 맞는다.
     *
     * 출처 오타 두 칸은 뺐다. 표 스스로의 규칙과 어긋난다: 같은 줄은 r 에 비례해야 하고, X(θ) = Z(90° − θ) 여야 한다.
     * - 15° X R0.4 = 0.0465345: 왼쪽 칸(R0.2)을 그대로 베꼈다. 같은 표 75° Z R0.4 는 0.093069.
     * - 30° X R0.2 = 0.042265: 왼쪽 칸(R0.1)을 그대로 베꼈다. 같은 표 60° Z R0.2 는 0.08453.
     * 허용치: 표가 5~7자리라 0.000005(직경은 두 배).
     *
     * 한 표로 꺾임 다섯 가지를 본다. 크기는 표 값이고 방향은 다음과 같다.
     * - 외경 정방향 TYPE 1(단면→면취→외경, 볼록 둘), TYPE 4(외경→테이퍼→어깨, 오목 둘), TYPE 3(이웃이 둘 다 수평).
     * - 내경 정방향 TYPE 1: 외경을 X 로 뒤집은 그림이다(재료 위, 팁 3(−r,−r) → 팁 2(−r,+r), G42 → G41). Z 는 같고 X 는 부호만 반대다.
     * - 외경 역방향 TYPE 5: 외경 정방향 TYPE 3 을 Z 로 뒤집은 그림이다(팁 3 → 팁 4(+r,−r), G42 → G41). 그래서 ΔZ 의 부호가 반대다.
     */
    @Test
    fun nipponBlogTable() {
        val ck = Check("NC旋盤のバイブル 표")
        val typos = setOf(15 to 0.4, 30 to 0.2)
        // 오타라고 본 근거를 표 자료로 확인: 왼쪽 칸과 같고, 대칭 칸과 다르다
        for ((deg, r) in typos) {
            val xs = MM_TABLE.first { it.first == deg }.second.first
            val zSym = MM_TABLE.first { it.first == 90 - deg }.second.second
            val i = MM_R.indexOf(r)
            if (xs[i] != xs[i - 1] || abs(zSym[i] - xs[i]) < 1e-3) ck.bad += "$deg° X R$r 를 오타로 본 근거가 표와 다르다"
        }
        for ((deg, xz) in MM_TABLE) {
            val (xs, zs) = xz
            for ((i, r) in MM_R.withIndex()) {
                val xOk = (deg to r) !in typos
                val x = xs[i]
                val z = zs[i]
                val d = deg.toDouble()
                val tag = "$deg° R$r"
                ck.near("$tag NoseR.taperDeltaZ", z, NoseR.taperDeltaZ(r, d), 0.000005)
                if (xOk) ck.near("$tag NoseR.taperDeltaXDia", 2 * x, NoseR.taperDeltaXDia(r, d), 0.00001)
                // (구성, 입력, T1 기대 (ΔX직경, ΔZ), T2 기대) — null 은 X 오타 칸이라 그 축을 안 본다
                val cases = listOf(
                    Triple("외경 정 TYPE 1", TaperInput(type = 1, noseR = r), listOf(-2 * x to 0.0, 0.0 to -z)),
                    Triple("외경 정 TYPE 4", TaperInput(type = 4, noseR = r), listOf(0.0 to -z, -2 * x to 0.0)),
                    Triple("외경 정 TYPE 3", TaperInput(type = 3, noseR = r), listOf(0.0 to -z, 0.0 to -z)),
                    Triple("내경 정 TYPE 1", TaperInput(inner = true, type = 1, noseR = r), listOf(2 * x to 0.0, 0.0 to -z)),
                    Triple("외경 역 TYPE 5", TaperInput(type = 5, reverse = true, noseR = r), listOf(0.0 to z, 0.0 to z)),
                )
                for ((name, base, exp) in cases) {
                    val usesX = exp.any { it.first != 0.0 }
                    if (usesX && !xOk) continue
                    val s = taper(ck, "$name $tag", base.copy(x1 = 200.0, z1 = 0.0, z2 = -5.0, angleDeg = d)) ?: continue
                    for (j in 0..1) {
                        ck.near("$name $tag T${j + 1} ΔX(직경)", exp[j].first, s.at(j).dXDia, if (exp[j].first == 0.0) 1e-9 else 0.00001)
                        ck.near("$name $tag T${j + 1} ΔZ", exp[j].second, s.at(j).dZ, if (exp[j].second == 0.0) 1e-9 else 0.000005)
                    }
                }
            }
        }
        ck.done()
    }

    /**
     * 나카무라토메(中村留精密工業) 「ノーズR補正の考え方は？NC旋盤のプログラムを解説【第三弾】」.
     * https://www.nakamura-tome.co.jp/2021/01/13/article_00008/
     * 표 「C面(45°)の場合の補正値」(그림 correction-value-table.png): 刃先R 0.2 → X 0.234 Z 0.117, 0.4 → X 0.468 Z 0.234,
     * 0.8 → X 0.936 Z 0.468. 본문: "45°では、XとZに2倍の関係… 刃先R0.2のZ軸の補正値を１つ覚えておけば、倍々の関係で…".
     * - X 는 직경값이다(X = 2Z). 그림(Coordinate-Point-Calculate.png)은 외경 단면 → C면 → 외경이라 TYPE 1 이다.
     * - 표는 0.117(참값 0.1171573 을 셋째 자리로 반올림)을 m 배 해서 만들었다(R0.4 X = 4 × 0.117 = 0.468, 참값 0.46863).
     *   그래서 허용치를 m × 0.0005 로 둔다(m = 칸 값 / 0.117). R0.2 칸은 반올림 그대로라 Fmt 글자까지 같아야 한다.
     *
     * 같은 회사 「座標計算の求め方は？【第二弾】」(https://www.nakamura-tome.co.jp/2020/12/17/article_00006/) 에서 두 가지를 더 본다.
     * - φ30 C1 을 Z 로 2mm 늘인 진입점 = X24.0 Z2.0 (ポイント2: "φ30-2mm-4mmでφ24 … Z2.0"). 노즈R 0 의 TYPE 1 진입점과 같아야 한다
     *   (구현 약속: 첫 꺾임이 볼록이면 경사선을 Z 로 zClear 바깥까지 늘인 점).
     * - "G01X-0.8F0.1（刃先R分マイナスの領域まで加工）" — 刃先R0.4 チップ(第三弾 예제)로 끝면을 중심까지 깎을 때 X−0.8 = −2r.
     *   단면을 중심 쪽으로 깎으면 G41(설계 문서), 팁 3: 노즈 중심이 축에 닿을 때 인선 X = −2r.
     *   라운드R TYPE 3(돔 끝이 축 위) 첫 보정점도 같은 까닭으로 X−0.8 이어야 한다.
     */
    @Test
    fun nakamuraTome() {
        val ck = Check("나카무라토메")
        for ((r, xDia, z) in listOf(Triple(0.2, 0.234, 0.117), Triple(0.4, 0.468, 0.234), Triple(0.8, 0.936, 0.468))) {
            val s = taper(ck, "C1 R$r", TaperInput(type = 1, noseR = r, x1 = 28.0, z1 = 0.0, x2 = 30.0, z2 = -1.0)) ?: continue
            ck.near("C45 R$r X(직경)", xDia, -s.at(0).dXDia, (xDia / 0.117) * 0.0005)
            ck.near("C45 R$r Z", z, -s.at(1).dZ, (z / 0.117) * 0.0005)
            if (r == 0.2) {
                ck.same("C45 R0.2 X 글자", "0.234", Fmt.fixed3(-s.at(0).dXDia))
                ck.same("C45 R0.2 Z 글자", "0.117", Fmt.fixed3(-s.at(1).dZ))
            }
        }
        taper(ck, "진입점", TaperInput(type = 1, noseR = 0.0, x1 = 28.0, z1 = 0.0, x2 = 30.0, z2 = -1.0, zClear = 2.0))?.let {
            ck.near("ポイント2 X", 24.0, it.entry.xDia, 0.0005)
            ck.near("ポイント2 Z", 2.0, it.entry.z, 0.0005)
            ck.same("ポイント2 NC", "G00 X24. Z2.", it.ncG40.first())
        }
        val face = NoseComp.tipPath(Contour.polyline(Pt.ofDia(0.1, 32.0), Pt.ofDia(0.1, 0.0)), 0.4, Side.G41, 3)
        ck.near("끝면 X−0.8", -0.8, face.path.segs[0].end.xDia, 0.0005)
        ck.near("끝면 Z 그대로", 0.1, face.path.segs[0].end.z, 0.0005)
        val dome = RoundCalc.calc(RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0, noseR = 0.4)).solution
        if (dome == null) ck.bad += "라운드R TYPE 3 멈춤" else ck.near("돔 첫 보정점 X−0.8", -0.8, dome.nodes[0].prog.xDia, 0.0005)
        ck.done()
    }

    /**
     * Yahoo!知恵袋 q14151811364 (2015-10, 베스트 답): "30度の角度でノーズR0.8" 의 손 보정.
     * https://detail.chiebukuro.yahoo.co.jp/qa/question_detail/q14151811364
     * Zc = Rn{1 − tan(θ/2)} = 0.8 × {1 − tan15°} = 0.5856, Xc = Zc·tanθ = 0.3381, "Xc・２にします 0.3381×２＝0.6762".
     * (Zc·tanθ = r(1 − tan(45° − θ/2)) 는 같은 식이다: t = tan(θ/2) 이면 둘 다 2rt/(1 + t).)
     * 도면이 없어 TYPE 1 꺾임(T1 단면 이웃 → X, T2 수평 이웃 → Z)에 대어 본다. 허용치: 넷째 자리 0.00005.
     *
     * Yahoo!知恵袋 q11256485671 (2022-02, 베스트 답): 노즈R 0.4, 1C 면취 "X55 Z195 → X57 Z194 → Z60".
     * https://detail.chiebukuro.yahoo.co.jp/qa/question_detail/q11256485671
     * 끝점 "0.4-0.4tan(45°/2)=0.234" → "194-0.234⇒Z193.766" (X57). 시작점은 "(0.4-0.4cos45°)*2=0.234",
     * "0.4-0.4sin45°=0.117" → "X54.766", "Z194.883".
     * 약속 맞추기: 이 프로그램은 면취 앞에 단면이 없고 공중에서 G42 로 들어온다. 그래서 시작점은 노즈가 도면 시작점에
     * 면취 법선 방향으로 닿는 점이다. 구현 TYPE 1 의 T1′ 은 앞의 단면과 만나는 점(X54.531 Z195)이라 점은 다르다.
     * 두 점 모두 같은 보정 면취선 위에 있어야 하므로, 구현의 T1′–T2′ 선이 X54.766 Z194.883 을 지나는지 본다.
     * 허용치: 셋째 자리 반올림 — 선까지 거리 ≤ (0.0005 + 0.00025)/√2 ≈ 0.0006(반경).
     */
    @Test
    fun yahooChiebukuro() {
        val ck = Check("Yahoo!知恵袋")
        taper(ck, "30° R0.8", TaperInput(type = 1, noseR = 0.8, x1 = 200.0, z1 = 0.0, z2 = -5.0, angleDeg = 30.0))?.let {
            ck.near("30° R0.8 Xc·2", 0.6762, -it.at(0).dXDia, 0.00005)
            ck.near("30° R0.8 Zc", 0.5856, -it.at(1).dZ, 0.00005)
        }
        taper(ck, "1C R0.4", TaperInput(type = 1, noseR = 0.4, x1 = 55.0, z1 = 195.0, x2 = 57.0, z2 = 194.0))?.let {
            val a = it.at(0).prog
            val b = it.at(1).prog
            ck.near("끝점 X", 57.0, b.xDia, 0.0005)
            ck.near("끝점 Z", 193.766, b.z, 0.0005)
            val d = b - a
            val s = Pt.ofDia(194.883, 54.766)
            ck.near("시작점 X54.766 Z194.883 이 보정 면취선 위", 0.0, (d cross (s - a)) / d.length, 0.0006)
        }
        ck.done()
    }

    /**
     * Daum 카페 「CNC선반 손으로계산」 답글(묻고 답하고 - 밀링.머시닝센터).
     * https://m.cafe.daum.net/njw/5eps/361?svc=cafeapi
     * 외경 470, C2 면취, 바이트 R0.8: 0.8 × (1 − tan22.5°) = 0.8 × 0.586 = 0.468, "X 값은 항상 곱하기 2" →
     * X = 470 − (0.468 × 2 + 2 × 2) = 465.064. 글에 적힌 식 "tan(2/@)" 은 tan(@/2) 를 잘못 적은 것으로 읽었다
     * (계산에서 tan22.5° 를 썼다).
     * 이 값은 면취 시작점(Ø466, 앞은 단면)의 X 보정 = TYPE 1 T1′ 이다. 글은 0.4686 을 0.468 로 잘라 썼다.
     * 그 잘림의 직경 영향은 2 × 0.00063 = 0.0013 이다. 참값은 465.0627 이라 허용치를 0.0015 로 둔다.
     */
    @Test
    fun daumCafe() {
        val ck = Check("Daum 카페")
        taper(ck, "C2 R0.8", TaperInput(type = 1, noseR = 0.8, x1 = 466.0, z1 = 0.0, x2 = 470.0, z2 = -2.0))?.let {
            ck.near("C2 R0.8 시작점 X", 465.064, it.at(0).prog.xDia, 0.0015)
            ck.near("C2 R0.8 시작점 Z", 0.0, it.at(0).prog.z, 1e-9)
        }
        ck.done()
    }

    /**
     * 직각삼각형·테이퍼 빈 값 풀기.
     * ① 나카무라토메 「テーパー部分における座標計算を解説」 https://www.nakamura-tome.co.jp/2022/03/30/article_00052/
     *    시작 (X30.0, Z0), 15°, Z−10: "tan15°= b / 10", "b = 2.67949", "X = 30 + (2.67949 × 2)", "X = 35.359".
     *    그림(test.png)은 끝면 → 15° 테이퍼 → 단면(턱)이라 TYPE 2 로 넣었다(도면 값 풀이는 TYPE 과 무관).
     * ② Goodheart-Willcox 「CNC Machining」 19장 Figure 19-11 (https://www.g-w.com/assets/files/pdf/sampchap/9781637767023_Ch19.pdf):
     *    Ø2.000 → Ø6.619, 사이각 60° → 한쪽 30°. "This segment represents the opposite side … equal to 2.309",
     *    "2.309 ÷ .5774 = 4", 테이퍼 Z−2 → Z−6. 2.309 는 (6.619 − 2)/2 = 2.3095 를 줄인 값이라 밑변 허용치는
     *    0.0005 / tan30° ≈ 0.0009 → 0.001. 거꾸로 밑변 4·30° 에서 Ø6.619 가 나와야 한다(셋째 자리 글자).
     */
    @Test
    fun rightTriangle() {
        val ck = Check("직각삼각형")
        fun tri(tag: String, i: TriangleInput) = RightTriangle.calc(i).also { if (it.solution == null) ck.bad += "$tag 멈춤 ${it.warnings}" }.solution
        tri("15°", TriangleInput(a = 10.0, angleA = 15.0))?.let {
            ck.near("15° 높이 b", 2.67949, it.b, 0.000005)
            ck.near("15° 밑변 = Z 거리", 10.0, it.dz, 1e-9)
            ck.same("15° 끝점 X", "35.359", Fmt.fixed3(30.0 + it.dxDia))
        }
        taper(ck, "15° 테이퍼", TaperInput(type = 2, x1 = 30.0, z1 = 0.0, z2 = -10.0, angleDeg = 15.0))?.let {
            ck.same("15° 테이퍼 X2", "35.359", Fmt.fixed3(it.points[1].vertex.xDia))
        }
        tri("30° 높이로", TriangleInput(b = 2.309, angleA = 30.0))?.let { ck.near("30° 밑변", 4.0, it.a, 0.001) }
        tri("30° 밑변으로", TriangleInput(a = 4.0, angleA = 30.0))?.let { ck.same("30° 큰 지름", "6.619", Fmt.fixed3(2.0 + it.dxDia)) }
        taper(ck, "30° 테이퍼", TaperInput(type = 3, x1 = 2.0, z1 = -2.0, z2 = -6.0, angleDeg = 30.0))?.let {
            ck.same("30° 테이퍼 X2", "6.619", Fmt.fixed3(it.points[1].vertex.xDia))
        }
        ck.done()
    }

    private fun TaperSolution.at(k: Int) = points[k].nodes.single()

    /** 계산을 멈추면 사유를 남기고 null */
    private fun taper(ck: Check, tag: String, i: TaperInput): TaperSolution? {
        val res = TaperCalc.calc(i)
        if (res.solution == null) ck.bad += "$tag: 멈춤 ${res.warnings}"
        return res.solution
    }

    /** 어긋난 것을 다 모아 한 번에 실패시킨다 — 표 한 칸에서 멈추지 않고 전부 보고하게 */
    private class Check(val name: String) {
        val bad = mutableListOf<String>()

        fun near(what: String, expected: Double, actual: Double, tol: Double) {
            if (!(abs(actual - expected) <= tol + 1e-9)) bad += "$what: 기대 $expected, 실제 $actual (차 ${actual - expected}, 허용 $tol)"
        }

        fun same(what: String, expected: String, actual: String) {
            if (expected != actual) bad += "$what: 기대 \"$expected\", 실제 \"$actual\""
        }

        fun done() {
            if (bad.isNotEmpty()) fail("$name — ${bad.size}건\n" + bad.joinToString("\n"))
        }
    }

    private companion object {
        val MM_R = listOf(0.1, 0.2, 0.4, 0.5, 0.8, 1.0, 1.2)

        /** 각도 → (X 줄, Z 줄), 열은 [MM_R]. https://senban.mmbible.com/lathe/3092/ 표를 그대로 옮김(오타 포함) */
        val MM_TABLE: List<Pair<Int, Pair<List<Double>, List<Double>>>> = listOf(
            5 to (listOf(0.008367, 0.016734, 0.033468, 0.041834, 0.066936, 0.083668, 0.100404) to
                listOf(0.095634, 0.191268, 0.382536, 0.47817, 0.765072, 0.95634, 1.147608)),
            10 to (listOf(0.01609, 0.03218, 0.06436, 0.08045, 0.12872, 0.1609, 0.19308) to
                listOf(0.0912513, 0.1825025, 0.365005, 0.456256, 0.73001, 0.912512, 1.095015)),
            15 to (listOf(0.0232673, 0.0465345, 0.0465345, 0.116337, 0.186138, 0.232674, 0.279207) to
                listOf(0.0868348, 0.1736695, 0.347339, 0.434174, 0.694678, 0.868348, 1.042017)),
            20 to (listOf(0.0299793, 0.0599585, 0.119917, 0.149896, 0.239834, 0.299792, 0.359751) to
                listOf(0.0823673, 0.1647345, 0.329469, 0.411837, 0.658938, 0.823674, 0.988407)),
            25 to (listOf(0.036293, 0.072586, 0.145172, 0.181465, 0.290344, 0.36293, 0.435516) to
                listOf(0.0778305, 0.155661, 0.311322, 0.389153, 0.622644, 0.778306, 0.933966)),
            30 to (listOf(0.042265, 0.042265, 0.16906, 0.211325, 0.33812, 0.42265, 0.50718) to
                listOf(0.073205, 0.14641, 0.29282, 0.366025, 0.58564, 0.73205, 0.87846)),
            35 to (listOf(0.0479433, 0.0958865, 0.191773, 0.239716, 0.383546, 0.479432, 0.575319) to
                listOf(0.06847, 0.13694, 0.27388, 0.342351, 0.54776, 0.684702, 0.82164)),
            40 to (listOf(0.0533693, 0.1067385, 0.213477, 0.266846, 0.426954, 0.533692, 0.640431) to
                listOf(0.063603, 0.127206, 0.254412, 0.318015, 0.508824, 0.63603, 0.763236)),
            45 to (listOf(0.0585788, 0.1171575, 0.234315, 0.292893, 0.46863, 0.585786, 0.702945) to
                listOf(0.0585788, 0.1171575, 0.234315, 0.292893, 0.46863, 0.585786, 0.702945)),
            50 to (listOf(0.063603, 0.127206, 0.254412, 0.318015, 0.508824, 0.63603, 0.763236) to
                listOf(0.0533693, 0.1067385, 0.213477, 0.266846, 0.426954, 0.533692, 0.640431)),
            55 to (listOf(0.06847, 0.13694, 0.27388, 0.342351, 0.54776, 0.684702, 0.82164) to
                listOf(0.0479433, 0.0958865, 0.191773, 0.239716, 0.383546, 0.479432, 0.575319)),
            60 to (listOf(0.073205, 0.14641, 0.29282, 0.366025, 0.58564, 0.73205, 0.87846) to
                listOf(0.042265, 0.08453, 0.16906, 0.211325, 0.33812, 0.42265, 0.50718)),
            65 to (listOf(0.0778305, 0.155661, 0.311322, 0.389153, 0.622644, 0.778306, 0.933966) to
                listOf(0.036293, 0.072586, 0.145172, 0.181465, 0.290344, 0.36293, 0.435516)),
            70 to (listOf(0.0823673, 0.1647345, 0.329469, 0.411837, 0.658938, 0.823674, 0.988407) to
                listOf(0.0299793, 0.0599585, 0.119917, 0.149896, 0.239834, 0.299792, 0.359751)),
            75 to (listOf(0.0868348, 0.1736695, 0.347339, 0.434174, 0.694678, 0.868348, 1.042017) to
                listOf(0.0232673, 0.0465345, 0.093069, 0.116337, 0.186138, 0.232674, 0.279207)),
            80 to (listOf(0.0912513, 0.1825025, 0.365005, 0.456256, 0.73001, 0.912512, 1.095015) to
                listOf(0.01609, 0.03218, 0.06436, 0.08045, 0.12872, 0.1609, 0.19308)),
            85 to (listOf(0.095634, 0.191268, 0.382536, 0.47817, 0.765072, 0.95634, 1.147608) to
                listOf(0.008367, 0.016734, 0.033468, 0.041834, 0.066936, 0.083668, 0.100404)),
        )
    }
}
