# RunDeck working guide

This file is the durable handoff for future Codex sessions. Read
`docs/status.md` before changing code; it distinguishes verified behavior from
planned work.

## Repository and workflow

- Repository: `https://github.com/tijuanajack/RunDeck.git`; authenticated HTTPS
  remote is `origin`.
- Work directly on `main` unless the user explicitly requests another branch.
- Preserve unrelated changes in a dirty tree. Make small, intentional commits,
  run checks, then push only changes made for the requested task.
- Do not claim a next step has been performed until code/build/deployment has
  actually happened. Give short progress updates during long builds/flashes.
- Do not commit APKs, firmware binaries, downloaded vendor archives, device
  identifiers, API keys, notification content, or the recovery image.

## Hardware safety

- Target board: Waveshare ESP32-S3-Touch-AMOLED-2.41, 600 × 450 landscape.
- The verified display implementation is SH8601-based, despite older planning
  references to RM690B0. Do not swap drivers or alter display/panel pin setup
  casually; a prior bad update caused a black-screen recovery event.
- Critical 2026-07-30 finding: with a verified data cable, Waveshare's
  untouched **Arduino** LVGL demo uploads and hashes successfully but leaves
  the panel black; the vendor ESP-IDF LVGL demo did the same. A source and
  header-matched ESP-IDF 5.5.2 `10_FactoryProgram` build lights the panel after
  upload reset but still fails after a true USB cold boot. Do not flash another
  candidate until serial cold-boot diagnostics identify the remaining runtime
  initialization mismatch. The factory image remains the recovery baseline.
- `firmware/esp-idf-baseline/` is the only approved next firmware candidate.
  It is deliberately display-only and pins ESP-IDF 5.5.2 with
  `esp_lcd_sh8601 2.0.1~1`. It must display solid green and emit
  `DISPLAY_GATE_PASS` on three USB cold boots before touch, LVGL, BLE, or the
  RunDeck UI may be brought onto this path. It is built but not yet flashed.
- Use a known-good direct USB data cable. A failing cable previously dropped
  full-image writes at about 14%; the ROM-loader factory restore succeeded
  after the cable was replaced.
- FQBN: `esp32:esp32:waveshare_esp32_s3_touch_amoled_241`.
- Tool versions are pinned for bring-up: ESP32 Arduino core **3.0.7**, LVGL
  **8.4.0**, NimBLE-Arduino **2.5.1**.
- The factory recovery image is a local, ignored file:
  `firmware/backups/factory-before-rundeck.bin`. Never delete or overwrite it.
- If the display is black after an update or USB power cycle, follow
  [the recovery gate](docs/recovery.md). Restore factory, obtain a physical
  cold-boot confirmation, then and only then flash RunDeck. A successful
  upload hash is not proof of a successful panel cold boot.
- BOOT is a recovery control. Do not assign safety-critical behavior to PWR.
- The normal helper requires `RUNDECK_HARDWARE_VALIDATED=1` and exactly one
  serial device:

  ```sh
  cd firmware
  RUNDECK_HARDWARE_VALIDATED=1 ./tools/flash-rundeck.sh
  ```

  When an Android phone is also connected, this intentionally refuses to
  guess. Identify the ESP32 first (its USB identity is Espressif USB JTAG),
  then compile/upload explicitly after confirming the port:

  ```sh
  udevadm info --query=property --name=/dev/ttyACM0
  sg dialout -c '/home/tjhurt/.local/bin/arduino-cli compile --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 RunDeck --output-dir build && /home/tjhurt/.local/bin/arduino-cli upload --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 --port /dev/ttyACM0 RunDeck'
  ```

  Do not blindly copy `/dev/ttyACM0`: confirm it is the ESP32 each session.
  Use `sg dialout -c` for serial access when needed.

## Android build and device deployment

- Android application ID: `com.rundeck.app`; minimum SDK 26, target SDK 36.
- The connected test phone is a Samsung running API 36. Check it with:

  ```sh
  /home/tjhurt/.local/android-sdk/platform-tools/adb devices
  ```

- Local JDK and SDK used by this workspace:

  ```sh
  cd android
  env PATH=/home/tjhurt/.local/rundeck-toolchain/jdk-17/bin:$PATH ./gradlew --no-daemon testDebugUnitTest assembleDebug --console=plain
  /home/tjhurt/.local/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
  /home/tjhurt/.local/android-sdk/platform-tools/adb shell am force-stop com.rundeck.app
  /home/tjhurt/.local/android-sdk/platform-tools/adb shell am start -n com.rundeck.app/.MainActivity
  ```

- Never grant location or notification permissions through ADB as a substitute
  for the user flow. The app must ask when the user starts a run.

## Current architecture contract

- Android is authoritative for timing, GPS, pace, distance, targets, presets,
  weather, media, notifications, and configuration. Firmware displays only
  fresh state and does not keep full run history.
- Current verified path: Android foreground `LocationManager` service →
  `RunSession` flow → `RunDeckBleClient` live-metrics write at 1 Hz → ESP32
  NimBLE server → immutable firmware `DisplayState` → LVGL dashboard.
- Live metrics are v1, little-endian, 33 bytes total. Preserve validation,
  sequence and freshness protections in `firmware/RunDeck/ble/rundeck_ble.cpp`
  and `android/app/src/main/java/com/rundeck/app/ble/RunDeckProtocol.kt`.
- For the Long Run target, Android owns the 8:50–9:20 /mi rule. Do not hardcode
  a competing target on the display. Firmware currently renders the target and
  status flags sent by Android.
- A zero pace means GPS has not produced a reliable pace; it must render as
  `--:--`, never a plausible live pace.

## Validation before handoff

- Android changes: run unit tests and `assembleDebug`; install/launch on the
  attached phone when the change affects user-visible behavior. Check logcat
  for `FATAL EXCEPTION` after launch.
- Firmware changes: compile before upload. Confirm the serial port belongs to
  the ESP32 and report successful flash output. Do not flash merely to test an
  unrelated Android change.
- BLE changes: test scan, connect, reconnect, and a live metric update. For
  GPS changes, perform a short real walk whenever feasible and compare phone
  and display pace/distance/elapsed values.

## Roadmap boundaries

Implement next: full run-state/preset CBOR and acknowledgements, resume/discard
checkpoint UI, phone-forwarded HR, then direct-HR reliability testing. Media,
notifications, weather, touch lock, brightness, and outdoor/power work follow.
Do not add maps, ski mode, Bowline, cloud/Garmin integrations, iOS, HRV,
structured workouts, or voice reply to V1.
