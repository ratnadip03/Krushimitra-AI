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
import com.example.data.FarmerProfile
import com.example.data.FarmerProfileRepository
import com.example.data.QwenInferenceService
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellAdvisorScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onNavigateToFreshness: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    
    var sellWithinDays by remember { mutableStateOf<Int?>(null) }
    var urgency by remember { mutableStateOf("") }
    var recommendedMarket by remember { mutableStateOf("") }
    var priceTip by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        farmerProfile = profileRepository.getProfileOnce()
    }

    LaunchedEffect(farmerProfile) {
        val profile = farmerProfile ?: return@LaunchedEffect
        val alerts = profile.freshnessAlerts
        if (alerts.isNotEmpty()) {
            val latest = alerts.last()
            isLoading = true
            if (com.example.BuildConfig.DEBUG) {
                Log.i("KRISHIMITRA_DEBUG", "F08_SELLADVISOR_CALLED")
            }
            try {
                val district = profile.location.district.ifEmpty { "Nagpur" }
                val state = profile.location.state.ifEmpty { "Maharashtra" }
                val crop = latest.crop.ifEmpty { profile.currentCrop }.ifEmpty { "Tomato" }

                val weather = try {
                    com.example.data.WeatherService(context).fetchWeather(district, state)
                } catch (e: Exception) {
                    profile.cachedWeather
                }

                val mandi = try {
                    com.example.data.MandiPriceService(context).fetchMandiPrice(crop, state)
                } catch (e: Exception) {
                    profile.cachedMandiPrice
                }

                if (com.example.BuildConfig.DEBUG) {
                    Log.i("KRISHIMITRA_DEBUG", "MANDI_INJECTED_TO_F08")
                    Log.i("KRISHIMITRA_DEBUG", "WEATHER_INJECTED_TO_F08")
                }

                val systemPrompt = "You are KrishiMitra AI, an expert sell timing advisor for Indian farmers."
                val query = """
                    Produce: ${latest.crop}
                    Freshness score: ${latest.freshnessScore}/100
                    Shelf life remaining: ${latest.shelfLifeDays} days
                    Quantity: ${profile.lastHarvest.qtyKg.takeIf { it > 0.0 } ?: 100.0}kg
                    Location: $district, $state
                    
                    Current Weather Context:
                    - Temperature: ${weather.tempC}°C
                    - Humidity: ${weather.humidity}%
                    - Rain Chance: ${weather.rainChanceNext3Days}
                    
                    Agmarknet Mandi Pricing Context:
                    - Market: ${mandi.market.ifEmpty { mandi.nearestMarket }.ifEmpty { "Local APMC" }}
                    - Modal Price: ₹${mandi.modalPrice}/Quintal (Latest Modal: ₹${mandi.latestModalPrice}/Quintal)
                    - Price Trend: ${mandi.trend}
                    - Nearest Market: ${mandi.nearestMarket}
                    
                    When should the farmer sell? Respond ONLY in JSON:
                    {
                      "sell_within_days": 3,
                      "urgency": "LOW OR MEDIUM OR HIGH OR CRITICAL",
                      "recommended_market": "nearest mandi suggestion for this district",
                      "price_tip": "one sentence about price strategy",
                      "reason": "one sentence explaining timing"
                    }
                """.trimIndent()
                
                val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                if (com.example.BuildConfig.DEBUG) {
                    Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: sell timing recommended")
                }
                
                val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                val resMap = adapter.fromJson(responseStr)
                
                sellWithinDays = (resMap?.get("sell_within_days") as? Number)?.toInt() ?: latest.shelfLifeDays
                urgency = resMap?.get("urgency")?.toString() ?: "MEDIUM"
                recommendedMarket = resMap?.get("recommended_market")?.toString() ?: ""
                priceTip = resMap?.get("price_tip")?.toString() ?: ""
                reason = resMap?.get("reason")?.toString() ?: ""
            } catch (e: Exception) {
                Log.e("SellAdvisorScreen", "Failed to get sell advice", e)
                
                val district = profile.location.district.ifEmpty { "Nagpur" }
                val state = profile.location.state.ifEmpty { "Maharashtra" }
                val crop = latest.crop.ifEmpty { profile.currentCrop }.ifEmpty { "Tomato" }

                val weather = try {
                    com.example.data.WeatherService(context).fetchWeather(district, state)
                } catch (err: Exception) {
                    profile.cachedWeather
                }
                
                val rainChanceVal = weather.rainChanceNext3Days.replace("%", "").trim().toIntOrNull() ?: 0
                val adjustedDays = if (weather.tempC > 32.0 || rainChanceVal > 60) {
                    (latest.shelfLifeDays - 2).coerceAtLeast(1)
                } else {
                    latest.shelfLifeDays
                }
                
                sellWithinDays = adjustedDays
                urgency = if (adjustedDays <= 2) "HIGH" else "MEDIUM"
                recommendedMarket = "Local Mandi"
                priceTip = "Monitor prices daily, sell early due to weather conditions."
                reason = "Based on freshness level, high temperature/rain risk."
            } finally {
                isLoading = false
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "विक्रीची योग्य वेळ"
        "hi" -> "बेचने का सही समय"
        else -> "Best Time to Sell"
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
            val alerts = currentProfile.freshnessAlerts
            if (alerts.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFD32F2F), modifier = Modifier.size(36.dp))
                        Text(
                            text = if (lang == "mr") "प्रथम ताजेपणा तपासा" else "Please run Freshness Check first based on crop visual data.",
                            color = Color(0xFFD32F2F)
                        )
                        Button(
                            onClick = onNavigateToFreshness,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Go to Freshness Check")
                        }
                    }
                }
            } else {
                val latest = alerts.last()
                val daysToSell = latest.shelfLifeDays
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color(0xFF1B6B2F))
                        } else {
                            val days = sellWithinDays ?: daysToSell
                            Surface(
                                color = if (days < 3) Color(0xFFFFEBEE) else Color(0xFFE2F3E7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Sell in $days Days (${urgency.ifEmpty { "MEDIUM" }})",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    modifier = Modifier.padding(16.dp),
                                    color = if (days < 3) Color(0xFFD32F2F) else Color(0xFF1B6B2F)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Based on freshness score of ${latest.freshnessScore}% from your recent check. Delaying sale beyond $days days will result in spoilage risks." +
                                       (if (reason.isNotEmpty()) "\n\n• Reason: $reason" else "") +
                                       (if (recommendedMarket.isNotEmpty()) "\n• Market: $recommendedMarket" else "") +
                                       (if (priceTip.isNotEmpty()) "\n• Price Tip: $priceTip" else ""),
                                fontSize = 14.sp,
                                color = Color(0xFF555F55)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
