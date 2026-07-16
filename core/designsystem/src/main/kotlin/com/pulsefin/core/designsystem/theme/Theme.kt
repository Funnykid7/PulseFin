package com.pulsefin.core.designsystem.theme

import android.os.Build
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
    secondary = PulseDarkSecondary,
    onSecondary = PulseDarkOnSecondary,
    secondaryContainer = PulseDarkSecondaryContainer,
    onSecondaryContainer = PulseDarkOnSecondaryContainer,
    tertiary = PulseDarkTertiary,
    onTertiary = PulseDarkOnTertiary,
    tertiaryContainer = PulseDarkTertiaryContainer,
    onTertiaryContainer = PulseDarkOnTertiaryContainer,
    background = PulseDarkBackground,
    onBackground = PulseDarkOnBackground,
    surface = PulseDarkSurface,
    onSurface = PulseDarkOnSurface,
    surfaceVariant = PulseDarkSurfaceVariant,
    onSurfaceVariant = PulseDarkOnSurfaceVariant,
    surfaceContainerLow = PulseDarkSurfaceContainerLow,
    surfaceContainer = PulseDarkSurfaceContainer,
    surfaceContainerHigh = PulseDarkSurfaceContainerHigh,
    surfaceContainerHighest = PulseDarkSurfaceContainerHighest,
    outline = PulseDarkOutline,
    outlineVariant = PulseDarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = PulseLightPrimary,
    onPrimary = PulseLightOnPrimary,
    primaryContainer = PulseLightPrimaryContainer,
    onPrimaryContainer = PulseLightOnPrimaryContainer,
    secondary = PulseLightSecondary,
    onSecondary = PulseLightOnSecondary,
    secondaryContainer = PulseLightSecondaryContainer,
    onSecondaryContainer = PulseLightOnSecondaryContainer,
    tertiary = PulseLightTertiary,
    onTertiary = PulseLightOnTertiary,
    tertiaryContainer = PulseLightTertiaryContainer,
    onTertiaryContainer = PulseLightOnTertiaryContainer,
    background = PulseLightBackground,
    onBackground = PulseLightOnBackground,
    surface = PulseLightSurface,
    onSurface = PulseLightOnSurface,
    surfaceVariant = PulseLightSurfaceVariant,
    onSurfaceVariant = PulseLightOnSurfaceVariant,
    surfaceContainerLow = PulseLightSurfaceContainerLow,
    surfaceContainer = PulseLightSurfaceContainer,
    surfaceContainerHigh = PulseLightSurfaceContainerHigh,
    surfaceContainerHighest = PulseLightSurfaceContainerHighest,
    outline = PulseLightOutline,
    outlineVariant = PulseLightOutlineVariant,
)

/**
 * PulseFin's Material 3 theme.
 *
 * Dark-first per the product spec: [darkTheme] defaults to true so the app is dark
 * regardless of the system setting. Dynamic Color (Monet) is on by default and, since
 * minSdk is 31, is always available; per-screen theming from album art layers on later.
 */
@Composable
fun PulseFinTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseTypography,
        shapes = PulseShapes,
        content = content,
    )
}
