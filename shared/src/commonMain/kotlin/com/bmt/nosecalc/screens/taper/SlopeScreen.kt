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
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperPoint
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.ViewerLine
import com.bmt.nosecalc.ui.ViewerMark
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme

/**
 * B 화면 — 테이퍼(외경·내경 · 2중 테이퍼): 그림 요약 → 입력 → 경고 → 보정 좌표 → 형상 → NC(G41/G42 · 직접 보정).
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
        zClear.toDoubleOrNull() ?: 1.0,
        rs.map { it.toDoubleOrNull() ?: 0.0 },
        start.toDoubleOrNull(),
        end.toDoubleOrNull(),
    )
    val result = remember(form) { TaperSolve.solve(form) }
    val sol = result.solution
    val tip = s.tip ?: standard
    val side = Side.of(kind.inner, s.reverse)
    val (frontV, backV) = legsVertical(kind, type)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dir = if (s.reverse) "역방향 가공 (Z+)" else "정방향 가공 (Z−)"
        SummaryCard(kind, type, s.reverse, "$dir · 팁 T$tip · $side") { s.open = false }

        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            NoseRPicker(noseR) { s.setShared("noseR", it) }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until n) {
                    val p = sol?.points?.get(i)?.vertex
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PointBadge("T${i + 1}", pointColor(c, i), Modifier.size(38.dp, 48.dp))
                        NumberField(
                            "X${i + 1} (직경)", xs[i], { s.setPoint("x${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", autoValue = auto(xs[i], p?.xDia),
                        )
                        NumberField(
                            "Z${i + 1}", zs[i], { s.setPoint("z${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", autoValue = auto(zs[i], p?.z),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (n == 3) 8.dp else 12.dp)) {
                    for (i in 0 until n) {
                        NumberField(
                            "T${i + 1} 모서리 R", rs[i], { s.setPoint("r${i + 1}", it) }, Modifier.weight(1f),
                            unit = "mm", placeholder = "0",
                        )
                    }
                }
                if (!kind.double) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            "각도", ang, { s.setPoint("ang", it) }, Modifier.weight(1f),
                            unit = "°", autoValue = auto(ang, sol?.anglesDeg?.first()),
                        )
                        NumberField("Z 여유 (진입)", zClear, { s.setShared("zClear", it) }, Modifier.weight(1f), unit = "mm", placeholder = "1.0")
                    }
                }
                // 시작점·끝점: 그 요소가 단면이면 X(직경), 수평이면 Z. 끝점을 비우면 5 mm 떨어진 곳까지 깎는다(그 값을 흐리게).
                // 시작점을 비우면 그 요소는 깎지 않고 연장선에서 들어가므로 그림의 5 mm 값 대신 "안 깎음"
                val last = sol?.drawing?.segs?.lastOrNull()?.end
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        "시작점 ${if (frontV) "X" else "Z"} (T1 앞)", start, { s.setPoint("start", it) }, Modifier.weight(1f),
                        unit = "mm", placeholder = "안 깎음",
                    )
                    NumberField(
                        "끝점 ${if (backV) "X" else "Z"} (T$n 뒤)", end, { s.setPoint("end", it) }, Modifier.weight(1f),
                        unit = "mm", autoValue = auto(end, last?.let { if (backV) it.xDia else it.z }),
                    )
                }
                if (kind.double) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Z 여유 (진입)", zClear, { s.setShared("zClear", it) }, Modifier.weight(1f), unit = "mm", placeholder = "1.0")
                        TipField(tip, standard, { s.tip = it }, Modifier.weight(1f))
                    }
                } else {
                    TipField(tip, standard, { s.tip = it })
                }
            }
            Text(
                (
                    if (kind.double) "세 점의 X·Z 를 모두 넣습니다. 각도는 결과로 나옵니다. "
                    else "X1·Z1·X2·Z2·각도 가운데 하나를 비우면(키패드 C → 확인) 나머지 넷으로 계산합니다. 보통 각도를 비워 둡니다. "
                    ) +
                    "모서리 R 은 비우면 0(날카로운 꼭짓점). 시작점·끝점은 앞뒤 요소의 바깥 끝 좌표(단면이면 X, 수평이면 Z)입니다 — " +
                    "끝점에 척·턱 쪽 실제 끝좌표를 넣으면 인선이 거기서 멈춥니다. 시작점을 비우면 그 요소는 깎지 않고 경사(또는 그 요소) 연장선에서 들어갑니다.",
                Modifier.padding(top = 12.dp),
                color = c.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }

        result.warnings.forEach {
            WarningBanner(taperWarningText(it, kind), level = if (sol == null) WarnLevel.Error else WarnLevel.Warn)
        }
        if (sol != null) {
            CompCard(sol)
            SlopeViewer(sol, form.noseR)
            val r = Fmt.nc(form.noseR).removeSuffix(".")
            NcCard(
                if (s.ncTab == 0) sol.ncG41G42 else sol.ncG40,
                if (s.ncTab == 0) {
                    "도면 좌표에 ${sol.side} 를 걸어 기계가 노즈R 을 보정합니다. 기계 공구 보정 화면에 R $r · T ${sol.tip} 을 넣으세요(첫 줄 주석). " +
                        "보정 시작·취소는 직선 이동에서 합니다. 마지막 줄 끝에서는 노즈 접점이 끝좌표에 와서 인선이 직접 보정보다 더 갈 수 있습니다 " +
                        "(끝 요소가 외경·내경처럼 수평이면 Z 로 노즈R 만큼) — 바로 뒤에 어깨·턱이 있으면 직접 보정 프로그램을 쓰세요. 이송 F 는 넣지 않았습니다."
                } else {
                    "앱이 계산한 가상 인선 T${sol.tip} 좌표입니다 — 기계의 노즈R 보정 없이(G40) 그대로 깎습니다. 이 좌표에 G41/G42 를 또 걸면 " +
                        "노즈R 만큼 두 번 밀립니다. 모서리 원호는 보정 R(볼록 R + r · 오목 R − r), 인선은 끝좌표에 멈춥니다. 이송 F 는 넣지 않았습니다."
                },
                tabs = listOf("G41/G42 보정 프로그램", "직접 보정 (G40)"),
                tab = s.ncTab,
                onTab = { s.ncTab = it },
            )
        }
    }
}

/** 빈칸이면 계산된 값(흐리게 칸에, "자동" 표는 라벨 줄에) */
private fun auto(text: String, v: Double?): String? = if (text.isNotEmpty()) null else v?.let { Fmt.fixed3(it) } ?: ""

/**
 * 보정 좌표(가상 인선 = 직접 보정 프로그램 좌표): 점마다 X·Z 큰 숫자 + ΔX·ΔZ.
 * 모서리 R 이 있는 점은 R 시작·끝 접점 둘(도면 좌표를 아래에)과 원호 방향·도면 R·보정 R. 아래 각도·진입점
 */
@Composable
private fun CompCard(sol: TaperSolution) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("보정 좌표", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("직접 보정 좌표 · 가상 인선 T${sol.tip}", color = c.textMuted, fontSize = 12.5.sp)
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
                if (corner == null) "ΔX ${signed(node.dXDia)}   ΔZ ${signed(node.dZ)}"
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
 * 도면선(앞뒤 요소·모서리 R 포함) · 노즈 중심 경로 · 보정점마다 노즈 원 · 직접 보정 프로그램(가상 인선) 경로 · G00 진입·후퇴.
 * 시작점을 비웠으면 프로그램이 진입점에서 바로 첫 꺾임점′ 으로 가므로 시작 요소 토막은 그리지 않는다.
 */
@Composable
private fun SlopeViewer(sol: TaperSolution, r: Double) {
    val c = AppTheme.colors
    val skip = if (sol.fromStart) 0 else 1
    val program = sol.program.segs.drop(skip)
    val tv = NoseComp.tipVector(sol.tip, r)
    val lines = buildList {
        add(ViewerLine(sol.drawing, c.drawProfile, 2.5.dp))
        add(ViewerLine(Contour(sol.center.segs.drop(skip)), c.sky, 1.4.dp, dashed = true))
        for (p in sol.points) for (node in p.nodes) add(ViewerLine(circle(node.prog - tv, r), c.sky, 1.2.dp))
        add(ViewerLine(Contour.polyline(sol.entry, program.first().start), c.simRapid, 1.6.dp, dashed = true))
        add(ViewerLine(Contour.polyline(program.last().end, sol.exit), c.simRapid, 1.6.dp, dashed = true))
        add(ViewerLine(Contour(program), c.simToolpath, 2.2.dp))
    }
    val marks = sol.points.flatMapIndexed { i, p ->
        val at = if (p.corner == null) p.vertex else p.nodes.first().drawn
        listOf(ViewerMark(at, pointColor(c, i), "T${i + 1}")) + p.nodes.map { ViewerMark(it.prog, c.simToolpath) }
    } + ViewerMark(sol.entry, c.simRapid, "진입")
    ViewerCard(
        lines, marks,
        listOf(
            Legend(c.drawProfile, "도면"),
            Legend(c.sky, "노즈 중심", dashed = true),
            Legend(c.simToolpath, "직접 보정 경로"),
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
