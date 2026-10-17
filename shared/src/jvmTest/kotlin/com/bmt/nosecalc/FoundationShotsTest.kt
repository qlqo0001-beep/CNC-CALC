package com.bmt.nosecalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test

/** 기초 부품(아이콘 …)을 라이트/다크로 → build/shots/foundation-*. 좁은 폰 · 큰 글꼴(-w360: 360dp · 글꼴 1.3배)도 같이 본다 */
class FoundationShotsTest {
    @Test
    fun components() {
        Shots.both("foundation-components", heightDp = 300) { AppHost(it) { FoundationGallery() } }
        Shots.both("foundation-components-w360", heightDp = 300, widthDp = 360, fontScale = 1.3f) { AppHost(it) { FoundationGallery() } }
    }
}

@Composable
private fun FoundationGallery() {
    val c = AppTheme.colors
    val icons = with(AppIcons) { listOf(Plus, Minus, Fit, Expand, Copy, Check, Help, Stop, Back, Arc3, ChevronDown) }
    Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter, vertical = Space.cardGap)) {
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            Text("아이콘", style = AppType.cardTitle, color = c.textStrong)
            for (size in listOf(20.dp, 16.dp)) {
                Text("${size.value.toInt()}dp", Modifier.padding(top = 12.dp, bottom = 6.dp), style = AppType.caption, color = c.textMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    icons.forEach { Icon(it, null, Modifier.size(size), tint = c.icon) }
                }
            }
        }
    }
}
