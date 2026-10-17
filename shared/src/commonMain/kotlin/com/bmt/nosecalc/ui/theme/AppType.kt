package com.bmt.nosecalc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 글자 크기 단계. 숫자는 보는 거리에 맞춰 넷(num*)으로만 쓴다(기계 앞에서 읽는 결과 좌표 = numHero).
 * 화면 규칙:
 * - 결과·좌표·NC 숫자는 num* (고정폭 굵게)
 * - 카드 제목은 cardTitle, 시트 제목은 sheetTitle, 칸 이름은 label
 * - 설명·힌트 글은 caption
 * - 화면 Column 은 .padding(horizontal = Space.gutter) + spacedBy(Space.cardGap), 카드 안 여백은 Space.cardPad
 * 색은 넣지 않는다 — Text(style = AppType.numHero, color = c.valueComp)처럼 쓴다
 */
object AppType {
    val numHero: TextStyle @Composable @ReadOnlyComposable get() = mono(24.sp)
    val numLarge: TextStyle @Composable @ReadOnlyComposable get() = mono(18.sp)
    val numBody: TextStyle @Composable @ReadOnlyComposable get() = mono(15.sp)
    val numSmall: TextStyle @Composable @ReadOnlyComposable get() = mono(12.5.sp)
    val screenTitle: TextStyle @Composable @ReadOnlyComposable get() = sans(20.sp, FontWeight.Bold)
    val cardTitle: TextStyle @Composable @ReadOnlyComposable get() = sans(16.sp, FontWeight.Bold)
    val sheetTitle: TextStyle @Composable @ReadOnlyComposable get() = sans(18.sp, FontWeight.Bold)
    val label: TextStyle @Composable @ReadOnlyComposable get() = sans(14.sp, FontWeight.SemiBold)
    val body: TextStyle @Composable @ReadOnlyComposable get() = sans(14.sp, FontWeight.Normal, 21.sp)
    val caption: TextStyle @Composable @ReadOnlyComposable get() = sans(12.5.sp, FontWeight.Normal, 18.sp)

    @Composable @ReadOnlyComposable
    private fun mono(size: TextUnit) = TextStyle(fontFamily = AppTheme.mono, fontSize = size, fontWeight = FontWeight.Bold)

    @Composable @ReadOnlyComposable
    private fun sans(size: TextUnit, weight: FontWeight, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = AppTheme.sans, fontSize = size, fontWeight = weight, lineHeight = lineHeight)
}

/** 여백: 화면 양옆 · 카드 안 · 카드 사이 · 입력칸 사이 */
object Space {
    val gutter = 12.dp
    val cardPad = 16.dp
    val cardGap = 12.dp
    val fieldGap = 12.dp
}

/** 모서리 둥글기 */
object Radius {
    val sheet = 20.dp
    val card = 14.dp
    val control = 10.dp
    val badge = 6.dp
}
