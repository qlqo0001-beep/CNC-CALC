package com.bmt.nosecalc.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.TriangleResult
import com.bmt.nosecalc.calc.TriangleSolution
import com.bmt.nosecalc.calc.TriangleWarning
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleFigure
import com.bmt.nosecalc.screens.triangle.TriangleState
import com.bmt.nosecalc.screens.triangle.triangleWarningText
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.ExpandableHint
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

/** 직각삼각형 */
@Composable
fun TriangleScreen() = TriangleScreen(TriangleState())

/**
 * 그림 → 경고(그림 바로 아래, 값 부족은 파랑 안내) → 아는 값 다섯 칸 → 결과 → X · Z 연동. 값이 바뀌면 바로 다시 푼다.
 * 다섯 칸은 한 묶음(키패드 "다음" 으로 a → b → c → A → B)이고, 그림의 칸을 눌러도 그 칸의 키패드가 열린다.
 * initial = 시험이 정한 처음 상태, editing = 처음부터 키패드를 열어 둘 칸(스크린샷 시험용)
 */
@Composable
internal fun TriangleScreen(initial: TriangleState, editing: TriField? = null) {
    val s = rememberSaveable(saver = TriangleState.Saver) { initial }
    val chain = rememberFieldChain(editing?.key)
    FeatureScaffold(Feature.TRIANGLE, TriangleHelp) {
        val input = s.input()
        val result = remember(input) { RightTriangle.calc(input) }
        val sol = result.solution
        val w = result.warnings.firstOrNull()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                TriangleFigure(s, result, focus = TriField.entries.firstOrNull { it.key == chain.active }, onPick = { chain.open(it.key) })
                FigureLegend()
            }
            if (w != null) WarningBanner(triangleWarningText(w), level = if (w == TriangleWarning.MISSING_INPUT) WarnLevel.Info else WarnLevel.Error)
            FieldChainHost(chain) { InputCard(s, result, s.wrong(w)) }
            if (sol != null) {
                ResultTable(s, sol)
                CoordCard(s, sol)
            }
            // 키패드가 열리면 아래 칸도 시트 위로 올라올 빈자리
            if (chain.sheetOpen) Spacer(Modifier.height(440.dp))
        }
    }
}

@Composable
private fun accent() = AppTheme.colors.features.getValue(Feature.TRIANGLE).menu

@Composable
private fun FigureLegend() {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Swatch(accent().copy(alpha = if (c.dark) .22f else .12f), accent())
        Text("넣은 값", Modifier.padding(start = 6.dp), color = c.textMuted, fontSize = 12.sp)
        Spacer(Modifier.width(18.dp))
        Swatch(c.card, if (c.dark) c.lineStrong else c.lineField)
        Text("계산값", Modifier.padding(start = 6.dp), color = c.textMuted, fontSize = 12.sp)
        Spacer(Modifier.width(18.dp))
        Text("칩을 눌러 값 넣기", color = c.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Swatch(bg: Color, border: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.size(16.dp, 12.dp).clip(shape).background(bg).border(1.5.dp, border, shape))
}

/** 다섯 칸(a · b · c / A · B) 가운데 아는 둘. 빈칸은 계산값을 자리표시로(못 풀면 "0"). wrong = 빨강으로 짚을 칸 */
@Composable
private fun InputCard(s: TriangleState, result: TriangleResult, wrong: Set<TriField>) {
    val c = AppTheme.colors
    val count = TriField.entries.count { s.given(it) }
    val ok = count == 2 && result.solution != null
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("아는 값", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                "넣은 값 $count / 2",
                color = if (ok) accent() else c.textMuted,
                fontSize = 12.5.sp,
                fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.weight(1f))
            PillButton("모두 지우기", null, { s.clear() })
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                listOf(TriField.A_SIDE, TriField.B_SIDE, TriField.C_SIDE),
                listOf(TriField.ANGLE_A, TriField.ANGLE_B),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { TriNumberField(s, result, it, it in wrong, Modifier.weight(1f)) }
                }
            }
        }
        // 수식은 줄바꿈 없는 공백(\u00A0)으로 — 줄 끝에서 "=" 뒤가 끊기지 않게
        ExpandableHint(
            "아는 값 둘을 넣으면 나머지가 나옵니다 (변 하나는 꼭).",
            "각도 A 는 밑변과 빗변 사이, 각도 B 는 높이와 빗변 사이입니다(A\u00A0+\u00A0B\u00A0=\u00A090°). 각도 둘만으로는 크기를 알 수 없어 변 하나는 꼭 넣습니다. " +
                "셋 이상 넣으면 서로 맞는지 확인합니다.",
            Modifier.padding(top = 4.dp),
        )
    }
}

/** 칸 하나. 키패드 작은 그림은 이 칸의 변 · 각을 짚는다 */
@Composable
private fun TriNumberField(s: TriangleState, result: TriangleResult, f: TriField, error: Boolean, modifier: Modifier) {
    val v = s.value(f)
    val sol = result.solution
    NumberField(
        f.label, v, { s.set(f, it) }, modifier,
        unit = f.unit,
        placeholder = "0",
        autoValue = if (v.isEmpty() && sol != null) Fmt.fixed3(f.of(sol)) else null,
        error = error,
        id = f.key,
        figure = { TriangleFigure(s, result, it, focus = f, compact = true) },
    )
}

/** 다섯 값 표: 이름 · 값(고정폭) · 입력/계산 */
@Composable
private fun ResultTable(s: TriangleState, sol: TriangleSolution) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("결과", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        TriField.entries.forEachIndexed { i, f ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.lineCard))
            val given = s.given(f)
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, Modifier.weight(1f), color = c.textBody, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    // 길이 줄에도 ° 한 칸을 투명하게 남겨 각도 줄과 소수점 자리를 맞춘다(고정폭)
                    buildAnnotatedString {
                        append(Fmt.fixed3(f.of(sol)))
                        withStyle(SpanStyle(color = if (f.unit == "°") Color.Unspecified else Color.Transparent)) { append("°") }
                    },
                    color = if (given) c.textValue else c.valueComp,
                    fontFamily = AppTheme.mono, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(12.dp))
                Tag(if (given) "입력" else "계산", if (given) accent() else c.valueComp)
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.width(40.dp).clip(shape).background(color.copy(alpha = .12f)).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** X · Z 연동: 밑변 = Z · 높이 = X(반경) 또는 반대. 좌표가 아니라 바뀐 양(ΔX · ΔZ)이고 ΔX 는 직경값 */
@Composable
private fun CoordCard(s: TriangleState, sol: TriangleSolution) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("X · Z 연동", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Segmented(
            listOf("밑변 Z · 높이 X(반경)", "밑변 X(반경) · 높이 Z"), if (s.baseIsZ) 0 else 1, { s.baseIsZ = it == 0 },
            Modifier.fillMaxWidth(), color = c.features.getValue(Feature.TRIANGLE).title, height = 42.dp, fontSize = 14,
        )
        // CoordPair 꼴: 두 칸 사이 1dp 구분선
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DeltaCell("ΔX", "지름 변화 (직경)", Fmt.fixed3(sol.dxDia), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(1.dp).height(40.dp).background(c.lineCard))
            Spacer(Modifier.width(12.dp))
            DeltaCell("ΔZ", "Z 이동", Fmt.fixed3(sol.dz), Modifier.weight(1f))
        }
        Text(
            "ΔX = ${if (s.baseIsZ) "높이" else "밑변"} × 2 (직경값) · 부호는 가공 방향에 맞게",
            Modifier.padding(top = 8.dp),
            color = c.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp,
        )
    }
}

/** 바뀐 양 한 칸(CoordPair 칸 꼴): 위 작은 이름, 아래 축 글자 + 오른쪽 맞춤 고정폭 값(numHero). 값이 칸보다 길면 글자를 줄여 한 줄에 */
@Composable
private fun DeltaCell(axis: String, caption: String, value: String, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier) {
        Text(caption, Modifier.fillMaxWidth(), color = c.textMuted, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(axis, Modifier.alignByBaseline(), color = c.textMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            BasicText(
                value,
                Modifier.weight(1f, fill = false).alignByBaseline(),
                style = AppType.numHero.copy(color = c.valueComp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(12.sp, AppType.numHero.fontSize, 0.5.sp),
            )
        }
    }
}

/** 헤더 도움말 버튼의 사용법 */
internal val TriangleHelp = listOf(
    "밑변 a · 높이 b · 빗변 c · 각도 A · 각도 B 가운데 아는 값 둘을 넣으면 나머지가 바로 나옵니다. 빈칸에는 계산값이 '자동' 표와 함께 보입니다. 칸을 비우려면 키패드에서 C, 모두 비우려면 '모두 지우기'.",
    "각도 A 는 밑변과 빗변 사이, 각도 B 는 높이와 빗변 사이입니다(A\u00A0+\u00A0B\u00A0=\u00A090°). 각도 둘만으로는 크기를 알 수 없어 변 하나는 꼭 넣어야 합니다.",
    "셋 이상 넣으면 서로 맞는지 봅니다(길이 0.001 mm · 각도 0.001° 안). 맞지 않거나 빗변이 변보다 짧으면 그림 아래에 빨간 띠가 뜨고 계산을 멈춥니다.",
    "그림에는 넣은 값(보라 칩)과 계산값(흰 칩)이 치수선 위에 적히고, 칩을 누르면 그 값의 키패드가 열립니다. 모양은 각도 A 를 따르되, 22° 보다 납작하거나 58° 보다 가파르면 보기 좋게 눌러 그리고 왼쪽 위에 '그림 비율 아님' 을 적습니다.",
    "X · Z 연동은 좌표가 아니라 바뀐 양입니다. 밑변을 Z, 높이를 X(반경)로 보면 ΔZ(Z 이동) = 밑변, ΔX(지름 변화) = 높이 × 2(직경값)입니다. 높이가 Z 쪽이면 '밑변 X(반경) · 높이 Z' 를 고릅니다. 그림의 밑변 · 높이 칸에 Z · X 가 적혀 있습니다.",
    "예: 테이퍼 길이 Z 20 에 지름이 Ø30 → Ø40 이면 밑변 20, 높이 5(반경 차) → 각도 A 14.036°, 빗변 20.616.",
)
