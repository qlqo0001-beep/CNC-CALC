package com.bmt.nosecalc.screens.taper

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.TaperPoint
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.CoordPair
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.LabeledValue
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space

/**
 * B 화면 — 테이퍼(외경·내경 · 2중 테이퍼): 그림 요약 → (멈춘 까닭 · 값을 넣으라는 안내) → 입력 → 경고 → 보정 좌표 → 형상 → NC(G41/G42 · 직접 보정).
 * 값이 바뀌면 바로 다시 푼다(계산 버튼 없음).
 */
@Composable
internal fun SlopeScreen(s: TaperState) {
    val c = AppTheme.colors
    val kind = s.kind
    val type = s.type ?: return
    val n = kind.points
    val xs = (1..n).map { s.point("x$it") }
    val zs = (1..n).map { s.point("z$it") }
    val rs = (1..n).map { s.point("r$it") }
    val ang = s.point("ang")
    val start = s.point("start")
    val end = s.point("end")
    val noseR = s.shared("noseR")
    val zClear = s.shared("zClear")
    val standard = NoseComp.standardTip(kind.inner, s.reverse)
    val form = SlopeForm(
        kind, type, s.reverse,
        noseR.toDoubleOrNull() ?: Double.NaN,
        s.tip,
        xs.map { it.toDoubleOrNull() },
        zs.map { it.toDoubleOrNull() },
        ang.toDoubleOrNull(),
        zClear.toDoubleOrNull() ?: 0.0,
        rs.map { it.toDoubleOrNull() ?: 0.0 },
        start.toDoubleOrNull(),
        end.toDoubleOrNull(),
    )
    val result = remember(form) { TaperSolve.solve(form) }
    val sol = result.solution
    val tip = s.tip ?: standard
    val (frontV, backV) = legsVertical(kind, type)
    // 치수를 아직 다 넣지 않았으면(0 · 빈칸) 빨간 멈춤 대신 파란 안내. 멈춘 경고는 그 칸들을 빨간 테로 짚는다
    val waiting = sol == null && (TaperWarning.MISSING_INPUT in result.warnings || form.waiting)
    val flagged = if (sol == null && !waiting) result.warnings.flatMap { taperWarningFields(it, form) }.toSet() else emptySet()
    fun auto(text: String, v: Double?) = if (waiting) null else autoValue(text, v)
    val r = if (form.noseR.isFinite()) Fmt.nc(form.noseR).removeSuffix(".") else "—"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Space.cardGap),
    ) {
        val dir = if (s.reverse) "역방향 가공 (Z+)" else "정방향 가공 (Z−)"
        SummaryCard(kind, type, s.reverse, "$dir · 노즈 R$r · 팁 ${tip}번") { s.open = false }
        // 멈춘 까닭은 입력 카드 위에(폰 첫 화면 안에)
        if (waiting) {
            WarningBanner(taperWarningText(TaperWarning.MISSING_INPUT, form), level = WarnLevel.Info)
        } else if (sol == null) {
            result.warnings.forEach { WarningBanner(taperWarningText(it, form), level = WarnLevel.Error) }
        }

        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            NoseRPicker(noseR, error = "noseR" in flagged) { s.setShared("noseR", it) }
            Spacer(Modifier.height(14.dp))
            // 단일 테이퍼는 다섯 값 가운데 하나를 비워 계산한다(키패드 '비우기 · 자동 계산')
            val blankable = !kind.double
            Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                for (i in 0 until n) {
                    val p = sol?.points?.get(i)?.vertex
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PointBadge("T${i + 1}", pointColor(c, i), Modifier.size(38.dp, 52.dp))
                        NumberField(
                            "X${i + 1}", xs[i], { s.setPoint("x${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", autoValue = auto(xs[i], p?.xDia), error = "x${i + 1}" in flagged,
                            tag = "Ø직경", hint = "직경값 — 도면 Ø 그대로 (Ø40 → 40)", blankable = blankable,
                        )
                        NumberField(
                            "Z${i + 1}", zs[i], { s.setPoint("z${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", autoValue = auto(zs[i], p?.z), error = "z${i + 1}" in flagged,
                            hint = "Z0 = 부품 오른쪽 끝면 · 척 쪽은 − (예: −5)", blankable = blankable,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (n == 3) 8.dp else Space.fieldGap)) {
                    for (i in 0 until n) {
                        NumberField(
                            "T${i + 1} 모서리 R", rs[i], { s.setPoint("r${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", placeholder = "기본 0", error = "r${i + 1}" in flagged, hint = "비우면 0 (날카로운 꼭짓점)",
                        )
                    }
                }
                if (!kind.double) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        NumberField(
                            "각도 (Z축 기준)", ang, { s.setPoint("ang", it) }, Modifier.weight(1f),
                            unit = "°", autoValue = auto(ang, sol?.anglesDeg?.first()), error = "ang" in flagged,
                            hint = "Z축(가로선)에서 잰 한쪽 각 · 도면이 꼭지각이면 ÷2 · X축 기준이면 90 − 값", blankable = true,
                        )
                        ZClearField(zClear, "zClear" in flagged, Modifier.weight(1f)) { s.setShared("zClear", it) }
                    }
                    ZClearNote(sol, form, r)
                }
                // 시작점·끝점: 그 요소가 단면이면 X(직경), 수평이면 Z. 넣었을 때만 그 요소를 깎는다.
                // 끝점을 비우면 마지막 T 점(모서리 R 이면 그 끝)에서 끝나 그 좌표를 자동 값으로, 시작점을 비우면 "안 깎음"
                val last = sol?.cutEnd
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField(
                        if (frontV) "시작 X (단면 바깥 Ø)" else "시작 Z (앞 끝)", start, { s.setPoint("start", it) }, Modifier.weight(1f),
                        unit = "mm", placeholder = "안 깎음", error = "start" in flagged,
                        hint = "T1 앞 요소가 시작하는 좌표 · 비우면 그 요소는 안 깎고 연장선에서 진입",
                    )
                    // 역방향은 끝 요소가 척 반대쪽이다
                    NumberField(
                        when {
                            backV -> "끝 X (단면 끝 Ø)"
                            s.reverse -> "끝 Z (뒤 끝)"
                            else -> "끝 Z (뒤 끝 · 척 쪽)"
                        },
                        end, { s.setPoint("end", it) }, Modifier.weight(1f),
                        unit = "mm", autoValue = auto(end, last?.let { if (backV) it.xDia else it.z }), error = "end" in flagged,
                        hint = (if (s.reverse) "T$n 뒤 요소의 실제 끝 좌표" else "척·턱 쪽 실제 끝 좌표") + " · 비우면 T$n 에서 끝남 (자동 값)",
                    )
                }
                if (kind.double) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        ZClearField(zClear, "zClear" in flagged, Modifier.weight(1f)) { s.setShared("zClear", it) }
                        TipField(tip, standard, { s.tip = it }, Modifier.weight(1f))
                    }
                    ZClearNote(sol, form, r)
                } else {
                    TipField(tip, standard, { s.tip = it })
                }
            }
            ExpandableHint(
                if (kind.double) "세 점의 X · Z 를 모두 넣습니다. 각도는 결과로 나옵니다."
                else "빈칸 하나는 나머지 넷으로 자동 계산합니다 (보통 각도를 비움).",
                (
                    if (kind.double) ""
                    else "X1·Z1·X2·Z2·각도 가운데 하나를 비우면(키패드의 '비우기 · 자동 계산') 나머지 넷으로 계산합니다. 보통 각도를 비워 둡니다. "
                    ) +
                    "모서리 R 은 비우면 0(날카로운 꼭짓점). 시작점·끝점은 앞뒤 요소의 바깥 끝 좌표(단면이면 X, 수평이면 Z)로, 넣었을 때만 그 요소를 깎습니다 — " +
                    "끝점을 비우면 마지막 T 점에서 끝나고, 넣으면 인선이 그 좌표까지 갑니다. 시작점을 비우면 첫 T 점에서 시작합니다. " +
                    "Z 여유는 첫 깎는 점에서 Z 로 띄운 진입 거리입니다(노즈 R 보다 작으면 노즈 R).",
                Modifier.padding(top = 4.dp),
            )
        }

        if (sol != null) {
            result.warnings.forEach {
                if (it == TaperWarning.AGAINST_TOOL) {
                    WarningBanner(taperWarningText(it, form), level = WarnLevel.Info, collapsedText = "바이트 반대쪽 면 있음 (역테이퍼 꼴) — 눌러서 자세히")
                } else {
                    WarningBanner(taperWarningText(it, form), level = WarnLevel.Warn)
                }
            }
            val g41 = s.ncTab == 0
            CompCard(sol, g41)
            SlopeViewer(sol, form.noseR)
            NcCard(
                if (g41) sol.ncG41G42 else sol.ncG40,
                if (g41) {
                    "도면 좌표에 ${sol.side} ${eulReul("${sol.side}")} 걸어 기계가 노즈R 을 보정합니다. " +
                        "기계 공구 보정 화면에 R$r · T${sol.tip} ${eulReul("${sol.tip}")} 넣으세요(첫 줄 주석). " +
                        "보정 시작·취소는 직선 이동에서 합니다. 마지막 줄 끝에서는 노즈 접점이 끝좌표에 와서 인선이 직접 보정보다 더 갈 수 있습니다 " +
                        "(끝 요소가 외경·내경처럼 수평이면 Z 로 노즈R 만큼) — 바로 뒤에 어깨·턱이 있으면 직접 보정 프로그램을 쓰세요. 이송 F 는 넣지 않았습니다."
                } else {
                    "앱이 계산한 가상 인선 ${sol.tip}번 좌표입니다 — 기계의 노즈R 보정 없이(G40) 그대로 깎습니다. 이 좌표에 G41/G42 를 또 걸면 " +
                        "노즈R 만큼 두 번 밀립니다. 모서리 원호는 보정 R(볼록 R + r · 오목 R − r), 인선은 끝좌표에 멈춥니다. 이송 F 는 넣지 않았습니다."
                },
                tabs = listOf("G41/G42 보정 프로그램", "직접 보정 (G40)"),
                tab = s.ncTab,
                onTab = { s.ncTab = it },
                programName = if (g41) "기계 보정(G41/G42) 프로그램" else "직접 보정(G40) 프로그램",
                facts = if (g41) {
                    listOf(NcFact("공구 보정 화면: R $r · T ${sol.tip} 입력", WarnLevel.Warn), NcFact("끝점에서 노즈R 만큼 더 갈 수 있음"), NcFact("이송 F 없음"))
                } else {
                    listOf(NcFact("G41/G42 걸지 말 것 (이중 보정)", WarnLevel.Error), NcFact("공구 보정 R 안 씀"), NcFact("이송 F 없음"))
                },
            )
        }
    }
}

@Composable
private fun ZClearField(value: String, error: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    NumberField(
        "Z 여유 (진입)", value, onChange, modifier, unit = "mm", placeholder = "기본 0", error = error,
        hint = "진입점까지 Z 거리 (+ 값) · 노즈 R 보다 작으면 노즈 R 만큼",
    )
}

/** Z 여유가 노즈 R 보다 작으면 진입은 노즈 R 만큼 띄운다(G41/G42 시작 이동 ≥ 노즈 R) — 왜 G00 이 그대로인지 알린다 */
@Composable
private fun ZClearNote(sol: TaperSolution?, f: SlopeForm, r: String) {
    if (sol != null && f.zClear < f.noseR) {
        WarningBanner("Z 여유가 노즈 R 보다 작아 진입을 노즈 R($r) 만큼 띄웠습니다", level = WarnLevel.Info)
    }
}

/** 끝 숫자를 읽은 소리로 목적격 조사(영 · 일 · 삼 · 육 · 칠 · 팔 = 받침 → 을, 이 · 사 · 오 · 구 → 를). G41 을 · G42 를 · T3 을 · T2 를 */
private fun eulReul(s: String): String = if (s.last() in "013678") "을" else "를"

/** 빈칸이면 계산된 값(칸에 청록 글로, "자동" 표는 라벨 줄에). 못 풀면 "" */
private fun autoValue(text: String, v: Double?): String? = if (text.isNotEmpty()) null else v?.let { Fmt.fixed3(it) } ?: ""

/**
 * 보정 좌표(가상 인선 = 직접 보정 프로그램 좌표): 점마다 X | Z 좌표 쌍 + "ΔX(직경) · ΔZ" 줄.
 * 모서리 R 이 있는 점은 R 시작·끝 접점 둘(도면 좌표를 아래에)과 원호 방향·도면 R·보정 R. 아래 각도·G00 진입점.
 * g41 = 아래 NC 가 G41/G42 프로그램 — 이 좌표를 그 프로그램에 넣으면 노즈R 이 두 번 들어간다고 위에 알린다
 */
@Composable
private fun CompCard(sol: TaperSolution, g41: Boolean) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("보정 좌표", "직접 보정(G40) 프로그램 좌표 · 팁 ${sol.tip}번") {
            Text("단위 mm · X 직경", style = AppType.caption, color = c.textMuted)
        }
        if (g41) {
            WarningBanner(
                "G41/G42 프로그램(아래 NC)은 도면 좌표를 씁니다. 이 보정 좌표를 그 프로그램에 넣으면 노즈R 이 두 번 들어갑니다.",
                level = WarnLevel.Info,
            )
        }
        sol.points.forEachIndexed { i, p ->
            Rule()
            Row(verticalAlignment = if (p.corner == null) Alignment.CenterVertically else Alignment.Top) {
                PointBadge("T${i + 1}′", pointColor(c, i), Modifier.size(44.dp, 40.dp))
                Spacer(Modifier.width(12.dp))
                PointRows(p, Modifier.weight(1f))
            }
        }
        Rule()
        val size = AppType.numLarge.fontSize
        val entry = "X${Fmt.fixed3(sol.entry.xDia)}  Z${Fmt.fixed3(sol.entry.z)}"
        val angles = sol.anglesDeg
        if (angles.size == 1) {
            Row {
                LabeledValue("각도 (Z축 기준)", "${Fmt.fixed3(angles[0])}°", Modifier.weight(1f), size = size)
                LabeledValue("G00 진입점", entry, Modifier.weight(1.5f), size = size)
            }
        } else {
            // 2중 테이퍼: 경사 둘(그림의 각1 = T1–T2 · 각2 = T2–T3)을 나란히, 진입점은 아랫줄
            Row {
                angles.forEachIndexed { i, a ->
                    LabeledValue("각${i + 1} (Z축 기준)", "${Fmt.fixed3(a)}°", Modifier.weight(1f), size = size)
                }
            }
            LabeledValue("G00 진입점", entry, Modifier.padding(top = 10.dp), size = size)
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.padding(vertical = 9.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.lineCard))
}

@Composable
private fun PointRows(p: TaperPoint, modifier: Modifier) {
    val c = AppTheme.colors
    val corner = p.corner
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        p.nodes.forEachIndexed { k, node ->
            if (k > 0) Spacer(Modifier.height(6.dp))
            if (corner != null) SmallLabel(if (k == 0) "R 시작 (접점)" else "R 끝 (접점)")
            CoordPair(Fmt.fixed3(node.prog.xDia), Fmt.fixed3(node.prog.z), c.valueComp)
            if (corner == null) {
                SubLine("ΔX(직경)" to signed(node.dXDia), "ΔZ" to signed(node.dZ))
            } else {
                SubLine("도면" to "X${Fmt.fixed3(node.drawn.xDia)}  Z${Fmt.fixed3(node.drawn.z)}")
            }
        }
        if (corner != null) {
            Text(
                "${if (corner.cw) "G02 시계" else "G03 반시계"} · 도면 R${Fmt.fixed3(corner.r)} → 보정 R" +
                    (corner.progR?.let { Fmt.fixed3(it) } ?: " 없음 (R ≤ 노즈R)"),
                Modifier.padding(top = 2.dp),
                style = AppType.label,
                color = c.simArc,
            )
        }
    }
}

/**
 * 좌표 쌍 아래 작은 줄: 이름(sans) + 값(고정폭) 쌍을 " · " 로 잇는다.
 * 한글 이름까지 고정폭으로 쓰면 줄 가운데서 대체 글꼴로 빠지므로 글꼴을 나눈다
 */
@Composable
private fun SubLine(vararg parts: Pair<String, String>) {
    val sans = AppTheme.sans
    Text(
        buildAnnotatedString {
            parts.forEachIndexed { i, (name, v) ->
                if (i > 0) append("  ·  ")
                withStyle(SpanStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold)) { append(name) }
                append(" $v")
            }
        },
        style = AppType.numSmall,
        color = AppTheme.colors.textMuted,
    )
}

@Composable
internal fun SmallLabel(text: String) {
    Text(text, color = AppTheme.colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * 도면선(앞뒤 요소·모서리 R 포함) · 노즈 중심 경로 · 보정점마다 노즈 원 · 직접 보정 프로그램(가상 인선) 경로(진입점에서 G01 포함) · G00 후퇴.
 * 시작·끝점을 비웠으면 프로그램이 그 요소를 깎지 않으므로(진입점에서 첫 꺾임점′ 으로, 마지막 꺾임점′ 에서 후퇴) 그 토막은 그리지 않는다.
 */
@Composable
private fun SlopeViewer(sol: TaperSolution, r: Double) {
    val c = AppTheme.colors
    val skip = if (sol.fromStart) 0 else 1
    val tail = if (sol.toEnd) 0 else 1
    val program = sol.program.segs.drop(skip).dropLast(tail)
    val tv = NoseComp.tipVector(sol.tip, r)
    val lines = buildList {
        add(ViewerLine(sol.drawing, c.drawProfile, 2.5.dp))
        add(ViewerLine(Contour(sol.center.segs.drop(skip).dropLast(tail)), c.sky, 1.4.dp, dashed = true))
        for (p in sol.points) for (node in p.nodes) add(ViewerLine(circle(node.prog - tv, r), c.sky, 1.2.dp))
        add(ViewerLine(Contour.polyline(sol.entry, program.first().start), c.simToolpath, 2.2.dp)) // 진입점에서 첫 점까지는 G01 이송
        add(ViewerLine(Contour.polyline(program.last().end, sol.exit), c.simRapid, 1.6.dp, dashed = true))
        add(ViewerLine(Contour(program), c.simToolpath, 2.2.dp))
    }
    val marks = sol.points.flatMapIndexed { i, p ->
        // T 점은 넣은 꼭짓점(모서리 R 이 있으면 이론 꼭짓점) 그대로. 테두리가 바로 옆 보정점(T1′ 등) 바탕에 가리지 않게 보정점을 먼저 그린다
        p.nodes.map { ViewerMark(it.prog, c.simToolpath) } + ViewerMark(p.vertex, pointColor(c, i), "T${i + 1}")
    } + ViewerMark(sol.entry, c.simRapid, "진입")
    ViewerCard(
        lines, marks,
        listOf(
            Legend(c.drawProfile, "도면"),
            Legend(c.sky, "노즈 중심", dashed = true),
            Legend(c.simToolpath, "직접 보정(G40) 경로"),
            Legend(c.simRapid, "급송", dashed = true),
        ),
    )
}

/** 노즈 원(반원 둘) */
internal fun circle(o: Pt, r: Double): Contour {
    val a = o + Pt(r, 0.0)
    val b = o - Pt(r, 0.0)
    return Contour(listOf(Arc(a, b, o, cw = false), Arc(b, a, o, cw = false)))
}
