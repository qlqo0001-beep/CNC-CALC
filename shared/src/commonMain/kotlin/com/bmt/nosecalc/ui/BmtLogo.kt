package com.bmt.nosecalc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BmtBlue = Color(0xFF0A4AA6)

/** BMT 표식(점 둘 + 4분의 1 고리). 받은 로고 그림을 보고 다시 그린 것 — 200×200 칸 */
val BmtMark: ImageVector by lazy {
    ImageVector.Builder("BmtMark", 200.dp, 200.dp, 200f, 200f).apply {
        path(fill = SolidColor(BmtBlue)) {
            // 바깥 반지름 140, 안 반지름 85, 중심 (190,180) 의 왼쪽 위 4분의 1 고리
            moveTo(50f, 180f)
            arcTo(140f, 140f, 0f, false, true, 190f, 40f)
            lineTo(190f, 95f)
            arcTo(85f, 85f, 0f, false, false, 105f, 180f)
            close()
        }
        path(fill = SolidColor(BmtBlue)) {
            moveTo(48f, 52f)
            arcTo(24f, 24f, 0f, true, true, 96f, 52f)
            arcTo(24f, 24f, 0f, true, true, 48f, 52f)
            close()
        }
        path(fill = SolidColor(BmtBlue)) {
            moveTo(138f, 152f)
            arcTo(20f, 20f, 0f, true, true, 178f, 152f)
            arcTo(20f, 20f, 0f, true, true, 138f, 152f)
            close()
        }
    }.build()
}

/**
 * "BMT" 글자. 폰과 PC 의 세리프 글꼴이 달라서 글꼴 대신 윤곽을 벡터로 넣었다 —
 * Noto Serif Black(OFL, notofonts 공식 배포본)의 B·M·T 윤곽, 글자 크기 100 기준 234.1×71.4 칸
 */
val BmtWordmark: ImageVector by lazy {
    ImageVector.Builder("BmtWordmark", 234.1.dp, 71.4.dp, 234.1f, 71.4f).apply {
        path(fill = SolidColor(BmtBlue)) {
            moveTo(0.0f, 71.4f)
            lineTo(0.0f, 65.3f)
            lineTo(5.2f, 65.3f)
            quadTo(7.3f, 65.3f, 8.3f, 63.8f)
            quadTo(9.2f, 62.3f, 9.2f, 59.8f)
            lineTo(9.2f, 11.6f)
            quadTo(9.2f, 8.4f, 7.8f, 7.3f)
            quadTo(6.3f, 6.1f, 4.9f, 6.1f)
            lineTo(0.0f, 6.1f)
            lineTo(0.0f, 0.0f)
            lineTo(32.8f, 0.0f)
            quadTo(48.0f, 0.0f, 56.0f, 4.0f)
            quadTo(64.0f, 7.9f, 64.0f, 17.4f)
            quadTo(64.0f, 22.0f, 61.9f, 25.2f)
            quadTo(59.8f, 28.4f, 56.4f, 30.4f)
            quadTo(53.0f, 32.3f, 49.0f, 33.3f)
            lineTo(49.0f, 33.8f)
            quadTo(56.9f, 35.1f, 61.9f, 39.4f)
            quadTo(66.9f, 43.7f, 66.9f, 51.6f)
            quadTo(66.9f, 61.5f, 59.3f, 66.5f)
            quadTo(51.6f, 71.4f, 35.8f, 71.4f)
            lineTo(0.0f, 71.4f)
            close()
            moveTo(31.9f, 30.6f)
            quadTo(37.5f, 30.6f, 40.3f, 27.8f)
            quadTo(43.2f, 25.0f, 43.2f, 18.4f)
            quadTo(43.2f, 11.9f, 40.2f, 9.4f)
            quadTo(37.2f, 6.9f, 31.7f, 6.9f)
            lineTo(29.1f, 6.9f)
            lineTo(29.1f, 30.6f)
            lineTo(31.9f, 30.6f)
            close()
            moveTo(34.3f, 64.5f)
            quadTo(39.6f, 64.5f, 42.6f, 61.6f)
            quadTo(45.6f, 58.6f, 45.6f, 51.3f)
            quadTo(45.6f, 37.5f, 34.5f, 37.5f)
            lineTo(29.1f, 37.5f)
            lineTo(29.1f, 64.5f)
            lineTo(34.3f, 64.5f)
            close()
            moveTo(72.0f, 71.4f)
            lineTo(72.0f, 65.3f)
            lineTo(75.3f, 65.3f)
            quadTo(78.1f, 65.3f, 79.7f, 64.0f)
            quadTo(81.2f, 62.7f, 81.2f, 59.8f)
            lineTo(81.2f, 11.6f)
            quadTo(81.2f, 8.7f, 79.8f, 7.4f)
            quadTo(78.5f, 6.1f, 76.9f, 6.1f)
            lineTo(72.0f, 6.1f)
            lineTo(72.0f, 0.0f)
            lineTo(105.2f, 0.0f)
            lineTo(118.8f, 41.7f)
            lineTo(131.3f, 0.0f)
            lineTo(164.1f, 0.0f)
            lineTo(164.1f, 6.1f)
            lineTo(158.8f, 6.1f)
            quadTo(157.5f, 6.1f, 156.2f, 7.4f)
            quadTo(155.0f, 8.6f, 155.0f, 10.9f)
            lineTo(155.0f, 60.6f)
            quadTo(155.0f, 65.3f, 159.8f, 65.3f)
            lineTo(164.1f, 65.3f)
            lineTo(164.1f, 71.4f)
            lineTo(128.1f, 71.4f)
            lineTo(128.1f, 65.3f)
            lineTo(129.6f, 65.3f)
            quadTo(132.8f, 65.3f, 134.1f, 64.0f)
            quadTo(135.3f, 62.6f, 135.3f, 59.2f)
            lineTo(135.3f, 11.7f)
            lineTo(117.1f, 71.4f)
            lineTo(109.2f, 71.4f)
            lineTo(89.7f, 11.5f)
            lineTo(89.7f, 59.3f)
            quadTo(89.7f, 63.3f, 91.7f, 64.3f)
            quadTo(93.6f, 65.3f, 97.2f, 65.3f)
            lineTo(98.1f, 65.3f)
            lineTo(98.1f, 71.4f)
            lineTo(72.0f, 71.4f)
            close()
            moveTo(180.8f, 71.4f)
            lineTo(180.8f, 65.3f)
            lineTo(184.0f, 65.3f)
            quadTo(186.9f, 65.3f, 188.5f, 64.2f)
            quadTo(190.2f, 63.1f, 190.2f, 58.9f)
            lineTo(190.2f, 6.9f)
            lineTo(183.4f, 6.9f)
            quadTo(178.9f, 6.9f, 177.2f, 8.4f)
            quadTo(175.5f, 9.9f, 175.1f, 13.2f)
            lineTo(174.2f, 20.8f)
            lineTo(166.4f, 20.8f)
            lineTo(166.9f, 0.0f)
            lineTo(233.6f, 0.0f)
            lineTo(234.1f, 20.8f)
            lineTo(226.3f, 20.8f)
            lineTo(225.5f, 13.2f)
            quadTo(225.1f, 9.9f, 223.4f, 8.4f)
            quadTo(221.7f, 6.9f, 217.2f, 6.9f)
            lineTo(210.4f, 6.9f)
            lineTo(210.4f, 59.3f)
            quadTo(210.4f, 63.3f, 212.1f, 64.3f)
            quadTo(213.8f, 65.3f, 216.6f, 65.3f)
            lineTo(219.8f, 65.3f)
            lineTo(219.8f, 71.4f)
            lineTo(180.8f, 71.4f)
            close()
        }
    }.build()
}

/** 표식 + "BMT" 글자. height = 표식 높이, 글자는 그 0.62 */
@Composable
fun BmtLogo(modifier: Modifier = Modifier, height: Dp = 28.dp, color: Color = BmtBlue) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(height * 0.22f), verticalAlignment = Alignment.CenterVertically) {
        Image(BmtMark, contentDescription = null, Modifier.size(height), colorFilter = ColorFilter.tint(color))
        Image(
            BmtWordmark, contentDescription = "BMT",
            Modifier.height(height * 0.62f).width(height * 0.62f * (234.1f / 71.4f)),
            colorFilter = ColorFilter.tint(color),
        )
    }
}
