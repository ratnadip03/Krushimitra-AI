package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectivityService
import com.example.data.FarmerProfile
import com.example.data.FarmerProfileRepository
import com.example.data.FeatureInferenceService
import com.example.data.QwenInferenceService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurplusScreen(
    profileRepository: FarmerProfileRepository,
    connectivityService: ConnectivityService,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var surplusSuggestion by remember { mutableStateOf("") }
    var listingSummary by remember { mutableStateOf("") }
    var urgencyText by remember { mutableStateOf("Medium") }
    val isOnline by connectivityService.isOnlineFlow.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        val profile = profileRepository.getProfileOnce()
        farmerProfile = profile
        try {
            val advice = FeatureInferenceService(qwenInferenceService).inferSurplusAdvice(profile)
            surplusSuggestion = advice.suggestion
            listingSummary = advice.listingSummary
            urgencyText = advice.urgency
        } catch (e: Exception) {
            surplusSuggestion = ""
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "अतिरिक्त देवाणघेवाण"
        "hi" -> "अधिशेष विनिमय"
        else -> "Surplus Exchange"
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
            if (surplusSuggestion.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F3E7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Auto-Surplus Suggestion", fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))
                        Text(surplusSuggestion, fontSize = 14.sp)
                    }
                }
            } else if (currentProfile.freshnessAlerts.isNotEmpty() && currentProfile.freshnessAlerts.last().freshnessScore < 60) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F3E7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Auto-Surplus Suggestion", fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))
                        Text("Freshness score is dropping. Consider selling supply locally at discount.", fontSize = 14.sp)
                    }
                }
            }

            Text("My Active Listings:", fontWeight = FontWeight.Bold)

            val syncStatus = if (isOnline) "🟢 Live Online" else "🟡 Pending Sync (Offline)"

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            listingSummary.ifEmpty {
                                "${currentProfile.lastHarvest.crop.ifEmpty { "Onion" }} - ${currentProfile.lastHarvest.qtyKg.takeIf { it > 0 } ?: 50} kg"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(syncStatus, fontSize = 12.sp, color = if (isOnline) Color(0xFF1B6B2F) else Color(0xFFF57F17))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Listed 2 hours ago. Urgency: $urgencyText", fontSize = 13.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
