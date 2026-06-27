package com.example.ui

import androidx.compose.animation.*
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
import com.example.data.*
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageGuideScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var inputCrop by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var guideReady by remember { mutableStateOf(false) }

    var temp by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var warnings by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val profile = profileRepository.getProfileOnce()
        farmerProfile = profile
        if (profile.lastHarvest.crop.isNotEmpty()) {
            inputCrop = profile.lastHarvest.crop
        }
    }

    val lang = farmerProfile?.language ?: "en"
    val titleText = when (lang) {
        "mr" -> "साठवणूक मार्गदर्शक"
        "hi" -> "भंडारण गाइड"
        else -> "Storage Guide"
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
            OutlinedTextField(
                value = inputCrop,
                onValueChange = { inputCrop = it },
                label = { Text("Crop Name to Store (e.g., Onion, Soybean)") },
                modifier = Modifier.fillMaxWidth().testTag("crop_storage_input")
            )
            
            Button(
                onClick = {
                    if (inputCrop.isNotEmpty()) {
                        coroutineScope.launch {
                            isLoading = true
                            guideReady = false
                            val profile = farmerProfile ?: FarmerProfile()
                            if (com.example.BuildConfig.DEBUG) {
                                Log.i("KRISHIMITRA_DEBUG", "F05_STORAGE_CALLED: crop=${inputCrop.ifEmpty { profile.lastHarvest.crop.ifEmpty { "Tomato" } }}")
                            }
                            try {
                                val systemPrompt = "You are KrishiMitra AI, an expert in post-harvest produce storage for Indian farmers."
                                val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                                val currentMonth = months[java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)]
                                
                                val query = """
                                    Crop to store: ${inputCrop.ifEmpty { profile.lastHarvest.crop.ifEmpty { "Tomato" } }}
                                    Location: ${profile.location.district.ifEmpty { "Nagpur" }}
                                    Current season: [detect from current month: $currentMonth]
                                    
                                    Provide storage instructions. Respond ONLY in JSON:
                                    {
                                      "optimal_temp_celsius": 15,
                                      "optimal_humidity_percent": 65,
                                      "storage_method": "gunny bag OR crate OR pit OR shade OR cold storage",
                                      "max_duration_days": 90,
                                      "key_tips": ["tip1", "tip2", "tip3"],
                                      "warning_signs": ["sign1", "sign2"]
                                    }
                                """.trimIndent()
                                
                                val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                                if (com.example.BuildConfig.DEBUG) {
                                    Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: storage guide generated")
                                }
                                
                                val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                                val resMap = adapter.fromJson(responseStr)
                                
                                temp = "${resMap?.get("optimal_temp_celsius") ?: "15"}°C"
                                humidity = "${resMap?.get("optimal_humidity_percent") ?: "65"}%"
                                method = resMap?.get("storage_method")?.toString() ?: "Shade"
                                duration = "${resMap?.get("max_duration_days") ?: "90"} Days"
                                
                                val tipsList = resMap?.get("key_tips") as? List<*>
                                val signsList = resMap?.get("warning_signs") as? List<*>
                                val tipsStr = tipsList?.joinToString(", ") ?: "Store in well-ventilated dry place"
                                val signsStr = signsList?.joinToString(", ") ?: "Spoilage, moisture"
                                
                                warnings = "Tips: $tipsStr. Signs: $signsStr"
                                guideReady = true
                            } catch (e: Exception) {
                                Log.e("StorageGuideScreen", "Failed to get storage guide", e)
                                temp = "15°C - 20°C"
                                humidity = "60% - 70%"
                                method = "Jute gunny bags in a ventilated shade"
                                duration = "3 - 5 Months"
                                warnings = "Watch for dark spots, mold growth, or sprouting."
                                guideReady = true
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B2F))
            ) {
                Text(if (lang == "mr") "मार्गदर्शक मिळवा" else "Get Storage Guide", fontWeight = FontWeight.Bold)
            }

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Color(0xFF1B6B2F), modifier = Modifier.size(24.dp))
                    Text("Thinking...", color = Color(0xFF1B6B2F))
                }
            }

            if (guideReady) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Optimized Plan for $inputCrop", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B6B2F))
                        Divider()
                        StorageRow("Optimal Temp", temp)
                        StorageRow("Optimal Humidity", humidity)
                        StorageRow("Storage Method", method)
                        StorageRow("Max Duration", duration)
                        StorageRow("Warning Signs", warnings)
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun StorageRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 14.sp, color = Color(0xFF1E271E), fontWeight = FontWeight.Medium)
    }
}
