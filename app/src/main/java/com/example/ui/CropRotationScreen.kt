package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
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
fun CropRotationScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var rotationPlan by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profile = profileRepository.getProfileOnce()
        farmerProfile = profile
        
        if (profile.rotationPlan.isEmpty()) {
            isLoading = true
            Log.i("KRISHIMITRA_DEBUG", "F03_ROTATION_CALLED: crop=${profile.currentCrop}")
            try {
                val systemPrompt = "You are KrishiMitra AI, an expert crop rotation advisor for Indian farmers."
                val query = """
                    Current crop: ${profile.currentCrop.ifEmpty { "Wheat" }}
                    Soil NPK: N=${profile.soil.N.ifEmpty { "80" }}, P=${profile.soil.P.ifEmpty { "40" }}, K=${profile.soil.K.ifEmpty { "40" }}, pH=${if (profile.soil.pH > 0.0) profile.soil.pH else 6.5}
                    Location: ${profile.location.district.ifEmpty { "Nagpur" }}, Maharashtra
                    
                    Suggest a 3-season crop rotation plan.
                    Respond ONLY in this JSON:
                    {
                      "season1": {"crop": "name", "reason": "one line"},
                      "season2": {"crop": "name", "reason": "one line"},
                      "season3": {"crop": "name", "reason": "one line"},
                      "overall_benefit": "one sentence summary"
                    }
                """.trimIndent()
                
                val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: rotation plan generated")
                
                val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                val resMap = adapter.fromJson(responseStr)
                
                val s1 = resMap?.get("season1") as? Map<*, *>
                val s2 = resMap?.get("season2") as? Map<*, *>
                val s3 = resMap?.get("season3") as? Map<*, *>
                
                val c1 = s1?.get("crop")?.toString() ?: (profile.currentCrop.ifEmpty { "Wheat" })
                val r1 = s1?.get("reason")?.toString() ?: "Initial crop phase."
                val c2 = s2?.get("crop")?.toString() ?: "Chickpea"
                val r2 = s2?.get("reason")?.toString() ?: "Nitrogen restoration phase."
                val c3 = s3?.get("crop")?.toString() ?: "Soybean"
                val r3 = s3?.get("reason")?.toString() ?: "Soil replenishment."
                
                val generatedPlan = listOf(
                    mapOf("season" to "Season 1 (Current)", "crop" to c1, "reason" to r1),
                    mapOf("season" to "Season 2", "crop" to c2, "reason" to r2),
                    mapOf("season" to "Season 3", "crop" to c3, "reason" to r3)
                )
                
                rotationPlan = generatedPlan

                val updatedProfile = profile.copy(rotationPlan = listOf(c1, c2, c3))
                profileRepository.updateProfile(updatedProfile, isOnline = false)
            } catch (e: Exception) {
                Log.e("CropRotationScreen", "Failed to generate crop rotation plan", e)
                val generatedPlan = listOf(
                    mapOf("season" to "Season 1 (Current)", "crop" to (profile.currentCrop.ifEmpty { "Wheat" }), "reason" to "Planned crop."),
                    mapOf("season" to "Season 2", "crop" to "Chickpea", "reason" to "Restores nitrogen level."),
                    mapOf("season" to "Season 3", "crop" to "Soybean", "reason" to "Replenishes organic matter.")
                )
                rotationPlan = generatedPlan
            } finally {
                isLoading = false
            }
        } else {
            // Already has rotation plan, map it to mock structure
            val stored = profile.rotationPlan
            rotationPlan = stored.mapIndexed { index, crop ->
                mapOf("season" to "Season ${index + 1}", "crop" to crop, "reason" to "Planned rotation phase.")
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "पीक फेरपालट"
        "hi" -> "फसल चक्र"
        else -> "Crop Rotation"
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (lang) {
                    "mr" -> "३ हंगामांचा फेरपालट आराखडा"
                    "hi" -> "3-सीज़न रोटेशन योजना"
                    else -> "3-Season Rotation Plan"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1B6B2F),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF1B6B2F))
                Text("Thinking...", color = Color(0xFF1B6B2F))
            } else {
                rotationPlan.forEachIndexed { index, phase ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = phase["season"] ?: "", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = phase["crop"] ?: "", fontSize = 20.sp, color = Color(0xFF1E271E), fontWeight = FontWeight.ExtraBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = phase["reason"] ?: "", fontSize = 14.sp, color = Color(0xFF555F55))
                        }
                    }
                    if (index < rotationPlan.size - 1) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Next",
                            tint = Color(0xFF1B6B2F),
                            modifier = Modifier.padding(vertical = 4.dp).size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
