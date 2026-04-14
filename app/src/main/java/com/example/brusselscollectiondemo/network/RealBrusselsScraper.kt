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
            val validationUrl = "$baseUrl/GetAddress.aspx".toHttpUrl().newBuilder()
                .addQueryParameter("rue", query.street)
                .addQueryParameter("numero", query.number)
                .addQueryParameter("zip", query.postalCode)
                .addQueryParameter("Lang", "fr")
                .addQueryParameter("operation", "VALIDATION")
                .build()

            val validationRequest = Request.Builder()
                .url(validationUrl)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            val validationText = client.newCall(validationRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d("SCRAPER", "VALIDATION URL = $validationUrl")
                Log.d("SCRAPER", "VALIDATION CODE = ${response.code}")
                Log.d("SCRAPER", "VALIDATION BODY = ${body.take(1000)}")
                check(response.isSuccessful) { "Erreur validation HTTP ${response.code}" }
                body
            }

            check(!validationText.trimStart().startsWith("<!DOCTYPE")) {
                "La validation renvoie du HTML au lieu de JSON. Réponse: ${validationText.take(200)}"
            }

            val validationJson = JSONObject(validationText)

            // Affiche le JSON exact pour qu'on adapte les clés ensuite
            Log.d("SCRAPER", "VALIDATION JSON = ${validationJson.toString(2)}")

            val streetId = validationJson.optString("StreetId")
                .ifBlank { validationJson.optString("streetId") }

            val houseNumberId = validationJson.optString("HouseNumberId")
                .ifBlank { validationJson.optString("houseNumberId") }

            check(streetId.isNotBlank()) {
                "Adresse non reconnue. JSON validation = ${validationJson.toString(2)}"
            }

            val calendarBody = FormBody.Builder()
                .add("streetId", streetId)
                .add("houseNumberId", houseNumberId)
                .add("lang", "fr")
                .build()

            val calendarRequest = Request.Builder()
                .url("$baseUrl/GetCalendarWeb.aspx")
                .post(calendarBody)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            val calendarText = client.newCall(calendarRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d("SCRAPER", "CALENDAR CODE = ${response.code}")
                Log.d("SCRAPER", "CALENDAR BODY = ${body.take(1500)}")
                check(response.isSuccessful) { "Erreur calendrier HTTP ${response.code}" }
                body
            }

            check(!calendarText.trimStart().startsWith("<!DOCTYPE")) {
                "Le calendrier renvoie du HTML au lieu de JSON. Réponse: ${calendarText.take(200)}"
            }

            val calendarJson = JSONObject(calendarText)
            Log.d("SCRAPER", "CALENDAR JSON = ${calendarJson.toString(2)}")

            val dataArray = when {
                calendarJson.has("Data") -> calendarJson.optJSONArray("Data") ?: JSONArray()
                calendarJson.has("data") -> calendarJson.optJSONArray("data") ?: JSONArray()
                else -> JSONArray()
            }

            val formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val formatter2 = DateTimeFormatter.ofPattern("dd/MM/yyyy")

            val events = mutableListOf<CollectionEvent>()

for (i in 0 until dataArray.length()) {
    val item = dataArray.getJSONObject(i)

    val rawDate = item.optString("Date")
        .ifBlank { item.optString("date") }

    val rawType = item.optString("Type")
        .ifBlank { item.optString("type") }
        .ifBlank { item.optString("Label") }

    val date = try {
        LocalDate.parse(rawDate, formatter1)
    } catch (_: Exception) {
        try {
            LocalDate.parse(rawDate, formatter2)
        } catch (_: Exception) {
            null
        }
    }

    if (date == null) {
        continue
    }

    val wasteType = when {
        rawType.contains("blanc", true) || rawType.contains("white", true) -> WasteType.WHITE
        rawType.contains("bleu", true) || rawType.contains("blue", true) -> WasteType.BLUE
        rawType.contains("jaune", true) || rawType.contains("yellow", true) -> WasteType.YELLOW
        rawType.contains("organ", true) || rawType.contains("orange", true) || rawType.contains("vert", true) -> WasteType.ORGANIC
        rawType.contains("encombr", true) || rawType.contains("bulky", true) -> WasteType.BULKY
        else -> WasteType.UNKNOWN
    }

    events.add(
        CollectionEvent(
            date = date,
            wasteType = wasteType,
            label = rawType.ifBlank { item.toString() }
        )
    )
}

            check(events.isNotEmpty()) {
                "Aucune collecte trouvée. JSON calendrier = ${calendarJson.toString(2)}"
            }

            CollectionSchedule(
                query = query,
                events = events.sortedBy { it.date }
            )
        }
    }
}
