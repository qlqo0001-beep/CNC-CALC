package com.bmt.nosecalc.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.Feature
import com.bmt.nosecalc.LocalAppState
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.Space

/** 준비 중 기능의 도움말 — 없는 화면을 설명하지 않는다 */
internal val ComingSoonHelp = listOf(HelpSection("", listOf("이 기능은 준비 중입니다. 나오면 여기에 사용법이 들어갑니다.")))

/** 아직 안 만든 기능 화면: 위쪽 카드에 준비 중 안내 + 지금 쓸 수 있는 계산으로 바로 가기 */
@Composable
fun ComingSoonPage(feature: Feature) {
    val state = LocalAppState.current
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.cardGap)) {
        AppCard(Modifier.fillMaxWidth(), padding = 24.dp) {
            FeatureIcon(feature, Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("준비 중인 기능입니다", color = c.textStrong, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            // 한글은 음절마다 줄이 바뀔 수 있어 이름 뒤에서 직접 줄을 바꾼다(어절이 쪼개지지 않게)
            Text(
                "${feature.menuTitle}${topicJosa(feature.menuTitle)}\n다음 업데이트에 들어갑니다.",
                color = c.textBody,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text("지금 쓸 수 있는 계산", color = c.textMuted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Feature.entries.filter { it.ready }.forEach { f -> Shortcut(f) { state.open(f) } }
            }
        }
    }
}

/** 바로 가기 한 줄(56dp): 기능 아이콘 + 이름 + 꺾쇠 */
@Composable
private fun Shortcut(f: Feature, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(c.subtle)
            .border(1.dp, c.lineField, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureIcon(f, Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            f.menuTitle,
            Modifier.weight(1f),
            color = c.textStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.textHint)
    }
}

/** 주제 조사: 끝 한글 음절에 받침이 있으면 "은", 없으면 "는"(한글이 없으면 "는") */
internal fun topicJosa(word: String): String {
    val last = word.lastOrNull { it in '가'..'힣' } ?: return "는"
    return if ((last - '가') % 28 != 0) "은" else "는"
}
