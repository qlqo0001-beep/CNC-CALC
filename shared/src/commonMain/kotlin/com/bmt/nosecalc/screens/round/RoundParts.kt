package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.screens.taper.Legend
import com.bmt.nosecalc.screens.taper.ViewerCard
import com.bmt.nosecalc.screens.taper.circle
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 04 의 TYPE 카드: 머리(칩) + 1dp 구분선 + 흰 몸통(그림). 칩은 홀수 하늘 · 짝수 민트(sky).
 * 테두리 2dp(고르면 청록 3dp), 모서리 10dp. 머리 높이·칩 자리는 04 를 잰 값(카드 폭 193dp 기준).
 */
@Composable
internal fun TypeCard(
    chip: String,
    sky: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    aspectRatio: Float,
    body: @Composable BoxScope.() -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .aspectRatio(aspectRatio)
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(c.card)
            .border(if (selected) 3.dp else 2.dp, if (selected) c.teal else c.lineStrong, shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(36.5.dp).background(c.subtle).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            TypeChip(chip, sky)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
        Box(Modifier.fillMaxWidth().weight(1f), content = body)
    }
}

@Composable
private fun TypeChip(text: String, sky: Boolean) {
    val c = AppTheme.colors
    val rc = if (sky) c.chipSky else c.chipMint
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .height(21.dp)
            .shadow(if (c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(rc.bg)
            .border(1.dp, rc.border, shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = rc.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em, maxLines = 1)
    }
}

/**
 * 수동(3점호) 그림: 어깨의 볼록 원호 위 세 점 P1(시작) · P2 · P3(끝), 중심과 R, 가공 방향 화살.
 * withText = 오른쪽에 설명(고르기 화면의 넓은 카드).
 */
@Composable
internal fun ManualArt(withText: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(if (withText) Modifier.fillMaxSize().weight(1f) else Modifier.fillMaxSize()) {
            val k = size.minDimension / 130.dp.toPx()
            fun d(v: Float) = (v * k).dp.toPx()
            val r = d(62f)
            // 중심은 왼쪽 아래, 원호는 오른쪽 아래(P1) → 왼쪽 위(P3) 로 반시계(G03, 볼록)
            val o = Offset(size.width / 2 - r * .42f, size.height / 2 + r * .52f)
            fun on(deg: Double) = o + Offset((r * cos(deg * PI / 180)).toFloat(), (-r * sin(deg * PI / 180)).toFloat())
            val p1 = on(0.0)
            val p2 = on(45.0)
            val p3 = on(90.0)
            val dash = PathEffect.dashPathEffect(floatArrayOf(d(4f), d(3f)))
            // 이어지는 도면(앞 면 · 뒤 외경)은 옅게
            drawLine(c.lineStrong, p1, p1 + Offset(0f, d(22f)), d(1.6f))
            drawLine(c.lineStrong, p3, p3 - Offset(d(30f), 0f), d(1.6f))
            drawArc(c.drawProfile, 0f, -90f, false, o - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(d(2.4f)))
            drawLine(c.drawRef, o, p2, d(1.1f), pathEffect = dash)
            drawLine(c.drawRef, o, p1, d(0.8f), pathEffect = dash)
            drawLine(c.drawRef, o, p3, d(0.8f), pathEffect = dash)
            drawCircle(c.drawRef, d(2.4f), o)
            // 가공 방향 화살(P1 → P3)
            val a = on(20.0) + (on(20.0) - o) * .18f
            val b = on(32.0) + (on(32.0) - o) * .18f
            val ad = b - a
            drawLine(c.green, on(8.0) + (on(8.0) - o) * .18f, b, d(1.4f))
            val u = ad / ad.getDistance()
            val n = Offset(-u.y, u.x)
            drawPath(
                Path().apply {
                    moveTo(b.x + u.x * d(4f), b.y + u.y * d(4f))
                    lineTo(b.x + n.x * d(3f), b.y + n.y * d(3f))
                    lineTo(b.x - n.x * d(3f), b.y - n.y * d(3f))
                    close()
                },
                c.green,
            )
            val style = TextStyle(fontFamily = sans, fontSize = (11.5f * k).sp, fontWeight = FontWeight.Bold)
            fun label(t: String, at: Offset, color: Color) {
                val l = measurer.measure(t, style.copy(color = color))
                drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            listOf(Triple(p1, "P1", c.drawT1), Triple(p2, "P2", c.drawT2), Triple(p3, "P3", c.red)).forEach { (p, t, col) ->
                drawCircle(c.card, d(3.6f), p)
                drawCircle(col, d(3.6f), p, style = Stroke(d(1.6f)))
                val out = (p - o) / r
                label(t, p + out * d(13f), col)
            }
            label("R", o + (p2 - o) * .5f + Offset(d(7f), d(7f)), c.drawAngle)
        }
        if (withText) {
            Column(Modifier.weight(1.25f).padding(end = 14.dp)) {
                Text("CAD 3점호", color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "원호 위 세 점(시작 → 가운데 → 끝)을 넣으면 중심 · R · G02/G03 을 구해 같은 노즈R 보정을 합니다. 외경 · 내경, 정 · 역방향 모두.",
                    color = c.textBody, fontSize = 12.5.sp, lineHeight = 18.sp,
                )
            }
        }
    }
}

/** 입력 화면 맨 위: 고른 TYPE 의 작은 그림 + TYPE · 이름 · 방향/팁 + 다시 고르기 */
@Composable
internal fun RoundSummary(type: Int, info: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(8.dp)
            Box(Modifier.width(132.dp).aspectRatio(193f / 189f).clip(shape).background(c.card).border(1.5.dp, c.lineStrong, shape)) {
                if (type == MANUAL) ManualArt(withText = false) else RoundTypeArt(type)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("라운드 R", color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (type == MANUAL) "수동 (3점호)" else "형상 $type",
                    color = c.textStrong, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em,
                )
                Text(roundTypeName(type), color = c.textBody, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(info, color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                PillButton("형상 다시 고르기", null, onBack)
            }
        }
    }
}

/** 모드: 접점(도면 윤곽) / 합성 R 궤적(보정). 같은 계산의 두 보기 */
@Composable
internal fun ModeCard(comp: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 10.dp) {
        Segmented(listOf("접점 모드", "합성 R 궤적"), if (comp) 1 else 0, { onChange(it == 1) }, Modifier.fillMaxWidth(), color = c.teal, height = 44.dp)
        Text(
            if (comp) "도면 R ± 노즈R(볼록 R + r · 오목 R − r) 궤적과 가상 인선 좌표 — 보정(G41/G42) 없이 바로 깎을 때"
            else "직선 · 원호가 접하는 점(도면 윤곽) — 기계의 노즈R 보정(G42)으로 깎을 때",
            Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            color = c.textMuted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
    }
}

/**
 * 노드 표: 요소 끝점마다 도면 X · Z(접점)와 보정 X · Z(가상 인선 = 프로그램 좌표), 가공 순서.
 * 점 사이 줄에 그 요소의 G01 / G02 · G03 과 R(도면 → 보정). 지금 모드의 열을 진하게 보인다.
 */
@Composable
internal fun NodeTable(sol: RoundSolution, comp: Boolean) {
    val c = AppTheme.colors
    val drawnColor = if (comp) c.textMuted else c.textValue
    val progColor = if (comp) c.valueComp else c.textMuted
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("노드 좌표", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("가공 순서 · X 직경 · ${sol.side} · 팁 T${sol.tip}", color = c.textMuted, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Spacer(Modifier.width(POINT_COL))
            Head("도면 (접점)", !comp, Modifier.weight(2f))
            Head("보정 (가상 인선)", comp, Modifier.weight(2f))
        }
        Row(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
            Text("점", Modifier.width(POINT_COL), color = c.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            listOf("X" to !comp, "Z" to !comp, "X" to comp, "Z" to comp).forEach { (t, on) ->
                Text(
                    t, Modifier.weight(1f), color = if (on) c.textStrong else c.textMuted,
                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.lineField))
        val arcs = sol.arcs.associateBy { it.index }
        sol.nodes.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("P${i + 1}", Modifier.width(POINT_COL), color = c.textStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Mono(Fmt.fixed3(n.drawn.xDia), drawnColor, Modifier.weight(1f))
                Mono(Fmt.fixed3(n.drawn.z), drawnColor, Modifier.weight(1f))
                Mono(Fmt.fixed3(n.prog.xDia), progColor, Modifier.weight(1f))
                Mono(Fmt.fixed3(n.prog.z), progColor, Modifier.weight(1f))
            }
            if (i < sol.drawing.segs.size) {
                val a = arcs[i]
                Text(
                    buildAnnotatedString {
                        append("↓ ")
                        if (a == null) {
                            withStyle(SpanStyle(color = c.textMuted, fontWeight = FontWeight.Bold)) { append("G01") }
                            append("  직선")
                        } else {
                            withStyle(SpanStyle(color = c.simArc, fontWeight = FontWeight.Bold)) { append(if (a.cw) "G02" else "G03") }
                            append(if (a.cw) "  시계  " else "  반시계  ")
                            withStyle(SpanStyle(color = if (comp) c.textMuted else c.textValue, fontWeight = FontWeight.Bold)) { append("R${Fmt.fixed3(a.r)}") }
                            append(" → ")
                            withStyle(SpanStyle(color = if (comp) c.valueComp else c.textMuted, fontWeight = FontWeight.Bold)) {
                                append(a.progR?.let { "R${Fmt.fixed3(it)}" } ?: "없음 (R ≤ 노즈R)")
                            }
                        }
                    },
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.subtle).padding(start = POINT_COL, top = 3.dp, bottom = 3.dp),
                    color = c.textBody,
                    fontSize = 12.5.sp,
                    fontFamily = AppTheme.mono,
                )
            }
        }
        Box(Modifier.padding(top = 6.dp, bottom = 8.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
        Text(
            if (comp) "진입점 (G00)  X${Fmt.fixed3(sol.entry.xDia)}  Z${Fmt.fixed3(sol.entry.z)}"
            else "도면 점 · 원호 R 은 모서리 R 을 접하게 끼운 윤곽입니다(접점은 직선 위 수선의 발, 원 위에서는 두 중심을 잇는 선 위).",
            color = if (comp) c.textValue else c.textMuted,
            fontFamily = if (comp) AppTheme.mono else AppTheme.sans,
            fontSize = if (comp) 14.sp else 12.5.sp,
            fontWeight = if (comp) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 18.sp,
        )
    }
}

private val POINT_COL = 34.dp

@Composable
private fun Head(text: String, on: Boolean, modifier: Modifier) {
    val c = AppTheme.colors
    Text(
        text,
        modifier.padding(start = 6.dp).clip(RoundedCornerShape(4.dp)).background(if (on) c.teal.copy(alpha = .12f) else Color.Transparent).padding(vertical = 2.dp),
        color = if (on) c.tealDeep else c.textMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun Mono(text: String, color: Color, modifier: Modifier) {
    Text(text, modifier, color = color, fontFamily = AppTheme.mono, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
}

/**
 * 형상 뷰어. 접점 = 도면 윤곽 + 접점 번호. 합성 R = 도면(가늘게) · 노즈 중심 궤적 · 꺾임점의 노즈 원 · 프로그램(가상 인선) · G00 진입.
 * 프로그램은 진입점에서 바로 첫 보정점으로 가므로 급송은 그 사이만 긋는다.
 */
@Composable
internal fun RoundViewer(sol: RoundSolution, comp: Boolean, r: Double) {
    val c = AppTheme.colors
    if (!comp) {
        ViewerCard(
            listOf(ViewerLine(sol.drawing, c.drawProfile, 2.5.dp)),
            sol.nodes.mapIndexed { i, n -> ViewerMark(n.drawn, c.drawT1, "${i + 1}") } +
                sol.arcs.map { ViewerMark(it.center, c.simArc) },
            listOf(Legend(c.drawProfile, "도면 윤곽"), Legend(c.drawT1, "접점 (번호 = 노드 표)"), Legend(c.simArc, "원호 중심")),
        )
        return
    }
    val centerNodes = (listOf(sol.center.segs.first().start) + sol.center.segs.map { it.end }).distinct()
    val lines = buildList {
        add(ViewerLine(sol.drawing, c.drawProfile, 1.6.dp))
        add(ViewerLine(sol.center, c.sky, 1.4.dp, dashed = true))
        if (r > 0) centerNodes.forEach { add(ViewerLine(circle(it, r), c.sky.copy(alpha = .7f), 1.dp)) }
        add(ViewerLine(Contour.polyline(sol.entry, sol.program.segs.first().start), c.simRapid, 1.6.dp, dashed = true))
        add(ViewerLine(sol.program, c.simToolpath, 2.2.dp))
    }
    val marks = sol.nodes.mapIndexed { i, n -> ViewerMark(n.prog, c.simToolpath, "${i + 1}") } + ViewerMark(sol.entry, c.simRapid, "진입")
    ViewerCard(
        lines, marks,
        listOf(
            Legend(c.drawProfile, "도면"),
            Legend(c.sky, "노즈 중심 (합성 R)", dashed = true),
            Legend(c.simToolpath, "프로그램"),
            Legend(c.simRapid, "급송", dashed = true),
        ),
    )
}

/** 원호 하나의 요약(수동 모드): 방향 · R(도면 → 보정) · 중심 · 각 */
@Composable
internal fun ArcCard(sol: RoundSolution) {
    val c = AppTheme.colors
    val a = sol.arcs.firstOrNull() ?: return
    val seg = sol.drawing.segs[a.index] as Arc
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("원호", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row {
            Stat("방향", "${if (a.cw) "G02" else "G03"} ${if (a.cw) "시계" else "반시계"}", Modifier.weight(1f), c.simArc)
            Stat("원호 각", "${Fmt.fixed3(seg.sweepDeg)}°", Modifier.weight(1f), c.textValue)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Stat("도면 R", Fmt.fixed3(a.r), Modifier.weight(1f), c.textValue)
            Stat("보정 R (합성)", a.progR?.let { Fmt.fixed3(it) } ?: "없음", Modifier.weight(1f), c.valueComp)
        }
        Spacer(Modifier.height(8.dp))
        Stat("중심 (X 직경 · Z)", "X${Fmt.fixed3(a.center.xDia)}  Z${Fmt.fixed3(a.center.z)}", Modifier.fillMaxWidth(), c.textValue)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color) {
    val c = AppTheme.colors
    Column(modifier) {
        Text(label, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = color, fontFamily = AppTheme.mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
