package com.bmt.nosecalc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.screens.round.RoundTypeArt
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TypeArt
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.CoordPair
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.DrawSpec
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.LabeledValue
import com.bmt.nosecalc.ui.ResultDock
import com.bmt.nosecalc.ui.TypeCard
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.artScale
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test
import kotlin.test.assertEquals

/** 기초 부품(아이콘 · 경고 띠 · 카드 부품 · 결과 띠 · 그림 도구)을 라이트/다크로 → build/shots/foundation-*. 좁은 폰 · 큰 글꼴(-w360: 360dp · 글꼴 1.3배)도 같이 본다 */
class FoundationShotsTest {
    @Test
    fun components() {
        Shots.both("foundation-components", heightDp = 1380) { AppHost(it) { FoundationGallery() } }
        Shots.both("foundation-components-w360", heightDp = 1620, widthDp = 360, fontScale = 1.3f) { AppHost(it) { FoundationGallery() } }
        // 360dp 폰(글꼴 그대로)에서 X 100.000 | Z -100.000 이 24sp 그대로 한 줄
        Shots.both("foundation-coord-w360", heightDp = 120, widthDp = 360) {
            AppHost(it) {
                AppCard(Modifier.fillMaxWidth().padding(Space.gutter)) {
                    CoordPair("100.000", "-100.000", AppTheme.colors.valueComp, dx = "-0.469", dz = "0.000")
                }
            }
        }
    }

    @Test
    fun drawKit() {
        Shots.both("foundation-drawkit", heightDp = 560) { AppHost(it) { DrawKitGallery() } }
    }

    @Test
    fun dockOneLine() {
        val t1 = DockRow("T1′", "39.531", "0.000", Color.Unspecified)
        val t2 = DockRow("T2′", "50.000", "-5.234", Color.Unspecified)
        assertEquals("T1′ X39.531 Z0.000 · T2′ X50.000 Z-5.234", DockStatus.Ok(listOf(t1, t2)).oneLine())
        assertEquals("T1′ X39.531 Z0.000 · T2′ X50.000 Z-5.234 · 외 1점", DockStatus.Ok(listOf(t1, t1, t2)).oneLine())
        assertEquals("T1′ X39.531 Z0.000 · 확인 필요 2건", DockStatus.Warn(2, listOf(t1)).oneLine())
        assertEquals("계산 멈춤 — 값이 서로 맞지 않습니다.", DockStatus.Error("값이 서로 맞지 않습니다.").oneLine())
    }
}

@Composable
private fun FoundationGallery() {
    val c = AppTheme.colors
    val icons = with(AppIcons) { listOf(Plus, Minus, Fit, Expand, Copy, Check, Help, Stop, Back, Arc3, ChevronDown) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.gutter, vertical = Space.cardGap),
        verticalArrangement = Arrangement.spacedBy(Space.cardGap),
    ) {
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            Text("아이콘", style = AppType.cardTitle, color = c.textStrong)
            for (size in listOf(20.dp, 16.dp)) {
                Text("${size.value.toInt()}dp", Modifier.padding(top = 12.dp, bottom = 6.dp), style = AppType.caption, color = c.textMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    icons.forEach { Icon(it, null, Modifier.size(size), tint = c.icon) }
                }
            }
        }
        WarningBanner("노즈R 이 경사보다 큽니다. 숫자를 확인하세요.")
        WarningBanner("값이 서로 맞지 않습니다(T2 가 T1 보다 작음).", level = WarnLevel.Error, actions = listOf("주 R 비우기" to {}, "D1 비우기" to {}, "L2 비우기" to {}))
        WarningBanner("값을 넣으면 바로 계산합니다 — X1 · Z1 · X2 · Z2 · 각도 가운데 넷.", level = WarnLevel.Info)
        WarningBanner(
            "바이트 반대쪽 면이 있습니다. 그 면은 이 공구로 깎지 못합니다.",
            level = WarnLevel.Info,
            collapsedText = "바이트 반대쪽 면 있음 (역테이퍼 꼴) — 눌러서 자세히",
        )
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            CardHeader("보정 좌표", "직접 보정(G40) 프로그램 좌표 · 팁 3번") {
                Text("단위 mm · X 직경", style = AppType.caption, color = c.textMuted)
            }
            CoordPair("39.531", "0.000", c.valueComp, dx = "-0.469", dz = "0.000")
            CoordPair("100.000", "-100.000", c.valueComp, Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                LabeledValue("각도 (Z축 기준)", "45.000°", Modifier.weight(1f))
                LabeledValue("G00 진입점", "X39.531 Z2.000", Modifier.weight(1f))
            }
            ExpandableHint(
                "빈칸 하나는 나머지 넷으로 자동 계산합니다 (보통 각도를 비움).",
                "X1 · Z1 · X2 · Z2 · 각도 가운데 넷을 넣으면 나머지 하나를 계산합니다.",
                Modifier.padding(top = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeCard("형상 1", "끝 면취", c.chipMint, false, {}, Modifier.weight(1f), aspectRatio = 199f / 221f) {
                TypeArt(TaperKind.OUTER, 1, false)
            }
            TypeCard("형상 2", "아령 모양 · 이름이 길면 줄임", c.chipSky, true, {}, Modifier.weight(1f), aspectRatio = 199f / 221f) {
                RoundTypeArt(2)
            }
        }
        val rows = listOf(
            DockRow("T1′", "39.531", "0.000", c.viewLabelT1),
            DockRow("T2′", "50.000", "-5.234", c.viewLabelT2),
        )
        Box(Modifier.fillMaxWidth()) { ResultDock(DockStatus.Ok(rows), true) {} }
        Box(Modifier.fillMaxWidth()) { ResultDock(DockStatus.Ok(listOf(rows[0], rows[1], DockRow("T3′", "62.000", "-12.500", c.indigo))), true) {} }
        Box(Modifier.fillMaxWidth()) { ResultDock(DockStatus.Warn(1, rows), true) {} }
        Box(Modifier.fillMaxWidth()) { ResultDock(DockStatus.Error("값이 서로 맞지 않습니다(T2 가 T1 보다 작음)."), true) {} }
    }
}

/** 그림 도구 견본: 같은 그림을 카드 바탕과 옅은 바탕(형상 카드)에 */
@Composable
private fun DrawKitGallery() {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.gutter, vertical = Space.cardGap),
        verticalArrangement = Arrangement.spacedBy(Space.cardGap),
    ) {
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            CardHeader("그림 도구", "카드 바탕")
            DrawKitSample(c.card)
        }
        val shape = RoundedCornerShape(12.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape).padding(Space.cardPad)) {
            CardHeader("그림 도구", "옅은 바탕 (형상 카드)")
            DrawKitSample(c.subtle)
        }
    }
}

/**
 * 반 단면 축: 빗금 소재 + 굵은 윤곽, 축 중심선, 긴 치수(안쪽 화살), 20dp 짧은 치수(바깥 화살 · 이름 오른쪽),
 * 지름 세로 치수, 굵은 선을 가로지르는 halo 글
 */
@Composable
private fun DrawKitSample(bg: Color) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val (ks, kt) = artScale(size.width, 340.dp)
        val axis = size.height - 46.dp.toPx()
        val x0 = 48.dp.toPx()
        val x1 = size.width - 70.dp.toPx()
        val xs = x1 - 20.dp.toPx()
        val big = axis - 100.dp.toPx()
        val small = axis - 70.dp.toPx()
        // 윤곽은 축에서 열린 꺾은선(축 위는 중심선만), 빗금은 축까지 닫은 면
        val outline = Path().apply {
            moveTo(x0, axis)
            lineTo(x0, big)
            lineTo(xs, big)
            lineTo(xs, small)
            lineTo(x1, small)
            lineTo(x1, axis)
        }
        hatchFill(Path().apply { addPath(outline); close() }, c.drawStock, c.drawHatch, ks)
        drawPath(outline, c.drawProfile, style = Stroke(DrawSpec.profile.toPx() * ks, join = StrokeJoin.Miter))
        centerLine(Offset(x0 - 12.dp.toPx(), axis), Offset(x1 + 12.dp.toPx(), axis), c.drawRef, ks)
        val dim = TextStyle(fontFamily = sans, fontSize = (11.5f * kt).sp, fontWeight = FontWeight.Medium, color = c.drawAngle)
        linearDim(Offset(x0, axis), Offset(x1, axis), 32.dp.toPx(), AnnotatedString("L 60"), c.drawAngle, ks, measurer, dim, bg)
        linearDim(Offset(xs, axis), Offset(x1, axis), 14.dp.toPx(), AnnotatedString("L1 2"), c.drawAngle, ks, measurer, dim, bg)
        linearDim(Offset(x0, axis), Offset(x0, big), -22.dp.toPx(), AnnotatedString("Ø40"), c.drawAngle, ks, measurer, dim, bg)
        val label = TextStyle(fontFamily = sans, fontSize = (15f * kt).sp, fontWeight = FontWeight.SemiBold, color = c.drawT1)
        haloText(measurer, AnnotatedString("T1 기준"), label, bg, ks, center = Offset((x0 + xs) / 2, big))
    }
}
