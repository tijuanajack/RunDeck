package com.rundeck.app.run

/** Android-owned run target. The display receives this immutable selection. */
data class RunPreset(
    val id: String,
    val name: String,
    val detail: String,
    val targetLabel: String,
    val lowerSecondsPerMile: Int,
    val upperSecondsPerMile: Int,
    val hrLowBpm: Int,
    val hrHighBpm: Int,
) {
    fun paceStatus(paceSecondsPerMile: Double?): PaceTargetStatus = when {
        paceSecondsPerMile == null -> PaceTargetStatus.GpsWeak
        paceSecondsPerMile < lowerSecondsPerMile -> PaceTargetStatus.EaseOff
        paceSecondsPerMile > upperSecondsPerMile -> PaceTargetStatus.PickItUp
        else -> PaceTargetStatus.OnTarget
    }

    fun heartRateStatus(heartRateBpm: Int?): HeartRateTargetStatus = when {
        heartRateBpm == null || heartRateBpm <= 0 -> HeartRateTargetStatus.Unavailable
        heartRateBpm > hrHighBpm -> HeartRateTargetStatus.High
        heartRateBpm < hrLowBpm -> HeartRateTargetStatus.Low
        else -> HeartRateTargetStatus.InZone
    }

    fun combinedStatus(paceSecondsPerMile: Double?, heartRateBpm: Int?): CombinedTargetStatus {
        val pace = paceStatus(paceSecondsPerMile)
        return when (heartRateStatus(heartRateBpm)) {
            HeartRateTargetStatus.High -> if (pace == PaceTargetStatus.EaseOff) CombinedTargetStatus.EaseOff else CombinedTargetStatus.BackOff
            HeartRateTargetStatus.Unavailable -> if (pace == PaceTargetStatus.GpsWeak) CombinedTargetStatus.GpsWeak else CombinedTargetStatus.Pace(pace)
            else -> CombinedTargetStatus.Pace(pace)
        }
    }
}

object RunPresetCatalog {
    val easy = RunPreset("easy", "EASY", "HR 135–145", "10:00-11:00 / HR 135-145", 10 * 60, 11 * 60, 135, 145)
    val steady = RunPreset("steady", "STEADY", "PACE 8:45–9:00", "8:45-9:00 / HR 135-150", 8 * 60 + 45, 9 * 60, 135, 150)
    val longRun = RunPreset("long_run", "LONG RUN", "10.0 MI   |   HR < 150   |   PACE 8:50–9:20", "8:50-9:20 / HR < 150", 8 * 60 + 50, 9 * 60 + 20, 135, 150)
    val all = listOf(easy, steady, longRun)

    fun byId(id: String): RunPreset = all.firstOrNull { it.id == id } ?: longRun
}
