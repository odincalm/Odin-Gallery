package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinShapes

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = OdinShapes.card,
    tonalAlpha: Float = 0.90f,
    elevation: Dp = 0.dp, // Avoid obvious shadows per design rules
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Liquid glass background: dark rgba(41,41,41, 0.90), light rgba(255,255,255, 0.80)
    val baseColor = remember(isDark, tonalAlpha) {
        if (isDark) {
            Color(0xFF292929).copy(alpha = tonalAlpha)
        } else {
            Color(0xFFFFFFFF).copy(alpha = (tonalAlpha * 0.9f).coerceIn(0.65f, 0.85f))
        }
    }

    // Subtle edge highlight (internal specular gradient) memoized to eliminate allocations per frame
    val edgeBorder = remember(isDark) {
        val highlightTop = if (isDark) Color(0x28FFFFFF) else Color(0x60FFFFFF)
        val highlightBottom = if (isDark) Color(0x0AFFFFFF) else Color(0x0D000000)
        BorderStroke(
            width = 0.5.dp,
            brush = Brush.verticalGradient(listOf(highlightTop, highlightBottom))
        )
    }

    val shadowMod = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = if (isDark) Color.Black.copy(alpha = 0.3f) else Color(0x14000000),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0x1F000000)
        )
    } else Modifier

    Box(
        modifier = modifier
            .then(shadowMod)
            .clip(shape)
            .background(baseColor)
            .border(edgeBorder, shape)
    ) {
        content()
    }
}

// Retain alias for existing callers
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = OdinShapes.card,
    tonalAlpha: Float = 0.90f,
    borderAlpha: Float = 0.2f,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) = GlassSurface(
    modifier = modifier,
    shape = shape,
    tonalAlpha = tonalAlpha,
    elevation = elevation,
    content = content
)

@Composable
fun GlassIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.Unspecified,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = OdinAnimations.springFast,
        label = "glass_icon_scale"
    )

    val isDark = isSystemInDarkTheme()
    val defaultTint = remember(isDark) {
        if (isDark) OdinColors.DarkPrimaryText else OdinColors.LightPrimaryText
    }
    val effectiveTint = remember(tint, defaultTint) {
        if (tint != Color.Unspecified) tint else defaultTint
    }

    GlassSurface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick
            ),
        shape = CircleShape,
        tonalAlpha = if (enabled) 0.88f else 0.50f
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) effectiveTint else effectiveTint.copy(alpha = 0.35f),
            modifier = Modifier
                .size(iconSize)
                .align(Alignment.Center)
        )
    }
}

// Retain alias for existing callers
@Composable
fun LiquidGlassIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.Unspecified,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit
) = GlassIconButton(
    modifier = modifier,
    icon = icon,
    contentDescription = contentDescription,
    tint = tint,
    size = size,
    iconSize = iconSize,
    onClick = onClick
)
