package com.example.brusselscollectiondemo.network

import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionEvent
import com.example.brusselscollectiondemo.data.CollectionSchedule
import com.example.brusselscollectiondemo.data.WasteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
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

            // 1. Validation adresse
            val validationUrl = "$baseUrl/GetAddress.aspx" +
                    "?rue=${query.street}" +
                    "&numero=${query.number}" +
                    "&zip=${query.postalCode}" +
                    "&Lang=fr" +
                    "&operation=VALIDATION"

            val validationResponse = client.newCall(
                Request.Builder().url(validationUrl).build()
            ).execute().use {
                JSONObject(it.body?.string() ?: "{}")
            }

            // ⚠️ IMPORTANT : extraire les bons champs depuis la réponse JSON
            val streetId = validationResponse.optString("StreetId")
            val houseNumberId = validationResponse.optString("HouseNumberId")

            check(streetId.isNotEmpty()) { "Adresse invalide" }

            // 2. Appel calendrier
            val body = FormBody.Builder()
                .add("streetId", streetId)
                .add("houseNumberId", houseNumberId)
                .add("lang", "fr")
                .build()

            val calendarResponse = client.newCall(
                Request.Builder()
                    .url("$baseUrl/GetCalendarWeb.aspx")
                    .post(body)
                    .build()
            ).execute().use {
                JSONObject(it.body?.string() ?: "{}")
            }

            val eventsJson = calendarResponse.optJSONArray("Data") ?: JSONArray()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val events = mutableListOf<CollectionEvent>()

            for (i in 0 until eventsJson.length()) {
                val item = eventsJson.getJSONObject(i)

                val date = LocalDate.parse(item.getString("Date"), formatter)
                val type = item.optString("Type")

                val wasteType = when {
                    type.contains("white", true) -> WasteType.WHITE
                    type.contains("blue", true) -> WasteType.BLUE
                    type.contains("yellow", true) -> WasteType.YELLOW
                    type.contains("organic", true) -> WasteType.ORGANIC
                    else -> WasteType.UNKNOWN
                }

                events.add(
                    CollectionEvent(
                        date = date,
                        wasteType = wasteType,
                        label = type
                    )
                )
            }

            CollectionSchedule(
                query = query,
                events = events
            )
        }
    }
}
