# Android companion

This is a buildable Jetpack Compose companion targeting Android 16/API 36 and
minimum API 26. V1 provides filtered RunDeck BLE discovery/reconnect, the
branded setup and preset flow, foreground GPS tracking, live pace/distance/
elapsed metrics, pause/resume/stop, checkpoint recovery, MediaSession controls,
message overlays, weather/clock context, brightness settings, HR ownership
selection, and device-origin run controls.

The app requests Nearby Devices and location/notification permissions at the
relevant user setup or run action. Android 16 device-origin starts first bring
the Activity visibly forward before starting the location foreground service.
Direct-device Garmin HR and cloud sync are intentionally not V1 features.

## Build

```sh
env PATH=/home/tjhurt/.local/rundeck-toolchain/jdk-17/bin:$PATH \
  ./gradlew --no-daemon testDebugUnitTest assembleDebug --console=plain
```

Install the resulting `app/build/outputs/apk/debug/app-debug.apk` through ADB
or Android Studio after enabling developer options and USB debugging.
