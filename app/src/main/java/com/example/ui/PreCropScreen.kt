package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCropScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onNavigateToSoilPassport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var recommendations by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profile = profileRepository.getProfileOnce()
        farmerProfile = profile
        if (profile.soil.healthScore > 0) {
            isLoading = true
            try {
                val prompt = """
                    Based on the soil health score and NPK/pH parameters, suggest the top 5 crops.
                    Return a JSON array of objects, where each object has these fields exactly:
                    "name": name of the crop,
                    "yield": expected yield per acre,
                    "suitability": percentage match like "90%",
                    "reason": 1 sentence explanation of why it fits the soil health.
                    
                    Example output format:
                    [
                      {"name": "Soybean", "yield": "8-10 quintal/acre", "suitability": "92%", "reason": "Excellent match for your pH."}
                    ]
                """.trimIndent()
                val responseStr = qwenInferenceService.runFeatureInference(prompt, profile)
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
                val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter<List<Map<String, String>>>(type)
                recommendations = adapter.fromJson(responseStr) ?: emptyList()
            } catch (e: Exception) {
                Log.e("PreCropScreen", "Failed to run inference", e)
            } finally {
                isLoading = false
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "पूर्व-पीक सल्लागार"
        "hi" -> "पूर्व-फसल सलाहकार"
        else -> "Pre-Crop Advisor"
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentProfile.soil.healthScore == 0 && farmerProfile != null) {
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
                            text = when (lang) {
                                "mr" -> "कृपया आधी माती ओळखपत्र पूर्ण करा जेणेकरून आम्ही योग्य पीक सुचवू शकू."
                                "hi" -> "कृपया पहले मिट्टी हेल्थ कार्ड पूरा करें ताकि हम सही फसल की सलाह दे सकें।"
                                else -> "Please complete Soil Passport first to get accurate crop recommendations."
                            },
                            textAlign = TextAlign.Center,
                            color = Color(0xFFD32F2F)
                        )
                        Button(
                            onClick = onNavigateToSoilPassport,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text(if (lang == "en") "Go to Soil Passport" else if (lang == "hi") "सॉइल पासपोर्ट पर जाएं" else "माती ओळखपत्रावर जा")
                        }
                    }
                }
            } else if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF1B6B2F))
                Text(
                    text = when (lang) {
                        "mr" -> "पीक शक्यता तपासत आहे..."
                        "hi" -> "फसल की संभावनाओं की जाँच की जा रही है..."
                        else -> "Thinking..."
                    },
                    color = Color(0xFF1B6B2F)
                )
            } else {
                recommendations.forEach { rec ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rec["name"] ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF1E271E)
                                )
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${rec["suitability"]} Match",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color(0xFF1B6B2F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Yield: ${rec["yield"]}", fontSize = 14.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = rec["reason"] ?: "", fontSize = 14.sp, color = Color(0xFF555F55))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp)) // Avoid FAB clipping
        }
    }
}
