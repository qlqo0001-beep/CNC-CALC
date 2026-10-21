package com.bmt.nosecalc.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.ManualArt
import com.bmt.nosecalc.screens.round.ManualScreen
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.RoundTypeArt
import com.bmt.nosecalc.screens.round.RoundTypeScreen
import com.bmt.nosecalc.screens.round.roundTypeShort
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.HelpSection
import com.bmt.nosecalc.ui.TypeCard
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
    // 도움말은 입력 화면이면 '치수 넣기' 부터
    FeatureScaffold(Feature.ROUND, RoundHelp, helpStart = if (s.open) 1 else 0) {
        OnBack(enabled = s.open) { s.open = false }
        val type = s.type
        when {
            !s.open || type == null -> PickScreen(s)
            type == MANUAL -> ManualScreen(s)
            else -> RoundTypeScreen(s, type)
        }
    }
}

/** 04: 섹션 줄("가공 형태 선택" + 오른쪽 '3점호 입력' 단추) → 형상 카드 2열(번호 칩 + 짧은 이름) → 수동(3점호) 카드 */
@Composable
private fun PickScreen(s: RoundState) {
    val c = AppTheme.colors
    fun pick(t: Int) {
        s.type = t
        s.open = true
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 9.dp).padding(top = 8.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..6).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t ->
                        TypeCard(
                            "형상 $t", roundTypeShort(t), if (t % 2 == 1) c.chipSky else c.chipMint, t == s.type, { pick(t) },
                            Modifier.weight(1f), aspectRatio = 193f / 226f,
                        ) {
                            RoundTypeArt(t)
                        }
                    }
                }
            }
            TypeCard("수동 (3점호)", null, c.chipSky, s.type == MANUAL, { pick(MANUAL) }, Modifier.fillMaxWidth(), aspectRatio = 392f / 168f) {
                ManualArt(withText = true)
            }
        }
    }
}

/** 섹션 줄 오른쪽 테두리 단추: 3점호 아이콘 + '3점호 입력'(수동 3점호 바로 가기). 40dp 높이, 누르는 자리 48dp */
@Composable
private fun ManualButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(shape)
            .background(c.card)
            .border(1.5.dp, c.lineField, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "수동 (CAD 3점호)" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Arc3, null, Modifier.size(16.dp), tint = c.textBody)
        Spacer(Modifier.width(6.dp))
        Text("3점호 입력", color = c.textBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 헤더 도움말 버튼의 사용법: 형상 고르기 · 치수 넣기(그림 먼저) · 결과와 NC · 경고 */
internal val RoundHelp = listOf(
    HelpSection(
        "형상 고르기",
        listOf(
            "그림에서 도면과 같은 가공 형상(형상 1~6)을 누릅니다. 모두 외경을 척 쪽(Z−)으로 깎는 바이트(팁 3번 · G42)이고, Z0 은 부품 오른쪽 끝, X 는 직경값입니다.",
            "수동(3점호)은 CAD 에서 원호 위 세 점(시작 → 가운데 → 끝, 가공 순서)을 찍어 넣으면 중심 · R · 방향(G02/G03)을 구해 같은 보정을 합니다. 끝 Z 가 시작보다 크면 역방향입니다. '가공 형태 선택' 줄 오른쪽의 '3점호 입력' 버튼으로도 엽니다.",
        ),
    ),
    HelpSection(
        "치수 넣기",
        listOf(
            "치수 칸은 0 에서 시작합니다. 그림에 적힌 이름 그대로 치수(주 R · 모서리 R · D · L …)를 넣으면 바로 계산하고 그림에도 값이 적힙니다. 칸을 누르면 키패드에 그 치수만 짚은 그림이 뜨고, '다음' 으로 다음 칸에 갑니다.",
            "주 R 은 중심이 축 위에 있는 구(공)의 R 이라서, 형상 2~5 는 R · 지름 · 길이 가운데 하나를 비우거나 0 으로 두면 나머지로 계산합니다(칸에 계산값이 '자동' 으로 보입니다). 칸을 비우려면 키패드의 '비우기 · 자동 계산' 을 누릅니다.",
            "길이(L · L1 · L2) · 지름(D)은 그림의 ○ 표시, 곧 모서리 R 을 넣기 전 이론 꼭짓점 사이입니다. 모서리 R 칸을 비우면 0 입니다. 형상 2 의 L2 는 A(왼쪽 이음점 ~ 오른쪽 끝) 또는 B(두 이음점 사이)를 고릅니다.",
        ),
        extra = { RoundHelpArt() },
    ),
    HelpSection(
        "결과와 NC",
        listOf(
            "결과 맨 위 스위치에서 보정 방식을 고릅니다. 테이퍼와 같은 스위치라 한쪽에서 바꾸면 다른 쪽도 바뀌고, 다음에 켜도 그대로입니다(처음은 기계 보정).",
            "기계 보정 (G41/G42)은 직선 · 원호가 접하는 점(도면 윤곽)과 그 좌표로 쓴 NC 를 보입니다 — 기계의 노즈R 보정(G42)으로 깎을 때 씁니다. 보정 시작 · 취소 블록과 공구 보정 R · T 는 기계에 맞게 직접 넣습니다.",
            "직접 보정 (G40)은 도면 R 에 노즈R 을 더하고 뺀 원호(볼록 R + r · 오목 R − r)로 노즈 중심이 굴러가는 길(합성 R 궤적)과, 가상 인선 좌표로 쓴 NC(G02/G03 끝점 · R)를 보입니다 — 보정 없이(G40) 바로 깎을 때 씁니다. " +
                "NC 첫 줄 주석 (G40 DIRECT R0.4 T3) 은 그 좌표에 든 노즈 R · 팁입니다(기계는 주석을 건너뜀). 이 NC 에 G41/G42 를 또 걸면 노즈R 만큼 두 번 밀립니다.",
            "노드 표는 요소 끝점마다 지금 보정 방식의 X · Z 를 크게, 다른 쪽(도면 · 보정) 값을 그 아래 작게 가공 순서로 보이고, 사이에 그 요소의 G01/G02/G03 과 R(도면 → 보정)을 적습니다. 노드 표가 화면 밖이면 아래 띠에 첫 점 · 끝 점이 보이고, '결과' 를 누르면 노드 표로 갑니다.",
            "형상 확인은 − · + · 맞춤 단추나 두 손가락(PC 는 Ctrl + 휠)으로 확대하고 끌어서 옮기며, 오른쪽 위 단추로 크게 봅니다. 원호 중심(+) 옆에 R 과 G02/G03 이 적힙니다 — 작은 R 은 확대하면 보입니다.",
        ),
    ),
    HelpSection(
        "경고",
        listOf(
            "파란 띠는 안내(값을 넣으면 바로 계산 · 형상 1 · 2 · 4 에 늘 있는 바이트 반대쪽 면 — 누르면 펼침), 노란 띠는 숫자는 나왔지만 확인할 것(오목 R ≤ 노즈R, 그 밖의 바이트 반대쪽 면), 빨간 띠는 계산을 멈춘 것입니다(모순 · 모양 불일치 · 모서리 R 이 너무 큼 · 노즈R 이 너무 큼 · 잘못된 값). 빨간 띠가 가리키는 칸은 빨간 테두리로 보입니다.",
        ),
    ),
)

/** 도움말 '치수 넣기' 그림: 형상 2 의 치수 이름과 이론 꼭짓점 ○ */
@Composable
private fun RoundHelpArt() {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(240.dp).aspectRatio(193f / 189f).clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape)) {
            RoundTypeArt(2, showDatum = true, l2Ref = L2Ref.B, labels = emptyMap())
        }
    }
}
