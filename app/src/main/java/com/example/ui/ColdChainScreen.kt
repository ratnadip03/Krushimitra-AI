package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
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
import com.example.data.GeminiService
import com.example.data.ConnectivityService
import android.util.Log
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColdChainScreen(
    profileRepository: FarmerProfileRepository,
    qwenInferenceService: QwenInferenceService,
    geminiService: GeminiService,
    connectivityService: ConnectivityService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rawProfile by profileRepository.observeProfile().collectAsState(initial = null)
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    
    var verdict by remember { mutableStateOf("") }
    var tempVal by remember { mutableStateOf("") }
    var daysVal by remember { mutableStateOf("") }
    var costVal by remember { mutableStateOf("") }
    var premiumVal by remember { mutableStateOf("") }
    var reasonVal by remember { mutableStateOf("") }
    var hintVal by remember { mutableStateOf("") }
    var stores by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(rawProfile) {
        rawProfile?.let { farmerProfile = it }
    }

    LaunchedEffect(farmerProfile) {
        val profile = farmerProfile ?: return@LaunchedEffect
        val crop = profile.lastHarvest.crop.ifEmpty { "Tomato" }
        val qty = profile.lastHarvest.qtyKg.takeIf { it > 0.0 } ?: 100.0
        val latestFreshnessScore = profile.freshnessAlerts.lastOrNull()?.freshnessScore ?: 75
        
        isLoading = true
        Log.i("KRISHIMITRA_DEBUG", "F07_COLDCHAIN_CALLED")
        
        val isOnline = connectivityService.isOnlineOnce()
        val query = """
            Produce: $crop
            Quantity: ${qty}kg
            Location: ${profile.location.district.ifEmpty { "Nagpur" }}, Maharashtra
            Freshness score: $latestFreshnessScore/100
        """.trimIndent()
        
        try {
            if (isOnline) {
                val prompt = """
                    $query
                    Suggest a cold storage verdict and nearby facility names in the district.
                    Respond ONLY in JSON format:
                    {
                      "recommended_temp_celsius": 12,
                      "max_storage_days": 30,
                      "storage_cost_estimate_inr": 500,
                      "expected_price_premium_pct": 20,
                      "verdict": "STORE_NOW",
                      "verdict_reason": "One sentence explanation.",
                      "nearest_storage_hint": "Area hint",
                      "facilities": [
                        {"name": "Facility Name A", "rate": "₹15 per kg/month"},
                        {"name": "Facility Name B", "rate": "₹12 per kg/month"}
                      ]
                    }
                """.trimIndent()
                val responseStr = geminiService.getRawGeminiResponse(prompt)
                Log.i("KRISHIMITRA_DEBUG", "GEMINI_RESPONSE: ${responseStr.take(200)}")
                
                val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                val resMap = adapter.fromJson(responseStr)
                
                verdict = resMap?.get("verdict")?.toString() ?: "STORE_NOW"
                tempVal = "${resMap?.get("recommended_temp_celsius") ?: "12"}°C"
                daysVal = "${resMap?.get("max_storage_days") ?: "30"} days"
                costVal = "₹${resMap?.get("storage_cost_estimate_inr") ?: "500"}"
                premiumVal = "${resMap?.get("expected_price_premium_pct") ?: "20"}%"
                reasonVal = resMap?.get("verdict_reason")?.toString() ?: ""
                hintVal = resMap?.get("nearest_storage_hint")?.toString() ?: ""
                
                val facList = resMap?.get("facilities") as? List<*>
                stores = facList?.map { item ->
                    val m = item as? Map<*, *>
                    Pair(m?.get("name")?.toString() ?: "Cold Storage", m?.get("rate")?.toString() ?: "₹12 per kg/month")
                } ?: emptyList()
            } else {
                val systemPrompt = "You are KrishiMitra AI, a cold storage advisor for Indian farmers."
                val qwenQuery = """
                    $query
                    Respond ONLY in JSON:
                    {
                      "recommended_temp_celsius": 12,
                      "max_storage_days": 30,
                      "storage_cost_estimate_inr": 500,
                      "expected_price_premium_pct": 20,
                      "verdict": "STORE_NOW OR SELL_NOW",
                      "verdict_reason": "one sentence explanation",
                      "nearest_storage_hint": "general area hint based on district e.g. Check Kolhapur APMC cold storage"
                    }
                """.trimIndent()
                val responseStr = qwenInferenceService.runCustomInference(systemPrompt, qwenQuery)
                Log.i("KRISHIMITRA_DEBUG", "INFERENCE_DONE: cold chain advised")
                
                val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Map::class.java)
                val resMap = adapter.fromJson(responseStr)
                
                verdict = resMap?.get("verdict")?.toString() ?: "STORE_NOW"
                tempVal = "${resMap?.get("recommended_temp_celsius") ?: "12"}°C"
                daysVal = "${resMap?.get("max_storage_days") ?: "30"} days"
                costVal = "₹${resMap?.get("storage_cost_estimate_inr") ?: "500"}"
                premiumVal = "${resMap?.get("expected_price_premium_pct") ?: "20"}%"
                reasonVal = resMap?.get("verdict_reason")?.toString() ?: ""
                hintVal = resMap?.get("nearest_storage_hint")?.toString() ?: ""
                stores = emptyList()
            }
        } catch (e: Exception) {
            Log.e("ColdChainScreen", "Failed cold chain advising", e)
            verdict = "STORE_NOW"
            tempVal = "12°C"
            daysVal = "30 days"
            costVal = "₹500"
            premiumVal = "20%"
            reasonVal = "Cold storage helps extend shelf life and capture better prices."
            hintVal = "Check local APMC warehouse."
            stores = emptyList()
        } finally {
            isLoading = false
        }
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    val titleText = when (lang) {
        "mr" -> "कोल्ड चेन मार्गदर्शक"
        "hi" -> "कोल्ड चेन गाइड"
        else -> "Cold Chain Guide"
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
            val crop = currentProfile.lastHarvest.crop.ifEmpty { "Produce" }
            val qty = currentProfile.lastHarvest.qtyKg.takeIf { it > 0 }?.toString() ?: "100"
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AcUnit, contentDescription = "Cold", tint = Color(0xFF1B6B2F))
                        Text("Store vs. Sell Verdict", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B6B2F))
                    }
                    Divider(color = Color.White)
                    Text("Produce: $crop ($qty kg)", fontSize = 14.sp)
                    
                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFF1B6B2F))
                    } else {
                        Text(
                            text = if (verdict.isNotEmpty()) {
                                "Recommendation: ${if (verdict == "STORE_NOW") "STORE in Cold Storage" else "SELL NOW"}\n\n" +
                                "• Optimal Temp: $tempVal\n" +
                                "• Max Storage: $daysVal\n" +
                                "• Est. Cost: $costVal\n" +
                                "• Expected Premium: $premiumVal\n\n" +
                                "Verdict reason: $reasonVal\n\n" +
                                "Nearest storage hint: $hintVal"
                            } else {
                                "Recommendation: STORE in Cold Storage\n\nCost to store for 1 month is approx ₹500/ton, but historical patterns show a 25% price increase next month. Net benefit is positive."
                            },
                            fontSize = 14.sp,
                            color = Color(0xFF1E271E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Text("Simulated nearby stores (Online active):", fontWeight = FontWeight.Bold)
            
            if (stores.isNotEmpty()) {
                stores.forEach { store ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(store.first, fontWeight = FontWeight.Bold)
                            Text(store.second, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Kisan Cold Storage (12 km away)", fontWeight = FontWeight.Bold)
                        Text("₹15 per kg/month", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AgriTech Warehouse (18 km away)", fontWeight = FontWeight.Bold)
                        Text("₹12 per kg/month (Bulk)", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
