package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.NumberPad
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.ShapeViewer
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.onFill

/** B 화면 맨 위: 고른 형상의 작은 그림 + 종류·형상 번호·이름·방향/팁 + 형상 다시 고르기 */
@Composable
internal fun SummaryCard(kind: TaperKind, type: Int, mirrored: Boolean, info: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(8.dp)
            Box(Modifier.width(132.dp).aspectRatio(1.08f).clip(shape).background(c.subtle).border(1.5.dp, c.lineStrong, shape)) {
                TypeArt(kind, type, mirrored)
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
    Text(text, color = AppTheme.colors.fieldNormal.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** 노즈R(= 인서트 날끝 반경 = 기계 공구 보정 화면 R 칸의 공구 반경): 0.2·0.4·0.8·1.2 고르기 + 직접(키패드) */
@Composable
internal fun NoseRPicker(value: String, onChange: (String) -> Unit) {
    val c = AppTheme.colors
    var editing by remember { mutableStateOf(false) }
    val v = value.toDoubleOrNull()
    val custom = v == null || v !in TaperCalc.NOSE_R_CHOICES
    Column {
        FieldLabel("노즈 R (공구 반경, mm)")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaperCalc.NOSE_R_CHOICES.forEach { r ->
                Chip("$r", on = v == r, Modifier.weight(1f)) { onChange("$r") }
            }
            Chip(if (custom) value else "직접", on = custom, Modifier.weight(1.25f)) { editing = true }
        }
    }
    if (editing) {
        NumberPad(
            "노즈 R (공구 반경)", "mm", value,
            onDone = {
                if (it.isNotEmpty()) onChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
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

/** 팁 번호 칸: 지금 번호 + 표준/직접. 누르면 [TipSheet] */
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
                .height(48.dp)
                .clip(shape)
                .background(c.fieldNormal.bg)
                .border(1.5.dp, c.fieldNormal.border, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("T$tip", color = c.textValue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
        SheetTitle("팁 번호 (가상 인선 번호)", "표준 T$standard")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TipDiagram(tip, standard) { onPick(if (it == standard) null else it) }
        }
        Text(
            "노즈 중심에서 가상 인선(노즈 원에 Z·X 로 접하는 두 선의 교점)으로 가는 방향입니다. 기계 공구 보정 화면의 T 칸 번호와 같아야 합니다.",
            Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = c.textBody,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
        )
        PillButton("표준 T$standard 로 되돌리기", null, { onPick(null) })
    }
}

/**
 * 팁 번호 그림(FANUC 1~9, 0): 가운데 노즈 원, 둘레에 번호. 이 앱의 그림 방향(오른쪽 Z+, 위 X+) 그대로다.
 * selected 는 가상 인선 자리를 점과 두 접선으로 보인다. onSelect = null 이면 보기만(도움말).
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
                drawCircle(c.drawToolBody.copy(alpha = 0.45f), r, o)
                drawCircle(c.drawProfile, r, o, style = Stroke(1.8.dp.toPx()))
                drawCircle(c.drawProfile, 2.dp.toPx(), o)
                if (selected != null) {
                    val v = NoseComp.tipVector(selected, r.toDouble())
                    val p = o + Offset(v.z.toFloat(), -v.x.toFloat())
                    // 가상 인선을 지나는 Z·X 방향 접선
                    if (v.x != 0.0) drawLine(c.drawRef, Offset(o.x - r * 1.5f, p.y), Offset(o.x + r * 1.5f, p.y), 1.4.dp.toPx(), pathEffect = dash)
                    if (v.z != 0.0) drawLine(c.drawRef, Offset(p.x, o.y - r * 1.5f), Offset(p.x, o.y + r * 1.5f), 1.4.dp.toPx(), pathEffect = dash)
                    drawLine(c.drawRef, o, p, 1.4.dp.toPx())
                    drawCircle(c.drawRef, 4.dp.toPx(), p)
                }
            }
            for (t in 0..8) {
                val v = NoseComp.tipVector(t, 1.0)
                val col = if (v.z > 0) 2 else if (v.z < 0) 0 else 1
                val row = if (v.x > 0) 0 else if (v.x < 0) 2 else 1
                val on = t == selected
                Column(
                    Modifier.offset(cell * col, cell * row).size(cell),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (on) c.teal else c.card)
                            .border(1.5.dp, if (on) c.teal else c.lineField, CircleShape)
                            .then(if (onSelect != null) Modifier.clickable { onSelect(t) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (t == 0) "0" else "$t", color = if (on) c.onFill(c.teal) else c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (t == standard) Text("표준", color = c.tealDeep, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("그림 방향: 오른쪽 Z+ · 위 X+", color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 도움말 끝에 붙는 팁 번호 그림과 설명 */
@Composable
internal fun TipHelp() {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("팁 번호 그림 (T3 = 외경 정방향 표준)", color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        TipDiagram(3, null, null)
        Text(
            "가운데 원이 노즈, 원에 Z·X 방향으로 접하는 두 점선이 만나는 점이 T3 의 가상 인선입니다. " +
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

/** NC 블록 카드: 고정폭 줄 + 복사(복사하면 토스트). tabs 가 있으면 제목 아래 프로그램 고르기(테이퍼: G41/G42 · 직접 보정) */
@Composable
internal fun NcCard(lines: List<String>, note: String, tabs: List<String> = emptyList(), tab: Int = 0, onTab: (Int) -> Unit = {}) {
    val c = AppTheme.colors
    val state = LocalAppState.current
    @Suppress("DEPRECATION") // LocalClipboard 는 ClipEntry 를 플랫폼마다 따로 만들어야 한다. 글 복사는 이것으로 충분하다
    val clipboard = LocalClipboardManager.current
    val shown = lines.take(MAX_NC_SHOWN)
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NC 프로그램", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${lines.size}줄", color = c.textMuted, fontSize = 12.5.sp)
            Spacer(Modifier.weight(1f))
            PillButton("복사", null, {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                state.toast("NC ${lines.size}줄을 복사했습니다")
            })
        }
        if (tabs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Segmented(tabs, tab, onTab, Modifier.fillMaxWidth(), color = c.teal, height = 44.dp, fontSize = 14)
        }
        Spacer(Modifier.height(10.dp))
        val shape = RoundedCornerShape(8.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.field).border(1.dp, c.lineField, shape).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            shown.forEach { NcLine(it) }
            if (lines.size > shown.size) {
                Text("… ${lines.size - shown.size}줄 더 (복사하면 모두)", color = c.textMuted, fontSize = 12.5.sp)
            }
        }
        if (note.isNotEmpty()) Text(note, Modifier.padding(top = 8.dp), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
}

private const val MAX_NC_SHOWN = 80

/** G 는 파랑, X·Z 는 초록(09 편집기의 색 갈래) */
@Composable
private fun NcLine(line: String) {
    val c = AppTheme.colors
    Text(
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
        },
        fontFamily = AppTheme.mono,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** 뷰어 범례 한 칸 */
internal data class Legend(val color: Color, val label: String, val dashed: Boolean = false)

/** 형상 뷰어 카드: 제목 + [ShapeViewer] + 범례 */
@Composable
internal fun ViewerCard(lines: List<ViewerLine>, marks: List<ViewerMark>, legend: List<Legend>) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("형상 확인", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("확대·이동 · 두 번 눌러 맞춤", color = c.textMuted, fontSize = 11.5.sp)
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
