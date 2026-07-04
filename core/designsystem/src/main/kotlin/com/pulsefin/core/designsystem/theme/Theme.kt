package com.pulsefin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PulseDarkPrimary,
    onPrimary = PulseDarkOnPrimary,
    primaryContainer = PulseDarkPrimaryContainer,
    onPrimaryContainer = PulseDarkOnPrimaryContainer,
    background = PulseDarkBackground,
    onBackground = PulseDarkOnBackground,
    surface = PulseDarkSurface,
    onSurface = PulseDarkOnSurface,
    surfaceVariant = PulseDarkSurfaceVariant,
    onSurfaceVariant = PulseDarkOnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = PulseLightPrimary,
    onPrimary = PulseLightOnPrimary,
    primaryContainer = PulseLightPrimaryContainer,
    onPrimaryContainer = PulseLightOnPrimaryContainer,
    background = PulseLightBackground,
    onBackground = PulseLightOnBackground,
    surface = PulseLightSurface,
    onSurface = PulseLightOnSurface,
    surfaceVariant = PulseLightSurfaceVariant,
    onSurfaceVariant = PulseLightOnSurfaceVariant,
)

/**
 * PulseFin's Material 3 (Expressive) theme.
 *
 * Dark-mode first per the product spec. Dynamic Color (Monet) is enabled by default and,
 * since minSdk is 31, is always available system-wide; per-screen theming derived from
 * album art (Now Playing, Library, etc.) is layered on top of this at the feature level.
 */
@Composable
fun PulseFinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseTypography,
        content = content,
    )
}
