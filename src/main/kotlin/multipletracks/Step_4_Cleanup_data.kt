package com.zaleslaw.multipletracks

import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.analysis.addSpeedMetrics
import com.zaleslaw.analysis.addTemporalAttributes
import com.zaleslaw.analysis.aggregatePointsByTime
import com.zaleslaw.analysis.calculateTrackStats
import com.zaleslaw.TRACKS_PATH
import com.zaleslaw.io.readGPX
import com.zaleslaw.visualization.visualizeSmoothedSpeedDistribution
import com.zaleslaw.visualization.visualizeTrackSpeedDistribution
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.print
import java.io.File

fun main() {
    val dir = "multipleTracks"

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
        ?: return

    println("Loaded ${pointsDf.rowsCount()} points from all tracks")

    // ── BEFORE: raw speed distributions ───────────────────────────────────
    println("\n--- BEFORE cleanup ---")
    pointsDf.visualizeSmoothedSpeedDistribution("$dir/speed_raw.png")
    pointsDf.calculateTrackStats().visualizeTrackSpeedDistribution("$dir/track_speed_raw.png")

    // ── Algorithm A: distance-jump filter ─────────────────────────────────
    // If a single GPS ping jumps >100 m, it's satellite noise, not walking.
    val cleanedA = pointsDf.filter { "haversineDistance"<Double>() < 100.0 }
    println("\nAlgorithm A (distance jump filter): ${pointsDf.rowsCount()} → ${cleanedA.rowsCount()} points")

    // ── Algorithm B: time-interval smoothing (10-second windows) ──────────
    // Average positions within each 10-second window; recalculate speed from
    // aggregated distance — noise averages out, real movement is preserved.
    val smoothedB = pointsDf.aggregatePointsByTime(intervalSeconds = 10)
    println("Algorithm B (10-sec aggregation): ${pointsDf.rowsCount()} → ${smoothedB.rowsCount()} intervals")

    // ── AFTER: cleaned speed distributions ────────────────────────────────
    println("\n--- AFTER cleanup ---")
    cleanedA.visualizeSmoothedSpeedDistribution("$dir/speed_cleaned_A.png")
    smoothedB.visualizeSmoothedSpeedDistribution("$dir/speed_smoothed_B.png")
    cleanedA.calculateTrackStats().also { it.print() }
        .visualizeTrackSpeedDistribution("$dir/track_speed_cleaned.png")

    // calculateTrackStats() needs haversineDistance; smoothedB uses distance_m
    smoothedB
        .add("haversineDistance") { this["distance_m"] as Double }
        .calculateTrackStats()
        .visualizeTrackSpeedDistribution("$dir/track_speed_smoothed.png")
}
