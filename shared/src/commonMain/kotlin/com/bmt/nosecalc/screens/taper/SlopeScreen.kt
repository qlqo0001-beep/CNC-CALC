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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dir = if (s.reverse) "역방향 가공 (Z+)" else "정방향 가공 (Z−)"
        SummaryCard(kind, type, s.reverse, "$dir · 노즈 R$r · 팁 ${tip}번") { s.open = false }
        // 멈춘 까닭은 입력 카드 위에(폰 첫 화면 안에)
        if (waiting) {
            WarningBanner(taperWarningText(TaperWarning.MISSING_INPUT, form), level = WarnLevel.Info)
        } else if (sol == null) {
            result.warnings.forEach { WarningBanner(taperWarningText(it, form), level = WarnLevel.Error) }
        }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            NoseRPicker(noseR, error = "noseR" in flagged) { s.setShared("noseR", it) }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until n) {
                    val p = sol?.points?.get(i)?.vertex
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PointBadge("T${i + 1}", pointColor(c, i), Modifier.size(38.dp, 52.dp))
                        NumberField(
                            "X${i + 1} (직경)", xs[i], { s.setPoint("x${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", autoValue = auto(xs[i], p?.xDia), error = "x${i + 1}" in flagged,
                        )
                        NumberField(
                            "Z${i + 1}", zs[i], { s.setPoint("z${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", autoValue = auto(zs[i], p?.z), error = "z${i + 1}" in flagged,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (n == 3) 8.dp else 12.dp)) {
                    for (i in 0 until n) {
                        NumberField(
                            "T${i + 1} 모서리 R", rs[i], { s.setPoint("r${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", placeholder = "0", error = "r${i + 1}" in flagged,
                        )
                    }
                }
                if (!kind.double) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            "각도", ang, { s.setPoint("ang", it) }, Modifier.weight(1f),
                            unit = "°", autoValue = auto(ang, sol?.anglesDeg?.first()), error = "ang" in flagged,
                        )
                        ZClearField(zClear, "zClear" in flagged, Modifier.weight(1f)) { s.setShared("zClear", it) }
                    }
                    ZClearNote(sol, form, r)
                }
                // 시작점·끝점: 그 요소가 단면이면 X(직경), 수평이면 Z. 넣었을 때만 그 요소를 깎는다.
                // 끝점을 비우면 마지막 T 점(모서리 R 이면 그 끝)에서 끝나 그 좌표를 자동 값으로, 시작점을 비우면 "안 깎음"
                val last = sol?.cutEnd
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        "시작점 ${if (frontV) "X" else "Z"} (T1 앞)", start, { s.setPoint("start", it) }, Modifier.weight(1f),
                        unit = "mm", placeholder = "안 깎음", error = "start" in flagged,
                    )
                    NumberField(
                        "끝점 ${if (backV) "X" else "Z"} (T$n 뒤)", end, { s.setPoint("end", it) }, Modifier.weight(1f),
                        unit = "mm", autoValue = auto(end, last?.let { if (backV) it.xDia else it.z }), error = "end" in flagged,
                    )
                }
                if (kind.double) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZClearField(zClear, "zClear" in flagged, Modifier.weight(1f)) { s.setShared("zClear", it) }
                        TipField(tip, standard, { s.tip = it }, Modifier.weight(1f))
                    }
                    ZClearNote(sol, form, r)
                } else {
                    TipField(tip, standard, { s.tip = it })
                }
            }
            Text(
                (
                    if (kind.double) "세 점의 X·Z 를 모두 넣습니다. 각도는 결과로 나옵니다. "
                    else "X1·Z1·X2·Z2·각도 가운데 하나를 비우면(키패드 C) 나머지 넷으로 계산합니다. 보통 각도를 비워 둡니다. "
                    ) +
                    "모서리 R 은 비우면 0(날카로운 꼭짓점). 시작점·끝점은 앞뒤 요소의 바깥 끝 좌표(단면이면 X, 수평이면 Z)로, 넣었을 때만 그 요소를 깎습니다 — " +
                    "끝점을 비우면 마지막 T 점에서 끝나고, 넣으면 인선이 그 좌표까지 갑니다. 시작점을 비우면 첫 T 점에서 시작합니다. " +
                    "Z 여유는 첫 깎는 점에서 Z 로 띄운 진입 거리입니다(노즈 R 보다 작으면 노즈 R).",
                Modifier.padding(top = 12.dp),
                color = c.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
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
    NumberField("Z 여유 (진입)", value, onChange, modifier, unit = "mm", placeholder = "0", error = error)
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
 * 보정 좌표(가상 인선 = 직접 보정 프로그램 좌표): 점마다 X·Z 큰 숫자 + ΔX·ΔZ.
 * 모서리 R 이 있는 점은 R 시작·끝 접점 둘(도면 좌표를 아래에)과 원호 방향·도면 R·보정 R. 아래 각도·진입점.
 * g41 = 아래 NC 가 G41/G42 프로그램 — 이 좌표를 그 프로그램에 넣으면 노즈R 이 두 번 들어간다고 위에 알린다
 */
@Composable
private fun CompCard(sol: TaperSolution, g41: Boolean) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
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
            Box(Modifier.padding(vertical = 9.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
            Row(verticalAlignment = if (p.corner == null) Alignment.CenterVertically else Alignment.Top) {
                PointBadge("T${i + 1}′", pointColor(c, i), Modifier.size(44.dp, 40.dp))
                Spacer(Modifier.width(12.dp))
                PointRows(p)
            }
        }
        Box(Modifier.padding(vertical = 9.dp).fillMaxWidth().height(1.dp).background(c.lineCard))
        Row {
            Column(Modifier.weight(1f)) {
                SmallLabel("각도")
                sol.anglesDeg.forEachIndexed { i, a ->
                    Text(
                        (if (sol.anglesDeg.size > 1) "각${i + 1} " else "") + "${Fmt.fixed3(a)}°",
                        color = c.textValue, fontFamily = AppTheme.mono, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1.5f)) {
                SmallLabel("진입점 (직접 보정 G00)")
                Text(
                    "X${Fmt.fixed3(sol.entry.xDia)}  Z${Fmt.fixed3(sol.entry.z)}",
                    color = c.textValue, fontFamily = AppTheme.mono, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PointRows(p: TaperPoint) {
    val c = AppTheme.colors
    val corner = p.corner
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        p.nodes.forEachIndexed { k, node ->
            if (corner != null) SmallLabel(if (k == 0) "R 시작 (접점)" else "R 끝 (접점)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                BigValue("X", Fmt.fixed3(node.prog.xDia))
                Spacer(Modifier.width(16.dp))
                BigValue("Z", Fmt.fixed3(node.prog.z))
            }
            Text(
                // 부호 없는 0.000 도 6칸으로 맞춰 줄마다 ΔZ 자리를 같게
                if (corner == null) "ΔX ${signed(node.dXDia).padStart(6)}   ΔZ ${signed(node.dZ).padStart(6)}"
                else "도면 X${Fmt.fixed3(node.drawn.xDia)}  Z${Fmt.fixed3(node.drawn.z)}",
                color = c.textMuted,
                fontFamily = AppTheme.mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (corner != null) {
            Text(
                "${if (corner.cw) "G02 시계" else "G03 반시계"} · 도면 R${Fmt.fixed3(corner.r)} → 보정 R" +
                    (corner.progR?.let { Fmt.fixed3(it) } ?: " 없음 (R ≤ 노즈R)"),
                Modifier.padding(top = 2.dp),
                color = c.simArc,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BigValue(axis: String, v: String) {
    val color = AppTheme.colors.valueComp
    Text(axis, color = color, fontFamily = AppTheme.mono, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(
        v, Modifier.width(112.dp), color = color, fontFamily = AppTheme.mono, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End, maxLines = 1,
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
