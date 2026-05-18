package com.zaleslaw.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlinx.datetime.*

/**
 * Adds temporal attributes to the DataFrame
 */
fun DataFrame<*>.addTemporalAttributes(): DataFrame<*> {
    return this
        .add("datetime") {
            val timeStr = this["time"] as String
            if (timeStr.isNotEmpty()) {
                try {
                    Instant.parse(timeStr).toLocalDateTime(TimeZone.currentSystemDefault())
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
        .add("hour_of_day") {
            (this["datetime"] as? LocalDateTime)?.hour ?: 0
        }
        .add("day_of_week") {
            (this["datetime"] as? LocalDateTime)?.dayOfWeek?.ordinal ?: 0
        }
        .add("day_name") {
            val dow = (this["datetime"] as? LocalDateTime)?.dayOfWeek
            dow?.toString() ?: "Unknown"
        }
        .add("month") {
            (this["datetime"] as? LocalDateTime)?.monthNumber ?: 0
        }
        .add("year") {
            (this["datetime"] as? LocalDateTime)?.year ?: 0
        }
        .add("date") {
            (this["datetime"] as? LocalDateTime)?.date?.toString() ?: ""
        }
        .add("time_of_day") {
            val hour = (this["datetime"] as? LocalDateTime)?.hour ?: 0
            when (hour) {
                in 0..5 -> "Night"
                in 6..11 -> "Morning"
                in 12..17 -> "Afternoon"
                in 18..23 -> "Evening"
                else -> "Unknown"
            }
        }
}

/**
 * Adds speed metrics between consecutive points
 */
fun DataFrame<*>.addSpeedMetrics(): DataFrame<*> {
    return this.add("speed_kmh") {
        if (index() == 0) {
            0.0
        } else {
            val prevRow = prev()!!
            val distance = this["haversineDistance"] as Double

            val prevTime = prevRow["datetime"] as? LocalDateTime
            val currTime = this["datetime"] as? LocalDateTime

            if (prevTime != null && currTime != null && distance > 0) {
                val prevInstant = prevTime.toInstant(TimeZone.currentSystemDefault())
                val currInstant = currTime.toInstant(TimeZone.currentSystemDefault())
                val timeDiffSeconds = (currInstant - prevInstant).inWholeSeconds

                if (timeDiffSeconds > 0) {
                    val speedMS = distance / timeDiffSeconds
                    speedMS * 3.6 // м/с в км/ч
                } else {
                    0.0
                }
            } else {
                0.0
            }
        }
    }.add("pace_min_per_km") {
        val speed = this["speed_kmh"] as Double
        if (speed > 0) {
            60.0 / speed
        } else {
            0.0
        }
    }
}

/**
 * Calculates per-track statistics using aggregate
 */
fun DataFrame<*>.calculateTrackStats(): DataFrame<*> {
    return this.groupBy { "filename"<String>() }.aggregate {
        // Collect all timestamps for the group
        val times = rows()
            .mapNotNull { it["datetime"] as? LocalDateTime }

        val startTime = times.minOrNull()
        val endTime = times.maxOrNull()

        val duration = if (startTime != null && endTime != null) {
            val start = startTime.toInstant(TimeZone.currentSystemDefault())
            val end = endTime.toInstant(TimeZone.currentSystemDefault())
            (end - start).inWholeMinutes
        } else {
            0L
        }

        // Aggregate metrics
        count() into "point_count"
        sum { "haversineDistance"<Double>() / 1000.0 } into "total_distance_km"
        mean { "speed_kmh"<Double>() } into "avg_speed_kmh"
        max { "speed_kmh"<Double>() } into "max_speed_kmh"

        // Add temporal metadata
        (startTime?.toString() ?: "") into "start_time"
        (endTime?.toString() ?: "") into "end_time"
        duration into "duration_minutes"
        (startTime?.hour ?: 0) into "start_hour"
        (startTime?.dayOfWeek?.ordinal ?: 0) into "day_of_week"
        (startTime?.dayOfWeek?.toString() ?: "Unknown") into "day_name"
        (startTime?.monthNumber ?: 0) into "month"
        (startTime?.date?.toString() ?: "") into "date"
    }
}