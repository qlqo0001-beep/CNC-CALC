package com.bmt.nosecalc.screens.round

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.screens.taper.FieldLabel
import com.bmt.nosecalc.screens.taper.NcCard
import com.bmt.nosecalc.screens.taper.NoseRPicker
import com.bmt.nosecalc.screens.taper.PointBadge
import com.bmt.nosecalc.screens.taper.TipField
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.FieldRole
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme

/**
 * TYPE 1~6 입력·결과: 그림 요약 → 모드 → 치수·공구 → 경고 → 노드 표 → 형상 → NC.
 * 값이 바뀌면 바로 다시 푼다. 접점 모드의 NC 는 같은 윤곽을 노즈R 0 으로 푼 것(= 도면 좌표 프로그램)이다.
 */
@Composable
internal fun RoundTypeScreen(s: RoundState, type: Int) {
    val c = AppTheme.colors
    val input = s.input(type)
    val result = remember(input) { RoundCalc.calc(input) }
    // 노즈R 0 으로 푼 NC = 도면 좌표 프로그램
    val drawn = remember(input) { RoundCalc.calc(input.copy(noseR = 0.0)).solution?.nc }
    val sol = result.solution
    val filled = sol?.filled
    val blanks = blankable(type)

    Page {
        RoundSummary(type, "외경 · 정방향 가공 (Z−) · 팁 T${input.tip} · ${Side.G42}") { s.open = false }
        ModeCard(s.comp) { s.comp = it }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Text("도면 치수", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dims(type).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { f ->
                            val v = s.dim(type, f.key)
                            val auto = f.key in blanks && v.isEmpty()
                            NumberField(
                                f.label, v, { s.setDim(type, f.key, it) }, Modifier.weight(1f),
                                unit = "mm",
                                autoValue = if (auto) filled?.let { autoText(it, f.key) } ?: "" else null,
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (type == 2) {
                    Column {
                        FieldLabel("L2 기준")
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf("A  왼쪽 이음점 ~ 오른쪽 끝", "B  두 이음점 사이"), if (s.l2Ref == L2Ref.A) 0 else 1,
                            { s.l2Ref = if (it == 0) L2Ref.A else L2Ref.B }, Modifier.fillMaxWidth(), color = c.teal, height = 42.dp, fontSize = 13,
                        )
                    }
                }
            }
            Hint(
                buildString {
                    val dia = dims(type).filter { it.label.startsWith("D") }
                    if (dia.isNotEmpty()) append("${dia.joinToString(" · ") { it.label }} 는 지름(직경)입니다. ")
                    append("길이는 모서리 R 을 넣기 전 이론 꼭짓점 사이, 모서리 R 이 없으면 0.")
                    if (blanks.isNotEmpty()) {
                        val names = dims(type).associate { it.key to it.label }
                        append(" ${blanks.joinToString(" · ") { names.getValue(it) }} 가운데 하나를 비우면 나머지로 계산합니다(주 R 은 중심이 축 위인 구).")
                    }
                },
            )
            Divider()
            ToolInputs(s, standardTip = RoundState.STANDARD_TIP, tip = input.tip, onTip = { s.tip = it }, leg = type != 1)
        }

        Results(result, type, s.comp, input.noseR, drawn)
    }
}

/** 수동(CAD 3점호): 외경/내경 · 세 점 · 공구 → 경고 → 원호 · 노드 표 → 형상 → NC */
@Composable
internal fun ManualScreen(s: RoundState) {
    val c = AppTheme.colors
    val input = s.arc3()
    val result = remember(input) { RoundCalc.arc3(input) }
    val drawn = remember(input) { RoundCalc.arc3(input.copy(noseR = 0.0)).solution?.nc }
    val sol = result.solution
    val standard = s.manualStandardTip()
    val tip = s.manualTip ?: standard
    val reverse = sol?.let { it.nodes.last().drawn.z > it.nodes.first().drawn.z }

    Page {
        val dir = when (reverse) {
            null -> "방향은 점으로 정해짐"
            true -> "역방향 가공 (Z+)"
            false -> "정방향 가공 (Z−)"
        }
        val side = if (reverse == null) "" else " · ${Side.of(s.inner, reverse)}"
        RoundSummary(MANUAL, "${if (s.inner) "내경" else "외경"} · $dir · 팁 T$tip$side") { s.open = false }
        ModeCard(s.comp) { s.comp = it }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Segmented(listOf("외경", "내경"), if (s.inner) 1 else 0, { s.inner = it == 1 }, Modifier.fillMaxWidth(), height = 44.dp)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple(1, "시작", FieldRole.Start), Triple(2, "가운데", FieldRole.Normal), Triple(3, "끝", FieldRole.End)).forEach { (i, name, role) ->
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PointBadge("P$i", pointColor(i), Modifier.size(38.dp, 48.dp))
                        NumberField("X$i $name (직경)", s.point("x$i"), { s.setPoint("x$i", it) }, Modifier.weight(1f), unit = "mm", role = role)
                        NumberField("Z$i", s.point("z$i"), { s.setPoint("z$i", it) }, Modifier.weight(1f), unit = "mm", role = role)
                    }
                }
            }
            Hint("CAD 에서 원호 위 세 점을 가공 순서(시작 → 가운데 → 끝)로 찍어 넣습니다. 가운데 점은 원호 위 아무 곳이나 됩니다. 끝 Z 가 시작보다 크면 역방향(역바이트)입니다.")
            Divider()
            ToolInputs(s, standardTip = standard, tip = tip, onTip = { s.manualTip = it }, leg = false)
        }

        if (sol != null) ArcCard(sol)
        Results(result, MANUAL, s.comp, input.noseR, drawn)
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 노즈R · Z 여유 · 끝 요소 길이(TYPE 2~6) · 팁 번호 — TYPE 끼리 함께 쓴다 */
@Composable
private fun ToolInputs(s: RoundState, standardTip: Int, tip: Int, onTip: (Int?) -> Unit, leg: Boolean) {
    NoseRPicker(s.shared("noseR")) { s.setShared("noseR", it) }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField("Z 여유 (진입)", s.shared("zClear"), { s.setShared("zClear", it) }, Modifier.weight(1f), unit = "mm", placeholder = "1.0")
        if (leg) {
            NumberField("끝 요소 길이", s.shared("leg"), { s.setShared("leg", it) }, Modifier.weight(1f), unit = "mm", placeholder = "5.0")
        } else {
            TipField(tip, standardTip, onTip, Modifier.weight(1f))
        }
    }
    if (leg) {
        Spacer(Modifier.height(12.dp))
        TipField(tip, standardTip, onTip)
    }
}

/** 경고 → 노드 표 → 형상 → NC(모드에 따라 보정 / 도면 좌표) */
@Composable
private fun Results(result: RoundResult, type: Int, comp: Boolean, noseR: Double, drawnNc: List<String>?) {
    val sol = result.solution
    result.warnings.forEach {
        WarningBanner(roundWarningText(it, type), level = if (sol == null) WarnLevel.Error else WarnLevel.Warn)
    }
    if (sol == null) return
    NodeTable(sol, comp)
    RoundViewer(sol, comp, noseR)
    val legNote = if (type in 2..6) " 마지막 줄은 끝 요소 끝(끝 요소 길이)까지입니다." else ""
    if (comp) {
        NcCard(
            sol.nc,
            "가상 인선 T${sol.tip} 기준 프로그램 좌표입니다 — 기계의 노즈R 보정은 쓰지 않습니다(G40). 원호 R 은 합성 R(볼록 R + r · 오목 R − r)이고, " +
                "90° 넘는 원호는 나눠 썼습니다. 이송 F 는 넣지 않았습니다.$legNote",
        )
    } else if (drawnNc != null) {
        NcCard(
            drawnNc,
            "도면(접점) 좌표 프로그램입니다. 기계의 노즈R 보정(${sol.side} · 공구 보정 R${Fmt.nc(noseR.takeIf { it.isFinite() } ?: 0.0)} · T${sol.tip})과 함께 쓰고, " +
                "보정 시작 · 취소(${sol.side}/G40) 블록은 기계에 맞게 넣으세요. 이 좌표를 보정 없이 깎으면 노즈R 만큼 형상이 틀립니다. 이송 F 는 넣지 않았습니다.$legNote",
        )
    }
}

/** 비운 칸에 흐리게 보일 계산된 값("자동" 표는 칸 라벨 줄에) */
private fun autoText(f: RoundInput, key: String): String {
    val v = when (key) {
        "mainR" -> f.mainR
        "d1" -> f.d1
        "d2" -> f.d2
        "l1" -> f.l1
        "l2" -> f.l2
        else -> null
    }
    return v?.let { Fmt.fixed3(it) } ?: ""
}

@Composable
private fun pointColor(i: Int) = when (i) {
    1 -> AppTheme.colors.drawT1
    2 -> AppTheme.colors.drawT2
    else -> AppTheme.colors.red
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(top = 12.dp), color = AppTheme.colors.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp)
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.lineCard))
}
