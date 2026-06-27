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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestTimingScreen(
    profileRepository: FarmerProfileRepository,
    connectivityService: ConnectivityService,
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

    // Query reactive network status
    val isOnline by connectivityService.isOnlineFlow.collectAsState(initial = false)

    // Load Profile Context
    LaunchedEffect(Unit) {
        farmerProfile = profileRepository.getProfileOnce()
    }

    val currentProfile = farmerProfile ?: FarmerProfile()
    val lang = currentProfile.language

    // Interactive Language localization variables
    val titleText = when (lang) {
        "mr" -> "काढणीची योग्य वेळ (Harvest)"
        "hi" -> "कटाई का समय"
        else -> "Harvest Timing"
    }

    val cameraButtonLabel = when (lang) {
        "mr" -> "पिकाचा फोटो घ्या"
        "hi" -> "फसल का फोटो लें"
        else -> "Take Crop Photo"
    }

    val galleryButtonLabel = when (lang) {
        "mr" -> "गॅलरीतून निवडा"
        "hi" -> "गैलरी से चुनें"
        else -> "Choose Gallery"
    }

    val instructions = when (lang) {
        "mr" -> "परिपक्वतेचे प्रमाण आणि काढणीचा अचूक काळ जाणून घेण्यासाठी पिकाचा स्पष्ट फोटो जोडा"
        "hi" -> "परिपक्वता स्तर और कटाई का सही समय जानने के लिए फसल का स्पष्ट फोटो अपलोड करें"
        else -> "Provide a clear picture of your crop pods/fruits to evaluate maturity index and harvest timings"
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            classificationResult = null
            modelAbsentExplanation = null
        }
    }

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
            // Selected Image Preview card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .testTag("harvest_image_card"),
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
                                text = instructions,
                                fontSize = 14.sp,
                                color = Color(0xFF555F55),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Capture Buttons Row
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

            // Run Analysis
            if (selectedBitmap != null && classificationResult == null && !isAnalyzing) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isAnalyzing = true
                            Log.i("KRISHIMITRA_DEBUG", "F06_HARVEST_CALLED")
                            kotlinx.coroutines.delay(1200) // Simulated AI calculation
                            val res = tfliteVisionService.classifyMaturity(selectedBitmap!!)
                            if (res.label == "MODEL_NOT_FOUND") {
                                modelAbsentExplanation = "Model file 'maturity_mobilenetv2.tflite' was not found in assets.\nPath: app/src/main/assets/models/tflite/maturity_mobilenetv2.tflite"
                            } else {
                                classificationResult = res
                            }
                            isAnalyzing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("analyze_harvest_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2F3E7), contentColor = Color(0xFF1B6B2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (lang) {
                            "mr" -> "काढणी काळ शोधा"
                            "hi" -> "कटाई समय की गणना करें"
                            else -> "Estimate Harvest Timing"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Processing Indicator
            if (isAnalyzing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF1B6B2F), modifier = Modifier.size(24.dp))
                    Text(
                        text = when (lang) {
                            "mr" -> "परिपक्वतेचे प्रमाण तपासत आहे..."
                            "hi" -> "परिपक्वता की जांच हो रही है..."
                            else -> "Analyzing maturity index..."
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF1B6B2F)
                    )
                }
            }

            // Model Missing Card
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
                            text = "Maturity Model Not Found / मॉडल उपलब्ध नहीं",
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

            // Display Results
            classificationResult?.let { result ->
                val label = result.label
                val score = result.score

                // Labels: unripe / nearly_ripe / ready_to_harvest / overripe
                val recommendation = when (label) {
                    "unripe" -> when (lang) {
                        "mr" -> "७ ते १० दिवसात काढणी करा"
                        "hi" -> "7-10 दिनों में कटाई करें"
                        else -> "Harvest in 7-10 days"
                    }
                    "nearly_ripe" -> when (lang) {
                        "mr" -> "३ ते ५ दिवसात काढणी करा"
                        "hi" -> "3-5 दिनों में कटाई करें"
                        else -> "Harvest in 3-5 days"
                    }
                    "ready_to_harvest" -> when (lang) {
                        "mr" -> "आता त्वरित काढणी करा"
                        "hi" -> "कटाई अभी करें"
                        else -> "Harvest Now"
                    }
                    else -> when (lang) {
                        "mr" -> "त्वरित काढणी करा (अतिपक्व)"
                        "hi" -> "तुरंत कटाई करें (अधिक परिपक्व)"
                        else -> "Harvest Immediately (Overripe)"
                    }
                }

                val badgeText = when (label) {
                    "unripe" -> "UNRIPE / अपरिपक्व"
                    "nearly_ripe" -> "NEARLY RIPE / अंशतः परिपक्व"
                    "ready_to_harvest" -> "READY TO HARVEST / काढणीस तयार"
                    else -> "OVERRIPE / अतिपक्व"
                }

                val badgeColor = when (label) {
                    "ready_to_harvest" -> Color(0xFF4CAF50)
                    "nearly_ripe" -> Color(0xFFFFC107)
                    "unripe" -> Color(0xFF2196F3)
                    else -> Color(0xFFF44336)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2F3E7), RoundedCornerShape(18.dp))
                        .testTag("harvest_result_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = when (lang) {
                                "mr" -> "पिकाचा परिपक्वता अहवाल"
                                "hi" -> "फसल परिपक्वता रिपोर्ट"
                                else -> "Crop Maturity Report"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1B6B2F),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Badge & Score
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, badgeColor)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Divider(color = Color(0xFFF1F4F1))

                        // Recommendation Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Recommendation icon",
                                tint = Color(0xFF1B6B2F),
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = when (lang) {
                                        "mr" -> "शिफारस:"
                                        "hi" -> "सलाह:"
                                        else -> "Recommendation:"
                                    },
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = recommendation,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF1B6B2F)
                                )
                            }
                        }

                        // Weather Alert Block (Online Context Only)
                        AnimatedVisibility(
                            visible = isOnline,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Weather warning",
                                        tint = Color(0xFFF57F17)
                                    )
                                    Column {
                                        Text(
                                            text = when (lang) {
                                                "mr" -> "हवामान इशारा! (Online Live Update)"
                                                "hi" -> "मौसम चेतावनी! (Online Live Update)"
                                                else -> "Weather Alert! (Online Live Update)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE65100)
                                        )
                                        Text(
                                            text = when (lang) {
                                                "mr" -> "पुढील ४८ तासात पावसाची शक्यता, काढणी झाल्यावर शेतमाल जागीच झाकून ठेवा."
                                                "hi" -> "अगले 48 घंटों में बारिश की संभावना। कटी फसल को सुरक्षित स्थान पर रखें।"
                                                else -> "Rainfall predicted in the next 48 hours. Ensure harvested stocks are stored under tarpaulin sheets."
                                            },
                                            fontSize = 11.sp,
                                            color = Color(0xFFE65100),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Weather Warning (Offline Context Only)
                        AnimatedVisibility(
                            visible = !isOnline,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = "Offline indicator",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when (lang) {
                                        "mr" -> "मॅ्युरिटी-ओन्ली सल्ला (आपण ऑफलाइन आहात)"
                                        "hi" -> "परिपक्वता केवल सलाह (आप ऑफलाइन हैं)"
                                        else -> "Maturity-only advice (You are offline)"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Divider(color = Color(0xFFF1F4F1))

                        // Dynamic advice
                        Text(
                            text = when (lang) {
                                "mr" -> "शेतमाल काढताना धान्यातील ओलावा मोजून घ्या, ज्यामुळे बाजारात चांगला भाव मिळेल आणि साठवणुकीत ताजी राहील."
                                "hi" -> "कटाई करते समय दानों की नमी की मात्रा जांचें, जिससे बाजार मूल्य अच्छा मिले और भंडारण सुरक्षित रहे।"
                                else -> "Check grain moisture levels prior to physical harvesting to ensure premium pricing and minimize mold risks inside silos."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF555F55),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
