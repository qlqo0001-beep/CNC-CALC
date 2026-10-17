package com.bmt.nosecalc.screens.taper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.GrooveCalc
import com.bmt.nosecalc.calc.GrooveInput
import com.bmt.nosecalc.calc.GrooveRef
import com.bmt.nosecalc.calc.GrooveSolution
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlin.math.max

/**
 * B 화면 — Outer Groove: 기준 인선(TYPE 1 왼쪽 T3 / TYPE 2 오른쪽 T4) → 치수 → 침범 경고 → 다단 진입 표 → 형상 → NC.
 * 벽·바닥이 축에 평행해 노즈R 보정은 0 이다(calc-design.md 홈 계산).
 */
@Composable
internal fun GrooveScreen(s: TaperState) {
    val c = AppTheme.colors
    val type = s.type ?: return
    val ref = if (type == 1) GrooveRef.LEFT else GrooveRef.RIGHT
    fun v(name: String) = s.shared(name)
    val input = GrooveInput(
        zStart = v("zStart").toDoubleOrNull(),
        width = v("width").toDoubleOrNull(),
        od = v("od").toDoubleOrNull(),
        bottom = v("bottom").toDoubleOrNull(),
        insert = v("insert").toDoubleOrNull(),
        overlap = v("overlap").toDoubleOrNull() ?: 0.5,
        ref = ref,
        xClear = v("xClear").toDoubleOrNull() ?: 1.0,
    )
    val result = remember(input) { GrooveCalc.calc(input) }
    val sol = result.solution

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryCard(TaperKind.GROOVE, type, false, "기준 인선 팁 T${ref.tip} · 노즈R 보정 0") { s.open = false }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            FieldLabel("기준 인선 (프로그램 기준 모서리)")
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("왼쪽 인선 (T3)", "오른쪽 인선 (T4)"), type - 1, { s.type = it + 1 }, height = 46.dp, fontSize = 15)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(s, "zStart", "시작 Z (오른쪽 벽)", Modifier.weight(1f))
                    Field(s, "width", "홈 폭 W", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(s, "od", "외경 D (직경)", Modifier.weight(1f))
                    Field(s, "bottom", "바닥 지름 d", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(s, "insert", "인서트 폭 T", Modifier.weight(1f))
                    Field(s, "overlap", "단 겹침", Modifier.weight(1f), placeholder = "0.5")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(s, "xClear", "X 여유 (반경)", Modifier.weight(1f), placeholder = "1.0")
                    Spacer(Modifier.weight(1f))
                }
            }
            Text(
                "피치 = T − 겹침. 첫 단은 날 오른쪽을 오른쪽 벽에, 마지막 단은 날 왼쪽을 왼쪽 벽에 정확히 맞춥니다. " +
                    "단마다 외경 + 2×X 여유 로 빠집니다.",
                Modifier.padding(top = 12.dp),
                color = c.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }

        result.warnings.forEach { WarningBanner(grooveWarningText(it), level = WarnLevel.Error) }
        if (sol != null) {
            val t = input.insert!!
            StepsCard(sol, ref)
            GrooveViewer(sol, input, t)
            NcCard(
                sol.nc,
                "단마다 G00 빠짐 → G01 바닥 → G00 빠짐, 끝에 벽·바닥 정삭. 기준 인선 좌표 그대로(G40) 깎습니다 — 벽·바닥이 축에 평행해 " +
                    "노즈R 보정량이 0 이고, 반대쪽 벽은 다른 날 모서리가 깎아 공구 보정 하나(G41/G42)로는 맡을 수 없어 G41/G42 프로그램은 따로 없습니다. " +
                    "이송 F 는 넣지 않았습니다.",
            )
        }
    }
}

@Composable
private fun Field(s: TaperState, name: String, label: String, modifier: Modifier, placeholder: String = "") {
    NumberField(label, s.shared(name), { s.setShared(name, it) }, modifier, unit = "mm", placeholder = placeholder)
}

/** 다단 진입 표: 단 수·피치·빠짐, 단마다 진입 Z·목표 X, 정삭 경로 */
@Composable
private fun StepsCard(sol: GrooveSolution, ref: GrooveRef) {
    val c = AppTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }
    val n = sol.steps.size
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("다단 진입", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("기준 인선 T${ref.tip} (${if (ref == GrooveRef.LEFT) "왼쪽" else "오른쪽"}) 의 프로그램 좌표", color = c.textMuted, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("단 수", "$n", Modifier.weight(0.8f))
            Stat("피치", Fmt.fixed3(sol.pitch), Modifier.weight(1f))
            Stat("빠짐 X", Fmt.fixed3(sol.retractXDia), Modifier.weight(1.1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().background(c.field).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("단", Modifier.width(34.dp), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("진입 Z", Modifier.weight(1f), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            Text("목표 X", Modifier.weight(1f), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            Text("", Modifier.width(78.dp))
        }
        val rows = if (all || n <= SHOWN) sol.steps.indices.toList() else (0 until SHOWN - 1) + (n - 1)
        rows.forEachIndexed { k, i ->
            if (k > 0 && rows[k - 1] != i - 1) {
                Text(
                    "… ${i - rows[k - 1] - 1}단 더 보기",
                    Modifier.fillMaxWidth().clickable { all = true }.padding(vertical = 6.dp),
                    color = c.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
            val st = sol.steps[i]
            val note = when {
                n == 1 -> "한 번에"
                i == 0 -> "오른쪽 벽"
                i == n - 1 -> "왼쪽 벽 맞춤"
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(34.dp), color = c.textBody, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Mono(Fmt.fixed3(st.z), Modifier.weight(1f))
                Mono(Fmt.fixed3(st.xDia), Modifier.weight(1f))
                Text(note, Modifier.width(78.dp), color = c.tealDeep, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
        }
        Spacer(Modifier.height(12.dp))
        SmallLabel("벽·바닥 정삭 (기준 인선)")
        val f = listOf(sol.finish.segs.first().start) + sol.finish.segs.map { it.end }
        val names = listOf("오른쪽 벽 위", "바닥", "왼쪽 벽", "위로")
        f.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(names[i], Modifier.width(92.dp), color = c.textBody, fontSize = 13.sp)
                Text(
                    "X${Fmt.fixed3(p.xDia)}  Z${Fmt.fixed3(p.z)}",
                    color = c.textValue, fontFamily = AppTheme.mono, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            "벽·바닥이 축에 평행해 노즈R 보정은 0 입니다. 바닥 두 모서리에는 인서트 노즈R 만큼 R 이 남습니다. " +
                "인서트 ${if (ref == GrooveRef.LEFT) "왼쪽" else "오른쪽"} 모서리에 공구 길이를 맞추고 팁 번호는 T${ref.tip} 으로 둡니다.",
            Modifier.padding(top = 8.dp),
            color = c.textMuted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
    }
}

private const val SHOWN = 12

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        SmallLabel(label)
        Text(value, color = AppTheme.colors.valueComp, fontFamily = AppTheme.mono, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Mono(text: String, modifier: Modifier) {
    Text(text, modifier, color = AppTheme.colors.textValue, fontFamily = AppTheme.mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
}

/** 외경 + 홈 도면선, 단마다 날 자리(점선), 정삭 경로(기준 인선) */
@Composable
private fun GrooveViewer(sol: GrooveSolution, i: GrooveInput, t: Double) {
    val c = AppTheme.colors
    val z0 = i.zStart!!
    val w = i.width!!
    val od = i.od!!
    val d = i.bottom!!
    val ext = max(2.0, w * 0.4)
    val off = if (i.ref == GrooveRef.LEFT) 0.0 else t
    val lines = buildList {
        add(
            ViewerLine(
                Contour.polyline(
                    Pt.ofDia(z0 + ext, od), Pt.ofDia(z0, od), Pt.ofDia(z0, d),
                    Pt.ofDia(z0 - w, d), Pt.ofDia(z0 - w, od), Pt.ofDia(z0 - w - ext, od),
                ),
                c.drawProfile, 2.5.dp,
            ),
        )
        if (sol.steps.size <= 40) {
            sol.steps.forEach { st ->
                val left = st.z - off
                add(
                    ViewerLine(
                        Contour.polyline(
                            Pt.ofDia(left, sol.retractXDia), Pt.ofDia(left, st.xDia),
                            Pt.ofDia(left + t, st.xDia), Pt.ofDia(left + t, sol.retractXDia),
                        ),
                        c.simRapid, 1.2.dp, dashed = true,
                    ),
                )
            }
        }
        add(ViewerLine(sol.finish, c.simToolpath, 2.2.dp))
    }
    val first = sol.steps.first()
    val last = sol.steps.last()
    val marks = buildList {
        add(ViewerMark(Pt.ofDia(first.z, first.xDia), c.drawT1, "1"))
        if (sol.steps.size > 1) add(ViewerMark(Pt.ofDia(last.z, last.xDia), c.drawT2, "${sol.steps.size}"))
    }
    ViewerCard(
        lines, marks,
        listOf(
            Legend(c.drawProfile, "도면"),
            Legend(c.simRapid, "단마다 날 자리", dashed = true),
            Legend(c.simToolpath, "정삭 (기준 인선)"),
        ),
    )
}
