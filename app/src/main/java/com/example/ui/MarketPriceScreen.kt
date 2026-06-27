package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectivityService
import com.example.data.FarmerProfile
import com.example.data.FarmerProfileRepository
import com.example.data.GeminiService
import androidx.compose.ui.platform.LocalContext
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketPriceScreen(
    profileRepository: FarmerProfileRepository,
    connectivityService: ConnectivityService,
    geminiService: GeminiService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    val isOnline by connectivityService.isOnlineFlow.collectAsState(initial = false)

    var priceInr by remember { mutableStateOf(4200) }
    var trendText by remember { mutableStateOf("🔼 Trending Up (+₹150 from yesterday)") }
    var floorText by remember { mutableStateOf("Do not sell below ₹4,000/Q today. Wait 3 days if buyer resists.") }
    var lastUpdatedText by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        farmerProfile = profileRepository.getProfileOnce()
    }

    LaunchedEffect(isOnline, farmerProfile) {
        val profile = farmerProfile ?: return@LaunchedEffect
        val crop = profile.currentCrop.ifEmpty { "Soybean" }
        val qty = profile.lastHarvest.qtyKg.takeIf { it > 0.0 } ?: 100.0
        val sharedPrefs = context.getSharedPreferences("market_price_cache", android.content.Context.MODE_PRIVATE)
        
        if (isOnline) {
            isFetching = true
            if (com.example.BuildConfig.DEBUG) {
                Log.i("KRISHIMITRA_DEBUG", "F09_MARKETPRICE_CALLED")
            }
            try {
                val state = profile.location.state.ifEmpty { "Maharashtra" }
                val mandi = try {
                    com.example.data.MandiPriceService(context).fetchMandiPrice(crop, state)
                } catch (e: Exception) {
                    profile.cachedMandiPrice
                }

                if (com.example.BuildConfig.DEBUG) {
                    Log.i("KRISHIMITRA_DEBUG", "MANDI_INJECTED_TO_F09")
                }

                val todayStr = java.time.LocalDate.now().toString()
                val prompt = """
                    Current date: $todayStr
                    Crop: $crop
                    Location: ${profile.location.district.ifEmpty { "Nagpur" }}, $state
                    Quantity farmer has: ${qty}kg
                    
                    Agmarknet Mandi Data:
                    - Market: ${mandi.market.ifEmpty { mandi.nearestMarket }.ifEmpty { "Local APMC" }}
                    - Modal Price: ₹${mandi.modalPrice}/Quintal (Latest: ₹${mandi.latestModalPrice}/Quintal)
                    - Min/Max Price: ₹${mandi.minPrice} - ₹${mandi.maxPrice}
                    - Price Trend: ${mandi.trend}
                    - Nearest Market: ${mandi.nearestMarket}
                    - Records Found: ${mandi.records.size}
                    
                    Provide mandi price intelligence using the real Agmarknet Mandi Data above. Respond ONLY in JSON:
                    {
                      "current_price_inr_per_quintal": 4200,
                      "price_trend": "RISING OR FALLING OR STABLE",
                      "best_sell_time": "now OR wait X days",
                      "negotiation_floor_inr": 4000,
                      "market_insight": "2 sentences about current market conditions for this crop",
                      "disclaimer": "Prices are AI estimates based on seasonal trends, verify at local mandi"
                    }
                """.trimIndent()
                
                val responseStr = geminiService.getRawGeminiResponse(prompt)
                if (com.example.BuildConfig.DEBUG) {
                    Log.i("KRISHIMITRA_DEBUG", "GEMINI_RESPONSE: $responseStr")
                }
                
                val now = System.currentTimeMillis()
                val df = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                val timeStr = df.format(java.util.Date(now))
                
                sharedPrefs.edit()
                    .putString("cached_response", responseStr)
                    .putString("cached_time", timeStr)
                    .apply()
                
                val adapter: com.squareup.moshi.JsonAdapter<Map<*, *>> = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                val resMap = adapter.fromJson(responseStr)
                
                val priceVal = (resMap?.get("current_price_inr_per_quintal") as? Number)?.toInt() ?: 4200
                val trend = resMap?.get("price_trend")?.toString() ?: "STABLE"
                val sellTime = resMap?.get("best_sell_time")?.toString() ?: "now"
                val floor = (resMap?.get("negotiation_floor_inr") as? Number)?.toInt() ?: 4000
                val insight = resMap?.get("market_insight")?.toString() ?: ""
                val disclaimer = resMap?.get("disclaimer")?.toString() ?: ""
                
                priceInr = priceVal
                trendText = "Trend: $trend (Best sell time: $sellTime)"
                floorText = "Do not sell below ₹$floor/Q today. Insight: $insight"
                lastUpdatedText = "Live (Updated: $timeStr)\nDisclaimer: $disclaimer"
            } catch (e: Exception) {
                Log.e("MarketPriceScreen", "Failed to fetch live prices", e)
            } finally {
                isFetching = false
            }
        } else {
            val cachedResponse = sharedPrefs.getString("cached_response", "")
            val cachedTime = sharedPrefs.getString("cached_time", "")
            if (cachedResponse != null && cachedResponse.isNotEmpty()) {
                try {
                    val adapter: com.squareup.moshi.JsonAdapter<Map<*, *>> = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                    val resMap = adapter.fromJson(cachedResponse)
                    val priceVal = (resMap?.get("current_price_inr_per_quintal") as? Number)?.toInt() ?: 4200
                    val trend = resMap?.get("price_trend")?.toString() ?: "STABLE"
                    val sellTime = resMap?.get("best_sell_time")?.toString() ?: "now"
                    val floor = (resMap?.get("negotiation_floor_inr") as? Number)?.toInt() ?: 4000
                    val insight = resMap?.get("market_insight")?.toString() ?: ""
                    
                    priceInr = priceVal
                    trendText = "Trend: $trend (Best sell time: $sellTime)"
                    floorText = "Do not sell below ₹$floor/Q today. Insight: $insight"
                    lastUpdatedText = "Last updated: $cachedTime (Connect for live prices)"
                } catch (e: Exception) {
                    lastUpdatedText = "No price cache available. Connect for live prices"
                }
            } else if (profile.cachedMandiPrice.modalPrice != 0.0) {
                priceInr = profile.cachedMandiPrice.modalPrice.toInt()
                trendText = "Agmarknet Price Range: ₹${profile.cachedMandiPrice.minPrice.toInt()} - ₹${profile.cachedMandiPrice.maxPrice.toInt()}"
                floorText = "Market: ${profile.cachedMandiPrice.market}. Source: ${profile.cachedMandiPrice.source}"
                lastUpdatedText = "Arrival Date: ${profile.cachedMandiPrice.lastUpdated}"
            } else {
                lastUpdatedText = "Connect for live prices"
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "बाजार भाव"
        "hi" -> "मंडी भाव"
        else -> "Market Price"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = titleText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B6B2F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFAFAF7)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val crop = currentProfile.currentCrop.ifEmpty { "Soybean" }

            if (!isOnline) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = "Offline", tint = Color(0xFFF57F17))
                        Text("This feature needs internet. Showing last known cached price for $crop.\n$lastUpdatedText", color = Color(0xFFF57F17), fontSize = 14.sp)
                    }
                }
            }

            if (isFetching) {
                CircularProgressIndicator(color = Color(0xFF1B6B2F))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Today's Mandi Price: $crop", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray)
                        Text("₹$priceInr / Quintal", fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = Color(0xFF1B6B2F))
                        
                        Surface(color = Color(0xFFE2F3E7), shape = RoundedCornerShape(6.dp)) {
                            Text(trendText, color = Color(0xFF1B6B2F), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Negotiation Floor Price", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(floorText, fontSize = 14.sp, color = Color(0xFF555F55))
                        if (isOnline) {
                            Text(lastUpdatedText, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
