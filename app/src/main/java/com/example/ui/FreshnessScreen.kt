package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshnessScreen(
    profileRepository: FarmerProfileRepository,
    tfliteVisionService: TFLiteVisionService,
    cameraService: CameraService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var farmerProfile by remember { mutableStateOf<FarmerProfile?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var classificationResult by remember { mutableStateOf<TFLiteResult?>(null) }
    var modelAbsentExplanation by remember { mutableStateOf<String?>(null) }

    // Fetch profile
    LaunchedEffect(Unit) {
        farmerProfile = profileRepository.getProfileOnce()
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    // Language localizations
    val titleText = when (lang) {
        "mr" -> "ताजेपणा तपासणी"
        "hi" -> "ताजगी जांच"
        else -> "Freshness Check"
    }

    val cameraButtonLabel = when (lang) {
        "mr" -> "फोटो काढा"
        "hi" -> "फोटो लें"
        else -> "Take Photo"
    }

    val galleryButtonLabel = when (lang) {
        "mr" -> "गॅलरीतून निवडा"
        "hi" -> "गैलरी से चुनें"
        else -> "Choose Gallery"
    }

    val placeholderText = when (lang) {
        "mr" -> "ताजेपणा तपासण्यासाठी पिकाचा किंवा उत्पादनाचा स्वच्छ फोटो काढा"
        "hi" -> "ताजगी जांच के लिए फसल या उत्पाद का एक स्पष्ट फोटो लें"
        else -> "Capture a clear photo of your crop or produce to check its freshness level"
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            classificationResult = null
            modelAbsentExplanation = null
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = cameraService.getBitmapFromUri(uri)
            if (bitmap != null) {
                selectedBitmap = bitmap
                classificationResult = null
                modelAbsentExplanation = null
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = titleText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1B6B2F)
                        )
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selected Image Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .testTag("freshness_image_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Crop Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera placeholder",
                                tint = Color(0xFF889588),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = placeholderText,
                                fontSize = 14.sp,
                                color = Color(0xFF555F55),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Image Source Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("camera_launch_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = cameraButtonLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("gallery_launch_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1B6B2F)),
                    border = BorderStroke(1.dp, Color(0xFF1B6B2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = galleryButtonLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Analysis Action Trigger
            if (selectedBitmap != null && classificationResult == null && !isAnalyzing) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isAnalyzing = true
                            kotlinx.coroutines.delay(1200) // simulated calculation delay
                            val res = tfliteVisionService.classifyFreshness(selectedBitmap!!)
                            if (res.label == "MODEL_NOT_FOUND") {
                                modelAbsentExplanation = "Model file 'freshness_mobilenetv2.tflite' was not found in assets.\nPath: app/src/main/assets/models/tflite/freshness_mobilenetv2.tflite"
                            } else if (res.label == "NOT_A_PRODUCE") {
                                modelAbsentExplanation = res.advice
                            } else {
                                classificationResult = res
                                val alert = FreshnessAlert(
                                    crop = res.produceType.ifEmpty { currentProfile.currentCrop.ifEmpty { "Tomato" } },
                                    freshnessScore = res.score,
                                    shelfLifeDays = res.shelfLifeDays,
                                    recommendation = res.advice,
                                    timestamp = System.currentTimeMillis()
                                )
                                val updatedProfile = currentProfile.copy(
                                    freshnessAlerts = currentProfile.freshnessAlerts + alert
                                )
                                profileRepository.updateProfile(updatedProfile, isOnline = false)
                            }
                            isAnalyzing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("analyze_freshness_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2F3E7), contentColor = Color(0xFF1B6B2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (lang) {
                            "mr" -> "ताजेपणाचे विश्लेषण करा"
                            "hi" -> "ताजगी का विश्लेषण करें"
                            else -> "Analyze Freshness"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Loader State
            if (isAnalyzing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF1B6B2F), modifier = Modifier.size(24.dp))
                    Text(
                        text = when (lang) {
                            "mr" -> "माहितीचे विश्लेषण सुरू आहे..."
                            "hi" -> "जानकारी का विश्लेषण हो रहा है..."
                            else -> "Running on-device AI analysis..."
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF1B6B2F)
                    )
                }
            }

            // Model Absent Instructions Panel
            if (modelAbsentExplanation != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("model_absent_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Model Not Found / मॉडेल आढळले नाही",
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = modelAbsentExplanation!!,
                            color = Color(0xFFD32F2F),
                            fontSize = 12.sp
                        )
                        Divider(color = Color(0xFFFFCDD2))
                        Text(
                            text = "Please follow 'MODEL FILE LOCATION' guidelines inside build file config to supply the TFLite file.",
                            fontSize = 11.sp,
                            color = Color(0xFF7F0000)
                        )
                    }
                }
            }

            // Result presentation card
            classificationResult?.let { result ->
                val score = result.score
                val label = result.label
                val confidence = result.confidence

                // Colors: green = 70-100, yellow = 40-69, red = 0-39
                val circleColor = when {
                    score >= 70 -> Color(0xFF4CAF50)
                    score >= 40 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }

                val shelfLife = when {
                    score >= 80 -> "8-10"
                    score >= 50 -> "4-7"
                    score >= 20 -> "2"
                    else -> "0"
                }

                val actionBadge = when {
                    score >= 80 -> "STORE"
                    score >= 50 -> "SELL"
                    else -> "PROCESS IMMEDIATELY"
                }

                val actionLocalized = when (lang) {
                    "mr" -> when (actionBadge) {
                        "STORE" -> "साठवणूक करा"
                        "SELL" -> "बाजारपेठेत विक्री करा"
                        else -> "मूल्यवर्धन प्रक्रिया करा"
                    }
                    "hi" -> when (actionBadge) {
                        "STORE" -> "भंडारण करें"
                        "SELL" -> "मंडी में बेचें"
                        else -> "प्रसंस्करण करें (कोल्ड चैन)"
                    }
                    else -> actionBadge
                }

                val headingLocalized = when (lang) {
                    "mr" -> "विश्लेषण निकाल (Offline TFLite)"
                    "hi" -> "विश्लेषण परिणाम (Offline TFLite)"
                    else -> "Analysis Result (Offline TFLite)"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2F3E7), RoundedCornerShape(18.dp))
                        .testTag("freshness_result_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = headingLocalized,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1B6B2F)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular visual score
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(circleColor.copy(alpha = 0.15f))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$score%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = circleColor
                                    )
                                    Text(
                                        text = "Freshness",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // Info details
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = when (lang) {
                                        "mr" -> "अंदाजे कालमर्यादा: $shelfLife दिवस"
                                        "hi" -> "अनुमानित समय: $shelfLife दिन"
                                        else -> "Shelf-Life: $shelfLife Days"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E271E)
                                )

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = circleColor.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = actionLocalized,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = circleColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = Color(0xFFF1F4F1))

                        // Offline AI dynamic suggestions
                        Text(
                            text = when (lang) {
                                "mr" -> "आपली सद्य माल पातळी पाहता, शेतमाल बाजारात घेऊन जाणे सर्वात जास्त फायदेशीर ठरेल."
                                "hi" -> "आपकी उपज की ताजी स्थिति को देखते हुए मंडी में बेचना सर्वोत्तम होगा।"
                                else -> "Based on your crop health metrics, storing or immediate selling is recommended."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF555F55),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
