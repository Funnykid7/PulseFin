package com.pulsefin.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import android.view.HapticFeedbackConstants
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView

/**
 * Springy press-scale: the element dips while held and bounces back on release. Reads the
 * button's own [InteractionSource] so the host keeps its onClick, ripple, and semantics — we
 * only animate a [Modifier.scale] (graphicsLayer), so children are never recomposed.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.86f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    // A light tactile tick the instant a press begins. Because bouncyClickable also routes through
    // pressScale, this gives every button, row, and card in the app consistent tap haptics.
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )
    this.scale(scale)
}

/**
 * A clickable with the app's signature press-bounce baked in: dips to [pressedScale] while held
 * and springs back on release, keeping the normal ripple. Use for rows, cards, and any tappable
 * surface that isn't a Material button (buttons take `pressScale` + their own interactionSource).
 */
fun Modifier.bouncyClickable(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .pressScale(interaction, pressedScale)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/** The [SharedTransitionScope] wrapping the NavHost; null until provided (e.g. in previews). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current nav destination's [AnimatedContentScope], for shared-element visibility. */
val LocalNavAnimatedContentScope = staticCompositionLocalOf<AnimatedContentScope?> { null }

/**
 * Tags this element as the shared album artwork for [key], so it morphs between the grid tile
 * and the detail hero during navigation. No-op when the transition scopes aren't provided.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedScope) {
        this@sharedArtwork.sharedElement(
            rememberSharedContentState(key),
            animatedScope,
        )
    }
}

/** Play/pause glyph that morphs (scale + fade) between states instead of hard-cutting. */
@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn()) togetherWith
                (scaleOut(spring(stiffness = Spring.StiffnessMedium)) + fadeOut())
        },
        label = "playPause",
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}
