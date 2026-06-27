package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { 
    SPLASH, LANGUAGE, ANNOUNCE, NAME, DISTRICT, STATE, LAND_ACRES, CURRENT_CROP, CONFIRM, DONE 
}

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.SPLASH,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val currentAnswer: String = "",
    val name: String = "",
    val language: String = "en",
    val district: String = "",
    val state: String = "Maharashtra",
    val landAcres: String = "",
    val currentCrop: String = ""
)

class OnboardingViewModel(
    private val voskService: VoskSTTService,
    private val piperService: PiperTTSService
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        startStep(OnboardingStep.SPLASH)
    }

    private fun getPrompt(step: OnboardingStep, lang: String): String {
        return when (step) {
            OnboardingStep.SPLASH -> ""
            OnboardingStep.LANGUAGE -> "Welcome to Krishi Mitra AI. Please select your preferred language. English, Hindi, or Marathi?"
            OnboardingStep.ANNOUNCE -> when (lang) {
                "mr" -> "कृषीमित्र मध्ये आपले स्वागत आहे. आम्ही तुम्हाला पीक सल्ला, हवामान, आणि बाजार भाव याबद्दल मदत करू शकतो. चला तुमची माहिती भरूया."
                "hi" -> "कृषि मित्र में आपका स्वागत है। हम आपको फसल सलाह, मौसम और मंडी भाव में मदद कर सकते हैं। चलिए आपकी प्रोफाइल बनाते हैं।"
                else -> "Welcome to KrishiMitra. We can help you with crop advice, weather, and market prices. Let's set up your profile."
            }
            OnboardingStep.NAME -> when (lang) {
                "mr" -> "तुमचं पूर्ण नाव काय आहे?"
                "hi" -> "आपका पूरा नाम क्या है?"
                else -> "What is your full name?"
            }
            OnboardingStep.DISTRICT -> when (lang) {
                "mr" -> "तुम्ही कोणत्या जिल्ह्यात राहता?"
                "hi" -> "आप किस जिले में रहते हैं?"
                else -> "Which district do you live in?"
            }
            OnboardingStep.STATE -> when (lang) {
                "mr" -> "कोणत्या राज्यात?"
                "hi" -> "कौन सा राज्य?"
                else -> "Which state?"
            }
            OnboardingStep.LAND_ACRES -> when (lang) {
                "mr" -> "तुमच्याकडे किती एकर जमीन आहे?"
                "hi" -> "आपके पास कितने एकड़ ज़मीन है?"
                else -> "How many acres of land do you have?"
            }
            OnboardingStep.CURRENT_CROP -> when (lang) {
                "mr" -> "सध्या तुम्ही कोणतं पीक घेत आहात?"
                "hi" -> "अभी आप कौन सी फसल उगा रहे हैं?"
                else -> "What crop are you currently growing?"
            }
            OnboardingStep.CONFIRM -> when (lang) {
                "mr" -> "तुमचे नाव ${_state.value.name}, जिल्हा ${_state.value.district}, राज्य ${_state.value.state}, एकर ${_state.value.landAcres}, पीक ${_state.value.currentCrop}. हे बरोबर आहे का?"
                "hi" -> "आपका नाम ${_state.value.name}, जिला ${_state.value.district}, राज्य ${_state.value.state}, एकड़ ${_state.value.landAcres}, फसल ${_state.value.currentCrop}. क्या यह सही है?"
                else -> "Your name is ${_state.value.name}, district ${_state.value.district}, state ${_state.value.state}, acres ${_state.value.landAcres}, crop ${_state.value.currentCrop}. Is this correct?"
            }
            OnboardingStep.DONE -> "Setup complete."
        }
    }

    fun setLanguage(lang: String) {
        _state.update { it.copy(language = lang) }
    }

    fun stopSpeaking() {
        piperService.stop()
        _state.update { it.copy(isSpeaking = false) }
    }

    fun startStep(step: OnboardingStep) {
        _state.update { it.copy(currentStep = step, currentAnswer = "") }
        val prompt = getPrompt(step, _state.value.language)
        
        if (step == OnboardingStep.SPLASH || step == OnboardingStep.DONE) return

        viewModelScope.launch {
            _state.update { it.copy(isSpeaking = true, isListening = false) }
            piperService.speak(prompt, _state.value.language)
            _state.update { it.copy(isSpeaking = false, isListening = true) }
            
            if (step != OnboardingStep.CONFIRM && step != OnboardingStep.ANNOUNCE) {
                try {
                    voskService.startListening(_state.value.language).collect { text ->
                        _state.update { it.copy(currentAnswer = text, isListening = false) }
                    }
                } catch (e: Exception) {
                    // Ignore for now, wait for permission grant
                    _state.update { it.copy(isListening = false) }
                }
            }
        }
    }
    
    fun retryListening() {
        if (_state.value.currentStep != OnboardingStep.CONFIRM && _state.value.currentStep != OnboardingStep.DONE && _state.value.currentStep != OnboardingStep.SPLASH && _state.value.currentStep != OnboardingStep.ANNOUNCE) {
            viewModelScope.launch {
                _state.update { it.copy(isListening = true) }
                try {
                    voskService.startListening(_state.value.language).collect { text ->
                        _state.update { it.copy(currentAnswer = text, isListening = false) }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(isListening = false) }
                }
            }
        }
    }
}
