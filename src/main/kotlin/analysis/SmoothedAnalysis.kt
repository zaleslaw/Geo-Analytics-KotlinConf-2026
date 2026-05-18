package com.zaleslaw.analysis

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * Aggregates GPS points from the current DataFrame into intervals based on the specified time duration.
 * Each interval is processed to calculate average latitude, longitude, elevation, and summary statistics
 * such as distance and speed between intervals. Distance and speed between intervals are recalculated
 * using consecutive averaged positions to account for noise reduction.
 *
 * @param intervalSeconds The duration, in seconds, for each interval. Defaults to 10 seconds.
 * @return A new DataFrame containing the aggregated GPS points for each interval. The resulting DataFrame
 * includes columns for filename, datetime, latitude, longitude, elevation, distance, speed, and point count.
 */
fun DataFrame<*>.aggregatePointsByTime(intervalSeconds: Int = 10): DataFrame<*> {
    println("\n=== Aggregating GPS points with $intervalSeconds second intervals ===")

    val result = mutableListOf<MutableMap<String, Any?>>()

    // Process each track separately so distance/speed calculations don't bleed across tracks
    val grouped = this.groupBy { "filename"<String>() }

    for (group in grouped.groups) {
        val filename = group["filename"][0] as String
        val trackDf = group

        if (trackDf.rowsCount() == 0) continue

        val sortedTrack = trackDf.sortBy("datetime")

        var intervalStart: LocalDateTime? = null
        var intervalPoints = mutableListOf<Map<String, Any?>>()
        val trackIntervals = mutableListOf<MutableMap<String, Any?>>()

        for (i in 0 until sortedTrack.rowsCount()) {
            val currentTime = sortedTrack[i]["datetime"] as? LocalDateTime ?: continue

            if (intervalStart == null) {
                intervalStart = currentTime
                intervalPoints.add(sortedTrack[i].toMap())
                continue
            }

            val startInstant = intervalStart.toInstant(TimeZone.currentSystemDefault())
            val currentInstant = currentTime.toInstant(TimeZone.currentSystemDefault())
            val diffSeconds = (currentInstant - startInstant).inWholeSeconds

            if (diffSeconds < intervalSeconds) {
                intervalPoints.add(sortedTrack[i].toMap())
            } else {
                if (intervalPoints.isNotEmpty()) {
                    trackIntervals.add(aggregateInterval(intervalPoints, filename))
                }
                intervalStart = currentTime
                intervalPoints = mutableListOf(sortedTrack[i].toMap())
            }
        }

        if (intervalPoints.isNotEmpty()) {
            trackIntervals.add(aggregateInterval(intervalPoints, filename))
        }

        // Recalculate distance and speed between consecutive averaged positions.
        // Summing raw haversineDistance within a window inflates noise; the correct
        // distance is haversine(prev_avg_position, curr_avg_position).
        if (trackIntervals.isNotEmpty()) {
            trackIntervals[0]["distance_m"] = 0.0
            trackIntervals[0]["speed_kmh"] = 0.0
            for (i in 1 until trackIntervals.size) {
                val prev = trackIntervals[i - 1]
                val curr = trackIntervals[i]
                val dist = haversine(
                    prev["lat"] as Double, prev["lon"] as Double,
                    curr["lat"] as Double, curr["lon"] as Double
                )
                val prevTime = prev["datetime"] as LocalDateTime
                val currTime = curr["datetime"] as LocalDateTime
                val timeSec = (currTime.toInstant(TimeZone.currentSystemDefault()) -
                        prevTime.toInstant(TimeZone.currentSystemDefault())).inWholeSeconds
                curr["distance_m"] = dist
                curr["speed_kmh"] = if (timeSec > 0) (dist / timeSec) * 3.6 else 0.0
            }
        }

        result.addAll(trackIntervals)
    }

    if (result.isEmpty()) {
        return dataFrameOf(
            "filename" to emptyList<String>(),
            "datetime" to emptyList<LocalDateTime>(),
            "lat" to emptyList<Double>(),
            "lon" to emptyList<Double>(),
            "ele" to emptyList<Double>(),
            "distance_m" to emptyList<Double>(),
            "speed_kmh" to emptyList<Double>(),
            "point_count" to emptyList<Int>()
        )
    }

    val aggregatedDf = dataFrameOf(
        "filename" to result.map { it["filename"] as String },
        "datetime" to result.map { it["datetime"] as LocalDateTime },
        "lat" to result.map { it["lat"] as Double },
        "lon" to result.map { it["lon"] as Double },
        "ele" to result.map { it["ele"] as Double },
        "distance_m" to result.map { it["distance_m"] as Double },
        "speed_kmh" to result.map { it["speed_kmh"] as Double },
        "point_count" to result.map { it["point_count"] as Int }
    )

    println("Aggregated ${this.rowsCount()} points into ${aggregatedDf.rowsCount()} intervals")

    return aggregatedDf
}

private fun aggregateInterval(points: List<Map<String, Any?>>, filename: String): MutableMap<String, Any?> {

    val avgLat = points.mapNotNull { it["lat"] as? Double }.average()
    val avgLon = points.mapNotNull { it["lon"] as? Double }.average()
    val avgEle = points.mapNotNull { it["ele"] as? Double }.average()

    val datetime = points.first()["datetime"] as LocalDateTime

    val totalDistance = points.mapNotNull { it["haversineDistance"] as? Double }.sum()

    val firstTime = points.first()["datetime"] as LocalDateTime
    val lastTime = points.last()["datetime"] as LocalDateTime
    val firstInstant = firstTime.toInstant(TimeZone.currentSystemDefault())
    val lastInstant = lastTime.toInstant(TimeZone.currentSystemDefault())
    val intervalSeconds = (lastInstant - firstInstant).inWholeSeconds

    val speed = if (intervalSeconds > 0) {
        (totalDistance / intervalSeconds) * 3.6
    } else {
        0.0
    }

    return mutableMapOf(
        "filename" to filename,
        "datetime" to datetime,
        "lat" to avgLat,
        "lon" to avgLon,
        "ele" to avgEle,
        "distance_m" to totalDistance, // replaced by post-processing in aggregatePointsByTime
        "speed_kmh" to speed,          // replaced by post-processing in aggregatePointsByTime
        "point_count" to points.size
    )
}
