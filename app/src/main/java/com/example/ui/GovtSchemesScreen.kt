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
import com.example.data.FarmerProfile
import com.example.data.FarmerProfileRepository
import com.example.data.QwenInferenceService
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovtSchemesScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var applicationGuidance by remember { mutableStateOf("") }
    var isLoadingGuidance by remember { mutableStateOf(false) }

    data class Scheme(
        val name: String,
        val eligible: Boolean,
        val reason: String,
        val documents: List<String>
    )

    LaunchedEffect(Unit) {
        val profile = profileRepository.getProfileOnce()
        farmerProfile = profile

        val pmKisan = Scheme(
            name = "PM-KISAN",
            eligible = profile.landAcres > 0.0 && profile.landAcres <= 5.0,
            reason = "Small farmer with ${profile.landAcres} acres",
            documents = listOf("Aadhaar", "Land Record (7/12)", "Bank Passbook")
        )
        val pmfby = Scheme(
            name = "PMFBY",
            eligible = profile.currentCrop.isNotEmpty(),
            reason = "Crop insurance for ${profile.currentCrop}",
            documents = listOf("Aadhaar", "Land Record", "Bank Account", "Crop Sowing Certificate")
        )
        val nmsa = Scheme(
            name = "NMSA Soil Health Card",
            eligible = profile.soil.healthScore < 70,
            reason = "Soil health score ${profile.soil.healthScore}/100 qualifies for soil improvement subsidy",
            documents = listOf("Aadhaar", "Land Record")
        )

        val eligibleList = listOf(pmKisan, pmfby, nmsa).filter { it.eligible }
        val eligibleNames = eligibleList.map { it.name }

        Log.i("KRISHIMITRA_DEBUG", "F15_SCHEMES_CHECKED: ${eligibleNames.joinToString()}")

        val updated = profile.copy(eligibleSchemes = eligibleNames)
        profileRepository.updateProfile(updated, isOnline = false)
        farmerProfile = updated

        if (eligibleNames.isNotEmpty()) {
            isLoadingGuidance = true
            try {
                val systemPrompt = "You are KrishiMitra AI, an expert govt schemes advisor for Indian farmers."
                val query = """
                    Farmer in ${profile.location.district.ifEmpty { "Nagpur" }} is eligible for ${eligibleNames.joinToString()}.
                    Give 2 sentences on how to apply in Maharashtra.
                    Respond ONLY in JSON: {"guidance": "text here"}
                """.trimIndent()

                Log.i("KRISHIMITRA_DEBUG", "F15_SCHEMES_GUIDANCE_CALL")
                val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: schemes guidance generated")

                val adapter = com.squareup.moshi.Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                    .adapter(Map::class.java)
                val resMap = adapter.fromJson(responseStr)
                val guidance = resMap?.get("guidance")?.toString() ?: ""
                applicationGuidance = guidance
                Log.i("KRISHIMITRA_DEBUG", "F15_GUIDANCE: $guidance")
            } catch (e: Exception) {
                Log.e("GovtSchemesScreen", "Failed to get scheme guidance", e)
            } finally {
                isLoadingGuidance = false
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "सरकारी योजना"
        "hi" -> "सरकारी योजनाएं"
        else -> "Govt Schemes"
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
            val eligible = currentProfile.eligibleSchemes
            if (eligible.isEmpty()) {
                Text("Update land info to view matching schemes.", color = Color.Gray)
            } else {
                Text("Schemes you are eligible for:", fontWeight = FontWeight.Bold)
                
                if (eligible.contains("PM-KISAN")) {
                    SchemeCard(
                        title = "PM-KISAN Samman Nidhi",
                        reason = "Eligible because land acreage is ≤ 5 acres (Small/Marginal farmer).",
                        docs = "Aadhaar Card, 7/12 Extract, Bank Account",
                        applyDesc = "Register at pmkisan.gov.in or visit local CSC scheme operator."
                    )
                }

                SchemeCard(
                    title = "Pradhan Mantri Fasal Bima Yojana (PMFBY)",
                    reason = "All farmers cultivating notified crops.",
                    docs = "Land Record, Bank Passbook, Sowing Certificate",
                    applyDesc = "Apply via PMC bank or online portal before cut-off date for Kharif/Rabi."
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SchemeCard(title: String, reason: String, docs: String, applyDesc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B6B2F))
            Text(reason, fontSize = 13.sp, color = Color(0xFF555F55))
            Divider()
            Text("Docs required:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(docs, fontSize = 12.sp, color = Color.Gray)
            Text("How to apply:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(applyDesc, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
