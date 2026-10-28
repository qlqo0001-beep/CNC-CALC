package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 3D z-버퍼 그림(ARGB 0xAARRGGBB — 알파는 0 또는 255)을 그림으로(설계 6.4 · 8.6). 공통 API 에 픽셀 쓰기가 없어 플랫폼마다 둔다.
 * [reuse] 가 같은 크기면 그 그림에 다시 써서 돌려준다(안드로이드 `Bitmap.setPixels`, PC Skia `installPixels`). 백그라운드 스레드에서 부른다.
 */
internal expect fun argbToImageBitmap(argb: IntArray, w: Int, h: Int, reuse: ImageBitmap?): ImageBitmap
