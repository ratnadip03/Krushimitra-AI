package com.example.data.viewmodel

import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * ViewModels F01–F16: Qwen-backed inference for all features.
 * Data-only layer — inject into existing UI state variables from screens.
 */
class SoilPassportViewModel(private val featureService: FeatureInferenceService) {
    suspend fun analyzeSoil(
        profile: FarmerProfile,
        n: String, p: String, k: String, ph: Double, healthScore: Int
    ) = featureService.inferSoilAdvice(profile, n, p, k, ph, healthScore)
}

class PreCropViewModel(private val qwen: QwenInferenceService) {
    suspend fun suggestCrops(profile: FarmerProfile): String {
        val prompt = """
            Top 5 crops for this soil. JSON array: [{"name":"","yield":"","suitability":"","reason":""}]
        """.trimIndent()
        return qwen.runFeatureInferenceForFeature(FeatureRoute.PRE_CROP, prompt, profile)
    }
}

class CropRotationViewModel(private val qwen: QwenInferenceService) {
    suspend fun planRotation(profile: FarmerProfile): String {
        val prompt = """
            3-season rotation for ${profile.currentCrop}. JSON: {"season1":{"crop":"","reason":""},"season2":{},"season3":{},"overall_benefit":""}
        """.trimIndent()
        return qwen.runFeatureInferenceForFeature(FeatureRoute.CROP_ROTATION, prompt, profile)
    }
}

class StorageGuideViewModel(private val qwen: QwenInferenceService) {
    suspend fun storageAdvice(profile: FarmerProfile, crop: String): String {
        val prompt = "Storage advice for $crop. JSON: {\"temp_c\":number,\"humidity_pct\":number,\"tip\":\"one sentence\"}"
        return qwen.runFeatureInferenceForFeature(FeatureRoute.STORAGE_GUIDE, prompt, profile)
    }
}

class ColdChainViewModel(private val featureService: FeatureInferenceService) {
    suspend fun advise(profile: FarmerProfile, crop: String, qty: Double, freshness: Int) =
        featureService.inferColdChain(profile, crop, qty, freshness)
}

class SellAdvisorViewModel(private val qwen: QwenInferenceService) {
    suspend fun sellAdvice(profile: FarmerProfile, contextBlock: String): String =
        qwen.runFeatureInferenceForFeature(FeatureRoute.SELL_ADVISOR, contextBlock, profile)
}

class MarketPriceViewModel(private val featureService: FeatureInferenceService) {
    suspend fun predictPrice(profile: FarmerProfile, mandi: MandiPriceData): String =
        featureService.inferMarketPrice(profile, mandi)
}

class PostHarvestLossViewModel(private val qwen: QwenInferenceService) {
    suspend fun analyzeLoss(profile: FarmerProfile, crop: String, qty: Double): String {
        val prompt = "Post-harvest loss for $crop ${qty}kg. JSON: {\"loss_pct\":number,\"causes\":\"\",\"prevention\":\"\"}"
        return qwen.runFeatureInferenceForFeature(FeatureRoute.POSTHARVEST_LOSS, prompt, profile)
    }
}

class SurplusViewModel(private val featureService: FeatureInferenceService) {
    suspend fun surplusAdvice(profile: FarmerProfile) = featureService.inferSurplusAdvice(profile)
}

class ValueAdditionViewModel(private val qwen: QwenInferenceService) {
    suspend fun valueAddIdeas(profile: FarmerProfile, crop: String): String {
        val prompt = "Value-addition for $crop. JSON: {\"options\":[{\"product\":\"\",\"margin_pct\":number,\"steps\":\"\"}]}"
        return qwen.runFeatureInferenceForFeature(FeatureRoute.VALUE_ADDITION, prompt, profile)
    }
}

class WasteEngineViewModel(private val qwen: QwenInferenceService) {
    suspend fun wasteRevenue(profile: FarmerProfile, wasteType: String, qtyKg: Double): String {
        val prompt = "Waste Type: $wasteType\nQuantity: $qtyKg kg\nJSON: {\"options\":[{\"method\":\"\",\"revenue_inr\":number}]}"
        return qwen.runFeatureInferenceForFeature(FeatureRoute.WASTE_ENGINE, prompt, profile)
    }
}

class CarbonTrackerViewModel(private val qwen: QwenInferenceService) {
    suspend fun carbonReport(profile: FarmerProfile): String {
        val prompt = "Carbon footprint ${profile.carbonFootprintKg}kg. JSON: {\"total_kg\":number,\"reduction_tip\":\"one sentence\"}"
        return qwen.runFeatureInferenceForFeature(FeatureRoute.CARBON_TRACKER, prompt, profile)
    }
}

class GovtSchemesViewModel(private val qwen: QwenInferenceService) {
    suspend fun schemeGuidance(profile: FarmerProfile, schemes: List<String>): String {
        val prompt = "Eligible schemes: ${schemes.joinToString()}. JSON: {\"guidance\":\"two sentences\"}"
        return qwen.runFeatureInferenceForFeature(FeatureRoute.GOVT_SCHEMES, prompt, profile)
    }
}

/** Factory for all 16 feature ViewModels (F04/F06 use Gemini Vision — not Qwen). */
class FeatureViewModelFactory(
    qwenInferenceService: QwenInferenceService
) {
    private val featureService = FeatureInferenceService(qwenInferenceService)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val f01Soil = SoilPassportViewModel(featureService)
    val f02PreCrop = PreCropViewModel(qwenInferenceService)
    val f03Rotation = CropRotationViewModel(qwenInferenceService)
    val f05Storage = StorageGuideViewModel(qwenInferenceService)
    val f07ColdChain = ColdChainViewModel(featureService)
    val f08Sell = SellAdvisorViewModel(qwenInferenceService)
    val f09Market = MarketPriceViewModel(featureService)
    val f10Loss = PostHarvestLossViewModel(qwenInferenceService)
    val f11Surplus = SurplusViewModel(featureService)
    val f12ValueAdd = ValueAdditionViewModel(qwenInferenceService)
    val f13Waste = WasteEngineViewModel(qwenInferenceService)
    val f14Carbon = CarbonTrackerViewModel(qwenInferenceService)
    val f15Schemes = GovtSchemesViewModel(qwenInferenceService)
}
