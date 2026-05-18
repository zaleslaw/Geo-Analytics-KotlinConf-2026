package com.zaleslaw.visualization

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.Layout
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.util.context.invoke
import org.jetbrains.kotlinx.kandy.letsplot.layers.*
import org.jetbrains.kotlinx.kandy.util.color.Color

private fun Layout.applyCleanupStyle() {
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

fun DataFrame<*>.visualizeSmoothedSpeedDistribution(outputPath: String = "smoothed_speed_distribution.png") {
    println("\n=== Creating Smoothed Speed Distribution Chart ===")

    val speeds = mutableListOf<Double>()
    for (i in 0 until this.rowsCount()) {
        val speed = this[i]["speed_kmh"] as Double
        if (speed > 0 && speed < 15) {
            speeds.add(speed)
        }
    }

    val binSize = 0.5
    val bins = mutableMapOf<Int, Int>()

    for (speed in speeds) {
        val binStart = (speed / binSize).toInt()
        bins[binStart] = (bins[binStart] ?: 0) + 1
    }

    val sortedBins = bins.keys.sorted()
    val binLabels = sortedBins.map { val start = it * binSize; "%.1f-%.1f".format(start, start + binSize) }
    val counts = sortedBins.map { bins[it] ?: 0 }
    val categories = sortedBins.map { if (it * binSize >= 6.0) "Running (≥6 km/h)" else "Walking" }

    val histData = dataFrameOf(
        "speed_range" to binLabels,
        "count" to counts,
        "category" to categories
    )

    histData.plot {
        bars {
            x("speed_range") { axis.name = "Speed (km/h)" }
            y("count") { axis.name = "Point Count" }
            alpha = 0.85
            fillColor("category") {
                scale = categorical(
                    "Walking" to Color.hex("#3498DB"),
                    "Running (≥6 km/h)" to Color.hex("#E74C3C")
                )
            }
        }
        layout {
            title = "Speed Distribution"
            applyCleanupStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}

/**
 * Walk count histogram by average speed (0.5 km/h bins).
 * Input: track-level DataFrame from calculateTrackStats() with avg_speed_kmh column.
 */
fun DataFrame<*>.visualizeTrackSpeedDistribution(outputPath: String = "track_speed_distribution.png") {
    println("\n=== Creating Track Speed Distribution Chart ===")

    val binned = this
        .filter { "avg_speed_kmh"<Double>() > 0.5 && "avg_speed_kmh"<Double>() < 12.0 }
        .add("speed_bin") {
            val s = this["avg_speed_kmh"] as Double
            (s / 0.5).toInt().toDouble() * 0.5
        }
        .groupBy { "speed_bin"<Double>() }
        .aggregate { count() into "walk_count" }
        .sortBy("speed_bin")

    binned.plot {
        bars {
            x("speed_bin") { axis.name = "Avg Speed (km/h)" }
            y("walk_count") { axis.name = "Number of Walks" }
            alpha = 0.85
            fillColor = Color.hex("#3498DB")
        }
        layout {
            title = "Walk Count by Average Speed"
            applyCleanupStyle()
        }
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}
