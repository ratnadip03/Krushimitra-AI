package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

class ModelNotFoundException(message: String) : Exception(message)
class PermissionException(message: String) : Exception(message)

class VoskSTTService(private val context: Context) {

    private var currentAudioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var loadedModelLang: String? = null

    @Volatile
    private var activeModel: org.vosk.Model? = null

    @Volatile
    private var activeRecognizer: org.vosk.Recognizer? = null

    private fun resolveModelLang(language: String): String {
        val normalized = AppLanguageManager.normalize(language)
        return when (normalized) {
            "mr", "hi", "en" -> normalized
            else -> AppLanguageManager.currentLanguage
        }
    }

    /**
     * Stop current session, unload Vosk model, and prepare for a new language model.
     */
    fun switchLanguage(newLanguage: String) {
        stopListening()
        unloadModel()
        loadedModelLang = null
        DebugLog.i("VOSK_LANGUAGE_SWITCH: target=${resolveModelLang(newLanguage)}")
    }

    private fun unloadModel() {
        try {
            activeRecognizer?.close()
        } catch (e: Exception) {
            Log.e("VoskSTTService", "Error closing recognizer", e)
        }
        activeRecognizer = null
        try {
            activeModel?.close()
        } catch (e: Exception) {
            Log.e("VoskSTTService", "Error closing model", e)
        }
        activeModel = null
        loadedModelLang = null
    }

    suspend fun ensureModelsDownloaded(
        onProgress: (lang: String, pct: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val langs = mapOf(
            "hi" to "models/vosk/vosk-model-small-hi-0.22.zip",
            "en" to "models/vosk/vosk-model-small-en-us-0.15.zip",
            "mr" to "models/vosk/vosk-model-small-mr-0.42.zip"
        )
        for ((lang, assetPath) in langs) {
            val modelDir = File(context.filesDir, "vosk_models/$lang")
            if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
                DebugLog.i("VOSK_${lang.uppercase()}_READY")
                continue
            }
            
            // Copy zip from assets to temp file
            val tempZipFile = File(context.filesDir, "temp_vosk_$lang.zip")
            DebugLog.i("VOSK_COPYING_FROM_ASSETS: $lang from assets/$assetPath")
            
            val totalBytes = try {
                val afd = context.assets.openFd(assetPath)
                val len = afd.length
                afd.close()
                len
            } catch (e: Exception) {
                if (lang == "hi") 44458845L else 41205931L
            }

            var copiedBytes = 0L
            context.assets.open(assetPath).use { input ->
                tempZipFile.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead = input.read(buffer)
                    while (bytesRead >= 0) {
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead
                        val pct = if (totalBytes > 0) ((copiedBytes * 100) / totalBytes).toInt() else 0
                        onProgress(lang, pct)
                        bytesRead = input.read(buffer)
                    }
                }
            }

            // Unzip from temp file to modelDir
            modelDir.mkdirs()
            java.util.zip.ZipInputStream(tempZipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(modelDir, entry.name.substringAfter("/")) // strip top folder
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            tempZipFile.delete()
            DebugLog.i("VOSK_${lang.uppercase()}_INSTALLED: ${modelDir.absolutePath}")
        }
    }

    private fun extractModelFromAssetsIfNeeded(lang: String) {
        val modelDir = File(context.filesDir, "vosk_models/$lang")
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
            return
        }

        val assetPath = when(lang) {
            "hi" -> "models/vosk/vosk-model-small-hi-0.22.zip"
            "en" -> "models/vosk/vosk-model-small-en-us-0.15.zip"
            "mr" -> "models/vosk/vosk-model-small-mr-0.42.zip"
            else -> throw IllegalArgumentException("Unsupported language model: $lang")
        }

        Log.i("VoskSTTService", "Extracting model $lang from assets $assetPath...")
        val tempZipFile = File(context.filesDir, "temp_vosk_auto_$lang.zip")
        try {
            context.assets.open(assetPath).use { input ->
                tempZipFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }

            modelDir.mkdirs()
            java.util.zip.ZipInputStream(tempZipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(modelDir, entry.name.substringAfter("/")) // strip top folder
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            Log.i("VoskSTTService", "Vosk model $lang successfully extracted to ${modelDir.absolutePath}")
        } catch (e: Exception) {
            Log.e("VoskSTTService", "Failed to extract model $lang from assets", e)
            modelDir.deleteRecursively()
            throw e
        } finally {
            tempZipFile.delete()
        }
    }

    fun startListening(
        language: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val modelLang = resolveModelLang(language)
        try {
            extractModelFromAssetsIfNeeded(modelLang)
        } catch (e: Exception) {
            onError("Failed to load local speech model: ${e.message}")
            return
        }
        val modelPath = "${context.filesDir}/vosk_models/$modelLang"
        
        if (!File(modelPath).exists()) {
            onError("Vosk model not found.")
            return
        }
        
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError("Microphone permission not granted")
            return
        }

        if (loadedModelLang != null && loadedModelLang != modelLang) {
            unloadModel()
        }

        val model = if (loadedModelLang == modelLang && activeModel != null) {
            activeModel!!
        } else {
            unloadModel()
            org.vosk.Model(modelPath).also {
                activeModel = it
                loadedModelLang = modelLang
                DebugLog.i("VOSK_MODEL_LOADED: lang=$modelLang")
            }
        }
        val recognizer = org.vosk.Recognizer(model, 16000.0f)
        activeRecognizer = recognizer
        
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT) * 2
        )
        
        currentAudioRecord?.stop()
        currentAudioRecord?.release()
        currentAudioRecord = audioRecord
        isRecording = true
        
        audioRecord.startRecording()
        DebugLog.i( "VOSK_RECORDING_STARTED: lang=$modelLang")
        
        recordingJob?.cancel()
        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(1024)
            var speechDetected = false
            var silenceCounter = 0
            val SILENCE_FRAMES_NEEDED = 15
            
            while (isRecording && MicSessionController.isMicActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rms = sqrt(buffer.take(read)
                        .map { it.toDouble().pow(2) }
                        .average()).toFloat()
                    
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val result = JSONObject(recognizer.result)
                            .getString("text").trim()
                        if (result.isNotEmpty()) {
                            DebugLog.i( "VOSK_FINAL: $result")
                            withContext(Dispatchers.Main) { onFinalResult(result) }
                            stopListening()
                            break
                        }
                    } else {
                        val partial = JSONObject(recognizer.partialResult)
                            .getString("partial").trim()
                        if (partial.isNotEmpty()) {
                            speechDetected = true
                            silenceCounter = 0
                            withContext(Dispatchers.Main) { onPartialResult(partial) }
                        } else if (speechDetected) {
                            silenceCounter++
                            if (silenceCounter >= SILENCE_FRAMES_NEEDED) {
                                // 1200ms silence after speech — auto stop
                                val final = JSONObject(recognizer.finalResult)
                                    .getString("text").trim()
                                DebugLog.i( "VOSK_SILENCE_STOP: $final")
                                withContext(Dispatchers.Main) {
                                    if (final.isNotEmpty()) onFinalResult(final)
                                    else onError("Could not understand. Please try again.")
                                }
                                stopListening()
                                break
                            }
                        }
                    }
                }
            }
            recognizer.close()
            activeRecognizer = null
        }
    }
    
    fun stopListening() {
        isRecording = false
        try {
            if (currentAudioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                currentAudioRecord?.stop()
            }
        } catch (e: Exception) {
            Log.e("VoskSTTService", "Error stopping AudioRecord", e)
        }
        try {
            currentAudioRecord?.release()
        } catch (e: Exception) {
            Log.e("VoskSTTService", "Error releasing AudioRecord", e)
        }
        currentAudioRecord = null
        recordingJob?.cancel()
        recordingJob = null
        try {
            activeRecognizer?.close()
        } catch (e: Exception) {
            Log.e("VoskSTTService", "Error closing recognizer on stop", e)
        }
        activeRecognizer = null
    }

    /**
     * Start on-device recording & speech-to-text decoding using Vosk.
     * Returns a Flow emitting the recognized text chunks for compatibility with ViewModel/Onboarding.
     */
    fun startListening(language: String): Flow<String> = callbackFlow {
        val modelLang = resolveModelLang(language)
        try {
            extractModelFromAssetsIfNeeded(modelLang)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val modelPath = "${context.filesDir}/vosk_models/$modelLang"
        if (!File(modelPath).exists()) {
            throw ModelNotFoundException("Vosk model not found.")
        }
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw PermissionException("Microphone permission not granted for STT")
        }

        startListening(
            language = language,
            onPartialResult = { partial ->
                trySend(partial)
            },
            onFinalResult = { final ->
                trySend(final)
                close()
            },
            onError = { error ->
                close(Exception(error))
            }
        )

        awaitClose {
            stopListening()
        }
    }.flowOn(Dispatchers.IO)
}
