package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OdinDarkColorScheme = darkColorScheme(
    primary = OdinColors.DarkAccentBlue,
    onPrimary = Color.White,
    primaryContainer = OdinColors.DarkSecondaryBackground,
    onPrimaryContainer = OdinColors.DarkPrimaryText,
    secondary = OdinColors.DarkAccentBlue,
    onSecondary = Color.White,
    secondaryContainer = OdinColors.DarkTertiaryBackground,
    onSecondaryContainer = OdinColors.DarkSecondaryText,
    tertiary = OdinColors.DarkDestructive,
    onTertiary = Color.White,
    background = OdinColors.DarkSystemBackground,
    onBackground = OdinColors.DarkPrimaryText,
    surface = OdinColors.DarkSecondaryBackground,
    onSurface = OdinColors.DarkPrimaryText,
    surfaceVariant = OdinColors.DarkTertiaryBackground,
    onSurfaceVariant = OdinColors.DarkSecondaryText,
    outline = Color(0x26FFFFFF),
    outlineVariant = Color(0x14FFFFFF),
    error = OdinColors.DarkDestructive,
    onError = Color.White
)

private val OdinLightColorScheme = lightColorScheme(
    primary = OdinColors.LightAccentBlue,
    onPrimary = Color.White,
    primaryContainer = OdinColors.LightSecondaryBackground,
    onPrimaryContainer = OdinColors.LightPrimaryText,
    secondary = OdinColors.LightAccentBlue,
    onSecondary = Color.White,
    secondaryContainer = OdinColors.LightTertiaryBackground,
    onSecondaryContainer = OdinColors.LightSecondaryText,
    tertiary = OdinColors.LightDestructive,
    onTertiary = Color.White,
    background = OdinColors.LightBackground,
    onBackground = OdinColors.LightPrimaryText,
    surface = OdinColors.LightSecondaryBackground,
    onSurface = OdinColors.LightPrimaryText,
    surfaceVariant = OdinColors.LightTertiaryBackground,
    onSurfaceVariant = OdinColors.LightSecondaryText,
    outline = Color(0x1F000000),
    outlineVariant = Color(0x0A000000),
    error = OdinColors.LightDestructive,
    onError = Color.White
)

@Composable
fun OdinGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep disciplined photographic OLED palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OdinDarkColorScheme
        else -> OdinLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = OdinGalleryTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
