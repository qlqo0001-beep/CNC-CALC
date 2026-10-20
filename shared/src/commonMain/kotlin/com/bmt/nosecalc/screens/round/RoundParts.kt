package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
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
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 수동(3점호) 그림: 볼록 원호 위 세 점 P1(시작) · P2 · P3(끝), 중심과 R, 원호 바깥의 가공 방향 호(화살), 왼쪽 아래 X · Z 축.
 * inner = 위아래(내경), reverse = 좌우(역방향)를 뒤집은 모양 — 글자는 뒤집지 않는다. 뒤집은 그림은 같은 축에서의 모양이라
 * 축 표시는 그대로(Z → 오른쪽, X ↑ 위)다. withText = 오른쪽에 설명(고르기 화면의 넓은 카드 · 입력 화면의 세 점 카드).
 */
@Composable
internal fun ManualArt(withText: Boolean, inner: Boolean = false, reverse: Boolean = false, modifier: Modifier = Modifier.fillMaxSize()) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(if (withText) Modifier.fillMaxSize().weight(1f) else Modifier.fillMaxSize()) {
            val k = size.minDimension / 130.dp.toPx()
            fun d(v: Float) = (v * k).dp.toPx()
            val sx = if (reverse) -1f else 1f
            val sy = if (inner) -1f else 1f
            val r = d(62f)
            // 중심은 왼쪽 아래, 원호는 오른쪽 아래(P1) → 왼쪽 위(P3) 로 반시계(G03, 볼록). 뒤집으면 그림 가운데를 두고 뒤집는다
            val o = Offset(size.width / 2 - sx * r * .42f, size.height / 2 + sy * r * .52f)
            fun on(deg: Double, rr: Float = r) = o + Offset(sx * (rr * cos(deg * PI / 180)).toFloat(), -sy * (rr * sin(deg * PI / 180)).toFloat())
            fun arc(from: Int, to: Int, rr: Float) = Path().apply {
                (from..to).forEachIndexed { i, deg -> on(deg.toDouble(), rr).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            }
            val p1 = on(0.0)
            val p2 = on(45.0)
            val p3 = on(90.0)
            val dash = PathEffect.dashPathEffect(floatArrayOf(d(4f), d(3f)))
            // 이어지는 도면(앞 면 · 뒤 외경)은 옅게
            drawLine(c.lineStrong, p1, p1 + Offset(0f, sy * d(22f)), d(1.6f))
            drawLine(c.lineStrong, p3, p3 - Offset(sx * d(30f), 0f), d(1.6f))
            drawPath(arc(0, 90, r), c.drawProfile, style = Stroke(d(2.4f)))
            drawLine(c.drawRef, o, p2, d(1.1f), pathEffect = dash)
            drawLine(c.drawRef, o, p1, d(0.8f), pathEffect = dash)
            drawLine(c.drawRef, o, p3, d(0.8f), pathEffect = dash)
            drawCircle(c.drawRef, d(2.4f), o)
            // 가공 방향(P1 → P3): 원호 바깥 10dp 의 같은 중심 호 + 끝 화살(뷰어의 프로그램 색)
            val ra = r + d(10f)
            drawPath(arc(12, 38, ra), c.viewPath, style = Stroke(d(1.6f)))
            val end = 38 * PI / 180
            arrowHead(on(38.0, ra), Offset(-sx * sin(end).toFloat(), -sy * cos(end).toFloat()), c.viewPath, d(6f))
            val style = TextStyle(fontFamily = sans, fontSize = (11.5f * k).sp, fontWeight = FontWeight.Bold)
            fun label(t: String, at: Offset, color: Color) {
                val l = measurer.measure(t, style.copy(color = color))
                drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            listOf(Triple(p1, "P1", c.drawT1), Triple(p2, "P2", c.drawT2), Triple(p3, "P3", c.indigo)).forEach { (p, t, col) ->
                drawCircle(c.card, d(3.6f), p)
                drawCircle(col, d(3.6f), p, style = Stroke(d(1.6f)))
                val out = (p - o) / r
                label(t, p + out * d(13f), col)
            }
            label("R", o + (p2 - o) * .5f + Offset(sx * d(7f), sy * d(7f)), c.drawAngle)
            // 축: 왼쪽 아래 Z → · X ↑ (14dp 화살, 글 10.5sp)
            val ax = Offset(10.dp.toPx(), size.height - 10.dp.toPx())
            val axis = TextStyle(fontFamily = sans, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.textMuted)
            for ((dir, name) in listOf(Offset(1f, 0f) to "Z", Offset(0f, -1f) to "X")) {
                val tip = ax + dir * 14.dp.toPx()
                drawLine(c.textMuted, ax, tip, 1.2.dp.toPx())
                arrowHead(tip, dir, c.textMuted, 4.dp.toPx())
                val l = measurer.measure(name, axis)
                val at = tip + dir * (3.dp.toPx() + (if (dir.x > 0) l.size.width else l.size.height) / 2f)
                drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
            }
        }
        if (withText) {
            Column(Modifier.weight(1.25f).padding(end = 14.dp)) {
                Text("CAD 3점호", color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    // 붙임 공백(\u00A0)으로 '·' 앞에서 줄이 바뀌지 않게
                    "원호 위 세 점(시작 → 가운데 → 끝)으로 중심\u00A0· R\u00A0· G02/G03 을 구해 노즈R 보정합니다. " +
                        "외경\u00A0· 내경, 정방향\u00A0· 역방향 모두.",
                    color = c.textBody, fontSize = 12.5.sp, lineHeight = 18.sp,
                )
            }
        }
    }
}

/** 입력 화면 맨 위(글만): 라운드 R · 형상 번호와 짧은 이름 · 방향 · 노즈 R · 팁 + 다시 고르기. 그림은 아래 치수 카드에 크게 */
@Composable
internal fun RoundSummary(type: Int, info: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("라운드 R", color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${if (type == MANUAL) "수동" else "형상 $type"} · ${roundTypeShort(type)}",
                    color = c.textStrong, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                )
                Text(info, color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            PillButton("다시 고르기", null, onBack)
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
            // '깎을 때' 가 쪼개져 줄바꿈되지 않게 낱말 이음(\u2060) · 붙임 공백(\u00A0)
            if (comp) "도면 R ± 노즈R(볼록 R + r · 오목 R − r) 궤적과 가상 인선 좌표 — 보정(G41/G42) 없이 바로 깎\u2060을\u00A0때"
            else "직선 · 원호가 접하는 점(도면 윤곽) — 기계의 노즈R 보정(G42)으로 깎\u2060을\u00A0때",
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
            Text("가공 순서 · X 직경 · ${sol.side} · 팁 ${sol.tip}번", color = c.textMuted, fontSize = 12.5.sp)
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
                Text("P${nodeNo(sol, i)}", Modifier.width(POINT_COL), color = c.textStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

/** 노드 번호. 3점호(filled == null)는 노드가 시작 · 끝 둘뿐이라 입력 칸 P1 · P3 와 이름을 맞춘다 */
private fun nodeNo(sol: RoundSolution, i: Int) = if (sol.filled == null && i == sol.nodes.lastIndex) 3 else i + 1

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
 * 형상 뷰어. 접점 = 도면 윤곽 + 접점 번호. 합성 R = 도면(가늘게) · 노즈 중심 궤적 · 꺾임점의 노즈 원 · 프로그램(가상 인선) · 진입점.
 * NC 는 G00 으로 진입점까지 온 뒤 G01 로 첫 보정점에 가므로, 그 사이는 프로그램(이송) 선으로 긋는다.
 */
@Composable
internal fun RoundViewer(sol: RoundSolution, comp: Boolean, r: Double) {
    val c = AppTheme.colors
    if (!comp) {
        ViewerCard(
            listOf(ViewerLine(sol.drawing, c.drawProfile, 2.5.dp)),
            sol.nodes.mapIndexed { i, n -> ViewerMark(n.drawn, c.drawT1, "${nodeNo(sol, i)}") } +
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
        add(ViewerLine(Contour.polyline(sol.entry, sol.program.segs.first().start), c.simToolpath, 2.2.dp)) // G01 진입 — NC 와 같게 이송
        add(ViewerLine(sol.program, c.simToolpath, 2.2.dp))
    }
    // 진입 글은 급송선 색(simRapid)이면 바탕에 흐리다
    val marks = sol.nodes.mapIndexed { i, n -> ViewerMark(n.prog, c.simToolpath, "${nodeNo(sol, i)}") } + ViewerMark(sol.entry, c.textBody, "진입")
    ViewerCard(
        lines, marks,
        listOf(
            Legend(c.drawProfile, "도면"),
            Legend(c.sky, "노즈 중심 (합성 R)", dashed = true),
            Legend(c.simToolpath, "프로그램"),
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
