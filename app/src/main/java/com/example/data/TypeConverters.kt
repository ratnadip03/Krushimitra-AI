package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // LocationData Converters
    private val locationAdapter = moshi.adapter(LocationData::class.java)

    @TypeConverter
    fun fromLocation(location: LocationData?): String? {
        return location?.let { locationAdapter.toJson(it) }
    }

    @TypeConverter
    fun toLocation(json: String?): LocationData? {
        return json?.let { locationAdapter.fromJson(it) }
    }

    // SoilData Converters
    private val soilAdapter = moshi.adapter(SoilData::class.java)

    @TypeConverter
    fun fromSoil(soil: SoilData?): String? {
        return soil?.let { soilAdapter.toJson(it) }
    }

    @TypeConverter
    fun toSoil(json: String?): SoilData? {
        return json?.let { soilAdapter.fromJson(it) }
    }

    // HarvestData Converters
    private val harvestAdapter = moshi.adapter(HarvestData::class.java)

    @TypeConverter
    fun fromHarvest(harvest: HarvestData?): String? {
        return harvest?.let { harvestAdapter.toJson(it) }
    }

    @TypeConverter
    fun toHarvest(json: String?): HarvestData? {
        return json?.let { harvestAdapter.fromJson(it) }
    }

    // List<FreshnessAlert> Converters
    private val freshnessListType = Types.newParameterizedType(List::class.java, FreshnessAlert::class.java)
    private val freshnessListAdapter = moshi.adapter<List<FreshnessAlert>>(freshnessListType)

    @TypeConverter
    fun fromFreshnessList(list: List<FreshnessAlert>?): String? {
        return list?.let { freshnessListAdapter.toJson(it) }
    }

    @TypeConverter
    fun toFreshnessList(json: String?): List<FreshnessAlert>? {
        return json?.let { freshnessListAdapter.fromJson(it) }
    }

    // WasteData Converters
    private val wasteAdapter = moshi.adapter(WasteData::class.java)

    @TypeConverter
    fun fromWaste(waste: WasteData?): String? {
        return waste?.let { wasteAdapter.toJson(it) }
    }

    @TypeConverter
    fun toWaste(json: String?): WasteData? {
        return json?.let { wasteAdapter.fromJson(it) }
    }

    // RevenueData Converters
    private val revenueAdapter = moshi.adapter(RevenueData::class.java)

    @TypeConverter
    fun fromRevenue(revenue: RevenueData?): String? {
        return revenue?.let { revenueAdapter.toJson(it) }
    }

    @TypeConverter
    fun toRevenue(json: String?): RevenueData? {
        return json?.let { revenueAdapter.fromJson(it) }
    }

    // List<String> Converters
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { stringListAdapter.toJson(it) }
    }

    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        return json?.let { stringListAdapter.fromJson(it) }
    }

    // WeatherData Converters
    private val weatherAdapter = moshi.adapter(WeatherData::class.java)

    @TypeConverter
    fun fromWeather(weather: WeatherData?): String? {
        return weather?.let { weatherAdapter.toJson(it) }
    }

    @TypeConverter
    fun toWeather(json: String?): WeatherData? {
        return json?.let { weatherAdapter.fromJson(it) }
    }

    // MandiPriceData Converters
    private val mandiPriceAdapter = moshi.adapter(MandiPriceData::class.java)

    @TypeConverter
    fun fromMandiPrice(mandiPrice: MandiPriceData?): String? {
        return mandiPrice?.let { mandiPriceAdapter.toJson(it) }
    }

    @TypeConverter
    fun toMandiPrice(json: String?): MandiPriceData? {
        return json?.let { mandiPriceAdapter.fromJson(it) }
    }
}
