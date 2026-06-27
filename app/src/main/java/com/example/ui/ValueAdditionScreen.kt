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
import com.example.data.*
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueAdditionScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var options by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        farmerProfile = profileRepository.getProfileOnce()
    }

    LaunchedEffect(farmerProfile) {
        val profile = farmerProfile ?: return@LaunchedEffect
        val crop = profile.lastHarvest.crop.ifEmpty { "Tomato" }
        isLoading = true
        if (com.example.BuildConfig.DEBUG) {
            Log.i("KRISHIMITRA_DEBUG", "F12_VALUEADD_CALLED")
        }
        try {
            val systemPrompt = "You are KrishiMitra AI, an expert value addition advisor for Indian farmers."
            val query = """
                Crop: $crop
                Quantity available: ${profile.lastHarvest.qtyKg.takeIf { it > 0.0 } ?: 100.0}kg
                Location: ${profile.location.district.ifEmpty { "Nagpur" }}
                
                Suggest value-added products. Respond in JSON:
                {
                  "options": [
                    {
                      "product": "Tomato Paste",
                      "revenue_per_kg_inr": 150,
                      "difficulty": "Easy",
                      "process": "one sentence process description",
                      "shelf_life_months": 6
                    }
                  ]
                }
            """.trimIndent()
            
            val responseStr = qwenInferenceService.runCustomInference(systemPrompt, query)
            if (com.example.BuildConfig.DEBUG) {
                Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: value addition options suggested")
            }
            
            val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
            val resMap = adapter.fromJson(responseStr)
            val rawOptions = resMap?.get("options") as? List<*>
            options = rawOptions?.mapNotNull { item ->
                val opt = item as? Map<*, *>
                if (opt != null) {
                    val product = opt["product"]?.toString() ?: ""
                    val revenue = opt["revenue_per_kg_inr"]?.toString() ?: "100"
                    val diff = opt["difficulty"]?.toString() ?: "Easy"
                    val proc = opt["process"]?.toString() ?: ""
                    val shelf = opt["shelf_life_months"]?.toString() ?: "3"
                    Triple(product, "₹$revenue/kg ($diff)", "$proc [Shelf Life: $shelf months]")
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("ValueAdditionScreen", "Failed to generate value addition options", e)
            options = listOf(
                Triple("Sun-Dried $crop", "₹250/kg (Easy)", "Cut thin slices, treat with mild salt, sun-dry for 3 days on clean mats. [Shelf Life: 6 months]"),
                Triple("$crop Paste/Puree", "₹180/kg (Medium)", "Boil, blend, strain seeds, add preservative, pack in jars. [Shelf Life: 12 months]"),
                Triple("$crop Pickle/Achar", "₹300/kg (Easy)", "Marinate in oil, spices, and vinegar. Cures in 7 days. [Shelf Life: 12 months]")
            )
        } finally {
            isLoading = false
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "मूल्यवर्धन प्रक्रिया"
        "hi" -> "मूल्य संवर्धन"
        else -> "Value Addition"
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
            val crop = currentProfile.lastHarvest.crop.ifEmpty { "Tomato" }
            
            Text("Transform your $crop supply into higher margin products.", fontSize = 15.sp, color = Color.Gray)

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF1B6B2F))
            } else {
                options.forEach { opt ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(opt.first, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E271E))
                                Text(opt.second, fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(opt.third, fontSize = 13.sp, color = Color(0xFF555F55))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
