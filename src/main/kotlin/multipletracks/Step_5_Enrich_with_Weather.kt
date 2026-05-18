package com.zaleslaw.multipletracks

import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.analysis.addSpeedMetrics
import com.zaleslaw.analysis.addTemporalAttributes
import com.zaleslaw.analysis.calculateTrackStats
import com.zaleslaw.TRACKS_PATH
import com.zaleslaw.io.readGPX
import com.zaleslaw.weather.WeatherService
import com.zaleslaw.weather.aggregateWeatherByTrack
import com.zaleslaw.weather.enrichWithWeather
import com.zaleslaw.weather.printWeatherStats
import com.zaleslaw.visualization.visualizeWalksByWeatherCategory
import com.zaleslaw.visualization.visualizeWalksByMonthAndWeather
import com.zaleslaw.visualization.visualizeWeatherHeatmap
import com.zaleslaw.visualization.visualizeAvgTemperatureByMonth
import com.zaleslaw.visualization.visualizeTemperatureVsDuration
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import java.io.File

fun main() = runBlocking {
    val dir = "multipleTracks"
    val weatherService = WeatherService()

    try {
        // ── Load tracks (per-track addDistanceColumns before concat) ──────────
        val pointsDf = File(TRACKS_PATH)
            .listFiles { f -> f.extension.equals("gpx", ignoreCase = true) }
            ?.map { file ->
                DataFrame.readGPX(file.absolutePath)
                    .add("filename") { file.name }
                    .addDistanceColumns()
            }
            ?.reduce { acc, df -> acc.concat(df) }
            ?.addTemporalAttributes()
            ?.addSpeedMetrics()
            ?: return@runBlocking

        println("Loaded ${pointsDf.rowsCount()} points from all tracks")

        // ── Enrich with weather — populates weather_cache.csv on first run ────
        println("\nEnriching with weather data (using cache)...")
        val enrichedDf = pointsDf.enrichWithWeather(weatherService, useCache = true)

        // ── Track-level stats (speed, distance, month…) ───────────────────────
        val trackStats = enrichedDf.calculateTrackStats()

        // ── Track-level weather (dominant category, averages) ─────────────────
        val trackWeather = enrichedDf.aggregateWeatherByTrack()
        trackWeather.printWeatherStats()

        // ── Combine for charts ────────────────────────────────────────────────
        val combined = trackStats.innerJoin(trackWeather, "filename")
        println("\nCombined ${combined.rowsCount()} tracks with weather data")

        // ── Charts ─────────────────────────────────────────────────────────────
        combined.visualizeWalksByWeatherCategory("$dir/walks_by_weather.png")
        combined.visualizeWalksByMonthAndWeather("$dir/walks_by_month_weather.png")
        combined.visualizeWeatherHeatmap("$dir/weather_heatmap.png")
        combined.visualizeAvgTemperatureByMonth("$dir/temperature_by_month.png")
        combined.visualizeTemperatureVsDuration("$dir/temperature_vs_duration.png")

    } finally {
        weatherService.close()
    }
}
