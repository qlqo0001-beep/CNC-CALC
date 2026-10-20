package com.bmt.nosecalc.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.OnBack
import com.bmt.nosecalc.screens.taper.GrooveScreen
import com.bmt.nosecalc.screens.taper.SlopeScreen
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.TipHelp
import com.bmt.nosecalc.screens.taper.TypeArt
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.ChipPlace
import com.bmt.nosecalc.ui.FeatureScaffold
import com.bmt.nosecalc.ui.OutlineTabs
import com.bmt.nosecalc.ui.TypeGrid
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
    FeatureScaffold(Feature.TAPER, TaperHelp, helpExtra = { TipHelp() }) {
        OnBack(enabled = s.open) { s.open = false }
        when {
            !s.open || s.type == null -> PickScreen(s)
            s.kind.groove -> GrooveScreen(s)
            else -> SlopeScreen(s)
        }
    }
}

/** A 화면(02): 가공 종류 탭 → 방향 바(홈은 안내 띠) → TYPE 그림 카드 2열 */
@Composable
private fun PickScreen(s: TaperState) {
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
            DirectionBar(s.reverse) {
                s.reverse = !s.reverse
                s.type = null
            }
        }
        Spacer(Modifier.height(6.dp))
        TypeGrid(
            kind.types,
            selected = s.type,
            onSelect = {
                s.type = it
                s.open = true
            },
            modifier = Modifier.padding(horizontal = 5.dp),
            chip = when {
                kind.inner && mirrored -> ChipPlace.TopEnd
                kind.inner -> ChipPlace.TopStart
                mirrored -> ChipPlace.BottomEnd
                else -> ChipPlace.BottomStart
            },
            chipTextSize = 11.5f,
        ) { type -> TypeArt(kind, type, mirrored) }
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

/** 방향 바(02): 왼쪽 지금 방향, 오른쪽 전환 버튼 */
@Composable
private fun DirectionBar(reverse: Boolean, onToggle: () -> Unit) {
    val c = AppTheme.colors
    Row(barModifier().padding(start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayIcon(reverse)
        Spacer(Modifier.width(4.dp))
        Text(
            if (reverse) "역방향 가공" else "정방향 가공",
            color = c.textStrong,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        val shape = RoundedCornerShape(7.dp)
        Row(
            Modifier
                .height(32.dp)
                .clip(shape)
                .background(c.card)
                .border(1.5.dp, c.lineCard, shape)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwapIcon()
            Spacer(Modifier.width(6.dp))
            Text(
                if (reverse) "정방향 전환" else "역방향 전환",
                color = c.textStrong,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01).em,
                maxLines = 1,
            )
        }
    }
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

/** ⏩ 그림 글자처럼 주황 네모 안 흰 겹삼각형. 역방향은 ⏪ */
@Composable
private fun PlayIcon(reverse: Boolean) {
    Canvas(Modifier.size(15.dp)) {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFFBB04C), Color(0xFFEE7E1E))),
            cornerRadius = CornerRadius(3.5.dp.toPx()),
        )
        scale(if (reverse) -1f else 1f, 1f) {
            val w = size.width
            val h = size.height
            for (x0 in listOf(0.18f, 0.48f)) {
                drawPath(
                    Path().apply {
                        moveTo(w * x0, h * 0.24f)
                        lineTo(w * (x0 + 0.34f), h * 0.5f)
                        lineTo(w * x0, h * 0.76f)
                        close()
                    },
                    Color.White,
                )
            }
        }
    }
}

/** ⇄ */
@Composable
private fun SwapIcon() {
    val color = AppTheme.colors.textHint
    Canvas(Modifier.size(13.dp, 12.dp)) {
        val w = size.width
        val h = size.height
        val s = 1.5.dp.toPx()
        val head = 3.5.dp.toPx()
        drawLine(color, Offset(w * 0.08f, h * 0.3f), Offset(w * 0.92f, h * 0.3f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.92f, h * 0.3f), Offset(w * 0.92f - head, h * 0.3f - head), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, h * 0.72f), Offset(w * 0.92f, h * 0.72f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, h * 0.72f), Offset(w * 0.08f + head, h * 0.72f + head), s, StrokeCap.Round)
    }
}

/** 헤더 도움말 버튼의 사용법(끝에 팁 번호 그림 [TipHelp] 이 붙는다) */
internal val TaperHelp = listOf(
    "위쪽 탭에서 가공 종류(외경 · 내경 · 외경 홈 · 외경/내경 2중 테이퍼)를 고르고, 아래 그림에서 도면과 같은 형상을 누릅니다.",
    "역바이트로 Z+ 쪽(척 반대쪽)으로 깎을 때는 역방향 전환을 누릅니다. 그림이 좌우로 뒤집히고 T1 은 처음 깎는 왼쪽 점이 됩니다. 표준 팁도 바뀝니다(외경 3 → 4, 내경 2 → 1).",
    "형상을 누르면 입력 화면이 열립니다. X 는 직경값, Z 는 도면 좌표입니다. 단일 테이퍼(외경·내경)는 X1·Z1·X2·Z2·각도 가운데 넷을 넣으면 나머지 하나를 계산합니다(보통 각도를 비워 둡니다). 칸을 비우려면 키패드에서 C 를 누릅니다.",
    "노즈 R 은 인서트 날끝의 반경입니다. 기계 공구 보정 화면의 R 칸에 넣는 공구 반경(밀링의 공구 반경과 같은 역할)이고, 팁 번호는 T 칸에 넣습니다.",
    "점마다 모서리 R 을 넣으면 그 꼭짓점에 앞뒤 요소와 접하는 원호를 끼웁니다(비우면 날카로운 꼭짓점). 결과에 R 시작·끝 접점과 G02/G03, 도면 R · 보정 R(볼록 R + 노즈R · 오목 R − 노즈R)이 나옵니다.",
    "시작점(T1 앞)·끝점(마지막 T 뒤)은 앞뒤 요소의 바깥 끝 좌표입니다. 그 요소가 단면이면 X(직경), 외경·내경이면 Z 를 넣습니다(예: 내경 구멍 깊이 Z-20). 끝점을 비우면 5 mm 떨어진 곳까지 깎습니다. 시작점을 비우면(칸에 '안 깎음') 그 요소는 깎지 않고 경사(또는 그 요소) 연장선에서 들어갑니다.",
    "NC 는 두 가지입니다. 기본 'G41/G42 보정 프로그램'은 도면 좌표에 G41/G42 를 걸어 기계가 노즈R 을 보정합니다(첫 줄 주석의 R·T 를 공구 보정 화면에). '직접 보정 (G40)'은 앱이 계산한 보정 좌표를 보정 없이 그대로 깎습니다. 둘을 섞지 마세요 — 보정 좌표에 G41/G42 를 또 걸면 노즈R 만큼 두 번 밀립니다.",
    "끝점에서 직접 보정은 인선이 끝좌표에 멈추고, G41/G42 는 노즈 접점이 끝좌표에 와서 인선이 더 갈 수 있습니다(끝 요소가 외경·내경처럼 수평이면 Z 로 노즈R 만큼). 바로 뒤에 어깨·턱이 있으면 직접 보정을 쓰세요.",
    "값을 바꾸면 바로 다시 계산합니다. 보정 좌표는 앞뒤 요소를 노즈R 만큼 띄운 선들의 교점에 가상 인선을 맞춘 직접 보정 좌표이고, ΔX(직경)·ΔZ 는 도면 점과의 차이입니다. NC 는 복사 버튼으로 복사합니다. 이송 F 는 넣지 않습니다.",
    "노란 띠는 숫자는 나왔지만 확인할 것, 빨간 띠는 계산을 멈춘 것입니다(값 부족·모순·형상 불일치·모서리 R 이 안 들어감·노즈R 이 너무 큼 등).",
    "외경 홈은 기준 인선(왼쪽 T3 / 오른쪽 T4)을 고르고 시작 Z(오른쪽 벽)·홈 폭·외경·바닥 지름·인서트 폭·겹침을 넣습니다. 다단 진입 Z 목록과 정삭 경로가 나오고, 마지막 단은 왼쪽 벽에 정확히 맞춥니다. 벽·바닥이 축에 평행해 노즈R 보정량이 0 이라 NC 는 하나입니다.",
    "팁 번호(가상 인선 번호)는 기계 공구 보정 화면의 T 칸 번호와 같아야 합니다. 입력 화면의 팁 번호 칸을 누르면 바꿀 수 있습니다.",
)
