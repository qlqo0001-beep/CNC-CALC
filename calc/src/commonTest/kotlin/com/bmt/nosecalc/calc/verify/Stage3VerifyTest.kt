package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.CutInput
import com.bmt.nosecalc.calc.CutWarning
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.DrillCatalog
import com.bmt.nosecalc.calc.DrillWarning
import com.bmt.nosecalc.calc.G76Calc
import com.bmt.nosecalc.calc.G76Input
import com.bmt.nosecalc.calc.G76Solution
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.PolarG12
import com.bmt.nosecalc.calc.PolarInput
import com.bmt.nosecalc.calc.PolarShape
import com.bmt.nosecalc.calc.PolarSolution
import com.bmt.nosecalc.calc.PolarWarning
import com.bmt.nosecalc.calc.Span3
import com.bmt.nosecalc.calc.SpeedFrom
import com.bmt.nosecalc.calc.ThreadClass
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadSeries
import com.bmt.nosecalc.calc.ThreadSpec
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.ThreadTol
import com.bmt.nosecalc.calc.WorkMaterials
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * 독립 검증 — 3단계(나사 G76 · 극좌표 G12.1 · 절삭조건). 구현과 **다른 출처 · 다른 방법**으로 본다. 구현 코드는 고치지 않았다.
 *
 * 1. 나사
 *  - 규격 한계 치수: 구현 테스트(amesweb · saolamfg · RoyMech)와 다른 공개 표 —
 *    미터 6g/6H = ASME B1.13M-1995 표(Engineers Edge "Metric External/Internal Thread Sizes",
 *    https://www.engineersedge.com/hardware/metric-external-thread-sizes1.htm · -sizes2 · metric-internal-thread-sizes0~2),
 *    그 표가 맞는지는 ISO 965-2:1973 미리보기(https://cdn.standards.iteh.ai/samples/2866/71172a0b1ab24e9b83a6e723b5fd79e9/ISO-965-2-1973.pdf
 *    표 1~4, 읽히는 줄)로 맞춰 봤다(`publishedTablesAgree`).
 *    UN = NBS Handbook H28 (1969) Part I 표 2.21(https://nvlpubs.nist.gov/nistpubs/Legacy/hb/nbshandbook28-1969p1.pdf, 1A~3B 전부)과
 *    ASME B1.1 표(Engineers Edge "ANSI External Screw Threads" https://www.engineersedge.com/screw_threads_chart.htm ·
 *    "ANSI Internal Screw Threads" https://www.engineersedge.com/thread_strength/internal_screw_threads_chart.htm).
 *    표의 오기는 근거를 적고 뺐다(아래 각 표 주석) — 구현 값을 보고 뺀 것은 없다.
 *    더한 것(2차): 기초 편차 = ISO 965-1:1998 표 1(iTeh 미리보기) — 4h 6g 6h 8g 6H 6G 모든 피치, 6g 한계 = Optimas 표(ASME 표에 없는
 *    M1.6 · M2 · M3.5 · M7 · M14 · M18 · M22 · M27 · M33 · M39 와 가는 나사), 인선 R > H/3, 회전수 · 분당 이송(Haas THREAD CHART
 *    "T.P.I. x 125 I.P.M. = RPM") · 역가공 · Z_ORDER, 멈춤 조건(d ≥ k · Δd < Δdmin · 암나사 시작 X ≤ 0 · 1000 회 넘음).
 *  - G76 예제: FANUC Series 0+-MODEL F Lathe 조작 설명서 B-64604EN-1/01 4.2.7 Fig. 4.2.7 (e) 와 같은 예의 FS10/11 형식(6.4.7),
 *    Haas ST/TL 프로그래밍 워크북 109~113쪽(O00113 · 나사 표 · "D" 값 표,
 *    https://haascnc.com/content/dam/haascnc/videos/bonus-content/g71-essential-tips-tod/lathe---programming-workbook.pdf),
 *    MHCC "Support for CNC" 교재 G76 장(D = K/√N, https://mhcc.pressbooks.pub/supportcnc/chapter/g76-threading-cycle/).
 *  - X · k: 구현의 "피치경 ∓ H ± 2R" 식을 쓰지 않고, 60° V 날(끝 R)의 단면 폭이 피치선에서 P/2 가 되는 깊이를 이분법으로 푼다
 *    (피치경 = 산과 홈 폭이 같은 지름).
 *  - 분석표: FANUC 4.2.7 의 글("n 번째 절입 Δd√n", "Δdmin 보다 작으면 Δdmin 으로", 정삭 여유 d, 정삭 m 회)대로 따로 짠 절입표와 비교.
 * 2. G12.1: 만든 NC 글을 이 파일의 해석기로 다시 읽는다(구현의 Pt · Seg · Contour 를 쓰지 않음). X 첫째 · C 둘째 축 평면,
 *    G02 = 시계, R 원호 중심은 짧은 호. 도형은 "변 k 의 바깥 법선 = X 에서 C 쪽으로 90° + 회전 + k·360°/n" 정의에서 따로 만든다.
 *    직접 보정(G40) 경로는 다 깎은 단면에서 공구 반경(파고듦 없음 + 둘레 전부 반경 안), 기계 보정(G41) 경로는 도면 위(공구 쪽은 진행 왼쪽)를 본다.
 *    더한 것(2차): 씨앗 고정 무작위 160 경우 + 결과 숫자(꼭짓점 지름 · 변 길이)를 도형 식으로, 멈춤 까닭을 도형 조건으로.
 * 3. 절삭조건: Mitsubishi Materials 기술 자료의 예제(손 계산 답이 적힌 문제)
 *    https://mitsubishicarbide.net/contents/mhg/enuk/html/product/technical_information/information/formula1.html (선삭) ·
 *    formula3.html (드릴), 드릴 카탈로그 모든 이송 구간의 경계 · 사이 · 행 밖, 출처 칸.
 *    더한 것(2차): G96 · G50 S 막힘(FANUC B-61394E/08 9.3 의 글로 따로 계산, 경계 ±1e-6).
 *
 * 테스트 하나가 여러 경우를 보며 틀린 것을 모두 모아 한 번에 실패시킨다(무엇이 얼마나 틀렸는지 다 보이게).
 */
class Stage3VerifyTest {

    // ================================================================ 공통

    private class Bad(private val title: String) {
        val items = mutableListOf<String>()

        fun check(ok: Boolean, msg: () -> String) {
            if (!ok) items += msg()
        }

        fun near(e: Double, a: Double, tol: Double, what: String) =
            check(abs(e - a) <= tol) { "$what: 기대 ${f6(e)}, 실제 ${f6(a)} (차 ${f6(a - e)}, 허용 ${f6(tol)})" }

        fun done() {
            if (items.isNotEmpty()) fail("$title — ${items.size}건\n" + items.take(80).joinToString("\n"))
        }
    }

    // ================================================================ 1. 나사 — 규격 한계 치수

    private fun metric(d: Double, p: Double) = ThreadTable.METRIC.single { it.d == d && it.pitch == p }
    private fun un(size: String, tpi: Int) = ThreadTable.UN.single { it.size == size && it.tpi == tpi }
    private fun unClass(label: String) = ThreadClass.entries.single { it.kind == ThreadKind.UN && it.label == label }

    /**
     * ASME B1.13M-1995 6g (Engineers Edge 표). 열: 지름 피치 | 바깥지름 최대 최소 | 유효지름 최대 최소 | 골지름 최대(평평한 골, d1 + es).
     * 같은 표의 M33×3.5 줄(32.968 — es 가 P1.5 의 0.032)과 M52×5 줄(Td2 0.236 — 같은 지름 구간 M48×5 는 0.250)은 ISO 965-2 · 같은 구간과
     * 안 맞는 오기라 뺐다. M1.4 이하는 ISO 965-2 에서 5H/6h 라 뺐다.
     */
    private val asme6g = rows(
        """
        3 0.5 2.980 2.874 2.655 2.580 2.439
        4 0.7 3.978 3.838 3.523 3.433 3.220
        5 0.8 4.976 4.826 4.456 4.361 4.110
        6 1 5.974 5.794 5.324 5.212 4.891
        8 1.25 7.972 7.760 7.160 7.042 6.619
        10 1.5 9.968 9.732 8.994 8.862 8.344
        12 1.75 11.966 11.701 10.829 10.679 10.072
        16 2 15.962 15.682 14.663 14.503 13.797
        20 2.5 19.958 19.623 18.334 18.164 17.252
        24 3 23.952 23.577 22.003 21.803 20.704
        30 3.5 29.947 29.522 27.674 27.462 26.158
        36 4 35.940 35.465 33.342 33.118 31.610
        42 4.5 41.937 41.437 39.014 38.778 37.066
        48 5 47.929 47.399 44.681 44.431 42.516
        8 1 7.974 7.794 7.324 7.212 6.891
        10 1.25 9.972 9.760 9.160 9.042 8.619
        12 1.25 11.972 11.760 11.160 11.028 10.619
        12 1.5 11.968 11.732 10.994 10.854 10.344
        16 1.5 15.968 15.732 14.994 14.854 14.344
        20 1.5 19.968 19.732 18.994 18.854 18.344
        20 2 19.962 19.682 18.663 18.503 17.797
        24 2 23.962 23.682 22.663 22.493 21.797
        """,
    )

    /** ASME B1.13M-1995 6H (Engineers Edge 표). 열: 지름 피치 | 안지름 최소 최대 | 유효지름 최소 최대 | 바깥지름 최소 */
    private val asme6H = rows(
        """
        3 0.5 2.459 2.599 2.675 2.775 3.000
        4 0.7 3.242 3.422 3.545 3.663 4.000
        5 0.8 4.134 4.334 4.480 4.605 5.000
        6 1 4.917 5.153 5.350 5.500 6.000
        8 1.25 6.647 6.912 7.188 7.348 8.000
        10 1.5 8.376 8.676 9.026 9.206 10.000
        12 1.75 10.106 10.441 10.863 11.063 12.000
        16 2 13.835 14.210 14.701 14.913 16.000
        20 2.5 17.294 17.744 18.376 18.600 20.000
        24 3 20.752 21.252 22.051 22.316 24.000
        30 3.5 26.211 26.771 27.727 28.007 30.000
        36 4 31.670 32.270 33.402 33.702 36.000
        42 4.5 37.129 37.799 39.077 39.392 42.000
        48 5 42.587 43.297 44.752 45.087 48.000
        8 1 6.917 7.153 7.350 7.500 8.000
        10 1.25 8.647 8.912 9.188 9.348 10.000
        12 1.25 10.647 10.912 11.188 11.368 12.000
        12 1.5 10.376 10.676 11.026 11.216 12.000
        16 1.5 14.376 14.676 15.026 15.216 16.000
        20 1.5 18.376 18.676 19.026 19.216 20.000
        20 2 17.835 18.210 18.701 18.913 20.000
        24 2 21.835 22.210 22.701 22.925 24.000
        """,
    )

    /**
     * NBS H28 (1969) 표 2.21 숫나사. 열: 크기 산수 급수 | 허용차 | 바깥지름 최대 최소 | 유효지름 최대 최소 (인치).
     * OCR 이 1/4-20 3A 바깥지름 최대를 ".2590" 으로 읽었다 — 3A 는 허용차 0 이라 기본 .2500(같은 표의 다른 3A 줄 · ASME 표와 같음).
     * 1/4-28 은 OCR 이 2A · 3A 의 유효지름 칸을 한 줄씩 밀어 놓아 공차 칸(.0033 · .0025)으로 줄을 맞췄다.
     */
    private val h28Ext = rows(
        """
        1/4 20 1A .0011 .2489 .2367 .2164 .2108
        1/4 20 2A .0011 .2489 .2408 .2164 .2127
        1/4 20 3A .0000 .2500 .2419 .2175 .2147
        1/4 28 1A .0010 .2490 .2392 .2258 .2208
        1/4 28 2A .0010 .2490 .2425 .2258 .2225
        1/4 28 3A .0000 .2500 .2435 .2268 .2243
        5/16 18 1A .0012 .3113 .2982 .2752 .2691
        5/16 18 2A .0012 .3113 .3026 .2752 .2712
        5/16 18 3A .0000 .3125 .3038 .2764 .2734
        1/2 13 1A .0015 .4985 .4822 .4485 .4411
        1/2 13 2A .0015 .4985 .4876 .4485 .4435
        1/2 13 3A .0000 .5000 .4891 .4500 .4463
        #10 24 2A .0010 .1890 .1818 .1619 .1586
        #10 24 3A .0000 .1900 .1828 .1629 .1604
        #6 32 2A .0008 .1372 .1312 .1169 .1141
        #6 32 3A .0000 .1380 .1320 .1177 .1156
        """,
    )

    /** NBS H28 표 2.21 암나사. 열: 크기 산수 급수 | 안지름 최소 최대 | 유효지름 최소 최대 (바깥지름 최소 = 기본 지름) */
    private val h28Int = rows(
        """
        1/4 20 1B .196 .207 .2175 .2248
        1/4 20 2B .196 .207 .2175 .2224
        1/4 20 3B .1960 .2067 .2175 .2211
        1/4 28 1B .211 .220 .2268 .2333
        1/4 28 2B .211 .220 .2268 .2311
        1/4 28 3B .2110 .2190 .2268 .2300
        5/16 18 1B .252 .265 .2764 .2843
        5/16 18 2B .252 .265 .2764 .2817
        5/16 18 3B .2520 .2630 .2764 .2803
        1/2 13 1B .417 .434 .4500 .4597
        1/2 13 2B .417 .434 .4500 .4565
        1/2 13 3B .4170 .4284 .4500 .4548
        #10 24 2B .145 .156 .1629 .1672
        #10 24 3B .1450 .1555 .1629 .1661
        #6 32 2B .104 .114 .1177 .1214
        #6 32 3B .1040 .1140 .1177 .1204
        """,
    )

    /** ASME B1.1 숫나사(Engineers Edge 표). 열: 크기 산수 급수 | 바깥지름 최대 최소 | 유효지름 최대 최소 (인치) */
    private val asmeUnExt = rows(
        """
        3/8 16 2A .3737 .3643 .3331 .3287
        3/8 16 3A .3750 .3656 .3344 .3311
        7/16 14 2A .4361 .4258 .3897 .3850
        7/16 14 3A .4375 .4272 .3911 .3876
        1/2 20 2A .4987 .4906 .4662 .4619
        1/2 20 3A .5000 .4919 .4675 .4643
        3/8 24 2A .3739 .3667 .3468 .3430
        3/8 24 3A .3750 .3678 .3479 .3450
        5/16 24 2A .3114 .3042 .2843 .2806
        5/16 24 3A .3125 .3053 .2854 .2827
        3/4 10 2A .7482 .7353 .6832 .6773
        3/4 10 3A .7500 .7371 .6850 .6806
        5/8 11 2A .6234 .6113 .5644 .5589
        5/8 11 3A .6250 .6129 .5660 .5619
        3/4 16 2A .7485 .7391 .7079 .7029
        3/4 16 3A .7500 .7406 .7094 .7056
        """,
    )

    /**
     * ASME B1.1 암나사(Engineers Edge 표). 열: 크기 산수 급수 | 안지름 최소 최대 | 바깥지름 최소.
     * 이 표의 1/4-20 3B 안지름 최대 0.2070 은 2B 값을 옮긴 오기(H28 표 2.21 은 .2067)라 이 표에서 1/4-20 은 뺐다.
     */
    private val asmeUnInt = rows(
        """
        3/8 16 2B .3070 .321 .3750
        3/8 16 3B .3070 .3182 .3750
        7/16 14 2B .3600 .376 .4375
        7/16 14 3B .3600 .3717 .4375
        1/2 20 2B .4460 .457 .5000
        1/2 20 3B .4460 .4537 .5000
        3/8 24 2B .3300 .340 .3750
        3/8 24 3B .3300 .3372 .3750
        5/16 24 2B .2670 .277 .3125
        5/16 24 3B .2670 .2754 .3125
        3/4 10 2B .6420 .663 .7500
        3/4 10 3B .6420 .6545 .7500
        5/8 11 2B .5270 .546 .6250
        5/8 11 3B .5270 .5391 .6250
        3/4 16 2B .6820 .696 .7500
        3/4 16 3B .6820 .6908 .7500
        """,
    )

    /**
     * 표끼리 맞는지(자료 확인): ASME B1.13M(Engineers Edge) ↔ ISO 965-2:1973 표 1~4 의 읽히는 줄,
     * ASME B1.1(Engineers Edge) ↔ H28 표 2.21 의 겹치는 줄. 이것이 맞아야 아래 비교가 "규격 표" 와의 비교다.
     */
    @Test
    fun publishedTablesAgree() {
        val bad = Bad("공개 표끼리")
        // ISO 965-2:1973 (6g: 바깥지름 최대 최소 · 유효지름 최대 최소, 6H: 유효지름 최대 최소 · 안지름 최대 최소)
        val iso6g = mapOf(
            (6.0 to 1.0) to listOf(5.974, 5.794, 5.324, 5.212), (10.0 to 1.5) to listOf(9.968, 9.732, 8.994, 8.862),
            (12.0 to 1.75) to listOf(11.966, 11.701, 10.829, 10.679), (20.0 to 2.5) to listOf(19.958, 19.623, 18.334, 18.164),
            (24.0 to 3.0) to listOf(23.952, 23.577, 22.003, 21.803), (30.0 to 3.5) to listOf(29.947, 29.522, 27.674, 27.462),
            (12.0 to 1.25) to listOf(11.972, 11.760, 11.160, 11.028), (10.0 to 1.25) to listOf(9.972, 9.760, 9.160, 9.042),
            (8.0 to 1.0) to listOf(7.974, 7.794, 7.324, 7.212),
        )
        val iso6H = mapOf(
            (6.0 to 1.0) to listOf(5.500, 5.350, 5.153, 4.917), (10.0 to 1.5) to listOf(9.206, 9.026, 8.676, 8.376),
            (20.0 to 2.5) to listOf(18.600, 18.376, 17.744, 17.294), (24.0 to 3.0) to listOf(22.316, 22.051, 21.252, 20.752),
            (8.0 to 1.25) to listOf(7.348, 7.188, 6.912, 6.647), (12.0 to 1.25) to listOf(11.368, 11.188, 10.912, 10.647),
            (8.0 to 1.0) to listOf(7.500, 7.350, 7.153, 6.917),
        )
        for ((k, v) in iso6g) {
            val r = asme6g.single { it[0].toDouble() == k.first && it[1].toDouble() == k.second }.map { it.toDouble() }
            listOf(r[2], r[3], r[4], r[5]).zip(v).forEach { (a, i) -> bad.near(i, a, 1e-9, "M${k.first}×${k.second} 6g ISO↔ASME") }
        }
        for ((k, v) in iso6H) {
            val r = asme6H.single { it[0].toDouble() == k.first && it[1].toDouble() == k.second }.map { it.toDouble() }
            listOf(r[5], r[4], r[3], r[2]).zip(v).forEach { (a, i) -> bad.near(i, a, 1e-9, "M${k.first}×${k.second} 6H ISO↔ASME") }
        }
        // H28 ↔ ASME B1.1 (Engineers Edge): 1/4-20 · 1/2-13 · #10-24 · #6-32 의 2A/3A 바깥지름 · 유효지름, 2B 안지름 최소
        val eeExt = mapOf(
            "1/4 20 2A" to listOf(.2489, .2408, .2164, .2127), "1/4 20 3A" to listOf(.2500, .2419, .2175, .2147),
            "1/2 13 2A" to listOf(.4985, .4876, .4485, .4435), "1/2 13 3A" to listOf(.5000, .4891, .4500, .4463),
            "#10 24 2A" to listOf(.1890, .1818, .1619, .1586), "#6 32 3A" to listOf(.1380, .1320, .1177, .1156),
            "1/4 28 2A" to listOf(.2490, .2425, .2258, .2225), "1/4 28 3A" to listOf(.2500, .2435, .2268, .2243),
        )
        for ((key, v) in eeExt) {
            val h = h28Ext.single { "${it[0]} ${it[1]} ${it[2]}" == key }.drop(4).map { it.toDouble() }
            h.zip(v).forEach { (a, e) -> bad.near(e, a, 1e-9, "$key H28↔ASME") }
        }
        bad.done()
    }

    /**
     * 미터 — 최대 실체 쪽 한계와 골 한계. 이 셋은 "기본 치수 + 기초 편차" 라 공차 식과 상관없이 표와 맞아야 한다.
     * 허용 0.002 mm: 표의 0.001 반올림(표는 공차역 안쪽으로 자른다) + 표의 es 가 μm 로 반올림된 것(P4.5 의 15 + 11P = 64.5 → 표 63).
     */
    @Test
    fun metricMaxMaterialAndRootLimitsMatchAsmeTable() {
        val bad = Bad("미터 6g/6H 최대 실체 쪽 · 골 한계(ASME B1.13M 표, ±0.002 mm)")
        for (v in asme6g) {
            val l = ThreadTol.limits(metric(v[0].toDouble(), v[1].toDouble()), ThreadClass.M6g)
            val n = "M${v[0]}×${v[1]} 6g"
            bad.near(v[2].toDouble(), l.crest.max, 0.002, "$n 바깥지름 최대")
            bad.near(v[4].toDouble(), l.pitchDia.max, 0.002, "$n 유효지름 최대")
            bad.near(v[6].toDouble(), l.rootLimit, 0.002, "$n 골지름 최대(평평한 골)")
        }
        for (v in asme6H) {
            val l = ThreadTol.limits(metric(v[0].toDouble(), v[1].toDouble()), ThreadClass.M6H)
            val n = "M${v[0]}×${v[1]} 6H"
            bad.near(v[2].toDouble(), l.crest.min, 0.002, "$n 안지름 최소")
            bad.near(v[4].toDouble(), l.pitchDia.min, 0.002, "$n 유효지름 최소")
            bad.near(v[6].toDouble(), l.rootLimit, 0.002, "$n 바깥지름 최소")
        }
        bad.done()
    }

    /** G76 목표 위치로 계산기가 고른 유효지름 · 산봉우리 지름(숫 바깥 · 암 안지름)이 공개 표의 공차역 안인지. 허용 = 표 반올림 */
    private fun metricZone(targets: List<Double>, title: String) {
        val bad = Bad(title)
        val tol = 0.0015
        for ((table, cls) in listOf(asme6g to ThreadClass.M6g, asme6H to ThreadClass.M6H)) {
            for (v in table) {
                val spec = metric(v[0].toDouble(), v[1].toDouble())
                val x = v.map { it.toDouble() }
                // 숫: 바깥 최대 최소 · 유효 최대 최소 / 암: 안 최소 최대 · 유효 최소 최대
                val crest = if (cls.internal) x[2]..x[3] else x[3]..x[2]
                val pd = if (cls.internal) x[4]..x[5] else x[5]..x[4]
                for (t in targets) {
                    val s = g76(spec, cls, t) ?: run { bad.items += "${spec.label} ${cls.label} $t%: 계산이 멈췄다"; null } ?: continue
                    val n = "${spec.label} ${cls.label} 목표 ${t.toInt()}%"
                    bad.check(s.pitchDia >= pd.start - tol && s.pitchDia <= pd.endInclusive + tol) {
                        "$n 유효지름 ${f4(s.pitchDia)} — 표 ${f4(pd.start)}~${f4(pd.endInclusive)} 밖 ${f4(outside(s.pitchDia, pd))} mm"
                    }
                    bad.check(s.crestDia >= crest.start - tol && s.crestDia <= crest.endInclusive + tol) {
                        "$n ${if (cls.internal) "안지름" else "바깥지름"} ${f4(s.crestDia)} — 표 ${f4(crest.start)}~${f4(crest.endInclusive)} 밖 ${f4(outside(s.crestDia, crest))} mm"
                    }
                }
            }
        }
        bad.done()
    }

    /** 목표 0 · 50 · 80(기본) · 90 % — 공개 표의 공차역 안이어야 한다 */
    @Test
    fun metricG76TargetsUpTo90PercentInsidePublishedZone() =
        metricZone(listOf(0.0, 50.0, 80.0, 90.0), "미터 G76 목표 0~90% 가 ASME B1.13M 공차역 안")

    /**
     * 목표 100% (최소 실체) — 화면이 고를 수 있는 값이다. 계산기 공차폭은 ISO 965-1 식 값이고 표는 그 값을 둥근 수로 정한 것이라
     * 100% 의 지름이 표의 최소 실체 한계(NO-GO 게이지 값)를 넘을 수 있는지 본다.
     */
    @Test
    fun metricG76Target100PercentInsidePublishedZone() =
        metricZone(listOf(100.0), "미터 G76 목표 100% 가 ASME B1.13M 공차역 안")

    /** UN — H28 표 2.21(1A~3B)과 ASME B1.1 표. 허용 = 그 칸이 적힌 자릿수의 반 + 0.0001 인치(H28 은 공차 성분을 따로 반올림해 더함) */
    @Test
    fun unifiedLimitsMatchH28AndAsmeTables() {
        val bad = Bad("UN 한계 치수(H28 표 2.21 · ASME B1.1)")
        fun tol(s: String) = 0.5 * 10.0.pow(-(s.length - s.indexOf('.') - 1)) + 0.0001
        // 안지름 최소는 표가 0.001 인치로 적는다(3B 칸도 .3070 · .6820 처럼 끝이 0 — 기본 안지름 .3073 · .6823 을 0.001 로)
        val minorMinTol = 0.0006
        for (v in h28Ext + asmeUnExt) {
            val spec = un(v[0], v[1].toInt())
            val cls = unClass(v[2])
            val l = ThreadTol.limits(spec, cls)
            val n = "${spec.label} ${cls.label}"
            val (mx, mn, px, pn) = if (v.size == 8) v.drop(4) else v.drop(3)
            bad.near(mx.toDouble(), l.crest.max / 25.4, tol(mx), "$n 바깥지름 최대")
            bad.near(mn.toDouble(), l.crest.min / 25.4, tol(mn), "$n 바깥지름 최소")
            bad.near(px.toDouble(), l.pitchDia.max / 25.4, tol(px), "$n 유효지름 최대")
            bad.near(pn.toDouble(), l.pitchDia.min / 25.4, tol(pn), "$n 유효지름 최소")
            if (v.size == 8) bad.near(-v[3].toDouble(), l.deviation / 25.4, tol(v[3]), "$n 허용차")
            // 골: UN 숫나사 골지름 최대 = 상대 암나사 안지름 최소(기본) − 허용차 (H28 6.3 "maximum material condition of the internal
            // thread less the allowance") — 표의 3B 안지름 최소와 허용차(기본 지름 − 바깥지름 최대)로
            val minorMin = h28Int.firstOrNull { it[0] == v[0] && it[1] == v[1] && it[2] == "3B" }?.get(3)
                ?: asmeUnInt.first { it[0] == v[0] && it[1] == v[1] && it[2] == "3B" }[3]
            val allowance = spec.d / 25.4 - mx.toDouble()
            bad.near(minorMin.toDouble() - allowance, l.rootLimit / 25.4, minorMinTol + 0.0001, "$n 골지름 최대(UN)")
        }
        for (v in h28Int) {
            val spec = un(v[0], v[1].toInt())
            val cls = unClass(v[2])
            val l = ThreadTol.limits(spec, cls)
            val n = "${spec.label} ${cls.label}"
            bad.near(v[3].toDouble(), l.crest.min / 25.4, minorMinTol, "$n 안지름 최소")
            bad.near(v[4].toDouble(), l.crest.max / 25.4, tol(v[4]), "$n 안지름 최대")
            bad.near(v[5].toDouble(), l.pitchDia.min / 25.4, tol(v[5]), "$n 유효지름 최소")
            bad.near(v[6].toDouble(), l.pitchDia.max / 25.4, tol(v[6]), "$n 유효지름 최대")
            bad.near(spec.d / 25.4, l.rootLimit / 25.4, 1e-9, "$n 바깥지름 최소")
        }
        for (v in asmeUnInt) {
            val spec = un(v[0], v[1].toInt())
            val cls = unClass(v[2])
            val l = ThreadTol.limits(spec, cls)
            val n = "${spec.label} ${cls.label}"
            bad.near(v[3].toDouble(), l.crest.min / 25.4, minorMinTol, "$n 안지름 최소")
            bad.near(v[4].toDouble(), l.crest.max / 25.4, tol(v[4]), "$n 안지름 최대")
            bad.near(v[5].toDouble(), l.rootLimit / 25.4, 1e-4, "$n 바깥지름 최소")
        }
        bad.done()
    }

    /** UN G76 목표 0 · 50 · 80 · 100 % 가 H28 표 2.21 공차역 안(허용 = 그 칸 자릿수 반올림) */
    @Test
    fun unifiedG76TargetsInsideH28Zone() {
        val bad = Bad("UN G76 목표가 H28 공차역 안")
        for (v in h28Ext + h28Int) {
            val spec = un(v[0], v[1].toInt())
            val cls = unClass(v[2])
            val inner = cls.internal
            val x = v.drop(3).map { it.toDouble() * 25.4 }
            // 숫: 허용차 · 바깥 최대 최소 · 유효 최대 최소 / 암: 안 최소 최대 · 유효 최소 최대
            val crest = if (inner) x[0]..x[1] else x[2]..x[1]
            val pd = if (inner) x[2]..x[3] else x[4]..x[3]
            // 암나사 안지름 최소는 H28 이 0.001 인치로 적는다 — 3B 칸의 .1960 · .4170 · .1450 도 기본 안지름 .19587 · .41673 · .14490 을
            // 셋째 자리로 반올림한 것(H28 10절 예 "Min minor diameter = 2.500 − 0.0387 = 2.461" — 2.4613 을 셋째 자리로).
            // 그래서 그 끝은 0.0005 + 0.0001 인치, 안지름 최대는 적힌 자릿수대로(2B .207 · 3B .2067)
            val crestLoTol = (if (inner) 0.0006 else 0.0001) * 25.4
            val crestHiTol = (if (inner) 0.5 * 10.0.pow(-(v[4].length - v[4].indexOf('.') - 1)) + 0.0001 else 0.0001) * 25.4
            for (t in listOf(0.0, 50.0, 80.0, 100.0)) {
                val s = g76(spec, cls, t) ?: run { bad.items += "${spec.label} ${cls.label} $t%: 멈춤"; null } ?: continue
                val n = "${spec.label} ${cls.label} 목표 ${t.toInt()}%"
                bad.check(s.pitchDia >= pd.start - 0.0001 * 25.4 && s.pitchDia <= pd.endInclusive + 0.0001 * 25.4) {
                    "$n 유효지름 ${f4(s.pitchDia)} 표 ${f4(pd.start)}~${f4(pd.endInclusive)} 밖"
                }
                bad.check(s.crestDia >= crest.start - crestLoTol && s.crestDia <= crest.endInclusive + crestHiTol) {
                    "$n 산봉우리 지름 ${f4(s.crestDia)} 표 ${f4(crest.start)}~${f4(crest.endInclusive)} 밖"
                }
            }
        }
        bad.done()
    }

    /**
     * ISO 965-1:1998 표 1 "Fundamental deviations"(iTeh 미리보기 https://cdn.standards.iteh.ai/samples/5393/bb7c10816efa4087b66e40d8375fb23b/ISO-965-1-1998.pdf
     * 8쪽) — G 의 EI 와 g 의 es 는 같은 크기(μm). h · H 는 0. 이 앱의 모든 미터 피치 · 급수(4h 6g 6h 8g 6H 6G)에서 deviation 이 표와 같은지.
     * 표는 μm 정수라 허용 ±0.5 μm. (표 값 읽기는 ASME B1.13M · Optimas 표의 바깥지름 최대 = d − es 로 P0.35~5 를 맞춰 봤다)
     */
    @Test
    fun metricFundamentalDeviationsMatchIso9651Table1() {
        val bad = Bad("미터 기초 편차(ISO 965-1 표 1, ±0.5 μm)")
        val gTable = mapOf(
            0.2 to 17, 0.25 to 18, 0.3 to 18, 0.35 to 19, 0.4 to 19, 0.45 to 20, 0.5 to 20, 0.6 to 21, 0.7 to 22, 0.75 to 22, 0.8 to 24,
            1.0 to 26, 1.25 to 28, 1.5 to 32, 1.75 to 34, 2.0 to 38, 2.5 to 42, 3.0 to 48, 3.5 to 53, 4.0 to 60, 4.5 to 63, 5.0 to 71,
            5.5 to 75, 6.0 to 80, 8.0 to 100,
        )
        for (p in ThreadTable.METRIC.map { it.pitch }.distinct().sorted()) {
            val g = gTable[p]
            if (g == null) {
                bad.items += "P$p: 표 1 에 없는 피치"
                continue
            }
            val spec = ThreadTable.METRIC.first { it.pitch == p }
            for (cls in ThreadClass.entries.filter { it.kind == ThreadKind.METRIC }) {
                val want = when (cls) {
                    ThreadClass.M6g, ThreadClass.M8g -> -g.toDouble()
                    ThreadClass.M6G -> g.toDouble()
                    else -> 0.0
                }
                val l = ThreadTol.limits(spec, cls)
                bad.near(want, l.deviation * 1000, 0.5 + 1e-9, "P$p ${cls.label} 기초 편차(μm, ${spec.label})")
            }
        }
        bad.done()
    }

    /**
     * Optimas "ISO Metric Screw Thread Tolerances"(https://optimas.com/technical-resources/iso-metric-screw-thread-tolerances/, 6g) —
     * 위 ASME 표에 없는 사이즈(지름 구간 1.4~2.8 · 2.8~5.6 · 11.2~22.4 · 22.4~45 의 다른 줄). 열: 지름 피치 | 바깥지름 최대 최소 | 유효지름 최대 최소.
     * 겹치는 19 줄(M3 … M36, M10×1.25 … M24×2)은 ASME 표와 모두 같다. 이 쪽의 오기 줄은 뺐다: M2.5(유효지름 2.118 · 2.117 — 폭 1 μm),
     * M8×1(바깥지름 최대 = 최소 7.794), M10×1(유효지름 최소 "9212"), M18×2(칸 밀림). M1~M1.4 는 6h 라 뺐다.
     */
    private val optimas6g = rows(
        """
        1.6 0.35 1.581 1.496 1.354 1.291
        1.8 0.35 1.781 1.696 1.554 1.491
        2 0.4 1.981 1.886 1.721 1.654
        3.5 0.6 3.479 3.354 3.089 3.004
        7 1 6.974 6.794 6.324 6.212
        14 2 13.962 13.682 12.663 12.503
        18 2.5 17.958 17.623 16.334 16.164
        22 2.5 21.958 21.623 20.334 20.164
        27 3 26.952 26.577 25.003 24.803
        33 3.5 32.947 32.522 30.674 30.462
        39 4 38.940 38.465 36.342 36.118
        14 1.5 13.968 13.732 12.994 12.854
        18 1.5 17.968 17.732 16.994 16.854
        22 1.5 21.968 21.732 20.994 20.854
        22 2 21.962 21.682 20.663 20.503
        27 2 26.962 26.682 25.663 25.493
        30 2 29.962 29.682 28.663 28.493
        33 2 32.962 32.682 31.663 31.493
        36 3 35.952 35.577 34.003 33.803
        39 3 38.952 38.577 37.003 36.803
        """,
    )

    /** Optimas 표 6g 줄 — 계산기의 목표 위치 지름이 공차역 안(허용 = 표 반올림 0.0015)인지 */
    private fun optimasZone(targets: List<Double>, bad: Bad) {
        for (v in optimas6g) {
            val spec = metric(v[0].toDouble(), v[1].toDouble())
            val x = v.map { it.toDouble() }
            for (t in targets) {
                val s = g76(spec, ThreadClass.M6g, t) ?: run { bad.items += "${spec.label} 6g $t%: 멈춤"; null } ?: continue
                val n = "${spec.label} 6g 목표 ${t.toInt()}%"
                val pd = x[5]..x[4]
                val crest = x[3]..x[2]
                bad.check(s.pitchDia >= pd.start - 0.0015 && s.pitchDia <= pd.endInclusive + 0.0015) {
                    "$n 유효지름 ${f4(s.pitchDia)} — 표 ${f4(pd.start)}~${f4(pd.endInclusive)} 밖 ${f4(outside(s.pitchDia, pd))} mm"
                }
                bad.check(s.crestDia >= crest.start - 0.0015 && s.crestDia <= crest.endInclusive + 0.0015) {
                    "$n 바깥지름 ${f4(s.crestDia)} — 표 ${f4(crest.start)}~${f4(crest.endInclusive)} 밖 ${f4(outside(s.crestDia, crest))} mm"
                }
            }
        }
    }

    /** Optimas 표: 최대 실체 쪽(바깥지름 최대 · 유효지름 최대, ±0.002 — 위 ASME 비교와 같은 허용)과 목표 0~90% */
    @Test
    fun metricOptimas6gMaxMaterialAndTargetsTo90() {
        val bad = Bad("미터 6g Optimas 표 — 최대 실체 쪽 · 목표 0~90%")
        for (v in optimas6g) {
            val l = ThreadTol.limits(metric(v[0].toDouble(), v[1].toDouble()), ThreadClass.M6g)
            val n = "M${v[0]}×${v[1]} 6g"
            bad.near(v[2].toDouble(), l.crest.max, 0.002, "$n 바깥지름 최대")
            bad.near(v[4].toDouble(), l.pitchDia.max, 0.002, "$n 유효지름 최대")
        }
        optimasZone(listOf(0.0, 50.0, 80.0, 90.0), bad)
        bad.done()
    }

    /** Optimas 표: 목표 100%(최소 실체) — 위 ASME 의 100% 시험과 같은 것을 다른 사이즈로 */
    @Test
    fun metricOptimas6gTarget100() {
        val bad = Bad("미터 6g Optimas 표 — 목표 100%")
        optimasZone(listOf(100.0), bad)
        bad.done()
    }

    // ================================================================ 1. 나사 — G76 예제 · X · 분석표

    /** 목표 · 급수를 넣은 G76. 첫 절입 0.25P(횟수가 적당히), X 여유 0.2(작은 암나사도 시작 X > 0) */
    private fun g76(spec: ThreadSpec, cls: ThreadClass, target: Double, tipR: Double? = null): G76Solution? =
        G76Calc.calc(
            G76Input(
                inner = cls.internal, spec = spec, cls = cls, zStart = 5.0, zEnd = -30.0, firstCut = 0.25 * spec.pitch, target = target,
                tipR = tipR, xClear = 0.2,
            ),
        ).solution

    /** 60° V 날(끝 반지름 R)의 단면 반폭 — 날 끝에서 높이 y. 끝 원호는 옆날과 만나는 높이 R(1 − sin30°)까지 */
    private fun toolHalfWidth(rTip: Double, y: Double): Double {
        val half = PI / 6 // 60° 날의 반각
        val yT = rTip * (1 - sin(half)) // 끝 원호와 옆날이 만나는 높이(날 끝에서)
        return if (y <= yT) sqrt(max(0.0, rTip * rTip - (rTip - y) * (rTip - y))) else rTip * cos(half) + (y - yT) * tan(half)
    }

    /** 60° V 날(끝 반지름 R)의 날 끝이 피치선(홈 폭 P/2)에서 얼마나 깊은지 — 날 단면 반폭이 P/4 가 되는 높이를 이분법으로 푼다 */
    private fun tipDepthBelowPitchLine(p: Double, rTip: Double): Double {
        var lo = 0.0
        var hi = 2 * p
        repeat(200) {
            val m = (lo + hi) / 2
            if (toolHalfWidth(rTip, m) < p / 4) lo = m else hi = m
        }
        return (lo + hi) / 2
    }

    private fun word(line: String, k: Char): String? = line.split(' ').firstOrNull { it.length > 1 && it[0] == k }?.substring(1)

    /**
     * FANUC 0i-F 조작 설명서 B-64604EN-1/01 4.2.7 Fig. 4.2.7 (e)(외경 68 · 리드 6 · 끝 Z25 · 시작 X80 Z130):
     * `G76 P011060 Q100 R200 ;` `G76 X60.64 Z25.0 P3680 Q1800 F6.0 ;` 과 같은 책 6.4.7 의 FS10/11 형식
     * `G76 X60.64 Z25.0 K3680 D1800 A60 P1 F6.0 ;`(면취는 매개변수 5130 = 10 → 1.0L).
     * 매뉴얼은 k = 0.6134 × 6 = 3.68(0.01 로 반올림)이라 X 는 0.01 자리, k 는 10 μm 자리까지 맞춘다. 절입은 1.8 · 1.8√2 · 1.8√3 · k − d.
     */
    @Test
    fun g76FanucManualExample() {
        val bad = Bad("FANUC 0i-F G76 예")
        val m68 = ThreadSpec(ThreadSeries.M_COARSE, "M68", 68.0, 6.0)
        val r = G76Calc.calc(
            G76Input(
                spec = m68, cls = ThreadClass.M6h, zStart = 130.0, zEnd = 25.0, firstCut = 1.8, target = 0.0, finishCount = 1,
                chamfer = 10, angle = 60, minCut = 0.1, finishAllow = 0.2, xClear = 6.0,
            ),
        )
        val s = assertNotNull(r.solution, "멈춤 ${r.warnings}")
        bad.check(s.program.first() == "G00 X80. Z130.") { "시작점: ${s.program.first()} (매뉴얼 G00 X80.0 Z130.0)" }
        // 첫 줄: R 은 소수점 지령 가능(같은 절 표) — R200(IS-B μm) = R0.2
        bad.check(s.nc[0] == "G76 P011060 Q100 R0.2") { "첫 줄 ${s.nc[0]} (매뉴얼 G76 P011060 Q100 R200)" }
        val l2 = s.nc[1]
        val x = word(l2, 'X')!!.toDouble()
        val z = word(l2, 'Z')!!.toDouble()
        val k = word(l2, 'P')!!.toInt()
        val dd = word(l2, 'Q')!!.toInt()
        val f = word(l2, 'F')!!.toDouble()
        bad.near(60.64, x, 0.005, "X (매뉴얼 60.64, 0.01 자리)")
        bad.near(25.0, z, 1e-9, "Z")
        bad.check(abs(k - 3680) <= 5) { "k: P$k (매뉴얼 P3680, 0.01 mm 자리)" }
        bad.check(dd == 1800) { "Δd: Q$dd (매뉴얼 Q1800)" }
        bad.near(6.0, f, 1e-12, "F 리드")
        // FS10/11 형식으로 옮겨 같은 낱말인지: X Z K(= k) D(= Δd) A(= a) P1 F, 면취 1.0L
        val p1 = word(s.nc[0], 'P')!!
        val fs10 = "G76 X${word(l2, 'X')} Z${word(l2, 'Z')} K$k D$dd A${p1.substring(4, 6)} P1 F${word(l2, 'F')}"
        bad.check(fs10.startsWith("G76 X60.6") && fs10.contains(" D1800 A60 P1 F6.") && p1.substring(2, 4) == "10") {
            "FS10/11 형식 $fs10 · 면취 ${p1.substring(2, 4)} (매뉴얼 G76 X60.64 Z25.0 K3680 D1800 A60 P1 F6.0 · 5130 = 10)"
        }
        // 절입(Fig. 4.2.7 (b): Δd, Δd√2, …, Δd√n · 정삭 여유 d)
        val kk = s.height
        val want = listOf(1.8, 1.8 * sqrt(2.0), 1.8 * sqrt(3.0), kk - 0.2, kk)
        bad.check(s.passes.size == want.size) { "절입 횟수 ${s.passes.size} (기대 ${want.size}: 거친 4 + 정삭 1)" }
        s.passes.zip(want).forEachIndexed { i, (p, w) -> bad.near(w, p.depth, 1e-9, "${i + 1}회 누적 깊이") }
        bad.done()
    }

    /**
     * Haas ST/TL 워크북 — 나사 표("THREAD CHART": 산수마다 외경 K · 내경 K)와 "D" 값 표(O.D., 7~14 산 · 6~16 회).
     * 외경 K = 0.6134P · 내경 K = 0.5413P 는 기본 깊이(목표 0% · 기본 인선 R), D = K/√N 이면 G76 이 N 회 깎는다(워크북 113쪽
     * "CALCULATED 1ST. PASS DEPTH ("D") FOR A DESIRED NUMBER OF PASSES", MHCC 교재 "D = K divided by the square root of N").
     * D 는 0.0001 인치로 반올림돼 있어 마지막에 아주 작은 한 회(k 의 1% 미만)가 더 생길 수 있다 — 그래서 N 또는 N + 1.
     * 표의 13산 · 15회 칸 .0129 는 앞뒤 칸(.0131 · .0118)과 K/√15 = .0122 와 안 맞는 오기라 뺐다.
     * 12산 · 7회 칸 .0195 도 K/√7 = .0511/2.6458 = .0193(같은 줄 다른 칸은 모두 K/√7 과 맞음)과 안 맞는 인쇄 오기라 자료 확인(D√N = K)에서만 뺐다
     * (그 D 로도 7 회라 횟수 확인은 한다). 자료 확인의 허용 = D 의 0.0001 반올림이 √N 배 + K 의 반올림 = 0.00005·√N + 0.00005 인치.
     * Haas 기계 기본값: 정삭 여유(설정 86) 0, 최소 절입(설정 99) .01 mm. 예 O00113: 3/4-16 외경 K0.0383 D0.0122 F0.0625 — 16산 10회.
     */
    @Test
    fun g76HaasThreadChartAndDTable() {
        val bad = Bad("Haas 나사 표 · D 표")
        val tpis = listOf(7, 8, 9, 10, 11, 12, 13, 14)
        val specOf = mapOf(7 to un("1-1/8", 7), 8 to un("1", 8), 9 to un("7/8", 9), 10 to un("3/4", 10), 11 to un("5/8", 11),
            12 to un("1", 12), 13 to un("1/2", 13), 14 to un("7/16", 14))
        val odK = listOf(.0876, .0767, .0682, .0613, .0558, .0511, .0472, .0438)
        val idK = listOf(.0773, .0677, .0601, .0541, .0492, .0451, .0416, .0387)
        val dTable = mapOf(
            6 to listOf(.0358, .0313, .0278, .0250, .0228, .0209, .0193, .0179), 7 to listOf(.0331, .0290, .0258, .0232, .0211, .0195, .0178, .0166),
            8 to listOf(.0310, .0271, .0241, .0217, .0197, .0181, .0167, .0155), 9 to listOf(.0292, .0256, .0227, .0204, .0186, .0170, .0157, .0146),
            10 to listOf(.0277, .0243, .0216, .0194, .0176, .0162, .0149, .0139), 11 to listOf(.0264, .0231, .0206, .0185, .0168, .0154, .0142, .0132),
            12 to listOf(.0253, .0221, .0197, .0177, .0161, .0148, .0136, .0127), 13 to listOf(.0243, .0213, .0189, .0170, .0155, .0142, .0131, .0122),
            14 to listOf(.0234, .0205, .0182, .0164, .0149, .0137, .0126, .0117), 15 to listOf(.0226, .0198, .0176, .0158, .0144, .0132, -1.0, .0113),
            16 to listOf(.0219, .0192, .0171, .0153, .0140, .0128, .0118, .0110),
        )
        fun calcAt(spec: ThreadSpec, inner: Boolean, d: Double): G76Solution? = G76Calc.calc(
            G76Input(inner = inner, spec = spec, zStart = 5.0, zEnd = -40.0, firstCut = d, target = 0.0, finishCount = 1, minCut = 0.01, finishAllow = 0.0),
        ).solution
        for ((i, t) in tpis.withIndex()) {
            val spec = specOf.getValue(t)
            val od = calcAt(spec, false, 0.3) ?: run { bad.items += "$t 산 외경 멈춤"; null } ?: continue
            val id = calcAt(spec, true, 0.3) ?: run { bad.items += "$t 산 내경 멈춤"; null } ?: continue
            bad.near(odK[i], od.height / 25.4, 0.00006, "${spec.label} 외경 K")
            bad.near(idK[i], id.height / 25.4, 0.00006, "${spec.label} 내경 K")
            for ((n, row) in dTable) {
                val dIn = row[i]
                if (dIn < 0) continue
                if (!(t == 12 && n == 7)) {
                    bad.near(odK[i], dIn * sqrt(n.toDouble()), 0.00005 * sqrt(n.toDouble()) + 0.00005 + 1e-9, "D 표 자료 $t 산 $n 회: D√N = K")
                }
                val s = calcAt(spec, false, dIn * 25.4) ?: run { bad.items += "$t 산 $n 회 멈춤"; null } ?: continue
                val rough = s.passes.filter { !it.finish }
                bad.check(rough.size == n || (rough.size == n + 1 && rough.last().cut <= 0.01 * s.height)) {
                    "$t 산 D$dIn: 거친 절입 ${rough.size} 회(마지막 ${f4(rough.last().cut)}) — 표는 $n 회"
                }
            }
        }
        // O00113: 3/4-16 외경 K0.0383 D0.0122 F0.0625 — 16 산이면 10 회
        val o113 = assertNotNull(calcAt(un("3/4", 16), false, 0.0122 * 25.4))
        bad.near(0.0383, o113.height / 25.4, 0.00006, "O00113 K")
        bad.check(o113.nc[1].endsWith(" F1.5875")) { "O00113 F: ${o113.nc[1]} (0.0625 인치 = 1.5875 mm)" }
        val r113 = o113.passes.count { !it.finish }
        bad.check(r113 == 10 || r113 == 11) { "O00113 거친 절입 $r113 회 (D 표 16 산 10 회)" }
        bad.done()
    }

    /** FANUC 4.2.7 글 그대로의 절입표(따로 짬): (누적 깊이, 그 회 절입, 정삭인지) */
    private fun fanucSchedule(k: Double, d: Double, dd: Double, dmin: Double, m: Int): List<Triple<Double, Double, Boolean>> {
        val out = mutableListOf<Triple<Double, Double, Boolean>>()
        val end = k - d
        var depth = 0.0
        var n = 0
        while (end - depth > 1e-9 && n < 5000) {
            n++
            val want = max(dd * sqrt(n.toDouble()), depth + dmin) // "When the cutting depth of one cycle ... smaller than this limit ... clamped"
            val next = if (want >= end - 1e-9) end else want
            out += Triple(next, next - depth, false)
            depth = next
        }
        out += Triple(k, k - depth, true) // 마지막 정삭: 정삭 여유 d 를 깎는다
        repeat(m - 1) { out += Triple(k, 0.0, true) } // "repeated" — 같은 깊이
        return out
    }

    /**
     * 분석표 — 규격 8 · 내외경 · 목표 3 · 첫 절입 3 · 최소 절입 3 · 정삭 여유 3 · 정삭 횟수 2 를 두루(2592 경우).
     * NC 두 줄에서 읽은 k · Δd · Δdmin · d · m 으로 따로 짠 절입표와 깊이 · 절입 · 정삭 표시 · 회마다 X 가 같은지,
     * 첫 줄 낱말(P m r a · Q Δdmin · R d)이 넣은 값인지, ONE_ROUGH_PASS 가 거친 절입 1 회일 때만인지.
     */
    @Test
    fun g76PassTableFollowsFanucRule() {
        val bad = Bad("G76 분석표")
        val specs = listOf(metric(6.0, 1.0), metric(10.0, 1.5), metric(12.0, 1.25), metric(20.0, 2.5), metric(48.0, 5.0),
            un("1/4", 20), un("1/2", 13), un("3/4", 16))
        var cases = 0
        for (s in specs) for (inner in listOf(false, true)) for (t in listOf(0.0, 80.0, 100.0)) for (ff in listOf(0.08, 0.2, 0.45)) {
            for (mc in listOf(0.0, 0.035, 0.1)) for (fa in listOf(0.0, 0.02, 0.1)) for (m in listOf(1, 3)) {
                val first = ff * s.pitch
                val res = G76Calc.calc(
                    G76Input(inner = inner, spec = s, zStart = 5.0, zEnd = -30.0, firstCut = first, minCut = mc, finishAllow = fa,
                        finishCount = m, target = t, xClear = 0.2, chamfer = 7, angle = 55),
                )
                val n = "${s.label} ${if (inner) "내" else "외"} $t% Δd${f4(first)} Δdmin$mc d$fa m$m"
                val sol = res.solution
                if (sol == null) {
                    bad.check(first < mc) { "$n: 멈춤 ${res.warnings}" }
                    continue
                }
                cases++
                val p1 = word(sol.nc[0], 'P')!!
                bad.check(p1 == "${m.toString().padStart(2, '0')}0755") { "$n: 첫 줄 P$p1" }
                bad.check(word(sol.nc[0], 'Q')!!.toLong() == floor(mc * 1000 + 0.5).toLong()) { "$n: 첫 줄 Q ${sol.nc[0]}" }
                bad.near(fa, word(sol.nc[0], 'R')!!.toDouble(), 1e-12, "$n: 첫 줄 R")
                val k = word(sol.nc[1], 'P')!!.toInt() / 1000.0
                val dd = word(sol.nc[1], 'Q')!!.toInt() / 1000.0
                val xNc = word(sol.nc[1], 'X')!!.toDouble()
                bad.near(first, dd, 0.0005 + 1e-12, "$n: Δd")
                val exp = fanucSchedule(k, fa, dd, mc, m)
                if (exp.size != sol.passes.size) {
                    bad.items += "$n: 횟수 ${sol.passes.size} (따로 짠 표 ${exp.size})"
                    continue
                }
                for ((i, pair) in exp.zip(sol.passes).withIndex()) {
                    val (e, p) = pair
                    bad.check(p.n == i + 1 && abs(p.depth - e.first) <= 1e-9 && abs(p.cut - e.second) <= 1e-9 && p.finish == e.third) {
                        "$n: ${i + 1}회 (깊이 ${f6(p.depth)} 절입 ${f6(p.cut)} 정삭 ${p.finish}) ≠ (${f6(e.first)} ${f6(e.second)} ${e.third})"
                    }
                    val xWant = if (inner) xNc - 2 * (k - e.first) else xNc + 2 * (k - e.first)
                    bad.near(xWant, p.xDia, 1e-9, "$n: ${i + 1}회 X")
                }
                val oneRough = exp.count { !it.third } == 1
                bad.check((G76Warning.ONE_ROUGH_PASS in res.warnings) == oneRough) { "$n: ONE_ROUGH_PASS ${res.warnings} (거친 ${exp.count { !it.third }} 회)" }
            }
        }
        bad.check(cases > 2000) { "경우 수 $cases" }
        bad.done()
    }

    /**
     * X · k — 날 단면을 이분법으로 푼 깊이로 따로 구한다. 목표 위치(0% = 최대 실체)의 유효지름 · 산봉우리 지름도 한계에서 따로 낸다.
     * 골 한계 경고: 숫나사 X > d1 + es(d1 = d − 1.082532P, ISO 724 · ASME B1.1 의 기본 안지름), 암나사 X < D + EI 일 때만.
     */
    @Test
    fun g76XAndHeightFromToolGeometry() {
        val bad = Bad("G76 X · k (날 단면 기하)")
        val specs = listOf(metric(6.0, 1.0), metric(10.0, 1.5), metric(12.0, 1.25), metric(20.0, 2.5), metric(48.0, 5.0),
            un("#10", 24), un("1/4", 20), un("1/2", 13), un("1-1/8", 7))
        for (s in specs) for (cls in ThreadClass.entries.filter { it.kind == s.kind }) {
            val h = sqrt(3.0) / 2 * s.pitch
            for (t in listOf(0.0, 37.5, 80.0, 100.0)) for (tip in listOf(null, 0.0, 0.05, h / 8, h / 4, 0.3 * h)) {
                val n = "${s.label} ${cls.label} $t% R${tip?.let { f4(it) } ?: "기본"}"
                val r = G76Calc.calc(
                    G76Input(inner = cls.internal, spec = s, cls = cls, zStart = 5.0, zEnd = -30.0, firstCut = 0.25 * s.pitch, target = t,
                        tipR = tip, xClear = 0.2),
                )
                val sol = r.solution ?: run { bad.items += "$n: 멈춤 ${r.warnings}"; null } ?: continue
                val l = sol.limits
                val w = t / 100
                val pd = if (cls.internal) l.pitchDia.min + w * l.pitchDia.width else l.pitchDia.max - w * l.pitchDia.width
                val crest = if (cls.internal) l.crest.min + w * l.crest.width else l.crest.max - w * l.crest.width
                bad.near(pd, sol.pitchDia, 1e-9, "$n: 목표 유효지름")
                bad.near(crest, sol.crestDia, 1e-9, "$n: 목표 산봉우리 지름")
                val rTip = tip ?: (if (cls.internal) h / 8 else h / 6)
                bad.near(rTip, sol.tipR, 1e-12, "$n: 인선 R(빈칸 = 숫 H/6 · 암 H/8)")
                val depth = tipDepthBelowPitchLine(s.pitch, rTip)
                val xWant = if (cls.internal) pd + 2 * depth else pd - 2 * depth
                bad.near(xWant, sol.x, 1e-6, "$n: X")
                val xNc = word(sol.nc[1], 'X')!!.toDouble()
                bad.near(xWant, xNc, 0.0005 + 1e-9, "$n: NC X")
                val kWant = (if (cls.internal) xNc - crest else crest - xNc) / 2
                bad.near(kWant, sol.height, 0.0005 + 1e-9, "$n: k")
                bad.check(word(sol.nc[1], 'P')!!.toInt() == floor(sol.height * 1000 + 0.5).toInt()) { "$n: P 낱말 ${sol.nc[1]}" }
                // 골 한계
                val basicMinor = s.d - 1.082532 * s.pitch
                val limit = if (cls.internal) s.d + l.deviation else basicMinor + l.deviation
                val over = if (cls.internal) limit - xWant else xWant - limit
                if (abs(over) > 1e-5) {
                    bad.check((G76Warning.ROOT_INTERFERES in r.warnings) == (over > 0)) { "$n: 골 한계 넘음 ${f4(over)} 인데 경고 ${r.warnings}" }
                }
            }
        }
        bad.done()
    }

    /**
     * 인선 R 이 H/3 을 넘으면 피치선(홈 폭 P/2)이 옆날이 아니라 끝 원호에 걸린다(원호와 옆날이 만나는 반폭 R·cos30° > P/4 ⇔ R > P/(2√3) = H/3).
     * 그때도 X 가 날 단면에서 홈 폭 P/2 인 깊이인지 — 위 시험(R ≤ 0.3H)과 같은 이분법. 목표 80% · 기본 급수.
     * 단, 목표 산봉우리 지름 높이(날 끝에서 k)의 날 단면 폭이 P 를 넘으면 이웃 홈을 깎는 날이 산봉우리를 깎아 목표 지름이 남지 않는다 —
     * 그때는 숫자 대신 TIP_TOO_BIG 로 멈춰야 한다(같은 날 단면으로 따로 판정. 이 범위에서는 숫나사 0.6H 셋).
     */
    @Test
    fun g76LargeTipRadiusFollowsToolGeometry() {
        val bad = Bad("G76 인선 R > H/3 의 X (날 단면 기하)")
        var cut = 0
        for (s in listOf(metric(10.0, 1.5), metric(20.0, 2.5), un("1/2", 13))) for (inner in listOf(false, true)) {
            val cls = ThreadClass.default(s.kind, inner)
            val h = sqrt(3.0) / 2 * s.pitch
            for (fr in listOf(0.34, 0.4, 0.5, 0.6)) {
                val tip = fr * h
                val n = "${s.label} ${cls.label} R ${fr}H(${f4(tip)})"
                val r = G76Calc.calc(
                    G76Input(inner = inner, spec = s, cls = cls, zStart = 5.0, zEnd = -30.0, firstCut = 0.25 * s.pitch, target = 80.0, tipR = tip, xClear = 0.2),
                )
                val l = ThreadTol.limits(s, cls)
                val pd = if (inner) l.pitchDia.min + 0.8 * l.pitchDia.width else l.pitchDia.max - 0.8 * l.pitchDia.width
                val crest = if (inner) l.crest.min + 0.8 * l.crest.width else l.crest.max - 0.8 * l.crest.width
                val depth = tipDepthBelowPitchLine(s.pitch, tip)
                val xWant = if (inner) pd + 2 * depth else pd - 2 * depth
                val xNc = floor(xWant * 1000 + 0.5) / 1000
                val k = abs(crest - xNc) / 2
                if (2 * toolHalfWidth(tip, k) > s.pitch) {
                    cut++
                    bad.check(r.solution == null && r.warnings == listOf(G76Warning.TIP_TOO_BIG)) {
                        "$n: 산봉우리 높이 날 폭 ${f4(2 * toolHalfWidth(tip, k))} > P — 멈춰야 하는데 ${r.warnings}"
                    }
                    continue
                }
                val sol = r.solution
                if (sol == null) {
                    bad.items += "$n: 멈춤 ${r.warnings} — 날 단면으로는 X ${f4(xWant)}"
                    continue
                }
                bad.near(xWant, sol.x, 1e-6, "$n: X")
            }
        }
        bad.check(cut in 1..6) { "산봉우리를 깎는 경우 $cut" }
        bad.done()
    }

    /**
     * 회전수 · 분당 이송 · 방향. Haas ST/TL 워크북 112쪽 "THREAD CHART" 의 "T.P.I. x 125 I.P.M. = RPM"(7 산 875 … 40 산 5000 RPM) —
     * 그 회전수의 분당 이송은 모두 125 인치/분 = 3175 mm/min. FANUC 은 나사에 G97(회전수 일정)이라 첫 줄 `G97 S…`.
     * 방향: 둘째 줄 Z = 끝 Z, G00 Z = 시작 Z, 역가공도 X · P · Q · F 는 같다(방향은 W = 끝 − 시작 의 부호). 순서가 거꾸로면 Z_ORDER.
     */
    @Test
    fun g76RpmFeedAndDirection() {
        val bad = Bad("G76 회전수 · 분당 이송 · 방향")
        val chart = listOf(
            un("1-1/8", 7), un("1", 8), un("7/8", 9), un("3/4", 10), un("5/8", 11), un("1", 12), un("1/2", 13), un("7/16", 14),
            un("3/8", 16), un("5/16", 18), un("1/4", 20), un("#10", 24), un("1/4", 28), un("#6", 32), un("#8", 36), un("#4", 40),
        )
        val rpms = listOf(875, 1000, 1125, 1250, 1375, 1500, 1625, 1750, 2000, 2250, 2500, 3000, 3500, 4000, 4500, 5000)
        for ((s, rpm) in chart.zip(rpms)) {
            bad.check(rpm == s.tpi!! * 125) { "표 자료: ${s.tpi} 산 $rpm RPM" }
            val base = G76Input(spec = s, zStart = 5.0, zEnd = -20.0, firstCut = 0.1)
            val a = G76Calc.calc(base).solution ?: run { bad.items += "${s.label}: 멈춤"; null } ?: continue
            val b = G76Calc.calc(base.copy(rpm = rpm.toDouble())).solution ?: run { bad.items += "${s.label} S$rpm: 멈춤"; null } ?: continue
            bad.check(b.program == listOf("G97 S$rpm") + a.program) { "${s.label}: 프로그램 ${b.program}" }
            bad.near(3175.0, b.feedPerMin ?: -1.0, 1e-6, "${s.label} S$rpm 분당 이송(125 IPM)")
            bad.check(a.feedPerMin == null) { "${s.label}: 회전수 없는데 분당 이송 ${a.feedPerMin}" }
        }
        for (s in listOf(metric(10.0, 1.5), un("1/4", 20))) for (inner in listOf(false, true)) {
            val f = G76Calc.calc(G76Input(inner = inner, spec = s, zStart = 5.0, zEnd = -20.0, firstCut = 0.1)).solution
            val rv = G76Calc.calc(G76Input(inner = inner, spec = s, dir = ThreadDir.REVERSE, zStart = -25.0, zEnd = 0.0, firstCut = 0.1)).solution
            if (f == null || rv == null) {
                bad.items += "${s.label} 내경 $inner: 멈춤"
                continue
            }
            bad.check(word(f.nc[1], 'Z') == "-20." && word(rv.nc[1], 'Z') == "0.") { "${s.label}: 끝 Z ${f.nc[1]} / ${rv.nc[1]}" }
            bad.check(rv.program.first().endsWith(" Z-25.") && f.program.first().endsWith(" Z5.")) { "${s.label}: 시작 ${f.program.first()} / ${rv.program.first()}" }
            for (k in listOf('X', 'P', 'Q', 'F')) bad.check(word(f.nc[1], k) == word(rv.nc[1], k)) { "${s.label}: 역가공 $k ${rv.nc[1]} ≠ ${f.nc[1]}" }
            bad.check(f.nc[0] == rv.nc[0] && f.passes == rv.passes) { "${s.label}: 역가공 첫 줄 · 분석표가 다르다" }
            // 시작 X: 숫나사 바깥지름 최대 + 2 × 여유(빈칸 1), 암나사 안지름 최소 − 2 × 여유
            val sx = if (inner) f.limits.crest.min - 2.0 else f.limits.crest.max + 2.0
            bad.check(word(f.program.first(), 'X')!!.toDouble().let { abs(it - sx) <= 0.0005 + 1e-9 }) { "${s.label}: 시작 X ${f.program.first()} (기대 ${f4(sx)})" }
        }
        for ((dir, zs, ze) in listOf(
            Triple(ThreadDir.FORWARD, 0.0, 0.0), Triple(ThreadDir.FORWARD, -5.0, 10.0),
            Triple(ThreadDir.REVERSE, 0.0, 0.0), Triple(ThreadDir.REVERSE, 10.0, -5.0),
        )) {
            val r = G76Calc.calc(G76Input(spec = metric(10.0, 1.5), dir = dir, zStart = zs, zEnd = ze, firstCut = 0.1))
            bad.check(r.solution == null && r.warnings == listOf(G76Warning.Z_ORDER)) { "$dir Z$zs → $ze: ${r.warnings}" }
        }
        bad.done()
    }

    /**
     * 멈춤이 따로 계산한 조건과 맞는지: FINISH_GE_HEIGHT(d ≥ k — 매뉴얼 "d < k"), FIRST_CUT_LT_MIN(Δd < Δdmin), CLEARANCE_TOO_BIG
     * (암나사 시작 X = 안지름 최소 − 2 × 여유 ≤ 0). k 는 날 단면 이분법으로 낸 X 를 0.001 로 반올림해 목표 산봉우리 지름에서 잰다.
     * 멈춤 ⇔ 조건 하나 이상, 그리고 낸 경고가 성립한 조건 가운데 하나.
     */
    @Test
    fun g76StopsAgreeWithIndependentConditions() {
        val bad = Bad("G76 멈춤 조건")
        var stops = 0
        var cases = 0
        for (s in listOf(metric(3.0, 0.5), metric(10.0, 1.5), un("#6", 32), un("1/4", 20))) for (inner in listOf(false, true)) {
            val cls = ThreadClass.default(s.kind, inner)
            val l = ThreadTol.limits(s, cls)
            val h = sqrt(3.0) / 2 * s.pitch
            val tip = if (inner) h / 8 else h / 6
            val pd = if (inner) l.pitchDia.min + 0.8 * l.pitchDia.width else l.pitchDia.max - 0.8 * l.pitchDia.width
            val crest = if (inner) l.crest.min + 0.8 * l.crest.width else l.crest.max - 0.8 * l.crest.width
            val depth = tipDepthBelowPitchLine(s.pitch, tip)
            val xNc = floor((if (inner) pd + 2 * depth else pd - 2 * depth) * 1000 + 0.5) / 1000
            val kUm = floor(abs(if (inner) xNc - crest else crest - xNc) / 2 * 1000 + 0.5).toLong()
            for (dFrac in listOf(0.0, 0.5, 0.97, 1.0, 1.2)) for (first in listOf(0.02, 0.035, 0.1)) for (minCut in listOf(0.0, 0.035, 0.05)) {
                for (xc in listOf(0.2, 1.0, 1.5, 3.0)) {
                    val d = floor(dFrac * kUm + 0.5) / 1000
                    val res = G76Calc.calc(
                        G76Input(inner = inner, spec = s, zStart = 5.0, zEnd = -20.0, target = 80.0, firstCut = first, minCut = minCut, finishAllow = d, xClear = xc),
                    )
                    cases++
                    val want = buildSet {
                        val ge = floor(d * 1000 + 0.5).toLong() >= kUm
                        if (ge) add(G76Warning.FINISH_GE_HEIGHT)
                        val lt = floor(first * 1000 + 0.5) < floor(minCut * 1000 + 0.5)
                        if (lt) add(G76Warning.FIRST_CUT_LT_MIN)
                        if (inner && l.crest.min - 2 * xc <= 0) add(G76Warning.CLEARANCE_TOO_BIG)
                        // 절입 횟수(거친 + 정삭 2) 1000 회 넘음 — 따로 짠 FANUC 절입표로 센다
                        if (!ge && !lt && fanucSchedule(kUm / 1000.0, d, first, minCut, 2).size > G76Calc.MAX_PASSES) add(G76Warning.TOO_MANY_PASSES)
                    }
                    val n = "${s.label} ${cls.label} d$d Δd$first Δdmin$minCut 여유$xc"
                    if (want.isEmpty()) {
                        bad.check(res.solution != null) { "$n: 멈춤 ${res.warnings} — 따로 본 조건은 계산 가능(k ${kUm}μm)" }
                    } else {
                        stops++
                        bad.check(res.solution == null && res.warnings.size == 1 && res.warnings[0] in want) { "$n: ${res.warnings} (성립한 조건 $want)" }
                    }
                }
            }
        }
        bad.check(stops > 100 && cases - stops > 100) { "경우 $cases · 멈춤 $stops" }
        bad.done()
    }

    /**
     * TOO_MANY_PASSES 경계 — KDoc "절입 횟수가 MAX_PASSES(1000)를 넘는다", 분석표는 거친 절입 + 정삭 m 회. 따로 짠 절입표로 거친 횟수 R 을 세고
     * m = 1000 − R(합 1000 — 숫자가 나와야)과 m = 1001 − R(합 1001 — 멈춰야)을 넣어 본다. 나온 분석표는 늘 1000 줄 이하여야 한다.
     */
    @Test
    fun g76PassLimitBoundary() {
        val bad = Bad("G76 절입 횟수 한도(1000)")
        val s = metric(10.0, 1.5)
        val k = assertNotNull(G76Calc.calc(G76Input(spec = s, zStart = 5.0, zEnd = -20.0, firstCut = 0.3, minCut = 0.0, finishAllow = 0.02)).solution).height
        var tried = 0
        for (um in 25..40) {
            val first = um / 1000.0
            val rough = fanucSchedule(k, 0.02, first, 0.0, 1).size - 1
            for ((total, stop) in listOf(1000 to false, 1001 to true)) {
                val m = total - rough
                if (m !in 1..99) continue
                tried++
                val r = G76Calc.calc(G76Input(spec = s, zStart = 5.0, zEnd = -20.0, firstCut = first, minCut = 0.0, finishAllow = 0.02, finishCount = m))
                val n = "Δd $first · 거친 $rough + 정삭 $m = $total 회"
                if (stop) bad.check(r.solution == null && r.warnings == listOf(G76Warning.TOO_MANY_PASSES)) { "$n: 멈추지 않음(분석표 ${r.solution?.passes?.size} 줄)" }
                else bad.check(r.solution?.passes?.size == total) { "$n: ${r.warnings} 분석표 ${r.solution?.passes?.size}" }
            }
        }
        bad.check(tried >= 2) { "경계 경우 $tried" }
        bad.done()
    }

    // ================================================================ 2. G12.1 — NC 를 따로 읽어 경로 확인

    /** 극좌표 평면의 점(X 반경 · C mm). X 오른쪽 · C 위 */
    private data class V(val x: Double, val c: Double) {
        operator fun plus(o: V) = V(x + o.x, c + o.c)
        operator fun minus(o: V) = V(x - o.x, c - o.c)
        operator fun times(k: Double) = V(x * k, c * k)
        infix fun dot(o: V) = x * o.x + c * o.c
        infix fun cross(o: V) = x * o.c - c * o.x
        val len: Double get() = hypot(x, c)
        fun unit() = this * (1 / len)

        /** 반시계 90° = 진행 왼쪽 */
        fun left() = V(-c, x)
    }

    private sealed interface Mv {
        val s: V
        val e: V
    }

    private data class Ln(override val s: V, override val e: V) : Mv

    private data class Ac(override val s: V, override val e: V, val ctr: V, val rad: Double, val ccw: Boolean) : Mv

    /** 읽은 이동 한 줄. state: G40 · G41(보정 중) · ON(G41 시작 줄) · OFF(G40 취소 줄) */
    private class Blk(val text: String, val mv: Mv, val state: String)

    private class NcRead(val moves: List<Blk>, val startR: Double?, val zAtG12: Double?, val zAfter: Double?, val errs: List<String>)

    private fun dirV(t: Double) = V(cos(t), sin(t))
    private fun ang(v: V) = atan2(v.c, v.x)
    private fun wrap(a: Double): Double {
        val m = a % (2 * PI)
        return if (m < 0) m + 2 * PI else m
    }

    private fun sweep(a: Ac): Double {
        val a0 = ang(a.s - a.ctr)
        val a1 = ang(a.e - a.ctr)
        return if (a.ccw) wrap(a1 - a0) else wrap(a0 - a1)
    }

    /** R 지령 원호(짧은 호, R > 0): G03 이면 중심이 현의 왼쪽 */
    private fun arcR(s: V, e: V, rr: Double, ccw: Boolean, errs: MutableList<String>, t: String): Ac {
        val ch = e - s
        val l = ch.len
        val h2 = rr * rr - l * l / 4
        if (h2 < -1e-3) errs += "R 이 현의 반보다 작다: $t"
        val h = sqrt(max(0.0, h2))
        val u = ch.unit()
        val mid = (s + e) * 0.5
        return Ac(s, e, if (ccw) mid + u.left() * h else mid - u.left() * h, rr, ccw)
    }

    /**
     * NC 해석: G12.1 앞은 선반(X 직경, C 각도), G12.1 은 C = 0 에서 시작(매뉴얼 "hypothetical axis is at coordinate 0"),
     * 모드 안 X 는 [xDia] 면 직경 · 아니면 반경, C 는 mm. G41 · G40 은 G01 에서만, 모드 안 G00 · Z 없음, 모드 안 첫 이동에 F.
     */
    private fun readNc(lines: List<String>, xDia: Boolean): NcRead {
        val errs = mutableListOf<String>()
        val moves = mutableListOf<Blk>()
        var polar = false
        var comp = "G40"
        var x: Double? = null
        var c: Double? = null
        var z: Double? = null
        var zAtG12: Double? = null
        var zAfter: Double? = null
        var startR: Double? = null
        var g98 = false
        var needF = false
        var afterG13 = false
        for (raw in lines) {
            val t = raw.trim()
            if (t.startsWith("(")) continue
            val toks = t.split(' ').filter { it.isNotEmpty() }
            val gs = toks.filter { it.startsWith("G") }.toSet()
            fun w(k: Char): Double? = toks.firstOrNull { it.length > 1 && it[0] == k }?.substring(1)?.toDouble()
            when {
                t == "G98" -> g98 = true
                "G12.1" in gs -> {
                    if (toks.size != 1) errs += "G12.1 이 혼자가 아니다: $t"
                    if (!g98) errs += "G12.1 앞에 G98(분당 이송) 없음"
                    if (comp != "G40") errs += "G12.1 이 G40 상태가 아니다"
                    if (c != 0.0) errs += "G12.1 앞 C 가 0 이 아니다: $c"
                    if (x == null) {
                        errs += "G12.1 앞 X 없음"
                        return NcRead(moves, null, null, null, errs)
                    }
                    polar = true
                    startR = x
                    c = 0.0
                    needF = true
                    zAtG12 = z
                }
                "G13.1" in gs -> {
                    if (toks.size != 1) errs += "G13.1 이 혼자가 아니다: $t"
                    if (comp != "G40") errs += "G13.1 이 G40 상태가 아니다(PS0037)"
                    polar = false
                    afterG13 = true
                }
                !polar -> {
                    w('X')?.let { x = it / 2 }
                    w('C')?.let { c = it }
                    w('Z')?.let {
                        z = it
                        if (afterG13) zAfter = it
                    }
                    if (afterG13 && (w('X') != null || w('C') != null)) errs += "G13.1 뒤 X/C 이동: $t"
                }
                else -> {
                    if ("G00" in gs) errs += "모드 안 G00: $t"
                    if (w('Z') != null) errs += "모드 안 Z: $t"
                    if (needF) {
                        if (w('F') == null) errs += "모드 안 첫 이동에 F 없음: $t"
                        needF = false
                    }
                    val s = V(x!!, c!!)
                    val e = V(w('X')?.let { if (xDia) it / 2 else it } ?: s.x, w('C') ?: s.c)
                    val g = listOf("G01", "G02", "G03").firstOrNull { it in gs } ?: run { errs += "이동 G 없음: $t"; "G01" }
                    val on = "G41" in gs
                    val off = "G40" in gs
                    if ("G42" in gs) errs += "G42: $t"
                    if ((on || off) && g != "G01") errs += "보정 시작 · 취소가 직선이 아니다(PS0034): $t"
                    val r = w('R')
                    val mv: Mv = if (g == "G01") Ln(s, e) else if (r == null) {
                        errs += "원호에 R 없음: $t"
                        Ln(s, e)
                    } else {
                        arcR(s, e, r, g == "G03", errs, t)
                    }
                    moves += Blk(t, mv, if (on) "ON" else if (off) "OFF" else comp)
                    if (on) comp = "G41"
                    if (off) comp = "G40"
                    x = e.x
                    c = e.c
                }
            }
        }
        if (polar) errs += "G13.1 없이 끝남"
        return NcRead(moves, startR, zAtG12, zAfter, errs)
    }

    private fun sampleLine(s: V, e: V, step: Double): List<V> {
        val n = max(1, ceil((e - s).len / step).toInt())
        return (0..n).map { s + (e - s) * (it.toDouble() / n) }
    }

    private fun sampleArc(ctr: V, rad: Double, a0: Double, a1: Double, step: Double): List<V> {
        val n = max(1, ceil(abs(a1 - a0) * rad / step).toInt())
        return (0..n).map { ctr + dirV(a0 + (a1 - a0) * it / n) * rad }
    }

    private fun sample(m: Mv, step: Double): List<V> = when (m) {
        is Ln -> sampleLine(m.s, m.e, step)
        is Ac -> {
            val a0 = ang(m.s - m.ctr)
            val sw = sweep(m)
            sampleArc(m.ctr, m.rad, a0, a0 + if (m.ccw) sw else -sw, step)
        }
    }

    private fun segDist(p: V, s: V, e: V): Double {
        val v = e - s
        val l2 = v dot v
        if (l2 <= 1e-18) return (p - s).len
        val t = (((p - s) dot v) / l2).coerceIn(0.0, 1.0)
        return (p - (s + v * t)).len
    }

    private fun dist(p: V, m: Mv): Double = when (m) {
        is Ln -> segDist(p, m.s, m.e)
        is Ac -> {
            val a0 = ang(m.s - m.ctr)
            val a = ang(p - m.ctr)
            val rel = if (m.ccw) wrap(a - a0) else wrap(a0 - a)
            if (rel <= sweep(m)) abs((p - m.ctr).len - m.rad) else min((p - m.s).len, (p - m.e).len)
        }
    }

    private fun tanStart(m: Mv): V = when (m) {
        is Ln -> (m.e - m.s).unit()
        is Ac -> (m.s - m.ctr).unit().left() * (if (m.ccw) 1.0 else -1.0)
    }

    private fun tanEnd(m: Mv): V = when (m) {
        is Ln -> (m.e - m.s).unit()
        is Ac -> (m.e - m.ctr).unit().left() * (if (m.ccw) 1.0 else -1.0)
    }

    private fun angDeg(a: V, b: V) = abs(atan2(a cross b, a dot b)) * 180 / PI

    /**
     * 둥근 정다각형 — docs 의 정의에서 따로 만든다: 변 k 의 바깥 법선 = X 에서 C 쪽으로 90° + 회전 + k·360°/n, 중심에서 변까지 a,
     * 모서리 R = rc(core = 중심에서 변까지 a − rc 인 다각형을 rc 만큼 부풀린 것).
     */
    private inner class Poly(val n: Int, val a: Double, val rc: Double, rotDeg: Double) {
        private val rot = rotDeg * PI / 180
        private val ap = a - rc

        fun nrm(k: Int) = dirV(PI / 2 + rot + 2 * PI * k / n)

        /** 변 k 와 k+1 사이의 core 꼭짓점 */
        fun core(k: Int) = dirV(PI / 2 + rot + 2 * PI * k / n + PI / n) * (ap / cos(PI / n))

        /** 부호 있는 거리(밖 +) */
        fun sd(p: V): Double {
            val m = (0 until n).maxOf { (p dot nrm(it)) - ap }
            val coreSd = if (m <= 0) m else (0 until n).minOf { segDist(p, core(it - 1), core(it)) }
            return coreSd - rc
        }

        fun boundary(step: Double): List<V> = buildList {
            for (k in 0 until n) {
                addAll(sampleLine(core(k - 1) + nrm(k) * rc, core(k) + nrm(k) * rc, step))
                if (rc > 0) {
                    val a0 = PI / 2 + rot + 2 * PI * k / n
                    addAll(sampleArc(core(k), rc, a0, a0 + 2 * PI / n, step))
                }
            }
        }
    }

    private class Case(val inp: PolarInput) {
        val tag = "${inp.shape} ${if (inp.inner) "내측" else "외측"} W${inp.width} 지름${inp.od} R${inp.cornerR ?: 0.0} 회전${inp.rotationDeg ?: 0.0} " +
            "D${inp.toolDia} ${if (inp.xDiameter) "X직경" else "X반경"}"
    }

    /** 한 경우를 두 프로그램 모두 확인. 틀린 것은 [bad] 에 모은다 */
    private fun checkPolar(cs: Case, sol: PolarSolution, warnings: List<PolarWarning>, bad: Bad) {
        val inp = cs.inp
        val tag = cs.tag
        val inner = inp.inner
        val flat = inp.shape == PolarShape.TWO_FLAT
        val r = inp.toolDia!! / 2
        val a = inp.width!! / 2
        val rs = inp.od!! / 2
        val clear = inp.clear ?: 1.0
        val rot = inp.rotationDeg ?: 0.0
        val rc = if (flat) 0.0 else inp.cornerR ?: 0.0
        val tol = 0.003
        val n0 = dirV(PI / 2 + rot * PI / 180)
        val poly = if (flat) null else Poly(inp.shape.sides, a, rc, rot)
        // 내측 R ≤ r 은 G41 프로그램이 날카로운 꼭짓점(문서 "보정 두 프로그램") — 공구가 못 가는 모서리는 r 로 남는다
        val sharpInner = inner && rc <= r + 1e-9
        val drawn = if (flat) null else Poly(inp.shape.sides, a, if (sharpInner) 0.0 else rc, rot)

        /** 다 깎은 단면에서 공구 중심까지(외측: 다각형 ∩ 소재의 거리 하한, 내측: 다각형 안 깊이) */
        fun clearance(p: V): Double = when {
            flat -> max(abs(p dot n0) - a, p.len - rs)
            inner -> -poly!!.sd(p)
            else -> max(poly!!.sd(p), p.len - rs)
        }

        // 공구가 닿아야 할 둘레(다 깎은 단면 중 깎는 곳)
        val targets: List<V> = when {
            flat -> {
                val half = sqrt(rs * rs - a * a)
                listOf(1.0, -1.0).flatMap { sg -> sampleLine(n0 * (sg * a) - n0.left() * half, n0 * (sg * a) + n0.left() * half, 0.05) }
            }
            inner -> Poly(inp.shape.sides, a, max(rc, r), rot).boundary(0.05)
            else -> poly!!.boundary(0.05).filter { it.len <= rs }
        }

        for ((name, prog, machine) in listOf(Triple("G41", sol.ncG41G42, true), Triple("G40", sol.ncG40, false))) {
            val t = "$tag [$name]"
            val rd = readNc(prog.lines, inp.xDiameter)
            rd.errs.forEach { bad.items += "$t: $it" }
            val startR = rd.startR ?: continue
            if (inner) {
                bad.check(startR <= rs - r - clear + tol && startR >= -tol) { "$t: 시작 X(반경) ${f4(startR)} 가 구멍 − 공구 − 여유 ${f4(rs - r - clear)} 안이 아니다" }
            } else {
                bad.check(startR >= rs + r + clear - tol) { "$t: 시작 X(반경) ${f4(startR)} < 소재 + 공구 + 여유 ${f4(rs + r + clear)}" }
            }
            bad.near(sol.startX, startR, 0.001, "$t: 시작 X")
            bad.check(rd.zAtG12 != null && abs(rd.zAtG12 - inp.depthZ!!) <= 1e-9) { "$t: G12.1 때 Z ${rd.zAtG12} ≠ 깊이 ${inp.depthZ}" }
            bad.check(rd.zAfter != null && abs(rd.zAfter - clear) <= 1e-9) { "$t: G13.1 뒤 Z ${rd.zAfter} ≠ 여유 $clear" }

            // 공구 중심이 확실한 움직임: 직접 보정은 전부, 기계 보정은 G40 상태(위치 잡기)만 — 단면을 파고들지 않음
            var gouge = 0
            var worst = 0.0
            for (b in rd.moves) {
                if (machine && b.state != "G40") continue
                for (p in sample(b.mv, 0.05)) {
                    val cl = clearance(p)
                    if (cl < r - tol) {
                        gouge++
                        worst = max(worst, r - cl)
                    }
                }
            }
            bad.check(gouge == 0) { "$t: 공구 중심이 단면에서 r 보다 가까움 ${gouge}점(최대 ${f4(worst)} mm 파고듦)" }

            if (!machine) {
                // 직접 보정: 둘레 모든 점이 경로에서 r 안 → 파고듦 없음과 함께 "공구 반경만큼 떨어진 경로"
                var miss = 0
                var far = 0.0
                for (q in targets) {
                    val d = rd.moves.minOf { dist(q, it.mv) }
                    if (d > r + tol) {
                        miss++
                        far = max(far, d - r)
                    }
                }
                bad.check(miss == 0) { "$t: 둘레 ${miss}점이 공구에 안 닿음(최대 ${f4(far)} mm 남음)" }
                bad.check(rd.moves.none { it.state != "G40" }) { "$t: 직접 보정인데 G41/G42 가 있다" }
                continue
            }

            // 기계 보정: 자르기 묶음 [ON, 진입 원호, 윤곽…, 후퇴 원호, OFF]
            val cuts = mutableListOf<List<Blk>>()
            var cur: MutableList<Blk>? = null
            for (b in rd.moves) {
                when (b.state) {
                    "ON" -> cur = mutableListOf(b)
                    "G41" -> cur?.add(b) ?: run { bad.items += "$t: G41 없이 보정 이동 ${b.text}" }
                    "OFF" -> {
                        cur?.let {
                            it += b
                            cuts += it
                        } ?: run { bad.items += "$t: G41 없이 G40 ${b.text}" }
                        cur = null
                    }
                }
            }
            bad.check(cuts.size == (if (flat) 2 else 1)) { "$t: 보정 묶음 ${cuts.size} 개" }
            val contour = mutableListOf<Mv>()
            for (cut in cuts) {
                if (cut.size < 5) {
                    bad.items += "$t: 보정 묶음이 짧다 ${cut.map { it.text }}"
                    continue
                }
                val start = cut.first().mv
                val off = cut.last().mv
                val leadIn = cut[1].mv
                val leadOut = cut[cut.size - 2].mv
                val body = cut.subList(2, cut.size - 2).map { it.mv }
                contour += body
                bad.check(leadIn is Ac && leadIn.ccw && leadIn.rad > r + 1e-6) { "$t: 진입이 반지름 > r 인 G03 이 아니다: ${cut[1].text}" }
                bad.check(leadOut is Ac && leadOut.ccw && leadOut.rad > r + 1e-6) { "$t: 후퇴가 반지름 > r 인 G03 이 아니다: ${cut[cut.size - 2].text}" }
                bad.check(angDeg(tanEnd(leadIn), tanStart(body.first())) <= 0.3) { "$t: 진입 원호가 윤곽에 접하지 않음" }
                bad.check(angDeg(tanEnd(body.last()), tanStart(leadOut)) <= 0.3) { "$t: 후퇴 원호가 윤곽에 접하지 않음" }
                bad.check(angDeg(tanEnd(start), tanStart(leadIn)) <= 0.3) { "$t: G41 시작 직선이 진입 원호와 한 직선이 아님" }
                bad.check(angDeg(tanEnd(leadOut), tanStart(off)) <= 0.3) { "$t: G40 취소 직선이 후퇴 원호와 한 직선이 아님" }
                // 윤곽은 도면 위(2면폭은 평면 위), 공구 쪽(진행 왼쪽)이 깎는 쪽
                for (m in body) {
                    var off2 = 0.0
                    for (p in sample(m, 0.05)) off2 = max(off2, if (flat) abs(abs(p dot n0) - a) else abs(drawn!!.sd(p)))
                    bad.check(off2 <= tol) { "$t: G41 윤곽이 도면에서 ${f4(off2)} mm 벗어남" }
                    val mid = sample(m, 1e9).let { (it.first() + it.last()) * 0.5 }
                    val p = if (m is Ac) m.ctr + (mid - m.ctr).unit() * m.rad else mid
                    val q = p + (if (m is Ac) (p - m.ctr).unit().left() * (if (m.ccw) 1.0 else -1.0) else tanStart(m)).left() * 0.05
                    val side = if (flat) abs(q dot n0) - a else drawn!!.sd(q)
                    bad.check(if (inner) side < 0 else side > 0) { "$t: 진행 왼쪽(G41)이 깎는 쪽이 아니다" }
                }
            }
            // 도면 둘레를 빠짐없이 지남
            val drawnTargets = if (flat) targets else drawn!!.boundary(0.05)
            val missDrawn = drawnTargets.count { q -> contour.minOf { dist(q, it) } > tol }
            bad.check(missDrawn == 0) { "$t: 도면 둘레 ${missDrawn}점을 G41 윤곽이 안 지남" }
            // 결과 숫자를 G41 윤곽에서 재어 본다
            if (!flat) {
                val lines = contour.filterIsInstance<Ln>()
                val side = if (sharpInner) 2 * a * tan(PI / inp.shape.sides) else sol.flatLength
                lines.drop(1).dropLast(1).forEach { bad.near(side, it.e.minus(it.s).len, 0.002, "$t: 변 길이") }
                if (lines.size >= 2) bad.near(side, (lines.first().e - lines.first().s).len + (lines.last().e - lines.last().s).len, 0.002, "$t: 나뉜 첫 변")
                if (!sharpInner) {
                    val maxR = contour.flatMap { sample(it, 0.01) }.maxOf { it.len }
                    bad.near(sol.cornerDia, 2 * maxR, 0.002, "$t: 꼭짓점 지름")
                }
            } else {
                for (l in contour.filterIsInstance<Ln>()) {
                    val dl = abs(l.s cross (l.e - l.s).unit())
                    bad.near(a, dl, 0.001, "$t: 평면까지 거리")
                    // 현 길이는 dl 에 민감하다(∂현/∂dl = 2dl/√(Rs² − dl²) — 외경이 W 에 가까우면 10 배 넘게) — NC 0.001 반올림이 그만큼 커진다
                    val sens = 2 * dl / sqrt(rs * rs - dl * dl)
                    bad.near(sol.flatLength, 2 * sqrt(rs * rs - dl * dl), 0.002 + sens * 0.001, "$t: 2면폭 현 길이")
                }
            }
        }

        // 살 두께 · 경고 — 소재(구멍) 둘레를 0.05° 로 훑어 잰다
        val stockMax = when {
            flat -> rs - a
            else -> {
                fun stockAt(t: Double): Double = dirV(t).let { if (inner) -poly!!.sd(it * rs) else poly!!.sd(it * rs) }
                // 0.05° 로 훑은 뒤 가장 큰 칸 둘레를 다시 1/2000 로(최댓값이 꺾인 점이라 거친 칸만으로는 수 μm 모자랄 수 있다)
                val best = (0 until 7200).maxBy { stockAt(it * PI / 3600) }
                (-2000..2000).maxOf { stockAt((best + it / 2000.0) * PI / 3600) }
            }
        }
        bad.near(stockMax, sol.stockMax, 0.002, "$tag: 가장 두꺼운 살")
        if (abs(stockMax - 2 * r) > 1e-3) {
            bad.check((PolarWarning.STOCK_LEFT in warnings) == (stockMax > 2 * r)) { "$tag: 살 ${f4(stockMax)} · 공구경 ${2 * r} 인데 경고 $warnings" }
        }
        if (!inner && !flat) {
            val cornerR = poly!!.boundary(0.01).maxOf { it.len }
            if (abs(cornerR - rs) > 1e-3) {
                bad.check((PolarWarning.CORNERS_OUTSIDE_OD in warnings) == (cornerR > rs)) { "$tag: 꼭짓점 반경 ${f4(cornerR)} · 소재 $rs 인데 경고 $warnings" }
            }
        } else {
            bad.check(PolarWarning.CORNERS_OUTSIDE_OD !in warnings) { "$tag: 내측 · 2면폭에 CORNERS_OUTSIDE_OD" }
        }
        bad.check((PolarWarning.INNER_CORNER_LT_TOOL in warnings) == (inner && rc < r - 1e-9)) { "$tag: INNER_CORNER_LT_TOOL 경고 $warnings" }
    }

    /**
     * 외측/내측 × 5 다각형(+ 외측 2면폭) × 회전 4 × 코너 R 4(0 · r/2 · r · 1.6r) × 공구 2 × X 직경/반경 = 656 경우, 두 프로그램씩.
     * W50(중심에서 변 25). 외측 소재는 W + 1.5 × 공구경(살 0.75D ≤ D — 한 바퀴로 깎임), 내측 구멍 Ø30.
     */
    @Test
    fun polarPathsAreOffsetByToolRadiusOrOnDrawing() {
        val bad = Bad("G12.1 경로")
        var n = 0
        val shapes = listOf(PolarShape.TRIANGLE, PolarShape.SQUARE, PolarShape.PENTAGON, PolarShape.HEXAGON, PolarShape.OCTAGON, PolarShape.TWO_FLAT)
        for (shape in shapes) for (inner in listOf(false, true)) {
            if (inner && shape == PolarShape.TWO_FLAT) continue
            for (rot in listOf(0.0, 15.0, -37.5, 100.0)) for (d in listOf(6.0, 10.0)) for (rf in listOf(0.0, 0.5, 1.0, 1.6)) {
                if (shape == PolarShape.TWO_FLAT && rf != 0.0) continue
                for (xDia in listOf(true, false)) {
                    val inp = PolarInput(
                        inner = inner, shape = shape, od = if (inner) 30.0 else 50.0 + 1.5 * d, width = 50.0,
                        cornerR = if (shape == PolarShape.TWO_FLAT) null else rf * d / 2, rotationDeg = rot, toolDia = d, depthZ = -4.0, feed = 150.0,
                        clear = if (d == 6.0) null else 2.0, xDiameter = xDia,
                    )
                    val cs = Case(inp)
                    val res = PolarG12.calc(inp)
                    val sol = res.solution
                    if (sol == null) {
                        bad.items += "${cs.tag}: 멈춤 ${res.warnings}"
                        continue
                    }
                    val before = bad.items.size
                    checkPolar(cs, sol, res.warnings, bad)
                    // 한 경우가 너무 많이 쌓지 않게
                    if (bad.items.size - before > 6) {
                        val extra = bad.items.size - before - 6
                        repeat(extra) { bad.items.removeAt(bad.items.lastIndex) }
                        bad.items += "${cs.tag}: … 외 ${extra}건"
                    }
                    n++
                }
            }
        }
        bad.check(n == 656) { "경우 수 $n" }
        bad.done()
    }

    /**
     * 회전 · 축 약속을 점으로 확인(docs "회전 + 는 X → C(+) 쪽", "육각형 회전 0 은 꼭짓점이 X 축 위", "사각형 회전 0 은 변이 C = ±W/2").
     * 육각형 W60 R0: 회전 0 → 꼭짓점 (X 34.641, C 0) · 회전 15 → (33.461, 8.966) · 회전 30 → X = 30 인 평면. 사각형 W60: C = ±30 평면.
     */
    @Test
    fun polarRotationAndAxisConventions() {
        val bad = Bad("G12.1 회전 · 축 약속")
        fun contourPoints(shape: PolarShape, rot: Double, xDia: Boolean): List<V> {
            val inp = PolarInput(shape = shape, od = 72.0, width = 60.0, rotationDeg = rot, toolDia = 10.0, depthZ = -3.0, feed = 100.0, xDiameter = xDia)
            val sol = assertNotNull(PolarG12.calc(inp).solution)
            val rd = readNc(sol.ncG41G42.lines, xDia)
            return rd.moves.filter { it.state == "G41" }.flatMap { listOf(it.mv.s, it.mv.e) }
        }
        val rv = 30 / cos(PI / 6)
        for (xDia in listOf(true, false)) {
            fun has(pts: List<V>, p: V, what: String) = bad.check(pts.any { (it - p).len <= 0.002 }) { "$what (X직경 $xDia): (${f4(p.x)}, ${f4(p.c)}) 이 G41 윤곽 끝점에 없다" }
            has(contourPoints(PolarShape.HEXAGON, 0.0, xDia), V(rv, 0.0), "육각형 회전 0 꼭짓점")
            has(contourPoints(PolarShape.HEXAGON, 15.0, xDia), dirV(15 * PI / 180) * rv, "육각형 회전 +15 꼭짓점(X → C+ 쪽)")
            has(contourPoints(PolarShape.HEXAGON, 30.0, xDia), V(30.0, rv / 2), "육각형 회전 30 의 X = 30 평면 끝")
            val sq = contourPoints(PolarShape.SQUARE, 0.0, xDia)
            has(sq, V(30.0, 30.0), "사각형 꼭짓점 (30, 30)")
            has(sq, V(-30.0, -30.0), "사각형 꼭짓점 (−30, −30)")
        }
        bad.done()
    }

    /**
     * 무작위 160 경우(씨앗 고정) — 위 격자 밖의 W · 소재/구멍 · 공구 · 코너 R · 회전 · 여유 · X 직경/반경을 같은 확인(checkPolar)으로.
     * 결과 숫자도 도형 식으로 따로: 꼭짓점 지름 2((a − R)/cos(π/n) + R), 변 길이 2(a − R)·tan(π/n), 2면폭은 현 2√(Rs² − a²) · 외경.
     * (내측 R < r 은 G41 이 날카로운 꼭짓점이라 숫자 비교는 뺀다)
     */
    @Test
    fun polarRandomShapes() {
        val bad = Bad("G12.1 무작위")
        val rng = Random(20261028)
        var n = 0
        var tries = 0
        // 칸에 넣는 값처럼 0.001(회전 0.01°)로 — Z 깊이 · 여유는 NC 에 0.001 로 찍히므로
        fun u(lo: Double, hi: Double, dec: Int = 3) = rnd(lo + rng.nextDouble() * (hi - lo), dec)
        while (n < 160 && tries < 2000) {
            tries++
            val shape = PolarShape.entries[rng.nextInt(PolarShape.entries.size)]
            val flat = shape == PolarShape.TWO_FLAT
            val inner = !flat && rng.nextBoolean()
            val w = u(10.0, 80.0)
            val a = w / 2
            val d = if (inner) u(1.0, 0.45 * w) else u(2.0, 16.0)
            val r = d / 2
            val clear = if (rng.nextInt(3) == 0) null else u(0.2, 3.0)
            val c = clear ?: 1.0
            val od = if (inner) {
                val lo = d + 2 * c + 0.2
                if (lo >= w) continue
                u(lo, w)
            } else {
                u(w + 0.5, w + 0.5 + 3 * d)
            }
            val rc = if (flat) null else when (rng.nextInt(5)) {
                0 -> null
                1 -> if (r < 0.6 * a) r else 0.0
                else -> u(0.0, 0.6 * a)
            }
            val inp = PolarInput(
                inner = inner, shape = shape, od = od, width = w, cornerR = rc, rotationDeg = u(-180.0, 180.0, 2), toolDia = d,
                depthZ = -u(1.0, 10.0), feed = u(50.0, 500.0, 0), clear = clear, xDiameter = rng.nextBoolean(),
            )
            val cs = Case(inp)
            val res = PolarG12.calc(inp)
            val sol = res.solution
            if (sol == null) {
                bad.items += "${cs.tag}: 멈춤 ${res.warnings}"
                continue
            }
            n++
            val before = bad.items.size
            checkPolar(cs, sol, res.warnings, bad)
            val rr = rc ?: 0.0
            if (flat) {
                bad.near(od, sol.cornerDia, 1e-6, "${cs.tag}: 2면폭 꼭짓점 지름 = 외경")
                bad.near(2 * sqrt(od * od / 4 - a * a), sol.flatLength, 1e-6, "${cs.tag}: 2면폭 현 길이")
            } else if (!(inner && rr < r - 1e-9)) {
                val k = shape.sides
                bad.near(2 * ((a - rr) / cos(PI / k) + rr), sol.cornerDia, 1e-6, "${cs.tag}: 꼭짓점 지름")
                bad.near(2 * (a - rr) * tan(PI / k), sol.flatLength, 1e-6, "${cs.tag}: 변 길이")
            }
            if (bad.items.size - before > 6) {
                val extra = bad.items.size - before - 6
                repeat(extra) { bad.items.removeAt(bad.items.lastIndex) }
                bad.items += "${cs.tag}: … 외 ${extra}건"
            }
        }
        bad.check(n == 160) { "경우 수 $n (시도 $tries)" }
        bad.done()
    }

    /**
     * 멈춤 — 도형으로 따로 본 까닭: 외측 W ≥ 외경(깎을 살 없음), 내측 구멍 > W(구멍이 다각형보다 큼), 코너 R ≥ a(변이 없음),
     * 내측 a ≤ 2r(진입 원호 자리 없음 — 문서의 조건), 내측 구멍 반경 < r + 여유(공구가 구멍에 못 들어감). 2면폭은 코너 R 을 안 쓴다.
     */
    @Test
    fun polarStopsMatchGeometry() {
        val bad = Bad("G12.1 멈춤")
        fun run(inp: PolarInput, want: PolarWarning?, what: String) {
            val r = PolarG12.calc(inp)
            if (want == null) bad.check(r.solution != null) { "$what: 멈춤 ${r.warnings}" }
            else bad.check(r.solution == null && r.warnings == listOf(want)) { "$what: ${r.warnings} (기대 $want)" }
        }
        val base = PolarInput(shape = PolarShape.HEXAGON, od = 60.0, width = 50.0, toolDia = 10.0, depthZ = -3.0, feed = 100.0)
        run(base, null, "기준")
        run(base.copy(od = 50.0), PolarWarning.W_GE_OD, "외측 W = 외경")
        run(base.copy(od = 49.0), PolarWarning.W_GE_OD, "외측 W > 외경")
        run(base.copy(cornerR = 25.0), PolarWarning.CORNER_R_TOO_BIG, "외측 R = a")
        run(base.copy(cornerR = 24.9), null, "외측 R < a")
        run(base.copy(shape = PolarShape.TWO_FLAT, cornerR = 40.0), null, "2면폭은 코너 R 안 씀")
        val inner = base.copy(inner = true, shape = PolarShape.SQUARE, od = 30.0, width = 50.0, toolDia = 10.0)
        run(inner, null, "내측 기준")
        run(inner.copy(od = 50.0), null, "내측 구멍 = W")
        run(inner.copy(od = 50.5), PolarWarning.HOLE_GT_W, "내측 구멍 > W")
        run(inner.copy(cornerR = 25.0), PolarWarning.CORNER_R_TOO_BIG, "내측 R = a")
        run(inner.copy(toolDia = 25.0, od = 40.0), PolarWarning.TOOL_TOO_BIG, "내측 a = 2r")
        run(inner.copy(toolDia = 24.9, od = 40.0), null, "내측 a > 2r")
        run(inner.copy(od = 12.0, clear = 1.0), null, "내측 구멍 반경 6 = r 5 + 여유 1(시작 X 0)")
        run(inner.copy(od = 11.9, clear = 1.0), PolarWarning.TOOL_GT_HOLE, "내측 구멍 반경 5.95 < r 5 + 여유 1")
        run(inner.copy(od = 12.0, clear = 0.9), null, "내측 구멍 반경 6 > 5 + 0.9")
        bad.done()
    }

    // ================================================================ 3. 절삭조건

    private fun rnd(v: Double, dec: Int): Double {
        val k = 10.0.pow(dec)
        return floor(v * k + 0.5) / k
    }

    /**
     * Mitsubishi Materials "Formulae for Cutting Power"(선삭) 예제 넷 — 교재는 π = 3.14:
     * ① n 700 · Ø50 → vc = 3.14 × 50 × 700 ÷ 1000 = 110 m/min ② n 500 · 분당 120 mm → f 0.24 ③ 100 mm · 1000 min⁻¹ · f 0.2 →
     * 200 mm/min · 0.5 분 = 30 초 ④ R0.8 · f 0.2 → h = 0.2² ÷ (8 × 0.8) × 1000 = 6.25 μm.
     */
    @Test
    fun cuttingMitsubishiTurningExamples() {
        val bad = Bad("Mitsubishi 선삭 예제")
        val a = Cutting.calc(CutInput(d = 50.0, speedFrom = SpeedFrom.N, n = 700.0))
        bad.near(3.14 * 50 * 700 / 1000 * (PI / 3.14), a.vc ?: -1.0, 1e-9, "① vc(식 같음, π 만 다름)")
        bad.check(rnd(a.vc ?: -1.0, 0) == 110.0) { "① vc ${a.vc} → 교재 110" }
        val b = Cutting.calc(CutInput(speedFrom = SpeedFrom.N, n = 500.0, f = 0.24))
        bad.near(120.0, b.vf ?: -1.0, 1e-9, "② f 0.24 × n 500 = 분당 120")
        val c = Cutting.calc(CutInput(speedFrom = SpeedFrom.N, n = 1000.0, f = 0.2, length = 100.0))
        bad.near(200.0, c.vf ?: -1.0, 1e-9, "③ 분당 이송")
        bad.near(0.5, c.timeMin ?: -1.0, 1e-12, "③ 시간(분)")
        bad.near(30.0, c.timeSec ?: -1.0, 1e-9, "③ 시간(초)")
        val d = Cutting.calc(CutInput(f = 0.2, noseR = 0.8))
        bad.near(6.25, d.rz ?: -1.0, 1e-9, "④ 이론 거칠기 h")
        bad.check(d.warnings.isEmpty()) { "④ 경고 ${d.warnings}" }
        bad.done()
    }

    /**
     * Mitsubishi Materials "Formulae for Drilling" 예제 셋: ① n 1350 · Ø12 → vc = 3.14 × 12 × 1350 ÷ 1000 = 50.9 m/min
     * ② f 0.2 · n 1350 → vf 270 mm/min ③ Vc 50 · Ø15 · f 0.15 · 깊이 30 · 1 구멍 → n = 50 × 1000 ÷ (15 × 3.14) = 1061.57,
     * Tc = 30 ÷ (1061.57 × 0.15) = 0.188 분 = 11.3 초. 직접 입력(DrillCatalog.manual)도 같은 n · vf.
     */
    @Test
    fun cuttingMitsubishiDrillingExamples() {
        val bad = Bad("Mitsubishi 드릴 예제")
        val a = Cutting.calc(CutInput(d = 12.0, speedFrom = SpeedFrom.N, n = 1350.0))
        bad.check(rnd(a.vc ?: -1.0, 1) == 50.9) { "① vc ${a.vc} → 교재 50.9" }
        val b = Cutting.calc(CutInput(speedFrom = SpeedFrom.N, n = 1350.0, f = 0.2))
        bad.near(270.0, b.vf ?: -1.0, 1e-9, "② vf")
        val c = Cutting.calc(CutInput(d = 15.0, vc = 50.0, f = 0.15, length = 30.0))
        bad.near(50.0 * 1000 / (15 * 3.14) * (3.14 / PI), c.n ?: -1.0, 1e-9, "③ n(식 같음, π 만 다름)")
        bad.check(rnd(c.n ?: -1.0, 0) == 1061.0) { "③ n ${c.n} (π = 3.14 면 1061.57)" }
        bad.check(rnd(c.timeMin ?: -1.0, 3) == 0.188) { "③ Tc ${c.timeMin} 분 → 교재 0.188" }
        bad.check(rnd(c.timeSec ?: -1.0, 1) == 11.3) { "③ Tc ${c.timeSec} 초 → 교재 11.3" }
        val m = DrillCatalog.manual(15.0, 50.0, 0.15)
        bad.near(c.n ?: -1.0, m.n?.rec ?: -1.0, 1e-9, "③ 직접 입력 n")
        bad.near(0.15 * (c.n ?: -1.0), m.vf?.rec ?: -1.0, 1e-9, "③ 직접 입력 vf")
        bad.check(m.warnings.isEmpty()) { "③ 직접 입력 경고 ${m.warnings}" }
        bad.done()
    }

    /**
     * G96 · G50 S — FANUC Series 0/00/0-Mate Lathe B-61394E/08 9.3 "CONSTANT SURFACE SPEED CONTROL": 주속 일정이면 회전수 = 1000·Vc/(π·D),
     * G50 S 를 넘으면 "clamped at the maximum spindle speed". 따라서 막히기 시작하는 지름 = 1000·Vc/(π·S), 실제 주속 = π·D·min(n, S)/1000.
     * 경계 바로 위 · 아래(지름 × (1 ± 1e-6))와 격자.
     */
    @Test
    fun cuttingG96ClampFollowsFanuc() {
        val bad = Bad("G96 · G50 S 막힘")
        for (vc in listOf(50.0, 120.0, 200.0, 350.0)) for (s in listOf(500.0, 2000.0, 4000.0)) {
            val dLim = 1000 * vc / (PI * s)
            for (d in listOf(5.0, 10.0, 20.0, 50.0, 120.0, dLim * (1 - 1e-6), dLim * (1 + 1e-6))) {
                val res = Cutting.calc(CutInput(d = d, vc = vc, smax = s))
                val g = res.g96 ?: run { bad.items += "Vc$vc S$s Ø$d: G96 결과 없음"; null } ?: continue
                val nCmd = 1000 * vc / (PI * d)
                val clamped = nCmd > s
                val n = min(nCmd, s)
                val tag = "Vc$vc S$s Ø${f4(d)}"
                bad.near(dLim, g.dLimit, 1e-9 * dLim, "$tag 막히는 지름")
                bad.near(nCmd, g.nCommand ?: -1.0, 1e-9 * nCmd, "$tag 지령 회전수")
                bad.near(n, g.n ?: -1.0, 1e-9 * n, "$tag 실제 회전수")
                bad.check(g.clamped == clamped && (CutWarning.RPM_CLAMPED in res.warnings) == clamped) { "$tag: 막힘 ${g.clamped} · ${res.warnings} (기대 $clamped)" }
                bad.near(PI * d * n / 1000, g.vcActual ?: -1.0, 1e-9 * vc, "$tag 실제 주속")
            }
        }
        bad.done()
    }

    /**
     * 카탈로그 조회 — 모든 메이커 · 시리즈 · 행 · 이송 구간에서:
     * 구간 양 끝과 가운데 = 그 구간의 f 그대로 · 경고 없음(시리즈 범위 밖이면 OUTSIDE_SERIES 만), n = 1000·Vc/(π·D)(회전수 표는 표 값 · Vc 환산),
     * vf = f·n(최소끼리 · 권장끼리 · 최대끼리), 출처 · 쪽. 구간 사이 가운데 = NOT_IN_FEED_TABLE · 앞뒤 구간, f · vf 없음.
     * 첫 구간 0.001 아래 · 끝 구간 0.001 위 = OUTSIDE_ROW 만(숫자 없음), 다른 행 안내는 그 직경이 드는 같은 ISO 군 행만.
     */
    @Test
    fun drillCatalogBandEdges() {
        val bad = Bad("드릴 카탈로그 구간 경계")
        var looks = 0
        for (mk in DrillCatalog.makers) for (s in mk.series) for ((ci, c) in s.conditions.withIndex()) {
            val tag = "${mk.key} ${s.series} #$ci"
            fun outsideSeries(d: Double) = d < s.diameterMin - 1e-9 || d > s.diameterMax + 1e-9
            if (c.feeds.isEmpty()) {
                val res = DrillCatalog.lookup(mk, s, ci, (s.diameterMin + s.diameterMax) / 2)
                bad.check(res.warnings.contains(DrillWarning.NO_FEED_IN_TABLE) && res.f == null) { "$tag: 이송 없는 행 ${res.warnings}" }
                val out = DrillCatalog.lookup(mk, s, ci, s.diameterMax + 0.001)
                bad.check(out.warnings == listOf(DrillWarning.OUTSIDE_ROW)) { "$tag: 시리즈 위 ${out.warnings}" }
                looks += 2
                continue
            }
            for ((j, band) in c.feeds.withIndex()) {
                for (d in listOf(band.dMin, band.dMax, (band.dMin + band.dMax) / 2)) {
                    val res = DrillCatalog.lookup(mk, s, ci, d)
                    looks++
                    val n = "$tag Ø${f4(d)}"
                    val wantW = buildList {
                        if (outsideSeries(d)) add(DrillWarning.OUTSIDE_SERIES)
                        if (!c.rpmTable && c.vcMin == null && c.vcRec == null && c.vcMax == null) add(DrillWarning.NO_VC_IN_TABLE)
                    }
                    bad.check(res.warnings == wantW) { "$n: 경고 ${res.warnings} (기대 $wantW)" }
                    bad.check(res.band == band) { "$n: 구간 ${res.band} ≠ $band" }
                    val wantF = listOf(band.fMin, band.fRec, band.fMax)
                    bad.check(wantF.all { it == null } && res.f == null || res.f == Span3(band.fMin, band.fRec, band.fMax)) { "$n: f ${res.f} ≠ $wantF" }
                    val wantN: List<Double?> = if (c.rpmTable) listOf(null, band.rpm?.toDouble(), null)
                    else listOf(c.vcMin, c.vcRec, c.vcMax).map { v -> v?.let { 1000 * it / (PI * d) } }
                    val gotN = listOf(res.n?.min, res.n?.rec, res.n?.max)
                    bad.check(wantN.zip(gotN).all { (w, g) -> (w == null && g == null) || (w != null && g != null && abs(w - g) <= 1e-9 * w) }) { "$n: n $gotN ≠ $wantN" }
                    if (c.rpmTable) bad.near(PI * d * (band.rpm ?: 0) / 1000, res.vc?.rec ?: -1.0, 1e-9, "$n: Vc 환산")
                    val nMin = wantN[0] ?: wantN[1]
                    val nMax = wantN[2] ?: wantN[1]
                    val wantVf = listOf(band.fMin to nMin, band.fRec to wantN[1], band.fMax to nMax).map { (f, nn) -> if (f != null && nn != null) f * nn else null }
                    val gotVf = listOf(res.vf?.min, res.vf?.rec, res.vf?.max)
                    bad.check(wantVf.zip(gotVf).all { (w, g) -> (w == null && g == null) || (w != null && g != null && abs(w - g) <= 1e-9 * w) }) { "$n: vf $gotVf ≠ $wantVf" }
                    bad.check(res.source != null && res.source?.id == c.sourceId && res.page == c.page) { "$n: 출처 ${res.source?.id} · ${res.page}" }
                }
                val next = c.feeds.getOrNull(j + 1)
                if (next != null && next.dMin - band.dMax > 2e-6) {
                    val d = (band.dMax + next.dMin) / 2
                    val res = DrillCatalog.lookup(mk, s, ci, d)
                    looks++
                    bad.check(DrillWarning.NOT_IN_FEED_TABLE in res.warnings && res.band == null && res.f == null && res.vf == null) { "$tag Ø${f4(d)} 구간 사이: ${res.warnings} f ${res.f}" }
                    bad.check(res.below == band && res.above == next) { "$tag Ø${f4(d)} 구간 사이: 앞뒤 ${res.below} ${res.above}" }
                    if (c.rpmTable) bad.check(res.n == null && res.vc == null) { "$tag Ø${f4(d)} 회전수 표 사이인데 n ${res.n}" }
                }
            }
            for (d in listOf(c.feeds.first().dMin - 0.001, c.feeds.last().dMax + 0.001)) {
                if (d <= 0) continue
                val res = DrillCatalog.lookup(mk, s, ci, d)
                looks++
                bad.check(res.warnings == listOf(DrillWarning.OUTSIDE_ROW) && res.vc == null && res.n == null && res.f == null) { "$tag Ø${f4(d)} 행 밖: ${res.warnings}" }
                for (alt in res.alternatives) {
                    val o = s.conditions[alt]
                    val inRow = if (o.feeds.isEmpty()) d >= s.diameterMin && d <= s.diameterMax else d >= o.feeds.first().dMin - 1e-9 && d <= o.feeds.last().dMax + 1e-9
                    bad.check(alt != ci && o.isoGroup == c.isoGroup && inRow) { "$tag Ø${f4(d)}: 안내한 행 #$alt 이 맞지 않음" }
                }
            }
        }
        bad.check(looks > 9000) { "조회 수 $looks" }
        bad.done()
    }

    /** 출처 칸 빠짐 없음: 메이커 출처(id · 제목 · http URL), 모든 행의 출처 id 가 있고 쪽이 있음, 재료분석의 군 · 예 출처 */
    @Test
    fun sourcesAreNeverMissing() {
        val bad = Bad("출처 칸")
        for (mk in DrillCatalog.makers) {
            bad.check(mk.catalog.sources.isNotEmpty()) { "${mk.key}: 출처 목록이 비었다" }
            for (src in mk.catalog.sources) {
                bad.check(src.id.isNotBlank() && src.title.isNotBlank()) { "${mk.key}: 출처 id · 제목 빈칸 $src" }
                bad.check(src.url.startsWith("https://") || src.url.startsWith("http://")) { "${mk.key} ${src.id}: URL '${src.url}'" }
            }
            for (s in mk.series) for (c in s.conditions) {
                bad.check(mk.source(c.sourceId) != null) { "${mk.key} ${s.series} ${c.material}: 출처 id '${c.sourceId}' 없음" }
                bad.check(c.page.isNotBlank()) { "${mk.key} ${s.series} ${c.material}: 쪽 빈칸" }
                bad.check(c.material.isNotBlank()) { "${mk.key} ${s.series}: 재질 빈칸" }
            }
        }
        for (g in WorkMaterials.groups) {
            bad.check(g.source.isNotBlank()) { "${g.group}: 군 출처 빈칸" }
            for (e in g.examples) bad.check(e.source.isNotBlank()) { "${g.group} ${e.name}: 출처 빈칸" }
        }
        bad.done()
    }

    private companion object {
        fun rows(t: String): List<List<String>> = t.trimIndent().lines().filter { it.isNotBlank() }.map { it.trim().split(Regex("\\s+")) }

        fun f6(v: Double): String = (floor(v * 1e6 + 0.5) / 1e6).toString()

        fun f4(v: Double): String = (floor(v * 1e4 + 0.5) / 1e4).toString()

        /** 범위 밖으로 나간 양(안이면 0) */
        fun outside(v: Double, r: ClosedFloatingPointRange<Double>) = if (v < r.start) v - r.start else if (v > r.endInclusive) v - r.endInclusive else 0.0
    }
}
