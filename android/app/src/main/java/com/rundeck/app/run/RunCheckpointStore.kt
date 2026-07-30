package com.rundeck.app.run

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.runDataStore by preferencesDataStore(name = "active_run")

/** Small, local recovery checkpoint. No route history or cloud data is stored here. */
class RunCheckpointStore(private val context: Context) {
    suspend fun save(state: RunUiState) {
        context.runDataStore.edit { values ->
            values[ACTIVE] = state.active.toString()
            values[ELAPSED] = state.elapsedSeconds
            values[DISTANCE] = state.distanceMeters
            state.paceSecondsPerMile?.let { values[PACE] = it } ?: values.remove(PACE)
            state.heartRateBpm?.let { values[HEART_RATE] = it } ?: values.remove(HEART_RATE)
            values[HEART_RATE_STATUS] = state.heartRateStatus
        }
    }

    suspend fun clear() {
        context.runDataStore.edit { it.clear() }
    }

    private companion object {
        val ACTIVE = stringPreferencesKey("active")
        val ELAPSED = longPreferencesKey("elapsed_seconds")
        val DISTANCE = doublePreferencesKey("distance_meters")
        val PACE = doublePreferencesKey("pace_seconds_per_mile")
        val HEART_RATE = intPreferencesKey("heart_rate_bpm")
        val HEART_RATE_STATUS = stringPreferencesKey("heart_rate_status")
    }
}
