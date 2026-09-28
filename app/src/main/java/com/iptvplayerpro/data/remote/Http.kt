package com.iptvplayerpro.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Resultado de una descarga de texto con soporte de caché ETag. */
data class TextFetchResult(
    val text: String?,
    val etag: String?,
    val lastModified: String?,
    /** true si el servidor respondió 304 Sin cambios. */
    val notModified: Boolean,
    val httpCode: Int
)

/**
 * Cliente HTTP compartido. Notas de seguridad:
 * - Se prefieren conexiones HTTPS cuando el servidor las soporta.
 * - No se registra ningún header con credenciales en logs.
 */
object Http {

    const val USER_AGENT = "IPTVPlayerPro/1.0 (Android)"

    /** Cliente para descargas de listas, EPG y verificaciones. */
    fun client(connectTimeoutSec: Long = 15, readTimeoutSec: Long = 25): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    /** Descarga texto (lista M3U, XMLTV, .conf de VPN) con soporte If-None-Match/If-Modified-Since. */
    fun fetchText(
        client: OkHttpClient,
        url: String,
        etag: String? = null,
        lastModified: String? = null
    ): TextFetchResult {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
        etag?.let { builder.header("If-None-Match", it) }
        lastModified?.let { builder.header("If-Modified-Since", it) }

        client.newCall(builder.build()).execute().use { response ->
            return if (response.code == 304) {
                TextFetchResult(null, etag, lastModified, notModified = true, httpCode = 304)
            } else {
                val body = if (response.isSuccessful) response.body?.string() else null
                TextFetchResult(
                    text = body,
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                    notModified = false,
                    httpCode = response.code
                )
            }
        }
    }

    /** Comprueba que una URL responda (rango pequeño). Devuelve el código HTTP o -1 si falla. */
    fun probe(client: OkHttpClient, url: String): Int {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-1024")
                .build()
            client.newCall(request).execute().use { response: Response -> response.code }
        } catch (e: Exception) {
            -1
        }
    }
}
