package com.example.astroxplore.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/**
 * Modern floating bottom action dock for primary document reading & group interactions.
 */
@Composable
fun PrimaryActionDock(
    onReadPdfClick: () -> Unit,
    onSecondaryActionClick: () -> Unit,
    downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    downloadProgress: Int = 0,
    secondaryButtonText: String = "Journal Club",
    secondaryButtonIcon: ImageVector = Icons.Outlined.Groups,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary Read PDF Button with Download States
            Button(
                onClick = onReadPdfClick,
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .testTag("action_dock_read_pdf"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (downloadState) {
                        DownloadState.DOWNLOADING -> {
                            CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Saving ($downloadProgress%)",
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

            // Secondary Action Button (e.g. Journal Club / Share)
            OutlinedButton(
                onClick = onSecondaryActionClick,
                modifier = Modifier
                    .weight(1.1f)
                    .height(48.dp)
                    .testTag("action_dock_secondary"),
                shape = RoundedCornerShape(14.dp),
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
