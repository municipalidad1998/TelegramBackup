package com.telegrambackup.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.dao.PlaylistDao
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.Playlist
import com.telegrambackup.data.local.entity.PlaylistEntry

@Database(
    entities = [BackupFile::class, Playlist::class, PlaylistEntry::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupFileDao(): BackupFileDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE backup_files ADD COLUMN uploadedToChatId TEXT")
            }
        }
    }
}
