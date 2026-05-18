package com.zaleslaw.weather

data class WeatherData(
    val temperature: Double,   // temperature in °C
    val precipitation: Double, // precipitation in mm/h
    val weatherCode: Int,      // WMO weather code
    val cloudCover: Int,       // cloud cover in %
    val windSpeed: Double      // wind speed in km/h
) {
    val weatherDescription: String
        get() = when (weatherCode) {
            0 -> "Clear"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Heavy rain"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }

    val weatherCategory: String
        get() = when {
            precipitation > 0.1 -> "Rainy"
            cloudCover < 40     -> "Sunny"
            else                -> "Cloudy"
        }
}

data class TrackWeatherSummary(
    val filename: String,
    val date: String,
    val avgTemperature: Double,
    val totalPrecipitation: Double,
    val avgCloudCover: Int,
    val avgWindSpeed: Double,
    val weatherCategory: String,
    val weatherDescription: String
)
