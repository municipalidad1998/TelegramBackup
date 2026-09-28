package com.iptvplayerpro.player

import androidx.media3.common.PlaybackException
import com.iptvplayerpro.domain.model.PlaybackIssue
import com.iptvplayerpro.domain.model.PlaybackIssueInfo
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.PlaylistStatus

/**
 * Traduce errores técnicos de ExoPlayer/Media3 a causas comprensibles:
 * conexión, URL incorrecta, cuenta vencida, servidor caído, restricción
 * geográfica indicada por el proveedor, formato no compatible o
 * autenticación.
 */
object PlaybackErrorClassifier {

    fun classify(
        error: PlaybackException?,
        playlist: Playlist?
    ): PlaybackIssueInfo {
        // Si la propia lista está vencida, el motivo principal es ese.
        if (playlist != null && playlist.status == PlaylistStatus.EXPIRED) {
            return PlaybackIssueInfo(
                PlaybackIssue.EXPIRED_ACCOUNT,
                technicalDetailOf(error)
            )
        }

        val code = error?.errorCode ?: PlaybackException.ERROR_CODE_UNSPECIFIED
        val httpCode = httpCodeOf(error)

        val issue = when (code) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> when {
                httpCode == null -> PlaybackIssue.CONNECTION
                httpCode >= 500 -> PlaybackIssue.SERVER_DOWN
                else -> PlaybackIssue.CONNECTION
            }

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpCode) {
                401, 407 -> PlaybackIssue.AUTH
                403 -> PlaybackIssue.GEO_RESTRICTED
                404, 410 -> PlaybackIssue.BAD_URL
                in 500..599 -> PlaybackIssue.SERVER_DOWN
                else -> PlaybackIssue.AUTH
            }

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlaybackIssue.BAD_URL

            PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION -> PlaybackIssue.GEO_RESTRICTED

            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> if (isDrmError(error)) {
                PlaybackIssue.DRM
            } else {
                PlaybackIssue.FORMAT
            }

            PlaybackException.ERROR_CODE_TIMEOUT -> PlaybackIssue.TIMEOUT

            PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED,
            PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
            PlaybackException.ERROR_CODE_CONCURRENT_STREAM_LIMIT -> PlaybackIssue.AUTH

            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> PlaybackIssue.DRM

            else -> if (isDrmError(error)) PlaybackIssue.DRM else PlaybackIssue.UNKNOWN
        }
        return PlaybackIssueInfo(issue, technicalDetailOf(error))
    }

    /** Código HTTP extraído de la causa raíz (excepciones HttpDataSource.InvalidResponseCodeException). */
    private fun httpCodeOf(error: PlaybackException?): Int? {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 6) {
            val name = cause.javaClass.name
            if (name == "androidx.media3.datasource.HttpDataSource\$InvalidResponseCodeException") {
                return try {
                    val field = cause.javaClass.getMethod("getResponseCode")
                    (field.invoke(cause) as? Int)
                } catch (e: Exception) {
                    null
                }
            }
            cause = cause.cause
            depth++
        }
        return null
    }

    private fun isDrmError(error: PlaybackException?): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 6) {
            val text = (cause.message ?: "") + " " + cause.javaClass.name
            if (text.contains("Drm", ignoreCase = true) ||
                text.contains("Widevine", ignoreCase = true) ||
                text.contains("unsupported scheme", ignoreCase = true)
            ) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    private fun technicalDetailOf(error: PlaybackException?): String? {
        if (error == null) return null
        return buildString {
            append("Código: ")
            append(error.errorCodeName)
            append(" (")
            append(error.errorCode)
            append(")")
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 4) {
                append("\n• ")
                append(cause.javaClass.simpleName)
                cause.message?.let { append(": ").append(it.take(200)) }
                cause = cause.cause
                depth++
            }
        }
    }
}
