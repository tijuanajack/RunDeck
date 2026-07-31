package com.rundeck.app.run

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small local persistence layer for the selected reusable V1 preset. */
object RunPresetPreferences {
    private const val PREFS = "rundeck_run_preset"
    private const val KEY_ID = "selected_id"
    private val selectedMutable = MutableStateFlow(RunPresetCatalog.longRun)
    val selected: StateFlow<RunPreset> = selectedMutable.asStateFlow()

    fun initialize(context: Context) {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ID, null)
        selectedMutable.value = RunPresetCatalog.byId(id ?: RunPresetCatalog.longRun.id)
    }

    fun set(context: Context, preset: RunPreset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ID, preset.id).apply()
        selectedMutable.value = preset
    }
}
