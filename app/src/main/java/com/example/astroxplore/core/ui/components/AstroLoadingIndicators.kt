package com.example.astroxplore.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
 * Expressive Material 3 Circular Progress Indicator.
 * Includes smooth rotational physics, rounded stroke caps, and optional track glow & label.
 */
@Composable
fun AstroM3LoadingIndicator(
    modifier: Modifier = Modifier,
    size: AstroLoadingSize = AstroLoadingSize.MEDIUM,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
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

            CircularProgressIndicator(
                modifier = Modifier.size(size.dp),
                color = color,
                trackColor = trackColor,
                strokeWidth = size.strokeWidth,
                strokeCap = StrokeCap.Round
            )
        }

        if (!label.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
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
 * Material 3 Linear Progress Indicator docked below top bars during background fetch/push tasks.
 */
@Composable
fun AstroLinearProgressBar(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
) {
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
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
