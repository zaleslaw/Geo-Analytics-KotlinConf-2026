package com.zaleslaw.model

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val time: String,
    val altitude: Double? = null
)
