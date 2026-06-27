package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Qwen inference for all 16 feature ViewModels (F01–F16).
 * Keeps prompts short and uses farmer profile context.
 */
class FeatureInferenceService(
    private val qwenInferenceService: QwenInferenceService
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun profileContext(profile: FarmerProfile): String =
        QwenInferenceService.buildFarmerContextBlock(profile)

    // F01 — Soil Passport
    data class SoilAdviceResult(
        val deficiency: String,
        val recommendation: String,
        val cropSuggestion: String
    )

    suspend fun inferSoilAdvice(
        profile: FarmerProfile,
        n: String, p: String, k: String, ph: Double, healthScore: Int
    ): SoilAdviceResult {
        val prompt = """
            ${profileContext(profile)}
            Soil N=$n, P=$p, K=$k, pH=$ph, health score=$healthScore/100.
            Respond ONLY in JSON: {"deficiency":"one sentence","recommendation":"one sentence","crop_suggestion":"one crop name"}
        """.trimIndent()
        val raw = qwenInferenceService.runFeatureInferenceForFeature(FeatureRoute.SOIL_PASSPORT, prompt, profile)
        val map = moshi.adapter(Map::class.java).fromJson(raw) as? Map<*, *>
        return SoilAdviceResult(
            deficiency = map?.get("deficiency")?.toString() ?: raw,
            recommendation = map?.get("recommendation")?.toString() ?: "",
            cropSuggestion = map?.get("crop_suggestion")?.toString() ?: profile.currentCrop
        )
    }

    // F11 — Surplus Exchange
    data class SurplusAdviceResult(
        val suggestion: String,
        val listingSummary: String,
        val urgency: String
    )

    suspend fun inferSurplusAdvice(profile: FarmerProfile): SurplusAdviceResult {
        val crop = profile.lastHarvest.crop.ifEmpty { profile.currentCrop }
        val qty = profile.lastHarvest.qtyKg.takeIf { it > 0 } ?: profile.landAcres * 100
        val freshness = profile.freshnessAlerts.lastOrNull()?.freshnessScore ?: 75
        val prompt = """
            ${profileContext(profile)}
            Surplus crop: $crop, ${qty}kg. Freshness score: $freshness/100.
            Respond ONLY in JSON: {"suggestion":"one sentence","listing_summary":"one sentence","urgency":"Low/Medium/High"}
        """.trimIndent()
        val raw = qwenInferenceService.runFeatureInferenceForFeature(FeatureRoute.SURPLUS, prompt, profile)
        val map = moshi.adapter(Map::class.java).fromJson(raw) as? Map<*, *>
        return SurplusAdviceResult(
            suggestion = map?.get("suggestion")?.toString() ?: raw,
            listingSummary = map?.get("listing_summary")?.toString() ?: "$crop - ${qty.toInt()} kg",
            urgency = map?.get("urgency")?.toString() ?: "Medium"
        )
    }

    // F09 — Market Price (Qwen instead of Gemini for feature inference)
    suspend fun inferMarketPrice(profile: FarmerProfile, mandi: MandiPriceData): String {
        val crop = profile.currentCrop.ifEmpty { mandi.commodity.ifEmpty { "Soybean" } }
        val prompt = """
            ${profileContext(profile)}
            Crop: $crop. Mandi modal price: ₹${mandi.modalPrice}/Q. Trend: ${mandi.trend}.
            Respond ONLY in JSON: {"current_price_inr_per_quintal":number,"price_trend":"RISING|FALLING|STABLE","best_sell_time":"text","negotiation_floor_inr":number,"market_insight":"two short sentences","disclaimer":"one sentence"}
        """.trimIndent()
        return qwenInferenceService.runFeatureInferenceForFeature(FeatureRoute.MARKET_PRICE, prompt, profile)
    }

    // F07 — Cold Chain (offline Qwen path)
    suspend fun inferColdChain(profile: FarmerProfile, crop: String, qty: Double, freshnessScore: Int): String {
        val prompt = """
            ${profileContext(profile)}
            Produce: $crop, ${qty}kg. Freshness: $freshnessScore/100.
            Respond ONLY in JSON: {"recommended_temp_celsius":number,"max_storage_days":number,"verdict":"one sentence","cost_estimate_inr":number,"premium_inr":number,"reason":"one sentence","nearby_stores":[{"name":"name","distance_km":"text"}]}
        """.trimIndent()
        return qwenInferenceService.runFeatureInferenceForFeature(FeatureRoute.COLD_CHAIN, prompt, profile)
    }
}
