package com.pulsefin.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Subtle squircle for list/grid/mini-player thumbnails (percent-based, scales with size). */
val SquircleShape: Shape = RoundedCornerShape(percent = 28)

/** Rounded square for the Now Playing hero art. */
val RoundedHeroShape: Shape = RoundedCornerShape(32.dp)

/** Bold M3 Expressive scalloped "cookie" for the play/pause button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun cookieShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

/** A distinct clover shape for the search action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun searchShape(): Shape = MaterialShapes.Clover4Leaf.toShape()
