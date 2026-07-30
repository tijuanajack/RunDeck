package com.rundeck.app.run

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RunUiState(
    val active: Boolean = false,
    val elapsedSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val paceSecondsPerMile: Double? = null,
    val gpsStatus: String = "GPS READY",
    val heartRateBpm: Int? = null,
    val heartRateStatus: String = "GARMIN STRAP OFF",
)

/** Process-local live state. DataStore checkpoints are the next persistence increment. */
object RunSession {
    private val mutableState = MutableStateFlow(RunUiState())
    val state = mutableState.asStateFlow()
    internal fun update(value: RunUiState) { mutableState.value = value }
}

class RunTrackingService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private var startedAtMs = 0L
    private var distanceMeters = 0.0
    private var previousLocation: Location? = null
    private val samples = ArrayDeque<LocationSample>()
    private val paceCalculator = PaceCalculator()
    private val checkpointScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var checkpoints: RunCheckpointStore

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkpoints = RunCheckpointStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRun()
            else -> if (startedAtMs == 0L) startRun()
        }
        return START_NOT_STICKY
    }

    private fun startRun() {
        startedAtMs = SystemClock.elapsedRealtime()
        startForeground(NOTIFICATION_ID, notification("Acquiring GPS…"))
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            RunSession.update(RunUiState(gpsStatus = "LOCATION PERMISSION NEEDED"))
            return
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 1f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 2f, this)
            publish(RunUiState(active = true, gpsStatus = "ACQUIRING GPS"))
        } catch (_: SecurityException) {
            RunSession.update(RunUiState(gpsStatus = "GPS UNAVAILABLE"))
        }
    }

    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val prior = previousLocation
        if (location.accuracy <= 25f && prior != null) {
            val segment = prior.distanceTo(location).toDouble()
            val elapsed = now - prior.elapsedRealtimeNanos / 1_000_000L
            // Reject GPS teleports faster than 7.5 m/s; ordinary running remains well below this.
            if (elapsed > 0 && segment / (elapsed / 1000.0) < 7.5) distanceMeters += segment
        }
        if (location.accuracy <= 25f) previousLocation = location

        samples.addLast(LocationSample(now, distanceMeters, location.accuracy))
        while (samples.size > 30 || samples.first().elapsedMs < now - 20_000L) samples.removeFirst()
        val pace = paceCalculator.currentPace(samples.toList()).secondsPerMile
        val elapsedSeconds = ((now - startedAtMs) / 1_000L).coerceAtLeast(0)
        val next = RunUiState(true, elapsedSeconds, distanceMeters, pace, "GPS LIVE")
        publish(next)
        updateNotification(next)
    }

    override fun onProviderDisabled(provider: String) {
        publish(RunSession.state.value.copy(gpsStatus = "GPS OFF"))
    }

    private fun stopRun() {
        locationManager.removeUpdates(this)
        RunSession.update(RunUiState())
        checkpointScope.launch { checkpoints.clear() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publish(state: RunUiState) {
        RunSession.update(state)
        checkpointScope.launch { checkpoints.save(state) }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Active Run", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(detail: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("RunDeck run in progress")
        .setContentText(detail)
        .setOngoing(true)
        .build()

    private fun updateNotification(state: RunUiState) {
        val miles = state.distanceMeters / 1609.344
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(String.format("%.2f mi  •  %s", miles, formatPace(state.paceSecondsPerMile))),
        )
    }

    companion object {
        const val ACTION_START = "com.rundeck.app.action.START_RUN"
        const val ACTION_STOP = "com.rundeck.app.action.STOP_RUN"
        private const val CHANNEL_ID = "active_run"
        private const val NOTIFICATION_ID = 41

        fun formatPace(seconds: Double?): String = seconds?.let {
            val total = it.roundToInt()
            "%d:%02d /mi".format(total / 60, total % 60)
        } ?: "--:-- /mi"
    }
}
