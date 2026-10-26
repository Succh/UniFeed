package com.succh.unifeed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** UniFeed 品牌橙，浅色模式主色 */
private val FoloOrange = Color(0xFFFF6D00)

/** 深色模式下提高明度，保证对比度 */
private val FoloOrangeLight = Color(0xFFFFB74D)

/** 浅色：iOS grouped 灰底，玻璃半透明叠在上面才看得出 */
private val LightColors = lightColorScheme(
    primary = FoloOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF4E2600),
    secondary = Color(0xFF5D5D5D),
    secondaryContainer = Color(0xFFFFE8D1),
    onSecondaryContainer = Color(0xFF3E1A00),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8A8A8E),
    outline = Color(0xFFE5E5EA),
    tertiary = Color(0xFFFFB300)
)

/** 深色：iOS 系统黑底，surface 略抬高方便玻璃叠层 */
private val DarkColors = darkColorScheme(
    primary = FoloOrangeLight,
    onPrimary = Color(0xFF3E1A00),
    primaryContainer = Color(0xFF6D3B00),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFFC8C8C8),
    secondaryContainer = Color(0xFF3E2A14),
    onSecondaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    onBackground = Color(0xFFE8E8ED),
    onSurface = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF3A3A3C),
    tertiary = Color(0xFFFFB300)
)

/**
 * 跟随系统深浅色的 iOS 玻璃拟态主题。
 * 所有界面通过 MaterialTheme.colorScheme / shapes 取色与圆角。
 */
@Composable
fun UniFeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = FoloShapes,
        content = content
    )
}
