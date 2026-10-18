package com.example.automkvdubber.service

import com.example.automkvdubber.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GtxTranslationService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    /**
     * Translates a list of SubtitleItems in batches, updating the translatedText property of each item.
     */
    suspend fun translateSubtitles(
        items: List<SubtitleItem>,
        sourceLang: String = "en",
        targetLang: String = "bn",
        onProgress: (current: Int, total: Int, statusText: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext

        val batchSize = 8 // Small, safe batch size to avoid URL length limit and translation line misalignments
        val batches = items.chunked(batchSize)

        for ((batchIndex, batch) in batches.withIndex()) {
            val processedTexts = batch.map { item ->
                cleanSubtitleText(item.originalText)
            }

            // Delimiter to safely separate subtitle lines while preserving line count
            val joinedText = processedTexts.joinToString("\n")

            var translatedJoined = ""
            var attempt = 0
            val maxAttempts = 3

            while (attempt < maxAttempts) {
                try {
                    translatedJoined = translateViaGtxApi(joinedText, sourceLang, targetLang)
                    if (translatedJoined.isNotBlank()) break
                } catch (e: Exception) {
                    attempt++
                    if (attempt < maxAttempts) delay(500L * attempt)
                }
                attempt++
            }

            val translatedLines = if (translatedJoined.isNotBlank()) {
                translatedJoined.split("\n")
            } else {
                emptyList()
            }

            for (i in batch.indices) {
                val translatedLine = translatedLines.getOrNull(i)?.trim()
                if (!translatedLine.isNullOrEmpty()) {
                    batch[i].translatedText = translatedLine
                } else {
                    // Fallback to individual translation if batch splitting didn't match line count
                    val singleTrans = try {
                        translateViaGtxApi(processedTexts[i], sourceLang, targetLang)
                    } catch (e: Exception) {
                        processedTexts[i]
                    }
                    batch[i].translatedText = if (singleTrans.isNotBlank()) singleTrans else batch[i].originalText
                }
            }

            val currentProcessed = ((batchIndex + 1) * batchSize).coerceAtMost(items.size)
            onProgress(currentProcessed, items.size, "অনুবাদ সম্পন্ন: $currentProcessed/${items.size}")
            delay(120L) // Polite delay between API calls
        }
    }

    /**
     * Calls Google Translate GTX endpoint.
     */
    private fun translateViaGtxApi(text: String, sourceLang: String, targetLang: String): String {
        if (text.isBlank()) return ""
        val cleanInput = text.trim()

        val encodedQuery = URLEncoder.encode(cleanInput, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx" +
                "&sl=$sourceLang&tl=$targetLang&hl=en&dt=t&dj=1&source=icon&tk=467103.467103&q=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val bodyStr = response.body?.string() ?: return ""
            val jsonObject = JSONObject(bodyStr)
            val sentencesArray = jsonObject.optJSONArray("sentences") ?: return ""
            val sb = StringBuilder()

            for (i in 0 until sentencesArray.length()) {
                val sentenceObj = sentencesArray.optJSONObject(i)
                if (sentenceObj != null && sentenceObj.has("trans")) {
                    sb.append(sentenceObj.getString("trans"))
                }
            }
            return sb.toString().trim()
        }
    }

    private fun cleanSubtitleText(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "") // Remove HTML tags like <i>, <b>, <font>
            .replace(Regex("\\{[^}]*\\}"), "") // Remove ASS/SSA style overrides like {\an8}
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
