package com.rundeck.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.rundeck.app.run.RunUiState
import com.rundeck.app.run.LongRunTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import java.util.UUID

data class DiscoveredRunDeck(val name: String, val address: String, val rssi: Int, internal val device: BluetoothDevice)

data class LiveBridgeStatus(
    val connected: Boolean = false,
    val streamingRun: Boolean = false,
    val lastSequence: Int? = null,
    val lastAttemptMs: Long = 0,
    val lastWriteConfirmedMs: Long = 0,
    val lastRunStateAckSequence: Int? = null,
    val lastRunStateAckMs: Long = 0,
    val lastError: String? = null,
) {
    fun label(nowMs: Long = SystemClock.elapsedRealtime()): String = when {
        lastError != null -> lastError
        !connected -> "DISPLAY OFFLINE"
        lastRunStateAckMs > 0 && nowMs - lastRunStateAckMs <= 5_000 -> "PRESET ACCEPTED"
        streamingRun && lastWriteConfirmedMs > 0 && nowMs - lastWriteConfirmedMs <= 3_000 -> "PHONE LIVE → RUNDECK"
        streamingRun && lastAttemptMs > 0 -> "SENDING TO RUNDECK…"
        else -> "DISPLAY CONNECTED"
    }
}

sealed interface DeviceConnection {
    data object Idle : DeviceConnection
    data object Scanning : DeviceConnection
    data class Connecting(val name: String) : DeviceConnection
    data class Ready(val name: String) : DeviceConnection
    data class Error(val message: String) : DeviceConnection
}

class RunDeckBleClient(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val scanner: BluetoothLeScanner? get() = bluetoothManager?.adapter?.bluetoothLeScanner
    private val sequence = AtomicInteger(0)
    private var gatt: BluetoothGatt? = null
    private var liveMetrics: BluetoothGattCharacteristic? = null
    private var runState: BluetoothGattCharacteristic? = null
    private var deviceEvents: BluetoothGattCharacteristic? = null
    private val demoHandler = Handler(Looper.getMainLooper())
    private var streamingDemoMetrics = false
    private var protocolStarted = false
    private var currentRunState: RunUiState? = null
    private var lastPublishedRunActive: Boolean? = null
    private val preferences = appContext.getSharedPreferences("rundeck_ble", Context.MODE_PRIVATE)
    private var rememberedAddress: String? = preferences.getString(KEY_DEVICE_ADDRESS, null)
    private var reconnectAttempts = 0
    private var reconnectEnabled = true
    private val seen = linkedMapOf<String, DiscoveredRunDeck>()
    private val _devices = MutableStateFlow<List<DiscoveredRunDeck>>(emptyList())
    val devices: StateFlow<List<DiscoveredRunDeck>> = _devices.asStateFlow()
    private val _connection = MutableStateFlow<DeviceConnection>(DeviceConnection.Idle)
    val connection: StateFlow<DeviceConnection> = _connection.asStateFlow()
    private val _bridge = MutableStateFlow(LiveBridgeStatus())
    val bridge: StateFlow<LiveBridgeStatus> = _bridge.asStateFlow()

    private val reconnectRunnable = Runnable { reconnectRememberedDevice() }

    init {
        if (rememberedAddress != null) scheduleReconnect(0L)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (!name.equals("RunDeck", ignoreCase = true)) return
            seen[device.address] = DiscoveredRunDeck(name, device.address, result.rssi, device)
            _devices.value = seen.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _connection.value = DeviceConnection.Error("Bluetooth scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== this@RunDeckBleClient.gatt) {
                gatt.close()
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onDisconnected(gatt, "Connection failed ($status)")
            } else if (newState == BluetoothGatt.STATE_CONNECTED) {
                reconnectAttempts = 0
                gatt.discoverServices()
            } else {
                onDisconnected(gatt, "RunDeck disconnected")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== this@RunDeckBleClient.gatt) return
            val service: BluetoothGattService? = gatt.getService(RunDeckProtocol.SERVICE_UUID)
            liveMetrics = service?.getCharacteristic(RunDeckProtocol.LIVE_METRICS_UUID)
            runState = service?.getCharacteristic(RunDeckProtocol.RUN_STATE_UUID)
            deviceEvents = service?.getCharacteristic(RunDeckProtocol.DEVICE_EVENT_UUID)
            _connection.value = if (status == BluetoothGatt.GATT_SUCCESS && liveMetrics != null && runState != null && deviceEvents != null) {
                _bridge.value = _bridge.value.copy(connected = true, lastError = null)
                beginProtocolStream()
                DeviceConnection.Ready(gatt.device.name ?: "RunDeck")
            } else {
                _bridge.value = _bridge.value.copy(connected = false, streamingRun = false, lastError = "PROTOCOL NOT FOUND")
                DeviceConnection.Error("RunDeck protocol service was not found")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == RunDeckProtocol.RUN_STATE_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) readDeviceEventAck()
                else {
                    _bridge.value = _bridge.value.copy(lastError = "RUN STATE WRITE FAILED ($status)")
                    startMetricsStream()
                }
                return
            }
            if (characteristic.uuid != RunDeckProtocol.LIVE_METRICS_UUID) return
            val now = SystemClock.elapsedRealtime()
            _bridge.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                _bridge.value.copy(connected = true, lastWriteConfirmedMs = now, lastError = null)
            } else {
                _bridge.value.copy(lastError = "METRIC WRITE FAILED ($status)")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleDeviceEvent(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleDeviceEvent(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (characteristic.uuid != RunDeckProtocol.DEVICE_EVENT_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) handleDeviceEvent(characteristic.uuid, value)
            else {
                _bridge.value = _bridge.value.copy(lastError = "ACK READ FAILED ($status)")
                startMetricsStream()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid != RunDeckProtocol.DEVICE_EVENT_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) handleDeviceEvent(characteristic.uuid, characteristic.value ?: return)
            else {
                _bridge.value = _bridge.value.copy(lastError = "ACK READ FAILED ($status)")
                startMetricsStream()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bluetoothScanner = scanner ?: run {
            _connection.value = DeviceConnection.Error("Bluetooth is unavailable or switched off")
            return
        }
        // Android reports SCAN_FAILED_ALREADY_STARTED if the user taps again
        // while a prior request is still active. Replacing it is deterministic.
        bluetoothScanner.stopScan(scanCallback)
        seen.clear(); _devices.value = emptyList(); _connection.value = DeviceConnection.Scanning
        bluetoothScanner.startScan(emptyList(), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connect(discovered: DiscoveredRunDeck) {
        scanner?.stopScan(scanCallback)
        if (gatt?.device?.address == discovered.address && _connection.value !is DeviceConnection.Idle) return
        rememberedAddress = discovered.address
        preferences.edit().putString(KEY_DEVICE_ADDRESS, discovered.address).apply()
        reconnectEnabled = true
        reconnectAttempts = 0
        connectDevice(discovered.device, discovered.name)
    }

    @SuppressLint("MissingPermission")
    fun sendDemoMetrics() {
        writeMetrics(
            sequence.getAndIncrement() and 0xFFFF,
            SystemClock.elapsedRealtime() and 0xFFFF_FFFFL,
            LiveMetrics(flags = 0x000F, paceSecondsPerMile = 505, distanceCentimeters = 12_345,
                elapsedSeconds = 72, movingSeconds = 70, speedCentimetersPerSecond = 320,
                temperatureDeciF = 780, forwardedHeartRate = 143),
        )
    }

    /** Publishes GPS state as the same compact packet the display already understands. */
    fun publishRunState(state: RunUiState) {
        currentRunState = state
        _bridge.value = _bridge.value.copy(streamingRun = state.active)
        if (lastPublishedRunActive != state.active) {
            sendRunState(state)
            lastPublishedRunActive = state.active
            return
        }
        if (state.active) sendRunMetrics(state)
    }

    private fun sendRunState(state: RunUiState) {
        val characteristic = runState ?: return
        val target = gatt ?: return
        val nextSequence = sequence.getAndIncrement() and 0xFFFF
        val payload = RunDeckProtocol.encodeRunState(
            nextSequence,
            RunStatePacket(
                active = state.active,
                presetName = "LONG RUN",
                targetLabel = LongRunTarget.label,
                paceLowSecondsPerMile = LongRunTarget.lowerSecondsPerMile,
                paceHighSecondsPerMile = LongRunTarget.upperSecondsPerMile,
                hrLowBpm = 135,
                hrHighBpm = 150,
            ),
        )
        _bridge.value = _bridge.value.copy(lastRunStateAckSequence = null, lastRunStateAckMs = 0, lastError = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = target.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (status != 0) _bridge.value = _bridge.value.copy(lastError = "RUN STATE QUEUE FAILED ($status)")
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            @Suppress("DEPRECATION")
            if (!target.writeCharacteristic(characteristic)) {
                _bridge.value = _bridge.value.copy(lastError = "RUN STATE QUEUE FAILED")
            }
        }
    }

    private fun sendRunMetrics(state: RunUiState) {
        val pace = state.paceSecondsPerMile?.roundToInt()?.coerceIn(1, 65_535) ?: 0
        writeMetrics(
            sequence.getAndIncrement() and 0xFFFF,
            SystemClock.elapsedRealtime() and 0xFFFF_FFFFL,
            LiveMetrics(
                flags = 0x0003 or LongRunTarget.status(state.paceSecondsPerMile).packetFlag,
                paceSecondsPerMile = pace,
                distanceCentimeters = (state.distanceMeters * 100).roundToInt().toLong(),
                elapsedSeconds = state.elapsedSeconds,
                movingSeconds = state.elapsedSeconds,
                speedCentimetersPerSecond = 0,
                temperatureDeciF = 0,
                forwardedHeartRate = state.heartRateBpm ?: 0,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun writeMetrics(sequence: Int, sourceMs: Long, metrics: LiveMetrics) {
        val characteristic = liveMetrics ?: run {
            _bridge.value = _bridge.value.copy(connected = false, lastError = "DISPLAY OFFLINE")
            return
        }
        val payload = RunDeckProtocol.encodeLiveMetrics(sequence, sourceMs, metrics)
        val target = gatt ?: run {
            _bridge.value = _bridge.value.copy(connected = false, lastError = "DISPLAY OFFLINE")
            return
        }
        _bridge.value = _bridge.value.copy(
            connected = true,
            streamingRun = currentRunState?.active == true,
            lastSequence = sequence,
            lastAttemptMs = SystemClock.elapsedRealtime(),
            lastError = null,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = target.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (status != 0) _bridge.value = _bridge.value.copy(lastError = "METRIC QUEUE FAILED ($status)")
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            @Suppress("DEPRECATION")
            if (!target.writeCharacteristic(characteristic)) {
                _bridge.value = _bridge.value.copy(lastError = "METRIC QUEUE FAILED")
            }
        }
    }

    fun startMetricsStream() {
        if (streamingDemoMetrics) return
        streamingDemoMetrics = true
        val runnable = object : Runnable {
            override fun run() {
                if (!streamingDemoMetrics || liveMetrics == null) return
                // Demo frames are intentionally never automatic. A disconnected
                // or inactive phone must become visibly unavailable on RunDeck.
                currentRunState?.takeIf { it.active }?.let(::sendRunMetrics)
                demoHandler.postDelayed(this, 1_000)
            }
        }
        demoHandler.postDelayed(runnable, 250)
    }

    private fun beginProtocolStream() {
        if (protocolStarted) return
        protocolStarted = true
        sendRunState(currentRunState ?: RunUiState())
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceEventAck() {
        val target = gatt ?: return
        val events = deviceEvents ?: return
        if (!target.readCharacteristic(events)) {
            _bridge.value = _bridge.value.copy(lastError = "ACK READ QUEUE FAILED")
            startMetricsStream()
        }
    }

    private fun handleDeviceEvent(uuid: UUID, payload: ByteArray) {
        if (uuid != RunDeckProtocol.DEVICE_EVENT_UUID) return
        runCatching { RunDeckProtocol.decodeDeviceEvent(payload) }
            .onSuccess { event ->
                if (event is DeviceEvent.Ack && event.commandType == RunDeckProtocol.COMMAND_RUN_STATE) {
                    val now = SystemClock.elapsedRealtime()
                    _bridge.value = if (event.status == RunDeckProtocol.ACK_OK) {
                        _bridge.value.copy(
                            connected = true,
                            lastRunStateAckSequence = event.acknowledgedSequence,
                            lastRunStateAckMs = now,
                            lastError = null,
                        )
                    } else {
                        _bridge.value.copy(lastError = "RUN STATE REJECTED (${event.status.toInt() and 0xFF})")
                    }
                    startMetricsStream()
                }
            }
            .onFailure {
                _bridge.value = _bridge.value.copy(lastError = "BAD DEVICE EVENT")
                startMetricsStream()
            }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        reconnectEnabled = false
        demoHandler.removeCallbacks(reconnectRunnable)
        streamingDemoMetrics = false
        demoHandler.removeCallbacksAndMessages(null)
        scanner?.stopScan(scanCallback)
        gatt?.close(); gatt = null; liveMetrics = null
        runState = null
        deviceEvents = null
        protocolStarted = false
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice, name: String) {
        demoHandler.removeCallbacks(reconnectRunnable)
        gatt?.close()
        liveMetrics = null
        runState = null
        deviceEvents = null
        protocolStarted = false
        _connection.value = DeviceConnection.Connecting(name)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else device.connectGatt(appContext, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun reconnectRememberedDevice() {
        val address = rememberedAddress ?: return
        val adapter = bluetoothManager?.adapter ?: return
        if (!reconnectEnabled || !adapter.isEnabled || gatt != null) return
        runCatching { adapter.getRemoteDevice(address) }
            .onSuccess { connectDevice(it, "RunDeck") }
            .onFailure { _connection.value = DeviceConnection.Error("Saved RunDeck is unavailable") }
    }

    private fun scheduleReconnect(delayMs: Long) {
        demoHandler.removeCallbacks(reconnectRunnable)
        demoHandler.postDelayed(reconnectRunnable, delayMs)
    }

    @SuppressLint("MissingPermission")
    private fun onDisconnected(closedGatt: BluetoothGatt, reason: String) {
        if (closedGatt !== gatt) return
        liveMetrics = null
        runState = null
        deviceEvents = null
        protocolStarted = false
        streamingDemoMetrics = false
        demoHandler.removeCallbacksAndMessages(null)
        gatt = null
        closedGatt.close()
        _bridge.value = _bridge.value.copy(connected = false, streamingRun = false, lastError = reason.uppercase())
        if (reconnectEnabled && rememberedAddress != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            val delayMs = 1_000L shl reconnectAttempts.coerceAtMost(3)
            reconnectAttempts += 1
            _connection.value = DeviceConnection.Connecting("RunDeck (reconnecting)")
            scheduleReconnect(delayMs)
        } else {
            _connection.value = DeviceConnection.Error(reason)
        }
    }

    private companion object {
        const val KEY_DEVICE_ADDRESS = "device_address"
        const val MAX_RECONNECT_ATTEMPTS = 5
    }
}
