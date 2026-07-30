# Android companion

This is a buildable Compose foundation targeting Android 16/API 36 and minimum
API 26. Its currently implemented pure domain component is the pace-window
calculator; device BLE, foreground location, persisted presets, weather, media,
and notification forwarding are deliberately separate adapters to be added in
their respective milestones.

Before the first device integration, add Hilt, Room, DataStore, and the Android
BLE client implementation with tests. Do not request notification-listener,
location, or nearby-device permission until the user starts that setup flow.
