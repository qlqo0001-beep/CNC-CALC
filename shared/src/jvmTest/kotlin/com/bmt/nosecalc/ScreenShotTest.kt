package com.bmt.nosecalc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.screens.TaperHelp
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.FieldRole
import com.bmt.nosecalc.ui.Modal
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.NumberPad
import com.bmt.nosecalc.ui.OutlineTabs
import com.bmt.nosecalc.ui.ResultCard
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.ShapeViewer
import com.bmt.nosecalc.ui.TypeGrid
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.test.Test

/** 화면마다 라이트/다크 PNG → build/shots. 원본 사진(docs/store)과 나란히 보고 다듬는다 */
class ScreenShotTest {
    @Test
    fun taper() = Shots.both("02-taper") { App(it) }

    @Test
    fun appMenu() = Shots.both("01-menu", setup = { it.menuOpen = true }) { App(it) }

    @Test
    fun features() {
        Feature.menu.forEach { f -> Shots.both("feature-${f.name.lowercase()}", setup = { it.open(f) }) { App(it) } }
    }

    @Test
    fun help() = Shots.both("help", setup = { it.helpOpen = true }) { App(it) }

    @Test
    fun keypad() = Shots.both("keypad") {
        AppHost(it) {
            TaperScreen()
            NumberPad("Z축 시작점", "mm", "5.0", onChange = {}, onClose = {})
        }
    }

    @Test
    fun modal() = Shots.both("modal") {
        AppHost(it) {
            TaperScreen()
            Modal("시뮬레이션", onDismiss = {}) {
                Text("모달 내용 자리", color = AppTheme.colors.textBody)
            }
        }
    }

    @Test
    fun components() = Shots.both("components", heightDp = 2150, frames = 12, setup = { it.toast("측정점 모드: 원을 터치하세요") }) {
        AppHost(it) { FeatureScaffold(Feature.TAPER, TaperHelp) { Gallery() } }
    }
}

/** 공통 부품 모아 보기(다음 화면들이 이걸 조합한다) */
@Composable
private fun Gallery() {
    val c = AppTheme.colors
    val taper = TaperCalc.calc(TaperInput(x1 = 40.0, z1 = 0.0, x2 = 50.0, z2 = -5.0)).solution!!
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlineTabs(listOf(listOf("외경", "내경", "외경 홈"), listOf("외경 2중 테이퍼", "내경 2중 테이퍼")), 0, {})
        Segmented(listOf("외경 가공 (OD)", "내경 가공 (ID)"), 0, {}, height = 56.dp, fontSize = 20)
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Z축 시작점", "5.0", {}, Modifier.weight(1f), role = FieldRole.Start)
                    NumberField("회전수 (RPM)", "1821", {}, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("가공 목표점(Z)", "-25.0", {}, Modifier.weight(1f), role = FieldRole.End)
                    NumberField("Z2", "", {}, Modifier.weight(1f), unit = "mm", autoValue = "-7.386")
                }
                NumberField("한 변 길이", "40", {}, unit = "mm", role = FieldRole.Dim)
            }
        }
        TypeGrid(3, selected = 2, onSelect = {}) { type ->
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val pts = when (type) {
                    1 -> listOf(0.19f to 0.27f, 0.45f to 0.27f, 0.68f to 0.52f, 0.68f to 0.80f)
                    2 -> listOf(0.38f to 0.08f, 0.38f to 0.37f, 0.62f to 0.62f, 0.62f to 0.90f)
                    else -> listOf(0.11f to 0.39f, 0.38f to 0.39f, 0.61f to 0.64f, 0.87f to 0.64f)
                }.map { Offset(it.first * w, it.second * h) }
                drawLine(c.drawRef, pts[2], Offset(pts[2].x - 0.3f * w, pts[2].y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, c.drawProfile, style = Stroke(5f))
                for (p in listOf(pts[1], pts[2])) {
                    drawCircle(c.subtle, 9f, p)
                    drawCircle(c.drawRef, 9f, p, style = Stroke(3f))
                }
            }
        }
        ResultCard("보정 좌표", listOf("X" to "34.000", "Z" to "-2.186"))
        WarningBanner("노즈R 이 경사보다 큽니다. 숫자를 확인하세요.")
        WarningBanner("값이 서로 맞지 않습니다(T2 가 T1 보다 작음).", level = WarnLevel.Error)
        ShapeViewer(
            listOf(
                ViewerLine(taper.drawing, c.drawProfile),
                ViewerLine(taper.program, c.simToolpath, dashed = true),
            ),
            Modifier.fillMaxWidth().height(260.dp),
            marks = listOf(
                ViewerMark(taper.points[0].vertex, c.drawT1, "T1"),
                ViewerMark(taper.points[1].vertex, c.drawT2, "T2"),
            ),
        )
    }
}
