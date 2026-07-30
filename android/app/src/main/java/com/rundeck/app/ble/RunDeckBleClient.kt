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
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

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
    private val seen = linkedMapOf<String, DiscoveredRunDeck>()
    private val _devices = MutableStateFlow<List<DiscoveredRunDeck>>(emptyList())
    val devices: StateFlow<List<DiscoveredRunDeck>> = _devices.asStateFlow()
    private val _connection = MutableStateFlow<DeviceConnection>(DeviceConnection.Idle)
    val connection: StateFlow<DeviceConnection> = _connection.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "RunDeck (${device.address.takeLast(5)})"
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
                startDemoStream()
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
        seen.clear(); _devices.value = emptyList(); _connection.value = DeviceConnection.Scanning
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(RunDeckProtocol.SERVICE_UUID)).build())
        bluetoothScanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
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
        val characteristic = liveMetrics ?: return
        val payload = RunDeckProtocol.encodeLiveMetrics(
            sequence.getAndIncrement() and 0xFFFF,
            SystemClock.elapsedRealtime(),
            LiveMetrics(flags = 0x000F, paceCentisecondsPerMile = 50500, distanceCentimeters = 12_345,
                elapsedSeconds = 72, movingSeconds = 70, speedCentimetersPerSecond = 320,
                temperatureDeciF = 780, forwardedHeartRate = 143),
        )
        val target = gatt ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = payload
            @Suppress("DEPRECATION") target.writeCharacteristic(characteristic)
        }
    }

    fun startDemoStream() {
        if (streamingDemoMetrics) return
        streamingDemoMetrics = true
        demoHandler.post(object : Runnable {
            override fun run() {
                if (!streamingDemoMetrics || liveMetrics == null) return
                sendDemoMetrics()
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
