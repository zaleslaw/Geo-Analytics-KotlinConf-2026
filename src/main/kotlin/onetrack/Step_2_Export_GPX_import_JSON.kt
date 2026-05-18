package com.zaleslaw.onetrack

import com.zaleslaw.ONE_TRACK_PATH
import com.zaleslaw.io.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.io.readJson
import org.jetbrains.kotlinx.dataframe.io.writeJson

/**
 * Entry point of the program that processes a GPX file, converts it to JSON, and verifies the conversion.
 *
 * The method performs the following steps:
 * 1. Reads a GPX file and parses its contents into a `DataFrame`.
 * 2. Prints the contents of the `DataFrame` for inspection.
 * 3. Exports the `DataFrame` to a JSON file.
 * 4. Reads the JSON file back into a `DataFrame` to ensure the data integrity.
 * 5. Prints the reloaded `DataFrame` to verify the JSON conversion.
 */
fun main() {
    val gpxDf = DataFrame.readGPX("${ONE_TRACK_PATH}.gpx")
    gpxDf.print()

    gpxDf.writeJson("${ONE_TRACK_PATH}.json")

    // check JSON is correct
    val fromJson = DataFrame.readJson("${ONE_TRACK_PATH}.json")
    fromJson.print()
}