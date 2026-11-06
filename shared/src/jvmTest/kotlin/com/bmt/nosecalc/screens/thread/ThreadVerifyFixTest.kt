package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieWarning
import com.bmt.nosecalc.calc.G76Warning
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.PipeThreadCalc
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.screens.ThreadHelp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 나사 확장 검증(2026-11-06)에서 나온 문제의 회귀 시험 — 화면 쪽. 기대값은 손 계산(식과 수를 주석에) · 카탈로그 원문 값이다.
 * 계산기 쪽 회귀는 calc 의 DieHeadCalcTest · PipeThreadTest · ThreadInsertsJsonTest.
 */
class ThreadVerifyFixTest {
    private fun store(values: Map<String, String> = emptyMap()) =
        DieStore(KeyValueStore(File.createTempFile("bmt-die", ".properties").apply { delete() }).apply { values.forEach { (k, v) -> put(k, v) } })
    private fun un(size: String, tpi: Int) = ThreadTable.pitches(ThreadKind.UN, size).first { it.tpi == tpi }
    private fun pt(size: String) = PipeTable.of(PipeKind.PT).single { it.size == size }

    /**
     * 옛 저장 글(2026-11-05 전 — 묶음 칸이 없음)의 UNF 줄을 되살리면 묶음은 그 줄의 계열(UNF)이어야 한다. UNC 로 남으면 사이즈 시트가 UNC 격자를 열고
     * 같은 크기를 다시 누르면 피치가 UNC(1/2-13)로 바뀐다
     */
    @Test
    fun saverOldUnfKeepsGroup() {
        val p = un("1/2", 20).pitch
        val old = listOf("false", "UN", "UNF|1/2|$p", "", "", "FORWARD", "", "", "false", "5", "", "-18", "", "", "", "", "", "", "0.2", "G92")
        val back = ThreadState.Saver.restore(old)!!
        assertEquals("1/2-20 UNF", back.spec?.label)
        assertEquals(UnGroup.UNF, back.unGroup)
        back.pickSize("1/2")
        assertEquals("1/2-20 UNF", back.spec?.label)
        // 묶음 칸이 있는 새 글은 그 칸 그대로(UNF 줄을 20UN 묶음에서 골랐으면 20UN)
        val s = ThreadState(family = ThreadFamily.UN, unGroup = UnGroup.UN20).apply { pickSize("1/2") }
        val again = ThreadState.Saver.restore(with(ThreadState.Saver) { SaverScope { true }.save(s) }!!)!!
        assertEquals(UnGroup.UN20, again.unGroup)
        assertEquals("1/2-20 UNF", again.spec?.label)
    }

    /**
     * 내경 인서트가 구멍에 들어가나: 이 인서트를 받는 가장 작은 홀더의 최소 구멍(VARDEX 0.67 in = 17.018 · Carmex 16)과 견준다.
     * 1/2-20 UNF 2B 안지름 최소 = 기본 안지름 12.7 − 1.082532 × 1.27 = 11.325184 < 17.018 → 노랑(작은 인서트).
     * Rc 1/2 · Z4 → −16(기준면 0): 끝 Z 안지름 = 20.955 − 2h − 16/16 = 17.631528(h = 0.640327 × 25.4/14 = 1.161736) —
     * 3IR14BSPT 는 (17.631528 − 17.018)/2 = 0.306764 < 시작 X 여유 1 → 노랑(바 뒤가 벽에 닿음), 여유 0.3 이면 없음.
     * 16 IR 14 BSPT 는 (17.631528 − 16)/2 = 0.815764 → 여유 1 은 노랑, 0.8 은 없음. 1-20 UNEF 안지름 24.025184 는 (24.025 − 17.018)/2 = 3.5 > 1 → 없음. 외경은 없음
     */
    @Test
    fun insertBoreWarning() {
        val unf = ThreadState(inner = true, family = ThreadFamily.UN, spec = un("1/2", 20))
        unf.pickInsert(unf.insertChoices.first { it.designation == "3IR20UN" })
        val w = assertNotNull(insertBoreWarning(unf))
        assertTrue("Ø11.325" in w && "Ø17.018" in w && "NVRC050-3" in w, w)
        val unef = ThreadState(inner = true, family = ThreadFamily.UN, spec = un("1", 20))
        unef.pickInsert(unef.insertChoices.first())
        assertNull(insertBoreWarning(unef))
        val rc = ThreadState(inner = true, family = ThreadFamily.PT, pipe = pt("1/2"), values = mapOf("zStart" to "4", "zEnd" to "-16", "firstCut" to "0.3"))
        assertEquals(17.631528, PipeThreadCalc.calc(rc.pipeInput()).solution!!.crestAtEnd, 1e-5)
        rc.pickInsert(rc.insertChoices.first { it.designation == "3IR14BSPT" })
        val c = assertNotNull(insertBoreWarning(rc))
        assertTrue("0.307" in c && "Ø17.632" in c && "Ø17.018" in c, c)
        rc.set(ThreadField.X_CLEAR, "0.3")
        assertNull(insertBoreWarning(rc))
        rc.set(ThreadField.X_CLEAR, "")
        rc.pickInsert(rc.insertChoices.first { it.designation == "16 IR 14 BSPT" })
        assertTrue("0.816" in insertBoreWarning(rc)!!)
        rc.set(ThreadField.X_CLEAR, "0.8")
        assertNull(insertBoreWarning(rc))
        val ext = threadOriginal().apply { pickInsert(insertChoices.first()) }
        assertNull(insertBoreWarning(ext))
        // 인서트 칸 아래 글: R 이 없는 줄은 비우면 규격 기본 R · 풀 프로파일이라 산봉우리도 만듦
        val note = insertNote(ext)!!
        assertTrue("비우면 규격 기본 R" in note && "H/6" in note && "산봉우리" in note, note)
    }

    /**
     * 관용 테이퍼 소재 가정: R 3/4 · Z5 → −15 · Δd 0.35 — 기준면 산봉우리 = 26.441 − 9.53/16 = 25.845375(d − h + (−9.53 − 0)/16 + h),
     * 강관 Ø27.2 에 바로 깎으면 기준면 쪽 첫 회 = (27.2 − 25.845375)/2 + 0.35 = 1.027313.
     * Rc 1 · 기준면 −2 · Z3 → −21 · Δd 0.3: 곧은 구멍(끝 Z 안지름)이면 입구 쪽 첫 회 = (−2 + 21)/16/2 + 0.3 = 0.89375
     */
    @Test
    fun pipeBlankAssumption() {
        val ext = threadPtExt()
        val sol = PipeThreadCalc.calc(ext.pipeInput()).solution!!
        val n = pipeResultNote(ext, sol)
        assertTrue("Ø25.845 → " in n && "Ø27.2" in n && "1.027 mm" in n && "앞 공정" in n, n)
        val int = threadPtInt()
        val si = PipeThreadCalc.calc(int.pipeInput()).solution!!
        assertTrue("0.894 mm" in pipeResultNote(int, si), pipeResultNote(int, si))
        // 도움말도 소재 가정을 적는다
        val help = ThreadHelp.single { it.title.startsWith("관용 테이퍼") }.items.joinToString(" ")
        assertTrue("앞 공정" in help && "M23" in help && "ANSI B2.1" in help, help)
    }

    /**
     * 시작 Z: 기준면과 같으면 멈춤(빨강), 2 × 피치 안이면 노랑 — R 3/4 기준면 0 · 시작 Z 2(2P = 3.628571). 나사 길이 경고는 M23 끝 빼기 몫을 말한다.
     * NPT 는 값의 출처(H28 1957 = ANSI B2.1)를 앞에
     */
    @Test
    fun pipeStartAndLengthTexts() {
        val near = threadPtExt().apply { set(ThreadField.Z_START, "2") }
        val r = PipeThreadCalc.calc(near.pipeInput())
        assertEquals(listOf(G76Warning.START_NEAR), r.warnings)
        val t = threadWarningText(G76Warning.START_NEAR, near, pipe = r.solution)
        assertTrue("2 × 피치" in t && "3.629" in t, t)
        val same = threadPtExt().apply { set(ThreadField.Z_START, "0") }
        assertEquals(listOf(G76Warning.START_INSIDE), PipeThreadCalc.calc(same.pipeInput()).warnings)
        assertTrue("같습니다" in threadWarningText(G76Warning.START_INSIDE, same), threadWarningText(G76Warning.START_INSIDE, same))
        val short = threadNptExt()
        val rs = PipeThreadCalc.calc(short.pipeInput())
        assertTrue("M23" in threadWarningText(G76Warning.SHORT_THREAD, short, pipe = rs.solution))
        assertTrue("M23" in pipeResultNote(short, rs.solution!!) && "No.5130" in pipeResultNote(short, rs.solution!!))
        assertEquals("ANSI B2.1 (ASME B1.20.1)", standardText(ThreadFamily.NPT))
    }

    /**
     * 다이스: [threadDie](M16×2 · S159 · Z5 → −24 · 끝면 0 · 플로트 1.5) — 당김 = 0.04/0.96 × 24 = 1.0, 멈춤 1초 동안 이동 = 159/60 × 2 = 5.3,
     * 가장 깊은 체이서 끝 = −24 − 1.5 = −25.5. 멈춤 0 → 1.0 < 1.5 라 노랑 DWELL_SHORT, 시작 Z 1(끝면 0 · 피치 2) → 노랑 START_NEAR.
     * NC 설명 · 도움말은 체이서 끝이 플로트까지 더 들어감과 다음 공작물 전에 머리를 닫음을 적는다
     */
    @Test
    fun dieTexts() {
        val s = threadDie()
        val d = store(threadDieStore())
        val sol = DieHeadCalc.calc(s.dieInput(d)).solution!!
        val cells = dieResultCells(s, d, sol).associate { it.first to it.second }
        assertEquals("1.000", cells["끝 당김 길이"])
        assertEquals("5.300", cells["멈춤 동안 체이서 이동"])
        assertEquals("Z-25.5", cells["체이서 끝 (가장 깊게)"])
        val zero = store(threadDieStore() + (DieStore.key(DieField.DWELL, null) to "0"))
        val wz = DieHeadCalc.calc(s.dieInput(zero))
        assertEquals(listOf(DieWarning.DWELL_SHORT), wz.warnings)
        assertTrue("급송" in dieWarningText(DieWarning.DWELL_SHORT, s, zero, wz.solution))
        val near = threadDie().apply { set(ThreadField.Z_START, "1") }
        assertEquals(listOf(DieWarning.START_NEAR), DieHeadCalc.calc(near.dieInput(d)).warnings)
        assertTrue(dieWarningText(DieWarning.START_NEAR, near, d).startsWith("Z축 시작점(Z1.)이 공작물 끝면(Z0.)에서 피치"))
        val nc = dieNcExplain(150.0)
        assertTrue("플로트" in nc && "닫" in nc && "이만큼 척 쪽" !in nc, nc)
        val help = ThreadHelp.single { it.title == "다이스 헤드" }.items.joinToString(" ")
        assertTrue("(1 − 비율)/비율" in help && "닫" in help && "실제 나사 끝은 G1 끝보다 이만큼 척 쪽" !in help, help)
        // 카탈로그 줄: 고른 범위의 S(Ø16 — 1000 × 10/(π × 16) = 198.9 → S199, 25 → 497.4 → S497)와 회사 Vc 가 범위 밖임(Vc 7.99 < 10)
        val note = dieSpeedNote(s, sol)
        assertTrue("S199" in note && "497" in note && "범위 밖" in note && "N/mm² 의 오식으로 보임" in note, note)
    }
}
