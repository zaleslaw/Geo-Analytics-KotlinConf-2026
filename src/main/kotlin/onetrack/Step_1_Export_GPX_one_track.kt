package com.zaleslaw.onetrack

import com.zaleslaw.ONE_TRACK_PATH
import com.zaleslaw.io.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print

fun main() {
    val gpxDf = DataFrame.readGPX("${ONE_TRACK_PATH}.gpx")
    gpxDf.print()
}