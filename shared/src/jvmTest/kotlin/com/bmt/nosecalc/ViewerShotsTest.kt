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
import com.bmt.nosecalc.screens.taper.circle
import com.bmt.nosecalc.ui.Arrow
import com.bmt.nosecalc.ui.LegendGlyph
import com.bmt.nosecalc.ui.LegendItem
import com.bmt.nosecalc.ui.MarkKind
import com.bmt.nosecalc.ui.ShapeViewerCard
import com.bmt.nosecalc.ui.UserView
import com.bmt.nosecalc.ui.ViewerFill
import com.bmt.nosecalc.ui.ViewerFullScreen
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.ViewerState
import com.bmt.nosecalc.ui.ViewerStock
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test

/** 형상 확인 카드(단추 줄 · 안내 알약 · 같은 보기를 다른 폭에서 · 그림 API · 라벨 · 범례 · 크게 보기)를 라이트/다크로 → build/shots/viewer-* */
class ViewerShotsTest {
    @Test
    fun card() {
        // 높은 형상(맞춤) · 같은 형상을 확대(맞춤 단추가 켜짐) · 납작한 2중 테이퍼(낮은 상자)
        Shots.both("viewer-card", heightDp = 1500) {
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

    /** 그림 API: 실선 · 끊은 선 · 점선, 선과 원호(시계 · 반시계)의 화살, 점 넷, 외경 · 내경 소재(빗금 · 척 쪽), 면, 축척 막대 */
    @Test
    fun api() {
        Shots.both("viewer-api", heightDp = 1180) {
            AppHost(it) {
                Page {
                    ApiOuter()
                    ApiInner()
                }
            }
        }
    }

    /** 점이 몰리고 뷰어 가장자리에 붙은 라벨 — 모두 뷰어 안, 서로 겹치지 않게 */
    @Test
    fun labels() {
        Shots.both("viewer-labels", heightDp = 560) {
            AppHost(it) { Page { Crowded() } }
        }
    }

    /** 좁은 폰(360dp) · 글꼴 1.3배에서 범례 열 칸이 잘리지 않고 줄을 바꾼다 */
    @Test
    fun legendNarrowLargeFont() {
        Shots.both("viewer-legend-w360", heightDp = 760, widthDp = 360, fontScale = 1.3f) {
            AppHost(it) { Page { ApiOuter(fullLegend = true) } }
        }
    }

    /** 크게 보기: 카드 위에 화면 전체로. 카드에서 확대한 보기를 그대로 이어받는다(맞춤 단추 켜짐) */
    @Test
    fun full() {
        Shots.both("viewer-full") {
            AppHost(it) {
                val state = remember { ViewerState(UserView(1.6f, Pt(-4.0, 12.0))) }
                Page { Sample(Face, state) }
                val p = partsOf(Face)
                ViewerFullScreen(p.lines, p.marks, p.legend, "형상 확인", Face.subtitle, p.stock, emptyList(), state) {}
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

private class Parts(val lines: List<ViewerLine>, val marks: List<ViewerMark>, val legend: List<LegendItem>, val stock: ViewerStock)

/** 화면들이 쓸 그림 말: 도면 · 경로(채운 화살) · 급송(점선 · 빈 화살) · T 점 · 노드 · 진입 · 외경 소재 */
@Composable
private fun partsOf(s: SampleShape): Parts {
    val c = AppTheme.colors
    val colors = listOf(c.viewLabelT1, c.viewLabelT2, c.indigo)
    return Parts(
        listOf(
            ViewerLine(s.drawing, c.drawProfile, 2.5.dp),
            ViewerLine(Contour.polyline(s.entry, s.program.segs.first().start), c.viewRapid, 2.dp, dotted = true, arrows = Arrow.Hollow),
            ViewerLine(s.program, c.viewPath, 2.dp, arrows = Arrow.Filled),
        ),
        s.points.mapIndexed { i, p -> ViewerMark(p, colors[i], "T${i + 1}") } +
            s.program.segs.map { ViewerMark(it.end, c.viewPath, kind = MarkKind.Dot) } +
            ViewerMark(s.entry, c.viewRapid, "진입", MarkKind.Square),
        listOf(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.viewPath, "직접 보정 경로"),
            LegendItem(c.viewRapid, "급송 G00", LegendGlyph.Dotted),
            LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
        ),
        ViewerStock(s.drawing, inner = false),
    )
}

@Composable
private fun Sample(s: SampleShape, state: ViewerState = remember { ViewerState() }) {
    val p = partsOf(s)
    ShapeViewerCard(p.lines, p.marks, p.legend, subtitle = s.subtitle, stock = p.stock, state = state)
}

/** 끝면 → 볼록 R3(G03) → Ø22 → 오목 R5(G02) → Ø32. 노즈 R0.8 중심 경로(끊은 선)와 가상 인선 경로(화살) */
@Composable
private fun ApiOuter(fullLegend: Boolean = false) {
    val c = AppTheme.colors
    val drawing = Contour(
        listOf(
            Line(Pt(0.0, 0.0), Pt(0.0, 8.0)),
            Arc(Pt(0.0, 8.0), Pt(-3.0, 11.0), Pt(-3.0, 8.0), cw = false),
            Line(Pt(-3.0, 11.0), Pt(-15.0, 11.0)),
            Arc(Pt(-15.0, 11.0), Pt(-20.0, 16.0), Pt(-15.0, 16.0), cw = true),
            Line(Pt(-20.0, 16.0), Pt(-30.0, 16.0)),
        ),
    )
    val center = Contour(
        listOf(
            Line(Pt(0.8, 0.0), Pt(0.8, 8.0)),
            Arc(Pt(0.8, 8.0), Pt(-3.0, 11.8), Pt(-3.0, 8.0), cw = false),
            Line(Pt(-3.0, 11.8), Pt(-15.0, 11.8)),
            Arc(Pt(-15.0, 11.8), Pt(-19.2, 16.0), Pt(-15.0, 16.0), cw = true),
            Line(Pt(-19.2, 16.0), Pt(-19.2, 16.8)),
            Line(Pt(-19.2, 16.8), Pt(-30.0, 16.8)),
        ),
    )
    // 가상 인선 = 중심 + (0.8, −0.8)
    val program = Contour(
        center.segs.map { s ->
            val d = Pt(0.8, -0.8)
            if (s is Arc) Arc(s.start + d, s.end + d, s.center + d, s.cw) else Line(s.start + d, s.end + d)
        },
    )
    val entry = Pt(3.0, -0.8)
    val exit = Pt(3.0, 19.0)
    val legend = listOf(
        LegendItem(c.drawProfile, "도면"),
        LegendItem(c.viewNose, "노즈 중심", LegendGlyph.Dashed),
        LegendItem(c.viewNose, "노즈 R0.8 · 가상 인선 3번", LegendGlyph.Circle),
        LegendItem(c.viewPath, "직접 보정(G40) 경로"),
        LegendItem(c.viewRapid, "급송 G00", LegendGlyph.Dotted),
        LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
    ) + if (fullLegend) {
        listOf(
            LegendItem(c.viewLabelT1, "T 점", LegendGlyph.Ring),
            LegendItem(c.viewPath, "노드", LegendGlyph.Dot),
            LegendItem(c.viewRapid, "진입 · 후퇴", LegendGlyph.Square),
            LegendItem(c.simArc, "원호 중심", LegendGlyph.Cross),
        )
    } else {
        emptyList()
    }
    ShapeViewerCard(
        listOf(
            ViewerLine(drawing, c.drawProfile, 2.5.dp),
            ViewerLine(center, c.viewNose, 1.4.dp, dashed = true),
            ViewerLine(circle(Pt(-3.0, 11.8), 0.8), c.viewNose, 1.2.dp, fill = c.viewNose.copy(alpha = .10f)),
            ViewerLine(circle(Pt(-15.0, 11.8), 0.8), c.viewNose, 1.2.dp, fill = c.viewNose.copy(alpha = .10f)),
            ViewerLine(Contour.polyline(entry, program.segs.first().start), c.viewRapid, 2.dp, dotted = true, arrows = Arrow.Hollow),
            ViewerLine(program, c.viewPath, 2.dp, arrows = Arrow.Filled),
            ViewerLine(Contour.polyline(program.segs.last().end, Pt(-29.2, 19.0), exit), c.viewRapid, 2.dp, dotted = true, arrows = Arrow.Hollow),
        ),
        listOf(
            ViewerMark(Pt(-3.0, 11.0), c.viewLabelT1, "T1"),
            ViewerMark(Pt(-15.0, 11.0), c.viewLabelT2, "T2"),
        ) + program.segs.map { ViewerMark(it.end, c.viewPath, kind = MarkKind.Dot) } + listOf(
            ViewerMark(entry, c.viewRapid, "진입", MarkKind.Square),
            ViewerMark(exit, c.viewRapid, "후퇴", MarkKind.Square),
            ViewerMark(Pt(-3.0, 8.0), c.simArc, "R3.000 → 3.800 G03", MarkKind.Cross),
            ViewerMark(Pt(-15.0, 16.0), c.simArc, "R5.000 → 4.200 G02", MarkKind.Cross),
        ),
        legend,
        subtitle = "외경 · 노즈 R0.8 · 가상 인선 3번",
        stock = ViewerStock(drawing, inner = false),
        // 끝면에서 깎아 낼 1mm(면)
        fills = listOf(ViewerFill(Contour.polyline(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 17.0), Pt(0.0, 17.0), Pt(0.0, 0.0)), c.amber.copy(alpha = .16f))),
    )
}

/** 내경: 모따기 → Ø20 구멍 → 단차 Ø14. 소재는 구멍 위(뷰어 위 끝까지) */
@Composable
private fun ApiInner() {
    val c = AppTheme.colors
    val drawing = Contour.polyline(Pt(0.0, 12.0), Pt(-2.0, 10.0), Pt(-18.0, 10.0), Pt(-18.0, 7.0), Pt(-30.0, 7.0))
    val program = Contour.polyline(Pt(0.4, 12.0), Pt(-1.83, 9.6), Pt(-17.6, 9.6), Pt(-17.6, 6.6), Pt(-30.0, 6.6))
    val entry = Pt(2.0, 12.0)
    ShapeViewerCard(
        listOf(
            ViewerLine(drawing, c.drawProfile, 2.5.dp),
            ViewerLine(Contour.polyline(entry, program.segs.first().start), c.viewRapid, 2.dp, dotted = true, arrows = Arrow.Hollow),
            ViewerLine(program, c.viewPath, 2.dp, arrows = Arrow.Filled),
        ),
        listOf(
            ViewerMark(Pt(-2.0, 10.0), c.viewLabelT1, "T1"),
            ViewerMark(Pt(-18.0, 10.0), c.viewLabelT2, "T2"),
            ViewerMark(entry, c.viewRapid, "진입", MarkKind.Square),
        ) + program.segs.map { ViewerMark(it.end, c.viewPath, kind = MarkKind.Dot) },
        listOf(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.viewPath, "직접 보정(G40) 경로"),
            LegendItem(c.viewRapid, "급송 G00", LegendGlyph.Dotted),
            LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
        ),
        subtitle = "내경 · 노즈 R0.4 · 가상 인선 2번",
        stock = ViewerStock(drawing, inner = true),
    )
}

/** 짧은 모서리가 이어진 윤곽: 노드 번호 아홉 · T 점 둘(노드와 겹침) · 가장자리의 진입 · 후퇴 · 끝 · 긴 R 알약 */
@Composable
private fun Crowded() {
    val c = AppTheme.colors
    val pts = listOf(
        Pt(0.0, 0.0), Pt(0.0, 6.0), Pt(-1.0, 7.0), Pt(-1.5, 7.6), Pt(-2.0, 8.0), Pt(-6.0, 8.0),
        Pt(-6.0, 9.0), Pt(-6.6, 9.5), Pt(-7.0, 10.0), Pt(-14.0, 10.0),
    )
    val drawing = Contour.polyline(*pts.toTypedArray())
    ShapeViewerCard(
        listOf(ViewerLine(drawing, c.drawProfile, 2.5.dp), ViewerLine(drawing, c.viewPath, 1.6.dp, arrows = Arrow.Filled)),
        listOf(
            ViewerMark(pts[1], c.viewLabelT1, "T1"),
            ViewerMark(pts[5], c.viewLabelT2, "T2"),
        ) + pts.drop(1).mapIndexed { i, p -> ViewerMark(p, c.viewPath, "${i + 1}", MarkKind.Dot) } + listOf(
            ViewerMark(Pt(1.5, 0.0), c.viewRapid, "진입", MarkKind.Square),
            ViewerMark(Pt(1.5, 10.5), c.viewRapid, "후퇴", MarkKind.Square),
            ViewerMark(Pt(-14.0, 10.0), c.textBody, "끝"),
            ViewerMark(Pt(-10.0, 10.0), c.simArc, "R2.000 → 2.400 G02", MarkKind.Cross),
        ),
        listOf(LegendItem(c.drawProfile, "도면"), LegendItem(c.viewPath, "노드", LegendGlyph.Dot)),
        subtitle = "라벨이 몰린 곳",
    )
}
