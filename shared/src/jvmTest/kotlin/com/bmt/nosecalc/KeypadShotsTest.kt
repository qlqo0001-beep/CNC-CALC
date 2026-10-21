package com.bmt.nosecalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.taper.TypeArt
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.BottomSheet
import com.bmt.nosecalc.ui.DockRow
import com.bmt.nosecalc.ui.DockStatus
import com.bmt.nosecalc.ui.FieldChain
import com.bmt.nosecalc.ui.FieldChainHost
import com.bmt.nosecalc.ui.FieldRole
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.NumberPad
import com.bmt.nosecalc.ui.oneLine
import com.bmt.nosecalc.ui.rememberFieldChain
import com.bmt.nosecalc.ui.theme.Space
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 숫자칸 모양 · 키패드(묶음 · 그림) · 시트를 라이트/다크로 → build/shots/field-* · keypad-*. 좁은 폰 · 큰 글꼴(-w360)도 본다 */
class KeypadShotsTest {
    @Test
    fun fieldStates() {
        Shots.both("field-states", heightDp = 650) { AppHost(it) { FieldStates() } }
        Shots.both("field-states-w360", heightDp = 760, widthDp = 360, fontScale = 1.3f) { AppHost(it) { FieldStates() } }
    }

    /** 빈칸(자동) 칸의 키패드: 안내 줄 · "자동 -7.385"(뒤 화면 taper-b-auto 의 Z2) · "비우기 · 자동 계산" */
    @Test
    fun keypadAuto() = Shots.both("keypad-auto") {
        AppHost(it) {
            TaperScreen(
                TaperState(type = 1, open = true).example().apply {
                    setPoint("z2", "")
                    setPoint("ang", "34.1")
                },
            )
            NumberPad(
                "Z2", "mm", "", onChange = {}, onClose = {},
                hint = "Z0 = 부품 오른쪽 끝면 · 척 쪽은 − (예: −5)", autoValue = "-7.385", blankable = true,
            )
        }
    }

    /** 칸 묶음(세 칸 중 둘째가 열림): 오른쪽 열 C · ⌫ · 닫기 · "다음", 제목 오른쪽 "2 / 3" */
    @Test
    fun keypadChain() = Shots.both("keypad-chain") { AppHost(it) { ChainFields(rememberFieldChain("z1")) } }

    /** 키패드 그림 · 결과 한 줄: 915dp 는 그림(170dp) · 미리보기가 보이고 키는 그대로 58dp. 680dp 는 그림 없이 지금 모양 */
    @Test
    fun keypadFigure() {
        var drawn = false
        val figure: @Composable (Modifier) -> Unit = { m ->
            drawn = true
            TypeArt(TaperKind.OUTER, 1, false, m.aspectRatio(199f / 184f))
        }
        val preview = DockStatus.Ok(
            listOf(DockRow("T1′", "39.531", "0.000", Color.Unspecified), DockRow("T2′", "50.000", "-5.234", Color.Unspecified)),
        ).oneLine()
        Shots.both("keypad-figure") { AppHost(it) { ChainFields(rememberFieldChain("z1"), figure, preview) } }
        assertTrue(drawn)
        drawn = false
        Shots.both("keypad-figure-h680", heightDp = 680) { AppHost(it) { ChainFields(rememberFieldChain("z1"), figure, preview) } }
        assertFalse(drawn)
    }

    /** 손잡이를 80dp 넘게 끌어 내리면 닫히고, 덜 끌면 제자리로 */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun sheetHandleDrags() {
        fun drag(byPx: Float): Boolean {
            val state = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
            val open = mutableStateOf(true)
            // 시트 = 손잡이 띠 24 + 내용 200 + 아래 16 → 800px 창(밀도 1)에서 위 끝 y 560, 손잡이 가운데 y 572
            val scene = ImageComposeScene(400, 800, Density(1f)) {
                AppHost(state) {
                    if (open.value) BottomSheet(onDismiss = { open.value = false }) { Box(Modifier.fillMaxWidth().height(200.dp)) }
                }
            }
            try {
                var t = 0L
                fun frame() = scene.render(t).close().also { t += 50_000_000L }
                repeat(8) { frame() }
                scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 572f))
                for (i in 1..10) {
                    scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 572f + byPx * i / 10))
                    frame()
                }
                scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 572f + byPx))
                repeat(8) { frame() }
                return open.value
            } finally {
                scene.close()
            }
        }
        assertFalse(drag(140f))
        assertTrue(drag(50f))
    }
}

/** 묶음 시험 칸 셋(X1 · Z1 / 각도). 값은 바뀌지 않는다 */
@Composable
private fun ChainFields(chain: FieldChain, figure: (@Composable (Modifier) -> Unit)? = null, preview: String? = null) {
    Column(Modifier.fillMaxSize().padding(Space.gutter)) {
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            FieldChainHost(chain) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                        NumberField("X1 (직경)", "40", {}, Modifier.weight(1f), unit = "mm", id = "x1", figure = figure, preview = preview)
                        NumberField("Z1", "-5.0", {}, Modifier.weight(1f), unit = "mm", id = "z1", figure = figure, preview = preview)
                    }
                    NumberField("각도", "30", {}, Modifier.fillMaxWidth(), unit = "°", id = "ang", figure = figure, preview = preview)
                }
            }
        }
    }
}

/** 칸 모양 모아 보기: 넣은 값 · 자동 · 못 푼 자동 · 문제 칸 · 표 · 자리표시 · 역할색 · 긴 값(잘리지 않고 줄어야) */
@Composable
private fun FieldStates() {
    Column(Modifier.fillMaxSize().padding(Space.gutter)) {
        AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField("Z1", "0", {}, Modifier.weight(1f), unit = "mm")
                    NumberField("Z2", "", {}, Modifier.weight(1f), unit = "mm", autoValue = "-7.386")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField("끝점 Z (T2 뒤)", "", {}, Modifier.weight(1f), unit = "mm", autoValue = "")
                    NumberField("각도", "30", {}, Modifier.weight(1f), unit = "°", error = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField("X1", "40", {}, Modifier.weight(1f), unit = "mm", tag = "Ø직경")
                    NumberField("X2", "50.000", {}, Modifier.weight(1f), unit = "mm", tag = "Ø직경")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField("T1 모서리 R", "", {}, Modifier.weight(1f), unit = "mm", placeholder = "기본 0")
                    NumberField("X 여유", "1", {}, Modifier.weight(1f), unit = "mm", tag = "반경")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField("Z3", "-9999.999", {}, Modifier.weight(1f), unit = "mm")
                    NumberField("X3", "123456789012", {}, Modifier.weight(1f), unit = "mm", tag = "Ø직경")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
                    NumberField("Z축 시작점", "5.0", {}, Modifier.weight(1f), role = FieldRole.Start)
                    NumberField("가공 목표점(Z)", "-25.0", {}, Modifier.weight(1f), role = FieldRole.End)
                }
            }
        }
    }
}
