package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VoiceResponse(
    val reply: String,
    @Json(name = "feature_route") val featureRoute: FeatureRoute
)
