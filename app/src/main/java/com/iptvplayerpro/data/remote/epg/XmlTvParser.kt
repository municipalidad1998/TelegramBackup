package com.iptvplayerpro.data.remote.epg

import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.data.local.EpgChannelEntity
import com.iptvplayerpro.data.local.EpgProgrammeEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parser XMLTV (EPG estándar) con XmlPullParser (streaming, bajo consumo de
 * memoria). Emite por lotes para que el repositorio pueda insertar en
 * transacciones pequeñas y no bloquear la interfaz con listas grandes.
 */
object XmlTvParser {

    data class Parsed(
        val channels: List<EpgChannelEntity>,
        val programmes: List<EpgProgrammeEntity>
    )

    /**
     * Parsea el XML llamando a [onBatch] por cada lote de programas
     * (y una vez con los canales al final de la cabecera).
     */
    fun parse(
        input: InputStream,
        batchSize: Int = 2000,
        onBatch: (channels: List<EpgChannelEntity>, programmes: List<EpgProgrammeEntity>) -> Unit
    ) {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(input, null) // detección de encoding

        val channels = ArrayList<EpgChannelEntity>(512)
        val batch = ArrayList<EpgProgrammeEntity>(batchSize)

        var currentChannelId: String? = null
        var currentDisplayName: String? = null

        var inChannel = false
        var inProgramme = false
        var inTitle = false
        var inDesc = false
        var inDisplayName = false

        var programmeChannel: String? = null
        var programmeStart: Long? = null
        var programmeStop: Long? = null
        var title = StringBuilder()
        var desc = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> {
                        inChannel = true
                        currentChannelId = parser.getAttributeValue(null, "id")
                        currentDisplayName = null
                    }
                    "display-name" -> if (inChannel) inDisplayName = true
                    "programme" -> {
                        inProgramme = true
                        programmeChannel = parser.getAttributeValue(null, "channel")
                        programmeStart = TimeFmt.parseXmlTvDate(parser.getAttributeValue(null, "start") ?: "")
                        programmeStop = TimeFmt.parseXmlTvDate(parser.getAttributeValue(null, "stop") ?: "")
                        title = StringBuilder()
                        desc = StringBuilder()
                    }
                    "title" -> if (inProgramme) inTitle = true
                    "desc" -> if (inProgramme) inDesc = true
                }

                XmlPullParser.TEXT -> when {
                    inDisplayName -> currentDisplayName = (currentDisplayName ?: "") + parser.text
                    inTitle -> title.append(parser.text)
                    inDesc -> desc.append(parser.text)
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "display-name" -> inDisplayName = false
                    "title" -> inTitle = false
                    "desc" -> inDesc = false
                    "channel" -> {
                        inChannel = false
                        if (!currentChannelId.isNullOrBlank()) {
                            channels.add(
                                EpgChannelEntity(
                                    epgChannelId = currentChannelId,
                                    displayName = currentDisplayName?.trim()?.takeIf { it.isNotBlank() }
                                )
                            )
                        }
                        currentChannelId = null
                        currentDisplayName = null
                    }
                    "programme" -> {
                        inProgramme = false
                        val ch = programmeChannel
                        val start = programmeStart
                        val stop = programmeStop
                        if (!ch.isNullOrBlank() && start != null && stop != null && stop > start) {
                            batch.add(
                                EpgProgrammeEntity(
                                    epgChannelId = ch,
                                    title = title.toString().trim().ifBlank { "Sin título" },
                                    description = desc.toString().trim().takeIf { it.isNotBlank() },
                                    start = start,
                                    end = stop
                                )
                            )
                            if (batch.size >= batchSize) {
                                onBatch(emptyList(), batch.toList())
                                batch.clear()
                            }
                        }
                        programmeChannel = null
                        programmeStart = null
                        programmeStop = null
                    }
                }
            }
            eventType = parser.next()
        }

        if (batch.isNotEmpty()) onBatch(emptyList(), batch)
        if (channels.isNotEmpty()) onBatch(channels, emptyList())
    }
}
