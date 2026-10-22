package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
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
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.HolderHit
import com.bmt.nosecalc.calc.HolderPart
import com.bmt.nosecalc.calc.HolderResult
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.screens.taper.circle
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.Arrow
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.LabeledValue
import com.bmt.nosecalc.ui.LegendGlyph
import com.bmt.nosecalc.ui.LegendItem
import com.bmt.nosecalc.ui.MarkKind
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.ShapeViewerCard
import com.bmt.nosecalc.ui.ViewerFill
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.ViewerStock
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.onFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 수동(3점호) 그림: 볼록 원호 위 세 점 P1(시작) · P2 · P3(끝), 중심과 R, 원호 바깥의 가공 방향 호(화살), 왼쪽 아래 X · Z 축.
 * inner = 위아래(내경), reverse = 좌우(역방향)를 뒤집은 모양 — 글자는 뒤집지 않는다. 뒤집은 그림은 같은 축에서의 모양이라
 * 축 표시는 그대로(Z → 오른쪽, X ↑ 위)다. withText = 오른쪽에 설명(고르기 화면의 넓은 카드 · 입력 화면의 세 점 카드).
 * focus = 키패드 그림: 그 점(1~3) 하나만 초점색 · 굵은 테 · 알약 이름으로, 다른 점은 옅게
 */
@Composable
internal fun ManualArt(withText: Boolean, inner: Boolean = false, reverse: Boolean = false, focus: Int? = null, modifier: Modifier = Modifier.fillMaxSize()) {
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
            // 초점 이름: 13sp 이상 진한 글, 초점색 α .16 을 카드에 섞은 알약 위(RoundTypeArt 의 초점과 같다)
            val pill = c.lineFocus.copy(alpha = .16f).compositeOver(c.card)
            fun label(t: String, at: Offset, color: Color, focused: Boolean = false) {
                if (focused) {
                    haloText(measurer, AnnotatedString(t), style.copy(color = c.textValue, fontSize = maxOf(13f, 11.5f * k).sp), pill, 1f, center = at, pill = true)
                    return
                }
                val l = measurer.measure(t, style.copy(color = color))
                drawText(l, topLeft = at - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            listOf(Triple(p1, "P1", c.drawT1), Triple(p2, "P2", c.drawT2), Triple(p3, "P3", c.indigo)).forEachIndexed { i, (p, t, own) ->
                val on = focus == i + 1
                val col = when {
                    on -> c.lineFocus
                    focus != null -> own.copy(alpha = .45f)
                    else -> own
                }
                drawCircle(c.card, d(if (on) 4.4f else 3.6f), p)
                drawCircle(col, d(if (on) 4.4f else 3.6f), p, style = Stroke(d(if (on) 2.2f else 1.6f)))
                val out = (p - o) / r
                label(t, p + out * d(if (on) 15f else 13f), col, on)
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

/** 입력 화면 맨 위(글만): 라운드 R · 형상 번호와 짧은 이름(inner = 내경 이름) · 방향 · 노즈 R · 팁 + 다시 고르기. 그림은 아래 치수 카드에 크게 */
@Composable
internal fun RoundSummary(type: Int, info: String, inner: Boolean = false, onBack: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("라운드 R", color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${if (type == MANUAL) "수동" else "형상 $type"} · ${roundTypeShort(type, inner)}",
                    color = c.textStrong, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                )
                Text(info, color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            PillButton("다시 고르기", null, onBack)
        }
    }
}

/**
 * 노드 표: 요소 끝점마다 한 덩어리, 가공 순서. 윗줄은 지금 보정 방식의 X · Z(직접 보정 = 합성 R 보정 좌표 valueComp, 기계 보정 = 도면 좌표 textValue)를
 * 크게(numLarge), 아랫줄은 다른 쪽 값을 작게 흐리게('도면 X… Z…' / '보정 X… Z…'). 머리 줄(X (직경) · Z)은 한 번, 왼쪽 끝에 큰 값의 이름.
 * 점 사이 줄에 그 요소의 G01 / G02 · G03 과 R(도면 → 보정)
 */
@Composable
internal fun NodeTable(sol: RoundSolution, comp: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val main = if (comp) c.valueComp else c.textValue
    AppCard(modifier.fillMaxWidth(), padding = 14.dp) {
        CardHeader("노드 좌표", "가공 순서 · X 직경 · 팁 ${sol.tip}번")
        Row(Modifier.padding(bottom = 4.dp)) {
            Text(if (comp) "보정" else "도면", Modifier.width(POINT_COL), color = main, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            listOf("X (직경)", "Z").forEach {
                Text(it, Modifier.weight(1f), color = c.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.lineField))
        val arcs = sol.arcs.associateBy { it.index }
        sol.nodes.forEachIndexed { i, n ->
            val (big, small) = if (comp) n.prog to n.drawn else n.drawn to n.prog
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("P${nodeNo(sol, i)}", Modifier.width(POINT_COL), color = c.textStrong, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    listOf(big.xDia, big.z).forEach {
                        Text(Fmt.fixed3(it), Modifier.weight(1f), style = AppType.numLarge, color = main, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
                Text(
                    buildAnnotatedString {
                        // 이름은 한글 글꼴로(고정폭 글꼴에 한글이 없다)
                        withStyle(SpanStyle(fontFamily = AppTheme.sans, fontWeight = FontWeight.SemiBold)) { append(if (comp) "도면 " else "보정 ") }
                        append("X${Fmt.fixed3(small.xDia)}  Z${Fmt.fixed3(small.z)}")
                    },
                    Modifier.fillMaxWidth(),
                    style = AppType.numSmall,
                    color = c.textMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
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
                    fontSize = 13.sp,
                    fontFamily = AppTheme.mono,
                )
            }
        }
        Box(Modifier.padding(top = 6.dp, bottom = 8.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
        if (comp) {
            LabeledValue("진입점 (G00)", "X${Fmt.fixed3(sol.entry.xDia)}  Z${Fmt.fixed3(sol.entry.z)}")
        } else {
            // 어절 안에서 줄이 바뀌지 않게(큰 글꼴에서 '윤곽입니 / 다')
            Text(
                keepWords("도면 점 · 원호 R 은 모서리 R 을 접하게 끼운 윤곽입니다(접점은 직선 위 수선의 발, 원 위에서는 두 중심을 잇는 선 위)."),
                color = c.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

private val POINT_COL = 34.dp

/** 노드 번호. 3점호(filled == null)는 노드가 시작 · 끝 둘뿐이라 입력 칸 P1 · P3 와 이름을 맞춘다 */
internal fun nodeNo(sol: RoundSolution, i: Int) = if (sol.filled == null && i == sol.nodes.lastIndex) 3 else i + 1

/** 형상 확인에 그릴 보링바: 바 · 검사 결과(건너뛰지 않은 것) · 도면 시작이 입구 끝면인가 · 닿는 곳의 NC 줄 번호(1부터, 지금 카드 그대로) */
internal class HolderShow(val bar: BoringBar, val result: HolderResult, val faceAtStart: Boolean, val lineNo: (HolderHit) -> Int)

/**
 * 형상 확인. 소재(빗금) 위에 도면(3.5dp), 그 위에 NC 의 프로그램 경로(1.8dp, 진행 화살). 진입점 □ 에서 첫 점까지는 NC 처럼 이송(G01)이라
 * 경로에 넣는다 — 라운드 NC 는 G00 으로 진입점에 온 뒤 끝까지 이송이라 그릴 급송 토막이 없다.
 * 기계 보정(comp = false) = 도면 좌표 프로그램(drawn, 노즈R 0 으로 푼 것) · 접점 ○ 번호. 직접 보정(comp) = 꺾임점의 노즈 원(옅게 칠함)과 가상 인선 꺾쇠 ·
 * 노즈 중심(끊은 선) · 가상 인선 프로그램 · 보정점 • 번호. 원호 중심은 + 와 원호 가운데까지 끊은 지시선, 'R10.000 G02'(직접 보정은 합성 R
 * 'R10.000 → 10.400 G02') 알약 — 화면 반지름이 14dp 보다 작으면 뷰어가 뺀다. inner = 소재가 구멍 쪽(내경).
 * holder(내경 보링바)가 있으면 지름 전체 단면(맞은편 벽 · 1dp 뒤집은 도면)에 고른 자리의 바 · 인서트 단면(초록, 입구 끝면 너머는 자름),
 * 겹친 곳(빨강 α .5), 닿는 경로 구간(빨강 3dp, 경로 밑), 처음 닿는 곳 ✕ '간섭 n줄'. 카드 아래 알약으로 자리를 고른다
 */
@Composable
internal fun RoundViewer(sol: RoundSolution, comp: Boolean, r: Double, drawn: RoundSolution?, inner: Boolean, holder: HolderShow? = null) {
    val c = AppTheme.colors
    val path = if (comp) sol else drawn
    val nose = comp && r > 0
    val hits = holder?.result?.hits.orEmpty()
    // 보링바를 그릴 자리(이름 → 노즈 중심). 처음은 첫 간섭, 닿지 않으면 가장 좁은 곳
    val poses = holder?.let { holderPoses(it.result, sol, r) }.orEmpty()
    var pick by remember(holder?.result) { mutableStateOf(0) }
    val at = poses.getOrNull(pick)?.second
    // 바는 입구 끝면 너머 max(3, 깊이 15 %) 에서 자른다 — 뷰어가 바 끝까지 맞추지 않게. 겹친 곳도 같은 곳에서
    val zs = sol.drawing.segs.flatMap { listOf(it.start.z, it.end.z) }
    val maxZ = zs.max() + max(3.0, 0.15 * (zs.max() - zs.min()))
    val lines = buildList {
        add(ViewerLine(sol.drawing, c.drawProfile, 3.5.dp))
        if (holder != null) {
            add(ViewerLine(mirrored(sol.drawing), c.drawProfile, 1.dp))
            if (path != null) for (h in hits) contactBand(path, h, comp)?.let { add(ViewerLine(it, c.red, 3.dp)) }
        }
        if (nose) {
            val tv = NoseComp.tipVector(sol.tip, r)
            for (o in (listOf(sol.center.segs.first().start) + sol.center.segs.map { it.end }).distinct()) {
                add(ViewerLine(circle(o, r), c.viewNose, 1.2.dp, fill = c.viewNose.copy(alpha = .10f)))
                // 가상 인선 꺾쇠: 노즈 원에 Z · X 방향으로 접하는 두 선이 가상 인선에서 만난다(팁 1~4)
                if (tv.z != 0.0 && tv.x != 0.0) add(ViewerLine(Contour.polyline(o + Pt(tv.z, 0.0), o + tv, o + Pt(0.0, tv.x)), c.viewNose.copy(alpha = .8f), 1.2.dp))
            }
            add(ViewerLine(sol.center, c.viewNose, 1.4.dp, dashed = true))
        }
        if (path != null) add(ViewerLine(Contour(listOf(Line(path.entry, path.program.segs.first().start)) + path.program.segs), c.viewPath, 1.8.dp, arrows = Arrow.Filled))
        if (holder != null && at != null) {
            for (part in HolderCheck.outline(holder.bar, r, at, maxZ, backBoring(sol))) add(ViewerLine(part, c.drawHolder, 1.6.dp, fill = c.drawHolderFill))
        }
    }
    // 겹친 곳은 띠마다 소재를 다시 재므로 자리 · 바가 바뀔 때만(키패드가 열리고 닫힐 때 다시 세지 않게)
    val overlap = remember(sol.drawing, holder?.bar, holder?.faceAtStart, r, at) {
        if (holder != null && at != null) HolderCheck.overlap(sol.drawing, holder.faceAtStart, holder.bar, r, at, maxZ) else emptyList()
    }.map { ViewerFill(it, c.red.copy(alpha = .5f)) }
    // 처음 닿는 곳 ✕ — 줄마다 하나(바 · 인서트 · 공구대 먼저, 최소 가공경만 걸린 줄은 'Dmin')
    val contacts = if (holder == null) emptyList() else hits.sortedBy { it.part == HolderPart.MIN_BORE }.distinctBy { holder.lineNo(it) }.map { h ->
        val name = if (h.part == HolderPart.MIN_BORE) "Dmin" else "간섭"
        ViewerMark(hitPoint(h, path, comp), c.red, "$name ${holder.lineNo(h)}줄", MarkKind.Cross)
    }
    val points = if (comp) {
        sol.nodes.mapIndexed { i, n -> ViewerMark(n.prog, c.viewPath, "${nodeNo(sol, i)}", MarkKind.Dot) }
    } else {
        sol.nodes.mapIndexed { i, n -> ViewerMark(n.drawn, c.viewLabelT1, "${nodeNo(sol, i)}") }
    }
    val centers = sol.arcs.map { a ->
        val g = if (a.cw) "G02" else "G03"
        val text = if (!comp) "R${Fmt.fixed3(a.r)} $g"
        else a.progR?.let { "R${Fmt.fixed3(a.r)} → ${Fmt.fixed3(it)} $g" } ?: "R${Fmt.fixed3(a.r)} $g · 보정 R 없음"
        ViewerMark(a.center, c.simArc, text, MarkKind.Cross, arc = sol.drawing.segs[a.index] as Arc)
    }
    val entry = listOfNotNull(path?.let { ViewerMark(it.entry, c.viewRapid, "진입", MarkKind.Square) })
    val holderLegend = if (holder == null) {
        emptyList()
    } else {
        listOfNotNull(LegendItem(c.drawHolderFill, "보링바 단면", LegendGlyph.Fill), LegendItem(c.red.copy(alpha = .5f), "간섭", LegendGlyph.Fill).takeIf { hits.isNotEmpty() })
    }
    val legend = holderLegend + if (comp) {
        listOfNotNull(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.viewNose, "노즈 중심", LegendGlyph.Dashed).takeIf { nose },
            LegendItem(c.viewNose, "노즈 R${Fmt.nc(r).removeSuffix(".")} · 가상 인선 ${sol.tip}번", LegendGlyph.Circle).takeIf { nose },
            LegendItem(c.viewPath, "프로그램 경로"),
            LegendItem(c.viewPath, "보정점 (번호 = 노드 표)", LegendGlyph.Dot),
            // 직접 보정도 원호 중심 + 와 지시선을 그린다(기계 보정 범례와 같게)
            LegendItem(c.simArc, "원호 중심", LegendGlyph.Cross).takeIf { centers.isNotEmpty() },
            LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
        )
    } else {
        listOf(
            LegendItem(c.drawProfile, "도면"),
            LegendItem(c.viewPath, "프로그램 경로"),
            LegendItem(c.viewLabelT1, "접점 (번호 = 노드 표)", LegendGlyph.Ring),
            LegendItem(c.simArc, "원호 중심", LegendGlyph.Cross),
            LegendItem(c.drawHatch, "소재", LegendGlyph.Fill),
        )
    }
    ShapeViewerCard(
        lines, points + centers + entry + contacts, legend,
        subtitle = if (comp) "직접 보정(G40) · 합성 R 궤적 · 팁 ${sol.tip}번" else "기계 보정(${sol.side}) · 도면 좌표",
        stock = ViewerStock(sol.drawing, inner, mirror = holder != null),
        fills = overlap,
        footer = if (poses.isEmpty()) null else ({ PosePicker(poses.map { it.first }, pick) { pick = it } }),
    )
}

/**
 * 보링바를 그릴 자리 → 노즈 중심: 첫 간섭(바 · 인서트 · 공구대 — 없으면 최소 가공경) · 가장 깊은 곳 · 가장 좁은 곳(바와 벽) ·
 * 노드 P1…Pn(그 점에 선 공구)
 */
private fun holderPoses(res: HolderResult, sol: RoundSolution, r: Double): List<Pair<String, Pt>> {
    val real = res.hits.filter { it.part != HolderPart.MIN_BORE }.ifEmpty { res.hits }
    val tv = NoseComp.tipVector(sol.tip, r)
    return listOfNotNull(
        real.firstOrNull()?.let { "첫 간섭" to it.first.nose },
        real.maxByOrNull { it.depth }?.let { "가장 깊은 곳" to it.worst.nose },
        res.barClearAt?.let { "가장 좁은 곳" to it.nose },
    ) + sol.nodes.mapIndexed { i, n -> "P${nodeNo(sol, i)}" to n.prog - tv }
}

/**
 * 닿는 구간: 보이는 경로(path)의 그 요소에서 처음 · 마지막 닿는 곳 사이 — comp(직접 보정)는 가상 인선 점, 기계 보정은 노즈가 닿는 도면 점
 * (✕ 표와 같은 곳. 도면 좌표 경로의 요소는 보정 경로와 길이가 달라 길이 비율로는 어긋난다). 진입 이동(−1)은 길이 비율로, 진입점에 선 자세(−2)는 점이라 없다
 */
internal fun contactBand(path: RoundSolution, h: HolderHit, comp: Boolean): Contour? {
    if (h.element < 0) {
        if (h.element == -2) return null
        val a = path.entry
        val d = path.program.segs.first().start - a
        return Contour.polyline(a + d * h.fromT, a + d * h.toT)
    }
    val s = path.program.segs.getOrNull(h.element) ?: return null
    val a = if (comp) h.first.tip else h.first.drawn
    val b = if (comp) h.last.tip else h.last.drawn
    return Contour(listOf(if (s is Arc) Arc(a, b, s.center, s.cw) else Line(a, b)))
}

/** 축(X0)에 뒤집은 윤곽(맞은편 벽) */
private fun mirrored(c: Contour) = Contour(
    c.segs.map {
        when (it) {
            is Line -> Line(Pt(it.start.z, -it.start.x), Pt(it.end.z, -it.end.x))
            is Arc -> Arc(Pt(it.start.z, -it.start.x), Pt(it.end.z, -it.end.x), Pt(it.center.z, -it.center.x), !it.cw)
        }
    },
)

/** 보링바 자리 고르기(형상 확인 카드의 단추 줄 아래): 이름 줄 + 가로로 미는 알약(40dp, 고른 것은 청록) */
@Composable
private fun PosePicker(names: List<String>, picked: Int, onPick: (Int) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
        Text("보링바를 그릴 자리", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { i, name ->
                val on = i == picked
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .height(40.dp)
                        .clip(shape)
                        .background(if (on) c.teal else c.field)
                        .border(1.5.dp, if (on) c.teal else c.lineField, shape)
                        .clickable { onPick(i) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, color = if (on) c.onFill(c.teal) else c.textBody, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** 원호 하나의 요약(수동 모드): 방향 · R(도면 → 보정) · 중심 · 각 */
@Composable
internal fun ArcCard(sol: RoundSolution) {
    val c = AppTheme.colors
    val a = sol.arcs.firstOrNull() ?: return
    val seg = sol.drawing.segs[a.index] as Arc
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        CardHeader("원호")
        Row {
            LabeledValue("방향", "${if (a.cw) "G02" else "G03"} ${if (a.cw) "시계" else "반시계"}", Modifier.weight(1f), c.simArc)
            LabeledValue("원호 각", "${Fmt.fixed3(seg.sweepDeg)}°", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            LabeledValue("도면 R", Fmt.fixed3(a.r), Modifier.weight(1f))
            LabeledValue("보정 R (합성)", a.progR?.let { Fmt.fixed3(it) } ?: "없음", Modifier.weight(1f), c.valueComp)
        }
        Spacer(Modifier.height(8.dp))
        LabeledValue("중심 (X 직경 · Z)", "X${Fmt.fixed3(a.center.xDia)}  Z${Fmt.fixed3(a.center.z)}", Modifier.fillMaxWidth())
    }
}
