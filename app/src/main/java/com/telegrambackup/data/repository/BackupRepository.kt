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

                        backupFileDao.markUploaded(fileId, tgFileId, msg.message_id, System.currentTimeMillis())
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

    suspend fun isWifiConnected(): Boolean = networkUtils.isWifiConnected()
    suspend fun isConfigured(): Boolean = preferences.isConfigured.first()
}
