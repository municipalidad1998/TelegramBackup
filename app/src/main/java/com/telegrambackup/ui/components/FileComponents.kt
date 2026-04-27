package com.telegrambackup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun FileListItem(
    file: BackupFile,
    onClick: () -> Unit,
    onUpload: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (file.fileType == FileType.IMAGE && File(file.filePath).exists()) {
                    AsyncImage(
                        model = file.filePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        when (file.fileType) {
                            FileType.IMAGE -> Icons.Filled.Image
                            FileType.VIDEO -> Icons.Filled.PlayCircle
                            FileType.AUDIO -> Icons.Filled.MusicNote
                            FileType.DOCUMENT -> Icons.Filled.Description
                        },
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${FileUtils.formatFileSize(file.fileSize)} • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(file.dateAdded)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            // Status
            StatusChip(status = file.uploadStatus, progress = file.uploadProgress)

            // Upload button for pending/error
            if (file.uploadStatus == UploadStatus.PENDING || file.uploadStatus == UploadStatus.ERROR) {
                onUpload?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Filled.CloudUpload, "Upload", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: UploadStatus, progress: Int = 0) {
    val (color, text, icon) = when (status) {
        UploadStatus.UPLOADED -> Triple(
            MaterialTheme.colorScheme.secondary,
            "Uploaded",
            Icons.Filled.CheckCircle
        )
        UploadStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.tertiary,
            "Pending",
            Icons.Filled.Schedule
        )
        UploadStatus.UPLOADING -> Triple(
            MaterialTheme.colorScheme.primary,
            "$progress%",
            Icons.Filled.CloudUpload
        )
        UploadStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.error,
            "Error",
            Icons.Filled.Error
        )
        UploadStatus.PAUSED -> Triple(
            MaterialTheme.colorScheme.outline,
            "Paused",
            Icons.Filled.Pause
        )
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun MediaGridItem(
    file: BackupFile,
    onClick: () -> Unit
) {
    val isLocallyAvailable = File(file.filePath).exists()
    val isCloudOnly = !isLocallyAvailable && file.uploadStatus == UploadStatus.UPLOADED

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLocallyAvailable) {
                AsyncImage(
                    model = file.filePath,
                    contentDescription = file.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isCloudOnly) {
                // File deleted locally but backed up — show cloud placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1B3A4B),
                                    Color(0xFF0D2233)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CloudDone,
                            null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFF34D058)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "En la nube",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34D058),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (file.fileType) {
                            FileType.IMAGE -> Icons.Filled.Image
                            FileType.VIDEO -> Icons.Filled.PlayCircle
                            FileType.AUDIO -> Icons.Filled.MusicNote
                            FileType.DOCUMENT -> Icons.Filled.Description
                        },
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Upload status badge (top-right corner)
            if (file.uploadStatus == UploadStatus.UPLOADED && !isCloudOnly) {
                // Small green cloud badge for locally-available uploaded files
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34D058).copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CloudDone,
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                }
            } else if (file.uploadStatus == UploadStatus.UPLOADING) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2AABEE).copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CloudUpload,
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                }
            }

            // Video play indicator (bottom-right)
            if (file.fileType == FileType.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
