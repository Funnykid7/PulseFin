package com.pulsefin.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.pulsefin.core.designsystem.R

// Downloadable Google Fonts provider (resolved at runtime via Google Play Services).
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

// Outfit — a rounded geometric display face for headers ("Your Mix", Now Playing, titles).
private val outfit = GoogleFont("Outfit")

@Suppress("ktlint")
private val OutfitFamily = FontFamily(
    Font(googleFont = outfit, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = outfit, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = outfit, fontProvider = provider, weight = FontWeight.Bold),
)

// Inter — a highly legible text face for body copy and lists.
private val inter = GoogleFont("Inter")

@Suppress("ktlint")
private val InterFamily = FontFamily(
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.SemiBold),
)

// M3 Typography: display/headline/titleLarge use Outfit; everything else uses Inter.
// Sizes/line-heights come from the M3 defaults; we swap family + weight (and tighten
// display tracking for a more expressive feel).
private val default = Typography()

internal val PulseTypography = Typography(
    displayLarge = default.displayLarge.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displayMedium = default.displayMedium.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    displaySmall = default.displaySmall.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold),
    headlineLarge = default.headlineLarge.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold),
    headlineMedium = default.headlineMedium.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold),
    headlineSmall = default.headlineSmall.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = default.titleLarge.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = default.titleMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = default.titleSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    bodyLarge = default.bodyLarge.copy(fontFamily = InterFamily),
    bodyMedium = default.bodyMedium.copy(fontFamily = InterFamily),
    bodySmall = default.bodySmall.copy(fontFamily = InterFamily),
    labelLarge = default.labelLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    labelMedium = default.labelMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    labelSmall = default.labelSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
)
