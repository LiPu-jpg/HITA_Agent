package com.hita.agent.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = HitaColors.Primary,
    onPrimary = HitaColors.BackgroundSecond,
    primaryContainer = HitaColors.PrimaryFade,
    onPrimaryContainer = HitaColors.BackgroundSecond,
    secondary = HitaColors.ColorFade,
    onSecondary = HitaColors.TextPrimary,
    secondaryContainer = HitaColors.BackgroundSecond,
    onSecondaryContainer = HitaColors.TextPrimary,
    tertiary = HitaColors.PrimaryFade,
    onTertiary = HitaColors.BackgroundSecond,
    background = HitaColors.BackgroundBottom,
    onBackground = HitaColors.TextPrimary,
    surface = HitaColors.BackgroundSecond,
    onSurface = HitaColors.TextPrimary,
    surfaceVariant = HitaColors.BackgroundBar,
    onSurfaceVariant = HitaColors.TextSecondary,
    outline = HitaColors.ControlDisabled,
    outlineVariant = HitaColors.PrimaryDisabled
)

private val DarkColorScheme = darkColorScheme(
    primary = HitaColors.Primary,
    onPrimary = HitaColors.BackgroundSecond,
    primaryContainer = HitaColors.PrimaryFade,
    onPrimaryContainer = HitaColors.BackgroundSecond,
    secondary = HitaColors.ColorFade,
    onSecondary = HitaColors.TextPrimary,
    secondaryContainer = HitaColors.BackgroundSecond,
    onSecondaryContainer = HitaColors.TextPrimary,
    tertiary = HitaColors.PrimaryFade,
    onTertiary = HitaColors.BackgroundSecond,
    background = HitaColors.BackgroundBottom,
    onBackground = HitaColors.TextPrimary,
    surface = HitaColors.BackgroundSecond,
    onSurface = HitaColors.TextPrimary,
    surfaceVariant = HitaColors.BackgroundBar,
    onSurfaceVariant = HitaColors.TextSecondary,
    outline = HitaColors.ControlDisabled,
    outlineVariant = HitaColors.PrimaryDisabled
)

@Composable
fun HitaAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HitaTypography.typography,
        content = content
    )
}
