package com.telegrambackup.data.repository

import android.content.Context
import android.util.Log
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.dao.PlaylistDao
import com.telegrambackup.data.local.entity.*
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.network.TelegramApiService
import com.telegrambackup.util.FileUtils
import com.telegrambackup.util.MediaScanner
import com.telegrambackup.util.NetworkUtils
import com.telegrambackup.util.UploadHistoryStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupFileDao: BackupFileDao,
    private val playlistDao: PlaylistDao,
    private val telegramApi: TelegramApiService,
    private val preferences: AppPreferences,
    private val mediaScanner: MediaScanner,
    private val networkUtils: NetworkUtils,
    private val historyStore: UploadHistoryStore
) {
    companion object {
        private const val TAG = "BackupRepository"
    }

    // ===================== File Operations =====================

    fun getAllFiles(): Flow<List<BackupFile>> = backupFileDao.getAllFiles()
    fun getFilesByType(type: FileType): Flow<List<BackupFile>> = backupFileDao.getFilesByType(type)
    fun getPendingFiles(): Flow<List<BackupFile>> = backupFileDao.getPendingFiles()
    fun getDistinctDays(): Flow<List<Long>> = backupFileDao.getDistinctDays()
    fun getFilesByDateRange(start: Long, end: Long): Flow<List<BackupFile>> =
        backupFileDao.getFilesByDateRange(start, end)

    fun getTotalCount(): Flow<Int> = backupFileDao.getTotalCount()
    fun getUploadedCount(): Flow<Int> = backupFileDao.getUploadedCount()
    fun getPendingCount(): Flow<Int> = backupFileDao.getPendingCount()
    fun getTotalUploadedSize(): Flow<Long?> = backupFileDao.getTotalUploadedSize()

    suspend fun getFileById(id: Long): BackupFile? = backupFileDao.getFileById(id)

    // ===================== Scan & Register =====================

    suspend fun scanAndRegisterNewFiles(): Int = withContext(Dispatchers.IO) {
        try {
            val scanned = try {
                mediaScanner.scanAllMedia()
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied scanning media", e)
                return@withContext 0
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning media", e)
                return@withContext 0
            }

            var newCount = 0

            for (sf in scanned) {
                try {
                    val file = File(sf.path)
                    if (!file.exists() || !file.canRead()) continue
                    if (file.length() <= 0L) continue

                    // Check duplicate by path first (fast)
                    val existingByPath = backupFileDao.getFileByPath(sf.path)
                    if (existingByPath != null) continue

                    // Then check by hash (slower, but catches moved/renamed files)
                    val hash = try {
                        FileUtils.sha256(file)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot hash file: ${sf.path}", e)
                        continue
                    }

                    val existingByHash = backupFileDao.getFileByHash(hash)
                    if (existingByHash != null) continue

                    val backupFile = BackupFile(
                        fileName = sf.name,
                        filePath = sf.path,
                        fileSize = sf.size,
                        fileHash = hash,
                        mimeType = sf.mimeType,
                        fileType = sf.fileType,
                        dateAdded = sf.dateAdded,
                        dateModified = sf.dateModified,
                        uploadStatus = UploadStatus.PENDING
                    )
                    backupFileDao.insert(backupFile)
                    newCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing file: ${sf.path}", e)
                    continue
                }
            }

            Log.i(TAG, "Scan complete: $newCount new files found")
            newCount
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            0
        }
    }

    // ===================== Upload =====================

    suspend fun uploadFile(fileId: Long, onProgress: ((Int) -> Unit)? = null): Result<BackupFile> {
        return withContext(Dispatchers.IO) {
            try {
                val file = backupFileDao.getFileById(fileId)
                    ?: return@withContext Result.failure(Exception("File not found in database"))

                if (file.uploadStatus == UploadStatus.UPLOADED) {
                    return@withContext Result.success(file)
                }

                val token = preferences.botToken.first()
                val chatId = preferences.chatId.first()

                if (token.isEmpty() || chatId.isEmpty()) {
                    return@withContext Result.failure(Exception("Telegram not configured. Go to Settings."))
                }

                backupFileDao.updateStatus(fileId, UploadStatus.UPLOADING, 0)

                val localFile = File(file.filePath)
                if (!localFile.exists()) {
                    if (!file.telegramFileId.isNullOrEmpty()) {
                        backupFileDao.updateStatus(fileId, UploadStatus.UPLOADED, 100)
                        return@withContext Result.success(file.copy(uploadStatus = UploadStatus.UPLOADED))
                    }
                    backupFileDao.markError(fileId, "File not found on device")
                    return@withContext Result.failure(Exception("File not found: ${file.fileName}"))
                }

                val caption = "📁 ${file.fileName}\n💾 ${FileUtils.formatFileSize(file.fileSize)}"

                val result = when (file.fileType) {
                    FileType.IMAGE -> telegramApi.sendPhoto(token, chatId, localFile, caption, onProgress)
                    FileType.VIDEO -> telegramApi.sendVideo(token, chatId, localFile, caption, onProgress = onProgress)
                    FileType.AUDIO -> telegramApi.sendAudio(token, chatId, localFile, caption, onProgress)
                    FileType.DOCUMENT -> telegramApi.sendDocument(token, chatId, localFile, caption, onProgress)
                }

                result.fold(
                    onSuccess = { msg ->
                        val tgFileId = when (file.fileType) {
                            FileType.IMAGE -> msg.photo?.lastOrNull()?.file_id
                            FileType.VIDEO -> msg.video?.file_id
                            FileType.AUDIO -> msg.audio?.file_id
                            FileType.DOCUMENT -> msg.document?.file_id
                        } ?: ""

                        backupFileDao.markUploaded(fileId, tgFileId, msg.message_id, System.currentTimeMillis(), chatId)
                        Result.success(file.copy(
                            uploadStatus = UploadStatus.UPLOADED,
                            telegramFileId = tgFileId,
                            telegramMessageId = msg.message_id
                        ))
                    },
                    onFailure = { e ->
                        backupFileDao.markError(fileId, e.message ?: "Upload failed")
                        Result.failure(e)
                    }
                )
            } catch (e: Exception) {
                try { backupFileDao.markError(fileId, e.message ?: "Unknown error") } catch (_: Exception) {}
                Result.failure(e)
            }
        }
    }

    suspend fun uploadAllPending(onProgress: ((fileId: Long, progress: Int) -> Unit)? = null): Int {
        return try {
            val pendingFiles = backupFileDao.getPendingFiles().first()
            var successCount = 0

            for (file in pendingFiles) {
                if (!networkUtils.isWifiConnected() && preferences.wifiOnly.first()) break
                val result = uploadFile(file.id) { progress -> onProgress?.invoke(file.id, progress) }
                if (result.isSuccess) successCount++
            }
            successCount
        } catch (e: Exception) {
            Log.e(TAG, "Upload all failed", e)
            0
        }
    }

    // ===================== Download from Telegram =====================

    suspend fun downloadFromTelegram(fileId: Long, destFile: File): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val file = backupFileDao.getFileById(fileId)
                    ?: return@withContext Result.failure(Exception("File not found"))

                val tgFileId = file.telegramFileId
                    ?: return@withContext Result.failure(Exception("No Telegram file ID"))

                val token = preferences.botToken.first()

                val fileInfo = telegramApi.getFile(token, tgFileId)
                fileInfo.fold(
                    onSuccess = { info ->
                        val filePath = info.file_path
                            ?: return@withContext Result.failure(Exception("No file path"))
                        telegramApi.downloadToFile(token, filePath, destFile)
                    },
                    onFailure = { Result.failure(it) }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ===================== Playlists =====================

    fun getAllPlaylists() = playlistDao.getAllPlaylists()
    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(Playlist(name = name))
    suspend fun deletePlaylist(playlist: Playlist) = playlistDao.deletePlaylist(playlist)
    fun getFilesForPlaylist(playlistId: Long) = playlistDao.getFilesForPlaylist(playlistId)
    suspend fun addToPlaylist(playlistId: Long, fileId: Long) {
        playlistDao.insertEntry(PlaylistEntry(playlistId = playlistId, fileId = fileId))
    }
    suspend fun removeFromPlaylist(playlistId: Long, fileId: Long) {
        playlistDao.removeFileFromPlaylist(playlistId, fileId)
    }

    // ===================== Connection Test =====================

    suspend fun testConnection(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val token = preferences.botToken.first()
                val chatId = preferences.chatId.first()
                telegramApi.testConnection(token, chatId).map { }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun testConnectionWith(token: String, chatId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                telegramApi.testConnection(token, chatId).map { }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun resetStuckUploading() = withContext(Dispatchers.IO) {
        try { backupFileDao.resetStuckUploading() } catch (e: Exception) { Log.e(TAG, "resetStuck error", e) }
    }

    suspend fun syncUploadedFilesForChat(chatId: String) = withContext(Dispatchers.IO) {
        try {
            backupFileDao.resetUploadedForDifferentChat(chatId)
            Log.i(TAG, "Reset uploaded files for chat: $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing uploaded files for chat", e)
        }
    }

    // Restore uploaded status from external history file (survives DB reset/reinstall)
    suspend fun restoreFromHistory(): Int = withContext(Dispatchers.IO) {
        try {
            val chatId = preferences.chatId.first()
            val records = historyStore.readAll().filter { it.chatId == chatId }
            if (records.isEmpty()) return@withContext 0

            // Build lookup maps
            val byHash = records.associateBy { it.fileHash }
            val byPath = records.associateBy { it.filePath }

            val allFiles = backupFileDao.getPendingFiles().first()
            var restored = 0
            for (file in allFiles) {
                val record = (if (!file.fileHash.isNullOrEmpty()) byHash[file.fileHash] else null)
                    ?: byPath[file.filePath]
                if (record != null) {
                    backupFileDao.markUploaded(file.id, record.telegramFileId, record.messageId, record.timestamp, chatId)
                    restored++
                }
            }
            Log.i(TAG, "Restored $restored files from upload history")
            restored
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from history", e)
            0
        }
    }

    // Sync uploaded status from Telegram chat by matching file names in captions
    suspend fun syncFromTelegram(): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val token = preferences.botToken.first()
            val chatId = preferences.chatId.first()
            if (token.isEmpty() || chatId.isEmpty()) return@withContext Pair(0, "Bot no configurado")

            // Get all pending files for matching
            val pendingFiles = backupFileDao.getPendingFiles().first()
            if (pendingFiles.isEmpty()) return@withContext Pair(0, "No hay archivos pendientes")

            // Build lookup by filename (caption contains "📁 fileName")
            val byName = pendingFiles.associateBy { it.fileName.lowercase() }

            // Get recent updates (last 100 messages sent TO the bot)
            val updatesResult = telegramApi.getUpdates(token)
            val updates = updatesResult.getOrNull() ?: emptyList()

            var matched = 0
            for (update in updates) {
                val msg = update.message ?: continue
                // Extract file info from message
                val (fileId, uniqueId, fileSize, fileName) = when {
                    msg.document != null -> listOf(msg.document.file_id, msg.document.file_unique_id, msg.document.file_size?.toString() ?: "", msg.document.file_name ?: "")
                    msg.video != null -> listOf(msg.video.file_id, msg.video.file_unique_id, msg.video.file_size?.toString() ?: "", "")
                    msg.audio != null -> listOf(msg.audio.file_id, msg.audio.file_unique_id, msg.audio.file_size?.toString() ?: "", "")
                    msg.photo != null -> {
                        val p = msg.photo.lastOrNull()
                        listOf(p?.file_id ?: "", p?.file_unique_id ?: "", p?.file_size?.toString() ?: "", "")
                    }
                    else -> continue
                }
                if (fileId.isEmpty()) continue

                // Match by file size or file name from the message caption
                val matchedFile = byName[fileName.lowercase()]
                    ?: pendingFiles.find { it.fileSize == fileSize.toLongOrNull() }
                if (matchedFile != null) {
                    backupFileDao.markUploaded(matchedFile.id, fileId, msg.message_id, System.currentTimeMillis(), chatId)
                    historyStore.record(matchedFile.fileHash ?: "", matchedFile.filePath, fileId, chatId, msg.message_id)
                    matched++
                }
            }

            if (matched > 0) Pair(matched, "✅ $matched archivos detectados en Telegram")
            else Pair(0, "No se encontraron archivos coincidentes en el historial reciente del bot")
        } catch (e: Exception) {
            Log.e(TAG, "syncFromTelegram error", e)
            Pair(0, "Error: ${e.message}")
        }
    }

    // Mark all pending files as uploaded (use when user confirms they're already in Telegram)
    suspend fun markAllAsUploaded(): Int = withContext(Dispatchers.IO) {
        try {
            val chatId = preferences.chatId.first()
            val pending = backupFileDao.getPendingFiles().first()
            for (file in pending) {
                backupFileDao.markUploaded(file.id, "", 0L, System.currentTimeMillis(), chatId)
                // Record in history so future restores work
                historyStore.record(file.fileHash ?: "", file.filePath, "", chatId, 0L)
            }
            Log.i(TAG, "Marked ${pending.size} files as uploaded")
            pending.size
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all as uploaded", e)
            0
        }
    }

    // Save index of all uploaded files to Telegram chat as a pinned JSON document
    suspend fun saveIndexToTelegram(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = preferences.botToken.first()
            val chatId = preferences.chatId.first()
            if (token.isEmpty() || chatId.isEmpty()) return@withContext Result.failure(Exception("Not configured"))

            val uploaded = backupFileDao.getAllUploadedFiles()
            if (uploaded.isEmpty()) return@withContext Result.success(Unit)

            val entries = uploaded.joinToString("\n") { f ->
                "${f.fileHash ?: ""}|${f.telegramFileId ?: ""}|${f.fileName}|${f.fileSize}|${f.uploadedToChatId ?: chatId}"
            }
            val json = buildString {
                append("{\"version\":2,\"chatId\":\"$chatId\",\"generated\":${System.currentTimeMillis()},")
                append("\"count\":${uploaded.size},")
                append("\"entries\":\"")
                append(entries.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"))
                append("\"}")
            }

            val result = telegramApi.sendJsonAsDocument(token, chatId, json, "telegram_backup_index.json")
            result.fold(
                onSuccess = { msg ->
                    telegramApi.pinChatMessage(token, chatId, msg.message_id)
                    Log.i(TAG, "Index saved to Telegram: ${uploaded.size} files")
                },
                onFailure = { Log.w(TAG, "Could not save index: ${it.message}") }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "saveIndexToTelegram error", e)
            Result.failure(e)
        }
    }

    // Restore uploaded status by reading the pinned index from Telegram chat
    suspend fun restoreFromTelegramIndex(): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val token = preferences.botToken.first()
            val chatId = preferences.chatId.first()
            if (token.isEmpty() || chatId.isEmpty()) return@withContext Pair(0, "Bot no configurado")

            // Get chat info to find pinned message
            val chatResult = telegramApi.getChatFull(token, chatId)
            val pinnedMsg = chatResult.getOrNull()?.pinned_message
                ?: return@withContext Pair(0, "No hay mensaje pinneado en el chat")

            // Must be a document with our caption
            val doc = pinnedMsg.document
            val caption = pinnedMsg.caption ?: ""
            if (doc == null || !caption.contains("TelegramBackupIndex")) {
                return@withContext Pair(0, "El mensaje pinneado no es un índice de respaldo")
            }

            // Download the index document
            val fileInfoResult = telegramApi.getFile(token, doc.file_id)
            val filePath = fileInfoResult.getOrNull()?.file_path
                ?: return@withContext Pair(0, "No se pudo obtener el índice")

            val contentResult = telegramApi.downloadFile(token, filePath)
            val jsonBytes = contentResult.getOrNull()
                ?: return@withContext Pair(0, "No se pudo descargar el índice")

            val json = String(jsonBytes, Charsets.UTF_8)

            // Parse entries: hash|telegramFileId|fileName|fileSize|chatId
            val entriesMatch = Regex("\"entries\":\"(.*?)\"(?:}|,)").find(json)
            val rawEntries = entriesMatch?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
            if (rawEntries.isEmpty()) return@withContext Pair(0, "Índice vacío")

            val pendingFiles = backupFileDao.getPendingFiles().first()
            val byHash = pendingFiles.associateBy { it.fileHash ?: "" }
            val byName = pendingFiles.associateBy { it.fileName }

            var restored = 0
            for (line in rawEntries.lines()) {
                if (line.isBlank()) continue
                val parts = line.split("|")
                if (parts.size < 4) continue
                val hash = parts[0]
                val tgFileId = parts[1]
                val fileName = parts[2]
                val fileSize = parts[3].toLongOrNull() ?: 0L
                val indexChatId = if (parts.size >= 5) parts[4] else chatId

                if (indexChatId != chatId) continue // skip files from different chat

                val file = byHash[hash] ?: byName[fileName]
                    ?: pendingFiles.find { it.fileSize == fileSize }
                    ?: continue

                backupFileDao.markUploaded(file.id, tgFileId, 0L, System.currentTimeMillis(), chatId)
                historyStore.record(hash, file.filePath, tgFileId, chatId, 0L)
                restored++
            }

            if (restored > 0) Pair(restored, "✅ $restored archivos detectados desde Telegram")
            else Pair(0, "No se encontraron coincidencias con archivos locales")
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromTelegramIndex error", e)
            Pair(0, "Error: ${e.message}")
        }
    }

    suspend fun isWifiConnected(): Boolean = networkUtils.isWifiConnected()
    suspend fun isConfigured(): Boolean = preferences.isConfigured.first()
}
