package com.pulsefin.app.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * Re-themes [content] to a Material 3 tonal (Monet) scheme derived from the given album art.
 * Falls back to the ambient theme until the art loads / if extraction fails. Seed extraction
 * uses Palette; the full tonal scheme is built with material-color-utilities (no Compose
 * dependency, so it's safe against our bleeding-edge material3).
 */
@Composable
fun ArtworkTheme(artworkUrl: String?, content: @Composable () -> Unit) {
    val scheme = rememberArtworkColorScheme(artworkUrl)
    MaterialTheme(
        colorScheme = scheme ?: MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

@Composable
fun rememberArtworkColorScheme(artworkUrl: String?): ColorScheme? {
    val context = LocalContext.current
    var scheme by remember(artworkUrl) { mutableStateOf<ColorScheme?>(null) }
    LaunchedEffect(artworkUrl) {
        scheme = artworkUrl?.let { url ->
            extractSeedColor(context, url)?.let(::schemeFromSeed)
        }
    }
    return scheme
}

private suspend fun extractSeedColor(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .allowHardware(false) // Palette needs to read pixels
        .size(160)
        .build()
    val drawable = context.imageLoader.execute(request).drawable ?: return null
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
    val palette = Palette.from(bitmap).generate()
    return palette.vibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
}

private fun schemeFromSeed(seed: Int): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), true, 0.0)
    val colors = MaterialDynamicColors()
    fun color(dynamic: DynamicColor) = Color(dynamic.getArgb(scheme))
    return darkColorScheme(
        primary = color(colors.primary()),
        onPrimary = color(colors.onPrimary()),
        primaryContainer = color(colors.primaryContainer()),
        onPrimaryContainer = color(colors.onPrimaryContainer()),
        secondary = color(colors.secondary()),
        onSecondary = color(colors.onSecondary()),
        secondaryContainer = color(colors.secondaryContainer()),
        onSecondaryContainer = color(colors.onSecondaryContainer()),
        tertiary = color(colors.tertiary()),
        onTertiary = color(colors.onTertiary()),
        tertiaryContainer = color(colors.tertiaryContainer()),
        onTertiaryContainer = color(colors.onTertiaryContainer()),
        background = color(colors.background()),
        onBackground = color(colors.onBackground()),
        surface = color(colors.surface()),
        onSurface = color(colors.onSurface()),
        surfaceVariant = color(colors.surfaceVariant()),
        onSurfaceVariant = color(colors.onSurfaceVariant()),
        surfaceContainerLow = color(colors.surfaceContainerLow()),
        surfaceContainer = color(colors.surfaceContainer()),
        surfaceContainerHigh = color(colors.surfaceContainerHigh()),
        surfaceContainerHighest = color(colors.surfaceContainerHighest()),
        outline = color(colors.outline()),
        outlineVariant = color(colors.outlineVariant()),
    )
}
