package com.rundeck.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rundeck.app.run.RunSession
import com.rundeck.app.run.RunTrackingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RunDeckApp() }
    }
}

class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val bleClient = RunDeckBleClient(application)
    val devices = bleClient.devices
    val connection = bleClient.connection
    fun scan() = bleClient.startScan()
    fun connect(device: DiscoveredRunDeck) = bleClient.connect(device)
    fun sendDemoMetrics() = bleClient.sendDemoMetrics()
    override fun onCleared() = bleClient.close()
}

@Composable
private fun RunDeckApp(viewModel: DeviceViewModel = viewModel()) {
    val devices by viewModel.devices.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val run by RunSession.state.collectAsState()
    var showRunSetup by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun beginRun() {
        val intent = Intent(context, RunTrackingService::class.java).setAction(RunTrackingService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }
    val runPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) beginRun()
    }
    val bluetoothPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.all { it }) viewModel.scan() }
    val requestBluetooth = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else viewModel.scan()
    }
    val requestRun = {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        runPermissions.launch(permissions.toTypedArray())
    }

    MaterialTheme {
        Surface(color = Black, modifier = Modifier.fillMaxSize()) {
            when {
                run.active -> ActiveRunScreen(run, onStop = {
                    context.startService(Intent(context, RunTrackingService::class.java).setAction(RunTrackingService.ACTION_STOP))
                })
                showRunSetup -> RunSetupScreen(
                    connected = connection is DeviceConnection.Ready,
                    onStart = requestRun,
                    onBack = { showRunSetup = false },
                )
                else -> DeviceSetupScreen(
                    connection, devices, requestBluetooth,
                    onConnect = viewModel::connect,
                    onContinue = { showRunSetup = true },
                )
            }
        }
    }
}

@Composable
private fun DeviceSetupScreen(
    connection: DeviceConnection,
    devices: List<DiscoveredRunDeck>,
    onScan: () -> Unit,
    onConnect: (DiscoveredRunDeck) -> Unit,
    onContinue: () -> Unit,
) = ScreenColumn {
    BrandHeader("DEVICE SETUP")
    Spacer(Modifier.height(14.dp))
    StatusCard(connection)
    Spacer(Modifier.height(28.dp))
    PrimaryButton("FIND RUNDECK", onScan)
    Spacer(Modifier.height(22.dp))
    Text("NEARBY DEVICES", color = Muted, fontSize = 13.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(10.dp))
    if (devices.isEmpty()) Text("Tap FIND RUNDECK to scan for the RunDeck display.", color = Muted)
    else devices.forEach { device -> DeviceRow(device) { onConnect(device) } }
    if (connection is DeviceConnection.Ready) {
        Spacer(Modifier.weight(1f))
        PrimaryButton("CONTINUE TO RUN SETUP", onContinue)
    }
}

@Composable
private fun RunSetupScreen(connected: Boolean, onStart: () -> Unit, onBack: () -> Unit) = ScreenColumn {
    Text("‹  DEVICE", color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
    Spacer(Modifier.height(18.dp))
    BrandHeader("READY TO RUN")
    Spacer(Modifier.height(26.dp))
    PresetCard("EASY", "HR 135–145", Color(0xFF91C84B))
    Spacer(Modifier.height(12.dp))
    PresetCard("STEADY", "PACE 8:45–9:00", Cyan)
    Spacer(Modifier.height(12.dp))
    PresetCard("LONG RUN", "10.0 MI   |   HR < 150   |   PACE 8:50–9:20", Lime, selected = true)
    Spacer(Modifier.weight(1f))
    Text(if (connected) "DISPLAY CONNECTED" else "PHONE-ONLY MODE", color = if (connected) Lime else Amber, fontSize = 13.sp, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(10.dp))
    PrimaryButton("START RUN", onStart)
    Spacer(Modifier.height(12.dp))
    Text("Location is used only while a run is active.", color = Muted, fontSize = 12.sp)
}

@Composable
private fun ActiveRunScreen(state: com.rundeck.app.run.RunUiState, onStop: () -> Unit) = ScreenColumn {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("RUNDECK", color = Lime, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text(state.gpsStatus, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(22.dp))
    Text("PACE", color = White, fontSize = 22.sp, letterSpacing = 3.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text(RunTrackingService.formatPace(state.paceSecondsPerMile), color = White, fontSize = 58.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(30.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Metric("DISTANCE", "%.2f MI".format(state.distanceMeters / 1609.344))
        Metric("ELAPSED", "%02d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60))
    }
    Spacer(Modifier.height(20.dp))
    Text("Long Run  •  HR < 150  •  Pace 8:50–9:20", color = Lime, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.weight(1f))
    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF451B1B), contentColor = White), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("STOP RUN", fontWeight = FontWeight.Black)
    }
}

@Composable private fun Metric(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, color = White, fontSize = 29.sp, fontWeight = FontWeight.Black)
    Text(label, color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
}

@Composable private fun PresetCard(title: String, detail: String, accent: Color, selected: Boolean = false) = Column(
    Modifier.fillMaxWidth().background(Color(0xFF0B0D10), RoundedCornerShape(16.dp)).padding(20.dp),
) {
    Text(title, color = if (selected) Lime else White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    Spacer(Modifier.height(5.dp)); Text(detail, color = accent, fontWeight = FontWeight.Bold)
}

@Composable private fun StatusCard(connection: DeviceConnection) {
    val detail = when (connection) {
        DeviceConnection.Idle -> "Ready to find your display"
        DeviceConnection.Scanning -> "Scanning nearby Bluetooth devices"
        is DeviceConnection.Connecting -> "Connecting to ${connection.name}"
        is DeviceConnection.Ready -> "Connected to ${connection.name}"
        is DeviceConnection.Error -> connection.message
    }
    val accent = if (connection is DeviceConnection.Ready) Lime else Cyan
    Column(Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("DISPLAY", color = accent, fontSize = 13.sp, letterSpacing = 2.sp); Spacer(Modifier.height(6.dp)); Text(detail, color = White, fontSize = 18.sp)
    }
}

@Composable private fun DeviceRow(device: DiscoveredRunDeck, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Color(0xFF12161B), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
) { Column { Text(device.name, color = White, fontWeight = FontWeight.Bold); Text("${device.address}  ${device.rssi} dBm", color = Muted, fontSize = 12.sp) }; Text("CONNECT", color = Lime, fontWeight = FontWeight.Bold) }

@Composable private fun BrandHeader(title: String) { Text("RUNDECK", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp); Text(title, color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 42.dp), content = content)
@Composable private fun PrimaryButton(label: String, onClick: () -> Unit) = Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Black), modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(12.dp)) { Text(label, fontWeight = FontWeight.Black, fontSize = 17.sp) }

private val Black = Color(0xFF000000)
private val White = Color(0xFFF6F7F8)
private val Muted = Color(0xFFA5A8AD)
private val Lime = Color(0xFF9AF000)
private val Cyan = Color(0xFF38D7E4)
private val Amber = Color(0xFFFFBD35)
