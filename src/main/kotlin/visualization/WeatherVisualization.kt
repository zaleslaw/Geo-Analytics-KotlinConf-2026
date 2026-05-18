package com.zaleslaw.visualization

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.Layout
import org.jetbrains.kotlinx.kandy.letsplot.feature.Position
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.feature.position
import org.jetbrains.kotlinx.kandy.letsplot.layers.*
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.context.invoke

private fun Layout.applyWeatherStyle() {
    size = 1200 to 800
    style {
        axis {
            text { fontSize = 25.0 }
            title { fontSize = 27.0 }
        }
        legend {
            text { fontSize = 25.0 }
            title { fontSize = 27.0 }
        }
    }
}

/**
 * Option A — Walk count by weather category (simple bar).
 * Input: track-level DataFrame with weather_category column.
 */
fun DataFrame<*>.visualizeWalksByWeatherCategory(outputPath: String = "walks_by_weather.png") {
    println("\n=== Creating Walks by Weather Category Chart ===")

    val byCategory = this
        .groupBy { "weather_category"<String>() }
        .aggregate { count() into "walk_count" }

    byCategory.plot {
        bars {
            x("weather_category") { axis.name = "Weather" }
            y("walk_count") { axis.name = "Number of Walks" }
            alpha = 0.85
            fillColor("weather_category") {
                scale = categorical(
                    "Sunny"  to Color.hex("#F39C12"),
                    "Cloudy" to Color.hex("#95A5A6"),
                    "Rainy"  to Color.hex("#2980B9")
                )
            }
        }
        layout {
            title = "Walks by Weather Category"
            applyWeatherStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}

/**
 * Option B — Stacked bar: walks per month, stacked by weather category.
 * Input: track-level DataFrame with month and weather_category columns.
 */
fun DataFrame<*>.visualizeWalksByMonthAndWeather(outputPath: String = "walks_by_month_weather.png") {
    println("\n=== Creating Walks by Month and Weather Chart ===")

    val byMonthCategory = this
        .filter { "month"<Int>() > 0 }
        .groupBy("month", "weather_category")
        .aggregate { count() into "walk_count" }
        .sortBy("month")

    byMonthCategory.plot {
        bars {
            x("month") { axis.name = "Month" }
            y("walk_count") { axis.name = "Number of Walks" }
            alpha = 0.85
            fillColor("weather_category") {
                scale = categorical(
                    "Sunny"  to Color.hex("#F39C12"),
                    "Cloudy" to Color.hex("#95A5A6"),
                    "Rainy"  to Color.hex("#2980B9")
                )
            }
            position = Position.stack()
        }
        layout {
            title = "Walks per Month by Weather"
            applyWeatherStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}

/**
 * Option C — Heatmap: month × weather category, intensity = walk count.
 * Input: track-level DataFrame with month and weather_category columns.
 */
fun DataFrame<*>.visualizeWeatherHeatmap(outputPath: String = "weather_heatmap.png") {
    println("\n=== Creating Weather Heatmap (month × category) ===")

    val heatData = this
        .filter { "month"<Int>() > 0 }
        .groupBy("month", "weather_category")
        .aggregate { count() into "walk_count" }

    heatData.plot {
        tiles {
            x("month") { axis.name = "Month" }
            y("weather_category") { axis.name = "Weather" }
            fillColor("walk_count")
        }
        layout {
            title = "Walk Distribution: Month × Weather"
            applyWeatherStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}

/**
 * Average walk temperature per month, colored by season.
 * Input: track-level DataFrame with month and avg_temperature columns.
 */
fun DataFrame<*>.visualizeAvgTemperatureByMonth(outputPath: String = "temperature_by_month.png") {
    println("\n=== Creating Avg Temperature by Month Chart ===")

    val byMonth = this
        .filter { "month"<Int>() > 0 && (this["avg_temperature"] as? Double) != null }
        .groupBy { "month"<Int>() }
        .aggregate { mean { "avg_temperature"<Double>() } into "mean_temp" }
        .sortBy("month")
        .add("season") {
            when (this["month"] as Int) {
                12, 1, 2 -> "Winter"
                3, 4, 5  -> "Spring"
                6, 7, 8  -> "Summer"
                else     -> "Fall"
            }
        }

    byMonth.plot {
        bars {
            x("month") { axis.name = "Month" }
            y("mean_temp") { axis.name = "Avg Temperature (°C)" }
            alpha = 0.85
            fillColor("season") {
                scale = categorical(
                    "Winter" to Color.hex("#5B9BD5"),
                    "Spring" to Color.hex("#70AD47"),
                    "Summer" to Color.hex("#FFC000"),
                    "Fall"   to Color.hex("#ED7D31")
                )
            }
        }
        layout {
            title = "Average Walk Temperature by Month"
            applyWeatherStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}

/**
 * Scatter: temperature vs walk duration, colored by weather category.
 * Input: track-level DataFrame with avg_temperature, duration_minutes, weather_category.
 */
fun DataFrame<*>.visualizeTemperatureVsDuration(outputPath: String = "temperature_vs_duration.png") {
    println("\n=== Creating Median Duration by Temperature Chart ===")

    val bins = this
        .filter {
            (this["avg_temperature"] as? Double) != null &&
            "duration_minutes"<Long>() > 0L
        }
        .add("bin_start") {
            (Math.floor((this["avg_temperature"] as Double) / 5.0).toInt() * 5)
        }
        .groupBy { "bin_start"<Int>() }
        .aggregate {
            val sorted = rows().mapNotNull { (it["duration_minutes"] as? Long)?.toDouble() }.sorted()
            (if (sorted.isNotEmpty()) sorted[sorted.size / 2] else 0.0) into "median_duration"
            count() into "walk_count"
        }
        .filter { "walk_count"<Int>() >= 2 }
        .sortBy("bin_start")
        .add("temp_range") {
            val s = this["bin_start"] as Int
            if (s < 0) "$s–${s + 5}°C" else "$s–${s + 5}°C"
        }
        .add("temp_season") {
            val s = this["bin_start"] as Int
            when {
                s < 5  -> "Cold"
                s < 15 -> "Mild"
                s < 25 -> "Warm"
                else   -> "Hot"
            }
        }

    bins.plot {
        bars {
            x("temp_range") { axis.name = "Temperature Range" }
            y("walk_count") { axis.name = "Number of Walks" }
            alpha = 0.85
            fillColor("temp_season") {
                scale = categorical(
                    "Cold" to Color.hex("#5B9BD5"),
                    "Mild" to Color.hex("#70AD47"),
                    "Warm" to Color.hex("#FFC000"),
                    "Hot"  to Color.hex("#C0392B")
                )
            }
        }
        layout {
            title = "Walk Count by Temperature Range"
            applyWeatherStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}
