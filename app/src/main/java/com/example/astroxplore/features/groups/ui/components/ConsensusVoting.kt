package com.example.astroxplore.features.groups.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.astroxplore.features.groups.model.GroupPaperModel

@Composable
fun ConsensusVoting(
    papers: List<GroupPaperModel>,
    onVote: (String) -> Unit,
    onUnvote: (String) -> Unit,
    onPaperClick: (String) -> Unit = {},
    onNominateForTalk: (GroupPaperModel) -> Unit = {},
    onAddPaperClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (papers.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.HowToVote,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "No Papers in Voting Queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Nominate research preprints to cast votes on what the journal club will read and present in the upcoming session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onAddPaperClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nominate a Paper")
                }
            }
        }
    } else {
        val sorted = papers.sortedByDescending { it.voteCount }
        val maxVotes = (sorted.maxOfOrNull { it.voteCount } ?: 1).coerceAtLeast(1)

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Expressive banner describing voting consensus
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoGraph,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Consensus Voting Queue",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Vote for papers you want analyzed. The top candidate leads the next seminar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            itemsIndexed(sorted, key = { _, item -> item.id ?: item.bibcode }) { index, paper ->
                EnhancedVoteItem(
                    rank = index + 1,
                    paper = paper,
                    maxVotes = maxVotes,
                    onVoteClick = {
                        val paperId = paper.id ?: return@EnhancedVoteItem
                        if (paper.isVotedByMe) onUnvote(paperId)
                        else onVote(paperId)
                    },
                    onPaperClick = { onPaperClick(paper.bibcode) },
                    onNominateClick = { onNominateForTalk(paper) }
                )
            }
        }
    }
}

@Composable
fun EnhancedVoteItem(
    rank: Int,
    paper: GroupPaperModel,
    maxVotes: Int,
    onVoteClick: () -> Unit,
    onPaperClick: () -> Unit,
    onNominateClick: () -> Unit
) {
    val progress = (paper.voteCount.toFloat() / maxVotes.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "voteProgress"
    )

    val containerColor by animateColorAsState(
        targetValue = if (paper.isVotedByMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        label = "voteCardColor"
    )

    val journalBadge = remember(paper.bibcode) { extractJournalBadge(paper.bibcode) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        label = "btnScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (paper.isVotedByMe) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    )
                } else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Rank badge, Journal badge, Bibcode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rank Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when (rank) {
                            1 -> MaterialTheme.colorScheme.primary
                            2 -> MaterialTheme.colorScheme.secondary
                            3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (rank == 1) "★ #1 Top Choice" else "#$rank Candidate",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (rank) {
                                1, 2, 3 -> Color.White
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Glowing Journal Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = journalBadge.containerColor
                    ) {
                        Text(
                            text = journalBadge.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = journalBadge.contentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Bibcode
                Text(
                    text = paper.bibcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = paper.title ?: "Research Paper: ${paper.bibcode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!paper.authors.isNullOrBlank() || !paper.year.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(paper.authors, paper.year).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Consensus Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = if (paper.isVotedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${paper.voteCount} ${if (paper.voteCount == 1) "Vote" else "Votes"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (paper.isVotedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${(progress * 100).toInt()}% Consensus",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row: Read, Schedule Talk, and Tactile Vote Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPaperClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onNominateClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Schedule", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Interactive Tactile Vote Button with Spring Scale Feedback
                FilledTonalButton(
                    onClick = onVoteClick,
                    interactionSource = interactionSource,
                    modifier = Modifier.scale(buttonScale),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (paper.isVotedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        contentColor = if (paper.isVotedByMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (paper.isVotedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = if (paper.isVotedByMe) "Voted" else "Vote",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (paper.isVotedByMe) "Voted" else "Vote",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private data class JournalBadgeInfo(
    val name: String,
    val containerColor: Color,
    val contentColor: Color
)

private fun extractJournalBadge(bibcode: String): JournalBadgeInfo {
    val upper = bibcode.uppercase()
    return when {
        upper.contains("ARXIV") -> JournalBadgeInfo("arXiv", Color(0xFF673AB7).copy(alpha = 0.2f), Color(0xFF9575CD))
        upper.contains("APJ") -> JournalBadgeInfo("ApJ", Color(0xFF2E7D32).copy(alpha = 0.2f), Color(0xFF81C784))
        upper.contains("MNRAS") -> JournalBadgeInfo("MNRAS", Color(0xFFE65100).copy(alpha = 0.2f), Color(0xFFFFB74D))
        upper.contains("A&A") || upper.contains("AA") -> JournalBadgeInfo("A&A", Color(0xFF00838F).copy(alpha = 0.2f), Color(0xFF4DD0E1))
        upper.contains("NATUR") -> JournalBadgeInfo("Nature", Color(0xFFC2185B).copy(alpha = 0.2f), Color(0xFFF06292))
        upper.contains("PRD") || upper.contains("PRL") -> JournalBadgeInfo("Phys Rev", Color(0xFFD84315).copy(alpha = 0.2f), Color(0xFFFF8A65))
        upper.contains("AJ") -> JournalBadgeInfo("AJ", Color(0xFF1565C0).copy(alpha = 0.2f), Color(0xFF64B5F6))
        else -> JournalBadgeInfo("ADS", Color(0xFF455A64).copy(alpha = 0.2f), Color(0xFFB0BEC5))
    }
}
