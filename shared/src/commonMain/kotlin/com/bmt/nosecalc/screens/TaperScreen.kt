package com.bmt.nosecalc.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.screens.taper.GrooveScreen
import com.bmt.nosecalc.screens.taper.SlopeScreen
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.TipHelp
import com.bmt.nosecalc.screens.taper.TypeArt
import com.bmt.nosecalc.screens.taper.typeShort
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.HelpSection
import com.bmt.nosecalc.ui.OutlineTabs
import com.bmt.nosecalc.ui.Segmented
import com.bmt.nosecalc.ui.TypeCard
import com.bmt.nosecalc.ui.theme.AppTheme

/** 테이퍼·홈 노즈R 보정 — 첫 화면(헤더 메뉴 버튼으로 다른 기능을 연다) */
@Composable
fun TaperScreen() = TaperScreen(TaperState())

/**
 * A 화면(가공 종류 · 방향 · TYPE 그림) → TYPE 을 누르면 B 화면(입력·결과). 뒤로 키·Esc 는 B → A.
 * initial = 처음 상태(스크린샷 시험이 정해서 그린다).
 */
@Composable
internal fun TaperScreen(initial: TaperState) {
    val s = rememberSaveable(saver = TaperState.Saver) { initial }
    // 입력 화면에서 열면 '값 넣기' 묶음부터
    FeatureScaffold(Feature.TAPER, TaperHelp, helpStart = if (s.open) 1 else 0) {
        OnBack(enabled = s.open) { s.open = false }
        when {
            !s.open || s.type == null -> PickScreen(s)
            s.kind.groove -> GrooveScreen(s)
            else -> SlopeScreen(s)
        }
    }
}

/**
 * A 화면(02): 가공 종류 탭 → 방향 칸(정방향 · 역방향 두 칸, 홈은 안내 띠) → 형상 카드 2열(넓은 창은 3열).
 * 카드 머리 띠에 번호 칩 + 짧은 이름 — 방향 · 내외경에 따라 그림은 뒤집혀도 칩 자리는 늘 같다
 */
@Composable
private fun PickScreen(s: TaperState) {
    val c = AppTheme.colors
    val kind = s.kind
    val mirrored = s.reverse && !kind.groove
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 12.dp)) {
        OutlineTabs(
            TaperKind.tabRows, kind.ordinal,
            {
                s.kind = TaperKind.entries[it]
                s.type = null
            },
            Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (kind.groove) {
            InfoBar("홈 바이트의 기준 인선을 선택해주세요.")
        } else {
            Segmented(
                listOf("정방향  Z− (척 쪽)", "역방향  Z+ (척 반대쪽)"),
                if (s.reverse) 1 else 0,
                {
                    s.reverse = it == 1
                    s.type = null
                },
                Modifier.padding(horizontal = 5.dp).fillMaxWidth(),
                color = c.teal,
                height = 48.dp,
                fontSize = 15,
            )
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.padding(horizontal = 5.dp)) {
            val cols = if (maxWidth >= 600.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..kind.types).chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { type ->
                            TypeCard(
                                "형상 $type", typeShort(kind, type), c.chipMint, type == s.type,
                                {
                                    s.type = type
                                    s.open = true
                                },
                                Modifier.weight(1f), aspectRatio = 199f / 221f,
                            ) { TypeArt(kind, type, mirrored) }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val barShape = RoundedCornerShape(9.dp)

@Composable
private fun barModifier(): Modifier {
    val c = AppTheme.colors
    return Modifier
        .padding(horizontal = 5.dp)
        .fillMaxWidth()
        .height(51.dp)
        .shadow(if (c.dark) 0.dp else 1.dp, barShape)
        .clip(barShape)
        .background(c.subtle)
        .border(1.dp, c.lineCard, barShape)
}

/** 11 의 안내 띠 모양(홈은 방향이 없다) */
@Composable
private fun InfoBar(text: String) {
    val c = AppTheme.colors
    Row(barModifier(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(AppIcons.Info, null, Modifier.size(18.dp), tint = c.textBody)
        Spacer(Modifier.width(8.dp))
        Text(text, color = c.textBody, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 헤더 도움말 버튼의 사용법: 형상 고르기 · 값 넣기(팁 번호 그림 [TipHelp] 먼저) · 결과와 NC · 경고 띠 · 외경 홈.
 * 입력 화면에서 열면 '값 넣기' 부터 보인다
 */
internal val TaperHelp = listOf(
    HelpSection(
        "형상 고르기",
        listOf(
            "위쪽 탭에서 가공 종류(외경 · 내경 · 외경 홈 · 외경/내경 2중 테이퍼)를 고르고, 아래 그림에서 도면과 같은 형상을 누릅니다. 카드 위 띠에 형상 번호와 짧은 이름이 있습니다.",
            "역바이트로 Z+ 쪽(척 반대쪽)으로 깎을 때는 방향 두 칸에서 '역방향 Z+' 를 누릅니다. 그림이 좌우로 뒤집히고 T1 은 처음 깎는 왼쪽 점이 됩니다. 표준 팁도 바뀝니다(외경 팁 3번 → 4번, 내경 팁 2번 → 1번).",
        ),
    ),
    HelpSection(
        "값 넣기",
        listOf(
            "형상을 누르면 입력 화면이 열립니다(맨 위 작은 그림을 누르면 형상을 다시 고릅니다). 입력 카드는 '도면 치수'(점마다 X · Z · 모서리 R, 각도, 시작 · 끝)와 " +
                "'공구'(노즈 R · Z 여유 · 팁 번호) 두 묶음입니다. 도면 치수 칸은 0 에서 시작하고, 값을 넣으면 바로 계산합니다. " +
                "X 는 직경값(도면 Ø 그대로, Ø40 → 40), Z 는 도면 좌표입니다(Z0 = 부품 오른쪽 끝면, 척 쪽은 −). " +
                "단일 테이퍼(외경·내경)는 X1·Z1·X2·Z2·각도 가운데 넷을 넣으면 나머지 하나를 계산합니다(보통 각도를 비워 둡니다). " +
                "각도는 Z축(가로선)에서 잰 한쪽 각입니다 — 도면이 꼭지각이면 ÷2, X축 기준이면 90 − 값.",
            "칸을 누르면 키패드가 열리고 값 아래에 그 칸의 뜻이 한 줄로 나옵니다. 누를 때마다 값이 바로 들어가고(확인 단추 없음), 처음 누른 숫자가 원래 값을 덮어씁니다. " +
                "칸을 비우려면 키패드의 '비우기 · 자동 계산' 을 누릅니다. '다음' 이 있으면 키패드를 닫지 않고 다음 칸으로 갑니다.",
            "노즈 R 은 인서트 날끝의 반경입니다. 기계 공구 보정 화면의 R 칸에 넣는 공구 반경(밀링의 공구 반경과 같은 역할)이고, 팁 번호는 그 화면의 T 칸에 넣습니다.",
            "점 줄 끝의 모서리 R 칸에 값을 넣으면 그 꼭짓점에 앞뒤 요소와 접하는 원호를 끼웁니다(비우면 0 = 날카로운 꼭짓점). 결과에 R 시작·끝 접점과 G02/G03, 도면 R · 보정 R(볼록 R + 노즈R · 오목 R − 노즈R)이 나옵니다.",
            "시작점(T1 앞)·끝점(마지막 T 뒤)은 앞뒤 요소의 바깥 끝 좌표입니다. 그 요소가 단면이면 X(직경), 외경·내경이면 Z 를 넣습니다(예: 내경 구멍 깊이 Z-20). 넣었을 때만 그 요소를 깎습니다. " +
                "끝점을 비우면 마지막 T 점(모서리 R 이 있으면 그 끝)에서 끝나고, 그 좌표가 끝점 칸에 자동으로 나옵니다. " +
                "시작점을 비우면(칸에 '안 깎음') 첫 T 점에서 시작합니다 — 그 앞 요소는 깎지 않고 경사(또는 그 요소) 연장선이나 단면 바깥에서 Z 로 들어갑니다. 시작점을 넣으면 그 좌표부터 앞 요소를 깎습니다.",
            "팁 번호(가상 인선 번호)는 기계 공구 보정 화면의 T 칸 번호와 같아야 합니다. 화면에는 '팁 N번' 으로 씁니다(T1 · T2 는 그림의 점). 입력 화면의 팁 번호 칸을 누르면 그림에서 고릅니다.",
        ),
        extra = { TipHelp() },
    ),
    HelpSection(
        "결과와 NC",
        listOf(
            "결과 맨 위 스위치에서 보정 방식을 고릅니다. 라운드와 같은 스위치라 한쪽에서 바꾸면 다른 쪽도 바뀌고, 다음에 켜도 그대로입니다. 결과 카드 · 형상 확인 · NC 가 모두 이것을 따릅니다.",
            "기본 '기계 보정 (G41/G42)'은 도면 좌표에 G41/G42 를 걸어 기계가 노즈R 을 보정합니다 — 결과 카드 맨 위의 R · T(첫 줄 주석 (R0.4 T3) 과 같음)를 공구 보정 화면에 넣습니다. " +
                "'직접 보정 (G40)'은 앱이 계산한 보정 좌표를 보정 없이 그대로 깎고, NC 첫 줄에 주석 (G40 DIRECT R0.4 T3) 이 붙습니다. 둘을 섞지 마세요 — 보정 좌표에 G41/G42 를 또 걸면 노즈R 만큼 두 번 밀립니다.",
            "끝점에서 직접 보정은 인선이 끝좌표에 멈추고, G41/G42 는 노즈 접점이 끝좌표에 와서 인선이 더 갈 수 있습니다(끝 요소가 외경·내경처럼 수평이면 Z 로 노즈R 만큼). 바로 뒤에 어깨·턱이 있으면 직접 보정을 쓰세요.",
            "값을 바꾸면 바로 다시 계산합니다. 보정 좌표는 앞뒤 요소를 노즈R 만큼 띄운 선들의 교점에 가상 인선을 맞춘 직접 보정 좌표이고, ΔX(직경)·ΔZ 는 도면 점과의 차이입니다(기계 보정에서는 '직접 보정 좌표도 보기' 를 눌러야 흐리게 보입니다). " +
                "G00 진입점은 첫 깎는 점에서 Z 여유만큼 Z 로 띄운 점입니다 — Z 여유가 노즈 R 보다 작으면(0 도) 노즈 R 만큼 띄웁니다. NC 는 복사 단추로 복사합니다. 이송 F 는 넣지 않습니다.",
            "결과 카드가 화면 밖이면 아래 띠에 T 점 좌표(직접 보정은 T1′ · T2′ 보정 좌표, 기계 보정은 T1 · T2 도면 좌표)가 보이고, '결과' 를 누르면 결과 카드로 갑니다. 홈은 첫 단 · 끝 단 좌표입니다.",
        ),
    ),
    HelpSection(
        "경고 띠",
        listOf(
            "노란 띠는 숫자는 나왔지만 확인할 것, 빨간 띠는 계산을 멈춘 것입니다(모순·형상 불일치·모서리 R 이 안 들어감·노즈R 이 너무 큼 등). 빨간 띠는 입력 카드 위에 나오고, 고칠 칸은 빨간 테로 짚습니다. 파란 띠는 안내입니다.",
            "단일 테이퍼의 다섯 값(X1 · Z1 · X2 · Z2 · 각도)이 모두 차서 서로 어긋나면 빨간 띠의 '각도 비우고 계산' 처럼 한 칸을 비우는 단추를 누릅니다(마지막에 채운 칸도 권합니다).",
        ),
    ),
    HelpSection(
        "외경 홈",
        listOf(
            "외경 홈은 '홈 치수'(시작 Z(오른쪽 벽)·홈 폭·외경 ØD·바닥 Ød)를 넣고, '공구' 에서 기준 인선(보통 왼쪽 · 척 쪽 팁 3번 / 오른쪽 팁 4번)을 고른 뒤 인서트 폭·겹침을 넣습니다. " +
                "X 여유는 반경값(한쪽 거리)이고, 0 이면 단마다 외경까지 후퇴합니다. 다단 진입 Z 목록과 정삭 경로가 나오고, 마지막 단은 왼쪽 벽에 정확히 맞춥니다. " +
                "벽·바닥이 X·Z 축에 평행해 노즈R 보정량이 0 이라 NC 는 하나입니다.",
        ),
    ),
)
