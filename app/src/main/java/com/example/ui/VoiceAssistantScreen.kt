package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.FeatureRoute
import kotlinx.coroutines.flow.asStateFlow

// Primary color defined as requested
val KrishiGreenPrimary = Color(0xFF1B6B2F)
val SoftBackground = Color(0xFFF4F9F4)
val DarkBackgroundText = Color(0xFF1E271E)

@Composable
fun VoiceAssistantScreen(
    viewModel: VoiceAssistantViewModel,
    modifier: Modifier = Modifier,
    onNavigateToFeature: (FeatureRoute) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startListening()
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.onSheetOpened()
    }

    // Observe navigation triggers
    LaunchedEffect(state.navigateTo) {
        state.navigateTo?.let { route ->
            onNavigateToFeature(route)
            viewModel.clearNavigation()
        }
    }

    // Dynamic localized strings map
    val currentLang = when (state.error) {
        "QWEN_NOT_DOWNLOADED" -> "en" // force to show dialog language simply
        else -> state.responseText.let { text ->
            if (text.startsWith("[")) {
                // Keep english state signals
                "en"
            } else {
                // dynamically fetch language from context or local store
                "en" // default
            }
        }
    }

    // Resolve current farmer language
    val resolvedLang = when {
        state.responseText.any { it.code in 0x0900..0x097F } -> {
            if (state.responseText.contains("करतो") || state.responseText.contains("माती") || state.responseText.contains("सांगतो")) "mr" else "hi"
        }
        else -> "en"
    }

    val idleText = when (resolvedLang) {
        "mr" -> "बोलण्यासाठी दाबा"
        "hi" -> "बोलने के लिए दबाएं"
        else -> "Tap to speak"
    }
    val listeningText = when (resolvedLang) {
        "mr" -> "ऐकतोय..."
        "hi" -> "सुन रहा हूँ..."
        else -> "Listening..."
    }
    val processingText = when (resolvedLang) {
        "mr" -> "विचार करतोय..."
        "hi" -> "सोच रहा हूँ..."
        else -> "Thinking..."
    }
    val speakingText = when (resolvedLang) {
        "mr" -> "सांगतोय..."
        "hi" -> "बता रहा हूँ..."
        else -> "Speaking..."
    }

    val statusText = when {
        state.isListening -> listeningText
        state.isProcessing -> processingText
        state.isSpeaking -> speakingText
        else -> idleText
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = SoftBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // [TOP BAR] - Connectivity & Languages
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connectivity Badge
                val isOnline = state.currentMode == ConnectivityMode.ONLINE
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isOnline) Color(0xFFE2F3E7) else Color(0xFFFDE8E8))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("connectivity_badge")
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Language Selectors
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val languages = listOf("mr" to "मराठी", "hi" to "हिंदी", "en" to "EN")
                    languages.forEach { (code, label) ->
                        val isSelected = resolvedLang == code || (code == "en" && resolvedLang != "mr" && resolvedLang != "hi")
                        val backgroundColor = if (isSelected) KrishiGreenPrimary else Color.White
                        val textColor = if (isSelected) Color.White else DarkBackgroundText
                        val chipBorder = if (isSelected) 0.dp else 1.dp

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(backgroundColor)
                                .clickable { viewModel.updateProfileLanguage(code) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("language_chip_$code"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // [CENTER MAIN MIC AREA]
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Audio Wave Pulse Simulation
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = if (state.isListening) 1.25f else 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = twinPulseAnimation(),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                // Color Configuration State machine
                val micColor by animateColorAsState(
                    targetValue = when {
                        state.isListening -> Color(0xFFD32F2F)  // Alert red pulsing
                        state.isProcessing || state.isSpeaking -> Color.Gray
                        else -> KrishiGreenPrimary               // Rich farming green
                    },
                    label = "mic_color"
                )

                Box(
                    modifier = Modifier
                        .size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Ripple Ring
                    if (state.isListening) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFFFFCDD2).copy(alpha = 0.5f))
                        )
                    }

                    // Main Button Core
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(micColor)
                            .clickable(enabled = !state.isSpeaking && !state.isProcessing) {
                                if (state.isListening) {
                                    viewModel.stopListening()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.startListening()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            .testTag("mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isListening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Microphone Button Trigger",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Microphone Status Text Indicator
                Text(
                    text = statusText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBackgroundText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("status_indicator_text")
                )
            }

            // [BOTTOM CARD - RESPONSE AREA]
            val hasValidText = state.responseText.isNotBlank()
            if (hasValidText) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("response_text_card"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.responseText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkBackgroundText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Action Row
                        if (!state.responseText.startsWith("[")) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFFEAEAEA))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.replayResponseSpeech() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("replay_voice_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Replay response voice",
                                        tint = KrishiGreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error display / Offline setup guides
            state.error?.let { err ->
                if (err == "QWEN_NOT_DOWNLOADED") {
                    OneTimeDownloadDialog(
                        onDownloadStart = { viewModel.downloadQwenModelAndResume() }
                    )
                } else if (!err.startsWith("Switched")) {
                    Text(
                        text = "Error: $err",
                        color = Color.Red,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }

            // Simulated progress for downloading models
            if (state.isModelDownloading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Downloading offline AI Engine (${state.downloadProgress}%)...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = state.downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = KrishiGreenPrimary,
                            trackColor = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

private fun twinPulseAnimation(): KeyframesSpec<Float> {
    return keyframes {
        durationMillis = 1000
        1.0f at 0 with LinearEasing
        1.15f at 500 with FastOutSlowInEasing
        1.25f at 1000 with LinearOutSlowInEasing
    }
}

@Composable
fun OneTimeDownloadDialog(onDownloadStart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(
                onClick = onDownloadStart,
                colors = ButtonDefaults.buttonColors(containerColor = KrishiGreenPrimary)
            ) {
                Text("Download Now")
            }
        },
        title = {
            Text(
                "Offline AI Engine Download Required",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                "KrishiMitra offline AI needs an initial 270MB download to work everywhere without internet connectivity. Connect to WiFi and tap Download to start immediately.",
                fontSize = 14.sp
            )
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

/**
 * FAB Bottom Sheet Trigger Wrapper available for inclusion on all other 15 feature screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantBottomSheetTrigger(
    viewModel: VoiceAssistantViewModel,
    content: @Composable (showSheet: () -> Unit) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    content { showBottomSheet = true }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = SoftBackground,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.LightGray) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.70f) // neat height covering standard display rules
                    .fillMaxWidth()
            ) {
                VoiceAssistantScreen(
                    viewModel = viewModel,
                    onNavigateToFeature = {
                        showBottomSheet = false
                    }
                )
            }
        }
    }
}
