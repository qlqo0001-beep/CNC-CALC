package com.bmt.nosecalc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
