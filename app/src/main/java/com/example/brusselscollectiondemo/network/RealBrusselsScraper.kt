package com.example.brusselscollectiondemo.network

import android.util.Log
import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionEvent
import com.example.brusselscollectiondemo.data.CollectionSchedule
import com.example.brusselscollectiondemo.data.WasteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
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

    private val searchEndpoint = "https://formsv2.arp-gan.eu/StreetEngine/GetAdress.aspx"
    private val calendarEndpoint = "https://formsv2.arp-gan.eu/GetCalendarv5//GetCalendarWeb.aspx"
    private val referer = "https://formsv2.arp-gan.eu/CalendarV5/?Language=FR"
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0"

    suspend fun fetch(query: AddressQuery): Result<CollectionSchedule> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedStreet = normalizeStreet(query.street)
            val normalizedNumber = query.number.trim()
            val normalizedZip = query.postalCode.trim()
            val normalizedCommune = query.municipality.trim()

            // 1. SEARCH
            val searchJson = callSearch(normalizedStreet)
            Log.d("SCRAPER", "SEARCH JSON = ${searchJson.toString(2)}")

            val match = extractSearchMatch(searchJson, normalizedStreet, normalizedZip)
                ?: error("Aucune rue trouvée pour '$normalizedStreet'")

            val selectedStreet = match.streetLabel
            val selectedId = match.id

            // 2. VALIDATION
            val validationJson = callValidation(
                rue = selectedStreet,
                numero = normalizedNumber,
                zip = normalizedZip,
                commune = normalizedCommune,
                id = selectedId
            )
            Log.d("SCRAPER", "VALIDATION JSON = ${validationJson.toString(2)}")

            val dataArray = validationJson.optJSONArray("data")
            val firstItem = if (dataArray != null && dataArray.length() > 0) dataArray.optJSONObject(0) else null
            val validatedId = firstItem?.optString("Value")

            check(!validatedId.isNullOrBlank()) {
                "id introuvable dans VALIDATION: ${validationJson.toString(2)}"
            }

            // 3. CALENDAR
            val calendarJson = callCalendar(
                rue = selectedStreet,
                numero = normalizedNumber,
                zip = normalizedZip,
                commune = normalizedCommune,
                id = validatedId
            )
            Log.d("SCRAPER", "CALENDAR JSON = ${calendarJson.toString(2)}")

            val events = extractCalendarEvents(calendarJson)

            val calendarImageUrl = firstNonBlank(
                calendarJson.optString("img_ramassage"),
                calendarJson.optString("Img_ramassage"),
                calendarJson.optString("image"),
                calendarJson.optString("img")
            )

            val calendarPdfUrl = firstNonBlank(
                calendarJson.optString("pdf"),
                calendarJson.optString("Pdf"),
                calendarJson.optString("pdf_ramassage"),
                calendarJson.optString("calendarPdf")
            )

            CollectionSchedule(
                query = query.copy(
                    street = selectedStreet,
                    number = normalizedNumber,
                    postalCode = normalizedZip,
                    municipality = normalizedCommune
                ),
                events = events.sortedBy { it.date },
                calendarImageUrl = calendarImageUrl,
                calendarPdfUrl = calendarPdfUrl
            )
        }
    }

    private fun callSearch(rue: String): JSONObject {
        val url = searchEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("rue", rue)
            .addQueryParameter("Lang", "fr")
            .addQueryParameter("operation", "SEARCH")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "fr,fr-FR;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", referer)
            .build()

        val text = client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d("SCRAPER", "SEARCH URL = $url")
            Log.d("SCRAPER", "SEARCH CODE = ${response.code}")
            Log.d("SCRAPER", "SEARCH BODY = ${body.take(2000)}")
            check(response.isSuccessful) { "Erreur SEARCH HTTP ${response.code}: ${body.take(300)}" }
            check(!body.trimStart().startsWith("<!DOCTYPE")) {
                "SEARCH renvoie du HTML au lieu de JSON: ${body.take(300)}"
            }
            body
        }

        return parseJsonLenient(text)
    }

    private fun callValidation(
        rue: String,
        numero: String,
        zip: String,
        commune: String,
        id: String
    ): JSONObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("rue", rue)
            .addFormDataPart("numero", numero)
            .addFormDataPart("zip", zip)
            .addFormDataPart("commune", commune)
            .addFormDataPart("id", id)
            .addFormDataPart("Lang", "FR")
            .addFormDataPart("operation", "VALIDATION")
            .build()

        val request = Request.Builder()
            .url(searchEndpoint)
            .post(body)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", userAgent)
            .header("Origin", "https://formsv2.arp-gan.eu")
            .header("Referer", referer)
            .build()

        val text = client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            Log.d("SCRAPER", "VALIDATION CODE = ${response.code}")
            Log.d("SCRAPER", "VALIDATION BODY = ${responseText.take(2000)}")
            check(response.isSuccessful) {
                "Erreur validation HTTP ${response.code}: ${responseText.take(300)}"
            }
            check(!responseText.trimStart().startsWith("<!DOCTYPE")) {
                "La validation renvoie du HTML au lieu de JSON: ${responseText.take(300)}"
            }
            responseText
        }

        return parseJsonLenient(text)
    }

    private fun callCalendar(
        rue: String,
        numero: String,
        zip: String,
        commune: String,
        id: String
    ): JSONObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("rue", rue)
            .addFormDataPart("numero", numero)
            .addFormDataPart("zip", zip)
            .addFormDataPart("commune", commune)
            .addFormDataPart("id", id)
            .addFormDataPart("Lang", "FR")
            .addFormDataPart("operation", "VALIDATION")
            .build()

        val request = Request.Builder()
            .url(calendarEndpoint)
            .post(body)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", userAgent)
            .header("Origin", "https://formsv2.arp-gan.eu")
            .header("Referer", referer)
            .build()

        val text = client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            Log.d("SCRAPER", "CALENDAR CODE = ${response.code}")
            Log.d("SCRAPER", "CALENDAR BODY = ${responseText.take(3000)}")
            check(response.isSuccessful) {
                "Erreur calendrier HTTP ${response.code}: ${responseText.take(300)}"
            }
            check(!responseText.trimStart().startsWith("<!DOCTYPE")) {
                "Le calendrier renvoie du HTML au lieu de JSON: ${responseText.take(300)}"
            }
            responseText
        }

        return parseJsonLenient(text)
    }

    private data class SearchMatch(
        val streetLabel: String,
        val id: String,
        val zip: String?
    )

    private fun extractSearchMatch(
        json: JSONObject,
        typedStreet: String,
        zip: String
    ): SearchMatch? {
        val candidates = mutableListOf<SearchMatch>()

        val dataArray = json.optJSONArray("data")
        if (dataArray != null) {
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue

                val streetLabel = firstNonBlank(
                    item.optString("Full"),
                    item.optString("full"),
                    item.optString("Rue"),
                    item.optString("rue"),
                    item.optString("Street"),
                    item.optString("street"),
                    item.optString("Value"),
                    item.optString("value")
                )

                val id = firstNonBlank(
                    item.optString("Id"),
                    item.optString("id"),
                    item.optString("Value"),
                    item.optString("value")
                )

                val itemZip = firstNonBlank(
                    item.optString("Zip"),
                    item.optString("zip"),
                    item.optString("CodePostal"),
                    item.optString("codePostal")
                )

                if (!streetLabel.isNullOrBlank() && !id.isNullOrBlank()) {
                    candidates.add(SearchMatch(streetLabel, id, itemZip))
                }
            }
        }

        if (candidates.isEmpty()) {
            val strings = mutableListOf<String>()
            collectAllStrings(json, strings)
            val guessedStreet = strings.firstOrNull { it.contains(typedStreet, ignoreCase = true) }
            val guessedId = strings.firstOrNull { it.all(Char::isDigit) }
            if (!guessedStreet.isNullOrBlank() && !guessedId.isNullOrBlank()) {
                return SearchMatch(guessedStreet, guessedId, null)
            }
            return null
        }

        return candidates.firstOrNull {
            (it.zip == null || it.zip == zip) && it.streetLabel.contains(typedStreet, ignoreCase = true)
        } ?: candidates.first()
    }

    private fun extractCalendarEvents(json: JSONObject): List<CollectionEvent> {
        val array = findFirstArray(
            json,
            "Data", "data", "Items", "items", "Calendar", "calendar"
        ) ?: return emptyList()

        val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val frFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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

            val date = parseDate(rawDate, isoFormatter, frFormatter) ?: continue

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
            else -> error("Réponse non JSON: ${trimmed.take(300)}")
        }
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
            when (val value = json.opt(key)) {
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

    private fun normalizeStreet(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return s
        val lower = s.lowercase()
        return if (
            lower.startsWith("rue ") ||
            lower.startsWith("avenue ") ||
            lower.startsWith("chaussée ") ||
            lower.startsWith("boulevard ") ||
            lower.startsWith("place ")
        ) s else "Rue $s"
    }
}
