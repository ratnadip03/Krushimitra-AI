package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun CarbonTrackerScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val rawProfile by profileRepository.observeProfile().collectAsState(initial = null)
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var co2Saved by remember { mutableStateOf(0.0) }
    var recommendationText by remember { mutableStateOf("") }
    var scoreText by remember { mutableStateOf("Excellent") }
    var eligibleText by remember { mutableStateOf("Eligible for upcoming Green Farm Certificate.") }

    LaunchedEffect(rawProfile) {
        rawProfile?.let { profile ->
            farmerProfile = profile
            
            val waste = profile.wasteThisSeason
            val BURNING_FACTOR = 3.67f
            val COMPOSTING_FACTOR = 0.04f
            val DIESEL_FACTOR = 2.68f
            val distanceKm = 50f
            
            val wasteEmission = if (waste.method == "burning")
                waste.wasteKg.toFloat() * BURNING_FACTOR
            else waste.wasteKg.toFloat() * COMPOSTING_FACTOR

            val transportEmission = (distanceKm / 8f) * DIESEL_FACTOR
            val totalCO2 = wasteEmission + transportEmission

            val sustainabilityScore = maxOf(0, 100 - (totalCO2 / 10).toInt())
            val certificateEligible = sustainabilityScore > 60

            co2Saved = totalCO2.toDouble()
            Log.i("KRISHIMITRA_DEBUG", "F14_CARBON_CALCULATED: totalCO2=$totalCO2")

            scoreText = if (sustainabilityScore > 80) "Excellent ($sustainabilityScore)" else if (sustainabilityScore > 60) "Good ($sustainabilityScore)" else "Fair ($sustainabilityScore)"
            eligibleText = if (certificateEligible) "Eligible for upcoming Green Farm Certificate." else "Not eligible for Green Farm Certificate yet."

            if (profile.carbonFootprintKg != co2Saved) {
                val updated = profile.copy(carbonFootprintKg = co2Saved)
                profileRepository.updateProfile(updated, isOnline = false)
            }

            coroutineScope.launch {
                try {
                    val systemPrompt = "You are KrishiMitra AI, a sustainability advisor for Indian farmers."
                    val query = "Farmer has sustainability score of ${sustainabilityScore} and total carbon emission is ${totalCO2} kg CO2. Give a one sentence recommendation to improve or maintain this."
                    val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                    recommendationText = responseStr
                } catch (e: Exception) {
                    Log.e("CarbonTrackerScreen", "Failed to get sustainability advice", e)
                    recommendationText = "Maintain high organic carbon levels and composting practices to reduce carbon footprint."
                }
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "कार्बन ट्रॅकर"
        "hi" -> "कार्बन ट्रैकर"
        else -> "Carbon Tracker"
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
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B6B2F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${co2Saved.toInt()} kg", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                            Text("CO2 Footprint", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sustainability Score: $scoreText", fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))
                    Text(recommendationText.ifEmpty { "You avoided crop burning and maintained soil organic carbon above average." }, fontSize = 14.sp)
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EnergySavingsLeaf, contentDescription = "Leaf", tint = Color(0xFF1B6B2F))
                        Text(eligibleText, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
