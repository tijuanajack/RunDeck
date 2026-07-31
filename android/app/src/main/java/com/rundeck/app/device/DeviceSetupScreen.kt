package com.rundeck.app.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rundeck.app.ble.DeviceConnection
import com.rundeck.app.ble.DiscoveredRunDeck
import com.rundeck.app.media.PhoneMediaState
import com.rundeck.app.notifications.RunDeckNotificationSettings
import com.rundeck.app.run.HrOwnershipMode
import com.rundeck.app.hr.HeartRateDevice
import com.rundeck.app.hr.HeartRateState
import com.rundeck.app.ui.Amber
import com.rundeck.app.ui.Black
import com.rundeck.app.ui.BrandHeader
import com.rundeck.app.ui.Cyan
import com.rundeck.app.ui.Lime
import com.rundeck.app.ui.Muted
import com.rundeck.app.ui.PrimaryButton
import com.rundeck.app.ui.StatusCard
import com.rundeck.app.ui.White

@Composable
fun DeviceSetupScreen(
    connection: DeviceConnection,
    devices: List<DiscoveredRunDeck>,
    media: PhoneMediaState,
    notifications: RunDeckNotificationSettings,
    onScan: () -> Unit,
    onConnect: (DiscoveredRunDeck) -> Unit,
    onContinue: () -> Unit,
    onEnableMedia: () -> Unit,
    onRefreshMedia: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onNotificationForwarding: (Boolean) -> Unit,
    onNotificationAllowAll: (Boolean) -> Unit,
    onNotificationContactsAll: (Boolean) -> Unit,
    onNotificationSourceAllowed: (String, Boolean) -> Unit,
    onNotificationContactAllowed: (String, String, Boolean) -> Unit,
    hrOwnership: HrOwnershipMode,
    onHrOwnershipMode: (HrOwnershipMode) -> Unit,
    heartRate: HeartRateState,
    heartRateDevices: List<HeartRateDevice>,
    onScanHeartRate: () -> Unit,
    onConnectHeartRate: (HeartRateDevice) -> Unit,
    backgroundRunAllowed: Boolean,
    onAllowBackgroundRuns: () -> Unit,
    onUseWeatherLocation: () -> Unit,
) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 42.dp),
) {
    BrandHeader("DEVICE SETUP")
    Spacer(Modifier.height(14.dp))
    StatusCard(connection)
    Spacer(Modifier.height(28.dp))
    PrimaryButton("FIND RUNDECK", onScan)
    Spacer(Modifier.height(22.dp))
    androidx.compose.material3.Text("NEARBY DEVICES", color = Muted, fontSize = 13.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(10.dp))
    if (devices.isEmpty()) androidx.compose.material3.Text("Tap FIND RUNDECK to scan for the RunDeck display.", color = Muted)
    else devices.forEach { device -> com.rundeck.app.ui.DeviceRow(device) { onConnect(device) } }
    Spacer(Modifier.height(22.dp))
    MediaCard(media, onEnableMedia, onRefreshMedia, onPrevious, onPlayPause, onNext)
    Spacer(Modifier.height(14.dp))
    NotificationCard(notifications, onEnableMedia, onNotificationForwarding, onNotificationAllowAll, onNotificationContactsAll, onNotificationSourceAllowed, onNotificationContactAllowed)
    Spacer(Modifier.height(14.dp))
    ResilienceCard(backgroundRunAllowed, onAllowBackgroundRuns)
    Spacer(Modifier.height(14.dp))
    WeatherLocationCard(onUseWeatherLocation)
    Spacer(Modifier.height(14.dp))
    HrModeCard(hrOwnership, onHrOwnershipMode)
    Spacer(Modifier.height(14.dp))
    HeartRateCard(heartRate, heartRateDevices, onScanHeartRate, onConnectHeartRate)
    if (connection is DeviceConnection.Ready) {
        Spacer(Modifier.height(24.dp))
        PrimaryButton("CONTINUE TO RUN SETUP", onContinue)
    }
}

@Composable
private fun WeatherLocationCard(onUseLocation: () -> Unit) = Column(
    Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp),
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text("WEATHER", color = Amber, fontSize = 13.sp, letterSpacing = 2.sp)
        androidx.compose.material3.Text("OPEN-METEO", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Text("Use the phone's recent location so temperature is available before a run starts.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    PrimaryButton("USE PHONE LOCATION", onUseLocation)
}

@Composable
private fun HeartRateCard(state: HeartRateState, devices: List<HeartRateDevice>, onScan: () -> Unit, onConnect: (HeartRateDevice) -> Unit) = Column(Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text("HR STRAP", color = Color(0xFFFF5252), fontSize = 13.sp, letterSpacing = 2.sp)
        androidx.compose.material3.Text(state.status, color = if (state.connected) Lime else Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Text(state.bpm?.let { "$it BPM" } ?: "No heart-rate reading", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    PrimaryButton("SCAN HR STRAPS", onScan)
    if (devices.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        devices.forEach { device ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(device.name, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    androidx.compose.material3.Text(device.address, color = Muted, fontSize = 11.sp)
                }
                Button(onClick = { onConnect(device) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14232A), contentColor = White), modifier = Modifier.width(100.dp).height(38.dp), shape = RoundedCornerShape(10.dp)) { androidx.compose.material3.Text("CONNECT", fontWeight = FontWeight.Black, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun HrModeCard(mode: HrOwnershipMode, onMode: (HrOwnershipMode) -> Unit) = Column(Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text("HEART RATE SOURCE", color = Color(0xFFFF5252), fontSize = 13.sp, letterSpacing = 2.sp)
        androidx.compose.material3.Text(if (mode == HrOwnershipMode.PhoneForwardedHr) "FORWARDING" else "SAFE", color = if (mode == HrOwnershipMode.PhoneForwardedHr) Lime else Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Text("Choose who owns the strap connection. No HR value is shown when the selected source is unavailable.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    HrOwnershipMode.entries.forEach { candidate ->
        Button(onClick = { onMode(candidate) }, colors = ButtonDefaults.buttonColors(containerColor = if (candidate == mode) Lime else Color(0xFF14232A), contentColor = if (candidate == mode) Black else White), modifier = Modifier.fillMaxWidth().height(42.dp).padding(bottom = 6.dp), shape = RoundedCornerShape(10.dp)) {
            androidx.compose.material3.Text(candidate.label, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        if (candidate == mode) androidx.compose.material3.Text(candidate.detail, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}

@Composable
private fun ResilienceCard(allowed: Boolean, onAllow: () -> Unit) = Column(Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text("BACKGROUND RUNS", color = Cyan, fontSize = 13.sp, letterSpacing = 2.sp)
        androidx.compose.material3.Text(if (allowed) "READY" else "REVIEW", color = if (allowed) Lime else Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Text("Keeps GPS and BLE active when the phone screen is locked.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    if (allowed) androidx.compose.material3.Text("Screen-lock protection is enabled.", color = Lime, fontSize = 13.sp)
    else {
        PrimaryButton("ALLOW BACKGROUND RUNS", onAllow)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text("Samsung will ask you to allow RunDeck to run without battery restrictions. Nothing changes until you approve it.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun NotificationCard(
    settings: RunDeckNotificationSettings,
    onOpenAccess: () -> Unit,
    onForwarding: (Boolean) -> Unit,
    onAllowAll: (Boolean) -> Unit,
    onContactsAll: (Boolean) -> Unit,
    onSourceAllowed: (String, Boolean) -> Unit,
    onContactAllowed: (String, String, Boolean) -> Unit,
) = Column(Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text("MESSAGES", color = Lime, fontSize = 13.sp, letterSpacing = 2.sp)
        androidx.compose.material3.Text(if (settings.forwardingEnabled) "ON" else "PAUSED", color = if (settings.forwardingEnabled) Lime else Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Text("Choose which message apps can pop up on RunDeck.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    ActionRow {
        ActionButton(if (settings.forwardingEnabled) "PAUSE" else "RESUME", if (settings.forwardingEnabled) Color(0xFF451B1B) else Color(0xFF20390A), White, Modifier.weight(1f)) { onForwarding(!settings.forwardingEnabled) }
        ActionButton("ACCESS", Color(0xFF14232A), White, Modifier.weight(1f), onOpenAccess)
    }
    Spacer(Modifier.height(10.dp))
    ActionRow {
        ActionButton("ALL APPS", if (settings.allowAllMessageApps) Lime else Color(0xFF14232A), if (settings.allowAllMessageApps) Black else White, Modifier.weight(1f)) { onAllowAll(true) }
        ActionButton("SELECTED", if (!settings.allowAllMessageApps) Lime else Color(0xFF14232A), if (!settings.allowAllMessageApps) Black else White, Modifier.weight(1f)) { onAllowAll(false) }
    }
    if (!settings.allowAllMessageApps) {
        Spacer(Modifier.height(12.dp))
        if (settings.sources.isEmpty()) androidx.compose.material3.Text("No message apps detected yet. Send one test text, then return here to pick the source.", color = Amber, fontSize = 12.sp)
        else settings.sources.forEach { source ->
            SettingRow(source.label, source.packageName, source.allowed, "ON", "OFF") { onSourceAllowed(source.packageName, !source.allowed) }
        }
    }
    Spacer(Modifier.height(12.dp))
    androidx.compose.material3.Text("CONTACT FILTER", color = Cyan, fontSize = 12.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(6.dp))
    androidx.compose.material3.Text("Optional sender-level filtering for the selected message apps.", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    ActionRow {
        ActionButton("ALL CONTACTS", if (settings.allowAllContacts) Lime else Color(0xFF14232A), if (settings.allowAllContacts) Black else White, Modifier.weight(1f)) { onContactsAll(true) }
        ActionButton("SELECTED", if (!settings.allowAllContacts) Lime else Color(0xFF14232A), if (!settings.allowAllContacts) Black else White, Modifier.weight(1f)) { onContactsAll(false) }
    }
    if (!settings.allowAllContacts) {
        Spacer(Modifier.height(8.dp))
        if (settings.contacts.isEmpty()) androidx.compose.material3.Text("Send a test message to discover contacts.", color = Amber, fontSize = 12.sp)
        else settings.contacts.forEach { contact ->
            SettingRow(contact.sender, contact.packageName, contact.allowed, "ON", "OFF") { onContactAllowed(contact.packageName, contact.sender, !contact.allowed) }
        }
    }
}

@Composable
private fun MediaCard(media: PhoneMediaState, onEnableMedia: () -> Unit, onRefreshMedia: () -> Unit, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) = Column(Modifier.fillMaxWidth().background(Color(0xFF080A0D), RoundedCornerShape(16.dp)).padding(18.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text("MEDIA", color = Cyan, fontSize = 13.sp, letterSpacing = 2.sp)
        androidx.compose.material3.Text(if (media.accessEnabled) "READY" else "ACCESS NEEDED", color = if (media.accessEnabled) Lime else Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Text(if (media.available) "${media.title} — ${media.artist}" else "No active media session", color = White, fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    ActionRow {
        ActionButton("ACCESS", Color(0xFF14232A), White, Modifier.weight(1f), onEnableMedia)
        ActionButton("REFRESH", Color(0xFF14232A), White, Modifier.weight(1f), onRefreshMedia)
    }
    Spacer(Modifier.height(10.dp))
    ActionRow {
        ActionButton("PREV", Color(0xFF14232A), White, Modifier.weight(1f), onPrevious)
        ActionButton(if (media.playing) "PAUSE" else "PLAY", Color(0xFF14232A), White, Modifier.weight(1f), onPlayPause)
        ActionButton("NEXT", Color(0xFF14232A), White, Modifier.weight(1f), onNext)
    }
}

@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
private fun ActionButton(label: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) = Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content), modifier = modifier.height(42.dp), shape = RoundedCornerShape(10.dp)) { androidx.compose.material3.Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp) }

@Composable
private fun SettingRow(title: String, detail: String, enabled: Boolean, onLabel: String, offLabel: String, onClick: () -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
    Column(Modifier.weight(1f)) {
        androidx.compose.material3.Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        androidx.compose.material3.Text(detail, color = Muted, fontSize = 11.sp)
    }
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = if (enabled) Lime else Color(0xFF14232A), contentColor = if (enabled) Black else White), modifier = Modifier.width(76.dp).height(36.dp), shape = RoundedCornerShape(10.dp)) { androidx.compose.material3.Text(if (enabled) onLabel else offLabel, fontWeight = FontWeight.Black, fontSize = 12.sp) }
}
