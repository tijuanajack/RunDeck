package com.rundeck.app.device

import android.app.Application
import android.content.Intent
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
import com.rundeck.app.weather.OpenMeteoWeather
import com.rundeck.app.weather.WeatherSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

    val devices = bleClient.devices
    val connection = bleClient.connection
    val bridge = bleClient.bridge
    val media = mediaController.state
    val notifications = RunDeckNotificationPreferences.settings
    val hrOwnership = HrOwnershipPreferences.mode

    init {
        RunDeckNotificationPreferences.initialize(application)
        HrOwnershipPreferences.initialize(application)
        mediaController.start()
        viewModelScope.launch {
            RunSession.state.collectLatest(bleClient::publishRunState)
        }
        viewModelScope.launch {
            hrOwnership.collectLatest(bleClient::setHrOwnershipMode)
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
                    DeviceRunControl.Pause -> RunTrackingService.ACTION_PAUSE
                    DeviceRunControl.Resume -> RunTrackingService.ACTION_RESUME
                    DeviceRunControl.Stop -> RunTrackingService.ACTION_STOP
                }
                getApplication<Application>().startService(
                    Intent(getApplication(), RunTrackingService::class.java).setAction(action),
                )
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
                val latitude = run.latitude
                val longitude = run.longitude
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

    override fun onCleared() {
        mediaController.close()
        bleClient.close()
    }
}
