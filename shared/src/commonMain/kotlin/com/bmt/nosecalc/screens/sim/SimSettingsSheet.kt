package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StockAuto
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.calc.sim.ToolShape
import com.bmt.nosecalc.calc.sim.ToolStep
import com.bmt.nosecalc.screens.taper.TipSheet
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.SheetTitle
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.onFill

/**
 * 시뮬레이션 설정(설계 7.8 — 원본에 없어 우리가 정함): 한 칸 묶음(`FieldChainHost`), 빈칸 = 자동/가정 값(`autoValue`).
 * '소재' [봉재 지름 ØD] [돌출 길이] / [기장여유 (끝면 Z)] [구멍 지름 Ød] / [척 지름] · '기계' [급송 X] [급송 Z] / [공구 교환] [최대 회전수] /
 * [원점 X] [원점 Z] · 급송 경로 · '프로그램 읽기' 소수점 없는 수 · 블록 스킵 · G92 끝 빼기 · '보기' 스위치 다섯.
 */
@Composable
internal fun SimSettingsSheet(sim: SimState, run: SimRun, bad: Set<StockField>, onDismiss: () -> Unit) {
    val auto = remember(run) { StockAuto.of(run) }
    val d = MachineSetup()
    BottomSheet(onDismiss, maxHeightFraction = 0.92f) {
        SheetTitle("시뮬레이션 설정", action = { PillButton("닫기", AppIcons.Close, onDismiss) })
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            FieldChainHost(rememberFieldChain()) {
                Section("소재", "빈칸은 프로그램의 이송(G01~G03 · 사이클)이 닿은 곳에서 잡은 자동 값입니다.")
                Pair2 {
                    StockNum(sim, StockField.DIA, "봉재 지름 ØD", auto?.dia, bad)
                    StockNum(sim, StockField.LENGTH, "돌출 길이", auto?.length, bad)
                }
                Pair2 {
                    StockNum(sim, StockField.FACE, "기장여유 (끝면 Z)", auto?.faceZ, bad)
                    StockNum(sim, StockField.BORE, "구멍 지름 Ød", auto?.boreDia ?: 0.0, bad)
                }
                Pair2 {
                    val dia = fieldValue(sim.stockText[StockField.DIA], stockOk(StockField.DIA)).orNull() ?: auto?.dia
                    StockNum(sim, StockField.CHUCK, "척 지름", dia?.let { StockAuto.chuckOf(it) }, bad)
                    Spacer(Modifier.weight(1f))
                }
                Section("기계", "빈칸은 가정 값입니다. 기계 사양서 값을 넣으면 가공 시간이 맞습니다.")
                Pair2 {
                    MachineNum(sim, MachineField.RAPID_X, "급송 X", "m/min", d.rapidX / 1000)
                    MachineNum(sim, MachineField.RAPID_Z, "급송 Z", "m/min", d.rapidZ / 1000)
                }
                Pair2 {
                    MachineNum(sim, MachineField.TOOL_CHANGE, "공구 교환", "초", d.toolChangeSec)
                    MachineNum(sim, MachineField.MAX_RPM, "최대 회전수", "rpm", d.maxRpm)
                }
                Pair2 {
                    val home = sim.runSetup().home(auto)
                    MachineNum(sim, MachineField.HOME_X, "원점 X (직경)", "mm", home.xDia)
                    MachineNum(sim, MachineField.HOME_Z, "원점 Z", "mm", home.z)
                }
                Label("급송 경로")
                Segmented(
                    listOf("축마다 따로 (꺾임)", "직선"),
                    if (sim.rapidPath == RapidPath.DOGLEG) 0 else 1,
                    { sim.changeRapidPath(if (it == 0) RapidPath.DOGLEG else RapidPath.LINEAR) },
                    Modifier.fillMaxWidth(),
                    fontSize = 14,
                )
                Section("프로그램 읽기", null)
                Label("소수점 없는 수")
                Segmented(
                    listOf("FANUC 표준 (X44 = 0.044)", "계산기식 (X44 = 44 mm)"),
                    if (sim.decimal == DecimalMode.STANDARD) 0 else 1,
                    { sim.changeDecimal(if (it == 0) DecimalMode.STANDARD else DecimalMode.CALCULATOR) },
                    Modifier.fillMaxWidth(),
                    fontSize = 13,
                )
                ToggleRow("블록 스킵 ( / 로 시작하는 블록 건너뜀)", sim.blockSkip) { sim.changeBlockSkip(it) }
                Pair2 {
                    val v = sim.machineText[MachineField.G92_CHAMFER].orEmpty()
                    NumberField(
                        "G92 끝 빼기 (리드 배수)", v, { sim.setMachine(MachineField.G92_CHAMFER, it) }, Modifier.weight(1f),
                        unit = "L", autoValue = "0", tag = "없음",
                        error = fieldValue(v, machineOk(MachineField.G92_CHAMFER)) == FieldValue.Bad,
                        hint = "나사 끝에서 45° 로 빠지는 길이(리드의 배수, 0~9.9). 빈칸 = 빼지 않고 직각으로", blankable = true, id = "m.g92",
                    )
                    Spacer(Modifier.weight(1f))
                }
                Section("보기", null)
                val f = sim.view
                ToggleRow("고스트 원", f.ghost) { sim.changeView(f.copy(ghost = it)) }
                ToggleRow("노즈 중심 경로", f.nose) { sim.changeView(f.copy(nose = it)) }
                ToggleRow("급송 점선", f.rapid) { sim.changeView(f.copy(rapid = it)) }
                ToggleRow("황삭 층 · 사이클 이동", f.layers) { sim.changeView(f.copy(layers = it)) }
                ToggleRow("남은 소재 칠하기", f.fill) { sim.changeView(f.copy(fill = it)) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, note: String?) {
    val c = AppTheme.colors
    Column(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
        Text(title, color = c.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (note != null) Text(keepWords(note), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 6.dp), color = AppTheme.colors.fieldNormal.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Pair2(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun RowScope.StockNum(sim: SimState, f: StockField, label: String, auto: Double?, bad: Set<StockField>) {
    val v = sim.stockText[f].orEmpty()
    NumberField(
        label, v, { sim.setStock(f, it) }, Modifier.weight(1f),
        unit = "mm",
        autoValue = auto?.let(::numText) ?: "",
        error = f in bad,
        hint = when (f) {
            StockField.DIA -> "봉재 바깥지름. 빈칸 = 가장 큰 이송 X 를 1 mm 단위로 올림"
            StockField.LENGTH -> "척 면에서 끝면까지. 빈칸 = 가장 작은 이송 Z 까지 + 10 mm"
            StockField.FACE -> "끝면의 Z(Z0 보다 앞이면 +). 빈칸 = 가장 큰 이송 Z(0 아래면 0)"
            StockField.BORE -> "미리 뚫린 구멍. 빈칸 = 안쪽 공구 이송이 있으면 그 가장 작은 X − 2, 없으면 통짜(0) · 봉재 지름보다 작게"
            StockField.CHUCK -> "공구가 닿는지 보는 척 지름. 빈칸 = 봉재 지름 × 2.5(100~300)"
        },
        blankable = true,
        id = "s.${f.key}",
    )
}

@Composable
private fun RowScope.MachineNum(sim: SimState, f: MachineField, label: String, unit: String, assumed: Double) {
    val v = sim.machineText[f].orEmpty()
    NumberField(
        label, v, { sim.setMachine(f, it) }, Modifier.weight(1f),
        unit = unit,
        autoValue = numText(assumed),
        tag = if (f == MachineField.HOME_X || f == MachineField.HOME_Z) null else "가정",
        error = fieldValue(v, machineOk(f)) == FieldValue.Bad,
        hint = when (f) {
            MachineField.RAPID_X -> "X 축 슬라이드(반경 방향) 급송 속도. 빈칸 = 20 m/min(가정)"
            MachineField.RAPID_Z -> "Z 축 급송 속도. 빈칸 = 24 m/min(가정)"
            MachineField.TOOL_CHANGE -> "공구 번호가 바뀌는 T 마다 더하는 시간. 빈칸 = 1 초(가정)"
            MachineField.MAX_RPM -> "주축 최대 회전수 — G50 S 와 함께 작은 쪽으로 자름. 빈칸 = 4000 rpm(가정)"
            MachineField.HOME_X -> "G28 원점 · 시작 자리(직경). 빈칸 = max(200, 소재 Ø + 100)"
            MachineField.HOME_Z -> "G28 원점 · 시작 자리. 빈칸 = 끝면 + 100"
            MachineField.G92_CHAMFER -> null
        },
        blankable = true,
        id = "m.${f.key}",
    )
}

/** 스위치 한 줄(48dp — 줄 전체를 누른다). 켜짐 = 청록 */
@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(keepWords(label), Modifier.weight(1f), color = c.textBody, fontSize = 14.5.sp)
        Spacer(Modifier.width(10.dp))
        val track = RoundedCornerShape(50)
        Box(
            Modifier.size(46.dp, 28.dp).clip(track).background(if (on) c.teal else c.field).border(1.5.dp, if (on) c.teal else c.lineStrong, track),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = if (on) 21.dp else 3.dp).size(22.dp).clip(CircleShape)
                    .background(if (on) c.onFill(c.teal) else c.lineStrong),
            )
        }
        Text(if (on) "켬" else "끔", Modifier.width(30.dp).padding(start = 6.dp), color = if (on) c.tealDeep else c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 공구 설정(설계 7.8): 프로그램이 쓴 보정 번호마다 카드(머리 'T0101 · 보정 01', 프로그램에 없는 번호는 흐리게 아래).
 * 종류(바이트 · 홈 · 나사 · 둥근) → [노즈 R] [팁 번호](테이퍼의 팁 번호 시트를 그대로 부른다) → 바이트: 인서트 C · D · V · T + [절입각 κr] /
 * 홈: [폭] / 나사: [각도]. G41/G42 를 쓰는 번호인데 노즈 R · 팁이 빈칸이면 빨간 테 + '보정에 필요합니다'(TOOL_DATA_MISSING).
 * 칸 글은 시트를 연 동안 쓴 그대로 두고, 고칠 때마다 숫자로 공구표에 넣는다(공구표는 앱을 켠 동안만 — 14.2-13).
 */
@Composable
internal fun ToolSettingsSheet(sim: SimState, run: SimRun, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val used = run.offsetsUsed
    val all = used + sim.tools.keys.filter { it !in used }.sorted()
    val toolNo = remember(run) { run.steps.filterIsInstance<ToolStep>().associate { it.offset to it.toTool } }
    val texts = remember { mutableStateMapOf<String, String>() }
    // 쓴 글이 있으면 그대로(“0.” 처럼 치는 중인 글), 없으면 공구표 값
    fun text(n: Int, k: String, v: Double?): String = texts["$n.$k"] ?: v?.let(::numText) ?: ""
    fun update(n: Int, f: (ToolOffset) -> ToolOffset) {
        sim.tools = sim.tools + (n to f(sim.tools[n] ?: ToolOffset()))
    }
    BottomSheet(onDismiss, maxHeightFraction = 0.92f) {
        SheetTitle("공구 설정", action = { PillButton("닫기", AppIcons.Close, onDismiss) })
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (all.isEmpty()) {
                Text(keepWords("프로그램에 T(공구 · 보정 번호)가 없습니다. T0101 처럼 쓰면 여기에 카드가 생깁니다."), color = c.textBody, fontSize = 14.5.sp)
            }
            FieldChainHost(rememberFieldChain()) {
                for (n in all) {
                    val t = sim.tools[n] ?: ToolOffset()
                    val inUse = n in used
                    val comp = n in run.compOffsets
                    val missing = comp && (t.noseR == null || t.tip == null)
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .alpha(if (inUse) 1f else 0.6f)
                            .clip(shape)
                            .background(c.card)
                            .border(if (missing) 2.dp else 1.dp, if (missing) c.error.label else c.lineCard, shape)
                            .padding(12.dp),
                    ) {
                        val tn = toolNo[n]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "T${(tn ?: n).toString().padStart(2, '0')}${n.toString().padStart(2, '0')} · 보정 ${n.toString().padStart(2, '0')}",
                                Modifier.weight(1f), color = c.textStrong, fontFamily = AppTheme.mono, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            )
                            if (!inUse) Text("프로그램에 없음", color = c.textMuted, fontSize = 12.5.sp)
                            if (comp) Text("G41/G42", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Segmented(
                            listOf("바이트", "홈", "나사", "둥근"), t.kind.ordinal,
                            { k -> update(n) { it.copy(kind = ToolKind.entries[k]) } },
                            Modifier.fillMaxWidth(), height = 44.dp, fontSize = 14,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberField(
                                "노즈 R (공구 반경)", text(n, "r", t.noseR),
                                { v ->
                                    texts["$n.r"] = v
                                    update(n) { it.copy(noseR = v.toDoubleOrNull()?.takeIf { r -> r >= 0 && r <= 50 }) }
                                },
                                Modifier.weight(1f), unit = "mm", error = missing && t.noseR == null,
                                hint = "인서트 날끝 반경(기계 공구 보정 화면의 R)", blankable = true, id = "t$n.r",
                            )
                            TipBox(t.tip, missing && t.tip == null, Modifier.weight(1f)) { tip -> update(n) { it.copy(tip = tip) } }
                        }
                        when (t.kind) {
                            ToolKind.TURN -> {
                                Spacer(Modifier.height(10.dp))
                                Text("인서트 모양", color = c.fieldNormal.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (s in InsertShape.entries) {
                                        InsertChip("${s.name} ${s.epsDeg.toInt()}°", t.insert == s, Modifier.weight(1f)) { update(n) { it.copy(insert = s) } }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NumberField(
                                        "절입각 κr", text(n, "k", t.kappaDeg),
                                        { v ->
                                            texts["$n.k"] = v
                                            update(n) { it.copy(kappaDeg = v.toDoubleOrNull()?.takeIf { k -> k > 0 && k < 180 }) }
                                        },
                                        Modifier.weight(1f), unit = "°", autoValue = numText(ToolShape.kappaOf(t.insert)),
                                        hint = "주절삭날과 이송 방향 사이 각(카탈로그 KAPR). 빈칸 = C 95° · D · V · T 93°(가정)", blankable = true, id = "t$n.k",
                                    )
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            ToolKind.GROOVE -> {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NumberField(
                                        "홈 폭", text(n, "w", t.width),
                                        { v ->
                                            texts["$n.w"] = v
                                            update(n) { it.copy(width = v.toDoubleOrNull()?.takeIf { w -> w > 0 && w <= 100 }) }
                                        },
                                        Modifier.weight(1f), unit = "mm", autoValue = "",
                                        hint = "홈 인서트 날 폭. 팁 3 = 왼쪽 모서리 기준 · 팁 4 = 오른쪽", blankable = true, id = "t$n.w",
                                    )
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            ToolKind.THREAD -> {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NumberField(
                                        "나사 각도", text(n, "a", t.threadDeg),
                                        { v ->
                                            texts["$n.a"] = v
                                            update(n) { it.copy(threadDeg = v.toDoubleOrNull()?.takeIf { a -> a > 0 && a < 180 }) }
                                        },
                                        Modifier.weight(1f), unit = "°", autoValue = "60",
                                        hint = "나사 인서트 끝각(미터 60° · 관용 55°)", blankable = true, id = "t$n.a",
                                    )
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            ToolKind.ROUND -> {}
                        }
                        if (missing) {
                            Text("보정에 필요합니다 — G41/G42 를 쓰는 번호입니다", Modifier.padding(top = 8.dp), color = c.error.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else if (ToolShape.outline(t.copy(noseR = t.noseR ?: 0.0, tip = t.tip ?: 0)).isEmpty()) {
                            Text(
                                keepWords("깎는 모양이 없습니다(노즈 R · 팁 · 폭) — 소재를 깎지 않고 경로만 그립니다"),
                                Modifier.padding(top = 8.dp), color = c.textMuted, fontSize = 12.5.sp,
                            )
                        }
                    }
                }
            }
            Text(
                keepWords("공구표는 앱을 켠 동안만 기억합니다. 홀더 몸통은 보지 않고 인서트 모양만으로 소재를 깎습니다."),
                Modifier.padding(bottom = 12.dp), color = c.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
        }
    }
}

/** 팁 번호 칸: 빈칸이면 '—'(처음은 빈칸 — 사용자 결정). 누르면 테이퍼의 팁 번호 시트(표준을 고르면 그 번호) */
@Composable
private fun TipBox(tip: Int?, error: Boolean, modifier: Modifier, onPick: (Int) -> Unit) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (error) Icon(AppIcons.Stop, null, Modifier.padding(end = 4.dp).size(16.dp), tint = c.error.label)
            Text("팁 번호", color = if (error) c.error.label else c.fieldNormal.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(if (error) c.error.bg else c.fieldNormal.bg)
                .border(if (error) 2.dp else 1.5.dp, if (error) c.error.label else c.fieldNormal.border, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tip?.let { "${it}번" } ?: "—", color = if (tip != null) c.textValue else c.textHint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("고르기", color = c.textMuted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
    if (open) {
        // 빈칸은 null 그대로 — -1 을 넘기면 그림의 tipVector(-1) 이 던져 앱이 꺼졌다(10-28 사용자 "팁번호 설정시 팅기는 문제")
        TipSheet(tip, STANDARD_TIP, onPick = {
            onPick(it ?: STANDARD_TIP)
            open = false
        }) { open = false }
    }
}

/** 팁 시트의 '표준'(외경 정방향 3번) */
private const val STANDARD_TIP = 3

@Composable
private fun InsertChip(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (on) c.teal else c.field)
            .border(1.5.dp, if (on) c.teal else c.lineField, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) c.onFill(c.teal) else c.textValue, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
