package com.zaleslaw.onetrack

import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.analysis.printBasicStatistics
import com.zaleslaw.analysis.printDistanceStatistics
import com.zaleslaw.ONE_TRACK_PATH
import com.zaleslaw.io.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print


fun main() {
    val gpxDf = DataFrame.readGPX("${ONE_TRACK_PATH}.gpx")
    gpxDf.print()

    gpxDf.printBasicStatistics()

    val enrichedDf = gpxDf.addDistanceColumns()

    enrichedDf.printDistanceStatistics()
}
