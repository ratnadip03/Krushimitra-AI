package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class WeatherService(private val context: Context) {
    private val TAG = "WeatherService"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val sharedPrefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val connectivityService = ConnectivityService(context)

    suspend fun fetchWeather(district: String, state: String): WeatherData = withContext(Dispatchers.IO) {
        val query = "$district,$state,India"
        val isOnline = connectivityService.isOnlineOnce()
        
        val data = if (!isOnline) {
            DebugLog.i( "WEATHER_OFFLINE: Returning cached weather data")
            getCachedWeather()
        } else {
            val apiKey = BuildConfig.WEATHER_API_KEY
            val url = "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=${android.net.Uri.encode(query)}"
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        DebugLog.e( "WEATHER_ERROR: code=${response.code} body=$errorBody")
                        getCachedWeather()
                    } else {
                        val responseBody = response.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            getCachedWeather()
                        } else {
                            val parsedData = parseWeatherResponse(responseBody)
                            if (parsedData != null) {
                                saveToCache(parsedData)
                                DebugLog.i( "WEATHER_FETCHED: temp=${parsedData.tempC}C, humidity=${parsedData.humidity}%")
                                parsedData
                            } else {
                                getCachedWeather()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch weather: ${e.message}", e)
                getCachedWeather()
            }
        }

        DebugLog.i(
            "WEATHER_RESPONSE: temp=${data.tempC} " +
            "condition=${data.conditionText} " +
            "rain3d=${data.rainChanceNext3Days}")

        return@withContext data
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseWeatherResponse(json: String): WeatherData? {
        return try {
            val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any>
            val current = root?.get("current") as? Map<String, Any>
            val conditionMap = current?.get("condition") as? Map<String, Any>

            val tempC = (current?.get("temp_c") as? Number)?.toDouble() ?: 0.0
            val humidity = (current?.get("humidity") as? Number)?.toDouble() ?: 0.0
            val feelsLikeC = (current?.get("feelslike_c") as? Number)?.toDouble() ?: 0.0
            val windKph = (current?.get("wind_kph") as? Number)?.toDouble() ?: 0.0
            val condition = conditionMap?.get("text") as? String ?: ""
            val lastUpdated = current?.get("last_updated") as? String ?: ""

            WeatherData(
                tempC = tempC,
                humidity = humidity,
                condition = condition,
                feelsLikeC = feelsLikeC,
                windKph = windKph,
                lastUpdated = lastUpdated,
                source = "WeatherAPI.com",
                conditionText = condition,
                rainChanceNext3Days = "0%"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing weather JSON: ${e.message}")
            null
        }
    }

    private fun getCachedWeather(): WeatherData {
        val tempC = sharedPrefs.getFloat("temp_c", 0.0f).toDouble()
        val humidity = sharedPrefs.getFloat("humidity", 0.0f).toDouble()
        val condition = sharedPrefs.getString("condition", "") ?: ""
        val feelsLikeC = sharedPrefs.getFloat("feelslike_c", 0.0f).toDouble()
        val windKph = sharedPrefs.getFloat("wind_kph", 0.0f).toDouble()
        val lastUpdated = sharedPrefs.getString("last_updated", "") ?: ""
        val conditionText = sharedPrefs.getString("condition_text", condition) ?: condition
        val rain3d = sharedPrefs.getString("rain_3d", "0%") ?: "0%"
        return WeatherData(
            tempC = tempC,
            humidity = humidity,
            condition = condition,
            feelsLikeC = feelsLikeC,
            windKph = windKph,
            lastUpdated = lastUpdated,
            source = "Cached (WeatherAPI.com)",
            conditionText = conditionText,
            rainChanceNext3Days = rain3d
        )
    }

    private fun saveToCache(data: WeatherData) {
        sharedPrefs.edit()
            .putFloat("temp_c", data.tempC.toFloat())
            .putFloat("humidity", data.humidity.toFloat())
            .putString("condition", data.condition)
            .putFloat("feelslike_c", data.feelsLikeC.toFloat())
            .putFloat("wind_kph", data.windKph.toFloat())
            .putString("last_updated", data.lastUpdated)
            .putString("condition_text", data.conditionText)
            .putString("rain_3d", data.rainChanceNext3Days)
            .apply()
    }
}
