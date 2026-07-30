package com.rundeck.app

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rundeck.app.ble.DeviceConnection
import com.rundeck.app.ble.DiscoveredRunDeck
import com.rundeck.app.ble.RunDeckBleClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RunDeckApp() }
    }
}

private class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val bleClient = RunDeckBleClient(application)
    val devices = bleClient.devices
    val connection = bleClient.connection

    fun scan() = bleClient.startScan()
    fun connect(device: DiscoveredRunDeck) = bleClient.connect(device)
    fun sendDemoMetrics() = bleClient.sendDemoMetrics()

    override fun onCleared() {
        bleClient.close()
    }
}

@Composable
private fun RunDeckApp(viewModel: DeviceViewModel = viewModel()) {
    val devices by viewModel.devices.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) viewModel.scan()
    }
    val requestBluetooth = remember {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
            } else {
                viewModel.scan()
            }
        }
    }

    MaterialTheme {
        Surface(color = Black, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 42.dp)) {
                Text("RUNDECK", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text("DEVICE SETUP", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                StatusCard(connection)
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = requestBluetooth,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Black),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("FIND RUNDECK", fontWeight = FontWeight.Black, fontSize = 17.sp) }
                Spacer(Modifier.height(22.dp))
                Text("NEARBY DEVICES", color = Muted, fontSize = 13.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                if (devices.isEmpty()) {
                    Text("Tap FIND RUNDECK to scan for the RunDeck display.", color = Muted)
                } else {
                    devices.forEach { device -> DeviceRow(device) { viewModel.connect(device) } }
                }
                if (connection is DeviceConnection.Ready) {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = viewModel::sendDemoMetrics,
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Black),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("SEND DEMO METRICS", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(connection: DeviceConnection) {
    val detail = when (connection) {
        DeviceConnection.Idle -> "Ready to find your display"
        DeviceConnection.Scanning -> "Scanning nearby Bluetooth devices"
        is DeviceConnection.Connecting -> "Connecting to ${connection.name}"
        is DeviceConnection.Ready -> "Connected to ${connection.name}"
        is DeviceConnection.Error -> connection.message
    }
    val accent = if (connection is DeviceConnection.Ready) Lime else Cyan
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp),
    ) {
        Text("DISPLAY", color = accent, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = White, fontSize = 18.sp)
    }
}

@Composable
private fun DeviceRow(device: DiscoveredRunDeck, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .background(Color(0xFF12161B), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(device.name, color = White, fontWeight = FontWeight.Bold)
            Text("${device.address}  ${device.rssi} dBm", color = Muted, fontSize = 12.sp)
        }
        Text("CONNECT", color = Lime, fontWeight = FontWeight.Bold)
    }
}

private val Black = Color(0xFF000000)
private val White = Color(0xFFF6F7F8)
private val Muted = Color(0xFFA5A8AD)
private val Lime = Color(0xFF9AF000)
private val Cyan = Color(0xFF38D7E4)
