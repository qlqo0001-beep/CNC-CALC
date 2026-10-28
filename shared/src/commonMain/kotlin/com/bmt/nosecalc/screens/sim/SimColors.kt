package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 키 한 종류의 색: 면 위 · 아래(그라데이션), 아래 턱, 글자 */
@Immutable
internal data class KeyColors(val top: Color, val bottom: Color, val lip: Color, val text: Color)

/**
 * 시뮬레이터 색(설계 7.9). 앱 공통 토큰(`AppColors`)은 고치지 않고 여기 따로 둔다 — `SimColors.of(AppTheme.colors.dark)`.
 *
 * 구문 색: 라이트는 09 에서 잰 색, 흰 바탕 대비 4.5:1 이 안 되는 X·Z·U·W(3.7) · P·Q·L(3.7)만 짙게. N 은 지금 줄 바탕(hlLine)
 * 위에서도 4.5:1 이 되게 #6A7587 → #5F6B7D. 다크는 우리가 정함(카드 #142032 바탕).
 *
 * 대비(WCAG) — 바탕: 라이트 카드 #FFFFFF · 지금 줄 #EDF5FC / 다크 카드 #142032 · 지금 줄 #1E3A52
 * | 낱말 | 라이트 | 지금 줄 | 다크 | 지금 줄 |
 * |---|---|---|---|---|
 * | G | 5.3 | 4.8 | 7.5 | 5.4 |
 * | X Z U W | 5.2 | 4.7 | 9.0 | 6.5 |
 * | P Q L | 5.5 | 5.0 | 8.4 | 6.0 |
 * | T S F | 6.4 | 5.8 | 7.4 | 5.3 |
 * | M | 5.3 | 4.8 | 8.2 | 5.9 |
 * | R I K ,C ,R | 7.4 | 6.7 | 8.0 | 5.7 |
 * | N | 5.4 | 4.9 | 6.4 | 4.6 |
 * | O · 쉼표 | 20 | 18.2 | 13.3 | 9.6 |
 * | 주석 | 5.6 | 5.1 | 8.4 | 6.0 |
 *
 * 키 글자(키 면 아래쪽 색 바탕, 라이트 / 다크): 글자 14.8 / 10.0 · 숫자 7.5 / 7.4 · 기호 7.2 / 5.0 · SP 7.3 / 7.3 · ⏎ 7.1 / 5.1 ·
 * ⌫ 아이콘 3.6 / 5.7(그림 3:1 이상) · SHIFT 작은 글자 4.9 / 6.0 · ▶ 궤적 5.0 / 7.7 · SHIFT 단추 꺼짐 6.0 / 8.8 · 켜짐 7.1 / 6.6.
 *
 * 궤적 창(설계 7.9 뷰 색) — 선 · 표는 뷰어 바탕(#EFF2F7 / #0B1421) 대비 3:1 이상, 숫자는 카드(#FFFFFF / #142032) 대비 4.5:1 이상:
 * | 토큰 | 라이트 | 다크 | 바탕 |
 * |---|---|---|---|
 * | layer(황삭 층) | 3.7 | 7.2 | 뷰어 |
 * | ghost(고스트 원) | 4.1 | 10.5 | 뷰어 |
 * | partEdge(남은 소재 테) | 5.2 | 7.2 | 뷰어 |
 * | part2d(남은 소재 면, α .55 — 면) | 2.2 | 3.9 | 뷰어 |
 * | chuck(척 테) | 6.8 | 3.9 | 뷰어 |
 * | valueSf(S · F 숫자) | 9.3 | 9.1 | 카드 |
 * | valueTime(가공 시간) | 5.1 | 9.8 | 카드 — 설계 7.9 의 #3B80B7 은 4.2 라 #2F72A8 로 짙게 |
 * | focusPill 위 글(측정 좌표 알약) | 6.5(흰 글) | 6.4(짙은 글) | 알약 — lineFocus #617FC5 위 흰 글은 3.9 라 알약만 짙게 |
 */
@Immutable
internal data class SimPalette(
    // 구문
    val g: Color,
    val axis: Color,
    val pql: Color,
    val tsf: Color,
    val m: Color,
    val rik: Color,
    val n: Color,
    val o: Color,
    val comment: Color,
    /** ; / % (블록 끝 · 건너뛰기 · 테이프) */
    val mark: Color,
    // 편집기
    val hlLine: Color,
    val hlBar: Color,
    /** 커서 · 고른 글 바탕(α 를 낮춰 씀) */
    val caret: Color,
    // 키패드
    val padBg: Color,
    val letter: KeyColors,
    val digit: KeyColors,
    val back: KeyColors,
    val punct: KeyColors,
    val space: KeyColors,
    val enter: KeyColors,
    /** 키 오른쪽 위 SHIFT 글자 */
    val shiftMark: Color,
    /** SHIFT 단추: 꺼짐 바탕 · 테 · 글, 켜짐 채움 · 글 */
    val shiftOffBg: Color,
    val shiftOffBorder: Color,
    val shiftOffText: Color,
    val shiftOn: Color,
    val onShiftOn: Color,
    /** ▶ 궤적 단추(초록 채움) · 글 — 라이트 흰 글 5.0:1, 다크 짙은 글 */
    val run: Color,
    val onRun: Color,
    // 궤적 창(설계 7.6 · 7.9)
    val layer: Color,
    val ghost: Color,
    val part2d: Color,
    val partEdge: Color,
    val chuck: Color,
    val valueSf: Color,
    val valueTime: Color,
    /** 측정 십자 옆 좌표 알약 바탕 · 글(초점색 — 원본의 주황 대신, 7.10) */
    val focusPill: Color,
    val onFocusPill: Color,
)

internal object SimColors {
    val Light = SimPalette(
        g = Color(0xFF3F64D4),
        axis = Color(0xFF2F7A57),
        pql = Color(0xFF2A7469),
        tsf = Color(0xFFA53660),
        m = Color(0xFFAB522F),
        rik = Color(0xFF7131B7),
        n = Color(0xFF5F6B7D),
        o = Color(0xFF080808),
        comment = Color(0xFF35735A),
        mark = Color(0xFF5F6B7D),
        hlLine = Color(0xFFEDF5FC),
        hlBar = Color(0xFF4AA2E2),
        caret = Color(0xFF2F5FC4),
        padBg = Color(0xFFD9E0E9),
        letter = KeyColors(Color(0xFFFFFFFF), Color(0xFFE8EBF0), Color(0xFFA3ABB8), Color(0xFF111827)),
        digit = KeyColors(Color(0xFFCDEBFC), Color(0xFF9FD5F6), Color(0xFF4A93CC), Color(0xFF0C3A5E)),
        back = KeyColors(Color(0xFFF4A3AC), Color(0xFFE2566A), Color(0xFFB4283F), Color(0xFFFFFFFF)),
        punct = Color(0xFFFCEFB4).let { KeyColors(it, Color(0xFFF6D673), Color(0xFFD99A2B), Color(0xFF5B3A06)) },
        space = KeyColors(Color(0xFFF1F3F6), Color(0xFFD5DAE1), Color(0xFF9AA3B0), Color(0xFF374151)),
        enter = KeyColors(Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFF4C1D95), Color(0xFFFFFFFF)),
        shiftMark = Color(0xFF5B6678),
        shiftOffBg = Color(0xFFEDE9FE),
        shiftOffBorder = Color(0xFFC4B5FD),
        shiftOffText = Color(0xFF6D28D9),
        shiftOn = Color(0xFF6D28D9),
        onShiftOn = Color(0xFFFFFFFF),
        run = Color(0xFF15803D),
        onRun = Color(0xFFFFFFFF),
        layer = Color(0xFF727D91),
        ghost = Color(0xFFB243CD),
        part2d = Color(0xFF9BA5B1),
        partEdge = Color(0xFF5B6675),
        chuck = Color(0xFF475569),
        valueSf = Color(0xFF273DA0),
        valueTime = Color(0xFF2F72A8),
        focusPill = Color(0xFF3B5BA5),
        onFocusPill = Color(0xFFFFFFFF),
    )

    val Dark = SimPalette(
        g = Color(0xFF93AEF7),
        axis = Color(0xFF6FD3A0),
        pql = Color(0xFF5CCBBE),
        tsf = Color(0xFFF48FB8),
        m = Color(0xFFF4A574),
        rik = Color(0xFFC7A6F7),
        n = Color(0xFF94A3B8),
        o = Color(0xFFE2E8F0),
        comment = Color(0xFF7FC9A0),
        mark = Color(0xFF94A3B8),
        hlLine = Color(0xFF1E3A52),
        hlBar = Color(0xFF60A5FA),
        caret = Color(0xFF93C5FD),
        padBg = Color(0xFF0F1A2A),
        letter = KeyColors(Color(0xFF2C3D56), Color(0xFF223149), Color(0xFF0A1220), Color(0xFFF1F5F9)),
        digit = KeyColors(Color(0xFF1F5078), Color(0xFF194263), Color(0xFF0B2236), Color(0xFFE0F2FE)),
        back = KeyColors(Color(0xFFB4385A), Color(0xFF8E2444), Color(0xFF4C0F22), Color(0xFFFFFFFF)),
        punct = KeyColors(Color(0xFF7A5C16), Color(0xFF5F4710), Color(0xFF2E2206), Color(0xFFFDE68A)),
        space = KeyColors(Color(0xFF3A4A62), Color(0xFF2E3C52), Color(0xFF111A28), Color(0xFFE2E8F0)),
        enter = KeyColors(Color(0xFF7C4DEB), Color(0xFF5B21B6), Color(0xFF2E1065), Color(0xFFFFFFFF)),
        shiftMark = Color(0xFFB4C0D2),
        shiftOffBg = Color(0xFF241B3D),
        shiftOffBorder = Color(0xFF5B45A0),
        shiftOffText = Color(0xFFC4B5FD),
        shiftOn = Color(0xFFA78BFA),
        onShiftOn = Color(0xFF1B1035),
        run = Color(0xFF34D399),
        onRun = Color(0xFF052E1B),
        layer = Color(0xFF94A3B8),
        ghost = Color(0xFFD8B4FE),
        part2d = Color(0xFF64748B),
        partEdge = Color(0xFF94A3B8),
        chuck = Color(0xFF64748B),
        valueSf = Color(0xFF93C5FD),
        valueTime = Color(0xFF7DD3FC),
        focusPill = Color(0xFF7896E6),
        onFocusPill = Color(0xFF0B1421),
    )

    fun of(dark: Boolean): SimPalette = if (dark) Dark else Light

    /** [com.bmt.nosecalc.calc.sim.GText.tokens] 의 종류 → 글자색. "!"(받지 않는 글)은 null — 본문색 + 물결 밑줄 */
    fun token(p: SimPalette, kind: String): Color? = when (kind) {
        "G" -> p.g
        "X", "Z", "U", "W" -> p.axis
        "P", "Q", "L" -> p.pql
        "T", "S", "F" -> p.tsf
        "M" -> p.m
        "R", "I", "K", ",C", ",R" -> p.rik
        "N" -> p.n
        "O" -> p.o
        "(" -> p.comment
        ";", "/", "%" -> p.mark
        else -> null
    }
}

/**
 * 3D 보기 색(설계 6.4 · 7.9 — 05). 바탕(`viewport3d` #09111D)이 두 테마 모두 어두워 라이트 · 다크 같은 색이다.
 * 음영 띠: 밝기 I(주변광 0.35 + 람베르트 0.65 + 금속 반짝임) 0 · 0.35 · 0.7 · 1.0 · 1.35 자리의 색을 잇는다 — 금속 그늘 #7E8893 · 바탕 #9BA5B1 · 밝은 면 #C9D1DA(설계 값).
 *
 * 대비(바탕 #09111D, WCAG): 금속 그늘 5.3 · 바탕 7.6 · 밝은 면 12.3 · 단면(한 단계 밝게) 바탕 9.9 — 금속과 단면 사이 1.3(색 차 + 평평한 음영으로 가름) ·
 * 척 몸통 1.9(05 처럼 뒤에 깔린 장식 — 정보 아님) · 턱 3.2 · 바닥 격자 1.7(장식). 안내 칩 흰 글 15.3, 칩 테 #34D399 9.9 · 어두운 단추 글 14.7 · 테 3.0.
 */
internal object Sim3DColors {
    /** 음영 띠의 밝기 자리 */
    val STOPS = floatArrayOf(0f, 0.35f, 0.7f, 1f, 1.35f)

    /** [com.bmt.nosecalc.calc.sim.Mesh] tag 순: 겉 · 단면 · 척 몸통 · 턱 */
    val ramps: List<IntArray> = listOf(
        intArrayOf(0xFF3E4752.toInt(), 0xFF7E8893.toInt(), 0xFF9BA5B1.toInt(), 0xFFC9D1DA.toInt(), 0xFFF2F5F8.toInt()),
        intArrayOf(0xFF56606B.toInt(), 0xFF98A2AD.toInt(), 0xFFB3BCC6.toInt(), 0xFFDCE2E8.toInt(), 0xFFFFFFFF.toInt()),
        intArrayOf(0xFF0E1A2B.toInt(), 0xFF1D3150.toInt(), 0xFF2A4468.toInt(), 0xFF3B5A85.toInt(), 0xFF5A7BA6.toInt()),
        intArrayOf(0xFF1A2333.toInt(), 0xFF2E3A4C.toInt(), 0xFF435166.toInt(), 0xFF5B6B80.toInt(), 0xFF7F8FA3.toInt()),
    )

    /** 바닥 격자 선(먼 곳일수록 옅게) */
    val grid = Color(0xFF3A5070)

    /** 안내 칩 '단면 절개 · 두 손가락 확대/이동'(05 — 어두운 바탕 + 초록 테) */
    val chipBg = Color(0xE60E2A1F)
    val chipBorder = Color(0xFF34D399)
    val chipText = Color(0xFFFFFFFF)

    /** 어두운 뷰포트 위 단추 '회전' · '자유'(05) — 꺼짐. 켜지면 앱 청록 */
    val darkBtnBg = Color(0xE613213A)
    val darkBtnBorder = Color(0xFF4A6283)
    val darkBtnText = Color(0xFFF1F5F9)
}
