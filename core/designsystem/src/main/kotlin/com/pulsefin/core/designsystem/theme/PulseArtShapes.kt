package com.pulsefin.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * Expressive album-art shapes. Per the design direction: a bold scalloped shape for the
 * Now Playing hero art, and a soft squircle for list/grid/mini-player thumbnails.
 */

/** Subtle squircle for thumbnails (percent-based so it scales with the art size). */
val SquircleShape: Shape = RoundedCornerShape(percent = 28)

/** Bold M3 Expressive scalloped "cookie" shape for the Now Playing hero art. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun heroArtShape(): Shape = MaterialShapes.Cookie12Sided.toShape()
