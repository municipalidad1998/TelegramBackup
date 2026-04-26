package com.telegrambackup.data.repository

import android.content.Context
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.dao.PlaylistDao
import com.telegrambackup.data.local.entity.*
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.network.TelegramApiService
import com.telegrambackup.util.FileUtils
import com.telegrambackup.util.MediaScanner
import com.telegrambackup.util.NetworkUtils
import com.telegrambackup.util.ScannedFile
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
    private val networkUtils: NetworkUtils
) {
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

    /**
     * Scan device for media and register new files in DB.
     * Returns count of newly discovered files.
     */
    suspend fun scanAndRegisterNewFiles(): Int = withContext(Dispatchers.IO) {
        val scanned = mediaScanner.scanAllMedia()
        var newCount = 0

        for (sf in scanned) {
            val file = File(sf.path)
            if (!file.exists()) continue

            // Check duplicate by path first
            val existingByPath = backupFileDao.getFileByPath(sf.path)
            if (existingByPath != null) continue

            // Then check by hash
            val hash = FileUtils.sha256(file)
            val existingByHash = backupFileDao.getFileByHash(hash)
            if (existingByHash != null) {
                // Same content, different path — still skip
                continue
            }

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
        }
        newCount
    }

    // ===================== Upload =====================

    /**
     * Upload a single file to Telegram.
     */
    suspend fun uploadFile(fileId: Long, onProgress: ((Int) -> Unit)? = null): Result<BackupFile> {
        return withContext(Dispatchers.IO) {
            try {
                val file = backupFileDao.getFileById(fileId)
                    ?: return@withContext Result.failure(Exception("File not found"))

                if (file.uploadStatus == UploadStatus.UPLOADED) {
                    return@withContext Result.success(file)
                }

                val token = preferences.botToken.first()
                val chatId = preferences.chatId.first()

                if (token.isEmpty() || chatId.isEmpty()) {
                    return@withContext Result.failure(Exception("Telegram not configured"))
                }

                backupFileDao.updateStatus(fileId, UploadStatus.UPLOADING, 0)

                val localFile = File(file.filePath)
                if (!localFile.exists()) {
                    // Try to find in Telegram if we have file_id
                    if (!file.telegramFileId.isNullOrEmpty()) {
                        backupFileDao.updateStatus(fileId, UploadStatus.UPLOADED, 100)
                        return@withContext Result.success(file.copy(uploadStatus = UploadStatus.UPLOADED))
                    }
                    backupFileDao.markError(fileId, "File not found on device")
                    return@withContext Result.failure(Exception("File not found on device"))
                }

                val caption = "📁 ${file.fileName}\n📅 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(file.dateAdded)}\n💾 ${FileUtils.formatFileSize(file.fileSize)}"

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

                        backupFileDao.markUploaded(fileId, tgFileId, msg.message_id, System.currentTimeMillis())
                        Result.success(file.copy(
                            uploadStatus = UploadStatus.UPLOADED,
                            telegramFileId = tgFileId,
                            telegramMessageId = msg.message_id
                        ))
                    },
                    onFailure = { e ->
                        backupFileDao.markError(fileId, e.message ?: "Unknown error")
                        Result.failure(e)
                    }
                )
            } catch (e: Exception) {
                backupFileDao.markError(fileId, e.message ?: "Unknown error")
                Result.failure(e)
            }
        }
    }

    /**
     * Upload all pending files.
     */
    suspend fun uploadAllPending(onProgress: ((fileId: Long, progress: Int) -> Unit)? = null): Int {
        val pendingFiles = backupFileDao.getPendingFiles().first()
        var successCount = 0

        for (file in pendingFiles) {
            if (!networkUtils.isWifiConnected() && preferences.wifiOnly.first()) {
                break // Stop if WiFi disconnected and wifi-only mode is on
            }

            val result = uploadFile(file.id) { progress ->
                onProgress?.invoke(file.id, progress)
            }
            if (result.isSuccess) successCount++
        }
        return successCount
    }

    // ===================== Download from Telegram =====================

    suspend fun downloadFromTelegram(fileId: Long, destFile: File): Result<File> {
        return withContext(Dispatchers.IO) {
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
        }
    }

    // ===================== Playlists =====================

    fun getAllPlaylists() = playlistDao.getAllPlaylists()
    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(Playlist(name = name))
    }
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
        val token = preferences.botToken.first()
        val chatId = preferences.chatId.first()
        return telegramApi.testConnection(token, chatId).map { }
    }

    suspend fun isWifiConnected(): Boolean = networkUtils.isWifiConnected()
    suspend fun isConfigured(): Boolean = preferences.isConfigured.first()
}
