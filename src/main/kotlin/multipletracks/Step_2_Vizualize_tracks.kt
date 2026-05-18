package com.zaleslaw.multipletracks

import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.geo.toGeoDataFrame
import com.zaleslaw.BERLIN_SHAPEFILE_PATH
import com.zaleslaw.TRACKS_PATH
import com.zaleslaw.io.*
import org.geotools.referencing.CRS
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.geo.GeoDataFrame
import org.jetbrains.kotlinx.dataframe.geo.io.readShapefile
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.withData
import org.jetbrains.kotlinx.kandy.letsplot.geo.layers.geoPoints
import org.jetbrains.kotlinx.kandy.letsplot.geo.layers.geoPolygon
import org.jetbrains.kotlinx.kandy.letsplot.scales.guide.model.limits
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.letsplot.y
import org.jetbrains.kotlinx.kandy.util.color.Color
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

    if (allDataFrames.isNotEmpty()) {
        println("\n========================================")
        println("=== Combined DataFrame Statistics ===")
        println("========================================")

        val combinedDf = allDataFrames.reduce { acc, df -> acc.concat(df) }

        println("Total points: ${combinedDf.rowsCount()}")
        println("Total files: ${gpxFiles.size}")

        // Calculate the boundary lines of all tracks
        val allLats = (0 until combinedDf.rowsCount()).map { combinedDf[it]["lat"] as Double }
        val allLons = (0 until combinedDf.rowsCount()).map { combinedDf[it]["lon"] as Double }

        println("\n=== Coordinate Bounds ===")
        println("Latitude range: ${allLats.minOrNull()} to ${allLats.maxOrNull()}")
        println("Longitude range: ${allLons.minOrNull()} to ${allLons.maxOrNull()}")

        // Common Distance
        val totalHaversineDistance = (1 until combinedDf.rowsCount())
            .map { combinedDf[it]["haversineDistance"] as Double }
            .sum()

        println("\n=== Total Distance (All Tracks) ===")
        println("Total distance: %.2f m (%.2f km)".format(totalHaversineDistance, totalHaversineDistance / 1000))

        println("\n=== First 10 Rows ===")
        println(combinedDf)

        val geoDataFrame = combinedDf.toGeoDataFrame()

        geoDataFrame.plot {
            geoPoints {
                size = 1.5
            }
            layout {
                title = "All GPX Tracks"
                size = 1200 to 800
            }
        }.save("multipleTracks/all_gpx_tracks.png")

        // It's too small picture, let's filter Berlin only
        val berlinPoints = geoDataFrame.modify {
            filter {
                (it["lat"] as Double) in 52.0..52.67 && (it["lon"] as Double) in 13.05..13.8
            }
        }

        berlinPoints.df.print()

        berlinPoints.plot {
            geoPoints {
                size = 1.5
                color = Color.ORANGE
            }
            layout {
                title = "GPX Tracks in Berlin"
                size = 1200 to 800
            }
        }.save("multipleTracks/berlin_gpx_tracks.png")

        val berlinBezirke = GeoDataFrame.readShapefile(BERLIN_SHAPEFILE_PATH)

        berlinBezirke.df.print()

        // Convert Web Mercator в WGS84 (EPSG:4326)
        val wgs84Crs = CRS.decode("EPSG:4326", true)
        val berlinWgs84 = berlinBezirke.applyCrs(wgs84Crs)

        berlinWgs84.plot {
            geoPolygon()
        }.save("multipleTracks/berlinBezirke.png")

        berlinWgs84.plot {
            x.axis.limits = 13.0..13.8
            y.axis.limits = 52.3..52.7

            geoPolygon()
            withData(berlinPoints) {
                geoPoints {
                    size = 1.5
                    color = Color.RED
                }
            }
            layout {
                title = "GPX Tracks in Berlin"
                size = 1200 to 800
            }
        }.save("multipleTracks/berlin_gpx_tracks_bezirk_layer.png")


        // Let's zoom to Pankow + Mitte
        berlinWgs84.plot {
            x.axis.limits = 13.3..13.6
            y.axis.limits = 52.5..52.6

            geoPolygon()
            withData(berlinPoints) {
                geoPoints {
                    size = 1.5
                    color = Color.ORANGE
                }
            }
            layout {
                title = "GPX Tracks in Pankow and Mitte"
                size = 1200 to 800
            }
        }.save("multipleTracks/berlin_gpx_tracks_bezirk_layer_pankow_mitte.png")
    }
}
