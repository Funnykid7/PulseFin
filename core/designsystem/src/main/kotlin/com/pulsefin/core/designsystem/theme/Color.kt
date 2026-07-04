package com.pulsefin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Dark-first fallback palette used when dynamic color (Monet) is unavailable (< Android 12
// won't happen given minSdk 31, but also for previews and desaturated album art).
internal val PulseDarkPrimary = Color(0xFFC9BFFF)
internal val PulseDarkOnPrimary = Color(0xFF322A5E)
internal val PulseDarkPrimaryContainer = Color(0xFF493F77)
internal val PulseDarkOnPrimaryContainer = Color(0xFFE6DEFF)
internal val PulseDarkBackground = Color(0xFF121016)
internal val PulseDarkOnBackground = Color(0xFFE6E1E9)
internal val PulseDarkSurface = Color(0xFF121016)
internal val PulseDarkOnSurface = Color(0xFFE6E1E9)
internal val PulseDarkSurfaceVariant = Color(0xFF48454E)
internal val PulseDarkOnSurfaceVariant = Color(0xFFCAC4CF)

internal val PulseLightPrimary = Color(0xFF61568F)
internal val PulseLightOnPrimary = Color(0xFFFFFFFF)
internal val PulseLightPrimaryContainer = Color(0xFFE6DEFF)
internal val PulseLightOnPrimaryContainer = Color(0xFF1D1149)
internal val PulseLightBackground = Color(0xFFFDF8FF)
internal val PulseLightOnBackground = Color(0xFF1C1B20)
internal val PulseLightSurface = Color(0xFFFDF8FF)
internal val PulseLightOnSurface = Color(0xFF1C1B20)
internal val PulseLightSurfaceVariant = Color(0xFFE6E0EC)
internal val PulseLightOnSurfaceVariant = Color(0xFF48454E)
