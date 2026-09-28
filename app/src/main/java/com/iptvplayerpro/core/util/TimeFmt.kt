package com.iptvplayerpro.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formato de fechas/horas en español. */
object TimeFmt {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEEE d MMM", Locale.getDefault())
    private val xmltvWithZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val xmltvNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    fun time(millis: Long): String = timeFormat.format(Date(millis))

    fun dateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

    fun date(millis: Long): String = dateFormat.format(Date(millis))

    fun dayLabel(millis: Long): String =
        dayFormat.format(Date(millis)).replaceFirstChar { it.uppercase(Locale.getDefault()) }

    /** "Hoy" / "Ayer" / fecha. */
    fun relativeDay(millis: Long): String {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            millis >= today -> "Hoy"
            millis >= today - TimeUnit.DAYS.toMillis(1) -> "Ayer"
            else -> date(millis)
        }
    }

    /** Hace X min/horas/días. */
    fun ago(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "hace instantes"
            diff < TimeUnit.HOURS.toMillis(1) -> "hace ${TimeUnit.MILLISECONDS.toMinutes(diff)} min"
            diff < TimeUnit.DAYS.toMillis(1) -> "hace ${TimeUnit.MILLISECONDS.toHours(diff)} h"
            else -> "hace ${TimeUnit.MILLISECONDS.toDays(diff)} d"
        }
    }

    /** Parsea la fecha de XMLTV: "20240101120000 +0000" o "20240101120000". */
    fun parseXmlTvDate(raw: String): Long? {
        val value = raw.trim()
        if (value.length < 14) return null
        return try {
            if (value.contains("+") || (value.length > 14 && value[14] == '-') || (value.length > 14 && value[14] == ' ')) {
                xmltvWithZone.parse(value)?.time
            } else {
                xmltvNoZone.parse(value.substring(0, 14))?.time
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Milisegundos legibles: 1h 25m / 45m / —. */
    fun durationMs(millis: Long): String {
        if (millis <= 0) return "—"
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun ms(mmss: String): Long {
        val parts = mmss.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toLong() * 60_000 + parts[1].toLong() * 1000
                3 -> parts[0].toLong() * 3_600_000 + parts[1].toLong() * 60_000 + parts[2].toLong() * 1000
                else -> 0L
            }
        } catch (e: NumberFormatException) {
            0L
        }
    }
}
