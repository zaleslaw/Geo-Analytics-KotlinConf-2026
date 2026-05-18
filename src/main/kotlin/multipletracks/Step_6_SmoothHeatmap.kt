package com.zaleslaw.multipletracks

import com.zaleslaw.analysis.addDistanceColumns
import com.zaleslaw.BERLIN_SHAPEFILE_PATH
import com.zaleslaw.TRACKS_PATH
import com.zaleslaw.io.readGPX
import com.zaleslaw.visualization.createSmoothHeatmap
import org.geotools.referencing.CRS
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.geo.GeoDataFrame
import org.jetbrains.kotlinx.dataframe.geo.io.readShapefile
import java.io.File

fun main() {
    val dir = "multipleTracks"

    val pointsDf = File(TRACKS_PATH)
        .listFiles { f -> f.extension.equals("gpx", ignoreCase = true) }
        ?.map { file ->
            DataFrame.readGPX(file.absolutePath)
                .add("filename") { file.name }
                .addDistanceColumns()
        }
        ?.reduce { acc, df -> acc.concat(df) }
        ?: return

    println("Loaded ${pointsDf.rowsCount()} points")

    val berlinPoints = pointsDf.filter {
        (this["lat"] as Double) in 52.32..52.68 &&
        (this["lon"] as Double) in 13.05..13.75
    }
    println("Berlin points: ${berlinPoints.rowsCount()}")

    // Load Berlin shapefile + convert to WGS84
    val berlinBezirke = GeoDataFrame.readShapefile(BERLIN_SHAPEFILE_PATH)
    val wgs84 = CRS.decode("EPSG:4326", true)
    val berlinWgs84 = berlinBezirke.applyCrs(wgs84)

    // Chart 1: full Berlin — neighbourhood-scale blur
    // cell ≈ 220 m  →  blurRadius ≈ 2 cells  →  physical radius ~440 m
    berlinPoints.createSmoothHeatmap(
        berlinGeoDataFrame = berlinWgs84,
        xRange = 13.08..13.73,
        yRange = 52.34..52.67,
        resolution = 200,
        blurMeters = 450.0,
        blurPasses = 3,
        outputPath = "$dir/heatmap_smooth_berlin_full.png",
        title = "Walk Heatmap (smooth) — Berlin"
    )

    // Chart 2: Mitte + Prenzlauer Berg — block-scale blur
    // cell ≈ 43 m  →  blurRadius ≈ 6 cells  →  physical radius ~260 m
    berlinPoints.createSmoothHeatmap(
        berlinGeoDataFrame = berlinWgs84,
        xRange = 13.33..13.52,
        yRange = 52.49..52.565,
        resolution = 300,
        blurMeters = 250.0,
        blurPasses = 3,
        outputPath = "$dir/heatmap_smooth_mitte.png",
        title = "Walk Heatmap (smooth) — Mitte & Prenzlauer Berg"
    )

    // Chart 3: Mitte tight zoom — street-level blur
    // cell ≈ 20 m  →  blurRadius ≈ 17 cells  →  physical radius ~350 m
    // 350 m gives visible glowing bands along streets without merging into blobs
    berlinPoints.createSmoothHeatmap(
        berlinGeoDataFrame = berlinWgs84,
        xRange = 13.36..13.48,
        yRange = 52.505..52.545,
        resolution = 400,
        blurMeters = 200.0,
        blurPasses = 3,
        outputPath = "$dir/heatmap_smooth_mitte_tight.png",
        title = "Walk Heatmap (smooth) — Mitte (street level)"
    )
}
