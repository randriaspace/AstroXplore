package com.example.astroxplore.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Material 3 Loading Sizes for AstroXplore.
 */
enum class AstroLoadingSize(val dp: Dp, val strokeWidth: Dp) {
    SMALL(24.dp, 2.5.dp),
    MEDIUM(44.dp, 4.dp),
    LARGE(64.dp, 5.dp)
}

/**
 * Expressive Material 3 Shimmer Modifier for skeleton loading states.
 */
fun Modifier.astroShimmer(
    enabled: Boolean = true,
    durationMillis: Int = 1200
): Modifier = composed {
    if (!enabled) return@composed this

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    )

    val transition = rememberInfiniteTransition(label = "astro_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "astro_shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim - 500f, y = translateAnim - 500f),
        end = Offset(x = translateAnim, y = translateAnim)
    )

    this.background(brush)
}

/**
 * Official Material 3 Circular Progress Indicator.
 * Supports both Determinate (known progress 0f..1f) and Indeterminate modes,
 * featuring a contrasting continuous track, rounded stroke caps, and smooth animated transitions.
 * Reference: https://m3.material.io/components/progress-indicators/guidelines
 */
@Composable
fun AstroM3CircularProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    size: AstroLoadingSize = AstroLoadingSize.MEDIUM,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = size.strokeWidth,
    showPercentage: Boolean = false,
    label: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size.dp)
        ) {
            // Subtle ambient halo behind the indicator
            Box(
                modifier = Modifier
                    .size(size.dp * 0.9f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.08f))
            )

            if (progress != null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.coerceIn(0f, 1f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "astro_m3_circular_determinate"
                )
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(size.dp),
                    color = color,
                    trackColor = trackColor,
                    strokeWidth = strokeWidth,
                    strokeCap = StrokeCap.Round
                )
                if (showPercentage && size != AstroLoadingSize.SMALL) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = if (size == AstroLoadingSize.LARGE) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(size.dp),
                    color = color,
                    trackColor = trackColor,
                    strokeWidth = strokeWidth,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        if (!label.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Backward-compatible wrapper forwarding to AstroM3CircularProgressIndicator.
 */
@Composable
fun AstroM3LoadingIndicator(
    modifier: Modifier = Modifier,
    size: AstroLoadingSize = AstroLoadingSize.MEDIUM,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    label: String? = null
) {
    AstroM3CircularProgressIndicator(
        progress = null,
        modifier = modifier,
        size = size,
        color = color,
        trackColor = trackColor,
        label = label
    )
}

/**
 * Material 3 Linear Progress Indicator docked below top bars during background fetch/push tasks.
 * Reference: https://m3.material.io/components/progress-indicators/guidelines
 */
@Composable
fun AstroLinearProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 4.dp
) {
    if (progress != null) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "astro_linear_determinate"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2)),
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2)),
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * Official Material 3 Docked Top App Bar Progress Indicator.
 * Smoothly animates into view directly beneath the TopAppBar during sync, pull-to-refresh, or active queries.
 */
@Composable
fun AstroM3DockedLinearProgress(
    visible: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 4.dp
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        AstroLinearProgressBar(
            progress = progress,
            color = color,
            trackColor = trackColor,
            height = height
        )
    }
}

/**
 * Compact inline progress badge for cards, action chips, and buttons.
 */
@Composable
fun AstroCompactProgressBadge(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AstroM3CircularProgressIndicator(
            progress = progress,
            size = AstroLoadingSize.SMALL,
            color = indicatorColor,
            trackColor = trackColor,
            strokeWidth = 2.dp
        )
        if (!statusText.isNullOrBlank()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Full-screen Material 3 loading container with branded presentation.
 */
@Composable
fun AstroFullScreenLoading(
    modifier: Modifier = Modifier,
    message: String = "Loading publications...",
    size: AstroLoadingSize = AstroLoadingSize.LARGE
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AstroM3LoadingIndicator(
                size = size,
                label = message
            )
        }
    }
}

/**
 * Material 3 Paper Card Skeleton Placeholder for Feed and Library lists.
 */
@Composable
fun AstroPaperCardSkeleton(
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Category pill & date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 22.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .astroShimmer()
                )
            }

            // Title Skeleton: 2 lines
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .astroShimmer()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .astroShimmer()
            )

            // Authors Row
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .astroShimmer()
            )

            // Abstract Snippet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .astroShimmer()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .astroShimmer()
            )

            // Footer Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 55.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .size(width = 75.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .astroShimmer()
                )
            }
        }
    }
}

/**
 * Material 3 Details Skeleton Placeholder for the Paper Details Reader screen.
 */
@Composable
fun AstroDetailsSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 90.dp, height = 24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .astroShimmer()
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 70.dp, height = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .astroShimmer()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .astroShimmer()
                )

                // Author Monogram Placeholders
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .astroShimmer()
                        )
                    }
                }
            }
        }

        // Tab Row Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .astroShimmer()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .astroShimmer()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .astroShimmer()
            )
        }

        // Abstract Section Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .astroShimmer()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .astroShimmer()
                )
            }
        }
    }
}
