package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ConnectivityMode {
    ONLINE, OFFLINE
}

enum class VoiceMode { INTERVIEW, ASSISTANT }
enum class InterviewStep { NAME, DISTRICT, STATE, LAND_ACRES, CURRENT_CROP, DONE }

data class VoiceAssistantUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
    val responseText: String = "",
    val currentMode: ConnectivityMode = ConnectivityMode.OFFLINE,
    val error: String? = null,
    val navigateTo: FeatureRoute? = null,
    val isModelDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val voiceMode: VoiceMode = VoiceMode.ASSISTANT,
    val interviewStep: InterviewStep = InterviewStep.NAME
)

class VoiceAssistantViewModel(
    private val context: Context,
    private val profileRepository: FarmerProfileRepository,
    private val connectivityService: ConnectivityService,
    private val voskSTTService: VoskSTTService,
    private val qwenInferenceService: QwenInferenceService,
    private val piperTTSService: PiperTTSService,
    private val geminiService: GeminiService,
    private val onlineSTTService: OnlineSTTService
) : ViewModel() {

    private val TAG = "VoiceAssistantViewModel"
    private var sttJob: Job? = null
    private var ttsObservationJob: Job? = null
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /** Single flag — mic loop runs only while this is true. */
    var isMicActive: Boolean = false
        private set

    private enum class AnnouncementPhase { NONE, ANNOUNCING, AWAITING_USER_CHOICE }
    private enum class GuidedPhase { NONE, INTRO, QUESTIONS, INFERENCE }

    private var announcementPhase = AnnouncementPhase.NONE
    private var guidedPhase = GuidedPhase.NONE
    private var guidedRoute: FeatureRoute? = null
    private var guidedQuestionIndex = 0
    private val guidedAnswers = mutableListOf<String>()
    private var pendingFeatureIntroAfterInterview = false
    private var pendingAutoListen = false

    private val _uiState = MutableStateFlow(VoiceAssistantUiState())
    val uiState: StateFlow<VoiceAssistantUiState> = _uiState.asStateFlow()

    private var lastCapturedSpeech: String = ""
    private var voiceMode: VoiceMode = VoiceMode.ASSISTANT
    private var interviewStep: InterviewStep = InterviewStep.NAME
    private var interviewData = mutableMapOf<InterviewStep, String>()

    private var navigatedInThisTurn = false
    private var pendingNavigationRoute: FeatureRoute? = null
    private var skipAutoListenAfterNav = false

    init {
        viewModelScope.launch {
            connectivityService.isOnlineFlow.collect { online ->
                _uiState.update {
                    it.copy(currentMode = if (online) ConnectivityMode.ONLINE else ConnectivityMode.OFFLINE)
                }
            }
        }
        observeTTSState()
    }

    fun onSheetOpened() {
        activateMicSession()
        checkAndStartVoiceAssistant()
    }

    fun onSheetClosed() {
        deactivateMic()
    }

    fun onAppPaused() {
        deactivateMic()
    }

    fun onAppStopped() {
        deactivateMic()
    }

    private fun activateMicSession() {
        isMicActive = true
        MicSessionController.isMicActive = true
    }

    private fun deactivateMic() {
        isMicActive = false
        MicSessionController.isMicActive = false
        sttJob?.cancel()
        sttJob = null
        voskSTTService.stopListening()
        onlineSTTService.stopListening()
        pendingAutoListen = false
        _uiState.update {
            it.copy(isListening = false, isProcessing = false)
        }
    }

    fun checkAndStartVoiceAssistant() {
        viewModelScope.launch {
            val profile = profileRepository.getProfileOnce()
            AppLanguageManager.initializeFromProfile(profile)
            if (profile.name.isBlank() || profile.name == "Shetkari Raja") {
                voiceMode = VoiceMode.INTERVIEW
                interviewStep = InterviewStep.NAME
                _uiState.update {
                    it.copy(voiceMode = VoiceMode.INTERVIEW, interviewStep = InterviewStep.NAME, error = null)
                }
                val prompt = AppLanguageManager.localized(
                    mr = "नमस्कार! मी कृषीमित्र आहे. सुरुवात करण्यासाठी, तुमचं पूर्ण नाव सांगा.",
                    hi = "नमस्ते! मैं कृषि मित्र हूँ। शुरू करने के लिए, अपना पूरा नाम बताएं।",
                    en = "Hello! I am KrishiMitra. To get started, please tell me your full name."
                )
                speak(prompt)
            } else {
                voiceMode = VoiceMode.ASSISTANT
                _uiState.update { it.copy(voiceMode = VoiceMode.ASSISTANT, error = null) }
                announceAllFeatures()
            }
        }
    }

    fun toggleAssistant(expanded: Boolean) {
        if (expanded) {
            activateMicSession()
            checkAndStartVoiceAssistant()
        } else {
            deactivateMic()
            piperTTSService.stop()
            _uiState.update { it.copy(isListening = false, isSpeaking = false, isProcessing = false) }
        }
    }

    private val voiceExceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Unhandled exception in Speech Scope Flow", exception)
        _uiState.update {
            it.copy(
                isListening = false,
                isProcessing = false,
                isSpeaking = false,
                error = exception.message ?: "An unexpected error occurred in KrishiMitra Assistant."
            )
        }
    }

    fun startListening() {
        if (!isMicActive) {
            activateMicSession()
        }
        if (!_uiState.value.isSpeaking && !_uiState.value.isProcessing) {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!isMicActive) return
        sttJob?.cancel()
        piperTTSService.stop()

        lastCapturedSpeech = ""
        val isOnline = connectivityService.isOnlineOnce()
        val lang = AppLanguageManager.currentLanguage

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _uiState.update {
                it.copy(isListening = false, isProcessing = false, isSpeaking = false, error = "PERMISSION_DENIED_MICROPHONE")
            }
            return
        }

        _uiState.update {
            it.copy(isListening = true, isProcessing = false, isSpeaking = false, error = null)
        }

        sttJob = viewModelScope.launch(voiceExceptionHandler) {
            val flow = if (isOnline) {
                onlineSTTService.startListening(lang)
            } else {
                try {
                    DebugLog.i("STT_OFFLINE_PATH: starting Vosk for $lang")
                    voskSTTService.startListening(lang)
                } catch (e: ModelNotFoundException) {
                    _uiState.update { it.copy(isListening = false, error = "QWEN_NOT_DOWNLOADED") }
                    return@launch
                } catch (e: PermissionException) {
                    _uiState.update { it.copy(isListening = false, error = "PERMISSION_DENIED_MICROPHONE") }
                    return@launch
                }
            }

            flow
                .onCompletion { cause ->
                    sttJob = null
                    _uiState.update { it.copy(isListening = false) }
                    if (cause == null && isMicActive) {
                        processCapturedSpeech()
                    }
                }
                .collect { partial ->
                    handleSpeechCaptured(partial)
                }
        }
    }

    private fun handleSpeechCaptured(text: String) {
        if (text.startsWith("[")) {
            _uiState.update { it.copy(responseText = text) }
        } else {
            lastCapturedSpeech = text
            _uiState.update { it.copy(responseText = text) }
        }
    }

    fun stopListening() {
        if (!_uiState.value.isListening) return
        voskSTTService.stopListening()
        onlineSTTService.stopListening()
        sttJob?.cancel()
        sttJob = null
        _uiState.update { it.copy(isListening = false, isProcessing = true) }
        processCapturedSpeech()
    }

    private fun processCapturedSpeech() {
        if (!isMicActive) {
            _uiState.update { it.copy(isProcessing = false) }
            return
        }

        if (lastCapturedSpeech.isBlank() || lastCapturedSpeech.startsWith("[")) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    responseText = AppLanguageManager.localized(
                        mr = "आवाज ऐकला नाही. पुन्हा बोला.",
                        hi = "आवाज़ नहीं सुना। फिर से बोलिए।",
                        en = "No speech detected. Please try again."
                    )
                )
            }
            return
        }

        when {
            voiceMode == VoiceMode.INTERVIEW -> handleInterviewAnswer(lastCapturedSpeech)
            guidedPhase == GuidedPhase.QUESTIONS -> handleGuidedAnswer(lastCapturedSpeech)
            else -> triggerInference(lastCapturedSpeech)
        }
    }

    private fun isValidInterviewAnswer(answer: String): Boolean {
        if (answer.isBlank() || answer.startsWith("[")) return false
        val lowercase = answer.lowercase()
        return !(lowercase.contains("error") || lowercase.contains("exception") ||
            lowercase.contains("permission") || lowercase.contains("denied"))
    }

    private fun validateInterviewField(step: InterviewStep, answer: String): Boolean {
        val trimmed = answer.trim()
        if (trimmed.isBlank() || trimmed.startsWith("[")) return false
        val lowercase = trimmed.lowercase()
        if (lowercase.contains("error") || lowercase.contains("exception") ||
            lowercase.contains("permission") || lowercase.contains("denied")) return false
        return when (step) {
            InterviewStep.NAME -> {
                val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
                words.size >= 2 && trimmed.length >= 3
            }
            InterviewStep.DISTRICT -> trimmed.length >= 3
            InterviewStep.CURRENT_CROP -> trimmed.length >= 2
            else -> true
        }
    }

    fun submitInterviewAnswer(answer: String) {
        if (!isValidInterviewAnswer(answer)) return
        lastCapturedSpeech = answer
        _uiState.update { it.copy(responseText = answer, error = null) }
        handleInterviewAnswer(answer)
    }

    private fun handleInterviewAnswer(answer: String) {
        if (!isValidInterviewAnswer(answer)) {
            _uiState.update { it.copy(isProcessing = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = false) }
            val profile = profileRepository.getProfileOnce()
            if (!validateInterviewField(interviewStep, answer)) {
                val retryPrompt = when (interviewStep) {
                    InterviewStep.NAME -> AppLanguageManager.localized(
                        mr = "कृपया तुमचे पूर्ण नाव पुन्हा सांगा.",
                        hi = "कृपया अपना पूरा नाम फिर से बताएं।",
                        en = "Please tell me your full name again."
                    )
                    InterviewStep.DISTRICT -> AppLanguageManager.localized(
                        mr = "कृपया योग्य जिल्ह्याचे नाव पुन्हा सांगा.",
                        hi = "कृपया सही जिले का नाम फिर से बताएं।",
                        en = "Please tell me a valid district name again."
                    )
                    InterviewStep.CURRENT_CROP -> AppLanguageManager.localized(
                        mr = "कृपया योग्य पिकाचे नाव पुन्हा सांगा.",
                        hi = "कृपया सही फसल का नाम फिर से बताएं।",
                        en = "Please tell me a valid crop name again."
                    )
                    else -> ""
                }
                if (retryPrompt.isNotEmpty()) speak(retryPrompt)
                pendingAutoListen = true
                return@launch
            }
            when (interviewStep) {
                InterviewStep.NAME -> {
                    interviewData[InterviewStep.NAME] = answer
                    interviewStep = InterviewStep.DISTRICT
                    _uiState.update { it.copy(interviewStep = InterviewStep.DISTRICT) }
                    speak(AppLanguageManager.localized(
                        mr = "तुम्ही कोणत्या जिल्ह्यात राहता?",
                        hi = "आप किस जिले में रहते हैं?",
                        en = "Which district do you live in?"
                    ))
                    pendingAutoListen = true
                }
                InterviewStep.DISTRICT -> {
                    interviewData[InterviewStep.DISTRICT] = answer
                    interviewStep = InterviewStep.STATE
                    _uiState.update { it.copy(interviewStep = InterviewStep.STATE) }
                    speak(AppLanguageManager.localized(
                        mr = "कोणत्या राज्यात?",
                        hi = "कौन सा राज्य?",
                        en = "Which state?"
                    ))
                    pendingAutoListen = true
                }
                InterviewStep.STATE -> {
                    interviewData[InterviewStep.STATE] = answer
                    interviewStep = InterviewStep.LAND_ACRES
                    _uiState.update { it.copy(interviewStep = InterviewStep.LAND_ACRES) }
                    speak(AppLanguageManager.localized(
                        mr = "तुमच्याकडे किती एकर जमीन आहे?",
                        hi = "आपके पास कितने एकड़ ज़मीन है?",
                        en = "How many acres of land do you have?"
                    ))
                    pendingAutoListen = true
                }
                InterviewStep.LAND_ACRES -> {
                    interviewData[InterviewStep.LAND_ACRES] = answer.filter { it.isDigit() || it == '.' }
                    interviewStep = InterviewStep.CURRENT_CROP
                    _uiState.update { it.copy(interviewStep = InterviewStep.CURRENT_CROP) }
                    speak(AppLanguageManager.localized(
                        mr = "सध्या तुम्ही कोणतं पीक घेत आहात?",
                        hi = "अभी आप कौन सी फसल उगा रहे हैं?",
                        en = "What crop are you currently growing?"
                    ))
                    pendingAutoListen = true
                }
                InterviewStep.CURRENT_CROP -> {
                    interviewData[InterviewStep.CURRENT_CROP] = answer
                    interviewStep = InterviewStep.DONE
                    _uiState.update { it.copy(interviewStep = InterviewStep.DONE) }
                    val name = interviewData[InterviewStep.NAME] ?: ""
                    val updatedProfile = profile.copy(
                        name = name,
                        location = profile.location.copy(
                            district = interviewData[InterviewStep.DISTRICT] ?: "",
                            state = interviewData[InterviewStep.STATE] ?: ""
                        ),
                        landAcres = interviewData[InterviewStep.LAND_ACRES]?.toDoubleOrNull() ?: 1.0,
                        currentCrop = interviewData[InterviewStep.CURRENT_CROP] ?: ""
                    )
                    profileRepository.updateProfile(updatedProfile, connectivityService.isOnlineOnce())
                    voiceMode = VoiceMode.ASSISTANT
                    _uiState.update { it.copy(voiceMode = VoiceMode.ASSISTANT) }
                    speak(AppLanguageManager.localized(
                        mr = "धन्यवाद $name! तुमची माहिती जतन केली. आता मी तुम्हाला मदत करू शकतो.",
                        hi = "धन्यवाद $name! आपकी जानकारी सेव हो गई है। अब मैं आपकी मदद कर सकता हूँ।",
                        en = "Thank you $name! Your profile is saved. I can now help you."
                    ))
                    pendingFeatureIntroAfterInterview = true
                }
                InterviewStep.DONE -> {}
            }
        }
    }

    private fun resetVoiceSession() {
        deactivateMic()
        piperTTSService.stop()
        lastCapturedSpeech = ""
        navigatedInThisTurn = false
        pendingNavigationRoute = null
        skipAutoListenAfterNav = false
        announcementPhase = AnnouncementPhase.NONE
        guidedPhase = GuidedPhase.NONE
        guidedRoute = null
        guidedQuestionIndex = 0
        guidedAnswers.clear()
        pendingFeatureIntroAfterInterview = false
        pendingAutoListen = false
        _uiState.update {
            it.copy(isListening = false, isProcessing = false, isSpeaking = false, error = null)
        }
    }

    private fun triggerInference(query: String) {
        viewModelScope.launch(voiceExceptionHandler) {
            val profile = profileRepository.getProfileOnce()
            val detection = VoiceIntentDetector.detect(query)

            when (detection.intent) {
                VoiceIntentDetector.VoiceIntent.HELP -> {
                    announceAllFeatures()
                    return@launch
                }
                VoiceIntentDetector.VoiceIntent.FEATURE -> {
                    beginFeatureFlow(detection.route)
                    return@launch
                }
                VoiceIntentDetector.VoiceIntent.GENERAL -> { /* continue to LLM */ }
            }

            val isOnline = connectivityService.isOnlineOnce()
            if (isOnline) {
                try {
                    val response = geminiService.getVoiceResponse(query, profile, conversationHistory)
                    conversationHistory.add(query to response.reply)
                    processLLMResponse(response)
                } catch (e: GeminiUnavailableException) {
                    Log.w(TAG, "Online Gemini failed. Fallback to offline Qwen: ${e.message}")
                    runOfflineInferenceFallback(query, profile)
                } catch (e: Exception) {
                    Log.e(TAG, "General online failure. Triggering offline fallback.", e)
                    runOfflineInferenceFallback(query, profile)
                }
            } else {
                runOfflineInferenceFallback(query, profile)
            }
        }
    }

    private fun announceAllFeatures() {
        conversationHistory.clear()
        val announcement = FeatureAnnouncements.allFeaturesAnnouncement()
        announcementPhase = AnnouncementPhase.ANNOUNCING
        guidedPhase = GuidedPhase.NONE
        _uiState.update {
            it.copy(isProcessing = false, isSpeaking = true, responseText = announcement)
        }
        navigatedInThisTurn = false
        pendingNavigationRoute = null
        skipAutoListenAfterNav = true
        speak(announcement)
    }

    private fun beginFeatureFlow(route: FeatureRoute) {
        guidedRoute = route
        guidedQuestionIndex = 0
        guidedAnswers.clear()
        guidedPhase = GuidedPhase.INTRO
        announcementPhase = AnnouncementPhase.NONE

        val intro = FeatureAnnouncements.featureDescription(route)
        conversationHistory.add("navigate" to intro)
        _uiState.update {
            it.copy(isProcessing = false, isSpeaking = true, responseText = intro)
        }

        if (route == FeatureRoute.GENERAL) {
            navigatedInThisTurn = false
            skipAutoListenAfterNav = false
            pendingAutoListen = true
            speak(intro)
            return
        }

        pendingNavigationRoute = route
        skipAutoListenAfterNav = true
        navigatedInThisTurn = true
        speak(intro)
    }

    private fun afterFeatureIntro(route: FeatureRoute) {
        handleFeatureRouting(route)

        if (FeatureVoiceGuidance.needsPhoto(route)) {
            guidedPhase = GuidedPhase.NONE
            speak(FeatureVoiceGuidance.photoCameraHint())
            deactivateMic()
            return
        }

        if (FeatureVoiceGuidance.hasGuidedQuestions(route)) {
            guidedPhase = GuidedPhase.QUESTIONS
            guidedQuestionIndex = 0
            askNextGuidedQuestion(route)
            return
        }

        if (route == FeatureRoute.GENERAL) {
            pendingAutoListen = true
            return
        }

        guidedPhase = GuidedPhase.NONE
        deactivateMic()
    }

    private fun askNextGuidedQuestion(route: FeatureRoute) {
        val questions = FeatureVoiceGuidance.guidedQuestions(route)
        if (guidedQuestionIndex >= questions.size) {
            runGuidedInference(route)
            return
        }
        val question = questions[guidedQuestionIndex].forLang()
        _uiState.update { it.copy(isSpeaking = true, responseText = question, isProcessing = false) }
        pendingAutoListen = true
        speak(question)
    }

    private fun handleGuidedAnswer(answer: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = false) }
            val route = guidedRoute ?: return@launch
            guidedAnswers.add(answer)

            var profile = profileRepository.getProfileOnce()
            profile = VoiceGuidedDataHandler.applyAnswersToProfile(route, profile, guidedAnswers)
            profileRepository.updateProfile(profile, connectivityService.isOnlineOnce())

            guidedQuestionIndex++
            val questions = FeatureVoiceGuidance.guidedQuestions(route)
            if (guidedQuestionIndex < questions.size) {
                askNextGuidedQuestion(route)
            } else {
                runGuidedInference(route)
            }
        }
    }

    private fun runGuidedInference(route: FeatureRoute) {
        viewModelScope.launch(voiceExceptionHandler) {
            guidedPhase = GuidedPhase.INFERENCE
            _uiState.update { it.copy(isProcessing = true) }
            val profile = profileRepository.getProfileOnce()
            try {
                val raw = VoiceGuidedDataHandler.runInference(route, profile, guidedAnswers, qwenInferenceService)
                val spoken = VoiceGuidedDataHandler.formatResultForSpeech(route, raw)
                conversationHistory.add("guided" to spoken)
                _uiState.update { it.copy(isProcessing = false, isSpeaking = true, responseText = spoken) }
                guidedPhase = GuidedPhase.NONE
                guidedRoute = null
                guidedAnswers.clear()
                deactivateMic()
                speak(spoken)
            } catch (e: ModelNotDownloadedException) {
                _uiState.update {
                    it.copy(isProcessing = false, isSpeaking = false, error = "QWEN_NOT_DOWNLOADED")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Guided inference failed", e)
                val err = AppLanguageManager.localized(
                    mr = "माफ करा, उत्तर तयार करता आले नाही.",
                    hi = "क्षमा करें, जवाब तैयार नहीं हो सका।",
                    en = "Sorry, could not generate a result."
                )
                _uiState.update { it.copy(isProcessing = false, isSpeaking = true, responseText = err) }
                guidedPhase = GuidedPhase.NONE
                speak(err)
            }
        }
    }

    private suspend fun runOfflineInferenceFallback(query: String, profile: FarmerProfile) {
        try {
            DebugLog.i("VM_CALL: startInference called")
            val response = qwenInferenceService.runInference(query, profile, conversationHistory)
            conversationHistory.add(query to response.reply)
            processLLMResponse(response)
        } catch (e: ModelNotDownloadedException) {
            _uiState.update {
                it.copy(isProcessing = false, isListening = false, isSpeaking = false, isModelDownloading = false, error = "QWEN_NOT_DOWNLOADED")
            }
        }
    }

    fun downloadQwenModelAndResume() {
        _uiState.update { it.copy(isModelDownloading = true, downloadProgress = 0, error = null) }
        viewModelScope.launch(voiceExceptionHandler) {
            qwenInferenceService.downloadQwenModel { progress ->
                _uiState.update { it.copy(downloadProgress = (progress * 0.5).toInt()) }
            }
            voskSTTService.ensureModelsDownloaded { lang, pct ->
                val base = 50
                val offset = when (lang) {
                    "hi" -> 0
                    "mr" -> 17
                    else -> 34
                }
                val overall = base + offset + (pct * 0.16).toInt()
                _uiState.update { it.copy(downloadProgress = overall) }
            }
            _uiState.update {
                it.copy(isModelDownloading = false, responseText = AppLanguageManager.localized(
                    mr = "ऑफलाइन AI डाउनलोड पूर्ण!",
                    hi = "ऑफलाइन AI डाउनलोड पूर्ण!",
                    en = "Offline AI Download Complete!"
                ))
            }
            activateMicSession()
            announceAllFeatures()
        }
    }

    private fun processLLMResponse(response: VoiceResponse) {
        _uiState.update {
            it.copy(isProcessing = false, isSpeaking = true, responseText = response.reply)
        }
        speak(response.reply)

        if (response.featureRoute != FeatureRoute.GENERAL) {
            pendingNavigationRoute = response.featureRoute
            skipAutoListenAfterNav = true
            navigatedInThisTurn = true
            guidedPhase = GuidedPhase.INTRO
            guidedRoute = response.featureRoute
        } else {
            pendingAutoListen = false
            deactivateMic()
        }
    }

    private fun handleFeatureRouting(route: FeatureRoute) {
        if (route == FeatureRoute.GENERAL) {
            navigatedInThisTurn = false
            return
        }
        navigatedInThisTurn = true
        skipAutoListenAfterNav = true
        _uiState.update { it.copy(navigateTo = route) }
    }

    private fun speak(text: String) {
        piperTTSService.speak(text)
    }

    private fun scheduleAutoListen(delayMs: Long = 800L) {
        if (!isMicActive) return
        viewModelScope.launch {
            delay(delayMs)
            if (isMicActive && pendingAutoListen && !_uiState.value.isSpeaking) {
                pendingAutoListen = false
                startListeningInternal()
            }
        }
    }

    private fun observeTTSState() {
        ttsObservationJob?.cancel()
        ttsObservationJob = viewModelScope.launch {
            piperTTSService.ttsStateFlow.collect { state ->
                when (state) {
                    TTSState.PLAYING -> _uiState.update { it.copy(isSpeaking = true) }
                    TTSState.DONE, TTSState.ERROR -> {
                        val wasSpeaking = _uiState.value.isSpeaking
                        _uiState.update { it.copy(isSpeaking = false) }
                        if (!wasSpeaking) return@collect

                        if (pendingFeatureIntroAfterInterview) {
                            pendingFeatureIntroAfterInterview = false
                            announceAllFeatures()
                            return@collect
                        }

                        if (announcementPhase == AnnouncementPhase.ANNOUNCING) {
                            announcementPhase = AnnouncementPhase.AWAITING_USER_CHOICE
                            skipAutoListenAfterNav = false
                            pendingAutoListen = true
                            scheduleAutoListen()
                            return@collect
                        }

                        if (guidedPhase == GuidedPhase.INTRO && guidedRoute != null) {
                            val route = guidedRoute!!
                            afterFeatureIntro(route)
                            return@collect
                        }

                        if (guidedPhase == GuidedPhase.QUESTIONS && pendingAutoListen) {
                            scheduleAutoListen()
                            return@collect
                        }

                        if (voiceMode == VoiceMode.INTERVIEW && pendingAutoListen) {
                            scheduleAutoListen()
                            return@collect
                        }

                        if (pendingNavigationRoute != null && guidedPhase == GuidedPhase.NONE) {
                            pendingNavigationRoute = null
                        }

                        if (pendingAutoListen && isMicActive) {
                            scheduleAutoListen()
                        }
                    }
                }
            }
        }
    }

    fun replayResponseSpeech() {
        val reply = _uiState.value.responseText
        if (reply.isNotBlank() && !reply.startsWith("[")) {
            speak(reply)
        }
    }

    fun clearNavigation() {
        _uiState.update { it.copy(navigateTo = null) }
    }

    fun updateProfileLanguage(lang: String) {
        viewModelScope.launch {
            val normalized = AppLanguageManager.normalize(lang)

            // 1. Save to Room immediately
            val profile = profileRepository.getProfileOnce().copy(language = normalized)
            profileRepository.updateProfile(profile, connectivityService.isOnlineOnce())

            // 2. Update AppLanguageManager
            AppLanguageManager.updateLanguage(normalized)

            // 3. Stop mic
            deactivateMic()

            // 4–5. Unload Vosk / load correct model
            voskSTTService.switchLanguage(normalized)

            // 6–7. Destroy and reinitialize TTS
            piperTTSService.reinitializeForLanguage(normalized)

            // 8. Clear Qwen conversation history
            conversationHistory.clear()
            resetVoiceSession()

            // 9. Re-announce features in new language
            activateMicSession()
            val confirmation = AppLanguageManager.localized(
                mr = "भाषा मराठी वर बदलली. आता मी फक्त मराठीत बोलेन.",
                hi = "भाषा हिंदी में बदल गई। अब मैं केवल हिंदी में बोलूँगा।",
                en = "Language switched to English. I will reply only in English now."
            )
            speak(confirmation)
            pendingFeatureIntroAfterInterview = true
        }
    }

    override fun onCleared() {
        deactivateMic()
        piperTTSService.shutdown()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val profileRepository: FarmerProfileRepository,
        private val connectivityService: ConnectivityService,
        private val voskSTTService: VoskSTTService,
        private val qwenInferenceService: QwenInferenceService,
        private val piperTTSService: PiperTTSService,
        private val geminiService: GeminiService,
        private val onlineSTTService: OnlineSTTService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VoiceAssistantViewModel::class.java)) {
                return VoiceAssistantViewModel(
                    context,
                    profileRepository,
                    connectivityService,
                    voskSTTService,
                    qwenInferenceService,
                    piperTTSService,
                    geminiService,
                    onlineSTTService
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class representation")
        }
    }
}
