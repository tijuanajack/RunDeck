# Android companion

This is a buildable Compose companion targeting Android 16/API 36 and minimum
API 26. It currently provides the Bluetooth setup flow, filtered RunDeck BLE
discovery, GATT connection scaffolding, and a tested v1 live-metrics codec.
The device has not advertised the RunDeck service yet, so the app correctly
shows no discoverable RunDeck until the matching firmware BLE milestone lands.

The app requests Nearby Devices only after the user taps `FIND RUNDECK`.
Foreground location, notifications, notification-listener access, Hilt/Room,
and persisted run settings are subsequent milestones; they are deliberately not
requested or enabled by this pairing prototype.

## Build

```sh
./gradlew testDebugUnitTest assembleDebug
```

Install the resulting `app/build/outputs/apk/debug/app-debug.apk` through ADB
or Android Studio after enabling developer options and USB debugging.
