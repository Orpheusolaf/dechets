package com.example.brusselscollectiondemo.network

import android.util.Log
import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionEvent
import com.example.brusselscollectiondemo.data.CollectionSchedule
import com.example.brusselscollectiondemo.data.WasteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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

    suspend fun fetch(query: AddressQuery): Result<CollectionSchedule> = withContext(Dispatchers.IO) {
        runCatching {
            // Test volontairement figé sur l'adresse qui marche côté site
            val validationJson = callValidation(
                rue = "Rue Franklin",
                numero = "1",
                zip = "1000",
                commune = "Bruxelles",
                id = "2112530"
            )

            Log.d("SCRAPER", "VALIDATION JSON = ${validationJson.toString(2)}")

            val streetId = extractString(
                validationJson,
                "StreetId", "streetId", "IdStreet", "idStreet", "Street_ID",
                "ID_STREET", "streetID"
            )

            val houseNumberId = extractString(
                validationJson,
                "HouseNumberId", "houseNumberId", "IdHouseNumber", "idHouseNumber",
                "HouseNumber_ID", "ID_HOUSENUMBER", "houseNumberID"
            )

            check(!streetId.isNullOrBlank()) {
                "streetId introuvable dans VALIDATION: ${validationJson.toString(2)}"
            }

            val calendarJson = callCalendar(
                streetId = streetId,
                houseNumberId = houseNumberId,
                lang = "fr"
            )

            Log.d("SCRAPER", "CALENDAR JSON = ${calendarJson.toString(2)}")

            val events = extractCalendarEvents(calendarJson)

            check(events.isNotEmpty()) {
                "Aucune collecte trouvée. Réponse calendrier: ${calendarJson.toString(2)}"
            }

            CollectionSchedule(
                query = query,
                events = events.sortedBy { it.date }
            )
        }
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
            .url("https://formsv2.arp-gan.eu/GetAddress.aspx")
            .post(body)
            .header("Accept", "application/json, text/plain, */*")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            )
            .header("Referer", "https://www.arp-gan.be/")
            .header("Origin", "https://www.arp-gan.be")
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
        streetId: String,
        houseNumberId: String?,
        lang: String
    ): JSONObject {
        val form = FormBody.Builder()
            .add("streetId", streetId)
            .add("lang", lang)
            .apply {
                if (!houseNumberId.isNullOrBlank()) {
                    add("houseNumberId", houseNumberId)
                }
            }
            .build()

        val request = Request.Builder()
            .url("https://formsv2.arp-gan.eu/GetCalendarWeb.aspx")
            .post(form)
            .header("Accept", "application/json, text/plain, */*")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            )
            .header("Referer", "https://www.arp-gan.be/")
            .header("Origin", "https://www.arp-gan.be")
            .build()

        val text = client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()

            Log.d("SCRAPER", "CALENDAR CODE = ${response.code}")
            Log.d("SCRAPER", "CALENDAR BODY = ${responseText.take(2000)}")

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

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }
}
