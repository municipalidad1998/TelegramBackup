package com.iptvplayerpro.domain.model

/**
 * Motivo por el que un canal no reproduce. Se usa para mostrar un mensaje
 * comprensible al usuario en lugar de un error técnico.
 */
enum class PlaybackIssue(val title: String, val bullets: List<String>, val retryable: Boolean) {
    CONNECTION(
        "Error de conexión",
        listOf(
            "Verifica tu conexión a Internet (Wi-Fi o datos móviles).",
            "Prueba nuevamente en unos segundos.",
            "Si usas VPN, cambia de servidor o desconéctala."
        ),
        retryable = true
    ),
    TIMEOUT(
        "El servidor tardó demasiado en responder",
        listOf(
            "Verifica tu conexión a Internet.",
            "Prueba nuevamente.",
            "El servidor puede estar saturado; intenta más tarde."
        ),
        retryable = true
    ),
    BAD_URL(
        "La dirección del canal no es válida o ya no existe",
        listOf(
            "Actualiza la lista desde «Mis listas».",
            "Prueba otro canal de la misma lista.",
            "Revisa el estado de la lista."
        ),
        retryable = false
    ),
    SERVER_DOWN(
        "El servidor no responde o está caído",
        listOf(
            "Prueba nuevamente en unos minutos.",
            "Prueba otro canal para confirmar si es un problema del servidor.",
            "Revisa el estado de la lista."
        ),
        retryable = true
    ),
    AUTH(
        "Problema de autenticación con el servidor",
        listOf(
            "Revisa tus credenciales en «Mis listas» → Editar.",
            "Revisa el estado de la lista.",
            "Verifica que tu cuenta siga activa con tu proveedor."
        ),
        retryable = false
    ),
    EXPIRED_ACCOUNT(
        "La cuenta o lista está vencida",
        listOf(
            "Revisa el estado de la lista en «Mis listas».",
            "Actualiza tus credenciales si renovaste la suscripción.",
            "Agrega una nueva lista si cambiaste de proveedor."
        ),
        retryable = false
    ),
    GEO_RESTRICTED(
        "El proveedor restringe este contenido por ubicación",
        listOf(
            "Conéctate con una VPN en una región permitida por tu proveedor (sección VPN).",
            "Consulta los términos de tu proveedor.",
            "Nota: IPTV Player Pro no evita restricciones impuestas por el proveedor."
        ),
        retryable = false
    ),
    FORMAT(
        "Formato de transmisión no compatible",
        listOf(
            "El canal usa un formato que esta versión no puede reproducir.",
            "Actualiza la app y prueba nuevamente.",
            "Prueba otro canal con formato HLS o TS."
        ),
        retryable = false
    ),
    DRM(
        "Contenido protegido",
        listOf(
            "Este contenido usa protección (DRM) que no puede reproducirse aquí.",
            "IPTV Player Pro no evita protecciones de contenido.",
            "Consulta con tu proveedor las opciones compatibles."
        ),
        retryable = false
    ),
    UNKNOWN(
        "No se pudo reproducir el canal",
        listOf(
            "Verifica tu conexión.",
            "Prueba nuevamente.",
            "Revisa credenciales y estado de la lista.",
            "Cambia de servidor VPN si el proveedor permite otra ubicación."
        ),
        retryable = true
    )
}

/** Clasificación de un error de reproducción con detalle técnico opcional. */
data class PlaybackIssueInfo(
    val issue: PlaybackIssue,
    val technicalDetail: String? = null
)
