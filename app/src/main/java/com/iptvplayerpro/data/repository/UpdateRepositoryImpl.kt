package com.iptvplayerpro.data.repository

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.iptvplayerpro.BuildConfig
import com.iptvplayerpro.core.util.Urls
import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.domain.model.UpdateInfo
import com.iptvplayerpro.domain.repository.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.io.File

/**
 * Sistema de actualización de la APK:
 * - Consulta una fuente oficial (URL configurable; soporta el JSON de Releases
 *   de GitHub o un JSON propio).
 * - Descarga con DownloadManager (visible para el usuario).
 * - Instala mediante el instalador oficial de Android (ACTION_VIEW); nunca se
 *   instala de forma silenciosa.
 */
class UpdateRepositoryImpl(
    private val context: Context,
    private val http: OkHttpClient
) : UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val currentVersionName: String get() = BuildConfig.VERSION_NAME
    override val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    override suspend fun checkForUpdate(updateUrl: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = updateUrl.trim()
            if (url.isEmpty()) return@runCatching null
            val fetched = try {
                Http.fetchText(http, url)
            } catch (e: Exception) {
                throw IllegalStateException("No se pudo consultar la fuente de actualizaciones.")
            }
            val text = fetched.text
                ?: throw IllegalStateException("Respuesta vacía (HTTP ${fetched.httpCode}).")
            val info = parse(text, url)
            info
        }
    }

    private fun parse(text: String, sourceUrl: String): UpdateInfo? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            if (sourceUrl.contains("api.github.com", ignoreCase = true)) {
                parseGitHub(obj)
            } else {
                parseCustom(obj)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGitHub(obj: kotlinx.serialization.json.JsonObject): UpdateInfo? {
        val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return null
        val versionName = tag.removePrefix("v").trim()
        val body = obj["body"]?.jsonPrimitive?.content ?: ""
        var apkUrl: String? = null
        val assets = obj["assets"]?.jsonArray ?: return buildInfo(versionName, 0, body, "")
        for (asset in assets) {
            val assetObj = asset as? kotlinx.serialization.json.JsonObject ?: continue
            val name = assetObj["name"]?.jsonPrimitive?.content ?: continue
            val download = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: continue
            if (name.endsWith(".apk", true)) {
                apkUrl = download
                break
            }
        }
        return buildInfo(versionName, 0, body, apkUrl ?: return null)
    }

    private fun parseCustom(obj: kotlinx.serialization.json.JsonObject): UpdateInfo? {
        val versionName = obj["versionName"]?.jsonPrimitive?.content
            ?: obj["version"]?.jsonPrimitive?.content ?: return null
        val versionCode = obj["versionCode"]?.jsonPrimitive?.int
            ?: obj["versionCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val changelog = obj["changelog"]?.jsonPrimitive?.content ?: ""
        val apkUrl = obj["apkUrl"]?.jsonPrimitive?.content
            ?: obj["downloadUrl"]?.jsonPrimitive?.content ?: return null
        return buildInfo(versionName, versionCode, changelog, apkUrl)
    }

    private fun buildInfo(
        versionName: String,
        versionCode: Int,
        changelog: String,
        apkUrl: String
    ): UpdateInfo {
        val code = if (versionCode > 0) versionCode else guessCode(versionName)
        return UpdateInfo(
            newVersionName = versionName,
            newVersionCode = code,
            changelog = changelog,
            downloadUrl = apkUrl,
            isUpdateAvailable = code > currentVersionCode ||
                (versionCode <= 0 && versionName != currentVersionName)
        )
    }

    private fun guessCode(versionName: String): Int {
        val parts = versionName.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return 0
        return parts.take(3).fold(0) { acc, part -> acc * 1000 + part }
    }

    override suspend fun downloadAndInstall(update: UpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(Urls.isValidHttpUrl(update.downloadUrl)) { "URL de descarga no válida." }
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IllegalStateException("No hay almacenamiento disponible.")
                val target = File(dir, "iptv-player-pro-${update.newVersionName}.apk")

                // Descarga con DownloadManager (progreso visible en el sistema).
                val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                    .setTitle("IPTV Player Pro ${update.newVersionName}")
                    .setDescription("Actualización de IPTV Player Pro")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(target))
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                // Se encola la descarga; el usuario instala desde la notificación
                // o puede volver a pulsar «Instalar» cuando termine.
                Unit
            }
        }

    /** Abre el instalador del sistema con la APK ya descargada (nunca silencioso). */
    fun installDownloadedApk(versionName: String): Boolean {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
            val apk = dir.listFiles()?.firstOrNull {
                it.name == "iptv-player-pro-$versionName.apk" && it.length() > 0
            } ?: return false
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
