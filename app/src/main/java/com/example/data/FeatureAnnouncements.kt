package com.example.data

/**
 * TTS announcements for voice navigation — delegates to FeatureVoiceGuidance + AppLanguageManager.
 */
object FeatureAnnouncements {

    fun featureDescription(route: FeatureRoute, lang: String = AppLanguageManager.currentLanguage): String =
        FeatureVoiceGuidance.featureIntro(route, lang)

    fun featureSelectionQuestion(lang: String = AppLanguageManager.currentLanguage): String =
        AppLanguageManager.localized(
            mr = "तुम्हाला कशात मदत हवी आहे?",
            hi = "आप किस चीज़ में मदद चाहते हैं?",
            en = "Which feature do you want help with?"
        )

    fun allFeaturesAnnouncement(lang: String = AppLanguageManager.currentLanguage): String =
        FeatureVoiceGuidance.fullMicOpeningAnnouncement(lang)
}
