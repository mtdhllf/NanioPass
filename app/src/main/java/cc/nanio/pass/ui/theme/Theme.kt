package cc.nanio.pass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 颜色与排版 token（浅色/深色两套）。
 */
@Immutable
data class NanioColors(
    val pageBg: Color,
    val cardBg: Color,
    val cardBgPress: Color,
    val cardBgSecondary: Color,
    val brand: Color,
    val brandDark: Color,
    val onBrand: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textValue: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val divider: Color,
    val searchBarBg: Color,
    val headerBg: Color,
    val ringTrack: Color,
    val fabShadow: Color,
    val scrim: Color,
    val isDark: Boolean,
)

private val LightColors = NanioColors(
    pageBg = Color(0xFFF3F3F3),
    cardBg = Color(0xFFFFFFFF),
    cardBgPress = Color(0x14000000),
    cardBgSecondary = Color(0xFFF7F7F7),
    brand = Color(0xFF3482FF),
    brandDark = Color(0xFF3381FF),
    onBrand = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF8C9DB0),
    textTertiary = Color(0x66000000),
    textValue = Color(0x99000000),
    danger = Color(0xFFFA382E),
    warning = Color(0xFFFF6900),
    success = Color(0xFF2EBE5B),
    divider = Color(0x0D000000),
    searchBarBg = Color(0xFFFFFFFF),
    headerBg = Color(0xFFF3F3F3),
    ringTrack = Color(0x0F000000),
    fabShadow = Color(0x663482FF),
    scrim = Color(0x80000000),
    isDark = false,
)

private val DarkColors = NanioColors(
    pageBg = Color(0xFF000000),
    cardBg = Color(0xFF1A1A1A),
    cardBgPress = Color(0x1FFFFFFF),
    cardBgSecondary = Color(0xFF242424),
    brand = Color(0xFF4A90FF),
    brandDark = Color(0xFF3482FF),
    onBrand = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8C9DB0),
    textTertiary = Color(0x80FFFFFF),
    textValue = Color(0xB3FFFFFF),
    danger = Color(0xFFFF5A50),
    warning = Color(0xFFFF7D1A),
    success = Color(0xFF3ED06B),
    divider = Color(0x1FFFFFFF),
    searchBarBg = Color(0xFF1A1A1A),
    headerBg = Color(0xFF000000),
    ringTrack = Color(0x1FFFFFFF),
    fabShadow = Color(0x664A90FF),
    scrim = Color(0x99000000),
    isDark = true,
)

val LocalNanioColors = staticCompositionLocalOf { LightColors }

private val NanioTypography = Typography(
    // 名称 / 标题：17sp medium
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    // 分组标题：14sp
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // 列表摘要：13sp
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // 正文：15sp
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    // 详情行值：14sp
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // 大标题
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
)

private fun ColorScheme.merge(colors: NanioColors): ColorScheme = copy(
    primary = colors.brand,
    onPrimary = colors.onBrand,
    primaryContainer = colors.brand.copy(alpha = 0.12f),
    onPrimaryContainer = colors.brand,
    background = colors.pageBg,
    onBackground = colors.textPrimary,
    surface = colors.cardBg,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.cardBgSecondary,
    onSurfaceVariant = colors.textSecondary,
    error = colors.danger,
    onError = Color.White,
    outline = colors.divider,
)

@Composable
fun NanioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).merge(colors)
    CompositionLocalProvider(LocalNanioColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NanioTypography,
            content = content,
        )
    }
}

/** 便捷访问：NanioTheme.colors */
object NanioTheme {
    val colors: NanioColors
        @Composable get() = LocalNanioColors.current
}
