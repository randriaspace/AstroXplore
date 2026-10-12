package com.example.astroxplore.features.search.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.astroxplore.core.ui.animation.expressiveBounce
import com.example.astroxplore.features.search.model.AiResearchBriefing

/**
 * Spotify-algorithm style discovery layout for research papers:
 * - "Made For You (Profile Research Keywords)" - Personalized keywords derived from the user's astrophysics profile & interests
 * - "Algorithmic Research Mixes" - Spotify mix cards with one-tap clean queries
 * - "Trending in Astrophysics" - High-impact topic pills
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpotifyStyleResearchHub(
    briefings: List<AiResearchBriefing>,
    userResearchInterests: List<String>,
    suggestedKeywords: List<String>,
    isLoadingAi: Boolean,
    onBriefingClick: (AiResearchBriefing) -> Unit,
    onKeywordClick: (String) -> Unit,
    onRefreshAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. "MADE FOR YOU" - User Profile Research Keywords (Spotify Daily Mix Pills)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Made For You",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tailored to your research profile & selected topics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(onClick = onRefreshAi, enabled = !isLoadingAi) {
                    if (isLoadingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // User Profile Keywords Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val profileKeywords = if (userResearchInterests.isNotEmpty()) {
                    userResearchInterests
                } else {
                    listOf(
                        "Exoplanet Atmospheres", "JWST Spectroscopy", "Cosmological Expansion",
                        "High Redshift Galaxies", "Stellar Evolution"
                    )
                }

                profileKeywords.forEachIndexed { index, keyword ->
                    SpotifyProfileKeywordPill(
                        keyword = keyword,
                        index = index,
                        onClick = { onKeywordClick(keyword) }
                    )
                }
            }
        }

        // 2. "RESEARCH MIX CAROUSEL" - Deep Algorithmic Mixes from Journal Clubs
        if (briefings.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Journal Club & Literature Mixes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Curated hypotheses combining your clubs and saved preprints",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(briefings, key = { it.id }) { briefing ->
                        SpotifyResearchMixCard(
                            briefing = briefing,
                            onClick = { onBriefingClick(briefing) }
                        )
                    }
                }
            }
        }

        // 3. "TRENDING IN ASTROPHYSICS" - Spotify Genre Pill Cluster
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Trending in Astrophysics",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Most cited & discussed preprints across journals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val curatedTrending = if (suggestedKeywords.isNotEmpty()) {
                    suggestedKeywords
                } else {
                    listOf(
                        "JWST NIRSpec", "Dark Matter Halo", "Supermassive Black Holes",
                        "Gravitational Waves", "Fast Radio Bursts", "Kilonova",
                        "Pop III Stars", "Protoplanetary Disks", "Magnetars"
                    )
                }

                curatedTrending.forEachIndexed { index, keyword ->
                    SpotifyTopicPill(
                        keyword = keyword,
                        index = index,
                        onClick = { onKeywordClick(keyword) }
                    )
                }
            }
        }
    }
}

/**
 * Spotify Profile Keyword Pill:
 * Represents user profile research interest keywords with vibrant gradients and music-tag aesthetic
 */
@Composable
fun SpotifyProfileKeywordPill(
    keyword: String,
    index: Int,
    onClick: () -> Unit
) {
    val cleanKeyword = if (keyword.startsWith("#")) keyword.substring(1) else keyword

    val palette = listOf(
        Color(0xFF1D9BF0) to Color(0xFF0284C7), // Astro Sky
        Color(0xFF8B5CF6) to Color(0xFF6D28D9), // Nebula Violet
        Color(0xFF10B981) to Color(0xFF059669), // Emerald
        Color(0xFFF59E0B) to Color(0xFFD97706), // Solar Gold
        Color(0xFFEC4899) to Color(0xFFDB2777), // Stellar Pink
        Color(0xFF06B6D4) to Color(0xFF0891B2)  // Cyan Pulse
    )
    val (primaryColor, darkColor) = palette[index % palette.size]

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.45f)),
        modifier = Modifier.expressiveBounce(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(primaryColor.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = primaryColor,
                shape = CircleShape,
                modifier = Modifier.size(8.dp)
            ) {}

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = cleanKeyword,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Spotify Card Style for Research Topics:
 * Rich vibrant gradient, distinct color theme per card, cover-art inspired aesthetic,
 * showing the core research hypothesis and clean one-tap search action.
 */
@Composable
fun SpotifyResearchMixCard(
    briefing: AiResearchBriefing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (gradientColors, accentColor) = when {
        briefing.categoryBadge.contains("Club", ignoreCase = true) -> {
            listOf(Color(0xFF1D9BF0).copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainerHigh) to Color(0xFF1D9BF0)
        }
        briefing.categoryBadge.contains("Citation", ignoreCase = true) -> {
            listOf(Color(0xFF8B5CF6).copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainerHigh) to Color(0xFF8B5CF6)
        }
        briefing.categoryBadge.contains("Exoplanet", ignoreCase = true) -> {
            listOf(Color(0xFF10B981).copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainerHigh) to Color(0xFF10B981)
        }
        else -> {
            listOf(Color(0xFFF59E0B).copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainerHigh) to Color(0xFFF59E0B)
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(260.dp)
            .height(290.dp)
            .clip(RoundedCornerShape(26.dp))
            .expressiveBounce(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = accentColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = briefing.categoryBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        if (briefing.targetBibstem != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = briefing.targetBibstem,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = briefing.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = briefing.hypothesisSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column {
                    Text(
                        text = "💡 ${briefing.contextSourceReason}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = accentColor.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Explore ADS Mix",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spotify Topic Pill
 */
@Composable
fun SpotifyTopicPill(
    keyword: String,
    index: Int,
    onClick: () -> Unit
) {
    val cleanKeyword = if (keyword.startsWith("#")) keyword.substring(1) else keyword

    val accentColors = listOf(
        Color(0xFF1D9BF0), // Astro Blue
        Color(0xFF8B5CF6), // Cosmic Violet
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Solar Amber
        Color(0xFFEC4899), // Nebula Magenta
        Color(0xFF06B6D4)  // Cyan
    )
    val accent = accentColors[index % accentColors.size]

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.expressiveBounce(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = cleanKeyword,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
