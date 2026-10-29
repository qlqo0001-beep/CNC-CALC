package com.bmt.nosecalc

import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.sim.StockSim
import com.bmt.nosecalc.calc.sim.StockSpec
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.sim.ToolShape
import com.bmt.nosecalc.screens.round.COMPANY_PRESETS
import com.bmt.nosecalc.screens.round.HOLDER_PRESETS
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.stopView
import com.bmt.nosecalc.screens.sim.COMPANY_HOLDERS
import com.bmt.nosecalc.screens.sim.SimState
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 독립 검증(10-29): 회사 사용 홀더 값을 조사 원본 json 이 아니라 검증자가 출처를 다시 열어 읽은 값과 맞추고,
 * 그 값으로 라운드 내경 계산(간섭 · 안전 끝점)과 시뮬레이터 깎기가 실제로 도는지 본다.
 *
 * 다시 연 출처(2026-10-29, 두 번째 검증자 — PDF 는 메모리로 읽어 쪽을 그림으로 그려 눈으로 대조):
 *  - 한국야금 Turning 2025-2026 PDF(466쪽, 만든 날 2024-12-16) B121(PDF 189, F = 90° · L = 95°) · B132(PDF 200, 카바이드 C-SCLCR)
 *    · B137(PDF 205, 강 S-STFCR, 머리 90°) · B91(PDF 159, DCKNR/L 75° — 오른쪽 작업 그림은 정면 이송) · B112(PDF 180, MVJNR/L 93° VN 1604)
 *  - 대구텍 Turning 2026 PDF(330쪽, 만든 날 2026-03-13) A44(홀더 모양 K 의 75° 는 자루 축(X) 이송에서 잼) · A175(C-SCLCR 95°) · A182(S-STFCR 91°)
 *  - 교세라 2024-2026 Chapter F PDF 157쪽(F157 'SHA / SH / SHC sleeves' 표의 SH0516-100 · SH0616-100) · 이스카 PICCO 제품 페이지
 *  - 미쓰비시 제품 페이지(20055542 …)는 이번에 다시 열지 못했다(사이트가 봇 검사로 막음) — 아래 미쓰비시 세 줄은 조사 값 그대로다
 */
class VerifyHoldersTest {
    private class Bar(val d: Double, val f: Double, val dMin: Double, val kr: Double, val shape: InsertShape, val edge: Double) {
        fun bar(overhang: Double? = null) = BoringBar(d, f, shape, edge, kr, overhang, dMin)
    }

    /** 검증자가 출처에서 읽은 값(품번 = 앱의 제조사 표기) */
    private val read: Map<String, Bar> = mapOf(
        // 한국야금 디지털 카탈로그 C-SCLCR/L (6-01-012030 · 012031 · 012033 · 012035 · 012037) — B132 표와도 같음
        "C08K-SCLCR-06" to Bar(8.0, 5.0, 10.0, 95.0, InsertShape.C, 6.0),
        "C10K-SCLCR-06" to Bar(10.0, 6.0, 12.0, 95.0, InsertShape.C, 6.0),
        "C12M-SCLCR-06" to Bar(12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0),
        "C12M-SCLCR-09" to Bar(12.0, 9.0, 16.0, 95.0, InsertShape.C, 9.0),
        "C16R-SCLCR-09" to Bar(16.0, 10.0, 20.0, 95.0, InsertShape.C, 9.0),
        // 한국야금 B137 강 자루 STFCR (머리 90°)
        "S12M-STFCR-09" to Bar(12.0, 9.0, 16.0, 90.0, InsertShape.T, 9.0),
        "S12M-STFCR-11" to Bar(12.0, 9.0, 16.0, 90.0, InsertShape.T, 11.0),
        "S16R-STFCR-11" to Bar(16.0, 11.0, 20.0, 90.0, InsertShape.T, 11.0),
        // 대구텍 A175 (95°)
        "C08K SCLCR 06" to Bar(8.0, 6.0, 11.0, 95.0, InsertShape.C, 6.0),
        "C10K SCLCR 06" to Bar(10.0, 7.0, 13.0, 95.0, InsertShape.C, 6.0),
        "C12M SCLCR 06" to Bar(12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0),
        "C12M SCLCR 09" to Bar(12.0, 9.0, 16.0, 95.0, InsertShape.C, 9.0),
        "C16R SCLCR 09" to Bar(16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0),
        // 대구텍 A182 (91°)
        "S10K STFCR 09" to Bar(10.0, 6.0, 12.0, 91.0, InsertShape.T, 9.0),
        "S12M STFCR 09" to Bar(12.0, 9.0, 16.0, 91.0, InsertShape.T, 9.0),
        "S12M STFCR 1102" to Bar(12.0, 9.0, 16.0, 91.0, InsertShape.T, 11.0),
        "S16R STFCR 1102" to Bar(16.0, 11.0, 20.0, 91.0, InsertShape.T, 11.0),
        // 미쓰비시 제품 페이지 — 다시 열지 못함(봇 검사), 조사 값
        "C10KSCLCR06" to Bar(10.0, 7.0, 13.0, 95.0, InsertShape.C, 6.0),
        "C12MSCLCR06" to Bar(12.0, 9.0, 16.0, 95.0, InsertShape.C, 6.0),
        "C16RSCLCR09" to Bar(16.0, 11.0, 20.0, 95.0, InsertShape.C, 9.0),
    )

    /** 1. 앱의 회사 프리셋 칸 = 다시 읽은 출처 값(바 값이 없는 줄은 슬리브 · 계열 이름 셋뿐) */
    @Test
    fun presetValuesMatchSourcesReopened() {
        val withBar = COMPANY_PRESETS.filter { it.bar != null }
        assertEquals(read.keys, withBar.map { it.code }.toSet())
        for (p in withBar) {
            val r = read.getValue(p.code)
            assertEquals(r.bar(), p.bar, p.code)
        }
        assertEquals(setOf("SH0516-100", "SH0616-100", "PICCO"), COMPANY_PRESETS.filter { it.bar == null }.map { it.code }.toSet())
    }

    /**
     * 1'. 시뮬레이터 홀더의 인서트 · κr = 출처(한국야금 B97 · B112 · B91, 샌드빅 DCKNL, 위 보링바 표), 나사 홀더는 종류만.
     * DCKNL 의 κr 은 빈칸 — 카탈로그 75° 는 정면(X) 이송 기준이라 시뮬레이터(Z 이송 기준)에 넣지 않는다(아래 3 · 3')
     */
    @Test
    fun simHolderAnglesMatchSources() {
        fun one(code: String, maker: String? = null) = COMPANY_HOLDERS.single { it.code == code && (maker == null || maker in it.makers) }
        assertEquals(Triple(ToolKind.TURN, InsertShape.C, 95.0), one("PCLNL2525M12").let { Triple(it.kind, it.insert, it.kappa) })
        assertEquals(Triple(ToolKind.TURN, InsertShape.V, 93.0), one("MVJNR2525M16").let { Triple(it.kind, it.insert, it.kappa) })
        assertEquals(Triple(ToolKind.TURN, InsertShape.C, null), one("DCKNL2525M12").let { Triple(it.kind, it.insert, it.kappa) })
        assertEquals(ToolKind.THREAD, one("THCER2525M16").kind)
        assertTrue(one("THCER2525M16").makers.isEmpty())
        assertEquals(ToolKind.THREAD, one("SNR0020L09A").kind)
        for (code in listOf("C08K-SCLCR06", "C10K-SCLCR06", "C12M-SCLCR06", "C12M-SCLCR09", "C16R-SCLCR09")) {
            assertEquals(InsertShape.C to 95.0, one(code).let { it.insert to it.kappa }, code)
        }
        assertEquals(91.0, one("S10K-STFCR09").kappa)
        for (code in listOf("S12M-STFCR09", "S12M-STFCR11", "S16R-STFCR11")) {
            assertEquals(InsertShape.T to 90.0, one(code, "한국야금").let { it.insert to it.kappa }, code)
            assertEquals(InsertShape.T to 91.0, one(code, "대구텍").let { it.insert to it.kappa }, code)
        }
    }

    /**
     * 2. 회사 프리셋을 고른 라운드 내경(예시 형상 2~6): 칸 → 바가 출처 값 그대로이고, 화면이 부르는 HolderStop.plan · stopView 가 그 바로 돈다.
     * 돔 바닥(형상 3)은 축까지 좁아져 DMIN 이 있으면 최소 가공경 닿음이 나오고 깊이는 DMIN/2 이하, DMIN 을 비우면 그 닿음이 없다.
     */
    @Test
    fun roundInnerRunsWithCompanyValues() {
        val r = 0.4
        val log = StringBuilder()
        for (p in COMPANY_PRESETS) {
            val ref = read[p.code]
            for (t in 2..6) {
                val s = roundInnerExample(t).apply { pickPreset(p) }
                if (ref == null) {
                    assertNull(s.holderBar(), "${p.code} 형상 $t")
                    continue
                }
                val bar = assertNotNull(s.holderBar(), "${p.code} 형상 $t")
                assertEquals(ref.bar(), bar, "${p.code} 형상 $t")
                val i = s.input(t)
                val sol = RoundCalc.calc(i).solution!!
                val drawn = RoundCalc.machine(i).solution
                val plan = HolderStop.plan(sol, drawn, r, true, bar, s.holderSafe())
                assertEquals(HolderStop.plan(sol, drawn, r, true, ref.bar(), 0.5), plan, "${p.code} 형상 $t")
                if (plan.result.skip != null) {
                    log.append("${p.code}\t형상 $t\tskip ${plan.result.skip}\n")
                    continue
                }
                val direct = stopView(plan, true, sol, drawn, r, bar)
                stopView(plan, false, sol, drawn, r, bar).text
                val parts = plan.result.hits.map { it.part }.distinct()
                log.append("${p.code}\t형상 $t\t${direct.kind}\t${parts}\tbarClear ${plan.result.barClear} @${plan.result.barClearElement} ${plan.result.barClearAt?.tip}\t${direct.head}\n")
                plan.result.hits.filter { it.part == HolderPart.BAR }.forEach { h ->
                    log.append("    BAR 요소 ${h.element} 깊이 ${h.depth} 맞은편 ${h.opposite} 처음 ${h.first.tip} 가장 깊은 ${h.worst.tip}\n")
                }
                if (p == COMPANY_PRESETS.first()) log.append("    도면 ${sol.drawing.segs}\n    진입 ${sol.entry}\n")
                if (t == 3) {
                    val mins = plan.result.hits.filter { it.part == HolderPart.MIN_BORE }
                    assertTrue(mins.isNotEmpty(), "${p.code} 돔 바닥 최소 가공경")
                    for (h in mins) assertTrue(h.depth > 0 && h.depth <= ref.dMin / 2 + 1e-9, "${p.code} 깊이 ${h.depth}")
                    val noDmin = HolderStop.plan(sol, drawn, r, true, bar.copy(dMin = null), s.holderSafe())
                    assertTrue(noDmin.result.hits.none { it.part == HolderPart.MIN_BORE }, p.code)
                }
            }
        }
        // 같은 품번 · 다른 제조사 값은 결과도 다르다(f 6 / 7, DMIN 12 / 13) — 형상 3
        fun plan3(code: String) = roundInnerExample(3).apply { pickPreset(HOLDER_PRESETS.single { it.code == code }) }.let { s ->
            val i = s.input(3)
            HolderStop.plan(RoundCalc.calc(i).solution!!, RoundCalc.machine(i).solution, r, true, s.holderBar()!!, s.holderSafe())
        }
        assertNotEquals(plan3("C10K-SCLCR-06"), plan3("C10K SCLCR 06"))
        // 대구텍 C16R SCLCR 09 는 샌드빅 A16R-SCLCR 09 와 칸이 같아(16 · 11 · 20 · 95 · C 9) 결과도 같다
        assertEquals(plan3("A16R-SCLCR 09"), plan3("C16R SCLCR 09"))
        File("build/verify").mkdirs()
        File("build/verify/round-company.txt").writeText(log.toString())
    }

    /**
     * 2'. 시뮬레이터: 홀더를 고른 공구표로 소재를 깎으면 그 인서트 모양으로 깎는다. 외경 내리막(26.6°, X40 Z−10 → X30 Z−20, 팁 3 · G40)에서
     * MVJNR(V 35° · κr 93° → 끝날각 52°)은 경사를 따라가고, PCLNL(C 80° · κr 95° → 끝날각 5°)은 뒤날이 경사를 판다.
     */
    @Test
    fun simCutsWithPickedHolderInsert() {
        val text = listOf(
            "O9100 (VERIFY)", "T0101", "G97 S1000 M03", "G99", "G00 X52. Z2.", "G00 X40. Z1.", "G01 Z-10. F0.2",
            "G01 X30. Z-20.", "G01 Z-30.", "G01 X52.", "G00 Z2.", "M30",
        ).joinToString("\n")
        val spec = StockSpec(dia = 50.0, length = 60.0, faceZ = 0.0, chuckDia = 100.0)
        fun cut(code: String): Pair<StockSim, ToolOffset> {
            val s = SimState(KeyValueStore(File.createTempFile("bmt-verify", ".properties").apply { delete() }))
            s.tools = mapOf(1 to ToolOffset(noseR = 0.4, tip = 3))
            s.pickHolder(1, COMPANY_HOLDERS.single { it.code == code })
            val run = s.runOf(text, s.tools)
            assertNull(run.stop, "$code ${run.issues}")
            return StockSim(spec, run, s.tools).also { it.cutTo(run.totalSec) } to s.tools.getValue(1)
        }
        val (v, vt) = cut("MVJNR2525M16")
        val (c, ct) = cut("PCLNL2525M12")
        assertEquals(ToolOffset(noseR = 0.4, tip = 3, insert = InsertShape.V, kappaDeg = 93.0), vt)
        assertEquals(ToolOffset(noseR = 0.4, tip = 3, insert = InsertShape.C, kappaDeg = 95.0), ct)
        val log = StringBuilder()
        for (z in listOf(-12.0, -14.0)) {
            val want = 15 + (z + 20) / 2
            val rv = v.outerAt(z)!!
            val rc = c.outerAt(z)!!
            log.append("z $z 경사 r $want  MVJNR $rv  PCLNL $rc\n")
            assertTrue(abs(rv - want) < 0.45, "MVJNR z $z r $rv (경사 $want)")
            assertTrue(rc < want - 1.0, "PCLNL z $z r $rc (경사 $want) — 뒤날이 파야 함")
        }
        // 곧은 부분(r 20): V 는 그대로, C 는 12 mm · 5° 뒤날이 경사를 내려가는 동안 곧은 부분까지 판다(물리상 맞음 — 끝날각 5° 로는 26.6° 내리막을 못 탄다)
        log.append("z -0.5  MVJNR ${v.outerAt(-0.5)}  PCLNL ${c.outerAt(-0.5)} / z -5  MVJNR ${v.outerAt(-5.0)}  PCLNL ${c.outerAt(-5.0)}\n")
        assertTrue(abs(v.outerAt(-0.5)!! - 20.0) < 0.05 && abs(v.outerAt(-5.0)!! - 20.0) < 0.05, log.toString())
        assertTrue(c.outerAt(-5.0)!! < 19.0, log.toString())
        File("build/verify").mkdirs()
        File("build/verify/sim-insert.txt").writeText(log.toString())
    }

    /**
     * 3. 시뮬레이터의 κr 은 Z 이송(척 쪽 선삭) 기준이다: 노즈 0 · 팁 3 바이트의 두 날이 Z 축과 이루는 각 = {κr 의 보각 쪽, 끝날각 180 − κr − ε}.
     * PCLNL(C · 95) → 85° · 5°. K 꼴(DCKN)의 75° 는 대구텍 A44 · 한국야금 B91 그림에서 정면(X) 이송 기준이라 κr 칸에 75 를 넣으면
     * B 꼴(DCBN, Z 이송 75°) 같은 다른 공구(75° · 25°)가 된다. 실제 DCKN 은 Z 기준으로 κr 95° · 뒤날 −15°(100° 모서리)라 그릴 수 없어
     * 홀더는 κr 을 비우고(C 95° 가정), 그러면 끝면 쪽 날(Z 축과 85° — 정면 뒤날각 5°)이 실제와 같다.
     */
    @Test
    fun dcknlKappaIsTakenAsTurningAngle() {
        fun edgeAngles(t: ToolOffset): List<Long> {
            val pts = ToolShape.outline(t)
            val far = pts.filter { abs(it.length - ToolShape.edgeOf(t.insert)) < 1e-6 }
            assertEquals(2, far.size, "$t $pts")
            return far.map { Math.round(Math.toDegrees(acos(abs(it.z) / it.length))) }.sorted()
        }
        fun edgeAngles(code: String) = edgeAngles(COMPANY_HOLDERS.single { it.code == code }.fill(ToolOffset(noseR = 0.0, tip = 3)))
        assertEquals(listOf(5L, 85L), edgeAngles("PCLNL2525M12"))
        assertEquals(listOf(5L, 85L), edgeAngles("DCKNL2525M12"))
        assertEquals(listOf(25L, 75L), edgeAngles(ToolOffset(noseR = 0.0, tip = 3, insert = InsertShape.C, kappaDeg = 75.0)))
        assertEquals(listOf(52L, 87L), edgeAngles("MVJNR2525M16"))
    }

    /**
     * 3'. 끝면 깎기(X 이송, Z0 · X54 → X−1): PCLNL 도 DCKNL(정면 깎기 홀더 — κr 빈칸)도 끝면 아래(z −1 · −2)를 건드리지 않는다.
     * 카탈로그 κr 75° 를 그대로 넣은 공구는 끝면을 쐐기로 판다(고치기 전 — z −1 반경 3.56) — 반경은 build/verify/sim-dcknl.txt 에.
     */
    @Test
    fun facingWithPickedHolder() {
        val text = listOf("O9101 (VERIFY FACE)", "T0101", "G97 S1000 M03", "G99", "G00 X54. Z0.", "G01 X-1. F0.15", "G00 Z2.", "M30").joinToString("\n")
        val spec = StockSpec(dia = 50.0, length = 60.0, faceZ = 1.0, chuckDia = 100.0)
        fun face(code: String, kappa75: Boolean = false): StockSim {
            val s = SimState(KeyValueStore(File.createTempFile("bmt-verify", ".properties").apply { delete() }))
            s.tools = mapOf(1 to ToolOffset(noseR = 0.4, tip = 3))
            s.pickHolder(1, COMPANY_HOLDERS.single { it.code == code })
            if (kappa75) s.tools = mapOf(1 to s.tools.getValue(1).copy(kappaDeg = 75.0))
            val run = s.runOf(text, s.tools)
            assertNull(run.stop, "$code ${run.issues}")
            return StockSim(spec, run, s.tools).also { it.cutTo(run.totalSec) }
        }
        val p = face("PCLNL2525M12")
        val d = face("DCKNL2525M12")
        val d75 = face("DCKNL2525M12", kappa75 = true)
        val log = buildString {
            for (z in listOf(0.5, -0.5, -1.0, -2.0, -3.0, -4.0)) append("z $z  PCLNL ${p.outerAt(z)}  DCKNL ${d.outerAt(z)}  κr 75 ${d75.outerAt(z)}\n")
        }
        File("build/verify").mkdirs()
        File("build/verify/sim-dcknl.txt").writeText(log)
        for (z in listOf(-1.0, -2.0)) {
            assertTrue(abs(p.outerAt(z)!! - 25.0) < 0.05, log)
            assertTrue(abs(d.outerAt(z)!! - 25.0) < 0.05, log)
        }
        assertTrue(d75.outerAt(-1.0)!! < 24.0, log)
    }
}
