package com.zaleslaw.visualization

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.geo.GeoDataFrame
import org.jetbrains.kotlinx.dataframe.geo.toGeo
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.geo.dsl.withData
import org.jetbrains.kotlinx.kandy.letsplot.geo.layers.geoPolygon
import org.jetbrains.kotlinx.kandy.letsplot.scales.continuousColorGradientN
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.letsplot.y
import org.jetbrains.kotlinx.kandy.letsplot.scales.guide.model.limits
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.context.invoke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

/**
 * Google-Maps-style smooth heatmap via 2-D Gaussian blur (box-blur × blurPasses).
 *
 * Algorithm:
 *   1. Bin GPS points into a fine [resolution × resolution] grid.
 *   2. Apply separable box blur blurPasses times — converges to a Gaussian kernel.
 *   3. Log-normalise to 0..1 so sparse areas still show colour.
 *   4. Render non-zero cells as JTS tile polygons with a heat colour scale.
 *
 * @param resolution  grid cells per axis — 200–400 is a good range
 * @param blurMeters  Gaussian radius in real metres — same value works at any zoom level
 * @param blurPasses  3 passes → visually indistinguishable from true Gaussian
 */
fun DataFrame<*>.createSmoothHeatmap(
    berlinGeoDataFrame: GeoDataFrame<*>,
    xRange: ClosedRange<Double>,
    yRange: ClosedRange<Double>,
    resolution: Int = 250,
    blurMeters: Double = 300.0,
    blurPasses: Int = 3,
    outputPath: String = "heatmap_smooth.png",
    title: String = "GPS Heatmap (smooth)"
) {
    println("\n=== Creating Smooth Heatmap: $title ===")

    val xMin = xRange.start; val xMax = xRange.endInclusive
    val yMin = yRange.start; val yMax = yRange.endInclusive
    val cellW = (xMax - xMin) / resolution
    val cellH = (yMax - yMin) / resolution

    // Convert blurMeters → blur radius in grid cells, accounting for latitude distortion
    val midLat = (yMin + yMax) / 2
    val metersPerCell = (xMax - xMin) * 111_320.0 * cos(midLat * PI / 180.0) / resolution
    val blurRadius = (blurMeters / metersPerCell).toInt().coerceAtLeast(1)
    println("  blurMeters=$blurMeters  cellSize=${"%.0f".format(metersPerCell)}m  blurRadius=$blurRadius cells")

    // 1. Bin into grid
    val grid = Array(resolution) { DoubleArray(resolution) }
    for (i in 0 until rowsCount()) {
        val lon = this[i]["lon"] as Double
        val lat = this[i]["lat"] as Double
        if (lon !in xRange || lat !in yRange) continue
        val xi = ((lon - xMin) / cellW).toInt().coerceIn(0, resolution - 1)
        val yi = ((lat - yMin) / cellH).toInt().coerceIn(0, resolution - 1)
        grid[yi][xi] += 1.0
    }

    // 2. Gaussian blur (separable box blur × blurPasses)
    repeat(blurPasses) { separableBoxBlur(grid, resolution, blurRadius) }

    val maxVal = grid.flatMap { it.toList() }.maxOrNull() ?: return
    val threshold = maxVal * 0.005  // skip near-empty cells — transparent background
    println("  max density: ${"%.2f".format(maxVal)}  threshold: ${"%.3f".format(threshold)}")

    // 3. Build tile polygons for non-zero cells
    val geomFactory = GeometryFactory()
    val halfW = cellW / 2; val halfH = cellH / 2

    val lonsList  = mutableListOf<Double>()
    val latsList  = mutableListOf<Double>()
    val valueList = mutableListOf<Double>()
    val geomList  = mutableListOf<Geometry>()

    for (yi in 0 until resolution) {
        for (xi in 0 until resolution) {
            val raw = grid[yi][xi]
            if (raw < threshold) continue
            val lon  = xMin + (xi + 0.5) * cellW
            val lat  = yMin + (yi + 0.5) * cellH
            val norm = ln(1.0 + raw) / ln(1.0 + maxVal)   // log scale
            lonsList  += lon
            latsList  += lat
            valueList += norm
            geomList  += geomFactory.createPolygon(arrayOf(
                Coordinate(lon - halfW, lat - halfH),
                Coordinate(lon + halfW, lat - halfH),
                Coordinate(lon + halfW, lat + halfH),
                Coordinate(lon - halfW, lat + halfH),
                Coordinate(lon - halfW, lat - halfH)
            )) as Geometry
        }
    }
    println("  Non-zero smooth cells: ${lonsList.size}")

    val smoothGeo = dataFrameOf(
        "lon"      to lonsList,
        "lat"      to latsList,
        "value"    to valueList,
        "geometry" to geomList
    ).toGeo(GeoDataFrame.DEFAULT_CRS)

    // 4. Plot: Berlin outline + smooth density layer
    berlinGeoDataFrame.plot {
        x.axis.limits = xMin..xMax
        y.axis.limits = yMin..yMax

        geoPolygon {
            fillColor = Color.BLACK
            borderLine { color = Color.hex("#444444"); width = 0.5 }
            alpha = 0.85
        }

        withData(smoothGeo) {
            geoPolygon {
                fillColor("value") {
                    scale = continuousColorGradientN(listOf(
                        Color.hex("#0000ff"),  // blue   — very cold
                        Color.hex("#00e5ff"),  // cyan
                        Color.hex("#00ff00"),  // green
                        Color.hex("#ffff00"),  // yellow
                        Color.hex("#ff6600"),  // orange
                        Color.hex("#ff0000"),  // red    — hottest
                    ))
                }
                alpha = 0.80
                borderLine { width = 0.0 }
            }
        }

        layout {
            this.title = title
            size = 1200 to 900
        }
    }.save(outputPath)

    println("Smooth heatmap saved: $outputPath")
}

private fun separableBoxBlur(grid: Array<DoubleArray>, n: Int, r: Int) {
    val w = (2 * r + 1).toDouble()
    val temp = Array(n) { DoubleArray(n) }
    for (y in 0 until n)
        for (x in 0 until n) {
            var sum = 0.0
            for (k in -r..r) sum += grid[y][(x + k).coerceIn(0, n - 1)]
            temp[y][x] = sum / w
        }
    for (y in 0 until n)
        for (x in 0 until n) {
            var sum = 0.0
            for (k in -r..r) sum += temp[(y + k).coerceIn(0, n - 1)][x]
            grid[y][x] = sum / w
        }
}
