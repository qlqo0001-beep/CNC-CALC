package com.bmt.nosecalc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.L2Ref
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.screens.round.DimKind
import com.bmt.nosecalc.screens.round.DimShown
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.RoundTypeArt
import com.bmt.nosecalc.screens.round.blankable
import com.bmt.nosecalc.screens.round.dims
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space
import kotlin.test.Test

/** 라운드 형상 그림(RoundTypeArt)을 여러 폭에서 라이트/다크로 → build/shots/art-round-*. 칸은 04 카드 몸통 비율(193 : 189) */
class RoundArtShotsTest {
    /** 고르기 카드 그림: 형상 1~6 을 193 · 300 · 600dp 폭에서 — 선 · 글 크기 묶음, 오른쪽 잘림 */
    @Test
    fun all() {
        Shots.both("art-round-all", heightDp = 5000, widthDp = 632) { AppHost(it) { Widths(listOf(193.dp, 300.dp, 600.dp), showDatum = false) } }
    }

    /** 입력 화면 그림(showDatum): Z0 ▲ · 이론 꼭짓점 ○ */
    @Test
    fun input() {
        Shots.both("art-round-input", heightDp = 1360, widthDp = 632) { AppHost(it) { Widths(listOf(193.dp, 300.dp), showDatum = true) } }
    }

    /**
     * 입력 화면 그림에 값(300dp): 형상 1~6 예시 값(비운 칸은 자동) + 형상 2 L2 기준 A · 서로 맞지 않는 치수(round-b2-error) ·
     * 값이 모자란 형상 5(빈칸 '?')
     */
    @Test
    fun labels() {
        val cases = (1..6).map { "형상 $it" to Case(it, roundExample()) } + listOf(
            "형상 2 · L2 기준 A" to Case(2, roundExample().apply { l2Ref = L2Ref.A }),
            "형상 2 · 치수 모순" to Case(2, roundExample().apply { setDim(2, "l2", "30") }),
            "형상 5 · 빈칸 둘" to Case(5, roundExample().apply { setDim(5, "mainR", "") }),
        )
        Shots.both("art-round-labels", heightDp = 1680, widthDp = 632) { AppHost(it) { Grid(cases, 300.dp) } }
    }

    /**
     * 내경 그림: 고르기 카드(193dp) · 입력 화면 Z0 ▲ 와 ○(300dp) · 예시 값(입구 구멍 깊이 10, 비운 칸 자동) · 관통 5 · 6 ·
     * 키패드 초점(형상 3 의 칸마다)
     */
    @Test
    fun inner() {
        val ex = roundInnerExample(3)
        val through = roundInnerExample(3).apply { through = true }
        val cases = (2..6).map { "내경 형상 $it" to Case(it, ex, labels = false) } +
            (2..6).map { "내경 형상 $it · 값" to Case(it, ex) } +
            (5..6).map { "내경 형상 $it · 관통" to Case(it, through) } +
            (dims(3).map { it.key to it.label } + ("leg" to "입구 구멍 깊이")).map { (key, label) -> "내경 3 · $label" to Case(3, ex, focus = key) }
        Shots.both("art-round-inner", heightDp = 2600, widthDp = 632) { AppHost(it) { Grid(cases, 193.dp) } }
        Shots.both("art-round-inner-wide", heightDp = 1700, widthDp = 632) { AppHost(it) { Grid(cases.subList(5, 12), 300.dp) } }
    }

    /** 키패드 그림(높이 150dp): 형상 2 · 5 의 칸마다 초점 하나 */
    @Test
    fun focus() {
        val cases = listOf(2, 5).flatMap { t ->
            (dims(t).map { it.key to it.label } + ("leg" to "끝 직선 길이")).map { (key, label) -> "형상 $t · $label" to Case(t, roundExample(), focus = key) }
        }
        Shots.both("art-round-focus", heightDp = 1000, widthDp = 632) { AppHost(it) { Grid(cases, 153.dp) } }
    }
}

/** labels = false 면 고르기 카드처럼 이름만(Z0 ▲ · ○ 없이) */
private class Case(val type: Int, val s: RoundState, val focus: String? = null, val labels: Boolean = true)

@Composable
private fun Widths(widths: List<Dp>, showDatum: Boolean) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxSize().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (w in widths) {
            Text("${w.value.toInt()}dp", Modifier.padding(top = 4.dp), style = AppType.caption, color = c.textMuted)
            // 한 줄에 들어가는 만큼(608dp 안)
            (1..6).chunked(((608 + 8) / (w.value.toInt() + 8)).coerceAtLeast(1)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        Box(Modifier.width(w).aspectRatio(193f / 189f).clip(shape).background(c.card).border(1.dp, c.lineCard, shape)) {
                            RoundTypeArt(t, showDatum = showDatum)
                        }
                    }
                }
            }
        }
    }
}

/** 입력 화면처럼 그린다: 값 · L2 기준 · 모순 치수(계산이 CONTRADICTION 이면 비울 수 있는 묶음) */
@Composable
private fun Grid(cases: List<Pair<String, Case>>, w: Dp) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxSize().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cases.chunked(((608 + 8) / (w.value.toInt() + 8)).coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, k) ->
                    Column(Modifier.width(w)) {
                        Text(name, Modifier.height(20.dp), style = AppType.caption, color = c.textMuted)
                        val result = RoundCalc.calc(k.s.input(k.type))
                        Box(Modifier.width(w).aspectRatio(193f / 189f).clip(shape).background(c.card).border(1.dp, c.lineCard, shape)) {
                            val inner = k.s.typeInner && k.type != 1
                            if (!k.labels) {
                                RoundTypeArt(k.type, inner = inner, through = inner && k.s.through)
                            } else {
                                RoundTypeArt(
                                    k.type,
                                    showDatum = k.focus == null,
                                    l2Ref = k.s.l2Ref,
                                    labels = shown(k.s, k.type),
                                    contradiction = if (RoundWarning.CONTRADICTION in result.warnings) blankable(k.type).toSet() else emptySet(),
                                    focus = k.focus,
                                    inner = inner,
                                    through = inner && k.s.through,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 칸 값 → 그림 값: 넣은 값 그대로, 비운 칸은 계산이 채운 값(자동) 또는 '?'(계산이 멈춤). 끝 직선 길이도 */
private fun shown(s: RoundState, type: Int): Map<String, DimShown> {
    val filled = RoundCalc.calc(s.input(type)).solution?.filled
    return dims(type).associate { f ->
        val v = s.dim(type, f.key)
        val auto = when (f.key) {
            "mainR" -> filled?.mainR
            "d1" -> filled?.d1
            "d2" -> filled?.d2
            "l1" -> filled?.l1
            "l2" -> filled?.l2
            else -> null
        }
        f.key to when {
            v.isNotEmpty() -> DimShown(v)
            auto != null -> DimShown(Fmt.fixed3(auto), DimKind.Auto)
            else -> DimShown("", DimKind.Missing)
        }
    } + ("leg" to DimShown(s.shared("leg")))
}
