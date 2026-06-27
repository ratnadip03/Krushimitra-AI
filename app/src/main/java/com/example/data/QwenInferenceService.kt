package com.example.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import android.content.res.AssetFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nehuatl.llamacpp.LlamaHelper
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred

// ════════════════════════════════════════
// 📁 MODEL FILE LOCATION
// Place your model file here:
// Path: app/src/main/assets/models/llm/
// Expected filename: Qwen2.5-0.5B-Instruct-Q4_K_M.gguf
// Expected size: ~397MB
// Source: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
// ════════════════════════════════════════

class ModelNotDownloadedException(message: String) : Exception(message)

class QwenInferenceService(private val context: Context) {

    private val TAG = "QwenInferenceService"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val responseAdapter = moshi.adapter(VoiceResponse::class.java)

    companion object {
        private const val MODEL_FILENAME = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
        private const val MODEL_ASSET_PATH = "models/llm/$MODEL_FILENAME"
        private const val INTERNAL_MODEL_DIR = "models/llm"

        const val SHORT_RESPONSE_PREFIX =
            "Reply in maximum 3 sentences. Be direct and specific. No introductions. No repeating the question.\n"

        const val VOICE_SHORT_RESPONSE_PREFIX =
            "Reply in maximum 2 sentences. Be direct and specific. No introductions. No repeating the question.\n"

        fun languageLock(language: String): String = when (AppLanguageManager.normalize(language)) {
            "mr" -> "तुम्ही फक्त मराठीत उत्तर द्यायचे आहे.\n" +
                "एकही शब्द इंग्रजी किंवा हिंदीत वापरू नका.\n" +
                "जर तुम्हाला उत्तर माहीत नसेल तरी मराठीतच सांगा."
            "hi" -> "आपको केवल हिंदी में जवाब देना है.\n" +
                "एक भी शब्द अंग्रेजी या मराठी में मत बोलो.\n" +
                "अगर जवाब नहीं पता तो भी हिंदी में बोलो."
            else -> "Reply only in English. Never use Hindi or Marathi."
        }

        fun buildFarmerContextBlock(profile: FarmerProfile, extraContext: String = ""): String = buildString {
            appendLine("Farmer name: ${profile.name}")
            appendLine("Location: ${profile.location.district}, ${profile.location.state}")
            appendLine("Current crop: ${profile.currentCrop}")
            appendLine("Soil type: N=${profile.soil.N}, P=${profile.soil.P}, K=${profile.soil.K}, pH=${profile.soil.pH}, health=${profile.soil.healthScore}/100")
            appendLine("Land size: ${profile.landAcres} acres")
            appendLine("Language: ${AppLanguageManager.normalize(profile.language)}")
            if (profile.soil.N.isNotBlank() || profile.soil.healthScore > 0) {
                appendLine("Saved soil NPK: N=${profile.soil.N}, P=${profile.soil.P}, K=${profile.soil.K}, pH=${profile.soil.pH}")
            }
            if (profile.cachedWeather.lastUpdated.isNotBlank()) {
                appendLine("Saved weather: ${profile.cachedWeather.conditionText}, ${profile.cachedWeather.tempC}°C, humidity ${profile.cachedWeather.humidity}%")
            }
            if (profile.cachedMandiPrice.modalPrice > 0) {
                appendLine("Saved mandi price: ${profile.cachedMandiPrice.commodity} ₹${profile.cachedMandiPrice.modalPrice}/Q at ${profile.cachedMandiPrice.nearestMarket.ifEmpty { profile.cachedMandiPrice.market }}, trend ${profile.cachedMandiPrice.trend}")
            }
            if (profile.freshnessAlerts.isNotEmpty()) {
                val latest = profile.freshnessAlerts.last()
                appendLine("Saved freshness: ${latest.crop} score ${latest.freshnessScore}/100, shelf life ${latest.shelfLifeDays} days")
            }
            if (extraContext.isNotBlank()) {
                append(extraContext.trim())
            }
        }.trimEnd()

        // Singleton model handle, shared across all callers
        @Volatile
        private var llamaHelper: LlamaHelper? = null

        @Volatile
        private var isModelLoaded: Boolean = false

        @Volatile
        private var isModelLoading: Boolean = false

        private val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
            extraBufferCapacity = 128,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        private val inferenceMutex = Mutex()
    }

    /**
     * Executes the text Qwen 0.5B on-device inference using the local llama.cpp / GGUF model.
     * Throws ModelNotDownloadedException if the actual file is not loaded.
     */
    suspend fun runInference(query: String, profile: FarmerProfile, conversationHistory: List<Pair<String,String>> = emptyList()): VoiceResponse = withContext(Dispatchers.IO) {
        ensureModelLoaded()

        val systemPrompt = buildSystemPrompt(profile, query, conversationHistory)

        try {
            val rawOutput = runNativeInference(systemPrompt, query)
            Log.d(TAG, "Raw LLM output: $rawOutput")

            val jsonResponse = extractJsonFromOutput(rawOutput)
            parseJsonResponse(jsonResponse)
        } catch (e: ModelNotDownloadedException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LLM inference failed, using error fallback", e)
            VoiceResponse(
                AppLanguageManager.localized(
                    mr = "माफ करा, AI मॉडेलमध्ये त्रुटी आली. पुन्हा प्रयत्न करा.",
                    hi = "क्षमा करें, AI मॉडल में त्रुटि हुई। फिर से प्रयास करें।",
                    en = "I apologize, the AI model encountered an error. Please try again."
                ),
                FeatureRoute.GENERAL
            )
        }
    }

    /**
     * Run a query with a custom system prompt.
     */
    suspend fun runCustomInference(systemPrompt: String, query: String, profile: FarmerProfile? = null): String = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        val freshProfile = profile ?: AppModule.provideFarmerProfileRepository(context).getProfileOnce()
        val enrichedPrompt = buildPromptWithLanguageLock(AppLanguageManager.currentLanguage, prependShortPrompt(systemPrompt, forVoice = false))
        try {
            val rawOutput = runNativeInference(enrichedPrompt, query)
            Log.d(TAG, "Custom inference raw: $rawOutput")
            extractJsonFromOutput(rawOutput)
        } catch (e: ModelNotDownloadedException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom inference failed", e)
            throw e
        }
    }

    /**
     * Feature-specific inference with short prompt and profile context (F01–F16).
     */
    suspend fun runFeatureInferenceForFeature(
        feature: FeatureRoute,
        featurePrompt: String,
        profile: FarmerProfile
    ): String = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        logFeatureCall(feature, profile, featurePrompt)

        val roleLine = when (feature) {
            FeatureRoute.SOIL_PASSPORT -> "You are KrishiMitra AI, a soil health advisor for Indian farmers."
            FeatureRoute.PRE_CROP -> "You are KrishiMitra AI, a pre-crop suitability advisor."
            FeatureRoute.CROP_ROTATION -> "You are KrishiMitra AI, a crop rotation planner."
            FeatureRoute.FRESHNESS -> "You are KrishiMitra AI, a produce freshness advisor."
            FeatureRoute.STORAGE_GUIDE -> "You are KrishiMitra AI, a produce storage advisor."
            FeatureRoute.HARVEST_TIMING -> "You are KrishiMitra AI, a harvest timing advisor."
            FeatureRoute.COLD_CHAIN -> "You are KrishiMitra AI, a cold chain advisor."
            FeatureRoute.SELL_ADVISOR -> "You are KrishiMitra AI, a sell-timing advisor."
            FeatureRoute.MARKET_PRICE -> "You are KrishiMitra AI, a mandi price advisor."
            FeatureRoute.POSTHARVEST_LOSS -> "You are KrishiMitra AI, a post-harvest loss advisor."
            FeatureRoute.SURPLUS -> "You are KrishiMitra AI, a surplus crop exchange advisor."
            FeatureRoute.VALUE_ADDITION -> "You are KrishiMitra AI, a value-addition advisor."
            FeatureRoute.WASTE_ENGINE -> "You are KrishiMitra AI, an agri-waste revenue advisor."
            FeatureRoute.CARBON_TRACKER -> "You are KrishiMitra AI, a farm carbon tracker."
            FeatureRoute.GOVT_SCHEMES -> "You are KrishiMitra AI, a government scheme advisor."
            FeatureRoute.GENERAL -> "You are KrishiMitra AI, a farming assistant."
        }

        val freshProfile = AppModule.provideFarmerProfileRepository(context).getProfileOnce()
            .let { if (it.farmerId == profile.farmerId) it else profile }

        val contextualPrompt = buildString {
            append(buildPromptWithLanguageLock(AppLanguageManager.currentLanguage, prependShortPrompt(roleLine, forVoice = false)))
            append("\n")
            append(buildFarmerContextBlock(freshProfile))
            append("\n")
            append(featurePrompt)
        }

        try {
            val rawOutput = runNativeInference(contextualPrompt, featurePrompt)
            Log.d(TAG, "Feature $feature inference raw: $rawOutput")
            extractJsonFromOutput(rawOutput)
        } catch (e: ModelNotDownloadedException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Feature $feature inference failed", e)
            throw e
        }
    }

    suspend fun runFeatureInference(featurePrompt: String, profile: FarmerProfile): String =
        runFeatureInferenceForFeature(FeatureRoute.GENERAL, featurePrompt, profile)

    private fun prependShortPrompt(base: String, forVoice: Boolean): String {
        val prefix = if (forVoice) VOICE_SHORT_RESPONSE_PREFIX else SHORT_RESPONSE_PREFIX
        val trimmed = base.trim()
        return if (trimmed.startsWith("Reply in maximum")) trimmed else "$prefix$trimmed"
    }

    private fun buildPromptWithLanguageLock(language: String, body: String): String {
        val lock = languageLock(language)
        val trimmedBody = body.trim()
        return if (trimmedBody.startsWith(lock)) trimmedBody else "$lock\n$trimmedBody"
    }

    private fun profileContextLine(profile: FarmerProfile): String = buildFarmerContextBlock(profile)

    private fun logFeatureCall(feature: FeatureRoute, profile: FarmerProfile, prompt: String) {
        when (feature) {
            FeatureRoute.SOIL_PASSPORT -> DebugLog.i("F01_SOIL_CALLED: N=${profile.soil.N}")
            FeatureRoute.PRE_CROP -> DebugLog.i("F02_PRECROP_CALLED: N=${profile.soil.N}")
            FeatureRoute.CROP_ROTATION -> DebugLog.i("F03_ROTATION_CALLED: crop=${profile.currentCrop}")
            FeatureRoute.FRESHNESS -> DebugLog.i("F04_FRESHNESS_QWEN: crop=${profile.currentCrop}")
            FeatureRoute.STORAGE_GUIDE -> DebugLog.i("F05_STORAGE_CALLED: crop=${profile.lastHarvest.crop}")
            FeatureRoute.HARVEST_TIMING -> DebugLog.i("F06_HARVEST_QWEN: crop=${profile.currentCrop}")
            FeatureRoute.COLD_CHAIN -> DebugLog.i("F07_COLDCHAIN_CALLED: crop=${profile.lastHarvest.crop}")
            FeatureRoute.SELL_ADVISOR -> DebugLog.i("F08_SELLADVISOR_QWEN: crop=${profile.currentCrop}")
            FeatureRoute.MARKET_PRICE -> DebugLog.i("F09_MARKETPRICE_QWEN: crop=${profile.currentCrop}")
            FeatureRoute.POSTHARVEST_LOSS -> DebugLog.i("F10_POSTHARVEST_CALLED")
            FeatureRoute.SURPLUS -> DebugLog.i("F11_SURPLUS_CALLED: crop=${profile.lastHarvest.crop}")
            FeatureRoute.VALUE_ADDITION -> DebugLog.i("F12_VALUEADD_CALLED: crop=${profile.lastHarvest.crop}")
            FeatureRoute.WASTE_ENGINE -> {
                val wasteType = prompt.substringAfter("- Waste Type: ", "Unknown").substringBefore("\n").trim()
                DebugLog.i("F13_WASTE_CALLED: type=$wasteType")
            }
            FeatureRoute.CARBON_TRACKER -> DebugLog.i("F14_CARBON_CALLED")
            FeatureRoute.GOVT_SCHEMES -> DebugLog.i("F15_SCHEMES_CALLED")
            FeatureRoute.GENERAL -> {}
        }
    }

    /**
     * Ensures the GGUF model is loaded into memory (lazy singleton).
     * Copies from assets to internal storage on first use, then loads via llama.cpp.
     */
    private suspend fun ensureModelLoaded() {
        if (isModelLoaded && llamaHelper != null) {
            DebugLog.i("MODEL_LOAD_STATUS: loaded=$isModelLoaded handle=${llamaHelper != null}")
            return
        }

        if (isModelLoading) {
            // Wait for ongoing load to complete
            while (isModelLoading) {
                kotlinx.coroutines.delay(200)
            }
            if (isModelLoaded) return
        }

        synchronized(this) {
            if (isModelLoaded && llamaHelper != null) return
            isModelLoading = true
        }

        try {
            val modelFile = getOrCopyModelFile()

            Log.i(TAG, "Loading GGUF model from: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)")
            val startTime = System.currentTimeMillis()

            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val helper = LlamaHelper(context.contentResolver, scope, sharedFlow)
            val modelUri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", modelFile).toString()

            withTimeout(30000) { // 30 seconds max to load a 400MB model on mobile RAM/storage
                suspendCancellableCoroutine<Unit> { continuation ->
                    var resumed = false
                    helper.load(modelUri, 2048) { modelId ->
                        Log.i(TAG, "LlamaHelper load callback: modelId = $modelId")
                        if (!resumed) {
                            resumed = true
                            if (modelId >= 0) {
                                llamaHelper = helper
                                isModelLoaded = true
                                DebugLog.i("MODEL_LOAD_STATUS: loaded=$isModelLoaded handle=${llamaHelper != null}")
                                continuation.resume(Unit)
                            } else {
                                DebugLog.i("MODEL_LOAD_STATUS: loaded=false handle=false")
                                continuation.resumeWithException(RuntimeException("Failed to load model: modelId = $modelId"))
                            }
                        }
                    }
                }
            }

            val loadTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ Model loaded. Size: ${modelFile.length() / 1024 / 1024}MB (took ${loadTime}ms)")
        } catch (e: Exception) {
            isModelLoading = false
            Log.e(TAG, "Model loading failed", e)
            when {
                e is ModelNotDownloadedException -> throw e
                e.message?.contains("memory", ignoreCase = true) == true ->
                    throw RuntimeException("Not enough RAM to load the AI model. Close other apps and try again.", e)
                else -> throw ModelNotDownloadedException(
                    "Failed to load the offline AI model: ${e.message}\n" +
                    "Please ensure the GGUF model file exists in assets/models/llm/"
                )
            }
        } finally {
            isModelLoading = false
        }
    }

    /**
     * Gets the model file from internal storage, copying from assets if needed.
     * Assets cannot be mmap'd directly, so we copy to filesDir on first run.
     */
    private fun getOrCopyModelFile(): File {
        val llmDir = File(context.filesDir, INTERNAL_MODEL_DIR)
        if (!llmDir.exists()) llmDir.mkdirs()
        val modelFile = File(llmDir, MODEL_FILENAME)

        if (modelFile.exists() && modelFile.length() > 100_000_000) {
            // Model already copied to internal storage
            return modelFile
        }

        // Check if model exists in assets
        val assetExists = try {
            val assetsList = context.assets.list("models/llm")
            assetsList?.contains(MODEL_FILENAME) == true
        } catch (e: IOException) {
            false
        }

        if (!assetExists) {
            throw ModelNotDownloadedException(
                "Offline LLM Model file is missing!\n" +
                "Expected file: assets/$MODEL_ASSET_PATH\n" +
                "Please add the GGUF model (~397MB) to make on-device AI operational."
            )
        }

        // Copy from assets to internal storage
        Log.i(TAG, "Copying model from assets to internal storage...")
        context.assets.open(MODEL_ASSET_PATH).use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
        Log.i(TAG, "Model copied: ${modelFile.length() / 1024 / 1024}MB")

        return modelFile
    }

    /**
     * Copy the model from assets to internal storage with progress reporting.
     * Used for explicit "download" flow from the UI.
     */
    suspend fun downloadQwenModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val llmDir = File(context.filesDir, INTERNAL_MODEL_DIR)
        if (!llmDir.exists()) llmDir.mkdirs()
        val modelFile = File(llmDir, MODEL_FILENAME)

        // Check if already present
        if (modelFile.exists() && modelFile.length() > 100_000_000) {
            withContext(Dispatchers.Main) { onProgress(100) }
            ensureModelLoaded()
            return@withContext
        }

        // Get total size from assets
        val afd: AssetFileDescriptor = context.assets.openFd(MODEL_ASSET_PATH)
        val totalBytes = afd.length
        afd.close()

        // Copy with progress
        var copiedBytes = 0L
        context.assets.open(MODEL_ASSET_PATH).use { input ->
            modelFile.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copiedBytes += bytesRead
                    val progress = ((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }
        }

        withContext(Dispatchers.Main) { onProgress(100) }
        Log.i(TAG, "Model copied to internal storage: ${modelFile.length() / 1024 / 1024}MB")

        // Load the model into memory
        ensureModelLoaded()
    }

    /**
     * Native inference using llama.cpp JNI bindings via kotlinllamacpp.
     *
     * CRITICAL: predict() and flow collection MUST happen in the SAME coroutine
     * launch block, in this exact order:
     *   1. Call predict() — this starts emitting LLMEvent tokens on the SharedFlow
     *   2. Immediately collect the SharedFlow — no gap, no race
     *
     * The previous implementation launched a separate collectJob BEFORE predict(),
     * but MutableSharedFlow with replay=0 drops any events emitted before the
     * collector subscribes. This caused the Done event to be permanently lost,
     * hanging inference forever (the "stuck on thinking" bug).
     */
    private suspend fun runNativeInference(
        systemPrompt: String,
        userQuery: String
    ): String = inferenceMutex.withLock {
        val helper = llamaHelper
            ?: throw ModelNotDownloadedException("Not loaded")

        val profile = try {
            AppModule.provideFarmerProfileRepository(context).getProfileOnce()
        } catch (e: Exception) {
            null
        }

        val language = profile?.let { AppLanguageManager.normalize(it.language) }
            ?: AppLanguageManager.currentLanguage
        val langLock = languageLock(language)
        val trimmedPrompt = systemPrompt.trim()
        val finalSystemPrompt = if (!trimmedPrompt.startsWith(langLock)) {
            "$langLock\n$trimmedPrompt"
        } else {
            trimmedPrompt
        }

        val enrichedUserQuery = if (profile != null && !userQuery.contains("Farmer name:")) {
            "${buildFarmerContextBlock(profile)}\n$userQuery"
        } else {
            userQuery
        }

        val fullPrompt = buildString {
            append("<|im_start|>system\n")
            append(finalSystemPrompt)
            append("\n<|im_end|>\n")
            append("<|im_start|>user\n")
            append(enrichedUserQuery)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        val responseBuilder = StringBuilder()
        val done = CompletableDeferred<String>()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val collectJob = scope.launch {
            sharedFlow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        responseBuilder.append(event.word)
                        DebugLog.i("TOKEN: ${event.word}")
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        DebugLog.i("INFERENCE_DONE: chars=${responseBuilder.length}")
                        done.complete(responseBuilder.toString())
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        DebugLog.e("ERROR: ${event.message}")
                        done.completeExceptionally(RuntimeException(event.message))
                    }
                    else -> {}
                }
            }
        }

        try {
            // Wait for flow subscription to complete before triggering prediction
            sharedFlow.subscriptionCount.first { it > 0 }

            DebugLog.i("PREDICT_CALLED: prompt_length=${fullPrompt.length}")
            helper.predict(fullPrompt, null, true)

            withTimeout(120_000) { done.await() }
        } finally {
            collectJob.cancel()
        }
    }

    private fun buildSystemPrompt(profile: FarmerProfile, query: String, conversationHistory: List<Pair<String,String>>): String {
        val historyString = conversationHistory.takeLast(3).joinToString("\n            ") { "Farmer: ${it.first}\nAssistant: ${it.second}" }
        val body = """
            ${prependShortPrompt("", forVoice = true)}
            You are KrishiMitra AI, a farming assistant for Indian farmers.
            Always respond in this exact JSON format:
            {
              "reply": "your answer here",
              "feature_route": "one of the route codes below"
            }

            Feature route codes:
            SOIL_PASSPORT, PRE_CROP, CROP_ROTATION,
            FRESHNESS, STORAGE_GUIDE, HARVEST_TIMING,
            COLD_CHAIN, SELL_ADVISOR, MARKET_PRICE,
            POSTHARVEST_LOSS, SURPLUS, VALUE_ADDITION,
            WASTE_ENGINE, CARBON_TRACKER, GOVT_SCHEMES,
            GENERAL

            Use GENERAL if no specific feature is needed.

            ${buildFarmerContextBlock(profile)}
            - Last Harvest: ${profile.lastHarvest.crop}, ${profile.lastHarvest.qtyKg}kg
            - Eligible Schemes: ${profile.eligibleSchemes.joinToString()}
            - Carbon Footprint: ${profile.carbonFootprintKg}kg CO2

            Recent Conversation History:
            $historyString

            Farmer says: $query
        """.trimIndent()
        return buildPromptWithLanguageLock(AppLanguageManager.currentLanguage, body)
    }

    private fun parseJsonResponse(rawJson: String): VoiceResponse {
        return try {
            responseAdapter.fromJson(rawJson) ?: VoiceResponse(rawJson, FeatureRoute.GENERAL)
        } catch (e: Exception) {
            VoiceResponse(rawJson, FeatureRoute.GENERAL)
        }
    }

    /**
     * Extract JSON object from LLM output that may contain extra text before/after the JSON.
     */
    private fun extractJsonFromOutput(output: String): String {
        // Try to find JSON object boundaries
        val firstBrace = output.indexOf('{')
        val lastBrace = output.lastIndexOf('}')

        if (firstBrace != -1 && lastBrace > firstBrace) {
            return output.substring(firstBrace, lastBrace + 1)
        }

        // If no JSON found, return raw output
        return output.trim()
    }
}
