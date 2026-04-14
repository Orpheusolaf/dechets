package com.example.brusselscollectiondemo.network

import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionEvent
import com.example.brusselscollectiondemo.data.CollectionSchedule
import com.example.brusselscollectiondemo.data.WasteType
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CollectionParser {

    private val frDate = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.FRANCE)

    fun parse(html: String, query: AddressQuery): CollectionSchedule {
        val doc = Jsoup.parse(html)

        val candidateTexts = doc.select("main, body, .main-content, .region-content")
            .flatMap { root -> root.select("tr, li, p, div, span") }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val events = candidateTexts
            .mapNotNull { parseLine(it) }
            .distinctBy { "${it.date}|${it.wasteType}|${it.label}" }
            .sortedBy { it.date }

        return CollectionSchedule(
            query = query,
            events = events
        )
    }

    private fun parseLine(line: String): CollectionEvent? {
        val regex = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
        val match = regex.find(line) ?: return null

        val date = runCatching { LocalDate.parse(match.value, frDate) }.getOrNull() ?: return null
        val lower = line.lowercase()

        val wasteType = when {
            "blanc" in lower -> WasteType.WHITE
            "bleu" in lower -> WasteType.BLUE
            "jaune" in lower -> WasteType.YELLOW
            "organique" in lower || "orange" in lower || "vert" in lower -> WasteType.ORGANIC
            "encombr" in lower -> WasteType.BULKY
            else -> WasteType.UNKNOWN
        }

        return CollectionEvent(
            date = date,
            wasteType = wasteType,
            label = line
        )
    }
}
