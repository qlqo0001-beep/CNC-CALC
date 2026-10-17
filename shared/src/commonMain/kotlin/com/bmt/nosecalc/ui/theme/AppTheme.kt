package com.bmt.nosecalc.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.resources.Res
import com.bmt.nosecalc.resources.courier_prime_bold
import com.bmt.nosecalc.resources.courier_prime_regular
import com.bmt.nosecalc.resources.noto_sans_kr
import org.jetbrains.compose.resources.Font

/** 테마 토큰 한 벌: 색 + 글꼴(본문 Noto Sans KR, 결과 숫자·G코드 고정폭 Courier Prime) */
@Immutable
class AppTokens(val colors: AppColors, val sans: FontFamily, val mono: FontFamily)

private val LocalAppTokens = staticCompositionLocalOf<AppTokens> { error("AppTheme 안에서만 쓴다") }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppTokens.current.colors
    val sans: FontFamily
        @Composable @ReadOnlyComposable get() = LocalAppTokens.current.sans
    val mono: FontFamily
        @Composable @ReadOnlyComposable get() = LocalAppTokens.current.mono
}

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val sans = notoSansKr()
    val mono = courierPrime()
    val tokens = remember(c, sans, mono) { AppTokens(c, sans, mono) }
    // material3 부품(물결 효과 등)이 쓰는 색만 맞춰 둔다. 화면은 AppTheme.colors 를 쓴다
    val scheme = if (dark) {
        darkColorScheme(primary = c.teal, background = c.page, surface = c.card, onSurface = c.textStrong, onBackground = c.textStrong)
    } else {
        lightColorScheme(primary = c.teal, background = c.page, surface = c.card, onSurface = c.textStrong, onBackground = c.textStrong)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalAppTokens provides tokens,
            LocalTextStyle provides TextStyle(fontFamily = sans, fontSize = 14.sp),
            LocalContentColor provides c.textBody,
            content = content,
        )
    }
}

/** 가변 글꼴 하나(wght 100~900)에서 쓰는 굵기만 꺼낸다 */
@Composable
private fun notoSansKr(): FontFamily {
    val fonts = listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
    ).map { Font(Res.font.noto_sans_kr, it) }
    return remember(fonts) { FontFamily(fonts) }
}

@Composable
private fun courierPrime(): FontFamily {
    val regular = Font(Res.font.courier_prime_regular, FontWeight.Normal)
    val bold = Font(Res.font.courier_prime_bold, FontWeight.Bold)
    return remember(regular, bold) { FontFamily(regular, bold) }
}
