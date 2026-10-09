package com.example.astroxplore.features.feed.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.astroxplore.core.ui.components.BibTeXCodeBlock
import com.example.astroxplore.core.ui.components.CollapsibleSection
import com.example.astroxplore.core.ui.components.MetadataMetricBadge
import com.example.astroxplore.core.ui.components.PublicationDetailRow
import com.example.astroxplore.features.feed.model.PaperModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTabContent(
    paper: PaperModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Metrics Badges
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricBadge(
                label = "Citations",
                value = "${paper.citationCount}",
                icon = Icons.Outlined.FormatQuote
            )
            paper.arxivId?.let { arxiv ->
                MetadataMetricBadge(
                    label = "arXiv ID",
                    value = arxiv,
                    icon = Icons.Outlined.Tag
                )
            }
            if (paper.bibcode.isNotBlank()) {
                MetadataMetricBadge(
                    label = "Bibcode",
                    value = paper.bibcode.take(12),
                    icon = Icons.Outlined.Bookmark
                )
            }
        }

        // Abstract Section with smooth collapsible reveal
        CollapsibleSection(
            title = "Abstract",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            initiallyExpanded = true
        ) {
            AstroAbstractView(
                rawAbstract = paper.abstractText,
                isExpanded = true
            )
        }
    }
}

@Composable
fun MetricsTabContent(
    paper: PaperModel,
    onOpenAds: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Impact Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CITATION IMPACT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${paper.citationCount}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "tracked citations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        // Publication Metadata Section
        CollapsibleSection(
            title = "Publication Metadata",
            icon = Icons.Outlined.Info,
            initiallyExpanded = true
        ) {
            PublicationDetailRow(label = "Published Date", value = paper.dateDisplay)
            PublicationDetailRow(label = "Bibcode", value = paper.bibcode)
            paper.arxivId?.let { arxiv ->
                PublicationDetailRow(label = "arXiv Identifier", value = arxiv)
            }
            if (paper.category.isNotBlank()) {
                PublicationDetailRow(label = "Primary Category", value = paper.category)
            }
            paper.rawPubDate?.let { pubDate ->
                PublicationDetailRow(label = "Raw Release Stamp", value = pubDate)
            }
        }

        // External ADS Portal Link Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NASA ADS Abstract Service",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View peer reviews and citation tree on ADS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onOpenAds) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Launch,
                        contentDescription = "Open NASA ADS"
                    )
                }
            }
        }
    }
}

@Composable
fun BibTeXTabContent(
    bibtex: String,
    onCopyBibTeX: () -> Unit,
    onShareBibTeX: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BibTeXCodeBlock(
            bibtexCode = bibtex,
            onCopyClick = onCopyBibTeX,
            onShareClick = onShareBibTeX
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Standard BibTeX record generated for use with Overleaf, LaTeX, Zotero, or Mendeley.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun PaperModel.toBibTeX(): String = buildString {
    append("@ARTICLE{").append(bibcode).append(",\n")
    if (authors.isNotEmpty()) {
        append("  author = {").append(authors.joinToString(" and ")).append("},\n")
    }
    append("  title = {").append(title).append("},\n")
    rawPubDate?.take(4)?.toIntOrNull()?.let { year ->
        append("  year = {").append(year).append("},\n")
    }
    append("  bibcode = {").append(bibcode).append("}\n}")
}
