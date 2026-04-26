package com.telegrambackup.data.local.dao

import androidx.room.*
import com.telegrambackup.data.local.entity.Playlist
import com.telegrambackup.data.local.entity.PlaylistEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // Playlist entries
    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun getEntriesForPlaylist(playlistId: Long): Flow<List<PlaylistEntry>>

    @Query("""
        SELECT bf.* FROM backup_files bf
        INNER JOIN playlist_entries pe ON bf.id = pe.fileId
        WHERE pe.playlistId = :playlistId
        ORDER BY pe.sortOrder
    """)
    fun getFilesForPlaylist(playlistId: Long): Flow<List<com.telegrambackup.data.local.entity.BackupFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PlaylistEntry): Long

    @Delete
    suspend fun deleteEntry(entry: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND fileId = :fileId")
    suspend fun removeFileFromPlaylist(playlistId: Long, fileId: Long)

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :playlistId")
    fun getPlaylistItemCount(playlistId: Long): Flow<Int>
}
