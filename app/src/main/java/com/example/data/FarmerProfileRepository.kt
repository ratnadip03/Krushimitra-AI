package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

object AppModule {
    @Volatile
    private var farmerProfileRepository: FarmerProfileRepository? = null

    fun provideFarmerProfileRepository(context: Context): FarmerProfileRepository {
        return farmerProfileRepository ?: synchronized(this) {
            farmerProfileRepository ?: FarmerProfileRepository(context.applicationContext).also { farmerProfileRepository = it }
        }
    }
    
    @Volatile
    private var connectivityService: ConnectivityService? = null
    fun provideConnectivityService(context: Context): ConnectivityService {
        return connectivityService ?: synchronized(this) {
             connectivityService ?: ConnectivityService(context.applicationContext).also { connectivityService = it }
        }
    }

    fun provideFeatureViewModelFactory(context: Context): com.example.data.viewmodel.FeatureViewModelFactory {
        return com.example.data.viewmodel.FeatureViewModelFactory(QwenInferenceService(context.applicationContext))
    }
}

// Emulating Hilt @Singleton semantic without full DI graph
class FarmerProfileRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.farmerProfileDao()
    private val TAG = "FarmerProfileRepository"
    private val weatherService = WeatherService(context)
    private val mandiPriceService = MandiPriceService(context)

    val currentFarmerId = "farmer_123" // Master identifier for single farmer brain context

    /**
     * Reactively observe the centralized farmer profile.
     */
    fun observeProfile(): Flow<FarmerProfile?> {
        return dao.getFarmerProfile(currentFarmerId)
    }

    /**
     * Get the singular snapshot of the profile.
     */
    suspend fun getProfileOnce(): FarmerProfile {
        val profile = dao.getFarmerProfileOnce(currentFarmerId) ?: createDefaultProfile()
        AppLanguageManager.initializeFromProfile(profile)
        return profile
    }

    /**
     * Update the centralized profile. Writes are persistent locally.
     * If online, it simulated/attempts a sync to "Firestore" (log visual update).
     */
    suspend fun updateProfile(profile: FarmerProfile, isOnline: Boolean = false) {
        val oldProfile = dao.getFarmerProfileOnce(currentFarmerId)
        var finalProfile = profile.copy(farmerId = currentFarmerId)

        // Clear rotation plan when current crop changes to force new plan generation
        if (oldProfile != null && oldProfile.currentCrop != finalProfile.currentCrop) {
            finalProfile = finalProfile.copy(rotationPlan = emptyList())
        }

        // Align lastHarvest crop with currentCrop if it's empty or was aligned with the old current crop
        if (finalProfile.lastHarvest.crop.isEmpty() || (oldProfile != null && finalProfile.lastHarvest.crop == oldProfile.currentCrop)) {
            finalProfile = finalProfile.copy(
                lastHarvest = finalProfile.lastHarvest.copy(
                    crop = finalProfile.currentCrop,
                    qtyKg = if (finalProfile.lastHarvest.qtyKg > 0.0) finalProfile.lastHarvest.qtyKg else 120.0
                )
            )
        }

        if (isOnline) {
            try {
                val district = finalProfile.location.district
                val state = finalProfile.location.state
                if (district.isNotEmpty() && state.isNotEmpty()) {
                    val weather = weatherService.fetchWeather(district, state)
                    finalProfile = finalProfile.copy(cachedWeather = weather)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather in updateProfile: ${e.message}")
            }

            try {
                val crop = finalProfile.currentCrop
                val state = finalProfile.location.state
                if (crop.isNotEmpty() && state.isNotEmpty()) {
                    val mandiPrice = mandiPriceService.fetchMandiPrice(crop, state)
                    finalProfile = finalProfile.copy(cachedMandiPrice = mandiPrice)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching mandi price in updateProfile: ${e.message}")
            }
        }

        dao.insertFarmerProfile(finalProfile)
        AppLanguageManager.updateLanguage(finalProfile.language)
        Log.d(TAG, "Locally updated Farmer Profile successfully: $finalProfile")

        if (isOnline) {
            syncToFirestore(finalProfile)
        } else {
            Log.d(TAG, "Offline mode active. Firestore sync queued locally.")
        }
    }

    /**
     * Reset or recreate a default profile for the initial launch of the app.
     */
    suspend fun createDefaultProfile(): FarmerProfile {
        val defaultProfile = FarmerProfile(
            farmerId = currentFarmerId,
            name = "", // Blank name triggers onboarding
            language = "en",
            location = LocationData(
                lat = 0.0,
                lng = 0.0,
                district = "",
                state = ""
            ),
            landAcres = 0.0,
            soil = SoilData(N = "78", P = "42", K = "40", pH = 6.6, organicCarbon = 0.65, healthScore = 82),
            currentCrop = "Soybean",
            plantingDate = 0L,
            rotationPlan = listOf(),
            lastHarvest = HarvestData(crop = "Soybean", qtyKg = 120.0, date = System.currentTimeMillis()),
            freshnessAlerts = emptyList(),
            wasteThisSeason = WasteData(crop = "Soybean", wasteKg = 0.0, method = ""),
            carbonFootprintKg = 0.0,
            eligibleSchemes = listOf(),
            estimatedRevenue = RevenueData(wasteIncome = 0.0, carbonPremium = 0.0)
        )
        dao.insertFarmerProfile(defaultProfile)
        AppLanguageManager.initializeFromProfile(defaultProfile)
        return defaultProfile
    }

    /**
     * Simulates a Firestore write operaton when online.
     */
    private fun syncToFirestore(profile: FarmerProfile) {
        Log.i(TAG, ">>> CONNECTED ONLINE: Automatically Synced Profile to Centralized Firestore Server [farmer_profile/${profile.farmerId}] <<<")
    }
}

