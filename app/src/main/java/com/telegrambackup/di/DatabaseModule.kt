package com.telegrambackup.di

import android.content.Context
import androidx.room.Room
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.dao.PlaylistDao
import com.telegrambackup.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "telegram_backup.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideBackupFileDao(db: AppDatabase): BackupFileDao = db.backupFileDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
}
