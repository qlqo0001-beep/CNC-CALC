package com.bmt.nosecalc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType

/**
 * 기능 헤더(1.4): [← 뒤로] 제목(기능색 한 색 · 20sp 굵게, 모든 기능 같은 꼴) + 한글 부제, 오른쪽 버튼들.
 * 카드색 띠 + 아래 1dp 선으로 본문과 갈린다. dimmed = APP MENU 가 열려 헤더가 어두워진 상태(01).
 * onBack 이 있으면(돌아갈 곳이 있을 때) 제목 왼쪽에 ← 원형 버튼.
 */
@Composable
fun FeatureHeader(
    feature: Feature,
    dimmed: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = AppTheme.colors
    val bg by animateColorAsState(if (dimmed) c.headerDim else c.card)
    Column(Modifier.fillMaxWidth().background(bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                // ← 가 있으면 누르는 자리(48dp)가 왼쪽 끝 가까이 오게 덜 띄운다
                .padding(start = if (onBack != null) 8.dp else 17.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                CircleIconButton(AppIcons.Back, "뒤로", onBack)
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                // 좁은 폰 · 큰 글꼴이면 16sp 까지 줄이고, 그래도 넘치면 … 로
                BasicText(
                    feature.title,
                    style = AppType.screenTitle.copy(color = c.features.getValue(feature).title),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(16.sp, 20.sp, 0.5.sp),
                )
                Spacer(Modifier.height(3.dp))
                BasicText(
                    feature.sub,
                    style = TextStyle(color = c.textBody, fontFamily = AppTheme.sans, fontWeight = FontWeight.Medium),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(10.sp, 12.5.sp, 0.5.sp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (dimmed) Color.Transparent else c.lineCard))
    }
}

/** 헤더 원형 아이콘 버튼: 보이는 지름 44dp, 누르는 자리 48dp. outlined = 1.5dp 테두리(도움말) */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = AppTheme.colors.icon,
    outlined: Boolean = false,
    size: Dp = 44.dp,
) {
    val c = AppTheme.colors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .shadow(if (c.dark) 0.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(c.circleBg)
            .then(
                when {
                    outlined -> Modifier.border(1.5.dp, c.lineCard, CircleShape)
                    c.dark -> Modifier.border(1.dp, c.lineCard, CircleShape)
                    else -> Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.52f), tint = tint)
    }
}

/** 헤더의 메뉴 버튼(원본 APPS, 햄버거 + 글자, 높이 44dp · 누르는 자리 48dp). 닫힘 = 채움, 열림 = 외곽선 */
@Composable
fun AppsButton(open: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(9.dp)
    val fg = if (open) c.tealDeep else c.onAccent // 열림 글: 연한 바탕 위 4.5:1 넘게
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(44.dp)
            .shadow(if (open || c.dark) 0.dp else 1.dp, shape)
            .clip(shape)
            .background(if (open) c.appsOpenBg else c.appsFill)
            .border(1.5.dp, if (open) c.appsBorder.copy(alpha = 0.55f) else c.appsBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Menu, null, Modifier.size(19.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text("메뉴", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 둥근 끝 알약 버튼(01·07 CLOSE): 보이는 높이 40dp, 누르는 자리 48dp. danger = 연한 빨강 판(지우기 같은 되돌릴 수 없는 일에만) */
@Composable
fun PillButton(text: String, icon: ImageVector?, onClick: () -> Unit, danger: Boolean = false) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(50)
    val fg = if (danger) c.error.label else c.textBody
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(shape)
            .background(if (danger) c.error.bg else c.card)
            .border(1.5.dp, if (danger) c.error.border else c.lineCard, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
