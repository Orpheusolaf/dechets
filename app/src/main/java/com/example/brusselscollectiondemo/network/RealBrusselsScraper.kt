package com.example.brusselscollectiondemo.network

import com.example.brusselscollectiondemo.data.AddressQuery
import com.example.brusselscollectiondemo.data.CollectionSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class RealBrusselsScraper(
    private val parser: CollectionParser = CollectionParser()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val calendarUrl = "https://www.arp-gan.be/fr/calendrier-sorties-de-sacs"

    suspend fun fetch(query: AddressQuery): Result<CollectionSchedule> = withContext(Dispatchers.IO) {
        runCatching {
            val initialRequest = Request.Builder()
                .url(calendarUrl)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            val initialHtml = client.newCall(initialRequest).execute().use { response ->
                check(response.isSuccessful) { "Erreur GET: ${response.code}" }
                response.body?.string().orEmpty()
            }

            val doc = Jsoup.parse(initialHtml)
            val form = doc.selectFirst("form") ?: error("Formulaire introuvable")

            val actionUrl = form.absUrl("action").ifBlank { calendarUrl }

            val fields = form.select("input[type=hidden]")
                .associate { it.attr("name") to it.attr("value") }
                .toMutableMap()

            /*
             * A AJUSTER SI NECESSAIRE :
             * remplace ces clés par les vrais attributs 'name' du formulaire
             * après inspection du HTML réel.
             */
            fields["street"] = query.street
            fields["number"] = query.number
            fields["postal_code"] = query.postalCode
            fields["municipality"] = query.municipality

            val formBody = FormBody.Builder().apply {
                fields.forEach { (k, v) -> add(k, v) }
            }.build()

            val postRequest = Request.Builder()
                .url(actionUrl)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            val resultHtml = client.newCall(postRequest).execute().use { response ->
                check(response.isSuccessful) { "Erreur POST: ${response.code}" }
                response.body?.string().orEmpty()
            }

            val schedule = parser.parse(resultHtml, query)

            check(schedule.events.isNotEmpty()) {
                "Aucune collecte détectée. Vérifie les noms des champs du formulaire."
            }

            schedule
        }
    }
}
