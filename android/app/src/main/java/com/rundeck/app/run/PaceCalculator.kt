package com.rundeck.app.run

data class LocationSample(val elapsedMs: Long, val distanceMeters: Double, val accuracyMeters: Float)
data class PaceResult(val secondsPerMile: Double?, val gpsWeak: Boolean)

/** Pure domain logic; location collection and persistence remain Android adapters. */
class PaceCalculator(private val windowMs: Long = 10_000) {
    fun currentPace(samples: List<LocationSample>): PaceResult {
        val ordered = samples.sortedBy { it.elapsedMs }.filter { it.accuracyMeters <= 25f }
        val end = ordered.lastOrNull() ?: return PaceResult(null, true)
        val start = ordered.firstOrNull { end.elapsedMs - it.elapsedMs <= windowMs } ?: return PaceResult(null, true)
        val elapsed = end.elapsedMs - start.elapsedMs
        val distance = end.distanceMeters - start.distanceMeters
        if (elapsed < 3_000 || distance <= 2.0) return PaceResult(null, true)
        return PaceResult((elapsed / 1000.0) / (distance / 1609.344), false)
    }
}
