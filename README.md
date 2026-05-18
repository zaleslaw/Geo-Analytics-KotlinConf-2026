# Geo Analytics with Kotlin — KotlinConf 2026

A step-by-step tutorial for analyzing GPS walking tracks using Kotlin DataFrame, Kandy, and the Open-Meteo weather API. The dataset is 30+ walks recorded in Berlin between 2023 and 2025.

No prior experience with geospatial data is required.

---

## What you will build

| Section | Input | Output |
|---------|-------|--------|
| Part 1 — Single Track | One GPX file | Map, distance stats, speed metrics |
| Part 2 — Multiple Tracks | 30+ GPX files | Heatmaps, temporal charts, weather correlation |

---

## Prerequisites

- JDK 11 or higher
- IntelliJ IDEA (Community or Ultimate)
- A GPX file exported from any fitness or health app (Samsung Health, Strava, Garmin, etc.)

Clone the repo and open it in IntelliJ. Gradle will download all dependencies automatically.

```bash
git clone https://github.com/zaleslaw/Geo-Analytics-KotlinConf-2026
cd Geo-Analytics-KotlinConf-2026
./gradlew build
```

---

## Key concepts

**GPX** — a standard XML format for GPS tracks. It stores a sequence of points, each with latitude, longitude, timestamp, and optionally altitude.

**DataFrame** — a table where each row is one GPS point and each column is an attribute (lat, lon, time, distance, speed, etc.).

**GeoDataFrame** — a DataFrame extended with geometry objects (points, polygons). Used for map rendering.

**CRS (Coordinate Reference System)** — defines how coordinates map to the Earth's surface. This project uses WGS84 (EPSG:4326), the same system used by GPS devices and Google Maps.

---

## Part 1: Single Track

Source files: `src/main/kotlin/onetrack/`

### Step 1 — Read a GPX file

`Step_1_Export_GPX_one_track.kt`

```kotlin
val gpxDf = DataFrame.readGPX("${ONE_TRACK_PATH}.gpx")
gpxDf.print()
```

`readGPX` parses the XML and returns a DataFrame with columns: `lat`, `lon`, `time`, `altitude`. Each row is one GPS measurement.

Expected output — a table with ~11 000 rows for a 30-minute walk.

### Step 2 — Export to JSON and reload

`Step_2_Export_GPX_import_JSON.kt`

```kotlin
gpxDf.writeJson("${ONE_TRACK_PATH}.json")
val fromJson = DataFrame.readJson("${ONE_TRACK_PATH}.json")
```

This step shows that a GPX track is just structured data. Once it is a DataFrame, it can be stored and loaded in any format Kotlin DataFrame supports.

### Step 3 — Visualize the track

`Step_3_Vizualize_one_track.kt`

```kotlin
val geoDataFrame = gpxDf.toGeoDataFrame()

geoDataFrame.plot {
    geoPoints {
        size = 2.0
        color = Color.ORANGE
    }
}.save("${ONE_TRACK_FILENAME}.png")
```

`toGeoDataFrame()` converts lat/lon columns into JTS Point geometry objects. The Kandy `plot` DSL renders them on a geographic canvas.

![Single track](lets-plot-images/20250112.png)

### Step 4 — Overlay on Berlin district boundaries

`Step_4_Vizualize_one_track_with_Berlin_area.kt`

```kotlin
val berlinBezirke = GeoDataFrame.readShapefile(BERLIN_SHAPEFILE_PATH)
val wgs84Crs = CRS.decode("EPSG:4326", true)
val berlinWgs84 = berlinBezirke.applyCrs(wgs84Crs)

berlinWgs84.plot {
    geoPolygon()
    withData(geoDataFrame) {
        geoPoints { size = 1.5 }
    }
}.save("berlinBezirke_${ONE_TRACK_FILENAME}.png")
```

The shapefile stores district boundaries in Web Mercator projection (EPSG:3857). The `applyCrs` call reprojects them to WGS84 so both layers share the same coordinate system.

| Berlin districts | Track on map |
|:---:|:---:|
| ![Berlin Bezirke](lets-plot-images/berlinBezirke.png) | ![Track on Berlin map](lets-plot-images/berlinBezirke_20250112.png) |

### Step 5 — Compute distances and statistics

`Step_5_Enrich_one_track.kt`

```kotlin
val enrichedDf = gpxDf.addDistanceColumns()
enrichedDf.printDistanceStatistics()
```

`addDistanceColumns()` calculates the distance between each consecutive pair of GPS points using the **Haversine formula** — the standard way to compute distances on a sphere given two lat/lon pairs.

Sample output:
```
Total distance : 4.23 km
Avg point gap  : 0.42 m
Max point gap  : 8.31 m
```

---

## Part 2: Multiple Tracks

Source files: `src/main/kotlin/multipletracks/`

This section loads all GPX files at once and builds aggregate visualizations.

### Step 1 — Load all GPX files

`Step_1_Export_GPX_tracks.kt`

```kotlin
val tracksDf = File(TRACKS_PATH)
    .listFiles { f -> f.extension.equals("gpx", ignoreCase = true) }
    ?.map { file ->
        DataFrame.readGPX(file.absolutePath)
            .add("filename") { file.name }
            .addDistanceColumns()
    }
    ?.reduce { acc, df -> acc.concat(df) }
```

Each file is parsed independently (distance calculation requires consecutive points within one track), then all DataFrames are concatenated into a single table with a `filename` column for grouping.

### Step 2 — Visualize all tracks on a map

`Step_2_Vizualize_tracks.kt`

All tracks plotted together show the geographic coverage of the dataset.

| All tracks | Filtered to Berlin | With district layer | Zoom: Pankow & Mitte |
|:---:|:---:|:---:|:---:|
| ![All tracks](lets-plot-images/multipleTracks/all_gpx_tracks.png) | ![Berlin tracks](lets-plot-images/multipleTracks/berlin_gpx_tracks.png) | ![Tracks with districts](lets-plot-images/multipleTracks/berlin_gpx_tracks_bezirk_layer.png) | ![Pankow Mitte zoom](lets-plot-images/multipleTracks/berlin_gpx_tracks_bezirk_layer_pankow_mitte.png) |

### Step 3 — Temporal analysis

`Step_3_Temporal_Analysis.kt`

```kotlin
val temporalDf  = tracksDf.addTemporalAttributes()   // hour, day, month, season
val trackStats  = temporalDf.addSpeedMetrics().calculateTrackStats()

trackStats.visualizeDurationByMonth("multipleTracks/duration_by_month.png")
trackStats.visualizeDurationByHour ("multipleTracks/duration_by_hour.png")
```

`addTemporalAttributes()` parses ISO 8601 timestamps and adds derived columns: `hour_of_day`, `day_of_week`, `month`, `year`, `season`.

`calculateTrackStats()` collapses the point-level table into one row per track: start time, total distance, total duration, average speed.

| Walk duration by month | Walk duration by hour of day |
|:---:|:---:|
| ![Duration by month](lets-plot-images/multipleTracks/duration_by_month.png) | ![Duration by hour](lets-plot-images/multipleTracks/duration_by_hour.png) |

### Step 4 — Clean GPS noise

`Step_4_Cleanup_data.kt`

Raw GPS data contains measurement errors — sudden position jumps that produce unrealistic speed values. Two algorithms are demonstrated:

**Algorithm A — distance jump filter**

```kotlin
val cleanedA = pointsDf.filter { "haversineDistance"<Double>() < 100.0 }
```

A GPS ping that jumps more than 100 m from the previous point is satellite noise, not walking. These rows are dropped.

**Algorithm B — time-interval aggregation**

```kotlin
val smoothedB = pointsDf.aggregatePointsByTime(intervalSeconds = 10)
```

Points within each 10-second window are averaged. The aggregated positions are smoother and the recalculated speed is more stable.

| Raw speed distribution | After Algorithm A | After Algorithm B |
|:---:|:---:|:---:|
| ![Speed raw](lets-plot-images/multipleTracks/speed_raw.png) | ![Speed cleaned](lets-plot-images/multipleTracks/speed_cleaned_A.png) | ![Speed smoothed](lets-plot-images/multipleTracks/speed_smoothed_B.png) |

The long tail on the right in the raw chart represents noise. Both algorithms remove most of it. Algorithm B also preserves more real low-speed data (standing, slow walking) because it averages rather than discards.

### Step 5 — Enrich with weather data

`Step_5_Enrich_with_Weather.kt`

```kotlin
val weatherService = WeatherService()
val enrichedDf = pointsDf.enrichWithWeather(weatherService, useCache = true)

val trackWeather = enrichedDf.aggregateWeatherByTrack()
val combined     = trackStats.innerJoin(trackWeather, "filename")
```

`WeatherService` calls the [Open-Meteo](https://open-meteo.com) historical weather API — free, no API key required. It fetches hourly temperature, precipitation, wind speed, cloud cover, and weather code for each track's location and time.

Results are cached in `weather_cache.csv` (keyed by lat, lon, date, hour), so repeated runs do not make redundant API calls.

`aggregateWeatherByTrack()` collapses point-level weather into one row per track: numeric values are averaged, weather category is determined by the dominant condition.

| Walks by weather | Walks by month and weather | Weather heatmap |
|:---:|:---:|:---:|
| ![Walks by weather](lets-plot-images/multipleTracks/walks_by_weather.png) | ![Walks by month and weather](lets-plot-images/multipleTracks/walks_by_month_weather.png) | ![Weather heatmap](lets-plot-images/multipleTracks/weather_heatmap.png) |

| Average temperature by month | Temperature vs walk duration |
|:---:|:---:|
| ![Temperature by month](lets-plot-images/multipleTracks/temperature_by_month.png) | ![Temperature vs duration](lets-plot-images/multipleTracks/temperature_vs_duration.png) |

### Step 6 — Generate a smooth heatmap

`Step_6_SmoothHeatmap.kt`

```kotlin
berlinPoints.createSmoothHeatmap(
    berlinGeoDataFrame = berlinWgs84,
    xRange     = 13.08..13.73,
    yRange     = 52.34..52.67,
    resolution = 200,
    blurMeters = 450.0,
    blurPasses = 3,
    outputPath = "$dir/heatmap_smooth_berlin_full.png",
    title      = "Walk Heatmap (smooth) — Berlin"
)
```

The heatmap algorithm:
1. Divides the bounding box into a grid (e.g. 200×200 cells).
2. Counts how many GPS points fall into each cell.
3. Applies a Gaussian blur using three passes of a box filter. The blur radius is computed from `blurMeters` divided by the physical size of one cell.
4. Normalizes density on a log scale and maps it to a blue → cyan → green → yellow → red color gradient.

The same data at three zoom levels:

| Berlin full | Mitte & Prenzlauer Berg | Mitte street level |
|:---:|:---:|:---:|
| ![Heatmap Berlin](lets-plot-images/multipleTracks/heatmap_smooth_berlin_full.png) | ![Heatmap Mitte](lets-plot-images/multipleTracks/heatmap_smooth_mitte.png) | ![Heatmap Mitte tight](lets-plot-images/multipleTracks/heatmap_smooth_mitte_tight.png) |

---

## Libraries used

| Library | Purpose |
|---------|---------|
| [Kotlin DataFrame](https://kotlin.github.io/dataframe/) | Tabular data manipulation |
| `dataframe-geo` | GeoDataFrame, shapefile reader, CRS reprojection |
| [Kandy](https://kotlin.github.io/kandy/) | Visualization DSL (grammar of graphics) |
| `kandy-geo` | Geographic plot layers (`geoPoints`, `geoPolygon`) |
| [JTS](https://locationtech.github.io/jts/) | Geometry engine (Point, Polygon, spatial operations) |
| [GeoTools](https://geotools.org) | Coordinate reference system decoding and projection |
| [Ktor](https://ktor.io) | Async HTTP client for weather API calls |
| [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) | Date/time parsing and arithmetic |
| [Open-Meteo](https://open-meteo.com) | Free historical weather API, no key required |

---

## Project structure

```
src/main/kotlin/
├── onetrack/          # Part 1: single track steps (Step_1 … Step_5)
├── multipletracks/    # Part 2: multiple tracks steps (Step_1 … Step_6)
├── analysis/          # Distance, speed, temporal, and cleanup functions
├── weather/           # WeatherService, cache, enrichment
├── visualization/     # Kandy chart builders
├── geo/               # GeoDataFrame conversion utilities
├── io/                # GPX parser
└── model/             # TrackPoint data class

src/main/resources/
├── tracks/            # 30+ GPX files (2023–2025, Berlin)
└── Berlin_Bezirke.*   # Berlin district boundary shapefile (ESRI format)
```
