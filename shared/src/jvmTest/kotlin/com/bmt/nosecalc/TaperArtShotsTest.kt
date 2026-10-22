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
 * 선 · 글은 묶여 있어 겹침은 좁은 카드에서 먼저 보인다.
 * art-taper-focus: 입력 중인 칸(focus)마다 보통 카드(199dp)와 요약 칸(compact, 162 × 150dp · 132dp) 한 줄씩
 */
class TaperArtShotsTest {
    @Test
    fun all() {
        Shots.both("art-taper-all", heightDp = 3450, widthDp = 430) { AppHost(it) { Gallery(199.dp, all) } }
        Shots.both("art-taper-all-w170", heightDp = 3060, widthDp = 372) { AppHost(it) { Gallery(170.dp, all) } }
        Shots.both("art-taper-all-w600", heightDp = 2520, widthDp = 640) { AppHost(it) { Gallery(600.dp, wide) } }
    }

    @Test
    fun focus() {
        Shots.both("art-taper-focus", heightDp = 3900, widthDp = 540) { AppHost(it) { FocusGallery() } }
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

/** 그림 · 입력 중인 칸: 외경 형상 1 은 점 칸마다, 2중 형상 1 은 T3, 홈은 치수 칸마다(null = 짚지 않음) */
private val focusRows =
    listOf(null, "x1", "z1", "x2", "z2", "ang", "r1", "r2", "start", "end").map { TaperKind.OUTER to it } +
        listOf("x3", "r3").map { TaperKind.OUTER_DOUBLE to it } +
        listOf(null, "od", "bottom", "width", "insert", "zStart").map { TaperKind.GROOVE to it }

/** 한 줄: 보통 카드 199dp, 요약 칸 162 × 150dp · 132dp(B 화면 요약 카드와 같은 테두리) */
@Composable
private fun FocusGallery() {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        focusRows.forEach { (kind, focus) ->
            Text("${kind.label} 형상 1 · ${focus ?: "짚지 않음"}", style = AppType.caption, color = c.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(199.dp to false, 162.dp to true, 132.dp to true).forEach { (w, compact) ->
                    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
                    Box(
                        Modifier.width(w).aspectRatio(1.08f).clip(shape).background(c.subtle)
                            .border(if (compact) 1.5.dp else 1.dp, if (compact) c.lineStrong else c.lineCard, shape),
                    ) { TypeArt(kind, 1, false, compact = compact, focus = focus) }
                }
            }
        }
    }
}

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
