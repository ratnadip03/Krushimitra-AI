package com.example.data

/**
 * Single source of truth for farmer language across STT, TTS, Qwen, and voice UX.
 * Initialized from Room profile at app start; never falls back to English unless language is "en".
 */
object AppLanguageManager {

    @Volatile
    var currentLanguage: String = "en"
        private set

    fun normalize(language: String?): String = when (language?.trim()?.lowercase()) {
        "mr" -> "mr"
        "hi" -> "hi"
        "en" -> "en"
        else -> currentLanguage.ifBlank { "en" }
    }

    fun initializeFromProfile(profile: FarmerProfile) {
        currentLanguage = normalize(profile.language)
    }

    fun updateLanguage(language: String) {
        currentLanguage = normalize(language)
    }

    fun languageLock(): String = QwenInferenceService.languageLock(currentLanguage)

    fun localized(mr: String, hi: String, en: String): String = when (currentLanguage) {
        "mr" -> mr
        "hi" -> hi
        else -> en
    }
}
