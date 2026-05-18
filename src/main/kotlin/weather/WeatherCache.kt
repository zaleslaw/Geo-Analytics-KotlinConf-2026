package com.zaleslaw.weather

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import java.io.File

object WeatherCache {
    private const val CACHE_FILE = "weather_cache.csv"

    fun load(): DataFrame<*>? {
        return try {
            val file = File(CACHE_FILE)
            if (file.exists()) {
                println("Loading weather cache from $CACHE_FILE...")
                val cache = DataFrame.readCSV(CACHE_FILE)
                if (!cache.columnNames().contains("hour")) {
                    // Old cache format — missing hourly granularity; discard and rebuild
                    println("Old cache format (no 'hour' column). Rebuilding cache...")
                    file.delete()
                    return null
                }
                cache
            } else {
                null
            }
        } catch (e: Exception) {
            println("Failed to load cache: ${e.message}")
            null
        }
    }

    fun save(cache: DataFrame<*>) {
        try {
            cache.writeCSV(CACHE_FILE)
            println("Weather cache saved to $CACHE_FILE (${cache.rowsCount()} entries)")
        } catch (e: Exception) {
            println("Failed to save cache: ${e.message}")
        }
    }

    fun createEmpty(): DataFrame<*> {
        return dataFrameOf(
            "lat" to emptyList<Double>(),
            "lon" to emptyList<Double>(),
            "date" to emptyList<String>(),
            "hour" to emptyList<Int>(),
            "temperature" to emptyList<Double>(),
            "precipitation" to emptyList<Double>(),
            "weather_code" to emptyList<Int>(),
            "cloud_cover" to emptyList<Int>(),
            "wind_speed" to emptyList<Double>()
        )
    }
}

/**
 * Returns cached weather for the given location and hour, or null on a miss.
 * Coordinate tolerance: 0.01° (~1 km).
 */
fun DataFrame<*>.getFromCache(lat: Double, lon: Double, dateTime: LocalDateTime): WeatherData? {
    if (this.rowsCount() == 0) return null

    val date = dateTime.date
    val hour = dateTime.hour

    val filtered = this.filter {
        val cacheLat = it["lat"] as Double
        val cacheLon = it["lon"] as Double
        val cacheDateValue = it["date"]
        val cacheHour = (it["hour"] as? Number)?.toInt() ?: -1

        val cacheDate = when (cacheDateValue) {
            is String -> cacheDateValue
            is LocalDate -> cacheDateValue.toString()
            else -> cacheDateValue.toString()
        }

        Math.abs(cacheLat - lat) < 0.01 &&
        Math.abs(cacheLon - lon) < 0.01 &&
        cacheDate == date.toString() &&
        cacheHour == hour
    }

    return if (filtered.rowsCount() > 0) {
        val row = filtered[0]
        WeatherData(
            temperature = row["temperature"] as Double,
            precipitation = row["precipitation"] as Double,
            weatherCode = (row["weather_code"] as Number).toInt(),
            cloudCover = (row["cloud_cover"] as Number).toInt(),
            windSpeed = row["wind_speed"] as Double
        )
    } else null
}

fun DataFrame<*>.addToCache(
    lat: Double,
    lon: Double,
    dateTime: LocalDateTime,
    weather: WeatherData
): DataFrame<*> {
    val newRow = dataFrameOf(
        "lat", "lon", "date", "hour",
        "temperature", "precipitation", "weather_code",
        "cloud_cover", "wind_speed"
    )(
        lat, lon, dateTime.date.toString(), dateTime.hour,
        weather.temperature, weather.precipitation, weather.weatherCode,
        weather.cloudCover, weather.windSpeed
    )
    return this.concat(newRow)
}

fun DataFrame<*>.deduplicate(): DataFrame<*> {
    return this
        .groupBy("lat", "lon", "date", "hour")
        .aggregate {
            first()["temperature"] into "temperature"
            first()["precipitation"] into "precipitation"
            first()["weather_code"] into "weather_code"
            first()["cloud_cover"] into "cloud_cover"
            first()["wind_speed"] into "wind_speed"
        }
}
