package com.telegrambackup.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_files",
    indices = [
        Index(value = ["fileHash"], unique = true),
        Index(value = ["uploadStatus"]),
        Index(value = ["dateAdded"]),
        Index(value = ["fileType"])
    ]
)
data class BackupFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,          // original local path
    val fileSize: Long,            // bytes
    val fileHash: String,          // SHA-256 for dedup
    val mimeType: String,
    val fileType: FileType,        // IMAGE, VIDEO, AUDIO, DOCUMENT
    val dateAdded: Long,           // epoch millis
    val dateModified: Long = dateAdded,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val telegramFileId: String? = null,  // Telegram file_id after upload
    val telegramMessageId: Long? = null,
    val uploadProgress: Int = 0,   // 0-100
    val uploadDate: Long? = null,
    val errorMessage: String? = null,
    val thumbnailPath: String? = null,
    val uploadedToChatId: String? = null   // which Telegram chat this was uploaded to
)

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT
}

enum class UploadStatus {
    PENDING, UPLOADING, UPLOADED, ERROR, PAUSED
}
