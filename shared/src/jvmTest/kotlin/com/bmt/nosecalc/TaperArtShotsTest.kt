package com.bmt.nosecalc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TypeArt
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test

/**
 * 테이퍼 형상 그림(TypeArt) 전부 — 외경 · 내경 · 외경 홈 · 2중(정 · 역방향)을 라이트/다크로 → build/shots/art-taper-*.
 * 카드 폭 199dp(412dp 폰) · 170dp(360dp 폰)는 전부, 600dp(PC 넓은 창)는 그림 갈래마다 하나씩 — 넓은 카드는 자리만 커지고
 * 선 · 글은 묶여 있어 겹침은 좁은 카드에서 먼저 보인다
 */
class TaperArtShotsTest {
    @Test
    fun all() {
        Shots.both("art-taper-all", heightDp = 3450, widthDp = 430) { AppHost(it) { Gallery(199.dp, all) } }
        Shots.both("art-taper-all-w170", heightDp = 3060, widthDp = 372) { AppHost(it) { Gallery(170.dp, all) } }
        Shots.both("art-taper-all-w600", heightDp = 2520, widthDp = 640) { AppHost(it) { Gallery(600.dp, wide) } }
    }
}

/** 한 줄 묶음: 이름, 종류, 역방향, 형상 번호들 */
private class ArtSet(val title: String, val kind: TaperKind, val reverse: Boolean, val types: List<Int>)

private val all = listOf(
    ArtSet("외경", TaperKind.OUTER, false, (1..5).toList()),
    ArtSet("내경", TaperKind.INNER, false, (1..5).toList()),
    ArtSet("외경 홈", TaperKind.GROOVE, false, listOf(1, 2)),
    ArtSet("외경 2중 테이퍼", TaperKind.OUTER_DOUBLE, false, (1..4).toList()),
    ArtSet("내경 2중 테이퍼", TaperKind.INNER_DOUBLE, false, (1..4).toList()),
    ArtSet("외경 2중 테이퍼 · 역방향", TaperKind.OUTER_DOUBLE, true, (1..4).toList()),
    ArtSet("내경 2중 테이퍼 · 역방향", TaperKind.INNER_DOUBLE, true, (1..4).toList()),
)

private val wide = listOf(
    ArtSet("외경", TaperKind.OUTER, false, listOf(1)),
    ArtSet("내경", TaperKind.INNER, false, listOf(4)),
    ArtSet("외경 2중 테이퍼", TaperKind.OUTER_DOUBLE, false, listOf(4)),
    ArtSet("외경 홈", TaperKind.GROOVE, false, listOf(1)),
)

/** 카드는 고르기 화면(02)과 같은 꼴: 폭 cardW, 비율 1.08, 옅은 바탕, 모서리 12dp. 한 줄에 2장(넓은 카드는 1장) */
@Composable
private fun Gallery(cardW: Dp, sets: List<ArtSet>) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxSize().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sets.forEach { s ->
            Text(s.title, Modifier.padding(top = 6.dp), style = AppType.label, color = c.textStrong)
            s.types.chunked(if (cardW > 300.dp) 1 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { type ->
                        Column {
                            Text("형상 $type", style = AppType.caption, color = c.textMuted)
                            Box(
                                Modifier.width(cardW).aspectRatio(1.08f).clip(shape).background(c.subtle).border(1.dp, c.lineCard, shape),
                            ) { TypeArt(s.kind, type, s.reverse) }
                        }
                    }
                }
            }
        }
    }
}
