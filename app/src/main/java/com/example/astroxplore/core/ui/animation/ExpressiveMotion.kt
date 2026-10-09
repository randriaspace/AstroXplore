package com.example.astroxplore.core.ui.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Material 3 Expressive Spring Motion Physics.
 * Provides subtle organic bounce and responsive tactile feedback on interactive elements.
 * Reference: https://m3.material.io/blog/building-with-m3-expressive
 */
object ExpressiveMotion {
    val BouncySpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SmoothSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    val FluidSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Adds an expressive spring-based tactile press feedback to any component.
 * Scales down smoothly on press and springs back organically on release.
 */
fun Modifier.expressiveBounce(
    scaleDown: Float = 0.965f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = ExpressiveMotion.BouncySpring,
        label = "expressive_bounce_scale"
    )

    this
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null, // Custom spring bounce replaces default ripple or pairs with it
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}
