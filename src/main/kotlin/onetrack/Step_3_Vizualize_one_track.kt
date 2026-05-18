package com.zaleslaw.onetrack

import com.zaleslaw.geo.toGeoDataFrame
import com.zaleslaw.ONE_TRACK_FILENAME
import com.zaleslaw.ONE_TRACK_PATH
import com.zaleslaw.io.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.geo.layers.geoPoints
import org.jetbrains.kotlinx.kandy.util.color.Color

/**
 * Reads a GPX file, processes the data, and generates a visualization of the track.
 *
 * The process includes the following steps:
 * 1. Reads the GPX file into a `DataFrame`.
 * 2. Prints the loaded data from the GPX file for inspection.
 * 3. Converts the `DataFrame` containing track points to a `GeoDataFrame` for geospatial operations.
 * 4. Creates a plot of the track points with customization for visual clarity, such as setting the
 *    point size and color.
 * 5. Saves the generated plot as an image file in PNG format.
 */
fun main() {
    val gpxDf = DataFrame.readGPX("${ONE_TRACK_PATH}.gpx")
    gpxDf.print()

    // Convert to GeoDataFrame
    val geoDataFrame = gpxDf.toGeoDataFrame()

    // Build and save the plot
    geoDataFrame.plot {
        geoPoints {
            size = 2.0
            color = Color.ORANGE
        }
    }.save("${ONE_TRACK_FILENAME}.png")
}
