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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.rundeck.app.run.RunUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

data class DiscoveredRunDeck(val name: String, val address: String, val rssi: Int, internal val device: BluetoothDevice)

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
    private val demoHandler = Handler(Looper.getMainLooper())
    private var streamingDemoMetrics = false
    private var currentRunState: RunUiState? = null
    private val seen = linkedMapOf<String, DiscoveredRunDeck>()
    private val _devices = MutableStateFlow<List<DiscoveredRunDeck>>(emptyList())
    val devices: StateFlow<List<DiscoveredRunDeck>> = _devices.asStateFlow()
    private val _connection = MutableStateFlow<DeviceConnection>(DeviceConnection.Idle)
    val connection: StateFlow<DeviceConnection> = _connection.asStateFlow()

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
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connection.value = DeviceConnection.Error("Connection failed ($status)")
                gatt.close()
            } else if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else {
                _connection.value = DeviceConnection.Idle
                liveMetrics = null
                streamingDemoMetrics = false
                demoHandler.removeCallbacksAndMessages(null)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService? = gatt.getService(RunDeckProtocol.SERVICE_UUID)
            liveMetrics = service?.getCharacteristic(RunDeckProtocol.LIVE_METRICS_UUID)
            _connection.value = if (status == BluetoothGatt.GATT_SUCCESS && liveMetrics != null) {
                startMetricsStream()
                DeviceConnection.Ready(gatt.device.name ?: "RunDeck")
            } else {
                DeviceConnection.Error("RunDeck protocol service was not found")
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
        _connection.value = DeviceConnection.Connecting(discovered.name)
        gatt?.close()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            discovered.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            discovered.device.connectGatt(appContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendDemoMetrics() {
        writeMetrics(
            sequence.getAndIncrement() and 0xFFFF,
            SystemClock.elapsedRealtime() and 0xFFFF_FFFFL,
            LiveMetrics(flags = 0x000F, paceCentisecondsPerMile = 50500, distanceCentimeters = 12_345,
                elapsedSeconds = 72, movingSeconds = 70, speedCentimetersPerSecond = 320,
                temperatureDeciF = 780, forwardedHeartRate = 143),
        )
    }

    /** Publishes GPS state as the same compact packet the display already understands. */
    fun publishRunState(state: RunUiState) {
        currentRunState = state
        if (state.active) sendRunMetrics(state)
    }

    private fun sendRunMetrics(state: RunUiState) {
        val pace = state.paceSecondsPerMile?.times(100)?.roundToInt()?.coerceIn(100, 300_000) ?: 0
        writeMetrics(
            sequence.getAndIncrement() and 0xFFFF,
            SystemClock.elapsedRealtime() and 0xFFFF_FFFFL,
            LiveMetrics(
                flags = if (pace > 0) 0x0003 else 0x0001,
                paceCentisecondsPerMile = pace,
                distanceCentimeters = (state.distanceMeters * 100).roundToInt().toLong(),
                elapsedSeconds = state.elapsedSeconds,
                movingSeconds = state.elapsedSeconds,
                speedCentimetersPerSecond = 0,
                temperatureDeciF = 0,
                forwardedHeartRate = 0,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun writeMetrics(sequence: Int, sourceMs: Long, metrics: LiveMetrics) {
        val characteristic = liveMetrics ?: return
        val payload = RunDeckProtocol.encodeLiveMetrics(sequence, sourceMs, metrics)
        val target = gatt ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            @Suppress("DEPRECATION") target.writeCharacteristic(characteristic)
        }
    }

    fun startMetricsStream() {
        if (streamingDemoMetrics) return
        streamingDemoMetrics = true
        demoHandler.post(object : Runnable {
            override fun run() {
                if (!streamingDemoMetrics || liveMetrics == null) return
                currentRunState?.takeIf { it.active }?.let(::sendRunMetrics) ?: sendDemoMetrics()
                demoHandler.postDelayed(this, 1_000)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun close() {
        streamingDemoMetrics = false
        demoHandler.removeCallbacksAndMessages(null)
        scanner?.stopScan(scanCallback)
        gatt?.close(); gatt = null; liveMetrics = null
    }
}
