package com.telegrambackup.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.dao.PlaylistDao
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.Playlist
import com.telegrambackup.data.local.entity.PlaylistEntry

@Database(
    entities = [BackupFile::class, Playlist::class, PlaylistEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupFileDao(): BackupFileDao
    abstract fun playlistDao(): PlaylistDao
}
