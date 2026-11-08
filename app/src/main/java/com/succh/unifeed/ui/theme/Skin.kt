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
 * 预设主题色（六套）：只替换 primary / primaryContainer 系，
 * 玻璃底与文字色仍由深浅模式决定，保证任何主题色下对比度不变。
 */
enum class AccentTheme(
    val label: String,
    val swatch: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val onDarkContainer: Color
) {
    ORANGE("活力橙", Color(0xFFFF6D00), Color(0xFFFF6D00), Color(0xFFFFE0B2), Color(0xFF4E2600), Color(0xFFFFB74D), Color(0xFF6D3B00), Color(0xFFFFE0B2)),
    OCEAN_BLUE("海洋蓝", Color(0xFF1976D2), Color(0xFF1976D2), Color(0xFFBBDEFB), Color(0xFF0D3064), Color(0xFF64B5F6), Color(0xFF123A66), Color(0xFFBBDEFB)),
    FOREST("森林绿", Color(0xFF2E7D32), Color(0xFF2E7D32), Color(0xFFC8E6C9), Color(0xFF0F3D12), Color(0xFF81C784), Color(0xFF1B4620), Color(0xFFC8E6C9)),
    GRAPE("葡萄紫", Color(0xFF7B1FA2), Color(0xFF7B1FA2), Color(0xFFE1BEE7), Color(0xFF3A1050), Color(0xFFBA68C8), Color(0xFF4A1B60), Color(0xFFE1BEE7)),
    ROSE("玫瑰红", Color(0xFFC2185B), Color(0xFFC2185B), Color(0xFFF8BBD0), Color(0xFF4E0F24), Color(0xFFF06292), Color(0xFF5C1B33), Color(0xFFF8BBD0)),
    TEAL("湖水青", Color(0xFF00897B), Color(0xFF00897B), Color(0xFFB2DFDB), Color(0xFF003B34), Color(0xFF4DB6AC), Color(0xFF0F4A43), Color(0xFFB2DFDB));

    companion object {
        fun fromName(name: String?): AccentTheme =
            entries.firstOrNull { it.name == name } ?: ORANGE
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
