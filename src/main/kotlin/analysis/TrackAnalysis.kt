package com.zaleslaw.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.prev
import kotlin.math.abs

fun DataFrame<*>.addDistanceColumns(): DataFrame<*> {
    return this
        .addHaversineDistance()
        .addEuclideanDistance()
        .addDistanceDelta()
}

fun DataFrame<*>.printBasicStatistics() {
    println("\n=== Basic Statistics ===")
    println("Number of points: ${this.rowsCount()}")

    val latitudes = (0 until rowsCount()).map { this[it]["lat"] as Double }
    val longitudes = (0 until rowsCount()).map { this[it]["lon"] as Double }

    println("Latitude - min: ${latitudes.minOrNull()}, max: ${latitudes.maxOrNull()}")
    println("Longitude - min: ${longitudes.minOrNull()}, max: ${longitudes.maxOrNull()}")
}

fun DataFrame<*>.printDistanceStatistics() {
    val haversineDistances = (1 until rowsCount()).map { this[it]["haversineDistance"] as Double }
    val euclideanDistances = (1 until rowsCount()).map { this[it]["euclideanDistance"] as Double }
    val deltas = (1 until rowsCount()).map { this[it]["distanceDelta"] as Double }

    val totalHaversine = haversineDistances.sum()
    val totalEuclidean = euclideanDistances.sum()

    println("\n=== Distances ===")
    println("Total haversine distance: %.2f m (%.2f km)".format(totalHaversine, totalHaversine / 1000))
    println("Total euclidean distance: %.2f m (%.2f km)".format(totalEuclidean, totalEuclidean / 1000))
    println("Total delta: %.2f m (%.4f%%)".format(
        totalHaversine - totalEuclidean,
        (totalHaversine - totalEuclidean) / totalHaversine * 100
    ))
    println("Average haversine distance: %.2f m".format(haversineDistances.average()))
    println("Average euclidean distance: %.2f m".format(euclideanDistances.average()))
    println("Average delta: %.6f m".format(deltas.average()))
    println("Max delta: %.6f m".format(deltas.maxOrNull() ?: 0.0))
}

private fun DataFrame<*>.addHaversineDistance(): DataFrame<*> {
    return this.add("haversineDistance") {
        if (index() == 0) {
            0.0
        } else {
            val prevRow = prev()!!
            haversine(
                prevRow["lat"] as Double, prevRow["lon"] as Double,
                this["lat"] as Double, this["lon"] as Double
            )
        }
    }
}

private fun DataFrame<*>.addEuclideanDistance(): DataFrame<*> {
    return this.add("euclideanDistance") {
        if (index() == 0) {
            0.0
        } else {
            val prevRow = prev()!!
            euclideanDistance(
                prevRow["lat"] as Double, prevRow["lon"] as Double,
                this["lat"] as Double, this["lon"] as Double
            )
        }
    }
}

private fun DataFrame<*>.addDistanceDelta(): DataFrame<*> {
    return this.add("distanceDelta") {
        val haversine = this["haversineDistance"] as Double
        val euclidean = this["euclideanDistance"] as Double
        haversine - euclidean
    }
}
