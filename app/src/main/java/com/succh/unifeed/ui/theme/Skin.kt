package com.succh.unifeed.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 皮肤自定义三件套：深浅模式、主题色预设、玻璃模糊强度。
 * 只用 Compose 自带能力，不引入新依赖；设置页改动即时生效即可预览。
 */

/** 深浅模式：跟随系统或手动固定。手动切到深色时玻璃底与文字一起翻转，保证对比度 */
enum class ThemeMode(val label: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: FOLLOW_SYSTEM
    }
}

/**
 * 一套主题在浅色 / 深色下的强调色角色。
 * 玻璃底与正文色仍由深浅模式决定；这里只换与品牌色绑定的角色。
 */
data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color
)

/**
 * 预设主题色：青绿为默认（贴近 v16 应龙图标），活力橙为完整可选皮肤，
 * 其余皮肤各自独立。切换后 primary / container / tertiary 系一并生效。
 */
enum class AccentTheme(
    val label: String,
    val swatch: Color,
    val light: AccentPalette,
    val dark: AccentPalette
) {
    JADE(
        "青绿",
        Color(0xFF00787C),
        AccentPalette(
            primary = Color(0xFF00787C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB2EBF2),
            onPrimaryContainer = Color(0xFF00383E),
            secondaryContainer = Color(0xFFD0F0F2),
            onSecondaryContainer = Color(0xFF00383E),
            tertiary = Color(0xFF04AAA4),
            tertiaryContainer = Color(0xFFB2EBF2)
        ),
        AccentPalette(
            primary = Color(0xFF40D0C8),
            onPrimary = Color(0xFF00383E),
            primaryContainer = Color(0xFF00383E),
            onPrimaryContainer = Color(0xFFB2EBF2),
            secondaryContainer = Color(0xFF0A3D42),
            onSecondaryContainer = Color(0xFFB2EBF2),
            tertiary = Color(0xFF04AAA4),
            tertiaryContainer = Color(0xFF0F4A4E)
        )
    ),
    ORANGE(
        "活力橙",
        Color(0xFFFF6D00),
        AccentPalette(
            primary = Color(0xFFFF6D00),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE0B2),
            onPrimaryContainer = Color(0xFF4E2600),
            secondaryContainer = Color(0xFFFFE8D1),
            onSecondaryContainer = Color(0xFF3E1A00),
            tertiary = Color(0xFFFFB300),
            tertiaryContainer = Color(0xFFFFE0B2)
        ),
        AccentPalette(
            primary = Color(0xFFFFB74D),
            onPrimary = Color(0xFF3E1A00),
            primaryContainer = Color(0xFF6D3B00),
            onPrimaryContainer = Color(0xFFFFE0B2),
            secondaryContainer = Color(0xFF3E2A14),
            onSecondaryContainer = Color(0xFFFFE0B2),
            tertiary = Color(0xFFFFB300),
            tertiaryContainer = Color(0xFF6D3B00)
        )
    ),
    OCEAN_BLUE(
        "海洋蓝",
        Color(0xFF1976D2),
        AccentPalette(
            primary = Color(0xFF1976D2),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFBBDEFB),
            onPrimaryContainer = Color(0xFF0D3064),
            secondaryContainer = Color(0xFFE3F2FD),
            onSecondaryContainer = Color(0xFF0D3064),
            tertiary = Color(0xFF0288D1),
            tertiaryContainer = Color(0xFFBBDEFB)
        ),
        AccentPalette(
            primary = Color(0xFF64B5F6),
            onPrimary = Color(0xFF0D3064),
            primaryContainer = Color(0xFF123A66),
            onPrimaryContainer = Color(0xFFBBDEFB),
            secondaryContainer = Color(0xFF0D2744),
            onSecondaryContainer = Color(0xFFBBDEFB),
            tertiary = Color(0xFF29B6F6),
            tertiaryContainer = Color(0xFF123A66)
        )
    ),
    FOREST(
        "森林绿",
        Color(0xFF2E7D32),
        AccentPalette(
            primary = Color(0xFF2E7D32),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC8E6C9),
            onPrimaryContainer = Color(0xFF0F3D12),
            secondaryContainer = Color(0xFFE8F5E9),
            onSecondaryContainer = Color(0xFF0F3D12),
            tertiary = Color(0xFF43A047),
            tertiaryContainer = Color(0xFFC8E6C9)
        ),
        AccentPalette(
            primary = Color(0xFF81C784),
            onPrimary = Color(0xFF0F3D12),
            primaryContainer = Color(0xFF1B4620),
            onPrimaryContainer = Color(0xFFC8E6C9),
            secondaryContainer = Color(0xFF1B4620),
            onSecondaryContainer = Color(0xFFC8E6C9),
            tertiary = Color(0xFF66BB6A),
            tertiaryContainer = Color(0xFF1B4620)
        )
    ),
    GRAPE(
        "葡萄紫",
        Color(0xFF7B1FA2),
        AccentPalette(
            primary = Color(0xFF7B1FA2),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE1BEE7),
            onPrimaryContainer = Color(0xFF3A1050),
            secondaryContainer = Color(0xFFF3E5F5),
            onSecondaryContainer = Color(0xFF3A1050),
            tertiary = Color(0xFF8E24AA),
            tertiaryContainer = Color(0xFFE1BEE7)
        ),
        AccentPalette(
            primary = Color(0xFFBA68C8),
            onPrimary = Color(0xFF3A1050),
            primaryContainer = Color(0xFF4A1B60),
            onPrimaryContainer = Color(0xFFE1BEE7),
            secondaryContainer = Color(0xFF4A1B60),
            onSecondaryContainer = Color(0xFFE1BEE7),
            tertiary = Color(0xFFCE93D8),
            tertiaryContainer = Color(0xFF4A1B60)
        )
    ),
    ROSE(
        "玫瑰红",
        Color(0xFFC2185B),
        AccentPalette(
            primary = Color(0xFFC2185B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF8BBD0),
            onPrimaryContainer = Color(0xFF4E0F24),
            secondaryContainer = Color(0xFFFCE4EC),
            onSecondaryContainer = Color(0xFF4E0F24),
            tertiary = Color(0xFFD81B60),
            tertiaryContainer = Color(0xFFF8BBD0)
        ),
        AccentPalette(
            primary = Color(0xFFF06292),
            onPrimary = Color(0xFF4E0F24),
            primaryContainer = Color(0xFF5C1B33),
            onPrimaryContainer = Color(0xFFF8BBD0),
            secondaryContainer = Color(0xFF5C1B33),
            onSecondaryContainer = Color(0xFFF8BBD0),
            tertiary = Color(0xFFF48FB1),
            tertiaryContainer = Color(0xFF5C1B33)
        )
    ),
    TEAL(
        "湖水青",
        Color(0xFF00897B),
        AccentPalette(
            primary = Color(0xFF00897B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB2DFDB),
            onPrimaryContainer = Color(0xFF003B34),
            secondaryContainer = Color(0xFFE0F2F1),
            onSecondaryContainer = Color(0xFF003B34),
            tertiary = Color(0xFF00897B),
            tertiaryContainer = Color(0xFFB2DFDB)
        ),
        AccentPalette(
            primary = Color(0xFF4DB6AC),
            onPrimary = Color(0xFF003B34),
            primaryContainer = Color(0xFF0F4A43),
            onPrimaryContainer = Color(0xFFB2DFDB),
            secondaryContainer = Color(0xFF0F4A43),
            onSecondaryContainer = Color(0xFFB2DFDB),
            tertiary = Color(0xFF4DB6AC),
            tertiaryContainer = Color(0xFF0F4A43)
        )
    );

    val lightPrimary: Color get() = light.primary
    val lightContainer: Color get() = light.primaryContainer
    val onLightContainer: Color get() = light.onPrimaryContainer
    val darkPrimary: Color get() = dark.primary
    val darkContainer: Color get() = dark.primaryContainer
    val onDarkContainer: Color get() = dark.onPrimaryContainer

    companion object {
        fun fromName(name: String?): AccentTheme =
            entries.firstOrNull { it.name == name } ?: JADE
    }
}

/** 背景光晕模糊强度：缩放 GlassBackdrop 的模糊半径 */
enum class GlassBlur(val label: String, val factor: Float) {
    WEAK("弱", 0.55f),
    NORMAL("标准", 1f),
    STRONG("强", 1.55f);

    companion object {
        fun fromName(name: String?): GlassBlur =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
