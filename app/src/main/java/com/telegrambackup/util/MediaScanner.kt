package com.telegrambackup.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedFile(
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val fileType: FileType
)

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Scan all media files on device.
     */
    fun scanAllMedia(): List<ScannedFile> {
        val files = mutableListOf<ScannedFile>()
        files.addAll(scanImages())
        files.addAll(scanVideos())
        files.addAll(scanAudio())
        files.addAll(scanDocuments())
        return files
    }

    fun scanImages(): List<ScannedFile> = scanMedia(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        FileType.IMAGE
    )

    fun scanVideos(): List<ScannedFile> = scanMedia(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        FileType.VIDEO
    )

    fun scanAudio(): List<ScannedFile> = scanMedia(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        FileType.AUDIO
    )

    fun scanDocuments(): List<ScannedFile> {
        val files = mutableListOf<ScannedFile>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE 'image/%' " +
                "AND ${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE 'video/%' " +
                "AND ${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE 'audio/%' " +
                "AND ${MediaStore.Files.FileColumns.SIZE} > 0"

        context.contentResolver.query(collection, projection, selection, null,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val name = cursor.getString(nameCol) ?: File(path).name
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val added = cursor.getLong(addedCol) * 1000
                val modified = cursor.getLong(modifiedCol) * 1000

                val uri = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"), id
                )

                files.add(ScannedFile(uri, path, name, size, mime, added, modified, FileType.DOCUMENT))
            }
        }
        return files
    }

    private fun scanMedia(collection: Uri, fileType: FileType): List<ScannedFile> {
        val files = mutableListOf<ScannedFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        context.contentResolver.query(collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val name = cursor.getString(nameCol) ?: File(path).name
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val added = cursor.getLong(addedCol) * 1000
                val modified = cursor.getLong(modifiedCol) * 1000

                if (size <= 0) continue

                val uri = ContentUris.withAppendedId(collection, id)
                files.add(ScannedFile(uri, path, name, size, mime, added, modified, fileType))
            }
        }
        return files
    }
}
