package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.BarField
import com.bmt.nosecalc.calc.BoringBar
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.HolderCheck
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.screens.taper.FieldLabel
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.arrowHead
import com.bmt.nosecalc.ui.centerLine
import com.bmt.nosecalc.ui.hatchFill
import com.bmt.nosecalc.ui.haloText
import com.bmt.nosecalc.ui.linearDim
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 보링바(홀더) 카드 — 내경 TYPE 화면 · 3점호 내경 화면에서 도면 · 공구 카드 다음. 단면 스케치(D · f · κr · κr′ · 돌출 길이) →
 * 프리셋(처음은 직접 입력, 고친 뒤엔 '수정됨' · 출처) → 칸: 바 지름 · 날 길이 · f · 인서트 모양 · 절입각(끝날각 κr′ 계산 줄) · 돌출 길이 · DMIN.
 * 칸 오류는 계산기의 problems 그대로 칸 아래 빨간 글(r = 노즈 R — f · 날 길이 하한). 칸은 화면의 키패드 묶음(다음)에 든다(preview = 키패드 결과 한 줄).
 * reverse = 뒤보링(3점호 역방향 내경) — 스케치의 인서트도 뒤집는다
 */
@Composable
internal fun HolderCard(s: RoundState, r: Double, preview: String? = null, reverse: Boolean = false) {
    val c = AppTheme.colors
    val bar = s.holderBar()
    val bad = if (bar != null && r >= 0 && r.isFinite()) HolderCheck.problems(bar, r) else emptySet()
    var picking by remember { mutableStateOf(false) }

    @Composable
    fun field(key: String, label: String, f: BarField, modifier: Modifier, hint: String, placeholder: String = "", unit: String = "mm") {
        Column(modifier) {
            NumberField(
                label, s.holder(key), { s.setHolder(key, it) }, Modifier.fillMaxWidth(),
                unit = unit, placeholder = placeholder, error = f in bad, hint = hint, id = "h/$key", preview = preview,
            )
            if (f in bad && bar != null) {
                Text(barFieldText(f, bar, r), Modifier.padding(top = 4.dp), color = c.error.label, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        CardHeader("보링바 (홀더)", "공구가 소재에 닿는지 봅니다 · 카탈로그 값")
        val shape = RoundedCornerShape(10.dp)
        HolderSketch(Modifier.fillMaxWidth().clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape).aspectRatio(SKETCH_W / SKETCH_H), reverse)
        Spacer(Modifier.height(14.dp))
        PresetField(s.preset, s.presetEdited()) { picking = true }
        s.preset?.let { Text("출처: ${it.host}", Modifier.padding(top = 4.dp), color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field("dia", "바 지름 D", BarField.DIA, Modifier.weight(1f), "생크 지름 · 카탈로그 DCON")
                field("edge", "날 길이 l", BarField.EDGE, Modifier.weight(1f), "인서트 크기 숫자 (DCMT 07 → 7)")
            }
            field("f", "f (바 중심 ~ 인선, 카탈로그 WF)", BarField.F, Modifier.fillMaxWidth(), "바 중심선에서 인선까지 · 카탈로그 WF")
            Column {
                FieldLabel("인서트 모양 (코너 각)")
                Spacer(Modifier.height(6.dp))
                Segmented(
                    InsertShape.entries.map { "${it.name} ${plain(it.epsDeg)}°" }, InsertShape.entries.indexOfFirst { it.name == s.holder("shape") },
                    { s.setHolder("shape", InsertShape.entries[it].name) }, Modifier.fillMaxWidth(), color = c.teal, height = 46.dp, fontSize = 15,
                )
            }
            Column {
                field("kr", "절입각 κr (카탈로그 KAPR)", BarField.KAPPA, Modifier.fillMaxWidth(), "카탈로그 KAPR · 보통 91 · 93 · 95 · 107.5", unit = "°")
                val kr = s.holder("kr").toDoubleOrNull()?.takeIf { it.isFinite() }
                val eps = InsertShape.entries.firstOrNull { it.name == s.holder("shape") }?.epsDeg
                if (kr != null && eps != null) {
                    Text("끝날각 κr′ = ${plain(180 - kr - eps)}°", Modifier.padding(top = 4.dp), color = c.textMuted, fontSize = 12.5.sp)
                }
            }
            field("ovh", "돌출 길이 (비우면 공구대 검사 안 함)", BarField.OVERHANG, Modifier.fillMaxWidth(), "인선에서 공구대 면까지 Z 거리", placeholder = "검사 안 함")
            field("dmin", "최소 가공경 Dmin (비우면 검사 안 함)", BarField.DMIN, Modifier.fillMaxWidth(), "카탈로그 DMIN · 헤드가 들어가는 가장 작은 구멍 지름", placeholder = "검사 안 함")
        }
    }
    if (picking) {
        PresetSheet(s.preset, onPick = {
            s.pickPreset(it)
            picking = false
        }) { picking = false }
    }
}

/** 프리셋 칸: '프리셋 (선택)' + 칸(품번 · 제조사 또는 '직접 입력', 고쳤으면 '수정됨' 표, 오른쪽 '고르기 ⌄'). 누르면 [PresetSheet] */
@Composable
private fun PresetField(p: HolderPreset?, edited: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Column {
        FieldLabel("프리셋 (선택)")
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(c.fieldNormal.bg)
                .border(1.5.dp, c.fieldNormal.border, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 품번 · 제조사 · 수정됨 은 남은 자리 안에서(품번이 길면 품번만 줄인다)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p?.code ?: "직접 입력",
                    Modifier.weight(1f, fill = false),
                    color = c.textValue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p != null) Text(p.maker, Modifier.padding(start = 6.dp), color = c.textMuted, fontSize = 13.sp, maxLines = 1)
                if (edited) {
                    Text(
                        "수정됨",
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(c.amber.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
                        color = c.amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("고르기", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(14.dp), tint = c.textMuted)
        }
    }
}

/** 프리셋 고르기 시트: 직접 입력 → 제조사별 품번(Ø · f · Dmin · κr · 인서트). 고른 줄은 청록 체크 */
@Composable
private fun PresetSheet(current: HolderPreset?, onPick: (HolderPreset?) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    BottomSheet(onDismiss, maxHeightFraction = 0.85f) {
        SheetTitle("보링바 프리셋", "제조사 카탈로그 값")
        Text(
            "같은 이름의 바도 제조사마다 f · DMIN · κr 이 다릅니다. 가진 공구의 카탈로그와 맞추세요. 돌출 길이는 직접 넣습니다.",
            Modifier.padding(bottom = 8.dp),
            style = AppType.caption,
            color = c.textMuted,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PresetRow("직접 입력", "칸에 넣은 값 그대로", current == null) { onPick(null) }
            HOLDER_PRESETS.groupBy { it.maker }.forEach { (maker, list) ->
                Text(maker, Modifier.padding(top = 14.dp, bottom = 4.dp), color = c.tealDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                list.forEach { p ->
                    PresetRow(p.code, "Ø${plain(p.dia)} · f ${plain(p.f)} · Dmin ${plain(p.dMin)} · κr ${plain(p.kappa)}° · ${p.shape.name} ${plain(p.edge)}", p == current) {
                        onPick(p)
                    }
                }
            }
        }
    }
}

/** 시트 한 줄(52dp 이상): 이름(굵게) · 아랫줄 값, 고른 줄은 오른쪽 청록 체크 */
@Composable
private fun PresetRow(name: String, sub: String, on: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(sub, color = c.textMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (on) Icon(AppIcons.Check, "고름", Modifier.size(20.dp), tint = c.teal)
    }
}

/** 스케치 자리(mm): Z −12 ~ 51, X −15 ~ 21 */
private const val SKETCH_W = 63f
private const val SKETCH_H = 36f

/**
 * 보링바 단면 스케치: 구멍(위 깎는 벽 · 아래 맞은편 벽, 빗금)에 넣은 견본 바(Ø16 · f 11 · D 7 · κr 93° — 바 · 인서트 모양은 계산기 outline 그대로)와 공구대.
 * 치수 D · f(바 중심선 ~ 인선) · 돌출 길이(인선 ~ 공구대 면), 각 κr(이송 −Z 에서 주절삭날) · κr′(+Z 에서 끝날).
 * reverse(뒤보링) = 계산기처럼 인서트를 노즈 중심에서 Z 로 뒤집는다(날이 +Z, 팁 1) — 바는 그대로 +Z 공구대로. 각은 이송 +Z 에서 κr · −Z 에서 κr′
 */
@Composable
private fun HolderSketch(modifier: Modifier, reverse: Boolean) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val sans = AppTheme.sans
    Canvas(modifier) {
        val r = 0.4
        val wall = 12.0
        val face = 28.0
        val turret = 44.0
        val bar = BoringBar(16.0, 11.0, InsertShape.D, 7.0, 93.0, overhang = turret)
        val axis = wall - bar.f
        val s = min(size.width / SKETCH_W, size.height / SKETCH_H).toDouble()
        fun at(z: Double, x: Double) = Offset(((z + 12) * s).toFloat(), ((21 - x) * s).toFloat())
        fun path(ct: Contour) = Path().apply {
            ct.segs.forEachIndexed { i, seg ->
                if (i == 0) at(seg.start.z, seg.start.x).let { moveTo(it.x, it.y) }
                at(seg.end.z, seg.end.x).let { lineTo(it.x, it.y) }
            }
            close()
        }
        fun rect(z0: Double, x0: Double, z1: Double, x1: Double) = Path().apply { addRect(Rect(at(z0, x1), at(z1, x0))) }
        // 소재: 위(깎는 벽) · 아래(맞은편 벽) 띠, 입구 끝면까지
        for (x0 in listOf(wall, -wall - 3)) hatchFill(rect(-12.0, x0, face, x0 + 3), c.drawStock, c.drawHatch, 1f)
        drawLine(c.drawProfile, at(-12.0, wall), at(face, wall), 2.dp.toPx())
        drawLine(c.drawProfile, at(-12.0, -wall), at(face, -wall), 1.dp.toPx())
        // 공구대
        rect(turret, axis - 11, turret + 6, axis + 11).let {
            drawPath(it, c.drawToolBody)
            drawPath(it, c.drawToolLine, style = Stroke(1.dp.toPx()))
        }
        // 바 · 인서트(뷰어와 같은 초록)
        val nose = Pt(0.0, wall) - NoseComp.tipVector(if (reverse) 1 else 2, r)
        for (part in HolderCheck.outline(bar, r, nose, turret, reverse)) {
            val p = path(part)
            drawPath(p, c.drawHolderFill)
            drawPath(p, c.drawHolder, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
        centerLine(at(-10.5, axis), at(turret + 7.5, axis), c.drawRef, 1f)
        // 치수 · 각
        val line = c.textBody
        val style = TextStyle(fontFamily = sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textValue)
        fun label(t: String) = AnnotatedString(t)
        linearDim(at(36.0, axis - 8), at(36.0, axis + 8), 0f, label("D"), line, 1f, measurer, style, c.subtle)
        linearDim(at(-8.5, axis), at(-8.5, wall), 0f, label("f"), line, 1f, measurer, style, c.subtle)
        linearDim(at(0.0, wall), at(turret, wall), -(6 * s).toFloat(), label("돌출 길이"), line, 1f, measurer, style, c.subtle)
        val k = bar.kappaDeg * PI / 180
        val kp = PI - k - bar.shape.epsDeg * PI / 180
        if (reverse) {
            // 뒤보링: 주절삭날은 이송 +Z 에서 아래로, 끝날은 −Z 에서. κr 이름은 날과 바 윗면 사이 틈(호 가운데는 바 위)
            angle(at(0.0, wall), 3.5 * s, 0.0, -k, "κr", c.drawAngle, c.subtle, measurer, style, labelAt = -0.3)
            angle(at(0.0, wall), 6.5 * s, PI, PI + kp, "κr′", c.drawAngle, c.subtle, measurer, style, labelAt = PI - 0.3)
        } else {
            angle(at(0.0, wall), 3.5 * s, PI, PI + k, "κr", c.drawAngle, c.subtle, measurer, style)
            // κr′ 이름은 호 위 깎는 벽 띠에(호 안쪽은 바 윗면과 겹친다)
            angle(at(0.0, wall), 6.5 * s, 0.0, -kp, "κr′", c.drawAngle, c.subtle, measurer, style, labelAt = 0.3)
        }
        // 벽 이름은 빗금 띠 위라 본문색(textBody — 밝은 테마 drawStock 위 6.3:1, textMuted 는 4:1 이 안 됐다)
        val muted = style.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.textBody)
        haloText(measurer, label("깎는 벽"), muted, c.drawStock, 1f, center = at(face - 6, wall + 1.5))
        haloText(measurer, label("맞은편 벽"), muted, c.drawStock, 1f, center = at(face - 6, -wall - 1.5))
    }
}

/**
 * 꼭짓점 o 에서 반지름 rad(px)의 각 호(화면은 y 가 아래라 수학 각을 뒤집는다): from → to(라디안, Z 오른쪽 · X 위 기준) +
 * 끝 화살 + 바깥 이름(halo 테, 각 labelAt — 처음은 호 가운데). 시작 방향으로 끊은 기준선(반지름 + 3dp)
 */
private fun DrawScope.angle(
    o: Offset,
    rad: Double,
    from: Double,
    to: Double,
    name: String,
    color: Color,
    halo: Color,
    measurer: TextMeasurer,
    style: TextStyle,
    labelAt: Double = (from + to) / 2,
) {
    fun on(a: Double, rr: Double) = o + Offset((rr * cos(a)).toFloat(), (-rr * sin(a)).toFloat())
    val n = 24
    val arc = Path().apply {
        for (i in 0..n) on(from + (to - from) * i / n, rad).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
    }
    drawLine(color, o, on(from, rad + 3.dp.toPx()), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
    drawPath(arc, color, style = Stroke(1.2.dp.toPx()))
    val d = if (to > from) 1 else -1
    arrowHead(on(to, rad), Offset((-sin(to) * d).toFloat(), (-cos(to) * d).toFloat()), color, 5.dp.toPx())
    haloText(measurer, AnnotatedString(name), style.copy(color = color), halo, 1f, center = on(labelAt, rad + 9.dp.toPx()))
}
