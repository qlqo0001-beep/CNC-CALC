package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 시뮬레이터 편집기 아이콘 — `ui/AppIcons` 와 같은 선 꼴(24 칸, 굵기 2). 공용 아이콘 파일은 고치지 않고 여기 둔다 */
internal object SimIcons {
    /** 되돌리기: 왼쪽으로 도는 화살 */
    val Undo by lazy {
        line("undo") {
            moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
            moveTo(4f, 9f); lineTo(15f, 9f)
            arcTo(5.5f, 5.5f, 0f, false, true, 15f, 20f)
            lineTo(11f, 20f)
        }
    }
    val Redo by lazy {
        line("redo") {
            moveTo(15f, 14f); lineTo(20f, 9f); lineTo(15f, 4f)
            moveTo(20f, 9f); lineTo(9f, 9f)
            arcTo(5.5f, 5.5f, 0f, false, false, 9f, 20f)
            lineTo(13f, 20f)
        }
    }
    val Search by lazy {
        line("search") {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f)
        }
    }

    /** 재생(궤적 확인): 오른쪽 삼각형 */
    val Play by lazy {
        line("play") { moveTo(7f, 4.5f); lineTo(19f, 12f); lineTo(7f, 19.5f); close() }
    }

    /** CNC 키패드 머리의 칩(09 ③) */
    val Chip by lazy {
        line("chip") {
            moveTo(7f, 6f); lineTo(17f, 6f); lineTo(18f, 7f); lineTo(18f, 17f); lineTo(17f, 18f); lineTo(7f, 18f); lineTo(6f, 17f); lineTo(6f, 7f); close()
            moveTo(10f, 10f); lineTo(14f, 10f); lineTo(14f, 14f); lineTo(10f, 14f); close()
            for (v in listOf(9f, 12f, 15f)) {
                moveTo(v, 2.5f); lineTo(v, 6f); moveTo(v, 18f); lineTo(v, 21.5f)
                moveTo(2.5f, v); lineTo(6f, v); moveTo(18f, v); lineTo(21.5f, v)
            }
        }
    }

    /** ⏎ 새 줄: 아래로 내려와 왼쪽으로 꺾인 화살(09 의 보라 키) */
    val Enter by lazy {
        line("enter") {
            moveTo(18f, 4f); lineTo(18f, 14f); lineTo(6f, 14f)
            moveTo(10f, 10f); lineTo(6f, 14f); lineTo(10f, 18f)
        }
    }

    /** 키패드 열기: 키보드 */
    val Keyboard by lazy {
        line("keyboard") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            arcTo(1.5f, 1.5f, 0f, false, true, 21.5f, 7.5f)
            lineTo(21.5f, 16.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20f, 18f)
            lineTo(4f, 18f)
            arcTo(1.5f, 1.5f, 0f, false, true, 2.5f, 16.5f)
            lineTo(2.5f, 7.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 4f, 6f)
            close()
            for (x in listOf(6.5f, 10f, 13.5f, 17f)) { moveTo(x, 10f); lineTo(x + 0.1f, 10f) }
            moveTo(8f, 14.5f); lineTo(16f, 14.5f)
        }
    }

    /** 더 보기: 가로 점 셋 */
    val More by lazy {
        line("more") { for (x in listOf(5f, 12f, 19f)) { moveTo(x, 12f); lineTo(x + 0.1f, 12f) } }
    }

    /** 지우기: 휴지통 */
    val Trash by lazy {
        line("trash") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(9f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(15f, 7f)
            moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
            moveTo(10f, 11f); lineTo(10f, 16.5f)
            moveTo(14f, 11f); lineTo(14f, 16.5f)
        }
    }
    val ChevronUp by lazy {
        line("chevronUp") { moveTo(5f, 15f); lineTo(12f, 8f); lineTo(19f, 15f) }
    }

    // ---- 궤적 창(05 · 06 · 10)

    /** 궤적 창 머리: 모니터 */
    val Monitor by lazy {
        line("monitor") {
            moveTo(4f, 4.5f); lineTo(20f, 4.5f); lineTo(20f, 15.5f); lineTo(4f, 15.5f); close()
            moveTo(9f, 20f); lineTo(15f, 20f)
            moveTo(12f, 15.5f); lineTo(12f, 20f)
        }
    }

    /** 시뮬레이션 설정: 미끄럼 셋 */
    val Sliders by lazy {
        line("sliders") {
            moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 17f); lineTo(20f, 17f)
            circle(9f, 7f, 1.8f); circle(15f, 12f, 1.8f); circle(8f, 17f, 1.8f)
        }
    }

    /** 공구 설정: 렌치 */
    val Wrench by lazy {
        line("wrench") {
            moveTo(14.5f, 4f)
            arcTo(4.5f, 4.5f, 0f, false, false, 19.8f, 9.6f)
            lineTo(17f, 10f); lineTo(15.5f, 8.5f); lineTo(16f, 5.8f); close()
            moveTo(14.8f, 9.2f); lineTo(5f, 19f)
        }
    }

    /** |◀ 블록 처음으로 */
    val SkipBack by lazy {
        line("skipBack") { moveTo(6f, 5f); lineTo(6f, 19f); moveTo(18f, 5f); lineTo(9f, 12f); lineTo(18f, 19f); close() }
    }

    /** ▶| 블록 끝까지 */
    val SkipFwd by lazy {
        line("skipFwd") { moveTo(18f, 5f); lineTo(18f, 19f); moveTo(6f, 5f); lineTo(15f, 12f); lineTo(6f, 19f); close() }
    }

    val Pause by lazy {
        line("pause") { moveTo(8f, 5f); lineTo(8f, 19f); moveTo(16f, 5f); lineTo(16f, 19f) }
    }

    /** 리셋: 처음으로 도는 화살 */
    val Reset by lazy {
        line("reset") {
            moveTo(4f, 5f); lineTo(4f, 10f); lineTo(9f, 10f)
            moveTo(4.6f, 9.5f)
            arcTo(8f, 8f, 0f, true, true, 4.5f, 14f)
        }
    }

    /** 처음부터(끝난 뒤 재생) */
    val Replay by lazy {
        line("replay") {
            moveTo(20f, 5f); lineTo(20f, 10f); lineTo(15f, 10f)
            moveTo(19.4f, 9.5f)
            arcTo(8f, 8f, 0f, true, false, 19.5f, 14f)
            moveTo(10f, 9f); lineTo(14.5f, 12f); lineTo(10f, 15f); close()
        }
    }

    /** 측정(자): ㄴ 자 눈금 */
    val Ruler by lazy {
        line("ruler") {
            moveTo(5f, 4f); lineTo(5f, 19f); lineTo(20f, 19f); lineTo(20f, 14f); lineTo(10f, 14f); lineTo(10f, 4f); close()
            moveTo(5f, 8f); lineTo(7.5f, 8f); moveTo(5f, 11f); lineTo(7.5f, 11f)
            moveTo(14f, 19f); lineTo(14f, 16.5f); moveTo(17f, 19f); lineTo(17f, 16.5f)
        }
    }

    /** 각도 확인: ∠ */
    val Angle by lazy {
        line("angle") {
            moveTo(19f, 5f); lineTo(5f, 19f); lineTo(20f, 19f)
            moveTo(11f, 13f)
            arcTo(8.5f, 8.5f, 0f, false, true, 13.5f, 19f)
        }
    }

    /** 싱글 블록: 줄 셋 + 점 */
    val Lines by lazy {
        line("lines") {
            moveTo(9f, 6f); lineTo(20f, 6f); moveTo(9f, 12f); lineTo(20f, 12f); moveTo(9f, 18f); lineTo(20f, 18f)
            moveTo(4.5f, 6f); lineTo(4.6f, 6f); moveTo(4.5f, 12f); lineTo(4.6f, 12f); moveTo(4.5f, 18f); lineTo(4.6f, 18f)
        }
    }

    /** M01 선택 정지: 손바닥 */
    val Hand by lazy {
        line("hand") {
            moveTo(7f, 13f); lineTo(7f, 7f)
            moveTo(10f, 12f); lineTo(10f, 4.5f)
            moveTo(13f, 12f); lineTo(13f, 5f)
            moveTo(16f, 12.5f); lineTo(16f, 7f)
            moveTo(7f, 13f); lineTo(7f, 15f)
            arcTo(5f, 5f, 0f, false, false, 12f, 20f)
            lineTo(13f, 20f)
            arcTo(4f, 4f, 0f, false, false, 17f, 16f)
            lineTo(19f, 11f)
        }
    }

    /** 속도: 계기 */
    val Gauge by lazy {
        line("gauge") {
            moveTo(4f, 16f)
            arcTo(8f, 8f, 0f, true, true, 20f, 16f)
            moveTo(12f, 15f); lineTo(16f, 9f)
        }
    }

    /** 범례: 선 두 개 + 점 */
    val Legend by lazy {
        line("legend") {
            moveTo(4f, 8f); lineTo(10f, 8f); moveTo(14f, 8f); lineTo(20f, 8f)
            moveTo(4f, 16f); lineTo(6f, 16f); moveTo(9f, 16f); lineTo(11f, 16f)
            circle(17f, 16f, 2.5f)
        }
    }

    /** 3D 1/4 단면 절개: 원에서 오른쪽 위 1/4 을 떼어 낸 꼴 */
    val Quarter by lazy {
        line("quarter") {
            moveTo(12f, 4f)
            arcTo(8f, 8f, 0f, true, false, 20f, 12f)
            lineTo(12f, 12f); close()
            moveTo(15f, 3.5f); lineTo(15f, 9f); lineTo(20.5f, 9f)
        }
    }

    /** 3D 회전(공작물이 축 둘레로 돎): 두 화살 원 */
    val Spin by lazy {
        line("spin") {
            moveTo(19.5f, 9f)
            arcTo(8f, 8f, 0f, false, false, 5f, 9.5f)
            moveTo(4.5f, 15f)
            arcTo(8f, 8f, 0f, false, false, 19f, 14.5f)
            moveTo(20f, 4.5f); lineTo(19.5f, 9f); lineTo(15f, 8.5f)
            moveTo(4f, 19.5f); lineTo(4.5f, 15f); lineTo(9f, 15.5f)
        }
    }

    /** 3D 자유 시점: 가운데 점을 도는 기운 궤도 + 화살 */
    val Orbit by lazy {
        line("orbit") {
            circle(12f, 12f, 2.2f)
            moveTo(4f, 15f)
            arcTo(9f, 4.5f, -20f, false, true, 19.5f, 7f)
            moveTo(16.5f, 5.2f); lineTo(19.5f, 7f); lineTo(17f, 9.6f)
            moveTo(20f, 9.5f)
            arcTo(9f, 4.5f, -20f, false, true, 6.5f, 17.5f)
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
