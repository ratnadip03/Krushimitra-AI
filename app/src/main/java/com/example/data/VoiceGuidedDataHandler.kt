package com.example.data

import com.example.data.viewmodel.FeatureViewModelFactory

/**
 * Maps voice-collected answers to farmer profile fields and runs feature inference.
 */
object VoiceGuidedDataHandler {

    fun applyAnswersToProfile(route: FeatureRoute, profile: FarmerProfile, answers: List<String>): FarmerProfile {
        if (answers.isEmpty()) return profile
        return when (route) {
            FeatureRoute.PRE_CROP -> profile.copy(
                landAcres = answers.getOrNull(0)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                    ?: profile.landAcres,
                soil = profile.soil.copy(
                    N = answers.getOrNull(1)?.takeIf { it.isNotBlank() } ?: profile.soil.N
                )
            )
            FeatureRoute.CROP_ROTATION -> profile.copy(
                currentCrop = answers.getOrNull(0)?.takeIf { it.isNotBlank() } ?: profile.currentCrop,
                landAcres = answers.getOrNull(1)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                    ?: profile.landAcres
            )
            FeatureRoute.STORAGE_GUIDE -> profile.copy(
                lastHarvest = profile.lastHarvest.copy(
                    crop = answers.getOrNull(0)?.takeIf { it.isNotBlank() } ?: profile.lastHarvest.crop
                )
            )
            FeatureRoute.HARVEST_TIMING, FeatureRoute.COLD_CHAIN,
            FeatureRoute.SELL_ADVISOR, FeatureRoute.MARKET_PRICE,
            FeatureRoute.POSTHARVEST_LOSS, FeatureRoute.VALUE_ADDITION -> profile.copy(
                currentCrop = answers.getOrNull(0)?.takeIf { it.isNotBlank() } ?: profile.currentCrop
            )
            FeatureRoute.SURPLUS -> profile.copy(
                lastHarvest = profile.lastHarvest.copy(
                    crop = answers.getOrNull(0)?.takeIf { it.isNotBlank() } ?: profile.lastHarvest.crop,
                    qtyKg = answers.getOrNull(1)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                        ?: profile.lastHarvest.qtyKg
                )
            )
            FeatureRoute.WASTE_ENGINE -> profile.copy(
                wasteThisSeason = profile.wasteThisSeason.copy(
                    crop = answers.getOrNull(0)?.takeIf { it.isNotBlank() } ?: profile.wasteThisSeason.crop
                )
            )
            FeatureRoute.CARBON_TRACKER -> profile.copy(
                landAcres = answers.getOrNull(0)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                    ?: profile.landAcres
            )
            FeatureRoute.GOVT_SCHEMES -> profile.copy(
                landAcres = answers.getOrNull(0)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                    ?: profile.landAcres
            )
            else -> profile
        }
    }

    fun buildInferenceContext(route: FeatureRoute, answers: List<String>): String = when (route) {
        FeatureRoute.PRE_CROP -> """
            Land acres: ${answers.getOrNull(0) ?: "unknown"}
            Soil type: ${answers.getOrNull(1) ?: "unknown"}
            Water availability: ${answers.getOrNull(2) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.CROP_ROTATION -> """
            Last season crop: ${answers.getOrNull(0) ?: "unknown"}
            Land acres: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.STORAGE_GUIDE -> """
            Crop being stored: ${answers.getOrNull(0) ?: "unknown"}
            Storage duration days: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.HARVEST_TIMING -> """
            Crop: ${answers.getOrNull(0) ?: "unknown"}
            Sowing date: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.COLD_CHAIN -> """
            Crop: ${answers.getOrNull(0) ?: "unknown"}
            Location: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.SELL_ADVISOR -> """
            Crop: ${answers.getOrNull(0) ?: "unknown"}
            Quantity kg: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.MARKET_PRICE -> "Crop: ${answers.getOrNull(0) ?: "unknown"}"
        FeatureRoute.POSTHARVEST_LOSS -> """
            Crop: ${answers.getOrNull(0) ?: "unknown"}
            Market distance: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.SURPLUS -> """
            Surplus crop: ${answers.getOrNull(0) ?: "unknown"}
            Quantity kg: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.VALUE_ADDITION -> "Crop: ${answers.getOrNull(0) ?: "unknown"}"
        FeatureRoute.WASTE_ENGINE -> "Waste type: ${answers.getOrNull(0) ?: "unknown"}"
        FeatureRoute.CARBON_TRACKER -> """
            Land acres: ${answers.getOrNull(0) ?: "unknown"}
            Fertilizer used: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        FeatureRoute.GOVT_SCHEMES -> """
            Land acres: ${answers.getOrNull(0) ?: "unknown"}
            Annual income: ${answers.getOrNull(1) ?: "unknown"}
        """.trimIndent()
        else -> answers.joinToString("\n")
    }

    suspend fun runInference(
        route: FeatureRoute,
        profile: FarmerProfile,
        answers: List<String>,
        qwen: QwenInferenceService
    ): String {
        val factory = FeatureViewModelFactory(qwen)
        val context = buildInferenceContext(route, answers)
        return when (route) {
            FeatureRoute.PRE_CROP -> factory.f02PreCrop.suggestCrops(profile)
            FeatureRoute.CROP_ROTATION -> factory.f03Rotation.planRotation(profile)
            FeatureRoute.STORAGE_GUIDE -> {
                val crop = answers.getOrNull(0) ?: profile.lastHarvest.crop
                factory.f05Storage.storageAdvice(profile, crop)
            }
            FeatureRoute.HARVEST_TIMING -> qwen.runFeatureInferenceForFeature(
                FeatureRoute.HARVEST_TIMING,
                "Harvest timing for: $context. JSON: {\"harvest_window\":\"text\",\"reason\":\"one sentence\"}",
                profile
            )
            FeatureRoute.COLD_CHAIN -> {
                val crop = answers.getOrNull(0) ?: profile.currentCrop
                factory.f07ColdChain.advise(profile, crop, profile.lastHarvest.qtyKg, 75)
            }
            FeatureRoute.SELL_ADVISOR -> factory.f08Sell.sellAdvice(profile, context)
            FeatureRoute.MARKET_PRICE -> factory.f09Market.predictPrice(profile, profile.cachedMandiPrice)
            FeatureRoute.POSTHARVEST_LOSS -> {
                val crop = answers.getOrNull(0) ?: profile.currentCrop
                val qty = answers.getOrNull(1)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull() ?: 100.0
                factory.f10Loss.analyzeLoss(profile, crop, qty)
            }
            FeatureRoute.SURPLUS -> factory.f11Surplus.surplusAdvice(profile).suggestion
            FeatureRoute.VALUE_ADDITION -> {
                val crop = answers.getOrNull(0) ?: profile.currentCrop
                factory.f12ValueAdd.valueAddIdeas(profile, crop)
            }
            FeatureRoute.WASTE_ENGINE -> {
                val waste = answers.getOrNull(0) ?: "general"
                factory.f13Waste.wasteRevenue(profile, waste, 50.0)
            }
            FeatureRoute.CARBON_TRACKER -> factory.f14Carbon.carbonReport(profile)
            FeatureRoute.GOVT_SCHEMES -> factory.f15Schemes.schemeGuidance(profile, profile.eligibleSchemes)
            FeatureRoute.GENERAL -> qwen.runInference(
                context,
                profile,
                emptyList()
            ).reply
            else -> ""
        }
    }

    fun formatResultForSpeech(route: FeatureRoute, raw: String): String {
        if (raw.isBlank()) return AppLanguageManager.localized(
            mr = "माफ करा, उत्तर मिळाले नाही.",
            hi = "क्षमा करें, जवाब नहीं मिला।",
            en = "Sorry, no result was generated."
        )
        return raw.take(500)
    }
}
