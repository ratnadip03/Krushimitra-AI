package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiUnavailableException(message: String) : Exception(message)

class GeminiService(
    private val context: android.content.Context? = null,
    private val qwenFallback: QwenInferenceService? = null
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    // OkHttp Client matching recommended 60-second timeouts from gemini-api skill
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val TAG = "GeminiService"

    /**
     * Executes a raw cloud Gemini API call returning direct JSON response.
     */
    suspend fun getRawGeminiResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (qwenFallback != null && context != null) {
            val profile = try {
                AppModule.provideFarmerProfileRepository(context).getProfileOnce()
            } catch (e: Exception) {
                FarmerProfile()
            }
            return@withContext qwenFallback.runFeatureInferenceForFeature(FeatureRoute.MARKET_PRICE, prompt, profile)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw GeminiUnavailableException("Gemini API Key is invalid or empty in settings secrets.")
        }

        val requestJson = """
            {
              "contents": [{
                "parts": [{
                  "text": ${escapeJsonString(prompt)}
                }]
              }],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val requestBody = requestJson.toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw GeminiUnavailableException("Gemini API server returned error (${response.code}): $errBody")
                }

                val responseBody = response.body?.string() ?: throw GeminiUnavailableException("Empty response from Gemini server")
                
                val root = moshi.adapter(Map::class.java).fromJson(responseBody) as? Map<*, *>
                val candidates = root?.get("candidates") as? List<*>
                val candidate = candidates?.firstOrNull() as? Map<*, *>
                val content = candidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val part = parts?.firstOrNull() as? Map<*, *>
                part?.get("text") as? String ?: throw Exception("Text segment absent")
            }
        } catch (e: IOException) {
            throw GeminiUnavailableException("Gemini network request timed out or server is offline: ${e.message}")
        }
    }

    /**
     * Executes the cloud Gemini API call.
     * Uses gemini-3.5-flash as the standard basic text reasoning model.
     * Throws GeminiUnavailableException if the call times out or encounters network errors.
     */
    suspend fun getVoiceResponse(query: String, profile: FarmerProfile, conversationHistory: List<Pair<String,String>> = emptyList()): VoiceResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw GeminiUnavailableException("Gemini API Key is invalid or empty in settings secrets.")
        }

        val prompt = buildSystemPrompt(profile, query, conversationHistory)

        // Construct Gemini direct REST request payload matching schema
        val requestJson = """
            {
              "contents": [{
                "parts": [{
                  "text": ${escapeJsonString(prompt)}
                }]
              }],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val requestBody = requestJson.toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw GeminiUnavailableException("Gemini API server returned error (${response.code}): $errBody")
                }

                val responseBody = response.body?.string() ?: throw GeminiUnavailableException("Empty response from Gemini server")
                parseGeminiResponse(responseBody)
            }
        } catch (e: IOException) {
            throw GeminiUnavailableException("Gemini network request timed out or server is offline: ${e.message}")
        }
    }

    private fun buildSystemPrompt(profile: FarmerProfile, query: String, conversationHistory: List<Pair<String,String>>): String {
        val historyString = conversationHistory.takeLast(3).joinToString("\n            ") { "Farmer: ${it.first}\nAssistant: ${it.second}" }
        val body = """
            ${QwenInferenceService.VOICE_SHORT_RESPONSE_PREFIX}
            You are KrishiMitra AI, a farming assistant for Indian farmers.
            Always respond in this exact JSON format:
            {
              "reply": "your answer here",
              "feature_route": "one of the route codes below"
            }

            Feature route codes:
            SOIL_PASSPORT, PRE_CROP, CROP_ROTATION,
            FRESHNESS, STORAGE_GUIDE, HARVEST_TIMING,
            COLD_CHAIN, SELL_ADVISOR, MARKET_PRICE,
            POSTHARVEST_LOSS, SURPLUS, VALUE_ADDITION,
            WASTE_ENGINE, CARBON_TRACKER, GOVT_SCHEMES,
            GENERAL

            Use GENERAL if no specific feature is needed.

            ${QwenInferenceService.buildFarmerContextBlock(profile)}
            - Last Harvest: ${profile.lastHarvest.crop}, ${profile.lastHarvest.qtyKg}kg
            - Eligible Schemes: ${profile.eligibleSchemes.joinToString()}
            - Carbon Footprint: ${profile.carbonFootprintKg}kg CO2

            Recent Conversation History:
            $historyString

            Farmer says: $query
        """.trimIndent()
        return QwenInferenceService.languageLock(AppLanguageManager.currentLanguage) + "\n" + body
    }

    private fun parseGeminiResponse(jsonString: String): VoiceResponse {
        return try {
            // Traverse the Gemini response payload JSON to find 'text'
            // Structure: response -> candidates -> content -> parts -> text
            val root = moshi.adapter(Map::class.java).fromJson(jsonString) as? Map<*, *>
            val candidates = root?.get("candidates") as? List<*>
            val candidate = candidates?.firstOrNull() as? Map<*, *>
            val content = candidate?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val part = parts?.firstOrNull() as? Map<*, *>
            val innerText = part?.get("text") as? String ?: throw Exception("Text segment absent")

            // Parse the inner text itself, which contains our custom JSON
            val innerMap = moshi.adapter(Map::class.java).fromJson(innerText) as? Map<*, *>
            val replyText = innerMap?.get("reply") as? String ?: ""
            val routeString = innerMap?.get("feature_route") as? String ?: "GENERAL"

            val route = try {
                FeatureRoute.valueOf(routeString)
            } catch (e: IllegalArgumentException) {
                FeatureRoute.GENERAL
            }

            VoiceResponse(replyText, route)
        } catch (e: Exception) {
            // Fallback parsing or general message
            Log.e(TAG, "Failed parsing Gemini structured JSON, falling back.", e)
            VoiceResponse("Unable to parse. Here is what I got: $jsonString", FeatureRoute.GENERAL)
        }
    }

    private fun escapeJsonString(input: String): String {
        val escaped = input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // ════════════════════════════════════════
    // GEMINI VISION — Freshness & Maturity
    // ════════════════════════════════════════

    /**
     * Analyzes produce freshness from a Bitmap via Gemini Vision API.
     */
    suspend fun analyzeFreshnessFromImage(
        context: android.content.Context,
        bitmap: android.graphics.Bitmap
    ): VisionAnalysisResult {
        val visionService = GeminiVisionService(context)
        return visionService.classifyFreshness(bitmap)
    }

    /**
     * Analyzes crop maturity/harvest timing from a Bitmap via Gemini Vision API.
     */
    suspend fun analyzeCropMaturity(
        context: android.content.Context,
        bitmap: android.graphics.Bitmap
    ): VisionAnalysisResult {
        val visionService = GeminiVisionService(context)
        return visionService.classifyMaturity(bitmap)
    }
}

