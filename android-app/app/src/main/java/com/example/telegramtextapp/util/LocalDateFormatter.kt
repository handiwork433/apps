package com.example.telegramtextapp.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LocalDateFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
) {
    fun format(isoString: String?): String? {
        if (isoString.isNullOrBlank()) return null
        return runCatching {
            val instant = Instant.parse(isoString)
            formatter.format(instant.atZone(zoneId))
        }.getOrNull()
    }
}
