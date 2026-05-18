package com.zaleslaw.experiments.onetrack

import com.zaleslaw.geo.toGeoDataFrame
import com.zaleslaw.BERLIN_SHAPEFILE_PATH
import com.zaleslaw.ONE_TRACK_FILENAME
import com.zaleslaw.ONE_TRACK_PATH
import com.zaleslaw.io.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.geo.GeoDataFrame
import org.jetbrains.kotlinx.dataframe.geo.io.readShapefile
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.withData
import org.jetbrains.kotlinx.kandy.letsplot.geo.layers.geoPoints
import org.jetbrains.kotlinx.kandy.letsplot.geo.layers.geoPolygon
import org.jetbrains.kotlinx.kandy.letsplot.scales.guide.model.limits
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.letsplot.y

/**
 * Example demonstrating visualization of a single GPX track overlaid on Berlin district boundaries.
 * 
 * This example:
 * 1. Reads a GPX file and converts it to a GeoDataFrame
 * 2. Loads Berlin Bezirke (districts) shapefile
 * 3. Converts the shapefile from Web Mercator to WGS84 coordinate system
 * 4. Creates two visualizations:
 *    - Berlin districts only
 *    - Berlin districts with the GPX track overlaid as points
 * 
 * The visualizations are saved as PNG files for inspection.
 */
fun main() {
    val gpxDf = DataFrame.readGPX("${ONE_TRACK_PATH}.gpx")
    gpxDf.print()

    // Convert to GeoDataFrame
    val geoDataFrame = gpxDf.toGeoDataFrame()

    // Read Berlin Bezirke shapefile
    val berlinBezirke = GeoDataFrame.readShapefile(BERLIN_SHAPEFILE_PATH)

    berlinBezirke.df.print()

    // Convert Web Mercator in WGS84 (EPSG:4326)
    val wgs84Crs = org.geotools.referencing.CRS.decode("EPSG:4326", true)
    val berlinWgs84 = berlinBezirke.applyCrs(wgs84Crs)

    // Plot Berlin Bezirke
    berlinWgs84.plot {
        geoPolygon()
    }.save("berlinBezirke.png")

    // Plot Berlin Bezirke with my track
    berlinWgs84.plot {
        x.axis.limits = 13.3..13.6
        y.axis.limits = 52.5..52.6

        geoPolygon()
        withData(geoDataFrame) {
            geoPoints {
                size = 1.5
            }
        }
    }.save("berlinBezirke_${ONE_TRACK_FILENAME}.png")
}
