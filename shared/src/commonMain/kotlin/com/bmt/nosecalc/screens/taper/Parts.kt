package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.NumberPad
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.ShapeViewer
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import com.bmt.nosecalc.ui.theme.onFill
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * B 화면 맨 위: 고른 형상의 작은 그림(요약 꼴 — 글 12sp · 각도 글 없이) + 종류·형상 번호·이름·방향/팁 + 형상 다시 고르기.
 * 그림을 눌러도 형상을 다시 고른다
 */
@Composable
internal fun SummaryCard(kind: TaperKind, type: Int, mirrored: Boolean, info: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(132.dp, 122.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.subtle)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "형상 다시 고르기" },
            ) {
                TypeArt(kind, type, mirrored, compact = true)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("형상 $type", color = c.textStrong, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(typeName(kind, type), color = c.textBody, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(info, color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                PillButton("형상 다시 고르기", null, onBack)
            }
        }
    }
}

/** 점 이름 표(입력 줄 왼쪽·결과 줄 왼쪽). 그림의 라벨 색과 같다 */
@Composable
internal fun PointBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.clip(shape).background(color.copy(alpha = 0.12f)).border(1.5.dp, color.copy(alpha = 0.55f), shape),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

/** 필드 위 라벨(NumberField 와 같은 모양) */
@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = AppType.label, color = AppTheme.colors.fieldNormal.label, maxLines = 1)
}

/**
 * 노즈R(= 인서트 날끝 반경 = 기계 공구 보정 화면 R 칸의 공구 반경): 0.2·0.4·0.8·1.2 고르기 + 직접(키패드).
 * error = 계산을 멈춘 까닭이 노즈R(칩 줄에 빨간 2dp 테 + 라벨 앞 멈춤 아이콘)
 */
@Composable
internal fun NoseRPicker(value: String, error: Boolean = false, onChange: (String) -> Unit) {
    val c = AppTheme.colors
    var editing by remember { mutableStateOf(false) }
    val v = value.toDoubleOrNull()
    val custom = v == null || v !in TaperCalc.NOSE_R_CHOICES
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (error) Icon(AppIcons.Stop, null, Modifier.padding(end = 4.dp).size(16.dp), tint = c.error.label)
            FieldLabel("노즈 R (공구 반경, mm)")
        }
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(9.dp)
        Row(
            if (error) Modifier.border(2.dp, c.error.label, shape).padding(4.dp) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TaperCalc.NOSE_R_CHOICES.forEach { r ->
                Chip("$r", on = v == r, Modifier.weight(1f)) { onChange("$r") }
            }
            Chip(if (custom) value else "직접", on = custom, Modifier.weight(1.25f)) { editing = true }
        }
    }
    if (editing) {
        // 노즈 R 은 비울 수 없다 — 비우면 연 때 값으로
        val start = remember { value }
        NumberPad("노즈 R (공구 반경)", "mm", value, onChange = { onChange(it.ifEmpty { start }) }, onClose = { editing = false })
    }
    if (custom && v == null) Text("노즈 R 을 넣으세요", color = c.error.label, fontSize = 12.sp)
}

@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(if (on) c.teal else c.field)
            .border(1.5.dp, if (on) c.teal else c.lineField, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) c.onFill(c.teal) else c.textValue, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 팁 번호 칸: 지금 번호("3번") + 표준/직접. 누르면 [TipSheet]. 화면의 T 는 도면 점(T1 · T2)이라 팁은 "N번" 으로 쓴다 */
@Composable
internal fun TipField(tip: Int, standard: Int, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(modifier) {
        FieldLabel("팁 번호")
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(c.fieldNormal.bg)
                .border(1.5.dp, c.fieldNormal.border, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${tip}번", color = c.textValue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            val std = tip == standard
            Text(
                if (std) "표준" else "직접",
                Modifier.clip(RoundedCornerShape(4.dp)).background((if (std) c.teal else c.amber).copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
                color = if (std) c.tealDeep else c.amber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text("바꾸기", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
    }
    if (open) {
        TipSheet(tip, standard, onPick = {
            onPick(it)
            open = false
        }) { open = false }
    }
}

/** 팁 번호 고르기 시트. 표준을 고르면 null(표준 따라감) */
@Composable
internal fun TipSheet(tip: Int, standard: Int, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    BottomSheet(onDismiss) {
        SheetTitle("팁 번호 (가상 인선 번호)", "표준 ${standard}번")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TipDiagram(tip, standard) { onPick(if (it == standard) null else it) }
        }
        Text(
            "노즈 중심에서 가상 인선(노즈 원에 Z·X 방향으로 접하는 두 선의 교점)으로 가는 방향입니다. 기계 공구 보정 화면의 T 칸에 이 번호를 넣습니다.",
            Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = c.textBody,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
        )
        PillButton("표준 ${standard}번으로 되돌리기", null, { onPick(null) })
    }
}

/** 팁 1~4 의 쓰임(번호 아래 글) */
private fun tipUse(t: Int): String? = when (t) {
    1 -> "내경 역방향"
    2 -> "내경 정방향"
    3 -> "외경 정방향"
    4 -> "외경 역방향"
    else -> null
}

/**
 * 팁 번호 그림(FANUC 1~8, 0): 가운데 노즈 원(중심 십자), 둘레에 번호(1~4 는 아래에 쓰임), 아래 축 방향과 "0 · 노즈 중심" 알약.
 * 이 앱의 그림 방향(오른쪽 Z+, 위 X+) 그대로다. 번호 칸(76dp) 전체를 누를 수 있다.
 * selected 는 가상 인선 자리를 점과 두 접선으로 보이고, 1~4 면 노즈 뒤에 80° 인서트를 옅게 그린다. onSelect = null 이면 보기만(도움말).
 */
@Composable
internal fun TipDiagram(selected: Int?, standard: Int?, onSelect: ((Int) -> Unit)?) {
    val c = AppTheme.colors
    val cell = 76.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(cell * 3)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = 38.dp.toPx()
                val o = center
                // 노즈 원에 외접하는 사각형 = 가상 인선이 놓일 수 있는 곳
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                drawRect(c.lineStrong, o - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.dp.toPx(), pathEffect = dash))
                if (selected in 1..4) {
                    // 80° 인서트: 이론 날끝 P0 가 가상 인선 쪽 대각선 위 r / sin 40° 에, 두 변(1.9r)이 노즈 원에 접한다
                    val v = NoseComp.tipVector(selected!!, 1.0)
                    val d = Offset(v.z.toFloat(), -v.x.toFloat()) / sqrt(2f)
                    val p0 = o + d * (r / sin(40 * PI / 180).toFloat())
                    fun edge(deg: Double): Offset {
                        val a = deg * PI / 180
                        return p0 + Offset((-d.x * cos(a) + d.y * sin(a)).toFloat(), (-d.x * sin(a) - d.y * cos(a)).toFloat()) * (1.9f * r)
                    }
                    val e1 = edge(40.0)
                    val e2 = edge(-40.0)
                    val insert = Path().apply {
                        moveTo(e1.x, e1.y)
                        lineTo(p0.x, p0.y)
                        lineTo(e2.x, e2.y)
                    }
                    drawPath(insert, c.drawInsert.copy(alpha = 0.22f))
                    // 날 두 변만 긋는다(뒤쪽 끊은 변은 노즈 원을 가로질러 헷갈린다)
                    drawPath(insert, c.drawToolLine.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
                }
                drawCircle(c.drawToolBody.copy(alpha = 0.45f), r, o)
                drawCircle(c.drawProfile, r, o, style = Stroke(1.8.dp.toPx()))
                // 노즈 중심 십자(0 번 자리)
                val h = 6.dp.toPx()
                drawLine(c.drawProfile, o - Offset(h, 0f), o + Offset(h, 0f), 1.2.dp.toPx())
                drawLine(c.drawProfile, o - Offset(0f, h), o + Offset(0f, h), 1.2.dp.toPx())
                if (selected != null) {
                    val v = NoseComp.tipVector(selected, r.toDouble())
                    val p = o + Offset(v.z.toFloat(), -v.x.toFloat())
                    // 가상 인선을 지나는 Z·X 방향 접선
                    if (v.x != 0.0) drawLine(c.drawRef, Offset(o.x - r * 1.35f, p.y), Offset(o.x + r * 1.35f, p.y), 1.4.dp.toPx(), pathEffect = dash)
                    if (v.z != 0.0) drawLine(c.drawRef, Offset(p.x, o.y - r * 1.35f), Offset(p.x, o.y + r * 1.35f), 1.4.dp.toPx(), pathEffect = dash)
                    drawLine(c.drawRef, o, p, 1.4.dp.toPx())
                    drawCircle(c.drawRef, 4.dp.toPx(), p)
                }
            }
            for (t in 1..8) {
                val v = NoseComp.tipVector(t, 1.0)
                val col = if (v.z > 0) 2 else if (v.z < 0) 0 else 1
                val row = if (v.x > 0) 0 else if (v.x < 0) 2 else 1
                Box(
                    Modifier
                        .offset(cell * col, cell * row)
                        .size(cell)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onSelect != null) Modifier.clickable { onSelect(t) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    // 단추는 늘 칸 가운데(번호끼리 줄이 맞게), 쓰임 글은 단추 바로 아래
                    TipButton("$t", on = t == selected, standard = t == standard)
                    tipUse(t)?.let {
                        val std = t == standard
                        Text(
                            it,
                            Modifier.align(Alignment.BottomCenter),
                            color = if (std) c.tealDeep else c.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = if (std) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 14.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        AxisGlyph()
        Spacer(Modifier.height(4.dp))
        val on = selected == 0
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .height(44.dp)
                .clip(shape)
                .background(if (on) c.teal else c.card)
                .border(1.5.dp, if (on) c.teal else c.lineField, shape)
                .then(if (onSelect != null) Modifier.clickable { onSelect(0) } else Modifier)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("0 · 노즈 중심", color = if (on) c.onFill(c.teal) else c.textStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 번호 단추(44dp 원). 표준 번호는 오른쪽 위 가장자리에 "표준" 알약 */
@Composable
private fun TipButton(text: String, on: Boolean, standard: Boolean) {
    val c = AppTheme.colors
    Box {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (on) c.teal else c.card)
                .border(1.5.dp, if (on) c.teal else c.lineField, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (on) c.onFill(c.teal) else c.textStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (standard) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(9.dp, (-3).dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.teal)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("표준", color = c.onFill(c.teal), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, lineHeight = 9.5.sp)
            }
        }
    }
}

/** 그림 방향: 오른쪽 Z+ · 위 X+ 화살 */
@Composable
private fun AxisGlyph() {
    val c = AppTheme.colors
    Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        AxisArrow("Z+", up = false, c.textMuted)
        Spacer(Modifier.width(16.dp))
        AxisArrow("X+", up = true, c.textMuted)
    }
}

@Composable
private fun AxisArrow(name: String, up: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val a = if (up) Offset(center.x, size.height) else Offset(0f, center.y)
        val b = if (up) Offset(center.x, 0f) else Offset(size.width, center.y)
        drawLine(color, a, b - (b - a) * 0.2f, 1.4.dp.toPx())
        arrowHead(b, b - a, color, 5.dp.toPx())
    }
    Spacer(Modifier.width(4.dp))
    Text(name, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

/** 도움말 끝에 붙는 팁 번호 그림과 설명 */
@Composable
internal fun TipHelp() {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("팁 번호 그림 (3번 = 외경 정방향 표준)", color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        TipDiagram(3, null, null)
        Text(
            "가운데 원이 노즈, 원에 Z·X 방향으로 접하는 두 점선이 만나는 점이 3번의 가상 인선입니다. " +
                "번호는 노즈 중심에서 가상 인선으로 가는 방향입니다: 3 외경 정방향, 4 외경 역방향, 2 내경 정방향, 1 내경 역방향, " +
                "5·6·7·8 은 변 가운데, 0(9)은 노즈 중심. 방향은 X+·Z+ 로 정해져 있어 그림이 위아래로 뒤집힌 기계(전방 공구대)에서도 번호는 같습니다.",
            color = c.textBody,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

/** 값 부호를 늘 붙인 고정 셋째 자리(+0.469 / −0.469 / 0.000) */
internal fun signed(v: Double): String {
    val t = Fmt.fixed3(v)
    return if (t.startsWith("-") || t == "0.000") t else "+$t"
}

/** NC 카드의 꼭 알 것 한 칸. tone = null 이면 보통(회색), 아니면 경고 띠와 같은 색 · 아이콘 */
internal data class NcFact(val text: String, val tone: WarnLevel? = null)

/**
 * NC 블록 카드: 머리(제목 · 복사 아이콘) → 프로그램 고르기(tabs, 테이퍼: G41/G42 · 직접 보정) → 꼭 알 것(facts) →
 * 줄 번호 + 고정폭 줄(골라 복사할 수 있고, 줄은 접지 않고 옆으로 민다 · 줄 번호는 복사되지 않음) →
 * 큰 복사 단추(프로그램 이름 · 줄 수, 누르면 2초 "복사됨") → 설명(첫 문장 + 자세히). 복사하면 토스트
 */
@Composable
internal fun NcCard(
    lines: List<String>,
    note: String,
    tabs: List<String> = emptyList(),
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    programName: String = "NC 프로그램",
    facts: List<NcFact> = emptyList(),
) {
    val c = AppTheme.colors
    val state = LocalAppState.current
    @Suppress("DEPRECATION") // LocalClipboard 는 ClipEntry 를 플랫폼마다 따로 만들어야 한다. 글 복사는 이것으로 충분하다
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val copy = {
        clipboard.setText(AnnotatedString(lines.joinToString("\n")))
        state.toast("$programName ${lines.size}줄을 복사했습니다")
        copied = true
    }
    val shown = lines.take(MAX_NC_SHOWN)
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("NC 프로그램") {
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, c.lineCard, CircleShape)
                    .clickable(onClick = copy),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Copy, "복사", Modifier.size(20.dp), tint = c.textBody)
            }
        }
        if (tabs.isNotEmpty()) {
            Segmented(tabs, tab, onTab, Modifier.fillMaxWidth().padding(bottom = 10.dp), color = c.teal, height = 44.dp, fontSize = 14)
        }
        if (facts.isNotEmpty()) {
            FlowRow(
                Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { facts.forEach { FactChip(it) } }
        }
        val shape = RoundedCornerShape(8.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(c.field).border(1.dp, c.lineField, shape).padding(vertical = 10.dp)) {
            Row {
                // 줄 번호(복사 밖) — 줄과 같은 줄 높이라 나란히 선다
                Column(Modifier.padding(start = 4.dp).width(24.dp)) {
                    shown.indices.forEach {
                        Text(
                            "${it + 1}",
                            Modifier.fillMaxWidth(),
                            style = AppType.numSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = NC_LINE, lineHeightStyle = NcLineStyle),
                            color = c.textHint,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(end = 12.dp)) { shown.forEach { NcLine(it) } }
                }
            }
            if (lines.size > shown.size) {
                Text("… ${lines.size - shown.size}줄 더 (복사하면 모두)", Modifier.padding(start = 38.dp, top = 4.dp), color = c.textMuted, fontSize = 12.5.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        val bg = if (copied) c.green else c.teal
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = copy).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (copied) AppIcons.Check else AppIcons.Copy, null, Modifier.size(20.dp), tint = c.onFill(bg))
            Spacer(Modifier.width(8.dp))
            Text(
                if (copied) "복사됨" else "복사 · $programName ${lines.size}줄",
                color = c.onFill(bg),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note.isNotEmpty()) {
            // 첫 문장만 보이고 나머지는 '자세히'
            val cut = note.indexOf(". ")
            if (cut < 0) {
                Text(note, Modifier.padding(top = 8.dp), style = AppType.caption, color = c.textMuted)
            } else {
                ExpandableHint(note.substring(0, cut + 1), note.substring(cut + 2), Modifier.padding(top = 4.dp))
            }
        }
    }
}

private const val MAX_NC_SHOWN = 80

/** NC 줄 높이(줄 번호와 같게) */
private val NC_LINE = 22.sp
private val NcLineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** 꼭 알 것 칩(28dp): 보통은 회색, 경고 · 멈춤 · 안내는 띠와 같은 색과 아이콘 */
@Composable
private fun FactChip(f: NcFact) {
    val c = AppTheme.colors
    val (rc, icon) = when (f.tone) {
        WarnLevel.Warn -> c.warn to AppIcons.Alert
        WarnLevel.Error -> c.error to AppIcons.Stop
        WarnLevel.Info -> c.info to AppIcons.Info
        null -> null to null
    }
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.height(28.dp).clip(shape).background(rc?.bg ?: c.subtle).border(1.dp, rc?.border ?: c.lineCard, shape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rc != null && icon != null) Icon(icon, null, Modifier.padding(end = 4.dp).size(14.dp), tint = rc.label)
        Text(f.text, color = rc?.label ?: c.textBody, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** G 는 파랑, X·Z 는 초록(09 편집기의 색 갈래), 주석 줄 "(…)" 은 흐리게. 접지 않는다 */
@Composable
private fun NcLine(line: String) {
    val c = AppTheme.colors
    Text(
        if (line.startsWith("(")) {
            AnnotatedString(line, SpanStyle(color = c.textMuted))
        } else {
            buildAnnotatedString {
                line.split(' ').forEachIndexed { i, w ->
                    if (i > 0) append(' ')
                    val color = when (w.firstOrNull()) {
                        'G' -> c.blue
                        'X', 'Z' -> c.valuePos
                        else -> c.textValue
                    }
                    withStyle(SpanStyle(color = color)) { append(w) }
                }
            }
        },
        style = AppType.numBody.copy(lineHeight = NC_LINE, lineHeightStyle = NcLineStyle),
        softWrap = false,
        maxLines = 1,
    )
}

/** 뷰어 범례 한 칸 */
internal data class Legend(val color: Color, val label: String, val dashed: Boolean = false)

/** 형상 뷰어 카드: 제목 + [ShapeViewer] + 범례 */
@Composable
internal fun ViewerCard(lines: List<ViewerLine>, marks: List<ViewerMark>, legend: List<Legend>) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        CardHeader("형상 확인", modifier = Modifier.padding(start = Space.cardPad, end = Space.cardPad, top = 14.dp)) {
            Text("확대·이동 · 두 번 눌러 맞춤", style = AppType.caption, color = c.textMuted)
        }
        ShapeViewer(lines, Modifier.fillMaxWidth().height(300.dp), marks)
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            legend.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(18.dp, 8.dp)) {
                        drawLine(
                            l.color, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx(),
                            pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(l.label, color = c.textBody, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
