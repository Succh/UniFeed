package com.succh.unifeed.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.drawWithContent

/**
 * iOS 玻璃拟态常量：半透明填充、柔光描边、轻阴影。
 * 实际“毛玻璃”靠半透明叠在 [GlassBackdrop] 光晕上呈现。
 */
object GlassTokens {
    val BorderWidth = 0.6.dp
    val CardElevation = 10.dp
    val BarAlphaLight = 0.72f
    val BarAlphaDark = 0.48f
    val CardAlphaLight = 0.62f
    val CardAlphaDark = 0.38f
    val GlowLight = Color.White.copy(alpha = 0.55f)
    val GlowDark = Color.White.copy(alpha = 0.16f)
}

@Composable
private fun isDarkGlass(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/** 顶栏 / 底栏半透明色，让背景光晕透出来 */
@Composable
fun glassBarColor(): Color {
    val alpha = if (isDarkGlass()) GlassTokens.BarAlphaDark else GlassTokens.BarAlphaLight
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

/** 卡片半透明色 */
@Composable
fun glassCardColor(): Color {
    val alpha = if (isDarkGlass()) GlassTokens.CardAlphaDark else GlassTokens.CardAlphaLight
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

/** 柔光描边，浅色偏白、深色偏淡白 */
@Composable
fun glassGlowColor(): Color =
    if (isDarkGlass()) GlassTokens.GlowDark else GlassTokens.GlowLight

@Composable
fun glassCardColors() = CardDefaults.cardColors(containerColor = glassCardColor())

@Composable
fun glassCardBorder(): BorderStroke = BorderStroke(GlassTokens.BorderWidth, glassGlowColor())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors(): TopAppBarColors {
    val onBg = MaterialTheme.colorScheme.onBackground
    return TopAppBarDefaults.topAppBarColors(
        containerColor = glassBarColor(),
        scrolledContainerColor = glassBarColor(),
        titleContentColor = onBg,
        navigationIconContentColor = onBg,
        actionIconContentColor = onBg
    )
}

/**
 * 玻璃面板：圆角裁剪 + 半透明底 + 柔光边。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(FoloDimens.CardRadius),
    elevation: Dp = GlassTokens.CardElevation
): Modifier {
    val tint = glassCardColor()
    val glow = glassGlowColor()
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = glow.copy(alpha = 0.22f),
            spotColor = Color.Black.copy(alpha = 0.08f)
        )
        .clip(shape)
        .background(tint)
        .border(GlassTokens.BorderWidth, glow, shape)
}

/** 顶部分割细线，用在底栏上沿 */
fun Modifier.glassHairline(color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset.Zero,
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx()
    )
}

/**
 * 全屏环境光晕：模糊色斑铺在 grouped 背景上，玻璃控件才能看出半透明。
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Box(
            Modifier
                .size(280.dp)
                .align(Alignment.TopStart)
                .offset(x = (-48).dp, y = 72.dp)
                .blur(64.dp)
                .background(scheme.primary.copy(alpha = 0.22f), CircleShape)
        )
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = 180.dp)
                .blur(72.dp)
                .background(scheme.tertiary.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 80.dp)
                .blur(80.dp)
                .background(scheme.primaryContainer.copy(alpha = 0.40f), CircleShape)
        )
        content()
    }
}
