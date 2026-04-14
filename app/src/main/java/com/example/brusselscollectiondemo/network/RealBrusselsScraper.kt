package com.example.brusselscollectiondemo.network

import android.util.Log
import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionEvent
import com.example.brusselscollectiondemo.data.CollectionSchedule
import com.example.brusselscollectiondemo.data.WasteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class RealBrusselsScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://formsv2.arp-gan.eu"

    suspend fun fetch(query: AddressQuery): Result<CollectionSchedule> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedStreet = query.street.trim()

            // 1) SEARCH
            val searchJson = callGetAddress(
                rue = normalizedStreet,
                numero = null,
                zip = null,
                operation = "SEARCH"
            )

            Log.d("SCRAPER", "SEARCH JSON = ${searchJson.toString(2)}")

            val selectedStreet = extractBestStreetLabel(searchJson, normalizedStreet)
                ?: error("Aucune rue trouvée pour '$normalizedStreet'")

            // 2) ADRESS_NUMBER_RANGE
            val numberRangeJson = callGetAddress(
                rue = selectedStreet,
                numero = null,
                zip = query.postalCode,
                operation = "ADRESS_NUMBER_RANGE"
            )

            Log.d("SCRAPER", "NUMBER_RANGE JSON = ${numberRangeJson.toString(2)}")

            val validatedNumber = extractMatchingNumber(numberRangeJson, query.number)
                ?: query.number

            // 3) VALIDATION
            val validationJson = callGetAddress(
                rue = selectedStreet,
                numero = validatedNumber,
                zip = query.postalCode,
                operation = "VALIDATION"
            )

            Log.d("SCRAPER", "VALIDATION JSON = ${validationJson.toString(2)}")

            val streetId = extractString(validationJson,
                "StreetId", "streetId", "IdStreet", "idStreet", "Street_ID"
            )

            val houseNumberId = extractString(validationJson,
                "HouseNumberId", "houseNumberId", "IdHouseNumber", "idHouseNumber", "HouseNumber_ID"
            )

            check(!streetId.isNullOrBlank()) {
                "streetId introuvable dans la réponse VALIDATION"
            }

            // houseNumberId peut parfois être absent selon la structure réelle
            val calendarJson = callCalendar(
                streetId = streetId,
                houseNumberId = houseNumberId,
                lang = "fr"
            )

            Log.d("SCRAPER", "CALENDAR JSON = ${calendarJson.toString(2)}")

            val events = extractCalendarEvents(calendarJson)

            check(events.isNotEmpty()) {
                "Aucune collecte trouvée dans la réponse calendrier"
            }

            CollectionSchedule(
                query = query.copy(street = selectedStreet, number = validatedNumber),
                events = events.sortedBy { it.date }
            )
        }
    }

    private fun callGetAddress(
        rue: String,
        numero: String?,
        zip: String?,
        operation: String
    ): JSONObject {
        val urlBuilder = "$baseUrl/GetAddress.aspx".toHttpUrl().newBuilder()
            .addQueryParameter("rue", rue)
            .addQueryParameter("Lang", "fr")
            .addQueryParameter("operation", operation)

        if (!numero.isNullOrBlank()) {
            urlBuilder.addQueryParameter("numero", numero)
        }

        if (!zip.isNullOrBlank()) {
            urlBuilder.addQueryParameter("zip", zip)
        }

        val url = urlBuilder.build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", browserLikeUserAgent())
            .header("Referer", "https://www.arp-gan.be/")
            .header("Origin", "https://www.arp-gan.be")
            .build()

        val body = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.d("SCRAPER", "$operation URL = $url")
            Log.d("SCRAPER", "$operation CODE = ${response.code}")
            Log.d("SCRAPER", "$operation BODY = ${text.take(1500)}")

            check(response.isSuccessful) {
                "Erreur $operation HTTP ${response.code}"
            }

            check(!text.trimStart().startsWith("<!DOCTYPE")) {
                "$operation renvoie du HTML au lieu de JSON"
            }

            text
        }

        return parseJsonLenient(body)
    }

    private fun callCalendar(
        streetId: String,
        houseNumberId: String?,
        lang: String
    ): JSONObject {
        val formBuilder = FormBody.Builder()
            .add("streetId", streetId)
            .add("lang", lang)

        if (!houseNumberId.isNullOrBlank()) {
            formBuilder.add("houseNumberId", houseNumberId)
        }

        val request = Request.Builder()
            .url("$baseUrl/GetCalendarWeb.aspx")
            .post(formBuilder.build())
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", browserLikeUserAgent())
            .header("Referer", "https://www.arp-gan.be/")
            .header("Origin", "https://www.arp-gan.be")
            .build()

        val body = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.d("SCRAPER", "CALENDAR CODE = ${response.code}")
            Log.d("SCRAPER", "CALENDAR BODY = ${text.take(2000)}")

            check(response.isSuccessful) {
                "Erreur calendrier HTTP ${response.code}"
            }

            check(!text.trimStart().startsWith("<!DOCTYPE")) {
                "Le calendrier renvoie du HTML au lieu de JSON"
            }

            text
        }

        return parseJsonLenient(body)
    }

    private fun extractBestStreetLabel(json: JSONObject, typedStreet: String): String? {
        val candidates = mutableListOf<String>()

        collectAllStrings(json, candidates)

        val normalizedTyped = typedStreet.trim().lowercase()

        return candidates
            .distinct()
            .firstOrNull { candidate ->
                val c = candidate.lowercase()
                c.contains(normalizedTyped) && (
                    c.contains("rue") ||
                    c.contains("avenue") ||
                    c.contains("chaussée") ||
                    c.contains("boulevard") ||
                    c.contains("place")
                )
            }
            ?: candidates.distinct().firstOrNull()
    }

    private fun extractMatchingNumber(json: JSONObject, requestedNumber: String): String? {
        val candidates = mutableListOf<String>()
        collectAllStrings(json, candidates)

        return candidates
            .map { it.trim() }
            .firstOrNull { it == requestedNumber }
    }

    private fun extractCalendarEvents(json: JSONObject): List<CollectionEvent> {
        val array = findFirstArray(json, "Data", "data", "Items", "items", "Calendar", "calendar")
            ?: return emptyList()

        val formatterIso = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formatterFr = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        val events = mutableListOf<CollectionEvent>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val rawDate = firstNonBlank(
                item.optString("Date"),
                item.optString("date"),
                item.optString("CollectionDate"),
                item.optString("collectionDate")
            ) ?: continue

            val rawType = firstNonBlank(
                item.optString("Type"),
                item.optString("type"),
                item.optString("Label"),
                item.optString("label"),
                item.optString("WasteType"),
                item.optString("wasteType")
            ) ?: item.toString()

            val date = parseDate(rawDate, formatterIso, formatterFr) ?: continue

            events.add(
                CollectionEvent(
                    date = date,
                    wasteType = mapWasteType(rawType),
                    label = rawType
                )
            )
        }

        return events
    }

    private fun parseDate(
        raw: String,
        iso: DateTimeFormatter,
        fr: DateTimeFormatter
    ): LocalDate? {
        return try {
            LocalDate.parse(raw, iso)
        } catch (_: Exception) {
            try {
                LocalDate.parse(raw, fr)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun mapWasteType(rawType: String): WasteType {
        val s = rawType.lowercase()
        return when {
            "blanc" in s || "white" in s -> WasteType.WHITE
            "bleu" in s || "blue" in s -> WasteType.BLUE
            "jaune" in s || "yellow" in s || "papier" in s -> WasteType.YELLOW
            "organ" in s || "orange" in s || "vert" in s || "food" in s -> WasteType.ORGANIC
            "encombr" in s || "bulky" in s -> WasteType.BULKY
            else -> WasteType.UNKNOWN
        }
    }

    private fun parseJsonLenient(text: String): JSONObject {
        val trimmed = text.trim()

        return when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONObject().put("data", JSONArray(trimmed))
            else -> error("Réponse non JSON: ${trimmed.take(200)}")
        }
    }

    private fun extractString(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = json.optString(key)
            if (value.isNotBlank()) return value
        }

        val nested = findFirstObject(json)
        if (nested != null) {
            for (key in keys) {
                val value = nested.optString(key)
                if (value.isNotBlank()) return value
            }
        }

        return null
    }

    private fun findFirstArray(json: JSONObject, vararg keys: String): JSONArray? {
        for (key in keys) {
            val arr = json.optJSONArray(key)
            if (arr != null) return arr
        }

        val nested = findFirstObject(json)
        if (nested != null) {
            for (key in keys) {
                val arr = nested.optJSONArray(key)
                if (arr != null) return arr
            }
        }

        return null
    }

    private fun findFirstObject(json: JSONObject): JSONObject? {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)
            if (value is JSONObject) return value
        }
        return null
    }

    private fun collectAllStrings(json: JSONObject, out: MutableList<String>) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)
            when (value) {
                is String -> if (value.isNotBlank()) out.add(value)
                is JSONObject -> collectAllStrings(value, out)
                is JSONArray -> collectAllStrings(value, out)
            }
        }
    }

    private fun collectAllStrings(array: JSONArray, out: MutableList<String>) {
        for (i in 0 until array.length()) {
            when (val value = array.opt(i)) {
                is String -> if (value.isNotBlank()) out.add(value)
                is JSONObject -> collectAllStrings(value, out)
                is JSONArray -> collectAllStrings(value, out)
            }
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun browserLikeUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
