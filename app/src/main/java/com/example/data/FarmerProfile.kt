package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LocationData(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val district: String = "",
    val state: String = ""
)

@JsonClass(generateAdapter = true)
data class SoilData(
    val N: String = "",
    val P: String = "",
    val K: String = "",
    val pH: Double = 0.0,
    val organicCarbon: Double = 0.0,
    val healthScore: Int = 0
)

@JsonClass(generateAdapter = true)
data class HarvestData(
    val crop: String = "",
    val qtyKg: Double = 0.0,
    val date: Long = 0L
)

@JsonClass(generateAdapter = true)
data class FreshnessAlert(
    val crop: String = "",
    val freshnessScore: Int = 0,
    val shelfLifeDays: Int = 0,
    val recommendation: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class WasteData(
    val crop: String = "",
    val wasteKg: Double = 0.0,
    val method: String = ""
)

@JsonClass(generateAdapter = true)
data class RevenueData(
    val wasteIncome: Double = 0.0,
    val carbonPremium: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class WeatherData(
    val tempC: Double = 0.0,
    val humidity: Double = 0.0,
    val condition: String = "",
    val feelsLikeC: Double = 0.0,
    val windKph: Double = 0.0,
    val lastUpdated: String = "",
    val source: String = "WeatherAPI.com",
    val conditionText: String = "",
    val rainChanceNext3Days: String = "0%"
)

@JsonClass(generateAdapter = true)
data class MandiPriceData(
    val commodity: String = "",
    val market: String = "",
    val minPrice: Double = 0.0,
    val maxPrice: Double = 0.0,
    val modalPrice: Double = 0.0,
    val lastUpdated: String = "",
    val source: String = "Agmarknet, Govt of India",
    val latestModalPrice: Double = 0.0,
    val trend: String = "STABLE",
    val nearestMarket: String = "",
    val records: List<String> = emptyList()
)

@Entity(tableName = "farmer_profile")
@JsonClass(generateAdapter = true)
data class FarmerProfile(
    @PrimaryKey val farmerId: String = "farmer_123",
    val name: String = "",
    val language: String = "en", // "mr" | "hi" | "en"
    val location: LocationData = LocationData(),
    val landAcres: Double = 0.0,
    val soil: SoilData = SoilData(),
    val currentCrop: String = "",
    val plantingDate: Long = 0L,
    val rotationPlan: List<String> = emptyList(),
    val lastHarvest: HarvestData = HarvestData(),
    val freshnessAlerts: List<FreshnessAlert> = emptyList(),
    val wasteThisSeason: WasteData = WasteData(),
    val carbonFootprintKg: Double = 0.0,
    val eligibleSchemes: List<String> = emptyList(),
    val estimatedRevenue: RevenueData = RevenueData(),
    val cachedWeather: WeatherData = WeatherData(),
    val cachedMandiPrice: MandiPriceData = MandiPriceData()
)
