package com.bmt.nosecalc.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.ManualArt
import com.bmt.nosecalc.screens.round.ManualScreen
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.RoundTypeArt
import com.bmt.nosecalc.screens.round.RoundTypeScreen
import com.bmt.nosecalc.screens.round.TypeCard
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.theme.AppTheme

/** 라운드 R */
@Composable
fun RoundScreen() = RoundScreen(RoundState())

/**
 * 가공 형태 고르기(04) → 카드를 누르면 입력·결과 화면. 뒤로 키·Esc 는 입력 화면 → 고르기.
 * initial = 처음 상태(스크린샷 시험이 정해서 그린다).
 */
@Composable
internal fun RoundScreen(initial: RoundState) {
    val s = rememberSaveable(saver = RoundState.Saver) { initial }
    FeatureScaffold(Feature.ROUND, RoundHelp) {
        OnBack(enabled = s.open) { s.open = false }
        val type = s.type
        when {
            !s.open || type == null -> PickScreen(s)
            type == MANUAL -> ManualScreen(s)
            else -> RoundTypeScreen(s, type)
        }
    }
}

/** 04: 섹션 줄(04 의 "SELECT MACHINING TYPE" = "가공 형태 선택", 오른쪽 정사각 버튼 = 수동 3점호) → 형상 카드 2열 → 수동(3점호) 카드 */
@Composable
private fun PickScreen(s: RoundState) {
    val c = AppTheme.colors
    fun pick(t: Int) {
        s.type = t
        s.open = true
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 9.dp).padding(top = 14.5.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 8.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "가공 형태 선택",
                Modifier.weight(1f),
                color = c.textMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
                maxLines = 1,
            )
            ManualButton { pick(MANUAL) }
        }
        Spacer(Modifier.height(19.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..6).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t ->
                        TypeCard("형상 $t", t % 2 == 1, t == s.type, { pick(t) }, Modifier.weight(1f), aspectRatio = 193f / 226f) {
                            RoundTypeArt(t)
                        }
                    }
                }
            }
            TypeCard("수동 (3점호)", true, s.type == MANUAL, { pick(MANUAL) }, Modifier.fillMaxWidth(), aspectRatio = 392f / 168f) {
                ManualArt(withText = true)
            }
        }
    }
}

/** 04 의 섹션 줄 오른쪽 흰 정사각 버튼(빈 둥근 네모). 용도가 사진에 없어 수동(3점호) 바로 가기로 쓴다 */
@Composable
private fun ManualButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(9.dp)
    val ink = c.textBody
    Canvas(
        Modifier
            .size(34.dp)
            .shadow(if (c.dark) 0.dp else 1.5.dp, shape)
            .clip(shape)
            .background(c.card)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "수동 (CAD 3점호)" },
    ) {
        val side = 15.5.dp.toPx()
        drawRoundRect(
            ink,
            topLeft = center - Offset(side / 2, side / 2),
            size = Size(side, side),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(1.9.dp.toPx()),
        )
    }
}

/** 헤더 도움말 버튼의 사용법 */
internal val RoundHelp = listOf(
    "그림에서 도면과 같은 가공 형상(형상 1~6)을 누릅니다. 모두 외경을 척 쪽(Z−)으로 깎는 바이트(팁 T3 · G42)이고, Z0 은 부품 오른쪽 끝, X 는 직경값입니다.",
    "그림에 적힌 이름 그대로 치수(주 R · 모서리 R · D · L …)를 넣습니다. 주 R 은 중심이 축 위에 있는 구(공)의 R 이라서, 형상 2~5 는 R · 지름 · 길이 가운데 하나를 비우면 나머지로 계산합니다(자리표시에 계산값이 보입니다). 칸을 비우려면 키패드에서 C 를 누릅니다.",
    "길이(L · L1 · L2)는 모서리 R 을 넣기 전 이론 꼭짓점 사이입니다. 모서리 R 이 없으면 0 을 넣습니다. 형상 2 의 L2 는 A(왼쪽 이음점 ~ 오른쪽 끝) 또는 B(두 이음점 사이)를 고릅니다.",
    "접점 모드는 직선 · 원호가 접하는 점(도면 윤곽)과 그 좌표로 쓴 NC 를 보입니다 — 기계의 노즈R 보정(G42)으로 깎을 때 씁니다.",
    "합성 R 궤적은 도면 R 에 노즈R 을 더하고 뺀 원호(볼록 R + r · 오목 R − r)로 노즈 중심이 굴러가는 길과, 가상 인선 좌표로 쓴 NC(G02/G03 끝점 · R)를 보입니다 — 보정 없이 바로 깎을 때 씁니다.",
    "노드 표는 요소 끝점마다 도면 X · Z 와 보정 X · Z 를 가공 순서로 보이고, 사이에 그 요소의 G01/G02/G03 과 R(도면 → 보정)을 적습니다.",
    "노란 띠는 숫자는 나왔지만 확인할 것(오목 R ≤ 노즈R, 바이트 반대쪽 면), 빨간 띠는 계산을 멈춘 것입니다(값 부족 · 모순 · 모양 불일치 · 모서리 R 이 너무 큼 · 노즈R 이 너무 큼).",
    "수동(3점호)은 CAD 에서 원호 위 세 점(시작 → 가운데 → 끝, 가공 순서)을 찍어 넣으면 중심 · R · 방향(G02/G03)을 구해 같은 보정을 합니다. 끝 Z 가 시작보다 크면 역방향입니다. '가공 형태 선택' 줄 오른쪽의 네모 버튼으로도 엽니다.",
    "형상 뷰어는 두 손가락(PC 는 마우스 휠)으로 확대하고 끌어서 옮깁니다. 두 번 누르면 다시 맞춥니다.",
)
