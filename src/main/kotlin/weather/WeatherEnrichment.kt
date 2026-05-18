package com.zaleslaw.weather

import kotlinx.datetime.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * Enriches GPS track points with historical weather data using a local cache.
 */
suspend fun DataFrame<*>.enrichWithWeather(
    weatherService: WeatherService,
    useCache: Boolean = true
): DataFrame<*> {
    var cache = if (useCache) {
        WeatherCache.load() ?: WeatherCache.createEmpty()
    } else {
        WeatherCache.createEmpty()
    }

    val cacheHits = mutableListOf<Triple<Double, Double, LocalDateTime>>()
    val cacheMisses = mutableListOf<Triple<Double, Double, LocalDateTime>>()

    // Collect unique (lat, lon, hour) slots — one request per track-hour, not per point
    val uniqueLocations = mutableSetOf<Triple<Double, Double, LocalDateTime>>()

    val trackMap = mutableMapOf<String, MutableList<Int>>()
    for (i in 0 until this.rowsCount()) {
        val filename = this[i]["filename"] as? String ?: continue
        trackMap.getOrPut(filename) { mutableListOf() }.add(i)
    }

    trackMap.forEach { (_, indices) ->
        val startRow = this[indices.first()]
        val endRow = this[indices.last()]

        val startDateTime = startRow["datetime"] as? LocalDateTime ?: return@forEach
        val endDateTime = endRow["datetime"] as? LocalDateTime ?: return@forEach

        val startLat = startRow["lat"] as Double
        val startLon = startRow["lon"] as Double

        val startHourDateTime = LocalDateTime(startDateTime.date, LocalTime(startDateTime.hour, 0))
        uniqueLocations.add(Triple(startLat, startLon, startHourDateTime))

        var currentHour = startDateTime.hour + 1
        val endHour = endDateTime.hour
        while (currentHour <= endHour) {
            uniqueLocations.add(Triple(startLat, startLon,
                LocalDateTime(startDateTime.date, LocalTime(currentHour, 0))))
            currentHour++
        }

        if (uniqueLocations.size >= 10000) return@forEach
    }

    println("Found ${uniqueLocations.size} unique time slots for ${trackMap.size} tracks")

    uniqueLocations.forEach { location ->
        val (lat, lon, dateTime) = location
        if (cache.getFromCache(lat, lon, dateTime) != null) cacheHits.add(location)
        else cacheMisses.add(location)
    }

    println("Cache: ${cacheHits.size} hits, ${cacheMisses.size} misses")

    val weatherMap = mutableMapOf<Triple<Double, Double, LocalDateTime>, WeatherData>()

    cacheHits.forEach { (lat, lon, dateTime) ->
        cache.getFromCache(lat, lon, dateTime)?.let { weatherMap[Triple(lat, lon, dateTime)] = it }
    }

    if (cacheMisses.isNotEmpty()) {
        println("Fetching weather for ${cacheMisses.size} new time slots...")
        val newWeatherData = weatherService.getWeatherBatch(cacheMisses)
        println("Received ${newWeatherData.size} of ${cacheMisses.size} weather records")

        newWeatherData.forEach { (location, weather) ->
            val (lat, lon, dateTime) = location
            cache = cache.addToCache(lat, lon, dateTime, weather)
            weatherMap[location] = weather
        }

        if (useCache && newWeatherData.isNotEmpty()) {
            cache = cache.deduplicate()
            WeatherCache.save(cache)
        }
    }

    // Use the track's start coordinates for all its points (weather doesn't vary within a local walk)
    val filenameToCoords = trackMap.mapValues { (_, indices) ->
        val startRow = this[indices.first()]
        Pair(startRow["lat"] as Double, startRow["lon"] as Double)
    }

    return this
        .add("temperature") {
            val datetime = this["datetime"] as? LocalDateTime ?: return@add null
            val filename = this["filename"] as String
            val (lat, lon) = filenameToCoords[filename] ?: return@add null
            weatherMap[Triple(lat, lon, LocalDateTime(datetime.date, LocalTime(datetime.hour, 0)))]?.temperature
        }
        .add("precipitation") {
            val datetime = this["datetime"] as? LocalDateTime ?: return@add null
            val filename = this["filename"] as String
            val (lat, lon) = filenameToCoords[filename] ?: return@add null
            weatherMap[Triple(lat, lon, LocalDateTime(datetime.date, LocalTime(datetime.hour, 0)))]?.precipitation
        }
        .add("weather_category") {
            val datetime = this["datetime"] as? LocalDateTime ?: return@add null
            val filename = this["filename"] as String
            val (lat, lon) = filenameToCoords[filename] ?: return@add null
            weatherMap[Triple(lat, lon, LocalDateTime(datetime.date, LocalTime(datetime.hour, 0)))]?.weatherCategory
        }
        .add("weather_description") {
            val datetime = this["datetime"] as? LocalDateTime ?: return@add null
            val filename = this["filename"] as String
            val (lat, lon) = filenameToCoords[filename] ?: return@add null
            weatherMap[Triple(lat, lon, LocalDateTime(datetime.date, LocalTime(datetime.hour, 0)))]?.weatherDescription
        }
        .add("wind_speed") {
            val datetime = this["datetime"] as? LocalDateTime ?: return@add null
            val filename = this["filename"] as String
            val (lat, lon) = filenameToCoords[filename] ?: return@add null
            weatherMap[Triple(lat, lon, LocalDateTime(datetime.date, LocalTime(datetime.hour, 0)))]?.windSpeed
        }
}

/**
 * Aggregates point-level weather to one row per track (dominant category, averages).
 */
fun DataFrame<*>.aggregateWeatherByTrack(): DataFrame<*> {
    return this.groupBy { "filename"<String>() }.aggregate {
        val temps = rows().mapNotNull { it["temperature"] as? Double }
        val precips = rows().mapNotNull { it["precipitation"] as? Double }
        val winds = rows().mapNotNull { it["wind_speed"] as? Double }
        val categories = rows().mapNotNull { it["weather_category"] as? String }
        val descriptions = rows().mapNotNull { it["weather_description"] as? String }
        val datetimes = rows().mapNotNull { it["datetime"] as? kotlinx.datetime.LocalDateTime }

        (if (temps.isNotEmpty()) temps.average() else null) into "avg_temperature"
        (if (precips.isNotEmpty()) precips.average() else null) into "avg_precipitation"
        (if (winds.isNotEmpty()) winds.average() else null) into "avg_wind_speed"
        (categories.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key) into "weather_category"
        (descriptions.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key) into "weather_description"
        (datetimes.firstOrNull()?.date?.toString()) into "date"
    }
}

/**
 * Prints per-track weather summary.
 */
fun DataFrame<*>.printWeatherStats() {
    println("\n=== Weather statistics per track ===")
    println("Total tracks: ${this.rowsCount()}")
    if (this.rowsCount() == 0) { println("No data"); return }

    for (i in 0 until this.rowsCount()) {
        val filename = this[i]["filename"] as String
        val date = this[i]["date"] as? String ?: "N/A"
        val temp = this[i]["avg_temperature"] as? Double
        val precip = this[i]["avg_precipitation"] as? Double
        val category = this[i]["weather_category"] as? String ?: "N/A"
        val description = this[i]["weather_description"] as? String ?: "N/A"

        println("\n$filename ($date)")
        if (temp != null) println("  Temperature: %.1f°C".format(temp))
        if (precip != null) println("  Precipitation: %.1f mm/h".format(precip))
        println("  Conditions: $category — $description")
    }
}