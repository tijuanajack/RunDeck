package com.rundeck.app.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

enum class WeatherState(val wireValue: Int) {
    Connected(0),
    Stale(1),
    Unavailable(2),
    Error(3),
}

data class WeatherSnapshot(
    val state: WeatherState = WeatherState.Unavailable,
    val temperatureF: Int? = null,
    val updatedAtMs: Long = 0L,
)

/** No-key V1 provider. The caller controls the ten-minute refresh interval. */
class OpenMeteoWeather {
    private var cached = WeatherSnapshot()

    suspend fun fetch(latitude: Double, longitude: Double, nowMs: Long): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m&temperature_unit=fahrenheit",
                    latitude,
                    longitude,
                ))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    requestMethod = "GET"
                }
                connection.inputStream.bufferedReader().use { reader ->
                    val body = reader.readText()
                    val match = Pattern.compile("\\\"temperature_2m\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(body)
                    require(match.find()) { "temperature missing" }
                    WeatherSnapshot(
                        state = WeatherState.Connected,
                        temperatureF = requireNotNull(match.group(1)).toDouble().roundToInt(),
                        updatedAtMs = nowMs,
                    )
                }.also { connection.disconnect() }
            }.getOrElse {
                cached.copy(state = if (cached.temperatureF != null) WeatherState.Stale else WeatherState.Unavailable)
            }.also { cached = it }
        }

    fun snapshot(nowMs: Long): WeatherSnapshot {
        if (cached.temperatureF == null) return cached
        return if (nowMs - cached.updatedAtMs > 10 * 60 * 1_000L) {
            cached.copy(state = WeatherState.Stale)
        } else cached
    }
}
