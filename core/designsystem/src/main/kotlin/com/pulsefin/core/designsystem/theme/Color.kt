package com.pulsefin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Dark-first brand palette (mauve primary, warm pink accent). Used when dynamic color
// (Monet) is off; per-screen album-art theming layers on top at the feature level later.
internal val PulseDarkPrimary = Color(0xFFCDBDFF)
internal val PulseDarkOnPrimary = Color(0xFF33265C)
internal val PulseDarkPrimaryContainer = Color(0xFF493C74)
internal val PulseDarkOnPrimaryContainer = Color(0xFFE9DDFF)

internal val PulseDarkSecondary = Color(0xFFCBC2DB)
internal val PulseDarkOnSecondary = Color(0xFF332D41)
internal val PulseDarkSecondaryContainer = Color(0xFF494458)
internal val PulseDarkOnSecondaryContainer = Color(0xFFE8DEF8)

internal val PulseDarkTertiary = Color(0xFFF0B7C6)
internal val PulseDarkOnTertiary = Color(0xFF4A2531)
internal val PulseDarkTertiaryContainer = Color(0xFF633B47)
internal val PulseDarkOnTertiaryContainer = Color(0xFFFFD9E2)

internal val PulseDarkBackground = Color(0xFF121016)
internal val PulseDarkOnBackground = Color(0xFFE7E1EA)
internal val PulseDarkSurface = Color(0xFF121016)
internal val PulseDarkOnSurface = Color(0xFFE7E1EA)
internal val PulseDarkSurfaceVariant = Color(0xFF48454E)
internal val PulseDarkOnSurfaceVariant = Color(0xFFCAC4CF)

internal val PulseDarkSurfaceContainerLow = Color(0xFF1B1A20)
internal val PulseDarkSurfaceContainer = Color(0xFF201E25)
internal val PulseDarkSurfaceContainerHigh = Color(0xFF2A282F)
internal val PulseDarkSurfaceContainerHighest = Color(0xFF35323A)

internal val PulseDarkOutline = Color(0xFF948F99)
internal val PulseDarkOutlineVariant = Color(0xFF48454E)

// Light fallback (kept valid; the app forces dark by default per the product spec). Secondary/
// tertiary/neutral roles mirror the dark scheme's own values, which are themselves the stock
// Material3 baseline purple tones for those roles — same hue family as this app's custom primary.
internal val PulseLightPrimary = Color(0xFF61568F)
internal val PulseLightOnPrimary = Color(0xFFFFFFFF)
internal val PulseLightPrimaryContainer = Color(0xFFE6DEFF)
internal val PulseLightOnPrimaryContainer = Color(0xFF1D1149)

internal val PulseLightSecondary = Color(0xFF625B71)
internal val PulseLightOnSecondary = Color(0xFFFFFFFF)
internal val PulseLightSecondaryContainer = Color(0xFFE8DEF8)
internal val PulseLightOnSecondaryContainer = Color(0xFF1E192B)

internal val PulseLightTertiary = Color(0xFF7D5260)
internal val PulseLightOnTertiary = Color(0xFFFFFFFF)
internal val PulseLightTertiaryContainer = Color(0xFFFFD8E4)
internal val PulseLightOnTertiaryContainer = Color(0xFF31111D)

internal val PulseLightBackground = Color(0xFFFDF8FF)
internal val PulseLightOnBackground = Color(0xFF1C1B20)
internal val PulseLightSurface = Color(0xFFFDF8FF)
internal val PulseLightOnSurface = Color(0xFF1C1B20)
internal val PulseLightSurfaceVariant = Color(0xFFE6E0EC)
internal val PulseLightOnSurfaceVariant = Color(0xFF48454E)

internal val PulseLightSurfaceContainerLow = Color(0xFFF7F2FA)
internal val PulseLightSurfaceContainer = Color(0xFFF1ECF4)
internal val PulseLightSurfaceContainerHigh = Color(0xFFECE6F0)
internal val PulseLightSurfaceContainerHighest = Color(0xFFE6E0E9)

internal val PulseLightOutline = Color(0xFF79747E)
internal val PulseLightOutlineVariant = Color(0xFFCAC4D0)
