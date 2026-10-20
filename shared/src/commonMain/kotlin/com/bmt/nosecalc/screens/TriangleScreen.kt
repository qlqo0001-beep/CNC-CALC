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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.RightTriangle
import com.bmt.nosecalc.calc.TriangleSolution
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleFigure
import com.bmt.nosecalc.screens.triangle.TriangleState
import com.bmt.nosecalc.screens.triangle.triangleWarningText
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.PillButton
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.theme.AppTheme

/** 직각삼각형 */
@Composable
fun TriangleScreen() = TriangleScreen(TriangleState())

/** 그림 → 아는 값 다섯 칸 → 경고 → 결과 → X · Z 연동. 값이 바뀌면 바로 다시 푼다. initial = 시험이 정한 처음 상태 */
@Composable
internal fun TriangleScreen(initial: TriangleState) {
    val s = rememberSaveable(saver = TriangleState.Saver) { initial }
    FeatureScaffold(Feature.TRIANGLE, TriangleHelp) {
        val input = s.input()
        val result = remember(input) { RightTriangle.calc(input) }
        val sol = result.solution
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 5.dp).padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                TriangleFigure(s, sol)
                FigureLegend()
            }
            InputCard(s, sol)
            result.warnings.forEach { WarningBanner(triangleWarningText(it), level = WarnLevel.Error) }
            if (sol != null) {
                ResultTable(s, sol)
                CoordCard(s, sol)
            }
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
        Swatch(c.card, c.lineField)
        Text("계산값", Modifier.padding(start = 6.dp), color = c.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Swatch(bg: Color, border: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.size(16.dp, 12.dp).clip(shape).background(bg).border(1.5.dp, border, shape))
}

/** 다섯 칸 가운데 아는 둘. 빈칸은 계산값을 자리표시로 */
@Composable
private fun InputCard(s: TriangleState, sol: TriangleSolution?) {
    val c = AppTheme.colors
    val count = TriField.entries.count { s.given(it) }
    val ok = count == 2 && sol != null
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TriNumberField(s, sol, TriField.A_SIDE, Modifier.weight(1f))
                TriNumberField(s, sol, TriField.B_SIDE, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TriNumberField(s, sol, TriField.C_SIDE, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TriNumberField(s, sol, TriField.ANGLE_A, Modifier.weight(1f))
                TriNumberField(s, sol, TriField.ANGLE_B, Modifier.weight(1f))
            }
        }
        Text(
            "각도 A 는 밑변과 빗변 사이, 각도 B 는 높이와 빗변 사이입니다(A + B = 90°). 각도 둘만으로는 크기를 알 수 없어 변 하나는 꼭 넣습니다. " +
                "셋 이상 넣으면 서로 맞는지 확인합니다.",
            Modifier.padding(top = 12.dp),
            color = c.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp,
        )
    }
}

@Composable
private fun TriNumberField(s: TriangleState, sol: TriangleSolution?, f: TriField, modifier: Modifier) {
    val v = s.value(f)
    NumberField(
        f.label, v, { s.set(f, it) }, modifier,
        unit = f.unit,
        autoValue = if (v.isEmpty() && sol != null) Fmt.fixed3(f.of(sol)) else null,
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
                    Fmt.fixed3(f.of(sol)) + if (f.unit == "°") "°" else "",
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

/** X · Z 연동: 밑변 = Z · 높이 = X(반경) 또는 반대. X 는 직경값 */
@Composable
private fun CoordCard(s: TriangleState, sol: TriangleSolution) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("X · Z 연동", color = c.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Segmented(
            listOf("밑변 = Z · 높이 = X", "밑변 = X · 높이 = Z"), if (s.baseIsZ) 0 else 1, { s.baseIsZ = it == 0 },
            Modifier.fillMaxWidth(), color = c.features.getValue(Feature.TRIANGLE).title, height = 42.dp, fontSize = 14,
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("X" to sol.dxDia, "Z" to sol.dz).forEach { (axis, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(axis, color = c.valueComp, fontFamily = AppTheme.mono, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text(Fmt.fixed3(v), color = c.valueComp, fontFamily = AppTheme.mono, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val (zName, xName) = if (s.baseIsZ) "밑변" to "높이" else "높이" to "밑변"
        Text(
            "Z 는 $zName(${Fmt.fixed3(sol.dz)}), X 는 $xName(반경 ${Fmt.fixed3(sol.dxDia / 2)}) × 2 인 직경값입니다. " +
                "Z 로 ${Fmt.fixed3(sol.dz)} 가는 동안 지름이 ${Fmt.fixed3(sol.dxDia)} 바뀝니다. 부호(+/−)는 가공 방향에 맞게 붙이세요.",
            Modifier.padding(top = 8.dp),
            color = c.textMuted, fontSize = 12.5.sp, lineHeight = 18.sp,
        )
    }
}

/** 헤더 도움말 버튼의 사용법 */
internal val TriangleHelp = listOf(
    "밑변 a · 높이 b · 빗변 c · 각도 A · 각도 B 가운데 아는 값 둘을 넣으면 나머지가 바로 나옵니다. 빈칸에는 계산값이 '자동' 표와 함께 보입니다. 칸을 비우려면 키패드에서 C, 모두 비우려면 '모두 지우기'.",
    "각도 A 는 밑변과 빗변 사이, 각도 B 는 높이와 빗변 사이입니다(A + B = 90°). 각도 둘만으로는 크기를 알 수 없어 변 하나는 꼭 넣어야 합니다.",
    "셋 이상 넣으면 서로 맞는지 봅니다(길이 0.001 mm · 각도 0.001° 안). 맞지 않거나 빗변이 변보다 짧으면 빨간 띠가 뜨고 계산을 멈춥니다.",
    "그림에는 넣은 값(보라 칸)과 계산값(흰 칸)이 제자리에 적힙니다. 모양은 각도 A 를 따르되, 22° 보다 납작하거나 58° 보다 가파르면 보기 좋게 눌러 그립니다.",
    "X · Z 연동: 밑변을 Z, 높이를 X(반경)로 보면 Z 이동량 = 밑변, X 변화량 = 높이 × 2(직경값)입니다. 높이가 Z 쪽이면 '밑변 = X · 높이 = Z' 를 고릅니다. 그림 왼쪽 위 화살이 어느 변이 Z · X 인지 보여 줍니다.",
    "예: 테이퍼 길이 Z 20 에 지름이 Ø30 → Ø40 이면 밑변 20, 높이 5(반경 차) → 각도 A 14.036°, 빗변 20.616.",
)
