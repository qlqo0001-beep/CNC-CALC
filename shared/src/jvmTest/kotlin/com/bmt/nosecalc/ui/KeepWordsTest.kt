package com.bmt.nosecalc.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 어절 붙이기: 한글 음절 사이 · 한글과 붙은 영문 · 숫자 · 닫는 괄호 사이에만 낱말 이음(\u2060), 띄어쓰기 · 기호 · 숫자끼리는 그대로 */
class KeepWordsTest {
    private val wj = "\u2060"

    @Test
    fun joinsInsideWordsOnly() {
        assertEquals("(변 하${wj}나${wj}는 꼭).", keepWords("(변 하나는 꼭)."))
        assertEquals("팁 3${wj}번 → 4${wj}번 · 노${wj}즈${wj}R 0.4", keepWords("팁 3번 → 4번 · 노즈R 0.4"))
        assertEquals("T1 보${wj}다 X100.000 Z-5.0", keepWords("T1 보다 X100.000 Z-5.0"))
        // 닫는 괄호 뒤 조사는 붙이고, 여는 괄호 앞은 그대로(괄호 앞에서는 줄이 바뀌어도 된다)
        assertEquals("(Ctrl + 휠)${wj}으${wj}로 · 요${wj}소(시${wj}작)${wj}를", keepWords("(Ctrl + 휠)으로 · 요소(시작)를"))
        assertEquals("A\u00A0+\u00A0B\u00A0=\u00A090°", keepWords("A\u00A0+\u00A0B\u00A0=\u00A090°"))
    }

    /** 이미 이음을 넣은 글(Parts.kt 의 '좌\u2060표\u2060로')은 두 번 넣지 않는다 */
    @Test
    fun idempotent() {
        val once = keepWords("도면 좌\u2060표\u2060로 깎습니다")
        assertEquals("도${wj}면 좌${wj}표${wj}로 깎${wj}습${wj}니${wj}다", once)
        assertEquals(once, keepWords(once))
    }
}
