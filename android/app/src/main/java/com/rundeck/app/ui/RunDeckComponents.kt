package com.rundeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rundeck.app.ble.DeviceConnection
import com.rundeck.app.ble.DiscoveredRunDeck

val Black = Color(0xFF000000)
val White = Color(0xFFF6F7F8)
val Muted = Color(0xFFA5A8AD)
val Lime = Color(0xFF9AF000)
val Cyan = Color(0xFF38D7E4)
val Amber = Color(0xFFFFBD35)

@Composable
fun StatusCard(connection: DeviceConnection) {
    val (label, color) = when (connection) {
        DeviceConnection.Idle -> "DISPLAY OFFLINE" to Amber
        DeviceConnection.Scanning -> "SCANNING" to Cyan
        is DeviceConnection.Connecting -> "CONNECTING" to Cyan
        is DeviceConnection.Ready -> "DISPLAY CONNECTED" to Lime
        is DeviceConnection.Error -> connection.message to Color(0xFFFF5252)
    }
    Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
}

@Composable
fun DeviceRow(device: DiscoveredRunDeck, onClick: () -> Unit) = Row(
    Modifier
        .fillMaxWidth()
        .background(Color(0xFF10151A), RoundedCornerShape(12.dp))
        .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(device.name, color = White, fontWeight = FontWeight.Bold)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Black),
        modifier = Modifier.width(110.dp).height(42.dp),
        shape = RoundedCornerShape(10.dp),
    ) { Text("CONNECT", fontWeight = FontWeight.Black, fontSize = 12.sp) }
}

@Composable
fun BrandHeader(title: String) {
    Text("RUNDECK", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
    Text(title, color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) = Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Black),
    modifier = Modifier.fillMaxWidth().height(58.dp),
    shape = RoundedCornerShape(12.dp),
) { Text(label, fontWeight = FontWeight.Black, fontSize = 17.sp) }
