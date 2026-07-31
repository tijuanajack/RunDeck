package com.rundeck.app.hr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class HeartRateDevice(val name: String, val address: String, internal val device: BluetoothDevice)
data class HeartRateState(val connected: Boolean = false, val bpm: Int? = null, val status: String = "GARMIN STRAP OFF", val deviceName: String? = null)

/** Standard Bluetooth Heart Rate Service client for phone-forwarded HR mode. */
class HeartRateClient(context: Context) {
    private val appContext = context.applicationContext
    private val scanner: BluetoothLeScanner? = appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private val preferences = appContext.getSharedPreferences("rundeck_hr", Context.MODE_PRIVATE)
    private var gatt: BluetoothGatt? = null
    private var selectedDevice: HeartRateDevice? = null
    private var reconnectAttempts = 0
    private var lastMeasurementAtMs = 0L
    private val seen = linkedMapOf<String, HeartRateDevice>()
    private val _devices = MutableStateFlow<List<HeartRateDevice>>(emptyList())
    val devices: StateFlow<List<HeartRateDevice>> = _devices.asStateFlow()
    private val _state = MutableStateFlow(HeartRateState())
    val state: StateFlow<HeartRateState> = _state.asStateFlow()
    private val staleCheck = object : Runnable {
        override fun run() {
            if (_state.value.connected && lastMeasurementAtMs > 0L && System.currentTimeMillis() - lastMeasurementAtMs > 10_000L) {
                _state.value = _state.value.copy(bpm = null, status = "HR STALE")
            }
            handler.postDelayed(this, 5_000L)
        }
    }

    init {
        handler.postDelayed(staleCheck, 5_000L)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (name.isBlank()) return
            seen[device.address] = HeartRateDevice(name, device.address, device)
            _devices.value = seen.values.sortedBy { it.name.lowercase() }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = _state.value.copy(status = "HR SCAN FAILED ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== this@HeartRateClient.gatt) { gatt.close(); return }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempts = 0
                _state.value = _state.value.copy(connected = false, bpm = null, status = "HR CONNECTING", deviceName = gatt.device.name)
                gatt.discoverServices()
            } else {
                gatt.close()
                this@HeartRateClient.gatt = null
                _state.value = HeartRateState(status = "GARMIN STRAP OFF")
                scheduleReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(HEART_RATE_SERVICE) ?: return disconnectWith("HR SERVICE NOT FOUND")
            val measurement = service.getCharacteristic(HEART_RATE_MEASUREMENT) ?: return disconnectWith("HR MEASUREMENT MISSING")
            gatt.setCharacteristicNotification(measurement, true)
            val descriptor = measurement.getDescriptor(CCCD) ?: return disconnectWith("HR NOTIFY MISSING")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _state.value = _state.value.copy(connected = true, status = "HR LIVE")
            else disconnectWith("HR SUBSCRIBE FAILED")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) parseMeasurement(characteristic.value ?: return)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) parseMeasurement(value)
        }
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        val bleScanner = scanner ?: return
        bleScanner.stopScan(scanCallback)
        seen.clear(); _devices.value = emptyList()
        _state.value = _state.value.copy(status = "SCANNING HR STRAPS")
        bleScanner.startScan(emptyList(), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        handler.postDelayed({ bleScanner.stopScan(scanCallback) }, 10_000L)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: HeartRateDevice) {
        scanner?.stopScan(scanCallback)
        reconnectAttempts = 0
        selectedDevice = device
        preferences.edit().putString(KEY_ADDRESS, device.address).apply()
        gatt?.close()
        gatt = device.device.connectGatt(appContext, false, gattCallback)
        _state.value = HeartRateState(status = "HR CONNECTING", deviceName = device.name)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectWith(status: String) {
        gatt?.close(); gatt = null
        _state.value = HeartRateState(status = status)
    }

    private fun scheduleReconnect() {
        val selected = selectedDevice ?: return
        if (reconnectAttempts >= 3) return
        reconnectAttempts++
        handler.postDelayed({
            gatt = selected.device.connectGatt(appContext, false, gattCallback)
        }, reconnectAttempts * 2_000L)
    }

    private fun parseMeasurement(value: ByteArray) {
        if (value.size < 2) return
        val flags = value[0].toInt() and 0xFF
        val bpm = if ((flags and 0x01) == 0) value[1].toInt() and 0xFF
        else if (value.size >= 3) (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        else return
        if (bpm !in 30..240) return
        lastMeasurementAtMs = System.currentTimeMillis()
        _state.value = _state.value.copy(connected = true, bpm = bpm, status = "HR LIVE")
    }

    @SuppressLint("MissingPermission")
    fun close() {
        scanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
        gatt?.close(); gatt = null
    }

    private companion object {
        val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val KEY_ADDRESS = "selected_address"
    }
}
