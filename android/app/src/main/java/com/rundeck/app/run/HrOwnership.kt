package com.rundeck.app.run

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HrOwnershipMode(val label: String, val detail: String) {
    DirectDeviceHr("DIRECT DEVICE HR", "Reserved for the Garmin strap concurrency gate."),
    PhoneForwardedHr("PHONE-FORWARDED HR", "Use HR received by Android and forward it to RunDeck."),
    PhoneOnly("PHONE ONLY", "Keep heart rate off the RunDeck display."),
}

object HrOwnershipPreferences {
    private const val PREFS = "rundeck_hr_prefs"
    private const val KEY_MODE = "ownership_mode"
    private val _mode = MutableStateFlow(HrOwnershipMode.PhoneForwardedHr)
    val mode: StateFlow<HrOwnershipMode> = _mode.asStateFlow()

    fun initialize(context: Context) {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        _mode.value = stored?.let { value -> HrOwnershipMode.entries.firstOrNull { it.name == value } }
            ?: HrOwnershipMode.PhoneForwardedHr
    }

    fun set(context: Context, mode: HrOwnershipMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }
}
