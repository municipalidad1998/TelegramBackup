package com.iptvplayerpro.data.repository

import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.domain.model.PublicIpInfo
import com.iptvplayerpro.domain.repository.IpInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/**
 * Consulta la IP pública del dispositivo (cuando sea técnicamente posible).
 * Usa servicios públicos sin enviar credenciales de ningún tipo.
 */
class IpInfoRepositoryImpl(private val http: OkHttpClient) : IpInfoRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetch(): PublicIpInfo? = withContext(Dispatchers.IO) {
        // Primer servicio con país; si falla, respaldo simple de IP.
        try {
            parseIpWhoIs(Http.fetchText(http, "https://ipwho.is/").text)
        } catch (e: Exception) {
            try {
                parseIpify(Http.fetchText(http, "https://api.ipify.org?format=json").text)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun parseIpWhoIs(text: String?): PublicIpInfo? {
        if (text.isNullOrBlank()) return null
        return try {
            val obj = json.parseToJsonElement(text).let { it as? kotlinx.serialization.json.JsonObject }
                ?: return null
            val ip = obj["ip"]?.jsonPrimitive?.content ?: return null
            PublicIpInfo(
                ip = ip,
                country = obj["country"]?.jsonPrimitive?.contentOrNullSafe(),
                countryCode = obj["country_code"]?.jsonPrimitive?.contentOrNullSafe(),
                city = obj["city"]?.jsonPrimitive?.contentOrNullSafe()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIpify(text: String?): PublicIpInfo? {
        if (text.isNullOrBlank()) return null
        return try {
            val obj = json.parseToJsonElement(text).let { it as? kotlinx.serialization.json.JsonObject }
                ?: return null
            val ip = obj["ip"]?.jsonPrimitive?.content ?: return null
            PublicIpInfo(ip = ip)
        } catch (e: Exception) {
            null
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.contentOrNullSafe(): String? = try {
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content
    } catch (e: Exception) {
        null
    }
}
