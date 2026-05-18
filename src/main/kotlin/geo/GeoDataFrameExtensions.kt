package com.zaleslaw.geo

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.geo.GeoDataFrame
import org.jetbrains.kotlinx.dataframe.geo.toGeo
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory

fun DataFrame<*>.toGeoDataFrame(): GeoDataFrame<*> {
    val geometryFactory = GeometryFactory()

    val latColumn = this["lat"]
    val lonColumn = this["lon"]
    val altitudeColumn = if (columnNames().contains("altitude")) this["altitude"] else null

    val geometries = (0 until rowsCount()).map { i ->
        val lat = latColumn[i] as? Double ?: error("Row $i: 'lat' must be Double")
        val lon = lonColumn[i] as? Double ?: error("Row $i: 'lon' must be Double")
        val altitude = altitudeColumn?.get(i) as? Double

        val coordinate = if (altitude != null) {
            Coordinate(lon, lat, altitude)
        } else {
            Coordinate(lon, lat)
        }

        geometryFactory.createPoint(coordinate) as Geometry
    }

    return this.add("geometry") { geometries[index()] }.toGeo(GeoDataFrame.DEFAULT_CRS)
}
