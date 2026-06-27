package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostHarvestLossScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var packaging by remember { mutableStateOf("Sack") }
    var distance by remember { mutableStateOf(50f) }
    var resultCalculated by remember { mutableStateOf(false) }
    var lossPercent by remember { mutableStateOf(0.0) }
    var tips by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingTips by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        farmerProfile = profileRepository.getProfileOnce()
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "काढणीपश्यात नुकसान प्रतिबंध"
        "hi" -> "कटाई पश्चात नुकसान बचाव"
        else -> "Post-Harvest Loss"
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
            Text("Packaging Method", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sack", "Crate", "Loose").forEach { method ->
                    FilterChip(
                        selected = packaging == method,
                        onClick = { packaging = method },
                        label = { Text(method) }
                    )
                }
            }

            Text("Distance to Market (km): ${distance.toInt()}", fontWeight = FontWeight.Bold)
            Slider(
                value = distance,
                onValueChange = { distance = it },
                valueRange = 5f..200f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF1B6B2F), activeTrackColor = Color(0xFF1B6B2F))
            )

            Button(
                onClick = {
                    if (com.example.BuildConfig.DEBUG) {
                        Log.i("KRISHIMITRA_DEBUG", "F10_POSTHARVEST_CALLED")
                    }
                    val crop = currentProfile.lastHarvest.crop.ifEmpty { "Tomato" }
                    val qty = currentProfile.lastHarvest.qtyKg.coerceAtLeast(100.0)
                    val baseLossRate = mapOf(
                        "Tomato" to 0.18f, "Onion" to 0.08f,
                        "Potato" to 0.10f, "Mango" to 0.20f,
                        "Banana" to 0.22f, "Apple" to 0.12f,
                        "Wheat" to 0.04f, "Rice" to 0.03f,
                        "Cotton" to 0.02f, "Sugarcane" to 0.05f
                    )
                    val selectedPackaging = packaging.lowercase()
                    val packagingFactor = when(selectedPackaging) {
                        "loose" -> 1.4f
                        "sack" -> 1.0f
                        "crate" -> 0.7f
                        else -> 1.0f
                    }

                    coroutineScope.launch {
                        // Fetch weather for loss adjustment
                        val district = currentProfile.location.district.ifEmpty { "Nagpur" }
                        val state = currentProfile.location.state.ifEmpty { "Maharashtra" }

                        val weather = try {
                            com.example.data.WeatherService(context).fetchWeather(district, state)
                        } catch (e: Exception) {
                            currentProfile.cachedWeather
                        }

                        if (com.example.BuildConfig.DEBUG) {
                            Log.i("KRISHIMITRA_DEBUG", "WEATHER_INJECTED_TO_F10")
                        }

                        // Adjust distanceFactor based on weather
                        val rainChanceVal = weather.rainChanceNext3Days.replace("%", "").trim().toIntOrNull() ?: 0
                        val weatherMultiplier = when {
                            weather.tempC > 38.0 && rainChanceVal > 50 -> 1.4f  // extreme heat + rain
                            weather.tempC > 35.0 -> 1.25f                        // high heat
                            rainChanceVal > 60 -> 1.2f                           // high rain chance
                            weather.humidity > 85.0 -> 1.15f                     // high humidity
                            else -> 1.0f
                        }

                        val distanceFactor = (1f + (distance / 500f)) * weatherMultiplier
                        val predictedLossPct = (baseLossRate[crop] ?: 0.12f) * packagingFactor * distanceFactor * 100f
                        lossPercent = (predictedLossPct / 100f).toDouble()
                        resultCalculated = true

                        isLoadingTips = true
                        try {
                            val systemPrompt = "You are KrishiMitra AI, an expert post-harvest advisor."
                            val query = """Crop: $crop, predicted loss: ${predictedLossPct}%, packaging: $selectedPackaging, distance: ${distance.toInt()}km.
                                |Current Weather:
                                |- Temperature: ${weather.tempC}°C, Humidity: ${weather.humidity}%
                                |- Rain chance: ${weather.rainChanceNext3Days}, Condition: ${weather.conditionText}
                                |Give 3 specific tips to reduce post-harvest losses considering these weather conditions. Respond in JSON: {'tips': ['tip1','tip2','tip3']}""".trimMargin()
                            
                            val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                            if (com.example.BuildConfig.DEBUG) {
                                Log.i("KRISHIMITRA_DEBUG", "F10_TIPS_DONE")
                            }
                            
                            val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                            val resMap = adapter.fromJson(responseStr)
                            val rawTipsList = resMap?.get("tips") as? List<*>
                            tips = rawTipsList?.map { it.toString() } ?: emptyList()
                        } catch (e: Exception) {
                            Log.e("PostHarvestLossScreen", "Failed to get tips from Qwen", e)
                            val rainTip = if (rainChanceVal > 50) "Cover produce with waterproof tarpaulin during transport — rain expected." else "Maintain ventilation in storage vehicles."
                            val heatTip = if (weather.tempC > 35.0) "Transport during early morning or late evening to avoid heat damage." else "Transport during evening cool hours to reduce moisture loss."
                            tips = listOf(
                                "Switch to plastic crates to avoid crushing layer damage.",
                                heatTip,
                                rainTip
                            )
                        } finally {
                            isLoadingTips = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B2F))
            ) {
                Text("Calculate Loss Risk", fontWeight = FontWeight.Bold)
            }

            if (resultCalculated) {
                val crop = currentProfile.lastHarvest.crop.ifEmpty { "Tomato" }
                val qty = currentProfile.lastHarvest.qtyKg.coerceAtLeast(100.0)
                val lossKg = qty * lossPercent
                val revLoss = lossKg * 30.0 // assume 30 rs per kg

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Predicted Loss: ${(lossPercent * 100).toInt()}%", fontSize = 20.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        Text("Volume Loss: ${lossKg.toInt()} kg out of $qty kg", fontSize = 14.sp)
                        Text("Revenue Loss: ~₹${revLoss.toInt()}", fontSize = 14.sp)
                        Divider()
                        Text("Tips to Intervene:", fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))
                        if (isLoadingTips) {
                            CircularProgressIndicator(color = Color(0xFF1B6B2F), modifier = Modifier.size(24.dp))
                        } else {
                            tips.forEach { tip ->
                                Text("• $tip", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
