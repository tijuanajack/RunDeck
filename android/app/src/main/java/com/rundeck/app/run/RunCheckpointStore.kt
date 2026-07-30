package com.rundeck.app.run

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
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
    }
}
