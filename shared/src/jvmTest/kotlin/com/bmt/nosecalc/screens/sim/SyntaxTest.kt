package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import com.bmt.nosecalc.ui.theme.DarkColors
import com.bmt.nosecalc.ui.theme.LightColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 구문 색(설계 7.9 · 9.4 SyntaxTest): 09 글의 낱말별 색과 대비 */
class SyntaxTest {
    /** 글자 하나하나의 색(스타일이 없으면 null) */
    private fun colors(a: AnnotatedString): List<Color?> = a.text.indices.map { i ->
        a.spanStyles.lastOrNull { i >= it.start && i < it.end }?.item?.color
    }

    private fun check(line: String, p: SimPalette, vararg want: Pair<String, Color?>) {
        val a = syntaxLine(line, p)
        val cs = colors(a)
        for ((piece, color) in want) {
            val at = line.indexOf(piece)
            assertTrue(at >= 0, "$piece 가 '$line' 에 없다")
            for (i in at until at + piece.length) assertEquals(color, cs[i], "'$line' 의 '${line[i]}'($i) — $piece")
        }
    }

    @Test
    fun original09LinesLight() = lines(SimColors.Light)

    @Test
    fun original09LinesDark() = lines(SimColors.Dark)

    private fun lines(p: SimPalette) {
        check("O1001", p, "O1001" to p.o)
        check("N11 T0101 M08", p, "N11" to p.n, "T0101" to p.tsf, "M08" to p.m)
        check("G50 S2000", p, "G50" to p.g, "S2000" to p.tsf)
        check("G71 U1.5 R0.5", p, "G71" to p.g, "U1.5" to p.axis, "R0.5" to p.rik)
        check("G71 P10 Q20 U0.4 W0.1 F0.25", p, "P10" to p.pql, "Q20" to p.pql, "W0.1" to p.axis, "F0.25" to p.tsf)
        check("G02 X34. Z-15. R7.", p, "X34." to p.axis, "Z-15." to p.axis, "R7." to p.rik)
        // ,C · ,R: 쉼표는 O 색(09 의 검정), 글자 · 숫자는 원호 색
        check("G01 Z-30. ,C1.", p, "," to p.o, "C1." to p.rik)
        check("G01 X54. ,R1.", p, "," to p.o, "R1." to p.rik)
        check("G03 X2. Z-.25 I0. K-.25", p, "I0." to p.rik, "K-.25" to p.rik)
        check("M98 P1000 L2", p, "P1000" to p.pql, "L2" to p.pql)
        check("G00 X40. (황삭 끝)", p, "(황삭 끝)" to p.comment)
        check("/N5 G00 X1.;", p, "/" to p.mark, ";" to p.mark)
        // 받지 않는 글(@ · 한글 · 숫자만)은 색 없음(본문색) — 물결 밑줄은 문제가 긋는다
        check("G00 @ 35", p, "@" to null, "35" to null)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    /** 구문 색은 편집기 바탕(카드)과 지금 줄 바탕 모두에서 4.5:1 이상 */
    @Test
    fun syntaxContrast() {
        for ((p, card) in listOf(SimColors.Light to LightColors.card, SimColors.Dark to DarkColors.card)) {
            val tokens = listOf(p.g, p.axis, p.pql, p.tsf, p.m, p.rik, p.n, p.o, p.comment, p.mark)
            for (t in tokens) {
                assertTrue(contrast(t, card) >= 4.5, "카드 위 $t: ${contrast(t, card)}")
                assertTrue(contrast(t, p.hlLine) >= 4.5, "지금 줄 위 $t: ${contrast(t, p.hlLine)}")
            }
        }
    }

    /** 키 글자는 키 면(그라데이션 아래쪽) 위 4.5:1, ⌫ · ⏎ 아이콘은 3:1 이상. SHIFT 작은 글자는 글자 키 위 4.5:1 */
    @Test
    fun keyContrast() {
        for (p in listOf(SimColors.Light, SimColors.Dark)) {
            for (k in listOf(p.letter, p.digit, p.punct, p.space)) {
                assertTrue(contrast(k.text, k.bottom) >= 4.5 && contrast(k.text, k.top) >= 4.5, "키 글자 $k")
            }
            for (k in listOf(p.back, p.enter)) assertTrue(contrast(k.text, k.bottom) >= 3.0, "아이콘 키 $k")
            assertTrue(contrast(p.shiftMark, p.letter.bottom) >= 4.5, "SHIFT 글자 ${contrast(p.shiftMark, p.letter.bottom)}")
            assertTrue(contrast(p.onRun, p.run) >= 4.5, "궤적 단추 ${contrast(p.onRun, p.run)}")
            assertTrue(contrast(p.onShiftOn, p.shiftOn) >= 4.5 && contrast(p.shiftOffText, p.shiftOffBg) >= 4.5, "SHIFT 단추")
        }
    }
}
