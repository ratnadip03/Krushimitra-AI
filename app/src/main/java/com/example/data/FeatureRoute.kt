package com.example.data

enum class FeatureRoute {
    SOIL_PASSPORT, PRE_CROP, CROP_ROTATION,
    FRESHNESS, STORAGE_GUIDE, HARVEST_TIMING,
    COLD_CHAIN, SELL_ADVISOR, MARKET_PRICE,
    POSTHARVEST_LOSS, SURPLUS, VALUE_ADDITION,
    WASTE_ENGINE, CARBON_TRACKER, GOVT_SCHEMES,
    GENERAL;

    fun toNavRoute(): String = when (this) {
        SOIL_PASSPORT -> "soil_passport"
        PRE_CROP -> "pre_crop"
        CROP_ROTATION -> "crop_rotation"
        FRESHNESS -> "freshness"
        STORAGE_GUIDE -> "storage_guide"
        HARVEST_TIMING -> "harvest_timing"
        COLD_CHAIN -> "cold_chain"
        SELL_ADVISOR -> "sell_advisor"
        MARKET_PRICE -> "market_price"
        POSTHARVEST_LOSS -> "postharvest_loss"
        SURPLUS -> "surplus"
        VALUE_ADDITION -> "value_addition"
        WASTE_ENGINE -> "waste_engine"
        CARBON_TRACKER -> "carbon_tracker"
        GOVT_SCHEMES -> "govt_schemes"
        GENERAL -> "home"
    }
}
