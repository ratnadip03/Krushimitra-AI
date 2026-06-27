package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class FeatureTile(
    val route: String,
    val emoji: String,
    val nameEn: String,
    val nameMr: String,
    val nameHi: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    profileRepository: FarmerProfileRepository,
    connectivityService: ConnectivityService,
    onNavigateToFeature: (String) -> Unit,
    onOpenVoiceAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    val isOnline by connectivityService.isOnlineFlow.collectAsState(initial = connectivityService.isOnlineOnce())

    var showProfileDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Fetch the real farmer profile
    LaunchedEffect(Unit) {
        profileRepository.observeProfile().collectLatest { profile ->
            if (profile != null) {
                farmerProfile = profile
            } else {
                farmerProfile = profileRepository.getProfileOnce()
            }
        }
    }

    LaunchedEffect(isOnline, farmerProfile) {
        val prof = farmerProfile ?: return@LaunchedEffect
        if (isOnline) {
            val needsWeather = prof.cachedWeather.tempC == 0.0 && prof.location.district.isNotEmpty()
            val needsMandi = prof.cachedMandiPrice.modalPrice == 0.0 && prof.currentCrop.isNotEmpty()
            if (needsWeather || needsMandi) {
                profileRepository.updateProfile(prof, isOnline = true)
            }
        }
    }

    val profile = farmerProfile ?: FarmerProfile()
    val lang = profile.language

    // Defined features (16 modules)
    val features = remember {
        listOf(
            FeatureTile("soil_passport", "🪱", "Soil Passport", "माती ओळखपत्र", "मृदा पासपोर्ट"),
            FeatureTile("pre_crop", "🌱", "Pre-Crop Advisor", "पूर्व-पीक सल्लागार", "पूर्व-फसल सलाहकार"),
            FeatureTile("crop_rotation", "🔄", "Crop Rotation", "पीक फेरपालट", "फसल चक्र"),
            FeatureTile("freshness", "🍅", "Freshness Check", "ताजेपणा तपासणी", "ताजगी जांच"),
            FeatureTile("storage_guide", "📦", "Storage Guide", "साठवणूक मार्गदर्शक", "भंडारण गाइड"),
            FeatureTile("harvest_timing", "🌾", "Harvest Timing", "काढणीची वेळ", "कटाई का समय"),
            FeatureTile("cold_chain", "❄️", "Cold Chain", "कोल्ड चेन मार्गदर्शक", "कोल्ड चेन सुरक्ष"),
            FeatureTile("sell_advisor", "💰", "Best Time to Sell", "विक्रीची योग्य वेळ", "बेचने का सही समय"),
            FeatureTile("market_price", "📈", "Market Price", "बाजार भाव", "मंडी भाव"),
            FeatureTile("postharvest_loss", "📉", "Post-Harvest Loss", "काढणीपश्यात नुकसान", "कटाई बाद नुकसान"),
            FeatureTile("surplus", "🤝", "Surplus Exchange", "अतिरिक्त देवाणघेवाण", "अधिशेष विनिमय"),
            FeatureTile("value_addition", "🏭", "Value Addition", "मूल्यवर्धन प्रक्रिया", "मूल्य संवर्धन"),
            FeatureTile("waste_engine", "♻️", "Agri Waste", "कृषी कचरा व्यवस्थापन", "कृषि अपशिष्ट"),
            FeatureTile("carbon_tracker", "🌍", "Carbon Tracker", "कार्बन ट्रॅकर", "कार्बन ट्रैकर"),
            FeatureTile("govt_schemes", "🏛️", "Govt Schemes", "सरकारी योजना", "सरकारी योजनाएं"),
            FeatureTile("voice", "🎤", "Voice Assistant", "व्हॉईस असिस्टंट", "वॉयस असिस्टेंट")
        )
    }

    // Greeting card indicators (with placeholders/dashes if profile is empty)
    val displayName = if (profile.name.isBlank() || profile.name == "Shetkari Raja") "---" else profile.name
    val displayDistrict = if (profile.location.district.isBlank()) "---" else profile.location.district
    val displayAcres = if (profile.landAcres <= 1.0 && profile.name.isBlank()) "---" else profile.landAcres.toString()
    val displayCrop = if (profile.currentCrop.isBlank()) "---" else profile.currentCrop

    val greetingText = when (lang) {
        "mr" -> "नमस्कार, $displayName!"
        "hi" -> "नमस्कार, $displayName!"
        else -> "Welcome, $displayName!"
    }

    val subtitleText = when (lang) {
        "mr" -> "जिल्हा: $displayDistrict मधील शेतकरी • क्षेत्र: $displayAcres एकर"
        "hi" -> "जिला: $displayDistrict के किसान • भूमि क्षेत्र: $displayAcres एकड़"
        else -> "Farmer from $displayDistrict • Land: $displayAcres Acres"
    }

    val soilHeader = when (lang) {
        "mr" -> "माती आरोग्य आणि चालू पीक"
        "hi" -> "मृदा स्वास्थ्य और वर्तमान फसल"
        else -> "Soil Health & Active Crop"
    }

    val activeCropLabel = when (lang) {
        "mr" -> "चालू पीक: $displayCrop"
        "hi" -> "वर्तमान फसल: $displayCrop"
        else -> "Current Crop: $displayCrop"
    }

    val soilScoreLabel = when (lang) {
        "mr" -> "माती आरोग्य स्कोअर: ${profile.soil.healthScore}/100"
        "hi" -> "मृदा स्वास्थ्य स्कोर: ${profile.soil.healthScore}/100"
        else -> "Soil Health Score: ${profile.soil.healthScore}/100"
    }

    val weatherLabel = if (profile.cachedWeather.tempC != 0.0) {
        val temp = profile.cachedWeather.tempC.toInt()
        val cond = profile.cachedWeather.condition
        when (lang) {
            "mr" -> "हवामान: ${temp}°C $cond (जिल्हा: $displayDistrict)"
            "hi" -> "मौसम: ${temp}°C $cond ($displayDistrict)"
            else -> "Weather: ${temp}°C $cond ($displayDistrict)"
        }
    } else {
        when (lang) {
            "mr" -> "हवामान: २९°C ☀️ निरभ्र (जिल्हा: $displayDistrict)"
            "hi" -> "मौसम: २९°C ☀️ साफ़ आकाश ($displayDistrict)"
            else -> "Weather: 29°C ☀️ Sunny ($displayDistrict)"
        }
    }

    val appTitle = "KrishiMitra AI"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // LAYER 1: BASE DASHBOARD SCENE (Header, Greeting, 16-Tile Modules)
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = appTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF1B6B2F),
                            modifier = Modifier.testTag("dashboard_app_title")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Online/Offline status
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = if (isOnline) "ONLINE" else "OFFLINE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOnline) Color(0xFF1B6B2F) else Color(0xFFC62828)
                                    )
                                }
                            }

                            // Profile Edit click chip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F4F1))
                                    .clickable { showProfileDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Farmer Accent",
                                    tint = Color(0xFF1B6B2F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = displayName.take(12),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E271E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Divider(color = Color(0xFFE2F3E7))
                }
            },
            containerColor = Color(0xFFFAFAF7)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Greeting card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_greeting_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = greetingText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B6B2F)
                        )

                        Text(
                            text = subtitleText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334F37)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = Color(0xFFC8E6C9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = soilHeader,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B6B2F)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = activeCropLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E271E)
                                )
                                Text(
                                    text = soilScoreLabel,
                                    fontSize = 13.sp,
                                    color = Color(0xFF555F55)
                                )
                            }

                            if (isOnline) {
                                Text(
                                    text = weatherLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF31613A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Modules Grid inside scrollable parent Column
                val featuresRows = remember(features) { features.chunked(2) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("features_grid"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    featuresRows.forEach { rowFeatures ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowFeatures.forEach { feature ->
                                val featureName = when (lang) {
                                    "mr" -> feature.nameMr
                                    "hi" -> feature.nameHi
                                    else -> feature.nameEn
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (feature.route == "voice") {
                                                onOpenVoiceAssistant()
                                            } else {
                                                onNavigateToFeature(feature.route)
                                            }
                                        }
                                        .border(1.dp, Color(0xFFE2F3E7), RoundedCornerShape(16.dp))
                                        .testTag("tile_card_${feature.route}"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp, horizontal = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = feature.emoji,
                                            fontSize = 36.sp,
                                            color = Color(0xFF1B6B2F),
                                            textAlign = TextAlign.Center
                                        )

                                        Text(
                                            text = featureName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E271E),
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            minLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (rowFeatures.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    if (showProfileDialog) {
        Dialog(onDismissRequest = { showProfileDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFAFAF7)
            ) {
                var editName by remember { mutableStateOf(profile.name) }
                var editLang by remember { mutableStateOf(profile.language) }
                var editDistrict by remember { mutableStateOf(profile.location.district) }
                var editStateArea by remember { mutableStateOf(profile.location.state) }
                var editAcres by remember { mutableStateOf(profile.landAcres.toString()) }
                var editCrop by remember { mutableStateOf(profile.currentCrop) }

                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Edit Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B6B2F))

                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it }, label = { Text("Name", color = Color(0xFF1E271E)) }, 
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E271E), fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E271E),
                            unfocusedTextColor = Color(0xFF1E271E)
                        )
                    )
                    OutlinedTextField(
                        value = editLang, onValueChange = { editLang = it }, label = { Text("Language (en, mr, hi)", color = Color(0xFF1E271E)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E271E), fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E271E),
                            unfocusedTextColor = Color(0xFF1E271E)
                        )
                    )
                    OutlinedTextField(
                        value = editDistrict, onValueChange = { editDistrict = it }, label = { Text("District", color = Color(0xFF1E271E)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E271E), fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E271E),
                            unfocusedTextColor = Color(0xFF1E271E)
                        )
                    )
                    OutlinedTextField(
                        value = editStateArea, onValueChange = { editStateArea = it }, label = { Text("State", color = Color(0xFF1E271E)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E271E), fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E271E),
                            unfocusedTextColor = Color(0xFF1E271E)
                        )
                    )
                    OutlinedTextField(
                        value = editAcres, onValueChange = { editAcres = it }, label = { Text("Acres", color = Color(0xFF1E271E)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E271E), fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E271E),
                            unfocusedTextColor = Color(0xFF1E271E)
                        )
                    )
                    OutlinedTextField(
                        value = editCrop, onValueChange = { editCrop = it }, label = { Text("Current Crop", color = Color(0xFF1E271E)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E271E), fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E271E),
                            unfocusedTextColor = Color(0xFF1E271E)
                        )
                    )

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            coroutineScope.launch {
                                val updated = profile.copy(
                                    name = editName,
                                    language = editLang,
                                    location = profile.location.copy(district = editDistrict, state = editStateArea),
                                    landAcres = editAcres.toDoubleOrNull() ?: profile.landAcres,
                                    currentCrop = editCrop
                                )
                                profileRepository.updateProfile(updated, isOnline = isOnline)
                                showProfileDialog = false
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
