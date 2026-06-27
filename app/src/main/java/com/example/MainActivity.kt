package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.VoiceAssistantScreen
import com.example.ui.VoiceAssistantViewModel
import com.example.ui.AppNavigation

import androidx.activity.viewModels
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private lateinit var voiceViewModel: VoiceAssistantViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Instantiate lightweight singleton services
    val profileRepository = AppModule.provideFarmerProfileRepository(applicationContext)
    val connectivityService = AppModule.provideConnectivityService(applicationContext)
    val voskSTTService = VoskSTTService(applicationContext)
    val qwenInferenceService = QwenInferenceService(applicationContext)
    val piperTTSService = PiperTTSService(applicationContext)
    val geminiService = GeminiService(applicationContext, qwenInferenceService)
    val onlineSTTService = OnlineSTTService(applicationContext)

    lifecycleScope.launch {
      val profile = profileRepository.getProfileOnce()
      AppLanguageManager.initializeFromProfile(profile)
    }

    val viewModelFactory = VoiceAssistantViewModel.Factory(
        applicationContext,
        profileRepository,
        connectivityService,
        voskSTTService,
        qwenInferenceService,
        piperTTSService,
        geminiService,
        onlineSTTService
    )

    voiceViewModel = VoiceAssistantViewModel(
        applicationContext,
        profileRepository,
        connectivityService,
        voskSTTService,
        qwenInferenceService,
        piperTTSService,
        geminiService,
        onlineSTTService
    )

    setContent {
      MyApplicationTheme {
        AppNavigation(
            profileRepository = profileRepository,
            connectivityService = connectivityService,
            voiceViewModel = voiceViewModel,
            voskSTTService = voskSTTService,
            piperTTSService = piperTTSService,
            qwenInferenceService = qwenInferenceService,
            geminiService = geminiService
        )
      }
    }
  }

  override fun onPause() {
    super.onPause()
    if (::voiceViewModel.isInitialized) {
      voiceViewModel.onAppPaused()
    }
  }

  override fun onStop() {
    super.onStop()
    if (::voiceViewModel.isInitialized) {
      voiceViewModel.onAppStopped()
    }
  }
}    


