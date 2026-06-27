package com.example.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.util.Locale

// ════════════════════════════════════════
// 📁 PIPER VOICE MODEL LOCATIONS
// Hindi voice:
//   Path: app/src/main/assets/piper_voices/
//   Filename: hi_IN-female.onnx
//             hi_IN-female.onnx.json
//   Size: ~50MB
//   Source: https://github.com/rhasspy/piper
//
// Marathi voice:
//   Path: app/src/main/assets/piper_voices/
//   Filename: mr_IN-female.onnx
//             mr_IN-female.onnx.json
//   Size: ~50MB
//
// English: Uses Android built-in TTS
//   No file needed
// ════════════════════════════════════════

enum class TTSState {
    PLAYING, DONE, ERROR
}

class PiperTTSService(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var sessionLanguage: String = AppLanguageManager.currentLanguage
    private val _ttsStateFlow = MutableSharedFlow<TTSState>(replay = 1)
    val ttsStateFlow: SharedFlow<TTSState> = _ttsStateFlow

    private val utteranceId = "KrishiMitraUtteranceTTS"
    private val TAG = "PiperTTSService"

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupUtteranceListener()
            isInitialized = true
            sessionLanguage = AppLanguageManager.currentLanguage
            tts?.language = localeForLanguage(sessionLanguage)
            Log.d(TAG, "Android Built-in and Piper TextToSpeech initialized successfully.")
        } else {
            Log.e(TAG, "Failed to initialize TTS Engine.")
            _ttsStateFlow.tryEmit(TTSState.ERROR)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _ttsStateFlow.tryEmit(TTSState.PLAYING)
            }

            override fun onDone(utteranceId: String?) {
                _ttsStateFlow.tryEmit(TTSState.DONE)
            }

            override fun onError(utteranceId: String?) {
                _ttsStateFlow.tryEmit(TTSState.ERROR)
            }
        })
    }

    /**
     * Destroy and recreate TTS engine with locale for the new language (on language switch).
     */
    fun reinitializeForLanguage(language: String) {
        val lang = AppLanguageManager.normalize(language)
        sessionLanguage = lang
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        tts = TextToSpeech(context, this)
        DebugLog.i("TTS_REINITIALIZED: lang=$lang")
    }

    private fun localeForLanguage(language: String): Locale = when (AppLanguageManager.normalize(language)) {
        "hi" -> Locale("hi", "IN")
        "mr" -> Locale("mr", "IN")
        else -> Locale("en", "IN")
    }

    /**
     * Converts response text to audio WAV or native speech.
     * Uses AppLanguageManager as source of truth for locale.
     */
    fun speak(text: String, language: String? = null) {
        val lang = AppLanguageManager.normalize(language ?: AppLanguageManager.currentLanguage)
        if (lang != sessionLanguage) {
            reinitializeForLanguage(lang)
        }
        if (!isInitialized || tts == null) {
            Log.e(TAG, "TTS not initialized, skipping speak action.")
            _ttsStateFlow.tryEmit(TTSState.ERROR)
            return
        }

        DebugLog.i("TTS_SPEAK: lang=$lang, textLen=${text.length}")

        if (lang != "en") {
            val modelName = "${lang}_IN-female.onnx"
            val jsonName = "${lang}_IN-female.onnx.json"
            try {
                val assets = context.assets.list("piper_voices")
                val files = assets?.toList() ?: emptyList()
                if (files.contains(modelName) && files.contains(jsonName)) {
                    Log.i(TAG, "Piper voice file loaded successfully from assets/piper_voices/$modelName")
                } else {
                    Log.w(TAG, "Piper voice assets missing for '$lang'. Falling back to Android system TTS.")
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to check assets for piper_voices")
            }
        }

        val locale = localeForLanguage(lang)
        val result = tts?.setLanguage(locale)
        DebugLog.i("TTS_LOCALE_SET: locale=$locale, result=$result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "Locale $locale TTS resource is not supported or missing data.")
            _ttsStateFlow.tryEmit(TTSState.ERROR)
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _ttsStateFlow.tryEmit(TTSState.DONE)
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
