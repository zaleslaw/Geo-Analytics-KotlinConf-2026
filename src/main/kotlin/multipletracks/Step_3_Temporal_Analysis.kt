package com.zaleslaw.multipletracks

import com.zaleslaw.TRACKS_PATH
import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.analysis.addSpeedMetrics
import com.zaleslaw.analysis.addTemporalAttributes
import com.zaleslaw.analysis.calculateTrackStats
import com.zaleslaw.io.*
import com.zaleslaw.visualization.visualizeDurationByHour
import com.zaleslaw.visualization.visualizeDurationByMonth
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.print
import java.io.File

fun main() {
    val gpxFiles = File(TRACKS_PATH).listFiles { f -> f.extension.equals("gpx", ignoreCase = true) } ?: emptyArray()

    val tracksDf = gpxFiles.map { file ->
        DataFrame.readGPX(file.absolutePath)
            .add("filename") { file.name }
            .addDistanceColumns()
    }.reduce { acc, df -> acc.concat(df) }

    tracksDf.print()

    val temporalDf = tracksDf.addTemporalAttributes()


    // GPS-noise-resilient charts: based on track duration (timestamps only, no km)
    val trackStats = temporalDf
        .addSpeedMetrics()
        .calculateTrackStats()

    trackStats.print()

    trackStats.visualizeDurationByMonth("multipleTracks/duration_by_month.png")
    trackStats.visualizeDurationByHour("multipleTracks/duration_by_hour.png")
}
