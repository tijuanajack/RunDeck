package com.rundeck.app.run

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.runDataStore by preferencesDataStore(name = "active_run")

/** Small, local recovery checkpoint. No route history or cloud data is stored here. */
class RunCheckpointStore(private val context: Context) {
    suspend fun save(state: RunUiState) {
        context.runDataStore.edit { values ->
            values[ACTIVE] = state.active
            values[PAUSED] = state.paused
            values[ELAPSED] = state.elapsedSeconds
            values[MOVING] = state.movingSeconds
            values[DISTANCE] = state.distanceMeters
            state.paceSecondsPerMile?.let { values[PACE] = it } ?: values.remove(PACE)
            state.heartRateBpm?.let { values[HEART_RATE] = it } ?: values.remove(HEART_RATE)
            values[HEART_RATE_STATUS] = state.heartRateStatus
            values[GPS_STATUS] = state.gpsStatus
        }
    }

    suspend fun load(): RunUiState? {
        val values = context.runDataStore.data.first()
        if (values[ACTIVE] != true) return null
        return RunUiState(
            active = true,
            paused = values[PAUSED] ?: true,
            elapsedSeconds = values[ELAPSED] ?: 0,
            movingSeconds = values[MOVING] ?: values[ELAPSED] ?: 0,
            distanceMeters = values[DISTANCE] ?: 0.0,
            paceSecondsPerMile = values[PACE],
            gpsStatus = values[GPS_STATUS] ?: "CHECKPOINT",
            heartRateBpm = values[HEART_RATE],
            heartRateStatus = values[HEART_RATE_STATUS] ?: "GARMIN STRAP OFF",
        )
    }

    suspend fun clear() {
        context.runDataStore.edit { it.clear() }
    }

    private companion object {
        val ACTIVE = booleanPreferencesKey("active_bool")
        val PAUSED = booleanPreferencesKey("paused")
        val ELAPSED = longPreferencesKey("elapsed_seconds")
        val MOVING = longPreferencesKey("moving_seconds")
        val DISTANCE = doublePreferencesKey("distance_meters")
        val PACE = doublePreferencesKey("pace_seconds_per_mile")
        val HEART_RATE = intPreferencesKey("heart_rate_bpm")
        val HEART_RATE_STATUS = stringPreferencesKey("heart_rate_status")
        val GPS_STATUS = stringPreferencesKey("gps_status")
    }
}
