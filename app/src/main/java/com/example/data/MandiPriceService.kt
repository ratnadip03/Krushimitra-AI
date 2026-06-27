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

class MandiPriceService(private val context: Context) {
    private val TAG = "MandiPriceService"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val sharedPrefs = context.getSharedPreferences("mandi_price_cache", Context.MODE_PRIVATE)
    private val connectivityService = ConnectivityService(context)

    fun normalizeCropName(crop: String): String {
        return when (crop.trim().lowercase()) {
            "tomato", "tamatar", "टमाटर", "टोमॅटो" -> "Tomato"
            "onion", "pyaz", "kanda", "कांदा", "प्याज" -> "Onion"
            "potato", "aloo", "batata", "आलू", "बटाटा" -> "Potato"
            "wheat", "gehu", "गेहू", "गहू" -> "Wheat"
            "rice", "paddy", "chawal", "तांदूळ" -> "Rice"
            "cotton", "kapas", "कपास", "कापूस" -> "Cotton"
            "sugarcane", "ganna", "ऊस", "गन्ना" -> "Sugarcane"
            "soybean", "soya", "सोयाबीन" -> "Soybean"
            "mango", "aam", "आंबा", "आम" -> "Mango"
            "banana", "kela", "केळी", "केला" -> "Banana"
            "grapes", "angur", "द्राक्ष", "अंगूर" -> "Grapes"
            "pomegranate", "anar", "डाळिंब" -> "Pomegranate"
            "chilli", "mirchi", "मिरची", "मिर्च" -> "Dry Chillies"
            else -> crop.replaceFirstChar { it.uppercase() }
        }
    }

    suspend fun fetchMandiPrice(crop: String, state: String, district: String = ""): MandiPriceData = withContext(Dispatchers.IO) {
        val isOnline = connectivityService.isOnlineOnce()
        val finalDistrict = if (district.isNotEmpty()) district else {
            try {
                val db = AppDatabase.getDatabase(context)
                db.farmerProfileDao().getFarmerProfileOnce("farmer_123")?.location?.district ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        val data = if (!isOnline) {
            DebugLog.i( "MANDI_OFFLINE: Returning cached mandi price data")
            getCachedMandiPrice(crop)
        } else {
            val apiKey = BuildConfig.MANDI_API_KEY
            val normalizedCrop = normalizeCropName(crop)
            val cleanState = state.trim()
            val cleanDistrict = finalDistrict.trim()

            var parsedData: MandiPriceData? = null
            var responseBody: String? = null

            // First attempt — state-wide only:
            // &filters[state.keyword]=cleanState
            // &filters[commodity]=$normalizedCrop
            // &limit=10
            if (cleanState.isNotEmpty()) {
                val urlStateOnly = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070" +
                        "?api-key=$apiKey" +
                        "&format=json" +
                        "&filters[state.keyword]=${java.net.URLEncoder.encode(cleanState, "UTF-8")}" +
                        "&filters[commodity]=${java.net.URLEncoder.encode(normalizedCrop, "UTF-8")}" +
                        "&limit=10"

                val request = Request.Builder().url(urlStateOnly).build()
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            responseBody = response.body?.string()
                            if (!responseBody.isNullOrEmpty()) {
                                parsedData = parseMandiPriceResponse(responseBody!!, normalizedCrop)
                                if (parsedData != null) {
                                    DebugLog.i( "MANDI_PRICE_FETCHED_STATE_WIDE: crop=${parsedData!!.commodity}, modal=₹${parsedData!!.modalPrice}/Q in $cleanState")
                                }
                            }
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            DebugLog.e( "MANDI_ERROR: code=${response.code} body=$errorBody")
                        }
                    }
                } catch (e: Exception) {
                    DebugLog.e( "Failed to fetch state-wide mandi price: ${e.message}", e)
                }
            }

            // If 0 records → try without state filter too:
            // &filters[commodity]=$normalizedCrop
            // &limit=10
            if (parsedData == null) {
                DebugLog.i( "Mandi price for $normalizedCrop state-wide returned 0 records (or failed/skipped). Trying without state filter...")
                val urlCommodityOnly = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070" +
                        "?api-key=$apiKey" +
                        "&format=json" +
                        "&filters[commodity]=${java.net.URLEncoder.encode(normalizedCrop, "UTF-8")}" +
                        "&limit=10"

                val request = Request.Builder().url(urlCommodityOnly).build()
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            responseBody = response.body?.string()
                            if (!responseBody.isNullOrEmpty()) {
                                parsedData = parseMandiPriceResponse(responseBody!!, normalizedCrop)
                                if (parsedData != null) {
                                    DebugLog.i( "MANDI_PRICE_FETCHED_NATION_WIDE: crop=${parsedData!!.commodity}, modal=₹${parsedData!!.modalPrice}/Q")
                                }
                            }
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            DebugLog.e( "MANDI_ERROR: code=${response.code} body=$errorBody")
                        }
                    }
                } catch (e: Exception) {
                    DebugLog.e( "Failed to fetch nation-wide mandi price: ${e.message}", e)
                }
            }

            // Fallback to Qwen offline inference if still null
            if (parsedData == null) {
                val msg = "No mandi price data available for $normalizedCrop today. Showing AI estimate."
                DebugLog.i(msg)

                parsedData = try {
                    val qwen = QwenInferenceService(context)
                    val profile = try {
                        AppModule.provideFarmerProfileRepository(context).getProfileOnce()
                    } catch (e: Exception) {
                        FarmerProfile(currentCrop = normalizedCrop, location = LocationData(district = cleanDistrict, state = cleanState))
                    }
                    val prompt = """
                        Crop: $normalizedCrop in $cleanDistrict, $cleanState, India.
                        Respond ONLY in JSON: {"min_price":number,"max_price":number,"modal_price":number}
                    """.trimIndent()
                    val qwenResponse = qwen.runFeatureInferenceForFeature(FeatureRoute.MARKET_PRICE, prompt, profile)
                    DebugLog.i("QWEN_MANDI_ESTIMATE_RAW: $qwenResponse")

                    val geminiAdapter = moshi.adapter(Map::class.java)
                    val qwenMap = geminiAdapter.fromJson(qwenResponse) as? Map<String, Any>

                    val minPrice = (qwenMap?.get("min_price") as? Number)?.toDouble() ?: 2000.0
                    val maxPrice = (qwenMap?.get("max_price") as? Number)?.toDouble() ?: 3000.0
                    val modalPrice = (qwenMap?.get("modal_price") as? Number)?.toDouble() ?: 2500.0

                    MandiPriceData(
                        commodity = normalizedCrop,
                        market = if (cleanDistrict.isNotEmpty()) "$cleanDistrict Market (AI)" else "Local Market (AI)",
                        minPrice = minPrice,
                        maxPrice = maxPrice,
                        modalPrice = modalPrice,
                        lastUpdated = "AI Estimate Today",
                        source = msg,
                        latestModalPrice = modalPrice,
                        trend = "STABLE (AI)",
                        nearestMarket = if (cleanDistrict.isNotEmpty()) "$cleanDistrict Market" else "Local Market",
                        records = listOf(qwenResponse)
                    )
                } catch (e: Exception) {
                    DebugLog.e("Failed to get Qwen price estimate: ${e.message}", e)
                    MandiPriceData(
                        commodity = normalizedCrop,
                        market = if (cleanDistrict.isNotEmpty()) "$cleanDistrict Market (AI Fallback)" else "Local Market (AI Fallback)",
                        minPrice = 2200.0,
                        maxPrice = 2800.0,
                        modalPrice = 2500.0,
                        lastUpdated = "AI Fallback Today",
                        source = "Mandi price API error. Qwen estimate unavailable.",
                        latestModalPrice = 2500.0,
                        trend = "STABLE",
                        nearestMarket = if (cleanDistrict.isNotEmpty()) "$cleanDistrict Market" else "Local Market",
                        records = emptyList()
                    )
                }
            }

            val finalData = parsedData!!
            saveToCache(finalData)
            finalData
        }

        val cropName = crop
        DebugLog.i(
            "MANDI_RESPONSE: crop=$cropName " +
            "price=${data.latestModalPrice} " +
            "trend=${data.trend} " +
            "market=${data.nearestMarket} " +
            "records=${data.records.size}")

        return@withContext data
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMandiPriceResponse(json: String, targetCommodity: String): MandiPriceData? {
        return try {
            val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any>
            val records = root?.get("records") as? List<Map<String, Any>>
            
            val record = records?.firstOrNull() 
                ?: return null // No records found

            val commodity = record["commodity"] as? String ?: targetCommodity
            val market = record["market"] as? String ?: "Local Market"
            val minPrice = (record["min_price"] as? String)?.toDoubleOrNull() 
                ?: (record["min_price"] as? Number)?.toDouble() ?: 0.0
            val maxPrice = (record["max_price"] as? String)?.toDoubleOrNull() 
                ?: (record["max_price"] as? Number)?.toDouble() ?: 0.0
            val modalPrice = (record["modal_price"] as? String)?.toDoubleOrNull() 
                ?: (record["modal_price"] as? Number)?.toDouble() ?: 0.0
            val arrivalDate = record["arrival_date"] as? String ?: ""

            MandiPriceData(
                commodity = commodity,
                market = market,
                minPrice = minPrice,
                maxPrice = maxPrice,
                modalPrice = modalPrice,
                lastUpdated = arrivalDate,
                source = "Agmarknet, Govt of India",
                latestModalPrice = modalPrice,
                trend = "STABLE",
                nearestMarket = market,
                records = records?.map { it.toString() } ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing mandi JSON: ${e.message}")
            null
        }
    }

    private fun getCachedMandiPrice(fallbackCommodity: String): MandiPriceData {
        val commodity = sharedPrefs.getString("commodity", fallbackCommodity) ?: fallbackCommodity
        val market = sharedPrefs.getString("market", "Local Market") ?: "Local Market"
        val minPrice = sharedPrefs.getFloat("min_price", 0.0f).toDouble()
        val maxPrice = sharedPrefs.getFloat("max_price", 0.0f).toDouble()
        val modalPrice = sharedPrefs.getFloat("modal_price", 0.0f).toDouble()
        val lastUpdated = sharedPrefs.getString("last_updated", "") ?: ""
        val trend = sharedPrefs.getString("trend", "STABLE") ?: "STABLE"
        val nearestMarket = sharedPrefs.getString("nearest_market", market) ?: market
        return MandiPriceData(
            commodity = commodity,
            market = market,
            minPrice = minPrice,
            maxPrice = maxPrice,
            modalPrice = modalPrice,
            lastUpdated = lastUpdated,
            source = "Cached (Agmarknet)",
            latestModalPrice = modalPrice,
            trend = trend,
            nearestMarket = nearestMarket,
            records = emptyList()
        )
    }

    private fun saveToCache(data: MandiPriceData) {
        sharedPrefs.edit()
            .putString("commodity", data.commodity)
            .putString("market", data.market)
            .putFloat("min_price", data.minPrice.toFloat())
            .putFloat("max_price", data.maxPrice.toFloat())
            .putFloat("modal_price", data.modalPrice.toFloat())
            .putString("last_updated", data.lastUpdated)
            .putString("trend", data.trend)
            .putString("nearest_market", data.nearestMarket)
            .apply()
    }
}
