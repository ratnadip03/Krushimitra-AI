package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gemini Vision API for freshness (F04) and harvest timing (F06) analysis.
 */
data class VisionAnalysisResult(
    val label: String,
    val confidence: Float,
    val score: Int,
    val produceType: String = "",
    val shelfLifeDays: Int = 0,
    val action: String = "",
    val advice: String = "",
    val storageTip: String = "",
    val marketTip: String = "",
    val harvestDays: Int = 0,
    val recommendation: String = "",
    val weatherNote: String = ""
)

/** @deprecated Use [VisionAnalysisResult] — kept for existing screen bindings. */
typealias TFLiteResult = VisionAnalysisResult

class GeminiVisionService(private val context: Context) {

    private val TAG = "GeminiVisionService"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val connectivityService = ConnectivityService(context)
    private val weatherService = WeatherService(context)

    private val freshnessPrompt = """You are an agricultural AI assistant analyzing produce freshness for an Indian farmer.

Analyze this fruit or vegetable photo and respond ONLY in this exact JSON format, nothing else:
{
  "produce_type": "name of the fruit or vegetable",
  "freshness_score": number between 0 and 100,
  "freshness_category": "one of: fresh_high OR fresh_medium OR near_expiry OR spoiled",
  "shelf_life_days": estimated days remaining,
  "action": "one of: SELL OR STORE OR PROCESS",
  "confidence": number between 0.0 and 1.0,
  "advice": "2-3 practical sentences for the farmer about what to do right now",
  "storage_tip": "one specific storage tip to extend shelf life",
  "market_tip": "one sentence about best time or place to sell this produce"
}

Scoring guide:
fresh_high = score 80-100, shelf_life 5-10 days
fresh_medium = score 50-79, shelf_life 2-5 days
near_expiry = score 20-49, shelf_life 0-2 days
spoiled = score 0-19, shelf_life 0 days

Use Indian farming context. If the image is not a fruit or vegetable, return freshness_score 0 and produce_type "Unknown - please upload a produce photo"."""

    suspend fun classifyFreshness(bitmap: Bitmap): VisionAnalysisResult = withContext(Dispatchers.IO) {
        DebugLog.i("FRESHNESS_ANALYZE_CALLED")
        val isOnline = connectivityService.isOnlineOnce()
        DebugLog.i("FRESHNESS_ONLINE: $isOnline")
        if (!isOnline) {
            Log.i(TAG, "Offline — returning OFFLINE_MODE for freshness")
            return@withContext VisionAnalysisResult(
                label = "OFFLINE_MODE",
                confidence = 0.0f,
                score = -1,
                advice = "You are offline. Please select freshness level manually."
            )
        }

        try {
            Log.i(TAG, "Sending image to Gemini Vision API for freshness analysis...")
            val base64Image = bitmapToBase64(bitmap)
            val responseText = callGeminiVision(base64Image, freshnessPrompt)
            DebugLog.i("GEMINI_RESPONSE: ${responseText.take(200)}")
            parseFreshnessResponse(responseText)
        } catch (e: Exception) {
            DebugLog.e("FRESHNESS_PARSE_FAILED: $e")
            Log.e(TAG, "Gemini freshness analysis failed", e)
            VisionAnalysisResult(
                label = "fresh_medium",
                confidence = 0.5f,
                score = 50,
                produceType = "Unknown",
                shelfLifeDays = 3,
                action = "SELL",
                advice = "Analysis failed. Please try again with a clearer photo.",
                storageTip = "",
                marketTip = ""
            )
        }
    }

    private val maturityPrompt = """You are an agricultural AI for Indian farmers. Analyze this crop field photo and respond ONLY in this JSON format:
{
  "crop_type": "identified crop name",
  "maturity_stage": "one of: unripe OR nearly_ripe OR ready_to_harvest OR overripe",
  "harvest_days": estimated days until optimal harvest (0 if ready now),
  "confidence": 0.0 to 1.0,
  "maturity_percentage": 0 to 100,
  "recommendation": "2-3 sentences advising the farmer on exact harvest timing",
  "weather_note": "one sentence - if weather data unavailable say to check local forecast before harvesting"
}

Maturity guide:
unripe = harvest in 7-14 days
nearly_ripe = harvest in 3-7 days
ready_to_harvest = harvest within 1-3 days
overripe = harvest immediately, quality declining"""

    suspend fun classifyMaturity(bitmap: Bitmap): VisionAnalysisResult = withContext(Dispatchers.IO) {
        DebugLog.i("F06_HARVEST_CALLED")
        if (!connectivityService.isOnlineOnce()) {
            Log.i(TAG, "Offline — returning OFFLINE_MODE for maturity")
            return@withContext VisionAnalysisResult(
                label = "OFFLINE_MODE",
                confidence = 0.0f,
                score = -1,
                recommendation = "You are offline. Please select maturity level manually."
            )
        }

        val profile = try {
            AppModule.provideFarmerProfileRepository(context).getProfileOnce()
        } catch (e: Exception) { null }

        val weather = try {
            val district = profile?.location?.district?.ifEmpty { "Nagpur" } ?: "Nagpur"
            val state = profile?.location?.state?.ifEmpty { "Maharashtra" } ?: "Maharashtra"
            weatherService.fetchWeather(district, state)
        } catch (e: Exception) {
            profile?.cachedWeather ?: WeatherData()
        }

        DebugLog.i("WEATHER_INJECTED_TO_F06")

        val weatherAppendix = "\n\nCurrent Weather Context for harvest timing decision:\n" +
            "- Temperature: ${weather.tempC}°C (Feels like: ${weather.feelsLikeC}°C)\n" +
            "- Humidity: ${weather.humidity}%\n" +
            "- Condition: ${weather.conditionText}\n" +
            "- Wind: ${weather.windKph} kph\n" +
            "- Rain chance next 3 days: ${weather.rainChanceNext3Days}\n" +
            "Use this weather data to refine your harvest timing recommendation and weather_note."

        val enrichedPrompt = maturityPrompt + weatherAppendix

        try {
            Log.i(TAG, "Sending image to Gemini Vision API for maturity analysis...")
            val base64Image = bitmapToBase64(bitmap)
            val responseText = callGeminiVision(base64Image, enrichedPrompt)
            DebugLog.i("GEMINI_RESPONSE: ${responseText.take(200)}")
            parseMaturityResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini maturity analysis failed", e)
            val rainVal = weather.rainChanceNext3Days.replace("%", "").trim().toIntOrNull() ?: 0
            val weatherNote = if (rainVal > 50) {
                "Rain expected (${weather.rainChanceNext3Days}). Harvest before rain and dry produce immediately."
            } else if (weather.tempC > 35.0) {
                "High temperature (${weather.tempC}°C). Harvest during early morning or late evening."
            } else {
                "Weather is moderate. Check local forecast before harvesting."
            }
            VisionAnalysisResult(
                label = "nearly_ripe",
                confidence = 0.5f,
                score = 75,
                harvestDays = 5,
                recommendation = "Analysis failed. Please try again with a clearer photo.",
                weatherNote = weatherNote
            )
        }
    }

    private fun callGeminiVision(base64Image: String, prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IOException("Gemini API Key is not configured. Add it to the .env file.")
        }

        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val requestJson = """
            {
              "contents": [{
                "parts": [
                  {
                    "inline_data": {
                      "mime_type": "image/jpeg",
                      "data": "$base64Image"
                    }
                  },
                  {
                    "text": "$escapedPrompt"
                  }
                ]
              }],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val requestBody = requestJson.toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Gemini Vision API error (${response.code}): $errBody")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from Gemini Vision API")

            return extractTextFromGeminiResponse(responseBody)
        }
    }

    private fun extractTextFromGeminiResponse(jsonString: String): String {
        val root = moshi.adapter(Map::class.java).fromJson(jsonString) as? Map<*, *>
        val candidates = root?.get("candidates") as? List<*>
        val candidate = candidates?.firstOrNull() as? Map<*, *>
        val content = candidate?.get("content") as? Map<*, *>
        val parts = content?.get("parts") as? List<*>
        val part = parts?.firstOrNull() as? Map<*, *>
        return part?.get("text") as? String
            ?: throw IOException("Could not extract text from Gemini response: $jsonString")
    }

    private fun extractJsonFromOutput(output: String): String {
        val firstBrace = output.indexOf('{')
        val lastBrace = output.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return output.substring(firstBrace, lastBrace + 1)
        }
        return output.trim()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFreshnessResponse(responseText: String): VisionAnalysisResult {
        val jsonStr = extractJsonFromOutput(responseText)
        val map = moshi.adapter(Map::class.java).fromJson(jsonStr) as? Map<String, Any>
            ?: return defaultFreshnessResult()

        val produceType = map["produce_type"] as? String ?: "Unknown"
        val freshnessScore = (map["freshness_score"] as? Number)?.toInt() ?: 50

        if (produceType.contains("Unknown", ignoreCase = true) || freshnessScore == 0) {
            return VisionAnalysisResult(
                label = "NOT_A_PRODUCE",
                confidence = 0.0f,
                score = 0,
                produceType = "Unknown",
                advice = "This does not appear to be a fruit or vegetable. Please take a clear photo of your produce."
            )
        }

        val freshnessCategory = map["freshness_category"] as? String ?: "fresh_medium"
        val shelfLifeDays = (map["shelf_life_days"] as? Number)?.toInt() ?: 3
        val action = map["action"] as? String ?: "SELL"
        val confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.5f
        val advice = map["advice"] as? String ?: ""
        val storageTip = map["storage_tip"] as? String ?: ""
        val marketTip = map["market_tip"] as? String ?: ""

        return VisionAnalysisResult(
            label = freshnessCategory,
            confidence = confidence,
            score = freshnessScore,
            produceType = produceType,
            shelfLifeDays = shelfLifeDays,
            action = action,
            advice = advice,
            storageTip = storageTip,
            marketTip = marketTip
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMaturityResponse(responseText: String): VisionAnalysisResult {
        val jsonStr = extractJsonFromOutput(responseText)
        val map = moshi.adapter(Map::class.java).fromJson(jsonStr) as? Map<String, Any>
            ?: return defaultMaturityResult()

        val cropType = map["crop_type"] as? String ?: "Unknown"
        val maturityStage = map["maturity_stage"] as? String ?: "nearly_ripe"
        val harvestDays = (map["harvest_days"] as? Number)?.toInt() ?: 5
        val confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.5f
        val maturityPct = (map["maturity_percentage"] as? Number)?.toInt() ?: 50
        val recommendation = map["recommendation"] as? String ?: ""
        val weatherNote = map["weather_note"] as? String ?: "Check local forecast before harvesting."

        return VisionAnalysisResult(
            label = maturityStage,
            confidence = confidence,
            score = maturityPct,
            produceType = cropType,
            harvestDays = harvestDays,
            recommendation = recommendation,
            weatherNote = weatherNote
        )
    }

    private fun defaultFreshnessResult() = VisionAnalysisResult(
        label = "fresh_medium",
        confidence = 0.5f,
        score = 50,
        produceType = "Unknown",
        shelfLifeDays = 3,
        action = "SELL",
        advice = "Please try with a clearer photo.",
        storageTip = "",
        marketTip = ""
    )

    private fun defaultMaturityResult() = VisionAnalysisResult(
        label = "nearly_ripe",
        confidence = 0.5f,
        score = 50,
        harvestDays = 5,
        recommendation = "Please try with a clearer photo.",
        weatherNote = "Check local forecast before harvesting."
    )

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}

/** @deprecated Use [GeminiVisionService] — kept for existing screen bindings. */
typealias TFLiteVisionService = GeminiVisionService
