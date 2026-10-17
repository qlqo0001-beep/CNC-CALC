package com.bmt.nosecalc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 선 아이콘(24 칸, 굵기 2). Icon(tint = …) 로 색을 입힌다 */
object AppIcons {
    val Menu by lazy {
        line("menu") {
            for (y in listOf(6f, 12f, 18f)) { moveTo(4f, y); lineTo(20f, y) }
        }
    }
    val Close by lazy {
        line("close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }
    val Sun by lazy {
        line("sun") {
            circle(12f, 12f, 4f)
            for (i in 0 until 8) {
                val a = i * PI / 4
                moveTo(12f + 7f * cos(a).toFloat(), 12f + 7f * sin(a).toFloat())
                lineTo(12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat())
            }
        }
    }
    val Moon by lazy {
        line("moon") {
            moveTo(12f, 3f)
            arcTo(6f, 6f, 0f, false, false, 21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 12f, 3f)
            close()
        }
    }
    val Doc by lazy {
        line("doc") {
            moveTo(14f, 2.5f); lineTo(6.5f, 2.5f)
            arcTo(2f, 2f, 0f, false, false, 4.5f, 4.5f)
            lineTo(4.5f, 19.5f)
            arcTo(2f, 2f, 0f, false, false, 6.5f, 21.5f)
            lineTo(17.5f, 21.5f)
            arcTo(2f, 2f, 0f, false, false, 19.5f, 19.5f)
            lineTo(19.5f, 8f); close()
            moveTo(14f, 2.5f); lineTo(14f, 8f); lineTo(19.5f, 8f)
            moveTo(8.5f, 9f); lineTo(10.5f, 9f)
            moveTo(8.5f, 13f); lineTo(15.5f, 13f)
            moveTo(8.5f, 17f); lineTo(15.5f, 17f)
        }
    }
    val Home by lazy {
        line("home") {
            moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
            moveTo(5.5f, 9f); lineTo(5.5f, 20f)
            arcTo(1f, 1f, 0f, false, false, 6.5f, 21f)
            lineTo(17.5f, 21f)
            arcTo(1f, 1f, 0f, false, false, 18.5f, 20f)
            lineTo(18.5f, 9f)
            moveTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f)
        }
    }
    val ChevronRight by lazy {
        line("chevron") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    }
    val Info by lazy {
        line("info") {
            circle(12f, 12f, 9.5f)
            moveTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(12f, 7.8f); lineTo(12f, 7.9f)
        }
    }
    val Alert by lazy {
        line("alert") {
            moveTo(10.3f, 4f)
            lineTo(2.6f, 17.5f)
            arcTo(2f, 2f, 0f, false, false, 4.3f, 20.5f)
            lineTo(19.7f, 20.5f)
            arcTo(2f, 2f, 0f, false, false, 21.4f, 17.5f)
            lineTo(13.7f, 4f)
            arcTo(2f, 2f, 0f, false, false, 10.3f, 4f)
            close()
            moveTo(12f, 9.5f); lineTo(12f, 13.5f)
            moveTo(12f, 17f); lineTo(12f, 17.1f)
        }
    }
    val Backspace by lazy {
        line("backspace") {
            moveTo(21f, 5f); lineTo(9f, 5f); lineTo(2.5f, 12f); lineTo(9f, 19f); lineTo(21f, 19f)
            arcTo(1f, 1f, 0f, false, false, 22f, 18f)
            lineTo(22f, 6f)
            arcTo(1f, 1f, 0f, false, false, 21f, 5f)
            close()
            moveTo(12.5f, 9.5f); lineTo(17.5f, 14.5f)
            moveTo(17.5f, 9.5f); lineTo(12.5f, 14.5f)
        }
    }
}

private fun line(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}
