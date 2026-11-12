package com.succh.unifeed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 浅色：iOS grouped 灰底，玻璃半透明叠在上面才看得出 */
private val LightNeutral = lightColorScheme(
    primary = AccentTheme.JADE.light.primary,
    onPrimary = AccentTheme.JADE.light.onPrimary,
    primaryContainer = AccentTheme.JADE.light.primaryContainer,
    onPrimaryContainer = AccentTheme.JADE.light.onPrimaryContainer,
    secondary = Color(0xFF5D5D5D),
    secondaryContainer = AccentTheme.JADE.light.secondaryContainer,
    onSecondaryContainer = AccentTheme.JADE.light.onSecondaryContainer,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8A8A8E),
    outline = Color(0xFFE5E5EA),
    tertiary = AccentTheme.JADE.light.tertiary,
    tertiaryContainer = AccentTheme.JADE.light.tertiaryContainer
)

/** 深色：iOS 系统黑底，surface 略抬高方便玻璃叠层 */
private val DarkNeutral = darkColorScheme(
    primary = AccentTheme.JADE.dark.primary,
    onPrimary = AccentTheme.JADE.dark.onPrimary,
    primaryContainer = AccentTheme.JADE.dark.primaryContainer,
    onPrimaryContainer = AccentTheme.JADE.dark.onPrimaryContainer,
    secondary = Color(0xFFC8C8C8),
    secondaryContainer = AccentTheme.JADE.dark.secondaryContainer,
    onSecondaryContainer = AccentTheme.JADE.dark.onSecondaryContainer,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    onBackground = Color(0xFFE8E8ED),
    onSurface = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF3A3A3C),
    tertiary = AccentTheme.JADE.dark.tertiary,
    tertiaryContainer = AccentTheme.JADE.dark.tertiaryContainer
)

/**
 * iOS 玻璃拟态主题：深浅模式可跟随系统或手动固定，主题色从皮肤预设取。
 * 所有界面通过 MaterialTheme.colorScheme / shapes 取色与圆角。
 */
@Composable
fun UniFeedTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    accent: AccentTheme = AccentTheme.JADE,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (darkTheme) accent.dark else accent.light
    val scheme = if (darkTheme) {
        DarkNeutral.copy(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.tertiary,
            tertiaryContainer = palette.tertiaryContainer
        )
    } else {
        LightNeutral.copy(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.tertiary,
            tertiaryContainer = palette.tertiaryContainer
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = FoloShapes,
        content = content
    )
}
