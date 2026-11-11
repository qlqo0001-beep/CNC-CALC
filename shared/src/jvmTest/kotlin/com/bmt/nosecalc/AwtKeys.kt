package com.bmt.nosecalc

import androidx.compose.ui.input.key.KeyEvent
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * PC 창이 받는 그대로의 키: AWT 키 이벤트(키 코드 · 눌린 글자 · 자리)를 Compose Desktop 이 창에서 쓰는 변환(`toComposeEvent`)으로 바꾼다.
 * `Key.NumPadDot` 처럼 이름으로 만든 키는 AWT 가 실제로 보내는 키와 다를 수 있다(숫자 키패드 . 은 VK_DECIMAL 인데 Key.NumPadDot 는
 * VK_PERIOD + 숫자 키패드 자리) — 숫자 키패드 시험은 이것으로 만든다. ch = 윈도우 AWT 가 KEY_PRESSED 에 싣는 글자
 */
internal fun awtKey(
    code: Int,
    ch: Char = AwtKeyEvent.CHAR_UNDEFINED,
    location: Int = AwtKeyEvent.KEY_LOCATION_STANDARD,
    modifiers: Int = 0,
): KeyEvent = KeyEvent(toCompose.invoke(null, AwtKeyEvent(source, AwtKeyEvent.KEY_PRESSED, 0L, modifiers, code, ch, location)))

/** 숫자 키패드의 키(NumLock 켬) */
internal fun numPadKey(code: Int, ch: Char, modifiers: Int = 0): KeyEvent = awtKey(code, ch, AwtKeyEvent.KEY_LOCATION_NUMPAD, modifiers)

private val source = object : Component() {}
private val toCompose = Class.forName("androidx.compose.ui.input.key.KeyEvent_desktopKt").getMethod("toComposeEvent", AwtKeyEvent::class.java)
