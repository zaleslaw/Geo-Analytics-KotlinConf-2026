package com.zaleslaw.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fetches historical weather data from Open-Meteo API.
 * Docs: https://open-meteo.com/en/docs/historical-weather-api
 */
class WeatherService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getWeatherForLocation(
        lat: Double,
        lon: Double,
        dateTime: LocalDateTime
    ): WeatherData? {
        for (attempt in 0..2) {
            try {
                val date = dateTime.date
                val hour = dateTime.hour
                val response: OpenMeteoResponse = client.get(buildUrl(lat, lon, date)).body()
                return response.hourly?.let { hourly ->
                    if (hourly.temperature_2m.isEmpty()) {
                        println("No weather data for ($lat, $lon) on $date")
                        return null
                    }
                    val idx = hour.coerceIn(0, hourly.temperature_2m.size - 1)
                    WeatherData(
                        temperature = hourly.temperature_2m.getOrNull(idx) ?: 0.0,
                        precipitation = hourly.precipitation.getOrNull(idx) ?: 0.0,
                        weatherCode = hourly.weather_code.getOrNull(idx) ?: 0,
                        cloudCover = hourly.cloud_cover.getOrNull(idx) ?: 0,
                        windSpeed = hourly.wind_speed_10m.getOrNull(idx) ?: 0.0
                    )
                }
            } catch (e: Exception) {
                if (attempt < 2) {
                    println("Attempt ${attempt + 1} failed for ($lat, $lon) — retrying in ${300L * (attempt + 1)}ms: ${e.message}")
                    kotlinx.coroutines.delay(300L * (attempt + 1))
                } else {
                    println("Failed to fetch weather for ($lat, $lon) at $dateTime: ${e.message}")
                }
            }
        }
        return null
    }

    suspend fun getWeatherBatch(
        locations: List<Triple<Double, Double, LocalDateTime>>
    ): Map<Triple<Double, Double, LocalDateTime>, WeatherData> {
        val results = mutableMapOf<Triple<Double, Double, LocalDateTime>, WeatherData>()
        locations.forEach { (lat, lon, dateTime) ->
            getWeatherForLocation(lat, lon, dateTime)?.let {
                results[Triple(lat, lon, dateTime)] = it
            }
            kotlinx.coroutines.delay(200) // respect API rate limit
        }
        return results
    }

    private fun buildUrl(lat: Double, lon: Double, date: LocalDate): String {
        return "https://archive-api.open-meteo.com/v1/archive?" +
                "latitude=$lat&longitude=$lon&" +
                "start_date=$date&end_date=$date&" +
                "hourly=temperature_2m,precipitation,weather_code,cloud_cover,wind_speed_10m&" +
                "timezone=auto"
    }

    fun close() = client.close()

    @Serializable
    private data class OpenMeteoResponse(val hourly: HourlyData? = null)

    @Serializable
    private data class HourlyData(
        val temperature_2m: List<Double>,
        val precipitation: List<Double>,
        val weather_code: List<Int>,
        val cloud_cover: List<Int>,
        val wind_speed_10m: List<Double>
    )
}
