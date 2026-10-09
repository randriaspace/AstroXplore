package com.example.astroxplore.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.astroxplore.core.database.entity.DownloadState
import com.example.astroxplore.core.ui.animation.expressiveBounce

/**
 * Modern floating bottom action dock for primary document reading & group interactions.
 */
@Composable
fun PrimaryActionDock(
    onReadPdfClick: () -> Unit,
    onSecondaryActionClick: () -> Unit,
    downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    downloadProgress: Int = 0,
    hasPdfUrl: Boolean = true,
    secondaryButtonText: String = "Journal Club",
    secondaryButtonIcon: ImageVector = Icons.Outlined.Groups,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadState == DownloadState.DOWNLOADING

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary Read PDF / Open Publisher Button with Download States
            Button(
                onClick = onReadPdfClick,
                enabled = !isDownloading,
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .expressiveBounce()
                    .testTag("action_dock_read_pdf"),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasPdfUrl) {
                        // External paper without downloadable PDF
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Open Publisher Link",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        when (downloadState) {
                            DownloadState.DOWNLOADING -> {
                                val progressFloat = if (downloadProgress > 0) downloadProgress / 100f else null
                                AstroM3CircularProgressIndicator(
                                    progress = progressFloat,
                                    size = AstroLoadingSize.SMALL,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                    strokeWidth = 2.5.dp
                                )
                                val statusLabel = if (downloadProgress > 0) "Saving ($downloadProgress%)" else "Connecting..."
                                Text(
                                    text = statusLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            DownloadState.DOWNLOADED -> {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Read Offline",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Read PDF",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Secondary Action Button (e.g. Journal Club / Share)
            OutlinedButton(
                onClick = onSecondaryActionClick,
                modifier = Modifier
                    .weight(1.1f)
                    .height(52.dp)
                    .expressiveBounce()
                    .testTag("action_dock_secondary"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = secondaryButtonIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = secondaryButtonText,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
