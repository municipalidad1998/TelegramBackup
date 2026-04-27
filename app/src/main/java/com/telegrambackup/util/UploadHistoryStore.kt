package com.telegrambackup.util

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class UploadRecord(
        val fileHash: String,
        val telegramFileId: String,
        val chatId: String,
        val messageId: Long,
        val timestamp: Long,
        val filePath: String
    )

    private val historyFile get() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "TelegramBackup/upload_history.log"
    )

    fun record(fileHash: String, filePath: String, telegramFileId: String, chatId: String, messageId: Long) {
        try {
            historyFile.parentFile?.mkdirs()
            // Escape pipe chars in path just in case
            val safePath = filePath.replace("|", "//PIPE//")
            historyFile.appendText("$fileHash|$telegramFileId|$chatId|$messageId|${System.currentTimeMillis()}|$safePath\n")
        } catch (e: Exception) {
            Log.w("UploadHistory", "Failed to record upload: ${e.message}")
        }
    }

    fun readAll(): List<UploadRecord> {
        return try {
            if (!historyFile.exists()) return emptyList()
            historyFile.readLines().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 5) {
                    UploadRecord(
                        fileHash = parts[0],
                        telegramFileId = parts[1],
                        chatId = parts[2],
                        messageId = parts[3].toLongOrNull() ?: 0L,
                        timestamp = parts[4].toLongOrNull() ?: 0L,
                        filePath = if (parts.size >= 6) parts[5].replace("//PIPE//", "|") else ""
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e("UploadHistory", "Failed to read history: ${e.message}")
            emptyList()
        }
    }

    fun exists(): Boolean = historyFile.exists() && historyFile.length() > 0
}
