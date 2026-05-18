package com.zaleslaw.multipletracks

import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.TRACKS_PATH
import com.zaleslaw.io.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import java.io.File

fun main() {
    val tracksDir = File(TRACKS_PATH)
    val gpxFiles = tracksDir.listFiles { file -> file.extension == "gpx" } ?: emptyArray()

    println("Found ${gpxFiles.size} GPX files")

    val allDataFrames = mutableListOf<DataFrame<*>>()

    for (gpxFile in gpxFiles) {
        val df = DataFrame.readGPX(gpxFile.absolutePath)

        val enrichedDf = df
            .addDistanceColumns()
            .add("filename") { gpxFile.name }

        allDataFrames.add(enrichedDf)
    }

    println("The number of created DataFrames: ${allDataFrames.size}")
}