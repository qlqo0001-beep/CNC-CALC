package com.bmt.nosecalc.screens.sim

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 같은 크기의 바꿀 수 있는 비트맵이면 그대로 다시 쓴다(재생 · 끌기 중 비트맵을 새로 만들지 않게) */
internal actual fun argbToImageBitmap(argb: IntArray, w: Int, h: Int, reuse: ImageBitmap?): ImageBitmap {
    val old = reuse?.asAndroidBitmap()
    if (reuse != null && old != null && old.isMutable && old.width == w && old.height == h) {
        old.setPixels(argb, 0, w, 0, 0, w, h)
        return reuse
    }
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    b.setPixels(argb, 0, w, 0, 0, w, h)
    return b.asImageBitmap()
}
