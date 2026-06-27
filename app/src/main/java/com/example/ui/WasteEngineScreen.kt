package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteEngineScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val rawProfile by profileRepository.observeProfile().collectAsState(initial = null)
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var wasteType by remember { mutableStateOf("Leaves/Stalks") }
    var qtyString by remember { mutableStateOf("") }
    var resultsReady by remember { mutableStateOf(false) }

    var compostOutput by remember { mutableStateOf("") }
    var compostValue by remember { mutableStateOf("") }
    var compostCarbon by remember { mutableStateOf("") }
    var biocharDetails by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(rawProfile) {
        rawProfile?.let { profile ->
            farmerProfile = profile
            val w = profile.wasteThisSeason
            if (w.wasteKg > 0 && !resultsReady) {
                qtyString = w.wasteKg.toString()
                resultsReady = true
            }
        }
    }

    LaunchedEffect(resultsReady, qtyString, wasteType, farmerProfile) {
        if (resultsReady) {
            val profile = farmerProfile ?: return@LaunchedEffect
            val qty = qtyString.toDoubleOrNull() ?: 0.0
            if (qty > 0) {
                isLoading = true
                if (com.example.BuildConfig.DEBUG) {
                    Log.i("KRISHIMITRA_DEBUG", "F13_WASTE_CALLED: type=$wasteType qty=$qty")
                }
                try {
                    val systemPrompt = "You are KrishiMitra AI, an expert agricultural waste monetization advisor."
                    val query = """
                        Crop waste type: $wasteType
                        Quantity: ${qty}kg
                        Crop: ${profile.currentCrop.ifEmpty { "Wheat" }}
                        Location: ${profile.location.district.ifEmpty { "Nagpur" }}
                        
                        Suggest waste monetization. Respond in JSON:
                        {
                          "options": [
                            {
                              "method": "Compost OR Biogas OR Biochar OR Animal Feed",
                              "revenue_estimate_inr": 500,
                              "carbon_reduction_pct": 80,
                              "process": "one sentence process description"
                            }
                          ],
                          "recommended": "best option name",
                          "total_co2_saved_kg": 150
                        }
                    """.trimIndent()
                    
                    val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
                    if (com.example.BuildConfig.DEBUG) {
                        Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: waste advice generated")
                    }
                    
                    val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                    val resMap = adapter.fromJson(responseStr)
                    
                    val rawOptions = resMap?.get("options") as? List<*>
                    val optionsList = rawOptions?.mapNotNull { item ->
                        val m = item as? Map<*, *>
                        if (m != null) {
                            val methodVal = m["method"]?.toString() ?: ""
                            val revenue = (m["revenue_estimate_inr"] as? Number)?.toInt() ?: 0
                            val carbonRed = (m["carbon_reduction_pct"] as? Number)?.toInt() ?: 0
                            val processVal = m["process"]?.toString() ?: ""
                            mapOf("method" to methodVal, "revenue" to revenue, "carbon" to carbonRed, "process" to processVal)
                        } else null
                    } ?: emptyList()
                    
                    val recommended = resMap?.get("recommended")?.toString() ?: "Compost"
                    val co2SavedVal = (resMap?.get("total_co2_saved_kg") as? Number)?.toInt() ?: 100
                    
                    if (optionsList.isNotEmpty()) {
                        val firstOpt = optionsList[0]
                        val methodVal = firstOpt["method"]?.toString() ?: ""
                        val revenue = firstOpt["revenue"]?.toString() ?: "0"
                        val carbonRed = firstOpt["carbon"]?.toString() ?: "0"
                        
                        compostOutput = "$methodVal (Recommended: $recommended)"
                        compostValue = "₹$revenue"
                        compostCarbon = "$co2SavedVal kg CO2 ($carbonRed% reduction)"
                        
                        val otherOpts = optionsList.drop(1).joinToString("\n\n") { opt ->
                            "${opt["method"]}: ${opt["process"]} (Est. revenue: ₹${opt["revenue"]}, Carbon reduction: ${opt["carbon"]}%)"
                        }
                        biocharDetails = otherOpts.ifEmpty {
                            "Recommended monetization: $recommended (${firstOpt["process"]?.toString() ?: "Convert waste efficiently."})"
                        }
                    } else {
                        compostOutput = "Compost"
                        compostValue = "₹400"
                        compostCarbon = "$co2SavedVal kg CO2"
                        biocharDetails = "Convert residue efficiently."
                    }
                    
                    val wData = WasteData(crop = profile.currentCrop, wasteKg = qty, method = recommended)
                    val updated = profile.copy(wasteThisSeason = wData)
                    profileRepository.updateProfile(updated, isOnline = false)
                } catch (e: Exception) {
                    Log.e("WasteEngineScreen", "Failed to run waste analysis", e)
                    compostOutput = "${(qty * 0.4).toInt()} kg high-quality compost."
                    compostValue = "₹${(qty * 0.4 * 10).toInt()}"
                    compostCarbon = "${(qty * 1.5).toInt()} kg CO2"
                    biocharDetails = "Burn residue efficiently under controlled oxygen to create biochar for soil health fixing."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "कृषी कचरा व्यवस्थापन"
        "hi" -> "कृषि अपशिष्ट प्रबंध"
        else -> "Agri Waste Engine"
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Leaves/Stalks", "Husks", "Spoiled fruit").forEach { type ->
                    FilterChip(
                        selected = wasteType == type,
                        onClick = { wasteType = type },
                        label = { Text(type) }
                    )
                }
            }

            OutlinedTextField(
                value = qtyString,
                onValueChange = { qtyString = it },
                label = { Text("Quantity (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    resultsReady = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B2F))
            ) {
                Text("Analyze Waste", fontWeight = FontWeight.Bold)
            }

            if (resultsReady) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF1B6B2F))
                } else {
                    Text("Monetization Options:", fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))
                    
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bio-Compost (Vermi)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Est. output: $compostOutput\nValue at regional rate: $compostValue", fontSize = 14.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Carbon saved: $compostCarbon vs field burning.", color = Color(0xFF1B6B2F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Biomass Briquettes / Biochar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(biocharDetails, fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
