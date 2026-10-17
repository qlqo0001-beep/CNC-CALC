package com.bmt.nosecalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.ui.LegendGlyph
import com.bmt.nosecalc.ui.LegendItem
import com.bmt.nosecalc.ui.ShapeViewerCard
import com.bmt.nosecalc.ui.UserView
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.ViewerState
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test

/** 형상 확인 카드(단추 줄 · 안내 알약 · 같은 보기를 다른 폭에서)를 라이트/다크로 → build/shots/viewer-* */
class ViewerShotsTest {
    @Test
    fun card() {
        // 높은 형상(맞춤) · 같은 형상을 확대(맞춤 단추가 켜짐) · 납작한 2중 테이퍼(낮은 상자)
        Shots.both("viewer-card", heightDp = 1480) {
            AppHost(it) {
                Page {
                    Sample(Face)
                    Sample(Face, remember { ViewerState(UserView(2.5f, Pt(-3.5, 13.5))) })
                    Sample(DoubleTaper)
                }
            }
        }
    }

    @Test
    fun hint() {
        // 안내는 1.5초 뒤 사라진다 — 1.2초(12프레임)에 찍는다
        Shots.both("viewer-hint", heightDp = 1060, frames = 12) {
            AppHost(it) {
                Page {
                    Sample(Face, remember { ViewerState().apply { showHint(mouse = false) } })
                    Sample(Face, remember { ViewerState().apply { showHint(mouse = true) } })
                }
            }
        }
    }

    /** 같은 보기(맞춤 × 1.2, 그림 가운데)를 폰 폭과 PC 폭에서 — 둘 다 그림이 가운데 */
    @Test
    fun sameViewOnPhoneAndPc() {
        for ((w, h) in listOf(412 to 540, 640 to 700)) {
            Shots.both("viewer-view-w$w", heightDp = h, widthDp = w) {
                AppHost(it) { Page { Sample(Face, remember { ViewerState(UserView(1.2f, Pt(-13.0, 9.9))) }) } }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.cardGap)) { content() }
}

/** 시험 그림: 도면 · 프로그램 경로 · 급송, 점 이름 */
private class SampleShape(val drawing: Contour, val program: Contour, val entry: Pt, val points: List<Pt>, val subtitle: String)

/** 끝면에서 R5 로 돌아 Ø30 · 단차 Ø38 — 세로가 긴 그림(노즈 R0.8 만큼 바깥 경로) */
private val Face = SampleShape(
    Contour(
        listOf(
            Line(Pt(0.0, 0.0), Pt(0.0, 10.0)),
            Arc(Pt(0.0, 10.0), Pt(-5.0, 15.0), Pt(-5.0, 10.0), cw = false),
            Line(Pt(-5.0, 15.0), Pt(-20.0, 15.0)),
            Line(Pt(-20.0, 15.0), Pt(-20.0, 19.0)),
            Line(Pt(-20.0, 19.0), Pt(-28.0, 19.0)),
        ),
    ),
    Contour(
        listOf(
            Line(Pt(0.8, 0.0), Pt(0.8, 10.0)),
            Arc(Pt(0.8, 10.0), Pt(-5.0, 15.8), Pt(-5.0, 10.0), cw = false),
            Line(Pt(-5.0, 15.8), Pt(-19.2, 15.8)),
            Line(Pt(-19.2, 15.8), Pt(-19.2, 19.8)),
            Line(Pt(-19.2, 19.8), Pt(-28.0, 19.8)),
        ),
    ),
    Pt(2.0, 0.0),
    listOf(Pt(0.0, 10.0), Pt(-5.0, 15.0)),
    "노즈 R0.8 · 가상 인선 3번",
)

/** 2중 테이퍼(Ø40 → Ø50 → Ø40) — 가로로 납작한 그림 */
private val DoubleTaper = SampleShape(
    Contour.polyline(Pt(5.0, 20.0), Pt(0.0, 20.0), Pt(-5.0, 25.0), Pt(-10.0, 20.0), Pt(-15.0, 20.0)),
    Contour.polyline(Pt(1.0, 20.4), Pt(0.17, 20.4), Pt(-5.0, 25.57), Pt(-10.17, 20.4), Pt(-15.0, 20.4)),
    Pt(2.0, 20.4),
    listOf(Pt(0.0, 20.0), Pt(-5.0, 25.0), Pt(-10.0, 20.0)),
    "노즈 R0.4 · 가상 인선 3번",
)

@Composable
private fun Sample(s: SampleShape, state: ViewerState = remember { ViewerState() }) {
    val c = AppTheme.colors
    val colors = listOf(c.drawT1, c.drawT2, c.indigo)
    ShapeViewerCard(
        listOf(
            ViewerLine(s.drawing, c.drawProfile, 2.5.dp),
            ViewerLine(Contour.polyline(s.entry, s.program.segs.first().start), c.simRapid, 1.6.dp, dashed = true),
            ViewerLine(s.program, c.simToolpath, 2.2.dp),
        ),
        s.points.mapIndexed { i, p -> ViewerMark(p, colors[i], "T${i + 1}") } + ViewerMark(s.entry, c.simRapid, "진입"),
        listOf(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.simToolpath, "직접 보정 경로"),
            LegendItem(c.simRapid, "급송", LegendGlyph.Dashed),
        ),
        subtitle = s.subtitle,
        state = state,
    )
}
