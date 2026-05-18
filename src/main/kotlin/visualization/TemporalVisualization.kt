package com.zaleslaw.visualization

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.*
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.context.invoke

fun DataFrame<*>.visualizeDurationByMonth(outputPath: String = "duration_by_month.png") {
    println("\n=== Creating Duration by Month Chart ===")

    val byMonth = this
        .filter { "month"<Int>() > 0 && "duration_minutes"<Long>() > 0L }
        .add("dur_hours") { (this["duration_minutes"] as Long).toDouble() / 60.0 }
        .groupBy { "month"<Int>() }
        .aggregate {
            sum { "dur_hours"<Double>() } into "total_hours"
        }
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
            y("total_hours") { axis.name = "Total Hours" }
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
            title = "Walk Duration by Month (hours)"
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
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}

fun DataFrame<*>.visualizeDurationByHour(outputPath: String = "duration_by_hour.png") {
    println("\n=== Creating Duration by Hour Chart ===")

    val byHour = this
        .filter { "start_hour"<Int>() >= 0 && "duration_minutes"<Long>() > 0L }
        .groupBy { "start_hour"<Int>() }
        .aggregate {
            sum { "duration_minutes"<Long>() } into "total_minutes"
        }
        .sortBy("start_hour")
        .add("time_of_day") {
            when (this["start_hour"] as Int) {
                in 0..5  -> "Night"
                in 6..11 -> "Morning"
                in 12..17 -> "Afternoon"
                else     -> "Evening"
            }
        }

    byHour.plot {
        bars {
            x("start_hour") { axis.name = "Hour of Day" }
            y("total_minutes") { axis.name = "Total Minutes" }
            alpha = 0.85
            fillColor("time_of_day") {
                scale = categorical(
                    "Night"     to Color.hex("#2C3E50"),
                    "Morning"   to Color.hex("#F39C12"),
                    "Afternoon" to Color.hex("#3498DB"),
                    "Evening"   to Color.hex("#8E44AD")
                )
            }
        }
        layout {
            title = "Walk Duration by Hour of Day (minutes)"
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
    }.save(outputPath)

    println("Chart saved to: $outputPath")
}