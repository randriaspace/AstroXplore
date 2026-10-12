package com.example.astroxplore.features.feed.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.ScatterPlot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * An immersive, expressive Material 3 loading component for astrophysics research papers.
 * Features:
 * - Dynamic cosmic orbital constellation rings with pulsating nodes
 * - Radiant celestial core with organic breathing scale
 * - Rotating telemetry captions simulating astronomical observatory instrument queries
 * - Expressive rounded shimmer cards
 */
@Composable
fun CosmicConstellationFeedLoader(
    modifier: Modifier = Modifier
) {
    val telemetryMessages = remember {
        listOf(
            "Calibrating orbital telescope telemetry...",
            "Querying NASA ADS astrophysics database...",
            "Aligning interferometer & citation clusters...",
            "Synthesizing high-energy astrophysics preprints...",
            "Filtering peer-reviewed & preprint publications..."
        )
    }

    var messageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2400L)
            messageIndex = (messageIndex + 1) % telemetryMessages.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expressive Cosmic Orbital Constellation Centerpiece
        Box(
            modifier = Modifier
                .size(200.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            CosmicOrbitalConstellationCanvas(
                modifier = Modifier.fillMaxSize()
            )

            // Celestial Core Icon with breathing glow
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Cosmic Scanner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animated Telemetry Captions
        AnimatedContent(
            targetState = telemetryMessages[messageIndex],
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "telemetry_text"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Aggregating latest astrophysical discoveries & arXiv archives",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Staggered Expressive 26dp Celestial Shimmer Cards
        repeat(2) { index ->
            CosmicShimmerPaperCard(staggerOffset = index * 200)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CosmicOrbitalConstellationCanvas(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic_rotation")

    // Slow orbital rotation
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    // Pulsing core breathing
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // Node harmonic oscillation
    val nodeGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "node_glow"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width.coerceAtMost(size.height) / 2f

        // 1. Ambient celestial radial glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.35f * pulseGlow),
                    tertiaryColor.copy(alpha = 0.12f * pulseGlow),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius * 0.95f
            ),
            radius = maxRadius * 0.95f,
            center = center
        )

        // 2. Concentric Orbital Rings (dashed & faint)
        val ring1Radius = maxRadius * 0.42f
        val ring2Radius = maxRadius * 0.68f
        val ring3Radius = maxRadius * 0.90f

        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 16f), orbitAngle)

        drawCircle(
            color = primaryColor.copy(alpha = 0.25f),
            radius = ring1Radius,
            center = center,
            style = Stroke(width = 1.5f, pathEffect = dashedEffect)
        )

        drawCircle(
            color = tertiaryColor.copy(alpha = 0.20f),
            radius = ring2Radius,
            center = center,
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 22f), -orbitAngle))
        )

        drawCircle(
            color = primaryColor.copy(alpha = 0.15f),
            radius = ring3Radius,
            center = center,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 30f), orbitAngle * 1.5f))
        )

        // 3. Dynamic Constellation Nodes on Rings
        val radAngle = Math.toRadians(orbitAngle.toDouble())
        val nodeCount = 5
        val nodePositions = mutableListOf<Offset>()

        for (i in 0 until nodeCount) {
            val angleOffset = (i * (360.0 / nodeCount)) * (Math.PI / 180.0)
            val currentAngle = radAngle + angleOffset
            val ringRadius = when (i % 3) {
                0 -> ring1Radius
                1 -> ring2Radius
                else -> ring3Radius
            }
            val x = center.x + (ringRadius * cos(currentAngle)).toFloat()
            val y = center.y + (ringRadius * sin(currentAngle)).toFloat()
            val pos = Offset(x, y)
            nodePositions.add(pos)

            // Draw glowing node
            drawCircle(
                color = primaryColor.copy(alpha = 0.3f * nodeGlow),
                radius = 7f,
                center = pos
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f * nodeGlow),
                radius = 3.5f,
                center = pos
            )
        }

        // 4. Connect constellation vector chords
        for (i in 0 until nodePositions.size) {
            val next = nodePositions[(i + 2) % nodePositions.size]
            drawLine(
                color = primaryColor.copy(alpha = 0.20f * pulseGlow),
                start = nodePositions[i],
                end = next,
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun CosmicShimmerPaperCard(
    staggerOffset: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = staggerOffset, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            surfaceColor,
            highlightColor,
            surfaceColor
        ),
        start = Offset(shimmerTranslate, shimmerTranslate),
        end = Offset(shimmerTranslate + 250f, shimmerTranslate + 250f)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .background(shimmerBrush)
                .padding(20.dp)
        ) {
            // Category & publication pill shimmer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 24.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title headline shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(22.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                        RoundedCornerShape(6.dp)
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(22.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                        RoundedCornerShape(6.dp)
                    )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Authors & abstract lines
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(4.dp)
                    )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp)
                    )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom action dock pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 28.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(14.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 65.dp, height = 28.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(14.dp)
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 36.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape
                        )
                )
            }
        }
    }
}
