package com.telegrambackup.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileUtils {

    /**
     * Calculate SHA-256 hash of a file.
     */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Get human-readable file size.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Determine file type from MIME.
     */
    fun getFileType(mimeType: String): com.telegrambackup.data.local.entity.FileType {
        return when {
            mimeType.startsWith("image/") -> com.telegrambackup.data.local.entity.FileType.IMAGE
            mimeType.startsWith("video/") -> com.telegrambackup.data.local.entity.FileType.VIDEO
            mimeType.startsWith("audio/") -> com.telegrambackup.data.local.entity.FileType.AUDIO
            else -> com.telegrambackup.data.local.entity.FileType.DOCUMENT
        }
    }

    /**
     * Get MIME type from file extension.
     */
    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return mimeMap[ext] ?: "application/octet-stream"
    }

    private val mimeMap = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "svg" to "image/svg+xml", "heic" to "image/heic", "heif" to "image/heif",
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "avi" to "video/x-msvideo",
        "mov" to "video/quicktime", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
        "webm" to "video/webm", "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "flac" to "audio/flac",
        "aac" to "audio/aac", "ogg" to "audio/ogg", "m4a" to "audio/mp4",
        "wma" to "audio/x-ms-wma", "opus" to "audio/opus",
        "pdf" to "application/pdf", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "txt" to "text/plain", "zip" to "application/zip",
        "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed",
        "apk" to "application/vnd.android.package-archive"
    )
}
