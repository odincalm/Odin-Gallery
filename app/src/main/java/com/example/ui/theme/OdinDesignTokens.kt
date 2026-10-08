package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OdinColors {
    // Dark Mode Tokens (Primary OLED Black)
    val DarkSystemBackground = Color(0xFF000000)
    val DarkSecondaryBackground = Color(0xFF1C1C1E)
    val DarkTertiaryBackground = Color(0xFF2C2C2E)

    val DarkPrimaryText = Color(0xFFFFFFFF)
    val DarkSecondaryText = Color(0x99EBEBF5)   // rgba(235,235,245,0.60)
    val DarkTertiaryText = Color(0x4DEBEBF5)    // rgba(235,235,245,0.30)
    val DarkDisabled = Color(0x2EEBEBF5)        // rgba(235,235,245,0.18)

    val DarkAccentBlue = Color(0xFF0A84FF)
    val DarkSuccess = Color(0xFF30D158)
    val DarkDestructive = Color(0xFFFF453A)
    val DarkWarning = Color(0xFFFF9F0A)

    // Dark Glass Material
    val DarkGlassSurface = Color(0xE6292929)     // rgba(41,41,41,0.90)
    val DarkGlassHighlight = Color(0x1FFFFFFF)   // subtle white sheen
    val DarkGlassBorder = Color(0x24FFFFFF)      // subtle edge definition

    // Light Mode Tokens
    val LightBackground = Color(0xFFFFFFFF)
    val LightSecondaryBackground = Color(0xFFF2F2F7)
    val LightTertiaryBackground = Color(0xFFE5E5EA)

    val LightPrimaryText = Color(0xD9000000)     // rgba(0,0,0,0.85)
    val LightSecondaryText = Color(0x803E3E3E)   // rgba(62,62,62,0.50)
    val LightTertiaryText = Color(0x4D3E3E3E)
    val LightDisabled = Color(0x26000000)

    val LightAccentBlue = Color(0xFF0071E3)
    val LightDestructive = Color(0xFFFF3B30)

    // Light Glass Material
    val LightGlassSurface = Color(0xBFFFFFFF)    // rgba(255,255,255,0.75)
    val LightGlassHighlight = Color(0x40FFFFFF)
    val LightGlassBorder = Color(0x14000000)
}

object OdinTypography {
    val largeTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp
    )

    val title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp
    )

    val sectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.26).sp
    )

    val headline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    )

    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    )

    val subheadline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    )

    val footnote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    )

    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
}

object OdinSpacing {
    val gridGap = 2.dp
    val screenHorizontalPadding = 16.dp
    val sectionSpacing = 28.dp
    val itemSpacing = 12.dp
}

object OdinShapes {
    val thumbnail = RoundedCornerShape(0.dp) // Apple Photos thumbnails have sharp edges in grid!
    val selectionThumbnail = RoundedCornerShape(6.dp)
    val pill = RoundedCornerShape(100.dp)
    val card = RoundedCornerShape(16.dp)
    val bottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val navCapsule = RoundedCornerShape(32.dp)
}

object OdinAnimations {
    val springFast = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    val springNormal = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val springSlow = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)

    val springOffset = spring<IntOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
}
