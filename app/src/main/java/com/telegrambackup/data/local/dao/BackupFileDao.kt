package com.telegrambackup.data.local.dao

import androidx.room.*
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupFileDao {

    @Query("SELECT * FROM backup_files ORDER BY dateAdded DESC")
    fun getAllFiles(): Flow<List<BackupFile>>

    @Query("SELECT * FROM backup_files WHERE fileType = :type ORDER BY dateAdded DESC")
    fun getFilesByType(type: FileType): Flow<List<BackupFile>>

    @Query("SELECT * FROM backup_files WHERE uploadStatus = :status ORDER BY dateAdded ASC")
    fun getFilesByStatus(status: UploadStatus): Flow<List<BackupFile>>

    @Query("SELECT * FROM backup_files WHERE uploadStatus = 'PENDING' OR uploadStatus = 'ERROR' ORDER BY dateAdded ASC")
    fun getPendingFiles(): Flow<List<BackupFile>>

    @Query("SELECT * FROM backup_files WHERE id = :id")
    suspend fun getFileById(id: Long): BackupFile?

    @Query("SELECT * FROM backup_files WHERE fileHash = :hash LIMIT 1")
    suspend fun getFileByHash(hash: String): BackupFile?

    @Query("SELECT * FROM backup_files WHERE filePath = :path LIMIT 1")
    suspend fun getFileByPath(path: String): BackupFile?

    @Query("SELECT * FROM backup_files WHERE dateAdded BETWEEN :startMs AND :endMs ORDER BY dateAdded DESC")
    fun getFilesByDateRange(startMs: Long, endMs: Long): Flow<List<BackupFile>>

    @Query("SELECT DISTINCT dateAdded / 86400000 * 86400000 as day FROM backup_files ORDER BY day DESC")
    fun getDistinctDays(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: BackupFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<BackupFile>)

    @Update
    suspend fun update(file: BackupFile)

    @Query("UPDATE backup_files SET uploadStatus = :status, uploadProgress = :progress WHERE id = :id")
    suspend fun updateStatus(id: Long, status: UploadStatus, progress: Int = 0)

    @Query("UPDATE backup_files SET uploadStatus = 'UPLOADED', telegramFileId = :fileId, telegramMessageId = :msgId, uploadDate = :date, uploadProgress = 100, uploadedToChatId = :chatId WHERE id = :id")
    suspend fun markUploaded(id: Long, fileId: String, msgId: Long, date: Long, chatId: String)

    @Query("UPDATE backup_files SET uploadStatus = 'PENDING', uploadProgress = 0, telegramFileId = NULL, telegramMessageId = NULL, uploadDate = NULL, uploadedToChatId = NULL WHERE uploadStatus = 'UPLOADED' AND uploadedToChatId IS NOT NULL AND uploadedToChatId != :chatId")
    suspend fun resetUploadedForDifferentChat(chatId: String)

    @Query("UPDATE backup_files SET uploadStatus = 'ERROR', errorMessage = :error WHERE id = :id")
    suspend fun markError(id: Long, error: String)

    @Delete
    suspend fun delete(file: BackupFile)

    @Query("SELECT COUNT(*) FROM backup_files")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_files WHERE uploadStatus = 'UPLOADED'")
    fun getUploadedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_files WHERE uploadStatus = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT SUM(fileSize) FROM backup_files WHERE uploadStatus = 'UPLOADED'")
    fun getTotalUploadedSize(): Flow<Long?>

    @Query("SELECT * FROM backup_files WHERE telegramFileId IS NOT NULL AND telegramFileId != ''")
    suspend fun getAllUploadedFiles(): List<BackupFile>
}
