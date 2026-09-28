package com.iptvplayerpro.vpn

/**
 * Inspección ligera de configuraciones WireGuard (sin la librería) para
 * validar y extraer datos informativos antes de guardar/cifrar.
 */
object WgConfigInspector {

    data class WgSummary(
        val endpointHost: String?,
        val publicKey: String?,
        val allowedIps: String?,
        val hasDns: Boolean
    )

    /** Normaliza saltos de línea y espacios. */
    fun normalize(text: String): String =
        text.replace("\r\n", "\n").replace("\r", "\n").trim()

    /** true si parece un .conf de WireGuard utilizable. */
    fun isValid(text: String): Boolean {
        val hasInterface = text.contains("[Interface]", ignoreCase = true)
        val hasPeer = text.contains("[Peer]", ignoreCase = true)
        val hasPrivateKey = text.lineSequence().any {
            it.trim().startsWith("PrivateKey", ignoreCase = true)
        }
        val hasPublicKey = text.lineSequence().any {
            it.trim().startsWith("PublicKey", ignoreCase = true)
        }
        return hasInterface && hasPeer && hasPrivateKey && hasPublicKey
    }

    /** Extrae datos resumidos (nunca la clave privada). */
    fun inspect(text: String): WgSummary {
        var endpoint: String? = null
        var publicKey: String? = null
        var allowedIps: String? = null
        var hasDns = false
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            val value = trimmed.substringAfter('=', "").trim()
            when {
                trimmed.startsWith("Endpoint", true) && endpoint == null -> {
                    endpoint = value.substringBeforeLast(":").ifBlank { null }
                }
                trimmed.startsWith("PublicKey", true) && publicKey == null -> {
                    publicKey = value.take(8) + "…" // solo un prefijo, no la clave completa
                }
                trimmed.startsWith("AllowedIPs", true) && allowedIps == null -> {
                    allowedIps = value
                }
                trimmed.startsWith("DNS", true) -> hasDns = true
            }
        }
        return WgSummary(endpoint, publicKey, allowedIps, hasDns)
    }

    /**
     * Protección contra fugas DNS: si la [Interface] no declara DNS, se añaden
     * resolvedores públicos para que las consultas no salgan fuera del túnel.
     */
    fun ensureDns(text: String): String {
        if (inspect(text).hasDns) return text
        return text.replaceFirst(
            Regex("(?i)(\\[Interface\\])"),
            "$1\n# DNS añadido por IPTV Player Pro (protección de fugas DNS)\nDNS = 1.1.1.1, 8.8.8.8"
        )
    }

    /** Intenta deducir el país a partir del nombre del perfil. */
    fun guessCountry(name: String): Pair<String, String>? {
        val lower = name.lowercase()
        for ((code, names) in COUNTRY_KEYWORDS) {
            if (names.any { lower.contains(it) }) return countryName(code) to code
        }
        // Códigos ISO directos: "us", "jp-free", "es#1"…
        val tokens = lower.split(Regex("[^a-z]+"))
        for (token in tokens) {
            if (token.length == 2) {
                COUNTRY_KEYWORDS[token.uppercase()]?.let { return countryName(token.uppercase()) to token.uppercase() }
            }
        }
        return null
    }

    fun countryName(code: String): String = COUNTRY_NAMES[code.uppercase()] ?: code

    private val COUNTRY_NAMES: Map<String, String> = mapOf(
        "AR" to "Argentina", "BO" to "Bolivia", "BR" to "Brasil", "CA" to "Canadá",
        "CL" to "Chile", "CO" to "Colombia", "CR" to "Costa Rica", "CU" to "Cuba",
        "DO" to "Rep. Dominicana", "EC" to "Ecuador", "SV" to "El Salvador",
        "ES" to "España", "US" to "Estados Unidos", "FR" to "Francia", "DE" to "Alemania",
        "GB" to "Reino Unido", "IT" to "Italia", "NL" to "Países Bajos", "SE" to "Suecia",
        "CH" to "Suiza", "NO" to "Noruega", "FI" to "Finlandia", "PL" to "Polonia",
        "PT" to "Portugal", "RO" to "Rumania", "TR" to "Turquía", "UA" to "Ucrania",
        "RU" to "Rusia", "MX" to "México", "PE" to "Perú", "PY" to "Paraguay",
        "UY" to "Uruguay", "VE" to "Venezuela", "PA" to "Panamá", "GT" to "Guatemala",
        "HN" to "Honduras", "NI" to "Nicaragua", "PR" to "Puerto Rico", "IN" to "India",
        "JP" to "Japón", "SG" to "Singapur", "HK" to "Hong Kong", "KR" to "Corea del Sur",
        "AU" to "Australia", "NZ" to "Nueva Zelanda", "ZA" to "Sudáfrica", "AE" to "EAU",
        "IL" to "Israel", "IE" to "Irlanda", "AT" to "Austria", "BE" to "Bélgica",
        "DK" to "Dinamarca", "CZ" to "Chequia", "GR" to "Grecia", "HU" to "Hungría",
        "MY" to "Malasia", "TH" to "Tailandia", "VN" to "Vietnam", "ID" to "Indonesia",
        "PH" to "Filipinas", "EG" to "Egipto", "MA" to "Marruecos"
    )

    private val COUNTRY_KEYWORDS: Map<String, List<String>> = mapOf(
        "AR" to listOf("argentina", "buenos aires"),
        "BR" to listOf("brasil", "brazil", "sao paulo", "são paulo"),
        "CA" to listOf("canada", "montreal", "toronto", "vancouver"),
        "CL" to listOf("chile", "santiago"),
        "CO" to listOf("colombia", "bogota", "bogotá"),
        "MX" to listOf("mexico", "méxico", "cdmx"),
        "ES" to listOf("españa", "espana", "spain", "madrid", "barcelona"),
        "US" to listOf("usa", "estados unidos", "united states", "eeuu", "new york", "los angeles", "chicago", "texas", "miami", "seattle"),
        "FR" to listOf("francia", "france", "paris", "parís"),
        "DE" to listOf("alemania", "germany", "berlin", "berlín", "frankfurt"),
        "GB" to listOf("reino unido", "uk", "london", "londres", "england", "britain"),
        "IT" to listOf("italia", "italy", "milan", "milán", "roma"),
        "NL" to listOf("paises bajos", "holanda", "netherlands", "amsterdam"),
        "SE" to listOf("suecia", "sweden", "stockholm"),
        "CH" to listOf("suiza", "switzerland", "zurich", "zurich"),
        "NO" to listOf("noruega", "norway", "oslo"),
        "FI" to listOf("finlandia", "finland", "helsinki"),
        "PL" to listOf("polonia", "poland", "warsaw", "varsovia"),
        "PT" to listOf("portugal", "lisboa", "lisbon", "oporto"),
        "RO" to listOf("rumania", "romania", "bucharest"),
        "TR" to listOf("turquia", "turquía", "turkey", "istanbul", "estambul"),
        "UA" to listOf("ucrania", "ukraine", "kyiv", "kiev"),
        "RU" to listOf("rusia", "russia", "moscu", "moscú"),
        "PE" to listOf("peru", "perú", "lima"),
        "UY" to listOf("uruguay", "montevideo"),
        "VE" to listOf("venezuela", "caracas"),
        "EC" to listOf("ecuador", "quito"),
        "PY" to listOf("paraguay", "asuncion", "asunción"),
        "BO" to listOf("bolivia", "la paz"),
        "CR" to listOf("costa rica", "san jose", "san josé"),
        "PA" to listOf("panama", "panamá"),
        "IN" to listOf("india", "mumbai", "delhi"),
        "JP" to listOf("japon", "japón", "japan", "tokyo", "tokio", "osaka"),
        "SG" to listOf("singapur", "singapore"),
        "HK" to listOf("hong kong", "hongkong"),
        "KR" to listOf("corea", "korea", "seul", "seúl", "seoul"),
        "AU" to listOf("australia", "sydney", "melbourne"),
        "NZ" to listOf("nueva zelanda", "new zealand", "auckland"),
        "ZA" to listOf("sudafrica", "sudáfrica", "south africa", "johannesburg"),
        "AE" to listOf("emiratos", "dubai", "dubái", "uae"),
        "IL" to listOf("israel", "tel aviv"),
        "IE" to listOf("irlanda", "ireland", "dublin", "dublín"),
        "AT" to listOf("austria", "viena", "vienna"),
        "BE" to listOf("belgica", "bélgica", "belgium", "brussels", "bruselas"),
        "DK" to listOf("dinamarca", "denmark", "copenhagen"),
        "CZ" to listOf("chequia", "czech", "praga", "prague"),
        "GR" to listOf("grecia", "greece", "atenas", "athens"),
        "MY" to listOf("malasia", "malaysia", "kuala lumpur"),
        "TH" to listOf("tailandia", "thailand", "bangkok"),
        "VN" to listOf("vietnam", "hanoi", "ho chi minh"),
        "ID" to listOf("indonesia", "jakarta"),
        "PH" to listOf("filipinas", "philippines", "manila"),
        "EG" to listOf("egipto", "egypt", "cairo"),
        "MA" to listOf("marruecos", "morocco", "casablanca")
    )
}
