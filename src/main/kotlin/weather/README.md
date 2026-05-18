# Weather Module

Enriches GPS tracks with historical hourly weather data from Open-Meteo.

## Features

- ☁️ Historical weather from Open-Meteo API (free, no registration required)
- ⏰ Hourly granularity per track
- 💾 Automatic CSV cache — no duplicate API calls
- 📊 Weather–activity correlation analysis

## Weather metrics

Per point/track:

| Column | Type | Description |
|--------|------|-------------|
| `temperature` | Double | °C |
| `precipitation` | Double | mm/h |
| `cloud_cover` | Int | % |
| `wind_speed` | Double | km/h |
| `weather_category` | String | `Sunny` / `Cloudy` / `Overcast` (derived from cloud cover) |
| `weather_description` | String | `Clear` / `Rain` / `Snow` / `Fog` / `Thunderstorm` … (WMO code) |

## Usage

```kotlin
runBlocking {
    val weatherService = WeatherService()
    try {
        val tracks = DataFrame.readMultipleGPX("tracks/")
            .addTemporalAttributes()   // adds datetime column — required
            .addDistanceColumns()

        // Enrich (reads from cache if available)
        val enriched = tracks.enrichWithWeather(weatherService)

        // Track-level aggregation
        val byTrack = enriched.aggregateWeatherByTrack()
        byTrack.printWeatherStats()

    } finally {
        weatherService.close()
    }
}
```

Disable cache:
```kotlin
tracks.enrichWithWeather(weatherService, useCache = false)
```

## Technical design

### Hourly granularity

For a track from 17:25 to 19:48, weather is fetched for 17:00, 18:00, and 19:00.
All points within an hour share the same weather reading.

### Cache key: (lat, lon, date, hour)

One API call returns all 24 hours for a day. The cache stores per-hour entries so data
from one track can be reused by another track on the same day and area.

Cache format:
```csv
lat,lon,date,hour,temperature,precipitation,weather_code,cloud_cover,wind_speed
53.540091,9.99516,2025-10-21,17,13.1,0.1,51,62,17.9
```

### Start-coordinate approximation

All points of a track use the **start coordinates** for weather lookup.
Justification: local walks stay within a few km — weather is effectively uniform.
This reduces API calls from thousands (one per point) to a handful (one per track-hour).

### Aggregation strategy

- Numeric metrics (temperature, precipitation, wind): **average** over the walk duration
- Categorical metrics (category, description): **dominant value** (most frequent hour)

## Cache management

The cache file `weather_cache.csv` is created automatically on first run.

- Loaded at startup
- Checked before every API request
- Updated and deduplicated on each run
- Old format (missing `hour` column) is discarded and rebuilt automatically

To reset: delete `weather_cache.csv`.

## API: Open-Meteo

Endpoint: `https://archive-api.open-meteo.com/v1/archive`

- Free, no API key
- Historical data from 1940
- 10,000 requests/day on the free tier
- Module uses 100 ms delay between requests

Parameters fetched:
```
hourly=temperature_2m,precipitation,weather_code,cloud_cover,wind_speed_10m
```

## Module structure

| File | Responsibility |
|------|---------------|
| `WeatherData.kt` | Data models, WMO code mapping |
| `WeatherService.kt` | Open-Meteo HTTP client |
| `WeatherCache.kt` | CSV cache read/write/deduplicate |
| `WeatherEnrichment.kt` | DataFrame enrichment and correlation analysis |

## Prerequisites

The input DataFrame must have:

| Column | Type | Provided by |
|--------|------|-------------|
| `filename` | String | `add("filename") { file.name }` |
| `datetime` | LocalDateTime | `addTemporalAttributes()` |
| `lat`, `lon` | Double | GPX parser |
