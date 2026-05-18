package com.zaleslaw.io

import com.zaleslaw.model.TrackPoint
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.DataFrame
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

fun DataFrame.Companion.readGPX(filePath: String): DataFrame<*> {
    val points = parseGPX(filePath)
    return dataFrameOf(
        "lat" to points.map { it.lat },
        "lon" to points.map { it.lon },
        "time" to points.map { it.time },
        "altitude" to points.map { it.altitude }
    )
}

private fun parseGPX(filePath: String): List<TrackPoint> {
    val points = mutableListOf<TrackPoint>()
    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = docBuilder.parse(File(filePath))

    val trackPoints = doc.getElementsByTagName("trkpt")
    for (i in 0 until trackPoints.length) {
        val node = trackPoints.item(i)
        val lat = node.attributes.getNamedItem("lat").nodeValue.toDouble()
        val lon = node.attributes.getNamedItem("lon").nodeValue.toDouble()

        val children = node.childNodes
        var time = ""
        var altitude: Double? = null

        for (j in 0 until children.length) {
            when (children.item(j).nodeName) {
                "time" -> time = children.item(j).textContent
                "ele" -> altitude = children.item(j).textContent.toDoubleOrNull()
            }
        }

        points.add(TrackPoint(lat, lon, time, altitude))
    }
    return points
}
