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
    val Plus by lazy {
        line("plus") {
            moveTo(5f, 12f); lineTo(19f, 12f)
            moveTo(12f, 5f); lineTo(12f, 19f)
        }
    }
    val Minus by lazy {
        line("minus") { moveTo(5f, 12f); lineTo(19f, 12f) }
    }

    /** 맞춤(전체 보기): 네 모서리 꺾쇠 */
    val Fit by lazy {
        line("fit") {
            moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
            moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        }
    }

    /** 크게 보기: 대각선 화살 둘(오른쪽 위 · 왼쪽 아래) */
    val Expand by lazy {
        line("expand") {
            moveTo(14f, 10f); lineTo(20f, 4f)
            moveTo(14.5f, 4f); lineTo(20f, 4f); lineTo(20f, 9.5f)
            moveTo(10f, 14f); lineTo(4f, 20f)
            moveTo(4f, 14.5f); lineTo(4f, 20f); lineTo(9.5f, 20f)
        }
    }

    /** 복사: 어긋난 둥근 네모 둘(뒤 것은 앞 것에 가린 만큼 뺀다) */
    val Copy by lazy {
        line("copy") {
            moveTo(11f, 9f); lineTo(18f, 9f)
            arcTo(2f, 2f, 0f, false, true, 20f, 11f)
            lineTo(20f, 18f)
            arcTo(2f, 2f, 0f, false, true, 18f, 20f)
            lineTo(11f, 20f)
            arcTo(2f, 2f, 0f, false, true, 9f, 18f)
            lineTo(9f, 11f)
            arcTo(2f, 2f, 0f, false, true, 11f, 9f)
            close()
            moveTo(9f, 15f); lineTo(6f, 15f)
            arcTo(2f, 2f, 0f, false, true, 4f, 13f)
            lineTo(4f, 6f)
            arcTo(2f, 2f, 0f, false, true, 6f, 4f)
            lineTo(13f, 4f)
            arcTo(2f, 2f, 0f, false, true, 15f, 6f)
            lineTo(15f, 9f)
        }
    }
    val Check by lazy {
        line("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    }

    /** 도움말: 원 안의 물음표 */
    val Help by lazy {
        line("help") {
            circle(12f, 12f, 9.5f)
            moveTo(9.3f, 9.4f)
            arcTo(2.8f, 2.8f, 0f, true, true, 13.4f, 11.9f)
            curveTo(12.6f, 12.3f, 12f, 12.8f, 12f, 13.8f)
            moveTo(12f, 17f); lineTo(12f, 17.1f)
        }
    }

    /** 멈춤(계산 멈춤 오류): 팔각형 안의 느낌표 — 경고(Alert 삼각형)와 모양으로 갈린다 */
    val Stop by lazy {
        line("stop") {
            moveTo(8.1f, 2.6f); lineTo(15.9f, 2.6f); lineTo(21.4f, 8.1f); lineTo(21.4f, 15.9f)
            lineTo(15.9f, 21.4f); lineTo(8.1f, 21.4f); lineTo(2.6f, 15.9f); lineTo(2.6f, 8.1f)
            close()
            moveTo(12f, 7.5f); lineTo(12f, 12.5f)
            moveTo(12f, 16.2f); lineTo(12f, 16.3f)
        }
    }
    val Back by lazy {
        line("back") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    /** 3점호: 1/4 원호와 그 위의 세 점(시작 · 가운데 · 끝) */
    val Arc3 by lazy {
        line("arc3") {
            moveTo(5f, 19f)
            arcTo(14f, 14f, 0f, false, true, 19f, 5f)
            for ((x, y) in listOf(5f to 19f, 9.1f to 9.1f, 19f to 5f)) circle(x, y, 1f)
        }
    }
    val ChevronDown by lazy {
        line("chevronDown") { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
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
