package com.rundeck.app.run

/** Android owns targets; the display receives the resulting status in live metrics. */
object LongRunTarget {
    const val lowerSecondsPerMile = 8 * 60 + 50
    const val upperSecondsPerMile = 9 * 60 + 20
    const val label = "8:50-9:20"

    fun status(paceSecondsPerMile: Double?): PaceTargetStatus = when {
        paceSecondsPerMile == null -> PaceTargetStatus.GpsWeak
        paceSecondsPerMile < lowerSecondsPerMile -> PaceTargetStatus.EaseOff
        paceSecondsPerMile > upperSecondsPerMile -> PaceTargetStatus.PickItUp
        else -> PaceTargetStatus.OnTarget
    }

    fun heartRateStatus(heartRateBpm: Int?): HeartRateTargetStatus = when {
        heartRateBpm == null || heartRateBpm <= 0 -> HeartRateTargetStatus.Unavailable
        heartRateBpm > 150 -> HeartRateTargetStatus.High
        heartRateBpm < 135 -> HeartRateTargetStatus.Low
        else -> HeartRateTargetStatus.InZone
    }

    fun combinedStatus(paceSecondsPerMile: Double?, heartRateBpm: Int?): CombinedTargetStatus {
        val pace = status(paceSecondsPerMile)
        return when (heartRateStatus(heartRateBpm)) {
            HeartRateTargetStatus.High -> if (pace == PaceTargetStatus.EaseOff) CombinedTargetStatus.EaseOff else CombinedTargetStatus.BackOff
            HeartRateTargetStatus.Unavailable -> if (pace == PaceTargetStatus.GpsWeak) CombinedTargetStatus.GpsWeak else CombinedTargetStatus.Pace(pace)
            else -> CombinedTargetStatus.Pace(pace)
        }
    }
}

enum class PaceTargetStatus(val label: String, val packetFlag: Int) {
    GpsWeak("GPS WEAK", 0x0020),
    OnTarget("ON TARGET", 0x0004),
    EaseOff("EASE OFF", 0x0008),
    PickItUp("PICK IT UP", 0x0010),
}

enum class HeartRateTargetStatus(val packetFlag: Int) {
    Unavailable(0),
    Low(0x0040),
    High(0x0080),
    InZone(0),
}

sealed interface CombinedTargetStatus {
    data object BackOff : CombinedTargetStatus
    data object EaseOff : CombinedTargetStatus
    data object GpsWeak : CombinedTargetStatus
    data class Pace(val status: PaceTargetStatus) : CombinedTargetStatus

    val label: String
        get() = when (this) {
            BackOff -> "BACK OFF"
            EaseOff -> "EASE OFF"
            GpsWeak -> "GPS WEAK"
            is Pace -> status.label
        }
}
