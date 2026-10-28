package com.bmt.nosecalc.screens.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.ui.AppIcons
import com.bmt.nosecalc.ui.CircleIconButton
import com.bmt.nosecalc.ui.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CNC 전용 키패드(설계 7.2 · 09 ③④). 화면 아래에 붙는 판 — 바텀시트 모양(위 모서리 11dp · 손잡이)이지만 위층이 아니라
 * 편집기가 그 위로 줄어든다(키패드를 연 채 편집기 글을 눌러 커서를 옮길 수 있게 — 원본처럼 덮으면 덮인 줄을 못 누른다).
 * 손잡이 줄: 칩 아이콘 + 'CNC 전용 키패드'(끌어내리면 닫힘). 머리: ↶ · ↷ · SHIFT · 🔍 · ▶ 궤적(초록) · ✕(중립).
 * 키 7 × 4(키 폭 = 판 폭에 맞춤, 넓은 창은 480dp 까지), 높이 [keyHeight], 간격 7dp, 모서리 6dp, 아래 3.5dp 턱.
 */
@Composable
internal fun CncKeypad(
    shift: ShiftState,
    canUndo: Boolean,
    canRedo: Boolean,
    findOn: Boolean,
    onKey: (PadKey) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShift: (long: Boolean) -> Unit,
    onFind: () -> Unit,
    onRun: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 58.dp,
) {
    val c = AppTheme.colors
    val p = SimColors.of(c.dark)
    val shape = RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.padBg)
            .border(1.dp, if (c.dark) c.lineStrong else c.lineField, shape)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HandleRow(onClose)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            // ▶ 옆 '궤적' 글은 자리가 될 때만(단추 넷 48 + SHIFT · ▶ 글이 글꼴 배율을 따른다)
            val fs = LocalDensity.current.fontScale
            val wide = maxWidth >= (266f + 72f * fs).dp
            Row(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PadButton(SimIcons.Undo, "되돌리기", onUndo, enabled = canUndo)
                PadButton(SimIcons.Redo, "다시하기", onRedo, enabled = canRedo)
                ShiftButton(shift, onShift)
                PadButton(SimIcons.Search, "찾기", onFind, on = findOn)
                Spacer(Modifier.weight(1f))
                RunButton(if (wide) "궤적" else null, onRun)
                CircleIconButton(AppIcons.Close, "키패드 닫기", onClose, size = 40.dp)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp)) {
            val gap = 7.dp
            val w = (minOf(maxWidth, 480.dp) - gap * 6) / 7
            Column(Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in CncRows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (k in row) KeyCap(k, shift, p, w, keyHeight, repeat = k.tone == KeyTone.BACK) { onKey(k) }
                    }
                }
            }
        }
    }
}

/** 손잡이 줄(24dp): 왼쪽 칩 아이콘 + 이름, 가운데 손잡이. 80dp 넘게 끌어내리면 닫힘 */
@Composable
private fun HandleRow(onClose: () -> Unit) {
    val c = AppTheme.colors
    var drag by remember { mutableFloatStateOf(0f) }
    val dismissAt = with(LocalDensity.current) { 60.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .draggable(
                rememberDraggableState { drag += it },
                Orientation.Vertical,
                onDragStopped = {
                    if (drag > dismissAt) onClose()
                    drag = 0f
                },
            ),
    ) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SimIcons.Chip, null, Modifier.size(15.dp), tint = c.textMuted)
            Spacer(Modifier.width(5.dp))
            Text("CNC 전용 키패드", color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.align(Alignment.Center).size(30.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
    }
}

/** 머리의 네모 단추(보이는 40dp · 누르는 48dp). on = 켜짐(청록 채움) */
@Composable
private fun PadButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, on: Boolean = false) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .alpha(if (enabled) 1f else .4f)
            .clip(shape)
            .background(if (on) c.teal else c.card)
            .border(1.dp, if (on) c.teal else c.lineField, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) c.onAccent else c.icon)
    }
}

/** SHIFT: 꺼짐 = 옅은 보라 테 · 한 글자 = 보라 채움 · 잠금 = 채움 + 밑줄(색만으로 가르지 않게 — 폭은 그대로). 길게 누르면 잠금 */
@Composable
private fun ShiftButton(shift: ShiftState, onShift: (Boolean) -> Unit) {
    val p = SimColors.of(AppTheme.colors.dark)
    val shape = RoundedCornerShape(10.dp)
    val on = shift != ShiftState.OFF
    val onShiftNow by rememberUpdatedState(onShift)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .widthIn(min = 64.dp)
            .clip(shape)
            .background(if (on) p.shiftOn else p.shiftOffBg)
            .border(1.dp, if (on) p.shiftOn else p.shiftOffBorder, shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onShiftNow(false) }, onLongPress = { onShiftNow(true) }) }
            .semantics {
                role = Role.Button
                contentDescription = when (shift) {
                    ShiftState.OFF -> "SHIFT 꺼짐"
                    ShiftState.ONCE -> "SHIFT 한 글자"
                    ShiftState.LOCK -> "SHIFT 고정"
                }
                onClick { onShiftNow(false); true }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SHIFT",
                color = if (on) p.onShiftOn else p.shiftOffText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (shift == ShiftState.LOCK) Box(Modifier.padding(top = 1.dp).size(36.dp, 2.5.dp).background(p.onShiftOn))
        }
    }
}

/** ▶ 궤적(초록 채움). 좁으면 ▶ 만 */
@Composable
private fun RunButton(text: String?, onClick: () -> Unit) {
    val p = SimColors.of(AppTheme.colors.dark)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(shape)
            .background(p.run)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "궤적 확인" }
            .padding(horizontal = if (text != null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SimIcons.Play, null, Modifier.size(16.dp), tint = p.onRun)
        if (text != null) {
            Spacer(Modifier.width(6.dp))
            Text(text, color = p.onRun, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/** 입체 키 하나: 아래 턱 + 그라데이션 면(누르면 내려앉음) + 큰 글자 + 오른쪽 위 SHIFT 글자. repeat = ⌫(누르면 바로, 0.4초 뒤 초당 15자) */
@Composable
private fun KeyCap(k: PadKey, shift: ShiftState, p: SimPalette, w: Dp, h: Dp, repeat: Boolean, onPress: () -> Unit) {
    val kc = when (k.tone) {
        KeyTone.LETTER -> p.letter
        KeyTone.DIGIT -> p.digit
        KeyTone.BACK -> p.back
        KeyTone.PUNCT -> p.punct
        KeyTone.SPACE -> p.space
        KeyTone.ENTER -> p.enter
    }
    var pressed by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    val lip = 3.5.dp
    val sink = if (pressed) 2.dp else 0.dp
    val shifted = shift != ShiftState.OFF && k.shift != null
    // 키 크기는 고정이라 글자는 글꼴 배율을 1.1 까지만 따른다(큰 글꼴에서 SHIFT 글자와 겹치지 않게)
    val fs = LocalDensity.current.fontScale
    val k1 = minOf(fs, 1.1f) / fs
    val big = if (shifted) k.shift else k.main
    val small = if (shifted) k.main else k.shift
    Box(
        Modifier
            .size(w, h)
            .semantics {
                role = Role.Button
                contentDescription = when (k.tone) {
                    KeyTone.BACK -> "지우기"
                    KeyTone.ENTER -> "새 줄"
                    KeyTone.SPACE -> "빈칸"
                    else -> big
                }
                onClick { press(); true }
            }
            .pointerInput(k, repeat) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed = true
                    var job: Job? = null
                    if (repeat) {
                        press()
                        job = scope.launch {
                            delay(400)
                            while (true) {
                                press()
                                delay(66)
                            }
                        }
                    }
                    val up = waitForUpOrCancellation()
                    job?.cancel()
                    pressed = false
                    if (up != null) {
                        up.consume()
                        if (!repeat) press()
                    }
                }
            }
            .drawBehind {
                val r = CornerRadius(6.dp.toPx())
                val lipPx = lip.toPx()
                val sinkPx = sink.toPx()
                val faceH = size.height - lipPx
                drawRoundRect(kc.lip, Offset(0f, lipPx), Size(size.width, faceH), r)
                drawRoundRect(
                    Brush.verticalGradient(listOf(kc.top, kc.bottom), startY = sinkPx, endY = sinkPx + faceH),
                    Offset(0f, sinkPx),
                    Size(size.width, faceH),
                    r,
                )
                if (k.tone == KeyTone.LETTER || k.tone == KeyTone.SPACE) {
                    drawRoundRect(kc.lip.copy(alpha = .45f), Offset(0f, sinkPx), Size(size.width, faceH), r, style = Stroke(1.dp.toPx()))
                }
            },
    ) {
        Box(Modifier.fillMaxSize().padding(bottom = lip).offset(y = sink), contentAlignment = Alignment.Center) {
            when (k.tone) {
                KeyTone.BACK -> Icon(AppIcons.Backspace, null, Modifier.size(24.dp), tint = kc.text)
                KeyTone.ENTER -> Icon(SimIcons.Enter, null, Modifier.size(26.dp), tint = kc.text)
                KeyTone.SPACE -> Text("SP", color = kc.text, fontSize = 15.sp * k1, fontWeight = FontWeight.Bold)
                KeyTone.PUNCT -> Text(big, color = kc.text, fontSize = 26.sp * k1, fontWeight = FontWeight.Black)
                // SHIFT 글자가 들어갈 키는 라이트에서 보라로(어느 키가 바뀌었는지 보이게)
                else -> Text(big, color = if (shifted && !AppTheme.colors.dark) p.shiftOn else kc.text, fontSize = 22.sp * k1, fontWeight = FontWeight.Bold)
            }
            if (small != null) {
                Text(
                    small,
                    Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 6.dp),
                    color = p.shiftMark,
                    fontSize = 11.sp * k1,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
