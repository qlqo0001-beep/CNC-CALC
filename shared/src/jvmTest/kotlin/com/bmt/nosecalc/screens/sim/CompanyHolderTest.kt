package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 공구 설정의 '홀더 고르기'(회사 사용 홀더, 10-29): 고르면 종류 · 인서트 모양 · κr 이 채워지고 노즈 R · 팁 · 폭 · 나사 각은 그대로,
 * 고친 뒤엔 '수정됨', 고른 이름표는 화면 상태 Saver 를 지나고, 예제를 열어 그 번호의 공구표가 바뀌면 이름표는 빠진다
 */
class CompanyHolderTest {
    private fun store() = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })
    private fun holder(code: String, maker: String? = null) =
        COMPANY_HOLDERS.single { it.code == code && (maker == null || maker in it.makers) }

    @Test
    fun pickFillsKindInsertKappa() {
        val s = SimState(store())
        s.tools = mapOf(1 to ToolOffset(noseR = 0.8, tip = 3, kind = ToolKind.GROOVE, insert = InsertShape.D, kappaDeg = 107.5, width = 3.0, threadDeg = 55.0))
        s.pickHolder(1, holder("MVJNR2525M16"))
        assertEquals(ToolOffset(noseR = 0.8, tip = 3, kind = ToolKind.TURN, insert = InsertShape.V, kappaDeg = 93.0, width = 3.0, threadDeg = 55.0), s.tools[1])
        assertEquals("MVJNR2525M16", s.holderOf(1)?.code)
        assertFalse(s.holderOf(1)!!.edited(s.tools[1]!!))

        // 같은 품번도 제조사마다 κr 이 다른 줄
        s.pickHolder(1, holder("S12M-STFCR09", "한국야금"))
        assertEquals(InsertShape.T to 90.0, s.tools[1]!!.insert to s.tools[1]!!.kappaDeg)
        s.pickHolder(1, holder("S12M-STFCR09", "대구텍"))
        assertEquals(91.0, s.tools[1]!!.kappaDeg)
        assertEquals(listOf("대구텍"), s.holderOf(1)!!.makers)

        // 고치면 수정됨, 고르지 않음으로 돌려도 칸 값은 그대로
        s.tools = s.tools + (1 to s.tools[1]!!.copy(kappaDeg = 95.0))
        assertTrue(s.holderOf(1)!!.edited(s.tools[1]!!))
        s.pickHolder(1, null)
        assertNull(s.holderOf(1))
        assertEquals(95.0, s.tools[1]!!.kappaDeg)

        // 빈 번호에도 고를 수 있다(공구표에 새 줄)
        s.pickHolder(7, holder("PCLNL2525M12"))
        assertEquals(ToolOffset(kind = ToolKind.TURN, insert = InsertShape.C, kappaDeg = 95.0), s.tools[7])
    }

    /** 나사 홀더는 종류만(나사 각은 인서트가 정한다), 값을 모르는 소경 줄은 인서트는 그대로 · κr 은 빈칸(가정 값) */
    @Test
    fun threadAndBlankHolders() {
        val s = SimState(store())
        s.tools = mapOf(2 to ToolOffset(noseR = 0.2, tip = 8, insert = InsertShape.D, kappaDeg = 93.0, threadDeg = 55.0))
        s.pickHolder(2, holder("THCER2525M16"))
        assertEquals(ToolOffset(noseR = 0.2, tip = 8, kind = ToolKind.THREAD, insert = InsertShape.D, kappaDeg = 93.0, threadDeg = 55.0), s.tools[2])
        assertTrue(holder("THCER2525M16").makers.isEmpty())
        s.pickHolder(2, holder("SNR0020L09A"))
        assertEquals(ToolKind.THREAD, s.tools[2]!!.kind)

        s.pickHolder(2, holder("PICCO"))
        assertEquals(ToolOffset(noseR = 0.2, tip = 8, kind = ToolKind.TURN, insert = InsertShape.D, kappaDeg = null, threadDeg = 55.0), s.tools[2])
        // 비운 κr 에 넣은 값은 고친 것이 아니다 · 종류를 바꾸면 고친 것
        s.tools = s.tools + (2 to s.tools[2]!!.copy(kappaDeg = 95.0, insert = InsertShape.C))
        assertFalse(s.holderOf(2)!!.edited(s.tools[2]!!))
        assertTrue(s.holderOf(2)!!.edited(s.tools[2]!!.copy(kind = ToolKind.ROUND)))
    }

    @Test
    fun labelsSurviveSaverAndExampleClearsThem() {
        val kv = store()
        val s = SimState(kv)
        s.pickHolder(1, holder("DCKNL2525M12"))
        s.pickHolder(12, holder("S16R-STFCR11", "대구텍"))
        val saver = SimState.saver(kv)
        val r = saver.restore(assertNotNull(with(saver) { SaverScope { true }.save(s) }))!!
        assertEquals(s.toolHolders, r.toolHolders)
        assertEquals("DCKNL2525M12", r.holderOf(1)?.code)
        assertEquals(91.0, r.holderOf(12)?.kappa)
        assertEquals(s.tools, r.tools)

        // 예제가 공구표를 가져오는 번호는 이름표를 뺀다(그 번호의 값이 예제 것으로 바뀜)
        val i = exampleList.indexOfFirst { 1 in it.tools }
        r.perform(Pending.Example(i))
        assertNull(r.holderOf(1))
        assertEquals(12 in exampleList[i].tools, r.holderOf(12) == null)
    }
}
