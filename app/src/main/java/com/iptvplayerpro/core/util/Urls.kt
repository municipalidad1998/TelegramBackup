package com.iptvplayerpro.core.util

import android.net.Uri
import java.net.MalformedURLException
import java.net.URL

/** Utilidades de validación y normalización de URLs. */
object Urls {

    /** Agrega https:// si falta el esquema y normaliza espacios. */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    /** true si la cadena es una URL http/https con host válido. */
    fun isValidHttpUrl(input: String): Boolean {
        val candidate = normalize(input)
        return try {
            val url = URL(candidate)
            url.protocol == "http" || url.protocol == "https"
        } catch (e: MalformedURLException) {
            false
        }
    }

    /**
     * Normaliza la URL de un servidor Xtream:
     * elimina sufijos comunes (/player_api.php, /c, /live, …) y barras finales.
     */
    fun normalizeXtreamServer(input: String): String {
        var url = normalize(input)
        for (suffix in listOf(
            "/player_api.php", "/panel_api.php", "/get.php", "/xmltv.php",
            "/c", "/live", "/movie", "/series", "/streaming"
        )) {
            if (url.endsWith(suffix, ignoreCase = true)) {
                url = url.removeSuffix(suffix)
            }
        }
        while (url.endsWith("/")) url = url.dropLast(1)
        return url
    }

    /** Host de una URL (o null). */
    fun hostOf(url: String): String? = try {
        URL(normalize(url)).host
    } catch (e: MalformedURLException) {
        null
    }

    /** true si la URL apunta a un servidor local (no confiable como fuente remota). */
    fun isLocalHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1"
    }

    /** Nombre de archivo (sin parámetros) a partir de una URL. */
    fun fileNameOf(url: String): String {
        return try {
            val uri = Uri.parse(normalize(url))
            val last = uri.lastPathSegment ?: "lista"
            last.substringBefore("?")
        } catch (e: Exception) {
            "lista"
        }
    }

    /** Detecta si el contenido parece una lista M3U/M3U8. */
    fun looksLikeM3U(content: String): Boolean {
        val head = content.take(4096)
        return head.contains("#EXTM3U") || head.contains("#EXTINF")
    }

    /** Detecta si una URL parece de lista M3U. */
    fun looksLikeM3uUrl(url: String): Boolean {
        val lower = normalize(url).lowercase()
        return lower.contains(".m3u8") || lower.contains(".m3u") || lower.contains("/get.php") ||
            lower.contains("playlist") || lower.contains("mag/")
    }
}
