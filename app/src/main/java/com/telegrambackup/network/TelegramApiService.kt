package com.telegrambackup.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T?,
    val description: String?
)

data class TelegramMessage(
    val message_id: Long,
    val chat: TelegramChat,
    val photo: List<TelegramPhotoSize>?,
    val video: TelegramVideo?,
    val document: TelegramDocument?,
    val audio: TelegramAudio?
)

data class TelegramChat(val id: Long, val title: String?)
data class TelegramPhotoSize(val file_id: String, val file_unique_id: String, val width: Int, val height: Int, val file_size: Long?)
data class TelegramVideo(val file_id: String, val file_unique_id: String, val width: Int, val height: Int, val duration: Int, val file_size: Long?)
data class TelegramDocument(val file_id: String, val file_unique_id: String, val file_name: String?, val file_size: Long?)
data class TelegramAudio(val file_id: String, val file_unique_id: String, val duration: Int, val file_size: Long?)

data class TelegramFile(
    val file_id: String,
    val file_unique_id: String,
    val file_size: Long?,
    val file_path: String?
)

data class TelegramUpdate(
    val update_id: Long,
    val message: TelegramMessage?
)

data class TelegramUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate>?
)

@Singleton
class TelegramApiService @Inject constructor() {

    private val gson: Gson = GsonBuilder().create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(token: String) = "https://api.telegram.org/bot$token"

    /**
     * Send a text message to verify bot works.
     */
    suspend fun testConnection(token: String, chatId: String): Result<TelegramMessage> {
        val url = "${baseUrl(token)}/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", "✅ Telegram Backup connected successfully!")
            .build()

        return post<TelegramMessage>(url, body)
    }

    /**
     * Send a document/file.
     */
    suspend fun sendDocument(
        token: String,
        chatId: String,
        file: File,
        caption: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): Result<TelegramMessage> {
        val url = "${baseUrl(token)}/sendDocument"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption.take(1024))
            .addFormDataPart(
                "document",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
                    .let { req -> ProgressRequestBody(req, onProgress) }
            )
            .build()

        return post<TelegramMessage>(url, body)
    }

    /**
     * Send a photo.
     */
    suspend fun sendPhoto(
        token: String,
        chatId: String,
        file: File,
        caption: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): Result<TelegramMessage> {
        val url = "${baseUrl(token)}/sendPhoto"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption.take(1024))
            .addFormDataPart(
                "photo",
                file.name,
                file.asRequestBody("image/*".toMediaType())
                    .let { req -> ProgressRequestBody(req, onProgress) }
            )
            .build()

        return post<TelegramMessage>(url, body)
    }

    /**
     * Send a video.
     */
    suspend fun sendVideo(
        token: String,
        chatId: String,
        file: File,
        caption: String = "",
        duration: Int = 0,
        onProgress: ((Int) -> Unit)? = null
    ): Result<TelegramMessage> {
        val url = "${baseUrl(token)}/sendVideo"
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption.take(1024))

        if (duration > 0) {
            builder.addFormDataPart("duration", duration.toString())
        }

        builder.addFormDataPart(
            "video",
            file.name,
            file.asRequestBody("video/*".toMediaType())
                .let { req -> ProgressRequestBody(req, onProgress) }
        )

        return post<TelegramMessage>(url, builder.build())
    }

    /**
     * Send an audio file.
     */
    suspend fun sendAudio(
        token: String,
        chatId: String,
        file: File,
        caption: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): Result<TelegramMessage> {
        val url = "${baseUrl(token)}/sendAudio"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption.take(1024))
            .addFormDataPart(
                "audio",
                file.name,
                file.asRequestBody("audio/*".toMediaType())
                    .let { req -> ProgressRequestBody(req, onProgress) }
            )
            .build()

        return post<TelegramMessage>(url, body)
    }

    /**
     * Get recent messages sent TO the bot (not by the bot).
     * Used to detect recently uploaded files via caption matching.
     */
    suspend fun getUpdates(token: String, offset: Long = 0): Result<List<TelegramUpdate>> {
        val url = "${baseUrl(token)}/getUpdates?offset=$offset&limit=100&timeout=0"
        val request = Request.Builder().url(url).get().build()
        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: ""
            val parsed = gson.fromJson(json, TelegramUpdatesResponse::class.java)
            if (parsed.ok) Result.success(parsed.result ?: emptyList())
            else Result.failure(Exception("getUpdates failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export chat messages using copyMessage trick: forward message_ids from chat to check existence.
     * Returns file sizes found in the chat via the forward API.
     */
    suspend fun getChatMessageFileInfo(token: String, chatId: String, messageId: Long): Result<TelegramMessage> {
        val url = "${baseUrl(token)}/forwardMessage"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("from_chat_id", chatId)
            .add("message_id", messageId.toString())
            .build()
        return post<TelegramMessage>(url, body)
    }

    /**
     * Get file info by file_id.
     */
    suspend fun getFile(token: String, fileId: String): Result<TelegramFile> {
        val url = "${baseUrl(token)}/getFile"
        val body = FormBody.Builder()
            .add("file_id", fileId)
            .build()

        return post<TelegramFile>(url, body)
    }

    /**
     * Download a file by file_path from Telegram.
     */
    suspend fun downloadFile(token: String, filePath: String): Result<ByteArray> {
        val url = "https://api.telegram.org/file/bot$token/$filePath"
        val request = Request.Builder().url(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.bytes() ?: ByteArray(0))
            } else {
                Result.failure(Exception("Download failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download file to local path.
     */
    suspend fun downloadToFile(token: String, filePath: String, destFile: File): Result<File> {
        val url = "https://api.telegram.org/file/bot$token/$filePath"
        val request = Request.Builder().url(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(destFile)
            } else {
                Result.failure(Exception("Download failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private inline fun <reified T> post(url: String, body: RequestBody): Result<T> {
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val parsed = gson.fromJson(json, TelegramResponse::class.java)
                if (parsed.ok) {
                    val result = gson.fromJson(
                        gson.toJson(parsed.result),
                        T::class.java
                    )
                    Result.success(result)
                } else {
                    Result.failure(Exception(parsed.description ?: "Telegram API error"))
                }
            } else {
                val errorMsg = try {
                    val parsed = gson.fromJson(json, TelegramResponse::class.java)
                    parsed.description ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${json.take(200)}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("No internet connection"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Connection timed out"))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Cannot connect to Telegram servers"))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("SSL error: ${e.message ?: "certificate problem"}"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Network error: ${e.javaClass.simpleName}"))
        }
    }
}

/**
 * RequestBody wrapper that reports upload progress.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: ((Int) -> Unit)?
) : RequestBody() {

    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: okio.BufferedSink) {
        val totalBytes = contentLength()
        var uploadedBytes = 0L

        val countingSink = object : okio.ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                uploadedBytes += byteCount
                if (totalBytes > 0) {
                    val progress = ((uploadedBytes * 100) / totalBytes).toInt()
                        .coerceIn(0, 100)
                    onProgress?.invoke(progress)
                }
            }
        }

        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
