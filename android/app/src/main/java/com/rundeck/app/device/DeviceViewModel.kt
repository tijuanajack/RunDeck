package com.rundeck.app.device

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rundeck.app.ble.DeviceMediaControl
import com.rundeck.app.ble.DeviceRunControl
import com.rundeck.app.ble.DisplayContextPacket
import com.rundeck.app.ble.DiscoveredRunDeck
import com.rundeck.app.ble.RunDeckBleClient
import com.rundeck.app.media.PhoneMediaController
import com.rundeck.app.notifications.RunDeckNotificationBridge
import com.rundeck.app.notifications.RunDeckNotificationListener
import com.rundeck.app.notifications.RunDeckNotificationPreferences
import com.rundeck.app.run.RunCheckpointStore
import com.rundeck.app.run.RunSession
import com.rundeck.app.run.RunTrackingService
import com.rundeck.app.run.HrOwnershipMode
import com.rundeck.app.run.HrOwnershipPreferences
import com.rundeck.app.run.RunPreset
import com.rundeck.app.run.RunPresetPreferences
import com.rundeck.app.weather.OpenMeteoWeather
import com.rundeck.app.weather.WeatherSnapshot
import com.rundeck.app.hr.HeartRateClient
import com.rundeck.app.hr.HeartRateDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Device-facing coordinator. UI observes these flows and sends user actions
 * here; BLE, media, notifications, and environmental context stay outside the
 * Compose screen layer so they can move into feature modules independently.
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val bleClient = RunDeckBleClient(application)
    private val checkpoints = RunCheckpointStore(application)
    private val mediaController = PhoneMediaController(application)
    private val weatherProvider = OpenMeteoWeather()
    private val heartRateClient = HeartRateClient(application)
    private var weatherCoordinates: Pair<Double, Double>? = null
    private val displayBrightnessPrefs = application.getSharedPreferences("rundeck_display", 0)
    private val _displayBrightness = MutableStateFlow(displayBrightnessPrefs.getInt("brightness", 208).coerceIn(16, 255))
    val displayBrightness: StateFlow<Int> = _displayBrightness
    private val _weatherLocationStatus = MutableStateFlow("NOT SET")
    val weatherLocationStatus: StateFlow<String> = _weatherLocationStatus

    val devices = bleClient.devices
    val connection = bleClient.connection
    val bridge = bleClient.bridge
    val media = mediaController.state
    val notifications = RunDeckNotificationPreferences.settings
    val hrOwnership = HrOwnershipPreferences.mode
    val selectedPreset = RunPresetPreferences.selected
    val heartRateDevices = heartRateClient.devices
    val heartRate = heartRateClient.state

    init {
        RunDeckNotificationPreferences.initialize(application)
        HrOwnershipPreferences.initialize(application)
        RunPresetPreferences.initialize(application)
        bleClient.setDisplayBrightness(_displayBrightness.value)
        mediaController.start()
        viewModelScope.launch {
            RunSession.state.collectLatest(bleClient::publishRunState)
        }
        viewModelScope.launch {
            hrOwnership.collectLatest(bleClient::setHrOwnershipMode)
        }
        viewModelScope.launch {
            selectedPreset.collectLatest(bleClient::setPreset)
        }
        viewModelScope.launch {
            heartRate.collectLatest { source ->
                val current = RunSession.state.value
                if (!current.active || hrOwnership.value != HrOwnershipMode.PhoneForwardedHr) return@collectLatest
                RunSession.update(current.copy(heartRateBpm = source.bpm, heartRateStatus = source.status))
            }
        }
        viewModelScope.launch {
            media.collectLatest(bleClient::publishMediaState)
        }
        viewModelScope.launch {
            bleClient.deviceMediaControls.collect { control ->
                when (control) {
                    DeviceMediaControl.Previous -> mediaController.previous()
                    DeviceMediaControl.PlayPause -> mediaController.playPause()
                    DeviceMediaControl.Next -> mediaController.next()
                }
            }
        }
        viewModelScope.launch {
            bleClient.deviceRunControls.collect { control ->
                val action = when (control) {
                    DeviceRunControl.Start -> RunTrackingService.ACTION_START
                    DeviceRunControl.Pause -> RunTrackingService.ACTION_PAUSE
                    DeviceRunControl.Resume -> RunTrackingService.ACTION_RESUME
                    DeviceRunControl.Stop -> RunTrackingService.ACTION_STOP
                }
                val intent = Intent(getApplication(), RunTrackingService::class.java).setAction(action)
                if (action == RunTrackingService.ACTION_START) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
        }
        viewModelScope.launch {
            RunDeckNotificationBridge.events.collect { payload ->
                bleClient.publishNotification(payload)
                delay(4_000L)
            }
        }
        viewModelScope.launch {
            bleClient.dismissedNotifications.collect { key ->
                RunDeckNotificationListener.dismiss(key)
            }
        }
        viewModelScope.launch {
            var lastWeatherFetchMs = 0L
            var weather = WeatherSnapshot()
            while (isActive) {
                val run = RunSession.state.value
                val nowMs = System.currentTimeMillis()
                val coordinates = if (run.latitude != null && run.longitude != null) {
                    run.latitude!! to run.longitude!!
                } else weatherCoordinates
                val latitude = coordinates?.first
                val longitude = coordinates?.second
                if (latitude != null && longitude != null && nowMs - lastWeatherFetchMs >= 10 * 60 * 1_000L) {
                    weather = weatherProvider.fetch(latitude, longitude, nowMs)
                    lastWeatherFetchMs = nowMs
                } else {
                    weather = weatherProvider.snapshot(nowMs)
                }
                val clock = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                bleClient.publishDisplayContext(
                    DisplayContextPacket(
                        clock = clock,
                        weatherState = weather.state.wireValue,
                        temperatureAvailable = weather.temperatureF != null,
                        temperatureF = weather.temperatureF ?: 0,
                    ),
                )
                delay(30_000L)
            }
        }
    }

    fun scan() = bleClient.startScan()
    fun connect(device: DiscoveredRunDeck) = bleClient.connect(device)
    fun sendDemoMetrics() = bleClient.sendDemoMetrics()
    fun discardCheckpoint() = viewModelScope.launch { checkpoints.clear() }
    fun refreshMedia() = mediaController.refresh()
    fun previousTrack() = mediaController.previous()
    fun playPause() = mediaController.playPause()
    fun nextTrack() = mediaController.next()
    fun setNotificationForwarding(enabled: Boolean) =
        RunDeckNotificationPreferences.setForwardingEnabled(getApplication(), enabled)
    fun setNotificationAllowAll(allowAll: Boolean) =
        RunDeckNotificationPreferences.setAllowAllMessageApps(getApplication(), allowAll)
    fun setNotificationContactsAll(allowAll: Boolean) =
        RunDeckNotificationPreferences.setAllowAllContacts(getApplication(), allowAll)
    fun setNotificationSourceAllowed(packageName: String, allowed: Boolean) =
        RunDeckNotificationPreferences.setSourceAllowed(getApplication(), packageName, allowed)
    fun setNotificationContactAllowed(packageName: String, sender: String, allowed: Boolean) =
        RunDeckNotificationPreferences.setContactAllowed(getApplication(), packageName, sender, allowed)
    fun setHrOwnershipMode(mode: HrOwnershipMode) = HrOwnershipPreferences.set(getApplication(), mode)
    fun setPreset(preset: RunPreset) = RunPresetPreferences.set(getApplication(), preset)
    fun setDisplayBrightness(level: Int) {
        val safe = level.coerceIn(16, 255)
        displayBrightnessPrefs.edit().putInt("brightness", safe).apply()
        _displayBrightness.value = safe
        bleClient.setDisplayBrightness(safe)
    }
    @Suppress("MissingPermission")
    fun seedWeatherFromLastKnownLocation() {
        val context = getApplication<Application>()
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _weatherLocationStatus.value = "LOCATION PERMISSION NEEDED"
            return
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: run {
            _weatherLocationStatus.value = "LOCATION UNAVAILABLE"
            return
        }
        val location = manager.allProviders.asSequence()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time } ?: run {
                _weatherLocationStatus.value = "NO RECENT FIX"
                return
            }
        weatherCoordinates = location.latitude to location.longitude
        _weatherLocationStatus.value = "LOCATION READY"
    }
    fun scanHeartRate() = heartRateClient.scan()
    fun connectHeartRate(device: HeartRateDevice) = heartRateClient.connect(device)

    override fun onCleared() {
        mediaController.close()
        bleClient.close()
        heartRateClient.close()
    }
}
