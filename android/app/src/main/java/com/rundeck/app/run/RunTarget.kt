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
}

enum class PaceTargetStatus(val label: String, val packetFlag: Int) {
    GpsWeak("GPS WEAK", 0x0020),
    OnTarget("ON TARGET", 0x0004),
    EaseOff("EASE OFF", 0x0008),
    PickItUp("PICK IT UP", 0x0010),
}
