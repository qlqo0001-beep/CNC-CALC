package com.bmt.nosecalc.screens.sim

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ARGB 정수는 작은 끝(little-endian) 바이트로 B · G · R · A — Skia BGRA_8888 그대로. 알파가 0 · 255 뿐이라 미리 곱한 알파와 같다.
 * PC 캔버스는 그릴 때마다 바꿀 수 있는 비트맵을 복사해 그리므로(Image.makeFromBitmap) 같은 비트맵에 다시 써도 된다.
 */
internal actual fun argbToImageBitmap(argb: IntArray, w: Int, h: Int, reuse: ImageBitmap?): ImageBitmap {
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    val bytes = ByteArray(w * h * 4)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(argb, 0, w * h)
    val old = reuse?.asSkiaBitmap()
    if (reuse != null && old != null && old.width == w && old.height == h) {
        old.installPixels(info, bytes, w * 4)
        old.notifyPixelsChanged()
        return reuse
    }
    val b = Bitmap()
    b.installPixels(info, bytes, w * 4)
    return b.asComposeImageBitmap()
}
