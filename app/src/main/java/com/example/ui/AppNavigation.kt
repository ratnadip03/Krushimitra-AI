package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.compose.ui.platform.LocalContext
import com.example.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    profileRepository: FarmerProfileRepository,
    connectivityService: ConnectivityService,
    voiceViewModel: VoiceAssistantViewModel,
    voskSTTService: VoskSTTService,
    piperTTSService: PiperTTSService,
    qwenInferenceService: QwenInferenceService,
    geminiService: GeminiService,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    val voiceNavState by voiceViewModel.uiState.collectAsState()

    LaunchedEffect(voiceNavState.navigateTo) {
        voiceNavState.navigateTo?.let { route ->
            if (route != FeatureRoute.GENERAL) {
                navController.navigate(route.toNavRoute()) {
                    launchSingleTop = true
                }
            }
            voiceViewModel.clearNavigation()
        }
    }

    val context = LocalContext.current
    val tfliteVisionService = remember { TFLiteVisionService(context) }
    val cameraService = remember { CameraService(context) }
    val tesseractOCRService = remember { TesseractOCRService(context) }

    // Start destination logic based on whether custom registration was completed
    LaunchedEffect(Unit) {
        val profile = profileRepository.getProfileOnce()
        if (profile.name.isBlank() || profile.name == "Shetkari Raja") {
            startDestination = "onboarding"
        } else {
            startDestination = "home"
        }
    }

    if (startDestination == null) {
        // Aesthetic Full-Screen Loader
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAFAF7)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1B6B2F))
        }
        return
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Mic FAB is visible on EVERY screen of the app except onboarding in first launch
    val showFab = currentRoute != null && currentRoute != "onboarding"

    VoiceAssistantBottomSheetTrigger(viewModel = voiceViewModel) { showSheet ->
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination!!,
                modifier = Modifier.fillMaxSize()
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        profileRepository = profileRepository,
                        voskSTTService = voskSTTService,
                        piperTTSService = piperTTSService,
                        onOnboardingComplete = {
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }

                composable("home") {
                    MainDashboardScreen(
                        profileRepository = profileRepository,
                        connectivityService = connectivityService,
                        onOpenVoiceAssistant = { showSheet() },
                        onNavigateToFeature = { route ->
                            if (route == "voice") {
                                showSheet()
                            } else {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }

                composable("soil_passport") {
                    SoilPassportScreen(
                        profileRepository = profileRepository,
                        tesseractOCRService = tesseractOCRService,
                        cameraService = cameraService,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("freshness") {
                    FreshnessScreen(
                        profileRepository = profileRepository,
                        tfliteVisionService = tfliteVisionService,
                        cameraService = cameraService,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("harvest_timing") {
                    HarvestTimingScreen(
                        profileRepository = profileRepository,
                        connectivityService = connectivityService,
                        tfliteVisionService = tfliteVisionService,
                        cameraService = cameraService,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("pre_crop") {
                    PreCropScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onNavigateToSoilPassport = { navController.navigate("soil_passport") },
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("crop_rotation") {
                    CropRotationScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("storage_guide") {
                    StorageGuideScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("cold_chain") {
                    ColdChainScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        geminiService = geminiService,
                        connectivityService = connectivityService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("sell_advisor") {
                    SellAdvisorScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onNavigateToFreshness = { navController.navigate("freshness") },
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("market_price") {
                    MarketPriceScreen(
                        profileRepository = profileRepository,
                        connectivityService = connectivityService,
                        geminiService = geminiService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("postharvest_loss") {
                    PostHarvestLossScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("surplus") {
                    SurplusScreen(
                        profileRepository = profileRepository,
                        connectivityService = connectivityService,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("value_addition") {
                    ValueAdditionScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("waste_engine") {
                    WasteEngineScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("carbon_tracker") {
                    CarbonTrackerScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("govt_schemes") {
                    GovtSchemesScreen(
                        profileRepository = profileRepository,
                        qwenInferenceService = qwenInferenceService,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            if (showFab) {
                FloatingActionButton(
                    onClick = { showSheet() },
                    containerColor = Color(0xFF1B6B2F),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .size(56.dp)
                        .testTag("floating_mic_fab")
                ) {
                    val voiceState by voiceViewModel.uiState.collectAsState()
                    Icon(
                        imageVector = if (voiceState.isListening) Icons.Default.MicNone else Icons.Default.Mic,
                        contentDescription = "Voice Assistant FAB Trigger",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
