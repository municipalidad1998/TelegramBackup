package com.iptvplayerpro.vpn

/**
 * Catálogo informativo de proveedores que ofrecen planes gratuitos con
 * configuraciones WireGuard descargables.
 *
 * IMPORTANTE — Cómo funciona la VPN en IPTV Player Pro:
 * - La app NO incluye credenciales ni servidores propios.
 * - El usuario importa SUS PROPIAS configuraciones .conf (obtenidas en su
 *   cuenta del proveedor que elija) y la app las conecta con Android
 *   VpnService + WireGuard.
 * - El tráfico del usuario nunca pasa por servidores de IPTV Player Pro.
 */
data class FreeVpnProvider(
    val name: String,
    val description: String,
    val freeAllowance: String,
    val regions: String,
    val website: String,
    val configSteps: List<String>
)

object FreeVpnCatalog {

    val providers: List<FreeVpnProvider> = listOf(
        FreeVpnProvider(
            name = "Proton VPN (Free)",
            description = "Plan gratuito sin límite de datos de Proton. Sin registros de actividad.",
            freeAllowance = "Ilimitado (velocidad moderada)",
            regions = "EE. UU., Países Bajos, Japón y más (según disponibilidad)",
            website = "https://protonvpn.com/free-vpn",
            configSteps = listOf(
                "Crea una cuenta gratuita en protonvpn.com.",
                "Entra en account.protonvpn.com → Descargas → Configuración WireGuard.",
                "Genera y descarga el archivo .conf del país que quieras.",
                "Vuelve aquí: «Agregar servidor» → «Importar archivo»."
            )
        ),
        FreeVpnProvider(
            name = "Windscribe (Free)",
            description = "10 GB al mes con cuenta de correo verificada. Genera configs WireGuard.",
            freeAllowance = "10 GB/mes (+5 GB por tuit opcional)",
            regions = "EE. UU., Canadá, Reino Unido, Alemania, Francia, Países Bajos, Suiza y más",
            website = "https://windscribe.com/free",
            configSteps = listOf(
                "Regístrate gratis en windscribe.com.",
                "Entra en windscribe.com/getconfig/wireguard.",
                "Elige ubicación y protocolo WireGuard; descarga el .conf.",
                "Impórtalo aquí con «Agregar servidor» → «Importar archivo»."
            )
        ),
        FreeVpnProvider(
            name = "PrivadoVPN (Free)",
            description = "10 GB al mes, con servidores gratuitos en varias ciudades.",
            freeAllowance = "10 GB/mes",
            regions = "Estocolmo, Frankfurt, Londres, Nueva York, Dallas, Los Ángeles…",
            website = "https://privadovpn.com/free-vpn/",
            configSteps = listOf(
                "Crea la cuenta gratuita en privadovpn.com.",
                "Descarga la app oficial o genera la config WireGuard desde el panel.",
                "Exporta el .conf de la ciudad deseada.",
                "Impórtalo en esta app para usarlo."
            )
        ),
        FreeVpnProvider(
            name = "hide.me (Free)",
            description = "Plan gratuito con datos mensuales y varias ubicaciones.",
            freeAllowance = "10 GB/mes (renovable)",
            regions = "Amsterdam, Frankfurt, Londres, Nueva York, Chicago, Sídney…",
            website = "https://hide.me/en/freevpn",
            configSteps = listOf(
                "Regístrate gratis en hide.me.",
                "En el panel del usuario abre «Configuración manual» → WireGuard.",
                "Genera y descarga el .conf del servidor elegido.",
                "Agréjalo aquí con «Importar archivo»."
            )
        )
    )

    /** Aviso legal/ético mostrado en la pantalla VPN. */
    const val DISCLAIMER =
        "La VPN sirve para proteger tu privacidad y para conectarte desde una región " +
            "que tu proveedor de contenido PERMITA. No garantiza el acceso a contenido " +
            "restringido por el proveedor, y esta aplicación no evita geobloqueos, DRM, " +
            "límites de suscripción ni ningún otro control de acceso. Usa solo fuentes " +
            "IPTV que estés autorizado a utilizar."

    /** Nota sobre fugas DNS. */
    const val DNS_NOTE =
        "Protección contra fugas DNS: si tu configuración no declara servidores DNS, " +
            "la app añade automáticamente resolvedores públicos (1.1.1.1 / 8.8.8.8) para " +
            "que las consultas viajen dentro del túnel siempre que sea compatible."
}
